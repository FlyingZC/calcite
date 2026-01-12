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
package org.apache.calcite.adapter.druid;

import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPredicateList;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelRule;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.rules.AggregateExtractProjectRule;
import org.apache.calcite.rel.rules.AggregateFilterTransposeRule;
import org.apache.calcite.rel.rules.FilterAggregateTransposeRule;
import org.apache.calcite.rel.rules.FilterProjectTransposeRule;
import org.apache.calcite.rel.rules.ProjectFilterTransposeRule;
import org.apache.calcite.rel.rules.SortProjectTransposeRule;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexExecutor;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexShuttle;
import org.apache.calcite.rex.RexSimplify;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.trace.CalciteTrace;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.immutables.value.Value;
import org.joda.time.Interval;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Druid查询优化规则集合类，用于将Calcite关系代数操作转换为Druid原生查询
 * 
 * 该类定义了一系列优化规则，用于将Calcite的各种关系操作（Filter、Project、Aggregate、Sort等）
 * 转换为Druid查询能够理解并高效执行的形式。Druid是一个分布式实时分析数据库，
 * 它有自己的查询模型和优化策略，这些规则的作用就是将通用的SQL查询优化为
 * Druid特定的查询形式，从而利用Druid的特性和性能优势。
 * 
 * 主要功能包括：
 * 1. 过滤条件下推（DruidFilterRule）：将WHERE条件转换为Druid的filter表达式
 * 2. 投影下推（DruidProjectRule）：将SELECT表达式转换为Druid的select和post-aggregation
 * 3. 聚合下推（DruidAggregateRule）：将GROUP BY和聚合函数转换为Druid的groupBy和aggregations
 * 4. 排序下推（DruidSortRule）：将ORDER BY和LIMIT转换为Druid的limit
 * 5. HAVING条件下推（DruidHavingFilterRule）：将HAVING条件转换为Druid的having filter
 * 6. 各种操作符的转换规则，用于优化查询执行计划
 * 
 * 设计原则：
 * - 尽可能将计算下推到Druid层执行，减少数据传输
 * - 利用Druid的原生能力（如时间范围过滤、维度过滤、聚合等）
 * - 保持与Calcite优化器的兼容性
 * - 支持复杂的查询场景（如过滤聚合、投影聚合等）
 */
public class DruidRules {
  // 私有构造函数，防止实例化，该类只作为规则集合使用
  private DruidRules() {}

  // 日志记录器，用于记录规则执行过程中的调试信息和警告
  protected static final Logger LOGGER = CalciteTrace.getPlannerTracer();

  // Druid过滤规则实例，用于将Filter操作转换为Druid的filter表达式
  public static final DruidFilterRule FILTER =
      DruidFilterRule.DruidFilterRuleConfig.DEFAULT.toRule();
  // Druid投影规则实例，用于将Project操作转换为Druid的select表达式
  public static final DruidProjectRule PROJECT =
      DruidProjectRule.DruidProjectRuleConfig.DEFAULT.toRule();
  // Druid聚合规则实例，用于将Aggregate操作转换为Druid的groupBy和aggregations
  public static final DruidAggregateRule AGGREGATE =
      DruidAggregateRule.DruidAggregateRuleConfig.DEFAULT.toRule();
  // Druid聚合+投影组合规则实例，用于同时处理Aggregate和Project的组合操作
  public static final DruidAggregateProjectRule AGGREGATE_PROJECT =
      DruidAggregateProjectRule.DruidAggregateProjectRuleConfig.DEFAULT.toRule();
  // Druid排序规则实例，用于将Sort操作转换为Druid的limit和排序
  public static final DruidSortRule SORT =
      DruidSortRule.DruidSortRuleConfig.DEFAULT.toRule();

  /** 规则：将Sort操作下推穿过Project操作，用于转换为复杂的Druid查询
   * 这个规则允许排序操作在投影之前执行，优化查询计划
   * 当Sort在Project之上时，可以将Sort下推到Project下方 */
  public static final SortProjectTransposeRule SORT_PROJECT_TRANSPOSE =
      (SortProjectTransposeRule) SortProjectTransposeRule.Config.DEFAULT
          .withOperandFor(Sort.class, Project.class, DruidQuery.class)
          .withDescription("DruidSortProjectTransposeRule")
          .toRule();

  /** 规则：将Project操作上推穿过Filter操作
   * 当Filter在DruidQuery之上时，将Filter之上的Project上推到Filter下方
   * 这样可以让Filter更早执行，减少处理的数据量 */
  public static final ProjectFilterTransposeRule PROJECT_FILTER_TRANSPOSE =
      (ProjectFilterTransposeRule) ProjectFilterTransposeRule.Config.DEFAULT
          .withOperandFor(Project.class, Filter.class, DruidQuery.class)
          .withDescription("DruidProjectFilterTransposeRule")
          .toRule();

  /** 规则：将Filter操作下推穿过Project操作
   * 当Project在DruidQuery之上时，将Project之上的Filter下推到Project下方
   * withCopyFilter和withCopyProject设为true表示保留原始的Filter和Project */
  public static final FilterProjectTransposeRule FILTER_PROJECT_TRANSPOSE =
      (FilterProjectTransposeRule) FilterProjectTransposeRule.Config.DEFAULT
          .withOperandFor(Filter.class, Project.class, DruidQuery.class)
          .withCopyFilter(true)
          .withCopyProject(true)
          .withDescription("DruidFilterProjectTransposeRule")
          .toRule();

  /** 规则：将Aggregate操作下推穿过Filter操作
   * 当Filter在DruidQuery之上时，将Filter之上的Aggregate下推到Filter下方
   * 这样可以让聚合在过滤之后执行，提高性能 */
  public static final AggregateFilterTransposeRule AGGREGATE_FILTER_TRANSPOSE =
      (AggregateFilterTransposeRule) AggregateFilterTransposeRule.Config.DEFAULT
          .withOperandFor(Aggregate.class, Filter.class, DruidQuery.class)
          .withDescription("DruidAggregateFilterTransposeRule")
          .toRule();

  /** 规则：将Filter操作下推穿过Aggregate操作
   * 当Aggregate在DruidQuery之上时，将Aggregate之上的Filter下推到Aggregate下方
   * 注意：这种情况下Filter会被转换为HAVING子句 */
  public static final FilterAggregateTransposeRule FILTER_AGGREGATE_TRANSPOSE =
      (FilterAggregateTransposeRule) FilterAggregateTransposeRule.Config.DEFAULT
          .withOperandFor(Filter.class, Aggregate.class, DruidQuery.class)
          .withDescription("DruidFilterAggregateTransposeRule")
          .toRule();

  // Druid后聚合投影规则实例，用于将聚合后的Project操作转换为Druid的post-aggregation
  public static final DruidPostAggregationProjectRule POST_AGGREGATION_PROJECT =
      DruidPostAggregationProjectRule.DruidPostAggregationProjectRuleConfig.DEFAULT.toRule();

  /** 规则：从Aggregate中提取Project操作
   * 当Aggregate在DruidQuery之上时，根据聚合使用的字段从Aggregate中提取Project
   * 这样可以优化查询计划，减少不必要的投影操作 */
  public static final AggregateExtractProjectRule PROJECT_EXTRACT_RULE =
      (AggregateExtractProjectRule) AggregateExtractProjectRule.Config.DEFAULT
          .withOperandFor(Aggregate.class, DruidQuery.class)
          .withDescription("DruidAggregateExtractProjectRule")
          .toRule();

