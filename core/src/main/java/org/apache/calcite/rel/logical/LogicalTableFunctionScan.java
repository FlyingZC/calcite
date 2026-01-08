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
package org.apache.calcite.rel.logical; // 声明包名，该类位于org.apache.calcite.rel.logical包下，是逻辑关系表达式包

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系表达式的调用约定，如逻辑层约定、物理层约定等
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式优化集群，包含所有关系表达式共享的元数据
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系表达式执行的成本，用于优化器比较不同执行计划
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示关系表达式优化器，用于选择最优的执行计划
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合，如约定、排序规则等
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化输入创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式的基接口，所有关系表达式都实现该接口
import org.apache.calcite.rel.core.TableFunctionScan; // 导入TableFunctionScan类，这是LogicalTableFunctionScan的父类，表示表函数扫描操作
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示关系表达式的提示信息，用于指导优化器行为
import org.apache.calcite.rel.metadata.RelColumnMapping; // 导入RelColumnMapping类，表示关系表达式的列映射信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据信息
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，描述表或查询结果的行类型
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式节点，用于构建SQL表达式树

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数或返回值

import java.lang.reflect.Type; // 导入Type类，用于表示Java类型
import java.util.Collections; // 导入Collections工具类，提供集合操作的工具方法
import java.util.List; // 导入List接口，表示有序集合
import java.util.Set; // 导入Set接口，表示无序不重复集合

/**
 * Sub-class of {@link org.apache.calcite.rel.core.TableFunctionScan}
 * not targeted at any particular engine or calling convention.
 * TableFunctionScan的子类，不针对特定的引擎或调用约定，表示逻辑层的表函数扫描操作
 * 表函数是一种特殊的SQL函数，可以返回一个表（多行多列），例如：SELECT * FROM TABLE(generate_series(1, 10))
 * LogicalTableFunctionScan表示在逻辑计划阶段的表函数扫描节点，在优化过程中会被转换为物理实现
 */
public class LogicalTableFunctionScan extends TableFunctionScan { // 声明LogicalTableFunctionScan类，继承自TableFunctionScan，表示逻辑层的表函数扫描节点
  //~ Constructors ----------------------------------------------------------- // 构造方法部分的分隔标记

  /**
   * Creates a <code>LogicalTableFunctionScan</code>.
   * 创建一个LogicalTableFunctionScan实例，这是最完整的构造方法，包含所有参数
   *
   * @param cluster        Cluster that this relational expression belongs to // 关系表达式所属的集群，包含优化器、类型系统等共享资源
   * @param hints          The hints of this node. // 该节点的提示信息，用于指导优化器的决策
   * @param inputs         0 or more relational inputs // 0个或多个关系输入，表函数可能需要其他表作为输入参数
   * @param traitSet       Trait set // 特征集合，定义该关系表达式的属性，如调用约定、排序规则等
   * @param rexCall        Function invocation expression // 函数调用表达式，表示调用的表函数及其参数
   * @param elementType    Element type of the collection that will implement // 实现该表的集合的元素类型，用于Java代码生成
   *                       this table // 例如：如果表函数返回List<Row>，则elementType为Row.class
   * @param rowType        Row type produced by function // 函数产生的行类型，描述表函数返回的列名和类型
   * @param columnMappings Column mappings associated with this function // 与该函数关联的列映射，用于将输出列映射到输入列
   */
  public LogicalTableFunctionScan( // 构造方法声明，创建LogicalTableFunctionScan实例
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      List<RelHint> hints, // 参数：提示信息列表
      List<RelNode> inputs, // 参数：输入关系表达式列表
      RexNode rexCall, // 参数：函数调用表达式
      @Nullable Type elementType, RelDataType rowType, // 参数：元素类型（可为null）和行类型
      @Nullable Set<RelColumnMapping> columnMappings) { // 参数：列映射集合（可为null）
    super(cluster, traitSet, hints, inputs, rexCall, elementType, rowType, // 调用父类TableFunctionScan的构造方法，传递所有参数
        columnMappings); // 继续传递列映射参数
  }

  /**
   * Creates a <code>LogicalTableFunctionScan</code>.
   * 创建一个LogicalTableFunctionScan实例，不包含hints参数的简化构造方法
   *
   * @param cluster        Cluster that this relational expression belongs to // 关系表达式所属的集群
   * @param inputs         0 or more relational inputs // 0个或多个关系输入
   * @param traitSet       Trait set // 特征集合
   * @param rexCall        Function invocation expression // 函数调用表达式
   * @param elementType    Element type of the collection that will implement // 实现该表的集合的元素类型
   *                       this table
   * @param rowType        Row type produced by function // 函数产生的行类型
   * @param columnMappings Column mappings associated with this function // 与该函数关联的列映射
   */
  public LogicalTableFunctionScan( // 构造方法声明，不包含hints的版本
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      List<RelNode> inputs, // 参数：输入关系表达式列表
      RexNode rexCall, // 参数：函数调用表达式
      @Nullable Type elementType, RelDataType rowType, // 参数：元素类型和行类型
      @Nullable Set<RelColumnMapping> columnMappings) { // 参数：列映射集合
    this(cluster, traitSet, Collections.emptyList(), inputs, rexCall, elementType, rowType, // 调用完整构造方法，hints参数使用空列表
        columnMappings); // 继续传递列映射参数
  }

