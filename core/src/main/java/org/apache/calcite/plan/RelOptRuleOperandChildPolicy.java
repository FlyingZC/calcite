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
// Apache Calcite项目包名，plan包包含关系表达式优化规则相关的核心类
package org.apache.calcite.plan;

/**
 * Policy by which operands will be matched by relational expressions with
 * any number of children.
 * 操作数子节点匹配策略枚举类：定义了关系表达式操作数如何匹配任意数量子节点的策略
 * 
 * 这个枚举是Calcite优化器规则匹配系统的核心组件之一，它决定了规则操作数如何匹配关系表达式树的子节点
 * 在规则匹配过程中，优化器需要判断一个关系表达式是否符合规则的匹配条件，其中子节点的匹配方式由这个策略决定
 * 
 * 核心概念说明：
 * 1. RelOptRule（优化规则）：定义了如何转换关系表达式的规则，包含一个或多个操作数
 * 2. RelOptRuleOperand（规则操作数）：规则中的匹配条件，描述了要匹配的关系表达式类型及其子节点结构
 * 3. RelNode（关系表达式）：表示SQL查询计划树中的一个节点，如Filter、Project、Join等
 * 4. 子节点匹配：当规则操作数匹配到一个关系表达式后，需要进一步检查该表达式的子节点是否符合规则要求
 * 
 * 使用场景示例：
 * - 当定义一个转换规则时，需要指定操作数如何匹配关系表达式的子节点
 * - 例如：定义一个将Filter+Project转换为Project+Filter的规则，需要指定子节点匹配策略
 * - 不同的策略决定了规则的匹配范围和灵活性
 */
public enum RelOptRuleOperandChildPolicy {
  /**
   * Signifies that operand can have any number of children.
   * 表示操作数可以拥有任意数量的子节点，包括零个子节点
   * 
   * ANY策略的含义：
   * 1. 匹配时不关心操作数有多少个子节点，子节点数量可以是0、1、2或更多
   * 2. 不对子节点的顺序、类型或数量做任何限制
   * 3. 这是最宽松的匹配策略，提供最大的灵活性
   * 
   * 适用场景：
   * - 当规则需要匹配任意结构的操作数时使用
   * - 当规则的转换逻辑不依赖于子节点的具体结构时使用
   * - 当需要匹配可能具有不同子节点数量的操作数时使用
   * 
   * 示例：
   * - 匹配任意Join操作，不管它有多少个子节点（虽然Join通常只有2个子节点）
   * - 匹配可能包含或不包含子节点的操作数
   * - 在某些通用规则中，只关注操作数本身的类型，而不关心其子节点结构
   * 
   * 注意事项：
   * - 使用ANY策略时，规则可能匹配到不符合预期的表达式
   * - 在规则实现中需要额外检查子节点的实际结构
   * - 由于匹配范围广，可能导致规则被过度应用
   */
  ANY,

  /**
   * Signifies that operand has no children. Therefore it matches a
   * leaf node, such as a table scan or VALUES operator.
   * 表示操作数没有任何子节点，因此只能匹配叶子节点，如表扫描或VALUES操作符
   * 
   * LEAF策略的含义：
   * 1. 操作数必须是叶子节点，即没有子节点的关系表达式
   * 2. 叶子节点通常是数据源节点，如TableScan、Values等
   * 3. 这是最严格的匹配策略之一，确保匹配的是树的末端节点
   * 
   * 适用场景：
   * - 当规则只针对数据源节点时使用
   * - 当规则需要确保操作数没有任何子节点时使用
   * - 在定义从叶子节点开始的转换规则时使用
   * 
   * 典型的叶子节点类型：
   * - TableScan：表扫描操作，从表中读取数据
   * - Values：VALUES子句，直接提供常量数据
   * - Uncollect：将数组展开为行
   * - 其他没有子节点的数据源操作
   * 
   * <p>{@code RelOptRuleOperand(Foo.class, NONE)} is equivalent to
   * {@code RelOptRuleOperand(Foo.class)} but we prefer the former because
   * it is more explicit.
   * RelOptRuleOperand(Foo.class, NONE)等同于RelOptRuleOperand(Foo.class)，但我们更倾向于使用前者，因为它更明确
   * 
   * 这里的NONE是历史遗留的命名，现在应该使用LEAF代替
   * 使用显式的LEAF或NONE参数可以使代码意图更清晰，提高可读性
   * 
   * 注意事项：
   * - 使用LEAF策略时，如果操作数有子节点则匹配失败
   * - 适用于规则转换的起始点，即从数据源开始的转换
   * - 在规则链中，LEAF操作数通常是转换的起点或终点
   */
  LEAF,

