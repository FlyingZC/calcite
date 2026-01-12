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
package org.apache.calcite.profile; // 包声明：Apache Calcite 框架中用于分析表数据统计信息的包

import org.apache.calcite.config.CalciteSystemProperty; // 导入：Calcite 系统属性配置类，用于获取调试等配置
import org.apache.calcite.linq4j.Ord; // 导入：LINQ4J 工具类，用于给元素添加序号索引
import org.apache.calcite.linq4j.tree.Primitive; // 导入：原始类型工具类，用于处理基本类型数组转换
import org.apache.calcite.materialize.Lattice; // 导入：Lattice 类，用于物化视图相关计算
import org.apache.calcite.rel.metadata.NullSentinel; // 导入：Null 哨兵对象，用于标识 NULL 值
import org.apache.calcite.runtime.FlatLists; // 导入：扁平列表工具类，用于创建高效的列表实现
import org.apache.calcite.util.ImmutableBitSet; // 导入：不可变位集合类，用于高效表示列的索引集合
import org.apache.calcite.util.Pair; // 导入：键值对工具类，用于存储两个相关联的对象
import org.apache.calcite.util.PartiallyOrderedSet; // 导入：偏序集合类，用于维护元素之间的部分顺序关系
import org.apache.calcite.util.Util; // 导入：通用工具类，提供各种辅助方法

import com.google.common.collect.ImmutableList; // 导入：Google Guava 不可变列表类，提供线程安全的列表实现
import com.google.common.collect.ImmutableSortedSet; // 导入：Google Guava 不可变排序集合类
import com.google.common.collect.Iterables; // 导入：Google Guava 可迭代对象工具类
import com.google.common.collect.Ordering; // 导入：Google Guava 排序工具类
import com.yahoo.sketches.hll.HllSketch; // 导入：Yahoo Data Sketches 的 HyperLogLog 草图算法实现，用于基数估计

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：Checker Framework 的可空类型注解

import java.nio.ByteBuffer; // 导入：Java NIO 字节缓冲区类，用于高效处理二进制数据
import java.nio.charset.StandardCharsets; // 导入：标准字符集编码类
import java.util.ArrayDeque; // 导入：数组双端队列类，提供高效的队列操作
import java.util.ArrayList; // 导入：动态数组列表类
import java.util.Arrays; // 导入：数组工具类
import java.util.BitSet; // 导入：位集合类，用于高效存储布尔值
import java.util.Collection; // 导入：集合接口
import java.util.Collections; // 导入：集合工具类
import java.util.Deque; // 导入：双端队列接口
import java.util.HashMap; // 导入：哈希映射类
import java.util.HashSet; // 导入：哈希集合类
import java.util.List; // 导入：列表接口
import java.util.Map; // 导入：映射接口
import java.util.NavigableSet; // 导入：可导航集合接口，支持子集操作
import java.util.PriorityQueue; // 导入：优先队列类，基于堆实现
import java.util.Queue; // 导入：队列接口
import java.util.Set; // 导入：集合接口
import java.util.SortedSet; // 导入：排序集合接口
import java.util.TreeSet; // 导入：树集合类，自动排序
import java.util.function.Predicate; // 导入：谓词函数式接口，用于条件判断

import static com.google.common.base.Preconditions.checkArgument; // 导入：Google Guava 参数校验工具

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入：LINQ4J 非空转换工具

import static org.apache.calcite.profile.ProfilerImpl.CompositeCollector.OF; // 导入：静态常量，用于调试

import static java.util.Objects.requireNonNull; // 导入：Java 对象非空校验工具

/**
 * Implementation of {@link Profiler} that only investigates "interesting"
 * combinations of columns.
 * Profiler 接口的实现类，只分析"有趣的"列组合
 *
 * 类作用：ProfilerImpl 是 Calcite 中用于分析表数据统计信息的核心实现类。它的主要功能是：
 * 1. 扫描表数据，分析列的分布情况（基数、NULL 值数量等）
 * 2. 识别列之间的函数依赖关系（如 A 列的值可以确定 B 列的值）
 * 3. 发现唯一键（候选键）和超键
 * 4. 评估列组合的"意外性"（surprise），即实际基数与预期基数的差异
 * 5. 使用 HyperLogLog 算法进行基数估计，减少内存消耗
 *
 * 核心设计思想：
 * - 采用多轮扫描策略，每轮只分析部分列组合，避免内存溢出
 * - 使用优先队列选择最"有趣"的列组合进行深入分析
 * - 使用偏序集合维护列组合之间的包含关系
 * - 动态切换收集器策略：精确收集（TreeSet）vs 近似估计（HyperLogLog）
 *
 * 使用场景：
 * - 查询优化器需要了解表数据分布来选择最优执行计划
 * - 物化视图选择需要识别高基数的列组合
 * - 数据质量检测需要发现函数依赖和唯一约束
 */
public class ProfilerImpl implements Profiler { // ProfilerImpl 类：Profiler 接口的具体实现，负责分析表数据的统计特性
  /** The number of combinations to consider per pass.
   * The number is determined by memory, but a value of 1,000 is typical.
   * You need 2KB memory per sketch, and one sketch for each combination.
   *
   * 成员变量作用：每轮扫描中考虑的列组合数量
   * - 由内存限制决定，典型值为 1000
   * - 每个组合大约需要 2KB 内存（用于 HyperLogLog sketch）
   * - 这个值控制了每轮扫描的内存消耗，防止内存溢出
   */
  private final int combinationsPerPass; // 每轮扫描的最大列组合数，由内存限制决定

  /** The minimum number of combinations considered "interesting". After that,
   * a combination is only considered "interesting" if its surprise is greater
   * than the median surprise.
   *
   * 成员变量作用：被认为是"有趣"的列组合的最小数量
   * - 在达到这个数量之前，所有列组合都被视为有趣的
   * - 超过这个数量后，只有意外性（surprise）高于中位数的组合才被认为有趣
   * - 这个值控制了分析的深度和选择性，典型值为 200
   * - 意外性 = 预期基数 - 实际基数，值越大表示越"意外"
   */
  private final int interestingCount; // 被认为有趣的列组合的最小数量阈值

  /** Whether a successor is considered interesting enough to analyze.
   *
   * 成员变量作用：判断后继列组合是否足够有趣以进行分析的谓词
   * - 这是一个函数式接口，接受一个 Pair<Space, Column> 参数
   * - Space 表示当前的列组合空间
   * - Column 表示要添加的新列
   * - 返回 true 表示这个新的列组合值得分析，false 表示跳过
   * - 默认实现接受所有组合，但可以自定义实现来过滤不感兴趣的组合
   */
  private final Predicate<Pair<Space, Column>> predicate; // 判断后继组合是否足够有趣的谓词函数

  public static Builder builder() { // 静态工厂方法：创建 ProfilerImpl 的构建器对象
    return new Builder(); // 返回新的 Builder 实例，用于链式调用配置参数
  }

