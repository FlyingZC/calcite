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
package org.apache.calcite.adapter.elasticsearch;

import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;

import static java.util.Objects.requireNonNull;

/**
 * Each table in the schema is an ELASTICSEARCH index.
 */
// ElasticsearchSchema类：这是Calcite适配器中用于连接Elasticsearch的Schema实现类
// 继承自AbstractSchema，表示这是一个抽象的Schema实现，用于定义数据库模式的结构
// 在Calcite中，Schema是表的容器，类似于数据库中的schema或catalog
// 在这个实现中，Schema中的每个表都对应Elasticsearch中的一个索引(index)
// 该类负责管理与Elasticsearch的连接，并将ES索引映射为Calcite可识别的表
public class ElasticsearchSchema extends AbstractSchema {

  private final RestClient client; // RestClient对象：用于与Elasticsearch集群进行HTTP通信的REST客户端，负责发送请求和接收响应

  private final ObjectMapper mapper; // ObjectMapper对象：Jackson库的JSON序列化/反序列化工具，用于处理Elasticsearch返回的JSON数据

  private final Map<String, Table> tableMap; // tableMap映射表：存储表名到Table对象的映射关系，key是索引名，value是对应的ElasticsearchTable对象

  /**
   * Default batch size to be used during scrolling.
   */
  // fetchSize：在滚动查询(scroll)时使用的默认批次大小
  // Elasticsearch的scroll API用于获取大量数据时，每次返回一批结果
  // 这个参数控制每批返回的文档数量，影响查询性能和内存使用
  private final int fetchSize;

  /**
   * Allows schema to be instantiated from existing elastic search client.
   *
   * @param client existing client instance
   * @param mapper mapper for JSON (de)serialization
   * @param index name of ES index
   */
  // 构造方法1：使用现有的Elasticsearch客户端创建Schema实例
  // 这是一个公共构造方法，允许外部传入已配置好的RestClient来创建Schema
  // @param client：已存在的Elasticsearch REST客户端实例，用于与ES集群通信
  // @param mapper：Jackson的ObjectMapper实例，用于JSON数据的序列化和反序列化
  // @param index：Elasticsearch索引名称，如果为null则自动发现所有索引
  public ElasticsearchSchema(RestClient client, ObjectMapper mapper,
      @Nullable String index) {
    this(client, mapper, index, ElasticsearchTransport.DEFAULT_FETCH_SIZE); // 调用另一个构造方法，使用默认的fetchSize值(DEFAULT_FETCH_SIZE)来初始化Schema
  }

  @VisibleForTesting // 可见性注解：标记该方法为可见性测试用，允许在测试类中访问这个构造方法
  // 构造方法2：完整的构造方法，允许自定义fetchSize参数
  // @param client：Elasticsearch REST客户端实例
  // @param mapper：Jackson的ObjectMapper实例
  // @param index：Elasticsearch索引名称，如果为null则自动发现所有索引
  // @param fetchSize：滚动查询时每批返回的文档数量
  ElasticsearchSchema(RestClient client, ObjectMapper mapper,
      @Nullable String index, int fetchSize) {
    super(); // 调用父类AbstractSchema的构造方法
    this.client = requireNonNull(client, "client"); // 验证client参数不为null，如果为null则抛出NullPointerException
    this.mapper = requireNonNull(mapper, "mapper"); // 验证mapper参数不为null，如果为null则抛出NullPointerException
    checkArgument(fetchSize > 0, // 验证fetchSize参数必须大于0，否则抛出IllegalArgumentException
        "invalid fetch size. Expected %s > 0", fetchSize);
    this.fetchSize = fetchSize; // 保存fetchSize参数到成员变量

    if (index == null) { // 如果index参数为null，表示需要自动发现Elasticsearch中的所有索引
      try {
        this.tableMap = createTables(indicesFromElastic()); // 从Elasticsearch获取所有索引列表，并为每个索引创建Table对象
      } catch (IOException e) { // 捕获IO异常
        throw new UncheckedIOException("Couldn't get indices", e); // 将受检的IOException转换为非受检的UncheckedIOException
      }
    } else { // 如果指定了index参数
      this.tableMap = createTables(Collections.singleton(index)); // 只为指定的单个索引创建Table对象
    }
  }

