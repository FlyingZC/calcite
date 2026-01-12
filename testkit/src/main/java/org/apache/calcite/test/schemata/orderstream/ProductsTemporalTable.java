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
package org.apache.calcite.test.schemata.orderstream; // 声明该类所在的包路径，属于org.apache.calcite.test.schemata.orderstream包

import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置类，用于提供Calcite连接时的配置信息
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系数据类型原型接口，用于延迟创建数据类型
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示Calcite中的模式(数据库schema)
import org.apache.calcite.schema.Statistic; // 导入统计信息接口，用于提供表的统计信息
import org.apache.calcite.schema.Statistics; // 导入统计信息工具类，用于创建统计信息对象
import org.apache.calcite.schema.TemporalTable; // 导入时态表接口，该类实现此接口以支持时态表功能
import org.apache.calcite.sql.SqlCall; // 导入SQL调用接口，表示SQL函数调用或操作符调用
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，表示SQL语法树中的节点
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义SQL标准数据类型

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可修改的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数

/**
 * Table representing the PRODUCTS_TEMPORAL temporal table. // 类注释：表示PRODUCTS_TEMPORAL时态表的表实现类
 * 
 * 这个类实现了TemporalTable接口，用于在Calcite中表示一个时态表(Temporal Table)。
 * 时态表是一种特殊的表，它记录了数据的历史版本，通过SYS_START和SYS_END字段来追踪每条记录的有效时间范围。
 * 这个类主要用于测试和演示Calcite的时态表功能，模拟一个产品表，包含产品ID、供应商ID以及系统开始时间和结束时间。
 * 
 * 核心功能：
 * 1. 定义时态表的行结构(Row Type)，包含ID、SUPPLIER、SYS_START、SYS_END四个字段
 * 2. 提供时态表的系统时间字段名称(SYS_START和SYS_END)
 * 3. 提供表的统计信息(行数和排序键)
 * 4. 实现Schema.TableType接口方法，表明这是一个普通表
 * 5. 实现rollup相关方法，虽然此表不支持rollup操作
 * 
 * 时态表在SQL中的应用：
 * - 支持FOR SYSTEM_TIME AS OF语法，可以查询某个时间点的数据状态
 * - 支持时态连接(Temporal Join)，可以将事实表与维度表的历史版本进行连接
 * - 自动根据查询时间过滤有效版本的数据
 */
public class ProductsTemporalTable implements TemporalTable { // 定义ProductsTemporalTable类，实现TemporalTable接口，表示一个时态表

  private final RelProtoDataType protoRowType = a0 -> a0.builder() // 定义行类型原型成员变量，使用lambda表达式创建RelProtoDataType，参数a0是RelDataTypeFactory，用于延迟创建行类型
      .add("ID", SqlTypeName.VARCHAR, 32) // 添加ID字段，类型为VARCHAR，长度为32，表示产品的唯一标识符
      .add("SUPPLIER", SqlTypeName.INTEGER) // 添加SUPPLIER字段，类型为INTEGER，表示供应商的ID
      .add("SYS_START", SqlTypeName.TIMESTAMP) // 添加SYS_START字段，类型为TIMESTAMP，表示记录生效的开始时间，用于时态查询
      .add("SYS_END", SqlTypeName.TIMESTAMP) // 添加SYS_END字段，类型为TIMESTAMP，表示记录失效的结束时间，用于时态查询
      .build(); // 构建并返回RelDataType对象，完成行类型的定义

  @Override public String getSysStartFieldName() { // 重写TemporalTable接口方法，获取系统开始时间字段的名称
    return "SYS_START"; // 返回系统开始时间字段名称"SYS_START"，Calcite在进行时态查询时会使用此字段名
  } // 方法结束

  @Override public String getSysEndFieldName() { // 重写TemporalTable接口方法，获取系统结束时间字段的名称
    return "SYS_END"; // 返回系统结束时间字段名称"SYS_END"，Calcite在进行时态查询时会使用此字段名
  } // 方法结束

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写Table接口方法，获取表的行类型(Row Type)，即表的结构定义
    return protoRowType.apply(typeFactory); // 使用传入的类型工厂typeFactory应用protoRowType原型，创建并返回实际的RelDataType对象
  } // 方法结束

  @Override public Statistic getStatistic() { // 重写Table接口方法，获取表的统计信息，用于查询优化器进行成本估算
    return Statistics.of(200d, ImmutableList.of()); // 返回统计信息对象，表中有200行数据，没有排序键(ImmutableList.of()表示空列表)
  } // 方法结束

  @Override public Schema.TableType getJdbcTableType() { // 重写Table接口方法，获取表的JDBC类型
    return Schema.TableType.TABLE; // 返回TABLE类型，表明这是一个普通表(区别于VIEW、SYSTEM TABLE等)
  } // 方法结束

  @Override public boolean isRolledUp(String column) { // 重写Table接口方法，判断指定列是否是rollup列(rollup是预聚合的数据列)
    return false; // 返回false，表示此表没有任何rollup列，所有列都是原始数据
  } // 方法结束

  @Override public boolean rolledUpColumnValidInsideAgg(String column, // 重写Table接口方法，判断rollup列是否可以在聚合函数内部使用
      SqlCall call, @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 参数：column-列名，call-SQL调用节点，parent-父节点，config-连接配置
    return false; // 返回false，因为此表没有rollup列，所以此方法返回false
  } // 方法结束
} // 类定义结束
