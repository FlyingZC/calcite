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

import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptListener;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTrait;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.plan.hep.HepRelVertex;
import org.apache.calcite.rel.AbstractRelNode;
import org.apache.calcite.rel.PhysicalNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.externalize.RelWriterImpl;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.trace.CalciteTrace;

import com.google.common.collect.Sets;

import org.apiguardian.api.API;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.calcite.linq4j.Nullness.castNonNull;

import static java.util.Objects.requireNonNull;

/**
 * Subset of an equivalence class where all relational expressions have the
 * same physical properties.
 * 等价类的子集，其中所有关系表达式都具有相同的物理属性。
 *
 * <p>Physical properties are instances of the {@link RelTraitSet}, and consist
 * of traits such as calling convention and collation (sort-order).
 * 物理属性是RelTraitSet的实例，由诸如调用约定和排序等特征组成。
 *
 * <p>For some traits, a relational expression can have more than one instance.
 * For example, R can be sorted on both [X] and [Y, Z]. In which case, R would
 * belong to the sub-sets for [X] and [Y, Z]; and also the leading edges [Y] and
 * [].
 * 对于某些特征，一个关系表达式可以有多个实例。例如，R可以按[X]和[Y,Z]排序。
 * 在这种情况下，R将属于[X]和[Y,Z]的子集，以及前导边缘[Y]和[]。
 *
 * @see RelNode
 * @see RelSet
 * @see RelTrait
 */
public class RelSubset extends AbstractRelNode {
  //~ Static fields/initializers ---------------------------------------------
  // 静态字段/初始化器

  private static final Logger LOGGER = CalciteTrace.getPlannerTracer(); // 日志记录器，用于跟踪规划器行为
  private static final int DELIVERED = 1; // 状态位：表示特征集已由子操作符或自身交付
  private static final int REQUIRED = 2; // 状态位：表示特征集由父操作符要求

  //~ Instance fields --------------------------------------------------------
  // 实例字段

  /** Optimization task state. */
  @Nullable OptimizeState taskState; // 优化任务状态，可以是OPTIMIZING或COMPLETED

  /** Cost of best known plan (it may have improved since). */
  RelOptCost bestCost; // 已知最佳计划的成本（可能已经改善）

  /** The set this subset belongs to. */
  final RelSet set; // 该子集所属的RelSet（等价类）

  /** Best known plan. */
  @Nullable RelNode best; // 已知的最佳计划（成本最低的RelNode）

  /** Timestamp for metadata validity. */
  long timestamp; // 元数据有效性的时间戳

  /**
   * Physical property state of current subset. Values:
   * 当前子集的物理属性状态。可能的值：
   *
   * <ul>
   * <li>0: logical operators, NONE convention is neither DELIVERED nor REQUIRED
   *     0: 逻辑操作符，NONE约定既不是DELIVERED也不是REQUIRED
   * <li>1: traitSet DELIVERED from child operators or itself
   *     1: 特征集由子操作符或自身交付
   * <li>2: traitSet REQUIRED from parent operators
   *     2: 特征集由父操作符要求
   * <li>3: both DELIVERED and REQUIRED
   *     3: 同时是DELIVERED和REQUIRED
   * </ul>
   */
  private int state = 0; // 物理属性状态，初始为0

  /**
   * This subset should trigger rules when it becomes delivered.
   * 当该子集变为交付状态时是否应该触发规则。
   */
  boolean triggerRule = false; // 是否触发规则的标志

  /**
   * When the subset state is REQUIRED, whether enable property enforcing
   * between this subset and other delivered subsets. When it is true,
   * no enforcer operators will be added even if the other subset can't
   * satisfy current subset's required traitSet.
   * 当子集状态为REQUIRED时，是否在当前子集与其他已交付子集之间启用属性强制执行。
   * 当为true时，即使其他子集不能满足当前子集所需的特征集，也不会添加强制执行操作符。
   */
  private boolean enforceDisabled = false; // 是否禁用强制执行的标志

  /**
   * The upper bound of the last OptimizeGroup call.
   * 上一次OptimizeGroup调用的上界成本。
   */
  RelOptCost upperBound; // 成本上界

