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
package org.apache.calcite.rel.core;  // 包声明：Apache Calcite核心关系代数包

import org.apache.calcite.plan.RelOptCluster;  // 导入：关系表达式集群，包含关系表达式共享的元数据
import org.apache.calcite.plan.RelOptCost;  // 导入：关系表达式成本模型，用于优化器评估执行成本
import org.apache.calcite.plan.RelOptPlanner;  // 导入：关系优化器，用于优化查询计划
import org.apache.calcite.plan.RelOptUtil;  // 导入：关系优化工具类，提供各种实用方法
import org.apache.calcite.plan.RelTraitSet;  // 导入：关系特征集合，定义关系表达式的物理属性
import org.apache.calcite.rel.BiRel;  // 导入：二元关系表达式基类，接受两个输入
import org.apache.calcite.rel.RelInput;  // 导入：关系表达式输入接口，用于反序列化
import org.apache.calcite.rel.RelNode;  // 导入：关系表达式接口，表示查询计划中的节点
import org.apache.calcite.rel.RelWriter;  // 导入：关系表达式写入器，用于输出查询计划
import org.apache.calcite.rel.hint.Hintable;  // 导入：支持提示的接口，允许添加优化提示
import org.apache.calcite.rel.hint.RelHint;  // 导入：关系提示，用于指导优化器行为
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入：关系元数据查询接口
import org.apache.calcite.rel.type.RelDataType;  // 导入：关系数据类型，描述行类型
import org.apache.calcite.sql.validate.SqlValidatorUtil;  // 导入：SQL验证工具类
import org.apache.calcite.util.ImmutableBitSet;  // 导入：不可变位集合，用于高效表示列索引集合
import org.apache.calcite.util.Litmus;  // 导入：断言检查工具，用于验证关系表达式

import com.google.common.collect.ImmutableList;  // 导入：Google Guava不可变列表
import com.google.common.collect.ImmutableSet;  // 导入：Google Guava不可变集合

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入：可空性注解

import java.util.List;  // 导入：Java列表接口
import java.util.Set;  // 导入：Java集合接口

import static java.util.Objects.requireNonNull;  // 导入：静态导入：对象非空检查方法

/**
 * 一个执行嵌套循环连接的关系运算符（Correlate：关联运算符）
 *
 * <p>它的行为类似于一种 {@link org.apache.calcite.rel.core.Join}，
 * 但通过在其环境中设置变量并重新启动其右侧输入来工作。
 *
 * <p>Correlate不是一个连接，因为：典型的规则不应该匹配Correlate。
 *
 * <p>Correlate用于表示关联子查询。一种实现策略是去关联化表达式。
 *
 * <table>
 *   <caption>物理操作到逻辑操作的映射</caption>
 *   <tr><th>物理操作</th><th>逻辑操作</th></tr>
 *   <tr><td>NestedLoops</td><td>Correlate(A, B, regular)</td></tr>
 *   <tr><td>NestedLoopsOuter</td><td>Correlate(A, B, outer)</td></tr>
 *   <tr><td>NestedLoopsSemi</td><td>Correlate(A, B, semi)</td></tr>
 *   <tr><td>NestedLoopsAnti</td><td>Correlate(A, B, anti)</td></tr>
 *   <tr><td>HashJoin</td><td>EquiJoin(A, B)</td></tr>
 *   <tr><td>HashJoinOuter</td><td>EquiJoin(A, B, outer)</td></tr>
 *   <tr><td>HashJoinSemi</td><td>SemiJoin(A, B, semi)</td></tr>
 *   <tr><td>HashJoinAnti</td><td>SemiJoin(A, B, anti)</td></tr>
 * </table>
 *
 * @see CorrelationId  // 关联ID，用于标识关联变量
 */
public abstract class Correlate extends BiRel implements Hintable {  // 抽象类：关联运算符，继承自BiRel（二元关系），实现Hintable接口
  //~ Instance fields --------------------------------------------------------  // 实例字段部分

