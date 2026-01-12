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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，表示该类属于 Elasticsearch 适配器包

import org.apache.calcite.jdbc.CalciteConnection; // 导入 Calcite 连接类，用于获取 Calcite 特定的连接功能
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 类，用于在 Calcite 中添加和管理 schema
import org.apache.calcite.test.CalciteAssert; // 导入 Calcite 测试断言工具类，用于编写测试断言

import com.fasterxml.jackson.core.JsonParser; // 导入 Jackson JSON 解析器，用于解析 JSON 数据
import com.fasterxml.jackson.databind.ObjectMapper; // 导入 Jackson 对象映射器，用于 JSON 和 Java 对象之间的转换
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入 Jackson 对象节点类，表示 JSON 对象
import com.google.common.collect.ImmutableMap; // 导入 Google Guava 的不可变 Map 类，用于创建不可修改的映射

import org.junit.jupiter.api.BeforeAll; // 导入 JUnit 5 的 BeforeAll 注解，表示在所有测试方法执行前运行一次
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，标记测试方法
import org.junit.jupiter.api.parallel.ResourceAccessMode; // 导入 JUnit 5 并行测试的资源访问模式枚举
import org.junit.jupiter.api.parallel.ResourceLock; // 导入 JUnit 5 的资源锁注解，用于控制对共享资源的访问

import java.sql.Connection; // 导入 JDBC 连接接口，用于数据库连接
import java.sql.DriverManager; // 导入 JDBC 驱动管理器，用于获取数据库连接
import java.sql.SQLException; // 导入 SQL 异常类，用于处理数据库操作中的错误
import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组列表
import java.util.Arrays; // 导入 Arrays 工具类，用于数组操作
import java.util.List; // 导入 List 接口，表示列表集合
import java.util.Locale; // 导入 Locale 类，用于本地化设置
import java.util.Map; // 导入 Map 接口，表示键值对映射

/**
 * Testing Elasticsearch join query. // 测试 Elasticsearch 连接查询的测试类
 * 该类主要用于验证 Calcite 框架通过 Elasticsearch 适配器执行 JOIN 操作的功能
 * 测试场景包括两个 Elasticsearch 索引之间的内连接查询，验证连接条件和结果集的正确性
 * 测试使用嵌入式的 Elasticsearch 实例，确保测试环境独立且可重复
 */
@ResourceLock(value = "elasticsearch-scrolls", mode = ResourceAccessMode.READ) // 使用资源锁注解，确保在测试期间对 Elasticsearch 滚动资源的读取访问是线程安全的
public class JoinTest { // 定义 JoinTest 测试类，用于测试 Elasticsearch 的 JOIN 功能

  public static final EmbeddedElasticsearchPolicy NODE = EmbeddedElasticsearchPolicy.create(); // 创建并初始化嵌入式 Elasticsearch 实例策略对象，用于管理测试用的 Elasticsearch 节点，该节点是静态共享的，所有测试方法都可以访问

  private static final String NAME_LEFT = "lt"; // 定义左侧表（索引）的名称常量，值为 "lt"，用于标识左表索引

  private static final String NAME_RIGHT = "rt"; // 定义右侧表（索引）的名称常量，值为 "rt"，用于标识右表索引


