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
// 声明包名，表示此类位于org.apache.calcite.adapter.os包下，属于Calcite框架的操作系统适配器模块
package org.apache.calcite.adapter.os;

// 导入DataContext类，用于在Calcite中传递查询执行时的上下文信息（如会话变量、配置参数等）
import org.apache.calcite.DataContext;
// 导入AbstractEnumerable抽象类，用于实现可枚举的数据集合，是LINQ风格查询的基础
import org.apache.calcite.linq4j.AbstractEnumerable;
// 导入Enumerable接口，表示可以被枚举的数据集合，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Enumerable;
// 导入Enumerator接口，用于遍历数据集合的枚举器，提供类似迭代器的功能
import org.apache.calcite.linq4j.Enumerator;
// 导入RelDataType类，表示关系数据类型，用于描述表或表达式的数据类型结构
import org.apache.calcite.rel.type.RelDataType;
// 导入RelDataTypeFactory接口，用于创建关系数据类型的工厂类
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入ScannableTable接口，表示可以被扫描的表，提供scan方法来获取数据
import org.apache.calcite.schema.ScannableTable;
// 导入SqlTypeName枚举，定义了SQL标准中的所有数据类型名称
import org.apache.calcite.sql.type.SqlTypeName;

// 导入@Nullable注解，用于标记可能为null的值，帮助进行空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Table function that executes the OS "cpu_info".
 * // 表函数类：执行操作系统的"cpu_info"命令，用于获取CPU信息
 * // 该类实现了Calcite的表函数机制，可以将操作系统级别的CPU信息作为虚拟表暴露给SQL查询
 * // 主要功能：通过调用OsQuery来执行系统命令，返回CPU的详细信息（如型号、核心数、制造商等）
 */
public class CpuInfoTableFunction { // 定义CpuInfoTableFunction公共类，作为表函数的入口点
  // 私有构造方法，防止外部实例化此类（工具类模式）
  private CpuInfoTableFunction() { // 私有构造函数，确保该类不能被实例化，只能通过静态方法调用
  } // 构造方法体为空，因为没有需要初始化的成员变量

  // 静态方法eval：评估并返回一个ScannableTable对象，该对象提供CPU信息的扫描功能
  // 参数b：布尔值参数（虽然声明了但未使用，可能是为了保持表函数接口的一致性）
  // 返回值：ScannableTable对象，可以被Calcite查询引擎扫描以获取CPU信息
  public static ScannableTable eval(boolean b) { // 定义静态方法eval，接受一个布尔参数并返回ScannableTable实例
    // 创建并返回匿名内部类，继承自AbstractBaseScannableTable（抽象基类，实现了ScannableTable接口）
    return new AbstractBaseScannableTable() { // 返回一个AbstractBaseScannableTable的匿名子类实例
      // 重写scan方法：执行实际的扫描操作，返回可枚举的数据集合
      // 参数root：DataContext对象，包含查询执行的上下文信息
      // 返回值：包含CPU信息行的可枚举集合，每行是一个Object数组
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，接受DataContext并返回可枚举的Object数组集合
        // 创建并返回AbstractEnumerable的匿名子类实例，用于提供可枚举的数据源
        return new AbstractEnumerable<Object[]>() { // 返回AbstractEnumerable的匿名子类，实现数据的枚举功能
          // 重写enumerator方法：创建并返回实际的枚举器对象，用于遍历数据
          // 返回值：Enumerator<Object[]>对象，用于逐行遍历CPU信息
          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回实际的枚举器
            // 创建并返回OsQuery对象，传入"cpu_info"命令作为参数
            // OsQuery会执行操作系统命令并返回结果作为可枚举的数据流
            return new OsQuery("cpu_info"); // 返回OsQuery枚举器，执行"cpu_info"系统命令并获取结果
          } // enumerator方法结束
        }; // AbstractEnumerable匿名子类结束
      } // scan方法结束

      // 重写getRowType方法：定义该表的行结构（即每行的列名和数据类型）
      // 参数typeFactory：RelDataTypeFactory工厂对象，用于创建和构建数据类型
      // 返回值：RelDataType对象，描述了CPU信息表的行类型结构
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，返回表的行类型定义
        // 使用typeFactory创建构建器，并逐步添加各列的定义
        return typeFactory.builder() // 创建RelDataType构建器
            .add("device_id", SqlTypeName.VARCHAR) // 添加device_id列：设备ID，字符串类型
            .add("model", SqlTypeName.VARCHAR) // 添加model列：CPU型号，字符串类型
            .add("manufacturer", SqlTypeName.VARCHAR) // 添加manufacturer列：制造商名称，字符串类型
            .add("number_of_cores", SqlTypeName.INTEGER) // 添加number_of_cores列：物理核心数，整数类型
            .add("logical_processors", SqlTypeName.INTEGER) // 添加logical_processors列：逻辑处理器数（超线程），整数类型
            .add("address_width", SqlTypeName.INTEGER) // 添加address_width列：地址宽度（32位或64位），整数类型
            .add("max_clock_speed", SqlTypeName.BIGINT) // 添加max_clock_speed列：最大时钟速度（MHz），长整型
            .add("socket_designation", SqlTypeName.INTEGER) // 添加socket_designation列：插槽编号，整数类型
            .add("cpu_load", SqlTypeName.DOUBLE) // 添加cpu_load列：CPU负载百分比，双精度浮点型
            .build(); // 构建并返回完整的RelDataType对象
      } // getRowType方法结束
    }; // AbstractBaseScannableTable匿名子类结束
  } // eval方法结束
} // CpuInfoTableFunction类结束
