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
package org.apache.calcite.plan.volcano; // 包声明，属于VolcanoPlanner测试包

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入可枚举调用约定
import org.apache.calcite.adapter.enumerable.EnumerableRel; // 导入可枚举关系表达式接口
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor; // 导入可枚举关系表达式实现器
import org.apache.calcite.plan.Convention; // 导入调用约定接口
import org.apache.calcite.plan.ConventionTraitDef; // 导入调用约定trait定义
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式集群
import org.apache.calcite.plan.RelOptCost; // 导入关系表达式优化成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器
import org.apache.calcite.plan.RelOptRuleCall; // 导入优化规则调用
import org.apache.calcite.plan.RelOptUtil; // 导入关系表达式优化工具类
import org.apache.calcite.plan.RelRule; // 导入关系表达式规则基类
import org.apache.calcite.plan.RelTrait; // 导入关系表达式trait接口
import org.apache.calcite.plan.RelTraitDef; // 导入关系表达式trait定义基类
import org.apache.calcite.plan.RelTraitSet; // 导入关系表达式trait集合
import org.apache.calcite.rel.AbstractRelNode; // 导入抽象关系表达式节点
import org.apache.calcite.rel.RelNode; // 导入关系表达式接口
import org.apache.calcite.rel.RelWriter; // 导入关系表达式写入器
import org.apache.calcite.rel.SingleRel; // 导入单输入关系表达式
import org.apache.calcite.rel.convert.ConverterImpl; // 导入转换器实现基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入转换规则基类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系表达式元数据查询
import org.apache.calcite.rel.type.RelDataType; // 导入关系表达式数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系表达式数据类型工厂
import org.apache.calcite.util.Pair; // 导入键值对工具类

import com.google.common.collect.HashMultimap; // 导入Guava的哈希多值映射
import com.google.common.collect.Multimap; // 导入Guava的多值映射接口

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解
import org.immutables.value.Value; // 导入Immutables注解
import org.junit.jupiter.api.Disabled; // 导入JUnit5的禁用注解
import org.junit.jupiter.api.Test; // 导入JUnit5的测试注解

import java.util.List; // 导入Java列表接口

import static org.apache.calcite.plan.volcano.PlannerTests.newCluster; // 导入创建集群的静态方法

import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest的类型匹配断言
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的相等断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit5的假断言
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5的真断言

/**
 * Unit test for handling of traits by {@link VolcanoPlanner}.
 */ // VolcanoPlanner的trait（特性）处理的单元测试类，用于测试VolcanoPlanner如何处理RelNode的trait转换和传播
// Trait是Calcite中用于描述关系表达式属性（如调用约定、排序方式等）的机制，VolcanoPlanner需要管理trait的转换和传播
// 本测试类包含多个测试方法，分别测试双重转换、转换后的规则匹配、trait传播以及NONE convention的成本计算等场景
// 测试使用了自定义的AltTrait和AltTraitDef来模拟非Convention类型的trait，验证VolcanoPlanner对多trait的支持
// 测试还使用了多种自定义的关系表达式和转换规则，构建复杂的trait转换场景来验证优化器的正确性
class VolcanoPlannerTraitTest {
  /**
   * Private calling convention representing a generic "physical" calling
   * convention.
   */ // 私有调用约定，表示一个通用的"物理"调用约定，用于测试物理层面的RelNode
  private static final Convention PHYS_CALLING_CONVENTION =
      new Convention.Impl(
          "PHYS",
          RelNode.class); // 创建一个名为"PHYS"的调用约定，适用于所有RelNode类型

  /**
   * Private trait definition for an alternate type of traits.
   */ // 私有的trait定义，用于测试另一种类型的trait（特性），除了Convention之外的扩展trait
  private static final AltTraitDef ALT_TRAIT_DEF = new AltTraitDef(); // 创建AltTraitDef实例，用于定义和管理AltTrait

  /**
   * Private alternate trait.
   */ // 私有的空trait，作为AltTrait的默认值或基础trait
  private static final AltTrait ALT_EMPTY_TRAIT =
      new AltTrait(ALT_TRAIT_DEF, "ALT_EMPTY"); // 创建一个名为"ALT_EMPTY"的空trait

  /**
   * Private alternate trait.
   */ // 私有的alternate trait，用于测试trait之间的转换
  private static final AltTrait ALT_TRAIT =
      new AltTrait(ALT_TRAIT_DEF, "ALT"); // 创建一个名为"ALT"的trait

  /**
   * Private alternate trait.
   */ // 私有的第二个alternate trait，用于测试trait之间的转换
  private static final AltTrait ALT_TRAIT2 =
      new AltTrait(ALT_TRAIT_DEF, "ALT2"); // 创建一个名为"ALT2"的trait

  /**
   * Ordinal count for alternate traits (so they can implement equals() and
   * avoid being canonized into the same trait).
   */ // alternate trait的序号计数器，用于为每个trait分配唯一序号，确保equals()方法能正确区分不同的trait，避免被规范化为同一个trait
  private static int altTraitOrdinal = 0; // 初始化序号计数器为0

