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
// 声明当前类所在的包，位于Apache Calcite框架的Cassandra适配器模块中
package org.apache.calcite.adapter.cassandra;

// 导入可枚举约定类，用于表示可枚举的RelNode特性集合
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
// 导入RelTraitSet类，用于表示RelNode的特性集合
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule抽象类，用于定义转换规则的基类
import org.apache.calcite.rel.convert.ConverterRule;

/**
 * Rule to convert a relational expression from
 * {@link CassandraRel#CONVENTION} to {@link EnumerableConvention}.
 * 这是一个规则类，用于将关系表达式从Cassandra约定转换为可枚举约定
 * 在Calcite的优化器中，不同的RelNode可以有不同的约定（Convention）
 * Cassandra约定表示该RelNode使用Cassandra特定的实现方式
 * 可枚举约定表示该RelNode可以被枚举，即可以逐行处理数据
 * 这个转换规则是Cassandra适配器中关键的优化规则之一
 *
 * @see CassandraRules#TO_ENUMERABLE 参见CassandraRules中的TO_ENUMERABLE常量
 */
// 定义CassandraToEnumerableConverterRule类，继承自ConverterRule抽象类
// 这个类的作用是将Cassandra特定的RelNode转换为可枚举的RelNode
// 使得Cassandra的数据源可以通过Calcite的枚举接口进行数据访问
public class CassandraToEnumerableConverterRule extends ConverterRule {
  /** Default configuration. */
  // 定义默认配置常量，用于初始化这个转换规则
  // Config是ConverterRule的配置类，包含了规则的所有配置信息
  // Config.INSTANCE是预定义的配置实例
  // withConversion方法配置转换规则的参数：
  //   - RelNode.class: 要转换的RelNode类型，这里表示所有RelNode都可以被转换
  //   - CassandraRel.CONVENTION: 输入的约定，即从Cassandra约定转换
  //   - EnumerableConvention.INSTANCE: 输出的约定，即转换为可枚举约定
  //   - "CassandraToEnumerableConverterRule": 规则的名称，用于调试和日志
  // withRuleFactory方法配置规则的工厂方法：
  //   - CassandraToEnumerableConverterRule::new: 使用构造函数引用作为工厂方法
  //     当优化器需要创建这个规则的实例时，会调用这个工厂方法
  // 这个DEFAULT_CONFIG是静态常量，可以在规则的注册时使用
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(RelNode.class, CassandraRel.CONVENTION,
          EnumerableConvention.INSTANCE, "CassandraToEnumerableConverterRule")
      .withRuleFactory(CassandraToEnumerableConverterRule::new);

  /** Creates a CassandraToEnumerableConverterRule. */
  // 构造方法，用于创建CassandraToEnumerableConverterRule实例
  // 参数config: 转换规则的配置对象，包含了规则的所有配置信息
  // 这个构造方法是protected的，意味着只有子类或同包的类可以调用
  // 通常通过DEFAULT_CONFIG来创建实例
  protected CassandraToEnumerableConverterRule(Config config) {
    // 调用父类ConverterRule的构造方法，传入配置对象
    // 父类会根据配置初始化规则的各种属性
    super(config);
  }

  // 重写父类的convert方法，实现具体的转换逻辑
  // 这个方法在优化器匹配到这个规则时被调用
  // 参数rel: 要转换的RelNode，通常是Cassandra约定的RelNode
  // 返回值: 转换后的RelNode，应该是可枚举约定的RelNode
  @Override public RelNode convert(RelNode rel) {
    // 创建新的特性集合，将输入RelNode的特性集合中的约定替换为输出约定
    // rel.getTraitSet(): 获取输入RelNode的特性集合
    // .replace(getOutConvention()): 将集合中的约定替换为输出约定（可枚举约定）
    // getOutConvention()是从父类ConverterRule继承的方法，返回配置中指定的输出约定
    // 这样做的目的是保持RelNode的其他特性不变，只改变约定
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutConvention());
    // 创建并返回一个新的CassandraToEnumerableConverter实例
    // 这个新的RelNode使用新的特性集合，并将原始RelNode作为输入
    // CassandraToEnumerableConverter是一个具体的RelNode实现，它包装了原始的Cassandra RelNode
    // 并提供了可枚举的数据访问接口
    // rel.getCluster(): 获取RelNode所属的Cluster，包含了查询的元数据等信息
    // newTraitSet: 新的特性集合，包含了可枚举约定
    // rel: 原始的Cassandra RelNode，作为子节点
    return new CassandraToEnumerableConverter(rel.getCluster(), newTraitSet, rel);
  }
}
