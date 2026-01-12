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
package org.apache.calcite.adapter.mongodb; // 声明包名，表示该类属于MongoDB适配器包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系代数优化器集群
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系代数特征集合
import org.apache.calcite.rel.InvalidRelException; // 导入InvalidRelException类，用于表示无效的关系代数异常
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数节点接口
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类，表示聚合关系代数表达式
import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类，表示聚合函数调用
import org.apache.calcite.sql.SqlAggFunction; // 导入SqlAggFunction接口，表示SQL聚合函数
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.sql.fun.SqlSumAggFunction; // 导入SqlSumAggFunction类，表示SUM聚合函数
import org.apache.calcite.sql.fun.SqlSumEmptyIsZeroAggFunction; // 导入SqlSumEmptyIsZeroAggFunction类，表示空值为零的SUM聚合函数
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合
import org.apache.calcite.util.Util; // 导入Util类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入ImmutableList类，表示不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型

import java.util.AbstractList; // 导入AbstractList类，表示抽象列表
import java.util.ArrayList; // 导入ArrayList类，表示动态数组列表
import java.util.List; // 导入List接口，表示列表集合

/**
 * Implementation of
 * {@link org.apache.calcite.rel.core.Aggregate} relational expression
 * in MongoDB.
 * // MongoDB中聚合关系代数表达式的实现类
 * // 该类负责将Calcite的聚合操作转换为MongoDB的聚合管道操作
 * // 支持的聚合函数包括：COUNT、SUM、MIN、MAX、AVG
 * // 支持单列和多列分组操作
 * // 不支持DISTINCT聚合和复杂的分组类型（如ROLLUP、CUBE等）
 */
