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
package org.apache.calcite.adapter.redis; // Apache Calcite Redis适配器包，提供Redis数据源的适配功能

import java.util.LinkedHashMap; // 导入LinkedHashMap类，用于保持字段定义的顺序
import java.util.List; // 导入List接口，用于存储字段列表

/**
 * get the redis table's field info. // 获取Redis表的字段信息
 * 
 * RedisTableFieldInfo类用于描述和存储Redis表的元数据信息，包括表名、数据格式、字段定义等。
 * 该类是Calcite适配器模式中用于连接Redis数据源的关键组件，负责将Redis的数据结构映射为Calcite可识别的表结构。
 * 
 * 主要功能：
 * 1. 存储Redis表的名称标识
 * 2. 定义表的数据格式（如JSON、CSV等）
 * 3. 维护表的字段列表及其属性
 * 4. 配置Redis键的分隔符
 * 
 * 使用场景：
 * - 在RedisSchema初始化时，用于加载和解析表的元数据配置
 * - 在查询计划生成时，提供表的字段信息用于类型推断和验证
 * - 在数据读写操作时，指导如何将Redis数据映射为Calcite的Relational数据模型
 */
public class RedisTableFieldInfo { // Redis表字段信息类，封装Redis表的元数据
  private String tableName; // 表名，标识Redis中的数据表名称
  private String dataFormat; // 数据格式，指定Redis中存储的数据格式（如"json"、"csv"等）
  private List<LinkedHashMap<String, Object>> fields; // 字段列表，每个字段是一个LinkedHashMap，包含字段名、类型等属性，使用LinkedHashMap保持字段定义的顺序
  private String keyDelimiter = ":"; // 键分隔符，用于组合Redis键的分隔符，默认为冒号":"，例如"table:primaryKey"

  public String getDataFormat() { // 获取数据格式的方法
    return dataFormat; // 返回当前表的数据格式字符串
  }

  public void setDataFormat(String dataFormat) { // 设置数据格式的方法
    this.dataFormat = dataFormat; // 将传入的数据格式字符串赋值给成员变量dataFormat
  }

  public String getTableName() { // 获取表名的方法
    return tableName; // 返回当前表的名称字符串
  }

  public void setTableName(String tableName) { // 设置表名的方法
    this.tableName = tableName; // 将传入的表名字符串赋值给成员变量tableName
  }

  public List<LinkedHashMap<String, Object>> getFields() { // 获取字段列表的方法
    return fields; // 返回当前表的字段列表，每个字段是一个LinkedHashMap包含字段属性
  }

  public void setFields(List<LinkedHashMap<String, Object>> fields) { // 设置字段列表的方法
    this.fields = fields; // 将传入的字段列表赋值给成员变量fields
  }

  public String getKeyDelimiter() { // 获取键分隔符的方法
    return keyDelimiter; // 返回当前使用的键分隔符字符串
  }

  public void setKeyDelimiter(String keyDelimiter) { // 设置键分隔符的方法
    this.keyDelimiter = keyDelimiter; // 将传入的键分隔符字符串赋值给成员变量keyDelimiter
  }
}
