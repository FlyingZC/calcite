/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache许可证2.0版本授权给您
 * (the "License"); you may not use this file except in compliance with  // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // 许可证网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS,  // 否则按"原样"分发，不提供任何明示或暗示的担保
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不承担任何形式的担保或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解语言特定的权限
 * limitations under the License.  // 以及使用限制
 */
package org.apache.calcite.plan;  // 声明此枚举类属于org.apache.calcite.plan包，这是Calcite查询规划器的核心包

/**
 * The mode of trait derivation.  // 特性派生模式的枚举定义，用于控制RelNode（关系表达式节点）如何从其子节点派生特性
 * 
 * 【类的作用详解】：
 * 这个枚举定义了Calcite查询优化器中RelNode从其子节点派生特性（Trait）的不同模式。
 * 特性（Trait）是Calcite中描述关系表达式属性的概念，例如：
 * - Convention：数据的物理存储格式（如EnumerableConvention表示可枚举的行集合）
 * - RelCollation：排序方式
 * - RelDistribution：数据分布方式
 * - RelPartitioning：分区方式
 * 
 * 在查询优化过程中，优化器需要决定如何从一个RelNode的子节点派生该节点的特性。
 * 不同的派生模式会影响优化器的搜索空间和性能。
 * 
 * 【为什么需要不同的派生模式】：
 * 1. LEFT_FIRST：大多数二元操作符（如Join、Union）的左子节点更重要，先考虑左子节点的特性
 * 2. RIGHT_FIRST：某些操作符（如索引嵌套循环连接）右子节点更重要
 * 3. BOTH：需要同时考虑左右子节点的特性，适用于不支持交换律的系统
 * 4. OMAKASE：完全自定义派生逻辑，给用户最大的灵活性
 * 5. PROHIBITED：禁止派生，用于某些不需要或不能派生特性的场景
 * 
 * 【使用场景】：
 * - 在RelNode的派生规则中指定使用哪种模式
 * - 控制优化器的搜索策略
 * - 优化特定操作符的执行计划
 */
public enum DeriveMode {  // 定义一个名为DeriveMode的枚举类型，表示特性派生的不同模式
  /**
   * Uses the left most child's traits to decide what  // 使用最左边子节点的特性来决定需要从其他子节点派生什么特性
   * traits to require from the other children. This  // 这种模式通常适用于大多数操作符
   * generally applies to most operators.
   * 
   * 【LEFT_FIRST模式详解】：
   * 这是默认和最常用的派生模式。当RelNode有多个子节点时，优先考虑最左边子节点的特性，
   * 然后根据左子节点的特性来要求其他子节点满足相应的特性。
   * 
   * 【工作原理】：
   * 1. 首先获取最左边子节点的特性（例如Convention）
   * 2. 以左子节点的特性为基础，要求其他子节点也满足相同的特性
   * 3. 如果其他子节点不满足，优化器会尝试转换这些子节点
   * 
   * 【适用场景】：
   * - 大多数二元操作符（Join、Union、Minus等）
   - 左子节点通常代表更大的数据集，优先考虑左子节点可以减少转换开销
   - 流水线处理中，左子节点的输出直接作为右子节点的输入
   * 
   * 【示例】：
   * 对于Join操作，如果左子节点是EnumerableConvention（可枚举的），
   * 则要求右子节点也转换为EnumerableConvention，这样可以高效地进行连接操作。
   * 
   * 【优点】：
   * - 搜索空间相对较小，优化速度快
   - 符合大多数场景的需求
   - 避免不必要的子节点转换
   */
  LEFT_FIRST,  // 枚举常量：左优先模式，先从左子节点派生特性

  /**
   * Uses the right most child's traits to decide what  // 使用最右边子节点的特性来决定需要从其他子节点派生什么特性
   * traits to require from the other children. Operators  // 像索引嵌套循环连接这样的操作符可能会发现这很有用
   * like index nested loop join may find this useful.
   * 
   * 【RIGHT_FIRST模式详解】：
   * 这种模式与LEFT_FIRST相反，优先考虑最右边子节点的特性。
   * 适用于右子节点更重要或更特殊的场景。
   * 
   * 【工作原理】：
   * 1. 首先获取最右边子节点的特性
   * 2. 以右子节点的特性为基础，要求其他子节点也满足相应的特性
   * 3. 优化器会尝试转换不满足要求的子节点
   * 
   * 【适用场景】：
   * - 索引嵌套循环连接（Index Nested Loop Join）：右子节点是索引表，需要保持其索引特性
   * - 右子节点是固定视图或物化视图，转换成本高
   * - 右子节点有特殊的物理属性（如已经排序、分区）
   * 
   * 【示例】：
   * 对于索引嵌套循环连接，右子节点可能是一个索引扫描，具有特定的物理特性。
   * 保持右子节点的特性可以避免破坏索引，提高查询性能。
   * 
   * 【优点】：
   * - 保护重要的右子节点特性
   * - 减少对特殊子节点的转换开销
   * - 适用于某些特定的优化场景
   */
  RIGHT_FIRST,  // 枚举常量：右优先模式，先从右子节点派生特性

