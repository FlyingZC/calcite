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
package org.apache.calcite.plan.volcano;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.rules.SubstitutionRule;
import org.apache.calcite.util.trace.CalciteTrace;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * 迭代规则队列：用于管理VolcanoPlanner中规则匹配的优先级队列
 * 
 * 作用说明：
 * 1. 维护一个规则匹配的队列，这些匹配尚未被应用规则处理
 * 2. 支持两种类型的规则匹配：SubstitutionRule（替换规则）和普通规则
 * 3. 提供规则匹配的添加、移除和遍历功能
 * 4. 通过MatchList内部类管理不同优先级的规则匹配
 * 5. 避免重复的规则匹配，提高优化效率
 * 
 * 核心机制：
 * - 使用两个队列：preQueue（优先处理SubstitutionRule）和queue（普通规则）
 * - 使用names集合快速检测重复的规则匹配
 * - 使用matchMap建立RelSubset到规则匹配的多值映射
 * - 支持规则匹配的动态添加和移除
 */
class IterativeRuleQueue extends RuleQueue {
  //~ Static fields/initializers ---------------------------------------------

  private static final Logger LOGGER = CalciteTrace.getPlannerTracer(); // 获取规划器的追踪日志记录器

  //~ Instance fields --------------------------------------------------------

  /**
   * 规则匹配列表，用于管理所有待处理的规则匹配
   * 
   * 作用说明：
   * - 初始状态为空的MatchList对象
   * - 当规划器调用addMatch方法时，规则匹配会被添加到合适的队列中
   * - 当规划器完成匹配处理后，对应的条目会从列表中移除，避免无效工作
   * - 包含两个队列：preQueue（SubstitutionRule优先队列）和queue（普通规则队列）
   * - 提供快速查找和去重功能
   */
  final MatchList matchList = new MatchList(); // 规则匹配列表，管理所有待处理的规则匹配

  //~ Constructors -----------------------------------------------------------

  // 构造函数：创建迭代规则队列并将其关联到指定的VolcanoPlanner规划器
  // 参数：planner - VolcanoPlanner实例，用于规则匹配和优化
  IterativeRuleQueue(VolcanoPlanner planner) {
    super(planner); // 调用父类RuleQueue的构造函数，初始化规划器引用
  }

  //~ Methods ----------------------------------------------------------------
  /**
   * 清空规则队列的内部数据结构
   * 
   * 作用说明：
   * - 检查队列是否为空
   * - 清空matchList中的所有队列和集合
   * - 返回是否清空了非空队列（即是否有内容被清除）
   * 
   * 返回值：true表示清空了非空队列，false表示队列原本就是空的
   */
  @Override public boolean clear() {
    boolean empty = true; // 假设队列为空
    if (!matchList.queue.isEmpty() || !matchList.preQueue.isEmpty()) { // 检查普通规则队列或优先队列是否不为空
      empty = false; // 如果有任一队列不为空，则标记为非空
    }
    matchList.clear(); // 清空matchList中的所有数据结构（队列、names集合、matchMap映射）
    return !empty; // 返回是否清空了非空队列（如果原本为空返回false，否则返回true）
  }

  /**
   * 添加规则匹配到队列中
   * 
   * 作用说明：
   * - 将新的规则匹配添加到队列中
   * - 通过names集合检测并避免重复的规则匹配
   * - 根据规则类型将匹配添加到不同的队列（SubstitutionRule到preQueue，普通规则到queue）
   * - 在matchMap中建立RelSubset到规则匹配的映射，便于后续查找
   * 
   * 参数：match - 待添加的规则匹配对象
   */
  @Override public void addMatch(VolcanoRuleMatch match) {
    final String matchName = match.toString(); // 获取规则匹配的字符串表示，用于去重

    if (!matchList.names.add(matchName)) { // 尝试将匹配名称添加到names集合
      // 如果添加失败，说明该匹配已经存在，直接返回，避免重复处理
      // Identical match has already been added.
      return;
    }

    LOGGER.trace("Rule-match queued: {}", matchName); // 记录规则匹配已入队的日志

    matchList.offer(match); // 根据规则类型将匹配添加到相应的队列中

    matchList.matchMap.put( // 在matchMap中建立RelSubset到规则匹配的映射
        requireNonNull(planner.getSubset(match.rels[0])), match); // 获取第一个关系表达式对应的RelSubset，并将其与规则匹配关联
  }

