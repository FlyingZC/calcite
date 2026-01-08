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
package org.apache.calcite.rel.logical; // LogicalFilter类所属的逻辑关系表达式包

import org.apache.calcite.plan.Convention; // 导入Convention，用于定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster，用于管理关系表达式集群
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet，用于定义关系表达式的特征集合
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef，用于定义排序特征
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef，用于定义分布特征
import org.apache.calcite.rel.RelInput; // 导入RelInput，用于从序列化输入创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode，关系表达式的基础接口
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle，用于遍历和修改关系表达式树
import org.apache.calcite.rel.RelWriter; // 导入RelWriter，用于将关系表达式写入输出流
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId，用于标识关联变量
import org.apache.calcite.rel.core.Filter; // 导入Filter，LogicalFilter的父类，表示过滤操作
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint，用于存储关系表达式的提示信息
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入RelMdCollation，用于计算排序元数据
import org.apache.calcite.rel.metadata.RelMdDistribution; // 导入RelMdDistribution，用于计算分布元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery，用于查询关系表达式的元数据
import org.apache.calcite.rex.RexNode; // 导入RexNode，行表达式的基类，用于表示过滤条件

import com.google.common.collect.ImmutableList; // 导入ImmutableList，用于创建不可变列表
import com.google.common.collect.ImmutableSet; // 导入ImmutableSet，用于创建不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可空类型

import java.util.List; // 导入List接口
import java.util.Objects; // 导入Objects工具类
import java.util.Set; // 导入Set接口

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于参数校验

/**
 * LogicalFilter类：逻辑过滤操作的关系表达式实现
 * 
 * 这个类是Filter抽象类的具体实现，表示SQL中的WHERE子句或HAVING子句对应的过滤操作
 * 它不针对任何特定的引擎或调用约定，是一个纯粹的逻辑操作符
 * 
 * 主要功能：
 * 1. 根据布尔条件过滤输入关系表达式的行
 * 2. 支持关联变量（correlation variables），用于处理子查询中的关联
 * 3. 继承自Filter基类，实现了逻辑层面的过滤语义
 * 
 * 在查询优化过程中，LogicalFilter会被转换为特定物理实现的Filter操作符
 * 例如EnumerableFilter、JdbcFilter等，具体取决于目标执行引擎
 * 
 * 典型使用场景：
 * - SQL查询中的WHERE条件过滤
 * - 聚合后的HAVING条件过滤
 * - 子查询中的关联条件处理
 */
public final class LogicalFilter extends Filter { // LogicalFilter类，继承自Filter，使用final修饰表示不可被继承
  private final ImmutableSet<CorrelationId> variablesSet; // 成员变量：存储此过滤操作设置的相关变量集合，用于处理子查询中的关联引用

  //~ Constructors -----------------------------------------------------------

  /**
   * 完整参数构造方法：创建一个LogicalFilter实例
   *
   * 这是LogicalFilter的主要构造方法，接收所有必要的参数
   * 注意：除非你明确知道自己在做什么，否则建议使用create工厂方法而不是直接调用构造方法
   *
   * @param cluster   关系表达式所属的集群，包含优化器上下文和共享资源
   * @param traitSet  关系表达式的特征集合，定义了物理属性如排序、分布等
   * @param hints     关系表达式的提示列表，用于指导优化器做出特定决策
   * @param child     输入关系表达式，即被过滤的数据源
   * @param condition 布尔表达式，用于判断每一行是否应该通过过滤
   * @param variablesSet 此关系表达式设置的关联变量集合，用于嵌套表达式（如子查询）中的引用
   */
  public LogicalFilter(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      List<RelHint> hints, // 参数：提示列表
      RelNode child, // 参数：输入关系表达式
      RexNode condition, // 参数：过滤条件表达式
      ImmutableSet<CorrelationId> variablesSet) { // 参数：关联变量集合
    super(cluster, traitSet, hints, child, condition); // 调用父类Filter的构造方法初始化基础属性
    this.variablesSet = requireNonNull(variablesSet, "variablesSet"); // 初始化variablesSet，使用requireNonNull确保非空
  }

