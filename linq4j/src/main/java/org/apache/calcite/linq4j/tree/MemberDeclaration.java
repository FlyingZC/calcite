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
package org.apache.calcite.linq4j.tree; // 定义包名，该类属于org.apache.calcite.linq4j.tree包

/**
 * Declaration of a member of a class. // 声明一个类的成员
 * MemberDeclaration是一个抽象基类，用于表示Java类中的成员声明，包括字段、方法、构造函数等
 * 它是LINQ4J（Language Integrated Query for Java）表达式树系统的一部分，用于在运行时动态生成和操作Java代码
 * 该类实现了Node接口，表明它是表达式树中的一个节点，可以被访问者模式遍历和转换
 * 具体的成员声明类型（如MethodDeclaration、FieldDeclaration等）都继承自这个抽象类
 */ // 成员声明类，表示Java类中的成员（字段、方法、构造函数等）的声明
public abstract class MemberDeclaration implements Node { // 定义抽象类MemberDeclaration，实现Node接口，使其成为表达式树节点
  @Override public abstract MemberDeclaration accept(Shuttle shuttle); // 抽象方法，接受访问者对象，用于遍历和转换表达式树节点，返回转换后的MemberDeclaration对象
} // 类定义结束
