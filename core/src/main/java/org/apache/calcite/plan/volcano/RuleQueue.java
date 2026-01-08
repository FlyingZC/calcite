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
package org.apache.calcite.plan.volcano; // 定义包名，该类属于Volcano优化器模块

import org.apache.calcite.plan.RelOptRuleOperand; // 导入RelOptRuleOperand类，表示规则的操作数
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式节点
import org.apache.calcite.util.Util; // 导入Util工具类

import java.util.ArrayDeque; // 导入ArrayDeque类，双端队列实现
import java.util.Deque; // 导入Deque接口，双端队列

/**
 * RuleQueue是一个抽象类，用于管理RuleDriver的规则匹配（rule matches）数据结构。
 * 不同的RuleDriver需要不同的方式来弹出匹配，因此需要不同的方式来存储未被调用的规则匹配。
 * 
 * 核心作用：
 * 1. 作为规则匹配的容器，管理优化过程中发现的所有规则匹配
 * 2. 提供添加匹配、清空队列等基础操作
 * 3. 提供匹配过滤功能，跳过无效或重复的匹配
 * 4. 为不同的RuleDriver实现提供不同的匹配弹出策略
 * 
 * 在Volcano优化器中，规则匹配是指某个转换规则可以应用到特定的RelNode组合上。
 * RuleQueue负责存储和管理这些匹配，以便优化器按照特定策略选择和应用匹配。
 */
public abstract class RuleQueue { // 定义抽象类RuleQueue，用于管理规则匹配

  protected final VolcanoPlanner planner; // 成员变量：持有VolcanoPlanner的引用，用于访问优化器的状态和方法（如获取子集、检查剪枝节点等）

  protected RuleQueue(VolcanoPlanner planner) { // 构造方法：接收VolcanoPlanner实例作为参数
    this.planner = planner; // 将传入的planner赋值给成员变量，建立与优化器的关联
  }

  /**
   * 向队列中添加一个规则匹配（RuleMatch）。
   * 这是一个抽象方法，由具体的子类实现，不同子类可能有不同的添加策略。
   *
   * @param match 要添加的规则匹配对象
   */
  public abstract void addMatch(VolcanoRuleMatch match); // 抽象方法：向队列添加规则匹配，由子类实现具体逻辑

  /**
   * 清空规则队列。
   * 返回值指示在清空之前队列是否为空。
   * 这是一个抽象方法，由具体的子类实现。
   *
   * @return 如果规则队列在清空前不为空，则返回true；否则返回false
   */
  public abstract boolean clear(); // 抽象方法：清空队列并返回清空前是否非空，由子类实现具体逻辑


  /**
   * 判断是否应该跳过某个匹配。
   * 如果匹配中的任何一个RelNode的重要性为零（被剪枝），则跳过该匹配。
   * 此外，还会检查匹配中是否存在循环引用（同一个子集在同一路径上出现多次）。
   * 
   * @param match 要检查的规则匹配
   * @return 如果应该跳过该匹配，则返回true；否则返回false
   */
  protected boolean skipMatch(VolcanoRuleMatch match) { // 方法：判断是否跳过某个匹配，用于过滤无效匹配
    for (RelNode rel : match.rels) { // 遍历匹配中的所有关系表达式节点
      if (planner.prunedNodes.contains(rel)) { // 检查当前节点是否在已剪枝节点集合中
        return true; // 如果节点被剪枝，则跳过该匹配
      }
    }

    // 如果从根操作数到叶子操作数的任何路径上，同一个子集出现多次，则我们匹配到了一个循环。
    // 一个消耗自身输出的关系表达式永远无法被实现，而且如果我们对其触发规则，可能会产生大量垃圾。
    // 例如，如果 Project(A, X = X + 0) 与 A 在同一个子集中，那么我们会生成：
    //   Project(A, X = X + 0 + 0)
    //   Project(A, X = X + 0 + 0 + 0)
    // 它们也在同一个子集中。这些表达式是有效的，但是无用的。
    final Deque<RelSubset> subsets = new ArrayDeque<>(); // 创建一个双端队列，用于存储当前路径上的子集
    try { // 使用try-catch来捕获检查重复子集时抛出的异常
      checkDuplicateSubsets(subsets, match.rule.getOperand(), match.rels); // 递归检查是否存在重复子集
    } catch (Util.FoundOne e) { // 捕获FoundOne异常，表示发现了重复子集
      return true; // 发现重复子集，跳过该匹配
    }
    return false; // 没有发现重复子集，不跳过该匹配
  }

  /**
   * 递归检查从操作数树的根到叶子节点的任何路径上是否存在重复的子集。
   * 
   * 如果匹配中有重复的子集但不在同一条路径上，这是允许的。例如：
   *   Join
   *  /   \
   * X     X
   * 
   * 这是一个有效的匹配，因为X出现在不同的分支上，不会形成循环。
   * 
   * @param subsets 当前路径上的子集栈，用于检测重复
   * @param operand 当前检查的操作数
   * @param rels 与操作数对应的关系表达式数组
   * @throws org.apache.calcite.util.Util.FoundOne 如果发现重复子集，则抛出此异常
   */
  private void checkDuplicateSubsets(Deque<RelSubset> subsets, // 参数：当前路径上的子集栈
      RelOptRuleOperand operand, RelNode[] rels) { // 参数：当前操作数和对应的关系表达式数组
    final RelSubset subset = planner.getSubsetNonNull(rels[operand.ordinalInRule]); // 根据操作数在规则中的序号获取对应的关系表达式所属的子集
    if (subsets.contains(subset)) { // 检查当前子集是否已经在路径栈中
      throw Util.FoundOne.NULL; // 如果子集已存在，抛出异常表示发现重复
    }
    if (!operand.getChildOperands().isEmpty()) { // 检查当前操作数是否有子操作数
      subsets.push(subset); // 将当前子集压入栈中，表示进入下一层
      for (RelOptRuleOperand childOperand : operand.getChildOperands()) { // 遍历所有子操作数
        checkDuplicateSubsets(subsets, childOperand, rels); // 递归检查每个子操作数的路径
      }
      final RelSubset x = subsets.pop(); // 弹出栈顶的子集，表示回溯到上一层
      assert x == subset; // 断言弹出的子集就是之前压入的子集，确保栈的正确性
    }
  }
}
