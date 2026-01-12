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
package org.apache.calcite.adapter.file; // 声明包名，这个类属于org.apache.calcite.adapter.file包，是文件适配器的一部分

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口，用于创建Java类型
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive类，表示基本类型及其包装类
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的类型系统

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map构建器

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，表示可能为null

import java.util.Map; // 导入Java的Map接口

/**
 * Type of a field in a Web (HTML) table. // 类注释：定义Web(HTML)表中字段的数据类型
 *
 * <p>Usually, and unless specified explicitly in the header row, a field is // 通常情况下，除非在表头行中明确指定，否则字段类型默认为STRING
 * of type {@link #STRING}. But specifying the field type in the fields // 但在字段中指定字段类型使得编写SQL更加容易
 * makes it easier to write SQL.
 */
enum FileFieldType { // 定义枚举类FileFieldType，表示文件适配器支持的字段类型
  STRING(null, String.class), // 字符串类型，没有对应的Primitive，使用String类
  BOOLEAN(Primitive.BOOLEAN), // 布尔类型，使用Primitive.BOOLEAN
  BYTE(Primitive.BYTE), // 字节类型，使用Primitive.BYTE
  CHAR(Primitive.CHAR), // 字符类型，使用Primitive.CHAR
  SHORT(Primitive.SHORT), // 短整型，使用Primitive.SHORT
  INT(Primitive.INT), // 整型，使用Primitive.INT
  LONG(Primitive.LONG), // 长整型，使用Primitive.LONG
  FLOAT(Primitive.FLOAT), // 单精度浮点型，使用Primitive.FLOAT
  DOUBLE(Primitive.DOUBLE), // 双精度浮点型，使用Primitive.DOUBLE
  DATE(null, java.sql.Date.class), // 日期类型，没有对应的Primitive，使用java.sql.Date类
  TIME(null, java.sql.Time.class), // 时间类型，没有对应的Primitive，使用java.sql.Time类
  TIMESTAMP(null, java.sql.Timestamp.class); // 时间戳类型，没有对应的Primitive，使用java.sql.Timestamp类

  private final @Nullable Primitive primitive; // 成员变量：表示该字段类型对应的基本类型Primitive对象，可能为null（如String、Date等非基本类型）
  private final Class clazz; // 成员变量：表示该字段类型对应的Java类对象（包装类或引用类）

  private static final Map<String, FileFieldType> MAP; // 静态成员变量：类型名称到FileFieldType枚举常量的映射表，用于快速查找

  static { // 静态初始化块，用于初始化MAP映射表
    ImmutableMap.Builder<String, FileFieldType> builder = // 创建不可变Map的构建器
        ImmutableMap.builder(); // 获取构建器实例
    for (FileFieldType value : values()) { // 遍历所有FileFieldType枚举常量
      builder.put(value.clazz.getSimpleName(), value); // 将类型的简单类名（如"String"、"Integer"）作为key，枚举常量作为value放入映射表

      if (value.primitive != null) { // 如果该类型有对应的基本类型Primitive
        builder.put(value.primitive.getPrimitiveName(), value); // 将基本类型的名称（如"int"、"boolean"）作为key，枚举常量作为value也放入映射表
      }
    }
    MAP = builder.build(); // 构建不可变Map并赋值给MAP静态变量
  }

  FileFieldType(Primitive primitive) { // 构造方法：接收一个Primitive对象，用于基本类型的枚举常量
    this(primitive, primitive.getBoxClass()); // 调用另一个构造方法，传入Primitive对象和其对应的包装类
  }

  FileFieldType(@Nullable Primitive primitive, Class clazz) { // 完整构造方法：接收Primitive对象和Java类对象
    this.primitive = primitive; // 初始化primitive成员变量
    this.clazz = clazz; // 初始化clazz成员变量
  }

  public RelDataType toType(JavaTypeFactory typeFactory) { // 方法：将FileFieldType转换为Calcite的RelDataType
    return typeFactory.createJavaType(clazz); // 使用类型工厂创建基于Java类的RelDataType
  }

  public static FileFieldType of(String typeString) { // 静态方法：根据类型字符串获取对应的FileFieldType枚举常量
    return MAP.get(typeString); // 从MAP映射表中查找并返回对应的FileFieldType，如果不存在则返回null
  }
}
