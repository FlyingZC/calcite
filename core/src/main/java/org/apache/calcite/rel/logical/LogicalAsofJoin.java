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
package org.apache.calcite.rel.logical; // 包声明：逻辑关系表达式包，包含所有逻辑层面的关系表达式类

import org.apache.calcite.plan.Convention; // 导入：约定接口，定义关系表达式的逻辑或物理特征
import org.apache.calcite.plan.RelOptCluster; // 导入：关系表达式集群，包含共享的优化器上下文
import org.apache.calcite.plan.RelTraitSet; // 导入：特征集合，用于存储关系表达式的多个特征
import org.apache.calcite.rel.RelInput; // 导入：关系输入接口，用于从序列化格式读取关系表达式
import org.apache.calcite.rel.RelNode; // 导入：关系节点接口，所有关系表达式的基接口
import org.apache.calcite.rel.RelShuttle; // 导入：关系穿梭器接口，用于访问和修改关系表达式树
import org.apache.calcite.rel.core.AsofJoin; // 导入：ASOF连接基类，提供ASOF连接的核心功能
import org.apache.calcite.rel.core.Join; // 导入：连接基类，提供连接操作的通用功能
import org.apache.calcite.rel.core.JoinRelType; // 导入：连接类型枚举，定义INNER、LEFT、RIGHT等连接类型
import org.apache.calcite.rel.hint.RelHint; // 导入：关系提示接口，用于向优化器提供优化建议
import org.apache.calcite.rel.type.RelDataTypeField; // 导入：关系数据类型字段，表示关系类型中的字段
import org.apache.calcite.rex.RexNode; // 导入：行表达式节点接口，表示SQL表达式

import com.google.common.collect.ImmutableList; // 导入：不可变列表实现，Guava库提供
import com.google.common.collect.ImmutableSet; // 导入：不可变集合实现，Guava库提供

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：可空注解，用于标记可能为null的参数

import java.util.ArrayList; // 导入：动态数组列表实现
import java.util.List; // 导入：列表接口
import java.util.Objects; // 导入：对象工具类，提供equals、hash等静态方法

import static java.util.Objects.requireNonNull; // 导入：requireNonNull静态方法，用于参数null检查

/**
 * Sub-class of {@link AsofJoin} encoding ASOF joins. // AsofJoin的子类，用于编码ASOF(As-Of)连接操作，ASOF连接是一种时间序列连接，用于在右表中查找左表每行数据最接近但不晚于的匹配行
 * Adapted from the {@link LogicalJoin} implementation. // 适配自LogicalJoin的实现，保持了与LogicalJoin相似的代码结构
 */
public final class LogicalAsofJoin extends AsofJoin { // LogicalAsofJoin是AsofJoin的最终子类，表示逻辑层面的ASOF连接操作节点
  //~ Instance fields --------------------------------------------------------

