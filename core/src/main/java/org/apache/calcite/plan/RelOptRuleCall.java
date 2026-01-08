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
package org.apache.calcite.plan; // 声明包名，该类属于org.apache.calcite.plan包，是Calcite优化器规划相关的核心包

import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式，是Calcite中所有关系代数操作符的基类
import org.apache.calcite.rel.core.Filter; // 导入Filter类，表示过滤操作符，用于在关系代数中实现WHERE子句
import org.apache.calcite.rel.hint.Hintable; // 导入Hintable接口，表示支持提示(Hint)的关系表达式，Hint可以影响优化器的行为
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据信息，如行数、成本等
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder工具类，用于构建关系表达式树，提供流式API来创建和组合关系操作符
import org.apache.calcite.util.trace.CalciteTrace; // 导入CalciteTrace工具类，用于获取Calcite的日志追踪器

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，提供线程安全的不可变列表实现
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类，提供线程安全的不可变Map实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的@Nullable注解，用于标记可能为null的值
import org.slf4j.Logger; // 导入SLF4J的Logger接口，用于日志记录

import java.util.HashMap; // 导入Java标准库的HashMap类，提供基于哈希表的Map实现
import java.util.List; // 导入Java标准库的List接口，表示有序集合
import java.util.Map; // 导入Java标准库的Map接口，表示键值对映射

/**
 * A <code>RelOptRuleCall</code> is an invocation of a {@link RelOptRule} with a
 * set of {@link RelNode relational expression}s as arguments.
 * RelOptRuleCall是RelOptRule的一次调用，带有一组作为参数的RelNode关系表达式
 * 这个类是Calcite优化器规则调用的核心抽象，每当一个优化规则匹配到一组关系表达式时，
 * 就会创建一个RelOptRuleCall对象来封装这次调用的上下文信息
 * 它是规则匹配和规则执行之间的桥梁，规则通过这个对象访问匹配到的关系表达式、优化器等信息
 */
public abstract class RelOptRuleCall { // 定义抽象类RelOptRuleCall，表示优化规则的一次调用
  //~ Static fields/initializers --------------------------------------------- // 静态字段和初始化器部分的分隔标记

  protected static final Logger LOGGER = CalciteTrace.getPlannerTracer(); // 声明静态日志记录器，用于记录优化器规划过程中的调试和错误信息，CalciteTrace.getPlannerTracer()返回专门用于优化器的日志追踪器

  /**
   * Generator for {@link #id} values.
   * id值的生成器，用于为每个RelOptRuleCall实例生成唯一的标识符
   */
  private static int nextId = 0; // 声明静态整型变量nextId，用于生成递增的ID值，初始值为0，每次创建RelOptRuleCall实例时会递增

  //~ Instance fields -------------------------------------------------------- // 实例字段部分的分隔标记

  public final int id; // 声明公共的final整型变量id，表示这个规则调用的唯一标识符，用于调试和追踪规则调用的顺序
  protected final RelOptRuleOperand operand0; // 声明受保护的final变量operand0，表示匹配到的根操作数(RelOptRuleOperand)，它是规则匹配模式的入口点
  protected Map<RelNode, List<RelNode>> nodeInputs; // 声明受保护的Map变量nodeInputs，用于存储每个匹配到的关系表达式及其输入子节点列表，特别用于处理可变数量子节点的情况
  public final RelOptRule rule; // 声明公共的final变量rule，表示被调用的优化规则(RelOptRule)，这个规则包含了匹配模式和转换逻辑
  public final RelNode[] rels; // 声明公共的final数组rels，存储所有匹配到的关系表达式，数组中的每个元素对应规则的一个操作数
  private final RelOptPlanner planner; // 声明私有的final变量planner，表示调用这个规则的优化器(RelOptPlanner)，优化器负责管理规则调用和关系表达式转换
  private final @Nullable List<RelNode> parents; // 声明私有的final变量parents，表示第一个关系表达式(rels[0])的父节点列表，可能为null，用于在转换时维护父节点关系

  //~ Constructors ----------------------------------------------------------- // 构造方法部分的分隔标记