  /**
   * Creates a {@code ProfilerImpl}.
   * 创建 ProfilerImpl 实例的构造方法
   *
   * @param combinationsPerPass Maximum number of columns (or combinations of
   *   columns) to compute each pass
   *   参数：每轮扫描的最大列组合数量，控制内存消耗
   * @param interestingCount Minimum number of combinations considered
   *   interesting
   *   参数：被认为有趣的列组合的最小数量，控制分析深度
   * @param predicate Whether a successor is considered interesting enough to
   *   analyze
   *   参数：判断后继组合是否有趣的谓词函数
   */
  ProfilerImpl(int combinationsPerPass, // 构造方法：创建 ProfilerImpl 实例
      int interestingCount, Predicate<Pair<Space, Column>> predicate) { // 参数：每轮组合数、有趣组合最小数量、有趣性判断谓词
    checkArgument(combinationsPerPass > 2); // 参数校验：每轮组合数必须大于 2，确保有意义
    checkArgument(interestingCount > 2); // 参数校验：有趣组合数必须大于 2，确保有意义
    this.combinationsPerPass = combinationsPerPass; // 赋值：设置每轮最大组合数
    this.interestingCount = interestingCount; // 赋值：设置有趣组合最小数量
    this.predicate = predicate; // 赋值：设置有趣性判断谓词
  }

  @Override public Profile profile(Iterable<List<Comparable>> rows, // 方法：分析表数据并生成统计信息概要文件
      final List<Column> columns, Collection<ImmutableBitSet> initialGroups) { // 参数：行数据、列定义、初始列组合集合
    return new Run(columns, initialGroups).profile(rows); // 创建 Run 对象并执行分析，返回 Profile 结果
  }

  /** A run of the profiler.
   *
   * 内部类作用：Profiler 的一次运行会话
   * - 每个 Run 对象代表一次完整的分析过程
   * - 负责协调多轮扫描、管理列组合队列、收集统计信息
   * - 维护分析过程中的所有中间状态和结果
   * - 实现了分批处理和增量分析的逻辑
   */
  class Run { // Run 类：Profiler 的一次运行会话，管理整个分析过程
    private final List<Column> columns; // 成员变量：列定义列表，存储所有列的信息
    final PartiallyOrderedSet<ImmutableBitSet> keyPoset = // 成员变量：键的偏序集合
        new PartiallyOrderedSet<>( // 创建偏序集合
            PartiallyOrderedSet.BIT_SET_INCLUSION_ORDERING); // 使用位集合包含关系作为偏序
    // keyPoset 作用：维护所有发现的键之间的部分顺序关系
    // - 用于检查一个列组合是否包含另一个键（超键检测）
    // - 用于查找某个列组合的父键和子键
    // - 帮助识别非最小键和函数依赖

    final Map<ImmutableBitSet, Distribution> distributions = new HashMap<>(); // 成员变量：列组合到分布信息的映射
    // distributions 作用：存储所有已分析的列组合的统计分布信息
    // - 键：列的索引集合（ImmutableBitSet）
    // - 值：分布信息（Distribution），包含基数、NULL 值数、意外性等
    // - 用于后续的基数估计和意外性计算

    /** List of spaces that have one column.
     * 成员变量作用：单列空间的列表
     * - 列表的每个元素对应一个单列的 Space 对象
     * - 索引与列的序号对应（singletonSpaces[i] 表示第 i 列的空间）
     * - 初始值为 null，在分析过程中填充
     * - 用于快速查找单列的统计信息和依赖关系
     */
    final List<@Nullable Space> singletonSpaces; // 单列空间列表，索引对应列序号

    /** Combinations of columns that we have computed but whose successors have
     * not yet been computed. We may add some of those successors to
     * {@link #spaceQueue}.
     *
     * 成员变量作用：已完成计算但后继尚未计算的列组合队列
     * - 这是一个优先队列（PriorityQueue），按"有趣性"排序
     * - 队列中的每个 Space 对象表示一个已分析的列组合
     * - 这些组合的后继（添加新列后的组合）可能在下一轮被分析
     * - 排序规则：列数少的优先；列数相同时，意外性高的优先
     * - 用于实现增量分析策略，优先分析最有趣的组合
     */
    final Queue<Space> doneQueue = // 已完成队列，存储已分析的列组合
        new PriorityQueue<>(100, (s0, s1) -> { // 创建优先队列，初始容量 100，自定义比较器
          // The space with 0 columns is more interesting than
          // any space with 1 column, and so forth.
          // 比较规则：0 列的组合比 1 列的组合更有趣，以此类推
          // For spaces with 2 or more columns we compare "surprise":
          // how many fewer values did it have than expected?
          // 对于 2 列及以上的组合，比较"意外性"：实际值比预期值少多少
          int c = Integer.compare(s0.columns.size(), s1.columns.size()); // 首先比较列数大小
          if (c == 0) { // 如果列数相同
            c = Double.compare(s0.surprise(), s1.surprise()); // 则比较意外性，意外性高的优先
          }
          return c; // 返回比较结果
        });

    final SurpriseQueue surprises; // 成员变量：意外性队列，用于维护最有趣的 N 个意外值

    /** Combinations of columns that we will compute next pass.
     *
     * 成员变量作用：下一轮将要计算的列组合队列
     * - 这是一个双端队列（ArrayDeque），支持高效的入队和出队
     * - 队列中的每个元素是列索引集合（ImmutableBitSet）
     * - 表示将在下一轮扫描中分析的列组合
     * - 由 doneQueue 中的组合生成后继而来
     * - 确保每个列组合只被分析一次（通过 resultSet 去重）
     */
    final Deque<ImmutableBitSet> spaceQueue = new ArrayDeque<>(); // 下一轮分析的列组合队列

    final List<Unique> uniques = new ArrayList<>(); // 成员变量：唯一键列表，存储所有发现的唯一键
    final List<FunctionalDependency> functionalDependencies = new ArrayList<>(); // 成员变量：函数依赖列表，存储所有发现的函数依赖

    /** Column ordinals that have ever been placed on {@link #spaceQueue}.
     * Ensures that we do not calculate the same combination more than once,
     * even though we generate a column set from multiple parents.
     *
     * 成员变量作用：曾经放入 spaceQueue 的列索引集合
     * - 这是一个集合（HashSet），用于去重
     * - 确保同一个列组合不会被多次分析
     * - 即使从多个父组合生成，也只分析一次
     * - 在生成后继组合时，先检查是否已存在，避免重复计算
     */
    final Set<ImmutableBitSet> resultSet = new HashSet<>(); // 结果集合，用于去重

    final PartiallyOrderedSet<Space> results = // 成员变量：结果的偏序集合
        new PartiallyOrderedSet<>((e1, e2) -> // 创建偏序集合，自定义比较器
            e2.columnOrdinals.contains(e1.columnOrdinals)); // 比较规则：e2 包含 e1 时，e1 <= e2
    // results 作用：维护所有 Space 对象之间的包含关系
    // - 用于查找某个 Space 的所有后代（超集）
    // - 帮助识别非最小键和函数依赖
    // - 支持高效的层级查询操作

    private final List<ImmutableBitSet> keyOrdinalLists = // 成员变量：键的列索引列表
        new ArrayList<>(); // 存储所有发现的唯一键的列索引集合
    // keyOrdinalLists 作用：记录所有发现的唯一键
    // - 用于后续的函数依赖分析
    // - 帮助识别非最小键（包含其他键的超键）

    private int rowCount; // 成员变量：总行数，在第一轮扫描后设置

