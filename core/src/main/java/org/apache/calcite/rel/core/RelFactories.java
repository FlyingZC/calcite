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
package org.apache.calcite.rel.core; // 包声明：RelFactories类位于org.apache.calcite.rel.core包中，该包包含Calcite关系代数核心类

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，用于标记仍在开发或测试阶段的API
import org.apache.calcite.plan.Context; // 导入上下文接口，用于在优化器中传递配置和状态信息
import org.apache.calcite.plan.Contexts; // 导入上下文工具类，用于创建和管理上下文对象
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群类，包含查询优化过程中共享的资源
import org.apache.calcite.plan.RelOptSamplingParameters; // 导入采样参数类，定义表采样的相关参数
import org.apache.calcite.plan.RelOptTable; // 导入优化表接口，表示优化过程中的表对象
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类，定义关系的物理和逻辑特征
import org.apache.calcite.rel.RelCollation; // 导入关系排序类，定义关系的排序规则
import org.apache.calcite.rel.RelDistribution; // 导入关系分布类，定义数据在集群中的分布方式
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系代数操作符的基类
import org.apache.calcite.rel.hint.RelHint; // 导入关系提示类，用于向优化器提供优化建议
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入逻辑聚合类，表示聚合操作
import org.apache.calcite.rel.logical.LogicalAsofJoin; // 导入逻辑ASOF连接类，表示ASOF连接操作
import org.apache.calcite.rel.logical.LogicalCorrelate; // 导入逻辑关联类，表示关联操作
import org.apache.calcite.rel.logical.LogicalExchange; // 导入逻辑交换类，表示数据交换操作
import org.apache.calcite.rel.logical.LogicalFilter; // 导入逻辑过滤类，表示过滤操作
import org.apache.calcite.rel.logical.LogicalIntersect; // 导入逻辑交集类，表示交集操作
import org.apache.calcite.rel.logical.LogicalJoin; // 导入逻辑连接类，表示连接操作
import org.apache.calcite.rel.logical.LogicalMatch; // 导入逻辑匹配类，表示模式匹配操作
import org.apache.calcite.rel.logical.LogicalMinus; // 导入逻辑差集类，表示差集操作
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影类，表示投影操作
import org.apache.calcite.rel.logical.LogicalRepeatUnion; // 导入逻辑重复联合类，表示迭代操作
import org.apache.calcite.rel.logical.LogicalSnapshot; // 导入逻辑快照类，表示快照操作
import org.apache.calcite.rel.logical.LogicalSort; // 导入逻辑排序类，表示排序操作
import org.apache.calcite.rel.logical.LogicalSortExchange; // 导入逻辑排序交换类，表示排序后交换操作
import org.apache.calcite.rel.logical.LogicalTableFunctionScan; // 导入逻辑表函数扫描类，表示表函数扫描操作
import org.apache.calcite.rel.logical.LogicalTableScan; // 导入逻辑表扫描类，表示表扫描操作
import org.apache.calcite.rel.logical.LogicalTableSpool; // 导入逻辑表缓存类，表示表缓存操作
import org.apache.calcite.rel.logical.LogicalUnion; // 导入逻辑联合类，表示联合操作
import org.apache.calcite.rel.logical.LogicalValues; // 导入逻辑值类，表示常量值操作
import org.apache.calcite.rel.metadata.RelColumnMapping; // 导入关系列映射类，定义列之间的映射关系
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，定义关系的类型信息
import org.apache.calcite.rex.RexCall; // 导入Rex调用类，表示表达式树中的函数调用
import org.apache.calcite.rex.RexCallBinding; // 导入Rex调用绑定类，用于类型检查和推断
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量类，表示常量表达式
import org.apache.calcite.rex.RexNode; // 导入Rex节点接口，所有行表达式的基类
import org.apache.calcite.sql.SqlKind; // 导入SQL种类枚举，定义SQL操作符的类型
import org.apache.calcite.sql.SqlOperatorBinding; // 导入SQL操作符绑定类，用于操作符的类型检查
import org.apache.calcite.sql.SqlTableFunction; // 导入SQL表函数接口，定义表函数的行为
import org.apache.calcite.sql.type.SqlReturnTypeInference; // 导入SQL返回类型推断接口，用于推断函数返回类型
import org.apache.calcite.tools.RelBuilder; // 导入关系构建器类，用于构建关系表达式树
import org.apache.calcite.tools.RelBuilderFactory; // 导入关系构建器工厂接口，用于创建RelBuilder实例
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合类，用于高效表示列索引集合

import com.google.common.collect.ImmutableList; // 导入Google Guava不可变列表类，提供线程安全的不可变列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava不可变集合类，提供线程安全的不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.lang.reflect.Type; // 导入Java类型类，用于表示Java类型
import java.util.List; // 导入列表接口，表示有序集合
import java.util.Map; // 导入映射接口，表示键值对集合
import java.util.Set; // 导入集合接口，表示不重复元素的集合
import java.util.SortedSet; // 导入有序集合接口，表示有序且不重复的集合

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于参数非空检查

/**
 * Contains factory interface and default implementation for creating various
 * rel nodes.
 * 包含用于创建各种关系节点的工厂接口和默认实现。
 * 
 * 这个类是Calcite关系代数框架的核心工厂类，它定义了创建各种关系操作符的接口和默认实现。
 * 通过工厂模式，Calcite可以根据不同的调用约定（calling convention）创建不同类型的关系节点。
 * 例如，在逻辑优化阶段创建LogicalProject，在物理优化阶段创建EnumerableProject等。
 * 
 * 主要功能：
 * 1. 定义各种关系操作符的工厂接口（如ProjectFactory、FilterFactory等）
 * 2. 提供默认的工厂实现（如ProjectFactoryImpl、FilterFactoryImpl等）
 * 3. 提供默认的工厂实例（DEFAULT_PROJECT_FACTORY、DEFAULT_FILTER_FACTORY等）
 * 4. 提供Struct类来组合所有工厂实例
 * 5. 提供LOGICAL_BUILDER用于创建逻辑关系表达式
 * 
 * 设计模式：
 * - 工厂模式：通过工厂接口和实现类来创建关系节点
 * - 策略模式：不同的工厂实现可以创建不同类型的关系节点
 * - 依赖注入：通过Context机制注入自定义的工厂实现
 */
public class RelFactories { // RelFactories类：关系节点工厂类，提供创建各种关系操作符的工厂接口和默认实现
  public static final ProjectFactory DEFAULT_PROJECT_FACTORY = // 默认投影工厂：用于创建投影操作符的默认工厂实例
      new ProjectFactoryImpl(); // 创建ProjectFactoryImpl实例，该实现会创建LogicalProject节点

  public static final FilterFactory DEFAULT_FILTER_FACTORY = // 默认过滤工厂：用于创建过滤操作符的默认工厂实例
      new FilterFactoryImpl(); // 创建FilterFactoryImpl实例，该实现会创建LogicalFilter节点

  public static final JoinFactory DEFAULT_JOIN_FACTORY = new JoinFactoryImpl(); // 默认连接工厂：用于创建连接操作符的默认工厂实例

  public static final AsofJoinFactory DEFAULT_ASOFJOIN_FACTORY = new AsofJoinFactoryImpl(); // 默认ASOF连接工厂：用于创建ASOF连接操作符的默认工厂实例

  public static final CorrelateFactory DEFAULT_CORRELATE_FACTORY = // 默认关联工厂：用于创建关联操作符的默认工厂实例
      new CorrelateFactoryImpl(); // 创建CorrelateFactoryImpl实例，该实现会创建LogicalCorrelate节点

  public static final SortFactory DEFAULT_SORT_FACTORY = // 默认排序工厂：用于创建排序操作符的默认工厂实例
      new SortFactoryImpl(); // 创建SortFactoryImpl实例，该实现会创建LogicalSort节点

  public static final ExchangeFactory DEFAULT_EXCHANGE_FACTORY = // 默认交换工厂：用于创建数据交换操作符的默认工厂实例
      new ExchangeFactoryImpl(); // 创建ExchangeFactoryImpl实例，该实现会创建LogicalExchange节点

  public static final SortExchangeFactory DEFAULT_SORT_EXCHANGE_FACTORY = // 默认排序交换工厂：用于创建排序后交换操作符的默认工厂实例
      new SortExchangeFactoryImpl(); // 创建SortExchangeFactoryImpl实例，该实现会创建LogicalSortExchange节点

  public static final AggregateFactory DEFAULT_AGGREGATE_FACTORY = // 默认聚合工厂：用于创建聚合操作符的默认工厂实例
      new AggregateFactoryImpl(); // 创建AggregateFactoryImpl实例，该实现会创建LogicalAggregate节点

  public static final SampleFactory DEFAULT_SAMPLE_FACTORY = // 默认采样工厂：用于创建采样操作符的默认工厂实例
      new SampleFactoryImpl(); // 创建SampleFactoryImpl实例，该实现会创建Sample节点

  public static final MatchFactory DEFAULT_MATCH_FACTORY = // 默认匹配工厂：用于创建模式匹配操作符的默认工厂实例
      new MatchFactoryImpl(); // 创建MatchFactoryImpl实例，该实现会创建LogicalMatch节点

  public static final SetOpFactory DEFAULT_SET_OP_FACTORY = // 默认集合操作工厂：用于创建集合操作符的默认工厂实例
      new SetOpFactoryImpl(); // 创建SetOpFactoryImpl实例，该实现会创建LogicalUnion、LogicalMinus或LogicalIntersect节点

  public static final ValuesFactory DEFAULT_VALUES_FACTORY = // 默认值工厂：用于创建常量值操作符的默认工厂实例
      new ValuesFactoryImpl(); // 创建ValuesFactoryImpl实例，该实现会创建LogicalValues节点

  public static final TableScanFactory DEFAULT_TABLE_SCAN_FACTORY = // 默认表扫描工厂：用于创建表扫描操作符的默认工厂实例
      new TableScanFactoryImpl(); // 创建TableScanFactoryImpl实例，该实现会创建LogicalTableScan节点

  public static final TableFunctionScanFactory // 默认表函数扫描工厂：用于创建表函数扫描操作符的默认工厂实例
      DEFAULT_TABLE_FUNCTION_SCAN_FACTORY = new TableFunctionScanFactoryImpl(); // 创建TableFunctionScanFactoryImpl实例

