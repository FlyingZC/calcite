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
package org.apache.calcite.rel.logical; // 定义包名，此类属于逻辑关系代数包，包含所有逻辑操作符的实现

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系表达式的调用约定（如物理实现方式）
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，代表关系表达式集群，包含优化器上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，代表关系表达式的特征集合（如排序、分布等）
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，代表排序规范，定义字段的排序顺序和方向
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef类，用于规范化排序特征定义
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution类，代表数据分布规范，定义数据如何分布到各个节点
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，用于规范化数据分布特征定义
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化输入中重建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，所有关系表达式的基类，代表查询计划中的一个节点
import org.apache.calcite.rel.core.SortExchange; // 导入SortExchange抽象类，LogicalSortExchange的父类，定义排序交换的基本行为

/**
 * Sub-class of {@link org.apache.calcite.rel.core.SortExchange} not // 这是SortExchange的子类
 * targeted at any particular engine or calling convention. // 不针对任何特定的引擎或调用约定，表示这是一个逻辑层面的排序交换操作符
 * // 类作用说明：LogicalSortExchange是Calcite中用于表示"排序并交换"操作的逻辑关系表达式节点
 * // 它结合了排序（Sort）和数据交换（Exchange）两个操作，通常用于分布式查询优化场景
 * // 在分布式系统中，当需要将数据按照特定顺序重新分布到不同节点时会使用此操作符
 * // 例如：在执行ORDER BY操作时，可能需要先在各个节点局部排序，然后通过SortExchange进行全局排序
 * // 此类是逻辑层面的表示，不包含具体的物理实现细节，物理实现由相应的规则（Rules）转换
 */
public class LogicalSortExchange extends SortExchange { // 定义LogicalSortExchange类，继承自SortExchange
  private LogicalSortExchange(RelOptCluster cluster, RelTraitSet traitSet, // 私有构造方法，参数：集群对象、特征集合、输入节点、分布规范、排序规范
      RelNode input, RelDistribution distribution, RelCollation collation) { // 参数续：输入关系表达式、数据分布方式、排序规则
    super(cluster, traitSet, input, distribution, collation); // 调用父类SortExchange的构造方法，初始化基本属性
  } // 构造方法结束

  /**
   * Creates a LogicalSortExchange by parsing serialized output. // 通过解析序列化输出来创建LogicalSortExchange对象
   * // 此构造方法用于从JSON或其他序列化格式中重建关系表达式
   * // RelInput包含了序列化的关系表达式信息，包括输入节点、分布规范、排序规范等
   */
  public LogicalSortExchange(RelInput input) { // 公有构造方法，接受RelInput参数
    super(input); // 调用父类SortExchange的构造方法，从RelInput中读取并初始化所有属性
  } // 构造方法结束

  /**
   * Creates a LogicalSortExchange. // 创建LogicalSortExchange对象的工厂方法
   * // 这是创建LogicalSortExchange的主要方法，负责规范化参数并构建正确的特征集合
   * // 方法会自动规范化排序规范和分布规范，确保它们使用标准化的表示形式
   *
   * @param input     Input relational expression // 参数：输入的关系表达式，即要进行排序和交换的数据源
   * @param distribution Distribution specification // 参数：数据分布规范，定义数据如何分布（如哈希分布、范围分布、广播等）
   * @param collation array of sort specifications // 参数：排序规范数组，定义字段的排序顺序（升序、降序）和排序字段
   */
  public static LogicalSortExchange create( // 静态工厂方法，用于创建LogicalSortExchange实例
      RelNode input, // 参数：输入关系表达式
      RelDistribution distribution, // 参数：数据分布规范
      RelCollation collation) { // 参数：排序规范
    RelOptCluster cluster = input.getCluster(); // 从输入节点获取RelOptCluster对象，包含优化器上下文信息
    collation = RelCollationTraitDef.INSTANCE.canonize(collation); // 规范化排序规范，将其转换为标准形式，确保排序定义的一致性
    distribution = RelDistributionTraitDef.INSTANCE.canonize(distribution); // 规范化数据分布规范，将其转换为标准形式，确保分布定义的一致性
    RelTraitSet traitSet = // 创建关系表达式特征集合
        input.getTraitSet().replace(Convention.NONE).replace(distribution).replace(collation); // 从输入节点的特征集合开始，依次替换为：无调用约定（逻辑层）、新的分布规范、新的排序规范
    return new LogicalSortExchange(cluster, traitSet, input, distribution, // 创建并返回新的LogicalSortExchange实例，传入集群、特征集合、输入节点、分布规范和排序规范
        collation); // 参数续：排序规范
  } // 工厂方法结束

  //~ Methods ---------------------------------------------------------------- // 方法分隔符注释，以下为类的方法定义

  @Override public SortExchange copy(RelTraitSet traitSet, RelNode newInput, // 重写copy方法，用于创建当前节点的副本，允许修改特征集合、输入节点、分布规范和排序规范
      RelDistribution newDistribution, RelCollation newCollation) { // 参数续：新的数据分布规范、新的排序规范
    return new LogicalSortExchange(this.getCluster(), traitSet, newInput, // 返回一个新的LogicalSortExchange实例，使用当前节点的集群、新的特征集合、新的输入节点
        newDistribution, newCollation); // 参数续：新的分布规范、新的排序规范
  } // copy方法结束
} // 类定义结束