  /**
   * A cache that recognize which RelNode has invoked the passThrough method
   * so as to avoid duplicate invocation.
   * 一个缓存，用于识别哪些RelNode已经调用了passThrough方法，以避免重复调用。
   */
  @Nullable Set<RelNode> passThroughCache; // passThrough方法调用缓存

  //~ Constructors -----------------------------------------------------------
  // 构造方法

  RelSubset(
      RelOptCluster cluster, // 关系表达式集群
      RelSet set, // 该子集所属的RelSet
      RelTraitSet traits) // 特征集
  {
    super(cluster, traits); // 调用父类AbstractRelNode的构造方法
    this.set = set; // 设置所属的RelSet
    assert traits.allSimple(); // 断言所有特征都是简单特征（非复合特征）
    computeBestCost(cluster, cluster.getPlanner()); // 计算最佳成本
    upperBound = bestCost; // 初始化成本上界为最佳成本
  }

  //~ Methods ----------------------------------------------------------------
  // 方法

  /**
   * Computes the best {@link RelNode} in this subset.
   * 计算该子集中最佳的RelNode。
   *
   * <p>Only necessary when a subset is created in a set that has subsets that
   * subsume it. Rationale:
   * 仅当在包含子集的集合中创建子集时才需要，这些子集包含它。原因：
   *
   * <ol>
   * <li>If the are no subsuming subsets, the subset is initially empty.</li>
   *     如果没有包含子集，该子集最初为空。
   * <li>After creation, {@code best} and {@code bestCost} are maintained
   *    incrementally by {@link VolcanoPlanner#propagateCostImprovements} and
   *    {@link RelSet#mergeWith(VolcanoPlanner, RelSet)}.</li>
   *     创建后，best和bestCost通过VolcanoPlanner#propagateCostImprovements和
   *     RelSet#mergeWith方法增量维护。
   * </ol>
   */
  @EnsuresNonNull("bestCost") // 确保bestCost不为null
  private void computeBestCost(
      @UnderInitialization RelSubset this, // 正在初始化的RelSubset实例
      RelOptCluster cluster, // 关系表达式集群
      RelOptPlanner planner) // 优化规划器
  {
    bestCost = planner.getCostFactory().makeInfiniteCost(); // 初始化为无限大成本
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象
    @SuppressWarnings("method.invocation.invalid")
    Iterable<RelNode> rels = getRels(); // 获取该子集中的所有关系表达式
    for (RelNode rel : rels) { // 遍历所有关系表达式
      final RelOptCost cost = planner.getCost(rel, mq); // 计算每个关系表达式的成本
      if (cost == null) { // 如果成本为null，跳过
        continue;
      }
      if (cost.isLt(bestCost)) { // 如果当前成本小于最佳成本
        bestCost = cost; // 更新最佳成本
        best = rel; // 更新最佳计划
      }
    }
  }

  void setDelivered() { // 设置子集为已交付状态
    triggerRule = !isDelivered(); // 如果之前未交付，则设置为需要触发规则
    state |= DELIVERED; // 设置DELIVERED状态位
  }

  void setRequired() { // 设置子集为所需状态
    triggerRule = false; // 不需要触发规则
    state |= REQUIRED; // 设置REQUIRED状态位
  }

  @API(since = "1.23", status = API.Status.EXPERIMENTAL) // 实验性API，从1.23版本开始
  public boolean isDelivered() { // 判断子集是否已交付
    return (state & DELIVERED) == DELIVERED; // 检查DELIVERED状态位
  }

  @API(since = "1.23", status = API.Status.EXPERIMENTAL) // 实验性API，从1.23版本开始
  public boolean isRequired() { // 判断子集是否被要求
    return (state & REQUIRED) == REQUIRED; // 检查REQUIRED状态位
  }

  void disableEnforcing() { // 禁用属性强制执行
    assert isDelivered(); // 断言子集已交付
    enforceDisabled = true; // 设置禁用标志
  }

  boolean isEnforceDisabled() { // 判断是否禁用了属性强制执行
    return enforceDisabled; // 返回禁用标志
  }

  public @Nullable RelNode getBest() { // 获取最佳计划（成本最低的RelNode）
    return best; // 返回best字段
  }

  public @Nullable RelNode getOriginal() { // 获取原始关系表达式
    return set.rel; // 返回RelSet中的原始关系表达式
  }