  @Disabled // 禁用此测试方法，可能因为测试不稳定或需要修复
  @Test void testDoubleConversion() { // 测试双重转换：测试VolcanoPlanner如何处理连续的trait转换，包括Convention和AltTrait的转换
    VolcanoPlanner planner = new VolcanoPlanner(); // 创建一个新的VolcanoPlanner实例，VolcanoPlanner是Calcite的基于成本的优化器

    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 向planner添加ConventionTraitDef，定义Convention trait的类型系统
    planner.addRelTraitDef(ALT_TRAIT_DEF); // 向planner添加自定义的AltTraitDef，定义AltTrait的类型系统

    planner.addRule(PhysToIteratorConverterRule.INSTANCE); // 添加从PHYS调用约定到ENUMERABLE调用约定的转换规则
    planner.addRule(
        AltTraitConverterRule.create(ALT_TRAIT, ALT_TRAIT2,
            "AltToAlt2ConverterRule")); // 添加从ALT_TRAIT到ALT_TRAIT2的转换规则
    planner.addRule(PhysLeafRule.INSTANCE); // 添加将NoneLeafRel转换为PhysLeafRel的规则
    planner.addRule(IterSingleRule.INSTANCE); // 添加将NoneSingleRel转换为IterSingleRel的规则

    RelOptCluster cluster = newCluster(planner); // 创建一个新的RelOptCluster，包含RexBuilder和RelOptPlanner

    NoneLeafRel noneLeafRel = // 创建一个NoneLeafRel（无输入的关系表达式）
        RelOptUtil.addTrait( // 使用RelOptUtil工具类为关系表达式添加trait
            new NoneLeafRel(cluster, "noneLeafRel"), ALT_TRAIT); // 创建NoneLeafRel并添加ALT_TRAIT

    NoneSingleRel noneRel = // 创建一个NoneSingleRel（有一个输入的关系表达式）
        RelOptUtil.addTrait( // 使用RelOptUtil工具类为关系表达式添加trait
            new NoneSingleRel(cluster, noneLeafRel), ALT_TRAIT2); // 创建NoneSingleRel，输入是noneLeafRel，并添加ALT_TRAIT2

    RelNode convertedRel = // 转换关系表达式的trait
        planner.changeTraits(noneRel, // 将noneRel的trait从NONE convention转换为ENUMERABLE convention
            cluster.traitSetOf(EnumerableConvention.INSTANCE) // 创建包含ENUMERABLE convention的trait集合
                .replace(ALT_TRAIT2)); // 替换为ALT_TRAIT2，保持AltTrait不变

    planner.setRoot(convertedRel); // 设置转换后的关系表达式为planner的根节点
    RelNode result = planner.chooseDelegate().findBestExp(); // 执行优化，找到成本最低的最佳执行计划

    assertThat(result, instanceOf(IterSingleRel.class)); // 断言最终结果是IterSingleRel类型
    assertThat(result.getTraitSet().getTrait(ConventionTraitDef.INSTANCE), // 断言结果的Convention trait
        is(EnumerableConvention.INSTANCE)); // 是ENUMERABLE convention
    assertThat(result.getTraitSet().getTrait(ALT_TRAIT_DEF), is(ALT_TRAIT2)); // 断言结果的AltTrait是ALT_TRAIT2

    RelNode child = result.getInputs().get(0); // 获取结果的第一个子节点
    assertTrue( // 断言子节点是AltTraitConverter或PhysToIteratorConverter之一
        (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter)); // 测试trait转换的中间节点

    child = child.getInputs().get(0); // 获取子节点的子节点
    assertTrue( // 断言这个子节点也是AltTraitConverter或PhysToIteratorConverter之一
        (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter)); // 测试双重转换路径

    child = child.getInputs().get(0); // 获取最底层的子节点
    assertThat(child, instanceOf(PhysLeafRel.class)); // 断言最底层是PhysLeafRel，即物理叶子节点
  }

  @Test void testRuleMatchAfterConversion() { // 测试转换后的规则匹配：验证在trait转换之后，planner能否正确应用优化规则
    VolcanoPlanner planner = new VolcanoPlanner(); // 创建一个新的VolcanoPlanner实例

    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加Convention trait定义
    planner.addRelTraitDef(ALT_TRAIT_DEF); // 添加AltTrait定义

    planner.addRule(PhysToIteratorConverterRule.INSTANCE); // 添加PHYS到ENUMERABLE的转换规则
    planner.addRule(PhysLeafRule.INSTANCE); // 添加NoneLeafRel到PhysLeafRel的转换规则
    planner.addRule(IterSingleRule.INSTANCE); // 添加NoneSingleRel到IterSingleRel的转换规则
    planner.addRule(IterSinglePhysMergeRule.INSTANCE); // 添加IterSingleRel和PhysToIteratorConverter的合并规则

    RelOptCluster cluster = newCluster(planner); // 创建RelOptCluster

    NoneLeafRel noneLeafRel = // 创建NoneLeafRel
        RelOptUtil.addTrait( // 添加ALT_TRAIT
            new NoneLeafRel(cluster, "noneLeafRel"), ALT_TRAIT);

    NoneSingleRel noneRel = // 创建NoneSingleRel
        RelOptUtil.addTrait( // 添加ALT_EMPTY_TRAIT
            new NoneSingleRel(cluster, noneLeafRel), ALT_EMPTY_TRAIT);

    RelNode convertedRel = // 转换trait
        planner.changeTraits(noneRel, // 将noneRel转换为ENUMERABLE convention
            cluster.traitSetOf(EnumerableConvention.INSTANCE) // 创建ENUMERABLE trait集合
                .replace(ALT_EMPTY_TRAIT)); // 保持ALT_EMPTY_TRAIT

    planner.setRoot(convertedRel); // 设置根节点
    RelNode result = planner.chooseDelegate().findBestExp(); // 执行优化，找到最佳计划

    assertThat(result, instanceOf(IterMergedRel.class)); // 断言最终结果是IterMergedRel，说明合并规则被正确应用
  }

