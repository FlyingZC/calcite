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
package org.apache.calcite.adapter.redis;

import org.apache.commons.lang3.StringUtils; // Apache Commons Lang3工具类，提供字符串操作方法（如判空等）

import com.fasterxml.jackson.core.JsonParser; // Jackson JSON解析器核心类，提供JSON解析配置选项
import com.fasterxml.jackson.databind.JsonNode; // Jackson JSON树节点类，表示JSON数据结构
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson对象映射器，用于JSON与Java对象的相互转换

import java.util.ArrayList; // Java集合框架的动态数组实现
import java.util.LinkedHashMap; // Java集合框架的有序哈希映射实现，保持插入顺序
import java.util.List; // Java集合框架的列表接口

import redis.clients.jedis.Jedis; // Jedis Redis客户端，用于执行Redis命令

import static java.util.Objects.requireNonNull; // Java Objects工具类的静态方法，用于非空检查

/**
 * The class with RedisDataProcess.
 * Redis数据处理类，负责从Redis中读取数据并将其转换为Calcite可以处理的格式
 * 支持多种Redis数据类型（STRING, LIST, SET, SORTED_SET, HASH）和多种数据格式（RAW, JSON, CSV）
 * 该类是Redis适配器与Calcite查询引擎之间的桥梁，负责底层数据的获取和转换
 */
public class RedisDataProcess {
  final String tableName; // Redis表名（即Redis的key名称），用于标识要操作的数据源
  final String dataFormat; // 数据格式类型（RAW/JSON/CSV），指定Redis中存储的数据格式
  final String keyDelimiter; // CSV格式下的字段分隔符，用于解析CSV格式的数据
  final RedisDataType dataType; // Redis数据类型枚举（STRING/LIST/SET/SORTED_SET/HASH），标识Redis中存储的数据结构类型
  final RedisDataFormat redisDataFormat; // Redis数据格式枚举（RAW/JSON/CSV），标识数据的存储格式
  final List<LinkedHashMap<String, Object>> fields; // 字段定义列表，每个LinkedHashMap包含字段映射关系，如字段名到JSON路径的映射
  private final Jedis jedis; // Redis客户端连接对象，用于执行Redis命令
  private final ObjectMapper objectMapper = new ObjectMapper(); // JSON解析器对象，用于将JSON字符串解析为JsonNode

