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
package org.apache.calcite.adapter.cassandra; // 包声明：Cassandra适配器包，包含Cassandra数据库相关的Calcite适配器实现

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster：关系表达式集群，用于管理关系表达式的共享信息
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost：关系操作的成本对象，包含CPU、IO和内存成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner：关系优化器，用于优化查询计划
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet：关系特征集合，定义关系表达式的物理属性（如约定、排序等）
import org.apache.calcite.rel.RelNode; // 导入RelNode：关系表达式基类，所有关系操作节点的父接口
import org.apache.calcite.rel.core.Project; // 导入Project：投影操作基类，用于选择、重命名和计算列
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery：关系元数据查询接口，用于获取统计信息
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType：关系数据类型，描述行的类型信息
import org.apache.calcite.rex.RexNode; // 导入RexNode：行表达式节点，表示表达式树中的节点
import org.apache.calcite.util.Pair; // 导入Pair：键值对工具类，用于存储两个相关联的对象

import com.google.common.collect.ImmutableList; // 导入ImmutableList：不可变列表，Google Guava提供的不可修改列表实现
import com.google.common.collect.ImmutableSet; // 导入ImmutableSet：不可变集合，Google Guava提供的不可修改集合实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable：可空注解，用于标记可能为null的值

import java.util.LinkedHashMap; // 导入LinkedHashMap：链式哈希映射，保持插入顺序的Map实现
import java.util.List; // 导入List：列表接口，表示有序的元素集合
import java.util.Map; // 导入Map：映射接口，表示键值对集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull：对象非空检查工具方法

/**
 * Implementation of {@link org.apache.calcite.rel.core.Project}
 * relational expression in Cassandra.
 * CassandraProject类：Project关系表达式在Cassandra数据源中的具体实现
 * 
 * 类作用说明：
 * 1. 这个类实现了Calcite中的Project（投影）操作，专门用于Cassandra数据源
 * 2. Project操作是SQL中最常见的操作之一，用于：
 *    - 选择特定的列（SELECT column1, column2）
 *    - 重命名列（SELECT column1 AS new_name）
 *    - 计算派生列（SELECT column1 + column2 AS sum）
 *    - 应用表达式（SELECT UPPER(column1) AS upper_name）
 * 3. 这个类实现了CassandraRel接口，表明它是一个Cassandra专用的关系操作
 * 4. 在CQL（Cassandra Query Language）中，Project操作对应SELECT子句中的列选择和表达式计算
 * 5. 该类负责将Calcite的Rex表达式树转换为CQL的SELECT子句
 * 
 * 核心功能：
 * - 将Calcite的投影操作转换为CQL的SELECT语句
 * - 处理列的引用、重命名和表达式计算
 * - 维护Cassandra约定的物理属性
 * - 提供成本估算供优化器使用
 */
