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
package org.apache.calcite.adapter.kafka;

/**
 * 用于定义 Kafka 表的参数常量接口
 * 该接口定义了在 Calcite 中创建 Kafka 表时所需的各种配置参数的常量名称
 * 这些参数通常在模型配置文件（如 JSON 或 YAML）中使用，用于指定 Kafka 连接信息和表定义
 * Kafka 允许将 Kafka 主题作为 Calcite 中的表进行查询，这些常量用于配置如何访问和解析 Kafka 消息
 */
interface KafkaTableConstants {
  // Kafka 主题名称参数，指定要读取的 Kafka 主题
  // 在模型配置中用于标识要查询的特定 Kafka 主题
  // 例如：在 JSON 配置中 "topic.name": "my_topic" 表示查询名为 my_topic 的主题
  String SCHEMA_TOPIC_NAME = "topic.name";
  // Kafka 引导服务器参数，指定 Kafka 集群的地址列表
  // 这是 Kafka 客户端连接到集群的必需参数，格式为 host:port，多个服务器用逗号分隔
  // 例如："bootstrap.servers": "localhost:9092" 或 "bootstrap.servers": "host1:9092,host2:9092"
  String SCHEMA_BOOTSTRAP_SERVERS = "bootstrap.servers";
  // 行转换器参数，指定用于将 Kafka 消息转换为 Calcite 行的转换器类名
  // Kafka 消息通常是字节流或 JSON 格式，需要通过转换器转换为 Calcite 可识别的行格式
  // 例如："row.converter": "org.apache.calcite.adapter.kafka.JsonRowConverter" 表示使用 JSON 转换器
  String SCHEMA_ROW_CONVERTER = "row.converter";
  // 自定义消费者参数，指定是否使用自定义的 Kafka 消费者实现
  // 当设置为 true 时，表示使用自定义的消费者逻辑，而不是默认的消费者
  // 例如："consumer.cust": "true" 启用自定义消费者
  String SCHEMA_CUST_CONSUMER = "consumer.cust";
  // 消费者参数，指定 Kafka 消费者的额外配置参数
  // 这些参数会传递给底层的 Kafka 消费者，用于精细控制消费行为
  // 例如："consumer.params": {"auto.offset.reset": "earliest", "enable.auto.commit": "false"}
  // 可以包含任何标准的 Kafka 消费者配置参数
  String SCHEMA_CONSUMER_PARAMS = "consumer.params";
}
