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
package org.apache.calcite.profile; // 声明包名，该类属于Apache Calcite项目的profile包，用于数据统计和性能分析

import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合元素提供索引
import org.apache.calcite.materialize.Lattice; // 导入Lattice类，用于多维数据立方体计算
import org.apache.calcite.rel.metadata.NullSentinel; // 导入NullSentinel类，用于表示NULL值的特殊标记
import org.apache.calcite.runtime.FlatLists; // 导入FlatLists类，用于创建扁平化的列表
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，用于表示不可变的位集合
import org.apache.calcite.util.PartiallyOrderedSet; // 导入PartiallyOrderedSet类，用于表示部分有序集合
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法

import com.google.common.collect.ImmutableSortedSet; // 导入Google Guava的不可变有序集合
import com.google.common.collect.Iterables; // 导入Google Guava的迭代器工具类

import org.checkerframework.checker.initialization.qual.UnknownInitialization; // 导入CheckerFramework的初始化检查注解
import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的可空性注解
import org.checkerframework.checker.nullness.qual.RequiresNonNull; // 导入CheckerFramework的非空要求注解

import java.util.ArrayList; // 导入Java标准库的动态数组列表
import java.util.BitSet; // 导入Java标准库的位集合
import java.util.Collection; // 导入Java标准库的集合接口
import java.util.Collections; // 导入Java标准库的集合工具类
import java.util.HashMap; // 导入Java标准库的哈希映射表
import java.util.HashSet; // 导入Java标准库的哈希集合
import java.util.List; // 导入Java标准库的列表接口
import java.util.Map; // 导入Java标准库的映射接口
import java.util.NavigableSet; // 导入Java标准库的可导航集合接口
import java.util.Set; // 导入Java标准库的集合接口
import java.util.SortedSet; // 导入Java标准库的有序集合接口
import java.util.TreeSet; // 导入Java标准库的树集合实现

import static java.util.Objects.requireNonNull; // 导入Java Objects类的requireNonNull静态方法

/**
 * Basic implementation of {@link Profiler}. // 简单分析器的基本实现，用于分析数据表的统计信息，包括唯一键、函数依赖和分布情况
 */
public class SimpleProfiler implements Profiler {

  @Override public Profile profile(Iterable<List<Comparable>> rows, // 重写profile方法，分析数据行的统计信息，rows为要分析的数据行集合，columns为列定义，initialGroups为初始分组（本分析器忽略此参数）
      final List<Column> columns, Collection<ImmutableBitSet> initialGroups) { // columns为数据列的定义集合，initialGroups为初始的列分组集合
    Util.discard(initialGroups); // this profiler ignores initial groups // 丢弃initialGroups参数，因为本简单分析器不使用初始分组信息
    return new Run(columns).profile(rows); // 创建一个新的Run实例并调用其profile方法来执行实际的分析工作，返回包含统计信息的Profile对象
  }

  /** Returns a measure of how much an actual value differs from expected. // 返回一个衡量实际值与期望值差异程度的度量指标
   * The formula is {@code abs(expected - actual) / (expected + actual)}. // 计算公式为：|期望值 - 实际值| / (期望值 + 实际值)
   *
   * <p>Examples:<ul> // 示例说明：
   *   <li>surprise(e, a) is always between 0 and 1; // 惊奇值始终在0到1之间
   *   <li>surprise(e, a) is 0 if e = a; // 当期望值等于实际值时，惊奇值为0
   *   <li>surprise(e, 0) is 1 if e &gt; 0; // 当实际值为0且期望值大于0时，惊奇值为1
   *   <li>surprise(0, a) is 1 if a &gt; 0; // 当期望值为0且实际值大于0时，惊奇值为1
   *   <li>surprise(5, 0) is 100%; // 期望值5，实际值0，惊奇值为100%
   *   <li>surprise(5, 3) is 25%; // 期望值5，实际值3，惊奇值为25%
   *   <li>surprise(5, 4) is 11%; // 期望值5，实际值4，惊奇值为11%
   *   <li>surprise(5, 5) is 0%; // 期望值5，实际值5，惊奇值为0%
   *   <li>surprise(5, 6) is 9%; // 期望值5，实际值6，惊奇值为9%
   *   <li>surprise(5, 16) is 52%; // 期望值5，实际值16，惊奇值为52%
   *   <li>surprise(5, 100) is 90%; // 期望值5，实际值100，惊奇值为90%
   * </ul>
   *
   * @param expected Expected value // 期望值参数
   * @param actual Actual value // 实际值参数
   * @return Measure of how much expected deviates from actual // 返回期望值偏离实际值的度量值（0到1之间）
   */
  public static double surprise(double expected, double actual) { // 静态方法：计算期望值与实际值之间的差异程度（惊奇值）
    if (expected == actual) { // 如果期望值等于实际值
      return 0d; // 返回0，表示没有差异
    }
    final double sum = expected + actual; // 计算期望值与实际值的和
    if (sum <= 0d) { // 如果和小于等于0
      return 1d; // 返回1，表示最大差异
    }
    return Math.abs(expected - actual) / sum; // 返回绝对差值与和的比值，即惊奇值
  }

