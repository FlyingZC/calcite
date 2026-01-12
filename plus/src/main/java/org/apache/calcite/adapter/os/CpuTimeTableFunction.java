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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证，允许自由使用和修改
package org.apache.calcite.adapter.os; // 声明该类所属的包，位于org.apache.calcite.adapter.os包下，这是Calcite中用于操作系统适配器的包

import org.apache.calcite.DataContext; // 导入DataContext类，表示数据上下文，用于在查询执行过程中传递上下文信息（如配置、变量等）
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入AbstractEnumerable抽象类，提供了可枚举集合的基础实现，用于创建可遍历的数据源
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的集合，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入Enumerator接口，表示枚举器，用于逐个遍历集合中的元素
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型，描述表或表达式的数据类型结构
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型的工厂类
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表，支持通过扫描获取数据
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义了SQL标准的数据类型名称（如BIGINT、VARCHAR等）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数或返回值，帮助静态分析工具检测空指针异常

/**
 * Table function that executes the OS "cpu_info".
 */
// 这是一个表函数类，用于执行操作系统的CPU时间信息查询，将CPU时间统计信息作为表数据返回
// 表函数是Calcite中的一种特殊函数，可以返回一个表而不是单个值
// 这个类提供了查询操作系统CPU时间使用情况的功能，包括用户态、内核态、空闲时间等统计信息
public class CpuTimeTableFunction { // 定义CpuTimeTableFunction类，这是一个工具类，提供静态方法创建CPU时间表函数
  private CpuTimeTableFunction() { // 私有构造方法，防止外部实例化这个类，因为这是一个纯工具类，只提供静态方法
  } // 构造方法体为空，不需要任何初始化操作

  public static ScannableTable eval(boolean b) { // 静态方法eval，用于评估并返回一个可扫描的表对象，参数b是布尔值（虽然未使用，但保留以保持接口一致性）
    return new AbstractBaseScannableTable() { // 返回一个AbstractBaseScannableTable的匿名子类实例，该子类实现了可扫描表的所有必要方法
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，扫描数据并返回可枚举的对象数组集合，参数root是数据上下文
        return new AbstractEnumerable<Object[]>() { // 返回一个AbstractEnumerable的匿名子类实例，提供可枚举的数据源
          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回一个枚举器用于遍历数据
            return new OsQuery("cpu_time"); // 创建并返回一个OsQuery枚举器实例，传入"cpu_time"作为查询类型，表示要查询CPU时间信息
          } // OsQuery枚举器会根据查询类型从操作系统获取相应的CPU时间统计数据
        }; // AbstractEnumerable匿名类结束
      } // scan方法结束，该方法定义了如何扫描和返回CPU时间数据

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型（即表的结构和各列的数据类型），参数typeFactory是类型工厂
        return typeFactory.builder() // 使用类型工厂创建一个RelDataType构建器，用于构建表的结构定义
            .add("idle", SqlTypeName.BIGINT) // 添加"idle"列，数据类型为BIGINT（长整型），表示CPU空闲时间（单位：毫秒或时钟周期）
            .add("nice", SqlTypeName.BIGINT) // 添加"nice"列，数据类型为BIGINT，表示nice优先级进程的CPU时间（用于低优先级进程）
            .add("irq", SqlTypeName.BIGINT) // 添加"irq"列，数据类型为BIGINT，表示硬件中断请求（IRQ）消耗的CPU时间
            .add("soft_irq", SqlTypeName.BIGINT) // 添加"soft_irq"列，数据类型为BIGINT，表示软中断（softirq）消耗的CPU时间
            .add("steal", SqlTypeName.BIGINT) // 添加"steal"列，数据类型为BIGINT，表示在虚拟化环境中被其他虚拟机"偷走"的CPU时间
            .add("system", SqlTypeName.BIGINT) // 添加"system"列，数据类型为BIGINT，表示内核态（系统态）消耗的CPU时间
            .add("user", SqlTypeName.BIGINT) // 添加"user"列，数据类型为BIGINT，表示用户态消耗的CPU时间（即应用程序运行的时间）
            .add("io_wait", SqlTypeName.BIGINT) // 添加"io_wait"列，数据类型为BIGINT，表示等待I/O操作完成的CPU时间
            .build(); // 构建并返回完整的RelDataType对象，定义了CPU时间表的完整结构
      } // getRowType方法结束，该方法定义了返回的表包含8个BIGINT类型的列
    }; // AbstractBaseScannableTable匿名类结束，该匿名类实现了CPU时间表的所有行为
  } // eval静态方法结束，返回一个可扫描的CPU时间表对象
} // CpuTimeTableFunction类结束