  public static final SnapshotFactory DEFAULT_SNAPSHOT_FACTORY = // 默认快照工厂：用于创建快照操作符的默认工厂实例
      new SnapshotFactoryImpl(); // 创建SnapshotFactoryImpl实例，该实现会创建LogicalSnapshot节点

  public static final SpoolFactory DEFAULT_SPOOL_FACTORY = // 默认缓存工厂：用于创建缓存操作符的默认工厂实例
      new SpoolFactoryImpl(); // 创建SpoolFactoryImpl实例，该实现会创建LogicalTableSpool节点

  public static final RepeatUnionFactory DEFAULT_REPEAT_UNION_FACTORY = // 默认重复联合工厂：用于创建迭代操作符的默认工厂实例
      new RepeatUnionFactoryImpl(); // 创建RepeatUnionFactoryImpl实例，该实现会创建LogicalRepeatUnion节点

  public static final Struct DEFAULT_STRUCT = // 默认结构体：包含所有默认工厂实例的不可变结构体
      new Struct(DEFAULT_FILTER_FACTORY, // 使用默认过滤工厂初始化结构体
          DEFAULT_PROJECT_FACTORY, // 使用默认投影工厂初始化结构体
          DEFAULT_AGGREGATE_FACTORY, // 使用默认聚合工厂初始化结构体
          DEFAULT_SORT_FACTORY, // 使用默认排序工厂初始化结构体
          DEFAULT_EXCHANGE_FACTORY, // 使用默认交换工厂初始化结构体
          DEFAULT_SORT_EXCHANGE_FACTORY, // 使用默认排序交换工厂初始化结构体
          DEFAULT_SET_OP_FACTORY, // 使用默认集合操作工厂初始化结构体
          DEFAULT_JOIN_FACTORY, // 使用默认连接工厂初始化结构体
          DEFAULT_ASOFJOIN_FACTORY, // 使用默认ASOF连接工厂初始化结构体
          DEFAULT_CORRELATE_FACTORY, // 使用默认关联工厂初始化结构体
          DEFAULT_VALUES_FACTORY, // 使用默认值工厂初始化结构体
          DEFAULT_TABLE_SCAN_FACTORY, // 使用默认表扫描工厂初始化结构体
          DEFAULT_TABLE_FUNCTION_SCAN_FACTORY, // 使用默认表函数扫描工厂初始化结构体
          DEFAULT_SNAPSHOT_FACTORY, // 使用默认快照工厂初始化结构体
          DEFAULT_SAMPLE_FACTORY, // 使用默认采样工厂初始化结构体
          DEFAULT_MATCH_FACTORY, // 使用默认匹配工厂初始化结构体
          DEFAULT_SPOOL_FACTORY, // 使用默认缓存工厂初始化结构体
          DEFAULT_REPEAT_UNION_FACTORY); // 使用默认重复联合工厂初始化结构体

  /** A {@link RelBuilderFactory} that creates a {@link RelBuilder} that will
   * create logical relational expressions for everything.
   * 一个创建RelBuilder的RelBuilderFactory，该RelBuilder将为所有内容创建逻辑关系表达式。
   * 
   * 这个常量定义了一个关系构建器工厂，它创建的RelBuilder会使用DEFAULT_STRUCT中的所有默认工厂，
   * 从而创建逻辑关系表达式（LogicalProject、LogicalFilter等）。
   * 
   * 使用场景：
   * - 在查询优化器的逻辑优化阶段使用
   * - 当需要创建纯逻辑的关系表达式树时使用
   * - 作为RelBuilder的默认配置使用
   */
  public static final RelBuilderFactory LOGICAL_BUILDER = // 逻辑构建器工厂：创建用于构建逻辑关系表达式的RelBuilder
      RelBuilder.proto(Contexts.of(DEFAULT_STRUCT)); // 使用DEFAULT_STRUCT创建上下文，然后创建RelBuilder原型

  private RelFactories() { // 私有构造函数：防止实例化，因为这是一个工具类，所有成员都是静态的
  }

  /**
   * Can create a
   * {@link org.apache.calcite.rel.logical.LogicalProject} of the
   * appropriate type for this rule's calling convention.
   * 可以创建适合此规则调用约定类型的LogicalProject关系节点。
   * 
   * ProjectFactory接口定义了创建投影操作符的方法。投影操作用于选择、重命名或计算输入关系的列。
   * 
   * 投影操作的作用：
   * - 选择需要的列（列裁剪）
   * - 重命名列
   * - 计算新的列（表达式计算）
   * - 消除不需要的列
   * 
   * 调用约定（Calling Convention）：
   * - 不同的调用约定会创建不同类型的Project节点
   * - 例如：LOGICAL约定创建LogicalProject，ENUMERABLE约定创建EnumerableProject
   */
  public interface ProjectFactory { // ProjectFactory接口：投影工厂接口，用于创建投影操作符
    /**
     * Creates a project.
     * 创建一个投影操作符。
     *
     * @param input The input - 输入关系节点，即要对其进行投影操作的关系
     * @param hints The hints - 优化提示列表，用于向优化器提供优化建议
     * @param childExprs The projection expressions - 投影表达式列表，每个表达式对应输出的一列
     * @param fieldNames The projection field names - 投影字段名称列表，对应输出列的名称
     * @return a project - 返回创建的投影关系节点
     * @deprecated Use {@link #createProject(RelNode, List, List, List, Set)} instead
     * 已废弃：请使用带有variablesSet参数的方法代替，这个方法将在2.0版本前移除
     */
    @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
    default RelNode createProject(RelNode input, List<RelHint> hints, // 创建投影操作符的默认方法，不带关联变量
        List<? extends RexNode> childExprs, @Nullable List<? extends @Nullable String> fieldNames) { // 投影表达式和字段名称
      return createProject(input, hints, childExprs, fieldNames, ImmutableSet.of()); // 调用完整方法，传入空的关联变量集合
    }

    /**
     * Creates a project.
     * 创建一个投影操作符。
     *
     * @param input The input - 输入关系节点，即要对其进行投影操作的关系
     * @param hints The hints - 优化提示列表，用于向优化器提供优化建议
     * @param childExprs The projection expressions - 投影表达式列表，每个表达式对应输出的一列
     * @param fieldNames The projection field names - 投影字段名称列表，对应输出列的名称
     * @param variablesSet Correlating variables that are set when reading a row
     *                     from the input, and which may be referenced from the
     *                     projection expressions
     *                     关联变量集合：从输入读取一行时设置的关联变量，可以在投影表达式中引用这些变量
     * @return a project - 返回创建的投影关系节点
     */
    RelNode createProject(RelNode input, List<RelHint> hints, // 创建投影操作符的完整方法，包含关联变量
        List<? extends RexNode> childExprs, @Nullable List<? extends @Nullable String> fieldNames, // 投影表达式和字段名称
        Set<CorrelationId> variablesSet); // 关联变量集合，用于支持相关的子查询
  }

  /**
   * Implementation of {@link ProjectFactory} that returns a vanilla
   * {@link org.apache.calcite.rel.logical.LogicalProject}.
   * ProjectFactory接口的实现类，返回标准的LogicalProject关系节点。
   * 
   * 这个实现类创建的是逻辑投影节点（LogicalProject），它是投影操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   * 
   * "vanilla"表示这是最基本、最标准的实现，没有任何特殊的优化或变体。
   */
  private static class ProjectFactoryImpl implements ProjectFactory { // ProjectFactoryImpl类：投影工厂实现类，创建逻辑投影节点
    @Override public RelNode createProject(RelNode input, List<RelHint> hints, // 重写createProject方法，创建LogicalProject节点
        List<? extends RexNode> childExprs, @Nullable List<? extends @Nullable String> fieldNames, // 投影表达式和字段名称
        Set<CorrelationId> variablesSet) { // 关联变量集合
      return LogicalProject.create(input, hints, childExprs, fieldNames, variablesSet); // 调用LogicalProject.create工厂方法创建逻辑投影节点
    }
  }

  /**
   * Can create a {@link Sort} of the appropriate type
   * for this rule's calling convention.
   * 可以创建适合此规则调用约定类型的Sort关系节点。
   * 
   * SortFactory接口定义了创建排序操作符的方法。排序操作用于对输入关系的行进行排序。
   * 
   * 排序操作的作用：
   * - 根据指定的排序键对行进行排序
   * - 支持升序和降序排序
   * - 支持多列排序
   * - 支持LIMIT和OFFSET（通过fetch和offset参数）
   * 
   * 排序操作是关系代数中常用的操作，通常在ORDER BY子句中使用。
   */
  public interface SortFactory { // SortFactory接口：排序工厂接口，用于创建排序操作符
    /** Creates a sort.
     * 创建一个排序操作符。
     * 
     * @param input - 输入关系节点，即要对其进行排序的关系
     * @param collation - 排序规则，定义排序的列和排序方向（升序/降序）
     * @param offset - 偏移量，表示跳过前N行，null表示不跳过任何行
     * @param fetch - 获取行数，表示只返回前N行，null表示返回所有行
     * @return - 返回创建的排序关系节点
     */
    RelNode createSort(RelNode input, RelCollation collation, @Nullable RexNode offset, // 创建排序操作符的方法
        @Nullable RexNode fetch); // offset和fetch参数用于实现LIMIT和OFFSET

    @Deprecated // to be removed before 2.0 // 已废弃：这个方法将在2.0版本前移除
    default RelNode createSort(RelTraitSet traitSet, RelNode input, // 旧的创建排序方法，包含RelTraitSet参数
        RelCollation collation, @Nullable RexNode offset, @Nullable RexNode fetch) { // 排序规则、偏移量和获取行数
      return createSort(input, collation, offset, fetch); // 调用新方法，忽略traitSet参数
    }
  }

