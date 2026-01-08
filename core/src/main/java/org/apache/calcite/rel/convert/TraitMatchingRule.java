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
package org.apache.calcite.rel.convert; // 包声明：定义类所在的包路径，org.apache.calcite.rel.convert包包含关系代数转换相关的类

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系表达式的调用约定（如逻辑、物理等）
import org.apache.calcite.plan.RelOptRuleCall; // 导入RelOptRuleCall类，表示规则调用的上下文对象
import org.apache.calcite.plan.RelOptRuleOperand; // 导入RelOptRuleOperand类，表示规则的操作数定义
import org.apache.calcite.plan.RelOptRuleOperandChildPolicy; // 导入RelOptRuleOperandChildPolicy枚举，定义规则操作数的子节点策略
import org.apache.calcite.plan.RelRule; // 导入RelRule基类，所有优化规则的基类
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式树的节点
import org.apache.calcite.rel.core.RelFactories; // 导入RelFactories类，提供创建关系表达式的工厂方法
import org.apache.calcite.tools.RelBuilderFactory; // 导入RelBuilderFactory接口，用于创建关系表达式构建器的工厂

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可空的返回值或参数
import org.immutables.value.Value; // 导入Value注解，用于生成不可变对象（Immutables库）

/**
 * TraitMatchingRule是一个包装器规则，它适配一个转换规则（ConverterRule），限制该转换规则只有在其输入已经匹配预期的输出特征（trait）时才能触发
 * 这个类的主要作用是：在存在多种实现选择的情况下，最小化不必要的转换操作
 * 使用场景：与HepPlanner（启发式规划器）配合使用，当有备选实现可用，且希望最小化转换器数量时
 * 
 * 核心功能解析：
 * 1. 包装现有的ConverterRule，为其添加输入特征匹配的检查逻辑
 * 2. 只有当输入关系节点已经包含目标输出特征时，才允许底层的转换规则执行
 * 3. 这样可以避免不必要的转换，提高查询优化效率
 * 
 * 工作原理：
 * - 当规则匹配时，检查输入RelNode的TraitSet是否已经包含转换规则的目标输出特征
 * - 如果已经包含，则调用底层转换规则的onMatch方法执行转换
 * - 如果不包含，则跳过该规则，避免执行不必要的转换
 * 
 * 应用示例：
 * - 假设有一个从逻辑计划转换为物理计划的规则，但如果输入已经是物理计划格式，就不需要再转换
 * - TraitMatchingRule可以确保这种情况下跳过转换，直接使用输入
 */
@Value.Enclosing // Value.Enclosing注解：标记这个类包含嵌套的不可变配置接口，由Immutables库处理
public class TraitMatchingRule extends RelRule<TraitMatchingRule.Config> { // 类定义：TraitMatchingRule继承自RelRule，泛型参数为Config配置接口
  /**
   * 创建TraitMatchingRule的配置对象
   * 这是一个静态工厂方法，用于构建规则的配置
   * 
   * 方法功能：
   * 1. 从传入的converterRule获取其操作数定义
   * 2. 验证converterRule的子节点策略必须是ANY（任意输入）
   * 3. 构建并返回ImmutableTraitMatchingRule.Config实例
   * 
   * 参数说明：
   * @param converterRule     要被限制的转换规则；该规则必须接受单个操作数，且期望单个输入
   *                          这是一个ConverterRule实例，通常包含从一种trait到另一种trait的转换逻辑
   * @param relBuilderFactory 用于构建关系表达式的工厂对象
   *                          用于创建关系代数表达式节点
   * 
   * 返回值：
   * @return 构建好的TraitMatchingRule.Config配置对象
   * 
   * 实现细节：
   * - 获取converterRule的操作数定义
   * - 断言子节点策略为ANY，确保规则可以接受任意输入
   * - 使用builder模式构建配置，设置：
   *   1. RelBuilderFactory：关系表达式构建工厂
   *   2. Description：规则描述信息，包含converterRule的字符串表示
   *   3. OperandSupplier：操作数供应器，定义规则匹配的关系节点模式
   *   4. ConverterRule：要包装的底层转换规则
   */
  public static Config config(ConverterRule converterRule, // 静态方法：创建配置对象，参数converterRule是要被包装的转换规则
      RelBuilderFactory relBuilderFactory) { // 参数relBuilderFactory是关系表达式构建工厂
    final RelOptRuleOperand operand = converterRule.getOperand(); // 获取converterRule的操作数定义，用于后续构建匹配模式
    assert operand.childPolicy == RelOptRuleOperandChildPolicy.ANY; // 断言检查：确保子节点策略是ANY，即可以接受任意输入
    return ImmutableTraitMatchingRule.Config.builder().withRelBuilderFactory(relBuilderFactory) // 使用builder模式构建配置，设置关系表达式构建工厂
        .withDescription("TraitMatchingRule: " + converterRule) // 设置规则描述，便于调试和日志记录
        .withOperandSupplier(b0 -> // 设置操作数供应器，定义规则匹配的关系节点结构
            b0.operand(operand.getMatchedClass()).oneInput(b1 -> // b0表示根操作数，匹配converterRule的匹配类，要求有一个输入
                b1.operand(RelNode.class).anyInputs())) // b1表示输入操作数，匹配任意RelNode，可以接受任意数量的子节点
        .withConverterRule(converterRule) // 设置要包装的底层转换规则
        .build(); // 构建并返回配置对象
  }

