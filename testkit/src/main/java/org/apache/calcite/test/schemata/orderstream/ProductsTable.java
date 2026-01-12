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
package org.apache.calcite.test.schemata.orderstream; // 声明该类属于org.apache.calcite.test.schemata.orderstream包，用于测试场景中的订单流模式

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文接口，用于在执行查询时传递运行时上下文信息
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置类，提供连接级别的配置参数
import org.apache.calcite.linq4j.Enumerable; // 导入LINQ4j的可枚举接口，支持延迟查询和函数式操作
import org.apache.calcite.linq4j.Linq4j; // 导入LINQ4j工具类，提供将集合转换为可枚举对象的静态方法
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，描述表或表达式的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系数据类型原型接口，用于延迟创建数据类型
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示可以直接扫描数据的表
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示数据库模式或命名空间
import org.apache.calcite.schema.Statistic; // 导入统计信息接口，提供表的统计元数据
import org.apache.calcite.schema.Statistics; // 导入统计工具类，用于创建统计信息对象
import org.apache.calcite.sql.SqlCall; // 导入SQL调用接口，表示SQL中的函数调用或操作符调用
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，表示抽象语法树中的节点
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL数据类型

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，提供线程安全的不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的参数或返回值

/**
 * Table representing the PRODUCTS relation. // 表示PRODUCTS关系的表类，用于在测试中模拟产品表数据
 * 该类实现了ScannableTable接口，表示这是一个可以直接扫描数据的内存表，用于Calcite测试框架中的订单流场景
 * PRODUCTS表通常包含产品ID和供应商ID等信息，用于演示Calcite对表数据的查询和处理能力
 */
public class ProductsTable implements ScannableTable { // 定义ProductsTable类，实现ScannableTable接口，表示可扫描的产品表
  private final ImmutableList<Object[]> rows; // 成员变量：存储表的行数据，使用不可变列表确保线程安全，每行是一个Object数组

  public ProductsTable(ImmutableList<Object[]> rows) { // 构造方法：接收行数据列表并初始化表
    this.rows = rows; // 将传入的行数据赋值给成员变量rows
  }

  private final RelProtoDataType protoRowType = a0 -> a0.builder() // 成员变量：定义行数据类型原型，使用lambda表达式创建RelProtoDataType，a0是RelDataTypeFactory参数
      .add("ID", SqlTypeName.VARCHAR, 32) // 添加ID列，类型为VARCHAR，长度为32字符，用于存储产品标识符
      .add("SUPPLIER", SqlTypeName.INTEGER) // 添加SUPPLIER列，类型为INTEGER，用于存储供应商ID
      .build(); // 构建并返回RelDataType对象，完成行类型的定义

  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) { // 重写scan方法，用于扫描表数据并返回可枚举的结果集
    return Linq4j.asEnumerable(rows); // 使用Linq4j将行数据列表转换为可枚举对象，支持延迟查询和流式处理
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，返回表的行数据类型描述
    return protoRowType.apply(typeFactory); // 使用类型工厂应用原型，创建具体的RelDataType对象
  }

  @Override public Statistic getStatistic() { // 重写getStatistic方法，返回表的统计信息用于查询优化
    return Statistics.of(200d, ImmutableList.of()); // 返回统计信息对象，估计行数为200，无索引信息
  }

  @Override public Schema.TableType getJdbcTableType() { // 重写getJdbcTableType方法，返回JDBC表类型
    return Schema.TableType.TABLE; // 返回TABLE类型，表示这是一个普通表而非视图或系统表
  }

  @Override public boolean isRolledUp(String column) { // 重写isRolledUp方法，判断指定列是否是上卷列
    return false; // 返回false，表示该表没有上卷列，所有列都是原始数据列
  }

  @Override public boolean rolledUpColumnValidInsideAgg(String column, // 重写rolledUpColumnValidInsideAgg方法，判断上卷列是否可以在聚合函数中使用
      SqlCall call, @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 参数：列名、SQL调用、父节点、连接配置
    return false; // 返回false，表示没有上卷列可以在聚合中使用
  }
}
