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
package org.apache.calcite.rel.core; // 包声明：Values类所在的包，位于org.apache.calcite.rel.core包中

import org.apache.calcite.plan.RelOptCluster; // 导入：RelOptCluster类，表示关系表达式所属的集群，包含类型工厂等共享资源
import org.apache.calcite.plan.RelOptCost; // 导入：RelOptCost类，表示关系操作的成本估算
import org.apache.calcite.plan.RelOptPlanner; // 导入：RelOptPlanner类，表示关系优化器，用于成本计算和规则匹配
import org.apache.calcite.plan.RelOptRule; // 导入：RelOptRule类，表示优化规则，用于转换关系表达式树
import org.apache.calcite.plan.RelTraitSet; // 导入：RelTraitSet类，表示关系表达式的特征集合（如物理实现方式）
import org.apache.calcite.rel.AbstractRelNode; // 导入：AbstractRelNode类，Values继承自该抽象类，提供关系表达式的基础实现
import org.apache.calcite.rel.RelInput; // 导入：RelInput类，用于从序列化输入创建关系表达式
import org.apache.calcite.rel.RelWriter; // 导入：RelWriter类，用于将关系表达式写入输出流（如explain输出）
import org.apache.calcite.rel.hint.Hintable; // 导入：Hintable接口，Values实现该接口以支持提示（hints）
import org.apache.calcite.rel.hint.RelHint; // 导入：RelHint类，表示关系表达式上的提示信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入：RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入：RelDataType类，表示关系类型（行类型）
import org.apache.calcite.rel.type.RelDataTypeField; // 导入：RelDataTypeField类，表示关系类型中的字段
import org.apache.calcite.rex.RexDigestIncludeType; // 导入：RexDigestIncludeType枚举，控制Rex表达式摘要中是否包含类型信息
import org.apache.calcite.rex.RexLiteral; // 导入：RexLiteral类，表示行表达式中的字面量值
import org.apache.calcite.sql.SqlExplainLevel; // 导入：SqlExplainLevel枚举，表示EXPLAIN输出的详细程度
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入：SqlTypeUtil类，提供SQL类型相关的工具方法
import org.apache.calcite.util.Pair; // 导入：Pair类，表示有序对，用于将两个对象组合在一起

import com.google.common.collect.ImmutableList; // 导入：ImmutableList类，Google Guava提供的不可变列表实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：Nullable注解，用于标记可能为null的返回值

import java.util.Collections; // 导入：Collections类，提供集合操作的工具方法
import java.util.List; // 导入：List接口，表示有序列表
import java.util.function.Predicate; // 导入：Predicate函数式接口，表示谓词（布尔值函数）
import java.util.stream.Collectors; // 导入：Collectors类，提供Stream的收集器方法

/**
 * Relational expression whose value is a sequence of zero or more literal row
 * values. 关系表达式，其值是零个或多个字面量行值的序列
 * 
 * Values类表示SQL中的VALUES子句，例如：VALUES (1, 'a'), (2, 'b')
 * 它是一个抽象类，表示一个包含常量数据的关系操作，不需要从外部数据源读取数据
 * 
 * 主要作用：
 * 1. 表示常量数据集合，常用于测试、数据初始化或作为子查询
 * 2. 在查询优化中，Values节点可以被识别并特殊处理（如空值优化）
 * 3. 支持提示（hints）以指导优化器的决策
 * 
 * 特点：
 * - 继承自AbstractRelNode，是关系表达式树的一个节点
 * - 实现Hintable接口，支持优化器提示
 * - 包含一个或多个字面量行，每行包含固定数量的字面量值
 * - 所有行的类型必须与声明的行类型一致
 */
public abstract class Values extends AbstractRelNode implements Hintable { // Values类声明：抽象类，继承AbstractRelNode并实现Hintable接口

  public static final Predicate<? super Values> IS_EMPTY_J = Values::isEmpty; // 静态常量：Java 8风格的谓词，用于判断Values是否为空（不包含任何元组），用于规则匹配

  protected final ImmutableList<RelHint> hints; // 成员变量：不可变的提示列表，存储应用于此Values节点的优化器提示

  @SuppressWarnings("Guava") // 注解：抑制Guava相关的警告
  @Deprecated // to be removed before 2.0 // 注解：标记为已弃用，将在2.0版本前移除
  public static final com.google.common.base.Predicate<? super Values> // 静态常量：Guava风格的谓词（已弃用），用于判断Values是否为空
      IS_EMPTY = Values::isEmpty; // 赋值：引用isEmpty静态方法作为谓词实现