public class CassandraProject extends Project implements CassandraRel { // 类定义：CassandraProject继承自Project基类并实现CassandraRel接口
  // 构造方法：创建一个CassandraProject实例
  // 参数说明：
  //   - cluster: 关系表达式集群，包含类型工厂、表达式工厂等共享资源
  //   - traitSet: 关系特征集合，定义物理属性（如约定CassandraRel.CONVENTION）
  //   - input: 输入关系节点，通常是CassandraTableScan或其他CassandraRel实现
  //   - projects: 投影表达式列表，每个RexNode代表一个列或表达式
  //   - rowType: 输出行类型，描述投影后结果行的结构（列名和类型）
  public CassandraProject(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法参数：集群和特征集
      RelNode input, List<? extends RexNode> projects, RelDataType rowType) { // 构造方法参数：输入节点、投影表达式列表和行类型
    // 调用父类Project的构造方法，参数说明：
    //   - cluster: 传递关系表达式集群
    //   - traitSet: 传递特征集合
    //   - ImmutableList.of(): 空的变量列表，Project不使用变量
    //   - input: 输入关系节点
    //   - projects: 投影表达式列表
    //   - rowType: 输出行类型
    //   - ImmutableSet.of(): 空的指示变量集合，用于标记投影中的变量
    super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of()); // 调用父类构造方法初始化Project
    // 断言检查：确保当前节点的约定是Cassandra约定
    // 这是一个重要的约束，确保CassandraProject只出现在Cassandra物理计划中
    assert getConvention() == CassandraRel.CONVENTION; // 断言：当前节点的约定必须是Cassandra约定
    // 断言检查：确保输入节点的约定也是Cassandra约定
    // 这保证了整个关系树遵循一致的约定，避免混合不同数据源的约定
    assert getConvention() == input.getConvention(); // 断言：输入节点的约定也必须是Cassandra约定
  } // 构造方法结束

  // copy方法：创建当前CassandraProject的副本，这是RelNode接口的标准方法
  // 参数说明：
  //   - traitSet: 新的特征集合，可以与当前节点的特征集不同（用于优化器探索不同计划）
  //   - input: 新的输入节点
  //   - projects: 新的投影表达式列表
  //   - rowType: 新的输出行类型
  // 返回值：创建的新的CassandraProject实例
  // 用途：优化器在重写规则时会调用此方法创建新的关系节点
  @Override public Project copy(RelTraitSet traitSet, RelNode input, // 方法重写：copy方法，用于创建节点的副本
      List<RexNode> projects, RelDataType rowType) { // copy方法参数：新的特征集、输入节点、投影表达式和行类型
    // 创建并返回一个新的CassandraProject实例
    // 使用getCluster()获取当前集群，确保新节点共享相同的类型工厂等资源
    return new CassandraProject(getCluster(), traitSet, input, projects, // 返回新的CassandraProject实例，使用当前集群和新参数
        rowType); // 传递行类型参数
  } // copy方法结束

  // computeSelfCost方法：计算当前节点的成本，供优化器用于选择最优计划
  // 参数说明：
  //   - planner: 关系优化器，提供成本计算上下文
  //   - mq: 元数据查询对象，可以获取统计信息（如行数、列数等）
  // 返回值：计算出的成本对象（可能为null，用@Nullable注解标记）
  // 成本说明：成本包含CPU成本、IO成本和内存成本，优化器会选择总成本最小的计划
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法重写：计算当前节点的执行成本
      RelMetadataQuery mq) { // computeSelfCost方法参数：优化器和元数据查询对象
    // 调用父类Project的computeSelfCost方法获取基础成本
    // 父类会根据输入行数、投影表达式数量等因素计算成本
    final RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类方法计算基础成本
    // 将成本乘以0.1，表示Cassandra的投影操作相对便宜
    // 这是因为Cassandra的投影操作通常只是选择列，不需要额外计算
    // 较低的成本会鼓励优化器尽可能下推投影操作到Cassandra层
    // requireNonNull确保cost不为null，如果为null会抛出NullPointerException
    return requireNonNull(cost, "cost").multiplyBy(0.1); // 返回乘以0.1后的成本，表示Cassandra投影操作成本较低
  } // computeSelfCost方法结束

  // implement方法：将当前关系节点转换为CQL（Cassandra Query Language）语句
  // 参数说明：
  //   - implementor: 实现器对象，负责构建CQL语句并管理转换过程
  // 返回值：无（void方法）
  // 这是CassandraRel接口的核心方法，负责将逻辑计划转换为物理的CQL语句
  @Override public void implement(Implementor implementor) { // 方法实现：将Project节点转换为CQL语句
    // 首先访问子节点（输入节点），让子节点先实现自己
    // 这确保了CQL语句的正确构建顺序：FROM子句（由子节点实现）先于SELECT子句
    // 参数0表示访问第一个子节点，Project只有一个输入
    implementor.visitChild(0, getInput()); // 访问并实现输入子节点（通常是TableScan）

    // 创建Rex到Cassandra的转换器
    // 这个转换器负责将Calcite的Rex表达式树转换为CQL的列引用或表达式
    // CassandraRules.cassandraFieldNames获取输入行的所有Cassandra字段名
    // 字段名用于正确地将RexInputRef（列引用）转换为CQL的列名
    final CassandraRules.RexToCassandraTranslator translator = // 创建Rex表达式到CQL的转换器
        new CassandraRules.RexToCassandraTranslator( // 转换器构造函数
            CassandraRules.cassandraFieldNames(getInput().getRowType())); // 传递输入行的Cassandra字段名列表

    // 创建字段映射表，用于存储原始字段名到新字段名的映射
    // 使用LinkedHashMap保持插入顺序，确保SELECT子句中列的顺序与原始顺序一致
    // Map的key是原始字段名（CQL中的列名），value是新字段名（投影后的列名）
    final Map<String, String> fields = new LinkedHashMap<>(); // 创建字段映射表，保持插入顺序

    // 遍历所有命名投影（getNamedProjects返回表达式和别名的对列表）
    // 每个Pair包含：left是RexNode表达式，right是列别名
    // 例如：SELECT column1 AS new_name, column2 + 1 AS calculated
    // 会得到两个Pair：(column1, "new_name")和(column2 + 1, "calculated")
    for (Pair<RexNode, String> pair : getNamedProjects()) { // 遍历所有命名投影表达式
      // 获取当前投影的表达式节点（RexNode）
      // RexNode可以是一个简单的列引用（RexInputRef），也可以是一个复杂的表达式（如函数调用、算术运算等）
      final RexNode node = pair.left; // 获取投影表达式

      // 获取当前投影的别名（列名）
      // 这是SELECT子句中指定的列名，如果没有指定AS别名，则使用原始列名
      final String name = pair.right; // 获取投影的别名

      // 使用转换器将Rex表达式转换为CQL的列名或表达式
      // 对于简单的列引用（RexInputRef），转换器会返回对应的Cassandra列名
      // 对于复杂表达式，转换器会尝试将其转换为CQL表达式（如果支持）
      // accept方法访问Rex表达式树，让转换器遍历并转换表达式
      final String originalName = node.accept(translator); // 将Rex表达式转换为CQL列名或表达式

      // 将转换后的原始字段名和新别名存入映射表
      // key: originalName - CQL中的列名或表达式
      // value: name - 投影后的列名（别名）
      // 例如：fields.put("column1", "new_name")表示将column1重命名为new_name
      fields.put(originalName, name); // 存储字段映射：原始字段名 -> 新别名
    } // 遍历结束

    // 将构建好的字段映射添加到实现器中
    // implementor.add方法会将这些字段添加到CQL的SELECT子句中
    // 第二个参数null表示没有过滤条件（WHERE子句由CassandraFilter处理）
    // 最终生成的CQL类似：SELECT originalName AS name, ... FROM table
    implementor.add(fields, null); // 将字段映射添加到实现器，构建SELECT子句
  } // implement方法结束
} // 类定义结束
