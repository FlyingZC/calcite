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
// Apache许可证声明，说明代码遵循Apache 2.0许可证
package org.apache.calcite.plan; // 包声明，该类属于org.apache.calcite.plan包，是Calcite查询优化器规划相关的包

import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数表达式节点，是Calcite中关系表达式的基础接口
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，表示转换规则，用于将一个RelTrait转换为另一个RelTrait

import com.google.common.collect.Interner; // 导入Google Guava的Interner接口，用于对象规范化（规范化是指确保相等的对象只有一个实例）
import com.google.common.collect.Interners; // 导入Google Guava的Interners工具类，用于创建Interner实例

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

/**
 * RelTraitDef represents a class of {@link RelTrait}s. Implementations of
 * RelTraitDef may be singletons under the following conditions:
 * RelTraitDef表示一类RelTrait（关系特征）的定义。RelTraitDef的实现类在满足以下条件时可以是单例：
 *
 * <ol>
 * <li>if the set of all possible associated RelTraits is finite and fixed (e.g.
 * all RelTraits for this RelTraitDef are known at compile time). For example,
 * the CallingConvention trait meets this requirement, because CallingConvention
 * is effectively an enumeration.</li>
 * 1. 如果所有可能的关联RelTraits集合是有限且固定的（例如，该RelTraitDef的所有RelTraits在编译时都是已知的）。
 *    例如，CallingConvention（调用约定）特征满足此要求，因为CallingConvention实际上是一个枚举。
 * <li>Either
 * 2. 或者满足以下任一条件：
 *
 * <ul>
 * <li> {@link #canConvert(RelOptPlanner, RelTrait, RelTrait)} and
 * {@link #convert(RelOptPlanner, RelNode, RelTrait, boolean)} do not require
 * planner-instance-specific information, <b>or</b></li>
 * 2.1 {@link #canConvert(RelOptPlanner, RelTrait, RelTrait)}和
 *     {@link #convert(RelOptPlanner, RelNode, RelTrait, boolean)}方法不需要特定于规划器实例的信息，<b>或者</b>
 *
 * <li>the RelTraitDef manages separate sets of conversion data internally. See
 * {@link ConventionTraitDef} for an example of this.</li>
 * 2.2 RelTraitDef在内部管理独立的转换数据集合。可以参考{@link ConventionTraitDef}作为示例。
 * </ul>
 * </li>
 * </ol>
 *
 * <p>Otherwise, a new instance of RelTraitDef must be constructed and
 * registered with each new planner instantiated.
 * 否则，必须为每个新实例化的规划器构造并注册一个新的RelTraitDef实例。
 *
 * @param <T> Trait that this trait definition is based upon
 * @param <T> 泛型参数，表示该特征定义所基于的Trait类型，必须是RelTrait的子类
 */
public abstract class RelTraitDef<T extends RelTrait> { // 抽象类定义，T是RelTrait的子类型，表示该特征定义管理的特征类型
  //~ Instance fields --------------------------------------------------------
  // 实例字段区域标记

  /**
   * Cache of traits.
   * 特征缓存，用于存储规范化的特征对象
   *
   * <p>Uses weak interner to allow GC.
   * 使用弱引用的规范化器（Weak Interner），允许垃圾回收器回收不再使用的特征对象
   */
  private final Interner<T> interner = Interners.newWeakInterner(); // 创建一个弱引用的Interner实例，用于规范化特征对象，确保相等的特征只有一个实例

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域标记

  protected RelTraitDef() { // 受保护的默认构造方法，允许子类实例化
  } // 构造方法体为空，因为该类主要是抽象方法定义

  //~ Methods ----------------------------------------------------------------
  // 方法区域标记

  /**
   * Whether a relational expression may possess more than one instance of
   * this trait simultaneously.
   * 判断一个关系表达式是否可以同时拥有该特征的多个实例
   *
   * <p>A subset has only one instance of a trait.
   * 子集（Subset）只能有一个特征实例
   */
  public boolean multiple() { // 公共方法，判断是否允许多个特征实例
    return false; // 默认返回false，表示关系表达式只能拥有该特征的一个实例（大多数特征都是单例的）
  } // 方法结束

  /** Returns the specific RelTrait type associated with this RelTraitDef. */
  /** 返回与此RelTraitDef关联的具体RelTrait类型 */
  public abstract Class<T> getTraitClass(); // 抽象方法，子类必须实现，返回该特征定义管理的特征类的Class对象

  /** Returns a simple name for this RelTraitDef (for use in
   * {@link org.apache.calcite.rel.RelNode#explain}). */
  /** 返回此RelTraitDef的简单名称（用于在{@link org.apache.calcite.rel.RelNode#explain}中显示） */
  public abstract String getSimpleName(); // 抽象方法，子类必须实现，返回该特征定义的简单名称，用于解释和调试

