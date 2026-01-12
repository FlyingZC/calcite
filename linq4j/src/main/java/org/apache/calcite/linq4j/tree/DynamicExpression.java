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
package org.apache.calcite.linq4j.tree;

/**
 * Represents a dynamic operation. // 表示一个动态操作表达式，用于在LINQ4J表达式树中表示动态绑定的操作
 */ // DynamicExpression是Expression的子类，用于处理那些在编译时无法确定但在运行时可以确定的操作
public class DynamicExpression extends Expression { // 继承自Expression基类，作为表达式树中的一个节点
  public DynamicExpression(Class type) { // 构造方法：创建一个动态表达式实例，type参数指定该表达式的返回类型
    super(ExpressionType.Dynamic, type); // 调用父类Expression的构造方法，传入表达式类型为Dynamic，并指定返回类型
  } // 构造方法结束，DynamicExpression本身不包含额外的成员变量，只依赖父类的type和nodeType字段

  @Override public Expression accept(Shuttle shuttle) { // accept方法：接受一个访问器(Shuttle)来访问当前表达式节点，实现访问者模式
    return shuttle.visit(this); // 调用shuttle的visit方法，传入当前DynamicExpression实例，让shuttle处理这个节点
  } // Shuttle是表达式树的遍历器，可以修改或转换表达式树中的节点

  @Override public <R> R accept(Visitor<R> visitor) { // accept方法：接受一个泛型访问器(Visitor)来访问当前表达式节点，实现访问者模式
    return visitor.visit(this); // 调用visitor的visit方法，传入当前DynamicExpression实例，返回类型R由visitor决定
  } // Visitor是表达式树的访问器，用于遍历和收集表达式树中的信息，不修改树结构

} // DynamicExpression类结束，这是一个简单的表达式节点类，主要用于标记表达式树中的动态操作点
