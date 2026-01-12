/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.adapter.mongodb; // 定义MongoFilter类所在的包路径，属于MongoDB适配器包

import org.apache.calcite.plan.RelOptCluster; // 引入RelOptCluster类，用于表示关系代数表达式所在的集群
import org.apache.calcite.plan.RelOptCost; // 引入RelOptCost类，用于表示关系代数表达式的执行成本
import org.apache.calcite.plan.RelOptPlanner; // 引入RelOptPlanner类，用于表示关系代数优化器
import org.apache.calcite.plan.RelOptUtil; // 引入RelOptUtil类，提供关系代数表达式处理的实用工具方法
import org.apache.calcite.plan.RelTraitSet; // 引入RelTraitSet类，用于表示关系代数表达式的特征集合
import org.apache.calcite.rel.RelNode; // 引入RelNode接口，表示关系代数表达式节点
import org.apache.calcite.rel.core.Filter; // 引入Filter类，MongoFilter继承自Filter，表示过滤操作
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 引入RelMetadataQuery类，用于查询关系代数表达式的元数据
import org.apache.calcite.rex.RexBuilder; // 引入RexBuilder类，用于构建行表达式(RexNode)
import org.apache.calcite.rex.RexCall; // 引入RexCall类，表示行表达式中的函数调用
import org.apache.calcite.rex.RexInputRef; // 引入RexInputRef类，表示行表达式中的输入字段引用
import org.apache.calcite.rex.RexLiteral; // 引入RexLiteral类，表示行表达式中的字面量常量
import org.apache.calcite.rex.RexNode; // 引入RexNode接口，表示行表达式节点
import org.apache.calcite.rex.RexUtil; // 引入RexUtil类，提供行表达式处理的实用工具方法
import org.apache.calcite.util.JsonBuilder; // 引入JsonBuilder类，用于构建JSON格式的MongoDB查询表达式
import org.apache.calcite.util.Pair; // 引入Pair类，用于表示键值对

import com.google.common.collect.HashMultimap; // 引入Google Guava的HashMultimap类，用于存储多值映射
import com.google.common.collect.Multimap; // 引入Google Guava的Multimap接口，表示多值映射

import org.checkerframework.checker.nullness.qual.Nullable; // 引入注解，用于标记可能为null的返回值

import java.util.ArrayList; // 引入ArrayList类，用于动态数组
import java.util.Collection; // 引入Collection接口，表示集合
import java.util.LinkedHashMap; // 引入LinkedHashMap类，用于保持插入顺序的哈希映射
import java.util.List; // 引入List接口，表示列表
import java.util.Map; // 引入Map接口，表示映射

/**
 * Implementation of a {@link org.apache.calcite.rel.core.Filter}
 * relational expression in MongoDB.
 * MongoFilter类是Calcite中Filter关系代数表达式在MongoDB中的实现
 * 它负责将Calcite的过滤条件转换为MongoDB的查询表达式
 * 实现了MongoRel接口，表示这是一个MongoDB特有的关系代数节点
 * 主要功能是将SQL中的WHERE条件转换为MongoDB的$match操作符
 */
public class MongoFilter extends Filter implements MongoRel { // 定义MongoFilter类，继承自Filter并实现MongoRel接口
  public MongoFilter( // 构造方法：创建MongoFilter实例
      RelOptCluster cluster, // 参数：关系代数表达式所在的集群，包含类型工厂等信息
      RelTraitSet traitSet, // 参数：关系代数表达式的特征集合，包含约定等特征
      RelNode child, // 参数：子节点，即过滤操作作用的输入关系
      RexNode condition) { // 参数：过滤条件，用RexNode表示的布尔表达式
    super(cluster, traitSet, child, condition); // 调用父类Filter的构造方法，初始化基础属性
    assert getConvention() == MongoRel.CONVENTION; // 断言：确保当前节点的约定是MongoRel.CONVENTION，即MongoDB约定
    assert getConvention() == child.getConvention(); // 断言：确保子节点的约定与当前节点一致，保证特征兼容
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算当前节点的执行成本
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于获取统计信息
    return super.computeSelfCost(planner, mq).multiplyBy(0.1); // 调用父类的成本计算方法并将结果乘以0.1，因为MongoDB的过滤操作成本较低
  }

