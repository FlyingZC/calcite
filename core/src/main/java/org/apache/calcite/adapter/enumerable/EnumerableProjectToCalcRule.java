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
// 声明当前类所在的包路径：org.apache.calcite.adapter.enumerable，这是Calcite中可枚举适配器包，包含用于生成可枚举代码的规则和实现
package org.apache.calcite.adapter.enumerable;

// 导入RelOptRuleCall类，表示优化规则调用的上下文对象，包含规则匹配时的相关信息
import org.apache.calcite.plan.RelOptRuleCall;
// 导入RelNode类，表示关系代数节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.RelNode;
// 导入ProjectToCalcRule类，这是父类，提供了将Project算子转换为Calc算子的通用逻辑
import org.apache.calcite.rel.rules.ProjectToCalcRule;
// 导入RexProgram类，表示行表达式程序，用于描述一组行表达式及其输入和输出类型
import org.apache.calcite.rex.RexProgram;
// 导入RelBuilderFactory类，用于创建关系节点构建器的工厂类
import org.apache.calcite.tools.RelBuilderFactory;

// 导入Value注解，来自Immutables库，用于生成不可变值对象的代码
import org.immutables.value.Value;

/**
 * 这是一个专门用于可枚举调用约定（EnumerableConvention）的ProjectToCalcRule变体
 * 
 * 类作用详解：
 * 1. 这个规则是Calcite优化器规则体系中的重要组成部分
 * 2. 它的作用是将EnumerableProject（可枚举投影操作）转换为EnumerableCalc（可枚举计算操作）
 * 3. Project操作是SQL中的投影操作，用于选择、重命名或计算列
 * 4. Calc操作是Calcite中更通用的计算操作，可以同时实现投影、过滤等多种功能
 * 5. 转换为Calc的好处是可以进一步优化，比如与相邻的Calc合并，减少中间结果
 * 6. 这个规则专门针对可枚举调用约定，意味着生成的代码是基于Java的Linq4j枚举接口
 * 
 * 为什么需要这个转换：
 * - Project只能实现简单的列投影和表达式计算
 * - Calc可以同时处理投影、过滤、条件等多种操作
 * - 通过将Project转换为Calc，可以与其他Calc操作合并，优化执行计划
 * - 可枚举约定意味着最终会生成Java代码来执行查询
 * 
 * 使用场景：
 * - 当优化器发现EnumerableProject节点时，会尝试应用此规则
 * - 转换后的EnumerableCalc可以与相邻的EnumerableCalc合并
 * - 最终生成更高效的执行代码
 *
 * @see EnumerableRules#ENUMERABLE_PROJECT_TO_CALC_RULE 参见EnumerableRules中定义的规则常量
 */
// @Value.Enclosing注解表示这个类包含一个使用@Value.Immutable注解的内部接口，用于生成不可变配置对象
@Value.Enclosing
// 定义EnumerableProjectToCalcRule类，继承自ProjectToCalcRule，表示这是一个针对可枚举约定的Project到Calc转换规则
public class EnumerableProjectToCalcRule extends ProjectToCalcRule {
  /**
   * 构造方法：使用配置对象创建EnumerableProjectToCalcRule实例
   * 
   * @param config 规则配置对象，包含规则的各种配置参数，如操作数匹配器、RelBuilderFactory等
   * 
   * 作用：
   * - 这是推荐的构造方式，使用不可变配置对象来初始化规则
   * - 配置对象包含了规则的所有必要参数，使规则更加灵活和可配置
   * - 通过调用父类构造器super(config)，将配置传递给父类ProjectToCalcRule
   * - 父类会根据配置初始化规则的操作数匹配器、描述等属性
   */
  // protected访问修饰符表示这个构造方法只能被子类或同包内的类调用，外部无法直接使用
  protected EnumerableProjectToCalcRule(Config config) {
    // 调用父类ProjectToCalcRule的构造方法，将配置对象传递给父类进行初始化
    // 父类会使用config中的信息来设置规则的各种属性，如操作数匹配器、描述等
    super(config);
  }

