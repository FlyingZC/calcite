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
package org.apache.calcite.adapter.os; // 声明该类所属的包，位于org.apache.calcite.adapter.os包下，这是Calcite中用于操作系统适配器的包

import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置类，用于获取Calcite连接时的配置信息
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示该表可以被扫描以获取数据
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示数据库模式
import org.apache.calcite.schema.Statistic; // 导入统计信息接口，用于提供表的统计信息
import org.apache.calcite.schema.Statistics; // 导入统计信息工具类，用于创建统计信息对象
import org.apache.calcite.sql.SqlCall; // 导入SQL调用类，表示SQL函数或操作符的调用
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，表示SQL语法树的节点
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合类，用于表示列索引的集合

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数

/**这是一个抽象基类，用于实现操作系统表函数的基类，OS表函数是指那些可以查询操作系统信息的表函数（如查询进程、文件系统等）
 * Abstract base class for implementations of OS table functions.
 */
abstract class AbstractBaseScannableTable implements ScannableTable { // 定义抽象类，实现了ScannableTable接口，表示这是一个可以被扫描的表
  protected AbstractBaseScannableTable() { // 受保护的构造方法，限制只能由子类调用，防止外部直接实例化这个抽象类
  } // 构造方法体为空，不需要任何初始化操作

  @Override public Statistic getStatistic() { // 重写ScannableTable接口的getStatistic方法，返回表的统计信息
    return Statistics.of(1000d, ImmutableList.of(ImmutableBitSet.of(1))); // 返回统计信息对象，包含行数估计为1000行，以及列1的位集合（表示第1列可能用于索引）
  } // 统计信息用于查询优化器选择最优的执行计划

  @Override public Schema.TableType getJdbcTableType() { // 重写getJdbcTableType方法，返回表的JDBC类型
    return Schema.TableType.TABLE; // 返回表类型为TABLE，表示这是一个普通的表（不是视图或系统表）
  } // JDBC表类型用于区分不同类型的表，如TABLE、VIEW、SYSTEM TABLE等

  @Override public boolean isRolledUp(String column) { // 重写isRolledUp方法，判断指定列是否是汇总列（即是否是聚合计算的结果）
    return false; // 返回false，表示该表的所有列都不是汇总列，都是原始数据列
  } // 汇总列通常用于物化视图，这里表示该表不包含任何汇总列

  @Override public boolean rolledUpColumnValidInsideAgg(String column, SqlCall call, // 重写rolledUpColumnValidInsideAgg方法，判断汇总列在聚合函数中是否有效
      @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 参数：column-列名，call-SQL调用节点，parent-父节点（可能为null），config-连接配置（可能为null）
    return true; // 返回true，表示汇总列在聚合函数中是有效的，可以安全使用
  } // 这个方法用于查询重写时判断是否可以将聚合操作下推到汇总列上
}