    /**
     * Creates a Run.
     * 创建 Run 实例的构造方法
     *
     * @param columns List of columns
     *   参数：列定义列表
     *
     * @param initialGroups List of combinations of columns that should be
     *                     profiled early, because they may be interesting
     *   参数：应该尽早分析的列组合列表，因为它们可能很有趣
     */
    Run(final List<Column> columns, Collection<ImmutableBitSet> initialGroups) { // 构造方法：创建 Run 实例
      this.columns = ImmutableList.copyOf(columns); // 复制列列表为不可变列表，防止外部修改
      for (Ord<Column> column : Ord.zip(columns)) { // 遍历列，检查列序号是否正确
        if (column.e.ordinal != column.i) { // 如果列的 ordinal 与索引不匹配
          throw new IllegalArgumentException(); // 抛出异常，数据不一致
        }
      }
      this.singletonSpaces = // 初始化单列空间列表
          new ArrayList<>(Collections.nCopies(columns.size(), (Space) null)); // 创建指定大小的列表，初始值为 null
      if (combinationsPerPass > Math.pow(2D, columns.size())) { // 如果每轮组合数大于所有组合的总数
        // There are not many columns. We can compute all combinations in the
        // first pass.
        // 列数不多，可以在第一轮计算所有组合
        for (ImmutableBitSet ordinals // 遍历所有可能的列组合
            : ImmutableBitSet.range(columns.size()).powerSet()) { // 生成所有列组合的幂集
          spaceQueue.add(ordinals); // 将所有组合加入队列，第一轮全部分析
        }
      } else { // 列数较多，需要多轮扫描
        // We will need to take multiple passes.
        // Pass 0, just put the empty combination on the queue.
        // Next pass, we will do its successors, the singleton combinations.
        // 需要多轮扫描
        // 第 0 轮：只放入空组合
        // 下一轮：分析其后继，即单列组合
        spaceQueue.add(ImmutableBitSet.of()); // 添加空组合（GROUP BY ()）
        spaceQueue.addAll(initialGroups); // 添加初始组合，优先分析这些可能有趣的组合
        if (columns.size() < combinationsPerPass) { // 如果列数小于每轮组合数
          // There are not very many columns. Compute the singleton
          // groups in pass 0.
          // 列数不多，在第 0 轮就计算单列组合
          for (Column column : columns) { // 遍历所有列
            spaceQueue.add(ImmutableBitSet.of(column.ordinal)); // 将每个单列组合加入队列
          }
        }
      }
      // The surprise queue must have enough room for all singleton groups
      // plus all initial groups.
      // 意外性队列必须有足够的空间容纳所有单列组合和初始组合
      surprises = // 创建意外性队列
          new SurpriseQueue(1 + columns.size() + initialGroups.size(), // 容量 = 1（空组合）+ 列数 + 初始组合数
              interestingCount); // 有趣组合的最小数量
    }

    Profile profile(Iterable<List<Comparable>> rows) { // 方法：执行分析并生成 Profile 对象
      int pass = 0; // 初始化轮次计数器
      for (;;) { // 无限循环，直到没有更多组合需要分析
        final List<Space> spaces = nextBatch(pass); // 获取下一批要分析的列组合
        if (spaces.isEmpty()) { // 如果没有更多组合
          break; // 退出循环
        }
        pass(pass++, spaces, rows); // 执行一轮扫描，分析这些组合
      }

      for (Space s : singletonSpaces) { // 遍历所有单列空间
        for (ImmutableBitSet dependent : requireNonNull(s, "s").dependents) { // 遍历每个单列的依赖集合
          functionalDependencies.add( // 添加函数依赖
              new FunctionalDependency(toColumns(dependent), // 依赖列集合
                  Iterables.getOnlyElement(s.columns))); // 决定列（单列）
        }
      }
      return new Profile(columns, new RowCount(rowCount), // 返回 Profile 对象，包含所有分析结果
          functionalDependencies, distributions.values(), uniques); // 参数：列列表、行数、函数依赖、分布信息、唯一键
    }

    /** Populates {@code spaces} with the next batch.
     * Returns an empty list if done.
     *
     * 方法作用：填充下一批要分析的列组合
     * - 从 spaceQueue 中取出组合，直到达到 combinationsPerPass 个
     * - 如果 spaceQueue 为空，则从 doneQueue 中取出已完成的组合，生成其后继
     * - 后继组合需要满足一定条件才被加入 spaceQueue
     * - 返回空列表表示所有组合都已分析完毕
     *
     * 算法流程：
     * 1. 从 spaceQueue 中取出组合，直到达到每轮最大数量
     * 2. 如果 spaceQueue 为空，从 doneQueue 中取出一个已完成组合
     * 3. 生成该组合的所有可能后继（添加一列）
     * 4. 过滤后继：不包含键、满足谓词条件、未重复
     * 5. 将过滤后的后继加入 spaceQueue
     * 6. 重复步骤 2-5，直到 spaceQueue 不为空或 doneQueue 为空
     */
    List<Space> nextBatch(int pass) { // 方法：获取下一批要分析的列组合
      final List<Space> spaces = new ArrayList<>(); // 创建空列表，用于存储本批组合
    loop: // 标签：用于内层循环跳转
      for (;;) { // 无限循环，直到返回
        if (spaces.size() >= combinationsPerPass) { // 如果已达到每轮最大数量
          // We have enough for the next pass.
          // 已经有足够的组合用于下一轮
          return spaces; // 返回当前批次
        }
        // First, see if there is a space we did have room for last pass.
        // 首先，检查是否有上一轮没空间处理的组合
        final ImmutableBitSet ordinals = spaceQueue.poll(); // 从队列中取出一个列索引集合
        if (ordinals != null) { // 如果取到了组合
          final Space space = new Space(this, ordinals, toColumns(ordinals)); // 创建 Space 对象
          spaces.add(space); // 加入本批次
          if (ordinals.cardinality() == 1) { // 如果是单列组合
            singletonSpaces.set(ordinals.nth(0), space); // 保存到单列空间列表
          }
        } else { // 如果 spaceQueue 为空
          // Next, take a space that was done last time, generate its
          // successors, and add the interesting ones to the space queue.
          // 从已完成队列中取出一个组合，生成其后继，将有趣的加入 spaceQueue
          for (;;) { // 无限循环，直到找到后继或 doneQueue 为空
            final Space doneSpace = doneQueue.poll(); // 从已完成队列中取出一个组合
            if (doneSpace == null) { // 如果已完成队列为空
              // There are no more done spaces. We're done.
              // 没有更多已完成组合，分析完毕
              return spaces; // 返回当前批次（可能为空）
            }
            if (doneSpace.columnOrdinals.cardinality() > 4) { // 如果列数超过 4
              // Do not generate successors for groups with lots of columns,
              // probably initial groups
              // 不为列数很多的组合生成后继，可能是初始组合
              continue; // 跳过这个组合
            }
            for (Column column : columns) { // 遍历所有列
              if (!doneSpace.columnOrdinals.get(column.ordinal)) { // 如果该列不在当前组合中
                if (pass == 0 // 如果是第 0 轮
                    || doneSpace.columnOrdinals.cardinality() == 0 // 或者是空组合
                    || !containsKey( // 或者不包含任何键
                        doneSpace.columnOrdinals.set(column.ordinal)) // 检查添加该列后是否包含键
                    && predicate.test(Pair.of(doneSpace, column))) { // 并且满足谓词条件
                  final ImmutableBitSet nextOrdinals = // 创建新的列索引集合
                      doneSpace.columnOrdinals.set(column.ordinal); // 添加新列
                  if (resultSet.add(nextOrdinals)) { // 如果该组合未被分析过（去重）
                    spaceQueue.add(nextOrdinals); // 加入 spaceQueue
                  }
                }
              }
            }
            // We've converted at a space into at least one interesting
            // successor.
            // 已将一个组合转换为至少一个有趣的后继
            if (!spaceQueue.isEmpty()) { // 如果 spaceQueue 不为空
              continue loop; // 跳出内层循环，继续外层循环
            }
          }
        }
      }
    }