  /**
   * 简化构造方法：创建一个LogicalFilter实例（不包含hints参数）
   *
   * 这个构造方法省略了hints参数，内部会创建一个空的hints列表
   * 注意：除非你明确知道自己在做什么，否则建议使用create工厂方法而不是直接调用构造方法
   *
   * @param cluster   关系表达式所属的集群
   * @param traitSet  关系表达式的特征集合
   * @param child     输入关系表达式
   * @param condition 布尔表达式，用于判断每一行是否应该通过过滤
   * @param variablesSet 此关系表达式设置的关联变量集合
   */
  public LogicalFilter(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode child, // 参数：输入关系表达式
      RexNode condition, // 参数：过滤条件表达式
      ImmutableSet<CorrelationId> variablesSet) { // 参数：关联变量集合
    this(cluster, traitSet, ImmutableList.of(), child, condition, variablesSet); // 调用完整构造方法，传入空的hints列表
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public LogicalFilter(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelNode child, // 参数：输入关系表达式
      RexNode condition) { // 参数：过滤条件表达式
    this(cluster, traitSet, child, condition, ImmutableSet.of()); // 调用简化构造方法，传入空的关联变量集合
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public LogicalFilter(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelNode child, // 参数：输入关系表达式
      RexNode condition) { // 参数：过滤条件表达式
    this(cluster, cluster.traitSetOf(Convention.NONE), child, condition, // 调用简化构造方法，自动创建包含Convention.NONE的traitSet
        ImmutableSet.of()); // 传入空的关联变量集合
  }

  /**
   * 反序列化构造方法：通过解析序列化输出来创建LogicalFilter
   *
   * 这个构造方法用于从序列化形式（如JSON）恢复LogicalFilter对象
   * 主要用于持久化和跨进程传输场景
   *
   * @param input 包含序列化数据的RelInput对象
   */
  public LogicalFilter(RelInput input) { // 参数：序列化输入对象
    super(input); // 调用父类Filter的反序列化构造方法
    this.variablesSet = ImmutableSet.of(); // 初始化variablesSet为空集合
  }

  /**
   * 工厂方法：创建一个LogicalFilter实例（不包含关联变量）
   *
   * 这是推荐的创建LogicalFilter的方式，它会自动处理特征集合的设置
   * 这个方法内部会调用另一个重载的create方法，传入空的关联变量集合
   *
   * @param input 输入关系表达式，即被过滤的数据源
   * @param condition 布尔表达式，用于判断每一行是否应该通过过滤
   * @return 新创建的LogicalFilter实例
   */
  public static LogicalFilter create(final RelNode input, RexNode condition) { // 静态工厂方法
    return create(input, condition, ImmutableSet.of()); // 调用重载的create方法，传入空的关联变量集合
  }

  /**
   * 工厂方法：创建一个LogicalFilter实例（包含关联变量）
   *
   * 这是推荐的创建LogicalFilter的方式，它会自动处理特征集合的设置
   * 该方法会根据输入关系表达式的元数据智能地设置排序和分布特征
   *
   * 处理流程：
   * 1. 获取输入关系表达式的集群和元数据查询对象
   * 2. 创建基础特征集合，使用Convention.NONE表示逻辑约定
   * 3. 根据输入的排序特征智能设置过滤后的排序特征
   * 4. 根据输入的分布特征智能设置过滤后的分布特征
   * 5. 使用计算出的特征集合创建LogicalFilter实例
   *
   * @param input 输入关系表达式，即被过滤的数据源
   * @param condition 布尔表达式，用于判断每一行是否应该通过过滤
   * @param variablesSet 此关系表达式设置的关联变量集合
   * @return 新创建的LogicalFilter实例
   */
  public static LogicalFilter create(final RelNode input, RexNode condition, // 静态工厂方法
      ImmutableSet<CorrelationId> variablesSet) { // 参数：关联变量集合
    final RelOptCluster cluster = input.getCluster(); // 获取输入关系表达式的集群
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象，用于查询输入的元数据
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE) // 创建基础特征集合，使用NONE约定表示逻辑操作
        .replaceIfs(RelCollationTraitDef.INSTANCE, // 智能替换排序特征：如果输入有排序，过滤操作可能保持排序
            () -> RelMdCollation.filter(mq, input)) // 使用RelMdCollation计算过滤后的排序特征
        .replaceIf(RelDistributionTraitDef.INSTANCE, // 智能替换分布特征：如果输入有分布，过滤操作可能保持分布
            () -> RelMdDistribution.filter(mq, input)); // 使用RelMdDistribution计算过滤后的分布特征
    return new LogicalFilter(cluster, traitSet, input, condition, variablesSet); // 使用计算出的特征集合创建LogicalFilter实例
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * 获取此过滤操作设置的关联变量集合
   *
   * 关联变量用于处理子查询中的关联引用，例如：
   * SELECT * FROM emp WHERE EXISTS (SELECT * FROM dept WHERE emp.deptno = dept.deptno)
   * 其中emp.deptno就是关联变量
   *
   * @return 关联变量的不可变集合，如果没有关联变量则返回空集合
   */
  @Override public Set<CorrelationId> getVariablesSet() { // 重写父类Filter的方法
    return variablesSet; // 返回存储的关联变量集合
  }

  /**
   * 复制此LogicalFilter，可以修改特征集合、输入和条件
   *
   * 这个方法用于在查询优化过程中创建新的LogicalFilter实例
   * 通常在应用优化规则时使用，例如将过滤条件下推到子查询中
   *
   * @param traitSet 新的特征集合，定义了新的物理属性
   * @param input 新的输入关系表达式
   * @param condition 新的过滤条件表达式
   * @return 新创建的LogicalFilter实例，具有指定的特征、输入和条件
   */
  @Override public LogicalFilter copy(RelTraitSet traitSet, RelNode input, // 重写父类Filter的copy方法
      RexNode condition) { // 参数：新的过滤条件
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：确保traitSet包含Convention.NONE（如果适用）
    return new LogicalFilter(getCluster(), traitSet, hints, input, condition, // 创建新的LogicalFilter实例，保留原有的hints和variablesSet
        variablesSet); // 传入原有的关联变量集合
  }

  /**
   * 接受关系表达式访问器（RelShuttle）
   *
   * 这个方法实现了访问者模式，允许RelShuttle遍历和修改关系表达式树
   * RelShuttle可以访问整个关系表达式树，并在需要时替换节点
   *
   * @param shuttle 关系表达式访问器，用于遍历和修改关系表达式树
   * @return 访问器处理后的结果，可能是修改后的关系表达式
   */
  @Override public RelNode accept(RelShuttle shuttle) { // 重写父类Filter的accept方法
    return shuttle.visit(this); // 调用访问器的visit方法，让访问器处理此LogicalFilter
  }

  /**
   * 将此LogicalFilter的详细信息写入RelWriter
   *
   * 这个方法用于生成关系表达式的可读描述，主要用于调试和日志输出
   * 它会输出过滤条件、输入关系表达式等信息
   *
   * @param pw 关系表达式写入器，用于输出关系表达式的详细信息
   * @return 写入器本身，支持链式调用
   */
  @Override public RelWriter explainTerms(RelWriter pw) { // 重写父类Filter的explainTerms方法
    return super.explainTerms(pw) // 调用父类的explainTerms方法，输出基础信息（如条件、输入等）
        .itemIf("variablesSet", variablesSet, !variablesSet.isEmpty()); // 如果variablesSet非空，则输出关联变量集合
  }

  /**
   * 深度比较两个LogicalFilter对象是否相等
   *
   * 这个方法比较两个LogicalFilter的所有属性，包括：
   * - 父类Filter的所有属性（输入、条件等）
   * - 关联变量集合
   *
   * 注意：这是深度比较，会递归比较所有子节点
   *
   * @param obj 要比较的对象
   * @return 如果两个LogicalFilter在所有属性上都相等则返回true，否则返回false
   */
  @Override public boolean deepEquals(@Nullable Object obj) { // 重写父类Filter的deepEquals方法
    return deepEquals0(obj) // 调用父类的deepEquals0方法，比较父类的属性
        && variablesSet.equals(((LogicalFilter) obj).variablesSet); // 比较关联变量集合是否相等
  }

  /**
   * 计算此LogicalFilter的深度哈希码
   *
   * 这个方法生成一个基于所有属性的哈希码，包括：
   * - 父类Filter的所有属性
   * - 关联变量集合
   *
   * 深度哈希码确保了如果两个LogicalFilter在所有属性上都相等，它们的哈希码也相等
   *
   * @return 基于所有属性的哈希码
   */
  @Override public int deepHashCode() { // 重写父类Filter的deepHashCode方法
    return Objects.hash(deepHashCode0(), variablesSet); // 计算父类哈希码和关联变量集合的组合哈希码
  }

  /**
   * 创建带有新提示列表的LogicalFilter副本
   *
   * 这个方法用于在保持其他属性不变的情况下，更新关系表达式的提示信息
   * 提示（hints）可以指导优化器做出特定的优化决策
   *
   * @param hintList 新的提示列表
   * @return 新创建的LogicalFilter实例，具有相同的输入、条件和关联变量，但使用新的提示列表
   */
  @Override public RelNode withHints(List<RelHint> hintList) { // 重写父类Filter的withHints方法
    return new LogicalFilter(getCluster(), traitSet, hintList, input, condition, variablesSet); // 创建新的LogicalFilter实例，使用新的提示列表
  }
}