  @BeforeAll // 使用 JUnit 5 的 BeforeAll 注解，表示该方法在所有测试方法执行前只运行一次，用于初始化测试环境
  public static void setupInstance() throws Exception { // 定义静态初始化方法，用于设置测试所需的 Elasticsearch 索引和数据，方法可能抛出异常
    final Map<String, String> ltMappings = ImmutableMap.<String, String>builder() // 创建左侧索引的字段映射构建器，用于定义索引中字段的数据类型
        .put("doc_id", "keyword") // 定义 doc_id 字段为 keyword 类型，keyword 类型适合精确匹配和聚合操作
        .put("val1", "long") // 定义 val1 字段为 long 类型，用于存储长整型数值
        .build(); // 构建不可变的映射对象，完成左侧索引的字段类型定义

    final Map<String, String> rtMappings = ImmutableMap.<String, String>builder() // 创建右侧索引的字段映射构建器，用于定义索引中字段的数据类型
        .put("doc_id", "keyword") // 定义 doc_id 字段为 keyword 类型，与左侧表保持一致以便进行连接操作
        .put("val2", "long") // 定义 val2 字段为 long 类型，用于存储长整型数值
        .build(); // 构建不可变的映射对象，完成右侧索引的字段类型定义

    NODE.createIndex(NAME_LEFT, ltMappings); // 在 Elasticsearch 节点上创建左侧索引，索引名为 "lt"，使用之前定义的字段映射
    NODE.createIndex(NAME_RIGHT, rtMappings); // 在 Elasticsearch 节点上创建右侧索引，索引名为 "rt"，使用之前定义的字段映射
    final ObjectMapper mapper = new ObjectMapper() // 创建 Jackson ObjectMapper 实例，用于 JSON 数据的序列化和反序列化
        .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES) // 启用允许字段名不加引号的特性，使 JSON 格式更友好
        .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES); // 启用允许单引号的特性，避免过多的引号使用，使 JSON 更简洁

    String ldoc1 = "{doc_id:'1', val1:1}"; // 定义左侧表的第一条文档数据，doc_id 为 "1"，val1 为 1
    String ldoc2 = "{doc_id:'2', val1:2}"; // 定义左侧表的第二条文档数据，doc_id 为 "2"，val1 为 2
    final List<ObjectNode> docs = new ArrayList<>(); // 创建左侧文档列表，用于存储待插入的文档对象
    for (String text : Arrays.asList(ldoc1, ldoc2)) { // 遍历左侧文档字符串数组，将每个字符串转换为 ObjectNode 对象
      docs.add((ObjectNode) mapper.readTree(text)); // 使用 ObjectMapper 将 JSON 字符串解析为 ObjectNode 对象并添加到列表中
    }
    NODE.insertBulk(NAME_LEFT, docs); // 将左侧文档列表批量插入到左侧索引 "lt" 中，完成数据的初始化


    String rdoc1 = "{doc_id:'1', val2:1}"; // 定义右侧表的第一条文档数据，doc_id 为 "1"，val2 为 1
    String rdoc2 = "{doc_id:'2', val2:2}"; // 定义右侧表的第二条文档数据，doc_id 为 "2"，val2 为 2
    final List<ObjectNode> rdocs = new ArrayList<>(); // 创建右侧文档列表，用于存储待插入的文档对象
    for (String text : Arrays.asList(rdoc1, rdoc2)) { // 遍历右侧文档字符串数组，将每个字符串转换为 ObjectNode 对象
      rdocs.add((ObjectNode) mapper.readTree(text)); // 使用 ObjectMapper 将 JSON 字符串解析为 ObjectNode 对象并添加到列表中
    }
    NODE.insertBulk(NAME_RIGHT, rdocs); // 将右侧文档列表批量插入到右侧索引 "rt" 中，完成数据的初始化
  }

  private static Connection createConnection() throws SQLException { // 创建 Calcite 数据库连接的私有静态方法，用于建立与 Calcite 的连接
    final Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 通过 DriverManager 获取 Calcite JDBC 连接，使用内存数据库模式
    final SchemaPlus root = // 获取 Calcite 连接的根 Schema，用于添加和管理子 Schema
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为 CalciteConnection 并获取其根 Schema

    root.add("elastic0", new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), NAME_LEFT)); // 在根 Schema 中添加名为 "elastic0" 的 Elasticsearch Schema，连接到左侧索引 "lt"
    root.add("elastic1", new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), NAME_RIGHT)); // 在根 Schema 中添加名为 "elastic1" 的 Elasticsearch Schema，连接到右侧索引 "rt"

    return connection; // 返回配置好的 Calcite 连接对象
  }

  /**
   * Test two elasticserch index join. // 测试两个 Elasticsearch 索引之间的连接查询
   * 该测试方法验证 Calcite 是否能够正确执行跨 Elasticsearch 索引的 JOIN 操作
   * 测试使用内连接（INNER JOIN），基于 doc_id 字段进行连接
   * 预期结果返回两条记录，包含左表的 doc_id 和 val1 字段
   */
  @Test void join() { // 定义测试方法，测试 Elasticsearch 索引的 JOIN 功能
    CalciteAssert.that() // 创建 CalciteAssert 测试断言构建器，用于构建测试用例
        .with(JoinTest::createConnection) // 使用 createConnection 方法创建的连接配置测试环境
        .query( // 指定要执行的 SQL 查询语句
            String.format(Locale.ROOT, // 使用 ROOT 语言环境格式化字符串，确保格式化的一致性
                "select t._MAP['doc_id'] AS \"doc_id\", t._MAP['val1'] AS \"val1\" " // 选择左表的 doc_id 和 val1 字段，使用 _MAP 访问 Elasticsearch 文档的字段
            + " from \"elastic0\".\"%s\" t " // 从左侧 Schema "elastic0" 的索引表中选择数据，表别名为 t
            + " join \"elastic1\".\"%s\" s" // 与右侧 Schema "elastic1" 的索引表进行连接，表别名为 s
            + " on cast(t._MAP['doc_id'] as varchar) = cast(s._MAP['doc_id'] as varchar)", // 连接条件：将两个表的 doc_id 字段转换为 varchar 类型后进行比较
                NAME_LEFT, NAME_RIGHT)) // 格式化字符串参数，使用左侧和右侧索引名称
        .returnsUnordered("doc_id=1; val1=1", // 断言查询结果包含第一条记录，doc_id 为 1，val1 为 1
            "doc_id=2; val1=2"); // 断言查询结果包含第二条记录，doc_id 为 2，val1 为 2，结果顺序不固定

  }

} // JoinTest 类结束
