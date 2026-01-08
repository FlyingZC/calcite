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
package org.apache.calcite.rel.core; // 包声明，定义Aggregate类所在的包路径

import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合元素添加索引
import org.apache.calcite.linq4j.function.Experimental; // 导入Experimental注解，标记实验性功能
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式的集群
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系操作的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示查询优化器
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil类，提供关系表达式工具方法
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelInput; // 导入RelInput接口，用于反序列化关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于输出关系表达式的解释信息
import org.apache.calcite.rel.SingleRel; // 导入SingleRel类，表示只有一个输入的关系表达式基类
import org.apache.calcite.rel.hint.Hintable; // 导入Hintable接口，表示支持提示的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示关系表达式的提示
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，表示关系数据类型字段
import org.apache.calcite.runtime.CalciteException; // 导入CalciteException类，表示Calcite运行时异常
import org.apache.calcite.runtime.Resources; // 导入Resources类，用于资源管理
import org.apache.calcite.sql.SqlAggFunction; // 导入SqlAggFunction接口，表示SQL聚合函数
import org.apache.calcite.sql.SqlOperatorBinding; // 导入SqlOperatorBinding类，表示SQL操作符绑定
import org.apache.calcite.sql.SqlUtil; // 导入SqlUtil类，提供SQL工具方法
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类，表示SQL解析位置
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，表示SQL类型名称
import org.apache.calcite.sql.validate.SqlValidatorException; // 导入SqlValidatorException类，表示SQL验证异常
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合
import org.apache.calcite.util.Litmus; // 导入Litmus枚举，用于验证错误处理策略
import org.apache.calcite.util.Pair; // 导入Pair类，表示键值对
import org.apache.calcite.util.Util; // 导入Util类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变列表
import com.google.common.math.IntMath; // 导入Google Guava的IntMath类，提供整数数学运算

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可空类型

import java.util.ArrayList; // 导入ArrayList类，表示动态数组
import java.util.Collections; // 导入Collections类，提供集合工具方法
import java.util.HashSet; // 导入HashSet类，表示哈希集合
import java.util.List; // 导入List接口，表示列表
import java.util.Set; // 导入Set接口，表示集合

import static com.google.common.base.Preconditions.checkArgument; // 导入Google Guava的checkArgument方法，用于参数校验

import static java.util.Objects.requireNonNull; // 导入Java的requireNonNull方法，用于空值检查

/**
 * Relational operator that eliminates
 * duplicates and computes totals.
 * 关系运算符，用于消除重复项并计算总计。
 *
 * <p>It corresponds to the {@code GROUP BY} operator in a SQL query
 * statement, together with the aggregate functions in the {@code SELECT}
 * clause.
 * 它对应于SQL查询语句中的GROUP BY运算符，以及SELECT子句中的聚合函数。
 *
 * <p>Rules:
 * 相关规则：
 *
 * <ul>
 * <li>{@link org.apache.calcite.rel.rules.AggregateProjectPullUpConstantsRule}
 * - 将常量从Project向上推送到Aggregate的规则
 * <li>{@link org.apache.calcite.rel.rules.AggregateExpandDistinctAggregatesRule}
 * - 展开DISTINCT聚合函数的规则
 * <li>{@link org.apache.calcite.rel.rules.AggregateReduceFunctionsRule}.
 * - 简化聚合函数的规则
 * </ul>
 */
public abstract class Aggregate extends SingleRel implements Hintable { // Aggregate抽象类，继承SingleRel并实现Hintable接口，表示聚合关系运算符

  protected final ImmutableList<RelHint> hints; // 关系表达式的提示列表，用于优化器指导

  public static boolean isSimple(Aggregate aggregate) { // 静态方法：判断聚合是否为简单聚合（不包含ROLLUP、CUBE、GROUPING SETS）
    return aggregate.getGroupType() == Group.SIMPLE; // 返回聚合类型是否为SIMPLE
  }

  @SuppressWarnings("Guava") // 抑制Guava相关警告
  @Deprecated // to be converted to Java Predicate before 2.0 // 已废弃，将在2.0版本前转换为Java Predicate
  public static final com.google.common.base.Predicate<Aggregate> IS_SIMPLE = // 静态常量：判断聚合是否为简单的谓词
      Aggregate::isSimple; // 使用方法引用指向isSimple方法

