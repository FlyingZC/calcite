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
 */ // Apache许可证2.0声明：允许自由使用、修改和分发，但需保留版权声明和许可证文本
package org.apache.calcite.rel.logical; // 声明包路径，LogicalTableScan类属于逻辑关系表达式的逻辑包，用于表示逻辑层面的表扫描操作

import org.apache.calcite.plan.Convention; // 导入Convention枚举，定义关系代数表达式的调用约定（如逻辑约定、物理约定等）
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数表达式的集群，包含类型系统和表达式工厂等共享资源
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化器视角下的表，包含表的元数据和统计信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系代数表达式的特征集合，如约定、排序、分区等物理属性
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，定义排序特征，用于描述数据的排序方式
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化数据中重建关系代数表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，所有关系代数表达式的基础接口，定义了关系代数树的基本行为
import org.apache.calcite.rel.core.TableScan; // 导入TableScan抽象类，表示表扫描操作的基类，LogicalTableScan继承此类
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示关系代数表达式的提示信息，用于优化器指导
import org.apache.calcite.schema.Table; // 导入Table接口，表示Calcite中的表定义，包含表的元数据和访问方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，提供不可变列表实现，确保线程安全

import java.util.List; // 导入Java标准库的List接口，用于存储有序的元素集合

/**
 * LogicalTableScan（逻辑表扫描）是一个关系代数表达式，用于从{@link RelOptTable}中读取所有行数据。
 *
 * <p>这个类是Calcite查询优化器中表示表扫描操作的逻辑节点。它位于关系代数树的叶子位置，
 * 代表数据查询的起点，即从表中读取原始数据。作为逻辑节点，它不关心具体的物理实现方式，
 * 只描述"需要扫描某个表"这一逻辑操作。
 *
 * <p>如果表是JDBC表（如<code>net.sf.saffron.ext.JdbcTable</code>），那么直接读取所有行是可行的。
 * 但对于其他类型的表，可能存在多种方式从表中读取数据。对于某些类型的表，甚至可能无法
 * 读取所有行，除非应用某些约束条件来缩小范围。
 *
 * <p>以<code>net.sf.saffron.ext.ReflectSchema</code>模式为例，
 *
 * <blockquote>
 * <pre>select from fields</pre>
 * </blockquote>
 *
 * <p>这个查询无法实现，因为反射模式下的字段集合可能过于庞大或无限，
 * 但是
 *
 * <blockquote>
 * <pre>select from fields as f
 * where f.getClass().getName().equals("java.lang.String")</pre>
 * </blockquote>
 *
 * <p>这个查询可以实现，因为添加了类型过滤约束。优化器的责任就是通过应用转换规则
 * 来找到这些可行的实现方式。LogicalTableScan会根据表的统计信息和特征，
 * 被优化器转换为特定的物理实现（如EnumerableTableScan、JdbcTableScan等）。
 */
public final class LogicalTableScan extends TableScan { // 定义LogicalTableScan类，继承自TableScan基类，使用final修饰防止被继承
  //~ Constructors ----------------------------------------------------------- // 构造函数区域的分隔标记，表示以下为构造函数部分