  @Disabled // 禁用此测试方法
  @Test void testTraitPropagation() { // 测试trait传播：验证trait如何在整个关系表达式树中传播，包括Convention和AltTrait
    VolcanoPlanner planner = new VolcanoPlanner(); // 创建VolcanoPlanner实例

    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加Convention trait定义
    planner.addRelTraitDef(ALT_TRAIT_DEF); // 添加AltTrait定义

    planner.addRule(PhysToIteratorConverterRule.INSTANCE); // 添加PHYS到ENUMERABLE的转换规则
    planner.addRule(
        AltTraitConverterRule.create(ALT_TRAIT, ALT_TRAIT2, // 添加ALT到ALT2的转换规则
            "AltToAlt2ConverterRule"));
    planner.addRule(PhysLeafRule.INSTANCE); // 添加NoneLeafRel到PhysLeafRel的规则
    planner.addRule(IterSingleRule2.INSTANCE); // 添加NoneSingleRel到IterSingleRel的规则（版本2）

    RelOptCluster cluster = newCluster(planner); // 创建RelOptCluster

    NoneLeafRel noneLeafRel = // 创建NoneLeafRel
        RelOptUtil.addTrait( // 添加ALT_TRAIT
            new NoneLeafRel(cluster, "noneLeafRel"), ALT_TRAIT);

    NoneSingleRel noneRel = // 创建NoneSingleRel
        RelOptUtil.addTrait( // 添加ALT_TRAIT2
            new NoneSingleRel(cluster, noneLeafRel), ALT_TRAIT2);

    RelNode convertedRel = // 转换trait
        planner.changeTraits(noneRel, // 转换为ENUMERABLE convention
            cluster.traitSetOf(EnumerableConvention.INSTANCE) // 创建ENUMERABLE trait集合
                .replace(ALT_TRAIT2)); // 保持ALT_TRAIT2

    planner.setRoot(convertedRel); // 设置根节点
    RelNode result = planner.chooseDelegate().findBestExp(); // 执行优化

    assertThat(result, instanceOf(IterSingleRel.class)); // 断言结果是IterSingleRel
    assertThat(result.getTraitSet().getTrait(ConventionTraitDef.INSTANCE), // 断言Convention
        is(EnumerableConvention.INSTANCE)); // 是ENUMERABLE
    assertThat(result.getTraitSet().getTrait(ALT_TRAIT_DEF), is(ALT_TRAIT2)); // 断言AltTrait是ALT_TRAIT2

    RelNode child = result.getInputs().get(0); // 获取第一个子节点
    assertThat(child, instanceOf(IterSingleRel.class)); // 断言子节点也是IterSingleRel（trait传播）
    assertThat(child.getTraitSet().getTrait(ConventionTraitDef.INSTANCE), // 断言子节点的Convention
        is(EnumerableConvention.INSTANCE)); // 也是ENUMERABLE
    assertThat(child.getTraitSet().getTrait(ALT_TRAIT_DEF), is(ALT_TRAIT2)); // 断言子节点的AltTrait也是ALT_TRAIT2

    child = child.getInputs().get(0); // 获取下一层子节点
    assertTrue( // 断言是转换器
        (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter));

    child = child.getInputs().get(0); // 继续获取下一层
    assertTrue( // 断言是转换器
        (child instanceof AltTraitConverter)
            || (child instanceof PhysToIteratorConverter));

    child = child.getInputs().get(0); // 获取最底层
    assertTrue(child instanceof PhysLeafRel); // 断言是PhysLeafRel
  }

  @Test void testPlanWithNoneConvention() { // 测试使用NONE convention的计划：验证NONE convention的cost计算行为
    VolcanoPlanner planner = new VolcanoPlanner(); // 创建VolcanoPlanner实例
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加Convention trait定义
    RelOptCluster cluster = newCluster(planner); // 创建RelOptCluster
    NoneTinyLeafRel leaf = new NoneTinyLeafRel(cluster, "noneLeafRel"); // 创建NoneTinyLeafRel（NONE convention的叶子节点）
    planner.setRoot(leaf); // 设置为根节点
    RelOptCost cost = planner.getCost(leaf, cluster.getMetadataQuery()); // 计算成本

    assertTrue(cost.isInfinite()); // 断言默认情况下NONE convention的成本是无穷大

    planner.setNoneConventionHasInfiniteCost(false); // 设置NONE convention不使用无穷大成本
    cost = planner.getCost(leaf, cluster.getMetadataQuery()); // 重新计算成本
    assertFalse(cost.isInfinite()); // 断言成本不再是无穷大
  }

