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
// Apache 许可证头部声明，说明此代码遵循 Apache 2.0 许可证
package org.apache.calcite.interpreter;  // 定义包名，表示此类属于 org.apache.calcite.interpreter 包，用于解释器相关功能

import org.apache.calcite.linq4j.Enumerable;  // 导入 Enumerable 类，用于支持 LINQ 风格的枚举操作，提供可枚举的数据集合功能

/**
 * Sink to which to send rows.  // 接口文档注释：这是一个用于接收行数据的接口（Sink 意为"接收器"或"汇"）
 *                              // 在解释器执行模式中，每个关系算子的输出都会通过 Sink 接口传递给下游算子
 *                              // 这是 Calcite 解释器模式中数据流转的核心接口之一
 *
 * <p>Corresponds to an output of a relational expression.  // 对应于关系表达式的输出端
 *                                                      // 在关系代数执行树中，每个算子都需要将结果输出到某个 Sink
 *                                                      // Sink 可以是另一个算子的输入，也可以是最终的结果收集器
 *                                                      // 这种设计使得解释器可以灵活地组合不同的算子形成执行计划
 */
public interface Sink {  // 定义一个公共接口 Sink，用于接收和处理行数据
  void send(Row row) throws InterruptedException;  // 发送一行数据到接收器，参数 row 表示要发送的行对象
                                                 // 此方法由上游算子调用，将处理后的行数据传递给下游
                                                 // 抛出 InterruptedException 表示发送过程可能被中断
                                                 // 在解释器执行过程中，所有数据流都通过此方法传递

  void end() throws InterruptedException;  // 通知接收器数据发送结束，表示没有更多数据需要发送
                                         // 当上游算子完成所有数据处理后，会调用此方法通知 Sink
                                         // Sink 可以根据此信号进行资源清理或触发后续操作
                                         // 抛出 InterruptedException 表示结束过程可能被中断

  /** This method is temporary. It will be removed without notice. */  // 方法文档注释：此方法是临时的，将在不通知的情况下被移除
  @Deprecated  // 标记此方法已过时，不建议继续使用，未来版本可能会删除
  void setSourceEnumerable(Enumerable<Row> enumerable) throws InterruptedException;  // 设置源数据枚举器，参数 enumerable 是包含行数据的可枚举集合
                                                                                   // 此方法提供了一种替代 send() 的数据传递方式
                                                                                   // 通过设置 Enumerable，Sink 可以直接从源枚举器中获取数据
                                                                                   // 抛出 InterruptedException 表示设置过程可能被中断
                                                                                   // 此方法已被标记为过时，可能用于某些特殊的优化场景
}  // 接口定义结束
