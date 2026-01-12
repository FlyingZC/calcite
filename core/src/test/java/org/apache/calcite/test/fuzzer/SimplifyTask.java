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
package org.apache.calcite.test.fuzzer; // 声明包名，表示该类属于 org.apache.calcite.test.fuzzer 包，这是 Calcite 模糊测试工具包的一部分

import org.apache.calcite.rex.RexNode; // 导入 RexNode 类，RexNode 是 Calcite 中表示行表达式的核心抽象类，用于表示 SQL 表达式的抽象语法树节点

/**
 * Tracks rex nodes used in {@link RexProgramFuzzyTest} to identify the ones
 * which take most time to simplify.
 */
// 类注释：SimplifyTask 类用于跟踪 RexProgramFuzzyTest 中使用的 rex 节点，以识别哪些节点在简化过程中花费的时间最长
// 该类实现了 Comparable 接口，使得 SimplifyTask 对象可以进行比较和排序，主要用于性能分析和优化
// 在模糊测试中，会生成大量的随机表达式并进行简化操作，通过跟踪每个简化的耗时，可以发现性能瓶颈
class SimplifyTask implements Comparable<SimplifyTask> { // 定义 SimplifyTask 类，实现 Comparable<SimplifyTask> 接口，支持对象间的比较
  public final RexNode node; // 成员变量：存储待简化的 RexNode 节点，即行表达式树的根节点，表示需要进行简化的原始表达式
  public final long seed; // 成员变量：存储生成该节点时使用的随机种子，用于重现测试场景和调试问题
  public final RexNode result; // 成员变量：存储简化后的 RexNode 节点，即经过简化操作后得到的优化后的表达式
  public final long duration; // 成员变量：存储简化操作所花费的时间（毫秒），用于性能分析和识别性能瓶颈

  SimplifyTask(RexNode node, long seed, RexNode result, long duration) { // 构造方法：创建一个 SimplifyTask 实例，初始化所有成员变量
    this.node = node; // 将传入的 node 参数赋值给成员变量 node，保存待简化的原始表达式节点
    this.seed = seed; // 将传入的 seed 参数赋值给成员变量 seed，保存生成该节点时使用的随机种子
    this.result = result; // 将传入的 result 参数赋值给成员变量 result，保存简化后的表达式节点
    this.duration = duration; // 将传入的 duration 参数赋值给成员变量 duration，保存简化操作的耗时
  } // 构造方法结束

  @Override public int compareTo(SimplifyTask o) { // 重写 Comparable 接口的 compareTo 方法，用于比较两个 SimplifyTask 对象的优先级
    if (duration != o.duration) { // 首先比较两个任务的耗时，如果耗时不同
      return Long.compare(duration, o.duration); // 则按耗时升序排序，耗时短的排在前面（性能好的在前）
    } // if 语句结束，耗时不同的情况处理完毕
    return Integer.compare(node.toString().length(), o.node.toString().length()); // 如果耗时相同，则按节点字符串长度升序排序，简单的表达式排在前面
  } // compareTo 方法结束
} // SimplifyTask 类结束
