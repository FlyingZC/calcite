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
package org.apache.calcite.adapter.redis; // 导入Redis适配器相关包

import org.junit.jupiter.api.AfterEach; // 导入JUnit5的AfterEach注解，用于在每个测试方法执行后执行清理操作
import org.junit.jupiter.api.BeforeEach; // 导入JUnit5的BeforeEach注解，用于在每个测试方法执行前执行初始化操作

import java.util.HashMap; // 导入HashMap类，用于创建哈希映射数据结构
import java.util.Map; // 导入Map接口，用于存储键值对数据

import redis.clients.jedis.Jedis; // 导入Jedis客户端，用于与Redis服务器进行交互
import redis.clients.jedis.JedisPool; // 导入Jedis连接池，用于管理Redis连接
import redis.clients.jedis.JedisPoolConfig; // 导入Jedis连接池配置类，用于配置连接池参数

/**
 * RedisDataCaseBase类是Redis适配器测试的基础测试类，继承自RedisCaseBase
 * 该类主要负责为Redis适配器的测试提供数据准备和环境管理功能
 * 它提供了Redis连接池的管理、测试数据的创建和清理等功能
 * 支持多种Redis数据类型（String、List、Set、Sorted Set、Hash）的测试数据准备
 * 支持多种数据格式（raw原始数据、csv格式、json格式）的测试数据准备
 */
public class RedisDataCaseBase extends RedisCaseBase { // 定义RedisDataCaseBase类，继承自RedisCaseBase基类，复用其Redis服务器配置功能
  private JedisPool pool; // 定义Redis连接池成员变量，用于管理与Redis服务器的连接，避免频繁创建和销毁连接

  final String[] tableNames = { // 定义测试表名称数组，包含15个Redis键名，用于测试不同数据类型和数据格式
      "raw_01", "raw_02", "raw_03", "raw_04", "raw_05", // raw开头的键名表示原始数据格式，分别对应String、List、Set、Sorted Set、Hash五种数据类型
      "csv_01", "csv_02", "csv_03", "csv_04", "csv_05", // csv开头的键名表示CSV格式数据，分别对应String、List、Set、Sorted Set、Hash五种数据类型
      "json_01", "json_02", "json_03", "json_04", "json_05" // json开头的键名表示JSON格式数据，分别对应String、List、Set、Sorted Set、Hash五种数据类型
  };

  @BeforeEach // 使用JUnit5的BeforeEach注解，标记该方法在每个测试方法执行前自动调用
  public void setUp() { // 定义setUp初始化方法，用于在每个测试方法执行前初始化Redis连接池和清空Redis数据
    try { // 使用try-catch块捕获可能的异常
      JedisPoolConfig jedisPoolConfig = new JedisPoolConfig(); // 创建Jedis连接池配置对象，用于配置连接池的各种参数
      jedisPoolConfig.setMaxTotal(10); // 设置连接池中最大连接数为10，限制同时可用的Redis连接数量
      pool = new JedisPool(jedisPoolConfig,  getRedisServerHost(), getRedisServerPort()); // 创建Jedis连接池实例，传入配置对象、Redis服务器主机名和端口号（从父类RedisCaseBase获取）

      // Flush all data // 注释说明：清空所有Redis数据库中的数据
      try (Jedis jedis = pool.getResource()) { // 使用try-with-resources语法从连接池获取Jedis连接，确保连接使用后自动关闭
        jedis.flushAll(); // 执行flushAll命令，清空Redis服务器中所有数据库的所有数据，确保测试环境干净
      } // try-with-resources自动关闭Jedis连接，将连接归还到连接池

    } catch (Exception e) { // 捕获所有可能的异常
      throw e; // 重新抛出异常，让测试框架处理异常并标记测试失败
    } // 结束异常处理
  } // 结束setUp方法