  // Druid HAVING过滤规则实例，用于将HAVING条件转换为Druid的having filter
  public static final DruidHavingFilterRule DRUID_HAVING_FILTER_RULE =
      DruidHavingFilterRule.DruidHavingFilterRuleConfig.DEFAULT
      .toRule();

  // Druid规则集合，包含所有可用的Druid优化规则
  // 这些规则会被注册到优化器中，用于将查询计划转换为Druid查询
  public static final List<RelOptRule> RULES =
      ImmutableList.of(FILTER,
          PROJECT_FILTER_TRANSPOSE,
          AGGREGATE_FILTER_TRANSPOSE,
          AGGREGATE_PROJECT,
          PROJECT_EXTRACT_RULE,
          PROJECT,
          POST_AGGREGATION_PROJECT,
          AGGREGATE,
          FILTER_AGGREGATE_TRANSPOSE,
          FILTER_PROJECT_TRANSPOSE,
          SORT,
          SORT_PROJECT_TRANSPOSE,
          DRUID_HAVING_FILTER_RULE);

  /**
   * Druid过滤规则：将Filter操作下推到DruidQuery中
   * 
   * 该规则负责将Calcite的Filter节点转换为Druid的filter表达式。
   * 主要功能包括：
   * 1. 将WHERE条件转换为Druid的JSON filter格式
   * 2. 识别时间范围条件，转换为Druid的intervals参数
   * 3. 区分可以下推和不能下推的过滤条件
   * 4. 优化过滤条件的表达式，利用Druid的索引能力
   * 
   * Druid支持多种过滤条件类型：
   * - 时间范围过滤（通过intervals参数）
   * - 维度过滤（等于、不等于、IN、LIKE等）
   * - 数值过滤（比较运算符）
   * - 布尔逻辑过滤（AND、OR、NOT）
   * 
   * 该规则会尝试将尽可能多的过滤条件下推到Druid层，
   * 不能下推的条件保留在Calcite层执行。
   */
  public static class DruidFilterRule
      extends RelRule<DruidFilterRule.DruidFilterRuleConfig> {

    /** 创建DruidFilterRule实例，使用给定的配置
     * @param config 规则配置对象，包含操作数类型等信息 */
    protected DruidFilterRule(DruidFilterRuleConfig config) {
      super(config);
    }

    /** 规则匹配时的处理逻辑，将Filter转换为Druid查询
     * @param call 规则调用对象，包含匹配的关系节点信息 */
    @Override public void onMatch(RelOptRuleCall call) {
      // 获取Filter节点（第一层关系节点）
      final Filter filter = call.rel(0);
      // 获取DruidQuery节点（第二层关系节点，Filter的输入）
      final DruidQuery query = call.rel(1);
      // 获取集群对象，包含优化器和类型工厂等信息
      final RelOptCluster cluster = filter.getCluster();
      // 创建关系构建器，用于构建新的关系节点
      final RelBuilder relBuilder = call.builder();
      // 获取表达式构建器，用于构建和操作Rex表达式
      final RexBuilder rexBuilder = cluster.getRexBuilder();

      // 检查在当前签名基础上添加'f'（filter）是否有效
      // Druid查询有严格的操作顺序限制，必须符合签名规则
      if (!DruidQuery.isValidSignature(query.signature() + 'f')) {
        return; // 无效则不应用此规则
      }

      // 创建有效谓词列表（可以转换为Druid filter的谓词）
      final List<RexNode> validPreds = new ArrayList<>();
      // 创建无效谓词列表（不能转换为Druid filter的谓词）
      final List<RexNode> nonValidPreds = new ArrayList<>();
      // 获取表达式执行器，用于简化表达式
      // 如果优化器有执行器则使用，否则使用默认的EXECUTOR
      final RexExecutor executor =
          Util.first(cluster.getPlanner().getExecutor(), RexUtil.EXECUTOR);
      // 获取从下层节点上推的谓词列表，用于表达式简化
      final RelOptPredicateList predicates =
          call.getMetadataQuery().getPulledUpPredicates(filter.getInput());
      // 创建表达式简化器，用于优化和简化过滤条件
      final RexSimplify simplify =
          new RexSimplify(rexBuilder, predicates, executor);
      // 简化过滤条件，将UNKNOWN转换为FALSE，使表达式更加简洁
      final RexNode cond =
          simplify.simplifyUnknownAsFalse(filter.getCondition());
      // 遍历简化后的条件的合取项（AND连接的各个子条件）
      for (RexNode e : RelOptUtil.conjunctions(cond)) {
        // 尝试将每个子条件转换为Druid JSON filter
        DruidJsonFilter druidJsonFilter =
            DruidJsonFilter.toDruidFilters(e, filter.getInput().getRowType(),
                query, rexBuilder);
        if (druidJsonFilter != null) {
          // 转换成功，添加到有效谓词列表
          validPreds.add(e);
        } else {
          // 转换失败，添加到无效谓词列表
          nonValidPreds.add(e);
        }
      }

      // 处理时间戳字段：获取时间戳字段在行类型中的索引位置
      int timestampFieldIdx =
          query.getRowType().getFieldNames()
              .indexOf(query.druidTable.timestampFieldName);
      // 初始化新的Druid查询节点
      RelNode newDruidQuery = query;
      // 将过滤条件分为三类：时间范围条件、可下推条件、不可下推条件
      final Triple<List<RexNode>, List<RexNode>, List<RexNode>> triple =
          splitFilters(validPreds, nonValidPreds, timestampFieldIdx);
      // 如果没有时间范围条件和可下推条件，则无法下推任何过滤
      if (triple.getLeft().isEmpty() && triple.getMiddle().isEmpty()) {
        // 没有可以下推的内容，直接返回
        return;
      }
      // 获取不可下推的条件，这些条件需要在Calcite层执行
      final List<RexNode> residualPreds = new ArrayList<>(triple.getRight());
      // 初始化时间间隔列表，用于存储Druid的时间范围过滤
      List<Interval> intervals = null;
      // 如果有时间范围条件
      if (!triple.getLeft().isEmpty()) {
        // 获取连接配置，用于获取时区信息
        final CalciteConnectionConfig connectionConfig =
            requireNonNull(
                cluster.getPlanner().getContext()
                    .unwrap(CalciteConnectionConfig.class));
        // 确保时区配置不为空
        requireNonNull(connectionConfig.timeZone());
        // 将时间范围条件转换为Druid的intervals格式
        intervals =
            DruidDateTimeUtils.createInterval(
                RexUtil.composeConjunction(rexBuilder, triple.getLeft()));
        // 如果转换失败或结果为空
        if (intervals == null || intervals.isEmpty()) {
          // 某些extract函数无法转换为interval，将其移到可下推条件列表
          triple.getMiddle().addAll(triple.getLeft());
        }
      }

      // 如果有可下推的过滤条件（非时间范围）
      if (!triple.getMiddle().isEmpty()) {
        // 创建新的Filter节点，使用可下推的条件
        final RelNode newFilter =
            filter.copy(filter.getTraitSet(), Util.last(query.rels),
                RexUtil.composeConjunction(rexBuilder, triple.getMiddle()));
        // 将Filter扩展到Druid查询中
        newDruidQuery = DruidQuery.extendQuery(query, newFilter);
      }
      // 如果有时间间隔（时间范围过滤）
      if (intervals != null && !intervals.isEmpty()) {
        // 将时间间隔扩展到Druid查询中
        newDruidQuery = DruidQuery.extendQuery((DruidQuery) newDruidQuery, intervals);
      }
      // 如果还有不可下推的条件
      if (!residualPreds.isEmpty()) {
        // 在Druid查询之上添加Filter节点来处理这些条件
        newDruidQuery = relBuilder
            .push(newDruidQuery)
            .filter(residualPreds)
            .build();
      }
      // 将转换后的查询节点作为结果返回
      call.transformTo(newDruidQuery);
    }

    /**
     * 将过滤条件分为三类：时间范围条件、可下推条件、不可下推条件
     * 
     * 该方法接收包含Druid支持操作的条件列表和包含不支持操作的条件列表，
     * 输出一个三元组，包含三个不同类别的条件：
     * 1. 左边（Left）：时间戳列上的条件过滤，这些条件可以转换为Druid的intervals参数
     * 2. 中间（Middle）：可以下推到Druid的条件过滤（非时间范围）
     * 3. 右边（Right）：不能下推到Druid的条件过滤，需要在Calcite层执行
     *
     * 分类策略：
     * - 如果条件只引用时间戳字段，归类为时间范围条件
     * - 如果条件引用了时间戳字段和其他字段，归类为可下推条件
     * - 如果条件不引用时间戳字段，但Druid支持，归类为可下推条件
     * - 不支持的条件归类为不可下推条件
     *
     * @param validPreds Druid支持的有效谓词列表
     * @param nonValidPreds Druid不支持的无效谓词列表
     * @param timestampFieldIdx 时间戳字段在行类型中的索引位置
     * @return 包含三类条件的Triple对象
     */
    private static Triple<List<RexNode>, List<RexNode>, List<RexNode>> splitFilters(
        final List<RexNode> validPreds,
        final List<RexNode> nonValidPreds, final int timestampFieldIdx) {
      // 创建时间范围条件列表，存储只涉及时间戳字段的条件
      final List<RexNode> timeRangeNodes = new ArrayList<>();
      // 创建可下推条件列表，存储可以转换为Druid filter的条件（非时间范围）
      final List<RexNode> pushableNodes = new ArrayList<>();
      // 创建不可下推条件列表，初始化为无效谓词列表
      final List<RexNode> nonPushableNodes = new ArrayList<>(nonValidPreds);
      // 遍历所有有效的谓词条件
      for (RexNode conj : validPreds) {
        // 创建输入引用访问器，用于分析条件引用了哪些字段
        final RelOptUtil.InputReferencedVisitor visitor = new RelOptUtil.InputReferencedVisitor();
        // 让访问器遍历条件表达式，收集引用的字段位置
        conj.accept(visitor);
        // 检查条件是否只引用时间戳字段（且只引用这一个字段）
        if (visitor.inputPosReferenced.contains(timestampFieldIdx)
            && visitor.inputPosReferenced.size() == 1) {
          // 只引用时间戳字段，归类为时间范围条件
          timeRangeNodes.add(conj);
        } else {
          // 引用其他字段或多个字段，归类为可下推条件
          pushableNodes.add(conj);
        }
      }
      // 返回包含三类条件的三元组
      return ImmutableTriple.of(timeRangeNodes, pushableNodes, nonPushableNodes);
    }

    /** DruidFilterRule的配置接口
     * 使用Immutables库生成不可变的配置对象
     * 定义了规则的默认配置和如何创建规则实例 */
    @Value.Immutable(singleton = false)
    public interface DruidFilterRuleConfig extends RelRule.Config {
      // 默认配置实例，使用builder模式构建
      // 定义了规则的匹配模式：Filter节点作为输入，下层是DruidQuery节点
      DruidFilterRuleConfig DEFAULT = ImmutableDruidFilterRuleConfig.builder()
          .withOperandSupplier(b0 ->
              b0.operand(Filter.class).oneInput(b1 ->
                  b1.operand(DruidQuery.class).noInputs()))
          .build();

      /** 根据当前配置创建DruidFilterRule实例
       * @return 新的DruidFilterRule对象 */
      @Override default DruidFilterRule toRule() {
        return new DruidFilterRule(this);
      }
    }
  }

