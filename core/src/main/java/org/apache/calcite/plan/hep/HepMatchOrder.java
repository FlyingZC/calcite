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
package org.apache.calcite.plan.hep; // 声明包名，该类属于org.apache.calcite.plan.hep包，hep代表HepPlanner（基于启发式的规划器）

/**
 * HepMatchOrder specifies the order of graph traversal when looking for rule
 * matches.
 * HepMatchOrder枚举类指定了在查找规则匹配时图遍历的顺序
 * 
 * 【类的作用】：
 * 这个枚举类定义了HepPlanner（基于启发式的优化器）在应用优化规则时遍历关系代数表达式树的不同策略
 * 关系代数表达式树是一个有向无环图（DAG），其中节点代表关系操作（如Filter、Project、Join等），边代表数据流
 * 不同的遍历顺序会影响规则应用的顺序和效率，进而影响最终优化结果的质量和性能
 * 
 * 【核心概念】：
 * 1. 关系代数表达式树：由RelNode节点组成的树状结构，表示SQL查询的执行计划
 * 2. 规则匹配：优化器尝试将预定义的转换规则应用到表达式树的某些子树上
 * 3. 遍历顺序：决定了优化器按照什么顺序访问表达式树中的节点来寻找规则匹配的机会
 * 
 * 【使用场景】：
 * 在配置HepPlanner时，可以通过设置matchOrder参数来选择合适的遍历策略
 * 不同的策略适用于不同的优化场景，例如：
 * - 自底向上适合从叶子节点（数据源）开始逐步优化
 * - 自顶向下适合从根节点（最终输出）开始逐步优化
 * - 深度优先适合处理具有大量分支的复杂表达式
 */
public enum HepMatchOrder { // 定义一个公共枚举类HepMatchOrder，包含四个枚举常量，代表四种不同的图遍历顺序策略

  /**
   * Match in arbitrary order. This is the default because it is
   * efficient, and most rules don't care about order.
   * 以任意顺序进行匹配。这是默认选项，因为它效率高，且大多数规则不在乎顺序
   *
   * 【详细解释】：
   * ARBITRARY是默认的匹配顺序，优化器会按照内部数据结构的顺序遍历节点
   * 这个顺序可能是基于节点添加到图中的顺序，也可能是其他内部实现细节
   * 
   * 【优点】：
   * 1. 效率最高：不需要维护额外的遍历状态，直接按内部存储顺序访问
   * 2. 实现简单：不需要复杂的遍历算法
   * 3. 适用于大多数情况：很多优化规则的应用顺序对最终结果影响不大
   * 
   * 【缺点】：
   * 1. 可能导致重复应用规则：在某些情况下，同一个节点可能被多次检查
   * 2. 不可预测：匹配顺序取决于内部实现，难以预测和调试
   * 3. 可能不是最优顺序：对于某些特定的优化场景，其他顺序可能更高效
   * 
   * 【适用场景】：
   * - 规则之间没有依赖关系，应用顺序不影响结果
   * - 表达式树结构相对简单，分支较少
   * - 追求最快的优化速度，对遍历顺序不敏感
   * 
   * 【示例】：
   * 对于表达式：Filter(Project(Scan))，ARBITRARY可能会先检查Filter，也可能先检查Project
   */
  ARBITRARY, // 枚举常量ARBITRARY，表示任意顺序匹配，这是默认值

