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
// Apache许可证头文件，声明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.enumerable;  // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite中用于可枚举适配器的包

import org.apache.calcite.linq4j.function.Experimental;  // 导入Experimental注解，用于标记实验性功能
import org.apache.calcite.plan.RelOptRule;  // 导入RelOptRule基类，所有优化规则都继承自这个类
import org.apache.calcite.rel.logical.LogicalAggregate;  // 导入逻辑聚合节点
import org.apache.calcite.rel.logical.LogicalMatch;  // 导入逻辑匹配节点
import org.apache.calcite.rel.logical.LogicalRepeatUnion;  // 导入逻辑重复联合节点
import org.apache.calcite.rel.logical.LogicalTableSpool;  // 导入逻辑表缓存节点
import org.apache.calcite.util.trace.CalciteTrace;  // 导入CalciteTrace工具类，用于获取日志记录器

import com.google.common.collect.ImmutableList;  // 导入Guava的ImmutableList类，用于创建不可变列表

import org.slf4j.Logger;  // 导入SLF4J的Logger接口，用于日志记录

import java.util.List;  // 导入Java的List接口

/**
 * Rules and relational operators for the
 * {@link EnumerableConvention enumerable calling convention}.
 */
// 类文档注释：该类包含了用于可枚举调用约定(EnumerableConvention)的规则和关系运算符
// 可枚举调用约定是Calcite中的一种执行模型，它将关系运算符转换为Java代码，通过LINQ4J库执行
public class EnumerableRules {  // 声明EnumerableRules类，这是一个工具类，包含所有可枚举相关的优化规则
  protected static final Logger LOGGER = CalciteTrace.getPlannerTracer();  // 声明静态日志记录器，用于记录查询优化器的日志信息，使用CalciteTrace获取规划器追踪器

  public static final boolean BRIDGE_METHODS = true;  // 声明一个静态布尔常量，表示是否启用桥接方法，桥接方法用于在不同类型的函数调用之间进行转换

