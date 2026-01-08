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
import org.apache.calcite.util.Pair;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * TopDownRuleQueue类：自顶向下的规则队列，用于管理级联优化器(cascades planner)中的规则匹配
 * 
 * 类作用：
 * 1. 维护规则匹配的队列结构，按照关系表达式(RelNode)组织规则匹配
 * 2. 实现自顶向下的规则应用策略，优先处理子节点的规则
 * 3. 区分普通规则和替换规则(substitution rule)，确保替换规则优先应用
 * 4. 提供规则的添加、弹出和清空等队列操作
 * 5. 避免重复添加相同的规则匹配
 * 
 * 工作原理：
 * - 使用Map结构，以RelNode为key，存储该RelNode对应的所有规则匹配
 * - 每个RelNode的规则匹配使用双端队列(Deque)存储，支持头部和尾部操作
 * - 普通规则添加到队列头部，替换规则添加到队列尾部
 * - 通过任务栈机制，替换规则虽然后添加但先执行
 */
class TopDownRuleQueue extends RuleQueue {

  /**
   * 成员变量matches：规则匹配映射表
   * 
   * 作用：
   * - 存储关系表达式(RelNode)到规则匹配队列的双向映射
   * - Key: RelNode - 关系表达式节点，表示规则匹配的目标对象
   * - Value: Deque<VolcanoRuleMatch> - 双端队列，存储该RelNode的所有规则匹配
   * 
   * 设计原因：
   * - 使用Map可以快速定位某个RelNode的所有规则匹配
   * 使用Deque支持头部和尾部的快速插入和删除操作
   * - HashMap提供O(1)的平均查找时间复杂度
   * - 支持多个RelNode同时存在各自的规则匹配队列
   */
  private final Map<RelNode, Deque<VolcanoRuleMatch>> matches = new HashMap<>();

  /**
   * 成员变量names：规则匹配名称集合
   * 
   * 作用：
   * - 记录所有已添加的规则匹配的唯一标识(toString()结果)
   * - 用于检测和避免重复添加相同的规则匹配
   * 
   * 设计原因：
   * - 使用Set集合提供O(1)的查找和插入性能
   * - 通过规则匹配的字符串表示作为唯一标识
   * - 防止同一个规则匹配被多次添加到队列中
   * - HashSet自动去重，提高效率
   */
  private final Set<String> names = new HashSet<>();

  /**
   * 构造方法：TopDownRuleQueue
   * 
   * 作用：
   * - 初始化TopDownRuleQueue实例
   * - 调用父类RuleQueue的构造方法，传入VolcanoPlanner引用
   * 
   * 参数：
   * - planner: VolcanoPlanner实例，火山优化器，用于访问优化器相关功能
   * 
   * 设计说明：
   * - 必须传入VolcanoPlanner实例，因为需要调用planner的方法(如isSubstituteRule)
   * - 继承RuleQueue的所有功能，在此基础上实现自顶向下的队列管理
   */
  TopDownRuleQueue(VolcanoPlanner planner) {
    super(planner);
  }

  /**
   * 方法：addMatch - 添加规则匹配到队列
   * 
   * 作用：
   * - 将一个新的规则匹配添加到对应的RelNode队列中
   * - 自动为RelNode创建队列(如果不存在)
   * - 避免重复添加相同的规则匹配
   * 
   * 参数：
   * - match: VolcanoRuleMatch - 要添加的规则匹配对象，包含规则和匹配的RelNode信息
   * 
   * 实现逻辑：
   * 1. 从match中获取第一个RelNode作为key
   * 2. 使用computeIfAbsent方法，如果该RelNode不存在队列则创建新的ArrayDeque
   * 3. 调用重载的addMatch方法将match添加到队列
   * 
   * 设计要点：
   * - 使用match.rel(0)获取规则匹配的目标RelNode
   * - computeIfAbsent是线程安全的懒加载模式
   * - ArrayDeque作为双端队列实现，性能优于LinkedList
   */
  @Override public void addMatch(VolcanoRuleMatch match) {
    RelNode rel = match.rel(0); // 获取规则匹配的第一个关系表达式作为key
    Deque<VolcanoRuleMatch> queue = matches.
        computeIfAbsent(rel, id -> new ArrayDeque<>()); // 如果rel不存在则创建新的双端队列
    addMatch(match, queue); // 调用重载方法将match添加到队列
  }

