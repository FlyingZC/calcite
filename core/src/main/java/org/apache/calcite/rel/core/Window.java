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
package org.apache.calcite.rel.core; // 定义包名，表示这个类属于 org.apache.calcite.rel.core 包

import org.apache.calcite.linq4j.Ord; // 导入 Ord 类，用于为列表元素提供索引
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，表示关系表达式集群
import org.apache.calcite.plan.RelOptCost; // 导入 RelOptCost 类，表示关系表达式的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入 RelOptPlanner 类，表示关系优化规划器
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系特征集合
import org.apache.calcite.rel.RelCollation; // 导入 RelCollation 类，表示排序规则
import org.apache.calcite.rel.RelCollations; // 导入 RelCollations 类，提供排序规则的工具方法
import org.apache.calcite.rel.RelFieldCollation; // 导入 RelFieldCollation 类，表示字段排序
import org.apache.calcite.rel.RelNode; // 导入 RelNode 类，表示关系表达式节点
import org.apache.calcite.rel.RelWriter; // 导入 RelWriter 类，用于输出关系表达式信息
import org.apache.calcite.rel.SingleRel; // 导入 SingleRel 类，表示只有一个子节点的关系表达式
import org.apache.calcite.rel.hint.Hintable; // 导入 Hintable 接口，表示支持提示的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入 RelHint 类，表示关系表达式提示
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery 类，用于查询元数据
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 类，表示关系数据类型
import org.apache.calcite.rex.RexCall; // 导入 RexCall 类，表示 Rex 节点调用
import org.apache.calcite.rex.RexChecker; // 导入 RexChecker 类，用于检查 Rex 节点的有效性
import org.apache.calcite.rex.RexFieldCollation; // 导入 RexFieldCollation 类，表示 Rex 字段排序
import org.apache.calcite.rex.RexLiteral; // 导入 RexLiteral 类，表示 Rex 字面量
import org.apache.calcite.rex.RexLocalRef; // 导入 RexLocalRef 类，表示 Rex 本地引用
import org.apache.calcite.rex.RexNode; // 导入 RexNode 类，表示 Rex 表达式节点
import org.apache.calcite.rex.RexSlot; // 导入 RexSlot 类，表示 Rex 槽位（字段引用）
import org.apache.calcite.rex.RexWindowBound; // 导入 RexWindowBound 类，表示窗口边界
import org.apache.calcite.rex.RexWindowExclusion; // 导入 RexWindowExclusion 类，表示窗口排除子句
import org.apache.calcite.sql.SqlAggFunction; // 导入 SqlAggFunction 类，表示 SQL 聚合函数
import org.apache.calcite.util.ImmutableBitSet; // 导入 ImmutableBitSet 类，表示不可变位集合
import org.apache.calcite.util.ImmutableIntList; // 导入 ImmutableIntList 类，表示不可变整数列表
import org.apache.calcite.util.Litmus; // 导入 Litmus 类，用于验证测试
import org.apache.calcite.util.Util; // 导入 Util 类，提供工具方法

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类，表示不可变列表

import org.checkerframework.checker.initialization.qual.UnderInitialization; // 导入 CheckerFramework 的初始化检查注解
import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的可空注解
import org.checkerframework.checker.nullness.qual.RequiresNonNull; // 导入 CheckerFramework 的非空要求注解

import java.util.AbstractList; // 导入 AbstractList 类，表示抽象列表
import java.util.Collections; // 导入 Collections 类，提供集合工具方法
import java.util.List; // 导入 List 接口，表示列表

import static java.util.Objects.hash; // 导入 Objects 的 hash 静态方法
import static java.util.Objects.requireNonNull; // 导入 Objects 的 requireNonNull 静态方法

