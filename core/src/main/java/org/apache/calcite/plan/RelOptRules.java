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
 */ // Apache许可证声明，规范开源软件的使用和分发
package org.apache.calcite.plan; // 声明该类属于org.apache.calcite.plan包，这是Calcite的计划优化核心包

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入可枚举适配器的规则集合，用于将关系代数转换为可枚举代码
import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类，用于读取系统级配置
import org.apache.calcite.interpreter.Bindables; // 导入解释器绑定工具类，用于解释执行
import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，标记该类为实验性API
import org.apache.calcite.plan.volcano.AbstractConverter; // 导入火山优化器中的抽象转换器规则
import org.apache.calcite.rel.rules.CoreRules; // 导入Calcite核心规则集合，包含最常用的优化规则
import org.apache.calcite.rel.rules.DateRangeRules; // 导入日期范围相关的优化规则
import org.apache.calcite.rel.rules.JoinPushThroughJoinRule; // 导入连接下推规则，用于优化嵌套连接
import org.apache.calcite.rel.rules.PruneEmptyRules; // 导入空结果剪枝规则，用于移除产生空结果的节点
import org.apache.calcite.rel.rules.SingleValuesOptimizationRules; // 导入单值优化规则，用于优化单行结果
import org.apache.calcite.rel.rules.materialize.MaterializedViewRules; // 导入物化视图相关规则，用于物化视图重写

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可修改的规则列表

import java.util.List; // 导入Java标准列表接口

/**
 * A utility class for organizing built-in rules and rule related
 * methods. Currently some rule sets are package private for serving core Calcite.
 * 这是一个工具类，用于组织内置的优化规则和规则相关方法。
 * 目前一些规则集是包私有的，专门服务于Calcite核心功能。
 *
 * @see RelOptRule // 参见关系优化规则基类，所有规则都继承自它
 * @see RelOptUtil // 参见关系优化工具类，提供各种优化相关的实用方法
 */ // 类级别的JavaDoc文档，说明该类的用途和功能
@Experimental // 标记该类为实验性API，表示可能会在未来版本中发生变化
public class RelOptRules { // 声明RelOptRules公共类，这是一个规则组织工具类

  private RelOptRules() { // 私有构造方法，防止实例化，因为这是一个纯工具类
  } // 构造方法体为空，不允许创建该类的实例

  /** Calc rule set; public so that {@link org.apache.calcite.tools.Programs} can
   * use it.
   * Calc规则集；公开的，以便org.apache.calcite.tools.Programs可以使用它。
   * 这个规则集专门用于Calc节点的优化，Calc节点是Calcite中用于合并过滤和投影的通用节点。
   */ // CALC_RULES的JavaDoc注释
  public static final ImmutableList<RelOptRule> CALC_RULES = // 声明公共静态常量CALC_RULES，这是一个不可变的优化规则列表，专门用于Calc节点优化
      ImmutableList.of(Bindables.FROM_NONE_RULE, // FROM_NONE_RULE：处理FROM子句为空的情况
          EnumerableRules.ENUMERABLE_CALC_RULE, // ENUMERABLE_CALC_RULE：将Calc节点转换为可枚举形式
          EnumerableRules.ENUMERABLE_FILTER_TO_CALC_RULE, // ENUMERABLE_FILTER_TO_CALC_RULE：将Filter节点转换为Calc节点
          EnumerableRules.ENUMERABLE_PROJECT_TO_CALC_RULE, // ENUMERABLE_PROJECT_TO_CALC_RULE：将Project节点转换为Calc节点
          CoreRules.FILTER_TO_CALC, // FILTER_TO_CALC：将Filter转换为Calc节点
          CoreRules.PROJECT_TO_CALC, // PROJECT_TO_CALC：将Project转换为Calc节点
          CoreRules.CALC_MERGE, // CALC_MERGE：合并相邻的Calc节点，减少节点数量

          // REVIEW jvs 9-Apr-2006: Do we still need these two?  Doesn't the
          // combination of CalcMergeRule, FilterToCalcRule, and
          // ProjectToCalcRule have the same effect?
          // 审查者jvs 2006年4月9日：我们还需要这两个规则吗？CalcMergeRule、
          // FilterToCalcRule和ProjectToCalcRule的组合难道没有相同的效果吗？
          CoreRules.FILTER_CALC_MERGE, // FILTER_CALC_MERGE：合并Filter和Calc节点
          CoreRules.PROJECT_CALC_MERGE); // PROJECT_CALC_MERGE：合并Project和Calc节点

