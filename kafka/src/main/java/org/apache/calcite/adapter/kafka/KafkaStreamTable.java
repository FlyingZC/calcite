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
package org.apache.calcite.adapter.kafka; // 定义包名，该类位于calcite的kafka适配器包中，用于将Kafka主题作为Calcite表使用

import org.apache.calcite.DataContext; // 导入DataContext类，用于在查询执行过程中传递上下文信息
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置类，用于获取连接级别的配置信息
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，用于实现LINQ风格的数据枚举
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可以被枚举的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合
import org.apache.calcite.rel.RelCollations; // 导入关系排序工具类，用于创建排序规则
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表或表达式的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示可以被扫描的表
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示数据库模式或命名空间
import org.apache.calcite.schema.Statistic; // 导入统计信息接口，提供表的统计信息
import org.apache.calcite.schema.Statistics; // 导入统计信息工具类，用于创建统计信息对象
import org.apache.calcite.schema.StreamableTable; // 导入可流式处理表接口，表示可以作为流处理的表
import org.apache.calcite.schema.Table; // 导入表接口，表示数据库表
import org.apache.calcite.sql.SqlCall; // 导入SQL调用节点类，表示SQL中的函数调用或操作符调用
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，表示SQL语法树中的节点

import org.apache.kafka.clients.consumer.Consumer; // 导入Kafka消费者接口，用于消费Kafka消息
import org.apache.kafka.clients.consumer.ConsumerConfig; // 导入Kafka消费者配置类，用于设置消费者参数
import org.apache.kafka.clients.consumer.KafkaConsumer; // 导入Kafka消费者实现类，用于实际消费消息

import com.google.common.collect.ImmutableList; // 导入不可变列表类，用于创建不可变的列表对象

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.util.Collections; // 导入集合工具类，用于创建不可修改的集合
import java.util.Properties; // 导入属性类，用于存储配置键值对
import java.util.concurrent.atomic.AtomicBoolean; // 导入原子布尔类，用于线程安全的布尔标志

/**
 * A table that maps to an Apache Kafka topic.
 * 表示映射到Apache Kafka主题的表，该类作为Calcite与Kafka之间的桥梁，将Kafka主题抽象为Calcite中的表
 *
 * <p>Currently only {@link KafkaStreamTable} is
 * implemented as a STREAM table.
 * 目前只有KafkaStreamTable被实现为流表（STREAM table），这意味着它支持流式数据处理
 * 流表的特点是数据源源不断地到达，查询时只处理新到达的数据，而不是历史数据
 * 这个类实现了ScannableTable和StreamableTable接口，使其既可以被扫描也可以作为流处理
 */
public class KafkaStreamTable implements ScannableTable, StreamableTable { // 定义KafkaStreamTable类，实现ScannableTable和StreamableTable接口
  final KafkaTableOptions tableOptions; // 定义最终的Kafka表选项成员变量，存储该表的所有配置信息，包括Kafka服务器地址、主题名称、消费者参数等

  KafkaStreamTable(final KafkaTableOptions tableOptions) { // 构造方法，接收KafkaTableOptions参数并初始化表选项
    this.tableOptions = tableOptions; // 将传入的tableOptions赋值给成员变量，保存表的配置信息
  }

