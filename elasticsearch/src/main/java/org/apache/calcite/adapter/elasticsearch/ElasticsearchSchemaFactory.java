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
// Apache Calcite Elasticsearch适配器的Schema工厂类，用于创建ElasticsearchSchema
// 该类实现了SchemaFactory接口，是Calcite连接Elasticsearch数据源的入口点
// 负责根据配置参数创建Elasticsearch客户端并构建相应的Schema对象
package org.apache.calcite.adapter.elasticsearch;

import org.apache.calcite.schema.Schema; // Calcite Schema接口，表示数据库模式
import org.apache.calcite.schema.SchemaFactory; // Calcite Schema工厂接口，用于创建Schema实例
import org.apache.calcite.schema.SchemaPlus; // SchemaPlus接口，扩展的Schema接口，支持嵌套Schema
import org.apache.calcite.util.UnsafeX509ExtendedTrustManager; // 不安全的X509信任管理器，用于禁用SSL验证

import org.apache.http.HttpHost; // HTTP主机对象，表示ES服务器地址和端口
import org.apache.http.auth.AuthScope; // 认证范围对象，定义认证适用的范围
import org.apache.http.auth.UsernamePasswordCredentials; // 用户名密码凭证对象
import org.apache.http.client.CredentialsProvider; // 凭证提供者接口，用于管理HTTP认证凭证
import org.apache.http.impl.client.BasicCredentialsProvider; // 基础凭证提供者实现

import com.fasterxml.jackson.core.JsonParser; // Jackson JSON解析器
import com.fasterxml.jackson.core.type.TypeReference; // Jackson类型引用，用于泛型反序列化
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson对象映射器，用于JSON序列化和反序列化
import com.google.common.base.Strings; // Google Guava字符串工具类
import com.google.common.cache.Cache; // Google Guava缓存接口
import com.google.common.cache.CacheBuilder; // Google Guava缓存构建器
import com.google.common.cache.RemovalListener; // 缓存移除监听器接口
import com.google.common.cache.RemovalNotification; // 缓存移除通知对象
import com.google.common.collect.ImmutableList; // Google Guava不可变列表类

import org.elasticsearch.client.RestClient; // Elasticsearch REST客户端
import org.elasticsearch.client.RestClientBuilder; // Elasticsearch REST客户端构建器
import org.slf4j.Logger; // SLF4J日志接口
import org.slf4j.LoggerFactory; // SLF4J日志工厂类

import java.io.IOException; // IO异常类
import java.security.KeyManagementException; // 密钥管理异常
import java.security.NoSuchAlgorithmException; // 无此算法异常
import java.util.Comparator; // 比较器接口
import java.util.List; // 列表接口
import java.util.Locale; // 地区设置类
import java.util.Map; // 映射接口
import java.util.concurrent.Callable; // 可调用接口
import java.util.concurrent.ExecutionException; // 执行异常
import java.util.stream.Collectors; // 流收集器工具类
import javax.net.ssl.SSLContext; // SSL上下文类
import javax.net.ssl.TrustManager; // 信任管理器接口

import static com.google.common.base.Preconditions.checkArgument; // 参数校验工具方法

import static java.util.Objects.requireNonNull; // 对象非空校验工具方法

/**
 * Elasticsearch Schema工厂类，用于创建ElasticsearchSchema实例
 *
 * <p>该类实现了SchemaFactory接口，允许在model.json文件中包含自定义的Elasticsearch schema配置
 * 通过解析JSON配置参数，创建连接到Elasticsearch集群的REST客户端，并构建相应的Schema对象
 * 该Schema对象可以被Calcite查询优化器使用，将SQL查询转换为Elasticsearch查询
 */
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明警告，因为该类通过反射被调用
public class ElasticsearchSchemaFactory implements SchemaFactory {

