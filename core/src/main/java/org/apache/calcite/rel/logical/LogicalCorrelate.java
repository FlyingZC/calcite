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
package org.apache.calcite.rel.logical; // 声明包名，该类位于逻辑关系代数包中

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系代数的约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数表达式所属的集群，包含类型工厂等共享资源
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系代数表达式的特征集合（如约定、排序、分布等）
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化数据中反序列化关系代数表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基础接口
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle接口，用于遍历和转换关系代数表达式树
import org.apache.calcite.rel.core.Correlate; // 导入Correlate基类，LogicalCorrelate继承自该类
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId类，用于标识相关变量，用于处理相关子查询
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举，定义连接类型（INNER、LEFT、RIGHT、FULL等）
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，用于存储优化器的提示信息
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，用于表示列索引的不可变位集合

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，用于创建不可变列表

import java.util.List; // 导入Java标准库的List接口

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于非空检查

/**
 * LogicalCorrelate是一个执行嵌套循环连接的关系代数操作符。
 *
 * <p>它的行为类似于一种特殊的Join操作，但通过在环境中设置变量并重新启动其右侧输入来工作。
 *     这是处理相关子查询的关键机制：左侧输入的每一行都会被保存为一个相关变量，
 *     然后用这个相关变量的值来驱动右侧输入的执行。
 *
 * <p>LogicalCorrelate用于表示相关查询（correlated query），即子查询中引用了外部查询的列。
 *     例如：SELECT * FROM emp WHERE EXISTS (SELECT * FROM dept WHERE emp.deptno = dept.deptno)
 *     这里emp.deptno就是相关变量，需要通过LogicalCorrelate来实现。
 *
 * <p>实现策略之一是对表达式进行去相关（de-correlate），将相关查询转换为非相关的形式，
 *     这样就可以使用更高效的连接算法而不是嵌套循环。
 *
 * <p>关键概念：
 *     1. 相关变量（CorrelationId）：左侧输入的每一行会生成一个相关变量ID，用于在右侧输入中引用
 *     2. 必需列（requiredColumns）：右侧输入实际需要使用的左侧输入的列
 *     3. 连接类型（joinType）：支持INNER、LEFT等连接类型
 *
 * @see org.apache.calcite.rel.core.CorrelationId // 参见CorrelationId类以了解相关变量的详细信息
 */
public final class LogicalCorrelate extends Correlate { // 定义LogicalCorrelate类，继承自Correlate基类，final表示不可被继承
  //~ Instance fields --------------------------------------------------------
  // 实例字段区域标记（该类没有定义新的实例字段，所有字段都继承自父类Correlate）
  // 父类Correlate包含的主要字段：
  // - RelNode left: 左侧输入的关系代数表达式
  // - RelNode right: 右侧输入的关系代数表达式
  // - CorrelationId correlationId: 相关变量ID，用于标识左侧输入的行
  // - ImmutableBitSet requiredColumns: 右侧输入需要使用的左侧列的索引集合
  // - JoinRelType joinType: 连接类型（INNER、LEFT、RIGHT、FULL等）

  //~ Constructors -----------------------------------------------------------
  // 构造函数区域标记

