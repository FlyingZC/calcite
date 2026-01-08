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
 */// Apache许可证声明，规定代码的使用权限和限制
package org.apache.calcite.plan.hep;// HepProgram类所在的包，Hep代表HepPlanner（启发式规划器），这是Calcite中的一种基于启发式规则的优化器

import com.google.common.collect.ImmutableList;// 导入Google Guava库的不可变列表类，用于存储指令列表，确保线程安全

import org.checkerframework.checker.nullness.qual.Nullable;// 导入空值检查框架的注解，用于标记可能为null的字段

import java.util.ArrayList;// 导入Java标准库的动态数组类，用于构建指令状态列表
import java.util.HashMap;// 导入Java标准库的哈希映射类，用于存储指令和对应动作的映射关系
import java.util.List;// 导入Java标准库的列表接口
import java.util.Map;// 导入Java标准库的映射接口
import java.util.function.Consumer;// 导入Java函数式接口，表示接受单个参数且无返回值的操作

import static org.apache.calcite.linq4j.Nullness.castNonNull;// 导入静态方法，用于将可能为null的值转换为非null值
import static org.apache.calcite.linq4j.Nullness.castToInitialized;// 导入静态方法，用于将值转换为已初始化的非null值

/**
 * HepProgram指定了HepPlanner（启发式规划器）尝试应用规则的顺序
 * HepPlanner是Calcite中的一种基于启发式规则的查询优化器，它按照预定义的程序顺序应用转换规则
 * 使用HepProgramBuilder来创建HepProgram的新实例，这是构建优化程序的推荐方式
 *
 * <p>注意：程序的结构是不可变的（immutable），但规划器在规划过程中会将其作为读写对象使用
 * 因此一个程序在同一时间只能被一个规划器使用，不能并发共享
 * 这种设计确保了规划过程的线程安全性和一致性
 *
 * <p>HepProgram继承自HepInstruction，这意味着它本身也可以作为一个指令嵌套到其他程序中
 * 这种递归结构允许构建复杂的优化策略，包括嵌套的规则组、重复应用等
 */
public class HepProgram extends HepInstruction {// HepProgram类定义，继承自HepInstruction基类，表示一个完整的优化程序
  //~ Static fields/initializers ---------------------------------------------// 静态字段和初始化块的分隔标记

  /**
   * 符号常量，表示匹配直到达到不动点（fixpoint），即不再有更多的匹配发生
   * 不动点是指应用规则后不再产生新的变化，达到稳定状态
   * 使用Integer.MAX_VALUE作为特殊值，表示无限次匹配直到收敛
   * 这是优化器中常见的概念，确保规则被应用直到无法再优化为止
   */
  public static final int MATCH_UNTIL_FIXPOINT = Integer.MAX_VALUE;// 定义静态常量，表示匹配直到不动点

  //~ Instance fields --------------------------------------------------------// 实例字段的分隔标记

  final ImmutableList<HepInstruction> instructions;// 存储程序中的所有指令列表，使用不可变列表确保线程安全，指令按执行顺序排列

  //~ Constructors -----------------------------------------------------------// 构造方法的分隔标记

  /**
   * 创建一个新的HepProgram实例
   * 程序具有初始的匹配顺序：深度优先（DEPTH_FIRST）
   * 程序具有初始的匹配限制：MATCH_UNTIL_FIXPOINT（直到不动点）
   * 构造函数是包私有的，只能通过HepProgramBuilder创建实例，这是建造者模式的应用
   *
   * @param instructions 指令列表，包含程序中要执行的所有优化指令（规则、规则组、匹配顺序设置等）
   */
  HepProgram(List<HepInstruction> instructions) {// 构造函数，接受指令列表作为参数
    this.instructions = ImmutableList.copyOf(instructions);// 使用不可变列表的copyOf方法创建指令列表的不可变副本，确保外部修改不会影响程序内部状态
  }

  /**
   * 创建并返回一个新的HepProgramBuilder实例
   * HepProgramBuilder是建造者模式的实现，用于逐步构建复杂的HepProgram
   * 提供了流畅的API（fluent API），支持链式调用，使代码更易读
   * 这是创建HepProgram的推荐方式，因为它提供了类型安全和编译时检查
   *
   * @return 新的HepProgramBuilder实例，可用于构建优化程序
   */
  public static HepProgramBuilder builder() {// 静态工厂方法，返回建造者实例
    return new HepProgramBuilder();// 创建并返回新的HepProgramBuilder对象
  }

  //~ Methods ----------------------------------------------------------------// 方法的分隔标记

  /**
   * 准备执行HepProgram，创建并返回程序的状态对象
   * 状态对象包含了程序执行过程中需要的所有运行时信息
   * 这个方法在优化开始时被调用，用于初始化程序的执行环境
   *
   * @param px 准备上下文（PrepareContext），包含了规划器、程序状态等信息
   * @return 新创建的State对象，表示HepProgram的执行状态
   */
  @Override State prepare(PrepareContext px) {// 重写父类HepInstruction的prepare方法
    return new State(px, instructions);// 创建并返回新的State对象，传入准备上下文和指令列表
  }

