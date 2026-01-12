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
package org.apache.calcite.adapter.geode.rel; // 声明包名，该类位于org.apache.calcite.adapter.geode.rel包下，是Geode适配器关系表达式转换规则包

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention类，表示可枚举的调用约定，用于标记可以转换为可枚举的关系表达式
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式特征集合，用于描述关系表达式的物理属性
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系表达式节点，是Calcite中所有关系操作符的基类
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，表示转换规则基类，用于将一种调用约定转换为另一种调用约定

/**
 * Rule to convert a relational expression from // 此类是一个转换规则，用于将关系表达式从
 * {@link GeodeRel#CONVENTION} to {@link EnumerableConvention}. // Geode调用约定转换为可枚举调用约定，这是Calcite优化器中的一种规则模式
 */ // 该规则的作用是将Geode特有的关系表达式转换为标准的可枚举关系表达式，使其能够被Calcite的Enumerable执行引擎处理
public class GeodeToEnumerableConverterRule extends ConverterRule { // 定义GeodeToEnumerableConverterRule类，继承自ConverterRule，实现从Geode约定到Enumerable约定的转换逻辑
  public static final ConverterRule INSTANCE = Config.INSTANCE // 定义静态常量INSTANCE，这是该转换规则的唯一实例，采用单例模式，Config.INSTANCE是ConverterRule的默认配置
      .withConversion(RelNode.class, GeodeRel.CONVENTION, // 配置转换规则：指定输入为任意RelNode类型，输入调用约定为GeodeRel.CONVENTION（Geode特有的调用约定）
          EnumerableConvention.INSTANCE, "GeodeToEnumerableConverterRule") // 输出调用约定为EnumerableConvention.INSTANCE（标准的可枚举调用约定），规则名称为"GeodeToEnumerableConverterRule"
      .withRuleFactory(GeodeToEnumerableConverterRule::new) // 设置规则工厂方法，使用方法引用GeodeToEnumerableConverterRule::new来创建规则实例
      .toRule(GeodeToEnumerableConverterRule.class); // 将配置转换为ConverterRule实例，并指定规则类型为GeodeToEnumerableConverterRule.class

  protected GeodeToEnumerableConverterRule(Config config) { // 构造方法，接收Config配置对象，用于初始化转换规则
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象，完成规则的初始化设置
  }

  @Override public RelNode convert(RelNode rel) { // 重写convert方法，执行实际的转换逻辑，接收一个RelNode参数（待转换的关系表达式），返回转换后的RelNode
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutConvention()); // 创建新的特征集合：获取原关系表达式的特征集，将其中的调用约定替换为输出调用约定（即EnumerableConvention）
    return new GeodeToEnumerableConverter(rel.getCluster(), newTraitSet, rel); // 创建并返回GeodeToEnumerableConverter实例：传入关系表达式的集群信息、新的特征集合和原始关系表达式，完成转换
  } // convert方法的核心作用是将Geode关系表达式包装为GeodeToEnumerableConverter，使其具有可枚举的特征，从而可以被Enumerable执行引擎处理
} // GeodeToEnumerableConverterRule类结束，该类实现了从Geode约定到Enumerable约定的转换规则，是Calcite优化器规则体系中的重要组成部分
