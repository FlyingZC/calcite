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
package org.apache.calcite.adapter.jdbc;

import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.InvalidRelException;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.SingleRel;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Intersect;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Minus;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.core.Union;
import org.apache.calcite.rel.core.Values;
import org.apache.calcite.rel.metadata.RelMdUtil;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.rel2sql.SqlImplementor;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexProgram;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.rex.RexVisitorImpl;
import org.apache.calcite.schema.ModifiableTable;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.trace.CalciteTrace;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

/**
 * Rules and relational operators for
 * {@link JdbcConvention}
 * calling convention.
 * 
 * 【类作用说明】:
 * 这个类定义了用于 JDBC 适配器的规则和关系运算符。
 * 
 * 核心功能:
 * 1. 提供将 Calcite 的逻辑关系算子(如 Join、Project、Filter 等)转换为 JDBC 物理实现(即生成 SQL 语句)的规则
 * 2. 定义了各种 JDBC 关系运算符的实现类(如 JdbcJoin、JdbcProject、JdbcFilter 等)
 * 3. 提供了用于创建 JDBC 关系表达式的工厂方法
 * 4. 这些规则和运算符使得 Calcite 可以将查询下推到 JDBC 数据源执行
 * 
 * 工作原理:
 * - Calcite 的查询优化器使用这些规则将逻辑计划转换为 JDBC 物理计划
 * - 每个 Rule 负责将特定类型的 RelNode 转换为对应的 JDBC 实现
 * - JDBC 实现最终会通过 JdbcImplementor 生成实际的 SQL 语句发送到数据库执行
 * 
 * 主要包含的内容:
 * - 各种工厂方法(PROJECT_FACTORY, FILTER_FACTORY 等)用于创建 JDBC 关系节点
 * - 各种转换规则(JdbcJoinRule, JdbcProjectRule 等)用于将逻辑节点转换为 JDBC 节点
 * - 各种 JDBC 关系算子实现(JdbcJoin, JdbcProject 等)实现了 JdbcRel 接口
 * - 辅助工具类和方法(如 CheckingUserDefinedFunctionVisitor)
 */
public class JdbcRules {
  private JdbcRules() {
    // 私有构造函数,防止实例化,因为这是一个工具类,只包含静态成员和静态方法
  }

  // 日志记录器,用于记录规划器相关的调试信息和警告
  protected static final Logger LOGGER = CalciteTrace.getPlannerTracer();

  // ===========================================
  // 工厂方法定义部分 - 用于创建 JDBC 关系节点
  // ===========================================

  // Project 算子的工厂方法
  // Project 是投影算子,用于选择、重命名或计算列
  static final RelFactories.ProjectFactory PROJECT_FACTORY =
      (input, hints, projects, fieldNames, variablesSet) -> {
        // 检查是否包含变量,JDBC Project 不支持变量(用于关联子查询等)
        checkArgument(variablesSet.isEmpty(),
            "JdbcProject does not allow variables");
        // 获取输入节点的集群信息,包含类型工厂等共享资源
        final RelOptCluster cluster = input.getCluster();
        // 根据投影表达式创建输出行的类型结构
        // RexUtil.createStructType 用于从表达式列表推导出输出行的类型
        final RelDataType rowType =
            RexUtil.createStructType(cluster.getTypeFactory(), projects,
                fieldNames, SqlValidatorUtil.F_SUGGESTER);
        // 创建并返回一个新的 JdbcProject 实例
        // JdbcProject 是 Project 算子在 JDBC 约定下的实现
        return new JdbcProject(cluster, input.getTraitSet(), input, projects,
            rowType);
      };

  // Filter 算子的工厂方法
  // Filter 是过滤算子,用于根据条件过滤行
  static final RelFactories.FilterFactory FILTER_FACTORY =
      (input, condition, variablesSet) -> {
        // 检查是否包含变量,JDBC Filter 不支持变量
        checkArgument(variablesSet.isEmpty(),
            "JdbcFilter does not allow variables");
        // 创建并返回一个新的 JdbcFilter 实例
        // JdbcFilter 是 Filter 算子在 JDBC 约定下的实现
        return new JdbcFilter(input.getCluster(),
            input.getTraitSet(), input, condition);
      };

  // Join 算子的工厂方法
  // Join 是连接算子,用于连接两个关系表
  static final RelFactories.JoinFactory JOIN_FACTORY =
      (left, right, hints, condition, variablesSet, joinType, semiJoinDone) => {
        // 获取左表所在的集群信息
        final RelOptCluster cluster = left.getCluster();
        // 创建特征集合,包含 JDBC 约定
        // traitSet 描述了关系节点的物理属性,如约定、排序等
        final RelTraitSet traitSet =
            cluster.traitSetOf(
                requireNonNull(left.getConvention(), "left.getConvention()"));
        try {
          // 创建并返回一个新的 JdbcJoin 实例
          // JdbcJoin 是 Join 算子在 JDBC 约定下的实现
          return new JdbcJoin(cluster, traitSet, left, right, condition,
              variablesSet, joinType);
        } catch (InvalidRelException e) {
          // 如果创建失败,抛出断言错误,这通常表示代码中的 bug
          throw new AssertionError(e);
        }
      };

  // Correlate 算子的工厂方法
  // Correlate 是关联算子,用于实现关联子查询
  static final RelFactories.CorrelateFactory CORRELATE_FACTORY =
      (left, right, hints, correlationId, requiredColumns, joinType) => {
        // JDBC 不支持 Correlate 算子,直接抛出不支持异常
        // 关联子查询需要特殊的处理方式,不能简单地转换为 SQL
        throw new UnsupportedOperationException("JdbcCorrelate");
      };

  // Sort 算子的工厂方法
  // Sort 是排序算子,用于对结果进行排序
  public static final RelFactories.SortFactory SORT_FACTORY =
      (input, collation, offset, fetch) => {
        // JDBC 不支持独立的 Sort 算子,因为排序通常在数据库内部处理
        // 排序会通过 JdbcSortRule 转换为 SQL 的 ORDER BY 子句
        throw new UnsupportedOperationException("JdbcSort");
      };

  // Exchange 算子的工厂方法
  // Exchange 是交换算子,用于分布式数据重分布
  public static final RelFactories.ExchangeFactory EXCHANGE_FACTORY =
      (input, distribution) => {
        // JDBC 不支持 Exchange 算子,因为 JDBC 连接的是单个数据库实例
        // 数据重分布在分布式系统中才有意义
        throw new UnsupportedOperationException("JdbcExchange");
      };

  // SortExchange 算子的工厂方法
  // SortExchange 是排序交换算子,结合了排序和数据重分布
  public static final RelFactories.SortExchangeFactory SORT_EXCHANGE_FACTORY =
      (input, distribution, collation) => {
        // JDBC 不支持 SortExchange 算子
        throw new UnsupportedOperationException("JdbcSortExchange");
      };

  // Aggregate 算子的工厂方法
  // Aggregate 是聚合算子,用于执行 GROUP BY 和聚合函数(SUM、COUNT 等)
  public static final RelFactories.AggregateFactory AGGREGATE_FACTORY =
      (input, hints, groupSet, groupSets, aggCalls) => {
        // 获取输入节点的集群信息
        final RelOptCluster cluster = input.getCluster();
        // 创建特征集合,包含 JDBC 约定
        final RelTraitSet traitSet =
            cluster.traitSetOf(
                requireNonNull(input.getConvention(), "input.getConvention()"));
        try {
          // 创建并返回一个新的 JdbcAggregate 实例
          // JdbcAggregate 是 Aggregate 算子在 JDBC 约定下的实现
          return new JdbcAggregate(cluster, traitSet, input, groupSet,
              groupSets, aggCalls);
        } catch (InvalidRelException e) {
          // 如果创建失败,抛出断言错误
          throw new AssertionError(e);
        }
      };

  // Match 算子的工厂方法
  // Match 是模式匹配算子,用于实现 SQL 的 MATCH_RECOGNIZE 子句(用于复杂事件处理)
  public static final RelFactories.MatchFactory MATCH_FACTORY =
      (input, pattern, rowType, strictStart, strictEnd, patternDefinitions,
          measures, after, subsets, allRows, partitionKeys, orderKeys,
          interval) => {
        // JDBC 不支持 Match 算子,因为 MATCH_RECOGNIZE 是高级特性,大多数数据库不支持
        throw new UnsupportedOperationException("JdbcMatch");
      };

  // SetOp(集合操作)算子的工厂方法
  // SetOp 包括 UNION、INTERSECT、EXCEPT 等集合操作
  public static final RelFactories.SetOpFactory SET_OP_FACTORY =
      (kind, inputs, all) => {
        // 获取第一个输入节点的集群信息
        RelNode input = inputs.get(0);
        RelOptCluster cluster = input.getCluster();
        // 创建特征集合,包含 JDBC 约定
        final RelTraitSet traitSet =
            cluster.traitSetOf(
                requireNonNull(input.getConvention(), "input.getConvention()"));
        // 根据集合操作类型创建对应的 JDBC 实现
        switch (kind) {
        case UNION:
          // 创建 JdbcUnion,对应 SQL 的 UNION 或 UNION ALL
          return new JdbcUnion(cluster, traitSet, inputs, all);
        case INTERSECT:
          // 创建 JdbcIntersect,对应 SQL 的 INTERSECT 或 INTERSECT ALL
          return new JdbcIntersect(cluster, traitSet, inputs, all);
        case EXCEPT:
          // 创建 JdbcMinus,对应 SQL 的 EXCEPT 或 EXCEPT ALL
          return new JdbcMinus(cluster, traitSet, inputs, all);
        default:
          // 未知的集合操作类型,抛出断言错误
          throw new AssertionError("unknown: " + kind);
        }
      };

  // Values 算子的工厂方法
  // Values 是常量值算子,用于生成常量行
  public static final RelFactories.ValuesFactory VALUES_FACTORY =
      (cluster, rowType, tuples) => {
        // JDBC 不支持独立的 Values 算子
        // 常量值通常会被转换为 SELECT ... UNION ALL ... 的形式
        throw new UnsupportedOperationException();
      };

  // TableScan 算子的工厂方法
  // TableScan 是表扫描算子,用于从表中读取数据
  public static final RelFactories.TableScanFactory TABLE_SCAN_FACTORY =
      (toRelContext, table) => {
        // JDBC 不使用工厂方法创建 TableScan
        // 表扫描通常由 JdbcTableScan 直接创建
        throw new UnsupportedOperationException();
      };

  // Snapshot 算子的工厂方法
  // Snapshot 是快照算子,用于时态查询
  public static final RelFactories.SnapshotFactory SNAPSHOT_FACTORY =
      (input, period) => {
        // JDBC 不支持 Snapshot 算子
        throw new UnsupportedOperationException();
      };

  /** A {@link RelBuilderFactory} that creates a {@link RelBuilder} that will
   * create JDBC relational expressions for everything.
   * 
   * 【成员变量说明】:
   * JDBC 构建器工厂,用于创建能够生成 JDBC 关系表达式的 RelBuilder。
   * 
   * RelBuilder 是 Calcite 提供的用于构建关系代数树的工具类。
   * 这个 JDBC_BUILDER 配置了各种工厂方法,使得 RelBuilder 创建的所有节点都是 JDBC 实现。
   * 
   * 使用场景:
   * - 当需要以编程方式构建 JDBC 关系表达式树时使用
   * - 提供了一种统一的方式来创建各种 JDBC 算子
   * 
   * 配置的工厂包括:
   * - PROJECT_FACTORY: 创建 JdbcProject
   * - FILTER_FACTORY: 创建 JdbcFilter
   * - JOIN_FACTORY: 创建 JdbcJoin
   * - SORT_FACTORY, EXCHANGE_FACTORY 等: 虽然不支持,但也配置了
   * - AGGREGATE_FACTORY: 创建 JdbcAggregate
   * - MATCH_FACTORY, SET_OP_FACTORY 等: 其他算子的工厂
   */
  public static final RelBuilderFactory JDBC_BUILDER =
      RelBuilder.proto(
          Contexts.of(PROJECT_FACTORY,
              FILTER_FACTORY,
              JOIN_FACTORY,
              SORT_FACTORY,
              EXCHANGE_FACTORY,
              SORT_EXCHANGE_FACTORY,
              AGGREGATE_FACTORY,
              MATCH_FACTORY,
              SET_OP_FACTORY,
              VALUES_FACTORY,
              TABLE_SCAN_FACTORY,
              SNAPSHOT_FACTORY));

