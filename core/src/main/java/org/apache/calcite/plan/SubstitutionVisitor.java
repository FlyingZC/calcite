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
package org.apache.calcite.plan;

import org.apache.calcite.config.CalciteSystemProperty;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.RelFactories;
import org.apache.calcite.rel.mutable.Holder;
import org.apache.calcite.rel.mutable.MutableAggregate;
import org.apache.calcite.rel.mutable.MutableCalc;
import org.apache.calcite.rel.mutable.MutableFilter;
import org.apache.calcite.rel.mutable.MutableIntersect;
import org.apache.calcite.rel.mutable.MutableJoin;
import org.apache.calcite.rel.mutable.MutableMinus;
import org.apache.calcite.rel.mutable.MutableRel;
import org.apache.calcite.rel.mutable.MutableRelVisitor;
import org.apache.calcite.rel.mutable.MutableRels;
import org.apache.calcite.rel.mutable.MutableScan;
import org.apache.calcite.rel.mutable.MutableSetOp;
import org.apache.calcite.rel.mutable.MutableUnion;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexExecutor;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexLocalRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexOver;
import org.apache.calcite.rex.RexProgram;
import org.apache.calcite.rex.RexProgramBuilder;
import org.apache.calcite.rex.RexShuttle;
import org.apache.calcite.rex.RexSimplify;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.rex.RexVisitor;
import org.apache.calcite.rex.RexVisitorImpl;
import org.apache.calcite.runtime.PairList;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.fun.SqlLibraryOperators;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelBuilderFactory;
import org.apache.calcite.util.ControlFlowException;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Litmus;
import org.apache.calcite.util.Optionality;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.mapping.Mapping;
import org.apache.calcite.util.mapping.Mappings;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import static org.apache.calcite.rex.RexUtil.andNot;
import static org.apache.calcite.rex.RexUtil.removeAll;

import static java.util.Objects.requireNonNull;

/**
 * Substitutes part of a tree of relational expressions with another tree.
 *
 * <p>The call {@code new SubstitutionVisitor(target, query).go(replacement))}
 * will return {@code query} with every occurrence of {@code target} replaced
 * by {@code replacement}.
 *
 * <p>The following example shows how {@code SubstitutionVisitor} can be used
 * for materialized view recognition.
 *
 * <ul>
 * <li>query = SELECT a, c FROM t WHERE x = 5 AND b = 4</li>
 * <li>target = SELECT a, b, c FROM t WHERE x = 5</li>
 * <li>replacement = SELECT * FROM mv</li>
 * <li>result = SELECT a, c FROM mv WHERE b = 4</li>
 * </ul>
 *
 * <p>Note that {@code result} uses the materialized view table {@code mv} and a
 * simplified condition {@code b = 4}.
 *
 * <p>Uses a bottom-up matching algorithm. Nodes do not need to be identical.
 * At each level, returns the residue.
 *
 * <p>The inputs must only include the core relational operators:
 * {@link org.apache.calcite.rel.core.TableScan},
 * {@link org.apache.calcite.rel.core.Filter},
 * {@link org.apache.calcite.rel.core.Project},
 * {@link org.apache.calcite.rel.core.Calc},
 * {@link org.apache.calcite.rel.core.Join},
 * {@link org.apache.calcite.rel.core.Union},
 * {@link org.apache.calcite.rel.core.Intersect},
 * {@link org.apache.calcite.rel.core.Aggregate}.
 */
// SubstitutionVisitor（替换访问者）：用于将关系表达式树的一部分替换为另一棵树的核心类
// 主要应用于物化视图识别和查询重写场景
// 核心功能：在查询表达式中查找目标模式，并用替换表达式替换它，同时保持语义等价性
// 使用自底向上的匹配算法，节点不需要完全相同，可以在每层返回残留表达式
public class SubstitutionVisitor {
  private static final boolean DEBUG = CalciteSystemProperty.DEBUG.value(); // 调试标志，用于打印详细的匹配过程信息
  private static final Strong STRONG = new Strong() { // 强度检查器，用于确定Calc是否可以被提升到Join之上
    @Override public boolean isNull(RexInputRef ref) {
      // Here strong is used to determine if the nullable side calc can be pulled up to the join,
      // and we will adjust nullability if RexInputRef is not null,
      // so here RexInputRef is always satisfies null-if-null
      return true; // 这里返回true表示RexInputRef总是满足null-if-null语义
    }
  };

  // 默认的统一规则集合，定义了所有支持的匹配和替换规则
  public static final ImmutableList<UnifyRule> DEFAULT_RULES =
      ImmutableList.of(
          TrivialRule.INSTANCE, // 平凡规则：查询和目标完全相等
          ScanToCalcUnifyRule.INSTANCE, // Scan到Calc的匹配规则
          CalcToCalcUnifyRule.INSTANCE, // Calc到Calc的匹配规则
          JoinOnLeftCalcToJoinUnifyRule.INSTANCE, // 左侧有Calc的Join到Join的匹配规则
          JoinOnRightCalcToJoinUnifyRule.INSTANCE, // 右侧有Calc的Join到Join的匹配规则
          JoinOnCalcsToJoinUnifyRule.INSTANCE, // 两侧都有Calc的Join到Join的匹配规则
          AggregateToAggregateUnifyRule.INSTANCE, // Aggregate到Aggregate的匹配规则
          AggregateOnCalcToAggregateUnifyRule.INSTANCE, // Calc上有Aggregate的匹配规则
          UnionToUnionUnifyRule.INSTANCE, // Union到Union的匹配规则
          UnionOnCalcsToUnionUnifyRule.INSTANCE, // Calc上有Union的匹配规则
          IntersectToIntersectUnifyRule.INSTANCE, // Intersect到Intersect的匹配规则
          IntersectOnCalcsToIntersectUnifyRule.INSTANCE); // Calc上有Intersect的匹配规则

  /**
   * Factory for a builder for relational expressions.
   */
  protected final RelBuilder relBuilder; // 关系表达式构建器工厂，用于构建新的关系表达式

  private final ImmutableList<UnifyRule> rules; // 可用的统一规则列表
  private final Map<Pair<Class, Class>, List<UnifyRule>> ruleMap =
      new HashMap<>(); // 规则映射表，根据查询和目标类型快速查找适用规则
  private final RelOptCluster cluster; // 关系优化集群，包含元数据、类型系统等
  private final RexSimplify simplify; // 表达式简化器，用于简化条件表达式
  private final Holder query; // 查询表达式的包装器，包含可变的关系表达式
  private final MutableRel target; // 目标模式，需要在查询中查找并替换的模式

  /**
   * Nodes in {@link #target} that have no children.
   */
  final List<MutableRel> targetLeaves; // 目标树的叶子节点列表（没有子节点的节点）

  /**
   * Nodes in {@link #query} that have no children.
   */
  final List<MutableRel> queryLeaves; // 查询树的叶子节点列表（没有子节点的节点）

  final Map<MutableRel, MutableRel> replacementMap = new HashMap<>(); // 替换映射表，记录目标到替换节点的映射

  final Multimap<MutableRel, MutableRel> equivalents =
      LinkedHashMultimap.create(); // 等价节点集合，记录语义等价但结构不同的节点

  /** Workspace while rule is being matched.
   * Careful, re-entrant!
   * Assumes no rule needs more than 2 slots. */
  protected final MutableRel[] slots = new MutableRel[2]; // 工作槽，用于规则匹配过程中存储临时数据，假设任何规则最多需要2个槽

  /** Creates a SubstitutionVisitor with the default rule set. */
  public SubstitutionVisitor(RelNode target_, RelNode query_) {
    // 使用默认规则集和逻辑构建器创建SubstitutionVisitor
    this(target_, query_, DEFAULT_RULES, RelFactories.LOGICAL_BUILDER);
  }

  /** Creates a SubstitutionVisitor with the default logical builder. */
  public SubstitutionVisitor(RelNode target_, RelNode query_,
      ImmutableList<UnifyRule> rules) {
    // 使用自定义规则集和默认逻辑构建器创建SubstitutionVisitor
    this(target_, query_, rules, RelFactories.LOGICAL_BUILDER);
  }

  public SubstitutionVisitor(RelNode target_, RelNode query_,
      ImmutableList<UnifyRule> rules, RelBuilderFactory relBuilderFactory) {
    // 完整的构造方法，初始化所有成员变量
    this.cluster = target_.getCluster(); // 获取关系优化集群
    final RexExecutor executor =
        Util.first(cluster.getPlanner().getExecutor(), RexUtil.EXECUTOR); // 获取表达式执行器
    final RelOptPredicateList predicates = RelOptPredicateList.EMPTY; // 初始谓词列表为空
    this.simplify =
        new RexSimplify(cluster.getRexBuilder(), predicates, executor); // 创建表达式简化器
    this.rules = rules; // 保存规则列表
    this.query = Holder.of(MutableRels.toMutable(query_)); // 将查询转换为可变关系表达式
    this.target = MutableRels.toMutable(target_); // 将目标转换为可变关系表达式
    this.relBuilder = relBuilderFactory.create(cluster, null); // 创建关系表达式构建器
    final Set<@Nullable MutableRel> parents = Sets.newIdentityHashSet(); // 父节点集合
    final List<MutableRel> allNodes = new ArrayList<>(); // 所有节点列表
    final MutableRelVisitor visitor =
        new MutableRelVisitor() { // 创建访问器来遍历关系表达式树
          @Override public void visit(@Nullable MutableRel node) {
            requireNonNull(node, "node");
            parents.add(node.getParent()); // 记录父节点
            allNodes.add(node); // 记录所有节点
            super.visit(node);
          }
        };
    visitor.go(target); // 遍历目标树

    // Populate the list of leaves in the tree under "target".
    // Leaves are all nodes that are not parents.
    // For determinism, it is important that the list is in scan order.
    allNodes.removeAll(parents); // 移除父节点，剩下的就是叶子节点
    targetLeaves = ImmutableList.copyOf(allNodes); // 保存目标树的叶子节点

    allNodes.clear(); // 清空节点列表
    parents.clear(); // 清空父节点集合
    visitor.go(query); // 遍历查询树
    allNodes.removeAll(parents); // 移除父节点，剩下的就是叶子节点
    queryLeaves = ImmutableList.copyOf(allNodes); // 保存查询树的叶子节点
  }

  void register(MutableRel result, MutableRel query) {
    // 注册替换结果，当前实现为空
  }

  /**
   * Maps a condition onto a target.
   *
   * <p>If condition is stronger than target, returns the residue.
   * If it is equal to target, returns the expression that evaluates to
   * the constant {@code true}. If it is weaker than target, returns
   * {@code null}.
   *
   * <p>The terms satisfy the relation
   *
   * <blockquote>
   * <pre>{@code condition = target AND residue}</pre>
   * </blockquote>
   *
   * <p>and {@code residue} must be as weak as possible.
   *
   * <p>Example #1: condition stronger than target
   * <ul>
   * <li>condition: x = 1 AND y = 2</li>
   * <li>target: x = 1</li>
   * <li>residue: y = 2</li>
   * </ul>
   *
   * <p>Note that residue {@code x > 0 AND y = 2} would also satisfy the
   * relation {@code condition = target AND residue} but is stronger than
   * necessary, so we prefer {@code y = 2}.
   *
   * <p>Example #2: target weaker than condition (valid, but not currently
   * implemented)
   * <ul>
   * <li>condition: x = 1</li>
   * <li>target: x = 1 OR z = 3</li>
   * <li>residue: x = 1</li>
   * </ul>
   *
   * <p>Example #3: condition and target are equivalent
   * <ul>
   * <li>condition: x = 1 AND y = 2</li>
   * <li>target: y = 2 AND x = 1</li>
   * <li>residue: TRUE</li>
   * </ul>
   *
   * <p>Example #4: condition weaker than target
   * <ul>
   * <li>condition: x = 1</li>
   * <li>target: x = 1 AND y = 2</li>
   * <li>residue: null (i.e. no match)</li>
   * </ul>
   *
   * <p>There are many other possible examples. It amounts to solving
   * whether {@code condition AND NOT target} can ever evaluate to
   * true, and therefore is a form of the NP-complete
   * <a href="http://en.wikipedia.org/wiki/Satisfiability">Satisfiability</a>
   * problem.
   */
  // splitFilter（拆分过滤器）：将条件表达式映射到目标表达式上
  // 如果condition比target强，返回残留条件
  // 如果condition等于target，返回TRUE
  // 如果condition比target弱，返回null（无法匹配）
  // 满足关系：condition = target AND residue
  // 这是一个NP完全的可满足性问题
  @VisibleForTesting
  public static @Nullable RexNode splitFilter(final RexSimplify simplify,
      RexNode condition, RexNode target) {
    final RexBuilder rexBuilder = simplify.rexBuilder;
    condition = simplify.simplify(condition);
    target = simplify.simplify(target);
    RexNode condition2 = canonizeNode(rexBuilder, condition);
    RexNode target2 = canonizeNode(rexBuilder, target);

    // First, try splitting into ORs.
    // Given target    c1 OR c2 OR c3 OR c4
    // and condition   c2 OR c4
    // residue is      c2 OR c4
    // Also deals with case target [x] condition [x] yields residue [true].
    RexNode z = splitOr(rexBuilder, condition2, target2);
    if (z != null) {
      return z;
    }

    if (isEquivalent(condition2, target2)) {
      return rexBuilder.makeLiteral(true);
    }

    RexNode x = andNot(rexBuilder, target2, condition2);
    if (mayBeSatisfiable(x)) {
      RexNode x2 =
          RexUtil.composeConjunction(rexBuilder,
              ImmutableList.of(condition2, target2));
      RexNode r =
          canonizeNode(rexBuilder, simplify.simplifyUnknownAsFalse(x2));
      if (!r.isAlwaysFalse() && isEquivalent(condition2, r)) {
        List<RexNode> conjs = RelOptUtil.conjunctions(r);
        for (RexNode e : RelOptUtil.conjunctions(target2)) {
          removeAll(conjs, e);
        }
        return RexUtil.composeConjunction(rexBuilder, conjs);
      }
    }
    return null;
  }

