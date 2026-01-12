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
package org.apache.calcite.adapter.mongodb; // 指定当前类所属的包：org.apache.calcite.adapter.mongodb，这是MongoDB适配器的包路径

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention，这是可枚举的调用约定，表示关系表达式可以被枚举为Java代码
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet，这是关系表达式的特征集合，包含物理属性如约定、排序、分布等
import org.apache.calcite.rel.RelNode; // 导入RelNode，这是关系表达式的基类，代表关系代数的一个节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule，这是转换规则的基类，用于将一种约定转换为另一种约定

/**
 * Rule to convert a relational expression from // 这是一个规则类的注释，说明该规则的作用
 * {@link MongoRel#CONVENTION} to {@link EnumerableConvention}. // 用于将MongoDB约定转换为可枚举约定
 * // 详细说明：MongoToEnumerableConverterRule是Calcite优化器中的一个转换规则，它的核心功能是将MongoDB特定的关系表达式（使用MongoRel.CONVENTION约定）
 * // 转换为可枚举的关系表达式（使用EnumerableConvention约定）。这个转换是Calcite查询优化过程中的关键步骤，它允许MongoDB适配器生成的逻辑计划
 * // 能够被Calcite的Enumerable执行引擎执行。Enumerable约定意味着关系表达式可以被编译成Java代码并执行，这是Calcite提供的一种灵活的执行方式。
 * // 在查询优化过程中，当优化器发现一个MongoDB关系节点时，会触发这个规则，将其转换为Enumerable节点，从而使得查询可以在Java环境中执行。
 */ // 类注释结束
public class MongoToEnumerableConverterRule extends ConverterRule { // 定义MongoToEnumerableConverterRule类，继承自ConverterRule基类，表示这是一个转换规则
  /** Singleton instance of MongoToEnumerableConverterRule. */ // 注释：这是MongoToEnumerableConverterRule的单例实例
  // 单例模式确保整个优化器中只有一个该规则的实例，避免重复创建对象，提高性能
  // INSTANCE是一个静态常量，使用Config.INSTANCE作为基础配置，通过链式调用配置转换规则的各种属性
  public static final ConverterRule INSTANCE = Config.INSTANCE // 获取ConverterRule的默认配置实例，这是所有转换规则的配置基础
      .withConversion(RelNode.class, MongoRel.CONVENTION, // 配置转换规则：将RelNode类及其子类从MongoRel.CONVENTION约定转换
          EnumerableConvention.INSTANCE, "MongoToEnumerableConverterRule") // 转换为EnumerableConvention.INSTANCE约定，规则名称为"MongoToEnumerableConverterRule"
      .withRuleFactory(MongoToEnumerableConverterRule::new) // 设置规则工厂，使用方法引用MongoToEnumerableConverterRule::new来创建规则实例
      .toRule(MongoToEnumerableConverterRule.class); // 将配置转换为实际的ConverterRule实例，指定规则类为MongoToEnumerableConverterRule.class

  /** Called from the Config. */ // 注释：这个构造方法由Config配置调用，用于创建规则实例
  // protected构造方法，接收Config对象作为参数，通过super(config)调用父类ConverterRule的构造方法
  // 构造方法的作用是初始化转换规则，包括输入约定、输出约定、转换条件等配置信息
  protected MongoToEnumerableConverterRule(Config config) { // 定义受保护的构造方法，参数是Config对象，包含转换规则的所有配置信息
    super(config); // 调用父类ConverterRule的构造方法，将配置传递给父类进行初始化
  } // 构造方法结束

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，这是ConverterRule的核心方法，用于执行实际的转换操作
    // 参数rel是需要转换的输入关系节点，该节点使用MongoRel.CONVENTION约定
    // 返回值是转换后的关系节点，该节点使用EnumerableConvention.INSTANCE约定
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutConvention()); // 创建新的特征集合：获取输入节点的特征集合，将其中的约定替换为输出约定（即EnumerableConvention）
    // getOutConvention()返回该规则的输出约定，即EnumerableConvention.INSTANCE
    // replace方法将特征集合中的约定替换为新的约定，其他特征保持不变
    return new MongoToEnumerableConverter(rel.getCluster(), newTraitSet, rel); // 创建并返回MongoToEnumerableConverter实例，这是转换后的节点
    // MongoToEnumerableConverter构造方法接收三个参数：rel.getCluster()获取集群信息（包含RexBuilder等），newTraitSet是新的特征集合，rel是输入节点（作为子节点）
    // MongoToEnumerableConverter是实际的转换器节点，它包装了原始的MongoDB关系节点，使其可以被Enumerable执行引擎执行
  } // convert方法结束
} // 类定义结束
