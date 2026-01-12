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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于org.apache.calcite.linq4j.tree包，是LINQ4J表达式树的一部分

/**
 * Represents an expression that applies a delegate or lambda expression to a
 * list of argument expressions.
 * // 表示一个表达式，该表达式将委托或lambda表达式应用于参数表达式列表
 * // InvocationExpression是表达式树中的一个节点类型，用于表示方法调用或委托调用
 * // 它是LINQ(Language Integrated Query)4J框架中表达式树的抽象表示，用于构建和操作表达式
 * // 这个类继承自Expression基类，是表达式树体系结构中的重要组成部分
 * // 在Calcite的代码生成过程中，用于表示各种方法调用操作，包括静态方法调用、实例方法调用等
 */
public class InvocationExpression extends Expression { // 定义InvocationExpression类，继承自Expression基类，表示一个调用表达式
  // 成员变量：该类没有定义额外的成员变量，所有成员变量都继承自父类Expression
  // Expression基类包含的主要成员变量有：
  // - nodeType: ExpressionType类型，表示表达式的节点类型（如Add、Subtract、Call等）
  // - type: Class类型，表示该表达式计算结果的类型

  public InvocationExpression(ExpressionType nodeType, Class type) { // 构造方法，用于创建InvocationExpression实例
    super(nodeType, type); // 调用父类Expression的构造方法，初始化表达式节点类型和结果类型
  } // 构造方法结束，nodeType参数指定表达式类型，type参数指定表达式的返回类型

  @Override public Expression accept(Shuttle shuttle) { // 接受访问者模式中的Shuttle访问器，用于遍历和转换表达式树
    return shuttle.visit(this); // 将当前InvocationExpression对象传递给Shuttle的visit方法，返回转换后的表达式
  } // accept方法结束，Shuttle是一个表达式树的访问器，可以遍历整个表达式树并进行转换

  @Override public <R> R accept(Visitor<R> visitor) { // 接受访问者模式中的Visitor访问器，用于遍历和分析表达式树，R是返回类型
    return visitor.visit(this); // 将当前InvocationExpression对象传递给Visitor的visit方法，返回访问结果
  } // accept方法结束，Visitor是一个表达式树的访问器，用于分析表达式树并返回特定类型的结果

} // 类定义结束，InvocationExpression类用于表示方法调用表达式，通过访问者模式支持表达式树的遍历和转换