  @SuppressWarnings("Guava") // 注解：抑制Guava相关的警告
  @Deprecated // to be removed before 2.0 // 注解：标记为已弃用，将在2.0版本前移除
  public static final com.google.common.base.Predicate<? super Values> // 静态常量：Guava风格的谓词（已弃用），用于判断Values是否非空
      IS_NOT_EMPTY = Values::isNotEmpty; // 赋值：引用isNotEmpty静态方法作为谓词实现

  //~ Instance fields -------------------------------------------------------- // 分隔符：实例字段部分的开始标记

  public final ImmutableList<ImmutableList<RexLiteral>> tuples; // 成员变量：不可变的元组列表，外层列表包含所有行，内层列表包含每行的字面量值，例如：[[1, 'a'], [2, 'b']]表示两行数据

  //~ Constructors ----------------------------------------------------------- // 分隔符：构造方法部分的开始标记

  /**
   * Creates a new Values. 创建一个新的Values实例
   *
   * <p>Note that tuples passed in become owned by 注意：传入的元组列表将被此关系表达式拥有（不进行深拷贝）
   * this rel (without a deep copy), so caller must not modify them after this 调用者在此调用后不得修改它们，否则会发生错误
   * call, otherwise bad things will happen.
   *
   * @param cluster Cluster that this relational expression belongs to 参数：cluster，此关系表达式所属的集群，包含类型工厂等共享资源
   * @param hints   Hints for this node 参数：hints，应用于此节点的提示列表
   * @param rowType Row type for tuples produced by this rel 参数：rowType，由此关系表达式产生的元组的行类型，定义了每行的字段名和类型
   * @param tuples  2-dimensional array of tuple values to be produced; outer 参数：tuples，要产生的元组值的二维数组；外层列表包含元组
   *                list contains tuples; each inner list is one tuple; all 每个内层列表是一个元组；所有元组必须具有相同的长度，符合rowType
   *                tuples must be of same length, conforming to rowType
   */
  @SuppressWarnings("method.invocation.invalid") // 注解：抑制方法调用无效的警告（因为在构造函数中调用抽象方法）
  protected Values( // 构造方法：Values类的完整构造函数，包含所有参数
      RelOptCluster cluster, // 参数：集群对象，提供类型工厂等共享资源
      List<RelHint> hints, // 参数：提示列表，用于指导优化器
      RelDataType rowType, // 参数：行类型，定义输出行的结构
      ImmutableList<ImmutableList<RexLiteral>> tuples, // 参数：元组列表，包含实际的常量数据
      RelTraitSet traits) { // 参数：特征集合，定义此关系表达式的物理实现特征
    super(cluster, traits); // 调用父类构造函数：初始化AbstractRelNode，传入集群和特征集合
    this.rowType = rowType; // 赋值：保存行类型到成员变量
    this.tuples = tuples; // 赋值：保存元组列表到成员变量（注意：不进行深拷贝，调用者不得修改）
    this.hints = ImmutableList.copyOf(hints); // 赋值：创建提示列表的不可变副本并保存
    assert assertRowType(); // 断言：验证所有元组与行类型匹配，确保类型安全性
  }

  /**
   * Creates a new Values. 创建一个新的Values实例（无提示版本）
   *
   * <p>Note that tuples passed in become owned by 注意：传入的元组列表将被此关系表达式拥有（不进行深拷贝）
   * this rel (without a deep copy), so caller must not modify them after this 调用者在此调用后不得修改它们，否则会发生错误
   * call, otherwise bad things will happen.
   *
   * @param cluster Cluster that this relational expression belongs to 参数：cluster，此关系表达式所属的集群，包含类型工厂等共享资源
   * @param rowType Row type for tuples produced by this rel 参数：rowType，由此关系表达式产生的元组的行类型，定义了每行的字段名和类型
   * @param tuples  2-dimensional array of tuple values to be produced; outer 参数：tuples，要产生的元组值的二维数组；外层列表包含元组
   *                list contains tuples; each inner list is one tuple; all 每个内层列表是一个元组；所有元组必须具有相同的长度，符合rowType
   *                tuples must be of same length, conforming to rowType
   */
  @SuppressWarnings("method.invocation.invalid") // 注解：抑制方法调用无效的警告（因为在构造函数中调用抽象方法）
  protected Values( // 构造方法：Values类的简化构造函数，不包含提示参数
      RelOptCluster cluster, // 参数：集群对象，提供类型工厂等共享资源
      RelDataType rowType, // 参数：行类型，定义输出行的结构
      ImmutableList<ImmutableList<RexLiteral>> tuples, // 参数：元组列表，包含实际的常量数据
      RelTraitSet traits) { // 参数：特征集合，定义此关系表达式的物理实现特征
    this(cluster, Collections.emptyList(), rowType, tuples, traits); // 调用完整构造函数：传入空列表作为提示参数
  }