  /** A run of the profiler. */ // 分析器的一次运行，负责执行具体的数据分析工作
  static class Run { // Run内部类：表示分析器的一次运行过程，包含所有分析状态和结果
    private final List<Column> columns; // 列定义列表，存储所有要分析的列信息
    final List<Space> spaces = new ArrayList<>(); // 空间列表，存储所有可能的列组合空间（Space对象）
    final List<@Nullable Space> singletonSpaces; // 单列空间列表，每个元素对应一个单列的Space对象，可能为null
    final List<Statistic> statistics = new ArrayList<>(); // 统计信息列表，存储所有发现的统计信息（唯一键、函数依赖、分布等）
    final PartiallyOrderedSet.Ordering<Space> ordering = // Space的部分有序集合排序规则
        (e1, e2) -> e2.columnOrdinals.contains(e1.columnOrdinals); // 如果e2的列序号包含e1的列序号，则e1 <= e2
    final PartiallyOrderedSet<Space> results = // 结果集合，存储所有极小的统计信息（非冗余的）
        new PartiallyOrderedSet<>(ordering); // 使用ordering规则创建的部分有序集合
    final PartiallyOrderedSet<Space> keyResults = // 键结果集合，存储所有发现的唯一键
        new PartiallyOrderedSet<>(ordering); // 使用ordering规则创建的部分有序集合
    private final List<ImmutableBitSet> keyOrdinalLists = // 键序号列表，存储所有发现的唯一键的列序号集合
        new ArrayList<>(); // 动态数组，用于快速检查某个列组合是否是唯一键或包含唯一键

    Run(final List<Column> columns) { // Run构造方法，初始化分析运行环境
      for (Ord<Column> column : Ord.zip(columns)) { // 遍历所有列，验证列序号的正确性
        if (column.e.ordinal != column.i) { // 如果列的ordinal属性与索引不匹配
          throw new IllegalArgumentException(); // 抛出非法参数异常
        }
      }
      this.columns = columns; // 保存列定义列表
      this.singletonSpaces = // 初始化单列空间列表
          new ArrayList<>(Collections.nCopies(columns.size(), null)); // 创建与列数相同大小的列表，初始值全为null
      for (ImmutableBitSet ordinals // 遍历所有可能的列组合（幂集）
          : ImmutableBitSet.range(columns.size()).powerSet()) { // 获取从0到列数-1的所有子集
        final Space space = new Space(ordinals, toColumns(ordinals)); // 为当前列组合创建一个Space对象
        spaces.add(space); // 将Space对象添加到spaces列表
        if (ordinals.cardinality() == 1) { // 如果当前组合只包含一列
          singletonSpaces.set(ordinals.nth(0), space); // 将该Space对象保存到singletonSpaces列表的对应位置
        }
      }
    }