  /**
   * Match from leaves up. A match attempt at a descendant precedes all match
   * attempts at its ancestors.
   * 从叶子节点向上匹配。对后代节点的匹配尝试总是先于对其祖先节点的所有匹配尝试
   *
   * 【详细解释】：
   * BOTTOM_UP采用自底向上的遍历策略，即从叶子节点（通常是表扫描、常量值等数据源节点）开始
   * 逐层向上遍历到根节点（最终输出节点）
   * 在关系代数树中，叶子节点是数据源，内部节点是操作符，根节点是最终结果
   * 
   * 【遍历过程】：
   * 1. 首先访问所有叶子节点（如TableScan）
   * 2. 然后访问叶子节点的父节点（如Filter、Project）
   * 3. 继续向上访问，直到到达根节点
   * 4. 确保在处理父节点之前，其所有子节点都已被处理
   * 
   * 【优点】：
   * 1. 符合数据流方向：数据从叶子流向根，优化顺序与数据流方向一致
   * 2. 局部优化效果好：先优化子树，再优化父节点，可以充分利用子树优化结果
   * 3. 避免重复计算：子节点优化后，父节点基于优化后的子树进行优化
   * 4. 适合谓词下推等优化：从叶子开始可以更好地将过滤条件推向数据源
   * 
   * 【缺点】：
   * 1. 可能错过某些全局优化机会：局部最优不一定是全局最优
   * 2. 需要维护遍历状态：需要记录哪些节点已被访问
   * 3. 效率略低于ARBITRARY：需要额外的遍历逻辑
   * 
   * 【适用场景】：
   * - 需要从数据源开始逐步优化的场景
   * - 谓词下推、投影下推等从叶子开始的优化
   * - 子树优化对父节点影响较大的情况
   * 
   * 【示例】：
   * 对于表达式：Join(Filter(Scan1), Filter(Scan2))
   * BOTTOM_UP顺序：Scan1 -> Filter1 -> Scan2 -> Filter2 -> Join
   * 这样可以先优化每个Scan上的Filter，再优化Join
   */
  BOTTOM_UP, // 枚举常量BOTTOM_UP，表示自底向上匹配，从叶子节点向根节点遍历

  /**
   * Match from root down. A match attempt at an ancestor always precedes all
   * match attempts at its descendants.
   * 从根节点向下匹配。对祖先节点的匹配尝试总是先于对其后代节点的所有匹配尝试
   *
   * 【详细解释】：
   * TOP_DOWN采用自顶向下的遍历策略，即从根节点（最终输出节点）开始
   * 逐层向下遍历到叶子节点（数据源节点）
   * 这种顺序与BOTTOM_UP相反，是从最终结果向数据源方向遍历
   * 
   * 【遍历过程】：
   * 1. 首先访问根节点
   * 2. 然后访问根节点的直接子节点
   * 3. 继续向下访问，直到到达所有叶子节点
   * 4. 确保在处理子节点之前，其父节点都已被处理
   * 
   * 【优点】：
   * 1. 符合查询意图：从最终输出开始，更容易理解查询的整体结构
   * 2. 适合全局优化：可以先考虑整体结构，再优化细节
   * 3. 适合某些特定优化：如视图合并、子查询展开等从根开始的优化
   * 4. 可以提前终止：如果根节点已经是最优的，可能不需要遍历整个树
   * 
   * 【缺点】：
   * 1. 可能错过局部优化机会：某些子树优化可能被忽略
   * 2. 可能需要多次遍历：子节点优化后可能需要重新考虑父节点
   * 3. 效率略低于ARBITRARY：需要额外的遍历逻辑
   * 
   * 【适用场景】：
   * - 需要从整体结构开始优化的场景
   * - 视图合并、子查询展开等从根开始的优化
   * - 需要考虑查询整体结构的优化
   * 
   * 【示例】：
   * 对于表达式：Join(Filter(Scan1), Filter(Scan2))
   * TOP_DOWN顺序：Join -> Filter1 -> Scan1 -> Filter2 -> Scan2
   * 这样可以先考虑Join的优化，再优化其子节点
   */
  TOP_DOWN, // 枚举常量TOP_DOWN，表示自顶向下匹配，从根节点向叶子节点遍历

