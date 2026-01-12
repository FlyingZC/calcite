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
package org.apache.calcite.plan.volcano; // 包声明：该测试类属于Volcano优化器包，用于测试排序特征的转换功能

import org.apache.calcite.plan.Convention; // 导入Convention类，表示关系表达式的调用约定（如逻辑、物理等）
import org.apache.calcite.plan.ConventionTraitDef; // 导入Convention特征定义类，用于定义调用约定特征
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示优化器集群，包含所有共享对象
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系表达式的执行成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，表示查询优化器
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示优化规则调用的上下文
import org.apache.calcite.plan.RelRule; // 导入RelRule基类，用于定义优化规则
import org.apache.calcite.plan.RelTraitDef; // 导入RelTraitDef基类，用于定义关系特征
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示一组关系特征的集合
import org.apache.calcite.plan.volcano.AbstractConverter.ExpandConversionRule; // 导入扩展转换规则，用于处理特征转换
import org.apache.calcite.rel.RelCollation; // 导入RelCollation接口，表示排序特征
import org.apache.calcite.rel.RelCollationImpl; // 导入RelCollationImpl类，提供RelCollation的默认实现
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类，表示单个字段的排序信息
import org.apache.calcite.rel.RelFieldCollation.Direction; // 导入Direction枚举，表示排序方向（升序、降序、聚集等）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式（查询计划树的节点）
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序操作的关系节点
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系节点的元数据
import org.apache.calcite.rex.RexNode; // 导入RexNode接口，表示行表达式（如条件、表达式等）

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值
import org.immutables.value.Value; // 导入Immutables注解，用于生成不可变值对象
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.List; // 导入Java集合List接口

import static org.apache.calcite.plan.volcano.PlannerTests.PHYS_CALLING_CONVENTION; // 导入物理调用约定常量
import static org.apache.calcite.plan.volcano.PlannerTests.TestLeafRel; // 导入测试用的叶子关系节点基类
import static org.apache.calcite.plan.volcano.PlannerTests.TestSingleRel; // 导入测试用的单输入关系节点基类
import static org.apache.calcite.plan.volcano.PlannerTests.newCluster; // 导入创建测试集群的工厂方法

import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest的instanceOf匹配器，用于断言对象类型
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具类
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5的assertTrue断言方法

/**
 * Unit test for {@link org.apache.calcite.rel.RelCollationTraitDef}. // 单元测试类：测试RelCollationTraitDef（排序特征定义）的功能
 * 该测试类验证Volcano优化器如何处理排序特征的转换，包括：
 * 1. 从无调用约定（Convention.NONE）到物理调用约定（PHYS_CALLING_CONVENTION）的转换
 * 2. 从叶子节点的排序特征（LEAF_COLLATION）到根节点的排序特征（ROOT_COLLATION）的转换
 * 3. 通过PhysicalSort节点实现排序特征的转换
 * 4. 验证优化器能够正确地应用转换规则并生成最优的执行计划
 */
class CollationConversionTest { // 测试类定义：CollationConversionTest，用于测试排序特征转换
  private static final TestRelCollationImpl LEAF_COLLATION = // 定义叶子节点的排序特征常量：表示叶子节点的排序方式
      new TestRelCollationImpl( // 创建测试用的排序特征实现实例
          ImmutableList.of(new RelFieldCollation(0, Direction.CLUSTERED))); // 创建包含一个字段的排序列表：第0个字段使用聚集排序方向

  private static final TestRelCollationImpl ROOT_COLLATION = // 定义根节点的排序特征常量：表示根节点的排序方式
      new TestRelCollationImpl(ImmutableList.of(new RelFieldCollation(0))); // 创建包含一个字段的排序列表：第0个字段使用默认排序方向（升序）

  private static final TestRelCollationTraitDef COLLATION_TRAIT_DEF = // 定义排序特征定义常量：用于管理排序特征的转换
      new TestRelCollationTraitDef(); // 创建测试用的排序特征定义实例

