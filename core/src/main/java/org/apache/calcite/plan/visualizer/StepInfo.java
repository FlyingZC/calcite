/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.plan.visualizer; // 定义包名，该类属于org.apache.calcite.plan.visualizer包，用于计划可视化功能

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于创建不可修改的列表
import com.google.common.collect.ImmutableMap; // 导入Google Guava库的不可变映射类，用于创建不可修改的映射

import java.util.List; // 导入Java标准库的List接口，用于表示有序列表
import java.util.Map; // 导入Java标准库的Map接口，用于表示键值对映射

/**
 * A step in the visualizer represents one rule call of the planner. // 可视化器中的一个步骤代表规划器的一次规则调用
 * 
 * StepInfo类用于记录和表示Calcite查询优化器在执行过程中的每一个步骤信息
 * 每个步骤对应优化器的一次规则匹配和应用操作，是可视化工具追踪优化过程的基本单元
 * 该类通过记录步骤ID、更新信息和匹配的关系节点，帮助开发者理解和调试查询优化过程
 * 
 * 主要功能：
 * 1. 记录每个优化步骤的唯一标识符，用于区分不同的优化步骤
 * 2. 保存该步骤对执行计划的更新信息，包括属性变化、成本估算等
 * 3. 记录该步骤匹配到的关系表达式节点列表，用于展示规则应用的范围
 * 
 * 使用场景：
 * - 在查询优化过程中，每当一个规则被匹配并应用时，创建一个StepInfo实例
 * - 可视化工具收集所有StepInfo实例，构建优化过程的完整时间线
 * - 开发者通过查看StepInfo信息，了解优化器如何逐步转换查询计划
 * - 调试优化问题时，可以通过StepInfo追踪规则应用的顺序和效果
 */
class StepInfo { // 定义StepInfo类，表示优化器可视化中的一个步骤
  private final String id; // 步骤的唯一标识符，用于在可视化中区分不同的优化步骤，通常是规则的名称或序列号
  private final Map<String, Object> updates; // 该步骤对执行计划的更新信息，键值对形式存储，可能包括成本、行数、属性等变化
  private final List<String> matchedRels; // 该步骤匹配到的关系表达式节点列表，每个字符串代表一个关系节点的描述或ID

  StepInfo(final String id, // 构造方法：创建一个新的StepInfo实例，参数id表示步骤的唯一标识符
      final Map<String, Object> updates, // 参数updates表示该步骤对执行计划的更新信息，以键值对形式存储
      final List<String> matchedRels) { // 参数matchedRels表示该步骤匹配到的关系表达式节点列表
    this.id = id; // 将传入的id参数赋值给成员变量id，初始化步骤的唯一标识符
    this.updates = ImmutableMap.copyOf(updates); // 使用Guava的ImmutableMap.copyOf创建updates的不可变副本，确保数据安全性
    this.matchedRels = ImmutableList.copyOf(matchedRels); // 使用Guava的ImmutableList.copyOf创建matchedRels的不可变副本，防止外部修改
  }

  public String getId() { // 获取步骤ID的方法，返回该步骤的唯一标识符
    return id; // 返回成员变量id的值，即步骤的唯一标识符字符串
  }

  public Map<String, Object> getUpdates() { // 获取更新信息的方法，返回该步骤对执行计划的更新信息
    return updates; // 返回成员变量updates的值，即包含更新信息的不可变Map
  }

  public List<String> getMatchedRels() { // 获取匹配关系节点的方法，返回该步骤匹配到的关系表达式节点列表
    return matchedRels; // 返回成员变量matchedRels的值，即包含匹配关系节点ID的不可变List
  }
}
