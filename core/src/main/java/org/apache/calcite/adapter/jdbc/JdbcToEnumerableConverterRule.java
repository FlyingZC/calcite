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
// 声明当前类所属的包名:org.apache.calcite.adapter.jdbc,这是Calcite JDBC适配器模块的包
package org.apache.calcite.adapter.jdbc;

// 导入EnumerableConvention类,这是一个可枚举约定,表示关系表达式可以通过枚举方式执行
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
// 导入RelTraitSet类,这是关系特征集合,用于定义关系表达式的物理属性和约定等特征
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode接口,这是所有关系代数表达式的基础接口,代表一个关系运算符节点
import org.apache.calcite.rel.RelNode;
// 导入ConverterRule类,这是转换规则的基类,用于定义如何将一种约定转换为另一种约定
import org.apache.calcite.rel.convert.ConverterRule;

// 导入@Nullable注解,用于标记可能为null的返回值或参数,这是Checker Framework的空值检查注解
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Rule to convert a relational expression from
 * {@link JdbcConvention} to
 * {@link EnumerableConvention}.
 * 
 * 这是一个转换规则类,用于将关系表达式从JDBC约定转换为可枚举约定
 * 
 * 类作用详细说明:
 * 1. 这个规则是Calcite优化器规则系统中的一个转换规则
 * 2. 它负责将使用JDBC数据源的物理计划节点转换为可枚举的物理计划节点
 * 3. 在Calcite的查询优化过程中,可能存在多种物理实现约定,这个规则实现了从JDBC约定到Enumerable约定的转换
 * 4. JDBC约定表示关系表达式将通过JDBC在数据库中执行
 * 5. Enumerable约定表示关系表达式将通过Java代码迭代执行
 * 6. 这个转换使得原本在数据库中执行的操作可以在Java内存中执行,为后续的优化和执行提供更多灵活性
 * 7. 继承自ConverterRule基类,遵循Calcite的转换规则模式
 */
public class JdbcToEnumerableConverterRule extends ConverterRule {
  /** Creates a JdbcToEnumerableConverterRule. */
  // 静态工厂方法,用于创建JdbcToEnumerableConverterRule实例
  // 参数说明:
  //   - JdbcConvention out: 指定源约定,即从哪个约定转换(JDBC约定)
  // 返回值说明:
  //   - 返回一个配置好的JdbcToEnumerableConverterRule实例
  // 方法详细说明:
  //   1. 这是创建规则的推荐方式,使用Builder模式配置规则
  //   2. Config.INSTANCE是预定义的配置对象,包含规则的基本配置
  //   3. withConversion方法配置转换规则的核心参数:
  //      - RelNode.class: 指定要转换的关系表达式类型,这里表示可以转换任何RelNode
  //      - out: 源约定(JDBC约定),表示从这个约定转换
  //      - EnumerableConvention.INSTANCE: 目标约定(可枚举约定),表示转换到这个约定
  //      - "JdbcToEnumerableConverterRule": 规则的名称,用于调试和日志
  //   4. withRuleFactory方法指定如何创建规则实例,使用方法引用JdbcToEnumerableConverterRule::new
  //   5. toRule方法最终构建出规则实例
  //   6. 这种设计模式遵循Calcite的规则配置最佳实践,提供了灵活的配置方式
  public static JdbcToEnumerableConverterRule create(JdbcConvention out) {
    // 返回配置好的规则实例
    return Config.INSTANCE
        // 配置转换规则:指定源类型、源约定、目标约定和规则名称
        .withConversion(RelNode.class, out, EnumerableConvention.INSTANCE,
            "JdbcToEnumerableConverterRule")
        // 配置规则工厂:使用构造器引用创建规则实例
        .withRuleFactory(JdbcToEnumerableConverterRule::new)
        // 最终构建规则实例
        .toRule(JdbcToEnumerableConverterRule.class);
  }

  /** Called from the Config. */
  // 受保护的构造方法,由Config对象调用
  // 参数说明:
  //   - Config config: 规则配置对象,包含规则的所有配置信息
  // 方法详细说明:
  //   1. 这是规则的实际构造方法,通过Config对象初始化
  //   2. Config对象包含了规则的所有元数据和配置信息
  //   3. 调用父类ConverterRule的构造方法,传递配置对象
  //   4. 这种设计模式使得规则配置与规则实例创建分离,提高了灵活性
  //   5. 构造方法被标记为protected,表示只能通过工厂方法或子类访问
  protected JdbcToEnumerableConverterRule(Config config) {
    // 调用父类ConverterRule的构造方法,传递配置对象进行初始化
    super(config);
  }

  // 重写ConverterRule的convert方法,执行实际的转换逻辑
  // 参数说明:
  //   - RelNode rel: 要转换的关系表达式节点
  // 返回值说明:
  //   - @Nullable RelNode: 转换后的关系表达式节点,如果无法转换则返回null
  // 方法详细说明:
  //   1. 这是规则的核心方法,负责将JDBC约定的关系表达式转换为Enumerable约定的关系表达式
  //   2. 转换过程包括:
  //      a. 获取原关系表达式的特征集合(RelTraitSet)
  //      b. 将特征集合中的约定替换为目标约定(EnumerableConvention)
  //      c. 创建新的JdbcToEnumerableConverter节点,封装转换后的特征和原始关系表达式
  //   3. RelTraitSet是关系表达式的特征集合,包含约定、排序、分布等物理属性
  //   4. replace方法创建新的特征集合,将约定替换为EnumerableConvention,其他特征保持不变
  //   5. JdbcToEnumerableConverter是实际的转换器节点,它会在执行时将JDBC数据转换为可枚举的数据
  //   6. rel.getCluster()获取关系表达式所在的集群,包含优化器上下文等信息
  //   7. 这种转换不改变关系表达式的逻辑结构,只改变其执行约定
  //   8. 转换后的节点可以被后续的Enumerable规则进一步优化和执行
  @Override public @Nullable RelNode convert(RelNode rel) {
    // 创建新的特征集合,将原特征集合中的约定替换为目标约定(EnumerableConvention)
    // getOutTrait()获取目标约定,即EnumerableConvention.INSTANCE
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutTrait());
    // 创建并返回JdbcToEnumerableConverter节点,封装转换后的特征和原始关系表达式
    // 这个节点会在执行时负责将JDBC数据源的数据转换为可枚举的数据流
    return new JdbcToEnumerableConverter(rel.getCluster(), newTraitSet, rel);
  }
}
