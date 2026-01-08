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
package org.apache.calcite.rel.logical; // 逻辑层Join关系表达式所在的包，包含所有逻辑操作符

import org.apache.calcite.plan.Convention; // 导入调用约定枚举，用于定义关系表达式的实现约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，包含优化器集群信息，如类型工厂和表达式工厂
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合，如约定和排序规则
import org.apache.calcite.rel.RelInput; // 导入RelInput接口，用于从序列化数据创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，所有关系表达式的基接口
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle接口，用于遍历和修改关系表达式树
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系表达式输出为可读格式
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId类，表示相关变量的标识符
import org.apache.calcite.rel.core.Join; // 导入Join抽象类，LogicalJoin的父类，定义Join的基本行为
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举，定义Join的类型（INNER, LEFT, RIGHT, FULL等）
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，用于向优化器提供提示信息
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型的字段
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，行表达式的基接口，用于表示条件表达式

import com.google.common.collect.ImmutableList; // 导入Guava的不可变列表类，用于存储不可变的字段列表
import com.google.common.collect.ImmutableSet; // 导入Guava的不可变集合类，用于存储不可变的集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型

import java.util.List; // 导入List接口，Java集合框架的列表接口
import java.util.Objects; // 导入Objects工具类，提供对象操作的静态方法
import java.util.Set; // 导入Set接口，Java集合框架的集合接口

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于参数非空校验

/**
 * Sub-class of {@link org.apache.calcite.rel.core.Join} // Join抽象类的子类
 * not targeted at any particular engine or calling convention. // 不针对任何特定的引擎或调用约定，是纯逻辑层面的Join操作
 *
 * <p>LogicalJoin是Calcite中逻辑层的Join关系表达式，表示两个关系表的连接操作。
 * 它不关心具体的物理实现方式，只描述连接的逻辑语义。LogicalJoin是优化器规则转换的基础，
 * 可以被转换为各种物理实现（如HashJoin、MergeJoin、NestedLoopJoin等）。
 *
 * <p>主要特点：
 * <ul>
 * <li>使用Convention.NONE约定，表示这是纯逻辑操作符，没有特定的实现约定</li>
 * <li>支持所有标准Join类型：INNER JOIN、LEFT OUTER JOIN、RIGHT OUTER JOIN、FULL OUTER JOIN</li>
 * <li>支持半连接（Semi-Join）优化，用于子查询优化</li>
 * <li>支持相关变量的传递，用于处理相关子查询</li>
 * </ul>
 *
 * <p>Some rules: // 一些相关的优化规则
 *
 * <ul>
 * <li>{@link org.apache.calcite.rel.rules.JoinExtractFilterRule} converts an // JoinExtractFilterRule规则将
 * {@link LogicalJoin inner join} to a {@link LogicalFilter filter} on top of a // 内连接转换为笛卡尔积连接上的过滤器
 * {@link LogicalJoin cartesian inner join}. // 用于提取连接条件中的过滤条件
 * </ul>
 */
public final class LogicalJoin extends Join { // LogicalJoin类，继承自Join抽象类，final表示不可被继承
  //~ Instance fields -------------------------------------------------------- // 实例字段部分开始标记

  // NOTE jvs 14-Mar-2006:  Normally we don't use state like this // 注释：通常我们不使用状态来控制规则触发
  // to control rule firing, but due to the non-local nature of // 但由于半连接优化的非局部性质
  // semijoin optimizations, it's pretty much required. // 使用这种状态是必须的
  private final boolean semiJoinDone; // 标记此Join是否已经转换为半连接，用于控制优化规则的触发

  private final ImmutableList<RelDataTypeField> systemFieldList; // 系统字段列表，这些字段会被添加到输出行类型的前面，通常为空列表但绝不能为null

  //~ Constructors ----------------------------------------------------------- // 构造方法部分开始标记