  // ===========================================
  // 规则创建方法
  // ===========================================

  /** Creates a list of rules with the given JDBC convention instance.
   * 
   * 【方法作用说明】:
   * 创建 JDBC 转换规则列表。
   * 
   * 这个方法为指定的 JDBC 约定创建所有必要的转换规则。
   * 这些规则用于将 Calcite 的逻辑关系算子转换为 JDBC 物理实现。
   * 
   * @param out 目标 JDBC 约定,指定要转换到哪个 JDBC 数据源
   * @return 包含所有 JDBC 转换规则的列表
   * 
   * 工作流程:
   * 1. 创建一个 ImmutableList.Builder 用于构建规则列表
   * 2. 调用 foreachRule 方法遍历并添加所有规则
   * 3. 返回构建好的规则列表
   * 
   * 包含的规则:
   * - JdbcToEnumerableConverterRule: JDBC 到可枚举的转换规则
   * - JdbcJoinRule: Join 转换规则
   * - JdbcProjectRule: Project 转换规则
   * - JdbcFilterRule: Filter 转换规则
   * - JdbcAggregateRule: Aggregate 转换规则
   * - JdbcSortRule: Sort 转换规则
   * - JdbcUnionRule: Union 转换规则
   * - JdbcIntersectRule: Intersect 转换规则
   * - JdbcMinusRule: Minus 转换规则
   * - JdbcTableModificationRule: 表修改转换规则
   * - JdbcValuesRule: Values 转换规则
   */
  public static List<RelOptRule> rules(JdbcConvention out) {
    // 创建不可变列表构建器,用于构建规则列表
    final ImmutableList.Builder<RelOptRule> b = ImmutableList.builder();
    // 调用 foreachRule 方法,为每个规则调用 b::add 添加到构建器
    foreachRule(out, b::add);
    // 构建并返回规则列表
    return b.build();
  }

  /** Creates a list of rules with the given JDBC convention instance
   * and builder factory.
   * 
   * 【方法作用说明】:
   * 创建带有自定义 RelBuilderFactory 的 JDBC 转换规则列表。
   * 
   * 这是 rules(JdbcConvention out) 方法的重载版本,允许指定自定义的 RelBuilderFactory。
   * 
   * @param out 目标 JDBC 约定
   * @param relBuilderFactory 自定义的 RelBuilderFactory,用于创建 RelBuilder
   * @return 包含所有 JDBC 转换规则的列表
   * 
   * 与单参数版本的区别:
   * - 这个版本允许为规则配置自定义的 RelBuilderFactory
   * - 每个规则都会使用配置的 RelBuilderFactory 来创建
   * - 提供了更大的灵活性,可以自定义关系表达式的构建方式
   */
  public static List<RelOptRule> rules(JdbcConvention out,
      RelBuilderFactory relBuilderFactory) {
    // 创建不可变列表构建器
    final ImmutableList.Builder<RelOptRule> b = ImmutableList.builder();
    // 调用 foreachRule 方法,为每个规则创建配置了 RelBuilderFactory 的版本
    // r.config.withRelBuilderFactory(relBuilderFactory).toRule() 会为规则配置构建器工厂
    foreachRule(out, r =>
        b.add(r.config.withRelBuilderFactory(relBuilderFactory).toRule()));
    // 构建并返回规则列表
    return b.build();
  }

  /** Private helper method to iterate over all JDBC rules and apply a consumer.
   * 
   * 【方法作用说明】:
   * 遍历所有 JDBC 规则并对每个规则应用消费者操作。
   * 
   * 这是一个私有辅助方法,用于避免重复代码。
   * 它遍历所有 JDBC 转换规则,并对每个规则执行指定的操作。
   * 
   * @param out 目标 JDBC 约定
   * @param consumer 对每个规则执行的操作,通常是添加到某个集合中
   * 
   * 设计模式:
   * - 使用了函数式编程的 Consumer 模式
   * - 允许调用者自定义对每个规则的处理方式
   * - 提高了代码的复用性和灵活性
   * 
   * 遍历的规则顺序:
   * 1. JdbcToEnumerableConverterRule - 必须首先转换到可枚举约定
   * 2. JdbcJoinRule - Join 转换
   * 3. JdbcProjectRule - Project 转换
   * 4. JdbcFilterRule - Filter 转换
   * 5. JdbcAggregateRule - Aggregate 转换
   * 6. JdbcSortRule - Sort 转换
   * 7. JdbcUnionRule - Union 转换
   * 8. JdbcIntersectRule - Intersect 转换
   * 9. JdbcMinusRule - Minus 转换
   * 10. JdbcTableModificationRule - 表修改转换
   * 11. JdbcValuesRule - Values 转换
   */
  private static void foreachRule(JdbcConvention out,
      Consumer<RelRule<?>> consumer) {
    // 接受并处理 JDBC 到可枚举的转换规则
    consumer.accept(JdbcToEnumerableConverterRule.create(out));
    // 接受并处理 Join 转换规则
    consumer.accept(JdbcJoinRule.create(out));
    // 接受并处理 Project 转换规则
    consumer.accept(JdbcProjectRule.create(out));
    // 接受并处理 Filter 转换规则
    consumer.accept(JdbcFilterRule.create(out));
    // 接受并处理 Aggregate 转换规则
    consumer.accept(JdbcAggregateRule.create(out));
    // 接受并处理 Sort 转换规则
    consumer.accept(JdbcSortRule.create(out));
    // 接受并处理 Union 转换规则
    consumer.accept(JdbcUnionRule.create(out));
    // 接受并处理 Intersect 转换规则
    consumer.accept(JdbcIntersectRule.create(out));
    // 接受并处理 Minus 转换规则
    consumer.accept(JdbcMinusRule.create(out));
    // 接受并处理表修改转换规则
    consumer.accept(JdbcTableModificationRule.create(out));
    // 接受并处理 Values 转换规则
    consumer.accept(JdbcValuesRule.create(out));
  }

  // ===========================================
  // 抽象基类定义
  // ===========================================

  /** Abstract base class for rule that converts to JDBC.
   * 
   * 【类作用说明】:
   * JDBC 转换规则的抽象基类。
   * 
   * 所有将关系算子转换为 JDBC 实现的规则都继承自这个类。
   * 这个类继承自 ConverterRule,提供了转换规则的基本框架。
   * 
   * 继承关系:
   * - JdbcConverterRule extends ConverterRule
   * - 所有具体的 JDBC 规则(JdbcJoinRule、JdbcProjectRule 等)都继承自 JdbcConverterRule
   * 
   * 提供的功能:
   * - 统一的转换规则接口
   * - 约定了输入和输出的约定
   * - 提供了基本的转换逻辑框架
   * 
   * 使用方式:
   * - 子类需要实现 convert 方法来定义具体的转换逻辑
   * - 子类通过 Config 配置转换规则的各种属性
   */
  abstract static class JdbcConverterRule extends ConverterRule {
    /**
     * 【构造方法说明】:
     * 创建 JDBC 转换规则实例。
     * 
     * @param config 规则配置对象,包含转换规则的各种属性:
     *               - from: 源约定(通常是 Convention.NONE)
     *               - to: 目标约定(JdbcConvention)
     *               - description: 规则描述
     *               - operandFactory: 操作数工厂
     *               - ruleFactory: 规则工厂
     * 
     * 构造方法功能:
     * - 调用父类 ConverterRule 的构造方法初始化规则
     * - 配置规则的基本属性和行为
     */
    protected JdbcConverterRule(Config config) {
      // 调用父类构造方法,初始化转换规则
      super(config);
    }
  }

  // ===========================================
  // Join 相关的规则和实现
  // ===========================================