  /**
   * Reorders some of the operands in this expression so structural comparison,
   * i.e., based on string representation, can be more precise.
   */
  // canonizeNode（规范化节点）：重新排序表达式中的操作数，使结构比较（基于字符串表示）更精确
  private static RexNode canonizeNode(RexBuilder rexBuilder, RexNode condition) {
    switch (condition.getKind()) {
    case AND:
    case OR: {
      RexCall call = (RexCall) condition;
      NavigableMap<String, RexNode> newOperands = new TreeMap<>();
      for (RexNode operand : call.operands) {
        operand = canonizeNode(rexBuilder, operand);
        newOperands.put(operand.toString(), operand);
      }
      if (newOperands.size() < 2) {
        return newOperands.values().iterator().next();
      }
      return rexBuilder.makeCall(call.getParserPosition(), call.getOperator(),
          ImmutableList.copyOf(newOperands.values()));
    }
    case EQUALS:
    case NOT_EQUALS:
    case LESS_THAN:
    case GREATER_THAN:
    case LESS_THAN_OR_EQUAL:
    case GREATER_THAN_OR_EQUAL: {
      RexCall call = (RexCall) condition;
      RexNode left = canonizeNode(rexBuilder, call.getOperands().get(0));
      RexNode right = canonizeNode(rexBuilder, call.getOperands().get(1));
      call =
          (RexCall) rexBuilder.makeCall(call.getParserPosition(), call.getOperator(), left, right);

      if (left.toString().compareTo(right.toString()) <= 0) {
        return call;
      }
      final RexNode result = RexUtil.invert(rexBuilder, call);
      if (result == null) {
        throw new NullPointerException("RexUtil.invert returned null for " + call);
      }
      return result;
    }
    case SEARCH: {
      final RexNode e = RexUtil.expandSearch(rexBuilder, null, condition);
      return canonizeNode(rexBuilder, e);
    }
    case CHECKED_PLUS:
    case CHECKED_TIMES:
    case PLUS:
    case TIMES: {
      RexCall call = (RexCall) condition;
      RexNode left = canonizeNode(rexBuilder, call.getOperands().get(0));
      RexNode right = canonizeNode(rexBuilder, call.getOperands().get(1));

      if (left.toString().compareTo(right.toString()) <= 0) {
        return rexBuilder.makeCall(call.getParserPosition(), call.getOperator(), left, right);
      }

      RexNode newCall =
          rexBuilder.makeCall(call.getParserPosition(), call.getOperator(), right, left);
      // new call should not be used if its inferred type is not same as old
      if (!newCall.getType().equals(call.getType())) {
        return call;
      }
      return newCall;
    }
    default:
      return condition;
    }
  }

  // splitOr（拆分或）：处理OR表达式的拆分
  // 用于在条件是OR表达式时进行特殊处理
  private static @Nullable RexNode splitOr(
      final RexBuilder rexBuilder, RexNode condition, RexNode target) {
    List<RexNode> conditions = RelOptUtil.disjunctions(condition);
    int conditionsLength = conditions.size();
    int targetsLength = 0;
    for (RexNode e : RelOptUtil.disjunctions(target)) {
      removeAll(conditions, e);
      targetsLength++;
    }
    if (conditions.isEmpty() && conditionsLength == targetsLength) {
      return rexBuilder.makeLiteral(true);
    } else if (conditions.isEmpty()) {
      return condition;
    }
    return null;
  }

  private static boolean isEquivalent(RexNode condition, RexNode target) {
    // isEquivalent方法：判断两个条件表达式是否等价
    // Example:
    //  e: x = 1 AND y = 2 AND z = 3 AND NOT (x = 1 AND y = 2)
    //  disjunctions: {x = 1, y = 2, z = 3}
    //  notDisjunctions: {x = 1 AND y = 2}
    final Set<String> conditionDisjunctions =
        new HashSet<>(RexUtil.strings(RelOptUtil.conjunctions(condition))); // 获取条件的合取项
    final Set<String> targetDisjunctions =
        new HashSet<>(RexUtil.strings(RelOptUtil.conjunctions(target))); // 获取目标的合取项
    return conditionDisjunctions.equals(targetDisjunctions); // 比较是否相等
  }

  /**
   * Returns whether a boolean expression ever returns true.
   *
   * <p>This method may give false positives. For instance, it will say
   * that {@code x = 5 AND x > 10} is satisfiable, because at present it
   * cannot prove that it is not.
   */
  // mayBeSatisfiable（可能可满足）：返回布尔表达式是否可能返回true
  // 此方法可能产生假阳性。例如，它会说x = 5 AND x > 10是可满足的，因为目前无法证明它不是
  public static boolean mayBeSatisfiable(RexNode e) {
    // Example:
    //  e: x = 1 AND y = 2 AND z = 3 AND NOT (x = 1 AND y = 2)
    //  disjunctions: {x = 1, y = 2, z = 3}
    //  notDisjunctions: {x = 1 AND y = 2}
    final List<RexNode> disjunctions = new ArrayList<>();
    final List<RexNode> notDisjunctions = new ArrayList<>();
    RelOptUtil.decomposeConjunction(e, disjunctions, notDisjunctions);

    // If there is a single FALSE or NOT TRUE, the whole expression is
    // always false.
    for (RexNode disjunction : disjunctions) {
      switch (disjunction.getKind()) {
      case LITERAL:
        if (!RexLiteral.isNullLiteral(disjunction)
            && !RexLiteral.booleanValue(disjunction)) {
          return false;
        }
        break;
      default:
        break;
      }
    }
    for (RexNode disjunction : notDisjunctions) {
      switch (disjunction.getKind()) {
      case LITERAL:
        if (!RexLiteral.isNullLiteral(disjunction)
            && RexLiteral.booleanValue(disjunction)) {
          return false;
        }
        break;
      default:
        break;
      }
    }
    // If one of the not-disjunctions is a disjunction that is wholly
    // contained in the disjunctions list, the expression is not
    // satisfiable.
    //
    // Example #1. x AND y AND z AND NOT (x AND y)  - not satisfiable
    // Example #2. x AND y AND NOT (x AND y)        - not satisfiable
    // Example #3. x AND y AND NOT (x AND y AND z)  - may be satisfiable
    for (RexNode notDisjunction : notDisjunctions) {
      final List<RexNode> disjunctions2 =
          RelOptUtil.conjunctions(notDisjunction);
      if (disjunctions.containsAll(disjunctions2)) {
        return false;
      }
    }
    return true;
  }

  public @Nullable RelNode go0(RelNode replacement_) {
    assert false; // not called
    MutableRel replacement = MutableRels.toMutable(replacement_); // 转换为可变关系表达式
    assert equalType(
        "target", target, "replacement", replacement, Litmus.THROW); // 检查类型是否相等
    replacementMap.put(target, replacement); // 记录替换映射
    final UnifyResult unifyResult = matchRecurse(target); // 递归匹配目标
    if (unifyResult == null) {
      return null; // 匹配失败
    }
    final MutableRel node0 = unifyResult.result; // 获取匹配结果
    MutableRel node = node0; // replaceAncestors(node0);
    if (DEBUG) {
      System.out.println("Convert: query:\n"
          + query.deep()
          + "\nunify.query:\n"
          + unifyResult.call.query.deep()
          + "\nunify.result:\n"
          + unifyResult.result.deep()
          + "\nunify.target:\n"
          + unifyResult.call.target.deep()
          + "\nnode0:\n"
          + node0.deep()
          + "\nnode:\n"
          + node.deep()); // 打印调试信息
    }
    return MutableRels.fromMutable(node, relBuilder); // 转换回不可变关系表达式
  }

  /**
   * Returns a list of all possible rels that result from substituting the
   * matched RelNode with the replacement RelNode within the query.
   *
   * <p>For example, the substitution result of A join B, while A and B
   * are both a qualified match for replacement R, is R join B, R join R,
   * A join R.
   */
  // go方法：执行替换操作，返回所有可能的替换结果
  // 例如：A join B中A和B都可以被R替换，则结果包括R join B, R join R, A join R
  @SuppressWarnings("MixedMutabilityReturnType")
  public List<RelNode> go(RelNode replacement_) {
    List<List<Replacement>> matches = go(MutableRels.toMutable(replacement_));
    if (matches.isEmpty()) {
      return ImmutableList.of();
    }
    List<RelNode> sub = new ArrayList<>();
    sub.add(MutableRels.fromMutable(query.getInput(), relBuilder));
    reverseSubstitute(relBuilder, query, matches, sub, 0, matches.size());
    return sub;
  }

  /**
   * Substitutes the query with replacement whenever possible but meanwhile
   * keeps track of all the substitutions and their original rel before
   * replacement, so that in later processing stage, the replacement can be
   * recovered individually to produce a list of all possible rels with
   * substitution in different places.
   */
  private List<List<Replacement>> go(MutableRel replacement) {
    assert equalType(
        "target", target, "replacement", replacement, Litmus.THROW);
    final List<MutableRel> queryDescendants = MutableRels.descendants(query);
    final List<MutableRel> targetDescendants = MutableRels.descendants(target);

    // Populate "equivalents" with (q, t) for each query descendant q and
    // target descendant t that are equal.
    final Map<MutableRel, MutableRel> map = new HashMap<>();
    for (MutableRel queryDescendant : queryDescendants) {
      map.put(queryDescendant, queryDescendant);
    }
    for (MutableRel targetDescendant : targetDescendants) {
      MutableRel queryDescendant = map.get(targetDescendant);
      if (queryDescendant != null) {
        assert rowTypesAreEquivalent(
            queryDescendant, targetDescendant, Litmus.THROW);
        equivalents.put(queryDescendant, targetDescendant);
      }
    }
    map.clear();

    final List<Replacement> attempted = new ArrayList<>();
    List<List<Replacement>> substitutions = new ArrayList<>();

    for (;;) {
      int count = 0;
      MutableRel queryDescendant = query;
    outer:
      while (queryDescendant != null) {
        for (Replacement r : attempted) {
          if (r.stopTrying && queryDescendant == r.after) {
            // This node has been replaced by previous iterations in the
            // hope to match its ancestors and stopTrying indicates
            // there's no need to be matched again.
            queryDescendant = MutableRels.preOrderTraverseNext(queryDescendant);
            continue outer;
          }
        }
        final MutableRel next = MutableRels.preOrderTraverseNext(queryDescendant);
        final MutableRel childOrNext =
            queryDescendant.getInputs().isEmpty()
                ? next : queryDescendant.getInputs().get(0);
        for (MutableRel targetDescendant : targetDescendants) {
          for (UnifyRule rule
              : applicableRules(queryDescendant, targetDescendant)) {
            UnifyRuleCall call =
                rule.match(this, queryDescendant, targetDescendant);
            if (call != null) {
              final UnifyResult result = rule.apply(call);
              if (result != null) {
                ++count;
                attempted.add(
                    new Replacement(result.call.query, result.result, result.stopTrying));
                result.call.query.replaceInParent(result.result);

                // Replace previous equivalents with new equivalents, higher up
                // the tree.
                for (int i = 0; i < rule.slotCount; i++) {
                  Collection<MutableRel> equi = equivalents.get(slots[i]);
                  if (!equi.isEmpty()) {
                    equivalents.remove(slots[i], equi.iterator().next());
                  }
                }
                assert rowTypesAreEquivalent(result.result, result.call.query, Litmus.THROW);
                equivalents.put(result.result, result.call.query);
                if (targetDescendant == target) {
                  // A real substitution happens. We purge the attempted
                  // replacement list and add them into substitution list.
                  // Meanwhile, we stop matching the descendants and jump
                  // to the next subtree in pre-order traversal.
                  if (!target.equals(replacement)) {
                    Replacement r =
                        replace(query.getInput(), target, replacement.clone());
                    if (r == null) {
                      throw new AssertionError(rule + " should have returned "
                          + "a result containing the target.");
                    }
                    attempted.add(r);
                  }
                  substitutions.add(ImmutableList.copyOf(attempted));
                  attempted.clear();
                  queryDescendant = next;
                  continue outer;
                }
                // We will try walking the query tree all over again to see
                // if there can be any substitutions after the replacement
                // attempt.
                break outer;
              }
            }
          }
        }
        queryDescendant = childOrNext;
      }
      // Quit the entire loop if:
      // 1) we have walked the entire query tree with one or more successful
      //    substitutions, thus count != 0 && attempted.isEmpty();
      // 2) we have walked the entire query tree but have made no replacement
      //    attempt, thus count == 0 && attempted.isEmpty();
      // 3) we had done some replacement attempt in a previous walk, but in
      //    this one we have not found any potential matches or substitutions,
      //    thus count == 0 && !attempted.isEmpty().
      if (count == 0 || attempted.isEmpty()) {
        break;
      }
    }
    if (!attempted.isEmpty()) {
      // We had done some replacement attempt in the previous walk, but that
      // did not lead to any substitutions in this walk, so we need to recover
      // the replacement.
      undoReplacement(attempted);
    }
    return substitutions;
  }

