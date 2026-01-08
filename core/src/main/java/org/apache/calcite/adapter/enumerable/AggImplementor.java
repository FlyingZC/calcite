/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明，这是Apache项目标准的开源许可证头部
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，查看随本工作分发的NOTICE文件以获取版权所有权相关信息
 * this work for additional information regarding copyright ownership.  // ASF根据Apache许可证2.0版将此文件授权给您
 * The ASF licenses this file to you under the Apache License, Version 2.0  // 您只能在遵守许可证的情况下使用此文件
 * (the "License"); you may not use this file except in compliance with  // 您可以在以下网址获取许可证副本
 * the License.  You may obtain a copy of the License at  // http://www.apache.org/licenses/LICENSE-2.0
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // 除非适用法律要求或书面同意，否则根据许可证分发的软件是按"原样"分发的
 *
 * Unless required by applicable law or agreed to in writing, software  // 不附带任何明示或暗示的保证或条件
 * distributed under the License is distributed on an "AS IS" BASIS,  // 有关许可证下的权限和限制，请参阅许可证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 有关许可证下的权限和限制，请参阅许可证
 * See the License for the specific language governing permissions and  // 有关许可证下的权限和限制，请参阅许可证
 * limitations under the License.  // 有关许可证下的权限和限制，请参阅许可证
 */
package org.apache.calcite.adapter.enumerable;  // 定义包名，表示该类位于Calcite的可枚举适配器包中，用于实现可枚举的查询执行

import org.apache.calcite.linq4j.tree.Expression;  // 导入Expression类，用于表示LINQ表达式树中的表达式节点

import java.lang.reflect.Type;  // 导入Type类，用于表示Java类型
import java.util.List;  // 导入List接口，用于表示类型列表

/**
 * Implements an aggregate function by generating expressions to  // 聚合函数实现器接口，通过生成表达式来实现聚合函数
 * initialize, add to, and get a result from, an accumulator.  // 这些表达式用于初始化累加器、向累加器添加值以及从累加器获取结果
 *
 * @see org.apache.calcite.adapter.enumerable.StrictAggImplementor  // 参见StrictAggImplementor，严格聚合函数实现器
 * @see org.apache.calcite.adapter.enumerable.StrictWinAggImplementor  // 参见StrictWinAggImplementor，严格窗口聚合函数实现器
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.CountImplementor  // 参见CountImplementor，计数聚合函数实现器
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.SumImplementor  // 参见SumImplementor，求和聚合函数实现器
 */
public interface AggImplementor {  // 定义AggImplementor接口，这是聚合函数实现器的核心接口
  /**
   * Returns the types of the intermediate variables used by the aggregate  // 返回聚合实现所使用的中间变量的类型列表
   * implementation.  // 这些中间变量用于在聚合过程中存储中间状态
   *
   * <p>For instance, for "concatenate to string" this can be  // 例如，对于"字符串连接"聚合，中间变量类型可以是StringBuilder
   * {@link java.lang.StringBuilder}.  // StringBuilder用于高效地构建字符串，避免频繁创建新字符串对象
   * Calcite calls this method before all other {@code implement*} methods.  // Calcite会在调用其他所有implement*方法之前先调用此方法
   *
   * @param info Aggregate context  // 参数info：聚合上下文对象，包含聚合函数的元数据和类型信息
   * @return Types of the intermediate variables used by the aggregate  // 返回值：聚合实现所使用的中间变量的类型列表
   *   implementation  // 这些类型决定了累加器的数据结构
   */
  List<Type> getStateType(AggContext info);  // getStateType方法：获取聚合函数的状态变量类型列表

  /**
   * Implements reset of the intermediate variables to the initial state.  // 实现将中间变量重置为初始状态的操作
   * {@link AggResetContext#accumulator()} should be used to reference  // 应该使用AggResetContext的accumulator()方法来引用状态变量
   * the state variables.  // 这样可以正确地访问和修改累加器中的状态
   * For instance, to zero the count, use the following code:  // 例如，要将计数器归零，可以使用以下代码：
   *
   * <blockquote><code>reset.currentBlock().add(<br>  // reset.currentBlock().add()：向当前代码块添加语句
   *   Expressions.statement(<br>  // Expressions.statement()：将表达式转换为语句
   *     Expressions.assign(reset.accumulator().get(0),<br>  // Expressions.assign()：创建赋值表达式，将累加器的第一个变量赋值为0
   *       Expressions.constant(0)));</code></blockquote>  // Expressions.constant(0)：创建常量表达式，值为0
   *
   * @param info Aggregate context  // 参数info：聚合上下文对象，包含聚合函数的元数据和类型信息
   * @param reset Reset context  // 参数reset：重置上下文对象，提供访问累加器和代码块的方法
   */
  void implementReset(AggContext info, AggResetContext reset);  // implementReset方法：实现聚合状态的重置逻辑

  /**
   * Updates intermediate values to account for the newly added value.  // 更新中间值以计入新添加的值
   * {@link AggResetContext#accumulator()} should be used to reference  // 应该使用AggResetContext的accumulator()方法来引用状态变量
   * the state variables.  // 这样可以正确地访问和修改累加器中的状态
   *
   * @param info Aggregate context  // 参数info：聚合上下文对象，包含聚合函数的元数据和类型信息
   * @param add Add context  // 参数add：添加上下文对象，提供访问输入值和累加器的方法
   */
  void implementAdd(AggContext info, AggAddContext add);  // implementAdd方法：实现将新值添加到聚合状态的逻辑

  /**
   * Calculates the resulting value based on the intermediate variables.  // 基于中间变量计算最终结果值
   * Note: this method must NOT destroy the intermediate variables as  // 注意：此方法绝不能销毁中间变量
   * calcite might reuse the state when calculating sliding aggregates.  // 因为Calcite在计算滑动窗口聚合时可能会重用这些状态
   * {@link AggResetContext#accumulator()} should be used to reference  // 应该使用AggResetContext的accumulator()方法来引用状态变量
   * the state variables.  // 这样可以正确地访问累加器中的状态
   *
   * @param info Aggregate context  // 参数info：聚合上下文对象，包含聚合函数的元数据和类型信息
   * @param result Result context  // 参数result：结果上下文对象，提供访问累加器和生成结果表达式的方法
   * @return Expression that is a result of calculating final value of  // 返回值：一个表达式，表示计算聚合函数最终值的结果
   *   the aggregate being implemented  // 这个表达式会被编译成可执行的代码
   */
  Expression implementResult(AggContext info, AggResultContext result);  // implementResult方法：实现从聚合状态计算最终结果的逻辑
}