  /**
   * Implementation of {@link RelFactories.SortFactory} that
   * returns a vanilla {@link Sort}.
   * SortFactory接口的实现类，返回标准的Sort关系节点。
   * 
   * 这个实现类创建的是逻辑排序节点（LogicalSort），它是排序操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class SortFactoryImpl implements SortFactory { // SortFactoryImpl类：排序工厂实现类，创建逻辑排序节点
    @Override public RelNode createSort(RelNode input, RelCollation collation, // 重写createSort方法，创建LogicalSort节点
        @Nullable RexNode offset, @Nullable RexNode fetch) { // 偏移量和获取行数
      return LogicalSort.create(input, collation, offset, fetch); // 调用LogicalSort.create工厂方法创建逻辑排序节点
    }
  }

  /**
   * Can create a {@link org.apache.calcite.rel.core.Exchange}
   * of the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的Exchange关系节点。
   * 
   * ExchangeFactory接口定义了创建数据交换操作符的方法。数据交换操作用于在分布式环境中重新分布数据。
   * 
   * 数据交换操作的作用：
   * - 改变数据的分布方式（如从单机分布变为分布式）
   * - 为后续的分布式操作准备数据（如分布式JOIN、分布式聚合）
   * - 实现数据重分区（repartition）
   * 
   * 分布式场景：
   * - 在分布式查询处理中，数据需要按照特定的键分布到不同的节点
   * - Exchange操作符负责在节点之间移动数据
   * - 常见的分布方式：单点分布、广播分布、哈希分布、范围分布等
   */
  public interface ExchangeFactory { // ExchangeFactory接口：数据交换工厂接口，用于创建数据交换操作符
    /** Creates an Exchange.
     * 创建一个数据交换操作符。
     * 
     * @param input - 输入关系节点，即要重新分布的关系
     * @param distribution - 数据分布规则，定义数据如何分布到不同节点
     * @return - 返回创建的数据交换关系节点
     */
    RelNode createExchange(RelNode input, RelDistribution distribution); // 创建数据交换操作符的方法
  }

  /**
   * Implementation of
   * {@link RelFactories.ExchangeFactory}
   * that returns a {@link Exchange}.
   * ExchangeFactory接口的实现类，返回标准的Exchange关系节点。
   * 
   * 这个实现类创建的是逻辑数据交换节点（LogicalExchange），它是数据交换操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class ExchangeFactoryImpl implements ExchangeFactory { // ExchangeFactoryImpl类：数据交换工厂实现类，创建逻辑数据交换节点
    @Override public RelNode createExchange( // 重写createExchange方法，创建LogicalExchange节点
        RelNode input, RelDistribution distribution) { // 输入关系和数据分布规则
      return LogicalExchange.create(input, distribution); // 调用LogicalExchange.create工厂方法创建逻辑数据交换节点
    }
  }

  /**
   * Can create a {@link SortExchange}
   * of the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的SortExchange关系节点。
   * 
   * SortExchangeFactory接口定义了创建排序后数据交换操作符的方法。
   * 这个操作符结合了排序和数据交换两个功能，先对数据进行排序，然后再进行数据交换。
   * 
   * 排序后数据交换操作的作用：
   * - 在数据交换之前对数据进行排序
   * - 确保数据在交换后保持有序
   * - 优化某些分布式操作的性能（如分布式合并连接）
   * 
   * 使用场景：
   * - 分布式归并排序（Merge Sort）
   * - 分布式归并连接（Merge Join）
   * - 需要全局排序的分布式查询
   */
  public interface SortExchangeFactory { // SortExchangeFactory接口：排序后数据交换工厂接口
    /**
     * Creates a {@link SortExchange}.
     * 创建一个排序后数据交换操作符。
     * 
     * @param input - 输入关系节点，即要排序并重新分布的关系
     * @param distribution - 数据分布规则，定义数据如何分布到不同节点
     * @param collation - 排序规则，定义排序的列和排序方向
     * @return - 返回创建的排序后数据交换关系节点
     */
    RelNode createSortExchange( // 创建排序后数据交换操作符的方法
        RelNode input, // 输入关系
        RelDistribution distribution, // 数据分布规则
        RelCollation collation); // 排序规则
  }

  /**
   * Implementation of
   * {@link RelFactories.SortExchangeFactory}
   * that returns a {@link SortExchange}.
   * SortExchangeFactory接口的实现类，返回标准的SortExchange关系节点。
   * 
   * 这个实现类创建的是逻辑排序后数据交换节点（LogicalSortExchange），它是排序后数据交换操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class SortExchangeFactoryImpl implements SortExchangeFactory { // SortExchangeFactoryImpl类：排序后数据交换工厂实现类
    @Override public RelNode createSortExchange( // 重写createSortExchange方法，创建LogicalSortExchange节点
        RelNode input, // 输入关系
        RelDistribution distribution, // 数据分布规则
        RelCollation collation) { // 排序规则
      return LogicalSortExchange.create(input, distribution, collation); // 调用LogicalSortExchange.create工厂方法创建逻辑排序后数据交换节点
    }
  }

  /**
   * Can create a {@link SetOp} for a particular kind of
   * set operation (UNION, EXCEPT, INTERSECT) and of the appropriate type
   * for this rule's calling convention.
   * 可以创建适合此规则调用约定类型的SetOp关系节点，用于特定类型的集合操作（UNION、EXCEPT、INTERSECT）。
   * 
   * SetOpFactory接口定义了创建集合操作符的方法。集合操作用于对两个或多个集合进行操作。
   * 
   * 集合操作的作用：
   * - UNION：合并多个集合，返回所有不重复的行（或所有行，如果使用ALL）
   * - EXCEPT（MINUS）：从第一个集合中减去第二个集合中的行
   * - INTERSECT：返回多个集合的交集
   * 
   * SQL对应：
   * - UNION对应SQL的UNION和UNION ALL
   * - EXCEPT对应SQL的EXCEPT和EXCEPT ALL
   * - INTERSECT对应SQL的INTERSECT和INTERSECT ALL
   * 
   * all参数：
   * - true：保留重复行（对应UNION ALL、EXCEPT ALL、INTERSECT ALL）
   * - false：消除重复行（对应UNION、EXCEPT、INTERSECT）
   */
  public interface SetOpFactory { // SetOpFactory接口：集合操作工厂接口，用于创建集合操作符
    /** Creates a set operation.
     * 创建一个集合操作符。
     * 
     * @param kind - 集合操作类型，可以是UNION、EXCEPT或INTERSECT
     * @param inputs - 输入关系节点列表，即要进行集合操作的多个集合
     * @param all - 是否保留重复行，true表示保留，false表示消除重复行
     * @return - 返回创建的集合操作关系节点
     */
    RelNode createSetOp(SqlKind kind, List<RelNode> inputs, boolean all); // 创建集合操作符的方法
  }

  /**
   * Implementation of {@link RelFactories.SetOpFactory} that
   * returns a vanilla {@link SetOp} for the particular kind of set
   * operation (UNION, EXCEPT, INTERSECT).
   * SetOpFactory接口的实现类，返回标准的SetOp关系节点，用于特定类型的集合操作。
   * 
   * 这个实现类根据集合操作类型创建相应的逻辑节点：
   * - UNION类型创建LogicalUnion节点
   * - EXCEPT类型创建LogicalMinus节点
   * - INTERSECT类型创建LogicalIntersect节点
   * 
   * 这些都是逻辑表示，在查询优化的逻辑阶段使用。
   */
  private static class SetOpFactoryImpl implements SetOpFactory { // SetOpFactoryImpl类：集合操作工厂实现类
    @Override public RelNode createSetOp(SqlKind kind, List<RelNode> inputs, // 重写createSetOp方法
        boolean all) { // 集合操作类型、输入关系列表和是否保留重复行
      switch (kind) { // 根据集合操作类型选择对应的实现
      case UNION: // 如果是UNION操作
        return LogicalUnion.create(inputs, all); // 创建LogicalUnion节点
      case EXCEPT: // 如果是EXCEPT操作
        return LogicalMinus.create(inputs, all); // 创建LogicalMinus节点
      case INTERSECT: // 如果是INTERSECT操作
        return LogicalIntersect.create(inputs, all); // 创建LogicalIntersect节点
      default: // 如果不是集合操作
        throw new AssertionError("not a set op: " + kind); // 抛出断言错误
      }
    }
  }

  /**
   * Can create a {@link LogicalAggregate} of the appropriate type
   * for this rule's calling convention.
   * 可以创建适合此规则调用约定类型的LogicalAggregate关系节点。
   * 
   * AggregateFactory接口定义了创建聚合操作符的方法。聚合操作用于对输入关系的行进行分组和聚合计算。
   * 
   * 聚合操作的作用：
   * - GROUP BY：按照指定的列对行进行分组
   * - 聚合函数：对每个分组计算聚合值（SUM、COUNT、AVG、MIN、MAX等）
   * - HAVING：过滤分组（HAVING条件在聚合后应用）
   * 
   * 聚合操作是SQL中最常用的操作之一，对应SQL的GROUP BY子句和聚合函数。
   * 
   * groupSet和groupSets参数：
   * - groupSet：当前使用的分组列集合
   * - groupSets：所有可能的分组列集合（用于GROUPING SETS、ROLLUP、CUBE）
   * - 对于简单的GROUP BY，groupSets只包含一个元素，即groupSet
   * - 对于GROUPING SETS，groupSets包含多个分组集合
   * - 对于ROLLUP和CUBE，groupSets包含所有可能的分组组合
   */
  public interface AggregateFactory { // AggregateFactory接口：聚合工厂接口，用于创建聚合操作符
    /** Creates an aggregate.
     * 创建一个聚合操作符。
     * 
     * @param input - 输入关系节点，即要进行聚合操作的关系
     * @param hints - 优化提示列表，用于向优化器提供优化建议
     * @param groupSet - 分组列集合，定义当前使用的分组列
     * @param groupSets - 所有分组列集合的列表，用于GROUPING SETS、ROLLUP、CUBE
     * @param aggCalls - 聚合调用列表，定义要执行的聚合函数
     * @return - 返回创建的聚合关系节点
     */
    RelNode createAggregate(RelNode input, List<RelHint> hints, ImmutableBitSet groupSet, // 创建聚合操作符的方法
        ImmutableList<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls); // 分组集合和聚合调用
  }

