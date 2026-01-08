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
package org.apache.calcite.rel.core; // 声明包名，该类属于org.apache.calcite.rel.core包，是Calcite核心关系表达式包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，包含类型工厂和元数据查询等信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost接口，表示关系表达式的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，表示优化器 planner
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合（如分布、排序等）
import org.apache.calcite.rel.BiRel; // 导入BiRel抽象类，表示具有两个子节点的关系表达式（Join继承自此类）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式的基本接口
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系表达式写入输出流（如调试输出）
import org.apache.calcite.rel.hint.Hintable; // 导入Hintable接口，表示可以接受提示的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示优化器提示
import org.apache.calcite.rel.metadata.RelMdUtil; // 导入RelMdUtil工具类，提供关系表达式元数据查询的实用方法
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，表示关系数据类型的字段
import org.apache.calcite.rex.RexChecker; // 导入RexChecker类，用于检查行表达式的有效性
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式（Row Expression）
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle类，用于遍历和转换行表达式
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，表示SQL类型名称（如BOOLEAN, INTEGER等）
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SqlValidatorUtil工具类，提供SQL验证相关的实用方法
import org.apache.calcite.util.Litmus; // 导入Litmus枚举，用于验证测试的通过/失败状态
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用的实用方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava的ImmutableSet类，表示不可变集合

import org.apiguardian.api.API; // 导入API注解，用于标记API的稳定性和版本
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf; // 导入空值检查注解，表示如果方法返回true则表达式非空
import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，表示可能为null

import java.util.Collections; // 导入Java标准库的Collections工具类
import java.util.List; // 导入Java标准库的List接口
import java.util.Objects; // 导入Java标准库的Objects工具类
import java.util.Set; // 导入Java标准库的Set接口

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于参数非空检查

/**
 * Relational expression that combines two relational expressions according to
 * some condition.
 * 根据某个条件组合两个关系表达式的关系表达式
 *
 * <p>Each output row has columns from the left and right inputs.
 * The set of output rows is a subset of the cartesian product of the two
 * inputs; precisely which subset depends on the join condition.
 * 每个输出行都包含左输入和右输入的列
 * 输出行的集合是两个输入的笛卡尔积的子集；具体是哪个子集取决于连接条件
 */
public abstract class Join extends BiRel implements Hintable { // Join抽象类，表示连接操作，继承自BiRel（双输入关系表达式），实现Hintable接口（支持优化器提示）
  //~ Instance fields --------------------------------------------------------
  // 实例字段区域：存储Join对象的成员变量

  protected final RexNode condition; // 连接条件，表示连接两个关系的布尔表达式，基于左右输入的字段构建
  protected final ImmutableSet<CorrelationId> variablesSet; // 关联变量集合，表示左侧关系设置且右侧关系使用的变量集合，这些变量对当前Join节点上方的节点不可见
  protected final ImmutableList<RelHint> hints; // 优化器提示列表，包含用于指导优化器决策的提示信息

  /**
   * Values must be of enumeration {@link JoinRelType}, except that
   * {@link JoinRelType#RIGHT} is disallowed.
   * 值必须是JoinRelType枚举类型，但不允许RIGHT类型（因为RIGHT JOIN会被转换为LEFT JOIN）
   */
  protected final JoinRelType joinType; // 连接类型，表示连接的类型（INNER, LEFT, SEMI等），但不允许RIGHT类型

  protected final JoinInfo joinInfo; // 连接信息对象，包含对连接条件的分析结果，如等值连接条件、非等值连接条件等

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域：定义Join对象的构造方法

