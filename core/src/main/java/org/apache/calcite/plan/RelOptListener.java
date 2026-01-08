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
package org.apache.calcite.plan; // 声明包名，表示该类属于org.apache.calcite.plan包，这是Calcite优化器相关的核心包

import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式节点，是Calcite中关系代数的基本构建块

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或返回值

import java.util.EventListener; // 导入EventListener接口，这是Java标准库中的事件监听器接口，用于处理事件

import java.util.EventObject; // 导入EventObject类，这是Java标准库中的事件对象基类，所有事件对象都继承自它

/**
 * RelOptListener defines an interface for listening to events which occur
 * during the optimization process.
 * RelOptListener定义了一个接口，用于监听在查询优化过程中发生的各种事件
 * 
 * 这个接口是Calcite优化器观察者模式的核心，允许外部监听器跟踪优化器的内部活动
 * 通过实现这个接口，可以监控优化器的决策过程，包括规则的尝试、关系的等价性发现、关系的丢弃和选择等
 * 这对于调试、性能分析、优化器行为理解和可视化都非常有用
 * 
 * 主要监听的事件类型包括：
 * 1. 关系表达式的等价性发现（relEquivalenceFound）
 * 2. 优化器规则的尝试（ruleAttempted）
 * 3. 优化器规则成功产生新表达式（ruleProductionSucceeded）
 * 4. 关系表达式的丢弃（relDiscarded）
 * 5. 关系表达式被选入最终计划（relChosen）
 */
public interface RelOptListener extends EventListener { // 定义RelOptListener接口，继承自EventListener，使其成为标准的事件监听器
  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔符，用于组织代码结构

  /**
   * Notifies this listener that a relational expression has been registered
   * with a particular equivalence class after an equivalence has been either
   * detected or asserted. Equivalence classes may be either logical (all
   * expressions which yield the same result set) or physical (all expressions
   * which yield the same result set with a particular calling convention).
   * 通知监听器一个关系表达式已经被注册到特定的等价类中，这是在等价性被检测或断言之后
   * 等价类可以是逻辑的（所有产生相同结果集的表达式）或物理的（所有以特定调用约定产生相同结果集的表达式）
   *
   * @param event details about the event 事件详情，包含等价性发现的具体信息
   * 
   * 这个方法在优化器发现两个关系表达式等价时被调用
   * 等价性是Calcite优化器的核心概念，它允许优化器在多个等价的实现之间选择最优的
   * 逻辑等价：两个表达式产生相同的逻辑结果，但实现方式可能不同
   * 物理等价：两个表达式不仅产生相同结果，而且使用相同的物理实现约定（如使用相同的存储格式）
   */
  void relEquivalenceFound(RelEquivalenceEvent event); // 声明方法，当发现关系表达式等价时调用，参数是RelEquivalenceEvent事件对象

  /**
   * Notifies this listener that an optimizer rule is being applied to a
   * particular relational expression. This rule is called twice; once before
   * the rule is invoked, and once after. Note that the rel attribute of the
   * event is always the old expression.
   * 通知监听器一个优化器规则正在被应用到特定的关系表达式上
   * 这个方法会被调用两次：一次在规则调用之前，一次在规则调用之后
   * 注意：事件中的rel属性总是旧的表达式（规则应用前的表达式）
   *
   * @param event details about the event 事件详情，包含规则尝试的具体信息
   * 
   * 这个方法允许监听器跟踪每个优化规则的执行过程
   * 通过before标志可以区分是规则执行前还是执行后
   * 这对于理解优化器的决策过程和规则的触发顺序非常有用
   * 如果规则执行失败或没有产生新的表达式，这个方法仍然会被调用两次
   */
  void ruleAttempted(RuleAttemptedEvent event); // 声明方法，当优化器规则被尝试应用时调用，参数是RuleAttemptedEvent事件对象

