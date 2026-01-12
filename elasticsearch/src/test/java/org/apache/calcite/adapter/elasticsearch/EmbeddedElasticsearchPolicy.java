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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，表示这个类属于Elasticsearch适配器包

import org.apache.calcite.util.Closer; // 导入Closer工具类，用于管理资源的关闭

import org.apache.http.HttpEntity; // 导入HTTP实体类，用于HTTP请求体
import org.apache.http.HttpHost; // 导入HTTP主机类，表示HTTP服务器地址
import org.apache.http.entity.ContentType; // 导入内容类型类，用于指定MIME类型
import org.apache.http.entity.StringEntity; // 导入字符串实体类，用于将字符串作为HTTP请求体

import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson的ObjectMapper，用于JSON序列化和反序列化
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson的ObjectNode，用于构建JSON对象

import org.elasticsearch.client.Request; // 导入Elasticsearch的Request类，用于构建REST请求
import org.elasticsearch.client.RestClient; // 导入Elasticsearch的RestClient，用于执行REST API调用
import org.elasticsearch.common.transport.TransportAddress; // 导入传输地址类，表示网络地址

import java.io.IOException; // 导入IO异常类
import java.util.ArrayList; // 导入ArrayList列表类
import java.util.List; // 导入List接口
import java.util.Locale; // 导入Locale类，用于本地化字符串格式化
import java.util.Map; // 导入Map接口

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数校验

/**
 * 用于初始化单个Elasticsearch节点的策略类。为了性能考虑（节点启动成本高），
 * 同一个实例在多个测试之间共享（Elasticsearch不允许在同一个JVM中运行多个实例）。
 *
 * <p>这个类的使用方式如下：
 *
 * <blockquote><pre><code>
 * public class MyTest {
 *   public static final EmbeddedElasticsearchPolicy RULE =
 *       EmbeddedElasticsearchPolicy.create();
 *
 *   &#64;BeforeClass
 *   public static void setup() {
 *      // ... 填充实例数据
 *      // 集合必须使用不同的名称，以便测试可以并发执行
 *   }
 *
 *   &#64;Test
 *   public void myTest() {
 *     RestClient client = RULE.restClient();
 *     // ....
 *   }
 * }
 * </code></pre></blockquote>
 */
class EmbeddedElasticsearchPolicy { // 定义嵌入式Elasticsearch策略类，用于在单元测试中管理ES节点

  private final EmbeddedElasticsearchNode node; // 嵌入式Elasticsearch节点实例，负责启动和管理ES节点
  private final ObjectMapper mapper; // Jackson的ObjectMapper实例，用于JSON数据的序列化和反序列化
  private final Closer closer; // Closer实例，用于统一管理资源的关闭，确保测试结束时正确释放资源
  private RestClient client; // Elasticsearch的REST客户端，用于执行HTTP请求与ES集群交互

  /** Holds the singleton policy instance. */ // 持有单例策略实例的内部类
  static class Singleton { // 单例内部类，使用静态内部类实现延迟加载的单例模式
    static final EmbeddedElasticsearchPolicy INSTANCE = // 静态常量，持有唯一的EmbeddedElasticsearchPolicy实例
        new EmbeddedElasticsearchPolicy(EmbeddedElasticsearchNode.create()); // 创建并初始化策略实例，传入ES节点
  }

  private EmbeddedElasticsearchPolicy(EmbeddedElasticsearchNode resource) { // 私有构造方法，防止外部直接实例化
    this.node = requireNonNull(resource, "resource"); // 校验资源参数不为空，并赋值给node成员变量
    this.node.start(); // 启动Elasticsearch节点，开始监听请求
    this.mapper = new ObjectMapper(); // 创建Jackson ObjectMapper实例，用于JSON处理
    this.closer = new Closer(); // 创建Closer实例，用于管理资源关闭
    closer.add(node); // 将ES节点添加到Closer中，确保测试结束时能正确关闭
    // initialize client // 初始化REST客户端
    restClient(); // 调用restClient()方法初始化REST客户端
  }

  /**
   * Factory method to create this rule. // 创建此规则的工厂方法
   *
   * @return managed resource to be used in unit tests // 返回用于单元测试的托管资源
   */
  public static EmbeddedElasticsearchPolicy create() { // 静态工厂方法，用于获取单例实例
    return Singleton.INSTANCE; // 返回单例实例
  }

