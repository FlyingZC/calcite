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
package org.apache.calcite.plan.hep;

import org.apache.calcite.linq4j.function.Function2;
import org.apache.calcite.linq4j.function.Functions;
import org.apache.calcite.plan.AbstractRelOptPlanner;
import org.apache.calcite.plan.CommonRelSubExprRule;
import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.RelDigest;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptCostFactory;
import org.apache.calcite.plan.RelOptCostImpl;
import org.apache.calcite.plan.RelOptMaterialization;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.plan.RelTrait;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.convert.Converter;
import org.apache.calcite.rel.convert.ConverterRule;
import org.apache.calcite.rel.convert.TraitMatchingRule;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.metadata.RelMdUtil;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.graph.BreadthFirstIterator;
import org.apache.calcite.util.graph.CycleDetector;
import org.apache.calcite.util.graph.DefaultDirectedGraph;
import org.apache.calcite.util.graph.DefaultEdge;
import org.apache.calcite.util.graph.DepthFirstIterator;
import org.apache.calcite.util.graph.DirectedGraph;
import org.apache.calcite.util.graph.Graphs;
import org.apache.calcite.util.graph.TopologicalOrderIterator;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

import static org.apache.calcite.linq4j.Nullness.castNonNull;

import static java.util.Objects.requireNonNull;

/**
 * HepPlanner是{@link RelOptPlanner}接口的启发式实现
 * HepPlanner代表"基于启发式表达式规划器",是一种基于规则的查询优化器
 * 它使用DAG(有向无环图)来表示查询计划,并通过应用优化规则来转换和优化查询
 * 与VolcanoPlanner(基于成本的优化器)不同,HepPlanner使用启发式方法,
 * 不需要计算所有可能计划的成本,而是按照预定义的程序顺序应用规则
 * 特点:
 * 1. 使用DAG表示查询计划,可以共享公共子表达式
 * 2. 支持多种规则匹配顺序(深度优先、广度优先、自顶向下、自底向上)
 * 3. 支持规则分组和子程序
 * 4. 不依赖成本模型,基于启发式规则应用
 */
public class HepPlanner extends AbstractRelOptPlanner {
  //~ Instance fields --------------------------------------------------------

  // 主程序,控制规则应用的顺序和方式,包含一系列指令(如匹配顺序、规则集合等)
  private final HepProgram mainProgram;

  // 当前查询图的根节点,表示整个查询计划的入口
  // 可以为null,表示尚未设置根节点
  private @Nullable HepRelVertex root;

  // 请求的根节点特征集合,表示最终计划需要满足的特征(如物理实现特征)
  // 例如:如果需要将逻辑计划转换为物理计划,这里会保存目标物理特征
  private @Nullable RelTraitSet requestedRootTraits;

  /**
   * 从RelNode摘要到HepRelVertex的映射表
   * RelDigest是RelNode的唯一标识符,基于RelNode的类型、输入、字段等信息生成
   * 通过这个映射,可以快速判断两个RelNode是否等价,从而实现公共子表达式共享
   * {@link RelDataType}通过其字段类型列表{@code List<RelDataType>}来表示
   * 这使得仅表达式名称不同的Project可以被当作等价处理
   * 例如:Project(a+b AS sum1) 和 Project(a+b AS sum2) 会被识别为等价
   */
  private final Map<RelDigest, HepRelVertex> mapDigestToVertex =
      new HashMap<>();

  // 转换计数器,记录自规划开始以来执行的规则转换次数
  // 用于触发垃圾回收和判断优化是否达到固定点
  private int nTransformations;

  // 上一次垃圾回收时的图大小(顶点数量)
  // 用于判断是否需要进行垃圾回收
  private int graphSizeLastGC;

  // 上一次垃圾回收时的转换次数
  // 用于判断自上次GC以来是否发生了修改
  private int nTransformationsLastGC;

  // 是否禁用DAG标志
  // 如果为true,则图保持为树结构,不允许共享公共子表达式
  // 如果为false,则允许DAG,可以共享相同的子表达式以减少内存使用
  private final boolean noDag;

  /**
   * 查询图,边从父节点指向子节点
   * 这是一个单根DAG(有向无环图),可能还有其他根节点对应于待垃圾收集的废弃计划片段
   * 图结构:
   * - 顶点(Vertex): HepRelVertex,包装了RelNode
   * - 边(Edge): DefaultEdge,表示父子关系(从父节点到子节点)
   * - 有向无环: 不允许循环,保证查询计划的语义正确性
   * - 公共子表达式共享: 通过DAG结构,相同的子表达式可以被多个父节点引用
   */
  private final DirectedGraph<HepRelVertex, DefaultEdge> graph =
      DefaultDirectedGraph.create();

  // 复制钩子函数,当RelNode被复制时调用
   // Function2<RelNode, RelNode, Void>表示接受两个RelNode参数(原始节点和新节点),返回Void
  // 用于在节点复制时执行自定义操作,例如记录复制事件或更新元数据
  private final Function2<RelNode, RelNode, Void> onCopyHook;

  // 物化视图列表,保存所有已注册的物化视图
  // 物化视图可以用于查询重写和优化,通过匹配查询计划中的子树来提升性能
  private final List<RelOptMaterialization> materializations =
      new ArrayList<>();

  //~ Constructors -----------------------------------------------------------

  /**
   * 创建一个新的HepPlanner,允许使用DAG结构
   * 这是简化构造方法,使用默认参数:
   * - context: null(无上下文)
   * - noDag: false(允许DAG,可以共享公共子表达式)
   * - onCopyHook: null(无复制钩子)
   * - costFactory: RelOptCostImpl.FACTORY(默认成本工厂)
   *
   * @param program 控制规则应用的程序,定义了规则应用的顺序和方式
   */
  public HepPlanner(HepProgram program) {
    this(program, null, false, null, RelOptCostImpl.FACTORY);
  }

  /**
   * 创建一个新的HepPlanner,允许使用DAG结构,并携带上下文
   * 上下文可以在规则应用过程中传递额外信息
   * 其他参数使用默认值
   *
   * @param program 控制规则应用的程序
   * @param context 在规划过程中携带的上下文信息,可以为null
   */
  public HepPlanner(HepProgram program, @Nullable Context context) {
    this(program, context, false, null, RelOptCostImpl.FACTORY);
  }

