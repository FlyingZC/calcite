/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者协议许可
 * this work for additional information regarding copyright ownership.  // 有关版权所有权的附加信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权给您
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证URL地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS, // 否则按"原样"分发软件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不含任何明示或暗示的担保或条件
 * See the License for the specific language governing permissions and // 详见许可证以了解权限和限制
 * limitations under the License. // 许可证下的限制
 */
package org.apache.calcite.adapter.redis; // 声明包名：org.apache.calcite.adapter.redis，表示这个类属于Calcite项目的Redis适配器模块

/**
 * Define the data processing type of redis. // 定义Redis的数据处理类型枚举类
 */ // 这是一个枚举类，用于表示Redis存储数据的格式类型，决定了Calcite如何解析Redis中的数据
public enum RedisDataFormat { // 定义RedisDataFormat枚举，包含RAW、CSV、JSON三种数据格式
  /**
   * Treat redis key and value as a string format. // 将Redis的键和值视为原始字符串格式处理
   */ // RAW格式：不进行任何解析，将Redis中的数据直接作为字符串处理
  RAW("raw"), // RAW枚举常量，对应的类型名称字符串为"raw"，表示原始字符串格式

  /**
   * Treat redis key and value as a csv format And parse the string // 将Redis的键和值视为CSV格式并解析字符串
   * to get the corresponding field content,The default separator is colon. // 以获取对应的字段内容，默认分隔符是冒号
   */ // CSV格式：将Redis中的数据按照CSV格式解析，使用分隔符（默认为冒号）来分割字段
  CSV("csv"), // CSV枚举常量，对应的类型名称字符串为"csv"，表示CSV格式

  /**
   * Treat redis key and value as a json format And parse the json string // 将Redis的键和值视为JSON格式并解析JSON字符串
   * to get the corresponding field content. // 以获取对应的字段内容
   */ // JSON格式：将Redis中的数据作为JSON字符串解析，可以提取JSON中的各个字段
  JSON("json"); // JSON枚举常量，对应的类型名称字符串为"json"，表示JSON格式

  private final String typeName; // 成员变量：存储该枚举常量对应的类型名称字符串，用于序列化和反序列化

  RedisDataFormat(String typeName) { // 构造方法：私有构造函数，用于初始化枚举常量
    this.typeName = typeName; // 将传入的类型名称字符串赋值给成员变量typeName
  }

  public static RedisDataFormat fromTypeName(String typeName) { // 静态工厂方法：根据类型名称字符串获取对应的枚举常量
    for (RedisDataFormat type : RedisDataFormat.values()) { // 遍历所有RedisDataFormat枚举常量
      if (type.getTypeName().equals(typeName)) { // 比较当前枚举常量的类型名称是否与传入的typeName相等
        return type; // 如果匹配，返回对应的枚举常量
      }
    }
    return null; // 如果没有匹配的枚举常量，返回null
  }

  public String getTypeName() { // 公共方法：获取当前枚举常量对应的类型名称字符串
    return this.typeName; // 返回成员变量typeName的值
  }
}
