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
 */ // Apache 开源许可证声明，指定代码的使用权限和限制
package org.apache.calcite.interpreter; // 定义包名，该类属于 org.apache.calcite.interpreter 包，这是 Calcite 解释器模块的核心包

import org.apache.calcite.rel.core.Window; // 导入 Window 类，这是 Calcite 中表示窗口操作的关系代数节点，用于实现窗口函数（如 ROW_NUMBER、RANK、SUM OVER 等）

/**
 * Interpreter node that implements a
 * {@link org.apache.calcite.rel.core.Window}.
 */ // 类注释：这是一个解释器节点，用于实现 Window 关系操作。在 Calcite 中，解释器模式是执行查询计划的一种方式，WindowNode 负责在解释器模式下执行窗口函数操作
public class WindowNode extends AbstractSingleNode<Window> { // 定义 WindowNode 类，继承自 AbstractSingleNode<Window>，表示这是一个处理单个子节点的解释器节点，泛型参数 Window 指定处理的 RelNode 类型
  WindowNode(Compiler compiler, Window rel) { // 构造方法：接收一个 Compiler 编译器对象和一个 Window 关系节点对象，用于创建 WindowNode 实例
    super(compiler, rel); // 调用父类 AbstractSingleNode 的构造方法，初始化编译器和关系节点，父类会处理 source（数据源）和 sink（数据接收器）的设置
  } // 构造方法结束

  @Override public void run() throws InterruptedException { // 重写 run 方法，这是解释器节点的核心执行方法，负责执行窗口操作，可能抛出 InterruptedException 表示可以被中断
    Row row; // 声明一个 Row 变量，用于暂存从数据源接收到的每一行数据，Row 是 Calcite 中表示数据行的抽象
    while ((row = source.receive()) != null) { // 循环从 source（数据源，即输入的子节点）接收数据行，直到返回 null 表示数据结束，这是典型的数据流处理模式
      sink.send(row); // 将接收到的行直接发送到 sink（数据接收器，即输出到下一个节点），这里看起来是透传，但实际上窗口函数的计算可能在编译阶段已经完成，或者由其他机制处理
    } // while 循环结束，表示所有数据行都已处理完毕
    sink.end(); // 通知 sink 数据流结束，不再有新的数据行，这是数据流协议的重要部分，确保下游节点知道数据已经全部发送完毕
  } // run 方法结束
} // WindowNode 类定义结束