  /**
   * Notifies this listener that an optimizer rule has been successfully
   * applied to a particular relational expression, resulting in a new
   * equivalent expression (relEquivalenceFound will also be called unless the
   * new expression is identical to an existing one). This rule is called
   * twice; once before registration of the new rel, and once after. Note that
   * the rel attribute of the event is always the new expression; to get the
   * old expression, use event.getRuleCall().rels[0].
   * 通知监听器一个优化器规则已经成功应用到特定的关系表达式上，产生了一个新的等价表达式
   * （relEquivalenceFound也会被调用，除非新表达式与现有表达式完全相同）
   * 这个方法会被调用两次：一次在注册新的关系表达式之前，一次在注册之后
   * 注意：事件中的rel属性总是新的表达式；要获取旧表达式，使用event.getRuleCall().rels[0]
   *
   * @param event details about the event 事件详情，包含规则成功产生的具体信息
   * 
   * 这个方法只在规则成功产生新的关系表达式时才被调用
   * 它与ruleAttempted的区别在于：ruleAttempted会在规则尝试时调用（无论成功与否），而这个方法只在成功时调用
   * 通过before标志可以区分是在注册新表达式之前还是之后
   * 这个事件通常紧随relEquivalenceFound事件，因为新产生的表达式会被注册到等价类中
   */
  void ruleProductionSucceeded(RuleProductionEvent event); // 声明方法，当优化器规则成功产生新表达式时调用，参数是RuleProductionEvent事件对象

  /**
   * Notifies this listener that a relational expression is no longer of
   * interest to the planner.
   * 通知监听器一个关系表达式不再被规划器关注
   *
   * @param event details about the event 事件详情，包含关系表达式被丢弃的具体信息
   * 
   * 这个方法在优化器决定放弃某个关系表达式时被调用
   * 丢弃的原因可能包括：
   * 1. 该表达式的成本太高，不可能被选入最终计划
   * 2. 该表达式已经被更好的等价表达式替代
   * 3. 该表达式违反了某些约束条件
   * 4. 优化器已经找到了足够好的解决方案，不需要继续探索这个表达式
   * 
   * 丢弃是优化器剪枝策略的一部分，用于减少搜索空间，提高优化效率
   */
  void relDiscarded(RelDiscardedEvent event); // 声明方法，当关系表达式被丢弃时调用，参数是RelDiscardedEvent事件对象

  /**
   * Notifies this listener that a relational expression has been chosen as
   * part of the final implementation of the query plan. After the plan is
   * complete, this is called one more time with null for the rel.
   * 通知监听器一个关系表达式已经被选为查询计划最终实现的一部分
   * 在计划完成后，这个方法会再被调用一次，此时rel参数为null
   *
   * @param event details about the event 事件详情，包含关系表达式被选中的具体信息
   * 
   * 这个方法在优化器完成优化，确定最终执行计划时被调用
   * 被选中的表达式将出现在最终的执行计划中
   * 最后一次调用时rel为null，表示优化过程完全结束
   * 
   * 这个事件对于理解优化器的最终决策和生成执行计划非常重要
   * 可以通过这个事件收集最终的执行计划信息
   */
  void relChosen(RelChosenEvent event); // 声明方法，当关系表达式被选入最终计划时调用，参数是RelChosenEvent事件对象

  //~ Inner Classes ---------------------------------------------------------- // 内部类区域的分隔符，用于组织代码结构

  /**
   * Event class for abstract event dealing with a relational expression. The
   * source of an event is typically the RelOptPlanner which initiated it.
   * 处理关系表达式的抽象事件类
   * 事件的源通常是发起它的RelOptPlanner（关系优化规划器）
   * 
   * 这是所有涉及关系表达式的事件的基类
   * 它继承自Java标准库的EventObject，遵循标准的事件处理模式
   * 它封装了关系表达式（RelNode）和事件源（通常是RelOptPlanner）
   * 
   * 设计目的：
   * 1. 为所有关系表达式相关的事件提供统一的基类
   * 2. 封装事件源和关系表达式，避免重复代码
   * 3. 提供获取关系表达式的统一接口
   */
  abstract class RelEvent extends EventObject { // 定义抽象类RelEvent，继承自EventObject，作为所有关系表达式事件的基类
    private final @Nullable RelNode rel; // 声明私有常量rel，类型为RelNode（可为null），表示事件涉及的关系表达式节点

    protected RelEvent(Object eventSource, @Nullable RelNode rel) { // 定义受保护的构造方法，参数：事件源对象和关系表达式（可为null）
      super(eventSource); // 调用父类EventObject的构造方法，传入事件源，EventObject会保存这个引用
      this.rel = rel; // 将传入的关系表达式赋值给成员变量rel，使用this关键字区分成员变量和参数
    } // 构造方法结束

    public @Nullable RelNode getRel() { // 定义公共方法getRel，返回类型为RelNode（可为null），用于获取事件涉及的关系表达式
      return rel; // 返回成员变量rel，即事件涉及的关系表达式节点
    } // 方法结束
  } // RelEvent类结束

