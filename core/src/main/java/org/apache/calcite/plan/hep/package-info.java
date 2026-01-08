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

/**
 * Provides a heuristic planner implementation for the interfaces in
 * {@link org.apache.calcite.plan}.
 * // 为 org.apache.calcite.plan 包中的接口提供启发式规划器实现
 * // 
 * // 【包作用说明】
 * // HEP (Heuristic Planner) 是 Calcite 中的启发式优化器实现，与基于成本的优化器 (CBO) 不同
 * // 
 * // 【核心概念】
 * // 1. 启发式规划器：基于预定义的规则和启发式方法进行查询优化，不依赖统计信息和成本估算
 * // 2. 规则驱动：通过应用一系列转换规则来优化关系表达式
 * // 3. 迭代优化：可以多次应用规则直到达到固定点或满足特定条件
 * // 4. 灵活性：允许用户自定义规则和优化策略
 * // 
 * // 【主要组件】
 * // - HepPlanner: 启发式规划器的主类，负责管理和应用优化规则
 * // - HepProgram: 定义优化程序的序列，控制规则的执行顺序和方式
 * // - HepRelVertex: 关系表达式的顶点表示，用于构建优化图
 * // - HepRuleCall: 规则调用的上下文，包含规则应用所需的信息
 * // - HepInstruction: 优化指令的抽象，包括规则添加、程序执行等
 * // 
 * // 【与 VolcanoPlanner 的区别】
 * // - VolcanoPlanner: 基于成本的优化器 (CBO)，使用动态规划和成本估算
 * // - HepPlanner: 启发式优化器，基于规则匹配和转换，不考虑成本
 * // 
 * // 【使用场景】
 * // 1. 快速原型开发：不需要统计信息即可测试优化规则
 * // 2. 特定优化：针对特定模式的优化，如子查询解嵌套、视图替换等
 * // 3. 规则测试：开发和测试新的优化规则
 * // 4. 预优化：在成本优化之前进行初步优化
 * // 
 * // 【优化流程】
 * // 1. 初始化：创建 HepPlanner 实例并添加规则
 * // 2. 图构建：将关系表达式转换为 HepRelVertex 图结构
 * // 3. 规则匹配：遍历图，查找匹配规则的顶点
 * // 4. 规则应用：对匹配的顶点应用规则转换
 * // 5. 迭代优化：重复匹配和应用直到满足终止条件
 * // 6. 结果生成：返回优化后的关系表达式
 * // 
 * // 【规则匹配策略】
 * // - DEPTH_FIRST: 深度优先匹配
 * // - ARBITRARY: 任意顺序匹配
 * // - TOP_DOWN: 自顶向下匹配
 * // - BOTTOM_UP: 自底向上匹配
 * // 
 * // 【关键特性】
 * // - 支持规则分组和优先级
 * // - 支持规则执行顺序控制
 * // - 支持部分优化和增量优化
 * // - 支持规则冲突检测和解决
 * // - 支持多阶段优化程序
 * // 
 * // 【性能考虑】
 * // - 启发式规划器通常比基于成本的规划器更快
 * // - 但可能产生次优的执行计划
 * // - 适合快速开发和测试场景
 * // - 生产环境通常使用基于成本的规划器
 * // 
 * // 【示例用法】
 * // HepProgramBuilder programBuilder = HepProgram.builder();
 * // programBuilder.addRuleInstance(MyRule.class);
 * // HepPlanner planner = new HepPlanner(programBuilder.build());
 * // RelNode optimized = planner.optimize(originalRel);
 * // 
 * // 【相关包】
 * // - org.apache.calcite.plan: 规划器接口和基础类
 * // - org.apache.calcite.rel: 关系表达式接口和实现
 * // - org.apache.calcite.rel.rules: 优化规则集合
 * // - org.apache.calcite.tools: 规划器工具和构建器
 */
package org.apache.calcite.plan.hep;