  /**
   * Implementation of {@link RelFactories.AggregateFactory}
   * that returns a vanilla {@link LogicalAggregate}.
   * AggregateFactory接口的实现类，返回标准的LogicalAggregate关系节点。
   * 
   * 这个实现类创建的是逻辑聚合节点（LogicalAggregate），它是聚合操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class AggregateFactoryImpl implements AggregateFactory { // AggregateFactoryImpl类：聚合工厂实现类
    @Override public RelNode createAggregate(RelNode input, List<RelHint> hints, // 重写createAggregate方法，创建LogicalAggregate节点
        ImmutableBitSet groupSet, ImmutableList<ImmutableBitSet> groupSets, // 分组列集合和所有分组集合
        List<AggregateCall> aggCalls) { // 聚合调用列表
      return LogicalAggregate.create(input, hints, groupSet, groupSets, aggCalls); // 调用LogicalAggregate.create工厂方法创建逻辑聚合节点
    }
  }

  /**
   * Can create a {@link Filter} of the appropriate type
   * for this rule's calling convention.
   * 可以创建适合此规则调用约定类型的Filter关系节点。
   * 
   * FilterFactory接口定义了创建过滤操作符的方法。过滤操作用于根据条件过滤输入关系的行。
   * 
   * 过滤操作的作用：
   * - 根据条件筛选行，只保留满足条件的行
   * - 对应SQL的WHERE子句和HAVING子句
   * - 是查询中最常用的操作之一
   * 
   * 关联变量（Correlation Variables）：
   * - 关联变量用于支持相关的子查询（Correlated Subquery）
   * - 在处理相关子查询时，外部查询的值需要传递给内部查询
   * - 关联变量就是这种传递机制的实现
   * 
   * 注意事项：
   * - 某些Filter实现不支持关联变量，如果variablesSet不为空会抛出异常
   * - 过滤条件必须是一个布尔表达式
   * - 只有条件评估为TRUE的行才会被输出
   */
  public interface FilterFactory { // FilterFactory接口：过滤工厂接口，用于创建过滤操作符
    /** Creates a filter.
     * 创建一个过滤操作符。
     *
     * <p>Some implementations of {@code Filter} do not support correlation
     * variables, and for these, this method will throw if {@code variablesSet}
     * is not empty.
     * 某些Filter实现不支持关联变量，如果variablesSet不为空，这个方法会抛出异常。
     *
     * @param input Input relational expression - 输入关系表达式，即要过滤的关系
     * @param condition Filter condition; only rows for which this condition
     *   evaluates to TRUE will be emitted - 过滤条件，只有条件评估为TRUE的行才会被输出
     * @param variablesSet Correlating variables that are set when reading
     *   a row from the input, and which may be referenced from inside the
     *   condition - 关联变量集合，从输入读取一行时设置的关联变量，可以在条件中引用
     */
    RelNode createFilter(RelNode input, RexNode condition, // 创建过滤操作符的方法
        Set<CorrelationId> variablesSet); // 关联变量集合

    @Deprecated // to be removed before 2.0 // 已废弃：这个方法将在2.0版本前移除
    default RelNode createFilter(RelNode input, RexNode condition) { // 旧的创建过滤方法，不带关联变量
      return createFilter(input, condition, ImmutableSet.of()); // 调用完整方法，传入空的关联变量集合
    }
  }

  /**
   * Implementation of {@link RelFactories.FilterFactory} that
   * returns a vanilla {@link LogicalFilter}.
   * FilterFactory接口的实现类，返回标准的LogicalFilter关系节点。
   * 
   * 这个实现类创建的是逻辑过滤节点（LogicalFilter），它是过滤操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   * 
   * LogicalFilter支持关联变量，可以处理相关的子查询。
   */
  private static class FilterFactoryImpl implements FilterFactory { // FilterFactoryImpl类：过滤工厂实现类
    @Override public RelNode createFilter(RelNode input, RexNode condition, // 重写createFilter方法，创建LogicalFilter节点
        Set<CorrelationId> variablesSet) { // 关联变量集合
      return LogicalFilter.create(input, condition, // 调用LogicalFilter.create工厂方法创建逻辑过滤节点
          ImmutableSet.copyOf(variablesSet)); // 将关联变量集合转换为不可变集合
    }
  }

  /**
   * Can create a join of the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的Join关系节点。
   *
   * <p>The result is typically a {@link Join}.
   * 结果通常是Join关系节点。
   * 
   * JoinFactory接口定义了创建连接操作符的方法。连接操作用于根据连接条件组合两个或多个关系的行。
   * 
   * 连接操作的作用：
   * - 根据连接条件组合左右两个输入关系的行
   * - 对应SQL的JOIN子句（INNER JOIN、LEFT JOIN、RIGHT JOIN、FULL JOIN等）
   * - 是关系代数中最重要和最复杂的操作之一
   * 
   * 连接类型（JoinRelType）：
   * - INNER：内连接，只返回匹配的行
   * - LEFT：左外连接，返回左表所有行和右表匹配的行
   * - RIGHT：右外连接，返回右表所有行和左表匹配的行
   * - FULL：全外连接，返回左右表所有行
   * 
   * 半连接（Semi-Join）：
   * - 半连接是一种特殊的连接，用于 EXISTS 和 IN 子查询
   * - semiJoinDone参数表示这个连接是否已经被转换为半连接
   * - 半连接只关心左表是否有匹配，不关心右表的具体值
   * 
   * 关联变量（Correlation Variables）：
   * - variablesSet包含左表设置、右表使用的变量
   * - 这些变量在连接树中不对此节点之上的节点可见
   * - 用于支持相关的子查询
   */
  public interface JoinFactory { // JoinFactory接口：连接工厂接口，用于创建连接操作符
    /**
     * Creates a join.
     * 创建一个连接操作符。
     *
     * @param left             Left input - 左输入关系节点
     * @param right            Right input - 右输入关系节点
     * @param hints            Hints - 优化提示列表
     * @param condition        Join condition - 连接条件，定义如何匹配左右表的行
     * @param variablesSet     Set of variables that are set by the
     *                         LHS and used by the RHS and are not available to
     *                         nodes above this LogicalJoin in the tree
     *                         关联变量集合，由左表设置、右表使用，且对此节点之上的节点不可见
     * @param joinType         Join type - 连接类型（INNER、LEFT、RIGHT、FULL）
     * @param semiJoinDone     Whether this join has been translated to a
     *                         semi-join - 是否已转换为半连接
     */
    RelNode createJoin(RelNode left, RelNode right, List<RelHint> hints, // 创建连接操作符的方法
        RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType, // 连接条件、关联变量和连接类型
        boolean semiJoinDone); // 是否已转换为半连接
  }

  /**
   * Creates ASOF join of the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的ASOF Join关系节点。
   * 
   * AsofJoinFactory接口定义了创建ASOF连接操作符的方法。
   * ASOF（As-Of）连接是一种特殊的连接，用于时间序列数据的连接。
   * 
   * ASOF连接的作用：
   * - 将左表的每一行与右表中"最近"的行进行连接
   * - "最近"通常基于时间戳列
   * - 用于时间序列数据的对齐和关联
   * 
   * ASOF连接的特点：
   * - 类似于LEFT JOIN，但右表只返回最匹配的一行
   * - 匹配条件通常包含时间戳的比较（如右表时间戳 <= 左表时间戳）
   - matchCondition定义了如何确定"最近"的行
   * 
   * 使用场景：
   * - 金融数据：将交易数据与报价数据对齐
   * - 传感器数据：将不同传感器的数据按时间对齐
   * - 日志数据：将事件日志与状态日志关联
   */
  public interface AsofJoinFactory { // AsofJoinFactory接口：ASOF连接工厂接口，用于创建ASOF连接操作符
    /**
     * Creates an ASOF join.
     * 创建一个ASOF连接操作符。
     *
     * @param left             Left input - 左输入关系节点
     * @param right            Right input - 右输入关系节点
     * @param hints            Hints - 优化提示列表
     * @param condition        Join condition - 连接条件
     * @param matchCondition   ASOF join match condition - ASOF连接匹配条件，定义如何确定"最近"的行
     * @param joinType         Type of join (ASOF or LEFT_ASOF) - 连接类型（ASOF或LEFT_ASOF）
     */
    RelNode createAsofJoin(RelNode left, RelNode right, List<RelHint> hints, // 创建ASOF连接操作符的方法
        RexNode condition, RexNode matchCondition, JoinRelType joinType); // 连接条件、匹配条件和连接类型
  }

  /**
   * Implementation of {@link JoinFactory} that returns a vanilla
   * {@link org.apache.calcite.rel.logical.LogicalJoin}.
   * JoinFactory接口的实现类，返回标准的LogicalJoin关系节点。
   * 
   * 这个实现类创建的是逻辑连接节点（LogicalJoin），它是连接操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class JoinFactoryImpl implements JoinFactory { // JoinFactoryImpl类：连接工厂实现类
    @Override public RelNode createJoin(RelNode left, RelNode right, List<RelHint> hints, // 重写createJoin方法，创建LogicalJoin节点
        RexNode condition, Set<CorrelationId> variablesSet, // 连接条件和关联变量
        JoinRelType joinType, boolean semiJoinDone) { // 连接类型和是否已转换为半连接
      return LogicalJoin.create(left, right, hints, condition, variablesSet, joinType, // 调用LogicalJoin.create工厂方法创建逻辑连接节点
          semiJoinDone, ImmutableList.of()); // 是否已转换为半连接和空的系统字段列表
    }
  }

  /**
   * Implementation of {@link AsofJoinFactory} that returns a vanilla
   * {@link LogicalAsofJoin}.
   * AsofJoinFactory接口的实现类，返回标准的LogicalAsofJoin关系节点。
   * 
   * 这个实现类创建的是逻辑ASOF连接节点（LogicalAsofJoin），它是ASOF连接操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class AsofJoinFactoryImpl implements AsofJoinFactory { // AsofJoinFactoryImpl类：ASOF连接工厂实现类
    @Override public RelNode createAsofJoin(RelNode left, RelNode right, List<RelHint> hints, // 重写createAsofJoin方法，创建LogicalAsofJoin节点
        RexNode condition, RexNode matchCondition, JoinRelType joinType) { // 连接条件、匹配条件和连接类型
      return LogicalAsofJoin.create(left, right, hints, // 调用LogicalAsofJoin.create工厂方法创建逻辑ASOF连接节点
          condition, matchCondition, joinType, ImmutableList.of()); // 连接条件、匹配条件、连接类型和空的系统字段列表
    }
  }

  /**
   * Can create a correlate of the appropriate type for a rule's calling
   * convention.
   * 可以创建适合此规则调用约定类型的Correlate关系节点。
   *
   * <p>The result is typically a {@link Correlate}.
   * 结果通常是Correlate关系节点。
   * 
   * CorrelateFactory接口定义了创建关联操作符的方法。关联操作用于处理相关的子查询。
   * 
   * 关联操作的作用：
   * - 实现相关的子查询（Correlated Subquery）
   * - 将外部查询的值传递给内部查询
   * - 支持EXISTS、IN、SOME、ANY、ALL等相关的子查询
   * 
   * 关联操作与普通连接的区别：
   * - 关联操作是嵌套循环连接的一种特殊形式
   * - 对于左表的每一行，右表都会被评估一次
   * - 右表的查询可以引用左表的列（通过关联变量）
   * 
   * 关联ID（CorrelationId）：
   * - 标识左表行的变量名
   * - 右表可以通过这个变量名引用左表的列
   * - 每个Correlate节点都有唯一的correlationId
   * 
   * 必需列（Required Columns）：
   * - 定义右表需要引用左表的哪些列
   * - 只传递必需的列，避免不必要的数据传递
   * - 优化性能，减少数据传输
   * 
   * 连接类型：
   * - INNER：内关联，只返回有匹配的行
   * - LEFT：左外关联，返回左表所有行
   */
  public interface CorrelateFactory { // CorrelateFactory接口：关联工厂接口，用于创建关联操作符

