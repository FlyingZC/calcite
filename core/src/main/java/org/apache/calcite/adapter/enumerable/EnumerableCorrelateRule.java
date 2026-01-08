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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.enumerable; // 定义包名，该类属于org.apache.calcite.adapter.enumerable包，用于可枚举适配器

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数节点的调用约定（如物理实现方式）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，用于定义将一种约定转换为另一种约定的规则
import org.apache.calcite.rel.core.Correlate; // 导入Correlate类，表示相关联接操作符，用于处理子查询中的相关变量
import org.apache.calcite.rel.logical.LogicalCorrelate; // 导入LogicalCorrelate类，表示逻辑层面的相关联接操作符

import org.immutables.value.Value; // 导入Value注解，用于生成不可变值对象（来自Immutables库）

/**
 * Implementation of nested loops over enumerable inputs.
 * // 这是一个基于可枚举输入的嵌套循环实现类
 * // 该规则用于将逻辑层面的相关联接（LogicalCorrelate）转换为可枚举的相关联接（EnumerableCorrelate）
 * // 相关联接通常用于处理相关子查询，即子查询中引用了外层查询的列
 * // 嵌套循环是实现相关联接的一种方式，对于左表的每一行，都要扫描右表来匹配
 * // 可枚举（Enumerable）意味着数据可以以Java Iterable的方式逐行处理
 *
 * @see EnumerableRules#ENUMERABLE_CORRELATE_RULE // 参见EnumerableRules类中的ENUMERABLE_CORRELATE_RULE常量
 */
@Value.Enclosing // Immutables库的注解，表示该类包含嵌套的不可变值类型定义
public class EnumerableCorrelateRule extends ConverterRule { // 定义类名，继承自ConverterRule，表示这是一个转换规则
  /** Default configuration. */
  // 默认配置常量，用于创建该转换规则的默认配置
  // Config是父类ConverterRule的内部接口，用于配置转换规则的各种参数
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 获取Config的默认实例
      .withConversion(LogicalCorrelate.class, r -> true, Convention.NONE, // 配置转换规则：将LogicalCorrelate（逻辑相关联接）转换为可枚举约定
          EnumerableConvention.INSTANCE, "EnumerableCorrelateRule") // 目标约定为EnumerableConvention.INSTANCE（可枚举约定），规则名称为"EnumerableCorrelateRule"
      .withRuleFactory(EnumerableCorrelateRule::new); // 设置规则工厂，使用构造函数引用来创建EnumerableCorrelateRule实例

  /** Creates an EnumerableCorrelateRule. */
  // 构造方法，用于创建EnumerableCorrelateRule实例
  // 参数config：转换规则的配置对象，包含转换目标、条件等信息
  protected EnumerableCorrelateRule(Config config) { // 定义受保护的构造方法，接受Config参数
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象
  }

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，用于将逻辑节点转换为物理节点
    // 参数rel：待转换的关系节点，这里应该是LogicalCorrelate类型
    // 返回值：转换后的物理节点，这里是EnumerableCorrelate类型
    final Correlate c = (Correlate) rel; // 将输入的RelNode强制转换为Correlate类型，提取相关联接节点
    return EnumerableCorrelate.create( // 调用EnumerableCorrelate的静态工厂方法创建可枚举相关联接节点
        convert(c.getLeft(), c.getLeft().getTraitSet() // 转换左子节点：将左子节点从当前约定转换为可枚举约定
            .replace(EnumerableConvention.INSTANCE)), // 替换左子节点的特征集为可枚举约定
        convert(c.getRight(), c.getRight().getTraitSet() // 转换右子节点：将右子节点从当前约定转换为可枚举约定
            .replace(EnumerableConvention.INSTANCE)), // 替换右子节点的特征集为可枚举约定
        c.getCorrelationId(), // 保留相关联接的相关ID，用于标识相关变量
        c.getRequiredColumns(), // 保留相关联接所需的列，即右子查询中引用的左表列
        c.getJoinType()); // 保留联接类型（如内连接、左外连接等）
  }
}