  /**
   * 从规则匹配队列的头部移除并返回一个规则匹配
   * 
   * 作用说明：
   * - 从队列中取出下一个待处理的规则匹配
   * - 优先处理preQueue中的SubstitutionRule，然后处理queue中的普通规则
   * - 跳过无效的规则匹配（如属于过时集合或已被修剪的匹配）
   * - 从matchMap中移除该匹配，避免重复处理
   * - 在DEBUG和TRACE级别记录详细的日志信息
   * 
   * 返回值：下一个有效的规则匹配，如果没有更多匹配则返回null
   * 
   * 注意：即使返回了匹配，VolcanoPlanner仍可能拒绝该匹配（如果匹配已变得无效）
   */
  public @Nullable VolcanoRuleMatch popMatch() {
    dumpPlannerState(); // 如果启用TRACE日志，输出规划器的当前状态

    VolcanoRuleMatch match; // 声明规则匹配变量
    for (;;) { // 无限循环，直到找到有效的匹配或队列为空
      if (matchList.size() == 0) { // 检查队列是否为空
        return null; // 队列为空，返回null
      }

      dumpRuleQueue(matchList); // 如果启用TRACE日志，输出当前规则队列的内容

      match = matchList.poll(); // 从队列中取出一个规则匹配（优先从preQueue取）
      if (match == null) { // 检查取出的匹配是否为null
        return null; // 返回null
      }

      if (skipMatch(match)) { // 检查是否需要跳过该匹配（匹配是否无效）
        LOGGER.debug("Skip match: {}", match); // 记录跳过匹配的调试信息
      } else { // 匹配有效
        break; // 跳出循环，返回该匹配
      }
    }

    // 如果规则匹配入队后集合发生了合并，该匹配可能无法从matchMap中移除
    // 因为RelSubset可能已经改变，这是可以接受的，因为matchMap最终会被清空
    // If sets have merged since the rule match was enqueued, the match
    // may not be removed from the matchMap because the subset may have
    // changed, it is OK to leave it since the matchMap will be cleared
    // at the end.
    matchList.matchMap.remove( // 从matchMap中移除该规则匹配
        planner.getSubset(match.rels[0]), match); // 获取第一个关系表达式对应的RelSubset，并移除该匹配

    LOGGER.debug("Pop match: {}", match); // 记录弹出匹配的调试信息
    return match; // 返回有效的规则匹配
  }

  /**
   * 在调试级别设置为TRACE时，将规则队列内容输出到日志
   * 
   * 作用说明：
   * - 这是一个调试辅助方法，用于追踪规则队列的状态
   * - 只有在TRACE日志级别启用时才会执行
   * - 输出preQueue和queue中的所有规则匹配
   * - 帮助开发者理解和调试优化过程
   * 
   * 参数：matchList - 待输出的规则匹配列表
   */
  private static void dumpRuleQueue(MatchList matchList) {
    if (LOGGER.isTraceEnabled()) { // 检查是否启用了TRACE日志级别
      StringBuilder b = new StringBuilder(); // 创建字符串构建器
      b.append("Rule queue:"); // 添加队列标题
      for (VolcanoRuleMatch rule : matchList.preQueue) { // 遍历preQueue中的所有规则
        b.append("\n"); // 添加换行符
        b.append(rule); // 添加规则匹配的字符串表示
      }
      for (VolcanoRuleMatch rule : matchList.queue) { // 遍历queue中的所有规则
        b.append("\n"); // 添加换行符
        b.append(rule); // 添加规则匹配的字符串表示
      }
      LOGGER.trace(b.toString()); // 输出队列内容到TRACE日志
    }
  }

  /**
   * 在调试级别设置为TRACE时，将规划器的状态输出到日志
   * 
   * 作用说明：
   * - 这是一个调试辅助方法，用于追踪规划器的完整状态
   * - 只有在TRACE日志级别启用时才会执行
   * - 输出规划器的所有RelNode和RelSubset信息
   * - 使元数据查询缓存失效，确保后续查询使用最新信息
   * - 帮助开发者理解和调试优化过程中的状态变化
   */
  private void dumpPlannerState() {
    if (LOGGER.isTraceEnabled()) { // 检查是否启用了TRACE日志级别
      StringWriter sw = new StringWriter(); // 创建字符串写入器
      PrintWriter pw = new PrintWriter(sw); // 创建打印写入器
      planner.dump(pw); // 将规划器的当前状态转储到打印写入器
      pw.flush(); // 刷新缓冲区，确保所有内容都被写入
      LOGGER.trace(sw.toString()); // 输出规划器状态到TRACE日志
      RelNode root = planner.getRoot(); // 获取规划器的根节点
      if (root != null) { // 检查根节点是否存在
        root.getCluster().invalidateMetadataQuery(); // 使元数据查询缓存失效，确保后续查询使用最新信息
      }
    }
  }

  //~ Inner Classes ----------------------------------------------------------