  /**
   * Creates a Values by parsing serialized output. 通过解析序列化输出来创建Values实例
   * 
   * 此构造函数用于从序列化格式（如JSON）反序列化Values节点
   * 通常用于查询计划的持久化和恢复
   */
  protected Values(RelInput input) { // 构造方法：从RelInput对象创建Values实例
    this(input.getCluster(), input.getRowType("type"), // 调用完整构造函数：从input中提取集群、行类型、元组和特征集合
        input.getTuples("tuples"), input.getTraitSet()); // 参数说明：从input中获取type字段作为行类型，tuples字段作为元组列表
  }

  //~ Methods ---------------------------------------------------------------- // 分隔符：方法部分的开始标记

  /** Predicate, to be used when defining an operand of a {@link RelOptRule}, 谓词，用于定义RelOptRule的操作数时使用
   * that returns true if a Values contains zero tuples. 如果Values包含零个元组则返回true
   *
   * <p>This is the conventional way to represent an empty relational 这是表示空关系表达式的常规方式
   * expression. There are several rules that recognize empty relational 有多个规则可以识别空关系表达式
   * expressions and prune away that section of the tree. 并修剪掉树的该部分（优化掉空值节点）
   * 
   * 用途：
   * - 在优化规则中作为匹配条件，识别空的Values节点
   * - 空Values节点可以被优化器移除，因为它们不产生任何数据
   */
  public static boolean isEmpty(Values values) { // 静态方法：判断给定的Values是否为空（不包含任何元组）
    return values.getTuples().isEmpty(); // 返回：如果元组列表为空则返回true，否则返回false
  }

  /** Predicate, to be used when defining an operand of a {@link RelOptRule}, 谓词，用于定义RelOptRule的操作数时使用
   * that returns true if a Values contains one or more tuples. 如果Values包含一个或多个元组则返回true
   *
   * <p>This is the conventional way to represent an empty relational 这是表示空关系表达式的常规方式（原文可能有误，应该是非空）
   * expression. There are several rules that recognize empty relational 有多个规则可以识别空关系表达式
   * expressions and prune away that section of the tree. 并修剪掉树的该部分（优化掉空值节点）
   */
  public static boolean isNotEmpty(Values values) { // 静态方法：判断给定的Values是否非空（包含至少一个元组）
    return !isEmpty(values); // 返回：如果Values不为空则返回true，否则返回false
  }

  public static boolean isSingleValue(Values values) { // 静态方法：判断给定的Values是否只包含单个元组（单行数据）
    return values.tuples.size() == 1; // 返回：如果元组列表大小为1则返回true，否则返回false
  }

  public ImmutableList<ImmutableList<RexLiteral>> getTuples(RelInput input) { // 方法：从RelInput对象中获取元组列表（用于反序列化）
    return input.getTuples("tuples"); // 返回：从input中获取名为"tuples"的元组列表
  }

  /** Returns the rows of literals represented by this Values relational 返回由此Values关系表达式表示的字面量行
   * expression. */
  public ImmutableList<ImmutableList<RexLiteral>> getTuples() { // 方法：获取此Values节点包含的所有元组（行数据）
    return tuples; // 返回：元组列表的引用（注意：返回的是不可变列表，但内容可能被外部修改，需谨慎使用）
  }

  /** Returns true if all tuples match rowType; otherwise, assert on 如果所有元组都与rowType匹配则返回true；否则在断言失败
   * mismatch. */
  private boolean assertRowType() { // 私有方法：验证所有元组的类型是否与声明的行类型匹配
    RelDataType rowType = getRowType(); // 获取：声明行类型
    for (List<RexLiteral> tuple : tuples) { // 遍历：对每个元组进行类型检查
      assert tuple.size() == rowType.getFieldCount(); // 断言：元组的列数必须与行类型的字段数相等
      for (Pair<RexLiteral, RelDataTypeField> pair // 遍历：对元组中的每个字面量与对应的字段类型进行配对检查
          : Pair.zip(tuple, rowType.getFieldList())) { // 使用Pair.zip将字面量列表与字段列表配对
        RexLiteral literal = pair.left; // 获取：当前字面量值
        RelDataType fieldType = pair.right.getType(); // 获取：当前字段的类型

        // TODO jvs 19-Feb-2006: strengthen this a bit.  For example, 待办事项：需要加强此检查，例如
        // overflow, rounding, and padding/truncation must already have 溢出、舍入和填充/截断必须已经处理
        // been dealt with.
        if (!RexLiteral.isNullLiteral(literal)) { // 判断：如果字面量不是NULL值
          assert SqlTypeUtil.canAssignFrom(fieldType, literal.getType()) // 断言：字段类型必须能够接受字面量的类型（类型兼容性检查）
              : "to " + fieldType + " from " + literal; // 断言失败信息：显示目标类型和源类型
        } // 如果是NULL值，则跳过类型检查（NULL可以赋值给任何类型）
      } // 结束字段遍历
    } // 结束元组遍历
    return true; // 返回：如果所有断言都通过，返回true
  }