  /** Druid HAVING过滤规则：将Having条件（聚合后的Filter）下推到DruidQuery中
   * 
   * 该规则负责将Calcite的HAVING子句转换为Druid的having filter表达式。
   * HAVING子句是在GROUP BY聚合之后执行的过滤条件，与WHERE子句不同。
   * 
   * 主要功能：
   * 1. 识别HAVING条件（在Aggregate之上的Filter）
   * 2. 将HAVING条件转换为Druid的having filter格式
   * 3. 支持对聚合结果的过滤（如 SUM(sales) > 1000）
   * 4. 利用Druid的原生having能力优化查询性能
   * 
   * Druid的having filter特点：
   * - 在聚合之后执行
   * - 可以引用聚合函数的结果
   * - 支持常见的比较运算符和逻辑运算符
   * - 可以减少从Druid返回的数据量
   * 
   * 注意：该规则只处理Druid支持的having条件，
   * 不支持的条件会在Calcite层执行。 */
  public static class DruidHavingFilterRule
      extends RelRule<DruidHavingFilterRule.DruidHavingFilterRuleConfig> {

    /** 创建DruidHavingFilterRule实例
     * @param config 规则配置对象 */
    protected DruidHavingFilterRule(DruidHavingFilterRuleConfig config) {
      super(config);
    }

    /** 规则匹配时的处理逻辑，将Having Filter转换为Druid查询
     * @param call 规则调用对象 */
    @Override public void onMatch(RelOptRuleCall call) {
      // 获取Filter节点（HAVING条件）
      final Filter filter = call.rel(0);
      // 获取DruidQuery节点（Filter的输入）
      final DruidQuery query = call.rel(1);
      // 获取集群对象
      final RelOptCluster cluster = filter.getCluster();
      // 获取表达式构建器
      final RexBuilder rexBuilder = cluster.getRexBuilder();

      // 检查在当前签名基础上添加'h'（having）是否有效
      if (!DruidQuery.isValidSignature(query.signature() + 'h')) {
        return; // 无效则不应用此规则
      }

      // 获取过滤条件
      final RexNode cond = filter.getCondition();
      // 尝试将HAVING条件转换为Druid JSON filter
      // 注意：这里使用query.getTopNode().getRowType()，因为having引用的是聚合后的字段
      final DruidJsonFilter druidJsonFilter =
          DruidJsonFilter.toDruidFilters(cond, query.getTopNode().getRowType(),
              query, rexBuilder);
      // 如果转换成功
      if (druidJsonFilter != null) {
        // 创建新的Filter节点，使用原始条件
        final RelNode newFilter = filter
            .copy(filter.getTraitSet(), Util.last(query.rels), filter.getCondition());
        // 将Filter扩展到Druid查询中（作为having filter）
        final DruidQuery newDruidQuery = DruidQuery.extendQuery(query, newFilter);
        // 返回转换后的查询
        call.transformTo(newDruidQuery);
      }
    }

    /** DruidHavingFilterRule的配置接口
     * 定义了规则的默认配置和如何创建规则实例 */
    @Value.Immutable(singleton = false)
    public interface DruidHavingFilterRuleConfig extends RelRule.Config {
      // 默认配置实例，定义了规则的匹配模式
      DruidHavingFilterRuleConfig DEFAULT = ImmutableDruidHavingFilterRuleConfig.builder()
          .withOperandSupplier(b0 ->
              b0.operand(Filter.class).oneInput(b1 ->
                  b1.operand(DruidQuery.class).noInputs()))
          .build();

      /** 根据当前配置创建DruidHavingFilterRule实例
       * @return 新的DruidHavingFilterRule对象 */
      @Override default DruidHavingFilterRule toRule() {
        return new DruidHavingFilterRule(this);
      }
    }
  }

