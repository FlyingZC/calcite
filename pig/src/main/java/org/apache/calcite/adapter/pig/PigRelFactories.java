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
// 包声明：该类属于 org.apache.calcite.adapter.pig 包，是 Calcite Pig 适配器的一部分
package org.apache.calcite.adapter.pig;

// 导入 Context 接口：用于在 Calcite 优化器中传递上下文信息
import org.apache.calcite.plan.Context;
// 导入 Contexts 工具类：用于创建 Context 对象的静态工厂方法
import org.apache.calcite.plan.Contexts;
// 导入 RelOptCluster 类：表示关系表达式集群，包含优化器的共享资源
import org.apache.calcite.plan.RelOptCluster;
// 导入 RelOptTable 类：表示优化器中的表对象，包含表的元数据信息
import org.apache.calcite.plan.RelOptTable;
// 导入 RelTraitSet 类：表示关系表达式的特征集合（如约定、排序、分布等）
import org.apache.calcite.plan.RelTraitSet;
// 导入 RelNode 接口：表示关系代数表达式节点，是 Calcite 关系代数树的基类
import org.apache.calcite.rel.RelNode;
// 导入 AggregateCall 类：表示聚合函数调用，包含聚合函数的详细信息
import org.apache.calcite.rel.core.AggregateCall;
// 导入 CorrelationId 类：表示相关变量的唯一标识符，用于子查询去相关化
import org.apache.calcite.rel.core.CorrelationId;
// 导入 JoinRelType 枚举：表示连接类型（内连接、左外连接、右外连接、全外连接等）
import org.apache.calcite.rel.core.JoinRelType;
// 导入 RelFactories 类：包含创建各种关系节点类型的工厂接口
import org.apache.calcite.rel.core.RelFactories;
// 导入 RelHint 类：表示关系节点的提示信息，用于影响优化器的决策
import org.apache.calcite.rel.hint.RelHint;
// 导入 RexNode 类：表示行表达式（Row Expression），是 Calcite 表达式树的基类
import org.apache.calcite.rex.RexNode;
// 导入 ImmutableBitSet 类：表示不可变的位集合，用于高效表示字段索引集合
import org.apache.calcite.util.ImmutableBitSet;
// 导入 Util 工具类：提供通用的工具方法
import org.apache.calcite.util.Util;

// 导入 ImmutableList 类：Google Guava 提供的不可变列表实现
import com.google.common.collect.ImmutableList;

// 导入 List 接口：Java 集合框架的列表接口
import java.util.List;
// 导入 Set 接口：Java 集合框架的集合接口
import java.util.Set;

// 静态导入 checkArgument 方法：用于方法参数的前置条件检查
import static com.google.common.base.Preconditions.checkArgument;

/** Implementations of factories in {@link RelFactories}
 * for the Pig adapter. */
// 类文档注释：该类实现了 RelFactories 中定义的工厂接口，专门用于 Pig 适配器
// Pig 是 Apache 的一个大数据处理平台，该类提供了创建 Pig 特定关系节点的工厂方法
// 这些工厂方法用于在 Pig 适配器中创建 TableScan、Filter、Aggregate 和 Join 等关系节点
public class PigRelFactories {

  // 定义一个公共静态常量 ALL_PIG_REL_FACTORIES，类型为 Context，用于包含所有 Pig 关系节点工厂
  // 该常量通过 Contexts.of() 方法创建，将四个工厂实例打包到一个 Context 对象中
  // 这四个工厂分别是：TableScanFactory（表扫描）、FilterFactory（过滤）、AggregateFactory（聚合）、JoinFactory（连接）
  // 在优化过程中，可以通过这个 Context 对象访问到所有 Pig 特定的工厂实现
  public static final Context ALL_PIG_REL_FACTORIES =
      Contexts.of(PigTableScanFactory.INSTANCE,
          PigFilterFactory.INSTANCE,
          PigAggregateFactory.INSTANCE,
          PigJoinFactory.INSTANCE);

  // 私有构造方法，防止外部实例化该类
  // 这是一个工具类，所有成员都是静态的，不需要创建实例
  // 通过私有构造方法确保该类不会被实例化，符合工具类的设计模式
  // prevent instantiation
  private PigRelFactories() {
  }

  /**
   * Implementation of
   * {@link org.apache.calcite.rel.core.RelFactories.TableScanFactory} that
   * returns a {@link PigTableScan}.
   */
  // 类文档注释：该类实现了 RelFactories.TableScanFactory 接口
  // TableScanFactory 是一个工厂接口，用于创建表扫描关系节点
  // 该实现专门为 Pig 适配器设计，返回 PigTableScan 类型的节点
  // PigTableScan 是 Pig 特定的表扫描节点，用于读取数据源
  public static class PigTableScanFactory implements RelFactories.TableScanFactory {

