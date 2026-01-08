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
 */ // Apache License 2.0 开源协议声明,规定代码的使用、修改和分发条款
package org.apache.calcite.plan; // 包声明,定义类所属的包为 org.apache.calcite.plan,这是 Calcite 框架中负责查询规划的核心包

import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口,是 Calcite 中所有关系代数算子的基类,代表查询树中的一个节点
import org.apache.calcite.rel.metadata.CachingRelMetadataProvider; // 导入缓存元数据提供者,用于缓存关系表达式的元数据以提高性能
import org.apache.calcite.rel.metadata.RelMetadataProvider; // 导入关系元数据提供者接口,用于提供关系表达式的元数据信息(如行数、大小等)
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询类,用于查询关系表达式的各种元数据信息
import org.apache.calcite.rex.RexExecutor; // 导入行表达式执行器,用于执行和评估常量表达式
import org.apache.calcite.sql2rel.RelDecorrelator; // 导入关系去相关器,用于消除子查询的相关性,将相关子查询转换为非相关子查询
import org.apache.calcite.util.CancelFlag; // 导入取消标志,用于支持长时间运行的查询优化过程的中断
import org.apache.calcite.util.trace.CalciteTrace; // 导入 Calcite 追踪工具,用于日志记录和调试

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值注解,用于标记可能为 null 的参数或返回值
import org.slf4j.Logger; // 导入 SLF4J 日志接口,用于记录日志信息

import java.util.List; // 导入 Java 集合框架的 List 接口,用于存储有序的元素列表
import java.util.regex.Pattern; // 导入正则表达式模式类,用于字符串模式匹配

/**
 * A <code>RelOptPlanner</code> is a query optimizer: it transforms a relational
 * expression into a semantically equivalent relational expression, according to
 * a given set of rules and a cost model.
 */ // 类文档注释:RelOptPlanner 是查询优化器接口,负责根据一组规则和成本模型将关系表达式转换为语义等价的关系表达式
public interface RelOptPlanner { // 定义 RelOptPlanner 接口,这是 Calcite 查询优化器的核心接口,定义了优化器必须实现的所有方法
  //~ Static fields/initializers --------------------------------------------- // 静态字段/初始化器分割线,用于代码组织和可读性

  Logger LOGGER = CalciteTrace.getPlannerTracer(); // 定义静态日志记录器,使用 CalciteTrace 获取规划器专用的日志追踪器,用于记录优化过程中的调试和错误信息

  //~ Methods ---------------------------------------------------------------- // 方法分割线,用于代码组织和可读性

  /**
   * Sets the root node of this query.
   *
   * @param rel Relational expression
   */ // 方法文档注释:设置查询的根节点,参数 rel 是要设置的关系表达式
  void setRoot(RelNode rel); // 声明设置根节点的方法,将给定的关系表达式设置为查询树的根节点,优化过程将从此节点开始

  /**
   * Returns the root node of this query.
   *
   * @return Root node
   */ // 方法文档注释:返回查询的根节点,返回值是根节点的关系表达式
  @Nullable RelNode getRoot(); // 声明获取根节点的方法,返回当前查询树的根节点,可能为 null 表示尚未设置根节点

  /**
   * Registers a rel trait definition. If the {@link RelTraitDef} has already
   * been registered, does nothing.
   *
   * @return whether the RelTraitDef was added, as per
   * {@link java.util.Collection#add}
   */ // 方法文档注释:注册关系特征定义,如果 RelTraitDef 已经注册则不做任何操作,返回值表示是否成功添加
  boolean addRelTraitDef(RelTraitDef relTraitDef); // 声明注册关系特征定义的方法,RelTraitDef 定义了关系表达式的特征类型(如约定、排序、分区等),成功添加返回 true,已存在返回 false

  /**
   * Clear all the registered RelTraitDef.
   */ // 方法文档注释:清除所有已注册的 RelTraitDef
  void clearRelTraitDefs(); // 声明清除所有关系特征定义的方法,删除所有已注册的特征类型定义,用于重置优化器的特征系统

  /**
   * Returns the list of active trait types.
   */ // 方法文档注释:返回当前活动的特征类型列表
  List<RelTraitDef> getRelTraitDefs(); // 声明获取所有关系特征定义列表的方法,返回当前优化器中注册的所有特征类型,如约定、排序、分区等