  /**
   * Creates a Join.
   * 创建一个Join对象
   *
   * @param cluster          Cluster，关系表达式集群，包含类型工厂、元数据查询等共享信息
   * @param traitSet         Trait set，特征集合，定义了关系表达式的物理特性（如分布、排序等）
   * @param hints            Hints，优化器提示列表，用于指导优化器的决策
   * @param left             Left input，左侧输入关系表达式
   * @param right            Right input，右侧输入关系表达式
   * @param condition        Join condition，连接条件，基于左右输入字段构建的布尔表达式
   * @param joinType         Join type，连接类型（INNER, LEFT, SEMI等）
   * @param variablesSet     variables that are set by the LHS and used by the RHS and are not available to nodes above this Join in the tree
   *                         左侧设置且右侧使用的变量集合，这些变量对Join节点上方的节点不可见
   */
  protected Join(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      List<RelHint> hints, // 参数：优化器提示列表
      RelNode left, // 参数：左侧输入关系
      RelNode right, // 参数：右侧输入关系
      RexNode condition, // 参数：连接条件
      Set<CorrelationId> variablesSet, // 参数：关联变量集合
      JoinRelType joinType) { // 参数：连接类型
    super(cluster, traitSet, left, right); // 调用父类BiRel的构造方法，初始化集群、特征集合和左右输入
    this.condition = requireNonNull(condition, "condition"); // 验证连接条件非空，否则抛出NullPointerException
    this.variablesSet = ImmutableSet.copyOf(variablesSet); // 将变量集合转换为不可变集合
    this.joinType = requireNonNull(joinType, "joinType"); // 验证连接类型非空，否则抛出NullPointerException
    this.joinInfo = JoinInfo.createWithStrictEquality(left, right, condition); // 创建连接信息对象，分析连接条件（包括等值条件和非等值条件）
    this.hints = ImmutableList.copyOf(hints); // 将提示列表转换为不可变列表
  }

  @Deprecated // to be removed before 2.0 // 标记为废弃，将在2.0版本前移除
  protected Join(
      RelOptCluster cluster, RelTraitSet traitSet, RelNode left, // 参数：集群、特征集合、左侧输入
      RelNode right, RexNode condition, Set<CorrelationId> variablesSet, // 参数：右侧输入、连接条件、关联变量集合
      JoinRelType joinType) { // 参数：连接类型
    this(cluster, traitSet, ImmutableList.of(), left, right, // 调用主构造方法，传入空的提示列表
        condition, variablesSet, joinType); // 传入连接条件、变量集合和连接类型
  }