  @API(since = "1.27", status = API.Status.INTERNAL) // 内部API，从1.27版本开始
  public RelNode getBestOrOriginal() { // 获取最佳计划或原始关系表达式
    RelNode result = getBest(); // 先尝试获取最佳计划
    if (result != null) { // 如果最佳计划存在
      return result; // 返回最佳计划
    }
    return requireNonNull(getOriginal(), "both best and original nodes are null"); // 否则返回原始关系表达式
  }

  @Override public RelNode stripped() { // 重写父类方法，返回剥离后的节点
    return getBestOrOriginal(); // 返回最佳计划或原始关系表达式
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
    if (inputs.isEmpty()) { // 如果没有输入（叶子节点）
      final RelTraitSet traitSet1 = traitSet.simplify(); // 简化特征集
      if (traitSet1.equals(this.traitSet)) { // 如果简化后的特征集与当前特征集相同
        return this; // 返回当前实例
      }
      return set.getOrCreateSubset(getCluster(), traitSet1, isRequired()); // 否则创建或获取新的子集
    }
    throw new UnsupportedOperationException(); // 如果有输入则抛出不支持异常
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) { // 计算自身成本
    return planner.getCostFactory().makeZeroCost(); // 返回零成本，因为RelSubset本身不执行计算
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 估算行数
    if (best != null) { // 如果存在最佳计划
      return mq.getRowCount(best); // 返回最佳计划的行数
    } else { // 否则
      return mq.getRowCount(castNonNull(set.rel)); // 返回原始关系表达式的行数
    }
  }

  @Override public void explain(RelWriter pw) { // 解释关系表达式
    // Not a typical implementation of "explain". We don't gather terms &
    // values to be printed later. We actually do the work.
    // 不是典型的"explain"实现。我们不收集稍后要打印的项和值。我们实际执行工作。
    pw.item("subset", toString()); // 写入子集信息
    final AbstractRelNode input = // 获取最佳计划或原始关系表达式
        (@Nullable AbstractRelNode) Util.first(getBest(), getOriginal());
    if (input == null) { // 如果输入为null
      return; // 直接返回
    }
    input.explainTerms(pw); // 解释输入的项
    pw.done(input); // 完成解释
  }

  @Override public boolean deepEquals(@Nullable Object obj) { // 深度相等比较
    return this == obj; // 使用引用相等性
  }

  @Override public int deepHashCode() { // 深度哈希码
    return this.hashCode(); // 使用常规哈希码
  }

  @Override protected RelDataType deriveRowType() { // 推导行类型
    return castNonNull(set.rel).getRowType(); // 返回原始关系表达式的行类型
  }

  /**
   * Returns the collection of RelNodes one of whose inputs is in this
   * subset.
   * 返回其输入之一在该子集中的RelNode集合。
   */
  Set<RelNode> getParents() { // 获取父节点集合
    final Set<RelNode> list = new LinkedHashSet<>(); // 创建有序集合
    for (RelNode parent : set.getParentRels()) { // 遍历RelSet中的所有父关系表达式
      for (RelSubset rel : inputSubsets(parent)) { // 遍历父节点的所有输入子集
        // see usage of this method in propagateCostImprovements0()
        // 参见propagateCostImprovements0()中对该方法的使用
        if (rel == this) { // 如果输入子集是当前子集
          list.add(parent); // 添加父节点到集合
          break; // 跳出内层循环
        }
      }
    }
    return list; // 返回父节点集合
  }

  /**
   * Returns the collection of distinct subsets that contain a RelNode one
   * of whose inputs is in this subset.
   * 返回包含RelNode的不同子集的集合，这些RelNode的输入之一在该子集中。
   */
  Set<RelSubset> getParentSubsets(VolcanoPlanner planner) { // 获取父子集集合
    final Set<RelSubset> list = new LinkedHashSet<>(); // 创建有序集合
    for (RelNode parent : set.getParentRels()) { // 遍历RelSet中的所有父关系表达式
      for (RelSubset rel : inputSubsets(parent)) { // 遍历父节点的所有输入子集
        if (rel.set == set && rel.getTraitSet().equals(traitSet)) { // 如果属于同一RelSet且特征集相同
          list.add(planner.getSubsetNonNull(parent)); // 添加父节点的子集
          break; // 跳出内层循环
        }
      }
    }
    return list; // 返回父子集集合
  }