    /**
     * Creates a correlate.
     * 创建一个关联操作符。
     *
     * @param left             Left input - 左输入关系节点
     * @param right            Right input - 右输入关系节点
     * @param hints            Hints - 优化提示列表
     * @param correlationId    Variable name for the row of left input - 左表行的变量名，用于右表引用左表列
     * @param requiredColumns  Required columns - 必需列集合，定义右表需要引用左表的哪些列
     * @param joinType         Join type - 连接类型（INNER或LEFT）
     */
    RelNode createCorrelate(RelNode left, RelNode right, List<RelHint> hints, // 创建关联操作符的方法
        CorrelationId correlationId, ImmutableBitSet requiredColumns, // 关联ID和必需列
        JoinRelType joinType); // 连接类型
  }

  /**
   * Implementation of {@link CorrelateFactory} that returns a vanilla
   * {@link org.apache.calcite.rel.logical.LogicalCorrelate}.
   * CorrelateFactory接口的实现类，返回标准的LogicalCorrelate关系节点。
   * 
   * 这个实现类创建的是逻辑关联节点（LogicalCorrelate），它是关联操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class CorrelateFactoryImpl implements CorrelateFactory { // CorrelateFactoryImpl类：关联工厂实现类

    @Override public RelNode createCorrelate(RelNode left, RelNode right, List<RelHint> hints, // 重写createCorrelate方法，创建LogicalCorrelate节点
        CorrelationId correlationId, ImmutableBitSet requiredColumns, JoinRelType joinType) { // 关联ID、必需列和连接类型
      return LogicalCorrelate.create(left, right, hints, correlationId, // 调用LogicalCorrelate.create工厂方法创建逻辑关联节点
          requiredColumns, joinType); // 必需列和连接类型
    }
  }

  /**
   * Can create a semi-join of the appropriate type for a rule's calling
   * convention.
   * 可以创建适合此规则调用约定类型的半连接关系节点。
   *
   * @deprecated Use {@link JoinFactory} instead.
   * 已废弃：请使用JoinFactory代替。半连接功能现在通过JoinFactory实现。
   * 
   * SemiJoinFactory接口定义了创建半连接操作符的方法。半连接是一种特殊的连接操作。
   * 
   * 半连接的作用：
   * - 用于EXISTS和IN子查询的优化
   * - 只关心左表的行是否在右表中有匹配，不关心右表的具体值
   * - 返回左表的行（如果右表有匹配），不返回右表的列
   * 
   * 半连接与普通连接的区别：
   * - 半连接只返回左表的列，不返回右表的列
   * - 半连接只检查是否存在匹配，不返回匹配的右表行
   * - 半连接的性能通常比普通连接更好
   * 
   * 使用场景：
   * - EXISTS子查询：SELECT * FROM t1 WHERE EXISTS (SELECT 1 FROM t2 WHERE t1.id = t2.id)
   * - IN子查询：SELECT * FROM t1 WHERE id IN (SELECT id FROM t2)
   * 
   * 为什么废弃：
   * - 半连接现在作为JoinFactory的一个特殊情况实现
   * - 通过JoinFactory的semiJoinDone参数标识
   * - 统一了连接操作的接口，简化了代码
   */
  @Deprecated // to be removed before 2.0 // 已废弃：这个接口将在2.0版本前移除
  public interface SemiJoinFactory { // SemiJoinFactory接口：半连接工厂接口（已废弃）
    /**
     * Creates a semi-join.
     * 创建一个半连接操作符。
     *
     * @param left             Left input - 左输入关系节点
     * @param right            Right input - 右输入关系节点
     * @param condition        Join condition - 连接条件
     */
    RelNode createSemiJoin(RelNode left, RelNode right, RexNode condition); // 创建半连接操作符的方法
  }

  /**
   * Can create a {@link Values} of the appropriate type for a rule's calling
   * convention.
   * 可以创建适合此规则调用约定类型的Values关系节点。
   * 
   * ValuesFactory接口定义了创建常量值操作符的方法。常量值操作用于表示常量数据。
   * 
   * 常量值操作的作用：
   * - 表示常量数据行
   * - 对应SQL的VALUES子句
   * - 用于INSERT语句中的值列表
   * - 用于创建临时数据
   * 
   * 使用场景：
   * - INSERT INTO table VALUES (1, 'a'), (2, 'b'), (3, 'c');
   * - SELECT * FROM (VALUES (1, 'a'), (2, 'b')) AS t(id, name);
   * - 在查询优化过程中生成的常量数据
   * 
   * tuples参数：
   * - 每个ImmutableList<RexLiteral>表示一行数据
   * - 每个RexLiteral表示一个常量值
   * - 所有行的结构必须与rowType匹配
   * 
   * rowType参数：
   * - 定义返回值的类型和名称
   * - 每列的类型必须与tuples中的RexLiteral类型匹配
   */
  public interface ValuesFactory { // ValuesFactory接口：常量值工厂接口，用于创建常量值操作符
    /**
     * Creates a Values.
     * 创建一个常量值操作符。
     * 
     * @param cluster - 关系优化集群，包含查询优化过程中共享的资源
     * @param rowType - 行类型，定义返回值的类型和名称
     * @param tuples - 元组列表，每个元组表示一行数据，每个元素是一个RexLiteral常量
     * @return - 返回创建的常量值关系节点
     */
    RelNode createValues(RelOptCluster cluster, RelDataType rowType, // 创建常量值操作符的方法
        List<ImmutableList<RexLiteral>> tuples); // 元组列表
  }

  /**
   * Implementation of {@link ValuesFactory} that returns a
   * {@link LogicalValues}.
   * ValuesFactory接口的实现类，返回标准的LogicalValues关系节点。
   * 
   * 这个实现类创建的是逻辑常量值节点（LogicalValues），它是常量值操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class ValuesFactoryImpl implements ValuesFactory { // ValuesFactoryImpl类：常量值工厂实现类
    @Override public RelNode createValues(RelOptCluster cluster, RelDataType rowType, // 重写createValues方法，创建LogicalValues节点
        List<ImmutableList<RexLiteral>> tuples) { // 行类型和元组列表
      return LogicalValues.create(cluster, rowType, // 调用LogicalValues.create工厂方法创建逻辑常量值节点
          ImmutableList.copyOf(tuples)); // 将元组列表转换为不可变列表
    }
  }

  /**
   * Can create a {@link TableScan} of the appropriate type for a rule's calling
   * convention.
   * 可以创建适合此规则调用约定类型的TableScan关系节点。
   * 
   * TableScanFactory接口定义了创建表扫描操作符的方法。表扫描操作用于从表中读取数据。
   * 
   * 表扫描操作的作用：
   * - 从数据源（表、视图等）读取数据
   * - 是查询执行树的叶子节点
   * - 对应SQL的FROM子句中的表引用
   * 
   * 表扫描的特点：
   * - 是关系代数树的最底层节点
   * - 没有子节点（叶子节点）
   * - 直接访问数据源
   * 
   * toRelContext参数：
   * - 提供将表转换为关系节点所需的上下文信息
   * - 包含RelOptCluster、RelTraitSet等优化信息
   * - 用于在转换过程中传递必要的参数
   * 
   * table参数：
   * - 表示要扫描的表或视图
   * - 包含表的元数据信息（schema、列、统计信息等）
   * - 可以是物理表、视图、子查询等
   */
  public interface TableScanFactory { // TableScanFactory接口：表扫描工厂接口，用于创建表扫描操作符
    /**
     * Creates a {@link TableScan}.
     * 创建一个表扫描操作符。
     * 
     * @param toRelContext - 表转换上下文，提供将表转换为关系节点所需的上下文信息
     * @param table - 优化表对象，表示要扫描的表或视图
     * @return - 返回创建的表扫描关系节点
     */
    RelNode createScan(RelOptTable.ToRelContext toRelContext, RelOptTable table); // 创建表扫描操作符的方法
  }

  /**
   * Implementation of {@link TableScanFactory} that returns a
   * {@link LogicalTableScan}.
   * TableScanFactory接口的实现类，返回标准的LogicalTableScan关系节点。
   * 
   * 这个实现类创建的是逻辑表扫描节点（LogicalTableScan），它是表扫描操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   * 
   * 注意：
   * - 这个实现直接调用table.toRel(toRelContext)方法
   * - 实际的表扫描节点由table对象自己创建
   * - 这允许不同的表类型有不同的扫描实现
   */
  private static class TableScanFactoryImpl implements TableScanFactory { // TableScanFactoryImpl类：表扫描工厂实现类
    @Override public RelNode createScan(RelOptTable.ToRelContext toRelContext, RelOptTable table) { // 重写createScan方法，创建表扫描节点
      return table.toRel(toRelContext); // 调用table.toRel方法，由表对象自己创建扫描节点
    }
  }