  /**
   * Creates index in Elasticsearch given a mapping. Mapping can // 根据给定的映射在Elasticsearch中创建索引。映射可以
   * contain nested fields expressed as dots({@code .}). // 包含用点号({@code .})表示的嵌套字段。
   *
   * <p>Example: // 示例：
   *
   * <pre>{@code // 代码示例
   *     b.a: long // 嵌套字段b.a，类型为long
   *     b.b: keyword // 嵌套字段b.b，类型为keyword
   * }</pre>
   *
   * @param index index of the index // 索引名称
   * @param mapping field and field type mapping // 字段和字段类型的映射关系
   * @throws IOException if there is an error // 如果发生错误则抛出IO异常
   */
  void createIndex(String index, Map<String, String> mapping) throws IOException { // 创建索引方法，接收索引名和字段映射
    requireNonNull(index, "index"); // 校验索引参数不为空
    requireNonNull(mapping, "mapping"); // 校验映射参数不为空

    ObjectNode mappings = mapper().createObjectNode(); // 创建JSON对象节点，用于构建索引映射配置

    ObjectNode properties = mappings.withObject("/mappings") // 获取或创建mappings对象
        .withObject("/properties"); // 获取或创建properties对象，存储字段定义
    for (Map.Entry<String, String> entry : mapping.entrySet()) { // 遍历映射中的每个字段条目
      applyMapping(properties, entry.getKey(), entry.getValue()); // 应用字段映射，处理嵌套字段
    }

    // create index and mapping // 创建索引和映射
    final HttpEntity entity = // 创建HTTP实体，包含索引映射的JSON数据
        new StringEntity(mapper().writeValueAsString(mappings), // 将mappings对象序列化为JSON字符串
            ContentType.APPLICATION_JSON); // 设置内容类型为JSON
    final Request r = new Request("PUT", "/" + index); // 创建PUT请求，用于创建索引
    r.setEntity(entity); // 设置请求体为索引映射数据
    restClient().performRequest(r); // 执行请求，在ES中创建索引
  }

  /**
   * Creates alias in elastic search given an index. // 在Elasticsearch中为给定索引创建别名。
   * as dots({@code .}). // 使用点号({@code .})表示。
   *
   * <p>Example: // 示例：
   *
   * <pre>{@code // 代码示例
   *     b.a: long // 嵌套字段b.a，类型为long
   *     b.b: keyword // 嵌套字段b.b，类型为keyword
   * }</pre>
   *
   * @param index index of the index // 索引名称
   * @param alias alias of the index // 索引的别名
   * @throws IOException if there is an error // 如果发生错误则抛出IO异常
   */
  void createAlias(String index, String alias) throws IOException { // 创建别名方法，接收索引名和别名
    requireNonNull(index, "index"); // 校验索引参数不为空
    requireNonNull(alias, "alias"); // 校验别名参数不为空

    ObjectNode actions = mapper().createObjectNode(); // 创建JSON对象节点，用于构建别名操作配置

    ObjectNode properties = actions.withObject("/actions").withObject("/add"); // 获取或创建actions.add对象，表示添加别名操作
    properties.put("index", index); // 设置要添加别名的索引名称
    properties.put("alias", alias); // 设置别名名称

    // create alias // 创建别名
    final HttpEntity entity = // 创建HTTP实体，包含别名操作的JSON数据
        new StringEntity(mapper().writeValueAsString(actions), // 将actions对象序列化为JSON字符串
            ContentType.APPLICATION_JSON); // 设置内容类型为JSON
    final Request r = new Request("POST", "/_aliases"); // 创建POST请求，用于操作别名
    r.setEntity(entity); // 设置请求体为别名操作数据
    restClient().performRequest(r); // 执行请求，在ES中创建别名
  }

  /**
   * Creates nested mappings for an index. This function is called recursively for each level. // 为索引创建嵌套映射。此函数对每一层都会递归调用。
   *
   * @param parent current parent // 当前父节点
   * @param key field name // 字段名称
   * @param type ES mapping type ({@code keyword}, {@code long} etc.) // ES映射类型（如{@code keyword}、{@code long}等）
   */
  private static void applyMapping(ObjectNode parent, String key, String type) { // 应用映射方法，递归处理嵌套字段
    final int index = key.indexOf('.'); // 查找字段名中第一个点号的位置
    if (index > -1) { // 如果存在点号，说明是嵌套字段
      String prefix  = key.substring(0, index); // 提取点号前的字段名作为前缀
      String suffix = key.substring(index + 1); // 提取点号后的字段名作为后缀

      if ("nested".equals(parent.get(prefix).get("type").asText())) { // 检查前缀字段的类型是否为nested
        // Nested field mapping // 嵌套字段映射
        applyMapping(parent.withObject("/" + prefix).withObject("/properties"), // 递归调用，在嵌套对象的properties中处理
            suffix, type); // 传入后缀和类型
      } else { // 如果不是nested类型
        // Multi-field mapping // 多字段映射
        applyMapping(parent.withObject("/" + prefix).withObject("/fields"), // 递归调用，在字段的fields中处理
            suffix, type); // 传入后缀和类型
      }
    } else { // 如果不存在点号，说明是简单字段
      if ("text".equalsIgnoreCase(type)) { // 检查字段类型是否为text
        // aggregations and sorting are disabled by default for text field type // text字段类型默认禁用聚合和排序
        parent.withObject("/" + key).put("type", type).put("fielddata", "true"); // 设置字段类型并启用fielddata以支持聚合和排序
      } else { // 如果不是text类型
        parent.withObject("/" + key).put("type", type); // 直接设置字段类型
      }
    }
  }

