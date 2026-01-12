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
 */ // Apache许可证头文件，声明版权和使用许可
package org.apache.calcite.plan.volcano; // 声明包名，该类属于org.apache.calcite.plan.volcano包，用于测试Volcano优化器中的特征转换功能

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数的调用约定（如逻辑约定、物理约定）
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef类，用于定义Convention特征的元数据和转换规则
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示优化过程中的集群，包含所有共享的优化上下文信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，用于表示关系表达式的成本估计
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，定义了查询优化器的核心接口
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示规则调用时的上下文信息
import org.apache.calcite.plan.RelRule; // 导入RelRule类，作为所有转换规则的基类
import org.apache.calcite.plan.RelTrait; // 导入RelTrait接口，表示关系表达式的一个特征（如分布、排序等）
import org.apache.calcite.plan.RelTraitDef; // 导入RelTraitDef类，用于定义特征的元数据和转换逻辑
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.plan.volcano.AbstractConverter.ExpandConversionRule; // 导入ExpandConversionRule类，用于展开特征转换规则
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式的基本单元
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值
import org.immutables.value.Value; // 导入Value注解，用于生成不可变值对象
import org.junit.jupiter.api.Test; // 导入Test注解，标记测试方法

import java.util.List; // 导入List接口，用于处理集合

import static org.apache.calcite.plan.volcano.PlannerTests.PHYS_CALLING_CONVENTION; // 导入物理调用约定常量
import static org.apache.calcite.plan.volcano.PlannerTests.TestLeafRel; // 导入测试用的叶子关系节点类
import static org.apache.calcite.plan.volcano.PlannerTests.TestSingleRel; // 导入测试用的单输入关系节点类
import static org.apache.calcite.plan.volcano.PlannerTests.newCluster; // 导入创建测试集群的工具方法

import static org.hamcrest.CoreMatchers.instanceOf; // 导入instanceOf匹配器，用于断言对象类型
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言工具类
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入assertTrue断言方法

/**
 * Unit test for {@link org.apache.calcite.rel.RelDistributionTraitDef}.
 * // 这是一个单元测试类，用于测试RelDistributionTraitDef（分布特征定义）的功能
 * // 该测试主要验证Volcano优化器中特征转换（Trait Conversion）的机制
 * // 测试重点：验证关系表达式在不同特征（Convention和Distribution）之间的转换能力
 * // 测试场景：从NONE约定转换为PHYSICAL约定，同时处理分布特征的转换（ANY -> RANDOM/SINGLETON）
 */ // 类级别的Javadoc注释，说明该测试类的用途
class TraitConversionTest { // 声明TraitConversionTest测试类，用于测试特征转换功能

  // 成员变量部分：定义测试中使用的特征定义和分布实例
  private static final ConvertRelDistributionTraitDef NEW_TRAIT_DEF_INSTANCE = // 声明一个静态常量，存储自定义的分布特征定义实例，用于测试
      new ConvertRelDistributionTraitDef(); // 创建ConvertRelDistributionTraitDef实例，该类负责处理SimpleDistribution特征的转换逻辑
  private static final SimpleDistribution SIMPLE_DISTRIBUTION_ANY = // 声明静态常量，表示任意分布（ANY），这是默认的分布方式
      new SimpleDistribution("ANY"); // 创建名为"ANY"的SimpleDistribution实例，表示数据可以任意分布
  private static final SimpleDistribution SIMPLE_DISTRIBUTION_RANDOM = // 声明静态常量，表示随机分布（RANDOM），数据随机分布在各个分区
      new SimpleDistribution("RANDOM"); // 创建名为"RANDOM"的SimpleDistribution实例，表示数据随机分布
  private static final SimpleDistribution SIMPLE_DISTRIBUTION_SINGLETON = // 声明静态常量，表示单例分布（SINGLETON），所有数据集中在单个节点
      new SimpleDistribution("SINGLETON"); // 创建名为"SINGLETON"的SimpleDistribution实例，表示数据集中在一个节点

