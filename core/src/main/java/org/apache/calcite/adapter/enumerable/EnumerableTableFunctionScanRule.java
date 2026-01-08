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
package org.apache.calcite.adapter.enumerable; // 定义包名,该包包含可枚举适配器的实现,用于将逻辑算子转换为可执行的可枚举算子

import org.apache.calcite.plan.Convention; // 导入Convention类,表示关系代数表达式的调用约定或执行约定
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类,表示关系节点的特征集合,如约定、排序方式等
import org.apache.calcite.rel.RelNode; // 导入RelNode类,表示关系代数表达式树中的一个节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类,表示将一种关系节点转换为另一种关系节点的规则
import org.apache.calcite.rel.core.TableFunctionScan; // 导入TableFunctionScan类,表示对表函数的扫描操作
import org.apache.calcite.rel.logical.LogicalTableFunctionScan; // 导入LogicalTableFunctionScan类,表示逻辑层面的表函数扫描操作

/** Rule to convert a {@link LogicalTableFunctionScan} to an {@link EnumerableTableFunctionScan}.
 * 这是一个转换规则类,用于将逻辑表函数扫描节点(LogicalTableFunctionScan)转换为可枚举表函数扫描节点(EnumerableTableFunctionScan)
 * 可枚举表函数扫描节点可以被编译为Java代码执行,是Calcite优化器物理实现阶段的重要组成部分
 * You may provide a custom config to convert other nodes that extend {@link TableFunctionScan}.
 * 你可以提供自定义配置来转换其他继承自TableFunctionScan的节点,使规则具有更好的扩展性
 *
 * @see EnumerableRules#ENUMERABLE_TABLE_FUNCTION_SCAN_RULE 参见EnumerableRules类中的枚举表函数扫描规则定义 */
public class EnumerableTableFunctionScanRule extends ConverterRule { // 定义类名,继承自ConverterRule,表示这是一个转换规则
  /** Default configuration.
   * 默认配置对象,定义了该转换规则的基本行为
   * 该配置指定了将LogicalTableFunctionScan转换为EnumerableTableFunctionScan的规则
   * 使用Config.INSTANCE作为基础配置,通过链式调用添加转换规则的具体参数
   * */
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 获取基础配置实例
      .withConversion(LogicalTableFunctionScan.class, Convention.NONE, // 指定转换源类为LogicalTableFunctionScan,源约定为NONE(表示逻辑层)
          EnumerableConvention.INSTANCE, "EnumerableTableFunctionScanRule") // 指定目标约定为EnumerableConvention.INSTANCE(可枚举约定),规则名称为"EnumerableTableFunctionScanRule"
      .withRuleFactory(EnumerableTableFunctionScanRule::new); // 指定规则工厂方法,使用构造函数引用创建规则实例

  /** Creates an EnumerableTableFunctionScanRule.
   * 构造方法,用于创建EnumerableTableFunctionScanRule实例
   * 该构造方法受保护,通过配置对象初始化父类ConverterRule
   * @param config 规则配置对象,包含转换规则的所有配置信息
   * */
  protected EnumerableTableFunctionScanRule(Config config) { // 定义受保护的构造方法,接收配置对象参数
    super(config); // 调用父类ConverterRule的构造方法,传入配置对象以初始化规则
  }

  /** Converts a relational expression.
   * 转换方法,将逻辑表函数扫描节点转换为可枚举表函数扫描节点
   * 这是ConverterRule接口的核心方法,执行实际的转换逻辑
   * @param rel 待转换的关系节点,类型为RelNode,实际传入的是LogicalTableFunctionScan实例
   * @return 转换后的可枚举表函数扫描节点(EnumerableTableFunctionScan)
   * */
  @Override public RelNode convert(RelNode rel) { // 重写父类的convert方法,实现具体的转换逻辑
    final RelTraitSet traitSet = // 创建最终的特征集合,用于新的可枚举节点
        rel.getTraitSet().replace(EnumerableConvention.INSTANCE); // 获取原节点的特征集合,并将其中的约定替换为可枚举约定(EnumerableConvention)
    TableFunctionScan scan = (TableFunctionScan) rel; // 将输入的RelNode强制转换为TableFunctionScan类型,以便访问表函数扫描特有的属性
    return new EnumerableTableFunctionScan(rel.getCluster(), traitSet, // 创建并返回新的EnumerableTableFunctionScan实例,传入集群对象和特征集合
        convertList(scan.getInputs(), traitSet.getTrait(0)), // 转换输入节点列表,将所有输入节点转换为符合目标约定的节点,traitSet.getTrait(0)获取特征集合中的第一个特征
        scan.getElementType(), scan.getRowType(), // 传入元素类型(Java类型)和行类型(RelDataType,描述结果集的列信息)
        scan.getCall(), scan.getColumnMappings()); // 传入函数调用(RexNode,表示表函数的调用表达式)和列映射关系(描述结果列与输入列的对应关系)
  }
} // 类定义结束
