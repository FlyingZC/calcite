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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证，允许在特定条件下使用和修改
package org.apache.calcite.jdbc; // 声明该类属于org.apache.calcite.jdbc包，这是Calcite JDBC驱动相关的包

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口，用于创建和管理Java类型系统中的类型
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置接口，用于管理连接的配置信息
import org.apache.calcite.jdbc.CalcitePrepare.Context; // 导入Calcite准备上下文接口，用于SQL语句的准备和执行
import org.apache.calcite.linq4j.QueryProvider; // 导入LINQ4J查询提供者接口，允许执行表达式树作为查询
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示可扩展的Schema，支持动态添加表、函数等对象

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值

import java.sql.Connection; // 导入JDBC标准Connection接口，提供数据库连接的基本功能
import java.sql.SQLException; // 导入SQL异常类，用于处理数据库操作中出现的错误
import java.util.Properties; // 导入Properties类，用于存储键值对形式的连接属性

/**
 * Extension to Calcite's implementation of
 * {@link java.sql.Connection JDBC connection} allows schemas to be defined
 * dynamically.
 * // 类说明：这是Calcite对标准JDBC Connection接口的扩展实现，允许动态定义Schema（数据模式）
 * // Schema是数据库的逻辑结构，包含表、视图、函数等对象，动态定义意味着可以在运行时添加、修改这些对象
 *
 * <p>You can start off with an empty connection (no schemas), define one
 * or two schemas, and start querying them.
 * // 使用场景说明：可以从一个空连接开始（没有任何Schema），然后定义一个或多个Schema，并立即开始查询
 * // 这种设计使得Calcite非常灵活，可以适应各种数据源和数据处理场景
 *
 * <p>Since a {@code CalciteConnection} implements the linq4j
 * {@link QueryProvider} interface, you can use a connection to execute
 * expression trees as queries.
 * // 重要特性：由于CalciteConnection实现了LINQ4J的QueryProvider接口，可以用来执行表达式树作为查询
 * // 这意味着除了SQL查询，还可以通过编程方式构建查询表达式树并执行，提供了更灵活的查询方式
 */
public interface CalciteConnection extends Connection, QueryProvider { // 定义CalciteConnection接口，继承JDBC的Connection和LINQ4J的QueryProvider接口，提供数据库连接和查询功能
  /**
   * Returns the root schema.
   * // 方法说明：返回根Schema对象，根Schema是所有其他Schema的父节点
   *
   * <p>You can define objects (such as relations) in this schema, and
   * also nested schemas.
   * // 详细说明：可以在根Schema中定义对象（如表、视图等关系型对象），也可以定义嵌套的子Schema
   * // SchemaPlus是可扩展的Schema，支持动态添加、删除和修改其中的对象
   * // 根Schema是Schema树的根节点，所有其他Schema都是它的子节点或后代节点
   *
   * @return Root schema
   * // 返回值说明：返回SchemaPlus对象，代表根Schema
   */
  SchemaPlus getRootSchema(); // 获取根Schema的方法声明，返回SchemaPlus对象

  /**
   * Returns the type factory.
   * // 方法说明：返回类型工厂对象
   *
   * @return Type factory
   * // 返回值说明：返回JavaTypeFactory对象，用于创建和管理数据类型
   * // 类型工厂是Calcite类型系统的核心，负责创建类型实例、类型转换、类型推断等操作
   */
  JavaTypeFactory getTypeFactory(); // 获取类型工厂的方法声明，返回JavaTypeFactory对象

  /**
   * Returns an instance of the connection properties.
   * // 方法说明：返回连接属性的实例
   *
   * <p>NOTE: The resulting collection of properties is same collection used
   * by the connection, and is writable, but behavior if you modify the
   * collection is undefined.
   * // 重要提示：返回的Properties集合与连接使用的集合是同一个对象，是可写的，但修改后的行为未定义
   * // 这意味着虽然可以修改属性，但修改后可能不会立即生效，或者产生不可预期的结果
   * // Some implementations might, for example, see
   * a modified property, but only if you set it before you create a
   * statement.
   * // 示例说明：某些实现可能会看到修改后的属性，但只有在创建Statement之前设置才有效
   * // We will remove this method when there are better
   * implementations of stateful connections and configuration.
   * // 未来计划：当有更好的有状态连接和配置实现时，将移除此方法
   *
   * @return properties
   * // 返回值说明：返回Properties对象，包含连接的所有属性配置
   */
  Properties getProperties(); // 获取连接属性的方法声明，返回Properties对象

  // in java.sql.Connection from JDK 1.7, but declare here to allow other JDKs
  // 注释说明：setSchema方法在JDK 1.7的java.sql.Connection接口中定义，但在这里重新声明以支持其他JDK版本
  // 这是为了向后兼容，确保在JDK 1.6等早期版本也能使用这个方法
  @Override void setSchema(String schema) throws SQLException; // 设置当前Schema的方法，参数为Schema名称，可能抛出SQLException异常

  // in java.sql.Connection from JDK 1.7, but declare here to allow other JDKs
  // 注释说明：getSchema方法在JDK 1.7的java.sql.Connection接口中定义，但在这里重新声明以支持其他JDK版本
  // 同样是为了向后兼容性考虑
  @Override @Nullable String getSchema() throws SQLException; // 获取当前Schema的方法，返回Schema名称（可能为null），可能抛出SQLException异常

  CalciteConnectionConfig config(); // 获取连接配置对象的方法，返回CalciteConnectionConfig接口实例，包含连接的所有配置信息

  /** Creates a context for preparing a statement for execution. */
  // 方法说明：创建一个用于准备SQL语句执行的上下文对象
  // 上下文对象包含了执行SQL语句所需的所有信息，如Schema、类型工厂、配置等
  // 这个上下文会被传递给CalcitePrepare，用于SQL语句的解析、验证和优化
  Context createPrepareContext(); // 创建准备上下文的方法声明，返回Context接口对象
} // 接口定义结束