  /**
   * Can create a {@link TableFunctionScan}
   * of the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的TableFunctionScan关系节点。
   * 
   * TableFunctionScanFactory接口定义了创建表函数扫描操作符的方法。表函数扫描用于调用返回表的用户定义函数。
   * 
   * 表函数扫描的作用：
   * - 调用返回表的用户定义函数（UDTF）
   * - 将函数调用的结果作为关系节点使用
   * - 支持函数的输入参数来自其他关系节点
   * 
   * 表函数的特点：
   * - 类似于普通函数，但返回一个表而不是单个值
   * - 可以在FROM子句中使用
   * - 可以接受其他查询的结果作为参数
   * 
   * 使用场景：
   * - UDTF（User-Defined Table Functions）：用户定义的表函数
   * - 生成函数：如GENERATE_SERIES、UNNEST等
   * - 数据转换函数：将JSON、XML等结构化数据转换为表
   * 
   * elementType参数：
   * - 表函数返回的Java元素类型
   * - 用于Java反射和类型检查
   * - 可以为null，表示使用默认类型
   * 
   * columnMappings参数：
   * - 定义输出列与输入列之间的映射关系
   * - 可以为null，表示没有列映射
   * - 用于某些特殊的表函数实现
   */
  public interface TableFunctionScanFactory { // TableFunctionScanFactory接口：表函数扫描工厂接口
    /** Creates a {@link TableFunctionScan}.
     * 创建一个表函数扫描操作符。
     * 
     * @param cluster - 关系优化集群，包含查询优化过程中共享的资源
     * @param inputs - 输入关系节点列表，作为表函数的参数
     * @param call - Rex调用表达式，表示表函数的调用
     * @param elementType - 元素类型，表函数返回的Java元素类型，可以为null
     * @param columnMappings - 列映射集合，定义输出列与输入列之间的映射关系，可以为null
     * @return - 返回创建的表函数扫描关系节点
     */
    RelNode createTableFunctionScan(RelOptCluster cluster, // 创建表函数扫描操作符的方法
        List<RelNode> inputs, RexCall call, @Nullable Type elementType, // 输入关系、函数调用和元素类型
        @Nullable Set<RelColumnMapping> columnMappings); // 列映射集合
  }

  /**
   * Implementation of
   * {@link TableFunctionScanFactory}
   * that returns a {@link TableFunctionScan}.
   * TableFunctionScanFactory接口的实现类，返回标准的TableFunctionScan关系节点。
   * 
   * 这个实现类创建的是逻辑表函数扫描节点（LogicalTableFunctionScan），它是表函数扫描操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   * 
   * 返回类型推断：
   * 这个实现类包含复杂的返回类型推断逻辑：
   * 1. 如果操作符实现了SqlTableFunction接口，使用SqlTableFunction的返回类型推断
   * 2. 否则，使用调用的类型（操作符可能有自定义的返回类型推断方式）
   */
  private static class TableFunctionScanFactoryImpl // TableFunctionScanFactoryImpl类：表函数扫描工厂实现类
      implements TableFunctionScanFactory { // 实现TableFunctionScanFactory接口
    @Override public RelNode createTableFunctionScan(RelOptCluster cluster, // 重写createTableFunctionScan方法，创建表函数扫描节点
        List<RelNode> inputs, RexCall call, @Nullable Type elementType, // 输入关系、函数调用和元素类型
        @Nullable Set<RelColumnMapping> columnMappings) { // 列映射集合
      final RelDataType rowType; // 声明行类型变量
      // To deduce the return type:
      // 1. if the operator implements SqlTableFunction,
      // use the SqlTableFunction's return type inference;
      // 2. else use the call's type, e.g. the operator may has
      // its custom way for return type inference.
      // 推断返回类型：
      // 1. 如果操作符实现了SqlTableFunction，使用SqlTableFunction的返回类型推断；
      // 2. 否则使用调用的类型，例如操作符可能有自定义的返回类型推断方式。
      if (call.getOperator() instanceof SqlTableFunction) { // 如果操作符是SqlTableFunction实例
        final SqlOperatorBinding callBinding = // 创建操作符绑定对象
            new RexCallBinding(cluster.getTypeFactory(), call.getOperator(), // 使用类型工厂、操作符和操作数
                call.operands, ImmutableList.of()); // 创建RexCallBinding
        final SqlTableFunction operator = (SqlTableFunction) call.getOperator(); // 获取SqlTableFunction操作符
        final SqlReturnTypeInference rowTypeInference = // 获取返回类型推断器
            operator.getRowTypeInference(); // 从操作符获取返回类型推断器
        rowType = rowTypeInference.inferReturnType(callBinding); // 推断返回类型
      } else { // 如果操作符不是SqlTableFunction实例
        rowType = call.getType(); // 使用调用的类型作为返回类型
      }

      return LogicalTableFunctionScan.create(cluster, inputs, call, // 调用LogicalTableFunctionScan.create工厂方法创建逻辑表函数扫描节点
          elementType, requireNonNull(rowType, "rowType"), columnMappings); // 元素类型、非空的行类型和列映射集合
    }
  }

  /**
   * Can create a {@link Snapshot} of
   * the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的Snapshot关系节点。
   * 
   * SnapshotFactory接口定义了创建快照操作符的方法。快照操作用于获取关系在特定时间点的数据。
   * 
   * 快照操作的作用：
   * - 获取关系在特定时间点的数据
   * - 支持时间旅行查询（Time Travel Query）
   * - 用于版本化表和历史数据查询
   * - 对应SQL的FOR SYSTEM_TIME AS OF子句（某些数据库）
   * 
   * 快照操作的特点：
   * - 不修改数据，只读取特定时间点的数据
   * - 支持历史数据查询和审计
   * - 可以用于数据恢复和回滚
   * 
   * 使用场景：
   * - 时间旅行查询：查询表在过去某个时间点的状态
   * - 审计日志：查看数据的历史变更
   * - 数据恢复：恢复到某个时间点的数据状态
   * - 版本控制：支持多版本并发控制（MVCC）
   * 
   * period参数：
   * - 表示要查询的时间点或时间范围
   * - 可以是时间戳、日期或其他时间表达式
   * - 必须能够被数据库或存储系统理解
   */
  public interface SnapshotFactory { // SnapshotFactory接口：快照工厂接口，用于创建快照操作符
    /**
     * Creates a {@link Snapshot}.
     * 创建一个快照操作符。
     * 
     * @param input - 输入关系节点，即要获取快照的关系
     * @param period - 时间点或时间范围表达式，表示要查询的时间点
     * @return - 返回创建的快照关系节点
     */
    RelNode createSnapshot(RelNode input, RexNode period); // 创建快照操作符的方法
  }

  /**
   * Implementation of {@link RelFactories.SnapshotFactory} that
   * returns a vanilla {@link LogicalSnapshot}.
   * SnapshotFactory接口的实现类，返回标准的LogicalSnapshot关系节点。
   * 
   * 这个实现类创建的是逻辑快照节点（LogicalSnapshot），它是快照操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  public static class SnapshotFactoryImpl implements SnapshotFactory { // SnapshotFactoryImpl类：快照工厂实现类
    @Override public RelNode createSnapshot(RelNode input, RexNode period) { // 重写createSnapshot方法，创建LogicalSnapshot节点
      return LogicalSnapshot.create(input, period); // 调用LogicalSnapshot.create工厂方法创建逻辑快照节点
    }
  }

  /**
   * Can create a {@link Match} of
   * the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的Match关系节点。
   * 
   * MatchFactory接口定义了创建模式匹配操作符的方法。模式匹配操作用于在数据中查找特定的模式。
   * 
   * 模式匹配操作的作用：
   * - 在序列数据中查找特定的模式
   * - 对应SQL的MATCH_RECOGNIZE子句
   * - 支持复杂的事件序列识别
   * - 用于流式数据处理和复杂事件处理（CEP）
   * 
   * 模式匹配的特点：
   * - 类似于正则表达式，但用于行序列
   * - 支持变量定义和模式组合
   * - 可以定义模式之间的约束关系
   * - 支持聚合和度量计算
   * 
   * 使用场景：
   * - 金融领域：识别股票价格模式、交易模式
   * - 物联网：传感器数据模式识别
   * - 日志分析：识别异常模式或攻击模式
   * - 用户行为分析：识别用户行为序列
   * 
   * 主要参数说明：
   * - pattern：模式定义，描述要查找的模式
   * - patternDefinitions：模式变量定义
   * - measures：度量计算，定义如何计算输出值
   * - partitionKeys：分区键，定义如何分组数据
   * - orderKeys：排序键，定义数据的顺序
   * - interval：时间间隔，定义模式匹配的时间窗口
   */
  public interface MatchFactory { // MatchFactory接口：模式匹配工厂接口，用于创建模式匹配操作符
    /** Creates a {@link Match}.
     * 创建一个模式匹配操作符。
     * 
     * @param input - 输入关系节点，即要在其中查找模式的关系
     * @param pattern - 模式表达式，定义要查找的模式
     * @param rowType - 行类型，定义输出结果的类型
     * @param strictStart - 是否严格匹配开始，true表示模式必须从第一行开始
     * @param strictEnd - 是否严格匹配结束，true表示模式必须匹配到最后一行
     * @param patternDefinitions - 模式定义映射，定义模式变量及其条件
     * @param measures - 度量映射，定义如何计算输出值
     * @param after - 匹配后的行为，定义如何跳过已匹配的行
     * @param subsets - 子集映射，定义模式变量的组合
     * @param allRows - 是否返回所有行，true表示返回所有匹配的行
     * @param partitionKeys - 分区键，定义如何分组数据
     * @param orderKeys - 排序键，定义数据的顺序
     * @param interval - 时间间隔，定义模式匹配的时间窗口，可以为null
     * @return - 返回创建的模式匹配关系节点
     */
    RelNode createMatch(RelNode input, RexNode pattern, // 创建模式匹配操作符的方法
        RelDataType rowType, boolean strictStart, boolean strictEnd, // 行类型、严格开始和严格结束标志
        Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures, // 模式定义和度量映射
        RexNode after, Map<String, ? extends SortedSet<String>> subsets, // 匹配后行为和子集映射
        boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys, // 是否返回所有行、分区键和排序键
        @Nullable RexNode interval); // 时间间隔
  }