  /** Event indicating that a relational expression has been chosen.
   *  事件，表示一个关系表达式已经被选中
   *  
   *  这个事件在优化器决定将某个关系表达式包含在最终执行计划中时触发
   *  它是RelEvent的简单子类，没有添加额外的字段
   *  通过这个事件可以跟踪哪些表达式最终被选入执行计划
   */
  class RelChosenEvent extends RelEvent { // 定义类RelChosenEvent，继承自RelEvent，表示关系表达式被选中的事件
    public RelChosenEvent(Object eventSource, @Nullable RelNode rel) { // 定义公共构造方法，参数：事件源对象和关系表达式（可为null）
      super(eventSource, rel); // 调用父类RelEvent的构造方法，传入事件源和关系表达式
    } // 构造方法结束
  } // RelChosenEvent类结束

  /** Event indicating that a relational expression has been found to
   *  be equivalent to an equivalence class.
   *  事件，表示一个关系表达式被发现与某个等价类等价
   *  
   *  这个事件在优化器发现两个关系表达式等价时触发
   *  它扩展了RelEvent，添加了等价类和是否为物理等价的标志
   *  等价类是Calcite优化器的核心概念，用于组织和管理等价的表达式
   *  
   *  等价类的作用：
   *  1. 将等价的表达式分组，便于管理和优化
   *  2. 支持成本比较，在等价的表达式中选择最优的
   *  3. 支持规则匹配，规则可以在等价类中寻找匹配的表达式
   */
  class RelEquivalenceEvent extends RelEvent { // 定义类RelEquivalenceEvent，继承自RelEvent，表示发现等价关系的事件
    private final Object equivalenceClass; // 声明私有常量equivalenceClass，类型为Object，表示等价类对象，等价类用于组织等价的表达式
    private final boolean isPhysical; // 声明私有常量isPhysical，类型为boolean，表示是否为物理等价（true表示物理等价，false表示逻辑等价）

    public RelEquivalenceEvent( // 定义公共构造方法
        Object eventSource, // 参数1：事件源对象，通常是RelOptPlanner
        RelNode rel, // 参数2：关系表达式节点，表示被发现等价的表达式
        Object equivalenceClass, // 参数3：等价类对象，表示该表达式被注册到哪个等价类
        boolean isPhysical) { // 参数4：是否为物理等价的标志
      super(eventSource, rel); // 调用父类RelEvent的构造方法，传入事件源和关系表达式
      this.equivalenceClass = equivalenceClass; // 将传入的等价类对象赋值给成员变量equivalenceClass
      this.isPhysical = isPhysical; // 将传入的物理等价标志赋值给成员变量isPhysical
    } // 构造方法结束

    public Object getEquivalenceClass() { // 定义公共方法getEquivalenceClass，返回类型为Object，用于获取等价类对象
      return equivalenceClass; // 返回成员变量equivalenceClass，即等价类对象
    } // 方法结束

    public boolean isPhysical() { // 定义公共方法isPhysical，返回类型为boolean，用于判断是否为物理等价
      return isPhysical; // 返回成员变量isPhysical，表示是否为物理等价
    } // 方法结束
  } // RelEquivalenceEvent类结束

  /** Event indicating that a relational expression has been discarded.
   *  事件，表示一个关系表达式已经被丢弃
   *  
   *  这个事件在优化器决定放弃某个关系表达式时触发
   *  它是RelEvent的简单子类，没有添加额外的字段
   *  通过这个事件可以跟踪哪些表达式在优化过程中被丢弃
   */
  class RelDiscardedEvent extends RelEvent { // 定义类RelDiscardedEvent，继承自RelEvent，表示关系表达式被丢弃的事件
    public RelDiscardedEvent(Object eventSource, RelNode rel) { // 定义公共构造方法，参数：事件源对象和关系表达式
      super(eventSource, rel); // 调用父类RelEvent的构造方法，传入事件源和关系表达式
    } // 构造方法结束
  } // RelDiscardedEvent类结束

  /** Event indicating that a planner rule has fired.
   *  事件，表示一个规划器规则已经被触发
   *  
   *  这个事件在优化器规则被触发时使用，是所有规则相关事件的基类
   *  它扩展了RelEvent，添加了规则调用（RelOptRuleCall）的信息
   *  RelOptRuleCall包含了规则被触发时的上下文信息，包括规则本身、匹配的关系表达式等
   *  
   *  规则触发的含义：
   *  1. 优化器发现某个关系表达式匹配某个规则的匹配模式
   *  2. 规则被调用，尝试对匹配的表达式进行转换
   *  3. 规则可能成功产生新的表达式，也可能失败
   */
  abstract class RuleEvent extends RelEvent { // 定义抽象类RuleEvent，继承自RelEvent，作为所有规则相关事件的基类
    private final RelOptRuleCall ruleCall; // 声明私有常量ruleCall，类型为RelOptRuleCall，表示规则调用的详细信息

