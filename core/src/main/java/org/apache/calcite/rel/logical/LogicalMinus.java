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
package org.apache.calcite.rel.logical; // 包声明：逻辑关系表达式所在的包

import org.apache.calcite.plan.Convention; // 导入Convention类，表示调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数优化集群，包含元数据工厂等
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系节点的特征集合（如约定、排序等）
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化数据创建关系节点
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式树的节点
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle接口，用于访问和转换关系表达式树
import org.apache.calcite.rel.core.Minus; // 导入Minus基类，表示集合差操作的核心实现
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示关系节点的提示信息（用于优化器）

import java.util.Collections; // 导入Collections工具类，用于创建不可修改的空集合
import java.util.List; // 导入List接口，表示有序集合

/**
 * Sub-class of {@link org.apache.calcite.rel.core.Minus}
 * not targeted at any particular engine or calling convention.
 * 
 * LogicalMinus类：逻辑集合差操作关系节点
 * 
 * 类作用：
 * 这是Calcite中实现集合差操作（EXCEPT）的逻辑关系节点。它继承自Minus基类，
 * 表示一个集合差操作，即从第一个输入结果集中减去后续所有输入结果集。
 * 
 * 核心功能：
 * 1. 实现SQL中的EXCEPT和EXCEPT ALL操作
 * 2. EXCEPT：去重的集合差，返回在第一个输入中但不在其他输入中的行，并去除重复
 * 3. EXCEPT ALL：不去重的集合差，保留所有重复行
 * 4. 该类是逻辑层面的表示，不针对任何特定的引擎或调用约定
 * 
 * 使用场景：
 * - 当SQL查询使用EXCEPT或EXCEPT ALL时，优化器会创建LogicalMinus节点
 * - 在查询优化过程中，LogicalMinus会被转换为物理实现（如EnumerableMinus）
 * 
 * 特点：
 * - final类，不可被继承
 * - 使用Convention.NONE表示这是一个逻辑节点
 * - 支持多个输入（至少2个），第一个输入是被减数，后续输入是减数
 */
public final class LogicalMinus extends Minus {
  //~ Constructors -----------------------------------------------------------
  
  // 以下是构造方法区域，用于创建LogicalMinus实例

  /**
   * Creates a LogicalMinus.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   * 
   * 完整构造方法：创建一个LogicalMinus实例
   * 
   * 参数说明：
   * @param cluster - 关系优化集群，包含类型系统、元数据工厂等共享资源
   * @param traitSet - 关系特征集合，定义此节点的特征（如约定Convention.NONE）
   * @param hints - 提示列表，用于指导优化器如何处理此节点
   * @param inputs - 输入关系节点列表，至少包含2个RelNode（第一个是被减数，后续是减数）
   * @param all - 是否保留重复行，true表示EXCEPT ALL，false表示EXCEPT（去重）
   * 
   * 构造逻辑：
   * - 调用父类Minus的构造方法，传递所有参数
   * - 父类会验证inputs至少包含2个元素
   * - 父类会设置all标志，确定是去重还是保留重复
   */
  public LogicalMinus(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelHint> hints, List<RelNode> inputs, boolean all) {
    super(cluster, traitSet, hints, inputs, all); // 调用父类Minus的构造方法进行初始化
  }

  /**
   * Creates a LogicalMinus.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   * 
   * 简化构造方法：创建一个LogicalMinus实例（无提示版本）
   * 
   * 参数说明：
   * @param cluster - 关系优化集群，包含类型系统、元数据工厂等共享资源
   * @param traitSet - 关系特征集合，定义此节点的特征
   * @param inputs - 输入关系节点列表，至少包含2个RelNode
   * @param all - 是否保留重复行，true表示EXCEPT ALL，false表示EXCEPT（去重）
   * 
   * 构造逻辑：
   * - 调用完整构造方法，传入空的提示列表Collections.emptyList()
   * - 这是一个便捷构造方法，用于不需要提示的场景
   */
  public LogicalMinus(RelOptCluster cluster, RelTraitSet traitSet,
      List<RelNode> inputs, boolean all) {
    this(cluster, traitSet, Collections.emptyList(), inputs, all); // 调用完整构造方法，hints使用空列表
  }

  @Deprecated // to be removed before 2.0 // 标记为废弃，将在2.0版本前移除
  public LogicalMinus(RelOptCluster cluster, List<RelNode> inputs,
      boolean all) {
    this(cluster, cluster.traitSetOf(Convention.NONE), inputs, all); // 调用简化构造方法，traitSet使用cluster的NONE约定
  }


  /**
   * Creates a LogicalMinus by parsing serialized output.
   * 
   * 反序列化构造方法：从序列化数据创建LogicalMinus实例
   * 
   * 参数说明：
   * @param input - 序列化的输入数据，包含cluster、traitSet、inputs、all等所有必要信息
   * 
   * 使用场景：
   * - 当从JSON或其他格式反序列化关系表达式树时使用
   * - 用于跨网络传输或持久化存储后的恢复
   * 
   * 构造逻辑：
   * - 调用父类Minus的反序列化构造方法
   * - 父类会从RelInput对象中提取所有必要参数并初始化节点
   */
  public LogicalMinus(RelInput input) {
    super(input); // 调用父类Minus的反序列化构造方法
  }

