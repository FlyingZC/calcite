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

import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteSystemProperty;
import org.apache.calcite.plan.AbstractRelOptPlanner;
import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelDigest;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptCostFactory;
import org.apache.calcite.plan.RelOptLattice;
import org.apache.calcite.plan.RelOptMaterialization;
import org.apache.calcite.plan.RelOptMaterializations;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTrait;
import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.PhysicalNode;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.Converter;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.externalize.RelWriterImpl;
import org.apache.calcite.rel.metadata.CyclicMetadataException;
import org.apache.calcite.rel.metadata.RelMdUtil;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.rules.SubstitutionRule;
import org.apache.calcite.rel.rules.TransformationRule;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.runtime.Hook;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.util.Litmus;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkArgument;

import static org.apache.calcite.linq4j.Nullness.castNonNull;

import static java.util.Objects.requireNonNull;

/**
 * VolcanoPlanner 优化器通过动态规划算法选择性地转换表达式来优化查询
 * 
 * VolcanoPlanner 是 Calcite 框架中基于 Volcano/Cascades 优化器模型的查询优化器实现
 * 
 * 核心概念和作用：
 * 1. 等价集合(RelSet): 将语义相同但实现不同的关系表达式组织在一起,形成等价集合
 * 2. 子集(RelSubset): 在一个等价集合中,按照不同的特征集合(如物理属性)划分的子集合
 * 3. 动态规划: 通过自底向上的方式,逐步搜索和评估所有可能的执行计划
 * 4. 成本模型: 使用成本函数评估不同执行计划的优劣,选择成本最低的方案
 * 5. 规则驱动: 通过应用转换规则生成等价的关系表达式,探索搜索空间
 * 6. 剪枝: 基于成本或其他启发式信息,排除明显不可行的执行计划
 * 
 * 优化流程：
 * 1. 注册: 将初始的关系表达式注册到优化器中
 * 2. 规则匹配: 为每个关系表达式匹配适用的转换规则
 * 3. 规则应用: 应用规则生成新的等价关系表达式
 * 4. 成本计算: 计算每个关系表达式的成本
 * 5. 合并: 将等价的关系表达式合并到同一个集合中
 * 6. 选择: 从等价集合中选择成本最低的关系表达式作为最终执行计划
 * 
 * 主要特点：
 * - 支持多阶段优化(逻辑优化和物理优化)
 * - 支持自定义规则和成本模型
 * - 支持特征集合(如调用约定、排序、分布等)
 * - 支持物化视图和 lattice 优化
 * - 提供自顶向下和自底向上两种优化模式
 */
public class VolcanoPlanner extends AbstractRelOptPlanner {

  //~ Instance fields --------------------------------------------------------

  protected @MonotonicNonNull RelSubset root; // 根子集,表示整个查询优化树的根节点,最终从这里获取最优执行计划

  /**
   * 操作数映射表:将 RelNode 类映射到可以应用于该类的关系操作数
   *
   * <p>每个操作数都可以作为规则调用的"入口点",当注册一个匹配该操作数的 RelNode 时
   * 这个映射表允许我们根据 RelNode 的类来快速定位适用的操作数,提高规则匹配效率
   * 
   * 作用:加速规则匹配过程,避免遍历所有规则来查找适用的规则
   */
  private final Multimap<Class<? extends RelNode>, RelOptRuleOperand>
      classOperands = LinkedListMultimap.create();

  /**
   * 所有等价集合的列表,仅用于调试目的
   * 
   * 作用:在调试时可以遍历所有集合,检查优化器的内部状态
   */
  final List<RelSet> allSets = new ArrayList<>();

  /**
   * 规范化映射表:从关系表达式的摘要(digest)映射到唯一的关系表达式
   * 
   * 摘要是关系表达式的字符串表示,包含其类型、输入和属性等信息
   * 
   * 作用:
   * - 快速检测重复的关系表达式
   * - 避免注册相同的表达式多次
   * - 支持等价检测和合并
   */
  private final Map<RelDigest, RelNode> mapDigestToRel =
      new HashMap<>();

  /**
   * 关系表达式到子集的映射表
   *
   * <p>使用 IdentityHashMap 而不是普通的 HashMap,这样可以简化 RelSet 合并的过程
   * 大多数 RelNode 通过其摘要(包含其子表达式所属的集合)来标识
   * 如果子表达式属于同一个集合,需要特别小心处理,否则会产生循环依赖
   * 
   * 作用:
   * - 快速查找关系表达式所属的子集
   * - 使用对象身份而非 equals/hashCode,避免摘要变化导致的问题
   */
  private final IdentityHashMap<RelNode, RelSubset> mapRel2Subset =
      new IdentityHashMap<>();

  /**
   * 需要被剪枝的关系节点集合
   *
   * <p>如果一个 RelNode 被标记为剪枝,则:
   * - 所有使用该节点的规则调用将被忽略
   * - 未来的规则调用不会排队等待该节点
   * 
   * 作用:排除明显不可行或已经确定不是最优的执行计划,减少搜索空间
   */
  final Set<RelNode> prunedNodes = new HashSet<>();

  /**
   * 所有已注册的 schema 集合
   * 
   * 作用:追踪哪些 schema 已经注册到优化器中,避免重复注册
   */
  private final Set<RelOptSchema> registeredSchemas = new HashSet<>();

  /**
   * 规则驱动器:用于管理规则和规则匹配
   * 
   * 作用:
   * - 维护规则匹配队列
   * - 决定何时应用规则
   * - 控制优化流程(自顶向下或自底向上)
   * - 处理规则调用的优先级
   */
  RuleDriver ruleDriver;

  /**
   * 当前注册的特征定义列表
   * 
   * 特征定义(RelTraitDef)定义了关系表达式的特征类型,如:
   * - Convention(调用约定):逻辑或物理实现
   * - Collation(排序):数据的排序属性
   * - Distribution(分布):数据的分布属性
   * 
   * 作用:
   * - 定义优化器支持的特征类型
   * - 用于创建和验证特征集合
   * - 支持特征转换规则
   */
  private final List<RelTraitDef> traitDefs = new ArrayList<>();

  private int nextSetId = 0; // 下一个等价集合的 ID,用于唯一标识每个集合

  private @MonotonicNonNull RelNode originalRoot; // 原始根节点,保存未优化的原始查询树,用于物化视图匹配等

  private @Nullable Convention rootConvention; // 根节点的调用约定,表示最终输出要求的物理实现类型

  /**
   * 优化器是否已锁定,锁定的优化器不接受新规则
   * 
   * 作用:在优化开始后锁定优化器,防止在优化过程中添加新规则导致不一致
   */
  private boolean locked;

  /**
   * 具有 Convention.NONE 的关系节点是否具有无限成本
   * 
   * Convention.NONE 表示逻辑节点,尚未转换为物理实现
   * 
   * 作用:
   * - 如果为 true,逻辑节点会被认为是不可执行的,成本为无限大
   * - 这有助于强制优化器将逻辑节点转换为物理节点
   */
  private boolean noneConventionHasInfiniteCost = true;

  private final List<RelOptMaterialization> materializations =
      new ArrayList<>(); // 物化视图列表,存储所有已注册的物化视图

  /**
   * Lattice 映射表:根据其星形表的限定名称映射 lattice
   * 
   * Lattice 是一种特殊的物化视图结构,用于星型模式查询优化
   * 
   * 作用:快速查找表对应的 lattice,用于查询重写和优化
   */
  private final Map<List<String>, RelOptLattice> latticeByName =
      new LinkedHashMap<>();

  final Map<RelNode, Provenance> provenanceMap; // 来源映射表:记录每个关系表达式的来源(规则调用或直接创建)

  final Deque<VolcanoRuleCall> ruleCallStack = new ArrayDeque<>(); // 规则调用栈,用于追踪当前的规则调用链

  /** 零成本,根据 #costFactory 定义,不一定是 VolcanoCost 类型
   * 
   * 作用:作为成本比较的基准值
   */
  final RelOptCost zeroCost;

  /** 无限成本,根据 #costFactory 定义,不一定是 VolcanoCost 类型
   * 
   * 作用:表示不可执行或被剪枝的执行计划的成本
   */
  final RelOptCost infCost;

  /**
   * 是否启用自顶向下优化模式
   * 
   * 自顶向下优化 vs 自底向上优化:
   * - 自顶向下:从根节点开始,递归地为每个子节点选择最优实现
   * - 自底向上:从叶子节点开始,逐层向上计算最优实现
   * 
   * 作用:控制优化策略,影响优化效率和最终结果
   */
  boolean topDownOpt = CalciteSystemProperty.TOPDOWN_OPT.value();

  /**
   * 额外的探索根节点集合
   * 
   * 作用:用于探索优化空间,可能找到比主根节点更好的执行计划
   */
  final Set<RelSubset> explorationRoots = new HashSet<>();

  //~ Constructors -----------------------------------------------------------

  /**
   * 创建一个未初始化的 VolcanoPlanner
   * 
   * 要完全初始化优化器,调用者必须注册:
   * - 期望的关系表达式集合
   * - 优化规则集合
   * - 调用约定(特征定义)
   * 
   * 使用默认的成本工厂(VolcanoCost.FACTORY)和外部上下文(null)
   */
  public VolcanoPlanner() {
    this(null, null);
  }

  /**
   * 创建一个未初始化的 VolcanoPlanner,并指定外部上下文
   * 
   * 要完全初始化优化器,调用者必须注册:
   * - 期望的关系表达式集合
   * - 优化规则集合
   * - 调用约定(特征定义)
   * 
   * 使用默认的成本工厂(VolcanoCost.FACTORY)
   * 
   * @param externalContext 外部上下文,可以包含配置信息、连接属性等
   */
  public VolcanoPlanner(Context externalContext) {
    this(null, externalContext);
  }