  @SuppressWarnings("Guava") // 抑制Guava相关警告
  @Deprecated // to be converted to Java Predicate before 2.0 // 已废弃，将在2.0版本前转换为Java Predicate
  public static final com.google.common.base.Predicate<Aggregate> NO_INDICATOR = // 静态常量：判断聚合不包含indicator的谓词
      Aggregate::noIndicator; // 使用方法引用指向noIndicator方法

  @SuppressWarnings("Guava") // 抑制Guava相关警告
  @Deprecated // to be converted to Java Predicate before 2.0 // 已废弃，将在2.0版本前转换为Java Predicate
  public static final com.google.common.base.Predicate<Aggregate> // 静态常量：判断聚合不是总计（grand total）的谓词
      IS_NOT_GRAND_TOTAL = Aggregate::isNotGrandTotal; // 使用方法引用指向isNotGrandTotal方法

  /** Used internally; will removed when {@link #indicator} is removed,
   * before 2.0. */
  @Experimental // 实验性功能
  public static void checkIndicator(boolean indicator) { // 静态方法：检查indicator参数，确保不再使用indicator
    checkArgument(!indicator, // 检查indicator必须为false
        "indicator is no longer supported; use GROUPING function instead"); // 抛出异常提示使用GROUPING函数替代
  }

  //~ Instance fields -------------------------------------------------------- // 实例字段部分

  @Deprecated // unused field, to be removed before 2.0 // 已废弃，将在2.0版本前移除
  public final boolean indicator = false; // indicator标志，已不再使用，始终为false

  protected final List<AggregateCall> aggCalls; // 聚合函数调用列表，包含所有在SELECT子句中的聚合函数调用
  protected final ImmutableBitSet groupSet; // 分组字段的位集合，表示GROUP BY子句中的所有字段索引
  public final ImmutableList<ImmutableBitSet> groupSets; // 所有分组集合的列表，支持ROLLUP、CUBE、GROUPING SETS等高级分组

  //~ Constructors ----------------------------------------------------------- // 构造方法部分

  /**
   * Creates an Aggregate.
   * 创建一个Aggregate关系表达式。
   *
   * <p>All members of {@code groupSets} must be sub-sets of {@code groupSet}.
   * groupSets的所有成员必须是groupSet的子集。
   * For a simple {@code GROUP BY}, {@code groupSets} is a singleton list
   * containing {@code groupSet}.
   * 对于简单的GROUP BY，groupSets是只包含groupSet的单元素列表。
   *
   * <p>It is allowed for {@code groupSet} to contain bits that are not in any
   * of the {@code groupSets}, even this does not correspond to valid SQL. See
   * discussion in
   * {@link org.apache.calcite.tools.RelBuilder#groupKey(ImmutableBitSet, Iterable)}.
   * 允许groupSet包含不在任何groupSets中的位，虽然这不对应有效的SQL。
   *
   * <p>If {@code GROUP BY} is not specified,
   * or equivalently if {@code GROUP BY ()} is specified,
   * {@code groupSet} will be the empty set,
   * and {@code groupSets} will have one element, that empty set.
   * 如果未指定GROUP BY，或指定了GROUP BY ()，groupSet将为空集合，groupSets将有一个元素，即空集合。
   *
   * <p>If {@code CUBE}, {@code ROLLUP} or {@code GROUPING SETS} are
   * specified, {@code groupSets} will have additional elements,
   * but they must each be a subset of {@code groupSet},
   * and they must be sorted by inclusion:
   * {@code (0, 1, 2), (1), (0, 2), (0), ()}.
   * 如果指定了CUBE、ROLLUP或GROUPING SETS，groupSets将有额外元素，但每个都必须是groupSet的子集，并按包含关系排序。
   *
   * @param cluster  Cluster - 关系表达式集群，包含类型工厂等共享资源
   * @param traitSet Trait set - 关系表达式的特征集合，如物理实现方式
   * @param hints    Hints of this relational expression - 关系表达式的提示列表
   * @param input    Input relational expression - 输入关系表达式
   * @param groupSet Bit set of grouping fields - 分组字段的位集合
   * @param groupSets List of all grouping sets; null for just {@code groupSet} - 所有分组集合的列表，null表示只有groupSet
   * @param aggCalls Collection of calls to aggregate functions - 聚合函数调用的集合
   */
  @SuppressWarnings("method.invocation.invalid") // 抑制方法调用无效的警告
  protected Aggregate( // 受保护的构造方法，创建Aggregate关系表达式
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      List<RelHint> hints, // 参数：提示列表
      RelNode input, // 参数：输入关系表达式
      ImmutableBitSet groupSet, // 参数：分组字段位集合
      @Nullable List<ImmutableBitSet> groupSets, // 参数：分组集合列表，可为null
      List<AggregateCall> aggCalls) { // 参数：聚合函数调用列表
    super(cluster, traitSet, input); // 调用父类SingleRel的构造方法
    this.hints = ImmutableList.copyOf(hints); // 将提示列表转换为不可变列表
    this.aggCalls = ImmutableList.copyOf(aggCalls); // 将聚合调用列表转换为不可变列表
    this.groupSet = requireNonNull(groupSet, "groupSet"); // 确保groupSet不为null
    if (groupSets == null) { // 如果groupSets为null
      this.groupSets = ImmutableList.of(groupSet); // 创建只包含groupSet的单元素列表
    } else { // 如果groupSets不为null
      this.groupSets = ImmutableList.copyOf(groupSets); // 将groupSets转换为不可变列表
      assert ImmutableBitSet.ORDERING.isStrictlyOrdered(groupSets) : groupSets; // 断言groupSets按严格顺序排列
      for (ImmutableBitSet set : groupSets) { // 遍历每个分组集合
        assert groupSet.contains(set); // 断言每个分组集合都是groupSet的子集
      }
    }
    assert groupSet.length() <= input.getRowType().getFieldCount(); // 断言groupSet的长度不超过输入字段数
    for (AggregateCall aggCall : aggCalls) { // 遍历每个聚合调用
      assert typeMatchesInferred(aggCall, Litmus.THROW); // 断言聚合调用类型与推断类型匹配
      checkArgument(aggCall.filterArg < 0 // 检查聚合调用的filter参数
          || isPredicate(input, aggCall.filterArg), // 或者是谓词类型
          "filter must be BOOLEAN NOT NULL"); // 抛出异常提示filter必须是BOOLEAN NOT NULL类型
    }
  }

