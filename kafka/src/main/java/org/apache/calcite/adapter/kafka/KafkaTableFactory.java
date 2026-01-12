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
package org.apache.calcite.adapter.kafka; // 定义包名，表示该类属于org.apache.calcite.adapter.kafka包，是Calcite框架中Kafka适配器的一部分

import org.apache.calcite.rel.type.RelDataType; // 导入Calcite的关系数据类型类，用于描述表的结构和列的类型信息
import org.apache.calcite.schema.SchemaPlus; // 导入Calcite的模式类，表示一个可以包含表和函数的模式
import org.apache.calcite.schema.TableFactory; // 导入Calcite的表工厂接口，用于创建表实例

import org.apache.kafka.clients.consumer.Consumer; // 导入Kafka消费者接口，用于从Kafka主题消费消息
import org.apache.kafka.clients.consumer.OffsetResetStrategy; // 导入Kafka的偏移量重置策略枚举，定义了当没有初始偏移量或当前偏移量无效时的处理策略

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的参数或返回值

import java.lang.reflect.InvocationTargetException; // 导入反射异常类，当通过反射调用方法抛出异常时使用
import java.util.Locale; // 导入本地化类，用于格式化字符串和区域相关的操作
import java.util.Map; // 导入Map接口，用于存储键值对配置信息

/**
 * Implementation of {@link TableFactory} for Apache Kafka. Currently an Apache Kafka
 * topic is mapping to a STREAM table.
 */
// 类注释：这是Apache Kafka的TableFactory接口实现类，用于在Calcite中创建Kafka表，目前将Kafka主题映射为流表（STREAM table）
public class KafkaTableFactory implements TableFactory<KafkaStreamTable> { // 定义KafkaTableFactory类，实现TableFactory接口，泛型参数为KafkaStreamTable表示创建的表类型
  public KafkaTableFactory() { // 默认构造方法，无参构造器，用于创建KafkaTableFactory实例
  } // 构造方法结束，目前不需要任何初始化操作

  @Override public KafkaStreamTable create(SchemaPlus schema, // 重写TableFactory接口的create方法，用于创建KafkaStreamTable实例，参数schema表示包含此表的SchemaPlus对象
      String name, // 参数name表示要创建的表的名称
      Map<String, Object> operand, // 参数operand表示表的配置参数映射，包含Kafka相关的配置信息
      @Nullable RelDataType rowType) { // 参数rowType表示表的行类型信息，可能为null，使用@Nullable注解标记
    final KafkaTableOptions tableOptionBuilder = new KafkaTableOptions(); // 创建KafkaTableOptions构建器对象，用于配置Kafka表的各项参数

    tableOptionBuilder.setBootstrapServers( // 设置Kafka集群的引导服务器地址，这是连接Kafka集群必需的参数
        (String) operand.getOrDefault(KafkaTableConstants.SCHEMA_BOOTSTRAP_SERVERS, null)); // 从operand中获取bootstrap.servers配置，如果不存在则返回null
    tableOptionBuilder.setTopicName( // 设置要订阅的Kafka主题名称，这是指定要消费的Kafka主题
        (String) operand.getOrDefault(KafkaTableConstants.SCHEMA_TOPIC_NAME, null)); // 从operand中获取topic.name配置，如果不存在则返回null

    final KafkaRowConverter rowConverter; // 声明行转换器变量，用于将Kafka消息转换为Calcite可识别的行数据
    if (operand.containsKey(KafkaTableConstants.SCHEMA_ROW_CONVERTER)) { // 检查operand中是否包含自定义行转换器的配置
      String rowConverterClass = (String) operand.get(KafkaTableConstants.SCHEMA_ROW_CONVERTER); // 获取自定义行转换器的完整类名
      try { // 开始try块，用于捕获反射创建实例时可能抛出的异常
        final Class<?> klass = Class.forName(rowConverterClass); // 通过反射加载指定的行转换器类
        rowConverter = (KafkaRowConverter) klass.getDeclaredConstructor().newInstance(); // 通过反射调用无参构造方法创建行转换器实例，并强制转换为KafkaRowConverter类型
      } catch (InstantiationException | InvocationTargetException // 捕获实例化异常和调用目标异常
          | IllegalAccessException | ClassNotFoundException // 捕获非法访问异常和类未找到异常
          | NoSuchMethodException e) { // 捕获无此方法异常
        final String details = // 创建详细的错误信息字符串
            String.format(Locale.ROOT, // 使用ROOT本地化环境格式化字符串，确保错误信息格式一致
                "Failed to create table '%s' with configuration:\n" // 错误信息第一部分：创建表失败
                    + "'%s'\n" // 错误信息第二部分：配置详情
                    + "KafkaRowConverter '%s' is invalid", // 错误信息第三部分：行转换器类名无效
                name, operand, rowConverterClass); // 填充格式化字符串的参数：表名、配置对象、行转换器类名
        throw new RuntimeException(details, e); // 抛出运行时异常，包含详细错误信息和原始异常
      } // catch块结束
    } else { // 如果operand中没有自定义行转换器配置
      rowConverter = new KafkaRowConverterImpl(); // 使用默认的行转换器实现类创建实例
    } // if-else块结束
    tableOptionBuilder.setRowConverter(rowConverter); // 将配置好的行转换器设置到表选项构建器中

    if (operand.containsKey(KafkaTableConstants.SCHEMA_CONSUMER_PARAMS)) { // 检查operand中是否包含Kafka消费者参数配置
      tableOptionBuilder.setConsumerParams( // 设置Kafka消费者的额外参数，如group.id、auto.offset.reset等
          (Map<String, String>) operand.get(KafkaTableConstants.SCHEMA_CONSUMER_PARAMS)); // 从operand中获取消费者参数映射，并强制类型转换为Map<String, String>
    } // if块结束
    if (operand.containsKey(KafkaTableConstants.SCHEMA_CUST_CONSUMER)) { // 检查operand中是否包含自定义Kafka消费者配置
      String custConsumerClass = (String) operand.get(KafkaTableConstants.SCHEMA_CUST_CONSUMER); // 获取自定义消费者类的完整类名
      try { // 开始try块，用于捕获反射创建消费者实例时可能抛出的异常
        tableOptionBuilder.setConsumer( // 设置自定义的Kafka消费者实例
            (Consumer) Class.forName(custConsumerClass) // 通过反射加载自定义消费者类
                .getConstructor(OffsetResetStrategy.class) // 获取接受OffsetResetStrategy参数的构造方法
                .newInstance(OffsetResetStrategy.NONE)); // 调用构造方法创建实例，传入OffsetResetStrategy.NONE作为参数，表示不自动重置偏移量
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException // 捕获类未找到、无此方法、非法访问异常
          | InstantiationException | InvocationTargetException e) { // 捕获实例化和调用目标异常
        final String details = // 创建详细的错误信息字符串
            String.format(Locale.ROOT, // 使用ROOT本地化环境格式化字符串
                "Fail to create table '%s' with configuration:\n" // 错误信息第一部分：创建表失败
                    + "'%s'\n" // 错误信息第二部分：配置详情
                    + "KafkaCustConsumer '%s' is invalid", // 错误信息第三部分：自定义消费者类名无效
                name, operand, custConsumerClass); // 填充格式化字符串的参数：表名、配置对象、消费者类名
        throw new RuntimeException(details, e); // 抛出运行时异常，包含详细错误信息和原始异常
      } // catch块结束
    } // if块结束

    return new KafkaStreamTable(tableOptionBuilder); // 创建并返回KafkaStreamTable实例，传入配置好的表选项对象
  } // create方法结束
} // KafkaTableFactory类定义结束
