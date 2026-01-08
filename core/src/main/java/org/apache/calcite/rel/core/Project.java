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
 */ // Apache License 2.0 许可证声明,声明代码的版权和使用许可
package org.apache.calcite.rel.core; // Project 类所在的包路径: org.apache.calcite.rel.core,属于 Calcite 核心模块的关系表达式核心包

import org.apache.calcite.linq4j.Ord; // 导入 Ord 类,用于为列表元素提供带索引的迭代器,方便在遍历时获取元素索引
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类,表示关系代数表达式所在的集群,包含类型工厂等共享资源
import org.apache.calcite.plan.RelOptCost; // 导入 RelOptCost 类,表示关系表达式的成本信息,包含 CPU、IO、行数等成本指标
import org.apache.calcite.plan.RelOptPlanner; // 导入 RelOptPlanner 类,表示查询优化器接口,用于成本计算和优化决策
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类,表示关系表达式的特征集合,如物理实现方式、排序等特征
import org.apache.calcite.rel.RelInput; // 导入 RelInput 类,用于从序列化格式反序列化关系表达式
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口,表示关系代数表达式树的节点基类
import org.apache.calcite.rel.RelWriter; // 导入 RelWriter 类,用于将关系表达式树写入输出流,用于 EXPLAIN 功能
import org.apache.calcite.rel.SingleRel; // 导入 SingleRel 类,表示只有一个子节点的关系表达式基类,Project 继承此类
import org.apache.calcite.rel.hint.Hintable; // 导入 Hintable 接口,表示支持查询提示的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入 RelHint 类,表示关系表达式的查询提示信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery 类,用于查询关系表达式的元数据,如行数、唯一性等
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 类,表示关系表达式的数据类型,如行类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入 RelDataTypeField 类,表示行类型中的单个字段信息
import org.apache.calcite.rex.RexChecker; // 导入 RexChecker 类,用于验证 Rex 表达式的正确性
import org.apache.calcite.rex.RexInputRef; // 导入 RexInputRef 类,表示对输入行的字段引用
import org.apache.calcite.rex.RexNode; // 导入 RexNode 类,表示行表达式(Row Expression)的基类,用于 SQL 表达式
import org.apache.calcite.rex.RexOver; // 导入 RexOver 类,表示窗口函数(WINDOW OVER)表达式
import org.apache.calcite.rex.RexShuttle; // 导入 RexShuttle 类,用于遍历和转换 Rex 表达式树的访问器
import org.apache.calcite.rex.RexUtil; // 导入 RexUtil 类,提供 Rex 表达式的工具方法
import org.apache.calcite.sql.SqlExplainLevel; // 导入 SqlExplainLevel 类,表示 EXPLAIN 输出的详细级别
import org.apache.calcite.tools.RelBuilder; // 导入 RelBuilder 类,用于构建关系表达式树的构建器
import org.apache.calcite.util.Litmus; // 导入 Litmus 类,用于断言和验证测试
import org.apache.calcite.util.Pair; // 导入 Pair 类,表示键值对,用于存储表达式和字段名的组合
import org.apache.calcite.util.Permutation; // 导入 Permutation 类,表示排列组合,用于字段重排序
import org.apache.calcite.util.Util; // 导入 Util 类,提供通用的工具方法
import org.apache.calcite.util.mapping.MappingType; // 导入 MappingType 类,表示映射的类型,如函数、满射、双射等
import org.apache.calcite.util.mapping.Mappings; // 导入 Mappings 类,提供字段映射的工具类

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类,表示不可变列表,保证线程安全
import com.google.common.collect.ImmutableSet; // 导入 Google Guava 的 ImmutableSet 类,表示不可变集合,保证线程安全

import org.apiguardian.api.API; // 导入 API 注解,用于标记 API 的稳定性状态
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf; // 导入 Nullness 注解,表示如果方法返回 true 则结果非空
import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解,表示参数或返回值可能为 null

import java.util.HashSet; // 导入 Java 标准库的 HashSet 类,用于存储不重复的元素集合
import java.util.List; // 导入 Java 标准库的 List 接口,表示有序列表
import java.util.Objects; // 导入 Java 标准库的 Objects 类,提供对象操作的工具方法
import java.util.Optional; // 导入 Java 标准库的 Optional 类,表示可能为空的容器对象
import java.util.Set; // 导入 Java 标准库的 Set 接口,表示不重复元素的集合

import static java.util.Objects.requireNonNull; // 导入 Java Objects 的 requireNonNull 静态方法,用于参数非空校验