  /**
   * Removes all internal state, including all registered rules,
   * materialized views, and lattices.
   */ // 方法文档注释:清除所有内部状态,包括所有已注册的规则、物化视图和立方体结构
  void clear(); // 声明清除所有内部状态的方法,重置优化器到初始状态,删除所有规则、物化视图、立方体等优化相关数据

  /**
   * Returns the list of all registered rules.
   */ // 方法文档注释:返回所有已注册规则的列表
  List<RelOptRule> getRules(); // 声明获取所有优化规则列表的方法,返回当前优化器中注册的所有转换规则,这些规则用于优化查询树

  /**
   * Registers a rule.
   *
   * <p>If the rule has already been registered, does nothing.
   * This method determines if the given rule is a
   * {@link org.apache.calcite.rel.convert.ConverterRule} and pass the
   * ConverterRule to all
   * {@link #addRelTraitDef(RelTraitDef) registered} RelTraitDef
   * instances.
   *
   * @return whether the rule was added, as per
   * {@link java.util.Collection#add}
   */ // 方法文档注释:注册优化规则,如果规则已注册则不做任何操作,还会自动将转换规则传递给所有已注册的特征定义
  boolean addRule(RelOptRule rule); // 声明注册优化规则的方法,将给定的优化规则添加到优化器中,如果是转换规则会自动注册到相关的特征定义

  /**
   * Removes a rule.
   *
   * @return true if the rule was present, as per
   * {@link java.util.Collection#remove(Object)}
   */ // 方法文档注释:移除规则,如果规则存在则移除并返回 true
  boolean removeRule(RelOptRule rule); // 声明移除优化规则的方法,从优化器中删除指定的规则,返回值表示是否成功移除

  /**
   * Provides the Context created when this planner was constructed.
   *
   * @return Never null; either an externally定义的 context, or a dummy
   * context that returns null for each requested interface
   */ // 方法文档注释:提供规划器构造时创建的上下文对象,永远不会返回 null
  Context getContext(); // 声明获取上下文对象的方法,返回规划器的上下文,包含配置信息和依赖服务,如果没有定义则返回空上下文

  /**
   * Sets the exclusion filter to use for this planner. Rules which match the
   * given pattern will not be fired regardless of whether or when they are
   * added to the planner.
   *
   * @param exclusionFilter pattern to match for exclusion; null to disable
   *                        filtering
   */ // 方法文档注释:设置规则排除过滤器,匹配给定模式的规则将不会被触发,参数为正则表达式模式,null 表示禁用过滤
  void setRuleDescExclusionFilter(@Nullable Pattern exclusionFilter); // 声明设置规则描述排除过滤器的方法,使用正则表达式过滤规则,匹配的规则将被排除在优化过程之外

  /**
   * Does nothing.
   *
   * @deprecated Previously, this method installed the cancellation-checking
   * flag for this planner, but is now deprecated. Now, you should add a
   * {@link CancelFlag} to the {@link Context} passed to the constructor.
   *
   * @param cancelFlag flag which the planner should periodically check
   */ // 方法文档注释:已废弃,之前用于设置取消标志,现在应该将 CancelFlag 添加到构造函数的 Context 中
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在 2.0 版本之前移除
  void setCancelFlag(CancelFlag cancelFlag); // 声明设置取消标志的方法(已废弃),不再执行任何操作,保留是为了向后兼容

  /**
   * Changes a relational expression to an equivalent one with a different set
   * of traits.
   *
   * @param rel Relational expression (may or may not have been registered; must
   *   not have the desired traits)
   * @param toTraits Trait set to convert the relational expression to
   * @return Relational expression with desired traits. Never null, but may be
   *   abstract
   */ // 方法文档注释:将关系表达式转换为具有不同特征集的等价表达式,参数 rel 是源表达式,toTraits 是目标特征集
  RelNode changeTraits(RelNode rel, RelTraitSet toTraits); // 声明改变特征的方法,将关系表达式转换为指定的特征集(如不同的约定、排序等),返回转换后的表达式

