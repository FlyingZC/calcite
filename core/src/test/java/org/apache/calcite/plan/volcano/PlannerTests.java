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
package org.apache.calcite.plan.volcano; // Volcano优化器测试包，包含测试Volcano规划器所需的工具类和辅助方法

import org.apache.calcite.plan.Convention; // 导入Convention类，表示关系表达式的调用约定（如逻辑层或物理层）
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，包含规划器的共享状态
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系表达式的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，表示优化规划器
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示规则调用的上下文
import org.apache.calcite.plan.RelRule; // 导入RelRule类，表示优化规则的基类
import org.apache.calcite.plan.RelTrait; // 导入RelTrait接口，表示关系表达式的特征
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式特征的集合
import org.apache.calcite.rel.AbstractRelNode; // 导入AbstractRelNode类，表示抽象关系节点
import org.apache.calcite.rel.BiRel; // 导入BiRel类，表示有两个输入的关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于写入关系表达式的解释信息
import org.apache.calcite.rel.SingleRel; // 导入SingleRel类，表示有一个输入的关系表达式
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询元数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建数据类型
import org.apache.calcite.rex.RexBuilder; // 导入RexBuilder类，用于构建行表达式
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SqlTypeFactoryImpl类，SQL类型工厂的实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可为空的返回值
import org.immutables.value.Value; // 导入Value注解，用于生成不可变值对象

import java.util.List; // 导入List接口，Java集合框架的列表接口

/**
 * Volcano规划器测试的公共类和工具方法
 * 
 * 这个类提供了测试Volcano优化器所需的辅助类、工具方法和测试用的关系表达式实现
 * Volcano是Calcite中基于成本的优化器，使用动态规划算法来寻找最优的执行计划
 * 
 * 主要内容包括：
 * 1. 测试用的调用约定（Convention）定义
 * 2. 测试用的关系表达式类（TestLeafRel、TestSingleRel等）
 * 3. 测试用的转换规则（PhysLeafRule、GoodSingleRule等）
 * 4. 工具方法（newCluster等）
 */
class PlannerTests {

  private PlannerTests() {} // 私有构造函数，防止实例化，这是一个工具类

  /**
   * 私有调用约定，表示物理实现
   * 
   * 这是一个自定义的Convention，用于在测试中区分逻辑层和物理层的关系表达式
   * Convention是RelTrait（关系特征）的一种，用于标记关系表达式所处的抽象层次
   * 
   * 主要特点：
   * 1. 名称：PHYS
   * 2. 适用类型：所有RelNode
   * 3. 可以转换为任何其他Convention（canConvertConvention返回true）
   * 4. 使用抽象转换器进行转换（useAbstractConvertersForConversion返回true）
   * 
   * 在Volcano优化器中，关系表达式通常从逻辑层（Convention.NONE）开始，
   * 通过规则转换为物理层（PHYS_CALLING_CONVENTION）
   */
  static final Convention PHYS_CALLING_CONVENTION =
      new Convention.Impl("PHYS", RelNode.class) { // 创建Convention实现，名称为"PHYS"，适用于所有RelNode
        @Override public boolean canConvertConvention(Convention toConvention) { // 重写方法：判断是否可以转换到目标Convention
          return true; // 返回true，表示可以转换为任何其他Convention
        }

        @Override public boolean useAbstractConvertersForConversion( // 重写方法：判断转换时是否使用抽象转换器
            RelTraitSet fromTraits, RelTraitSet toTraits) { // 参数：源特征集和目标特征集
          return true; // 返回true，表示在转换时使用抽象转换器而不是直接转换
        }

        @Override public RelNode enforce(final RelNode input, // 重写方法：强制将关系表达式转换为所需的特征集
            final RelTraitSet required) { // 参数：输入关系表达式和所需的特征集
          return null; // 返回null，表示不强制执行转换
        }
      };

  static final Convention PHYS_CALLING_CONVENTION_2 = // 第二个物理调用约定，用于测试不同的物理实现
      new Convention.Impl("PHYS_2", RelNode.class) { // 创建名称为"PHYS_2"的Convention
      }; // 没有重写任何方法，使用默认行为

