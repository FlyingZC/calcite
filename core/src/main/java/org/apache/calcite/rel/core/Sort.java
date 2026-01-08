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
package org.apache.calcite.rel.core; // 包声明：Calcite 核心关系表达式包

import org.apache.calcite.linq4j.Ord; // Ord：为集合元素提供索引的包装类
import org.apache.calcite.plan.RelOptCluster; // RelOptCluster：关系表达式集群，包含类型系统和表达式构建器
import org.apache.calcite.plan.RelOptCost; // RelOptCost：关系操作的成本模型接口
import org.apache.calcite.plan.RelOptPlanner; // RelOptPlanner：查询优化器接口
import org.apache.calcite.plan.RelTraitSet; // RelTraitSet：关系特征集合，定义物理属性
import org.apache.calcite.rel.RelCollation; // RelCollation：排序规范，定义排序规则
import org.apache.calcite.rel.RelCollationTraitDef; // RelCollationTraitDef：排序特征定义
import org.apache.calcite.rel.RelFieldCollation; // RelFieldCollation：单个字段的排序规则
import org.apache.calcite.rel.RelInput; // RelInput：序列化的关系表达式输入
import org.apache.calcite.rel.RelNode; // RelNode：关系表达式接口
import org.apache.calcite.rel.RelWriter; // RelWriter：关系表达式写入器，用于生成查询计划字符串
import org.apache.calcite.rel.SingleRel; // SingleRel：单输入关系表达式基类
import org.apache.calcite.rel.hint.Hintable; // Hintable：支持提示信息的接口
import org.apache.calcite.rel.hint.RelHint; // RelHint：关系表达式提示信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // RelMetadataQuery：元数据查询接口
import org.apache.calcite.rex.RexLiteral; // RexLiteral：字面量表达式
import org.apache.calcite.rex.RexNode; // RexNode：行表达式接口
import org.apache.calcite.rex.RexShuttle; // RexShuttle：表达式访问者，用于遍历和修改表达式
import org.apache.calcite.util.Util; // Util：工具类，提供各种实用方法

import com.google.common.collect.ImmutableList; // ImmutableList：Google Guava 的不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // Nullable：可空类型注解

import java.util.Collections; // Collections：集合工具类
import java.util.List; // List：列表接口

import static java.util.Objects.requireNonNull; // requireNonNull：对象非空检查方法

/**
 * Relational expression that imposes a particular sort order on its input
 * without otherwise changing its content.
 * 关系表达式，在不改变其内容的情况下对其输入施加特定的排序顺序。
 * Sort 是 Calcite 中表示排序操作的抽象关系节点，它继承自 SingleRel（单输入关系节点）并实现了 Hintable 接口（支持提示信息）。
 * Sort 节点可以执行三种操作：
 * 1. 排序（ORDER BY）：按照指定的排序规则对输入数据进行排序
 * 2. 偏移（OFFSET）：跳过前 N 行数据
 * 3. 限制（FETCH/LIMIT）：只返回前 N 行数据
 * 
 * 典型的 SQL 对应：SELECT * FROM table ORDER BY col1, col2 OFFSET 10 FETCH 5
 * 
 * Sort 是一个抽象类，具体的实现在子类中，如 LogicalSort（逻辑排序）和 EnumerableSort（可枚举排序）。
 */
public abstract class Sort extends SingleRel implements Hintable {
  //~ Instance fields --------------------------------------------------------
  // 实例字段区域：定义 Sort 节点的核心属性

  // collation：排序规则对象，定义了排序的字段、排序方向（升序/降序）和空值处理方式
  // RelCollation 是一个不可变对象，包含一个或多个 RelFieldCollation，每个 RelFieldCollation 描述一个排序字段
  // 例如：ORDER BY col1 ASC, col2 DESC 对应两个 RelFieldCollation
  public final RelCollation collation; // 排序规范：定义排序字段、方向和空值处理
  public final @Nullable RexNode offset; // 偏移表达式：表示要跳过的行数（对应 SQL 的 OFFSET），可为 null 表示不跳过
  public final @Nullable RexNode fetch; // 获取表达式：表示要返回的行数（对应 SQL 的 FETCH/LIMIT），可为 null 表示返回所有行
  protected final ImmutableList<RelHint> hints; // 提示信息列表：优化器提示，用于指导查询优化器的行为

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域：提供多种构造函数来创建 Sort 节点