  /**
   * Negotiates an appropriate planner to deal with distributed queries. The
   * idea is that the schemas decide among themselves which has the most
   * knowledge. Right now, the local planner retains control.
   */ // 方法文档注释:协商选择合适的规划器来处理分布式查询,让各个模式决定谁最有知识,目前本地规划器保持控制
  RelOptPlanner chooseDelegate(); // 声明选择委托规划器的方法,用于分布式查询场景,选择最合适的规划器来处理查询,当前实现返回本地规划器

  /**
   * Defines a pair of relational expressions that are equivalent.
   *
   * <p>Typically {@code tableRel} is a
   * {@link org.apache.calcite.rel.logical.LogicalTableScan} representing a
   * table that is a materialized view and {@code queryRel} is the SQL
   * expression that populates that view. The intention is that
   * {@code tableRel} is cheaper to evaluate and therefore if the query being
   * optimized uses (or can be rewritten to use) {@code queryRel} as a
   * sub-expression then it can be optimized by using {@code tableRel}
   * instead.
   */ // 方法文档注释:定义一对等价的关系表达式,通常 tableRel 是物化视图的表扫描,queryRel 是填充该视图的 SQL 表达式
  void addMaterialization(RelOptMaterialization materialization); // 声明添加物化视图的方法,注册物化视图信息,优化器可以使用物化视图来加速查询

  /**
   * Returns the materializations that have been registered with the planner.
   */ // 方法文档注释:返回已注册到规划器的所有物化视图
  List<RelOptMaterialization> getMaterializations(); // 声明获取所有物化视图列表的方法,返回当前优化器中注册的所有物化视图定义

  /**
   * Defines a lattice.
   *
   * <p>The lattice may have materializations; it is not necessary to call
   * {@link #addMaterialization} for these; they are registered implicitly.
   */ // 方法文档注释:定义立方体结构,立方体可能包含物化视图,不需要显式调用 addMaterialization,它们会被隐式注册
  void addLattice(RelOptLattice lattice); // 声明添加立方体的方法,注册立方体定义,立方体是多维数据结构,用于优化星型模式查询

  /**
   * Retrieves a lattice, given its star table.
   */ // 方法文档注释:根据星型表检索立方体
  @Nullable RelOptLattice getLattice(RelOptTable table); // 声明获取立方体的方法,根据给定的星型表(事实表)返回对应的立方体定义,可能为 null

  /**
   * Finds the most efficient expression to implement this query.
   *
   * @throws CannotPlanException if cannot find a plan
   */ // 方法文档注释:查找实现此查询的最有效表达式,如果无法找到计划则抛出 CannotPlanException 异常
  RelNode findBestExp(); // 声明查找最佳表达式的方法,这是优化器的核心方法,通过应用规则和成本模型找到最优的执行计划

  /**
   * Returns the factory that creates
   * {@link org.apache.calcite.plan.RelOptCost}s.
   */ // 方法文档注释:返回创建 RelOptCost 对象的工厂
  RelOptCostFactory getCostFactory(); // 声明获取成本工厂的方法,返回用于创建成本对象的工厂,成本对象用于衡量关系表达式的执行代价

  /**
   * Computes the cost of a RelNode. In most情况下, this just dispatches to
   * {@link RelMetadataQuery#getCumulativeCost}.
   *
   * @param rel Relational expression of interest
   * @param mq Metadata query
   * @return estimated cost
   */ // 方法文档注释:计算关系表达式的成本,大多数情况下直接调用 RelMetadataQuery#getCumulativeCost 方法
  @Nullable RelOptCost getCost(RelNode rel, RelMetadataQuery mq); // 声明获取成本的方法,计算给定关系表达式的执行成本,使用元数据查询来获取准确的成本估计

  // CHECKSTYLE: IGNORE 2 // Checkstyle 忽略指令,允许下面的代码不符合某些代码风格规则
  /** @deprecated Use {@link #getCost(RelNode, RelMetadataQuery)}
   * or, better, call {@link RelMetadataQuery#getCumulativeCost(RelNode)}. */ // 已废弃,建议使用带 RelMetadataQuery 参数的方法,或直接调用 RelMetadataQuery#getCumulativeCost
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在 2.0 版本之前移除
  @Nullable RelOptCost getCost(RelNode rel); // 声明获取成本的方法(已废弃),不带元数据查询参数的版本,不再推荐使用