  static final Convention PHYS_CALLING_CONVENTION_3 = // 第三个物理调用约定，用于测试特征满足逻辑
      new Convention.Impl("PHYS_3", RelNode.class) { // 创建名称为"PHYS_3"的Convention
        @Override public boolean satisfies(RelTrait trait) { // 重写satisfies方法，判断当前Convention是否满足指定的特征
          if (trait.equals(PHYS_CALLING_CONVENTION)) { // 如果传入的特征是PHYS_CALLING_CONVENTION
            return true; // 返回true，表示PHYS_3满足PHYS_CALLING_CONVENTION的特征
          }
          return super.satisfies(trait); // 否则调用父类的satisfies方法进行判断
        }
      }; // 这个Convention的特殊之处在于它满足PHYS_CALLING_CONVENTION的特征，这在测试特征层次时很有用

  static RelOptCluster newCluster(VolcanoPlanner planner) { // 工具方法：创建一个新的RelOptCluster（关系表达式集群）
    final RelDataTypeFactory typeFactory = // 创建SQL类型工厂，用于构建SQL数据类型
        new SqlTypeFactoryImpl(org.apache.calcite.rel.type.RelDataTypeSystem.DEFAULT); // 使用默认的类型系统
    return RelOptCluster.create(planner, new RexBuilder(typeFactory)); // 创建并返回RelOptCluster，传入planner和RexBuilder
  }
  // RelOptCluster是关系表达式集群，包含规划器、类型工厂、RexBuilder等共享状态
  // 所有在同一个查询规划中的关系表达式都会共享同一个RelOptCluster

  /** Leaf relational expression. */ // 叶子关系表达式（没有输入的关系表达式）
  abstract static class TestLeafRel extends AbstractRelNode { // 抽象类，继承自AbstractRelNode，表示测试用的叶子关系表达式
    final String label; // 成员变量：标签，用于标识和区分不同的叶子节点，便于调试和测试

    TestLeafRel(RelOptCluster cluster, RelTraitSet traits, String label) { // 构造方法：创建TestLeafRel实例
      super(cluster, traits); // 调用父类AbstractRelNode的构造方法，传入集群和特征集
      this.label = label; // 保存标签
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本的方法，用于优化器选择最优计划
        RelMetadataQuery mq) { // 参数：优化器和元数据查询
      return planner.getCostFactory().makeInfiniteCost(); // 返回无限成本，表示这个节点不应该被选择（除非有其他规则转换它）
    }

    @Override protected RelDataType deriveRowType() { // 推导行类型的方法，确定这个关系表达式输出的数据类型
      final RelDataTypeFactory typeFactory = getCluster().getTypeFactory(); // 获取类型工厂
      return typeFactory.builder() // 创建类型构建器
          .add("this", typeFactory.createJavaType(Void.TYPE)) // 添加一个名为"this"的列，类型为Void
          .build(); // 构建并返回行类型
    }

    @Override public RelWriter explainTerms(RelWriter pw) { // 解释术语的方法，用于生成执行计划的解释信息
      return super.explainTerms(pw).item("label", label); // 调用父类方法并添加label项
    }
  }
  // TestLeafRel是所有测试用叶子关系表达式的基类
  // 它提供了通用的功能：标签标识、无限成本、Void类型的行类型
  // 具体的叶子节点（如NoneLeafRel、PhysLeafRel）会继承这个类并添加特定功能

  /** Relational expression with one input. */ // 单输入关系表达式（有一个输入的关系表达式）
  abstract static class TestSingleRel extends SingleRel { // 抽象类，继承自SingleRel，表示测试用的单输入关系表达式
    TestSingleRel(RelOptCluster cluster, RelTraitSet traits, RelNode input) { // 构造方法：创建TestSingleRel实例
      super(cluster, traits, input); // 调用父类SingleRel的构造方法，传入集群、特征集和输入节点
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本的方法
        RelMetadataQuery mq) { // 参数：优化器和元数据查询
      return planner.getCostFactory().makeInfiniteCost(); // 返回无限成本，表示这个节点不应该被选择
    }