  /**
   * 创建一个 VolcanoPlanner,并指定成本工厂和外部上下文
   * 
   * @param costFactory 成本工厂,用于创建成本对象,如果为 null 则使用默认的 VolcanoCost.FACTORY
   * @param externalContext 外部上下文,可以包含配置信息、连接属性等
   * 
   * 初始化步骤:
   * 1. 调用父类构造函数,设置成本工厂和外部上下文
   * 2. 创建零成本和无限成本对象
   * 3. 根据日志级别决定是否捕获来源信息
   * 4. 初始化规则队列和驱动器
   */
  @SuppressWarnings("method.invocation.invalid")
  public VolcanoPlanner(@Nullable RelOptCostFactory costFactory,
      @Nullable Context externalContext) {
    super(costFactory == null ? VolcanoCost.FACTORY : costFactory,
        externalContext);
    this.zeroCost = this.costFactory.makeZeroCost(); // 创建零成本对象
    this.infCost = this.costFactory.makeInfiniteCost(); // 创建无限成本对象
    // 如果日志级别为 DEBUG,则捕获来源信息,否则使用空映射表以节省内存
    this.provenanceMap =
        LOGGER.isDebugEnabled() ? new HashMap<>()
            : Util.blackholeMap();
    initRuleQueue(); // 初始化规则队列和驱动器
  }

  /**
   * 初始化规则队列和驱动器
   * 
   * 根据 topDownOpt 标志选择合适的驱动器:
   * - TopDownRuleDriver: 自顶向下优化模式
   * - IterativeRuleDriver: 自底向上迭代优化模式
   * 
   * @EnsuresNonNull("ruleDriver") 确保规则驱动器非空
   */
  @EnsuresNonNull("ruleDriver")
  private void initRuleQueue() {
    if (topDownOpt) {
      ruleDriver = new TopDownRuleDriver(this); // 自顶向下驱动器
    } else {
      ruleDriver = new IterativeRuleDriver(this); // 自底向上迭代驱动器
    }
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * 启用或禁用自顶向下优化模式
   *
   * <p>注意:启用自顶向下优化会自动启用自顶向下的特征传播
   * 
   * 切换优化模式会重新初始化规则队列和驱动器
   * 
   * @param value true 表示启用自顶向下优化,false 表示使用自底向上优化
   */
  public void setTopDownOpt(boolean value) {
    if (topDownOpt == value) {
      return; // 如果模式没有改变,直接返回
    }
    topDownOpt = value; // 更新模式标志
    initRuleQueue(); // 重新初始化规则队列和驱动器
  }

  // 实现 RelOptPlanner 接口
  /**
   * 检查关系表达式是否已注册到优化器中
   * 
   * @param rel 要检查的关系表达式
   * @return true 如果关系表达式已注册,false 否则
   */
  @Override public boolean isRegistered(RelNode rel) {
    return mapRel2Subset.get(rel) != null; // 通过映射表检查是否存在
  }

  /**
   * 设置查询树的根节点
   * 
   * 这是优化流程的起点,根节点代表整个查询
   * 
   * 执行步骤:
   * 1. 注册根节点到优化器
   * 2. 保存原始根节点(用于物化视图匹配)
   * 3. 记录根节点的调用约定
   * 4. 确保根节点包含所有必要的转换器
   * 
   * @param rel 要设置为根的关系表达式
   */
  @Override public void setRoot(RelNode rel) {
    this.root = registerImpl(rel, null); // 注册根节点
    if (this.originalRoot == null) {
      this.originalRoot = rel; // 保存原始根节点
    }

    rootConvention = this.root.getConvention(); // 记录根节点的调用约定
    ensureRootConverters(); // 确保根节点包含所有必要的转换器
  }

  /**
   * 获取当前根节点
   * 
   * @return 根节点,如果未设置则返回 null
   * @Pure 纯函数,不修改状态
   */
  @Pure
  @Override public @Nullable RelNode getRoot() {
    return root;
  }

  /**
   * 获取所有已注册的物化视图列表
   * 
   * @return 物化视图的不可变副本
   */
  @Override public List<RelOptMaterialization> getMaterializations() {
    return ImmutableList.copyOf(materializations);
  }

  /**
   * 添加物化视图到优化器
   * 
   * 物化视图可以用于查询重写,将查询替换为预计算的结果
   * 
   * @param materialization 要添加的物化视图
   */
  @Override public void addMaterialization(
      RelOptMaterialization materialization) {
    materializations.add(materialization);
  }

  /**
   * 添加 lattice 到优化器
   * 
   * Lattice 是一种特殊的物化视图结构,用于星型模式查询优化
   * 
   * @param lattice 要添加的 lattice
   */
  @Override public void addLattice(RelOptLattice lattice) {
    latticeByName.put(lattice.starRelOptTable.getQualifiedName(), lattice);
  }

  /**
   * 根据表获取对应的 lattice
   * 
   * @param table 关系表
   * @return 对应的 lattice,如果不存在则返回 null
   */
  @Override public @Nullable RelOptLattice getLattice(RelOptTable table) {
    return latticeByName.get(table.getQualifiedName());
  }

  /**
   * 注册物化视图和 lattice,用于查询优化
   * 
   * 执行步骤:
   * 1. 检查配置是否启用物化视图
   * 2. 尝试使用物化视图替换查询中的子查询
   * 3. 注册无法直接替换但可能有用的物化视图表
   * 4. 尝试使用 lattice 优化查询
   * 
   * 注意:避免在填充物化视图时使用物化视图,防止循环依赖
   */
  protected void registerMaterializations() {
    // 避免在填充物化视图时使用物化视图!
    final CalciteConnectionConfig config =
        context.unwrap(CalciteConnectionConfig.class);
    if (config == null || !config.materializationsEnabled()) {
      return; // 如果配置禁用物化视图,直接返回
    }

    requireNonNull(root, "root");
    requireNonNull(originalRoot, "originalRoot");

    // 使用物化视图注册关系表达式
    final List<Pair<RelNode, List<RelOptMaterialization>>> materializationUses =
        RelOptMaterializations.useMaterializedViews(originalRoot, materializations);
    for (Pair<RelNode, List<RelOptMaterialization>> use : materializationUses) {
      RelNode rel = use.left;
      Hook.SUB.run(rel);
      registerImpl(rel, root.set); // 注册使用物化视图替换后的关系表达式
    }

    // 注册那些无法在根节点转换中找到替换,但可能有用的物化视图表
    final Set<RelOptMaterialization> applicableMaterializations =
        new HashSet<>(
            RelOptMaterializations.getApplicableMaterializations(
                originalRoot, materializations));
    for (Pair<RelNode, List<RelOptMaterialization>> use : materializationUses) {
      applicableMaterializations.removeAll(use.right); // 移除已使用的物化视图
    }
    for (RelOptMaterialization materialization : applicableMaterializations) {
      RelSubset subset = registerImpl(materialization.queryRel, null); // 注册查询关系表达式
      explorationRoots.add(subset); // 添加到探索根节点
      RelNode tableRel2 =
          RelOptUtil.createCastRel(
              materialization.tableRel,
              materialization.queryRel.getRowType(),
              true);
      registerImpl(tableRel2, subset.set); // 注册表关系表达式
    }

    // 使用 lattice 注册关系表达式
    final List<Pair<RelNode, RelOptLattice>> latticeUses =
        RelOptMaterializations.useLattices(
            originalRoot, ImmutableList.copyOf(latticeByName.values()));
    if (!latticeUses.isEmpty()) {
      RelNode rel = latticeUses.get(0).left;
      Hook.SUB.run(rel);
      registerImpl(rel, root.set); // 注册使用 lattice 优化后的关系表达式
    }
  }

  /**
   * 查找关系表达式所属的等价集合
   * 
   * 如果关系表达式未注册,则返回 null
   * 
   * @param rel 关系表达式
   * @return 关系表达式所属的等价集合,如果未注册则返回 null
   */
  public @Nullable RelSet getSet(RelNode rel) {
    requireNonNull(rel, "rel");
    final RelSubset subset = getSubset(rel); // 获取子集
    if (subset != null) {
      return requireNonNull(subset.set, "subset.set"); // 返回子集所属的等价集合
    }
    return null;
  }

  /**
   * 添加特征定义到优化器
   * 
   * 特征定义定义了关系表达式的特征类型,如调用约定、排序、分布等
   * 
   * @param relTraitDef 要添加的特征定义
   * @return true 如果添加成功(之前不存在),false 如果已存在
   */
  @Override public boolean addRelTraitDef(RelTraitDef relTraitDef) {
    return !traitDefs.contains(relTraitDef) && traitDefs.add(relTraitDef);
  }

  /**
   * 清除所有已注册的特征定义
   */
  @Override public void clearRelTraitDefs() {
    traitDefs.clear();
  }

  /**
   * 获取所有已注册的特征定义列表
   * 
   * @return 特征定义列表
   */
  @Override public List<RelTraitDef> getRelTraitDefs() {
    return traitDefs;
  }

  /**
   * 创建空的特征集合
   * 
   * 空特征集合包含所有已注册特征定义的默认值
   * 
   * @return 包含所有特征默认值的特征集合
   */
  @Override public RelTraitSet emptyTraitSet() {
    RelTraitSet traitSet = super.emptyTraitSet();
    for (RelTraitDef traitDef : traitDefs) {
      if (traitDef.multiple()) {
        // TODO: 重构 RelTraitSet 以允许为任何给定特征提供条目列表
      }
      traitSet = traitSet.plus(traitDef.getDefault()); // 添加特征的默认值
    }
    return traitSet;
  }

  /**
   * 清除优化器的所有状态
   * 
   * 执行步骤:
   * 1. 调用父类的 clear 方法
   * 2. 移除所有规则
   * 3. 清除所有内部数据结构
   * 
   * 作用:重置优化器到初始状态,可以用于重新开始优化
   */
  @Override public void clear() {
    super.clear();
    for (RelOptRule rule : getRules()) {
      removeRule(rule); // 移除所有规则
    }
    this.classOperands.clear(); // 清除操作数映射
    this.allSets.clear(); // 清除所有等价集合
    this.mapDigestToRel.clear(); // 清除摘要映射
    this.mapRel2Subset.clear(); // 清除关系表达式到子集的映射
    this.prunedNodes.clear(); // 清除剪枝节点
    this.ruleDriver.clear(); // 清除规则驱动器
    this.materializations.clear(); // 清除物化视图
    this.latticeByName.clear(); // 清除 lattice
    this.provenanceMap.clear(); // 清除来源映射
  }

  /**
   * 添加优化规则到优化器
   * 
   * 执行步骤:
   * 1. 检查优化器是否已锁定,如果锁定则拒绝添加
   * 2. 调用父类的 addRule 方法
   * 3. 为规则的每个操作数注册入口点
   * 4. 如果是转换规则,注册到特征定义中
   * 
   * @param rule 要添加的优化规则
   * @return true 如果添加成功,false 如果失败(优化器锁定或规则已存在)
   */
  @Override public boolean addRule(RelOptRule rule) {
    if (locked) {
      return false; // 优化器已锁定,拒绝添加规则
    }

    if (!super.addRule(rule)) {
      return false; // 父类添加失败
    }

    final boolean isTransFormRule = rule instanceof TransformationRule;
    // 规则的每个操作数都是规则调用的"入口点"
    // 将每个操作数注册到所有可能匹配它的具体子类
    for (RelOptRuleOperand operand : rule.getOperands()) {
      for (Class<? extends RelNode> subClass
          : subClasses(operand.getMatchedClass())) {
        if (isTransFormRule && PhysicalNode.class.isAssignableFrom(subClass)) {
          continue; // 跳过转换规则匹配物理节点
        }
        classOperands.put(subClass, operand); // 注册操作数
      }
    }

    // 如果这是转换规则,检查它是否操作我们感兴趣的特征类型
    // 如果是,则将规则注册到特征中
    if (rule instanceof ConverterRule) {
      ConverterRule converterRule = (ConverterRule) rule;

      final RelTrait ruleTrait = converterRule.getInTrait();
      final RelTraitDef ruleTraitDef = ruleTrait.getTraitDef();
      if (traitDefs.contains(ruleTraitDef)) {
        ruleTraitDef.registerConverterRule(this, converterRule); // 注册转换规则
      }
    }

    return true;
  }

  /**
   * 从优化器中移除优化规则
   * 
   * 执行步骤:
   * 1. 调用父类的 removeRule 方法
   * 2. 移除所有相关的操作数
   * 3. 如果是转换规则,从特征定义中注销
   * 
   * @param rule 要移除的优化规则
   * @return true 如果移除成功,false 如果规则不存在
   */
  @Override public boolean removeRule(RelOptRule rule) {
    // 移除描述
    if (!super.removeRule(rule)) {
      return false; // 规则不存在
    }

    // 移除操作数
    classOperands.values().removeIf(entry -> entry.getRule().equals(rule));

    // 移除特征映射(特别是转换图中的条目)
    if (rule instanceof ConverterRule) {
      ConverterRule converterRule = (ConverterRule) rule;
      final RelTrait ruleTrait = converterRule.getInTrait();
      final RelTraitDef ruleTraitDef = ruleTrait.getTraitDef();
      if (traitDefs.contains(ruleTraitDef)) {
        ruleTraitDef.deregisterConverterRule(this, converterRule); // 注销转换规则
      }
    }
    return true;
  }

  /**
   * 当遇到新的 RelNode 类时调用
   * 
   * 为新类创建映射,使得该类的实例可以匹配现有的操作数
   * 
   * 执行步骤:
   * 1. 调用父类的 onNewClass 方法
   * 2. 检查是否为物理节点
   * 3. 遍历所有规则,为匹配新类的操作数创建映射
   * 
   * @param node 新的关系节点
   */
  @Override protected void onNewClass(RelNode node) {
    super.onNewClass(node);

    final boolean isPhysical = node instanceof PhysicalNode;
    // 创建映射,使得该类的实例可以匹配现有的操作数
    final Class<? extends RelNode> clazz = node.getClass();
    for (RelOptRule rule : mapDescToRule.values()) {
      if (isPhysical && rule instanceof TransformationRule) {
        continue; // 跳过物理节点匹配转换规则
      }
      for (RelOptRuleOperand operand : rule.getOperands()) {
        if (operand.getMatchedClass().isAssignableFrom(clazz)) {
          classOperands.put(clazz, operand); // 注册操作数映射
        }
      }
    }
  }

  /**
   * 改变关系表达式的特征集合
   * 
   * 通过创建或获取具有指定特征集合的子集来实现特征转换
   * 
   * @param rel 原始关系表达式
   * @param toTraits 目标特征集合
   * @return 具有目标特征集合的关系表达式
   */
  @Override public RelNode changeTraits(final RelNode rel, RelTraitSet toTraits) {
    assert !rel.getTraitSet().equals(toTraits); // 断言特征集合不同
    assert toTraits.allSimple(); // 断言所有特征都是简单的

    RelSubset rel2 = ensureRegistered(rel, null); // 确保关系表达式已注册
    if (rel2.getTraitSet().equals(toTraits)) {
      return rel2; // 如果已经是目标特征集合,直接返回
    }

    return rel2.set.getOrCreateSubset(
        rel.getCluster(), toTraits, true); // 创建或获取具有目标特征集合的子集
  }

  /**
   * 选择委托优化器
   * 
   * VolcanoPlanner 不使用委托,返回自身
   * 
   * @return 自身
   */
  @Override public RelOptPlanner chooseDelegate() {
    return this;
  }

  /**
   * 查找实现查询的最优执行计划
   * 
   * 这是优化器的核心方法,执行完整的优化流程
   * 
   * 执行步骤:
   * 1. 确保根节点包含所有必要的转换器
   * 2. 注册物化视图和 lattice
   * 3. 运行规则驱动器,应用所有适用的规则
   * 4. 如果启用跟踪,转储优化器状态
   * 5. 构建成本最低的执行计划
   * 6. 如果启用调试,输出最优计划和来源信息
   * 
   * @return 找到的最优执行计划
   */
  @Override public RelNode findBestExp() {
    requireNonNull(root, "root");
    ensureRootConverters(); // 确保根节点包含所有必要的转换器
    registerMaterializations(); // 注册物化视图和 lattice

    ruleDriver.drive(); // 运行规则驱动器

    if (LOGGER.isTraceEnabled()) {
      StringWriter sw = new StringWriter();
      final PrintWriter pw = new PrintWriter(sw);
      dump(pw);
      pw.flush();
      LOGGER.info(sw.toString()); // 输出优化器状态
    }
    dumpRuleAttemptsInfo();
    RelNode cheapest = root.buildCheapestPlan(this); // 构建成本最低的执行计划
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Cheapest plan:\n{}", RelOptUtil.toString(cheapest, SqlExplainLevel.ALL_ATTRIBUTES));

      if (!provenanceMap.isEmpty()) {
        LOGGER.debug("Provenance:\n{}", Dumpers.provenance(provenanceMap, cheapest)); // 输出来源信息
      }
    }
    return cheapest;
  }

