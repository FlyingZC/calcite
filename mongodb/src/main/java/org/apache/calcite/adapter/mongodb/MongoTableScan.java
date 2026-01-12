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
package org.apache.calcite.adapter.mongodb; // 声明包名，表示这个类属于 MongoDB 适配器包

import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式集群类，用于管理关系表达式树
import org.apache.calcite.plan.RelOptCost; // 导入关系表达式成本类，用于表示执行计划的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器类，用于执行查询优化
import org.apache.calcite.plan.RelOptRule; // 导入关系表达式优化规则类，定义转换规则
import org.apache.calcite.plan.RelOptTable; // 导入关系表达式表类，表示表元数据
import org.apache.calcite.plan.RelTraitSet; // 导入关系表达式特征集合类，定义物理属性
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口，所有关系节点的基类
import org.apache.calcite.rel.core.TableScan; // 导入表扫描基类，表示从表中读取数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询类，用于获取统计信息
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，表示行类型

import com.google.common.collect.ImmutableList; // 导入不可变列表类，用于创建不可修改的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，标记可能为 null 的参数

import java.util.List; // 导入列表接口

import static com.google.common.base.Preconditions.checkArgument; // 导入参数检查工具，用于验证参数合法性

import static java.util.Objects.requireNonNull; // 导入对象非空检查工具，确保对象不为 null

/**
 * Relational expression representing a scan of a MongoDB collection. // 表示 MongoDB 集合扫描的关系表达式
 * // 这个类是 Calcite 查询优化器中 MongoDB 适配器的核心组件之一
 * // 它代表从 MongoDB 集合中读取数据的物理算子
 * // 继承自 TableScan，表示这是一个表扫描操作
 * // 实现 MongoRel 接口，表示这是一个 MongoDB 特定的关系表达式
 *
 * <p> Additional operations might be applied, // 可能会应用额外的操作
 * using the "find" or "aggregate" methods. // 使用 MongoDB 的 find 或 aggregate 方法
 * // 在执行时，这个扫描操作会被转换为 MongoDB 的查询语句
 * // 可以根据需要添加投影、过滤等下推操作到 MongoDB 端
 */
public class MongoTableScan extends TableScan implements MongoRel { // 定义 MongoTableScan 类，继承 TableScan 并实现 MongoRel 接口
  final MongoTable mongoTable; // 成员变量：MongoDB 表对象，存储 MongoDB 集合的元数据和连接信息，final 表示不可变
  final @Nullable RelDataType projectRowType; // 成员变量：投影的行类型，如果为 null 则返回原始行，final 表示不可变，@Nullable 表示可以为 null

  /**
   * Creates a MongoTableScan. // 创建一个 MongoTableScan 实例
   * // 这是构造方法，用于初始化 MongoDB 表扫描节点
   * // 构造方法被 protected 修饰，表示只能通过工厂方法或子类创建
   *
   * @param cluster        Cluster // 参数：关系表达式集群，包含查询优化器的上下文信息
   * @param traitSet       Traits // 参数：特征集合，定义物理属性如约定（Convention）和排序等
   * @param table          Table // 参数：表对象，包含表的元数据信息如表名、字段等
   * @param mongoTable     MongoDB table // 参数：MongoDB 表对象，包含 MongoDB 特定的信息如集合名、连接配置等
   * @param projectRowType Fields and types to project; null to project raw row // 参数：投影的行类型，指定需要返回的字段及其类型，null 表示返回原始行
   */
  protected MongoTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法开始，接收集群对象和特征集合
      RelOptTable table, MongoTable mongoTable, // 接收表对象和 MongoDB 表对象
      @Nullable RelDataType projectRowType) { // 接收可空的投影行类型参数
    super(cluster, traitSet, ImmutableList.of(), table); // 调用父类 TableScan 的构造方法，传入集群、特征集、空列表和表对象
    this.mongoTable = requireNonNull(mongoTable, "mongoTable"); // 初始化 mongoTable 成员变量，使用 requireNonNull 确保不为 null，否则抛出 NullPointerException
    this.projectRowType = projectRowType; // 初始化 projectRowType 成员变量，可以为 null
    checkArgument(getConvention() == MongoRel.CONVENTION); // 检查特征集合中的约定是否为 MongoDB 约定，如果不是则抛出 IllegalArgumentException
  } // 构造方法结束

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写 copy 方法，用于复制关系表达式节点
    assert inputs.isEmpty(); // 断言输入列表为空，因为表扫描节点没有子节点
    return this; // 返回当前对象本身，因为 MongoTableScan 是不可变的，不需要创建新实例
  } // copy 方法结束

  @Override public RelDataType deriveRowType() { // 重写 deriveRowType 方法，用于推导此节点的输出行类型
    return projectRowType != null ? projectRowType : super.deriveRowType(); // 如果有投影行类型则返回投影类型，否则调用父类方法获取原始行类型
  } // deriveRowType 方法结束

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写 computeSelfCost 方法，用于计算此节点的执行成本
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于获取表的统计信息
    // scans with a small project list are cheaper // 注释：投影字段少的扫描成本更低
    final float f = // 定义浮点变量 f，用于根据投影字段数量调整成本系数
        projectRowType == null ? 1f // 如果没有投影（返回所有字段），则系数为 1.0
            : (float) projectRowType.getFieldCount() / 100f; // 如果有投影，则系数为字段数除以 100，字段越多系数越大
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq)); // 调用父类方法计算基础成本，使用 requireNonNull 确保结果不为 null
    return cost.multiplyBy(.1 * f); // 返回调整后的成本，乘以 0.1 和系数 f，使 MongoDB 扫描的成本相对较低
  } // computeSelfCost 方法结束

  @Override public void register(RelOptPlanner planner) { // 重写 register 方法，用于向优化器注册此节点相关的规则
    planner.addRule(MongoToEnumerableConverterRule.INSTANCE); // 向优化器添加 MongoDB 到可枚举转换规则，用于将 MongoDB 关系表达式转换为可执行代码
    for (RelOptRule rule : MongoRules.RULES) { // 遍历 MongoDB 规则集合中的所有规则
      planner.addRule(rule); // 将每个规则添加到优化器中，这些规则可以优化 MongoDB 查询计划
    } // for 循环结束
  } // register 方法结束

  @Override public void implement(Implementor implementor) { // 重写 implement 方法，用于实现此节点为 MongoDB 查询
    implementor.mongoTable = mongoTable; // 将 MongoDB 表对象设置到实现器中，用于生成查询语句
    implementor.table = table; // 将表对象设置到实现器中，用于获取表元数据
  } // implement 方法结束
} // MongoTableScan 类定义结束
