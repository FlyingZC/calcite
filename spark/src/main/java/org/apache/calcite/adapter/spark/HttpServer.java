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
// 指定当前类所属的包名，位于 org.apache.calcite.adapter.spark 包下，这是 Calcite 的 Spark 适配器包
package org.apache.calcite.adapter.spark;

// 导入 Checker Framework 的注解，用于标记可能为 null 的值，帮助进行静态空值检查
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入 Jetty 服务器的连接器接口，用于配置服务器的网络连接
import org.eclipse.jetty.server.Connector;
// 导入 Jetty 的处理器接口，用于处理 HTTP 请求
import org.eclipse.jetty.server.Handler;
// 导入 Jetty 服务器核心类，提供 HTTP 服务功能
import org.eclipse.jetty.server.Server;
// 导入 Jetty 的服务器连接器实现，用于创建具体的网络连接
import org.eclipse.jetty.server.ServerConnector;
// 导入 Jetty 的默认处理器，用于处理未匹配的请求
import org.eclipse.jetty.server.handler.DefaultHandler;
// 导入 Jetty 的处理器列表，用于管理多个处理器
import org.eclipse.jetty.server.handler.HandlerList;
// 导入 Jetty 的资源处理器，用于提供静态文件服务
import org.eclipse.jetty.server.handler.ResourceHandler;
// 导入 Jetty 的队列线程池，用于管理服务器的线程池
import org.eclipse.jetty.util.thread.QueuedThreadPool;

// 导入 Java 的文件类，用于文件操作
import java.io.File;
// 导入 Java 的 IO 异常类，用于处理输入输出错误
import java.io.IOException;
// 导入 Java 的 IPv4 地址类
import java.net.Inet4Address;
// 导入 Java 的 IP 地址类
import java.net.InetAddress;
// 导入 Java 的接口地址类，用于获取网络接口的地址信息
import java.net.InterfaceAddress;
// 导入 Java 的网络接口类，用于获取本机的网络接口信息
import java.net.NetworkInterface;
// 导入 Java 的集合工具类，用于将枚举转换为列表
import java.util.Collections;

/**
 * An HTTP server for static content used to allow worker nodes to access JARs.
 * // 这是一个用于提供静态内容服务的 HTTP 服务器，主要用于允许工作节点访问 JAR 文件
 * // 在分布式计算环境中，Calcite 需要将 JAR 文件分发给各个 worker 节点，这个 HTTP 服务器就负责托管这些 JAR 文件
 *
 * <p>Based on Spark HttpServer, wraps a Jetty server.
 * // 基于 Spark 的 HttpServer 实现，内部封装了 Jetty 服务器（一个高性能的 Java HTTP 服务器）
 * // Jetty 是一个开源的、基于 Java 的 Web 服务器和 Servlet 容器，提供高性能、低延迟的 HTTP 服务
 * // 这个类是对 Jetty 服务器的简化封装，专门用于 Calcite 的 Spark 适配器场景
 */
// 定义 HttpServer 类，用于提供静态文件的 HTTP 服务
// 该类是包级私有访问权限（没有 public 修饰符），只能在 org.apache.calcite.adapter.spark 包内使用
class HttpServer {
  // 本地 IP 地址的静态缓存变量，用于存储本机的外部可访问 IP 地址
  // 使用 static 修饰符，表示该变量属于类，在所有实例间共享
  // 该变量在首次调用 localIpAddress() 方法时被初始化，之后会被缓存以避免重复查找
  // 使用 volatile 不是必须的，因为 localIpAddress() 方法使用了同步机制保证线程安全
  private static String localIpAddress;

  // 资源基础目录，指定 HTTP 服务器要提供服务的根目录
  // 这个目录下包含的文件（如 JAR 文件）可以通过 HTTP 访问
  // 使用 final 修饰符表示该变量在构造函数初始化后不可再改变，保证对象的不可变性
  // 例如：如果 resourceBase = "/path/to/jars"，则访问 http://host:port/my.jar 会返回 /path/to/jars/my.jar
  private final File resourceBase;

  // HttpServer 类的构造函数，用于创建 HTTP 服务器实例
  // 参数 resourceBase：指定要提供服务的资源基础目录，该目录下的文件可以通过 HTTP 访问
  // 构造函数只初始化资源基础目录，不启动服务器，服务器需要显式调用 start() 方法启动
  // 这种设计模式允许延迟启动，可以在需要时才启动服务器
  HttpServer(File resourceBase) {
    // 将传入的资源基础目录参数赋值给成员变量 resourceBase
    // 使用 this 关键字区分成员变量和参数变量
    this.resourceBase = resourceBase;
  }

