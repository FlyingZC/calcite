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
package org.apache.calcite.adapter.kafka; // Kafka适配器包，包含Kafka数据源适配器相关类

import org.apache.calcite.config.CalciteConnectionProperty; // Calcite连接属性配置类
import org.apache.calcite.test.CalciteAssert; // Calcite测试断言工具类，用于构建测试场景

import com.google.common.io.Resources; // Google Guava工具类，用于读取资源文件

import org.junit.jupiter.api.Test; // JUnit 5测试注解，标记测试方法

import java.io.IOException; // IO异常类
import java.io.UncheckedIOException; // 未检查的IO异常包装类
import java.net.URL; // URL资源定位类
import java.nio.charset.StandardCharsets; // 标准字符集编码

import static java.util.Objects.requireNonNull; // 对象非空检查工具方法

/**
 * Unit test cases for Kafka adapter. // Kafka适配器的单元测试类
 * // 该类用于测试Calcite框架中Kafka适配器的功能，包括流式查询、过滤、投影等操作
 * // 测试场景包括：从Kafka主题读取流式数据、应用过滤条件、自定义行转换器等
 */
  protected static final URL MODEL = // 静态常量：Kafka模型配置文件的URL资源路径
      requireNonNull(KafkaAdapterTest.class.getResource("/kafka.model.json")); // 获取kafka.model.json资源文件，如果为null则抛出NullPointerException

  private CalciteAssert.AssertThat assertModel(String model) { // 私有辅助方法：根据模型字符串创建Calcite断言构建器
    // ensure that Schema from this instance is being used // 确保使用当前实例的Schema
    model = model.replace(KafkaAdapterTest.class.getName(), KafkaAdapterTest.class.getName()); // 替换类名为当前类名（此处实际无变化，可能用于确保一致性）

    return CalciteAssert.that() // 创建Calcite断言构建器
        .withModel(model); // 设置模型配置字符串
  }

  private CalciteAssert.AssertThat assertModel(URL url) { // 私有辅助方法：根据模型URL创建Calcite断言构建器
    requireNonNull(url, "url"); // 检查URL参数不为null，否则抛出NullPointerException
    try {
      return assertModel(Resources.toString(url, StandardCharsets.UTF_8)); // 将URL资源读取为UTF-8编码的字符串，并调用assertModel(String)方法
    } catch (IOException e) { // 捕获IO异常
      throw new UncheckedIOException(e); // 将检查型IO异常包装为未检查异常抛出
    }
  }

  @Test void testSelect() { // 测试方法：测试从Kafka表进行流式SELECT查询
    assertModel(MODEL) // 使用MODEL配置创建断言构建器
        .query("SELECT STREAM * FROM KAFKA.MOCKTABLE") // 执行流式查询：从KAFKA.MOCKTABLE表选择所有列
        .limit(2) // 限制结果集为2条记录

        .typeIs("[MSG_PARTITION INTEGER NOT NULL" // 验证结果类型：包含分区、时间戳、偏移量、键、值字段
            + ", MSG_TIMESTAMP BIGINT NOT NULL" // MSG_TIMESTAMP字段类型为BIGINT且不允许为空
            + ", MSG_OFFSET BIGINT NOT NULL" // MSG_OFFSET字段类型为BIGINT且不允许为空
            + ", MSG_KEY_BYTES VARBINARY" // MSG_KEY_BYTES字段类型为VARBINARY（可变二进制）
            + ", MSG_VALUE_BYTES VARBINARY NOT NULL]") // MSG_VALUE_BYTES字段类型为VARBINARY且不允许为空

        .returnsUnordered( // 验证返回结果（不关注顺序）
            "MSG_PARTITION=0; MSG_TIMESTAMP=-1; MSG_OFFSET=0; MSG_KEY_BYTES=mykey0; MSG_VALUE_BYTES=myvalue0", // 第一条记录：分区0，时间戳-1，偏移量0
            "MSG_PARTITION=0; MSG_TIMESTAMP=-1; MSG_OFFSET=1" // 第二条记录：分区0，时间戳-1，偏移量1
                + "; MSG_KEY_BYTES=mykey1; MSG_VALUE_BYTES=myvalue1") // 键值对为mykey1和myvalue1

        .explainContains("PLAN=EnumerableInterpreter\n" // 验证执行计划包含可枚举解释器和可绑定表扫描
            + "  BindableTableScan(table=[[KAFKA, MOCKTABLE, (STREAM)]])\n"); // 扫描KAFKA.MOCKTABLE流式表
  }

  @Test void testFilterWithProject() { // 测试方法：测试带过滤条件和投影的流式查询
    assertModel(MODEL) // 使用MODEL配置创建断言构建器
        .with(CalciteConnectionProperty.TOPDOWN_OPT.camelName(), false) // 设置连接属性：禁用自顶向下优化（使用自底向上优化）
        .query("SELECT STREAM MSG_PARTITION,MSG_OFFSET,MSG_VALUE_BYTES FROM KAFKA.MOCKTABLE" // 执行流式查询：选择分区、偏移量、值字段
            + " WHERE MSG_OFFSET>0") // 添加过滤条件：只选择偏移量大于0的记录
        .limit(1) // 限制结果集为1条记录

        .returnsUnordered( // 验证返回结果（不关注顺序）
            "MSG_PARTITION=0; MSG_OFFSET=1; MSG_VALUE_BYTES=myvalue1") // 期望结果：分区0，偏移量1，值为myvalue1
        .explainContains( // 验证执行计划包含Calc计算节点
            "PLAN=EnumerableCalc(expr#0..4=[{inputs}], expr#5=[0:BIGINT], expr#6=[>($t2, $t5)], MSG_PARTITION=[$t0], MSG_OFFSET=[$t2], MSG_VALUE_BYTES=[$t4], $condition=[$t6])\n" // Calc表达式：过滤条件MSG_OFFSET>0，投影指定字段
                + "  EnumerableInterpreter\n" // 可枚举解释器节点
                + "    BindableTableScan(table=[[KAFKA, MOCKTABLE, (STREAM)]])"); // 底层扫描KAFKA.MOCKTABLE流式表
  }

  @Test void testCustRowConverter() { // 测试方法：测试自定义行转换器功能
    assertModel(MODEL) // 使用MODEL配置创建断言构建器
        .query("SELECT STREAM * FROM KAFKA.MOCKTABLE_CUST_ROW_CONVERTER") // 执行流式查询：从使用自定义行转换器的表选择所有列
        .limit(2) // 限制结果集为2条记录

        .typeIs("[TOPIC_NAME VARCHAR NOT NULL" // 验证结果类型：包含主题名、分区ID、时间戳类型字段
            + ", PARTITION_ID INTEGER NOT NULL" // PARTITION_ID字段类型为INTEGER且不允许为空
            + ", TIMESTAMP_TYPE VARCHAR]") // TIMESTAMP_TYPE字段类型为VARCHAR（可空）

        .returnsUnordered( // 验证返回结果（不关注顺序）
            "TOPIC_NAME=testtopic; PARTITION_ID=0; TIMESTAMP_TYPE=NoTimestampType", // 第一条记录：主题testtopic，分区0，无时间戳类型
            "TOPIC_NAME=testtopic; PARTITION_ID=0; TIMESTAMP_TYPE=NoTimestampType") // 第二条记录：主题testtopic，分区0，无时间戳类型

        .explainContains("PLAN=EnumerableInterpreter\n" // 验证执行计划包含可枚举解释器
            + "  BindableTableScan(table=[[KAFKA, MOCKTABLE_CUST_ROW_CONVERTER, (STREAM)]])\n"); // 扫描KAFKA.MOCKTABLE_CUST_ROW_CONVERTER流式表（使用自定义行转换器）
  }


  @Test void testAsBatch() { // 测试方法：测试将流式表当作批处理表使用（应该失败）
    assertModel(MODEL) // 使用MODEL配置创建断言构建器
        .query("SELECT * FROM KAFKA.MOCKTABLE") // 执行查询：不带STREAM关键字，尝试将流表当作批处理表使用
        .failsAtValidation("Cannot convert stream 'MOCKTABLE' to relation"); // 验证查询在验证阶段失败，并抛出特定错误消息
  } // 该测试确保流式表不能被当作批处理表使用，这是Calcite流处理的重要约束
}
