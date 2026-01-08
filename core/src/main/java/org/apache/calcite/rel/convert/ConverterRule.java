/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，详见NOTICE文件中的版权信息
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权您使用此文件
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache 2.0许可证的URL
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 按"原样"分发，不提供任何明示或暗示的保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何形式的保证，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 详见许可证中关于权限和限制的语言
 * limitations under the License.
 */
package org.apache.calcite.rel.convert; // 转换规则包，包含各种关系表达式转换规则的实现

import org.apache.calcite.plan.Convention; // 约定类，定义了关系表达式的调用约定，如LOGICAL、ENUMERABLE等
import org.apache.calcite.plan.RelOptRule; // 关系优化规则基类，所有优化规则都必须继承此类
import org.apache.calcite.plan.RelOptRuleCall; // 优化规则调用上下文，包含匹配的关系表达式和转换方法
import org.apache.calcite.plan.RelRule; // 关系规则基类，提供了基于配置的规则构建方式
import org.apache.calcite.plan.RelTrait; // 关系特征接口，表示关系表达式的某种属性，如Convention、Distribution等
import org.apache.calcite.plan.RelTraitDef; // 关系特征定义，定义了特征的行为和规范化方法
import org.apache.calcite.rel.RelNode; // 关系表达式接口，表示关系代数中的一个操作符
import org.apache.calcite.tools.RelBuilderFactory; // 关系表达式构建器工厂，用于创建RelNode的构建器

import org.checkerframework.checker.nullness.qual.Nullable; // Checker框架的可空注解，用于标记可能为null的返回值
import org.immutables.value.Value; // Immutables库的注解，用于生成不可变的值对象

import java.util.Locale; // 本地化类，用于生成符合本地化规则的字符串
import java.util.Objects; // 对象工具类，提供了对象比较、哈希等方法
import java.util.function.Function; // 函数接口，表示接受一个参数并产生结果的函数
import java.util.function.Predicate; // 谓词接口，表示一个布尔值函数，用于过滤条件

import static org.apache.calcite.linq4j.Nullness.castNonNull; // LINQ4J库的静态方法，用于消除可空类型的警告

import static java.util.Objects.requireNonNull; // Java对象的静态方法，用于检查参数是否为null，如果为null则抛出NullPointerException

/**
 * 转换规则的抽象基类，用于将关系表达式从一个调用约定（calling convention）转换为另一个调用约定而不改变其语义
 * 在Calcite优化器中，不同的RelNode可能具有不同的约定（Convention），例如LOGICAL（逻辑约定）、ENUMERABLE（可枚举约定）、PHYSICAL（物理约定）等
 * ConverterRule的作用就是将一个RelNode从一种约定转换为另一种约定，例如将逻辑表达式转换为物理执行计划
 * 这个类定义了转换规则的基本框架，子类需要实现具体的转换逻辑
 * 转换规则是Calcite优化器规则体系中的重要组成部分，通过应用转换规则可以将关系表达式树从一个状态转换到另一个状态
 *
 * <h3>核心概念：</h3>
 * <ul>
 *   <li><strong>约定（Convention）</strong>：表示关系表达式的执行约定，例如：
 *     <ul>
 *       <li>Convention.NONE：无约定，表示未指定具体的执行方式</li>
 *       <li>Convention.LOGICAL：逻辑约定，表示逻辑层面的关系表达式</li>
 *       <li>Convention.ENUMERABLE：可枚举约定，表示可以转换为Java代码执行</li>
 *       <li>Convention.PHYSICAL：物理约定，表示物理执行计划</li>
 *     </ul>
 *   </li>
 *   <li><strong>特征（Trait）</strong>：关系表达式的属性，包括约定（Convention）、分布（Distribution）、排序（Collation）等</li>
 *   <li><strong>转换规则（ConverterRule）</strong>：定义如何将一个RelNode从一种特征转换为另一种特征的规则</li>
 * </ul>
 *
 * <h3>工作原理：</h3>
 * <ol>
 *   <li>优化器在遍历关系表达式树时，会检查当前RelNode的特征</li>
 *   <li>如果RelNode的特征与目标特征不匹配，优化器会查找适用的ConverterRule</li>
 *   <li>找到匹配的ConverterRule后，调用其convert方法进行转换</li>
 *   <li>转换后的RelNode具有目标特征，可以继续优化或执行</li>
 * </ol>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * // 创建一个将逻辑Filter转换为可枚举Filter的规则
 * public class FilterToEnumerableConverter extends ConverterRule {
 *   public FilterToEnumerableConverter() {
 *     super(Filter.class, Convention.LOGICAL, Convention.ENUMERABLE, "FilterToEnumerableConverter");
 *   }
 *
 *   @Override public RelNode convert(RelNode rel) {
 *     Filter filter = (Filter) rel;
 *     // 创建新的Filter，具有ENUMERABLE约定
 *     return filter.copy(filter.getTraitSet().replace(Convention.ENUMERABLE), filter.getInputs());
 *   }
 * }
 * </pre>
 *
 * <h3>重要方法：</h3>
 * <ul>
 *   <li>{@link #convert(RelNode)}：抽象方法，子类必须实现具体的转换逻辑</li>
 *   <li>{@link #onMatch(RelOptRuleCall)}：当规则匹配时调用，执行转换操作</li>
 *   <li>{@link #isGuaranteed()}：判断规则是否能转换所有关系表达式</li>
 * </ul>
 */
