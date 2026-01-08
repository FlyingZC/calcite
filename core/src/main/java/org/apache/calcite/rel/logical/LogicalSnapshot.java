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
 */ // Apache License 2.0 许可证头部，声明版权和使用条款
package org.apache.calcite.rel.logical; // 声明包名，LogicalSnapshot 类位于 logical 包下

import org.apache.calcite.plan.Convention; // 导入 Convention 接口，用于定义关系表达式的调用约定（物理实现约定）
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，表示关系表达式集群，包含类型工厂和表达式工厂等共享资源
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系表达式的特征集合（如排序、分布等）
import org.apache.calcite.rel.RelCollationTraitDef; // 导入 RelCollationTraitDef 类，定义排序特征
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入 RelDistributionTraitDef 类，定义分布特征
import org.apache.calcite.rel.RelInput; // 导入 RelInput 类，用于从序列化输出中解析关系表达式
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，所有关系表达式的基接口
import org.apache.calcite.rel.core.Snapshot; // 导入 Snapshot 抽象类，LogicalSnapshot 的父类，表示快照操作
import org.apache.calcite.rel.hint.RelHint; // 导入 RelHint 类，表示关系表达式的提示信息
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入 RelMdCollation 类，提供排序元数据
import org.apache.calcite.rel.metadata.RelMdDistribution; // 导入 RelMdDistribution 类，提供分布元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery 类，用于查询关系表达式的元数据
import org.apache.calcite.rex.RexNode; // 导入 RexNode 类，表示行表达式（关系表达式）

import com.google.common.collect.ImmutableList; // 导入 ImmutableList 类，Google Guava 提供的不可变列表工具类

import java.util.List; // 导入 List 接口，Java 标准库的列表接口

/**
 * Sub-class of {@link org.apache.calcite.rel.core.Snapshot}
 * not targeted at any particular engine or calling convention.
 */ // LogicalSnapshot 类的文档注释：这是 Snapshot 的子类，不针对任何特定引擎或调用约定
/**
 * LogicalSnapshot 类：逻辑快照关系表达式
 * 
 * 【类的作用】
 * LogicalSnapshot 是 Calcite 查询优化器中用于表示表快照操作的关系表达式节点。
 * 它允许查询在特定时间点的表数据，而不影响当前表的数据。这是实现时间旅行查询（Time Travel Query）
 * 的核心组件，常用于数据仓库和历史数据分析场景。
 * 
 * 【核心功能】
 * 1. 时间旅行查询：允许查询历史时间点的数据状态
 * 2. 逻辑层抽象：提供逻辑层面的快照操作，不绑定到任何特定的物理实现
 * 3. 特征推导：自动推导排序（Collation）和分布（Distribution）特征
 * 4. 提示支持：支持通过 hint 优化查询执行
 * 
 * 【继承关系】
 * 继承自 Snapshot 抽象类，是快照操作的逻辑实现。与物理快照（如 EnumerableSnapshot）不同，
 * LogicalSnapshot 不绑定到任何特定的调用约定（Convention），这使得它可以在优化过程中
 * 被转换为各种不同的物理实现。
 * 
 * 【使用场景】
 * - 查询表在某个历史时间点的数据
 * - 数据审计和追溯
 * - 时序数据分析
 * - 版本控制和数据回滚
 * 
 * 【关键概念】
 * - period（时间点）：表示查询的历史时间点，是一个 RexNode 表达式
 * - input（输入）：被快照的表或关系表达式
 * - traitSet（特征集）：包含排序、分布等物理特征
 * 
 * 【示例】
 * SQL: SELECT * FROM employees FOR SYSTEM_TIME AS OF '2023-01-01'
 * 会被转换为 LogicalSnapshot(input=TableScan(employees), period='2023-01-01')
 */
public class LogicalSnapshot extends Snapshot { // 定义 LogicalSnapshot 类，继承自 Snapshot 抽象类，表示逻辑快照操作

  //~ Constructors ----------------------------------------------------------- // 构造方法区域的分隔注释

  /**
   * Creates a LogicalSnapshot by parsing serialized output.
   */ // 构造方法文档注释：通过解析序列化输出来创建 LogicalSnapshot
  public LogicalSnapshot(RelInput input) { // 构造方法：从序列化输入创建 LogicalSnapshot 对象，input 包含反序列化所需的所有信息
    super(input); // 调用父类 Snapshot 的构造方法，传递 RelInput 参数以初始化快照操作
  } // 构造方法结束