  static final List<RelOptRule> BASE_RULES = // 声明包级静态常量BASE_RULES，这是基础规则集，包含最常用的优化规则
      ImmutableList.of(CoreRules.AGGREGATE_STAR_TABLE, // AGGREGATE_STAR_TABLE：优化星型模式中的聚合操作
          CoreRules.AGGREGATE_PROJECT_STAR_TABLE, // AGGREGATE_PROJECT_STAR_TABLE：优化带投影的星型模式聚合
          CalciteSystemProperty.COMMUTE.value() // 检查系统属性COMMUTE的值，决定是否启用交换规则
              ? CoreRules.JOIN_ASSOCIATE // 如果COMMUTE为true，使用JOIN_ASSOCIATE规则（连接结合律）
              : CoreRules.PROJECT_MERGE, // 如果COMMUTE为false，使用PROJECT_MERGE规则（合并投影）
          CoreRules.FILTER_SCAN, // FILTER_SCAN：将Filter下推到Scan（表扫描）节点
          CoreRules.PROJECT_FILTER_TRANSPOSE, // PROJECT_FILTER_TRANSPOSE：交换Project和Filter的顺序
          CoreRules.FILTER_PROJECT_TRANSPOSE, // FILTER_PROJECT_TRANSPOSE：交换Filter和Project的顺序
          CoreRules.FILTER_INTO_JOIN, // FILTER_INTO_JOIN：将Filter条件推入Join条件中
          CoreRules.JOIN_PUSH_EXPRESSIONS, // JOIN_PUSH_EXPRESSIONS：将表达式下推到Join中
          CoreRules.AGGREGATE_EXPAND_DISTINCT_AGGREGATES, // AGGREGATE_EXPAND_DISTINCT_AGGREGATES：展开DISTINCT聚合
          CoreRules.AGGREGATE_EXPAND_WITHIN_DISTINCT, // AGGREGATE_EXPAND_WITHIN_DISTINCT：展开WITHIN DISTINCT聚合
          CoreRules.AGGREGATE_CASE_TO_FILTER, // AGGREGATE_CASE_TO_FILTER：将聚合中的CASE表达式转换为Filter
          CoreRules.AGGREGATE_REDUCE_FUNCTIONS, // AGGREGATE_REDUCE_FUNCTIONS：简化聚合函数
          CoreRules.FILTER_AGGREGATE_TRANSPOSE, // FILTER_AGGREGATE_TRANSPOSE：交换Filter和Aggregate的顺序
          CoreRules.PROJECT_WINDOW_TRANSPOSE, // PROJECT_WINDOW_TRANSPOSE：交换Project和Window的顺序
          CoreRules.MATCH, // MATCH：处理模式匹配操作
          CoreRules.JOIN_COMMUTE, // JOIN_COMMUTE：交换Join的左右输入顺序（交换律）
          JoinPushThroughJoinRule.RIGHT, // JoinPushThroughJoinRule.RIGHT：将Join从右侧推过另一个Join
          JoinPushThroughJoinRule.LEFT, // JoinPushThroughJoinRule.LEFT：将Join从左侧推过另一个Join
          CoreRules.SORT_PROJECT_TRANSPOSE, // SORT_PROJECT_TRANSPOSE：交换Sort和Project的顺序
          CoreRules.SORT_JOIN_TRANSPOSE, // SORT_JOIN_TRANSPOSE：交换Sort和Join的顺序
          CoreRules.SORT_REMOVE_CONSTANT_KEYS, // SORT_REMOVE_CONSTANT_KEYS：移除Sort中的常量键
          CoreRules.SORT_UNION_TRANSPOSE, // SORT_UNION_TRANSPOSE：交换Sort和Union的顺序
          CoreRules.EXCHANGE_REMOVE_CONSTANT_KEYS, // EXCHANGE_REMOVE_CONSTANT_KEYS：移除Exchange中的常量键
          CoreRules.SORT_EXCHANGE_REMOVE_CONSTANT_KEYS, // SORT_EXCHANGE_REMOVE_CONSTANT_KEYS：移除Sort-Exchange组合中的常量键
          CoreRules.SAMPLE_TO_FILTER, // SAMPLE_TO_FILTER：将Sample转换为Filter
          CoreRules.FILTER_SAMPLE_TRANSPOSE, // FILTER_SAMPLE_TRANSPOSE：交换Filter和Sample的顺序
          CoreRules.FILTER_WINDOW_TRANSPOSE); // FILTER_WINDOW_TRANSPOSE：交换Filter和Window的顺序