  //~ Inner Classes ----------------------------------------------------------

  /** Implementation of {@link RelTrait} for testing. */ // RelTrait的测试实现类，用于测试自定义trait的功能
  private static class AltTrait implements RelTrait { // AltTrait实现了RelTrait接口，表示一种自定义的trait类型
    private final AltTraitDef traitDef; // trait定义的引用，指向创建此trait的AltTraitDef实例
    private final int ordinal; // trait的唯一序号，用于区分不同的trait实例
    private final String description; // trait的描述信息，用于toString()方法

    private AltTrait(AltTraitDef traitDef, String description) { // 私有构造函数，创建AltTrait实例
      this.traitDef = traitDef; // 保存trait定义
      this.description = description; // 保存描述信息
      this.ordinal = altTraitOrdinal++; // 分配并递增序号，确保每个trait有唯一序号
    }

    public void register(RelOptPlanner planner) {} // 注册方法，向planner注册此trait（空实现）

    public RelTraitDef getTraitDef() { // 获取此trait的定义
      return traitDef; // 返回AltTraitDef实例
    }

    public boolean equals(Object other) { // 判断两个AltTrait是否相等
      if (other == this) { // 如果是同一个对象
        return true; // 返回true
      }
      if (!(other instanceof AltTrait)) { // 如果不是AltTrait类型
        return false; // 返回false
      }
      AltTrait that = (AltTrait) other; // 强制转换为AltTrait
      return this.ordinal == that.ordinal; // 比较序号是否相等
    }

    public int hashCode() { // 计算hash值
      return ordinal; // 使用序号作为hash值
    }

    public boolean satisfies(RelTrait trait) { // 判断此trait是否满足另一个trait的要求
      return trait.equals(ALT_EMPTY_TRAIT) || equals(trait); // 如果目标trait是ALT_EMPTY_TRAIT或等于此trait，则满足
    }

    public String toString() { // 转换为字符串
      return description; // 返回描述信息
    }
  }

  /** Definition of {@link AltTrait}. */ // AltTrait的定义类，继承自RelTraitDef，负责管理AltTrait的转换规则和默认值
  private static class AltTraitDef extends RelTraitDef<AltTrait> { // AltTraitDef是RelTraitDef的子类，专门用于AltTrait类型
    private final Multimap<RelTrait, Pair<RelTrait, ConverterRule>> conversionMap = // 转换映射表，存储源trait到目标trait和转换规则的映射
        HashMultimap.create(); // 使用Guava的HashMultimap创建多值映射

    public Class<AltTrait> getTraitClass() { // 获取此trait定义管理的trait类
      return AltTrait.class; // 返回AltTrait类
    }

    public String getSimpleName() { // 获取trait定义的简单名称
      return "alt_phys"; // 返回"alt_phys"作为标识
    }

    public AltTrait getDefault() { // 获取默认的trait实例
      return ALT_TRAIT; // 返回ALT_TRAIT作为默认值
    }

    public @Nullable RelNode convert( // 转换关系表达式的trait
        RelOptPlanner planner, // 优化器实例
        RelNode rel, // 要转换的关系表达式
        AltTrait toTrait, // 目标trait
        boolean allowInfiniteCostConverters) { // 是否允许使用无穷大成本的转换器
      RelTrait fromTrait = rel.getTraitSet().getTrait(this); // 获取关系表达式当前的AltTrait

      if (conversionMap.containsKey(fromTrait)) { // 如果存在从当前trait的转换规则
        final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询对象
        for (Pair<RelTrait, ConverterRule> traitAndRule // 遍历所有转换规则
            : conversionMap.get(fromTrait)) {
          RelTrait trait = traitAndRule.left; // 获取目标trait
          ConverterRule rule = traitAndRule.right; // 获取转换规则

          if (trait == toTrait) { // 如果目标trait匹配
            RelNode converted = rule.convert(rel); // 应用转换规则
            if ((converted != null) // 如果转换成功
                && (!planner.getCost(converted, mq).isInfinite() // 且成本不是无穷大
                || allowInfiniteCostConverters)) { // 或允许无穷大成本转换器
              return converted; // 返回转换后的关系表达式
            }
          }
        }
      }

      return null; // 没有找到合适的转换规则，返回null
    }

    public boolean canConvert( // 判断是否可以从一个trait转换为另一个trait
        RelOptPlanner planner, // 优化器实例
        AltTrait fromTrait, // 源trait
        AltTrait toTrait) { // 目标trait
      if (conversionMap.containsKey(fromTrait)) { // 如果存在从源trait的转换规则
        for (Pair<RelTrait, ConverterRule> traitAndRule // 遍历转换规则
            : conversionMap.get(fromTrait)) {
          if (traitAndRule.left == toTrait) { // 如果找到目标trait
            return true; // 返回true，表示可以转换
          }
        }
      }

      return false; // 没有找到转换规则，返回false
    }