  /**
   * Iterates over each child, uses current child's traits  // 遍历每个子节点，使用当前子节点的特性来决定需要从其他子节点派生什么特性
   * to decide what traits to require from the other  // 这种模式包含了LEFT_FIRST和RIGHT_FIRST两种情况
   * children. It includes both LEFT_FIRST and RIGHT_FIRST.
   * System that doesn't enable join commutativity should  // 不启用连接交换律的系统应该考虑这个选项
   * consider this option. Special customized operators  // 像有3个输入的Join这样的特殊自定义操作符也会发现这很有用
   * like a Join who has 3 inputs may find this useful too.
   * 
   * 【BOTH模式详解】：
   * 这是最全面的派生模式，会遍历所有子节点，对每个子节点都尝试派生特性。
   * 它实际上是LEFT_FIRST和RIGHT_FIRST的并集，适用于需要全面考虑的场景。
   * 
   * 【工作原理】：
   * 1. 遍历RelNode的所有子节点
   * 2. 对于每个子节点，以其特性为基础要求其他子节点满足相应特性
   * 3. 优化器会生成多种可能的派生组合
   * 4. 通过代价评估选择最优的执行计划
   * 
   * 【适用场景】：
   * - 不支持连接交换律的系统（即A JOIN B != B JOIN A）
   * - 多路连接（3个或更多输入的Join操作）
   * - 需要全面搜索优化空间的场景
   * - 子节点数量不确定或可变的操作符
   * 
   * 【示例】：
   * 对于三路连接（A JOIN B JOIN C），BOTH模式会考虑：
   * - 以A为基础派生B和C的特性
   * - 以B为基础派生A和C的特性
   * - 以C为基础派生A和B的特性
   * 这样可以找到最优的连接顺序和物理实现。
   * 
   * 【优点】：
   * - 搜索空间最全面，可能找到最优解
   * - 适用于复杂的优化场景
   * - 不依赖连接交换律等假设
   * 
   * 【缺点】：
   * - 搜索空间大，优化时间可能较长
   * - 可能生成大量相似的执行计划
   */
  BOTH,  // 枚举常量：双向模式，同时考虑所有子节点的特性派生

  /**
   * Leave it to you, you decide what you cook. This will  // 完全由你决定，你决定如何烹饪。这将允许规划器传递所有子节点的所有特性
   * allow planner to pass all the traits from all the  // 用户决定如何利用这些特性以及是否派生新的关系节点
   * children, the user decides how to make use of these
   * traits and whether to derive new rel nodes.
   * 
   * 【OMAKASE模式详解】：
   * "Omakase"是日语"お任せ"的音译，意为"交给您决定"或"主厨推荐"。
   * 这个模式给用户最大的灵活性，完全由用户自定义如何派生特性。
   * 
   * 【工作原理】：
   * 1. 优化器收集所有子节点的所有特性
   * 2. 将这些特性传递给用户的自定义派生逻辑
   * 3. 用户可以自由决定：
   *    - 使用哪些子节点的特性
   *    - 如何组合这些特性
   *    - 是否需要派生新的RelNode
   *    - 是否需要转换某些子节点
   * 4. 优化器根据用户的决策执行相应的操作
   * 
   * 【适用场景】：
   * - 需要完全自定义派生逻辑的特殊操作符
   * - 复杂的多阶段优化策略
   * - 需要基于业务规则的特性选择
   * - 实验性的优化算法
   * 
   * 【示例】：
   * 对于一个特殊的自定义Join操作符，用户可能希望：
   * - 根据数据分布特性选择不同的连接算法
   * - 根据内存可用性决定是否使用哈希连接
   * - 根据网络拓扑决定数据分布策略
   * 
   * 【优点】：
   * - 最大的灵活性和可定制性
   * - 可以实现复杂的优化策略
   * - 适用于特殊场景和实验性功能
   * 
   * 【缺点】：
   * - 需要用户深入理解Calcite的优化机制
   * - 实现复杂度高
   * - 容易出错，需要充分测试
   */
  OMAKASE,  // 枚举常量：自定义模式，完全由用户决定如何派生特性

  /**
   * Trait derivation is prohibited.  // 禁止特性派生
   * 
   * 【PROHIBITED模式详解】：
   * 这个模式明确禁止从子节点派生特性。RelNode将保持其当前特性，
   * 不会尝试从子节点派生新的特性。
   * 
   * 【工作原理】：
   * 1. 优化器遇到此模式时，跳过特性派生步骤
   * 2. RelNode保持其当前的特性不变
   * 3. 子节点的特性不会被用来影响父节点的特性
   * 
   * 【适用场景】：
   * - RelNode的特性已经固定，不需要或不能从子节点派生
   * - 某些特殊的物理操作符（如TableScan、Values等）
   * - 需要保持特定特性的中间节点
   * - 避免不必要的特性派生开销
   * 
   * 【示例】：
   * - TableScan：其特性由表本身决定，不能从子节点派生（因为它没有子节点）
   * - Values：生成固定数据的操作符，其特性是确定的
   * - 某些物化视图：需要保持特定的物理特性
   * 
   * 【优点】：
   * - 避免不必要的派生开销
   * - 保持特性的稳定性
   * - 适用于特性固定的操作符
   * 
   * 【使用注意事项】：
   * - 只有在确定不需要派生特性时才使用
   * - 过度使用可能导致优化空间受限
   * - 需要确保RelNode的特性是合理的
   */
  PROHIBITED  // 枚举常量：禁止模式，不进行特性派生
}