    private boolean containsKey(ImmutableBitSet ordinals) { // 方法：检查列组合是否包含任何已知的键
      for (ImmutableBitSet keyOrdinals : keyOrdinalLists) { // 遍历所有已知键
        if (ordinals.contains(keyOrdinals)) { // 如果当前组合包含该键
          return true; // 返回 true，包含键
        }
      }
      return false; // 不包含任何键，返回 false
    }

    void pass(int pass, List<Space> spaces, Iterable<List<Comparable>> rows) { // 方法：执行一轮扫描，分析指定的列组合
      if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了调试模式
        System.out.println("pass: " + pass // 打印轮次信息
            + ", spaces.size: " + spaces.size() // 组合数量
            + ", distributions.size: " + distributions.size()); // 已分析的分布数量
      }

      for (Space space : spaces) { // 遍历所有要分析的组合
        space.collector = Collector.create(space, 1000); // 为每个组合创建收集器，阈值为 1000
      }

      int rowCount = 0; // 初始化行数计数器
      for (final List<Comparable> row : rows) { // 遍历所有行数据
        ++rowCount; // 行数加 1
        for (Space space : spaces) { // 遍历所有组合
          castNonNull(space.collector).add(row); // 将该行数据添加到每个组合的收集器中
        }
      }

      // Populate unique keys.
      // If [x, y] is a key,
      // then [x, y, z] is a non-minimal key (therefore not interesting),
      // and [x, y] -> [a] is a functional dependency but not interesting,
      // and [x, y, z] is not an interesting distribution.
      // 填充唯一键信息
      // 如果 [x, y] 是键，
      // 那么 [x, y, z] 是非最小键（因此不有趣），
      // 并且 [x, y] -> [a] 是函数依赖但不有趣，
      // 并且 [x, y, z] 不是有趣的分布
      for (Space space : spaces) { // 遍历所有组合
        Collector collector = space.collector; // 获取收集器
        if (collector != null) { // 如果收集器存在
          collector.finish(); // 完成收集，计算统计信息
        }
        space.collector = null; // 清空收集器引用，释放内存
//        results.add(space); // 将组合加入结果集合（已注释）

        int nonMinimal = 0; // 初始化非最小计数器
      dependents: // 标签：用于跳出多层循环
        for (Space s : results.getDescendants(space)) { // 遍历当前组合的所有后代（超集）
          if (s.cardinality == space.cardinality) { // 如果后代的基数等于当前组合的基数
            // We have discovered a sub-set that has the same cardinality.
            // The column(s) that are not in common are functionally
            // dependent.
            // 发现了一个子集具有相同的基数
            // 不共同的列是函数依赖的
            final ImmutableBitSet dependents = // 计算依赖列集合
                space.columnOrdinals.except(s.columnOrdinals); // 当前组合减去子集
            for (int i : s.columnOrdinals) { // 遍历子集的所有列
              final Space s1 = singletonSpaces.get(i); // 获取该列的单列空间
              final ImmutableBitSet rest = s.columnOrdinals.clear(i); // 获取子集的其他列
              for (ImmutableBitSet dependent : requireNonNull(s1, "s1").dependents) { // 遍历该列的依赖集合
                if (rest.contains(dependent)) { // 如果其他列包含该依赖
                  // The "key" of this functional dependency is not minimal.
                  // For instance, if we know that
                  //   (a) -> x
                  // then
                  //   (a, b, x) -> y
                  // is not minimal; we could say the same with a smaller key:
                  //   (a, b) -> y
                  // 这个函数依赖的"键"不是最小的
                  // 例如，如果我们知道 (a) -> x
                  // 那么 (a, b, x) -> y 不是最小的；可以用更小的键表示：(a, b) -> y
                  ++nonMinimal; // 非最小计数加 1
                  continue dependents; // 跳出多层循环，处理下一个后代
                }
              }
            }
            for (int dependent : dependents) { // 遍历所有依赖列
              final Space s1 = singletonSpaces.get(dependent); // 获取依赖列的单列空间
              for (ImmutableBitSet d : requireNonNull(s1, "s1").dependents) { // 遍历该列的依赖集合
                if (s.columnOrdinals.contains(d)) { // 如果子集包含该依赖
                  ++nonMinimal; // 非最小计数加 1
                  continue dependents; // 跳出多层循环
                }
              }
            }
            space.dependencies.or(dependents.toBitSet()); // 将依赖列加入当前组合的依赖位集合
            for (int d : dependents) { // 遍历所有依赖列
              Space spaceD = requireNonNull(singletonSpaces.get(d), "singletonSpaces.get(d)"); // 获取依赖列的单列空间
              spaceD.dependents.add(space.columnOrdinals); // 将当前组合加入依赖列的依赖集合
            }
          }
        }
        if (nonMinimal > 0) { // 如果存在非最小依赖
          continue; // 跳过这个组合，不加入分布信息
        }
        final String s = space.columns.toString(); // for debug // 调试用：列组合的字符串表示
        Util.discard(s); // 丢弃字符串，避免编译器警告
        double expectedCardinality = // 计算预期基数
            expectedCardinality(rowCount, space.columnOrdinals); // 调用方法计算预期基数

        final boolean minimal = nonMinimal == 0 // 判断是否为最小组合
            && !space.unique // 并且不是唯一键
            && !containsKey(space.columnOrdinals); // 并且不包含任何键
        space.expectedCardinality = expectedCardinality; // 设置预期基数
        if (minimal) { // 如果是最小组合
          final Distribution distribution = // 创建分布对象
              new Distribution(space.columns, space.valueSet, space.cardinality, // 参数：列集合、值集合、基数
                  space.nullCount, expectedCardinality, minimal); // 参数：NULL 值数、预期基数、是否最小
          final double surprise = distribution.surprise(); // 计算意外性
          if (CalciteSystemProperty.DEBUG.value() && surprise > 0.1d) { // 如果开启调试且意外性较高
            System.out.println(distribution.columnOrdinals() // 打印调试信息
                + " " + distribution.columns // 列组合
                + ", cardinality: " + distribution.cardinality // 基数
                + ", expected: " + distribution.expectedCardinality // 预期基数
                + ", surprise: " + distribution.surprise()); // 意外性
          }
          if (surprises.offer(surprise)) { // 如果意外性队列接受这个值（足够有趣）
            distributions.put(space.columnOrdinals, distribution); // 将分布信息加入映射
            keyPoset.add(space.columnOrdinals); // 将列组合加入键偏序集合
            doneQueue.add(space); // 将组合加入已完成队列
          }
        }

        if (space.cardinality == rowCount) { // 如果基数等于总行数
          // We have discovered a new key. It is not a super-set of a key.
          // 发现了一个新键，它不是任何键的超集
          uniques.add(new Unique(space.columns)); // 添加到唯一键列表
          keyOrdinalLists.add(space.columnOrdinals); // 添加到键索引列表
          space.unique = true; // 标记为唯一键
        }
      }

      if (pass == 0) { // 如果是第 0 轮
        this.rowCount = rowCount; // 保存总行数
      }
    }

    /** Estimates the cardinality of a collection of columns represented by
     * {@code columnOrdinals}, drawing on existing distributions.
     *
     * 方法作用：估计列组合的基数
     * - 首先检查是否已有该组合的分布信息
     * - 如果有，直接返回实际基数
     * - 如果没有，调用 expectedCardinality 方法进行估计
     *
     * @param rowCount 总行数
     * @param columns 列索引集合
     * @return 估计的基数
     */
    private double cardinality(double rowCount, ImmutableBitSet columns) { // 方法：估计列组合的基数
      final Distribution distribution = distributions.get(columns); // 获取该组合的分布信息
      if (distribution != null) { // 如果存在分布信息
        return distribution.cardinality; // 返回实际基数
      } else { // 如果不存在分布信息
        return expectedCardinality(rowCount, columns); // 调用方法估计基数
      }
    }

