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
package org.apache.calcite.adapter.elasticsearch; // 定义包名，表示该类属于Elasticsearch适配器包

import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，用于获取Calcite连接对象
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite的模式对象
import org.apache.calcite.schema.impl.ViewTable; // 导入ViewTable类，用于创建视图表
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert类，用于测试断言

import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson的ObjectNode类，用于处理JSON对象节点
import com.google.common.collect.ImmutableMap; // 导入Google Guava的ImmutableMap类，用于创建不可变Map

import org.junit.jupiter.api.BeforeAll; // 导入JUnit5的BeforeAll注解，表示在所有测试方法执行前执行一次
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，表示测试方法
import org.junit.jupiter.api.parallel.ResourceAccessMode; // 导入JUnit5的ResourceAccessMode枚举，定义资源访问模式
import org.junit.jupiter.api.parallel.ResourceLock; // 导入JUnit5的ResourceLock注解，用于控制并行测试的资源访问

import java.sql.Connection; // 导入JDBC的Connection接口，表示数据库连接
import java.sql.DriverManager; // 导入JDBC的DriverManager类，用于管理数据库驱动
import java.sql.SQLException; // 导入JDBC的SQLException类，表示SQL异常
import java.util.Arrays; // 导入Java的Arrays工具类，用于数组操作
import java.util.Collections; // 导入Java的Collections工具类，用于集合操作
import java.util.Locale; // 导入Java的Locale类，用于本地化处理
import java.util.Map; // 导入Java的Map接口，表示键值对映射

/**
 * Checks renaming of fields (also upper, lower cases) during projections.
 * 检查投影操作期间字段重命名（包括大小写转换）的功能
 * 该测试类专门用于验证Elasticsearch适配器在处理字段投影时如何处理字段名称的转换
 * 包括大小写转换、字段重命名等场景，确保查询结果的字段名符合预期
 */
@ResourceLock(value = "elasticsearch-scrolls", mode = ResourceAccessMode.READ) // 使用资源锁注解，确保Elasticsearch滚动资源以只读模式访问，避免并发测试冲突
class ProjectionTest { // 定义ProjectionTest测试类，用于测试Elasticsearch适配器的投影功能

  public static final EmbeddedElasticsearchPolicy NODE = EmbeddedElasticsearchPolicy.create(); // 创建并初始化嵌入式Elasticsearch策略对象，用于管理测试用的Elasticsearch实例，该对象是静态常量，在整个测试期间共享

  private static final String NAME = "projectiontest"; // 定义测试索引名称为"projectiontest"，用于在Elasticsearch中创建和操作的索引名称

  @BeforeAll // JUnit5注解，表示该方法在所有测试方法执行前只执行一次，用于初始化测试环境
  public static void setupInstance() throws Exception { // 定义静态初始化方法，用于设置测试实例，抛出Exception表示可能抛出任何异常
    final Map<String, String> mappings = // 创建字段映射Map，定义Elasticsearch索引的字段类型
        ImmutableMap.of("A", "keyword", // 使用ImmutableMap创建不可变映射，定义字段"A"为keyword类型（精确匹配字符串）
            "b", "keyword", "cCC", "keyword", "DDd", "keyword"); // 定义字段"b"、"cCC"、"DDd"都为keyword类型，测试不同大小写混合的字段名

    NODE.createIndex(NAME, mappings); // 调用Elasticsearch策略对象的createIndex方法，创建名为"projectiontest"的索引，并应用字段映射配置

    String doc = "{'A': 'aa', 'b': 'bb', 'cCC': 'cc', 'DDd': 'dd'}".replace('\'', '"'); // 创建测试文档JSON字符串，包含四个字段，使用单引号后替换为双引号以符合JSON格式
    NODE.insertDocument(NAME, (ObjectNode) NODE.mapper().readTree(doc)); // 将JSON文档解析为ObjectNode对象，并插入到Elasticsearch的"projectiontest"索引中，用于后续测试查询
  }

