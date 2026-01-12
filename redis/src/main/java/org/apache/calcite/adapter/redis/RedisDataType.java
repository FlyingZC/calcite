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
package org.apache.calcite.adapter.redis; // 定义包路径，该类属于 Calcite Redis 适配器包，用于 Redis 数据源的集成

/**
 * All available data type for Redis. // Redis 所有可用数据类型的枚举定义，用于表示 Redis 中支持的各种数据结构类型
 */
public enum RedisDataType { // 定义一个枚举类 RedisDataType，用于枚举 Redis 支持的所有数据类型

  /**
   * Strings are the most basic kind of Redis value. Redis Strings are binary safe, // 字符串是 Redis 最基本的数据类型，Redis 字符串是二进制安全的
   * this means that a Redis string can contain any kind of data, for instance a JPEG image // 这意味着 Redis 字符串可以包含任何类型的数据，例如 JPEG 图像
   * or a serialized Ruby object. // 或者序列化的 Ruby 对象
   * A String value can be at max 512 Megabytes in length. // 字符串值的最大长度为 512 兆字节
   */
  STRING("string"), // 定义 STRING 枚举常量，对应 Redis 的 string 类型，构造参数为类型名称 "string"

  /**
   * Redis Hashes are maps between string fields and string values. // Redis 哈希是字符串字段和字符串值之间的映射
   */
  HASH("hash"), // 定义 HASH 枚举常量，对应 Redis 的 hash 类型，构造参数为类型名称 "hash"

  /**
   * Redis Lists are simply lists of strings, sorted by insertion order. // Redis 列表是简单的字符串列表，按插入顺序排序
   */
  LIST("list"), // 定义 LIST 枚举常量，对应 Redis 的 list 类型，构造参数为类型名称 "list"

  /**
   * Redis Sets are an unordered collection of Strings. // Redis 集合是字符串的无序集合
   */
  SET("set"), // 定义 SET 枚举常量，对应 Redis 的 set 类型，构造参数为类型名称 "set"

  /**
   * Redis Sorted Sets are, similarly to Redis Sets, non repeating collections of Strings. // Redis 有序集合类似于 Redis 集合，是不重复的字符串集合
   * The difference is that every member of a Sorted Set is associated with score, // 不同之处在于有序集合的每个成员都关联一个分数
   * that is used in order to take the sorted set ordered, // 该分数用于对有序集合进行排序
   * from the smallest to the greatest score. // 从最小分数到最大分数
   * While members are unique, scores may be repeated. // 虽然成员是唯一的，但分数可以重复
   */
  SORTED_SET("zset"), // 定义 SORTED_SET 枚举常量，对应 Redis 的 zset（有序集合）类型，构造参数为类型名称 "zset"

  /**
   * HyperLogLog is a probabilistic data structure used in order to count unique things. // HyperLogLog 是一种概率数据结构，用于统计唯一事物（基数估算）
   */
  HYPER_LOG_LOG("pfadd"), // 定义 HYPER_LOG_LOG 枚举常量，对应 Redis 的 HyperLogLog 类型，构造参数为命令名称 "pfadd"

  /**
   * Redis implementation of publish and subscribe paradigm. // Redis 发布/订阅模式的实现
   * Published messages are characterized into channels, // 发布的消息被分类到频道中
   * without knowledge of what (if any) subscribers there may be. // 而不知道可能有哪些订阅者（如果有的话）
   * Subscribers express interest in one or more channels, and only receive messages // 订阅者对一个或多个频道表达兴趣，并且只接收他们感兴趣的消息
   * that are of interest, without knowledge of what (if any) publishers there are. // 而不知道可能有哪些发布者（如果有的话）
   */
  PUBSUB("publish"); // 定义 PUBSUB 枚举常量，对应 Redis 的发布/订阅功能，构造参数为命令名称 "publish"


  private final String typeName; // 定义私有最终成员变量 typeName，用于存储 Redis 数据类型的名称，final 表示该变量不可变

  RedisDataType(String typeName) { // 枚举类的构造方法，接收一个字符串参数 typeName
    this.typeName = typeName; // 将传入的类型名称赋值给成员变量 typeName
  }

  public static RedisDataType fromTypeName(String typeName) { // 定义静态方法 fromTypeName，根据类型名称获取对应的 RedisDataType 枚举值
    for (RedisDataType type : RedisDataType.values()) { // 遍历 RedisDataType 枚举类的所有枚举值
      if (type.getTypeName().equals(typeName)) { // 比较当前枚举值的类型名称是否与传入的 typeName 相等
        return type; // 如果相等，返回当前枚举值
      }
    }
    return null; // 如果遍历完所有枚举值都没有找到匹配的类型，返回 null
  }

  public String getTypeName() { // 定义公共方法 getTypeName，获取当前枚举值的类型名称
    return this.typeName; // 返回成员变量 typeName 的值
  }
}