    @Override protected RelDataType deriveRowType() { // 推导行类型的方法
      return getInput().getRowType(); // 直接返回输入节点的行类型（单输入节点的输出类型通常与输入相同）
    }
  }
  // TestSingleRel是所有测试用单输入关系表达式的基类
  // 它提供了通用的功能：继承自SingleRel、无限成本、行类型继承自输入
  // 具体的单输入节点（如NoneSingleRel、PhysSingleRel）会继承这个类并添加特定功能

  /** Relational expression with one input and convention NONE. */ // 单输入关系表达式，使用NONE调用约定（逻辑层）
  static class NoneSingleRel extends TestSingleRel { // 静态内部类，继承自TestSingleRel，表示逻辑层的单输入节点
    NoneSingleRel(RelOptCluster cluster, RelNode input) { // 构造方法：创建NoneSingleRel实例
      super(cluster, cluster.traitSetOf(Convention.NONE), input); // 调用父类构造方法，特征集设置为Convention.NONE（逻辑层）
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制方法：创建这个节点的副本
      assert traitSet.contains(Convention.NONE); // 断言：新的特征集必须包含Convention.NONE
      return new NoneSingleRel(getCluster(), sole(inputs)); // 创建并返回新的NoneSingleRel实例，sole(inputs)获取唯一的输入
    }
  }
  // NoneSingleRel是逻辑层的单输入关系表达式
  // Convention.NONE表示这是逻辑层的节点，还没有转换为物理实现
  // 在优化过程中，这类节点会被规则转换为物理层的节点（如PhysSingleRel）

  /** Relational expression with two inputs and convention PHYS. */ // 双输入关系表达式，使用PHYS调用约定（物理层）
  static class PhysBiRel extends BiRel { // 静态内部类，继承自BiRel，表示物理层的双输入节点（如Join）
    PhysBiRel(RelOptCluster cluster, RelTraitSet traitSet, RelNode left, // 构造方法：创建PhysBiRel实例
        RelNode right) { // 参数：集群、特征集、左输入、右输入
      super(cluster, traitSet, left, right); // 调用父类BiRel的构造方法
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本的方法
        RelMetadataQuery mq) { // 参数：优化器和元数据查询
      return planner.getCostFactory().makeTinyCost(); // 返回微小成本，表示这个节点成本较低，容易被优化器选择
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制方法：创建这个节点的副本
      assert inputs.size() == 2; // 断言：输入列表必须包含2个节点
      return new PhysBiRel(getCluster(), traitSet, inputs.get(0), // 创建并返回新的PhysBiRel实例
          inputs.get(1)); // 传入左输入和右输入
    }

    @Override protected RelDataType deriveRowType() { // 推导行类型的方法
      return getLeft().getRowType(); // 返回左输入的行类型（通常双输入节点的输出类型与左输入相同）
    }
  }
  // PhysBiRel是物理层的双输入关系表达式
  // 典型用途：表示物理层的Join操作
  // 成本设置为微小值，使其在优化过程中容易被选择
  // BiRel是Calcite中用于表示有两个输入的关系表达式的基类

  /** Relational expression with zero inputs and convention NONE. */ // 零输入关系表达式（叶子节点），使用NONE调用约定（逻辑层）
  static class NoneLeafRel extends TestLeafRel { // 静态内部类，继承自TestLeafRel，表示逻辑层的叶子节点
    NoneLeafRel(RelOptCluster cluster, String label) { // 构造方法：创建NoneLeafRel实例
      super(cluster, cluster.traitSetOf(Convention.NONE), label); // 调用父类构造方法，特征集设置为Convention.NONE
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制方法：创建这个节点的副本
      assert traitSet.comprises(Convention.NONE); // 断言：新的特征集必须完全由Convention.NONE组成
      assert inputs.isEmpty(); // 断言：输入列表必须为空（叶子节点没有输入）
      return this; // 返回this，因为叶子节点是不可变的，直接返回当前实例
    }
  }
  // NoneLeafRel是逻辑层的叶子关系表达式
  // 典型用途：表示逻辑层的表扫描（LogicalTableScan）
  // Convention.NONE表示这是逻辑层的节点
  // 在优化过程中，这类节点会被规则转换为物理层的叶子节点（如PhysLeafRel）

