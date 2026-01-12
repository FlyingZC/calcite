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
package org.apache.calcite.adapter.elasticsearch; // 包声明：Elasticsearch适配器的测试包
import com.fasterxml.jackson.core.JsonParser; // 导入Jackson库的JsonParser，用于解析JSON
import com.fasterxml.jackson.core.JsonProcessingException; // 导入Jackson库的JsonProcessingException，用于处理JSON解析异常
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson库的ObjectMapper，用于JSON对象映射
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson库的ObjectNode，表示JSON对象节点
import com.google.common.collect.ImmutableMap; // 导入Guava库的ImmutableMap，用于创建不可变Map

import org.junit.jupiter.api.BeforeEach; // 导入JUnit5的BeforeEach注解，表示在每个测试方法前执行
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，表示测试方法

import java.util.ArrayList; // 导入Java标准库的ArrayList，用于动态数组
import java.util.HashMap; // 导入Java标准库的HashMap，用于哈希映射
import java.util.List; // 导入Java标准库的List，用于列表接口
import java.util.Map; // 导入Java标准库的Map，用于映射接口

import static org.hamcrest.CoreMatchers.hasItem; // 导入Hamcrest断言库的hasItem，用于验证集合包含某元素
import static org.hamcrest.CoreMatchers.hasItems; // 导入Hamcrest断言库的hasItems，用于验证集合包含多个元素
import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest断言库的instanceOf，用于验证对象类型
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is，用于验证值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言库的assertThat，用于断言
import static org.hamcrest.Matchers.aMapWithSize; // 导入Hamcrest断言库的aMapWithSize，用于验证Map大小
import static org.hamcrest.Matchers.anEmptyMap; // 导入Hamcrest断言库的anEmptyMap，用于验证Map为空
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest断言库的hasSize，用于验证集合大小
import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入JUnit5的assertNotNull，用于验证对象不为空

/**
 * Testing correct parsing of JSON (elasticsearch) response.
 * 测试类：ElasticsearchJsonTest
 * 作用：测试Elasticsearch适配器对JSON响应的正确解析能力
 * 功能范围：
 * 1. 测试聚合(Aggregations)的JSON解析
 * 2. 测试多值聚合(MultiValue)的解析
 * 3. 测试分桶聚合(Buckets)的解析
 * 4. 测试嵌套聚合(Nested Aggregations)的解析
 * 5. 测试多层分桶聚合(Multi-level Buckets)的解析
 * 6. 测试映射属性(Mapping Properties)的解析
 * 7. 测试保留关键字(Reserved Keywords)的映射处理
 * 8. 测试多字段映射(Multi-field Mappings)的解析
 * 9. 测试嵌套字段映射(Nested Field Mappings)的解析
 */
class ElasticsearchJsonTest { // 测试类定义：Elasticsearch JSON解析测试类

  private ObjectMapper mapper; // 成员变量：ObjectMapper实例，用于JSON序列化和反序列化，是Jackson库的核心类