  /**
   * Creates a LogicalJoin. // 创建一个LogicalJoin实例
   *
   * <p>Use {@link #create} unless you know what you're doing. // 除非清楚自己在做什么，否则建议使用create工厂方法而不是直接调用构造函数
   *
   * <p>这是LogicalJoin的主要构造方法，用于创建一个逻辑连接操作符。
   * 它接收左右两个输入关系、连接条件、连接类型等参数，构建一个完整的Join节点。
   *
   * @param cluster          Cluster // RelOptCluster对象，包含优化器集群信息，如类型工厂、表达式工厂等共享资源
   * @param traitSet         Trait set // 关系表达式的特征集合，通常包含Convention.NONE约定
   * @param hints            Hints // 提示信息列表，用于向优化器提供额外的指导信息
   * @param left             Left input // 左侧输入关系表达式，即Join操作的左表
   * @param right            Right input // 右侧输入关系表达式，即Join操作的右表
   * @param condition        Join condition // 连接条件，一个RexNode表达式，定义了两个表如何连接
   * @param joinType         Join type // 连接类型，枚举值包括INNER, LEFT, RIGHT, FULL等
   * @param variablesSet     Set of variables that are set by the // 相关变量集合，由左子树设置并被右子树使用的变量
   *                         LHS and used by the RHS and are not available to // 这些变量对LogicalJoin之上的节点不可见
   *                         nodes above this LogicalJoin in the tree // 用于处理相关子查询
   * @param semiJoinDone     Whether this join has been translated to a // 标记此Join是否已经转换为半连接
   *                         semi-join // 用于控制半连接优化规则的触发
   * @param systemFieldList  List of system fields that will be prefixed to // 系统字段列表，会被添加到输出行类型的前面
   *                         output row type; typically empty but must not be // 通常为空列表但绝不能为null
   *                         null
   * @see #isSemiJoinDone() // 参见isSemiJoinDone方法
   */
  public LogicalJoin( // LogicalJoin构造方法
      RelOptCluster cluster, // 参数：优化器集群
      RelTraitSet traitSet, // 参数：特征集合
      List<RelHint> hints, // 参数：提示列表
      RelNode left, // 参数：左输入
      RelNode right, // 参数：右输入
      RexNode condition, // 参数：连接条件
      Set<CorrelationId> variablesSet, // 参数：相关变量集合
      JoinRelType joinType, // 参数：连接类型
      boolean semiJoinDone, // 参数：半连接完成标记
      ImmutableList<RelDataTypeField> systemFieldList) { // 参数：系统字段列表
    super(cluster, traitSet, hints, left, right, condition, variablesSet, joinType); // 调用父类Join的构造方法，初始化Join的基本属性
    this.semiJoinDone = semiJoinDone; // 设置半连接完成标记
    this.systemFieldList = requireNonNull(systemFieldList, "systemFieldList"); // 设置系统字段列表，使用requireNonNull确保不为null
  }

  @Deprecated // to be removed before 2.0 // 已废弃的构造方法，将在2.0版本前移除
  public LogicalJoin(RelOptCluster cluster, RelTraitSet traitSet, // 废弃的构造方法，缺少hints参数
      RelNode left, RelNode right, RexNode condition, Set<CorrelationId> variablesSet, // 参数定义
      JoinRelType joinType, boolean semiJoinDone, // 参数定义
      ImmutableList<RelDataTypeField> systemFieldList) { // 参数定义
    this(cluster, traitSet, ImmutableList.of(), left, right, condition, // 调用完整构造方法，hints设为空列表
        variablesSet, joinType, semiJoinDone, systemFieldList); // 传递其他参数
  }

  @Deprecated // to be removed before 2.0 // 已废弃的构造方法，将在2.0版本前移除
  public LogicalJoin(RelOptCluster cluster, RelTraitSet traitSet, RelNode left, // 废弃的构造方法，使用String类型的variablesStopped
      RelNode right, RexNode condition, JoinRelType joinType, // 参数定义
      Set<String> variablesStopped, boolean semiJoinDone, // 旧版本的变量停止集合，使用String类型
      ImmutableList<RelDataTypeField> systemFieldList) { // 参数定义
    this(cluster, traitSet, ImmutableList.of(), left, right, condition, // 调用完整构造方法，hints设为空列表
        CorrelationId.setOf(variablesStopped), joinType, semiJoinDone, // 将String集合转换为CorrelationId集合
        systemFieldList); // 传递其他参数
  }

