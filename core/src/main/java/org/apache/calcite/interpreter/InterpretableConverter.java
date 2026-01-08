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
 */ // Apache许可证声明，指定开源协议和版权信息
package org.apache.calcite.interpreter; // 声明包名，该类属于org.apache.calcite.interpreter包，是Calcite解释器模块的核心包

import org.apache.calcite.DataContext; // 导入DataContext接口，用于提供执行SQL查询所需的上下文环境（如数据源、参数等）
import org.apache.calcite.linq4j.Enumerable; // 导入Enumerable接口，表示可枚举的数据集合，类似于Java 8的Stream，用于延迟计算和迭代数据
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef类，定义关系表达式调用约定（Convention）的特征定义，用于描述不同数据访问方式的约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，关系优化集群，包含查询优化所需的全局信息（如RexBuilder、类型工厂等）
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，关系特征集合，定义关系表达式的物理属性（如调用约定、排序、分区等）
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，关系表达式的基类，表示关系代数中的操作（如扫描、过滤、投影、连接等）
import org.apache.calcite.rel.convert.ConverterImpl; // 导入ConverterImpl抽象类，关系表达式转换器的基类，用于将一个调用约定转换为另一个调用约定
import org.apache.calcite.runtime.ArrayBindable; // 导入ArrayBindable接口，表示可以绑定到数据上下文并返回Object[]数组的可绑定对象

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值，帮助静态分析工具进行空值检查

import java.util.List; // 导入List接口，用于表示有序集合，这里用于存储关系表达式的输入列表

/**
 * Relational expression that converts any relational expression input to
 * {@link org.apache.calcite.interpreter.InterpretableConvention}, by wrapping
 * it in an interpreter.
 */ // 类文档注释：这个类是一个关系表达式，用于将任意输入的关系表达式转换为InterpretableConvention（可解释约定），通过将其包装在解释器中实现
public class InterpretableConverter extends ConverterImpl // 声明InterpretableConverter类，继承自ConverterImpl，表示这是一个将关系表达式转换为可解释约定的转换器
    implements ArrayBindable { // 实现ArrayBindable接口，表示这个类可以绑定到数据上下文并返回Object[]数组，使其可以作为数据源进行查询
  protected InterpretableConverter(RelOptCluster cluster, RelTraitSet traits, // 构造方法：初始化InterpretableConverter，参数cluster是关系优化集群，traits是关系特征集合
      RelNode input) { // 参数input是输入的关系表达式，即需要被转换的源关系表达式
    super(cluster, ConventionTraitDef.INSTANCE, traits, input); // 调用父类ConverterImpl的构造方法，传入集群、约定特征定义实例、特征集合和输入关系表达式
  } // 构造方法结束，完成初始化

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于创建当前关系表达式的副本，参数traitSet是新的特征集合，inputs是新的输入列表
    return new InterpretableConverter(getCluster(), traitSet, sole(inputs)); // 返回一个新的InterpretableConverter实例，使用当前集群、新特征集合和输入列表中的唯一输入
  } // copy方法结束，返回关系表达式的副本

  @Override public Class<Object[]> getElementType() { // 重写getElementType方法，返回Enumerable中元素的类型
    return Object[].class; // 返回Object[].class，表示每个元素是一个对象数组（即一行数据）
  } // getElementType方法结束，返回元素类型

  @Override public Enumerable<@Nullable Object[]> bind(DataContext dataContext) { // 重写bind方法，将当前表达式绑定到数据上下文，返回可枚举的数据集合，参数dataContext是数据上下文
    return new Interpreter(dataContext, getInput()); // 创建并返回一个新的Interpreter实例，传入数据上下文和输入关系表达式，Interpreter会解释执行输入关系表达式
  } // bind方法结束，返回可枚举的数据集合
} // InterpretableConverter类定义结束