  /**
   * 检查是否应该取消优化
   * 
   * 如果取消标志被设置,抛出超时异常
   * 
   * @throws VolcanoTimeoutException 如果优化被取消
   */
  @Override public void checkCancel() {
    if (cancelFlag.get()) {
      throw new VolcanoTimeoutException();
    }
  }

  /**
   * 确保根子集包含到其等价集合中所有其他子集的转换器
   *
   * <p>这样优化器会尝试找到那些其他子集的廉价实现,然后可以转换为根节点
   * 这是计划中唯一需要显式转换器的地方;在其他地方,消费者会要求特定约定的结果
   * 但根节点没有消费者
   * 
   * 执行步骤:
   * 1. 收集现有的转换器
   * 2. 遍历根节点的等价集合中的所有子集
   * 3. 对于每个子集,计算与根节点的特征差异
   * 4. 如果差异只有一个特征,创建转换器
   * 
   * @RequiresNonNull("root") 确保根节点非空
   */
  @RequiresNonNull("root")
  void ensureRootConverters() {
    final Set<RelSubset> subsets = new HashSet<>();
    for (RelNode rel : root.getRels()) {
      if (rel instanceof AbstractConverter && !topDownOpt) {
        subsets.add((RelSubset) ((AbstractConverter) rel).getInput()); // 收集现有转换器
      }
    }
    for (RelSubset subset : root.set.subsets) {
      final ImmutableList<RelTrait> difference =
          root.getTraitSet().difference(subset.getTraitSet()); // 计算特征差异
      if (difference.size() == 1 && subsets.add(subset)) {
        register(
            new AbstractConverter(subset.getCluster(), subset,
                difference.get(0).getTraitDef(), root.getTraitSet()), // 创建转换器
            root);
      }
    }
  }

  /**
   * 注册关系表达式到优化器
   * 
   * 执行步骤:
   * 1. 断言关系表达式未注册
   * 2. 如果提供了等价关系表达式,验证行类型并获取其等价集合
   * 3. 调用 registerImpl 实际注册
   * 
   * @param rel 要注册的关系表达式
   * @param equivRel 等价关系表达式,可以为 null
   * @return 注册后的子集
   */
  @Override public RelSubset register(
      RelNode rel,
      @Nullable RelNode equivRel) {
    assert !isRegistered(rel) : "pre: isRegistered(rel)";
    final RelSet set;
    if (equivRel == null) {
      set = null; // 没有等价关系表达式
    } else {
      final RelDataType relType = rel.getRowType();
      final RelDataType equivRelType = equivRel.getRowType();
      if (!RelOptUtil.areRowTypesEqual(relType,
          equivRelType, false)) {
        throw new IllegalArgumentException(
            RelOptUtil.getFullTypeDifferenceString("rel rowtype", relType,
                "equiv rowtype", equivRelType)); // 行类型不匹配
      }
      equivRel = ensureRegistered(equivRel, null); // 确保等价关系表达式已注册
      set = getSet(equivRel); // 获取等价集合
    }
    return registerImpl(rel, set); // 实际注册
  }