  protected final CorrelationId correlationId;  // 关联ID：唯一标识关联变量，用于在左右输入之间传递关联信息
  protected final ImmutableBitSet requiredColumns;  // 必需列：左输入中需要传递给右输入的列索引集合（位集表示）
  protected final JoinRelType joinType;  // 连接类型：INNER（内连接）、LEFT（左连接）、SEMI（半连接）、ANTI（反连接）
  protected final ImmutableList<RelHint> hints;  // 提示列表：用于指导优化器行为的优化提示列表

  //~ Constructors -----------------------------------------------------------  // 构造方法部分

  /**
   * Creates a Correlate.  // 创建一个Correlate实例
   *
   * @param cluster      Cluster this relational expression belongs to  // 集群：此关系表达式所属的集群
   * @param left         Left input relational expression  // 左输入：左侧关系表达式
   * @param right        Right input relational expression  // 右输入：右侧关系表达式
   * @param correlationId Variable name for the row of left input  // 关联ID：左输入行的变量名
   * @param requiredColumns Set of columns that are used by correlation  // 必需列：关联使用的列集合
   * @param joinType Join type  // 连接类型：连接的类型
   */
  @SuppressWarnings("method.invocation.invalid")  // 抑制警告：方法调用无效（调用父类构造函数）
  protected Correlate(  // 构造方法：创建Correlate实例（带提示参数）
      RelOptCluster cluster,  // 参数：关系表达式集群
      RelTraitSet traitSet,  // 参数：关系特征集合
      List<RelHint> hints,  // 参数：提示列表
      RelNode left,  // 参数：左输入关系表达式
      RelNode right,  // 参数：右输入关系表达式
      CorrelationId correlationId,  // 参数：关联ID
      ImmutableBitSet requiredColumns,  // 参数：必需列集合
      JoinRelType joinType) {  // 参数：连接类型
    super(cluster, traitSet, left, right);  // 调用父类BiRel构造函数，初始化集群、特征集和左右输入
    assert !joinType.generatesNullsOnLeft() : "Correlate has invalid join type " + joinType;  // 断言：连接类型不能在左侧生成null（Correlate不支持RIGHT/FULL连接）
    this.joinType = requireNonNull(joinType, "joinType");  // 初始化：连接类型（非空检查）
    this.correlationId = requireNonNull(correlationId, "correlationId");  // 初始化：关联ID（非空检查）
    this.requiredColumns = requireNonNull(requiredColumns, "requiredColumns");  // 初始化：必需列集合（非空检查）
    this.hints = ImmutableList.copyOf(hints);  // 初始化：提示列表（创建不可变副本）
    assert isValid(Litmus.THROW, null);  // 断言：验证当前Correlate实例是否有效（如果无效则抛出异常）
  }

  @Deprecated // to be removed before 2.0  // 已弃用：将在2.0版本前移除
  protected Correlate(  // 构造方法：创建Correlate实例（不带提示参数，已弃用）
      RelOptCluster cluster,  // 参数：关系表达式集群
      RelTraitSet traitSet,  // 参数：关系特征集合
      RelNode left,  // 参数：左输入关系表达式
      RelNode right,  // 参数：右输入关系表达式
      CorrelationId correlationId,  // 参数：关联ID
      ImmutableBitSet requiredColumns,  // 参数：必需列集合
      JoinRelType joinType) {  // 参数：连接类型
    this(cluster, traitSet, ImmutableList.of(), left, right,  // 调用带提示参数的构造函数，传入空提示列表
        correlationId, requiredColumns, joinType);  // 传递其他参数
  }