  public RedisDataProcess(Jedis jedis, RedisTableFieldInfo tableFieldInfo) { // 构造方法，初始化Redis数据处理对象
    this.jedis = jedis; // 保存Redis客户端连接
    String type = jedis.type(tableFieldInfo.getTableName()); // 查询Redis中指定key的数据类型（STRING/LIST/SET/HASH等）
    fields = tableFieldInfo.getFields(); // 获取字段定义列表，包含字段映射关系
    dataFormat = tableFieldInfo.getDataFormat(); // 获取数据格式（RAW/JSON/CSV）
    tableName = tableFieldInfo.getTableName(); // 获取表名（Redis key名称）
    keyDelimiter = tableFieldInfo.getKeyDelimiter(); // 获取CSV分隔符
    dataType = requireNonNull(RedisDataType.fromTypeName(type)); // 将Redis类型字符串转换为枚举类型，若类型未知则抛出异常
    redisDataFormat =
        requireNonNull(
            RedisDataFormat.fromTypeName(tableFieldInfo.getDataFormat())); // 将数据格式字符串转换为枚举类型，若格式未知则抛出异常
    objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true) // 配置JSON解析器：允许字段名不加引号
        .configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true) // 配置JSON解析器：允许使用单引号
        .configure(JsonParser.Feature.ALLOW_COMMENTS, true); // 配置JSON解析器：允许JSON中包含注释
  }

  public List<Object[]> read() { // 读取Redis数据并转换为Object数组列表，根据不同的Redis数据类型调用相应的Redis命令
    switch (dataType) { // 根据Redis数据类型选择不同的读取策略
    case STRING: // 如果是STRING类型
      return parse(jedis.keys(tableName)); // 使用KEYS命令获取所有匹配的key，然后解析每个key对应的value
    case LIST: // 如果是LIST类型
      return parse(jedis.lrange(tableName, 0, -1)); // 使用LRANGE命令获取列表所有元素（从索引0到-1表示全部）
    case SET: // 如果是SET类型
      return parse(jedis.smembers(tableName)); // 使用SMEMBERS命令获取集合所有成员
    case SORTED_SET: // 如果是有序集合类型
      return parse(jedis.zrange(tableName, 0, -1)); // 使用ZRANGE命令获取有序集合所有元素（按分数升序）
    case HASH: // 如果是HASH类型
      return parse(jedis.hvals(tableName)); // 使用HVALS命令获取哈希表所有值
    default: // 未知数据类型
      return new ArrayList<>(); // 返回空列表
    }
  }

  private Object[] parseJson(String value) { // 解析JSON格式字符串，提取指定字段的值并返回Object数组
    assert StringUtils.isNotEmpty(value); // 断言传入的value不为空，如果为空则抛出AssertionError
    Object[] arr = new Object[fields.size()]; // 创建对象数组，大小与字段数量相等
    try { // 开始JSON解析过程
      JsonNode jsonNode = objectMapper.readTree(value); // 将JSON字符串解析为JsonNode树结构
      Object obj; // 临时变量，用于存储字段映射配置
      for (int i = 0; i < arr.length; i++) { // 遍历所有字段定义
        obj = fields.get(i).get("mapping"); // 获取当前字段的映射配置（JSON路径）
        if (obj == null) { // 如果没有配置映射
          arr[i] = ""; // 设置为空字符串
        } else { // 如果配置了映射
          arr[i] = jsonNode.findValue(fields.get(i).get("mapping").toString()); // 从JSON树中查找指定路径的值
        }
      }
    } catch (Exception e) { // 捕获JSON解析过程中的异常
      throw new RuntimeException("Parsing json failed: ", e); // 抛出运行时异常，包含原始异常信息
    }
    return arr; // 返回解析后的对象数组
  }

  private Object[] parseCsv(String value) { // 解析CSV格式字符串，使用分隔符分割并返回Object数组
    assert StringUtils.isNotEmpty(value); // 断言传入的value不为空，如果为空则抛出AssertionError
    String[] values = value.split(keyDelimiter); // 使用配置的分隔符将字符串分割为字符串数组
    Object[] arr = new Object[fields.size()]; // 创建对象数组，大小与字段数量相等
    assert values.length == arr.length; // 断言分割后的字段数量与定义的字段数量一致
    for (int i = 0; i < arr.length; i++) { // 遍历所有字段
      arr[i] = values[i] == null ? "" : values[i]; // 如果值为null则设置为空字符串，否则使用原值
    }
    return arr; // 返回解析后的对象数组
  }

  List<Object[]> parse(Iterable<String> keys) { // 解析可迭代的字符串集合，根据数据格式类型进行解析并返回Object数组列表
    List<Object[]> objects = new ArrayList<>(); // 创建结果列表，用于存储解析后的对象数组
    for (String key : keys) { // 遍历所有key或value
      if (dataType == RedisDataType.STRING) { // 如果Redis数据类型是STRING
        key = jedis.get(key); // 使用GET命令获取key对应的实际value值
      }
      switch (redisDataFormat) { // 根据数据格式类型选择解析方式
      case RAW: // 原始格式（RAW）
        objects.add(new Object[]{key}); // 直接将值作为单个元素的数组添加到结果中
        break; // 跳出switch
      case JSON: // JSON格式
        objects.add(parseJson(key)); // 使用JSON解析器解析并添加到结果中
        break; // 跳出switch
      case CSV: // CSV格式
        objects.add(parseCsv(key)); // 使用CSV解析器解析并添加到结果中
        break; // 跳出switch
      default: // 未知格式
        break; // 跳过，不做处理
      }
    }
    return objects; // 返回解析后的对象数组列表
  }

  public List<Object[]> parse(List<String> keys) { // 解析字符串列表，根据数据格式类型进行解析并返回Object数组列表（公共方法）
    List<Object[]> objects = new ArrayList<>(); // 创建结果列表，用于存储解析后的对象数组
    for (String key : keys) { // 遍历所有key或value
      if (dataType == RedisDataType.STRING) { // 如果Redis数据类型是STRING
        key = jedis.get(key); // 使用GET命令获取key对应的实际value值
      }
      switch (redisDataFormat) { // 根据数据格式类型选择解析方式
      case RAW: // 原始格式（RAW）
        objects.add(new Object[]{key}); // 直接将值作为单个元素的数组添加到结果中
        break; // 跳出switch
      case JSON: // JSON格式
        objects.add(parseJson(key)); // 使用JSON解析器解析并添加到结果中
        break; // 跳出switch
      case CSV: // CSV格式
        objects.add(parseCsv(key)); // 使用CSV解析器解析并添加到结果中
        break; // 跳出switch
      default: // 未知格式
        break; // 跳过，不做处理
      }
    }
    return objects; // 返回解析后的对象数组列表
  }
}
