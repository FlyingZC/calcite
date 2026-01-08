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
// Apache Calcite 项目包声明,该文件位于可枚举适配器包下,提供了将关系表达式转换为 LINQ 表达式的实现
package org.apache.calcite.adapter.enumerable;

// 导入 Expression 类,表示 LINQ 表达式树中的表达式节点,是构建表达式树的基础类型
import org.apache.calcite.linq4j.tree.Expression;
// 导入 ParameterExpression 类,表示 LINQ 表达式树中的参数表达式,用于声明方法参数和变量
import org.apache.calcite.linq4j.tree.ParameterExpression;
// 导入 RexCall 类,表示 Calcite 关系表达式中的函数调用节点,包含函数名和操作数
import org.apache.calcite.rex.RexCall;

/** Implementor of Functions used in MATCH_RECOGNIZE Context. */
// MATCH_RECOGNIZE 是 SQL 标准中的模式匹配语法,用于在事件流中识别复杂模式(如股票价格连续上涨3次)
// 这个接口定义了如何在 MATCH_RECOGNIZE 上下文中实现函数调用,将 SQL 模式匹配表达式转换为可执行的 Java 代码
// 它是 Calcite 将 SQL 模式匹配查询转换为可执行代码的关键接口之一
public interface MatchImplementor {

  /**
   * Implements a call.
   *
   * @param translator Translator for the call
   * @param call Call that should be implemented
   * @param row Current Row
   * @param rows All Rows that are traversed so far
   * @param symbols All Symbols of the rows that were traversed so far
   * @return Translated call
   */
  // 核心方法:将关系表达式函数调用转换为 LINQ 表达式,用于在模式匹配过程中执行特定逻辑
  // translator: RexToLixTranslator 对象,负责将关系表达式(RexNode)转换为 LINQ 表达式(Expression)
  //             它维护了类型系统、变量映射等转换所需的上下文信息,是转换过程的核心工具
  // call: RexCall 对象,表示要实现的函数调用节点,包含函数名称和参数列表
  //       例如: PREV(row.price, 1) 或 CLASSIFIER() 等模式匹配专用函数
  // row: ParameterExpression 对象,表示当前正在处理的行变量,用于访问当前行的数据
  //      在模式匹配过程中,每一行数据都会被封装成一个对象,通过这个参数可以访问当前行的字段
  // rows: ParameterExpression 对象,表示到目前为止已经遍历的所有行的集合,通常是 List 类型
  //       用于访问历史行数据,实现如 PREV() 等需要访问历史数据的函数
  // symbols: ParameterExpression 对象,表示已遍历行的符号集合,记录了每行匹配到的模式变量
  //          例如:在模式 "A+ B*" 中,symbols 会记录哪些行匹配了 A,哪些行匹配了 B
  //          这对于实现如 CLASSIFIER() 等需要知道当前行匹配了哪个模式的函数至关重要
  // currentIndex: ParameterExpression 对象,表示当前行的索引位置,用于在行集合中定位当前行
  //               这对于实现 PREV(row, offset) 等需要根据偏移量访问历史行的函数非常重要
  // 返回值: Expression 对象,表示转换后的 LINQ 表达式,可以直接嵌入到生成的 Java 代码中执行
  //         这个表达式会访问传入的参数(row, rows, symbols, currentIndex)来计算函数调用的结果
  Expression implement(
      RexToLixTranslator translator,  // 关系表达式到 LINQ 表达式的转换器,提供类型转换、变量查找等功能
      RexCall call,                    // 要实现的关系表达式函数调用,包含函数名和参数
      ParameterExpression row,         // 当前行的参数表达式,用于访问当前行的字段值
      ParameterExpression rows,        // 所有已遍历行的集合参数表达式,用于访问历史行数据
      ParameterExpression symbols,     // 模式符号集合参数表达式,记录每行匹配的模式变量
      ParameterExpression currentIndex); // 当前行索引的参数表达式,用于定位当前行在集合中的位置

}