  // 测试方法：验证特征转换的完整流程
  @Test void testTraitConversion() { // 使用@Test注解标记测试方法，验证特征转换功能
    final VolcanoPlanner planner = new VolcanoPlanner(); // 创建VolcanoPlanner优化器实例，这是基于Volcano算法的查询优化器
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 向优化器注册Convention特征定义，用于处理调用约定（逻辑/物理）的转换
    planner.addRelTraitDef(NEW_TRAIT_DEF_INSTANCE); // 向优化器注册自定义的分布特征定义，用于处理分布特征的转换

    planner.addRule(RandomSingleTraitRule.INSTANCE); // 添加RandomSingleTraitRule规则，用于将NoneSingleRel转换为RandomSingleRel
    planner.addRule(SingleLeafTraitRule.INSTANCE); // 添加SingleLeafTraitRule规则，用于将NoneLeafRel转换为SingletonLeafRel
    planner.addRule(ExpandConversionRule.INSTANCE); // 添加ExpandConversionRule规则，用于展开特征转换，确保所有必要的转换都被执行
    planner.setTopDownOpt(false); // 设置优化器使用自底向上的优化策略（false表示自底向上，true表示自顶向下）

    final RelOptCluster cluster = newCluster(planner); // 创建测试用的RelOptCluster集群，包含优化器和相关上下文
    final NoneLeafRel leafRel = new NoneLeafRel(cluster, "a"); // 创建NoneLeafRel叶子节点，使用NONE约定和ANY分布，标签为"a"
    final NoneSingleRel singleRel = new NoneSingleRel(cluster, leafRel); // 创建NoneSingleRel单输入节点，输入为leafRel，使用NONE约定和ANY分布
    final RelNode convertedRel = // 调用优化器的changeTraits方法，将singleRel的特征集转换为PHYS_CALLING_CONVENTION
        planner.changeTraits(singleRel, // 输入的关系节点
            cluster.traitSetOf(PHYS_CALLING_CONVENTION)); // 目标特征集：物理调用约定
    planner.setRoot(convertedRel); // 将转换后的关系节点设置为优化器的根节点，开始优化过程
    final RelNode result = planner.chooseDelegate().findBestExp(); // 执行优化，找到最优的表达式（findBestExp会触发完整的优化过程）

    // 验证结果：检查根节点是否为RandomSingleRel类型
    assertThat(result, instanceOf(RandomSingleRel.class)); // 断言result是RandomSingleRel的实例，验证转换规则是否正确应用
    assertTrue(result.getTraitSet().contains(PHYS_CALLING_CONVENTION)); // 断言结果的特征集包含物理调用约定
    assertTrue(result.getTraitSet().contains(SIMPLE_DISTRIBUTION_RANDOM)); // 断言结果的特征集包含RANDOM分布

    // 验证输入节点：检查RandomSingleRel的输入是否为BridgeRel
    final RelNode input = result.getInput(0); // 获取result的第一个输入节点（RandomSingleRel只有一个输入）
    assertThat(input, instanceOf(BridgeRel.class)); // 断言input是BridgeRel的实例，BridgeRel用于桥接不同的分布特征
    assertTrue(input.getTraitSet().contains(PHYS_CALLING_CONVENTION)); // 断言输入节点的特征集包含物理调用约定
    assertTrue(input.getTraitSet().contains(SIMPLE_DISTRIBUTION_RANDOM)); // 断言输入节点的特征集包含RANDOM分布

    // 验证叶子节点：检查BridgeRel的输入是否为SingletonLeafRel
    final RelNode input2 = input.getInput(0); // 获取input的第一个输入节点（BridgeRel只有一个输入）
    assertThat(input2, instanceOf(SingletonLeafRel.class)); // 断言input2是SingletonLeafRel的实例，叶子节点应该转换为单例分布
    assertTrue(input2.getTraitSet().contains(PHYS_CALLING_CONVENTION)); // 断言叶子节点的特征集包含物理调用约定
    assertTrue(input2.getTraitSet().contains(SIMPLE_DISTRIBUTION_SINGLETON)); // 断言叶子节点的特征集包含SINGLETON分布
  }

