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
// 声明包名，表示这个类属于 org.apache.calcite.plan 包，是 Calcite 查询优化器计划相关的包
package org.apache.calcite.plan;

// 导入 CalciteConnectionConfig 类，用于获取 Calcite 连接配置信息
import org.apache.calcite.config.CalciteConnectionConfig;
// 导入 CalciteSchema 类，表示 Calcite 的模式（Schema）定义
import org.apache.calcite.jdbc.CalciteSchema;
// 导入 Lattice 类，表示数据立方体（Lattice）的定义，用于物化视图优化
import org.apache.calcite.materialize.Lattice;
// 导入 MaterializationService 类，提供物化视图的服务接口
import org.apache.calcite.materialize.MaterializationService;
// 导入 TileKey 类，表示物化视图的键（Tile Key），用于标识特定的物化视图
import org.apache.calcite.materialize.TileKey;
// 导入 RelNode 类，表示关系代数表达式，是 Calcite 中查询的抽象表示
import org.apache.calcite.rel.RelNode;
// 导入 ImmutableBitSet 类，表示不可变的位集合，用于高效地表示列索引集合
import org.apache.calcite.util.ImmutableBitSet;
// 导入 Pair 类，表示一个键值对，用于返回两个相关联的对象
import org.apache.calcite.util.Pair;

// 导入 Nullable 注解，表示返回值可能为 null
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 List 接口，用于表示有序的集合
import java.util.List;

/**
 * Use of a lattice by the query optimizer.
 * 查询优化器使用的 Lattice（数据立方体）包装类
 * 
 * 这个类封装了 Lattice 对象和对应的星型模式表，为查询优化器提供了使用 Lattice 进行查询重写和物化视图的能力。
 * Lattice 是一种基于维度建模的数据结构，通常用于数据仓库和 OLAP 查询优化。
 * 
 * 主要功能：
 * 1. 将查询重写为使用 Lattice 结构，提高查询性能
 * 2. 获取或创建物化视图来满足聚合查询
 * 3. 管理星型模式的根表和维度表关系
 * 
 * 使用场景：
 * - 当查询涉及星型模式的维度表和事实表时
 * - 当需要利用物化视图加速聚合查询时
 * - 当查询优化器需要自动创建和使用物化视图时
 */
// 定义 RelOptLattice 类，作为查询优化器与 Lattice 之间的桥梁
public class RelOptLattice {
  // 成员变量：Lattice 对象，表示数据立方体的定义
  // Lattice 包含了维度表、事实表、度量、维度等信息，是物化视图优化的核心数据结构
  // public final 表示这个字段是公开的、不可变的，一旦构造就不能修改
  public final Lattice lattice;
  
  // 成员变量：RelOptTable 对象，表示星型模式表的关系优化表
  // RelOptTable 是 Calcite 中表的抽象表示，包含了表的元数据、统计信息等
  // starRelOptTable 通常指向星型模式中的事实表（Fact Table），是 Lattice 的核心表
  // public final 表示这个字段是公开的、不可变的
  public final RelOptTable starRelOptTable;

  // 构造方法：创建 RelOptLattice 实例
  // @param lattice Lattice 对象，包含数据立方体的定义
  // @param starRelOptTable 星型模式表的关系优化表，通常是事实表
  public RelOptLattice(Lattice lattice, RelOptTable starRelOptTable) {
    // 将传入的 lattice 参数赋值给成员变量 lattice
    this.lattice = lattice;
    // 将传入的 starRelOptTable 参数赋值给成员变量 starRelOptTable
    this.starRelOptTable = starRelOptTable;
  }

  // 方法：获取 Lattice 的根表（Root Table）
  // 根表通常是星型模式中的事实表，是所有维度表连接的中心表
  // @return RelOptTable 对象，表示 Lattice 的根表
  public RelOptTable rootTable() {
    // 通过 lattice.rootNode 获取 Lattice 的根节点，然后调用 relOptTable() 方法获取对应的 RelOptTable
    // rootNode 是 Lattice 的根节点，代表事实表
    // relOptTable() 是根节点的方法，返回对应的关系优化表
    return lattice.rootNode.relOptTable();
  }

  /** Rewrites a relational expression to use a lattice.
   * 将关系表达式重写为使用 Lattice 结构
   *
   * <p>Returns null if a rewrite is not possible.
   * 如果无法重写，则返回 null
   *
   * @param node Relational expression
   * 关系表达式，表示原始查询的 RelNode 树
   * @return Rewritten query
   * 重写后的查询，如果无法重写则返回 null
   */
  public @Nullable RelNode rewrite(RelNode node) {
    // 调用 RelOptMaterialization.tryUseStar 方法尝试使用星型表重写查询
    // node 是原始的关系表达式（原始查询）
    // starRelOptTable 是星型模式表，用于替换原始查询中的表引用
    // 如果重写成功，返回新的 RelNode；如果无法重写，返回 null
    return RelOptMaterialization.tryUseStar(node, starRelOptTable);
  }