  @Test void testCollationConversion() { // 测试方法：测试排序特征的转换功能
    final VolcanoPlanner planner = new VolcanoPlanner(); // 创建Volcano优化器实例：基于成本的动态规划优化器
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 向优化器注册调用约定特征定义：用于管理物理/逻辑约定的转换
    planner.addRelTraitDef(COLLATION_TRAIT_DEF); // 向优化器注册排序特征定义：用于管理排序特征的转换

    planner.addRule(SingleNodeRule.INSTANCE); // 添加SingleNodeRule规则：用于将NoneSingleRel转换为RootSingleRel
    planner.addRule(LeafTraitRule.INSTANCE); // 添加LeafTraitRule规则：用于将NoneLeafRel转换为LeafRel
    planner.addRule(ExpandConversionRule.INSTANCE); // 添加扩展转换规则：用于处理特征转换的扩展
    planner.setTopDownOpt(false); // 设置优化器使用自底向上的优化策略：从叶子节点向根节点优化

    final RelOptCluster cluster = newCluster(planner); // 创建优化器集群：包含RexBuilder等共享对象
    final NoneLeafRel leafRel = new NoneLeafRel(cluster, "a"); // 创建叶子关系节点：使用无调用约定和叶子排序特征，标签为"a"
    final NoneSingleRel singleRel = new NoneSingleRel(cluster, leafRel); // 创建单输入关系节点：使用无调用约定和叶子排序特征，输入为leafRel
    final RelNode convertedRel = // 转换关系节点的特征：将singleRel转换为具有物理调用约定和根排序特征的节点
        planner.changeTraits(singleRel, // 要转换的关系节点
            cluster.traitSetOf(PHYS_CALLING_CONVENTION).plus(ROOT_COLLATION)); // 目标特征集：物理调用约定 + 根排序特征
    planner.setRoot(convertedRel); // 设置优化器的根节点：从该节点开始优化
    RelNode result = planner.chooseDelegate().findBestExp(); // 执行优化并找到最优执行计划：返回优化后的关系节点
    assertThat(result, instanceOf(RootSingleRel.class)); // 断言：结果应该是RootSingleRel类型（物理调用约定的根节点）
    assertTrue(result.getTraitSet().contains(ROOT_COLLATION)); // 断言：结果应该包含根排序特征
    assertTrue(result.getTraitSet().contains(PHYS_CALLING_CONVENTION)); // 断言：结果应该包含物理调用约定

    final RelNode input = result.getInput(0); // 获取结果的第一个输入节点：应该是PhysicalSort节点
    assertThat(input, instanceOf(PhysicalSort.class)); // 断言：输入应该是PhysicalSort类型（物理排序节点）
    assertTrue(result.getTraitSet().contains(ROOT_COLLATION)); // 断言：结果应该包含根排序特征（再次验证）
    assertTrue(input.getTraitSet().contains(PHYS_CALLING_CONVENTION)); // 断言：输入应该包含物理调用约定

    final RelNode input2 = input.getInput(0); // 获取PhysicalSort的输入节点：应该是LeafRel节点
    assertThat(input2, instanceOf(LeafRel.class)); // 断言：输入应该是LeafRel类型（物理叶子节点）
    assertTrue(input2.getTraitSet().contains(LEAF_COLLATION)); // 断言：输入应该包含叶子排序特征
    assertTrue(input.getTraitSet().contains(PHYS_CALLING_CONVENTION)); // 断言：输入应该包含物理调用约定（再次验证）
  }