  @Override public MongoFilter copy(RelTraitSet traitSet, RelNode input, // 重写copy方法，创建MongoFilter的副本
      RexNode condition) { // 参数：新的特征集合、输入节点和过滤条件
    return new MongoFilter(getCluster(), traitSet, input, condition); // 返回新的MongoFilter实例，使用新的参数
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法，实现MongoDB查询的生成
    implementor.visitChild(0, getInput()); // 首先访问子节点，生成子查询的MongoDB实现
    Translator translator = // 创建Translator对象，用于将RexNode表达式转换为MongoDB表达式字符串
        new Translator(implementor.rexBuilder, // 传入RexBuilder用于构建行表达式
            MongoRules.mongoFieldNames(getRowType())); // 获取当前行类型的MongoDB字段名列表
    String match = translator.translateMatch(condition); // 将过滤条件转换为MongoDB的$match表达式字符串
    implementor.add(null, match); // 将生成的$match表达式添加到实现器中，null表示不关联特定字段
  }

  /** Translates {@link RexNode} expressions into MongoDB expression strings. */
  // 内部Translator类：负责将Calcite的RexNode表达式转换为MongoDB查询表达式字符串
  // 这是MongoFilter的核心转换逻辑，将SQL的WHERE条件转换为MongoDB的查询语法
  static class Translator { // 定义静态内部类Translator，用于表达式转换
    final JsonBuilder builder = new JsonBuilder(); // JsonBuilder实例，用于构建MongoDB查询的JSON格式
    private final RexBuilder rexBuilder; // RexBuilder实例，用于构建和操作行表达式
    private final List<String> fieldNames; // 字段名列表，存储当前关系表达式中所有字段的MongoDB名称

    Translator(RexBuilder rexBuilder, List<String> fieldNames) { // Translator构造方法
      this.rexBuilder = rexBuilder; // 保存RexBuilder实例，用于后续表达式处理
      this.fieldNames = fieldNames; // 保存字段名列表，用于字段引用的转换
    }

    private String translateMatch(RexNode condition) { // 将RexNode条件转换为MongoDB的$match表达式字符串
      Map<String, Object> map = builder.map(); // 创建一个新的Map对象，用于存储查询条件
      map.put("$match", translateOr(condition)); // 将转换后的条件放入$match键中，translateOr处理OR逻辑
      return builder.toJsonString(map); // 将Map转换为JSON字符串并返回，这是MongoDB查询的最终格式
    }

    private Map<String, Object> translateOr(RexNode condition) { // 将RexNode条件转换为MongoDB的OR表达式Map
      final RexNode condition2 = // 首先展开搜索条件，将IN操作符转换为多个OR条件
          RexUtil.expandSearch(rexBuilder, null, condition); // expandSearch将IN、BETWEEN等操作符规范化

      List<Map<String, Object>> list = new ArrayList<>(); // 创建列表，用于存储OR条件的各个分支
      for (RexNode node : RelOptUtil.disjunctions(condition2)) { // 遍历所有OR分支，disjunctions提取OR操作的所有子条件
        list.add(translateAnd(node)); // 对每个OR分支调用translateAnd，处理AND逻辑
      }
      switch (list.size()) { // 根据OR分支的数量决定返回格式
      case 1: // 如果只有一个分支
        return list.get(0); // 直接返回该分支，不需要包装$or操作符
      default: // 如果有多个分支
        Map<String, Object> map = builder.map(); // 创建新的Map
        map.put("$or", list); // 将所有分支放入$or键中，表示OR关系
        return map; // 返回包含$or的Map
      }
    }

    /** Translates a condition that may be an AND of other conditions. Gathers
     * together conditions that apply to the same field. */
    // 将RexNode条件转换为MongoDB的AND表达式Map，并将同一字段的多个条件合并
    private Map<String, Object> translateAnd(RexNode node0) { // translateAnd方法：处理AND逻辑，合并同一字段的条件
      final Multimap<String, Pair<String, RexLiteral>> multimap = // 创建多值映射，存储字段到操作符和值的映射
          HashMultimap.create(); // 一个字段可以有多个操作符，如{age: {$gt: 18, $lt: 65}}
      final Map<String, RexLiteral> eqMap = // 创建映射，存储等值条件（字段名 -> 字面量）
          new LinkedHashMap<>(); // 使用LinkedHashMap保持插入顺序
      final List<Map<String, Object>> orMapList = new ArrayList<>(); // 创建列表，存储嵌套的OR条件
      for (RexNode node : RelOptUtil.conjunctions(node0)) { // 遍历所有AND分支，conjunctions提取AND操作的所有子条件
        translateMatch2(node, orMapList, multimap, eqMap); // 对每个条件调用translateMatch2进行转换
      }
      Map<String, Object> map = builder.map(); // 创建结果Map，用于存储最终的条件
      for (Map.Entry<String, RexLiteral> entry : eqMap.entrySet()) { // 遍历所有等值条件
        multimap.removeAll(entry.getKey()); // 从multimap中移除该字段的所有非等值条件，避免冲突
        map.put(entry.getKey(), literalValue(entry.getValue())); // 将等值条件直接放入结果Map，格式为{field: value}
      }
      for (Map.Entry<String, Collection<Pair<String, RexLiteral>>> entry // 遍历所有非等值条件
          : multimap.asMap().entrySet()) { // 获取multimap的视图，每个字段对应多个操作符
        Map<String, Object> map2 = builder.map(); // 创建内层Map，用于存储同一字段的多个操作符
        for (Pair<String, RexLiteral> s : entry.getValue()) { // 遍历该字段的所有操作符
          addPredicate(map2, s.left, literalValue(s.right)); // 将操作符和值添加到内层Map
        }
        map.put(entry.getKey(), map2); // 将内层Map放入结果Map，格式为{field: {op1: val1, op2: val2}}
      }
      if (!orMapList.isEmpty()) { // 如果存在嵌套的OR条件
        Map<String, Object> andMap = builder.map(); // 创建AND Map
        if (!map.isEmpty()) { // 如果map不为空，说明有其他条件
          orMapList.add(map); // 将map添加到OR列表中
        }
        andMap.put("$and", orMapList); // 将OR列表放入$and键中
        return andMap; // 返回包含$and的Map
      }
      return map; // 返回结果Map
    }

