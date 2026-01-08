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
package org.apache.calcite.adapter.enumerable; // 包声明：该类位于org.apache.calcite.adapter.enumerable包中，这是Calcite的可枚举适配器包

import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder：用于构建代码块的构建器类
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression：表示LINQ4J表达式的抽象语法树节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions：用于创建各种表达式实例的工具类
import org.apache.calcite.plan.DeriveMode; // 导入DeriveMode：定义特性推导模式的枚举
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster：关系表达式集群，包含共享的环境信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost：关系表达式成本的抽象表示
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner：关系表达式优化器的接口
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet：关系特性集合，用于描述关系表达式的属性
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef：排序特性的定义
import org.apache.calcite.rel.RelNode; // 导入RelNode：关系表达式的抽象基类
import org.apache.calcite.rel.RelNodes; // 导入RelNodes：关系节点的工具类
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId：相关性的标识符
import org.apache.calcite.rel.core.Join; // 导入Join：连接操作的关系表达式基类
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType：连接类型的枚举（内连接、左外连接等）
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入RelMdCollation：排序元数据的提供者
import org.apache.calcite.rel.metadata.RelMdUtil; // 导入RelMdUtil：元数据工具类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery：元数据查询接口
import org.apache.calcite.rex.RexNode; // 导入RexNode：行表达式的抽象基类
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod：内置方法的枚举
import org.apache.calcite.util.Pair; // 导入Pair：包含两个元素的不可变对

import com.google.common.collect.ImmutableList; // 导入ImmutableList：Google Guava提供的不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable：用于标记可为null的注解

import java.util.List; // 导入List：Java标准库的列表接口
import java.util.Set; // 导入Set：Java标准库的集合接口

/** Implementation of {@link org.apache.calcite.rel.core.Join} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}
 * that allows conditions that are not just {@code =} (equals). */
// 类注释：这是Join关系表达式在可枚举调用约定中的实现，允许使用不仅仅是等号（=）的连接条件
// 核心功能：实现嵌套循环连接算法，这是最基础的连接实现方式，支持任意复杂的连接条件
// 嵌套循环连接的特点：对于左表的每一行，遍历右表的所有行，检查是否满足连接条件
// 适用场景：当连接条件不是简单的等值连接时，或者数据量较小时使用
// 性能特点：时间复杂度为O(m*n)，其中m和n分别是左右表的行数，通常比哈希连接和排序合并连接慢，但适用性最广
public class EnumerableNestedLoopJoin extends Join implements EnumerableRel { // 类定义：继承自Join基类，实现EnumerableRel接口，表示这是一个可枚举的嵌套循环连接关系表达式
  /** Creates an EnumerableNestedLoopJoin. */
  // 方法注释：创建一个EnumerableNestedLoopJoin实例的构造方法
  // 参数说明：
  //   - cluster: 关系表达式集群，包含共享的环境信息（如类型工厂、表达式构建器等）
  //   - traits: 关系特性集合，定义了该关系表达式的属性（如调用约定、排序等）
  //   - left: 左子节点，表示连接操作的左表
  //   - right: 右子节点，表示连接操作的右表
  //   - condition: 连接条件，以RexNode（行表达式）的形式表示，可以是任意复杂的布尔表达式
  //   - variablesSet: 相关性标识符集合，用于处理子查询中的相关性
  //   - joinType: 连接类型，可以是内连接（INNER）、左外连接（LEFT）、右外连接（RIGHT）、全外连接（FULL）等
  // 实现细节：调用父类Join的构造方法，传入空列表作为系统字段列表
  protected EnumerableNestedLoopJoin(RelOptCluster cluster, RelTraitSet traits, // 构造方法参数：关系表达式集群和特性集合
      RelNode left, RelNode right, RexNode condition, // 构造方法参数：左子节点、右子节点和连接条件
      Set<CorrelationId> variablesSet, JoinRelType joinType) { // 构造方法参数：相关性标识符集合和连接类型
    super(cluster, traits, ImmutableList.of(), left, right, condition, variablesSet, joinType); // 调用父类Join的构造方法，初始化连接操作
  } // 构造方法结束

  @Deprecated // to be removed before 2.0
  // 注释：已弃用的构造方法，将在2.0版本之前移除，不建议使用
  // 参数说明：
  //   - variablesStopped: 已停止的变量名称集合，旧版本使用String集合表示相关性
  // 实现细节：将String集合转换为CorrelationId集合，然后调用新的构造方法
  protected EnumerableNestedLoopJoin(RelOptCluster cluster, RelTraitSet traits, // 构造方法参数：关系表达式集群和特性集合
      RelNode left, RelNode right, RexNode condition, JoinRelType joinType, // 构造方法参数：左子节点、右子节点、连接条件和连接类型
      Set<String> variablesStopped) { // 构造方法参数：已停止的变量名称集合（旧版本）
    this(cluster, traits, left, right, condition, // 调用新版本的构造方法
        CorrelationId.setOf(variablesStopped), joinType); // 将String集合转换为CorrelationId集合
  } // 构造方法结束