  /**
   * 确保关系表达式已注册,如果未注册则注册
   * 
   * 执行步骤:
   * 1. 检查关系表达式是否已注册
   * 2. 如果已注册且提供了等价关系表达式,合并等价集合
   * 3. 规范化子集
   * 4. 如果未注册,则注册
   * 5. 如果启用调试,验证树的正确性
   * 
   * @param rel 要确保注册的关系表达式
   * @param equivRel 等价关系表达式,可以为 null
   * @return 注册后的子集
   */
  @Override public RelSubset ensureRegistered(RelNode rel, @Nullable RelNode equivRel) {
    RelSubset result;
    final RelSubset subset = getSubset(rel);
    if (subset != null) {
      if (equivRel != null) {
        final RelSubset equivSubset = getSubsetNonNull(equivRel);
        if (subset.set != equivSubset.set) {
          merge(equivSubset.set, subset.set); // 合并等价集合
        }
      }
      result = canonize(subset); // 规范化子集
    } else {
      result = register(rel, equivRel); // 注册关系表达式
    }

    // 检查树的正确性会显著减慢优化速度
    // 只在日志级别为 debug 或更细时执行
    if (LOGGER.isDebugEnabled()) {
      assert isValid(Litmus.THROW);
    }

    return result;
  }

  /**
   * 检查内部一致性
   * 
   * 验证优化器的内部状态是否正确,包括:
   * - 等价集合没有被合并
   * - 子集属于正确的等价集合
   * - 最优关系表达式有效
   * - 最优成本是最新的
   * - 没有关系表达式的成本低于最优成本
   * 
   * @param litmus 用于报告验证结果的对象
   * @return true 如果验证通过,false 否则
   */
  protected boolean isValid(Litmus litmus) {
    RelNode root = getRoot();
    if (root == null) {
      return true; // 没有根节点,认为有效
    }

    RelMetadataQuery metaQuery = root.getCluster().getMetadataQuerySupplier().get();
    for (RelSet set : allSets) {
      if (set.equivalentSet != null) {
        return litmus.fail("set [{}] has been merged: it should not be in the list", set); // 等价集合已被合并
      }
      for (RelSubset subset : set.subsets) {
        if (subset.set != set) {
          return litmus.fail("subset [{}] is in wrong set [{}]",
              subset, set); // 子集属于错误的等价集合
        }

        if (subset.best != null) {

          // 确保最优关系表达式有效
          if (!subset.set.rels.contains(subset.best)) {
            return litmus.fail("RelSubset [{}] does not contain its best RelNode [{}]",
                    subset, subset.best); // 最优关系表达式不在集合中
          }

          // 确保最优成本是最新的
          try {
            RelOptCost bestCost = getCostOrInfinite(subset.best, metaQuery);
            if (!subset.bestCost.equals(bestCost)) {
              return litmus.fail("RelSubset [" + subset
                      + "] has wrong best cost "
                      + subset.bestCost + ". Correct cost is " + bestCost); // 最优成本不正确
            }
          } catch (CyclicMetadataException e) {
            // 忽略循环元数据异常
          }
        }

        for (RelNode rel : subset.getRels()) {
          try {
            RelOptCost relCost = getCost(rel, metaQuery);
            if (relCost != null && relCost.isLt(subset.bestCost)) {
              return litmus.fail("rel [{}] has lower cost {} than "
                      + "best cost {} of subset [{}]",
                      rel, relCost, subset.bestCost, subset); // 存在成本低于最优成本的关系表达式
            }
          } catch (CyclicMetadataException e) {
            // 忽略循环元数据异常
          }
        }
      }
    }
    return litmus.succeed();
  }

  /**
   * 注册抽象关系规则
   * 
   * 注册标准的抽象关系规则,如投影、过滤、连接等
   */
  public void registerAbstractRelationalRules() {
    RelOptUtil.registerAbstractRelationalRules(this);
  }

  /**
   * 注册 schema 到优化器
   * 
   * Schema 可以提供表和规则
   * 
   * @param schema 要注册的 schema
   */
  @Override public void registerSchema(RelOptSchema schema) {
    if (registeredSchemas.add(schema)) {
      try {
        schema.registerRules(this); // 注册 schema 提供的规则
      } catch (Exception e) {
        throw new AssertionError("While registering schema " + schema, e);
      }
    }
  }

  /**
   * 设置优化器是否将具有 Convention.NONE 的关系节点视为具有无限成本
   *
   * Convention.NONE 表示逻辑节点,尚未转换为物理实现
   * 
   * @param infinite true 表示将 none 约定的节点视为无限成本,false 则不
   */
  public void setNoneConventionHasInfiniteCost(boolean infinite) {
    this.noneConventionHasInfiniteCost = infinite;
  }

  /**
   * 返回关系表达式的成本,如果成本未知则返回无限成本
   *
   * @param rel 关系表达式
   * @param mq 元数据查询对象
   * @return 关系表达式的成本,如果成本未知则返回无限成本
   * @see org.apache.calcite.plan.volcano.RelSubset#bestCost
   */
  private RelOptCost getCostOrInfinite(RelNode rel, RelMetadataQuery mq) {
    RelOptCost cost = getCost(rel, mq);
    return cost == null ? infCost : cost; // 成本未知时返回无限成本
  }

  /**
   * 计算关系表达式的成本
   * 
   * 执行步骤:
   * 1. 如果是子集,返回其最优成本
   * 2. 如果是 none 约定且设置为无限成本,返回无限成本
   * 3. 获取非累积成本
   * 4. 确保成本为正数
   * 5. 累加所有输入的成本
   * 
   * @param rel 关系表达式
   * @param mq 元数据查询对象
   * @return 关系表达式的成本,如果无法计算则返回 null
   */
  @Override public @Nullable RelOptCost getCost(RelNode rel, RelMetadataQuery mq) {
    requireNonNull(rel, "rel");
    if (rel instanceof RelSubset) {
      return ((RelSubset) rel).bestCost; // 子集返回最优成本
    }
    if (noneConventionHasInfiniteCost
        && rel.getTraitSet().getTrait(ConventionTraitDef.INSTANCE) == Convention.NONE) {
      return costFactory.makeInfiniteCost(); // none 约定返回无限成本
    }
    RelOptCost cost = mq.getNonCumulativeCost(rel); // 获取非累积成本
    if (cost == null) {
      return null; // 无法计算成本
    }
    if (!zeroCost.isLt(cost)) {
      // 成本必须为正数,所以调整它
      cost = costFactory.makeTinyCost();
    }
    for (RelNode input : rel.getInputs()) {
      RelOptCost inputCost = getCost(input, mq); // 递归计算输入成本
      if (inputCost == null) {
        return null; // 无法计算输入成本
      }
      cost = cost.plus(inputCost); // 累加输入成本
    }
    return cost;
  }

  /**
   * 返回关系表达式所属的子集
   *
   * @param rel 关系表达式
   * @return 所属的子集,如果未注册则返回 null
   */
  public @Nullable RelSubset getSubset(RelNode rel) {
    requireNonNull(rel, "rel");
    if (rel instanceof RelSubset) {
      return (RelSubset) rel; // 如果本身就是子集,直接返回
    } else {
      return mapRel2Subset.get(rel); // 从映射表中查找
    }
  }

  /**
   * 返回关系表达式所属的子集,如果未找到则抛出异常
   *
   * @param rel 关系表达式
   * @return 所属的子集
   * @throws AssertionError 如果未找到子集
   */
  @API(since = "1.26", status = API.Status.EXPERIMENTAL)
  public RelSubset getSubsetNonNull(RelNode rel) {
    return requireNonNull(getSubset(rel), () -> "Subset is not found for " + rel);
  }

  /**
   * 返回具有指定特征集合的关系表达式所属的子集
   * 
   * @param rel 关系表达式
   * @param traits 目标特征集合
   * @return 具有指定特征集合的子集,如果不存在则返回 null
   */
  public @Nullable RelSubset getSubset(RelNode rel, RelTraitSet traits) {
    if ((rel instanceof RelSubset) && rel.getTraitSet().equals(traits)) {
      return (RelSubset) rel; // 如果本身就是子集且特征匹配,直接返回
    }
    RelSet set = getSet(rel);
    if (set == null) {
      return null; // 未注册
    }
    return set.getSubset(traits); // 从等价集合中查找
  }

