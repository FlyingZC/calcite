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
package org.apache.calcite.adapter.innodb;

import org.apache.calcite.sql.type.SqlTypeName;

import com.alibaba.innodb.java.reader.column.ColumnType;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

/**
 * InnoDB列类型到SQL类型的转换规则映射类
 * 
 * 本类负责将innodb-java-reader库中定义的InnoDB列类型
 * 映射为Calcite框架中对应的SQL类型(SqlTypeName)
 * 
 * 主要功能：
 * 1. 维护InnoDB列类型到Calcite SQL类型的静态映射关系
 * 2. 提供单例模式访问转换规则
 * 3. 支持根据InnoDB类型名称查找对应的SQL类型
 * 
 * 应用场景：当Calcite适配器需要读取InnoDB表结构时，
 * 需要将InnoDB的列类型转换为Calcite能够识别的SQL类型
 * 
 * 设计模式：单例模式(Singleton Pattern)，确保全局只有一个转换规则实例
 */
public class ColumnTypeToSqlTypeConversionRules {
  //~ Static fields/initializers ---------------------------------------------

  // 单例实例，使用饿汉式初始化，确保类加载时就创建实例，线程安全
  // INSTANCE是类的唯一实例，通过instance()方法获取
  private static final ColumnTypeToSqlTypeConversionRules INSTANCE =
      new ColumnTypeToSqlTypeConversionRules();

  //~ Instance fields --------------------------------------------------------

  // 转换规则映射表，键为InnoDB列类型名称(字符串)，值为对应的Calcite SQL类型
  // 使用ImmutableMap构建不可变映射，确保线程安全和数据一致性
  // rules在构造函数中初始化后不可修改
  private final Map<String, SqlTypeName> rules =
      ImmutableMap.<String, SqlTypeName>builder()
          // 整数类型映射：将InnoDB的整数类型映射为Calcite对应的整数类型
          .put(ColumnType.TINYINT, SqlTypeName.TINYINT) // TINYINT类型映射，1字节有符号整数
          .put(ColumnType.SMALLINT, SqlTypeName.SMALLINT) // SMALLINT类型映射，2字节有符号整数
          .put(ColumnType.MEDIUMINT, SqlTypeName.INTEGER) // MEDIUMINT类型映射，3字节有符号整数，映射为Calcite的INTEGER
          .put(ColumnType.INT, SqlTypeName.INTEGER) // INT类型映射，4字节有符号整数
          .put(ColumnType.BIGINT, SqlTypeName.BIGINT) // BIGINT类型映射，8字节有符号整数
          // 无符号整数类型映射：将InnoDB的无符号整数类型映射为Calcite对应的整数类型
          // 注意：Calcite的类型系统对无符号类型的支持有限，这里映射为对应的有符号类型
          .put(ColumnType.UNSIGNED_TINYINT, SqlTypeName.TINYINT) // 无符号TINYINT映射为TINYINT
          .put(ColumnType.UNSIGNED_SMALLINT, SqlTypeName.SMALLINT) // 无符号SMALLINT映射为SMALLINT
          .put(ColumnType.UNSIGNED_MEDIUMINT, SqlTypeName.INTEGER) // 无符号MEDIUMINT映射为INTEGER
          .put(ColumnType.UNSIGNED_INT, SqlTypeName.INTEGER) // 无符号INT映射为INTEGER
          .put(ColumnType.UNSIGNED_BIGINT, SqlTypeName.BIGINT) // 无符号BIGINT映射为BIGINT

          // 浮点数和定点数类型映射：将InnoDB的浮点数和定点数类型映射为Calcite对应的数值类型
          .put(ColumnType.FLOAT, SqlTypeName.REAL) // FLOAT类型映射，单精度浮点数
          .put(ColumnType.REAL, SqlTypeName.REAL) // REAL类型映射，双精度浮点数
          .put(ColumnType.DOUBLE, SqlTypeName.DOUBLE) // DOUBLE类型映射，双精度浮点数
          .put(ColumnType.DECIMAL, SqlTypeName.DECIMAL) // DECIMAL类型映射，定点数，精确数值
          .put(ColumnType.NUMERIC, SqlTypeName.DECIMAL) // NUMERIC类型映射，同DECIMAL，精确数值

          // 布尔类型映射：将InnoDB的布尔类型映射为Calcite的BOOLEAN类型
          .put(ColumnType.BOOL, SqlTypeName.BOOLEAN) // BOOL类型映射，布尔值
          .put(ColumnType.BOOLEAN, SqlTypeName.BOOLEAN) // BOOLEAN类型映射，布尔值

