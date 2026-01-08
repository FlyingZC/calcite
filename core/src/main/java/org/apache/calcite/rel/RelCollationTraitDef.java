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
package org.apache.calcite.rel;

import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.logical.LogicalSort;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Definition of the ordering trait. // 排序特征的定义类，用于描述关系代数节点的排序属性
 *
 * <p>Ordering is a physical property (i.e. a trait) because it can be changed
 * without loss of information. The converter to do this is the
 * {@link org.apache.calcite.rel.core.Sort} operator. // 排序是一个物理属性（即特征），因为它可以在不丢失信息的情况下被改变，用于执行排序转换的操作符是Sort
 *
 * <p>Unlike other current traits, a {@link RelNode} can have more than one
 * value of this trait simultaneously. For example,
 * <code>LogicalTableScan(table=TIME_BY_DAY)</code> might be sorted by
 * <code>{the_year, the_month, the_date}</code> and also by
 * <code>{time_id}</code>. We have to allow a RelNode to belong to more than
 * one RelSubset (these RelSubsets are always in the same set). // 与其他当前特征不同，一个RelNode可以同时具有多个此特征的值，例如LogicalTableScan(table=TIME_BY_DAY)可能按{the_year, the_month, the_date}排序，也可能按{time_id}排序，我们必须允许一个RelNode属于多个RelSubset（这些RelSubset总是在同一个集合中）
 */
public class RelCollationTraitDef extends RelTraitDef<RelCollation> { // RelCollationTraitDef继承自RelTraitDef<RelCollation>，表示这是RelCollation（排序）特征的定义类，RelTraitDef是所有特征定义的基类
  public static final RelCollationTraitDef INSTANCE = // RelCollationTraitDef类的单例实例，使用单例模式确保整个系统中只有一个RelCollationTraitDef实例，所有需要使用RelCollation特征定义的地方都通过这个常量访问
      new RelCollationTraitDef(); // 调用私有构造函数创建RelCollationTraitDef实例

  private RelCollationTraitDef() { // 私有构造函数，防止外部创建新的实例，确保单例模式
  } // 构造函数体为空，因为这是一个简单的单例类，不需要初始化任何成员变量

  @Override public Class<RelCollation> getTraitClass() { // 重写父类方法，返回此特征定义所管理的特征类型（RelCollation类）
    return RelCollation.class; // 返回RelCollation.class，表示此特征定义管理的是RelCollation（排序）特征，RelCollation表示关系代数节点的排序属性
  }

  @Override public String getSimpleName() { // 重写父类方法，返回此特征的简单名称，用于日志输出和调试信息
    return "sort"; // 返回"sort"作为此特征的简单名称，表示这是一个排序特征
  }

  @Override public boolean multiple() { // 重写父类方法，判断一个RelNode是否可以同时具有多个此特征的值
    return true; // 返回true，表示一个RelNode可以同时具有多个排序特征值，例如一个表扫描可以按多个不同的字段组合排序，这与其他特征（如约定Convention）不同
  }

  @Override public RelCollation getDefault() { // 重写父类方法，返回此特征的默认值
    return RelCollations.EMPTY; // 返回RelCollations.EMPTY，表示默认的排序是空排序（即没有特定的排序要求），RelCollations.EMPTY是一个预定义的常量，表示不要求任何特定的排序
  }

  @Override public @Nullable RelNode convert( // 重写父类方法，将给定的RelNode从当前排序特征转换为目标排序特征，返回转换后的RelNode，如果无法转换则返回null
      RelOptPlanner planner, // 优化器规划器，用于注册新的RelNode和转换特征集
      RelNode rel, // 需要转换排序特征的原始关系代数节点
      RelCollation toCollation, // 目标排序特征，表示要将rel转换成的排序方式
      boolean allowInfiniteCostConverters) { // 是否允许使用无限成本的转换器，这个参数在此方法中未使用，但保留以保持接口一致性
    if (toCollation.getFieldCollations().isEmpty()) { // 检查目标排序是否为空排序（即没有排序字段）
      // An empty sort doesn't make sense. // 空排序没有意义，因为不需要排序
      return null; // 返回null表示无法进行转换
    } // 如果目标排序为空，直接返回null

    // Create a logical sort, then ask the planner to convert its remaining
    // traits (e.g. convert it to an EnumerableSortRel if rel is enumerable
    // convention) // 创建一个逻辑排序节点，然后要求规划器转换其剩余的特征（例如，如果rel是可枚举约定，则将其转换为EnumerableSortRel）
    final Sort sort = LogicalSort.create(rel, toCollation, null, null); // 创建一个LogicalSort节点，将rel作为输入，使用toCollation作为排序规则，null表示没有偏移量和限制（即不进行分页）
    RelNode newRel = planner.register(sort, rel); // 将新创建的sort节点注册到规划器中，rel作为父节点，规划器会处理特征集的转换和子集管理
    final RelTraitSet newTraitSet = rel.getTraitSet().replace(toCollation); // 创建新的特征集，将rel的原特征集中的排序特征替换为目标排序toCollation，其他特征保持不变
    if (!newRel.getTraitSet().equals(newTraitSet)) { // 检查新注册节点的特征集是否与期望的特征集一致
      newRel = planner.changeTraits(newRel, newTraitSet); // 如果不一致，要求规划器将newRel的特征集更改为newTraitSet，这可能会触发进一步的转换
    } // 如果特征集一致，则不需要更改
    return newRel; // 返回转换后的RelNode，这个节点具有目标排序特征toCollation
  }

  @Override public boolean canConvert( // 重写父类方法，判断是否可以从一个排序特征转换到另一个排序特征
      RelOptPlanner planner, RelCollation fromTrait, RelCollation toTrait) { // planner是优化器规划器，fromTrait是源排序特征，toTrait是目标排序特征，这三个参数在此方法中未使用，但保留以保持接口一致性
    return true; // 返回true，表示任何排序特征都可以转换到任何其他排序特征，因为可以通过添加Sort操作符来实现任意排序转换
  }
}
