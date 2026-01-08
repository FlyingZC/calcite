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
// Apache许可证声明，说明代码版权和使用许可
package org.apache.calcite.adapter.cassandra; // 定义包名，此类属于Cassandra适配器包

import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群类，用于管理关系表达式的优化过程
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化规划器类，负责执行查询优化
import org.apache.calcite.plan.RelOptRule; // 导入关系优化规则类，定义查询转换规则
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表类，表示逻辑表
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合类，定义关系表达式的物理属性
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系表达式的基类
import org.apache.calcite.rel.core.TableScan; // 导入表扫描基类，表示从表中读取数据的操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，定义行或字段的类型

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，标记可能为null的字段

import java.util.List; // 导入Java集合框架的List接口

/**
 * Relational expression representing a scan of a Cassandra collection.
 * 表示扫描Cassandra集合（表）的关系表达式
 * 
 * 这个类是Calcite查询计划中的一个关键节点，负责表示对Cassandra表的扫描操作
 * 它继承自TableScan基类，实现了CassandraRel接口，是Cassandra适配器的核心组件
 * 在查询执行时，这个节点会被转换为实际的数据访问操作，从Cassandra数据库中读取数据
 * 
 * 核心功能：
 * 1. 表示对Cassandra表的扫描操作，是查询计划的叶子节点
 * 2. 支持投影操作，可以只选择特定的列
 * 3. 向查询优化器注册Cassandra相关的优化规则
 * 4. 实现查询执行接口，生成实际的数据访问代码
 * 
 * 在Calcite查询优化流程中的位置：
 * - 查询解析后生成逻辑计划，会创建TableScan节点
 * - 经过优化规则转换，可能被转换为其他节点（如CassandraProject）
 * - 最终转换为可执行代码，通过implement方法实现
 */
public class CassandraTableScan extends TableScan implements CassandraRel { // 定义Cassandra表扫描类，继承TableScan并实现CassandraRel接口
  final CassandraTable cassandraTable; // 成员变量：CassandraTable对象，表示被扫描的Cassandra表的元数据信息，包含表名、列信息、键空间等
  final @Nullable RelDataType projectRowType; // 成员变量：可空的关系数据类型，表示投影后的行类型，如果为null则表示扫描所有列

  /**
   * Creates a CassandraTableScan.
   * 创建一个Cassandra表扫描对象
   *
   * @param cluster        Cluster - 关系优化集群，包含查询优化所需的全局信息，如类型系统、表达式工厂等
   * @param traitSet       Traits - 关系特征集合，定义此节点的物理属性（如调用约定、排序等）
   * @param table          Table - 关系优化表，表示逻辑表的元数据信息
   * @param cassandraTable Cassandra table - Cassandra表的具体实现，包含Cassandra特有的元数据和配置
   * @param projectRowType Fields and types to project; null to project raw row - 要投影的字段和类型，null表示投影原始行（所有列）
   */
  protected CassandraTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：关系优化集群和关系特征集合
      RelOptTable table, CassandraTable cassandraTable, @Nullable RelDataType projectRowType) { // 构造方法参数：关系优化表、Cassandra表和投影行类型
    super(cluster, traitSet, ImmutableList.of(), table); // 调用父类TableScan的构造方法，传入集群、特征集、空输入列表和表
    this.cassandraTable = cassandraTable; // 保存Cassandra表对象的引用，用于后续访问Cassandra表的元数据
    this.projectRowType = projectRowType; // 保存投影行类型，用于确定扫描时需要读取哪些列

    assert getConvention() == CassandraRel.CONVENTION; // 断言检查：确保此节点的调用约定是Cassandra的调用约定，保证节点类型正确
  } // 构造方法结束

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于复制节点并应用新的特征集
    assert inputs.isEmpty(); // 断言检查：确保输入列表为空，因为TableScan是叶子节点，不应该有输入
    return this; // 返回当前对象，因为CassandraTableScan是不可变的，不需要创建新对象
  } // copy方法结束

  @Override public RelDataType deriveRowType() { // 重写deriveRowType方法，计算此节点的输出行类型
    return projectRowType != null ? projectRowType : super.deriveRowType(); // 如果投影行类型不为null则返回投影类型，否则调用父类方法获取原始行类型
  } // deriveRowType方法结束

  @Override public void register(RelOptPlanner planner) { // 重写register方法，向查询优化器注册Cassandra相关的优化规则
    planner.addRule(CassandraRules.TO_ENUMERABLE); // 注册Cassandra到可枚举转换规则，用于将Cassandra关系表达式转换为可执行的枚举表达式
    for (RelOptRule rule : CassandraRules.RULES) { // 遍历Cassandra规则集合中的所有规则
      planner.addRule(rule); // 将每个规则注册到查询优化器中，使优化器能够应用这些规则进行查询优化
    } // for循环结束
  } // register方法结束

  @Override public void implement(Implementor implementor) { // 重写implement方法，实现查询执行逻辑，生成实际的数据访问代码
    implementor.cassandraTable = cassandraTable; // 将Cassandra表对象设置到实现器中，用于生成数据访问代码
    implementor.table = table; // 将关系优化表设置到实现器中，用于获取表的元数据信息
  } // implement方法结束
} // CassandraTableScan类结束
