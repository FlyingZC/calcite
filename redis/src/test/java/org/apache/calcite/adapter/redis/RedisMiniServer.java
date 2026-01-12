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
package org.apache.calcite.adapter.redis; // 声明包名，表示这个类属于 org.apache.calcite.adapter.redis 包，这是 Calcite Redis 适配器的测试包

import org.junit.jupiter.api.BeforeEach; // 导入 JUnit 5 的 BeforeEach 注解，用于在每个测试方法执行前执行初始化操作
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable; // 导入条件执行注解，用于根据环境变量决定是否执行测试

import java.util.HashMap; // 导入 HashMap 类，用于存储键值对数据
import java.util.Map; // 导入 Map 接口，用于定义键值对集合

import redis.clients.jedis.Jedis; // 导入 Jedis 客户端类，用于与 Redis 服务器进行交互
import redis.clients.jedis.JedisPool; // 导入 Jedis 连接池类，用于管理 Redis 连接池
import redis.clients.jedis.JedisPoolConfig; // 导入 Jedis 连接池配置类，用于配置连接池参数
import redis.embedded.RedisServer; // 导入嵌入式 Redis 服务器类，用于在测试中启动本地 Redis 实例

import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入断言方法，用于验证对象不为 null

/**
 * RedisServerMini for redis dataType's test. // 这是一个测试类，用于测试 Redis 适配器支持的各种数据类型（String、List、Set、Sorted Set、Hash）
 * 该类的主要作用是：
 * 1. 启动一个嵌入式 Redis 服务器用于测试
 * 2. 初始化 Jedis 连接池用于连接 Redis
 * 3. 创建各种数据类型的测试数据（raw 原始数据、json 格式数据、csv 格式数据）
 * 4. 为 Calcite Redis 适配器的集成测试提供数据支持
 * 
 * 测试数据包括：
 * - String 类型：raw_01, json_01, csv_01
 * - List 类型：raw_02, json_02, csv_02
 * - Set 类型：raw_03, json_03, csv_03
 * - Sorted Set 类型：raw_04, json_04, csv_04
 * - Hash 类型：raw_05, json_05, csv_05
 */
public class RedisMiniServer { // 定义 RedisMiniServer 测试类，用于管理 Redis 测试服务器的生命周期和测试数据
  private static JedisPool pool; // 静态 Jedis 连接池对象，用于管理 Redis 连接，避免频繁创建和销毁连接，提高性能
  private static final int PORT = 6379; // 静态常量，定义 Redis 服务器的端口号，Redis 默认端口为 6379
  private static final String HOST = "127.0.0.1"; // 静态常量，定义 Redis 服务器的主机地址，使用本地回环地址

  @BeforeEach // JUnit 5 注解，标记此方法在每个测试方法执行之前运行，用于初始化测试环境
  public void setUp() { // setUp 方法，在每个测试前执行，负责启动 Redis 服务器、初始化连接池和创建测试数据
    try { // 使用 try-catch 捕获可能的异常，确保即使启动失败也不会影响测试框架
      RedisServer redisServer = new RedisServer(PORT); // 创建嵌入式 Redis 服务器实例，指定端口号为 6379
      redisServer.start(); // 启动 Redis 服务器，在本地运行 Redis 实例
      JedisPoolConfig jedisPoolConfig = new JedisPoolConfig(); // 创建 Jedis 连接池配置对象，用于配置连接池参数
      jedisPoolConfig.setMaxTotal(10); // 设置连接池最大连接数为 10，限制同时最多可以有 10 个活跃连接
      pool = new JedisPool(jedisPoolConfig, HOST, PORT); // 创建 Jedis 连接池，使用配置对象、主机地址和端口号初始化
      makeData(); // 调用 makeData 方法创建测试数据，向 Redis 中插入各种类型的测试数据
      System.out.println("The redis server is started at host: " + HOST + " port: " + PORT); // 输出启动成功信息，包含主机和端口
    } catch (Exception e) { // 捕获所有可能的异常
      assertNotNull(e.getMessage()); // 断言异常消息不为 null，确保异常有可读的错误信息
    }
  }