  /**
   * Equivalence checking for row types, but except for the field names.
   */
  // rowTypesAreEquivalent（行类型等价）：检查行类型是否等价，但不考虑字段名
  private static boolean rowTypesAreEquivalent(
      MutableRel rel0, MutableRel rel1, Litmus litmus) {
    if (rel0.rowType.getFieldCount() != rel1.rowType.getFieldCount()) {
      return litmus.fail("Mismatch for column count: [{}]", Pair.of(rel0, rel1));
    }
    for (Pair<RelDataTypeField, RelDataTypeField> pair
        : Pair.zip(rel0.rowType.getFieldList(), rel1.rowType.getFieldList())) {
      if (!pair.left.getType().equals(pair.right.getType())) {
        return litmus.fail("Mismatch for column type: [{}]", Pair.of(rel0, rel1));
      }
    }
    return litmus.succeed();
  }

  /**
   * Represents a replacement action: before &rarr; after.
   * {@code stopTrying} indicates whether there's no need
   * to do matching for the same query node again.
   */
  // Replacement（替换）：表示替换操作：替换前 -> 替换后
  // stopTrying指示是否需要对相同的查询节点再次进行匹配
  static class Replacement {
    final MutableRel before; // 替换前的关系表达式
    final MutableRel after; // 替换后的关系表达式
    final boolean stopTrying; // 是否停止尝试

    Replacement(MutableRel before, MutableRel after) {
      this(before, after, true); // 默认stopTrying为true
    }

    Replacement(MutableRel before, MutableRel after, boolean stopTrying) {
      this.before = before; // 初始化替换前
      this.after = after; // 初始化替换后
      this.stopTrying = stopTrying; // 初始化停止标志
    }
  }

  /** Within a relational expression {@code query}, replaces occurrences of
   * {@code find} with {@code replace}.
   *
   * <p>Assumes relational expressions (and their descendants) are not null.
   * Does not handle cycles. */
  // replace（替换）：在关系表达式query中，将所有出现的find替换为replace
  // 假设关系表达式（及其子孙）不为null
  // 不处理循环
  public static @Nullable Replacement replace(MutableRel query, MutableRel find,
      MutableRel replace) {
    if (find.equals(replace)) {
      // Short-cut common case.
      return null;
    }
    assert equalType("find", find, "replace", replace, Litmus.THROW);
    return replaceRecurse(query, find, replace);
  }

  /** Helper for {@link #replace}. */
  // replaceRecurse（递归替换）：replace方法的辅助方法，递归地在树中查找并替换
  private static @Nullable Replacement replaceRecurse(MutableRel query,
      MutableRel find, MutableRel replace) {
    if (find.equals(query)) {
      query.replaceInParent(replace);
      return new Replacement(query, replace);
    }
    for (MutableRel input : query.getInputs()) {
      Replacement r = replaceRecurse(input, find, replace);
      if (r != null) {
        return r;
      }
    }
    return null;
  }

  // undoReplacement（撤销替换）：撤销替换操作，恢复原始状态
  private static void undoReplacement(List<Replacement> replacement) {
    for (int i = replacement.size() - 1; i >= 0; i--) {
      Replacement r = replacement.get(i);
      r.after.replaceInParent(r.before);
    }
  }

  // redoReplacement（重做替换）：重新执行替换操作
  private static void redoReplacement(List<Replacement> replacement) {
    for (Replacement r : replacement) {
      r.before.replaceInParent(r.after);
    }
  }

  // reverseSubstitute（反向替换）：生成所有可能的替换组合
  // 通过递归地撤销和重做替换来生成所有可能的查询变体
  private static void reverseSubstitute(RelBuilder relBuilder, Holder query,
      List<List<Replacement>> matches, List<RelNode> sub,
      int replaceCount, int maxCount) {
    if (matches.isEmpty()) {
      return;
    }
    final List<List<Replacement>> rem = matches.subList(1, matches.size());
    reverseSubstitute(relBuilder, query, rem, sub, replaceCount, maxCount);
    undoReplacement(matches.get(0));
    if (++replaceCount < maxCount) {
      sub.add(MutableRels.fromMutable(query.getInput(), relBuilder));
    }
    reverseSubstitute(relBuilder, query, rem, sub, replaceCount, maxCount);
    redoReplacement(matches.get(0));
  }

  // matchRecurse（递归匹配）：递归地匹配目标关系表达式
  // 自底向上地遍历目标树，尝试在每个级别匹配查询节点
  private @Nullable UnifyResult matchRecurse(MutableRel target) {
    assert false; // not called
    final List<MutableRel> targetInputs = target.getInputs();
    MutableRel queryParent = null;

    for (MutableRel targetInput : targetInputs) {
      UnifyResult unifyResult = matchRecurse(targetInput);
      if (unifyResult == null) {
        return null;
      }
      queryParent = unifyResult.call.query.replaceInParent(unifyResult.result);
    }

    if (targetInputs.isEmpty()) {
      for (MutableRel queryLeaf : queryLeaves) {
        for (UnifyRule rule : applicableRules(queryLeaf, target)) {
          final UnifyResult x = apply(rule, queryLeaf, target);
          if (x != null) {
            if (DEBUG) {
              System.out.println("Rule: " + rule
                  + "\nQuery:\n"
                  + queryParent
                  + (x.call.query != queryParent
                     ? "\nQuery (original):\n"
                     + queryParent
                     : "")
                  + "\nTarget:\n"
                  + target.deep()
                  + "\nResult:\n"
                  + x.result.deep()
                  + "\n");
            }
            return x;
          }
        }
      }
    } else {
      requireNonNull(queryParent, "queryParent");
      for (UnifyRule rule : applicableRules(queryParent, target)) {
        final UnifyResult x = apply(rule, queryParent, target);
        if (x != null) {
          if (DEBUG) {
            System.out.println(
                "Rule: " + rule
                + "\nQuery:\n"
                + queryParent.deep()
                + (x.call.query != queryParent
                   ? "\nQuery (original):\n"
                   + queryParent.toString()
                   : "")
                + "\nTarget:\n"
                + target.deep()
                + "\nResult:\n"
                + x.result.deep()
                + "\n");
          }
          return x;
        }
      }
    }
    if (DEBUG) {
      System.out.println(
          "Unify failed:"
          + "\nQuery:\n"
          + queryParent
          + "\nTarget:\n"
          + target.toString()
          + "\n");
    }
    return null;
  }

  // apply（应用规则）：应用统一规则到查询节点
  // 创建规则调用并执行规则
  private @Nullable UnifyResult apply(UnifyRule rule, MutableRel query,
      MutableRel target) {
    final UnifyRuleCall call =
        new UnifyRuleCall(rule, query, target, ImmutableList.of());
    return rule.apply(call);
  }

  // applicableRules（适用规则）：获取适用于给定查询和目标类型的规则列表
  // 使用缓存提高性能
  private List<UnifyRule> applicableRules(MutableRel query,
      MutableRel target) {
    final Class queryClass = query.getClass();
    final Class targetClass = target.getClass();
    final Pair<Class, Class> key = Pair.of(queryClass, targetClass);
    List<UnifyRule> list = ruleMap.get(key);
    if (list == null) {
      final ImmutableList.Builder<UnifyRule> builder =
          ImmutableList.builder();
      for (UnifyRule rule : rules) {
        //noinspection unchecked
        if (mightMatch(rule, queryClass, targetClass)) {
          builder.add(rule);
        }
      }
      list = builder.build();
      ruleMap.put(key, list);
    }
    return list;
  }

  // mightMatch（可能匹配）：检查规则是否可能匹配给定的查询和目标类
  // 基于类的继承关系进行预检查
  private static boolean mightMatch(UnifyRule rule,
      Class queryClass, Class targetClass) {
    return rule.queryOperand.clazz.isAssignableFrom(queryClass)
        && rule.targetOperand.clazz.isAssignableFrom(targetClass);
  }

  /** Exception thrown to exit a matcher. Not really an error. */
  // MatchFailed（匹配失败）：用于退出匹配器的异常，不是真正的错误
  protected static class MatchFailed extends ControlFlowException {
    @SuppressWarnings("ThrowableInstanceNeverThrown")
    public static final MatchFailed INSTANCE = new MatchFailed(); // 单例实例
  }

  /** Rule that attempts to match a query relational expression
   * against a target relational expression.
   *
   * <p>The rule declares the query and target types; this allows the
   * engine to fire only a few rules in a given context.
   */
  // UnifyRule（统一规则）：尝试将查询关系表达式匹配到目标关系表达式的抽象规则类
  // 规则声明了查询和目标的类型，允许引擎在给定上下文中只触发少量规则
  public abstract static class UnifyRule {
    protected final int slotCount; // 规则需要的槽数量（用于存储临时数据）
      protected final Operand queryOperand; // 查询操作数，定义查询端的匹配模式
      protected final Operand targetOperand; // 目标操作数，定义目标端的匹配模式
    
      protected UnifyRule(int slotCount, Operand queryOperand,
          Operand targetOperand) {
        this.slotCount = slotCount; // 初始化槽数量
        this.queryOperand = queryOperand; // 初始化查询操作数
        this.targetOperand = targetOperand; // 初始化目标操作数
      }
    
      /**
       * Applies this rule to a particular node in a query. The goal is
       * to convert {@code query} into {@code target}. Before the rule is
       * invoked, Calcite has made sure that query's children are equivalent
       * to target's children.
       *
       * <p>There are 3 possible outcomes:
       *
       * <ul>
       *
       * <li>{@code query} already exactly matches {@code target}; returns
       * {@code target}</li>
       *
       * <li>{@code query} is sufficiently close to a match for
       * {@code target}; returns {@code target}</li>
       *
       * <li>{@code query} cannot be made to match {@code target}; returns
       * null</li>
       *
       * </ul>
       *
       * <p>REVIEW: Is possible that we match query PLUS one or more of its
       * ancestors?
       *
       * @param call Input parameters
       */
      // apply方法：将规则应用到查询中的特定节点
      // 目标是将query转换为target
      // 在规则被调用之前，Calcite已经确保query的子节点与target的子节点等价
      // 有三种可能的结果：
      // 1. query已经完全匹配target，返回target
      // 2. query足够接近匹配target，返回target
      // 3. query无法匹配target，返回null
      protected abstract @Nullable UnifyResult apply(UnifyRuleCall call);
    protected @Nullable UnifyRuleCall match(SubstitutionVisitor visitor, MutableRel query,
        MutableRel target) {
      if (queryOperand.matches(visitor, query)) {
        if (targetOperand.matches(visitor, target)) {
          return visitor.new UnifyRuleCall(this, query, target,
              copy(visitor.slots, slotCount));
        }
      }
      return null;
    }

    protected <E> ImmutableList<E> copy(E[] slots, int slotCount) {
      // Optimize if there are 0 or 1 slots.
      switch (slotCount) {
      case 0:
        return ImmutableList.of();
      case 1:
        return ImmutableList.of(slots[0]);
      default:
        return ImmutableList.copyOf(slots).subList(0, slotCount);
      }
    }
  }

  /**
   * Arguments to an application of a {@link UnifyRule}.
   */
  // UnifyRuleCall（统一规则调用）：UnifyRule应用的参数封装类
  public class UnifyRuleCall {
    protected final UnifyRule rule; // 正在应用的规则
    public final MutableRel query; // 查询节点
    public final MutableRel target; // 目标节点
    protected final ImmutableList<MutableRel> slots; // 工作槽数组

    public UnifyRuleCall(UnifyRule rule, MutableRel query, MutableRel target,
        ImmutableList<MutableRel> slots) {
      this.rule = requireNonNull(rule, "rule"); // 初始化规则
      this.query = requireNonNull(query, "query"); // 初始化查询
      this.target = requireNonNull(target, "target"); // 初始化目标
      this.slots = requireNonNull(slots, "slots"); // 初始化槽
    }

    public UnifyResult result(MutableRel result) {
      // 创建统一结果，默认stopTrying为true
      return result(result,  true);
    }

    public UnifyResult result(MutableRel result, boolean stopTrying) {
      // 创建统一结果
      assert MutableRels.contains(result, target); // 确保结果包含目标
      assert equalType("result", result, "query", query,
          Litmus.THROW); // 确保类型相等
      MutableRel replace = replacementMap.get(target); // 获取替换映射
      if (replace != null) {
        assert false; // replacementMap is always empty
        // result =
        replace(result, target, replace); // 执行替换
      }
      register(result, query); // 注册结果
      return new UnifyResult(this, result, stopTrying); // 返回统一结果
    }

    /**
     * Creates a {@link UnifyRuleCall} based on the parent of {@code query}.
     */
    // create方法：基于query的父节点创建UnifyRuleCall
    public UnifyRuleCall create(MutableRel query) {
      return new UnifyRuleCall(rule, query, target, slots);
    }

    public RelOptCluster getCluster() {
      // 获取关系优化集群
      return cluster;
    }

