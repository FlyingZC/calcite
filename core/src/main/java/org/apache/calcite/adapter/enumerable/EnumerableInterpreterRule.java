/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权给您
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证URL地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则软件按"原样"分发
 * distributed under the License is distributed on an "AS IS" BASIS, // 不附带任何明示或暗示的保证或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 详见许可证了解具体权限和限制
 * See the License for the specific language governing permissions and // 查看许可证了解权限和限制的具体语言
 * limitations under the License. // 许可证下的限制
 */
package org.apache.calcite.adapter.enumerable; // 包声明：org.apache.calcite.adapter.enumerable，表示此类属于可枚举适配器包

import org.apache.calcite.interpreter.BindableConvention; // 导入BindableConvention类：表示可绑定的调用约定，用于标记可绑定关系节点
import org.apache.calcite.rel.RelNode; // 导入RelNode类：关系表达式的基础接口，表示关系代数中的操作
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类：转换规则基类，用于定义从一个调用约定到另一个调用约定的转换规则

/**
 * Planner rule that converts {@link org.apache.calcite.interpreter.BindableRel} // 规划器规则：将BindableRel（可绑定关系节点）
 * to {@link org.apache.calcite.adapter.enumerable.EnumerableRel} by creating // 转换为EnumerableRel（可枚举关系节点）
 * an {@link org.apache.calcite.adapter.enumerable.EnumerableInterpreter}. // 通过创建一个EnumerableInterpreter（可枚举解释器）来实现
 * // 这个规则的作用是：当优化器遇到BindableConvention的节点时，可以应用此规则将其转换为EnumerableConvention的节点
 * // 这样就可以使用可枚举的方式来执行查询，而不是解释器方式
 *
 * @see EnumerableRules#TO_INTERPRETER // 参见EnumerableRules中的TO_INTERPRETER常量，它包含了此规则的实例
 */
public class EnumerableInterpreterRule extends ConverterRule { // 类定义：EnumerableInterpreterRule继承自ConverterRule，是一个转换规则
  /** Default configuration. */ // 默认配置：定义了规则的默认配置常量
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 创建默认配置：使用Config.INSTANCE作为基础配置
      .withConversion(RelNode.class, BindableConvention.INSTANCE, // 配置转换：从任意RelNode类型，从BindableConvention调用约定
          EnumerableConvention.INSTANCE, "EnumerableInterpreterRule") // 转换为EnumerableConvention调用约定，规则名称为"EnumerableInterpreterRule"
      .withRuleFactory(EnumerableInterpreterRule::new); // 设置规则工厂：使用方法引用创建EnumerableInterpreterRule实例

  protected EnumerableInterpreterRule(Config config) { // 构造方法：受保护的构造方法，接收配置对象
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象进行初始化
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分开始标记

  @Override public RelNode convert(RelNode rel) { // 方法定义：重写父类的convert方法，执行实际的转换逻辑，参数rel是要转换的关系节点
    return EnumerableInterpreter.create(rel, 0.5d); // 返回值：创建并返回一个EnumerableInterpreter节点，传入原始关系节点和0.5d的成本因子
    // EnumerableInterpreter是一个包装节点，它会在运行时使用解释器来执行BindableConvention的节点
    // 0.5d是一个成本因子，用于优化器评估转换的成本，影响优化器的决策
  }
} // 类结束