  /**
   * 使用转换器改变关系表达式的特征集合
   * 
   * 通过逐步应用特征转换规则来改变特征集合
   * 
   * 执行步骤:
   * 1. 获取源特征集合和目标特征集合
   * 2. 遍历每个特征,逐步转换
   * 3. 如果特征已经满足要求,跳过
   * 4. 否则使用特征定义的转换方法进行转换
   * 5. 注册转换后的关系表达式
   * 
   * 注意:特征可能建立在另一个特征之上,例如排序特征通常在分布特征之后
   * 因为分布会破坏排序,所以转换时使用 fromTraits 作为前一个转换的 RelNode 的特征
   * 
   * @param rel 原始关系表达式
   * @param toTraits 目标特征集合
   * @return 转换后的关系表达式,如果无法转换则返回 null
   */
@Nullable RelNode changeTraitsUsingConverters(
      RelNode rel,
      RelTraitSet toTraits) {
    final RelTraitSet fromTraits = rel.getTraitSet(); // 获取源特征集合

    assert fromTraits.size() >= toTraits.size(); // 断言源特征数量大于等于目标特征数量

    final boolean allowInfiniteCostConverters =
        CalciteSystemProperty.ALLOW_INFINITE_COST_CONVERTERS.value(); // 是否允许无限成本转换器

    // 特征可能建立在另一个特征之上...例如排序特征通常在分布特征之后
    // 因为分布会破坏排序;所以在下面的转换中我们使用 fromTraits 作为
    // 刚刚转换的 RelNode 的特征
    // 此外,toTraits 可能比 fromTraits 有更少的特征,多余的特征将保持不变
    // 最后,toTraits 中的任何 null 条目都会被忽略
    RelNode converted = rel;
    for (int i = 0; (converted != null) && (i < toTraits.size()); i++) {
      RelTrait fromTrait = converted.getTraitSet().getTrait(i); // 获取当前特征
      final RelTraitDef traitDef = fromTrait.getTraitDef(); // 获取特征定义
      RelTrait toTrait = toTraits.getTrait(i); // 获取目标特征

      if (toTrait == null) {
        continue; // 目标特征为 null,跳过
      }

      assert traitDef == toTrait.getTraitDef(); // 断言特征定义相同
      if (fromTrait.satisfies(toTrait)) {
        // 无需转换,已经正确
        continue;
      }

      // 使用特征定义的转换方法进行转换
      RelNode convertedRel =
          traitDef.convert(
              this,
              converted,
              toTrait,
              allowInfiniteCostConverters);
      if (convertedRel != null) {
        assert castNonNull(convertedRel.getTraitSet().getTrait(traitDef)).satisfies(toTrait); // 断言转换后的特征满足要求
        register(convertedRel, converted); // 注册转换后的关系表达式
      }

      converted = convertedRel; // 更新为转换后的关系表达式
    }

    // 确保最终转换的特征集合包含所需的所有特征
    if (converted != null) {
      assert converted.getTraitSet().satisfies(toTraits);
    }

    return converted;
  }

  /**
   * 剪枝关系节点
   * 
   * 将关系节点标记为剪枝,优化器将忽略使用该节点的规则调用
   * 
   * @param rel 要剪枝的关系节点
   */
  @Override public void prune(RelNode rel) {
    prunedNodes.add(rel);
  }

  /**
   * 将 VolcanoPlanner 的内部状态转储到写入器
   *
   * 输出内容包括:
   * - 根节点
   * - 原始关系表达式
   * - 所有等价集合(如果启用)
   * - Graphviz 格式的可视化图(如果启用)
   *
   * @param pw 打印写入器
   * @see #normalizePlan(String)
   */
  public void dump(PrintWriter pw) {
    pw.println("Root: " + root); // 输出根节点
    pw.println("Original rel:"); // 输出原始关系表达式

    if (originalRoot != null) {
      originalRoot.explain(
          new RelWriterImpl(pw, SqlExplainLevel.ALL_ATTRIBUTES, false));
    }

    try {
      if (CalciteSystemProperty.DUMP_SETS.value()) {
        pw.println();
        pw.println("Sets:"); // 输出所有等价集合
        Dumpers.dumpSets(this, pw);
      }
      if (CalciteSystemProperty.DUMP_GRAPHVIZ.value()) {
        pw.println();
        pw.println("Graphviz:"); // 输出 Graphviz 格式的可视化图
        Dumpers.dumpGraphviz(this, pw);
      }
    } catch (Exception | AssertionError e) {
      pw.println("Error when dumping plan state: \n"
          + e);
    }
  }

  /**
   * 将优化器状态转换为 Graphviz DOT 格式的字符串
   * 
   * @return Graphviz DOT 格式的字符串
   */
  public String toDot() {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    Dumpers.dumpGraphviz(this, pw);
    pw.flush();
    return sw.toString();
  }

  /**

     * 重新计算关系表达式的摘要(digest)

     *

     * <p>由于关系表达式的摘要包含其子节点的标识符,当子节点被重命名时需要调用此方法

     * 例如,当子节点的集合与另一个集合合并时

     *

     * 执行步骤:

     * 1. 修复输入节点(更新为规范化的子集)

     * 2. 获取新的摘要

     * 3. 检查是否存在等价的关系表达式

     * 4. 如果存在,将当前关系表达式替换为等价关系表达式

     * 5. 更新子集和等价集合

     * 

     * @param rel 关系表达式

     */

    void rename(RelNode rel) {

      String oldDigest = "";

      if (LOGGER.isTraceEnabled()) {

        oldDigest = rel.getDigest(); // 记录旧摘要

      }

      if (fixUpInputs(rel)) { // 修复输入节点

        final RelDigest newDigest = rel.getRelDigest(); // 获取新摘要

        LOGGER.trace("Rename #{} from '{}' to '{}'", rel.getId(), oldDigest, newDigest);

        final RelNode equivRel = mapDigestToRel.put(newDigest, rel); // 尝试将新摘要映射到当前关系表达式

        if (equivRel != null) {

          assert equivRel != rel; // 断言不是同一个关系表达式

  

          // 已经存在一个具有相同名称的等价关系表达式,我们刚刚把它踢出去了

          // 把它放回去,忘记 'rel'

          LOGGER.trace("After renaming rel#{} it is now equivalent to rel#{}",

              rel.getId(), equivRel.getId());

  

          mapDigestToRel.put(newDigest, equivRel); // 恢复等价关系表达式

          checkPruned(equivRel, rel); // 检查剪枝状态

  

          RelSubset equivRelSubset = getSubsetNonNull(equivRel); // 获取等价关系表达式的子集

  

          // 从子节点移除反向链接

          for (RelNode input : rel.getInputs()) {

            ((RelSubset) input).set.parents.remove(rel);

          }

  

  

          // 从子集中移除 rel(这可能会使子集为空,但如果是这样,

          // 在集合合并时会处理)

          final RelSubset subset =

              requireNonNull(mapRel2Subset.put(rel, equivRelSubset));

          boolean existed = subset.set.rels.remove(rel);

          checkArgument(existed, "rel was not known to its set");

          final RelSubset equivSubset = getSubsetNonNull(equivRel);

          for (RelSubset s : subset.set.subsets) {

            if (s.best == rel) {

              s.best = equivRel; // 更新最优关系表达式

              // 传播成本改进,因为这可能会改变子集的最优成本

              propagateCostImprovements(equivRel);

            }

          }

  

          if (equivSubset != subset) {

            // 等价关系表达式在不同的子集中,因此集合是等价的

            assert equivSubset.getTraitSet().equals(

                subset.getTraitSet());

            assert equivSubset.set != subset.set;

            merge(equivSubset.set, subset.set); // 合并等价集合

          }

        }

      }

    }

  

    /**

     * 检查关系表达式是否使任何子集更便宜,如果是,则将新成本传播到父关系节点

     *

     * 执行步骤:

     * 1. 初始化传播映射和优先队列

     * 2. 将初始关系表达式加入队列

     * 3. 从队列中取出关系表达式

     * 4. 对于每个子集,检查是否需要更新最优成本

     * 5. 如果需要更新,更新子集的最优成本和最优关系表达式

     * 6. 将父节点加入队列进行传播

     * 

     * 注意:理论上成本应该变小,但根据测试,成本有时会增加,因此总是执行更新

     *

     * @param rel 成本已改进的关系表达式

     */

    void propagateCostImprovements(RelNode rel) {

      RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询

      Map<RelNode, RelOptCost> propagateRels = new HashMap<>(); // 传播映射:关系表达式 -> 成本

      PriorityQueue<RelNode> propagateHeap = new PriorityQueue<>((o1, o2) -> { // 优先队列,按成本排序

        RelOptCost c1 = propagateRels.get(o1);

        RelOptCost c2 = propagateRels.get(o2);

        if (c1 == null) {

          return c2 == null ? 0 : -1;

        }

        if (c2 == null) {

          return 1;

        }

        if (c1.equals(c2)) {

          return 0;

        } else if (c1.isLt(c2)) {

          return -1;

        }

        return 1;

      });

      propagateRels.put(rel, getCostOrInfinite(rel, mq)); // 将初始关系表达式加入映射

      propagateHeap.offer(rel); // 将初始关系表达式加入队列

  

      RelNode relNode;

      while ((relNode = propagateHeap.poll()) != null) { // 从队列中取出关系表达式

        RelOptCost cost = requireNonNull(propagateRels.get(relNode), "propagateRels.get(relNode)");

  

        for (RelSubset subset : getSubsetNonNull(relNode).set.subsets) {

          if (!relNode.getTraitSet().satisfies(subset.getTraitSet())) {

            continue; // 特征不匹配,跳过

          }

  

          // 当我们找到更便宜的关系表达式时,更新子集的最优关系表达式和最优成本

          if (relNode != subset.best && !cost.isLt(subset.bestCost)) {

            continue; // 不是最优且成本不更低,跳过

          }

  

          // 当检测到变化时更新 RelNode 的成本

  

          // 更新的原因是当 RelSet 中的一个子集找到成本更低的 RelNode 时,

          // 需要更新子集的父节点以具有最优 RelNode 和最优成本

          // 理论上,这个成本应该变得更小

          // 然而,根据 JdbcAdapterTest 中添加的 SQL {@link testVolcanoPlannerInternalValid},

          // 观察到 RelNode 的成本有时会增加

          // 因此,执行更新

          if (relNode == subset.best && cost.equals(subset.bestCost)) {

            continue; // 成本没有变化,跳过

          }

  

          subset.timestamp++; // 更新时间戳

          LOGGER.trace("Subset cost changed: subset [{}] cost was {} now {}",

              subset, subset.bestCost, cost);

  

          subset.bestCost = cost; // 更新最优成本

          subset.best = relNode; // 更新最优关系表达式

          // 由于最优关系表达式已更改,应清除此子集的缓存元数据

          mq.clearCache(subset);

  

          for (RelNode parent : subset.getParents()) {

            mq.clearCache(parent); // 清除父节点的缓存

            RelOptCost newCost = getCostOrInfinite(parent, mq); // 计算父节点的新成本

            RelOptCost existingCost = propagateRels.get(parent);

            if (existingCost == null || newCost.isLt(existingCost)) {

              propagateRels.put(parent, newCost); // 更新父节点的成本

              if (existingCost != null) {

                // 成本降低,强制堆调整其顺序

                propagateHeap.remove(parent);

              }

              propagateHeap.offer(parent); // 将父节点加入队列

            }

          }

        }

      }

    }