  /**
   * Creates a LogicalSnapshot.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   *
   * @param cluster   Cluster that this relational expression belongs to
   * @param traitSet  The traits of this relational expression
   * @param hints     Hints for this node
   * @param input     Input relational expression
   * @param period    Timestamp expression which as the table was at the given
   *                  time in the past
   */ // 完整构造方法的文档注释：创建 LogicalSnapshot 对象，建议使用 create 工厂方法
  public LogicalSnapshot(RelOptCluster cluster, RelTraitSet traitSet, List<RelHint> hints, // 构造方法：创建 LogicalSnapshot 对象，cluster 是关系表达式集群，traitSet 是特征集合，hints 是提示列表
      RelNode input, RexNode period) { // input 是输入关系表达式（被快照的表），period 是时间点表达式（表示查询的历史时间）
    super(cluster, traitSet, hints, input, period); // 调用父类 Snapshot 的构造方法，初始化快照操作的所有参数
  } // 构造方法结束

  /**
   * Creates a LogicalSnapshot.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   *
   * @param cluster   Cluster that this relational expression belongs to
   * @param traitSet  The traits of this relational expression
   * @param input     Input relational expression
   * @param period    Timestamp expression which as the table was at the given
   *                  time in the past
   */ // 简化构造方法的文档注释：创建 LogicalSnapshot 对象，不使用 hints
  public LogicalSnapshot(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法：创建 LogicalSnapshot 对象，cluster 是关系表达式集群，traitSet 是特征集合
      RelNode input, RexNode period) { // input 是输入关系表达式，period 是时间点表达式
    super(cluster, traitSet, ImmutableList.of(), input, period); // 调用父类 Snapshot 的构造方法，hints 参数使用空列表 ImmutableList.of()，表示没有提示
  } // 构造方法结束

  @Override public Snapshot copy(RelTraitSet traitSet, RelNode input, // 重写 copy 方法：创建当前 LogicalSnapshot 的副本，traitSet 是新的特征集合，input 是新的输入关系表达式
      RexNode period) { // period 是新的时间点表达式
    return new LogicalSnapshot(getCluster(), traitSet, hints, input, period); // 返回新的 LogicalSnapshot 对象，使用当前对象的 cluster 和 hints，以及传入的新参数
  } // copy 方法结束

  /** Creates a LogicalSnapshot. */ // 静态工厂方法的文档注释：创建 LogicalSnapshot 对象
  public static LogicalSnapshot create(RelNode input, RexNode period) { // 静态工厂方法：创建 LogicalSnapshot 对象，input 是输入关系表达式，period 是时间点表达式
    final RelOptCluster cluster = input.getCluster(); // 获取输入关系表达式所属的集群，用于共享类型工厂和表达式工厂等资源
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 从集群中获取元数据查询对象，用于查询输入的排序和分布等元数据
    final RelTraitSet traitSet = cluster.traitSet() // 创建特征集合，从集群的默认特征集开始
        .replace(Convention.NONE) // 替换调用约定为 NONE，表示这是一个逻辑节点，不绑定到任何物理实现
        .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果可能，替换排序特征，使用 RelCollationTraitDef.INSTANCE 定义排序特征
            () -> RelMdCollation.snapshot(mq, input)) // 通过 RelMdCollation.snapshot 方法从输入推导快照的排序特征
        .replaceIf(RelDistributionTraitDef.INSTANCE, // 如果可能，替换分布特征，使用 RelDistributionTraitDef.INSTANCE 定义分布特征
            () -> RelMdDistribution.snapshot(mq, input)); // 通过 RelMdDistribution.snapshot 方法从输入推导快照的分布特征
    return new LogicalSnapshot(cluster, traitSet, input, period); // 返回新创建的 LogicalSnapshot 对象，使用推导的特征集合
  } // create 静态方法结束

  @Override public RelNode withHints(final List<RelHint> hintList) { // 重写 withHints 方法：为当前 LogicalSnapshot 添加提示，hintList 是提示列表
    return new LogicalSnapshot(getCluster(), traitSet, hintList, input, getPeriod()); // 返回新的 LogicalSnapshot 对象，使用当前对象的特征集、输入和时间点，以及新的提示列表
  } // withHints 方法结束
} // LogicalSnapshot 类定义结束