  /**
   * Match in depth-first order.
   * 以深度优先顺序进行匹配
   *
   * <p>It avoids applying a rule to the previous
   * {@link org.apache.calcite.rel.RelNode} repeatedly after new vertex is
   * generated in one rule application. It can therefore be more efficient than
   * {@link #ARBITRARY} in cases such as
   * {@link org.apache.calcite.rel.core.Union} with large fan-out.
   * 它避免在一个规则应用生成新顶点后，重复对先前的{@link org.apache.calcite.rel.RelNode}应用规则
   * 因此在诸如具有大量扇出的{@link org.apache.calcite.rel.core.Union}等情况下，它比{@link #ARBITRARY}更高效
   *
   * 【详细解释】：
   * DEPTH_FIRST采用深度优先搜索（DFS）的遍历策略
   * 深度优先是指沿着树的分支尽可能深地搜索，直到到达叶子节点，然后回溯
   * 这种策略可以避免在规则应用后对已处理的节点重复检查
   * 
   * 【遍历过程】：
   * 1. 从根节点开始
   * 2. 选择一个子节点深入，继续选择其子节点，直到到达叶子
   * 3. 回溯到最近的分支点，选择另一个未访问的子节点
   * 4. 重复上述过程，直到所有节点都被访问
   * 
   * 【核心优势】：
   * 避免重复应用规则：这是DEPTH_FIRST最重要的特性
   * 当一个规则应用到某个节点并生成新的子图时，其他遍历顺序可能会重新检查已经处理过的节点
   * 而DEPTH_FIRST通过深度优先的访问方式，可以确保每个节点只被检查一次
   * 
   * 【避免重复的机制】：
   * 1. 深度优先会完整地处理一个分支后再处理下一个分支
   * 2. 当规则应用产生新节点时，这些新节点会在当前分支的后续处理中被考虑
   * 3. 不会因为新节点的产生而重新访问已经处理过的其他分支
   * 
   * 【优点】：
   * 1. 避免重复规则应用：显著减少重复检查，提高效率
   * 2. 适合复杂表达式：对于分支较多的表达式树特别有效
   * 3. 内存效率高：深度优先通常需要的栈空间较小
   * 4. 可预测性强：遍历顺序相对固定，便于调试
   * 
   * 【缺点】：
   * 1. 可能错过某些优化机会：某些优化可能需要交叉考虑不同分支
   * 2. 实现复杂度中等：需要维护递归或栈来跟踪遍历状态
   * 3. 对深度敏感：对于非常深的树，递归可能导致栈溢出
   * 
   * 【适用场景】：
   * - 表达式树有大量分支（如Union连接多个子查询）
   * - 规则应用会产生新节点，需要避免重复检查
   * - 需要高效的遍历，同时避免重复工作
   * - 复杂的查询优化，包含多个子查询和连接
   * 
   * 【典型用例 - Union场景】：
   * 考虑表达式：Union(Scan1, Scan2, Scan3, ..., Scan100)
   * 
   * 使用ARBITRARY：
   * - 可能先优化Scan1，然后优化Scan2，再优化Scan3...
   * - 如果某个规则将Scan1优化成了新的子树，可能会重新检查Scan2、Scan3等
   * - 导致大量重复工作
   * 
   * 使用DEPTH_FIRST：
   * - 先完整处理Scan1及其优化结果
   * - 然后处理Scan2，不会因为Scan1的变化而重新检查
   * - 每个Scan只被处理一次，效率显著提高
   * 
   * 【示例】：
   * 对于表达式：Union(Filter(Scan1), Join(Scan2, Scan3))
   * DEPTH_FIRST顺序可能是：
   * 1. Union -> Filter -> Scan1（完整处理第一个分支）
   * 2. 回溯到Union -> Join -> Scan2（处理第二个分支的第一部分）
   * 3. Scan2 -> Scan3（完成第二个分支）
   * 
   * 【与ARBITRARY的对比】：
   * ARBITRARY：简单快速，但可能在Union等场景下产生大量重复工作
   * DEPTH_FIRST：稍微复杂，但能避免重复，在大扇出场景下更高效
   * 
   * 【与其他顺序的对比】：
   * BOTTOM_UP：从叶子到根，适合谓词下推
   * TOP_DOWN：从根到叶子，适合全局优化
   * DEPTH_FIRST：深度优先，适合避免重复，处理复杂分支
   */
  DEPTH_FIRST // 枚举常量DEPTH_FIRST，表示深度优先匹配，避免重复规则应用，适合大扇出场景
} // 枚举类定义结束，包含四个遍历策略：ARBITRARY（任意）、BOTTOM_UP（自底向上）、TOP_DOWN（自顶向下）、DEPTH_FIRST（深度优先）