  /**
   * Druid投影规则：将Project操作下推到DruidQuery中
   * 
   * 该规则负责将Calcite的Project节点转换为Druid的select表达式。
   * Project节点对应SQL中的SELECT子句，用于选择和计算输出字段。
   * 
   * 主要功能：
   * 1. 将SELECT表达式转换为Druid的select和dimensions/metrics
   * 2. 支持列引用、表达式计算、函数调用等
   * 3. 优化投影操作，减少不必要的数据传输
   * 4. 支持部分下推：部分表达式在Druid层计算，部分在Calcite层计算
   * 
   * Druid的select能力：
   * - 选择维度列（dimensions）
   * - 选择度量列（metrics）
   * - 支持简单的表达式计算
   * - 支持虚拟列（virtual columns）
   * 
   * 该规则会尝试将尽可能多的投影表达式下推到Druid层，
   * 对于Druid不支持的表达式，保留在Calcite层执行。
   */
  public static class DruidProjectRule
      extends RelRule<DruidProjectRule.DruidProjectRuleConfig> {

    /** 创建DruidProjectRule实例
     * @param config 规则配置对象 */
    protected DruidProjectRule(DruidProjectRuleConfig config) {
      super(config);
    }

    /** 检查规则是否匹配
     * Druid不支持相关变量（correlated variables），所以必须检查
     * @param call 规则调用对象
     * @return 如果Project没有相关变量则返回true，否则返回false */
    @Override public boolean matches(RelOptRuleCall call) {
      final Project project = call.rel(0);
      // 检查Project是否包含相关变量集合，如果为空则可以应用此规则
      return project.getVariablesSet().isEmpty();
    }

    /** 规则匹配时的处理逻辑，将Project转换为Druid查询
     * @param call 规则调用对象 */
    @Override public void onMatch(RelOptRuleCall call) {
      // 获取Project节点
      final Project project = call.rel(0);
      // 获取DruidQuery节点
      final DruidQuery query = call.rel(1);
      // 获取集群对象
      final RelOptCluster cluster = project.getCluster();
      // 获取表达式构建器
      final RexBuilder rexBuilder = cluster.getRexBuilder();
      // 检查在当前签名基础上添加'p'（project）是否有效
      if (!DruidQuery.isValidSignature(query.signature() + 'p')) {
        return; // 无效则不应用此规则
      }

      // 尝试将整个Project作为scan下推（所有表达式都可以在Druid层计算）
      if (DruidQuery.computeProjectAsScan(project, query.getTable().getRowType(), query)
          != null) {
        // 所有表达式都可以完整地下推到Druid
        final RelNode newProject =
            project.copy(project.getTraitSet(),
                ImmutableList.of(Util.last(query.rels)));
        // 将Project扩展到Druid查询中
        RelNode newNode = DruidQuery.extendQuery(query, newProject);
        // 返回转换后的查询
        call.transformTo(newNode);
        return;
      }

      // 尝试分割Project：部分下推到Druid，部分保留在Calcite
      final Pair<List<RexNode>, List<RexNode>> pair =
          splitProjects(rexBuilder, query, project.getProjects());
      if (pair == null) {
        // 无法下推任何有用的内容到Druid
        return;
      }
      // 获取需要在Calcite层执行的表达式（above）
      final List<RexNode> above = pair.left;
      // 获取可以在Druid层执行的表达式（below）
      final List<RexNode> below = pair.right;
      // 创建类型构建器，用于构建下推Project的行类型
      final RelDataTypeFactory.Builder builder =
          cluster.getTypeFactory().builder();
      // 获取Druid查询的最后一个关系节点
      final RelNode input = Util.last(query.rels);
      // 遍历下推的表达式，构建行类型
      for (RexNode e : below) {
        final String name;
        if (e instanceof RexInputRef) {
          // 如果是输入引用，使用原始字段名
          name = input.getRowType().getFieldNames().get(((RexInputRef) e).getIndex());
        } else {
          // 如果是表达式，字段名为null（会自动生成）
          name = null;
        }
        // 添加字段到类型构建器
        builder.add(name, e.getType());
      }
      // 创建下推的Project节点（在Druid层执行）
      final RelNode newProject =
          project.copy(project.getTraitSet(), input, below, builder.build());
      // 将下推的Project扩展到Druid查询中
      final DruidQuery newQuery = DruidQuery.extendQuery(query, newProject);
      // 创建上层的Project节点（在Calcite层执行，处理剩余表达式）
      final RelNode newProject2 =
              project.copy(project.getTraitSet(), newQuery, above,
                  project.getRowType());
      // 返回转换后的查询
      call.transformTo(newProject2);
    }

    /** 分割Project表达式为两部分：需要在Calcite层执行的表达式和可以在Druid层执行的表达式
     * 
     * 该方法分析Project中的所有表达式，确定哪些可以下推到Druid，哪些必须保留在Calcite层。
     * 策略是：如果表达式引用了所有输入字段，则无法下推（因为需要全表扫描）；
     * 如果只引用部分字段，则可以将这些字段下推，其他表达式保留。
     *
     * @param rexBuilder 表达式构建器，用于创建新的表达式
     * @param input 输入关系节点
     * @param nodes Project中的表达式列表
     * @return Pair对象，左边是需要在Calcite层执行的表达式，右边是可以在Druid层执行的表达式
     *         如果无法下推任何内容，返回null */
    private static @Nullable Pair<List<RexNode>, List<RexNode>> splitProjects(
        final RexBuilder rexBuilder, final RelNode input, List<RexNode> nodes) {
      // 创建输入引用访问器，用于分析表达式引用了哪些输入字段
      final RelOptUtil.InputReferencedVisitor visitor =
          new RelOptUtil.InputReferencedVisitor();
      // 让访问器遍历所有表达式，收集引用的字段位置
      visitor.visitEach(nodes);
      // 检查是否引用了所有输入字段
      if (visitor.inputPosReferenced.size() == input.getRowType().getFieldCount()) {
        // 引用了所有输入字段，无法优化下推
        return null;
      }
      // 创建下推节点列表（在Druid层执行的简单字段引用）
      final List<RexNode> belowNodes = new ArrayList<>();
      // 创建下推节点类型列表
      final List<RelDataType> belowTypes = new ArrayList<>();
      // 获取被引用的字段位置列表
      final List<Integer> positions = Lists.newArrayList(visitor.inputPosReferenced);
      // 遍历被引用的字段位置
      for (int i : positions) {
        // 创建对该字段的输入引用表达式
        final RexNode node = rexBuilder.makeInputRef(input, i);
        belowNodes.add(node);
        belowTypes.add(node.getType());
      }
      // 使用RexShuttle重写表达式，将输入引用映射到新的位置
      final List<RexNode> aboveNodes = new RexShuttle() {
        /** 重写输入引用表达式
         * @param ref 原始的输入引用
         * @return 重写后的输入引用，引用下推后的字段 */
        @Override public RexNode visitInputRef(RexInputRef ref) {
          // 找到原始字段位置在新列表中的索引
          final int index = positions.indexOf(ref.getIndex());
          // 创建新的输入引用，引用下推后的字段
          return rexBuilder.makeInputRef(belowTypes.get(index), index);
        }
      }.visitList(nodes);
      // 返回分割后的表达式对
      return Pair.of(aboveNodes, belowNodes);
    }

    /** DruidProjectRule的配置接口
     * 定义了规则的默认配置和如何创建规则实例 */
    @Value.Immutable(singleton = false)
    public interface DruidProjectRuleConfig extends RelRule.Config {
      // 默认配置实例
      DruidProjectRuleConfig DEFAULT = ImmutableDruidProjectRuleConfig.builder()
          .withOperandSupplier(b0 ->
              b0.operand(Project.class).oneInput(b1 ->
                  b1.operand(DruidQuery.class).noInputs()))
          .build();

      /** 根据当前配置创建DruidProjectRule实例
       * @return 新的DruidProjectRule对象 */
      @Override default DruidProjectRule toRule() {
        return new DruidProjectRule(this);
      }
    }
  }

