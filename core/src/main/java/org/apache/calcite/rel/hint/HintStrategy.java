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
// Apache开源协议声明，说明该代码的版权和使用许可
package org.apache.calcite.rel.hint;  // 定义包名，该类属于org.apache.calcite.rel.hint包，处理关系表达式提示功能

import org.apache.calcite.plan.RelOptRule;  // 导入优化规则类，用于定义查询优化规则
import org.apache.calcite.rel.convert.ConverterRule;  // 导入转换规则类，用于定义关系表达式之间的转换规则

import com.google.common.collect.ImmutableSet;  // 导入Google Guava的不可变集合类，用于存储不可变的规则集合

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入空值检查注解，用于标记可能为null的字段

import static java.util.Objects.requireNonNull;  // 导入Objects工具类的requireNonNull方法，用于参数非空检查

/**
 * Represents a hint strategy entry of {@link HintStrategyTable}.  // 表示HintStrategy表中的一个提示策略条目
 *
 * <p>A {@code HintStrategy} defines:  // HintStrategy定义了以下内容：
 *
 * <ul>
 *   <li>{@link HintPredicate}: tests whether a hint should apply to  // HintPredicate：测试提示是否应该应用于关系表达式
 *   a relational expression;</li>
 *   <li>{@link HintOptionChecker}: validates the hint options;</li>  // HintOptionChecker：验证提示选项的有效性
 *   <li>{@code excludedRules}: rules to exclude when a relational expression  // excludedRules：当关系表达式应用优化规则时需要排除的规则
 *   is going to apply a planner rule;</li>
 *   <li>{@code converterRules}: fallback rules to apply when there are  // converterRules：当排除excludedRules后没有合适的实现时应用的备用规则
 *   no proper implementations after excluding the {@code excludedRules}.</li>
 * </ul>
 *
 * <p>The {@link HintPredicate} is required, all the other items are optional.  // HintPredicate是必需的，其他项都是可选的
 *
 * <p>{@link HintStrategy} is immutable.  // HintStrategy是不可变的对象
 */
public class HintStrategy {  // 定义HintStrategy类，表示查询提示的策略定义
  //~ Instance fields --------------------------------------------------------  // 实例字段分隔符（IDE格式化标记）

  public final HintPredicate predicate;  // 提示谓词：用于判断提示是否应该应用于特定的关系表达式（必需字段）
  public final @Nullable HintOptionChecker hintOptionChecker;  // 提示选项检查器：用于验证提示选项的有效性（可选字段，可能为null）
  public final ImmutableSet<RelOptRule> excludedRules;  // 排除规则集合：在优化过程中需要排除的规则列表（不可变集合）
  public final ImmutableSet<ConverterRule> converterRules;  // 转换规则集合：当排除规则后可用的备用转换规则列表（不可变集合）

  //~ Constructors -----------------------------------------------------------  // 构造方法分隔符（IDE格式化标记）

  private HintStrategy(  // 私有构造方法，确保只能通过Builder创建实例
      HintPredicate predicate,  // 参数：提示谓词，用于判断提示适用性
      @Nullable HintOptionChecker hintOptionChecker,  // 参数：提示选项检查器，用于验证提示选项
      ImmutableSet<RelOptRule> excludedRules,  // 参数：排除的规则集合
      ImmutableSet<ConverterRule> converterRules) {  // 参数：转换规则集合
    this.predicate = predicate;  // 初始化提示谓词字段
    this.hintOptionChecker = hintOptionChecker;  // 初始化提示选项检查器字段
    this.excludedRules = excludedRules;  // 初始化排除规则集合字段
    this.converterRules = converterRules;  // 初始化转换规则集合字段
  }

  /**
   * Returns a {@link HintStrategy} builder with given hint predicate.  // 返回一个带有给定提示谓词的HintStrategy构建器
   *
   * @param hintPredicate hint predicate  // 参数：提示谓词，用于判断提示是否适用
   * @return {@link Builder} instance  // 返回值：Builder实例，用于构建HintStrategy对象
   */
  public static Builder builder(HintPredicate hintPredicate) {  // 静态工厂方法，创建Builder实例
    return new Builder(hintPredicate);  // 返回新的Builder对象，传入必需的提示谓词
  }

  //~ Inner Class ------------------------------------------------------------  // 内部类分隔符（IDE格式化标记）

