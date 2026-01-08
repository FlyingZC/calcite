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
package org.apache.calcite.profile;  // 定义包名，org.apache.calcite.profile 包包含数据分析和性能分析相关的类

import org.apache.calcite.materialize.Lattice;  // 导入 Lattice 类，用于多维数据立方体相关操作
import org.apache.calcite.util.ImmutableBitSet;  // 导入 ImmutableBitSet 类，用于表示不可变的位集合，常用于表示列索引集合
import org.apache.calcite.util.JsonBuilder;  // 导入 JsonBuilder 类，用于构建 JSON 格式的输出
import org.apache.calcite.util.Util;  // 导入 Util 工具类，提供各种通用工具方法

import com.google.common.collect.ImmutableList;  // 导入 Google Guava 的 ImmutableList，用于创建不可变列表
import com.google.common.collect.ImmutableMap;  // 导入 Google Guava 的 ImmutableMap，用于创建不可变映射
import com.google.common.collect.ImmutableSortedSet;  // 导入 Google Guava 的 ImmutableSortedSet，用于创建不可变有序集合

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入注解，用于标记可能为空的值

import java.math.BigDecimal;  // 导入 BigDecimal 类，用于高精度的十进制运算
import java.math.MathContext;  // 导入 MathContext 类，用于设置 BigDecimal 的精度和舍入模式
import java.math.RoundingMode;  // 导入 RoundingMode 枚举，定义舍入模式
import java.util.ArrayList;  // 导入 ArrayList 类，用于动态数组
import java.util.Collection;  // 导入 Collection 接口，表示集合的根接口
import java.util.List;  // 导入 List 接口，表示有序集合
import java.util.Map;  // 导入 Map 接口，表示键值对映射
import java.util.NavigableSet;  // 导入 NavigableSet 接口，表示可导航的有序集合
import java.util.SortedSet;  // 导入 SortedSet 接口，表示有序集合

import static java.util.Objects.requireNonNull;  // 静态导入 requireNonNull 方法，用于检查对象是否为空

/**
 * Profiler 接口：数据集分析器接口，用于分析数据集的统计信息和特征
 * 
 * 该接口定义了数据集分析的核心功能，包括：
 * 1. 分析数据集的列组合和分布情况
 * 2. 发现数据集中的唯一键（Unique Key）
 * 3. 识别函数依赖（Functional Dependency）
 * 4. 计算列或列组合的基数（Cardinality）
 * 5. 生成数据集的各种统计信息
 * 
 * 这个接口是 Calcite 查询优化器中用于收集统计信息的基础设施，
 * 这些统计信息对于查询优化、成本估算和物化视图选择等场景非常重要。
 * 
 * 主要使用场景：
 * - 查询优化器使用统计信息来估算查询成本
 * - 物化视图管理器使用统计信息来选择合适的物化视图
 * - 数据质量分析工具使用统计信息来发现数据特征
 */
public interface Profiler {  // 定义 Profiler 接口，这是数据集分析器的核心接口
  /** Creates a profile of a data set.  // 创建数据集的分析结果（Profile）
   *
   * @param rows List of rows. Can be iterated over more than once (maybe not  // 参数：行列表，包含数据集的所有行，可以被多次迭代（但可能代价较高）
   *             cheaply)  // 每行是一个 Comparable 对象的列表，表示该行的列值
   * @param columns Column definitions  // 参数：列定义列表，包含数据集中所有列的元数据信息
   *
   * @param initialGroups List of combinations of columns that should be  // 参数：初始列组合集合，包含应该优先分析的列组合
   *                     profiled early, because they may be interesting  // 这些列组合可能比较重要或有意义，需要优先分析
   *
   * @return A profile describing relationships within the data set  // 返回值：Profile 对象，描述数据集内部的各种关系和统计信息
   */
  Profile profile(Iterable<List<Comparable>> rows, List<Column> columns,  // profile 方法定义，接收行数据、列定义和初始列组合，返回分析结果
      Collection<ImmutableBitSet> initialGroups);  // 初始列组合参数，指定优先分析的列组合

  /** Column.  // Column 类：表示数据集中的列，实现了 Comparable 接口以便排序
   * 
   * 该类封装了列的基本信息，包括列的序号（ordinal）和名称（name）。
   * 列序号在数据集中是唯一且连续的，从 0 开始。
   * 
   * 主要用途：
   * - 标识数据集中的列
   - 在统计信息中引用特定的列
   - 作为函数依赖和分布统计的组成部分
   */
  class Column implements Comparable<Column> {  // Column 类定义，实现 Comparable 接口支持按序号比较
    public final int ordinal;  // 成员变量：列的序号（从0开始），在数据集中唯一且连续，用于标识列的位置
    public final String name;  // 成员变量：列的名称，用于显示和引用，可能包含表名等前缀