  /**
   * Druid后聚合投影规则：将Project作为Post Aggregator下推到DruidQuery中
   * 
   * 该规则负责将聚合后的Project操作转换为Druid的post-aggregation表达式。
   * Post Aggregation是Druid特有的功能，用于在聚合之后计算派生字段。
   * 
   * 主要功能：
   * 1. 识别聚合后的Project操作
   * 2. 将表达式转换为Druid的post-aggregation格式
   * 3. 支持对聚合结果的计算（如 SUM(a) + SUM(b)）
   * 4. 利用Druid的原生post-aggregation能力优化性能
   * 
   * Druid的post-aggregation特点：
   * - 在聚合之后执行
   * - 可以引用聚合函数的结果
   * - 支持算术运算、函数调用等
   * - 在Druid服务器端计算，减少数据传输
   * 
   * 与普通Project的区别：
   * - 普通Project在数据读取后立即执行
   * - Post Aggregation在聚合之后执行
   * - Post Aggregation只能引用聚合结果
   * - Post Aggregation在Druid查询的特定阶段执行
   */
  public static class DruidPostAggregationProjectRule
      extends RelRule<DruidPostAggregationProjectRule.DruidPostAggregationProjectRuleConfig> {

    /** 创建DruidPostAggregationProjectRule实例
     * @param config 规则配置对象 */
    protected DruidPostAggregationProjectRule(DruidPostAggregationProjectRuleConfig config) {
      super(config);
    }

    /** 规则匹配时的处理逻辑，将Project作为Post Aggregator下推
     * @param call 规则调用对象 */
    @Override public void onMatch(RelOptRuleCall call) {
      // 获取Project节点
      Project project = call.rel(0);
      // 获取DruidQuery节点
      DruidQuery query = call.rel(1);
      // 检查在当前签名基础上添加'o'（post-aggregation）是否有效
      if (!DruidQuery.isValidSignature(query.signature() + 'o')) {
        return; // 无效则不应用此规则
      }
      // 检查Project中是否有函数调用表达式
      boolean hasRexCalls = false;
      for (RexNode rexNode : project.getProjects()) {
        if (rexNode instanceof RexCall) {
          // 找到函数调用表达式
          hasRexCalls = true;
          break;
        }
      }
      // 只有当Project会产生Post Aggregators时才尝试下推
      if (hasRexCalls) {

        // 获取Druid查询的顶层节点
        final RelNode topNode = query.getTopNode();
        // 获取顶层聚合节点（可能是Aggregate或Filter + Aggregate）
        final Aggregate topAgg;
        if (topNode instanceof Aggregate) {
          // 顶层是Aggregate节点
          topAgg = (Aggregate) topNode;
        } else {
          // 顶层是Filter节点，其输入是Aggregate节点
          topAgg = (Aggregate) ((Filter) topNode).getInput();
        }

        // 检查每个Project表达式是否可以转换为Druid表达式
        for (RexNode rexNode : project.getProjects()) {
          // 尝试将表达式转换为Druid表达式
          if (DruidExpressions.toDruidExpression(rexNode, topAgg.getRowType(), query) == null) {
            // 转换失败，不能下推
            return;
          }
        }
        // 所有表达式都可以转换，创建新的Project节点
        final RelNode newProject = project
            .copy(project.getTraitSet(), ImmutableList.of(Util.last(query.rels)));
        // 将Project作为Post Aggregation扩展到Druid查询中
        final DruidQuery newQuery = DruidQuery.extendQuery(query, newProject);
        // 返回转换后的查询
        call.transformTo(newQuery);
      }
    }

    /** DruidPostAggregationProjectRule的配置接口
     * 定义了规则的默认配置和如何创建规则实例 */
    @Value.Immutable(singleton = false)
    public interface DruidPostAggregationProjectRuleConfig extends RelRule.Config {
      // 默认配置实例
      DruidPostAggregationProjectRuleConfig DEFAULT =
          ImmutableDruidPostAggregationProjectRuleConfig.builder()
              .withOperandSupplier(b0 ->
                  b0.operand(Project.class).oneInput(b1 ->
                      b1.operand(DruidQuery.class).noInputs()))
              .build();

      /** 根据当前配置创建DruidPostAggregationProjectRule实例
       * @return 新的DruidPostAggregationProjectRule对象 */
      @Override default DruidPostAggregationProjectRule toRule() {
        return new DruidPostAggregationProjectRule(this);
      }
    }
  }

