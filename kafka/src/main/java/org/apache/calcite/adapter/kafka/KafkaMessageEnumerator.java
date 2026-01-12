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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.kafka; // 定义包名，该类属于Calcite的Kafka适配器包

import org.apache.calcite.linq4j.Enumerator; // 导入Calcite的LINQ4J枚举器接口，用于实现数据遍历功能

import org.apache.kafka.clients.consumer.Consumer; // 导入Kafka消费者接口，用于从Kafka主题消费消息
import org.apache.kafka.clients.consumer.ConsumerConfig; // 导入Kafka消费者配置类，提供配置常量引用
import org.apache.kafka.clients.consumer.ConsumerRecord; // 导入Kafka消费者记录类，表示单个Kafka消息
import org.apache.kafka.clients.consumer.ConsumerRecords; // 导入Kafka消费者记录集合类，表示一批Kafka消息

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的类型

import java.time.Duration; // 导入Java时间持续时间类，用于指定轮询超时时间
import java.util.ArrayDeque; // 导入数组双端队列实现，用作消息缓冲区
import java.util.Deque; // 导入双端队列接口，定义缓冲区的数据结构
import java.util.concurrent.atomic.AtomicBoolean; // 导入原子布尔类，用于线程安全的取消标志

import static java.util.Objects.requireNonNull; // 导入对象工具类的静态方法，用于非空检查

/**
 * Enumerator to read data from {@link Consumer}, // 枚举器类，用于从Kafka消费者读取数据
 * and converted into SQL rows with {@link KafkaRowConverter}. // 并通过Kafka行转换器将数据转换为SQL行
 * // 该类实现了Calcite的Enumerator接口，使Kafka消息能够被Calcite查询引擎像处理关系型数据表一样处理
 *
 * @param <K> Type for Kafka message key, // 泛型参数K表示Kafka消息键的类型
 *           refer to {@link ConsumerConfig#KEY_DESERIALIZER_CLASS_CONFIG}; // 对应Kafka消费者配置中的键反序列化器配置
 * @param <V> Type for Kafka message value, // 泛型参数V表示Kafka消息值的类型
 *           refer to {@link ConsumerConfig#VALUE_DESERIALIZER_CLASS_CONFIG}; // 对应Kafka消费者配置中的值反序列化器配置
 */
public class KafkaMessageEnumerator<K, V> implements Enumerator<@Nullable Object[]> { // 定义Kafka消息枚举器类，实现Enumerator接口，返回可空的Object数组
  final Consumer consumer; // Kafka消费者实例，用于从Kafka主题拉取消息，final表示初始化后不可变
  final KafkaRowConverter<K, V> rowConverter; // Kafka行转换器实例，负责将Kafka消息转换为SQL行格式，final表示初始化后不可变
  private final AtomicBoolean cancelFlag; // 原子布尔标志，用于线程安全地控制枚举操作的取消，final表示初始化后不可变

  // runtime // 运行时状态部分
  private final Deque<ConsumerRecord<K, V>> bufferedRecords = new ArrayDeque<>(); // 消息缓冲队列，用于存储从Kafka拉取但尚未处理的记录，使用ArrayDeque实现双端队列
  private @Nullable ConsumerRecord<K, V> curRecord; // 当前正在处理的Kafka消息记录，使用@Nullable注解表示可能为null

  KafkaMessageEnumerator(final Consumer consumer, // 构造方法，接收Kafka消费者实例作为参数
      final KafkaRowConverter<K, V> rowConverter, // 接收Kafka行转换器实例作为参数
      final AtomicBoolean cancelFlag) { // 接收原子布尔取消标志作为参数
    this.consumer = consumer; // 将传入的Kafka消费者实例赋值给成员变量
    this.rowConverter = rowConverter; // 将传入的Kafka行转换器实例赋值给成员变量
    this.cancelFlag = cancelFlag; // 将传入的原子布尔取消标志赋值给成员变量
  } // 构造方法结束，初始化枚举器的核心组件

  /**
   * It returns an Array of Object, with each element represents a field of row. // 返回一个对象数组，每个元素代表行的一个字段
   */ // Javadoc注释，说明current()方法的作用
  @Override public Object[] current() { // 重写Enumerator接口的current()方法，返回当前行的数据
    return rowConverter.toRow(requireNonNull(curRecord, "curRecord")); // 使用行转换器将当前Kafka记录转换为SQL行，requireNonNull确保curRecord不为null
  } // current()方法结束

  @Override public boolean moveNext() { // 重写Enumerator接口的moveNext()方法，移动到下一条记录
    if (cancelFlag.get()) { // 检查取消标志是否为true
      return false; // 如果已取消，返回false表示没有更多记录
    } // 取消检查结束

    while (bufferedRecords.isEmpty()) { // 当缓冲队列为空时循环
      pullRecords(); // 从Kafka拉取一批消息到缓冲队列
    } // 缓冲队列填充循环结束

    curRecord = bufferedRecords.removeFirst(); // 从缓冲队列头部取出一条记录作为当前记录
    return true; // 返回true表示成功移动到下一条记录
  } // moveNext()方法结束

  private void pullRecords() { // 私有方法，从Kafka拉取一批消息
    ConsumerRecords<K, V> records = consumer.poll(Duration.ofMillis(100)); // 使用消费者轮询消息，超时时间为100毫秒
    for (ConsumerRecord record : records) { // 遍历拉取到的所有记录
      bufferedRecords.add(record); // 将每条记录添加到缓冲队列尾部
    } // 记录遍历结束
  } // pullRecords()方法结束

  @Override public void reset() { // 重写Enumerator接口的reset()方法，重置枚举器状态
    this.bufferedRecords.clear(); // 清空缓冲队列中的所有记录
    pullRecords(); // 重新从Kafka拉取消息填充缓冲队列
  } // reset()方法结束

  @Override public void close() { // 重写Enumerator接口的close()方法，关闭枚举器并释放资源
    consumer.close(); // 关闭Kafka消费者，释放相关资源
  } // close()方法结束
} // KafkaMessageEnumerator类定义结束
