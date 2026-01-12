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
package org.apache.calcite.test; // 声明包名，表示该类属于 org.apache.calcite.test 测试包

import org.apache.calcite.DataContexts; // 导入 DataContexts 类，用于创建数据上下文，提供执行查询的环境
import org.apache.calcite.jdbc.CalciteConnection; // 导入 CalciteConnection 接口，表示 Calcite 的 JDBC 连接，扩展了标准 JDBC 连接功能
import org.apache.calcite.linq4j.tree.Expressions; // 导入 Expressions 工具类，用于构建 LINQ 表达式树，支持函数式编程风格的查询构建
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入 ParameterExpression 类，表示 LINQ 表达式中的参数，用于 lambda 表达式中
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，表示 Calcite 的模式（schema），可以包含表、视图等数据库对象
import org.apache.calcite.schema.Schemas; // 导入 Schemas 工具类，提供模式相关的操作方法，如创建查询对象等
import org.apache.calcite.util.Util; // 导入 Util 工具类，提供各种通用的工具方法

import org.junit.jupiter.api.Test; // 导入 Test 注解，用于标记测试方法，由 JUnit 5 框架识别并执行

import java.sql.Connection; // 导入 Connection 接口，表示标准的 JDBC 数据库连接
import java.sql.SQLException; // 导入 SQLException 类，表示数据库操作过程中可能发生的异常

import static java.util.Objects.requireNonNull; // 静态导入 requireNonNull 方法，用于检查对象引用是否为 null

/**
 * Tests for a linq4j front-end and JDBC back-end. // 类文档注释：该测试类用于测试 LINQ4j 前端和 JDBC 后端的集成
 * // 说明：本类展示了如何使用 Calcite 的 LINQ4j API 来构建查询，并通过 JDBC 后端执行查询
 * // LINQ4j 是 Calcite 提供的 Java 语言集成查询（Language Integrated Query）框架
 * // 允许使用 Java 代码以函数式编程的方式构建和执行数据库查询
 * // JDBC 后端表示查询最终通过 JDBC 协议发送到数据库执行
 */ // 类文档注释结束
class LinqFrontJdbcBackTest { // 定义测试类 LinqFrontJdbcBackTest，测试 LINQ4j 前端与 JDBC 后端的集成
  @Test void testTableWhere() throws SQLException { // 测试方法：测试使用 LINQ4j 查询表并应用 where 过滤条件，可能抛出 SQLException 异常
    final Connection connection = // 声明并初始化 JDBC 连接对象，使用 final 修饰表示不可重新赋值
        CalciteAssert.that(CalciteAssert.Config.JDBC_FOODMART).connect(); // 使用 CalciteAssert 工具类创建并获取到 FoodMart 数据库的 JDBC 连接，JDBC_FOODMART 是预配置的连接配置
    final CalciteConnection calciteConnection = // 声明并初始化 Calcite 连接对象，这是 Calcite 特有的连接接口，提供了额外的功能
        connection.unwrap(CalciteConnection.class); // 将标准 JDBC 连接解包为 CalciteConnection，以访问 Calcite 特定的功能
    final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根模式（root schema），这是 Calcite 中所有数据库对象的容器
    ParameterExpression c = // 声明并创建一个参数表达式，用于表示查询中的参数变量
        Expressions.parameter(JdbcTest.Customer.class, "c"); // 创建一个类型为 JdbcTest.Customer 的参数表达式，参数名为 "c"，用于 lambda 表达式中引用客户对象
    String s = // 声明字符串变量，用于存储查询结果的字符串表示
        Schemas.queryable(DataContexts.of(calciteConnection, rootSchema), // 调用 Schemas.queryable 方法创建可查询对象，传入数据上下文（包含连接和模式）
                requireNonNull(rootSchema.subSchemas().get("foodmart")), // 获取根模式下的 "foodmart" 子模式，requireNonNull 确保子模式不为 null
            JdbcTest.Customer.class, "customer") // 指定要查询的实体类型为 JdbcTest.Customer，表名为 "customer"
            .where( // 应用 where 过滤条件，筛选满足条件的记录
                Expressions.lambda( // 创建 lambda 表达式，用于定义过滤条件
                    Expressions.lessThan( // 创建小于比较表达式
                        Expressions.field(c, "customer_id"), // 访问参数 c 的 customer_id 字段
                        Expressions.constant(5)), // 创建常量表达式，值为 5，表示筛选 customer_id 小于 5 的记录
                    c)) // lambda 表达式的参数列表，包含参数 c
            .toList() // 将查询结果转换为列表，执行查询并获取所有符合条件的记录
            .toString(); // 将结果列表转换为字符串表示，用于验证查询结果
    Util.discard(s); // 调用 Util.discard 方法显式丢弃变量 s，避免编译器警告"未使用的变量"，同时表明 s 的值仅用于测试验证
  } // 测试方法结束
} // 类定义结束
