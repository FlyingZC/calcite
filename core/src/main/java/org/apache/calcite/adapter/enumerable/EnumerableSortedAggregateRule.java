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
package org.apache.calcite.adapter.enumerable; // 声明包名，此类属于org.apache.calcite.adapter.enumerable包，用于可枚举适配器相关功能

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数算子的调用约定（物理实现方式）
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系节点的特征集合（如排序、分区等物理属性）
import org.apache.calcite.rel.RelCollations; // 导入RelCollations类，用于创建和操作排序规范（Collation，即字段的排序顺序）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数树中的一个节点，是所有关系节点的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule抽象类，用于定义将一种关系节点转换为另一种关系节点的规则
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类，表示聚合操作（如GROUP BY、SUM、COUNT等）的关系节点
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入LogicalAggregate类，表示逻辑层面的聚合操作节点
import org.apache.calcite.util.ImmutableIntList; // 导入ImmutableIntList类，用于表示不可变的整数列表，常用于表示字段索引集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

/**
 * Rule to convert a {@link LogicalAggregate} to an {@link EnumerableSortedAggregate}.
 * 将逻辑聚合节点（LogicalAggregate）转换为可枚举排序聚合节点（EnumerableSortedAggregate）的规则
 * 此规则适用于输入数据已经按照分组列排序的情况，可以利用预排序来优化聚合操作
 * You may provide a custom config to convert other nodes that extend {@link Aggregate}.
 * 您可以提供自定义配置来转换其他继承自Aggregate的节点
 *
 * @see EnumerableRules#ENUMERABLE_SORTED_AGGREGATE_RULE
 */
class EnumerableSortedAggregateRule extends ConverterRule { // 定义类EnumerableSortedAggregateRule，继承自ConverterRule，用于聚合节点的转换规则
  /** Default configuration. */
  /** 默认配置，定义了此转换规则的默认行为和参数 */
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 获取ConverterRule的默认配置实例
      .withConversion(LogicalAggregate.class, Convention.NONE, // 配置转换规则：源节点类型为LogicalAggregate，源调用约定为NONE（逻辑层）
          EnumerableConvention.INSTANCE, "EnumerableSortedAggregateRule") // 目标调用约定为EnumerableConvention.INSTANCE（可枚举物理实现），规则描述为"EnumerableSortedAggregateRule"
      .withRuleFactory(EnumerableSortedAggregateRule::new); // 设置规则工厂，使用方法引用创建EnumerableSortedAggregateRule实例

  /** Called from the Config. */
  /** 构造方法，从Config对象创建规则实例，由配置对象调用 */
  protected EnumerableSortedAggregateRule(Config config) { // 受保护的构造方法，接收Config参数
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象
  }

  @Override public @Nullable RelNode convert(RelNode rel) { // 重写convert方法，用于将输入的RelNode转换为目标类型的RelNode，@Nullable表示可能返回null
    final Aggregate agg = (Aggregate) rel; // 将输入的RelNode强转为Aggregate类型，因为此规则只处理聚合节点
    if (!Aggregate.isSimple(agg)) { // 检查聚合是否为简单聚合（不包含GROUPING SETS、CUBE、ROLLUP等复杂分组）
      return null; // 如果不是简单聚合，返回null表示此规则不适用，让其他规则尝试转换
    }
    final RelTraitSet inputTraits = rel.getCluster() // 获取输入节点的关系特征集合，用于定义输入节点的物理属性
        .traitSet().replace(EnumerableConvention.INSTANCE) // 将调用约定替换为EnumerableConvention.INSTANCE（可枚举约定）
        .replace( // 替换排序特征，定义输入数据的排序顺序
            RelCollations.of( // 创建排序规范对象
                ImmutableIntList.copyOf( // 将分组列索引转换为不可变整数列表
            agg.getGroupSet().asList()))); // 获取分组列的索引集合（ImmutableBitSet）并转换为列表
    final RelTraitSet selfTraits = // 创建当前节点（EnumerableSortedAggregate）的特征集合
        inputTraits.replace( // 基于输入特征集合进行替换
            RelCollations.of( // 创建新的排序规范
                ImmutableIntList.identity(agg.getGroupSet().cardinality()))); // 创建从0到分组列数量-1的索引序列，表示输出结果的分组顺序
    return new EnumerableSortedAggregate( // 创建并返回EnumerableSortedAggregate节点实例
        rel.getCluster(), // 传入关系集群（包含类型系统、元数据等共享信息）
        selfTraits, // 传入当前节点的特征集合（包括调用约定和排序规范）
        convert(agg.getInput(), inputTraits), // 递归转换输入节点，使其满足inputTraits定义的物理属性
        agg.getGroupSet(), // 传入分组列集合（ImmutableBitSet，表示哪些字段用于分组）
        agg.getGroupSets(), // 传入分组集合列表（用于GROUPING SETS，简单聚合时只有一个）
        agg.getAggCallList()); // 传入聚合函数调用列表（如SUM、COUNT、AVG等）
  }
}
