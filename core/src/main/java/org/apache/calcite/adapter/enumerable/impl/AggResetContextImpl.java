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
package org.apache.calcite.adapter.enumerable.impl;  // 定义包路径，属于Calcite框架的枚举适配器模块中impl包，存放实现类

import org.apache.calcite.adapter.enumerable.AggResetContext;  // 导入聚合重置上下文接口，用于定义聚合操作重置阶段的上下文
import org.apache.calcite.adapter.enumerable.NestedBlockBuilderImpl;  // 导入嵌套代码块构建器基类，用于构建嵌套的Java代码块
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入代码块构建器，用于构建Java表达式和语句块
import org.apache.calcite.linq4j.tree.Expression;  // 导入表达式类，表示Java表达式（如变量引用、方法调用等）
import org.apache.calcite.rel.core.AggregateCall;  // 导入聚合调用类，表示Calcite中的聚合函数调用（如SUM、COUNT等）

import java.util.List;  // 导入List接口，用于存储表达式列表

/**
 * Implementation of
 * {@link org.apache.calcite.adapter.enumerable.AggResetContext}.
 * AggResetContextImpl是聚合重置上下文的实现类，用于在聚合操作执行过程中重置累加器状态
 * 
 * 【类的作用详解】：
 * 1. 聚合操作的生命周期包括三个阶段：Reset（重置）、Add（添加）、Result（结果）
 * 2. Reset阶段：在开始新的聚合计算前，需要将累加器初始化或重置为初始状态
 * 3. 这个类提供了Reset阶段的上下文环境，允许开发者添加重置累加器的代码
 * 4. 继承自NestedBlockBuilderImpl，可以构建嵌套的代码块结构
 * 5. 实现AggResetContext接口，提供聚合重置所需的标准方法
 * 
 * 【使用场景】：
 * - 在生成可枚举聚合代码时，需要在每个分组开始前重置累加器
 * - 例如：SUM聚合需要将累加器重置为0，COUNT需要重置为0，AVG需要重置sum和count两个累加器
 * - 这个类为不同聚合函数提供了统一的接口来生成重置代码
 */
public abstract class AggResetContextImpl extends NestedBlockBuilderImpl  // 定义抽象类，继承嵌套代码块构建器，可以构建代码块
    implements AggResetContext {  // 实现聚合重置上下文接口，提供聚合重置的标准方法
  private final List<Expression> accumulator;  // 成员变量：累加器表达式列表，存储所有累加器变量的表达式引用

  /**
   * Creates aggregate reset context.
   * 创建聚合重置上下文的构造方法
   *
   * @param block Code block that will contain the added initialization  // 参数：代码块构建器，用于包含添加的初始化代码
   * @param accumulator Accumulator variables that store the intermediate  // 参数：累加器变量列表，存储聚合计算的中间状态
   *                    aggregate state  // 累加器用于在聚合过程中累积中间结果，如SUM累加器存储累加和
   */
  protected AggResetContextImpl(BlockBuilder block, List<Expression> accumulator) {  // 受保护的构造方法，只能由子类调用
    super(block);  // 调用父类NestedBlockBuilderImpl的构造方法，传入代码块构建器，初始化代码块构建环境
    this.accumulator = accumulator;  // 将传入的累加器表达式列表赋值给成员变量，保存累加器引用
  }

  @Override public List<Expression> accumulator() {  // 重写接口方法，获取累加器表达式列表
    return accumulator;  // 返回累加器表达式列表，供调用者访问和操作累加器变量
  }

  public AggregateCall call() {  // 获取聚合调用信息的方法（默认实现）
    throw new UnsupportedOperationException();  // 抛出不支持操作异常，表示此方法在基类中不支持，需要子类根据需要重写
  }
}