    /** Creates a Column.  // Column 构造方法：创建一个新的列对象
     *
     * @param ordinal Unique and contiguous within a particular data set  // 参数：列序号，在特定数据集中唯一且连续
     * @param name Name of the column  // 参数：列名称，用于标识和显示该列
     */
    public Column(int ordinal, String name) {  // Column 构造方法，接收序号和名称参数
      this.ordinal = ordinal;  // 将参数序号赋值给成员变量 ordinal
      this.name = name;  // 将参数名称赋值给成员变量 name
    }  // 构造方法结束

    static ImmutableBitSet toOrdinals(Iterable<Column> columns) {  // 静态方法：将列集合转换为位集合，位集合中每个位对应一个列序号
      final ImmutableBitSet.Builder builder = ImmutableBitSet.builder();  // 创建 ImmutableBitSet 构建器，用于构建不可变位集合
      for (Column column : columns) {  // 遍历列集合中的每一列
        builder.set(column.ordinal);  // 在构建器中设置对应列序号的位为1
      }  // 循环结束
      return builder.build();  // 构建并返回不可变位集合
    }  // toOrdinals 方法结束

    @Override public int hashCode() {  // 重写 hashCode 方法，用于支持哈希表操作
      return ordinal;  // 返回列序号作为哈希码，确保序号相同的列具有相同的哈希码
    }  // hashCode 方法结束

    @Override public boolean equals(@Nullable Object o) {  // 重写 equals 方法，用于比较两个列对象是否相等
      return this == o  // 首先检查是否是同一个对象引用
          || o instanceof Column  // 然后检查对象是否是 Column 类的实例
          && ordinal == ((Column) o).ordinal;  // 最后检查列序号是否相等
    }  // equals 方法结束

    @Override public int compareTo(Column column) {  // 重写 compareTo 方法，实现 Comparable 接口，用于列的排序
      return Integer.compare(ordinal, column.ordinal);  // 比较两个列的序号，返回比较结果
    }  // compareTo 方法结束

    @Override public String toString() {  // 重写 toString 方法，用于将列对象转换为字符串表示
      return name;  // 返回列名称作为字符串表示
    }  // toString 方法结束
  }  // Column 类定义结束

  /** Statistic produced by the profiler.  // Statistic 接口：分析器产生的统计信息的基接口
   * 
   * 所有统计信息（行数、唯一键、函数依赖、分布等）都实现此接口。
   * 该接口提供了一个方法将统计信息转换为 Map 对象，便于序列化为 JSON 格式。
   */
  interface Statistic {  // Statistic 接口定义，所有统计信息的基接口
    Object toMap(JsonBuilder jsonBuilder);  // 方法声明：将统计信息转换为 Map 对象，使用 JsonBuilder 构建
  }  // Statistic 接口结束

  /** Whole data set.  // RowCount 类：表示整个数据集的行数统计信息
   * 
   * 该类记录数据集中的总行数，是最基础的统计信息之一。
   * 行数统计对于查询优化、成本估算和基数计算都非常重要。
   */
  class RowCount implements Statistic {  // RowCount 类定义，实现 Statistic 接口
    final int rowCount;  // 成员变量：数据集的总行数，表示数据集中包含的记录数量

    public RowCount(int rowCount) {  // RowCount 构造方法：创建行数统计对象
      this.rowCount = rowCount;  // 将参数行数赋值给成员变量 rowCount
    }  // RowCount 构造方法结束

    @Override public Object toMap(JsonBuilder jsonBuilder) {  // 重写 toMap 方法，将行数统计转换为 Map 对象
      final Map<String, @Nullable Object> map = jsonBuilder.map();  // 使用 JsonBuilder 创建一个 Map 对象
      map.put("type", "rowCount");  // 将统计类型设置为 "rowCount"，标识这是行数统计
      map.put("rowCount", rowCount);  // 将行数值添加到 Map 中
      return map;  // 返回构建好的 Map 对象
    }  // toMap 方法结束
  }  // RowCount 类定义结束

