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
package org.apache.calcite.adapter.kafka; // 定义包名，表示这个类位于org.apache.calcite.adapter.kafka包下，是Kafka适配器的一部分

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型（即表的行类型结构）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory类，用于创建关系数据类型的工厂类
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入RelDataTypeSystem类，用于定义关系数据类型系统（如类型兼容性规则）
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SqlTypeFactoryImpl类，是RelDataTypeFactory的SQL标准实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义了SQL标准的数据类型名称（如VARCHAR、INTEGER等）

import org.apache.kafka.clients.consumer.ConsumerRecord; // 导入ConsumerRecord类，表示从Kafka消费者接收到的消息记录

/**
 * Implementation of {@link KafkaRowConverter} for testing. Both key and value
 * are saved as {@code byte[]}.
 */
// 类注释：这是一个用于测试的KafkaRowConverter实现类，实现了KafkaRowConverter接口
// 泛型参数<String, String>表示Kafka消息的键和值都是String类型
// 该类的主要作用是将Kafka消息记录转换为Calcite可以处理的行数据格式
class KafkaRowConverterTest implements KafkaRowConverter<String, String> { // 定义测试用的Kafka行转换器类，实现KafkaRowConverter接口，键和值类型都是String
  /**
   * Generates a row schema for a given Kafka topic.
   *
   * @param topicName Kafka topic name
   * @return row type
   */
  // 方法注释：为指定的Kafka主题生成行数据类型（即行schema），定义了从Kafka消息转换后的行结构
  // 该方法决定了每条Kafka消息转换后的行包含哪些字段以及每个字段的数据类型
  // @param topicName Kafka主题名称，用于标识要生成schema的主题
  // @return RelDataType 返回关系数据类型对象，描述了行的结构（字段名、类型、是否可空等）
  @Override public RelDataType rowDataType(final String topicName) { // 重写接口方法，为指定主题生成行数据类型，final修饰表示topicName参数不可修改
    final RelDataTypeFactory typeFactory = // 创建SQL类型工厂实例，用于构建各种SQL数据类型，使用默认的关系数据类型系统
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 实例化SqlTypeFactoryImpl，传入默认的RelDataTypeSystem
    final RelDataTypeFactory.Builder fieldInfo = typeFactory.builder(); // 获取类型构建器，用于逐步添加字段定义，构建最终的行类型
    fieldInfo.add("TOPIC_NAME", typeFactory.createSqlType(SqlTypeName.VARCHAR)).nullable(false); // 添加第一个字段：TOPIC_NAME（主题名称），类型为VARCHAR（可变长度字符串），设置为不可空
    fieldInfo.add("PARTITION_ID", typeFactory.createSqlType(SqlTypeName.INTEGER)).nullable(false); // 添加第二个字段：PARTITION_ID（分区ID），类型为INTEGER（整数），设置为不可空
    fieldInfo.add("TIMESTAMP_TYPE", typeFactory.createSqlType(SqlTypeName.VARCHAR)).nullable(true); // 添加第三个字段：TIMESTAMP_TYPE（时间戳类型），类型为VARCHAR，设置为可空（因为某些消息可能没有时间戳类型）

    return fieldInfo.build(); // 构建并返回完整的行数据类型，包含了上述三个字段的定义
  }

  /**
   * Parses and reformats Kafka messages from consumer, to fit with row schema
   * defined as {@link #rowDataType(String)}.
   *
   * @param message Raw Kafka message record
   * @return fields in the row
   */
  // 方法注释：解析并重新格式化从Kafka消费者获取的消息，使其符合rowDataType方法定义的行schema
  // 该方法将ConsumerRecord对象转换为Object数组，每个数组元素对应行中的一个字段
  // @param message 原始的Kafka消息记录，包含topic、partition、key、value等信息
  // @return Object[] 返回对象数组，包含转换后的行字段值，数组顺序必须与rowDataType定义的字段顺序一致
  @Override public Object[] toRow(final ConsumerRecord<String, String> message) { // 重写接口方法，将Kafka消息记录转换为行数据，final修饰表示message参数不可修改
    Object[] fields = new Object[3]; // 创建长度为3的对象数组，用于存储转换后的行字段值，对应rowDataType中定义的3个字段
    fields[0] = message.topic(); // 将Kafka消息的主题名称赋值给数组的第一个元素（对应TOPIC_NAME字段）
    fields[1] = message.partition(); // 将Kafka消息的分区ID赋值给数组的第二个元素（对应PARTITION_ID字段）
    fields[2] = message.timestampType().name; // 将Kafka消息的时间戳类型名称赋值给数组的第三个元素（对应TIMESTAMP_TYPE字段）

    return fields; // 返回包含转换后字段值的对象数组
  }
}
