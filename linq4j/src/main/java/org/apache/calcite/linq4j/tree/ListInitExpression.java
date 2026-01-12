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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于org.apache.calcite.linq4j.tree包，是Calcite LINQ4J表达式树的一部分

/**
 * Represents a constructor call that has a collection initializer. // 表示带有集合初始化器的构造函数调用表达式
 * ListInitExpression用于表示在创建集合对象时同时初始化集合元素的表达式，例如：new ArrayList<String>(){{add("a");add("b");}} // 这是Java中的双括号初始化语法，ListInitExpression就是用来表示这种初始化方式的抽象语法树节点
 * 它继承自Expression基类，是Calcite表达式树系统中的一个重要节点类型，用于在代码生成和转换过程中表示集合的初始化操作 // Expression是所有表达式节点的基类，提供了表达式树的通用功能
 * 在LINQ4J中，表达式树用于表示代码结构，可以进行分析、转换和执行，ListInitExpression专门处理集合初始化的场景 // LINQ4J是Calcite中的语言集成查询框架，表达式树是其核心抽象
 */ // 类注释结束
public class ListInitExpression extends Expression { // 定义ListInitExpression类，继承自Expression，表示集合初始化表达式节点
  public ListInitExpression(ExpressionType nodeType, Class type) { // 构造方法，初始化ListInitExpression实例，nodeType表示表达式类型，type表示集合的类型
    super(nodeType, type); // 调用父类Expression的构造方法，传入节点类型和类型信息，完成基类的初始化
  } // 构造方法结束

  @Override public Expression accept(Shuttle shuttle) { // 重写accept方法，接受Shuttle访问者对象，Shuttle是表达式树的访问者模式实现，用于遍历和转换表达式树
    return shuttle.visit(this); // 调用Shuttle的visit方法访问当前ListInitExpression节点，返回转换后的表达式，Shuttle可以根据需要修改或替换表达式节点
  } // accept方法结束，实现了访问者模式，允许外部代码遍历和操作表达式树

  @Override public <R> R accept(Visitor<R> visitor) { // 重写accept方法，接受泛型Visitor访问者对象，Visitor是另一个访问者接口，可以返回自定义类型R的结果
    return visitor.visit(this); // 调用Visitor的visit方法访问当前ListInitExpression节点，返回类型为R的结果，Visitor用于分析表达式树并提取信息
  } // accept方法结束，提供了另一个访问者接口，支持不同类型的访问操作

} // 类定义结束，ListInitExpression类完整实现