  /**
   * MatchList内部类：表示一组规则匹配的集合
   * 
   * 作用说明：
   * - 管理所有待处理的规则匹配
   * - 使用两个队列分别存储不同优先级的规则匹配
   * - 提供快速去重和查找功能
   * - 维护RelSubset到规则匹配的多值映射
   * - 支持规则匹配的添加、移除和查询操作
   * 
   * 核心数据结构：
   * - preQueue：SubstitutionRule的优先队列（ArrayDeque）
   * - queue：普通规则队列（ArrayDeque）
   * - names：规则匹配名称集合，用于去重（HashSet）
   * - matchMap：RelSubset到规则匹配的多值映射（HashMultimap）
   */
  private static class MatchList {

    /**
     * SubstitutionRule的规则匹配队列（优先队列）
     * 
     * 作用说明：
     * - 存储所有SubstitutionRule类型的规则匹配
     * - SubstitutionRule具有更高的优先级，会先于普通规则被处理
     * - 使用ArrayDeque实现，提供高效的队列操作
     * - 在poll()方法中会优先从这个队列取出匹配
     */
    private final Queue<VolcanoRuleMatch> preQueue = new ArrayDeque<>();

    /**
     * 当前阶段的VolcanoRuleMatch列表（普通规则队列）
     * 
     * 作用说明：
     * - 存储所有非SubstitutionRule类型的规则匹配
     * - 新的规则匹配会被添加到队列的末尾
     * - 规则不按任何方式排序，按照添加顺序处理
     * - 使用ArrayDeque实现，提供高效的队列操作
     * - 只有在preQueue为空时才会从这个队列取出匹配
     */
    private final Queue<VolcanoRuleMatch> queue = new ArrayDeque<>();

    /**
     * 队列中包含的规则匹配名称集合
     * 
     * 作用说明：
     * - 存储所有已添加到队列的规则匹配的字符串表示
     * - 用于快速检测重复的规则匹配
     * - 在addMatch方法中，通过检查names集合来避免添加重复的匹配
     * - 使用HashSet实现，提供O(1)的查找复杂度
     */
    final Set<String> names = new HashSet<>();

    /**
     * RelSubset到VolcanoRuleMatch的多值映射
     * 
     * 作用说明：
     * - 建立RelSubset对象到规则匹配的多对多映射关系
     * - 一个RelSubset可以对应多个规则匹配
     * - 便于快速查找与特定RelSubset相关的所有规则匹配
     * - 在popMatch方法中，用于从映射中移除已处理的匹配
     * - 使用Guava的HashMultimap实现，支持一对多的映射关系
     */
    final Multimap<RelSubset, VolcanoRuleMatch> matchMap =
        HashMultimap.create(); // 创建HashMultimap实例

    /**
     * 获取规则匹配的总数
     * 
     * 作用说明：
     * - 返回preQueue和queue中所有规则匹配的总数
     * - 用于判断队列是否为空
     * - 在popMatch方法中用于检查是否有待处理的匹配
     * 
     * 返回值：两个队列中规则匹配的总和
     */
    int size() {
      return preQueue.size() + queue.size(); // 返回优先队列和普通队列的大小之和
    }

    /**
     * 从队列中取出一个规则匹配
     * 
     * 作用说明：
     * - 优先从preQueue中取出SubstitutionRule匹配
     * - 如果preQueue为空，则从queue中取出普通规则匹配
     * - 实现了优先级队列的逻辑，确保SubstitutionRule优先处理
     * 
     * 返回值：取出的规则匹配，如果两个队列都为空则返回null
     */
    @Nullable VolcanoRuleMatch poll() {
      VolcanoRuleMatch match = preQueue.poll(); // 优先从preQueue中取出匹配
      if (match == null) { // 如果preQueue为空
        match = queue.poll(); // 则从queue中取出匹配
      }
      return match; // 返回取出的匹配（可能为null）
    }

    /**
     * 将规则匹配添加到队列中
     * 
     * 作用说明：
     * - 根据规则类型将匹配添加到不同的队列
     * - SubstitutionRule类型的规则添加到preQueue
     * - 其他类型的规则添加到queue
     * - 实现了规则的分类管理
     * 
     * 参数：match - 待添加的规则匹配
     */
    void offer(VolcanoRuleMatch match) {
      if (match.getRule() instanceof SubstitutionRule) { // 检查规则是否是SubstitutionRule类型
        preQueue.offer(match); // 如果是，添加到优先队列preQueue
      } else { // 如果不是SubstitutionRule
        queue.offer(match); // 添加到普通队列queue
      }
    }

    /**
     * 清空所有队列和集合
     * 
     * 作用说明：
     * - 清空preQueue中的所有规则匹配
     * - 清空queue中的所有规则匹配
     * - 清空names集合中的所有名称
     * - 清空matchMap中的所有映射关系
     * - 在clear方法中被调用，用于重置队列状态
     */
    void clear() {
      preQueue.clear(); // 清空优先队列
      queue.clear(); // 清空普通队列
      names.clear(); // 清空名称集合
      matchMap.clear(); // 清空映射关系
    }
  }
}