  /** Rule that converts a join to JDBC.
   * 
   * 【类作用说明】:
   * 将 Join 关系算子转换为 JDBC Join 实现的规则。
   * 
   * 这个规则负责将逻辑上的 Join 算子转换为可以在 JDBC 数据源上执行的 SQL JOIN 语句。
   * 
   * 转换条件:
   * 1. Join 类型必须被 JDBC 方言支持
   * 2. Join 条件必须是 JDBC 支持的表达式(不能包含子查询等复杂逻辑)
   * 3. 不能是 SEMI 或 ANTI Join(这些需要特殊处理)
   * 
   * 支持的 Join 类型:
   * - INNER JOIN: 内连接
   * - LEFT OUTER JOIN: 左外连接
   * - RIGHT OUTER JOIN: 右外连接
   * - FULL OUTER JOIN: 全外连接(需要数据库支持)
   * 
   * 不支持的 Join 类型:
   * - SEMI JOIN: 半连接(IN 子查询)
   * - ANTI JOIN: 反半连接(NOT IN 子查询)
   */
  public static class JdbcJoinRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcJoinRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcJoinRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE(逻辑约定)
     * - to: out(JDBC 约定)
     * - operand: Join.class(转换的目标类型)
     * - ruleFactory: JdbcJoinRule::new(规则工厂)
     * - description: "JdbcJoinRule"
     */
    public static JdbcJoinRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换:从 Join 类,从 NONE 约定,到 out 约定
          .withConversion(Join.class, Convention.NONE, out, "JdbcJoinRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcJoinRule::new)
          // 转换为规则实例
          .toRule(JdbcJoinRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcJoinRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 Join 关系节点转换为 JdbcJoin 节点。
     * 
     * @param rel 要转换的 Join 关系节点
     * @return 转换后的 JdbcJoin 节点,如果转换失败则返回 null
     * 
     * 转换逻辑:
     * 1. 检查 Join 类型,SEMI 和 ANTI Join 不支持转换
     * 2. 调用 convert(Join, boolean) 方法执行实际转换
     * 
     * 返回 null 的情况:
     * - Join 类型是 SEMI 或 ANTI
     * - Join 条件包含不支持的表达式
     * - 输入转换失败
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Join 类型
      final Join join = (Join) rel;
      // 根据 Join 类型判断是否可以转换
      switch (join.getJoinType()) {
      case SEMI:
      case ANTI:
        // SEMI 和 ANTI Join 无法直接转换为 SQL
        // 因为它们返回的列数比普通 Join 少
        // 需要特殊处理或使用子查询
        return null;
      default:
        // 其他类型的 Join 调用 convert 方法进行转换
        // true 表示需要转换输入的约定
        return convert(join, true);
      }
    }

    /**
     * Converts a {@code Join} into a {@code JdbcJoin}.
     * 
     * 【方法作用说明】:
     * 将 Join 算子转换为 JdbcJoin 算子。
     * 
     * @param join 要转换的 Join 算子
     * @param convertInputTraits 是否转换输入的约定为 Join 的 JDBC 约定
     * @return 转换后的 JdbcJoin 实例,如果转换失败则返回 null
     * 
     * 转换步骤:
     * 1. 创建新输入列表
     * 2. 如果需要,将每个输入转换为 JDBC 约定
     * 3. 检查 Join 条件是否支持
     * 4. 创建 JdbcJoin 实例
     * 
     * 转换失败的情况:
     * - 输入节点无法转换为 JDBC 约定
     * - Join 条件包含不支持的表达式
     * - 创建 JdbcJoin 时抛出 InvalidRelException
     * 
     * 参数说明:
     * - convertInputTraits=true: 将输入节点转换为 JDBC 约定(正常情况)
     * - convertInputTraits=false: 不转换输入节点(特殊场景,如嵌套转换)
     */
    public @Nullable RelNode convert(Join join, boolean convertInputTraits) {
      // 创建新的输入节点列表
      final List<RelNode> newInputs = new ArrayList<>();
      // 遍历 Join 的所有输入(通常是左右两个表)
      for (RelNode input : join.getInputs()) {
        // 如果需要转换输入约定,且输入的约定不是目标 JDBC 约定
        if (convertInputTraits && input.getConvention() != getOutTrait()) {
          // 将输入节点转换为 JDBC 约定
          // input.getTraitSet().replace(out) 创建新的特征集合,将约定替换为 JDBC 约定
          input =
              convert(input,
                  input.getTraitSet().replace(out));
        }
        // 将处理后的输入添加到新输入列表
        newInputs.add(input);
      }
      // 如果需要转换输入约定,检查 Join 条件是否支持
      if (convertInputTraits && !canJoinOnCondition(join.getCondition())) {
        // Join 条件不支持,返回 null
        return null;
      }
      try {
        // 创建并返回 JdbcJoin 实例
        // 参数包括:集群、特征集合、左输入、右输入、连接条件、变量集合、连接类型
        return new JdbcJoin(
            join.getCluster(),
            join.getTraitSet().replace(out),
            newInputs.get(0),  // 左输入
            newInputs.get(1),  // 右输入
            join.getCondition(),
            join.getVariablesSet(),
            join.getJoinType());
      } catch (InvalidRelException e) {
        // 创建 JdbcJoin 失败,记录调试日志并返回 null
        LOGGER.debug(e.toString());
        return null;
      }
    }

    /**
     * Returns whether a condition is supported by {@link JdbcJoin}.
     * 
     * 【方法作用说明】:
     * 判断 Join 条件是否被 JdbcJoin 支持。
     * 
     * 这个方法检查 Join 条件表达式是否可以转换为 SQL 的 ON 子句。
     * 
     * @param node Join 条件表达式(RexNode)
     * @return 如果条件支持则返回 true,否则返回 false
     * 
     * 支持的表达式类型:
     * - DYNAMIC_PARAM: 动态参数(?)
     * - INPUT_REF: 输入引用(列引用)
     * - LITERAL: 字面量(TRUE、FALSE、数字、字符串等)
     * - AND: 逻辑与
     * - OR: 逻辑或
     * - IS_NULL, IS_NOT_NULL: 空值判断
     * - IS_TRUE, IS_NOT_TRUE: 真值判断
     * - IS_FALSE, IS_NOT_FALSE: 假值判断
     * - EQUALS: 等于(=)
     * - NOT_EQUALS: 不等于(<> 或 !=)
     * - GREATER_THAN: 大于(>)
     * - GREATER_THAN_OR_EQUAL: 大于等于(>=)
     * - LESS_THAN: 小于(<)
     * - LESS_THAN_OR_EQUAL: 小于等于(<=)
     * - IS_NOT_DISTINCT_FROM: 不区分 NULL 的相等
     * - CAST: 类型转换
     * 
     * 不支持的表达式:
     * - 子查询
     * - 聚合函数
     * - 窗口函数
     * - 用户定义函数
     * - 其他复杂表达式
     * 
     * 递归检查:
     * - 对于复合表达式(如 AND、OR),递归检查所有操作数
     * - 只有所有操作数都支持时,整个表达式才支持
     */
    private static boolean canJoinOnCondition(RexNode node) {
      // 操作数列表,用于存储复合表达式的子表达式
      final List<RexNode> operands;
      // 根据表达式类型进行判断
      switch (node.getKind()) {
      case DYNAMIC_PARAM:
        // 动态参数支持
      case INPUT_REF:
        // 输入引用(列引用)支持
      case LITERAL:
        // 字面量支持
        // Join 条件中的字面量通常是 TRUE 或 FALSE
        return true;
      case AND:
        // 逻辑与支持
      case OR:
        // 逻辑或支持
      case IS_NULL:
        // IS NULL 支持
      case IS_NOT_NULL:
        // IS NOT NULL 支持
      case IS_TRUE:
        // IS TRUE 支持
      case IS_NOT_TRUE:
        // IS NOT TRUE 支持
      case IS_FALSE:
        // IS FALSE 支持
      case IS_NOT_FALSE:
        // IS NOT FALSE 支持
      case EQUALS:
        // 等于支持
      case NOT_EQUALS:
        // 不等于支持
      case GREATER_THAN:
        // 大于支持
      case GREATER_THAN_OR_EQUAL:
        // 大于等于支持
      case LESS_THAN:
        // 小于支持
      case LESS_THAN_OR_EQUAL:
        // 小于等于支持
      case IS_NOT_DISTINCT_FROM:
        // 不区分 NULL 的相等支持
      case CAST:
        // 类型转换支持
        // 获取复合表达式的所有操作数
        operands = ((RexCall) node).getOperands();
        // 递归检查每个操作数
        for (RexNode operand : operands) {
          // 如果任何一个操作数不支持,则整个表达式不支持
          if (!canJoinOnCondition(operand)) {
            return false;
          }
        }
        // 所有操作数都支持,返回 true
        return true;
      default:
        // 默认情况不支持
        return false;
      }
    }

    /**
     * 【方法作用说明】:
     * 判断规则是否匹配给定的规则调用。
     * 
     * 这个方法在优化器考虑应用规则时调用,用于确定规则是否适用。
     * 
     * @param call 规则调用对象,包含关系节点等信息
     * @return 如果规则匹配则返回 true,否则返回 false
     * 
     * 匹配条件:
     * - Join 类型必须被 JDBC 方言支持
     * 
     * 方言支持检查:
     * - 不同的数据库对 Join 类型的支持程度不同
     * - 例如,某些数据库可能不支持 FULL OUTER JOIN
     * - SqlDialect.supportsJoinType() 方法用于检查特定 Join 类型的支持
     */
    @Override public boolean matches(RelOptRuleCall call) {
      // 调用对象中的第一个关系节点,即 Join 节点
      Join join = call.rel(0);
      // 获取 Join 类型(INNER、LEFT、RIGHT、FULL 等)
      JoinRelType joinType = join.getJoinType();
      // 检查 JDBC 方言是否支持该 Join 类型
      return ((JdbcConvention) getOutConvention()).dialect.supportsJoinType(joinType);
    }
  }

  /** Join operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Join 算子。
   * 
   * 这个类表示一个可以在 JDBC 数据源上执行的 Join 操作。
   * 它实现了 JdbcRel 接口,可以被 JdbcImplementor 转换为 SQL JOIN 语句。
   * 
   * 继承关系:
   * - JdbcJoin extends Join
   * - Join extends AbstractJoin
   * - AbstractJoin extends BiRel
   * - BiRel extends AbstractRelNode
   * - JdbcJoin implements JdbcRel
   * 
   * 实现的功能:
   * - 存储 Join 的所有信息(左右输入、连接条件、连接类型等)
   * - 提供 copy 方法用于创建副本
   * - 实现 implement 方法用于生成 SQL
   * - 提供成本估算和行数估算
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL JOIN 语句
   * - 生成的 SQL 格式: SELECT ... FROM left_table [JOIN_TYPE] right_table ON condition
   */
  public static class JdbcJoin extends Join implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcJoin 实例。
     * 
     * @param cluster 关系集群,包含类型工厂等共享资源
     * @param traitSet 特征集合,描述节点的物理属性
     * @param left 左输入关系节点
     * @param right 右输入关系节点
     * @param condition Join 条件表达式
     * @param variablesSet 相关变量集合(用于关联子查询)
     * @param joinType Join 类型(INNER、LEFT、RIGHT、FULL 等)
     * @throws InvalidRelException 如果 Join 无效则抛出异常
     * 
     * 构造方法功能:
     * - 调用父类 Join 的构造方法初始化 Join 节点
     * - 传入空提示列表(ImmutableList.of())
     * - 验证约定必须是 JdbcConvention
     */
    public JdbcJoin(RelOptCluster cluster, RelTraitSet traitSet,
        RelNode left, RelNode right, RexNode condition,
        Set<CorrelationId> variablesSet, JoinRelType joinType)
        throws InvalidRelException {
      // 调用父类构造方法,传入空提示列表
      super(cluster, traitSet, ImmutableList.of(), left, right, condition, variablesSet, joinType);
    }

    /**
     * 【构造方法说明】:
     * 已废弃的构造方法,将在 2.0 版本之前移除。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param left 左输入
     * @param right 右输入
     * @param condition Join 条件
     * @param joinType Join 类型
     * @param variablesStopped 已停止的变量集合(使用字符串而不是 CorrelationId)
     * @throws InvalidRelException 如果 Join 无效
     * 
     * 废弃原因:
     * - 使用字符串表示变量已被 CorrelationId 替代
     * - 新代码应该使用新的构造方法
     */
    @Deprecated // to be removed before 2.0
    protected JdbcJoin(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        RelNode left,
        RelNode right,
        RexNode condition,
        JoinRelType joinType,
        Set<String> variablesStopped)
        throws InvalidRelException {
      // 调用新构造方法,将字符串集合转换为 CorrelationId 集合
      this(cluster, traitSet, left, right, condition,
          CorrelationId.setOf(variablesStopped), joinType);
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcJoin 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param condition 新的 Join 条件
     * @param left 新的左输入
     * @param right 新的右输入
     * @param joinType 新的 Join 类型
     * @param semiJoinDone 半连接完成标志(未使用)
     * @return 新的 JdbcJoin 实例
     * 
     * 使用场景:
     * - 优化器在应用规则时创建修改后的节点
     * - 改变 Join 的某些属性而不改变其他属性
     * 
     * 异常处理:
     * - 如果创建失败,转换为 AssertionError,表示代码中的 bug
     */
    @Override public JdbcJoin copy(RelTraitSet traitSet, RexNode condition,
        RelNode left, RelNode right, JoinRelType joinType,
        boolean semiJoinDone) {
      try {
        // 创建并返回新的 JdbcJoin 实例
        return new JdbcJoin(getCluster(), traitSet, left, right,
            condition, variablesSet, joinType);
      } catch (InvalidRelException e) {
        // 语义错误不应该发生,必须是 bug
        // 转换为内部错误
        throw new AssertionError(e);
      }
    }

    /**
     * 【方法作用说明】:
     * 计算 JdbcJoin 的自身成本。
     * 
     * @param planner 优化器
     * @param mq 元数据查询对象
     * @return 估算的成本,包含行数、CPU 成本、IO 成本
     * 
     * 成本计算策略:
     * - 行数:使用元数据查询获取估算的行数
     * - CPU 成本:0(JDBC Join 在数据库中执行,CPU 成本由数据库承担)
     * - IO 成本:0(JDBC Join 的 IO 成本由数据库承担)
     * 
     * 为什么 CPU 和 IO 成本为 0:
     * - JDBC 操作被下推到数据库执行
     * - Calcite 只负责发送 SQL 和接收结果
     * - 实际的计算和 IO 成本在数据库中发生
     * 
     * 成本模型:
     * - Cost = (rowCount, cpu, io)
     * - 这里简化为只有行数
     */
    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
        RelMetadataQuery mq) {
      // 使用元数据查询获取估算的行数
      double rowCount = mq.getRowCount(this);

      // 创建成本对象,只包含行数,CPU 和 IO 成本为 0
      return planner.getCostFactory().makeCost(rowCount, 0, 0);
    }