  @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
  protected Aggregate( // 受保护的构造方法，旧版本，不包含hints参数
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode input, // 参数：输入关系表达式
      ImmutableBitSet groupSet, // 参数：分组字段位集合
      List<ImmutableBitSet> groupSets, // 参数：分组集合列表
      List<AggregateCall> aggCalls) { // 参数：聚合函数调用列表
    this(cluster, traitSet, new ArrayList<>(), input, groupSet, groupSets, aggCalls); // 调用主构造方法，传入空的hints列表
  }

  @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
  protected Aggregate( // 受保护的构造方法，旧版本，包含indicator参数
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traits, // 参数：特征集合
      RelNode child, // 参数：子节点（输入关系表达式）
      boolean indicator, // 参数：indicator标志（已废弃）
      ImmutableBitSet groupSet, // 参数：分组字段位集合
      List<ImmutableBitSet> groupSets, // 参数：分组集合列表
      List<AggregateCall> aggCalls) { // 参数：聚合函数调用列表
    this(cluster, traits, ImmutableList.of(), child, groupSet, groupSets, aggCalls); // 调用主构造方法，传入空的hints列表
    checkIndicator(indicator); // 检查indicator参数
  }

  public static boolean isNotGrandTotal(Aggregate aggregate) { // 静态方法：判断聚合是否不是总计（grand total）
    return aggregate.getGroupCount() > 0; // 返回分组数量是否大于0
  }

  @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
  public static boolean noIndicator(Aggregate aggregate) { // 静态方法：判断聚合不包含indicator
    return true; // 始终返回true，因为indicator已废弃
  }

  private static boolean isPredicate(RelNode input, int index) { // 私有静态方法：判断指定索引的字段是否为谓词类型
    final RelDataType type = // 获取字段类型
        input.getRowType().getFieldList().get(index).getType(); // 从输入行类型中获取指定索引字段的类型
    return type.getSqlTypeName() == SqlTypeName.BOOLEAN // 判断类型是否为BOOLEAN
        && !type.isNullable(); // 并且类型不可为null
  }