  //~ Constructors -----------------------------------------------------------

  /** Creates a TraitMatchingRule. */ // 注释：创建TraitMatchingRule实例的构造方法
  protected TraitMatchingRule(Config config) { // 受保护的构造方法：接受Config配置对象作为参数
    super(config); // 调用父类RelRule的构造方法，传入配置对象
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public TraitMatchingRule(ConverterRule converterRule) { // 已废弃的构造方法：只接受converterRule参数
    this(config(converterRule, RelFactories.LOGICAL_BUILDER)); // 调用config方法创建配置，使用默认的LOGICAL_BUILDER
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public TraitMatchingRule(ConverterRule converterRule, // 已废弃的构造方法：接受converterRule和relBuilderFactory参数
      RelBuilderFactory relBuilderFactory) { // 参数relBuilderFactory用于自定义关系表达式构建工厂
    this(config(converterRule, relBuilderFactory)); // 调用config方法创建配置，然后调用主构造方法
  }

  //~ Methods ----------------------------------------------------------------

  @Override public @Nullable Convention getOutConvention() { // 重写方法：获取规则输出的调用约定（Convention）
    return config.converterRule().getOutConvention(); // 返回底层converterRule的输出Convention，可能为null
  }

  @Override public void onMatch(RelOptRuleCall call) { // 重写方法：当规则匹配时被调用，执行规则的核心逻辑
    RelNode input = call.rel(1); // 从规则调用上下文中获取索引为1的关系节点作为输入（索引0是根节点）
    final ConverterRule converterRule = config.converterRule(); // 从配置中获取底层转换规则
    if (input.getTraitSet().contains(converterRule.getOutTrait())) { // 检查输入节点的特征集合是否已经包含转换规则的目标输出特征
      converterRule.onMatch(call); // 如果输入已经匹配目标特征，则调用底层转换规则的onMatch方法执行转换
    } // 如果输入不匹配目标特征，则不执行任何操作，跳过该规则
  }

  /** Rule configuration. */ // 注释：规则配置接口定义
  @Value.Immutable(singleton = false) // Value.Immutable注解：标记为不可变配置接口，singleton=false表示不是单例
  public interface Config extends RelRule.Config { // 接口定义：Config继承自RelRule.Config，定义TraitMatchingRule的配置
    @Override default TraitMatchingRule toRule() { // 默认方法：将配置转换为TraitMatchingRule规则实例
      return new TraitMatchingRule(this); // 创建并返回新的TraitMatchingRule实例，传入当前配置对象
    }

    /** Returns the rule to be restricted; rule must take a single
     * operand expecting a single input. */ // 注释：返回要被限制的规则；该规则必须接受单个操作数且期望单个输入
    ConverterRule converterRule(); // 抽象方法：返回要包装的底层转换规则

    /** Sets {@link #converterRule()}. */ // 注释：设置converterRule属性
    Config withConverterRule(ConverterRule converterRule); // 抽象方法：设置转换规则，返回新的配置对象（builder模式）
  }
}
