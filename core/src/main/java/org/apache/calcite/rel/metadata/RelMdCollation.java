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
package org.apache.calcite.rel.metadata; // 包声明：定义了RelMdCollation类所在的包，属于元数据处理包

import org.apache.calcite.adapter.enumerable.EnumerableCorrelate; // 导入：可枚举相关联操作符，用于处理相关联查询
import org.apache.calcite.adapter.enumerable.EnumerableHashJoin; // 导入：可枚举哈希连接实现
import org.apache.calcite.adapter.enumerable.EnumerableLimit; // 导入：可枚举限制操作符，用于限制返回的行数
import org.apache.calcite.adapter.enumerable.EnumerableMergeJoin; // 导入：可枚举归并连接实现
import org.apache.calcite.adapter.enumerable.EnumerableMergeUnion; // 导入：可枚举归并联合操作符
import org.apache.calcite.adapter.enumerable.EnumerableNestedLoopJoin; // 导入：可枚举嵌套循环连接实现
import org.apache.calcite.adapter.jdbc.JdbcToEnumerableConverter; // 导入：JDBC到可枚举的转换器
import org.apache.calcite.linq4j.Ord; // 导入：LINQ4J工具类，用于处理带索引的元素
import org.apache.calcite.plan.RelOptTable; // 导入：关系优化表接口
import org.apache.calcite.plan.hep.HepRelVertex; // 导入：HepPlanner的顶点表示
import org.apache.calcite.plan.volcano.RelSubset; // 导入：Volcano优化器的关系子集
import org.apache.calcite.rel.RelCollation; // 导入：关系排序特征，表示数据的排序方式
import org.apache.calcite.rel.RelCollationTraitDef; // 导入：关系排序特征定义
import org.apache.calcite.rel.RelCollations; // 导入：关系排序工具类
import org.apache.calcite.rel.RelFieldCollation; // 导入：字段排序，描述单个字段的排序方向和空值处理
import org.apache.calcite.rel.RelNode; // 导入：关系节点接口，所有关系代数操作的基类
import org.apache.calcite.rel.core.Calc; // 导入：计算操作符，结合了投影和过滤
import org.apache.calcite.rel.core.Filter; // 导入：过滤操作符，用于过滤数据行
import org.apache.calcite.rel.core.Join; // 导入：连接操作符，用于连接两个关系
import org.apache.calcite.rel.core.JoinRelType; // 导入：连接类型枚举（内连接、左连接、右连接等）
import org.apache.calcite.rel.core.Match; // 导入：模式匹配操作符，用于行模式识别
import org.apache.calcite.rel.core.Project; // 导入：投影操作符，用于选择和计算列
import org.apache.calcite.rel.core.Sort; // 导入：排序操作符，用于对数据进行排序
import org.apache.calcite.rel.core.SortExchange; // 导入：排序交换操作符，用于分布式排序
import org.apache.calcite.rel.core.TableModify; // 导入：表修改操作符，用于INSERT/UPDATE/DELETE操作
import org.apache.calcite.rel.core.TableScan; // 导入：表扫描操作符，用于从表中读取数据
import org.apache.calcite.rel.core.Values; // 导入：常量值操作符，用于生成常量行
import org.apache.calcite.rel.core.Window; // 导入：窗口操作符，用于窗口函数计算
import org.apache.calcite.rel.type.RelDataType; // 导入：关系数据类型接口
import org.apache.calcite.rex.RexCall; // 导入：表达式调用，表示函数或操作符调用
import org.apache.calcite.rex.RexCallBinding; // 导入：表达式调用绑定，用于类型检查和单调性推断
import org.apache.calcite.rex.RexInputRef; // 导入：输入引用表达式，引用输入行的某个字段
import org.apache.calcite.rex.RexLiteral; // 导入：字面量表达式，表示常量值
import org.apache.calcite.rex.RexNode; // 导入：表达式节点基类
import org.apache.calcite.rex.RexProgram; // 导入：表达式程序，包含投影和过滤的表达式集合
import org.apache.calcite.sql.validate.SqlMonotonicity; // 导入：SQL单调性，描述表达式的单调性属性
import org.apache.calcite.util.ImmutableBitSet; // 导入：不可变位集合，用于表示字段索引集合
import org.apache.calcite.util.ImmutableIntList; // 导入：不可变整数列表，用于存储字段索引
import org.apache.calcite.util.Pair; // 导入：键值对工具类
import org.apache.calcite.util.Util; // 导入：通用工具类