  @BeforeEach // JUnit5注解：在每个测试方法执行前运行此方法，用于初始化测试环境
  public void setUp() { // 测试设置方法：初始化ObjectMapper配置
    this.mapper = new ObjectMapper() // 创建ObjectMapper实例，用于JSON转换
        .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true) // 配置：允许JSON字段名不加引号，兼容ES的非标准JSON
        .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true); // 配置：允许使用单引号，兼容ES的非标准JSON
  } // 方法结束：setUp方法完成，mapper已配置完成

  @Test // JUnit5注解：标记为测试方法
  void aggEmpty() throws Exception { // 测试方法：测试空的聚合响应解析
    String json = "{}"; // 定义空JSON字符串，模拟Elasticsearch返回的空聚合结果

    ElasticsearchJson.Aggregations a = mapper.readValue(json, ElasticsearchJson.Aggregations.class); // 使用ObjectMapper将JSON字符串反序列化为Aggregations对象
    assertNotNull(a); // 断言：验证聚合对象不为null
    assertThat(a.asList(), hasSize(0)); // 断言：验证聚合列表为空，大小为0
    assertThat(a.asMap(), aMapWithSize(0)); // 断言：验证聚合映射为空，大小为0
  } // 方法结束：aggEmpty测试完成

  @Test // JUnit5注解：标记为测试方法
  void aggSingle1() throws Exception { // 测试方法：测试单个聚合值的解析
    String json = "{agg1: {value: '111'}}"; // 定义JSON字符串，包含一个名为agg1的聚合，值为'111'

    ElasticsearchJson.Aggregations a = mapper.readValue(json, ElasticsearchJson.Aggregations.class); // 反序列化JSON为Aggregations对象
    assertNotNull(a); // 断言：验证聚合对象不为null
    assertThat(a.asList(), hasSize(1)); // 断言：验证聚合列表大小为1，包含一个聚合
    assertThat(a.asMap(), aMapWithSize(1)); // 断言：验证聚合映射大小为1
    assertThat(a.asList().get(0).getName(), is("agg1")); // 断言：验证第一个聚合的名称为"agg1"
    assertThat(a.asMap().keySet().iterator().next(), is("agg1")); // 断言：验证映射的键为"agg1"
    assertThat(((ElasticsearchJson.MultiValue) a.asList().get(0)).value(), // 断言：验证聚合的值为'111'，转换为MultiValue类型获取value
        is("111")); // 期望值为字符串"111"

    List<Map<String, Object>> rows = new ArrayList<>(); // 创建列表，用于存储访问后的行数据
    ElasticsearchJson.visitValueNodes(a, rows::add); // 访问聚合值节点，将结果添加到rows列表中
    assertThat(rows, hasSize(1)); // 断言：验证行列表大小为1
    assertThat(rows.get(0).get("agg1"), is("111")); // 断言：验证第一行中agg1字段的值为'111'
  } // 方法结束：aggSingle1测试完成

  @Test // JUnit5注解：标记为测试方法
  void aggMultiValues() throws Exception { // 测试方法：测试多值聚合（包含min、max、avg）的解析
    String json = "{ agg1: {min: 0, max: 2, avg: 2.33}}"; // 定义JSON字符串，包含多值聚合agg1，有min、max、avg三个值
    ElasticsearchJson.Aggregations a = mapper.readValue(json, ElasticsearchJson.Aggregations.class); // 反序列化JSON为Aggregations对象
    assertNotNull(a); // 断言：验证聚合对象不为null
    assertThat(a.asList(), hasSize(1)); // 断言：验证聚合列表大小为1
    assertThat(a.asMap(), aMapWithSize(1)); // 断言：验证聚合映射大小为1
    assertThat(a.asList().get(0).getName(), is("agg1")); // 断言：验证聚合名称为"agg1"

    Map<String, Object> values = ((ElasticsearchJson.MultiValue) a.get("agg1")).values(); // 获取agg1聚合的所有值，返回Map
    assertThat(values.keySet(), hasItems("min", "max", "avg")); // 断言：验证值的键包含min、max、avg
  } // 方法结束：aggMultiValues测试完成

  @Test // JUnit5注解：标记为测试方法
  void aggSingle2() throws Exception { // 测试方法：测试两个单值聚合的解析
    String json = "{ agg1: {value: 'foo'}, agg2: {value: 42}}"; // 定义JSON字符串，包含两个聚合：agg1值为'foo'，agg2值为42

    ElasticsearchJson.Aggregations a = mapper.readValue(json, ElasticsearchJson.Aggregations.class); // 反序列化JSON为Aggregations对象
    assertNotNull(a); // 断言：验证聚合对象不为null
    assertThat(a.asList(), hasSize(2)); // 断言：验证聚合列表大小为2
    assertThat(a.asMap(), aMapWithSize(2)); // 断言：验证聚合映射大小为2
    assertThat(a.asMap().keySet(), hasItems("agg1", "agg2")); // 断言：验证映射包含"agg1"和"agg2"两个键
  } // 方法结束：aggSingle2测试完成

  @Test // JUnit5注解：标记为测试方法
  void aggBuckets1() throws Exception { // 测试方法：测试单层分桶聚合的解析
    String json = "{ groupby: {buckets: [{key:'k1', doc_count:0, myagg:{value: 1.1}},"
        + " {key:'k2', myagg:{value: 2.2}}] }}"; // 定义JSON字符串，包含一个名为groupby的分桶聚合，有两个桶

    ElasticsearchJson.Aggregations a = // 反序列化JSON为Aggregations对象
        mapper.readValue(json, ElasticsearchJson.Aggregations.class); // 完成反序列化

    assertThat(a.asMap().keySet(), hasItem("groupby")); // 断言：验证映射包含"groupby"键
    assertThat(a.get("groupby"), // 断言：验证groupby聚合是MultiBucketsAggregation类型
        instanceOf(ElasticsearchJson.MultiBucketsAggregation.class)); // 类型检查
    ElasticsearchJson.MultiBucketsAggregation multi = a.get("groupby"); // 获取groupby聚合，转换为MultiBucketsAggregation
    assertThat(multi.buckets(), hasSize(2)); // 断言：验证桶的数量为2
    assertThat(multi.getName(), is("groupby")); // 断言：验证聚合名称为"groupby"
    assertThat(multi.buckets().get(0).key(), is("k1")); // 断言：验证第一个桶的键为"k1"
    assertThat(multi.buckets().get(0).keyAsString(), is("k1")); // 断言：验证第一个桶的键字符串为"k1"
    assertThat(multi.buckets().get(1).key(), is("k2")); // 断言：验证第二个桶的键为"k2"
    assertThat(multi.buckets().get(1).keyAsString(), is("k2")); // 断言：验证第二个桶的键字符串为"k2"
  } // 方法结束：aggBuckets1测试完成

  @Test // JUnit5注解：标记为测试方法
  void aggManyAggregations() throws Exception { // 测试方法：测试多个聚合在分桶中的解析
    String json = "{groupby:{buckets:[]" // 定义JSON字符串，包含分桶聚合，每个桶包含多个聚合
        + "{key:'k1', a1:{value:1}, a2:{value:2}},"
        + "{key:'k2', a1:{value:3}, a2:{value:4}}"
        + "]}}"; // JSON结束

    ElasticsearchJson.Aggregations a = // 反序列化JSON为Aggregations对象
        mapper.readValue(json, ElasticsearchJson.Aggregations.class); // 完成反序列化
    ElasticsearchJson.MultiBucketsAggregation multi = a.get("groupby"); // 获取groupby聚合，转换为MultiBucketsAggregation

    assertThat(multi.buckets().get(0).getAggregations().asMap(), // 断言：验证第一个桶的聚合数量为2
        aMapWithSize(2)); // 大小验证
    assertThat(multi.buckets().get(0).getName(), is("groupby")); // 断言：验证第一个桶的名称为"groupby"
    assertThat(multi.buckets().get(0).key(), is("k1")); // 断言：验证第一个桶的键为"k1"
    assertThat(multi.buckets().get(0).getAggregations().asMap().keySet(), // 断言：验证第一个桶的聚合包含"a1"和"a2"
        hasItems("a1", "a2")); // 键验证
    assertThat(multi.buckets().get(1).getAggregations().asMap(), // 断言：验证第二个桶的聚合数量为2
        aMapWithSize(2)); // 大小验证
    assertThat(multi.buckets().get(1).getName(), is("groupby")); // 断言：验证第二个桶的名称为"groupby"
    assertThat(multi.buckets().get(1).key(), is("k2")); // 断言：验证第二个桶的键为"k2"
    assertThat(multi.buckets().get(1).getAggregations().asMap().keySet(), // 断言：验证第二个桶的聚合包含"a1"和"a2"
        hasItems("a1", "a2")); // 键验证
    List<Map<String, Object>> rows = new ArrayList<>(); // 创建列表，用于存储访问后的行数据
    ElasticsearchJson.visitValueNodes(a, rows::add); // 访问聚合值节点，将结果添加到rows列表中
    assertThat(rows, hasSize(2)); // 断言：验证行列表大小为2
    assertThat(rows.get(0).get("groupby"), is("k1")); // 断言：验证第一行的groupby值为"k1"
    assertThat(rows.get(0).get("a1"), is(1)); // 断言：验证第一行的a1值为1
    assertThat(rows.get(0).get("a2"), is(2)); // 断言：验证第一行的a2值为2
  } // 方法结束：aggManyAggregations测试完成

  @Test // JUnit5注解：标记为测试方法
  void aggMultiBuckets() throws Exception { // 测试方法：测试多层分桶聚合的解析
    String json = "{col1: {buckets: []" // 定义JSON字符串，包含两层分桶聚合：col1和col2
        + "{col2: {doc_count:1, buckets:[{key:'k3', max:{value:41}}]}, key:'k1'}," // 第一个桶：col1='k1'，包含col2分桶
        + "{col2: {buckets:[{key:'k4', max:{value:42}}], doc_count:1}, key:'k2'}" // 第二个桶：col1='k2'，包含col2分桶
        + "]}}"; // JSON结束

    ElasticsearchJson.Aggregations a = mapper.readValue(json, ElasticsearchJson.Aggregations.class); // 反序列化JSON为Aggregations对象
    assertNotNull(a); // 断言：验证聚合对象不为null

    assertThat(a.asMap().keySet(), hasItem("col1")); // 断言：验证映射包含"col1"键
    assertThat(a.get("col1"), // 断言：验证col1聚合是MultiBucketsAggregation类型
        instanceOf(ElasticsearchJson.MultiBucketsAggregation.class)); // 类型检查
    ElasticsearchJson.MultiBucketsAggregation m = a.get("col1"); // 获取col1聚合，转换为MultiBucketsAggregation
    assertThat(m.getName(), is("col1")); // 断言：验证聚合名称为"col1"
    assertThat(m.buckets(), hasSize(2)); // 断言：验证桶的数量为2
    assertThat(m.buckets().get(0).key(), is("k1")); // 断言：验证第一个桶的键为"k1"
    assertThat(m.buckets().get(0).getName(), is("col1")); // 断言：验证第一个桶的名称为"col1"
    assertThat(m.buckets().get(0).getAggregations().asMap().keySet(), hasItem("col2")); // 断言：验证第一个桶包含col2聚合
    assertThat(m.buckets().get(1).key(), is("k2")); // 断言：验证第二个桶的键为"k2"
    List<Map<String, Object>> rows = new ArrayList<>(); // 创建列表，用于存储访问后的行数据
    ElasticsearchJson.visitValueNodes(a, rows::add); // 访问聚合值节点，将结果添加到rows列表中
    assertThat(rows, hasSize(2)); // 断言：验证行列表大小为2

    assertThat(rows.get(0).keySet(), hasItems("col1", "col2", "max")); // 断言：验证第一行包含col1、col2、max三个字段
    assertThat(rows.get(0).get("col1"), is("k1")); // 断言：验证第一行的col1值为"k1"
    assertThat(rows.get(0).get("col2"), is("k3")); // 断言：验证第一行的col2值为"k3"
    assertThat(rows.get(0).get("max"), is(41)); // 断言：验证第一行的max值为41

    assertThat(rows.get(1).keySet(), hasItems("col1", "col2", "max")); // 断言：验证第二行包含col1、col2、max三个字段
    assertThat(rows.get(1).get("col1"), is("k2")); // 断言：验证第二行的col1值为"k2"
    assertThat(rows.get(1).get("col2"), is("k4")); // 断言：验证第二行的col2值为"k4"
    assertThat(rows.get(1).get("max"), is(42)); // 断言：验证第二行的max值为42
  } // 方法结束：aggMultiBuckets测试完成

  /**
   * Validate that property names which are reserved keywords ES
   * are correctly mapped (e.g. {@code type} or {@code properties})
   * 验证Elasticsearch保留关键字（如type或properties）作为属性名时能正确映射
   */
  @Test // JUnit5注解：标记为测试方法
  void reservedKeywordMapping() throws Exception { // 测试方法：测试保留关键字的映射处理
    // have special property names: type and properties
    ObjectNode mapping = mapper.readValue("{properties:{" // 定义JSON字符串，包含特殊的属性名：type和properties
        + "type:{type:'text'}," // 属性名为type，类型为text
        + "keyword:{type:'keyword'}," // 属性名为keyword，类型为keyword
        + "properties:{type:'long'}" // 属性名为properties，类型为long
        + "}}", ObjectNode.class); // 反序列化为ObjectNode
    Map<String, String> result = new HashMap<>(); // 创建HashMap，用于存储映射结果
    ElasticsearchJson.visitMappingProperties(mapping, result::put); // 访问映射属性，将结果放入result中

    assertThat(result.get("type"), is("text")); // 断言：验证type属性的类型为text
    assertThat(result.get("keyword"), is("keyword")); // 断言：验证keyword属性的类型为keyword
    assertThat(result.get("properties"), is("long")); // 断言：验证properties属性的类型为long
  } // 方法结束：reservedKeywordMapping测试完成


  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5974">[CALCITE-5974]
   * Elasticsearch adapter throws ClassCastException when index mapping sets
   * dynamic_templates without properties</a>.
   * 测试用例：验证当索引映射设置dynamic_templates而没有properties字段时，不会抛出ClassCastException
   */
  @Test // JUnit5注解：标记为测试方法
  void reservedEmptyPropertiesMapping() throws Exception { // 测试方法：测试没有properties字段的映射
    // have special property names: type and properties
    ObjectNode mapping = // 定义JSON字符串，包含dynamic_templates，但没有properties字段
        mapper.readValue("{dynamic_templates:[]" // dynamic_templates是ES的动态模板配置
            + "{integers:" // 模板名称为integers
            + "{match_mapping_type:'long',mapping:{type:'integer'}}" // 匹配long类型，映射为integer
            + "}]}", ObjectNode.class); // 反序列化为ObjectNode

    // The 'dynamic_templates' object has no 'properties' field,
    // so the result is empty.
    Map<String, String> result = new HashMap<>(); // 创建HashMap，用于存储映射结果
    ElasticsearchJson.visitMappingProperties(mapping, result::put); // 访问映射属性，由于没有properties字段，结果为空
    assertThat(result, anEmptyMap()); // 断言：验证结果为空Map
  } // 方法结束：reservedEmptyPropertiesMapping测试完成

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498]
   * Elasticsearch multi-field mappings do not work</a>.
   * 测试用例：验证多个单字段映射的解析
   */
  @Test // JUnit5注解：标记为测试方法
  void testVisitMappingPropertiesWithMultipleSingleFieldMappings() // 测试方法：测试多个单字段映射
      throws JsonProcessingException { // 可能抛出JSON处理异常
    ObjectNode mapping = // 定义JSON字符串，包含多个单字段映射
        mapper.readValue("{'properties':{" // properties字段包含多个属性定义
            + "'title':{'type':'text'}," // title字段类型为text
            + "'name':{'type':'keyword'}" // name字段类型为keyword
            + "}}", ObjectNode.class); // 反序列化为ObjectNode

    Map<String, String> result = getMappingAsMap(mapping); // 调用辅助方法获取映射结果

    assertThat(result.get("title"), is("text")); // 断言：验证title字段类型为text
    assertThat(result.get("name"), is("keyword")); // 断言：验证name字段类型为keyword
  } // 方法结束：testVisitMappingPropertiesWithMultipleSingleFieldMappings测试完成

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498]
   * Elasticsearch multi-field mappings do not work</a>.
   * 测试用例：验证多个多字段映射的解析（ES允许一个字段有多种类型）
   */
  @Test // JUnit5注解：标记为测试方法
  void testVisitMappingPropertiesWithMultipleMultiFieldMappings() // 测试方法：测试多个多字段映射
      throws Exception { // 可能抛出异常
    ObjectNode mapping = // 定义JSON字符串，包含多字段映射
        mapper.readValue("{'properties':{" // properties字段包含多字段定义
            + "'title':{'type':'text'," // title字段主类型为text
            +   "'fields':{'keyword':{'type': 'keyword'}}" // title.keyword子字段类型为keyword
            + "}," // title字段定义结束
            + "'name':{'type':'text'," // name字段主类型为text
            +   "'fields':{'name_keyword':{'type': 'keyword'}}" // name.name_keyword子字段类型为keyword
            + "}" // name字段定义结束
            + "}}", ObjectNode.class); // 反序列化为ObjectNode

    Map<String, String> result = getMappingAsMap(mapping); // 调用辅助方法获取映射结果

    assertThat(result.get("title"), is("text")); // 断言：验证title字段类型为text
    assertThat(result.get("title.keyword"), is("keyword")); // 断言：验证title.keyword子字段类型为keyword
    assertThat(result.get("name"), is("text")); // 断言：验证name字段类型为text
    assertThat(result.get("name.name_keyword"), is("keyword")); // 断言：验证name.name_keyword子字段类型为keyword
  } // 方法结束：testVisitMappingPropertiesWithMultipleMultiFieldMappings测试完成

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498]
   * Elasticsearch multi-field mappings do not work</a>.
   * 测试用例：验证多个嵌套字段映射的解析（nested类型用于嵌套对象）
   */
  @Test // JUnit5注解：标记为测试方法
  void testVisitMappingPropertiesWithMultipleNestedFieldMappings() // 测试方法：测试多个嵌套字段映射
      throws Exception { // 可能抛出异常
    ObjectNode mapping = // 定义JSON字符串，包含嵌套字段映射
        mapper.readValue("{properties:{" // properties字段包含嵌套对象定义
        + "'author':{'type':'nested'," // author字段类型为nested（嵌套对象）
        +   "'properties':{" // author对象的属性定义
        +     "'name':{'type':'text'}," // name字段类型为text
        +     "'age':{'type':'integer'}" // age字段类型为integer
        +   "}}," // author对象定义结束
        + "'address':{'type':'nested'," // address字段类型为nested（嵌套对象）
        +   "'properties':{" // address对象的属性定义
        +     "'street':{'type':'keyword'}," // street字段类型为keyword
        +     "'zip':{'type':'integer'}" // zip字段类型为integer
        +   "}}" // address对象定义结束
        + "}}", ObjectNode.class); // 反序列化为ObjectNode

    Map<String, String> result = getMappingAsMap(mapping); // 调用辅助方法获取映射结果

    assertThat(result.get("author"), is("nested")); // 断言：验证author字段类型为nested
    assertThat(result.get("author.name"), is("text")); // 断言：验证author.name字段类型为text
    assertThat(result.get("author.age"), is("integer")); // 断言：验证author.age字段类型为integer

    assertThat(result.get("address"), is("nested")); // 断言：验证address字段类型为nested
    assertThat(result.get("address.street"), is("keyword")); // 断言：验证address.street字段类型为keyword
    assertThat(result.get("address.zip"), is("integer")); // 断言：验证address.zip字段类型为integer
  } // 方法结束：testVisitMappingPropertiesWithMultipleNestedFieldMappings测试完成

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6498">[CALCITE-6498]
   * Elasticsearch multi-field mappings do not work</a>.
   * 测试用例：验证嵌套字段和多字段的组合映射解析
   */
  @Test // JUnit5注解：标记为测试方法
  void testVisitMappingPropertiesWithNestedAndMultiFieldMappings() // 测试方法：测试嵌套字段和多字段的组合
      throws Exception { // 可能抛出异常
    // 'title' is a multi-mapped field
    // 'author' is a nested field ('author.name' is multi-mapped)
    ObjectNode mapping = // 定义JSON字符串，包含嵌套字段和多字段的组合
        mapper.readValue("{properties:{" // properties字段包含字段定义
            + "'title':{'type':'text'," // title字段主类型为text
            +   "'fields':{'keyword':{'type': 'keyword'}}" // title.keyword子字段类型为keyword（多字段）
            + "}," // title字段定义结束
            + "'author':{'type':'nested'," // author字段类型为nested（嵌套对象）
            +   "'properties':{" // author对象的属性定义
            +     "'name':{'type':'text'," // name字段主类型为text
            +       "'fields':{'keyword':{'type': 'keyword'}}}," // name.keyword子字段类型为keyword（多字段）
            +     "'age':{'type':'integer'}" // age字段类型为integer
            +   "}" // author对象定义结束
            + "}}}", ObjectNode.class); // 反序列化为ObjectNode

    Map<String, String> result = getMappingAsMap(mapping); // 调用辅助方法获取映射结果

    // Checking the multi-field mapping
    assertThat(result.get("title"), is("text")); // 断言：验证title字段类型为text
    assertThat(result.get("title.keyword"), is("keyword")); // 断言：验证title.keyword子字段类型为keyword

    // Checking the nested mapping
    assertThat(result.get("author"), is("nested")); // 断言：验证author字段类型为nested
    assertThat(result.get("author.name"), is("text")); // 断言：验证author.name字段类型为text
    assertThat(result.get("author.name.keyword"), is("keyword")); // 断言：验证author.name.keyword子字段类型为keyword
    assertThat(result.get("author.age"), is("integer")); // 断言：验证author.age字段类型为integer
  } // 方法结束：testVisitMappingPropertiesWithNestedAndMultiFieldMappings测试完成

  private static Map<String, String> getMappingAsMap(ObjectNode mapping) { // 私有静态辅助方法：将映射转换为不可变Map
    // ImmutableMap.Builder makes sure that we don't add the same key twice
    // (would throw exception otherwise)
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder(); // 创建不可变Map的构建器，确保不会重复添加键
    ElasticsearchJson.visitMappingProperties(mapping, builder::put); // 访问映射属性，将结果放入构建器中

    return builder.build(); // 构建并返回不可变Map
  } // 方法结束：getMappingAsMap方法完成
} // 类结束：ElasticsearchJsonTest类定义结束
