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
// Apache许可证头部，声明代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.elasticsearch; // 定义包路径，该类属于Elasticsearch适配器包

import org.apache.calcite.util.TestUtil; // 导入Calcite测试工具类，用于异常处理

import org.elasticsearch.action.admin.cluster.node.info.NodeInfo; // 导入ES节点信息类，用于获取节点详细信息
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse; // 导入ES节点信息响应类，用于接收节点查询结果
import org.elasticsearch.client.Client; // 导入ES客户端接口，用于与ES集群交互
import org.elasticsearch.common.settings.Settings; // 导入ES配置设置类，用于配置ES节点参数
import org.elasticsearch.common.transport.TransportAddress; // 导入ES传输地址类，用于表示网络传输地址
import org.elasticsearch.http.HttpInfo; // 导入ES HTTP信息类，用于获取HTTP服务绑定地址
import org.elasticsearch.node.InternalSettingsPreparer; // 导入ES内部设置准备器，用于准备节点环境
import org.elasticsearch.node.Node; // 导入ES节点类，代表一个ES节点实例
import org.elasticsearch.node.NodeValidationException; // 导入ES节点验证异常类，用于处理节点启动验证失败
import org.elasticsearch.painless.PainlessPlugin; // 导入Painless脚本插件，用于支持脚本字段功能
import org.elasticsearch.plugins.Plugin; // 导入ES插件接口，用于扩展ES功能
import org.elasticsearch.transport.Netty4Plugin; // 导入Netty4传输插件，用于基于Netty4的网络通信

import java.io.File; // 导入文件类，用于文件操作
import java.io.IOException; // 导入IO异常类，处理文件操作异常
import java.nio.file.Files; // 导入文件工具类，用于创建临时目录
import java.util.Arrays; // 导入数组工具类，用于数组操作
import java.util.Collection; // 导入集合接口，用于存储插件类集合

import static com.google.common.base.Preconditions.checkState; // 导入状态检查工具，用于验证对象状态

import static java.util.Collections.emptyMap; // 导入空集合工具，用于创建空Map
import static java.util.Objects.requireNonNull; // 导入对象非空检查工具，用于参数验证

/**
 * Represents a single elastic search node which can run embedded in a java application. // 表示一个可以嵌入到Java应用程序中运行的Elasticsearch节点
 *
 * <p>Intended for unit and integration tests. Settings and plugins are crafted for Calcite. // 旨在用于单元测试和集成测试。设置和插件是为Calcite量身定制的。
 * 该类提供了在测试环境中启动和管理嵌入式ES节点的功能，避免了需要外部ES集群的依赖
 * 支持自动清理临时数据，实现了AutoCloseable接口以便于资源管理
 */ // 类注释结束
class EmbeddedElasticsearchNode implements AutoCloseable { // 定义嵌入式ES节点类，实现AutoCloseable接口以支持try-with-resources语法

  private final Node node; // ES节点实例，final表示初始化后不可变，是实际执行ES操作的底层对象
  private volatile boolean isStarted; // 节点启动状态标志，volatile关键字确保多线程环境下的可见性，防止指令重排

  private EmbeddedElasticsearchNode(Node node) { // 私有构造方法，通过工厂方法创建实例，限制外部直接实例化
    this.node = requireNonNull(node, "node"); // 初始化node成员变量，使用requireNonNull确保node参数不为null，否则抛出NullPointerException
  } // 构造方法结束

  /**
   * Creates an instance with existing settings. // 使用现有配置创建实例
   *
   * @param settings Configuration parameters of ES instance // ES实例的配置参数，包含节点名称、数据路径、网络设置等
   *
   * @return instance that needs to be explicitly started (using // 返回的实例需要显式调用start()方法才能启动
   * {@link #start()}) // 引用start()方法的文档链接
   */ // 方法注释结束
  private static EmbeddedElasticsearchNode create(Settings settings) { // 私有静态工厂方法，根据给定的配置创建ES节点实例
    // ensure PainlessPlugin is installed or otherwise scripted fields would not work // 确保安装了PainlessPlugin插件，否则脚本字段功能将无法工作
    Node node = new LocalNode(settings, Arrays.asList(Netty4Plugin.class, PainlessPlugin.class)); // 创建LocalNode实例，传入配置和插件列表（Netty4Plugin用于网络传输，PainlessPlugin用于脚本支持）
    return new EmbeddedElasticsearchNode(node); // 返回封装后的EmbeddedElasticsearchNode实例
  } // create方法结束

