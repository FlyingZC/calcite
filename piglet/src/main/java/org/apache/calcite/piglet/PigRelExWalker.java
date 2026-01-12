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
package org.apache.calcite.piglet;

import org.apache.pig.impl.logicalLayer.FrontendException; // Pig前端异常类，用于处理Pig操作过程中的错误
import org.apache.pig.impl.util.Utils; // Pig工具类，提供集合合并等实用方法
import org.apache.pig.newplan.Operator; // Pig操作符接口，表示Pig逻辑计划中的操作节点
import org.apache.pig.newplan.OperatorPlan; // Pig操作计划接口，表示一个操作符的集合
import org.apache.pig.newplan.PlanVisitor; // Pig计划访问者接口，用于遍历和访问计划中的操作符
import org.apache.pig.newplan.PlanWalker; // Pig计划遍历器抽象类，定义了遍历计划的接口
import org.apache.pig.newplan.logical.expression.LogicalExpressionPlan; // Pig逻辑表达式计划类，表示Pig中的表达式树

import java.util.Collection; // Java集合接口，用于存储操作符集合

/**
 * Post-order walker for Pig expression plans. Walk the plan from
 * source to sinks.
 * Pig表达式计划的后序遍历器，用于从源到汇遍历计划
 * 该类继承自Pig的PlanWalker抽象类，实现了后序遍历（深度优先遍历）算法
 * 后序遍历意味着先访问子节点，再访问父节点，这对于表达式求值非常重要
 * 因为表达式的值通常依赖于其子表达式的值
 * 该类主要用于将Pig的逻辑表达式计划转换为Calcite的关系表达式
 * 通过访问者模式，遍历表达式树中的每个节点并进行转换处理
 */
class PigRelExWalker extends PlanWalker { // 定义PigRelExWalker类，继承自PlanWalker，用于遍历Pig表达式计划
  PigRelExWalker(OperatorPlan plan) { // 构造方法，创建一个新的PigRelExWalker实例，接收一个操作计划作为参数
    super(plan); // 调用父类PlanWalker的构造方法，将传入的操作计划保存到父类的plan字段中
  } // 构造方法结束

  @Override public void walk(PlanVisitor planVisitor) throws FrontendException { // 重写父类PlanWalker的walk方法，开始遍历操作计划，接收一个计划访问者作为参数
    if (!(planVisitor instanceof PigRelExVisitor)) { // 检查传入的访问者是否是PigRelExVisitor类型
      throw new FrontendException("Expected PigRelOpVisitor", 2223); // 如果不是，抛出前端异常，错误码2223
    } // 类型检查结束
    if (!(getPlan() instanceof LogicalExpressionPlan)) { // 检查当前计划是否是逻辑表达式计划类型
      throw new FrontendException("Expected LogicalExpressionPlan", 2223); // 如果不是，抛出前端异常，错误码2223
    } // 类型检查结束

    final PigRelExVisitor pigRelVistor = (PigRelExVisitor) planVisitor; // 将传入的访问者强制转换为PigRelExVisitor类型，用于后续访问操作符
    final LogicalExpressionPlan plan = (LogicalExpressionPlan) getPlan(); // 将当前计划强制转换为逻辑表达式计划类型，用于后续遍历

    if (plan.getSources().isEmpty()) { // 检查计划中的源操作符（根节点）是否为空
      return; // 如果为空，直接返回，无需遍历
    } // 空计划检查结束

    if (plan.getSources().size() > 1) { // 检查计划中的源操作符数量是否大于1
      throw new FrontendException( // 如果大于1，抛出前端异常，因为表达式计划应该只有一个根节点
          "Found LogicalExpressionPlan with more than one root.  Unexpected.", 2224); // 错误信息，错误码2224
    } // 多根节点检查结束

    postOrderWalk(plan.getSources().get(0), pigRelVistor); // 调用postOrderWalk方法，从根节点开始后序遍历整个表达式计划
  } // walk方法结束

  /**
   * Does post-order walk on the Pig expression plan from source to sinks.
   * 对Pig表达式计划进行后序遍历，从源节点遍历到汇节点
   * 后序遍历的顺序是：先遍历所有子节点，再访问当前节点
   * 这种遍历方式对于表达式树非常重要，因为计算表达式值时需要先计算子表达式的值
   *
   * @param root The root expression operator // 根表达式操作符，即当前要遍历的节点
   * @param visitor The visitor of each Pig expression node. // 访问者对象，用于访问每个Pig表达式节点并进行转换处理
   * @throws FrontendException Exception during processing Pig operator // 处理Pig操作符时可能抛出的前端异常
   */
  private void postOrderWalk(Operator root, PlanVisitor visitor) throws FrontendException { // 私有方法，递归实现后序遍历算法
    final Collection<Operator> nexts = // 定义一个操作符集合，用于存储当前节点的所有后继节点
        Utils.mergeCollection(plan.getSuccessors(root), plan.getSuccessors(root)); // 使用Utils工具类合并后继节点集合，这里合并了两次相同的集合，可能是为了去重
    if (nexts != null) { // 检查后继节点集合是否不为空
      for (Operator op : nexts) { // 遍历所有后继节点
        postOrderWalk(op, visitor); // 递归调用postOrderWalk方法，先处理子节点（后序遍历的核心）
      } // 后继节点遍历结束
    } // 后继节点检查结束
    root.accept(visitor); // 在处理完所有子节点后，访问当前节点，让访问者对当前节点进行处理
  } // postOrderWalk方法结束

  @Override public PlanWalker spawnChildWalker(OperatorPlan operatorPlan)  { // 重写父类PlanWalker的spawnChildWalker方法，用于创建子遍历器
    return new PigRelExWalker(operatorPlan); // 创建并返回一个新的PigRelExWalker实例，用于遍历子计划
  } // spawnChildWalker方法结束
} // PigRelExWalker类定义结束
