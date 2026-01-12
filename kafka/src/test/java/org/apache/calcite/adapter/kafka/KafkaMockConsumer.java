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
package org.apache.calcite.adapter.kafka; // 声明该类属于Calcite框架的Kafka适配器包，用于处理Kafka数据源的适配逻辑

import org.apache.kafka.clients.consumer.ConsumerRecord; // 导入Kafka消费者记录类，表示消费到的单条消息记录，包含topic、partition、offset、key和value等信息
import org.apache.kafka.clients.consumer.MockConsumer; // 导入Kafka的模拟消费者类，用于测试环境中模拟真实的Kafka消费者行为，不需要连接真实的Kafka集群
import org.apache.kafka.clients.consumer.OffsetResetStrategy; // 导入Kafka偏移量重置策略枚举，定义了当消费者没有有效的偏移量时的重置策略，如EARLIEST(从最早开始)或LATEST(从最新开始)
import org.apache.kafka.common.TopicPartition; // 导入Kafka主题分区类，表示Kafka中的一个主题及其分区编号的组合，是消费者订阅和消费的基本单位

import java.nio.charset.StandardCharsets; // 导入Java标准字符集类，用于指定字符编码方式，这里使用UTF-8编码将字符串转换为字节数组
import java.util.Arrays; // 导入Java数组工具类，提供数组的操作方法，这里用于将单个元素转换为列表
import java.util.HashMap; // 导入Java哈希映射类，用于存储键值对，这里用于存储TopicPartition到偏移量的映射关系

/**
 * A mock consumer to test Kafka adapter. // 这是一个用于测试Calcite Kafka适配器的模拟消费者类
 * 该类继承自Kafka的MockConsumer，提供了预配置的测试数据，用于在单元测试中模拟Kafka消息消费行为
 * 主要用于测试Calcite框架如何从Kafka主题中读取数据并进行SQL查询处理
 * 该类会自动创建一个名为"testtopic"的测试主题，并预填充10条测试消息记录
 */
public class KafkaMockConsumer extends MockConsumer { // 定义KafkaMockConsumer类，继承自MockConsumer，用于模拟Kafka消费者行为
  public KafkaMockConsumer(final OffsetResetStrategy offsetResetStrategy) { // 构造方法，接收偏移量重置策略参数（虽然参数传入但实际未使用，固定使用EARLIEST策略）
    super(OffsetResetStrategy.EARLIEST); // 调用父类MockConsumer的构造方法，传入EARLIEST策略表示当没有有效偏移量时从最早的消息开始消费

    assign(Arrays.asList(new TopicPartition("testtopic", 0))); // 为消费者分配主题分区，创建一个名为"testtopic"的测试主题的0号分区，并将其转换为列表后分配给消费者

    HashMap<TopicPartition, Long> beginningOffsets = new HashMap<>(); // 创建一个哈希映射对象，用于存储每个主题分区的起始偏移量，键为TopicPartition对象，值为偏移量Long类型
    beginningOffsets.put(new TopicPartition("testtopic", 0), 0L); // 将"testtopic"主题的0号分区的起始偏移量设置为0（即从第一条消息开始），存入哈希映射中
    updateBeginningOffsets(beginningOffsets); // 调用父类方法更新消费者的起始偏移量信息，使消费者知道每个分区的起始位置

    for (int idx = 0; idx < 10; ++idx) { // 使用循环创建10条测试消息记录，idx从0到9递增，每条记录都有唯一的序号
      addRecord( // 调用父类方法向模拟消费者添加一条消费记录，该记录会被消费者后续poll()方法读取到
          new ConsumerRecord<>("testtopic", // 创建一个新的ConsumerRecord对象，指定主题名称为"testtopic"
              0, idx, // 指定分区号为0，偏移量为idx（即0到9），每条记录的偏移量唯一递增
              ("mykey" + idx).getBytes(StandardCharsets.UTF_8), // 将消息键设置为"mykey"加上序号（如"mykey0"、"mykey1"等），并使用UTF-8编码转换为字节数组
              ("myvalue" + idx).getBytes(StandardCharsets.UTF_8))); // 将消息值设置为"myvalue"加上序号（如"myvalue0"、"myvalue1"等），并使用UTF-8编码转换为字节数组
    } // 循环结束，共添加了10条测试消息记录，每条记录都有唯一的key和value
  } // 构造方法结束，此时模拟消费者已经配置好并预填充了测试数据，可以用于测试
} // 类定义结束