/**
 * Relational expression that computes a set of
 * 'select expressions' from its input relational expression.
 * // 关系表达式,从其输入关系表达式计算一组"选择表达式"(SELECT 子句中的表达式)
 * // Project 是 SQL 中 SELECT 操作在关系代数中的表示,用于对输入数据进行投影、计算和转换
 * // 它可以包含字段引用、算术运算、函数调用等表达式,是查询中最常用的操作之一
 * // 例如: SELECT emp_id, salary * 1.1 AS new_salary FROM employees 中的投影部分
 *
 * @see org.apache.calcite.rel.logical.LogicalProject
 */ // 参见 LogicalProject 类,这是 Project 的逻辑实现
public abstract class Project extends SingleRel implements Hintable { // Project 抽象类,继承 SingleRel(单子节点关系表达式),实现 Hintable 接口(支持查询提示)
  //~ Instance fields -------------------------------------------------------- // 实例字段区域开始标记

  protected final ImmutableList<RexNode> exps; // 投影表达式列表,存储 SELECT 子句中的所有表达式,每个表达式对应输出的一列

  protected final ImmutableList<RelHint> hints; // 查询提示列表,存储优化器提示,用于指导查询优化器的决策

  protected final ImmutableSet<CorrelationId> variablesSet; // 相关变量集合,存储此投影设置的关联变量,供嵌套表达式使用(用于子查询相关)

  //~ Constructors ----------------------------------------------------------- // 构造方法区域开始标记

  /**
   * Creates a Project.
   * // 创建一个 Project 关系表达式实例
   *
   * @param cluster  Cluster that this relational expression belongs to
   * // cluster: 此关系表达式所属的集群,包含类型工厂、优化器等共享资源
   * @param traits   Traits of this relational expression
   * // traits: 此关系表达式的特征集合,如物理实现方式、排序特性等
   * @param hints    Hints of this relation expression
   * // hints: 此关系表达式的查询提示列表,用于指导优化器行为
   * @param input    Input relational expression
   * // input: 输入关系表达式,即此投影操作的数据源(如 TableScan、Filter 等)
   * @param projects List of expressions for the input columns
   * // projects: 投影表达式列表,每个表达式对应输出的一列,可以是字段引用、算术运算、函数调用等
   * @param rowType  Output row type
   * // rowType: 输出行类型,定义输出行的字段名和数据类型,必须与 projects 表达式列表一一对应
   * @param variableSet Correlation variables set by this relational expression
   *                    to be used by nested expressions
   * // variableSet: 此关系表达式设置的相关变量集合,供嵌套表达式使用(用于处理子查询相关)
   */
  @SuppressWarnings("method.invocation.invalid") // 抑制无效方法调用警告,因为父类构造函数调用是合法的
  protected Project(
      RelOptCluster cluster, // 集群参数,传递给父类构造函数
      RelTraitSet traits, // 特征集参数,传递给父类构造函数
      List<RelHint> hints, // 提示列表,存储到实例变量
      RelNode input, // 输入关系表达式,传递给父类构造函数
      List<? extends RexNode> projects, // 投影表达式列表,存储到实例变量
      RelDataType rowType, // 输出行类型,存储到实例变量
      Set<CorrelationId> variableSet) { // 相关变量集合,存储到实例变量
    super(cluster, traits, input); // 调用父类 SingleRel 的构造函数,初始化集群、特征集和输入节点
    this.exps = ImmutableList.copyOf(projects); // 将投影表达式列表转换为不可变列表,存储到 exps 字段
    this.hints = ImmutableList.copyOf(hints); // 将提示列表转换为不可变列表,存储到 hints 字段
    this.rowType = requireNonNull(rowType, "rowType"); // 校验 rowType 非空并存储,如果为空则抛出 NullPointerException
    this.variablesSet = ImmutableSet.copyOf(variableSet); // 将相关变量集合转换为不可变集合,存储到 variablesSet 字段
    assert isValid(Litmus.THROW, null); // 断言此 Project 节点有效,如果无效则抛出异常(用于开发时验证)
  }

  @Deprecated // to be removed before 2.0 // 已过时的构造方法,将在 2.0 版本前移除,建议使用包含 variableSet 参数的完整构造方法
  protected Project(
      RelOptCluster cluster, // 集群参数
      RelTraitSet traits, // 特征集参数
      List<RelHint> hints, // 提示列表参数
      RelNode input, // 输入关系表达式
      List<? extends RexNode> projects, // 投影表达式列表
      RelDataType rowType) { // 输出行类型
    this(cluster, traits, hints, input, projects, rowType, ImmutableSet.of()); // 调用完整构造方法,传入空的相关变量集合
  } // 此构造方法不指定相关变量集合,默认为空集合