    private static void addPredicate(Map<String, Object> map, String op, Object v) { // 添加谓词到Map中，处理重复操作符
      if (map.containsKey(op) && stronger(op, map.get(op), v)) { // 如果操作符已存在且新值更强
        return; // 直接返回，不添加新值，保留更强的条件
      }
      map.put(op, v); // 将操作符和值添加到Map中
    }

    /** Returns whether {@code v0} is a stronger value for operator {@code key}
     * than {@code v1}.
     *
     * <p>For example, {@code stronger("$lt", 100, 200)} returns true, because
     * "&lt; 100" is a more powerful condition than "&lt; 200".
     */
    // 判断v0是否比v1对操作符key是更强的条件
    private static boolean stronger(String key, Object v0, Object v1) { // stronger方法：比较两个条件哪个更严格
      if (key.equals("$lt") || key.equals("$lte")) { // 对于小于或小于等于操作符
        if (v0 instanceof Number && v1 instanceof Number) { // 如果都是数字
          return ((Number) v0).doubleValue() < ((Number) v1).doubleValue(); // v0更小则更强，因为x < 100比x < 200更严格
        }
        if (v0 instanceof String && v1 instanceof String) { // 如果都是字符串
          return v0.toString().compareTo(v1.toString()) < 0; // v0更小则更强
        }
      }
      if (key.equals("$gt") || key.equals("$gte")) { // 对于大于或大于等于操作符
        return stronger("$lt", v1, v0); // 反向比较，v0更大则更强，等价于v1 < v0
      }
      return false; // 其他情况返回false
    }

    private static Object literalValue(RexLiteral literal) { // 从RexLiteral中提取Java值
      return literal.getValue2(); // 调用getValue2方法获取字面量的实际值
    }

    private Void translateMatch2(RexNode node, List<Map<String, Object>> orMapList, // translateMatch2方法：根据节点类型分发转换逻辑
        Multimap<String, Pair<String, RexLiteral>> multimap, Map<String, RexLiteral> eqMap) { // 参数：节点、OR列表、多值映射、等值映射
      switch (node.getKind()) { // 根据节点类型进行分发
      case EQUALS: // 如果是等于操作
        return translateBinary(null, null, (RexCall) node, multimap, eqMap); // 调用translateBinary，op为null表示等值
      case LESS_THAN: // 如果是小于操作
        return translateBinary("$lt", "$gt", (RexCall) node, multimap, eqMap); // 调用translateBinary，MongoDB操作符为$lt，反向为$gt
      case LESS_THAN_OR_EQUAL: // 如果是小于等于操作
        return translateBinary("$lte", "$gte", (RexCall) node, multimap, eqMap); // 调用translateBinary，MongoDB操作符为$lte，反向为$gte
      case NOT_EQUALS: // 如果是不等于操作
        return translateBinary("$ne", "$ne", (RexCall) node, multimap, eqMap); // 调用translateBinary，MongoDB操作符为$ne
      case GREATER_THAN: // 如果是大于操作
        return translateBinary("$gt", "$lt", (RexCall) node, multimap, eqMap); // 调用translateBinary，MongoDB操作符为$gt，反向为$lt
      case GREATER_THAN_OR_EQUAL: // 如果是大于等于操作
        return translateBinary("$gte", "$lte", (RexCall) node, multimap, eqMap); // 调用translateBinary，MongoDB操作符为$gte，反向为$lte
      case OR: // 如果是OR操作
        return translateOrAddToList(node, orMapList); // 调用translateOrAddToList处理OR条件
      default: // 其他不支持的操作
        throw new AssertionError("cannot translate " + node); // 抛出断言错误，表示无法转换
      }
    }