  /**
   * Druid聚合规则：将Aggregate操作下推到DruidQuery中
   * 
   * 该规则负责将Calcite的Aggregate节点转换为Druid的groupBy和aggregations。
   * Aggregate节点对应SQL中的GROUP BY和聚合函数（SUM、COUNT、AVG等）。
   * 
   * 主要功能：
   * 1. 将GROUP BY转换为Druid的groupBy dimensions
   * 2. 将聚合函数转换为Druid的aggregations
   * 3. 支持常见的聚合函数（COUNT、SUM、MIN、MAX、AVG等）
   * 4. 处理分组集（Group Sets，目前只支持单个分组集）
   * 
   * Druid的聚合能力：
   * - 支持维度分组（groupBy）
   * - 支持多种聚合函数（count、sum、min、max、avg等）
   * - 支持过滤聚合（filtered aggregations）
   * - 支持近似聚合（如approxCountDistinct）
   * - 聚合在Druid服务器端执行，利用其列式存储优势
   * 
   * 限制：
   * - 目前只支持单个分组集（不支持GROUPING SETS、CUBE、ROLLUP）
   * - 聚合函数必须能转换为Druid支持的格式
   * - 分组字段必须能映射到Druid的dimensions
   */
  public static class DruidAggregateRule
      extends RelRule<DruidAggregateRule.DruidAggregateRuleConfig> {

    /** 创建DruidAggregateRule实例
     * @param config 规则配置对象 */
    protected DruidAggregateRule(DruidAggregateRuleConfig config) {
      super(config);
    }

    /** 规则匹配时的处理逻辑，将Aggregate转换为Druid查询
     * @param call 规则调用对象 */
    @Override public void onMatch(RelOptRuleCall call) {
      // 获取Aggregate节点
      final Aggregate aggregate = call.rel(0);
      // 获取DruidQuery节点
      final DruidQuery query = call.rel(1);
      // 获取Druid查询的顶层节点
      final RelNode topDruidNode = query.getTopNode();
      // 检查顶层节点是否是Project，如果是则获取该Project
      final Project project = topDruidNode instanceof Project ? (Project) topDruidNode : null;
      // 检查在当前签名基础上添加'a'（aggregate）是否有效
      if (!DruidQuery.isValidSignature(query.signature() + 'a')) {
        return; // 无效则不应用此规则
      }

      // 检查分组集数量，Druid目前只支持单个分组集
      if (aggregate.getGroupSets().size() != 1) {
        return; // 多个分组集不支持
      }
      // 计算Project的分组集，检查是否可以映射到Druid的dimensions
      if (DruidQuery
          .computeProjectGroupSet(project, aggregate.getGroupSet(), query.table.getRowType(), query)
          == null) {
        return; // 无法映射到Druid的dimensions
      }
      // 获取聚合函数的名称列表（跳过分组字段）
      final List<String> aggNames = Util
          .skip(aggregate.getRowType().getFieldNames(), aggregate.getGroupSet().cardinality());
      // 计算Druid JSON聚合，检查所有聚合函数是否支持
      if (DruidQuery.computeDruidJsonAgg(aggregate.getAggCallList(), aggNames, project, query)
          == null) {
        return; // 有聚合函数不支持
      }
      // 创建新的Aggregate节点
      final RelNode newAggregate = aggregate
          .copy(aggregate.getTraitSet(), ImmutableList.of(query.getTopNode()));
      // 将Aggregate扩展到Druid查询中
      call.transformTo(DruidQuery.extendQuery(query, newAggregate));
    }

    /** DruidAggregateRule的配置接口
     * 定义了规则的默认配置和如何创建规则实例 */
    @Value.Immutable(singleton = false)
    public interface DruidAggregateRuleConfig extends RelRule.Config {
      // 默认配置实例
      DruidAggregateRuleConfig DEFAULT = ImmutableDruidAggregateRuleConfig.builder()
          .withOperandSupplier(b0 ->
              b0.operand(Aggregate.class).oneInput(b1 ->
                  b1.operand(DruidQuery.class).noInputs()))
          .build();

      /** 根据当前配置创建DruidAggregateRule实例
       * @return 新的DruidAggregateRule对象 */
      @Override default DruidAggregateRule toRule() {
        return new DruidAggregateRule(this);
      }
    }
  }