    public RexSimplify getSimplify() {
      // 获取表达式简化器
      return simplify;
    }
  }

  /**
   * Result of an application of a {@link UnifyRule} indicating that the
   * rule successfully matched {@code query} against {@code target} and
   * generated a {@code result} that is equivalent to {@code query} and
   * contains {@code target}. {@code stopTrying} indicates whether there's
   * no need to do matching for the same query node again.
   */
  // UnifyResult（统一结果）：UnifyRule应用的结果类
  // 表示规则成功将query匹配到target，并生成了与query等价且包含target的result
  // stopTrying指示是否需要对相同的查询节点再次进行匹配
  public static class UnifyResult {
    private final UnifyRuleCall call; // 规则调用
    private final MutableRel result; // 匹配结果
    private final boolean stopTrying; // 是否停止尝试

    UnifyResult(UnifyRuleCall call, MutableRel result, boolean stopTrying) {
      this.call = call; // 初始化规则调用
      assert equalType("query", call.query, "result", result,
          Litmus.THROW); // 确保类型相等
      this.result = result; // 初始化结果
      this.stopTrying = stopTrying; // 初始化停止标志
    }
  }

  /** Abstract base class for implementing {@link UnifyRule}. */
  // AbstractUnifyRule（抽象统一规则）：实现UnifyRule的抽象基类
  // 提供了工具方法和验证逻辑
  public abstract static class AbstractUnifyRule extends UnifyRule {
    @SuppressWarnings("method.invocation.invalid")
    protected AbstractUnifyRule(Operand queryOperand, Operand targetOperand,
        int slotCount) {
      super(slotCount, queryOperand, targetOperand); // 调用父类构造方法
      //noinspection AssertWithSideEffects
      assert isValid(); // 验证规则的有效性
    }

    protected boolean isValid() {
      // 验证规则的有效性
      final SlotCounter slotCounter = new SlotCounter(); // 创建槽计数器
      slotCounter.visit(queryOperand); // 访问查询操作数
      assert slotCounter.queryCount == slotCount; // 验证查询槽数量
      assert slotCounter.targetCount == 0; // 验证目标槽数量为0
      slotCounter.queryCount = 0; // 重置查询计数
      slotCounter.visit(targetOperand); // 访问目标操作数
      assert slotCounter.queryCount == 0; // 验证查询槽数量为0
      assert slotCounter.targetCount == slotCount; // 验证目标槽数量
      return true;
    }

    /** Creates an operand with given inputs. */
    // operand方法：创建具有给定输入的操作数
    protected static Operand operand(Class<? extends MutableRel> clazz,
        Operand... inputOperands) {
      return new InternalOperand(clazz, ImmutableList.copyOf(inputOperands));
    }

    /** Creates an operand that doesn't check inputs. */
    // any方法：创建不检查输入的操作数
    protected static Operand any(Class<? extends MutableRel> clazz) {
      return new AnyOperand(clazz);
    }

    /** Creates an operand that matches a relational expression in the query. */
    // query方法：创建匹配查询中关系表达式的操作数
    protected static Operand query(int ordinal) {
      return new QueryOperand(ordinal);
    }

    /** Creates an operand that matches a relational expression in the
     * target. */
    // target方法：创建匹配目标中关系表达式的操作数
    protected static Operand target(int ordinal) {
      return new TargetOperand(ordinal);
    }
  }

  /** Implementation of {@link UnifyRule} that matches if the query is already
   * equal to the target.
   *
   * <p>Matches scans to the same table, because these will be
   * {@link MutableScan}s with the same
   * {@link org.apache.calcite.rel.core.TableScan} instance.
   */
  // TrivialRule（平凡规则）：当查询已经等于目标时匹配的规则
  // 匹配到同一表的扫描，因为它们是具有相同TableScan实例的MutableScan
  private static class TrivialRule extends AbstractUnifyRule {
    private static final TrivialRule INSTANCE = new TrivialRule(); // 单例实例

    private TrivialRule() {
      super(any(MutableRel.class), any(MutableRel.class), 0); // 任意类型，不需要槽
    }