  private void makeData() { // 私有方法，用于创建和初始化 Redis 中的测试数据，包含 String、List、Set、Sorted Set、Hash 五种数据类型
    try (Jedis jedis = pool.getResource()) { // 使用 try-with-resources 从连接池获取 Jedis 连接，确保使用后自动关闭连接
      jedis.del("raw_01"); // 删除键 raw_01，确保测试数据干净，避免旧数据干扰
      jedis.del("raw_02"); // 删除键 raw_02，清理可能存在的旧数据
      jedis.del("raw_03"); // 删除键 raw_03，清理可能存在的旧数据
      jedis.del("raw_04"); // 删除键 raw_04，清理可能存在的旧数据
      jedis.del("raw5"); // 删除键 raw5，清理可能存在的旧数据
      jedis.del("json_01"); // 删除键 json_01，清理可能存在的旧数据
      jedis.del("json_02"); // 删除键 json_02，清理可能存在的旧数据
      jedis.del("json_03"); // 删除键 json_03，清理可能存在的旧数据
      jedis.del("json_04"); // 删除键 json_04，清理可能存在的旧数据
      jedis.del("json_05"); // 删除键 json_05，清理可能存在的旧数据
      jedis.del("csv_01"); // 删除键 csv_01，清理可能存在的旧数据
      jedis.del("csv_02"); // 删除键 csv_02，清理可能存在的旧数据
      jedis.del("csv_03"); // 删除键 csv_03，清理可能存在的旧数据
      jedis.del("csv_04"); // 删除键 csv_04，清理可能存在的旧数据
      jedis.del("csv_05"); // 删除键 csv_05，清理可能存在的旧数据
      // set string // String 类型数据：Redis 最基本的数据类型，存储键值对
      jedis.set("raw_01", "123"); // 设置键 raw_01 的值为 "123"，这是原始字符串数据
      jedis.set("json_01", "{\"DEPTNO\":10,\"NAME\":\"Sales\"}"); // 设置键 json_01 的值为 JSON 格式字符串，包含部门号和名称
      jedis.set("csv_01", "10:Sales"); // 设置键 csv_01 的值为 CSV 格式字符串，使用冒号分隔字段
      // set list // List 类型数据：有序的字符串集合，可以重复，使用 lpush 从左侧插入元素
      jedis.lpush("raw_02", "book1"); // 向列表 raw_02 左侧插入元素 "book1"，原始字符串数据
      jedis.lpush("raw_02", "book2"); // 向列表 raw_02 左侧插入元素 "book2"，原始字符串数据
      jedis.lpush("json_02", "{\"DEPTNO\":10,\"NAME\":\"Sales1\"}"); // 向列表 json_02 左侧插入 JSON 格式元素
      jedis.lpush("json_02", "{\"DEPTNO\":20,\"NAME\":\"Sales2\"}"); // 向列表 json_02 左侧插入 JSON 格式元素
      jedis.lpush("csv_02", "10:Sales"); // 向列表 csv_02 左侧插入 CSV 格式元素
      jedis.lpush("csv_02", "20:Sales"); // 向列表 csv_02 左侧插入 CSV 格式元素
      // set Set // Set 类型数据：无序的唯一字符串集合，不允许重复元素
      jedis.sadd("raw_03", "user1"); // 向集合 raw_03 添加元素 "user1"，原始字符串数据
      jedis.sadd("raw_03", "user2"); // 向集合 raw_03 添加元素 "user2"，原始字符串数据
      jedis.sadd("json_03", "{\"DEPTNO\":10,\"NAME\":\"Sales1\"}"); // 向集合 json_03 添加 JSON 格式元素
      jedis.sadd("json_03", "{\"DEPTNO\":20,\"NAME\":\"Sales1\"}"); // 向集合 json_03 添加 JSON 格式元素
      jedis.sadd("csv_03", "10:Sales"); // 向集合 csv_03 添加 CSV 格式元素
      jedis.sadd("csv_03", "20:Sales"); // 向集合 csv_03 添加 CSV 格式元素
      // set sortSet // Sorted Set 类型数据：有序的唯一字符串集合，每个元素关联一个分数用于排序
      jedis.zadd("raw_04", 22, "user3"); // 向有序集合 raw_04 添加元素 "user3"，分数为 22
      jedis.zadd("raw_04", 24, "user4"); // 向有序集合 raw_04 添加元素 "user4"，分数为 24
      jedis.zadd("json_04", 1, "{\"DEPTNO\":10,\"NAME\":\"Sales1\"}"); // 向有序集合 json_04 添加 JSON 元素，分数为 1
      jedis.zadd("json_04", 2, "{\"DEPTNO\":11,\"NAME\":\"Sales2\"}"); // 向有序集合 json_04 添加 JSON 元素，分数为 2
      jedis.zadd("csv_04", 1, "10:Sales"); // 向有序集合 csv_04 添加 CSV 元素，分数为 1
      jedis.zadd("csv_04", 2, "20:Sales"); // 向有序集合 csv_04 添加 CSV 元素，分数为 2
      // set map // Hash 类型数据：存储键值对的映射，类似于 Java 的 Map
      Map<String, String> raw5 = new HashMap<>(); // 创建 HashMap 用于存储 raw_05 的字段和值
      raw5.put("stuA", "a1"); // 向 map 中添加键值对，学生 A 的值为 "a1"
      raw5.put("stuB", "b2"); // 向 map 中添加键值对，学生 B 的值为 "b2"
      jedis.hmset("raw_05", raw5); // 将 map 设置为 Redis 哈希 raw_05 的字段值

      Map<String, String> json5 = new HashMap<>(); // 创建 HashMap 用于存储 json_05 的字段和值
      json5.put("stuA", "{\"DEPTNO\":10,\"NAME\":\"stuA\"}"); // 向 map 中添加键值对，值为 JSON 格式字符串
      json5.put("stuB", "{\"DEPTNO\":10,\"NAME\":\"stuB\"}"); // 向 map 中添加键值对，值为 JSON 格式字符串
      jedis.hmset("json_05", json5); // 将 map 设置为 Redis 哈希 json_05 的字段值

      Map<String, String> csv5 = new HashMap<>(); // 创建 HashMap 用于存储 csv_05 的字段和值
      csv5.put("stuA", "10:Sales"); // 向 map 中添加键值对，值为 CSV 格式字符串
      csv5.put("stuB", "20:Sales"); // 向 map 中添加键值对，值为 CSV 格式字符串
      jedis.hmset("csv_05", csv5); // 将 map 设置为 Redis 哈希 csv_05 的字段值
    } // try-with-resources 自动关闭 Jedis 连接，将连接归还给连接池
  }

  @EnabledIfEnvironmentVariable(named = "RedisMiniServerEnabled", matches = "true") // 条件注解，只有当环境变量 RedisMiniServerEnabled 设置为 true 时才执行此测试
  @Test void redisServerMiniTest() { // 测试方法，用于验证 Redis 服务器是否正常启动和运行（方法体为空，实际测试在其他测试类中进行）
  } // 测试方法结束
} // 类定义结束
