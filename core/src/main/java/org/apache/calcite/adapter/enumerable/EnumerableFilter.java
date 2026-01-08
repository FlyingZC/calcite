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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite框架中可枚举适配器包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系表达式的集群，包含类型系统、元数据查询等共享信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系表达式的特征集合，如调用约定、排序规则、分布方式等
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，用于表示关系表达式的排序规则（即字段排序顺序）
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，用于定义排序规则特征
import org.apache.calcite.rel.RelCollations; // 导入RelCollations类，提供创建和操作排序规则的静态工具方法
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，用于定义数据分布特征（如数据如何分布到不同节点）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，这是Calcite中所有关系表达式（如表扫描、过滤、投影等）的基类
import org.apache.calcite.rel.core.Filter; // 导入Filter类，这是Calcite中过滤操作符的基类，表示SQL中的WHERE子句
import org.apache.calcite.rel.metadata.RelMdCollation; // 导入RelMdCollation类，用于计算关系表达式的排序规则元数据
import org.apache.calcite.rel.metadata.RelMdDistribution; // 导入RelMdDistribution类，用于计算关系表达式的数据分布元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据信息
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式（Row Expression），即Calcite中的表达式树节点
import org.apache.calcite.util.Pair; // 导入Pair类，用于表示键值对，常用于返回两个相关值

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可能为null的值，帮助静态分析工具进行空值检查

import java.util.List; // 导入Java标准库的List接口，用于表示有序集合

/** Implementation of {@link org.apache.calcite.rel.core.Filter} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */ // 类注释：这是Filter操作符在可枚举调用约定（EnumerableConvention）下的实现
public class EnumerableFilter // 定义类名EnumerableFilter，表示可枚举的过滤操作符
    extends Filter // 继承自Filter基类，继承了过滤操作符的基本功能和属性
    implements EnumerableRel { // 实现EnumerableRel接口，表示该关系表达式可以被转换为可枚举的Java代码
  /** Creates an EnumerableFilter. // 方法注释：创建一个EnumerableFilter实例
   *
   * <p>Use {@link #create} unless you know what you're doing. */ // 建议使用create静态工厂方法，除非你明确知道自己在做什么
  public EnumerableFilter( // 定义构造方法，用于创建EnumerableFilter实例
      RelOptCluster cluster, // 参数cluster：关系表达式集群，包含类型系统、元数据查询等共享信息
      RelTraitSet traitSet, // 参数traitSet：特征集合，定义该关系表达式的各种特征（如调用约定、排序、分布等）
      RelNode child, // 参数child：子关系表达式（即输入数据源），Filter会对这个子节点的输出进行过滤
      RexNode condition) { // 参数condition：过滤条件，是一个RexNode表达式树，表示SQL中的WHERE条件
    super(cluster, traitSet, child, condition); // 调用父类Filter的构造方法，初始化继承的属性
    assert getConvention() instanceof EnumerableConvention; // 断言：确保调用约定是EnumerableConvention类型，如果不是则抛出异常
  }

  /** Creates an EnumerableFilter. */ // 方法注释：创建一个EnumerableFilter实例的静态工厂方法（推荐使用）
  public static EnumerableFilter create(final RelNode input, // 定义静态工厂方法，参数input：输入的关系表达式（即子节点）
      RexNode condition) { // 参数condition：过滤条件表达式
    final RelOptCluster cluster = input.getCluster(); // 获取输入节点的集群信息，包含类型系统、元数据查询等共享资源
    final RelMetadataQuery mq = cluster.getMetadataQuery(); // 从集群中获取元数据查询对象，用于查询关系表达式的元数据信息
    final RelTraitSet traitSet = // 创建特征集合，定义该Filter节点的各种特征
        cluster.traitSetOf(EnumerableConvention.INSTANCE) // 首先设置调用约定为EnumerableConvention，表示这是可枚举的实现
            .replaceIfs( // 条件性地替换排序规则特征
                RelCollationTraitDef.INSTANCE, // 指定要替换的特征是排序规则
                () -> RelMdCollation.filter(mq, input)) // 使用Lambda表达式，从子节点的元数据中推导出Filter后的排序规则
            .replaceIf(RelDistributionTraitDef.INSTANCE, // 条件性地替换数据分布特征
                () -> RelMdDistribution.filter(mq, input)); // 使用Lambda表达式，从子节点的元数据中推导出Filter后的数据分布规则
    return new EnumerableFilter(cluster, traitSet, input, condition); // 创建并返回新的EnumerableFilter实例
  }

  @Override public EnumerableFilter copy(RelTraitSet traitSet, RelNode input, // 重写copy方法，用于创建该Filter节点的副本，但可以替换某些属性
      RexNode condition) { // 参数condition：新的过滤条件
    return new EnumerableFilter(getCluster(), traitSet, input, condition); // 创建并返回新的EnumerableFilter实例，使用新的特征集合、输入节点和条件
  }

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写implement方法，用于将此关系表达式转换为可枚举的Java代码实现
    // EnumerableCalc is always better // 注释：EnumerableCalc总是比EnumerableFilter更好，因为Calc可以同时实现过滤和投影
    throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为实际的过滤实现会由EnumerableCalc来处理
  }

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> passThroughTraits( // 重写passThroughTraits方法，用于判断是否可以将父节点要求的特征直接传递给子节点
      RelTraitSet required) { // 参数required：父节点要求的特征集合
    RelCollation collation = required.getCollation(); // 从要求的特征集合中提取排序规则
    if (collation == null || collation == RelCollations.EMPTY) { // 如果排序规则为空或者是空的排序规则
      return null; // 返回null，表示不需要传递特征
    }
    RelTraitSet traits = traitSet.replace(collation); // 创建新的特征集合，将当前的排序规则替换为要求的排序规则
    return Pair.of(traits, ImmutableList.of(traits)); // 返回一个Pair，第一个元素是当前节点应该使用的特征集合，第二个元素是子节点可以使用的特征集合列表
  }

  @Override public @Nullable Pair<RelTraitSet, List<RelTraitSet>> deriveTraits( // 重写deriveTraits方法，用于从子节点的特征推导出当前节点应该使用的特征
      final RelTraitSet childTraits, // 参数childTraits：子节点的特征集合
      final int childId) { // 参数childId：子节点的ID（用于标识是哪个子节点）
    RelCollation collation = childTraits.getCollation(); // 从子节点的特征集合中提取排序规则
    if (collation == null || collation == RelCollations.EMPTY) { // 如果子节点的排序规则为空或者是空的排序规则
      return null; // 返回null，表示无法推导特征
    }
    RelTraitSet traits = traitSet.replace(collation); // 创建新的特征集合，将当前的排序规则替换为子节点的排序规则（因为Filter不改变数据的排序）
    return Pair.of(traits, ImmutableList.of(traits)); // 返回一个Pair，第一个元素是当前节点应该使用的特征集合，第二个元素是子节点可以使用的特征集合列表
  }
}