import com.google.common.collect.ImmutableList; // 导入：Google Guava不可变列表
import com.google.common.collect.LinkedListMultimap; // 导入：Google Guava链表多重映射
import com.google.common.collect.Multimap; // 导入：Google Guava多重映射接口
import com.google.common.collect.Ordering; // 导入：Google Guava排序工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：可空性注解

import java.util.ArrayList; // 导入：Java集合框架-动态数组列表
import java.util.Collection; // 导入：Java集合框架-集合接口
import java.util.HashMap; // 导入：Java集合框架-哈希映射
import java.util.List; // 导入：Java集合框架-列表接口
import java.util.Map; // 导入：Java集合框架-映射接口
import java.util.NavigableSet; // 导入：Java集合框架-可导航集合
import java.util.SortedSet; // 导入：Java集合框架-有序集合
import java.util.TreeSet; // 导入：Java集合框架-树集合实现
import java.util.stream.Collectors; // 导入：Java流API-收集器工具

import static java.util.Objects.requireNonNull; // 导入：静态导入-对象工具方法的requireNonNull方法

/**
 * RelMdCollation supplies a default implementation of
 * {@link org.apache.calcite.rel.metadata.RelMetadataQuery#collations}
 * for the standard logical algebra.
 * RelMdCollation为标准逻辑代数提供了RelMetadataQuery#collations的默认实现
 * 该类负责计算和推断各种关系操作符(RelNode)的排序属性(Collation)
 * 排序属性描述了关系操作符输出的数据是否按照某些字段排序
 * 这个信息对于查询优化器非常重要，可以避免不必要的排序操作
 * 例如：如果输入已经按照某个字段排序，那么后续的ORDER BY操作就可以被优化掉
 */
