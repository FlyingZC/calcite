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
// Apache 许可证声明，规定了代码的使用条款和限制
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包，是 LINQ4J 表达式树的一部分

/**
 * Factory for creating table expressions that may be used in generated code
 * for accessing table data.
 */
// 这是一个接口，用于创建表表达式工厂，这些表达式可以在生成的代码中用于访问表数据
// TableExpressionFactory 是一个函数式接口，定义了如何创建用于访问表数据的表达式
// 在 Calcite 的代码生成过程中，需要生成 Java 代码来访问数据源，这个接口提供了创建这种访问表达式的统一方式
// 实现该接口的类可以根据不同的数据源类型（如内存表、JDBC 表、CSV 表等）生成不同的访问表达式
public interface TableExpressionFactory { // 定义一个公共接口 TableExpressionFactory，表表达式工厂接口

  /**
   * Creates {@link Expression} to be used in generated code for accessing table data.
   *
   * @param clazz Class that provides specific methods for accessing table data.
   *
   * @return {@link Expression} instance
   */
  // 创建一个表达式，该表达式将在生成的代码中用于访问表数据
  // 这是一个抽象方法，需要由实现类提供具体的实现
  // 参数 clazz 是一个 Class 对象，该类提供了访问表数据的特定方法
  // 例如，clazz 可能是一个包含 getEnumerator() 方法的类，该方法返回表的数据枚举器
  // 返回值是一个 Expression 对象，这个表达式将被插入到生成的 Java 代码中
  // Expression 是 LINQ4J 表达式树的抽象表示，可以是方法调用、字段访问、构造函数调用等
  // 通过返回的 Expression，生成的代码可以调用相应的方法来获取表数据
  Expression create(Class clazz); // 声明一个抽象方法 create，接收一个 Class 参数，返回一个 Expression 对象
} // 接口定义结束
