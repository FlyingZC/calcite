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
package org.apache.calcite.rel.logical; // 声明包名，LogicalTableSpool属于逻辑关系代数包

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，标记API可能变化
import org.apache.calcite.plan.Convention; // 导入调用约定，定义关系代数节点的约定类型
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含查询优化所需的全局信息
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表，表示优化器中的表元数据
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系节点的物理属性（如排序、分布等）
import org.apache.calcite.rel.RelCollationTraitDef; // 导入排序特征定义，用于处理数据的排序属性
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入分布特征定义，用于处理数据的分布属性
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系代数操作的基类
import org.apache.calcite.rel.core.Spool; // 导入Spool基类，实现数据的缓存/暂存功能
import org.apache.calcite.rel.core.TableSpool; // 导入TableSpool类，基于表的缓存实现
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入元数据查询接口，用于获取关系的统计信息

/**
 * Sub-class of {@link TableSpool} not targeted at any particular engine or
 * calling convention.
 * LogicalTableSpool是TableSpool的子类，不针对任何特定的引擎或调用约定
 * 它是逻辑层面的表缓存操作，用于在查询优化阶段表示将数据缓存到表中的操作
 *
 * <p>NOTE: The current API is experimental and subject to change without
 * notice.
 * 注意：当前API是实验性的，可能会在没有通知的情况下发生变化
 * 这个类主要用于查询优化过程中的中间表示，可以将重复扫描的数据缓存起来以提高性能
 */
@Experimental // 标记为实验性API，提醒开发者此接口可能在未来版本中发生变化
public class LogicalTableSpool extends TableSpool { // 定义LogicalTableSpool类，继承自TableSpool，实现逻辑层面的表缓存操作

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域标记，用于分隔代码块，提高可读性
  
  /**
   * LogicalTableSpool的构造方法
   * @param cluster 关系优化集群，包含查询优化所需的全局信息如类型系统、表达式工厂等
   * @param traitSet 关系特征集合，定义此节点的物理属性（如排序规则、数据分布方式等）
   * @param input 输入关系节点，表示需要被缓存的数据源
   * @param readType 读取类型，定义如何从缓存中读取数据（如LAZY_EAGER、LAZY等）
   * @param writeType 写入类型，定义如何将数据写入缓存（如LAZY_EAGER、LAZY等）
   * @param table 关系优化表，表示用于存储缓存数据的目标表
   */
  public LogicalTableSpool(RelOptCluster cluster, RelTraitSet traitSet, RelNode input,
      Type readType, Type writeType, RelOptTable table) {
    super(cluster, traitSet, input, readType, writeType, table); // 调用父类TableSpool的构造方法，初始化所有参数
  }

  /** Creates a LogicalTableSpool.
   * 创建LogicalTableSpool的静态工厂方法，方便创建实例并自动设置适当的特征集合
   * @param input 输入关系节点，表示需要被缓存的数据源
   * @param readType 读取类型，定义如何从缓存中读取数据
   * @param writeType 写入类型，定义如何将数据写入缓存
   * @param table 关系优化表，表示用于存储缓存数据的目标表
   * @return 新创建的LogicalTableSpool实例
   */
  public static LogicalTableSpool create(RelNode input, Type readType,
      Type writeType, RelOptTable table) {
    RelOptCluster cluster = input.getCluster(); // 从输入节点获取关系优化集群，确保使用相同的集群上下文
    RelMetadataQuery mq = cluster.getMetadataQuery(); // 获取元数据查询对象，用于查询输入节点的统计信息
    RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE) // 创建特征集合，使用NONE约定表示逻辑节点（非物理实现）
        .replaceIfs(RelCollationTraitDef.INSTANCE, // 如果输入节点有排序特征，则替换当前特征集合中的排序特征
            () -> mq.collations(input)) // 通过元数据查询获取输入节点的排序信息
        .replaceIf(RelDistributionTraitDef.INSTANCE, // 如果输入节点有分布特征，则替换当前特征集合中的分布特征
            () -> mq.distribution(input)); // 通过元数据查询获取输入节点的分布信息
    return new LogicalTableSpool(cluster, traitSet, input, readType, writeType, table); // 创建并返回新的LogicalTableSpool实例
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域标记，用于分隔代码块，提高可读性

  /**
   * 复制当前LogicalTableSpool节点，可以修改部分参数
   * @param traitSet 新的关系特征集合，可以修改节点的物理属性
   * @param input 新的输入关系节点，可以替换数据源
   * @param readType 新的读取类型，可以修改缓存读取策略
   * @param writeType 新的写入类型，可以修改缓存写入策略
   * @return 复制后的新LogicalTableSpool实例，保持原有的table引用
   */
  @Override protected Spool copy(RelTraitSet traitSet, RelNode input,
      Type readType, Type writeType) {
    return new LogicalTableSpool(input.getCluster(), traitSet, input, // 创建新的LogicalTableSpool实例，使用输入节点的集群
        readType, writeType, table); // 使用现有的table引用（从父类继承的成员变量）
  }
}
