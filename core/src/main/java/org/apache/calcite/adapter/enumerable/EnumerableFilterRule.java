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
package org.apache.calcite.adapter.enumerable; // 定义包名，该类位于org.apache.calcite.adapter.enumerable包下，表示可枚举适配器包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数节点的调用约定（如逻辑约定、可枚举约定等）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数节点的基类，所有关系操作节点都继承自此类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，表示转换规则基类，用于将一种约定转换为另一种约定
import org.apache.calcite.rel.core.Filter; // 导入Filter类，表示过滤操作的关系节点基类
import org.apache.calcite.rel.logical.LogicalFilter; // 导入LogicalFilter类，表示逻辑过滤操作的关系节点
import org.apache.calcite.rex.RexUtil; // 导入RexUtil类，提供行表达式（RexNode）的实用工具方法

/**
 * Rule to convert a {@link LogicalFilter} to an {@link EnumerableFilter}.
 * 这是一个转换规则类，用于将逻辑过滤节点（LogicalFilter）转换为可枚举过滤节点（EnumerableFilter）
 * You may provide a custom config to convert other nodes that extend {@link Filter}.
 * 你可以提供自定义配置来转换其他继承自Filter的节点
 *
 * @see EnumerableRules#ENUMERABLE_FILTER_RULE
 * 参见EnumerableRules类中的ENUMERABLE_FILTER_RULE常量，这是该规则的默认实例
 */
class EnumerableFilterRule extends ConverterRule { // EnumerableFilterRule类继承自ConverterRule，实现从逻辑约定到可枚举约定的转换
  /** Default configuration. */
  // 默认配置常量，使用Config.INSTANCE作为基础配置
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义公共静态最终配置对象，这是该规则的默认配置
      .withConversion(LogicalFilter.class, f -> // 配置转换规则：指定要转换的源节点类型为LogicalFilter.class，并提供一个谓词函数f
              !f.containsOver() && !RexUtil.SubQueryFinder.containsSubQuery(f), // 谓词函数：检查过滤条件中不包含窗口函数（containsOver）且不包含子查询（containsSubQuery），只有满足此条件的LogicalFilter才会被转换
          Convention.NONE, // 指定源节点的调用约定为Convention.NONE（无约定，表示逻辑节点）
          EnumerableConvention.INSTANCE, // 指定目标节点的调用约定为EnumerableConvention.INSTANCE（可枚举约定）
          "EnumerableFilterRule") // 指定规则的名称为"EnumerableFilterRule"，用于标识和调试
      .withRuleFactory(EnumerableFilterRule::new); // 指定规则工厂，使用方法引用EnumerableFilterRule::new来创建规则实例

  protected EnumerableFilterRule(Config config) { // 受保护的构造方法，接收一个Config配置对象作为参数
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象，完成规则的初始化
  }

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，实现具体的转换逻辑，接收一个RelNode参数，返回转换后的RelNode
    final Filter filter = (Filter) rel; // 将传入的rel节点强制转换为Filter类型，赋值给局部变量filter，这里实际传入的应该是LogicalFilter
    return new EnumerableFilter( // 创建并返回一个新的EnumerableFilter实例，这是转换后的可枚举过滤节点
        rel.getCluster(), // 参数1：传入关系节点的Cluster对象，包含查询优化器的上下文信息（如类型系统、表达式工厂等）
        rel.getTraitSet().replace(EnumerableConvention.INSTANCE), // 参数2：传入节点的特征集合（TraitSet），并替换其中的调用约定为EnumerableConvention.INSTANCE，表示该节点现在遵循可枚举约定
        convert(filter.getInput(), // 参数3：调用convert方法递归转换过滤器的输入节点，确保输入节点也转换为可枚举约定
            filter.getInput().getTraitSet() // 获取输入节点的特征集合
                .replace(EnumerableConvention.INSTANCE)), // 将输入节点的特征集合中的调用约定替换为EnumerableConvention.INSTANCE，确保输入节点也遵循可枚举约定
        filter.getCondition()); // 参数4：传入过滤条件（RexNode类型），保持过滤条件不变，直接从原Filter节点获取
  }
} // 类结束标记