          // 字符串和二进制类型映射：将InnoDB的字符串和二进制类型映射为Calcite对应的类型
          .put(ColumnType.CHAR, SqlTypeName.CHAR) // CHAR类型映射，定长字符串
          .put(ColumnType.VARCHAR, SqlTypeName.VARCHAR) // VARCHAR类型映射，变长字符串
          .put(ColumnType.BINARY, SqlTypeName.BINARY) // BINARY类型映射，定长二进制数据
          .put(ColumnType.VARBINARY, SqlTypeName.VARBINARY) // VARBINARY类型映射，变长二进制数据
          .put(ColumnType.TINYBLOB, SqlTypeName.VARBINARY) // TINYBLOB类型映射，最大255字节的二进制大对象
          .put(ColumnType.MEDIUMBLOB, SqlTypeName.VARBINARY) // MEDIUMBLOB类型映射，最大16MB的二进制大对象
          .put(ColumnType.BLOB, SqlTypeName.VARBINARY) // BLOB类型映射，最大65KB的二进制大对象
          .put(ColumnType.LONGBLOB, SqlTypeName.VARBINARY) // LONGBLOB类型映射，最大4GB的二进制大对象
          .put(ColumnType.TINYTEXT, SqlTypeName.VARCHAR) // TINYTEXT类型映射，最大255字节的文本
          .put(ColumnType.MEDIUMTEXT, SqlTypeName.VARCHAR) // MEDIUMTEXT类型映射，最大16MB的文本
          .put(ColumnType.TEXT, SqlTypeName.VARCHAR) // TEXT类型映射，最大65KB的文本
          .put(ColumnType.LONGTEXT, SqlTypeName.VARCHAR) // LONGTEXT类型映射，最大4GB的文本

          // 日期时间类型映射：将InnoDB的日期时间类型映射为Calcite对应的日期时间类型
          .put(ColumnType.YEAR, SqlTypeName.SMALLINT) // YEAR类型映射，年份值，映射为SMALLINT
          .put(ColumnType.TIME, SqlTypeName.TIME) // TIME类型映射，时间值
          .put(ColumnType.DATE, SqlTypeName.DATE) // DATE类型映射，日期值
          .put(ColumnType.DATETIME, SqlTypeName.TIMESTAMP) // DATETIME类型映射，日期时间值，映射为TIMESTAMP
          .put(ColumnType.TIMESTAMP, SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) // TIMESTAMP类型映射，时间戳，映射为带本地时区的时间戳

          // 特殊类型映射：将InnoDB的特殊类型映射为Calcite对应的类型
          .put(ColumnType.ENUM, SqlTypeName.VARCHAR) // ENUM类型映射，枚举值，映射为VARCHAR
          .put(ColumnType.SET, SqlTypeName.VARCHAR) // SET类型映射，集合值，映射为VARCHAR
          .put(ColumnType.BIT, SqlTypeName.VARBINARY) // BIT类型映射，位字段，映射为VARBINARY

          .build(); // 构建不可变映射表，完成rules的初始化

  //~ Methods ----------------------------------------------------------------

  /**
   * 获取ColumnTypeToSqlTypeConversionRules的单例实例
   * 
   * 本方法实现了单例模式，返回类的唯一实例
   * 使用场景：当需要使用类型转换规则时，通过此方法获取实例
   * 
   * @return ColumnTypeToSqlTypeConversionRules的单例实例
   */
  public static ColumnTypeToSqlTypeConversionRules instance() {
    return INSTANCE; // 返回静态初始化的单例实例
  }

  /**
   * 根据InnoDB列类型名称查找对应的Calcite SQL类型
   * 
   * 本方法是类型转换的核心方法，通过类型名称在rules映射表中查找对应的SqlTypeName
   * 如果找不到匹配的类型，则返回SqlTypeName.ANY，表示任意类型
   * 
   * 工作原理：
   * 1. 接收InnoDB列类型名称作为输入参数
   * 2. 在rules映射表中查找该名称对应的SqlTypeName
   * 3. 如果找到，返回对应的SqlTypeName
   * 4. 如果未找到，返回SqlTypeName.ANY作为默认值
   * 
   * 使用场景：当读取InnoDB表结构时，需要将列类型转换为Calcite能够识别的类型
   * 
   * @param name InnoDB列类型名称，例如"TINYINT"、"VARCHAR"、"DATETIME"等
   * @return 对应的SqlTypeName，如果未找到则返回SqlTypeName.ANY
   */
  public SqlTypeName lookup(String name) {
    return rules.getOrDefault(name, SqlTypeName.ANY); // 在rules映射表中查找，未找到则返回ANY
  }
}
