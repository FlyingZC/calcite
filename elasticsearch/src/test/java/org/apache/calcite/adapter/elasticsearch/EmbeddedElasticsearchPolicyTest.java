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
// Apache许可证声明，说明该文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.elasticsearch; // 包声明，该类位于Elasticsearch适配器测试包中

import org.apache.http.HttpEntity; // 导入Apache Http的HttpEntity类，用于处理HTTP响应实体
import org.apache.http.util.EntityUtils; // 导入EntityUtils工具类，用于将HttpEntity转换为字符串

import com.fasterxml.jackson.databind.JsonNode; // 导入Jackson的JsonNode类，用于JSON树模型的节点操作
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson的ObjectMapper类，用于JSON序列化和反序列化
import com.google.common.collect.ImmutableMap; // 导入Guava的ImmutableMap类，用于创建不可变的Map

import org.elasticsearch.client.Request; // 导入Elasticsearch的Request类，用于构建HTTP请求
import org.elasticsearch.client.Response; // 导入Elasticsearch的Response类，用于处理HTTP响应
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法
import org.junit.jupiter.api.parallel.ResourceAccessMode; // 导入并行测试的资源访问模式枚举
import org.junit.jupiter.api.parallel.ResourceLock; // 导入并行测试的资源锁注解

import java.io.IOException; // 导入IOException类，处理IO异常
import java.util.Map; // 导入Map接口，用于存储键值对映射

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的assertThat断言方法

/**
 * Tests for {@link EmbeddedElasticsearchPolicy}.
 * EmbeddedElasticsearchPolicy的测试类
 * 
 * 本测试类用于验证EmbeddedElasticsearchPolicy（嵌入式Elasticsearch策略）的功能，
 * 特别关注Elasticsearch索引的字段映射创建功能。
 * 
 * 主要测试场景包括：
 * 1. 简单字段映射：创建具有基本数据类型字段的索引
 * 2. 嵌套字段映射：创建包含嵌套对象的索引
 * 3. 多字段映射：创建一个字段具有多种类型的索引（如text和keyword）
 * 4. 混合映射：同时包含嵌套字段和多字段的复杂场景
 * 
 * 这些测试旨在解决CALCITE-6498问题，该问题涉及Elasticsearch多字段映射无法正常工作的bug。
 * 
 * @see EmbeddedElasticsearchPolicy 被测试的嵌入式Elasticsearch策略类
 */
@ResourceLock(value = "elasticsearch-scrolls", mode = ResourceAccessMode.READ) // 使用资源锁注解，确保测试以读模式访问elasticsearch-scrolls资源，避免并发冲突
public class EmbeddedElasticsearchPolicyTest { // EmbeddedElasticsearchPolicy的测试类声明

  // 静态常量：嵌入式Elasticsearch节点策略实例
  // 该实例在类加载时创建，用于所有测试方法共享，避免重复创建Elasticsearch节点
  private static final EmbeddedElasticsearchPolicy NODE = // 声明一个静态最终常量，类型为EmbeddedElasticsearchPolicy
      EmbeddedElasticsearchPolicy.create(); // 调用EmbeddedElasticsearchPolicy的create()静态方法创建并初始化嵌入式Elasticsearch节点

  /** Test case for // 测试用例文档注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498] // 引用JIRA问题CALCITE-6498的链接
   * Elasticsearch multi-field mappings do not work</a>. // 问题描述：Elasticsearch多字段映射无法工作
   */ // 测试用例文档注释结束
  @Test void testCreateIndexWithSimpleFieldMappings() throws Exception { // 测试方法：测试创建具有简单字段映射的索引，声明可能抛出异常
    final Map<String, String> mapping = // 声明一个不可变的Map映射，存储字段名到字段类型的映射关系
        ImmutableMap.of("a", "keyword", "b", "text", "c", "long"); // 创建包含三个字段的映射：a字段为keyword类型，b字段为text类型，c字段为long类型
    final String simpleMappingIndex = "index_simple_mapping"; // 定义索引名称为"index_simple_mapping"，用于标识简单映射测试的索引

    NODE.createIndex(simpleMappingIndex, mapping); // 调用嵌入式节点的createIndex方法，创建指定名称的索引并应用字段映射

    final JsonNode properties = getMappings(simpleMappingIndex); // 调用getMappings辅助方法，从Elasticsearch获取索引的映射信息并解析为JsonNode对象

    assertThat(properties.path("a").path("type").asText(), is("keyword")); // 断言：验证字段a的类型是否为keyword，通过JsonNode的path方法导航到type节点并转换为文本进行比较
    assertThat(properties.path("b").path("type").asText(), is("text")); // 断言：验证字段b的类型是否为text
    assertThat(properties.path("c").path("type").asText(), is("long")); // 断言：验证字段c的类型是否为long
  } // 测试方法结束

