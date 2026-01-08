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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite可枚举适配器包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于表示关系代数节点的调用约定（执行约定）
import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是Calcite中所有关系代数节点的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，这是转换规则的基类，用于将一种RelNode转换为另一种RelNode
import org.apache.calcite.rel.core.Match; // 导入Match类，这是SQL MATCH_RECOGNIZE操作的核心关系节点，用于模式匹配
import org.apache.calcite.rel.logical.LogicalMatch; // 导入LogicalMatch类，这是Match操作的逻辑表示，用于优化器的逻辑阶段

/**
 * Rule to convert a {@link LogicalMatch} to an {@link EnumerableMatch}. // 本类是一个转换规则，用于将逻辑匹配节点（LogicalMatch）转换为可枚举匹配节点（EnumerableMatch）
 * You may provide a custom config to convert other nodes that extend {@link Match}. // 可以通过提供自定义配置来转换其他继承自Match类的节点
 *
 * @see EnumerableRules#ENUMERABLE_MATCH_RULE // 参见EnumerableRules类中的ENUMERABLE_MATCH_RULE常量，这是该规则的默认实例
 */
public class EnumerableMatchRule extends ConverterRule { // 定义EnumerableMatchRule类，继承自ConverterRule，专门处理Match操作的转换
  /** Default configuration. */ // 默认配置的JavaDoc注释
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 定义静态常量DEFAULT_CONFIG，这是该转换规则的默认配置，使用Config.INSTANCE作为基础配置
      .withConversion(LogicalMatch.class, Convention.NONE, // 配置转换规则：将LogicalMatch类（源节点类型）从Convention.NONE（无约定）转换为
          EnumerableConvention.INSTANCE, "EnumerableMatchRule") // EnumerableConvention.INSTANCE（可枚举约定），规则名称为"EnumerableMatchRule"
      .withRuleFactory(EnumerableMatchRule::new); // 配置规则工厂，使用方法引用EnumerableMatchRule::new来创建规则实例

  /** Creates an EnumerableMatchRule. */ // 构造方法的JavaDoc注释
  protected EnumerableMatchRule(Config config) { // 定义受保护的构造方法，接收Config参数，用于创建EnumerableMatchRule实例
    super(config); // 调用父类ConverterRule的构造方法，传入配置参数
  } // 构造方法结束

  @Override public RelNode convert(RelNode rel) { // 重写父类的convert方法，用于将输入的RelNode转换为目标RelNode，这是转换规则的核心方法
    final Match match = (Match) rel; // 将输入的RelNode强制转换为Match类型，match变量保存原始的Match关系节点
    return EnumerableMatch.create( // 调用EnumerableMatch的静态工厂方法create，创建可枚举的Match节点
        convert(match.getInput(), // 转换Match节点的输入节点，递归调用convert方法将输入节点转换为可枚举约定
            match.getInput().getTraitSet() // 获取输入节点的特征集合（TraitSet），特征集合包含了节点的各种属性（如调用约定、排序等）
                .replace(EnumerableConvention.INSTANCE)), // 将输入节点的特征集合中的约定替换为EnumerableConvention.INSTANCE，确保输入节点也转换为可枚举形式
        match.getRowType(), // 传递Match节点的行类型（RowType），定义了输出行的字段结构
        match.getPattern(), match.isStrictStart(), match.isStrictEnd(), // 传递模式匹配相关的参数：模式定义、是否严格开始、是否严格结束
        match.getPatternDefinitions(), match.getMeasures(), match.getAfter(), // 传递模式定义、度量（MEASURES）子句、AFTER子句参数
        match.getSubsets(), match.isAllRows(), match.getPartitionKeys(), // 传递子集定义、是否返回所有行（ALL ROWS）、分区键
        match.getOrderKeys(), match.getInterval()); // 传递排序键和时间间隔参数
  } // convert方法结束
} // EnumerableMatchRule类结束
