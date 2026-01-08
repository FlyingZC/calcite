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
package org.apache.calcite.rel.core; // 声明包名，该类属于 org.apache.calcite.rel.core 包，是 Calcite 关系代数核心包的一部分

import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群类，用于管理关系节点的共享信息（如类型工厂、表达式工厂等）
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类，用于定义关系节点的物理属性（如排序、分布等）
import org.apache.calcite.rel.RelInput; // 导入关系输入类，用于从序列化数据中反序列化关系节点
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，是 Calcite 中所有关系表达式的基类
import org.apache.calcite.rel.hint.RelHint; // 导入关系提示类，用于存储优化器提示信息
import org.apache.calcite.rel.metadata.RelMdUtil; // 导入关系元数据工具类，提供各种元数据计算方法
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询类，用于查询关系节点的元数据（如行数、大小等）
import org.apache.calcite.sql.SqlKind; // 导入 SQL 种类枚举，表示 SQL 操作的类型（如 UNION、INTERSECT 等）

import java.util.Collections; // 导入集合工具类，提供不可修改的空集合等静态方法
import java.util.List; // 导入列表接口，用于存储有序的元素集合

/**
 * Relational expression that returns the union of the rows of its inputs,
 * optionally eliminating duplicates.
 *
 * <p>Corresponds to SQL {@code UNION} and {@code UNION ALL}.
 */
// Union 类：抽象类，表示 SQL 中的 UNION 操作（并集操作）
// 继承自 SetOp（集合操作基类），用于表示关系代数中的并集操作
// 该类对应 SQL 中的 UNION（去重并集）和 UNION ALL（不去重并集）操作
// 它将多个输入关系节点的行合并为一个结果集，根据 all 参数决定是否去除重复行
// 作为一个抽象类，它定义了 UNION 操作的基本结构，具体实现由子类提供（如 LogicalUnion、EnumerableUnion 等）
public abstract class Union extends SetOp { // 声明抽象类 Union，继承自 SetOp 集合操作基类
  //~ Constructors -----------------------------------------------------------

  // 构造方法1：带有提示信息的完整构造方法
  // 参数说明：
  //   - cluster: 关系优化集群，包含类型工厂、表达式工厂等共享资源
  //   - traits: 关系特征集合，定义该关系节点的物理属性（如排序规则、分布方式等）
  //   - hints: 优化器提示列表，用于指导优化器如何处理该关系节点
  //   - inputs: 输入关系节点列表，包含需要进行 UNION 操作的所有子关系节点
  //   - all: 布尔值，true 表示 UNION ALL（不去重），false 表示 UNION（去重）
  // 该构造方法调用父类 SetOp 的构造方法，传入 SqlKind.UNION 表示这是 UNION 操作
  protected Union( // 声明受保护的构造方法，允许子类调用
      RelOptCluster cluster, // 参数：关系优化集群，提供共享的优化上下文
      RelTraitSet traits, // 参数：关系特征集合，定义节点的物理属性
      List<RelHint> hints, // 参数：优化器提示列表，用于指导优化器决策
      List<RelNode> inputs, // 参数：输入关系节点列表，包含所有要合并的子关系
      boolean all) { // 参数：是否为 UNION ALL，true 表示不去重，false 表示去重
    super(cluster, traits, hints, inputs, SqlKind.UNION, all); // 调用父类 SetOp 构造方法，传入所有参数和操作类型 UNION
  } // 构造方法结束

  // 构造方法2：不带提示信息的简化构造方法
  // 参数说明：
  //   - cluster: 关系优化集群，包含类型工厂、表达式工厂等共享资源
  //   - traits: 关系特征集合，定义该关系节点的物理属性
  //   - inputs: 输入关系节点列表，包含需要进行 UNION 操作的所有子关系节点
  //   - all: 布尔值，true 表示 UNION ALL（不去重），false 表示 UNION（去重）
  // 该构造方法内部使用空列表作为 hints 参数，适用于不需要优化器提示的场景
  protected Union( // 声明受保护的构造方法，允许子类调用
      RelOptCluster cluster, // 参数：关系优化集群，提供共享的优化上下文
      RelTraitSet traits, // 参数：关系特征集合，定义节点的物理属性
      List<RelNode> inputs, // 参数：输入关系节点列表，包含所有要合并的子关系
      boolean all) { // 参数：是否为 UNION ALL，true 表示不去重，false 表示去重
    super(cluster, traits, Collections.emptyList(), inputs, SqlKind.UNION, all); // 调用父类构造方法，使用空集合作为 hints，传入操作类型 UNION
  } // 构造方法结束

  /**
   * Creates a Union by parsing serialized output.
   */
  // 构造方法3：通过反序列化创建 Union 对象
  // 参数说明：
  //   - input: 关系输入对象，包含从序列化数据中解析出的关系节点信息
  // 该构造方法用于从序列化格式（如 JSON、XML 等）中恢复 Union 对象
  // 主要用于分布式计算、持久化或跨进程传输关系树的场景
  protected Union(RelInput input) { // 声明受保护的构造方法，接受序列化的关系输入对象
    super(input); // 调用父类 SetOp 的构造方法，由父类处理反序列化逻辑
  } // 构造方法结束

  //~ Methods ----------------------------------------------------------------

  // 方法：estimateRowCount - 估算 Union 操作的输出行数
  // 参数说明：
  //   - mq: 关系元数据查询对象，用于查询输入关系节点的元数据（如行数等）
  // 返回值：double 类型，表示估算的输出行数
  // 实现逻辑：
  //   1. 首先调用 RelMdUtil.getUnionAllRowCount 计算所有输入的行数总和（即 UNION ALL 的行数）
  //   2. 如果 all 为 false（即 UNION 操作，需要去重），则将行数乘以 0.5 作为估算值
  //     （这是一个经验值，假设去重后保留约 50% 的行）
  //   3. 如果 all 为 true（即 UNION ALL 操作），则直接返回所有输入的行数总和
  // 该方法用于优化器的成本估算，帮助优化器选择最优的执行计划
  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写父类方法，估算输出行数
    double dRows = RelMdUtil.getUnionAllRowCount(mq, this); // 计算所有输入的行数总和（UNION ALL 的行数）
    if (!all) { // 如果 all 为 false，表示需要去重（UNION 操作）
      dRows *= 0.5; // 将行数乘以 0.5，估算去重后的行数（经验值）
    } // if 语句结束
    return dRows; // 返回估算的行数
  } // 方法结束

  // 方法：estimateRowCount - 静态方法，估算 Union 操作的输出行数（已废弃）
  // 参数说明：
  //   - rel: 关系节点对象，应该是 Union 类型的实例
  // 返回值：double 类型，表示估算的输出行数
  // 废弃原因：
  //   - 这是一个静态方法，不符合面向对象的设计原则
  //   - 应该使用实例方法 estimateRowCount(RelMetadataQuery mq) 代替
  //   - 计划在 2.0 版本之前移除
  // 实现逻辑：
  //   1. 从关系节点的集群中获取元数据查询对象
  //   2. 将关系节点转换为 Union 类型
  //   3. 调用 RelMdUtil.getUnionAllRowCount 计算行数
  // 该方法仅用于向后兼容，新代码不应使用
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在 2.0 版本前移除
  public static double estimateRowCount(RelNode rel) { // 声明静态方法，接受关系节点作为参数
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 从关系节点的集群中获取元数据查询对象
    return RelMdUtil.getUnionAllRowCount(mq, (Union) rel); // 调用工具方法计算 Union 操作的行数，将 rel 强制转换为 Union 类型
  } // 方法结束
} // 类结束