  /**
   * Creates a Sort.
   * 创建一个 Sort 节点（最完整的构造方法）
   *
   * @param cluster   Cluster this relational expression belongs to
   *                  关系表达式所属的集群，包含类型系统和表达式构建器
   * @param traits    Traits
   *                  关系特征集合，定义了物理属性（如排序规则、分布方式等）
   * @param hints     Hints for this node
   *                  该节点的提示信息列表，用于指导优化器
   * @param child     input relational expression
   *                  输入的关系表达式（子节点），Sort 节点只有一个输入
   * @param collation array of sort specifications
   *                  排序规范数组，定义了排序的字段、方向和空值处理
   * @param offset    Expression for number of rows to discard before returning
   *                  first row
   *                  偏移表达式，表示在返回第一行之前要跳过的行数（对应 SQL OFFSET）
   * @param fetch     Expression for number of rows to fetch
   *                  获取表达式，表示要返回的行数（对应 SQL FETCH/LIMIT）
   */
  protected Sort(
      RelOptCluster cluster, // 关系表达式集群
      RelTraitSet traits, // 关系特征集合
      List<RelHint> hints, // 提示信息列表
      RelNode child, // 输入关系表达式
      RelCollation collation, // 排序规范
      @Nullable RexNode offset, // 偏移表达式
      @Nullable RexNode fetch) { // 获取表达式
    super(cluster, traits, child); // 调用父类 SingleRel 的构造方法，设置集群、特征和子节点
    this.collation = collation; // 保存排序规范
    this.offset = offset; // 保存偏移表达式
    this.fetch = fetch; // 保存获取表达式
    this.hints = ImmutableList.copyOf(hints); // 创建提示信息的不可变副本

    // 断言：特征集合必须包含适用的排序规范
    assert traits.containsIfApplicable(collation)
            : "traits=" + traits + ", collation=" + collation;
    // 断言：不能创建无意义的 Sort 节点（没有排序字段、没有偏移、没有获取）
    assert !(fetch == null
            && offset == null
            && collation.getFieldCollations().isEmpty())
            : "trivial sort"; // 这种 Sort 节点没有任何操作，是多余的
  }

  /**
   * Creates a Sort.
   * 创建一个仅包含排序的 Sort 节点（没有 OFFSET 和 FETCH）
   *
   * @param cluster   Cluster this relational expression belongs to
   *                  关系表达式所属的集群
   * @param traits    Traits
   *                  关系特征集合
   * @param child     input relational expression
   *                  输入的关系表达式
   * @param collation array of sort specifications
   *                  排序规范数组
   */
  protected Sort(
      RelOptCluster cluster, // 关系表达式集群
      RelTraitSet traits, // 关系特征集合
      RelNode child, // 输入关系表达式
      RelCollation collation) { // 排序规范
    // 调用完整构造方法，使用空的提示列表，offset 和 fetch 都设为 null
    this(cluster, traits, Collections.emptyList(), child, collation, null, null);
  }

  /**
   * Creates a Sort.
   * 创建一个包含排序、偏移和获取的 Sort 节点（没有提示信息）
   *
   * @param cluster   Cluster this relational expression belongs to
   *                  关系表达式所属的集群
   * @param traits    Traits
   *                  关系特征集合
   * @param child     input relational expression
   *                  输入的关系表达式
   * @param collation array of sort specifications
   *                  排序规范数组
   * @param offset    Expression for number of rows to discard before returning
   *                  first row
   *                  偏移表达式
   * @param fetch     Expression for number of rows to fetch
   *                  获取表达式
   */
  protected Sort(
      RelOptCluster cluster, // 关系表达式集群
      RelTraitSet traits, // 关系特征集合
      RelNode child, // 输入关系表达式
      RelCollation collation, // 排序规范
      @Nullable RexNode offset, // 偏移表达式
      @Nullable RexNode fetch) { // 获取表达式
    // 调用完整构造方法，使用空的提示列表
    this(cluster, traits, Collections.emptyList(), child, collation, offset, fetch);
  }

