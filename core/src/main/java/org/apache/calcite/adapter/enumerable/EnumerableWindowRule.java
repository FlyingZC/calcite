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
package org.apache.calcite.adapter.enumerable; // 声明该类属于 org.apache.calcite.adapter.enumerable 包，这是 Calcite 框架中用于可枚举适配器的包

import org.apache.calcite.plan.Convention; // 导入 Convention 类，用于定义关系代数节点的约定（convention），表示节点的物理实现方式
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，用于表示关系节点的特征集合（trait set），包含约定、排序、分区等特征
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，这是 Calcite 中所有关系代数节点的基接口
import org.apache.calcite.rel.convert.ConverterRule; // 导入 ConverterRule 抽象类，这是所有转换规则的基类，用于将一种约定转换为另一种约定
import org.apache.calcite.rel.core.Window; // 导入 Window 类，表示窗口函数逻辑关系节点，包含窗口函数的定义和分组信息
import org.apache.calcite.rel.logical.LogicalWindow; // 导入 LogicalWindow 类，表示逻辑层的窗口函数节点，是优化器的逻辑表示

/**
 * Rule to convert a {@link LogicalWindow} to an {@link EnumerableWindow}. // 类注释：这是一个转换规则，用于将逻辑窗口节点（LogicalWindow）转换为可枚举窗口节点（EnumerableWindow）
 * You may provide a custom config to convert other nodes that extend {@link Window}. // 说明可以通过提供自定义配置来转换其他继承自 Window 的节点
 *
 * @see EnumerableRules#ENUMERABLE_WINDOW_RULE // 参考链接：指向 EnumerableRules 中定义的默认窗口规则实例
 */
class EnumerableWindowRule extends ConverterRule { // 类定义：EnumerableWindowRule 继承自 ConverterRule，用于将逻辑窗口转换为可枚举窗口
  /** Default configuration. */ // 成员变量注释：默认配置，定义了该转换规则的基本行为
  static final Config DEFAULT_CONFIG = Config.INSTANCE // 创建默认配置实例，使用 ConverterRule.Config.INSTANCE 作为基础配置
      .withConversion(LogicalWindow.class, Convention.NONE, // 配置转换规则：指定源节点类型为 LogicalWindow，源约定为 Convention.NONE（表示逻辑层）
          EnumerableConvention.INSTANCE, "EnumerableWindowRule") // 目标约定为 EnumerableConvention.INSTANCE（可枚举约定），规则名称为 "EnumerableWindowRule"
      .withRuleFactory(EnumerableWindowRule::new); // 设置规则工厂，使用构造函数引用来创建 EnumerableWindowRule 实例

  /** Called from the Config. */ // 构造方法注释：从配置对象调用，用于初始化转换规则
  protected EnumerableWindowRule(Config config) { // 构造方法定义：接收 Config 配置对象作为参数
    super(config); // 调用父类 ConverterRule 的构造方法，传入配置对象进行初始化
  }

  @Override public RelNode convert(RelNode rel) { // 方法注释：执行实际的转换操作，将逻辑窗口节点转换为可枚举窗口节点，返回转换后的关系节点
    final Window winAgg = (Window) rel; // 将输入的关系节点强制转换为 Window 类型，获取窗口聚合节点
    final RelTraitSet traitSet = // 创建新的特征集合，用于转换后的节点
        winAgg.getTraitSet().replace(EnumerableConvention.INSTANCE); // 从原窗口节点获取特征集合，并将约定替换为可枚举约定
    final RelNode child = winAgg.getInput(); // 获取窗口节点的子节点（输入关系），这是窗口函数要处理的数据源
    final RelNode convertedChild = // 转换子节点，确保子节点也使用可枚举约定
        convert(child, // 调用父类的 convert 方法转换子节点
            child.getTraitSet().replace(EnumerableConvention.INSTANCE)); // 将子节点的特征集合中的约定替换为可枚举约定
    return new EnumerableWindow(rel.getCluster(), traitSet, convertedChild, // 创建新的 EnumerableWindow 实例，传入集群信息、特征集合、转换后的子节点
        winAgg.getConstants(), winAgg.getRowType(), winAgg.groups); // 传入窗口常量、行类型和窗口分组信息，完成窗口函数的转换
  }
} // 类结束