    Profile profile(Iterable<List<Comparable>> rows) { // profile方法：执行数据行分析，返回包含统计信息的Profile对象
      final List<Comparable> values = new ArrayList<>(); // 创建临时列表，用于存储当前行的列值
      int rowCount = 0; // 行计数器，记录总行数
      for (final List<Comparable> row : rows) { // 遍历每一行数据
        ++rowCount; // 行计数器递增
      joint: // 标签，用于跳出外层循环
        for (Space space : spaces) { // 遍历所有列组合空间
          values.clear(); // 清空临时值列表
          for (Column column : space.columns) { // 遍历当前空间包含的所有列
            final Comparable value = row.get(column.ordinal); // 获取当前行在当前列的值
            values.add(value); // 将值添加到临时列表
            if (value == NullSentinel.INSTANCE) { // 如果值是NULL标记
              space.nullCount++; // 当前空间的NULL计数器递增
              continue joint; // 跳出当前空间的处理，继续下一个空间
            }
          }
          space.values.add(FlatLists.ofComparable(values)); // 将当前列组合的值列表添加到当前空间的values集合
        }
      }

      // Populate unique keys // 填充唯一键信息
      // If [x, y] is a key, // 如果[x, y]是一个唯一键
      // then [x, y, z] is a key but not intersecting, // 那么[x, y, z]也是键但不相交（包含更多列）
      // and [x, y] -> [a] is a functional dependency but not interesting, // [x, y] -> [a]是函数依赖但不有趣（因为a已被唯一键确定）
      // and [x, y, z] is not an interesting distribution. // [x, y, z]不是一个有趣的分布（因为它是超集）
      final Map<ImmutableBitSet, Distribution> distributions = new HashMap<>(); // 创建分布映射表，存储列组合到分布对象的映射
      for (Space space : spaces) { // 遍历所有列组合空间
        if (space.values.size() == rowCount // 如果当前空间的不同值数量等于总行数（即所有行的该列组合都不同）
            && !containsKey(space.columnOrdinals, false)) { // 且该列组合不包含任何已知的唯一键
          // We have discovered a new key. // 我们发现了一个新的唯一键
          // It is not an existing key or a super-set of a key. // 它不是现有的键，也不是现有键的超集
          statistics.add(new Unique(space.columns)); // 创建Unique统计对象并添加到统计列表
          space.unique = true; // 标记当前空间为唯一键
          keyOrdinalLists.add(space.columnOrdinals); // 将列序号添加到键序号列表
        }

        int nonMinimal = 0; // 非最小计数器，用于标记当前函数依赖是否为最小
      dependents: // 标签，用于跳出外层循环
        for (Space s : results.getDescendants(space)) { // 遍历当前空间的所有后代空间（列子集）
          if (s.cardinality() == space.cardinality()) { // 如果子空间与当前空间的基数相同
            // We have discovered a sub-set that has the same cardinality. // 我们发现了一个具有相同基数的子集
            // The column(s) that are not in common are functionally // 不在公共部分的列是函数依赖的
            // dependent. // 依赖列
            final ImmutableBitSet dependents = // 计算依赖列的序号集合
                space.columnOrdinals.except(s.columnOrdinals); // 当前列序号减去子集列序号
            for (int i : s.columnOrdinals) { // 遍历子集的所有列
              final Space s1 = singletonSpaces.get(i); // 获取该列的单列空间
              final ImmutableBitSet rest = s.columnOrdinals.clear(i); // 获取子集去掉当前列后的列集合
              for (ImmutableBitSet dependent : requireNonNull(s1, "s1").dependents) { // 遍历该列的所有已知依赖
                if (rest.contains(dependent)) { // 如果剩余列包含该依赖
                  // The "key" of this functional dependency is not minimal. // 该函数依赖的"键"不是最小的
                  // For instance, if we know that // 例如，如果我们知道
                  //   (a) -> x // (a) -> x
                  // then // 那么
                  //   (a, b, x) -> y // (a, b, x) -> y
                  // is not minimal; we could say the same with a smaller key: // 不是最小的；可以用更小的键说同样的话：
                  //   (a, b) -> y // (a, b) -> y
                  ++nonMinimal; // 非最小计数器递增
                  continue dependents; // 跳出当前依赖的处理
                }
              }
            }
            for (int dependent : dependents) { // 遍历所有依赖列
              final Space s1 = singletonSpaces.get(dependent); // 获取该列的单列空间
              for (ImmutableBitSet d : requireNonNull(s1, "s1").dependents) { // 遍历该列的所有已知依赖
                if (s.columnOrdinals.contains(d)) { // 如果当前列集合包含该依赖
                  ++nonMinimal; // 非最小计数器递增
                  continue dependents; // 跳出当前依赖的处理
                }
              }
            }
            space.dependencies.or(dependents.toBitSet()); // 将依赖列添加到当前空间的依赖位集合
            for (int d : dependents) { // 遍历所有依赖列
              Space spaceD = // 获取依赖列的单列空间
                  requireNonNull(singletonSpaces.get(d), // 获取单列空间，不能为null
                      () -> "singletonSpaces.get(d) is null for " + d); // 错误信息
              spaceD.dependents.add(s.columnOrdinals); // 将当前列集合添加到依赖列的依赖者集合
            }
          }
        }

        int nullCount; // NULL计数变量
        final SortedSet<Comparable> valueSet; // 值集合变量
        if (space.columns.size() == 1) { // 如果当前空间只包含一列
          nullCount = space.nullCount; // 使用空间的NULL计数
          valueSet = // 创建不可变的有序值集合
              ImmutableSortedSet.copyOf( // 复制为不可变集合
                  Util.transform(space.values, Iterables::getOnlyElement)); // 从每个值列表中提取唯一元素
        } else { // 如果当前空间包含多列
          nullCount = -1; // NULL计数设为-1（表示不适用）
          valueSet = null; // 值集合设为null（多列不存储值集合）
        }
        double expectedCardinality; // 期望基数变量
        final double cardinality = space.cardinality(); // 获取当前空间的实际基数（不同值的数量）
        switch (space.columns.size()) { // 根据列数量计算期望基数
        case 0: // 如果没有列
          expectedCardinality = 1d; // 期望基数为1（只有一个空组合）
          break; // 跳出switch
        case 1: // 如果只有一列
          expectedCardinality = rowCount; // 期望基数等于总行数（最坏情况）
          break; // 跳出switch
        default: // 如果有多列
          expectedCardinality = rowCount; // 初始期望基数为总行数
          for (Column column : space.columns) { // 遍历当前空间的所有列
            final Distribution d1 = // 获取单列的分布信息
                distributions.get(ImmutableBitSet.of(column.ordinal)); // 从分布映射表中获取
            final Distribution d2 = // 获取去掉当前列后的分布信息
                distributions.get(space.columnOrdinals.clear(column.ordinal)); // 从分布映射表中获取
            final double d = // 使用Lattice公式计算期望行数
                Lattice.getRowCount(rowCount, // 总行数
                    requireNonNull(d1, "d1").cardinality, // 单列基数
                    requireNonNull(d2, "d2").cardinality); // 去掉当前列后的基数
            expectedCardinality = Math.min(expectedCardinality, d); // 取最小值作为期望基数
          }
        }
        final boolean minimal = nonMinimal == 0 // 判断是否为最小分布：非最小计数为0
            && !space.unique // 且不是唯一键
            && !containsKey(space.columnOrdinals, true); // 且不包含任何唯一键（严格）
        final Distribution distribution = // 创建分布对象
            new Distribution(space.columns, valueSet, cardinality, nullCount, // 参数：列、值集合、基数、NULL计数
                expectedCardinality, minimal); // 参数：期望基数、是否最小
        statistics.add(distribution); // 将分布对象添加到统计列表
        distributions.put(space.columnOrdinals, distribution); // 将分布对象添加到分布映射表

        if (distribution.minimal) { // 如果是最小分布
          results.add(space); // 将空间添加到结果集合
        }
      }

      for (Space s : singletonSpaces) { // 遍历所有单列空间
        for (ImmutableBitSet dependent : requireNonNull(s, "s").dependents) { // 遍历该列的所有依赖者（列组合）
          if (!containsKey(dependent, false) // 如果依赖者不包含任何唯一键
              && !hasNull(dependent)) { // 且依赖者中没有NULL值
            statistics.add( // 创建函数依赖统计对象并添加到统计列表
                new FunctionalDependency(toColumns(dependent), // 依赖列
                    Iterables.getOnlyElement(s.columns))); // 被依赖列（单列）
          }
        }
      }
      return new Profile(columns, new RowCount(rowCount), // 返回Profile对象，包含列定义、行计数
          Iterables.filter(statistics, FunctionalDependency.class), // 过滤出所有函数依赖
          Iterables.filter(statistics, Distribution.class), // 过滤出所有分布
          Iterables.filter(statistics, Unique.class)); // 过滤出所有唯一键
    }