    /**
     * 【方法作用说明】:
     * 估算 JdbcJoin 的输出行数。
     * 
     * @param mq 元数据查询对象
     * @return 估算的输出行数
     * 
     * 估算策略:
     * - 返回左右输入行数的最大值
     * - 这是一个保守的估算,通常大于实际行数
     * 
     * 为什么使用最大值:
     * - INNER JOIN: 行数 <= min(left, right)
     * - LEFT JOIN: 行数 = left
     * - RIGHT JOIN: 行数 = right
     * - FULL JOIN: 行数 <= left + right
     * - 使用最大值是一个安全的上界
     * 
     * 更精确的估算:
     * - 可以使用统计信息和选择率
     * - 但这里使用简单的最大值策略
     */
    @Override public double estimateRowCount(RelMetadataQuery mq) {
      // 获取左输入的估算行数
      final double leftRowCount = mq.getRowCount(left);
      // 获取右输入的估算行数
      final double rightRowCount = mq.getRowCount(right);
      // 返回两者中的最大值
      return Math.max(leftRowCount, rightRowCount);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcJoin,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL 和相关表达式
     * 
     * 实现过程:
     * - 调用 implementor.implement(this) 方法
     * - JdbcImplementor 会将 JdbcJoin 转换为 SQL JOIN 语句
     * - 生成的 SQL 格式: SELECT ... FROM table1 [JOIN_TYPE] table2 ON condition
     * 
     * SQL 生成示例:
     * - INNER JOIN: SELECT * FROM t1 INNER JOIN t2 ON t1.id = t2.id
     * - LEFT JOIN: SELECT * FROM t1 LEFT JOIN t2 ON t1.id = t2.id
     * 
     * 返回值:
     * - JdbcImplementor.Result 对象
     * - 包含生成的 SQL 字符串
     * - 包含表达式到列名的映射
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  /** Calc operator implemented in JDBC convention.
   *
   * @see org.apache.calcite.rel.core.Calc
   * */
  @Deprecated // to be removed before 2.0
  public static class JdbcCalc extends SingleRel implements JdbcRel {
    /**
     * 【成员变量说明】:
     * RexProgram,表示 Calc 算子的程序。
     * 
     * RexProgram 是一个组合的算子,可以同时表示 Project 和 Filter。
     * 它包含:
     * - 输入表达式列表
     * - 项目表达式列表(用于计算输出列)
     * - 条件表达式(用于过滤)
     * - 输出行类型
     * 
     * 为什么使用 RexProgram:
     * - Calc 是 Project 和 Filter 的组合
     * - RexProgram 可以高效地表示这种组合
     * - 优化器可以使用 RexProgram 进行等价变换
     */
    private final RexProgram program;

    /**
     * 【构造方法说明】:
     * 创建 JdbcCalc 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入关系节点
     * @param program RexProgram,包含 Project 和 Filter 的逻辑
     * 
     * 构造方法功能:
     * - 调用父类 SingleRel 的构造方法
     * - 验证约定必须是 JdbcConvention
     * - 设置输出行类型为程序的输出类型
     */
    public JdbcCalc(RelOptCluster cluster,
        RelTraitSet traitSet,
        RelNode input,
        RexProgram program) {
      // 调用父类构造方法
      super(cluster, traitSet, input);
      // 断言约定是 JdbcConvention
      assert getConvention() instanceof JdbcConvention;
      // 保存程序
      this.program = program;
      // 设置输出行类型为程序的输出类型
      this.rowType = program.getOutputRowType();
    }

    /**
     * 【构造方法说明】:
     * 已废弃的构造方法,将在 2.0 版本之前移除。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入
     * @param program RexProgram
     * @param flags 标志位(未使用)
     */
    @Deprecated // to be removed before 2.0
    public JdbcCalc(RelOptCluster cluster, RelTraitSet traitSet, RelNode input,
        RexProgram program, int flags) {
      // 调用新构造方法,忽略 flags 参数
      this(cluster, traitSet, input, program);
      Util.discard(flags);
    }

    /**
     * 【方法作用说明】:
     * 解释 JdbcCalc 的术语,用于调试和日志记录。
     * 
     * @param pw 关系写入器
     * @return 关系写入器,用于链式调用
     * 
     * 输出格式:
     * - 包含程序的详细信息
     * - 包含输入、输出、条件等信息
     */
    @Override public RelWriter explainTerms(RelWriter pw) {
      // 使用程序解释 Calc 的术语
      return program.explainCalc(super.explainTerms(pw));
    }

    /**
     * 【方法作用说明】:
     * 估算 JdbcCalc 的输出行数。
     * 
     * @param mq 元数据查询对象
     * @return 估算的输出行数
     * 
     * 估算策略:
     * - 使用 RelMdUtil.estimateFilteredRows 方法
     * - 根据程序中的过滤条件估算行数
     */
    @Override public double estimateRowCount(RelMetadataQuery mq) {
      // 使用工具类估算过滤后的行数
      return RelMdUtil.estimateFilteredRows(getInput(), program, mq);
    }

    /**
     * 【方法作用说明】:
     * 计算 JdbcCalc 的自身成本。
     * 
     * @param planner 优化器
     * @param mq 元数据查询对象
     * @return 估算的成本
     * 
     * 成本计算:
     * - 行数:使用元数据查询获取
     * - CPU 成本:输入行数 * 表达式数量
     * - IO 成本:0
     */
    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
        RelMetadataQuery mq) {
      // 获取输出行数
      double dRows = mq.getRowCount(this);
      // CPU 成本 = 输入行数 * 表达式数量
      double dCpu = mq.getRowCount(getInput())
          * program.getExprCount();
      // IO 成本为 0
      double dIo = 0;
      // 创建并返回成本对象
      return planner.getCostFactory().makeCost(dRows, dCpu, dIo);
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcCalc 的副本。
     * 
     * @param traitSet 新的特征集合
     * @param inputs 新的输入列表
     * @return 新的 JdbcCalc 实例
     */
    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
      // 创建并返回新的 JdbcCalc 实例
      return new JdbcCalc(getCluster(), traitSet, sole(inputs), program);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcCalc,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Project 相关的规则和实现
  // ===========================================

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Project} to
   * an {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcProject}.
   * 
   * 【类作用说明】:
   * 将 Project 关系算子转换为 JdbcProject 实现的规则。
   * 
   * Project 算子用于:
   * - 选择列(类似 SQL 的 SELECT 子句)
   * - 重命名列
   * - 计算表达式(如 col1 + col2)
   * - 添加常量列
   * 
   * 转换条件:
   * 1. Project 不包含变量(用于关联子查询)
   * 2. 如果包含窗口函数,JDBC 方言必须支持窗口函数
   * 3. 不能包含用户定义函数(UDF)
   * 
   * 不转换的情况:
   * - 包含变量
   * - 包含窗口函数但方言不支持
   * - 包含用户定义函数
   */
  public static class JdbcProjectRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcProjectRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcProjectRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Project.class
     * - description: "JdbcProjectRule"
     * 
     * 转换条件(通过 lambda 表达式):
     * - 方言支持窗口函数 OR Project 不包含窗口函数
     * - AND Project 不包含用户定义函数
     */
    public static JdbcProjectRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换,使用 lambda 表达式定义转换条件
          .withConversion(Project.class, project =>
                  // 条件1: 方言支持窗口函数 OR Project 不包含窗口函数
                  (out.dialect.supportsWindowFunctions()
                      || !project.containsOver())
                      // 条件2: Project 不包含用户定义函数
                      && !userDefinedFunctionInProject(project),
              Convention.NONE, out, "JdbcProjectRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcProjectRule::new)
          // 转换为规则实例
          .toRule(JdbcProjectRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcProjectRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 检查 Project 是否包含用户定义函数。
     * 
     * @param project 要检查的 Project 节点
     * @return 如果包含用户定义函数则返回 true,否则返回 false
     * 
     * 检查方法:
     * - 创建 CheckingUserDefinedFunctionVisitor 访问器
     * - 遍历 Project 的所有投影表达式
     * - 对每个表达式调用访问器进行检查
     * - 如果任何一个表达式包含 UDF,则返回 true
     */
    private static boolean userDefinedFunctionInProject(Project project) {
      // 创建 UDF 检查访问器
      CheckingUserDefinedFunctionVisitor visitor = new CheckingUserDefinedFunctionVisitor();
      // 遍历 Project 的所有投影表达式
      for (RexNode node : project.getProjects()) {
        // 让表达式接受访问器
        node.accept(visitor);
        // 如果访问器检测到 UDF,返回 true
        if (visitor.containsUserDefinedFunction()) {
          return true;
        }
      }
      // 没有检测到 UDF,返回 false
      return false;
    }

    /**
     * 【方法作用说明】:
     * 判断规则是否匹配给定的规则调用。
     * 
     * @param call 规则调用对象
     * @return 如果规则匹配则返回 true,否则返回 false
     * 
     * 匹配条件:
     * - Project 不包含变量集合
     * 
     * 为什么检查变量:
     * - 变量用于关联子查询
     * - JDBC Project 不支持变量
     * - 包含变量的 Project 需要特殊处理
     */
    @Override public boolean matches(RelOptRuleCall call) {
      // 获取 Project 节点
      Project project = call.rel(0);
      // 检查变量集合是否为空
      return project.getVariablesSet().isEmpty();
    }

    /**
     * 【方法作用说明】:
     * 将 Project 关系节点转换为 JdbcProject 节点。
     * 
     * @param rel 要转换的 Project 关系节点
     * @return 转换后的 JdbcProject 节点
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Project
     * 2. 将输入节点转换为 JDBC 约定
     * 3. 创建 JdbcProject 实例
     * 
     * 参数说明:
     * - projects: 投影表达式列表
     * - rowType: 输出行类型
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Project
      final Project project = (Project) rel;

      // 创建并返回 JdbcProject 实例
      // 参数:集群、特征集合(替换为 JDBC 约定)、输入(转换为 JDBC 约定)、投影表达式、输出行类型
      return new JdbcProject(
          rel.getCluster(),
          rel.getTraitSet().replace(out),
          convert(
              project.getInput(),
              project.getInput().getTraitSet().replace(out)),
          project.getProjects(),
          project.getRowType());
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Project} in
   * {@link JdbcConvention jdbc calling convention}.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Project 算子。
   * 
   * Project 算子对应 SQL 的 SELECT 子句,用于:
   * - 选择列
   * - 重命名列
   * - 计算表达式
   * - 添加常量
   * 
   * 继承关系:
   * - JdbcProject extends Project
   * - Project extends SingleRel
   * - JdbcProject implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式: SELECT expr1 AS col1, expr2 AS col2, ... FROM ...
   * 
   * 示例:
   * - 输入: SELECT emp_id, salary * 1.1 AS new_salary FROM emp
   * - JdbcProject 包含两个表达式:
   *   1. emp_id (列引用)
   *   2. salary * 1.1 (算术表达式)
   */
  public static class JdbcProject
      extends Project
      implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcProject 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入关系节点
     * @param projects 投影表达式列表
     * @param rowType 输出行类型
     * 
     * 构造方法功能:
     * - 调用父类 Project 的构造方法
     * - 传入空提示列表
     * - 传入空变量集合(ImmutableSet.of())
     * - 验证约定必须是 JdbcConvention
     */
    public JdbcProject(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        RelNode input,
        List<? extends RexNode> projects,
        RelDataType rowType) {
      // 调用父类构造方法,传入空提示列表和空变量集合
      super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of());
      // 断言约定是 JdbcConvention
      assert getConvention() instanceof JdbcConvention;
    }

    /**
     * 【构造方法说明】:
     * 已废弃的构造方法,将在 2.0 版本之前移除。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入
     * @param projects 投影表达式列表
     * @param rowType 输出行类型
     * @param flags 标志位(未使用)
     */
    @Deprecated // to be removed before 2.0
    public JdbcProject(RelOptCluster cluster, RelTraitSet traitSet,
        RelNode input, List<RexNode> projects, RelDataType rowType, int flags) {
      // 调用新构造方法,忽略 flags 参数
      this(cluster, traitSet, input, projects, rowType);
      Util.discard(flags);
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcProject 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param input 新的输入
     * @param projects 新的投影表达式列表
     * @param rowType 新的输出行类型
     * @return 新的 JdbcProject 实例
     */
    @Override public JdbcProject copy(RelTraitSet traitSet, RelNode input,
        List<RexNode> projects, RelDataType rowType) {
      // 创建并返回新的 JdbcProject 实例
      return new JdbcProject(getCluster(), traitSet, input, projects, rowType);
    }

    /**
     * 【方法作用说明】:
     * 计算 JdbcProject 的自身成本。
     * 
     * @param planner 优化器
     * @param mq 元数据查询对象
     * @return 估算的成本
     * 
     * 成本计算策略:
     * - 使用父类的成本计算方法
     * - 乘以 COST_MULTIPLIER(通常小于 1)
     * 
     * 为什么乘以乘数:
     * - JDBC 操作被下推到数据库执行
     * - 相比于在内存中执行,成本更低
     * - 乘数鼓励优化器选择 JDBC 实现
     */
    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
        RelMetadataQuery mq) {
      // 调用父类的方法计算成本
      RelOptCost cost = super.computeSelfCost(planner, mq);
      // 如果成本为 null,直接返回
      if (cost == null) {
        return null;
      }
      // 乘以成本乘数,降低 JDBC 实现的成本
      return cost.multiplyBy(JdbcConvention.COST_MULTIPLIER);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcProject,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: projects = [emp_id, salary * 1.1 AS new_salary]
     * - 输出: SELECT emp_id, salary * 1.1 AS new_salary FROM emp
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Filter 相关的规则和实现
  // ===========================================

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Filter} to
   * an {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcFilter}.
   * 
   * 【类作用说明】:
   * 将 Filter 关系算子转换为 JdbcFilter 实现的规则。
   * 
   * Filter 算子用于:
   * - 根据条件过滤行(类似 SQL 的 WHERE 子句)
   * 
   * 转换条件:
   * 1. Filter 不包含用户定义函数(UDF)
   * 
   * 不转换的情况:
   * - 包含用户定义函数
   */
  public static class JdbcFilterRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcFilterRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcFilterRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Filter.class
     * - description: "JdbcFilterRule"
     * 
     * 转换条件(通过 lambda 表达式):
     * - Filter 不包含用户定义函数
     */
    public static JdbcFilterRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换,使用 lambda 表达式定义转换条件
          .withConversion(Filter.class, r -> !userDefinedFunctionInFilter(r),
              Convention.NONE, out, "JdbcFilterRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcFilterRule::new)
          // 转换为规则实例
          .toRule(JdbcFilterRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcFilterRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 检查 Filter 是否包含用户定义函数。
     * 
     * @param filter 要检查的 Filter 节点
     * @return 如果包含用户定义函数则返回 true,否则返回 false
     * 
     * 检查方法:
     * - 创建 CheckingUserDefinedFunctionVisitor 访问器
     * - 让过滤条件接受访问器
     * - 返回访问器的检查结果
     */
    private static boolean userDefinedFunctionInFilter(Filter filter) {
      // 创建 UDF 检查访问器
      CheckingUserDefinedFunctionVisitor visitor = new CheckingUserDefinedFunctionVisitor();
      // 让过滤条件接受访问器
      filter.getCondition().accept(visitor);
      // 返回是否包含 UDF
      return visitor.containsUserDefinedFunction();
    }

    /**
     * 【方法作用说明】:
     * 将 Filter 关系节点转换为 JdbcFilter 节点。
     * 
     * @param rel 要转换的 Filter 关系节点
     * @return 转换后的 JdbcFilter 节点
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Filter
     * 2. 将输入节点转换为 JDBC 约定
     * 3. 创建 JdbcFilter 实例
     * 
     * 参数说明:
     * - condition: 过滤条件表达式
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Filter
      final Filter filter = (Filter) rel;

      // 创建并返回 JdbcFilter 实例
      // 参数:集群、特征集合(替换为 JDBC 约定)、输入(转换为 JDBC 约定)、过滤条件
      return new JdbcFilter(
          rel.getCluster(),
          rel.getTraitSet().replace(out),
          convert(filter.getInput(),
              filter.getInput().getTraitSet().replace(out)),
          filter.getCondition());
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Filter} in
   * {@link JdbcConvention jdbc calling convention}.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Filter 算子。
   * 
   * Filter 算子对应 SQL 的 WHERE 子句,用于:
   * - 根据条件过滤行
   * 
   * 继承关系:
   * - JdbcFilter extends Filter
   * - Filter extends SingleRel
   * - JdbcFilter implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式: SELECT ... FROM ... WHERE condition
   * 
   * 示例:
   * - 输入: condition = salary > 50000
   * - 输出: SELECT * FROM emp WHERE salary > 50000
   */
  public static class JdbcFilter extends Filter implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcFilter 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入关系节点
     * @param condition 过滤条件表达式
     * 
     * 构造方法功能:
     * - 调用父类 Filter 的构造方法
     * - 验证约定必须是 JdbcConvention
     */
    public JdbcFilter(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        RelNode input,
        RexNode condition) {
      // 调用父类构造方法
      super(cluster, traitSet, input, condition);
      // 断言约定是 JdbcConvention
      assert getConvention() instanceof JdbcConvention;
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcFilter 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param input 新的输入
     * @param condition 新的过滤条件
     * @return 新的 JdbcFilter 实例
     */
    @Override public JdbcFilter copy(RelTraitSet traitSet, RelNode input,
        RexNode condition) {
      // 创建并返回新的 JdbcFilter 实例
      return new JdbcFilter(getCluster(), traitSet, input, condition);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcFilter,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: condition = salary > 50000 AND dept_id = 10
     * - 输出: SELECT * FROM emp WHERE salary > 50000 AND dept_id = 10
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Aggregate 相关的规则和实现
  // ===========================================

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Aggregate}
   * to a {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcAggregate}.
   * 
   * 【类作用说明】:
   * 将 Aggregate 关系算子转换为 JdbcAggregate 实现的规则。
   * 
   * Aggregate 算子用于:
   * - 执行 GROUP BY 分组
   * - 执行聚合函数(SUM、COUNT、AVG、MIN、MAX 等)
   * 
   * 转换条件:
   * 1. 不支持 GROUPING SETS(只支持单个分组集合)
   * 2. 聚合函数必须被 JDBC 方言支持
   * 3. 聚合函数不能有 distinctKeys
   * 4. 如果聚合函数有 FILTER 子句,方言必须支持
   * 
   * 不转换的情况:
   * - 包含 GROUPING SETS
   * - 聚合函数不被方言支持
   * - 聚合函数有 distinctKeys
   * - 聚合函数有 FILTER 但方言不支持
   */
  public static class JdbcAggregateRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcAggregateRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcAggregateRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Aggregate.class
     * - description: "JdbcAggregateRule"
     */
    public static JdbcAggregateRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换
          .withConversion(Aggregate.class, Convention.NONE, out,
              "JdbcAggregateRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcAggregateRule::new)
          // 转换为规则实例
          .toRule(JdbcAggregateRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcAggregateRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 Aggregate 关系节点转换为 JdbcAggregate 节点。
     * 
     * @param rel 要转换的 Aggregate 关系节点
     * @return 转换后的 JdbcAggregate 节点,如果转换失败则返回 null
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Aggregate
     * 2. 检查是否包含 GROUPING SETS
     * 3. 将输入节点转换为 JDBC 约定
     * 4. 创建 JdbcAggregate 实例
     * 
     * 返回 null 的情况:
     * - 包含 GROUPING SETS(groupSets.size() != 1)
     * - 创建 JdbcAggregate 时抛出 InvalidRelException
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Aggregate
      final Aggregate agg = (Aggregate) rel;
      // 检查分组集合数量
      if (agg.getGroupSets().size() != 1) {
        // GROUPING SETS 不支持
        // GROUPING SETS 允许在单个查询中指定多个分组
        // 例如: GROUP BY GROUPING SETS ((dept_id), (job_id), ())
        // 这是一个高级特性,需要特殊处理
        return null;
      }
      // 创建新的特征集合,将约定替换为 JDBC 约定
      final RelTraitSet traitSet =
          agg.getTraitSet().replace(out);
      try {
        // 创建并返回 JdbcAggregate 实例
        // 参数:集群、特征集合、输入(转换为 JDBC 约定)、分组集合、分组集合列表、聚合调用列表
        return new JdbcAggregate(rel.getCluster(), traitSet,
            convert(agg.getInput(), out), agg.getGroupSet(),
            agg.getGroupSets(), agg.getAggCallList());
      } catch (InvalidRelException e) {
        // 创建失败,记录调试日志并返回 null
        LOGGER.debug(e.toString());
        return null;
      }
    }
  }

  /** Returns whether this JDBC data source can implement a given aggregate
   * function.
   * 
   * 【方法作用说明】:
   * 判断 JDBC 数据源是否可以实现给定的聚合函数。
   * 
   * @param aggregateCall 聚合调用,包含聚合函数的信息
   * @param sqlDialect SQL 方言,描述数据库的特性
   * @return 如果可以实现则返回 true,否则返回 false
   * 
   * 判断条件:
   * 1. 方言支持该聚合函数类型
   * 2. 聚合调用没有 distinctKeys
   * 
   * 支持的聚合函数类型:
   * - COUNT: 计数
   * - SUM: 求和
   * - AVG: 平均值
   * - MIN: 最小值
   * - MAX: 最大值
   * - 其他标准 SQL 聚合函数
   * 
   * 不支持的情况:
   * - 聚合函数不被方言支持(如某些数据库不支持 ARRAY_AGG)
   * - 聚合调用有 distinctKeys(表示 DISTINCT 聚合)
   */
  private static boolean canImplement(AggregateCall aggregateCall,
      SqlDialect sqlDialect) {
    // 检查方言是否支持该聚合函数类型
    // 并且聚合调用没有 distinctKeys
    return sqlDialect.supportsAggregateFunction(
        aggregateCall.getAggregation().getKind())
        && aggregateCall.distinctKeys == null;
  }

  /** Aggregate operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Aggregate 算子。
   * 
   * Aggregate 算子对应 SQL 的 GROUP BY 和聚合函数,用于:
   * - 分组数据
   * - 计算聚合值
   * 
   * 继承关系:
   * - JdbcAggregate extends Aggregate
   * - Aggregate extends SingleRel
   * - JdbcAggregate implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式: SELECT group_cols, agg_func(...) FROM ... GROUP BY group_cols
   * 
   * 示例:
   * - 输入: groupSet = {dept_id}, aggCalls = [COUNT(*), SUM(salary)]
   * - 输出: SELECT dept_id, COUNT(*), SUM(salary) FROM emp GROUP BY dept_id
   * 
   * 限制:
   * - 不支持 GROUPING SETS
   * - 聚合函数必须被方言支持
   * - 不支持 DISTINCT 聚合(除非方言支持)
   */
  public static class JdbcAggregate extends Aggregate implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcAggregate 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入关系节点
     * @param groupSet 分组列集合(表示 GROUP BY 的列)
     * @param groupSets 分组集合列表(用于 GROUPING SETS,这里只支持单个)
     * @param aggCalls 聚合调用列表
     * @throws InvalidRelException 如果聚合无效则抛出异常
     * 
     * 构造方法功能:
     * - 调用父类 Aggregate 的构造方法
     * - 验证约定必须是 JdbcConvention
     * - 验证分组集合数量为 1(不支持 GROUPING SETS)
     * - 验证所有聚合函数都可以实现
     * - 验证聚合函数的 FILTER 子句(如果有)被方言支持
     */
    public JdbcAggregate(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        RelNode input,
        ImmutableBitSet groupSet,
        @Nullable List<ImmutableBitSet> groupSets,
        List<AggregateCall> aggCalls)
        throws InvalidRelException {
      // 调用父类构造方法,传入空提示列表
      super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls);
      // 断言约定是 JdbcConvention
      assert getConvention() instanceof JdbcConvention;
      // 断言分组集合数量为 1(不支持 GROUPING SETS)
      assert this.groupSets.size() == 1 : "Grouping sets not supported";
      // 获取方言
      final SqlDialect dialect = ((JdbcConvention) getConvention()).dialect;
      // 遍历所有聚合调用
      for (AggregateCall aggCall : aggCalls) {
        // 检查聚合函数是否可以实现
        if (!canImplement(aggCall, dialect)) {
          // 聚合函数不能实现,抛出异常
          throw new InvalidRelException("cannot implement aggregate function "
              + aggCall);
        }
        // 检查聚合函数是否有 FILTER 子句
        if (aggCall.hasFilter() && !dialect.supportsAggregateFunctionFilter()) {
          // 聚合函数有 FILTER 但方言不支持,抛出异常
          // FILTER 子句示例: COUNT(*) FILTER (WHERE salary > 50000)
          throw new InvalidRelException("dialect does not support aggregate "
              + "functions FILTER clauses");
        }
      }
    }

    /**
     * 【构造方法说明】:
     * 已废弃的构造方法,将在 2.0 版本之前移除。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入
     * @param indicator 指示器标志(已废弃)
     * @param groupSet 分组集合
     * @param groupSets 分组集合列表
     * @param aggCalls 聚合调用列表
     * @throws InvalidRelException 如果聚合无效
     */
    @Deprecated // to be removed before 2.0
    public JdbcAggregate(RelOptCluster cluster, RelTraitSet traitSet,
        RelNode input, boolean indicator, ImmutableBitSet groupSet,
        List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls)
        throws InvalidRelException {
      // 调用新构造方法,忽略 indicator 参数
      this(cluster, traitSet, input, groupSet, groupSets, aggCalls);
      checkIndicator(indicator);
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcAggregate 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param input 新的输入
     * @param groupSet 新的分组集合
     * @param groupSets 新的分组集合列表
     * @param aggCalls 新的聚合调用列表
     * @return 新的 JdbcAggregate 实例
     */
    @Override public JdbcAggregate copy(RelTraitSet traitSet, RelNode input,
        ImmutableBitSet groupSet,
        @Nullable List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) {
      try {
        // 创建并返回新的 JdbcAggregate 实例
        return new JdbcAggregate(getCluster(), traitSet, input,
            groupSet, groupSets, aggCalls);
      } catch (InvalidRelException e) {
        // 语义错误不应该发生,必须是 bug
        // 转换为内部错误
        throw new AssertionError(e);
      }
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcAggregate,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: groupSet = {dept_id}, aggCalls = [COUNT(*), SUM(salary)]
     * - 输出: SELECT dept_id, COUNT(*), SUM(salary) FROM emp GROUP BY dept_id
     * 
     * 带 FILTER 的示例:
     * - 输入: aggCalls = [SUM(salary) FILTER (WHERE salary > 0)]
     * - 输出: SELECT SUM(salary) FILTER (WHERE salary > 0) FROM emp
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Sort 相关的规则和实现
  // ===========================================

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Sort} to an
   * {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcSort}.
   * 
   * 【类作用说明】:
   * 将 Sort 关系算子转换为 JdbcSort 实现的规则。
   * 
   * Sort 算子用于:
   * - 排序结果(ORDER BY)
   * - 限制结果数量(LIMIT/OFFSET)
   * 
   * 转换条件:
   * - 无特殊条件,所有 Sort 都可以转换
   * 
   * SQL 生成:
   * - ORDER BY 子句
   * - LIMIT 子句(对应 fetch)
   * - OFFSET 子句(对应 offset)
   */
  public static class JdbcSortRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcSortRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcSortRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Sort.class
     * - description: "JdbcSortRule"
     */
    public static JdbcSortRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换
          .withConversion(Sort.class, Convention.NONE, out, "JdbcSortRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcSortRule::new)
          // 转换为规则实例
          .toRule(JdbcSortRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcSortRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 Sort 关系节点转换为 JdbcSort 节点。
     * 
     * @param rel 要转换的 Sort 关系节点
     * @return 转换后的 JdbcSort 节点
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Sort
     * 2. 调用 convert(Sort, boolean) 方法执行实际转换
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 调用重载的 convert 方法,true 表示需要转换输入约定
      return convert((Sort) rel, true);
    }

    /**
     * Converts a {@code Sort} into a {@code JdbcSort}.
     * 
     * 【方法作用说明】:
     * 将 Sort 算子转换为 JdbcSort 算子。
     * 
     * @param sort 要转换的 Sort 算子
     * @param convertInputTraits 是否转换输入的约定为 Sort 的 JDBC 约定
     * @return 转换后的 JdbcSort 实例
     * 
     * 转换步骤:
     * 1. 创建新的特征集合,将约定替换为 JDBC 约定
     * 2. 如果需要,将输入节点转换为 JDBC 约定
     * 3. 创建 JdbcSort 实例
     * 
     * 参数说明:
     * - collation: 排序规则,包含排序列和排序方向
     * - offset: 偏移量(OFFSET),用于跳过前 N 行
     * - fetch: 获取行数(LIMIT),用于限制返回行数
     */
    public RelNode convert(Sort sort, boolean convertInputTraits) {
      // 创建新的特征集合,将约定替换为 JDBC 约定
      final RelTraitSet traitSet = sort.getTraitSet().replace(out);

      // 处理输入节点
      final RelNode input;
      if (convertInputTraits) {
        // 如果需要转换输入约定
        // 创建输入的特征集合,将约定替换为 JDBC 约定
        final RelTraitSet inputTraitSet = sort.getInput().getTraitSet().replace(out);
        // 转换输入节点
        input = convert(sort.getInput(), inputTraitSet);
      } else {
        // 不需要转换,直接使用原输入
        input = sort.getInput();
      }

      // 创建并返回 JdbcSort 实例
      // 参数:集群、特征集合、输入、排序规则、偏移量、获取行数
      return new JdbcSort(sort.getCluster(), traitSet,
          input, sort.getCollation(), sort.offset, sort.fetch);
    }
  }

  /** Sort operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Sort 算子。
   * 
   * Sort 算子对应 SQL 的 ORDER BY、LIMIT、OFFSET 子句,用于:
   * - 排序结果
   * - 限制返回的行数
   * - 跳过前 N 行
   * 
   * 继承关系:
   * - JdbcSort extends Sort
   * - Sort extends SingleRel
   * - JdbcSort implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式: SELECT ... FROM ... ORDER BY ... LIMIT ... OFFSET ...
   * 
   * 示例:
   * - 输入: collation = [emp_id ASC], offset = 10, fetch = 20
   * - 输出: SELECT ... FROM emp ORDER BY emp_id ASC LIMIT 20 OFFSET 10
   */
  public static class JdbcSort
      extends Sort
      implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcSort 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param input 输入关系节点
     * @param collation 排序规则,包含排序列和方向
     * @param offset 偏移量(OFFSET),可以为 null
     * @param fetch 获取行数(LIMIT),可以为 null
     * 
     * 构造方法功能:
     * - 调用父类 Sort 的构造方法
     * - 验证约定必须是 JdbcConvention
     * - 验证输入的约定与当前约定一致
     */
    public JdbcSort(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        RelNode input,
        RelCollation collation,
        @Nullable RexNode offset,
        @Nullable RexNode fetch) {
      // 调用父类构造方法
      super(cluster, traitSet, input, collation, offset, fetch);
      // 断言约定是 JdbcConvention
      assert getConvention() instanceof JdbcConvention;
      // 断言输入的约定与当前约定一致
      assert getConvention() == input.getConvention();
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcSort 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param newInput 新的输入
     * @param newCollation 新的排序规则
     * @param offset 新的偏移量
     * @param fetch 新的获取行数
     * @return 新的 JdbcSort 实例
     */
    @Override public JdbcSort copy(RelTraitSet traitSet, RelNode newInput,
        RelCollation newCollation, @Nullable RexNode offset, @Nullable RexNode fetch) {
      // 创建并返回新的 JdbcSort 实例
      return new JdbcSort(getCluster(), traitSet, newInput, newCollation,
          offset, fetch);
    }

    /**
     * 【方法作用说明】:
     * 计算 JdbcSort 的自身成本。
     * 
     * @param planner 优化器
     * @param mq 元数据查询对象
     * @return 估算的成本
     * 
     * 成本计算策略:
     * - 使用父类的成本计算方法
     * - 乘以 0.9,降低成本
     * 
     * 为什么乘以 0.9:
     * - JDBC Sort 在数据库中执行,效率较高
     * - 相比于在内存中排序,成本更低
     * - 乘数鼓励优化器选择 JDBC 实现
     */
    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
        RelMetadataQuery mq) {
      // 调用父类的方法计算成本
      RelOptCost cost = super.computeSelfCost(planner, mq);
      // 如果成本为 null,直接返回
      if (cost == null) {
        return null;
      }
      // 乘以 0.9,降低成本
      return cost.multiplyBy(0.9);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcSort,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: collation = [emp_id ASC, salary DESC], offset = 10, fetch = 20
     * - 输出: SELECT ... FROM emp ORDER BY emp_id ASC, salary DESC LIMIT 20 OFFSET 10
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Union 相关的规则和实现
  // ===========================================

  /**
   * Rule to convert an {@link org.apache.calcite.rel.core.Union} to a
   * {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcUnion}.
   * 
   * 【类作用说明】:
   * 将 Union 关系算子转换为 JdbcUnion 实现的规则。
   * 
   * Union 算子用于:
   * - 合并多个查询的结果集
   * - 对应 SQL 的 UNION 或 UNION ALL
   * 
   * 转换条件:
   * - 无特殊条件,所有 Union 都可以转换
   * 
   * SQL 生成:
   * - UNION 或 UNION ALL 子句
   */
  public static class JdbcUnionRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcUnionRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcUnionRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Union.class
     * - description: "JdbcUnionRule"
     */
    public static JdbcUnionRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换
          .withConversion(Union.class, Convention.NONE, out, "JdbcUnionRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcUnionRule::new)
          // 转换为规则实例
          .toRule(JdbcUnionRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcUnionRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 Union 关系节点转换为 JdbcUnion 节点。
     * 
     * @param rel 要转换的 Union 关系节点
     * @return 转换后的 JdbcUnion 节点
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Union
     * 2. 创建新的特征集合,将约定替换为 JDBC 约定
     * 3. 将所有输入节点转换为 JDBC 约定
     * 4. 创建 JdbcUnion 实例
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Union
      final Union union = (Union) rel;
      // 创建新的特征集合,将约定替换为 JDBC 约定
      final RelTraitSet traitSet =
          union.getTraitSet().replace(out);
      // 创建并返回 JdbcUnion 实例
      // 参数:集群、特征集合、输入列表(转换为 JDBC 约定)、all 标志
      return new JdbcUnion(rel.getCluster(), traitSet,
          convertList(union.getInputs(), out), union.all);
    }
  }

  /** Union operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Union 算子。
   * 
   * Union 算子对应 SQL 的 UNION 或 UNION ALL,用于:
   * - 合并多个查询的结果集
   * 
   * 继承关系:
   * - JdbcUnion extends Union
   * - Union extends SetOp
   * - SetOp extends AbstractRelNode
   * - JdbcUnion implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式: SELECT ... UNION [ALL] SELECT ... UNION [ALL] SELECT ...
   * 
   * 示例:
   * - 输入: inputs = [emp1, emp2], all = true
   * - 输出: SELECT * FROM emp1 UNION ALL SELECT * FROM emp2
   * 
   * UNION vs UNION ALL:
   * - UNION: 去除重复行
   * - UNION ALL: 保留所有行(包括重复)
   */
  public static class JdbcUnion extends Union implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcUnion 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param inputs 输入关系节点列表
     * @param all 是否保留所有行(UNION ALL)
     * 
     * 构造方法功能:
     * - 调用父类 Union 的构造方法
     */
    public JdbcUnion(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        List<RelNode> inputs,
        boolean all) {
      // 调用父类构造方法
      super(cluster, traitSet, inputs, all);
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcUnion 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param inputs 新的输入列表
     * @param all 新的 all 标志
     * @return 新的 JdbcUnion 实例
     */
    @Override public JdbcUnion copy(
        RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
      // 创建并返回新的 JdbcUnion 实例
      return new JdbcUnion(getCluster(), traitSet, inputs, all);
    }

    /**
     * 【方法作用说明】:
     * 计算 JdbcUnion 的自身成本。
     * 
     * @param planner 优化器
     * @param mq 元数据查询对象
     * @return 估算的成本
     * 
     * 成本计算策略:
     * - 使用父类的成本计算方法
     * - 乘以 COST_MULTIPLIER,降低成本
     * 
     * 为什么乘以乘数:
     * - JDBC Union 在数据库中执行,效率较高
     * - 相比于在内存中合并,成本更低
     * - 乘数鼓励优化器选择 JDBC 实现
     */
    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
        RelMetadataQuery mq) {
      // 调用父类的方法计算成本
      RelOptCost cost = super.computeSelfCost(planner, mq);
      // 如果成本为 null,直接返回
      if (cost == null) {
        return null;
      }
      // 乘以成本乘数,降低成本
      return cost.multiplyBy(JdbcConvention.COST_MULTIPLIER);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcUnion,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: inputs = [emp1, emp2, emp3], all = false
     * - 输出: SELECT * FROM emp1 UNION SELECT * FROM emp2 UNION SELECT * FROM emp3
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Intersect 相关的规则和实现
  // ===========================================

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Intersect}
   * to a {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcIntersect}.
   * 
   * 【类作用说明】:
   * 将 Intersect 关系算子转换为 JdbcIntersect 实现的规则。
   * 
   * Intersect 算子用于:
   * - 返回多个查询结果的交集
   * - 对应 SQL 的 INTERSECT
   * 
   * 转换条件:
   * - 不支持 INTERSECT ALL,只支持 INTERSECT
   * 
   * 不转换的情况:
   * - all = true (INTERSECT ALL)
   * 
   * SQL 生成:
   * - INTERSECT 子句
   */
  public static class JdbcIntersectRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcIntersectRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcIntersectRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Intersect.class
     * - description: "JdbcIntersectRule"
     */
    public static JdbcIntersectRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换
          .withConversion(Intersect.class, Convention.NONE, out,
              "JdbcIntersectRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcIntersectRule::new)
          // 转换为规则实例
          .toRule(JdbcIntersectRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcIntersectRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 Intersect 关系节点转换为 JdbcIntersect 节点。
     * 
     * @param rel 要转换的 Intersect 关系节点
     * @return 转换后的 JdbcIntersect 节点,如果转换失败则返回 null
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Intersect
     * 2. 检查是否为 INTERSECT ALL
     * 3. 创建新的特征集合,将约定替换为 JDBC 约定
     * 4. 将所有输入节点转换为 JDBC 约定
     * 5. 创建 JdbcIntersect 实例
     * 
     * 返回 null 的情况:
     * - all = true (INTERSECT ALL 不支持)
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Intersect
      final Intersect intersect = (Intersect) rel;
      // 检查是否为 INTERSECT ALL
      if (intersect.all) {
        // INTERSECT ALL 不支持,返回 null
        // INTERSECT ALL 会返回重复的行,实现较复杂
        return null;
      }
      // 创建新的特征集合,将约定替换为 JDBC 约定
      final RelTraitSet traitSet =
          intersect.getTraitSet().replace(out);
      // 创建并返回 JdbcIntersect 实例
      // 参数:集群、特征集合、输入列表(转换为 JDBC 约定)、all 标志(固定为 false)
      return new JdbcIntersect(rel.getCluster(), traitSet,
          convertList(intersect.getInputs(), out), false);
    }
  }

  /** Intersect operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Intersect 算子。
   * 
   * Intersect 算子对应 SQL 的 INTERSECT,用于:
   * - 返回多个查询结果的交集
   * 
   * 继承关系:
   * - JdbcIntersect extends Intersect
   * - Intersect extends SetOp
   * - SetOp extends AbstractRelNode
   * - JdbcIntersect implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式: SELECT ... INTERSECT SELECT ... INTERSECT SELECT ...
   * 
   * 示例:
   * - 输入: inputs = [emp1, emp2]
   * - 输出: SELECT * FROM emp1 INTERSECT SELECT * FROM emp2
   * 
   * 限制:
   * - 不支持 INTERSECT ALL
   */
  public static class JdbcIntersect
      extends Intersect
      implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcIntersect 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param inputs 输入关系节点列表
     * @param all 是否保留所有行(必须为 false)
     * 
     * 构造方法功能:
     * - 调用父类 Intersect 的构造方法
     * - 断言 all 为 false
     */
    public JdbcIntersect(
        RelOptCluster cluster,
        RelTraitSet traitSet,
        List<RelNode> inputs,
        boolean all) {
      // 调用父类构造方法
      super(cluster, traitSet, inputs, all);
      // 断言 all 为 false
      assert !all;
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcIntersect 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param inputs 新的输入列表
     * @param all 新的 all 标志(必须为 false)
     * @return 新的 JdbcIntersect 实例
     */
    @Override public JdbcIntersect copy(
        RelTraitSet traitSet, List<RelNode> inputs, boolean all) {
      // 创建并返回新的 JdbcIntersect 实例
      return new JdbcIntersect(getCluster(), traitSet, inputs, all);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcIntersect,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: inputs = [emp1, emp2, emp3]
     * - 输出: SELECT * FROM emp1 INTERSECT SELECT * FROM emp2 INTERSECT SELECT * FROM emp3
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Minus 相关的规则和实现
  // ===========================================

  /**
   * Rule to convert a {@link org.apache.calcite.rel.core.Minus} to a
   * {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcMinus}.
   * 
   * 【类作用说明】:
   * 将 Minus 关系算子转换为 JdbcMinus 实现的规则。
   * 
   * Minus 算子用于:
   * - 返回第一个查询结果中不在其他查询结果中的行
   * - 对应 SQL 的 EXCEPT
   * 
   * 转换条件:
   * - 不支持 EXCEPT ALL,只支持 EXCEPT
   * 
   * 不转换的情况:
   * - all = true (EXCEPT ALL)
   * 
   * SQL 生成:
   * - EXCEPT 子句
   */
  public static class JdbcMinusRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcMinusRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcMinusRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Minus.class
     * - description: "JdbcMinusRule"
     */
    public static JdbcMinusRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换
          .withConversion(Minus.class, Convention.NONE, out, "JdbcMinusRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcMinusRule::new)
          // 转换为规则实例
          .toRule(JdbcMinusRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcMinusRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 Minus 关系节点转换为 JdbcMinus 节点。
     * 
     * @param rel 要转换的 Minus 关系节点
     * @return 转换后的 JdbcMinus 节点,如果转换失败则返回 null
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Minus
     * 2. 检查是否为 EXCEPT ALL
     * 3. 创建新的特征集合,将约定替换为 JDBC 约定
     * 4. 将所有输入节点转换为 JDBC 约定
     * 5. 创建 JdbcMinus 实例
     * 
     * 返回 null 的情况:
     * - all = true (EXCEPT ALL 不支持)
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Minus
      final Minus minus = (Minus) rel;
      // 检查是否为 EXCEPT ALL
      if (minus.all) {
        // EXCEPT ALL 不支持,返回 null
        // EXCEPT ALL 会返回重复的行,实现较复杂
        return null;
      }
      // 创建新的特征集合,将约定替换为 JDBC 约定
      final RelTraitSet traitSet =
          rel.getTraitSet().replace(out);
      // 创建并返回 JdbcMinus 实例
      // 参数:集群、特征集合、输入列表(转换为 JDBC 约定)、all 标志(固定为 false)
      return new JdbcMinus(rel.getCluster(), traitSet,
          convertList(minus.getInputs(), out), false);
    }
  }

  /** Minus operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Minus 算子。
   * 
   * Minus 算子对应 SQL 的 EXCEPT,用于:
   * - 返回第一个查询结果中不在其他查询结果中的行
   * 
   * 继承关系:
   * - JdbcMinus extends Minus
   * - Minus extends SetOp
   * - SetOp extends AbstractRelNode
   * - JdbcMinus implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式: SELECT ... EXCEPT SELECT ... EXCEPT SELECT ...
   * 
   * 示例:
   * - 输入: inputs = [emp1, emp2]
   * - 输出: SELECT * FROM emp1 EXCEPT SELECT * FROM emp2
   * 
   * 限制:
   * - 不支持 EXCEPT ALL
   */
  public static class JdbcMinus extends Minus implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcMinus 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param inputs 输入关系节点列表
     * @param all 是否保留所有行(必须为 false)
     * 
     * 构造方法功能:
     * - 调用父类 Minus 的构造方法
     * - 断言 all 为 false
     */
    public JdbcMinus(RelOptCluster cluster, RelTraitSet traitSet,
        List<RelNode> inputs, boolean all) {
      // 调用父类构造方法
      super(cluster, traitSet, inputs, all);
      // 断言 all 为 false
      assert !all;
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcMinus 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param inputs 新的输入列表
     * @param all 新的 all 标志(必须为 false)
     * @return 新的 JdbcMinus 实例
     */
    @Override public JdbcMinus copy(RelTraitSet traitSet, List<RelNode> inputs,
        boolean all) {
      // 创建并返回新的 JdbcMinus 实例
      return new JdbcMinus(getCluster(), traitSet, inputs, all);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcMinus,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: inputs = [emp1, emp2, emp3]
     * - 输出: SELECT * FROM emp1 EXCEPT SELECT * FROM emp2 EXCEPT SELECT * FROM emp3
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // TableModify 相关的规则和实现
  // ===========================================

  /** Rule that converts a table-modification to JDBC.
   * 
   * 【类作用说明】:
   * 将 TableModify 关系算子转换为 JdbcTableModify 实现的规则。
   * 
   * TableModify 算子用于:
   * - 修改表数据(INSERT、UPDATE、DELETE)
   * 
   * 转换条件:
   * 1. 表必须是可修改的(实现 ModifiableTable 接口)
   * 
   * 不转换的情况:
   * - 表不是可修改的
   */
  public static class JdbcTableModificationRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcTableModificationRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcTableModificationRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: TableModify.class
     * - description: "JdbcTableModificationRule"
     */
    public static JdbcTableModificationRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换
          .withConversion(TableModify.class, Convention.NONE, out,
              "JdbcTableModificationRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcTableModificationRule::new)
          // 转换为规则实例
          .toRule(JdbcTableModificationRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcTableModificationRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 TableModify 关系节点转换为 JdbcTableModify 节点。
     * 
     * @param rel 要转换的 TableModify 关系节点
     * @return 转换后的 JdbcTableModify 节点,如果转换失败则返回 null
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 TableModify
     * 2. 检查表是否可修改
     * 3. 创建新的特征集合,将约定替换为 JDBC 约定
     * 4. 将输入节点转换为 JDBC 约定
     * 5. 创建 JdbcTableModify 实例
     * 
     * 返回 null 的情况:
     * - 表不是可修改的
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 TableModify
      final TableModify modify =
          (TableModify) rel;
      // 检查表是否可修改
      // unwrap 尝试将表包装为 ModifiableTable
      final ModifiableTable modifiableTable =
          modify.getTable().unwrap(ModifiableTable.class);
      if (modifiableTable == null) {
        // 表不可修改,返回 null
        return null;
      }
      // 创建新的特征集合,将约定替换为 JDBC 约定
      final RelTraitSet traitSet =
          modify.getTraitSet().replace(out);
      // 创建并返回 JdbcTableModify 实例
      // 参数:集群、特征集合、表、目录读取器、输入(转换为 JDBC 约定)、操作类型、更新列列表、源表达式列表、是否扁平化
      return new JdbcTableModify(
          modify.getCluster(), traitSet,
          modify.getTable(),
          modify.getCatalogReader(),
          convert(modify.getInput(), traitSet),
          modify.getOperation(),
          modify.getUpdateColumnList(),
          modify.getSourceExpressionList(),
          modify.isFlattened());
    }
  }

  /** Table-modification operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 TableModify 算子。
   * 
   * TableModify 算子对应 SQL 的 INSERT、UPDATE、DELETE 语句,用于:
   * - 修改表数据
   * 
   * 继承关系:
   * - JdbcTableModify extends TableModify
   * - TableModify extends SingleRel
   * - JdbcTableModify implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式:
     - INSERT: INSERT INTO table (...) VALUES (...)
     - UPDATE: UPDATE table SET ... WHERE ...
     - DELETE: DELETE FROM table WHERE ...
   * 
   * 操作类型:
   * - INSERT: 插入数据
   * - UPDATE: 更新数据
   * - DELETE: 删除数据
   * - MERGE: 合并数据
   */
  public static class JdbcTableModify extends TableModify implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcTableModify 实例。
     * 
     * @param cluster 关系集群
     * @param traitSet 特征集合
     * @param table 要修改的表
     * @param catalogReader 目录读取器
     * @param input 输入关系节点
     * @param operation 操作类型(INSERT、UPDATE、DELETE、MERGE)
     * @param updateColumnList 更新的列列表(UPDATE 操作使用)
     * @param sourceExpressionList 源表达式列表
     * @param flattened 是否扁平化
     * 
     * 构造方法功能:
     * - 调用父类 TableModify 的构造方法
     * - 验证约定必须是 JdbcConvention
     * - 验证输入的约定必须是 JdbcConvention
     * - 验证表必须是可修改的
     * - 验证表必须有 Queryable 表达式
     */
    public JdbcTableModify(RelOptCluster cluster,
        RelTraitSet traitSet,
        RelOptTable table,
        Prepare.CatalogReader catalogReader,
        RelNode input,
        Operation operation,
        @Nullable List<String> updateColumnList,
        @Nullable List<RexNode> sourceExpressionList,
        boolean flattened) {
      // 调用父类构造方法
      super(cluster, traitSet, table, catalogReader, input, operation,
          updateColumnList, sourceExpressionList, flattened);
      // 断言输入的约定是 JdbcConvention
      assert input.getConvention() instanceof JdbcConvention;
      // 断言当前约定是 JdbcConvention
      assert getConvention() instanceof JdbcConvention;
      // 检查表是否可修改
      final ModifiableTable modifiableTable =
          table.unwrap(ModifiableTable.class);
      if (modifiableTable == null) {
        // 表不可修改,抛出断言错误
        // TODO: 应该在验证器中报告用户错误
        throw new AssertionError();
      }
      // 检查表是否有 Queryable 表达式
      Expression expression = table.getExpression(Queryable.class);
      if (expression == null) {
        // 表没有 Queryable 表达式,抛出断言错误
        // TODO: 应该在验证器中报告用户错误
        throw new AssertionError();
      }
    }

    /**
     * 【方法作用说明】:
     * 计算 JdbcTableModify 的自身成本。
     * 
     * @param planner 优化器
     * @param mq 元数据查询对象
     * @return 估算的成本
     * 
     * 成本计算策略:
     * - 使用父类的成本计算方法
     * - 乘以 0.1,大幅降低成本
     * 
     * 为什么乘以 0.1:
     * - 表修改操作在数据库中执行,效率高
     * - 相比于在内存中修改数据,成本更低
     * - 乘数鼓励优化器选择 JDBC 实现
     */
    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
        RelMetadataQuery mq) {
      // 调用父类的方法计算成本
      RelOptCost cost = super.computeSelfCost(planner, mq);
      // 如果成本为 null,直接返回
      if (cost == null) {
        return null;
      }
      // 乘以 0.1,大幅降低成本
      return cost.multiplyBy(.1);
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcTableModify 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param inputs 新的输入列表
     * @return 新的 JdbcTableModify 实例
     */
    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
      // 创建并返回新的 JdbcTableModify 实例
      return new JdbcTableModify(
          getCluster(), traitSet, getTable(), getCatalogReader(),
          sole(inputs), getOperation(), getUpdateColumnList(),
          getSourceExpressionList(), isFlattened());
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcTableModify,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - INSERT: INSERT INTO emp (emp_id, name) VALUES (1, 'John')
     * - UPDATE: UPDATE emp SET salary = 60000 WHERE emp_id = 1
     * - DELETE: DELETE FROM emp WHERE emp_id = 1
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // Values 相关的规则和实现
  // ===========================================

  /** Rule that converts a values operator to JDBC.
   * 
   * 【类作用说明】:
   * 将 Values 关系算子转换为 JdbcValues 实现的规则。
   * 
   * Values 算子用于:
   * - 生成常量行
   * - 对应 SQL 的 VALUES 子句或 SELECT ... UNION ALL ...
   * 
   * 转换条件:
   * - 无特殊条件,所有 Values 都可以转换
   * 
   * SQL 生成:
   * - VALUES 子句或 UNION ALL 形式
   */
  public static class JdbcValuesRule extends JdbcConverterRule {
    /**
     * 【方法作用说明】:
     * 创建 JdbcValuesRule 实例的静态工厂方法。
     * 
     * @param out 目标 JDBC 约定
     * @return 配置好的 JdbcValuesRule 实例
     * 
     * 配置内容:
     * - from: Convention.NONE
     * - to: out(JDBC 约定)
     * - operand: Values.class
     * - description: "JdbcValuesRule"
     */
    public static JdbcValuesRule create(JdbcConvention out) {
      return Config.INSTANCE
          // 配置转换
          .withConversion(Values.class, Convention.NONE, out, "JdbcValuesRule")
          // 设置规则工厂方法
          .withRuleFactory(JdbcValuesRule::new)
          // 转换为规则实例
          .toRule(JdbcValuesRule.class);
    }

    /**
     * 【构造方法说明】:
     * 由 Config 调用的构造方法。
     * 
     * @param config 规则配置对象
     */
    protected JdbcValuesRule(Config config) {
      super(config);
    }

    /**
     * 【方法作用说明】:
     * 将 Values 关系节点转换为 JdbcValues 节点。
     * 
     * @param rel 要转换的 Values 关系节点
     * @return 转换后的 JdbcValues 节点
     * 
     * 转换步骤:
     * 1. 将输入强制转换为 Values
     * 2. 创建新的特征集合,将约定替换为 JDBC 约定
     * 3. 创建 JdbcValues 实例
     */
    @Override public @Nullable RelNode convert(RelNode rel) {
      // 将输入强制转换为 Values
      Values values = (Values) rel;
      // 创建并返回 JdbcValues 实例
      // 参数:集群、行类型、元组列表、特征集合(替换为 JDBC 约定)
      return new JdbcValues(values.getCluster(), values.getRowType(),
          values.getTuples(), values.getTraitSet().replace(out));
    }
  }

  /** Values operator implemented in JDBC convention.
   * 
   * 【类作用说明】:
   * 在 JDBC 约定下实现的 Values 算子。
   * 
   * Values 算子对应 SQL 的 VALUES 子句或常量行,用于:
   * - 生成常量行
   * 
   * 继承关系:
   * - JdbcValues extends Values
   * - Values extends AbstractRelNode
   * - JdbcValues implements JdbcRel
   * 
   * SQL 生成:
   * - 通过 JdbcImplementor.implement(this) 生成 SQL
   * - 生成的 SQL 格式:
     - VALUES (1, 'John'), (2, 'Jane'), (3, 'Bob')
     - 或 SELECT 1, 'John' UNION ALL SELECT 2, 'Jane' UNION ALL SELECT 3, 'Bob'
   * 
   * 示例:
   * - 输入: tuples = [(1, 'John'), (2, 'Jane'), (3, 'Bob')]
   * - 输出: VALUES (1, 'John'), (2, 'Jane'), (3, 'Bob')
   */
  public static class JdbcValues extends Values implements JdbcRel {
    /**
     * 【构造方法说明】:
     * 创建 JdbcValues 实例。
     * 
     * @param cluster 关系集群
     * @param rowType 行类型
     * @param tuples 元组列表,每个元组是一行常量值
     * @param traitSet 特征集合
     * 
     * 构造方法功能:
     * - 调用父类 Values 的构造方法
     */
    JdbcValues(RelOptCluster cluster, RelDataType rowType,
        ImmutableList<ImmutableList<RexLiteral>> tuples, RelTraitSet traitSet) {
      // 调用父类构造方法
      super(cluster, rowType, tuples, traitSet);
    }

    /**
     * 【方法作用说明】:
     * 创建 JdbcValues 的副本,可以修改部分属性。
     * 
     * @param traitSet 新的特征集合
     * @param inputs 新的输入列表(Values 没有输入,应该为空)
     * @return 新的 JdbcValues 实例
     */
    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
      // 断言输入列表为空
      assert inputs.isEmpty();
      // 创建并返回新的 JdbcValues 实例
      return new JdbcValues(getCluster(), getRowType(), tuples, traitSet);
    }

    /**
     * 【方法作用说明】:
     * 实现 JdbcValues,生成 SQL。
     * 
     * @param implementor JDBC 实现器
     * @return 实现结果,包含生成的 SQL
     * 
     * SQL 生成示例:
     * - 输入: tuples = [(1, 'John'), (2, 'Jane'), (3, 'Bob')]
     * - 输出: VALUES (1, 'John'), (2, 'Jane'), (3, 'Bob')
     * 
     * 或 UNION ALL 形式:
     * - 输出: SELECT 1, 'John' UNION ALL SELECT 2, 'Jane' UNION ALL SELECT 3, 'Bob'
     */
    @Override public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      // 委托给实现器进行实现
      return implementor.implement(this);
    }
  }

  // ===========================================
  // 工具类
  // ===========================================

  /** Visitor that checks whether part of a projection is a user-defined
   * function (UDF).
   * 
   * 【类作用说明】:
   * 检查投影表达式中是否包含用户定义函数(UDF)的访问器。
   * 
   * 用户定义函数:
   * - 用户自定义的 SQL 函数
   * - 不能直接转换为 SQL,因为数据库不知道如何执行
   * - 需要在 Calcite 中执行,然后下推其他部分
   * 
   * 使用场景:
   * - JdbcProjectRule 使用此访问器检查 Project 是否可以转换为 JDBC
   * - JdbcFilterRule 使用此访问器检查 Filter 是否可以转换为 JDBC
   * 
   * 工作原理:
   * - 继承自 RexVisitorImpl,遍历表达式树
   * - 当遇到函数调用(RexCall)时,检查是否是用户定义函数
   * - 如果找到 UDF,设置标志并停止遍历
   */
  private static class CheckingUserDefinedFunctionVisitor
      extends RexVisitorImpl<Void> {
    /**
     * 【成员变量说明】:
     * 标志,表示是否发现了用户定义函数。
     * 
     * 初始值为 false,当访问器遇到 UDF 时设置为 true。
     */
    private boolean containsUsedDefinedFunction = false;

    /**
     * 【构造方法说明】:
     * 创建 CheckingUserDefinedFunctionVisitor 实例。
     * 
     * 构造方法功能:
     * - 调用父类 RexVisitorImpl 的构造方法
     * - 传入 true,表示要深度优先遍历表达式树
     */
    CheckingUserDefinedFunctionVisitor() {
      // 调用父类构造方法,true 表示深度优先遍历
      super(true);
    }

    /**
     * 【方法作用说明】:
     * 返回是否发现了用户定义函数。
     * 
     * @return 如果发现了 UDF 则返回 true,否则返回 false
     */
    public boolean containsUserDefinedFunction() {
      // 返回标志值
      return containsUsedDefinedFunction;
    }

    /**
     * 【方法作用说明】:
     * 访问函数调用节点。
     * 
     * @param call 函数调用节点
     * @return null
     * 
     * 访问逻辑:
     * - 获取函数操作符
     * - 检查是否是用户定义函数
     * - 如果是,设置标志
     * - 继续遍历子表达式
     * 
     * 判断 UDF 的方法:
     * - 检查操作符是否是 SqlFunction
     * - 检查函数类型是否是用户定义的
     */
    @Override public Void visitCall(RexCall call) {
      // 获取函数操作符
      SqlOperator operator = call.getOperator();
      // 检查是否是 SqlFunction 并且是用户定义的
      if (operator instanceof SqlFunction
          && ((SqlFunction) operator).getFunctionType().isUserDefined()) {
        // 发现 UDF,设置标志
        containsUsedDefinedFunction |= true;
      }
      // 调用父类方法,继续遍历子表达式
      return super.visitCall(call);
    }

  }

}