  /**
   * Implementation of {@link MatchFactory}
   * that returns a {@link LogicalMatch}.
   * MatchFactory接口的实现类，返回标准的LogicalMatch关系节点。
   * 
   * 这个实现类创建的是逻辑模式匹配节点（LogicalMatch），它是模式匹配操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class MatchFactoryImpl implements MatchFactory { // MatchFactoryImpl类：模式匹配工厂实现类
    @Override public RelNode createMatch(RelNode input, RexNode pattern, // 重写createMatch方法，创建LogicalMatch节点
        RelDataType rowType, boolean strictStart, boolean strictEnd, // 输入关系、模式、行类型和严格标志
        Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures, // 模式定义和度量映射
        RexNode after, Map<String, ? extends SortedSet<String>> subsets, // 匹配后行为和子集映射
        boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys, // 是否返回所有行、分区键和排序键
        @Nullable RexNode interval) { // 时间间隔
      return LogicalMatch.create(input, rowType, pattern, strictStart, // 调用LogicalMatch.create工厂方法创建逻辑模式匹配节点
          strictEnd, patternDefinitions, measures, after, subsets, allRows, // 传递所有参数
          partitionKeys, orderKeys, interval); // 分区键、排序键和时间间隔
    }
  }

  /**
   * Can create a {@link Sample} of
   * the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的Sample关系节点。
   * 
   * SampleFactory接口定义了创建采样操作符的方法。采样操作用于从关系中随机抽取部分数据。
   * 
   * 采样操作的作用：
   * - 从表中随机抽取部分数据
   * - 用于数据分析和探索
   * - 减少数据量，提高查询性能
   * - 对应SQL的TABLESAMPLE子句
   * 
   * 采样操作的特点：
   * - 随机抽取数据，保证样本的代表性
   * - 可以指定采样比例或采样行数
   * - 支持不同的采样方法（如系统采样、伯努利采样）
   * - 采样结果是不确定的，每次可能不同
   * 
   * 使用场景：
   * - 数据探索：快速了解数据特征
   * - 性能测试：在小数据集上测试查询性能
   * - 机器学习：从大数据集中抽取训练样本
   * - 数据分析：对大数据集进行近似分析
   * 
   * parameter参数：
   * - 包含采样的所有参数信息
   * - 定义采样方法、采样比例、采样种子等
   * - 由RelOptSamplingParameters类封装
   */
  public interface SampleFactory { // SampleFactory接口：采样工厂接口，用于创建采样操作符
    /** Creates a {@link Sample}.
     * 创建一个采样操作符。
     * 
     * @param input - 输入关系节点，即要采样的关系
     * @param parameter - 采样参数，定义采样的方法、比例等
     * @return - 返回创建的采样关系节点
     */
    RelNode createSample(RelNode input, RelOptSamplingParameters parameter); // 创建采样操作符的方法
  }

  /**
   * Implementation of {@link SampleFactory}
   * that returns a {@link Sample}.
   * SampleFactory接口的实现类，返回标准的Sample关系节点。
   * 
   * 这个实现类创建的是采样节点（Sample），它是采样操作符的表示。
   * 注意：这里直接使用new Sample()构造函数，而不是LogicalSample.create()工厂方法。
   * 这可能是因为Sample节点没有对应的Logical子类，或者Sample本身就是逻辑表示。
   */
  private static class SampleFactoryImpl implements SampleFactory { // SampleFactoryImpl类：采样工厂实现类
    @Override public RelNode createSample(RelNode input, // 重写createSample方法，创建Sample节点
        RelOptSamplingParameters parameter) { // 输入关系和采样参数
      return new Sample(input.getCluster(), input, parameter); // 使用Sample构造函数创建采样节点，传入集群、输入和参数
    }
  }

  /**
   * Can create a {@link Spool} of
   * the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的Spool关系节点。
   * 
   * SpoolFactory接口定义了创建缓存操作符的方法。缓存操作用于临时存储数据，支持重复读取。
   * 
   * 缓存操作的作用：
   * - 将数据临时存储到缓存中
   * - 支持数据的重复读取
   * - 用于优化需要多次扫描相同数据的查询
   * - 避免重复计算或重复访问数据源
   * 
   * 缓存操作的特点：
   * - 可以缓存中间结果，提高查询性能
   * - 支持不同的读写策略
   * - 可以用于迭代查询和递归查询
   * - 缓存的生命周期与查询相关
   * 
   * 使用场景：
   * - 迭代查询：在迭代过程中重复使用相同的数据
   * - 递归查询：缓存递归的中间结果
   * - 重复扫描：避免重复访问相同的数据源
   * - 物化视图：临时物化中间结果
   * 
   * readType和writeType参数：
   * - 定义缓存的读写策略
   * - 可以是LAZY（延迟）或EAGER（急切）
   * - LAZY：按需读取或写入
   * - EAGER：立即读取或写入所有数据
   * 
   * @Experimental注解：
   * - 表示这是一个实验性功能
   * - API可能会在未来版本中发生变化
   * - 不建议在生产环境中使用
   */
  @Experimental // 标记为实验性功能，API可能会变化
  public interface SpoolFactory { // SpoolFactory接口：缓存工厂接口，用于创建缓存操作符
    /** Creates a {@link TableSpool}.
     * 创建一个表缓存操作符。
     * 
     * @param input - 输入关系节点，即要缓存的数据
     * @param readType - 读取类型，定义缓存的读取策略（LAZY或EAGER）
     * @param writeType - 写入类型，定义缓存的写入策略（LAZY或EAGER）
     * @param table - 优化表对象，表示缓存表
     * @return - 返回创建的表缓存关系节点
     */
    RelNode createTableSpool(RelNode input, Spool.Type readType, // 创建表缓存操作符的方法
        Spool.Type writeType, RelOptTable table); // 写入类型和缓存表
  }

  /**
   * Implementation of {@link SpoolFactory}
   * that returns Logical Spools.
   * SpoolFactory接口的实现类，返回逻辑缓存关系节点。
   * 
   * 这个实现类创建的是逻辑表缓存节点（LogicalTableSpool），它是缓存操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class SpoolFactoryImpl implements SpoolFactory { // SpoolFactoryImpl类：缓存工厂实现类
    @Override public RelNode createTableSpool(RelNode input, Spool.Type readType, // 重写createTableSpool方法，创建LogicalTableSpool节点
        Spool.Type writeType, RelOptTable table) { // 输入关系、读取类型、写入类型和缓存表
      return LogicalTableSpool.create(input, readType, writeType, table); // 调用LogicalTableSpool.create工厂方法创建逻辑表缓存节点
    }
  }

  /**
   * Can create a {@link RepeatUnion} of
   * the appropriate type for a rule's calling convention.
   * 可以创建适合此规则调用约定类型的RepeatUnion关系节点。
   * 
   * RepeatUnionFactory接口定义了创建重复联合操作符的方法。重复联合操作用于实现迭代查询和递归查询。
   * 
   * 重复联合操作的作用：
   * - 实现迭代查询和递归查询
   * - 对应SQL的WITH RECURSIVE子句
   * - 支持固定点迭代（Fixed-Point Iteration）
   * - 用于处理层次结构、图遍历等递归问题
   * 
   * 重复联合操作的特点：
   * - 包含两个部分：种子（seed）和迭代（iterative）
   * - 种子是初始结果集
   * - 迭代部分基于前一次的结果生成新的结果
   * - 重复执行直到满足终止条件
   * 
   * 工作原理：
   * 1. 从种子开始，生成初始结果
   * 2. 将初始结果与迭代部分结合，生成新的结果
   * 3. 将新结果与之前的结果合并
   * 4. 重复步骤2-3，直到没有新结果或达到迭代限制
   * 
   * 使用场景：
   * - 层次查询：查询组织结构、分类层次等
   * - 图遍历：查找路径、连通分量等
   * - 递归数据：处理递归定义的数据结构
   * - 传递闭包：计算关系的传递闭包
   * 
   * all参数：
   * - true：保留所有迭代的结果（包括重复行）
   * - false：只保留不重复的结果
   * 
   * iterationLimit参数：
   * - 限制最大迭代次数
   * - 防止无限循环
   * - -1表示无限制
   * 
   * @Experimental注解：
   * - 表示这是一个实验性功能
   * - API可能会在未来版本中发生变化
   * - 不建议在生产环境中使用
   */
  @Experimental // 标记为实验性功能，API可能会变化
  public interface RepeatUnionFactory { // RepeatUnionFactory接口：重复联合工厂接口，用于创建重复联合操作符
    /** Creates a {@link RepeatUnion}.
     * 创建一个重复联合操作符。
     * 
     * @param seed - 种子关系节点，迭代的初始结果
     * @param iterative - 迭代关系节点，基于前一次结果生成新结果
     * @param all - 是否保留所有结果，true表示保留所有（包括重复），false表示只保留不重复的
     * @param iterationLimit - 迭代限制，最大迭代次数，-1表示无限制
     * @param table - 优化表对象，用于存储中间结果
     * @return - 返回创建的重复联合关系节点
     */
    RelNode createRepeatUnion(RelNode seed, RelNode iterative, boolean all, // 创建重复联合操作符的方法
        int iterationLimit, RelOptTable table); // 迭代限制和优化表
  }

  /**
   * Implementation of {@link RepeatUnion}
   * that returns a {@link LogicalRepeatUnion}.
   * RepeatUnionFactory接口的实现类，返回标准的LogicalRepeatUnion关系节点。
   * 
   * 这个实现类创建的是逻辑重复联合节点（LogicalRepeatUnion），它是重复联合操作符的逻辑表示。
   * 在查询优化的逻辑阶段使用，不涉及具体的物理实现细节。
   */
  private static class RepeatUnionFactoryImpl implements RepeatUnionFactory { // RepeatUnionFactoryImpl类：重复联合工厂实现类
    @Override public RelNode createRepeatUnion(RelNode seed, RelNode iterative, // 重写createRepeatUnion方法，创建LogicalRepeatUnion节点
        boolean all, int iterationLimit, RelOptTable table) { // 种子关系、迭代关系、是否保留所有、迭代限制和优化表
      return LogicalRepeatUnion.create(seed, iterative, all, iterationLimit, table); // 调用LogicalRepeatUnion.create工厂方法创建逻辑重复联合节点
    }
  }

