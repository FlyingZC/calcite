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
package org.apache.calcite.rel; // 定义包名，该类位于org.apache.calcite.rel包下，属于Calcite的关系表达式相关模块

import org.apache.calcite.plan.RelMultipleTrait; // 导入RelMultipleTrait接口，表示可以有多种值的RelTrait特性
import org.apache.calcite.util.mapping.Mappings; // 导入Mappings工具类，用于处理字段映射关系

import java.util.List; // 导入List接口，用于存储分布键的列索引列表

/**
 * Description of the physical distribution of a relational expression.
 * 关系表达式物理分布的描述接口
 * 
 * <p>该接口定义了关系表达式在不同节点上的物理分布方式，是Calcite优化器中重要的trait（特性）之一。
 * 在分布式查询处理中，数据如何分布到不同的计算节点上对查询性能至关重要。
 * RelDistribution描述了数据在物理执行时的分布策略，例如：单节点、哈希分布、范围分布、广播等。
 * 
 * <p>作为RelMultipleTrait的子接口，RelDistribution可以有多个不同的值（不同于RelSingleTrait只能有一个值）。
 * 这意味着在优化过程中，同一个关系表达式可以有多种分布方式，优化器需要选择最优的分布策略。
 * 
 * <p>TBD (To Be Done - 待办事项):
 * <ul>
 *   <li>Can we shorten {@link Type#HASH_DISTRIBUTED} to HASH, etc.
 *       是否可以将HASH_DISTRIBUTED等类型名称缩短为HASH等，使代码更简洁
 *   </li>
 *   <li>Do we need {@link RelDistributions}.DEFAULT?
 *       是否需要RelDistributions.DEFAULT作为默认分布方式
 *   </li>
 *   <li>{@link RelDistributionTraitDef#convert}
 *       does not create specific physical operators as it does in Drill. Drill
 *       will need to create rules; or we could allow "converters" to be
 *       registered with the planner that are not trait-defs.
 *       RelDistributionTraitDef.convert方法不像Drill中那样创建特定的物理操作符。
 *       Drill需要创建规则；或者我们可以允许将"转换器"注册到规划器中，而不是作为trait-def。
 *   </li>
 * </ul>
 * 
 * <p>使用场景：
 * 1. 在分布式查询执行中，决定数据如何在多个节点间分布
 * 2. 优化器根据RelDistribution特性选择最优的物理执行计划
 * 3. 在Join、Aggregate等操作中，确保数据按照正确的方式分布
 * 4. 支持数据重分布（redistribution）操作，例如通过Exchange操作符改变数据分布
 * 
 * <p>相关类：
 * - RelDistributionTraitDef: RelDistribution的特性定义类，负责管理RelDistribution的转换和满足
 * - RelDistributions: 工厂类，提供创建各种RelDistribution实例的静态方法
 * - Exchange: 物理操作符，用于改变关系表达式的分布方式
 * - DistributionTraitDef: 旧版本的特性定义类（已被RelDistributionTraitDef替代）
 */
public interface RelDistribution extends RelMultipleTrait { // 定义RelDistribution接口，继承自RelMultipleTrait，表示这是一个可以有多种值的特性
  /** Returns the type of distribution.
   *  获取分布类型的枚举值
   *  @return Type 返回分布类型枚举，包括SINGLETON、HASH_DISTRIBUTED、RANGE_DISTRIBUTED等
   */
  Type getType(); // 声明获取分布类型的方法，返回Type枚举值

  /**
   * Returns the ordinals of the key columns.
   *  获取分布键列的索引列表（列的序号，从0开始）
   *
   * <p>分布键是指用于决定数据如何分布到不同节点的列。例如：
   * - HASH_DISTRIBUTED: 对分布键进行哈希计算，决定数据去哪个节点
   * - RANGE_DISTRIBUTED: 根据分布键的值范围进行分区
   * 
   * <p>Order is important for some types (RANGE); other types (HASH) consider
   * it unimportant but impose an arbitrary order; other types (BROADCAST,
   * SINGLETON) never have keys.
   *  对于某些分布类型（如RANGE），分布键的顺序非常重要；其他类型（如HASH）认为顺序不重要，
   *  但会强加一个任意的顺序；其他类型（BROADCAST、SINGLETON）从来没有分布键。
   *  
   * <p>示例：
   * - 对于HASH_DISTRIBUTED，返回[0, 1]表示使用第0列和第1列的组合作为哈希键
   * - 对于RANGE_DISTRIBUTED，返回[0]表示按第0列的值范围进行分区
   * - 对于BROADCAST和SINGLETON，返回空列表
   * 
   * @return List<Integer> 返回分布键列的索引列表，对于没有分布键的分布类型返回空列表
   */
  List<Integer> getKeys(); // 声明获取分布键列索引列表的方法，返回包含列索引的List

