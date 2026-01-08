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
package org.apache.calcite.interpreter; // 声明当前类所在的包，属于interpreter（解释器）包，该包负责Calcite的解释执行功能

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系表达式的调用约定（Convention），描述如何执行关系代数操作
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于存储关系节点的特征集合（trait set），如调用约定、排序方式等
import org.apache.calcite.rel.RelNode; // 导入RelNode类，这是所有关系代数操作节点的基类，代表关系表达式树中的一个节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule基类，所有转换规则的基类，用于将一个Convention转换为另一个Convention

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值，帮助静态分析工具进行空指针检查

/**
 * Rule to convert a relational expression from
 * {@link org.apache.calcite.plan.Convention#NONE}
 * to {@link org.apache.calcite.interpreter.BindableConvention}.
 * 这是一个转换规则类，用于将关系表达式从NONE约定转换为BindableConvention约定
 * NONE约定表示关系表达式还没有确定具体的执行方式，BindableConvention表示可以通过解释器绑定执行
 * 这个规则是Calcite优化器规则体系的一部分，用于在查询优化过程中将关系表达式转换为可执行的解释器形式
 *
 * @see Bindables#FROM_NONE_RULE // 参见Bindables类中的FROM_NONE_RULE常量，那里定义了该规则的使用方式
 */
public class NoneToBindableConverterRule extends ConverterRule { // 定义类名，继承自ConverterRule，表示这是一个转换规则
  /** Default configuration. */
  // 定义默认配置常量DEFAULT_CONFIG，这是一个静态final成员变量，规则配置的默认值
  // Config.INSTANCE是ConverterRule.Config的默认实例，withConversion方法配置转换规则的具体参数
  // RelNode.class表示该规则适用于所有类型的关系节点
  // Convention.NONE表示输入的Convention为NONE（未确定执行方式）
  // BindableConvention.INSTANCE表示输出的Convention为BindableConvention（可绑定执行）
  // "NoneToBindableConverterRule"是规则的描述名称
  // withRuleFactory方法指定了创建规则实例的工厂方法，使用方法引用NoneToBindableConverterRule::new
  public static final Config DEFAULT_CONFIG = Config.INSTANCE
      .withConversion(RelNode.class, Convention.NONE,
          BindableConvention.INSTANCE, "NoneToBindableConverterRule")
      .withRuleFactory(NoneToBindableConverterRule::new);

  /** Called from the Config. */
  // 定义受保护的构造方法，接收Config参数，从配置对象创建规则实例
  // 这个构造方法会被Config对象通过反射或工厂方法调用
  // super(config)调用父类ConverterRule的构造方法，将配置传递给父类进行初始化
  protected NoneToBindableConverterRule(Config config) {
    super(config);
  }

  // 重写父类ConverterRule的convert方法，这是转换规则的核心方法，负责执行实际的转换逻辑
  // @Nullable注解表示返回值可能为null，如果转换失败则返回null
  // rel参数是待转换的关系节点，输入Convention为NONE
  // 该方法将NONE约定的关系节点转换为BindableConvention约定的InterpretableConverter节点
  @Override public @Nullable RelNode convert(RelNode rel) {
    // 创建新的特征集合，通过调用rel.getTraitSet()获取当前节点的特征集合
    // replace方法将特征集合中的Convention替换为输出Convention（即BindableConvention）
    // 这样新的特征集合就包含了BindableConvention，表示该节点可以通过解释器执行
    RelTraitSet newTraitSet = rel.getTraitSet().replace(getOutConvention());
    // 创建并返回InterpretableConverter节点，这是转换后的可解释执行节点
    // rel.getCluster()获取关系节点的集群信息，包含类型系统、表达式工厂等共享资源
    // newTraitSet是新的特征集合，包含BindableConvention
    // rel是原始的关系节点，作为InterpretableConverter的子节点
    // InterpretableConverter是一个包装节点，它将原始节点包装成可解释执行的形式
    return new InterpretableConverter(rel.getCluster(), newTraitSet, rel);
  }
}