    /** Estimates the cardinality of a collection of columns represented by
     * {@code columnOrdinals}, drawing on existing distributions. Does not
     * look in the distribution map for this column set.
     *
     * 方法作用：估计列组合的预期基数（不查找分布映射）
     * - 根据列组合的基数使用不同的估计策略
     * - 0 列：返回 1（只有一行）
     * - 1 列：返回行数（假设每列最多有行数个不同值）
     * - 多列：使用父组合和子组合的信息进行估计
     *
     * 估计策略：
     * - 对于父组合（子集），使用独立性假设：cardinality(A,B) = cardinality(A) * cardinality(B) / rowCount
     * - 对于子组合（超集），基数不超过子组合的基数
     * - 取所有估计的最小值作为最终估计
     *
     * @param rowCount 总行数
     * @param columns 列索引集合
     * @return 估计的预期基数
     */
    private double expectedCardinality(double rowCount, // 方法：估计列组合的预期基数
        ImmutableBitSet columns) { // 参数：总行数、列索引集合
      switch (columns.cardinality()) { // 根据列数选择不同的估计策略
      case 0: // 0 列（空组合）
        return 1d; // 返回 1，表示只有一行（grand total）
      case 1: // 1 列
        return rowCount; // 返回行数，假设最多有行数个不同值
      default: // 多列
        double c = rowCount; // 初始估计为行数
        List<ImmutableBitSet> parents = // 获取所有父组合（子集）
            requireNonNull(keyPoset.getParents(columns, true), // 调用方法获取父组合
                () -> "keyPoset.getParents(columns, true) is null for " // 错误消息
                    + columns); // 列组合
        for (ImmutableBitSet bitSet : parents) { // 遍历所有父组合
          if (bitSet.isEmpty()) { // 如果父组合是空的
            // If the parent is the empty group (i.e. "GROUP BY ()", the grand
            // total) we cannot improve on the estimate.
            // 如果父组合是空的（即 GROUP BY ()，总计），无法改进估计
            continue; // 跳过这个父组合
          }
          final Distribution d1 = distributions.get(bitSet); // 获取父组合的分布信息
          final double c2 = cardinality(rowCount, columns.except(bitSet)); // 估计剩余列的基数
          final double d = // 使用独立性假设估计组合基数
              Lattice.getRowCount(rowCount, // 调用 Lattice 方法
                  requireNonNull(d1, "d1").cardinality, c2); // 参数：行数、父组合基数、剩余列基数
          c = Math.min(c, d); // 取最小值作为当前估计
        }
        List<ImmutableBitSet> children = // 获取所有子组合（超集）
            requireNonNull(keyPoset.getChildren(columns, true), // 调用方法获取子组合
                () -> "keyPoset.getChildren(columns, true) is null for " // 错误消息
                    + columns); // 列组合
        for (ImmutableBitSet bitSet : children) { // 遍历所有子组合
          final Distribution d1 = distributions.get(bitSet); // 获取子组合的分布信息
          c = Math.min(c, requireNonNull(d1, "d1").cardinality); // 取最小值，不超过子组合的基数
        }
        return c; // 返回最终估计
      }
    }


