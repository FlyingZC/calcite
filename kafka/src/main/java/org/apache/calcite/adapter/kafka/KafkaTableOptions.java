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
package org.apache.calcite.adapter.kafka; // Kafka适配器包，包含Kafka数据源相关的类

import org.apache.kafka.clients.consumer.Consumer; // 导入Kafka消费者接口，用于消费Kafka消息

import java.util.Map; // 导入Map接口，用于存储键值对配置参数

/**
 * Available options for {@link KafkaStreamTable}. // KafkaStreamTable类的可用配置选项类
 * 该类是一个配置选项容器，用于封装Kafka流表所需的各种配置参数，采用Builder模式支持链式调用
 * 用于配置Kafka数据源的连接信息、主题名称、行转换器、消费者参数等
 */
public final class KafkaTableOptions { // 最终类，不可被继承，确保配置对象的不可变性和安全性
  private String bootstrapServers; // Kafka集群的引导服务器地址列表，格式为"host1:port1,host2:port2"，用于建立初始连接
  private String topicName; // Kafka主题名称，指定要从哪个主题消费数据
  private KafkaRowConverter rowConverter; // Kafka行转换器，负责将Kafka消息记录转换为Calcite可识别的行数据结构
  private Map<String, String> consumerParams; // Kafka消费者的配置参数映射，包含消费者启动所需的各种配置项（如group.id、enable.auto.commit等）
  // added to inject MockConsumer for testing. // 添加用于注入MockConsumer以支持单元测试
  private Consumer consumer; // Kafka消费者实例，实际消费Kafka消息的客户端对象，支持注入MockConsumer用于测试

  public String getBootstrapServers() { // 获取Kafka引导服务器地址列表的getter方法
    return bootstrapServers; // 返回当前配置的bootstrapServers值
  }

  public KafkaTableOptions setBootstrapServers(final String bootstrapServers) { // 设置Kafka引导服务器地址列表的setter方法，支持链式调用
    this.bootstrapServers = bootstrapServers; // 将传入的bootstrapServers值赋给成员变量
    return this; // 返回当前对象实例，支持链式调用
  }

  public String getTopicName() { // 获取Kafka主题名称的getter方法
    return topicName; // 返回当前配置的topicName值
  }

  public KafkaTableOptions setTopicName(final String topicName) { // 设置Kafka主题名称的setter方法，支持链式调用
    this.topicName = topicName; // 将传入的topicName值赋给成员变量
    return this; // 返回当前对象实例，支持链式调用
  }

  public KafkaRowConverter getRowConverter() { // 获取Kafka行转换器的getter方法
    return rowConverter; // 返回当前配置的rowConverter对象
  }

  public KafkaTableOptions setRowConverter( // 设置Kafka行转换器的setter方法，支持链式调用
      final KafkaRowConverter rowConverter) { // 参数：KafkaRowConverter类型的行转换器对象
    this.rowConverter = rowConverter; // 将传入的rowConverter对象赋给成员变量
    return this; // 返回当前对象实例，支持链式调用
  }

  public Map<String, String> getConsumerParams() { // 获取Kafka消费者配置参数的getter方法
    return consumerParams; // 返回当前配置的consumerParams映射集合
  }

  public KafkaTableOptions setConsumerParams(final Map<String, String> consumerParams) { // 设置Kafka消费者配置参数的setter方法，支持链式调用
    this.consumerParams = consumerParams; // 将传入的consumerParams映射赋给成员变量
    return this; // 返回当前对象实例，支持链式调用
  }

  public Consumer getConsumer() { // 获取Kafka消费者实例的getter方法
    return consumer; // 返回当前配置的consumer对象
  }

  public KafkaTableOptions setConsumer(final Consumer consumer) { // 设置Kafka消费者实例的setter方法，支持链式调用，用于注入实际的Consumer或MockConsumer
    this.consumer = consumer; // 将传入的consumer对象赋给成员变量
    return this; // 返回当前对象实例，支持链式调用
  }
}