  private static List<RelSubset> inputSubsets(RelNode parent) { // 获取父节点的输入子集列表
    //noinspection unchecked
    return (List<RelSubset>) (List) parent.getInputs(); // 将输入转换为RelSubset列表
  }

  /**
   * Returns a list of relational expressions one of whose children is this
   * subset. The elements of the list are distinct.
   * 返回其子节点之一是该子集的关系表达式列表。列表中的元素是唯一的。
   */
  public Collection<RelNode> getParentRels() { // 获取父关系表达式集合
    final Set<RelNode> list = new LinkedHashSet<>(); // 创建有序集合
  parentLoop: // 标签，用于跳出外层循环
    for (RelNode parent : set.getParentRels()) { // 遍历RelSet中的所有父关系表达式
      for (RelSubset rel : inputSubsets(parent)) { // 遍历父节点的所有输入子集
        if (rel.set == set && traitSet.satisfies(rel.getTraitSet())) { // 如果属于同一RelSet且满足特征集
          list.add(parent); // 添加父节点到集合
          continue parentLoop; // 跳出外层循环
        }
      }
    }
    return list; // 返回父关系表达式集合
  }

  RelSet getSet() { // 获取该子集所属的RelSet
    return set; // 返回set字段
  }

  /**
   * Adds expression <code>rel</code> to this subset.
   * 将关系表达式rel添加到该子集。
   */
  void add(RelNode rel) { // 添加关系表达式到子集
    assert !(rel instanceof HepRelVertex); // 断言不是HepRelVertex（Hep规划器使用）
    if (set.rels.contains(rel)) { // 如果RelSet中已包含该关系表达式
      return; // 直接返回
    }

    VolcanoPlanner planner = (VolcanoPlanner) rel.getCluster().getPlanner(); // 获取Volcano规划器
    if (planner.getListener() != null) { // 如果有监听器
      RelOptListener.RelEquivalenceEvent event = // 创建等价事件
          new RelOptListener.RelEquivalenceEvent(
              planner, // 规划器
              rel, // 关系表达式
              this, // 子集
              true); // 等价标志
      planner.getListener().relEquivalenceFound(event); // 通知监听器发现等价关系
    }

    set.addInternal(rel); // 内部添加到RelSet
    if (false) { // 此代码块被禁用（用于调试）
      Set<CorrelationId> variablesSet = RelOptUtil.getVariablesSet(rel); // 获取变量集
      Set<CorrelationId> variablesStopped = rel.getVariablesSet(); // 获取停止的变量
      Set<CorrelationId> variablesPropagated = // 计算传播的变量
          Util.minus(variablesSet, variablesStopped);
      assert set.variablesPropagated.containsAll(variablesPropagated); // 断言RelSet包含所有传播的变量
      Set<CorrelationId> variablesUsed = RelOptUtil.getVariablesUsed(rel); // 获取使用的变量
      assert set.variablesUsed.containsAll(variablesUsed); // 断言RelSet包含所有使用的变量
    }
  }

  /**
   * Recursively builds a tree consisting of the cheapest plan at each node.
   * 递归构建由每个节点的最便宜计划组成的树。
   */
  RelNode buildCheapestPlan(VolcanoPlanner planner) { // 构建最便宜的计划树
    CheapestPlanReplacer replacer = new CheapestPlanReplacer(planner); // 创建最便宜计划替换器
    final RelNode cheapest = replacer.visit(this, -1, null); // 访问并替换为最便宜计划

    if (planner.getListener() != null) { // 如果有监听器
      RelOptListener.RelChosenEvent event = // 创建选择事件
          new RelOptListener.RelChosenEvent(
              planner, // 规划器
              null); // 关系表达式（null表示完成）
      planner.getListener().relChosen(event); // 通知监听器已选择计划
    }

    return cheapest; // 返回最便宜的计划
  }

  @Override public void collectVariablesUsed(Set<CorrelationId> variableSet) { // 收集使用的变量
    variableSet.addAll(set.variablesUsed); // 将RelSet中使用的变量添加到集合
  }

  @Override public void collectVariablesSet(Set<CorrelationId> variableSet) { // 收集设置的变量
    variableSet.addAll(set.variablesPropagated); // 将RelSet中传播的变量添加到集合
  }