  static final List<RelOptRule> ABSTRACT_RULES = // 声明包级静态常量ABSTRACT_RULES，这是抽象规则集，用于高级优化
      ImmutableList.of(CoreRules.AGGREGATE_ANY_PULL_UP_CONSTANTS, // AGGREGATE_ANY_PULL_UP_CONSTANTS：从任意聚合中提取常量
          CoreRules.UNION_PULL_UP_CONSTANTS, // UNION_PULL_UP_CONSTANTS：从Union中提取常量
          PruneEmptyRules.UNION_INSTANCE, // UNION_INSTANCE：剪枝产生空结果的Union节点
          PruneEmptyRules.INTERSECT_INSTANCE, // INTERSECT_INSTANCE：剪枝产生空结果的Intersect节点
          PruneEmptyRules.MINUS_INSTANCE, // MINUS_INSTANCE：剪枝产生空结果的Minus节点
          PruneEmptyRules.PROJECT_INSTANCE, // PROJECT_INSTANCE：剪枝产生空结果的Project节点
          PruneEmptyRules.FILTER_INSTANCE, // FILTER_INSTANCE：剪枝产生空结果的Filter节点
          PruneEmptyRules.SORT_INSTANCE, // SORT_INSTANCE：剪枝产生空结果的Sort节点
          PruneEmptyRules.AGGREGATE_INSTANCE, // AGGREGATE_INSTANCE：剪枝产生空结果的Aggregate节点
          PruneEmptyRules.JOIN_LEFT_INSTANCE, // JOIN_LEFT_INSTANCE：剪枝左连接产生空结果的情况
          PruneEmptyRules.JOIN_RIGHT_INSTANCE, // JOIN_RIGHT_INSTANCE：剪枝右连接产生空结果的情况
          PruneEmptyRules.SORT_FETCH_ZERO_INSTANCE, // SORT_FETCH_ZERO_INSTANCE：剪枝fetch为0的Sort节点
          PruneEmptyRules.EMPTY_TABLE_INSTANCE, // EMPTY_TABLE_INSTANCE：剪枝空表扫描
          SingleValuesOptimizationRules.JOIN_LEFT_INSTANCE, // JOIN_LEFT_INSTANCE：优化左连接中的单值情况
          SingleValuesOptimizationRules.JOIN_RIGHT_INSTANCE, // JOIN_RIGHT_INSTANCE：优化右连接中的单值情况
          SingleValuesOptimizationRules.JOIN_LEFT_PROJECT_INSTANCE, // JOIN_LEFT_PROJECT_INSTANCE：优化左连接带投影的单值情况
          SingleValuesOptimizationRules.JOIN_RIGHT_PROJECT_INSTANCE, // JOIN_RIGHT_PROJECT_INSTANCE：优化右连接带投影的单值情况
          CoreRules.UNION_MERGE, // UNION_MERGE：合并相邻的Union节点
          CoreRules.INTERSECT_MERGE, // INTERSECT_MERGE：合并相邻的Intersect节点
          CoreRules.MINUS_MERGE, // MINUS_MERGE：合并相邻的Minus节点
          CoreRules.PROJECT_OVER_SUM_TO_SUM0_RULE, // PROJECT_OVER_SUM_TO_SUM0_RULE：将SUM转换为SUM0以处理空集
          CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW, // PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW：分离Project和Window
          CoreRules.FILTER_MERGE, // FILTER_MERGE：合并相邻的Filter节点
          DateRangeRules.FILTER_INSTANCE, // FILTER_INSTANCE：日期范围过滤规则
          CoreRules.INTERSECT_TO_DISTINCT, // INTERSECT_TO_DISTINCT：将Intersect转换为DISTINCT
          CoreRules.MINUS_TO_DISTINCT); // MINUS_TO_DISTINCT：将Minus转换为DISTINCT