  /** Relational expression with zero inputs and convention PHYS. */ // 零输入关系表达式（叶子节点），使用PHYS调用约定（物理层）
  static class PhysLeafRel extends TestLeafRel { // 静态内部类，继承自TestLeafRel，表示物理层的叶子节点
    final Convention convention; // 成员变量：存储这个节点使用的具体Convention（可能是PHYS、PHYS_2、PHYS_3等）

    PhysLeafRel(RelOptCluster cluster, String label) { // 构造方法：创建PhysLeafRel实例，使用默认的PHYS_CALLING_CONVENTION
      this(cluster, PHYS_CALLING_CONVENTION, label); // 调用另一个构造方法，传入PHYS_CALLING_CONVENTION
    }

    PhysLeafRel(RelOptCluster cluster, Convention convention, String label) { // 构造方法：创建PhysLeafRel实例，指定Convention
      super(cluster, cluster.traitSetOf(convention), label); // 调用父类构造方法，特征集设置为指定的Convention
      this.convention = convention; // 保存Convention引用
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本的方法
        RelMetadataQuery mq) { // 参数：优化器和元数据查询
      return planner.getCostFactory().makeTinyCost(); // 返回微小成本，表示这个节点成本较低，容易被优化器选择
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制方法：创建这个节点的副本
      assert traitSet.comprises(convention); // 断言：新的特征集必须完全由这个节点的Convention组成
      assert inputs.isEmpty(); // 断言：输入列表必须为空（叶子节点没有输入）
      return this; // 返回this，因为叶子节点是不可变的，直接返回当前实例
    }
  }
  // PhysLeafRel是物理层的叶子关系表达式
  // 典型用途：表示物理层的表扫描（PhysicalTableScan）
  // 支持多种Convention（PHYS、PHYS_2、PHYS_3等），便于测试不同的物理实现
  // 成本设置为微小值，使其在优化过程中容易被选择

