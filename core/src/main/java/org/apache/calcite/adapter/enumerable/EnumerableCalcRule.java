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
// 声明包名，表示这个类属于org.apache.calcite.adapter.enumerable包，该包包含了可枚举适配器的相关实现
package org.apache.calcite.adapter.enumerable;

// 导入Convention类，用于定义关系代数表达式的约定（Convention），表示数据如何被访问和处理的规范
import org.apache.calcite.plan.Convention;
// 导入RelOptUtil类，提供关系代数优化的工具方法，包含各种辅助函数用于处理RelNode
import org.apache.calcite.plan.RelOptUtil;
// 导入RelNode接口，表示关系代数表达式树的节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule类，是转换规则的基类，用于将一种RelNode转换为另一种RelNode
import org.apache.calcite.rel.convert.ConverterRule;
// 导入Calc类，表示计算关系操作符，包含投影和过滤的组合操作
import org.apache.calcite.rel.core.Calc;
// 导入LogicalCalc类，表示逻辑层面的Calc操作符，是Calc在逻辑计划中的表示
import org.apache.calcite.rel.logical.LogicalCalc;

// 导入Value类，来自Immutables库，用于生成不可变值对象的注解
import org.immutables.value.Value;

/**
 * Rule to convert a {@link LogicalCalc} to an {@link EnumerableCalc}.
 * 这是一个转换规则，用于将逻辑层的LogicalCalc转换为可枚举层的EnumerableCalc
 * You may provide a custom config to convert other nodes that extend {@link Calc}.
 * 你可以提供自定义配置来转换其他继承自Calc的节点
 * 这个规则是Calcite优化器规则体系的一部分，负责将逻辑计划转换为物理执行计划
 * LogicalCalc是逻辑层面的计算节点，包含投影和过滤操作
 * EnumerableCalc是物理层面的计算节点，可以被枚举执行
 * 转换过程会保持原有的程序逻辑不变，只是改变执行方式
 *
 * @see EnumerableRules#ENUMERABLE_CALC_RULE 引用EnumerableRules类中的ENUMERABLE_CALC_RULE常量，这是该规则的默认实例
 */
// @Value.Enclosing注解表示这个类是一个内部值类的封闭类，用于Immutables库生成不可变配置对象
@Value.Enclosing
// EnumerableCalcRule类继承自ConverterRule，是一个转换规则，专门用于将LogicalCalc转换为EnumerableCalc
// ConverterRule是Calcite中所有转换规则的基类，提供了转换的基本框架
class EnumerableCalcRule extends ConverterRule {
  /** Default configuration. */
  // 定义一个静态常量DEFAULT_CONFIG，表示这个规则的默认配置
  // Config是ConverterRule中定义的配置接口，用于配置转换规则的各种参数
  // 这个配置是所有EnumerableCalcRule实例的基础配置
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      // withConversion方法配置转换规则的核心参数
      // LogicalCalc.class: 指定要转换的源节点类型，即从LogicalCalc开始转换
      // RelOptUtil::notContainsWindowedAgg: 指定转换条件，这是一个谓词函数，只有当不包含窗口聚合时才进行转换
      //   这样可以确保FarragoMultisetSplitter先处理包含multiset的情况
      // Convention.NONE: 指定源节点的约定，NONE表示逻辑层，还没有具体的物理实现
      // EnumerableConvention.INSTANCE: 指定目标节点的约定，INSTANCE表示可枚举约定，即使用Java代码执行
      // "EnumerableCalcRule": 指定规则的名称，用于调试和日志输出
      .withConversion(LogicalCalc.class, RelOptUtil::notContainsWindowedAgg,
          Convention.NONE, EnumerableConvention.INSTANCE,
          "EnumerableCalcRule")
      // withRuleFactory方法指定规则工厂，用于创建EnumerableCalcRule实例
      // EnumerableCalcRule::new: 使用方法引用，将构造函数作为工厂方法
      // 这样Config就可以创建EnumerableCalcRule的实例
      .withRuleFactory(EnumerableCalcRule::new);

  // 构造方法，接受Config配置参数
  // config参数包含了规则的所有配置信息，包括转换条件、源类型、目标类型等
  // protected访问级别表示只有子类或同包内的类可以访问
  protected EnumerableCalcRule(Config config) {
    // 调用父类ConverterRule的构造方法，传入配置参数
    // 父类会根据配置初始化规则的各种属性
    super(config);
  }

  // convert方法是ConverterRule的核心方法，负责执行实际的转换操作
  // @Override注解表示这个方法重写了父类ConverterRule的方法
  // rel参数是要转换的源RelNode，在这个规则中应该是LogicalCalc类型
  // 返回值是转换后的RelNode，在这个规则中是EnumerableCalc类型
  @Override public RelNode convert(RelNode rel) {
    // 将传入的RelNode强制转换为Calc类型，因为传入的应该是LogicalCalc，它继承自Calc
    // Calc是一个抽象类，表示包含投影和过滤操作的关系节点
    final Calc calc = (Calc) rel;
    // 获取Calc节点的输入节点，即Calc操作所作用的上游节点
    // 输入节点可能是TableScan、Join或其他关系操作符
    final RelNode input = calc.getInput();
    // 创建并返回EnumerableCalc节点
    // EnumerableCalc.create是工厂方法，用于创建EnumerableCalc实例
    // 第一个参数是转换后的输入节点，需要将输入节点也转换为Enumerable约定
    //   convert方法递归调用，将input转换为EnumerableConvention.INSTANCE约定
    //   input.getTraitSet().replace(EnumerableConvention.INSTANCE)创建新的TraitSet，替换约定为Enumerable
    // 第二个参数是calc.getProgram()，获取Calc节点包含的程序，包括投影和过滤逻辑
    //   程序是一个RexProgram对象，包含了行表达式（RexNode）的集合
    //   程序中定义了如何从输入行计算输出行，包括投影表达式和过滤条件
    return EnumerableCalc.create(
        convert(input,
            input.getTraitSet().replace(EnumerableConvention.INSTANCE)),
        calc.getProgram());
  }
}