  /**
   * Creates an Aggregate by parsing serialized output.
   * 通过解析序列化输出来创建Aggregate关系表达式。
   */
  protected Aggregate(RelInput input) { // 受保护的构造方法，用于反序列化
    this(input.getCluster(), input.getTraitSet(), new ArrayList<>(), // 调用主构造方法
        input.getInput(), input.getBitSet("group"), // 从RelInput中获取输入、group、groups、aggs
        input.getBitSetList("groups"), input.getAggregateCalls("aggs"));
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分

  @Override public final RelNode copy(RelTraitSet traitSet, // 重写copy方法，创建关系表达式的副本
      List<RelNode> inputs) { // 参数：输入关系表达式列表
    return copy(traitSet, sole(inputs), groupSet, groupSets, aggCalls); // 调用具体的copy方法，传入当前的groupSet、groupSets、aggCalls
  }

  /** Creates a copy of this aggregate.
   * 创建此聚合关系表达式的副本。
   *
   * @param traitSet Traits - 新的特征集合
   * @param input Input - 新的输入关系表达式
   * @param groupSet Bit set of grouping fields - 新的分组字段位集合
   * @param groupSets List of all grouping sets; null for just {@code groupSet} - 新的分组集合列表，null表示只有groupSet
   * @param aggCalls Collection of calls to aggregate functions - 新的聚合函数调用集合
   * @return New {@code Aggregate} if any parameter differs from the value of
   *   this {@code Aggregate}, or just {@code this} if all the parameters are
   *   the same
   *   如果任何参数与此Aggregate不同，返回新的Aggregate；如果所有参数相同，返回this
   *
   * @see #copy(org.apache.calcite.plan.RelTraitSet, java.util.List) // 参见另一个copy方法
   */
  public abstract Aggregate copy(RelTraitSet traitSet, RelNode input, // 抽象方法：创建Aggregate的副本
      ImmutableBitSet groupSet, // 参数：分组字段位集合
      @Nullable List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls); // 参数：分组集合列表和聚合调用列表

  @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
  public Aggregate copy(RelTraitSet traitSet, RelNode input, // copy方法，旧版本，包含indicator参数
      boolean indicator, ImmutableBitSet groupSet, // 参数：indicator标志（已废弃）和分组字段位集合
      List<ImmutableBitSet> groupSets, List<AggregateCall> aggCalls) { // 参数：分组集合列表和聚合调用列表
    checkIndicator(indicator); // 检查indicator参数
    return copy(traitSet, input, groupSet, groupSets, aggCalls); // 调用新版本的copy方法
  }

  /**
   * Returns a list of calls to aggregate functions.
   * 返回聚合函数调用的列表。
   *
   * @return list of calls to aggregate functions - 聚合函数调用列表
   */
  public List<AggregateCall> getAggCallList() { // 获取聚合函数调用列表
    return aggCalls; // 返回aggCalls字段
  }

  /**
   * Returns a list of calls to aggregate functions together with their output
   * field names.
   * 返回聚合函数调用及其输出字段名称的列表。
   *
   * @return list of calls to aggregate functions and their output field names - 聚合函数调用和输出字段名称的列表
   */
  public List<Pair<AggregateCall, String>> getNamedAggCalls() { // 获取带名称的聚合调用列表
    final int offset = getGroupCount(); // 计算偏移量，即分组字段的数量
    return Pair.zip(aggCalls, Util.skip(getRowType().getFieldNames(), offset)); // 将聚合调用与对应的字段名称配对
  }

  /**
   * Returns the number of grouping fields.
   * 返回分组字段的数量。
   * These grouping fields are the leading fields in both the input and output
   * records.
   * 这些分组字段是输入和输出记录中的前导字段。
   *
   * <p>NOTE: The {@link #getGroupSet()} data structure allows for the
   * grouping fields to not be on the leading edge. New code should, if
   * possible, assume that grouping fields are in arbitrary positions in the
   * input relational expression.
   * 注意：getGroupSet()数据结构允许分组字段不在前导位置。新代码应尽可能假设分组字段在输入关系表达式中的任意位置。
   *
   * @return number of grouping fields - 分组字段的数量
   */
  public int getGroupCount() { // 获取分组字段的数量
    return groupSet.cardinality(); // 返回groupSet的基数（元素个数）
  }

  /**
   * Returns the number of indicator fields.
   * 返回indicator字段的数量。
   *
   * <p>Always zero.
   * 始终为零。
   *
   * @return number of indicator fields, always zero - indicator字段数量，始终为0
   */
  @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
  public int getIndicatorCount() { // 获取indicator字段数量
    return 0; // 始终返回0
  }

  /**
   * Returns a bit set of the grouping fields.
   * 返回分组字段的位集合。
   *
   * @return bit set of ordinals of grouping fields - 分组字段序号的位集合
   */
  public ImmutableBitSet getGroupSet() { // 获取分组字段的位集合
    return groupSet; // 返回groupSet字段
  }

  /**
   * Returns the list of grouping sets computed by this Aggregate.
   * 返回此Aggregate计算的所有分组集合的列表。
   *
   * @return List of all grouping sets - 所有分组集合的列表
   */
  public ImmutableList<ImmutableBitSet> getGroupSets() { // 获取分组集合列表
    return groupSets; // 返回groupSets字段
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，输出关系表达式的解释信息
    // We skip the "groups" element if it is a singleton of "group".
    // 如果groups是group的单元素，则跳过"groups"元素。
    super.explainTerms(pw) // 调用父类的explainTerms方法
        .item("group", groupSet) // 添加group项
        .itemIf("groups", groupSets, getGroupType() != Group.SIMPLE) // 如果不是简单分组，添加groups项
        .itemIf("aggs", aggCalls, pw.nest()); // 如果嵌套输出，添加aggs项
    if (!pw.nest()) { // 如果不嵌套输出
      for (Ord<AggregateCall> ord : Ord.zip(aggCalls)) { // 遍历每个聚合调用
        pw.item(Util.first(ord.e.name, "agg#" + ord.i), ord.e); // 添加聚合调用项，使用名称或生成名称
      }
    }
    return pw; // 返回RelWriter
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写estimateRowCount方法，估算行数
    // Assume that each sort column has 50% of the value count.
    // 假设每个排序列有50%的唯一值。
    // Therefore one sort column has .5 * rowCount,
    // 因此一个排序列有0.5 * rowCount的行数，
    // 2 sort columns give .75 * rowCount.
    // 两个排序列有0.75 * rowCount的行数。
    // Zero sort columns yields 1 row (or 0 if the input is empty).
    // 零个排序列产生1行（如果输入为空则为0）。
    final int groupCount = groupSet.cardinality(); // 获取分组字段数量
    if (groupCount == 0) { // 如果没有分组字段
      return 1; // 返回1行
    } else { // 如果有分组字段
      double rowCount = super.estimateRowCount(mq); // 获取父类估算的行数
      rowCount *= 1.0 - Math.pow(.5, groupCount); // 根据分组数量调整行数估算
      return rowCount; // 返回调整后的行数
    }
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算自身成本
      RelMetadataQuery mq) { // 参数：元数据查询
    // REVIEW jvs 24-Aug-2008:  This is bogus, but no more bogus
    // REVIEW jvs 2008年8月24日：这是虚假的，但不会比Join中的更虚假
    // than what's currently in Join.
    double rowCount = mq.getRowCount(this); // 获取行数
    // Aggregates with more aggregate functions cost a bit more
    // 聚合函数越多，成本稍高
    float multiplier = 1f + (float) aggCalls.size() * 0.125f; // 计算乘数，基于聚合函数数量
    for (AggregateCall aggCall : aggCalls) { // 遍历每个聚合调用
      if (aggCall.getAggregation().getName().equals("SUM")) { // 如果是SUM函数
        // Pretend that SUM costs a little bit more than $SUM0,
        // 假装SUM比$SUM0成本稍高，
        // to make things deterministic.
        // 以使结果具有确定性。
        multiplier += 0.0125f; // 增加乘数
      }
    }
    return planner.getCostFactory().makeCost(rowCount * multiplier, 0, 0); // 返回成本对象
  }

  @Override protected RelDataType deriveRowType() { // 重写deriveRowType方法，推导行类型
    return deriveRowType(getCluster().getTypeFactory(), getInput().getRowType(), // 调用静态deriveRowType方法
        false, groupSet, groupSets, aggCalls); // 传入indicator为false和其他参数
  }

  /**
   * Computes the row type of an {@code Aggregate} before it exists.
   * 在Aggregate存在之前计算其行类型。
   *
   * @param typeFactory Type factory - 类型工厂
   * @param inputRowType Input row type - 输入行类型
   * @param indicator Deprecated, always false - 已废弃，始终为false
   * @param groupSet Bit set of grouping fields - 分组字段位集合
   * @param groupSets List of all grouping sets; null for just {@code groupSet} - 所有分组集合的列表，null表示只有groupSet
   * @param aggCalls Collection of calls to aggregate functions - 聚合函数调用的集合
   * @return Row type of the aggregate - 聚合的行类型
   */
  public static RelDataType deriveRowType(RelDataTypeFactory typeFactory, // 静态方法：推导Aggregate的行类型
      final RelDataType inputRowType, boolean indicator, // 参数：输入行类型和indicator标志
      ImmutableBitSet groupSet, @Nullable List<ImmutableBitSet> groupSets, // 参数：分组字段位集合和分组集合列表
      final List<AggregateCall> aggCalls) { // 参数：聚合函数调用列表
    final List<Integer> groupList = groupSet.asList(); // 将groupSet转换为列表
    assert groupList.size() == groupSet.cardinality(); // 断言列表大小等于groupSet的基数
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建类型构建器
    final List<RelDataTypeField> fieldList = inputRowType.getFieldList(); // 获取输入字段列表
    final Set<String> containedNames = new HashSet<>(); // 创建已包含字段名的集合
    for (int groupKey : groupList) { // 遍历每个分组字段索引
      final RelDataTypeField field = fieldList.get(groupKey); // 获取字段
      containedNames.add(field.getName()); // 添加字段名到已包含集合
      builder.add(field); // 添加字段到构建器
      if (groupSets != null && !ImmutableBitSet.allContain(groupSets, groupKey)) { // 如果存在分组集合且并非所有集合都包含此字段
        builder.nullable(true); // 设置字段可为null
      }
    }
    checkIndicator(indicator); // 检查indicator参数
    for (Ord<AggregateCall> aggCall : Ord.zip(aggCalls)) { // 遍历每个聚合调用
      final String base; // 基础名称
      if (aggCall.e.name != null) { // 如果聚合调用有名称
        base = aggCall.e.name; // 使用聚合调用的名称
      } else { // 如果聚合调用没有名称
        base = "$f" + (groupList.size() + aggCall.i); // 生成默认名称，如$f0、$f1等
      }
      String name = base; // 初始名称
      int i = 0; // 计数器
      while (containedNames.contains(name)) { // 如果名称已存在
        name = base + "_" + i++; // 添加后缀使名称唯一
      }
      containedNames.add(name); // 添加名称到已包含集合
      builder.add(name, aggCall.e.type); // 添加字段到构建器
    }
    return builder.build(); // 构建并返回行类型
  }

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) { // 重写isValid方法，验证关系表达式是否有效
    return super.isValid(litmus, context) // 调用父类的isValid方法
        && litmus.check(Util.isDistinct(getRowType().getFieldNames()), // 检查字段名是否唯一
            "distinct field names: {}", getRowType()); // 如果不唯一，输出错误信息
  }