  @Override public Enumerable<@Nullable Object[]> scan(final DataContext root) { // 重写scan方法，扫描表数据并返回可枚举对象，root参数提供查询执行的上下文信息
    final AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root); // 从DataContext中获取取消标志，用于在查询被取消时中断操作
    return new AbstractEnumerable<@Nullable Object[]>() { // 返回一个抽象可枚举对象，该对象可以产生枚举器来遍历Kafka消息
      @Override public Enumerator<@Nullable Object[]> enumerator() { // 重写enumerator方法，创建并返回一个枚举器对象，用于实际遍历数据
        if (tableOptions.getConsumer() != null) { // 检查tableOptions中是否已经配置了消费者实例
          return new KafkaMessageEnumerator(tableOptions.getConsumer(), // 如果已有消费者，直接使用该消费者创建枚举器，避免重复创建
              tableOptions.getRowConverter(), cancelFlag); // 同时传入行转换器和取消标志，行转换器用于将Kafka消息转换为Calcite行格式
        } // 如果已有消费者，直接返回枚举器，跳过消费者创建逻辑

        Properties consumerConfig = new Properties(); // 如果没有预配置的消费者，创建新的Properties对象来存储消费者配置
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, // 设置Kafka引导服务器配置，指定Kafka集群的地址
            tableOptions.getBootstrapServers()); // 从tableOptions中获取Bootstrap服务器地址
        // by default it's <byte[], byte[]>
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, // 设置值反序列化器配置，默认使用字节数组反序列化器
            "org.apache.kafka.common.serialization.ByteArrayDeserializer"); // 指定ByteArrayDeserializer类作为值的反序列化器
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, // 设置键反序列化器配置，默认使用字节数组反序列化器
            "org.apache.kafka.common.serialization.ByteArrayDeserializer"); // 指定ByteArrayDeserializer类作为键的反序列化器

        if (tableOptions.getConsumerParams() != null) { // 检查tableOptions中是否有额外的消费者参数
          consumerConfig.putAll(tableOptions.getConsumerParams()); // 如果有额外参数，将它们全部添加到consumerConfig中，覆盖或补充默认配置
        } // 完成消费者配置的设置
        Consumer consumer = new KafkaConsumer<>(consumerConfig); // 使用配置好的consumerConfig创建KafkaConsumer实例
        consumer.subscribe(Collections.singletonList(tableOptions.getTopicName())); // 订阅指定的Kafka主题，将主题名称包装在单元素列表中

        return new KafkaMessageEnumerator(consumer, tableOptions.getRowConverter(), cancelFlag); // 创建并返回KafkaMessageEnumerator，传入消费者、行转换器和取消标志
      } // enumerator方法结束，返回消息枚举器
    }; // 匿名AbstractEnumerable类定义结束
  } // scan方法结束，返回可枚举对象

  @Override public RelDataType getRowType(final RelDataTypeFactory typeFactory) { // 重写getRowType方法，返回表的行类型信息，typeFactory用于创建数据类型
    return tableOptions.getRowConverter().rowDataType(tableOptions.getTopicName()); // 使用行转换器获取行数据类型，传入主题名称作为参数
  } // getRowType方法结束，返回关系数据类型

  @Override public Statistic getStatistic() { // 重写getStatistic方法，返回表的统计信息，用于查询优化器进行成本估算
    return Statistics.of(100d, ImmutableList.of(), // 创建统计信息对象，设置行数为100（固定值），空列表表示没有唯一键
        RelCollations.createSingleton(0)); // 创建单列排序规则，表示第一列有排序
  } // getStatistic方法结束，返回统计信息对象

  @Override public boolean isRolledUp(final String column) { // 重写isRolledUp方法，判断指定列是否是聚合列（rollup）
    return false; // 返回false，表示Kafka流表不支持rollup聚合列
  } // isRolledUp方法结束，返回false

  @Override public boolean rolledUpColumnValidInsideAgg(final String column, final SqlCall call, // 重写rolledUpColumnValidInsideAgg方法，判断rollup列在聚合函数中是否有效
      final @Nullable SqlNode parent, // parent参数表示父节点，可能为null
      final @Nullable CalciteConnectionConfig config) { // config参数表示连接配置，可能为null
    return false; // 返回false，表示不支持rollup列在聚合中使用
  } // rolledUpColumnValidInsideAgg方法结束，返回false

  @Override public Table stream() { // 重写stream方法，返回流表实例
    return this; // 返回当前对象本身，因为KafkaStreamTable本身就是流表
  } // stream方法结束，返回this

  @Override public Schema.TableType getJdbcTableType() { // 重写getJdbcTableType方法，返回JDBC表类型
    return Schema.TableType.STREAM; // 返回STREAM类型，表示这是一个流表而不是普通表
  } // getJdbcTableType方法结束，返回STREAM表类型
} // KafkaStreamTable类定义结束