  /** Converts a NoneSingleRel to RootSingleRel. */ // 单节点规则：将NoneSingleRel转换为RootSingleRel
  public static class SingleNodeRule // 单节点转换规则：继承自RelRule，用于优化单输入节点的转换
      extends RelRule<SingleNodeRule.Config> { // 泛型参数为规则的配置类型
    static final SingleNodeRule INSTANCE = ImmutableSingleNodeRuleConfig.builder() // 创建规则的唯一实例：使用建造者模式
        .withOperandSupplier(b -> b.operand(NoneSingleRel.class).anyInputs()) // 设置操作数提供者：匹配NoneSingleRel类型的节点，不限制输入
        .build() // 构建配置对象
        .toRule(); // 将配置转换为规则实例

    protected SingleNodeRule(Config config) { // 构造方法：接收配置对象
      super(config); // 调用父类RelRule的构造方法
    }

    @Override public Convention getOutConvention() { // 重写方法：获取输出调用约定
      return PHYS_CALLING_CONVENTION; // 返回物理调用约定：表示转换后的节点使用物理约定
    }

    @Override public void onMatch(RelOptRuleCall call) { // 重写方法：当规则匹配时执行转换
      NoneSingleRel single = call.rel(0); // 获取匹配的关系节点：NoneSingleRel类型
      RelNode input = single.getInput(); // 获取节点的输入：即叶子节点
      RelNode physInput = // 转换输入节点：将输入转换为具有物理调用约定和根排序特征的节点
          convert(input, // 要转换的节点
              single.getTraitSet() // 使用当前节点的特征集
                  .replace(PHYS_CALLING_CONVENTION) // 替换为物理调用约定
                  .plus(ROOT_COLLATION)); // 添加根排序特征
      call.transformTo( // 执行转换：创建新的关系节点
          new RootSingleRel( // 创建RootSingleRel节点
              single.getCluster(), // 使用相同的集群
              physInput)); // 使用转换后的输入节点
    }

    /** Rule configuration. */ // 规则配置接口：定义规则的配置属性
    @Value.Immutable // 使用Immutables注解生成不可变实现
    @Value.Style(init = "with*", typeImmutable = "ImmutableSingleNodeRuleConfig") // 设置代码生成风格：初始化方法以"with"开头，不可变类名为"ImmutableSingleNodeRuleConfig"
    public interface Config extends RelRule.Config { // 配置接口：继承自RelRule.Config
      @Override default SingleNodeRule toRule() { // 默认方法：将配置转换为规则实例
        return new SingleNodeRule(this); // 创建并返回SingleNodeRule实例
      }
    }
  }

  /** Root node with physical convention and ROOT_COLLATION trait. */ // 根节点类：具有物理调用约定和根排序特征
  private static class RootSingleRel extends TestSingleRel { // 根节点类：继承自TestSingleRel（测试用的单输入节点基类）
    RootSingleRel(RelOptCluster cluster, RelNode input) { // 构造方法：接收集群和输入节点
      super(cluster, // 调用父类构造方法：传入集群
          cluster.traitSetOf(PHYS_CALLING_CONVENTION).plus(ROOT_COLLATION), // 特征集：物理调用约定 + 根排序特征
          input); // 传入输入节点
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写方法：计算自身的执行成本
        RelMetadataQuery mq) { // 参数：元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回极小的成本：表示该节点执行成本很低
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写方法：复制节点
      return new RootSingleRel(getCluster(), sole(inputs)); // 创建新的RootSingleRel实例：使用相同的集群和唯一输入
    }
  }