  /** Converts a {@link NoneSingleRel} (none convention, distribution any)
   * to {@link RandomSingleRel} (physical convention, distribution random).
   * // 这是一个内部静态类，继承自RelRule，定义了一个转换规则
   * // 规则功能：将NoneSingleRel（NONE约定，ANY分布）转换为RandomSingleRel（PHYSICAL约定，RANDOM分布）
   * // 该规则展示了如何在转换关系节点时同时改变多个特征（Convention和Distribution）
   */ // RandomSingleTraitRule类的Javadoc注释
  public static class RandomSingleTraitRule // 声明RandomSingleTraitRule类，这是一个转换规则类
      extends RelRule<RandomSingleTraitRule.Config> { // 继承RelRule基类，使用泛型指定配置类型为RandomSingleTraitRule.Config
    static final RandomSingleTraitRule INSTANCE = // 声明静态常量INSTANCE，这是该规则的唯一实例（单例模式）
        ImmutableRandomSingleTraitRuleConfig.builder() // 使用Immutables库的builder模式创建配置对象
        .build() // 构建配置对象
        .withOperandSupplier(b -> // 设置操作数提供者，定义规则匹配的关系节点类型
            b.operand(NoneSingleRel.class).anyInputs()) // 匹配NoneSingleRel类型的节点，不限制输入节点
        .toRule(); // 将配置转换为规则实例

    RandomSingleTraitRule(Config config) { // 构造方法，接收配置对象作为参数
      super(config); // 调用父类RelRule的构造方法，初始化规则
    }

    @Override public Convention getOutConvention() { // 重写getOutConvention方法，指定输出节点的调用约定
      return PHYS_CALLING_CONVENTION; // 返回物理调用约定，表示转换后的节点使用物理约定
    }

    @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法，当规则匹配成功时执行转换逻辑
      NoneSingleRel single = call.rel(0); // 从规则调用上下文中获取匹配的NoneSingleRel节点（索引0）
      RelNode input = single.getInput(); // 获取single节点的输入节点
      RelNode physInput = // 调用convert方法转换输入节点，使其匹配新的特征集
          convert(input, // 要转换的输入节点
              single.getTraitSet() // 获取single的当前特征集
                  .replace(PHYS_CALLING_CONVENTION) // 将Convention替换为PHYS_CALLING_CONVENTION
                  .plus(SIMPLE_DISTRIBUTION_RANDOM)); // 添加RANDOM分布特征
      call.transformTo( // 调用transformTo方法，执行实际的节点转换
          new RandomSingleRel( // 创建新的RandomSingleRel节点
              single.getCluster(), // 使用原节点的集群
              physInput)); // 使用转换后的输入节点
    }