  /** Unique key.  // Unique 类：表示唯一键统计信息
   * 
   * 该类表示数据集中的一组列，这些列的组合值在整个数据集中是唯一的。
   * 唯一键信息对于查询优化非常重要，可以用于：
   * 1. 识别候选主键
   * 2. 优化 JOIN 操作
   * 3. 消除重复数据
   * 4. 改进查询计划
   */
  class Unique implements Statistic {  // Unique 类定义，实现 Statistic 接口
    final NavigableSet<Column> columns;  // 成员变量：构成唯一键的列集合，使用 NavigableSet 保持有序性

    public Unique(SortedSet<Column> columns) {  // Unique 构造方法：创建唯一键统计对象
      this.columns = ImmutableSortedSet.copyOf(columns);  // 将参数列集合转换为不可变的有序集合并赋值给成员变量
    }  // Unique 构造方法结束

    @Override public Object toMap(JsonBuilder jsonBuilder) {  // 重写 toMap 方法，将唯一键统计转换为 Map 对象
      final Map<String, @Nullable Object> map = jsonBuilder.map();  // 使用 JsonBuilder 创建一个 Map 对象
      map.put("type", "unique");  // 将统计类型设置为 "unique"，标识这是唯一键统计
      map.put("columns", FunctionalDependency.getObjects(jsonBuilder, columns));  // 将构成唯一键的列名称列表添加到 Map 中
      return map;  // 返回构建好的 Map 对象
    }  // toMap 方法结束
  }  // Unique 类定义结束

  /** Functional dependency.  // FunctionalDependency 类：表示函数依赖统计信息
   * 
   * 函数依赖（Functional Dependency, FD）是数据库理论中的概念，表示一组列的值
   * 可以唯一确定另一列的值。形式化表示为：X -> Y，其中 X 是决定列集合，Y 是依赖列。
   * 
   * 例如：如果 (city, state) -> zipcode，表示城市和州的组合可以唯一确定邮编。
   * 
   * 函数依赖对于查询优化非常重要，可以用于：
   * 1. 消除冗余列
   * 2. 优化 JOIN 操作
   * 3. 改进基数估算
   * 4. 发现数据质量问题
   */
  class FunctionalDependency implements Statistic {  // FunctionalDependency 类定义，实现 Statistic 接口
    final NavigableSet<Column> columns;  // 成员变量：决定列集合（X），这些列的值可以唯一确定依赖列的值
    final Column dependentColumn;  // 成员变量：依赖列（Y），其值由决定列集合唯一确定

    FunctionalDependency(SortedSet<Column> columns, Column dependentColumn) {  // FunctionalDependency 构造方法：创建函数依赖统计对象
      this.columns = ImmutableSortedSet.copyOf(columns);  // 将参数决定列集合转换为不可变的有序集合并赋值给成员变量
      this.dependentColumn = dependentColumn;  // 将参数依赖列赋值给成员变量
    }  // FunctionalDependency 构造方法结束

    @Override public Object toMap(JsonBuilder jsonBuilder) {  // 重写 toMap 方法，将函数依赖统计转换为 Map 对象
      final Map<String, @Nullable Object> map = jsonBuilder.map();  // 使用 JsonBuilder 创建一个 Map 对象
      map.put("type", "fd");  // 将统计类型设置为 "fd"（Functional Dependency），标识这是函数依赖统计
      map.put("columns", getObjects(jsonBuilder, columns));  // 将决定列集合的名称列表添加到 Map 中
      map.put("dependentColumn", dependentColumn.name);  // 将依赖列的名称添加到 Map 中
      return map;  // 返回构建好的 Map 对象
    }  // toMap 方法结束

    private static List<@Nullable Object> getObjects(JsonBuilder jsonBuilder,  // 私有静态方法：将列集合转换为列名称列表
        NavigableSet<Column> columns) {  // 参数：列集合，需要转换为名称列表
      final List<@Nullable Object> list = jsonBuilder.list();  // 使用 JsonBuilder 创建一个列表对象
      for (Column column : columns) {  // 遍历列集合中的每一列
        list.add(column.name);  // 将列名称添加到列表中
      }  // 循环结束
      return list;  // 返回列名称列表
    }  // getObjects 方法结束
  }  // FunctionalDependency 类定义结束

