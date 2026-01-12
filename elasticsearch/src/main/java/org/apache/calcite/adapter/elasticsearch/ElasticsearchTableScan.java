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
package org.apache.calcite.adapter.elasticsearch; // 声明该类所属的包，位于Elasticsearch适配器包下

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，包含类型工厂和表达式工厂
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost接口，表示关系操作的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，表示查询优化器
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，表示优化规则
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化器中的表抽象
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式树的节点
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，表示表扫描操作，是本类的父类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules类，包含Calcite核心优化规则
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或参数

import java.util.List; // 导入Java标准库的List接口，表示有序集合

import static java.util.Objects.requireNonNull; // 导入Objects类的requireNonNull静态方法，用于参数非空校验

/**
 * Relational expression representing a scan of an Elasticsearch type. // 表示扫描Elasticsearch类型的关系表达式
 *
 * <p> Additional operations might be applied, // 可以应用额外的操作
 * using the "find" method. // 使用"find"方法
 */
public class ElasticsearchTableScan extends TableScan implements ElasticsearchRel { // 定义ElasticsearchTableScan类，继承TableScan并实现ElasticsearchRel接口
  private final ElasticsearchTable elasticsearchTable; // 成员变量：Elasticsearch表对象，存储了Elasticsearch表的元数据和配置信息，包括索引名称、字段映射等
  private final @Nullable RelDataType projectRowType; // 成员变量：投影的行类型，如果为null则投影原始行，否则只投影指定的字段和类型，用于优化查询性能

  /**
   * Creates an ElasticsearchTableScan. // 创建一个ElasticsearchTableScan实例
   *
   * @param cluster Cluster // 关系表达式集群，包含类型工厂和表达式工厂等共享资源
   * @param traitSet Trait set // 关系表达式的特征集合，例如约定(convention)、排序(collation)等
   * @param table Table // 优化器中的表抽象，包含表的元数据信息
   * @param elasticsearchTable Elasticsearch table // Elasticsearch表对象，包含索引名称、字段映射等具体信息
   * @param projectRowType Fields and types to project; null to project raw row // 要投影的字段和类型，如果为null则投影原始行
   */
  ElasticsearchTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：集群和特征集
       RelOptTable table, ElasticsearchTable elasticsearchTable, // 构造方法参数：表和Elasticsearch表对象
       @Nullable RelDataType projectRowType) { // 构造方法参数：投影行类型，可能为null
    super(cluster, traitSet, ImmutableList.of(), table); // 调用父类TableScan的构造方法，传入集群、特征集、空输入列表和表
    this.elasticsearchTable = // 初始化成员变量elasticsearchTable
        requireNonNull(elasticsearchTable, "elasticsearchTable"); // 使用requireNonNull确保elasticsearchTable不为null，否则抛出NullPointerException
    this.projectRowType = projectRowType; // 初始化成员变量projectRowType，可能为null

    assert getConvention() == ElasticsearchRel.CONVENTION; // 断言当前关系的约定是Elasticsearch约定，确保在正确的约定下执行
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于复制关系表达式，传入新的特征集和输入列表
    assert inputs.isEmpty(); // 断言输入列表为空，因为TableScan是叶子节点，没有输入
    return this; // 返回this，因为ElasticsearchTableScan是不可变的，不需要创建新实例
  }

  @Override public RelDataType deriveRowType() { // 重写deriveRowType方法，用于推导此关系表达式输出的行类型
    return projectRowType != null ? projectRowType : super.deriveRowType(); // 如果projectRowType不为null则返回它，否则调用父类的deriveRowType方法返回原始行类型
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，用于计算此关系表达式自身的成本，传入优化器和元数据查询对象
      RelMetadataQuery mq) { // 元数据查询对象，用于查询关系的元数据信息
    final float f = projectRowType == null ? 1f : (float) projectRowType.getFieldCount() / 100f; // 计算字段数因子：如果projectRowType为null则因子为1，否则为字段数除以100，用于调整成本
    final RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类的computeSelfCost方法获取基础成本
    return requireNonNull(cost, "cost").multiplyBy(.1 * f); // 确保cost不为null，然后乘以0.1和字段数因子，返回调整后的成本，使Elasticsearch扫描更便宜
  }

  @Override public void register(RelOptPlanner planner) { // 重写register方法，用于向优化器注册相关的优化规则
    planner.addRule(ElasticsearchToEnumerableConverterRule.INSTANCE); // 向优化器添加Elasticsearch到可枚举的转换规则实例，用于将Elasticsearch关系表达式转换为可枚举的关系表达式
    for (RelOptRule rule : ElasticsearchRules.RULES) { // 遍历ElasticsearchRules中的所有规则
      planner.addRule(rule); // 向优化器添加每个Elasticsearch规则，用于优化Elasticsearch查询
    }

    // remove this rule otherwise elastic can't correctly interpret approx_count_distinct() // 移除此规则，否则Elasticsearch无法正确解释approx_count_distinct()函数
    // it is converted to cardinality aggregation in Elastic // 在Elasticsearch中它被转换为cardinality聚合
    planner.removeRule(CoreRules.AGGREGATE_EXPAND_DISTINCT_AGGREGATES); // 从优化器中移除聚合展开不同聚合的规则，因为Elasticsearch有自己的处理方式
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法，用于实现此关系表达式，传入Implementor对象用于收集实现信息
    implementor.elasticsearchTable = elasticsearchTable; // 将Elasticsearch表对象设置到implementor中，用于后续生成Elasticsearch查询
    implementor.table = table; // 将表对象设置到implementor中，用于后续处理表相关信息
  }
} // 类定义结束
