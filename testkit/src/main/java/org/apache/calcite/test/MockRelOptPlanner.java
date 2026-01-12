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
package org.apache.calcite.test;

import org.apache.calcite.DataContexts;
import org.apache.calcite.plan.AbstractRelOptPlanner;
import org.apache.calcite.plan.Context;
import org.apache.calcite.plan.RelHintsPropagator;
import org.apache.calcite.plan.RelOptCostImpl;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexExecutorImpl;
import org.apache.calcite.util.Pair;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * MockRelOptPlanner is a mock implementation of the {@link RelOptPlanner}
 * interface. // MockRelOptPlanner是RelOptPlanner接口的模拟实现
 * // 这是一个专门用于测试的规划器实现，不执行实际的优化逻辑
 * // 主要用于单元测试中验证规则是否能够正确匹配和转换关系表达式
 * // 与真正的优化器（如VolcanoPlanner）不同，MockRelOptPlanner只支持单个规则
 * // 它按照简单的深度优先顺序递归匹配规则，一旦匹配成功就立即应用转换
 * // 这种简化的设计使得测试特定的优化规则变得更加简单和可控
 */
public class MockRelOptPlanner extends AbstractRelOptPlanner {
  //~ Instance fields --------------------------------------------------------
  // 实例字段区域开始标记

  private RelNode root; // 根关系表达式，表示当前优化树的根节点，是规划器操作的起点

  private @Nullable RelOptRule rule; // 当前注册的优化规则，MockRelOptPlanner只支持单个规则，可以为null

  private RelNode transformationResult; // 规则转换后的结果关系表达式，当规则成功匹配并转换后，保存转换后的节点

  private long metadataTimestamp = 0L; // 元数据时间戳，用于标识关系表达式元数据的最后修改时间，已废弃

  //~ Methods ----------------------------------------------------------------
  // 方法区域开始标记

  /** Creates MockRelOptPlanner. */ // 创建MockRelOptPlanner实例
  public MockRelOptPlanner(Context context) { // 构造方法，接收上下文参数
    super(RelOptCostImpl.FACTORY, context); // 调用父类构造方法，传入成本工厂和上下文
    setExecutor(new RexExecutorImpl(DataContexts.EMPTY)); // 设置Rex表达式执行器，用于执行常量折叠等操作
  }

  @Override public void setRoot(RelNode rel) { // 设置根关系表达式
    this.root = rel; // 将传入的关系表达式赋值给root字段，作为优化树的根节点
  }

  @Override public @Nullable RelNode getRoot() { // 获取根关系表达式
    return root; // 返回当前设置的根关系表达式，可能为null
  }

  @Override public void clear() { // 清空规划器状态
    super.clear(); // 调用父类的clear方法，执行通用的清理操作
    this.rule = null; // 将当前规则设置为null，清空已注册的规则
  }

  @Override public List<RelOptRule> getRules() { // 获取已注册的规则列表
    return rule == null // 如果当前规则为null
        ? ImmutableList.of() : ImmutableList.of(rule); // 返回空列表，否则返回包含当前规则的不可变列表
  }

  @Override public boolean addRule(RelOptRule rule) { // 添加优化规则到规划器
    assert this.rule == null // 断言当前规则必须为null
        : "MockRelOptPlanner only supports a single rule"; // 如果不为null，抛出断言错误，因为MockRelOptPlanner只支持单个规则
    this.rule = rule; // 将传入的规则赋值给rule字段

    return false; // 返回false表示规则没有被添加（因为MockRelOptPlanner不支持真正的规则管理）
  }

  @Override public boolean removeRule(RelOptRule rule) { // 从规划器中移除优化规则
    return false; // 返回false，MockRelOptPlanner不支持移除规则
  }

  @Override public RelNode changeTraits(RelNode rel, RelTraitSet toTraits) { // 改变关系表达式的特征集
    return rel; // 直接返回原始关系表达式，MockRelOptPlanner不执行实际的trait转换
  }