  private static Connection createConnection() throws SQLException { // 定义私有静态方法，用于创建Calcite数据库连接，抛出SQLException表示可能抛出SQL异常
    final Connection connection = // 声明JDBC连接对象
        DriverManager.getConnection("jdbc:calcite:"); // 使用DriverManager获取Calcite JDBC连接，连接字符串"jdbc:calcite:"表示连接到内存中的Calcite实例
    final SchemaPlus root = // 声明Calcite根Schema对象
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为CalciteConnection类型，并获取根Schema对象，用于添加表和视图

    root.add("elastic", // 在根Schema中添加名为"elastic"的子Schema
        new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), NAME)); // 创建ElasticsearchSchema对象，传入Elasticsearch REST客户端、JSON映射器和索引名称，实现与Elasticsearch的集成

    // add calcite view programmatically
    // 以编程方式添加Calcite视图
    final String viewSql = // 定义视图SQL字符串
        String.format(Locale.ROOT, "select cast(_MAP['A'] AS varchar(2)) AS a," // 使用Locale.ROOT确保格式化不受本地化影响，从_MAP中提取字段'A'并转换为varchar(2)类型，重命名为小写'a'
            + " cast(_MAP['b'] AS varchar(2)) AS b, " // 从_MAP中提取字段'b'并转换为varchar(2)类型，保持小写'b'
            + " cast(_MAP['cCC'] AS varchar(2)) AS c, " // 从_MAP中提取字段'cCC'并转换为varchar(2)类型，重命名为小写'c'
            + " cast(_MAP['DDd'] AS varchar(2)) AS d " // 从_MAP中提取字段'DDd'并转换为varchar(2)类型，重命名为小写'd'
            + " from \"elastic\".\"%s\"", NAME); // 从"elastic" schema的指定索引名称的表中选择数据

    root.add("VIEW", // 在根Schema中添加名为"VIEW"的视图
        ViewTable.viewMacro(root, viewSql, // 使用ViewTable.viewMacro方法创建视图宏，传入根Schema、视图SQL
            Collections.singletonList("elastic"), // 指定视图依赖的schema路径列表，这里只依赖"elastic" schema
            Arrays.asList("elastic", "view"), false)); // 指定视图的完整路径为["elastic", "view"]，false表示不启用流式处理

    return connection; // 返回创建的连接对象，供测试方法使用
  }

  @Test void projection() { // 定义测试方法，使用@Test注解标记，测试投影功能
    CalciteAssert.that() // 创建CalciteAssert断言构建器，用于构建测试断言
        .with(ProjectionTest::createConnection) // 使用ProjectionTest类的createConnection方法创建连接，为测试提供数据源
        .query("select * from view") // 执行SQL查询，从view视图中选择所有字段
        .returns("A=aa; B=bb; C=cc; D=dd\n"); // 验证查询结果，期望返回四列数据，字段名被转换为大写，值分别为aa、bb、cc、dd

    CalciteAssert.that() // 创建第二个断言，测试指定字段的投影
        .with(ProjectionTest::createConnection) // 使用相同的连接创建方法
        .query("select a, b, c, d from view") // 执行SQL查询，从view视图中选择a、b、c、d四个字段（小写）
        .returns("A=aa; B=bb; C=cc; D=dd\n"); // 验证查询结果，字段名被转换为大写，保持原始顺序

    CalciteAssert.that() // 创建第三个断言，测试字段顺序颠倒的投影
        .with(ProjectionTest::createConnection) // 使用相同的连接创建方法
        .query("select d, c, b, a from view") // 执行SQL查询，从view视图中选择d、c、b、a四个字段（逆序）
        .returns("D=dd; C=cc; B=bb; A=aa\n"); // 验证查询结果，字段名被转换为大写，顺序与查询顺序一致

    CalciteAssert.that() // 创建第四个断言，测试单个字段的投影
        .with(ProjectionTest::createConnection) // 使用相同的连接创建方法
        .query("select a from view") // 执行SQL查询，从view视图中选择单个字段a
        .returns("A=aa\n"); // 验证查询结果，只返回一列数据，字段名被转换为大写

    CalciteAssert.that() // 创建第五个断言，测试两个字段的投影
        .with(ProjectionTest::createConnection) // 使用相同的连接创建方法
        .query("select a, b from view") // 执行SQL查询，从view视图中选择a、b两个字段
        .returns("A=aa; B=bb\n"); // 验证查询结果，返回两列数据，字段名被转换为大写

    CalciteAssert.that() // 创建第六个断言，测试两个字段顺序颠倒的投影
        .with(ProjectionTest::createConnection) // 使用相同的连接创建方法
        .query("select b, a from view") // 执行SQL查询，从view视图中选择b、a两个字段（逆序）
        .returns("B=bb; A=aa\n"); // 验证查询结果，返回两列数据，字段名被转换为大写，顺序与查询顺序一致

  }

} // 类定义结束