/**
 * A relational expression representing a set of window aggregates. // 表示窗口聚合集合的关系表达式
 *
 * <p>A Window can handle several window aggregate functions, over several // Window 可以处理多个窗口聚合函数，跨越多个分区
 * partitions, with pre- and post-expressions, and an optional post-filter. // 包含前置和后置表达式，以及可选的后置过滤器
 * Each of the partitions is defined by a partition key (zero or more columns) // 每个分区由分区键（零个或多个列）定义
 * and a range (logical or physical). The partitions expect the data to be // 和范围（逻辑或物理）定义。分区要求数据在输入到关系表达式时已正确排序
 * sorted correctly on input to the relational expression.
 *
 * <p>Each {@link Window.Group} has a set of // 每个 Window.Group 包含一组
 * {@link org.apache.calcite.rex.RexOver} objects. // RexOver 对象（窗口函数调用）
 *
 * <p>Created by {@link org.apache.calcite.rel.rules.ProjectToWindowRule}. // 由 ProjectToWindowRule 规则创建
 */
public abstract class Window extends SingleRel implements Hintable { // Window 抽象类，继承自 SingleRel 并实现 Hintable 接口
  public final ImmutableList<Group> groups; // 窗口组列表，每个组包含具有相同窗口规范的窗口聚合调用
  public final ImmutableList<RexLiteral> constants; // 常量列表，这些常量是额外的输入
  protected final ImmutableList<RelHint> hints; // 提示列表，用于优化器的提示信息

  /**
   * Creates a window relational expression. // 创建一个窗口关系表达式
   *
   * @param cluster Cluster // 关系表达式集群，包含类型工厂等共享资源
   * @param traitSet Trait set // 关系特征集合，定义物理属性如排序、分布等
   * @param hints   Hints for this node // 此节点的提示列表，用于指导优化器
   * @param input   Input relational expression // 输入关系表达式
   * @param constants List of constants that are additional inputs // 常量列表，作为额外的输入
   * @param rowType Output row type // 输出行类型，定义输出行的结构
   * @param groups Windows // 窗口组列表，每个组包含窗口聚合调用
   */
  protected Window(RelOptCluster cluster, RelTraitSet traitSet, List<RelHint> hints, // 受保护的构造方法，创建 Window 实例
      RelNode input, List<RexLiteral> constants, RelDataType rowType, List<Group> groups) { // 参数包括集群、特征集、提示、输入、常量、行类型和窗口组
    super(cluster, traitSet, input); // 调用父类 SingleRel 的构造方法
    this.constants = ImmutableList.copyOf(constants); // 将常量列表转换为不可变列表并赋值
    this.rowType = requireNonNull(rowType, "rowType"); // 设置行类型，要求非空
    this.groups = ImmutableList.copyOf(groups); // 将窗口组列表转换为不可变列表并赋值
    this.hints = ImmutableList.copyOf(hints); // 将提示列表转换为不可变列表并赋值
  }

  /**
   * Creates a window relational expression. // 创建一个窗口关系表达式（无提示版本）
   *
   * @param cluster Cluster // 关系表达式集群
   * @param traitSet Trait set // 关系特征集合
   * @param input   Input relational expression // 输入关系表达式
   * @param constants List of constants that are additional inputs // 常量列表
   * @param rowType Output row type // 输出行类型
   * @param groups Windows // 窗口组列表
   */
  public Window(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 公共构造方法，创建无提示的 Window 实例
      List<RexLiteral> constants, RelDataType rowType, List<Group> groups) { // 参数包括集群、特征集、输入、常量、行类型和窗口组
    this(cluster, traitSet, Collections.emptyList(), input, constants, rowType, groups); // 调用主构造方法，传入空的提示列表
  }

