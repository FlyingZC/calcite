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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于org.apache.calcite.linq4j.tree包，这是Calcite的LINQ4J模块中的表达式树相关包

/**
 * Specifies what kind of jump a {@link GotoStatement} represents. // 该枚举类用于指定GotoStatement（跳转语句）表示的跳转类型
 * GotoExpressionKind是一个枚举类型，定义了在表达式树中表示控制流跳转的各种类型 // 在LINQ4J的表达式树中，跳转语句需要区分不同的类型，如goto、return、break、continue等
 * 这个枚举为GotoStatement提供了类型标识，使得表达式树能够准确表示各种控制流跳转操作 // 每种跳转类型都有对应的前缀字符串，用于代码生成时的输出
 */
public enum GotoExpressionKind { // 定义一个公共枚举类GotoExpressionKind，表示跳转表达式的种类
  /**
   * A GotoExpression that represents a jump to some location. // 表示一个跳转到指定位置的goto表达式
   */
  Goto("goto "), // 枚举常量Goto，表示标准的goto跳转语句，前缀字符串为"goto "（注意后面有空格）

  /**
   * A GotoExpression that represents a return statement. // 表示一个return返回语句
   */
  Return("return"), // 枚举常量Return，表示return语句，用于从方法中返回值或退出方法，前缀字符串为"return"

  /**
   * A GotoExpression that represents a break statement. // 表示一个break中断语句
   */
  Break("break"), // 枚举常量Break，表示break语句，用于跳出循环或switch语句，前缀字符串为"break"

  /**
   * A GotoExpression that represents a continue statement. // 表示一个continue继续语句
   */
  Continue("continue"), // 枚举常量Continue，表示continue语句，用于跳过当前循环迭代并继续下一次迭代，前缀字符串为"continue"

  /**
   * A GotoExpression that evaluates an expression and carries on. // 表示一个按顺序执行表达式的跳转
   */
  Sequence(""); // 枚举常量Sequence，表示顺序执行，不产生实际的跳转，前缀字符串为空字符串""

  final String prefix; // 成员变量：存储该跳转类型对应的前缀字符串，用于代码生成时的输出，final表示该字段不可变

  GotoExpressionKind(String prefix) { // 构造方法：接收一个前缀字符串参数，用于初始化枚举常量
    this.prefix = prefix; // 将传入的前缀字符串参数赋值给成员变量prefix，保存该跳转类型对应的前缀
  }
}
