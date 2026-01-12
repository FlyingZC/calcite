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
package org.apache.calcite.adapter.pig; // 指定当前类所属的包，位于org.apache.calcite.adapter.pig包下，这是Calcite中用于Pig适配器的包

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention类，表示可枚举的约定，是Calcite中用于表示可以生成Java代码执行的关系代数约定
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系节点的特征集合，包含约定、排序、分布等特征
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，表示转换规则，用于将一种约定的关系节点转换为另一种约定的关系节点

/**
 * Rule to convert a relational expression from // 这是一个转换规则的类文档注释，说明该规则用于将关系表达式从一种约定转换为另一种约定
 * {@link PigRel#CONVENTION} to {@link EnumerableConvention}. // 具体来说，是将Pig约定转换为Enumerable约定，Pig约定表示使用Apache Pig的数据处理方式，Enumerable约定表示可以使用Java代码枚举处理数据
 * // 该类是Calcite优化器中的一个规则，用于在物理计划生成阶段，将Pig适配器的关系节点转换为可执行的可枚举节点
 */ 
public class PigToEnumerableConverterRule extends ConverterRule { // 定义PigToEnumerableConverterRule类，继承自ConverterRule，表示这是一个将Pig约定转换为Enumerable约定的转换规则
  public static final ConverterRule INSTANCE = Config.INSTANCE // 定义一个静态常量INSTANCE，这是该规则的唯一实例，使用Config.INSTANCE作为基础配置进行构建
      .withConversion(RelNode.class, PigRel.CONVENTION, // 配置转换规则：指定要转换的节点类型为RelNode（任何关系节点），输入约定为Prel.CONVENTION（Pig约定）
          EnumerableConvention.INSTANCE, "PigToEnumerableConverterRule") // 输出约定为EnumerableConvention.INSTANCE（可枚举约定），规则名称为"PigToEnumerableConverterRule"
      .withRuleFactory(PigToEnumerableConverterRule::new) // 设置规则工厂，使用方法引用PigToEnumerableConverterRule::new来创建规则实例
      .toRule(PigToEnumerableConverterRule.class); // 将配置转换为ConverterRule实例，并指定规则类为PigToEnumerableConverterRule.class
  // INSTANCE是一个单例模式的应用，确保整个优化器中只有一个该规则的实例，提高性能和一致性

  private PigToEnumerableConverterRule(Config config) { // 私有构造方法，接收Config参数，这是ConverterRule的配置对象
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象，完成规则的初始化
  } // 私有构造方法确保只能通过工厂方法创建实例，保持单例模式

  @Override public RelNode convert(RelNode rel) { // 重写父类ConverterRule的convert方法，用于执行实际的转换逻辑，接收一个RelNode参数表示要转换的关系节点
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutConvention()); // 获取原关系节点的特征集合，并将其约定替换为输出约定（EnumerableConvention），创建新的特征集合
    // getOutConvention()返回该规则的目标约定，即EnumerableConvention.INSTANCE
    // replace方法会创建一个新的特征集合，其中约定被替换，其他特征保持不变
    return new PigToEnumerableConverter(rel.getCluster(), newTraitSet, rel); // 创建并返回一个新的PigToEnumerableConverter节点
    // 传入原节点的Cluster（集群信息，包含RexBuilder等工具）、新的特征集合newTraitSet、以及原节点rel作为输入
    // PigToEnumerableConverter是一个包装节点，它将Pig约定的节点包装为Enumerable约定的节点，使其可以生成可执行的Java代码
  } // convert方法完成转换，返回一个具有新约定的关系节点，优化器会继续对这个新节点进行优化和执行
} // PigToEnumerableConverterRule类定义结束，该规则是Calcite Pig适配器中的关键组件，负责将Pig逻辑转换为可执行的可枚举代码