    public void registerConverterRule( // 注册转换规则
        RelOptPlanner planner, // 优化器实例
        ConverterRule converterRule) { // 要注册的转换规则
      if (!converterRule.isGuaranteed()) { // 如果转换规则不保证成功
        return; // 不注册
      }

      RelTrait fromTrait = converterRule.getInTrait(); // 获取源trait
      RelTrait toTrait = converterRule.getOutTrait(); // 获取目标trait

      conversionMap.put(fromTrait, Pair.of(toTrait, converterRule)); // 将转换规则添加到映射表
    }
  }

  /** A relational expression with zero inputs. */ // 测试用的叶子关系表达式抽象类，没有输入，用于构建测试用例的基础关系表达式
  private abstract static class TestLeafRel extends AbstractRelNode { // 继承自AbstractRelNode，表示没有输入的关系表达式
    private final String label; // 标签，用于标识和调试

    protected TestLeafRel( // 受保护的构造函数
        RelOptCluster cluster, // 关系表达式集群
        RelTraitSet traits, // trait集合
        String label) { // 标签
      super(cluster, traits); // 调用父类构造函数
      this.label = label; // 保存标签
    }

    public String getLabel() { // 获取标签
      return label; // 返回标签值
    }

    // implement RelNode // 实现RelNode接口
    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本
        RelMetadataQuery mq) { // 元数据查询对象
      return planner.getCostFactory().makeInfiniteCost(); // 返回无穷大成本，默认实现
    }

    // implement RelNode // 实现RelNode接口
    protected RelDataType deriveRowType() { // 推导行类型
      final RelDataTypeFactory typeFactory = getCluster().getTypeFactory(); // 获取类型工厂
      return typeFactory.builder() // 创建类型构建器
          .add("this", typeFactory.createJavaType(Void.TYPE)) // 添加一个Void类型的列
          .build(); // 构建行类型
    }