  /**
   * Signifies that the operand's children must precisely match its
   * child operands, in order.
   * 表示操作数的子节点必须精确匹配其子操作数，并且顺序必须一致
   * 
   * SOME策略的含义：
   * 1. 操作数的子节点数量必须与规则中定义的子操作数数量完全相同
   * 2. 子节点的顺序必须与子操作数的定义顺序一致
   * 3. 每个子节点必须匹配对应的子操作数类型和约束
   * 4. 这是最常用的匹配策略，提供了精确的结构匹配
   * 
   * 适用场景：
   * - 当规则需要匹配特定结构的子树时使用
   * - 当子节点的顺序对转换逻辑很重要时使用
   * - 当需要确保操作数具有特定的子节点数量和类型时使用
   * 
   * 示例：
   * - 匹配Filter操作，且其子节点必须是Project操作
   * - 匹配Join操作，且其左右子节点必须分别是TableScan和Filter
   * - 匹配Project->Filter->TableScan这样的三层结构
   * 
   * 匹配过程：
   * 1. 首先检查操作数本身的类型是否匹配
   * 2. 然后检查子节点数量是否与子操作数数量一致
   * 3. 依次检查每个子节点是否匹配对应的子操作数
   * 4. 所有检查都通过则匹配成功
   * 
   * 注意事项：
   * - SOME策略要求精确匹配，灵活性较低但准确性高
   * - 子节点的顺序很重要，顺序不同会导致匹配失败
   * - 适用于结构明确的规则转换场景
   * - 如果子节点数量不匹配，即使类型匹配也会失败
   */
  SOME,

  /**
   * Signifies that the rule matches any one of its parents' children.
   * The parent may have one or more children.
   * 表示规则匹配其父节点的任意一个子节点，父节点可以有一个或多个子节点
   * 
   * UNORDERED策略的含义：
   * 1. 操作数可以匹配父节点的任意一个子节点，不限制具体是第几个
   * 2. 子节点的顺序不重要，只要匹配到任意一个即可
   * 3. 父节点可以有多个子节点，规则会尝试匹配每一个
   * 4. 相比SOME策略，UNORDERED提供了更大的灵活性
   * 
   * 适用场景：
   * - 当规则需要匹配父节点的任意子节点时使用
   * - 当子节点的位置不重要，只关心类型时使用
   * - 当父节点有多个可选的子节点时使用
   * - 当规则可以应用于父节点的任意子位置时使用
   * 
   * 示例：
   * - 匹配Join的任意一个子节点（左子节点或右子节点）
   * - 匹配Union的任意一个输入子节点
   * - 匹配Aggregate的任意子节点
   * - 在交换律规则中，匹配交换的任意一侧
   * 
   * 与SOME的区别：
   * - SOME要求精确匹配所有子节点，顺序固定
   * - UNORDERED只要求匹配任意一个子节点，顺序不重要
   * - SOME适用于固定结构，UNORDERED适用于灵活结构
   * 
   * 匹配过程：
   * 1. 确定父节点及其所有子节点
   * 2. 遍历父节点的每个子节点
   * 3. 检查是否有子节点匹配操作数
   * 4. 找到匹配项即返回成功
   * 
   * 注意事项：
   * - UNORDERED策略可能导致规则被多次应用（如果多个子节点都匹配）
   * - 在规则实现中可能需要进一步限制匹配条件
   * - 适用于需要灵活匹配的场景，但要注意避免过度匹配
   * - 与ANY不同，UNORDERED仍然要求匹配具体的子操作数类型
   */
  UNORDERED,
}
