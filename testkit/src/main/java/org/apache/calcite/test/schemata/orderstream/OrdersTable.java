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
package org.apache.calcite.test.schemata.orderstream; // 包声明：订单流测试模式包，包含用于测试订单流相关的表实现类

import org.apache.calcite.DataContext; // 导入DataContext：Calcite的数据上下文接口，提供执行环境信息
import org.apache.calcite.config.CalciteConnectionConfig; // 导入CalciteConnectionConfig：Calcite连接配置接口，提供连接级别的配置信息
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable：LINQ4j的可枚举接口，表示可查询的数据集合
import org.apache.calcite.linq4j.Linq4j; // 导入LINQ4j：LINQ4j工具类，提供静态方法用于创建和操作可枚举集合
import org.apache.calcite.schema.StreamableTable; // 导入StreamableTable：可流式表接口，表示可以持续产生数据的流表
import org.apache.calcite.schema.Table; // 导入Table：Calcite表接口，定义表的基本行为
import org.apache.calcite.sql.SqlCall; // 导入SqlCall：SQL调用节点，表示SQL中的函数调用或操作符调用
import org.apache.calcite.sql.SqlNode; // 导入SqlNode：SQL节点基类，所有SQL语法树的节点都继承自此类

import com.google.common.collect.ImmutableList; // 导入ImmutableList：Google Guava提供的不可变列表实现，保证线程安全和数据不可变性

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable：Checker Framework注解，表示值可能为null，用于静态空值检查

/**
 * Table representing the ORDERS stream. // 类文档注释：表示订单流的表类，用于在测试环境中模拟订单流数据源
 * 这个类实现了StreamableTable接口，表示它是一个可以持续产生数据的流表，而不是静态表
 * 它继承自BaseOrderStreamTable，复用了基础的订单流表功能
 * 该类主要用于Calcite的测试场景，提供了一个简单的内存中的订单流数据源
 */
public class OrdersTable extends BaseOrderStreamTable // 类声明：OrdersTable类继承自BaseOrderStreamTable基类，复用基础订单流表功能
    implements StreamableTable { // 实现StreamableTable接口，表明这是一个支持流式查询的表，可以持续产生数据
  private final ImmutableList<Object[]> rows; // 成员变量：存储订单数据的不可变列表，每个元素是一个Object数组，代表一行订单数据，final保证引用不可变，ImmutableList保证内容不可变

  public OrdersTable(ImmutableList<Object[]> rows) { // 构造方法：创建OrdersTable实例，接收订单数据列表作为参数
    this.rows = rows; // 将传入的订单数据列表赋值给成员变量rows，使用this关键字区分成员变量和参数
  }

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 方法重写：scan方法用于扫描表数据并返回可枚举的结果集，@Override注解表示重写父类或接口方法，返回类型是可包含null值的Object数组的可枚举集合
    return Linq4j.asEnumerable(rows); // 使用LINQ4j工具类将rows列表转换为可枚举集合返回，asEnumerable方法将Java集合转换为LINQ4j的Enumerable，方便进行流式查询操作
  }

  @Override public Table stream() { // 方法重写：stream方法返回表的流式版本，用于支持流式查询，@Override注解表示重写StreamableTable接口方法
    return new OrdersTable(rows); // 返回一个新的OrdersTable实例，使用相同的rows数据，这样可以将表从静态模式转换为流式模式，支持持续查询新到达的数据
  }

  @Override public boolean isRolledUp(String column) { // 方法重写：isRolledUp方法检查指定列是否是上卷(rollup)列，上卷列是聚合函数的结果列，@Override注解表示重写Table接口方法
    return false; // 返回false表示所有列都不是上卷列，这个实现不支持上卷操作，因为这是基础测试表实现
  }

  @Override public boolean rolledUpColumnValidInsideAgg(String column, // 方法重写：rolledUpColumnValidInsideAgg方法检查上卷列在聚合函数内部是否有效，@Override注解表示重写Table接口方法
      SqlCall call, @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 参数：column-列名，call-SQL调用节点，parent-父SQL节点，config-连接配置
    return false; // 返回false表示上卷列在聚合函数内部无效，这个实现始终返回false，因为不支持上卷操作
  }
}