  /**
   * Returns whether the inferred type of an {@link AggregateCall} matches the
   * type it was given when it was created.
   * 返回AggregateCall的推断类型是否与其创建时给定的类型匹配。
   *
   * @param aggCall Aggregate call - 聚合调用
   * @param litmus What to do if an error is detected (types do not match) - 检测到错误时的处理策略
   * @return Whether the inferred and declared types match - 推断类型和声明类型是否匹配
   */
  private boolean typeMatchesInferred( // 私有方法：检查聚合调用类型是否与推断类型匹配
      final AggregateCall aggCall, // 参数：聚合调用
      final Litmus litmus) { // 参数：错误处理策略
    SqlAggFunction aggFunction = aggCall.getAggregation(); // 获取聚合函数
    AggCallBinding callBinding = aggCall.createBinding(this); // 创建聚合调用绑定
    RelDataType type = aggFunction.inferReturnType(callBinding); // 推断返回类型
    RelDataType expectedType = aggCall.type; // 获取期望类型
    return RelOptUtil.eq("aggCall type", // 比较两种类型是否相等
        expectedType, // 期望类型
        "inferred type", // 推断类型
        type, // 推断类型
        litmus); // 错误处理策略
  }

  /**
   * Returns whether any of the aggregates are DISTINCT.
   * 返回是否有任何聚合是DISTINCT聚合。
   *
   * @return Whether any of the aggregates are DISTINCT - 是否有任何DISTINCT聚合
   */
  public boolean containsDistinctCall() { // 判断是否包含DISTINCT聚合调用
    for (AggregateCall call : aggCalls) { // 遍历每个聚合调用
      if (call.isDistinct()) { // 如果是DISTINCT聚合
        return true; // 返回true
      }
    }
    return false; // 返回false
  }

