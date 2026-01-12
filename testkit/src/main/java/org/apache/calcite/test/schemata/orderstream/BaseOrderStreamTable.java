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
package org.apache.calcite.test.schemata.orderstream;

import org.apache.calcite.config.CalciteConnectionConfig; // Calcite连接配置类，用于获取连接级别的配置信息
import org.apache.calcite.rel.RelCollations; // RelCollations工具类，用于创建和操作排序规则
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口，表示表或表达式的类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂接口，用于创建RelDataType实例
import org.apache.calcite.rel.type.RelProtoDataType; // 关系数据类型原型接口，用于延迟创建数据类型
import org.apache.calcite.schema.ScannableTable; // 可扫描表接口，表示可以逐行扫描数据的表
import org.apache.calcite.schema.Schema; // Schema接口，表示数据库模式或命名空间
import org.apache.calcite.schema.Statistic; // 统计信息接口，提供表的统计信息如行数、排序键等
import org.apache.calcite.schema.Statistics; // Statistics工具类，用于创建Statistic实例
import org.apache.calcite.sql.SqlCall; // SqlCall类，表示SQL函数调用或操作符调用
import org.apache.calcite.sql.SqlNode; // SqlNode接口，表示SQL语法树的节点
import org.apache.calcite.sql.type.SqlTypeName; // SqlTypeName枚举，定义SQL标准的数据类型名称

import com.google.common.collect.ImmutableList; // Google Guava库的不可变列表集合类

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework注解，表示可能为null的值

/**
 * 订单流表的基类。用于管理测试表使用的基础模式和通用功能。
 * 
 * 这个抽象类实现了ScannableTable接口，为订单流相关的测试表提供通用的结构定义。
 * 它定义了订单表的标准行类型（ROWTIME、ID、PRODUCT、UNITS），并提供了表统计信息。
 * 子类需要实现具体的数据扫描逻辑。
 * 
 * 主要功能：
 * 1. 定义订单表的标准行类型结构，包含时间戳、订单ID、产品名称和数量字段
 * 2. 提供表的统计信息，包括预估行数和排序信息
 * 3. 实现ScannableTable接口的基本方法，使表可以被Calcite查询引擎扫描
 * 
 * 使用场景：
 * - 用于Calcite流处理测试，模拟实时订单数据流
 * - 作为其他具体订单流表实现的基础类
 * - 提供一致的表结构定义，便于测试用例的开发
 */
public abstract class BaseOrderStreamTable implements ScannableTable { // 抽象类：订单流表的基类，实现ScannableTable接口以支持数据扫描
  protected final RelProtoDataType protoRowType = a0 -> a0.builder() // 成员变量：行类型原型，使用Lambda表达式定义表的结构，a0是RelDataTypeFactory参数
      .add("ROWTIME", SqlTypeName.TIMESTAMP) // 添加ROWTIME字段，类型为TIMESTAMP（时间戳），表示订单发生的时间
      .add("ID", SqlTypeName.INTEGER) // 添加ID字段，类型为INTEGER（整数），表示订单的唯一标识符
      .add("PRODUCT", SqlTypeName.VARCHAR, 10) // 添加PRODUCT字段，类型为VARCHAR（可变长字符串），最大长度10，表示产品名称
      .add("UNITS", SqlTypeName.INTEGER) // 添加UNITS字段，类型为INTEGER（整数），表示订单的产品数量
      .build(); // 构建并返回RelDataType对象，完成行类型的定义

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写ScannableTable接口方法：获取表的行类型（表结构定义）
    return protoRowType.apply(typeFactory); // 使用传入的类型工厂typeFactory应用protoRowType原型，生成具体的RelDataType对象并返回
  }

  @Override public Statistic getStatistic() { // 重写ScannableTable接口方法：获取表的统计信息，用于查询优化
    return Statistics.of(100d, ImmutableList.of(), // 创建并返回Statistic对象：第一个参数100d表示表的预估行数为100；第二个参数ImmutableList.of()表示表的键集合为空（没有主键或唯一键）
        RelCollations.createSingleton(0)); // 第三个参数表示表的排序规则，createSingleton(0)表示按第0列（ROWTIME字段）排序，单列排序
  }

  @Override public Schema.TableType getJdbcTableType() { // 重写ScannableTable接口方法：获取表的JDBC类型
    return Schema.TableType.TABLE; // 返回TABLE类型，表示这是一个普通表（而非视图、系统表等）
  }

  @Override public boolean isRolledUp(String column) { // 重写ScannableTable接口方法：判断指定列是否是聚合列（roll-up column）
    return false; // 返回false表示所有列都不是聚合列，即所有列都是原始数据列
  }

  @Override public boolean rolledUpColumnValidInsideAgg(String column, // 重写ScannableTable接口方法：判断聚合列是否可以在聚合函数内部使用
      SqlCall call, @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 参数：column为列名；call为SQL调用节点；parent为父SQL节点（可能为null）；config为连接配置（可能为null）
    return false; // 返回false表示聚合列不可以在聚合函数内部使用（由于isRolledUp始终返回false，这个方法实际不会被调用）
  }
} // 类定义结束