  /**
   * Creates elastic node as single member of a cluster. Node will not // 创建作为集群单个成员的ES节点。节点不会自动启动
   * be started unless {@link #start()} is explicitly called. // 除非显式调用start()方法
   *
   * <p>Need {@code synchronized} because of static caches inside ES // 需要synchronized关键字，因为ES内部有静态缓存
   * (which are not thread safe). // （这些缓存不是线程安全的）
   *
   * @return instance; needs to be explicitly started using {@link #start()} // 返回实例；需要显式调用start()方法启动
   */ // 方法注释结束
  public static synchronized EmbeddedElasticsearchNode create() { // 公共静态同步工厂方法，创建默认配置的ES节点实例，synchronized保证线程安全
    File data; // 声明数据目录文件变量，用于存储ES索引数据
    File home; // 声明主目录文件变量，用于存储ES配置和日志
    try { // 开始try块，捕获可能的IO异常
      data = Files.createTempDirectory("es-data").toFile(); // 在系统临时目录下创建名为"es-data"的临时目录，用于存储ES数据
      data.deleteOnExit(); // 设置JVM退出时自动删除该临时目录，避免残留垃圾文件
      home = Files.createTempDirectory("es-home").toFile(); // 在系统临时目录下创建名为"es-home"的临时目录，用于ES主目录
      home.deleteOnExit(); // 设置JVM退出时自动删除该临时目录，避免残留垃圾文件
    } catch (IOException e) { // 捕获IO异常，处理临时目录创建失败的情况
      throw TestUtil.rethrow(e); // 使用TestUtil工具重新抛出异常，将检查类型异常转换为运行时异常
    } // try-catch块结束

    Settings settings = Settings.builder() // 开始构建ES配置对象，使用建造者模式
        .put("node.name", "fake-elastic") // 设置节点名称为"fake-elastic"，用于标识该测试节点
        .put("path.home", home.getAbsolutePath()) // 设置ES主目录为临时home目录的绝对路径
        .put("path.data", data.getAbsolutePath()) // 设置ES数据目录为临时data目录的绝对路径
        .put("http.type", "netty4") // 设置HTTP服务类型为netty4，使用Netty4作为HTTP服务器
        // allow multiple instances to run in parallel // 允许多个实例并行运行
        .put("transport.tcp.port", 0) // 设置TCP传输端口为0，表示使用随机可用端口，避免端口冲突
        .put("http.port", 0) // 设置HTTP服务端口为0，表示使用随机可用端口，避免端口冲突
        .put("network.host", "localhost") // 设置网络主机为localhost，限制只能本地访问，提高测试安全性
        .build(); // 构建配置对象并返回

    return create(settings); // 调用私有create方法，传入配置参数，创建并返回ES节点实例
  } // public create方法结束

  /** Starts the current node. */ // 启动当前节点
  public void start() { // 公共方法，启动ES节点
    checkState(!isStarted, "already started"); // 检查节点是否已经启动，如果已启动则抛出IllegalStateException异常
    try { // 开始try块，捕获节点启动可能抛出的验证异常
      node.start(); // 调用底层Node对象的start方法，启动ES节点服务
      this.isStarted = true; // 将启动状态标志设置为true，表示节点已成功启动
    } catch (NodeValidationException e) { // 捕获节点验证异常，处理节点启动验证失败的情况
      throw TestUtil.rethrow(e); // 使用TestUtil工具重新抛出异常，将检查类型异常转换为运行时异常
    } // try-catch块结束
  } // start方法结束