  @Deprecated // to be removed before 2.0 // 已过时的构造方法,将在 2.0 版本前移除
  protected Project(RelOptCluster cluster, RelTraitSet traits,
      RelNode input, List<? extends RexNode> projects, RelDataType rowType) {
    // 集群参数,特征集参数,输入关系表达式,投影表达式列表,输出行类型
    this(cluster, traits, ImmutableList.of(), input, projects, rowType, ImmutableSet.of());
    // 调用完整构造方法,传入空的提示列表和空的相关变量集合
  } // 此构造方法不指定提示列表和相关变量集合,都默认为空

  @Deprecated // to be removed before 2.0 // 已过时的构造方法,将在 2.0 版本前移除
  protected Project(RelOptCluster cluster, RelTraitSet traitSet, RelNode input,
      List<? extends RexNode> projects, RelDataType rowType, int flags) {
    // 集群参数,特征集参数,输入关系表达式,投影表达式列表,输出行类型,标志位(已废弃)
    this(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of());
    // 调用完整构造方法,传入空的提示列表和空的相关变量集合
    Util.discard(flags); // 丢弃 flags 参数,因为该参数已废弃,仅用于兼容性
  } // 此构造方法包含 flags 参数但已废弃,flags 参数被忽略

  /**
   * Creates a Project by parsing serialized output.
   * // 通过解析序列化输出来创建 Project 实例,用于从 JSON 或其他序列化格式恢复关系表达式树
   */
  protected Project(RelInput input) { // RelInput 参数包含序列化的关系表达式数据
    this(input.getCluster(), // 从序列化输入获取集群
        input.getTraitSet(), // 从序列化输入获取特征集
        ImmutableList.of(), // 创建空的提示列表
        input.getInput(), // 从序列化输入获取输入关系表达式
        requireNonNull(input.getExpressionList("exprs"), "exprs"), // 从序列化输入获取表达式列表,校验非空
        input.getRowType("exprs", "fields"), // 从序列化输入获取行类型,基于表达式和字段名
        ImmutableSet.copyOf( // 将相关变量 ID 列表转换为不可变集合
            Util.transform( // 转换相关变量 ID 列表
                Optional.ofNullable(input.getIntegerList("variablesSet")) // 获取 variablesSet 列表,可能为 null
                    .orElse(ImmutableList.of()), // 如果为 null 则使用空列表
                id -> new CorrelationId(id)))); // 将每个整数 ID 转换为 CorrelationId 对象
  } // 此构造方法用于从序列化格式(如 JSON)反序列化 Project 节点

  //~ Methods ---------------------------------------------------------------- // 方法区域开始标记

  @Override public final RelNode copy(RelTraitSet traitSet,
      List<RelNode> inputs) { // 重写父类 RelNode 的 copy 方法,用于复制关系表达式
    // traitSet: 新的特征集,inputs: 新的输入节点列表
    return copy(traitSet, sole(inputs), exps, getRowType());
    // 调用子类实现的 copy 方法,传入特征集、唯一输入节点、原投影表达式列表和原输出行类型
  } // 此方法用于在优化过程中复制 Project 节点,通常用于应用规则转换

  /**
   * Copies a project.
   * // 复制一个 Project 节点,允许修改特征集、输入节点、投影表达式和输出行类型
   *
   * @param traitSet Traits
   * // traitSet: 新的特征集,用于指定物理实现方式等特性
   * @param input Input
   * // input: 新的输入关系表达式
   * @param projects Project expressions
   * // projects: 新的投影表达式列表
   * @param rowType Output row type
   * // rowType: 新的输出行类型
   * @return New {@code Project} if any parameter differs from the value of this
   *   {@code Project}, or just {@code this} if all the parameters are
   *   the same
   * // 返回: 如果任何参数与当前 Project 不同,则返回新的 Project 实例;如果所有参数都相同,则返回 this
   *
   * @see #copy(RelTraitSet, List)
   */ // 参见另一个 copy 方法的重载版本
  public abstract Project copy(RelTraitSet traitSet, RelNode input,
      List<RexNode> projects, RelDataType rowType); // 抽象方法,由子类(如 LogicalProject)实现,用于创建 Project 副本

  @Deprecated // to be removed before 2.0 // 已过时的 copy 方法,将在 2.0 版本前移除
  public Project copy(RelTraitSet traitSet, RelNode input,
      List<RexNode> projects, RelDataType rowType, int flags) {
    // 特征集,输入节点,投影表达式列表,输出行类型,标志位(已废弃)
    Util.discard(flags); // 丢弃 flags 参数,因为该参数已废弃
    return copy(traitSet, input, projects, rowType); // 调用不含 flags 的 copy 方法
  } // 此方法包含 flags 参数但已废弃,flags 参数被忽略