  /**
   * Creates a copy of this {@code Window}. // 创建此 Window 的副本
   *
   * @param constants Replaces the list of constants in the returned copy // 替换返回副本中的常量列表
   * @return New {@code Window} // 新的 Window 实例
   */
  public abstract Window copy(List<RexLiteral> constants); // 抽象方法，用于创建 Window 的副本，可以指定新的常量列表

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) { // 重写 isValid 方法，验证关系表达式的有效性
    // In the window specifications, an aggregate call such as // 在窗口规范中，聚合调用如
    // 'SUM(RexInputRef #10)' refers to expression #10 of inputProgram. // 'SUM(RexInputRef #10)' 引用 inputProgram 的表达式 #10
    // (Not its projections.) // （不是它的投影）
    final RelDataType childRowType = getInput().getRowType(); // 获取子节点的行类型

    final int childFieldCount = childRowType.getFieldCount(); // 获取子节点的字段数量
    final int inputSize = childFieldCount + constants.size(); // 计算输入大小（子字段数 + 常量数）
    final List<RelDataType> inputTypes = // 创建输入类型列表
        new AbstractList<RelDataType>() { // 使用匿名抽象列表类
          @Override public RelDataType get(int index) { // 重写 get 方法，根据索引获取类型
            return index < childFieldCount // 如果索引小于子字段数
                ? childRowType.getFieldList().get(index).getType() // 返回子字段的类型
                : constants.get(index - childFieldCount).getType(); // 否则返回常量的类型
          }

          @Override public int size() { // 重写 size 方法，返回列表大小
            return inputSize; // 返回输入大小
          }
        };

    final RexChecker checker = new RexChecker(inputTypes, context, litmus); // 创建 Rex 检查器，用于验证 Rex 节点
    int count = 0; // 初始化计数器
    for (Group group : groups) { // 遍历所有窗口组
      for (RexWinAggCall over : group.aggCalls) { // 遍历组中的所有窗口聚合调用
        ++count; // 增加计数
        if (!checker.isValid(over)) { // 检查窗口聚合调用是否有效
          return litmus.fail(null); // 如果无效，返回失败
        }
      }
    }
    if (count == 0) { // 如果计数为 0（没有聚合调用）
      return litmus.fail("empty"); // 返回失败，提示为空
    }
    return litmus.succeed(); // 返回成功
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写 explainTerms 方法，输出关系表达式的详细信息
    super.explainTerms(pw); // 调用父类的 explainTerms 方法
    for (Ord<Group> window : Ord.zip(groups)) { // 遍历所有窗口组（带索引）
      pw.item("window#" + window.i, window.e.toString()); // 输出每个窗口组的信息
    }
    if (this.constants != null && this.constants.size() > 0) { // 如果常量列表不为空
      pw.item("constants", constants); // 输出常量信息
    }
    return pw; // 返回 RelWriter
  }

  public static ImmutableIntList getProjectOrdinals(final List<RexNode> exprs) { // 静态方法，从 Rex 节点列表中提取投影序号
    return ImmutableIntList.copyOf( // 返回不可变整数列表
        new AbstractList<Integer>() { // 创建匿名抽象列表
          @Override public Integer get(int index) { // 重写 get 方法，获取指定索引的序号
            return ((RexSlot) exprs.get(index)).getIndex(); // 将 Rex 节点转换为 RexSlot 并获取其索引
          }

          @Override public int size() { // 重写 size 方法，返回列表大小
            return exprs.size(); // 返回表达式列表的大小
          }
        });
  }

  public static RelCollation getCollation( // 静态方法，从 Rex 字段排序列表中获取排序规则
      final List<RexFieldCollation> collations) { // 参数：Rex 字段排序列表
    return RelCollations.of( // 返回 RelCollation 对象
        new AbstractList<RelFieldCollation>() { // 创建匿名抽象列表
          @Override public RelFieldCollation get(int index) { // 重写 get 方法
            final RexFieldCollation collation = collations.get(index); // 获取指定索引的 Rex 字段排序
            return new RelFieldCollation( // 创建新的 RelFieldCollation 对象
                ((RexLocalRef) collation.left).getIndex(), // 从 RexLocalRef 中提取字段索引
                collation.getDirection(), // 获取排序方向
                collation.getNullDirection()); // 获取空值排序方向
          }

          @Override public int size() { // 重写 size 方法
            return collations.size(); // 返回排序列表的大小
          }
        });
  }

  /**
   * Returns constants that are additional inputs of current relation. // 返回当前关系的额外输入常量
   *
   * @return constants that are additional inputs of current relation // 当前关系的额外输入常量列表
   */
  public List<RexLiteral> getConstants() { // 获取常量列表
    return constants; // 返回常量列表
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写 computeSelfCost 方法，计算关系表达式的成本
      RelMetadataQuery mq) { // 参数：优化规划器和元数据查询
    // Cost is proportional to the number of rows and the number of // 成本与行数和组件数（组和聚合函数）成正比
    // components (groups and aggregate functions). There is // 没有 I/O 成本
    // no I/O cost.
    //
    // TODO #1. Add memory cost. // TODO #1: 添加内存成本
    // TODO #2. MIN and MAX have higher CPU cost than SUM and COUNT. // TODO #2: MIN 和 MAX 的 CPU 成本比 SUM 和 COUNT 高
    final double rowsIn = mq.getRowCount(getInput()); // 获取输入的行数
    int count = groups.size(); // 初始化计数为窗口组数量
    for (Group group : groups) { // 遍历所有窗口组
      count += group.aggCalls.size(); // 加上每个组中的聚合调用数量
    }
    return planner.getCostFactory().makeCost(rowsIn, rowsIn * count, 0); // 创建成本对象：CPU 成本 = 行数 * 组件数，I/O 成本为 0
  }

  /**
   * Group of windowed aggregate calls that have the same window specification. // 具有相同窗口规范的窗口聚合调用组
   *
   * <p>The specification is defined by an upper and lower bound, exclusion clause, // 规范由上界、下界、排除子句定义
   * and also has zero or more partitioning columns. // 并且有零个或多个分区列
   *
   * <p>A window is either logical or physical. A physical window is measured // 窗口可以是逻辑的或物理的。物理窗口按行数测量
   * in terms of row count. A logical window is measured in terms of rows // 逻辑窗口按与当前排序键一定距离内的行测量
   * within a certain distance from the current sort key.
   *
   * <p>For example: // 例如：
   *
   * <ul>
   * <li><code>ROWS BETWEEN 10 PRECEDING and 5 FOLLOWING</code> is a physical // <code>ROWS BETWEEN 10 PRECEDING and 5 FOLLOWING</code> 是一个物理窗口
   * window with an upper and lower bound; // 具有上界和下界
   * <li><code>RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND UNBOUNDED // <code>RANGE BETWEEN INTERVAL '1' HOUR PRECEDING AND UNBOUNDED
   * FOLLOWING</code> is a logical window with only a lower bound; // FOLLOWING</code> 是一个只有下界的逻辑窗口
   * <li><code>RANGE INTERVAL '10' MINUTES PRECEDING</code> (which is // <code>RANGE INTERVAL '10' MINUTES PRECEDING</code>（等同于
   * equivalent to <code>RANGE BETWEEN INTERVAL '10' MINUTES PRECEDING AND // <code>RANGE BETWEEN INTERVAL '10' MINUTES PRECEDING AND
   * CURRENT ROW</code>) is a logical window with an upper and lower bound. // CURRENT ROW</code>）是一个具有上界和下界的逻辑窗口
   * </ul>
   */
  public static class Group { // 静态内部类，表示窗口组
    public final ImmutableBitSet keys; // 分区键的位集合，表示用于分区的列
    public final boolean isRows; // 是否为物理窗口（ROWS），false 表示逻辑窗口（RANGE）
    public final RexWindowBound lowerBound; // 窗口下界
    public final RexWindowBound upperBound; // 窗口上界
    public final RexWindowExclusion exclude; // 窗口排除子句
    public final RelCollation orderKeys; // 排序键
    private final String digest; // 窗口组的字符串表示（摘要）

    /**
     * List of {@link Window.RexWinAggCall} // Window.RexWinAggCall 对象列表
     * objects, each of which is a call to a // 每个对象都是对
     * {@link org.apache.calcite.sql.SqlAggFunction}. // SqlAggFunction 的调用
     */
    public final ImmutableList<RexWinAggCall> aggCalls; // 窗口聚合调用列表

    public Group( // Group 构造方法
        ImmutableBitSet keys, // 分区键位集合
        boolean isRows, // 是否为物理窗口
        RexWindowBound lowerBound, // 窗口下界
        RexWindowBound upperBound, // 窗口上界
        RexWindowExclusion exclude, // 窗口排除子句
        RelCollation orderKeys, // 排序键
        List<RexWinAggCall> aggCalls) { // 窗口聚合调用列表
      this.keys = requireNonNull(keys, "keys"); // 设置分区键，要求非空
      this.isRows = isRows; // 设置是否为物理窗口
      this.lowerBound = requireNonNull(lowerBound, "lowerBound"); // 设置窗口下界，要求非空
      this.upperBound = requireNonNull(upperBound, "upperBound"); // 设置窗口上界，要求非空
      this.exclude = exclude; // 设置窗口排除子句
      this.orderKeys = requireNonNull(orderKeys, "orderKeys"); // 设置排序键，要求非空
      this.aggCalls = ImmutableList.copyOf(aggCalls); // 将聚合调用列表转换为不可变列表
      this.digest = computeString(); // 计算窗口组的字符串表示
    }

    @Override public String toString() { // 重写 toString 方法
      return digest; // 返回窗口组的字符串表示
    }

    @RequiresNonNull({"keys", "orderKeys", "lowerBound", "upperBound", "aggCalls"}) // 要求这些字段非空
    private String computeString(@UnderInitialization Group this) { // 计算窗口组的字符串表示
      final StringBuilder buf = new StringBuilder("window("); // 创建字符串构建器，以 "window(" 开头
      final int i = buf.length(); // 记录当前长度
      if (!keys.isEmpty()) { // 如果分区键不为空
        buf.append("partition "); // 添加 "partition "
        buf.append(keys); // 添加分区键
      }
      if (!orderKeys.getFieldCollations().isEmpty()) { // 如果排序键不为空
        if (buf.length() > i) { // 如果已经添加了内容
          buf.append(' '); // 添加空格分隔
        }
        buf.append("order by "); // 添加 "order by "
        buf.append(orderKeys); // 添加排序键
      }
      if (orderKeys.getFieldCollations().isEmpty() // 如果没有 ORDER BY
          && lowerBound.isUnboundedPreceding() // 并且下界是无界前导
          && upperBound.isUnboundedFollowing()) { // 并且上界是无界后继
        // skip bracket if no ORDER BY, and if bracket is the default, // 跳过窗口边界，因为没有 ORDER BY，并且边界是默认的
        // "RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING", // "RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"
        // which is equivalent to // 这等同于
        // "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING" // "ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"
      } else if (!orderKeys.getFieldCollations().isEmpty() // 如果有 ORDER BY
          && lowerBound.isUnboundedPreceding() // 并且下界是无界前导
          && upperBound.isCurrentRow() // 并且上界是当前行
          && !isRows) { // 并且是逻辑窗口
        // skip bracket if there is ORDER BY, and if bracket is the default, // 跳过窗口边界，因为有 ORDER BY，并且边界是默认的
        // "RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW", // "RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"
        // which is NOT equivalent to // 这不等同于
        // "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW" // "ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"
      } else { // 否则需要添加窗口边界
        if (buf.length() > i) { // 如果已经添加了内容
          buf.append(' '); // 添加空格分隔
        }
        buf.append(isRows ? "rows " : "range "); // 添加 "rows " 或 "range "
        buf.append("between "); // 添加 "between "
        buf.append(lowerBound); // 添加下界
        buf.append(" and "); // 添加 " and "
        buf.append(upperBound); // 添加上界
        if (exclude != RexWindowExclusion.EXCLUDE_NO_OTHER) { // 如果有排除子句
          buf.append(" ").append(exclude); // 添加排除子句
        }
      }
      if (!aggCalls.isEmpty()) { // 如果聚合调用列表不为空
        if (buf.length() > i) { // 如果已经添加了内容
          buf.append(' '); // 添加空格分隔
        }
        buf.append("aggs "); // 添加 "aggs "
        buf.append(aggCalls); // 添加聚合调用列表
      }
      buf.append(")"); // 添加 ")"
      return buf.toString(); // 返回构建的字符串
    }

    @Override public boolean equals(@Nullable Object obj) { // 重写 equals 方法
      return this == obj // 如果是同一个对象，返回 true
          || obj instanceof Group // 或者是 Group 类的实例
          && this.digest.equals(((Group) obj).digest); // 并且摘要字符串相等
    }

    @Override public int hashCode() { // 重写 hashCode 方法
      return digest.hashCode(); // 返回摘要字符串的哈希码
    }

    public RelCollation collation() { // 获取排序规则
      return orderKeys; // 返回排序键
    }

    /**
     * Returns if the window is guaranteed to have rows. // 返回窗口是否保证有行
     * This is useful to refine data type of window aggregates. // 这对于优化窗口聚合的数据类型很有用
     * For instance sum(non-nullable) over (empty window) is NULL. // 例如，sum(non-nullable) over (空窗口) 的结果是 NULL
     *
     * @return true when the window is non-empty // 当窗口非空时返回 true
     * @see org.apache.calcite.sql.SqlWindow#isAlwaysNonEmpty() // 参见 SqlWindow 的 isAlwaysNonEmpty 方法
     * @see org.apache.calcite.sql.SqlOperatorBinding#getGroupCount() // 参见 SqlOperatorBinding 的 getGroupCount 方法
     * @see org.apache.calcite.sql.validate.SqlValidatorImpl#resolveWindow(org.apache.calcite.sql.SqlNode, org.apache.calcite.sql.validate.SqlValidatorScope) // 参见 SqlValidatorImpl 的 resolveWindow 方法
     */
    public boolean isAlwaysNonEmpty() { // 判断窗口是否保证非空
      int lowerKey = lowerBound.getOrderKey(); // 获取下界的排序键
      int upperKey = upperBound.getOrderKey(); // 获取上界的排序键
      return lowerKey > -1 && lowerKey <= upperKey // 如果下界有效且小于等于上界
            && (exclude == RexWindowExclusion.EXCLUDE_NO_OTHER // 并且排除子句为无排除或仅排除平局
               || exclude == RexWindowExclusion.EXCLUDE_TIES); // 则返回 true
    }

    /**
     * Presents a view of the {@link RexWinAggCall} list as a list of // 将 RexWinAggCall 列表呈现为 AggregateCall 列表
     * {@link AggregateCall}. // 的视图
     */
    public List<AggregateCall> getAggregateCalls(Window windowRel) { // 获取聚合调用列表
      final List<String> fieldNames = // 获取字段名称列表
          Util.skip(windowRel.getRowType().getFieldNames(), // 跳过输入字段，只获取窗口聚合产生的字段名
              windowRel.getInput().getRowType().getFieldCount());
      return new AbstractList<AggregateCall>() { // 返回匿名抽象列表
        @Override public int size() { // 重写 size 方法
          return aggCalls.size(); // 返回聚合调用列表的大小
        }

        @Override public AggregateCall get(int index) { // 重写 get 方法
          final RexWinAggCall aggCall = aggCalls.get(index); // 获取指定索引的窗口聚合调用
          final SqlAggFunction op = (SqlAggFunction) aggCall.getOperator(); // 获取聚合函数
          return AggregateCall.create(aggCall.getParserPosition(), op, aggCall.distinct, false, // 创建 AggregateCall 对象
              aggCall.ignoreNulls, ImmutableList.of(), // 参数：解析位置、操作符、是否去重、是否近似、是否忽略空值、过滤器
              getProjectOrdinals(aggCall.getOperands()), // 操作数的投影序号
              -1, null, RelCollations.EMPTY, // 分组字段、排序字段、排序规则
              aggCall.getType(), fieldNames.get(aggCall.ordinal)); // 结果类型和字段名
        }
      };
    }
  }

  /**
   * A call to a windowed aggregate function. // 窗口聚合函数的调用
   *
   * <p>Belongs to a {@link Window.Group}. // 属于一个 Window.Group
   *
   * <p>It's a bastard son of a {@link org.apache.calcite.rex.RexCall}; similar // 它是 RexCall 的"私生子"；足够相似以至于可以被 RexVisitor 访问
   * enough that it gets visited by a {@link org.apache.calcite.rex.RexVisitor}, // 但它也有一些额外的数据成员
   * but it also has some extra data members.
   */
  public static class RexWinAggCall extends RexCall { // 静态内部类，表示窗口聚合调用，继承自 RexCall
    /**
     * Ordinal of this aggregate within its partition. // 此聚合在其分区中的序号
     */
    public final int ordinal; // 序号，表示在分区中的位置

   /** Whether to eliminate duplicates before applying aggregate function. // 是否在应用聚合函数之前消除重复值 */
    public final boolean distinct; // 去重标志

   /** Whether to ignore nulls. // 是否忽略空值 */
    public final boolean ignoreNulls; // 忽略空值标志

    @Deprecated // to be removed before 2.0 // 已弃用，将在 2.0 版本之前移除
    public RexWinAggCall( // 已弃用的构造方法
        SqlAggFunction aggFun, // 聚合函数
        RelDataType type, // 结果类型
        List<RexNode> operands, // 操作数列表
        int ordinal, // 序号
        boolean distinct) {// 是否去重
      this(aggFun, type, operands, ordinal, distinct, false); // 调用主构造方法，ignoreNulls 设为 false
    }

    /**
     * Creates a RexWinAggCall. // 创建一个 RexWinAggCall 实例
     *
     * @param aggFun   Aggregate function // 聚合函数
     * @param type     Result type // 结果类型
     * @param operands Operands to call // 操作数列表
     * @param ordinal  Ordinal within its partition // 在其分区中的序号
     * @param distinct Eliminate duplicates before applying aggregate function // 是否在应用聚合函数之前消除重复值
     */
    public RexWinAggCall( // 主构造方法
        SqlAggFunction aggFun, // 聚合函数
        RelDataType type, // 结果类型
        List<RexNode> operands, // 操作数列表
        int ordinal, // 序号
        boolean distinct, // 是否去重
        boolean ignoreNulls) { // 是否忽略空值
      super(type, aggFun, operands); // 调用父类 RexCall 的构造方法
      this.ordinal = ordinal; // 设置序号
      this.distinct = distinct; // 设置去重标志
      this.ignoreNulls = ignoreNulls; // 设置忽略空值标志
    }

    @Override public boolean equals(@Nullable Object o) { // 重写 equals 方法
      if (this == o) { // 如果是同一个对象
        return true; // 返回 true
      }
      if (o == null || getClass() != o.getClass()) { // 如果为空或类型不同
        return false; // 返回 false
      }
      if (!super.equals(o)) { // 如果父类不相等
        return false; // 返回 false
      }
      RexWinAggCall that = (RexWinAggCall) o; // 转换为 RexWinAggCall 类型
      return ordinal == that.ordinal // 比较序号
          && distinct == that.distinct // 比较去重标志
          && ignoreNulls == that.ignoreNulls; // 比较忽略空值标志
    }

    @Override public int hashCode() { // 重写 hashCode 方法
      if (hash == 0) { // 如果哈希码为 0
        hash = hash(super.hashCode(), ordinal, distinct, ignoreNulls); // 计算哈希码
      }
      return hash; // 返回哈希码
    }

    @Override public RexCall clone(RelDataType type, List<RexNode> operands) { // 重写 clone 方法
      return super.clone(type, operands); // 调用父类的 clone 方法
    }
  }

  @Override public ImmutableList<RelHint> getHints() { // 重写 getHints 方法
    return hints; // 返回提示列表
  }
} // Window 类结束