  @Override public ImmutableList<RelHint> getHints() { // 重写getHints方法，获取提示列表
    return hints; // 返回hints字段
  }

  /**
   * Returns the type of roll-up.
   * 返回roll-up的类型。
   *
   * @return Type of roll-up - roll-up类型
   */
  public Group getGroupType() { // 获取分组类型
    return Group.induce(groupSet, groupSets); // 调用Group.induce方法推断分组类型
  }

  /** Describes the kind of roll-up. */
  public enum Group {
    SIMPLE,
    ROLLUP,
    CUBE,
    OTHER;

    public static Group induce(ImmutableBitSet groupSet,
        List<ImmutableBitSet> groupSets) {
      if (!ImmutableBitSet.ORDERING.isStrictlyOrdered(groupSets)) {
        throw new IllegalArgumentException("must be sorted: " + groupSets);
      }
      if (groupSets.size() == 1 && groupSets.get(0).equals(groupSet)) {
        return SIMPLE;
      }
      if (groupSets.size() == IntMath.pow(2, groupSet.cardinality())) {
        return CUBE;
      }
      if (isRollup(groupSet, groupSets)) {
        return ROLLUP;
      }
      return OTHER;
    }

    /** Returns whether a list of sets is a rollup.
     *
     * <p>For example, if {@code groupSet} is <code>{2, 4, 5}</code>, then
     * <code>[{2, 4, 5], {2, 5}, {5}, {}]</code> is a rollup. The first item is
     * equal to {@code groupSet}, and each subsequent item is a subset with one
     * fewer bit than the previous.
     *
     * @see #getRollup(List) */
    public static boolean isRollup(ImmutableBitSet groupSet,
        List<ImmutableBitSet> groupSets) {
      if (groupSets.size() != groupSet.cardinality() + 1) {
        return false;
      }
      ImmutableBitSet g = null;
      for (ImmutableBitSet bitSet : groupSets) {
        if (g == null) {
          // First item must equal groupSet
          if (!bitSet.equals(groupSet)) {
            return false;
          }
        } else {
          // Each subsequent items must be a subset with one fewer bit than the
          // previous item
          if (!g.contains(bitSet)
              || g.cardinality() - bitSet.cardinality() != 1) {
            return false;
          }
        }
        g = bitSet;
      }
      requireNonNull(g, "groupSet must not be empty");
      checkArgument(g.isEmpty());
      return true;
    }