  @Deprecated // to be removed before 2.0 // 已过时的方法,将在 2.0 版本前移除
  public boolean isBoxed() { // 判断此 Project 是否是"装箱"的
    return true; // 始终返回 true,表示 Project 节点总是装箱的(即保留所有字段)
  } // 此方法已废弃,不再使用

  @Override public RelNode accept(RexShuttle shuttle) { // 重写 accept 方法,接受 RexShuttle 访问器来遍历和转换表达式
    // shuttle: RexShuttle 访问器,用于遍历和转换 Rex 表达式树
    List<RexNode> exps = shuttle.apply(this.exps); // 应用 RexShuttle 到所有投影表达式,返回转换后的表达式列表
    if (this.exps == exps) { // 如果表达式列表没有变化(引用相同)
      return this; // 返回 this,表示不需要创建新节点
    } // 如果表达式列表有变化,需要创建新的 Project 节点
    final RelDataType rowType = // 基于新的表达式列表重新计算输出行类型
        RexUtil.createStructType( // 创建结构类型(行类型)
            getInput().getCluster().getTypeFactory(), // 获取类型工厂
            exps, // 新的表达式列表
            getRowType().getFieldNames(), // 使用原字段名列表
            null); // 无额外字段信息
    return copy(traitSet, getInput(), exps, rowType); // 复制 Project 节点,使用新的表达式列表和行类型
  } // 此方法用于表达式树的转换,例如常量折叠、表达式简化等优化规则

  /**
   * Returns the project expressions.
   * // 返回投影表达式列表
   *
   * @return Project expressions
   * // 返回: 投影表达式列表,每个表达式对应输出的一列
   */
  public List<RexNode> getProjects() { // 获取投影表达式列表的方法
    return exps; // 返回存储在 exps 字段中的投影表达式列表
  } // 此方法用于访问 Project 节点的投影表达式

  /**
   * Returns a list of (expression, name) pairs. Convenient for various
   * transformations.
   * // 返回(表达式,字段名)对的列表,方便进行各种转换操作
   *
   * @return List of (expression, name) pairs
   * // 返回: (RexNode 表达式, String 字段名)对的列表
   */
  public final List<Pair<RexNode, String>> getNamedProjects() { // 获取带名称的投影表达式列表
    return Pair.zip(getProjects(), getRowType().getFieldNames());
    // 将投影表达式列表和字段名列表组合成键值对列表,每个对包含一个表达式和对应的字段名
  } // 此方法常用于需要同时访问表达式和字段名的场景,如重命名、投影下推等优化

  /** Returns a list of project expressions, each of which is wrapped in a
   * call to {@code AS} if its field name differs from the default.
   * // 返回投影表达式列表,如果字段名与默认名称不同,则将表达式包装在 AS 调用中
   *
   * <p>This method has a similar effect to {@link #getNamedProjects()},
   * but the single list is easier to manage.
   * // 此方法与 getNamedProjects() 效果类似,但返回单个列表更易于管理
   *
   * @see org.apache.calcite.tools.RelBuilder#alias(RexNode, String)
   */ // 参见 RelBuilder 的 alias 方法,用于为表达式添加别名
  // TODO: move to RelBuilder? // TODO: 将此方法移到 RelBuilder 中?
  // TODO: replace calls to getNamedProjects // TODO: 替换 getNamedProjects 的调用
  public final List<RexNode> getAliasedProjects(RelBuilder b) { // 获取带别名的投影表达式列表
    // b: RelBuilder 实例,用于构建别名表达式
    final ImmutableList.Builder<RexNode> builder = ImmutableList.builder(); // 创建不可变列表构建器
    Pair.forEach(exps, getRowType().getFieldList(), (e, f) -> // 遍历表达式和字段列表
        builder.add(b.alias(e, f.getName()))); // 为每个表达式添加别名,添加到构建器
    return builder.build(); // 构建并返回带别名的表达式列表
  } // 此方法用于确保每个表达式都有明确的别名,便于后续处理

  @Override public ImmutableList<RelHint> getHints() { // 重写 Hintable 接口的 getHints 方法
    return hints; // 返回存储在 hints 字段中的查询提示列表
  } // 此方法用于访问 Project 节点的查询提示

  @Deprecated // to be removed before 2.0 // 已过时的方法,将在 2.0 版本前移除
  public int getFlags() { // 获取标志位(已废弃)
    return 1; // 始终返回 1,表示默认标志
  } // 此方法已废弃,不再使用