  /**
   * Creates a Correlate by parsing serialized output.  // 通过解析序列化输出来创建Correlate
   *
   * @param input Input representation  // 输入：输入表示（序列化的关系表达式）
   */
  protected Correlate(RelInput input) {  // 构造方法：从序列化输入创建Correlate实例
    this(  // 调用主构造函数
        input.getCluster(), input.getTraitSet(), input.getInputs().get(0),  // 从输入获取集群、特征集和左输入（第一个输入）
        input.getInputs().get(1),  // 从输入获取右输入（第二个输入）
        new CorrelationId(  // 创建新的关联ID
            requireNonNull((Integer) input.get("correlation"), "correlation")),  // 从输入获取关联值（非空检查）
        input.getBitSet("requiredColumns"),  // 从输入获取必需列集合（位集）
        requireNonNull(input.getEnum("joinType", JoinRelType.class), "joinType"));  // 从输入获取连接类型枚举（非空检查）
  }

  //~ Methods ----------------------------------------------------------------  // 方法部分

  @Override public boolean isValid(Litmus litmus, @Nullable Context context) {  // 方法：验证关系表达式是否有效
    ImmutableBitSet leftColumns = ImmutableBitSet.range(left.getRowType().getFieldCount());  // 创建左输入所有列的位集（从0到列数-1）
    return super.isValid(litmus, context)  // 调用父类验证方法
        && litmus.check(leftColumns.contains(requiredColumns),  // 检查：必需列是否是左输入列的子集
        "Required columns {} not subset of left columns {}", requiredColumns, leftColumns)  // 如果不是子集，输出错误信息
        && RelOptUtil.notContainsCorrelation(left, correlationId, litmus);  // 检查：左输入是否不包含此关联ID（避免循环依赖）
  }

  @Override public Correlate copy(RelTraitSet traitSet, List<RelNode> inputs) {  // 方法：复制关系表达式（BiRel接口方法）
    assert inputs.size() == 2;  // 断言：输入列表必须包含两个元素（左输入和右输入）
    return copy(traitSet,  // 调用抽象copy方法，传入新的特征集
        inputs.get(0),  // 传入新的左输入
        inputs.get(1),  // 传入新的右输入
        correlationId,  // 传入关联ID（保持不变）
        requiredColumns,  // 传入必需列集合（保持不变）
        joinType);  // 传入连接类型（保持不变）
  }

  public abstract Correlate copy(RelTraitSet traitSet,  // 抽象方法：复制Correlate实例（子类必须实现）
      RelNode left, RelNode right, CorrelationId correlationId,  // 参数：特征集、左输入、右输入、关联ID
      ImmutableBitSet requiredColumns, JoinRelType joinType);  // 参数：必需列集合、连接类型

  public JoinRelType getJoinType() {  // 方法：获取连接类型
    return joinType;  // 返回：连接类型（INNER、LEFT、SEMI或ANTI）
  }

  @Override protected RelDataType deriveRowType() {  // 方法：推导输出行类型
    switch (joinType) {  // 根据连接类型决定输出行类型
    case LEFT:  // 左连接情况
    case INNER:  // 内连接情况
      return SqlValidatorUtil.deriveJoinRowType(left.getRowType(),  // 返回：左右输入的连接行类型（包含两侧列）
          right.getRowType(), joinType,  // 传入右输入行类型和连接类型
          getCluster().getTypeFactory(), null,  // 传入类型工厂和空条件
          ImmutableList.of());  // 传入空字段列表
    case ANTI:  // 反连接情况
    case SEMI:  // 半连接情况
      return left.getRowType();  // 返回：仅左输入的行类型（不包含右输入的列）
    default:  // 未知连接类型
      throw new IllegalStateException("Unknown join type " + joinType);  // 抛出：非法状态异常
    }
  }

  @Override public RelWriter explainTerms(RelWriter pw) {  // 方法：输出关系表达式的解释信息
    return super.explainTerms(pw)  // 调用父类方法，输出基本术语
        .item("correlation", correlationId)  // 添加：关联ID项
        .item("joinType", joinType.lowerName)  // 添加：连接类型项（小写名称）
        .item("requiredColumns", requiredColumns);  // 添加：必需列集合项
  }

