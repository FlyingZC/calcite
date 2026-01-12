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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，该类属于Elasticsearch适配器测试包

import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，用于获取Calcite特定的连接功能
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，表示Calcite中的模式对象
import org.apache.calcite.schema.impl.ViewTable; // 导入ViewTable类，用于创建视图表
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert测试工具类，用于断言SQL查询结果

import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson的ObjectNode类，用于处理JSON对象
import com.google.common.collect.ImmutableMap; // 导入Guava的ImmutableMap类，用于创建不可变Map

import org.junit.jupiter.api.BeforeAll; // 导入JUnit5的BeforeAll注解，标记在所有测试方法执行前运行的方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，标记测试方法
import org.junit.jupiter.api.parallel.ResourceAccessMode; // 导入并行测试的资源访问模式枚举
import org.junit.jupiter.api.parallel.ResourceLock; // 导入并行测试的资源锁注解

import java.sql.Connection; // 导入JDBC的Connection接口，表示数据库连接
import java.sql.DriverManager; // 导入JDBC的DriverManager类，用于管理数据库驱动程序
import java.sql.SQLException; // 导入JDBC的SQLException类，表示数据库异常
import java.util.Arrays; // 导入Java的Arrays工具类
import java.util.Collections; // 导入Java的Collections工具类
import java.util.Locale; // 导入Java的Locale类，用于区域设置
import java.util.Map; // 导入Java的Map接口

/**
 * Test of different boolean expressions (some more complex than others). // 测试不同的布尔表达式（有些更复杂）
 * 
 * 本类用于测试Calcite在Elasticsearch适配器中对布尔表达式的处理能力，包括：
 * - 简单的比较运算符（=, <>, >, <, >=, <=）
 * - 逻辑运算符（AND, OR, NOT）
 * - IN和NOT IN运算符
 * - IS NULL判断
 * - 复杂的嵌套布尔表达式
 * 
 * 测试数据包含一条记录：{a='a', b='b', c='c', int=42}
 * 通过创建Elasticsearch索引和Calcite视图来验证SQL查询的正确性
 */
@ResourceLock(value = "elasticsearch-scrolls", mode = ResourceAccessMode.READ) // 使用资源锁确保Elasticsearch滚动查询的读访问模式
class BooleanLogicTest { // 声明BooleanLogicTest测试类

  public static final EmbeddedElasticsearchPolicy NODE = EmbeddedElasticsearchPolicy.create(); // 创建嵌入式Elasticsearch策略实例，用于管理ES测试节点

  private static final String NAME = "booleanlogic"; // 定义Elasticsearch索引名称为"booleanlogic"

  /**
   * Creates {@code zips} index and inserts some data. // 创建索引并插入测试数据
   *
   * @throws Exception when ES node setup failed // 当ES节点设置失败时抛出异常
   */
  @BeforeAll // 标记此方法在所有测试方法执行前运行一次
  public static void setupInstance() throws Exception { // 设置测试实例的方法，创建ES索引并插入测试数据

    final Map<String, String> mapping = // 创建字段映射Map，定义索引的字段类型
        ImmutableMap.of("a", "keyword", "b", "keyword", // 字段a映射为keyword类型（精确匹配）
            "c", "keyword", "int", "long"); // 字段c映射为keyword类型，字段int映射为long类型

    NODE.createIndex(NAME, mapping); // 在ES中创建名为"booleanlogic"的索引，并应用字段映射

    String doc = "{'a': 'a', 'b':'b', 'c':'c', 'int': 42}".replace('\'', '"'); // 创建JSON文档字符串，将单引号替换为双引号
    NODE.insertDocument(NAME, (ObjectNode) NODE.mapper().readTree(doc)); // 将文档插入到ES索引中，使用mapper将JSON字符串解析为ObjectNode
  }

