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
package org.apache.calcite.plan.volcano; // 定义包名，该类位于volcano优化器包下

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster，表示关系代数表达式的集群信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost，表示关系代数表达式的代价
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner，表示关系代数优化器
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall，表示优化规则的调用
import org.apache.calcite.plan.RelRule; // 导入RelRule，表示优化规则的基类
import org.apache.calcite.plan.RelTrait; // 导入RelTrait，表示关系代数表达式的特征
import org.apache.calcite.plan.RelTraitDef; // 导入RelTraitDef，表示关系代数特征的定义
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet，表示关系代数特征的集合
import org.apache.calcite.rel.RelNode; // 导入RelNode，表示关系代数表达式
import org.apache.calcite.rel.RelWriter; // 导入RelWriter，用于写入关系代数表达式
import org.apache.calcite.rel.convert.ConverterImpl; // 导入ConverterImpl，转换器的实现基类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery，用于查询元数据
import org.apache.calcite.tools.RelBuilderFactory; // 导入RelBuilderFactory，用于构建关系代数表达式

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可能为null的值
import org.immutables.value.Value; // 导入Value注解，用于创建不可变值对象

import java.util.List; // 导入List接口

/**
 * Converts a relational expression to any given output convention.
 * 将关系代数表达式转换为任意给定的输出约定（调用约定）。
 *
 * <p>Unlike most {@link org.apache.calcite.rel.convert.Converter}s, an abstract
 * converter is always abstract. You would typically create an
 * <code>AbstractConverter</code> when it is necessary to transform a relational
 * expression immediately; later, rules will transform it into relational
 * expressions which can be implemented.
 * 与大多数转换器不同，抽象转换器始终是抽象的。当需要立即转换关系代数表达式时，
 * 通常会创建AbstractConverter；稍后，规则会将其转换为可以被实际实现的关系代数表达式。
 *
 * <p>If an abstract converter cannot be satisfied immediately (because the
 * source subset is abstract), the set is flagged, so this converter will be
 * expanded as soon as a non-abstract relexp is added to the set.
 * 如果抽象转换器无法立即满足（因为源子集是抽象的），则该集合会被标记，
 * 这样一旦向集合中添加了非抽象的关系代数表达式，该转换器就会被展开。
 * 
 * 【类的作用】：AbstractConverter是Volcano优化器中用于表示从一个特征集转换到另一个特征集的占位符节点。
 * 它本身不执行实际的转换逻辑，而是作为一个标记，表示需要进行转换。当优化器发现需要将一个关系代数表达式
 * 从一种特征（比如物理实现方式）转换为另一种特征时，会先创建AbstractConverter节点，然后通过
 * ExpandConversionRule规则将其展开为具体的转换器链。这种设计允许优化器延迟转换决策，
 * 在找到最优实现方式后再进行实际转换，从而避免过早进行昂贵的转换操作。
 * AbstractConverter总是返回无限大的代价（makeInfiniteCost），这样可以确保在优化过程中
 * 优先选择具有有限代价的实现，只有在必要时才会展开AbstractConverter进行实际转换。
 */
@Value.Enclosing // Value注解，标记该类包含不可变值的嵌套类
public class AbstractConverter extends ConverterImpl { // AbstractConverter类，继承自ConverterImpl，表示抽象转换器
  //~ Constructors -----------------------------------------------------------

  public AbstractConverter( // 构造方法，创建AbstractConverter实例
      RelOptCluster cluster, // 参数：关系代数表达式集群，包含类型信息和表达式工厂
      RelSubset rel, // 参数：RelSubset，表示输入的关系代数子集，包含多个等价的关系代数表达式
      @Nullable RelTraitDef traitDef, // 参数：RelTraitDef，表示要转换的特征定义，可能为null
      RelTraitSet traits) { // 参数：RelTraitSet，表示目标特征集，即转换后应该具有的特征
    super(cluster, traitDef, traits, rel); // 调用父类ConverterImpl的构造方法，初始化转换器
    assert traits.allSimple(); // 断言：确保目标特征集中的所有特征都是简单特征（非复合特征）
  }