  /** Returns whether this Project contains any windowed-aggregate functions. */
  // 返回此 Project 是否包含任何窗口聚合函数(WINDOW OVER 函数)
  public final boolean containsOver() { // 检查是否包含窗口函数
    return RexOver.containsOver(getProjects(), null); // 调用 RexOver.containsOver 检查投影表达式列表中是否包含 RexOver 表达式
  } // 此方法用于判断投影中是否有窗口函数,影响优化器的决策

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) { // 重写父类的 isValid 方法,验证 Project 节点的有效性
    // litmus: 断言工具,用于记录验证失败信息,context: 验证上下文,可为 null
    if (!super.isValid(litmus, context)) { // 首先调用父类的验证方法
      return litmus.fail(null); // 如果父类验证失败,返回失败
    } // 父类验证包括输入节点、特征集等基本验证
    if (!RexUtil.compatibleTypes(exps, getRowType(), litmus)) { // 检查投影表达式的类型是否与输出行类型兼容
      return litmus.fail("incompatible types"); // 如果类型不兼容,返回失败
    } // 类型兼容性检查确保表达式的计算结果类型与声明的输出类型一致
    RexChecker checker = // 创建 RexChecker 实例,用于验证表达式
        new RexChecker( // 构造表达式检查器
            getInput().getRowType(), context, litmus); // 传入输入行类型、上下文和断言工具
    for (RexNode exp : exps) { // 遍历每个投影表达式
      exp.accept(checker); // 让表达式接受检查器的访问,验证表达式正确性
      if (checker.getFailureCount() > 0) { // 如果检查器发现错误
        return litmus.fail("{} failures in expression {}", // 返回失败,并报告失败数量和表达式
            checker.getFailureCount(), exp);
      } // 检查内容包括字段引用是否有效、类型是否匹配等
    } // 遍历所有表达式进行验证
    if (!Util.isDistinct(getRowType().getFieldNames())) { // 检查输出行的字段名是否唯一
      return litmus.fail("field names not distinct: {}", rowType); // 如果字段名重复,返回失败
    } // 字段名唯一性是关系代数的基本要求
    //CHECKSTYLE: IGNORE 1 // 忽略 Checkstyle 警告
    if (false && !Util.isDistinct(Util.transform(exps, RexNode::toString))) { // 此检查当前被禁用(false &&)
      // Projecting the same expression twice is usually a bad idea,
      // // 投影相同的表达式两次通常是一个坏主意
      // because it may create expressions downstream which are equivalent
      // // 因为它可能在下游创建等效但看起来不同的表达式
      // but which look different. We can't ban duplicate projects,
      // // 但我们不能禁止重复的投影
      // because we need to allow
      // // 因为我们需要允许以下情况:
      //
      //  SELECT a, b FROM c UNION SELECT x, x FROM z
      // // UNION 操作中可能需要重复投影
      return litmus.fail("duplicate expressions: {}", exps); // 如果发现重复表达式,返回失败
    } // 此检查用于检测冗余的重复投影,但由于某些合法用例,当前被禁用
    return litmus.succeed(); // 所有验证通过,返回成功
  } // 此方法用于开发时验证 Project 节点的正确性,确保表达式和类型都有效

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) { // 重写 computeSelfCost 方法,计算 Project 节点的自身成本
    // planner: 查询优化器,mq: 元数据查询接口
    double dRows = mq.getRowCount(getInput()); // 获取输入节点的行数,作为输出行数
    double dCpu = dRows * exps.size(); // 计算 CPU 成本: 行数乘以表达式数量(每行需要计算所有表达式)
    double dIo = 0; // IO 成本为 0,因为 Project 不需要额外的 I/O 操作
    return planner.getCostFactory().makeCost(dRows, dCpu, dIo); // 创建并返回成本对象
  } // 此方法用于优化器的成本估算,帮助选择最优的执行计划

  /**
   * Returns the number of expressions at the front of an array which are
   * simply projections of the same field.
   * // 返回数组开头的表达式数量,这些表达式只是对相同字段的简单投影
   *
   * @param refs References
   * // refs: 引用列表,通常是投影表达式列表
   * @return the index of the first non-trivial expression, or list.size otherwise
   * // 返回: 第一个非平凡表达式的索引,如果所有表达式都是平凡的则返回列表大小
   */
  private static int countTrivial(List<RexNode> refs) { // 计算开头平凡投影的数量
    for (int i = 0; i < refs.size(); i++) { // 遍历引用列表
      RexNode ref = refs.get(i); // 获取第 i 个引用
      if (!(ref instanceof RexInputRef) // 如果不是输入字段引用
          || ((RexInputRef) ref).getIndex() != i) { // 或者引用的索引不等于位置索引
        return i; // 返回当前位置,表示从 i 开始是非平凡表达式
      } // 平凡表达式是指: 第 i 个表达式是对输入第 i 个字段的直接引用
    } // 例如: exps[0] 是 $0, exps[1] 是 $1, exps[2] 是 $2 + 1, 则返回 2
    return refs.size(); // 如果所有表达式都是平凡的,返回列表大小
  } // 此方法用于优化投影表达式的显示和存储,识别可以省略的平凡投影

  @Override public Set<CorrelationId> getVariablesSet() { // 重写 getVariablesSet 方法,获取相关变量集合
    return variablesSet; // 返回存储在 variablesSet 字段中的相关变量集合
  } // 此方法返回此 Project 设置的相关变量,供嵌套表达式使用

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写 explainTerms 方法,用于生成 EXPLAIN 输出
    super.explainTerms(pw); // 调用父类的 explainTerms 方法,输出基本属性
    pw.itemIf("variablesSet", variablesSet, !variablesSet.isEmpty()); // 如果相关变量集合非空,输出变量集合
    // Skip writing field names so the optimizer can reuse the projects that differ in
    // field names only
    // 跳过写入字段名,以便优化器可以重用仅字段名不同的投影
    if (pw.getDetailLevel() == SqlExplainLevel.DIGEST_ATTRIBUTES) { // 如果输出级别是 DIGEST_ATTRIBUTES(摘要级别)
      final int firstNonTrivial = countTrivial(exps); // 计算开头平凡投影的数量
      if (firstNonTrivial == 1) { // 如果只有第一个表达式是平凡的
        pw.item("inputs", "0"); // 输出 inputs=0,表示第一个字段是直接投影
      } else if (firstNonTrivial != 0) { // 如果有多个平凡表达式
        pw.item("inputs", "0.." + (firstNonTrivial - 1)); // 输出 inputs=0..n,表示前 n 个字段是直接投影
      } // 摘要级别省略平凡表达式,使输出更简洁
      if (firstNonTrivial != exps.size()) { // 如果还有非平凡表达式
        pw.item("exprs", exps.subList(firstNonTrivial, exps.size())); // 输出非平凡表达式列表
      } // 只输出需要计算的表达式
      return pw; // 返回写入器
    } // 摘要级别用于优化器内部比较,忽略字段名差异

    if (pw.nest()) { // 如果需要嵌套输出
      pw.item("fields", getRowType().getFieldNames()); // 输出字段名列表
      pw.item("exprs", exps); // 输出表达式列表
    } else { // 如果不需要嵌套输出
      for (Ord<RelDataTypeField> field : Ord.zip(getRowType().getFieldList())) { // 遍历字段列表
        String fieldName = field.e.getName(); // 获取字段名
        if (fieldName == null) { // 如果字段名为 null
          fieldName = "field#" + field.i; // 使用 "field#索引" 作为字段名
        } // 处理匿名字段的情况
        pw.item(fieldName, exps.get(field.i)); // 输出字段名和对应的表达式
      } // 以字段名为键,表达式为值输出
    } // 非嵌套格式更易读,适合用户查看

    return pw; // 返回写入器
  } // 此方法用于生成 Project 节点的 EXPLAIN 输出,支持多种输出级别

  @API(since = "1.24", status = API.Status.INTERNAL) // API 注解,标记为内部 API,从 1.24 版本开始
  @EnsuresNonNullIf(expression = "#1", result = true) // Nullness 注解,如果方法返回 true 则参数非空
  protected boolean deepEquals0(@Nullable Object obj) { // 深度比较两个 Project 对象是否相等
    // obj: 要比较的对象,可能为 null
    if (this == obj) { // 如果是同一个对象引用
      return true; // 返回 true
    } // 引用相等直接返回
    if (obj == null || getClass() != obj.getClass()) { // 如果 obj 为 null 或类型不同
      return false; // 返回 false
    } // 类型检查确保比较的是同类型的对象
    Project o = (Project) obj; // 将 obj 转换为 Project 类型
    return traitSet.equals(o.traitSet) // 比较特征集是否相等
        && input.deepEquals(o.input) // 比较输入节点是否深度相等
        && exps.equals(o.exps) // 比较投影表达式列表是否相等
        && hints.equals(o.hints) // 比较提示列表是否相等
        && getRowType().equalsSansFieldNames(o.getRowType()); // 比较行类型是否相等(忽略字段名)
  } // 此方法用于深度比较 Project 节点,用于优化器的等价类检测

  @API(since = "1.24", status = API.Status.INTERNAL) // API 注解,标记为内部 API,从 1.24 版本开始
  protected int deepHashCode0() { // 计算深度哈希码
    return Objects.hash(traitSet, input.deepHashCode(), exps, hints);
    // 使用 Objects.hash 计算组合哈希码,包含特征集、输入节点的深度哈希、表达式列表和提示列表
  } // 此方法用于深度计算 Project 节点的哈希码,用于哈希表等数据结构

  /**
   * Returns a mapping, or null if this projection is not a mapping.
   * // 返回映射关系,如果此投影不是映射则返回 null
   *
   * @return Mapping, or null if this projection is not a mapping
   * // 返回: 目标映射对象,如果投影不是简单的字段映射则返回 null
   */
  public Mappings.@Nullable TargetMapping getMapping() { // 获取此投影的映射关系
    return getMapping(getInput().getRowType().getFieldCount(), exps);
    // 调用静态 getMapping 方法,传入输入字段数量和投影表达式列表
  } // 此方法用于判断投影是否只是简单的字段重排序或子集

  /**
   * Returns a mapping of a set of project expressions.
   * // 返回一组投影表达式的映射关系
   *
   * <p>The mapping is an inverse surjection.
   * // 映射是一个逆满射(INVERSE_SURJECTION)
   * Every target has a source field, but no
   * source has more than one target.
   * // 每个目标字段都有一个源字段,但没有一个源字段对应多个目标字段
   * Thus you can safely call
   * {@link org.apache.calcite.util.mapping.Mappings.TargetMapping#getSourceOpt(int)}.
   * // 因此可以安全地调用 getSourceOpt 方法获取源字段
   *
   * @param inputFieldCount Number of input fields
   * // inputFieldCount: 输入字段的数量
   * @param projects Project expressions
   * // projects: 投影表达式列表
   * @return Mapping of a set of project expressions, or null if projection is
   * not a mapping
   * // 返回: 投影表达式的映射对象,如果不是映射则返回 null
   */
  public static Mappings.@Nullable TargetMapping getMapping(int inputFieldCount,
      List<? extends RexNode> projects) { // 静态方法,检查投影表达式是否构成映射
    // inputFieldCount: 输入字段数量,projects: 投影表达式列表
    if (inputFieldCount < projects.size()) { // 如果输入字段数少于输出字段数
      return null; // surjection is not possible // 返回 null,因为无法构成满射
    } // 逆满射要求源字段数 >= 目标字段数
    Mappings.TargetMapping mapping = // 创建目标映射对象
        Mappings.create(MappingType.INVERSE_SURJECTION, // 映射类型为逆满射
            inputFieldCount, projects.size()); // 源字段数和目标字段数
    for (Ord<RexNode> exp : Ord.<RexNode>zip(projects)) { // 遍历投影表达式
      if (!(exp.e instanceof RexInputRef)) { // 如果表达式不是输入字段引用
        return null; // 返回 null,不是简单的字段映射
      } // 只有 RexInputRef 才能构成映射
      int source = ((RexInputRef) exp.e).getIndex(); // 获取源字段索引
      if (mapping.getTargetOpt(source) != -1) { // 如果该源字段已经有目标
        return null; // 返回 null,违反了逆满射约束(一个源不能对应多个目标)
      } // 逆满射要求每个源最多对应一个目标
      mapping.set(source, exp.i); // 设置映射:源字段 -> 目标位置
    } // 遍历所有表达式
    return mapping; // 返回映射对象
  } // 此方法用于检测投影是否只是简单的字段映射,可用于优化投影下推

  /**
   * Returns a partial mapping of a set of project expressions.
   * // 返回一组投影表达式的部分映射
   *
   * <p>The mapping is an inverse function.
   * // 映射是一个逆函数(INVERSE_FUNCTION)
   * Every target has a source field, but
   * a source might have 0, 1 or more targets.
   * // 每个目标字段都有一个源字段,但一个源字段可能对应 0、1 或多个目标字段
   * Project expressions that do not consist of
   * a mapping are ignored.
   * // 不构成映射的投影表达式被忽略
   *
   * @param inputFieldCount Number of input fields
   * // inputFieldCount: 输入字段的数量
   * @param projects Project expressions
   * // projects: 投影表达式列表
   * @return Mapping of a set of project expressions, never null
   * // 返回: 投影表达式的映射对象,永远不会为 null
   */
  public static Mappings.TargetMapping getPartialMapping(int inputFieldCount,
      List<? extends RexNode> projects) { // 静态方法,获取投影表达式的部分映射
    // inputFieldCount: 输入字段数量,projects: 投影表达式列表
    Mappings.TargetMapping mapping = // 创建目标映射对象
        Mappings.create(MappingType.INVERSE_FUNCTION, // 映射类型为逆函数
            inputFieldCount, projects.size()); // 源字段数和目标字段数
    for (Ord<RexNode> exp : Ord.<RexNode>zip(projects)) { // 遍历投影表达式
      if (exp.e instanceof RexInputRef) { // 如果表达式是输入字段引用
        mapping.set(((RexInputRef) exp.e).getIndex(), exp.i); // 设置映射:源字段 -> 目标位置
      } // 忽略非 RexInputRef 的表达式
    } // 只记录简单的字段引用,忽略复杂表达式
    return mapping; // 返回映射对象(可能为空映射)
  } // 此方法用于获取投影中简单字段引用的映射关系,忽略复杂表达式

  /**
   * Returns a permutation, if this projection is merely a permutation of its
   * input fields; otherwise null.
   * // 如果此投影只是输入字段的排列组合,则返回排列对象;否则返回 null
   *
   * @return Permutation, if this projection is merely a permutation of its
   *   input fields; otherwise null
   * // 返回: 排列对象,如果投影只是字段重排序;如果不是则返回 null
   */
  public @Nullable Permutation getPermutation() { // 获取此投影的排列关系
    return getPermutation(getInput().getRowType().getFieldCount(), exps);
    // 调用静态 getPermutation 方法,传入输入字段数量和投影表达式列表
  } // 此方法用于判断投影是否只是简单的字段重排序

  /**
   * Returns a permutation, if this projection is merely a permutation of its
   * input fields; otherwise null.
   * // 如果投影只是输入字段的排列组合,则返回排列对象;否则返回 null
   */
  public static @Nullable Permutation getPermutation(int inputFieldCount,
      List<? extends RexNode> projects) { // 静态方法,检查投影是否构成排列
    // inputFieldCount: 输入字段数量,projects: 投影表达式列表
    final int fieldCount = projects.size(); // 获取输出字段数量
    if (fieldCount != inputFieldCount) { // 如果输入和输出字段数量不同
      return null; // 返回 null,排列要求字段数量相同
    } // 排列必须是双射,源和目标数量必须相等
    final Permutation permutation = new Permutation(fieldCount); // 创建排列对象
    final Set<Integer> alreadyProjected = new HashSet<>(fieldCount); // 创建已投影字段索引集合
    for (int i = 0; i < fieldCount; ++i) { // 遍历所有输出位置
      final RexNode exp = projects.get(i); // 获取第 i 个表达式
      if (exp instanceof RexInputRef) { // 如果表达式是输入字段引用
        final int index = ((RexInputRef) exp).getIndex(); // 获取源字段索引
        if (!alreadyProjected.add(index)) { // 如果该源字段已经被投影过
          return null; // 返回 null,违反了排列约束(字段不能重复)
        } // 排列要求每个源字段恰好出现一次
        permutation.set(i, index); // 设置排列:目标位置 i -> 源字段索引
      } else { // 如果表达式不是输入字段引用
        return null; // 返回 null,不是简单的字段排列
      } // 排列要求所有表达式都是 RexInputRef
    } // 遍历所有位置
    return permutation; // 返回排列对象
  } // 此方法用于检测投影是否只是字段的重新排序,可用于优化

  /**
   * Checks whether this is a functional mapping.
   * // 检查此投影是否是函数映射
   * Every output is a source field, but
   * a source field may appear as zero, one, or more output fields.
   * // 每个输出字段都是一个源字段,但一个源字段可能出现 0、1 或多次
   */
  public boolean isMapping() { // 检查投影是否是函数映射
    for (RexNode exp : exps) { // 遍历所有投影表达式
      if (!(exp instanceof RexInputRef)) { // 如果表达式不是输入字段引用
        return false; // 返回 false,不是函数映射
      } // 函数映射要求所有表达式都是 RexInputRef
    } // 遍历所有表达式
    return true; // 返回 true,是函数映射
  } // 此方法用于判断投影是否只包含字段引用,不包含计算

  //~ Inner Classes ---------------------------------------------------------- // 内部类区域开始标记

  /** No longer used. */
  // 不再使用的内部类
  @Deprecated // to be removed before 2.0 // 已过时的内部类,将在 2.0 版本前移除
  public static class Flags { // 标志位常量类,已废弃
    public static final int ANON_FIELDS = 2; // 匿名字段标志,值为 2,表示字段是匿名的
    public static final int BOXED = 1; // 装箱标志,值为 1,表示节点是装箱的
    public static final int NONE = 0; // 无标志,值为 0,表示没有特殊标志
  } // 此类已废弃,不再使用
} // Project 类定义结束
