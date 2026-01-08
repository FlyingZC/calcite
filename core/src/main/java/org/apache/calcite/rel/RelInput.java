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
package org.apache.calcite.rel; // 定义包名，表示该接口属于org.apache.calcite.rel包，这是Calcite中关系表达式(RelNode)的核心包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式簇，包含共享的元数据和优化器上下文
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化器中的表对象，包含表的元数据信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合，如物理特性、排序特性等
import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类，表示聚合函数调用，如SUM、COUNT等
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，描述行的结构
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示行表达式中的字面量常量
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式节点，是所有行表达式的基类
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合，常用于表示列的索引集合

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，表示值可能为null

import java.math.BigDecimal; // 导入BigDecimal类，表示高精度的十进制数
import java.util.List; // 导入List接口，表示有序集合

/**
 * Context from which a relational expression can initialize itself, // 表示这是一个上下文接口，关系表达式可以从中初始化自己
 * reading from a serialized form of the relational expression. // 通过从关系表达式的序列化形式中读取数据来完成初始化
 * 
 * RelInput接口的作用：// 详细说明接口的作用
 * 1. 提供关系表达式反序列化的标准接口，允许从序列化形式重建RelNode对象
 * 2. 在RelNode的创建过程中，提供访问各种属性和子节点的方法
 * 3. 支持JSON、XML等序列化格式的解析，实现关系表达式的持久化和传输
 * 4. 是RelNode工厂模式的核心接口，允许动态创建任意类型的关系表达式
 * 5. 在查询优化器的规则匹配和转换过程中，用于重建修改后的关系表达式树
 * 
 * 使用场景：// 说明主要使用场景
 * - 从JSON/XML等序列化格式加载关系表达式
 * - 在查询优化过程中重建修改后的关系表达式
 * - 实现关系表达式的跨进程传输
 * - 支持查询计划的缓存和恢复
 */
public interface RelInput { // 定义RelInput接口，这是一个公共接口，用于提供关系表达式初始化所需的上下文信息
  RelOptCluster getCluster(); // 获取关系表达式簇(RelOptCluster)，包含共享的元数据、类型系统和优化器上下文，每个RelNode必须属于一个Cluster

  RelTraitSet getTraitSet(); // 获取关系表达式的特征集合(RelTraitSet)，包含物理特性(如Convention)、排序特性(Collation)、分布特性(Distribution)等

  RelOptTable getTable(String table); // 根据表名获取优化器表对象(RelOptTable)，参数table是表的标识符，返回的表对象包含表的元数据、统计信息等

  /**
   * Returns the input relational expression. Throws if there is not precisely // 返回单个输入关系表达式，如果输入数量不正好是一个则抛出异常
   * one input. // 说明该方法期望只有一个输入，多于或少于一个都会抛出异常
   */
  RelNode getInput(); // 获取单个输入关系表达式(RelNode)，用于只有一个输入的算子(如Filter、Project等)，如果输入数量不是1则抛出异常

  List<RelNode> getInputs(); // 获取所有输入关系表达式列表(List<RelNode>)，用于有多个输入的算子(如Join、Union等)，返回输入节点的有序列表

  /**
   * Returns an expression. // 返回一个行表达式(RexNode)
   */
  @Nullable RexNode getExpression(String tag); // 根据标签(tag)获取行表达式(RexNode)，tag是序列化数据中的键名，返回对应的表达式节点，可能为null

  ImmutableBitSet getBitSet(String tag); // 根据标签(tag)获取不可变位集合(ImmutableBitSet)，常用于表示列索引集合(如GROUP BY的列、聚合的列等)

  @Nullable List<ImmutableBitSet> getBitSetList(String tag); // 根据标签(tag)获取不可变位集合列表(List<ImmutableBitSet>)，可能为null，用于表示多个列集合(如多重GROUP BY)

  List<AggregateCall> getAggregateCalls(String tag); // 根据标签(tag)获取聚合调用列表(List<AggregateCall>)，包含所有聚合函数调用(如SUM、COUNT、AVG等)的详细信息

  @Nullable Object get(String tag); // 根据标签(tag)获取任意类型的对象(Object)，可能为null，用于获取序列化数据中的原始值

  /**
   * Returns a {@code string} value. // 返回字符串值
   * Throws if wrong type, returns null if not present. // 如果类型错误则抛出异常，如果不存在则返回null
   */
  @Nullable String getString(String tag); // 根据标签(tag)获取字符串值，可能为null，如果值存在但不是字符串类型则抛出异常

  /**
   * Returns a {@code float} value. // 返回浮点数值
   * Throws if not present or wrong type. // 如果不存在或类型错误则抛出异常
   */
  float getFloat(String tag); // 根据标签(tag)获取浮点数值(float)，如果值不存在或不是浮点数类型则抛出异常

  /**
   * Returns a {@code BigDecimal} value. // 返回BigDecimal值
   * Throws if not present or wrong type. // 如果不存在或类型错误则抛出异常
   */
  BigDecimal getBigDecimal(String tag); // 根据标签(tag)获取高精度十进制数(BigDecimal)，用于精确的数值计算，如果值不存在或不是BigDecimal类型则抛出异常

  /**
   * Returns an enum value. Throws if not a valid member. // 返回枚举值，如果不是有效的枚举成员则抛出异常
   */
  <E extends Enum<E>> @Nullable E getEnum(String tag, Class<E> enumClass); // 根据标签(tag)和枚举类型(enumClass)获取枚举值，泛型E必须是枚举类型，可能返回null

  @Nullable List<RexNode> getExpressionList(String tag); // 根据标签(tag)获取行表达式列表(List<RexNode>)，可能为null，用于获取多个表达式(如Project中的表达式列表)

  @Nullable List<String> getStringList(String tag); // 根据标签(tag)获取字符串列表(List<String>)，可能为null，用于获取多个字符串值(如列名列表)

  @Nullable List<Integer> getIntegerList(String tag); // 根据标签(tag)获取整数列表(List<Integer>)，可能为null，用于获取多个整数值(如列索引列表)

  @Nullable List<List<Integer>> getIntegerListList(String tag); // 根据标签(tag)获取整数列表的列表(List<List<Integer>>)，可能为null，用于获取二维整数数组

  RelDataType getRowType(String tag); // 根据标签(tag)获取行数据类型(RelDataType)，描述行的结构(包含列名和列类型)，用于定义输出行的schema

  RelDataType getRowType(String expressionsTag, String fieldsTag); // 根据表达式标签和字段标签获取行数据类型(RelDataType)，从表达式列表和字段列表构造行类型

  RelCollation getCollation(); // 获取排序特性(RelCollation)，描述数据的排序顺序，包含排序字段和排序方向(ASC/DESC)

  RelDistribution getDistribution(); // 获取分布特性(RelDistributiion)，描述数据的分布方式(如SINGLETON、HASH、RANGE_DISTRIBUTED、ROUND_ROBIN等)

  ImmutableList<ImmutableList<RexLiteral>> getTuples(String tag); // 根据标签(tag)获取元组列表，每个元组是RexLiteral的不可变列表，用于表示Values算子中的常量行数据

  boolean getBoolean(String tag, boolean default_); // 根据标签(tag)获取布尔值，如果标签不存在则返回默认值default_，用于获取可选的布尔配置项
}
