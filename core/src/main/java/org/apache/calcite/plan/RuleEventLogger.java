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
package org.apache.calcite.plan; // 声明包名，该类属于 org.apache.calcite.plan 包，负责优化器相关功能

import org.apache.calcite.rel.RelNode; // 导入 RelNode 类，表示关系代数表达式节点，是 Calcite 中所有关系操作符的基类
import org.apache.calcite.util.trace.CalciteTrace; // 导入 CalciteTrace 工具类，用于获取 Calcite 框架的日志追踪器

import org.slf4j.Logger; // 导入 SLF4J 的 Logger 接口，用于日志记录
import org.slf4j.Marker; // 导入 SLF4J 的 Marker 接口，用于标记日志消息，便于日志过滤和分类
import org.slf4j.MarkerFactory; // 导入 SLF4J 的 MarkerFactory 工厂类，用于创建 Marker 实例

import java.util.Arrays; // 导入 Arrays 工具类，用于数组操作
import java.util.stream.Collectors; // 导入 Collectors 工具类，用于流式数据收集

/**
 * Listener for logging useful debugging information on certain rule events. // 这是一个监听器类，用于在特定规则事件发生时记录有用的调试信息
 * // 该类实现了 RelOptListener 接口，可以监听优化器规划过程中的各种事件
 * // 主要用于开发调试，帮助开发者理解优化器如何应用规则、转换关系表达式
 * // 该监听器会记录规则尝试、规则产生成功等关键事件的详细信息
 */
public class RuleEventLogger implements RelOptListener { // 定义 RuleEventLogger 类，实现 RelOptListener 接口，成为优化器事件监听器
  private static final Logger LOG = CalciteTrace.getPlannerTracer(); // 定义静态日志记录器，通过 CalciteTrace 获取规划器追踪器，用于记录优化器相关的调试信息
  private static final Marker FULL = MarkerFactory.getMarker("FULL_PLAN"); // 定义静态标记 FULL，用于标记显示完整计划树的日志消息，便于在日志中过滤出包含完整计划的信息
  @Override public void relEquivalenceFound(final RelEquivalenceEvent event) { // 重写 relEquivalenceFound 方法，当发现等价关系表达式时调用，用于监听等价关系发现事件

  } // 方法体为空，当前实现不处理等价关系发现事件

  @Override public void ruleAttempted(final RuleAttemptedEvent event) { // 重写 ruleAttempted 方法，当规则被尝试应用时调用，监听规则尝试事件
    if (event.isBefore() && LOG.isDebugEnabled()) { // 检查是否在规则应用之前（isBefore）以及日志级别是否为 DEBUG，避免不必要的日志记录
      RelOptRuleCall call = event.getRuleCall(); // 获取规则调用对象 RelOptRuleCall，包含了规则应用的所有上下文信息，如规则本身、输入关系表达式等
      String ruleArgs = Arrays.stream(call.rels) // 获取规则输入的关系表达式数组，转换为流式处理
          .map(rel -> "rel#" + rel.getId() + ":" + rel.getRelTypeName()) // 将每个关系表达式映射为字符串，格式为 "rel#ID:TypeName"，例如 "rel#12:LogicalFilter"，便于识别
          .collect(Collectors.joining(",")); // 将所有关系表达式字符串用逗号连接成一个字符串，例如 "rel#12:LogicalFilter,rel#13:LogicalScan"
      LOG.debug("call#{}: Apply rule [{}] to [{}]", call.id, call.getRule(), ruleArgs); // 记录 DEBUG 级别日志，输出规则调用信息，包括调用ID、规则名称和输入关系表达式列表，格式为 "call#ID: Apply rule [RuleName] to [rel#ID:TypeName,rel#ID:TypeName]"
    } // 结束条件判断，只有满足条件才记录日志
  } // 方法结束

  @Override public void ruleProductionSucceeded(RuleProductionEvent event) { // 重写 ruleProductionSucceeded 方法，当规则成功产生新的关系表达式时调用，监听规则产生成功事件
    if (event.isBefore() && LOG.isDebugEnabled()) { // 检查是否在规则产生之前（isBefore）以及日志级别是否为 DEBUG，避免不必要的日志记录
      RelOptRuleCall call = event.getRuleCall(); // 获取规则调用对象，包含规则应用的上下文信息

      Arrays.stream(call.rels).forEach(rel -> // 遍历规则输入的所有关系表达式，对每个关系表达式进行处理
          LOG.debug(FULL, "call#{}: Full plan for rule input [rel#{}:{}]: {}", call.id, rel.getId(), // 使用 FULL 标记记录 DEBUG 级别日志，输出规则输入的完整计划树，包括关系表达式ID、类型名和完整的计划树字符串表示
              rel.getRelTypeName(), System.lineSeparator() + RelOptUtil.toString(rel))); // 调用 RelOptUtil.toString() 将关系表达式转换为可读的字符串形式，前面添加换行符使输出更清晰

      RelNode newRel = event.getRel(); // 获取规则产生的新关系表达式，这是规则应用后生成的结果
      String description = // 定义字符串变量，用于描述新产生的关系表达式
          newRel == null ? "null" : "rel#" + newRel.getId() + ":" + newRel.getRelTypeName(); // 如果新关系表达式为 null，描述为 "null"；否则格式化为 "rel#ID:TypeName"
      LOG.debug("call#{}: Rule [{}] produced [{}]", call.id, call.getRule(), description); // 记录 DEBUG 级别日志，输出规则产生结果信息，包括调用ID、规则名称和产生的新关系表达式描述
      if (newRel != null) { // 检查新关系表达式是否不为 null，避免对 null 进行操作
        LOG.debug(FULL, "call#{}: Full plan for [{}]:{}", call.id, description, // 使用 FULL 标记记录 DEBUG 级别日志，输出新关系表达式的完整计划树
            System.lineSeparator() + RelOptUtil.toString(newRel)); // 调用 RelOptUtil.toString() 将新关系表达式转换为可读的字符串形式，前面添加换行符
      } // 结束条件判断
    } // 结束条件判断
  } // 方法结束

  @Override public void relDiscarded(final RelDiscardedEvent event) { // 重写 relDiscarded 方法，当关系表达式被丢弃时调用，监听关系表达式丢弃事件

  } // 方法体为空，当前实现不处理关系表达式丢弃事件

  @Override public void relChosen(final RelChosenEvent event) { // 重写 relChosen 方法，当关系表达式被选中作为最终计划的一部分时调用，监听关系表达式选择事件

  } // 方法体为空，当前实现不处理关系表达式选择事件
} // 类定义结束