  // 日志记录器，用于记录工厂类的运行时信息和错误
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchSchemaFactory.class);

  // REST客户端缓存的最大数量，限制为100个以防止资源耗尽
  // 这个限制是为了防止因Calcite无法关闭创建的客户端而导致的资源泄漏
  private static final int REST_CLIENT_CACHE_SIZE = 100;

  // RestClient对象会分配系统资源（如文件描述符）并且是线程安全的
  // 这里使用从定义RestClient的参数派生的键来缓存它们
  // 这样做的主要原因是限制Calcite当前无法关闭其创建的客户端而导致的资源泄漏
  // 泄漏的OS资源包括文件描述符，在Linux上默认每个进程限制为1024个
  // 该缓存使用Guava Cache实现，当缓存满时会自动移除最旧的客户端并关闭其资源
  private static final Cache<List, RestClient> REST_CLIENTS = CacheBuilder.newBuilder()
      .maximumSize(REST_CLIENT_CACHE_SIZE) // 设置缓存最大容量为100
      .removalListener(new RemovalListener<List, RestClient>() { // 设置缓存移除监听器
        @Override public void onRemoval(RemovalNotification<List, RestClient> notice) { // 当缓存项被移除时调用
          LOGGER.warn( // 记录警告日志
              "Will close an ES REST client to keep the number of open clients under {}. " // 将关闭ES REST客户端以保持打开的客户端数量在{}以下
              + "Any schema objects that might still have been relying on this client are now " // 任何可能仍依赖此客户端的schema对象现在都已损坏
              + "broken! Do not try to access more than {} distinct ES REST APIs through this " // 不要尝试通过此适配器访问超过{}个不同的ES REST API
              + "adapter.",
              REST_CLIENT_CACHE_SIZE,
              REST_CLIENT_CACHE_SIZE);

          try {
            // 释放此RestClient分配的资源，关闭网络连接
            notice.getValue().close();
          } catch (IOException ex) {
            LOGGER.warn("Could not close RestClient {}", notice.getValue(), ex); // 记录关闭失败的警告
          }
        }
      })
      .build(); // 构建缓存实例

  // 默认构造方法，通过反射调用
  // 不需要任何初始化参数，所有配置都从create方法的operand参数中获取
  public ElasticsearchSchemaFactory() {
  }

  /**
   * 创建Elasticsearch Schema实例
   * 该方法是SchemaFactory接口的核心方法，根据配置参数创建ElasticsearchSchema对象
   * operand参数接受以下键值对配置：
   *
   * <ul>
   *   <li><b>username</b>: ES集群的用户名，用于认证</li>
   *   <li><b>password</b>: ES集群的密码，用于认证</li>
   *   <li><b>hosts</b>: ES集群的主机列表，格式为["host1:port1","host2:port2"]，与coordinates二选一</li>
   *   <li><b>coordinates</b>: ES集群的坐标列表，格式为{"host1":port1,"host2":port2}，已废弃，建议使用hosts</li>
   *   <li><b>pathPrefix</b>: ES集群的路径前缀，用于代理或网关场景</li>
   *   <li><b>disableSSLVerification</b>: 布尔参数，是否禁用SSL验证，默认为false，生产环境应始终为false</li>
   *   <li><b>index</b>: 要访问的ES索引名称</li>
   * </ul>
   *
   * @param parentSchema 父Schema对象，用于构建Schema层次结构
   * @param name 当前Schema的名称
   * @param operand 从model.json文件中读取的"operand" JSON属性，包含ES连接配置
   * @return 返回ES集群的Schema对象，该对象包含ES索引的元数据和查询能力
   */
  @Override public Schema create(SchemaPlus parentSchema, String name,
      Map<String, Object> operand) {

    // 将operand转换为Map类型，用于后续的配置参数提取
    final Map map = (Map) operand;

    // 创建Jackson ObjectMapper对象，用于解析JSON配置
    final ObjectMapper mapper = new ObjectMapper();
    // 配置JSON解析器允许使用单引号，提高配置灵活性
    mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

    try { // 尝试解析配置并创建Schema

      List<HttpHost> hosts; // 声明ES主机列表变量

      // 检查配置中是否包含"hosts"参数（推荐方式）
      if (map.containsKey("hosts")) {
        // 从配置中读取hosts字符串并解析为字符串列表
        final List<String> configHosts =
            mapper.readValue((String) map.get("hosts"),
                new TypeReference<List<String>>() { });

        // 将字符串列表转换为HttpHost对象列表
        hosts =
            configHosts.stream() // 创建流
                .map(host -> HttpHost.create(host)) // 将每个主机字符串转换为HttpHost对象
                .collect(Collectors.toList()); // 收集为列表
      } else if (map.containsKey("coordinates")) { // 检查配置中是否包含"coordinates"参数（已废弃）
        // 从配置中读取coordinates字符串并解析为Map<主机名, 端口>
        final Map<String, Integer> coordinates =
                mapper.readValue((String) map.get("coordinates"),
                    new TypeReference<Map<String, Integer>>() { });

        // 将坐标Map转换为HttpHost对象列表
        hosts =
            coordinates.entrySet() // 获取所有键值对
                .stream() // 创建流
                .map(entry -> new HttpHost(entry.getKey(), entry.getValue())) // 将每个条目转换为HttpHost对象
                .collect(Collectors.toList()); // 收集为列表

        LOGGER.warn("Prefer using hosts, coordinates is deprecated."); // 记录警告，建议使用hosts而不是coordinates
      } else { // 如果既没有hosts也没有coordinates，抛出异常
        throw new IllegalArgumentException
        ("Both 'coordinates' and 'hosts' is missing in configuration. Provide one of them.");
      }
      // 对主机列表进行排序，确保连接顺序的一致性
      List<HttpHost> sortedHost = getSortedHost(hosts);

      // 从配置中获取路径前缀，用于代理或网关场景
      final String pathPrefix = (String) map.get("pathPrefix");

      // 启用或禁用SSL验证
      boolean disableSSLVerification; // 声明SSL验证标志
      if (map.containsKey("disableSSLVerification")) { // 检查是否配置了SSL验证选项
        String temp = (String) map.get("disableSSLVerification"); // 获取配置值
        // 将字符串转换为布尔值，转换为小写以兼容各种大小写写法
        disableSSLVerification = Boolean.getBoolean(temp.toLowerCase(Locale.ROOT));
      } else { // 如果没有配置，默认不禁用SSL验证
        disableSSLVerification = false;
      }

      // 创建ES REST客户端
      String username = (String) map.get("username"); // 获取用户名
      String password = (String) map.get("password"); // 获取密码
      // 调用connect方法创建或获取缓存的REST客户端
      final RestClient client =
          connect(sortedHost, pathPrefix, username, password, disableSSLVerification);
      final String index = (String) map.get("index"); // 获取要访问的索引名称

      // 创建并返回ElasticsearchSchema对象，传入客户端、ObjectMapper和索引名称
      return new ElasticsearchSchema(client, new ObjectMapper(), index);
    } catch (IOException e) { // 捕获JSON解析异常
      throw new RuntimeException("Cannot parse values from json", e); // 抛出运行时异常
    }
  }

  // 对ES主机列表进行排序的工具方法
  // 排序的目的是确保相同配置的主机列表具有相同的顺序，从而提高缓存命中率
  // 使用主机的字符串表示进行字典序排序
  protected static List<HttpHost> getSortedHost(List<HttpHost> hosts) {
    // 使用流式处理对主机列表进行排序
    List<HttpHost> sortedHosts =
        hosts
            .stream() // 创建流
            .sorted(Comparator.comparing(HttpHost::toString, String::compareTo)) // 按主机字符串表示排序
            .collect(Collectors.toList()); // 收集为新列表
    return sortedHosts; // 返回排序后的主机列表
  }

  /**
   * 根据用户配置构建Elasticsearch REST客户端
   * 该方法使用缓存机制，相同配置的客户端会被复用，避免重复创建和资源浪费
   * 缓存键由hosts、pathPrefix、username和password组成，确保相同配置返回相同的客户端实例
   *
   * @param hosts ES HTTP主机列表，包含要连接的ES服务器地址和端口
   * @param pathPrefix ES集群的路径前缀，用于代理或网关场景，可以为null
   * @param username ES集群的用户名，用于基本认证，可以为null
   * @param password ES集群的密码，用于基本认证，可以为null
   * @param disableSSLVerification 是否禁用SSL验证，true表示不验证SSL证书，false表示严格验证
   * @return 返回新的或缓存的ES底层REST HTTP客户端，该客户端是线程安全的
   */
  @SuppressWarnings({"java:S4830", "java:S5527"}) // 抑制SSL相关的安全警告，因为disableSSLVerification参数允许用户控制
  private static RestClient connect(List<HttpHost> hosts, String pathPrefix,
                                    String username, String password,
                                    boolean disableSSLVerification) {

    requireNonNull(hosts, "hosts or coordinates"); // 校验hosts参数非空
    checkArgument(!hosts.isEmpty(), "no ES hosts specified"); // 校验hosts列表不为空
    // 当两个列表的所有对应元素都相等时，这两个列表被认为是相等的
    // 这使得RestClient参数列表适合作为缓存键
    // 使用ImmutableList创建不可变的缓存键，确保键的稳定性
    List cacheKey = ImmutableList.of(hosts, pathPrefix, username, password);

    try { // 尝试从缓存获取或创建新的RestClient
      // 使用Guava Cache的get方法，如果缓存中不存在则调用Callable创建新的实例
      return REST_CLIENTS.get(cacheKey, new Callable<RestClient>() {
        @Override public RestClient call() throws NoSuchAlgorithmException, KeyManagementException {
          // 创建RestClient构建器，传入主机数组
          RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[hosts.size()]));

          // 如果用户名和密码都不为空，则配置基本认证
          if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
            // 创建凭证提供者
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            // 设置全局认证范围的用户名密码凭证
            credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(username, password));
            // 配置HTTP客户端构建器，设置默认凭证提供者
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
          }

          // 如果需要禁用SSL验证（仅用于测试或开发环境）
          if (disableSSLVerification) {
            // 获取TLS SSL上下文实例
            SSLContext sslContext = SSLContext.getInstance("TLS");
            // 初始化SSL上下文，使用不安全的信任管理器，接受所有证书
            sslContext.init(null, new TrustManager[] {UnsafeX509ExtendedTrustManager.getInstance()},
                null);

            // 配置HTTP客户端构建器，设置SSL上下文和主机名验证器
            // 主机名验证器始终返回true，即不验证主机名
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                httpClientBuilder.setSSLContext(sslContext)
                    .setSSLHostnameVerifier((host, session) -> true));
          }

          // 如果配置了路径前缀，则设置到构建器中
          // 路径前缀用于所有ES API请求的URL前缀，常用于代理或网关场景
          if (pathPrefix != null && !pathPrefix.isEmpty()) {
            builder.setPathPrefix(pathPrefix);
          }
          // 构建并返回RestClient实例
          return builder.build();
        }
      });
    } catch (ExecutionException ex) { // 捕获缓存执行异常
      throw new RuntimeException("Cannot return a cached RestClient", ex); // 抛出运行时异常
    }
  }
}