  /**
   * Returns the correlating expressions.  // 返回关联表达式
   *
   * @return correlating expressions  // 返回：关联ID
   */
  public CorrelationId getCorrelationId() {  // 方法：获取关联ID
    return correlationId;  // 返回：关联ID对象
  }

  @Override public String getCorrelVariable() {  // 方法：获取关联变量名称
    return correlationId.getName();  // 返回：关联ID的名称（字符串形式）
  }

  /**
   * Returns the required columns in left relation required for the correlation  // 返回左关系中关联所需的列
   * in the right.  // 用于右侧关联
   *
   * @return columns in left relation required for the correlation in the right  // 返回：左关系中右侧关联所需的列
   */
  public ImmutableBitSet getRequiredColumns() {  // 方法：获取必需列集合
    return requiredColumns;  // 返回：必需列的位集
  }

  @Override public Set<CorrelationId> getVariablesSet() {  // 方法：获取变量集合（RelNode接口方法）
    return ImmutableSet.of(correlationId);  // 返回：包含当前关联ID的不可变集合
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) {  // 方法：估算输出行数
    double leftRowCount = mq.getRowCount(left);  // 获取：左输入的行数
    switch (joinType) {  // 根据连接类型估算行数
    case SEMI:  // 半连接情况
    case ANTI:  // 反连接情况
      return leftRowCount;  // 返回：左输入的行数（半连接和反连接不会增加行数）
    default:  // 其他连接类型（INNER、LEFT）
      return leftRowCount * mq.getRowCount(right);  // 返回：左输入行数乘以右输入行数（笛卡尔积）
    }
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,  // 方法：计算自身执行成本
      RelMetadataQuery mq) {  // 参数：元数据查询接口
    double rowCount = mq.getRowCount(this);  // 获取：当前Correlate的输出行数

    final double rightRowCount = mq.getRowCount(right);  // 获取：右输入的行数
    final double leftRowCount = mq.getRowCount(left);  // 获取：左输入的行数
    if (Double.isInfinite(leftRowCount) || Double.isInfinite(rightRowCount)) {  // 检查：如果任一输入行数为无穷大
      return planner.getCostFactory().makeInfiniteCost();  // 返回：无穷大成本
    }

    Double restartCount = mq.getRowCount(getLeft());  // 获取：重启动数（等于左输入行数，因为每行左输入都要执行一次右输入）
    if (restartCount == null) {  // 检查：如果重启动数为null
      return planner.getCostFactory().makeInfiniteCost();  // 返回：无穷大成本
    }
    // RelMetadataQuery.getCumulativeCost(getRight()); does not work for  // 注释：getCumulativeCost方法对RelSubset不工作
    // RelSubset, so we ask planner to cost-estimate right relation  // 因此请求优化器估算右关系的成本
    RelOptCost rightCost = planner.getCost(getRight(), mq);  // 获取：右输入的成本（通过优化器估算）
    if (rightCost == null) {  // 检查：如果右输入成本为null
      return planner.getCostFactory().makeInfiniteCost();  // 返回：无穷大成本
    }
    RelOptCost rescanCost =  // 计算重扫描成本（右输入需要执行restartCount-1次）
        rightCost.multiplyBy(Math.max(1.0, restartCount - 1));  // 右输入成本乘以（重启动数-1），至少为1.0

    return planner.getCostFactory().makeCost(  // 创建成本对象
        rowCount /* generate results */ + leftRowCount /* scan left results */,  // CPU成本：生成结果行数 + 扫描左输入行数
        0, 0).plus(rescanCost);  // I/O成本和内存成本为0，加上重扫描成本
  }

  @Override public ImmutableList<RelHint> getHints() {  // 方法：获取提示列表（Hintable接口方法）
    return hints;  // 返回：提示列表
  }
}
