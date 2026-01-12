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
// Apache许可证声明，允许在遵守Apache 2.0许可证的前提下使用、修改和分发代码
package org.apache.calcite.adapter.pig; // 声明包名，该类属于org.apache.calcite.adapter.pig包，这是Calcite框架中用于适配Pig的包

import org.apache.calcite.sql.type.SqlTypeName; // 导入Calcite的SQL类型名枚举，用于表示标准SQL数据类型

import org.apache.pig.data.DataType; // 导入Pig的数据类型类，用于表示Pig的数据类型

import static org.apache.calcite.sql.type.SqlTypeName.VARCHAR; // 静态导入VARCHAR类型，方便直接使用

/**
 * Supported Pig data types and their Calcite counterparts.
 * 支持的Pig数据类型及其在Calcite中对应的类型
 * 这个枚举类用于建立Pig数据类型和Calcite SQL数据类型之间的映射关系
 * 它是Calcite适配器模式的一部分，使得Calcite能够理解和处理Pig的数据类型
 * 当Calcite需要与Pig进行交互时，通过这个枚举来进行类型转换和映射
 */
public enum PigDataType { // 定义一个枚举类，用于表示Pig数据类型到Calcite SQL类型的映射关系，枚举类型确保类型安全

  CHARARRAY(DataType.CHARARRAY, VARCHAR); // 定义CHARARRAY枚举常量，对应Pig的字符数组类型，映射到Calcite的VARCHAR类型，这是目前唯一支持的Pig数据类型

  private final byte pigType; // 存储Pig数据类型的字节表示，Pig使用byte类型来标识不同的数据类型，final表示该字段不可变
  private final SqlTypeName sqlType; // 存储对应的Calcite SQL类型名，用于在Calcite内部表示该数据类型，final表示该字段不可变

  PigDataType(byte pigType, SqlTypeName sqlType) { // 构造方法，用于创建枚举实例时初始化Pig类型和对应的SQL类型
    this.pigType = pigType; // 将传入的Pig类型字节值赋给实例变量pigType，保存Pig的类型标识
    this.sqlType = sqlType; // 将传入的SQL类型名赋给实例变量sqlType，保存对应的Calcite SQL类型
  }

  public byte getPigType() { // 公共getter方法，用于获取当前枚举实例对应的Pig数据类型字节值
    return pigType; // 返回Pig类型的字节表示，供外部调用者使用
  }

  public SqlTypeName getSqlType() { // 公共getter方法，用于获取当前枚举实例对应的Calcite SQL类型名
    return sqlType; // 返回Calcite SQL类型名，供外部调用者使用
  }

  public static PigDataType valueOf(byte pigType) { // 静态工厂方法，根据Pig类型字节值查找对应的PigDataType枚举实例
    for (PigDataType pigDataType : values()) { // 遍历所有PigDataType枚举实例，查找与传入pigType匹配的实例
      if (pigDataType.pigType == pigType) { // 比较当前枚举实例的pigType与传入的pigType是否相等
        return pigDataType; // 如果匹配，返回对应的PigDataType枚举实例
      }
    }
    throw new IllegalArgumentException( // 如果遍历完所有枚举实例都没有找到匹配的类型，抛出非法参数异常
        "Pig data type " + DataType.findTypeName(pigType) + " is not supported"); // 异常消息包含不支持的Pig类型名称，使用DataType.findTypeName获取类型名称
  }

  public static PigDataType valueOf(SqlTypeName sqlType) { // 静态工厂方法，根据Calcite SQL类型名查找对应的PigDataType枚举实例
    for (PigDataType pigDataType : values()) { // 遍历所有PigDataType枚举实例，查找与传入sqlType匹配的实例
      if (pigDataType.sqlType == sqlType) { // 比较当前枚举实例的sqlType与传入的sqlType是否相等
        return pigDataType; // 如果匹配，返回对应的PigDataType枚举实例
      }
    }
    throw new IllegalArgumentException("SQL data type " + sqlType + " is not supported"); // 如果遍历完所有枚举实例都没有找到匹配的类型，抛出非法参数异常，异常消息包含不支持的SQL类型名
  }
} // 枚举类定义结束，这个类提供了Pig和Calcite类型系统的桥接功能
