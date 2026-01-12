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
package org.apache.calcite.adapter.os; // 声明包名，该类属于 org.apache.calcite.adapter.os 包，这是 Calcite 框架中用于操作系统适配器的包

import org.apache.calcite.DataContext; // 导入 DataContext 类，用于在查询执行过程中传递上下文信息，如会话变量、配置参数等
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入 AbstractEnumerable 类，这是 LINQ4J 框架中的抽象可枚举类，用于实现可枚举的数据源
import org.apache.calcite.linq4j.Enumerable; // 导入 Enumerable 接口，表示可以被枚举查询的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入 Enumerator 接口，表示数据迭代器，用于逐行遍历数据
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 类，表示关系型数据类型，用于描述表的结构
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，用于创建关系型数据类型
import org.apache.calcite.schema.ScannableTable; // 导入 ScannableTable 接口，表示可以被扫描查询的表
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举，定义了 SQL 标准的数据类型名称

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可能为 null 的值，帮助进行空值检查

/**
 * Table function that executes the OS "memory_info". // 此类是一个表函数，用于执行操作系统的 "memory_info" 命令来获取内存信息
 * 这个类的作用是在 Calcite SQL 查询中提供一个虚拟表，该表包含操作系统的内存使用情况信息
 * 通过调用操作系统的底层命令（如 Linux 的 free 命令或 Windows 的系统 API），获取内存统计数据
 * 返回的表包含内存总量、已用内存、空闲内存、交换分区总量、已用交换分区、空闲交换分区、换入页数、换出页数等字段
 * 这个类实现了 Calcite 的表函数机制，允许用户在 SQL 查询中像查询普通表一样查询系统内存信息
 * 例如：SELECT * FROM TABLE(memory_info(true)) 可以获取系统内存信息
 */
public class MemoryInfoTableFunction { // 定义 MemoryInfoTableFunction 类，这是一个表函数类，用于获取操作系统内存信息
  private MemoryInfoTableFunction() { // 私有构造方法，防止外部实例化此类，因为这是一个工具类，所有方法都是静态的
  } // 构造方法结束

  public static ScannableTable eval(boolean b) { // 静态方法 eval，这是表函数的入口方法，参数 b 是一个布尔标志（虽然当前实现中未使用，但保留以备将来扩展），返回一个 ScannableTable 对象，该对象可以被 Calcite 查询引擎扫描
    return new AbstractBaseScannableTable() { // 返回一个匿名内部类，继承自 AbstractBaseScannableTable（这是一个抽象基类，提供了 ScannableTable 接口的基本实现）
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写 scan 方法，该方法负责扫描数据并返回一个可枚举的数据集合，参数 root 是 DataContext 对象，包含查询执行上下文信息，返回值是 Enumerable<Object[]>，表示可以枚举的对象数组（每一行数据是一个 Object 数组）
        return new AbstractEnumerable<Object[]>() { // 返回一个匿名内部类，继承自 AbstractEnumerable，实现可枚举的数据源
          @Override public Enumerator<Object[]> enumerator() { // 重写 enumerator 方法，该方法返回一个数据迭代器，用于逐行遍历数据
            return new OsQuery("memory_info"); // 创建并返回一个 OsQuery 对象，传入命令名称 "memory_info"，OsQuery 类负责执行操作系统命令并将结果转换为可枚举的数据流
          } // enumerator 方法结束
        }; // AbstractEnumerable 匿名类结束
      } // scan 方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写 getRowType 方法，该方法定义返回数据的行类型（表结构），参数 typeFactory 是 RelDataTypeFactory 对象，用于创建关系型数据类型
        return typeFactory.builder() // 使用 typeFactory 创建一个 RelDataType.Builder 对象，用于构建行类型
            .add("mem_total", SqlTypeName.DOUBLE) // 添加第一个字段 "mem_total"，表示内存总量（单位通常是 MB 或 GB），数据类型为 DOUBLE（双精度浮点数）
            .add("mem_used", SqlTypeName.DOUBLE) // 添加第二个字段 "mem_used"，表示已使用的内存量，数据类型为 DOUBLE
            .add("mem_free", SqlTypeName.DOUBLE) // 添加第三个字段 "mem_free"，表示空闲的内存量，数据类型为 DOUBLE
            .add("swap_total", SqlTypeName.DOUBLE) // 添加第四个字段 "swap_total"，表示交换分区（虚拟内存）总量，数据类型为 DOUBLE
            .add("swap_used", SqlTypeName.DOUBLE) // 添加第五个字段 "swap_used"，表示已使用的交换分区量，数据类型为 DOUBLE
            .add("swap_free", SqlTypeName.DOUBLE) // 添加第六个字段 "swap_free"，表示空闲的交换分区量，数据类型为 DOUBLE
            .add("swap_pages_in", SqlTypeName.DOUBLE) // 添加第七个字段 "swap_pages_in"，表示从磁盘换入内存的页面数，数据类型为 DOUBLE
            .add("swap_pages_out", SqlTypeName.DOUBLE) // 添加第八个字段 "swap_pages_out"，表示从内存换出到磁盘的页面数，数据类型为 DOUBLE
            .build(); // 构建并返回 RelDataType 对象，完成行类型的定义
      } // getRowType 方法结束
    }; // AbstractBaseScannableTable 匿名类结束
  } // eval 方法结束
} // MemoryInfoTableFunction 类结束
