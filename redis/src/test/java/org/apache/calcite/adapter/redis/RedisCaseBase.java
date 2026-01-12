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
 */ // Apache开源许可证声明，说明该代码遵循Apache 2.0许可证，允许自由使用、修改和分发
package org.apache.calcite.adapter.redis; // 声明当前类所属的包，位于org.apache.calcite.adapter.redis包下

import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性类，用于读取TEST_WITH_DOCKER_CONTAINER等配置

import org.junit.jupiter.api.AfterAll; // 导入JUnit 5的AfterAll注解，标记在所有测试方法执行后执行的方法
import org.junit.jupiter.api.AfterEach; // 导入JUnit 5的AfterEach注解，标记在每个测试方法执行后执行的方法
import org.junit.jupiter.api.BeforeAll; // 导入JUnit 5的BeforeAll注解，标记在所有测试方法执行前执行的方法
import org.junit.jupiter.api.BeforeEach; // 导入JUnit 5的BeforeEach注解，标记在每个测试方法执行前执行的方法
import org.junit.jupiter.api.parallel.Execution; // 导入JUnit 5的并行执行注解，用于配置测试的执行模式
import org.junit.jupiter.api.parallel.ExecutionMode; // 导入JUnit 5的执行模式枚举，包含SAME_THREAD、CONCURRENT等模式
import org.testcontainers.DockerClientFactory; // 导入TestContainers的Docker客户端工厂类，用于检查Docker是否可用
import org.testcontainers.containers.GenericContainer; // 导入TestContainers的通用容器类，用于管理Docker容器生命周期

import java.io.IOException; // 导入Java的IO异常类，用于处理输入输出相关的异常
import java.net.ServerSocket; // 导入Java的网络服务器套接字类，用于查找可用端口
import java.util.logging.Logger; // 导入Java的日志记录器类，用于记录测试运行时的日志信息

import redis.embedded.RedisServer; // 导入嵌入式Redis服务器类，用于在JVM进程中启动Redis服务器

/**
 * RedisITCaseBase.
 * Redis测试用例基类，为所有Redis适配器测试提供Redis服务器环境支持
 * 该类负责启动和管理Redis服务器，支持两种运行模式：
 * 1. Docker容器模式：使用TestContainers启动Redis 7.2.4版本的Docker容器
 * 2. 嵌入式模式：使用embedded-redis库启动嵌入式Redis服务器（Windows系统上使用）
 * 该类确保在测试执行前Redis服务器已经启动，测试执行后正确清理资源
 * 所有继承该类的测试用例都可以通过getRedisServerPort()和getRedisServerHost()获取Redis连接信息
 */
@Execution(ExecutionMode.SAME_THREAD) // 强制所有测试方法在同一线程中执行，避免并发问题
public abstract class RedisCaseBase {

  private static final int PORT = getAvailablePort(); // 动态获取一个可用的端口号，用于嵌入式Redis服务器监听
  private static final String HOST = "127.0.0.1"; // 嵌入式Redis服务器的主机地址，默认使用本地回环地址
  private static final String MAX_HEAP = "maxheap 51200000"; // Redis服务器的最大堆内存配置，设置为50MB（51200000字节），用于限制Redis内存使用

  /**
   * The Redis Docker container.
   * Redis Docker容器实例，用于在测试环境中运行Redis服务器
   *
   * <p>Uses the Redis 2.8.19 version to be aligned with the embedded server.
   * 使用Redis 7.2.4版本的Docker镜像，通过TestContainers库管理容器生命周期
   * 容器暴露6379端口（Redis默认端口），容器启动后会自动映射到宿主机可用端口
   * 该容器是静态常量，在测试套件启动时初始化，在所有测试结束后销毁
   */
  private static final GenericContainer<?> REDIS_CONTAINER =
      new GenericContainer<>("redis:7.2.4").withExposedPorts(6379); // 创建Redis 7.2.4版本的Docker容器，并暴露6379端口

  /**
   * The embedded Redis server.
   * 嵌入式Redis服务器实例，作为Docker容器不可用时的备用方案
   *
   * <p>With the existing dependencies (com.github.kstyrc:embedded-redis:0.6) it
   * uses by default Redis 2.8.19 version.
   * 使用com.github.kstyrc:embedded-redis:0.6依赖库，默认使用Redis 2.8.19版本
   * 该服务器在当前JVM进程中运行，不需要外部Docker环境
   * 主要用于Windows系统或Docker不可用的开发环境
   * 在每个测试方法执行前启动，测试方法执行后停止
   */
  private static RedisServer redisServer; // 嵌入式Redis服务器实例，在JVM进程中运行