    // 定义一个公共静态常量 INSTANCE，类型为 PigTableScanFactory，采用单例模式
    // 这是工厂类的实例，可以在整个应用中共享使用
    // 使用单例模式避免重复创建工厂对象，提高性能
    public static final PigTableScanFactory INSTANCE = new PigTableScanFactory();

    // 重写 createScan 方法，用于创建表扫描关系节点
    // 参数说明：
    //   - toRelContext: 表转换上下文，包含集群信息等
    //   - table: 要扫描的表对象，包含表的元数据信息
    // 返回值：创建的 PigTableScan 关系节点
    @Override public RelNode createScan(RelOptTable.ToRelContext toRelContext,
        RelOptTable table) {
      // 从上下文中获取关系表达式集群（RelOptCluster）
      // 集群包含了优化器的共享资源，如类型系统、表达式构建器等
      final RelOptCluster cluster = toRelContext.getCluster();
      // 创建并返回一个新的 PigTableScan 对象
      // 参数说明：
      //   - cluster: 关系表达式集群
      //   - cluster.traitSetOf(PigRel.CONVENTION): 创建特征集合，设置为 Pig 约定（CONVENTION）
      //     特征集合定义了关系节点的物理属性，PigRel.CONVENTION 表示该节点属于 Pig 适配器
      //   - table: 要扫描的表对象
      return new PigTableScan(cluster, cluster.traitSetOf(PigRel.CONVENTION), table);
    }
  }

  /**
   * Implementation of
   * {@link org.apache.calcite.rel.core.RelFactories.FilterFactory} that
   * returns a {@link PigFilter}.
   */
  // 类文档注释：该类实现了 RelFactories.FilterFactory 接口
  // FilterFactory 是一个工厂接口，用于创建过滤关系节点
  // 该实现专门为 Pig 适配器设计，返回 PigFilter 类型的节点
  // PigFilter 是 Pig 特定的过滤节点，用于根据条件过滤数据行
  public static class PigFilterFactory implements RelFactories.FilterFactory {

    // 定义一个公共静态常量 INSTANCE，类型为 PigFilterFactory，采用单例模式
    // 这是工厂类的实例，可以在整个应用中共享使用
    public static final PigFilterFactory INSTANCE = new PigFilterFactory();

    // 重写 createFilter 方法，用于创建过滤关系节点
    // 参数说明：
    //   - input: 输入关系节点，表示要过滤的数据源
    //   - condition: 过滤条件，是一个 RexNode 行表达式
    //   - variablesSet: 相关变量集合，用于子查询去相关化
    // 返回值：创建的 PigFilter 关系节点
    @Override public RelNode createFilter(RelNode input, RexNode condition,
        Set<CorrelationId> variablesSet) {
      // 检查参数前置条件：确保 variablesSet 为空
      // PigFilter 不支持相关变量，因为 Pig 的过滤操作不支持子查询去相关化
      // 如果 variablesSet 不为空，抛出 IllegalArgumentException 异常
      checkArgument(variablesSet.isEmpty(),
          "PigFilter does not allow variables");
      // 创建特征集合，将输入节点的特征集合替换为 Pig 约定
      // replace 方法会创建新的特征集合，将约定特征设置为 PigRel.CONVENTION
      // 这样确保生成的 PigFilter 节点具有正确的 Pig 特征
      final RelTraitSet traitSet =
          input.getTraitSet().replace(PigRel.CONVENTION);
      // 创建并返回一个新的 PigFilter 对象
      // 参数说明：
      //   - input.getCluster(): 从输入节点获取关系表达式集群
      //   - traitSet: 上面创建的特征集合，包含 Pig 约定
      //   - input: 输入关系节点
      //   - condition: 过滤条件表达式
      return new PigFilter(input.getCluster(), traitSet, input, condition);
    }
  }

  /**
   * Implementation of
   * {@link org.apache.calcite.rel.core.RelFactories.AggregateFactory} that
   * returns a {@link PigAggregate}.
   */
  // 类文档注释：该类实现了 RelFactories.AggregateFactory 接口
  // AggregateFactory 是一个工厂接口，用于创建聚合关系节点
  // 该实现专门为 Pig 适配器设计，返回 PigAggregate 类型的节点
  // PigAggregate 是 Pig 特定的聚合节点，用于执行 GROUP BY 和聚合函数操作
  public static class PigAggregateFactory implements RelFactories.AggregateFactory {

