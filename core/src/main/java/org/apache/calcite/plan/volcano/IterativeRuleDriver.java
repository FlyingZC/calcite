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
package org.apache.calcite.plan.volcano; // 声明包名，该类属于org.apache.calcite.plan.volcano包，是火山优化器规划器相关类

import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数节点，是Calcite中关系表达式的基本构建块
import org.apache.calcite.util.trace.CalciteTrace; // 导入CalciteTrace工具类，用于获取日志追踪器

import org.slf4j.Logger; // 导入SLF4J日志接口，用于记录日志信息

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于参数非空检查

/***
 * IterativeRuleDriver是火山优化器中的规则驱动器实现类，采用迭代式算法执行优化规则
 * 
 * 类作用：
 * 该类实现了RuleDriver接口，负责驱动火山优化器的规则匹配和执行过程。
 * 它采用迭代式算法，通过循环从规则队列中取出匹配的规则并执行，直到队列为空。
 * 这是VolcanoPlanner的核心驱动机制之一，负责协调规则的触发和执行顺序。
 * 
 * 工作原理：
 * 1. 维护一个规则队列，存储所有待执行的规则匹配
 * 2. 在drive()方法中无限循环，从队列中弹出规则匹配
 * 3. 对每个规则匹配调用onMatch()方法执行规则转换
 * 4. 每次规则执行后调用canonize()规范化规划器状态
 * 5. 遇到超时异常或队列为空时停止优化过程
 * 
 * The algorithm executes repeatedly. The exact rules
 * that may be fired varies.
 * 
 * <p>The planner iterates over the rule matches presented
 * by the rule queue until the rule queue becomes empty.
 */
class IterativeRuleDriver implements RuleDriver { // 定义IterativeRuleDriver类，实现RuleDriver接口，表示迭代式规则驱动器

  private static final Logger LOGGER = CalciteTrace.getPlannerTracer(); // 成员变量：静态日志记录器，用于记录规划器执行过程中的调试信息，通过CalciteTrace获取规划器追踪器

  private final VolcanoPlanner planner; // 成员变量：火山优化器实例引用，final表示构造后不可变，用于访问规划器的各种状态和方法，如root节点、规范化操作等
  private final IterativeRuleQueue ruleQueue; // 成员变量：迭代式规则队列实例引用，final表示构造后不可变，用于存储和管理待执行的规则匹配，按优先级排序

  IterativeRuleDriver(VolcanoPlanner planner) { // 构造方法：初始化迭代式规则驱动器，接收VolcanoPlanner实例作为参数
    this.planner = planner; // 将传入的规划器实例赋值给成员变量planner，保存引用以便后续使用
    ruleQueue = new IterativeRuleQueue(planner); // 创建迭代式规则队列实例，传入规划器作为参数，初始化规则队列
  }

  @Override public IterativeRuleQueue getRuleQueue() { // 方法：重写RuleDriver接口方法，获取规则队列实例，允许外部访问当前的规则队列
    return ruleQueue; // 返回成员变量ruleQueue，提供对规则队列的访问
  }

  @Override public void drive() { // 方法：核心驱动方法，负责执行优化规则的主循环，这是整个规则驱动过程的核心逻辑
    while (true) { // 无限循环，持续从规则队列中取出并执行规则匹配，直到遇到break条件退出
      requireNonNull(planner.root, "RelSubset must not be null at this point"); // 检查规划器的root节点是否为null，如果为null抛出NullPointerException，确保规划器处于有效状态
      LOGGER.debug("Best cost before rule match: {}", planner.root.bestCost); // 记录调试日志，输出当前root节点的最佳成本，用于追踪优化过程中的成本变化

      VolcanoRuleMatch match = ruleQueue.popMatch(); // 从规则队列中弹出一个优先级最高的规则匹配对象，如果队列为空则返回null
      if (match == null) { // 判断是否获取到规则匹配，如果为null表示规则队列为空
        break; // 跳出while循环，结束优化过程，因为没有更多规则需要执行
      }

      assert match.getRule().matches(match); // 断言语句，验证规则是否与当前匹配对象匹配，确保规则的有效性，断言失败会抛出AssertionError
      try { // 开始try-catch块，捕获规则执行过程中可能抛出的异常
        match.onMatch(); // 调用规则匹配对象的onMatch()方法，执行规则转换逻辑，这是规则实际生效的地方
      } catch (VolcanoTimeoutException e) { // 捕获火山优化器超时异常，当优化过程超时抛出
        LOGGER.warn("Volcano planning times out, cancels the subsequent optimization."); // 记录警告日志，提示优化过程超时，取消后续优化
        planner.canonize(); // 调用规划器的canonize()方法，规范化规划器状态，清理和整理优化结果
        break; // 跳出while循环，结束优化过程
      }

      // The root may have been merged with another
      // subset. Find the new root subset.
      planner.canonize(); // 规范化规划器状态，处理可能发生的集合合并和根节点变化，确保规划器内部一致性
    } // while循环结束，优化过程完成

  } // drive()方法结束

  @Override public void onProduce(RelNode rel, RelSubset subset) { // 方法：重写RuleDriver接口方法，当产生新的关系节点时调用，当前实现为空，不需要特殊处理
  } // 空方法体，迭代式驱动器不需要处理产生事件

  @Override public void onSetMerged(RelSet set) { // 方法：重写RuleDriver接口方法，当关系集合被合并时调用，当前实现为空，不需要特殊处理
  } // 空方法体，迭代式驱动器不需要处理集合合并事件

  @Override public void clear() { // 方法：重写RuleDriver接口方法，清理规则驱动器的内部状态，释放资源
    ruleQueue.clear(); // 调用规则队列的clear()方法，清空队列中的所有规则匹配，重置队列状态
  } // clear()方法结束
} // IterativeRuleDriver类定义结束