  /** Value distribution, including cardinality and optionally values, of a  // Distribution 类：表示值分布统计信息，包括基数和可选的值列表
   * column or set of columns. If the set of columns is empty, it describes  // 描述一个列或一组列的值分布情况。如果列集合为空，则描述整个数据集的行数
   * the number of rows in the entire data set.  // 
   * 
   * 值分布统计包含以下关键信息：
   * 1. 列集合：描述哪些列的分布情况
   * 2. 基数：不同值的数量
   * 3. 值列表：可选，如果值数量较少则记录所有不同的值
   * 4. NULL 值数量：记录 NULL 值的出现次数
   * 5. 期望基数：基于统计模型预期的基数
   * 6. 惊讶度（surprise）：实际基数与期望基数的差异程度
   * 
   * 值分布统计对于查询优化非常重要，可以用于：
   * 1. 选择性估算（Selectivity Estimation）
   * 2. JOIN 成本估算
   * 3. 索引设计建议
   * 4. 数据倾斜检测
   */
  class Distribution implements Statistic {  // Distribution 类定义，实现 Statistic 接口
    static final MathContext ROUND5 =  // 静态常量：精度为 5 的数学上下文，用于 BigDecimal 运算
        new MathContext(5, RoundingMode.HALF_EVEN);  // 使用银行家舍入法（四舍六入五成双）

    static final MathContext ROUND3 =  // 静态常量：精度为 3 的数学上下文，用于 BigDecimal 运算
        new MathContext(3, RoundingMode.HALF_EVEN);  // 使用银行家舍入法（四舍六入五成双）

    final NavigableSet<Column> columns;  // 成员变量：列集合，描述哪些列的分布情况
    final @Nullable NavigableSet<Comparable> values;  // 成员变量：值集合，包含所有不同的值（如果数量较少），否则为 null
    final double cardinality;  // 成员变量：基数，表示不同值的数量（distinct values count）
    final int nullCount;  // 成员变量：NULL 值数量，表示这些列中 NULL 值出现的次数
    final double expectedCardinality;  // 成员变量：期望基数，基于统计模型预期的基数，用于计算惊讶度
    final boolean minimal;  // 成员变量：是否为最小分布，即不被唯一键或函数依赖隐含的分布

    /** Creates a Distribution.  // Distribution 构造方法：创建值分布统计对象
     *
     * @param columns Column or columns being described  // 参数：列集合，描述哪些列的分布情况
     * @param values Values of columns, or null if there are too many  // 参数：值集合，包含所有不同的值，如果值太多则为 null
     * @param cardinality Number of distinct values  // 参数：基数，表示不同值的数量
     * @param nullCount Number of rows where this column had a null value;  // 参数：NULL 值数量，表示这些列中 NULL 值出现的次数
     * @param expectedCardinality Expected cardinality  // 参数：期望基数，基于统计模型预期的基数
     * @param minimal Whether the distribution is not implied by a unique  // 参数：是否为最小分布，即不被唯一键或函数依赖隐含
     *   or functional dependency  // 如果为 true，表示这是一个独立的分布信息；如果为 false，表示可以从其他统计信息推导出来
     */
    public Distribution(SortedSet<Column> columns, @Nullable SortedSet<Comparable> values,  // Distribution 构造方法，接收列集合、值集合、基数等参数
        double cardinality, int nullCount, double expectedCardinality,  // 继续接收 NULL 值数量、期望基数和最小标志参数
        boolean minimal) {  // 继续接收最小标志参数
      this.columns = ImmutableSortedSet.copyOf(columns);  // 将参数列集合转换为不可变的有序集合并赋值给成员变量
      this.values = values == null ? null : ImmutableSortedSet.copyOf(values);  // 如果值集合不为 null，则转换为不可变的有序集合，否则保持 null
      this.cardinality = cardinality;  // 将参数基数赋值给成员变量
      this.nullCount = nullCount;  // 将参数 NULL 值数量赋值给成员变量
      this.expectedCardinality = expectedCardinality;  // 将参数期望基数赋值给成员变量
      this.minimal = minimal;  // 将参数最小标志赋值给成员变量
    }  // Distribution 构造方法结束

