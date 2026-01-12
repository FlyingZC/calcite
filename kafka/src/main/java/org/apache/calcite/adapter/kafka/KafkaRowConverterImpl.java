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
package org.apache.calcite.adapter.kafka; // 定义包名，这个类属于org.apache.calcite.adapter.kafka包，是Calcite框架中Kafka适配器的一部分

import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，用于表示关系型数据类型（即表结构中的字段类型）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系型数据类型的工厂类
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入RelDataTypeSystem接口，定义了类型系统的行为和规则
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SqlTypeFactoryImpl类，是RelDataTypeFactory的SQL类型实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义了所有标准的SQL类型名称（如INTEGER、VARCHAR等）

import org.apache.kafka.clients.consumer.ConsumerRecord; // 导入Kafka的ConsumerRecord类，表示从Kafka消费者接收到的消息记录

/**
 * Default implementation of {@link KafkaRowConverter}, both key and value are byte[].
 */ // 类级JavaDoc注释：这是KafkaRowConverter接口的默认实现类，用于处理Kafka消息的键和值都是字节数组（byte[]类型）的情况
public class KafkaRowConverterImpl implements KafkaRowConverter<byte[], byte[]> { // 定义类KafkaRowConverterImpl，实现KafkaRowConverter接口，泛型参数<byte[], byte[]>表示Kafka消息的键和值都是字节数组类型
  /**
   * Generates the row schema for a given Kafka topic.
   *
   * @param topicName Kafka topic name
   * @return row type
   */ // 方法的JavaDoc注释：为指定的Kafka主题生成行结构（即表结构），参数topicName是Kafka主题名称，返回值是RelDataType类型，表示生成的行结构
  @Override public RelDataType rowDataType(final String topicName) { // 重写rowDataType方法，使用@Override注解表示这是接口方法的实现，方法访问修饰符为public，返回RelDataType类型，参数topicName是final修饰的String类型，表示Kafka主题名称
    final RelDataTypeFactory typeFactory = // 声明一个final类型的RelDataTypeFactory变量typeFactory，用于创建SQL类型
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 创建SqlTypeFactoryImpl实例，传入RelDataTypeSystem.DEFAULT作为参数，表示使用默认的类型系统
    final RelDataTypeFactory.Builder fieldInfo = typeFactory.builder(); // 声明一个final类型的RelDataTypeFactory.Builder变量fieldInfo，通过typeFactory.builder()方法获取构建器对象，用于构建行结构
    fieldInfo.add("MSG_PARTITION", typeFactory.createSqlType(SqlTypeName.INTEGER)).nullable(false); // 向fieldInfo构建器中添加第一个字段，字段名为"MSG_PARTITION"（消息分区号），类型为INTEGER（整数），nullable(false)表示该字段不允许为空
    fieldInfo.add("MSG_TIMESTAMP", typeFactory.createSqlType(SqlTypeName.BIGINT)).nullable(false); // 向fieldInfo构建器中添加第二个字段，字段名为"MSG_TIMESTAMP"（消息时间戳），类型为BIGINT（长整型），nullable(false)表示该字段不允许为空
    fieldInfo.add("MSG_OFFSET", typeFactory.createSqlType(SqlTypeName.BIGINT)).nullable(false); // 向fieldInfo构建器中添加第三个字段，字段名为"MSG_OFFSET"（消息偏移量），类型为BIGINT（长整型），nullable(false)表示该字段不允许为空
    fieldInfo.add("MSG_KEY_BYTES", typeFactory.createSqlType(SqlTypeName.VARBINARY)).nullable(true); // 向fieldInfo构建器中添加第四个字段，字段名为"MSG_KEY_BYTES"（消息键的字节数组），类型为VARBINARY（可变长度二进制），nullable(true)表示该字段允许为空（因为Kafka消息可能没有键）
    fieldInfo.add("MSG_VALUE_BYTES", typeFactory.createSqlType(SqlTypeName.VARBINARY)) // 向fieldInfo构建器中添加第五个字段，字段名为"MSG_VALUE_BYTES"（消息值的字节数组），类型为VARBINARY（可变长度二进制）
        .nullable(false); // 设置该字段不允许为空，因为Kafka消息必须有值

    return fieldInfo.build(); // 调用fieldInfo.build()方法构建并返回RelDataType对象，该对象包含了完整的行结构定义（5个字段的名称、类型和可空性）
  }

  /**
   * Parses and reformats a Kafka message from the consumer, to align with the
   * row schema defined as {@link #rowDataType(String)}.
   *
   * @param message Raw Kafka message record
   * @return fields in the row
   */ // 方法的JavaDoc注释：解析并重新格式化从Kafka消费者获取的消息，使其与rowDataType方法定义的行结构保持一致，参数message是原始的Kafka消息记录，返回值是Object数组，表示行中的字段值
  @Override public Object[] toRow(final ConsumerRecord<byte[], byte[]> message) { // 重写toRow方法，使用@Override注解表示这是接口方法的实现，方法访问修饰符为public，返回Object数组，参数message是final修饰的ConsumerRecord<byte[], byte[]>类型，表示从Kafka消费者接收到的消息记录
    Object[] fields = new Object[5]; // 创建一个长度为5的Object数组fields，用于存储转换后的行数据，对应rowDataType方法定义的5个字段
    fields[0] = message.partition(); // 将数组的第一个元素（索引0）设置为Kafka消息的分区号，对应MSG_PARTITION字段
    fields[1] = message.timestamp(); // 将数组的第二个元素（索引1）设置为Kafka消息的时间戳，对应MSG_TIMESTAMP字段
    fields[2] = message.offset(); // 将数组的第三个元素（索引2）设置为Kafka消息的偏移量，对应MSG_OFFSET字段
    fields[3] = message.key(); // 将数组的第四个元素（索引3）设置为Kafka消息的键（字节数组），对应MSG_KEY_BYTES字段
    fields[4] = message.value(); // 将数组的第五个元素（索引4）设置为Kafka消息的值（字节数组），对应MSG_VALUE_BYTES字段

    return fields; // 返回包含5个字段值的Object数组，该数组与rowDataType方法定义的行结构完全对应
  }
} // 类定义结束，KafkaRowConverterImpl类实现了KafkaRowConverter接口，提供了将Kafka消息转换为Calcite可处理的行数据的功能