  /**
   * Applies mapping to this distribution trait.
   *  将映射应用到当前分布特性上，返回应用映射后的新分布特性
   *
   * <p>Mapping can change the distribution trait only if it depends on distribution keys.
   *  只有当分布依赖于分布键时，映射才能改变分布特性。
   * 
   * <p>该方法主要用于在关系表达式经过投影、重排等操作后，更新分布特性以反映列索引的变化。
   *  例如，经过一个投影操作，列的索引可能发生变化，相应的分布键索引也需要更新。
   * 
   * <p>For example if relation is HASH distributed by keys [0, 1], after applying
   * a mapping (3, 2, 1, 0), the relation will have a distribution HASH(2,3) because
   * distribution keys changed their ordinals.
   *  例如，如果关系表达式按键[0, 1]进行哈希分布，在应用映射(3, 2, 1, 0)后，
   *  该关系将具有分布HASH(2,3)，因为分布键的序号发生了变化。
   *  映射(3, 2, 1, 0)表示：原列0→新列3，原列1→新列2，原列2→新列1，原列3→新列0
   *  因此原分布键[0, 1]变为[2, 3]
   * 
   * <p>If mapping eliminates one of the distribution keys, the {@link Type#ANY}
   * distribution will be returned.
   *  如果映射消除了其中一个分布键，将返回{@link Type#ANY}分布。
   *  这是因为当分布键被移除后，原来的分布策略无法维持，只能接受任意分布。
   * 
   * <p>If distribution doesn't have keys (BROADCAST or SINGLETON), method will return
   * the same distribution.
   *  如果分布没有分布键（BROADCAST或SINGLETON），方法将返回相同的分布。
   *  因为这些分布方式不依赖于具体的列，所以映射不会影响它们。
   * 
   * @param mapping   Mapping 映射对象，描述列索引之间的对应关系
   * @return distribution with mapping applied 返回应用映射后的新分布特性
   */
  @Override RelDistribution apply(Mappings.TargetMapping mapping); // 重写RelMultipleTrait接口的apply方法，用于应用映射到分布特性

  /** Type of distribution.
   *  分布类型的枚举定义
   *  该枚举定义了Calcite支持的所有物理分布类型，每种类型描述了数据如何在多个计算节点间分布
   */
  enum Type { // 定义Type枚举，包含所有支持的分布类型
    /** There is only one instance of the stream. It sees all records.
     *  单例分布：只有一个流实例，它可以看到所有记录
     *  
     *  <p>这是最简单的分布方式，所有数据都在一个节点上处理。
     *  适用于小数据量或不需要并行处理的场景。
     *  
     *  <p>特点：
     *  - 所有数据集中在一个节点
     *  - 没有数据传输开销
     *  - 适合小数据集
     *  - 可能成为性能瓶颈
     */
    SINGLETON("single"), // 单例分布类型，简称为"single"

    /** There are multiple instances of the stream, and each instance contains
     * records whose keys hash to a particular hash value. Instances are
     * disjoint; a given record appears on exactly one stream.
     *  哈希分布：有多个流实例，每个实例包含其键哈希到特定哈希值的记录。
     *  实例之间是不相交的；给定的记录只出现在一个流上。
     *  
     *  <p>这是分布式数据库中最常用的分布方式。通过对分布键进行哈希计算，
     *  决定记录应该去哪个节点。相同哈希值的记录会被分配到同一个节点。
     *  
     *  <p>特点：
     *  - 数据均匀分布（假设分布键选择合理）
     *  - 相同键的记录在同一节点，便于聚合和Join
     *  - 容易产生数据倾斜（如果分布键选择不当）
     *  - 需要计算哈希值
     *  
     *  <p>适用场景：
     *  - Join操作（特别是等值Join）
     *  - Group By聚合操作
     *  - 需要快速查找特定键的场景
     */
    HASH_DISTRIBUTED("hash"), // 哈希分布类型，简称为"hash"