  /**
   * Registers a relational expression in the expression bank.
   *
   * <p>After it has been registered, you may not modify it.
   *
   * <p>The expression must not already have been registered. If you are not
   * sure whether it has been registered, call
   * {@link #ensureRegistered(RelNode, RelNode)}.
   *
   * @param rel      Relational expression to register (must not already be
   *                 registered)
   * @param equivRel Relational expression it is equivalent to (may be null)
   * @return the same expression, or an equivalent existing expression
   */ // 方法文档注释:在表达式库中注册关系表达式,注册后不能修改,表达式必须未被注册过,不确定时调用 ensureRegistered 方法
  RelNode register( // 声明注册关系表达式的方法,将关系表达式注册到优化器的表达式库中,用于跟踪和管理所有表达式
      RelNode rel, // 参数 rel:要注册的关系表达式,必须尚未被注册
      @Nullable RelNode equivRel); // 参数 equivRel:等价的关系表达式,可能为 null,用于将表达式放入同一个等价类中

  /**
   * Registers a relational expression if it is not already registered.
   *
   * <p>If {@code equivRel} is specified, {@code rel} is placed in the same
   * equivalence set. It is OK if {@code equivRel} has different traits;
   * {@code rel} will end up in a different subset of the same set.
   *
   * <p>It is OK if {@code rel} is a subset.
   *
   * @param rel      Relational expression to register
   * @param equivRel Relational expression it is equivalent to (may be null)
   * @return Registered relational expression
   */ // 方法文档注释:如果关系表达式尚未注册则注册,如果指定了 equivRel,则放入同一个等价集合中,即使特征不同也可以
  RelNode ensureRegistered(RelNode rel, @Nullable RelNode equivRel); // 声明确保注册的方法,安全地注册关系表达式,如果已注册则返回已存在的表达式

  /**
   * Determines whether a relational expression has been registered.
   *
   * @param rel expression to test
   * @return whether rel has been registered
   */ // 方法文档注释:判断关系表达式是否已被注册
  boolean isRegistered(RelNode rel); // 声明检查注册状态的方法,判断给定的关系表达式是否已经注册到优化器的表达式库中

  /**
   * Tells this planner that a schema exists. This is the schema's chance to
   * tell the planner about all of the special transformation rules.
   */ // 方法文档注释:通知规划器存在一个模式,这是模式告诉规划器所有特殊转换规则的机会
  void registerSchema(RelOptSchema schema); // 声明注册模式的方法,将模式注册到优化器,模式可以提供特定的转换规则和统计信息

  /**
   * Adds a listener to this planner.
   *
   * @param newListener new listener to be notified of events
   */ // 方法文档注释:向规划器添加监听器
  void addListener(RelOptListener newListener); // 声明添加监听器的方法,注册事件监听器,监听器可以接收优化过程中的各种事件通知

  /**
   * Gives this planner a chance to register one or more
   * {@link RelMetadataProvider}s in the chain which will be used to answer
   * metadata queries.
   *
   * <p>Planners which use their own relational expressions internally
   * to represent concepts such as equivalence classes will generally need to
   * supply corresponding metadata providers.
   *
   * @param list receives planner's custom providers, if any
   */ // 方法文档注释:给规划器机会注册一个或多个元数据提供者到链中,用于回答元数据查询,使用内部关系表达式表示等价类的规划器需要提供相应的元数据提供者
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在 2.0 版本之前移除
  void registerMetadataProviders(List<RelMetadataProvider> list); // 声明注册元数据提供者的方法(已废弃),将自定义的元数据提供者添加到元数据提供者链中

  /**
   * Gets a timestamp for a given rel's metadata. This timestamp is used by
   * {@link CachingRelMetadataProvider} to decide whether cached metadata has
   * gone stale.
   *
   * @param rel rel of interest
   * @return timestamp of last change which might affect metadata derivation
   */ // 方法文档注释:获取给定关系表达式的元数据时间戳,用于缓存元数据提供者判断缓存是否过期
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在 2.0 版本之前移除
  long getRelMetadataTimestamp(RelNode rel); // 声明获取元数据时间戳的方法(已废弃),返回关系表达式最后可能影响元数据派生的时间戳