  /**
   * 已废弃的构造方法：使用RelBuilderFactory创建规则实例
   * 
   * @param relBuilderFactory 关系节点构建器工厂，用于创建RelNode构建器
   * 
   * 作用：
   * - 这是一个旧的构造方法，已标记为@Deprecated，将在2.0版本前移除
   * - 为了向后兼容而保留，允许旧的代码继续使用
   * - 内部会将RelBuilderFactory转换为Config对象，然后调用新的构造方法
   * - 新代码应该使用Config构造方法
   * 
   * 为什么废弃：
   * - 新的Config模式更加灵活，可以配置更多参数
   * - 符合Immutables库的设计模式，使用不可变配置对象
   * - 便于规则的可配置性和扩展性
   */
  // @Deprecated注解表示这个方法已废弃，编译器会发出警告，提醒开发者使用新的构造方法
  @Deprecated // to be removed before 2.0
  // public访问修饰符表示这个构造方法可以从任何地方调用
  public EnumerableProjectToCalcRule(RelBuilderFactory relBuilderFactory) {
    // 调用新的Config构造方法：使用默认配置，并设置RelBuilderFactory
    // Config.DEFAULT获取默认配置对象
    // .withRelBuilderFactory(relBuilderFactory)设置关系构建器工厂
    // .as(Config.class)将配置转换为Config接口类型
    // this(...)调用当前类的另一个构造方法（带Config参数的构造方法）
    this(Config.DEFAULT.withRelBuilderFactory(relBuilderFactory)
        .as(Config.class));
  }

  /**
   * 核心方法：当规则匹配时调用，执行Project到Calc的转换
   * 
   * @param call 规则调用对象，包含匹配到的关系节点和优化器上下文
   * 
   * 作用：
   * - 这是规则执行的入口方法，当优化器发现匹配的EnumerableProject节点时会调用
   * - 提取Project节点及其输入
   * - 创建RexProgram来描述Project的表达式计算
   * - 使用RexProgram创建EnumerableCalc节点
   * - 将优化后的节点返回给优化器
   * 
   * 执行流程：
   * 1. 从调用对象中获取匹配到的EnumerableProject节点
   * 2. 获取Project节点的输入关系节点
   * 3. 创建RexProgram，封装Project的所有投影表达式
   * 4. 使用RexProgram创建新的EnumerableCalc节点
   * 5. 将转换结果通知优化器
   * 
   * 技术细节：
   * - RexProgram是Calcite中用于描述行表达式计算的核心数据结构
   * - 它包含输入类型、输出类型、表达式列表、条件等信息
   * - EnumerableCalc是可枚举约定的Calc节点，可以生成Java代码执行
   */
  // @Override注解表示这个方法重写了父类ProjectToCalcRule的onMatch方法
  // public访问修饰符表示这个方法可以被外部调用
  // void表示这个方法没有返回值
  @Override public void onMatch(RelOptRuleCall call) {
    // 从规则调用对象中获取第0个关系节点，即匹配到的EnumerableProject节点
    // call.rel(0)返回匹配到的第一个操作数，根据配置，这里就是EnumerableProject
    // final关键字表示这个引用不能被重新赋值，保证引用不变性
    final EnumerableProject project = call.rel(0);
    // 获取Project节点的输入关系节点
    // project.getInput()返回Project的子节点，通常是表扫描或其他关系操作
    // 这是Project操作的数据来源
    final RelNode input = project.getInput();
    // 创建RexProgram对象，这是转换的核心步骤
    // RexProgram.create是静态工厂方法，用于创建行表达式程序
    // 参数1：input.getRowType() - 输入行的类型，描述输入数据的列信息（列名、类型等）
    // 参数2：project.getProjects() - 投影表达式列表，描述如何从输入列计算出输出列
    //        每个表达式对应一个输出列，可以是简单的列引用，也可以是复杂的计算表达式
    // 参数3：null - 表示没有过滤条件（Project操作不包含WHERE子句）
    //        如果是Project+Filter合并的Calc，这里会有条件表达式
    // 参数4：project.getRowType() - 输出行的类型，描述输出数据的列信息
    //        这决定了最终查询结果的列名和类型
    // 参数5：project.getCluster().getRexBuilder() - Rex构建器，用于创建行表达式
    //        RexBuilder提供了创建各种行表达式（如常量、函数调用、引用等）的工厂方法
    final RexProgram program =
        RexProgram.create(input.getRowType(),
            project.getProjects(),
            null,
            project.getRowType(),
            project.getCluster().getRexBuilder());
    // 使用输入节点和RexProgram创建EnumerableCalc节点
    // EnumerableCalc.create是静态工厂方法，用于创建可枚举的Calc节点
    // 参数1：input - Calc节点的输入，与原Project的输入相同
    // 参数2：program - 描述Calc操作的RexProgram，包含所有计算逻辑
    // 返回的EnumerableCalc节点可以生成Java代码来执行计算
    // 这样就将Project操作转换为了更通用的Calc操作
    final EnumerableCalc calc = EnumerableCalc.create(input, program);
    // 将转换结果通知优化器，完成规则的应用
    // call.transformTo(calc)告诉优化器将匹配的Project节点替换为新的Calc节点
    // 优化器会继续应用其他规则对这个Calc节点进行优化
    // 例如，可能会将这个Calc与相邻的Calc合并，或者进一步转换为物理执行计划
    call.transformTo(calc);
  }

