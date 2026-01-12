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
package org.apache.calcite.plan.volcano; // 声明包名，该类属于org.apache.calcite.plan.volcano包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系表达式的调用约定(物理或逻辑)
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef类，用于定义调用约定特征
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示优化集群，包含共享的优化对象
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系表达式的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示优化规划器的接口
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示规则调用的上下文
import org.apache.calcite.plan.RelRule; // 导入RelRule类，表示转换规则的基类
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式树中的节点
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值
import org.immutables.value.Value; // 导入Immutables注解，用于生成不可变值对象
import org.junit.jupiter.api.Test; // 导入Jupiter测试注解，标记测试方法

import java.util.List; // 导入Java集合框架的List接口

import static org.apache.calcite.plan.volcano.PlannerTests.GoodSingleRule; // 导入测试工具类中的GoodSingleRule规则
import static org.apache.calcite.plan.volcano.PlannerTests.NoneLeafRel; // 导入测试工具类中的NoneLeafRel关系节点
import static org.apache.calcite.plan.volcano.PlannerTests.NoneSingleRel; // 导入测试工具类中的NoneSingleRel关系节点
import static org.apache.calcite.plan.volcano.PlannerTests.PHYS_CALLING_CONVENTION; // 导入测试工具类中的物理调用约定
import static org.apache.calcite.plan.volcano.PlannerTests.PhysLeafRel; // 导入测试工具类中的PhysLeafRel关系节点
import static org.apache.calcite.plan.volcano.PlannerTests.PhysSingleRel; // 导入测试工具类中的PhysSingleRel关系节点
import static org.apache.calcite.plan.volcano.PlannerTests.TestSingleRel; // 导入测试工具类中的TestSingleRel关系节点
import static org.apache.calcite.plan.volcano.PlannerTests.newCluster; // 导入测试工具类中的newCluster方法

import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest断言，用于检查对象是否为指定类型的实例
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类

/**
 * Unit test for {@link VolcanoPlanner}. // 类文档注释：这是VolcanoPlanner的单元测试类
 * 该测试类用于验证VolcanoPlanner中组合规则(ComboRule)的功能
 * 主要测试场景：验证多个规则如何协同工作，以及如何通过组合规则优化关系表达式树
 * 测试重点：组合规则如何将中间节点向上移动，以及成本如何影响优化器的选择
 */
class ComboRuleTest { // 定义测试类ComboRuleTest，用于测试VolcanoPlanner的组合规则功能

  @Test void testCombo() { // 测试方法：测试组合规则的完整转换流程
    VolcanoPlanner planner = new VolcanoPlanner(); // 创建VolcanoPlanner优化器实例，用于基于成本的优化
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 向优化器添加调用约定特征定义，用于处理物理和逻辑约定

    planner.addRule(ComboRule.INSTANCE); // 添加组合规则到优化器，该规则会重新排列节点顺序
    planner.addRule(AddIntermediateNodeRule.INSTANCE); // 添加中间节点规则到优化器，该规则会在叶子节点上添加中间节点
    planner.addRule(GoodSingleRule.INSTANCE); // 添加良好的单节点规则到优化器，用于将NoneSingleRel转换为PhysSingleRel

    RelOptCluster cluster = newCluster(planner); // 创建优化集群，包含共享的优化对象和表达式工厂
    NoneLeafRel leafRel = new NoneLeafRel(cluster, "a"); // 创建一个None约定(逻辑)的叶子关系节点，标签为"a"
    NoneSingleRel singleRel = new NoneSingleRel(cluster, leafRel); // 创建第一个None约定的单输入关系节点，输入为leafRel
    NoneSingleRel singleRel2 = new NoneSingleRel(cluster, singleRel); // 创建第二个None约定的单输入关系节点，输入为singleRel，形成三层结构
    RelNode convertedRel = // 声明转换后的关系节点变量
        planner.changeTraits(singleRel2, // 将singleRel2的特征从None约定转换为物理约定
            cluster.traitSetOf(PHYS_CALLING_CONVENTION)); // 获取物理调用约定的特征集合
    planner.setRoot(convertedRel); // 将转换后的关系节点设置为优化器的根节点，开始优化过程
    RelNode result = planner.chooseDelegate().findBestExp(); // 执行优化，找到成本最低的最优表达式
    assertThat(result, instanceOf(IntermediateNode.class)); // 断言优化结果是IntermediateNode类型，验证组合规则成功应用
  } // 测试方法结束