  /**
   * Creates a RelOptRuleCall.
   * 创建一个RelOptRuleCall实例
   *
   * @param planner      Planner 优化器，负责规则调用和关系表达式管理
   * @param operand      Root operand 根操作数，表示规则匹配的入口点
   * @param rels         Array of relational expressions which matched each
   *                     operand 关系表达式数组，每个元素对应匹配到的操作数
   * @param nodeInputs   For each node which matched with
   *                     {@code matchAnyChildren}=true, a list of the node's
   *                     inputs 对于每个以matchAnyChildren=true匹配的节点，存储其输入子节点列表
   * @param parents      list of parent RelNodes corresponding to the first
   *                     relational expression in the array argument, if known;
   *                     otherwise, null 对应于第一个关系表达式的父节点列表，如果已知则提供，否则为null
   */
  protected RelOptRuleCall( // 定义受保护的构造方法，用于创建RelOptRuleCall实例
      RelOptPlanner planner, // 参数：优化器实例
      RelOptRuleOperand operand, // 参数：根操作数
      RelNode[] rels, // 参数：匹配到的关系表达式数组
      Map<RelNode, List<RelNode>> nodeInputs, // 参数：节点输入映射
      @Nullable List<RelNode> parents) { // 参数：父节点列表，可能为null
    this.id = nextId++; // 为当前实例分配唯一的递增ID，并递增nextId计数器
    this.planner = planner; // 保存优化器引用
    this.operand0 = operand; // 保存根操作数引用
    this.nodeInputs = nodeInputs; // 保存节点输入映射
    this.rule = operand.getRule(); // 从操作数中获取对应的优化规则
    this.rels = rels; // 保存匹配到的关系表达式数组
    this.parents = parents; // 保存父节点列表
    assert rels.length == rule.operands.size(); // 断言检查：确保关系表达式数量与规则的操作数数量一致，这是规则匹配正确性的重要保证
  }