    /** Returns whether a set of column ordinals // 返回列序号集合
     * matches or contains a unique key. // 是否匹配或包含唯一键
     * If {@code strict}, it must contain a unique key. */ // 如果strict为true，则必须严格包含唯一键（不能相等）
    private boolean containsKey(ImmutableBitSet ordinals, boolean strict) { // 检查列序号集合是否包含唯一键
      for (ImmutableBitSet keyOrdinals : keyOrdinalLists) { // 遍历所有已知的唯一键序号集合
        if (ordinals.contains(keyOrdinals)) { // 如果当前列集合包含某个唯一键
          return !(strict && keyOrdinals.equals(ordinals)); // 如果strict且相等则返回false，否则返回true
        }
      }
      return false; // 不包含任何唯一键，返回false
    }

    private boolean hasNull(ImmutableBitSet columnOrdinals) { // 检查列组合中是否有NULL值
      for (Integer columnOrdinal : columnOrdinals) { // 遍历列序号集合中的所有列
        Space space = // 获取该列的单列空间
            requireNonNull(singletonSpaces.get(columnOrdinal), // 获取单列空间，不能为null
                () -> "singletonSpaces.get(columnOrdinal) is null for " // 错误信息
                    + columnOrdinal); // 列序号
        if (space.nullCount > 0) { // 如果该列的NULL计数大于0
          return true; // 返回true，表示有NULL值
        }
      }
      return false; // 所有列都没有NULL值，返回false
    }

