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
package org.apache.calcite.test.schemata.orderstream; // 包声明：该类属于org.apache.calcite.test.schemata.orderstream包，专门用于测试流式订单数据

import org.apache.calcite.avatica.util.DateTimeUtils; // 导入DateTimeUtils工具类，用于处理日期时间相关的操作
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示Calcite中的关系数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，代表Calcite中的模式（Schema），可以包含多个表
import org.apache.calcite.schema.Table; // 导入Table接口，代表Calcite中的表，可以是普通表或流表
import org.apache.calcite.schema.TableFactory; // 导入TableFactory接口，用于创建表的工厂接口

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变的列表集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记参数可以为null

import java.util.Map; // 导入Map接口，用于存储键值对集合

/** Mock table that returns a stream of orders from a fixed array. */ // 类文档注释：这是一个模拟表工厂类，用于从固定数组中返回订单流数据，主要用于测试场景
@SuppressWarnings("UnusedDeclaration") // 抑制未使用声明的警告，因为该类可能通过反射被调用，编译器无法静态检测
public class OrdersStreamTableFactory implements TableFactory<Table> { // 类定义：OrdersStreamTableFactory实现了TableFactory<Table>接口，是一个专门用于创建订单流表的工厂类
  // public constructor, per factory contract // 注释说明：按照工厂接口的契约要求，必须提供公共的无参构造函数
  public OrdersStreamTableFactory() { // 公共无参构造函数：用于创建OrdersStreamTableFactory实例，工厂类通常通过反射调用此构造函数
  } // 构造函数体为空，因为该工厂类不需要初始化任何成员变量

  @Override public Table create(SchemaPlus schema, String name, // 方法签名：create方法实现了TableFactory接口，用于创建Table实例，@Override注解表示重写接口方法
      Map<String, Object> operand, @Nullable RelDataType rowType) { // 参数：schema-所属的Schema模式对象；name-表名称；operand-表配置参数的键值对映射；rowType-可空的行类型定义
    return new OrdersTable(getRowList()); // 返回值：创建并返回一个新的OrdersTable实例，传入通过getRowList()方法获取的订单数据行列表
  } // 方法结束：OrdersTable是一个具体实现Table接口的类，用于封装订单流数据

  public static ImmutableList<Object[]> getRowList() { // 方法签名：静态方法getRowList，返回一个不可变的Object数组的列表，每个Object[]代表一行数据
    final Object[][] rows = { // 定义一个二维Object数组rows，用于存储订单数据的所有行，final表示该引用不可重新赋值
        {ts(10, 15, 0), 1, "paint", 10}, // 第一行数据：时间戳(10点15分0秒)、订单ID为1、商品名称为"paint"、数量为10
        {ts(10, 24, 15), 2, "paper", 5}, // 第二行数据：时间戳(10点24分15秒)、订单ID为2、商品名称为"paper"、数量为5
        {ts(10, 24, 45), 3, "brush", 12}, // 第三行数据：时间戳(10点24分45秒)、订单ID为3、商品名称为"brush"、数量为12
        {ts(10, 58, 0), 4, "paint", 3}, // 第四行数据：时间戳(10点58分0秒)、订单ID为4、商品名称为"paint"、数量为3
        {ts(11, 10, 0), 5, "paint", 3} // 第五行数据：时间戳(11点10分0秒)、订单ID为5、商品名称为"paint"、数量为3
    }; // 数组初始化结束：这里定义了5条订单记录，每条记录包含时间戳、订单ID、商品名称和数量四个字段
    return ImmutableList.copyOf(rows); // 返回值：使用Guava的ImmutableList.copyOf方法将二维数组转换为不可变的列表，确保数据不会被外部修改
  } // 方法结束：返回的ImmutableList包含所有订单数据行，这些数据将用于创建OrdersTable

  private static Object ts(int h, int m, int s) { // 方法签名：私有静态辅助方法ts，用于生成特定时间的时间戳对象，参数h-小时、m-分钟、s-秒
    return DateTimeUtils.unixTimestamp(2015, 2, 15, h, m, s); // 返回值：调用DateTimeUtils.unixTimestamp方法，将年月日时分秒转换为Unix时间戳（从1970年1月1日开始的毫秒数）
  } // 方法结束：这里固定日期为2015年2月15日，只允许指定时分秒，用于生成测试数据中的时间戳
} // 类定义结束：OrdersStreamTableFactory类提供了创建订单流表的能力，是Calcite流式处理测试的重要组成部分