    /** There are multiple instances of the stream, and each instance contains
     * records whose keys fall into a particular range. Instances are
     * disjoint; a given record appears on exactly one stream.
     *  范围分布：有多个流实例，每个实例包含其键落在特定范围内的记录。
     *  实例之间是不相交的；给定的记录只出现在一个流上。
     *  
     *  <p>根据分布键的值范围将数据分配到不同节点。例如，可以按年龄范围、
     *  时间范围等进行分区。需要预先定义分区的边界值。
     *  
     *  <p>特点：
     *  - 保持数据的局部性（相邻的值在同一节点）
     *  - 适合范围查询（可以只查询相关节点）
     *  - 容易产生数据倾斜（如果数据分布不均）
     *  - 需要预先知道数据的分布情况
     *  
     *  <p>适用场景：
     *  - 时间序列数据（按日期范围分区）
     *  - 数值型数据（按数值范围分区）
     *  - 范围查询频繁的场景
     */
    RANGE_DISTRIBUTED("range"), // 范围分布类型，简称为"range"

    /** There are multiple instances of the stream, and each instance contains
     * randomly chosen records. Instances are disjoint; a given record appears
     * on exactly one stream.
     *  随机分布：有多个流实例，每个实例包含随机选择的记录。
     *  实例之间是不相交的；给定的记录只出现在一个流上。
     *  
     *  <p>通过随机方式将数据分配到不同节点，不依赖于任何键值。
     *  每条记录被随机分配到一个节点，期望结果是数据均匀分布。
     *  
     *  <p>特点：
     *  - 实现简单
     *  - 期望均匀分布（但不保证）
     *  - 不保持数据的局部性
     *  - 不支持基于键的优化操作
     *  
     *  <p>适用场景：
     *  - 不需要基于键的操作
     *  - 采样和统计计算
     *  - 对数据均匀性要求不高的场景
     */
    RANDOM_DISTRIBUTED("random"), // 随机分布类型，简称为"random"

    /** There are multiple instances of the stream, and records are assigned
     * to instances in turn. Instances are disjoint; a given record appears
     * on exactly one stream.
     *  轮询分布：有多个流实例，记录被轮流分配到实例中。
     *  实例之间是不相交的；给定的记录只出现在一个流上。
     *  
     *  <p>按照顺序将记录分配到不同节点，第一条记录到节点0，第二条到节点1，
     *  以此类推，循环往复。这是一种最简单的负载均衡方式。
     *  
     *  <p>特点：
     *  - 实现非常简单
     *  - 均匀分布（假设记录大小相近）
     *  - 不保持数据的局部性
     *  - 不支持基于键的优化操作
     *  
     *  <p>适用场景：
     *  - 不需要基于键的操作
     *  - 简单的负载均衡
     *  - 全表扫描等操作
     */
    ROUND_ROBIN_DISTRIBUTED("rr"), // 轮询分布类型，简称为"rr"（round robin）

    /** There are multiple instances of the stream, and all records appear in
     * each instance.
     *  广播分布：有多个流实例，所有记录都出现在每个实例中。
     *  
     *  <p>将完整的数据集复制到每个节点。每个节点都有完整的数据副本。
     *  这是一种数据冗余的分布方式。
     *  
     *  <p>特点：
     *  - 每个节点都有完整数据
     *  - 数据传输开销大（需要复制完整数据）
     *  - 存储开销大（多份数据副本）
     *  - 适合小表
     *  
     *  <p>适用场景：
     *  - 维表（小表）与大表的Join
     *  - 需要在每个节点进行相同计算的场景
     *  - 小数据量的全局操作
     */
    BROADCAST_DISTRIBUTED("broadcast"), // 广播分布类型，简称为"broadcast"

    /** Not a valid distribution, but indicates that a consumer will accept any
     * distribution.
     *  任意分布：不是有效的分布，但表示消费者将接受任何分布。
     *  
     *  <p>这是一种特殊的分布类型，不表示实际的物理分布，而是用作占位符，
     *  表示可以接受任何分布方式。在优化过程中，优化器可以选择最合适的分布方式。
     *  
     *  <p>特点：
     *  - 不表示实际的物理分布
     *  - 用作优化器的占位符
     *  - 表示对分布方式没有要求
     *  - 优化器可以自由选择最优分布
     *  
     *  <p>适用场景：
     *  - 优化器的初始状态
     *  - 不关心分布方式的操作
     *  - 作为转换的中间状态
     */
    ANY("any"); // 任意分布类型，简称为"any"

    public final String shortName; // 公共的final成员变量，存储分布类型的简短名称，用于序列化和反序列化

    Type(String shortName) { // Type枚举的构造函数，接收一个简短名称作为参数
      this.shortName = shortName; // 将传入的简短名称赋值给shortName成员变量
    }
  }
}