    @RequiresNonNull("columns") // 要求columns字段不能为null
    private ImmutableSortedSet<Column> toColumns( // 将列序号转换为列对象的集合
        @UnknownInitialization Run this, // Run实例（可能未完全初始化）
        Iterable<Integer> ordinals) { // 列序号的可迭代集合
      //noinspection Convert2MethodRef // 禁止转换为方法引用的警告
      return ImmutableSortedSet.copyOf( // 创建不可变的有序列集合
          Util.transform(ordinals, idx -> columns.get(idx))); // 将列序号映射为列对象
    }
  }

  /** Work space for a particular combination of columns. */ // 特定列组合的工作空间，用于存储和分析该列组合的统计信息
  static class Space implements Comparable<Space> { // Space内部类：表示一个特定列组合的分析空间，实现了Comparable接口以便排序
    final ImmutableBitSet columnOrdinals; // 列序号的不可变位集合，标识该空间包含哪些列
    final ImmutableSortedSet<Column> columns; // 列对象的不可变有序集合，包含该空间的所有列
    int nullCount; // NULL计数器，记录该列组合中出现NULL值的行数
    final NavigableSet<FlatLists.ComparableList<Comparable>> values = // 值集合，存储该列组合的所有不同值组合
        new TreeSet<>(); // 使用树集合实现，自动排序且不重复
    boolean unique; // 唯一键标记，如果为true表示该列组合构成唯一键
    final BitSet dependencies = new BitSet(); // 依赖位集合，记录该列组合依赖的其他列
    final Set<ImmutableBitSet> dependents = new HashSet<>(); // 依赖者集合，记录依赖该列组合的其他列组合

    Space(ImmutableBitSet columnOrdinals, Iterable<Column> columns) { // Space构造方法，初始化列组合空间
      this.columnOrdinals = columnOrdinals; // 保存列序号集合
      this.columns = ImmutableSortedSet.copyOf(columns); // 创建列的不可变有序集合副本
    }

    @Override public int hashCode() { // 重写hashCode方法，基于列序号集合计算哈希值
      return columnOrdinals.hashCode(); // 返回列序号集合的哈希值
    }

    @Override public boolean equals(@Nullable Object o) { // 重写equals方法，比较两个Space对象是否相等
      return o == this // 如果是同一个对象，返回true
          || o instanceof Space // 或者是Space类的实例
          && columnOrdinals.equals(((Space) o).columnOrdinals); // 且列序号集合相等，返回true
    }

    @Override public int compareTo(Space o) { // 重写compareTo方法，实现Space对象的比较
      return columnOrdinals.equals(o.columnOrdinals) ? 0 // 如果列序号集合相等，返回0
          : columnOrdinals.contains(o.columnOrdinals) ? 1 // 如果当前包含其他，返回1（当前更大）
              : -1; // 否则返回-1（当前更小）
    }

    /** Number of distinct values. Null is counted as a value, if present. */ // 不同值的数量，如果存在NULL值则将其计为一个值
    public double cardinality() { // 计算当前空间的基数（不同值的数量）
      return values.size() + (nullCount > 0 ? 1 : 0); // 返回非空值的数量加上（如果有NULL值则加1）
    }
  }
}
