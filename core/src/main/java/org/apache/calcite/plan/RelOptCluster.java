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
package org.apache.calcite.plan; // 声明包名，该类属于org.apache.calcite.plan包，即Calcite的查询优化器计划包

import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.core.CorrelationId; // 导入CorrelationId类，用于标识关联变量，在子查询去相关化时使用
import org.apache.calcite.rel.hint.HintStrategyTable; // 导入HintStrategyTable类，用于存储和查询提示策略，提示是用户提供的优化建议
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider; // 导入默认的元数据提供者，提供关系表达式元数据的默认实现
import org.apache.calcite.rel.metadata.JaninoRelMetadataProvider; // 导入Janino元数据提供者，使用Janino编译器动态生成元数据查询代码以提高性能
import org.apache.calcite.rel.metadata.MetadataFactory; // 导入元数据工厂接口，用于创建元数据提供者（已废弃）
import org.apache.calcite.rel.metadata.RelMetadataProvider; // 导入元数据提供者接口，用于提供关系表达式的元数据信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入元数据查询类，用于查询关系表达式的各种元数据（如行数、大小等）
import org.apache.calcite.rel.metadata.RelMetadataQueryBase; // 导入元数据查询基类，提供线程本地存储元数据提供者的功能
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂，用于创建和管理SQL数据类型
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式（RexNode），即Calcite中的表达式树
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式节点，是Calcite中所有表达式的基类

import org.checkerframework.checker.initialization.qual.UnknownInitialization; // 导入CheckerFramework注解，表示对象可能处于未初始化状态
import org.checkerframework.checker.nullness.qual.EnsuresNonNull; // 导入CheckerFramework注解，表示方法执行后确保指定字段非空
import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解，表示字段或返回值可以为null

import java.util.HashMap; // 导入HashMap类，用于存储键值对映射
import java.util.Map; // 导入Map接口，表示映射关系
import java.util.concurrent.atomic.AtomicInteger; // 导入AtomicInteger类，用于线程安全的整数计数器
import java.util.function.Supplier; // 导入Supplier函数式接口，用于提供值的供应者

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入静态方法，用于将可能为null的值转换为非null类型

import static java.util.Objects.requireNonNull; // 导入静态方法，用于检查对象是否为null，如果为null则抛出NullPointerException

/**
 * An environment for related relational expressions during the
 * optimization of a query.
 * 在查询优化过程中，为相关的关系表达式提供环境上下文
 * 
 * RelOptCluster是Calcite查询优化器的核心环境类，它封装了在查询优化过程中所有关系表达式共享的上下文信息
 * 
 * 核心作用：
 * 1. 作为关系表达式（RelNode）的容器和上下文环境，所有属于同一个查询的关系表达式都共享同一个Cluster
 * 2. 提供类型工厂（RelDataTypeFactory），用于创建和管理SQL数据类型
 * 3. 提供表达式构建器（RexBuilder），用于构建行表达式（RexNode）
 * 4. 提供优化器（RelOptPlanner），负责执行优化规则和生成最优执行计划
 * 5. 管理元数据提供者（RelMetadataProvider），用于查询关系表达式的元数据（如行数、大小等）
 * 6. 管理关联变量（CorrelationId），用于子查询去相关化
 * 7. 管理特性集合（RelTraitSet），定义关系表达式的物理特性（如排序、分布等）
 * 8. 管理提示策略（HintStrategyTable），用于处理用户提供的优化提示
 * 
 * 设计模式：
 * - 工厂模式：提供create()方法创建Cluster实例
 * - 单例模式：同一个查询的所有关系表达式共享同一个Cluster
 * - 上下文模式：作为优化过程的上下文对象，封装所有共享资源
 * 
 * 使用场景：
 * - SQL解析后，将SQL转换为关系表达式树时，需要创建Cluster
 * - 在优化过程中，所有RelNode都持有对Cluster的引用
 * - 在创建新的关系表达式时，需要传入Cluster以共享上下文
 */
