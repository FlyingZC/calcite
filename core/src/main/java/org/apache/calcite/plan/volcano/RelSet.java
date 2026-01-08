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
package org.apache.calcite.plan.volcano;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptListener;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTrait;
import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.Converter;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.Spool;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.trace.CalciteTrace;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.calcite.linq4j.Nullness.castNonNull;

import static java.util.Objects.requireNonNull;

/**
 * RelSet是VolcanoPlanner中的等价集合类,用于管理具有相同语义的关系表达式集合
 * 
 * <p>核心概念:
 * 1. 等价集合:包含所有语义相同的RelNode,它们产生相同的输出结果
 * 2. 成本优化:在等价集合中,我们总是选择成本最低的表达式作为最佳实现
 * 3. 调用约定:集合中的所有表达式必须具有相同的调用约定(调用约定决定了物理实现方式)
 * 
 * <p>在VolcanoPlanner中的作用:
 * - 作为优化过程中的核心数据结构,用于识别和合并等价的物理计划
 * - 管理多个RelSubset,每个RelSubset代表一组具有相同trait集的等价表达式
 * - 支持规则匹配和成本传播,实现动态规划优化算法
 * - 维护父子关系,支持自底向上的优化过程
 * 
 * <p>典型使用场景:
 * - 当发现两个RelNode等价时,它们会被放入同一个RelSet
 * - 优化器会在RelSet内应用转换规则,生成新的等价表达式
 * - 通过成本比较,选择最优的物理实现
 * 
 * <p>重要特性:
 * - 所有表达式具有相同的调用约定
 * - 通过RelSubset组织不同trait集的表达式
 * - 支持集合合并,当发现两个集合等价时会进行合并
 * - 维护变量传播和使用信息,支持相关子查询优化
 * 
 * <p>All of the expressions in an <code>RelSet</code> have the same calling
 * convention.
 */
class RelSet {
  //~ Static fields/initializers ---------------------------------------------

  private static final Logger LOGGER = CalciteTrace.getPlannerTracer(); // 日志记录器,用于记录优化器运行过程中的调试信息和跟踪信息

  //~ Instance fields --------------------------------------------------------

  final List<RelNode> rels = new ArrayList<>(); // 存储该RelSet中所有等价的关系表达式(RelNode)列表,这些表达式语义相同但可能具有不同的trait集和成本
  /**
   * Relational expressions that have a subset in this set as a child. This
   * is a multi-set. If multiple relational expressions in this set have the
   * same parent, there will be multiple entries.
   * 
   * 中文说明:存储所有以该RelSet的某个RelSubset为子节点的父RelNode列表
   * 这是一个多重集合,如果该集合中的多个表达式有相同的父节点,会有多个条目
   * 用于维护父子关系,支持自底向上的成本传播和规则触发
   */
  final List<RelNode> parents = new ArrayList<>();
  final List<RelSubset> subsets = new ArrayList<>(); // 存储该RelSet中所有的RelSubset列表,每个RelSubset代表一组具有相同trait集的等价表达式,支持不同物理实现的比较和选择

  /**
   * Set to the superseding set when this is found to be equivalent to another
   * set.
   * 
   * 中文说明:当发现该RelSet与另一个RelSet等价时,指向合并后的新RelSet
   * @MonotonicNonNull注解表示该字段只会被设置一次(单调非空),用于标记已废弃的集合
   * 合并后,该RelSet的所有引用会重定向到equivalentSet,实现集合的合并和消亡
   */
  @MonotonicNonNull RelSet equivalentSet;
  @MonotonicNonNull RelNode rel; // 该RelSet中第一个加入的RelNode,用于类型验证和基准比较,确保后续加入的表达式类型一致

  /**
   * Exploring state of current RelSet.
   * 
   * 中文说明:当前RelSet的探索状态,用于控制优化过程
   * EXPLORING:正在探索中,所有可能的规则匹配已调度但未完全应用
   * EXPLORED:已完全探索,可以提供有效的下界成本
   * 防止重复探索,确保优化过程的收敛性
   */
  @Nullable ExploringState exploringState;

