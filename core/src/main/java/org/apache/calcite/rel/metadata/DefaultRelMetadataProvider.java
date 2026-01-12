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
// Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.rel.metadata;
// 定义包路径，这个类位于org.apache.calcite.rel.metadata包中，属于关系表达式元数据模块

import com.google.common.collect.ImmutableList;
// 导入Google Guava库中的ImmutableList类，用于创建不可变的列表，保证线程安全

/**
 * DefaultRelMetadataProvider supplies a default implementation of the
 * {@link RelMetadataProvider} interface. It provides generic formulas and
 * derivation rules for the standard logical algebra; coverage corresponds to
 * the methods declared in {@link RelMetadataQuery}.
 */
// 类文档注释：DefaultRelMetadataProvider提供了RelMetadataProvider接口的默认实现
// 它为标准逻辑代数提供了通用公式和推导规则
// 覆盖范围对应于RelMetadataQuery中声明的方法
// 这个类是Calcite元数据系统的核心组件，负责提供各种关系表达式的元数据计算能力
public class DefaultRelMetadataProvider extends ChainedRelMetadataProvider {
  // DefaultRelMetadataProvider继承自ChainedRelMetadataProvider
  // ChainedRelMetadataProvider是一个链式元数据提供器，可以组合多个元数据提供器
  // 这种设计模式允许系统按优先级顺序尝试不同的元数据提供器来获取元数据
  public static final DefaultRelMetadataProvider INSTANCE =
      new DefaultRelMetadataProvider();
  // 定义一个静态常量INSTANCE，这是DefaultRelMetadataProvider的单例实例
  // 使用单例模式是因为元数据提供器是无状态的，可以全局共享
  // 这个实例包含了所有默认的元数据处理器，应该作为链中的最后一个提供器使用
  // 在Calcite的优化器中，这个单例会被注册到RelMetadataProvider链中

  //~ Constructors -----------------------------------------------------------
  // 分隔符注释，标记构造方法部分的开始