  @Override public EnumerableNestedLoopJoin copy(RelTraitSet traitSet, // 方法签名：复制当前关系表达式，可以修改某些属性
      RexNode condition, RelNode left, RelNode right, JoinRelType joinType, // 方法参数：新的特性集合、连接条件、左右子节点和连接类型
      boolean semiJoinDone) { // 方法参数：半连接是否完成的标志（在此实现中未使用）
    return new EnumerableNestedLoopJoin(getCluster(), traitSet, left, right, // 返回：创建新的EnumerableNestedLoopJoin实例
        condition, variablesSet, joinType); // 保留原有的variablesSet集合
  } // 方法结束

  /** Creates an EnumerableNestedLoopJoin. */
  // 方法注释：静态工厂方法，用于创建EnumerableNestedLoopJoin实例
  // 参数说明：
  //   - left: 左子节点（左表）
  //   - right: 右子节点（右表）
  //   - condition: 连接条件
  //   - variablesSet: 相关性标识符集合
  //   - joinType: 连接类型
  // 返回值：新创建的EnumerableNestedLoopJoin实例
  // 实现细节：
  //   1. 从左子节点获取集群对象
  //   2. 创建元数据查询对象
  //   3. 构建特性集合，设置可枚举调用约定
  //   4. 根据左右子节点和连接类型推导排序特性
  //   5. 创建并返回新的实例
  public static EnumerableNestedLoopJoin create( // 方法定义：静态工厂方法
      RelNode left, // 参数：左子节点
      RelNode right, // 参数：右子节点
      RexNode condition, // 参数：连接条件
      Set<CorrelationId> variablesSet, // 参数：相关性标识符集合
      JoinRelType joinType) { // 参数：连接类型
    final RelOptCluster cluster = left.getCluster(); // 获取关系表达式集群，从左子节点获取
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 创建元数据查询对象，用于查询各种元数据
    final RelTraitSet traitSet = // 构建特性集合
        cluster.traitSetOf(EnumerableConvention.INSTANCE) // 设置可枚举调用约定
            .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果需要，替换排序特性
                () -> RelMdCollation.enumerableNestedLoopJoin(mq, left, right, joinType)); // 调用元数据提供者推导嵌套循环连接的排序特性
    return new EnumerableNestedLoopJoin(cluster, traitSet, left, right, condition, // 创建并返回新的实例
        variablesSet, joinType); // 传入相关性集合和连接类型
  } // 方法结束

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法签名：计算当前关系表达式的成本
      RelMetadataQuery mq) { // 参数：元数据查询对象
    double rowCount = mq.getRowCount(this); // 获取当前连接操作的预估行数

    // Joins can be flipped, and for many algorithms, both versions are viable
    // and have the same cost. To make the results stable between versions of
    // the planner, make one of the versions slightly more expensive.
    // 注释：连接操作可以交换左右表位置，对于许多算法，两个版本都是可行的且具有相同的成本
    // 为了在不同版本的优化器之间保持稳定的结果，让其中一个版本稍微昂贵一些
    switch (joinType) { // 根据连接类型进行处理
    case SEMI: // 情况：半连接（只返回左表中与右表匹配的行，且左表行不重复）
    case ANTI: // 情况：反连接（只返回左表中与右表不匹配的行）
      // SEMI and ANTI join cannot be flipped
      // 注释：半连接和反连接不能交换左右表位置，因为它们的语义不对称
      break; // 不做任何修改，直接保持原样
    case RIGHT: // 情况：右外连接（返回右表所有行，左表不匹配的填充null）
      rowCount = RelMdUtil.addEpsilon(rowCount); // 给行数添加一个极小值，使右外连接的成本略高，避免交换左右表
      break; // 结束case
    default: // 情况：其他连接类型（内连接、左外连接等）
      if (RelNodes.COMPARATOR.compare(left, right) > 0) { // 如果左子节点"大于"右子节点（基于某种比较规则）
        rowCount = RelMdUtil.addEpsilon(rowCount); // 给行数添加一个极小值，使这种情况的成本略高，避免交换
      } // 结束if
    } // 结束switch

    final double rightRowCount = mq.getRowCount(right); // 获取右子节点的预估行数
    final double leftRowCount = mq.getRowCount(left); // 获取左子节点的预估行数
    if (Double.isInfinite(leftRowCount)) { // 如果左子节点的行数是无穷大
      rowCount = leftRowCount; // 将总行数设为无穷大，因为嵌套循环连接的成本受限于较大的表
    } // 结束if
    if (Double.isInfinite(rightRowCount)) { // 如果右子节点的行数是无穷大
      rowCount = rightRowCount; // 将总行数设为无穷大
    } // 结束if

    RelOptCost cost = planner.getCostFactory().makeCost(rowCount, 0, 0); // 创建成本对象，只考虑行数，CPU和I/O成本设为0
    // Give it some penalty
    // 注释：给成本添加一些惩罚，因为嵌套循环连接通常比其他连接算法效率低
    cost = cost.multiplyBy(10); // 将成本乘以10，使优化器更倾向于选择其他连接算法
    return cost; // 返回计算出的成本
  } // 方法结束

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits( // 方法签名：将特性向下传递给子节点
      final RelTraitSet required) { // 参数：要求的特性集合
    // EnumerableNestedLoopJoin traits passdown shall only pass through collation to
    // left input. It is because for EnumerableNestedLoopJoin always
    // uses left input as the outer loop, thus only left input can preserve ordering.
    // Push sort both to left and right inputs does not help right outer join. It's because in
    // implementation, EnumerableNestedLoopJoin produces (null, right_unmatched) all together,
    // which does not preserve ordering from right side.
    // 注释：嵌套循环连接的特性传递只能将排序特性传递给左输入
    // 原因：嵌套循环连接总是使用左输入作为外层循环，因此只有左输入可以保持排序
    // 将排序同时推送到左右输入对右外连接没有帮助，因为在实现中，嵌套循环连接会批量生成(null, right_unmatched)，
    // 这不会保持来自右侧的排序
    return EnumerableTraitsUtils.passThroughTraitsForJoin( // 调用工具类方法处理连接操作的特性传递
        required, joinType, getLeft().getRowType().getFieldCount(), traitSet); // 传入要求的特性、连接类型、左表字段数和当前特性集合
  } // 方法结束

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits( // 方法签名：从子节点推导特性
      final RelTraitSet childTraits, final int childId) { // 参数：子节点的特性集合和子节点ID（0表示左子节点，1表示右子节点）
    return EnumerableTraitsUtils.deriveTraitsForJoin( // 调用工具类方法处理连接操作的特性推导
        childTraits, childId, joinType, traitSet, right.getTraitSet()); // 传入子节点特性、子节点ID、连接类型、当前特性和右子节点特性
  } // 方法结束

  @Override public DeriveMode getDeriveMode() { // 方法签名：获取特性推导模式
    if (joinType == JoinRelType.FULL || joinType == JoinRelType.RIGHT) { // 如果是全外连接或右外连接
      return DeriveMode.PROHIBITED; // 返回禁止推导模式，因为这两种连接类型无法保持排序
    } // 结束if

    return DeriveMode.LEFT_FIRST; // 返回左优先推导模式，表示先从左子节点推导特性
  } // 方法结束

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 方法签名：实现可枚举关系表达式，生成可执行代码
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建生成的方法体
    final Result leftResult = // 访问左子节点，生成其可枚举实现
        implementor.visitChild(this, 0, (EnumerableRel) left, pref); // 参数：当前节点、子节点ID、子节点、偏好设置
    Expression leftExpression = // 将左子节点的代码块添加到构建器中
        builder.append("left", leftResult.block); // 参数：变量名"left"和左子节点的代码块
    final Result rightResult = // 访问右子节点，生成其可枚举实现
        implementor.visitChild(this, 1, (EnumerableRel) right, pref); // 参数：当前节点、子节点ID、子节点、偏好设置
    Expression rightExpression = // 将右子节点的代码块添加到构建器中
        builder.append("right", rightResult.block); // 参数：变量名"right"和右子节点的代码块
    final PhysType physType = // 创建物理类型，表示输出行的物理表示
        PhysTypeImpl.of(implementor.getTypeFactory(), // 参数：类型工厂
            getRowType(), // 参数：行类型（输出行的结构）
            pref.preferArray()); // 参数：是否偏好使用数组表示
    final Expression predicate = // 生成连接条件的谓词表达式
        EnumUtils.generatePredicate(implementor, getCluster().getRexBuilder(), left, right, // 参数：实现器、表达式构建器、左右子节点
            leftResult.physType, rightResult.physType, condition); // 参数：左右子节点的物理类型和连接条件
    return implementor.result( // 返回实现结果
        physType, // 参数：物理类型
        builder.append( // 将嵌套循环连接的调用添加到代码块中
            Expressions.call(BuiltInMethod.NESTED_LOOP_JOIN.method, // 调用内置的嵌套循环连接方法
                leftExpression, // 参数：左子节点的表达式
                rightExpression, // 参数：右子节点的表达式
                predicate, // 参数：连接条件谓词
                EnumUtils.joinSelector(joinType, // 参数：连接类型选择器，根据连接类型生成结果行
                    physType, // 参数：物理类型
                    ImmutableList.of(leftResult.physType, // 参数：左右子节点的物理类型列表
                        rightResult.physType)),
                Expressions.constant(EnumUtils.toLinq4jJoinType(joinType)))) // 参数：LINQ4J连接类型的常量表达式
            .toBlock()); // 转换为代码块
  } // 方法结束
} // 类结束