  /**
   * 创建一个新的HepPlanner,提供完整的配置选项
   * 这是最完整的构造方法,允许自定义所有参数
   *
   * @param program 控制规则应用的程序,定义了优化流程
   * @param context 在规划过程中携带的上下文信息,用于在规则之间传递状态
   * @param noDag 如果为false,当表达式相同时创建共享节点(允许DAG);如果为true,保持树结构
   * @param onCopyHook 当节点被复制时调用的函数,用于自定义复制行为
   * @param costFactory 成本工厂,用于计算RelNode的成本(虽然HepPlanner主要基于启发式,但仍需要成本信息)
   */
  public HepPlanner(
      HepProgram program,
      @Nullable Context context,
      boolean noDag,
      @Nullable Function2<RelNode, RelNode, Void> onCopyHook,
      RelOptCostFactory costFactory) {
    super(costFactory, context); // 调用父类AbstractRelOptPlanner的构造方法
    this.mainProgram = requireNonNull(program, "program"); // 确保program不为null
    this.onCopyHook = Util.first(onCopyHook, Functions.ignore2()); // 如果onCopyHook为null,使用空函数
    this.noDag = noDag; // 设置是否禁用DAG
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * 设置查询计划的根节点
   * 将给定的RelNode添加到查询图中,并将其设置为根节点
   * 这是规划过程的入口点,在调用findBestExp之前必须调用此方法
   *
   * @param rel 要设置为根的RelNode,通常是查询的初始逻辑计划
   */
  @Override public void setRoot(RelNode rel) {
    root = addRelToGraph(rel); // 将RelNode及其所有子节点递归添加到图结构中
    dumpGraph(); // 如果启用了跟踪,输出当前图结构用于调试
  }

  /**
   * 获取当前查询计划的根节点
   *
   * @return 当前的根节点,如果尚未设置则返回null
   */
  @Override public @Nullable RelNode getRoot() {
    return root;
  }

  /**
   * 清理规划器状态
   * 移除所有已注册的规则和物化视图,重置规划器到初始状态
   * 通常用于重新开始规划或释放资源
   */
  @Override public void clear() {
    super.clear(); // 调用父类的清理方法
    for (RelOptRule rule : getRules()) { // 遍历所有已注册的规则
      removeRule(rule); // 移除每个规则
    }
    this.materializations.clear(); // 清空物化视图列表
  }

  /**
   * 改变RelNode的特征集合
   * 在HepPlanner中,这个方法主要用于记录根节点的目标特征
   * 对于非根节点,特征被忽略,因为HepPlanner不主动进行特征转换
   * 只有当根节点需要转换特征时(如从逻辑特征转换为物理特征),才会记录目标特征
   *
   * @param rel 要改变特征的RelNode
   * @param toTraits 目标特征集合
   * @return 原始的RelNode(HepPlanner不立即执行转换)
   */
  @Override public RelNode changeTraits(RelNode rel, RelTraitSet toTraits) {
    // 忽略特征,除了根节点,我们需要记住最终的转换应该是什么
    // 检查rel是否是根节点(可能是HepRelVertex包装的,也可能是内部的RelNode)
    if ((rel == root) || (rel == requireNonNull(root, "root").getCurrentRel())) {
      requestedRootTraits = toTraits; // 记录根节点的目标特征,用于后续的转换规则
    }
    return rel; // 返回原始RelNode,不立即执行转换
  }

  /**
   * 查找最佳表达式(优化查询计划)
   * 这是HepPlanner的核心方法,执行完整的优化流程:
   * 1. 执行主程序(应用所有优化规则)
   * 2. 清理垃圾(移除不可达的节点)
   * 3. 构建最终计划(将图结构转换为RelNode树)
   *
   * @return 优化后的RelNode,表示最佳查询计划
   */
  @Override public RelNode findBestExp() {
    requireNonNull(root, "'root' must not be null"); // 确保根节点已设置

    executeProgram(mainProgram); // 执行主程序,应用所有优化规则

    // 清理垃圾,移除所有不在最终计划中的节点
    // 这可以释放内存,并确保最终计划只包含可达节点
    collectGarbage();
    dumpRuleAttemptsInfo(); // 如果启用了跟踪,输出规则尝试信息
    return buildFinalPlan(requireNonNull(root, "'root' must not be null")); // 构建最终的RelNode树
  }

  /**
   * 程序的顶层入口点,初始化状态然后执行程序
   * HepProgram包含一系列指令(如规则集合、匹配顺序等)
   * 这个方法准备执行环境并开始执行程序
   *
   * @param program 要执行的HepProgram,包含优化规则和执行策略
   */
  private void executeProgram(HepProgram program) {
    // 创建准备上下文,包含对HepPlanner的引用
    final HepInstruction.PrepareContext px =
        HepInstruction.PrepareContext.create(this);
    // 准备程序,创建执行状态(HepState)
    // HepState包含程序执行所需的所有状态信息
    final HepState state = program.prepare(px);
    // 执行程序,应用所有指令
    state.execute();
  }

/**
   * 执行程序的另一个重载版本,用于子程序
   * 这个方法处理程序状态的初始化和指令执行,并在适当时触发垃圾回收
   * 垃圾回收策略:当转换次数超过上次GC时的图大小时执行GC
   * 这种策略可以摊销GC成本,同时保持内存使用与图大小成正比
   *
   * @param instruction 要执行的HepProgram指令
   * @param state 程序状态,包含执行上下文
   */
  void executeProgram(HepProgram instruction, HepProgram.State state) {
    state.init(); // 初始化程序状态
    // 遍历并执行每个指令状态
    state.instructionStates.forEach(instructionState -> {
      instructionState.execute(); // 执行当前指令
      // 计算自上次GC以来执行的转换次数
      int delta = nTransformations - nTransformationsLastGC;
      // 如果转换次数大于上次GC时的图大小,说明有足够的垃圾可以收集
      if (delta > graphSizeLastGC) {
        // 自上次垃圾回收以来执行的转换次数大于当时的图顶点数
        // 这意味着现在应该有合理数量的垃圾可以收集
        // 我们这样做是为了在多个指令之间摊销垃圾收集成本,
        // 同时保持高水位内存使用与图大小成比例
        collectGarbage();
      }
    });
  }

  /**
   * 执行匹配限制指令
   * 设置规则匹配的最大次数,用于控制优化过程的规模
   * 达到限制后,规则应用将停止
   *
   * @param instruction 匹配限制指令,包含limit值
   * @param state 指令状态,包含程序状态
   */
  void executeMatchLimit(HepInstruction.MatchLimit instruction,
      HepInstruction.MatchLimit.State state) {
    LOGGER.trace("Setting match limit to {}", instruction.limit); // 记录跟踪日志
    state.programState.matchLimit = instruction.limit; // 设置匹配限制
  }

  /**
   * 执行匹配顺序指令
   * 设置规则匹配的顺序,影响规则应用的策略
   * 可选的匹配顺序:
   * - ARBITRARY: 任意顺序
   * - DEPTH_FIRST: 深度优先
   * - TOP_DOWN: 自顶向下
   * - BOTTOM_UP: 自底向上
   *
   * @param instruction 匹配顺序指令,包含order值
   * @param state 指令状态,包含程序状态
   */
  void executeMatchOrder(HepInstruction.MatchOrder instruction,
      HepInstruction.MatchOrder.State state) {
    LOGGER.trace("Setting match order to {}", instruction.order); // 记录跟踪日志
    state.programState.matchOrder = instruction.order; // 设置匹配顺序
  }

  /**
   * 执行规则实例指令
   * 应用单个特定的规则实例到查询图
   * 如果当前正在跳过组(在收集阶段),则不执行
   *
   * @param instruction 规则实例指令,包含要应用的规则
   * @param state 指令状态,包含程序状态
   */
  void executeRuleInstance(HepInstruction.RuleInstance instruction,
      HepInstruction.RuleInstance.State state) {
    if (state.programState.skippingGroup()) {
      // 如果正在跳过组(在BeginGroup和EndGroup之间收集规则),则不执行
      return;
    }
    // 应用单个规则,forceConversions=true表示强制执行转换规则
    applyRules(state.programState, ImmutableList.of(instruction.rule), true);
  }

  /**
   * 执行规则查找指令
   * 根据规则的描述查找规则,然后应用该规则
   * 规则描述是规则的唯一标识符
   * 如果当前正在跳过组,则不执行
   *
   * @param instruction 规则查找指令,包含规则描述
   * @param state 指令状态,包含程序状态和缓存的规则
   */
  void executeRuleLookup(HepInstruction.RuleLookup instruction,
      HepInstruction.RuleLookup.State state) {
    if (state.programState.skippingGroup()) {
      // 如果正在跳过组,则不执行
      return;
    }
    RelOptRule rule = state.rule; // 尝试从状态中获取缓存的规则
    if (rule == null) {
      // 如果缓存中没有规则,根据描述查找规则
      state.rule = rule = getRuleByDescription(instruction.ruleDescription);
      LOGGER.trace("Looking up rule with description {}, found {}",
          instruction.ruleDescription, rule); // 记录查找结果
    }
    if (rule != null) {
      // 如果找到了规则,应用该规则
      applyRules(state.programState, ImmutableList.of(rule), true);
    }
  }

  /**
   * 执行规则类指令
   * 应用所有属于指定类的规则
   * 使用instanceof检查规则是否属于指定的类
   * 规则集合会被缓存,避免重复查找
   *
   * @param instruction 规则类指令,包含规则类
   * @param state 指令状态,包含程序状态和缓存的规则集合
   */
  void executeRuleClass(HepInstruction.RuleClass instruction,
      HepInstruction.RuleClass.State state) {
    if (state.programState.skippingGroup()) {
      // 如果正在跳过组,则不执行
      return;
    }
    LOGGER.trace("Applying rule class {}", instruction.ruleClass); // 记录跟踪日志
    Set<RelOptRule> ruleSet = state.ruleSet; // 尝试获取缓存的规则集合
    if (ruleSet == null) {
      // 如果缓存中没有规则集合,创建一个新的
      state.ruleSet = ruleSet = new LinkedHashSet<>();
      Class<?> ruleClass = instruction.ruleClass; // 获取目标规则类
      // 遍历所有已注册的规则
      for (RelOptRule rule : mapDescToRule.values()) {
        // 检查规则是否是指定类的实例
        if (ruleClass.isInstance(rule)) {
          ruleSet.add(rule); // 将匹配的规则添加到集合中
        }
      }
    }
    // 应用所有匹配的规则
    applyRules(state.programState, ruleSet, true);
  }

  /**
   * 执行规则集合指令
   * 应用给定的规则集合
   * 这是最直接的规则应用方式,直接使用提供的规则列表
   *
   * @param instruction 规则集合指令,包含要应用的规则列表
   * @param state 指令状态,包含程序状态
   */
  void executeRuleCollection(HepInstruction.RuleCollection instruction,
      HepInstruction.RuleCollection.State state) {
    if (state.programState.skippingGroup()) {
      // 如果正在跳过组,则不执行
      return;
    }
    // 应用规则集合中的所有规则
    applyRules(state.programState, instruction.rules, true);
  }

  /**
   * 执行转换规则指令
   * 应用所有转换规则(ConvertRule),用于将RelNode从一种特征转换为另一种特征
   * 转换规则分为两类:
   * 1. 保证的转换(guaranteed=true): 总是适用的转换,通常从逻辑特征到物理特征
   * 2. 非保证的转换(guaranteed=false): 可选的转换,用于优化
   *
   * 对于非保证的转换,还会添加TraitMatchingRule以支持自底向上的匹配
   *
   * @param instruction 转换规则指令,包含guaranteed标志
   * @param state 指令状态,包含程序状态和缓存的规则集合
   */
  void executeConverterRules(HepInstruction.ConverterRules instruction,
      HepInstruction.ConverterRules.State state) {
    checkArgument(state.programState.group == null); // 确保不在组内
    Set<RelOptRule> ruleSet = state.ruleSet; // 尝试获取缓存的规则集合
    if (ruleSet == null) {
      // 如果缓存中没有规则集合,创建一个新的
      state.ruleSet = ruleSet = new LinkedHashSet<>();
      // 遍历所有已注册的规则
      for (RelOptRule rule : mapDescToRule.values()) {
        // 跳过非转换规则
        if (!(rule instanceof ConverterRule)) {
          continue;
        }
        ConverterRule converter = (ConverterRule) rule;
        // 检查规则的guaranteed属性是否与指令匹配
        if (converter.isGuaranteed() != instruction.guaranteed) {
          continue;
        }

        // 添加规则本身以自顶向下工作
        ruleSet.add(converter);
        if (!instruction.guaranteed) {
          // 对于非保证的转换,添加TraitMatchingRule以自底向上工作
          // TraitMatchingRule会匹配需要该转换特征的父节点
          ruleSet.add(
              TraitMatchingRule.config(converter, RelFactories.LOGICAL_BUILDER)
                  .toRule());
        }
      }
    }
    // 应用所有匹配的转换规则
    applyRules(state.programState, ruleSet, instruction.guaranteed);
  }

  /**
   * 执行公共子表达式规则指令
   * 应用所有公共子表达式规则(CommonRelSubExprRule)
   * 这些规则用于识别和优化公共子表达式
   *
   * @param instruction 公共子表达式规则指令
   * @param state 指令状态,包含程序状态和缓存的规则集合
   */
  void executeCommonRelSubExprRules(
      HepInstruction.CommonRelSubExprRules instruction,
      HepInstruction.CommonRelSubExprRules.State state) {
    checkArgument(state.programState.group == null); // 确保不在组内
    Set<RelOptRule> ruleSet = state.ruleSet; // 尝试获取缓存的规则集合
    if (ruleSet == null) {
      // 如果缓存中没有规则集合,创建一个新的
      state.ruleSet = ruleSet = new LinkedHashSet<>();
      // 遍历所有已注册的规则
      for (RelOptRule rule : mapDescToRule.values()) {
        // 只保留公共子表达式规则
        if (!(rule instanceof CommonRelSubExprRule)) {
          continue;
        }
        ruleSet.add(rule); // 将匹配的规则添加到集合中
      }
    }
    // 应用所有匹配的公共子表达式规则
    applyRules(state.programState, ruleSet, true);
  }

  /**
   * 执行子程序指令
   * 子程序会重复执行,直到达到固定点(不再发生转换)
   * 这用于确保规则被充分应用,直到无法进一步优化
   *
   * @param instruction 子程序指令
   * @param state 指令状态,包含程序状态
   */
  void executeSubProgram(HepInstruction.SubProgram instruction,
      HepInstruction.SubProgram.State state) {
    LOGGER.trace("Entering subprogram"); // 记录跟踪日志
    for (;;) {
      // 记录执行前的转换次数
      int nTransformationsBefore = nTransformations;
      // 执行子程序
      state.programState.execute();
      // 检查是否发生了新的转换
      if (nTransformations == nTransformationsBefore) {
        // 这次没有发生任何事情,达到固定点
        break;
      }
    }
    LOGGER.trace("Leaving subprogram"); // 记录跟踪日志
  }

  /**
   * 执行开始组指令
   * 开始一个规则组,收集组内的规则而不是立即应用
   * 规则组允许将多个规则收集在一起,然后一次性应用
   * 这可以用于控制规则的应用顺序和策略
   *
   * @param instruction 开始组指令
   * @param state 指令状态,包含程序状态和对应的结束组状态
   */
  void executeBeginGroup(HepInstruction.BeginGroup instruction,
      HepInstruction.BeginGroup.State state) {
    checkArgument(state.programState.group == null); // 确保没有嵌套的组
    // 设置当前组为对应的结束组状态
    state.programState.group = state.endGroup;
    LOGGER.trace("Entering group"); // 记录跟踪日志
  }

  /**
   * 执行结束组指令
   * 结束规则组,应用收集到的所有规则
   * 在BeginGroup和EndGroup之间收集的规则会被一次性应用
   *
   * @param instruction 结束组指令
   * @param state 指令状态,包含程序状态和收集到的规则集合
   */
  void executeEndGroup(HepInstruction.EndGroup instruction,
      HepInstruction.EndGroup.State state) {
    checkArgument(state.programState.group == state); // 确保结束的是正确的组
    state.programState.group = null; // 清除当前组
    state.collecting = false; // 停止收集模式
    // 应用收集到的所有规则
    applyRules(state.programState, state.ruleSet, true);
    LOGGER.trace("Leaving group"); // 记录跟踪日志
  }

  /**
   * 深度优先应用规则
   * 在深度优先遍历的顺序上应用规则,当规则匹配并转换成功后,
   * 立即从转换后的节点继续深度优先遍历
   *
   * 这种策略可以快速深入到计划的深层,优先优化子表达式
   *
   * @param programState 程序状态,包含匹配限制等信息
   * @param iter 图节点的迭代器
   * @param rules 要应用的规则集合
   * @param forceConversions 是否强制执行转换规则
   * @param nMatches 已匹配的次数(输入参数)
   * @return 更新后的匹配次数
   */
  private int depthFirstApply(HepProgram.State programState,
      Iterator<HepRelVertex> iter, Collection<RelOptRule> rules,
      boolean forceConversions, int nMatches) {
    // 遍历图中的每个节点
    while (iter.hasNext()) {
      HepRelVertex vertex = iter.next(); // 获取下一个节点
      // 尝试应用每个规则
      for (RelOptRule rule : rules) {
        // 尝试在当前节点应用规则
        HepRelVertex newVertex =
            applyRule(rule, vertex, forceConversions);
        // 如果规则未匹配或匹配后没有改变,继续下一个规则
        if (newVertex == null || newVertex == vertex) {
          continue;
        }
        // 规则匹配成功并产生了转换
        ++nMatches; // 增加匹配计数
        // 检查是否达到匹配限制
        if (nMatches >= programState.matchLimit) {
          return nMatches; // 达到限制,返回
        }
        // 尽可能从我们离开的地方继续
        // 必须创建一个新的迭代器,因为转换使旧的迭代器失效
        Iterator<HepRelVertex> depthIter =
            getGraphIterator(programState, newVertex);
        // 递归地从新节点继续深度优先应用
        nMatches =
            depthFirstApply(programState, depthIter, rules, forceConversions,
                nMatches);
        break; // 跳出规则循环,继续下一个节点
      }
    }
    return nMatches; // 返回总匹配次数
  }

  /**
   * 应用规则集合到查询图
   * 这是规则应用的核心方法,根据匹配顺序和策略应用规则
   *
   * 处理流程:
   * 1. 如果在组内,将规则添加到组集合中
   * 2. 否则,根据匹配顺序遍历图并应用规则
   * 3. 重复直到达到固定点(不再发生转换)
   *
   * @param programState 程序状态,包含匹配顺序和限制
   * @param rules 要应用的规则集合
   * @param forceConversions 是否强制执行转换规则
   */
  private void applyRules(HepProgram.State programState,
      Collection<RelOptRule> rules, boolean forceConversions) {
    // 检查是否在组内
    final HepInstruction.EndGroup.State group = programState.group;
    if (group != null) {
      // 如果在组内,将规则添加到组集合中,而不是立即应用
      checkArgument(group.collecting); // 确保处于收集模式
      Set<RelOptRule> ruleSet = requireNonNull(group.ruleSet, "group.ruleSet");
      ruleSet.addAll(rules); // 将规则添加到组集合
      return; // 返回,不立即应用
    }

    LOGGER.trace("Applying rule set {}", rules); // 记录跟踪日志

    // 判断转换后是否需要完全重启
    // 对于ARBITRARY和DEPTH_FIRST顺序,不需要完全重启
    // 对于TOP_DOWN和BOTTOM_UP顺序,需要完全重启
    final boolean fullRestartAfterTransformation =
        programState.matchOrder != HepMatchOrder.ARBITRARY
            && programState.matchOrder != HepMatchOrder.DEPTH_FIRST;

    int nMatches = 0; // 匹配计数器

    boolean fixedPoint; // 是否达到固定点
    do {
      // 获取图迭代器,从根节点开始
      Iterator<HepRelVertex> iter =
          getGraphIterator(programState, requireNonNull(root, "root"));
      fixedPoint = true; // 假设达到固定点
      // 遍历图中的每个节点
      while (iter.hasNext()) {
        HepRelVertex vertex = iter.next(); // 获取下一个节点
        // 尝试应用每个规则
        for (RelOptRule rule : rules) {
          // 尝试在当前节点应用规则
          HepRelVertex newVertex =
              applyRule(rule, vertex, forceConversions);
          // 如果规则未匹配或匹配后没有改变,继续下一个规则
          if (newVertex == null || newVertex == vertex) {
            continue;
          }
          // 规则匹配成功并产生了转换
          ++nMatches; // 增加匹配计数
          // 检查是否达到匹配限制
          if (nMatches >= programState.matchLimit) {
            return; // 达到限制,返回
          }
          if (fullRestartAfterTransformation) {
            // 如果需要完全重启,从根节点重新开始
            iter = getGraphIterator(programState, requireNonNull(root, "root"));
          } else {
            // 尽可能从我们离开的地方继续
            // 必须创建一个新的迭代器,因为转换使旧的迭代器失效
            iter = getGraphIterator(programState, newVertex);
            // 如果是深度优先顺序,递归应用
            if (programState.matchOrder == HepMatchOrder.DEPTH_FIRST) {
              nMatches =
                  depthFirstApply(programState, iter, rules, forceConversions, nMatches);
              // 检查是否达到匹配限制
              if (nMatches >= programState.matchLimit) {
                return; // 达到限制,返回
              }
            }
            // 记住需要再次循环,因为我们跳过了一些东西
            fixedPoint = false; // 未达到固定点,需要继续
          }
          break; // 跳出规则循环,继续下一个节点
        }
      }
    } while (!fixedPoint); // 继续直到达到固定点
  }

  /**
   * 获取图迭代器,根据匹配顺序返回不同的遍历策略
   *
   * 遍历策略:
   * - ARBITRARY: 深度优先(任意顺序)
   * - DEPTH_FIRST: 深度优先
   * - TOP_DOWN: 自顶向下(拓扑排序)
   * - BOTTOM_UP: 自底向上(拓扑排序)
   *
   * @param programState 程序状态,包含匹配顺序
   * @param start 起始节点
   * @return 图节点的迭代器
   */
  private Iterator<HepRelVertex> getGraphIterator(
      HepProgram.State programState, HepRelVertex start) {
    // 确保没有垃圾,因为拓扑排序不从特定根节点开始,
    // 而且规则无法在垃圾上触发

    // FIXME jvs 25-Sept-2006: 我不得不把这个移到前面,因为FRG-215,
    // 这个问题仍在调查中。一旦我们弄清楚,把它移到下面的位置以获得更好的优化器性能。
    collectGarbage(); // 执行垃圾回收

    // 根据匹配顺序创建不同的迭代器
    switch (requireNonNull(programState.matchOrder, "programState.matchOrder")) {
    case ARBITRARY:
    case DEPTH_FIRST:
      // 深度优先遍历,从指定起始节点开始
      return DepthFirstIterator.of(graph, start).iterator();

    case TOP_DOWN:
    case BOTTOM_UP:
      // 拓扑排序遍历,必须从根节点开始
      assert start == root; // 确保起始节点是根节点
      // 参见上面的垃圾回收注释
/*
        collectGarbage();
*/
      // 拓扑排序遍历,根据matchOrder决定方向
      return TopologicalOrderIterator.of(graph, programState.matchOrder).iterator();
    default:
      throw new
          UnsupportedOperationException("Unsupported match order: " + programState.matchOrder);
    }
  }

  /**
   * 在指定节点上应用单个规则
   * 这是规则应用的核心方法,负责:
   * 1. 检查规则是否适用(特别是转换规则和公共子表达式规则)
   * 2. 匹配规则的operands
   * 3. 调用规则进行转换
   * 4. 应用转换结果
   *
   * @param rule 要应用的规则
   * @param vertex 要应用规则的节点
   * @param forceConversions 是否强制执行转换规则
   * @return 转换后的新节点,如果规则未匹配或未产生转换则返回null
   */
  private @Nullable HepRelVertex applyRule(
      RelOptRule rule,
      HepRelVertex vertex,
      boolean forceConversions) {
    // 检查节点是否仍在图中(可能已被垃圾回收)
    if (!graph.vertexSet().contains(vertex)) {
      return null; // 节点不在图中,返回null
    }
    RelTrait parentTrait = null; // 父节点的特征(用于转换规则)
    List<RelNode> parents = null; // 父节点列表(用于公共子表达式规则)
    // 特殊处理转换规则
    if (rule instanceof ConverterRule) {
      // 保证的转换规则需要特殊处理,确保它们只在需要的地方触发,
      // 否则它们倾向于无限触发
      ConverterRule converterRule = (ConverterRule) rule;
      // 如果是保证的转换规则或不需要强制转换,检查转换是否适用
      if (converterRule.isGuaranteed() || !forceConversions) {
        // 检查转换规则是否适用于当前节点
        if (!doesConverterApply(converterRule, vertex)) {
          return null; // 转换不适用,返回null
        }
        parentTrait = converterRule.getOutTrait(); // 记录输出特征
      }
    } else if (rule instanceof CommonRelSubExprRule) {
      // 只有当顶点是公共子表达式时才触发公共子表达式规则
      List<HepRelVertex> parentVertices = getVertexParents(vertex); // 获取父节点
      // 如果父节点少于2个,说明不是公共子表达式
      if (parentVertices.size() < 2) {
        return null; // 不是公共子表达式,返回null
      }
      // 收集父节点的RelNode
      parents = new ArrayList<>();
      for (HepRelVertex pVertex : parentVertices) {
        parents.add(pVertex.getCurrentRel());
      }
    }

    // 匹配规则的operands
    final List<RelNode> bindings = new ArrayList<>(); // 绑定的RelNode列表
    final Map<RelNode, List<RelNode>> nodeChildren = new HashMap<>(); // 节点的子节点映射
    boolean match =
        matchOperands(
            rule.getOperand(), // 规则的operand
            vertex.getCurrentRel(), // 节点的RelNode
            bindings, // 输出:绑定的RelNode
            nodeChildren); // 输出:子节点映射

    // 如果operand不匹配,返回null
    if (!match) {
      return null;
    }

    // 创建规则调用对象
    HepRuleCall call =
        new HepRuleCall(
            this, // 规划器
            rule.getOperand(), // 规则的operand
            bindings.toArray(new RelNode[0]), // 绑定的RelNode数组
            nodeChildren, // 子节点映射
            parents); // 父节点列表(用于公共子表达式规则)

    // 允许规则应用自己的侧边条件
    if (!rule.matches(call)) {
      return null; // 规则的侧边条件不满足,返回null
    }

    // 触发规则,执行转换
    fireRule(call);

    // 如果规则产生了结果,应用转换结果
    if (!call.getResults().isEmpty()) {
      return applyTransformationResults(
          vertex, // 原始节点
          call, // 规则调用
          parentTrait); // 父节点特征(用于转换规则)
    }

    // 规则没有产生结果,返回null
    return null;
  }

  /**
   * 检查转换规则是否适用于指定节点
   * 转换规则只在以下情况下适用:
   * 1. 至少有一个父节点需要转换器输出的特征
   * 2. 或者节点是根节点,且请求的根特征包含转换器输出的特征
   *
   * 这种检查可以避免不必要的转换,防止转换规则无限触发
   *
   * @param converterRule 转换规则
   * @param vertex 要检查的节点
   * @return 如果转换规则适用则返回true,否则返回false
   */
  private boolean doesConverterApply(
      ConverterRule converterRule,
      HepRelVertex vertex) {
    RelTrait outTrait = converterRule.getOutTrait(); // 获取转换器的输出特征
    // 获取节点的所有父节点
    List<HepRelVertex> parents = Graphs.predecessorListOf(graph, vertex);
    // 遍历每个父节点
    for (HepRelVertex parent : parents) {
      RelNode parentRel = parent.getCurrentRel(); // 获取父节点的RelNode
      // 如果父节点是转换器,跳过(不支持转换器链)
      if (parentRel instanceof Converter) {
        // 我们不支持转换器链
        continue;
      }
      // 检查父节点的特征集合是否包含转换器输出的特征
      if (parentRel.getTraitSet().contains(outTrait)) {
        // 这个父节点需要转换器产生的特征
        return true; // 转换适用
      }
    }
    // 如果节点是根节点,且请求的根特征包含转换器输出的特征,则转换适用
    return (vertex == root)
        && (requestedRootTraits != null)
        && requestedRootTraits.contains(outTrait);
  }

  /**
   * 获取节点的父节点列表
   * 如果一个节点作为父节点的输入出现多次,则每个输入引用都算作一个父节点
   * 例如:如果节点A同时作为节点B的两个输入,那么B会在父节点列表中出现两次
   *
   * @param vertex 要获取父节点的顶点
   * @return 该顶点的父节点列表
   */
  private List<HepRelVertex> getVertexParents(HepRelVertex vertex) {
    final List<HepRelVertex> parents = new ArrayList<>(); // 父节点列表
    // 获取图中的所有前驱节点(父节点)
    final List<HepRelVertex> parentVertices =
        Graphs.predecessorListOf(graph, vertex);

    // 遍历每个父节点
    for (HepRelVertex pVertex : parentVertices) {
      RelNode parent = pVertex.getCurrentRel(); // 获取父节点的RelNode
      // 遍历父节点的每个输入
      for (int i = 0; i < parent.getInputs().size(); i++) {
        HepRelVertex child = (HepRelVertex) parent.getInputs().get(i); // 获取输入节点
        // 如果输入节点是目标节点,添加父节点到列表
        if (child == vertex) {
          parents.add(pVertex);
        }
      }
    }
    return parents; // 返回父节点列表
  }

  /**
   * 递归匹配规则的operands到RelNode
   * 这是规则匹配的核心方法,检查RelNode是否符合规则operand的要求
   *
   * 匹配策略根据childPolicy决定:
   * - ANY: 不匹配子节点,只匹配当前节点
   * - UNORDERED: 每个子operand至少匹配一个子节点(顺序不重要)
   * - 默认: 按顺序匹配子operands和子节点
   *
   * @param operand 规则的operand,定义了要匹配的模式
   * @param rel 要匹配的RelNode
   * @param bindings 输出参数:匹配的RelNode列表
   * @param nodeChildren 输出参数:RelNode到其子节点的映射
   * @return 如果匹配成功则返回true,否则返回false
   */
  private static boolean matchOperands(
      RelOptRuleOperand operand,
      RelNode rel,
      List<RelNode> bindings,
      Map<RelNode, List<RelNode>> nodeChildren) {
    // 检查当前RelNode是否匹配operand
    if (!operand.matches(rel)) {
      return false; // 不匹配,返回false
    }
    // 检查所有输入是否都是HepRelVertex
    for (RelNode input : rel.getInputs()) {
      if (!(input instanceof HepRelVertex)) {
        // 图可能已经为物化视图部分优化了。在这种情况下,
        // 输入将是一个RelNode,不应该在这里再次匹配。
        return false;
      }
    }
    // 将当前RelNode添加到绑定列表
    bindings.add(rel);
    // 获取子节点列表(强制转换为List<HepRelVertex>)
    @SuppressWarnings("unchecked")
    List<HepRelVertex> childRels = (List) rel.getInputs();
    // 根据子节点策略进行匹配
    switch (operand.childPolicy) {
    case ANY:
      // 不匹配子节点,只匹配当前节点
      return true;
    case UNORDERED:
      // 对于每个operand,至少有一个子节点必须匹配
      // 如果matchAnyChildren,通常只有一个operand
      for (RelOptRuleOperand childOperand : operand.getChildOperands()) {
        boolean match = false; // 是否找到匹配
        // 遍历所有子节点,尝试匹配当前operand
        for (HepRelVertex childRel : childRels) {
          // 递归匹配子节点
          match =
              matchOperands(
                  childOperand,
                  childRel.getCurrentRel(),
                  bindings,
                  nodeChildren);
          if (match) {
            break; // 找到匹配,跳出循环
          }
        }
        // 如果没有找到匹配,返回false
        if (!match) {
          return false;
        }
      }
      // 收集所有子节点
      final List<RelNode> children = new ArrayList<>(childRels.size());
      for (HepRelVertex childRel : childRels) {
        children.add(childRel.getCurrentRel());
      }
      // 将子节点列表添加到映射中
      nodeChildren.put(rel, children);
      return true; // 所有operand都匹配,返回true
    default:
      // 默认策略:按顺序匹配子operands和子节点
      int n = operand.getChildOperands().size(); // 子operands的数量
      // 检查子节点数量是否足够
      if (childRels.size() < n) {
        return false; // 子节点不足,返回false
      }
      // 按顺序匹配每个子operand和子节点
      for (Pair<HepRelVertex, RelOptRuleOperand> pair
          : Pair.zip(childRels, operand.getChildOperands())) {
        // 递归匹配子节点
        boolean match =
            matchOperands(
                pair.right, // 子operand
                pair.left.getCurrentRel(), // 子节点
                bindings,
                nodeChildren);
        // 如果不匹配,返回false
        if (!match) {
          return false;
        }
      }
      return true; // 所有子operands都匹配,返回true
    }
  }

  /**
   * 应用规则转换结果到查询图
   * 选择最佳转换结果(基于成本),更新图结构,并通知监听器
   *
   * 处理流程:
   * 1. 如果有多个结果,选择成本最低的一个
   * 2. 将最佳结果添加到图中
   * 3. 收缩顶点,用新顶点替换旧顶点
   * 4. 通知监听器转换完成
   *
   * @param vertex 原始顶点
   * @param call 规则调用,包含转换结果
   * @param parentTrait 父节点特征(用于转换规则)
   * @return 转换后的新顶点
   */
  private HepRelVertex applyTransformationResults(
      HepRelVertex vertex,
      HepRuleCall call,
      @Nullable RelTrait parentTrait) {
    // TODO jvs 5-Apr-2006: 选择给出最佳全局成本的那个,而不是最佳局部成本
    // 这需要"试探性"的图编辑

    assert !call.getResults().isEmpty(); // 确保有转换结果

    RelNode bestRel = null; // 最佳RelNode

    // 如果只有一个结果,不需要成本计算
    if (call.getResults().size() == 1) {
      // 不需要成本计算;跳过它以最小化遇到没有成本信息的rel的机会
      bestRel = call.getResults().get(0);
    } else {
      // 有多个结果,选择成本最低的一个
      RelOptCost bestCost = null; // 最佳成本
      final RelMetadataQuery mq = call.getMetadataQuery(); // 元数据查询
      // 遍历所有结果,计算成本
      for (RelNode rel : call.getResults()) {
        RelOptCost thisCost = getCost(rel, mq); // 计算成本
        if (LOGGER.isTraceEnabled()) {
          // 保持在isTraceEnabled中以调用getRowCount方法
          LOGGER.trace("considering {} with cumulative cost={} and rowcount={}",
              rel, thisCost, mq.getRowCount(rel));
        }
        // 跳过没有成本信息的结果
        if (thisCost == null) {
          continue;
        }
        // 如果这是第一个结果或成本更低,更新最佳结果
        if (bestRel == null || thisCost.isLt(castNonNull(bestCost))) {
          bestRel = rel;
          bestCost = thisCost;
        }
      }
    }

    // 增加转换计数
    ++nTransformations;
    // 通知监听器转换开始
    notifyTransformation(
        call,
        requireNonNull(bestRel, "bestRel"),
        true);

    // 在添加结果之前,复制顶点的父节点列表
    // 我们稍后在收缩时需要这个,以便我们只更新现有的父节点,而不是新的父节点
    // (否则可能导致循环)。还要注意根据特征过滤父节点,以防我们正在处理转换规则。
    final List<HepRelVertex> allParents =
        Graphs.predecessorListOf(graph, vertex);
    final List<HepRelVertex> parents = new ArrayList<>();
    // 过滤父节点
    for (HepRelVertex parent : allParents) {
      // 如果有父节点特征要求,进行过滤
      if (parentTrait != null) {
        RelNode parentRel = parent.getCurrentRel();
        // 如果父节点是转换器,跳过
        if (parentRel instanceof Converter) {
          // 我们不支持自动链式转换
          // 在这里将转换器作为候选父节点处理
          // 可能会导致下面的"iParentMatch"检查
          // 丢弃多父节点DAG情况下需要的新转换器
          continue;
        }
        // 如果父节点不需要转换后的特征,跳过
        if (!parentRel.getTraitSet().contains(parentTrait)) {
          // 这个父节点不需要转换后的结果
          continue;
        }
      }
      parents.add(parent); // 添加父节点到列表
    }

    // 将最佳RelNode添加到图中
    HepRelVertex newVertex = addRelToGraph(bestRel);

    // 由于公共子表达式识别,newVertex可能与其中一个父节点相同
    // (例如JoinCommuteRule添加的LogicalProject)
    // 在这种情况下,将转换视为无操作以避免创建循环
    int iParentMatch = parents.indexOf(newVertex);
    if (iParentMatch != -1) {
      // newVertex与某个父节点相同,使用该父节点
      newVertex = parents.get(iParentMatch);
    } else {
      // 收缩顶点,用新顶点替换旧顶点
      contractVertices(newVertex, vertex, parents);
    }

    // 如果有监听器,执行垃圾回收
    if (getListener() != null) {
      // 假设监听器不想看到垃圾
      collectGarbage();
    }

    // 通知监听器转换完成
    notifyTransformation(
        call,
        bestRel,
        false);

    // 输出图结构用于调试
    dumpGraph();

    return newVertex; // 返回新顶点
  }

  /**
   * 注册RelNode到规划器
   * 在HepPlanner中,这个方法被忽略,因为HepPlanner使用图结构而不是注册表
   * 这个方法主要用于告诉VolcanoPlanner如何避免无限循环
   *
   * @param rel 要注册的RelNode
   * @param equivRel 等价的RelNode(可选)
   * @return 原始的RelNode
   */
  @Override public RelNode register(
      RelNode rel,
      @Nullable RelNode equivRel) {
    // 忽略;这个调用主要是告诉Volcano如何避免无限循环
    return rel; // 返回原始RelNode
  }

  /**
   * 当RelNode被复制时调用
   * 调用注册的复制钩子函数,允许自定义复制行为
   *
   * @param rel 原始RelNode
   * @param newRel 新复制的RelNode
   */
  @Override public void onCopy(RelNode rel, RelNode newRel) {
    onCopyHook.apply(rel, newRel); // 调用复制钩子函数
  }

  /**
   * 确保RelNode已注册
   * 在HepPlanner中,这个方法直接返回RelNode,因为HepPlanner不使用注册表
   *
   * @param rel 要确保注册的RelNode
   * @param equivRel 等价的RelNode(可选)
   * @return 原始的RelNode
   */
  @Override public RelNode ensureRegistered(RelNode rel, @Nullable RelNode equivRel) {
    return rel; // 返回原始RelNode
  }

  /**
   * 检查RelNode是否已注册
   * 在HepPlanner中,这个方法总是返回true,因为HepPlanner不使用注册表
   *
   * @param rel 要检查的RelNode
   * @return 总是返回true
   */
  @Override public boolean isRegistered(RelNode rel) {
    return true; // 总是返回true
  }

  /**
   * 将RelNode及其所有子节点递归添加到查询图中
   * 这是构建查询图的核心方法,负责:
   * 1. 递归添加子节点
   * 2. 替换RelNode的输入为子节点顶点
   * 3. 检查是否已存在等价顶点(如果允许DAG)
   * 4. 创建新顶点并添加到图中
   *
   * @param rel 要添加到图的RelNode
   * @return 表示RelNode的HepRelVertex顶点
   */
  private HepRelVertex addRelToGraph(
      RelNode rel) {
    // 检查转换是否已经产生了对现有顶点的引用
    // 如果RelNode已经在图中,直接返回(它已经是HepRelVertex)
    if (graph.vertexSet().contains(rel)) {
      return (HepRelVertex) rel;
    }

    // 递归添加子节点,将rel的输入替换为对应的子节点顶点
    final List<RelNode> inputs = rel.getInputs(); // 获取原始输入列表
    final List<RelNode> newInputs = new ArrayList<>(); // 创建新的输入列表
    // 遍历每个输入,递归添加到图中
    for (RelNode input1 : inputs) {
      HepRelVertex childVertex = addRelToGraph(input1); // 递归添加子节点
      newInputs.add(childVertex); // 将子节点顶点添加到新输入列表
    }

    // 如果输入列表发生了变化,复制RelNode并更新输入
    if (!Util.equalShallow(inputs, newInputs)) {
      RelNode oldRel = rel; // 保存原始RelNode
      rel = rel.copy(rel.getTraitSet(), newInputs); // 复制RelNode并更新输入
      onCopy(oldRel, rel); // 调用复制钩子
    }
    // 第一次添加到DAG时计算摘要,
    // 否则无法获取公共子表达式的等价顶点
    rel.recomputeDigest();

    // 只有在允许DAG时才尝试查找等价RelNode
    if (!noDag) {
      // 检查图中是否已存在等价顶点
      HepRelVertex equivVertex = mapDigestToVertex.get(rel.getRelDigest());
      if (equivVertex != null) {
        // 使用现有顶点(共享公共子表达式)
        return equivVertex;
      }
    }

    // 没有等价顶点:创建一个新顶点来表示这个rel
    HepRelVertex newVertex = new HepRelVertex(rel); // 创建新顶点
    graph.addVertex(newVertex); // 将顶点添加到图中
    updateVertex(newVertex, rel); // 更新顶点信息

    // 添加边:从新顶点到每个子节点顶点
    for (RelNode input : rel.getInputs()) {
      graph.addEdge(newVertex, (HepRelVertex) input);
    }

    // 增加转换计数
    nTransformations++;
    return newVertex; // 返回新顶点
  }

  /**
   * 收缩顶点,用保留顶点替换废弃顶点
   * 这个方法在规则转换后调用,用于更新图结构
   *
   * 处理流程:
   * 1. 更新保留顶点的信息
   * 2. 更新所有父节点,将废弃顶点替换为保留顶点
   * 3. 更新图的边结构
   * 4. 如果废弃顶点是根节点,更新根节点
   *
   * 注意:废弃顶点不会立即从图中删除,因为可能仍有其他节点引用它
   * 垃圾收集会在稍后清理不可达的顶点
   *
   * @param preservedVertex 要保留的顶点(转换后的新顶点)
   * @param discardedVertex 要废弃的顶点(转换前的旧顶点)
   * @param parents 需要更新的父节点列表
   */
  private void contractVertices(
      HepRelVertex preservedVertex,
      HepRelVertex discardedVertex,
      List<HepRelVertex> parents) {
    // 如果保留顶点和废弃顶点相同,无需操作
    if (preservedVertex == discardedVertex) {
      // 无操作
      return;
    }

    // 更新保留顶点的信息
    RelNode rel = preservedVertex.getCurrentRel();
    updateVertex(preservedVertex, rel);

    // 更新废弃顶点的指定父节点
    for (HepRelVertex parent : parents) {
      RelNode parentRel = parent.getCurrentRel(); // 获取父节点的RelNode
      List<RelNode> inputs = parentRel.getInputs(); // 获取父节点的输入列表
      // 遍历父节点的每个输入
      for (int i = 0; i < inputs.size(); ++i) {
        RelNode child = inputs.get(i); // 获取输入节点
        // 如果输入节点不是废弃顶点,跳过
        if (child != discardedVertex) {
          continue;
        }
        // 将废弃顶点替换为保留顶点
        parentRel.replaceInput(i, preservedVertex);
      }
      // 清除父节点的元数据缓存
      clearCache(parent);
      // 更新图的边:删除指向废弃顶点的边,添加指向保留顶点的边
      graph.removeEdge(parent, discardedVertex);
      graph.addEdge(parent, preservedVertex);
      // 更新父节点的信息
      updateVertex(parent, parentRel);
    }

    // 注意:我们实际上不执行graph.removeVertex(discardedVertex),
    // 因为它可能仍然可以从保留顶点到达
    // 将这项工作留给垃圾收集

    // 如果废弃顶点是根节点,更新根节点
    if (discardedVertex == root) {
      root = preservedVertex;
    }
  }

  /**
   * 清除RelNode及其祖先的元数据缓存
   * 当RelNode的输入发生变化时,需要清除缓存以确保元数据查询返回正确结果
   *
   * @param vertex 要清除缓存的顶点
   */
  private void clearCache(HepRelVertex vertex) {
    // 清除顶点RelNode的元数据缓存
    RelMdUtil.clearCache(vertex.getCurrentRel());
    // 如果顶点没有需要清除的缓存,返回
    if (!RelMdUtil.clearCache(vertex)) {
      return;
    }
    // 使用广度优先遍历清除所有祖先节点的缓存
    Queue<DefaultEdge> queue =
        new ArrayDeque<>(graph.getInwardEdges(vertex)); // 获取所有入边(指向该节点的边)
    while (!queue.isEmpty()) {
      DefaultEdge edge = queue.remove(); // 取出一条边
      HepRelVertex source = (HepRelVertex) edge.source; // 获取边的源节点(父节点)
      // 清除父节点的元数据缓存
      RelMdUtil.clearCache(source.getCurrentRel());
      // 如果父节点还有需要清除的缓存,将其入边加入队列
      if (RelMdUtil.clearCache(source)) {
        queue.addAll(graph.getInwardEdges(source));
      }
    }
  }

  /**
     * 更新顶点信息
     * 当顶点的RelNode发生变化时,更新顶点、摘要映射,并通知监听器
     *
     * 处理流程:
     * 1. 如果RelNode发生变化,通知丢弃旧的RelNode
     * 2. 更新摘要映射:删除旧的映射,添加新的映射
     * 3. 如果RelNode发生变化,替换顶点的RelNode
     * 4. 通知等价关系
     *
     * @param vertex 要更新的顶点
     * @param rel 新的RelNode
     */
    private void updateVertex(HepRelVertex vertex, RelNode rel) {

      // 如果RelNode发生变化,通知丢弃旧的RelNode
      if (rel != vertex.getCurrentRel()) {

        // REVIEW jvs 5-Apr-2006: 我们稍后在垃圾收集期间会再次做这个
        // 或者我们可以摆脱标记-清除垃圾收集,通过向下遍历到这里
        // 只能到达的所有rel来精确地完成
        notifyDiscard(vertex.getCurrentRel());

      }

      // 获取旧摘要键
      RelDigest oldKey = vertex.getCurrentRel().getRelDigest();

      // 如果旧摘要映射到当前顶点,删除该映射
      if (mapDigestToVertex.get(oldKey) == vertex) {

        mapDigestToVertex.remove(oldKey);

      }
      // 当在一个规则应用中发生转换时,支持vertex2替换vertex1,
      // 但是vertex1和vertex2的当前relNode是相同的,
      // 那么摘要也是相同的。但是我们不能移除vertex2,
      // 否则在collectGC时摘要会在mapDigestToVertex中被错误地移除
      // 所以必须更新映射到顶点的摘要
      mapDigestToVertex.put(rel.getRelDigest(), vertex);

      // 如果RelNode发生变化,替换顶点的RelNode
      if (rel != vertex.getCurrentRel()) {

        vertex.replaceRel(rel);

      }

      // 通知等价关系
      notifyEquivalence(
          rel,
          vertex,
          false);

    }

  /**
   * 构建最终计划
   * 将图结构转换为RelNode树,这是优化过程的最后一步
   *
   * 处理流程:
   * 1. 通知监听器选择了这个RelNode
   * 2. 递归处理子节点,将HepRelVertex替换为RelNode
   * 3. 如果输入发生变化,清除缓存并重新计算摘要
   * 4. 返回最终的RelNode
   *
   * @param vertex 要构建的顶点
   * @return 构建好的RelNode树
   */
  private RelNode buildFinalPlan(HepRelVertex vertex) {
    // 获取顶点的RelNode
    RelNode rel = vertex.getCurrentRel();

    // 通知监听器选择了这个RelNode
    notifyChosen(rel);

    // 递归处理子节点,将rel的输入替换为对应的子节点rel
    List<RelNode> inputs = rel.getInputs(); // 获取输入列表
    boolean changed = false; // 标记是否发生了变化
    // 遍历每个输入
    for (int i = 0; i < inputs.size(); ++i) {
      RelNode child = inputs.get(i); // 获取输入节点
      // 如果输入节点不是HepRelVertex,说明已经被替换过了
      if (!(child instanceof HepRelVertex)) {
        // 已经被替换
        continue;
      }
      // 递归构建子节点的最终计划
      child = buildFinalPlan((HepRelVertex) child);
      // 替换输入节点
      rel.replaceInput(i, child);
      changed = true; // 标记发生了变化
    }
    // 如果发生了变化,清除缓存并重新计算摘要
    if (changed) {
      RelMdUtil.clearCache(rel); // 清除元数据缓存
      rel.recomputeDigest(); // 重新计算摘要
    }

    // 后置条件检查:确保rel不是HepRelVertex
    if (rel instanceof HepRelVertex) {
      throw new AssertionError("post-condition failed: " + rel);
    }
    return rel; // 返回最终的RelNode
  }

  /**
   * 执行垃圾收集
   * 使用标记-清除算法,移除从根节点不可达的顶点
   * 这可以释放内存,并确保图结构的一致性
   *
   * 处理流程:
   * 1. 检查是否需要GC(是否有新的转换)
   * 2. 从根节点开始,标记所有可达的顶点
   * 3. 收集所有不可达的顶点
   * 4. 从图中移除不可达的顶点
   * 5. 清理摘要映射
   *
   * 垃圾收集策略:
   * - 只在发生转换后执行
   * - 使用标记-清除算法
   * - 同时清理摘要映射
   */
  private void collectGarbage() {
    // 检查自上次GC以来是否有修改
    if (nTransformations == nTransformationsLastGC) {
      // 自上次GC以来没有发生修改,所以不可能有垃圾
      return;
    }
    // 更新上次GC的转换次数
    nTransformationsLastGC = nTransformations;

    LOGGER.trace("collecting garbage"); // 记录跟踪日志

    // 基本的标记-清除算法
    final Set<HepRelVertex> rootSet = new HashSet<>(); // 可达顶点集合
    HepRelVertex root = requireNonNull(this.root, "this.root"); // 获取根节点
    // 如果根节点在图中,从根节点开始标记所有可达顶点
    if (graph.vertexSet().contains(root)) {
      BreadthFirstIterator.reachable(rootSet, graph, root);
    }

    // 如果所有顶点都可达,没有垃圾可收集
    if (rootSet.size() == graph.vertexSet().size()) {
      // 所有都是可达的:没有垃圾可收集
      return;
    }
    // 收集所有不可达的顶点
    final Set<HepRelVertex> sweepSet = new HashSet<>();
    // 遍历所有顶点,找出不可达的顶点
    for (HepRelVertex vertex : graph.vertexSet()) {
      if (!rootSet.contains(vertex)) {
        // 顶点不可达,添加到清除集合
        sweepSet.add(vertex);
        // 通知丢弃该顶点的RelNode
        RelNode rel = vertex.getCurrentRel();
        notifyDiscard(rel);
      }
    }
    // 确保清除集合不为空
    assert !sweepSet.isEmpty();
    // 从图中移除所有不可达的顶点
    graph.removeAllVertices(sweepSet);
    // 更新上次GC时的图大小
    graphSizeLastGC = graph.vertexSet().size();

    // 清理摘要映射
    Iterator<Map.Entry<RelDigest, HepRelVertex>> digestIter =
        mapDigestToVertex.entrySet().iterator();
    // 遍历摘要映射,移除不可达顶点的映射
    while (digestIter.hasNext()) {
      HepRelVertex vertex = digestIter.next().getValue();
      if (sweepSet.contains(vertex)) {
        // 顶点不可达,移除映射
        digestIter.remove();
      }
    }
  }

  /**
   * 断言图中没有循环
   * 验证查询图是有向无环图(DAG),确保查询计划的语义正确性
   * 如果检测到循环,抛出断言错误
   *
   * @throws AssertionError 如果图中存在循环
   */
  private void assertNoCycles() {
    // 验证图是无环的
    final CycleDetector<HepRelVertex, DefaultEdge> cycleDetector =
        new CycleDetector<>(graph); // 创建循环检测器
    Set<HepRelVertex> cyclicVertices = cycleDetector.findCycles(); // 查找循环顶点
    // 如果没有循环,返回
    if (cyclicVertices.isEmpty()) {
      return;
    }

    // 检测到循环,抛出断言错误
    throw new AssertionError("Query graph cycle detected in HepPlanner: "
        + cyclicVertices);
  }

  /**
   * 输出图结构用于调试
   * 如果启用了跟踪级别日志,输出当前图的详细信息
   * 包括每个顶点的RelNode类型、行数和成本
   *
   * 输出格式:
   * - 使用广度优先遍历
   * - 显示每个顶点的RelNode
   * - 显示行数和累积成本
   */
  private void dumpGraph() {
    // 如果未启用跟踪日志,返回
    if (!LOGGER.isTraceEnabled()) {
      return;
    }

    // 断言图中没有循环
    assertNoCycles();

    // 获取根节点
    HepRelVertex root = this.root;
    // 如果根节点为null,记录日志并返回
    if (root == null) {
      LOGGER.trace("dumpGraph: root is null");
      return;
    }
    // 获取元数据查询
    final RelMetadataQuery mq = root.getCluster().getMetadataQuery();
    // 构建输出字符串
    final StringBuilder sb = new StringBuilder();
    sb.append("\nBreadth-first from root:  {\n");
    // 使用广度优先遍历输出每个顶点
    for (HepRelVertex vertex : BreadthFirstIterator.of(graph, root)) {
      sb.append("    ")
          .append(vertex) // 顶点标识
          .append(" = ");
      RelNode rel = vertex.getCurrentRel(); // 获取RelNode
      sb.append(rel) // RelNode类型
          .append(", rowcount=")
          .append(mq.getRowCount(rel)) // 行数
          .append(", cumulative cost=")
          .append(getCost(rel, mq)) // 累积成本
          .append('\n');
    }
    sb.append("}");
    // 输出日志
    LOGGER.trace(sb.toString());
  }

  @Deprecated // to be removed before 2.0
  @Override public void registerMetadataProviders(List<RelMetadataProvider> list) {
    list.add(0, new HepRelMetadataProvider());
  }

  @Deprecated // to be removed before 2.0
  @Override public long getRelMetadataTimestamp(RelNode rel) {
    // TODO jvs 20-Apr-2006: This is overly conservative.  Better would be
    // to keep a timestamp per HepRelVertex, and update only affected
    // vertices and all ancestors on each transformation.
    return nTransformations;
  }

  @Override public ImmutableList<RelOptMaterialization> getMaterializations() {
    return ImmutableList.copyOf(materializations);
  }

  @Override public void addMaterialization(RelOptMaterialization materialization) {
    materializations.add(materialization);
  }
}