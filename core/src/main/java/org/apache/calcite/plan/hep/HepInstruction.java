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
package org.apache.calcite.plan.hep;  // 声明包名，该类属于HepPlanner（Hepatic Planner，基于启发式转换的优化器）的指令系统

import org.apache.calcite.plan.RelOptRule;  // 导入RelOptRule类，表示优化规则

import com.google.common.collect.ImmutableList;  // 导入Google Guava的不可变列表，用于存储规则集合

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;  // 导入空值检查框架的注解，表示可能为null但一旦赋值后不会再变为null
import org.checkerframework.checker.nullness.qual.Nullable;  // 导入空值检查框架的注解，表示可能为null

import java.util.Collection;  // 导入Java集合接口
import java.util.HashSet;  // 导入HashSet集合类，用于存储规则集合
import java.util.List;  // 导入List接口
import java.util.Set;  // 导入Set接口

import static org.apache.calcite.linq4j.Nullness.castNonNull;  // 导入静态方法，用于将可能为null的对象转换为非null对象

import static java.util.Objects.requireNonNull;  // 导入静态方法，用于检查对象是否为null，如果为null则抛出NullPointerException

/**
 * HepInstruction represents one instruction in a HepProgram. The actual
 * instruction set is defined here via inner classes; if these grow too big,
 * they should be moved out to top-level classes.
 */
// HepInstruction表示HepProgram（HepPlanner的程序）中的一条指令
// 实际的指令集通过内部类在这里定义；如果这些内部类变得太大，应该移到顶层类中
// 这个类是抽象的，作为所有具体指令的基类
abstract class HepInstruction {
  //~ Methods ----------------------------------------------------------------  // 方法区域分隔符

  /** Creates runtime state for this instruction.
   *
   * <p>The state is mutable, knows how to execute the instruction, and is
   * discarded after this execution. See {@link HepState}.
   *
   * @param px Preparation context; the state should copy from the context
   * all information that it will need to execute
   *
   * @return Initialized state
   */
  // 为此指令创建运行时状态
  // 状态是可变的，知道如何执行该指令，并在执行后被丢弃
  // 参见HepState类
  // 参数px：准备上下文；状态应该从上下文中复制执行所需的所有信息
  // 返回值：初始化后的状态对象
  abstract HepState prepare(PrepareContext px);  // 抽象方法，由子类实现，用于创建指令的执行状态

  //~ Inner Classes ----------------------------------------------------------  // 内部类区域分隔符

  /** Instruction that executes all rules of a given class. */
  // 指令：执行给定类的所有规则
  // 这个内部类表示一条指令，用于执行某个特定RelOptRule子类的所有规则实例
  static class RuleClass extends HepInstruction {  // 继承自HepInstruction，表示执行规则类的指令
    final Class<? extends RelOptRule> ruleClass;  // 成员变量：存储规则类的Class对象，必须是RelOptRule的子类，final表示不可变