  /**
   * Returns current address to connect to with HTTP client. // 返回用于HTTP客户端连接的当前地址
   *
   * @return hostname/port for HTTP connection // HTTP连接的主机名和端口
   */ // 方法注释结束
  public TransportAddress httpAddress() { // 公共方法，获取HTTP服务的绑定地址
    checkState(isStarted, "node is not started"); // 检查节点是否已启动，如果未启动则抛出IllegalStateException异常

    NodesInfoResponse response =  client().admin().cluster().prepareNodesInfo() // 获取客户端的管理员接口，准备节点信息查询请求
        .execute().actionGet(); // 同步执行查询请求并获取响应结果
    if (response.getNodes().size() != 1) { // 检查返回的节点数量是否为1，因为嵌入式节点应该是单节点
      throw new IllegalStateException("Expected single node but got " // 抛出非法状态异常，说明期望单个节点但实际得到了多个节点
          + response.getNodes().size()); // 拼接实际节点数量到异常消息中
    } // if条件结束
    NodeInfo node = response.getNodes().get(0); // 从响应中获取第一个（也是唯一一个）节点的信息
    HttpInfo httpInfo = node.getInfo(HttpInfo.class); // 从节点信息中获取HTTP服务信息对象
    return httpInfo.address().boundAddresses()[0]; // 返回HTTP服务绑定的第一个地址（TransportAddress类型）
  } // httpAddress方法结束

  /**
   * Exposes elastic // 暴露Elasticsearch的
   * <a href="https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/transport-client.html">transport client</a> // 传输客户端（建议优先使用HTTP客户端）
   * (use of HTTP client is preferred). // （推荐使用HTTP客户端）
   *
   * @return current elastic search client // 返回当前的Elasticsearch客户端实例
   */ // 方法注释结束
  public Client client() { // 公共方法，获取ES客户端对象
    checkState(isStarted, "node is not started"); // 检查节点是否已启动，如果未启动则抛出IllegalStateException异常
    return node.client(); // 返回底层Node对象的客户端，用于执行ES操作
  } // client方法结束

  @Override public void close() throws Exception { // 重写AutoCloseable接口的close方法，用于清理资源
    node.close(); // 关闭底层ES节点，释放所有资源
    // cleanup data dirs // 清理数据目录
    for (String name : Arrays.asList("path.data", "path.home")) { // 遍历数据目录和主目录的配置键名
      if (node.settings().get(name) != null) { // 检查配置中是否存在该路径设置
        File file = new File(node.settings().get(name)); // 根据路径字符串创建File对象
        if (file.exists()) { // 检查文件或目录是否存在
          file.delete(); // 删除该文件或目录，清理临时数据
        } // if条件结束
      } // if条件结束
    } // for循环结束
  } // close方法结束

  /**
   * Having separate class to expose (protected) constructor which allows to install // 使用单独的类来暴露（受保护的）构造方法，该方法允许安装
   * different plugins. In our case it is {@code GroovyPlugin} for scripted fields // 不同的插件。在我们的例子中是用于脚本字段的GroovyPlugin
   * like {@code loc[0]} or {@code loc[1]['foo']}. // 例如loc[0]或loc[1]['foo']这样的脚本字段访问
   *
   * <p>This class is intended solely for tests // 该类仅用于测试
   */ // 类注释结束
  private static class LocalNode extends Node { // 定义私有静态内部类LocalNode，继承自Node，用于自定义节点配置

    private LocalNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) { // 私有构造方法，接收配置和插件类集合
      super( // 调用父类Node的构造方法
          InternalSettingsPreparer.prepareEnvironment(settings, emptyMap(), // 准备ES节点环境，传入配置、空配置补充和空配置提供者
            null, () -> "default_node_name"), // 传入null作为配置目录，使用lambda提供默认节点名称
          classpathPlugins, // 传入插件类集合，用于安装自定义插件
          false); // 传入false表示不进行验证，用于测试环境
    } // LocalNode构造方法结束
  } // LocalNode类结束
} // EmbeddedElasticsearchNode类结束