  /** Converts a {@link NoneLeafRel} (with none convention) to {@link LeafRel}
   * (with physical convention). */ // 叶子特征规则：将NoneLeafRel（无调用约定）转换为LeafRel（物理调用约定）
  public static class LeafTraitRule // 叶子特征转换规则：继承自RelRule
      extends RelRule<LeafTraitRule.Config> { // 泛型参数为规则的配置类型
    static final LeafTraitRule INSTANCE = ImmutableLeafTraitRuleConfig.builder() // 创建规则的唯一实例：使用建造者模式
        .withOperandSupplier(b -> b.operand(NoneLeafRel.class).anyInputs()) // 设置操作数提供者：匹配NoneLeafRel类型的节点
        .build() // 构建配置对象
        .toRule(); // 将配置转换为规则实例

    LeafTraitRule(Config config) { // 构造方法：接收配置对象
      super(config); // 调用父类RelRule的构造方法
    }

    @Override public Convention getOutConvention() { // 重写方法：获取输出调用约定
      return PHYS_CALLING_CONVENTION; // 返回物理调用约定：表示转换后的节点使用物理约定
    }

    @Override public void onMatch(RelOptRuleCall call) { // 重写方法：当规则匹配时执行转换
      NoneLeafRel leafRel = call.rel(0); // 获取匹配的关系节点：NoneLeafRel类型
      call.transformTo(new LeafRel(leafRel.getCluster(), leafRel.label)); // 执行转换：创建LeafRel节点，使用相同的集群和标签
    }

    /** Rule configuration. */ // 规则配置接口：定义规则的配置属性
    @Value.Immutable // 使用Immutables注解生成不可变实现
    @Value.Style(init = "with*", typeImmutable = "ImmutableLeafTraitRuleConfig") // 设置代码生成风格：初始化方法以"with"开头，不可变类名为"ImmutableLeafTraitRuleConfig"
    public interface Config extends RelRule.Config { // 配置接口：继承自RelRule.Config
      @Override default LeafTraitRule toRule() { // 默认方法：将配置转换为规则实例
        return new LeafTraitRule(this); // 创建并返回LeafTraitRule实例
      }
    }
  }

  /** Leaf node with physical convention and LEAF_COLLATION trait. */ // 叶子节点类：具有物理调用约定和叶子排序特征
  private static class LeafRel extends TestLeafRel { // 叶子节点类：继承自TestLeafRel（测试用的叶子节点基类）
    LeafRel(RelOptCluster cluster, String label) { // 构造方法：接收集群和标签
      super(cluster, // 调用父类构造方法：传入集群
          cluster.traitSetOf(PHYS_CALLING_CONVENTION).plus(LEAF_COLLATION), // 特征集：物理调用约定 + 叶子排序特征
          label); // 传入标签
    }

    public @Nullable RelOptCost computeSelfCost( // 计算自身的执行成本
        RelOptPlanner planner, // 参数：优化器对象
        RelMetadataQuery mq) { // 参数：元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回极小的成本：表示该节点执行成本很低
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制节点
      return new LeafRel(getCluster(), label); // 创建新的LeafRel实例：使用相同的集群和标签
    }
  }

  /** Leaf node with none convention and LEAF_COLLATION trait. */ // 无调用约定的叶子节点类：具有叶子排序特征
  private static class NoneLeafRel extends TestLeafRel { // 无调用约定的叶子节点类：继承自TestLeafRel
    NoneLeafRel(RelOptCluster cluster, String label) { // 构造方法：接收集群和标签
      super(cluster, cluster.traitSetOf(Convention.NONE).plus(LEAF_COLLATION), // 特征集：无调用约定 + 叶子排序特征
          label); // 传入标签
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写方法：复制节点
      assert traitSet.comprises(Convention.NONE, LEAF_COLLATION); // 断言：特征集必须包含无调用约定和叶子排序特征
      assert inputs.isEmpty(); // 断言：输入列表必须为空（叶子节点没有输入）
      return this; // 返回自身：叶子节点是不可变的，直接返回this
    }
  }

  /** A single-input node with none convention and LEAF_COLLATION trait. */ // 单输入节点类：具有无调用约定和叶子排序特征
  private static class NoneSingleRel extends TestSingleRel { // 无调用约定的单输入节点类：继承自TestSingleRel
    NoneSingleRel(RelOptCluster cluster, RelNode input) { // 构造方法：接收集群和输入节点
      super(cluster, cluster.traitSetOf(Convention.NONE).plus(LEAF_COLLATION), // 特征集：无调用约定 + 叶子排序特征
          input); // 传入输入节点
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制节点
      assert traitSet.comprises(Convention.NONE, LEAF_COLLATION); // 断言：特征集必须包含无调用约定和叶子排序特征
      return new NoneSingleRel(getCluster(), sole(inputs)); // 创建新的NoneSingleRel实例：使用相同的集群和唯一输入
    }
  }