  private final ImmutableList<RelDataTypeField> systemFieldList; // 系统字段列表，这些字段会被前缀到输出行类型中；通常为空列表，但不能为null，用于存储系统级别的元数据字段

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a LogicalAsofJoin. // 创建一个LogicalAsofJoin实例，这是主要的构造方法
   *
   * <p>Use {@link #create} unless you know what you're doing. // 除非你清楚自己在做什么，否则建议使用静态工厂方法create()而不是直接调用此构造函数
   *
   * @param cluster          Cluster // 关系表达式集群，包含共享的优化器上下文信息，如类型系统、元数据提供者等
   * @param traitSet         Trait set // 特征集合，定义了此关系表达式的物理和逻辑属性，如约定(Convention)、排序等
   * @param hints            Hints // 提示列表，用于向优化器提供额外的指导信息，如使用特定的索引或连接策略
   * @param left             Left input // 左子节点，表示连接操作的左输入关系
   * @param right            Right input // 右子节点，表示连接操作的右输入关系
   * @param condition        Join condition // 连接条件，通常是一个等值条件表达式，用于确定两个表之间的匹配键
   * @param matchCondition   Temporal condition // 时间条件，ASOF连接特有的条件，用于匹配时间序列中最接近但不超过的时间点
   * @param systemFieldList  List of system fields that will be prefixed to // 系统字段列表，这些字段会被添加到输出行类型的前面
   *                         output row type; typically empty but must not be null // 通常为空列表，但绝不能为null
   */
  public LogicalAsofJoin( // 构造函数开始，创建LogicalAsofJoin实例
      RelOptCluster cluster, // 参数：关系表达式集群对象
      RelTraitSet traitSet, // 参数：特征集合对象
      List<RelHint> hints, // 参数：提示列表
      RelNode left, // 参数：左输入关系节点
      RelNode right, // 参数：右输入关系节点
      RexNode condition, // 参数：连接条件表达式
      RexNode matchCondition, // 参数：时间匹配条件表达式
      JoinRelType joinType, // 参数：连接类型(如INNER, LEFT等)
      ImmutableList<RelDataTypeField> systemFieldList) { // 参数：系统字段列表
    super(cluster, traitSet, hints, left, right, // 调用父类AsofJoin的构造函数，传递基本参数
        condition, matchCondition, ImmutableSet.of(), joinType); // 传递连接条件、时间条件、空的变量集合和连接类型
    this.systemFieldList = requireNonNull(systemFieldList, "systemFieldList"); // 初始化系统字段列表，确保不为null，否则抛出NullPointerException
  }

  /**
   * Creates a LogicalAsofJoin by parsing serialized output. // 通过解析序列化输出来创建LogicalAsofJoin实例，用于从JSON或其他序列化格式恢复关系表达式
   */
  public LogicalAsofJoin(RelInput input) { // 反序列化构造函数，从RelInput对象中读取数据并构建LogicalAsofJoin
    this(input.getCluster(), input.getCluster().traitSetOf(Convention.NONE), // 调用主构造函数，从input中获取cluster和traitSet(约定为NONE)
        new ArrayList<>(), // 创建空的提示列表
        input.getInputs().get(0), input.getInputs().get(1), // 获取左输入和右输入关系节点
        requireNonNull(input.getExpression("condition"), "condition"), // 获取连接条件表达式，确保不为null
        requireNonNull(input.getExpression("matchCondition"), "matchCondition"), // 获取时间匹配条件表达式，确保不为null
        requireNonNull(input.getEnum("joinType", JoinRelType.class), "joinType"), // 获取连接类型枚举值，确保不为null
        ImmutableList.of()); // 创建空的系统字段列表
  }

  /** Creates a LogicalAsofJoin. // 静态工厂方法，用于创建LogicalAsofJoin实例，是推荐的创建方式 */
  public static LogicalAsofJoin create(RelNode left, RelNode right, List<RelHint> hints, // 方法参数：左输入、右输入、提示列表
      RexNode condition, RexNode matchCondition, // 连接条件和时间匹配条件
      JoinRelType joinType, // 连接类型
      ImmutableList<RelDataTypeField> systemFieldList) { // 系统字段列表
    final RelOptCluster cluster = left.getCluster(); // 从左输入节点获取关系表达式集群
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建特征集合，约定设置为NONE表示逻辑层
    return new LogicalAsofJoin(cluster, traitSet, hints, left, right, condition, matchCondition, // 调用构造函数创建并返回LogicalAsofJoin实例
        joinType, systemFieldList);
  }

  //~ Methods ----------------------------------------------------------------

  public LogicalAsofJoin copy( // 复制方法，创建一个新的LogicalAsofJoin实例，可以修改部分属性
      RelTraitSet traitSet, RexNode conditionExpr, RexNode matchConditionExpr, // 参数：新的特征集合、连接条件、时间匹配条件
      RelNode left, RelNode right) { // 参数：新的左输入和右输入节点
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：如果适用，特征集合必须包含Convention.NONE
    return new LogicalAsofJoin(getCluster(), // 创建并返回新的LogicalAsofJoin实例，使用当前cluster
        getCluster().traitSetOf(Convention.NONE), hints, left, right, conditionExpr, // 使用Convention.NONE的特征集合，保留原有的hints，使用新的条件和输入
        matchConditionExpr, joinType, systemFieldList); // 使用新的时间匹配条件，保留原有的连接类型和系统字段列表
  }

  @Override public RelNode accept(RelShuttle shuttle) { // 接受访问者模式，允许RelShuttle访问并可能修改此关系表达式
    return shuttle.visit(this); // 调用访问者的visit方法，将当前对象传递给访问者处理，返回可能被修改后的关系节点
  }

  @Override public boolean deepEquals(@Nullable Object obj) { // 深度相等比较方法，比较两个LogicalAsofJoin对象的所有属性
    if (this == obj) { // 如果是同一个对象引用
      return true; // 直接返回true
    }
    LogicalAsofJoin asofObj = requireNonNull((LogicalAsofJoin) obj); // 将obj转换为LogicalAsofJoin类型，确保不为null
    return deepEquals0(obj) // 调用父类的deepEquals0方法比较基本属性(如cluster, traitSet, hints, left, right, condition, joinType等)
        && matchCondition.equals(asofObj.matchCondition) // 比较时间匹配条件是否相等
        && systemFieldList.equals(asofObj.systemFieldList); // 比较系统字段列表是否相等
  }

  @Override public int deepHashCode() { // 深度哈希码计算方法，基于所有属性生成哈希码
    return Objects.hash(deepHashCode0(), systemFieldList); // 使用Objects.hash方法组合父类的哈希码和系统字段列表的哈希码
  }

  @Override public ImmutableList<RelDataTypeField> getSystemFieldList() { // 获取系统字段列表的getter方法
    return systemFieldList; // 返回系统字段列表，这些字段会被前缀到输出行类型中
  }

  @Override public Join copy( // 实现Join接口的copy方法，但此方法不适用于LogicalAsofJoin
      RelTraitSet traitSet, RexNode conditionExpr, RelNode left, RelNode right, // 参数：特征集合、连接条件、左输入、右输入
      JoinRelType joinType, boolean semiJoinDone) { // 参数：连接类型、半连接完成标志
    // This method does not provide the matchCondition as an argument, so it should never be called // 此方法没有提供matchCondition参数，因此不应该被调用
    throw new RuntimeException("This method should not be called"); // 抛出运行时异常，因为LogicalAsofJoin需要matchCondition参数
  }

  @Override public Join copy(RelTraitSet traitSet, List<RelNode> inputs) { // 实现RelNode接口的copy方法，根据新的特征集合和输入列表创建副本
    assert inputs.size() == 2; // 断言：输入列表必须包含2个节点(左输入和右输入)
    return new LogicalAsofJoin(getCluster(), traitSet, hints, // 创建新的LogicalAsofJoin实例，使用当前cluster、新的traitSet和原有的hints
        inputs.get(0), inputs.get(1), // 使用新的左输入和右输入节点
        getCondition(), getMatchCondition(), joinType, systemFieldList); // 保留原有的连接条件、时间匹配条件、连接类型和系统字段列表
  }

  @Override public RelNode withHints(List<RelHint> hintList) { // 创建带有新提示列表的副本，用于修改优化器提示
    return new LogicalAsofJoin(getCluster(), traitSet, hintList, // 创建新的LogicalAsofJoin实例，使用当前cluster、traitSet和新的hintList
        left, right, condition, matchCondition, joinType, systemFieldList); // 保留原有的左右输入、连接条件、时间匹配条件、连接类型和系统字段列表
  }
}