    @Override public Object toMap(JsonBuilder jsonBuilder) {  // 重写 toMap 方法，将值分布统计转换为 Map 对象
      final Map<String, @Nullable Object> map = jsonBuilder.map();  // 使用 JsonBuilder 创建一个 Map 对象
      map.put("type", "distribution");  // 将统计类型设置为 "distribution"，标识这是值分布统计
      map.put("columns", FunctionalDependency.getObjects(jsonBuilder, columns));  // 将列名称列表添加到 Map 中
      if (values != null) {  // 如果值集合不为 null（即值数量较少，可以全部记录）
        List<@Nullable Object> list = jsonBuilder.list();  // 使用 JsonBuilder 创建一个列表对象
        for (Comparable value : values) {  // 遍历值集合中的每一个值
          if (value instanceof java.sql.Date) {  // 如果值是 java.sql.Date 类型
            value = value.toString();  // 将日期转换为字符串表示
          }  // 结束类型检查
          list.add(value);  // 将值添加到列表中
        }  // 循环结束
        map.put("values", list);  // 将值列表添加到 Map 中
      }  // 结束条件判断
      map.put("cardinality", new BigDecimal(cardinality, ROUND5));  // 将基数转换为精度为 5 的 BigDecimal 并添加到 Map 中
      if (nullCount > 0) {  // 如果 NULL 值数量大于 0
        map.put("nullCount", nullCount);  // 将 NULL 值数量添加到 Map 中
      }  // 结束条件判断
      map.put("expectedCardinality",  // 将期望基数转换为精度为 5 的 BigDecimal 并添加到 Map 中
          new BigDecimal(expectedCardinality, ROUND5));  // 使用精度为 5 的数学上下文
      map.put("surprise", new BigDecimal(surprise(), ROUND3));  // 将惊讶度转换为精度为 3 的 BigDecimal 并添加到 Map 中
      return map;  // 返回构建好的 Map 对象
    }  // toMap 方法结束

    ImmutableBitSet columnOrdinals() {  // 方法：将列集合转换为位集合（位集合中每个位对应一个列序号）
      return Column.toOrdinals(columns);  // 调用 Column 类的静态方法 toOrdinals 进行转换
    }  // columnOrdinals 方法结束

    double surprise() {  // 方法：计算惊讶度（surprise），表示实际基数与期望基数的差异程度
      return SimpleProfiler.surprise(expectedCardinality, cardinality);  // 调用 SimpleProfiler 的静态方法计算惊讶度
    }  // surprise 方法结束
  }  // Distribution 类定义结束

  /** The result of profiling, contains various statistics about the  // Profile 类：数据集分析的结果类，包含表数据的各种统计信息
   * data in a table.  // 
   * 
   * Profile 类是数据集分析的完整结果，包含以下统计信息：
   * 1. 行数统计（RowCount）：数据集的总行数
   * 2. 函数依赖列表（FunctionalDependency）：所有发现的函数依赖关系
   * 3. 分布统计列表（Distribution）：所有列或列组合的值分布信息
   * 4. 唯一键列表（Unique）：所有发现的唯一键
   * 
   * 该类还提供了以下功能：
   * 1. 快速查找特定列组合的分布统计
   * 2. 计算任意列组合的基数（cardinality）
   * 3. 将所有统计信息转换为统一的格式
   * 
   * 主要用途：
   * - 查询优化器使用这些统计信息来估算查询成本
   * - 物化视图管理器使用这些统计信息来选择合适的物化视图
   * - 数据质量分析工具使用这些统计信息来发现数据特征
   */
  class Profile {  // Profile 类定义，表示数据集分析的完整结果
    public final RowCount rowCount;  // 成员变量：行数统计，记录数据集的总行数
    public final List<FunctionalDependency> functionalDependencyList;  // 成员变量：函数依赖列表，包含所有发现的函数依赖关系
    public final List<Distribution> distributionList;  // 成员变量：分布统计列表，包含所有列或列组合的值分布信息
    public final List<Unique> uniqueList;  // 成员变量：唯一键列表，包含所有发现的唯一键

    private final Map<ImmutableBitSet, Distribution> distributionMap;  // 私有成员变量：分布统计映射，以列序号的位集合为键，快速查找分布统计
    private final List<Distribution> singletonDistributionList;  // 私有成员变量：单列分布统计列表，按列序号顺序存储每个单独列的分布统计