  /**
   * 将已经注册的关系表达式注册到新的等价集合中
   *
   * 执行步骤:
   * 1. 检查是否存在等价的关系表达式
   * 2. 如果存在,检查剪枝状态
   * 3. 如果不存在,将关系表达式添加到正确的集合和子集
   * 
   * 使用场景:当关系表达式的子节点被发现与另一个集合等价时,可能需要重新注册
   *
   * @param set 目标等价集合
   * @param rel 关系表达式
   */
  void reregister(
      RelSet set,
      RelNode rel) {
    // 是否存在等价的关系表达式?(这可能刚刚发生,因为关系表达式的子节点
    // 刚刚被发现与另一个集合等价)
    RelNode equivRel = mapDigestToRel.get(rel.getRelDigest());
    if (equivRel != null && equivRel != rel) {
      assert equivRel.getClass() == rel.getClass(); // 断言类相同
      assert equivRel.getTraitSet().equals(rel.getTraitSet()); // 断言特征集合相同

      checkPruned(equivRel, rel); // 检查剪枝状态
      return;
    }

    // 将关系表达式添加到正确的集合和子集中
    if (!prunedNodes.contains(rel)) {
      addRelToSet(rel, set); // 添加到集合
    }
  }

  /**
   * 如果后者(与关系节点相同)已经被剪枝,则剪枝关系节点
   * 
   * 作用:确保等价的关系表达式具有相同的剪枝状态
   * 
   * @param rel 关系表达式
   * @param duplicateRel 等价的关系表达式
   */
  private void checkPruned(RelNode rel, RelNode duplicateRel) {
    if (prunedNodes.contains(duplicateRel)) {
      prunedNodes.add(rel); // 如果等价关系表达式已被剪枝,则剪枝当前关系表达式
    }
  }

  /**
   * 在根节点与另一个子集合并的情况下,找到新的根子集
   * 
   * @RequiresNonNull("root") 确保根节点非空
   */
  @RequiresNonNull("root")
  void canonize() {
    root = canonize(root); // 规范化根节点
  }

  /**
   * 如果子集有一个或多个等价子集(由于集合与另一个集合合并),返回等价类的领导者子集
   *
   * 执行步骤:
   * 1. 检查集合是否有等价集合
   * 2. 如果没有,返回子集本身
   * 3. 如果有,遍历等价链找到根集合
   * 4. 返回根集合中具有相同特征的子集
   * 
   * @param subset 子集
   * @return 子集等价类的领导者
   */
  private static RelSubset canonize(final RelSubset subset) {
    RelSet set = subset.set;
    if (set.equivalentSet == null) {
      return subset; // 没有等价集合,返回子集本身
    }
    do {
      set = set.equivalentSet; // 遍历等价链
    } while (set.equivalentSet != null);
    return set.getOrCreateSubset(
        subset.getCluster(), subset.getTraitSet(), subset.isRequired()); // 返回根集合中的子集
  }

  /**
   * 触发关系表达式匹配的所有规则
   *
   * 执行步骤:
   * 1. 获取关系表达式类对应的所有操作数
   * 2. 对于每个操作数,检查是否匹配关系表达式
   * 3. 如果匹配,创建规则调用并执行匹配
   * 
   * @param rel 刚刚创建的关系表达式(或者可能来自队列)
   */
  void fireRules(RelNode rel) {
    for (RelOptRuleOperand operand : classOperands.get(rel.getClass())) { // 获取所有操作数
      if (operand.matches(rel)) { // 检查是否匹配
        final VolcanoRuleCall ruleCall;
        ruleCall = new DeferringRuleCall(this, operand); // 创建延迟规则调用
        ruleCall.match(rel); // 执行匹配
      }
    }
  }

  /**
   * 修复关系表达式的输入节点
   * 
   * 将输入节点更新为规范化的子集,处理集合合并后的情况
   * 
   * 执行步骤:
   * 1. 遍历所有输入节点
   * 2. 将每个输入节点规范化
   * 3. 如果规范化后的子集不同,更新父节点关系
   * 4. 如果有变化,重新计算摘要
   * 
   * @param rel 关系表达式
   * @return true 如果有变化,false 否则
   */
  private boolean fixUpInputs(RelNode rel) {
    List<RelNode> inputs = rel.getInputs();
    List<RelNode> newInputs = new ArrayList<>(inputs.size());
    int changeCount = 0;
    for (RelNode input : inputs) {
      assert input instanceof RelSubset;
      final RelSubset subset = (RelSubset) input;
      RelSubset newSubset = canonize(subset); // 规范化子集
      newInputs.add(newSubset);
      if (newSubset != subset) {
        if (subset.set != newSubset.set) {
          subset.set.parents.remove(rel); // 从旧集合移除父节点
          newSubset.set.parents.add(rel); // 添加到新集合
        }
        changeCount++; // 记录变化
      }
    }

    if (changeCount > 0) {
      RelMdUtil.clearCache(rel); // 清除缓存
      RelNode removed = mapDigestToRel.remove(rel.getRelDigest());
      assert removed == rel;
      for (int i = 0; i < inputs.size(); i++) {
        rel.replaceInput(i, newInputs.get(i)); // 替换输入节点
      }
      rel.recomputeDigest(); // 重新计算摘要
      return true;
    }
    return false;
  }

  /**
   * 合并两个等价集合
   * 
   * 执行步骤:
   * 1. 找到每个集合的等价树根
   * 2. 如果已经等价,直接返回
   * 3. 决定合并方向(将较小的合并到较大的)
   * 4. 执行合并
   * 5. 如果合并的集合是根集合,更新根节点
   * 6. 通知规则驱动器
   * 
   * 合并策略:
   * - 优先将子集合合并到父集合
   * - 如果没有父子关系,将较小的合并到较大的
   * - 较小是指:父节点少、关系表达式少、ID 更大(更年轻)
   * 
   * @param set1 第一个等价集合
   * @param set2 第二个等价集合
   * @return 合并后的等价集合
   */
  private RelSet merge(RelSet set1, RelSet set2) {
    assert set1 != set2 : "pre: set1 != set2";

    // 找到每个集合的等价树根
    set1 = equivRoot(set1);
    set2 = equivRoot(set2);

    // 如果 set1 和 set2 已经等价,无需操作
    if (set2 == set1) {
      return set1;
    }

    // 如果必要,交换集合,以便我们总是将较新的集合合并到较旧的集合
    // 或将父集合合并到子集合
    final boolean swap;
    final Set<RelSet> childrenOf1 = set1.getChildSets(this);
    final Set<RelSet> childrenOf2 = set2.getChildSets(this);
    final boolean set2IsParentOfSet1 = childrenOf2.contains(set1);
    final boolean set1IsParentOfSet2 = childrenOf1.contains(set2);
    if (set2IsParentOfSet1 && set1IsParentOfSet2) {
      // 存在长度为 1 的循环;每个集合都是另一个的(直接)父节点
      // 交换以便我们合并到较大、较旧的集合
      swap = isSmaller(set1, set2);
    } else if (set2IsParentOfSet1) {
      // set2 是 set1 的父节点。不交换。我们想要将 set2 合并到 set1
      swap = false;
    } else if (set1IsParentOfSet2) {
      // set1 是 set2 的父节点。交换,以便我们将 set1 合并到 set2
      swap = true;
    } else {
      // 都不是对方的父节点
      // 交换以便我们合并到较大、较旧的集合
      swap = isSmaller(set1, set2);
    }
    if (swap) {
      RelSet t = set1;
      set1 = set2;
      set2 = t;
    }

    // 合并
    set1.mergeWith(this, set2);

    if (root == null) {
      throw new IllegalStateException("root must not be null");
    }

    // 合并的集合是根集合吗?如果是,结果是新的根
    if (set2 == getSet(root)) {
      root =
          set1.getOrCreateSubset(root.getCluster(), root.getTraitSet(),
              root.isRequired()); // 更新根节点
      ensureRootConverters();
    }

    if (ruleDriver != null) {
      ruleDriver.onSetMerged(set1); // 通知规则驱动器
    }
    return set1;
  }

  /**
   * 返回 set1 是否比 set2 不受欢迎(或更小,或更年轻)
   * 
   * 如果是,将 set1 合并到 set2 比将 set2 合并到 set1 更高效
   * 
   * 比较优先级:
   * 1. 父节点数量(越少越不受欢迎)
   * 2. 关系表达式数量(越少越小)
   * 3. ID(越大越年轻)
   * 
   * @param set1 第一个等价集合
   * @param set2 第二个等价集合
   * @return true 如果 set1 比 set2 不受欢迎/更小/更年轻
   */
  private static boolean isSmaller(RelSet set1, RelSet set2) {
    if (set1.parents.size() != set2.parents.size()) {
      return set1.parents.size() < set2.parents.size(); // true 如果 set1 比 set2 不受欢迎
    }
    if (set1.rels.size() != set2.rels.size()) {
      return set1.rels.size() < set2.rels.size(); // true 如果 set1 比 set2 小
    }
    return set1.id > set2.id; // true 如果 set1 比 set2 年轻
  }

