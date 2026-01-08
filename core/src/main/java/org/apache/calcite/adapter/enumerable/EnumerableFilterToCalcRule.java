/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，说明此代码遵循Apache 2.0许可证
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，详见随分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权此文件给您
 * (the "License"); you may not use this file except in compliance with // ("许可证")；除非符合许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache许可证2.0的URL地址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证分发的软件按"原样"基础分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解特定语言的权限和
 * limitations under the License.  // 许可证下的限制
 */
package org.apache.calcite.adapter.enumerable;  // 声明此类属于org.apache.calcite.adapter.enumerable包，这是Calcite中可枚举适配器包

import org.apache.calcite.plan.RelOptRuleCall;  // 导入RelOptRuleCall类，用于表示优化规则调用的上下文
import org.apache.calcite.plan.RelRule;  // 导入RelRule基类，这是所有关系代数规则的基类
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，表示关系代数表达式树的节点
import org.apache.calcite.rel.type.RelDataType;  // 导入RelDataType类，表示关系数据类型（行类型）
import org.apache.calcite.rex.RexBuilder;  // 导入RexBuilder类，用于构建行表达式（RexNode）
import org.apache.calcite.rex.RexProgram;  // 导入RexProgram类，表示行表达式程序，包含一组表达式
import org.apache.calcite.rex.RexProgramBuilder;  // 导入RexProgramBuilder类，用于构建RexProgram
import org.apache.calcite.tools.RelBuilderFactory;  // 导入RelBuilderFactory接口，用于创建关系节点构建器

import org.immutables.value.Value;  // 导入Immutables库的Value注解，用于生成不可变值对象

/** Variant of {@link org.apache.calcite.rel.rules.FilterToCalcRule} for  // 这是一个FilterToCalcRule规则的变体，专门用于
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}.  // 可枚举调用约定（EnumerableConvention）
 *  // 这个规则的作用是将EnumerableFilter节点转换为EnumerableCalc节点
 *  // Filter节点表示过滤操作，Calc节点表示计算操作（可以同时处理投影、过滤等）
 *  // 转换为Calc节点可以优化执行计划，减少节点数量
 *  // 可枚举调用约定意味着这个规则适用于可以生成可枚举代码的适配器
 *  // 可枚举（Enumerable）是Calcite中一种特殊的执行方式，可以生成Java代码来执行查询
 *  // 这个规则是Calcite优化器规则体系的一部分，在基于成本的优化器（CBO）中使用
 *
 * @see EnumerableRules#ENUMERABLE_FILTER_TO_CALC_RULE */  // 参见EnumerableRules类中的ENUMERABLE_FILTER_TO_CALC_RULE常量
@Value.Enclosing  // Immutables注解，表示这个类包含嵌套的配置接口，需要生成不可变实现类
public class EnumerableFilterToCalcRule  // 定义类名：EnumerableFilterToCalcRule，表示将可枚举过滤器转换为可枚举计算器的规则
    extends RelRule<EnumerableFilterToCalcRule.Config> {  // 继承自RelRule基类，泛型参数是Config接口，表示规则的配置类型
  /** Creates an EnumerableFilterToCalcRule. */  // 构造方法注释：创建一个EnumerableFilterToCalcRule实例
  protected EnumerableFilterToCalcRule(Config config) {  // 受保护的构造方法，接收Config配置对象作为参数
    super(config);  // 调用父类RelRule的构造方法，传递配置对象，初始化规则的基本属性
  }  // 构造方法结束

  @Deprecated // to be removed before 2.0  // 标记为已过时的注解，将在2.0版本之前移除，表示不推荐使用此构造方法
  public EnumerableFilterToCalcRule(RelBuilderFactory relBuilderFactory) {  // 公共构造方法，接收RelBuilderFactory作为参数
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory)  // 调用主构造方法，使用默认配置并设置关系构建器工厂
        .as(Config.class));  // 将配置转换为Config类型，确保类型安全
  }  // 过时构造方法结束

  @Override public void onMatch(RelOptRuleCall call) {  // 重写父类的onMatch方法，当规则匹配时被调用，接收规则调用对象
    final EnumerableFilter filter = call.rel(0);  // 从规则调用中获取第0个关系节点（即Filter节点），声明为final确保不可修改
    final RelNode input = filter.getInput();  // 获取Filter节点的输入节点（即被过滤的数据源），声明为final确保不可修改

    // Create a program containing a filter.  // 注释：创建一个包含过滤条件的程序
    final RexBuilder rexBuilder = filter.getCluster().getRexBuilder();  // 获取Filter所在集群的RexBuilder，用于构建行表达式
    final RelDataType inputRowType = input.getRowType();  // 获取输入节点的行类型（即输入数据的字段结构），声明为final确保不可修改
    final RexProgramBuilder programBuilder =  // 创建RexProgramBuilder实例，用于构建RexProgram
        new RexProgramBuilder(inputRowType, rexBuilder);  // 传入输入行类型和RexBuilder，初始化程序构建器
    programBuilder.addIdentity();  // 添加恒等映射，即输出字段与输入字段一一对应，保持原始数据不变
    programBuilder.addCondition(filter.getCondition());  // 将Filter的过滤条件添加到程序中，作为过滤条件
    final RexProgram program = programBuilder.getProgram();  // 从构建器中获取构建完成的RexProgram，声明为final确保不可修改

    final EnumerableCalc calc = EnumerableCalc.create(input, program);  // 创建EnumerableCalc节点，传入输入节点和程序，将Filter转换为Calc
    call.transformTo(calc);  // 调用规则调用的transformTo方法，将原始节点转换为新的Calc节点，完成规则转换
  }  // onMatch方法结束

  /** Rule configuration. */  // 注释：规则配置接口
  @Value.Immutable  // Immutables注解，表示这是一个不可变值对象，Immutables库会自动生成实现类
  public interface Config extends RelRule.Config {  // 定义Config接口，继承自RelRule.Config，表示规则的配置
    Config DEFAULT = ImmutableEnumerableFilterToCalcRule.Config.of()  // 定义默认配置常量，使用Immutables生成的不可变配置
        .withOperandSupplier(b ->  // 设置操作数提供器，用于定义规则匹配的操作数模式
            b.operand(EnumerableFilter.class).anyInputs());  // 匹配任意输入的EnumerableFilter节点，即规则适用于所有EnumerableFilter

    @Override default EnumerableFilterToCalcRule toRule() {  // 重写toRule方法，将配置转换为规则实例
      return new EnumerableFilterToCalcRule(this);  // 创建并返回一个新的EnumerableFilterToCalcRule实例，传入当前配置
    }  // toRule方法结束
  }  // Config接口结束
}  // EnumerableFilterToCalcRule类结束