  @Deprecated // to be removed before 2.0 // 标记为已过时，将在2.0版本前移除
  public LogicalTableFunctionScan( // 已过时的构造方法，不包含traitSet参数
      RelOptCluster cluster, // 参数：关系表达式集群
      List<RelNode> inputs, // 参数：输入关系表达式列表
      RexNode rexCall, // 参数：函数调用表达式
      @Nullable Type elementType, RelDataType rowType, // 参数：元素类型和行类型
      @Nullable Set<RelColumnMapping> columnMappings) { // 参数：列映射集合
    this(cluster, cluster.traitSetOf(Convention.NONE), inputs, rexCall, // 调用其他构造方法，自动创建traitSet，使用NONE约定
        elementType, rowType, columnMappings); // 传递其他参数
  }

  /**
   * Creates a LogicalTableFunctionScan by parsing serialized output.
   * 通过解析序列化输出来创建LogicalTableFunctionScan，用于从JSON等格式恢复关系表达式
   */
  public LogicalTableFunctionScan(RelInput input) { // 构造方法声明，接受RelInput参数
    super(input); // 调用父类构造方法，从RelInput中解析并初始化所有字段
  }

  /** Creates a LogicalTableFunctionScan. // 创建LogicalTableFunctionScan的静态工厂方法
   * 这是一个便捷的工厂方法，自动设置traitSet为逻辑约定（Convention.NONE）
   */
  public static LogicalTableFunctionScan create( // 静态工厂方法声明
      RelOptCluster cluster, // 参数：关系表达式集群
      List<RelNode> inputs, // 参数：输入关系表达式列表
      RexNode rexCall, // 参数：函数调用表达式
      @Nullable Type elementType, RelDataType rowType, // 参数：元素类型和行类型
      @Nullable Set<RelColumnMapping> columnMappings) { // 参数：列映射集合
    final RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建特征集合，使用NONE约定表示逻辑层
    return new LogicalTableFunctionScan(cluster, traitSet, inputs, rexCall, // 创建并返回LogicalTableFunctionScan实例
        elementType, rowType, columnMappings); // 传递所有参数
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分的分隔标记

  @Override public LogicalTableFunctionScan copy( // 重写copy方法，用于复制关系表达式并可能修改部分属性
      RelTraitSet traitSet, // 参数：新的特征集合
      List<RelNode> inputs, // 参数：新的输入关系表达式列表
      RexNode rexCall, // 参数：新的函数调用表达式
      @Nullable Type elementType, // 参数：新的元素类型
      RelDataType rowType, // 参数：新的行类型
      @Nullable Set<RelColumnMapping> columnMappings) { // 参数：新的列映射集合
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言traitSet包含NONE约定（如果是适用于约定的特征）
    return new LogicalTableFunctionScan( // 创建并返回新的LogicalTableFunctionScan实例
        getCluster(), // 使用当前集群
        traitSet, // 使用新的特征集合
        inputs, // 使用新的输入列表
        rexCall, // 使用新的函数调用表达式
        elementType, // 使用新的元素类型
        rowType, // 使用新的行类型
        columnMappings); // 使用新的列映射集合
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算该关系表达式的执行成本
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于获取额外的元数据信息
    // REVIEW jvs 8-Jan-2006:  what is supposed to be here // 注释：需要审查，对于抽象关系节点应该返回什么成本
    // for an abstract rel? // 这是一个逻辑层节点，成本由具体的物理实现决定
    return planner.getCostFactory().makeHugeCost(); // 返回一个巨大的成本值，表示逻辑节点本身没有实际成本，优化器会优先选择物理实现
  }

  @Override public RelNode withHints( // 重写withHints方法，为关系表达式添加提示信息
      final List<RelHint> hintList) { // 参数：新的提示信息列表
    return new LogicalTableFunctionScan(getCluster(), getTraitSet(), hintList, // 创建新的实例，使用新的提示列表，其他属性保持不变
        getInputs(), getCall(), getElementType(), getRowType(), columnMappings); // 传递当前实例的输入、调用、元素类型、行类型和列映射
  }
} // 类定义结束