public class RelMdCollation
    implements MetadataHandler<BuiltInMetadata.Collation> { // 实现元数据处理器接口，处理Collation（排序）元数据
  public static final RelMetadataProvider SOURCE = // 静态常量：元数据提供者，用于反射调用排序元数据方法
      ReflectiveRelMetadataProvider.reflectiveSource( // 使用反射创建元数据提供者
          new RelMdCollation(), BuiltInMetadata.Collation.Handler.class); // 传入RelMdCollation实例和处理器类

  //~ Constructors -----------------------------------------------------------

  private RelMdCollation() {} // 私有构造函数：防止外部实例化，采用单例模式

  //~ Methods ----------------------------------------------------------------

  @Override public MetadataDef<BuiltInMetadata.Collation> getDef() { // 重写接口方法：获取元数据定义
    return BuiltInMetadata.Collation.DEF; // 返回Collation元数据的定义
  }

  /** Catch-all implementation for
   * {@link BuiltInMetadata.Collation#collations()},
   * invoked using reflection, for any relational expression not
   * handled by a more specific method.
   * 这是BuiltInMetadata.Collation#collations()的通用实现，
   * 通过反射调用，用于处理没有更特定方法的关系表达式
   *
   * <p>{@link org.apache.calcite.rel.core.Union},
   * {@link org.apache.calcite.rel.core.Intersect},
   * {@link org.apache.calcite.rel.core.Minus},
   * {@link org.apache.calcite.rel.core.Join},
   * {@link org.apache.calcite.rel.core.Correlate}
   * do not in general return sorted results
   * (but implementations using particular algorithms may).
   * Union、Intersect、Minus、Join、Correlate等操作符通常不返回排序结果
   * （但使用特定算法的实现可能会返回排序结果）
   *
   * @param rel Relational expression 关系表达式参数
   * @return Relational expression's collations 返回关系表达式的排序属性列表
   *
   * @see org.apache.calcite.rel.metadata.RelMetadataQuery#collations(RelNode)
   */
  public @Nullable ImmutableList<RelCollation> collations(RelNode rel, // 方法：获取任意关系节点的排序属性，返回null表示无排序
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于递归查询子节点的元数据
    return null; // 返回null：表示默认情况下没有排序属性
  }

  private static <E> @Nullable ImmutableList<E> copyOf(@Nullable Collection<? extends E> values) { // 私有静态方法：将集合转换为不可变列表
    return values == null ? null : ImmutableList.copyOf(values); // 如果集合为null返回null，否则返回不可变副本
  }

  public @Nullable ImmutableList<RelCollation> collations(Window rel, // 方法：获取Window操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf(window(mq, rel.getInput(), rel.groups)); // 调用window辅助方法并复制结果，Window保持输入的排序
  }

  public @Nullable ImmutableList<RelCollation> collations(Match rel, // 方法：获取Match操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用match辅助方法并复制结果
        match(mq, rel.getInput(), rel.getRowType(), rel.getPattern(), // 传入输入、行类型、模式等参数
            rel.isStrictStart(), rel.isStrictEnd(), // 传入严格开始和严格结束标志
            rel.getPatternDefinitions(), rel.getMeasures(), rel.getAfter(), // 传入模式定义、度量、after子句
            rel.getSubsets(), rel.isAllRows(), rel.getPartitionKeys(), // 传入子集、所有行标志、分区键
            rel.getOrderKeys(), rel.getInterval())); // 传入排序键和间隔
  }

  public @Nullable ImmutableList<RelCollation> collations(Filter rel, // 方法：获取Filter操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return mq.collations(rel.getInput()); // 过滤操作不改变排序，直接返回输入的排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(TableModify rel, // 方法：获取TableModify操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return mq.collations(rel.getInput()); // 表修改操作不改变排序，直接返回输入的排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(TableScan scan, // 方法：获取TableScan操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    final BuiltInMetadata.Collation.Handler handler = // 尝试从表中获取Collation处理器
        scan.getTable().unwrap(BuiltInMetadata.Collation.Handler.class); // 解包表对象获取处理器
    if (handler != null) { // 如果找到了自定义的Collation处理器
      return handler.collations(scan, mq); // 使用自定义处理器获取排序属性
    }
    return copyOf(table(scan.getTable())); // 否则调用table辅助方法获取表的默认排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(EnumerableMergeJoin join, // 方法：获取EnumerableMergeJoin的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    // In general a join is not sorted. But a merge join preserves the sort
    // order of the left and right sides.
    // 通常连接不保证排序，但归并连接保持左右两侧的排序顺序
    return copyOf( // 调用mergeJoin辅助方法并复制结果
        RelMdCollation.mergeJoin(mq, join.getLeft(), join.getRight(), // 传入左右输入节点
            join.analyzeCondition().leftKeys, join.analyzeCondition().rightKeys, // 传入连接条件分析的左右键
            join.getJoinType())); // 传入连接类型
  }

  public @Nullable ImmutableList<RelCollation> collations(EnumerableHashJoin join, // 方法：获取EnumerableHashJoin的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用enumerableHashJoin辅助方法并复制结果
        RelMdCollation.enumerableHashJoin(mq, join.getLeft(), join.getRight(), join.getJoinType())); // 传入左右输入和连接类型
  }

  public @Nullable ImmutableList<RelCollation> collations(EnumerableNestedLoopJoin join, // 方法：获取EnumerableNestedLoopJoin的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用enumerableNestedLoopJoin辅助方法并复制结果
        RelMdCollation.enumerableNestedLoopJoin(mq, join.getLeft(), join.getRight(), // 传入左右输入节点
            join.getJoinType())); // 传入连接类型
  }

  public @Nullable ImmutableList<RelCollation> collations(EnumerableMergeUnion mergeUnion, // 方法：获取EnumerableMergeUnion的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    final RelCollation collation = mergeUnion.getTraitSet().getCollation(); // 从特征集中获取排序属性
    if (collation == null) { // 如果排序属性为null
      // should not happen 不应该发生
      return null; // 返回null
    }
    // MergeUnion guarantees order, like a sort MergeUnion保证顺序，类似于排序
    return copyOf(RelMdCollation.sort(collation)); // 调用sort辅助方法并复制结果
  }

  public @Nullable ImmutableList<RelCollation> collations(EnumerableCorrelate join, // 方法：获取EnumerableCorrelate的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用enumerableCorrelate辅助方法并复制结果
        RelMdCollation.enumerableCorrelate(mq, join.getLeft(), join.getRight(), // 传入左右输入节点
            join.getJoinType())); // 传入连接类型
  }

  public @Nullable ImmutableList<RelCollation> collations(EnumerableLimit rel, // 方法：获取EnumerableLimit的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return mq.collations(rel.getInput()); // 限制操作不改变排序，直接返回输入的排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(Sort sort, // 方法：获取Sort操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用sort辅助方法并复制结果
        RelMdCollation.sort(sort.getCollation())); // 传入Sort操作符的排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(SortExchange sort, // 方法：获取SortExchange操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用sort辅助方法并复制结果
        RelMdCollation.sort(sort.getCollation())); // 传入SortExchange操作符的排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(Project project, // 方法：获取Project操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用project辅助方法并复制结果
        project(mq, project.getInput(), project.getProjects())); // 传入输入节点和投影表达式列表
  }

  public @Nullable ImmutableList<RelCollation> collations(Calc calc, // 方法：获取Calc操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf(calc(mq, calc.getInput(), calc.getProgram())); // 调用calc辅助方法并复制结果，传入输入和程序
  }

  public @Nullable ImmutableList<RelCollation> collations(Values values, // 方法：获取Values操作符的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 调用values辅助方法并复制结果
        values(mq, values.getRowType(), values.getTuples())); // 传入行类型和元组列表
  }

  public @Nullable ImmutableList<RelCollation> collations(JdbcToEnumerableConverter rel, // 方法：获取JdbcToEnumerableConverter的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return mq.collations(rel.getInput()); // 转换操作不改变排序，直接返回输入的排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(HepRelVertex rel, // 方法：获取HepRelVertex的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return mq.collations(rel.stripped()); // 获取剥离后的关系节点的排序属性
  }

  public @Nullable ImmutableList<RelCollation> collations(RelSubset rel, // 方法：获取RelSubset的排序属性
      RelMetadataQuery mq) { // 参数：元数据查询对象
    return copyOf( // 复制结果
        requireNonNull( // 确保不为null
            rel.getTraitSet().getTraits(RelCollationTraitDef.INSTANCE))); // 从特征集中获取Collation特征
  }

  // Helper methods 辅助方法部分

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.TableScan}'s collation.
   * 辅助方法：确定TableScan的排序属性 */
  public static @Nullable List<RelCollation> table(RelOptTable table) { // 方法：获取表的排序属性
    return table.getCollationList(); // 返回表的排序属性列表
  }

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.Snapshot}'s collation.
   * 辅助方法：确定Snapshot的排序属性 */
  public static @Nullable List<RelCollation> snapshot(RelMetadataQuery mq, RelNode input) { // 方法：获取快照的排序属性
    return mq.collations(input); // 快照保持输入的排序属性
  }

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.Sort}'s collation.
   * 辅助方法：确定Sort的排序属性 */
  public static List<RelCollation> sort(RelCollation collation) { // 方法：获取排序操作符的排序属性
    return ImmutableList.of(collation); // 返回包含单个排序属性的不可变列表
  }

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.Filter}'s collation.
   * 辅助方法：确定Filter的排序属性 */
  public static @Nullable List<RelCollation> filter(RelMetadataQuery mq, RelNode input) { // 方法：获取过滤操作符的排序属性
    return mq.collations(input); // 过滤保持输入的排序属性
  }

  /** Helper method to determine a
   * limit's collation.
   * 辅助方法：确定limit的排序属性 */
  public static @Nullable List<RelCollation> limit(RelMetadataQuery mq, RelNode input) { // 方法：获取限制操作符的排序属性
    return mq.collations(input); // 限制保持输入的排序属性
  }

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.Calc}'s collation.
   * 辅助方法：确定Calc的排序属性 */
  public static @Nullable List<RelCollation> calc(RelMetadataQuery mq, RelNode input, // 方法：获取Calc操作符的排序属性
      RexProgram program) { // 参数：Rex程序，包含投影和过滤表达式
    final List<RexNode> projects = // 获取投影表达式列表
        program
            .getProjectList() // 获取程序中的投影列表
            .stream() // 转换为流
            .map(program::expandLocalRef) // 展开局部引用为完整表达式
            .collect(Collectors.toList()); // 收集为列表
    return project(mq, input, projects); // 调用project辅助方法处理投影
  }

  /** Helper method to determine a {@link Project}'s collation. */
  public static @Nullable List<RelCollation> project(RelMetadataQuery mq,
      RelNode input, List<? extends RexNode> projects) {
    final NavigableSet<RelCollation> collations = new TreeSet<>();
    final List<RelCollation> inputCollations = mq.collations(input);
    if (inputCollations == null || inputCollations.isEmpty()) {
      return ImmutableList.of();
    }
    final Multimap<Integer, Integer> targets = LinkedListMultimap.create();
    final Map<Integer, SqlMonotonicity> targetsWithMonotonicity =
        new HashMap<>();
    for (Ord<RexNode> project : Ord.<RexNode>zip(projects)) {
      if (project.e instanceof RexInputRef) {
        targets.put(((RexInputRef) project.e).getIndex(), project.i);
      } else if (project.e instanceof RexCall) {
        final RexCall call = (RexCall) project.e;
        final RexCallBinding binding =
            RexCallBinding.create(input.getCluster().getTypeFactory(), call, inputCollations);
        targetsWithMonotonicity.put(project.i, call.getOperator().getMonotonicity(binding));
      }
    }
    List<List<RelFieldCollation>> fieldCollationsList = new ArrayList<>();
  loop:
    for (RelCollation ic : inputCollations) {
      if (ic.getFieldCollations().isEmpty()) {
        continue;
      }
      fieldCollationsList.clear();
      fieldCollationsList.add(new ArrayList<>());
      for (RelFieldCollation ifc : ic.getFieldCollations()) {
        final Collection<Integer> integers = targets.get(ifc.getFieldIndex());
        if (integers.isEmpty()) {
          continue loop; // cannot do this collation
        }
        fieldCollationsList = fieldCollationsList.stream()
            .flatMap(fieldCollations -> integers.stream()
                .map(integer -> {
                  List<RelFieldCollation> newFieldCollations = new ArrayList<>(fieldCollations);
                  newFieldCollations.add(ifc.withFieldIndex(integer));
                  return newFieldCollations;
                })).collect(Collectors.toList());
      }
      assert !fieldCollationsList.isEmpty();
      for (List<RelFieldCollation> fieldCollations : fieldCollationsList) {
        assert !fieldCollations.isEmpty();
        collations.add(RelCollations.of(fieldCollations));
      }
    }

    final List<RelFieldCollation> fieldCollationsForRexCalls =
        new ArrayList<>();
    for (Map.Entry<Integer, SqlMonotonicity> entry
        : targetsWithMonotonicity.entrySet()) {
      final SqlMonotonicity value = entry.getValue();
      switch (value) {
      case NOT_MONOTONIC:
      case CONSTANT:
        break;
      default:
        fieldCollationsForRexCalls.add(
            new RelFieldCollation(entry.getKey(),
                RelFieldCollation.Direction.of(value)));
        break;
      }
    }

    if (!fieldCollationsForRexCalls.isEmpty()) {
      collations.add(RelCollations.of(fieldCollationsForRexCalls));
    }

    return copyOf(collations);
  }

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.Window}'s collation.
   *
   * <p>A Window projects the fields of its input first, followed by the output
   * from each of its windows. Assuming (quite reasonably) that the
   * implementation does not re-order its input rows, then any collations of its
   * input are preserved. */
  public static @Nullable List<RelCollation> window(RelMetadataQuery mq, RelNode input,
      ImmutableList<Window.Group> groups) {
    return mq.collations(input);
  }

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.Match}'s collation. */
  public static @Nullable List<RelCollation> match(RelMetadataQuery mq, RelNode input,
       RelDataType rowType, RexNode pattern,
       boolean strictStart, boolean strictEnd,
       Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures,
       RexNode after, Map<String, ? extends SortedSet<String>> subsets,
       boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys,
       @Nullable RexNode interval) {
    return mq.collations(input);
  }

  /** Helper method to determine a
   * {@link org.apache.calcite.rel.core.Values}'s collation.
   *
   * <p>We actually under-report the collations. A Values with 0 or 1 rows - an
   * edge case, but legitimate and very common - is ordered by every permutation
   * of every subset of the columns.
   *
   * <p>So, our algorithm aims to:<ul>
   *   <li>produce at most N collations (where N is the number of columns);
   *   <li>make each collation as long as possible;
   *   <li>do not repeat combinations already emitted -
   *       if we've emitted {@code (a, b)} do not later emit {@code (b, a)};
   *   <li>probe the actual values and make sure that each collation is
   *      consistent with the data
   * </ul>
   *
   * <p>So, for an empty Values with 4 columns, we would emit
   * {@code (a, b, c, d), (b, c, d), (c, d), (d)}. */
  public static List<RelCollation> values(RelMetadataQuery mq,
      RelDataType rowType, ImmutableList<ImmutableList<RexLiteral>> tuples) {
    Util.discard(mq); // for future use
    final List<RelCollation> list = new ArrayList<>();
    final int n = rowType.getFieldCount();
    final List<Pair<RelFieldCollation, Ordering<List<RexLiteral>>>> pairs =
        new ArrayList<>();
  outer:
    for (int i = 0; i < n; i++) {
      pairs.clear();
      for (int j = i; j < n; j++) {
        final RelFieldCollation fieldCollation = new RelFieldCollation(j);
        Ordering<List<RexLiteral>> comparator = comparator(fieldCollation);
        Ordering<List<RexLiteral>> ordering;
        if (pairs.isEmpty()) {
          ordering = comparator;
        } else {
          ordering = Util.last(pairs).right.compound(comparator);
        }
        pairs.add(Pair.of(fieldCollation, ordering));
        if (!ordering.isOrdered(tuples)) {
          if (j == i) {
            continue outer;
          }
          pairs.remove(pairs.size() - 1);
        }
      }
      if (!pairs.isEmpty()) {
        list.add(RelCollations.of(Pair.left(pairs)));
      }
    }
    return list;
  }

  public static Ordering<List<RexLiteral>> comparator(
      RelFieldCollation fieldCollation) {
    final int nullComparison = fieldCollation.nullDirection.nullComparison;
    final int x = fieldCollation.getFieldIndex();
    switch (fieldCollation.direction) {
    case ASCENDING:
      return new Ordering<List<RexLiteral>>() {
        @Override public int compare(List<RexLiteral> o1, List<RexLiteral> o2) {
          final Comparable c1 = o1.get(x).getValueAs(Comparable.class);
          final Comparable c2 = o2.get(x).getValueAs(Comparable.class);
          return RelFieldCollation.compare(c1, c2, nullComparison);
        }
      };
    default:
      return new Ordering<List<RexLiteral>>() {
        @Override public int compare(List<RexLiteral> o1, List<RexLiteral> o2) {
          final Comparable c1 = o1.get(x).getValueAs(Comparable.class);
          final Comparable c2 = o2.get(x).getValueAs(Comparable.class);
          return RelFieldCollation.compare(c2, c1, -nullComparison);
        }
      };
    }
  }

  /** Helper method to determine a {@link Join}'s collation assuming that it
   * uses a merge-join algorithm.
   *
   * <p>If the inputs are sorted on other keys <em>in addition to</em> the join
   * key, the result preserves those collations too.
   *
   * @deprecated Use {@link #mergeJoin(RelMetadataQuery, RelNode, RelNode, ImmutableIntList, ImmutableIntList, JoinRelType)} */
  @Deprecated // to be removed before 2.0
  public static @Nullable List<RelCollation> mergeJoin(RelMetadataQuery mq,
      RelNode left, RelNode right,
      ImmutableIntList leftKeys, ImmutableIntList rightKeys) {
    return mergeJoin(mq, left, right, leftKeys, rightKeys, JoinRelType.INNER);
  }

  /** Helper method to determine a {@link Join}'s collation assuming that it
   * uses a merge-join algorithm.
   *
   * <p>If the inputs are sorted on other keys <em>in addition to</em> the join
   * key, the result preserves those collations too. */
  public static @Nullable List<RelCollation> mergeJoin(RelMetadataQuery mq,
      RelNode left, RelNode right,
      ImmutableIntList leftKeys, ImmutableIntList rightKeys, JoinRelType joinType) {
    assert EnumerableMergeJoin.isMergeJoinSupported(joinType)
        : "EnumerableMergeJoin unsupported for join type " + joinType;

    final ImmutableList<RelCollation> leftCollations = mq.collations(left);
    if (!joinType.projectsRight()) {
      return leftCollations;
    }
    if (leftCollations == null) {
      return null;
    }

    final ImmutableList<RelCollation> rightCollations = mq.collations(right);
    if (rightCollations == null) {
      return leftCollations;
    }

    final ImmutableList.Builder<RelCollation> builder = ImmutableList.builder();
    builder.addAll(leftCollations);

    final int leftFieldCount = left.getRowType().getFieldCount();
    for (RelCollation collation : rightCollations) {
      builder.add(RelCollations.shift(collation, leftFieldCount));
    }
    return builder.build();
  }

  /**
   * Returns the collation of {@link EnumerableHashJoin} based on its inputs and the join type.
   */
  public static @Nullable List<RelCollation> enumerableHashJoin(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    if (joinType == JoinRelType.SEMI) {
      return enumerableSemiJoin(mq, left, right);
    } else {
      return enumerableJoin0(mq, left, right, joinType);
    }
  }

  /**
   * Returns the collation of {@link EnumerableNestedLoopJoin}
   * based on its inputs and the join type.
   */
  public static @Nullable List<RelCollation> enumerableNestedLoopJoin(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    return enumerableJoin0(mq, left, right, joinType);
  }

  public static @Nullable List<RelCollation> enumerableCorrelate(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    // The current implementation always preserve the sort order of the left input
    return mq.collations(left);
  }

  public static @Nullable List<RelCollation> enumerableSemiJoin(RelMetadataQuery mq,
      RelNode left, RelNode right) {
    // The current implementation always preserve the sort order of the left input
    return mq.collations(left);
  }

  @SuppressWarnings("unused")
  public static @Nullable List<RelCollation> enumerableBatchNestedLoopJoin(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    // The current implementation always preserve the sort order of the left input
    return mq.collations(left);
  }

  @SuppressWarnings("unused")
  private static @Nullable List<RelCollation> enumerableJoin0(RelMetadataQuery mq,
      RelNode left, RelNode right, JoinRelType joinType) {
    // The current implementation can preserve the sort order of the left input if one of the
    // following conditions hold:
    // (i) join type is INNER or LEFT;
    // (ii) RelCollation always orders nulls last.
    final ImmutableList<RelCollation> leftCollations = mq.collations(left);
    if (leftCollations == null) {
      return null;
    }
    switch (joinType) {
    case SEMI:
    case ANTI:
    case INNER:
    case LEFT:
    case ASOF:
    case LEFT_ASOF:
      return leftCollations;
    case RIGHT:
    case FULL:
      for (RelCollation collation : leftCollations) {
        for (RelFieldCollation field : collation.getFieldCollations()) {
          if (!(RelFieldCollation.NullDirection.LAST == field.nullDirection)) {
            return null;
          }
        }
      }
      return leftCollations;
    default:
      break;
    }
    return null;
  }
}