  /**
   * Records conversions / enforcements that have happened on the
   * pair of derived and required traitset.
   * 
   * 中文说明:记录已执行的转换和强制约束操作
   * 存储从源trait集到目标trait集的转换对,避免重复创建相同的转换器
   * 每个Pair<RelTraitSet, RelTraitSet>代表一个已注册的转换关系
   * 用于trait转换的优化和去重
   */
  final Set<Pair<RelTraitSet, RelTraitSet>> conversions = new HashSet<>();

  /**
   * Variables that are set by relational expressions in this set
   * and available for use by parent and child expressions.
   * 
   * 中文说明:该RelSet中的表达式设置的关联变量,可用于父表达式和子表达式
   * 用于支持相关子查询和关联变量的传播
   * 确保变量的正确作用域和可见性
   */
  final Set<CorrelationId> variablesPropagated;

  /**
   * Variables that are used by relational expressions in this set.
   * 
   * 中文说明:该RelSet中的表达式使用的关联变量集合
   * 用于跟踪变量的依赖关系,支持相关子查询的优化
   * 确保变量在使用前已被正确设置
   */
  final Set<CorrelationId> variablesUsed;
  final int id; // RelSet的唯一标识符,用于日志记录、调试和集合引用管理

  /**
   * Reentrancy flag.
   * 
   * 中文说明:重入标志,用于防止元数据查询时的无限递归
   * 当为true时,表示正在进行元数据查询,避免重复触发相同的查询
   * 保护优化器在获取元数据信息时的稳定性
   */
  boolean inMetadataQuery;

  //~ Constructors -----------------------------------------------------------

