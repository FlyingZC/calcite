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
package org.apache.calcite.rel.logical; // 声明包名，该类属于逻辑关系表达式包

import org.apache.calcite.plan.Convention; // 导入约定接口，用于定义关系表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含查询优化器的共享资源
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，用于定义关系表达式的特征
import org.apache.calcite.rel.RelInput; // 导入关系输入接口，用于从序列化数据创建关系节点
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系表达式的基础接口
import org.apache.calcite.rel.RelShuttle; // 导入关系穿梭器接口，用于遍历和修改关系表达式树
import org.apache.calcite.rel.core.Union; // 导入Union基类，逻辑UNION操作的核心实现
import org.apache.calcite.rel.hint.RelHint; // 导入关系提示接口，用于向优化器提供提示信息

import java.util.Collections; // 导入集合工具类，提供不可修改的空集合等
import java.util.List; // 导入列表接口，用于存储有序集合

/**
 * LogicalUnion 类：逻辑UNION操作的关系表达式实现
 * 
 * 这是 {@link org.apache.calcite.rel.core.Union} 的子类，表示SQL中的UNION或UNION ALL操作
 * 该类不针对任何特定的引擎或调用约定，是Calcite中逻辑层面的UNION操作表示
 * 
 * 主要功能：
 * 1. 表示多个关系表达式的集合操作（UNION或UNION ALL）
 * 2. 支持去重（UNION）和不去重（UNION ALL）两种模式
 * 3. 作为逻辑算子，可以被优化器转换为物理算子
 * 4. 支持提示（hints）来影响优化器的决策
 * 
 * 使用场景：
 * - SQL查询中的UNION操作：SELECT ... UNION SELECT ...
 * - SQL查询中的UNION ALL操作：SELECT ... UNION ALL SELECT ...
 * - 查询优化过程中的中间表示
 * 
 * 继承关系：
 * - 继承自 Union 抽象类，实现了特定的UNION操作逻辑
 * - 实现 RelNode 接口，作为关系表达式树的一个节点
 * 
 * 关键特性：
 * - final类，不能被继承
 * - 支持多输入（多个子查询）
 * - 通过all参数控制是否去重
 * - 支持优化器提示
 */
public final class LogicalUnion extends Union {
  //~ Constructors -----------------------------------------------------------

  /**
   * 构造方法1：创建一个LogicalUnion实例（完整参数版本）
   * 
   * 这是主要的构造方法，接受所有必要的参数来创建逻辑UNION节点
   * 
   * <p>Use {@link #create} unless you know what you're doing.</p>
   * 
   * 参数说明：
   * @param cluster 关系优化集群（RelOptCluster），包含查询优化器的共享资源如类型系统、元数据提供者等
   *                该对象在整个查询优化过程中是共享的，确保所有关系节点使用相同的优化上下文
   * @param traitSet 关系特征集合（RelTraitSet），定义该关系表达式的特征属性
   *                 包括调用约定（Convention）、分布特性、排序特性等
   *                 优化器会根据这些特征进行规则匹配和转换
   * @param hints 关系提示列表（List<RelHint>），向优化器提供的提示信息
   *             可以用于影响优化器的决策，如使用特定的索引、控制并行度等
   *             可能为空列表，表示没有提示
   * @param inputs 输入关系节点列表（List<RelNode>），表示要执行UNION操作的多个子查询
   *               每个RelNode代表一个子查询的关系表达式
   *               列表大小至少为2（UNION操作至少需要两个输入）
   *               所有输入的schema必须兼容（列数相同，列类型可兼容）
   * @param all 布尔值，控制是否保留重复行
   *            true = UNION ALL，保留所有行包括重复行
   *            false = UNION，去除重复行（执行去重操作）
   *            去重操作通常需要额外的排序或哈希操作，性能开销较大
   * 
   * 构造过程：
   * 1. 调用父类Union的构造方法，初始化UNION操作的基本属性
   * 2. 将参数传递给父类，由父类设置成员变量
   * 3. 父类会验证输入的有效性（如输入列表不能为空等）
   * 
   * 使用场景：
   * - 在规则转换过程中创建新的LogicalUnion节点
   * - 在查询重写时修改现有的UNION操作
   * - 需要指定提示信息的场景
   */
  public LogicalUnion(RelOptCluster cluster, // 关系优化集群参数
      RelTraitSet traitSet, // 关系特征集合参数
      List<RelHint> hints, // 优化器提示列表参数
      List<RelNode> inputs, // 输入关系节点列表参数
      boolean all) { // 是否保留重复行参数
    super(cluster, traitSet, hints, inputs, all); // 调用父类Union的构造方法，初始化UNION操作
  }

