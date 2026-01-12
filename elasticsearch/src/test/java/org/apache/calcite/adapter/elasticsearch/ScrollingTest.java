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
package org.apache.calcite.adapter.elasticsearch; // 声明该类属于elasticsearch适配器包

import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite JDBC连接类，用于创建和配置Calcite数据库连接
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，用于添加和管理数据模式
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言工具类，用于测试SQL查询结果
import org.apache.calcite.test.ConnectionFactory; // 导入连接工厂接口，用于创建数据库连接

import com.fasterxml.jackson.databind.JsonNode; // 导入JsonNode类，用于处理JSON数据节点
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入ObjectNode类，用于处理JSON对象节点

import org.elasticsearch.client.Request; // 导入Elasticsearch请求类，用于构建HTTP请求
import org.elasticsearch.client.Response; // 导入Elasticsearch响应类，用于处理HTTP响应
import org.junit.jupiter.api.BeforeAll; // 导入BeforeAll注解，用于在所有测试方法执行前执行一次初始化操作
import org.junit.jupiter.api.Disabled; // 导入Disabled注解，用于禁用某些测试方法
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法
import org.junit.jupiter.api.parallel.ResourceLock; // 导入ResourceLock注解，用于控制测试资源的并发访问

import java.io.IOException; // 导入IO异常类，用于处理输入输出异常
import java.io.InputStream; // 导入输入流类，用于读取数据
import java.sql.Connection; // 导入JDBC连接接口，用于数据库连接
import java.sql.DriverManager; // 导入驱动管理器类，用于获取数据库连接
import java.util.ArrayList; // 导入ArrayList类，用于动态数组
import java.util.Arrays; // 导入Arrays工具类，用于数组操作
import java.util.Collections; // 导入Collections工具类，用于集合操作
import java.util.List; // 导入List接口，用于列表集合
import java.util.Locale; // 导入Locale类，用于本地化处理
import java.util.stream.IntStream; // 导入IntStream类，用于整数流操作

/**
 * Tests usage of scrolling API like correct results and resource cleanup
 * (delete scroll after scan).
 */ // 类文档注释：测试Elasticsearch滚动API的使用，包括正确的结果和资源清理（扫描后删除滚动上下文）
@ResourceLock("elasticsearch-scrolls") // 资源锁注解，确保Elasticsearch滚动资源在测试期间不被并发访问
class ScrollingTest { // ScrollingTest类：用于测试Elasticsearch滚动API功能的测试类

  public static final EmbeddedElasticsearchPolicy NODE = EmbeddedElasticsearchPolicy.create(); // 成员变量：嵌入式Elasticsearch策略实例，用于管理Elasticsearch测试节点，create()方法创建并启动一个嵌入式Elasticsearch节点

  private static final String NAME = "scroll"; // 成员变量：索引名称常量，用于标识Elasticsearch中的测试索引
  private static final int SIZE = 10; // 成员变量：测试数据大小常量，表示插入到索引中的文档数量

  @BeforeAll // 注解：在所有测试方法执行前执行一次的初始化方法
  public static void setupInstance() throws Exception { // setupInstance方法：初始化测试环境，创建索引并插入测试数据
    NODE.createIndex(NAME, Collections.singletonMap("value", "long")); // 创建名为"scroll"的索引，定义字段"value"为long类型
    final List<ObjectNode> docs = new ArrayList<>(); // 创建文档列表，用于存储要插入的JSON文档
    for (int i = 0; i < SIZE; i++) { // 循环生成SIZE个测试文档
      String json = String.format(Locale.ROOT, "{\"value\": %d}", i); // 格式化JSON字符串，包含value字段，值为0到SIZE-1
      docs.add((ObjectNode) NODE.mapper().readTree(json)); // 将JSON字符串解析为ObjectNode并添加到文档列表中
    }
    NODE.insertBulk(NAME, docs); // 批量插入文档到Elasticsearch索引中
  }

