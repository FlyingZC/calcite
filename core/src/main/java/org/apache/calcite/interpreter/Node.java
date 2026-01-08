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
package org.apache.calcite.interpreter; // 声明包名，该类位于org.apache.calcite.interpreter包中，这是Calcite的解释器执行引擎包

/**
 * Relational expression that can be executed using an interpreter.
 * 这是一个表示可以用解释器执行的关系表达式接口
 * 
 * 【接口作用详解】：
 * Node是Calcite解释器执行引擎的核心接口，它代表关系代数树中的一个可执行节点
 * 在Calcite中，关系表达式（RelNode）通常会被优化器转换为物理执行计划
 * 解释器执行模式是一种不生成代码、直接解释执行的方式，适用于：
 * 1. 快速原型验证和测试
 * 2. 不需要编译的场景
 * 3. 动态SQL执行
 * 4. 调试和开发阶段
 * 
 * 【设计模式】：
 * - 采用访问者模式的变体，每个Node代表一个操作符
 * - 继承AutoCloseable接口，支持资源自动释放
 * - run()方法是执行入口，类似于Runnable接口
 * 
 * 【与RelNode的关系】：
 * - RelNode是逻辑表示，描述"做什么"
 * - Node是物理执行表示，描述"怎么做"
 * - RelNode可以通过转换规则转换为对应的Node实现
 * 
 * 【执行流程】：
 * 1. 优化器生成RelNode树
 * 2. EnumerableInterpreter将RelNode树转换为Node树
 * 3. 调用根节点的run()方法开始执行
 * 4. 数据从叶子节点流向根节点（拉取模式）
 * 
 * 【典型实现类】：
 * - ScanNode: 表扫描节点
 * - FilterNode: 过滤节点
 * - ProjectNode: 投影节点
 * - JoinNode: 连接节点
 * - AggregateNode: 聚合节点
 * - SortNode: 排序节点
 * - ValuesNode: 常量值节点
 * - UnionNode: 集合合并节点
 */
public interface Node extends AutoCloseable { // 定义Node接口，继承AutoCloseable以支持资源自动释放
  void run() throws InterruptedException; // 核心执行方法，启动该节点的执行逻辑，可能抛出InterruptedException表示执行被中断

  @Override default void close() { // 重写AutoCloseable的close方法，使用default关键字提供默认实现
  } // 默认的close方法是空实现，子类可以根据需要重写以释放资源（如关闭游标、释放内存等）
}
