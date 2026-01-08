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
package org.apache.calcite.plan.hep; // 定义HepRuleCall类所在的包路径，位于org.apache.calcite.plan.hep包下，这是基于启发式规划器的实现包

import org.apache.calcite.plan.RelHintsPropagator; // 导入RelHintsPropagator类，用于在关系代数节点之间传播Hint信息（优化提示信息）
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，这是Calcite优化器的核心接口，定义了关系代数优化器的行为
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，这是规则调用的基类，HepRuleCall将继承这个基类
import org.apache.calcite.plan.RelOptRuleOperand; // 导入RelOptRuleOperand类，表示规则的操作数，定义了规则匹配的节点类型和约束条件
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil工具类，提供关系代数优化过程中常用的工具方法
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，这是Calcite中关系代数节点的基类，表示一个关系表达式（如TableScan、Filter、Project等）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数或返回值，帮助进行空值安全检查

import java.util.ArrayList; // 导入ArrayList类，这是Java集合框架中的动态数组实现，用于存储可变长度的元素列表
import java.util.List; // 导入List接口，这是Java集合框架中的列表接口，定义了有序集合的行为
import java.util.Map; // 导入Map接口，这是Java集合框架中的映射接口，定义了键值对集合的行为

/**
 * HepRuleCall implements {@link RelOptRuleCall} for a {@link HepPlanner}. It
 * remembers transformation results so that the planner can choose which one (if
 * any) should replace the original expression.
 * HepRuleCall为HepPlanner（基于启发式的规划器）实现了RelOptRuleCall接口。
 * 它会记住转换结果，以便规划器可以选择其中一个（如果有的话）来替换原始表达式。
 * 
 * 类作用详解：
 * 1. HepRuleCall是HepPlanner优化器中规则调用的具体实现，继承自RelOptRuleCall基类
 * 2. 它在基于启发式（Hep）的优化过程中，负责管理优化规则对关系代数树的转换操作
 * 3. 与标准的VolcanoPlanner不同，HepPlanner使用HepRuleCall来收集规则转换的所有可能结果
 * 4. 它维护一个results列表，存储规则转换产生的所有候选结果关系代数节点
 * 5. 规划器可以根据这些结果选择最优的转换方案，或者保留多个转换结果供后续优化步骤使用
 * 6. HepRuleCall支持规则产生多个转换结果，这对于启发式优化器探索不同的优化路径非常重要
 * 7. 它还处理Hint信息的传播，确保优化提示在转换过程中正确传递到新的关系代数节点
 * 8. HepRuleCall是HepPlanner实现迭代优化和规则应用的核心组件之一
 */
public class HepRuleCall extends RelOptRuleCall { // 定义HepRuleCall类，继承自RelOptRuleCall，表示在HepPlanner中的规则调用
  //~ Instance fields -------------------------------------------------------- // 成员变量区域的分隔符，用于代码可读性

  private final List<RelNode> results = new ArrayList<>(); // 成员变量：results列表，用于存储规则转换产生的所有结果关系代数节点，使用ArrayList实现动态数组，final表示引用不可变但内容可变

  //~ Constructors ----------------------------------------------------------- // 构造方法区域的分隔符，用于代码可读性

  HepRuleCall( // 构造方法：创建HepRuleCall实例，包级私有，只有同包（hep包）内的类可以调用
      RelOptPlanner planner, // 参数：planner，表示调用此规则的优化器实例，类型为RelOptPlanner接口
      RelOptRuleOperand operand, // 参数：operand，表示规则的操作数，定义了规则匹配的节点类型和约束条件
      RelNode[] rels, // 参数：rels，表示匹配到的关系代数节点数组，按照规则操作数的顺序排列
      Map<RelNode, List<RelNode>> nodeChildren, // 参数：nodeChildren，表示节点到其子节点列表的映射，用于构建关系代数树的父子关系
      @Nullable List<RelNode> parents) { // 参数：parents，表示匹配到的节点的父节点列表，可能为null，使用@Nullable注解标记
    super(planner, operand, rels, nodeChildren, parents); // 调用父类RelOptRuleCall的构造方法，初始化基类的成员变量
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔符，用于代码可读性

  @Override public void transformTo(RelNode rel, Map<RelNode, RelNode> equiv, // 方法：transformTo，重写父类方法，用于将规则转换结果添加到results列表中，@Override表示重写父类方法，public表示公开访问
      RelHintsPropagator handler) { // 参数：handler，Hint传播器，用于在转换过程中传播优化提示信息
    final RelNode rel0 = rels[0]; // 获取匹配到的第一个关系代数节点（通常是规则匹配的根节点），final表示此变量不可重新赋值
    RelOptUtil.verifyTypeEquivalence(rel0, rel, rel0); // 验证转换前后的关系代数节点的类型等价性，确保转换不改变数据的语义类型，这是优化规则正确性的重要检查
    rel = handler.propagate(rel0, rel); // 使用Hint传播器将原始节点的Hint信息传播到新的关系代数节点，确保优化提示在转换过程中不丢失
    results.add(rel); // 将转换后的关系代数节点添加到results列表中，收集所有可能的转换结果供规划器选择
    rel0.getCluster().invalidateMetadataQuery(); // 使原始节点所属的Cluster的元数据查询缓存失效，因为转换可能改变了统计信息或元数据，需要重新计算
  } // transformTo方法结束

  List<RelNode> getResults() { // 方法：getResults，包级私有方法，用于获取规则转换产生的所有结果列表
    return results; // 返回存储所有转换结果的results列表，调用者可以遍历这些结果选择最优的转换方案
  } // getResults方法结束
} // HepRuleCall类定义结束