  @Deprecated // to be removed before 2.0 // 标记为废弃，将在2.0版本前移除
  protected Join(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode left, // 参数：左侧输入关系
      RelNode right, // 参数：右侧输入关系
      RexNode condition, // 参数：连接条件
      JoinRelType joinType, // 参数：连接类型
      Set<String> variablesStopped) { // 参数：已停止的变量名称集合（旧版使用字符串表示变量）
    this(cluster, traitSet, ImmutableList.of(), left, right, condition, // 调用主构造方法，传入空的提示列表
        CorrelationId.setOf(variablesStopped), joinType); // 将字符串集合转换为CorrelationId集合，传入连接类型
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域：定义Join类的各种方法

  @Override public RelNode accept(RexShuttle shuttle) { // 重写accept方法，接受RexShuttle访问器来遍历和转换行表达式
    RexNode condition = shuttle.apply(this.condition); // 使用shuttle访问器对连接条件进行转换
    if (this.condition == condition) { // 如果连接条件没有被修改（引用相等）
      return this; // 直接返回当前对象，不需要创建新对象
    }
    return copy(traitSet, condition, left, right, joinType, isSemiJoinDone()); // 如果条件被修改，创建一个新的Join副本，使用新的条件
  }

  public RexNode getCondition() { // 获取连接条件的方法
    return condition; // 返回连接条件（行表达式）
  }

  public JoinRelType getJoinType() { // 获取连接类型的方法
    return joinType; // 返回连接类型（INNER, LEFT, SEMI等）
  }

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) { // 重写isValid方法，验证Join对象的有效性
    if (!super.isValid(litmus, context)) { // 首先调用父类的验证方法
      return false; // 如果父类验证失败，直接返回false
    }
    if (getRowType().getFieldCount() // 检查输出行的字段数量是否正确
        != getSystemFieldList().size() // 系统字段数量
        + left.getRowType().getFieldCount() // 左侧输入的字段数量
        + (joinType.projectsRight() ? right.getRowType().getFieldCount() : 0)) { // 右侧输入的字段数量（如果连接类型需要投影右侧）
      return litmus.fail("field count mismatch"); // 字段数量不匹配，返回失败
    }
    if (condition != null) { // 如果连接条件不为null
      if (condition.getType().getSqlTypeName() != SqlTypeName.BOOLEAN) { // 检查连接条件的类型是否为布尔类型
        return litmus.fail("condition must be boolean: {}", // 返回失败，条件必须是布尔类型
            condition.getType()); // 输出条件的类型信息
      }
      // The input to the condition is a row type consisting of system fields, left fields, and right fields.
      // Very similar to the output row type, except that fields have not yet been made due to outer joins.
      // 连接条件的输入是一个由系统字段、左字段和右字段组成的行类型
      // 这与输出行类型非常相似，只是由于外连接，字段尚未被设置为可空
      RexChecker checker = // 创建行表达式检查器
          new RexChecker( // 用于验证连接条件的有效性
              getCluster().getTypeFactory().builder() // 获取类型工厂的构建器
                  .addAll(getSystemFieldList()) // 添加系统字段
                  .addAll(getLeft().getRowType().getFieldList()) // 添加左侧输入的字段
                  .addAll(getRight().getRowType().getFieldList()) // 添加右侧输入的字段
                  .build(), // 构建输入行类型
              context, litmus); // 传入上下文和验证器
      condition.accept(checker); // 使用检查器验证连接条件
      if (checker.getFailureCount() > 0) { // 如果检查器发现了失败
        return litmus.fail(checker.getFailureCount() // 返回失败，输出失败次数和连接条件
            + " failures in condition " + condition);
      }
    }
    return litmus.succeed(); // 所有验证通过，返回成功
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算Join自身的成本
      RelMetadataQuery mq) { // 参数：元数据查询对象
    // Maybe we should remove this for semi-join?
    // 也许我们应该为半连接移除这个特殊处理？
    if (isSemiJoin()) { // 如果这是半连接
      // REVIEW jvs 9-Apr-2006:  Just for now...
      // 审查者jvs在2006年4月9日的注释：这只是临时的处理方式
      return planner.getCostFactory().makeTinyCost(); // 返回一个极小的成本值，因为半连接通常成本很低
    }
    double rowCount = mq.getRowCount(this); // 从元数据查询中获取Join的行数估计
    return planner.getCostFactory().makeCost(rowCount, 0, 0); // 创建成本对象，只考虑行数，CPU和I/O成本设为0
  }

  // CHECKSTYLE: IGNORE 1 // 忽略CheckStyle检查规则1
  /** @deprecated Use {@link RelMdUtil#getJoinRowCount(RelMetadataQuery, Join, RexNode)}. */
  // 废弃注释：建议使用RelMdUtil.getJoinRowCount方法
  @Deprecated // to be removed before 2.0 // 标记为废弃，将在2.0版本前移除
  public static double estimateJoinedRows( // 静态方法：估计连接后的行数
      Join joinRel, // 参数：Join关系表达式
      RexNode condition) { // 参数：连接条件
    final RelMetadataQuery mq = joinRel.getCluster().getMetadataQuery(); // 从集群中获取元数据查询对象
    return Util.first(RelMdUtil.getJoinRowCount(mq, joinRel, condition), 1D); // 使用RelMdUtil工具计算连接行数，如果返回null则使用1.0作为默认值
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写estimateRowCount方法，估计Join的输出行数
    return Util.first(RelMdUtil.getJoinRowCount(mq, this, condition), 1D); // 使用RelMdUtil工具计算连接行数，如果返回null则使用1.0作为默认值
  }