    /** Returns the ordered list of bits in a rollup.
     *
     * <p>For example, given a {@code groupSets} value
     * <code>[{2, 4, 5], {2, 5}, {5}, {}]</code>, returns the list
     * {@code [5, 2, 4]}, which are the succession of bits
     * added to each of the sets starting with the empty set.
     *
     * @see #isRollup(ImmutableBitSet, List) */
    public static List<Integer> getRollup(List<ImmutableBitSet> groupSets) {
      final List<Integer> rollUpBits = new ArrayList<>(groupSets.size() - 1);
      ImmutableBitSet g = null;
      for (ImmutableBitSet bitSet : groupSets) {
        if (g == null) {
          // First item must equal groupSet
        } else {
          // Each subsequent items must be a subset with one fewer bit than the
          // previous item
          ImmutableBitSet diff = g.except(bitSet);
          assert diff.cardinality() == 1;
          rollUpBits.add(diff.nth(0));
        }
        g = bitSet;
      }
      Collections.reverse(rollUpBits);
      return ImmutableList.copyOf(rollUpBits);
    }
  }

  //~ Inner Classes ---------------------------------------------------------- // 内部类部分

  /**
   * Implementation of the {@link SqlOperatorBinding} interface for an
   * {@link AggregateCall aggregate call} applied to a set of operands in the
   * context of a {@link org.apache.calcite.rel.logical.LogicalAggregate}.
   * SqlOperatorBinding接口的实现，用于在LogicalAggregate上下文中应用于一组操作数的聚合调用。
   */
  public static class AggCallBinding extends SqlOperatorBinding { // AggCallBinding类，聚合调用绑定，继承SqlOperatorBinding
    private final List<RelDataType> preOperands; // 前操作数类型列表，如ORDER BY子句中的操作数
    private final List<RelDataType> operands; // 操作数类型列表，聚合函数的参数类型
    private final int groupCount; // GROUP BY子句中的列数
    private final boolean filter; // 聚合函数是否有FILTER子句