  /**
   * 构造方法2：创建一个LogicalUnion实例（无hints版本）
   * 
   * 这是一个简化的构造方法，不接受hints参数，内部使用空列表
   * 
   * <p>Use {@link #create} unless you know what you're doing.</p>
   * 
   * 参数说明：
   * @param cluster 关系优化集群（RelOptCluster），包含查询优化器的共享资源
   * @param traitSet 关系特征集合（RelTraitSet），定义该关系表达式的特征属性
   * @param inputs 输入关系节点列表（List<RelNode>），表示要执行UNION操作的多个子查询
   * @param all 布尔值，控制是否保留重复行
   *            true = UNION ALL，保留所有行包括重复行
   *            false = UNION，去除重复行
   * 
   * 构造过程：
   * 1. 调用完整参数的构造方法（构造方法1）
   * 2. 将hints参数设置为空列表（Collections.emptyList()）
   * 3. 传递其他参数到完整构造方法
   * 
   * 使用场景：
   * - 不需要优化器提示的场景
   * - 代码更简洁，减少参数传递
   */
  public LogicalUnion(RelOptCluster cluster, // 关系优化集群参数
      RelTraitSet traitSet, // 关系特征集合参数
      List<RelNode> inputs, // 输入关系节点列表参数
      boolean all) { // 是否保留重复行参数
    this(cluster, traitSet, Collections.emptyList(), inputs, all); // 调用完整构造方法，hints使用空列表
  }

  /**
   * 构造方法3：创建一个LogicalUnion实例（已弃用的旧版本）
   * 
   * 这是一个旧版本的构造方法，不包含traitSet参数
   * 自动使用Convention.NONE作为默认约定
   * 
   * @deprecated 将在2.0版本之前移除，请使用新的构造方法
   * 
   * 参数说明：
   * @param cluster 关系优化集群（RelOptCluster），包含查询优化器的共享资源
   * @param inputs 输入关系节点列表（List<RelNode>），表示要执行UNION操作的多个子查询
   * @param all 布尔值，控制是否保留重复行
   * 
   * 构造过程：
   * 1. 从cluster中获取默认的traitSet（Convention.NONE）
   * 2. 调用构造方法2（无hints版本）
   * 3. 传递cluster、默认traitSet、inputs和all参数
   * 
   * 使用场景：
   * - 旧代码兼容性
   * - 不推荐在新代码中使用
   */
  @Deprecated // to be removed before 2.0 // 标记为已弃用，将在2.0版本前移除
  public LogicalUnion(RelOptCluster cluster, List<RelNode> inputs, // 关系优化集群和输入列表参数
      boolean all) { // 是否保留重复行参数
    this(cluster, cluster.traitSetOf(Convention.NONE), inputs, all); // 调用构造方法2，使用默认的NONE约定
  }

  /**
   * 构造方法4：通过解析序列化输出来创建LogicalUnion实例
   * 
   * 这个构造方法用于从序列化的数据中重建LogicalUnion对象
   * 通常用于查询计划的持久化和反序列化
   * 
   * 参数说明：
   * @param input 关系输入对象（RelInput），包含序列化的关系节点信息
   *              RelInput是一个接口，提供了从序列化数据读取关系节点属性的方法
   *              包含的信息包括：输入列表、all标志、hints等
   * 
   * 构造过程：
   * 1. 调用父类Union的构造方法，传入RelInput对象
   * 2. 父类会从RelInput中读取所有必要的属性
   * 3. 重建完整的LogicalUnion对象
   * 
   * 使用场景：
   * - 从持久化存储中加载查询计划
   * - 网络传输查询计划后的反序列化
   * - 查询计划的缓存和恢复
   */
  public LogicalUnion(RelInput input) { // 关系输入对象参数
    super(input); // 调用父类Union的构造方法，从序列化数据重建对象
  }