  void insertDocument(String index, ObjectNode document) throws IOException { // 插入单个文档方法
    requireNonNull(index, "index"); // 校验索引参数不为空
    requireNonNull(document, "document"); // 校验文档参数不为空
    String uri = String.format(Locale.ROOT, "/%s/_doc?refresh", index); // 构建文档插入的URI，refresh参数表示立即刷新
    StringEntity entity = // 创建字符串实体，包含文档的JSON数据
        new StringEntity(mapper().writeValueAsString(document), // 将文档对象序列化为JSON字符串
            ContentType.APPLICATION_JSON); // 设置内容类型为JSON
    final Request r = new Request("POST", uri); // 创建POST请求，用于插入文档
    r.setEntity(entity); // 设置请求体为文档数据
    restClient().performRequest(r); // 执行请求，在ES中插入文档
  }

  void insertBulk(String index, List<ObjectNode> documents) throws IOException { // 批量插入文档方法
    requireNonNull(index, "index"); // 校验索引参数不为空
    requireNonNull(documents, "documents"); // 校验文档列表参数不为空

    if (documents.isEmpty()) { // 如果文档列表为空
      // nothing to process // 没有需要处理的内容
      return; // 直接返回
    }

    List<String> bulk = new ArrayList<>(documents.size() * 2); // 创建列表，用于存储批量操作的数据，大小为文档数量的两倍
    for (ObjectNode doc : documents) { // 遍历每个文档
      bulk.add(String.format(Locale.ROOT, "{\"index\": {\"_index\":\"%s\"}}", index)); // 添加索引操作元数据，指定目标索引
      bulk.add(mapper().writeValueAsString(doc)); // 添加文档的JSON数据
    }

    final StringEntity entity = // 创建字符串实体，包含批量操作的JSON数据
        new StringEntity(String.join("\n", bulk) + "\n", // 将所有操作用换行符连接，并在末尾添加换行符
            ContentType.APPLICATION_JSON); // 设置内容类型为JSON

    final Request r = new Request("POST", "/_bulk?refresh"); // 创建POST请求，用于批量操作，refresh参数表示立即刷新
    r.setEntity(entity); // 设置请求体为批量操作数据
    restClient().performRequest(r); // 执行请求，在ES中批量插入文档
  }

  /**
   * Exposes Jackson API to be used to parse search results. // 暴露Jackson API用于解析搜索结果。
   *
   * @return existing instance of ObjectMapper // 返回现有的ObjectMapper实例
   */
  ObjectMapper mapper() { // 获取ObjectMapper实例方法
    return mapper; // 返回mapper成员变量
  }

  /**
   * Low-level http rest client connected to current embedded Elasticsearch // 连接到当前嵌入式Elasticsearch实例的低级HTTP REST客户端
   * instance. // 实例。
   *
   * @return http client connected to ES cluster // 返回连接到ES集群的HTTP客户端
   */
  RestClient restClient() { // 获取REST客户端方法
    if (client != null) { // 如果客户端已经初始化
      return client; // 直接返回现有客户端
    }

    final RestClient client = RestClient.builder(httpHost()) // 使用HTTP主机地址构建REST客户端
        .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder // 配置请求参数
            .setConnectTimeout(60 * 1000)  // default 1000 // 设置连接超时时间为60秒（默认1秒）
            .setSocketTimeout(3 * 60 * 1000))  // default 30000 // 设置Socket超时时间为3分钟（默认30秒）
        .build(); // 构建REST客户端
    closer.add(client); // 将客户端添加到Closer中，确保测试结束时能正确关闭
    this.client = client; // 保存客户端实例到成员变量
    return client; // 返回REST客户端
  }

  HttpHost httpHost() { // 获取HTTP主机方法
    final TransportAddress address = httpAddress(); // 获取传输地址
    return new HttpHost(address.getAddress(), address.getPort()); // 使用传输地址创建并返回HTTP主机对象
  }

  /**
   * HTTP address for rest clients (can be ES native or any other). // REST客户端的HTTP地址（可以是ES原生或其他）。
   *
   * @return http address to connect to // 返回要连接的HTTP地址
   */
  private TransportAddress httpAddress() { // 获取HTTP地址方法
    return node.httpAddress(); // 返回ES节点的HTTP地址
  }
}
