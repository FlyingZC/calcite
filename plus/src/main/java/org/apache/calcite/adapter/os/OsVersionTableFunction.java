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
// Apache许可证声明，规定代码的使用权限和限制条件
package org.apache.calcite.adapter.os; // 定义包名，表示这个类属于org.apache.calcite.adapter.os包，该包包含与操作系统相关的适配器

import org.apache.calcite.DataContext; // 导入DataContext类，用于在执行SQL查询时传递上下文信息（如会话变量、用户信息等）
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，用于实现LINQ风格的枚举器，提供可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，支持LINQ查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历数据集合，提供类似迭代器的功能
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，用于描述表的结构（列名、数据类型等）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建RelDataType对象，是类型系统的工厂类
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可以被扫描的表，用于实现自定义表的数据源
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义了SQL标准的数据类型（如VARCHAR、BIGINT等）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值，帮助进行静态空值检查

/**
 * Table function that executes the OS "os_version".
 */ // 类的JavaDoc注释：说明这是一个表函数，用于执行操作系统的"os_version"命令
public class OsVersionTableFunction { // 定义OsVersionTableFunction类，这是一个表函数类，用于获取操作系统版本信息
  private OsVersionTableFunction() { // 私有构造方法，防止外部实例化此类，因为这是一个工具类，只通过静态方法提供功能
  } // 构造方法结束，没有任何实现，因为不需要实例化

  public static ScannableTable eval(boolean b) { // 静态方法eval，接收一个布尔参数b（未使用，可能是为了兼容函数签名），返回一个ScannableTable对象
    return new AbstractBaseScannableTable() { // 创建并返回一个匿名内部类实例，继承自AbstractBaseScannableTable，实现了ScannableTable接口
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，用于扫描表数据并返回可枚举的结果集，参数root是数据上下文
        return new AbstractEnumerable<Object[]>() { // 创建并返回一个匿名内部类实例，继承自AbstractEnumerable，提供可枚举的数据集合
          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回一个枚举器对象，用于遍历数据
            return new OsQuery("os_version"); // 创建并返回OsQuery对象，传入"os_version"命令，该对象会执行操作系统命令并返回结果
          } // enumerator方法结束
        }; // AbstractEnumerable匿名类实例化结束
      } // scan方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，用于定义表的行类型（即表的结构，包含哪些列及其数据类型）
        return typeFactory.builder() // 使用类型工厂创建一个构建器，用于构建RelDataType对象
            .add("name", SqlTypeName.VARCHAR) // 添加名为"name"的列，数据类型为VARCHAR，表示操作系统名称
            .add("version", SqlTypeName.VARCHAR) // 添加名为"version"的列，数据类型为VARCHAR，表示操作系统版本号
            .add("build", SqlTypeName.VARCHAR) // 添加名为"build"的列，数据类型为VARCHAR，表示操作系统构建号
            .add("code_name", SqlTypeName.VARCHAR) // 添加名为"code_name"的列，数据类型为VARCHAR，表示操作系统的代号（如Windows的代号）
            .add("install_date", SqlTypeName.BIGINT) // 添加名为"install_date"的列，数据类型为BIGINT，表示操作系统的安装日期（时间戳）
            .build(); // 构建并返回RelDataType对象，完成表结构的定义
      } // getRowType方法结束
    }; // AbstractBaseScannableTable匿名类实例化结束
  } // eval方法结束
} // OsVersionTableFunction类定义结束