  RelSet( // RelSet构造函数,初始化一个等价集合
      int id, // 集合的唯一标识符
      Set<CorrelationId> variablesPropagated, // 该集合传播的关联变量集合
      Set<CorrelationId> variablesUsed) { // 该集合使用的关联变量集合
    this.id = id; // 设置集合ID
    this.variablesPropagated = variablesPropagated; // 设置传播变量
    this.variablesUsed = variablesUsed; // 设置使用变量
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * Returns all of the {@link RelNode}s which reference {@link RelNode}s in
   * this set.
   * 
   * 中文说明:返回所有引用该RelSet中RelNode的父RelNode列表
   * 用于维护父子关系,支持成本传播和规则触发
   * @return 父RelNode列表
   */
  public List<RelNode> getParentRels() {
    return parents; // 返回父节点列表
  }

  /**
   * Returns the child RelSet for the current set.
   * 
   * 中文说明:返回当前RelSet的所有子RelSet集合
   * 遍历该集合中的所有RelNode,收集它们的输入所属的RelSet
   * 跳过Converter节点,因为它们只用于trait转换,不改变数据流
   * @param planner VolcanoPlanner实例,用于获取等价集合信息
   * @return 子RelSet集合
   */
  public Set<RelSet> getChildSets(VolcanoPlanner planner) {
    Set<RelSet> childSets = new HashSet<>(); // 创建子集合集合
    for (RelNode node : this.rels) { // 遍历该集合中的所有RelNode
      if (node instanceof Converter) { // 如果是转换器节点,跳过
        continue;
      }
      for (RelNode child : node.getInputs()) { // 遍历该节点的所有输入
        RelSet childSet = // 获取子节点的等价集合
            VolcanoPlanner.equivRoot(((RelSubset) child).getSet());
        if (childSet.id != this.id) { // 如果不是当前集合,添加到子集合
          childSets.add(childSet);
        }
      }
    }
    return childSets; // 返回子集合集合
  }

  /**
   * Returns all of the {@link RelNode}s contained by any subset of this set
   * (does not include the subset objects themselves).
   * 
   * 中文说明:返回该RelSet中所有RelSubset包含的所有RelNode列表
   * 不包括RelSubset对象本身,只返回实际的关系表达式
   * @return 所有关系表达式列表
   */
  public List<RelNode> getRelsFromAllSubsets() {
    return rels; // 返回所有关系表达式列表
  }

  public @Nullable RelSubset getSubset(RelTraitSet traits) { // 根据trait集获取对应的RelSubset
    for (RelSubset subset : subsets) { // 遍历所有子集
      if (subset.getTraitSet().equals(traits)) { // 如果trait集匹配
        return subset; // 返回该子集
      }
    }
    return null; // 未找到匹配的子集,返回null
  }

  /**
   * Removes all references to a specific {@link RelNode} in both the subsets
   * and their parent relationships.
   * 
   * 中文说明:移除特定RelNode的所有引用
   * 从父节点列表中移除该RelNode,清理引用关系
   * 通常在RelNode被剪枝或废弃时调用
   * @param rel 要移除的关系表达式
   */
  void obliterateRelNode(RelNode rel) {
    parents.remove(rel); // 从父节点列表中移除该RelNode
  }

  /**
   * Adds a relational expression to a set, with its results available under a
   * particular calling convention. An expression may be in the set several
   * times with different calling conventions (and hence different costs).
   * 
   * 中文说明:向RelSet中添加一个关系表达式,使其结果在特定的调用约定下可用
   * 一个表达式可以以不同的调用约定(因此有不同的成本)多次添加到集合中
   * 该方法会创建或获取对应的RelSubset,并将表达式添加到其中
   * @param rel 要添加的关系表达式
   * @return 添加表达式后的RelSubset
   */
  public RelSubset add(RelNode rel) {
    assert equivalentSet == null : "adding to a dead set"; // 断言集合未被废弃
    final RelTraitSet traitSet = rel.getTraitSet().simplify(); // 简化trait集
    final RelSubset subset = // 获取或创建对应的RelSubset
        getOrCreateSubset(rel.getCluster(), traitSet, rel.isEnforcer());
    subset.add(rel); // 将表达式添加到子集中
    return subset; // 返回子集
  }

  /**
   * If the subset is required, convert delivered subsets to this subset.
   * Otherwise, convert this subset to required subsets in this RelSet.
   * The subset can be both required and delivered.
   * 
   * 中文说明:添加转换器以实现trait集之间的转换
   * 如果subset是required的,则将delivered的子集转换为该subset
   * 否则,将该subset转换为该RelSet中的required子集
   * subset可以同时是required和delivered的
   * 该方法会分析trait差异,创建必要的转换器,并注册到优化器中
   * @param subset 目标RelSubset
   * @param required 是否为required子集
   * @param useAbstractConverter 是否使用抽象转换器
   */
  void addConverters(RelSubset subset, boolean required,
      boolean useAbstractConverter) {
    RelOptCluster cluster = subset.getCluster(); // 获取集群信息
    List<RelSubset> others = // 根据required标志筛选目标子集
        subsets.stream()
            .filter(n -> required ? n.isDelivered() : n.isRequired())
            .collect(Collectors.toList());

    for (RelSubset other : others) { // 遍历目标子集
      assert other.getTraitSet().size() == subset.getTraitSet().size(); // 断言trait集大小相同
      RelSubset from = subset; // 源子集
      RelSubset to = other; // 目标子集

      if (required) { // 如果是required,反转源和目标
        from = other;
        to = subset;
      }

      if (from == to // 如果源和目标相同,跳过
          || to.isEnforceDisabled() // 如果目标禁用了强制约束,跳过
          || useAbstractConverter // 如果使用抽象转换器且不适用,跳过
              && from.getConvention() != null
              && !from.getConvention().useAbstractConvertersForConversion(
                  from.getTraitSet(), to.getTraitSet())) {
        continue;
      }

      if (!conversions.add(Pair.of(from.getTraitSet(), to.getTraitSet()))) { // 如果转换已存在,跳过
        continue;
      }

      final ImmutableList<RelTrait> difference = // 计算trait差异
          to.getTraitSet().difference(from.getTraitSet());

      boolean needsConverter = false; // 是否需要转换器

      for (RelTrait fromTrait : difference) { // 遍历差异trait
        RelTraitDef traitDef = fromTrait.getTraitDef(); // 获取trait定义
        RelTrait toTrait = to.getTraitSet().getTrait(traitDef); // 获取目标trait

        if (toTrait == null || !traitDef.canConvert( // 如果不能转换,不需要转换器
            cluster.getPlanner(), fromTrait, toTrait)) {
          needsConverter = false;
          break;
        }

        if (!fromTrait.satisfies(toTrait)) { // 如果源trait不满足目标trait,需要转换器
          needsConverter = true;
        }
      }

      if (needsConverter) { // 如果需要转换器
        final RelNode enforcer; // 强制约束节点
        if (useAbstractConverter) { // 使用抽象转换器
          enforcer = new AbstractConverter(cluster, from, null, to.getTraitSet());
        } else { // 使用约定特定转换器
          Convention convention = // 获取调用约定
              requireNonNull(subset.getConvention(),
                  () -> "convention is null for " + subset);
          enforcer = convention.enforce(from, to.getTraitSet()); // 创建转换器
        }

        if (enforcer != null) { // 如果转换器创建成功
          cluster.getPlanner().register(enforcer, to); // 注册到优化器
        }
      }
    }
  }

  RelSubset getOrCreateSubset( // 获取或创建具有指定trait集的RelSubset
      RelOptCluster cluster, RelTraitSet traits, boolean required) {
    boolean needsConverter = false; // 是否需要添加转换器
    final VolcanoPlanner planner = (VolcanoPlanner) cluster.getPlanner(); // 获取优化器
    RelSubset subset = getSubset(traits); // 尝试获取现有子集

    if (subset == null) { // 如果子集不存在
      needsConverter = true; // 需要添加转换器
      subset = new RelSubset(cluster, this, traits); // 创建新的RelSubset

      // 需要先添加子集再添加抽象转换器(用于others->subset),
      // 否则在register()期间优化器会尝试再次添加该子集
      subsets.add(subset); // 将子集添加到集合中

      if (planner.getListener() != null) { // 如果有监听器
        postEquivalenceEvent(planner, subset); // 发布等价事件
      }
    } else if ((required && !subset.isRequired()) // 如果子集存在但状态不匹配
        || (!required && !subset.isDelivered())) {
      needsConverter = true; // 需要添加转换器
    }

    if (subset.getConvention() == Convention.NONE) { // 如果调用约定为NONE
      needsConverter = false; // 不需要转换器
    } else if (required) { // 如果是required
      subset.setRequired(); // 设置为required状态
    } else { // 否则
      subset.setDelivered(); // 设置为delivered状态
    }

    if (needsConverter) { // 如果需要转换器
      addConverters(subset, required, !planner.topDownOpt); // 添加转换器
    }

    return subset; // 返回子集
  }

  private void postEquivalenceEvent(VolcanoPlanner planner, RelNode rel) { // 发布等价事件到监听器
    RelOptListener listener = planner.getListener(); // 获取监听器
    if (listener == null) { // 如果没有监听器,直接返回
      return;
    }
    RelOptListener.RelEquivalenceEvent event = // 创建等价事件
        new RelOptListener.RelEquivalenceEvent(
            planner, // 优化器
            rel, // 关系表达式
            "equivalence class " + id, // 等价类描述
            false); // 是否为强制约束
    listener.relEquivalenceFound(event); // 通知监听器发现等价关系
  }

  /**
   * Adds an expression <code>rel</code> to this set, without creating a
   * {@link org.apache.calcite.plan.volcano.RelSubset}. (Called only from
   * {@link org.apache.calcite.plan.volcano.RelSubset#add}.
   *
   * @param rel Relational expression
   * 
   * 中文说明:向该RelSet中添加一个关系表达式,不创建RelSubset
   * 该方法仅从RelSubset.add()调用,是内部添加方法
   * 会验证表达式的类型等价性,确保所有表达式语义相同
   * @param rel 要添加的关系表达式
   */
  void addInternal(RelNode rel) {
    if (!rels.contains(rel)) { // 如果表达式不在列表中
      rels.add(rel); // 添加到rels列表
      for (RelTrait trait : rel.getTraitSet()) { // 验证trait的规范化
        assert trait == trait.getTraitDef().canonize(trait); // 断言trait已规范化
      }

      VolcanoPlanner planner = // 获取优化器
          (VolcanoPlanner) rel.getCluster().getPlanner();
      if (planner.getListener() != null) { // 如果有监听器
        postEquivalenceEvent(planner, rel); // 发布等价事件
      }
    }
    if (this.rel == null) { // 如果是第一个表达式
      this.rel = rel; // 设置为基准表达式
    } else { // 否则验证类型等价性
      // 行类型必须相同,除了字段名
      RelOptUtil.verifyTypeEquivalence( // 验证类型等价
          this.rel, // 基准表达式
          rel, // 新表达式
          this); // 当前集合
    }
  }

  /**
   * Merges <code>otherSet</code> into this RelSet.
   *
   * <p>One generally calls this method after discovering that two relational
   * expressions are equivalent, and hence the <code>RelSet</code>s they
   * belong to are equivalent also.
   *
   * <p>After this method completes, <code>otherSet</code> is obsolete, its
   * {@link #equivalentSet} member points to this RelSet, and this RelSet is
   * still alive.
   *
   * @param planner  Planner
   * @param otherSet RelSet which is equivalent to this one
   * 
   * 中文说明:将otherSet合并到当前RelSet中
   * 该方法在发现两个关系表达式等价后调用,因此它们所属的RelSet也等价
   * 合并完成后,otherSet被废弃,其equivalentSet指向当前RelSet,当前RelSet仍然存活
   * 合并过程包括:合并子集、重新注册表达式、传播成本改进、触发规则匹配等
   * @param planner VolcanoPlanner实例
   * @param otherSet 与当前RelSet等价的另一个RelSet
   */
  void mergeWith(
      VolcanoPlanner planner,
      RelSet otherSet) {
    assert this != otherSet; // 断言不是同一个集合
    assert this.equivalentSet == null; // 断言当前集合未被废弃
    assert otherSet.equivalentSet == null; // 断言otherSet未被废弃
    LOGGER.trace("Merge set#{} into set#{}", otherSet.id, id); // 记录合并日志
    otherSet.equivalentSet = this; // 将otherSet标记为指向当前集合
    RelOptCluster cluster = castNonNull(rel).getCluster(); // 获取集群信息

    // 从allSets表中移除otherSet
    boolean existed = planner.allSets.remove(otherSet); // 从优化器的集合表中移除
    assert existed : "merging with a dead otherSet"; // 断言otherSet存在于表中

    Set<RelNode> changedRels = new HashSet<>(); // 收集需要更新最佳成本的RelNode

    // 合并子集
    for (RelSubset otherSubset : otherSet.subsets) { // 遍历otherSet的所有子集
      RelSubset subset = null; // 目标子集
      RelTraitSet otherTraits = otherSubset.getTraitSet(); // 获取trait集

      // 如果是逻辑或已交付的物理trait集
      if (otherSubset.isDelivered() || !otherSubset.isRequired()) {
        subset = getOrCreateSubset(cluster, otherTraits, false); // 获取或创建delivered子集
      }

      // 可能是required,或者既是delivered又是required,在这种情况下再次注册
      if (otherSubset.isRequired()) {
        subset = getOrCreateSubset(cluster, otherTraits, true); // 获取或创建required子集
      }

      requireNonNull(subset, "subset"); // 断言子集不为null
      if (subset.passThroughCache == null) { // 合并passThrough缓存
        subset.passThroughCache = otherSubset.passThroughCache; // 直接赋值
      } else if (otherSubset.passThroughCache != null) {
        subset.passThroughCache.addAll(otherSubset.passThroughCache); // 合并缓存
      }

      // 收集需要更新最佳成本的RelSubset实例
      if (otherSubset.bestCost.isLt(subset.bestCost) && otherSubset.best != null) {
        changedRels.add(otherSubset.best); // 添加到变更列表
      }
    }

    Set<RelNode> parentRels = new HashSet<>(parents); // 复制父节点列表
    for (RelNode otherRel : otherSet.rels) { // 遍历otherSet的所有表达式
      if (!(otherRel instanceof Spool) // 如果不是Spool节点
          && !otherRel.isEnforcer() // 且不是强制约束节点
          && parentRels.contains(otherRel)) { // 且是父节点
        // 如果otherRel是强制约束操作符(如Sort, Exchange),不要剪枝它
        // 以防它没有被标记为enforcer
        if (otherRel.getInputs().size() != 1 // 如果输入数量不是1
            || otherRel.getInput(0).getTraitSet() // 或输入trait不满足当前trait
                .satisfies(otherRel.getTraitSet())) {
          planner.prune(otherRel); // 剪枝该节点
        }
      }
      planner.reregister(this, otherRel); // 重新注册到当前集合
    }

    // 是否有另一个集合合并到这个集合?
    assert equivalentSet == null; // 断言当前集合未被废弃

    // 传播变更的RelNode的最佳成本信息
    for (RelNode rel : changedRels) { // 遍历变更的RelNode
      planner.propagateCostImprovements(rel); // 传播成本改进
    }

    // 更新所有在otherSet中有子节点的RelNode,反映子节点已重命名的事实
    // 复制数组以防止ConcurrentModificationException
    final List<RelNode> previousParents = // 复制otherSet的父节点列表
        ImmutableList.copyOf(otherSet.getParentRels());
    for (RelNode parentRel : previousParents) { // 遍历父节点
      planner.rename(parentRel); // 重命名父节点
    }

    // 重命名可能导致该集合与另一个集合合并。如果是这样,
    // 该集合现在已废弃。不需要更新该集合的子节点 - 事实上这可能很危险
    if (equivalentSet != null) { // 如果当前集合已被废弃
      return; // 直接返回
    }

    // 确保合并导致的成本变化被传播
    for (RelNode parentRel : getParentRels()) { // 遍历父节点
      planner.propagateCostImprovements(parentRel); // 传播成本改进
    }
    assert equivalentSet == null; // 断言当前集合未被废弃

    // 旧集合中的每个关系表达式现在都有新的父节点,
    // 因此可能触发新规则。检查规则匹配,就像新注册一样
    // (这可能导致已触发一次的规则再次触发)
    for (RelNode rel : rels) { // 遍历所有表达式
      assert planner.getSet(rel) == this; // 断言表达式属于当前集合
      planner.fireRules(rel); // 触发规则匹配
    }
    // 在子集上也触发规则匹配
    for (RelSubset subset : subsets) { // 遍历所有子集
      planner.fireRules(subset); // 触发规则匹配
    }
  }

  //~ Inner Classes ----------------------------------------------------------

  /**
   * An enum representing exploring state of current RelSet.
   * 
   * 中文说明:表示当前RelSet探索状态的枚举
   * 用于控制优化过程,防止重复探索,确保优化收敛
   */
  enum ExploringState {
    /**
     * The RelSet is exploring.
     * It means all possible rule matches are scheduled, but not fully applied.
     * This RelSet will refuse to explore again, but cannot provide a valid LB.
     * 
     * 中文说明:RelSet正在探索中
     * 所有可能的规则匹配已调度但未完全应用
     * 该RelSet将拒绝再次探索,但无法提供有效的下界成本
     */
    EXPLORING,

    /**
     * The RelSet is fully explored and is able to provide a valid LB.
     * 
     * 中文说明:RelSet已完全探索,可以提供有效的下界成本
     * 所有规则匹配已完成,成本信息已稳定
     * 可以用于成本比较和最优计划选择
     */
    EXPLORED
  }
}