public class RelOptCluster { // 定义RelOptCluster类，关系优化集群类
  //~ Instance fields -------------------------------------------------------- // 成员变量部分开始

  private final RelDataTypeFactory typeFactory; // 类型工厂，用于创建和管理SQL数据类型（如VARCHAR、INTEGER等），final表示初始化后不可修改
  private final RelOptPlanner planner; // 优化器，负责执行优化规则和生成最优执行计划，final表示初始化后不可修改
  private final AtomicInteger nextCorrel; // 原子整数计数器，用于生成唯一的关联变量ID，确保线程安全，final表示初始化后不可修改
  private final Map<String, RelNode> mapCorrelToRel; // 映射表，将关联变量名称映射到对应的关系表达式节点，用于子查询去相关化，final表示初始化后不可修改
  private RexNode originalExpression; // 原始表达式，存储查询的原始表达式（已废弃，保留用于兼容性）
  private final RexBuilder rexBuilder; // 行表达式构建器，用于构建和操作表达式树（如条件、计算等），final表示初始化后不可修改
  private RelMetadataProvider metadataProvider; // 元数据提供者，用于提供关系表达式的元数据信息（如行数、大小、唯一性等）
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  private MetadataFactory metadataFactory; // 元数据工厂，用于创建元数据提供者（已废弃，被RelMetadataProvider取代）
  private @Nullable HintStrategyTable hintStrategies; // 提示策略表，用于存储和处理用户提供的优化提示，@Nullable表示可以为null
  private final RelTraitSet emptyTraitSet; // 空特性集合，表示不包含任何特性的特性集合，作为创建新特性集合的基础，final表示初始化后不可修改
  private @Nullable RelMetadataQuery mq; // 元数据查询对象，用于查询关系表达式的元数据，@Nullable表示可以为null，使用延迟初始化
  private Supplier<RelMetadataQuery> mqSupplier; // 元数据查询供应者，用于提供新的RelMetadataQuery实例，支持在每次规则调用时重新生成元数据查询对象

  //~ Constructors ----------------------------------------------------------- // 构造方法部分开始

  /**
   * Creates a cluster.
   * 创建一个RelOptCluster实例（已废弃的构造方法）
   * 
   * @deprecated 使用新的create()方法代替，该方法将在2.0版本前移除
   * 该构造方法已被废弃，因为它依赖于RelOptQuery类，而RelOptQuery类也被废弃
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  RelOptCluster( // 定义构造方法，包访问权限
      RelOptQuery query, // 查询对象（已废弃），包含关联变量计数器和映射表
      RelOptPlanner planner, // 优化器，负责执行优化规则
      RelDataTypeFactory typeFactory, // 类型工厂，用于创建SQL数据类型
      RexBuilder rexBuilder) { // 表达式构建器，用于构建表达式树
    this(planner, typeFactory, rexBuilder, query.nextCorrel, // 调用主构造方法，传入query中的关联变量计数器
        query.mapCorrelToRel); // 传入query中的关联变量映射表
  }

  /**
   * Creates a cluster.
   * 创建一个RelOptCluster实例（主构造方法）
   * 
   * <p>For use only from {@link #create} and {@link RelOptQuery}.
   * 该构造方法仅供create()静态方法和RelOptQuery类使用，外部不应直接调用
   * 
   * 构造方法初始化了Cluster的所有核心组件：
   * 1. 设置关联变量计数器和映射表
   * 2. 设置优化器、类型工厂和表达式构建器
   * 3. 初始化原始表达式
   * 4. 设置默认的元数据提供者
   * 5. 设置元数据查询供应者
   * 6. 初始化空特性集合
   */
  RelOptCluster(RelOptPlanner planner, RelDataTypeFactory typeFactory, // 定义主构造方法，包访问权限
      RexBuilder rexBuilder, AtomicInteger nextCorrel, // 接收优化器、类型工厂、表达式构建器和关联变量计数器
      Map<String, RelNode> mapCorrelToRel) { // 接收关联变量映射表
    this.nextCorrel = nextCorrel; // 保存关联变量计数器引用，用于生成唯一的关联变量ID
    this.mapCorrelToRel = mapCorrelToRel; // 保存关联变量映射表引用，用于关联变量名称到关系表达式的映射
    this.planner = requireNonNull(planner, "planner"); // 保存优化器引用，使用requireNonNull确保planner不为null
    this.typeFactory = requireNonNull(typeFactory, "typeFactory"); // 保存类型工厂引用，使用requireNonNull确保typeFactory不为null
    this.rexBuilder = rexBuilder; // 保存表达式构建器引用
    this.originalExpression = rexBuilder.makeLiteral("?"); // 初始化原始表达式为一个字面量"?"，用于向后兼容

    // set up a default rel metadata provider,
    // 设置默认的关系元数据提供者
    // giving the planner first crack at everything
    // 让优化器优先处理所有元数据请求
    setMetadataProvider(DefaultRelMetadataProvider.INSTANCE); // 设置默认的元数据提供者，使用单例模式
    setMetadataQuerySupplier(RelMetadataQuery::instance); // 设置元数据查询供应者，使用RelMetadataQuery的静态方法
    this.emptyTraitSet = planner.emptyTraitSet(); // 从优化器获取空特性集合，包含优化器支持的所有特性定义
    assert emptyTraitSet.size() == planner.getRelTraitDefs().size(); // 断言空特性集合的大小等于优化器定义的特性数量，用于调试
  }

