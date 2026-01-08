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
package org.apache.calcite.materialize; // 定义包名，该类位于materialize包中，用于处理物化视图相关的功能

import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化器中的表对象
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型中的字段
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示关系表达式节点，用于表示SQL表达式
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法
import org.apache.calcite.util.graph.AttributedDirectedGraph; // 导入AttributedDirectedGraph类，表示带属性的有向图
import org.apache.calcite.util.mapping.IntPair; // 导入IntPair类，表示整数对，用于映射字段索引

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变列表

import org.checkerframework.checker.initialization.qual.NotOnlyInitialized; // 导入CheckerFramework注解，用于初始化检查

import java.util.ArrayList; // 导入ArrayList类，表示动态数组列表
import java.util.HashMap; // 导入HashMap类，表示哈希映射
import java.util.HashSet; // 导入HashSet类，表示哈希集合
import java.util.List; // 导入List接口，表示有序列表
import java.util.Map; // 导入Map接口，表示键值对映射
import java.util.Set; // 导入Set接口，表示不重复元素的集合
import java.util.TreeSet; // 导入TreeSet类，表示基于树的有序集合

import static java.util.Objects.requireNonNull; // 导入Objects类的requireNonNull静态方法，用于非空检查

/** Space within which lattices exist. */ // 类注释：LatticeSpace表示立方体（Lattice）存在的空间
class LatticeSpace { // 定义LatticeSpace类，用于管理立方体相关的表、边、路径等信息
  final SqlStatisticProvider statisticProvider; // 成员变量：SQL统计信息提供者，用于获取表的统计信息（如行数、列数等），final表示不可变
  private final Map<List<String>, LatticeTable> tableMap = new HashMap<>(); // 成员变量：表映射，键为表的限定名（如["schema", "table"]），值为LatticeTable对象，用于缓存已注册的表
  @SuppressWarnings("assignment.type.incompatible") // 注解：忽略类型不兼容的警告，因为Checker框架可能无法正确识别循环依赖的初始化
  final @NotOnlyInitialized AttributedDirectedGraph<LatticeTable, Step> g = // 成员变量：有向图，顶点是LatticeTable（表），边是Step（连接步骤），用于表示表之间的连接关系
      new AttributedDirectedGraph<>(new Step.Factory(this)); // 创建有向图对象，使用Step.Factory作为边的工厂，传入this作为LatticeSpace实例
  private final Map<List<String>, String> simpleTableNames = new HashMap<>(); // 成员变量：简单表名映射，键为表的限定名，值为简单表名（可能带schema前缀），用于生成唯一的表名
  private final Set<String> simpleNames = new HashSet<>(); // 成员变量：已使用的简单表名集合，用于快速检查表名是否已被使用
  /** Root nodes, indexed by digest. */ // 注释：根节点映射，通过摘要（digest）索引
  final Map<String, LatticeRootNode> nodeMap = new HashMap<>(); // 成员变量：根节点映射，键为节点的摘要字符串，值为LatticeRootNode对象，用于存储立方体的根节点
  final Map<ImmutableList<Step>, Path> pathMap = new HashMap<>(); // 成员变量：路径映射，键为Step的不可变列表（表示路径上的步骤集），值为Path对象，用于缓存已创建的路径
  final Map<LatticeTable, List<RexNode>> tableExpressions = new HashMap<>(); // 成员变量：表表达式映射，键为LatticeTable，值为RexNode列表（表示该表的派生列表达式），用于存储表的派生列

  LatticeSpace(SqlStatisticProvider statisticProvider) { // 构造方法：创建LatticeSpace实例
    this.statisticProvider = // 初始化statisticProvider成员变量
        requireNonNull(statisticProvider, "statisticProvider"); // 使用requireNonNull确保statisticProvider不为null，否则抛出NullPointerException
  }

  /** Derives a unique name for a table, qualifying with schema name only if
   * necessary. */ // 方法注释：为表派生唯一的名称，仅在必要时添加schema名称前缀
  String simpleName(LatticeTable table) { // 方法：获取LatticeTable的简单名称
    return simpleName(table.t.getQualifiedName()); // 调用重载的simpleName方法，传入表的限定名（如["sales", "emps"]）
  }