    @Override public @Nullable UnifyResult apply(UnifyRuleCall call) {
      // 应用规则：如果查询等于目标，返回目标
      if (call.query.equals(call.target)) {
        return call.result(call.target);
      }
      return null; // 不匹配
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a
   * {@link MutableScan} to a {@link MutableCalc}
   * which has {@link MutableScan} as child.
   */
  // ScanToCalcUnifyRule（扫描到计算统一规则）：将MutableScan匹配到具有MutableScan子节点的MutableCalc的规则
  private static class ScanToCalcUnifyRule extends AbstractUnifyRule {

    public static final ScanToCalcUnifyRule INSTANCE = new ScanToCalcUnifyRule(); // 单例实例

    private ScanToCalcUnifyRule() {
      // 查询端：任意MutableScan
      // 目标端：MutableCalc，其子节点是任意MutableScan
      super(any(MutableScan.class),
          operand(MutableCalc.class, any(MutableScan.class)), 0);
    }

    @Override protected @Nullable UnifyResult apply(UnifyRuleCall call) {

      final MutableScan query = (MutableScan) call.query;

      final MutableCalc target = (MutableCalc) call.target;
      final MutableScan targetInput = (MutableScan) target.getInput();
      final Pair<RexNode, List<RexNode>> targetExplained = explainCalc(target);
      final RexNode targetCond = targetExplained.left;
      final List<RexNode> targetProjs = targetExplained.right;

      final RexBuilder rexBuilder = call.getCluster().getRexBuilder();

      if (!query.equals(targetInput) || !targetCond.isAlwaysTrue()) {
        return null;
      }
      final RexShuttle shuttle = getRexShuttle(targetProjs);
      final List<RexNode> compenProjs;
      try {
        compenProjs = shuttle.apply(rexBuilder.identityProjects(query.rowType));
      } catch (MatchFailed e) {
        return null;
      }
      if (RexUtil.isIdentity(compenProjs, target.rowType)) {
        return call.result(target);
      } else {
        RexProgram compenRexProgram =
            RexProgram.create(target.rowType, compenProjs, null, query.rowType,
                rexBuilder);
        MutableCalc compenCalc = MutableCalc.of(target, compenRexProgram);
        return tryMergeParentCalcAndGenResult(call, compenCalc);
      }
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a
   * {@link MutableCalc} to a {@link MutableCalc}.
   * The matching condition is as below:
   * 1. All columns of query can be expressed by target;
   * 2. The filtering condition of query must equals to or be weaker than target.
   */
  // CalcToCalcUnifyRule（计算到计算统一规则）：将MutableCalc匹配到MutableCalc的规则
  // 匹配条件：
  // 1. 查询的所有列都可以用目标表示
  // 2. 查询的过滤条件必须等于或弱于目标
  private static class CalcToCalcUnifyRule extends AbstractUnifyRule {

    public static final CalcToCalcUnifyRule INSTANCE =
        new CalcToCalcUnifyRule(); // 单例实例

    private CalcToCalcUnifyRule() {
      // 查询端：MutableCalc，其子节点使用槽0
      // 目标端：MutableCalc，其子节点使用槽0
      super(operand(MutableCalc.class, query(0)),
          operand(MutableCalc.class, target(0)), 1);
    }

    @Override public @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableCalc query = (MutableCalc) call.query;
      final Pair<RexNode, List<RexNode>> queryExplained = explainCalc(query);
      final RexNode queryCond = queryExplained.left;
      final List<RexNode> queryProjs = queryExplained.right;

      final MutableCalc target = (MutableCalc) call.target;
      final Pair<RexNode, List<RexNode>> targetExplained = explainCalc(target);
      final RexNode targetCond = targetExplained.left;
      final List<RexNode> targetProjs = targetExplained.right;

      final RexBuilder rexBuilder = call.getCluster().getRexBuilder();

      try {
        final RexShuttle shuttle = getRexShuttle(targetProjs);
        final RexNode splitted =
            splitFilter(call.getSimplify(), queryCond, targetCond);

        final RexNode compenCond;
        if (splitted != null) {
          if (splitted.isAlwaysTrue()) {
            compenCond = null;
          } else {
            // Compensate the residual filtering condition.
            compenCond = shuttle.apply(splitted);
          }
        } else if (implies(
            call.getCluster(), queryCond, targetCond, query.getInput().rowType)) {
          // Fail to split filtering condition, but implies that target contains
          // all lines of query, thus just set compensating filtering condition
          // as the filtering condition of query.
          compenCond = shuttle.apply(queryCond);
        } else {
          return null;
        }

        final List<RexNode> compenProjs = shuttle.apply(queryProjs);
        if (compenCond == null
            && RexUtil.isIdentity(compenProjs, target.rowType)) {
          return call.result(target);
        } else {
          final RexProgram compenRexProgram =
              RexProgram.create(target.rowType, compenProjs, compenCond,
                  query.rowType, rexBuilder);
          final MutableCalc compenCalc = MutableCalc.of(target, compenRexProgram);
          return tryMergeParentCalcAndGenResult(call, compenCalc);
        }
      } catch (MatchFailed e) {
        return null;
      }
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a {@link MutableJoin}
   * which has {@link MutableCalc} as left child to a {@link MutableJoin}.
   * We try to pull up the {@link MutableCalc} to top of {@link MutableJoin},
   * then match the {@link MutableJoin} in query to {@link MutableJoin} in target.
   */
  // JoinOnLeftCalcToJoinUnifyRule（左计算连接到连接统一规则）：将左侧有Calc的Join匹配到Join的规则
  // 尝试将MutableCalc提升到MutableJoin之上，然后匹配查询中的Join到目标中的Join
  private static class JoinOnLeftCalcToJoinUnifyRule extends AbstractUnifyRule {

    public static final JoinOnLeftCalcToJoinUnifyRule INSTANCE =
        new JoinOnLeftCalcToJoinUnifyRule(); // 单例实例

    private JoinOnLeftCalcToJoinUnifyRule() {
      // 查询端：MutableJoin，左子节点是MutableCalc（使用槽0），右子节点使用槽1
      // 目标端：MutableJoin，左子节点使用槽0，右子节点使用槽1
      super(
          operand(MutableJoin.class, operand(MutableCalc.class, query(0)), query(1)),
          operand(MutableJoin.class, target(0), target(1)), 2);
    }

    @Override protected @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableJoin query = (MutableJoin) call.query;
      final MutableCalc qInput0 = (MutableCalc) query.getLeft();
      final MutableRel qInput1 = query.getRight();
      MutableRel qInput0Input = qInput0.getInput();
      final Pair<RexNode, List<RexNode>> qInput0Explained = explainCalc(qInput0);
      final RexNode qInput0Cond = qInput0Explained.left;
      final List<RexNode> qInput0Projs = qInput0Explained.right;

      final MutableJoin target = (MutableJoin) call.target;

      final RexBuilder rexBuilder = call.getCluster().getRexBuilder();

      // Check whether is same join type.
      final JoinRelType joinRelType = sameJoinType(query.joinType, target.joinType);
      if (joinRelType == null) {
        return null;
      }
      // Check if calc under join can be pulled up.
      if (!canPullUpCalcUnderJoin(joinRelType, qInput0Explained, null)) {
        return null;
      }

      // Try pulling up MutableCalc only when Join condition references mapping.
      final List<RexNode> identityProjects =
          rexBuilder.identityProjects(qInput1.rowType);
      if (!referenceByMapping(query.condition, qInput0Projs, identityProjects)) {
        return null;
      }

      final RexNode newQueryJoinCond = new RexShuttle() {
        @Override public RexNode visitInputRef(RexInputRef inputRef) {
          final int idx = inputRef.getIndex();
          if (idx < fieldCnt(qInput0)) {
            final int newIdx = ((RexInputRef) qInput0Projs.get(idx)).getIndex();
            return new RexInputRef(newIdx, inputRef.getType());
          } else {
            int newIdx = idx - fieldCnt(qInput0) + fieldCnt(qInput0Input);
            return new RexInputRef(newIdx, inputRef.getType());
          }
        }
      }.apply(query.condition);

      final RexNode splitted =
          splitFilter(call.getSimplify(), newQueryJoinCond, target.condition);
      // MutableJoin matches only when the conditions are analyzed to be same.
      if (splitted != null && splitted.isAlwaysTrue()) {
        final RexNode compenCond = qInput0Cond;
        final List<RexNode> compenProjs =
            shiftAndAdjustProjectExpr(query, target, rexBuilder,
                qInput0Projs, qInput0Input.rowType.getFieldList(), null, null);

        final RexProgram compenRexProgram =
            RexProgram.create(target.rowType, compenProjs, compenCond,
                query.rowType, rexBuilder);
        final MutableCalc compenCalc = MutableCalc.of(target, compenRexProgram);
        return tryMergeParentCalcAndGenResult(call, compenCalc);
      }

      return null;
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a {@link MutableJoin}
   * which has {@link MutableCalc} as right child to a {@link MutableJoin}.
   * We try to pull up the {@link MutableCalc} to top of {@link MutableJoin},
   * then match the {@link MutableJoin} in query to {@link MutableJoin} in target.
   */
  // JoinOnRightCalcToJoinUnifyRule（右计算连接到连接统一规则）：将右侧有Calc的Join匹配到Join的规则
  // 尝试将MutableCalc提升到MutableJoin之上，然后匹配查询中的Join到目标中的Join
  private static class JoinOnRightCalcToJoinUnifyRule extends AbstractUnifyRule {

    public static final JoinOnRightCalcToJoinUnifyRule INSTANCE =
        new JoinOnRightCalcToJoinUnifyRule(); // 单例实例

    private JoinOnRightCalcToJoinUnifyRule() {
      // 查询端：MutableJoin，左子节点使用槽0，右子节点是MutableCalc（使用槽1）
      // 目标端：MutableJoin，左子节点使用槽0，右子节点使用槽1
      super(
          operand(MutableJoin.class, query(0), operand(MutableCalc.class, query(1))),
          operand(MutableJoin.class, target(0), target(1)), 2);
    }

    @Override protected @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableJoin query = (MutableJoin) call.query;
      final MutableRel qInput0 = query.getLeft();
      final MutableCalc qInput1 = (MutableCalc) query.getRight();
      final Pair<RexNode, List<RexNode>> qInput1Explained = explainCalc(qInput1);
      final RexNode qInput1Cond = qInput1Explained.left;
      final List<RexNode> qInput1Projs = qInput1Explained.right;

      final MutableJoin target = (MutableJoin) call.target;

      final RexBuilder rexBuilder = call.getCluster().getRexBuilder();

      // Check whether is same join type.
      final JoinRelType joinRelType = sameJoinType(query.joinType, target.joinType);
      if (joinRelType == null) {
        return null;
      }
      // Check if calc under join can be pulled up.
      if (!canPullUpCalcUnderJoin(joinRelType, null, qInput1Explained)) {
        return null;
      }

      // Try pulling up MutableCalc only when Join condition references mapping.
      final List<RexNode> identityProjects =
          rexBuilder.identityProjects(qInput0.rowType);
      if (!referenceByMapping(query.condition, identityProjects, qInput1Projs)) {
        return null;
      }

      final RexNode newQueryJoinCond = new RexShuttle() {
        @Override public RexNode visitInputRef(RexInputRef inputRef) {
          final int idx = inputRef.getIndex();
          if (idx < fieldCnt(qInput0)) {
            return inputRef;
          } else {
            final int newIdx = ((RexInputRef) qInput1Projs.get(idx - fieldCnt(qInput0)))
                .getIndex() + fieldCnt(qInput0);
            return new RexInputRef(newIdx, inputRef.getType());
          }
        }
      }.apply(query.condition);

      final RexNode splitted =
          splitFilter(call.getSimplify(), newQueryJoinCond, target.condition);
      // MutableJoin matches only when the conditions are analyzed to be same.
      if (splitted != null && splitted.isAlwaysTrue()) {
        final RexNode compenCond =
            RexUtil.shift(qInput1Cond, qInput0.rowType.getFieldCount());

        final List<RexNode> compenProjs =
            shiftAndAdjustProjectExpr(query, target, rexBuilder,
                null, null, qInput1Projs, qInput1.getInput().rowType.getFieldList());

        final RexProgram compensatingRexProgram =
            RexProgram.create(target.rowType, compenProjs, compenCond,
                query.rowType, rexBuilder);
        final MutableCalc compenCalc = MutableCalc.of(target, compensatingRexProgram);
        return tryMergeParentCalcAndGenResult(call, compenCalc);
      }
      return null;
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a {@link MutableJoin}
   * which has {@link MutableCalc} as children to a {@link MutableJoin}.
   * We try to pull up the {@link MutableCalc} to top of {@link MutableJoin},
   * then match the {@link MutableJoin} in query to {@link MutableJoin} in target.
   */
  // JoinOnCalcsToJoinUnifyRule（双计算连接到连接统一规则）：将两侧都有Calc的Join匹配到Join的规则
  // 尝试将MutableCalc提升到MutableJoin之上，然后匹配查询中的Join到目标中的Join
  private static class JoinOnCalcsToJoinUnifyRule extends AbstractUnifyRule {

    public static final JoinOnCalcsToJoinUnifyRule INSTANCE =
        new JoinOnCalcsToJoinUnifyRule(); // 单例实例

    private JoinOnCalcsToJoinUnifyRule() {
      // 查询端：MutableJoin，左子节点是MutableCalc（使用槽0），右子节点是MutableCalc（使用槽1）
      // 目标端：MutableJoin，左子节点使用槽0，右子节点使用槽1
      super(
          operand(MutableJoin.class,
              operand(MutableCalc.class, query(0)), operand(MutableCalc.class, query(1))),
          operand(MutableJoin.class, target(0), target(1)), 2);
    }

    @Override protected @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableJoin query = (MutableJoin) call.query;
      final MutableCalc qInput0 = (MutableCalc) query.getLeft();
      final MutableCalc qInput1 = (MutableCalc) query.getRight();
      final Pair<RexNode, List<RexNode>> qInput0Explained = explainCalc(qInput0);
      final RexNode qInput0Cond = qInput0Explained.left;
      final List<RexNode> qInput0Projs = qInput0Explained.right;
      final Pair<RexNode, List<RexNode>> qInput1Explained = explainCalc(qInput1);
      final RexNode qInput1Cond = qInput1Explained.left;
      final List<RexNode> qInput1Projs = qInput1Explained.right;

      final MutableJoin target = (MutableJoin) call.target;

      final RexBuilder rexBuilder = call.getCluster().getRexBuilder();

      // Check whether is same join type.
      final JoinRelType joinRelType = sameJoinType(query.joinType, target.joinType);
      if (joinRelType == null) {
        return null;
      }
      // Check if calc under join can be pulled up.
      if (!canPullUpCalcUnderJoin(joinRelType, qInput0Explained, qInput1Explained)) {
        return null;
      }

      if (!referenceByMapping(query.condition, qInput0Projs, qInput1Projs)) {
        return null;
      }

      RexNode newQueryJoinCond = new RexShuttle() {
        @Override public RexNode visitInputRef(RexInputRef inputRef) {
          final int idx = inputRef.getIndex();
          if (idx < fieldCnt(qInput0)) {
            final int newIdx = ((RexInputRef) qInput0Projs.get(idx)).getIndex();
            return new RexInputRef(newIdx, inputRef.getType());
          } else {
            final int newIdx = ((RexInputRef) qInput1Projs.get(idx - fieldCnt(qInput0)))
                .getIndex() + fieldCnt(qInput0.getInput());
            return new RexInputRef(newIdx, inputRef.getType());
          }
        }
      }.apply(query.condition);
      final RexNode splitted =
          splitFilter(call.getSimplify(), newQueryJoinCond, target.condition);
      // MutableJoin matches only when the conditions are analyzed to be same.
      if (splitted != null && splitted.isAlwaysTrue()) {
        final RexNode qInput1CondShifted =
            RexUtil.shift(qInput1Cond, fieldCnt(qInput0.getInput()));
        final RexNode compenCond =
            RexUtil.composeConjunction(rexBuilder,
                ImmutableList.of(qInput0Cond, qInput1CondShifted));

        final List<RexNode> compenProjs =
            shiftAndAdjustProjectExpr(query, target, rexBuilder,
                qInput0Projs, qInput0.getInput().rowType.getFieldList(),
                qInput1Projs, qInput1.getInput().rowType.getFieldList());

        final RexProgram compensatingRexProgram =
            RexProgram.create(target.rowType, compenProjs, compenCond,
                query.rowType, rexBuilder);
        final MutableCalc compensatingCalc =
            MutableCalc.of(target, compensatingRexProgram);
        return tryMergeParentCalcAndGenResult(call, compensatingCalc);
      }
      return null;
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a {@link MutableAggregate}
   * which has {@link MutableCalc} as child to a {@link MutableAggregate}.
   * We try to pull up the {@link MutableCalc} to top of {@link MutableAggregate},
   * then match the {@link MutableAggregate} in query to {@link MutableAggregate} in target.
   */
  // AggregateOnCalcToAggregateUnifyRule（计算上聚合到聚合统一规则）：将Calc上有Aggregate的匹配到Aggregate的规则
  // 尝试将MutableCalc提升到MutableAggregate之上，然后匹配查询中的Aggregate到目标中的Aggregate
  private static class AggregateOnCalcToAggregateUnifyRule extends AbstractUnifyRule {

    public static final AggregateOnCalcToAggregateUnifyRule INSTANCE =
        new AggregateOnCalcToAggregateUnifyRule(); // 单例实例

    private AggregateOnCalcToAggregateUnifyRule() {
      // 查询端：MutableAggregate，其子节点是MutableCalc（使用槽0）
      // 目标端：MutableAggregate，其子节点使用槽0
      super(operand(MutableAggregate.class, operand(MutableCalc.class, query(0))),
          operand(MutableAggregate.class, target(0)), 1);
    }

    @Override protected @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableAggregate query = (MutableAggregate) call.query;
      final MutableCalc qInput = (MutableCalc) query.getInput();
      final Pair<RexNode, List<RexNode>> qInputExplained = explainCalc(qInput);
      final RexNode qInputCond = qInputExplained.left;
      final List<RexNode> qInputProjs = qInputExplained.right;

      final MutableAggregate target = (MutableAggregate) call.target;

      final RexBuilder rexBuilder = call.getCluster().getRexBuilder();

      final Mappings.TargetMapping mapping =
          Project.getMapping(fieldCnt(qInput.getInput()), qInputProjs);
      if (mapping == null) {
        return null;
      }

      if (!qInputCond.isAlwaysTrue()) {
        try {
          // Fail the matching when filtering condition references
          // non-grouping columns in target.
          qInputCond.accept(new RexVisitorImpl<Void>(true) {
            @Override public Void visitInputRef(RexInputRef inputRef) {
              if (!target.groupSets.stream()
                  .allMatch(groupSet -> groupSet.get(inputRef.getIndex()))) {
                throw Util.FoundOne.NULL;
              }
              return super.visitInputRef(inputRef);
            }
          });
        } catch (Util.FoundOne one) {
          return null;
        }
      }

      final Mapping inverseMapping = mapping.inverse();
      final MutableAggregate aggregate2 =
          permute(query, qInput.getInput(), inverseMapping);

      final Mappings.TargetMapping mappingForQueryCond =
          Mappings.target(target.groupSet::indexOf,
              target.getInput().rowType.getFieldCount(),
              target.groupSet.cardinality());
      final RexNode targetCond = RexUtil.apply(mappingForQueryCond, qInputCond);

      final MutableRel unifiedAggregate =
          unifyAggregates(aggregate2, targetCond, target);
      if (unifiedAggregate == null) {
        return null;
      }
      // Add Project if the mapping breaks order of fields in GroupSet
      if (!Mappings.keepsOrdering(mapping)) {
        final int fieldCount = aggregate2.rowType.getFieldCount();
        final PairList<Integer, Integer> pairs = PairList.of();
        final List<Integer> groupings = aggregate2.groupSet.toList();
        for (int i = 0; i < groupings.size(); i++) {
          pairs.add(mapping.getTarget(groupings.get(i)), i);
        }
        pairs.sort(
            Comparator.<Map.Entry<Integer, Integer>>comparingInt(Map.Entry::getKey)
                .thenComparingInt(Map.Entry::getValue));
        final List<Integer> posList = new ArrayList<>(pairs.rightList());
        for (int i = posList.size(); i < fieldCount; i++) {
          posList.add(i);
        }
        final List<RexNode> compenProjs =
            MutableRels.createProjectExprs(unifiedAggregate, posList);
        final RexProgram compensatingRexProgram =
            RexProgram.create(unifiedAggregate.rowType, compenProjs, null,
                query.rowType, rexBuilder);
        final MutableCalc compenCalc =
            MutableCalc.of(unifiedAggregate, compensatingRexProgram);
        if (unifiedAggregate instanceof MutableCalc) {
          final MutableCalc newCompenCalc =
              mergeCalc(rexBuilder, compenCalc, (MutableCalc) unifiedAggregate);
          if (newCompenCalc == null) {
            return null;
          }
          return tryMergeParentCalcAndGenResult(call, newCompenCalc);
        } else {
          return tryMergeParentCalcAndGenResult(call, compenCalc);
        }
      } else {
        return tryMergeParentCalcAndGenResult(call, unifiedAggregate);
      }
    }
  }

  /** A {@link SubstitutionVisitor.UnifyRule} that matches a
   * {@link org.apache.calcite.rel.core.Aggregate} to a
   * {@link org.apache.calcite.rel.core.Aggregate}, provided
   * that they have the same child. */
  // AggregateToAggregateUnifyRule（聚合到聚合统一规则）：将Aggregate匹配到Aggregate的规则
  // 要求它们具有相同的子节点
  private static class AggregateToAggregateUnifyRule extends AbstractUnifyRule {
    public static final AggregateToAggregateUnifyRule INSTANCE =
        new AggregateToAggregateUnifyRule(); // 单例实例

    private AggregateToAggregateUnifyRule() {
      // 查询端：MutableAggregate，其子节点使用槽0
      // 目标端：MutableAggregate，其子节点使用槽0
      super(operand(MutableAggregate.class, query(0)),
          operand(MutableAggregate.class, target(0)), 1);
    }

    @Override public @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableAggregate query = (MutableAggregate) call.query;
      final MutableAggregate target = (MutableAggregate) call.target;
      assert query != target;
      // in.query can be rewritten in terms of in.target if its groupSet is
      // a subset, and its aggCalls are a superset. For example:
      //   query: SELECT x, COUNT(b) FROM t GROUP BY x
      //   target: SELECT x, y, SUM(a) AS s, COUNT(b) AS cb FROM t GROUP BY x, y
      // transforms to
      //   result: SELECT x, SUM(cb) FROM (target) GROUP BY x
      if (query.getInput() != target.getInput()) {
        return null;
      }
      if (!target.groupSet.contains(query.groupSet)) {
        return null;
      }
      final MutableRel result = unifyAggregates(query, null, target);
      if (result == null) {
        return null;
      }
      return tryMergeParentCalcAndGenResult(call, result);
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a
   * {@link MutableUnion} to a {@link MutableUnion} where the query and target
   * have the same inputs but might not have the same order.
   */
  // UnionToUnionUnifyRule（并集到并集统一规则）：将MutableUnion匹配到MutableUnion的规则
  // 要求查询和目标具有相同的输入，但顺序可能不同
  private static class UnionToUnionUnifyRule extends AbstractUnifyRule {
    public static final UnionToUnionUnifyRule INSTANCE = new UnionToUnionUnifyRule(); // 单例实例

    private UnionToUnionUnifyRule() {
      super(any(MutableUnion.class), any(MutableUnion.class), 0); // 任意类型，不需要槽
    }

    @Override public @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableUnion query = (MutableUnion) call.query;
      final MutableUnion target = (MutableUnion) call.target;
      final List<MutableRel> queryInputs = new ArrayList<>(query.getInputs());
      final List<MutableRel> targetInputs = new ArrayList<>(target.getInputs());
      if (query.isAll() == target.isAll()
          && sameRelCollectionNoOrderConsidered(queryInputs, targetInputs)) {
        return call.result(target);
      }
      return null;
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a {@link MutableUnion}
   * which has {@link MutableCalc} as child to a {@link MutableUnion}.
   * We try to pull up the {@link MutableCalc} to top of {@link MutableUnion},
   * then match the {@link MutableUnion} in query to {@link MutableUnion} in target.
   */
  // UnionOnCalcsToUnionUnifyRule（计算上并集到并集统一规则）：将Calc上有Union的匹配到Union的规则
  // 尝试将MutableCalc提升到MutableUnion之上，然后匹配查询中的Union到目标中的Union
  private static class UnionOnCalcsToUnionUnifyRule extends AbstractUnifyRule {
    public static final UnionOnCalcsToUnionUnifyRule INSTANCE =
        new UnionOnCalcsToUnionUnifyRule(); // 单例实例

    private UnionOnCalcsToUnionUnifyRule() {
      super(any(MutableUnion.class), any(MutableUnion.class), 0); // 任意类型，不需要槽
    }

    @Override public @Nullable UnifyResult apply(UnifyRuleCall call) {
      return setOpApply(call);
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a
   * {@link MutableIntersect} to a {@link MutableIntersect} where the query and target
   * have the same inputs but might not have the same order.
   */
  // IntersectToIntersectUnifyRule（交集到交集统一规则）：将MutableIntersect匹配到MutableIntersect的规则
  // 要求查询和目标具有相同的输入，但顺序可能不同
  private static class IntersectToIntersectUnifyRule extends AbstractUnifyRule {
    public static final IntersectToIntersectUnifyRule INSTANCE =
        new IntersectToIntersectUnifyRule(); // 单例实例

    private IntersectToIntersectUnifyRule() {
      super(any(MutableIntersect.class), any(MutableIntersect.class), 0); // 任意类型，不需要槽
    }

    @Override public @Nullable UnifyResult apply(UnifyRuleCall call) {
      final MutableIntersect query = (MutableIntersect) call.query;
      final MutableIntersect target = (MutableIntersect) call.target;
      final List<MutableRel> queryInputs = new ArrayList<>(query.getInputs());
      final List<MutableRel> targetInputs = new ArrayList<>(target.getInputs());
      if (query.isAll() == target.isAll()
          && sameRelCollectionNoOrderConsidered(queryInputs, targetInputs)) {
        return call.result(target);
      }
      return null;
    }
  }

  /**
   * A {@link SubstitutionVisitor.UnifyRule} that matches a {@link MutableIntersect}
   * which has {@link MutableCalc} as child to a {@link MutableIntersect}.
   * We try to pull up the {@link MutableCalc} to top of {@link MutableIntersect},
   * then match the {@link MutableIntersect} in query to {@link MutableIntersect} in target.
   */
  // IntersectOnCalcsToIntersectUnifyRule（计算上交集到交集统一规则）：将Calc上有Intersect的匹配到Intersect的规则
  // 尝试将MutableCalc提升到MutableIntersect之上，然后匹配查询中的Intersect到目标中的Intersect
  private static class IntersectOnCalcsToIntersectUnifyRule extends AbstractUnifyRule {
    public static final IntersectOnCalcsToIntersectUnifyRule INSTANCE =
        new IntersectOnCalcsToIntersectUnifyRule(); // 单例实例

    private IntersectOnCalcsToIntersectUnifyRule() {
      super(any(MutableIntersect.class), any(MutableIntersect.class), 0); // 任意类型，不需要槽
    }

    @Override public @Nullable UnifyResult apply(UnifyRuleCall call) {
      return setOpApply(call);
    }
  }

  /**
   * Applies a AbstractUnifyRule to a particular node in a query. We try to pull up the
   * {@link MutableCalc} to top of {@link MutableUnion} or {@link MutableIntersect}, this
   * method not suit for {@link MutableMinus}.
   *
   * @param call Input parameters
   */
  // setOpApply（集合操作应用）：将AbstractUnifyRule应用到查询中的特定节点
  // 尝试将MutableCalc提升到MutableUnion或MutableIntersect之上
  // 此方法不适用于MutableMinus
  private static @Nullable UnifyResult setOpApply(UnifyRuleCall call) {
    if (call.query instanceof MutableMinus && call.target
        instanceof MutableMinus) {
      return null;
    }
    final MutableSetOp query = (MutableSetOp) call.query;
    final MutableSetOp target = (MutableSetOp) call.target;
    final List<MutableCalc> queryInputs = new ArrayList<>();
    final List<MutableRel> queryGrandInputs = new ArrayList<>();
    final List<MutableRel> targetInputs = new ArrayList<>(target.getInputs());

    final RexBuilder rexBuilder = call.getCluster().getRexBuilder();

    for (MutableRel rel : query.getInputs()) {
      if (rel instanceof MutableCalc) {
        queryInputs.add((MutableCalc) rel);
        queryGrandInputs.add(((MutableCalc) rel).getInput());
      } else {
        return null;
      }
    }

    if (query.isAll() && target.isAll()
        && sameRelCollectionNoOrderConsidered(queryGrandInputs, targetInputs)) {
      final Pair<RexNode, List<RexNode>> queryInputExplained0 =
          explainCalc(queryInputs.get(0));
      for (int i = 1; i < queryGrandInputs.size(); i++) {
        final Pair<RexNode, List<RexNode>> queryInputExplained =
            explainCalc(queryInputs.get(i));
        // Matching fails when filtering conditions are not equal or projects are not equal.
        RexNode residue =
            splitFilter(call.getSimplify(), queryInputExplained0.left,
                queryInputExplained.left);
        if (residue == null || !residue.isAlwaysTrue()) {
          return null;
        }
        for (Pair<RexNode, RexNode> pair : Pair.zip(
            queryInputExplained0.right, queryInputExplained.right)) {
          if (!pair.left.equals(pair.right)) {
            return null;
          }
        }
      }

      List<RexNode> projectExprs =
          MutableRels.createProjects(target, queryInputExplained0.right);
      final RexProgram compenRexProgram =
          RexProgram.create(target.rowType, projectExprs,
              queryInputExplained0.left, query.rowType, rexBuilder);
      final MutableCalc compenCalc = MutableCalc.of(target, compenRexProgram);
      return tryMergeParentCalcAndGenResult(call, compenCalc);
    }

    return null;
  }

  /** Check if list0 and list1 contains the same nodes -- order is not considered. */
  // sameRelCollectionNoOrderConsidered（相同关系集合不考虑顺序）：检查list0和list1是否包含相同的节点，不考虑顺序
  private static boolean sameRelCollectionNoOrderConsidered(
      List<MutableRel> list0, List<MutableRel> list1) {
    if (list0.size() != list1.size()) {
      return false;
    }
    for (MutableRel rel : list0) {
      int index = list1.indexOf(rel);
      if (index == -1) {
        return false;
      } else {
        list1.remove(index);
      }
    }
    return true;
  }

  // fieldCnt（字段计数）：获取关系表达式的字段数量
  private static int fieldCnt(MutableRel rel) {
    return rel.rowType.getFieldCount();
  }

  /** Explain filtering condition and projections from MutableCalc. */
  // explainCalc（解释计算）：从MutableCalc中提取过滤条件和投影表达式
  public static Pair<RexNode, List<RexNode>> explainCalc(MutableCalc calc) {
    final RexShuttle shuttle = getExpandShuttle(calc.program);
    final RexNode condition;
    if (calc.program.getCondition() == null) {
      condition = calc.cluster.getRexBuilder().makeLiteral(true);
    } else {
      condition = calc.program.getCondition().accept(shuttle);
    }
    final List<RexNode> projects =
        ImmutableList.copyOf(shuttle.apply(calc.program.getProjectList()));
    return Pair.of(condition, projects);
  }

  /**
   * Generate result by merging parent and child if they are both MutableCalc.
   * Otherwise result is the child itself.
   */
  // tryMergeParentCalcAndGenResult（尝试合并父Calc并生成结果）：如果父节点和子节点都是MutableCalc，则合并它们生成结果
  // 否则结果就是子节点本身
  private static UnifyResult tryMergeParentCalcAndGenResult(
      UnifyRuleCall call, MutableRel child) {
    final MutableRel parent = call.query.getParent();
    if (child instanceof MutableCalc && parent instanceof MutableCalc) {
      final MutableCalc mergedCalc =
          mergeCalc(call.getCluster().getRexBuilder(), (MutableCalc) parent,
              (MutableCalc) child);
      if (mergedCalc != null) {
        // Note that property of stopTrying in the result is false
        // and this query node deserves further matching iterations.
        return call.create(parent).result(mergedCalc, false);
      }
    }
    return call.result(child);
  }

  /** Merge two MutableCalc together. */
  // mergeCalc（合并计算）：合并两个MutableCalc
  private static @Nullable MutableCalc mergeCalc(
      RexBuilder rexBuilder, MutableCalc topCalc, MutableCalc bottomCalc) {
    RexProgram topProgram = topCalc.program;
    if (RexOver.containsOver(topProgram)) {
      return null;
    }

    RexProgram mergedProgram =
        RexProgramBuilder.mergePrograms(
            topCalc.program,
            bottomCalc.program,
            rexBuilder);
    assert mergedProgram.getOutputRowType()
        == topProgram.getOutputRowType();
    return MutableCalc.of(bottomCalc.getInput(), mergedProgram);
  }

  // getExpandShuttle（获取展开Shuttle）：构建一个展开RexLocalRef的shuttle
  // 用于将RexProgram中的局部引用展开为实际表达式
  private static RexShuttle getExpandShuttle(RexProgram rexProgram) {
    return new RexShuttle() {
      @Override public RexNode visitLocalRef(RexLocalRef localRef) {
        return rexProgram.expandLocalRef(localRef);
      }
    };
  }

  /** Check if condition cond0 implies cond1. */
  // implies（蕴含）：检查条件cond0是否蕴含cond1
  // 即cond0为true时，cond1是否也必须为true
  private static boolean implies(
      RelOptCluster cluster, RexNode cond0, RexNode cond1, RelDataType rowType) {
    RexExecutor rexImpl =
        Util.first(cluster.getPlanner().getExecutor(), RexUtil.EXECUTOR);
    RexImplicationChecker rexImplicationChecker =
        new RexImplicationChecker(cluster.getRexBuilder(), rexImpl, rowType);
    return rexImplicationChecker.implies(cond0, cond1);
  }

  /** Check if join condition only references RexInputRef. */
  // referenceByMapping（通过映射引用）：检查连接条件是否只引用RexInputRef
  // 用于确定连接条件是否可以通过投影映射来重写
  private static boolean referenceByMapping(
      RexNode joinCondition, List<RexNode>... projectsOfInputs) {
    List<RexNode> projects = new ArrayList<>();
    for (List<RexNode> projectsOfInput : projectsOfInputs) {
      projects.addAll(projectsOfInput);
    }

    try {
      RexVisitor rexVisitor = new RexVisitorImpl<Void>(true) {
        @Override public Void visitInputRef(RexInputRef inputRef) {
          if (!(projects.get(inputRef.getIndex()) instanceof RexInputRef)) {
            throw Util.FoundOne.NULL;
          }
          return super.visitInputRef(inputRef);
        }
      };
      joinCondition.accept(rexVisitor);
    } catch (Util.FoundOne e) {
      return false;
    }
    return true;
  }

  // sameJoinType（相同连接类型）：检查两个连接类型是否相同
  private static @Nullable JoinRelType sameJoinType(JoinRelType type0, JoinRelType type1) {
    if (type0 == type1) {
      return type0;
    } else {
      return null;
    }
  }

  // permute（置换）：根据映射关系置换聚合操作的分组和聚合函数
  // 用于在聚合操作上应用字段映射
  public static MutableAggregate permute(MutableAggregate aggregate,
      MutableRel input, Mapping mapping) {
    ImmutableBitSet groupSet = Mappings.apply(mapping, aggregate.groupSet);
    ImmutableList<ImmutableBitSet> groupSets =
        Mappings.apply2(mapping, aggregate.groupSets);
    List<AggregateCall> aggregateCalls =
        Util.transform(aggregate.aggCalls, call -> call.transform(mapping));
    return MutableAggregate.of(input, groupSet, groupSets, aggregateCalls);
  }

  // unifyAggregates（统一聚合）：统一两个聚合操作，生成等价的结果
  // 处理两种情况：
  // 1. 查询和目标具有相同的分组级别，生成投影
  // 2. 查询是更粗粒度的聚合级别，生成聚合
  public static @Nullable MutableRel unifyAggregates(MutableAggregate query,
      @Nullable RexNode targetCond, MutableAggregate target) {
    MutableRel result;
    RexBuilder rexBuilder = query.cluster.getRexBuilder();
    Map<RexNode, RexNode> targetCondConstantMap =
        RexUtil.predicateConstants(RexNode.class, rexBuilder, RelOptUtil.conjunctions(targetCond));
    // Collect rexInputRef in constant filter condition.
    Set<Integer> constantCondInputRefs = new HashSet<>();
    List<Integer> targetGroupByIndexList = target.groupSet.asList();
    RexShuttle rexShuttle = new RexShuttle() {
      @Override public RexNode visitInputRef(RexInputRef inputRef) {
        constantCondInputRefs.add(targetGroupByIndexList.get(inputRef.getIndex()));
        return super.visitInputRef(inputRef);
      }
    };
    for (RexNode rexNode : targetCondConstantMap.keySet()) {
      rexNode.accept(rexShuttle);
    }
    Set<Integer> compenGroupSet = null;
    // Calc the missing group list of query, do not cover grouping sets cases.
    if (query.groupSets.size() == 1 && target.groupSets.size() == 1) {
      if (target.groupSet.contains(query.groupSet)) {
        compenGroupSet = target.groupSets.get(0).except(query.groupSets.get(0)).asSet();
      }
    }
    // If query and target have the same group list,
    // or query has constant filter for missing columns in group by list.
    if (query.groupSets.equals(target.groupSets)
        || (compenGroupSet != null && constantCondInputRefs.containsAll(compenGroupSet))) {
      int projOffset = 0;
      if (!query.groupSets.equals(target.groupSets)) {
        projOffset = requireNonNull(compenGroupSet, "compenGroupSet").size();
      }
      // Same level of aggregation. Generate a project.
      final List<Integer> projects = new ArrayList<>();
      final int groupCount = query.groupSet.cardinality();
      for (Integer inputIndex : query.groupSet.asList()) {
        // Use the index in target group by.
        int i = targetGroupByIndexList.indexOf(inputIndex);
        projects.add(i);
      }
      final List<AggregateCall> targetGroupGenAggCalls = new ArrayList<>();
      for (AggregateCall aggregateCall : query.aggCalls) {
        int i = target.aggCalls.indexOf(aggregateCall);
        if (i < 0) {
          final AggregateCall newAggCall =
              genAggCallWithTargetGrouping(aggregateCall, targetGroupByIndexList);
          if (newAggCall == null) {
            return null;
          } else {
            // Here, we create a new `MutableAggregate` to return.
            // So, we record this new agg-call.
            targetGroupGenAggCalls.add(newAggCall);
          }
        } else {
          if (!targetGroupGenAggCalls.isEmpty()) {
            // Here, we didn't build target's agg-call by ref of mv's agg-call,
            // if some agg-call is generated by target's grouping.
            // So, we return null to stop it.
            return null;
          }
          projects.add(groupCount + i + projOffset);
        }
      }

      if (targetGroupGenAggCalls.isEmpty()) {
        List<RexNode> compenProjs = MutableRels.createProjectExprs(target, projects);
        RexProgram compenRexProgram =
            RexProgram.create(target.rowType, compenProjs, targetCond,
                query.rowType, rexBuilder);
        result = MutableCalc.of(target, compenRexProgram);
      } else {
        result =
            MutableAggregate.of(target, target.groupSet, target.groupSets,
                targetGroupGenAggCalls);
      }
    } else if (target.getGroupType() == Aggregate.Group.SIMPLE) {
      // Query is coarser level of aggregation. Generate an aggregate.
      final Map<Integer, Integer> map = new HashMap<>();
      target.groupSet.forEachInt(k -> map.put(k, map.size()));
      for (int c : query.groupSet) {
        if (!map.containsKey(c)) {
          return null;
        }
      }
      final ImmutableBitSet groupSet = query.groupSet.permute(map);
      ImmutableList<ImmutableBitSet> groupSets = null;
      if (query.getGroupType() != Aggregate.Group.SIMPLE) {
        groupSets =
            ImmutableBitSet.ORDERING.immutableSortedCopy(
                ImmutableBitSet.permute(query.groupSets, map));
      }
      final List<AggregateCall> aggregateCalls = new ArrayList<>();
      for (AggregateCall aggregateCall : query.aggCalls) {
        AggregateCall newAggCall = null;
        // 1. try to find rollup agg-call.
        if (!aggregateCall.isDistinct()) {
          int i = target.aggCalls.indexOf(aggregateCall);
          if (i >= 0) {
            // When an SqlAggFunction does not support roll up, it will return null,
            // which means that it cannot do secondary aggregation
            // and the materialization recognition will fail.
            final SqlAggFunction aggFunction = aggregateCall.getAggregation().getRollup();
            if (aggFunction != null) {
              newAggCall =
                  AggregateCall.create(aggregateCall.getParserPosition(),
                      aggFunction, aggregateCall.isDistinct(),
                      aggregateCall.isApproximate(), aggregateCall.ignoreNulls(),
                      aggregateCall.rexList,
                      ImmutableList.of(target.groupSet.cardinality() + i), -1,
                      aggregateCall.distinctKeys, aggregateCall.collation,
                      aggregateCall.type, aggregateCall.name);
            }
          }
        }
        // 2. try to build a new agg-cal by target's grouping.
        if (newAggCall == null) {
          newAggCall = genAggCallWithTargetGrouping(aggregateCall, targetGroupByIndexList);
        }
        if (newAggCall == null) {
          // gen agg call fail.
          return null;
        }
        aggregateCalls.add(newAggCall);
      }
      if (targetCond != null && !targetCond.isAlwaysTrue()) {
        RexProgram compenRexProgram =
            RexProgram.create(target.rowType,
                rexBuilder.identityProjects(target.rowType),
                targetCond, target.rowType, rexBuilder);

        result =
            MutableAggregate.of(MutableCalc.of(target, compenRexProgram),
                groupSet, groupSets, aggregateCalls);
      } else {
        result =
            MutableAggregate.of(target, groupSet, groupSets, aggregateCalls);
      }
    } else {
      return null;
    }
    return result;
  }

  /**
   * Generate agg call by mv's grouping.
   */
  // genAggCallWithTargetGrouping（根据目标分组生成聚合调用）：根据物化视图的分组生成聚合调用
  // 用于在聚合重写时生成新的聚合函数调用
  private static @Nullable AggregateCall genAggCallWithTargetGrouping(AggregateCall queryAggCall,
      List<Integer> targetGroupByIndexes) {
    final SqlAggFunction aggregation = queryAggCall.getAggregation();
    final List<Integer> argList = queryAggCall.getArgList();
    final List<Integer> newArgList = new ArrayList<>();
    for (Integer arg : argList) {
      final int newArgIndex = targetGroupByIndexes.indexOf(arg);
      if (newArgIndex < 0) {
        return null;
      }
      newArgList.add(newArgIndex);
    }
    final boolean isAllowBuild;
    if (newArgList.isEmpty()) {
      // Size of agg-call's args is empty, we stop to build a new agg-call,
      // eg: count(1) or count(*).
      isAllowBuild = false;
    } else if (queryAggCall.isDistinct()) {
      // Args of agg-call is distinct, we can build a new agg-call.
      isAllowBuild = true;
    } else if (aggregation.getDistinctOptionality() == Optionality.IGNORED) {
      // If attribute of agg-call's distinct could be ignored,
      // we can build a new agg-call.
      isAllowBuild = true;
    } else {
      isAllowBuild = false;
    }
    if (!isAllowBuild) {
      return null;
    }
    return AggregateCall.create(queryAggCall.getParserPosition(), aggregation,
        queryAggCall.isDistinct(), queryAggCall.isApproximate(),
        queryAggCall.ignoreNulls(), queryAggCall.rexList,
        newArgList, -1, queryAggCall.distinctKeys,
        queryAggCall.collation, queryAggCall.type,
        queryAggCall.name);
  }

  @Deprecated // to be removed before 2.0
  // getRollup（获取上卷函数）：获取聚合函数的上卷函数
  // 用于在粗粒度聚合重写时确定如何从细粒度聚合结果计算
  public static @Nullable SqlAggFunction getRollup(SqlAggFunction aggregation) {
    if (aggregation == SqlStdOperatorTable.SUM
        || aggregation == SqlStdOperatorTable.MIN
        || aggregation == SqlStdOperatorTable.MAX
        || aggregation == SqlStdOperatorTable.SOME
        || aggregation == SqlStdOperatorTable.EVERY
        || aggregation == SqlLibraryOperators.BOOL_AND
        || aggregation == SqlLibraryOperators.BOOL_OR
        || aggregation == SqlLibraryOperators.LOGICAL_AND
        || aggregation == SqlLibraryOperators.LOGICAL_OR
        || aggregation == SqlStdOperatorTable.SUM0
        || aggregation == SqlStdOperatorTable.ANY_VALUE) {
      return aggregation;
    } else if (aggregation == SqlStdOperatorTable.COUNT) {
      return SqlStdOperatorTable.SUM0;
    } else {
      return null;
    }
  }

  /** Builds a shuttle that stores a list of expressions, and can map incoming
   * expressions to references to them. */
  // getRexShuttle（获取RexShuttle）：构建一个存储表达式列表的shuttle，可以将传入的表达式映射到对它们的引用
  // 用于在表达式重写时进行变量替换
  private static RexShuttle getRexShuttle(List<RexNode> rexNodes) {
    final Map<RexNode, Integer> map = new HashMap<>();
    for (int i = 0; i < rexNodes.size(); i++) {
      final RexNode rexNode = rexNodes.get(i);
      if (map.containsKey(rexNode)) {
        continue;
      }
      map.put(rexNode, i);
    }
    return new RexShuttle() {
      @Override public RexNode visitInputRef(RexInputRef ref) {
        final Integer integer = map.get(ref);
        if (integer != null) {
          return new RexInputRef(integer, ref.getType());
        }
        throw MatchFailed.INSTANCE;
      }

      @Override public RexNode visitCall(RexCall call) {
        final Integer integer = map.get(call);
        if (integer != null) {
          return new RexInputRef(integer, call.getType());
        }
        return super.visitCall(call);
      }

      @Override public RexNode visitLiteral(RexLiteral literal) {
        final Integer integer = map.get(literal);
        if (integer != null) {
          return new RexInputRef(integer, literal.getType());
        }
        return super.visitLiteral(literal);
      }
    };
  }

  /** Returns if one rel is weaker than another. */
  // isWeaker（是否更弱）：判断一个关系表达式是否比另一个更弱
  // 更弱意味着包含更多的行（条件更宽松）
  protected boolean isWeaker(MutableRel rel0, MutableRel rel) {
    if (rel0 == rel || equivalents.get(rel0).contains(rel)) {
      return false;
    }

    if (!(rel0 instanceof MutableFilter)
        || !(rel instanceof MutableFilter)) {
      return false;
    }

    if (!rel.rowType.equals(rel0.rowType)) {
      return false;
    }

    final MutableRel rel0input = ((MutableFilter) rel0).getInput();
    final MutableRel relinput = ((MutableFilter) rel).getInput();
    if (rel0input != relinput
        && !equivalents.get(rel0input).contains(relinput)) {
      return false;
    }

    return implies(rel0.cluster, ((MutableFilter) rel0).condition,
        ((MutableFilter) rel).condition, rel.rowType);
  }

  /** Returns whether two relational expressions have the same row-type. */
  // equalType（类型相等）：判断两个关系表达式是否具有相同的行类型
  public static boolean equalType(String desc0, MutableRel rel0, String desc1,
      MutableRel rel1, Litmus litmus) {
    return RelOptUtil.equal(desc0, rel0.rowType, desc1, rel1.rowType, litmus);
  }

  /**
   * Check if calc under join can be pulled up,
   * when meeting JoinOnCalc of query unify to Join of target.
   * Working in rules: {@link JoinOnLeftCalcToJoinUnifyRule} <br/>
   * {@link JoinOnRightCalcToJoinUnifyRule} <br/>
   * {@link JoinOnCalcsToJoinUnifyRule} <br/>
   */
  // canPullUpCalcUnderJoin（检查是否可以提升计算）：检查Join下的Calc是否可以被提升
  // 用于查询的JoinOnCalc统一到目标的Join时
  // 工作规则：JoinOnLeftCalcToJoinUnifyRule、JoinOnRightCalcToJoinUnifyRule、JoinOnCalcsToJoinUnifyRule
  private static boolean canPullUpCalcUnderJoin(JoinRelType joinType,
      @Nullable Pair<RexNode, List<RexNode>> qInput0Explained,
      @Nullable Pair<RexNode, List<RexNode>> qInput1Explained) {
    if (qInput0Explained != null
        && joinType.generatesNullsOn(0)
        && !isCalcStrong(qInput0Explained)) {
      return false;
    }
    return qInput1Explained == null
        || !joinType.generatesNullsOn(1)
        || isCalcStrong(qInput1Explained);
  }

  /** Determines if all projects are strong and the condition is always true. */
  // isCalcStrong（Calc是否强）：确定所有投影都是强的，并且条件总是true
  // 强意味着在null-if-null语义下是安全的
  private static boolean isCalcStrong(Pair<RexNode, List<RexNode>> inputExplained) {
    final RexNode cond = inputExplained.left;
    final List<RexNode> projs = inputExplained.right;
    return cond.isAlwaysTrue() && projs.stream().allMatch(STRONG::isNull);
  }

  /**
   * Generates project expressions by shifting and adjusting the nullability of expressions
   * based on the provided join targets and inputs.
   *
   * <p>Used in the Join rewrite to pull up the calc in query
   * to the join in mv to ensure operator equivalence.
   * (Already make sure that pull up is valid).
   *
   * <p>Working in rules: {@link JoinOnLeftCalcToJoinUnifyRule},
   * {@link JoinOnRightCalcToJoinUnifyRule},
   * {@link JoinOnCalcsToJoinUnifyRule}.
   *
   * @param query MutableRel of query
   * @param target MutableRel of target
   * @param rexBuilder Rex builder
   * @param qInput0Projs Project expressions from the left calc of the query join if exist
   * @param qInput0InputFields Input fields from the left input of the query join if exist
   * @param qInput1Projs Project expressions from the right calc of the query join if exist
   * @param qInput1InputFields Input fields from the right input of the query join if exist
   * @return The Project expression that makes target equivalent to query
   */
  // shiftAndAdjustProjectExpr（平移和调整投影表达式）：通过平移和调整表达式的可空性来生成投影表达式
  // 用于Join重写，将查询中的Calc提升到物化视图的Join之上，以确保操作符等价性
  // （已经确保提升是有效的）
  // 工作规则：JoinOnLeftCalcToJoinUnifyRule、JoinOnRightCalcToJoinUnifyRule、JoinOnCalcsToJoinUnifyRule
  private static List<RexNode> shiftAndAdjustProjectExpr(MutableJoin query, MutableJoin target,
      RexBuilder rexBuilder,
      @Nullable List<RexNode> qInput0Projs, @Nullable List<RelDataTypeField> qInput0InputFields,
      @Nullable List<RexNode> qInput1Projs, @Nullable List<RelDataTypeField> qInput1InputFields) {
    int queryLeftCount = fieldCnt(query.getLeft());
    int targetLeftCount = fieldCnt(target.getLeft());
    int[] adjustments0 = new int[target.rowType.getFieldCount()];
    int[] adjustments1 = new int[target.rowType.getFieldCount()];

    if (qInput1Projs != null) {
      Arrays.fill(adjustments1, targetLeftCount);
    }

    // In cases such as JoinOnLeftCalcToJoinUnifyRule and JoinOnCalcsToJoinUnifyRule rules,
    // initialize the converter where the left calc needs to be pulled up.
    RelOptUtil.RexInputConverter converter0 =
        new RelOptUtil.RexInputConverter(rexBuilder, qInput0InputFields,
            target.rowType.getFieldList(), adjustments0);

    // In cases such as JoinOnRightCalcToJoinUnifyRule and JoinOnCalcsToJoinUnifyRule rules,
    // initialize the converter where the left calc needs to be pulled up.
    RelOptUtil.RexInputConverter converter1 =
        new RelOptUtil.RexInputConverter(rexBuilder, qInput1InputFields,
            target.rowType.getFieldList(), adjustments1);

    final List<RexNode> compenProjs = new ArrayList<>();
    for (int i = 0; i < fieldCnt(query); i++) {
      RelDataType type = query.rowType.getFieldList().get(i).getType();
      if (i < queryLeftCount) {
        if (qInput0Projs == null) {
          compenProjs.add(new RexInputRef(i, type));
        } else {
          // Before:
          //        QueryJoin      TargetJoin
          //        /     \         /     \
          //      Calc   Right    Left   Right
          //        |
          //      Left
          //
          // After:
          //        QueryJoin         Calc
          //        /     \            |
          //      Calc   Right     TargetJoin
          //        |               /     \
          //      Left            Left   Right
          //
          // Since Calc is on the left side of the Join, no shift is required.
          // However, due to nullability caused by the Join,
          // we must adjust the type of RexInputRef.
          RexNode apply = converter0.apply(qInput0Projs.get(i));
          compenProjs.add(adjustNullability(apply, type, rexBuilder));
        }
      } else {
        if (qInput1Projs == null) {
          // This is equivalent to a shift, but without a calc on the right side to pull up,
          // we know it's a RexInputRef, so converter isn't needed.
          compenProjs.add(
              new RexInputRef(i - queryLeftCount + targetLeftCount, type));
        } else {
          // Before:
          //        QueryJoin      TargetJoin
          //        /     \         /     \
          //      Left   Calc    Left   Right
          //               |
          //             Right
          //
          // After, We pull up the Calc of Query to target to make it equivalent to Query:
          //        QueryJoin          Calc
          //        /     \             |
          //      Left   Calc       TargetJoin
          //               |         /     \
          //             Right     Left   Right
          //
          // With regard to type adjustments, as above.
          // And since Query's Right is equivalent to Target's Right and now calc
          // will be pulled up above the join, we need to shift join's leftCount,
          // which is what we did in initializing converter1.
          RexNode apply = converter1.apply(qInput1Projs.get(i - queryLeftCount));
          compenProjs.add(adjustNullability(apply, type, rexBuilder));
        }
      }
    }
    return compenProjs;
  }

  /** Cast RexNode to the given type if only nullability differs, otherwise throw. */
  // adjustNullability（调整可空性）：如果只有可空性不同，则将RexNode转换为给定类型，否则抛出异常
  private static RexNode adjustNullability(RexNode rexNode,
      RelDataType type, RexBuilder rexBuilder) {
    if (rexNode.getType().equals(type)) {
      return rexNode;
    }
    final RelDataType adjustedType =
        rexBuilder.getTypeFactory().createTypeWithNullability(rexNode.getType(), type.isNullable());
    if (type.equals(adjustedType)) {
      return rexBuilder.makeCast(adjustedType, rexNode);
    }
    throw new AssertionError("Adjust nullability failed:" + rexNode.getType() + " " + type);
  }

  /** Operand to a {@link UnifyRule}. */
  // Operand（操作数）：UnifyRule的操作数抽象类，定义匹配模式
  public abstract static class Operand {
    protected final Class<? extends MutableRel> clazz; // 操作数匹配的关系表达式类型

    protected Operand(Class<? extends MutableRel> clazz) {
      this.clazz = clazz; // 初始化类型
    }

    public abstract boolean matches(SubstitutionVisitor visitor, MutableRel rel);
    // matches方法：判断关系表达式是否匹配此操作数

    public boolean isWeaker(SubstitutionVisitor visitor, MutableRel rel) {
      // isWeaker方法：判断关系表达式是否弱于此操作数
      return false; // 默认实现返回false
    }
  }

  /** Operand to a {@link UnifyRule} that matches a relational expression of a
   * given type. It has zero or more child operands. */
  // InternalOperand（内部操作数）：匹配给定类型的关系表达式的操作数，可以有零个或多个子操作数
  private static class InternalOperand extends Operand {
      private final List<Operand> inputs; // 子操作数列表
  
      InternalOperand(Class<? extends MutableRel> clazz, List<Operand> inputs) {
        super(clazz); // 初始化类型
        this.inputs = inputs; // 初始化子操作数
      }
  
      @Override public boolean matches(SubstitutionVisitor visitor, MutableRel rel) {
        // matches方法：检查关系表达式是否匹配此操作数
        return clazz.isInstance(rel) // 检查类型
            && allMatch(visitor, inputs, rel.getInputs()); // 检查所有子操作数
      }
  
  
      @Override public boolean isWeaker(SubstitutionVisitor visitor, MutableRel rel) {
        // isWeaker方法：检查关系表达式是否弱于此操作数
        return clazz.isInstance(rel) // 检查类型
            && allWeaker(visitor, inputs, rel.getInputs()); // 检查所有子操作数
      }
      private static boolean allMatch(SubstitutionVisitor visitor,
          List<Operand> operands, List<MutableRel> rels) {
        // allMatch方法：检查所有操作数是否匹配
        if (operands.size() != rels.size()) {
          return false; // 数量不匹配
        }
        for (Pair<Operand, MutableRel> pair : Pair.zip(operands, rels)) {
          if (!pair.left.matches(visitor, pair.right)) {
            return false; // 操作数不匹配
          }
        }
        return true;
      }
  
      private static boolean allWeaker(
          SubstitutionVisitor visitor,
          List<Operand> operands, List<MutableRel> rels) {
        // allWeaker方法：检查所有操作数是否更弱
        if (operands.size() != rels.size()) {
          return false; // 数量不匹配
        }
        for (Pair<Operand, MutableRel> pair : Pair.zip(operands, rels)) {
          if (!pair.left.isWeaker(visitor, pair.right)) {
            return false; // 操作数不更弱
          }
        }
        return true;
      }
    }

  /** Operand to a {@link UnifyRule} that matches a relational expression of a
   * given type. */
  // AnyOperand（任意操作数）：匹配给定类型的关系表达式的操作数，不检查子节点
  private static class AnyOperand extends Operand {
    AnyOperand(Class<? extends MutableRel> clazz) {
      super(clazz); // 初始化类型
    }

    @Override public boolean matches(SubstitutionVisitor visitor, MutableRel rel) {
      return clazz.isInstance(rel); // 检查关系表达式是否是指定类型的实例
    }
  }

  /** Operand that assigns a particular relational expression to a variable.
   *
   * <p>It is applied to a descendant of the query, writes the operand into the
   * slots array, and always matches.
   * There is a corresponding operand of type {@link TargetOperand} that checks
   * whether its relational expression, a descendant of the target, is
   * equivalent to this {@code QueryOperand}'s relational expression.
   */
  // QueryOperand（查询操作数）：将特定的关系表达式分配给变量的操作数
  // 应用于查询的子孙节点，将操作数写入槽数组，总是匹配
  // 有一个对应的TargetOperand操作数，检查目标的关系表达式是否与此QueryOperand的关系表达式等价
  private static class QueryOperand extends Operand {
    private final int ordinal; // 槽序号

    protected QueryOperand(int ordinal) {
      super(MutableRel.class); // 初始化为MutableRel类型
      this.ordinal = ordinal; // 初始化槽序号
    }

    @Override public boolean matches(SubstitutionVisitor visitor, MutableRel rel) {
      visitor.slots[ordinal] = rel; // 将关系表达式存入槽中
      return true; // 总是匹配
    }
  }

  /** Operand that checks that a relational expression matches the corresponding
   * relational expression that was passed to a {@link QueryOperand}. */
  // TargetOperand（目标操作数）：检查关系表达式是否匹配传递给QueryOperand的相应关系表达式的操作数
  private static class TargetOperand extends Operand {
    private final int ordinal; // 槽序号

    protected TargetOperand(int ordinal) {
      super(MutableRel.class); // 初始化为MutableRel类型
      this.ordinal = ordinal; // 初始化槽序号
    }

    @Override public boolean matches(SubstitutionVisitor visitor,
        MutableRel rel) {
      final MutableRel rel0 = visitor.slots[ordinal]; // 从槽中获取查询操作数存储的关系表达式
      requireNonNull(rel0, "QueryOperand should have been called first"); // 确保QueryOperand已被调用
      return rel0 == rel || visitor.equivalents.get(rel0).contains(rel); // 检查是否相同或等价
    }

    @Override public boolean isWeaker(SubstitutionVisitor visitor, MutableRel rel) {
      final MutableRel rel0 = visitor.slots[ordinal]; // 从槽中获取查询操作数存储的关系表达式
      requireNonNull(rel0, "QueryOperand should have been called first"); // 确保QueryOperand已被调用
      return visitor.isWeaker(rel0, rel); // 检查是否弱于
    }
  }

  /** Visitor that counts how many {@link QueryOperand} and
   * {@link TargetOperand} in an operand tree. */
  // SlotCounter（槽计数器）：访问器，计算操作数树中有多少QueryOperand和TargetOperand
  private static class SlotCounter {
    int queryCount; // QueryOperand计数
    int targetCount; // TargetOperand计数

    void visit(Operand operand) {
      // visit方法：访问操作数并计数
      if (operand instanceof QueryOperand) {
        ++queryCount; // QueryOperand计数加1
      } else if (operand instanceof TargetOperand) {
        ++targetCount; // TargetOperand计数加1
      } else if (operand instanceof AnyOperand) {
        // nothing
        // AnyOperand不需要计数
      } else {
        // 递归访问InternalOperand的子操作数
        for (Operand input : ((InternalOperand) operand).inputs) {
          visit(input);
        }
      }
    }
  }
}