  /**
   * 查找等价集合的根
   * 
   * 使用双指针法检测循环:
   * - s 指针每次移动一步
   * - p 指针每次移动两步
   * - 如果存在循环,两个指针会相遇
   * 
   * @param s 等价集合
   * @return 等价集合的根
   */
  static RelSet equivRoot(RelSet s) {
    RelSet p = s; // 以两倍速度迭代,以检测循环
    while (s.equivalentSet != null) {
      p = forward2(s, p); // p 移动两步
      s = s.equivalentSet; // s 移动一步
    }
    return s;
  }

  /**
   * 向前移动两个链接,在每个位置检查循环
   * 
   * @param s 慢指针
   * @param p 快指针
   * @return 快指针的新位置
   */
  private static @Nullable RelSet forward2(RelSet s, @Nullable RelSet p) {
    p = forward1(s, p); // 移动一步
    p = forward1(s, p); // 再移动一步
    return p;
  }

  /**
   * 向前移动一个链接,检查循环
   * 
   * @param s 慢指针
   * @param p 快指针
   * @return 快指针的新位置
   * @throws AssertionError 如果检测到循环
   */
  private static @Nullable RelSet forward1(RelSet s, @Nullable RelSet p) {
    if (p != null) {
      p = p.equivalentSet; // 移动一步
      if (p == s) {
        throw new AssertionError("cycle in equivalence tree"); // 检测到循环
      }
    }
    return p;
  }

  /**
   * 注册新的关系表达式并排队规则匹配
   * 
   * 如果 set 不为 null,将表达式作为该等价集合的一部分
   * 如果已经注册了相同的表达式,则无需注册此表达式,也不应排队规则匹配
   *
   * 执行步骤:
   * 1. 如果是子集,调用 registerSubset
   * 2. 验证关系表达式未注册且属于当前优化器
   * 3. 验证关系表达式实现了调用约定要求的接口
   * 4. 验证特征数量正确
   * 5. 确保子表达式已注册
   * 6. 记录来源信息
   * 7. 检查是否存在等价的表达式
   * 8. 处理转换器特殊情况
   * 9. 创建或获取等价集合
   * 10. 将关系表达式添加到集合中
   * 11. 触发规则匹配
   *
   * @param rel 要注册的关系表达式,必须是 RelSubset 或未注册的 RelNode
   * @param set 关系表达式所属的集合,可以为 null
   * @return 等价集合
   */
  private RelSubset registerImpl(
      RelNode rel,
      @Nullable RelSet set) {
    if (rel instanceof RelSubset) {
      return registerSubset(set, (RelSubset) rel); // 如果是子集,调用 registerSubset
    }

    assert !isRegistered(rel) : "already been registered: " + rel; // 断言未注册
    if (rel.getCluster().getPlanner() != this) {
      throw new AssertionError("Relational expression " + rel
          + " belongs to a different planner than is currently being used."); // 验证属于当前优化器
    }

    // 现在是确保关系表达式实现其调用约定所需接口的好时机
    final RelTraitSet traits = rel.getTraitSet();
    final Convention convention =
        requireNonNull(traits.getTrait(ConventionTraitDef.INSTANCE));
    if (!convention.getInterface().isInstance(rel)
        && !(rel instanceof Converter)) {
      throw new AssertionError("Relational expression " + rel
          + " has calling-convention " + convention
          + " but does not implement the required interface '"
          + convention.getInterface() + "' of that convention"); // 验证实现了接口
    }
    if (traits.size() != traitDefs.size()) {
      throw new AssertionError("Relational expression " + rel
          + " does not have the correct number of traits: " + traits.size()
          + " != " + traitDefs.size()); // 验证特征数量
    }

    // 确保子表达式已注册
    rel = rel.onRegister(this);

    // 记录来源信息(规则调用可能为 null)
    final VolcanoRuleCall ruleCall = ruleCallStack.peek();
    if (ruleCall == null) {
      provenanceMap.put(rel, Provenance.EMPTY); // 无来源
    } else {
      provenanceMap.put(
          rel,
          new RuleProvenance(
              ruleCall.rule,
              ImmutableList.copyOf(ruleCall.rels),
              ruleCall.id)); // 来自规则调用
    }

    // 如果与现有表达式等价,返回等价表达式所属的集合
    RelDigest digest = rel.getRelDigest();
    RelNode equivExp = mapDigestToRel.get(digest);
    if (equivExp == null) {
      // 不做任何事
    } else if (equivExp == rel) {
      // 同一个 rel 已经注册,返回其子集
      return getSubsetNonNull(equivExp);
    } else {
      if (!RelOptUtil.areRowTypesEqual(equivExp.getRowType(),
          rel.getRowType(), false)) {
        throw new IllegalArgumentException(
            RelOptUtil.getFullTypeDifferenceString("equiv rowtype",
                equivExp.getRowType(), "rel rowtype", rel.getRowType())); // 行类型不匹配
      }
      checkPruned(equivExp, rel); // 检查剪枝状态

      RelSet equivSet = getSet(equivExp);
      if (equivSet != null) {
        LOGGER.trace(
            "Register: rel#{} is equivalent to {}", rel.getId(), equivExp);
        return registerSubset(set, getSubsetNonNull(equivExp)); // 返回等价子集
      }
    }

    // 转换器与其子节点在同一个集合中
    if (rel instanceof Converter) {
      final RelNode input = ((Converter) rel).getInput();
      final RelSet childSet = castNonNull(getSet(input)); // 获取子节点的集合
      if ((set != null)
          && (set != childSet)
          && (set.equivalentSet == null)) {
        LOGGER.trace(
            "Register #{} {} (and merge sets, because it is a conversion)",
            rel.getId(), rel.getRelDigest());
        merge(set, childSet); // 合并集合

        // 在合并过程中,子集合可能已经改变,由于我们尚未注册,
        // 我们不会收到通知。因此检查我们现在是否与现有表达式等价
        if (fixUpInputs(rel)) {
          digest = rel.getRelDigest();
          RelNode equivRel = mapDigestToRel.get(digest);
          if ((equivRel != rel) && (equivRel != null)) {

            // 确保这个不好的 rel 没有以任何方式进入集合
            // (fixupInputs 会做这件事,但它不知道是否应该做,所以它无论如何都会做)
            set.obliterateRelNode(rel);

            // 已经存在等价表达式。使用那个表达式,忘记这个表达式
            return getSubsetNonNull(equivRel);
          }
        }
      } else {
        set = childSet; // 使用子节点的集合
      }
    }

    // 将表达式放在适当的等价集合中
    if (set == null) {
      set =
          new RelSet(nextSetId++,
              Util.minus(RelOptUtil.getVariablesSet(rel),
                  rel.getVariablesSet()),
              RelOptUtil.getVariablesUsed(rel)); // 创建新的等价集合
      this.allSets.add(set);
    }

    // 链式查找"活动"等价集合,以防多个集合同时合并
    while (set.equivalentSet != null) {
      set = set.equivalentSet; // 遍历等价链
    }

    // 允许每个 rel 注册自己的规则
    registerClass(rel);

    final int subsetBeforeCount = set.subsets.size();
    RelSubset subset = addRelToSet(rel, set); // 将关系表达式添加到集合

    final RelNode xx = mapDigestToRel.putIfAbsent(digest, rel); // 尝试将摘要映射到关系表达式

    LOGGER.trace("Register {} in {}", rel, subset);

    // 在我们递归注册其子节点时,此关系表达式可能已经被注册
    // 如果是这种情况,我们就完成了
    if (xx != null) {
      return subset;
    }

    for (RelNode input : rel.getInputs()) {
      RelSubset childSubset = (RelSubset) input;
      childSubset.set.parents.add(rel); // 添加父节点引用
    }

    // 排队由此关系表达式创建触发的所有规则
    fireRules(rel);

    // 这是一个新的子集
    if (set.subsets.size() > subsetBeforeCount
        || subset.triggerRule) {
      fireRules(subset); // 触发子集的规则
    }

    return subset;
  }

  /**
   * 将关系表达式添加到等价集合中
   * 
   * 执行步骤:
   * 1. 将关系表达式添加到集合
   * 2. 建立映射
   * 3. 传播成本改进
   * 4. 通知规则驱动器
   * 
   * 注意:在注册 RelNode 树时,有时节点的成本会改进,但子集不会收到通知
   * 最终可能会得到一个子集,其中有一个成本为 99 的 rel,但它认为其最优成本是 100
   * 我们认为这是因为父节点的反向链接尚未建立
   * 因此,给子集另一个机会来计算其成本
   * 
   * @param rel 关系表达式
   * @param set 等价集合
   * @return 子集
   */
  private RelSubset addRelToSet(RelNode rel, RelSet set) {
    RelSubset subset = set.add(rel); // 添加到集合
    mapRel2Subset.put(rel, subset); // 建立映射

    // 在注册 RelNode 树时,有时节点的成本会改进,但子集不会收到通知
    // 最终可能会得到一个子集,其中有一个成本为 99 的 rel,但它认为其最优成本是 100
    // 我们认为这是因为父节点的反向链接尚未建立
    // 因此,给子集另一个机会来计算其成本
    try {
      propagateCostImprovements(rel); // 传播成本改进
    } catch (CyclicMetadataException e) {
      // 忽略循环元数据异常
    }

    if (ruleDriver != null) {
      ruleDriver.onProduce(rel, subset); // 通知规则驱动器
    }

    return subset;
  }

  /**
   * 注册子集
   * 
   * 如果子集属于不同的集合,合并集合
   * 
   * @param set 等价集合
   * @param subset 子集
   * @return 规范化后的子集
   */
  private RelSubset registerSubset(
      @Nullable RelSet set,
      RelSubset subset) {
    if ((set != subset.set)
        && (set != null)
        && (set.equivalentSet == null)) {
      LOGGER.trace("Register #{} {}, and merge sets", subset.getId(), subset);
      merge(set, subset.set); // 合并集合
    }
    return canonize(subset); // 规范化子集
  }

