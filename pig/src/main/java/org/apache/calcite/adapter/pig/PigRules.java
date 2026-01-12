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
package org.apache.calcite.adapter.pig; // 定义包名，该类属于 org.apache.calcite.adapter.pig 包，用于 Pig 适配器相关功能

import org.apache.calcite.plan.Convention; // 导入 Convention 类，用于表示关系代数操作的调用约定（如物理实现的约定）
import org.apache.calcite.plan.RelOptRule; // 导入 RelOptRule 类，表示优化器规则，用于将一种关系表达式转换为另一种
import org.apache.calcite.plan.RelOptRuleCall; // 导入 RelOptRuleCall 类，表示优化器规则调用的上下文
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系表达式的一组特征集合（如约定、排序等）
import org.apache.calcite.rel.RelNode; // 导入 RelNode 类，表示关系代数表达式树的节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入 ConverterRule 类，表示转换规则，用于将一种约定的关系节点转换为另一种约定的节点
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入 LogicalAggregate 类，表示逻辑聚合操作节点
import org.apache.calcite.rel.logical.LogicalFilter; // 导入 LogicalFilter 类，表示逻辑过滤操作节点
import org.apache.calcite.rel.logical.LogicalJoin; // 导入 LogicalJoin 类，表示逻辑连接操作节点
import org.apache.calcite.rel.logical.LogicalProject; // 导入 LogicalProject 类，表示逻辑投影操作节点
import org.apache.calcite.rel.logical.LogicalTableScan; // 导入 LogicalTableScan 类，表示逻辑表扫描操作节点

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类，用于创建不可变列表

import java.util.List; // 导入 Java 标准库的 List 接口，用于表示列表集合

/**
 * Various {@link RelOptRule}s using the Pig convention.
 */
// PigRules 类作用：定义了一系列使用 Pig 约定的关系优化规则（RelOptRule），用于将 Calcite 的逻辑操作符转换为对应的 Pig 操作符
// 这个类是一个工具类，包含了所有将逻辑关系节点转换为 Pig 物理节点的转换规则
// Pig 是 Apache 的数据流平台，用于处理大规模数据，这些规则使得 Calcite 可以将 SQL 查询转换为 Pig Latin 脚本
public class PigRules { // 定义 PigRules 类，该类只包含静态成员和静态内部类，不需要实例化

  // ALL_PIG_OPT_RULES 成员变量作用：定义所有 Pig 优化规则的不可变列表，包含了所有从逻辑节点到 Pig 节点的转换规则
  // 这个列表包含了5个转换规则：过滤规则、表扫描规则、投影规则、聚合规则和连接规则
  // 使用 ImmutableList.of 方法创建不可变列表，确保规则集合不会被意外修改
  public static final List<ConverterRule> ALL_PIG_OPT_RULES = // 声明公共静态常量列表，存储所有 Pig 转换规则
      ImmutableList.of(PigFilterRule.INSTANCE, // 添加 PigFilterRule 规则实例，用于将 LogicalFilter 转换为 PigFilter
          PigTableScanRule.INSTANCE, // 添加 PigTableScanRule 规则实例，用于将 LogicalTableScan 转换为 PigTableScan
          PigProjectRule.INSTANCE, // 添加 PigProjectRule 规则实例，用于将 LogicalProject 转换为 PigProject
          PigAggregateRule.INSTANCE, // 添加 PigAggregateRule 规则实例，用于将 LogicalAggregate 转换为 PigAggregate
          PigJoinRule.INSTANCE); // 添加 PigJoinRule 规则实例，用于将 LogicalJoin 转换为 PigJoin