  /**
   * Prunes a node from the planner.
   *
   * <p>When a node is pruned, the related pending rule
   * calls are cancelled, and future rules will not fire.
   * This can be used to reduce the search space.
   *
   * @param rel the node to prune.
   */ // 方法文档注释:从规划器中修剪节点,节点被修剪后,相关的待处理规则调用被取消,未来规则不会触发,可用于减少搜索空间
  void prune(RelNode rel); // 声明修剪节点的方法,从优化过程中移除指定的关系表达式,停止对该节点的进一步优化

  /**
   * Registers a class of RelNode. If this class of RelNode has been seen
   * before, does nothing.
   *
   * @param node Relational expression
   */ // 方法文档注释:注册 RelNode 类,如果该类已经见过则不做任何操作
  void registerClass(RelNode node); // 声明注册类的方法,记录关系表达式节点的类型,用于优化器了解可用的节点类型

  /**
   * Creates an empty trait set. It contains all registered traits, and the
   * default values of any traits that have them.
   *
   * <p>The empty trait set acts as the prototype (a kind of factory) for all
   * subsequently created trait sets.
   *
   * @return Empty trait set
   */ // 方法文档注释:创建空的特征集,包含所有已注册的特征和任何特征的默认值,空特征集作为原型(一种工厂)用于后续创建的所有特征集
  RelTraitSet emptyTraitSet(); // 声明创建空特征集的方法,返回包含所有已注册特征及其默认值的空特征集,作为创建新特征集的基础

  /** Sets the object that can execute scalar expressions. */ // 方法文档注释:设置可以执行标量表达式的对象
  void setExecutor(@Nullable RexExecutor executor); // 声明设置执行器的方法,配置用于执行和评估常量表达式的执行器

  /** Returns the executor used to evaluate constant expressions. */ // 方法文档注释:返回用于评估常量表达式的执行器
  @Nullable RexExecutor getExecutor(); // 声明获取执行器的方法,返回当前配置的标量表达式执行器,可能为 null

  /** Sets the decorrelator. */ // 方法文档注释:设置去相关器
  void setDecorrelator(@Nullable RelDecorrelator decorrelator); // 声明设置去相关器的方法,配置用于消除子查询相关性的去相关器对象

  /** Returns the decorrelator used to decorrelate expressions.
   *
   * @throws IllegalStateException if the decorrelator has not been set
   * */ // 方法文档注释:返回用于去相关表达式的去相关器,如果未设置则抛出 IllegalStateException 异常
  RelDecorrelator getDecorrelator(); // 声明获取去相关器的方法,返回当前配置的去相关器,如果未设置会抛出异常

  /** Called when a relational expression is copied to a similar expression. */ // 方法文档注释:当关系表达式被复制为类似表达式时调用
  void onCopy(RelNode rel, RelNode newRel); // 声明复制回调的方法,在关系表达式被复制时调用,允许优化器跟踪和管理表达式的复制操作

  // CHECKSTYLE: IGNORE 1 // Checkstyle 忽略指令,允许下面的代码不符合某些代码风格规则
  /** @deprecated Use {@link RexExecutor} */ // 已废弃,建议使用 RexExecutor 接口
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在 2.0 版本之前移除
  interface Executor extends RexExecutor { // 定义 Executor 接口(已废弃),扩展 RexExecutor 接口,用于执行标量表达式,保留是为了向后兼容
  }

  /**
   * Thrown by {@link org.apache.calcite.plan.RelOptPlanner#findBestExp()}.
   */ // 异常类文档注释:由 RelOptPlanner#findBestExp() 方法抛出
  class CannotPlanException extends RuntimeException { // 定义无法规划异常类,继承自运行时异常,表示优化器无法找到可行的执行计划
    public CannotPlanException(String message) { // 异常类构造方法,接收错误消息字符串
      super(message); // 调用父类 RuntimeException 的构造方法,传入错误消息
    }
  }
} // 接口定义结束,RelOptPlanner 接口定义了 Query Optimizer 的所有核心方法,包括规则管理、成本计算、表达式注册、物化视图等