  /**
   * 创建一个LogicalTableScan实例。
   *
   * <p>这是主要的构造函数，用于创建逻辑表扫描节点。除非你清楚自己在做什么，
   * 否则建议使用静态工厂方法{@link #create}来创建实例，因为该方法会自动
   * 处理特征集（trait set）的初始化。
   *
   * @param cluster 关系代数集群，包含类型系统、表达式工厂等共享资源，所有关系代数节点都共享同一个cluster
   * @param traitSet 特征集，定义了这个节点的物理属性（如调用约定、排序方式等），对于LogicalTableScan通常包含Convention.NONE
   * @param hints 提示列表，包含优化器提示信息，用于指导查询优化过程
   * @param table 要扫描的表，以RelOptTable形式表示，包含表的元数据、统计信息和访问方法
   */
  public LogicalTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数参数：cluster-关系代数集群，traitSet-特征集
      List<RelHint> hints, RelOptTable table) { // 构造函数参数：hints-提示列表，table-要扫描的表
    super(cluster, traitSet, hints, table); // 调用父类TableScan的构造函数，初始化表扫描的基本属性
  } // 构造函数结束

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本之前移除，建议使用带hints参数的构造函数
  public LogicalTableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数参数：cluster-关系代数集群，traitSet-特征集
      RelOptTable table) { // 构造函数参数：table-要扫描的表
    this(cluster, traitSet, ImmutableList.of(), table); // 调用主构造函数，传入空的不可变列表作为hints参数
  } // 构造函数结束，这是一个向后兼容的构造函数，不提供hints功能

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本之前移除，建议使用带hints参数的构造函数
  public LogicalTableScan(RelOptCluster cluster, RelOptTable table) { // 构造函数参数：cluster-关系代数集群，table-要扫描的表
    this(cluster, cluster.traitSetOf(Convention.NONE), ImmutableList.of(), table); // 调用主构造函数，自动创建特征集（包含Convention.NONE）和空的hints列表
  } // 构造函数结束，这是一个最简化的构造函数，自动处理特征集和hints

  /**
   * 通过解析序列化输出来创建LogicalTableScan实例。
   *
   * <p>这个构造函数用于从序列化的数据（如JSON、XML等格式）中重建LogicalTableScan对象。
   * 当查询计划被序列化存储或传输后，可以使用此方法将其反序列化为可用的关系代数节点。
   * RelInput对象包含了重建节点所需的所有信息，包括集群、特征集、提示和表引用等。
   *
   * @param input 序列化的输入对象，包含重建LogicalTableScan所需的所有信息
   */
  public LogicalTableScan(RelInput input) { // 构造函数参数：input-序列化输入对象
    super(input); // 调用父类TableScan的构造函数，从RelInput中读取并初始化所有属性
  } // 构造函数结束，用于反序列化场景

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于创建当前节点的副本
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言：新的特征集必须包含Convention.NONE（逻辑约定），确保逻辑节点的特征正确
    assert inputs.isEmpty(); // 断言：输入列表必须为空，因为TableScan是叶子节点，不应该有子节点
    return this; // 返回this，因为LogicalTableScan是不可变的，不需要创建新对象，直接返回当前实例即可
  } // copy方法结束，用于优化器在转换过程中复制节点

  /** 创建一个LogicalTableScan实例。
   *
   * <p>这是推荐的创建LogicalTableScan的静态工厂方法。它会自动处理特征集的初始化，
   * 包括从表的统计信息中提取排序信息，并将其添加到特征集中。这样可以确保LogicalTableScan
   * 节点包含所有必要的物理属性信息，便于优化器进行后续的转换和优化。
   *
   * @param cluster 关系代数集群，包含类型系统、表达式工厂等共享资源
   * @param relOptTable 要扫描的表，以RelOptTable形式表示，包含表的元数据和统计信息
   * @param hints 提示列表，包含优化器提示信息，用于指导查询优化过程
   * @return 新创建的LogicalTableScan实例
   */
  public static LogicalTableScan create(RelOptCluster cluster, // 静态工厂方法，用于创建LogicalTableScan实例
      final RelOptTable relOptTable, List<RelHint> hints) { // 方法参数：relOptTable-要扫描的表，hints-提示列表
    final Table table = relOptTable.unwrap(Table.class); // 尝试从RelOptTable中解包获取Table对象，用于访问表的统计信息
    final RelTraitSet traitSet = // 创建特征集，用于定义节点的物理属性
        cluster.traitSetOf(Convention.NONE) // 首先创建包含Convention.NONE（逻辑约定）的基础特征集
            .replaceIfs(RelCollationTraitDef.INSTANCE, () -> { // 根据条件替换排序特征，如果表有排序信息则添加到特征集中
              if (table != null) { // 如果成功获取到Table对象
                return table.getStatistic().getCollations(); // 返回表的统计信息中的排序信息（collations）
              } // 如果table为null，则执行下面的代码
              return ImmutableList.of(); // 返回空的不可变列表，表示没有排序信息
            }); // replaceIfs方法结束，根据条件决定是否添加排序特征
    return new LogicalTableScan(cluster, traitSet, hints, relOptTable); // 使用构造函数创建并返回LogicalTableScan实例
  } // create静态方法结束

  @Override public RelNode withHints(List<RelHint> hintList) { // 重写withHints方法，用于创建带有新提示列表的节点副本
    return new LogicalTableScan(getCluster(), traitSet, hintList, table); // 创建新的LogicalTableScan实例，使用新的提示列表，其他属性保持不变
  } // withHints方法结束，用于在优化过程中添加或修改提示信息
} // 类定义结束，LogicalTableScan类是一个不可变的逻辑表扫描节点
