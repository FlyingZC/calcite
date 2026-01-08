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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证协议
package org.apache.calcite.adapter.enumerable; // 指定该类所在的包路径，属于enumerable适配器模块

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数节点的调用约定（如物理实现方式）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数节点的基础接口
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule基类，用于定义将一种关系节点转换为另一种的规则
import org.apache.calcite.rel.core.Values; // 导入Values类，表示逻辑上的常量值关系节点
import org.apache.calcite.rel.logical.LogicalValues; // 导入LogicalValues类，表示逻辑层的Values节点

/** Planner rule that converts a {@link LogicalValues} to an {@link EnumerableValues}.
 * You may provide a custom config to convert other nodes that extend {@link Values}.
 *
 * @see EnumerableRules#ENUMERABLE_VALUES_RULE */
// 这是一个优化器规则类，用于将逻辑层的Values节点转换为可枚举的Values节点
// LogicalValues表示逻辑上的常量值表（如SELECT 1, 2, 3）
// EnumerableValues表示物理实现上可以通过枚举方式访问的Values节点
// 支持通过自定义配置来转换Values的其他子类
// 参见EnumerableRules类中定义的ENUMERABLE_VALUES_RULE常量
public class EnumerableValuesRule extends ConverterRule { // 定义EnumerableValuesRule类，继承自ConverterRule基类，实现转换规则
  /** Default configuration. */
  // 定义默认配置常量，用于创建该规则实例时使用
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 使用ConverterRule的Config基类实例作为起点配置
      .withConversion(LogicalValues.class, Convention.NONE, // 指定转换规则：将LogicalValues类（逻辑Values节点）作为转换源
          EnumerableConvention.INSTANCE, "EnumerableValuesRule") // 转换目标为EnumerableConvention（可枚举调用约定），规则名称为"EnumerableValuesRule"
      .withRuleFactory(EnumerableValuesRule::new); // 指定规则工厂方法，使用构造函数引用来创建EnumerableValuesRule实例

  /** Creates an EnumerableValuesRule. */
  // 构造方法，用于创建EnumerableValuesRule实例
  protected EnumerableValuesRule(Config config) { // 接收Config配置对象作为参数
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象完成初始化
  }

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，实现具体的转换逻辑，接收一个RelNode作为输入，返回转换后的RelNode
    final Values logicalValues = (Values) rel; // 将输入的RelNode强制转换为Values类型，获得逻辑Values节点
    final EnumerableValues enumerableValues = // 创建EnumerableValues实例，这是物理实现的Values节点
        EnumerableValues.create(logicalValues.getCluster(), // 从逻辑Values节点获取Cluster信息（包含优化器上下文、表达式工厂等）
            logicalValues.getRowType(), logicalValues.getTuples()); // 从逻辑Values节点获取行类型（RowType，描述结果集的列信息）和元组数据（Tuples，实际的常量值数据）
    return enumerableValues.copy( // 创建EnumerableValues的副本，并修改其特征集
        logicalValues.getTraitSet().replace(EnumerableConvention.INSTANCE), // 将逻辑Values的特征集中的调用约定替换为EnumerableConvention，实现从逻辑到物理的转换
        enumerableValues.getInputs()); // 保留EnumerableValues的输入列表（Values节点通常没有输入，这里保持一致性）
  }
} // 类定义结束