    // 定义一个公共静态常量 INSTANCE，类型为 PigAggregateFactory，采用单例模式
    // 这是工厂类的实例，可以在整个应用中共享使用
    public static final PigAggregateFactory INSTANCE = new PigAggregateFactory();

    // 重写 createAggregate 方法，用于创建聚合关系节点
    // 参数说明：
    //   - input: 输入关系节点，表示要聚合的数据源
    //   - hints: 提示信息列表，用于影响优化器的决策（Pig 不支持）
    //   - groupSet: 分组字段集合，使用位集合表示哪些字段用于分组
    //   - groupSets: 分组集合列表，支持多级分组（如 GROUPING SETS）
    //   - aggCalls: 聚合函数调用列表，包含所有要执行的聚合函数
    // 返回值：创建的 PigAggregate 关系节点
    @Override public RelNode createAggregate(RelNode input,
        List<RelHint> hints,
        ImmutableBitSet groupSet, ImmutableList<ImmutableBitSet> groupSets,
        List<AggregateCall> aggCalls) {
      // 丢弃 hints 参数，因为 Pig 不支持提示信息
      // Util.discard 方法接收一个参数但不使用它，用于避免编译器警告
      // 这样可以明确表示我们有意忽略这个参数
      Util.discard(hints);
      // 创建并返回一个新的 PigAggregate 对象
      // 参数说明：
      //   - input.getCluster(): 从输入节点获取关系表达式集群
      //   - input.getTraitSet(): 从输入节点获取特征集合（已经包含 Pig 约定）
      //   - input: 输入关系节点
      //   - groupSet: 分组字段集合
      //   - groupSets: 分组集合列表（支持 GROUPING SETS、ROLLUP、CUBE）
      //   - aggCalls: 聚合函数调用列表（如 SUM、COUNT、AVG 等）
      return new PigAggregate(input.getCluster(), input.getTraitSet(), input,
          groupSet, groupSets, aggCalls);
    }
  }

  /**
   * Implementation of
   * {@link org.apache.calcite.rel.core.RelFactories.JoinFactory} that
   * returns a {@link PigJoin}.
   */
  // 类文档注释：该类实现了 RelFactories.JoinFactory 接口
  // JoinFactory 是一个工厂接口，用于创建连接关系节点
  // 该实现专门为 Pig 适配器设计，返回 PigJoin 类型的节点
  // PigJoin 是 Pig 特定的连接节点，用于执行两个数据源的连接操作
  public static class PigJoinFactory implements RelFactories.JoinFactory {

    // 定义一个公共静态常量 INSTANCE，类型为 PigJoinFactory，采用单例模式
    // 这是工厂类的实例，可以在整个应用中共享使用
    public static final PigJoinFactory INSTANCE = new PigJoinFactory();

    // 重写 createJoin 方法，用于创建连接关系节点
    // 参数说明：
    //   - left: 左输入关系节点，表示连接操作的左侧数据源
    //   - right: 右输入关系节点，表示连接操作的右侧数据源
    //   - hints: 提示信息列表，用于影响优化器的决策（Pig 不支持）
    //   - condition: 连接条件，是一个 RexNode 行表达式
    //   - variablesSet: 相关变量集合，用于子查询去相关化（Pig 不支持）
    //   - joinType: 连接类型（内连接、左外连接、右外连接、全外连接等）
    //   - semiJoinDone: 是否已完成半连接转换的标志（Pig 不支持）
    // 返回值：创建的 PigJoin 关系节点
    @Override public RelNode createJoin(RelNode left, RelNode right, List<RelHint> hints,
        RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType,
        boolean semiJoinDone) {
      // 丢弃 hints 参数，因为 Pig 不支持提示信息
      Util.discard(hints);
      // 丢弃 variablesSet 参数，因为 Pig 不支持相关变量
      Util.discard(variablesSet);
      // 丢弃 semiJoinDone 参数，因为 Pig 不支持半连接优化
      Util.discard(semiJoinDone);
      // 创建并返回一个新的 PigJoin 对象
      // 参数说明：
      //   - left.getCluster(): 从左输入节点获取关系表达式集群
      //   - left.getTraitSet(): 从左输入节点获取特征集合（已经包含 Pig 约定）
      //   - left: 左输入关系节点
      //   - right: 右输入关系节点
      //   - condition: 连接条件表达式（如 ON 子句中的条件）
      //   - joinType: 连接类型（INNER、LEFT、RIGHT、FULL 等）
      return new PigJoin(left.getCluster(), left.getTraitSet(), left, right, condition, joinType);
    }
  }
}
