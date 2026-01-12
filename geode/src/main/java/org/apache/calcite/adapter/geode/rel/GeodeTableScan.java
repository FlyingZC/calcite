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
package org.apache.calcite.adapter.geode.rel; // 指定当前类所属的包，org.apache.calcite.adapter.geode.rel表示这是Calcite框架中Geode适配器的rel（关系表达式）包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，用于管理关系表达式和优化器
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示关系优化器，用于优化关系表达式树
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，表示优化规则，用于转换关系表达式
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化器中的表对象，包含表的元数据信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系特征集合，定义了关系表达式的物理属性（如调用约定、排序等）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式节点，是Calcite中所有关系操作的基础接口
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，表示表扫描操作，是关系表达式树的叶子节点
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，描述字段的数据类型信息

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，表示不可变的列表，用于创建固定的集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数或字段

import java.util.List; // 导入Java标准库的List接口，表示有序集合

import static com.google.common.base.Preconditions.checkArgument; // 静态导入Google Guava的checkArgument方法，用于验证方法参数的有效性

import static java.util.Objects.requireNonNull; // 静态导入Java的requireNonNull方法，用于验证对象不为null

/**
 * Relational expression representing a scan of a Geode collection.
 * 表示扫描Geode集合的关系表达式，这是Calcite框架中用于Apache Geode数据库适配器的表扫描节点
 * GeodeTableScan是关系表达式树的叶子节点，负责从Geode数据存储中读取数据
 * 它继承自TableScan基类，并实现了GeodeRel接口，表明它是一个Geode特定的关系操作
 */
public class GeodeTableScan extends TableScan implements GeodeRel { // 定义GeodeTableScan类，继承自TableScan（表扫描基类）并实现GeodeRel接口（Geode关系表达式接口）

  final GeodeTable geodeTable; // GeodeTable对象，表示Geode表的元数据和访问接口，包含表的结构信息、字段定义以及与Geode数据存储的连接信息
  final @Nullable RelDataType projectRowType; // 可选的投影行类型，用于指定需要从表中投影（选择）的字段及其类型；如果为null，则表示投影原始的完整行数据

  /**
   * Creates a GeodeTableScan.
   * 创建一个GeodeTableScan实例，用于表示对Geode表的扫描操作
   *
   * @param cluster        Cluster，关系表达式集群，包含类型系统和表达式工厂等共享资源
   * @param traitSet       Traits，关系特征集合，定义了此扫描操作的特征（如调用约定、物理属性等）
   * @param table          Table，优化器中的表对象，包含表的元数据信息
   * @param geodeTable     Geode table，Geode表对象，包含Geode特定的表信息和访问方法
   * @param projectRowType Fields and types to project; null to project raw row，要投影的字段和类型；如果为null，则投影原始行数据
   */
  GeodeTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：关系表达式集群和特征集合
      RelOptTable table, GeodeTable geodeTable, // 构造方法参数：优化器表对象和Geode表对象
      @Nullable RelDataType projectRowType) { // 构造方法参数：可选的投影行类型
    super(cluster, traitSet, ImmutableList.of(), table); // 调用父类TableScan的构造方法，传入集群、特征集、空输入列表和表对象
    this.geodeTable = requireNonNull(geodeTable, "geodeTable"); // 初始化geodeTable字段，使用requireNonNull确保geodeTable不为null，否则抛出NullPointerException
    this.projectRowType = projectRowType; // 初始化projectRowType字段，保存投影行类型（可能为null）

    checkArgument(getConvention() == GeodeRel.CONVENTION); // 验证调用约定（Convention）是否为GeodeRel.CONVENTION，确保此节点属于Geode调用约定，否则抛出IllegalArgumentException
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于创建此关系表达式的副本，传入新的特征集和输入列表
    assert inputs.isEmpty(); // 断言输入列表为空，因为TableScan是叶子节点，不应该有输入
    return this; // 返回this，因为GeodeTableScan是不可变的，不需要创建副本，直接返回当前实例
  }

  @Override public RelDataType deriveRowType() { // 重写deriveRowType方法，用于推导此扫描操作输出的行类型（即结果集的schema）
    return projectRowType != null ? projectRowType : super.deriveRowType(); // 如果projectRowType不为null，则返回投影行类型；否则调用父类的deriveRowType方法返回原始表的行类型
  }

  @Override public void register(RelOptPlanner planner) { // 重写register方法，向优化器注册此扫描节点相关的优化规则
    planner.addRule(GeodeToEnumerableConverterRule.INSTANCE); // 向优化器注册GeodeToEnumerableConverterRule实例，该规则用于将Geode关系表达式转换为可枚举的关系表达式（即可以执行的关系表达式）
    for (RelOptRule rule : GeodeRules.RULES) { // 遍历GeodeRules.RULES集合，该集合包含所有Geode相关的优化规则
      planner.addRule(rule); // 将每个规则注册到优化器中，使优化器在优化过程中可以使用这些规则来转换和优化关系表达式树
    }
  }

  @Override public void implement(GeodeImplementContext geodeImplementContext) { // 重写implement方法，用于实现此扫描节点，将关系表达式转换为可执行的Geode查询
    // Note: Scan is the leaf and we do NOT visit its inputs
    geodeImplementContext.geodeTable = geodeTable; // 将GeodeTable对象设置到实现上下文中，使后续的代码生成可以使用Geode表的元数据和访问方法
    geodeImplementContext.table = table; // 将优化器表对象设置到实现上下文中，使后续的代码生成可以使用表的元数据信息
  }
} // 类定义结束
