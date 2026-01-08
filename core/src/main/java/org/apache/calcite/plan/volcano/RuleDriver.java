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
package org.apache.calcite.plan.volcano; // 定义包名，该接口位于火山优化器（VolcanoPlanner）的规则驱动模块中

import org.apache.calcite.rel.RelNode; // 导入关系表达式节点类，表示关系代数树中的一个节点

/**
 * A rule driver applies rules with designed algorithms. // 规则驱动器接口，使用设计的算法来应用优化规则
 * 
 * 类作用详解：
 * RuleDriver 是 Calcite 火山优化器（VolcanoPlanner）中的核心接口，定义了规则驱动器的行为规范。
 * 
 * 在火山优化器中，规则驱动器负责：
 * 1. 管理规则队列（RuleQueue）：存储待应用的优化规则
 * 2. 应用优化规则：按照特定算法从队列中取出规则并应用到关系表达式上
 * 3. 响应优化过程中的事件：如新的 RelNode 产生、RelSet 合并等
 * 4. 驱动优化过程：通过 drive() 方法启动和执行优化过程
 * 
 * 火山优化器采用基于规则的优化（CBO），通过不断应用转换规则来探索等价的关系表达式空间，
 * RuleDriver 控制着这个探索过程的策略和节奏。
 * 
 * 常见的实现类包括：
 * - IterativeRuleDriver：迭代式规则驱动器，循环应用规则直到达到收敛条件
 * - TopDownRuleDriver：自顶向下的规则驱动器，按照特定顺序应用规则
 * 
 * 这个接口的设计使得优化器可以灵活地切换不同的规则应用策略，而无需修改优化器的核心逻辑。
 */
interface RuleDriver { // 定义规则驱动器接口，所有具体的规则驱动器实现都必须实现此接口

  /**
   * Gets the rule queue. // 获取规则队列
   * 
   * 方法作用详解：
   * 返回当前规则驱动器使用的规则队列（RuleQueue）。
   * 
   * 规则队列的作用：
   * - 存储所有待应用的优化规则
   - 管理规则的优先级和调度顺序
   - 支持规则的添加、删除和检索
   * 
   * 返回的 RuleQueue 对象包含了：
   * - 当前所有可应用的规则
   - 规则的匹配信息（哪些 RelNode 可以被哪些规则匹配）
   - 规则的优先级和成本估计
   * 
   * 调用场景：
   * - 优化器需要查看当前待应用的规则时
   * - 调试和监控优化过程时
   * - 测试规则调度策略时
   * 
   * @return the rule queue // 返回规则队列对象，包含所有待应用的优化规则
   */
  RuleQueue getRuleQueue(); // 声明获取规则队列的方法，返回 RuleQueue 对象

  /**
   * Applies rules. // 应用规则
   * 
   * 方法作用详解：
   * 这是规则驱动器的核心方法，负责执行规则应用过程。
   * 
   * 执行流程通常包括：
   * 1. 从规则队列中获取待应用的规则
   * 2. 为规则找到匹配的 RelNode 或 RelSubset
   * 3. 应用规则生成新的等价 RelNode
   * 4. 将新的 RelNode 注册到优化器中
   * 5. 更新规则队列，添加新的规则匹配机会
   * 6. 重复上述过程，直到满足终止条件
   * 
   * 终止条件可能包括：
   * - 规则队列为空（没有更多规则可以应用）
   * - 达到最大迭代次数
   * - 成本不再降低（收敛）
   * - 超时
   * 
   * 不同实现类的 drive() 方法可能采用不同的策略：
   * - IterativeRuleDriver：循环直到队列为空
   * - TopDownRuleDriver：按照特定顺序应用规则
   * 
   * 注意事项：
   * - 此方法可能会长时间运行，因为优化过程可能很复杂
   * - 应该定期检查中断标志，以支持用户取消
   * - 需要正确处理异常，避免优化过程崩溃
   */
  void drive(); // 声明应用规则的方法，无返回值，执行规则应用的主循环

  /**
   * Callback when new RelNodes are added into RelSet. // 当新的 RelNode 被添加到 RelSet 时的回调方法
   * 
   * 方法作用详解：
   * 这是一个回调方法，在优化过程中产生新的关系表达式节点时被调用。
   * 
   * 调用时机：
   * - 当一个优化规则成功应用并生成新的 RelNode 时
   * - 当新的 RelNode 被注册到 RelSet 中时
   * - 当 RelSet 中添加新的物理实现时
   * 
   * 参数说明：
   * - rel：新生成的 RelNode，是规则应用的结果
   * - subset：该 RelNode 所属的 RelSubset（等价关系表达式集合）
   * 
   * 方法职责：
   * 1. 通知规则驱动器有新的 RelNode 产生
   * 2. 可能触发新的规则匹配机会
   * 3. 更新规则队列，添加与新 RelNode 相关的规则
   * 4. 可能触发进一步的优化动作
   * 
   * 实现要点：
   * - 应该高效处理，避免成为性能瓶颈
   * - 需要正确处理重复的 RelNode
   * - 应该触发相关规则的匹配检查
   * 
   * @param rel the new RelNode // 新生成的关系表达式节点
   * @param subset subset to add // 该节点所属的 RelSubset（等价关系表达式集合）
   */
  void onProduce(RelNode rel, RelSubset subset); // 声明新 RelNode 产生时的回调方法

  /**
   * Callback when RelSets are merged. // 当 RelSet 合并时的回调方法
   * 
   * 方法作用详解：
   * 这是一个回调方法，在优化过程中两个或多个 RelSet 被合并时被调用。
   * 
   * RelSet 合并的原因：
   * - 发现两个 RelSet 实际上是等价的
   * - 通过规则转换发现新的等价关系
   * - 优化器确定两个集合可以合并以减少搜索空间
   * 
   * 调用时机：
   * - 当 VolcanoPlanner 合并两个 RelSet 时
   * - 当发现新的等价关系导致集合合并时
   * 
   * 参数说明：
   * - set：合并后的结果 RelSet，包含了被合并集合的所有 RelNode
   * 
   * 方法职责：
   * 1. 通知规则驱动器发生了 RelSet 合并
   * 2. 更新规则队列，处理合并后可能产生的新规则匹配机会
   * 3. 清理与被合并集合相关的规则
   * 4. 可能触发进一步的优化动作
   * 
   * 实现要点：
   * - 需要正确处理合并后的规则匹配
   * - 应该移除重复的规则匹配
   * - 可能需要重新评估规则的优先级
   * 
   * @param set the merged result set // 合并后的结果 RelSet
   */
  void onSetMerged(RelSet set); // 声明 RelSet 合并时的回调方法

  /**
   * Clears this RuleDriver. // 清理规则驱动器
   * 
   * 方法作用详解：
   * 清理规则驱动器的内部状态，释放资源。
   * 
   * 调用时机：
   * - 优化过程结束时
   * - 优化器重置时
   * - 规则驱动器不再使用时
   * 
   * 方法职责：
   * 1. 清空规则队列
   * 2. 释放内部数据结构
   * 3. 清理缓存和临时数据
   * 4. 重置内部状态
   * 
   * 实现要点：
   * - 应该彻底清理，避免内存泄漏
   * - 清理后规则驱动器应该可以重新使用
   * - 需要正确处理并发访问（如果有）
   * 
   * 注意事项：
   * - 清理后不应再调用其他方法
   * - 如果规则驱动器正在运行，应该先停止再清理
   */
  void clear(); // 声明清理规则驱动器的方法
}