public class MongoAggregate
    extends Aggregate // 继承Aggregate基类，表示这是一个聚合关系节点
    implements MongoRel { // 实现MongoRel接口，表示这是一个MongoDB特定的关系节点
  public MongoAggregate( // 构造方法：创建MongoAggregate实例
      RelOptCluster cluster, // 参数：关系代数优化器集群，包含优化器上下文信息
      RelTraitSet traitSet, // 参数：关系代数特征集合，定义该节点的物理属性（如MongoDB约定）
      RelNode input, // 参数：输入关系节点，表示聚合操作的数据源
      ImmutableBitSet groupSet, // 参数：分组字段集合，使用位集合表示哪些字段用于分组
      @Nullable List<ImmutableBitSet> groupSets, // 参数：分组集合列表，用于多级分组（如GROUPING SETS），可为null
      List<AggregateCall> aggCalls) // 参数：聚合函数调用列表，包含所有要执行的聚合操作
      throws InvalidRelException { // 声明可能抛出无效关系异常
    super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls); // 调用父类Aggregate的构造方法，初始化聚合节点
    assert getConvention() == MongoRel.CONVENTION; // 断言：确保当前节点的约定是MongoDB约定
    assert getConvention() == input.getConvention(); // 断言：确保输入节点的约定也是MongoDB约定，保证约定一致性

    for (AggregateCall aggCall : aggCalls) { // 遍历所有聚合函数调用
      if (aggCall.isDistinct()) { // 检查是否是DISTINCT聚合（如COUNT(DISTINCT x)）
        throw new InvalidRelException( // 抛出异常：MongoDB不支持DISTINCT聚合
            "distinct aggregation not supported"); // 异常消息：不支持distinct聚合
      }
    }
    switch (getGroupType()) { // 根据分组类型进行判断
    case SIMPLE: // 如果是简单分组（GROUP BY）
      break; // 支持简单分组，不做任何处理
    default: // 其他分组类型（如ROLLUP、CUBE、GROUPING SETS）
      throw new InvalidRelException("unsupported group type: " // 抛出异常：不支持复杂分组类型
          + getGroupType()); // 在异常消息中包含具体的分组类型
    }
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public MongoAggregate(RelOptCluster cluster, RelTraitSet traitSet, // 废弃的构造方法：用于向后兼容
      RelNode input, boolean indicator, ImmutableBitSet groupSet, // 参数：indicator参数已废弃，Calcite不再使用
      List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) // 其他参数与主构造方法相同
      throws InvalidRelException { // 声明可能抛出无效关系异常
    this(cluster, traitSet, input, groupSet, groupSets, aggCalls); // 调用主构造方法，忽略indicator参数
    checkIndicator(indicator); // 检查indicator参数（应该为false），用于向后兼容验证
  }

  @Override public Aggregate copy(RelTraitSet traitSet, RelNode input, // 重写copy方法：创建当前节点的副本
      ImmutableBitSet groupSet, @Nullable List<ImmutableBitSet> groupSets, // 参数：新的特征集合、输入节点、分组集合等
      List<AggregateCall> aggCalls) { // 参数：新的聚合函数调用列表
    try { // 尝试创建新的MongoAggregate实例
      return new MongoAggregate(getCluster(), traitSet, input, // 返回新的MongoAggregate对象，使用当前集群和新的参数
          groupSet, groupSets, aggCalls); // 传入新的分组集合和聚合调用列表
    } catch (InvalidRelException e) { // 捕获无效关系异常
      // Semantic error not possible. Must be a bug. Convert to
      // internal error.
      // 语义错误不可能发生，这必须是bug。转换为内部错误
      throw new AssertionError(e); // 抛出断言错误，表示这是一个内部bug
    }
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法：将聚合操作转换为MongoDB聚合管道
    implementor.visitChild(0, getInput()); // 访问并实现子节点（输入关系），确保子节点的MongoDB操作先被执行
    List<String> list = new ArrayList<>(); // 创建列表：用于存储$group操作的字段映射
    final List<String> inNames = // 获取输入字段的MongoDB字段名列表
        MongoRules.mongoFieldNames(getInput().getRowType()); // 从输入关系类型中提取字段名
    final List<String> outNames = // 获取输出字段的MongoDB字段名列表
        MongoRules.mongoFieldNames(getRowType()); // 从输出关系类型中提取字段名
    int i = 0; // 初始化索引：用于遍历输出字段
    if (groupSet.cardinality() == 1) { // 如果只有一个分组字段（单列分组）
      final String inName = inNames.get(groupSet.nth(0)); // 获取分组字段的输入名称
      list.add("_id: " + MongoRules.maybeQuote("$" + inName)); // 添加到列表：MongoDB的$group操作使用_id字段存储分组键
      ++i; // 索引递增
    } else { // 如果有多个分组字段（多列分组）
      List<String> keys = new ArrayList<>(); // 创建列表：存储多列分组的键值对
      for (int group : groupSet) { // 遍历所有分组字段
        final String inName = inNames.get(group); // 获取当前分组字段的输入名称
        keys.add(inName + ": " + MongoRules.quote("$" + inName)); // 添加键值对：字段名和对应的MongoDB字段引用
        ++i; // 索引递增
      }
      list.add("_id: " + Util.toString(keys, "{", ", ", "}")); // 添加到列表：将多列分组构建为MongoDB对象格式
    }
    for (AggregateCall aggCall : aggCalls) { // 遍历所有聚合函数调用
      list.add( // 添加聚合字段映射
          MongoRules.maybeQuote(outNames.get(i++)) + ": " // 输出字段名，引用聚合结果
          + toMongo(aggCall.getAggregation(), inNames, aggCall.getArgList())); // 调用toMongo方法将聚合函数转换为MongoDB语法
    }
    implementor.add(null, // 添加$group操作到MongoDB聚合管道
        "{$group: " + Util.toString(list, "{", ", ", "}") + "}"); // 构建MongoDB的$group阶段，包含分组键和聚合函数
    final List<String> fixups; // 声明列表：用于存储$project操作的修复逻辑，用于调整输出字段格式
    if (groupSet.cardinality() == 1) { // 如果是单列分组
      fixups = new AbstractList<String>() { // 创建抽象列表：延迟计算字段映射
        @Override public String get(int index) { // 重写get方法：根据索引返回字段映射
          final String outName = outNames.get(index); // 获取输出字段名
          return MongoRules.maybeQuote(outName) + ": " // 返回字段映射：输出字段名映射到对应的MongoDB字段
              + MongoRules.maybeQuote("$" + (index == 0 ? "_id" : outName)); // 第一个字段映射到_id，其他字段映射到自身
        }

        @Override public int size() { // 重写size方法：返回输出字段数量
          return outNames.size(); // 返回输出字段列表的大小
        }
      };
    } else { // 如果是多列分组
      fixups = new ArrayList<>(); // 创建列表：存储字段修复映射
      fixups.add("_id: 0"); // 添加：排除_id字段（MongoDB的$group会生成_id）
      i = 0; // 重置索引
      for (int group : groupSet) { // 遍历所有分组字段
        fixups.add( // 添加字段映射：从_id对象中提取分组字段
            MongoRules.maybeQuote(outNames.get(group)) // 输出字段名
            + ": " // 冒号分隔
            + MongoRules.maybeQuote("$_id." + outNames.get(group))); // 引用_id对象中的对应字段
        ++i; // 索引递增
      }
      for (AggregateCall ignored : aggCalls) { // 遍历所有聚合函数调用（ignored表示不使用该变量）
        final String outName = outNames.get(i++); // 获取输出字段名并递增索引
        fixups.add( // 添加字段映射：聚合结果字段映射到自身
            MongoRules.maybeQuote(outName) + ": " + MongoRules.maybeQuote( // 输出字段名映射到对应的MongoDB字段
                "$" + outName)); // 引用聚合结果字段
      }
    }
    if (!groupSet.isEmpty()) { // 如果存在分组字段（非空分组）
      implementor.add(null, // 添加$project操作到MongoDB聚合管道
          "{$project: " + Util.toString(fixups, "{", ", ", "}") + "}"); // 构建MongoDB的$project阶段，调整输出字段格式
    }
  }

  private static String toMongo(SqlAggFunction aggregation, List<String> inNames, // 私有静态方法：将SQL聚合函数转换为MongoDB聚合表达式
      List<Integer> args) { // 参数：aggregation为SQL聚合函数，inNames为输入字段名列表，args为聚合函数参数索引列表
    if (aggregation == SqlStdOperatorTable.COUNT) { // 如果是COUNT聚合函数
      if (args.isEmpty()) { // 如果是COUNT(*)（无参数）
        return "{$sum: 1}"; // 返回：MongoDB的$sum操作，对每行计数1
      } else { // 如果是COUNT(column)（有参数）
        assert args.size() == 1; // 断言：COUNT函数只有一个参数
        final String inName = inNames.get(args.get(0)); // 获取参数对应的输入字段名
        return "{$sum: {$cond: [ {$eq: [" // 返回：使用$sum和$cond组合，只统计非null值
            + MongoRules.quote(inName) // 引用字段名
            + ", null]}, 0, 1]}}"; // 如果字段值为null则计数0，否则计数1
      }
    } else if (aggregation instanceof SqlSumAggFunction // 如果是SUM聚合函数
        || aggregation instanceof SqlSumEmptyIsZeroAggFunction) { // 或者是空值为零的SUM聚合函数
      assert args.size() == 1; // 断言：SUM函数只有一个参数
      final String inName = inNames.get(args.get(0)); // 获取参数对应的输入字段名
      return "{$sum: " + MongoRules.maybeQuote("$" + inName) + "}"; // 返回：MongoDB的$sum操作，对字段值求和
    } else if (aggregation == SqlStdOperatorTable.MIN) { // 如果是MIN聚合函数
      assert args.size() == 1; // 断言：MIN函数只有一个参数
      final String inName = inNames.get(args.get(0)); // 获取参数对应的输入字段名
      return "{$min: " + MongoRules.maybeQuote("$" + inName) + "}"; // 返回：MongoDB的$min操作，求字段最小值
    } else if (aggregation == SqlStdOperatorTable.MAX) { // 如果是MAX聚合函数
      assert args.size() == 1; // 断言：MAX函数只有一个参数
      final String inName = inNames.get(args.get(0)); // 获取参数对应的输入字段名
      return "{$max: " + MongoRules.maybeQuote("$" + inName) + "}"; // 返回：MongoDB的$max操作，求字段最大值
    } else if (aggregation == SqlStdOperatorTable.AVG) { // 如果是AVG聚合函数
      assert args.size() == 1; // 断言：AVG函数只有一个参数
      final String inName = inNames.get(args.get(0)); // 获取参数对应的输入字段名
      return "{$avg: " + MongoRules.maybeQuote("$" + inName) + "}"; // 返回：MongoDB的$avg操作，求字段平均值
    } else { // 其他不支持的聚合函数
      throw new AssertionError("unknown aggregate " + aggregation); // 抛出断言错误：未知的聚合函数
    }
  }
} // 类结束