  /** Intermediate node, the cost decreases as it is pushed up the tree // 中间节点类文档注释：这是一个特殊的中间节点
   * (more inputs it has, cheaper it gets). // 成本特性：随着节点在树中向上移动(即它下面有更多节点)，成本会降低
   * 这种成本模型鼓励优化器将中间节点尽可能向上移动，以获得更低的总体成本
   * nodesBelowCount字段记录了该节点下面有多少个节点，用于计算成本
   */
  private static class IntermediateNode extends TestSingleRel { // 定义IntermediateNode类，继承自TestSingleRel，表示一个中间节点
    final int nodesBelowCount; // 成员变量：记录该节点下面有多少个节点，用于成本计算，值越大成本越低

    IntermediateNode(RelOptCluster cluster, RelNode input, int nodesBelowCount) { // 构造方法：创建中间节点
      super(cluster, cluster.traitSetOf(PHYS_CALLING_CONVENTION), input); // 调用父类构造方法，设置集群、物理约定特征和输入节点
      this.nodesBelowCount = nodesBelowCount; // 初始化nodesBelowCount字段，记录该节点下面的节点数量
    } // 构造方法结束

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写成本计算方法：计算该节点自身的成本
        RelMetadataQuery mq) { // 参数：RelMetadataQuery用于查询元数据(虽然本方法未使用)
      return planner.getCostFactory().makeCost(100, 100, 100) // 创建基础成本：CPU=100, IO=100, ROWS=100
          .multiplyBy(1.0 / nodesBelowCount); // 将基础成本乘以(1/nodesBelowCount)，节点数越多成本越低
    } // 成本计算方法结束

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制方法：创建该节点的副本
      assert traitSet.comprises(PHYS_CALLING_CONVENTION); // 断言特征集合包含物理调用约定，确保特征正确
      return new IntermediateNode(getCluster(), sole(inputs), nodesBelowCount); // 返回新的IntermediateNode实例，保持相同的nodesBelowCount
    } // 复制方法结束
  } // IntermediateNode类结束

  /** Rule that adds an intermediate node above the {@link PhysLeafRel}. // AddIntermediateNodeRule类文档注释：这是一个转换规则
   * 该规则的作用是在PhysLeafRel(物理叶子节点)之上添加一个IntermediateNode(中间节点)
   * 这是优化过程的第一步，将逻辑叶子节点转换为物理叶子节点，并添加中间节点
   */
  public static class AddIntermediateNodeRule // 定义AddIntermediateNodeRule类，继承自RelRule
      extends RelRule<AddIntermediateNodeRule.Config> { // 使用泛型配置类型
    static final AddIntermediateNodeRule INSTANCE = ImmutableAddIntermediateNodeRuleConfig.builder() // 创建规则实例：使用构建器模式
        .build() // 构建配置对象
        .withOperandSupplier(b -> b.operand(NoneLeafRel.class).anyInputs()) // 设置操作数：匹配NoneLeafRel类型的节点，不限制输入
        .toRule(); // 将配置转换为规则实例

    AddIntermediateNodeRule(Config config) { // 构造方法：接收配置对象
      super(config); // 调用父类RelRule的构造方法，初始化规则
    } // 构造方法结束

    @Override public Convention getOutConvention() { // 重写方法：获取输出的调用约定
      return PHYS_CALLING_CONVENTION; // 返回物理调用约定，表示该规则产生物理节点
    } // 方法结束

    @Override public void onMatch(RelOptRuleCall call) { // 重写方法：当规则匹配成功时执行转换
      NoneLeafRel leaf = call.rel(0); // 获取匹配到的NoneLeafRel节点(索引0)

      RelNode physLeaf = new PhysLeafRel(leaf.getCluster(), leaf.label); // 创建物理叶子节点，使用原节点的集群和标签
      RelNode intermediateNode = new IntermediateNode(physLeaf.getCluster(), physLeaf, 1); // 创建中间节点，输入为物理叶子节点，初始nodesBelowCount为1

      call.transformTo(intermediateNode); // 执行转换，将原节点替换为中间节点
    } // onMatch方法结束

    /** Rule configuration. // Config接口文档注释：规则的配置接口
     * 使用Immutables注解自动生成不可变配置类
     */
    @Value.Immutable // 标记接口为不可变值类型，Immutables会自动生成实现
    @Value.Style(typeImmutable = "ImmutableAddIntermediateNodeRuleConfig") // 设置生成的实现类名称
    public interface Config extends RelRule.Config { // 定义Config接口，继承RelRule.Config
      @Override default AddIntermediateNodeRule toRule() { // 默认方法：将配置转换为规则实例
        return new AddIntermediateNodeRule(this); // 创建并返回AddIntermediateNodeRule实例
      } // toRule方法结束
    } // Config接口结束
  } // AddIntermediateNodeRule类结束

  /** Matches {@link PhysSingleRel}-{@link IntermediateNode}-Any // ComboRule类文档注释：这是一个组合转换规则
   * and converts to {@link IntermediateNode}-{@link PhysSingleRel}-Any. // 转换目标：将PhysSingleRel-IntermediateNode-Any模式转换为IntermediateNode-PhysSingleRel-Any
   * 该规则的作用是将中间节点向上移动，使其位于物理单节点之上
   * 这样可以利用成本模型的优势，因为nodesBelowCount增加会降低成本
   * 例如：PhysSingleRel(IntermediateNode(PhysLeafRel)) -> IntermediateNode(PhysSingleRel(PhysLeafRel))
   */
  public static class ComboRule extends RelRule<ComboRule.Config> { // 定义ComboRule类，继承自RelRule
    static final ComboRule INSTANCE = ImmutableComboRuleConfig.builder() // 创建规则实例：使用构建器模式
        .build() // 构建配置对象
        .withOperandSupplier(b0 -> // 设置操作数：定义匹配模式
            b0.operand(PhysSingleRel.class).oneInput(b1 -> // 匹配PhysSingleRel类型，有一个输入
                b1.operand(IntermediateNode.class).oneInput(b2 -> // 该输入是IntermediateNode类型，有一个输入
                    b2.operand(RelNode.class).anyInputs()))) // IntermediateNode的输入是任意RelNode类型
        .toRule(); // 将配置转换为规则实例

    ComboRule(Config config) { // 构造方法：接收配置对象
      super(config); // 调用父类RelRule的构造方法，初始化规则
    } // 构造方法结束

    @Override public Convention getOutConvention() { // 重写方法：获取输出的调用约定
      return PHYS_CALLING_CONVENTION; // 返回物理调用约定，表示该规则产生物理节点
    } // 方法结束

    @Override public boolean matches(RelOptRuleCall call) { // 重写方法：检查规则是否匹配
      if (call.rels.length < 3) { // 检查关系节点数组长度是否至少为3
        return false; // 如果不足3个节点，返回false，不匹配
      } // if结束

      if (call.rel(0) instanceof PhysSingleRel // 检查索引0的节点是否为PhysSingleRel类型
          && call.rel(1) instanceof IntermediateNode // 检查索引1的节点是否为IntermediateNode类型
          && call.rel(2) instanceof RelNode) { // 检查索引2的节点是否为RelNode类型
        return true; // 如果三个节点类型都匹配，返回true，规则匹配成功
      } // if结束
      return false; // 否则返回false，规则不匹配
    } // matches方法结束

    @Override public void onMatch(RelOptRuleCall call) { // 重写方法：当规则匹配成功时执行转换
      List<RelNode> newInputs = ImmutableList.of(call.rel(2)); // 创建新的输入列表，只包含索引2的节点(最底层的节点)
      IntermediateNode oldInter = call.rel(1); // 获取原来的中间节点(索引1)
      RelNode physRel = call.rel(0).copy(call.rel(0).getTraitSet(), newInputs); // 复制PhysSingleRel节点，使用新的输入列表(索引2的节点)
      RelNode converted = // 声明转换后的节点
          new IntermediateNode(physRel.getCluster(), physRel, // 创建新的中间节点，输入为复制后的PhysSingleRel节点
              oldInter.nodesBelowCount + 1); // nodesBelowCount加1，表示该节点下面多了一个节点，成本会降低
      call.transformTo(converted); // 执行转换，将原节点模式替换为新的节点模式
    } // onMatch方法结束

    /** Rule configuration. // Config接口文档注释：规则的配置接口
     * 使用Immutables注解自动生成不可变配置类
     */
    @Value.Immutable // 标记接口为不可变值类型，Immutables会自动生成实现
    @Value.Style(typeImmutable = "ImmutableComboRuleConfig") // 设置生成的实现类名称
    public interface Config extends RelRule.Config { // 定义Config接口，继承RelRule.Config
      @Override default ComboRule toRule() { // 默认方法：将配置转换为规则实例
        return new ComboRule(this); // 创建并返回ComboRule实例
      } // toRule方法结束
    } // Config接口结束
  } // ComboRule类结束
} // ComboRuleTest类结束
