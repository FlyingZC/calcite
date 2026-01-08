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
package org.apache.calcite.plan;  // 声明该类所在的包，位于org.apache.calcite.plan包下，这是Calcite框架中负责查询优化和规则管理的核心包

/**
 * A <code>CommonRelSubExprRule</code> is an abstract base class for rules
 * that are fired only on relational expressions that appear more than once
 * in a query tree.
 */
// CommonRelSubExprRule是一个抽象基类，用于定义那些仅在查询树中出现多次的关系表达式上触发的规则
// 这种规则专门用于处理公共子表达式，即相同的子查询或关系表达式在查询中多次出现的情况
// 通过识别和优化这些重复的子表达式，可以避免重复计算，提高查询性能
// 这是一个优化规则的基类，子类可以继承它来实现具体的公共子表达式优化逻辑

// TODO: obsolete this?  // 这是一个待办事项标记，表示可能需要考虑废弃这个类，可能是因为有更好的替代方案或者这个类已经不再使用
public abstract class CommonRelSubExprRule  // 定义一个公共的抽象类CommonRelSubExprRule，抽象类意味着不能直接实例化，需要子类继承
    extends RelRule<CommonRelSubExprRule.Config> {  // 继承自RelRule基类，并指定泛型参数为CommonRelSubExprRule.Config接口
  // RelRule是Calcite中所有优化规则的基类，提供了规则的基本框架和配置机制
  // 通过继承RelRule，这个类自动获得了规则匹配、转换等基础功能
  // 泛型参数Config指定了该规则的配置类型，这里是CommonRelSubExprRule.Config接口
  //~ Constructors -----------------------------------------------------------  // 分隔符，标记构造方法部分的开始，用于代码组织和可读性

  /** Creates a CommonRelSubExprRule. */
  // 受保护的构造方法，用于创建CommonRelSubExprRule实例
  // 受保护意味着只有子类可以调用这个构造方法，外部无法直接实例化这个抽象类
  // 参数Config config是该规则的配置对象，包含了规则运行所需的各种配置信息
  protected CommonRelSubExprRule(Config config) {  // 构造方法签名，接收一个Config类型的参数
    super(config);  // 调用父类RelRule的构造方法，将配置对象传递给父类进行初始化
    // 这是标准的Java继承模式，子类构造方法必须调用父类构造方法
    // 通过super(config)，父类RelRule会根据配置对象初始化规则的各种属性和行为
  }  // 构造方法结束

  /** Rule configuration. */
  // 定义一个公共的接口Config，用于配置CommonRelSubExprRule规则
  // 这个接口继承自RelRule.Config，扩展了基类的配置能力
  // 通过配置接口，可以灵活地设置规则的各种参数和行为，而不需要修改规则本身的代码
  // 这是Calcite框架中使用的设计模式之一，将配置与实现分离
  public interface Config extends RelRule.Config {  // 接口定义，继承自RelRule.Config接口
  }  // 接口结束，当前是一个空接口，表示暂时不需要额外的配置项，但保留了扩展性
}  // 类定义结束
