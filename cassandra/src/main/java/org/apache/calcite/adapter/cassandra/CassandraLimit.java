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
package org.apache.calcite.adapter.cassandra; // 定义包名，属于 Calcite 的 Cassandra 适配器模块

import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，用于表示关系代数表达式集群（包含类型工厂等共享资源）
import org.apache.calcite.plan.RelOptCost; // 导入 RelOptCost 类，用于表示关系操作符的执行成本（CPU、IO、内存等）
import org.apache.calcite.plan.RelOptPlanner; // 导入 RelOptPlanner 类，用于表示查询优化器，负责计算成本和选择执行计划
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，用于表示关系操作符的特征集合（如约定、排序等）
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，是 Calcite 中所有关系操作符的基类
import org.apache.calcite.rel.RelWriter; // 导入 RelWriter 类，用于将关系操作符的信息输出为可读格式（用于调试和解释）
import org.apache.calcite.rel.SingleRel; // 导入 SingleRel 类，是只有一个子节点的关系操作符的基类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery 类，用于查询关系操作符的元数据（如行数、大小等）
import org.apache.calcite.rex.RexLiteral; // 导入 RexLiteral 类，用于表示常量表达式（如数字、字符串等字面量）
import org.apache.calcite.rex.RexNode; // 导入 RexNode 类，是所有行表达式的基类（用于表示表达式树）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，用于标记可空的字段或参数

import java.util.List; // 导入 List 接口，用于存储集合类型的数据

/**
 * Implementation of limits in Cassandra. // 类注释：Cassandra 数据库中 LIMIT 操作的实现类
 * 
 * 该类负责将 SQL 中的 LIMIT 和 OFFSET 子句转换为 Cassandra 查询中的限制条件。
 * 继承自 SingleRel，表示这是一个单输入的关系操作符（只有一个子节点）。
 * 实现了 CassandraRel 接口，表明这是一个专门为 Cassandra 适配的关系操作符。
 * 
 * 在 Cassandra 中，LIMIT 操作用于限制返回的结果集大小，可以显著提高查询性能，
 * 因为 Cassandra 只需要读取和返回指定数量的数据，而不是扫描整个表。
 * 
 * 该类的主要功能：
 * 1. 存储和传递 offset（偏移量）和 fetch（获取行数）参数
 * 2. 在成本估算时返回零成本（因为 LIMIT 实际上减少了数据量）
 * 3. 将 LIMIT 操作转换为 Cassandra 查询的 LIMIT 子句
 * 4. 支持查询计划的复制和修改
 * 
 * 使用场景：
 * - SQL 查询中包含 LIMIT 子句时
 * - SQL 查询中包含 OFFSET 子句时（虽然 Cassandra 对 OFFSET 支持有限）
 * - 优化器需要估算 LIMIT 操作的成本时
 * - 将 Calcite 的逻辑计划转换为 Cassandra 物理查询时
 */
public class CassandraLimit extends SingleRel implements CassandraRel { // 类定义：Cassandra 的 LIMIT 操作实现类，继承自 SingleRel，实现 CassandraRel 接口
  public final @Nullable RexNode offset; // 成员变量：偏移量表达式，表示要跳过的行数（SQL 中的 OFFSET），可以为 null（表示没有偏移），使用 RexNode 类型以支持动态表达式
  public final @Nullable RexNode fetch; // 成员变量：获取行数表达式，表示要返回的最大行数（SQL 中的 LIMIT），可以为 null（表示没有限制），使用 RexNode 类型以支持动态表达式

  /**
   * 构造方法：创建 CassandraLimit 实例
   * 
   * @param cluster 关系表达式集群，包含类型工厂、Rex 构建器等共享资源
   * @param traitSet 特征集合，定义了这个操作符的物理属性（如约定、排序等）
   * @param input 输入的关系节点（子节点），通常是表扫描或其他过滤操作
   * @param offset 偏移量表达式，表示从第几行开始返回（SQL 中的 OFFSET），可以为 null
   * @param fetch 获取行数表达式，表示最多返回多少行（SQL 中的 LIMIT），可以为 null
   * 
   * 构造方法会调用父类 SingleRel 的构造方法来初始化基础属性，
   * 然后保存 offset 和 fetch 参数，并检查特征集合的一致性。
   */
  public CassandraLimit(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：集群对象，包含类型工厂等共享资源
      RelNode input, @Nullable RexNode offset, @Nullable RexNode fetch) { // 构造方法参数：输入节点、偏移量表达式、获取行数表达式
    super(cluster, traitSet, input); // 调用父类 SingleRel 的构造方法，初始化集群、特征集合和输入节点
    this.offset = offset; // 将 offset 参数保存到实例变量中，用于后续查询计划转换
    this.fetch = fetch; // 将 fetch 参数保存到实例变量中，用于后续查询计划转换
    assert getConvention() == input.getConvention(); // 断言：确保当前节点的约定（Convention）与输入节点的约定一致，这是 Calcite 优化器的基本要求
  }

