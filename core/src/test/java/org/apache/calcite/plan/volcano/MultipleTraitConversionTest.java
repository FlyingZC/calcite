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
package org.apache.calcite.plan.volcano; // 测试类所在的包，属于VolcanoPlanner的测试包

import org.apache.calcite.plan.ConventionTraitDef; // 导入Convention特征定义，用于定义关系代数节点的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，用于管理关系代数节点的共享资源
import org.apache.calcite.plan.RelOptCost; // 导入关系优化代价，用于衡量执行计划的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化规划器接口
import org.apache.calcite.plan.RelTrait; // 导入关系特征接口，表示关系代数节点的属性
import org.apache.calcite.plan.RelTraitDef; // 导入关系特征定义接口，用于定义特定类型的特征
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，表示一组关系特征的组合
import org.apache.calcite.rel.RelCollationTraitDef; // 导入关系排序特征定义，用于定义排序规则
import org.apache.calcite.rel.RelCollations; // 导入关系排序工具类，用于创建排序对象
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，表示关系代数表达式
import org.apache.calcite.rel.SingleRel; // 导入单输入关系节点基类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询接口，用于获取节点的元数据信息
import org.apache.calcite.util.ImmutableIntList; // 导入不可变整数列表，用于表示字段索引列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的返回值
import org.junit.jupiter.api.Test; // 导入JUnit测试注解，标记测试方法

import java.util.List; // 导入Java集合框架中的List接口

import static org.apache.calcite.plan.volcano.PlannerTests.newCluster; // 导入测试工具方法，用于创建测试用的关系优化集群

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器，用于断言对象相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest匹配器，用于断言集合大小
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit断言方法，用于断言条件为真

/**
 * Tests that ensures that we do not add enforcers for the already satisfied traits. // 测试类的主要目的：确保不会为已经满足的特征添加强制器（enforcer）
 * See https://issues.apache.org/jira/browse/CALCITE-4466 for more information. // 参考JIRA issue CALCITE-4466了解更多背景信息
 * 这个测试类用于验证VolcanoPlanner在进行多特征转换时的正确性，特别是当源特征已经满足目标特征时，不应该添加不必要的强制器
 */
public class MultipleTraitConversionTest { // 测试类定义：多特征转换测试类
  @SuppressWarnings("ConstantConditions") // 抑制常量条件警告，表示代码中可能存在看似总是为真的条件
  @Test void testMultipleTraitConversion() { // 测试方法：测试多特征转换功能
    VolcanoPlanner planner = new VolcanoPlanner(); // 创建一个新的VolcanoPlanner实例，这是Calcite中基于代价的优化规划器

    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 向规划器添加Convention特征定义，用于管理调用约定特征
    planner.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 向规划器添加RelCollation特征定义，用于管理排序特征
    planner.addRelTraitDef(CustomTraitDef.INSTANCE); // 向规划器添加自定义特征定义，用于测试自定义特征转换
    planner.setNoneConventionHasInfiniteCost(false); // 设置None约定不具有无限代价，允许在转换过程中使用None约定

    RelOptCluster cluster = newCluster(planner); // 创建一个关系优化集群，用于管理测试中所有关系节点的共享资源

    RelTraitSet fromTraits = cluster.traitSetOf(RelCollations.of(ImmutableIntList.of(0, 1))); // 创建源特征集合：包含排序特征[0, 1]，表示按第0列和第1列升序排序

    RelTraitSet toTraits = fromTraits // 创建目标特征集合：基于源特征集合进行修改
        .plus(RelCollations.of(0)) // 添加排序特征[0]，表示只按第0列排序（注意：源特征[0,1]已经满足目标特征[0]）
        .plus(CustomTrait.TO); // 添加自定义特征TO，表示需要转换为TO状态

    CustomLeafRel rel = new CustomLeafRel(cluster, fromTraits); // 创建一个自定义叶子关系节点，使用源特征集合
    planner.setRoot(rel); // 将该关系节点设置为规划器的根节点，开始优化过程

    RelNode convertedRel = planner.changeTraitsUsingConverters(rel, toTraits); // 使用转换器将关系节点的特征从源特征转换为目标特征
    assertThat(convertedRel.getClass(), is(CustomTraitEnforcer.class)); // 断言转换后的关系节点类型是CustomTraitEnforcer（自定义特征强制器）
    assertTrue(convertedRel.getTraitSet().satisfies(toTraits)); // 断言转换后的节点特征集合满足目标特征集合

    // Make sure that the equivalence set contains only the original and converted rels. // 确保等价集合只包含原始关系节点和转换后的关系节点
    // It should not contain the collation enforcer, because the "from" collation already // 不应该包含排序强制器，因为源排序特征已经满足目标排序特征
    // satisfies the "to" collation. // 源特征[0,1]已经满足目标特征[0]，所以不需要额外的排序强制器
    List<RelNode> rels = planner.getSubset(rel).set.rels; // 获取关系节点所在子集的等价集合中的所有关系节点
    assertThat(rels, hasSize(2)); // 断言等价集合中只有2个关系节点（原始节点和转换后的节点）
    assertTrue(rels.stream().anyMatch(r -> r instanceof CustomLeafRel)); // 断言等价集合中至少有一个CustomLeafRel类型的节点
    assertTrue(rels.stream().anyMatch(r -> r instanceof CustomTraitEnforcer)); // 断言等价集合中至少有一个CustomTraitEnforcer类型的节点
  }