  /**
   * Creates a Sort by parsing serialized output.
   * 通过解析序列化输出来创建 Sort 节点（用于从 JSON 等格式反序列化）
   */
  protected Sort(RelInput input) { // RelInput 包含序列化的关系表达式信息
    // 从 RelInput 中提取各个参数并调用完整构造方法
    this(input.getCluster(), // 获取集群
        input.getTraitSet().plus(input.getCollation()), // 获取特征集并添加排序规范
        input.getInput(), // 获取输入关系表达式
        RelCollationTraitDef.INSTANCE.canonize(input.getCollation()), // 规范化排序规范
        input.getExpression("offset"), // 获取 offset 表达式
        input.getExpression("fetch")); // 获取 fetch 表达式
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域：定义 Sort 节点的核心功能

  // copy 方法：创建 Sort 节点的副本，用于优化器重写和转换
  @Override public final Sort copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写父类的 copy 方法
    return copy(traitSet, sole(inputs), collation, offset, fetch); // 调用子类实现的 copy 方法，sole(inputs) 获取唯一的输入节点
  }

  // copy 方法：创建 Sort 节点的副本，允许修改排序规范
  public final Sort copy(RelTraitSet traitSet, RelNode newInput, // 新的特征集合和输入节点
      RelCollation newCollation) { // 新的排序规范
    return copy(traitSet, newInput, newCollation, offset, fetch); // 调用子类实现的 copy 方法，保持 offset 和 fetch 不变
  }

  // 抽象方法：创建 Sort 节点的副本，允许修改所有参数
  // 子类必须实现此方法来创建具体的 Sort 节点副本
  public abstract Sort copy(RelTraitSet traitSet, RelNode newInput, // 新的特征集合和输入节点
      RelCollation newCollation, @Nullable RexNode offset, @Nullable RexNode fetch); // 新的排序规范、偏移和获取