  /**
   * 计算自身的执行成本
   * 
   * @param planner 查询优化器，用于创建成本对象
   * @param mq 元数据查询对象，用于获取统计信息（如行数、列大小等）
   * @return 返回零成本对象，表示 LIMIT 操作本身不增加执行成本
   * 
   * 该方法返回零成本的原因：
   * 1. LIMIT 操作实际上减少了需要处理的数据量，因此不会增加成本
   * 2. 在 Cassandra 中，LIMIT 是在数据读取时直接应用的，不需要额外的计算
   * 3. 返回零成本可以鼓励优化器尽早应用 LIMIT，从而减少后续操作的数据量
   * 
   * 注意：实际的成本应该由子节点（输入节点）承担，LIMIT 只是限制了结果集大小。
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法重写：计算自身成本，返回可空的成本对象
      RelMetadataQuery mq) { // 方法参数：元数据查询对象，用于获取统计信息
    // We do this so we get the limit for free // 注释：这样做是为了让 LIMIT 操作"免费"（不增加成本）
    return planner.getCostFactory().makeZeroCost(); // 返回零成本对象，表示 LIMIT 操作本身不增加执行成本，鼓励优化器尽早应用
  }

  /**
   * 复制当前节点，创建一个新的 CassandraLimit 实例
   * 
   * @param traitSet 新的特征集合，可以与原节点不同（用于优化器修改计划）
   * @param newInputs 新的输入节点列表（通常是修改后的子节点）
   * @return 返回新的 CassandraLimit 实例，保持原有的 offset 和 fetch 参数
   * 
   * 该方法用于：
   * 1. 优化器在探索不同的执行计划时，复制节点并修改其属性
   * 2. 在规则匹配和应用时，创建新的节点实例
   * 3. 保持原有的 offset 和 fetch 参数不变，只修改特征集合和输入节点
   * 
   * 注意：sole(newInputs) 方法用于从列表中提取唯一的输入节点（因为 SingleRel 只有一个子节点）。
   */
  @Override public CassandraLimit copy(RelTraitSet traitSet, List<RelNode> newInputs) { // 方法重写：复制节点，返回新的 CassandraLimit 实例
    return new CassandraLimit(getCluster(), traitSet, sole(newInputs), offset, fetch); // 创建新的 CassandraLimit 实例，使用新的特征集合和输入节点，但保持原有的 offset 和 fetch
  }

  /**
   * 实现 Cassandra 查询的转换逻辑
   * 
   * @param implementor 实现器对象，负责将 Calcite 的逻辑计划转换为 Cassandra 的物理查询
   * 
   * 该方法的主要功能：
   * 1. 先访问子节点（输入节点），确保子节点的查询逻辑已经实现
   * 2. 如果 offset 不为 null，将其转换为整数值并设置到实现器中
   * 3. 如果 fetch 不为 null，将其转换为整数值并设置到实现器中
   * 
   * 转换过程：
   * - visitChild(0, getInput())：递归访问子节点，确保整个查询树都被正确转换
   * - RexLiteral.intValue(offset)：将 RexNode 类型的 offset 表达式转换为 Java int 值
   * - implementor.offset 和 implementor.fetch：将转换后的值存储到实现器中，
   *   实现器会在生成 Cassandra CQL 查询时使用这些值
   * 
   * 注意：
   * - offset 和 fetch 必须是字面量（RexLiteral），否则会抛出异常
   * - 该方法假设 offset 和 fetch 的值在编译时已知，不支持动态表达式
   */
  @Override public void implement(Implementor implementor) { // 方法重写：实现 Cassandra 查询转换逻辑
    implementor.visitChild(0, getInput()); // 访问子节点（索引为 0），确保子节点的查询逻辑已经实现，这是递归转换的关键步骤
    if (offset != null) { // 检查 offset 是否不为 null（是否设置了偏移量）
      implementor.offset = RexLiteral.intValue(offset); // 将 offset 表达式转换为整数值，并设置到实现器的 offset 字段中
    } // 结束 if 语句
    if (fetch != null) { // 检查 fetch 是否不为 null（是否设置了获取行数限制）
      implementor.fetch = RexLiteral.intValue(fetch); // 将 fetch 表达式转换为整数值，并设置到实现器的 fetch 字段中
    } // 结束 if 语句
  } // 结束 implement 方法

  /**
   * 输出查询计划的可读描述
   * 
   * @param pw 关系写入器，用于构建和输出查询计划描述
   * @return 返回关系写入器对象，支持链式调用
   * 
   * 该方法用于：
   * 1. 生成查询计划的文本描述，用于调试和日志记录
   * 2. 在 EXPLAIN 命令中展示查询计划
   * 3. 帮助用户理解优化器选择的执行计划
   * 
   * 输出格式示例：
   * CassandraLimit(offset=10, fetch=100)
   * 
   * 注意：
   * - itemIf 方法只在条件为 true 时才输出该项
   * - 如果 offset 或 fetch 为 null，则不会在输出中显示
   * - super.explainTerms(pw) 会输出父类（SingleRel）的基础信息
   */
  @Override public RelWriter explainTerms(RelWriter pw) { // 方法重写：输出查询计划的可读描述
    super.explainTerms(pw); // 调用父类的 explainTerms 方法，输出基础信息（如特征集合、输入节点等）
    pw.itemIf("offset", offset, offset != null); // 如果 offset 不为 null，输出 offset 项及其值
    pw.itemIf("fetch", fetch, fetch != null); // 如果 fetch 不为 null，输出 fetch 项及其值
    return pw; // 返回关系写入器，支持链式调用
  } // 结束 explainTerms 方法
} // 结束 CassandraLimit 类定义