  /** Dummy collation trait implementation for the test. */ // 测试用的排序特征实现类
  private static class TestRelCollationImpl extends RelCollationImpl { // 排序特征实现类：继承自RelCollationImpl
    TestRelCollationImpl(ImmutableList<RelFieldCollation> fieldCollations) { // 构造方法：接收字段排序列表
      super(fieldCollations); // 调用父类构造方法：传入字段排序列表
    }

    @Override public RelTraitDef getTraitDef() { // 重写方法：获取该特征的特征定义
      return COLLATION_TRAIT_DEF; // 返回测试用的排序特征定义
    }
  }

  /** Dummy collation trait def implementation for the test (uses
   * {@link PhysicalSort} below). */ // 测试用的排序特征定义类（使用下面的PhysicalSort实现转换）
  private static class TestRelCollationTraitDef // 排序特征定义类：继承自RelTraitDef<RelCollation>
      extends RelTraitDef<RelCollation> { // 泛型参数为RelCollation类型
    public Class<RelCollation> getTraitClass() { // 获取特征类
      return RelCollation.class; // 返回RelCollation类
    }

    public String getSimpleName() { // 获取特征的简单名称
      return "testsort"; // 返回"testsort"作为特征名称
    }

    @Override public boolean multiple() { // 判断是否允许多个特征实例
      return true; // 返回true：表示可以同时具有多个排序特征
    }

    public RelCollation getDefault() { // 获取默认的排序特征
      return LEAF_COLLATION; // 返回叶子排序特征作为默认值
    }

    public @Nullable RelNode convert(RelOptPlanner planner, RelNode rel, // 转换关系节点的排序特征
        RelCollation toCollation, boolean allowInfiniteCostConverters) { // 参数：目标排序特征、是否允许无限成本转换器
      if (toCollation.getFieldCollations().isEmpty()) { // 检查目标排序特征是否为空
        // An empty sort doesn't make sense. // 空的排序没有意义
        return null; // 返回null：表示无法转换
      }

      return new PhysicalSort(rel.getCluster(), // 创建PhysicalSort节点：通过物理排序实现排序特征转换
          rel.getTraitSet().replace(toCollation), rel, toCollation, null, null); // 参数：集群、替换后的特征集、输入节点、排序特征、offset（null）、fetch（null）
    }

    public boolean canConvert(RelOptPlanner planner, RelCollation fromTrait, // 判断是否可以从一个排序特征转换到另一个
        RelCollation toTrait) { // 参数：源排序特征、目标排序特征
      return true; // 返回true：表示任何排序特征之间都可以转换
    }
  }

  /** Physical sort node (not logical). */ // 物理排序节点类（非逻辑排序）
  private static class PhysicalSort extends Sort { // 物理排序类：继承自Sort（排序操作的关系节点）
    PhysicalSort(RelOptCluster cluster, RelTraitSet traits, RelNode input, // 构造方法：接收集群、特征集、输入节点、排序特征、offset、fetch
        RelCollation collation, @Nullable RexNode offset, // 参数：排序特征、偏移量（用于分页）
        @Nullable RexNode fetch) { // 参数：获取行数（用于分页）
      super(cluster, traits, input, collation, offset, fetch); // 调用父类Sort的构造方法
    }

    public Sort copy(RelTraitSet traitSet, RelNode newInput, // 复制节点：创建新的物理排序节点
        RelCollation newCollation, @Nullable RexNode offset, // 参数：新的特征集、新的输入节点、新的排序特征、offset、fetch
        @Nullable RexNode fetch) { // 参数：获取行数
      return new PhysicalSort(getCluster(), traitSet, newInput, newCollation, // 创建并返回新的PhysicalSort实例
          offset, fetch); // 传入offset和fetch参数
    }

    public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身的执行成本
        RelMetadataQuery mq) { // 参数：元数据查询对象
      return planner.getCostFactory().makeTinyCost(); // 返回极小的成本：表示该节点执行成本很低
    }
  }
}