    Profile(List<Column> columns, RowCount rowCount,  // Profile 构造方法：创建数据集分析结果对象
        Iterable<FunctionalDependency> functionalDependencyList,  // 参数：函数依赖列表，包含所有发现的函数依赖关系
        Iterable<Distribution> distributionList, Iterable<Unique> uniqueList) {  // 参数：分布统计列表和唯一键列表
      this.rowCount = rowCount;  // 将参数行数统计赋值给成员变量
      this.functionalDependencyList =  // 将参数函数依赖列表转换为不可变列表并赋值给成员变量
          ImmutableList.copyOf(functionalDependencyList);  // 使用 Guava 的 ImmutableList.copyOf 方法
      this.distributionList = ImmutableList.copyOf(distributionList);  // 将参数分布统计列表转换为不可变列表并赋值给成员变量
      this.uniqueList = ImmutableList.copyOf(uniqueList);  // 将参数唯一键列表转换为不可变列表并赋值给成员变量

      final ImmutableMap.Builder<ImmutableBitSet, Distribution> m =  // 创建不可变映射构建器，用于构建分布统计映射
          ImmutableMap.builder();  // 使用 Guava 的 ImmutableMap.builder 方法
      for (Distribution distribution : distributionList) {  // 遍历分布统计列表中的每一个分布统计
        m.put(distribution.columnOrdinals(), distribution);  // 将分布统计的列序号位集合作为键，分布统计本身作为值添加到映射中
      }  // 循环结束
      distributionMap = m.build();  // 构建不可变映射并赋值给成员变量 distributionMap

      final ImmutableList.Builder<Distribution> b = ImmutableList.builder();  // 创建不可变列表构建器，用于构建单列分布统计列表
      for (int i = 0; i < columns.size(); i++) {  // 遍历所有列，按列序号顺序
        int key = i;  // 保存当前列序号作为键，用于错误消息
        b.add(  // 从分布映射中获取当前列的分布统计并添加到列表构建器中
            requireNonNull(distributionMap.get(ImmutableBitSet.of(i)),  // 获取列序号为 i 的分布统计，如果不存在则抛出异常
                () -> "distributionMap.get(ImmutableBitSet.of(i)) for " + key));  // 错误消息，显示缺失的列序号
      }  // 循环结束
      singletonDistributionList = b.build();  // 构建不可变列表并赋值给成员变量 singletonDistributionList
    }  // Profile 构造方法结束

    public List<Statistic> statistics() {  // 方法：获取所有统计信息的列表，按顺序包含行数、函数依赖、分布统计和唯一键
      return ImmutableList.<Statistic>builder()  // 创建不可变列表构建器，泛型类型为 Statistic
          .add(rowCount)  // 添加行数统计到列表中
          .addAll(functionalDependencyList)  // 添加所有函数依赖到列表中
          .addAll(distributionList)  // 添加所有分布统计到列表中
          .addAll(uniqueList)  // 添加所有唯一键到列表中
          .build();  // 构建不可变列表并返回
    }  // statistics 方法结束

    public double cardinality(ImmutableBitSet columnOrdinals) {  // 方法：计算指定列组合的基数（不同值的数量）
      final ImmutableBitSet originalOrdinals = columnOrdinals;  // 保存原始的列序号位集合，用于后续比较
      for (;;) {  // 无限循环，直到找到匹配的分布统计或无法继续
        final Distribution distribution = distributionMap.get(columnOrdinals);  // 从分布映射中获取当前列序号位集合对应的分布统计
        if (distribution != null) {  // 如果找到了匹配的分布统计
          if (columnOrdinals == originalOrdinals) {  // 如果当前列序号位集合就是原始请求的列序号位集合
            return distribution.cardinality;  // 直接返回该分布统计的基数
          } else {  // 如果当前列序号位集合是原始请求的子集（通过逐步清除位得到）
            final List<Double> cardinalityList = new ArrayList<>();  // 创建基数列表，用于存储各个部分的基数
            cardinalityList.add(distribution.cardinality);  // 将找到的分布统计的基数添加到列表中
            for (int ordinal : originalOrdinals.except(columnOrdinals)) {  // 遍历原始列序号位集合中不在当前列序号位集合中的列序号
              final Distribution d = singletonDistributionList.get(ordinal);  // 从单列分布统计列表中获取该列的分布统计
              cardinalityList.add(d.cardinality);  // 将该列的基数添加到基数列表中
            }  // 循环结束
            return Lattice.getRowCount(rowCount.rowCount, cardinalityList);  // 使用 Lattice 类的方法计算组合基数并返回
          }  // 结束条件判断
        }  // 结束条件判断
        // Clear the last bit and iterate.  // 注释：清除最后一个位并继续迭代
        // Better would be to combine all of our nearest ancestors.  // 注释：更好的方法是组合所有最近的祖先
        final List<Integer> list = columnOrdinals.asList();  // 将当前列序号位集合转换为列表
        columnOrdinals = columnOrdinals.clear(Util.last(list));  // 清除最后一个位，尝试查找父级分布统计
      }  // 无限循环结束
    }  // cardinality 方法结束
  }  // Profile 类定义结束
}  // Profiler 接口定义结束
