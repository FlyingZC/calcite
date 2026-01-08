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
package org.apache.calcite.plan; // 声明该类所属的包，位于org.apache.calcite.plan包下，这是Calcite优化器相关的核心包

import java.util.ArrayList; // 导入ArrayList类，用于创建动态数组来存储多个监听器
import java.util.List; // 导入List接口，用于定义监听器集合的类型

/**
 * MulticastRelOptListener implements the {@link RelOptListener} interface by
 * forwarding events on to a collection of other listeners.
 * MulticastRelOptListener类实现了RelOptListener接口，通过将事件转发给其他监听器的集合来实现多播功能
 * 
 * 【类的作用详解】：
 * 这是一个关系表达式优化器监听器的多播实现类。它充当了一个事件分发器的角色，能够将优化器产生的各种事件
 *（如规则尝试、关系表达式选择、关系表达式丢弃等）同时转发给多个注册的监听器。这种设计模式允许在优化过程中
 *同时维护多个独立的监听器，每个监听器可以执行不同的任务（例如日志记录、性能监控、调试信息收集等），
 *而不会相互干扰。
 * 
 * 【使用场景】：
 * 1. 当需要在优化过程中同时执行多种监听操作时，可以使用这个类来统一管理
 * 2. 便于扩展新的监听功能，只需添加新的监听器即可，无需修改现有代码
 * 3. 适用于需要同时记录优化过程多个方面的场景，如同时记录规则应用情况和物理实现选择情况
 */
public class MulticastRelOptListener implements RelOptListener { // 类定义，实现了RelOptListener接口，表示这是一个可以接收优化器事件的监听器
  //~ Instance fields -------------------------------------------------------- // 实例字段的分隔标记，注释说明下面开始定义实例变量

  private final List<RelOptListener> listeners; // 定义一个私有的final类型的List集合，用于存储所有注册的RelOptListener监听器对象，final表示这个引用一旦初始化就不能再指向其他对象

  //~ Constructors ----------------------------------------------------------- // 构造方法的分隔标记，注释说明下面开始定义构造方法

  /**
   * Creates a new empty multicast listener.
   * 创建一个新的空的多播监听器对象
   */
  public MulticastRelOptListener() { // 无参构造方法，用于创建一个MulticastRelOptListener实例
    listeners = new ArrayList<>(); // 初始化listeners为一个新的空ArrayList实例，准备用来添加监听器
  }

  //~ Methods ---------------------------------------------------------------- // 方法的分隔标记，注释说明下面开始定义方法

  /**
   * Adds a listener which will receive multicast events.
   * 添加一个监听器，该监听器将接收到多播的事件
   *
   * @param listener listener to add // 参数说明：listener表示要添加的监听器对象，它必须是RelOptListener类型或其子类型
   */
  public void addListener(RelOptListener listener) { // 公共方法，用于向多播监听器中添加一个新的监听器
    listeners.add(listener); // 将传入的监听器对象添加到listeners集合中，之后所有事件都会转发给这个监听器
  }

  // implement RelOptListener // 注释说明这是一个实现RelOptListener接口的方法
  @Override public void relEquivalenceFound(RelEquivalenceEvent event) { // 重写接口方法，当发现关系表达式等价时被调用，event参数包含了等价事件的详细信息
    for (RelOptListener listener : listeners) { // 遍历listeners集合中的每一个监听器
      listener.relEquivalenceFound(event); // 调用当前监听器的relEquivalenceFound方法，将等价事件转发给该监听器处理
    }
  }

  // implement RelOptListener // 注释说明这是一个实现RelOptListener接口的方法
  @Override public void ruleAttempted(RuleAttemptedEvent event) { // 重写接口方法，当优化规则被尝试应用时被调用，event参数包含了规则尝试事件的详细信息
    for (RelOptListener listener : listeners) { // 遍历listeners集合中的每一个监听器
      listener.ruleAttempted(event); // 调用当前监听器的ruleAttempted方法，将规则尝试事件转发给该监听器处理
    }
  }

  // implement RelOptListener // 注释说明这是一个实现RelOptListener接口的方法
  @Override public void ruleProductionSucceeded(RuleProductionEvent event) { // 重写接口方法，当优化规则成功产生新的关系表达式时被调用，event参数包含了规则成功产生事件的详细信息
    for (RelOptListener listener : listeners) { // 遍历listeners集合中的每一个监听器
      listener.ruleProductionSucceeded(event); // 调用当前监听器的ruleProductionSucceeded方法，将规则成功产生事件转发给该监听器处理
    }
  }

  // implement RelOptListener // 注释说明这是一个实现RelOptListener接口的方法
  @Override public void relChosen(RelChosenEvent event) { // 重写接口方法，当一个关系表达式被选中作为最终执行计划的一部分时被调用，event参数包含了关系表达式被选中事件的详细信息
    for (RelOptListener listener : listeners) { // 遍历listeners集合中的每一个监听器
      listener.relChosen(event); // 调用当前监听器的relChosen方法，将关系表达式被选中事件转发给该监听器处理
    }
  }

  // implement RelOptListener // 注释说明这是一个实现RelOptListener接口的方法
  @Override public void relDiscarded(RelDiscardedEvent event) { // 重写接口方法，当一个关系表达式被丢弃（未被选中）时被调用，event参数包含了关系表达式被丢弃事件的详细信息
    for (RelOptListener listener : listeners) { // 遍历listeners集合中的每一个监听器
      listener.relDiscarded(event); // 调用当前监听器的relDiscarded方法，将关系表达式被丢弃事件转发给该监听器处理
    }
  }
} // 类定义结束