  String simpleName(RelOptTable table) { // 方法：获取RelOptTable的简单名称
    return simpleName(table.getQualifiedName()); // 调用重载的simpleName方法，传入表的限定名
  }

  String simpleName(List<String> table) { // 方法：根据表的限定名列表生成简单名称
    final String name = simpleTableNames.get(table); // 从缓存中获取该表已有简单名称
    if (name != null) { // 如果缓存中存在
      return name; // 直接返回缓存的名称
    }
    final String name2 = Util.last(table); // 获取限定名的最后一部分（表名本身），如["sales", "emps"] -> "emps"
    if (simpleNames.add(name2)) { // 尝试将表名添加到已使用名称集合，如果返回true表示该名称未被使用
      simpleTableNames.put(ImmutableList.copyOf(table), name2); // 将表的限定名映射到这个简单名称，使用不可变列表作为键
      return name2; // 返回简单名称（仅表名）
    }
    final String name3 = table.toString(); // 如果表名已被使用，则使用完整限定名（如"[sales, emps]"）作为简单名称
    simpleTableNames.put(ImmutableList.copyOf(table), name3); // 将表的限定名映射到完整限定名
    return name3; // 返回完整限定名
  }

  LatticeTable register(RelOptTable t) { // 方法：注册一个表到LatticeSpace中，返回对应的LatticeTable对象
    final LatticeTable table = tableMap.get(t.getQualifiedName()); // 从缓存中查找是否已注册该表
    if (table != null) { // 如果表已存在
      return table; // 直接返回缓存的LatticeTable对象
    }
    final LatticeTable table2 = new LatticeTable(t); // 创建新的LatticeTable对象，包装原始的RelOptTable
    tableMap.put(t.getQualifiedName(), table2); // 将新表添加到缓存映射中
    g.addVertex(table2); // 在有向图中添加该表作为顶点
    return table2; // 返回新创建的LatticeTable对象
  }

  Step addEdge(LatticeTable source, LatticeTable target, List<IntPair> keys) { // 方法：在两个表之间添加一条边（连接），source是源表，target是目标表，keys是连接键对列表
    keys = sortUnique(keys); // 对连接键对进行排序和去重，确保顺序一致且无重复
    final Step step = g.addEdge(source, target, keys); // 尝试在有向图中添加边，使用连接键对作为边的属性
    if (step != null) { // 如果添加成功且边不存在
      return step; // 返回新创建的Step对象
    }
    for (Step step2 : g.getEdges(source, target)) { // 如果添加失败，遍历源表到目标表之间的所有边
      if (step2.keys.equals(keys)) { // 查找连接键对相同的边
        return step2; // 返回已存在的Step对象
      }
    }
    throw new AssertionError("addEdge failed, yet no edge present"); // 如果既没添加成功也没找到现有边，抛出断言错误（不应该发生）
  }

  /** Returns a list of {@link IntPair} that is sorted and unique. */ // 方法注释：返回排序且唯一的IntPair列表
  static List<IntPair> sortUnique(List<IntPair> keys) { // 方法：对IntPair列表进行排序和去重，静态方法可在任何地方调用
    if (keys.size() > 1) { // 如果列表中有多于一个元素
      // list may not be sorted; sort it // 注释：列表可能未排序，需要排序
      keys = IntPair.ORDERING.immutableSortedCopy(keys); // 使用IntPair的排序规则创建不可变的排序列表副本
      if (!IntPair.ORDERING.isStrictlyOrdered(keys)) { // 检查列表是否严格有序（无重复）
        // list may contain duplicates; sort and eliminate duplicates // 注释：列表可能包含重复，需要排序并消除重复
        final Set<IntPair> set = new TreeSet<>(IntPair.ORDERING); // 创建TreeSet，使用相同的排序规则
        set.addAll(keys); // 将所有IntPair添加到TreeSet中（自动去重）
        keys = ImmutableList.copyOf(set); // 将TreeSet转换为不可变的ImmutableList
      }
    }
    return keys; // 返回排序且唯一的列表
  }