  /**
   * 创建一个LogicalCorrelate实例（完整参数版本的构造函数）。
   *
   * <p>该构造函数用于创建一个新的LogicalCorrelate节点，表示一个相关子查询操作。
   * LogicalCorrelate的执行逻辑是：对于左侧输入的每一行，将其保存为一个相关变量，
   * 然后用这个相关变量的值来执行右侧输入，产生结果行。
   *
   * <p>执行流程示例：
   * 1. 从左侧输入读取一行数据
   * 2. 将该行的requiredColumns指定的列保存到correlationId对应的相关变量中
   * 3. 用这些相关变量的值执行右侧输入
   * 4. 将左侧行和右侧行根据joinType进行连接，生成输出行
   * 5. 重复步骤1-4，直到左侧输入的所有行都被处理完毕
   *
   * @param cluster      该关系代数表达式所属的集群，包含类型工厂、表达式工厂等共享资源
   * @param traitSet     该关系代数表达式的特征集合，包含约定、排序、分布等物理属性
   * @param hints        优化器提示列表，用于指导优化器如何处理该节点
   * @param left         左侧输入的关系代数表达式（通常是被引用的外部查询）
   * @param right        右侧输入的关系代数表达式（通常是相关子查询）
   * @param correlationId 相关变量ID，用于标识左侧输入的行，右侧输入可以通过这个ID引用左侧的值
   * @param requiredColumns 必需列集合，表示右侧输入实际需要使用的左侧输入的列索引
   *                        这是一个优化，只传递真正需要的列，减少数据传输
   * @param joinType     连接类型，决定了如何组合左右两侧的行
   *                     - INNER: 只有左右两侧都匹配的行才出现在结果中
   *                     - LEFT: 所有左侧行都出现在结果中，右侧行可能为null
   *                     - SEMI: 只返回左侧行，如果右侧有匹配则返回
   *                     - ANTI: 只返回左侧行，如果右侧没有匹配则返回
   */
  public LogicalCorrelate( // 构造函数声明，创建LogicalCorrelate实例
      RelOptCluster cluster, // 参数：关系代数表达式所属的集群
      RelTraitSet traitSet, // 参数：特征集合，包含约定、排序等
      List<RelHint> hints, // 参数：优化器提示列表
      RelNode left, // 参数：左侧输入关系代数表达式
      RelNode right, // 参数：右侧输入关系代数表达式
      CorrelationId correlationId, // 参数：相关变量ID
      ImmutableBitSet requiredColumns, // 参数：必需列集合
      JoinRelType joinType) { // 参数：连接类型
    super( // 调用父类Correlate的构造函数
        cluster, // 传递cluster参数给父类
        traitSet, // 传递traitSet参数给父类
        hints, // 传递hints参数给父类
        left, // 传递left参数给父类
        right, // 传递right参数给父类
        correlationId, // 传递correlationId参数给父类
        requiredColumns, // 传递requiredColumns参数给父类
        joinType); // 传递joinType参数给父类
  } // 构造函数结束

  @Deprecated // to be removed before 2.0 // 标记为已弃用，将在2.0版本前移除
  public LogicalCorrelate( // 构造函数声明（不包含hints参数的旧版本）
      RelOptCluster cluster, // 参数：关系代数表达式所属的集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode left, // 参数：左侧输入关系代数表达式
      RelNode right, // 参数：右侧输入关系代数表达式
      CorrelationId correlationId, // 参数：相关变量ID
      ImmutableBitSet requiredColumns, // 参数：必需列集合
      JoinRelType joinType) { // 参数：连接类型
    this(cluster, traitSet, ImmutableList.of(), left, right, // 调用完整参数版本的构造函数，hints参数设为空列表
        correlationId, requiredColumns, joinType); // 传递其余参数
  } // 构造函数结束

  /**
   * 通过解析序列化输出来创建LogicalCorrelate实例。
   *
   * <p>该构造函数用于从序列化的RelInput对象中恢复LogicalCorrelate节点。
   * RelInput通常来自JSON或其他序列化格式，包含重建该节点所需的所有信息。
   *
   * <p>反序列化过程：
   * 1. 从RelInput中提取cluster和traitSet
   * 2. 获取输入列表（left和right）
   * 3. 从序列化数据中提取correlation、requiredColumns和joinType
   * 4. 使用这些信息创建新的LogicalCorrelate实例
   *
   * @param input 包含序列化数据的RelInput对象，提供重建节点所需的所有信息
   */
  public LogicalCorrelate(RelInput input) { // 构造函数声明，从RelInput反序列化
    this(input.getCluster(), input.getTraitSet(), ImmutableList.of(), // 调用完整参数构造函数，hints设为空列表
        input.getInputs().get(0), // 从输入列表中获取第一个输入（left）
        input.getInputs().get(1), // 从输入列表中获取第二个输入（right）
        new CorrelationId( // 创建新的CorrelationId对象
            (Integer) requireNonNull(input.get("correlation"), "correlation")), // 从序列化数据中获取correlation值，非空检查
        input.getBitSet("requiredColumns"), // 从序列化数据中获取requiredColumns位集合
        requireNonNull(input.getEnum("joinType", JoinRelType.class), "joinType")); // 从序列化数据中获取joinType枚举值，非空检查
  } // 构造函数结束