  /** State for a {@link HepProgram} instruction. */
  // HepProgram指令的状态类，管理程序执行过程中的运行时状态
  // State类继承自HepState基类，表示一个可执行的优化指令状态
  // 这个类是内部类，可以访问外部HepProgram的私有成员
  // State对象包含了程序执行所需的所有动态信息，包括匹配限制、匹配顺序、规则组状态等
  class State extends HepState {// 内部类State定义，继承自HepState基类
    final ImmutableList<HepState> instructionStates;// 存储所有指令的状态列表，每个指令对应一个HepState对象，按执行顺序排列，使用不可变列表确保线程安全
      int matchLimit = MATCH_UNTIL_FIXPOINT;// 匹配限制，表示规则最多被应用的次数，默认为MATCH_UNTIL_FIXPOINT（直到不动点）
      HepMatchOrder matchOrder = HepMatchOrder.DEPTH_FIRST;// 匹配顺序，决定规则应用的顺序策略，默认为深度优先（DEPTH_FIRST）
      HepInstruction.EndGroup.@Nullable State group;// 当前活动的规则组状态，@Nullable表示可能为null，null表示不在任何规则组中
    State(PrepareContext px, List<HepInstruction> instructions) {// State构造函数，接受准备上下文和指令列表作为参数
      super(px);// 调用父类HepState的构造函数，初始化基础状态
      final PrepareContext px2 = px.withProgramState(castToInitialized(this));// 创建新的准备上下文，将当前程序状态注入到上下文中，castToInitialized确保this不为null
      final List<HepState> states = new ArrayList<>();// 创建动态数组，用于临时存储指令状态，因为需要处理前向引用
      final Map<HepInstruction, Consumer<HepState>> actions = new HashMap<>();// 创建哈希映射，用于存储指令和对应动作的映射关系，用于处理BeginGroup和EndGroup的配对
      for (HepInstruction instruction : instructions) {// 遍历指令列表中的每个指令
        final HepState state;// 声明变量用于存储当前指令的状态
        if (instruction instanceof BeginGroup) {// 如果当前指令是BeginGroup（规则组开始指令）
          // BeginGroup指令的状态需要对应的EndGroup指令的状态，但我们还没有看到EndGroup
          // 这是一个前向引用问题，因为BeginGroup和EndGroup是成对出现的，但EndGroup在后面
          // 解决方案：先在列表中放置一个占位符State，并添加一个动作来替换这个State
          // 当我们到达EndGroup时，会调用这个动作，用正确的状态替换占位符
          final int i = states.size();// 记录当前列表大小，这是BeginGroup状态将要放置的位置
          actions.put(((BeginGroup) instruction).endGroup, state2 ->// 将EndGroup指令作为key，创建一个Consumer动作作为value存入actions映射
              states.set(i,// 当动作被调用时，将占位符替换为真正的BeginGroup状态
                  instruction.prepare(// 调用BeginGroup的prepare方法创建状态
                      px2.withEndGroupState((EndGroup.State) state2))));// 使用包含EndGroup状态的上下文，确保BeginGroup可以访问EndGroup的状态
          state = castNonNull(null);// 创建null占位符，castNonNull在这里是形式上的，实际值是null
        } else {// 如果当前指令不是BeginGroup
          state = instruction.prepare(px2);// 直接调用指令的prepare方法创建状态
          if (actions.containsKey(instruction)) {// 如果当前指令在actions映射中有对应的动作（说明它是某个BeginGroup的EndGroup）
            actions.get(instruction).accept(state);// 调用对应的动作，将状态传递给之前注册的Consumer，完成前向引用的解析
          }
        }
        states.add(state);// 将状态添加到临时列表中
      }
      this.instructionStates = ImmutableList.copyOf(states);// 将临时列表转换为不可变列表，赋值给final字段instructionStates
    }

    @Override void init() {// 重写父类的init方法，初始化状态
      matchLimit = MATCH_UNTIL_FIXPOINT;// 重置匹配限制为默认值（直到不动点）
      matchOrder = HepMatchOrder.DEPTH_FIRST;// 重置匹配顺序为默认值（深度优先）
      group = null;// 重置规则组状态为null，表示不在任何规则组中
    }

    /**
     * 执行HepProgram，触发优化过程
     * 这个方法被HepPlanner调用，用于执行程序中定义的所有指令
     * 执行过程会按照指令列表的顺序依次执行每个指令
     */
    @Override void execute() {// 重写父类的execute方法
      planner.executeProgram(HepProgram.this, this);// 调用规划器的executeProgram方法，传入当前程序和状态对象，开始执行优化
    }

    /**
     * 判断当前是否应该跳过规则组的收集阶段
     * 这个方法用于优化规则组的处理，避免重复收集规则
     *
     * @return true表示应该跳过规则组的收集，false表示不跳过
     */
    boolean skippingGroup() {// 判断是否跳过规则组的方法
      if (group != null) {// 如果当前在一个规则组中（group不为null）
        // 如果已经收集了规则集（group.collecting为false），则跳过
        return !group.collecting;// 返回是否不再收集规则，true表示跳过，false表示继续收集
      } else {// 如果当前不在任何规则组中
        // 不在分组中，不跳过
        return false;// 返回false，表示不跳过
      }
    }
  }// State内部类的结束大括号
}// HepProgram类的结束大括号
