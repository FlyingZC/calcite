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
package org.apache.calcite.plan.hep; // 声明包名，该类属于 org.apache.calcite.plan.hep 包，这是 HepPlanner（启发式规划器）的核心包

/** Able to execute an instruction or program, and contains all mutable state
 * for that instruction. // 能够执行指令或程序，并包含该指令的所有可变状态
 *
 * <p>The goal is that programs are re-entrant - they can be used by more than
 * one thread at the same time. We achieve this by making instructions and programs
 * immutable. All mutable state is held in the state objects. // 目标是使程序可重入 - 它们可以被多个线程同时使用。我们通过使指令和程序不可变来实现这一点。所有可变状态都保存在状态对象中。
 *
 * <p>State objects are allocated, just before the program is executed, by
 * calling {@link HepInstruction#prepare(HepInstruction.PrepareContext)} on the
 * program and recursively on all of its instructions. */ // 状态对象在程序执行前立即分配，通过在程序上递归调用 {@link HepInstruction#prepare(HepInstruction.PrepareContext)} 及其所有指令来实现。
abstract class HepState { // 抽象类 HepState：表示 HepPlanner 中指令或程序的可变状态容器，用于支持多线程重入执行
  final HepPlanner planner; // 成员变量：HepPlanner 引用，指向执行该指令的启发式规划器实例，用于访问规划器的各种方法和状态
  final HepProgram.State programState; // 成员变量：HepProgram.State 引用，指向程序的状态对象，用于跟踪程序执行过程中的状态信息

  HepState(HepInstruction.PrepareContext px) { // 构造方法：接收一个准备上下文对象，用于初始化状态
    this.planner = px.planner; // 从准备上下文中获取 planner 引用并赋值，该规划器将执行与此状态关联的指令
    this.programState = px.programState; // 从准备上下文中获取 programState 引用并赋值，用于跟踪程序执行状态
  }

  /** Executes the instruction. */ // 执行指令，抽象方法由子类实现具体的执行逻辑
  abstract void execute(); // 抽象方法：执行与该状态关联的指令，具体实现由各个子类根据指令类型提供

  /** Re-initializes the state. (The state was initialized when it was created
   * via {@link HepInstruction#prepare}.) */ // 重新初始化状态（状态在通过 {@link HepInstruction#prepare} 创建时已被初始化）
  void init() { // 默认实现方法：重新初始化状态，子类可以覆盖此方法以提供特定的初始化逻辑
  } // 空实现，表示默认情况下不需要额外的初始化操作，子类可以根据需要覆盖
}