  @Override public RelNode findBestExp() { // 寻找最优表达式，执行优化过程
    if (rule != null) { // 如果存在已注册的规则
      matchRecursive(root, null, -1); // 从根节点开始递归匹配规则，parent为null，ordinalInParent为-1
    }
    return root; // 返回优化后的根节点
  }

  /**
   * Recursively matches a rule. // 递归匹配规则
   * // 该方法按照深度优先顺序遍历关系表达式树，尝试匹配当前规则
   * // 一旦规则成功匹配并产生转换结果，立即停止遍历并应用转换
   *
   * @param rel             Relational expression // 当前要匹配的关系表达式节点
   * @param parent          Parent relational expression // 父关系表达式节点，用于替换操作
   * @param ordinalInParent Ordinal of relational expression among its // 当前节点在父节点的子节点列表中的索引位置
   *                        siblings
   * @return whether match occurred // 是否发生了匹配和转换
   */
  private boolean matchRecursive( // 私有方法：递归匹配规则
      RelNode rel, // 当前关系表达式节点
      @Nullable RelNode parent, // 父关系表达式节点
      int ordinalInParent) { // 当前节点在父节点中的索引
    List<RelNode> bindings = new ArrayList<RelNode>(); // 创建绑定列表，用于保存匹配到的关系表达式
    if (match( // 尝试匹配规则的操作数
        rule.getOperand(), // 获取规则的根操作数
        rel, // 传入当前关系表达式
        bindings)) { // 传入绑定列表，匹配成功时填充
      MockRuleCall call = // 如果匹配成功，创建模拟规则调用对象
          new MockRuleCall(
              this, // 传入当前规划器
              rule.getOperand(), // 传入规则的操作数
              bindings.toArray(new RelNode[0])); // 将绑定列表转换为数组传入
      if (rule.matches(call)) { // 调用规则的matches方法进行额外匹配检查
        rule.onMatch(call); // 如果通过检查，调用规则的onMatch方法执行转换
      }
    }

    if (transformationResult != null) { // 如果规则转换产生了结果
      if (parent == null) { // 如果父节点为null，说明当前是根节点
        root = transformationResult; // 直接替换根节点为转换结果
      } else { // 否则
        parent.replaceInput(ordinalInParent, transformationResult); // 替换父节点的指定输入为转换结果
      }
      return true; // 返回true表示发生了转换
    }

    List<? extends RelNode> children = rel.getInputs(); // 获取当前节点的所有子节点
    for (int i = 0; i < children.size(); ++i) { // 遍历每个子节点
      if (matchRecursive(children.get(i), rel, i)) { // 递归调用matchRecursive，传入子节点作为当前节点
        return true; // 如果子节点匹配成功，立即返回true，停止遍历
      }
    }
    return false; // 所有节点都未匹配成功，返回false
  }