  /** Retrieves a materialized table that will satisfy an aggregate query on
   * the star table.
   * 获取一个物化表，该表将满足星型表上的聚合查询
   *
   * <p>The current implementation creates a materialization and populates it,
   * provided that {@link Lattice#auto} is true.
   * 当前实现会创建一个物化视图并填充它，前提是 Lattice.auto 属性为 true
   * auto 表示自动物化，即系统会自动创建和管理物化视图
   *
   * <p>Future implementations might return materializations at a different
   * level of aggregation, from which the desired result can be obtained by
   * rolling up.
   * 未来的实现可能会返回不同聚合级别的物化视图，通过上卷（roll-up）操作得到所需结果
   * 上卷是指在更粗的粒度上重新聚合数据
   *
   * @param planner Current planner
   * 当前的关系优化器，用于获取配置信息和上下文
   * @param groupSet Grouping key
   * 分组键，表示 GROUP BY 子句中的列集合，使用 ImmutableBitSet 表示列索引
   * @param measureList Calls to aggregate functions
   * 聚合函数调用列表，表示 SELECT 子句中的聚合函数，如 SUM、COUNT、AVG 等
   * @return Materialized table
   * 返回一个 Pair 对象，包含两个元素：
   * 1. CalciteSchema.TableEntry：物化表的表条目，包含表名、表对象等信息
   * 2. TileKey：物化视图的键，用于唯一标识这个物化视图
   * 如果无法获取物化表，则返回 null
   */
  public @Nullable Pair<CalciteSchema.TableEntry, TileKey> getAggregate(
      RelOptPlanner planner, ImmutableBitSet groupSet,
      List<Lattice.Measure> measureList) {
    // 从优化器的上下文中获取 Calcite 连接配置
    // planner.getContext() 获取优化器的上下文对象
    // unwrap(CalciteConnectionConfig.class) 尝试将上下文解包为 CalciteConnectionConfig
    // CalciteConnectionConfig 包含了连接级别的配置，如是否自动创建物化视图等
    final CalciteConnectionConfig config =
        planner.getContext().unwrap(CalciteConnectionConfig.class);
    // 如果无法获取到配置对象，说明配置不可用，返回 null
    // 这种情况下无法创建物化视图
    if (config == null) {
      return null;
    }
    // 获取物化视图服务的单例实例
    // MaterializationService.instance() 返回全局唯一的物化视图服务对象
    // 这个服务负责管理所有的物化视图，包括创建、删除、查询等操作
    final MaterializationService service = MaterializationService.instance();
    // 判断是否需要创建物化视图
    // lattice.auto 表示 Lattice 是否配置为自动物化
    // config.createMaterializations() 表示连接配置是否允许创建物化视图
    // 只有当两者都为 true 时，才会创建新的物化视图
    boolean create = lattice.auto && config.createMaterializations();
    // 从星型表中获取 CalciteSchema 对象
    // unwrap(CalciteSchema.class) 尝试将 RelOptTable 解包为 CalciteSchema
    // CalciteSchema 包含了模式级别的信息，如表、函数、类型等
    final CalciteSchema schema = starRelOptTable.unwrap(CalciteSchema.class);
    // 如果无法获取到 CalciteSchema 对象，抛出异常
    // 这表示星型表的元数据不完整或不正确
    if (schema == null) {
      // 抛出 IllegalArgumentException，说明无法从 starRelOptTable 获取 CalciteSchema
      throw new IllegalArgumentException("Can't get CalciteSchema from "
          + starRelOptTable);
    }
    // 调用物化视图服务的 defineTile 方法定义或获取物化视图
    // 参数说明：
    // - lattice：Lattice 对象，包含数据立方体定义
    // - groupSet：分组键，表示 GROUP BY 的列
    // - measureList：聚合函数列表，表示需要计算的度量
    // - schema：CalciteSchema 对象，表示模式信息
    // - create：是否创建新的物化视图
    // - false：最后一个参数表示是否强制重新创建，false 表示如果已存在则使用现有的
    // 返回值：Pair 对象，包含表条目和 TileKey
    return service.defineTile(lattice, groupSet, measureList, schema, create,
        false);
  }
}