  public void makeData() { // 定义makeData方法，用于在Redis中创建各种类型的测试数据，包括String、List、Set、Sorted Set和Hash五种数据类型，每种类型支持raw、csv、json三种格式
    try (Jedis jedis = pool.getResource()) { // 使用try-with-resources语法从连接池获取Jedis连接，确保连接使用后自动关闭
      jedis.del(tableNames); // 删除数组中所有指定的键，确保这些键不存在，避免数据冲突
      // set string // 注释说明：设置String类型的测试数据
      jedis.set("raw_01", "123"); // 在Redis中设置键"raw_01"的值为字符串"123"，这是原始格式的String类型数据
      jedis.set("json_01", "{\"DEPTNO\":10,\"NAME\":\"Sales\"}"); // 在Redis中设置键"json_01"的值为JSON格式字符串，包含DEPTNO和NAME两个字段，这是JSON格式的String类型数据
      jedis.set("csv_01", "10:Sales"); // 在Redis中设置键"csv_01"的值为CSV格式字符串，使用冒号分隔字段，这是CSV格式的String类型数据
      // set list // 注释说明：设置List类型的测试数据
      jedis.lpush("raw_02", "book1", "book2"); // 使用lpush命令向列表"raw_02"的头部插入两个元素"book1"和"book2"，列表中的元素顺序为["book2", "book1"]
      jedis.lpush("json_02", "{\"DEPTNO\":10,\"NAME\":\"Sales1\"}", "{\"DEPTNO\":20," // 使用lpush命令向列表"json_02"的头部插入两个JSON格式字符串元素，包含DEPTNO和NAME字段
          + "\"NAME\":\"Sales2\"}"); // 继续插入第二个JSON元素，列表中的元素顺序为第二个JSON在前，第一个JSON在后
      jedis.lpush("csv_02", "10:Sales", "20:Sales"); // 使用lpush命令向列表"csv_02"的头部插入两个CSV格式字符串元素，使用冒号分隔字段
      // set Set // 注释说明：设置Set类型的测试数据
      jedis.sadd("raw_03", "user1", "user2"); // 使用sadd命令向集合"raw_03"中添加两个元素"user1"和"user2"，集合中的元素是无序且不重复的
      jedis.sadd("json_03", "{\"DEPTNO\":10,\"NAME\":\"Sales1\"}", "{\"DEPTNO\":20," // 使用sadd命令向集合"json_03"中添加两个JSON格式字符串元素
          + "\"NAME\":\"Sales1\"}"); // 继续添加第二个JSON元素，集合会自动去重
      jedis.sadd("csv_03", "10:Sales", "20:Sales"); // 使用sadd命令向集合"csv_03"中添加两个CSV格式字符串元素
      // set sortSet // 注释说明：设置Sorted Set（有序集合）类型的测试数据
      jedis.zadd("raw_04", 22, "user3"); // 使用zadd命令向有序集合"raw_04"中添加元素"user3"，其分数（排序权重）为22
      jedis.zadd("raw_04", 24, "user4"); // 使用zadd命令向有序集合"raw_04"中添加元素"user4"，其分数为24，元素会按分数从小到大排序
      jedis.zadd("json_04", 1, "{\"DEPTNO\":10,\"NAME\":\"Sales1\"}"); // 使用zadd命令向有序集合"json_04"中添加JSON格式元素，分数为1
      jedis.zadd("json_04", 2, "{\"DEPTNO\":11,\"NAME\":\"Sales2\"}"); // 使用zadd命令向有序集合"json_04"中添加第二个JSON格式元素，分数为2
      jedis.zadd("csv_04", 1, "10:Sales"); // 使用zadd命令向有序集合"csv_04"中添加CSV格式元素，分数为1
      jedis.zadd("csv_04", 2, "20:Sales"); // 使用zadd命令向有序集合"csv_04"中添加第二个CSV格式元素，分数为2
      // set map // 注释说明：设置Map（Hash）类型的测试数据
      Map<String, String> raw_05 = new HashMap<>(); // 创建一个HashMap对象，用于存储raw格式的Hash数据
      raw_05.put("stuA", "a1"); // 向HashMap中添加键值对，键为"stuA"，值为"a1"
      raw_05.put("stuB", "b2"); // 向HashMap中添加键值对，键为"stuB"，值为"b2"
      jedis.hmset("raw_05", raw_05); // 使用hmset命令将HashMap中的所有键值对设置到Redis的Hash结构"raw_05"中

      Map<String, String> json_05 = new HashMap<>(); // 创建一个HashMap对象，用于存储json格式的Hash数据
      json_05.put("stuA", "{\"DEPTNO\":10,\"NAME\":\"stuA\"}"); // 向HashMap中添加键值对，键为"stuA"，值为JSON格式字符串
      json_05.put("stuB", "{\"DEPTNO\":10,\"NAME\":\"stuB\"}"); // 向HashMap中添加键值对，键为"stuB"，值为JSON格式字符串
      jedis.hmset("json_05", json_05); // 使用hmset命令将HashMap中的所有键值对设置到Redis的Hash结构"json_05"中

      Map<String, String> csv_05 = new HashMap<>(); // 创建一个HashMap对象，用于存储csv格式的Hash数据
      csv_05.put("stuA", "10:Sales"); // 向HashMap中添加键值对，键为"stuA"，值为CSV格式字符串
      csv_05.put("stuB", "20:Sales"); // 向HashMap中添加键值对，键为"stuB"，值为CSV格式字符串
      jedis.hmset("csv_05", csv_05); // 使用hmset命令将HashMap中的所有键值对设置到Redis的Hash结构"csv_05"中
    } // try-with-resources自动关闭Jedis连接，将连接归还到连接池
  } // 结束makeData方法

  @AfterEach // 使用JUnit5的AfterEach注解，标记该方法在每个测试方法执行后自动调用
  public void shutDown() { // 定义shutDown清理方法，用于在每个测试方法执行后清理资源
    if (null != pool) { // 检查连接池对象是否不为null，避免空指针异常
      pool.destroy(); // 销毁连接池，关闭所有连接并释放相关资源
    } // 结束条件判断
  } // 结束shutDown方法
} // 结束RedisDataCaseBase类定义