  /**
   * Returns the rel nodes in this rel subset.  All rels must have the same
   * traits and are logically equivalent.
   * 返回该关系子集中的关系节点。所有关系必须具有相同的特征并且在逻辑上等价。
   *
   * @return all the rels in the subset
   *         子集中的所有关系
   */
  public Iterable<RelNode> getRels() { // 获取子集中的所有关系表达式
    return () -> Linq4j.asEnumerable(set.rels) // 将RelSet中的关系转换为可枚举集合
        .where(v1 -> v1.getTraitSet().satisfies(traitSet)) // 过滤出满足当前特征集的关系
        .iterator(); // 返回迭代器
  }

  /**
   * As {@link #getRels()} but returns a list.
   * 与getRels()相同，但返回列表。
   */
  public List<RelNode> getRelList() { // 获取子集中的所有关系表达式列表
    final List<RelNode> list = new ArrayList<>(); // 创建列表
    for (RelNode rel : set.rels) { // 遍历RelSet中的所有关系
      if (rel.getTraitSet().satisfies(traitSet)) { // 如果满足当前特征集
        list.add(rel); // 添加到列表
      }
    }
    return list; // 返回列表
  }

  /**
   * Returns whether this subset contains the specified relational expression.
   * 返回该子集是否包含指定的关系表达式。
   */
  public boolean contains(RelNode node) { // 判断子集是否包含指定节点
    return set.rels.contains(node) && node.getTraitSet().satisfies(traitSet); // 检查RelSet中是否包含且满足特征集
  }

  /**
   * Returns stream of subsets whose traitset satisfies
   * current subset's traitset.
   * 返回特征集满足当前子集特征集的子集流。
   */
  @API(since = "1.23", status = API.Status.EXPERIMENTAL) // 实验性API，从1.23版本开始
  public Stream<RelSubset> getSubsetsSatisfyingThis() { // 获取满足当前特征集的子集流
    return set.subsets.stream() // 获取RelSet中所有子集的流
      .filter(s -> s.getTraitSet().satisfies(traitSet)); // 过滤出满足当前特征集的子集
  }

  /**
   * Returns stream of subsets whose traitset is satisfied
   * by current subset's traitset.
   * 返回其特征集被当前子集特征集满足的子集流。
   */
  @API(since = "1.23", status = API.Status.EXPERIMENTAL) // 实验性API，从1.23版本开始
  public Stream<RelSubset> getSatisfyingSubsets() { // 获取被当前特征集满足的子集流
    return set.subsets.stream() // 获取RelSet中所有子集的流
      .filter(s -> traitSet.satisfies(s.getTraitSet())); // 过滤出被当前特征集满足的子集
  }

  /**
   * Returns the best cost if this subset is fully optimized
   * or null if the subset is not fully optimized.
   * 如果该子集已完全优化，则返回最佳成本；否则返回null。
   */
  @API(since = "1.24", status = API.Status.INTERNAL) // 内部API，从1.24版本开始
  public @Nullable RelOptCost getWinnerCost() { // 获取胜者成本（优化完成后的最佳成本）
    if (taskState == OptimizeState.COMPLETED && bestCost.isLe(upperBound)) { // 如果优化完成且最佳成本<=上界
      return bestCost; // 返回最佳成本
    }
    // if bestCost != upperBound, it means optimize failed
    // 如果bestCost != upperBound，表示优化失败
    return null; // 返回null
  }

  void startOptimize(RelOptCost ub) { // 开始优化
    assert getWinnerCost() == null : this + " is already optimized"; // 断言尚未优化
    if (upperBound.isLt(ub)) { // 如果当前上界小于新的上界
      upperBound = ub; // 更新上界
      if (bestCost.isLt(upperBound)) { // 如果最佳成本小于上界
        upperBound = bestCost; // 使用最佳成本作为上界
      }
    }
    taskState = OptimizeState.OPTIMIZING; // 设置状态为优化中
  }

  void setOptimized() { // 设置为已优化
    taskState = OptimizeState.COMPLETED; // 设置状态为已完成
  }

  boolean resetTaskState() { // 重置任务状态
    boolean optimized = taskState != null; // 记录是否已优化
    taskState = null; // 清空任务状态
    upperBound = bestCost; // 重置上界为最佳成本
    return optimized; // 返回是否已优化
  }