  /** 创建一个LogicalCorrelate实例（静态工厂方法）。 */
  public static LogicalCorrelate create(RelNode left, RelNode right, List<RelHint> hints, // 静态工厂方法声明
      CorrelationId correlationId, ImmutableBitSet requiredColumns, // 参数：相关变量ID和必需列
      JoinRelType joinType) { // 参数：连接类型
    final RelOptCluster cluster = left.getCluster(); // 从左侧输入获取cluster对象
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建特征集合，约定设置为NONE（逻辑层）
    return new LogicalCorrelate(cluster, traitSet, hints, left, right, correlationId, // 创建并返回LogicalCorrelate实例
        requiredColumns, joinType); // 传递所有参数
  } // 静态工厂方法结束

  @Deprecated // to be removed before 2.0 // 标记为已弃用
  public static LogicalCorrelate create(RelNode left, RelNode right, // 静态工厂方法声明（不包含hints参数的旧版本）
      CorrelationId correlationId, ImmutableBitSet requiredColumns, // 参数：相关变量ID和必需列
      JoinRelType joinType) { // 参数：连接类型
    return create(left, right, ImmutableList.of(), correlationId, requiredColumns, joinType); // 调用完整版本，hints设为空列表
  } // 静态工厂方法结束

  //~ Methods ----------------------------------------------------------------
  // 方法区域标记

  /**
   * 复制当前LogicalCorrelate节点，可以修改部分属性。
   *
   * <p>该方法用于创建当前节点的一个副本，同时允许修改某些属性（如traitSet、left、right等）。
   * 这是关系代数表达式树的标准操作，用于优化器在规则应用时创建新的节点变体。
   *
   * <p>使用场景：
   * 1. 优化器应用规则时，需要创建修改后的节点
   * 2. 改变物理属性（如排序、分布）时
   * 3. 转换为不同的实现方式时
   *
   * @param traitSet 新的特征集合，可以包含不同的物理属性
   * @param left 新的左侧输入关系代数表达式
   * @param right 新的右侧输入关系代数表达式
   * @param correlationId 新的相关变量ID
   * @param requiredColumns 新的必需列集合
   * @param joinType 新的连接类型
   * @return 新创建的LogicalCorrelate节点，包含指定的属性
   */
  @Override public LogicalCorrelate copy(RelTraitSet traitSet, // 重写copy方法
      RelNode left, RelNode right, CorrelationId correlationId, // 参数：新的输入和关联信息
      ImmutableBitSet requiredColumns, JoinRelType joinType) { // 参数：新的列和连接类型
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：特征集合中包含Convention.NONE（逻辑层的约定）
    return new LogicalCorrelate(getCluster(), traitSet, hints, left, right, // 创建并返回新的LogicalCorrelate实例
        correlationId, requiredColumns, joinType); // 传递所有参数
  } // copy方法结束

  /**
   * 接受一个RelShuttle访问器，用于遍历和转换关系代数表达式树。
   *
   * <p>这是访问者模式（Visitor Pattern）的实现，允许外部代码遍历和修改关系代数表达式树。
   * RelShuttle可以访问树中的每个节点，并可以选择性地替换节点或保持不变。
   *
   * <p>使用场景：
   * 1. 优化器遍历表达式树进行分析
   * 2. 规则引擎应用转换规则
   * 3. 表达式树的深度拷贝或修改
   *
   * @param shuttle RelShuttle访问器对象，负责访问和处理关系代数表达式树
   * @return 访问器处理后的结果，可能是当前节点或被替换的新节点
   */
  @Override public RelNode accept(RelShuttle shuttle) { // 重写accept方法
    return shuttle.visit(this); // 调用shuttle的visit方法，将当前节点传递给访问器
  } // accept方法结束

  /**
   * 创建具有新提示（hints）的LogicalCorrelate节点副本。
   *
   * <p>该方法用于修改节点的提示信息，而不改变其他属性。
   * 提示是给优化器的指导信息，用于影响优化决策，但不保证一定被遵守。
   *
   * <p>使用场景：
   * 1. 优化器根据规则添加或修改提示
   * 2. 用户通过SQL提示（hint）影响执行计划
   * 3. 在优化过程中传递优化建议
   *
   * @param hintList 新的提示列表，包含一个或多个RelHint对象
   * @return 具有新提示的LogicalCorrelate节点副本，其他属性保持不变
   */
  @Override public RelNode withHints(List<RelHint> hintList) { // 重写withHints方法
    return new LogicalCorrelate(getCluster(), traitSet, hintList, left, right, // 创建并返回新的LogicalCorrelate实例，使用新的提示列表
        correlationId, requiredColumns, joinType); // 传递其他现有参数
  } // withHints方法结束
} // 类定义结束