    <R extends RelOptRule> RuleClass(Class<R> ruleClass) {  // 泛型构造方法，R必须继承自RelOptRule
      this.ruleClass = requireNonNull(ruleClass, "ruleClass");  // 通过requireNonNull检查参数不为null，否则抛出NullPointerException，"ruleClass"是错误信息
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link RuleClass} instruction. */
    // RuleClass指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示RuleClass指令的执行状态
      /** Actual rule set instantiated during planning by filtering all the
       * planner's rules through {@link #ruleClass}. */
      // 实际的规则集合，在规划期间通过过滤规划器的所有规则并匹配ruleClass来实例化
      @Nullable Set<RelOptRule> ruleSet;  // 成员变量：存储实际要执行的规则集合，@Nullable表示可能为null

      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeRuleClass(RuleClass.this, this);  // 调用规划器的executeRuleClass方法，传入RuleClass指令和当前状态
      }
    }
  }

  /** Instruction that executes all rules in a given collection. */
  // 指令：执行给定集合中的所有规则
  // 这个内部类表示一条指令，用于执行用户提供的规则集合中的所有规则
  static class RuleCollection extends HepInstruction {  // 继承自HepInstruction，表示执行规则集合的指令
    /** Collection of rules to apply. */
    // 要应用的规则集合
    final List<RelOptRule> rules;  // 成员变量：存储要执行的规则列表，使用不可变List，final表示不可变

    RuleCollection(Collection<RelOptRule> rules) {  // 构造方法，接收规则集合
      this.rules = ImmutableList.copyOf(rules);  // 使用Guava的ImmutableList.copyOf创建规则的不可变副本，确保规则集合不会被修改
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link RuleCollection} instruction. */
    // RuleCollection指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示RuleCollection指令的执行状态
      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeRuleCollection(RuleCollection.this, this);  // 调用规划器的executeRuleCollection方法，传入RuleCollection指令和当前状态
      }
    }
  }

  /** Instruction that executes converter rules. */
  // 指令：执行转换规则
  // 这个内部类表示一条指令，用于执行所有转换规则（Converter Rules）
  // 转换规则是特殊的优化规则，用于将关系表达式从一种特征（trait）转换为另一种特征
  static class ConverterRules extends HepInstruction {  // 继承自HepInstruction，表示执行转换规则的指令
    final boolean guaranteed;  // 成员变量：标志位，表示转换是否保证成功，final表示不可变

    ConverterRules(boolean guaranteed) {  // 构造方法，接收转换是否保证成功的标志
      this.guaranteed = guaranteed;  // 设置guaranteed标志，如果为true表示转换必须成功，否则可以失败
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link ConverterRules} instruction. */
    // ConverterRules指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示ConverterRules指令的执行状态
      /** Actual rule set instantiated during planning by filtering all the
       * planner's rules, looking for the desired converters. */
      // 实际的规则集合，在规划期间通过过滤规划器的所有规则，查找所需的转换规则来实例化
      @MonotonicNonNull Set<RelOptRule> ruleSet;  // 成员变量：存储转换规则集合，@MonotonicNonNull表示可能为null但一旦赋值后不会再变为null

      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeConverterRules(ConverterRules.this, this);  // 调用规划器的executeConverterRules方法，传入ConverterRules指令和当前状态
      }
    }
  }

  /** Instruction that finds common relational sub-expressions. */
  // 指令：查找公共关系子表达式
  // 这个内部类表示一条指令，用于查找和识别公共的子表达式（Common Subexpression Elimination，CSE）
  // 公共子表达式消除是一种优化技术，通过识别和重用重复的子表达式来减少计算
  static class CommonRelSubExprRules extends HepInstruction {  // 继承自HepInstruction，表示查找公共子表达式的指令
    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link CommonRelSubExprRules} instruction. */
    // CommonRelSubExprRules指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示CommonRelSubExprRules指令的执行状态
      @Nullable Set<RelOptRule> ruleSet;  // 成员变量：存储规则集合，@Nullable表示可能为null

      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeCommonRelSubExprRules(CommonRelSubExprRules.this, this);  // 调用规划器的executeCommonRelSubExprRules方法，传入指令和当前状态
      }
    }
  }

  /** Instruction that executes a given rule. */
  // 指令：执行给定的单个规则
  // 这个内部类表示一条指令，用于执行用户明确指定的单个优化规则
  static class RuleInstance extends HepInstruction {  // 继承自HepInstruction，表示执行单个规则实例的指令
    /** Explicitly specified rule. */
    // 明确指定的规则
    final RelOptRule rule;  // 成员变量：存储要执行的具体规则对象，final表示不可变

    RuleInstance(RelOptRule rule) {  // 构造方法，接收规则对象
      this.rule = requireNonNull(rule, "rule");  // 通过requireNonNull检查参数不为null，否则抛出NullPointerException，"rule"是错误信息
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link RuleInstance} instruction. */
    // RuleInstance指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示RuleInstance指令的执行状态
      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeRuleInstance(RuleInstance.this, this);  // 调用规划器的executeRuleInstance方法，传入RuleInstance指令和当前状态
      }
    }
  }

  /** Instruction that executes a rule that is looked up by description. */
  // 指令：执行通过描述查找的规则
  // 这个内部类表示一条指令，用于通过规则的描述字符串来查找并执行对应的规则
  // 这是一种动态查找机制，允许在运行时根据描述来定位规则
  static class RuleLookup extends HepInstruction {  // 继承自HepInstruction，表示通过描述查找并执行规则的指令
    /** Description to look for. */
    // 要查找的描述字符串
    final String ruleDescription;  // 成员变量：存储规则的描述字符串，用于查找匹配的规则，final表示不可变

    RuleLookup(String ruleDescription) {  // 构造方法，接收规则描述字符串
      this.ruleDescription = requireNonNull(ruleDescription, "ruleDescription");  // 通过requireNonNull检查参数不为null，否则抛出NullPointerException
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link RuleLookup} instruction. */
    // RuleLookup指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示RuleLookup指令的执行状态
      /** Rule looked up by planner from description. */
      // 由规划器从描述中查找出的规则
      @Nullable RelOptRule rule;  // 成员变量：存储查找到的规则对象，@Nullable表示可能为null（如果未找到匹配的规则）

      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void init() {  // 重写父类的init方法，初始化状态
        // Look up anew each run.
        // 每次运行时重新查找
        rule = null;  // 将规则设置为null，表示需要重新查找，这样可以支持多次运行时的动态查找
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeRuleLookup(RuleLookup.this, this);  // 调用规划器的executeRuleLookup方法，传入RuleLookup指令和当前状态
      }
    }
  }

  /** Instruction that sets match order. */
  // 指令：设置匹配顺序
  // 这个内部类表示一条指令，用于设置规则匹配的顺序策略
  // 匹配顺序决定了优化器如何遍历和匹配规则，影响优化过程的效率和结果
  static class MatchOrder extends HepInstruction {  // 继承自HepInstruction，表示设置匹配顺序的指令
    final HepMatchOrder order;  // 成员变量：存储匹配顺序枚举值，final表示不可变

    MatchOrder(HepMatchOrder order) {  // 构造方法，接收匹配顺序枚举值
      this.order = requireNonNull(order, "order");  // 通过requireNonNull检查参数不为null，否则抛出NullPointerException，"order"是错误信息
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link MatchOrder} instruction. */
    // MatchOrder指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示MatchOrder指令的执行状态
      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeMatchOrder(MatchOrder.this, this);  // 调用规划器的executeMatchOrder方法，传入MatchOrder指令和当前状态
      }
    }
  }

  /** Instruction that sets match limit. */
  // 指令：设置匹配限制
  // 这个内部类表示一条指令，用于设置规则匹配的最大次数限制
  // 这是一种性能优化措施，防止优化器在匹配规则时进行无限循环或过多的尝试
  static class MatchLimit extends HepInstruction {  // 继承自HepInstruction，表示设置匹配限制的指令
    final int limit;  // 成员变量：存储匹配次数的上限值，final表示不可变

    MatchLimit(int limit) {  // 构造方法，接收限制数值
      this.limit = limit;  // 设置匹配次数的上限
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link MatchLimit} instruction. */
    // MatchLimit指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示MatchLimit指令的执行状态
      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeMatchLimit(MatchLimit.this, this);  // 调用规划器的executeMatchLimit方法，传入MatchLimit指令和当前状态
      }
    }
  }

  /** Instruction that executes a sub-program. */
  // 指令：执行子程序
  // 这个内部类表示一条指令，用于执行一个子程序（HepProgram）
  // 这允许构建复杂的优化程序结构，支持嵌套和模块化的优化策略
  static class SubProgram extends HepInstruction {  // 继承自HepInstruction，表示执行子程序的指令
    final HepProgram subProgram;  // 成员变量：存储要执行的子程序对象，final表示不可变

    SubProgram(HepProgram subProgram) {  // 构造方法，接收子程序对象
      this.subProgram = requireNonNull(subProgram, "subProgram");  // 通过requireNonNull检查参数不为null，否则抛出NullPointerException
    }

    @Override HepProgram.State prepare(PrepareContext px) {  // 重写父类的prepare方法，返回HepProgram.State类型
      return subProgram.prepare(px);  // 调用子程序的prepare方法，创建并返回子程序的执行状态
    }

    /** State for a {@link SubProgram} instruction. */
    // SubProgram指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示SubProgram指令的执行状态
      final HepProgram.State subProgramState;  // 成员变量：存储子程序的执行状态对象，final表示不可变

      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
        subProgramState = subProgram.prepare(px);  // 调用子程序的prepare方法，创建子程序的执行状态并保存
      }

      @Override void init() {  // 重写父类的init方法，初始化状态
        subProgramState.init();  // 同时也初始化子程序的状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeSubProgram(SubProgram.this, this);  // 调用规划器的executeSubProgram方法，传入SubProgram指令和当前状态
      }
    }
  }

  /** Instruction that begins a group. */
  // 指令：开始一个规则组
  // 这个内部类表示一条指令，用于标记规则组的开始
  // 规则组允许将多个规则组合在一起，作为一个整体进行优化，提高优化效率
  static class BeginGroup extends HepInstruction {  // 继承自HepInstruction，表示开始规则组的指令
    final EndGroup endGroup;  // 成员变量：存储对应的结束组指令对象，用于建立开始和结束之间的关联，final表示不可变

    BeginGroup(EndGroup endGroup) {  // 构造方法，接收对应的结束组指令
      this.endGroup = requireNonNull(endGroup, "endGroup");  // 通过requireNonNull检查参数不为null，否则抛出NullPointerException
    }

    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link BeginGroup} instruction. */
    // BeginGroup指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示BeginGroup指令的执行状态
      final HepInstruction.EndGroup.State endGroup;  // 成员变量：存储结束组的状态对象，final表示不可变

      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
        this.endGroup = requireNonNull(px.endGroupState, "endGroupState");  // 从准备上下文中获取结束组的状态，并检查不为null
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeBeginGroup(BeginGroup.this, this);  // 调用规划器的executeBeginGroup方法，传入BeginGroup指令和当前状态
      }
    }
  }

  /** Placeholder instruction that marks the beginning of a group under
   * construction. */
  // 占位符指令，标记正在构造的规则组的开始
  // 这是一个临时占位符，用于在构建规则组时标记开始位置
  static class Placeholder extends HepInstruction {  // 继承自HepInstruction，表示占位符指令
    @Override HepState prepare(PrepareContext px) {  // 重写父类的prepare方法
      throw new UnsupportedOperationException();  // 抛出不支持操作异常，因为占位符不应该被实际执行
    }
  }

  /** Instruction that ends a group. */
  // 指令：结束一个规则组
  // 这个内部类表示一条指令，用于标记规则组的结束
  // 与BeginGroup配对使用，定义规则组的边界
  static class EndGroup extends HepInstruction {  // 继承自HepInstruction，表示结束规则组的指令
    @Override State prepare(PrepareContext px) {  // 重写父类的prepare方法，用于创建执行状态
      return new State(px);  // 创建并返回一个新的State对象，传入准备上下文
    }

    /** State for a {@link EndGroup} instruction. */
    // EndGroup指令的状态类，负责管理该指令的执行状态
    class State extends HepState {  // 继承自HepState，表示EndGroup指令的执行状态
      /** Actual rule set instantiated during planning by collecting grouped
       * rules. */
      // 实际的规则集合，在规划期间通过收集分组规则来实例化
      final Set<RelOptRule> ruleSet = new HashSet<>();  // 成员变量：存储规则组中的所有规则，使用HashSet存储，final表示引用不可变

      boolean collecting = true;  // 成员变量：标志位，表示是否正在收集规则，初始值为true

      State(PrepareContext px) {  // 构造方法，接收准备上下文
        super(px);  // 调用父类HepState的构造方法，初始化状态
      }

      @Override void execute() {  // 重写父类的execute方法，执行该指令
        planner.executeEndGroup(EndGroup.this, this);  // 调用规划器的executeEndGroup方法，传入EndGroup指令和当前状态
      }

      @Override void init() {  // 重写父类的init方法，初始化状态
        collecting = true;  // 将收集标志设置为true，表示开始收集规则
      }
    }
  }

  /** All the information that might be necessary to initialize {@link HepState}
   * for a particular instruction. */
  // 初始化特定指令的HepState所需的所有信息
  // 这是一个上下文对象，封装了初始化指令状态所需的所有必要信息
  static class PrepareContext {  // 准备上下文类，用于在准备阶段传递必要的信息
    final HepPlanner planner;  // 成员变量：HepPlanner规划器对象，final表示不可变
    final HepProgram.State programState;  // 成员变量：HepProgram的程序状态对象，final表示不可变
    final EndGroup.State endGroupState;  // 成员变量：结束组的状态对象，final表示不可变

    private PrepareContext(HepPlanner planner,  // 私有构造方法，接收规划器、程序状态和结束组状态
        HepProgram.State programState, EndGroup.State endGroupState) {
      this.planner = planner;  // 设置规划器
      this.programState = programState;  // 设置程序状态
      this.endGroupState = endGroupState;  // 设置结束组状态
    }

    static PrepareContext create(HepPlanner planner) {  // 静态工厂方法，创建准备上下文
      return new PrepareContext(planner, castNonNull(null), castNonNull(null));  // 创建新的准备上下文，程序状态和结束组状态都设为null
    }

    PrepareContext withProgramState(HepProgram.State programState) {  // 实例方法，设置程序状态并返回新的上下文对象
      return new PrepareContext(planner, programState, endGroupState);  // 创建新的准备上下文，更新程序状态
    }

    PrepareContext withEndGroupState(EndGroup.State endGroupState) {  // 实例方法，设置结束组状态并返回新的上下文对象
      return new PrepareContext(planner, programState, endGroupState);  // 创建新的准备上下文，更新结束组状态
    }
  }
}