  private EnumerableRules() {  // 私有构造方法，防止实例化该类，因为这是一个工具类，所有成员都是静态的
  }  // 构造方法体为空，没有任何操作

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalJoin} to
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则将逻辑连接(LogicalJoin)节点转换为可枚举调用约定的实现
  // 逻辑连接表示SQL中的JOIN操作，可枚举实现会生成Java代码来执行连接
  public static final RelOptRule ENUMERABLE_JOIN_RULE =  // 声明一个静态常量，表示可枚举连接规则，用于将逻辑连接转换为可枚举连接
      EnumerableJoinRule.DEFAULT_CONFIG.toRule(EnumerableJoinRule.class);  // 使用默认配置创建EnumerableJoinRule实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalAsofJoin} to
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则将逻辑ASOF连接(LogicalAsofJoin)节点转换为可枚举调用约定的实现
  // ASOF连接是一种特殊的连接操作，用于匹配时间序列数据中的最近记录
  public static final RelOptRule ENUMERABLE_ASOFJOIN_RULE =  // 声明一个静态常量，表示可枚举ASOF连接规则
      EnumerableAsofJoinRule.DEFAULT_CONFIG.toRule(EnumerableAsofJoinRule.class);  // 使用默认配置创建EnumerableAsofJoinRule实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalJoin} to
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则将逻辑连接(LogicalJoin)节点转换为可枚举合并连接(EnumerableMergeJoin)
  // 合并连接是一种高效的连接算法，要求输入数据已经排序，通过一次遍历完成连接
  public static final RelOptRule ENUMERABLE_MERGE_JOIN_RULE =  // 声明一个静态常量，表示可枚举合并连接规则
      EnumerableMergeJoinRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableMergeJoinRule.class);  // 使用配置创建规则实例

  public static final RelOptRule ENUMERABLE_CORRELATE_RULE =  // 声明一个静态常量，表示可枚举相关规则
      EnumerableCorrelateRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableCorrelateRule.class);  // 使用配置创建规则实例，相关规则用于处理相关子查询

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalJoin} into an
   * {@link org.apache.calcite.adapter.enumerable.EnumerableBatchNestedLoopJoin}. */
  // 注释：该规则将逻辑连接(LogicalJoin)转换为可枚举批处理嵌套循环连接(EnumerableBatchNestedLoopJoin)
  // 批处理嵌套循环连接是嵌套循环连接的优化版本，通过批量处理提高性能
  public static final RelOptRule ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE =  // 声明一个静态常量，表示可枚举批处理嵌套循环连接规则
      EnumerableBatchNestedLoopJoinRule.Config.DEFAULT.toRule();  // 使用默认配置创建规则实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalProject} to an
   * {@link EnumerableProject}. */
  // 注释：该规则将逻辑投影(LogicalProject)节点转换为可枚举投影(EnumerableProject)
  // 投影操作用于选择、重命名或计算列，是SQL中SELECT子句的基础
  public static final EnumerableProjectRule ENUMERABLE_PROJECT_RULE =  // 声明一个静态常量，表示可枚举投影规则
      EnumerableProjectRule.DEFAULT_CONFIG.toRule(EnumerableProjectRule.class);  // 使用默认配置创建规则实例

  public static final EnumerableFilterRule ENUMERABLE_FILTER_RULE =  // 声明一个静态常量，表示可枚举过滤规则
      EnumerableFilterRule.DEFAULT_CONFIG.toRule(EnumerableFilterRule.class);  // 使用默认配置创建规则实例，过滤规则用于处理WHERE条件

  public static final EnumerableCalcRule ENUMERABLE_CALC_RULE =  // 声明一个静态常量，表示可枚举计算规则
      EnumerableCalcRule.DEFAULT_CONFIG.toRule(EnumerableCalcRule.class);  // 使用默认配置创建规则实例，Calc规则可以同时处理投影和过滤

  public static final EnumerableAggregateRule ENUMERABLE_AGGREGATE_RULE =  // 声明一个静态常量，表示可枚举聚合规则
      EnumerableAggregateRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableAggregateRule.class);  // 使用配置创建规则实例，聚合规则用于处理GROUP BY和聚合函数

  /** Rule that converts a {@link org.apache.calcite.rel.core.Sort} to an
   * {@link EnumerableSort}. */
  // 注释：该规则将排序(Sort)节点转换为可枚举排序(EnumerableSort)
  // 排序操作用于ORDER BY子句，对结果集进行排序
  public static final EnumerableSortRule ENUMERABLE_SORT_RULE =  // 声明一个静态常量，表示可枚举排序规则
      EnumerableSortRule.DEFAULT_CONFIG.toRule(EnumerableSortRule.class);  // 使用默认配置创建规则实例

  public static final EnumerableLimitSortRule ENUMERABLE_LIMIT_SORT_RULE =  // 声明一个静态常量，表示可枚举限制排序规则
      EnumerableLimitSortRule.Config.DEFAULT.toRule();  // 使用默认配置创建规则实例，限制排序规则用于处理LIMIT和OFFSET

  public static final EnumerableLimitRule ENUMERABLE_LIMIT_RULE =  // 声明一个静态常量，表示可枚举限制规则
      EnumerableLimitRule.Config.DEFAULT.toRule();  // 使用默认配置创建规则实例，限制规则用于处理LIMIT

  /** Rule that converts a {@link org.apache.calcite.rel.logical.LogicalUnion}
   * to an {@link EnumerableUnion}. */
  // 注释：该规则将逻辑联合(LogicalUnion)节点转换为可枚举联合(EnumerableUnion)
  // 联合操作用于SQL中的UNION，合并多个查询的结果集
  public static final EnumerableUnionRule ENUMERABLE_UNION_RULE =  // 声明一个静态常量，表示可枚举联合规则
      EnumerableUnionRule.DEFAULT_CONFIG.toRule(EnumerableUnionRule.class);  // 使用默认配置创建规则实例

  /** Rule that converts a {@link LogicalRepeatUnion} into an
   * {@link EnumerableRepeatUnion}. */
  // 注释：该规则将逻辑重复联合(LogicalRepeatUnion)转换为可枚举重复联合(EnumerableRepeatUnion)
  // 重复联合用于递归查询，如SQL中的WITH RECURSIVE
  public static final EnumerableRepeatUnionRule ENUMERABLE_REPEAT_UNION_RULE =  // 声明一个静态常量，表示可枚举重复联合规则
      EnumerableRepeatUnionRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableRepeatUnionRule.class);  // 使用配置创建规则实例

  /** Rule to convert a {@link org.apache.calcite.rel.logical.LogicalSort} on top of a
   * {@link org.apache.calcite.rel.logical.LogicalUnion} into a {@link EnumerableMergeUnion}. */
  // 注释：该规则将逻辑联合(LogicalUnion)上的逻辑排序(LogicalSort)转换为可枚举合并联合(EnumerableMergeUnion)
  // 合并联合是一种优化的联合操作，当输入已排序时可以更高效地合并
  public static final EnumerableMergeUnionRule ENUMERABLE_MERGE_UNION_RULE =  // 声明一个静态常量，表示可枚举合并联合规则
      EnumerableMergeUnionRule.Config.DEFAULT_CONFIG.toRule();  // 使用默认配置创建规则实例

  /** Rule that converts a {@link LogicalTableSpool} into an
   * {@link EnumerableTableSpool}. */
  // 注释：该规则将逻辑表缓存(LogicalTableSpool)转换为可枚举表缓存(EnumerableTableSpool)
  // 表缓存用于缓存查询结果，避免重复计算，提高性能
  @Experimental  // 使用@Experimental注解标记该规则为实验性功能，表示API可能在未来版本中改变
  public static final EnumerableTableSpoolRule ENUMERABLE_TABLE_SPOOL_RULE =  // 声明一个静态常量，表示可枚举表缓存规则
      EnumerableTableSpoolRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableTableSpoolRule.class);  // 使用配置创建规则实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalIntersect} to an
   * {@link EnumerableIntersect}. */
  // 注释：该规则将逻辑交集(LogicalIntersect)节点转换为可枚举交集(EnumerableIntersect)
  // 交集操作用于SQL中的INTERSECT，返回多个查询的公共行
  public static final EnumerableIntersectRule ENUMERABLE_INTERSECT_RULE =  // 声明一个静态常量，表示可枚举交集规则
      EnumerableIntersectRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableIntersectRule.class);  // 使用配置创建规则实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalMinus} to an
   * {@link EnumerableMinus}. */
  // 注释：该规则将逻辑差集(LogicalMinus)节点转换为可枚举差集(EnumerableMinus)
  // 差集操作用于SQL中的EXCEPT，返回第一个查询中存在但第二个查询中不存在的行
  public static final EnumerableMinusRule ENUMERABLE_MINUS_RULE =  // 声明一个静态常量，表示可枚举差集规则
      EnumerableMinusRule.DEFAULT_CONFIG.toRule(EnumerableMinusRule.class);  // 使用默认配置创建规则实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalTableModify} to
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则将逻辑表修改(LogicalTableModify)节点转换为可枚举调用约定的实现
  // 表修改操作包括INSERT、UPDATE、DELETE等数据修改操作
  public static final EnumerableTableModifyRule ENUMERABLE_TABLE_MODIFICATION_RULE =  // 声明一个静态常量，表示可枚举表修改规则
      EnumerableTableModifyRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableTableModifyRule.class);  // 使用配置创建规则实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalValues} to
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则将逻辑值(LogicalValues)节点转换为可枚举调用约定的实现
  // 值节点表示SQL中的VALUES子句，用于生成常量数据行
  public static final EnumerableValuesRule ENUMERABLE_VALUES_RULE =  // 声明一个静态常量，表示可枚举值规则
      EnumerableValuesRule.DEFAULT_CONFIG.toRule(EnumerableValuesRule.class);  // 使用默认配置创建规则实例

  /** Rule that converts a {@link org.apache.calcite.rel.logical.LogicalWindow}
   * to an {@link org.apache.calcite.adapter.enumerable.EnumerableWindow}. */
  // 注释：该规则将逻辑窗口(LogicalWindow)节点转换为可枚举窗口(EnumerableWindow)
  // 窗口操作用于SQL中的窗口函数，如ROW_NUMBER、RANK、SUM OVER等
  public static final EnumerableWindowRule ENUMERABLE_WINDOW_RULE =  // 声明一个静态常量，表示可枚举窗口规则
      EnumerableWindowRule.DEFAULT_CONFIG.toRule(EnumerableWindowRule.class);  // 使用默认配置创建规则实例

  /** Rule that converts an {@link org.apache.calcite.rel.core.Collect}
   * to an {@link EnumerableCollect}. */
  // 注释：该规则将收集(Collect)节点转换为可枚举收集(EnumerableCollect)
  // 收集操作用于将多行数据收集到一个数组或集合中
  public static final EnumerableCollectRule ENUMERABLE_COLLECT_RULE =  // 声明一个静态常量，表示可枚举收集规则
      EnumerableCollectRule.DEFAULT_CONFIG.toRule(EnumerableCollectRule.class);  // 使用默认配置创建规则实例

  /** Rule that converts an {@link org.apache.calcite.rel.core.Uncollect}
   * to an {@link EnumerableUncollect}. */
  // 注释：该规则将反收集(Uncollect)节点转换为可枚举反收集(EnumerableUncollect)
  // 反收集操作是收集的逆操作，将数组或集合展开为多行数据
  public static final EnumerableUncollectRule ENUMERABLE_UNCOLLECT_RULE =  // 声明一个静态常量，表示可枚举反收集规则
      EnumerableUncollectRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableUncollectRule.class);  // 使用配置创建规则实例

  public static final EnumerableFilterToCalcRule ENUMERABLE_FILTER_TO_CALC_RULE =  // 声明一个静态常量，表示可枚举过滤到计算规则
      EnumerableFilterToCalcRule.Config.DEFAULT.toRule();  // 使用默认配置创建规则实例，该规则将Filter转换为Calc

  /** Variant of {@link org.apache.calcite.rel.rules.ProjectToCalcRule} for
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则是ProjectToCalcRule在可枚举调用约定下的变体
  // 将投影(Project)节点转换为计算(Calc)节点，Calc节点可以同时处理投影和过滤
  public static final EnumerableProjectToCalcRule ENUMERABLE_PROJECT_TO_CALC_RULE =  // 声明一个静态常量，表示可枚举投影到计算规则
      EnumerableProjectToCalcRule.Config.DEFAULT.toRule();  // 使用默认配置创建规则实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalTableScan} to
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则将逻辑表扫描(LogicalTableScan)节点转换为可枚举调用约定的实现
  // 表扫描是查询的起点，从数据源读取数据
  public static final EnumerableTableScanRule ENUMERABLE_TABLE_SCAN_RULE =  // 声明一个静态常量，表示可枚举表扫描规则
      EnumerableTableScanRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableTableScanRule.class);  // 使用配置创建规则实例

  /** Rule that converts a
   * {@link org.apache.calcite.rel.logical.LogicalTableFunctionScan} to
   * {@link EnumerableConvention enumerable calling convention}. */
  // 注释：该规则将逻辑表函数扫描(LogicalTableFunctionScan)节点转换为可枚举调用约定的实现
  // 表函数扫描用于调用表值函数，返回一个表作为结果
  public static final EnumerableTableFunctionScanRule ENUMERABLE_TABLE_FUNCTION_SCAN_RULE =  // 声明一个静态常量，表示可枚举表函数扫描规则
      EnumerableTableFunctionScanRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableTableFunctionScanRule.class);  // 使用配置创建规则实例

  /** Rule that converts a {@link LogicalMatch} to an
   * {@link EnumerableMatch}. */
  // 注释：该规则将逻辑匹配(LogicalMatch)节点转换为可枚举匹配(EnumerableMatch)
  // 匹配操作用于SQL中的MATCH_RECOGNIZE子句，用于模式匹配
  public static final EnumerableMatchRule ENUMERABLE_MATCH_RULE =  // 声明一个静态常量，表示可枚举匹配规则
      EnumerableMatchRule.DEFAULT_CONFIG.toRule(EnumerableMatchRule.class);  // 使用默认配置创建规则实例

  /** Rule to convert a {@link LogicalAggregate}
   * to an {@link EnumerableSortedAggregate}. */
  // 注释：该规则将逻辑聚合(LogicalAggregate)转换为可枚举排序聚合(EnumerableSortedAggregate)
  // 排序聚合是一种特殊的聚合实现，要求输入数据已排序，可以更高效地执行聚合
  public static final EnumerableSortedAggregateRule ENUMERABLE_SORTED_AGGREGATE_RULE =  // 声明一个静态常量，表示可枚able排序聚合规则
      EnumerableSortedAggregateRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableSortedAggregateRule.class);  // 使用配置创建规则实例

  /** Rule that converts any enumerable relational expression to bindable. */
  // 注释：该规则将任何可枚举关系表达式转换为可绑定(Bindable)表达式
  // 可绑定表达式可以通过解释器执行，适用于无法直接生成Java代码的情况
  public static final EnumerableBindable.EnumerableToBindableConverterRule TO_BINDABLE =  // 声明一个静态常量，表示可枚举到可绑定的转换规则
      EnumerableBindable.EnumerableToBindableConverterRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableBindable.EnumerableToBindableConverterRule.class);  // 使用配置创建规则实例

  /**
   * Rule that converts {@link org.apache.calcite.interpreter.BindableRel}
   * to {@link org.apache.calcite.adapter.enumerable.EnumerableRel} by creating
   * an {@link org.apache.calcite.adapter.enumerable.EnumerableInterpreter}. */
  // 注释：该规则通过创建可枚举解释器(EnumerableInterpreter)，将可绑定关系表达式(BindableRel)转换为可枚举关系表达式(EnumerableRel)
  // 可绑定表达式通过解释器执行，而可枚举表达式生成Java代码执行
  public static final EnumerableInterpreterRule TO_INTERPRETER =  // 声明一个静态常量，表示可绑定到可枚举的解释器规则
      EnumerableInterpreterRule.DEFAULT_CONFIG  // 获取默认配置
          .toRule(EnumerableInterpreterRule.class);  // 使用配置创建规则实例

  public static final List<RelOptRule> ENUMERABLE_RULES =  // 声明一个静态常量列表，包含所有可枚举规则
      ImmutableList.of(  // 使用Guava的ImmutableList创建不可变列表，包含以下所有规则
          EnumerableRules.ENUMERABLE_JOIN_RULE,  // 添加可枚举连接规则
          EnumerableRules.ENUMERABLE_ASOFJOIN_RULE,  // 添加可枚举ASOF连接规则
          EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE,  // 添加可枚举合并连接规则
          EnumerableRules.ENUMERABLE_CORRELATE_RULE,  // 添加可枚举相关规则
          EnumerableRules.ENUMERABLE_PROJECT_RULE,  // 添加可枚举投影规则
          EnumerableRules.ENUMERABLE_FILTER_RULE,  // 添加可枚举过滤规则
          EnumerableRules.ENUMERABLE_CALC_RULE,  // 添加可枚举计算规则
          EnumerableRules.ENUMERABLE_AGGREGATE_RULE,  // 添加可枚举聚合规则
          EnumerableRules.ENUMERABLE_SORT_RULE,  // 添加可枚举排序规则
          EnumerableRules.ENUMERABLE_LIMIT_RULE,  // 添加可枚able限制规则
          EnumerableRules.ENUMERABLE_COLLECT_RULE,  // 添加可枚举收集规则
          EnumerableRules.ENUMERABLE_UNCOLLECT_RULE,  // 添加可枚举反收集规则
          EnumerableRules.ENUMERABLE_MERGE_UNION_RULE,  // 添加可枚举合并联合规则
          EnumerableRules.ENUMERABLE_UNION_RULE,  // 添加可枚举联合规则
          EnumerableRules.ENUMERABLE_REPEAT_UNION_RULE,  // 添加可枚举重复联合规则
          EnumerableRules.ENUMERABLE_TABLE_SPOOL_RULE,  // 添加可枚举表缓存规则
          EnumerableRules.ENUMERABLE_INTERSECT_RULE,  // 添加可枚举交集规则
          EnumerableRules.ENUMERABLE_MINUS_RULE,  // 添加可枚able差集规则
          EnumerableRules.ENUMERABLE_TABLE_MODIFICATION_RULE,  // 添加可枚able表修改规则
          EnumerableRules.ENUMERABLE_VALUES_RULE,  // 添加可枚able值规则
          EnumerableRules.ENUMERABLE_WINDOW_RULE,  // 添加可枚able窗口规则
          EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,  // 添加可枚able表扫描规则
          EnumerableRules.ENUMERABLE_TABLE_FUNCTION_SCAN_RULE,  // 添加可枚able表函数扫描规则
          EnumerableRules.ENUMERABLE_MATCH_RULE);  // 添加可枚able匹配规则

  public static List<RelOptRule> rules() {  // 声明一个公共静态方法，返回可枚举规则列表
    return ENUMERABLE_RULES;  // 返回ENUMERABLE_RULES常量，即所有可枚举规则的列表
  }  // 方法体结束
}  // 类定义结束
