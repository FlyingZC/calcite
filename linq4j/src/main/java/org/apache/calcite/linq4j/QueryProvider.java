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
package org.apache.calcite.linq4j; // 定义包名，该类属于 Apache Calcite 的 linq4j 模块，提供类似 LINQ 的查询功能

import org.apache.calcite.linq4j.tree.Expression; // 导入表达式树类，用于表示查询的表达式结构

import java.lang.reflect.Type; // 导入 Type 类，用于表示 Java 类型信息

/**
 * Defines methods to create and execute queries that are described by a
 * {@link Queryable} object.
 * 定义方法来创建和执行由 {@link Queryable} 对象描述的查询
 *
 * <p>Analogous to LINQ's System.Linq.QueryProvider.
 * 类似于 .NET LINQ 中的 System.Linq.QueryProvider 接口
 * 
 * QueryProvider 是 linq4j 框架中的核心接口之一，它负责：
 * 1. 创建 Queryable 对象：基于表达式树创建可查询对象
 * 2. 执行查询：将表达式树转换为实际的查询操作并返回结果
 * 3. 提供查询执行机制：支持返回单个值的查询和返回枚举序列的查询
 * 
 * 该接口是 linq4j 实现延迟查询执行的关键，它将查询的定义（表达式树）与查询的执行分离，
 * 使得查询可以被组合、优化，并在需要时才真正执行
 */
public interface QueryProvider { // 定义 QueryProvider 接口，这是查询提供者的核心接口
  /**
   * Constructs a {@link Queryable} object that can evaluate the query
   * represented by a specified expression tree.
   * 构造一个 {@link Queryable} 对象，该对象可以评估由指定表达式树表示的查询
   *
   * <p>NOTE: The {@link org.apache.calcite.linq4j.Queryable#getExpression()}
   * property of the returned {@link Queryable} object is equal to
   * {@code expression}.
   * 注意：返回的 {@link Queryable} 对象的 {@link org.apache.calcite.linq4j.Queryable#getExpression()}
   * 属性等于 {@code expression}
   *
   * @param expression Expression - 表达式树，表示要执行的查询逻辑，包含查询的所有操作和转换
   * @param rowType Row type - 行类型，使用 Class 表示，指定查询返回的元素类型
   * @param <T> Row type - 泛型类型参数，表示查询结果的元素类型
   *
   * @return Queryable - 返回一个 Queryable 对象，该对象封装了查询表达式并支持延迟执行
   * 
   * 该方法是创建可查询对象的主要方法之一，它将表达式树包装成 Queryable 对象，
   * 使得后续的查询操作可以基于这个表达式树进行组合和转换
   */
  <T> Queryable<T> createQuery(Expression expression, Class<T> rowType); // 创建 Queryable 对象，使用 Class 类型表示行类型

  /**
   * Constructs a {@link Queryable} object that can evaluate the query
   * represented by a specified expression tree. The row type may contain
   * generic information.
   * 构造一个 {@link Queryable} 对象，该对象可以评估由指定表达式树表示的查询。
   * 行类型可能包含泛型信息
   *
   * @param expression Expression - 表达式树，表示要执行的查询逻辑，包含查询的所有操作和转换
   * @param rowType Row type - 行类型，使用 Type 表示，可以包含泛型类型信息，比 Class 类型更灵活
   * @param <T> Row type - 泛型类型参数，表示查询结果的元素类型
   *
   * @return Queryable - 返回一个 Queryable 对象，该对象封装了查询表达式并支持延迟执行
   * 
   * 该方法是 createQuery 的重载版本，使用 Type 而不是 Class 来表示行类型，
   * 这样可以支持泛型类型（如 List<String>、Map<String, Integer> 等）
   * 在某些需要保留泛型类型信息的场景下，这个方法比使用 Class 的版本更有用
   */
  <T> Queryable<T> createQuery(Expression expression, Type rowType); // 创建 Queryable 对象，使用 Type 类型表示行类型，支持泛型

  /**
   * Executes the query represented by a specified expression tree.
   * 执行由指定表达式树表示的查询
   *
   * <p>This method executes queries that return a single value
   * (instead of an enumerable sequence of values). Expression trees that
   * represent queries that return enumerable results are executed when the
   * {@link Queryable} object that contains the expression tree is
   * enumerated.
   * 此方法执行返回单个值的查询（而不是可枚举的值序列）。
   * 表示返回可枚举结果的查询的表达式树在包含该表达式树的 {@link Queryable} 对象被枚举时执行
   *
   * <p>The Queryable standard查询操作符方法 that return singleton
   * results call {@code execute}. They pass it a
   * {@link org.apache.calcite.linq4j.tree.MethodCallExpression}
   * that represents a linq4j query.
   * 返回单例结果的 Queryable 标准查询操作符方法会调用 {@code execute}。
   * 它们传递一个 {@link org.apache.calcite.linq4j.tree.MethodCallExpression}，
   * 该表达式表示一个 linq4j 查询
   * 
   * 该方法用于执行立即返回结果的查询，如 Count()、Sum()、First()、Any() 等聚合操作，
   * 这些操作不返回序列，而是返回单个标量值
   */
  <T> T execute(Expression expression, Class<T> type); // 执行查询并返回单个结果值，使用 Class 类型表示返回类型

  /**
   * Executes the query represented by a specified expression tree.
   * The row type may contain type parameters.
   * 执行由指定表达式树表示的查询。行类型可能包含类型参数（泛型）
   *
   * @param expression Expression - 表达式树，表示要执行的查询逻辑
   * @param type Type - 返回值类型，使用 Type 表示，可以包含泛型类型信息
   * @param <T> - 泛型类型参数，表示查询返回的结果类型
   * @return T - 查询执行的结果，返回单个值
   * 
   * 该方法是 execute 的重载版本，使用 Type 而不是 Class 来表示返回类型，
   * 这样可以支持泛型类型（如 List<String>、Map<String, Integer> 等）
   * 在某些需要保留泛型类型信息的场景下，这个方法比使用 Class 的版本更有用
   */
  <T> T execute(Expression expression, Type type); // 执行查询并返回单个结果值，使用 Type 类型表示返回类型，支持泛型

  /**
   * Executes a queryable, and returns an enumerator over the
   * rows that it yields.
   * 执行一个可查询对象，并返回一个枚举器，用于遍历该查询产生的行
   *
   * @param queryable Queryable - 要执行的可查询对象，包含查询表达式树
   * 
   * @return Enumerator over rows - 返回一个枚举器对象，可以逐个访问查询结果中的行
   * 
   * 该方法用于执行返回序列的查询，与 execute 方法不同，这个方法返回一个 Enumerator 对象，
   * 而不是单个值。Enumerator 支持延迟执行和按需获取数据，是 linq4j 中处理序列查询的核心机制
   * 
   * 当调用 Where()、Select()、OrderBy() 等返回序列的操作时，最终会通过该方法执行查询
   */
  <T> Enumerator<T> executeQuery(Queryable<T> queryable); // 执行查询并返回枚举器，用于遍历查询结果序列
} // QueryProvider 接口定义结束