    /** Rule configuration.
     * // 这是一个内部接口，定义了规则的配置
     * // 使用Immutables注解生成不可变的配置实现类
     */ // Config接口的Javadoc注释
    @Value.Immutable // 使用Immutables注解，自动生成不可变实现类
    @Value.Style(typeImmutable = "ImmutableRandomSingleTraitRuleConfig") // 设置生成的实现类名称
    public interface Config extends RelRule.Config { // 声明Config接口，继承RelRule.Config
      @Override default RandomSingleTraitRule toRule() { // 重写toRule方法，将配置转换为规则实例
        return new RandomSingleTraitRule(this); // 创建并返回RandomSingleRel规则实例
      }
    }
  }

  /** Rel with physical convention and random distribution.
   * // 这是一个内部静态类，继承自TestSingleRel
   * // 类功能：表示具有物理约定（PHYSICAL）和随机分布（RANDOM）的关系节点
   * // 该类用于测试中作为转换后的目标节点
   */ // RandomSingleRel类的Javadoc注释
  private static class RandomSingleRel extends TestSingleRel { // 声明RandomSingleRel类，继承TestSingleRel测试基类
    RandomSingleRel(RelOptCluster cluster, RelNode input) { // 构造方法，接收集群和输入节点
      super(cluster, // 调用父类构造方法，传入集群
          cluster.traitSetOf(PHYS_CALLING_CONVENTION) // 创建特征集，包含物理调用约定
              .plus(SIMPLE_DISTRIBUTION_RANDOM), input); // 添加RANDOM分布特征，并传入输入节点
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算节点的自身成本
        RelMetadataQuery mq) { // 参数：优化器实例和元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回一个极小的成本，使该节点在优化中优先被选择
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，创建节点的副本
      return new RandomSingleRel(getCluster(), sole(inputs)); // 创建新的RandomSingleRel实例，保持相同的集群和输入
    }
  }

  /** Converts {@link NoneLeafRel} (none convention, any distribution) to
   * {@link SingletonLeafRel} (physical convention, singleton distribution).
   * // 这是一个内部静态类，继承自RelRule，定义了一个转换规则
   * // 规则功能：将NoneLeafRel（NONE约定，ANY分布）转换为SingletonLeafRel（PHYSICAL约定，SINGLETON分布）
   * // 该规则展示了叶子节点的特征转换
   */ // SingleLeafTraitRule类的Javadoc注释
  public static class SingleLeafTraitRule // 声明SingleLeafTraitRule类，这是一个转换规则类
      extends RelRule<SingleLeafTraitRule.Config> { // 继承RelRule基类，使用泛型指定配置类型为SingleLeafTraitRule.Config
    static final SingleLeafTraitRule INSTANCE = // 声明静态常量INSTANCE，这是该规则的唯一实例（单例模式）
        ImmutableSingleLeafTraitRuleConfig.builder() // 使用Immutables库的builder模式创建配置对象
        .build() // 构建配置对象
        .withOperandSupplier(b -> // 设置操作数提供者，定义规则匹配的关系节点类型
            b.operand(NoneLeafRel.class).anyInputs()) // 匹配NoneLeafRel类型的节点，不限制输入节点
        .toRule(); // 将配置转换为规则实例

    SingleLeafTraitRule(Config config) { // 构造方法，接收配置对象作为参数
      super(config); // 调用父类RelRule的构造方法，初始化规则
    }

    @Override public Convention getOutConvention() { // 重写getOutConvention方法，指定输出节点的调用约定
      return PHYS_CALLING_CONVENTION; // 返回物理调用约定，表示转换后的节点使用物理约定
    }

    @Override public void onMatch(RelOptRuleCall call) { // 重写onMatch方法，当规则匹配成功时执行转换逻辑
      NoneLeafRel leafRel = call.rel(0); // 从规则调用上下文中获取匹配的NoneLeafRel节点（索引0）
      call.transformTo( // 调用transformTo方法，执行实际的节点转换
          new SingletonLeafRel(leafRel.getCluster(), leafRel.label)); // 创建新的SingletonLeafRel节点，使用原节点的集群和标签
    }

    /** Rule configuration.
     * // 这是一个内部接口，定义了规则的配置
     * // 使用Immutables注解生成不可变的配置实现类
     */ // Config接口的Javadoc注释
    @Value.Immutable // 使用Immutables注解，自动生成不可变实现类
    @Value.Style(typeImmutable = "ImmutableSingleLeafTraitRuleConfig") // 设置生成的实现类名称
    public interface Config extends RelRule.Config { // 声明Config接口，继承RelRule.Config
      @Override default SingleLeafTraitRule toRule() { // 重写toRule方法，将配置转换为规则实例
        return new SingleLeafTraitRule(this); // 创建并返回SingleLeafTraitRule规则实例
      }
    }
  }

  /** Rel with singleton distribution, physical convention.
   * // 这是一个内部静态类，继承自TestLeafRel
   * // 类功能：表示具有物理约定（PHYSICAL）和单例分布（SINGLETON）的叶子关系节点
   * // 该类用于测试中作为转换后的目标叶子节点
   */ // SingletonLeafRel类的Javadoc注释
  private static class SingletonLeafRel extends TestLeafRel { // 声明SingletonLeafRel类，继承TestLeafRel测试基类
    SingletonLeafRel(RelOptCluster cluster, String label) { // 构造方法，接收集群和标签
      super(cluster, // 调用父类构造方法，传入集群
          cluster.traitSetOf(PHYS_CALLING_CONVENTION) // 创建特征集，包含物理调用约定
              .plus(SIMPLE_DISTRIBUTION_SINGLETON), label); // 添加SINGLETON分布特征，并传入标签
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算节点的自身成本
        RelMetadataQuery mq) { // 参数：优化器实例和元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回一个极小的成本，使该节点在优化中优先被选择
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，创建节点的副本
      return new SingletonLeafRel(getCluster(), label); // 创建新的SingletonLeafRel实例，保持相同的集群和标签
    }
  }

  /** Bridges the {@link SimpleDistribution}, difference between
   * {@link SingletonLeafRel} and {@link RandomSingleRel}.
   * // 这是一个内部静态类，继承自TestSingleRel
   * // 类功能：作为桥接节点，处理不同分布特征之间的转换
   * // 该节点用于连接SINGLETON分布的叶子节点和RANDOM分布的单输入节点
   * // 在优化过程中，当输入和输出的分布特征不匹配时，会插入BridgeRel进行转换
   */ // BridgeRel类的Javadoc注释
  private static class BridgeRel extends TestSingleRel { // 声明BridgeRel类，继承TestSingleRel测试基类
    BridgeRel(RelOptCluster cluster, RelNode input) { // 构造方法，接收集群和输入节点
      super(cluster, // 调用父类构造方法，传入集群
          cluster.traitSetOf(PHYS_CALLING_CONVENTION) // 创建特征集，包含物理调用约定
              .plus(SIMPLE_DISTRIBUTION_RANDOM), input); // 添加RANDOM分布特征，并传入输入节点
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算节点的自身成本
        RelMetadataQuery mq) { // 参数：优化器实例和元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回一个极小的成本，使该节点在优化中优先被选择
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，创建节点的副本
      return new BridgeRel(getCluster(), sole(inputs)); // 创建新的BridgeRel实例，保持相同的集群和输入
    }
  }

  /** Dummy distribution for test (simplified version of RelDistribution).
   * // 这是一个内部静态类，实现了RelTrait接口
   * // 类功能：表示一个简化的分布特征，用于测试目的
   * // 该类模拟了Calcite中RelDistribution的功能，但进行了简化
   * // 支持三种分布类型：ANY（任意）、RANDOM（随机）、SINGLETON（单例）
   */ // SimpleDistribution类的Javadoc注释
  private static class SimpleDistribution implements RelTrait { // 声明SimpleDistribution类，实现RelTrait接口
    private final String name; // 声明私有成员变量name，用于存储分布类型的名称

    SimpleDistribution(String name) { // 构造方法，接收分布类型的名称
      this.name = name; // 将参数name赋值给成员变量name
    }

    @Override public String toString() { // 重写toString方法，返回分布类型的字符串表示
      return name; // 直接返回name成员变量的值
    }

    @Override public RelTraitDef getTraitDef() { // 重写getTraitDef方法，返回该特征的特征定义
      return NEW_TRAIT_DEF_INSTANCE; // 返回ConvertRelDistributionTraitDef实例
    }

    @Override public boolean satisfies(RelTrait trait) { // 重写satisfies方法，判断当前特征是否满足另一个特征
      return trait == this || trait == SIMPLE_DISTRIBUTION_ANY; // 如果特征相同或目标特征是ANY，则返回true（ANY可以满足任何分布）

    }

    @Override public void register(RelOptPlanner planner) {} // 重写register方法，向优化器注册该特征（空实现，因为不需要特殊注册逻辑）
  }

  /** Dummy distribution trait def for test (handles conversion of
   * SimpleDistribution).
   * // 这是一个内部静态类，继承自RelTraitDef<SimpleDistribution>
   * // 类功能：定义SimpleDistribution特征的元数据和转换逻辑
   * // 该类负责处理SimpleDistribution特征之间的转换，包括：
   * // 1. 判断是否可以转换（canConvert）
   * // 2. 执行实际的转换（convert）
   * // 3. 提供默认特征（getDefault）
   */ // ConvertRelDistributionTraitDef类的Javadoc注释
  private static class ConvertRelDistributionTraitDef // 声明ConvertRelDistributionTraitDef类
      extends RelTraitDef<SimpleDistribution> { // 继承RelTraitDef基类，指定泛型类型为SimpleDistribution

    @Override public Class<SimpleDistribution> getTraitClass() { // 重写getTraitClass方法，返回该特征定义对应的特征类
      return SimpleDistribution.class; // 返回SimpleDistribution的Class对象
    }

    @Override public String toString() { // 重写toString方法，返回特征定义的字符串表示
      return getSimpleName(); // 调用getSimpleName方法获取简单名称
    }

    @Override public String getSimpleName() { // 重写getSimpleName方法，返回特征定义的简单名称
      return "ConvertRelDistributionTraitDef"; // 返回字符串"ConvertRelDistributionTraitDef"
    }

    @Override public @Nullable RelNode convert(RelOptPlanner planner, RelNode rel, // 重写convert方法，执行特征转换
        SimpleDistribution toTrait, boolean allowInfiniteCostConverters) { // 参数：优化器、关系节点、目标特征、是否允许无限成本的转换器
      if (toTrait == SIMPLE_DISTRIBUTION_ANY) { // 如果目标特征是ANY分布
        return rel; // 直接返回原节点，因为ANY是默认分布，不需要转换
      }

      return new BridgeRel(rel.getCluster(), rel); // 创建并返回BridgeRel节点，将原节点包装在BridgeRel中以实现分布转换
    }

    @Override public boolean canConvert(RelOptPlanner planner, // 重写canConvert方法，判断是否可以进行特征转换
        SimpleDistribution fromTrait, SimpleDistribution toTrait) { // 参数：优化器、源特征、目标特征
      return (fromTrait == toTrait) // 如果源特征和目标特征相同，则可以转换（无需转换）
          || (toTrait == SIMPLE_DISTRIBUTION_ANY) // 如果目标特征是ANY，则可以转换（ANY是默认分布）
          || (fromTrait == SIMPLE_DISTRIBUTION_SINGLETON // 如果源特征是SINGLETON且目标特征是RANDOM
          && toTrait == SIMPLE_DISTRIBUTION_RANDOM); // 则可以转换（从单例分布转换为随机分布）

    }

    @Override public SimpleDistribution getDefault() { // 重写getDefault方法，返回默认的特征值
      return SIMPLE_DISTRIBUTION_ANY; // 返回ANY分布作为默认值
    }
  }

  /** Any distribution and none convention.
   * // 这是一个内部静态类，继承自TestLeafRel
   * // 类功能：表示具有任意分布（ANY）和NONE约定（逻辑约定）的叶子关系节点
   * // 该类作为测试的起始节点，代表逻辑层面的叶子节点
   */ // NoneLeafRel类的Javadoc注释
  private static class NoneLeafRel extends TestLeafRel { // 声明NoneLeafRel类，继承TestLeafRel测试基类
    NoneLeafRel(RelOptCluster cluster, String label) { // 构造方法，接收集群和标签
      super(cluster, cluster.traitSetOf(Convention.NONE), label); // 调用父类构造方法，使用NONE约定和默认分布
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，创建节点的副本
      assert traitSet.comprises(Convention.NONE, SIMPLE_DISTRIBUTION_ANY); // 断言特征集包含NONE约定和ANY分布
      assert inputs.isEmpty(); // 断言输入列表为空（叶子节点没有输入）
      return this; // 返回当前实例（叶子节点是不可变的，可以直接返回自己）
    }
  }

  /** Rel with any distribution and none convention.
   * // 这是一个内部静态类，继承自TestSingleRel
   * // 类功能：表示具有任意分布（ANY）和NONE约定（逻辑约定）的单输入关系节点
   * // 该类作为测试的中间节点，代表逻辑层面的单输入节点
   */ // NoneSingleRel类的Javadoc注释
  private static class NoneSingleRel extends TestSingleRel { // 声明NoneSingleRel类，继承TestSingleRel测试基类
    NoneSingleRel(RelOptCluster cluster, RelNode input) { // 构造方法，接收集群和输入节点
      super(cluster, cluster.traitSetOf(Convention.NONE), input); // 调用父类构造方法，使用NONE约定和默认分布
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，创建节点的副本
      assert traitSet.comprises(Convention.NONE, SIMPLE_DISTRIBUTION_ANY); // 断言特征集包含NONE约定和ANY分布
      return new NoneSingleRel(getCluster(), sole(inputs)); // 创建新的NoneSingleRel实例，保持相同的集群和输入
    }
  }
}