  protected RelOptRuleCall( // 定义另一个受保护的构造方法，不提供父节点列表的简化版本
      RelOptPlanner planner, // 参数：优化器实例
      RelOptRuleOperand operand, // 参数：根操作数
      RelNode[] rels, // 参数：匹配到的关系表达式数组
      Map<RelNode, List<RelNode>> nodeInputs) { // 参数：节点输入映射
    this(planner, operand, rels, nodeInputs, null); // 调用完整构造方法，将parents参数设为null
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分的分隔标记

  /**
   * Returns the root operand matched by this rule.
   * 返回此规则匹配到的根操作数
   *
   * @return root operand 根操作数
   */
  public RelOptRuleOperand getOperand0() { // 定义公共方法，获取根操作数
    return operand0; // 返回存储的根操作数
  }

  /**
   * Returns the invoked planner rule.
   * 返回被调用的优化器规则
   *
   * @return planner rule 优化器规则
   */
  public RelOptRule getRule() { // 定义公共方法，获取优化规则
    return rule; // 返回存储的优化规则
  }

  /**
   * Returns a list of matched relational expressions.
   * 返回匹配到的关系表达式列表
   *
   * @return matched relational expressions 匹配到的关系表达式数组
   * @deprecated Use {@link #getRelList()} or {@link #rel(int)}
   * 已废弃：请使用getRelList()或rel(int)方法替代
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public RelNode[] getRels() { // 定义公共方法，返回关系表达式数组
    return rels; // 返回存储的关系表达式数组
  }

  /**
   * Returns a list of matched relational expressions.
   * 返回匹配到的关系表达式列表
   *
   * @return matched relational expressions 匹配到的关系表达式不可变列表
   * @see #rel(int) 参见rel(int)方法获取单个关系表达式
   */
  public List<RelNode> getRelList() { // 定义公共方法，返回关系表达式的不可变列表
    return ImmutableList.copyOf(rels); // 使用Guava的ImmutableList.copyOf创建rels数组的不可变副本并返回
  }

  /**
   * Retrieves the {@code ordinal}th matched relational expression. This
   * corresponds to the {@code ordinal}th operand of the rule.
   * 检索第ordinal个匹配到的关系表达式，这对应于规则的第ordinal个操作数
   *
   * @param ordinal Ordinal 序号，表示要获取的关系表达式的索引位置
   * @param <T>     Type 泛型类型参数，继承自RelNode
   * @return Relational expression 关系表达式
   */
  public <T extends RelNode> T rel(int ordinal) { // 定义公共泛型方法，根据序号获取指定类型的关系表达式
    //noinspection unchecked // 抑制未检查的类型转换警告
    return (T) rels[ordinal]; // 强制类型转换并返回rels数组中指定位置的关系表达式
  }

  /**
   * Returns the children of a given relational expression node matched in a
   * rule.
   * 返回规则中匹配到的给定关系表达式节点的子节点
   *
   * <p>If the policy of the operand which caused the match is not
   * {@link org.apache.calcite.plan.RelOptRuleOperandChildPolicy#ANY},
   * the children will have their
   * own operands and therefore be easily available in the array returned by
   * the {@link #getRelList()} method, so this method returns null.
   * 如果导致匹配的操作数的策略不是ANY，则子节点会有自己的操作数，
   * 因此可以通过getRelList()方法返回的数组轻松获取，所以此方法返回null
   *
   * <p>This method is for
   * {@link org.apache.calcite.plan.RelOptRuleOperandChildPolicy#ANY},
   * which is generally used when a node can have a variable number of
   * children, and hence where the matched children are not retrievable by any
   * other means.
   * 此方法专门用于ANY策略，通常用于节点可以有可变数量子节点的情况，
   * 此时匹配到的子节点无法通过其他方式获取
   *
   * <p>Warning: it produces wrong result for {@code unordered(...)} case.
   * 警告：对于unordered(...)情况，此方法会产生错误结果
   *
   * @param rel Relational expression 关系表达式
   * @return Children of relational expression 关系表达式的子节点列表，如果不存在则返回null
   */
  public @Nullable List<RelNode> getChildRels(RelNode rel) { // 定义公共方法，获取指定关系表达式的子节点列表
    return nodeInputs.get(rel); // 从nodeInputs映射中查找并返回指定关系表达式的子节点列表
  }

  /** Assigns the input relational expressions of a given relational expression,
   * as seen by this particular call. Is only called when the operand is
   * {@link RelRule.OperandDetailBuilder#anyInputs() any}.
   * 为给定的关系表达式分配输入关系表达式，从此次特定的调用的角度来看
   * 只有当操作数是anyInputs()时才会调用此方法
   */
  protected void setChildRels(RelNode rel, List<RelNode> inputs) { // 定义受保护方法，设置指定关系表达式的子节点列表
    if (nodeInputs.isEmpty()) { // 检查nodeInputs映射是否为空
      nodeInputs = new HashMap<>(); // 如果为空，创建一个新的HashMap实例
    }
    nodeInputs.put(rel, inputs); // 将关系表达式和其输入列表的映射关系存入nodeInputs
  }

  /**
   * Returns the planner.
   * 返回优化器
   *
   * @return planner 优化器实例
   */
  public RelOptPlanner getPlanner() { // 定义公共方法，获取优化器
    return planner; // 返回存储的优化器引用
  }

  /**
   * Determines whether the rule is excluded by any root node hint.
   * 确定规则是否被任何根节点提示排除
   *
   * @return true iff rule should be excluded 如果规则应该被排除则返回true
   */
  public boolean isRuleExcluded() { // 定义公共方法，检查规则是否被提示排除
    for (RelNode rel : rels) { // 遍历所有匹配到的关系表达式
      if (!(rel instanceof Hintable)) { // 检查关系表达式是否实现了Hintable接口
        continue; // 如果不支持提示，跳过当前关系表达式
      }
      if (rel.getCluster() // 获取关系表达式所属的集群
              .getHintStrategies() // 获取集群的提示策略管理器
              .isRuleExcluded((Hintable) rel, rule)) { // 检查提示策略是否排除了当前规则
        return true; // 如果规则被排除，立即返回true
      }
    }
    return false; // 如果没有任何提示排除此规则，返回false
  }

  /**
   * Returns the current RelMetadataQuery
   * to be used for instance by
   * {@link RelOptRule#onMatch(RelOptRuleCall)}.
   * 返回当前的RelMetadataQuery，例如在RelOptRule.onMatch(RelOptRuleCall)中使用
   */
  public RelMetadataQuery getMetadataQuery() { // 定义公共方法，获取元数据查询对象
    return rel(0).getCluster().getMetadataQuery(); // 从第一个关系表达式的集群中获取元数据查询对象
  }

  /**
   * Returns a list of parents of the first relational expression.
   * 返回第一个关系表达式的父节点列表
   */
  public @Nullable List<RelNode> getParents() { // 定义公共方法，获取父节点列表
    return parents; // 返回存储的父节点列表，可能为null
  }

  /**
   * Registers that a rule has produced an equivalent relational expression.
   * 注册规则已经产生了一个等价的关系表达式
   *
   * <p>Called by the rule whenever it finds a match. The implementation of
   * this method guarantees that the original relational expression (that is,
   * <code>this.rels[0]</code>) has its traits propagated to the new
   * relational expression (<code>rel</code>) and its unregistered children.
   * Any trait not specifically set in the RelTraitSet returned by <code>
   * rel.getTraits()</code> will be copied from <code>
   * this.rels[0].getTraitSet()</code>.
   * 当规则找到匹配时调用此方法。此方法的实现保证原始关系表达式(this.rels[0])
   * 的特征被传播到新的关系表达式(rel)及其未注册的子节点。
   * 任何在rel.getTraits()返回的RelTraitSet中未明确设置的特征都将从this.rels[0].getTraitSet()复制
   *
   * <p>The hints of the root relational expression of
   * the rule call(<code>this.rels[0]</code>)
   * are copied to the new relational expression(<code>rel</code>)
   * with specified handler {@code handler}.
   * 规则调用的根关系表达式(this.rels[0])的提示被复制到新的关系表达式(rel)，
   * 使用指定的处理器handler
   *
   * @param rel     Relational expression equivalent to the root relational
   *                expression of the rule call, {@code call.rels(0)}
   *                等价于规则调用的根关系表达式(call.rels(0))的关系表达式
   * @param equiv   Map of other equivalences 其他等价关系的映射
   * @param handler Handler to customize the relational expression that registers
   *                into the planner, the first parameter is the root relational expression
   *                and the second parameter is the new relational expression
   *                处理器，用于自定义注册到优化器的关系表达式，第一个参数是根关系表达式，
   *                第二个参数是新的关系表达式
   */
  public abstract void transformTo(RelNode rel, // 定义抽象方法，注册规则产生的等价关系表达式，带有等价映射和提示处理器
      Map<RelNode, RelNode> equiv, // 参数：其他等价关系的映射
      RelHintsPropagator handler); // 参数：提示传播处理器

  /**
   * Registers that a rule has produced an equivalent relational expression,
   * with specified equivalences.
   * 注册规则已经产生了一个等价的关系表达式，带有指定的等价关系
   *
   * <p>The hints are copied with filter strategies from
   * the root relational expression of the rule call(<code>this.rels[0]</code>)
   * to the new relational expression(<code>rel</code>).
   * 提示从规则调用的根关系表达式(this.rels[0])复制到新的关系表达式(rel)，
   * 使用过滤策略
   *
   * @param rel   Relational expression equivalent to the root relational
   *              expression of the rule call, {@code call.rels(0)}
   *              等价于规则调用的根关系表达式(call.rels(0))的关系表达式
   * @param equiv Map of other equivalences 其他等价关系的映射
   */
  public void transformTo(RelNode rel, Map<RelNode, RelNode> equiv) { // 定义公共方法，注册等价关系表达式，使用默认的提示传播策略
    transformTo(rel, equiv, RelOptUtil::propagateRelHints); // 调用完整的transformTo方法，使用RelOptUtil.propagateRelHints作为提示处理器
  }

  /**
   * Registers that a rule has produced an equivalent relational expression,
   * but no other equivalences.
   * 注册规则已经产生了一个等价的关系表达式，但没有其他等价关系
   *
   * <p>The hints are copied with filter strategies from
   * the root relational expression of the rule call(<code>this.rels[0]</code>)
   * to the new relational expression(<code>rel</code>).
   * 提示从规则调用的根关系表达式(this.rels[0])复制到新的关系表达式(rel)，
   * 使用过滤策略
   *
   * @param rel Relational expression equivalent to the root relational
   *            expression of the rule call, {@code call.rels(0)}
   *            等价于规则调用的根关系表达式(call.rels(0))的关系表达式
   */
  public final void transformTo(RelNode rel) { // 定义公共final方法，注册等价关系表达式，不带其他等价关系
    transformTo(rel, ImmutableMap.of()); // 调用transformTo方法，传入空的不可变Map作为等价映射
  }

  /**
   * Registers that a rule has produced an equivalent relational expression,
   * but no other equivalences.
   * 注册规则已经产生了一个等价的关系表达式，但没有其他等价关系
   *
   * <p>The hints of the root relational expression of
   * the rule call(<code>this.rels[0]</code>)
   * are copied to the new relational expression(<code>rel</code>)
   * with specified handler {@code handler}.
   * 规则调用的根关系表达式(this.rels[0])的提示被复制到新的关系表达式(rel)，
   * 使用指定的处理器handler
   *
   * @param rel     Relational expression equivalent to the root relational
   *                expression of the rule call, {@code call.rels(0)}
   *                等价于规则调用的根关系表达式(call.rels(0))的关系表达式
   * @param handler Handler to customize the relational expression that registers
   *                into the planner, the first parameter is the root relational expression
   *                and the second parameter is the new relational expression
   *                处理器，用于自定义注册到优化器的关系表达式，第一个参数是根关系表达式，
   *                第二个参数是新的关系表达式
   *
   */
  public final void transformTo(RelNode rel, RelHintsPropagator handler) { // 定义公共final方法，注册等价关系表达式，使用自定义提示处理器
    transformTo(rel, ImmutableMap.of(), handler); // 调用完整的transformTo方法，传入空的不可变Map和自定义提示处理器
  }

  /** Creates a {@link org.apache.calcite.tools.RelBuilder} to be used by
   * code within the call. The {@link RelOptRule#relBuilderFactory} argument contains policies
   * such as what implementation of {@link Filter} to create.
   * 创建一个RelBuilder，供调用内的代码使用。RelOptRule.relBuilderFactory参数包含策略，
   * 例如要创建Filter的哪个实现
   */
  public RelBuilder builder() { // 定义公共方法，创建RelBuilder实例
    return rule.relBuilderFactory.create(rel(0).getCluster(), null); // 使用规则的relBuilderFactory创建RelBuilder，传入第一个关系表达式的集群和null上下文
  }
}