  @Nullable RelNode passThrough(RelNode rel) { // 传递方法，用于物理节点优化
    if (!(rel instanceof PhysicalNode)) { // 如果不是物理节点
      return null; // 返回null
    }
    if (passThroughCache == null) { // 如果缓存为空
      passThroughCache = Sets.newIdentityHashSet(); // 创建身份哈希集
      passThroughCache.add(rel); // 添加到缓存
    } else if (!passThroughCache.add(rel)) { // 如果添加失败（已存在）
      return null; // 返回null
    }
    return ((PhysicalNode) rel).passThrough(this.getTraitSet()); // 调用物理节点的passThrough方法
  }

  boolean isExplored() { // 判断是否已探索
    return set.exploringState == RelSet.ExploringState.EXPLORED; // 检查探索状态
  }

  boolean explore() { // 开始探索
    if (set.exploringState != null) { // 如果已经在探索或已探索
      return false; // 返回false
    }
    set.exploringState = RelSet.ExploringState.EXPLORING; // 设置状态为探索中
    return true; // 返回true表示开始探索
  }

  void setExplored() { // 设置为已探索
    set.exploringState = RelSet.ExploringState.EXPLORED; // 设置状态为已探索
  }

  //~ Inner Classes ----------------------------------------------------------
  // 内部类

  /**
   * Identifies the leaf-most non-implementable nodes.
   * 识别最底层的不可实现节点。
   */
  static class DeadEndFinder { // 死胡同查找器
    final Set<RelSubset> deadEnds = new HashSet<>(); // 死胡同子集集合
    // To save time
    // 为了节省时间
    private final Set<RelNode> visitedNodes = new HashSet<>(); // 已访问节点集合
    // For cycle detection
    // 用于循环检测
    private final Set<RelNode> activeNodes = new HashSet<>(); // 活动节点集合

    private boolean visit(RelNode p) { // 访问节点
      if (p instanceof RelSubset) { // 如果是RelSubset
        visitSubset((RelSubset) p); // 访问子集
        return false; // 返回false
      }
      return visitRel(p); // 访问关系节点
    }

    private void visitSubset(RelSubset subset) { // 访问子集
      RelNode cheapest = subset.getBest(); // 获取最佳计划
      if (cheapest != null) { // 如果最佳计划存在
        // Subset is implementable, and we are looking for bad ones, so stop here
        // 子集是可实现的，我们在查找坏的子集，所以在这里停止
        return; // 直接返回
      }

      boolean isEmpty = true; // 标记是否为空
      for (RelNode rel : subset.getRels()) { // 遍历子集中的所有关系
        if (rel instanceof AbstractConverter) { // 如果是转换器
          // Converters are not implementable
          // 转换器不可实现
          continue; // 跳过
        }
        if (!activeNodes.add(rel)) { // 如果添加失败（已在活动集中）
          continue; // 跳过
        }
        boolean res = visit(rel); // 递归访问
        isEmpty &= res; // 更新是否为空
        activeNodes.remove(rel); // 从活动集中移除
      }
      if (isEmpty) { // 如果为空
        deadEnds.add(subset); // 添加到死胡同集合
      }
    }

    /**
     * Returns true when input {@code RelNode} is cyclic.
     * 当输入RelNode是循环时返回true。
     */
    private boolean visitRel(RelNode p) { // 访问关系节点
      // If one of the inputs is in "active" set, that means the rel forms a cycle,
      // then we just ignore it. Cyclic rels are not implementable.
      // 如果输入之一在"active"集合中，这意味着rel形成循环，我们就忽略它。
      // 循环的rel不可实现。
      for (RelNode oldInput : p.getInputs()) { // 遍历所有输入
        if (activeNodes.contains(oldInput)) { // 如果输入在活动集中
          return true; // 返回true表示循环
        }
      }
      // The same subset can be used multiple times (e.g. union all with the same inputs),
      // so it is important to perform "contains" and "add" in different loops
      // 同一个子集可以被多次使用（例如，具有相同输入的union all），
      // 所以在不同的循环中执行"contains"和"add"很重要
      activeNodes.addAll(p.getInputs()); // 将所有输入添加到活动集
      for (RelNode oldInput : p.getInputs()) { // 遍历所有输入
        if (!visitedNodes.add(oldInput)) { // 如果添加失败（已访问）
          // We don't want to explore the same subset twice
          // 我们不想探索同一个子集两次
          continue; // 跳过
        }
        visit(oldInput); // 递归访问
      }
      activeNodes.removeAll(p.getInputs()); // 从活动集中移除所有输入
      return false; // 返回false
    }
  }

