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
package org.apache.calcite.adapter.redis; // Redis适配器测试基类所在的包，用于测试Redis数据源的SQL查询功能

import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类，用于获取测试开关等配置
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言工具类，用于SQL查询测试
import org.apache.calcite.util.Sources; // 导入资源工具类，用于加载配置文件

import com.fasterxml.jackson.core.JsonParser; // 导入Jackson JSON解析器，用于解析JSON配置文件
import com.fasterxml.jackson.databind.JsonNode; // 导入Jackson JSON节点类，表示JSON树结构
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson对象映射器，用于JSON与Java对象的转换

import org.junit.jupiter.api.BeforeEach; // 导入JUnit5的BeforeEach注解，标记在每个测试方法前执行的方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，标记测试方法

import java.io.File; // 导入Java文件类，用于文件操作
import java.util.HashMap; // 导入Java HashMap类，用于存储键值对映射
import java.util.Map; // 导入Java Map接口，表示映射关系

import redis.clients.jedis.Protocol; // 导入Redis协议类，用于获取Redis默认端口等配置

import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入JUnit5断言方法，用于验证对象不为null

/**
 * Tests for the {@code org.apache.calcite.adapter.redis} package.
 * Redis适配器包的测试基类，提供Redis数据源的SQL查询测试功能
 * 该类继承自RedisDataCaseBase，用于测试Redis适配器的各种查询场景
 * 包括基础查询、连接查询等，支持多种数据格式（CSV、JSON、原始数据）
 */
public class RedisAdapterCaseBase extends RedisDataCaseBase { // Redis适配器测试基类，继承RedisDataCaseBase获取Redis数据操作能力
  /**
   * URL of the "redis-zips" model.
   * Redis混合模型配置文件的绝对路径，该JSON文件定义了Redis数据源的各种表结构
   * 包括表名、数据格式、字段映射等信息，用于构建Calcite模型
   */
  private final String filePath = // 存储Redis模型配置文件的绝对路径，final表示初始化后不可修改
      Sources.of(RedisAdapterCaseBase.class.getResource("/redis-mix-model.json")) // 从类路径加载redis-mix-model.json资源文件
          .file().getAbsolutePath(); // 获取File对象并转换为绝对路径字符串

  private String model; // 存储解析后的Redis模型配置字符串，包含表结构、连接信息等，用于构建Calcite查询引擎

  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告，因为HashMap使用了泛型
  private static final Map<String, Integer> TABLE_MAPS = new HashMap(15); // 测试表名与预期行数的映射表，用于验证查询结果，static final表示类级别的常量

  static { // 静态初始化块，在类加载时执行一次，用于初始化TABLE_MAPS映射表
    TABLE_MAPS.put("raw_01", 1); // 原始数据表raw_01预期有1条记录
    TABLE_MAPS.put("raw_02", 2); // 原始数据表raw_02预期有2条记录
    TABLE_MAPS.put("raw_03", 2); // 原始数据表raw_03预期有2条记录
    TABLE_MAPS.put("raw_04", 2); // 原始数据表raw_04预期有2条记录
    TABLE_MAPS.put("raw_05", 2); // 原始数据表raw_05预期有2条记录
    TABLE_MAPS.put("csv_01", 1); // CSV格式表csv_01预期有1条记录
    TABLE_MAPS.put("csv_02", 2); // CSV格式表csv_02预期有2条记录
    TABLE_MAPS.put("csv_03", 2); // CSV格式表csv_03预期有2条记录
    TABLE_MAPS.put("csv_04", 2); // CSV格式表csv_04预期有2条记录
    TABLE_MAPS.put("csv_05", 2); // CSV格式表csv_05预期有2条记录
    TABLE_MAPS.put("json_01", 1); // JSON格式表json_01预期有1条记录
    TABLE_MAPS.put("json_02", 2); // JSON格式表json_02预期有2条记录
    TABLE_MAPS.put("json_03", 2); // JSON格式表json_03预期有2条记录
    TABLE_MAPS.put("json_04", 2); // JSON格式表json_04预期有2条记录
    TABLE_MAPS.put("json_05", 2); // JSON格式表json_05预期有2条记录
  }

