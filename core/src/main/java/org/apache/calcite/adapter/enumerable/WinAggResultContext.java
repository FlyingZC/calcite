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
// Apache许可证声明，表明这是Apache软件基金会的开源项目
package org.apache.calcite.adapter.enumerable; // 定义包名，该类位于org.apache.calcite.adapter.enumerable包下，这是Calcite的可枚举适配器包

import org.apache.calcite.linq4j.tree.Expression; // 导入Linq4j的Expression类，用于表示表达式树中的节点
import org.apache.calcite.rex.RexNode; // 导入RexNode类，用于表示关系表达式节点，是Calcite内部的表达式表示

import java.util.List; // 导入List接口，用于存储有序的元素集合

/**
 * Information for a call to // 此接口提供的信息用于调用
 * {@link AggImplementor#implementResult(AggContext, AggResultContext)}. // 聚合实现器的实现结果方法，该方法负责生成聚合函数的结果计算逻辑
 *
 * <p>Typically, the aggregation implementation will convert // 通常情况下，聚合实现会将
 * {@link #accumulator()} to the resulting value of the aggregation.  The // 累加器转换为聚合函数的最终结果值。实现过程
 * implementation MUST NOT destroy the contents of {@link #accumulator()}. // 绝对不能破坏累加器的内容，因为累加器可能需要被后续操作继续使用
 */
// WinAggResultContext是一个接口，专门为窗口聚合函数提供结果计算上下文信息
// 它继承自两个父接口：AggResultContext（普通聚合结果上下文）和WinAggFrameResultContext（窗口聚合帧结果上下文）
// 这意味着窗口聚合既需要普通聚合的能力，也需要处理窗口帧的特殊能力
// 窗口聚合是指在SQL中使用的OVER子句，如SUM(salary) OVER (PARTITION BY dept ORDER BY hire_date)这样的聚合函数
// 与普通聚合不同，窗口聚合不会将多行数据聚合成一行，而是为每一行计算一个基于窗口的聚合值
public interface WinAggResultContext extends AggResultContext, // 继承AggResultContext接口，获得普通聚合结果上下文的所有能力
    WinAggFrameResultContext { // 继承WinAggFrameResultContext接口，获得窗口帧结果上下文的所有能力，如访问窗口帧的边界、行索引等
  /**
   * Returns {@link org.apache.calcite.rex.RexNode} representation of arguments. // 返回聚合函数参数的RexNode表示形式
   * This can be useful for manual translation of required arguments with // 这在需要手动翻译参数时非常有用，特别是当需要使用
   * different {@link NullPolicy}. // 不同的空值处理策略时
   *
   * @return {@link org.apache.calcite.rex.RexNode} representation of arguments // 返回参数的RexNode列表，每个RexNode代表一个参数的表达式
   */
  // rexArguments方法返回聚合函数参数的RexNode表示
  // RexNode是Calcite的关系表达式节点，是Calcite内部用于表示SQL表达式的抽象语法树节点
  // 例如，对于SUM(salary + bonus)这样的窗口聚合，参数列表中会包含一个表示salary + bonus的RexNode
  // 返回RexNode而不是直接返回Expression的好处是可以在代码生成阶段进行更灵活的转换和优化
  // NullPolicy是Calcite中用于控制如何处理NULL值的策略，如是否跳过NULL值、NULL值是否影响结果等
  // 通过返回RexNode，调用者可以根据需要应用不同的NULL处理策略进行转换
  List<RexNode> rexArguments(); // 方法声明，返回一个RexNode列表，表示窗口聚合函数的所有参数

  /**
   * Returns Linq4j form of arguments. // 返回参数的Linq4j表达式形式
   * The resulting value is equivalent to // 返回的结果等价于
   * {@code rowTranslator().translateList(rexArguments())}. // 调用行转换器对rexArguments()进行转换后的结果
   * This is handy if you need just operate on argument. // 如果你只需要操作参数而不需要关心其他上下文，这个方法非常方便
   *
   * @param rowIndex index of the requested row. The index must be in range // 参数：请求的行索引，该索引必须在分区的startIndex和endIndex范围内
   *                 of partition's startIndex and endIndex. // 即该索引必须指向当前窗口分区内的有效行
   * @return Linq4j form of arguments of the particular row // 返回指定行的参数的Linq4j表达式列表，每个Expression代表该行对应参数的值
   */
  // arguments方法返回指定行的参数的Linq4j表达式形式
  // Linq4j是Calcite使用的Java LINQ（Language Integrated Query）库，Expression是Linq4j中用于表示表达式的类
  // 该方法接受一个rowIndex参数，表示要获取哪一行的参数值
  // 例如，对于窗口函数SUM(salary) OVER (ORDER BY hire_date)，每一行都会有一个salary值
  // 当计算第5行的窗口聚合结果时，可能需要访问第3行的salary值，这时就可以调用arguments(rowIndex)来获取第3行的参数表达式
  // 返回的Expression可以直接用于生成Java代码，实现参数的访问和计算
  // 该方法内部会调用rowTranslator().translateList(rexArguments())来实现从RexNode到Expression的转换
  // rowTranslator是一个行转换器，负责将关系表达式转换为可以在特定行上执行的Linq4j表达式
  List<Expression> arguments(Expression rowIndex); // 方法声明，接受一个行索引参数，返回该行参数的Linq4j表达式列表
}