    private ImmutableSortedSet<Column> toColumns(Iterable<Integer> ordinals) { // 方法：将列索引转换为列对象集合
      //noinspection Convert2MethodRef
      return ImmutableSortedSet.copyOf( // 创建不可变排序集合
          Util.transform(ordinals, idx -> columns.get(idx))); // 转换索引为列对象
    }
  }

  /** Work space for a particular combination of columns.
   *
   * 内部类作用：特定列组合的工作空间
   * - 代表一个列组合及其统计信息
   * - 存储该组合的基数、NULL 值数、意外性等信息
   * - 维护该组合与其他组合的依赖关系
   * - 支持意外性计算和分布信息查询
   *
   * 核心字段：
   * - columnOrdinals: 列索引集合，唯一标识该组合
   * - columns: 列对象集合，用于显示和比较
   * - cardinality: 基数（不同值的数量）
   * - nullCount: NULL 值的数量
   * - expectedCardinality: 预期基数（基于独立性假设）
   * - unique: 是否为唯一键
   * - dependencies: 依赖列的位集合
   * - dependents: 依赖该组合的其他组合
   */
  static class Space { // Space 类：特定列组合的工作空间
    private final Run run; // 成员变量：所属的 Run 对象
    final ImmutableBitSet columnOrdinals; // 成员变量：列索引集合，唯一标识该组合
    final ImmutableSortedSet<Column> columns; // 成员变量：列对象集合，排序后便于显示
    boolean unique; // 成员变量：是否为唯一键
    final BitSet dependencies = new BitSet(); // 成员变量：依赖列的位集合，表示哪些列函数依赖于该组合
    final Set<ImmutableBitSet> dependents = new HashSet<>(); // 成员变量：依赖该组合的其他组合集合
    double expectedCardinality; // 成员变量：预期基数，基于独立性假设计算
    @Nullable Collector collector; // 成员变量：收集器，用于收集行的值
    /** Assigned by {@link Collector#finish()}. */
    int nullCount; // 成员变量：NULL 值数量，由 Collector.finish() 赋值
    /** Number of distinct values. Null is counted as a value, if present.
     * Assigned by {@link Collector#finish()}. */
    int cardinality; // 成员变量：不同值的数量（基数），NULL 也算一个值，由 Collector.finish() 赋值
    /** Assigned by {@link Collector#finish()}. */
    @Nullable SortedSet<Comparable> valueSet; // 成员变量：值集合（排序），如果基数较小则存储，否则为 null

    Space(Run run, ImmutableBitSet columnOrdinals, Iterable<Column> columns) { // 构造方法：创建 Space 对象
      this.run = run; // 赋值：所属的 Run 对象
      this.columnOrdinals = columnOrdinals; // 赋值：列索引集合
      this.columns = ImmutableSortedSet.copyOf(columns); // 赋值：列对象集合（不可变排序）
    }

    @Override public int hashCode() { // 方法：计算哈希码
      return columnOrdinals.hashCode(); // 返回列索引集合的哈希码
    }

    @Override public boolean equals(@Nullable Object o) { // 方法：判断是否相等
      return o == this // 如果是同一个对象
          || o instanceof Space // 或者是 Space 类型
          && columnOrdinals.equals(((Space) o).columnOrdinals); // 并且列索引集合相等
    }

    /** Returns the distribution created from this space, or null if no
     * distribution has been registered yet.
     *
     * 方法作用：返回该组合的分布信息
     * - 从 Run 的 distributions 映射中查找
     * - 如果存在则返回，否则返回 null
     *
     * @return 分布信息对象，如果未注册则返回 null
     */
    public @Nullable Distribution distribution() { // 方法：获取该组合的分布信息
      return run.distributions.get(columnOrdinals); // 从映射中查找并返回
    }

    double surprise() { // 方法：计算意外性
      return SimpleProfiler.surprise(expectedCardinality, cardinality); // 调用 SimpleProfiler 方法计算意外性
    }
  }

  /** Builds a {@link org.apache.calcite.profile.ProfilerImpl}.
   *
   * 内部类作用：ProfilerImpl 的构建器
   * - 使用建造者模式创建 ProfilerImpl 实例
   * - 支持链式调用配置参数
   * - 提供合理的默认值
   *
   * 可配置参数：
   * - combinationsPerPass: 每轮最大组合数（默认 100）
   * - predicate: 有趣性判断谓词（默认接受所有）
   *
   * 默认配置：
   * - combinationsPerPass: 100
   * - interestingCount: 200
   * - predicate: p -> true（接受所有组合）
   */
  public static class Builder { // Builder 类：ProfilerImpl 的构建器
    int combinationsPerPass = 100; // 成员变量：每轮最大组合数，默认 100
    Predicate<Pair<Space, Column>> predicate = p -> true; // 成员变量：有趣性判断谓词，默认接受所有

    public ProfilerImpl build() { // 方法：构建 ProfilerImpl 实例
      return new ProfilerImpl(combinationsPerPass, 200, predicate); // 创建实例，interestingCount 固定为 200
    }

    public Builder withPassSize(int passSize) { // 方法：设置每轮最大组合数
      this.combinationsPerPass = passSize; // 赋值
      return this; // 返回 this，支持链式调用
    }

    public Builder withMinimumSurprise(double v) { // 方法：设置最小意外性（当前实现未使用）
      predicate = // 创建新的谓词
          spaceColumnPair -> { // 谓词函数
            @SuppressWarnings("unused")
            final Space space = spaceColumnPair.left; // 获取 Space 对象（未使用）
            return false; // 返回 false，不接受任何组合（这是一个占位实现）
          };
      return this; // 返回 this，支持链式调用
    }
  }

  /** Collects values of a column or columns.
   *
   * 抽象类作用：收集列或列组合的值
   * - 定义了收集器的基本接口
   * - 支持单列和多列两种收集模式
   * - 提供工厂方法创建适当的收集器类型
   *
   * 两种收集策略：
   * 1. 精确收集：使用 TreeSet 存储所有不同的值
   *    - 优点：精确，可以获取所有不同的值
   *    - 缺点：内存消耗大，不适合高基数列
   * 2. 近似收集：使用 HyperLogLog sketch 估计基数
   *    - 优点：内存消耗小，适合高基数列
   *    - 缺点：只能估计基数，无法获取具体的值
   *
   * 动态切换：
   * - 当不同值数量达到阈值（默认 1000）时，自动切换到 HyperLogLog 模式
   */
  abstract static class Collector { // Collector 抽象类：收集列或列组合的值
    protected final Space space; // 成员变量：所属的 Space 对象

    Collector(Space space) { // 构造方法：创建收集器
      this.space = space; // 赋值：所属的 Space 对象
    }

    abstract void add(List<Comparable> row); // 抽象方法：添加一行数据到收集器
    abstract void finish(); // 抽象方法：完成收集，计算统计信息

    /** Creates an initial collector of the appropriate kind.
     *
     * 方法作用：创建适当类型的收集器
     * - 根据列组合的列数选择收集器类型
     * - 单列：使用 SingletonCollector
     * - 多列：使用 CompositeCollector
     * - 所有收集器初始都使用精确收集模式
     * - 当达到阈值时会自动切换到 HyperLogLog 模式
     *
     * @param space 列组合的工作空间
     * @param sketchThreshold 切换到 HyperLogLog 的阈值
     * @return 新创建的收集器实例
     */
    public static Collector create(Space space, int sketchThreshold) { // 静态方法：创建收集器
      final List<Integer> columnOrdinalList = space.columnOrdinals.asList(); // 获取列索引列表
      if (columnOrdinalList.size() == 1) { // 如果是单列
        return new SingletonCollector(space, columnOrdinalList.get(0), // 创建单列收集器
            sketchThreshold); // 参数：空间、列索引、阈值
      } else { // 如果是多列
        return new CompositeCollector(space, // 创建多列收集器
            (int[]) Primitive.INT.toArray(columnOrdinalList), sketchThreshold); // 参数：空间、列索引数组、阈值
      }
    }
  }

  /** Collector that collects values of a single column.
   *
   * 内部类作用：单列收集器
   * - 使用 NavigableSet（TreeSet）存储不同的值
   * - 当值数量达到阈值时，自动切换到 HllSingletonCollector
   * - 精确统计 NULL 值数量
   *
   * 工作流程：
   * 1. 使用 TreeSet 存储所有非 NULL 值
   * 2. 单独计数 NULL 值
   * 3. 当 TreeSet 大小达到阈值时，切换到 HyperLogLog 模式
   * 4. 完成收集后，计算基数和 NULL 值数
   *
   * 优点：
   * - 精确，可以获取所有不同的值
   * - 值自动排序，便于显示和比较
   *
   * 缺点：
   * - 内存消耗大，不适合高基数列
   */
  static class SingletonCollector extends Collector { // SingletonCollector 类：单列收集器
    final NavigableSet<Comparable> values = new TreeSet<>(); // 成员变量：值集合（排序），存储所有非 NULL 值
    final int columnOrdinal; // 成员变量：列索引
    final int sketchThreshold; // 成员变量：切换到 HyperLogLog 的阈值
    int nullCount = 0; // 成员变量：NULL 值计数器

    SingletonCollector(Space space, int columnOrdinal, int sketchThreshold) { // 构造方法：创建单列收集器
      super(space); // 调用父类构造方法
      this.columnOrdinal = columnOrdinal; // 赋值：列索引
      this.sketchThreshold = sketchThreshold; // 赋值：阈值
    }

    @Override public void add(List<Comparable> row) { // 方法：添加一行数据
      final Comparable v = row.get(columnOrdinal); // 获取该列的值
      if (v == NullSentinel.INSTANCE) { // 如果是 NULL 值
        nullCount++; // NULL 计数加 1
      } else { // 如果是非 NULL 值
        if (values.add(v) && values.size() == sketchThreshold) { // 添加值到集合，如果达到阈值
          // Too many values. Switch to a sketch collector.
          // 值太多，切换到草图收集器
          final HllSingletonCollector collector = // 创建 HyperLogLog 单列收集器
              new HllSingletonCollector(space, columnOrdinal); // 参数：空间、列索引
          for (Comparable value : values) { // 遍历所有已收集的值
            collector.add(value); // 将值添加到新收集器
          }
          space.collector = collector; // 替换收集器引用
        }
      }
    }

    @Override public void finish() { // 方法：完成收集
      space.nullCount = nullCount; // 设置 NULL 值数量
      space.cardinality = values.size() + (nullCount > 0 ? 1 : 0); // 设置基数：非 NULL 值数 + (如果有 NULL 则 +1)
      space.valueSet = values.size() < 20 ? values : null; // 如果值少于 20 个，保存值集合；否则为 null
    }
  }

  /** Collector that collects two or more column values in a tree set.
   *
   * 内部类作用：多列收集器
   * - 使用 HashSet 存储 FlatLists.ComparableList（扁平列表）
   * - 当值数量达到阈值时，自动切换到 HllCompositeCollector
   * - 精确统计包含 NULL 或部分 NULL 的行数
   *
   * 工作流程：
   * 1. 从每行提取指定列的值，组成 ComparableList
   * 2. 使用 HashSet 存储，自动去重
   * 3. 统计包含 NULL 或部分 NULL 的行数
   * 4. 当 HashSet 大小达到阈值时，切换到 HyperLogLog 模式
   * 5. 完成收集后，计算基数和 NULL 值数
   *
   * 数据结构：
   * - FlatLists.ComparableList: 高效的不可变列表实现
   * - HashSet: 快速去重
   *
   * 优点：
   * - 精确，可以获取所有不同的组合
   * - 内存消耗比单列收集器更合理
   *
   * 缺点：
   * - 仍然不适合极高基数的列组合
   */
  static class CompositeCollector extends Collector { // CompositeCollector 类：多列收集器
    protected static final ImmutableBitSet OF = ImmutableBitSet.of(2, 13); // 静态常量：用于调试的特殊列组合
    final Set<FlatLists.ComparableList> values = new HashSet<>(); // 成员变量：值集合，存储列值组合
    final int[] columnOrdinals; // 成员变量：列索引数组
    final Comparable[] columnValues; // 成员变量：列值数组，用于临时存储
    int nullCount = 0; // 成员变量：包含 NULL 或部分 NULL 的行数
    private final int sketchThreshold; // 成员变量：切换到 HyperLogLog 的阈值

    CompositeCollector(Space space, int[] columnOrdinals, int sketchThreshold) { // 构造方法：创建多列收集器
      super(space); // 调用父类构造方法
      this.columnOrdinals = columnOrdinals; // 赋值：列索引数组
      this.columnValues = new Comparable[columnOrdinals.length]; // 赋值：列值数组
      this.sketchThreshold = sketchThreshold; // 赋值：阈值
    }

    @Override public void add(List<Comparable> row) { // 方法：添加一行数据
      if (space.columnOrdinals.equals(OF)) { // 如果是调试用的特殊列组合
        Util.discard(0); // 丢弃，用于断点调试
      }
      int nullCountThisRow = 0; // 初始化本行 NULL 计数器
      for (int i = 0, length = columnOrdinals.length; i < length; i++) { // 遍历所有列
        final Comparable value = row.get(columnOrdinals[i]); // 获取该列的值
        if (value == NullSentinel.INSTANCE) { // 如果是 NULL 值
          if (nullCountThisRow++ == 0) { // 如果这是本行的第一个 NULL
            nullCount++; // NULL 行数加 1
          }
        }
        columnValues[i] = value; // 保存列值到数组
      }
      //noinspection unchecked
      if (((Set) values).add(FlatLists.copyOf(columnValues)) // 添加值组合到集合，如果达到阈值
          && values.size() == sketchThreshold) { // 检查是否达到阈值
        // Too many values. Switch to a sketch collector.
        // 值太多，切换到草图收集器
        final HllCompositeCollector collector = // 创建 HyperLogLog 多列收集器
            new HllCompositeCollector(space, columnOrdinals); // 参数：空间、列索引数组
        final List<Comparable> list = // 创建列表，用于迁移数据
            new ArrayList<>( // 创建动态数组列表
                Collections.nCopies(columnOrdinals[columnOrdinals.length - 1] // 初始化列表大小
                        + 1, // 最大列索引 + 1
                    null)); // 初始值为 null
        for (FlatLists.ComparableList value : this.values) { // 遍历所有已收集的值组合
          for (int i = 0; i < value.size(); i++) { // 遍历每个值
            Comparable c = (Comparable) value.get(i); // 获取值
            list.set(columnOrdinals[i], c); // 将值设置到列表的对应位置
          }
          collector.add(list); // 将列表添加到新收集器
        }
        space.collector = collector; // 替换收集器引用
      }
    }

    @Override public void finish() { // 方法：完成收集
      // number of input rows (not distinct values)
      // that were null or partially null
      // 输入行数（不是不同值）
      // 包含 NULL 或部分 NULL 的行数
      space.nullCount = nullCount; // 设置 NULL 行数
      space.cardinality = values.size() + (nullCount > 0 ? 1 : 0); // 设置基数：非 NULL 组合数 + (如果有 NULL 行则 +1)
      space.valueSet = null; // 多列组合不保存值集合（内存消耗太大）
    }

  }

  /** Collector that collects two or more column values into a HyperLogLog
   * sketch.
   *
   * 抽象类作用：使用 HyperLogLog 草图收集列值
   * - HyperLogLog 是一种概率算法，用于估计基数
   * - 内存消耗固定且很小（约 12KB）
   * - 适合高基数列或列组合
   * - 只能估计基数，无法获取具体的值
   *
   * HyperLogLog 原理：
   * - 使用哈希函数将值映射到随机位置
   * - 统计前导零的个数，估计基数
   * - 误差率约为 1.04/sqrt(m)，其中 m 是寄存器数量
   *
   * 优点：
   * - 内存消耗小且固定
   * - 适合高基数数据
   * - 计算速度快
   *
   * 缺点：
   * - 只能估计基数，有误差
   * - 无法获取具体的值
   */
  abstract static class HllCollector extends Collector { // HllCollector 抽象类：HyperLogLog 收集器
    final HllSketch sketch; // 成员变量：HyperLogLog 草图对象
    int nullCount = 0; // 成员变量：NULL 值计数器

    static final long[] NULL_BITS = {0x9f77d57e93167a16L}; // 静态常量：NULL 值的特殊哈希位

    HllCollector(Space space) { // 构造方法：创建 HyperLogLog 收集器
      super(space); // 调用父类构造方法
      this.sketch = HllSketch.builder().build(); // 创建 HyperLogLog 草图对象
    }

    protected void add(Comparable value) { // 方法：添加一个值到草图
      if (value == NullSentinel.INSTANCE) { // 如果是 NULL 值
        sketch.update(NULL_BITS); // 使用特殊的哈希位更新草图
      } else if (value instanceof String) { // 如果是字符串
        sketch.update((String) value); // 直接更新字符串
      } else if (value instanceof Double) { // 如果是 Double
        sketch.update((Double) value); // 直接更新 Double
      } else if (value instanceof Float) { // 如果是 Float
        sketch.update((Float) value); // 直接更新 Float
      } else if (value instanceof Long) { // 如果是 Long
        sketch.update((Long) value); // 直接更新 Long
      } else if (value instanceof Number) { // 如果是其他数字类型
        sketch.update(((Number) value).longValue()); // 转换为 long 后更新
      } else { // 其他类型
        sketch.update(value.toString()); // 转换为字符串后更新
      }
    }

    @Override public void finish() { // 方法：完成收集
      space.nullCount = nullCount; // 设置 NULL 值数量
      space.cardinality = (int) sketch.getEstimate(); // 设置基数：从草图获取估计值
      space.valueSet = null; // 不保存值集合（HyperLogLog 无法获取具体值）
    }
  }

  /** Collector that collects one column value into a HyperLogLog sketch.
   *
   * 内部类作用：单列 HyperLogLog 收集器
   * - 继承自 HllCollector
   * - 专门用于单列的基数估计
   * - 从行数据中提取指定列的值
   *
   * 工作流程：
   * 1. 从每行提取指定列的值
   * 2. 如果是 NULL，使用特殊哈希位
   * 3. 调用父类的 add 方法更新草图
   * 4. 完成收集后，获取估计的基数
   */
  static class HllSingletonCollector extends HllCollector { // HllSingletonCollector 类：单列 HyperLogLog 收集器
    final int columnOrdinal; // 成员变量：列索引

    HllSingletonCollector(Space space, int columnOrdinal) { // 构造方法：创建单列 HyperLogLog 收集器
      super(space); // 调用父类构造方法
      this.columnOrdinal = columnOrdinal; // 赋值：列索引
    }

    @Override public void add(List<Comparable> row) { // 方法：添加一行数据
      final Comparable value = row.get(columnOrdinal); // 获取该列的值
      if (value == NullSentinel.INSTANCE) { // 如果是 NULL 值
        nullCount++; // NULL 计数加 1
        sketch.update(NULL_BITS); // 使用特殊哈希位更新草图
      } else { // 如果是非 NULL 值
        add(value); // 调用父类方法更新草图
      }
    }
  }

  /** Collector that collects two or more column values into a HyperLogLog
   * sketch.
   *
   * 内部类作用：多列 HyperLogLog 收集器
   * - 继承自 HllCollector
   * - 专门用于多列组合的基数估计
   * - 将多列值序列化为字节数组后更新草图
   *
   * 工作流程：
   * 1. 从每行提取指定列的值
   * 2. 将值序列化为字节数组（使用 ByteBuffer）
   * 3. 每种类型使用不同的类型标识符
   * 4. 统计包含 NULL 或部分 NULL 的行数
   * 5. 将字节数组更新到草图
   *
   * 序列化格式：
   * - NULL: 0x00
   * - String: 0x01 + UTF-8 bytes
   * - Double: 0x02 + 8 bytes
   * - Float: 0x03 + 4 bytes
   * - Long: 0x04 + 8 bytes
   * - Integer: 0x05 + 4 bytes
   * - Boolean true: 0x06
   * - Boolean false: 0x07
   * - Other: 0x08 + UTF-8 bytes
   *
   * 优点：
   * - 可以处理任意类型的列值
   * - 内存消耗小
   * - 适合高基数多列组合
   */
  static class HllCompositeCollector extends HllCollector { // HllCompositeCollector 类：多列 HyperLogLog 收集器
    private final int[] columnOrdinals; // 成员变量：列索引数组
    private final ByteBuffer buf = ByteBuffer.allocate(1024); // 成员变量：字节缓冲区，用于序列化

    HllCompositeCollector(Space space, int[] columnOrdinals) { // 构造方法：创建多列 HyperLogLog 收集器
      super(space); // 调用父类构造方法
      this.columnOrdinals = columnOrdinals; // 赋值：列索引数组
    }

    @Override public void add(List<Comparable> row) { // 方法：添加一行数据
      if (space.columnOrdinals.equals(OF)) { // 如果是调试用的特殊列组合
        Util.discard(0); // 丢弃，用于断点调试
      }
      int nullCountThisRow = 0; // 初始化本行 NULL 计数器
      buf.clear(); // 清空缓冲区
      for (int columnOrdinal : columnOrdinals) { // 遍历所有列
        final Comparable value = row.get(columnOrdinal); // 获取该列的值
        if (value == NullSentinel.INSTANCE) { // 如果是 NULL 值
          if (nullCountThisRow++ == 0) { // 如果这是本行的第一个 NULL
            nullCount++; // NULL 行数加 1
          }
          buf.put((byte) 0); // 写入 NULL 类型标识符 0x00
        } else if (value instanceof String) { // 如果是字符串
          buf.put((byte) 1) // 写入字符串类型标识符 0x01
              .put(((String) value).getBytes(StandardCharsets.UTF_8)); // 写入 UTF-8 编码的字节
        } else if (value instanceof Double) { // 如果是 Double
          buf.put((byte) 2).putDouble((Double) value); // 写入 Double 类型标识符 0x02 和 8 字节值
        } else if (value instanceof Float) { // 如果是 Float
          buf.put((byte) 3).putFloat((Float) value); // 写入 Float 类型标识符 0x03 和 4 字节值
        } else if (value instanceof Long) { // 如果是 Long
          buf.put((byte) 4).putLong((Long) value); // 写入 Long 类型标识符 0x04 和 8 字节值
        } else if (value instanceof Integer) { // 如果是 Integer
          buf.put((byte) 5).putInt((Integer) value); // 写入 Integer 类型标识符 0x05 和 4 字节值
        } else if (value instanceof Boolean) { // 如果是 Boolean
          buf.put((Boolean) value ? (byte) 6 : (byte) 7); // 写入 Boolean 类型标识符 0x06 或 0x07
        } else { // 其他类型
          buf.put((byte) 8) // 写入其他类型标识符 0x08
              .put(value.toString().getBytes(StandardCharsets.UTF_8)); // 转换为字符串后写入 UTF-8 字节
        }
      }
      sketch.update(Arrays.copyOf(buf.array(), buf.position())); // 将缓冲区的有效部分复制到数组并更新草图
    }
  }

  /** A priority queue of the last N surprise values. Accepts a new value if
   * the queue is not yet full, or if its value is greater than the median value
   * over the last N.
   *
   * 内部类作用：意外性优先队列
   * - 维护最后 N 个意外值
   * - 在预热阶段（warmUpCount），接受所有值
   * - 预热后，只接受大于当前最小值的意外值
   * - 使用优先队列和双端队列实现
   *
   * 工作原理：
   * - priorityQueue: 最小堆，存储所有意外值，快速获取最小值
   * - deque: 双端队列，维护插入顺序，用于移除最旧的值
   * - 在预热阶段，所有值都被接受
   * - 预热后，只有大于当前最小值的值才被接受
   * - 当队列满时，移除最旧的值
   *
   * 应用场景：
   * - 过滤不有趣的列组合
   * - 只保留最有趣的 N 个组合
   * - 动态调整有趣性阈值
   *
   * 优点：
   * - 内存消耗固定（只保存 N 个值）
   * - 可以动态调整阈值
   * - 适合流式数据
   */
  static class SurpriseQueue { // SurpriseQueue 类：意外性优先队列
    private final int warmUpCount; // 成员变量：预热数量，在此数量之前接受所有值
    private final int size; // 成员变量：队列大小，最大保存的意外值数量
    int count = 0; // 成员变量：计数器，记录已处理的意外值数量
    final Deque<Double> deque = new ArrayDeque<>(); // 成员变量：双端队列，维护插入顺序
    final PriorityQueue<Double> priorityQueue = // 成员变量：优先队列（最小堆），快速获取最小值
        new PriorityQueue<>(11, Ordering.natural()); // 初始容量 11，自然排序

    SurpriseQueue(int warmUpCount, int size) { // 构造方法：创建意外性队列
      this.warmUpCount = warmUpCount; // 赋值：预热数量
      this.size = size; // 赋值：队列大小
      checkArgument(warmUpCount > 3); // 参数校验：预热数量必须大于 3
      checkArgument(size > 0); // 参数校验：队列大小必须大于 0
    }

    @Override public String toString() { // 方法：转换为字符串（用于调试）
      return "min: " + priorityQueue.peek() // 最小值
          + ", contents: " + deque.toString(); // 队列内容
    }

    boolean isValid() { // 方法：验证队列状态（用于调试）
      if (CalciteSystemProperty.DEBUG.value()) { // 如果开启调试模式
        System.out.println(toString()); // 打印队列状态
      }
      assert deque.size() == priorityQueue.size(); // 断言：两个队列大小相等
      if (count > size) { // 如果计数超过队列大小
        assert deque.size() == size; // 断言：双端队列大小等于队列大小
      }
      return true; // 返回 true，表示有效
    }

    boolean offer(double d) { // 方法：尝试添加一个意外值
      boolean b; // 返回值：是否接受该值
      if (count++ < warmUpCount || d > castNonNull(priorityQueue.peek())) { // 如果在预热阶段或值大于当前最小值
        if (priorityQueue.size() >= size) { // 如果优先队列已满
          priorityQueue.remove(deque.pop()); // 移除最旧的值
        }
        priorityQueue.add(d); // 添加新值到优先队列
        deque.add(d); // 添加新值到双端队列
        b = true; // 接受该值
      } else { // 如果不在预热阶段且值不大于当前最小值
        b = false; // 拒绝该值
      }
      if (CalciteSystemProperty.DEBUG.value()) { // 如果开启调试模式
        System.out.println("offer " + d // 打印调试信息
            + " min " + priorityQueue.peek() // 当前最小值
            + " accepted " + b); // 是否接受
      }
      return b; // 返回是否接受
    }
  }
}
