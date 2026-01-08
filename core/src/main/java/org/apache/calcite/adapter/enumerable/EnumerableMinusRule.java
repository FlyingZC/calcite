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
// Apache软件基金会许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.enumerable; // 定义包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite的可枚举适配器包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数操作的调用约定（convention），定义了如何执行关系操作
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系节点的特征集合，包含调用约定、排序、分区等特性
import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是所有关系代数节点的基类，表示关系代数树中的一个节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，这是转换规则的基类，用于将一种关系节点转换为另一种关系节点
import org.apache.calcite.rel.core.Minus; // 导入Minus类，这是EXCEPT操作（集合差集）的关系节点基类，表示SQL中的EXCEPT操作
import org.apache.calcite.rel.logical.LogicalMinus; // 导入LogicalMinus类，这是EXCEPT操作的逻辑表示，对应SQL中的EXCEPT逻辑操作

/**
 * Rule to convert an {@link LogicalMinus} to an {@link EnumerableMinus}.
 * You may provide a custom config to convert other nodes that extend {@link Minus}.
 *
 * @see EnumerableRules#ENUMERABLE_MINUS_RULE
 */
// 类文档注释：说明这是一个将LogicalMinus转换为EnumerableMinus的规则
// LogicalMinus是逻辑层面的EXCEPT操作，EnumerableMinus是可枚举执行层面的EXCEPT操作
// 可以通过自定义配置来转换其他继承自Minus的节点
// 参见EnumerableRules类中的ENUMERABLE_MINUS_RULE常量，这是该规则的默认实例
class EnumerableMinusRule extends ConverterRule { // 定义EnumerableMinusRule类，继承自ConverterRule，这是一个转换规则类
  /** Default configuration. */
  // 成员变量注释：默认配置常量，用于定义该转换规则的基本配置
  static final Config DEFAULT_CONFIG = Config.INSTANCE // 使用Config.INSTANCE作为基础配置，这是一个预定义的配置实例
      .withConversion(LogicalMinus.class, Convention.NONE, // 指定转换规则：将LogicalMinus类（逻辑EXCEPT操作）从Convention.NONE（无特定调用约定）转换
          EnumerableConvention.INSTANCE, "EnumerableMinusRule") // 转换为EnumerableConvention.INSTANCE（可枚举调用约定），规则名称为"EnumerableMinusRule"
      .withRuleFactory(EnumerableMinusRule::new); // 设置规则工厂，使用方法引用EnumerableMinusRule::new来创建该规则的实例

  /** Called from the Config. */
  // 构造方法注释：从Config中调用的构造方法，用于创建EnumerableMinusRule实例
  protected EnumerableMinusRule(Config config) { // 定义受保护的构造方法，接收Config参数
    super(config); // 调用父类ConverterRule的构造方法，传入配置参数，初始化转换规则的基本属性
  } // 构造方法结束

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，将输入的关系节点转换为可枚举的关系节点，RelNode是关系节点的基类
    final Minus minus = (Minus) rel; // 将输入的RelNode强制转换为Minus类型，minus表示EXCEPT操作的关系节点
    final EnumerableConvention out = EnumerableConvention.INSTANCE; // 获取输出调用约定，即EnumerableConvention.INSTANCE，表示可枚举的执行约定
    final RelTraitSet traitSet = // 创建新的特征集合，用于替换原有节点的特征
        rel.getTraitSet().replace( // 获取原节点的特征集合，并替换其中的调用约定
            EnumerableConvention.INSTANCE); // 将调用约定替换为EnumerableConvention.INSTANCE，表示该节点将使用可枚举方式执行
    return new EnumerableMinus(rel.getCluster(), traitSet, // 创建并返回新的EnumerableMinus节点，传入聚类信息（包含类型工厂等）、特征集合
        convertList(minus.getInputs(), out), minus.all); // 将minus的所有输入节点转换为可枚举节点，并传入all标志（是否保留重复行，EXCEPT ALL vs EXCEPT）
  } // convert方法结束，返回转换后的EnumerableMinus节点
} // 类定义结束