    protected RuleEvent( // 定义受保护的构造方法
        Object eventSource, // 参数1：事件源对象，通常是RelOptPlanner
        RelNode rel, // 参数2：关系表达式节点，表示规则应用的目标表达式
        RelOptRuleCall ruleCall) { // 参数3：规则调用对象，包含规则和匹配的详细信息
      super(eventSource, rel); // 调用父类RelEvent的构造方法，传入事件源和关系表达式
      this.ruleCall = ruleCall; // 将传入的规则调用对象赋值给成员变量ruleCall
    } // 构造方法结束

    public RelOptRuleCall getRuleCall() { // 定义公共方法getRuleCall，返回类型为RelOptRuleCall，用于获取规则调用对象
      return ruleCall; // 返回成员变量ruleCall，即规则调用对象
    } // 方法结束
  } // RuleEvent类结束

  /** Event indicating that a planner rule has been attempted.
   *  事件，表示一个规划器规则已经被尝试
   *  
   *  这个事件在优化器尝试应用规则时触发
   *  它扩展了RuleEvent，添加了before标志，用于区分是在规则执行前还是执行后
   *  每次规则尝试都会触发两次这个事件：一次在规则执行前（before=true），一次在规则执行后（before=false）
   *  
   *  这个事件允许监听器跟踪规则的执行过程，包括：
   *  1. 规则何时被触发
   *  2. 规则执行前后的状态
   *  3. 规则是否成功（通过比较before和after事件）
   */
  class RuleAttemptedEvent extends RuleEvent { // 定义类RuleAttemptedEvent，继承自RuleEvent，表示规则被尝试的事件
    private final boolean before; // 声明私有常量before，类型为boolean，表示是在规则执行前（true）还是执行后（false）

    public RuleAttemptedEvent( // 定义公共构造方法
        Object eventSource, // 参数1：事件源对象，通常是RelOptPlanner
        RelNode rel, // 参数2：关系表达式节点，表示规则应用的目标表达式（总是旧表达式）
        RelOptRuleCall ruleCall, // 参数3：规则调用对象，包含规则和匹配的详细信息
        boolean before) { // 参数4：before标志，true表示规则执行前，false表示规则执行后
      super(eventSource, rel, ruleCall); // 调用父类RuleEvent的构造方法，传入事件源、关系表达式和规则调用对象
      this.before = before; // 将传入的before标志赋值给成员变量before
    } // 构造方法结束

    public boolean isBefore() { // 定义公共方法isBefore，返回类型为boolean，用于判断是否在规则执行前
      return before; // 返回成员变量before，表示是否在规则执行前
    } // 方法结束
  } // RuleAttemptedEvent类结束

  /** Event indicating that a planner rule has produced a result.
   *  事件，表示一个规划器规则已经产生结果
   *  
   *  这个事件在优化器规则成功产生新的关系表达式时触发
   *  它继承自RuleAttemptedEvent，因此也包含before标志
   *  与RuleAttemptedEvent的区别在于：这个事件只在规则成功产生新表达式时才触发
   *  
   *  这个事件也会被触发两次：一次在注册新表达式之前（before=true），一次在注册之后（before=false）
   *  通过这个事件可以跟踪规则成功产生的表达式
   */
  class RuleProductionEvent extends RuleAttemptedEvent { // 定义类RuleProductionEvent，继承自RuleAttemptedEvent，表示规则成功产生结果的事件
    public RuleProductionEvent( // 定义公共构造方法
        Object eventSource, // 参数1：事件源对象，通常是RelOptPlanner
        RelNode rel, // 参数2：关系表达式节点，表示规则产生的新表达式（注意：这里rel是新表达式，与RuleAttemptedEvent不同）
        RelOptRuleCall ruleCall, // 参数3：规则调用对象，包含规则和匹配的详细信息
        boolean before) { // 参数4：before标志，true表示注册新表达式前，false表示注册新表达式后
      super(eventSource, rel, ruleCall, before); // 调用父类RuleAttemptedEvent的构造方法，传入所有参数
    } // 构造方法结束
  } // RuleProductionEvent类结束
} // RelOptListener接口结束
