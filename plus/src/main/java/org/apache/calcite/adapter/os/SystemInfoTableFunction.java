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
// Apache许可证声明，说明该文件遵循Apache 2.0许可证
package org.apache.calcite.adapter.os; // 定义包名，该类位于org.apache.calcite.adapter.os包下，属于Calcite的操作系统适配器模块

import org.apache.calcite.DataContext; // 导入DataContext类，用于提供数据上下文信息，包含会话变量、配置等运行时信息
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，用于实现LINQ风格的可枚举集合，提供数据遍历功能
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，定义可枚举数据源的标准接口，支持延迟查询执行
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，用于遍历可枚举集合的枚举器，提供逐行访问数据的能力
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，描述表或表达式的数据结构
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建和管理RelDataType对象的工厂类
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，定义可扫描表的接口，表数据可以通过扫描方式获取
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL标准数据类型名称，如VARCHAR、INTEGER、BIGINT等

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标注可能为null的值，帮助静态分析工具进行空值检查

/**
 * Table function that executes the OS "system_info".
 */ // 类注释：这是一个表函数类，用于执行操作系统的"system_info"命令，获取系统硬件信息
// 该类实现了Calcite的表函数机制，允许在SQL查询中调用系统信息获取功能
// 返回的数据包含主机名、CPU信息、内存信息、硬件厂商等详细的系统配置信息
public class SystemInfoTableFunction { // 定义SystemInfoTableFunction类，这是一个表函数类，用于获取操作系统系统信息
  private SystemInfoTableFunction() { // 私有构造方法，防止实例化该类，因为该类只提供静态方法，不需要创建对象实例
  } // 构造方法结束，空实现，确保该类不能被实例化

  public static ScannableTable eval(boolean b) { // 静态方法eval，返回一个ScannableTable对象，这是Calcite表函数的入口方法
    // 参数b是一个布尔值，虽然当前实现中未使用，但保留作为未来扩展的参数
    // 该方法创建并返回一个匿名内部类实例，该类继承自AbstractBaseScannableTable
    return new AbstractBaseScannableTable() { // 创建AbstractBaseScannableTable的匿名子类实例，实现可扫描表的功能
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，执行表扫描并返回可枚举的数据集合
        // 参数root是DataContext对象，包含查询执行的上下文信息
        // 返回值是Enumerable<Object[]>，表示可枚举的对象数组集合，每个数组代表一行数据
        return new AbstractEnumerable<Object[]>() { // 创建AbstractEnumerable的匿名子类，实现可枚举集合
          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回用于遍历数据的枚举器对象
            return new OsQuery("system_info"); // 创建OsQuery对象，传入"system_info"命令作为参数，用于执行系统信息查询
            // OsQuery是枚举器实现，负责执行操作系统命令并将输出结果转换为可枚举的数据行
          } // enumerator方法结束
        }; // AbstractEnumerable匿名类创建结束
      } // scan方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型结构
        // 参数typeFactory是RelDataTypeFactory对象，用于创建RelDataType实例
        // 返回值是RelDataType对象，描述该表包含的列及其数据类型
        return typeFactory.builder() // 创建RelDataType构建器，用于构建行类型定义
            .add("hostname", SqlTypeName.VARCHAR) // 添加hostname列，类型为VARCHAR，表示主机名
            .add("uuid", SqlTypeName.VARCHAR) // 添加uuid列，类型为VARCHAR，表示系统唯一标识符
            .add("cpu_type", SqlTypeName.VARCHAR) // 添加cpu_type列，类型为VARCHAR，表示CPU类型（如x86_64、ARM等）
            .add("cpu_vendor", SqlTypeName.VARCHAR) // 添加cpu_vendor列，类型为VARCHAR，表示CPU厂商（如Intel、AMD等）
            .add("cpu_model", SqlTypeName.VARCHAR) // 添加cpu_model列，类型为VARCHAR，表示CPU型号（如Core i7、Ryzen等）
            .add("cpu_physical_cores", SqlTypeName.INTEGER) // 添加cpu_physical_cores列，类型为INTEGER，表示物理CPU核心数
            .add("cpu_logical_cores", SqlTypeName.INTEGER) // 添加cpu_logical_cores列，类型为INTEGER，表示逻辑CPU核心数（包含超线程）
            .add("cpu_sockets", SqlTypeName.INTEGER) // 添加cpu_sockets列，类型为INTEGER，表示CPU插槽数（物理CPU数量）
            .add("physical_memory", SqlTypeName.BIGINT) // 添加physical_memory列，类型为BIGINT，表示物理内存大小（字节）
            .add("hardware_vendor", SqlTypeName.VARCHAR) // 添加hardware_vendor列，类型为VARCHAR，表示硬件厂商
            .add("hardware_model", SqlTypeName.VARCHAR) // 添加hardware_model列，类型为VARCHAR，表示硬件型号
            .add("hardware_version", SqlTypeName.VARCHAR) // 添加hardware_version列，类型为VARCHAR，表示硬件版本
            .add("hardware_serial", SqlTypeName.VARCHAR) // 添加hardware_serial列，类型为VARCHAR，表示硬件序列号
            .add("board_vendor", SqlTypeName.VARCHAR) // 添加board_vendor列，类型为VARCHAR，表示主板厂商
            .add("board_model", SqlTypeName.VARCHAR) // 添加board_model列，类型为VARCHAR，表示主板型号
            .add("board_version", SqlTypeName.VARCHAR) // 添加board_version列，类型为VARCHAR，表示主板版本
            .add("board_serial", SqlTypeName.VARCHAR) // 添加board_serial列，类型为VARCHAR，表示主板序列号
            .add("computer_name", SqlTypeName.VARCHAR) // 添加computer_name列，类型为VARCHAR，表示计算机名称
            .build(); // 构建RelDataType对象，完成行类型定义
      } // getRowType方法结束
    }; // AbstractBaseScannableTable匿名类创建结束
  } // eval方法结束
} // SystemInfoTableFunction类定义结束