  /** Test case for // 测试用例文档注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498] // 引用JIRA问题CALCITE-6498的链接
   * Elasticsearch multi-field mappings do not work</a>. // 问题描述：Elasticsearch多字段映射无法工作
   */ // 测试用例文档注释结束
  @Test void testCreateIndexWithNestedFieldMappings() throws Exception { // 测试方法：测试创建具有嵌套字段映射的索引，声明可能抛出异常
    final Map<String, String> mapping = // 声明一个不可变的Map映射，存储嵌套字段的映射关系
        ImmutableMap.of("a", "nested", "a.b", "text", "a.c", "long"); // 创建映射：a字段为nested类型（嵌套对象），a.b字段为text类型，a.c字段为long类型
    final String index = "index_nested_field_mappings"; // 定义索引名称为"index_nested_field_mappings"，用于标识嵌套字段映射测试的索引

    NODE.createIndex(index, mapping); // 调用嵌入式节点的createIndex方法，创建包含嵌套字段的索引

    final JsonNode properties = getMappings(index); // 调用getMappings辅助方法，获取索引的映射信息

    assertThat(properties.path("a").path("type").asText(), is("nested")); // 断言：验证字段a的类型是否为nested
    assertThat(properties.path("a") // 导航到字段a节点
                          .path("properties") // 导航到a字段的properties子节点（存储嵌套对象的属性）
                            .path("b").path("type").asText(), is("text")); // 断言：验证嵌套对象中字段b的类型是否为text
    assertThat(properties.path("a") // 导航到字段a节点
                          .path("properties") // 导航到a字段的properties子节点
                            .path("c").path("type").asText(), is("long")); // 断言：验证嵌套对象中字段c的类型是否为long
  } // 测试方法结束

  /** Test case for // 测试用例文档注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498] // 引用JIRA问题CALCITE-6498的链接
   * Elasticsearch multi-field mappings do not work</a>. // 问题描述：Elasticsearch多字段映射无法工作
   */ // 测试用例文档注释结束
  @Test void testCreateIndexWithMultiFieldMappings() throws Exception { // 测试方法：测试创建具有多字段映射的索引，声明可能抛出异常
    final Map<String, String> mapping = // 声明一个不可变的Map映射，存储多字段映射关系
        ImmutableMap.of("a", "text", "a.keyword", "keyword"); // 创建映射：a字段为text类型，a.keyword字段为keyword类型（这是Elasticsearch的多字段特性，一个字段可以有多个子字段）
    final String index = "index_multi_field_mappings"; // 定义索引名称为"index_multi_field_mappings"，用于标识多字段映射测试的索引

    NODE.createIndex(index, mapping); // 调用嵌入式节点的createIndex方法，创建包含多字段的索引

    final JsonNode properties = getMappings(index); // 调用getMappings辅助方法，获取索引的映射信息

    assertThat(properties.path("a").path("type").asText(), is("text")); // 断言：验证字段a的主类型是否为text
    assertThat(properties.path("a") // 导航到字段a节点
                          .path("fields") // 导航到a字段的fields子节点（存储多字段映射）
                            .path("keyword").path("type").asText(), // 导航到keyword子字段并获取其type
        is("keyword")); // 断言：验证a.keyword字段的类型是否为keyword
  } // 测试方法结束

  /** Test case for // 测试用例文档注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498] // 引用JIRA问题CALCITE-6498的链接
   * Elasticsearch multi-field mappings do not work</a>. // 问题描述：Elasticsearch多字段映射无法工作
   */ // 测试用例文档注释结束
  @Test void testCreateIndexWithNestedFieldMappingsAndMultiFieldMappings() // 测试方法：测试创建同时包含嵌套字段映射和多字段映射的索引
      throws Exception { // 声明可能抛出异常
    final Map<String, String> mapping = // 声明一个不可变的Map映射，存储复杂的嵌套和多字段映射关系
        ImmutableMap.of("a", "nested", "a.b", "text", "a.b.keyword", "keyword"); // 创建映射：a字段为nested类型，a.b字段为text类型，a.b.keyword字段为keyword类型（嵌套对象中的多字段）
    final String index = "index_nested_and_multi_field_mappings"; // 定义索引名称为"index_nested_and_multi_field_mappings"，用于标识混合映射测试的索引

    NODE.createIndex(index, mapping); // 调用嵌入式节点的createIndex方法，创建包含复杂映射的索引

    final JsonNode properties = getMappings(index); // 调用getMappings辅助方法，获取索引的映射信息

    assertThat(properties.path("a").path("type").asText(), is("nested")); // 断言：验证字段a的类型是否为nested
    assertThat(properties.path("a") // 导航到字段a节点
                          .path("properties") // 导航到a字段的properties子节点
                            .path("b").path("type").asText(), is("text")); // 断言：验证嵌套对象中字段b的类型是否为text
    assertThat(properties.path("a") // 导航到字段a节点
                          .path("properties") // 导航到a字段的properties子节点
                            .path("b").path("fields") // 导航到b字段的fields子节点
                              .path("keyword") // 导航到keyword子字段
                                .path("type").asText(), is("keyword")); // 断言：验证a.b.keyword字段的类型是否为keyword
  } // 测试方法结束

  // 私有静态辅助方法：获取指定索引的映射信息
  // 参数：index - 要查询的索引名称
  // 返回值：JsonNode对象，包含索引的字段映射属性
  private static JsonNode getMappings(String index) throws IOException { // 方法声明，接收索引名称参数，可能抛出IOException异常
    Response indexMappingsResponse = NODE.restClient() // 获取嵌入式节点的REST客户端
        .performRequest(new Request("GET", "/" + index + "/_mapping")); // 执行GET请求，获取指定索引的映射信息，URL格式为：/{index}/_mapping
    HttpEntity entity = indexMappingsResponse.getEntity(); // 从HTTP响应中获取实体对象
    String responseBody = EntityUtils.toString(entity); // 将HttpEntity转换为字符串形式的响应体
    JsonNode responseJson = new ObjectMapper().readTree(responseBody); // 使用ObjectMapper将JSON字符串解析为JsonNode树结构

    // It's more readable to assert on a JsonNode than on a map, where you need // 注释：在JsonNode上进行断言比在Map上更易读，
    // to cast a lot // 因为在Map中需要进行大量的类型转换
    return responseJson.path(index).path("mappings").path("properties"); // 返回从响应JSON中提取的properties节点，导航路径为：索引名 -> mappings -> properties
  } // 方法结束
} // 类结束
