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
package org.apache.calcite.plan.hep; // HepPlanner优化器相关的包，包含启发式优化程序的定义

import org.apache.calcite.plan.CommonRelSubExprRule; // 公共子表达式规则，用于识别和优化重复的子表达式
import org.apache.calcite.plan.RelOptPlanner; // 关系表达式优化器接口，所有优化器的基类
import org.apache.calcite.plan.RelOptRule; // 优化规则接口，定义了如何转换关系表达式

import java.util.ArrayList; // 动态数组实现，用于存储指令列表
import java.util.Collection; // 集合接口，用于传递规则集合
import java.util.List; // 列表接口，定义指令列表的类型

import static com.google.common.base.Preconditions.checkArgument; // 参数检查工具，用于验证方法参数的有效性

/**
 * HepProgramBuilder creates instances of {@link HepProgram}. // HepProgramBuilder用于创建HepProgram实例，HepProgram是HepPlanner优化器的执行程序
 * HepPlanner（Heuristic Planner）是Calcite中的启发式优化器，它通过应用一系列优化规则来改进查询计划
 * HepProgram定义了优化规则的执行顺序、匹配顺序、匹配限制等配置信息
 * HepProgramBuilder采用建造者模式，提供链式调用的API来构建优化程序
 * 支持的功能包括：添加规则类、规则集合、规则实例、规则组、转换规则、公共子表达式规则、匹配顺序控制、匹配限制控制、子程序等
 */
public class HepProgramBuilder {
  //~ Instance fields --------------------------------------------------------
public class HepProgramBuilder {
  //~ Instance fields --------------------------------------------------------

  private final List<HepInstruction> instructions = new ArrayList<>(); // 存储优化程序的所有指令列表，每个指令代表一个优化操作，如添加规则、设置匹配顺序等