  /**
   * Matches a relational expression to a rule. // 将关系表达式与规则的操作数进行匹配
   * // 该方法检查给定的关系表达式是否满足规则操作数的匹配条件
   * // 匹配过程包括：检查操作数类型、检查子节点数量、递归匹配子操作数
   * // 匹配成功时，将关系表达式添加到绑定列表中
   *
   * @param operand  Root operand of rule // 规则的根操作数，定义了要匹配的关系表达式模式
   * @param rel      Relational expression // 要匹配的关系表达式
   * @param bindings Bindings, populated on successful match // 绑定列表，匹配成功时会被填充
   * @return whether relational expression matched rule // 关系表达式是否匹配规则
   */
  private static boolean match(RelOptRuleOperand operand, RelNode rel, // 私有静态方法：匹配操作数和关系表达式
      List<RelNode> bindings) { // 绑定列表，用于保存匹配到的关系表达式
    if (!operand.matches(rel)) { // 调用操作数的matches方法检查是否匹配
      return false; // 如果不匹配，返回false
    }
    bindings.add(rel); // 将匹配的关系表达式添加到绑定列表中
    switch (operand.childPolicy) { // 根据子节点策略进行处理
    case ANY: // 如果策略是ANY，表示对子节点数量没有限制
      return true; // 直接返回true，匹配成功
    default: // 其他策略
      // fall through // 继续执行后续代码，检查子节点
    }
    List<RelOptRuleOperand> childOperands = operand.getChildOperands(); // 获取操作数的所有子操作数
    List<? extends RelNode> childRels = rel.getInputs(); // 获取关系表达式的所有子节点
    if (childOperands.size() != childRels.size()) { // 如果子操作数数量与子节点数量不相等
      return false; // 返回false，匹配失败
    }
    for (Pair<RelOptRuleOperand, ? extends RelNode> pair // 遍历每个子操作数和子节点对
        : Pair.zip(childOperands, childRels)) { // 将子操作数列表和子节点列表配对
      if (!match(pair.left, pair.right, bindings)) { // 递归调用match方法，匹配子操作数和子节点
        return false; // 如果任何一对不匹配，返回false
      }
    }
    return true; // 所有子操作数和子节点都匹配成功，返回true
  }

  @Override public RelNode register(RelNode rel, @Nullable RelNode equivRel) { // 注册关系表达式到规划器
    return rel; // 直接返回原始关系表达式，MockRelOptPlanner不执行实际的注册操作
  }

  @Override public RelNode ensureRegistered(RelNode rel, RelNode equivRel) { // 确保关系表达式已注册
    return rel; // 直接返回原始关系表达式，MockRelOptPlanner假设所有关系表达式都已注册
  }

  @Override public boolean isRegistered(RelNode rel) { // 检查关系表达式是否已注册
    return true; // 总是返回true，MockRelOptPlanner假设所有关系表达式都已注册
  }

  @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
  @Override public long getRelMetadataTimestamp(RelNode rel) { // 获取关系表达式的元数据时间戳
    return metadataTimestamp; // 返回元数据时间戳字段值
  }

  @Deprecated // to be removed before 2.0 // 已废弃，将在2.0版本前移除
  /** Allow tests to tweak the timestamp. */ // 允许测试调整时间戳
  public void setRelMetadataTimestamp(long metadataTimestamp) { // 设置元数据时间戳
    this.metadataTimestamp = metadataTimestamp; // 将传入的时间戳赋值给metadataTimestamp字段
  }

  //~ Inner Classes ----------------------------------------------------------
  // 内部类区域开始标记

  /** Mock call to a planner rule. */ // 模拟规划器规则的调用
  // MockRuleCall是RelOptRuleCall的模拟实现，用于在测试环境中模拟规则的调用过程
  // 它捕获规则转换的结果，并将其保存到外部类的transformationResult字段中
  private class MockRuleCall extends RelOptRuleCall { // 私有内部类，继承自RelOptRuleCall
    /**
     * Creates a MockRuleCall. // 创建MockRuleCall实例
     *
     * @param planner Planner // 规划器实例
     * @param operand Operand // 规则的操作数
     * @param rels    List of matched relational expressions // 匹配到的关系表达式列表
     */
    MockRuleCall( // 构造方法
        RelOptPlanner planner, // 规划器参数
        RelOptRuleOperand operand, // 操作数参数
        RelNode[] rels) { // 关系表达式数组参数
      super( // 调用父类构造方法
          planner, // 传入规划器
          operand, // 传入操作数
          rels, // 传入关系表达式数组
          Collections.emptyMap()); // 传入空的等价关系映射
    }

    @Override public void transformTo(RelNode rel, Map<RelNode, RelNode> equiv, // 将关系表达式转换为目标表达式
        RelHintsPropagator handler) { // 提示传播处理器
      transformationResult = rel; // 将转换后的关系表达式保存到外部类的transformationResult字段中
    }
  }
}
