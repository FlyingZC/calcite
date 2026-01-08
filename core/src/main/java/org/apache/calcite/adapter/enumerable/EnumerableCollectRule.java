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
// Apache许可证声明，说明代码的授权和使用条件
package org.apache.calcite.adapter.enumerable; // 声明包名为org.apache.calcite.adapter.enumerable，表示这个类属于可枚举适配器包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数运算的调用约定（即物理实现方式）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数运算的节点（抽象语法树的节点）
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，表示用于将一个物理实现转换为另一个物理实现的规则基类
import org.apache.calcite.rel.core.Collect; // 导入Collect类，表示COLLECT运算符的物理实现（用于将多行数据收集到一个集合中）

/**
 * Rule to convert an {@link org.apache.calcite.rel.core.Collect} to an
 * {@link EnumerableCollect}.
 * 这是一个转换规则类，用于将Collect物理运算节点转换为EnumerableCollect物理运算节点
 * Collect是通用的COLLECT运算符实现，而EnumerableCollect是专门用于可枚举实现（基于Java代码生成）的COLLECT运算符
 * 这个规则是Calcite优化器规则体系的一部分，用于在优化过程中将逻辑运算转换为物理运算
 *
 * @see EnumerableRules#ENUMERABLE_COLLECT_RULE // 参见EnumerableRules类中的ENUMERABLE_COLLECT_RULE常量，这是该规则的实例
 */
class EnumerableCollectRule extends ConverterRule { // 定义EnumerableCollectRule类，继承自ConverterRule基类，表示这是一个转换规则
  /** Default configuration. */ // 默认配置的注释说明
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义公共静态常量DEFAULT_CONFIG，表示该转换规则的默认配置对象
      .withConversion(Collect.class, Convention.NONE, // 配置转换规则：将Collect类（输入类型）从Convention.NONE（无特定约定）
          EnumerableConvention.INSTANCE, "EnumerableCollectRule") // 转换为EnumerableConvention.INSTANCE（可枚举约定），规则名称为"EnumerableCollectRule"
      .withRuleFactory(EnumerableCollectRule::new); // 设置规则工厂方法，使用EnumerableCollectRule的构造函数来创建规则实例

  /** Called from the Config. */ // 构造函数的注释说明，说明此构造函数由Config对象调用
  protected EnumerableCollectRule(Config config) { // 定义受保护的构造函数，接收Config配置对象作为参数
    super(config); // 调用父类ConverterRule的构造函数，传入配置对象进行初始化
  }

  @Override public RelNode convert(RelNode rel) { // 重写父类的convert方法，用于执行实际的转换逻辑，接收一个RelNode节点，返回转换后的RelNode节点
    final Collect collect = (Collect) rel; // 将输入的RelNode强制转换为Collect类型，因为此规则只处理Collect节点
    final RelNode input = collect.getInput(); // 获取Collect节点的输入节点（即要收集的数据源），这是RelNode类型
    return EnumerableCollect.create( // 创建并返回一个新的EnumerableCollect节点，这是转换后的可枚举COLLECT运算符
        convert(input, // 调用convert方法递归转换输入节点，将输入节点转换为可枚举约定
            input.getTraitSet().replace(EnumerableConvention.INSTANCE)), // 获取输入节点的特征集，并将其约定替换为EnumerableConvention.INSTANCE（可枚举约定）
        collect.getRowType()); // 传递Collect节点的行类型（输出行的结构），确保转换后的节点保持相同的输出类型
  }
} // 类定义结束