  @Override public String getDigest() { // 获取摘要字符串
    return "RelSubset#" + set.id + '.' + getTraitSet(); // 返回格式化的摘要
  }

  /**
   * Visitor which walks over a tree of {@link RelSet}s, replacing each node
   * with the cheapest implementation of the expression.
   * 访问者，遍历RelSet树，将每个节点替换为表达式的最便宜实现。
   */
  static class CheapestPlanReplacer { // 最便宜计划替换器
    final VolcanoPlanner planner; // Volcano规划器
    final Map<Integer, RelNode> visited = new HashMap<>(); // 已访问节点映射（ID -> RelNode）

    CheapestPlanReplacer(VolcanoPlanner planner) { // 构造方法
      super(); // 调用父类构造
      this.planner = requireNonNull(planner, "planner"); // 设置规划器，确保不为null
    }

    private static String traitDiff(RelTraitSet original, RelTraitSet desired) { // 计算特征差异
      return Pair.zip(original, desired) // 将原始和期望的特征集配对
          .stream() // 转换为流
          .filter(p -> !p.left.satisfies(p.right)) // 过滤出不满足的特征
          .map(p -> p.left.getTraitDef().getSimpleName() + ": " + p.left + " -> " + p.right) // 格式化差异
          .collect(Collectors.joining(", ", "[", "]")); // 收集为字符串
    }

