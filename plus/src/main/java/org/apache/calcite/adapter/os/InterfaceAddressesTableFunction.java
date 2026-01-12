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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.os; // 定义包名，该类位于org.apache.calcite.adapter.os包下，属于Calcite框架的操作系统适配器模块

import org.apache.calcite.DataContext; // 导入DataContext类，用于提供查询执行时的上下文信息，包含会话相关的数据
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，用于实现LINQ风格的枚举功能，提供可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，表示数据枚举器，用于遍历数据集合中的元素
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，描述表的结构和字段类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型实例的工厂
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表，支持全表扫描操作
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL标准的数据类型名称

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标注可能为null的值，进行空值检查

/**
 * Table function that executes the OS "interface_addresses".
 */ // 类的JavaDoc注释，说明这是一个表函数，用于执行操作系统的"interface_addresses"命令，获取网络接口地址信息
public class InterfaceAddressesTableFunction { // 定义InterfaceAddressesTableFunction类，这是一个表函数类，用于将操作系统的网络接口地址信息暴露为Calcite可查询的表
  private InterfaceAddressesTableFunction() { // 私有构造方法，防止实例化，因为该类只包含静态方法，不需要创建对象实例
  } // 构造方法体为空，仅用于阻止外部实例化

  public static ScannableTable eval(boolean b) { // 定义静态eval方法，返回ScannableTable对象，参数b是布尔值但未使用（可能是为了兼容某些调用约定），该方法创建并返回一个可扫描的表对象
    return new AbstractBaseScannableTable() { // 返回AbstractBaseScannableTable的匿名子类实例，AbstractBaseScannableTable是抽象基础可扫描表类，实现了ScannableTable接口
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，执行表扫描并返回可枚举的数据集合，参数root是DataContext对象提供查询上下文
        return new AbstractEnumerable<Object[]>() { // 返回AbstractEnumerable的匿名子类实例，用于提供可枚举的数据集合
          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回数据枚举器，用于遍历数据
            return new OsQuery("interface_addresses"); // 创建并返回OsQuery对象，传入"interface_addresses"命令，OsQuery会执行该系统命令并返回结果作为枚举数据
          } // enumerator方法结束，返回OsQuery枚举器
        }; // AbstractEnumerable匿名类定义结束
      } // scan方法结束，返回可枚举的数据集合

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行结构（字段类型），参数typeFactory是关系数据类型工厂
        return typeFactory.builder() // 使用工厂创建RelDataType构建器，用于构建表的行类型结构
            .add("name", SqlTypeName.VARCHAR) // 添加"name"字段，类型为VARCHAR，表示网络接口名称
            .add("ipv4_address", SqlTypeName.VARCHAR) // 添加"ipv4_address"字段，类型为VARCHAR，表示IPv4地址
            .add("ipv6_address", SqlTypeName.VARCHAR) // 添加"ipv6_address"字段，类型为VARCHAR，表示IPv6地址
            .add("mac", SqlTypeName.VARCHAR) // 添加"mac"字段，类型为VARCHAR，表示MAC地址（物理地址）
            .add("status", SqlTypeName.VARCHAR) // 添加"status"字段，类型为VARCHAR，表示网络接口状态（如up/down）
            .build(); // 构建并返回RelDataType对象，完成行类型定义
      } // getRowType方法结束，返回表的行类型结构
    }; // AbstractBaseScannableTable匿名类定义结束
  } // eval方法结束，返回ScannableTable对象
} // InterfaceAddressesTableFunction类定义结束
