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
package org.apache.calcite.rel.logical;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelInput;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelShuttle;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.util.ImmutableBitSet;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

/**
 * <code>LogicalAggregate</code> is a relational operator which eliminates
 * duplicates and computes totals.
 *
 * <p>Rules:
 *
 * <ul>
 * <li>{@link org.apache.calcite.rel.rules.AggregateProjectPullUpConstantsRule}
 * <li>{@link org.apache.calcite.rel.rules.AggregateExpandDistinctAggregatesRule}
 * <li>{@link org.apache.calcite.rel.rules.AggregateReduceFunctionsRule}.
 * </ul>
 * 
 * LogicalAggregate是Calcite中用于表示逻辑聚合操作的关系运算符节点
 * 它是SQL中GROUP BY子句和聚合函数（如SUM、COUNT、AVG等）在关系代数中的表示
 * 主要功能包括：
 * 1. 根据指定的分组字段（GROUP BY）对输入数据进行分组
 * 2. 对每个分组计算聚合函数的值
 * 3. 消除重复行（当没有聚合函数时相当于DISTINCT）
 * 
 * 继承自Aggregate基类，实现了逻辑层面的聚合操作
 * 与物理聚合节点（如EnumerableAggregate）不同，LogicalAggregate只描述逻辑结构
 * 不关心具体的执行方式和实现细节
 * 
 * 支持的特性：
 * - 基本分组（GROUP BY）
 * - 聚合函数（SUM、COUNT、AVG、MIN、MAX等）
 * - 分组集合（GROUPING SETS）
 * - ROLLUP和CUBE（通过分组集合实现）
 * 
 * 在优化过程中，LogicalAggregate会被转换为适合特定数据源的物理实现
 */
public final class LogicalAggregate extends Aggregate {
  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a LogicalAggregate.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   *
   * @param cluster    Cluster that this relational expression belongs to
   * @param traitSet   Traits
   * @param hints      Hints for this relational expression
   * @param input      Input relational expression
   * @param groupSet Bit set of grouping fields
   * @param groupSets Grouping sets, or null to use just {@code groupSet}
   * @param aggCalls Array of aggregates to compute, not null
   */
  public LogicalAggregate(
      RelOptCluster cluster,  // 关系表达式所属的集群，包含类型系统、表达式工厂等共享资源
      RelTraitSet traitSet,   // 关系表达式的特征集合，定义了物理属性（如约定、排序、分布等）
      List<RelHint> hints,    // 优化器提示列表，用于指导查询优化过程
      RelNode input,          // 输入关系表达式，通常是LogicalProject或其他产生数据的关系节点
      ImmutableBitSet groupSet,  // 分组字段的位集合，使用位集合表示哪些字段用于GROUP BY，例如{0,2}表示第0和第2个字段
      @Nullable List<ImmutableBitSet> groupSets,  // 分组集合列表，支持GROUPING SETS、ROLLUP、CUBE等高级分组特性，null表示只使用groupSet
      List<AggregateCall> aggCalls) {  // 聚合函数调用列表，每个AggregateCall代表一个聚合函数（如SUM(col1)、COUNT(*)等）
    super(cluster, traitSet, hints, input, groupSet, groupSets, aggCalls);  // 调用父类Aggregate的构造方法初始化聚合节点
  }

  @Deprecated // to be removed before 2.0  // 标记为废弃，将在2.0版本前移除
  public LogicalAggregate(
      RelOptCluster cluster,  // 关系表达式所属的集群
      RelTraitSet traitSet,   // 关系表达式的特征集合
      RelNode input,          // 输入关系表达式
      ImmutableBitSet groupSet,  // 分组字段的位集合
      List<ImmutableBitSet> groupSets,  // 分组集合列表
      List<AggregateCall> aggCalls) {  // 聚合函数调用列表
    this(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls);  // 调用完整的构造方法，hints参数传空列表
  }

