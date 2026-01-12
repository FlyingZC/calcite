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
package org.apache.calcite.piglet; // 包声明：Piglet模块，用于Pig与Calcite的集成

import org.apache.pig.impl.logicalLayer.FrontendException; // 导入Pig前端异常类，用于处理Pig操作中的错误
import org.apache.pig.impl.util.Utils; // 导入Pig工具类，提供集合合并等实用方法
import org.apache.pig.newplan.Operator; // 导入Pig操作符接口，表示Pig逻辑计划中的操作节点
import org.apache.pig.newplan.OperatorPlan; // 导入Pig操作计划接口，表示Pig逻辑计划
import org.apache.pig.newplan.PlanVisitor; // 导入Pig计划访问者接口，用于遍历计划中的节点
import org.apache.pig.newplan.PlanWalker; // 导入Pig计划遍历器基类，定义遍历计划的策略
import org.apache.pig.newplan.logical.relational.LogicalRelationalNodesVisitor; // 导入Pig逻辑关系节点访问者，用于访问关系型操作节点
import org.apache.pig.newplan.logical.relational.LogicalRelationalOperator; // 导入Pig逻辑关系操作符接口，表示关系型操作

import java.util.Collection; // 导入Java集合接口，用于存储操作符集合

/**
 * Post-order walker for Pig logical relational plans. Walks the plan
 * from sinks to sources.
 * // Pig逻辑关系计划的后序遍历器，从汇聚节点（sinks）向源节点（sources）遍历计划
 * // 这个类实现了后序遍历（post-order traversal）策略，即先访问子节点，再访问父节点
 * // 在Pig逻辑计划中，sinks是最终输出节点，sources是数据源节点
 * // 后序遍历确保在处理一个节点之前，它的所有前置节点都已经被处理过
 * // 这种遍历方式对于依赖分析和转换非常重要，因为父节点的处理可能需要子节点的结果
 */
class PigRelOpWalker extends PlanWalker { // PigRelOpWalker类继承自PlanWalker，实现Pig逻辑关系计划的后序遍历
  /**
   * Visitor that allow doing pre-visit.
   * // 允许进行预访问的访问者类
   * // 这是一个抽象内部类，继承自LogicalRelationalNodesVisitor
   * // 它扩展了标准访问者模式，增加了在访问节点之前执行检查的能力
   * // preVisit方法允许在真正访问节点之前进行一些预处理或判断
   * // 例如，可以检查节点是否已经被访问过，或者是否需要跳过某些节点
   */
  abstract static class PlanPreVisitor extends LogicalRelationalNodesVisitor { // 抽象静态内部类，继承自LogicalRelationalNodesVisitor
    PlanPreVisitor(OperatorPlan plan, PlanWalker walker) throws FrontendException { // 构造方法：初始化预访问者
      super(plan, walker); // 调用父类构造方法，传入操作计划和遍历器
    }

    /**
     * Called before a node.
     *
     * @param root Pig logical operator to check
     * @return Returns whether the node has been visited before
     * // 在访问节点之前被调用的抽象方法
     * // 参数root：要检查的Pig逻辑操作符
     * // 返回值：如果返回true表示节点已经被访问过，可以跳过；返回false表示节点未被访问，需要继续处理
     * // 这个方法允许访问者在真正访问节点之前进行判断，避免重复访问或跳过不需要处理的节点
     */
    public abstract boolean preVisit(LogicalRelationalOperator root); // 抽象方法：在访问节点之前调用，返回是否已访问
  }

  PigRelOpWalker(OperatorPlan plan) { // 构造方法：创建Pig关系操作遍历器实例
    super(plan); // 调用父类PlanWalker的构造方法，传入要遍历的操作计划
  }

  @Override public void walk(PlanVisitor planVisitor) throws FrontendException { // 重写walk方法：开始遍历操作计划
    if (!(planVisitor instanceof PigRelOpVisitor)) { // 检查传入的访问者是否为PigRelOpVisitor类型
      throw new FrontendException("Expected PigRelOpVisitor", 2223); // 如果不是，抛出前端异常，错误码2223
    }

    final PigRelOpVisitor pigRelVistor = (PigRelOpVisitor) planVisitor; // 将访问者强制转换为PigRelOpVisitor类型
    postOrderWalk(pigRelVistor.getCurrentRoot(), pigRelVistor); // 调用后序遍历方法，从当前根节点开始遍历
  }

  /**
   * Does post-order walk on the Pig logical relational plans from sinks to sources.
   *
   * @param root The root Pig logical relational operator
   * @param visitor The visitor of each Pig logical operator node
   * @throws FrontendException Exception during processing Pig operator
   * // 对Pig逻辑关系计划执行后序遍历，从汇聚节点到源节点
   * // 参数root：根Pig逻辑关系操作符，作为遍历的起点
   // 参数visitor：每个Pig逻辑操作符节点的访问者，用于处理每个节点
   // 抛出FrontendException：处理Pig操作符时的异常
   // 后序遍历的步骤：
   // 1. 首先检查当前节点是否为null或已被访问过
   // 2. 如果未被访问，递归遍历所有前置节点（ predecessors）
   // 3. 当所有前置节点都处理完毕后，再处理当前节点
   // 4. 这种顺序确保了在处理父节点时，子节点的信息已经完全可用
   */
  private void postOrderWalk(Operator root, PlanPreVisitor visitor) throws FrontendException { // 私有方法：执行后序遍历
    if (root == null || visitor.preVisit((LogicalRelationalOperator) root)) { // 检查根节点是否为null或已被访问过
      return; // 如果为null或已访问，直接返回，跳过当前节点
    }

    Collection<Operator> nexts = // 获取当前节点的所有前置节点
        Utils.mergeCollection(plan.getPredecessors(root), plan.getSoftLinkPredecessors(root)); // 合并硬链接和软链接的前置节点
    if (nexts != null) { // 如果存在前置节点
      for (Operator op : nexts) { // 遍历每个前置节点
        postOrderWalk(op, visitor); // 递归调用后序遍历方法，处理前置节点
      }
    }
    root.accept(visitor); // 当所有前置节点都处理完毕后，接受访问者处理当前节点
  }

  @Override public PlanWalker spawnChildWalker(OperatorPlan operatorPlan) { // 重写spawnChildWalker方法：创建子遍历器
    return new PigRelOpWalker(operatorPlan); // 返回一个新的PigRelOpWalker实例，用于遍历子计划
  }
}