    public RelNode visit( // 访问节点并替换为最便宜实现
        RelNode p, // 要访问的节点
        int ordinal, // 序号（父节点中的位置）
        @Nullable RelNode parent) { // 父节点
      final int pId = p.getId(); // 获取节点ID
      RelNode prevVisit = visited.get(pId); // 获取之前访问的结果
      if (prevVisit != null) { // 如果之前已访问
        // return memoized result of previous visit if available
        // 如果可用，返回之前访问的记忆结果
        return prevVisit; // 返回记忆结果
      }

      if (p instanceof RelSubset) { // 如果是RelSubset
        RelSubset subset = (RelSubset) p; // 转换为RelSubset
        RelNode cheapest = subset.best; // 获取最佳计划
        if (cheapest == null) { // 如果最佳计划为null（无法生成）
          // Dump the planner's expression pool so we can figure
          // out why we reached impasse.
          // 转储规划器的表达式池，以便我们找出为什么陷入僵局。
          StringWriter sw = new StringWriter(); // 创建字符串写入器
          final PrintWriter pw = new PrintWriter(sw); // 创建打印写入器

          pw.print("There are not enough rules to produce a node with desired properties"); // 打印错误信息
          RelTraitSet desiredTraits = subset.getTraitSet(); // 获取期望的特征集
          String sep = ": "; // 分隔符
          for (RelTrait trait : desiredTraits) { // 遍历所有特征
            pw.print(sep); // 打印分隔符
            pw.print(trait.getTraitDef().getSimpleName()); // 打印特征定义名称
            pw.print("="); // 打印等号
            pw.print(trait); // 打印特征值
            sep = ", "; // 更新分隔符
          }
          pw.print("."); // 打印句号
          DeadEndFinder finder = new DeadEndFinder(); // 创建死胡同查找器
          finder.visit(subset); // 查找死胡同
          if (finder.deadEnds.isEmpty()) { // 如果没有死胡同
            pw.print(" All the inputs have relevant nodes, however the cost is still infinite."); // 打印信息
          } else { // 如果有死胡同
            Map<String, Long> problemCounts = // 统计问题数量
                finder.deadEnds.stream() // 获取死胡同流
                    .filter(deadSubset -> deadSubset.getOriginal() != null) // 过滤出有原始节点的
                    .map(x -> { // 转换为问题描述
                      RelNode original = castNonNull(x.getOriginal()); // 获取原始节点
                      return original.getClass().getSimpleName() // 类名
                          + traitDiff(original.getTraitSet(), x.getTraitSet()); // 加上特征差异
                    })
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())); // 按问题描述分组计数
            // Sort problems from most often to less often ones
            // 按从最常遇到到最少遇到的顺序排序问题
            String problems = problemCounts.entrySet().stream() // 获取问题条目流
                .sorted(Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed()) // 按计数降序排序
                .map(e -> e.getKey() + (e.getValue() > 1 ? " (" + e.getValue() + " cases)" : "")) // 格式化
                .collect(Collectors.joining(", ")); // 连接为字符串
            pw.println(); // 换行
            pw.print("Missing conversion"); // 打印"缺少转换"
            pw.print(finder.deadEnds.size() == 1 ? " is " : "s are "); // 打印单复数
            pw.print(problems); // 打印问题描述
            pw.println(); // 换行
            if (finder.deadEnds.size() == 1) { // 如果只有一个死胡同
              pw.print("There is 1 empty subset: "); // 打印信息
            }
            if (finder.deadEnds.size() > 1) { // 如果有多个死胡同
              pw.println("There are " + finder.deadEnds.size() + " empty subsets:"); // 打印信息
            }
            int i = 0; // 计数器
            int rest = finder.deadEnds.size(); // 剩余数量
            for (RelSubset deadEnd : finder.deadEnds) { // 遍历所有死胡同
              if (finder.deadEnds.size() > 1) { // 如果有多个
                pw.print("Empty subset "); // 打印前缀
                pw.print(i); // 打印序号
                pw.print(": "); // 打印冒号
              }
              pw.print(deadEnd); // 打印死胡同
              pw.println(", the relevant part of the original plan is as follows"); // 打印提示
              RelNode original = deadEnd.getOriginal(); // 获取原始节点
              if (original != null) { // 如果原始节点存在
                original.explain( // 解释原始节点
                    new RelWriterImpl(pw, SqlExplainLevel.EXPPLAN_ATTRIBUTES, true)); // 创建写入器
              }
              i++; // 增加计数器
              rest--; // 减少剩余数量
              if (rest > 0) { // 如果还有剩余
                pw.println(); // 换行
              }
              if (i >= 10 && rest > 1) { // 如果已打印10个且还有多个
                pw.print("The rest "); // 打印前缀
                pw.print(rest); // 打印剩余数量
                pw.println(" leafs are omitted."); // 打印省略信息
                break; // 跳出循环
              }
            }
          }
          pw.println(); // 换行

          planner.dump(pw); // 转储规划器状态
          pw.flush(); // 刷新写入器
          final String dump = sw.toString(); // 获取转储字符串
          RuntimeException e = // 创建异常
              new RelOptPlanner.CannotPlanException(dump); // 无法计划异常
          LOGGER.trace("Caught exception in class={}, method=visit", getClass().getName(), e); // 记录日志
          throw e; // 抛出异常
        }
        p = cheapest; // 使用最佳计划
      }

      if (ordinal != -1) { // 如果序号有效（不是根节点）
        if (planner.getListener() != null) { // 如果有监听器
          RelOptListener.RelChosenEvent event = // 创建选择事件
              new RelOptListener.RelChosenEvent(
                  planner, // 规划器
                  p); // 节点
          planner.getListener().relChosen(event); // 通知监听器
        }
      }

      List<RelNode> oldInputs = p.getInputs(); // 获取原始输入列表
      List<RelNode> inputs = new ArrayList<>(); // 创建新输入列表
      for (int i = 0; i < oldInputs.size(); i++) { // 遍历所有输入
        RelNode oldInput = oldInputs.get(i); // 获取原始输入
        RelNode input = visit(oldInput, i, p); // 递归访问输入
        inputs.add(input); // 添加到新列表
      }
      if (!inputs.equals(oldInputs)) { // 如果输入有变化
        final RelNode pOld = p; // 保存旧节点
        p = p.copy(p.getTraitSet(), inputs); // 复制节点并使用新输入
        planner.provenanceMap.put( // 记录来源
            p, new VolcanoPlanner.DirectProvenance(pOld)); // 直接来源
      }
      visited.put(pId, p); // memoize result for pId
      // 记忆pId的结果
      return p; // 返回节点
    }
  }

  /** State of optimizer. */
  // 优化器状态
  enum OptimizeState { // 优化状态枚举
    OPTIMIZING, // 优化中
    COMPLETED // 已完成
  }
}