  @Override public Set<CorrelationId> getVariablesSet() { // 重写getVariablesSet方法，获取关联变量集合
    return variablesSet; // 返回关联变量集合，这些变量由左侧设置，右侧使用，但对上层节点不可见
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，将Join的信息写入输出流（用于调试和解释）
    return super.explainTerms(pw) // 首先调用父类的explainTerms方法，输出基本信息
        .item("condition", condition) // 添加连接条件项
        .item("joinType", joinType.lowerName) // 添加连接类型项（使用小写名称）
        .itemIf("variablesSet", variablesSet, !variablesSet.isEmpty()) // 如果变量集合不为空，添加变量集合项
        .itemIf( // 条件添加系统字段项
            "systemFields", // 项名：systemFields
            getSystemFieldList(), // 系统字段列表
            !getSystemFieldList().isEmpty()); // 仅当系统字段列表不为空时才添加
  }

  @API(since = "1.24", status = API.Status.INTERNAL) // API注解：从1.24版本开始，内部API
  @EnsuresNonNullIf(expression = "#1", result = true) // 空值检查注解：如果方法返回true，则表达式#1非空
  protected boolean deepEquals0(@Nullable Object obj) { // 深度相等判断方法，比较两个Join对象是否深度相等
    if (this == obj) { // 如果是同一个对象引用
      return true; // 返回true
    }
    if (obj == null || getClass() != obj.getClass()) { // 如果obj为null或类型不同
      return false; // 返回false
    }
    Join o = (Join) obj; // 将obj转换为Join类型
    return traitSet.equals(o.traitSet) // 比较特征集合是否相等
        && left.deepEquals(o.left) // 深度比较左侧输入是否相等
        && right.deepEquals(o.right) // 深度比较右侧输入是否相等
        && condition.equals(o.condition) // 比较连接条件是否相等
        && joinType == o.joinType // 比较连接类型是否相等
        && hints.equals(o.hints) // 比较提示列表是否相等
        && getRowType().equalsSansFieldNames(o.getRowType()); // 比较行类型是否相等（忽略字段名）
  }

  @API(since = "1.24", status = API.Status.INTERNAL) // API注解：从1.24版本开始，内部API
  protected int deepHashCode0() { // 深度哈希码计算方法，计算Join对象的深度哈希码
    return Objects.hash(traitSet, // 使用Objects.hash计算哈希码，包含特征集合
        left.deepHashCode(), right.deepHashCode(), // 左右输入的深度哈希码
        condition, joinType, hints); // 连接条件、连接类型和提示列表
  }

  @Override protected RelDataType deriveRowType() { // 重写deriveRowType方法，推导Join的输出行类型
    return SqlValidatorUtil.deriveJoinRowType(left.getRowType(), // 使用SqlValidatorUtil工具推导连接的行类型
        right.getRowType(), joinType, getCluster().getTypeFactory(), null, // 传入左右行类型、连接类型、类型工厂、字段名列表（null表示自动生成）
        getSystemFieldList()); // 传入系统字段列表
  }

  /**
   * Returns whether this LogicalJoin has already spawned a
   * {@code SemiJoin} via
   * {@link org.apache.calcite.rel.rules.JoinAddRedundantSemiJoinRule}.
   * 返回此LogicalJoin是否已经通过JoinAddRedundantSemiJoinRule规则生成了SemiJoin
   *
   * <p>The base implementation returns false.
   * 基类实现返回false
   *
   * @return whether this join has already spawned a semi join
   * 返回此连接是否已经生成了半连接
   */
  public boolean isSemiJoinDone() { // 判断此Join是否已经生成了半连接
    return false; // 基类实现返回false，子类可以重写
  }

  /**
   * Returns whether this Join is a semijoin.
   * 返回此Join是否为半连接
   *
   * @return true if this Join's join type is semi.
   * 如果此Join的连接类型是SEMI则返回true
   */
  public boolean isSemiJoin() { // 判断此Join是否为半连接
    return joinType == JoinRelType.SEMI; // 检查连接类型是否为SEMI
  }