  // Jetty 服务器实例，用于提供 HTTP 服务
  // 使用 @Nullable 注解表示该变量可能为 null（服务器未启动时）
  // 使用 private 修饰符封装该变量，外部只能通过 start()、stop()、uri() 等方法间接操作
  // 在服务器启动前该变量为 null，调用 start() 方法后初始化，调用 stop() 方法后重置为 null
  private @Nullable Server server;
  // 服务器监听的端口号，用于记录服务器实际监听的端口
  // 初始值为 -1，表示服务器未启动或已停止
  // 当调用 start() 方法时，Jetty 会自动分配一个可用端口（通过 connector.setPort(0) 实现），然后将端口号赋值给该变量
  // 当调用 stop() 方法时，该变量被重置为 -1
  // 外部可以通过 uri() 方法获取完整的访问地址（包含 IP 和端口）
  private int port = -1;

  // 启动 HTTP 服务器的方法
  // 该方法会创建并启动 Jetty 服务器，配置线程池、连接器、处理器等组件
  // 如果服务器已经启动（server != null），则抛出运行时异常
  // 该方法没有返回值，启动成功后可以通过 uri() 方法获取访问地址
  void start() {
    // 检查服务器是否已经启动，如果 server 不为 null，说明服务器已经在运行
    if (server != null) {
      // 抛出运行时异常，提示服务器已经启动，避免重复启动导致错误
      throw new RuntimeException("Server is already started");
    } else {
      // 创建 Jetty 的队列线程池，用于管理服务器的线程
      // QueuedThreadPool 是 Jetty 提供的高性能线程池实现，使用队列来管理待执行的任务
      // 相比普通的线程池，QueuedThreadPool 提供了更好的性能和资源利用率
      QueuedThreadPool threadPool = new QueuedThreadPool();
      // 将线程池设置为守护线程（daemon thread）
      // 守护线程的特点是：当 JVM 中只剩下守护线程时，JVM 会退出
      // 这样设置的好处是：当主程序退出时，HTTP 服务器线程会自动停止，不会阻止 JVM 退出
      // 如果不设置为守护线程，即使主程序结束，服务器线程也会继续运行，导致 JVM 无法正常退出
      threadPool.setDaemon(true);
      // 使用配置好的线程池创建 Jetty 服务器实例
      // Server 是 Jetty 的核心类，负责管理整个服务器的生命周期
      // 传入线程池后，服务器会使用该线程池来处理所有的 HTTP 请求
      server = new Server(threadPool);
      // 让服务器管理线程池的生命周期
      // 这样当服务器启动、停止时，线程池也会相应地启动、停止
      // 避免了手动管理线程池的复杂性
      server.manage(threadPool);

      // 创建服务器连接器（ServerConnector），用于处理客户端的连接请求
      // ServerConnector 是 Jetty 的网络连接实现，负责监听端口、接受连接、读写数据
      // 传入 server 参数，将连接器与服务器关联
      final ServerConnector connector = new ServerConnector(server);
      // 设置连接器的空闲超时时间（单位：毫秒）
      // 空闲超时是指：如果客户端连接在指定时间内没有数据传输，服务器会主动断开连接
      // 60 * 1000 = 60000 毫秒 = 60 秒
      // 这个设置可以避免僵尸连接占用服务器资源
      connector.setIdleTimeout(60 * 1000);
      // 设置连接器监听的端口号为 0
      // 设置为 0 是一个特殊的值，表示让操作系统自动分配一个可用的端口号
      // 这是一种动态端口分配机制，避免端口冲突
      // 服务器启动后，可以通过 connector.getLocalPort() 获取实际分配的端口号
      connector.setPort(0);
      // 将连接器配置到服务器
      // 服务器可以配置多个连接器，用于监听不同的端口或协议
      // 这里只配置了一个连接器数组，包含上面创建的 connector
      server.setConnectors(new Connector[] { connector });

      // 创建资源处理器（ResourceHandler），用于提供静态文件服务
      // ResourceHandler 是 Jetty 提供的专门用于处理静态文件请求的处理器
      // 它会根据请求的 URL 路径，从文件系统中读取对应的文件并返回给客户端
      final ResourceHandler resHandler = new ResourceHandler();
      // 设置资源处理器的基础目录（资源根目录）
      // resourceBase.getAbsolutePath() 获取资源基础目录的绝对路径
      // 例如：如果 resourceBase 是 File("/path/to/jars")，则绝对路径可能是 "/Users/user/calcite/jars"
      // 当客户端请求 "http://host:port/my.jar" 时，服务器会返回 "/path/to/jars/my.jar" 文件
      // 资源处理器会自动处理文件的 MIME 类型、范围请求、缓存控制等
      resHandler.setResourceBase(resourceBase.getAbsolutePath());

      // 创建处理器列表（HandlerList），用于管理多个处理器
      // HandlerList 是 Jetty 提供的处理器容器，可以按顺序管理多个处理器
      // 当收到请求时，HandlerList 会依次调用每个处理器，直到有处理器处理该请求
      final HandlerList handlerList = new HandlerList();
      // 设置处理器列表中的处理器数组
      // 第一个处理器：resHandler（资源处理器），用于处理静态文件请求
      // 第二个处理器：new DefaultHandler()（默认处理器），用于处理未匹配的请求
      // DefaultHandler 会返回 404 错误或目录列表（如果启用了目录浏览）
      // 这种设计模式允许链式处理：如果资源处理器无法处理请求（文件不存在），则交给默认处理器处理
      handlerList.setHandlers(new Handler[] {resHandler, new DefaultHandler()});
      // 将处理器列表设置到服务器
      // 服务器收到请求后，会调用处理器列表来处理请求
      server.setHandler(handlerList);
      // 启动服务器
      // server.start() 会启动 Jetty 服务器，开始监听端口并接受连接
      // 该方法可能会抛出异常（如端口被占用、权限不足等）
      try {
        // 启动服务器，这是一个阻塞操作，直到服务器完全启动
        // 启动过程包括：初始化线程池、绑定端口、启动连接器、初始化处理器等
        server.start();
      } catch (Exception e) {
        // 如果启动过程中发生异常，将异常包装为 RuntimeException 抛出
        // 使用 RuntimeException 是因为 start() 方法没有声明抛出检查型异常
        // 这种异常处理方式简化了调用方的代码，调用方不需要显式处理异常
        throw new RuntimeException(e);
      }
      // 获取连接器实际监听的端口号
      // 因为之前设置了 connector.setPort(0)，操作系统会自动分配一个可用端口
      // connector.getLocalPort() 返回实际分配的端口号
      // 例如：可能分配到 12345 端口，则 port = 12345
      // 外部可以通过 uri() 方法获取完整的访问地址（如 "http://192.168.1.100:12345"）
      port = connector.getLocalPort();
    }
  }

