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
package org.apache.calcite.rel.convert; // 转换器相关的包，包含关系表达式转换的实现

import org.apache.calcite.plan.RelOptCluster; // 关系表达式集群，包含共享资源如类型系统和表达式工厂
import org.apache.calcite.plan.RelOptCost; // 关系表达式成本，用于优化器选择最优执行计划
import org.apache.calcite.plan.RelOptPlanner; // 关系表达式优化器，负责选择最优执行计划
import org.apache.calcite.plan.RelTraitDef; // 关系特征定义，如排序、分布等特征的抽象定义
import org.apache.calcite.plan.RelTraitSet; // 关系特征集合，包含一组关系特征的组合
import org.apache.calcite.rel.RelNode; // 关系表达式节点，代表关系代数操作
import org.apache.calcite.rel.SingleRel; // 单子节点关系表达式，只有一个子节点的关系表达式
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 关系元数据查询，用于获取行数、大小等统计信息

import org.checkerframework.checker.nullness.qual.Nullable; // 可空注解，标记可能为null的值

/**
 * Abstract implementation of {@link Converter}. // Converter接口的抽象实现类
 * 
 * 【类的作用】：
 * ConverterImpl是Calcite中关系表达式转换器的抽象基类，用于将一个关系表达式从一种特征集转换为另一种特征集。
 * 
 * 核心功能：
 * 1. 提供转换器的基础实现，所有具体的转换器都应该继承此类
 * 2. 管理输入特征集和输出特征集，确保转换的正确性
 * 3. 提供成本估算方法，帮助优化器选择最优的转换路径
 * 4. 作为SingleRel的子类，确保转换器只有一个子节点（即被转换的关系表达式）
 * 
 * 应用场景：
 * - 将逻辑计划转换为物理计划（如LogicalSort转换为EnumerableSort）
 * - 在不同的调用约定之间转换（如从EnumerableConvention转换为BindableConvention）
 * - 改变关系表达式的分布特征（如从BROADCAST分布转换为HASH分布）
 * 
 * 设计模式：
 * - 模板方法模式：提供通用的转换框架，子类实现具体的转换逻辑
 * - 装饰器模式：在原始关系表达式外层包装转换逻辑，不修改原始表达式
 * 
 * 重要概念：
 * - 特征转换：关系表达式具有不同的特征（如排序、分布、调用约定），转换器负责改变这些特征
 * - 特征集：一组特征的组合，描述关系表达式的物理属性
 * - 特征定义：特征的类型定义，如RelCollationDef（排序特征定义）、RelDistributionDef（分布特征定义）
 * 
 * 使用示例：
 * // 将一个逻辑排序转换为可枚举排序
 * Converter converter = new EnumerableSortConverter(...);
 * RelNode converted = converter.convert(logicalSort);
 * 
 * 继承说明：
 * - 子类必须实现具体的转换逻辑
 * - 子类可以重写computeSelfCost方法以提供更精确的成本估算
 * - 子类通常对应一个特定的规则（Rule），由优化器触发转换
 * 
 * 与优化器的交互：
 * 1. 优化器通过规则匹配发现需要转换的关系表达式
 * 2. 创建对应的Converter实现类实例
 * 3. 调用computeSelfCost估算转换成本
 * 4. 选择成本最低的转换方案
 * 5. 执行转换，生成新的关系表达式
 */
public abstract class ConverterImpl extends SingleRel // 抽象类，继承自SingleRel，只有一个子节点的关系表达式
    implements Converter { // 实现Converter接口，表示这是一个转换器
  //~ Instance fields -------------------------------------------------------- // 实例字段区域标记

  protected final RelTraitSet inTraits; // 输入特征集：存储子节点（被转换的关系表达式）的特征集合，表示转换前的特征
  protected final @Nullable RelTraitDef traitDef; // 特征定义：指定此转换器转换的特征类型（如排序特征、分布特征），如果为null则表示转换所有特征

  //~ Constructors ----------------------------------------------------------- // 构造方法区域标记

  /**
   * Creates a ConverterImpl. // 创建一个ConverterImpl实例
   *
   * @param cluster  planner's cluster // 优化器集群对象，包含类型系统、表达式工厂等共享资源
   * @param traitDef the RelTraitDef this converter converts // 此转换器转换的特征定义，指定转换哪种类型的特征
   * @param traits   the output traits of this converter // 此转换器输出（转换后）的特征集合
   * @param child    child rel (provides input traits) // 子关系表达式，即被转换的关系表达式，提供输入特征集
   */
  protected ConverterImpl( // 受保护的构造方法，供子类调用
      RelOptCluster cluster, // 优化器集群参数
      @Nullable RelTraitDef traitDef, // 特征定义参数，可为null
      RelTraitSet traits, // 输出特征集参数
      RelNode child) { // 子关系表达式参数
    super(cluster, traits, child); // 调用父类SingleRel的构造方法，初始化集群、特征集和子节点
    this.inTraits = child.getTraitSet(); // 从子节点获取输入特征集并保存，记录转换前的特征
    this.traitDef = traitDef; // 保存特征定义，指定此转换器负责转换的特征类型
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域标记

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写成本计算方法，计算此转换器自身的成本
      RelMetadataQuery mq) { // 元数据查询对象，用于获取统计信息
    double dRows = mq.getRowCount(getInput()); // 获取输入关系表达式的行数，作为行数成本的基数
    double dCpu = dRows; // CPU成本等于行数，假设每行处理需要固定CPU时间
    double dIo = 0; // I/O成本为0，因为转换器本身不进行磁盘I/O操作
    return planner.getCostFactory().makeCost(dRows, dCpu, dIo); // 使用优化器的成本工厂创建成本对象并返回
  }

  @Deprecated // to be removed before 2.0 // 已废弃注解，将在2.0版本前移除
  protected Error cannotImplement() { // 创建一个错误对象，表示无法实现转换
    return new AssertionError(getClass() + " cannot convert from " // 创建断言错误，包含类名和输入特征集信息
        + inTraits + " traits"); // 拼接输入特征集信息到错误消息中
  }

  @Override public RelTraitSet getInputTraits() { // 重写获取输入特征集方法
    return inTraits; // 返回保存的输入特征集
  }

  @Override public @Nullable RelTraitDef getTraitDef() { // 重写获取特征定义方法
    return traitDef; // 返回保存的特征定义，可能为null
  }

} // 类结束
