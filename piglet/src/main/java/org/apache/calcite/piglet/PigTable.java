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
package org.apache.calcite.piglet; // 声明包名，表示该类属于 org.apache.calcite.piglet 包，piglet 是 Calcite 框架中用于支持 Apache Pig 语言的子模块

import org.apache.calcite.DataContext; // 导入 DataContext 类，用于在查询执行过程中传递上下文信息，如会话参数、用户定义的变量等
import org.apache.calcite.linq4j.Enumerable; // 导入 Enumerable 接口，表示可枚举的数据集合，是 LINQ4J 框架的核心接口，用于支持类似 LINQ 的查询操作
import org.apache.calcite.linq4j.tree.Expressions; // 导入 Expressions 类，用于创建表达式树，主要用于生成代码和构建查询表达式
import org.apache.calcite.plan.RelOptSchema; // 导入 RelOptSchema 接口，表示关系优化模式的 schema，包含表、函数等元数据信息
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable 接口，表示关系优化中的表对象，包含表的元数据和统计信息
import org.apache.calcite.prepare.RelOptTableImpl; // 导入 RelOptTableImpl 类，是 RelOptTable 接口的实现类，用于创建表对象
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口，表示关系数据类型，描述表的结构（字段名、字段类型等）
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，用于创建关系数据类型，是类型系统的工厂类
import org.apache.calcite.schema.ScannableTable; // 导入 ScannableTable 接口，表示可扫描的表，即可以直接读取数据的表
import org.apache.calcite.schema.Statistic; // 导入 Statistic 接口，表示表的统计信息，如行数、列的分布等，用于查询优化
import org.apache.calcite.schema.Statistics; // 导入 Statistics 类，用于创建统计信息对象
import org.apache.calcite.schema.impl.AbstractTable; // 导入 AbstractTable 类，是 Table 接口的抽象实现，提供了表的基础功能

import com.google.common.collect.ImmutableList; // 导入 Google Guava 库的 ImmutableList 类，用于创建不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的 Nullable 注解，用于标记可能为 null 的值

import java.util.List; // 导入 Java 标准库的 List 接口，用于表示列表集合

/**
 * A non-queriable table that contains only row type to represent a Pig Table. This table is used
 * for constructing Calcite logical plan from Pig DAG.
 */ // 类的 JavaDoc 注释，说明这是一个不可查询的表，只包含行类型信息，用于表示 Pig 表，该表用于从 Pig DAG（有向无环图）构建 Calcite 逻辑计划
public class PigTable extends AbstractTable implements ScannableTable { // 定义 PigTable 类，继承自 AbstractTable 抽象类并实现 ScannableTable 接口，表示这是一个 Pig 表，具有可扫描的特性
  // Dummy statistics with 10 rows for any table // 注释说明这是一个虚拟的统计信息，假设任何表都有 10 行数据，用于优化器进行成本估算
  private static final Statistic DUMMY_STATISTICS = Statistics.of(10.0, ImmutableList.of()); // 定义静态常量 DUMMY_STATISTICS，使用 Statistics.of() 方法创建统计信息对象，参数 10.0 表示行数，ImmutableList.of() 表示空的列统计信息列表，这是一个虚拟统计信息，因为 PigTable 不实际存储数据
  private final RelDataType rowType; // 定义成员变量 rowType，类型为 RelDataType，用于存储表的行类型信息，即表的结构定义（包含字段名、字段类型等），使用 final 修饰表示该变量在构造后不可改变

  private PigTable(RelDataType rowType) { // 定义私有构造方法，参数为 RelDataType 类型的 rowType，表示表的行类型，私有构造方法确保只能通过工厂方法创建实例
    this.rowType = rowType; // 将传入的 rowType 参数赋值给成员变量 rowType，初始化表的行类型信息
  }

  /**
   * Creates a {@link RelOptTable} for a schema only table.
   *
   * @param schema Catalog object
   * @param rowType Relational schema for the table
   * @param names Names of Pig table
   */ // 方法的 JavaDoc 注释，说明该方法用于创建仅包含 schema（结构）的表的 RelOptTable 对象，参数包括：schema 表示目录对象，rowType 表示表的关系模式，names 表示 Pig 表的名称列表
  public static RelOptTable createRelOptTable(RelOptSchema schema, // 定义公共静态方法 createRelOptTable，用于创建 RelOptTable 对象，这是工厂方法，参数 schema 表示关系优化模式的 schema，包含表的元数据
      RelDataType rowType, List<String> names) { // 参数 rowType 表示表的行类型信息，参数 names 表示表的名称列表（可能包含多级名称，如 ["catalog", "schema", "table"]）
    final PigTable pigTable = new PigTable(rowType); // 创建 PigTable 实例，使用传入的 rowType 参数初始化，final 修饰表示该局部变量不可重新赋值
    return RelOptTableImpl.create(schema, rowType, names, pigTable, // 调用 RelOptTableImpl.create() 静态方法创建 RelOptTable 对象，参数包括：schema、rowType、names、pigTable 实例
        c -> Expressions.constant(Boolean.TRUE)); // 最后一个参数是一个 lambda 表达式，用于创建表达式，返回一个常量 Boolean.TRUE，表示该表总是可访问的，这个表达式用于生成代码时判断表是否可访问
  }

  @Override public RelDataType getRowType(final RelDataTypeFactory typeFactory) { // 重写父类 AbstractTable 的 getRowType() 方法，用于获取表的行类型，参数 typeFactory 是关系数据类型工厂，final 修饰表示该参数在方法内不可改变
    return rowType; // 返回成员变量 rowType，即表的行类型信息，注意这里没有使用 typeFactory 参数，因为 rowType 已经在构造时确定
  }

  @Override public Statistic getStatistic() { // 重写接口 ScannableTable 的 getStatistic() 方法，用于获取表的统计信息
    return DUMMY_STATISTICS; // 返回静态常量 DUMMY_STATISTICS，即虚拟的统计信息（10 行数据），因为 PigTable 不实际存储数据，所以使用虚拟统计信息
  }

  @Override public Enumerable<@Nullable Object[]> scan(final DataContext root) { // 重写接口 ScannableTable 的 scan() 方法，用于扫描表数据并返回可枚举的数据集合，参数 root 是数据上下文对象，final 修饰表示该参数在方法内不可改变，返回类型是可空对象数组的可枚举集合
    return null; // 返回 null，表示 PigTable 不支持实际的数据扫描，因为 PigTable 只用于表示 Pig 表的结构，不包含实际数据，实际的数据扫描由 Pig 引擎完成
  }
} // 类定义结束
