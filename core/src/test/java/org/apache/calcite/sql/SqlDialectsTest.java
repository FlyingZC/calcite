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
package org.apache.calcite.sql; // 定义包名，表示此测试类属于 org.apache.calcite.sql 包，用于测试 SQL 方言相关的功能

import org.apache.calcite.jdbc.Driver; // 导入 Calcite 的 JDBC 驱动类，用于创建数据库连接

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.sql.Connection; // 导入 Java SQL 的 Connection 接口，表示数据库连接
import java.sql.DatabaseMetaData; // 导入 Java SQL 的 DatabaseMetaData 接口，用于获取数据库元数据信息
import java.sql.DriverManager; // 导入 Java SQL 的 DriverManager 类，用于管理 JDBC 驱动和创建数据库连接
import java.sql.SQLException; // 导入 Java SQL 的 SQLException 类，用于处理 SQL 相关的异常

import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 断言库的 is 匹配器，用于验证值是否相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 断言库的 assertThat 方法，用于编写断言

/**
 * Tests for {@link SqlDialects}.
 * // 本类是 SqlDialects 的测试类，用于测试 SqlDialects 工具类的功能
 * // SqlDialects 是 Calcite 框架中用于管理和创建 SQL 方言的工具类
 * // SQL 方言是指不同数据库系统（如 MySQL、PostgreSQL、Oracle 等）在 SQL 语法上的差异
 * // SqlDialects 提供了根据数据库元数据创建对应的 SQL 方言上下文的功能
 * // 本测试类主要验证从 Calcite 数据库元数据创建 SqlDialect.Context 的正确性
 * // SqlDialect.Context 是一个构建器类，用于配置和创建 SqlDialect 实例
 * // SqlDialect 实例封装了特定数据库的 SQL 语法规则和特性
 */
public class SqlDialectsTest { // 定义测试类 SqlDialectsTest，用于测试 SqlDialects 工具类的功能
  @Test void testCreateContextFromCalciteMetaData() throws SQLException { // 定义测试方法，验证从 Calcite 元数据创建 SqlDialect.Context 的功能，throws SQLException 表示可能抛出 SQL 异常
    Connection connection = // 声明数据库连接对象 Connection，用于与数据库建立连接
        DriverManager.getConnection(Driver.CONNECT_STRING_PREFIX); // 使用 DriverManager 获取数据库连接，Driver.CONNECT_STRING_PREFIX 是 Calcite 驱动的连接字符串前缀，通常为 "jdbc:calcite:"
    DatabaseMetaData metaData = connection.getMetaData(); // 从数据库连接中获取 DatabaseMetaData 对象，该对象包含了数据库的元数据信息，如数据库名称、版本、支持的 SQL 特性等

    SqlDialect.Context context = SqlDialects.createContext(metaData); // 调用 SqlDialects 静态方法 createContext，根据数据库元数据创建 SqlDialect.Context 对象，该方法会解析元数据并设置上下文的各种属性，如数据库名称、版本、支持的 SQL 特性等
    assertThat(context.databaseProductName(), // 断言验证：检查 SqlDialect.Context 的 databaseProductName() 方法返回的数据库名称是否正确
        is(metaData.getDatabaseProductName())); // 使用 Hamcrest 的 is 匹配器，验证 context.databaseProductName() 的返回值与 metaData.getDatabaseProductName() 的返回值相等
    assertThat(context.databaseMajorVersion(), // 断言验证：检查 SqlDialect.Context 的 databaseMajorVersion() 方法返回的数据库主版本号是否正确
        is(metaData.getDatabaseMajorVersion())); // 使用 Hamcrest 的 is 匹配器，验证 context.databaseMajorVersion() 的返回值与 metaData.getDatabaseMajorVersion() 的返回值相等
  } // 测试方法结束，验证了 SqlDialects.createContext 方法能够正确地从数据库元数据中提取数据库名称和版本信息
} // 类定义结束，SqlDialectsTest 类用于测试 SqlDialects 工具类的功能