  /**
   * 规则配置接口：定义规则的可配置属性
   * 
   * 接口作用详解：
   * 1. 使用Immutables库的@Value.Immutable注解，自动生成不可变的配置实现类
   * 2. 继承自ProjectToCalcRule.Config，复用父类的配置定义
   * 3. 提供默认配置实例DEFAULT，包含标准的操作数匹配器
   * 4. 定义toRule方法，将配置转换为规则实例
   * 
   * 为什么需要配置接口：
   * - 使规则可配置化，可以根据需要调整规则的行为
   * - 支持不可变对象模式，保证线程安全和一致性
   * - 符合Calcite规则框架的设计模式
   * - 便于规则的可扩展性和可测试性
   * 
   * 配置内容包括：
   * - 操作数匹配器（OperandSupplier）：定义规则匹配哪些类型的节点
   * - RelBuilderFactory：用于创建关系节点
   * - 规则描述等其他元信息
   */
  // @Value.Immutable注解告诉Immutables库自动生成ImmutableEnumerableProjectToCalcRule.Config实现类
  // 生成的类是不可变的，所有字段都是final的，只能通过builder模式创建
  @Value.Immutable
  // @SuppressWarnings注解抑制Immutables相关的警告，因为Immutables会生成代码
  @SuppressWarnings("immutables")
  // public interface表示这是一个公共接口，可以被其他类访问和实现
  // Config接口继承自ProjectToCalcRule.Config，复用父类的配置方法
  public interface Config extends ProjectToCalcRule.Config {
    /**
     * 默认配置实例：包含标准的操作数匹配器配置
     * 
     * 作用：
     * - 提供一个预定义的默认配置，无需手动配置即可使用
     * - 设置操作数匹配器，使规则匹配任意输入的EnumerableProject节点
     * - 其他配置项使用父类的默认值
     * 
     * 配置详解：
     * - ImmutableEnumerableProjectToCalcRule.Config.of()：创建空的配置构建器
     * - .withOperandSupplier(...)：设置操作数匹配器
     *   - b -> b.operand(EnumerableProject.class).anyInputs()
     *   - operand(EnumerableProject.class)：匹配EnumerableProject类型的节点
     *   - anyInputs()：匹配任意输入的Project节点，不限制输入的类型和数量
     * - 这样配置后，规则会匹配所有的EnumerableProject节点，不论它们的输入是什么
     */
    // Config是接口类型，DEFAULT是一个静态常量，存储默认配置实例
    Config DEFAULT = ImmutableEnumerableProjectToCalcRule.Config.of()
        // 设置操作数匹配器，这是一个lambda表达式
        // 参数b是OperandSupplier的构建器，用于定义匹配规则
        .withOperandSupplier(b ->
            // 定义要匹配的操作数：匹配EnumerableProject类型的节点
            // .anyInputs()表示匹配任意输入的Project节点
            // 这意味着规则会对所有EnumerableProject节点生效
            b.operand(EnumerableProject.class).anyInputs());

    /**
     * 将配置转换为规则实例
     * 
     * @return 返回一个新的EnumerableProjectToCalcRule实例
     * 
     * 作用：
     * - 这是配置接口的默认方法，提供了从配置到规则的转换逻辑
     * - 优化器框架会调用这个方法来创建规则实例
     * - 使用this关键字引用当前配置对象
     * - 返回使用当前配置创建的规则实例
     * 
     * 设计模式：
     * - 这是工厂方法模式的应用
     * - 配置对象作为工厂，负责创建规则实例
     * - 解耦了规则创建和规则使用
     * - 便于规则的可配置性和可扩展性
     */
    // @Override注解表示这个方法重写了父接口ProjectToCalcRule.Config的toRule方法
    // default关键字表示这是接口的默认方法，提供了默认实现
    // 方法返回类型是EnumerableProjectToCalcRule，表示返回一个规则实例
    @Override default EnumerableProjectToCalcRule toRule() {
      // 创建并返回一个新的EnumerableProjectToCalcRule实例
      // this关键字传递当前配置对象给构造方法
      // 构造方法会使用配置中的所有参数来初始化规则
      return new EnumerableProjectToCalcRule(this);
    }
  }
}
