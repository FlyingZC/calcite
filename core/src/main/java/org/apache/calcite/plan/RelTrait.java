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
package org.apache.calcite.plan; // 定义包名为 org.apache.calcite.plan，表示这个类属于 Calcite 查询优化器的计划包

import org.apache.calcite.rel.RelDistributions; // 导入 RelDistributions 类，用于处理数据分布相关的特性
import org.apache.calcite.rel.core.Project; // 导入 Project 类，用于投影操作，在 apply 方法中需要用到
import org.apache.calcite.util.mapping.Mappings; // 导入 Mappings 类，用于处理字段映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记参数可能为 null

/**
 * RelTrait represents the manifestation of a relational expression trait within
 * a trait definition. For example, a {@code CallingConvention.JAVA} is a trait
 * of the {@link ConventionTraitDef} trait definition.
 * RelTrait 表示关系表达式特性在特性定义中的具体表现形式。例如，CallingConvention.JAVA 是 ConventionTraitDef 特性定义的一个特性实例。
 *
 * <h2><a id="EqualsHashCodeNote">Note about equals() and hashCode()</a></h2>
 * <h2><a id="EqualsHashCodeNote">关于 equals() 和 hashCode() 的注意事项</a></h2>
 *
 * <p>If all instances of RelTrait for a particular RelTraitDef are defined in
 * an {@code enum} and no new RelTraits can be introduced at runtime, you need
 * not override {@link #hashCode()} and {@link #equals(Object)}. If, however,
 * new RelTrait instances are generated at runtime (e.g. based on state external
 * to the planner), you must implement {@link #hashCode()} and
 * {@link #equals(Object)} for proper {@link RelTraitDef#canonize canonization}
 * of your RelTrait objects.
 * 如果某个 RelTraitDef 的所有 RelTrait 实例都在枚举中定义，并且在运行时不能引入新的 RelTrait，则不需要重写 hashCode() 和 equals() 方法。
 * 但是，如果在运行时生成新的 RelTrait 实例（例如基于优化器外部的状态），则必须实现 hashCode() 和 equals() 方法，以便对 RelTrait 对象进行正确的规范化处理。
 */
public interface RelTrait { // 定义 RelTrait 接口，这是所有关系表达式特性（如约定、排序、分布等）的基接口
  //~ Methods ---------------------------------------------------------------- // 方法分隔符，表示下面是方法的定义部分

  /**
   * Returns the RelTraitDef that defines this RelTrait.
   * 返回定义此 RelTrait 的 RelTraitDef（特性定义）。
   *
   * @return the RelTraitDef that defines this RelTrait
   * 返回定义此 RelTrait 的 RelTraitDef 对象
   */
  RelTraitDef getTraitDef(); // 声明方法，返回定义此特性的特性定义对象，用于标识特性所属的类型

  /**
   * See <a href="#EqualsHashCodeNote">note about equals() and hashCode()</a>.
   * 参见关于 equals() 和 hashCode() 的注意事项。
   */
  @Override int hashCode(); // 声明 hashCode 方法，重写 Object 类的 hashCode，用于哈希表等数据结构

  /**
   * See <a href="#EqualsHashCodeNote">note about equals() and hashCode()</a>.
   * 参见关于 equals() 和 hashCode() 的注意事项。
   */
  @Override boolean equals(@Nullable Object o); // 声明 equals 方法，重写 Object 类的 equals，用于比较两个特性是否相等，参数可能为 null

