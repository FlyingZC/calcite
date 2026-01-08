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
package org.apache.calcite.adapter.enumerable; // 包声明:位于org.apache.calcite.adapter.enumerable包下,这是Calcite框架中可枚举适配器相关的包

import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类:表示Linq4j中的表达式树节点,用于构建Java代码表达式
import org.apache.calcite.rex.RexNode; // 导入RexNode类:表示关系表达式节点,是Calcite内部用于表示SQL表达式的抽象语法树节点

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解:用于标记可能为null的返回值

import java.util.List; // 导入List接口:Java集合框架的列表接口,用于存储有序的元素集合

/**
 * Information for a call to // 类注释:提供调用信息的上下文接口
 * {@link org.apache.calcite.adapter.enumerable.AggImplementor#implementAdd(AggContext, AggAddContext)}. // 用于AggImplementor接口的implementAdd方法调用,该方法负责实现聚合函数的累加逻辑
 *
 * <p>Typically, the aggregation implementation will use {@link #arguments()} // 通常,聚合函数的实现会使用arguments()方法
 * or {@link #rexArguments()} to update aggregate value. // 或者使用rexArguments()方法来更新聚合值
 */
public interface AggAddContext extends AggResultContext { // 定义接口AggAddContext,继承自AggResultContext接口,表示聚合函数累加操作的上下文信息
  /**
   * Returns {@link org.apache.calcite.rex.RexNode} representation of arguments. // 方法注释:返回参数的RexNode表示形式
   * This can be useful for manual translation of required arguments with // 这对于手动转换具有不同空值策略
   * different {@link NullPolicy}. // 的所需参数非常有用
   *
   * @return {@link org.apache.calcite.rex.RexNode} representation of arguments // 返回参数的RexNode表示形式列表
   */
  List<RexNode> rexArguments(); // 方法声明:返回聚合函数参数的RexNode列表,RexNode是Calcite内部的关系表达式表示

  /**
   * Returns {@link org.apache.calcite.rex.RexNode} representation of the // 方法注释:返回过滤条件的RexNode表示形式
   * filter, or null. // 如果没有过滤条件则返回null
   */
  @Nullable RexNode rexFilterArgument(); // 方法声明:返回聚合函数的过滤条件参数的RexNode,可能为null

  /**
   * Returns Linq4j form of arguments. // 方法注释:返回参数的Linq4j表达式形式
   * The resulting value is equivalent to // 返回值等价于执行以下操作
   * {@code rowTranslator().translateList(rexArguments())}. // 使用行转换器将RexNode列表转换为Linq4j表达式列表
   * This is handy if you need just operate on argument. // 如果你只需要对参数进行操作,这非常方便
   *
   * @return Linq4j form of arguments. // 返回参数的Linq4j表达式列表
   */
  List<Expression> arguments(); // 方法声明:返回聚合函数参数的Linq4j表达式列表,这些表达式可以直接用于生成Java代码

  /**
   * Returns a // 方法注释:返回一个
   * {@link org.apache.calcite.adapter.enumerable.RexToLixTranslator} // RexToLixTranslator转换器实例
   * suitable to transform the arguments. // 该转换器适合用于转换参数
   *
   * @return {@link RexToLixTranslator} suitable to transform the arguments // 返回适合转换参数的RexToLixTranslator实例
   */
  RexToLixTranslator rowTranslator(); // 方法声明:返回RexToLixTranslator对象,用于将RexNode(Relational Expression Node)转换为Linq4j表达式
}