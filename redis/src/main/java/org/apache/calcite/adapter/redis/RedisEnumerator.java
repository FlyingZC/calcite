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
package org.apache.calcite.adapter.redis; // 定义包名，该类属于Calcite的Redis适配器模块

import org.apache.calcite.linq4j.Enumerator; // 导入Calcite的LINQ4J枚举器接口，用于迭代访问数据
import org.apache.calcite.linq4j.Linq4j; // 导入Calcite的LINQ4J工具类，提供创建枚举器的方法

import org.apache.commons.lang3.StringUtils; // 导入Apache Commons Lang的字符串工具类，用于字符串判断

import java.util.LinkedHashMap; // 导入LinkedHashMap，用于保持插入顺序的键值对集合
import java.util.List; // 导入List接口，用于处理列表数据
import java.util.Map; // 导入Map接口，用于处理键值对映射

import redis.clients.jedis.Jedis; // 导入Jedis客户端，用于与Redis服务器进行通信

import static java.util.Objects.requireNonNull; // 导入Objects工具类的requireNonNull方法，用于空值检查

/**
 * Implementation of {@link RedisEnumerator}. // RedisEnumerator类的实现说明，该类实现了Calcite的枚举器接口，用于从Redis数据库中读取数据并提供迭代访问功能
 * // 该类是Redis适配器的核心组件之一，负责将Redis中的数据转换为Calcite可以处理的Object[]数组格式
 * // 通过实现Enumerator接口，该类提供了标准的数据迭代访问方式，支持current()、moveNext()、reset()和close()等方法
 * // 在构造函数中，该类会连接到Redis服务器，读取指定表的数据，并将其封装为枚举器供上层查询引擎使用
 */
class RedisEnumerator implements Enumerator<Object[]> { // 定义RedisEnumerator类，实现Enumerator<Object[]>接口，泛型Object[]表示每行数据以对象数组形式存储
  private final Enumerator<Object[]> enumerator; // 成员变量：声明一个最终的枚举器对象，用于实际的数据迭代操作，该枚举器包装了从Redis读取的数据列表

  RedisEnumerator(RedisConfig redisConfig, RedisSchema schema, String tableName) { // 构造方法：接收Redis配置对象、Redis模式对象和表名三个参数，用于初始化Redis枚举器
    RedisTableFieldInfo tableFieldInfo = schema.getTableFieldInfo(tableName); // 从Redis模式中获取指定表的字段信息，包含表的字段定义和数据格式等元数据

    RedisJedisManager redisManager = // 创建Redis Jedis管理器，负责管理Redis连接
        new RedisJedisManager(redisConfig.getHost(), redisConfig.getPort(), // 使用Redis配置中的主机地址、端口号
            redisConfig.getDatabase(), redisConfig.getPassword()); // 以及数据库编号和密码来初始化Jedis管理器

    try (Jedis jedis = redisManager.getResource()) { // 使用try-with-resources语法从Jedis管理器获取Jedis连接资源，确保使用完毕后自动关闭连接
      if (StringUtils.isNotEmpty(redisConfig.getPassword())) { // 检查Redis配置中的密码是否不为空
        jedis.auth(redisConfig.getPassword()); // 如果密码不为空，则使用Jedis客户端进行密码认证，确保连接的安全性
      }
      RedisDataProcess dataProcess = new RedisDataProcess(jedis, tableFieldInfo); // 创建Redis数据处理器，传入Jedis连接和表的字段信息，用于从Redis中读取和处理数据
      List<Object[]> objs = dataProcess.read(); // 调用数据处理器的read方法，从Redis中读取数据并转换为Object[]列表形式
      enumerator = Linq4j.enumerator(objs); // 使用Linq4j工具类将对象列表转换为枚举器，赋值给成员变量enumerator，供后续迭代使用
    } // try-with-resources自动关闭Jedis连接
  }

  static Map<String, Object> deduceRowType(RedisTableFieldInfo tableFieldInfo) { // 静态方法：根据表的字段信息推导出行类型（即字段名称和类型映射），返回Map<String, Object>类型的映射关系
    final Map<String, Object> fieldBuilder = new LinkedHashMap<>(); // 创建LinkedHashMap用于构建字段映射，使用LinkedHashMap保持字段的插入顺序
    String dataFormat = tableFieldInfo.getDataFormat(); // 从表字段信息中获取数据格式，数据格式决定了Redis数据的存储方式（如RAW、HASH等）
    RedisDataFormat redisDataFormat = // 根据数据格式类型名称获取对应的Redis数据格式枚举值
        requireNonNull(RedisDataFormat.fromTypeName(dataFormat)); // 使用requireNonNull确保数据格式不为null，如果为null则抛出NullPointerException
    if (redisDataFormat == RedisDataFormat.RAW) { // 判断数据格式是否为RAW格式，RAW格式表示Redis中存储的是原始键值对
      fieldBuilder.put("key", "key"); // 如果是RAW格式，则只添加一个名为"key"的字段，类型为"key"，表示只返回Redis的键
    } else { // 如果不是RAW格式，则处理其他数据格式（如HASH格式等）
      for (LinkedHashMap<String, Object> field : tableFieldInfo.getFields()) { // 遍历表字段信息中的所有字段定义，每个字段是一个LinkedHashMap，包含字段名称和类型等信息
        fieldBuilder.put(field.get("name").toString(), field.get("type").toString()); // 将字段的名称和类型添加到fieldBuilder映射中，键为字段名，值为字段类型
      }
    }
    return fieldBuilder; // 返回构建好的字段映射Map，包含了该表所有字段的名称和类型信息
  }

  @Override public Object[] current() { // 重写Enumerator接口的current方法，返回当前迭代位置的元素
    return enumerator.current(); // 委托给内部枚举器的current方法，返回当前行的Object[]数组数据
  }

  @Override public boolean moveNext() { // 重写Enumerator接口的moveNext方法，将迭代器移动到下一个位置
    return enumerator.moveNext(); // 委托给内部枚举器的moveNext方法，如果还有下一个元素返回true，否则返回false
  }

  @Override public void reset() { // 重写Enumerator接口的reset方法，将迭代器重置到初始位置
    enumerator.reset(); // 委托给内部枚举器的reset方法，将迭代器重置，使其可以重新从头开始迭代
  }

  @Override public void close() { // 重写Enumerator接口的close方法，关闭枚举器并释放相关资源
    enumerator.close(); // 委托给内部枚举器的close方法，关闭枚举器，释放内存和连接等资源
  }
}
