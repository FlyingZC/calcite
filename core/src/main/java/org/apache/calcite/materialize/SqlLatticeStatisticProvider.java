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
package org.apache.calcite.materialize; // 声明包名，该类属于org.apache.calcite.materialize包，用于物化视图和格（Lattice）相关功能

import org.apache.calcite.DataContexts; // 导入DataContexts工具类，用于创建数据上下文，提供执行SQL所需的环境信息
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表，支持逐行扫描数据
import org.apache.calcite.schema.Table; // 导入Table接口，Calcite中表的抽象表示
import org.apache.calcite.schema.impl.MaterializedViewTable; // 导入物化视图表实现类，用于物化视图相关操作
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合工具类，用于高效表示列索引集合

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，确保线程安全和不可变性
import com.google.common.collect.Iterables; // 导入Google Guava的Iterables工具类，提供集合操作的便捷方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于静态类型检查标记可能为null的值

import java.util.ArrayList; // 导入ArrayList动态数组类，用于存储可变大小的列表
import java.util.List; // 导入List接口，Java集合框架的基础列表接口

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * LatticeStatisticProvider接口的实现类，通过执行"SELECT COUNT(DISTINCT ...) ..." SQL查询来获取统计信息
 * 
 * 类作用说明：
 * 1. 这个类是LatticeStatisticProvider接口的具体实现，专门用于获取格（Lattice）的统计信息
 * 2. 格（Lattice）是Calcite中用于物化视图优化的数据结构，表示一个多维数据立方体
 * 3. 该类通过执行实际的SQL查询（主要是COUNT DISTINCT查询）来获取列的基数（cardinality）等统计信息
 * 4. 基数是指列中不同值的数量，是优化器选择物化视图策略的重要依据
 * 5. 提供了两种工厂模式：直接创建和缓存创建，后者可以缓存统计信息避免重复查询
 * 
 * 工作原理：
 * - 当需要获取某列的基数时，会构造一个COUNT DISTINCT的SQL查询
 * - 通过MaterializationService创建临时表来执行这个SQL
 * - 扫描查询结果并返回基数数值
 * - 支持多列组合的基数计算
 */
class SqlLatticeStatisticProvider implements LatticeStatisticProvider { // 定义SqlLatticeStatisticProvider类，实现LatticeStatisticProvider接口
  static final Factory FACTORY = SqlLatticeStatisticProvider::new; // 静态工厂常量，使用方法引用创建SqlLatticeStatisticProvider实例，Factory是函数式接口，接受Lattice参数返回LatticeStatisticProvider

  static final Factory CACHED_FACTORY = lattice -> { // 静态缓存工厂常量，创建带缓存的统计信息提供器，使用lambda表达式实现Factory接口
    LatticeStatisticProvider provider = FACTORY.apply(lattice); // 首先通过基础工厂创建一个普通的SqlLatticeStatisticProvider实例
    return new CachingLatticeStatisticProvider(lattice, provider); // 然后用CachingLatticeStatisticProvider包装这个provider，添加缓存功能，避免重复查询数据库
  }; // lambda表达式结束

  private final Lattice lattice; // 成员变量：存储对Lattice对象的引用，final表示不可变，Lattice代表多维数据格，包含事实表、维度表和统计信息

  /** Creates a SqlLatticeStatisticProvider. */ // Javadoc注释：说明构造方法的作用
  private SqlLatticeStatisticProvider(Lattice lattice) { // 私有构造方法，只能通过工厂方法创建实例，接受Lattice参数
    this.lattice = requireNonNull(lattice, "lattice"); // 使用requireNonNull校验lattice参数不为null，如果为null则抛出NullPointerException，错误信息为"lattice"
  } // 构造方法结束

  @Override public double cardinality(List<Lattice.Column> columns) { // 重写接口方法，计算一组列的基数（cardinality），返回double类型值，参数是Lattice.Column对象列表
    final List<Double> counts = new ArrayList<>(); // 创建一个Double类型的动态数组，用于存储每个列的基数值
    for (Lattice.Column column : columns) { // 遍历输入的列列表，对每一列进行处理
      counts.add(cardinality(lattice, column)); // 调用静态方法cardinality计算当前列的基数，并将结果添加到counts列表中
    } // for循环结束
    return (int) Lattice.getRowCount(lattice.getFactRowCount(), counts); // 调用Lattice的静态方法getRowCount，根据事实表的总行数和各列基数计算最终的基数并返回，强制转换为int类型
  } // cardinality方法结束

  private static double cardinality(Lattice lattice, Lattice.Column column) { // 私有静态方法，计算单个列的基数，接受Lattice和Lattice.Column参数，返回double类型的基数值
    final String sql = lattice.countSql(ImmutableBitSet.of(column.ordinal)); // 调用lattice的countSql方法生成COUNT DISTINCT SQL语句，ImmutableBitSet.of(column.ordinal)创建包含当前列序号的位集合
    final Table table = // 声明Table变量，用于存储执行SQL后创建的临时表
        new MaterializationService.DefaultTableFactory() // 创建MaterializationService的默认表工厂实例
            .createTable(lattice.rootSchema, sql, ImmutableList.of()); // 调用createTable方法在lattice的根模式下执行SQL并创建表，ImmutableList.of()表示空参数列表
    final @Nullable Object[] values = // 声明可能为null的对象数组，用于存储查询结果
        Iterables.getOnlyElement( // 调用Iterables.getOnlyElement方法获取迭代器中的唯一元素，如果结果不唯一会抛出异常
            ((ScannableTable) table).scan( // 将table强制转换为ScannableTable并调用scan方法执行扫描，返回可枚举的结果集
                DataContexts.of(MaterializedViewTable.MATERIALIZATION_CONNECTION, // 创建DataContext对象，传入物化视图连接配置
                    lattice.rootSchema.plus()))); // 添加lattice的根模式到数据上下文中，plus()返回包含根模式的SchemaPlus对象
    Number value = (Number) values[0]; // 从结果数组中获取第一个元素（即COUNT查询的结果值），强制转换为Number类型
    requireNonNull(value, () -> "count(*) produced null in " + sql); // 校验value不为null，如果为null则抛出异常，错误信息包含SQL语句
    return value.doubleValue(); // 将Number类型转换为double类型并返回，得到列的基数
  } // cardinality静态方法结束
} // SqlLatticeStatisticProvider类结束