  @Deprecated // to be removed before 2.0 // 已废弃的构造方法，将在2.0版本前移除
  public LogicalJoin(RelOptCluster cluster, RelNode left, RelNode right, // 废弃的简化构造方法
      RexNode condition, JoinRelType joinType, Set<String> variablesStopped) { // 参数定义，缺少semiJoinDone和systemFieldList
    this(cluster, cluster.traitSetOf(Convention.NONE), ImmutableList.of(), // 调用完整构造方法，traitSet使用Convention.NONE
        left, right, condition, CorrelationId.setOf(variablesStopped), // 将String集合转换为CorrelationId集合
        joinType, false, ImmutableList.of()); // semiJoinDone设为false，systemFieldList设为空列表
  }

  @Deprecated // to be removed before 2.0 // 已废弃的构造方法，将在2.0版本前移除
  public LogicalJoin(RelOptCluster cluster, RelNode left, RelNode right, // 废弃的简化构造方法，缺少traitSet和hints
      RexNode condition, JoinRelType joinType, Set<String> variablesStopped, // 参数定义
      boolean semiJoinDone, ImmutableList<RelDataTypeField> systemFieldList) { // 参数定义
    this(cluster, cluster.traitSetOf(Convention.NONE), ImmutableList.of(), // 调用完整构造方法，traitSet使用Convention.NONE，hints设为空列表
        left, right, condition, CorrelationId.setOf(variablesStopped), joinType, // 将String集合转换为CorrelationId集合
        semiJoinDone, systemFieldList); // 传递其他参数
  }

  /**
   * Creates a LogicalJoin by parsing serialized output. // 通过解析序列化输出来创建LogicalJoin
   *
   * <p>这个构造方法用于从序列化的RelInput对象创建LogicalJoin实例。
   * RelInput通常来自JSON或其他序列化格式，包含了重建LogicalJoin所需的所有信息。
   * 这种方式在分布式计算框架中很有用，可以将逻辑计划序列化后发送到远程节点执行。
   */
  public LogicalJoin(RelInput input) { // 从RelInput创建LogicalJoin的构造方法
    this(input.getCluster(), input.getCluster().traitSetOf(Convention.NONE), // 从RelInput获取cluster，使用Convention.NONE约定
        ImmutableList.of(), // hints设为空列表
        input.getInputs().get(0), input.getInputs().get(1), // 获取左右两个输入关系表达式
        requireNonNull(input.getExpression("condition"), "condition"), // 从RelInput获取连接条件表达式，确保不为null
        ImmutableSet.of(), // variablesSet设为空集合
        requireNonNull(input.getEnum("joinType", JoinRelType.class), "joinType"), // 从RelInput获取连接类型，确保不为null
        false, // semiJoinDone设为false
        ImmutableList.of()); // systemFieldList设为空列表
  }

  /** Creates a LogicalJoin. // 创建LogicalJoin的工厂方法（简化版本）
   *
   * <p>这是创建LogicalJoin的推荐方法，它会自动设置traitSet为Convention.NONE，
   * 并将semiJoinDone和systemFieldList设置为默认值。
   *
   * @param left          左侧输入关系表达式
   * @param right         右侧输入关系表达式
   * @param hints         提示信息列表
   * @param condition     连接条件表达式
   * @param variablesSet  相关变量集合
   * @param joinType      连接类型
   * @return 新创建的LogicalJoin实例
   */
  public static LogicalJoin create(RelNode left, RelNode right, List<RelHint> hints, // 创建LogicalJoin的静态工厂方法
      RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType) { // 参数定义
    return create(left, right, hints, condition, variablesSet, joinType, false, // 调用完整的create方法，semiJoinDone设为false
        ImmutableList.of()); // systemFieldList设为空列表
  }