  private ConnectionFactory newConnectionFactory(int fetchSize) { // newConnectionFactory方法：创建连接工厂，用于生成带有指定fetchSize的数据库连接
    return () -> { // 返回一个lambda表达式，实现ConnectionFactory接口的getConnection方法
      final Connection connection = // 创建Calcite JDBC连接
          DriverManager.getConnection("jdbc:calcite:"); // 通过驱动管理器获取Calcite数据库连接
      final SchemaPlus root = // 获取根Schema对象
          connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为CalciteConnection并获取根Schema
      root.add("elastic", // 向根Schema添加名为"elastic"的Elasticsearch Schema
          new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), NAME, // 创建ElasticsearchSchema实例，传入REST客户端、JSON映射器和索引名称
              fetchSize)); // 传入fetchSize参数，控制每次从Elasticsearch获取的文档数量
      return connection; // 返回配置好的数据库连接
    };
  }

  @Disabled("It seems like other tests leave scrolls behind, so this test fails if executed after" // 禁用注解：说明该测试被禁用的原因，其他测试可能会留下滚动上下文
      + " one of the other elasticsearch test") // 导致该测试在其他Elasticsearch测试之后执行时会失败
  @Test void scrolling() throws Exception { // scrolling方法：测试滚动API的正确性和资源清理功能
    final String[] expected = IntStream.range(0, SIZE).mapToObj(i -> "V=" + i) // 创建期望的结果数组，包含"V=0"到"V=9"的字符串
        .toArray(String[]::new); // 将流转换为字符串数组
    final String query = String.format(Locale.ROOT, "select _MAP['value'] as v from " // 构造SQL查询语句，从Elasticsearch索引的_MAP字段中提取value值
        + "\"elastic\".\"%s\"", NAME); // 使用格式化字符串，指定elastic schema和scroll索引

    for (int fetchSize : Arrays.asList(1, 2, 3, SIZE / 2, SIZE - 1, SIZE, SIZE + 1, 2 * SIZE)) { // 遍历不同的fetchSize值，测试不同批量大小的滚动行为
      CalciteAssert.that() // 创建Calcite断言对象
          .with(newConnectionFactory(fetchSize)) // 设置连接工厂，使用当前的fetchSize
          .query(query) // 执行SQL查询
          .returnsUnordered(expected); // 验证查询结果是否与期望结果匹配（不考虑顺序）
      assertNoActiveScrolls(); // 断言没有活动的滚动上下文，确保资源被正确清理
    }
  }

  /**
   * Ensures there are no pending scroll contexts in elastic search cluster.
   * Queries {@code /_nodes/stats/indices/search} endpoint.
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/indices-stats.html">Indices Stats</a>
   */ // 方法文档注释：确保Elasticsearch集群中没有待处理的滚动上下文，通过查询/_nodes/stats/indices/search端点来验证
  private void assertNoActiveScrolls() throws IOException  { // assertNoActiveScrolls方法：断言没有活动的滚动上下文，验证资源是否被正确清理
    // get node stats // 注释：获取节点统计信息
    final Response response = NODE.restClient() // 使用Elasticsearch REST客户端发送请求
        .performRequest(new Request("GET", "/_nodes/stats/indices/search")); // 发送GET请求到/_nodes/stats/indices/search端点，获取节点搜索统计信息

    try (InputStream is = response.getEntity().getContent()) { // 使用try-with-resources自动关闭输入流，从响应中获取内容流
      final ObjectNode node = NODE.mapper().readValue(is, ObjectNode.class); // 将输入流解析为ObjectNode对象
      final String path = "/indices/search/scroll_current"; // 定义JSON路径，用于获取当前活动滚动上下文的数量
      final JsonNode scrollCurrent = // 获取scroll_current节点的值
          node.get("nodes").elements().next().at(path); // 从nodes数组中获取第一个节点的scroll_current值
      if (scrollCurrent.isMissingNode()) { // 检查节点是否存在
        throw new IllegalStateException("Couldn't find node at " + path); // 如果节点不存在，抛出非法状态异常
      }

      if (scrollCurrent.asInt() != 0) { // 检查活动滚动上下文数量是否为0
        final String message = String.format(Locale.ROOT, "Expected no active scrolls " // 格式化错误消息
            + "but got %d. Current index stats %s", scrollCurrent.asInt(), node); // 包含实际滚动上下文数量和索引统计信息
        throw new AssertionError(message); // 抛出断言错误，表示资源未正确清理
      }
    }
  }


} // 类结束标记，ScrollingTest类定义结束
