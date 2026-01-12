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
package org.apache.calcite.adapter.os; // 定义包名，该包包含操作系统适配器相关的类

import org.apache.calcite.DataContext; // 导入数据上下文接口，用于在查询执行过程中传递上下文信息
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，用于实现LINQ风格的可枚举集合
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可以被遍历的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合中的元素
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示关系模型中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示可以被扫描的表
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义了SQL中的各种数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

/**
 * Table function that executes the OS "interface_details".
 * 表函数，用于执行操作系统的"interface_details"命令，获取网络接口详细信息
 * 该类提供了一个静态方法eval，返回一个ScannableTable对象，该对象可以扫描并返回网络接口的详细信息
 * 包括设备名称、MAC地址、是否为虚拟接口、MTU、速度、入站/出站包数、入站/出站字节数、入站/出站错误数、入站丢包数和冲突数等
 */
public class InterfaceDetailsTableFunction { // 定义接口详情表函数类，用于获取操作系统网络接口的详细信息
  private InterfaceDetailsTableFunction() { // 私有构造方法，防止实例化，该类只提供静态方法
  } // 构造方法结束，空实现，确保该类不能被实例化

  public static ScannableTable eval(boolean b) { // 静态方法，返回一个可扫描表对象，参数b是布尔值，但实际未使用，保留以兼容表函数签名
    return new AbstractBaseScannableTable() { // 返回一个抽象可扫描表的匿名子类实例，AbstractBaseScannableTable是OS适配器中的基类
      @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，扫描数据并返回可枚举的对象数组集合，root是数据上下文
        return new AbstractEnumerable<Object[]>() { // 返回一个抽象可枚举的匿名子类实例，用于提供枚举器
          @Override public Enumerator<Object[]> enumerator() { // 重写enumerator方法，返回一个枚举器对象用于遍历数据
            return new OsQuery("interface_details"); // 创建并返回OsQuery对象，执行"interface_details"命令获取网络接口详细信息
          } // enumerator方法结束
        }; // AbstractEnumerable匿名类实例化结束
      } // scan方法结束

      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，返回表的行类型结构，typeFactory是类型工厂
        return typeFactory.builder() // 使用类型工厂创建一个构建器，用于构建行类型
            .add("device_name", SqlTypeName.VARCHAR) // 添加设备名称列，类型为VARCHAR字符串类型
            .add("mac", SqlTypeName.VARCHAR) // 添加MAC地址列，类型为VARCHAR字符串类型
            .add("is_virtual", SqlTypeName.VARCHAR) // 添加是否为虚拟接口列，类型为VARCHAR字符串类型
            .add("mtu", SqlTypeName.BIGINT) // 添加最大传输单元列，类型为BIGINT长整型
            .add("speed", SqlTypeName.BIGINT) // 添加网络速度列，类型为BIGINT长整型
            .add("i_packets", SqlTypeName.BIGINT) // 添加入站包数列，类型为BIGINT长整型
            .add("o_packets", SqlTypeName.BIGINT) // 添加出站包数列，类型为BIGINT长整型
            .add("i_bytes", SqlTypeName.BIGINT) // 添加入站字节数列，类型为BIGINT长整型
            .add("o_bytes", SqlTypeName.BIGINT) // 添加出站字节数列，类型为BIGINT长整型
            .add("i_errors", SqlTypeName.BIGINT) // 添加入站错误数列，类型为BIGINT长整型
            .add("o_errors", SqlTypeName.BIGINT) // 添加出站错误数列，类型为BIGINT长整型
            .add("i_drops", SqlTypeName.BIGINT) // 添加入站丢包数列，类型为BIGINT长整型
            .add("collisions", SqlTypeName.BIGINT) // 添加冲突数列，类型为BIGINT长整型
            .build(); // 构建并返回行类型对象
      } // getRowType方法结束
    }; // AbstractBaseScannableTable匿名类实例化结束
  } // eval方法结束
}