    public RelWriter explainTerms(RelWriter pw) { // 解释关系表达式的属性
      return super.explainTerms(pw) // 调用父类的explainTerms
          .item("label", label); // 添加标签属性
    }
  }

  /** A relational expression with zero inputs, of NONE convention. */ // NONE convention的叶子关系表达式，表示逻辑层面的关系表达式
  private static class NoneLeafRel extends TestLeafRel { // 继承自TestLeafRel，使用NONE convention
    protected NoneLeafRel( // 受保护的构造函数
        RelOptCluster cluster, // 关系表达式集群
        String label) { // 标签
      super( // 调用父类构造函数
          cluster, // 传入集群
          cluster.traitSetOf(Convention.NONE), // 使用NONE convention的trait集合
          label); // 传入标签
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
      return new NoneLeafRel(getCluster(), getLabel()); // 创建新的NoneLeafRel实例
    }
  }

  /** Relational expression with zero inputs, of PHYS convention. */ // PHYS convention的叶子关系表达式，表示物理层面的关系表达式
  private static class PhysLeafRel extends TestLeafRel { // 继承自TestLeafRel，使用PHYS_CALLING_CONVENTION
    PhysLeafRel( // 构造函数
        RelOptCluster cluster, // 关系表达式集群
        String label) { // 标签
      super( // 调用父类构造函数
          cluster, // 传入集群
          cluster.traitSetOf(PHYS_CALLING_CONVENTION), // 使用PHYS_CALLING_CONVENTION的trait集合
          label); // 传入标签
    }

    // implement RelNode // 实现RelNode接口
    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本
        RelMetadataQuery mq) { // 元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回极小的成本，表示物理节点成本很低
    }

    // TODO: SWZ Implement clone? // TODO: 需要实现clone方法
  }

  /** Relational expression with one input. */ // 测试用的单输入关系表达式抽象类，继承自SingleRel
  private abstract static class TestSingleRel extends SingleRel { // 继承自SingleRel，表示有一个输入的关系表达式
    protected TestSingleRel( // 受保护的构造函数
        RelOptCluster cluster, // 关系表达式集群
        RelTraitSet traits, // trait集合
        RelNode child) { // 子节点
      super(cluster, traits, child); // 调用父类构造函数
    }

    // implement RelNode // 实现RelNode接口
    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本
        RelMetadataQuery mq) { // 元数据查询对象
      return planner.getCostFactory().makeInfiniteCost(); // 返回无穷大成本，默认实现
    }

    // implement RelNode // 实现RelNode接口
    protected RelDataType deriveRowType() { // 推导行类型
      return getInput().getRowType(); // 返回输入的行类型
    }

    // TODO: SWZ Implement clone? // TODO: 需要实现clone方法
  }

  /** Relational expression with one input, of NONE convention. */ // NONE convention的单输入关系表达式
  private static class NoneSingleRel extends TestSingleRel { // 继承自TestSingleRel，使用NONE convention
    protected NoneSingleRel( // 受保护的构造函数
        RelOptCluster cluster, // 关系表达式集群
        RelNode child) { // 子节点
      this( // 调用另一个构造函数
          cluster, // 传入集群
          cluster.traitSetOf(Convention.NONE), // 使用NONE convention的trait集合
          child); // 传入子节点
    }

    protected NoneSingleRel( // 受保护的构造函数，允许自定义trait集合
        RelOptCluster cluster, // 关系表达式集群
        RelTraitSet traitSet, // 自定义trait集合
        RelNode child) { // 子节点
      super(cluster, traitSet, child); // 调用父类构造函数
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
      return new NoneSingleRel( // 创建新的NoneSingleRel实例
          getCluster(), // 获取集群
          traitSet, // 使用新的trait集合
          sole(inputs)); // 使用唯一的输入
    }
  }


  /** A mix-in interface to extend {@link RelNode}, for testing. */ // 混入接口，用于扩展RelNode，测试用
  interface FooRel extends EnumerableRel { // FooRel继承自EnumerableRel，表示可枚举的关系表达式
  }

  /** Relational expression with one input, that implements the {@link FooRel}
   * mix-in interface. */ // 实现FooRel接口的单输入关系表达式，使用ENUMERABLE convention
  private static class IterSingleRel extends TestSingleRel implements FooRel { // 继承自TestSingleRel并实现FooRel接口
    IterSingleRel(RelOptCluster cluster, RelNode child) { // 构造函数
      super( // 调用父类构造函数
          cluster, // 传入集群
          cluster.traitSetOf(EnumerableConvention.INSTANCE), // 使用ENUMERABLE convention的trait集合
          child); // 传入子节点
    }

    // implement RelNode // 实现RelNode接口
    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本
        RelMetadataQuery mq) { // 元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回极小的成本
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
      assert traitSet.comprises(EnumerableConvention.INSTANCE); // 断言trait集合包含ENUMERABLE convention
      return new IterSingleRel( // 创建新的IterSingleRel实例
          getCluster(), // 获取集群
          sole(inputs)); // 使用唯一的输入
    }

    @Override public Result implement(EnumerableRelImplementor implementor, // 实现可枚举的关系表达式
        Prefer pref) { // 偏好设置
      return null; // 返回null，测试用不需要实际实现
    }
  }

  /** Relational expression with zero inputs, of the PHYS convention. */ // 转换规则，将NoneLeafRel转换为PhysLeafRel
  public static class PhysLeafRule extends RelRule<PhysLeafRule.Config> { // 继承自RelRule，定义转换规则
    static final PhysLeafRule INSTANCE = ImmutablePhysLeafRuleConfig.builder().build() // 创建规则实例
        .withOperandSupplier(b -> // 设置操作数提供器
            b.operand(NoneLeafRel.class).anyInputs()) // 匹配NoneLeafRel，任意输入
        .as(Config.class) // 转换为Config接口
        .toRule(); // 转换为规则

    PhysLeafRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public Convention getOutConvention() { // 获取输出convention
      return PHYS_CALLING_CONVENTION; // 返回PHYS_CALLING_CONVENTION
    }

    @Override public void onMatch(RelOptRuleCall call) { // 当规则匹配时调用
      NoneLeafRel leafRel = call.rel(0); // 获取匹配的关系表达式
      call.transformTo( // 转换为新的关系表达式
          new PhysLeafRel( // 创建PhysLeafRel实例
              leafRel.getCluster(), // 使用原集群
              leafRel.getLabel())); // 使用原标签
    }

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // 使用Immutables注解生成不可变配置类
    @Value.Style(init = "with*", typeImmutable = "ImmutablePhysLeafRuleConfig") // 设置Immutables样式
    public interface Config extends RelRule.Config { // 配置接口继承自RelRule.Config
      @Override default PhysLeafRule toRule() { // 转换为规则
        return new PhysLeafRule(this); // 创建PhysLeafRule实例
      }
    }
  }

  /** Relational expression with zero input, of NONE convention, and tiny cost. */ // NONE convention的叶子关系表达式，成本极小
  private static class NoneTinyLeafRel extends TestLeafRel { // 继承自TestLeafRel
    protected NoneTinyLeafRel( // 受保护的构造函数
        RelOptCluster cluster, // 关系表达式集群
        String label) { // 标签
      super( // 调用父类构造函数
          cluster, // 传入集群
          cluster.traitSetOf(Convention.NONE), // 使用NONE convention的trait集合
          label); // 传入标签
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
      return new NoneTinyLeafRel(getCluster(), getLabel()); // 创建新的NoneTinyLeafRel实例
    }

    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本
                                      RelMetadataQuery mq) { // 元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回极小的成本
    }
  }

  /** Planner rule to convert a {@link NoneSingleRel} to ENUMERABLE
   * convention. */ // 转换规则，将NoneSingleRel转换为ENUMERABLE convention的IterSingleRel
  public static class IterSingleRule // 定义转换规则
      extends RelRule<IterSingleRule.Config> { // 继承自RelRule
    static final IterSingleRule INSTANCE = ImmutableIterSingleRuleConfig.builder() // 创建规则实例
        .build() // 构建配置
        .withOperandSupplier(b -> // 设置操作数提供器
            b.operand(NoneSingleRel.class).anyInputs()) // 匹配NoneSingleRel，任意输入
        .as(Config.class) // 转换为Config接口
        .toRule(); // 转换为规则

    IterSingleRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public Convention getOutConvention() { // 获取输出convention
      return EnumerableConvention.INSTANCE; // 返回ENUMERABLE convention
    }

    @Override public RelTrait getOutTrait() { // 获取输出trait
      return getOutConvention(); // 返回输出convention
    }

    @Override public void onMatch(RelOptRuleCall call) { // 当规则匹配时调用
      NoneSingleRel rel = call.rel(0); // 获取匹配的关系表达式

      RelNode converted = // 转换子节点
          convert( // 调用转换方法
              rel.getInput(0), // 获取输入
              rel.getTraitSet().replace(getOutTrait())); // 替换trait为输出trait

      call.transformTo( // 转换为新的关系表达式
          new IterSingleRel( // 创建IterSingleRel实例
              rel.getCluster(), // 使用原集群
              converted)); // 使用转换后的子节点
    }

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // 使用Immutables注解
    @Value.Style(init = "with*", typeImmutable = "ImmutableIterSingleRuleConfig") // 设置样式
    public interface Config extends RelRule.Config { // 配置接口
      @Override default IterSingleRule toRule() { // 转换为规则
        return new IterSingleRule(this); // 创建IterSingleRule实例
      }
    }
  }

  /** Another planner rule to convert a {@link NoneSingleRel} to ENUMERABLE
   * convention. */ // 另一个转换规则，将NoneSingleRel转换为ENUMERABLE convention的IterSingleRel（版本2）
  public static class IterSingleRule2 // 定义转换规则2
      extends RelRule<IterSingleRule2.Config> { // 继承自RelRule
    static final IterSingleRule2 INSTANCE = ImmutableIterSingleRule2Config.builder() // 创建规则实例
        .build() // 构建配置
        .withOperandSupplier(b -> // 设置操作数提供器
            b.operand(NoneSingleRel.class).anyInputs()) // 匹配NoneSingleRel，任意输入
        .as(Config.class) // 转换为Config接口
        .toRule(); // 转换为规则

    IterSingleRule2(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public Convention getOutConvention() { // 获取输出convention
      return EnumerableConvention.INSTANCE; // 返回ENUMERABLE convention
    }

    @Override public RelTrait getOutTrait() { // 获取输出trait
      return getOutConvention(); // 返回输出convention
    }

    @Override public void onMatch(RelOptRuleCall call) { // 当规则匹配时调用
      NoneSingleRel rel = call.rel(0); // 获取匹配的关系表达式

      RelNode converted = // 转换子节点
          convert( // 调用转换方法
              rel.getInput(0), // 获取输入
              rel.getTraitSet().replace(getOutTrait())); // 替换trait为输出trait

      IterSingleRel child = // 创建中间的IterSingleRel
          new IterSingleRel( // 创建实例
              rel.getCluster(), // 使用原集群
              converted); // 使用转换后的子节点

      call.transformTo( // 转换为新的关系表达式
          new IterSingleRel( // 创建外层的IterSingleRel实例
              rel.getCluster(), // 使用原集群
              child)); // 使用中间的IterSingleRel作为子节点
    }

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // 使用Immutables注解
    @Value.Style(init = "with*", typeImmutable = "ImmutableIterSingleRule2Config") // 设置样式
    public interface Config extends RelRule.Config { // 配置接口
      @Override default IterSingleRule2 toRule() { // 转换为规则
        return new IterSingleRule2(this); // 创建IterSingleRule2实例
      }
    }
  }

  /** Planner rule that converts between {@link AltTrait}s. */ // 转换规则，在不同的AltTrait之间进行转换
  private static class AltTraitConverterRule extends ConverterRule { // 继承自ConverterRule
    static AltTraitConverterRule create(AltTrait fromTrait, AltTrait toTrait, // 静态工厂方法，创建转换规则
        String description) { // 规则描述
      return Config.INSTANCE // 使用默认配置
          .withConversion(RelNode.class, fromTrait, toTrait, description) // 设置转换参数
          .withRuleFactory(AltTraitConverterRule::new) // 设置规则工厂
          .toRule(AltTraitConverterRule.class); // 转换为规则
    }

    private final RelTrait toTrait; // 目标trait

    AltTraitConverterRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
      this.toTrait = config.outTrait(); // 保存目标trait
    }

    @Override public RelNode convert(RelNode rel) { // 转换关系表达式
      return new AltTraitConverter( // 创建AltTraitConverter实例
          rel.getCluster(), // 使用原集群
          rel, // 使用原关系表达式
          toTrait); // 转换到目标trait
    }

    public boolean isGuaranteed() { // 判断转换是否保证成功
      return true; // 返回true，表示保证成功
    }
  }

  /** Relational expression that converts between {@link AltTrait} values. */ // 转换器关系表达式，在不同的AltTrait值之间进行转换
  private static class AltTraitConverter extends ConverterImpl { // 继承自ConverterImpl
    private final RelTrait toTrait; // 目标trait

    private AltTraitConverter( // 私有构造函数
        RelOptCluster cluster, // 关系表达式集群
        RelNode child, // 子节点
        RelTrait toTrait) { // 目标trait
      super( // 调用父类构造函数
          cluster, // 传入集群
          toTrait.getTraitDef(), // 传入trait定义
          child.getTraitSet().replace(toTrait), // 替换子节点的trait为目标trait
          child); // 传入子节点

      this.toTrait = toTrait; // 保存目标trait
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
      return new AltTraitConverter( // 创建新的AltTraitConverter实例
          getCluster(), // 获取集群
          sole(inputs), // 使用唯一的输入
          toTrait); // 使用目标trait
    }
  }

  /** Planner rule that converts from PHYS to ENUMERABLE convention. */ // 转换规则，将PHYS convention转换为ENUMERABLE convention
  private static class PhysToIteratorConverterRule extends ConverterRule { // 继承自ConverterRule
    static final PhysToIteratorConverterRule INSTANCE = Config.INSTANCE // 创建规则实例
        .withConversion(RelNode.class, PHYS_CALLING_CONVENTION, // 设置转换参数：从PHYS到ENUMERABLE
            EnumerableConvention.INSTANCE, "PhysToIteratorRule") // 使用描述"PhysToIteratorRule"
        .withRuleFactory(PhysToIteratorConverterRule::new) // 设置规则工厂
        .toRule(PhysToIteratorConverterRule.class); // 转换为规则

    PhysToIteratorConverterRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public RelNode convert(RelNode rel) { // 转换关系表达式
      return new PhysToIteratorConverter( // 创建PhysToIteratorConverter实例
          rel.getCluster(), // 使用原集群
          rel); // 使用原关系表达式
    }
  }

  /** Planner rule that converts PHYS to ENUMERABLE convention. */ // 转换器关系表达式，将PHYS convention转换为ENUMERABLE convention
  private static class PhysToIteratorConverter extends ConverterImpl { // 继承自ConverterImpl
    PhysToIteratorConverter( // 构造函数
        RelOptCluster cluster, // 关系表达式集群
        RelNode child) { // 子节点
      super( // 调用父类构造函数
          cluster, // 传入集群
          ConventionTraitDef.INSTANCE, // 传入Convention trait定义
          child.getTraitSet().replace(EnumerableConvention.INSTANCE), // 替换子节点的trait为ENUMERABLE
          child); // 传入子节点
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
      return new PhysToIteratorConverter( // 创建新的PhysToIteratorConverter实例
          getCluster(), // 获取集群
          sole(inputs)); // 使用唯一的输入
    }
  }

  /** Planner rule that converts an {@link IterSingleRel} on a
   * {@link PhysToIteratorConverter} into a {@link IterMergedRel}. */ // 合并规则，将IterSingleRel和PhysToIteratorConverter合并为IterMergedRel
  public static class IterSinglePhysMergeRule // 定义合并规则
      extends RelRule<IterSinglePhysMergeRule.Config> { // 继承自RelRule
    static final IterSinglePhysMergeRule INSTANCE = // 创建规则实例
        ImmutableIterSinglePhysMergeRuleConfig.builder().build() // 构建配置
            .withOperandSupplier(b0 -> // 设置操作数提供器
                b0.operand(IterSingleRel.class).oneInput(b1 -> // 匹配IterSingleRel，有一个输入
                    b1.operand(PhysToIteratorConverter.class).anyInputs())) // 输入是PhysToIteratorConverter
            .as(Config.class) // 转换为Config接口
            .toRule(); // 转换为规则

    protected IterSinglePhysMergeRule(Config config) { // 受保护的构造函数
      super(config); // 调用父类构造函数
    }

    @Override public void onMatch(RelOptRuleCall call) { // 当规则匹配时调用
      IterSingleRel singleRel = call.rel(0); // 获取匹配的IterSingleRel
      call.transformTo( // 转换为新的关系表达式
          new IterMergedRel(singleRel.getCluster(),  null)); // 创建IterMergedRel实例
    }

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // 使用Immutables注解
    @Value.Style(init = "with*", typeImmutable = "ImmutableIterSinglePhysMergeRuleConfig") // 设置样式
    public interface Config extends RelRule.Config { // 配置接口
      @Override default IterSinglePhysMergeRule toRule() { // 转换为规则
        return new IterSinglePhysMergeRule(this); // 创建IterSinglePhysMergeRule实例
      }
    }
  }

  /** Relational expression with no inputs, that implements the {@link FooRel}
   * mix-in interface. */ // 合并后的关系表达式，没有输入，实现FooRel接口
  private static class IterMergedRel extends TestLeafRel implements FooRel { // 继承自TestLeafRel并实现FooRel接口
    IterMergedRel(RelOptCluster cluster, String label) { // 构造函数
      super( // 调用父类构造函数
          cluster, // 传入集群
          cluster.traitSetOf(EnumerableConvention.INSTANCE), // 使用ENUMERABLE convention的trait集合
          label); // 传入标签
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本
        RelMetadataQuery mq) { // 元数据查询对象
      return planner.getCostFactory().makeZeroCost(); // 返回零成本，表示合并后的节点成本为0
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制关系表达式
      assert traitSet.comprises(EnumerableConvention.INSTANCE); // 断言trait集合包含ENUMERABLE convention
      assert inputs.isEmpty(); // 断言输入列表为空
      return new IterMergedRel(getCluster(), this.getLabel()); // 创建新的IterMergedRel实例
    }

    @Override public Result implement(EnumerableRelImplementor implementor, // 实现可枚举的关系表达式
        Prefer pref) { // 偏好设置
      return null; // 返回null，测试用不需要实际实现
    }
  }
}