  /**
   * Returns a list of system fields that will be prefixed to
   * output row type.
   * 返回将前缀到输出行类型的系统字段列表
   *
   * @return list of system fields
   * 系统字段列表
   */
  public List<RelDataTypeField> getSystemFieldList() { // 获取系统字段列表
    return Collections.emptyList(); // 返回空列表，基类实现没有系统字段
  }

  @Deprecated // to be removed before 2.0 // 标记为废弃，将在2.0版本前移除
  public static RelDataType deriveJoinRowType( // 静态方法：推导连接的行类型（废弃版本）
      RelDataType leftType, // 参数：左侧输入的行类型
      RelDataType rightType, // 参数：右侧输入的行类型
      JoinRelType joinType, // 参数：连接类型
      RelDataTypeFactory typeFactory, // 参数：类型工厂
      @Nullable List<String> fieldNameList, // 参数：字段名列表（可为null）
      List<RelDataTypeField> systemFieldList) { // 参数：系统字段列表
    return SqlValidatorUtil.deriveJoinRowType(leftType, rightType, joinType, // 委托给SqlValidatorUtil工具推导行类型
        typeFactory, fieldNameList, systemFieldList); // 传入所有参数
  }

  @Deprecated // to be removed before 2.0 // 标记为废弃，将在2.0版本前移除
  public static RelDataType createJoinType( // 静态方法：创建连接的行类型（废弃版本）
      RelDataTypeFactory typeFactory, // 参数：类型工厂
      RelDataType leftType, // 参数：左侧输入的行类型
      RelDataType rightType, // 参数：右侧输入的行类型
      List<String> fieldNameList, // 参数：字段名列表
      List<RelDataTypeField> systemFieldList) { // 参数：系统字段列表
    return SqlValidatorUtil.createJoinType(typeFactory, leftType, rightType, // 委托给SqlValidatorUtil工具创建行类型
        fieldNameList, systemFieldList); // 传入所有参数
  }

  @Override public Join copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法（RelNode接口版本），创建Join的副本
    assert inputs.size() == 2; // 断言：输入列表必须包含2个元素（左右输入）
    return copy(traitSet, getCondition(), inputs.get(0), inputs.get(1), // 调用具体的copy方法，传入特征集合、连接条件、左右输入
        joinType, isSemiJoinDone()); // 传入连接类型和半连接完成标志
  }

  /**
   * Creates a copy of this join, overriding condition, system fields and
   * inputs.
   * 创建此Join的副本，可以覆盖连接条件、系统字段和输入
   *
   * <p>General contract as {@link RelNode#copy}.
   * 通用约定与RelNode.copy相同
   *
   * @param traitSet      Traits，特征集合，定义副本的物理特性
   * @param conditionExpr Condition，连接条件表达式
   * @param left          Left input，左侧输入关系表达式
   * @param right         Right input，右侧输入关系表达式
   * @param joinType      Join type，连接类型
   * @param semiJoinDone  Whether this join has been translated to a semi-join
   *                      此Join是否已经转换为半连接
   * @return Copy of this join
   * 返回此Join的副本
   */
  public abstract Join copy(RelTraitSet traitSet, RexNode conditionExpr, // 抽象方法：创建Join的副本，子类必须实现
      RelNode left, RelNode right, JoinRelType joinType, boolean semiJoinDone); // 参数：特征集合、连接条件、左右输入、连接类型、半连接标志

  /**
   * Analyzes the join condition.
   * 分析连接条件
   *
   * @return Analyzed join condition
   * 返回分析后的连接条件信息
   */
  public JoinInfo analyzeCondition() { // 分析连接条件的方法
    return joinInfo; // 返回在构造函数中创建的JoinInfo对象，包含连接条件的分析结果
  }

  @Override public ImmutableList<RelHint> getHints() { // 重写getHints方法，获取优化器提示列表
    return hints; // 返回不可变的提示列表
  }
} // Join类结束