  static final List<RelOptRule> ABSTRACT_RELATIONAL_RULES = // 声明包级静态常量ABSTRACT_RELATIONAL_RULES，抽象关系规则集
      ImmutableList.of(CoreRules.FILTER_INTO_JOIN, // FILTER_INTO_JOIN：将Filter推入Join条件
          CoreRules.JOIN_CONDITION_PUSH, // JOIN_CONDITION_PUSH：下推Join条件
          AbstractConverter.ExpandConversionRule.INSTANCE, // ExpandConversionRule：扩展转换规则，用于处理不同Convention之间的转换
          CoreRules.JOIN_COMMUTE, // JOIN_COMMUTE：交换Join的左右输入
          CoreRules.PROJECT_TO_SEMI_JOIN, // PROJECT_TO_SEMI_JOIN：将Project转换为半连接
          CoreRules.JOIN_ON_UNIQUE_TO_SEMI_JOIN, // JOIN_ON_UNIQUE_TO_SEMI_JOIN：将唯一键连接转换为半连接
          CoreRules.JOIN_TO_SEMI_JOIN, // JOIN_TO_SEMI_JOIN：将连接转换为半连接
          CoreRules.AGGREGATE_REMOVE, // AGGREGATE_REMOVE：移除不必要的聚合节点
          CoreRules.UNION_TO_DISTINCT, // UNION_TO_DISTINCT：将Union转换为DISTINCT
          CoreRules.UNION_TO_VALUES, // UNION_TO_VALUES：将Union转换为Values常量
          CoreRules.PROJECT_REMOVE, // PROJECT_REMOVE：移除不必要的投影节点
          CoreRules.PROJECT_AGGREGATE_MERGE, // PROJECT_AGGREGATE_MERGE：合并Project和Aggregate
          CoreRules.AGGREGATE_JOIN_TRANSPOSE, // AGGREGATE_JOIN_TRANSPOSE：交换Aggregate和Join的顺序
          CoreRules.AGGREGATE_MERGE, // AGGREGATE_MERGE：合并相邻的聚合节点
          CoreRules.AGGREGATE_PROJECT_MERGE, // AGGREGATE_PROJECT_MERGE：合并Aggregate和Project
          CoreRules.CALC_REMOVE, // CALC_REMOVE：移除不必要的Calc节点
          CoreRules.SORT_REMOVE); // SORT_REMOVE：移除不必要的排序节点

  static final List<RelOptRule> CONSTANT_REDUCTION_RULES = // 声明包级静态常量CONSTANT_REDUCTION_RULES，常量简化规则集
      ImmutableList.of(CoreRules.PROJECT_REDUCE_EXPRESSIONS, // PROJECT_REDUCE_EXPRESSIONS：简化Project中的表达式
          CoreRules.FILTER_REDUCE_EXPRESSIONS, // FILTER_REDUCE_EXPRESSIONS：简化Filter中的表达式
          CoreRules.CALC_REDUCE_EXPRESSIONS, // CALC_REDUCE_EXPRESSIONS：简化Calc中的表达式
          CoreRules.WINDOW_REDUCE_EXPRESSIONS, // WINDOW_REDUCE_EXPRESSIONS：简化Window中的表达式
          CoreRules.JOIN_REDUCE_EXPRESSIONS, // JOIN_REDUCE_EXPRESSIONS：简化Join中的表达式
          CoreRules.FILTER_VALUES_MERGE, // FILTER_VALUES_MERGE：合并Filter和Values常量
          CoreRules.PROJECT_FILTER_VALUES_MERGE, // PROJECT_FILTER_VALUES_MERGE：合并Project、Filter和Values
          CoreRules.PROJECT_VALUES_MERGE, // PROJECT_VALUES_MERGE：合并Project和Values
          CoreRules.AGGREGATE_VALUES); // AGGREGATE_VALUES：处理聚合中的常量值

  public static final List<RelOptRule> MATERIALIZATION_RULES = // 声明公共静态常量MATERIALIZATION_RULES，物化视图规则集
      ImmutableList.of(MaterializedViewRules.FILTER_SCAN, // FILTER_SCAN：物化视图中的Filter-Scan重写
          MaterializedViewRules.PROJECT_FILTER, // PROJECT_FILTER：物化视图中的Project-Filter重写
          MaterializedViewRules.FILTER, // FILTER：物化视图中的Filter重写
          MaterializedViewRules.PROJECT_JOIN, // PROJECT_JOIN：物化视图中的Project-Join重写
          MaterializedViewRules.JOIN, // JOIN：物化视图中的Join重写
          MaterializedViewRules.PROJECT_AGGREGATE, // PROJECT_AGGREGATE：物化视图中的Project-Aggregate重写
          MaterializedViewRules.AGGREGATE); // AGGREGATE：物化视图中的Aggregate重写
} // 类结束
