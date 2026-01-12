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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，这个类属于org.apache.calcite.adapter.elasticsearch包，是Calcite的Elasticsearch适配器包

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention类，这是Calcite中用于表示可枚举数据源约定（Convention）的类，是输出数据的物理实现约定
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，这是Calcite中表示关系表达式特征集合的类，包含多个RelTrait特征
import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是Calcite中所有关系表达式节点的基类，表示关系代数中的一个操作
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，这是Calcite中用于定义转换规则的基类，用于将一种约定的RelNode转换为另一种约定的RelNode

/**
 * Rule to convert a relational expression from
 * {@link ElasticsearchRel#CONVENTION} to {@link EnumerableConvention}.
 */ // 类级别的JavaDoc注释：说明这个规则类的作用是将关系表达式从Elasticsearch约定（CONVENTION）转换为可枚举约定（EnumerableConvention）
public class ElasticsearchToEnumerableConverterRule extends ConverterRule { // 定义ElasticsearchToEnumerableConverterRule类，继承自ConverterRule，是一个转换规则，用于将Elasticsearch的关系表达式转换为可枚举的关系表达式
  /** Singleton instance of ElasticsearchToEnumerableConverterRule. */ // 静态常量INSTANCE的JavaDoc注释：说明这是ElasticsearchToEnumerableConverterRule的单例实例
  static final ConverterRule INSTANCE = Config.INSTANCE // 定义一个静态final常量INSTANCE，类型为ConverterRule，这是该规则的单例实例，通过Config.INSTANCE配置构建
      .withConversion(RelNode.class, ElasticsearchRel.CONVENTION, // 调用withConversion方法指定转换规则：RelNode.class表示可以转换任何RelNode类型，ElasticsearchRel.CONVENTION表示输入的约定是Elasticsearch约定
          EnumerableConvention.INSTANCE, // EnumerableConvention.INSTANCE表示输出的约定是可枚举约定，即转换后的结果将是一个可遍历的数据集
          "ElasticsearchToEnumerableConverterRule") // 指定这个转换规则的名称为"ElasticsearchToEnumerableConverterRule"，用于日志和调试
      .withRuleFactory(ElasticsearchToEnumerableConverterRule::new) // 调用withRuleFactory方法指定规则工厂，使用方法引用ElasticsearchToEnumerableConverterRule::new创建规则实例的工厂方法
      .toRule(ElasticsearchToEnumerableConverterRule.class); // 调用toRule方法生成最终的ConverterRule实例，传入ElasticsearchToEnumerableConverterRule.class作为规则类型

  /** Called from the Config. */ // 构造方法的JavaDoc注释：说明这个构造方法是从Config（配置）中调用的
  protected ElasticsearchToEnumerableConverterRule(Config config) { // 定义保护级别的构造方法，接收Config参数，这是ConverterRule的配置对象，包含了规则的所有配置信息
    super(config); // 调用父类ConverterRule的构造方法，传入config参数，完成父类的初始化
  }

  @Override public RelNode convert(RelNode relNode) { // 重写父类ConverterRule的convert方法，接收一个RelNode参数，返回转换后的RelNode，这是转换规则的核心方法
    RelTraitSet newTraitSet = relNode.getTraitSet().replace(getOutConvention()); // 获取输入RelNode的特征集合，并调用replace方法将输入约定替换为输出约定（getOutConvention()返回EnumerableConvention），生成新的特征集合
    return new ElasticsearchToEnumerableConverter(relNode.getCluster(), newTraitSet, relNode); // 创建并返回一个新的ElasticsearchToEnumerableConverter实例，传入RelNode的集群信息、新的特征集合和原始RelNode，这个转换器负责将Elasticsearch的数据转换为可枚举的数据
  } // convert方法结束，返回转换后的RelNode
} // ElasticsearchToEnumerableConverterRule类定义结束