  /** Creates a cluster.
   * 创建一个RelOptCluster实例（公共静态工厂方法）
   * 
   * 这是创建Cluster实例的推荐方式，它会初始化所有必要的组件
   * 
   * @param planner 优化器，负责执行优化规则
   * @param rexBuilder 表达式构建器，用于构建表达式树
   * @return 新创建的RelOptCluster实例
   */
  public static RelOptCluster create(RelOptPlanner planner, // 定义公共静态工厂方法
      RexBuilder rexBuilder) { // 接收优化器和表达式构建器参数
    return new RelOptCluster(planner, rexBuilder.getTypeFactory(), // 创建新的Cluster实例，从rexBuilder获取类型工厂
        rexBuilder, new AtomicInteger(0), new HashMap<>()); // 初始化关联变量计数器为0，创建空的关联变量映射表
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分开始

  /**
   * 获取查询对象（已废弃）
   * 
   * @deprecated 该方法已废弃，将在2.0版本前移除
   * @return 创建的RelOptQuery对象，包含优化器和关联变量信息
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public RelOptQuery getQuery() { // 定义公共方法，获取查询对象
    return new RelOptQuery(castNonNull(planner), nextCorrel, mapCorrelToRel); // 创建新的RelOptQuery对象，传入优化器、计数器和映射表
  }

  /**
   * 获取原始表达式（已废弃）
   * 
   * @deprecated 该方法已废弃，将在2.0版本前移除
   * @return 原始表达式节点
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public RexNode getOriginalExpression() { // 定义公共方法，获取原始表达式
    return originalExpression; // 返回原始表达式节点
  }

  /**
   * 设置原始表达式（已废弃）
   * 
   * @deprecated 该方法已废弃，将在2.0版本前移除
   * @param originalExpression 要设置的原始表达式节点
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public void setOriginalExpression(RexNode originalExpression) { // 定义公共方法，设置原始表达式
    this.originalExpression = originalExpression; // 保存原始表达式
  }

  /**
   * 获取优化器
   * 
   * @return 关系优化器实例，用于执行优化规则
   */
  public RelOptPlanner getPlanner() { // 定义公共方法，获取优化器
    return planner; // 返回优化器引用
  }

  /**
   * 获取类型工厂
   * 
   * @return 关系数据类型工厂实例，用于创建SQL数据类型
   */
  public RelDataTypeFactory getTypeFactory() { // 定义公共方法，获取类型工厂
    return typeFactory; // 返回类型工厂引用
  }

  /**
   * 获取表达式构建器
   * 
   * @return RexBuilder实例，用于构建行表达式
   */
  public RexBuilder getRexBuilder() { // 定义公共方法，获取表达式构建器
    return rexBuilder; // 返回表达式构建器引用
  }

  /**
   * 获取元数据提供者
   * 
   * @return 元数据提供者实例，可能为null
   */
  public @Nullable RelMetadataProvider getMetadataProvider() { // 定义公共方法，获取元数据提供者
    return metadataProvider; // 返回元数据提供者引用
  }

  /**
   * Overrides the default metadata provider for this cluster.
   * 覆盖此集群的默认元数据提供者
   * 
   * 该方法允许用户自定义元数据提供者，用于提供特定的元数据实现
   * 
   * 执行步骤：
   * 1. 保存自定义的元数据提供者
   * 2. 创建MetadataFactoryImpl实例包装元数据提供者（已废弃）
   * 3. 将元数据提供者包装为JaninoRelMetadataProvider并设置到线程本地存储
   *    JaninoRelMetadataProvider使用Janino编译器动态生成代码，提高元数据查询性能
   * 4. 设置到RelMetadataQueryBase的ThreadLocal中，供RelMetadataQuery使用
   * 
   * @param metadataProvider 自定义的元数据提供者
   */
  @EnsuresNonNull({"this.metadataProvider", "this.metadataFactory"}) // 确保方法执行后metadataProvider和metadataFactory非空
  @SuppressWarnings("deprecation") // 抑制废弃警告
  public void setMetadataProvider( // 定义公共方法，设置元数据提供者
      @UnknownInitialization RelOptCluster this, // 表示对象可能处于未初始化状态
      RelMetadataProvider metadataProvider) { // 接收元数据提供者参数
    this.metadataProvider = metadataProvider; // 保存元数据提供者引用
    this.metadataFactory = // 创建元数据工厂实例（已废弃）
        new org.apache.calcite.rel.metadata.MetadataFactoryImpl(metadataProvider); // 包装元数据提供者
    // Wrap the metadata provider as a JaninoRelMetadataProvider
    // 将元数据提供者包装为JaninoRelMetadataProvider
    // and set it to the ThreadLocal,
    // 并设置到线程本地存储
    // JaninoRelMetadataProvider is required by the RelMetadataQuery.
    // JaninoRelMetadataProvider是RelMetadataQuery所需的
    RelMetadataQueryBase.THREAD_PROVIDERS // 访问RelMetadataQueryBase的ThreadLocal提供者
        .set(JaninoRelMetadataProvider.of(metadataProvider)); // 将元数据提供者包装为JaninoRelMetadataProvider并设置到ThreadLocal
  }

  /**
   * Returns a {@link MetadataFactory}.
   * 返回元数据工厂
   * 
   * @deprecated 该方法已废弃，请使用getMetadataQuery()代替
   * @return 元数据工厂实例
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public MetadataFactory getMetadataFactory() { // 定义公共方法，获取元数据工厂
    return metadataFactory; // 返回元数据工厂引用
  }

  /**
   * Sets up the customized {@link RelMetadataQuery} instance supplier that to
   * use during rule planning.
   * 设置自定义的RelMetadataQuery实例供应者，用于规则规划期间
   * 
   * 该方法允许用户自定义元数据查询对象的创建方式
   * 
   * 工作原理：
   * - 在优化过程中，每次调用getMetadataQuery()时，会通过mqSupplier获取新的RelMetadataQuery实例
   * - RelMetadataQuery实例会被缓存在Cluster中，直到调用invalidateMetadataQuery()使其失效
   * - 在每次RelOptRuleCall周期中，可能会使元数据查询失效并重新生成，以确保元数据的准确性
   * 
   * <p>Note that the {@code mqSupplier} should return
   * a fresh new {@link RelMetadataQuery} instance because the instance would be
   * cached in this cluster, and we may invalidate and re-generate it
   * for each {@link RelOptRuleCall} cycle.
   * 注意：mqSupplier应该返回一个新的RelMetadataQuery实例，因为实例会被缓存在集群中，
   * 我们可能会在每个RelOptRuleCall周期中使其失效并重新生成
   * 
   * @param mqSupplier RelMetadataQuery实例的供应者
   */
  @EnsuresNonNull("this.mqSupplier") // 确保方法执行后mqSupplier非空
  public void setMetadataQuerySupplier( // 定义公共方法，设置元数据查询供应者
      @UnknownInitialization RelOptCluster this, // 表示对象可能处于未初始化状态
      Supplier<RelMetadataQuery> mqSupplier) { // 接收RelMetadataQuery供应者参数
    this.mqSupplier = mqSupplier; // 保存元数据查询供应者引用
  }

  /**
   * Returns the current RelMetadataQuery.
   * 返回当前的RelMetadataQuery实例
   * 
   * 该方法使用延迟初始化模式，只有在第一次调用时才会通过mqSupplier创建RelMetadataQuery实例
   * 
   * <p>This method might be changed or moved in future.
   * 该方法在未来可能会被修改或移动
   * If you have a {@link RelOptRuleCall} available,
   * 如果你有RelOptRuleCall可用，
   * for example if you are in a {@link RelOptRule#onMatch(RelOptRuleCall)}
   * 例如如果你在RelOptRule#onMatch(RelOptRuleCall)方法中
   * method, then use {@link RelOptRuleCall#getMetadataQuery()} instead. */
   * 那么请使用RelOptRuleCall#getMetadataQuery()代替
   * 
   * @return RelMetadataQuery实例，用于查询关系表达式的元数据
   */
  public RelMetadataQuery getMetadataQuery() { // 定义公共方法，获取元数据查询对象
    if (mq == null) { // 如果元数据查询对象为null（延迟初始化）
      mq = castNonNull(mqSupplier).get(); // 通过供应者获取新的RelMetadataQuery实例并保存
    }
    return mq; // 返回元数据查询对象
  }

  /**
   * Returns the supplier of RelMetadataQuery.
   * 返回RelMetadataQuery的供应者
   * 
   * @return RelMetadataQuery实例的供应者
   */
  public Supplier<RelMetadataQuery> getMetadataQuerySupplier() { // 定义公共方法，获取元数据查询供应者
    return this.mqSupplier; // 返回元数据查询供应者引用
  }

  /**
   * Should be called whenever the current {@link RelMetadataQuery} becomes
   * invalid. Typically invoked from {@link RelOptRuleCall#transformTo}.
   * 当当前的RelMetadataQuery失效时应该调用此方法
   * 通常从RelOptRuleCall#transformTo调用
   * 
   * 该方法会使缓存的RelMetadataQuery实例失效，下次调用getMetadataQuery()时会重新创建
   * 
   * 使用场景：
   * - 当关系表达式树发生变化时，元数据可能不再准确
   * - 在优化规则应用后，需要重新计算元数据
   * - 在RelOptRuleCall#transformTo中，转换关系表达式后需要使元数据失效
   */
  public void invalidateMetadataQuery() { // 定义公共方法，使元数据查询失效
    mq = null; // 将元数据查询对象设置为null，下次调用getMetadataQuery()时会重新创建
  }

  /**
   * Sets up the hint propagation strategies to be used during rule planning.
   * 设置在规则规划期间使用的提示传播策略
   * 
   * 提示（Hint）是用户提供的优化建议，可以影响优化器的决策
   * HintStrategyTable定义了如何处理和传播这些提示
   * 
   * <p>Use <code>RelOptNode.getCluster().getHintStrategies()</code> to fetch
   * the hint strategies.
   * 使用RelOptNode.getCluster().getHintStrategies()获取提示策略
   * 
   * <p>Note that this method is only for internal use; the cluster {@code hintStrategies}
   * would be always set up with the instance configured by
   * {@link org.apache.calcite.sql2rel.SqlToRelConverter.Config}.
   * 注意：此方法仅供内部使用；集群的hintStrategies将始终使用SqlToRelConverter.Config配置的实例设置
   * 
   * @param hintStrategies 指定的提示策略，用于覆盖默认策略（空策略）
   */
  public void setHintStrategies(HintStrategyTable hintStrategies) { // 定义公共方法，设置提示策略表
    requireNonNull(hintStrategies, "hintStrategies"); // 检查hintStrategies是否为null，如果为null则抛出异常
    this.hintStrategies = hintStrategies; // 保存提示策略表引用
  }

  /**
   * Returns the hint strategies of this cluster. It is immutable during the whole planning phrase.
   * 返回此集群的提示策略表。它在整个规划阶段是不可变的
   * 
   * @return 提示策略表，如果未设置则返回空策略表
   */
  public HintStrategyTable getHintStrategies() { // 定义公共方法，获取提示策略表
    if (this.hintStrategies == null) { // 如果提示策略表为null（延迟初始化）
      this.hintStrategies = HintStrategyTable.EMPTY; // 设置为空策略表
    }
    return this.hintStrategies; // 返回提示策略表
  }

  /**
   * Constructs a new id for a correlating variable. It is unique within the
   * whole query.
   * 为关联变量构造一个新的ID。它在整个查询中是唯一的
   * 
   * 关联变量用于子查询去相关化，将子查询转换为连接操作
   * 每个关联变量都有一个唯一的ID，用于标识和引用
   * 
   * @return 新创建的关联变量ID
   */
  public CorrelationId createCorrel() { // 定义公共方法，创建关联变量ID
    return new CorrelationId(nextCorrel.getAndIncrement()); // 创建新的CorrelationId，原子性地递增计数器
  }

  /** Returns the default trait set for this cluster.
   * 返回此集群的默认特性集合
   * 
   * 特性集合（RelTraitSet）定义了关系表达式的物理特性，如：
   * - 排序特性（RelCollation）
   * - 分布特性（RelDistribution）
   * - 约定特性（RelConvention，如物理约定、逻辑约定等）
   * 
   * 默认特性集合是空的，作为创建新特性集合的基础
   * 
   * @return 空的特性集合
   */
  public RelTraitSet traitSet() { // 定义公共方法，获取默认特性集合
    return emptyTraitSet; // 返回空特性集合
  }

  // CHECKSTYLE: IGNORE 2 // 告诉Checkstyle忽略此处的检查
  /** @deprecated For {@code traitSetOf(t1, t2)},
   * 已废弃：对于traitSetOf(t1, t2)，
   * use {@link #traitSet}().replace(t1).replace(t2). */
   * 请使用traitSet().replace(t1).replace(t2)代替
   * 
   * 该方法已废弃，因为新的API更清晰和灵活
   * 
   * @param traits 要添加到特性集合中的特性
   * @return 包含指定特性的特性集合
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public RelTraitSet traitSetOf(RelTrait... traits) { // 定义公共方法，创建包含多个特性的特性集合
    RelTraitSet traitSet = emptyTraitSet; // 从空特性集合开始
    for (RelTrait trait : traits) { // 遍历所有特性
      traitSet = traitSet.replace(trait); // 将每个特性替换到特性集合中
    }
    return traitSet; // 返回包含所有特性的特性集合
  }

  /**
   * 创建包含单个特性的特性集合
   * 
   * 这是创建特性集合的推荐方式，比traitSetOf(RelTrait... traits)更清晰
   * 
   * @param trait 要添加到特性集合中的特性
   * @return 包含指定特性的特性集合
   */
  public RelTraitSet traitSetOf(RelTrait trait) { // 定义公共方法，创建包含单个特性的特性集合
    return emptyTraitSet.replace(trait); // 将特性替换到空特性集合中并返回
  }
} // 类定义结束