  /** If a group is under construction, ordinal of the first instruction in the
   * group; otherwise -1. */
  private int group = -1; // 用于跟踪规则组的构建状态，如果当前正在构建规则组，则存储组中第一条指令的索引位置；否则为-1，表示没有正在构建的规则组

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a new HepProgramBuilder with an initially empty program. The // 创建一个新的HepProgramBuilder实例，初始时程序为空
   * program under construction has an initial match order of // 正在构建的程序具有初始的匹配顺序
   * {@link HepMatchOrder#DEPTH_FIRST}, and an initial match limit of // 默认使用深度优先的匹配顺序
   * {@link HepProgram#MATCH_UNTIL_FIXPOINT}. // 初始匹配限制为MATCH_UNTIL_FIXPOINT，表示会持续匹配直到达到固定点（不再有规则可以应用）
   */
  public HepProgramBuilder() { // 默认构造函数，创建一个空的优化程序构建器
  } // 构造函数体为空，因为成员变量已经在声明时初始化

  //~ Methods ----------------------------------------------------------------

  private void clear() { // 清空构建器的状态，重置为初始状态
    instructions.clear(); // 清空指令列表，移除所有已添加的指令
    group = -1; // 重置规则组状态为-1，表示没有正在构建的规则组
  } // 此方法通常在build()方法中被调用，以便在构建完程序后重置构建器状态，使其可以用于构建新的程序

  /**
   * Adds an instruction to attempt to match any rules of a given class. The // 添加一条指令，尝试匹配指定类别的所有规则
   * order in which the rules within a class will be attempted is arbitrary, // 同一类别的规则尝试匹配的顺序是任意的（不确定的）
   * so if more control is needed, use addRuleInstance instead. // 如果需要更精确的控制，应该使用addRuleInstance方法
   *
   * <p>Note that when this method is used, it is also necessary to add the // 注意：使用此方法时，还需要通过RelOptPlanner.addRule()方法将实际的规则对象添加到优化器中
   * actual rule objects of interest to the planner via // 否则优化器无法找到这些规则
   * {@link RelOptPlanner#addRule}. If the planner does not have any // 如果优化器中没有指定类别的规则，这条指令将不执行任何操作（nop）
   * rules of the given class, this instruction is a nop.
   *
   * <p>TODO: support classification via rule annotations. // TODO：未来计划支持通过规则注解进行分类
   *
   * @param ruleClass class of rules to fire, e.g. ConverterRule.class // 要触发的规则类，例如ConverterRule.class
   */
  public <R extends RelOptRule> HepProgramBuilder addRuleClass( // 泛型方法，R必须是RelOptRule的子类
      Class<R> ruleClass) { // 参数：规则类的Class对象
    return addInstruction(new HepInstruction.RuleClass(ruleClass)); // 创建RuleClass指令并添加到指令列表中，返回this以支持链式调用
  } // 这种方式允许在运行时动态添加规则，而不需要在编译时确定具体规则

  /**
   * Adds an instruction to attempt to match any rules in a given collection. // 添加一条指令，尝试匹配给定集合中的所有规则
   * The order in which the rules within a collection will be attempted is // 集合中规则的尝试匹配顺序是任意的
   * arbitrary, so if more control is needed, use addRuleInstance instead. The // 如果需要更精确的控制，应该使用addRuleInstance方法
   * collection can be "live" in the sense that not all rule instances need to // 集合可以是"动态的"，意味着调用此方法时不需要所有规则实例都已添加到集合中
   * have been added to it at the time this method is called. The collection // 集合内容会在每次程序执行时重新评估
   * contents are reevaluated for each execution of the program.
   *
   * <p>Note that when this method is used, it is NOT necessary to add the // 注意：使用此方法时，不需要通过RelOptPlanner.addRule()将规则添加到优化器中
   * rules to the planner via {@link RelOptPlanner#addRule}; the instances // 这里提供的规则实例将被直接使用
   * supplied here will be used. However, adding the rules to the planner // 但是，冗余地添加规则到优化器是良好的实践，因为其他优化器可能需要它
   * redundantly is good form since other planners may require it.
   *
   * @param rules collection of rules to fire // 要触发的规则集合
   */
  public HepProgramBuilder addRuleCollection(Collection<RelOptRule> rules) { // 参数：规则集合
    return addInstruction(new HepInstruction.RuleCollection(rules)); // 创建RuleCollection指令并添加到指令列表中，返回this以支持链式调用
  } // 这种方式适合一次性添加多个规则，且集合内容可以在运行时动态变化

  /**
   * Adds an instruction to attempt to match a specific rule object. // 添加一条指令，尝试匹配指定的规则对象
   *
   * <p>Note that when this method is used, it is NOT necessary to add the // 注意：使用此方法时，不需要通过RelOptPlanner.addRule()将规则添加到优化器中
   * rule to the planner via {@link RelOptPlanner#addRule}; the instance // 这里提供的规则实例将被直接使用
   * supplied here will be used. However, adding the rule to the planner // 但是，冗余地添加规则到优化器是良好的实践，因为其他优化器可能需要它
   * redundantly is good form since other planners may require it.
   *
   * @param rule rule to fire // 要触发的规则对象
   */
  public HepProgramBuilder addRuleInstance(RelOptRule rule) { // 参数：具体的规则实例
    return addInstruction(new HepInstruction.RuleInstance(rule)); // 创建RuleInstance指令并添加到指令列表中，返回this以支持链式调用
  } // 这是最精确的规则添加方式，可以完全控制规则的执行顺序和时机

  /**
   * Adds an instruction to attempt to match a specific rule identified by its // 添加一条指令，尝试匹配通过唯一描述标识的特定规则
   * unique description.
   *
   * <p>Note that when this method is used, it is necessary to also add the // 注意：使用此方法时，需要通过RelOptPlanner.addRule()将规则对象添加到优化器中
   * rule object of interest to the planner via {@link RelOptPlanner#addRule}. // 这允许优化器和插件之间解耦：优化器只知道规则描述，而插件提供实际实例
   * This allows for some decoupling between optimizers and plugins: the // 如果优化器中没有匹配描述的规则，这条指令将不执行任何操作（nop）
   * optimizer only knows about rule descriptions, while the plugins supply // 这种方式支持插件化的架构，使得优化器核心不需要知道具体的规则实现
   * the actual instances. If the planner does not have a rule matching the
   * description, this instruction is a nop.
   *
   * @param ruleDescription description of rule to fire // 要触发的规则的描述字符串
   */
  public HepProgramBuilder addRuleByDescription(String ruleDescription) { // 参数：规则的描述字符串
    return addInstruction( // 创建RuleLookup指令并添加到指令列表中，返回this以支持链式调用
        new HepInstruction.RuleLookup(ruleDescription)); // RuleLookup会在执行时通过描述查找对应的规则实例
  } // 这种方式适合在优化器和规则实现之间需要解耦的场景，例如插件系统

  /**
   * Adds an instruction to begin a group of rules. All subsequent rules added // 添加一条指令，开始一个规则组。所有后续添加的规则
   * (until the next endRuleGroup) will be collected into the group rather // （直到下一个addGroupEnd）将被收集到组中，而不是单独触发
   * than firing individually. After addGroupBegin has been called, only // 调用addGroupBegin后，只能调用addRuleXXX方法，直到调用addGroupEnd
   * addRuleXXX methods may be called until the next addGroupEnd.
   */
  public HepProgramBuilder addGroupBegin() { // 开始一个规则组
    checkArgument(group < 0); // 检查当前没有正在构建的规则组，避免嵌套规则组
    group = instructions.size(); // 记录当前指令列表的大小，这是规则组第一条指令的索引位置
    return addInstruction(new HepInstruction.Placeholder()); // 添加一个占位符指令，稍后会被替换为BeginGroup指令
  } // 规则组允许将多个规则作为一个整体进行优化，可以提高优化效率

  /**
   * Adds an instruction to end a group of rules, firing the group // 添加一条指令，结束规则组，并集体触发组中的规则
   * collectively. The order in which the rules within a group will be // 组内规则的尝试匹配顺序是任意的
   * attempted is arbitrary. Match order and limit applies to the group as a // 匹配顺序和限制应用于整个规则组，而不是组内的单个规则
   * whole.
   */
  public HepProgramBuilder addGroupEnd() { // 结束规则组
    checkArgument(group >= 0); // 检查当前有正在构建的规则组，确保addGroupBegin已被调用
    final HepInstruction.EndGroup endGroup = new HepInstruction.EndGroup(); // 创建EndGroup指令，表示规则组的结束
    instructions.set(group, new HepInstruction.BeginGroup(endGroup)); // 将之前添加的占位符替换为BeginGroup指令，并关联EndGroup
    group = -1; // 重置group状态为-1，表示规则组构建完成
    return addInstruction(endGroup); // 添加EndGroup指令到指令列表末尾，返回this以支持链式调用
  } // 规则组作为一个整体进行优化，可以提高优化效率，减少重复的匹配和转换操作

  /**
   * Adds an instruction to attempt to match instances of // 添加一条指令，尝试匹配转换规则实例
   * {@link org.apache.calcite.rel.convert.ConverterRule}, // ConverterRule是用于将关系表达式从一种特征转换为另一种特征的规则
   * but only where a conversion is actually required. // 但只在实际需要转换的地方进行匹配
   *
   * @param guaranteed if true, use only guaranteed converters; if false, use // 参数：如果为true，只使用保证的转换器；如果为false，只使用非保证的转换器
   *                   only non-guaranteed converters // 保证的转换器是指能够保证转换结果的转换器，非保证的转换器可能会失败
   */
  public HepProgramBuilder addConverters(boolean guaranteed) { // 添加转换规则指令
    checkArgument(group < 0); // 检查当前没有正在构建的规则组，确保不在规则组中
    return addInstruction(new HepInstruction.ConverterRules(guaranteed)); // 创建ConverterRules指令并添加到指令列表中，返回this以支持链式调用
  } // 转换规则用于在不同特征之间转换关系表达式，例如从逻辑特征转换为物理特征

  /**
   * Adds an instruction to attempt to match instances of // 添加一条指令，尝试匹配公共子表达式规则实例
   * {@link CommonRelSubExprRule}, but only in cases where vertices have more // 但只在顶点有多个父节点的情况下
   * than one parent. // 公共子表达式是指在查询计划中出现多次的相同子表达式，可以通过提取公共子表达式来优化查询
   */
  public HepProgramBuilder addCommonRelSubExprInstruction() { // 添加公共子表达式规则指令
    checkArgument(group < 0); // 检查当前没有正在构建的规则组，确保不在规则组中
    return addInstruction(new HepInstruction.CommonRelSubExprRules()); // 创建CommonRelSubExprRules指令并添加到指令列表中，返回this以支持链式调用
  } // 公共子表达式优化可以减少重复计算，提高查询性能

  /**
   * Adds an instruction to change the order of pattern matching for // 添加一条指令，改变后续指令的模式匹配顺序
   * subsequent instructions. The new order will take effect for the rest of // 新的匹配顺序将在程序的剩余部分生效（不包括子程序）
   * the program (not counting subprograms) or until another match order // 或者直到遇到另一个匹配顺序指令
   * instruction is encountered.
   *
   * @param order new match direction to set // 参数：要设置的新匹配方向，如DEPTH_FIRST（深度优先）或ARBITRARY（任意）
   */
  public HepProgramBuilder addMatchOrder(HepMatchOrder order) { // 设置模式匹配顺序
    checkArgument(group < 0); // 检查当前没有正在构建的规则组，确保不在规则组中
    return addInstruction(new HepInstruction.MatchOrder(order)); // 创建MatchOrder指令并添加到指令列表中，返回this以支持链式调用
  } // 匹配顺序影响规则应用时如何遍历表达式树，深度优先通常能更快找到匹配

  /**
   * Adds an instruction to limit the number of pattern matches for subsequent // 添加一条指令，限制后续指令的模式匹配次数
   * instructions. The limit will take effect for the rest of the program (not // 限制将在程序的剩余部分生效（不包括子程序）
   * counting subprograms) or until another limit instruction is encountered. // 或者直到遇到另一个限制指令
   *
   * @param limit limit to set; use {@link HepProgram#MATCH_UNTIL_FIXPOINT} to // 参数：要设置的限制值；使用MATCH_UNTIL_FIXPOINT来移除限制
   *              remove limit // MATCH_UNTIL_FIXPOINT表示持续匹配直到达到固定点（不再有规则可以应用）
   */
  public HepProgramBuilder addMatchLimit(int limit) { // 设置模式匹配次数限制
    checkArgument(group < 0); // 检查当前没有正在构建的规则组，确保不在规则组中
    return addInstruction(new HepInstruction.MatchLimit(limit)); // 创建MatchLimit指令并添加到指令列表中，返回this以支持链式调用
  } // 匹配限制可以控制优化过程的复杂度，防止无限循环或过长的优化时间

  /**
   * Adds an instruction to execute a subprogram. Note that this is different // 添加一条指令，执行子程序。注意这与单独添加子程序的指令不同
   * from adding the instructions from the subprogram individually. When added // 当作为子程序添加时，序列将重复执行直到达到固定点
   * as a subprogram, the sequence will execute repeatedly until a fixpoint is // 而当单独添加指令时，序列只会执行一次（每个指令有单独的固定点）
   * reached, whereas when the instructions are added individually, the
   * sequence will only execute once (with a separate fixpoint for each
   * instruction).
   *
   * <p>The subprogram has its own state for match order and limit // 子程序有自己的匹配顺序和限制状态
   * (initialized to the defaults every time the subprogram is executed) and // （每次执行子程序时都会初始化为默认值）
   * any changes it makes to those settings do not affect the parent program. // 它对这些设置的任何更改都不会影响父程序
   *
   * @param program subProgram to execute // 参数：要执行的子程序
   */
  public HepProgramBuilder addSubprogram(HepProgram program) { // 添加子程序指令
    checkArgument(group < 0); // 检查当前没有正在构建的规则组，确保不在规则组中
    return addInstruction(new HepInstruction.SubProgram(program)); // 创建SubProgram指令并添加到指令列表中，返回this以支持链式调用
  } // 子程序允许将一组优化规则封装成一个可重复执行的单元，直到达到固定点

  private HepProgramBuilder addInstruction(HepInstruction instruction) { // 私有辅助方法，将指令添加到指令列表中
    instructions.add(instruction); // 将指令添加到指令列表的末尾
    return this; // 返回this以支持链式调用，这是建造者模式的核心特性
  } // 所有公开的addXXX方法都通过此方法添加指令，确保了一致性和可维护性

  /**
   * Returns the constructed program, clearing the state of this program // 返回构建的程序，作为副作用清空此程序构建器的状态
   * builder as a side-effect.
   *
   * @return immutable program // 返回值：不可变的程序对象
   */
  public HepProgram build() { // 构建并返回HepProgram实例
    checkArgument(group < 0); // 检查当前没有正在构建的规则组，确保所有规则组都已正确关闭
    HepProgram program = new HepProgram(instructions); // 使用当前指令列表创建新的HepProgram实例
    clear(); // 清空构建器的状态，使其可以用于构建新的程序
    return program; // 返回构建好的不可变程序对象
  } // 这是建造者模式的最后一步，创建最终的不可变对象并重置构建器状态
}