  /**
   * Druid聚合+投影组合规则：将Aggregate和Project同时下推到DruidQuery中
   * 
   * 该规则负责处理Aggregate和Project的组合操作，将它们一起转换为Druid查询。
   * 这种模式在SQL中很常见，例如：
   * SELECT SUM(amount) * 1.1 AS total_with_tax
   * FROM sales
   * GROUP BY region
   * 
   * 主要功能：
   * 1. 同时处理Aggregate和Project节点
   * 2. 优化过滤聚合（filtered aggregations）
   * 3. 将聚合后的计算转换为Druid的post-aggregations
   * 4. 处理聚合函数的过滤条件（FILTER子句）
   * 
   * 过滤聚合优化：
   * - 提取公共过滤条件到外层filter
   * - 简化总是为TRUE或FALSE的过滤条件
   * - 将聚合过滤与外层过滤AND在一起，以优化数据剪枝
   * 
   * 该规则比单独的AggregateRule和ProjectRule更高效，
   * 因为它可以同时考虑两个节点的优化机会。
   */
  public static class DruidAggregateProjectRule
      extends RelRule<DruidAggregateProjectRule.DruidAggregateProjectRuleConfig> {

    /** 创建DruidAggregateProjectRule实例
     * @param config 规则配置对象 */
    protected DruidAggregateProjectRule(DruidAggregateProjectRuleConfig config) {
      super(config);
    }

    /** 规则匹配时的处理逻辑，将Aggregate和Project同时转换为Druid查询
     * @param call 规则调用对象 */
    @Override public void onMatch(RelOptRuleCall call) {
      // 获取Aggregate节点（第一层）
      final Aggregate aggregate = call.rel(0);
      // 获取Project节点（第二层）
      final Project project = call.rel(1);
      // 获取DruidQuery节点（第三层）
      final DruidQuery query = call.rel(2);
      // 检查在当前签名基础上添加'p'（project）和'a'（aggregate）是否有效
      if (!DruidQuery.isValidSignature(query.signature() + 'p' + 'a')) {
        return; // 无效则不应用此规则
      }
      // 检查分组集数量，Druid目前只支持单个分组集
      if (aggregate.getGroupSets().size() != 1) {
        return; // 多个分组集不支持
      }
      // 计算Project的分组集，检查是否可以映射到Druid的dimensions
      if (DruidQuery
          .computeProjectGroupSet(project, aggregate.getGroupSet(), query.table.getRowType(), query)
          == null) {
        return; // 无法映射到Druid的dimensions
      }
      // 获取聚合函数的名称列表（跳过分组字段）
      final List<String> aggNames = Util
          .skip(aggregate.getRowType().getFieldNames(), aggregate.getGroupSet().cardinality());
      // 计算Druid JSON聚合，检查所有聚合函数是否支持
      if (DruidQuery.computeDruidJsonAgg(aggregate.getAggCallList(), aggNames, project, query)
          == null) {
        return; // 有聚合函数不支持
      }
      // 创建新的Project节点
      final RelNode newProject =
          project.copy(project.getTraitSet(),
              ImmutableList.of(Util.last(query.rels)));
      // 创建新的Aggregate节点，以newProject为输入
      final RelNode newAggregate =
          aggregate.copy(aggregate.getTraitSet(),
              ImmutableList.of(newProject));
      // 获取聚合调用中的过滤引用（用于过滤聚合优化）
      List<Integer> filterRefs = getFilterRefs(aggregate.getAggCallList());
      // 根据是否有过滤引用决定优化策略
      final DruidQuery query2;
      if (!filterRefs.isEmpty()) {
        // 有过滤引用，执行过滤聚合优化
        query2 =
            optimizeFilteredAggregations(call, query, (Project) newProject,
                (Aggregate) newAggregate);
      } else {
        // 没有过滤引用，直接扩展查询
        final DruidQuery query1 = DruidQuery.extendQuery(query, newProject);
        query2 = DruidQuery.extendQuery(query1, newAggregate);
      }
      // 返回转换后的查询
      call.transformTo(query2);
    }

    /** 从给定的聚合调用列表中返回唯一的过滤引用集合
     * 
     * 该方法遍历所有聚合调用，收集所有带有过滤条件的聚合调用的过滤参数索引。
     * 这些过滤引用用于后续的过滤聚合优化。
     *
     * @param calls 聚合调用列表
     * @return 包含所有唯一过滤引用的集合 */
    private static Set<Integer> getUniqueFilterRefs(List<AggregateCall> calls) {
      // 创建集合存储唯一的过滤引用
      Set<Integer> refs = new HashSet<>();
      // 遍历所有聚合调用
      for (AggregateCall call : calls) {
        // 检查聚合调用是否有过滤条件
        if (call.hasFilter()) {
          // 添加过滤参数索引到集合
          refs.add(call.filterArg);
        }
      }
      // 返回唯一的过滤引用集合
      return refs;
    }

    /**
     * 尝试优化DruidQuery中带有过滤条件的聚合
     * 
     * 该方法使用以下步骤优化过滤聚合：
     * <ol>
     * <li>尝试将公共过滤条件提取到外层的"filter"字段中；
     * <li>尽可能消除总是为TRUE或FALSE的表达式；
     * <li>将聚合过滤条件与外层过滤条件AND在一起，以允许数据剪枝。
     * </ol>
     *
     * <p>应该在将聚合和投影下推到Druid之前调用此方法。
     * 假设至少有一个聚合调用附加了过滤条件。
     *
     * @param call 规则调用对象
     * @param query Druid查询对象
     * @param project 投影节点
     * @param aggregate 聚合节点
     * @return 优化后的Druid查询对象 */
    private static DruidQuery optimizeFilteredAggregations(RelOptRuleCall call,
        DruidQuery query,
        Project project, Aggregate aggregate) {
      // 初始化过滤节点
      Filter filter = null;
      // 获取表达式构建器
      final RexBuilder builder = query.getCluster().getRexBuilder();
      // 获取表达式执行器
      final RexExecutor executor =
          Util.first(query.getCluster().getPlanner().getExecutor(),
              RexUtil.EXECUTOR);
      // 获取表扫描节点（第一个关系节点）
      final RelNode scan = query.rels.get(0);
      // 获取从扫描节点上推的谓词列表
      final RelOptPredicateList predicates =
          call.getMetadataQuery().getPulledUpPredicates(scan);
      // 创建表达式简化器
      final RexSimplify simplify =
          new RexSimplify(builder, predicates, executor);

      // 检查Druid查询是否原始包含过滤节点
      boolean containsFilter = false;
      for (RelNode node : query.rels) {
        if (node instanceof Filter) {
          // 找到过滤节点
          filter = (Filter) node;
          containsFilter = true;
          break;
        }
      }

      // 检查是否所有聚合调用都有过滤参数引用
      boolean allHaveFilters = allAggregatesHaveFilters(aggregate.getAggCallList());

      // 获取唯一的过滤引用集合
      Set<Integer> uniqueFilterRefs = getUniqueFilterRefs(aggregate.getAggCallList());

      // 此方法的前置条件之一：必须至少有一个过滤引用
      assert !uniqueFilterRefs.isEmpty();

      // 创建新的聚合调用列表
      List<AggregateCall> newCalls = new ArrayList<>();

      // 将所有过滤条件OR在一起，以便可以与外层过滤AND
      List<RexNode> disjunctions = new ArrayList<>();
      for (Integer i : uniqueFilterRefs) {
        // 移除IS_TRUE包装，添加到析取列表
        disjunctions.add(stripFilter(project.getProjects().get(i)));
      }
      // 创建析取表达式（OR所有过滤条件）
      RexNode filterNode = RexUtil.composeDisjunction(builder, disjunctions);

      // 擦除过滤引用
      for (AggregateCall aggCall : aggregate.getAggCallList()) {
        // 如果只有一个唯一过滤引用且所有聚合都有过滤（过滤被提取）
        // 或者聚合有过滤且过滤条件总是为TRUE
        if ((uniqueFilterRefs.size() == 1
                && allHaveFilters) // 过滤被提取
            || aggCall.hasFilter()
            && project.getProjects().get(aggCall.filterArg).isAlwaysTrue()) {
          // 移除过滤引用（设为-1表示无过滤）
          aggCall = aggCall.withFilter(-1);
        }
        newCalls.add(aggCall);
      }
      // 创建新的Aggregate节点，使用新的聚合调用列表
      aggregate =
          aggregate.copy(aggregate.getTraitSet(), aggregate.getInput(),
              aggregate.getGroupSet(), aggregate.getGroupSets(), newCalls);

      // 如果原始查询包含过滤节点
      if (containsFilter) {
        // 将当前过滤节点与原始过滤条件AND在一起
        filterNode = builder.makeCall(SqlStdOperatorTable.AND, filterNode, filter.getCondition());
      }

      // 尽可能简化过滤表达式
      RexNode tempFilterNode = filterNode;
      filterNode = simplify.simplifyUnknownAsFalse(filterNode);

      // 简化后表达式可能总是为FALSE
      // Druid无法处理这样的过滤
      // 当以下表达式（f_n+1可能不存在）时会发生这种情况：
      // f_n+1 AND (f_1 OR f_2 OR ... OR f_n) 简化为总是FALSE
      // f_n+1不能为FALSE，因为它来自下推的过滤节点
      // 每个f_i不能为FALSE，因为DruidAggregateProjectRule会捕获这种情况
      // 所以唯一的解决方案是恢复到未简化的版本，让Druid处理最终不可满足的过滤
      if (filterNode.isAlwaysFalse()) {
        filterNode = tempFilterNode;
      }

      // 创建新的过滤节点
      filter = LogicalFilter.create(scan, filterNode);

      // 决定是否添加新的过滤节点
      // 只有当过滤条件不是总是为TRUE且所有聚合都有过滤时才添加
      boolean addNewFilter = !filter.getCondition().isAlwaysTrue() && allHaveFilters;
      // 假设过滤节点总是在表扫描节点之后
      // 表扫描节点总是存在
      int startIndex = containsFilter && addNewFilter ? 2 : 1;

      // 构造新的关系节点列表
      List<RelNode> newNodes =
          constructNewNodes(query.rels, addNewFilter, startIndex,
              filter, project, aggregate);

      // 创建新的Druid查询对象，使用新的关系节点列表
      return DruidQuery.create(query.getCluster(),
             aggregate.getTraitSet().replace(query.getConvention()),
             query.getTable(), query.druidTable, newNodes);
    }

    /** 检查是否所有聚合调用都有过滤参数
     * @param calls 聚合调用列表
     * @return 如果所有聚合调用都有过滤参数则返回true，否则返回false */
    private static boolean allAggregatesHaveFilters(List<AggregateCall> calls) {
      // 遍历所有聚合调用
      for (AggregateCall call : calls) {
        // 如果有一个聚合调用没有过滤参数，返回false
        if (!call.hasFilter()) {
          return false;
        }
      }
      // 所有聚合调用都有过滤参数，返回true
      return true;
    }

    /**
     * 返回一个新的关系节点列表，按照旧节点的顺序、给定的过滤节点和任何额外节点的顺序
     * 
     * 该方法用于重构Druid查询的关系节点列表，在适当的位置插入新的过滤节点，
     * 并确保所有节点正确链接。
     *
     * @param oldNodes 原始的关系节点列表
     * @param addFilter 是否添加新的过滤节点
     * @param startIndex 开始添加旧节点的索引位置
     * @param filter 要添加的过滤节点
     * @param trailingNodes 要添加的尾部节点（如project、aggregate）
     * @return 新的关系节点列表 */
    private static List<RelNode> constructNewNodes(List<RelNode> oldNodes,
        boolean addFilter, int startIndex, RelNode filter, RelNode... trailingNodes) {
      // 创建新的节点列表
      List<RelNode> newNodes = new ArrayList<>();

      // 第一个节点总是表扫描，所以任何过滤节点都应该在它之后
      newNodes.add(oldNodes.get(0));

      // 如果需要添加过滤节点
      if (addFilter) {
        // 添加过滤节点
        newNodes.add(filter);
        // 这是必需的，以便每个关系节点都链接到它之前的节点
        if (startIndex < oldNodes.size()) {
          // 获取下一个节点，复制它并使用过滤节点作为输入
          RelNode next = oldNodes.get(startIndex);
          newNodes.add(next.copy(next.getTraitSet(), Collections.singletonList(filter)));
          // 增加起始索引
          startIndex++;
        }
      }

      // 添加旧节点列表中的其余节点
      for (int i = startIndex; i < oldNodes.size(); i++) {
        newNodes.add(oldNodes.get(i));
      }

      // 添加尾部节点（需要链接它们）
      for (RelNode node : trailingNodes) {
        // 复制节点并使用新节点列表的最后一个节点作为输入
        newNodes.add(node.copy(node.getTraitSet(), Collections.singletonList(Util.last(newNodes))));
      }

      // 返回新的节点列表
      return newNodes;
    }

    /** 移除RexCall前面的IS_TRUE包装（如果存在）
     * 
     * Druid过滤条件可能被包装在IS_TRUE中，这个方法移除这个包装。
     *
     * @param node 可能包含IS_TRUE包装的表达式节点
     * @return 移除IS_TRUE包装后的表达式节点 */
    private static RexNode stripFilter(RexNode node) {
      // 检查节点类型是否为IS_TRUE
      if (node.getKind() == SqlKind.IS_TRUE) {
        // 返回IS_TRUE的操作数（去掉包装）
        return ((RexCall) node).getOperands().get(0);
      }
      // 没有IS_TRUE包装，直接返回
      return node;
    }

    /** 获取聚合调用列表中的过滤引用列表
     * @param calls 聚合调用列表
     * @return 包含所有过滤引用的列表 */
    private static List<Integer> getFilterRefs(List<AggregateCall> calls) {
      // 创建列表存储过滤引用
      List<Integer> refs = new ArrayList<>();
      // 遍历所有聚合调用
      for (AggregateCall call : calls) {
        // 如果聚合调用有过滤条件
        if (call.hasFilter()) {
          // 添加过滤参数索引到列表
          refs.add(call.filterArg);
        }
      }
      // 返回过滤引用列表
      return refs;
    }

    /** DruidAggregateProjectRule的配置接口
     * 定义了规则的默认配置和如何创建规则实例 */
    @Value.Immutable(singleton = false)
    public interface DruidAggregateProjectRuleConfig extends RelRule.Config {
      // 默认配置实例，定义了规则匹配模式：Aggregate -> Project -> DruidQuery
      DruidAggregateProjectRuleConfig DEFAULT = ImmutableDruidAggregateProjectRuleConfig.builder()
          .withOperandSupplier(b0 ->
              b0.operand(Aggregate.class).oneInput(b1 ->
                  b1.operand(Project.class).oneInput(b2 ->
                      b2.operand(DruidQuery.class).noInputs())))
          .build();

      /** 根据当前配置创建DruidAggregateProjectRule实例
       * @return 新的DruidAggregateProjectRule对象 */
      @Override default DruidAggregateProjectRule toRule() {
        return new DruidAggregateProjectRule(this);
      }
    }
  }

