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
package org.apache.calcite.test.schemata.orderstream; // 定义包名，该类位于org.apache.calcite.test.schemata.orderstream包下

import org.apache.calcite.DataContext; // 导入DataContext类，用于提供查询执行时的上下文信息
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ风格的集合操作方法

import com.google.common.collect.ImmutableList; // 导入Guava的不可变列表类，用于存储不可变的对象数组集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值

/** Table representing the history of the ORDERS stream. */ // Javadoc注释：表示ORDERS流的历史记录的表
public class OrdersHistoryTable extends BaseOrderStreamTable { // OrdersHistoryTable类，继承自BaseOrderStreamTable，表示订单流的历史数据表，用于存储和查询订单历史记录
  private final ImmutableList<Object[]> rows; // 成员变量：rows，不可变的对象数组列表，用于存储订单历史记录的每一行数据，每个Object[]代表一行订单记录，包含订单的各种属性（如订单ID、产品ID、数量、时间戳等）

  public OrdersHistoryTable(ImmutableList<Object[]> rows) { // 构造方法：接收一个不可变的对象数组列表作为参数，用于初始化订单历史表
    this.rows = rows; // 将传入的rows参数赋值给实例变量rows，用于存储订单历史数据
  }

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，用于扫描订单历史表并返回可枚举的数据集合，参数root是DataContext对象，提供查询执行时的上下文信息，返回值是可枚举的对象数组，每个数组代表一行订单历史记录，可能为null
    return Linq4j.asEnumerable(rows); // 使用Linq4j工具类的asEnumerable方法将ImmutableList<Object[]>转换为Enumerable<@Nullable Object[]>，使得数据可以使用LINQ风格进行查询和处理
  }
}