  @BeforeEach // JUnit5注解，标记在每个测试方法执行前运行此方法，用于初始化测试数据
  @Override public void makeData() { // 重写父类的makeData方法，用于准备测试数据
    super.makeData(); // 调用父类RedisDataCaseBase的makeData方法，初始化Redis测试数据
    readModelByJson(); // 读取并解析JSON模型配置文件，构建Calcite模型字符串
  }

  /**
   * Whether to run this test.
   * 判断是否启用Redis测试，通过系统属性控制测试的执行
   * @return 如果系统属性TEST_REDIS为true则返回true，否则返回false
   */
  private boolean enabled() { // 私有方法，用于判断是否启用Redis测试
    return CalciteSystemProperty.TEST_REDIS.value(); // 返回系统属性TEST_REDIS的值，控制测试是否执行
  }

  /**
   * Creates a query against a data set given by a map.
   * 创建一个SQL查询断言对象，用于执行和验证SQL查询
   * @param sql 要执行的SQL查询语句
   * @return CalciteAssert.AssertQuery对象，可以链式调用验证方法
   */
  private CalciteAssert.AssertQuery sql(String sql) { // 私有方法，用于创建SQL查询断言
    assertNotNull(model, "model cannot be null!"); // 断言model不为null，确保模型已正确加载
    return CalciteAssert.model(model) // 使用模型配置创建CalciteAssert对象
        .enable(enabled()) // 根据enabled()方法的返回值决定是否启用测试
        .query(sql); // 设置要执行的SQL查询语句，返回AssertQuery对象用于验证结果
  }

  private void readModelByJson() { // 私有方法，从JSON文件读取并解析Redis模型配置
    String strResult = null; // 初始化结果字符串为null
    try { // 开始异常处理块，捕获JSON解析过程中的异常
      ObjectMapper objMapper = new ObjectMapper(); // 创建Jackson ObjectMapper对象，用于JSON解析
      objMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true) // 配置解析器允许字段名不加引号
          .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true) // 配置解析器允许使用单引号
          .configure(JsonParser.Feature.ALLOW_COMMENTS, true); // 配置解析器允许JSON中包含注释
      File file = new File(filePath); // 根据filePath创建File对象
      if (file.exists()) { // 检查配置文件是否存在
        JsonNode rootNode = objMapper.readTree(file); // 读取JSON文件并解析为JsonNode树结构
        strResult = // 将解析后的JSON转换为字符串，并替换Redis端口
            rootNode.toString().replace(Integer.toString(Protocol.DEFAULT_PORT), // 将默认Redis端口6379替换为实际测试端口
                Integer.toString(getRedisServerPort())); // 获取当前测试使用的Redis服务器端口
      }
    } catch (Exception ignored) { // 捕获所有异常，避免配置文件解析失败导致测试中断
    }
    model = strResult; // 将解析后的模型字符串赋值给model成员变量
  }

  @Test void testRedisBySql() { // 测试方法，验证Redis适配器的基础SQL查询功能
    TABLE_MAPS.forEach((table, count) -> { // 遍历TABLE_MAPS中的所有表名和预期行数
      String sql = "Select count(*) as c from \"" + table + "\" where true"; // 构造SQL查询语句，统计每个表的记录数
      sql(sql).returnsUnordered("C=" + count); // 执行SQL查询并验证结果，期望返回的count值与预期一致
    });
  }

  @Test void testSqlWithJoin() { // 测试方法，验证Redis适配器的连接查询功能
    String sql = "Select a.DEPTNO, b.NAME " // 构造SQL连接查询语句，从csv_01表和json_02表进行左连接
        + "from \"csv_01\" a left join \"json_02\" b " // 使用左连接，以csv_01表为主表
        + "on a.DEPTNO=b.DEPTNO where true"; // 连接条件是两个表的DEPTNO字段相等
    sql(sql).returnsUnordered("DEPTNO=10; NAME=\"Sales1\""); // 执行SQL查询并验证结果，期望返回部门编号10和名称Sales1
  }
}