  /**
   * Druid排序规则：将Sort操作下推到DruidQuery中
   * 
   * 该规则负责将Calcite的Sort节点转换为Druid的limit和排序功能。
   * Sort节点对应SQL中的ORDER BY和LIMIT/OFFSET子句。
   * 
   * 主要功能：
   * 1. 将LIMIT转换为Druid的limit参数
   * 2. 将ORDER BY转换为Druid的排序功能（仅对groupBy查询有效）
   * 3. 限制：Druid不支持OFFSET（必须为0）
   * 
   * Druid的排序能力：
   * - SCAN查询：只支持LIMIT（纯limit），不支持排序
   * - GROUP BY查询：支持LIMIT和ORDER BY（对维度或度量排序）
   * - TIMESERIES查询：支持LIMIT和ORDER BY
   * - TOPN查询：本身就是排序查询
   * 
   * 限制：
   * - OFFSET必须为0（Druid不支持跳过行）
   * - SCAN查询只支持纯LIMIT，不支持排序
   * - 排序必须在Druid查询的最后阶段执行
   * 
   * 注意：Druid的排序能力有限，复杂的排序需要在Calcite层执行。
   */
  public static class DruidSortRule
      extends RelRule<DruidSortRule.DruidSortRuleConfig> {

    /** 创建DruidSortRule实例
     * @param config 规则配置对象 */
    protected DruidSortRule(DruidSortRuleConfig config) {
      super(config);
    }

    /** 规则匹配时的处理逻辑，将Sort转换为Druid查询
     * @param call 规则调用对象 */
    @Override public void onMatch(RelOptRuleCall call) {
      // 获取Sort节点
      final Sort sort = call.rel(0);
      // 获取DruidQuery节点
      final DruidQuery query = call.rel(1);
      // 检查在当前签名基础上添加'l'（limit）是否有效
      if (!DruidQuery.isValidSignature(query.signature() + 'l')) {
        return; // 无效则不应用此规则
      }
      // 支持的情况有两种：
      // 1. SCAN类型查询之上的纯limit（无排序）
      // 2. Druid group by查询中的排序和limit（对维度或度量排序）
      // 检查offset是否为0，Druid不支持offset（跳过行）
      if (sort.offset != null && RexLiteral.intValue(sort.offset) != 0) {
        // offset不为0，Druid不支持
        return;
      }
      // 如果是SCAN查询，必须是纯limit（无排序字段）
      if (query.getQueryType() == QueryType.SCAN && !RelOptUtil.isPureLimit(sort)) {
        // SCAN查询不支持排序
        return;
      }

      // 创建新的Sort节点
      final RelNode newSort = sort
          .copy(sort.getTraitSet(), ImmutableList.of(Util.last(query.rels)));
      // 将Sort扩展到Druid查询中
      call.transformTo(DruidQuery.extendQuery(query, newSort));
    }

    /** DruidSortRule的配置接口
     * 定义了规则的默认配置和如何创建规则实例 */
    @Value.Immutable(singleton = false)
    public interface DruidSortRuleConfig extends RelRule.Config {
      // 默认配置实例
      DruidSortRuleConfig DEFAULT = ImmutableDruidSortRuleConfig.builder()
          .withOperandSupplier(b0 ->
              b0.operand(Sort.class).oneInput(b1 ->
                  b1.operand(DruidQuery.class).noInputs()))
          .build();

      /** 根据当前配置创建DruidSortRule实例
       * @return 新的DruidSortRule对象 */
      @Override default DruidSortRule toRule() {
        return new DruidSortRule(this);
      }
    }
  }
}
