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
package org.apache.calcite.test; // 指定当前类属于org.apache.calcite.test包，这是Calcite测试代码的标准包路径

import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，用于创建和管理Calcite数据库连接
import org.apache.calcite.util.Smalls; // 导入Smalls工具类，提供测试用的简单表实现

import com.google.common.collect.ImmutableMultiset; // 导入Google Guava的不可变多重集合，用于验证结果集的精确内容

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法

import java.sql.Connection; // 导入JDBC连接接口，用于数据库连接
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于获取数据库连接
import java.sql.ResultSet; // 导入JDBC结果集接口，表示查询结果
import java.sql.ResultSetMetaData; // 导入结果集元数据接口，用于获取列信息
import java.sql.Statement; // 导入JDBC语句接口，用于执行SQL语句

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest断言库的equalTo匹配器，用于验证相等性
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest断言库的nullValue匹配器，用于验证null值
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类，用于执行断言

/**
 * RelMdColumnOriginsTest类：关系表达式元数据列起源测试类
 * 
 * 【类作用】：
 * 该测试类专门用于测试Calcite中的RelMdColumnOrigins（关系元数据列起源）功能。
 * RelMdColumnOrigins是Calcite元数据系统的重要组成部分，负责追踪关系表达式中每个列的来源。
 * 这对于查询优化、列推导、视图重写等核心功能至关重要。
 *
 * 【核心功能】：
 * 1. 测试聚合操作（特别是GROUPING SETS）的列起源追踪
 * 2. 验证复杂查询中列的原始来源是否被正确识别
 * 3. 确保元数据系统在各种SQL场景下的准确性
 *
 * 【测试背景】：
 * CALCITE-542是一个重要的Issue，主要关注在RelMdColumnOrigins中支持聚合分组集（GROUPING SETS）。
 * GROUPING SETS是SQL标准中的高级特性，允许在单个查询中指定多个分组级别。
 * 例如，GROUP BY ROLLUP(A,B)会生成(A,B)、(A)、()三个分组级别。
 * 
 * 【技术细节】：
 * - 测试使用Calcite内存数据库进行
 * - 验证结果集的精确内容（包括重复行）
 * - 检查结果集元数据的列名、表名和模式名
 * - 使用ImmutableMultiset确保结果的精确匹配（考虑重复次数）
 *
 * 【重要性】：
 * 正确的列起源追踪是以下功能的基础：
 * - 查询重写和优化
 * - 物化视图匹配
 * - 列权限控制
 * - 查询计划验证
 * - 列推导分析
 */
