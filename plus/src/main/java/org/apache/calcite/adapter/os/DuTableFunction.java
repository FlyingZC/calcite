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
package org.apache.calcite.adapter.os; // 定义包名，该类位于org.apache.calcite.adapter.os包下，这是Calcite的操作系统适配器包，用于访问操作系统级别的功能

import org.apache.calcite.DataContext; // 导入DataContext类，这是Calcite的数据上下文接口，提供运行时环境和数据访问能力
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，这是Calcite的LINQ风格的集合接口，用于表示可枚举的数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，用于描述表的行结构
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型的工厂类
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可以被扫描的表，是Calcite表的一种实现方式
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL标准的数据类型名称，如BIGINT、VARCHAR等

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的类型，来自CheckerFramework框架

/**
 * Table function that executes the OS "du" ("disk usage") command
 * to compute file sizes.
 * 表函数，用于执行操作系统的"du"（磁盘使用量）命令来计算文件大小
 * 该类实现了Calcite的表函数机制，允许SQL查询直接调用操作系统命令获取磁盘使用信息
 * 返回的表包含两列：size_k（文件大小，单位KB）和path（文件路径）
 * 这是一个无状态的工具类，通过静态方法eval()创建ScannableTable实例
 */
public class DuTableFunction { // 定义DuTableFunction类，这是一个表函数类，用于封装操作系统du命令的执行逻辑
  private DuTableFunction() { // 私有构造方法，防止实例化该类，因为该类只通过静态方法提供服务
  } // 构造方法体为空，表示这是一个纯工具类，不需要实例化

  public static ScannableTable eval(boolean b) { // 定义静态方法eval，接收一个布尔参数b（虽然参数未使用，但为了保持表函数签名一致性），返回一个ScannableTable实例
    return new AbstractBaseScannableTable() { // 创建并返回一个AbstractBaseScannableTable的匿名内部类实例，这是一个可扫描表的抽象实现
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，执行实际的扫描操作，接收DataContext参数，返回可枚举的对象数组（每行数据）
        return Processes.processLines("du", "-ak") // 调用Processes工具类的processLines方法，执行系统命令"du -ak"，-a表示显示所有文件，-k表示以KB为单位显示大小，返回包含命令输出的字符串流
            .select(a0 -> { // 对每一行执行转换操作，a0是命令输出的一行字符串
              final String[] fields = a0.split("\t"); // 使用制表符分割每一行，得到两个字段：文件大小和文件路径
              return new Object[] {Long.valueOf(fields[0]), fields[1]}; // 将分割后的字段转换为对象数组，第一个字段转换为Long类型（文件大小），第二个字段保持String类型（文件路径）
            }); // 结束select转换操作
      } // scan方法结束，返回包含转换后数据的Enumerable

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义返回表的行结构，接收RelDataTypeFactory参数用于构建数据类型
        return typeFactory.builder() // 使用类型工厂创建构建器，用于逐步构建行类型
            .add("size_k", SqlTypeName.BIGINT) // 添加第一列，列名为"size_k"，数据类型为BIGINT（64位整数），表示文件大小（KB）
            .add("path", SqlTypeName.VARCHAR) // 添加第二列，列名为"path"，数据类型为VARCHAR（可变长度字符串），表示文件路径
            .build(); // 构建并返回完整的RelDataType对象，描述了该表有两列的结构
      } // getRowType方法结束，返回表的行类型定义
    }; // 匿名内部类创建结束，返回ScannableTable实例
  } // eval方法结束
} // DuTableFunction类结束