    private Void translateOrAddToList(RexNode node, List<Map<String, Object>> orMapList) { // translateOrAddToList方法：处理OR条件并添加到列表
      Map<String, Object> or = translateOr(node); // 调用translateOr转换OR条件
      orMapList.add(or); // 将转换后的OR条件添加到orMapList中
      return null; // 返回null，因为返回类型是Void
    }

    /** Translates a call to a binary operator, reversing arguments if
     * necessary. */
    // translateBinary方法：翻译二元操作符调用，必要时交换参数顺序
    private Void translateBinary(String op, String rop, RexCall call, // 参数：MongoDB操作符、反向操作符、RexCall节点
        Multimap<String, Pair<String, RexLiteral>> multimap, Map<String, RexLiteral> eqMap) { // 参数：多值映射、等值映射
      final RexNode left = call.operands.get(0); // 获取左操作数
      final RexNode right = call.operands.get(1); // 获取右操作数
      boolean b = translateBinary2(op, left, right, multimap, eqMap); // 尝试按正常顺序翻译
      if (b) { // 如果翻译成功
        return null; // 直接返回
      }
      b = translateBinary2(rop, right, left, multimap, eqMap); // 尝试按反向顺序翻译，处理字面量在左边的情况
      if (b) { // 如果翻译成功
        return null; // 直接返回
      }
      throw new AssertionError("cannot translate op " + op + " call " + call); // 如果都失败，抛出断言错误
    }

    /** Translates a call to a binary operator. Returns whether successful. */
    // translateBinary2方法：翻译二元操作符调用，返回是否成功
    private boolean translateBinary2(String op, RexNode left, RexNode right, // 参数：MongoDB操作符、左操作数、右操作数
        Multimap<String, Pair<String, RexLiteral>> multimap, Map<String, RexLiteral> eqMap) { // 参数：多值映射、等值映射
      switch (right.getKind()) { // 检查右操作数的类型
      case LITERAL: // 如果是字面量
        break; // 继续处理
      default: // 其他类型
        return false; // 返回false，表示翻译失败
      }
      final RexLiteral rightLiteral = (RexLiteral) right; // 将右操作数转换为RexLiteral
      switch (left.getKind()) { // 检查左操作数的类型
      case INPUT_REF: // 如果是输入字段引用
        final RexInputRef left1 = (RexInputRef) left; // 将左操作数转换为RexInputRef
        String name = fieldNames.get(left1.getIndex()); // 根据索引获取字段名
        translateOp2(op, name, rightLiteral, multimap, eqMap); // 调用translateOp2完成翻译
        return true; // 返回true，表示翻译成功
      case CAST: // 如果是类型转换
        return translateBinary2(op, ((RexCall) left).operands.get(0), right, multimap, eqMap); // 递归处理转换后的表达式
      case ITEM: // 如果是数组元素访问（如field.item）
        String itemName = MongoRules.isItem((RexCall) left); // 获取数组元素的完整名称
        if (itemName != null) { // 如果成功获取名称
          translateOp2(op, itemName, rightLiteral, multimap, eqMap); // 调用translateOp2完成翻译
          return true; // 返回true，表示翻译成功
        }
        // fall through // 继续向下执行，处理其他情况
      default: // 其他类型
        return false; // 返回false，表示翻译失败
      }
    }

    private void translateOp2(String op, String name, RexLiteral right, // translateOp2方法：完成操作符的最终翻译
        Multimap<String, Pair<String, RexLiteral>> multimap, Map<String, RexLiteral> eqMap) { // 参数：操作符、字段名、字面量、多值映射、等值映射
      if (op == null) { // 如果操作符为null，表示等值操作
        // E.g.: {deptno: 100} // 例如：{deptno: 100}
        eqMap.put(name, right); // 将字段名和字面量放入等值映射
      } else { // 如果操作符不为null，表示比较操作
        // E.g. {deptno: {$lt: 100}} // 例如：{deptno: {$lt: 100}}
        // which may later be combined with other conditions: // 可能会与其他条件合并
        // E.g. {deptno: [$lt: 100, $gt: 50]} // 例如：{deptno: [$lt: 100, $gt: 50]}
        multimap.put(name, Pair.of(op, right)); // 将字段名、操作符和字面量放入多值映射
      }
    }
  }
}