  @Override protected Map<String, Table> getTableMap() { // 重写父类AbstractSchema的getTableMap方法，返回Schema中的表映射
    return tableMap; // 返回已初始化的tableMap，其中包含了所有索引到Table对象的映射关系
  }

  // createTables方法：根据给定的索引列表创建Table映射
// 该方法为每个Elasticsearch索引创建一个ElasticsearchTable对象，并将其添加到映射表中
// @param indices：Elasticsearch索引名称的可迭代集合
// @return：包含索引名到Table对象映射的不可变Map
private Map<String, Table> createTables(Iterable<String> indices) {
    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建不可变Map的构建器，用于构建最终的tableMap
    for (String index : indices) { // 遍历每个索引名称
      final ElasticsearchTransport transport = // 为当前索引创建ElasticsearchTransport对象，该对象负责与Elasticsearch进行数据传输
          new ElasticsearchTransport(client, mapper, index, fetchSize);
      builder.put(index, new ElasticsearchTable(transport)); // 创建ElasticsearchTable对象，并使用索引名作为key添加到构建器中
    }
    return builder.build(); // 构建并返回不可变的Map，包含所有索引到Table的映射
  }

  /**
   * Queries {@code _alias} definition to automatically detect all indices.
   *
   * @return list of indices
   * @throws IOException for any IO related issues
   * @throws IllegalStateException if reply is not understood
   */
  // indicesFromElastic方法：从Elasticsearch自动发现所有可用的索引
  // 该方法通过查询Elasticsearch的_alias端点来获取集群中的所有索引和别名
  // 返回的集合包含所有索引名和别名，都作为可用的表名
  // @return：包含所有索引名和别名的Set集合
  // @throws IOException：当与Elasticsearch通信发生IO错误时抛出
  // @throws IllegalStateException：当Elasticsearch返回的响应格式不符合预期时抛出
  private Set<String> indicesFromElastic() throws IOException {
    final String endpoint = "/_alias"; // 定义Elasticsearch的_alias端点，该端点返回所有索引及其别名信息
    final Response response = client.performRequest(new Request("GET", endpoint)); // 向Elasticsearch发送GET请求，获取所有索引的别名信息
    try (InputStream is = response.getEntity().getContent()) { // 使用try-with-resources自动关闭响应的输入流
      final JsonNode root = mapper.readTree(is); // 使用Jackson的ObjectMapper将响应的JSON数据解析为JsonNode树结构
      if (!root.isObject() || root.isEmpty()) { // 验证响应根节点是否为非空对象，如果不是则抛出异常
        final String message = String.format(Locale.ROOT, "Invalid response for %s/%s " // 构建错误消息，包含主机信息、请求行、节点类型和大小
            + "Expected object of at least size 1 got %s (of size %d)", response.getHost(),
            response.getRequestLine(), root.getNodeType(), root.size());
        throw new IllegalStateException(message); // 抛出IllegalStateException，表示响应格式不符合预期
      }

      Set<String> indices = Sets.newHashSet(root.fieldNames()); // 从根节点获取所有字段名（即索引名），并创建一个新的HashSet
      Set<String> aliases = root.findValues("aliases").stream() // 查找所有"aliases"节点，并转换为流进行处理
          .map(JsonNode::fieldNames) // 对每个aliases节点获取其字段名（即别名）
          .flatMap( // 将多个别名集合扁平化为一个流
              it -> StreamSupport.stream(
                  Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED), false))
          .collect(Collectors.toSet()); // 将流收集为Set集合，包含所有别名
      indices.addAll(aliases); // 将所有别名添加到索引集合中，这样别名也可以作为表名使用
      return indices; // 返回包含所有索引名和别名的集合
    }
  }

}
