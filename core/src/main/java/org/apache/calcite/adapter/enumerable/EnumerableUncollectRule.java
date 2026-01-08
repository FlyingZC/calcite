/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the License"); you may not use this file except in compliance with
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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类位于org.apache.calcite.adapter.enumerable包下，这是Calcite中用于可枚举适配器的包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数操作的约定（convention），是RelTraitSet的一部分
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系节点的特征集合，包括约定、排序、分布等特性
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，这是Calcite中所有关系表达式（关系节点）的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule抽象类，这是所有转换规则的基类，用于将一个RelNode转换为另一个RelNode
import org.apache.calcite.rel.core.Uncollect; // 导入Uncollect类，这是Calcite中用于实现UNNEST/UNCOLLECT操作的关系节点，用于将数组或嵌套表展开为行

/**
 * Rule to convert an {@link org.apache.calcite.rel.core.Uncollect} to an
 * {@link EnumerableUncollect}.
 * 该规则用于将Uncollect关系节点转换为EnumerableUncollect关系节点
 * Uncollect是标准的逻辑算子，用于展开数组或嵌套表；EnumerableUncollect是适配到可枚举约定（EnumerableConvention）的物理实现
 *
 * @see EnumerableRules#ENUMERABLE_UNCOLLECT_RULE
 */
class EnumerableUncollectRule extends ConverterRule { // 定义EnumerableUncollectRule类，继承自ConverterRule，表示这是一个转换规则
  /** Default configuration. */ // 默认配置，用于初始化该转换规则的配置对象
  static final Config DEFAULT_CONFIG = Config.INSTANCE // 创建默认配置对象，Config.INSTANCE是基类提供的默认配置实例
      .withConversion(Uncollect.class, Convention.NONE, // 指定转换规则：将Uncollect类型的节点从Convention.NONE约定转换为
          EnumerableConvention.INSTANCE, "EnumerableUncollectRule") // EnumerableConvention.INSTANCE约定，规则名称为"EnumerableUncollectRule"
      .withRuleFactory(EnumerableUncollectRule::new); // 设置规则工厂，使用方法引用创建EnumerableUncollectRule实例

  /** Called from the Config. */ // 受保护的构造方法，由Config对象调用，用于创建规则实例
  protected EnumerableUncollectRule(Config config) { // 构造方法，接收Config配置对象作为参数
    super(config); // 调用父类ConverterRule的构造方法，传递配置对象
  }

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，这是转换规则的核心方法，负责将输入的RelNode转换为目标RelNode
    final Uncollect uncollect = (Uncollect) rel; // 将输入的RelNode强制转换为Uncollect类型，因为该规则专门处理Uncollect节点
    final RelTraitSet traitSet = // 创建新的特征集合，该集合将包含可枚举约定
        uncollect.getTraitSet().replace(EnumerableConvention.INSTANCE); // 从Uncollect节点获取原有的特征集合，并将其中约定替换为EnumerableConvention.INSTANCE
    final RelNode input = uncollect.getInput(); // 获取Uncollect节点的输入节点，即需要展开的数组或嵌套表对应的关系节点
    final RelNode newInput = // 转换输入节点，将其从当前约定转换为可枚举约定
        convert(input, // 调用convert方法转换输入节点，这是ConverterRule提供的方法，会查找并应用适当的转换规则
            input.getTraitSet().replace(EnumerableConvention.INSTANCE)); // 为输入节点创建包含可枚举约定的特征集合
    return EnumerableUncollect.create(traitSet, newInput, // 创建并返回EnumerableUncollect节点，传入特征集合、转换后的输入节点
        uncollect.withOrdinality); // 以及withOrdinality标志，该标志指示是否在展开结果中包含行号列
  }
}