  // prevent instantiation - 防止实例化，因为 PigRules 类只包含静态成员，不需要创建实例
  private PigRules() {} // 私有构造方法，防止外部实例化该类

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalFilter} to a
   * {@link PigFilter}.
   */
  // PigFilterRule 内部类作用：定义将 LogicalFilter（逻辑过滤）转换为 PigFilter（Pig 过滤）的转换规则
  // 该规则负责将 Calcite 的逻辑过滤操作转换为 Pig 的 FILTER 操作
  // 继承自 ConverterRule，是一个转换规则，用于改变关系节点的约定（Convention）
  private static class PigFilterRule extends ConverterRule { // 定义 PigFilterRule 私有静态内部类，继承自 ConverterRule
    // INSTANCE 成员变量作用：PigFilterRule 的单例实例，使用 Config 配置创建
    // 通过 Config.INSTANCE.withConversion 方法配置转换规则，指定：
    // - LogicalFilter.class：要从哪种逻辑节点转换
    // - Convention.NONE：源节点的约定（NONE 表示逻辑层）
    // - PigRel.CONVENTION：目标节点的约定（Pig 约定）
    // - "PigFilterRule"：规则的描述名称
    private static final PigFilterRule INSTANCE = Config.INSTANCE // 声明 PigFilterRule 的静态常量实例
        .withConversion(LogicalFilter.class, Convention.NONE, // 配置从 LogicalFilter 且约定为 NONE 的节点进行转换
            PigRel.CONVENTION, "PigFilterRule") // 转换为 PigRel.CONVENTION 约定，规则名称为 "PigFilterRule"
        .withRuleFactory(PigFilterRule::new) // 设置规则工厂方法，使用 PigFilterRule 的构造函数创建实例
        .toRule(PigFilterRule.class); // 将配置转换为规则对象，规则类型为 PigFilterRule.class

    // PigFilterRule 构造方法作用：初始化 PigFilterRule 转换规则
    // 接收 Config 配置对象，调用父类 ConverterRule 的构造方法进行初始化
    protected PigFilterRule(Config config) { // 定义受保护的构造方法，接收 Config 参数
      super(config); // 调用父类 ConverterRule 的构造方法，传入配置对象
    }

    // convert 方法作用：执行实际的转换操作，将 LogicalFilter 节点转换为 PigFilter 节点
    // 该方法是 ConverterRule 的核心方法，负责创建新的 Pig 节点并设置正确的特征集
    // 参数 rel：待转换的关系节点，应该是 LogicalFilter 类型
    // 返回值：转换后的 PigFilter 节点
    @Override public RelNode convert(RelNode rel) { // 重写 convert 方法，实现节点转换逻辑
      final LogicalFilter filter = (LogicalFilter) rel; // 将传入的关系节点强制转换为 LogicalFilter 类型
      final RelTraitSet traitSet = filter.getTraitSet().replace(PigRel.CONVENTION); // 获取过滤器的特征集，并将约定替换为 PigRel.CONVENTION
      return new PigFilter(rel.getCluster(), traitSet, // 创建并返回新的 PigFilter 节点，传入集群、特征集
          convert(filter.getInput(), PigRel.CONVENTION), filter.getCondition()); // 递归转换输入节点为 Pig 约定，并传入过滤条件
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalTableScan}
   * to a {@link PigTableScan}.
   */
  // PigTableScanRule 内部类作用：定义将 LogicalTableScan（逻辑表扫描）转换为 PigTableScan（Pig 表扫描）的转换规则
  // 该规则负责将 Calcite 的逻辑表扫描操作转换为 Pig 的 LOAD 操作
  // 表扫描是查询计划的叶子节点，表示从数据源读取数据
  private static class PigTableScanRule extends ConverterRule { // 定义 PigTableScanRule 私有静态内部类，继承自 ConverterRule
    // INSTANCE 成员变量作用：PigTableScanRule 的单例实例，使用 Config 配置创建
    // 配置从 LogicalTableScan 且约定为 NONE 的节点转换为 PigRel.CONVENTION 约定的节点
    private static final PigTableScanRule INSTANCE = Config.INSTANCE // 声明 PigTableScanRule 的静态常量实例
        .withConversion(LogicalTableScan.class, Convention.NONE, // 配置从 LogicalTableScan 且约定为 NONE 的节点进行转换
            PigRel.CONVENTION, "PigTableScanRule") // 转换为 PigRel.CONVENTION 约定，规则名称为 "PigTableScanRule"
        .withRuleFactory(PigTableScanRule::new) // 设置规则工厂方法，使用 PigTableScanRule 的构造函数创建实例
        .as(Config.class) // 将配置对象转换为 Config 类型（类型转换）
        .toRule(PigTableScanRule.class); // 将配置转换为规则对象，规则类型为 PigTableScanRule.class

    // PigTableScanRule 构造方法作用：初始化 PigTableScanRule 转换规则
    // 接收 Config 配置对象，调用父类 ConverterRule 的构造方法进行初始化
    protected PigTableScanRule(Config config) { // 定义受保护的构造方法，接收 Config 参数
      super(config); // 调用父类 ConverterRule 的构造方法，传入配置对象
    }

    // convert 方法作用：执行实际的转换操作，将 LogicalTableScan 节点转换为 PigTableScan 节点
    // 参数 rel：待转换的关系节点，应该是 LogicalTableScan 类型
    // 返回值：转换后的 PigTableScan 节点
    @Override public RelNode convert(RelNode rel) { // 重写 convert 方法，实现节点转换逻辑
      final LogicalTableScan scan = (LogicalTableScan) rel; // 将传入的关系节点强制转换为 LogicalTableScan 类型
      final RelTraitSet traitSet = // 获取扫描节点的特征集
          scan.getTraitSet().replace(PigRel.CONVENTION); // 并将约定替换为 PigRel.CONVENTION
      return new PigTableScan(rel.getCluster(), traitSet, scan.getTable()); // 创建并返回新的 PigTableScan 节点，传入集群、特征集和表信息
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalProject} to
   * a {@link PigProject}.
   */
  // PigProjectRule 内部类作用：定义将 LogicalProject（逻辑投影）转换为 PigProject（Pig 投影）的转换规则
  // 该规则负责将 Calcite 的逻辑投影操作转换为 Pig 的 FOREACH ... GENERATE 操作
  // 投影操作用于选择、重命名或计算输出列，是 SQL 中 SELECT 子句的核心
  private static class PigProjectRule extends ConverterRule { // 定义 PigProjectRule 私有静态内部类，继承自 ConverterRule
    // INSTANCE 成员变量作用：PigProjectRule 的单例实例，使用 Config 配置创建
    // 配置从 LogicalProject 且约定为 NONE 的节点转换为 PigRel.CONVENTION 约定的节点
    private static final PigProjectRule INSTANCE = Config.INSTANCE // 声明 PigProjectRule 的静态常量实例
        .withConversion(LogicalProject.class, Convention.NONE, // 配置从 LogicalProject 且约定为 NONE 的节点进行转换
            PigRel.CONVENTION, "PigProjectRule") // 转换为 PigRel.CONVENTION 约定，规则名称为 "PigProjectRule"
        .withRuleFactory(PigProjectRule::new) // 设置规则工厂方法，使用 PigProjectRule 的构造函数创建实例
        .toRule(PigProjectRule.class); // 将配置转换为规则对象，规则类型为 PigProjectRule.class

    // PigProjectRule 构造方法作用：初始化 PigProjectRule 转换规则
    // 接收 Config 配置对象，调用父类 ConverterRule 的构造方法进行初始化
    protected PigProjectRule(Config config) { // 定义受保护的构造方法，接收 Config 参数
      super(config); // 调用父类 ConverterRule 的构造方法，传入配置对象
    }

    // matches 方法作用：判断当前规则是否匹配给定的规则调用
    // 该方法用于在进行转换前检查条件，只有返回 true 时才会执行转换
    // 参数 call：优化器规则调用的上下文，包含相关信息
    // 返回值：如果规则匹配返回 true，否则返回 false
    // 这里检查投影是否包含变量集合（variablesSet），如果为空才匹配，因为 Pig 不支持带变量的投影
    @Override public boolean matches(RelOptRuleCall call) { // 重写 matches 方法，实现规则匹配逻辑
      final LogicalProject project = call.rel(0); // 从规则调用中获取第一个关系节点（索引为0），转换为 LogicalProject 类型
      return project.getVariablesSet().isEmpty(); // 返回投影的变量集合是否为空，只有为空时才允许转换
    }

    // convert 方法作用：执行实际的转换操作，将 LogicalProject 节点转换为 PigProject 节点
    // 参数 rel：待转换的关系节点，应该是 LogicalProject 类型
    // 返回值：转换后的 PigProject 节点
    @Override public RelNode convert(RelNode rel) { // 重写 convert 方法，实现节点转换逻辑
      final LogicalProject project = (LogicalProject) rel; // 将传入的关系节点强制转换为 LogicalProject 类型
      final RelTraitSet traitSet = project.getTraitSet().replace(PigRel.CONVENTION); // 获取投影的特征集，并将约定替换为 PigRel.CONVENTION
      return new PigProject(project.getCluster(), traitSet, project.getInput(), // 创建并返回新的 PigProject 节点，传入集群、特征集、输入节点
          project.getProjects(), project.getRowType()); // 以及投影表达式列表和输出行类型
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalAggregate} to a
   * {@link PigAggregate}.
   */
  // PigAggregateRule 内部类作用：定义将 LogicalAggregate（逻辑聚合）转换为 PigAggregate（Pig 聚合）的转换规则
  // 该规则负责将 Calcite 的逻辑聚合操作转换为 Pig 的 GROUP BY 和聚合函数操作
  // 聚合操作用于执行 GROUP BY、COUNT、SUM、AVG、MIN、MAX 等聚合计算
  private static class PigAggregateRule extends ConverterRule { // 定义 PigAggregateRule 私有静态内部类，继承自 ConverterRule
    // INSTANCE 成员变量作用：PigAggregateRule 的单例实例，使用 Config 配置创建
    // 配置从 LogicalAggregate 且约定为 NONE 的节点转换为 PigRel.CONVENTION 约定的节点
    private static final PigAggregateRule INSTANCE = Config.INSTANCE // 声明 PigAggregateRule 的静态常量实例
        .withConversion(LogicalAggregate.class, Convention.NONE, // 配置从 LogicalAggregate 且约定为 NONE 的节点进行转换
            PigRel.CONVENTION, "PigAggregateRule") // 转换为 PigRel.CONVENTION 约定，规则名称为 "PigAggregateRule"
        .withRuleFactory(PigAggregateRule::new) // 设置规则工厂方法，使用 PigAggregateRule 的构造函数创建实例
        .toRule(PigAggregateRule.class); // 将配置转换为规则对象，规则类型为 PigAggregateRule.class

    // PigAggregateRule 构造方法作用：初始化 PigAggregateRule 转换规则
    // 接收 Config 配置对象，调用父类 ConverterRule 的构造方法进行初始化
    protected PigAggregateRule(Config config) { // 定义受保护的构造方法，接收 Config 参数
      super(config); // 调用父类 ConverterRule 的构造方法，传入配置对象
    }

    // convert 方法作用：执行实际的转换操作，将 LogicalAggregate 节点转换为 PigAggregate 节点
    // 参数 rel：待转换的关系节点，应该是 LogicalAggregate 类型
    // 返回值：转换后的 PigAggregate 节点
    @Override public RelNode convert(RelNode rel) { // 重写 convert 方法，实现节点转换逻辑
      final LogicalAggregate agg = (LogicalAggregate) rel; // 将传入的关系节点强制转换为 LogicalAggregate 类型
      final RelTraitSet traitSet = agg.getTraitSet().replace(PigRel.CONVENTION); // 获取聚合的特征集，并将约定替换为 PigRel.CONVENTION
      return new PigAggregate(agg.getCluster(), traitSet, agg.getInput(), // 创建并返回新的 PigAggregate 节点，传入集群、特征集、输入节点
          agg.getGroupSet(), agg.getGroupSets(), agg.getAggCallList()); // 以及分组集合、分组集合列表和聚合调用列表
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalJoin} to
   * a {@link PigJoin}.
   */
  // PigJoinRule 内部类作用：定义将 LogicalJoin（逻辑连接）转换为 PigJoin（Pig 连接）的转换规则
  // 该规则负责将 Calcite 的逻辑连接操作转换为 Pig 的 JOIN 操作
  // 连接操作用于将两个或多个表的数据根据连接条件组合在一起
  private static class PigJoinRule extends ConverterRule { // 定义 PigJoinRule 私有静态内部类，继承自 ConverterRule
    // INSTANCE 成员变量作用：PigJoinRule 的单例实例，使用 Config 配置创建
    // 配置从 LogicalJoin 且约定为 NONE 的节点转换为 PigRel.CONVENTION 约定的节点
    private static final PigJoinRule INSTANCE = Config.INSTANCE // 声明 PigJoinRule 的静态常量实例
        .withConversion(LogicalJoin.class, Convention.NONE, // 配置从 LogicalJoin 且约定为 NONE 的节点进行转换
            PigRel.CONVENTION, "PigJoinRule") // 转换为 PigRel.CONVENTION 约定，规则名称为 "PigJoinRule"
        .withRuleFactory(PigJoinRule::new) // 设置规则工厂方法，使用 PigJoinRule 的构造函数创建实例
        .toRule(PigJoinRule.class); // 将配置转换为规则对象，规则类型为 PigJoinRule.class

    // PigJoinRule 构造方法作用：初始化 PigJoinRule 转换规则
    // 接收 Config 配置对象，调用父类 ConverterRule 的构造方法进行初始化
    protected PigJoinRule(Config config) { // 定义受保护的构造方法，接收 Config 参数
      super(config); // 调用父类 ConverterRule 的构造方法，传入配置对象
    }

    // convert 方法作用：执行实际的转换操作，将 LogicalJoin 节点转换为 PigJoin 节点
    // 参数 rel：待转换的关系节点，应该是 LogicalJoin 类型
    // 返回值：转换后的 PigJoin 节点
    @Override public RelNode convert(RelNode rel) { // 重写 convert 方法，实现节点转换逻辑
      final LogicalJoin join = (LogicalJoin) rel; // 将传入的关系节点强制转换为 LogicalJoin 类型
      final RelTraitSet traitSet = join.getTraitSet().replace(PigRel.CONVENTION); // 获取连接的特征集，并将约定替换为 PigRel.CONVENTION
      return new PigJoin(join.getCluster(), traitSet, join.getLeft(), join.getRight(), // 创建并返回新的 PigJoin 节点，传入集群、特征集、左输入节点、右输入节点
          join.getCondition(), join.getJoinType()); // 以及连接条件和连接类型（如 INNER、LEFT、RIGHT 等）
    }
  }
} // PigRules 类结束