  /** Creates a LogicalJoin, flagged with whether it has been translated to a // 创建LogicalJoin的工厂方法（完整版本）
   * semi-join. // 可以指定是否已转换为半连接
   *
   * <p>这是创建LogicalJoin的完整工厂方法，允许指定所有参数。
   * 它会自动设置traitSet为Convention.NONE，这是逻辑操作符的标准约定。
   *
   * @param left            左侧输入关系表达式
   * @param right           右侧输入关系表达式
   * @param hints           提示信息列表
   * @param condition       连接条件表达式
   * @param variablesSet    相关变量集合
   * @param joinType        连接类型
   * @param semiJoinDone    半连接完成标记
   * @param systemFieldList 系统字段列表
   * @return 新创建的LogicalJoin实例
   */
  public static LogicalJoin create(RelNode left, RelNode right, List<RelHint> hints, // 创建LogicalJoin的完整静态工厂方法
      RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType, // 参数定义
      boolean semiJoinDone, ImmutableList<RelDataTypeField> systemFieldList) { // 参数定义
    final RelOptCluster cluster = left.getCluster(); // 从左输入获取cluster对象
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建traitSet，使用Convention.NONE约定
    return new LogicalJoin(cluster, traitSet, hints, left, right, condition, // 调用构造方法创建LogicalJoin实例
        variablesSet, joinType, semiJoinDone, systemFieldList); // 传递所有参数
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分开始标记

  @Override public LogicalJoin copy(RelTraitSet traitSet, RexNode conditionExpr, // 复制方法：创建LogicalJoin的副本，可以修改部分属性
      RelNode left, RelNode right, JoinRelType joinType, boolean semiJoinDone) { // 参数：新的特征集合、条件表达式、左右输入、连接类型、半连接标记
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：如果traitSet适用，必须包含Convention.NONE约定
    return new LogicalJoin(getCluster(), // 返回新的LogicalJoin实例，使用当前cluster
        getCluster().traitSetOf(Convention.NONE), hints, left, right, conditionExpr, // traitSet强制使用Convention.NONE，使用当前的hints
        variablesSet, joinType, semiJoinDone, systemFieldList); // 使用当前的variablesSet和systemFieldList
  }

  @Override public RelNode accept(RelShuttle shuttle) { // 接受访问者模式：让RelShuttle访问此节点
    return shuttle.visit(this); // 将控制权交给RelShuttle，让它决定如何处理此节点
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 解释方法：将LogicalJoin的属性输出到RelWriter
    // Don't ever print semiJoinDone=false. This way, we // 不要输出semiJoinDone=false，这样可以避免在
    // don't clutter things up in optimizers that don't use semi-joins. // 不使用半连接的优化器中产生混乱
    return super.explainTerms(pw) // 调用父类的explainTerms方法输出基本属性
        .itemIf("semiJoinDone", semiJoinDone, semiJoinDone); // 只有当semiJoinDone为true时才输出该属性
  }

  @Override public boolean deepEquals(@Nullable Object obj) { // 深度相等比较：比较两个LogicalJoin的所有属性是否相等
    if (this == obj) { // 如果是同一个对象引用
      return true; // 直接返回true
    }
    return deepEquals0(obj) // 调用父类的deepEquals0方法比较继承的属性
        && semiJoinDone == ((LogicalJoin) obj).semiJoinDone // 比较semiJoinDone字段是否相等
        && systemFieldList.equals(((LogicalJoin) obj).systemFieldList); // 比较systemFieldList字段是否相等
  }

  @Override public int deepHashCode() { // 深度哈希码计算：基于所有属性计算哈希码
    return Objects.hash(deepHashCode0(), semiJoinDone, systemFieldList); // 结合父类的哈希码、semiJoinDone和systemFieldList计算哈希码
  }

  @Override public boolean isSemiJoinDone() { // 获取半连接完成标记：判断此Join是否已经转换为半连接
    return semiJoinDone; // 返回semiJoinDone字段的值
  }

  @Override public List<RelDataTypeField> getSystemFieldList() { // 获取系统字段列表：返回将被添加到输出行类型前面的系统字段
    return systemFieldList; // 返回systemFieldList字段的值
  }

  @Override public RelNode withHints(List<RelHint> hintList) { // 设置提示：创建一个新的LogicalJoin实例，使用新的提示列表
    return new LogicalJoin(getCluster(), traitSet, hintList, // 返回新的LogicalJoin实例，使用新的hintList
        left, right, condition, variablesSet, joinType, semiJoinDone, systemFieldList); // 其他属性保持不变
  }
}
