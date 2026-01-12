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
 */ // Apache许可证声明，指定版权和使用条款
// 这是Apache软件基金会的标准许可证头，声明了该代码的版权归属和使用许可
// Apache License 2.0是一个宽松的开源许可证，允许自由使用、修改和分发代码
// 用户可以自由地使用这个代码，无论是商业用途还是非商业用途
// 但需要保留原始的版权声明和许可证声明
// 如果修改了代码，需要明确标注修改的部分
// 该许可证不提供任何担保，使用风险由用户自行承担
package org.apache.calcite.adapter.innodb; // 声明包名，该类属于org.apache.calcite.adapter.innodb包
// org.apache.calcite.adapter.innodb包包含了Calcite的InnoDB适配器相关类
// InnoDB适配器允许Calcite通过JDBC连接到MySQL/InnoDB数据库，并执行SQL查询
// 这个包中的类包括InnodbRel约定、InnodbToEnumerableConverterRule转换规则、InnodbToEnumerableConverter转换节点等
// 通过这个适配器，Calcite可以将SQL查询优化后转换为针对InnoDB数据库的查询，并获取结果

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention接口，表示可枚举的约定，用于生成可执行的Java代码
// EnumerableConvention是Calcite中最重要的约定之一，它表示关系表达式可以被转换为Java代码并执行
// 实现了这个约定的关系表达式可以生成Enumerable<Row>类型的结果，可以被迭代处理
// 这个约定通常用于物理计划的最后阶段，将逻辑计划转换为可执行的代码
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
// RelTraitSet是一组RelTrait的集合，每个RelTrait表示关系表达式的一个特征，如约定、排序、分布等
// 特征集合在优化过程中非常重要，优化器根据特征集合来决定哪些规则可以应用
// 例如，只有当关系表达式的约定匹配时，相应的转换规则才能应用
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点，是Calcite中所有关系代数操作的基类
// RelNode是Calcite中关系代数操作的抽象表示，如TableScan、Filter、Project、Join等都是RelNode的子类
// 每个RelNode代表一个关系代数操作，包含输入节点、输出行类型、特征集合等信息
// RelNode通过树状结构组织起来，形成完整的查询计划树
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule抽象类，表示转换规则，用于将一个关系表达式从一个约定转换为另一个约定
// ConverterRule是RelOptRule的子类，专门用于处理约定转换
// 它定义了输入约定和输出约定，当优化器发现一个关系表达式的约定匹配输入约定时，就会应用这个规则
// 转换规则是Calcite优化器规则体系的重要组成部分，用于将逻辑计划逐步转换为物理计划

/**
 * Rule to convert a relational expression from
 * {@link InnodbRel#CONVENTION} to {@link EnumerableConvention}.
 */ // 类文档注释：该类是一个转换规则，用于将关系表达式从InnodbRel约定转换为EnumerableConvention约定
// InnodbRel约定表示使用InnoDB存储引擎的关系表达式，而EnumerableConvention表示可以枚举执行的关系表达式
// 这个规则是Calcite优化器规则体系的一部分，用于将逻辑计划转换为可执行的物理计划
// 在Calcite的优化过程中，关系表达式会经历多个阶段的转换，从逻辑计划到物理计划
// InnodbToEnumerableConverterRule是最后阶段的转换之一，它将使用InnoDB存储引擎的逻辑表达式转换为可以实际执行的可枚举表达式
// 转换后的InnodbToEnumerableConverter节点可以生成Java代码，通过JDBC连接到MySQL/InnoDB数据库执行查询
// 这个规则通常在VolcanoPlanner或HepPlanner等优化器中被触发，当优化器发现一个关系表达式的约定是InnodbRel.CONVENTION
// 并且需要将其转换为EnumerableConvention时，就会应用这个规则
// 转换过程保持了原始关系表达式的语义不变，只是改变了执行约定，使其可以被枚举执行
public class InnodbToEnumerableConverterRule extends ConverterRule { // 定义InnodbToEnumerableConverterRule类，继承自ConverterRule，表示这是一个转换规则