  private static Connection createConnection() throws SQLException { // 创建Calcite数据库连接的方法
    final Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 通过JDBC获取Calcite连接，使用内存模式
    final SchemaPlus root = // 获取连接的根Schema
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为CalciteConnection并获取根Schema

    root.add("elastic", // 在根Schema中添加名为"elastic"的子Schema
        new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), NAME)); // 创建ElasticsearchSchema实例，传入ES客户端、JSON映射器和索引名

    // add calcite view programmatically // 以编程方式添加Calcite视图
    final String viewSql = // 定义视图SQL语句
        String.format(Locale.ROOT, "select cast(_MAP['a'] AS varchar(2)) AS a, " // 从ES的_MAP字段中提取a字段并转换为varchar(2)类型
            + " cast(_MAP['b'] AS varchar(2)) AS b, " // 从ES的_MAP字段中提取b字段并转换为varchar(2)类型
            + " cast(_MAP['c'] AS varchar(2)) AS c, " // 从ES的_MAP字段中提取c字段并转换为varchar(2)类型
            + " cast(_MAP['int'] AS integer) AS num" // 从ES的_MAP字段中提取int字段并转换为integer类型
            + " from \"elastic\".\"%s\"", NAME); // 从elastic schema的指定索引中查询

    root.add("VIEW", // 在根Schema中添加名为"VIEW"的视图
        ViewTable.viewMacro(root, viewSql, // 创建视图表，传入根Schema、视图SQL
            Collections.singletonList("elastic"), // 视图的路径列表为["elastic"]
            Arrays.asList("elastic", "view"), false)); // 视图的全限定路径为["elastic", "view"]，不使用临时视图

    return connection; // 返回创建的连接
  }

  @Test void expressions() { // 测试各种布尔表达式的测试方法
    assertSingle("select * from view"); // 测试无条件查询，应返回单条记录
    assertSingle("select * from view where a = 'a'"); // 测试等于条件，a字段等于'a'，应返回单条记录
    assertEmpty("select * from view where a <> 'a'"); // 测试不等于条件，a字段不等于'a'，应返回空结果
    assertSingle("select * from view where  'a' = a"); // 测试等于条件的反向写法，应返回单条记录
    assertEmpty("select * from view where a = 'b'"); // 测试等于条件，a字段等于'b'，应返回空结果
    assertEmpty("select * from view where 'b' = a"); // 测试等于条件的反向写法，应返回空结果
    assertSingle("select * from view where a in ('a', 'b')"); // 测试IN操作符，a字段在列表中，应返回单条记录
    assertSingle("select * from view where a in ('a', 'c') and b = 'b'"); // 测试AND条件，a在列表中且b='b'，应返回单条记录
    assertSingle("select * from view where (a = 'ZZ' or a = 'a')  and b = 'b'"); // 测试OR与AND的组合条件，应返回单条记录
    assertSingle("select * from view where b = 'b' and a in ('a', 'c')"); // 测试AND条件，b='b'且a在列表中，应返回单条记录
    assertSingle("select * from view where num = 42 and a in ('a', 'c')"); // 测试AND条件，num=42且a在列表中，应返回单条记录
    assertEmpty("select * from view where a in ('a', 'c') and b = 'c'"); // 测试AND条件，a在列表中但b不等于'c'，应返回空结果
    assertSingle("select * from view where a in ('a', 'c') and b = 'b' and num = 42"); // 测试多个AND条件，应返回单条记录
    assertSingle("select * from view where a in ('a', 'c') and b = 'b' and num >= 42"); // 测试大于等于条件，应返回单条记录
    assertEmpty("select * from view where a in ('a', 'c') and b = 'b' and num <> 42"); // 测试不等于条件，应返回空结果
    assertEmpty("select * from view where a in ('a', 'c') and b = 'b' and num > 42"); // 测试大于条件，应返回空结果
    assertSingle("select * from view where num = 42"); // 测试数字等于条件，应返回单条记录
    assertSingle("select * from view where 42 = num"); // 测试数字等于条件的反向写法，应返回单条记录
    assertEmpty("select * from view where num > 42"); // 测试数字大于条件，应返回空结果
    assertEmpty("select * from view where 42 > num"); // 测试数字大于条件的反向写法，应返回空结果
    assertEmpty("select * from view where num > 42 and num > 42"); // 测试重复的大于条件，应返回空结果
    assertEmpty("select * from view where num > 42 and num < 42"); // 测试矛盾的AND条件，应返回空结果
    assertEmpty("select * from view where num > 42 and num < 42 and num <> 42"); // 测试多个矛盾的AND条件，应返回空结果
    assertEmpty("select * from view where num > 42 and num < 42 and num = 42"); // 测试完全矛盾的AND条件，应返回空结果
    assertEmpty("select * from view where num > 42 or num < 42 and num = 42"); // 测试OR与AND的优先级，应返回空结果
    assertSingle("select * from view where num > 42 and num < 42 or num = 42"); // 测试OR与AND的优先级，应返回单条记录
    assertSingle("select * from view where num > 42 or num < 42 or num = 42"); // 测试多个OR条件，应返回单条记录
    assertEmpty("select * from view where num is null"); // 测试IS NULL条件，应返回空结果
    assertSingle("select * from view where num >= 42 and num <= 42 and num = 42"); // 测试范围条件，应返回单条记录
    assertEmpty("select * from view where num >= 42 and num <= 42 and num <> 42"); // 测试范围条件与不等于的矛盾，应返回空结果
    assertEmpty("select * from view where num < 42"); // 测试小于条件，应返回空结果
    assertEmpty("select * from view where num <> 42"); // 测试不等于条件，应返回空结果
    assertSingle("select * from view where num >= 42"); // 测试大于等于条件，应返回单条记录
    assertSingle("select * from view where num <= 42"); // 测试小于等于条件，应返回单条记录
    assertSingle("select * from view where num < 43"); // 测试小于条件，应返回单条记录
    assertSingle("select * from view where num < 50"); // 测试小于条件，应返回单条记录
    assertSingle("select * from view where num > 41"); // 测试大于条件，应返回单条记录
    assertSingle("select * from view where num > 0"); // 测试大于条件，应返回单条记录
    assertSingle("select * from view where (a = 'a' and b = 'b') or (num = 42 and c = 'c')"); // 测试嵌套的OR与AND条件，应返回单条记录
    assertSingle("select * from view where c = 'c' and (a in ('a', 'b') or num in (41, 42))"); // 测试嵌套的IN与OR条件，应返回单条记录
    assertSingle("select * from view where (a = 'a' or b = 'b') or (num = 42 and c = 'c')"); // 测试嵌套的OR与AND条件，应返回单条记录
    assertSingle("select * from view where a = 'a' and (b = '0' or (b = 'b' and " // 测试深层嵌套的OR与AND条件，应返回单条记录
        +  "(c = '0' or (c = 'c' and num = 42))))"); // 继续深层嵌套条件
  }

  /**
   * Tests negations ({@code NOT} operator). // 测试否定操作符（NOT）
   */
  @Test void notExpression() { // 测试NOT操作符的测试方法
    assertEmpty("select * from view where not a = 'a'"); // 测试NOT等于条件，应返回空结果
    assertSingle("select * from view where not not a = 'a'"); // 测试双重NOT等于条件，应返回单条记录
    assertEmpty("select * from view where not not not a = 'a'"); // 测试三重NOT等于条件，应返回空结果
    assertSingle("select * from view where not a <> 'a'"); // 测试NOT不等于条件，应返回单条记录
    assertSingle("select * from view where not not not a <> 'a'"); // 测试三重NOT不等于条件，应返回单条记录
    assertEmpty("select * from view where not 'a' = a"); // 测试NOT等于条件的反向写法，应返回空结果
    assertSingle("select * from view where not 'a' <> a"); // 测试NOT不等于条件的反向写法，应返回单条记录
    assertSingle("select * from view where not a = 'b'"); // 测试NOT等于条件，应返回单条记录
    assertSingle("select * from view where not 'b' = a"); // 测试NOT等于条件的反向写法，应返回单条记录
    assertEmpty("select * from view where not a in ('a')"); // 测试NOT IN条件，应返回空结果
    assertEmpty("select * from view where a not in ('a')"); // 测试NOT IN操作符，应返回空结果
    assertSingle("select * from view where not a not in ('a')"); // 测试双重NOT IN条件，应返回单条记录
    assertEmpty("select * from view where not a not in ('b')"); // 测试NOT NOT IN条件，应返回空结果
    assertEmpty("select * from view where not not a not in ('a')"); // 测试双重NOT NOT IN条件，应返回空结果
    assertSingle("select * from view where not not a not in ('b')"); // 测试双重NOT NOT IN条件，应返回单条记录
    assertEmpty("select * from view where not a in ('a', 'b')"); // 测试NOT IN多值条件，应返回空结果
    assertEmpty("select * from view where a not in ('a', 'b')"); // 测试NOT IN多值操作符，应返回空结果
    assertEmpty("select * from view where not a not in ('z')"); // 测试NOT NOT IN条件，应返回空结果
    assertEmpty("select * from view where not a not in ('z')"); // 测试NOT NOT IN条件（重复），应返回空结果
    assertSingle("select * from view where not a in ('z')"); // 测试NOT IN条件，应返回单条记录
    assertSingle("select * from view where not (not num = 42 or not a in ('a', 'c'))"); // 测试复杂的NOT嵌套条件，应返回单条记录
    assertEmpty("select * from view where not num > 0"); // 测试NOT大于条件，应返回空结果
    assertEmpty("select * from view where num = 42 and a not in ('a', 'c')"); // 测试AND与NOT IN条件，应返回空结果
    assertSingle("select * from view where not (num > 42 or num < 42 and num = 42)"); // 测试NOT嵌套的OR与AND条件，应返回单条记录
  }

  private void assertSingle(String query) { // 断言查询返回单条记录的辅助方法
    CalciteAssert.that() // 创建CalciteAssert构建器
            .with(BooleanLogicTest::createConnection) // 设置连接提供者，使用createConnection方法创建连接
            .query(query) // 执行传入的SQL查询
            .returns("A=a; B=b; C=c; NUM=42\n"); // 断言返回结果为单条记录，包含指定的字段值
  }

  private void assertEmpty(String query) { // 断言查询返回空结果的辅助方法
    CalciteAssert.that() // 创建CalciteAssert构建器
            .with(BooleanLogicTest::createConnection) // 设置连接提供者，使用createConnection方法创建连接
            .query(query) // 执行传入的SQL查询
            .returns(""); // 断言返回结果为空字符串
  }

}
