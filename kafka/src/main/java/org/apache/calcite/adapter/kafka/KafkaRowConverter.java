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
// 声明包名，表示这个接口属于 Apache Calcite 的 Kafka 适配器模块
package org.apache.calcite.adapter.kafka;

// 导入 Calcite 的关系数据类型 RelDataType，用于描述 Calcite 中行的结构
import org.apache.calcite.rel.type.RelDataType;

// 导入 Kafka 消费者配置类，用于获取反序列化器配置信息
import org.apache.kafka.clients.consumer.ConsumerConfig;
// 导入 Kafka 消费者记录类，表示从 Kafka 主题中消费的一条消息
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Interface to handle formatting between Kafka message and Calcite row. // 接口作用：处理 Kafka 消息和 Calcite 行之间的格式转换
 *
 * @param <K> type for Kafka message key, // 泛型参数 K：表示 Kafka 消息的键类型
 *           refer to {@link ConsumerConfig#KEY_DESERIALIZER_CLASS_CONFIG}; // 参考 Kafka 消费者配置中的键反序列化器配置
 * @param <V> type for Kafka message value, // 泛型参数 V：表示 Kafka 消息的值类型
 *           refer to {@link ConsumerConfig#VALUE_DESERIALIZER_CLASS_CONFIG}; // 参考 Kafka 消费者配置中的值反序列化器配置
 *
 */
// 定义一个泛型接口，用于将 Kafka 消息转换为 Calcite 可以处理的行数据
public interface KafkaRowConverter<K, V> {

  /**
   * Generates the row type for a given Kafka topic. // 方法作用：为指定的 Kafka 主题生成 Calcite 行类型
   *
   * @param topicName Kafka topic name // 参数：Kafka 主题名称
   * @return row type // 返回值：Calcite 关系数据类型，描述了该主题消息对应的行结构
   */
  // 抽象方法：根据 Kafka 主题名称生成对应的 Calcite 行类型（RelDataType）
  // 这个行类型定义了该主题消息在 Calcite 中应该具有的字段结构
  RelDataType rowDataType(String topicName);

  /**
   * Parses and reformats a Kafka message from the consumer, // 方法作用：解析并重新格式化从 Kafka 消费者获取的消息
   * to align with row type defined as {@link #rowDataType(String)}. // 使其与 rowDataType 方法定义的行类型保持一致
   *
   * @param message Raw Kafka message record // 参数：原始的 Kafka 消息记录对象
   * @return fields in the row // 返回值：行中的字段数组，每个字段对应行类型中的一个字段
   */
  // 抽象方法：将 Kafka 消息记录转换为 Calcite 行数据（Object 数组）
  // 这个方法负责从 ConsumerRecord 中提取数据，并按照 rowDataType 定义的结构组织成字段数组
  Object[] toRow(ConsumerRecord<K, V> message);
}