  /**
   * 静态工厂方法：创建LogicalUnion实例
   * 
   * 这是推荐的创建LogicalUnion实例的方法
   * 自动从第一个输入节点获取cluster和traitSet
   * 
   * 参数说明：
   * @param inputs 输入关系节点列表（List<RelNode>），表示要执行UNION操作的多个子查询
   *               至少包含一个RelNode节点
   *               所有输入的schema必须兼容
   * @param all 布尔值，控制是否保留重复行
   *            true = UNION ALL，保留所有行包括重复行
   *            false = UNION，去除重复行
   * 
   * 创建过程：
   * 1. 从第一个输入节点（inputs.get(0)）获取RelOptCluster对象
   *    确保所有输入使用相同的优化集群
   * 2. 从cluster创建默认的traitSet，使用Convention.NONE约定
   * 3. 调用构造方法2（无hints版本）创建LogicalUnion实例
   * 4. 返回新创建的LogicalUnion对象
   * 
   * 返回值：
   * @return 新创建的LogicalUnion实例
   * 
   * 使用场景：
   * - 在查询解析后创建逻辑UNION节点
   * - 在规则转换过程中创建新的UNION节点
   * - 推荐使用的创建方法，代码更简洁
   */
  public static LogicalUnion create(List<RelNode> inputs, boolean all) { // 输入列表和all标志参数
    final RelOptCluster cluster = inputs.get(0).getCluster(); // 从第一个输入节点获取关系优化集群
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建默认的特征集合，使用NONE约定
    return new LogicalUnion(cluster, traitSet, inputs, all); // 调用构造方法创建并返回LogicalUnion实例
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * copy方法：创建当前LogicalUnion节点的副本
   * 
   * 这是RelNode接口的核心方法之一，用于在规则转换过程中创建关系节点的副本
   * 允许修改traitSet、inputs或all参数，同时保持其他属性不变
   * 
   * 参数说明：
   * @param traitSet 新的关系特征集合（RelTraitSet），用于替换当前节点的特征
   *                可以包含不同的调用约定、分布特性等
   *                优化器在规则转换时会修改traitSet以应用不同的物理实现
   * @param inputs 新的输入关系节点列表（List<RelNode>），用于替换当前节点的输入
   *               可以是不同的子查询，但schema必须兼容
   *               优化器可能会重写或优化输入的子查询
   * @param all 新的all标志（boolean），控制是否保留重复行
   *            可以修改为true或false，改变UNION的行为
   *            优化器可能会根据成本分析决定是否去重
   * 
   * 方法实现：
   * 1. 断言检查：确保新的traitSet包含Convention.NONE约定（如果适用）
   *    LogicalUnion是逻辑算子，必须使用NONE约定
   *    如果traitSet不包含NONE约定，会抛出断言错误
   * 2. 创建新的LogicalUnion实例：
   *    - 使用当前节点的cluster（getCluster()）
   *    - 使用新的traitSet参数
   *    - 保留当前节点的hints
   *    - 使用新的inputs参数
   *    - 使用新的all参数
   * 3. 返回新创建的LogicalUnion实例
   * 
   * 返回值：
   * @return 新的LogicalUnion实例，具有指定的traitSet、inputs和all参数
   * 
   * 使用场景：
   * - 优化器规则转换：将逻辑UNION转换为物理UNION
   * - 查询重写：修改UNION操作的特征或输入
   * - 成本估算：创建不同配置的UNION节点进行比较
   * - 特征传播：在优化过程中传递和修改特征
   * 
   * 注意事项：
   * - 原始节点不会被修改，而是创建一个新节点
   * - hints从当前节点复制到新节点
   * - 必须确保新的traitSet包含Convention.NONE（如果适用）
   */
  @Override public LogicalUnion copy( // 重写RelNode接口的copy方法
      RelTraitSet traitSet, List<RelNode> inputs, boolean all) { // 新的特征集合、输入列表和all标志
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：确保traitSet包含NONE约定（逻辑算子的要求）
    return new LogicalUnion(getCluster(), traitSet, hints, inputs, all); // 创建并返回新的LogicalUnion实例
  }

  /**
   * accept方法：接受关系穿梭器（RelShuttle）的访问
   * 
   * 这是访问者模式（Visitor Pattern）的实现，允许RelShuttle遍历和修改关系表达式树
   * RelShuttle可以访问、修改或替换关系节点
   * 
   * 参数说明：
   * @param shuttle 关系穿梭器（RelShuttle），用于访问和修改关系表达式树
   *                RelShuttle是一个接口，定义了访问各种关系节点的方法
   *                可以用于：
   *                - 遍历关系表达式树
   *                - 收集统计信息
   *                - 验证关系表达式
   *                - 修改或替换节点
   *                - 应用转换规则
   * 
   * 方法实现：
   * 1. 调用RelShuttle的visit方法，传入当前LogicalUnion实例
   * 2. RelShuttle会根据其实现逻辑处理当前节点
   * 3. 可能会：
   *    - 递归访问子节点
   *    - 修改当前节点
   *    - 替换当前节点
   *    - 收集信息
   * 4. 返回RelShuttle处理后的结果
   * 
   * 返回值：
   * @return 处理后的关系节点（RelNode），可能是：
   *         - 原始的LogicalUnion节点（如果没有修改）
   *         - 修改后的LogicalUnion节点
   *         - 替换后的其他类型节点
   *         - null（在某些情况下）
   * 
   * 使用场景：
   * - 查询优化：优化器使用RelShuttle遍历和优化查询计划
   * - 计划验证：验证查询计划的正确性
   * - 统计信息收集：收集查询计划的统计信息
   * - 计划转换：应用转换规则修改查询计划
   * - 调试和测试：检查关系表达式树的结构
   * 
   * 常见的RelShuttle实现：
   * - RelShuttleImpl：基本的穿梭器实现
   * - RelHomogenizer：规范化关系表达式
   * - RelDecorrelator：去相关化处理
   * - RelSubset：关系子集的穿梭器
   */
  @Override public RelNode accept(RelShuttle shuttle) { // 重写RelNode接口的accept方法
    return shuttle.visit(this); // 调用RelShuttle的visit方法，让穿梭器访问当前节点
  }

  /**
   * withHints方法：创建带有新提示列表的LogicalUnion副本
   * 
   * 这个方法用于创建当前LogicalUnion节点的副本，但使用新的提示列表
   * 其他属性（cluster、traitSet、inputs、all）保持不变
   * 
   * 参数说明：
   * @param hintList 新的关系提示列表（List<RelHint>），用于替换当前节点的提示
   *                 可以包含多个RelHint对象
   *                 每个RelHint可以向优化器提供特定的提示信息
   *                 常见的提示包括：
   *                 - 索引提示：建议使用特定索引
   *                 - 并行度提示：建议的并行度
   *                 - 连接顺序提示：建议的连接顺序
   *                 - 聚合策略提示：建议的聚合实现方式
   * 
   * 方法实现：
   * 1. 创建新的LogicalUnion实例：
   *    - 使用当前节点的cluster（getCluster()）
   *    - 使用当前节点的traitSet（traitSet）
   *    - 使用新的hintList参数
   *    - 使用当前节点的inputs（inputs）
   *    - 使用当前节点的all标志（all）
   * 2. 返回新创建的LogicalUnion实例
   * 
   * 返回值：
   * @return 新的LogicalUnion实例，具有指定的hintList
   *         其他属性与当前节点相同
   * 
   * 使用场景：
   * - 优化器提示：向优化器提供额外的优化建议
   * - 查询调优：通过 hints 影响查询执行计划
   * - A/B测试：比较不同提示下的查询性能
   * - 强制执行：强制优化器使用特定的执行策略
   * 
   * 注意事项：
   * - 原始节点不会被修改
   * - 提示只是建议，优化器不一定会遵循
   * - 提示的有效性取决于具体的优化器实现
   * - 过多的提示可能会限制优化器的优化空间
   * 
   * 示例：
   * // 创建一个带有并行度提示的UNION
   * RelHint parallelHint = RelHint.builder("parallel")
   *     .hintOption("parallelism", "4")
   *     .build();
   * LogicalUnion withHint = union.withHints(Collections.singletonList(parallelHint));
   */
  @Override public RelNode withHints(List<RelHint> hintList) { // 重写RelNode接口的withHints方法
    return new LogicalUnion(getCluster(), traitSet, hintList, inputs, all); // 创建并返回带有新提示的LogicalUnion实例
  }
}
