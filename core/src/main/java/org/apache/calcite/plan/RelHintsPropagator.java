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
// Apache许可证声明，这是一个开源软件，遵循Apache 2.0许可证协议
package org.apache.calcite.plan; // 声明包名为org.apache.calcite.plan，表示这个类属于Calcite查询优化器的计划包

import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是Calcite中关系代数表达式的基本接口，代表查询计划中的一个节点

/**
 * Customize the propagation of the {@link org.apache.calcite.rel.hint.RelHint}s
 * from the root relational expression of a rule call {@link RelOptRuleCall} to
 * the new equivalent expression.
 * 自定义提示（RelHint）的传播方式，提示是一种用于指导查询优化器行为的机制
 * 这个接口允许用户自定义如何将提示从规则调用的根关系表达式传播到新的等价表达式
 * 在Calcite中，提示（Hint）是一种用于影响查询优化器决策的机制，比如指定使用特定的索引、连接算法等
 * 当优化器应用规则（Rule）转换关系表达式时，可能需要将原始表达式上的提示传播到转换后的新表达式上
 * 这个接口提供了自定义传播逻辑的能力，而不是使用默认的传播行为
 *
 * @see RelOptUtil#propagateRelHints(RelNode, RelNode)
 * 参见RelOptUtil类中的propagateRelHints方法，该方法提供了默认的提示传播实现
 */
@FunctionalInterface // Java 8引入的函数式接口注解，表示这个接口只有一个抽象方法，可以用Lambda表达式实现
public interface RelHintsPropagator { // 定义一个公共接口RelHintsPropagator，用于自定义提示传播行为

  /**
   * Propagates the hints from a rule call's root relational expression {@code oriNode}
   * to the new equivalent relational expression {@code equiv}.
   * 将提示从一个规则调用的根关系表达式oriNode传播到新的等价关系表达式equiv
   * 这个方法是接口的核心方法，用于实现自定义的提示传播逻辑
   * 在查询优化过程中，当优化器应用某个规则将oriNode转换为equiv时
   * 需要决定是否将oriNode上的提示传播到equiv上，以及如何传播
   *
   * @param oriNode Root relational expression of a rule call
   * oriNode参数表示规则调用的根关系表达式，即应用优化规则之前的原始关系表达式节点
   * 这个节点可能包含一些提示（hints），这些提示需要在转换过程中被处理
   *
   * @param equiv   Equivalent expression
   * equiv参数表示等价的表达式，即应用优化规则后生成的新关系表达式节点
   * 这个节点是oriNode的等价转换版本，可能需要接收来自oriNode的提示
   *
   * @return New relational expression that would register into the planner
   * 返回一个新的关系表达式，这个表达式将被注册到查询优化器中
   * 返回的表达式可能是equiv本身，也可能是添加了提示后的新表达式
   * 具体的返回值取决于传播逻辑的实现
   */
  RelNode propagate(RelNode oriNode, RelNode equiv); // 定义抽象方法propagate，接收原始节点和等价节点，返回传播提示后的新节点
} // 接口定义结束