  /** Creates a LogicalMinus.
   * 
   * 静态工厂方法：创建LogicalMinus实例的推荐方式
   * 
   * 参数说明：
   * @param inputs - 输入关系节点列表，至少包含2个RelNode
   * @param all - 是否保留重复行，true表示EXCEPT ALL，false表示EXCEPT（去重）
   * 
   * 返回值：
   * @return 新创建的LogicalMinus实例
   * 
   * 创建逻辑：
   * 1. 从第一个输入节点获取RelOptCluster（所有输入应该共享同一个cluster）
   * 2. 创建traitSet，使用Convention.NONE表示这是逻辑节点
   * 3. 调用简化构造方法创建LogicalMinus实例
   * 
   * 设计优势：
   * - 自动从输入节点提取cluster，避免手动指定
   * - 自动设置正确的traitSet（Convention.NONE）
   * - 这是创建LogicalMinus的推荐方式
   */
  public static LogicalMinus create(List<RelNode> inputs, boolean all) {
    final RelOptCluster cluster = inputs.get(0).getCluster(); // 从第一个输入节点获取cluster
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建traitSet，使用NONE约定表示逻辑节点
    return new LogicalMinus(cluster, traitSet, inputs, all); // 调用构造方法创建实例
  }

  //~ Methods ----------------------------------------------------------------
  
  // 以下是方法区域，定义LogicalMinus的各种行为

  /**
   * 复制方法：创建此节点的副本，可以修改特征集合、输入和all标志
   * 
   * 参数说明：
   * @param traitSet - 新的特征集合，用于替换当前节点的特征
   * @param inputs - 新的输入节点列表，用于替换当前节点的输入
   * @param all - 新的all标志，用于替换当前节点的all值
   * 
   * 返回值：
   * @return 新创建的LogicalMinus副本
   * 
   * 方法逻辑：
   * 1. 断言traitSet包含Convention.NONE（如果适用），确保逻辑节点的约定正确
   * 2. 调用构造方法创建新的LogicalMinus实例
   * 3. 保留当前节点的hints列表
   * 4. 使用getCluster()获取当前节点的cluster
   * 
   * 使用场景：
   * - 优化器在重写规则中需要创建修改后的节点副本
   * - 改变节点的特征（如从逻辑转为物理实现）
   * - 改变节点的输入（如输入被优化器重写）
   */
  @Override public LogicalMinus copy(RelTraitSet traitSet, List<RelNode> inputs,
      boolean all) {
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言traitSet包含NONE约定（如果适用）
    return new LogicalMinus(getCluster(), traitSet, hints, inputs, all); // 创建新的LogicalMinus副本
  }

  /**
   * 访问者模式方法：接受RelShuttle访问器的访问
   * 
   * 参数说明：
   * @param shuttle - 关系表达式访问器，用于遍历和转换关系表达式树
   * 
   * 返回值：
   * @return 访问器处理后的结果，可能是转换后的新节点，也可能是原节点
   * 
   * 方法逻辑：
   * - 将当前节点传递给shuttle的visit方法
   * - shuttle会根据其实现决定如何处理此节点
   * - 常见的shuttle实现包括：RelVisitor（只读访问）、RelShuttleImpl（可转换）
   * 
   * 使用场景：
   * - 遍历关系表达式树进行统计或分析
   * - 转换关系表达式树（如优化规则）
   * - 收集节点信息或验证表达式树
   * 
   * 设计模式：
   * - 这是访问者模式（Visitor Pattern）的实现
   * - 允许在不修改节点类的情况下定义新的操作
   */
  @Override public RelNode accept(RelShuttle shuttle) {
    return shuttle.visit(this); // 调用shuttle的visit方法，传入当前节点
  }

  /**
   * 设置提示方法：创建带有新提示列表的节点副本
   * 
   * 参数说明：
   * @param hintList - 新的提示列表，用于替换当前节点的提示
   * 
   * 返回值：
   * @return 带有新提示列表的LogicalMinus副本
   * 
   * 方法逻辑：
   * 1. 使用当前节点的cluster和traitSet
   * 2. 使用新的提示列表hintList
   * 3. 保留当前节点的inputs和all值
   * 4. 创建新的LogicalMinus实例
   * 
   * 使用场景：
   * - 优化器或用户希望为节点添加优化提示
   * - 修改节点的提示信息以影响优化器的决策
   * - 在查询重写过程中调整提示
   * 
   * 提示作用：
   * - 提示可以指导优化器选择特定的执行策略
   * - 例如：提示使用特定的连接算法、索引等
   * - 提示是建议性的，优化器可以选择忽略
   * 
   * 设计原则：
   * - 此方法返回新实例，保持原节点不可变
   * - 符合函数式编程风格，便于优化器进行等价转换
   */
  @Override public RelNode withHints(List<RelHint> hintList) {
    return new LogicalMinus(getCluster(), traitSet, hintList, inputs, all); // 创建带有新提示的副本
  }
}