  // 停止 HTTP 服务器的方法
  // 该方法会停止 Jetty 服务器，释放资源，重置状态
  // 如果服务器已经停止（server == null），则抛出运行时异常
  void stop() {
    // 检查服务器是否已经停止，如果 server 为 null，说明服务器已经停止
    if (server == null) {
      // 抛出运行时异常，提示服务器已经停止，避免重复停止导致错误
      throw new RuntimeException("Server is already stopped");
    } else {
      // 尝试停止服务器，使用 try-catch 捕获可能的异常
      try {
        // 创建一个局部变量 server1，引用当前的 server 实例
        // 这样做的目的是：在将 server 设置为 null 之前，先保存对 server 的引用
        // 这是一种防御性编程，避免在 stop() 方法执行过程中，其他线程访问 null 的 server
        final Server server1 = server;
        // 将 port 重置为 -1，表示服务器已停止
        // 这是一种状态标记，外部可以通过检查 port 是否为 -1 来判断服务器是否运行
        port = -1;
        // 将 server 设置为 null，释放对 Jetty 服务器实例的引用
        // 这样做可以让垃圾回收器回收服务器对象，避免内存泄漏
        // 同时，server == null 也可以作为服务器已停止的标志
        server = null;
        // 停止服务器实例
        // server1.stop() 会停止 Jetty 服务器，包括：停止接受新连接、关闭现有连接、停止线程池等
        // 该方法可能会抛出异常（如停止过程中发生错误）
        server1.stop();
      } catch (Exception e) {
        // 如果停止过程中发生异常，将异常包装为 RuntimeException 抛出
        // 使用 RuntimeException 是因为 stop() 方法没有声明抛出检查型异常
        // 这种异常处理方式简化了调用方的代码，调用方不需要显式处理异常
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Returns the URI of this HTTP server ("http://host:port").
   * // 返回此 HTTP 服务器的 URI（统一资源标识符），格式为 "http://host:port"
   * // 例如："http://192.168.1.100:12345"
   * // 该方法用于获取服务器的访问地址，其他节点可以通过这个地址访问服务器托管的文件
   * // 如果服务器未启动（server == null），则抛出运行时异常
   */
  String uri() {
    // 检查服务器是否已启动，如果 server 为 null，说明服务器未启动
    if (server == null) {
      // 抛出运行时异常，提示服务器未启动
      // 这是一种防御性编程，避免在服务器未启动时返回无效的 URI
      throw new RuntimeException("Server is not started");
    } else {
      // 返回服务器的完整 URI，格式为 "http://IP地址:端口号"
      // localIpAddress() 方法获取本机的外部可访问 IP 地址
      // port 是服务器实际监听的端口号
      // 例如：返回 "http://192.168.1.100:12345"
      // 其他节点可以通过这个 URI 访问服务器托管的 JAR 文件
      return "http://" + localIpAddress() + ":" + port;
    }
  }

  /**
   * Get the local host's IP address in dotted-quad format (e.g. 1.2.3.4).
   * // 获取本地主机的 IP 地址，格式为点分十进制（例如：1.2.3.4）
   * // 该方法会智能地选择本机的外部可访问 IP 地址，而不是回环地址（127.0.0.1）
   * // 在分布式环境中，worker 节点需要通过这个 IP 地址访问 driver 节点的 HTTP 服务器
   * // Note, this is typically not used from within core spark.
   * // 注意：这个方法通常不在 Spark 核心中使用，主要用于 Calcite 的 Spark 适配器
   */
  static synchronized String localIpAddress() {
    // 使用类级别的同步锁，确保线程安全
    // synchronized (HttpServer.class) 表示锁定 HttpServer 类对象
    // 这样可以防止多个线程同时执行 localIpAddress() 方法，避免竞态条件
    synchronized (HttpServer.class) {
      // 检查本地 IP 地址是否已经初始化（是否为 null）
      if (localIpAddress == null) {
        // 如果未初始化，则尝试查找本地 IP 地址
        // 使用 try-catch 捕获可能发生的 IO 异常
        try {
          // 调用 findLocalIpAddress() 方法查找本地 IP 地址
          // 该方法会遍历所有网络接口，找到一个合适的外部可访问 IP 地址
          localIpAddress = findLocalIpAddress();
        } catch (IOException e) {
          // 如果查找过程中发生 IO 异常，将异常包装为 RuntimeException 抛出
          // IOException 可能发生在：网络接口不可用、权限不足等情况下
          throw new RuntimeException(e);
        }
      }
    }
    // 返回本地 IP 地址
    // 由于使用了同步机制和缓存机制，该方法在首次调用后会缓存结果，后续调用直接返回缓存值
    // 这种设计提高了性能，避免了重复查找 IP 地址的开销
    return localIpAddress;
  }

  // 查找本地 IP 地址的私有方法
  // 该方法会智能地选择本机的外部可访问 IP 地址
  // 返回的 IP 地址格式为点分十进制（例如：192.168.1.100）
  // 可能抛出 IOException，表示在查找 IP 地址过程中发生错误
  private static String findLocalIpAddress() throws IOException {
    // 首先检查是否设置了环境变量 CALCITE_LOCAL_IP
    // 环境变量可以强制指定使用特定的 IP 地址
    // 这是一种配置机制，允许用户在特殊情况下手动指定 IP 地址
    String defaultIpOverride = System.getenv("CALCITE_LOCAL_IP");
    // 如果环境变量存在且不为 null，则直接使用环境变量指定的 IP 地址
    if (defaultIpOverride != null) {
      // 返回环境变量指定的 IP 地址
      // 这种机制允许用户在 IP 地址自动检测失败时，手动指定正确的 IP 地址
      return defaultIpOverride;
    } else {
      // 如果没有设置环境变量，则自动查找本地 IP 地址
      // InetAddress.getLocalHost() 获取本地主机的 IP 地址
      // 该方法返回的是根据主机名解析得到的 IP 地址
      final InetAddress address = InetAddress.getLocalHost();
      // 检查获取到的地址是否是回环地址（loopback address）
      // 回环地址是指 127.0.0.1 或 ::1，这些地址只能在本地访问，外部无法访问
      // 在分布式环境中，如果使用回环地址，其他节点无法访问 HTTP 服务器
      if (address.isLoopbackAddress()) {
        // 如果解析到回环地址（例如 127.0.1.1，这种情况在 Debian 系统上常见）
        // 则尝试通过本地网络接口找到一个更好的地址（非回环地址）
        // Address resolves to something like 127.0.1.1, which happens on
        // Debian; try to find a better address using the local network
        // interfaces.
        // 遍历所有网络接口（NetworkInterface）
        // NetworkInterface.getNetworkInterfaces() 返回所有网络接口的枚举
        // Collections.list() 将枚举转换为列表，方便遍历
        // 网络接口包括：以太网卡、无线网卡、虚拟网卡等
        for (NetworkInterface ni
            : Collections.list(NetworkInterface.getNetworkInterfaces())) {
          // 遍历当前网络接口的所有接口地址（InterfaceAddress）
          // 一个网络接口可能有多个地址（例如：IPv4 地址、IPv6 地址）
          for (InterfaceAddress interfaceAddress : ni.getInterfaceAddresses()) {
            // 获取接口地址中的 IP 地址
            final InetAddress addr = interfaceAddress.getAddress();
            // 检查该地址是否满足以下条件：
            // 1. 不是链路本地地址（!addr.isLinkLocalAddress()）
            //    链路本地地址是 169.254.x.x，这种地址只能在本地网络中使用，不能跨网段访问
            // 2. 不是回环地址（!addr.isLoopbackAddress()）
            //    回环地址是 127.x.x.x，这种地址只能在本地访问
            // 3. 是 IPv4 地址（addr instanceof Inet4Address）
            //    IPv6 地址格式不同，这里只使用 IPv4 地址
            if (!addr.isLinkLocalAddress()
                && !addr.isLoopbackAddress() && addr instanceof Inet4Address) {
              // 如果找到了满足条件的地址，说明找到了一个合理的外部可访问 IP 地址
              // We've found an address that looks reasonable!
              // 记录警告信息，提示用户主机名解析到回环地址，但使用了另一个地址
              // 警告信息包括：主机名、回环地址、实际使用的地址、网络接口名称
              logWarning("Your hostname, "
                  + InetAddress.getLocalHost().getHostName()
                  + " resolves to a loopback address: "
                  + address.getHostAddress() + "; using "
                  + addr.getHostAddress() + " instead (on interface "
                  + ni.getName() + ")");
              // 记录警告信息，提示用户可以通过设置 CALCITE_LOCAL_IP 环境变量来指定其他地址
              logWarning(
                  "Set CALCITE_LOCAL_IP if you need to bind to another address");
              // 返回找到的 IP 地址
              // 这个地址是点分十进制格式（例如：192.168.1.100）
              return addr.getHostAddress();
            }
          }
        }
        // 如果遍历所有网络接口后仍未找到合适的地址，记录警告信息
        // 警告信息包括：主机名、回环地址
        logWarning(
            "Your hostname, " + InetAddress.getLocalHost().getHostName()
            + " resolves to a loopback address: " + address.getHostAddress()
            + ", but we couldn't find any external IP address!");
        // 记录警告信息，提示用户可以通过设置 CALCITE_LOCAL_IP 环境变量来指定其他地址
        logWarning(
            "Set CALCITE_LOCAL_IP if you need to bind to another address");
      }
      // 如果获取到的地址不是回环地址，或者遍历网络接口后仍未找到更好的地址
      // 则直接返回获取到的地址
      // 这个地址可能是回环地址（如果遍历未找到更好的地址），也可能是外部可访问地址
      return address.getHostAddress();
    }
  }

  // 记录警告信息的私有方法
  // 该方法将警告信息输出到标准输出（System.out）
  // 参数 s：要输出的警告信息字符串
  // 使用标准输出而不是标准错误（System.err），是因为这些警告信息对用户来说是正常的信息提示
  private static void logWarning(String s) {
    // 将警告信息输出到标准输出
    // System.out 是标准输出流，通常连接到控制台
    // 这种简单的日志记录方式适用于小型应用，在生产环境中通常会使用日志框架（如 Log4j、SLF4J）
    System.out.println(s);
  }
}