  /**
   * Creates a new default provider. This provider defines "catch-all"
   * handlers for generic RelNodes, so it should always be given lowest
   * priority when chaining.
   *
   * <p>Use this constructor only from a sub-class. Otherwise use the singleton
   * instance, {@link #INSTANCE}.
   */
  // 构造方法文档注释：创建一个新的默认提供器
  // 这个提供器为通用的RelNode定义了"兜底"处理器
  // 因此在链式调用时应该始终赋予最低优先级
  // <p>表示开始新的段落
  // 这个构造方法应该只在子类中使用，否则应该使用单例实例INSTANCE
  protected DefaultRelMetadataProvider() {
    // 受保护的构造方法，防止外部直接实例化，强制使用单例INSTANCE
    // 构造方法通过super调用父类ChainedRelMetadataProvider的构造方法
    // 传入一个包含所有元数据源的不可变列表
    super(
        // 调用父类构造方法，传入一个ImmutableList作为参数
        // 这个列表包含了所有默认的元数据处理器源
        // 每个SOURCE都是一个RelMetadataProvider实例，负责特定类型的元数据计算
        ImmutableList.of(
            // 创建不可变列表，包含所有默认的元数据处理器
            // 这些处理器按照特定顺序排列，优先级高的在前
            // 当查询元数据时，会按顺序尝试这些处理器，直到找到能处理该请求的处理器

            RelMdPercentageOriginalRows.SOURCE,
            // RelMdPercentageOriginalRows.SOURCE：处理原始行百分比元数据
            // 用于计算经过过滤等操作后，保留了原始表的多少行数据
            // 这个元数据对于估算查询的选择性和优化器决策非常重要

            RelMdColumnOrigins.SOURCE,
            // RelMdColumnOrigins.SOURCE：处理列的来源信息元数据
            // 追踪查询结果中每一列的来源表和来源列
            // 用于列级血缘分析，理解数据是如何从源表经过转换得到最终结果的

            RelMdExpressionLineage.SOURCE,
            // RelMdExpressionLineage.SOURCE：处理表达式的血缘关系元数据
            // 追踪表达式的起源和转换过程
            // 帮助理解复杂表达式是如何从源列推导出来的

            RelMdTableReferences.SOURCE,
            // RelMdTableReferences.SOURCE：处理表引用元数据
            // 识别关系表达式引用了哪些表
            // 用于查询优化和权限检查

            RelMdNodeTypes.SOURCE,
            // RelMdNodeTypes.SOURCE：处理节点类型元数据
            // 提供关系节点的类型信息
            // 用于类型检查和类型推断

            RelMdRowCount.SOURCE,
            // RelMdRowCount.SOURCE：处理行数统计元数据
            // 估算关系表达式输出的行数
            // 这是查询优化中最基础和最重要的元数据之一，用于计算成本

            RelMdMaxRowCount.SOURCE,
            // RelMdMaxRowCount.SOURCE：处理最大行数元数据
            // 返回关系表达式可能输出的最大行数
            // 用于优化器的边界条件检查和资源估算

            RelMdMinRowCount.SOURCE,
            // RelMdMinRowCount.SOURCE：处理最小行数元数据
            // 返回关系表达式可能输出的最小行数
            // 用于优化器的边界条件检查和资源估算

            RelMdUniqueKeys.SOURCE,
            // RelMdUniqueKeys.SOURCE：处理唯一键元数据
            // 识别关系表达式结果中的唯一键或候选键
            // 用于去重优化、连接策略选择等

            RelMdColumnUniqueness.SOURCE,
            // RelMdColumnUniqueness.SOURCE：处理列唯一性元数据
            // 判断特定列是否包含唯一值
            // 用于优化器决定是否可以使用索引或进行特定优化

            RelMdPopulationSize.SOURCE,
            // RelMdPopulationSize.SOURCE：处理总体大小元数据
            // 估算列中不同值的数量（基数）
            // 用于选择率估算和连接算法选择

            RelMdSize.SOURCE,
            // RelMdSize.SOURCE：处理数据大小元数据
            // 估算关系表达式输出的字节数
            // 用于内存管理和资源分配

            RelMdParallelism.SOURCE,
            // RelMdParallelism.SOURCE：处理并行度元数据
            // 提供关系表达式可以并行执行的程度
            // 用于分布式查询优化和并行执行计划生成

            RelMdDistribution.SOURCE,
            // RelMdDistribution.SOURCE：处理数据分布元数据
            // 描述数据在不同节点或分区上的分布情况
            // 用于分布式查询优化和数据重分布决策

            RelMdLowerBoundCost.SOURCE,
            // RelMdLowerBoundCost.SOURCE：处理成本下界元数据
            // 提供执行某个操作的最小可能成本
            // 用于优化器剪枝，快速排除劣质计划

            RelMdMeasure.SOURCE,
            // RelMdMeasure.SOURCE：处理度量元数据
            // 提供聚合操作相关的度量信息
            // 用于聚合查询的优化和评估

            RelMdMemory.SOURCE,
            // RelMdMemory.SOURCE：处理内存使用元数据
            // 估算执行某个操作所需的内存量
            // 用于内存管理和避免内存溢出

            RelMdDistinctRowCount.SOURCE,
            // RelMdDistinctRowCount.SOURCE：处理不同行数统计元数据
            // 估算某个列或列组合的不同值数量
            // 用于选择率计算和连接优化

            RelMdSelectivity.SOURCE,
            // RelMdSelectivity.SOURCE：处理选择性元数据
            // 估算过滤条件的选择性（过滤掉多少行）
            // 是查询优化中最重要的元数据之一，影响所有成本估算

            RelMdExplainVisibility.SOURCE,
            // RelMdExplainVisibility.SOURCE：处理解释可见性元数据
            // 控制在执行计划解释中哪些信息应该显示
            // 用于生成用户友好的执行计划说明

            RelMdPredicates.SOURCE,
            // RelMdPredicates.SOURCE：处理谓词元数据
            // 提供关系表达式上已知的谓词信息
            // 用于谓词下推和查询重写优化

            RelMdAllPredicates.SOURCE,
            // RelMdAllPredicates.SOURCE：处理所有谓词元数据
            // 提供关系表达式上所有可能的谓词信息
            // 用于更全面的谓词下推和查询优化

            RelMdCollation.SOURCE));
  // RelMdCollation.SOURCE：处理排序顺序元数据
  // 描述关系表达式输出的排序属性
  // 用于消除不必要的排序操作和选择最优的连接算法
  // 注意：这个SOURCE是列表中的最后一个，表示最低优先级
  }
}
// 类定义结束
// DefaultRelMetadataProvider作为Calcite元数据系统的核心，提供了所有标准元数据的默认实现
// 这些元数据处理器通过链式模式组织，允许系统灵活地扩展和覆盖默认行为
// 优化器通过这些元数据来估算成本、选择最优的执行计划
