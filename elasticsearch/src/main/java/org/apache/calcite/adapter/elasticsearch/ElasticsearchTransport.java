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
package org.apache.calcite.adapter.elasticsearch; // 包声明：Elasticsearch适配器包，包含Elasticsearch相关的适配器类

import org.apache.calcite.runtime.Hook; // 导入Hook类，用于查询计划的钩子功能

import org.apache.http.HttpEntity; // 导入HttpEntity接口，表示HTTP请求或响应的实体
import org.apache.http.HttpEntityEnclosingRequest; // 导入HttpEntityEnclosingRequest接口，表示包含实体的HTTP请求
import org.apache.http.HttpRequest; // 导入HttpRequest接口，表示HTTP请求
import org.apache.http.HttpStatus; // 导入HttpStatus类，包含HTTP状态码常量
import org.apache.http.client.methods.HttpDelete; // 导入HttpDelete类，表示HTTP DELETE请求
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase; // 导入HttpEntityEnclosingRequestBase类，包含实体的HTTP请求基类
import org.apache.http.client.methods.HttpGet; // 导入HttpGet类，表示HTTP GET请求
import org.apache.http.client.methods.HttpPost; // 导入HttpPost类，表示HTTP POST请求
import org.apache.http.client.utils.URIBuilder; // 导入URIBuilder类，用于构建URI
import org.apache.http.entity.ContentType; // 导入ContentType类，表示内容类型
import org.apache.http.entity.StringEntity; // 导入StringEntity类，表示字符串实体
import org.apache.http.util.EntityUtils; // 导入EntityUtils类，提供实体工具方法

import com.fasterxml.jackson.core.JsonProcessingException; // 导入JsonProcessingException类，JSON处理异常
import com.fasterxml.jackson.databind.ObjectMapper; // 导入ObjectMapper类，Jackson的JSON序列化/反序列化核心类
import com.fasterxml.jackson.databind.node.ArrayNode; // 导入ArrayNode类，表示JSON数组节点
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入ObjectNode类，表示JSON对象节点
import com.fasterxml.jackson.databind.node.TextNode; // 导入TextNode类，表示JSON文本节点
import com.google.common.collect.ImmutableMap; // 导入ImmutableMap类，Google Guava的不可变Map

import org.elasticsearch.client.Request; // 导入Request类，Elasticsearch客户端的请求类
import org.elasticsearch.client.Response; // 导入Response类，Elasticsearch客户端的响应类
import org.elasticsearch.client.RestClient; // 导入RestClient类，Elasticsearch的REST客户端
import org.slf4j.Logger; // 导入Logger接口，SLF4J日志接口
import org.slf4j.LoggerFactory; // 导入LoggerFactory类，SLF4J日志工厂

import java.io.IOException; // 导入IOException类，IO异常
import java.io.InputStream; // 导入InputStream类，输入流
import java.io.UncheckedIOException; // 导入UncheckedIOException类，未检查的IO异常
import java.net.URI; // 导入URI类，统一资源标识符
import java.net.URISyntaxException; // 导入URISyntaxException类，URI语法异常
import java.util.Collections; // 导入Collections类，集合工具类
import java.util.Locale; // 导入Locale类，地区设置
import java.util.Map; // 导入Map接口，映射接口
import java.util.function.Function; // 导入Function接口，函数式接口
import java.util.stream.StreamSupport; // 导入StreamSupport类，流支持工具类

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于参数非空验证

/**
 * Set of predefined functions for REST interaction with elastic search API. Performs
 * HTTP requests and JSON (de)serialization.
 */
