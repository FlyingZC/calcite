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
 */ // Apache许可证声明，指定代码的使用权限和限制
package org.apache.calcite.adapter.os; // 定义包名，表示该类属于Calcite框架的操作系统适配器包

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文接口，用于在查询执行时传递运行时信息
import org.apache.calcite.linq4j.Enumerable; // 导入LINQ4J的可枚举接口，用于提供类似LINQ的查询操作
import org.apache.calcite.rel.type.RelDataType; // 导入Calcite的关系数据类型接口，表示表的结构信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入Calcite的关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.ScannableTable; // 导入Calcite的可扫描表接口，表示可以被扫描的数据表
import org.apache.calcite.sql.type.SqlTypeName; // 导入Calcite的SQL类型名称枚举，定义SQL标准数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的可空注解，用于标记可能为null的类型

/**
 * Table function that executes the OS "jps" ("Java Virtual Machine Process
 * Status Tool") command to list all java processes of a user.
 */ // 类文档注释：说明这是一个表函数，用于执行操作系统的"jps"（Java虚拟机进程状态工具）命令来列出用户的所有Java进程
public class JpsTableFunction { // 定义JpsTableFunction类，这是一个表函数类，用于将操作系统的jps命令结果转换为Calcite可查询的表
  private JpsTableFunction() { // 私有构造方法，防止实例化，因为该类只提供静态方法
  } // 构造方法结束，空实现

  public static ScannableTable eval(boolean b) { // 静态方法eval，接受一个布尔参数（未使用），返回一个ScannableTable对象，用于创建可扫描的表
    return new AbstractBaseScannableTable() { // 返回一个匿名内部类实例，继承自AbstractBaseScannableTable，实现可扫描表的抽象功能
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，扫描数据并返回可枚举的对象数组，root参数提供数据上下文
        return Processes.processLines("jps", "-mlvV") // 调用Processes工具类执行jps命令，参数-mlvV表示显示完整的应用程序主类名、JVM参数和main方法的参数
            .select(a0 -> { // 使用LINQ的select方法对每一行输出进行转换处理
              final String[] fields = a0.split(" "); // 将jps命令的输出行按空格分割成字段数组
              return new Object[]{Long.valueOf(fields[0]), fields[1]}; // 将分割后的字段转换为对象数组，第一个字段转换为Long类型的进程ID，第二个字段保持为字符串类型的进程信息
            }); // select lambda表达式结束，返回转换后的对象数组
      } // scan方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型结构，typeFactory参数用于创建数据类型
        return typeFactory.builder() // 使用类型工厂创建构建器
            .add("pid", SqlTypeName.BIGINT) // 添加第一列，列名为"pid"，类型为BIGINT（大整数），用于存储Java进程的进程ID
            .add("info", SqlTypeName.VARCHAR) // 添加第二列，列名为"info"，类型为VARCHAR（可变长字符串），用于存储Java进程的详细信息（类名和参数）
            .build(); // 构建并返回关系数据类型对象
      } // getRowType方法结束
    }; // 匿名内部类实例化结束
  } // eval方法结束
} // JpsTableFunction类结束