  /** Builder for {@link HintStrategy}. */  // HintStrategy的构建器类，用于构建不可变的HintStrategy对象
  public static class Builder {  // 定义静态内部类Builder，使用建造者模式创建HintStrategy实例
    private final HintPredicate predicate;  // 提示谓词：必需字段，用于判断提示适用性（final修饰，构造后不可变）
    private @Nullable HintOptionChecker optionChecker;  // 提示选项检查器：可选字段，用于验证提示选项（可能为null）
    private ImmutableSet<RelOptRule> excludedRules;  // 排除规则集合：存储需要排除的优化规则
    private ImmutableSet<ConverterRule> converterRules;  // 转换规则集合：存储可用的转换规则

    private Builder(HintPredicate predicate) {  // Builder的私有构造方法
      this.predicate = requireNonNull(predicate, "predicate");  // 初始化提示谓词，确保非空，否则抛出NullPointerException
      this.excludedRules = ImmutableSet.of();  // 初始化排除规则集合为空集合
      this.converterRules = ImmutableSet.of();  // 初始化转换规则集合为空集合
    }

    /** Registers a hint option checker to validate the hint options. */  // 注册提示选项检查器，用于验证提示选项的有效性
    public Builder optionChecker(HintOptionChecker optionChecker) {  // 设置提示选项检查器的方法
      this.optionChecker = requireNonNull(optionChecker, "optionChecker");  // 设置选项检查器，确保非空，否则抛出NullPointerException
      return this;  // 返回Builder实例，支持链式调用
    }

    /**
     * Registers an array of rules to exclude during the  // 注册在优化过程中需要排除的规则数组
     * {@link org.apache.calcite.plan.RelOptPlanner} planning.  // 在RelOptPlanner规划期间排除这些规则
     *
     * <p>The desired converter rules work together with the excluded rules.  // 期望的转换规则与排除规则协同工作
     * We have no validation here but they expect to have the same  // 这里没有验证，但它们应该具有相同的功能（语义等价）
     * function(semantic equivalent).
     *
     * <p>A rule fire cancels if:  // 规则触发会在以下情况下取消：
     *
     * <ol>
     *   <li>The registered {@link #excludedRules} contains the rule</li>  // 1. 注册的excludedRules包含该规则
     *   <li>And the desired converter rules conversion is not possible  // 2. 并且期望的转换规则无法对该规则匹配的根节点进行转换
     *   for the rule matched root node</li>
     * </ol>
     *
     * @param rules excluded rules  // 参数：需要排除的规则数组
     */
    public Builder excludedRules(RelOptRule... rules) {  // 设置排除规则的方法，使用可变参数
      this.excludedRules = ImmutableSet.copyOf(rules);  // 将规则数组转换为不可变集合并赋值
      return this;  // 返回Builder实例，支持链式调用
    }

    /**
     * Registers an array of desired converter rules during the  // 注册在优化过程中期望使用的转换规则数组
     * {@link org.apache.calcite.plan.RelOptPlanner} planning.  // 在RelOptPlanner规划期间使用这些转换规则
     *
     * <p>The desired converter rules work together with the excluded rules.  // 期望的转换规则与排除规则协同工作
     * We have no validation here but they expect to have the same  // 这里没有验证，但它们应该具有相同的功能（语义等价）
     * function(semantic equivalent).
     *
     * <p>A rule fire cancels if:  // 规则触发会在以下情况下取消：
     *
     * <ol>
     *   <li>The registered {@link #excludedRules} contains the rule</li>  // 1. 注册的excludedRules包含该规则
     *   <li>And the desired converter rules conversion is not possible  // 2. 并且期望的转换规则无法对该规则匹配的根节点进行转换
     *   for the rule matched root node</li>
     * </ol>
     *
     * <p>If no converter rules are specified, we assume the conversion is possible.  // 如果没有指定转换规则，则假设转换是可能的
     *
     * @param rules desired converter rules  // 参数：期望的转换规则数组
     */
    public Builder converterRules(ConverterRule... rules) {  // 设置转换规则的方法，使用可变参数
      this.converterRules = ImmutableSet.copyOf(rules);  // 将转换规则数组转换为不可变集合并赋值
      return this;  // 返回Builder实例，支持链式调用
    }

    public HintStrategy build() {  // 构建HintStrategy实例的方法
      return new HintStrategy(predicate, optionChecker, excludedRules, converterRules);  // 使用Builder中配置的所有字段创建新的HintStrategy实例
    }
  }
}  // 类定义结束
