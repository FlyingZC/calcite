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
package org.apache.calcite.rel; // 声明包名，表示该类属于org.apache.calcite.rel包，这是Calcite中关系表达式(RelNode)的核心包

import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，这是Calcite的优化器接口，负责执行查询优化过程
import org.apache.calcite.plan.RelTraitDef; // 导入RelTraitDef类，这是关系特质定义的基类，所有特质定义都需要继承此类
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，这是关系特质集合，用于存储和管理关系节点的多个特质
import org.apache.calcite.rel.core.Exchange; // 导入Exchange类，这是用于改变数据分布的物理算子，可以通过数据重分布来改变RelDistribution特质
import org.apache.calcite.rel.logical.LogicalExchange; // 导入LogicalExchange类，这是Exchange算子的逻辑表示，用于在逻辑规划阶段表示数据重分布操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值或参数

/**
 * Definition of the distribution trait. // 定义数据分布特质，该类是RelDistribution特质的定义类，负责描述和管理数据分布属性
 *
 * <p>Distribution is a physical property (i.e. a trait) because it can be // 数据分布是一个物理属性（即特质），因为它可以在不丢失信息的情况下被改变
 * changed without loss of information. The converter to do this is the // 执行这种转换的转换器是Exchange算子，它可以在保持数据完整性的前提下重新分布数据
 * {@link Exchange} operator. // Exchange算子是专门用于数据重分布的物理操作符
 */
public class RelDistributionTraitDef extends RelTraitDef<RelDistribution> { // RelDistributionTraitDef类继承自RelTraitDef<RelDistribution>，表示这是RelDistribution特质的定义类，泛型参数RelDistribution表示该特质定义管理的特质类型
  public static final RelDistributionTraitDef INSTANCE = // 定义一个静态常量INSTANCE，这是RelDistributionTraitDef的单例实例，采用单例模式确保全局只有一个特质定义实例
      new RelDistributionTraitDef(); // 通过私有构造函数创建RelDistributionTraitDef的实例并赋值给INSTANCE常量

  private RelDistributionTraitDef() { // 私有构造函数，防止外部创建实例，确保单例模式的实现
  } // 构造函数为空，因为特质定义不需要初始化任何状态

  @Override public Class<RelDistribution> getTraitClass() { // 重写父类方法，返回该特质定义所管理的特质类，即RelDistribution.class
    return RelDistribution.class; // 返回RelDistribution类的Class对象，告诉优化器这个特质定义管理的是RelDistribution类型的特质
  } // 该方法用于在运行时识别特质类型，是特质定义的核心方法之一

  @Override public String getSimpleName() { // 重写父类方法，返回该特质的简单名称，用于日志输出和调试信息
    return "dist"; // 返回字符串"dist"作为RelDistribution特质的简单名称，"dist"是distribution的缩写
  } // 这个简单名称用于在优化器日志、错误信息和调试输出中标识该特质

  @Override public RelDistribution getDefault() { // 重写父类方法，返回该特质的默认值，当没有指定分布策略时使用
    return RelDistributions.ANY; // 返回RelDistributions.ANY作为默认分布，ANY表示任意分布，即不强制要求特定的分布方式
  } // 默认值为ANY意味着优化器可以自由选择最优的数据分布策略，不受分布约束的限制

  @Override public @Nullable RelNode convert(RelOptPlanner planner, RelNode rel, // 重写父类方法，将给定的关系节点转换为具有目标分布特质的新节点，@Nullable表示可能返回null
      RelDistribution toDistribution, boolean allowInfiniteCostConverters) { // toDistribution参数指定目标分布特质，allowInfiniteCostConverters参数表示是否允许无限成本的转换器
    if (toDistribution == RelDistributions.ANY) { // 检查目标分布是否为ANY（任意分布），ANY是最宽松的分布要求
      return rel; // 如果目标分布是ANY，则不需要进行任何转换，直接返回原始关系节点，因为任何分布都满足ANY的要求
    } // 这种优化避免了不必要的Exchange操作，提高了查询效率

    // Create a logical sort, then ask the planner to convert its remaining // 注释说明：创建一个逻辑Exchange，然后请求优化器转换其剩余的特质（例如，如果rel是可枚举约定，则将其转换为EnumerableSortRel）
    // traits (e.g. convert it to an EnumerableSortRel if rel is enumerable // 这里注释有误，应该说是创建逻辑Exchange而不是逻辑排序，Exchange用于数据重分布
    // convention) // 注释说明示例：如果rel使用的是可枚举约定，则将Exchange转换为相应的可枚举实现
    final Exchange exchange = LogicalExchange.create(rel, toDistribution); // 创建一个逻辑Exchange节点，该节点将重新分布rel的数据以满足toDistribution的要求，LogicalExchange是数据重分布的逻辑表示
    RelNode newRel = planner.register(exchange, rel); // 将新创建的Exchange节点注册到优化器中，planner.register方法会处理节点的注册和可能的规范化，rel作为输入节点
    final RelTraitSet newTraitSet = rel.getTraitSet().replace(toDistribution); // 创建新的特质集合，将原始节点的特质集合中的分布特质替换为目标分布特质toDistribution，其他特质保持不变
    if (!newRel.getTraitSet().equals(newTraitSet)) { // 检查新节点的特质集合是否与期望的特质集合相等，如果不相等则需要进行特质转换
      newRel = planner.changeTraits(newRel, newTraitSet); // 调用优化器的changeTraits方法，将newRel的特质集合转换为newTraitSet，这可能涉及进一步的规则应用和节点转换
    } // 这一步确保最终返回的节点的特质集合完全符合要求
    return newRel; // 返回转换后的关系节点，该节点具有目标分布特质toDistribution
  } // 该方法是特质转换的核心实现，通过插入Exchange节点来改变数据分布，是物理属性转换的关键

  @Override public boolean canConvert(RelOptPlanner planner, RelDistribution fromTrait, // 重写父类方法，判断是否可以从源分布特质转换为目标分布特质，planner参数提供优化器上下文
      RelDistribution toTrait) { // fromTrait参数表示源分布特质，toTrait参数表示目标分布特质
    return true; // 返回true表示任何分布特质之间都可以相互转换，因为Exchange算子可以实现任意分布之间的转换
  } // 这意味着优化器总是可以通过添加Exchange节点来改变数据分布，虽然可能会有性能开销，但在功能上总是可行的
} // RelDistributionTraitDef类结束，该类完整定义了RelDistribution特质的语义、转换规则和默认值