// ElasticsearchTransport类：用于与Elasticsearch REST API进行交互的传输层类，负责执行HTTP请求和JSON序列化/反序列化操作
final class ElasticsearchTransport {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchTable.class); // 日志记录器，用于记录ElasticsearchTransport类的运行日志

  static final int DEFAULT_FETCH_SIZE = 5196; // 默认的批量获取大小，用于滚动查询时每次获取的文档数量

  private final ObjectMapper mapper; // Jackson的ObjectMapper对象，用于JSON数据的序列化和反序列化操作
  private final RestClient restClient; // Elasticsearch的REST客户端，用于执行HTTP请求与Elasticsearch集群进行通信

  final String indexName; // Elasticsearch索引名称，表示要操作的索引

  final ElasticsearchVersion version; // Elasticsearch版本信息，缓存在构造函数中，用于适配不同版本的API差异

  final ElasticsearchMapping mapping; // Elasticsearch索引映射信息，缓存在构造函数中，包含索引的字段类型等元数据

  /**
   * Default batch size.
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-scroll.html">Scrolling API</a>
   */
  final int fetchSize; // 批量获取大小，用于滚动查询时每次获取的文档数量，参考Elasticsearch的Scrolling API文档

  ElasticsearchTransport(final RestClient restClient, // 构造方法：初始化ElasticsearchTransport实例
      final ObjectMapper mapper, // 参数：Jackson的ObjectMapper对象，用于JSON处理
      final String indexName, // 参数：Elasticsearch索引名称
      final int fetchSize) { // 参数：批量获取大小
    this.mapper = requireNonNull(mapper, "mapper"); // 验证mapper不为空并赋值
    this.restClient = requireNonNull(restClient, "restClient"); // 验证restClient不为空并赋值
    this.indexName = requireNonNull(indexName, "indexName"); // 验证indexName不为空并赋值
    this.fetchSize = fetchSize; // 设置批量获取大小
    this.version = version(); // 调用version()方法获取并缓存Elasticsearch版本信息
    this.mapping = fetchAndCreateMapping(); // 调用fetchAndCreateMapping()方法获取并缓存索引映射信息
  }

  RestClient restClient() { // 方法：获取Elasticsearch REST客户端实例
    return this.restClient; // 返回内部的REST客户端对象
  }

  /**
   * Detects current Elastic Search version by connecting to a existing instance.
   * It is a {@code GET} request to {@code /}. Returned JSON has server information
   * (including version).
   *
   * @return parsed version from ES, or {@link ElasticsearchVersion#UNKNOWN}
   */
  private ElasticsearchVersion version() { // 方法：通过连接到Elasticsearch实例检测当前版本，发送GET请求到根路径获取服务器信息
    final HttpRequest request = new HttpGet("/"); // 创建HTTP GET请求，访问Elasticsearch根路径
    // version extract function
    final Function<ObjectNode, ElasticsearchVersion> fn = // 创建版本提取函数，从JSON响应中提取版本号
        node -> ElasticsearchVersion.fromString( // 调用ElasticsearchVersion的fromString方法解析版本字符串
            node.get("version").get("number").asText()); // 从JSON节点中获取version.number字段的值
    return rawHttp(ObjectNode.class) // 获取原始HTTP请求函数，期望返回类型为ObjectNode
        .andThen(fn) // 链接版本提取函数
        .apply(request); // 应用到HTTP请求上并返回解析后的版本信息
  }

  /**
   * Build index mapping returning new instance of {@link ElasticsearchMapping}.
   */
  private ElasticsearchMapping fetchAndCreateMapping() { // 方法：获取索引映射信息并创建ElasticsearchMapping实例
    final String uri = String.format(Locale.ROOT, "/%s/_mapping", indexName); // 构建获取索引映射的URI路径
    final ObjectNode root = rawHttp(ObjectNode.class).apply(new HttpGet(uri)); // 发送HTTP GET请求获取索引映射的JSON数据
    ObjectNode properties = (ObjectNode) root.elements().next().get("mappings"); // 从JSON根节点中提取mappings属性

    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder(); // 创建不可变Map构建器，用于存储字段名和类型的映射关系
    ElasticsearchJson.visitMappingProperties(properties, builder::put); // 遍历映射属性，将字段名和类型添加到构建器中
    return new ElasticsearchMapping(indexName, builder.build()); // 创建并返回ElasticsearchMapping实例，包含索引名和字段映射
  }

  ObjectMapper mapper() { // 方法：获取Jackson ObjectMapper实例
    return mapper; // 返回内部的ObjectMapper对象
  }

  Function<HttpRequest, Response> rawHttp() { // 方法：返回一个函数，该函数执行原始HTTP请求并返回Response对象
    return new HttpFunction(restClient); // 创建并返回HttpFunction实例，封装了HTTP请求执行逻辑
  }

  <T> Function<HttpRequest, T> rawHttp(Class<T> responseType) { // 方法：返回一个函数，执行HTTP请求并将响应解析为指定类型
    requireNonNull(responseType, "responseType"); // 验证responseType参数不为空
    return rawHttp().andThen(new JsonParserFn<>(mapper, responseType)); // 链接HTTP请求函数和JSON解析函数
  }

  /**
   * Fetches search results given a scrollId.
   */
  Function<String, ElasticsearchJson.Result> scroll() { // 方法：返回一个函数，根据scrollId获取滚动查询的下一批结果
    return scrollId -> { // 函数实现：接收scrollId作为参数
      // fetch next scroll
      final HttpPost request = new HttpPost(URI.create("/_search/scroll")); // 创建HTTP POST请求到/_search/scroll端点
      final ObjectNode payload = mapper.createObjectNode() // 创建JSON负载对象
          .put("scroll", "1m") // 设置滚动上下文保持时间为1分钟
          .put("scroll_id", scrollId); // 设置滚动ID

      try {
        final String json = mapper.writeValueAsString(payload); // 将负载对象转换为JSON字符串
        request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON)); // 设置请求体为JSON格式
        return rawHttp(ElasticsearchJson.Result.class).apply(request); // 执行HTTP请求并解析响应为Result对象
      } catch (IOException e) { // 捕获IO异常
        String message = String.format(Locale.ROOT, "Couldn't fetch next scroll %s", scrollId); // 构建错误消息
        throw new UncheckedIOException(message, e); // 抛出未检查的IO异常
      }
    };

  }

  void closeScroll(Iterable<String> scrollIds) { // 方法：关闭指定的滚动上下文，释放服务器资源
    requireNonNull(scrollIds, "scrollIds"); // 验证scrollIds参数不为空

    // delete current scroll
    final URI uri = URI.create("/_search/scroll"); // 创建删除滚动上下文的URI
    // http DELETE with payload
    final HttpEntityEnclosingRequestBase request = new HttpEntityEnclosingRequestBase() { // 创建自定义的HTTP请求，支持DELETE方法带请求体
      @Override public String getMethod() { // 重写getMethod方法返回DELETE方法名
        return HttpDelete.METHOD_NAME; // 返回DELETE方法标识
      }
    };

    request.setURI(uri); // 设置请求的URI
    final ObjectNode payload = mapper().createObjectNode(); // 创建JSON负载对象
    // ES2 expects json array for DELETE scroll API
    final ArrayNode array = payload.withArray("scroll_id"); // 创建scroll_id数组，Elasticsearch 2.x版本要求数组格式

    StreamSupport.stream(scrollIds.spliterator(), false) // 将scrollIds转换为流
        .map(TextNode::new) // 将每个scrollId转换为TextNode
        .forEach(array::add); // 将所有scrollId添加到数组中

    try {
      final String json = mapper().writeValueAsString(payload); // 将负载对象转换为JSON字符串
      request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON)); // 设置请求体为JSON格式
      @SuppressWarnings("unused")
      Response response = rawHttp().apply(request); // 执行HTTP请求关闭滚动上下文
    } catch (IOException | UncheckedIOException e) { // 捕获IO异常
      LOGGER.warn("Failed to close scroll(s): {}", scrollIds, e); // 记录警告日志，不中断流程
    }
  }

  Function<ObjectNode, ElasticsearchJson.Result> search() { // 方法：返回一个函数，执行搜索查询（无HTTP参数）
    return search(Collections.emptyMap()); // 调用带参数的search方法，传入空的HTTP参数Map
  }

  /**
   * Search request using HTTP post.
   */
  Function<ObjectNode, ElasticsearchJson.Result> search(final Map<String, String> httpParams) { // 方法：返回一个函数，执行搜索查询（带HTTP参数）
    requireNonNull(httpParams, "httpParams"); // 验证httpParams参数不为空
    return query -> { // 函数实现：接收查询条件作为参数
      Hook.QUERY_PLAN.run(query); // 运行查询计划钩子，用于调试和监控
      String path = String.format(Locale.ROOT, "/%s/_search", indexName); // 构建搜索API的路径
      final HttpPost post; // 声明HTTP POST请求对象
      try {
        URIBuilder builder = new URIBuilder(path); // 创建URI构建器
        httpParams.forEach(builder::addParameter); // 将所有HTTP参数添加到URI中
        post = new HttpPost(builder.build()); // 创建HTTP POST请求
        final String json = mapper.writeValueAsString(query); // 将查询对象转换为JSON字符串
        LOGGER.debug("Elasticsearch Query: {}", json); // 记录调试日志输出查询内容
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON)); // 设置请求体为JSON格式
      } catch (URISyntaxException e) { // 捕获URI语法异常
        throw new RuntimeException(e); // 抛出运行时异常
      } catch (JsonProcessingException e) { // 捕获JSON处理异常
        throw new UncheckedIOException(e); // 抛出未检查的IO异常
      }

      return rawHttp(ElasticsearchJson.Result.class).apply(post); // 执行HTTP请求并解析响应为Result对象
    };
  }

  /**
   * Parses HTTP response into some class using jackson API.
   *
   * @param <T> result type
   */
  private static class JsonParserFn<T> implements Function<Response, T> { // 内部类：使用Jackson API将HTTP响应解析为指定类型的对象
    private final ObjectMapper mapper; // Jackson ObjectMapper实例，用于JSON解析
    private final Class<T> klass; // 目标类型的Class对象

    JsonParserFn(final ObjectMapper mapper, final Class<T> klass) { // 构造方法：初始化JsonParserFn实例
      this.mapper = mapper; // 设置ObjectMapper实例
      this.klass = klass; // 设置目标类型Class对象
    }

    @Override public T apply(final Response response) { // 方法：将HTTP响应解析为指定类型的对象
      try (InputStream is = response.getEntity().getContent()) { // 获取响应实体的输入流并使用try-with-resources自动关闭
        return mapper.readValue(is, klass); // 使用ObjectMapper将JSON流读取为指定类型的对象
      } catch (IOException e) { // 捕获IO异常
        final String message = // 构建错误消息
            String.format(Locale.ROOT,
                "Couldn't parse HTTP response %s into %s", response, klass); // 格式化错误信息
        throw new UncheckedIOException(message, e); // 抛出未检查的IO异常
      }
    }
  }

  /**
   * Basic rest operations interacting with elastic cluster.
   */
  private static class HttpFunction implements Function<HttpRequest, Response> { // 内部类：封装与Elasticsearch集群交互的基本REST操作

    private final RestClient restClient; // Elasticsearch REST客户端实例

    HttpFunction(final RestClient restClient) { // 构造方法：初始化HttpFunction实例
      this.restClient = requireNonNull(restClient, "restClient"); // 验证restClient不为空并赋值
    }

    @Override public Response apply(final HttpRequest request) { // 方法：执行HTTP请求并返回响应
      try {
        return applyInternal(request); // 调用内部方法执行请求
      } catch (IOException e) { // 捕获IO异常
        throw new UncheckedIOException(e); // 抛出未检查的IO异常
      }
    }

    private Response applyInternal(final HttpRequest request) // 方法：内部方法，执行HTTP请求的具体逻辑
        throws IOException  { // 声明可能抛出IO异常

      requireNonNull(request, "request"); // 验证request参数不为空
      final HttpEntity entity = request instanceof HttpEntityEnclosingRequest // 判断请求是否包含请求体
          ? ((HttpEntityEnclosingRequest) request).getEntity() : null; // 如果是则获取请求体，否则为null

      final Request r = // 创建Elasticsearch的Request对象
          new Request(request.getRequestLine().getMethod(), // 获取HTTP方法（GET/POST/DELETE等）
              request.getRequestLine().getUri()); // 获取请求URI
      r.setEntity(entity); // 设置请求实体
      final Response response = restClient.performRequest(r); // 执行请求并获取响应

      final String payload = entity != null && entity.isRepeatable() // 判断实体是否可重复读取
          ? EntityUtils.toString(entity) : "<empty>"; // 如果可重复则转换为字符串，否则标记为空

      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) { // 检查响应状态码是否为200
        final String error = EntityUtils.toString(response.getEntity()); // 获取响应错误信息

        final String message = // 构建详细的错误消息
            String.format(Locale.ROOT,
                "Error while querying Elastic (on %s/%s) status: %s\n" // 包含主机、请求行、状态码
                    + "Payload:\n" // 请求体部分
                    + "%s\n" // 请求体内容
                    + "Error:\n" // 错误信息部分
                    + "%s\n", // 错误详情
                response.getHost(), response.getRequestLine(), // 主机和请求行
                response.getStatusLine(), payload, error); // 状态行、请求体和错误信息
        throw new RuntimeException(message); // 抛出运行时异常
      }

      return response; // 返回成功的响应
    }
  }
}
