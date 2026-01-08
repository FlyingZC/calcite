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
// 声明包名：org.apache.calcite.adapter.enumerable，表示这个类属于Calcite框架的可枚举适配器包
package org.apache.calcite.adapter.enumerable;

// 导入Convention类：表示关系代数节点的调用约定（Convention），定义了节点如何被实现和执行
import org.apache.calcite.plan.Convention;
// 导入RelTraitSet类：表示关系代数节点的特征集合，包含调用约定、排序、分区等属性
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode类：表示关系代数节点，是Calcite中所有关系表达式（如表、过滤器、连接等）的基类
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule类：表示转换规则的基类，用于将一种类型的RelNode转换为另一种类型
import org.apache.calcite.rel.convert.ConverterRule;
// 导入Union类：表示UNION操作的关系代数节点，用于合并多个查询结果集
import org.apache.calcite.rel.core.Union;
// 导入LogicalUnion类：表示逻辑层的UNION操作节点，是Union的子类，用于逻辑优化阶段
import org.apache.calcite.rel.logical.LogicalUnion;
// 导入Util类：Calcite的工具类，提供了各种静态辅助方法
import org.apache.calcite.util.Util;

// 导入List类：Java集合框架的List接口，用于表示有序的元素集合
import java.util.List;

/**
 * Rule to convert an {@link LogicalUnion} to an {@link EnumerableUnion}.
 * 这是一个转换规则类，用于将逻辑层的LogicalUnion节点转换为可执行的EnumerableUnion节点
 * You may provide a custom config to convert other nodes that extend {@link Union}.
 * 可以提供自定义配置来转换其他继承自Union的节点
 *
 * @see EnumerableRules#ENUMERABLE_UNION_RULE
 * 参见EnumerableRules类中的ENUMERABLE_UNION_RULE常量，这是该规则的默认实例
 */
// EnumerableUnionRule类继承自ConverterRule，表示这是一个关系代数节点的转换规则
// 该类的作用是将逻辑层面的UNION操作（LogicalUnion）转换为可执行的物理层面的UNION操作（EnumerableUnion）
// 在Calcite的查询优化过程中，规则（Rule）是核心概念，用于模式匹配和转换关系表达式树
class EnumerableUnionRule extends ConverterRule {
  /** Default configuration. */
  // 声明一个静态final常量DEFAULT_CONFIG，表示该转换规则的默认配置对象
  // Config.INSTANCE是ConverterRule.Config的默认实例，提供了转换规则的基本配置
  // withConversion方法配置转换规则：
  //   第一个参数LogicalUnion.class：指定要转换的源节点类型为LogicalUnion
  //   第二个参数Convention.NONE：指定源节点的调用约定为NONE（表示无特定约定，通常是逻辑节点）
  //   第三个参数EnumerableConvention.INSTANCE：指定目标节点的调用约定为EnumerableConvention（可枚举约定）
  //   第四个参数"EnumerableUnionRule"：指定规则的描述名称
  // withRuleFactory方法配置规则工厂：
  //   使用Lambda表达式EnumerableUnionRule::new指定如何创建该规则实例
  static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(LogicalUnion.class, Convention.NONE,
          EnumerableConvention.INSTANCE, "EnumerableUnionRule")
      .withRuleFactory(EnumerableUnionRule::new);

  /** Called from the Config. */
  // 声明受保护的构造方法，接收一个Config参数
  // 该构造方法由Config配置对象调用，用于创建EnumerableUnionRule实例
  // 通过super(config)调用父类ConverterRule的构造方法，传入配置对象初始化规则
  protected EnumerableUnionRule(Config config) {
    super(config);
  }

  // @Override注解表示该方法重写了父类ConverterRule的convert方法
  // public RelNode convert(RelNode rel)是转换规则的核心方法，用于执行实际的转换逻辑
  // 参数rel：待转换的关系代数节点，在这个规则中应该是LogicalUnion类型
  // 返回值RelNode：转换后的关系代数节点，在这个规则中返回EnumerableUnion实例
  @Override public RelNode convert(RelNode rel) {
    // 将传入的rel节点强制转换为Union类型（实际上是LogicalUnion）
    // union变量保存了原始的逻辑UNION节点，包含所有输入和UNION的属性（如是否去重）
    final Union union = (Union) rel;
    // 获取EnumerableConvention的实例，这是目标调用约定
    // out变量表示转换后的节点应该遵循的调用约定，即可枚举约定
    final EnumerableConvention out = EnumerableConvention.INSTANCE;
    // 构建新的特征集（RelTraitSet）
    // rel.getCluster()获取当前节点所属的集群（Cluster），集群包含了类型系统、表达式工厂等共享资源
    // .traitSet获取当前节点的特征集
    // .replace(out)将特征集中的调用约定替换为EnumerableConvention，其他特征保持不变
    // traitSet变量保存了转换后节点应该具有的特征集合
    final RelTraitSet traitSet = rel.getCluster().traitSet().replace(out);
    // 转换UNION操作的所有输入节点
    // union.getInputs()获取当前UNION节点的所有输入节点（返回List<RelNode>）
    // Util.transform是一个工具方法，用于对集合中的每个元素应用转换函数
    // Lambda表达式n -> convert(n, traitSet)表示：
    //   n是集合中的每个输入节点
    //   convert(n, traitSet)调用当前规则或其他规则将该节点转换为具有traitSet特征的节点
    // newInputs变量保存了转换后的所有输入节点列表，这些节点都已经是可枚举的
    final List<RelNode> newInputs =
        Util.transform(union.getInputs(), n -> convert(n, traitSet));
    // 创建并返回新的EnumerableUnion节点
    // rel.getCluster()使用原节点的集群
    // traitSet使用新构建的特征集（包含EnumerableConvention）
    // newInputs传入转换后的输入节点列表
    // union.all传入UNION的all属性，表示是否保留重复行（true表示UNION ALL，false表示UNION）
    // 返回的EnumerableUnion节点是物理执行层面的UNION操作，可以被编译为Java代码执行
    return new EnumerableUnion(rel.getCluster(), traitSet,
        newInputs, union.all);
  }
}