  // 实现 RelOptPlanner 接口
  /**
   * 注册元数据提供者
   * 
   * @deprecated 将在 2.0 之前移除
   */
  @Deprecated // to be removed before 2.0
  @Override public void registerMetadataProviders(List<RelMetadataProvider> list) {
    list.add(0, new VolcanoRelMetadataProvider());
  }

  // 实现 RelOptPlanner 接口
  /**
   * 获取关系元数据时间戳
   * 
   * @deprecated 将在 2.0 之前移除
   * @param rel 关系表达式
   * @return 时间戳,如果未注册则返回 0
   */
  @Deprecated // to be removed before 2.0
  @Override public long getRelMetadataTimestamp(RelNode rel) {
    RelSubset subset = getSubset(rel);
    if (subset == null) {
      return 0;
    } else {
      return subset.timestamp;
    }
  }

  /**
   * 规范化计划字符串表示中的子集引用
   *
   * <p>这在编写测试时很有用:它有助于确保当引入额外规则生成新子集
   * 并导致后续子集编号偏移时,测试不会中断
   *
   * <p>例如,
   *
   * <blockquote>
   * FennelAggRel.FENNEL_EXEC(child=Subset#17.FENNEL_EXEC,groupCount=1,
   * EXPR$1=COUNT())<br>
   * &nbsp;&nbsp;FennelSortRel.FENNEL_EXEC(child=Subset#2.FENNEL_EXEC,
   * key=[0], discardDuplicates=false)<br>
   * &nbsp;&nbsp;&nbsp;&nbsp;FennelCalcRel.FENNEL_EXEC(
   * child=Subset#4.FENNEL_EXEC, expr#0..8={inputs}, expr#9=3456,
   * DEPTNO=$t7, $f0=$t9)<br>
   * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MockTableImplRel.FENNEL_EXEC(
   * table=[CATALOG, SALES, EMP])</blockquote>
   *
   * <p>变成
   *
   * <blockquote>
   * FennelAggRel.FENNEL_EXEC(child=Subset#{0}.FENNEL_EXEC, groupCount=1,
   * EXPR$1=COUNT())<br>
   * &nbsp;&nbsp;FennelSortRel.FENNEL_EXEC(child=Subset#{1}.FENNEL_EXEC,
   * key=[0], discardDuplicates=false)<br>
   * &nbsp;&nbsp;&nbsp;&nbsp;FennelCalcRel.FENNEL_EXEC(
   * child=Subset#{2}.FENNEL_EXEC,expr#0..8={inputs},expr#9=3456,DEPTNO=$t7,
   * $f0=$t9)<br>
   * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;MockTableImplRel.FENNEL_EXEC(
   * table=[CATALOG, SALES, EMP])</blockquote>
   *
   * <p>当且仅当 plan 为 null 时返回 null
   *
   * @param plan 计划字符串
   * @return 规范化后的计划字符串
   */
  public static @PolyNull String normalizePlan(@PolyNull String plan) {
    if (plan == null) {
      return null;
    }
    final Pattern poundDigits = Pattern.compile("Subset#[0-9]+\\."); // 匹配子集编号
    int i = 0;
    while (true) {
      final Matcher matcher = poundDigits.matcher(plan);
      if (!matcher.find()) {
        return plan; // 没有更多匹配
      }
      final String token = matcher.group(); // 例如 "Subset#23."
      plan = plan.replace(token, "Subset#{" + i++ + "}."); // 替换为规范化格式
    }
  }

  /**
   * 设置优化器是否已锁定
   * 
   * 锁定的优化器不接受新规则
   * addRule 方法将不执行任何操作并返回 false
   *
   * @param locked 优化器是否锁定
   */
  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  /**
   * 判断规则是否为逻辑规则
   *
   * 逻辑规则是指应用于逻辑节点的规则,而非物理节点
   *
   * @param rel 关系节点
   * @return true 如果关系节点是逻辑节点
   */
  @API(since = "1.24", status = API.Status.EXPERIMENTAL)
  public boolean isLogical(RelNode rel) {
    return !(rel instanceof PhysicalNode)
        && rel.getConvention() != rootConvention;
  }

  /**
   * 检查规则匹配是否为替换规则匹配
   *
   * 替换规则用于用等价表达式替换现有表达式
   *
   * @param match 要检查的规则匹配
   * @return true 如果规则匹配是替换规则匹配
   */
  @API(since = "1.24", status = API.Status.EXPERIMENTAL)
  protected boolean isSubstituteRule(VolcanoRuleCall match) {
    return match.getRule() instanceof SubstitutionRule;
  }

  /**
   * 检查规则匹配是否为转换规则匹配
   *
   * 转换规则用于将逻辑表达式转换为物理表达式
   *
   * @param match 要检查的规则匹配
   * @return true 如果规则匹配是转换规则匹配
   */
  @API(since = "1.24", status = API.Status.EXPERIMENTAL)
  protected boolean isTransformationRule(VolcanoRuleCall match) {
    return match.getRule() instanceof TransformationRule;
  }


  /**
   * 获取关系操作符的下限成本
   *
   * 下限成本是关系操作符可能达到的最低成本估计
   * 用于剪枝和优化决策
   *
   * @param rel 关系节点
   * @return 关系节点的下限成本,确保不为 null
   */
  @API(since = "1.24", status = API.Status.EXPERIMENTAL)
  protected RelOptCost getLowerBound(RelNode rel) {
    RelMetadataQuery mq = rel.getCluster().getMetadataQuery();
    RelOptCost lowerBound = mq.getLowerBoundCost(rel, this); // 获取下限成本
    if (lowerBound == null) {
      return zeroCost; // 如果无法获取,返回零成本
    }
    return lowerBound;
  }

  /**
   * 获取输入节点的上限成本
   * 
   * 允许用户覆盖此方法,因为某些实现可能在某些 RelNode 上有不同的成本模型
   * 例如 Spool
   * 
   * 执行步骤:
   * 1. 如果上限成本不是无限的
   * 2. 获取根节点的非累积成本
   * 3. 如果成本有效,从上限成本中减去根节点成本
   * 
   * @param mExpr 关系表达式
   * @param upperBound 上限成本
   * @return 输入节点的上限成本
   */
  @API(since = "1.24", status = API.Status.EXPERIMENTAL)
  protected RelOptCost upperBoundForInputs(
      RelNode mExpr, RelOptCost upperBound) {
    if (!upperBound.isInfinite()) {
      RelOptCost rootCost = mExpr.getCluster()
          .getMetadataQuery().getNonCumulativeCost(mExpr); // 获取根节点成本
      if (rootCost != null && !rootCost.isInfinite()) {
        return upperBound.minus(rootCost); // 减去根节点成本
      }
    }
    return upperBound;
  }

  //~ Inner Classes ----------------------------------------------------------

  /**
   * 延迟规则调用类
   * 
   * 与 RelOptRuleCall 不同,RelOptRuleCall 在找到匹配时立即调用规则
   * 而 DeferringRuleCall 创建一个 VolcanoRuleMatch,可以在稍后调用
   * 
   * 作用:允许优化器延迟规则应用,以便更好地控制优化流程
   */
  private static class DeferringRuleCall extends VolcanoRuleCall {
    /**
     * 构造延迟规则调用
     * 
     * @param planner 优化器
     * @param operand 规则操作数
     */
    DeferringRuleCall(
        VolcanoPlanner planner,
        RelOptRuleOperand operand) {
      super(planner, operand);
    }

    /**
     * 而不是调用规则(像基类那样),创建一个可以稍后调用的 VolcanoRuleMatch
     * 
     * 执行步骤:
     * 1. 创建规则匹配对象
     * 2. 将匹配添加到规则队列
     * 
     * 作用:延迟规则应用,允许优化器控制何时应用规则
     */
    @Override protected void onMatch() {
      final VolcanoRuleMatch match =
          new VolcanoRuleMatch(
              volcanoPlanner,
              getOperand0(),
              rels,
              nodeInputs);
      volcanoPlanner.ruleDriver.getRuleQueue().addMatch(match); // 添加到规则队列
    }
  }

  /**
   * 来源抽象类:表示 RelNode 的来源
   * 
   * 作用:追踪关系表达式是如何创建的,用于调试和优化分析
   */
  abstract static class Provenance {
    public static final Provenance EMPTY = new UnknownProvenance(); // 空来源
  }

  /**
   * 未知来源:我们不知道这个 RelNode 来自哪里
   * 
   * 可能是手动创建的,或者由 sql-to-rel 转换器创建的
   * 
   * 作用:表示没有明确来源的关系表达式
   */
  private static class UnknownProvenance extends Provenance {
  }

  /**
   * 直接来源:通过复制从另一个 RelNode 直接获得的 RelNode
   * 
   * 作用:表示通过复制操作创建的关系表达式
   */
  static class DirectProvenance extends Provenance {
    final RelNode source; // 源关系表达式

    /**
     * 构造直接来源
     * 
     * @param source 源关系表达式
     */
    DirectProvenance(RelNode source) {
      this.source = source;
    }
  }

  /**
   * 规则来源:通过触发规则获得的 RelNode
   * 
   * 作用:表示通过应用优化规则创建的关系表达式
   */
  static class RuleProvenance extends Provenance {
    final RelOptRule rule; // 应用的规则
    final ImmutableList<RelNode> rels; // 规则匹配的关系表达式列表
    final int callId; // 规则调用 ID

    /**
     * 构造规则来源
     * 
     * @param rule 应用的规则
     * @param rels 规则匹配的关系表达式列表
     * @param callId 规则调用 ID
     */
    RuleProvenance(RelOptRule rule, ImmutableList<RelNode> rels, int callId) {
      this.rule = rule;
      this.rels = rels;
      this.callId = callId;
    }
  }
}