  /** {@inheritDoc}
   * 计算排序操作的自身成本
   *
   * <p>The CPU cost of a Sort has three main cases:
   * Sort 的 CPU 成本主要有三种情况：
   *
   * <ul>
   * <li>If {@code fetch} is zero, CPU cost is zero; otherwise,
   * 如果 fetch 为零，CPU 成本为零；否则，
   *
   * <li>if the sort keys are empty, we don't need to sort, only step over
   * the rows, and therefore the CPU cost is
   * {@code min(fetch + offset, inputRowCount) * bytesPerRow}; otherwise
   * 如果排序键为空，我们不需要排序，只需要遍历行，因此 CPU 成本是
   * {@code min(fetch + offset, inputRowCount) * bytesPerRow}；否则
   *
   * <li>we need to read and sort {@code inputRowCount} rows, with at most
   * {@code min(fetch + offset, inputRowCount)} of them in the sort data
   * structure at a time, giving a CPU cost of {@code inputRowCount *
   * log(min(fetch + offset, inputRowCount)) * bytesPerRow}.
   * 我们需要读取并排序 {@code inputRowCount} 行，在排序数据结构中最多保留
   * {@code min(fetch + offset, inputRowCount)} 行，因此 CPU 成本是
   * {@code inputRowCount * log(min(fetch + offset, inputRowCount)) * bytesPerRow}。
   * </ul>
   *
   * <p>The cost model factors in row width via {@code bytesPerRow}, because
   * sorts need to move rows around, not just compare them; by making the cost
   * higher if rows are wider, we discourage pushing a Project through a Sort.
   * 成本模型通过 {@code bytesPerRow} 考虑行宽度，因为排序需要移动行，而不仅仅是比较它们；
   * 通过使更宽的行成本更高，我们阻止将 Project 推过 Sort。
   * We assume that each field is 4 bytes, and we add 3 'virtual fields' to
   * represent the per-row overhead. Thus a 1-field row is (3 + 1) * 4 = 16
   * bytes; a 5-field row is (3 + 5) * 4 = 32 bytes.
   * 我们假设每个字段是 4 字节，并添加 3 个"虚拟字段"来表示每行的开销。
   * 因此，1 字段的行是 (3 + 1) * 4 = 16 字节；5 字段的行是 (3 + 5) * 4 = 32 字节。
   *
   * <p>The cost model does not consider a 5-field sort to be more expensive
   * than, say, a 2-field sort, because both sorts will compare just one field
   * most of the time. */
  // 成本模型不认为 5 字段排序比 2 字段排序更昂贵，因为两种排序大多数时间只比较一个字段。
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写成本计算方法
      RelMetadataQuery mq) { // 元数据查询对象，用于获取行数等信息
    final double offsetValue = Util.first(doubleValue(offset), 0d); // 获取 offset 的值，如果为 null 则默认为 0
    assert offsetValue >= 0 : "offset should not be negative:" + offsetValue; // 断言 offset 不能为负数

    final double inCount = mq.getRowCount(input); // 获取输入节点的行数估计
    @Nullable Double fetchValue = doubleValue(fetch); // 获取 fetch 的值，可能为 null
    final double readCount; // 定义实际读取的行数
    if (fetchValue == null) { // 如果 fetch 为 null，表示没有限制
      readCount = inCount; // 读取所有行
    } else if (fetchValue <= 0) { // 如果 fetch 小于等于 0
      // Case 1. Read zero rows from input, therefore CPU cost is zero.
      // 情况 1：从输入中读取零行，因此 CPU 成本为零。
      return planner.getCostFactory().makeCost(inCount, 0, 0); // 返回零 CPU 成本
    } else { // fetch 大于 0
      readCount = Math.min(inCount, offsetValue + fetchValue); // 读取行数为输入行数和（offset+fetch）的最小值
    }

    final double bytesPerRow = (3 + getRowType().getFieldCount()) * 4; // 计算每行的字节数（3 个虚拟字段 + 实际字段）* 4 字节

    final double cpu; // 定义 CPU 成本
    if (collation.getFieldCollations().isEmpty()) { // 如果排序键为空
      // Case 2. If sort keys are empty, CPU cost is cheaper because we are just
      // stepping over the first "readCount" rows, rather than sorting all
      // "inCount" them. (Presumably we are applying FETCH and/or OFFSET,
      // otherwise this Sort is a no-op.)
      // 情况 2：如果排序键为空，CPU 成本更低，因为我们只是遍历前 "readCount" 行，
      // 而不是对所有 "inCount" 行进行排序。（大概我们正在应用 FETCH 和/或 OFFSET，
      // 否则这个 Sort 是无操作的。）
      cpu = readCount * bytesPerRow; // CPU 成本 = 读取行数 * 每行字节数
    } else { // 有排序键
      // Case 3. Read and sort all "inCount" rows, keeping "readCount" in the
      // sort data structure at a time.
      // 情况 3：读取并排序所有 "inCount" 行，在排序数据结构中一次最多保留 "readCount" 行。
      cpu = Util.nLogM(inCount, readCount) * bytesPerRow; // CPU 成本 = n * log(m) * 每行字节数（排序算法的时间复杂度）
    }
    return planner.getCostFactory().makeCost(readCount, cpu, 0); // 返回成本对象（行数、CPU 成本、IO 成本）
  }

  // accept 方法：接受 RexShuttle 访问者，用于遍历和修改表达式
  @Override public RelNode accept(RexShuttle shuttle) { // RexShuttle 是表达式访问者，可以修改表达式
    RexNode offset = shuttle.apply(this.offset); // 应用访问者到 offset 表达式，可能返回修改后的表达式
    RexNode fetch = shuttle.apply(this.fetch); // 应用访问者到 fetch 表达式，可能返回修改后的表达式
    List<RexNode> originalSortExps = getSortExps(); // 获取原始的排序表达式列表
    List<RexNode> sortExps = shuttle.apply(originalSortExps); // 应用访问者到排序表达式列表
    assert sortExps == originalSortExps // 断言：排序表达式不能被修改
        : "Sort node does not support modification of input field expressions."
          + " Old expressions: " + originalSortExps + ", new ones: " + sortExps; // Sort 节点不支持修改输入字段表达式
    if (offset == this.offset // 如果 offset 没有变化
        && fetch == this.fetch) { // 并且 fetch 也没有变化
      return this; // 返回当前节点，不需要创建副本
    }
    return copy(traitSet, getInput(), collation, offset, fetch); // 创建并返回修改后的副本
  }

  // isEnforcer 方法：判断此 Sort 节点是否是强制执行器
  // 强制执行器是指仅用于确保排序顺序的 Sort 节点，不执行 OFFSET 或 FETCH 操作
  @Override public boolean isEnforcer() { // 重写父类的 isEnforcer 方法
    return offset == null && fetch == null // 如果没有 offset 和 fetch
        && !collation.getFieldCollations().isEmpty(); // 并且有排序字段，则是强制执行器
  }

  /**
   * Returns the array of {@link RelFieldCollation}s asked for by the sort
   * specification, from most significant to least significant.
   * 返回排序规范要求的 {@link RelFieldCollation} 数组，从最重要到最不重要。
   *
   * <p>See also {@link RelMetadataQuery#collations(RelNode)},
   * which lists all known collations. For example,
   * <code>ORDER BY time_id</code> might also be sorted by
   * <code>the_year, the_month</code> because of a known monotonicity
   * constraint among the columns. {@code getCollation} would return
   * <code>[time_id]</code> and {@code collations} would return
   * <code>[ [time_id], [the_year, the_month] ]</code>.
   * 另请参见 {@link RelMetadataQuery#collations(RelNode)}，它列出了所有已知的排序规则。
   * 例如，<code>ORDER BY time_id</code> 可能也按 <code>the_year, the_month</code> 排序，
   * 因为列之间存在已知的单调性约束。
   * {@code getCollation} 将返回 <code>[time_id]</code>，
   * 而 {@code collations} 将返回 <code>[ [time_id], [the_year, the_month] ]</code>。
   */
  public RelCollation getCollation() { // 获取排序规范
    return collation; // 返回排序规范对象
  }

  /** Returns the sort expressions.
   * 返回排序表达式列表。
   * 将排序字段转换为 RexNode 表达式，用于表达式访问者模式。 */
  public List<RexNode> getSortExps() { // 获取排序表达式列表
    //noinspection StaticPseudoFunctionalStyleMethod
    return Util.transform(collation.getFieldCollations(), field -> // 遍历所有排序字段
        getCluster().getRexBuilder().makeInputRef(input, // 为每个字段创建输入引用表达式
            requireNonNull(field, "field").getFieldIndex())); // 获取字段索引
  }

  // explainTerms 方法：生成关系表达式的解释信息，用于调试和查询计划展示
  @Override public RelWriter explainTerms(RelWriter pw) { // RelWriter 用于构建查询计划字符串
    super.explainTerms(pw); // 调用父类的 explainTerms 方法，输出基本属性
    if (pw.nest()) { // 如果使用嵌套格式
      pw.item("collation", collation); // 直接输出排序规范对象
    } else { // 使用扁平格式
      for (Ord<RexNode> ord : Ord.zip(getSortExps())) { // 遍历排序表达式
        pw.item("sort" + ord.i, ord.e); // 输出每个排序表达式（如 sort0, sort1）
      }
      for (Ord<RelFieldCollation> ord // 遍历排序字段
          : Ord.zip(collation.getFieldCollations())) {
        if (!pw.expand()) { // 如果不展开详细信息
          pw.item("dir" + ord.i, ord.e.shortString()); // 输出简短的排序方向（如 ASC, DESC）
        } else { // 展开详细信息
          pw.item("dir" + ord.i, ord.e.fullString()); // 输出完整的排序方向（包括空值处理）
        }
      }
    }
    pw.itemIf("offset", offset, offset != null); // 如果 offset 不为 null，输出 offset 表达式
    pw.itemIf("fetch", fetch, fetch != null); // 如果 fetch 不为 null，输出 fetch 表达式
    return pw; // 返回 RelWriter 对象
  }

  /** Returns the double value of a node if it is a literal, otherwise null.
   * 如果节点是字面量，则返回其 double 值，否则返回 null。
   * 用于成本计算中提取 offset 和 fetch 的数值。 */
  private static @Nullable Double doubleValue(@Nullable RexNode r) { // 静态方法：获取表达式的 double 值
    return r instanceof RexLiteral // 如果表达式是字面量
        ? ((RexLiteral) r).getValueAs(Double.class) // 返回其 double 值
        : null; // 否则返回 null
  }

  // getHints 方法：获取提示信息列表
  @Override public ImmutableList<RelHint> getHints() { // 实现 Hintable 接口的方法
    return hints; // 返回提示信息列表
  }
} // Sort 类结束