  @Override protected RelDataType deriveRowType() { // 方法：派生行类型（AbstractRelNode的抽象方法实现）
    if (rowType == null) { // 判断：如果行类型为null
      throw new AssertionError("rowType must not be null for " + this); // 抛出：断言错误，rowType不能为null
    } // 行类型不为null
    return rowType; // 返回：声明的行类型
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法：计算自身成本（RelNode接口方法实现）
      RelMetadataQuery mq) { // 参数：planner为优化器，mq为元数据查询对象
    double dRows = mq.getRowCount(this); // 获取：行数（元组数量）作为成本计算的行数部分

    // Assume CPU is negligible since values are precomputed. 假设CPU成本可以忽略，因为值是预先计算的（常量数据）
    double dCpu = 1; // 设置：CPU成本为1（极小值，表示几乎不需要CPU计算）
    double dIo = 0; // 设置：IO成本为0（不需要从磁盘或网络读取数据）
    return planner.getCostFactory().makeCost(dRows, dCpu, dIo); // 返回：创建成本对象，包含行数、CPU和IO成本
  }

  // implement RelNode // 注释：实现RelNode接口
  @Override public double estimateRowCount(RelMetadataQuery mq) { // 方法：估算行数（RelNode接口方法实现）
    return tuples.size(); // 返回：元组列表的大小，即常量数据的行数
  }

  // implement RelNode // 注释：实现RelNode接口
  @Override public RelWriter explainTerms(RelWriter pw) { // 方法：输出关系表达式的解释信息（RelNode接口方法实现）
    // A little adapter just to get the tuples to come out 一个适配器，用于让元组以花括号而不是方括号输出
    // with curly brackets instead of square brackets.  Plus 并且增加更多空格以提高可读性
    // more whitespace for readability.
    RelDataType rowType = getRowType(); // 获取：行类型
    RelWriter relWriter = super.explainTerms(pw) // 调用：父类的explainTerms方法，获取基础解释信息
        // For rel digest, include the row type since a rendered 对于关系摘要，包含行类型，因为渲染的
        // literal may leave the type ambiguous (e.g. "null"). 字面量可能使类型不明确（例如"null"）
        .itemIf("type", rowType, // 条件添加：如果是摘要级别，添加行类型信息
            pw.getDetailLevel() == SqlExplainLevel.DIGEST_ATTRIBUTES) // 判断：当详细级别为DIGEST_ATTRIBUTES时
        .itemIf("type", rowType.getFieldList(), pw.nest()); // 条件添加：如果嵌套输出，添加字段列表
    if (pw.nest()) { // 判断：如果是嵌套输出模式
      pw.item("tuples", tuples); // 添加：直接输出元组列表（使用默认格式）
    } else { // 非嵌套输出模式
      pw.item("tuples", // 添加：格式化输出元组列表
          tuples.stream() // 流式处理：对元组列表进行流式转换
              .map(row -> row.stream() // 映射：对每行进行流式处理
                  .map(lit -> lit.computeDigest(RexDigestIncludeType.NO_TYPE)) // 映射：计算每个字面量的摘要（不包含类型信息）
                  .collect(Collectors.joining(", ", "{ ", " }"))) // 收集：将字面量用逗号连接，包裹在花括号中
              .collect(Collectors.joining(", ", "[", "]"))); // 收集：将所有行用逗号连接，包裹在方括号中，形成最终格式
    } // 格式示例：[{ 1, 'a' }, { 2, 'b' }]
    return relWriter; // 返回：RelWriter对象，支持链式调用
  }

  @Override public ImmutableList<RelHint> getHints() { // 方法：获取提示列表（Hintable接口方法实现）
    return hints; // 返回：不可变的提示列表
  }
} // 类结束