  /** Relational expression with one input and convention PHYS. */ // 单输入关系表达式，使用PHYS调用约定（物理层）
  static class PhysSingleRel extends TestSingleRel { // 静态内部类，继承自TestSingleRel，表示物理层的单输入节点
    PhysSingleRel(RelOptCluster cluster, RelNode input) { // 构造方法：创建PhysSingleRel实例
      super(cluster, cluster.traitSetOf(PHYS_CALLING_CONVENTION), input); // 调用父类构造方法，特征集设置为PHYS_CALLING_CONVENTION
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 计算自身成本的方法
        RelMetadataQuery mq) { // 参数：优化器和元数据查询
      return planner.getCostFactory().makeTinyCost(); // 返回微小成本，表示这个节点成本较低，容易被优化器选择
    }

    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 复制方法：创建这个节点的副本
      assert traitSet.contains(PHYS_CALLING_CONVENTION); // 断言：新的特征集必须包含PHYS_CALLING_CONVENTION
      return new PhysSingleRel(getCluster(), sole(inputs)); // 创建并返回新的PhysSingleRel实例，sole(inputs)获取唯一的输入
    }
  }
  // PhysSingleRel是物理层的单输入关系表达式
  // 典型用途：表示物理层的单目操作（如Filter、Project、Sort等）
  // 成本设置为微小值，使其在优化过程中容易被选择
  // 通常由规则从逻辑层的NoneSingleRel转换而来

  /** Planner rule that converts {@link NoneLeafRel} to PHYS convention. */ // 优化器规则：将NoneLeafRel转换为PHYS调用约定
  public static class PhysLeafRule extends RelRule<PhysLeafRule.Config> { // 静态内部类，继承自RelRule，表示转换规则
    static final PhysLeafRule INSTANCE = // 静态常量：规则的唯一实例（单例模式）
        ImmutableTraitPhysLeafRuleConfig.builder() // 创建配置构建器
            .withOperandSupplier(b -> b.operand(NoneLeafRel.class).anyInputs()) // 设置操作数提供器：匹配NoneLeafRel类型的节点，任意输入
            .build() // 构建配置
            .toRule(); // 将配置转换为规则实例

    protected PhysLeafRule(Config config) { // 构造方法：创建PhysLeafRule实例
      super(config); // 调用父类RelRule的构造方法，传入配置
    }

    @Override public Convention getOutConvention() { // 获取输出Convention的方法
      return PHYS_CALLING_CONVENTION; // 返回PHYS_CALLING_CONVENTION，表示这个规则会生成物理层的节点
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时调用的方法
      NoneLeafRel leafRel = call.rel(0); // 从规则调用中获取匹配的NoneLeafRel节点（第0个关系表达式）
      call.transformTo( // 转换关系表达式
          new PhysLeafRel(leafRel.getCluster(), leafRel.label)); // 创建新的PhysLeafRel节点，使用相同的集群和标签
    }
    // onMatch方法是规则的核心逻辑
    // 当规则匹配到一个NoneLeafRel节点时，会调用这个方法
    // 方法将NoneLeafRel转换为PhysLeafRel，实现从逻辑层到物理层的转换

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // Immutables注解：生成不可变的配置实现类
    @Value.Style(init = "with*", typeImmutable = "ImmutableTraitPhysLeafRuleConfig") // Immutables样式配置：初始化方法以"with"开头，生成的不可变类名为ImmutableTraitPhysLeafRuleConfig
    public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
      @Override default PhysLeafRule toRule() { // 默认方法：将配置转换为规则实例
        return new PhysLeafRule(this); // 创建并返回PhysLeafRule实例，传入this（当前配置）
      }
    }
  }
  // PhysLeafRule是一个转换规则，用于将逻辑层的叶子节点转换为物理层的叶子节点
  // 规则模式：
  // 1. 匹配：NoneLeafRel类型的节点
  // 2. 转换：创建PhysLeafRel节点
  // 3. Convention变化：从NONE（逻辑层）转换为PHYS（物理层）
  // 这个规则在Volcano优化器中会被自动应用，以探索不同的物理实现

  /** Planner rule that converts {@link NoneLeafRel} to PHYS convention with different type. */ // 优化器规则：将NoneLeafRel转换为PHYS调用约定，但使用不同的类型（用于测试类型不匹配场景）
  public static class MockPhysLeafRule extends RelRule<MockPhysLeafRule.Config> { // 静态内部类，继承自RelRule
    static final MockPhysLeafRule INSTANCE = // 静态常量：规则的唯一实例
        ImmutableMockPhysLeafRuleConfig.builder() // 创建配置构建器
            .withOperandSupplier(b -> b.operand(NoneLeafRel.class).anyInputs()) // 设置操作数提供器：匹配NoneLeafRel类型的节点
            .build() // 构建配置
            .toRule(); // 将配置转换为规则实例

    /** Relational expression with zero inputs and convention PHYS. */ // 零输入关系表达式，使用PHYS调用约定
    public static class MockPhysLeafRel extends PhysLeafRel { // 静态内部类，继承自PhysLeafRel，表示一个特殊的物理叶子节点
      MockPhysLeafRel(RelOptCluster cluster, String label) { // 构造方法：创建MockPhysLeafRel实例
        super(cluster, PHYS_CALLING_CONVENTION, label); // 调用父类构造方法，使用PHYS_CALLING_CONVENTION
      }

      @Override protected RelDataType deriveRowType() { // 推导行类型的方法，重写父类实现
        final RelDataTypeFactory typeFactory = getCluster().getTypeFactory(); // 获取类型工厂
        return typeFactory.builder() // 创建类型构建器
            .add("this", typeFactory.createJavaType(Integer.class)) // 添加一个名为"this"的列，类型为Integer（与父类的Void类型不同）
            .build(); // 构建并返回行类型
      }
    }

    protected MockPhysLeafRule(Config config) { // 构造方法：创建MockPhysLeafRule实例
      super(config); // 调用父类RelRule的构造方法，传入配置
    }

    @Override public Convention getOutConvention() { // 获取输出Convention的方法
      return PHYS_CALLING_CONVENTION; // 返回PHYS_CALLING_CONVENTION
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时调用的方法
      NoneLeafRel leafRel = call.rel(0); // 从规则调用中获取匹配的NoneLeafRel节点

      // It would throw exception. // 注释说明：这个转换会抛出异常（因为类型不匹配）
      call.transformTo( // 转换关系表达式
          new MockPhysLeafRel(leafRel.getCluster(), leafRel.label)); // 创建MockPhysLeafRel节点（类型与原节点不同）
    }
    // 这个规则用于测试类型不匹配的场景
    // MockPhysLeafRel的行类型是Integer，而NoneLeafRel的行类型是Void
    // 这种类型不匹配会导致优化过程中出现异常，用于测试优化器的错误处理能力

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // Immutables注解：生成不可变的配置实现类
    @Value.Style(init = "with*", typeImmutable = "ImmutableMockPhysLeafRuleConfig") // Immutables样式配置
    public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
      @Override default MockPhysLeafRule toRule() { // 默认方法：将配置转换为规则实例
        return new MockPhysLeafRule(this); // 创建并返回MockPhysLeafRule实例
      }
    }
  }
  // MockPhysLeafRule是一个测试规则，用于验证优化器对类型不匹配的处理
  // 这个规则会产生类型不匹配的转换，用于测试：
  // 1. 优化器的错误检测能力
  // 2. 类型系统的正确性
  // 3. 规则匹配和转换的边界情况

  /** Planner rule that matches a {@link NoneSingleRel} and succeeds. */ // 优化器规则：匹配NoneSingleRel并成功转换（一个正常的、可工作的规则）
  public static class GoodSingleRule // 静态内部类，继承自RelRule
      extends RelRule<GoodSingleRule.Config> { // 泛型参数指定配置类型为GoodSingleRule.Config
    static final GoodSingleRule INSTANCE = // 静态常量：规则的唯一实例
        ImmutableGoodSingleRuleConfig.builder() // 创建配置构建器
            .withOperandSupplier(b -> // 设置操作数提供器
                b.operand(NoneSingleRel.class).anyInputs()) // 匹配NoneSingleRel类型的节点，任意输入
            .build() // 构建配置
            .toRule(); // 将配置转换为规则实例

    protected GoodSingleRule(Config config) { // 构造方法：创建GoodSingleRule实例
      super(config); // 调用父类RelRule的构造方法，传入配置
    }

    @Override public Convention getOutConvention() { // 获取输出Convention的方法
      return PHYS_CALLING_CONVENTION; // 返回PHYS_CALLING_CONVENTION，表示这个规则会生成物理层的节点
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时调用的方法
      NoneSingleRel single = call.rel(0); // 从规则调用中获取匹配的NoneSingleRel节点
      RelNode input = single.getInput(); // 获取NoneSingleRel的输入节点
      RelNode physInput = // 转换输入节点为物理层节点
          convert(input, // 调用convert方法转换输入节点
              single.getTraitSet().replace(PHYS_CALLING_CONVENTION)); // 将特征集替换为PHYS_CALLING_CONVENTION
      call.transformTo( // 转换关系表达式
          new PhysSingleRel(single.getCluster(), physInput)); // 创建PhysSingleRel节点，使用转换后的物理输入
    }
    // onMatch方法的逻辑：
    // 1. 获取匹配的NoneSingleRel节点
    // 2. 获取它的输入节点
    // 3. 将输入节点转换为物理层（PHYS_CALLING_CONVENTION）
    // 4. 创建PhysSingleRel节点，使用转换后的物理输入
    // 5. 提交转换结果
    // 这个规则展示了如何正确地处理带有输入的节点转换：先转换输入，再创建新节点

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // Immutables注解：生成不可变的配置实现类
    @Value.Style(init = "with*", typeImmutable = "ImmutableGoodSingleRuleConfig") // Immutables样式配置
    public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
      @Override default GoodSingleRule toRule() { // 默认方法：将配置转换为规则实例
        return new GoodSingleRule(this); // 创建并返回GoodSingleRule实例
      }
    }
  }
  // GoodSingleRule是一个正常的、可工作的转换规则
  // 规则模式：
  // 1. 匹配：NoneSingleRel类型的节点
  // 2. 转换：将输入节点转换为物理层，然后创建PhysSingleRel
  // 3. Convention变化：从NONE（逻辑层）转换为PHYS（物理层）
  // 这个规则展示了正确的转换模式：先转换子节点，再创建父节点
  // 这是Volcano优化器中规则的典型实现方式

  /**
   * Planner rule that matches a parent with two children and asserts that they
   * are not the same.
   */ // 优化器规则：匹配一个有两个子节点的父节点，并断言这两个子节点不相同（用于测试规则匹配的正确性）
  public static class AssertOperandsDifferentRule // 静态内部类，继承自RelRule
      extends RelRule<AssertOperandsDifferentRule.Config> { // 泛型参数指定配置类型
    public static final AssertOperandsDifferentRule INSTANCE = // 静态常量：规则的唯一实例
        ImmutableAssertOperandsDifferentRuleConfig.builder().build().withOperandSupplier(b0 -> // 创建配置构建器并设置操作数提供器
                b0.operand(PhysBiRel.class).inputs( // 匹配PhysBiRel类型的节点，并指定它的两个输入
                    b1 -> b1.operand(PhysLeafRel.class).anyInputs(), // 第一个输入：PhysLeafRel类型的节点
                    b2 -> b2.operand(PhysLeafRel.class).anyInputs())) // 第二个输入：PhysLeafRel类型的节点
            .toRule(); // 将配置转换为规则实例

    protected AssertOperandsDifferentRule(Config config) { // 构造方法：创建AssertOperandsDifferentRule实例
      super(config); // 调用父类RelRule的构造方法，传入配置
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时调用的方法
      PhysLeafRel left = call.rel(1); // 获取左子节点（第1个关系表达式，索引从0开始，所以这是第二个）
      PhysLeafRel right = call.rel(2); // 获取右子节点（第2个关系表达式，这是第三个）

      assert left != right : left + " should be different from " + right; // 断言：左右子节点必须不相同，否则抛出异常
    }
    // onMatch方法的逻辑：
    // 1. 获取匹配的PhysBiRel节点的两个PhysLeafRel子节点
    // 2. 断言这两个子节点不是同一个对象
    // 这个规则用于测试：
    // 1. 规则匹配器是否正确识别了不同的子节点
    // 2. 规则调用上下文中的rel()方法是否正确返回了对应的关系表达式
    // 3. 优化器是否正确地维护了节点之间的身份关系

    /** Rule configuration. */ // 规则配置接口
    @Value.Immutable // Immutables注解：生成不可变的配置实现类
    @Value.Style(init = "with*", typeImmutable = "ImmutableAssertOperandsDifferentRuleConfig") // Immutables样式配置
    public interface Config extends RelRule.Config { // 配置接口，继承自RelRule.Config
      @Override default AssertOperandsDifferentRule toRule() { // 默认方法：将配置转换为规则实例
        return new AssertOperandsDifferentRule(this); // 创建并返回AssertOperandsDifferentRule实例
      }
    }
  }
  // AssertOperandsDifferentRule是一个断言规则，用于验证优化器的正确性
  // 规则模式：
  // 1. 匹配：PhysBiRel类型的节点，且它的两个输入都是PhysLeafRel类型
  // 2. 动作：断言两个PhysLeafRel子节点不是同一个对象
  // 这个规则不会进行转换，只是进行断言检查
  // 在测试中，如果优化器错误地重用了同一个节点，这个断言会失败，帮助发现bug
}
// PlannerTests类的总结：
// 这个类提供了Volcano优化器测试所需的所有基础设施
// 主要组件：
// 1. Convention定义：PHYS_CALLING_CONVENTION、PHYS_CALLING_CONVENTION_2、PHYS_CALLING_CONVENTION_3
// 2. 关系表达式类：TestLeafRel、TestSingleRel、NoneLeafRel、PhysLeafRel、NoneSingleRel、PhysSingleRel、PhysBiRel
// 3. 转换规则：PhysLeafRule、MockPhysLeafRule、GoodSingleRule、AssertOperandsDifferentRule
// 4. 工具方法：newCluster
//
// 这些组件共同构成了一个完整的测试框架，用于：
// - 测试Volcano优化器的规则匹配和转换机制
// - 验证Convention系统的正确性
// - 测试成本计算和优化选择
// - 验证类型系统的正确性
// - 测试规则组合和复杂转换场景