  /**
   * Takes an arbitrary RelTrait and returns the canonical representation of
   * that RelTrait. Canonized RelTrait objects may always be compared using
   * the equality operator (<code>==</code>).
   * 接收一个任意的RelTrait并返回该RelTrait的规范化表示。规范化的RelTrait对象总是可以使用相等运算符（<code>==</code>）进行比较。
   *
   * <p>If an equal RelTrait has already been canonized and is still in use,
   * it will be returned. Otherwise, the given RelTrait is made canonical and
   * returned.
   * 如果一个相等的RelTrait已经被规范化并且仍在使用中，则返回该对象。否则，将给定的RelTrait规范化并返回。
   *
   * @param trait a possibly non-canonical RelTrait
   * @param trait 参数，可能非规范化的RelTrait对象
   * @return a canonical RelTrait.
   * @return 返回值，规范化的RelTrait对象
   */
  public final T canonize(T trait) { // 公共final方法，规范化特征对象，确保相等的特征只有一个实例
    if (!(trait instanceof RelCompositeTrait)) { // 如果特征不是复合特征（RelCompositeTrait）
      assert getTraitClass().isInstance(trait) // 断言特征是该特征定义管理的特征类型的实例
          : getClass().getName() // 如果断言失败，输出错误信息：类名
          + " cannot canonize a " // 加上提示信息：不能规范化
          + trait.getClass().getName(); // 加上特征的类名
    } // 断言结束
    return interner.intern(trait); // 使用Interner规范化特征对象，如果已存在相等的对象则返回现有对象，否则返回传入的对象
  } // 方法结束

  /**
   * Converts the given RelNode to the given RelTrait.
   * 将给定的RelNode转换为指定的RelTrait
   *
   * @param planner                     the planner requesting the conversion
   * @param planner 参数，请求转换的优化规划器（RelOptPlanner）
   * @param rel                         RelNode to convert
   * @param rel 参数，要转换的关系表达式节点（RelNode）
   * @param toTrait                     RelTrait to convert to
   * @param toTrait 参数，要转换到的目标特征（RelTrait）
   * @param allowInfiniteCostConverters flag indicating whether infinite cost
   *                                    converters are allowed
   * @param allowInfiniteCostConverters 参数，布尔标志，指示是否允许无限成本的转换器
   * @return a converted RelNode or null if conversion is not possible
   * @return 返回值，转换后的RelNode，如果转换不可能则返回null
   */
  public abstract @Nullable RelNode convert( // 抽象方法，子类必须实现，执行实际的转换操作
      RelOptPlanner planner, // 参数：优化规划器
      RelNode rel, // 参数：要转换的关系表达式
      T toTrait, // 参数：目标特征
      boolean allowInfiniteCostConverters); // 参数：是否允许无限成本的转换器

  /**
   * Tests whether the given RelTrait can be converted to another RelTrait.
   * 测试给定的RelTrait是否可以转换为另一个RelTrait
   *
   * @param planner   the planner requesting the conversion test
   * @param planner 参数，请求转换测试的优化规划器（RelOptPlanner）
   * @param fromTrait the RelTrait to convert from
   * @param fromTrait 参数，要转换的源特征（RelTrait）
   * @param toTrait   the RelTrait to convert to
   * @param toTrait 参数，要转换到的目标特征（RelTrait）
   * @return true if fromTrait can be converted to toTrait
   * @return 返回值，如果fromTrait可以转换为toTrait则返回true，否则返回false
   */
  public abstract boolean canConvert( // 抽象方法，子类必须实现，测试转换是否可行
      RelOptPlanner planner, // 参数：优化规划器
      T fromTrait, // 参数：源特征
      T toTrait); // 参数：目标特征

  /**
   * Provides notification of the registration of a particular
   * {@link ConverterRule} with a {@link RelOptPlanner}. The default
   * implementation does nothing.
   * 提供特定{@link ConverterRule}在{@link RelOptPlanner}中注册的通知。默认实现不执行任何操作。
   *
   * @param planner       the planner registering the rule
   * @param planner 参数，注册规则的规划器
   * @param converterRule the registered converter rule
   * @param converterRule 参数，已注册的转换规则
   */
  public void registerConverterRule( // 公共方法，通知特征定义有新的转换规则注册
      RelOptPlanner planner, // 参数：优化规划器
      ConverterRule converterRule) { // 参数：转换规则
  } // 默认实现为空，子类可以覆盖以处理规则注册事件

  /**
   * Provides notification that a particular {@link ConverterRule} has been
   * de-registered from a {@link RelOptPlanner}. The default implementation
   * does nothing.
   * 提供特定{@link ConverterRule}已从{@link RelOptPlanner}中取消注册的通知。默认实现不执行任何操作。
   *
   * @param planner       the planner registering the rule
   * @param planner 参数，注册规则的规划器
   * @param converterRule the registered converter rule
   * @param converterRule 参数，已注册的转换规则
   */
  public void deregisterConverterRule( // 公共方法，通知特征定义有转换规则被取消注册
      RelOptPlanner planner, // 参数：优化规划器
      ConverterRule converterRule) { // 参数：转换规则
  } // 默认实现为空，子类可以覆盖以处理规则取消注册事件

  /**
   * Returns the default member of this trait.
   * 返回该特征的默认成员
   */
  public abstract T getDefault(); // 抽象方法，子类必须实现，返回该特征的默认值
} // 类定义结束