  /**
   * 方法：addMatch - 私有重载方法，将规则匹配添加到指定队列
   * 
   * 作用：
   * - 将规则匹配添加到指定的双端队列中
   * - 检查并避免重复添加
   * - 根据规则类型决定添加位置(头部或尾部)
   * 
   * 参数：
   * - match: VolcanoRuleMatch - 要添加的规则匹配对象
   * - queue: Deque<VolcanoRuleMatch> - 目标双端队列
   * 
   * 实现逻辑：
   * 1. 尝试将match的toString()添加到names集合
   * 2. 如果添加失败(已存在)，说明重复，直接返回
   * 3. 判断是否为替换规则(substitution rule)
   * 4. 非替换规则添加到队列头部，替换规则添加到队列尾部
   * 
   * 关键设计：
   * - 使用names集合的add方法返回值判断是否重复
   * - 替换规则虽然后添加到队列尾部，但通过任务栈机制先执行
   * - 这种设计确保替换规则优先于普通规则应用
   * 
   * 执行顺序说明：
   * 虽然替换规则添加到队列尾部，但实际执行顺序是：
   * 1. 从队列头部开始遍历规则
   * 2. 为每个规则创建ApplyRule任务
   * 3. 将任务压入栈中
   * 4. 从栈中弹出任务执行(后进先出)
   * 5. 因此后添加的替换规则对应的任务先执行
   */
  private void addMatch(VolcanoRuleMatch match, Deque<VolcanoRuleMatch> queue) {
    if (!names.add(match.toString())) { // 尝试添加到names集合，如果已存在则返回false
      return; // 规则匹配已存在，避免重复添加，直接返回
    }

    // 替换规则(substitution rule)会优先应用，尽管它被添加到队列末尾
    // 处理流程如下：
    //   1) 将非替换规则放在队列头部，替换规则放在队列尾部
    //   2) 按从前往后的顺序从队列中取出每个规则，生成ApplyRule任务
    //   3) 将每个ApplyRule任务压入任务栈
    // 结果：替换规则先执行，因为ApplyRule(substitution)任务比ApplyRule(non-substitution)任务更早从栈中弹出
    if (!planner.isSubstituteRule(match)) { // 判断是否为替换规则
      queue.addFirst(match); // 非替换规则添加到队列头部
    } else {
      queue.addLast(match); // 替换规则添加到队列尾部
    }
  }

  /**
   * 方法：popMatch - 从队列中弹出符合条件的规则匹配
   * 
   * 作用：
   * - 从指定RelNode的队列中查找并移除第一个符合条件的规则匹配
   * - 支持通过谓词(Predicate)过滤规则匹配
   * - 跳过应该被忽略的规则匹配
   * 
   * 参数：
   * - category: Pair<RelNode, Predicate<VolcanoRuleMatch>> - 分类条件
   *   - left: RelNode - 目标关系表达式，用于定位对应的规则匹配队列
   *   - right: Predicate<VolcanoRuleMatch> - 可选的谓词，用于过滤规则匹配
   * 
   * 返回值：
   * - @Nullable VolcanoRuleMatch - 找到的规则匹配，如果没有找到则返回null
   * 
   * 实现逻辑：
   * 1. 根据RelNode获取对应的规则匹配队列
   * 2. 如果队列不存在，返回null
   * 3. 遍历队列中的每个规则匹配
   * 4. 如果提供了谓词且谓词测试失败，跳过当前规则
   * 5. 从队列中移除当前规则
   * 6. 检查是否应该跳过该规则，如果跳过则继续查找
   * 7. 返回第一个符合条件的规则匹配
   * 
   * 设计要点：
   * - 使用迭代器安全地遍历和删除元素
   * - 支持灵活的过滤条件
   * - 调用skipMatch方法检查是否应该跳过
   * - 返回第一个符合条件的规则匹配，实现先进先出
   */
  public @Nullable VolcanoRuleMatch popMatch(Pair<RelNode, Predicate<VolcanoRuleMatch>> category) {
    Deque<VolcanoRuleMatch> queue = matches.get(category.left); // 根据RelNode获取对应的规则匹配队列
    if (queue == null) { // 如果队列不存在
      return null; // 返回null，表示没有找到规则匹配
    }
    Iterator<VolcanoRuleMatch> iterator = queue.iterator(); // 获取队列的迭代器
    while (iterator.hasNext()) { // 遍历队列中的每个规则匹配
      VolcanoRuleMatch next = iterator.next(); // 获取下一个规则匹配
      if (category.right != null && !category.right.test(next)) { // 如果提供了谓词且谓词测试失败
        continue; // 跳过当前规则匹配，继续查找下一个
      }
      iterator.remove(); // 从队列中移除当前规则匹配
      if (!skipMatch(next)) { // 检查是否应该跳过该规则匹配
        return next; // 不需要跳过，返回该规则匹配
      }
    }
    return null; // 遍历完队列仍未找到符合条件的规则匹配，返回null
  }

  /**
   * 方法：clear - 清空规则队列
   * 
   * 作用：
   * - 清空所有规则匹配队列
   * - 清空规则匹配名称集合
   * - 返回清空前队列是否非空
   * 
   * 返回值：
   * - boolean - 如果清空前队列非空返回true，否则返回false
   * 
   * 实现逻辑：
   * 1. 记录清空前队列是否为空
   * 2. 清空matches映射表
   * 3. 清空names集合
   * 4. 返回清空前队列是否非空
   * 
   * 设计要点：
   * - 使用isEmpty()检查队列状态，避免修改后再检查
   * - 清空操作包括matches和names两个集合
   * - 返回值可以用于判断是否进行了清理操作
   * - 覆盖父类RuleQueue的clear方法，实现特定清理逻辑
   */
  @Override public boolean clear() {
    boolean empty = matches.isEmpty(); // 记录清空前队列是否为空
    matches.clear(); // 清空所有规则匹配队列
    names.clear(); // 清空所有规则匹配名称
    return !empty; // 返回清空前队列是否非空(非空返回true)
  }
}