  @BeforeAll // 在所有测试方法执行前只执行一次，用于初始化测试环境
  public static void startRedisContainer() {
    // Check if docker is running, and start container if possible
    // 检查系统属性是否配置为使用Docker容器，以及Docker环境是否可用
    if (CalciteSystemProperty.TEST_WITH_DOCKER_CONTAINER.value() // 检查系统属性TEST_WITH_DOCKER_CONTAINER是否设置为true
        && DockerClientFactory.instance().isDockerAvailable()) { // 检查Docker客户端是否可用（Docker守护进程是否运行）
      REDIS_CONTAINER.start(); // 启动Redis Docker容器，容器会在后台运行并暴露6379端口
    }
  }

  @BeforeEach // 在每个测试方法执行前都执行一次，确保每个测试都有干净的Redis环境
  public void createRedisServer() throws IOException {
    if (!REDIS_CONTAINER.isRunning()) { // 检查Docker容器是否正在运行，如果未运行则使用嵌入式Redis服务器
      if (isWindows()) { // 判断当前操作系统是否为Windows
        // Windows系统需要使用builder模式创建Redis服务器，并设置maxheap参数
        // 这是因为Windows上的Redis需要显式配置内存限制
        redisServer = RedisServer.builder().port(PORT).setting(MAX_HEAP).build(); // 使用构建器模式创建Redis服务器，指定端口和最大堆内存
      } else {
        // 非Windows系统（Linux/Mac）可以直接使用端口创建Redis服务器
        redisServer = new RedisServer(PORT); // 使用指定端口创建Redis服务器实例
      }
      Logger.getAnonymousLogger().info("Not using Docker, starting RedisMiniServer"); // 记录日志，提示正在使用嵌入式Redis服务器而非Docker容器
      redisServer.start(); // 启动嵌入式Redis服务器，开始监听指定端口
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").startsWith("Windows"); // 获取操作系统名称属性，判断是否以"Windows"开头，返回true表示当前系统是Windows
  }

  @AfterEach // 在每个测试方法执行后都执行一次，用于清理测试环境
  public void stopRedisServer() {
    if (!REDIS_CONTAINER.isRunning()) { // 检查Docker容器是否未运行，如果未运行则停止嵌入式Redis服务器
      redisServer.stop(); // 停止嵌入式Redis服务器，释放端口和内存资源
    }
  }

  /**
   * Find a non-occupied port.
   * 查找一个未被占用的端口号，用于嵌入式Redis服务器监听
   *
   * @return A non-occupied port.
   * @return 返回一个可用的端口号，范围在1024-65535之间
   */
  public static int getAvailablePort() {
    for (int i = 0; i < 50; i++) { // 最多尝试50次查找可用端口
      try (ServerSocket serverSocket = new ServerSocket(0)) { // 创建ServerSocket，传入0表示让系统自动分配一个可用端口
        int port = serverSocket.getLocalPort(); // 获取系统分配的端口号
        if (port != 0) { // 验证端口号是否有效（非0表示有效）
          return port; // 返回找到的可用端口号
        }
      } catch (IOException ignored) { // 捕获IO异常，表示端口分配失败，继续下一次尝试
      }
    }

    throw new RuntimeException("Could not find an available port on the host."); // 如果尝试50次仍未找到可用端口，抛出运行时异常
  }

  @AfterAll // 在所有测试方法执行后只执行一次，用于清理测试环境
  public static void stopRedisContainer() {
    if (REDIS_CONTAINER.isRunning()) { // 检查Docker容器是否正在运行
      REDIS_CONTAINER.stop(); // 停止并销毁Redis Docker容器，释放容器资源
    }
  }

  static int getRedisServerPort() {
    return  REDIS_CONTAINER.isRunning() ? REDIS_CONTAINER.getMappedPort(6379) : PORT; // 如果Docker容器正在运行，返回容器映射到宿主机的端口号；否则返回嵌入式Redis服务器的端口号
  }

  static String getRedisServerHost() {
    return REDIS_CONTAINER.isRunning() ? REDIS_CONTAINER.getHost() : HOST; // 如果Docker容器正在运行，返回容器的主机地址（通常是localhost）；否则返回嵌入式Redis服务器的主机地址（127.0.0.1）
  }

}