  /** Immutable record that contains an instance of each factory.
   * 不可变记录，包含每个工厂的一个实例。
   * 
   * Struct类是一个不可变的容器类，它包含了所有关系节点工厂的实例。
   * 这个类的主要作用是将所有工厂组合在一起，方便统一管理和传递。
   * 
   * 设计目的：
   * - 提供一个统一的容器来持有所有工厂实例
   * - 支持通过Context机制注入自定义的工厂实现
   * - 简化工厂的传递和管理
   * - 支持依赖注入和配置
   * 
   * 不可变性：
   * - 所有字段都是final的
   * - 构造后不能修改
   * - 线程安全
   * 
   * 使用场景：
   * - 作为RelBuilder的配置参数
   * - 存储在Context中供全局使用
   * - 支持自定义工厂的注入和替换
   */
  public static class Struct { // Struct类：不可变结构体，包含所有工厂实例
    public final FilterFactory filterFactory; // 过滤工厂，用于创建过滤操作符
    public final ProjectFactory projectFactory; // 投影工厂，用于创建投影操作符
    public final AggregateFactory aggregateFactory; // 聚合工厂，用于创建聚合操作符
    public final SortFactory sortFactory; // 排序工厂，用于创建排序操作符
    public final ExchangeFactory exchangeFactory; // 交换工厂，用于创建数据交换操作符
    public final SortExchangeFactory sortExchangeFactory; // 排序交换工厂，用于创建排序后数据交换操作符
    public final SetOpFactory setOpFactory; // 集合操作工厂，用于创建集合操作符
    public final JoinFactory joinFactory; // 连接工厂，用于创建连接操作符
    public final AsofJoinFactory asofJoinFactory; // ASOF连接工厂，用于创建ASOF连接操作符
    public final CorrelateFactory correlateFactory; // 关联工厂，用于创建关联操作符
    public final ValuesFactory valuesFactory; // 常量值工厂，用于创建常量值操作符
    public final TableScanFactory scanFactory; // 表扫描工厂，用于创建表扫描操作符
    public final TableFunctionScanFactory tableFunctionScanFactory; // 表函数扫描工厂，用于创建表函数扫描操作符
    public final SnapshotFactory snapshotFactory; // 快照工厂，用于创建快照操作符
    public final MatchFactory matchFactory; // 模式匹配工厂，用于创建模式匹配操作符
    public final SampleFactory sampleFactory; // 采样工厂，用于创建采样操作符
    public final SpoolFactory spoolFactory; // 缓存工厂，用于创建缓存操作符
    public final RepeatUnionFactory repeatUnionFactory; // 重复联合工厂，用于创建重复联合操作符

    private Struct(FilterFactory filterFactory, // 私有构造函数，初始化所有工厂实例
        ProjectFactory projectFactory, // 过滤工厂
        AggregateFactory aggregateFactory, // 聚合工厂
        SortFactory sortFactory, // 排序工厂
        ExchangeFactory exchangeFactory, // 交换工厂
        SortExchangeFactory sortExchangeFactory, // 排序交换工厂
        SetOpFactory setOpFactory, // 集合操作工厂
        JoinFactory joinFactory, // 连接工厂
        AsofJoinFactory asofJoinFactory, // ASOF连接工厂
        CorrelateFactory correlateFactory, // 关联工厂
        ValuesFactory valuesFactory, // 常量值工厂
        TableScanFactory scanFactory, // 表扫描工厂
        TableFunctionScanFactory tableFunctionScanFactory, // 表函数扫描工厂
        SnapshotFactory snapshotFactory, // 快照工厂
        SampleFactory sampleFactory, // 采样工厂
        MatchFactory matchFactory, // 模式匹配工厂
        SpoolFactory spoolFactory, // 缓存工厂
        RepeatUnionFactory repeatUnionFactory) { // 重复联合工厂
      this.filterFactory = requireNonNull(filterFactory, "filterFactory"); // 设置过滤工厂，确保非空
      this.projectFactory = requireNonNull(projectFactory, "projectFactory"); // 设置投影工厂，确保非空
      this.aggregateFactory = requireNonNull(aggregateFactory, "aggregateFactory"); // 设置聚合工厂，确保非空
      this.sortFactory = requireNonNull(sortFactory, "sortFactory"); // 设置排序工厂，确保非空
      this.exchangeFactory = requireNonNull(exchangeFactory, "exchangeFactory"); // 设置交换工厂，确保非空
      this.sortExchangeFactory = requireNonNull(sortExchangeFactory, "sortExchangeFactory"); // 设置排序交换工厂，确保非空
      this.setOpFactory = requireNonNull(setOpFactory, "setOpFactory"); // 设置集合操作工厂，确保非空
      this.joinFactory = requireNonNull(joinFactory, "joinFactory"); // 设置连接工厂，确保非空
      this.asofJoinFactory = requireNonNull(asofJoinFactory, "asofJoinFactory"); // 设置ASOF连接工厂，确保非空
      this.correlateFactory = requireNonNull(correlateFactory, "correlateFactory"); // 设置关联工厂，确保非空
      this.valuesFactory = requireNonNull(valuesFactory, "valuesFactory"); // 设置常量值工厂，确保非空
      this.scanFactory = requireNonNull(scanFactory, "scanFactory"); // 设置表扫描工厂，确保非空
      this.tableFunctionScanFactory = // 设置表函数扫描工厂，确保非空
          requireNonNull(tableFunctionScanFactory, "tableFunctionScanFactory"); 
      this.snapshotFactory = requireNonNull(snapshotFactory, "snapshotFactory"); // 设置快照工厂，确保非空
      this.sampleFactory = requireNonNull(sampleFactory, "sampleFactory"); // 设置采样工厂，确保非空
      this.matchFactory = requireNonNull(matchFactory, "matchFactory"); // 设置模式匹配工厂，确保非空
      this.spoolFactory = requireNonNull(spoolFactory, "spoolFactory"); // 设置缓存工厂，确保非空
      this.repeatUnionFactory = requireNonNull(repeatUnionFactory, "repeatUnionFactory"); // 设置重复联合工厂，确保非空
    }

    /**
     * 从上下文中创建Struct实例。
     * 
     * 这个方法实现了工厂的依赖注入机制：
     * 1. 首先尝试从上下文中获取整个Struct实例
     * 2. 如果没有找到，则尝试从上下文中获取各个工厂实例
     * 3. 如果某个工厂实例不存在，则使用默认的工厂实例
     * 
     * 这种设计允许用户自定义特定的工厂实现，而不需要自定义所有工厂。
     * 
     * @param context - 上下文对象，包含配置和状态信息
     * @return - 返回Struct实例，包含所有工厂实例（优先使用上下文中的，否则使用默认的）
     */
    public static Struct fromContext(Context context) { // 从上下文中创建Struct实例的静态方法
      Struct struct = context.unwrap(Struct.class); // 尝试从上下文中获取Struct实例
      if (struct != null) { // 如果找到了Struct实例
        return struct; // 直接返回该实例
      }
      return new Struct( // 否则，创建新的Struct实例
          context.maybeUnwrap(FilterFactory.class) // 尝试从上下文中获取FilterFactory实例
              .orElse(DEFAULT_FILTER_FACTORY), // 如果没有找到，使用默认的FilterFactory
          context.maybeUnwrap(ProjectFactory.class) // 尝试从上下文中获取ProjectFactory实例
              .orElse(DEFAULT_PROJECT_FACTORY), // 如果没有找到，使用默认的ProjectFactory
          context.maybeUnwrap(AggregateFactory.class) // 尝试从上下文中获取AggregateFactory实例
              .orElse(DEFAULT_AGGREGATE_FACTORY), // 如果没有找到，使用默认的AggregateFactory
          context.maybeUnwrap(SortFactory.class) // 尝试从上下文中获取SortFactory实例
              .orElse(DEFAULT_SORT_FACTORY), // 如果没有找到，使用默认的SortFactory
          context.maybeUnwrap(ExchangeFactory.class) // 尝试从上下文中获取ExchangeFactory实例
              .orElse(DEFAULT_EXCHANGE_FACTORY), // 如果没有找到，使用默认的ExchangeFactory
          context.maybeUnwrap(SortExchangeFactory.class) // 尝试从上下文中获取SortExchangeFactory实例
              .orElse(DEFAULT_SORT_EXCHANGE_FACTORY), // 如果没有找到，使用默认的SortExchangeFactory
          context.maybeUnwrap(SetOpFactory.class) // 尝试从上下文中获取SetOpFactory实例
              .orElse(DEFAULT_SET_OP_FACTORY), // 如果没有找到，使用默认的SetOpFactory
          context.maybeUnwrap(JoinFactory.class) // 尝试从上下文中获取JoinFactory实例
              .orElse(DEFAULT_JOIN_FACTORY), // 如果没有找到，使用默认的JoinFactory
          context.maybeUnwrap(AsofJoinFactory.class) // 尝试从上下文中获取AsofJoinFactory实例
              .orElse(DEFAULT_ASOFJOIN_FACTORY), // 如果没有找到，使用默认的AsofJoinFactory
          context.maybeUnwrap(CorrelateFactory.class) // 尝试从上下文中获取CorrelateFactory实例
              .orElse(DEFAULT_CORRELATE_FACTORY), // 如果没有找到，使用默认的CorrelateFactory
          context.maybeUnwrap(ValuesFactory.class) // 尝试从上下文中获取ValuesFactory实例
              .orElse(DEFAULT_VALUES_FACTORY), // 如果没有找到，使用默认的ValuesFactory
          context.maybeUnwrap(TableScanFactory.class) // 尝试从上下文中获取TableScanFactory实例
              .orElse(DEFAULT_TABLE_SCAN_FACTORY), // 如果没有找到，使用默认的TableScanFactory
          context.maybeUnwrap(TableFunctionScanFactory.class) // 尝试从上下文中获取TableFunctionScanFactory实例
              .orElse(DEFAULT_TABLE_FUNCTION_SCAN_FACTORY), // 如果没有找到，使用默认的TableFunctionScanFactory
          context.maybeUnwrap(SnapshotFactory.class) // 尝试从上下文中获取SnapshotFactory实例
              .orElse(DEFAULT_SNAPSHOT_FACTORY), // 如果没有找到，使用默认的SnapshotFactory
          context.maybeUnwrap(SampleFactory.class) // 尝试从上下文中获取SampleFactory实例
              .orElse(DEFAULT_SAMPLE_FACTORY), // 如果没有找到，使用默认的SampleFactory
          context.maybeUnwrap(MatchFactory.class) // 尝试从上下文中获取MatchFactory实例
              .orElse(DEFAULT_MATCH_FACTORY), // 如果没有找到，使用默认的MatchFactory
          context.maybeUnwrap(SpoolFactory.class) // 尝试从上下文中获取SpoolFactory实例
              .orElse(DEFAULT_SPOOL_FACTORY), // 如果没有找到，使用默认的SpoolFactory
          context.maybeUnwrap(RepeatUnionFactory.class) // 尝试从上下文中获取RepeatUnionFactory实例
              .orElse(DEFAULT_REPEAT_UNION_FACTORY)); // 如果没有找到，使用默认的RepeatUnionFactory
    }
  }
} // RelFactories类结束