  /** Default configuration. */ // 成员变量注释：DEFAULT_CONFIG是默认配置对象，用于定义这个转换规则的配置信息
  // 它是Config类型的静态常量，使用Config.INSTANCE作为基础配置
  // withConversion方法指定了转换规则的具体参数：
  //   - RelNode.class: 表示这个规则可以应用于任何RelNode类型的关系表达式
  //   - InnodbRel.CONVENTION: 表示输入关系表达式的约定是InnoDB约定
  //   - EnumerableConvention.INSTANCE: 表示输出关系表达式的约定是可枚举约定
  //   - "InnodbToEnumerableConverterRule": 表示这个规则的名称，用于调试和日志记录
  // withRuleFactory方法指定了创建规则实例的工厂方法，使用方法引用InnodbToEnumerableConverterRule::new
  // 这个配置对象在运行时会被优化器用来创建规则实例
  public static final Config DEFAULT_CONFIG = Config.INSTANCE // 声明静态常量DEFAULT_CONFIG，使用Config.INSTANCE作为基础配置
      .withConversion(RelNode.class, InnodbRel.CONVENTION, // 调用withConversion方法，指定转换的输入类型为RelNode，输入约定为InnodbRel.CONVENTION
          EnumerableConvention.INSTANCE, "InnodbToEnumerableConverterRule") // 指定输出约定为EnumerableConvention.INSTANCE，规则名称为"InnodbToEnumerableConverterRule"
      .withRuleFactory(InnodbToEnumerableConverterRule::new); // 指定规则工厂方法，使用InnodbToEnumerableConverterRule的构造函数创建实例

  /** Creates a InnodbToEnumerableConverterRule. */
  // 构造方法注释：这是InnodbToEnumerableConverterRule的受保护构造方法
  // 参数config是配置对象，包含了规则的所有配置信息，包括转换规则、工厂方法等
  // 构造方法调用父类ConverterRule的构造方法，传入配置对象，完成规则的初始化
  // 使用protected修饰符表示这个构造方法只能被同包或子类访问，通常通过工厂方法创建实例
  protected InnodbToEnumerableConverterRule(Config config) { // 声明构造方法，接收Config类型的参数config
    super(config); // 调用父类ConverterRule的构造方法，传入配置对象，完成父类的初始化
  }

  @Override public RelNode convert(RelNode rel) { // 重写ConverterRule的convert方法，这是转换规则的核心方法，用于执行实际的转换操作
    // 参数rel是要转换的输入关系表达式节点，它的约定是InnodbRel.CONVENTION
    // 方法返回值是转换后的关系表达式节点，它的约定是EnumerableConvention.INSTANCE
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutConvention()); // 获取输入关系表达式的特征集合，并将其中约定特征替换为输出约定（EnumerableConvention）
    // rel.getTraitSet()获取输入关系表达式的特征集合，包含了所有特征（如约定、排序、分布等）
    // replace(getOutConvention())将特征集合中的约定特征替换为输出约定，其他特征保持不变
    // getOutConvention()返回这个规则配置的输出约定，即EnumerableConvention.INSTANCE
    // 这样创建的newTraitSet包含了输入关系表达式的所有特征，但约定已经从InnoDB约定转换为可枚举约定
    return new InnodbToEnumerableConverter(rel.getCluster(), newTraitSet, rel); // 创建并返回一个新的InnodbToEnumerableConverter节点，完成转换
    // InnodbToEnumerableConverter是转换后的物理节点，它包装了原始的InnoDB关系表达式
    // 构造方法接收三个参数：
    //   - rel.getCluster(): 获取输入关系表达式所在的集群信息，包含类型系统、表达式工厂等共享信息
    //   - newTraitSet: 使用新的特征集合，其中约定已经替换为可枚举约定
    //   - rel: 原始的输入关系表达式，作为子节点被包装
    // 返回的InnodbToEnumerableConverter节点可以在运行时生成Java代码，通过JDBC查询InnoDB数据库并返回可枚举的结果集
  }
}
