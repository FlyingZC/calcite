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
package org.apache.calcite.chinook; // 指定这个类所在的包路径，位于 org.apache.calcite.chinook 包下

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.sql.Connection; // 导入 JDBC Connection 接口，用于建立数据库连接
import java.sql.DriverManager; // 导入 DriverManager 类，用于管理数据库驱动和创建连接
import java.sql.PreparedStatement; // 导入 PreparedStatement 接口，用于执行预编译的 SQL 语句
import java.sql.ResultSet; // 导入 ResultSet 接口，用于表示查询结果集

/**
 * Tests against parameters in prepared statement when using underlying JDBC
 * sub-schema.
 *
 * <p>Under JDK 23 and higher, this test requires
 * "{@code -Djava.security.manager=allow}" command-line arguments due to
 * Avatica's use of deprecated methods in {@link javax.security.auth.Subject}.
 * These arguments are set automatically if you run via Gradle.
 */
// RemotePreparedStatementParametersTest 类：这个测试类专门用于测试在使用远程 JDBC 子模式时，PreparedStatement 参数的正确性
// 它验证了通过 Avatica 远程服务器执行带有参数的预编译 SQL 语句时，参数是否能够正确传递和执行
// 该类包含三个测试方法，分别测试单参数、多参数以及原生 JDBC 模式下的参数处理
class RemotePreparedStatementParametersTest { // 测试类定义，使用默认访问修饰符（包级私有）

  @Test void testSimpleStringParameterShouldWorkWithCalcite() throws Exception { // 测试方法：验证在 Calcite 模式下，简单的字符串参数能够正常工作
    // given
    ChinookAvaticaServer server = new ChinookAvaticaServer(); // 创建一个 ChinookAvaticaServer 实例，用于启动远程 Avatica 服务器
    server.startWithCalcite(); // 启动服务器并使用 Calcite 作为底层查询引擎，Calcite 会解析和优化 SQL 查询
    Connection connection = DriverManager.getConnection(server.getURL()); // 通过 DriverManager 获取到远程服务器的 JDBC 连接，使用服务器提供的 URL
    // when
    PreparedStatement pS = // 创建 PreparedStatement 对象，用于执行带有参数的 SQL 查询
        connection.prepareStatement("select * from chinook.artist where name = ?"); // 准备 SQL 语句，从 chinook.artist 表中查询 name 等于参数值的记录，? 是参数占位符
    pS.setString(1, "AC/DC"); // 设置第一个参数（索引从 1 开始）的值为字符串 "AC/DC"，这将替换 SQL 语句中的第一个 ?
    // then
    ResultSet resultSet = pS.executeQuery(); // 执行查询并获取结果集，此时参数值已经被正确传递到服务器端
    server.stop(); // 停止 Avatica 服务器，释放资源
  }

  @Test void testSeveralParametersShouldWorkWithCalcite() throws Exception { // 测试方法：验证在 Calcite 模式下，多个参数能够正常工作
    // given
    ChinookAvaticaServer server = new ChinookAvaticaServer(); // 创建一个新的 ChinookAvaticaServer 实例
    server.startWithCalcite(); // 启动服务器并使用 Calcite 查询引擎
    Connection connection = DriverManager.getConnection(server.getURL()); // 获取到远程服务器的 JDBC 连接
    // when
    PreparedStatement pS = // 创建 PreparedStatement 对象
        connection.prepareStatement( // 准备带有两个参数的 SQL 语句
            "select * from chinook.track where name = ? or milliseconds > ?"); // 从 chinook.track 表中查询，条件是 name 等于第一个参数或 milliseconds 大于第二个参数
    pS.setString(1, "AC/DC"); // 设置第一个参数为字符串 "AC/DC"
    pS.setInt(2, 10); // 设置第二个参数为整数 10，表示毫秒数大于 10 的记录
    // then
    ResultSet resultSet = pS.executeQuery(); // 执行查询，验证多个参数是否能够正确传递和处理
    server.stop(); // 停止服务器
  }

  @Test void testParametersShouldWorkWithRaw() throws Exception { // 测试方法：验证在原生 JDBC 模式下（不使用 Calcite），参数能够正常工作
    // given
    ChinookAvaticaServer server = new ChinookAvaticaServer(); // 创建一个新的 ChinookAvaticaServer 实例
    server.startWithRaw(); // 启动服务器并使用原生 JDBC 模式，直接使用底层数据库而不经过 Calcite 解析
    Connection connection = DriverManager.getConnection(server.getURL()); // 获取到远程服务器的 JDBC 连接
    // when
    PreparedStatement pS = // 创建 PreparedStatement 对象
        connection.prepareStatement("select * from \"Artist\" where \"Name\" = ?"); // 准备 SQL 语句，使用双引号标识表名和列名（原生数据库的标识符引用方式）
    pS.setString(1, "AC/DC"); // 设置第一个参数为 "AC/DC"
    // then
    ResultSet resultSet = pS.executeQuery(); // 执行查询，验证原生 JDBC 模式下的参数处理
    server.stop(); // 停止服务器
  }
} // 类定义结束