  /** Returns a list of {@link IntPair}, transposing source and target fields,
   * and ensuring the result is sorted and unique. */ // 方法注释：返回IntPair列表，交换源和目标字段，并确保结果排序且唯一
  static List<IntPair> swap(List<IntPair> keys) { // 方法：交换每个IntPair中的源和目标字段索引（如(0,1)变为(1,0)），静态方法
    return sortUnique(Util.transform(keys, x -> IntPair.of(x.target, x.source))); // 使用Util.transform转换每个IntPair，交换source和target，然后调用sortUnique确保排序唯一
  }

  Path addPath(List<Step> steps) { // 方法：添加一条路径，路径由多个Step组成，返回Path对象
    final ImmutableList<Step> key = ImmutableList.copyOf(steps); // 将Step列表转换为不可变的ImmutableList，作为路径的唯一标识
    final Path path = pathMap.get(key); // 从缓存中查找是否已存在该路径
    if (path != null) { // 如果路径已存在
      return path; // 直接返回缓存的Path对象
    }
    final Path path2 = new Path(key, pathMap.size()); // 创建新的Path对象，传入步骤列表和路径索引（当前路径映射的大小）
    pathMap.put(key, path2); // 将新路径添加到缓存映射中
    return path2; // 返回新创建的Path对象
  }

  /** Registers an expression as a derived column of a given table.
   *
   * <p>Its ordinal is the number of fields in the row type plus the ordinal
   * of the extended expression. For example, if a table has 10 fields then its
   * derived columns will have ordinals 10, 11, 12 etc. */ // 方法注释：将表达式注册为给定表的派生列，其序号是行类型中的字段数加上扩展表达式的序号，例如表有10个字段，派生列的序号将是10、11、12等
  int registerExpression(LatticeTable table, RexNode e) { // 方法：注册一个表达式为表的派生列，返回该派生列的字段序号
    final List<RexNode> expressions = // 获取或创建该表的派生列表达式列表
        tableExpressions.computeIfAbsent(table, t -> new ArrayList<>()); // 如果table不存在于映射中，则创建新的ArrayList
    final int fieldCount = table.t.getRowType().getFieldCount(); // 获取表原始行类型的字段总数
    for (int i = 0; i < expressions.size(); i++) { // 遍历已有的派生列表达式
      if (expressions.get(i).toString().equals(e.toString())) { // 如果找到相同的表达式（通过字符串比较）
        return fieldCount + i; // 返回该派生列的序号（原始字段数 + 偏移量）
      }
    }
    final int result = fieldCount + expressions.size(); // 计算新派生列的序号（原始字段数 + 当前派生列数量）
    expressions.add(e); // 将新表达式添加到派生列列表
    return result; // 返回新派生列的序号
  }

  /** Returns the name of field {@code field} of {@code table}.
   *
   * <p>If the field is derived (see
   * {@link #registerExpression(LatticeTable, RexNode)}) its name is its
   * {@link RexNode#toString()}. */ // 方法注释：返回表中指定字段的名称，如果是派生列（见registerExpression方法），其名称是RexNode的字符串表示
  public String fieldName(LatticeTable table, int field) { // 方法：获取表中指定序号字段的名称
    final List<RelDataTypeField> fieldList = // 获取表行类型的字段列表
        table.t.getRowType().getFieldList(); // 返回所有原始字段的列表
    final int fieldCount = fieldList.size(); // 获取原始字段总数
    if (field < fieldCount) { // 如果字段序号小于原始字段数（即原始字段）
      return fieldList.get(field).getName(); // 返回该字段的名称
    } else { // 如果字段序号大于等于原始字段数（即派生列）
      List<RexNode> rexNodes = tableExpressions.get(table); // 获取该表的派生列表达式列表
      if (rexNodes == null) { // 如果没有找到派生列表达式
        throw new AssertionError("no expressions found for table " + table); // 抛出断言错误（不应该发生）
      }
      return rexNodes.get(field - fieldCount).toString(); // 返回对应派生列表达式的字符串表示（序号减去原始字段数得到偏移量）
    }
  }
} // 类结束