class RelMdColumnOriginsTest {
  /**
   * 测试方法：testQueryWithAggregateGroupingSets
   * 
   * 【方法作用】：
   * 测试带有聚合分组集（GROUPING SETS）的查询中列起源追踪功能。
   * 该方法验证RelMdColumnOrigins元数据提供者能够正确处理包含ROLLUP的聚合操作。
   *
   * 【测试场景】：
   * 1. 创建一个包含简单数据的测试表T1
   * 2. 执行两次使用GROUP BY ROLLUP(A,B)的子查询
   * 3. 使用GROUPING(A)函数计算分组级别标识
   * 4. 将两个子查询的结果进行自连接
   * 5. 验证查询结果的正确性和元数据信息
   *
   * 【GROUPING函数说明】：
   * GROUPING(col)函数返回一个整数，表示指定列在当前分组中是否被聚合：
   * - 返回0：该列参与了当前分组
   * - 返回1：该列在当前分组中被聚合（即该列的值被NULL替代）
   *
   * 【ROLLUP说明】：
   * GROUP BY ROLLUP(A,B)等价于GROUP BY A,B UNION ALL GROUP BY A UNION ALL GROUP BY ()
   * 会生成三个分组级别，每个级别对应不同的GROUPING值组合。
   *
   * 【预期结果】：
   * - 结果1（ID=0; ID=0）：25次，表示两个子查询都在A列参与分组的级别
   * - 结果2（ID=1; ID=1）：1次，表示两个子查询都在A列被聚合的级别
   * 
   * 【元数据验证】：
   * - 验证列名正确
   * - 验证表名为null（因为列来自聚合计算，不是原始表的列）
   * - 验证模式名为null
   *
   * 【技术意义】：
   * 该测试确保Calcite能够正确追踪经过复杂聚合和连接操作后的列起源，
   * 这对于查询优化器决定是否可以使用物化视图、进行列推导等至关重要。
   */
  @Test void testQueryWithAggregateGroupingSets() throws Exception { // 使用@Test注解标记为测试方法，声明可能抛出异常
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建Calcite内存数据库连接，jdbc:calcite:是Calcite的内存数据库URL
    CalciteConnection calciteConnection = // 声明Calcite连接变量，用于访问Calcite特定功能
        connection.unwrap(CalciteConnection.class); // 将标准JDBC连接解包为Calcite连接，获取Calcite特有的API

    calciteConnection.getRootSchema().add("T1", // 获取根Schema并添加名为"T1"的表
        new Smalls.SimpleTable()); // 创建Smalls.SimpleTable实例，这是一个用于测试的简单表实现，包含预定义的测试数据
    Statement statement = calciteConnection.createStatement(); // 创建SQL语句对象，用于执行SQL查询
    ResultSet resultSet = // 声明结果集变量，用于存储查询结果
        statement.executeQuery("SELECT TABLE1.ID, TABLE2.ID FROM " // 执行SQL查询，选择两个子查询的ID列
                + "(SELECT GROUPING(A) AS ID FROM T1 " // 第一个子查询：从T1表选择GROUPING(A)作为ID列
                + "GROUP BY ROLLUP(A,B)) TABLE1 " // 使用ROLLUP(A,B)进行分组，生成多个分组级别，别名为TABLE1
                + "JOIN " // 连接操作
                + "(SELECT GROUPING(A) AS ID FROM T1 " // 第二个子查询：结构与第一个相同，从T1表选择GROUPING(A)作为ID列
                + "GROUP BY ROLLUP(A,B)) TABLE2 " // 使用ROLLUP(A,B)进行分组，别名为TABLE2
                + "ON TABLE1.ID = TABLE2.ID"); // 连接条件：两个子查询的ID值相等

    final String result1 = "ID=0; ID=0"; // 定义第一个预期结果字符串，表示两个ID都是0（A列参与分组）
    final String result2 = "ID=1; ID=1"; // 定义第二个预期结果字符串，表示两个ID都是1（A列被聚合）
    final ImmutableMultiset<String> expectedResult = // 声明不可变多重集合，用于存储预期结果（考虑重复次数）
        ImmutableMultiset.<String>builder() // 创建ImmutableMultiset构建器
            .addCopies(result1, 25) // 添加25个result1副本，表示预期有25行"ID=0; ID=0"的结果
            .add(result2) // 添加1个result2，表示预期有1行"ID=1; ID=1"的结果
            .build(); // 构建不可变多重集合
    assertThat(CalciteAssert.toSet(resultSet), equalTo(expectedResult)); // 使用Hamcrest断言验证实际结果集等于预期结果，toSet方法将结果集转换为多重集合格式

    final ResultSetMetaData resultSetMetaData = resultSet.getMetaData(); // 获取结果集的元数据对象，包含列信息
    assertThat(resultSetMetaData.getColumnName(1), equalTo("ID")); // 断言第一列的列名为"ID"
    assertThat(resultSetMetaData.getTableName(1), nullValue()); // 断言第一列的表名为null，因为ID来自GROUPING函数计算，不是原始表的列
    assertThat(resultSetMetaData.getSchemaName(1), nullValue()); // 断言第一列的模式名为null
    assertThat(resultSetMetaData.getColumnName(2), equalTo("ID")); // 断言第二列的列名为"ID"
    assertThat(resultSetMetaData.getTableName(2), nullValue()); // 断言第二列的表名为null，原因同上
    assertThat(resultSetMetaData.getSchemaName(2), nullValue()); // 断言第二列的模式名为null
    resultSet.close(); // 关闭结果集，释放数据库资源
    statement.close(); // 关闭语句对象，释放数据库资源
    connection.close(); // 关闭数据库连接，释放所有相关资源
  }
}