  //~ Methods ----------------------------------------------------------------


  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，创建当前节点的副本
    return new AbstractConverter( // 返回新的AbstractConverter实例
        getCluster(), // 获取当前节点的集群信息
        (RelSubset) sole(inputs), // 从输入列表中获取唯一的输入（强制转换为RelSubset）
        traitDef, // 使用当前的特征定义
        traitSet); // 使用新的特征集
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算当前节点的代价
      RelMetadataQuery mq) { // 参数：RelMetadataQuery，用于查询元数据
    return planner.getCostFactory().makeInfiniteCost(); // 返回无限大的代价，表示AbstractConverter本身不应该被选为最终实现
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，生成解释信息
    super.explainTerms(pw); // 调用父类的explainTerms方法，输出基本解释信息
    for (RelTrait trait : traitSet) { // 遍历目标特征集中的每个特征
      pw.item(trait.getTraitDef().getSimpleName(), trait); // 将特征定义的简单名称和特征值写入RelWriter
    }
    return pw; // 返回RelWriter，支持链式调用
  }

  @Override public boolean isEnforcer() { // 重写isEnforcer方法，判断是否为强制器
    return true; // 返回true，表示AbstractConverter是一个强制器，用于强制特征转换
  }

  //~ Inner Classes ----------------------------------------------------------

  /**
   * Rule that converts an {@link AbstractConverter} into a chain of
   * converters from the source relation to the target traits.
   * 该规则用于将AbstractConverter展开为从源关系到目标特征的一系列转换器链。
   *
   * <p>The chain produced is minimal: we have previously built the transitive
   * closure of the graph of conversions, so we choose the shortest chain.
   * 生成的转换器链是最小的：我们之前已经构建了转换图的传递闭包，所以选择最短的链。
   *
   * <p>Unlike the {@link AbstractConverter} they are replacing, these
   * converters are guaranteed to be able to convert any relation of their
   * calling convention. Furthermore, because they introduce subsets of other
   * calling conventions along the way, these subsets may spawn more efficient
   * conversions which are not generally applicable.
   * 与它们所替换的AbstractConverter不同，这些转换器保证能够转换其调用约定的任何关系。
   * 此外，因为它们在转换过程中引入了其他调用约定的子集，这些子集可能会产生更高效的转换，
   * 而这些转换在一般情况下不适用。
   *
   * <p>AbstractConverters can be messy, so they restrain themselves: they
   * don't fire if the target subset already has an implementation (with less
   * than infinite cost).
   * AbstractConverter可能会很混乱，所以它们会自我约束：如果目标子集已经有实现（代价小于无限大），
   * 则不会触发该规则。
   * 
   * 【内部类的作用】：ExpandConversionRule是一个优化规则，用于将AbstractConverter节点展开为具体的转换器链。
   * 当优化器发现AbstractConverter节点时，会触发该规则，尝试找到从源特征集到目标特征集的最短转换路径。
   * 该规则通过调用VolcanoPlanner的changeTraitsUsingConverters方法来执行实际的转换，将输入关系代数表达式
   * 转换为目标特征集。如果转换成功，则用转换后的结果替换AbstractConverter节点。
   * 该规则确保只有在必要时才进行转换，避免不必要的转换操作，提高优化效率。
   */
  public static class ExpandConversionRule // 内部静态类ExpandConversionRule，继承自RelRule，表示展开转换规则
      extends RelRule<ExpandConversionRule.Config> { // 泛型参数为ExpandConversionRule.Config
    public static final ExpandConversionRule INSTANCE = // 静态常量INSTANCE，表示ExpandConversionRule的单例实例
        Config.DEFAULT.toRule(); // 通过默认配置创建规则实例

    /** Creates an ExpandConversionRule. 创建ExpandConversionRule实例。 */
    protected ExpandConversionRule(Config config) { // 构造方法，接收配置对象
      super(config); // 调用父类RelRule的构造方法，初始化规则配置
    }

    @Deprecated // to be removed before 2.0 // 标记为过时，将在2.0版本前移除
    public ExpandConversionRule(RelBuilderFactory relBuilderFactory) { // 已弃用的构造方法，接收RelBuilderFactory
      this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory) // 使用默认配置并设置RelBuilderFactory
          .as(Config.class)); // 转换为Config类型
    }

    @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法，当规则匹配时执行
      final VolcanoPlanner planner = (VolcanoPlanner) call.getPlanner(); // 获取VolcanoPlanner优化器实例
      AbstractConverter converter = call.rel(0); // 获取规则匹配的AbstractConverter节点（第一个关系代数表达式）
      final RelNode child = converter.getInput(); // 获取AbstractConverter的输入节点（子节点）
      RelNode converted = // 声明变量存储转换后的关系代数表达式
          planner.changeTraitsUsingConverters( // 调用VolcanoPlanner的changeTraitsUsingConverters方法进行转换
              child, // 参数：输入的关系代数表达式
              converter.traitSet); // 参数：目标特征集
      if (converted != null) { // 如果转换成功（返回值不为null）
        call.transformTo(converted); // 将AbstractConverter替换为转换后的关系代数表达式
      }
    }

    /** Rule configuration. 规则配置接口。 */
    @Value.Immutable // Value注解，表示该接口是不可变值的接口
    public interface Config extends RelRule.Config { // Config接口，继承自RelRule.Config，定义规则配置
      Config DEFAULT = ImmutableConverter.Config.of() // 默认配置，通过ImmutableConverter创建
          .withOperandSupplier(b -> // 设置操作数提供器
              b.operand(AbstractConverter.class).anyInputs()); // 匹配AbstractConverter类型的节点，输入不限

      @Override default ExpandConversionRule toRule() { // 重写toRule方法，创建规则实例
        return new ExpandConversionRule(this); // 返回使用当前配置创建的ExpandConversionRule实例
      }
    }
  }
}