  @Deprecated // to be removed before 2.0  // 标记为废弃，将在2.0版本前移除
  public LogicalAggregate(RelOptCluster cluster, RelTraitSet traitSet,  // 集群和特征集合
      RelNode input, boolean indicator, ImmutableBitSet groupSet,  // 输入关系、indicator标志（已废弃）、分组字段位集合
      List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) {  // 分组集合列表、聚合函数调用列表
    super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls);  // 调用父类构造方法
    checkIndicator(indicator);  // 检查indicator参数，确保为false（因为indicator特性已被废弃）
  }

  @Deprecated // to be removed before 2.0  // 标记为废弃，将在2.0版本前移除
  public LogicalAggregate(RelOptCluster cluster,  // 集群
      RelNode input, boolean indicator, ImmutableBitSet groupSet,  // 输入关系、indicator标志（已废弃）、分组字段位集合
      List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) {  // 分组集合列表、聚合函数调用列表
    super(cluster, cluster.traitSetOf(Convention.NONE), ImmutableList.of(), input, groupSet,  // 使用默认的Convention.NONE特征集
        groupSets, aggCalls);  // 调用父类构造方法
    checkIndicator(indicator);  // 检查indicator参数，确保为false（因为indicator特性已被废弃）
  }

  /**
   * Creates a LogicalAggregate by parsing serialized output.
   */
  public LogicalAggregate(RelInput input) {  // RelInput包含序列化后的关系表达式数据，用于从JSON或其他格式反序列化
    super(input);  // 调用父类Aggregate的构造方法，从RelInput中读取cluster、traitSet、hints、input、groupSet、groupSets、aggCalls等参数
  }

  /** Creates a LogicalAggregate. */
  public static LogicalAggregate create(final RelNode input,  // 输入关系表达式
      List<RelHint> hints,  // 优化器提示列表
      ImmutableBitSet groupSet,  // 分组字段的位集合
      @Nullable List<ImmutableBitSet> groupSets,  // 分组集合列表（可为null）
      List<AggregateCall> aggCalls) {  // 聚合函数调用列表
    return create_(input, hints, groupSet, groupSets, aggCalls);  // 调用私有的create_方法创建LogicalAggregate实例
  }

  @Deprecated // to be removed before 2.0  // 标记为废弃，将在2.0版本前移除
  public static LogicalAggregate create(final RelNode input,  // 输入关系表达式
      ImmutableBitSet groupSet,  // 分组字段的位集合
      List<ImmutableBitSet> groupSets,  // 分组集合列表
      List<AggregateCall> aggCalls) {  // 聚合函数调用列表
    return create_(input, ImmutableList.of(), groupSet, groupSets, aggCalls);  // 调用create_方法，hints参数传空列表
  }

  @Deprecated // to be removed before 2.0  // 标记为废弃，将在2.0版本前移除
  public static LogicalAggregate create(final RelNode input,  // 输入关系表达式
      boolean indicator,  // indicator标志（已废弃）
      ImmutableBitSet groupSet,  // 分组字段的位集合
      List<ImmutableBitSet> groupSets,  // 分组集合列表
      List<AggregateCall> aggCalls) {  // 聚合函数调用列表
    checkIndicator(indicator);  // 检查indicator参数，确保为false（因为indicator特性已被废弃）
    return create_(input, ImmutableList.of(), groupSet, groupSets, aggCalls);  // 调用create_方法，hints参数传空列表
  }

  private static LogicalAggregate create_(final RelNode input,  // 输入关系表达式
      List<RelHint> hints,  // 优化器提示列表
      ImmutableBitSet groupSet,  // 分组字段的位集合
      @Nullable List<ImmutableBitSet> groupSets,  // 分组集合列表（可为null）
      List<AggregateCall> aggCalls) {  // 聚合函数调用列表
    final RelOptCluster cluster = input.getCluster();  // 从输入节点获取集群对象，包含类型系统等共享资源
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE);  // 创建特征集合，使用Convention.NONE表示这是逻辑节点
    return new LogicalAggregate(cluster, traitSet, hints, input, groupSet,  // 创建并返回LogicalAggregate实例
        groupSets, aggCalls);
  }

  //~ Methods ----------------------------------------------------------------

  @Override public LogicalAggregate copy(RelTraitSet traitSet, RelNode input,  // 复制方法，创建一个新的LogicalAggregate实例，可以修改特征集、输入、分组和聚合函数
      ImmutableBitSet groupSet,  // 新的分组字段位集合
      @Nullable List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) {  // 新的分组集合列表和聚合函数调用列表
    assert traitSet.containsIfApplicable(Convention.NONE);  // 断言特征集包含Convention.NONE，确保这是逻辑节点
    return new LogicalAggregate(getCluster(), traitSet, hints, input,  // 创建并返回新的LogicalAggregate实例，保留原有的cluster和hints
        groupSet, groupSets, aggCalls);  // 使用新的参数创建实例
  }

  @Override public RelNode accept(RelShuttle shuttle) {  // 接受访问者模式的方法，允许RelShuttle遍历和修改关系表达式树
    return shuttle.visit(this);  // 调用RelShuttle的visit方法，将当前LogicalAggregate节点传递给访问者进行处理
  }

  @Override public RelNode withHints(List<RelHint> hintList) {  // 创建一个新的LogicalAggregate实例，使用指定的提示列表，其他属性保持不变
    return new LogicalAggregate(getCluster(), traitSet, hintList, input,  // 创建并返回新的LogicalAggregate实例，使用新的hintList
        groupSet, groupSets, aggCalls);  // 保留原有的cluster、traitSet、input、groupSet、groupSets和aggCalls
  }
}