  /**
   * Leaf rel. // 自定义叶子关系节点类，用于测试
   */
  private static class CustomLeafRel extends PlannerTests.TestLeafRel { // 自定义叶子关系节点，继承自测试用的叶子关系节点基类
    CustomLeafRel(RelOptCluster cluster, RelTraitSet traits) { // 构造方法：创建自定义叶子关系节点
      super(cluster, traits, CustomLeafRel.class.getSimpleName()); // 调用父类构造方法，传入集群、特征集合和节点名称
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法：创建节点副本
      return new CustomLeafRel(getCluster(), traitSet); // 返回一个新的CustomLeafRel实例，使用新的特征集合
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法：计算节点自身的执行代价
        RelMetadataQuery mq) { // mq参数：关系元数据查询对象，用于获取节点的元数据信息
      return planner.getCostFactory().makeTinyCost(); // 返回一个极小的代价值，表示该节点的执行成本很低
    }
  }

  /**
   * An enforcer used by the custom trait def. // 自定义特征强制器类，用于强制应用自定义特征
   */
  private static class CustomTraitEnforcer extends SingleRel { // 自定义特征强制器，继承自单输入关系节点基类
    private CustomTraitEnforcer(RelOptCluster cluster, RelTraitSet traits, RelNode input) { // 私有构造方法：创建自定义特征强制器
      super(cluster, traits, input); // 调用父类构造方法，传入集群、特征集合和输入关系节点
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法：创建强制器副本
      return new CustomTraitEnforcer(getCluster(), traitSet, inputs.get(0)); // 返回一个新的CustomTraitEnforcer实例，使用新的特征集合和输入节点
    }
  }

  /**
   * Custom trait. // 自定义特征类，用于测试特征转换机制
   */
  private static class CustomTrait implements RelTrait { // 自定义特征类，实现RelTrait接口

    private static final CustomTrait FROM = new CustomTrait("FROM"); // 定义FROM状态的自定义特征常量，表示初始状态
    private static final CustomTrait TO = new CustomTrait("TO"); // 定义TO状态的自定义特征常量，表示目标状态

    private final String label; // 成员变量：特征标签，用于标识特征的状态

    private CustomTrait(String label) { // 私有构造方法：创建自定义特征实例
      this.label = label; // 初始化特征标签
    }

    @SuppressWarnings("rawtypes") // 抑制原始类型警告
    @Override public RelTraitDef getTraitDef() { // 重写getTraitDef方法：获取该特征对应的特征定义
      return CustomTraitDef.INSTANCE; // 返回自定义特征定义的单例实例
    }

    @Override public boolean satisfies(RelTrait trait) { // 重写satisfies方法：判断当前特征是否满足给定的特征
      return equals(trait); // 只有当两个特征完全相等时才认为满足
    }

    @Override public void register(RelOptPlanner planner) { // 重写register方法：向规划器注册该特征
      // No-op // 空操作，不需要特殊的注册逻辑
    }

    @Override public String toString() { // 重写toString方法：返回特征的字符串表示
      return label; // 返回特征标签
    }

    @Override public boolean equals(Object o) { // 重写equals方法：判断两个特征是否相等
      return (o instanceof CustomTrait) && label.equals(((CustomTrait) o).label); // 只有当对象类型相同且标签相等时才返回true
    }

    @Override public int hashCode() { // 重写hashCode方法：返回特征的哈希码
      return label.hashCode(); // 返回标签的哈希码
    }
  }

  /**
   * Custom trait definition. // 自定义特征定义类，用于定义如何转换自定义特征
   */
  private static class CustomTraitDef extends RelTraitDef<CustomTrait> { // 自定义特征定义类，继承自RelTraitDef基类，泛型参数为CustomTrait

    private static final CustomTraitDef INSTANCE = new CustomTraitDef(); // 定义特征定义的单例实例

    @Override public Class<CustomTrait> getTraitClass() { // 重写getTraitClass方法：获取该特征定义管理的特征类
      return CustomTrait.class; // 返回CustomTrait类对象
    }

    @Override public String getSimpleName() { // 重写getSimpleName方法：获取特征定义的简单名称
      return "custom"; // 返回"custom"作为特征名称
    }

    @Override public @Nullable RelNode convert( // 重写convert方法：将关系节点从一个特征状态转换为另一个特征状态
        RelOptPlanner planner, // 规划器参数：执行转换的规划器实例
        RelNode rel, // 关系节点参数：需要转换的关系节点
        CustomTrait toTrait, // 目标特征参数：要转换到的目标特征
        boolean allowInfiniteCostConverters) { // 是否允许无限代价转换器的标志
      return new CustomTraitEnforcer( // 返回一个新的自定义特征强制器节点
          rel.getCluster(), // 使用原节点的集群
          rel.getTraitSet().replace(toTrait), // 使用替换了目标特征的特征集合
          rel); // 使用原节点作为输入
    }

    @Override public boolean canConvert(RelOptPlanner planner, CustomTrait fromTrait, // 重写canConvert方法：判断是否可以从一个特征转换到另一个特征
        CustomTrait toTrait) { // 目标特征参数
      return true; // 总是返回true，表示任何特征之间都可以转换
    }

    @Override public CustomTrait getDefault() { // 重写getDefault方法：获取该特征的默认值
      return CustomTrait.FROM; // 返回FROM状态作为默认特征
    }
  }
}
