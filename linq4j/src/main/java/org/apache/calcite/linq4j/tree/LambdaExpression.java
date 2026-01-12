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
package org.apache.calcite.linq4j.tree; // 定义包路径，该类属于org.apache.calcite.linq4j.tree包，是Calcite LINQ4J框架中表达式树的一部分

/**
 * Describes a lambda expression. This captures a block of code that is similar
 * to a Java method body.
 * // LambdaExpression类：表示lambda表达式，捕获一个类似于Java方法体的代码块
 * // 这个类是Calcite LINQ4J框架中用于表示lambda表达式的抽象语法树节点
 * // lambda表达式是函数式编程的核心概念，允许将代码作为数据传递和操作
 * // 在Calcite中，lambda表达式广泛用于查询转换、表达式求值和代码生成等场景
 * // 例如：在Enumerable查询中，lambda表达式用于定义过滤、投影、排序等操作
 * // 该类继承自Expression基类，是表达式树体系中的一个重要节点类型
 */
public class LambdaExpression extends Expression { // LambdaExpression类继承自Expression，表示lambda表达式节点
  public LambdaExpression(ExpressionType nodeType, Class type) { // 构造方法：创建一个LambdaExpression实例，参数nodeType表示表达式节点类型，type表示表达式返回类型
    super(nodeType, type); // 调用父类Expression的构造方法，初始化节点类型和返回类型
  } // 构造方法结束

  @Override public Expression accept(Shuttle shuttle) { // accept方法：接受访问者模式中的Shuttle访问者，用于遍历和转换表达式树
    return shuttle.visit(this); // 调用Shuttle的visit方法访问当前LambdaExpression节点，返回转换后的表达式
  } // accept方法结束

  @Override public <R> R accept(Visitor<R> visitor) { // accept方法：接受访问者模式中的Visitor访问者，用于遍历和访问表达式树，泛型R表示访问方法的返回类型
    return visitor.visit(this); // 调用Visitor的visit方法访问当前LambdaExpression节点，返回访问结果
  } // accept方法结束

} // LambdaExpression类定义结束
