/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // 获得Apache软件基金会(ASF)的许可，根据一个或多个贡献者许可协议
 * contributor license agreements.  See the NOTICE file distributed with  // 参与者许可协议。查看随此工作分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 以获取有关版权所有权的其他信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache许可证2.0版（"许可证"）授予您此文件
 * (the "License"); you may not use this file except in compliance with  // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下网址获取许可证的副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache许可证2.0的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则软件
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证"按原样"分发，不提供任何形式的明示或暗示担保
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 无论是明示的还是暗示的保证或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解特定语言的权限和
 * limitations under the License.  // 许可证下的限制
 */
package org.apache.calcite.rel.convert;  // 定义Converter接口所在的包，org.apache.calcite.rel.convert表示这是Calcite框架中用于关系表达式转换的包

import org.apache.calcite.plan.RelTraitDef;  // 导入RelTraitDef类，用于定义关系表达式的特征（trait）定义
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet类，用于表示关系表达式的一组特征集合
import org.apache.calcite.rel.RelNode;  // 导入RelNode接口，这是Calcite中所有关系表达式（关系代数节点）的基类

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记可能为null的返回值或参数

/**
 * 关系表达式通过实现Converter接口来表示它转换关系表达式的物理属性或特征（trait）
 * A relational expression implements the interface <code>Converter</code> to
 * indicate that it converts a physical attribute, or
 * {@link org.apache.calcite.plan.RelTrait trait}, of a relational expression
 * from one value to another.
 * 【核心概念】：转换器（Converter）是Calcite优化器中用于改变关系表达式物理属性的特殊节点，它将一个关系表达式从一个特征值转换为另一个特征值
 * 【应用场景】：例如将一个普通的扫描转换为排序后的扫描，或者将一个非分布式的执行计划转换为分布式的执行计划
 *
 * <p>有时这种转换是昂贵的；例如，要将非不同的对象流转换为不同的对象流，我们必须克隆输入中的每个对象
 * Sometimes this conversion is expensive; for example, to convert a
 * non-distinct to a distinct object stream, we have to clone every object in
 * the input.
 * 【成本考虑】：转换器可能会引入显著的性能开销，优化器需要在转换带来的收益和成本之间进行权衡
 * 【示例】：去重操作需要复制所有对象，这在数据量大时成本很高
 *
 * <p>转换器不改变被评估的逻辑表达式；转换后，行数和这些行的值仍然相同
 * A converter does not change the logical expression being evaluated; after
 * conversion, the number of rows and the values of those rows will still be the
 * same.
 * 【逻辑等价性】：转换器只改变物理属性，不改变逻辑语义，即转换前后的查询结果完全一致
 * 【重要原则】：转换器必须保证逻辑等价性，不能改变查询的输出结果
 *
 * 通过声明自己是转换器，关系表达式告诉优化器这种等价性，优化器将逻辑等价但具有不同物理特征的表达式分组
 * 到称为<code>RelSet</code>的组中
 * By declaring itself to be a converter, a relational expression is
 * telling the planner about this equivalence, and the planner groups
 * expressions which are logically equivalent but have different physical traits
 * into groups called <code>RelSet</code>s.
 * 【RelSet概念】：RelSet是优化器中用于管理逻辑等价但物理属性不同的关系表达式集合的数据结构
 * 【优化器作用】：优化器通过RelSet可以找到执行相同逻辑查询但具有不同物理属性的所有等价计划，从而选择最优的执行计划
 *
 * <p>原则上，可以设计同时改变多个特征的转换器（例如同时改变排序顺序和关系表达式的物理位置）
 * In principle one could devise converters which change multiple traits
 * simultaneously (say change the sort-order and the physical location of a
 * relational expression).
 * 【多特征转换】：理论上可以同时转换多个特征，但这会增加复杂性
 *
 * 在这种情况下，方法{@link #getInputTraits()}将返回一个{@link org.apache.calcite.plan.RelTraitSet}
 * In which case, the method {@link #getInputTraits()}
 * would return a {@link org.apache.calcite.plan.RelTraitSet}.
 * 【多特征处理的实现】：如果支持多特征转换，getInputTraits()需要返回完整的特征集合
 *
 * 但为了简单起见，此类一次只允许转换一个特征；假定所有其他特征都被保留
 * But for
 * simplicity, this class only allows one trait to be converted at a
 * time; all other traits are assumed to be preserved.
 * 【设计原则】：简化设计，每次只转换一个特征，其他特征保持不变
 * 【实现约束】：转换器必须保留除目标特征之外的所有其他特征
 */
public interface Converter extends RelNode {  // 定义Converter接口，继承自RelNode，表示这是一个关系表达式节点，专门用于特征转换
  //~ Methods ----------------------------------------------------------------  // 方法区域的分隔符注释，表示以下是方法定义部分

  /**
   * 返回输入关系表达式的特征
   Returns the trait of the input relational expression.
   * 【方法作用】：获取转换器输入端的特征集合，这些特征将被转换
   * 【返回值说明】：返回一个RelTraitSet对象，包含输入关系表达式的所有特征
   * 【使用场景】：优化器使用此方法来确定转换器的输入特征，以便应用相应的转换规则
   *
   * @return input trait  // 返回输入关系表达式的特征集合
   */
  RelTraitSet getInputTraits();  // 声明获取输入特征集合的方法，返回RelTraitSet类型

  /**
   * 返回此转换器所工作的特征定义
   Returns the definition of trait which this converter works on.
   * 【方法作用】：获取转换器要修改的特征的定义（RelTraitDef），用于标识转换器具体转换哪种特征
   * 【特征定义】：RelTraitDef是特征的元数据，描述了特征的类型和如何比较特征值
   * 【示例】：如果转换器用于排序，则返回排序特征的定义；如果用于分布，则返回分布特征的定义
   *
   * <p>输入关系表达式（由规则匹配）必须具有此特征，并且具有由{@link #getInputTraits()}给定的值
   * The input relational expression (matched by the rule) must possess
   * this trait and have the value given by {@link #getInputTraits()}, and the
   * traits of the output of this converter given by {@link #getTraitSet()} will
   * have one trait altered and the other orthogonal traits will be the same.
   * 【输入约束】：转换规则匹配的输入关系表达式必须具有getTraitDef()返回的特征类型，并且特征值必须与getInputTraits()中指定的值一致
   * 【输出特征】：转换器输出的特征集合（通过getTraitSet()获取）中，只有一个特征被改变，其他正交特征保持不变
   * 【正交特征概念】：正交特征是指相互独立的特征，一个特征的改变不影响其他特征，例如排序和分布是正交的
   * 【转换保证】：转换器保证除了目标特征外，所有其他特征值都保持不变
   *
   * @return trait which this converter modifies  // 返回此转换器修改的特征定义，可能为null
   */
  @Nullable RelTraitDef getTraitDef();  // 声明获取特征定义的方法，使用@Nullable注解表示返回值可能为null

  /**
   * 返回唯一的输入关系表达式
   Returns the sole input relational expression.
   * 【方法作用】：获取转换器的输入子节点，即被转换的关系表达式
   * 【输入节点】：转换器通常只有一个输入节点，因为它的作用是对单个关系表达式进行特征转换
   * 【子节点关系】：这是关系表达式树中的边，连接转换器节点和它的输入节点
   * 【访问模式】：优化器通过此方法遍历关系表达式树，了解查询计划的结构
   *
   * @return child relational expression  // 返回子关系表达式节点
   */
  RelNode getInput();  // 声明获取输入关系表达式的方法，返回RelNode类型
}  // Converter接口定义结束，花括号表示接口声明的结束