@Value.Enclosing
public abstract class ConverterRule
    extends RelRule<ConverterRule.Config> {
  //~ Instance fields --------------------------------------------------------

  private final RelTrait inTrait; // 输入特征（trait），表示该规则可以转换的源特征，例如Convention.NONE（无约定）或Convention.LOGICAL（逻辑约定）
  private final RelTrait outTrait; // 输出特征（trait），表示该规则转换后的目标特征，例如Convention.ENUMERABLE（可枚举约定）
  protected final Convention out; // 输出约定（Convention），是outTrait的快捷方式，仅当outTrait是Convention类型时才有值，否则为null

  //~ Constructors -----------------------------------------------------------

  /** 创建一个ConverterRule实例，使用配置对象来初始化规则的所有参数
   * @param config 规则的配置对象，包含输入特征、输出特征、规则工厂等信息
   */
  protected ConverterRule(Config config) {
    super(config); // 调用父类RelRule的构造方法，初始化规则的基本配置
    this.inTrait = requireNonNull(config.inTrait()); // 从配置中获取输入特征，不能为null
    this.outTrait = requireNonNull(config.outTrait()); // 从配置中获取输出特征，不能为null

    // 源特征和目标特征必须具有相同的类型（traitDef），因为转换只能在同一种特征类型之间进行
    // 例如，不能将Convention转换为Distribution，因为它们是不同类型的特征
    assert inTrait.getTraitDef() == outTrait.getTraitDef();

    // 大多数子类都关注于将一个约定转换为另一个约定，对于这些情况，"out"字段提供了一个方便的快捷方式
    // 如果outTrait是Convention类型，则将其赋值给out字段，否则out字段为null
    this.out =
        outTrait instanceof Convention ? (Convention) outTrait
            : castNonNull(null);
  }

  /**
   * 创建一个ConverterRule实例（已废弃，请使用Config方式）
   *
   * @param clazz       要考虑转换的关系表达式类型，例如Filter.class、Project.class等
   * @param in          要考虑转换的关系表达式的输入特征，例如Convention.NONE
   * @param out         要转换到的目标特征，例如Convention.ENUMERABLE
   * @param descriptionPrefix 规则描述的前缀，用于生成规则的描述信息
   *
   * @deprecated 请使用 {@link #ConverterRule(Config)} 构造方法，这个方法将在2.0版本之前移除
   */
  @Deprecated // to be removed before 2.0
  protected ConverterRule(Class<? extends RelNode> clazz, RelTrait in,
      RelTrait out, String descriptionPrefix) {
    this(Config.INSTANCE // 使用默认配置实例
        .withConversion(clazz, in, out, descriptionPrefix)); // 配置转换规则的基本参数
  }

  @SuppressWarnings("Guava") // 抑制Guava相关的警告
  @Deprecated // to be removed before 2.0
  protected <R extends RelNode> ConverterRule(Class<R> clazz,
      com.google.common.base.Predicate<? super R> predicate, // Guava谓词，用于过滤哪些关系表达式需要转换
      RelTrait in, RelTrait out, String descriptionPrefix) {
    this(Config.INSTANCE // 使用默认配置实例
        .withConversion(clazz, (Predicate<? super R>) predicate::apply, // 将Guava谓词转换为Java 8的Predicate
            in, out, descriptionPrefix)); // 配置转换规则的基本参数
  }

  /**
   * 创建一个带有谓词的ConverterRule实例（已废弃，请使用Config方式）
   *
   * @param clazz       要考虑转换的关系表达式类型，例如Filter.class、Project.class等
   * @param predicate   应用于关系表达式的谓词，只有满足谓词条件的关系表达式才会被考虑转换
   * @param in          要考虑转换的关系表达式的输入特征，例如Convention.NONE
   * @param out         要转换到的目标特征，例如Convention.ENUMERABLE
   * @param relBuilderFactory 用于构建关系表达式的工厂，提供了创建RelNode的构建器
   * @param descriptionPrefix 规则描述的前缀，用于生成规则的描述信息
   *
   * @deprecated 请使用 {@link #ConverterRule(Config)} 构造方法，这个方法将在2.0版本之前移除
   */
  @Deprecated // to be removed before 2.0
  protected <R extends RelNode> ConverterRule(Class<R> clazz,
      Predicate<? super R> predicate, RelTrait in, RelTrait out,
      RelBuilderFactory relBuilderFactory, String descriptionPrefix) {
    this(ImmutableConverterRule.Config.builder() // 创建不可变的配置构建器
        .withRelBuilderFactory(relBuilderFactory) // 设置关系表达式构建工厂
        .build() // 构建基础配置
        .withConversion(clazz, predicate, in, out, descriptionPrefix)); // 配置转换规则的基本参数
  }

  @SuppressWarnings("Guava") // 抑制Guava相关的警告
  @Deprecated // to be removed before 2.0
  protected <R extends RelNode> ConverterRule(Class<R> clazz,
      com.google.common.base.Predicate<? super R> predicate, RelTrait in, // Guava谓词，用于过滤哪些关系表达式需要转换
      RelTrait out, RelBuilderFactory relBuilderFactory, String description) {
    this(clazz, (Predicate<? super R>) predicate::apply, in, out, // 将Guava谓词转换为Java 8的Predicate，并调用上一个构造方法
        relBuilderFactory, description);
  }

  //~ Methods ----------------------------------------------------------------

  @Override public Convention getOutConvention() {
    return (Convention) outTrait; // 返回输出约定（Convention），将outTrait强制转换为Convention类型返回
  }

  @Override public RelTrait getOutTrait() {
    return outTrait; // 返回输出特征（trait），即转换后的目标特征
  }

  public RelTrait getInTrait() {
    return inTrait; // 返回输入特征（trait），即转换前的源特征
  }

  public RelTraitDef getTraitDef() {
    return inTrait.getTraitDef(); // 返回特征定义（traitDef），用于标识特征的类型，例如ConventionTraitDef、RelDistributionDef等
  }

  private static String createDescription(String descriptionPrefix, // 规则描述的前缀
      RelTrait in, RelTrait out) { // 输入特征和输出特征
    return String.format(Locale.ROOT, "%s(in:%s,out:%s)", // 使用格式化字符串生成规则描述，格式为"前缀(in:输入特征,out:输出特征)"
        Objects.toString(descriptionPrefix, "ConverterRule"), in, out); // 如果descriptionPrefix为null，则使用默认值"ConverterRule"
  }

  /** 将一个关系表达式转换到该规则的目标特征，这是ConverterRule的核心抽象方法，子类必须实现具体的转换逻辑
   *
   * <p>如果转换不可能实现，则返回null。例如，某些特定的RelNode可能无法转换到目标特征
   *
   * @param rel 要转换的关系表达式，该关系表达式应该已经具有该规则的输入特征（inTrait）
   * @return 转换后的关系表达式，具有该规则的输出特征（outTrait）；如果转换不可行则返回null
   */
  public abstract @Nullable RelNode convert(RelNode rel);

  /**
   * 判断该规则是否可以转换输入约定下的<strong>任何</strong>关系表达式
   *
   * <p>如果返回true，表示该规则可以处理输入约定下的所有类型的关系表达式
   * 如果返回false，表示该规则只能处理特定类型的关系表达式，或者转换不是总是可行的
   *
   * <p>例如，union-to-java转换器不是保证的，因为它只适用于Union操作符，不能处理其他类型的RelNode
   * 默认实现返回false，因为大多数转换规则都只适用于特定类型的关系表达式
   *
   * @return {@code true} 如果该规则可以转换输入约定下的<strong>任何</strong>关系表达式；否则返回false
   */
  public boolean isGuaranteed() {
    return false; // 默认返回false，表示该规则不能保证转换所有关系表达式
  }

  @Override public void onMatch(RelOptRuleCall call) { // 当规则匹配时被调用，这是RelOptRule的核心方法
    RelNode rel = call.rel(0); // 获取规则匹配的第一个关系表达式（索引为0）
    if (rel.getTraitSet().contains(inTrait)) { // 检查该关系表达式是否包含输入特征，只有包含输入特征才能进行转换
      final RelNode converted = convert(rel); // 调用抽象方法convert进行转换，子类实现具体的转换逻辑
      if (converted != null) { // 如果转换成功（返回非null）
        call.transformTo(converted); // 将原始关系表达式转换为转换后的关系表达式，通知优化器进行转换
      }
    }
  }

  //~ Inner Classes ----------------------------------------------------------

  /** 规则配置接口，使用Immutables库的@Value.Immutable注解生成不可变的配置实现类
   * 这个接口定义了ConverterRule的所有配置参数，包括输入特征、输出特征、规则工厂等
   * 通过builder模式可以方便地构建配置对象，确保配置的不可变性和线程安全性
   * singleton = false表示不使用单例模式，每次都创建新的实例
   */
  @Value.Immutable(singleton = false)
  public interface Config extends RelRule.Config { // 继承RelRule.Config，共享基础配置
    Config INSTANCE = ImmutableConverterRule.Config.builder() // 创建默认配置实例
        .withInTrait(Convention.NONE) // 设置输入特征为NONE（无约定）
        .withOutTrait(Convention.NONE) // 设置输出特征为NONE（无约定）
        .withRuleFactory(new Function<Config, ConverterRule>() { // 设置规则工厂，用于从配置创建规则实例
          @Override public ConverterRule apply(final Config config) {
            throw new UnsupportedOperationException("A rule factory must be provided"); // 默认工厂抛出异常，要求用户提供自定义工厂
          }
        }).build(); // 构建配置实例

    RelTrait inTrait(); // 获取输入特征，这是该规则可以转换的源特征

    /** 设置 {@link #inTrait}，返回新的配置对象（因为配置是不可变的） */
    Config withInTrait(RelTrait trait); // trait参数是新的输入特征

    RelTrait outTrait(); // 获取输出特征，这是该规则转换后的目标特征

    /** 设置 {@link #outTrait}，返回新的配置对象（因为配置是不可变的） */
    Config withOutTrait(RelTrait trait); // trait参数是新的输出特征

    Function<Config, ConverterRule> ruleFactory(); // 获取规则工厂，这是一个函数接口，输入是Config，输出是ConverterRule实例

    /** 设置 {@link #outTrait}，返回新的配置对象（因为配置是不可变的） */
    Config withRuleFactory(Function<Config, ConverterRule> factory); // factory参数是新的规则工厂

    default <R extends RelNode> Config withConversion(Class<R> clazz, // 要转换的关系表达式类型
        Predicate<? super R> predicate, RelTrait in, RelTrait out, // 谓词用于过滤、输入特征、输出特征
        String descriptionPrefix) { // 规则描述的前缀
      return withInTrait(in) // 设置输入特征
          .withOutTrait(out) // 设置输出特征
          .withOperandSupplier(b -> // 设置操作数供应器，定义规则匹配的条件
              b.operand(clazz).predicate(predicate).convert(in)) // 匹配指定类型的操作数，应用谓词过滤，并要求该操作数具有输入特征
          .withDescription(createDescription(descriptionPrefix, in, out)) // 设置规则描述
          .as(Config.class); // 返回Config接口类型
    }

    default Config withConversion(Class<? extends RelNode> clazz, RelTrait in, // 重载方法，不带谓词参数
        RelTrait out, String descriptionPrefix) {
      return withConversion(clazz, r -> true, in, out, descriptionPrefix); // 使用总是返回true的谓词，表示匹配所有类型为clazz的关系表达式
    }

    @Override default RelOptRule toRule() {
      return toRule(ConverterRule.class); // 调用重载方法，使用ConverterRule.class作为参数
    }

    default <R extends ConverterRule> R toRule(Class<R> ruleClass) { // 泛型方法，将配置转换为指定类型的规则实例
      return ruleClass.cast(ruleFactory().apply(this)); // 使用规则工厂将当前配置转换为规则实例，并强制转换为指定的规则类型
    }
  }

}