  /**
   * Returns whether this trait satisfies a given trait.
   * 返回此特性是否满足给定的特性。
   *
   * <p>A trait satisfies another if it is the same or stricter. For example,
   * {@code ORDER BY x, y} satisfies {@code ORDER BY x}.
   * 如果一个特性与另一个特性相同或更严格，则满足另一个特性。例如，ORDER BY x, y 满足 ORDER BY x。
   *
   * <p>A trait's {@code satisfies} relation must be a partial order (reflexive,
   * anti-symmetric, transitive). Many traits cannot be "loosened"; their
   * {@code satisfies} is an equivalence relation, where only X satisfies X.
   * 特性的 satisfies 关系必须是偏序关系（自反、反对称、传递）。许多特性不能"放宽"；它们的 satisfies 是等价关系，只有 X 满足 X。
   *
   * <p>If a trait has multiple values
   * (see {@link org.apache.calcite.plan.RelCompositeTrait})
   * a collection (T0, T1, ...) satisfies T if any Ti satisfies T.
   * 如果一个特性有多个值（参见 RelCompositeTrait），则集合 (T0, T1, ...) 满足 T 当且仅当任意 Ti 满足 T。
   *
   * @param trait Given trait
   * 给定的特性对象
   * @return Whether this trait subsumes a given trait
   * 返回此特性是否包含（满足）给定的特性
   */
  boolean satisfies(RelTrait trait); // 声明 satisfies 方法，判断当前特性是否满足给定的特性，用于特性转换和匹配

  /**
   * Returns a succinct name for this trait. The planner may use this String
   * to describe the trait.
   * 返回此特性的简洁名称。优化器可以使用此字符串来描述该特性。
   */
  @Override String toString(); // 声明 toString 方法，重写 Object 类的 toString，返回特性的字符串表示

  /**
   * Registers a trait instance with the planner.
   * 向优化器注册特性实例。
   *
   * <p>This is an opportunity to add rules that relate to that trait. However,
   * typical implementations will do nothing.
   * 这是添加与该特性相关的规则的机会。但是，典型的实现不会做任何操作。
   *
   * @param planner Planner
   * 优化器对象
   */
  void register(RelOptPlanner planner); // 声明 register 方法，向优化器注册此特性，用于注册相关的转换规则

  /**
   * Applies a mapping to this trait.
   * 对此特性应用映射。
   *
   * <p>Some traits may be changed if the columns order is changed by a mapping
   * of the {@link Project} operator.
   * 如果通过 Project 操作符的映射改变了列的顺序，某些特性可能会发生变化。
   *
   * <p>For example, if relation {@code SELECT a, b ORDER BY a, b} is sorted by
   * columns [0, 1], then the project {@code SELECT b, a} over this relation
   * will be sorted by columns [1, 0]. In the same time project {@code SELECT b}
   * will not be sorted at all because it doesn't contain the collation
   * prefix and this method will return an empty collation.
   * 例如，如果关系 SELECT a, b ORDER BY a, b 按列 [0, 1] 排序，那么在此关系上的投影 SELECT b, a 将按列 [1, 0] 排序。
   * 同时，投影 SELECT b 将完全不排序，因为它不包含排序前缀，此方法将返回空的排序特性。
   *
   * <p>Other traits are independent from the columns remapping. For example
   * {@link Convention} or {@link RelDistributions#SINGLETON}.
   * 其他特性与列重新映射无关。例如 Convention（约定）或 RelDistributions#SINGLETON（单例分布）。
   *
   * @param mapping   Mapping
   * 目标映射对象，描述字段之间的映射关系
   * @return trait with mapping applied
   * 返回应用映射后的特性对象
   */
  default <T extends RelTrait> T apply(Mappings.TargetMapping mapping) { // 声明 apply 默认方法，将映射应用到特性上，处理列顺序变化对特性的影响
    return (T) this; // 默认实现返回 this，表示大多数特性不受列映射影响
  } // apply 方法结束

  /**
   * Returns whether this trait is the default trait value.
   * 返回此特性是否为默认特性值。
   */
  default boolean isDefault() { // 声明 isDefault 默认方法，判断当前特性是否为该特性定义的默认值
    return this == getTraitDef().getDefault(); // 通过与特性定义的默认值比较来判断
  } // isDefault 方法结束
} // RelTrait 接口定义结束