    /**
     * Creates an AggCallBinding.
     * 创建AggCallBinding对象。
     *
     * @param typeFactory  Type factory - 类型工厂
     * @param aggFunction  Aggregate function - 聚合函数
     * @param preOperands  Data types of pre-operands - 前操作数的数据类型
     * @param operands     Data types of operands - 操作数的数据类型
     * @param groupCount   Number of columns in the GROUP BY clause - GROUP BY子句中的列数
     * @param filter       Whether the aggregate function has a FILTER clause - 聚合函数是否有FILTER子句
     */
    public AggCallBinding(RelDataTypeFactory typeFactory, // 构造方法：创建AggCallBinding对象
        SqlAggFunction aggFunction, List<RelDataType> preOperands, // 参数：类型工厂、聚合函数、前操作数类型列表
        List<RelDataType> operands, int groupCount, // 参数：操作数类型列表、分组列数
        boolean filter) { // 参数：是否有FILTER子句
      super(typeFactory, aggFunction); // 调用父类SqlOperatorBinding的构造方法
      this.preOperands = requireNonNull(preOperands, "preOperands"); // 确保preOperands不为null
      this.operands = // 确保operands不为null
          requireNonNull(operands, // 检查operands
              "operands of aggregate call should not be null"); // 错误信息
      this.groupCount = groupCount; // 设置分组列数
      this.filter = filter; // 设置FILTER标志
      checkArgument(groupCount >= 0, // 检查分组列数是否非负
          "number of group by columns should be greater than zero in " // 错误信息
              + "aggregate call. Got %s", groupCount); // 显示实际值
    }

    @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
    public AggCallBinding(RelDataTypeFactory typeFactory, // 构造方法：旧版本，不包含preOperands参数
        SqlAggFunction aggFunction, List<RelDataType> operands, int groupCount, // 参数：类型工厂、聚合函数、操作数类型列表、分组列数
        boolean filter) { // 参数：是否有FILTER子句
      this(typeFactory, aggFunction, ImmutableList.of(), operands, groupCount, // 调用主构造方法，传入空的preOperands列表
          filter); // 传入filter参数
    }

    @Override public int getGroupCount() { // 重写getGroupCount方法，获取分组列数
      return groupCount; // 返回groupCount字段
    }

    @Override public boolean hasFilter() { // 重写hasFilter方法，判断是否有FILTER子句
      return filter; // 返回filter字段
    }

    @Override public int getPreOperandCount() { // 重写getPreOperandCount方法，获取前操作数数量
      return preOperands.size(); // 返回preOperands的大小
    }

    @Override public int getOperandCount() { // 重写getOperandCount方法，获取操作数总数
      return preOperands.size() + operands.size(); // 返回前操作数和操作数的总数
    }

    @Override public RelDataType getOperandType(int ordinal) { // 重写getOperandType方法，获取指定序号的操作数类型
      return ordinal < preOperands.size() // 如果序号小于前操作数数量
          ? preOperands.get(ordinal) // 返回前操作数类型
          : operands.get(ordinal - preOperands.size()); // 否则返回操作数类型
    }

    @Override public CalciteException newError( // 重写newError方法，创建Calcite异常
        Resources.ExInst<SqlValidatorException> e) { // 参数：错误资源实例
      return SqlUtil.newContextException(SqlParserPos.ZERO, e); // 返回带有零位置的上下文异常
    }
  }

  /** Used for PERCENTILE_DISC return type inference. */
  /** 用于PERCENTILE_DISC返回类型推断。 */
  public static class PercentileDiscAggCallBinding extends AggCallBinding { // PercentileDiscAggCallBinding类，用于PERCENTILE_DISC聚合函数的返回类型推断
    private final RelDataType collationType; // 排序类型，用于PERCENTILE_DISC函数

    PercentileDiscAggCallBinding(RelDataTypeFactory typeFactory, SqlAggFunction aggFunction, // 构造方法：创建PercentileDiscAggCallBinding对象
        List<RelDataType> operands, RelDataType collationType, int groupCount, // 参数：类型工厂、聚合函数、操作数类型列表、排序类型、分组列数
        boolean filter) { // 参数：是否有FILTER子句
      super(typeFactory, aggFunction, operands, groupCount, filter); // 调用父类AggCallBinding的构造方法
      assert aggFunction.isPercentile(); // 断言聚合函数是百分位函数
      this.collationType = collationType; // 设置排序类型
    }

    @Override public RelDataType getCollationType() { // 重写getCollationType方法，获取排序类型
      return collationType; // 返回collationType字段
    }
  }
} // Aggregate类结束
