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
package org.apache.calcite.materialize;

import org.apache.calcite.avatica.AvaticaUtils;
import org.apache.calcite.config.CalciteSystemProperty;
import org.apache.calcite.jdbc.CalcitePrepare;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.linq4j.Ord;
import org.apache.calcite.linq4j.tree.Primitive;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.rel2sql.SqlImplementor;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.runtime.Utilities;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.MaterializedViewTable;
import org.apache.calcite.schema.impl.StarTable;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlJoin;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUtil;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.statistic.MapSqlStatisticProvider;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Litmus;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.graph.DefaultDirectedGraph;
import org.apache.calcite.util.graph.DefaultEdge;
import org.apache.calcite.util.graph.DirectedGraph;
import org.apache.calcite.util.graph.TopologicalOrderIterator;
import org.apache.calcite.util.mapping.IntPair;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

import static org.apache.calcite.linq4j.Nullness.castNonNull;
import static org.apache.calcite.rel.rel2sql.SqlImplementor.POS;

import static java.util.Objects.requireNonNull;

/**
 * Lattice（格）类：表示基于星型模式的物化视图结构
 * 
 * 【类的作用】
 * Lattice 是 Calcite 中用于物化视图优化的核心类，它：
 * 1. 表示一个基于星型模式（star schema）的数据立方体结构
 * 2. 支持识别和推荐物化视图
 * 3. 提供基于立方体的查询优化能力
 * 4. 管理维度（dimensions）和度量（measures）
 * 5. 生成用于填充物化视图的 SQL 查询
 * 
 * 【核心概念】
 * - 星型模式：一个中心事实表（fact table）和多个维度表（dimension tables）
 * - Tile（瓦片）：物化视图的一个切片，包含特定的维度组合和度量
 * - Measure（度量）：聚合函数，如 SUM、COUNT、AVG 等
 * - Dimension（维度）：用于分组的列
 * - LatticeNode（格节点）：表示星型模式中的一个表及其连接关系
 * 
 * 【使用场景】
 * 当用户执行查询时，Calcite 可以：
 * 1. 检查是否有预计算的 Tile 可以加速查询
 * 2. 自动推荐应该创建哪些物化视图
 * 3. 利用现有的物化视图重写查询
 */
public class Lattice {
  /** 根模式（root schema）：包含所有表和子模式的顶层模式 */ // 根模式：包含所有表和子模式的顶层模式
  public final CalciteSchema rootSchema;
  
  /** 根节点：星型模式树的根节点，通常对应事实表 */ // 根节点：星型模式树的根节点，通常对应事实表
  public final LatticeRootNode rootNode;
  
  /** 列集合：包含所有列（基础列和派生列）的不可变列表，按顺序排列 */ // 列集合：包含所有列（基础列和派生列）的不可变列表，按顺序排列
  public final ImmutableList<Column> columns;
  
  /** 自动模式：是否自动识别和推荐物化视图 */ // 自动模式：是否自动识别和推荐物化视图
  public final boolean auto;
  
  /** 算法模式：是否使用算法自动计算最优的 Tile 集合 */ // 算法模式：是否使用算法自动计算最优的 Tile 集合
  public final boolean algorithm;
  
  /** 算法最大执行时间（毫秒）：算法运行的超时时间 */ // 算法最大执行时间（毫秒）：算法运行的超时时间
  public final long algorithmMaxMillis;
  
  /** 行数估计：未聚合的星型模式的估计行数 */ // 行数估计：未聚合的星型模式的估计行数
  public final double rowCountEstimate;
  
  /** 默认度量集合：默认的聚合函数列表，如 SUM、COUNT 等 */ // 默认度量集合：默认的聚合函数列表，如 SUM、COUNT 等
  public final ImmutableList<Measure> defaultMeasures;
  
  /** Tile 集合：所有预定义或计算出的物化视图切片 */ // Tile 集合：所有预定义或计算出的物化视图切片
  public final ImmutableList<Tile> tiles;
  
  /** 列使用情况：记录每列的使用方式（True=作为度量参数，False=作为维度） */ // 列使用情况：记录每列的使用方式（True=作为度量参数，False=作为维度）
  public final ImmutableListMultimap<Integer, Boolean> columnUses;
  
  /** 统计信息提供者：用于获取列的基数（distinct 值数量）等统计信息 */ // 统计信息提供者：用于获取列的基数（distinct 值数量）等统计信息
  public final LatticeStatisticProvider statisticProvider;

  /**
   * Lattice 私有构造函数
   * 
   * 【参数说明】
   * @param rootSchema 根模式，包含所有表和子模式
   * @param rootNode 根节点，表示星型模式的根（通常是事实表）
   * @param auto 是否自动识别和推荐物化视图
   * @param algorithm 是否使用算法自动计算最优 Tile
   * @param algorithmMaxMillis 算法最大执行时间（毫秒）
   * @param statisticProviderFactory 统计信息提供者工厂，用于创建统计信息提供者
   * @param rowCountEstimate 行数估计，如果为 null 则使用默认值 1000
   * @param columns 所有列的列表（包括基础列和派生列）
   * @param defaultMeasures 默认度量集合，必须是唯一且有序的
   * @param tiles Tile 集合
   * @param columnUses 列使用情况的多重映射
   * 
   * 【构造逻辑】
   * 1. 保存所有传入的参数
   * 2. 验证 Lattice 的有效性（检查根节点和度量参数）
   * 3. 如果行数估计为 null，设置默认值 1000
   * 4. 检查行数估计必须大于 0
   * 5. 使用工厂创建统计信息提供者
   */
  private Lattice(CalciteSchema rootSchema, LatticeRootNode rootNode,
      boolean auto, boolean algorithm, long algorithmMaxMillis,
      LatticeStatisticProvider.Factory statisticProviderFactory,
      @Nullable Double rowCountEstimate, ImmutableList<Column> columns,
      ImmutableSortedSet<Measure> defaultMeasures, ImmutableList<Tile> tiles,
      ImmutableListMultimap<Integer, Boolean> columnUses) {
    this.rootSchema = rootSchema; // 保存根模式引用
    this.rootNode = requireNonNull(rootNode, "rootNode"); // 保存根节点，确保非空
    this.columns = requireNonNull(columns, "columns"); // 保存列列表，确保非空
    this.auto = auto; // 保存自动模式标志
    this.algorithm = algorithm; // 保存算法模式标志
    this.algorithmMaxMillis = algorithmMaxMillis; // 保存算法最大执行时间
    this.defaultMeasures = defaultMeasures.asList(); // 将度量集合转换为列表（已确保唯一和有序）
    this.tiles = requireNonNull(tiles, "tiles"); // 保存 Tile 列表，确保非空
    this.columnUses = columnUses; // 保存列使用情况

    // 验证 Lattice 的有效性，如果无效则抛出异常
    assert isValid(Litmus.THROW);

    // 如果行数估计为 null，使用默认值 1000
    // 注：未来可以通过统计信息 SPI 改进这个默认值（CALCITE-429）
    if (rowCountEstimate == null) {
      // We could improve this when we fix
      // [CALCITE-429] Add statistics SPI for lattice optimization algorithm
      rowCountEstimate = 1000d;
    }
    // 检查行数估计必须大于 0
    checkArgument(rowCountEstimate > 0d);
    this.rowCountEstimate = rowCountEstimate; // 保存行数估计
    // 使用工厂创建统计信息提供者，并确保非空
    @SuppressWarnings("argument.type.incompatible")
    LatticeStatisticProvider statisticProvider =
        requireNonNull(statisticProviderFactory.apply(this));
    this.statisticProvider = statisticProvider; // 保存统计信息提供者
  }

  /**
   * 创建 Lattice 的静态工厂方法
   * 
   * 【参数说明】
   * @param schema 模式，用于解析 SQL
   * @param sql 定义 Lattice 的 SQL 查询（通常是星型模式的 SELECT 语句）
   * @param auto 是否自动识别和推荐物化视图
   * 
   * 【返回值】
   * @return 新创建的 Lattice 实例
   * 
   * 【实现细节】
   * 使用 Builder 模式构建 Lattice，设置 auto 属性后调用 build()
   */
  public static Lattice create(CalciteSchema schema, String sql, boolean auto) {
    return builder(schema, sql).auto(auto).build(); // 创建 Builder，设置 auto 标志，构建 Lattice
  }

  /**
   * 验证 Lattice 的有效性
   * 
   * 【参数说明】
   * @param litmus 验证器，用于报告验证失败信息
   * 
   * 【返回值】
   * @return 如果有效返回 true，否则返回 false
   * 
   * 【验证内容】
   * 1. 验证根节点是否有效
   * 2. 验证每个度量的参数是否都是已注册的列
   * 
   * 【注解说明】
   * @RequiresNonNull 确保在调用此方法时，rootNode、defaultMeasures 和 columns 已经初始化
   */
  @RequiresNonNull({"rootNode", "defaultMeasures", "columns"})
  private boolean isValid(
      @UnknownInitialization Lattice this,
      Litmus litmus) {
    // 验证根节点是否有效
    if (!rootNode.isValid(litmus)) {
      return false; // 根节点无效，Lattice 无效
    }
    // 验证每个度量的参数是否都是已注册的列
    for (Measure measure : defaultMeasures) {
      for (Column arg : measure.args) {
        // 检查度量参数是否在 columns 列表中
        if (columns.get(arg.ordinal) != arg) {
          // 度量参数不是已注册的列，报告失败
          return litmus.fail("measure argument must be a column registered in"
              + " this lattice: {}", measure);
        }
      }
    }
    return litmus.succeed(); // 所有验证通过，Lattice 有效
  }

  /**
   * 从 SQL 的 FROM 子句中提取表别名
   * 
   * 【参数说明】
   * @param from SQL 的 FROM 子句（可能是 JOIN 或表引用）
   * @param aliases 输出参数，用于收集表别名列表
   * @param current 当前别名（递归传递使用）
   * 
   * 【实现逻辑】
   * 1. 如果是 JOIN，递归处理左右子节点
   * 2. 如果是 AS 操作，提取别名并递归处理
   * 3. 否则，如果是表引用，添加别名（如果没有别名则从表名推断）
   */
  private static void populateAliases(SqlNode from, List<@Nullable String> aliases,
      @Nullable String current) {
    // 处理 JOIN 节点
    if (from instanceof SqlJoin) {
      SqlJoin join = (SqlJoin) from;
      populateAliases(join.getLeft(), aliases, null); // 递归处理左表
      populateAliases(join.getRight(), aliases, null); // 递归处理右表
    } 
    // 处理 AS 操作（表别名）
    else if (from.getKind() == SqlKind.AS) {
      populateAliases(SqlUtil.stripAs(from), aliases,
          SqlValidatorUtil.alias(from)); // 提取别名并递归处理
    } 
    // 处理普通表引用
    else {
      // 如果没有当前别名，则从表名推断
      if (current == null) {
        current = SqlValidatorUtil.alias(from);
      }
      aliases.add(current); // 添加别名到列表
    }
  }

  /**
   * 从关系表达式树中提取 TableScan 节点和连接条件
   * 
   * 【参数说明】
   * @param nodes 输出参数，用于收集所有 TableScan 节点
   * @param tempLinks 输出参数，用于收集连接条件（每个条件是一个 int[][]）
   * @param rel 当前处理的关系表达式节点
   * 
   * 【返回值】
   * @return 如果成功提取返回 true
   * 
   * 【实现逻辑】
   * 1. 跳过 LogicalProject 节点，直接处理其输入
   * 2. 遇到 TableScan 节点，添加到 nodes 列表
   * 3. 遇到 LogicalJoin 节点，递归处理左右子节点，并提取连接条件
   * 4. 其他节点类型抛出异常（Lattice 只支持特定的节点类型）
   * 
   * 【约束条件】
   * - 只支持内连接（不允许外连接）
   * - 只支持等值连接
   */
  private static boolean populate(List<TableScan> nodes, List<int[][]> tempLinks,
      RelNode rel) {
    // 跳过 Project 节点，直接处理其输入
    if (nodes.isEmpty() && rel instanceof LogicalProject) {
      return populate(nodes, tempLinks, ((LogicalProject) rel).getInput());
    }
    // 处理 TableScan 节点
    if (rel instanceof TableScan) {
      nodes.add((TableScan) rel); // 添加表扫描节点
      return true;
    }
    // 处理 LogicalJoin 节点
    if (rel instanceof LogicalJoin) {
      LogicalJoin join = (LogicalJoin) rel;
      // 检查是否为外连接，Lattice 只支持内连接
      if (join.getJoinType().isOuterJoin()) {
        throw new RuntimeException("only non nulls-generating join allowed, but got "
            + join.getJoinType());
      }
      // 递归处理左子节点
      populate(nodes, tempLinks, join.getLeft());
      // 递归处理右子节点
      populate(nodes, tempLinks, join.getRight());
      // 提取连接条件（每个条件转换为 int[][]）
      for (RexNode rex : RelOptUtil.conjunctions(join.getCondition())) {
        tempLinks.add(grab(nodes, rex)); // 将每个等值条件转换为 (输入, 字段) 对
      }
      return true;
    }
    // 不支持的节点类型
    throw new RuntimeException("Invalid node type "
        + rel.getClass().getSimpleName() + " in lattice query");
  }

  /**
   * 将等值连接表达式 "t1.c1 = t2.c2" 转换为两个 (input, field) 对
   * 
   * 【参数说明】
   * @param leaves TableScan 节点列表（对应各个表）
   * @param rex 等值连接表达式
   * 
   * 【返回值】
   * @return 二维数组，每个元素是 [输入索引, 字段索引]
   * 
   * 【示例】
   * 输入：equals($0.c1, $1.c2)
   * 输出：[[0, 1], [1, 2]] 表示第一个表的第1列等于第二个表的第2列
   * 
   * 【约束条件】
   * 只支持等值连接（EQUALS 操作符）
   */
  private static int[][] grab(List<TableScan> leaves, RexNode rex) {
    switch (rex.getKind()) {
    case EQUALS:
      break; // 只支持等值连接
    default:
      throw new AssertionError("only equi-join allowed"); // 不支持其他连接类型
    }
    final List<RexNode> operands = ((RexCall) rex).getOperands(); // 获取操作数
    // 将两个操作数都转换为 (输入, 字段) 对
    return new int[][] {
        inputField(leaves, operands.get(0)), // 左操作数
        inputField(leaves, operands.get(1))}; // 右操作数
  }

  /**
   * 将表达式转换为 (input, field) 对
   * 
   * 【参数说明】
   * @param leaves TableScan 节点列表
   * @param rex 表达式（必须是 RexInputRef）
   * 
   * @return 包含两个元素的数组：[输入索引, 字段索引]
   * 
   * 【实现逻辑】
   * 1. 检查表达式是否为 RexInputRef（列引用）
   * 2. 根据列的全局索引，确定它属于哪个表（输入）和表中的哪个字段
   * 
   * 【示例】
   * 假设有两个表：t1 有 3 列，t2 有 2 列
   * - 列索引 0-2 属于 t1（输入0）
   * - 列索引 3-4 属于 t2（输入1）
   * - 列索引 3 对应 [1, 0]（t2 的第 0 列）
   */
  private static int[] inputField(List<TableScan> leaves, RexNode rex) {
    // 只支持列引用
    if (!(rex instanceof RexInputRef)) {
      throw new RuntimeException("only equi-join of columns allowed: " + rex);
    }
    RexInputRef ref = (RexInputRef) rex;
    int start = 0; // 当前表的起始列索引
    // 遍历所有表，找到包含该列的表
    for (int i = 0; i < leaves.size(); i++) {
      final RelNode leaf = leaves.get(i);
      final int end = start + leaf.getRowType().getFieldCount(); // 当前表的结束列索引
      // 检查列索引是否在当前表的范围内
      if (ref.getIndex() < end) {
        return new int[] {i, ref.getIndex() - start}; // 返回 [表索引, 列在表中的索引]
      }
      start = end; // 移动到下一个表
    }
    throw new AssertionError("input not found"); // 不应该到达这里
  }

  /**
   * 返回 Lattice 的字符串表示
   * 
   * 【返回值】
   * @return 格式为 "rootNode:defaultMeasures" 的字符串
   */
  @Override public String toString() {
    return rootNode + ":" + defaultMeasures; // 返回根节点和默认度量的组合
  }

  /**
   * 生成 SQL 查询，用于填充指定的 Tile
   * 
   * 【参数说明】
   * @param groupSet 分组列的索引集合（维度）
   * @param aggCallList 聚合函数列表（度量）
   * 
   * 【返回值】
   * @return 生成的 SQL 查询字符串
   * 
   * 【实现细节】
   * 调用 sql(groupSet, true, aggCallList)，默认启用分组
   */
  public String sql(ImmutableBitSet groupSet, List<Measure> aggCallList) {
    return sql(groupSet, true, aggCallList); // 调用重载方法，启用分组
  }

  /**
   * 生成 SQL 查询，用于填充指定的 Tile
   * 
   * 【参数说明】
   * @param groupSet 分组列的索引集合（维度）
   * @param group 是否启用 GROUP BY 子句
   * @param aggCallList 聚合函数列表（度量）
   * 
   * 【返回值】
   * @return 生成的 SQL 查询字符串
   * 
   * 【实现细节】
   * 使用 Calcite 方言调用重载方法
   */
  public String sql(ImmutableBitSet groupSet, boolean group,
      List<Measure> aggCallList) {
    final SqlDialect dialect = SqlDialect.DatabaseProduct.CALCITE.getDialect(); // 使用 Calcite 方言
    return sql(groupSet, group, aggCallList, dialect); // 调用重载方法
  }

  /**
   * 生成 SQL 查询，用于填充指定的 Tile（完整版本）
   * 
   * 【参数说明】
   * @param groupSet 分组列的索引集合（维度）
   * @param group 是否启用 GROUP BY 子句
   * @param aggCallList 聚合函数列表（度量）
   * @param dialect SQL 方言（用于生成特定数据库的 SQL）
   * 
   * 【返回值】
   * @return 生成的 SQL 查询字符串
   * 
   * 【实现逻辑】
   * 1. 确定需要使用的节点（表）
   * 2. 构建 SELECT 子句（包含维度列和度量）
   * 3. 构建 FROM 子句（包含所有必要的表和 JOIN 条件）
   * 4. 构建 GROUP BY 子句（如果启用分组）
   * 
   * 【示例输出】
   * SELECT 
   *   sales.country AS c0, 
   *   SUM(sales.amount) AS m0
   * FROM sales AS sales
   *   JOIN products AS products ON sales.product_id = products.id
   * GROUP BY sales.country
   */
  public String sql(ImmutableBitSet groupSet, boolean group,
      List<Measure> aggCallList, SqlDialect dialect) {
    final List<LatticeNode> usedNodes = new ArrayList<>(); // 使用的节点列表
    if (group) {
      // 如果启用分组，确定需要哪些节点
      final ImmutableBitSet.Builder columnSetBuilder = groupSet.rebuild();
      // 添加度量参数使用的列
      for (Measure call : aggCallList) {
        for (Column arg : call.args) {
          columnSetBuilder.set(arg.ordinal);
        }
      }
      final ImmutableBitSet columnSet = columnSetBuilder.build();

      // 确定需要哪些节点：使用节点如果其列被使用，或者有子节点的列被使用
      for (LatticeNode node : rootNode.descendants) {
        if (ImmutableBitSet.range(node.startCol, node.endCol)
            .intersects(columnSet)) {
          node.use(usedNodes); // 标记节点为已使用
        }

        // 如果没有使用任何节点，至少使用根节点
        if (usedNodes.isEmpty()) {
          usedNodes.add(rootNode);
        }
      }
    } else {
      // 如果不分组，使用所有节点
      usedNodes.addAll(rootNode.descendants);
    }

    // 构建 SELECT 子句
    final StringBuilder buf = new StringBuilder("SELECT ");
    // 构建 GROUP BY 子句
    final StringBuilder groupBuf = new StringBuilder("\nGROUP BY ");
    int k = 0; // 计数器，用于添加逗号分隔符
    final Set<String> columnNames = new HashSet<>(); // 用于确保列名唯一
    final SqlWriter w = createSqlWriter(dialect, buf, resolveField(dialect)); // 创建 SQL 写入器
    if (groupSet != null) {
      // 添加维度列到 SELECT 和 GROUP BY
      for (int i : groupSet) {
        if (k++ > 0) {
          buf.append(", "); // 添加逗号分隔符
          groupBuf.append(", ");
        }
        final Column column = columns.get(i);
        column.toSql(w); // 将列写入 SELECT 子句
        column.toSql(w.with(groupBuf)); // 将列写入 GROUP BY 子句
        if (column instanceof BaseColumn) {
          columnNames.add(((BaseColumn) column).column); // 记录基础列名
        }
        // 如果列有自定义别名，添加 AS 子句
        if (!column.alias.equals(column.defaultAlias())) {
          buf.append(" AS ");
          dialect.quoteIdentifier(buf, column.alias);
        }
      }
      // 添加度量到 SELECT 子句
      int m = 0; // 度量计数器
      for (Measure measure : aggCallList) {
        if (k++ > 0) {
          buf.append(", "); // 添加逗号分隔符
        }
        buf.append(measure.agg.getName()) // 添加聚合函数名
            .append("(");
        if (measure.args.isEmpty()) {
          buf.append("*"); // COUNT(*) 的情况
        } else {
          // 添加度量参数
          int z = 0;
          for (Column arg : measure.args) {
            if (z++ > 0) {
              buf.append(", ");
            }
            arg.toSql(w); // 将参数转换为 SQL
          }
        }
        buf.append(") AS ");
        // 生成唯一的度量别名（m0, m1, m2, ...）
        String measureName;
        while (!columnNames.add(measureName = "m" + m)) {
          ++m; // 确保别名唯一
        }
        dialect.quoteIdentifier(buf, measureName);
      }
    } else {
      // 如果没有分组列，选择所有列
      buf.append("*");
    }
    
    // 构建 FROM 子句和 JOIN 条件
    buf.append("\nFROM ");
    for (LatticeNode node : usedNodes) {
      if (node instanceof LatticeChildNode) {
        buf.append("\nJOIN "); // 子节点使用 JOIN
      }
      // 添加表名和别名
      dialect.quoteIdentifier(buf, node.table.t.getQualifiedName());
      String alias = node.alias;
      if (alias != null) {
        buf.append(" AS ");
        dialect.quoteIdentifier(buf, alias);
      }
      // 如果是子节点，添加 JOIN 条件
      if (node instanceof LatticeChildNode) {
        final LatticeChildNode node1 = (LatticeChildNode) node;
        buf.append(" ON ");
        k = 0;
        // 添加所有连接条件（用 AND 连接）
        for (IntPair pair : node1.link) {
          if (k++ > 0) {
            buf.append(" AND ");
          }
          final Column left = columns.get(node1.parent.startCol + pair.source);
          left.toSql(w); // 左侧列
          buf.append(" = ");
          final Column right = columns.get(node.startCol + pair.target);
          right.toSql(w); // 右侧列
        }
      }
    }
    
    // 调试输出
    if (CalciteSystemProperty.DEBUG.value()) {
      System.out.println("Lattice SQL:\n"
          + buf);
    }
    
    // 添加 GROUP BY 子句
    if (group) {
      if (groupSet.isEmpty()) {
        groupBuf.append("()"); // 空分组
      }
      buf.append(groupBuf);
    }
    return buf.toString(); // 返回生成的 SQL
  }

  /**
   * 创建字段解析函数：将字段索引转换为 SqlNode
   * 
   * 【参数说明】
   * @param dialect SQL 方言
   * 
   * @return 函数，输入字段索引，返回对应的 SqlNode
   * 
   * 【实现逻辑】
   * 1. 如果是基础列，返回 SqlIdentifier（表名.列名）
   * 2. 如果是派生列，将 RexNode 转换为 SqlNode
   * 
   * 【示例】
   * - BaseColumn: new SqlIdentifier(["sales", "amount"], POS)
   * - DerivedColumn: 将表达式转换为 SQL
   */
  private IntFunction<SqlNode> resolveField(SqlDialect dialect) {
    final IntFunction<SqlNode>[] fieldFuncRef = new IntFunction[1];
    fieldFuncRef[0] = f -> {
      Column column = columns.get(f);
      if (column instanceof BaseColumn) {
        // 基础列：返回表名.列名的标识符
        return new SqlIdentifier(ImmutableList.of(((BaseColumn) column).column), POS);
      }
      if (column instanceof DerivedColumn) {
        // 派生列：将 RexNode 转换为 SqlNode
        return new SqlImplementor.SimpleContext(dialect, fieldFuncRef[0])
            .toSql(null, ((DerivedColumn) column).e);
      }
      throw new UnsupportedOperationException(); // 不支持的列类型
    };
    return fieldFuncRef[0];
  }

  /**
   * 创建 SQL 写入器上下文
   * 
   * 【参数说明】
   * @param dialect SQL 方言
   * @param buf 用于构建 SQL 的 StringBuilder
   * @param field 字段解析函数
   * 
   * @return SqlWriter 实例
   * 
   * 【作用】
   * SqlWriter 提供了将 RexNode 表达式转换为 SQL 的能力
   */
  public SqlWriter createSqlWriter(SqlDialect dialect, StringBuilder buf,
      IntFunction<SqlNode> field) {
    return new SqlWriter(this, dialect, buf,
        new SqlImplementor.SimpleContext(dialect, field));
  }

  /**
   * 生成统计 distinct 值数量的 SQL 查询
   * 
   * 【参数说明】
   * @param groupSet 分组列的索引集合
   * 
   * 【返回值】
   * @return SQL 查询，返回 distinct 值的数量
   * 
   * 【示例】
   * 输入：groupSet = {0, 1}
   * 输出：select count(*) as c from (SELECT col0, col1 FROM ... GROUP BY col0, col1)
   */
  public String countSql(ImmutableBitSet groupSet) {
    return "select count(*) as c from ("
        + sql(groupSet, ImmutableList.of()) // 生成分组查询（无度量）
        + ")";
  }

  /**
   * 创建 StarTable（星型表）
   * 
   * 【返回值】
   * @return StarTable 实例，包含 Lattice 中的所有表
   * 
   * 【作用】
   * StarTable 是 Calcite 中表示星型模式的特殊表类型
   */
  public StarTable createStarTable() {
    final List<Table> tables = new ArrayList<>();
    // 收集所有节点对应的表
    for (LatticeNode node : rootNode.descendants) {
      tables.add(node.table.t.unwrapOrThrow(Table.class));
    }
    return StarTable.of(this, tables); // 创建 StarTable
  }

  /**
   * 创建 Lattice Builder
   * 
   * 【参数说明】
   * @param calciteSchema 模式
   * @param sql 定义 Lattice 的 SQL 查询
   * 
   * @return Builder 实例
   * 
   * 【实现细节】
   * 使用默认的统计信息提供者（MapSqlStatisticProvider）
   */
  public static Builder builder(CalciteSchema calciteSchema, String sql) {
    return builder(new LatticeSpace(MapSqlStatisticProvider.INSTANCE),
        calciteSchema, sql);
  }

  /**
   * 创建 Lattice Builder（带 LatticeSpace）
   * 
   * 【参数说明】
   * @param space Lattice 空间，用于注册和共享 LatticeTable
   * @param calciteSchema 模式
   * @param sql 定义 Lattice 的 SQL 查询
   * 
   * @return Builder 实例
   */
  static Builder builder(LatticeSpace space, CalciteSchema calciteSchema,
      String sql) {
    return new Builder(space, calciteSchema, sql);
  }

  /**
   * 将 AggregateCall 列表转换为 Measure 列表
   * 
   * 【参数说明】
   * @param aggCallList 聚合调用列表
   * 
   * @return Measure 列表
   */
  public List<Measure> toMeasures(List<AggregateCall> aggCallList) {
    return Util.transform(aggCallList, this::toMeasure); // 转换每个 AggregateCall
  }

  /**
   * 将单个 AggregateCall 转换为 Measure
   * 
   * 【参数说明】
   * @param aggCall 聚合调用
   * 
   * @return Measure 实例
   */
  private Measure toMeasure(AggregateCall aggCall) {
    return new Measure(aggCall.getAggregation(), aggCall.isDistinct(),
        aggCall.name, Util.transform(aggCall.getArgList(), columns::get));
  }

  /**
   * 计算 Tile 集合
   * 
   * @return Tile 集合
   * 
   * 【实现逻辑】
   * - 如果 algorithm 为 false，返回预定义的 tiles
   * - 如果 algorithm 为 true，使用 TileSuggester 算法计算最优的 Tile 集合
   */
  public Iterable<? extends Tile> computeTiles() {
    if (!algorithm) {
      return tiles; // 返回预定义的 tiles
    }
    return new TileSuggester(this).tiles(); // 使用算法计算最优 tiles
  }

  /**
   * 获取未聚合的星型模式的行数估计
   * 
   * @return 事实表的估计行数
   */
  public double getFactRowCount() {
    return rowCountEstimate;
  }

  /**
   * 获取指定维度的 Tile 的行数估计
   * 
   * 【参数说明】
   * @param columns 维度列列表
   * 
   * @return 估计的行数
   * 
   * 【实现细节】
   * 使用统计信息提供者计算基数
   */
  public double getRowCount(List<Column> columns) {
    return statisticProvider.cardinality(columns);
  }

  /**
   * 静态方法：根据事实表行数和各列的基数计算 Tile 行数
   * 
   * 【参数说明】
   * @param factCount 事实表行数
   * @param columnCounts 各列的基数（distinct 值数量）
   * 
   * @return 估计的 Tile 行数
   * 
   * 【数学原理】
   * 使用概率公式计算期望的不同值数量：
   * 当从 n 个整数中选择 p 个值时（有放回），期望的不同值数量为：
   * n * (1 - ((n - 1) / n) ^ p)
   * 
   * 对于多个均匀分布的属性，它们的行为类似于一个均匀分布的属性，
   * 其不同值数量为各属性不同值数量的乘积。
   */
  public static double getRowCount(double factCount, double... columnCounts) {
    return getRowCount(factCount, Primitive.asList(columnCounts));
  }

  /**
   * 静态方法：根据事实表行数和各列的基数计算 Tile 行数（列表版本）
   * 
   * 【参数说明】
   * @param factCount 事实表行数
   * @param columnCounts 各列的基数列表
   * 
   * @return 估计的 Tile 行数
   * 
   * 【数学原理】
   * 假设有多个均匀分布的属性 A1...Am，分别有 N1...Nm 个不同值
   * 它们的行为类似于一个均匀分布的属性，有 N1 * ... * Nm 个不同值
   * 
   * 使用公式：n * (1 - ((n - 1) / n) ^ factCount)
   * 其中 n = 所有列基数的乘积
   * 
   * 【边界情况】
   * - 如果某列基数为 1，不影响结果
   * - 如果 n 很大，((n-1)/n) 可能接近 1，导致数值精度问题
   * - 结果上限为 factCount（不能超过事实表行数）
   */
  public static double getRowCount(double factCount,
      List<Double> columnCounts) {
    // The expected number of distinct values when choosing p values
    // with replacement from n integers is n . (1 - ((n - 1) / n) ^ p).
    //
    // If we have several uniformly distributed attributes A1 ... Am
    // with N1 ... Nm distinct values, they behave as one uniformly
    // distributed attribute with N1 * ... * Nm distinct values.
    double n = 1d; // 计算所有列基数的乘积
    for (Double columnCount : columnCounts) {
      if (columnCount > 1d) {
        n *= columnCount;
      }
    }
    final double a = (n - 1d) / n; // 计算衰减因子
    if (a == 1d) {
      // 如果 n 很大，(n-1)/n 接近 1，直接返回事实表行数
      // A under-flows if nn is large.
      return factCount;
    }
    // 应用公式：n * (1 - a ^ factCount)
    final double v = n * (1d - Math.pow(a, factCount));
    // 结果上限为事实表行数（避免数值误差导致超过事实表行数）
    // Cap at fact-row-count, because numerical artifacts can cause it
    // to go a few % over.
    return Math.min(v, factCount);
  }

  /**
   * 获取所有列的唯一别名列表
   * 
   * @return 列别名列表
   */
  public List<String> uniqueColumnNames() {
    return Util.transform(columns, column -> column.alias);
  }

  /**
   * 将基础列转换为路径和偏移量
   * 
   * 【参数说明】
   * @param c 基础列
   * 
   * @return 包含路径和列偏移量的 Pair
   * 
   * 【作用】
   * 用于确定列在 Lattice 树中的位置
   */
  Pair<Path, Integer> columnToPathOffset(BaseColumn c) {
    // 遍历所有节点和路径
    for (Pair<LatticeNode, Path> p
        : Pair.zip(rootNode.descendants, rootNode.paths)) {
      if (Objects.equals(p.left.alias, c.table)) {
        // 找到对应的节点，返回路径和列偏移量
        return Pair.of(p.right, c.ordinal - p.left.startCol);
      }
    }
    throw new AssertionError("lattice column not found: " + c);
  }

  /**
   * 获取 Lattice 中的所有表集合
   * 
   * @return LatticeTable 集合
   */
  public Set<LatticeTable> tables() {
    return rootNode.descendants.stream().map(n -> n.table)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * 获取指定表别名的第一列的序号
   * 
   * 【参数说明】
   * @param tableAlias 表别名
   * 
   * @return 列序号，如果表不存在返回 -1
   */
  public int firstColumn(String tableAlias) {
    for (Column column : columns) {
      if (column instanceof BaseColumn
          && ((BaseColumn) column).table.equals(tableAlias)) {
        return column.ordinal; // 返回第一列的序号
      }
    }
    return -1; // 表不存在
  }

  /**
   * 判断列是否总是作为度量参数使用
   * 
   * 【参数说明】
   * @param column 列（基础列或派生列）
   * 
   * @return 如果所有使用都是作为度量参数返回 true，否则返回 false
   * 
   * 【示例】
   * 查询：select sum(x + y), sum(a + b) from t group by x + y
   * - "x + y" 既作为度量参数，又作为分组维度，返回 false
   * - "a + b" 只作为度量参数，返回 true
   */
  public boolean isAlwaysMeasure(Column column) {
    return !columnUses.get(column.ordinal).contains(false); // 检查是否有非度量使用
  }

  /**
   * 临时图的边
   * 
   * 【作用】
   * 在构建 Lattice 树时，使用临时图来表示表之间的连接关系
   * Edge 包含连接条件中的所有 (源列, 目标列) 对
   */
  private static class Edge extends DefaultEdge {
    /** 边的工厂方法 */
    public static final DirectedGraph.EdgeFactory<Vertex, Edge> FACTORY =
        Edge::new;

    /** 连接条件中的所有 (源列索引, 目标列索引) 对 */
    final List<IntPair> pairs = new ArrayList<>();

    /**
     * 构造函数
     * 
     * @param source 源顶点
     * @param target 目标顶点
     */
    Edge(Vertex source, Vertex target) {
      super(source, target);
    }

    /**
     * 获取目标顶点
     */
    Vertex getTarget() {
      return (Vertex) target;
    }

    /**
     * 获取源顶点
     */
    Vertex getSource() {
      return (Vertex) source;
    }
  }

  /**
   * 临时图的顶点
   * 
   * 【作用】
   * 表示图中的一个表，包含表引用和别名
   */
  private static class Vertex {
    /** 表引用 */
    final LatticeTable table;
    /** 表别名（可能为 null） */
    final @Nullable String alias;

    /**
     * 构造函数
     * 
     * @param table 表引用
     * @param alias 表别名
     */
    private Vertex(LatticeTable table, @Nullable String alias) {
      this.table = table;
      this.alias = alias;
    }
  }

  /**
   * Measure（度量）类：表示 Lattice 中的一个聚合函数
   * 
   * 【类的作用】
   * Measure 表示一个聚合函数调用，如 SUM、COUNT、AVG 等
   * 它是不可变的（immutable）
   * 
   * 【示例】
   * - SUM(products.weight)：计算产品重量之和
   * - COUNT()：计数（等同于 COUNT(*)）
   * - COUNT(DISTINCT customer.id)：计算不同客户的数量
   */
  public static class Measure implements Comparable<Measure> {
    /** 聚合函数（如 SUM、COUNT、AVG 等） */
    public final SqlAggFunction agg;
    /** 是否使用 DISTINCT */
    public final boolean distinct;
    /** 度量名称（可能为 null） */
    public final @Nullable String name;
    /** 度量参数列表（列） */
    public final ImmutableList<Column> args;
    /** 度量的摘要字符串（用于调试和比较） */
    public final String digest;

    /**
     * 构造函数
     * 
     * @param agg 聚合函数
     * @param distinct 是否使用 DISTINCT
     * @param name 度量名称
     * @param args 度量参数（列的迭代器）
     */
    public Measure(SqlAggFunction agg, boolean distinct, @Nullable String name,
        Iterable<Column> args) {
      this.agg = requireNonNull(agg, "agg"); // 确保聚合函数非空
      this.distinct = distinct; // 保存 DISTINCT 标志
      this.name = name; // 保存名称
      this.args = ImmutableList.copyOf(args); // 保存参数列表（不可变副本）

      // 构建摘要字符串（用于比较和调试）
      final StringBuilder b = new StringBuilder()
          .append(agg) // 添加聚合函数名
          .append(distinct ? "(DISTINCT " : "("); // 添加 DISTINCT（如果有）
      for (Ord<Column> arg : Ord.zip(this.args)) {
        if (arg.i > 0) {
          b.append(", "); // 添加逗号分隔符
        }
        // 添加参数
        if (arg.e instanceof BaseColumn) {
          // 基础列：使用 表名.列名
          b.append(((BaseColumn) arg.e).table);
          b.append('.');
          b.append(((BaseColumn) arg.e).column);
        } else {
          // 派生列：使用别名
          b.append(arg.e.alias);
        }
      }
      b.append(')'); // 闭合括号
      this.digest = b.toString(); // 保存摘要
    }

    /**
     * 比较两个 Measure
     * 
     * 【比较顺序】
     * 1. 先比较参数列表
     * 2. 再比较聚合函数名称
     * 3. 最后比较 DISTINCT 标志
     * 
     * @param measure 另一个 Measure
     * @return 负数、0 或正数
     */
    @Override public int compareTo(Measure measure) {
      int c = compare(args, measure.args); // 先比较参数
      if (c == 0) {
        c = agg.getName().compareTo(measure.agg.getName()); // 再比较聚合函数名
        if (c == 0) {
          c = Boolean.compare(distinct, measure.distinct); // 最后比较 DISTINCT
        }
      }
      return c;
    }

    /**
     * 返回度量的字符串表示
     * 
     * @return 摘要字符串
     */
    @Override public String toString() {
      return digest;
    }

    /**
     * 计算哈希码
     * 
     * @return 哈希码
     */
    @Override public int hashCode() {
      return Objects.hash(agg, args);
    }

    /**
     * 判断两个 Measure 是否相等
     * 
     * @param obj 另一个对象
     * @return 如果相等返回 true
     * 
     * 【相等条件】
     * - 聚合函数相同
     * - 参数列表相同
     * - DISTINCT 标志相同
     */
    @Override public boolean equals(@Nullable Object obj) {
      return obj == this
          || obj instanceof Measure
          && this.agg.equals(((Measure) obj).agg)
          && this.args.equals(((Measure) obj).args)
          && this.distinct == ((Measure) obj).distinct;
    }

    /**
     * 返回度量参数的序号集合（BitSet）
     * 
     * @return 参数列序号的 BitSet
     */
    public ImmutableBitSet argBitSet() {
      final ImmutableBitSet.Builder bitSet = ImmutableBitSet.builder();
      for (Column arg : args) {
        bitSet.set(arg.ordinal); // 添加参数列的序号
      }
      return bitSet.build();
    }

    /**
     * 返回度量参数的序号列表
     * 
     * @return 参数列序号的列表
     */
    public List<Integer> argOrdinals() {
      return Util.transform(args, column -> column.ordinal);
    }

    /**
     * 比较两个列列表
     * 
     * @param list0 第一个列表
     * @param list1 第二个列表
     * @return 负数、0 或正数
     */
    private static int compare(List<Column> list0, List<Column> list1) {
      final int size = Math.min(list0.size(), list1.size()); // 取最小长度
      for (int i = 0; i < size; i++) {
        final int o0 = list0.get(i).ordinal; // 比较列序号
        final int o1 = list1.get(i).ordinal;
        final int c = Utilities.compare(o0, o1);
        if (c != 0) {
          return c; // 如果不相等，立即返回
        }
      }
      return Utilities.compare(list0.size(), list1.size()); // 比较列表长度
    }

    /**
     * 复制度量，映射其参数
     * 
     * @param mapper 参数映射函数
     * @return 新的 Measure 实例
     */
    Measure copy(Function<Column, Column> mapper) {
      return new Measure(agg, distinct, name, Util.transform(args, mapper));
    }
  }

  /**
   * Column（列）抽象类：表示 Lattice 中的列
   * 
   * 【类的作用】
   * Lattice 中的列可以是：
   * - BaseColumn：基础列（来自表的列）
   * - DerivedColumn：派生列（基于 SQL 表达式的列）
   * 
   * 每列都有一个序号（ordinal）和一个别名（alias）
   * 别名在整个 Lattice 中是唯一的
   */
  public abstract static class Column implements Comparable<Column> {
    /** 列在 Lattice 中的序号（从 0 开始） */
    public final int ordinal;
    /** 列的别名，在整个 Lattice 中唯一 */
    public final String alias;

    /**
     * 构造函数
     * 
     * @param ordinal 列序号
     * @param alias 列别名
     */
    private Column(int ordinal, String alias) {
      this.ordinal = ordinal;
      this.alias = requireNonNull(alias, "alias"); // 确保别名非空
    }

    /**
     * 将列列表转换为序号的 BitSet
     * 
     * @param columns 列列表
     * @return 序号的 BitSet
     */
    static ImmutableBitSet toBitSet(List<Column> columns) {
      final ImmutableBitSet.Builder builder = ImmutableBitSet.builder();
      for (Column column : columns) {
        builder.set(column.ordinal); // 添加列序号
      }
      return builder.build();
    }

    /**
     * 比较两个列（按序号）
     * 
     * @param column 另一个列
     * @return 负数、0 或正数
     */
    @Override public int compareTo(Column column) {
      return Utilities.compare(ordinal, column.ordinal);
    }

    /**
     * 计算哈希码（基于序号）
     * 
     * @return 哈希码
     */
    @Override public int hashCode() {
      return ordinal;
    }

    /**
     * 判断两个列是否相等（基于序号）
     * 
     * @param obj 另一个对象
     * @return 如果相等返回 true
     */
    @Override public boolean equals(@Nullable Object obj) {
      return obj == this
          || obj instanceof Column
          && this.ordinal == ((Column) obj).ordinal;
    }

    /**
     * 将列转换为 SQL
     * 
     * @param writer SQL 写入器
     */
    public abstract void toSql(SqlWriter writer);

    /**
     * 返回 SQL 默认会给出的别名
     * 
     * @return 默认别名（对于派生列可能为 null）
     */
    public abstract @Nullable String defaultAlias();
  }

  /**
   * BaseColumn（基础列）类：表示来自表的列
   * 
   * 【类的作用】
   * BaseColumn 表示星型模式中某个表的某个列
   * 它由表别名和列名标识
   * 
   * 【示例】
   * - sales.amount：sales 表的 amount 列
   * - products.name：products 表的 name 列
   */
  public static class BaseColumn extends Column {
    /** 表别名（列所属的表引用） */
    public final String table;

    /** 列名（在表中唯一，但在 Lattice 中不一定唯一） */
    public final String column;

    /**
     * 构造函数
     * 
     * @param ordinal 列序号
     * @param table 表别名
     * @param column 列名
     * @param alias 列别名（在整个 Lattice 中唯一）
     */
    private BaseColumn(int ordinal, String table, String column, String alias) {
      super(ordinal, alias);
      this.table = requireNonNull(table, "table"); // 确保表别名非空
      this.column = requireNonNull(column, "column"); // 确保列名非空
    }

    /**
     * 返回列的字符串表示
     * 
     * @return [table, column] 列表
     */
    @Override public String toString() {
      return identifiers().toString();
    }

    /**
     * 返回标识符列表
     * 
     * @return [table, column]
     */
    public List<String> identifiers() {
      return ImmutableList.of(table, column);
    }

    /**
     * 将列转换为 SQL
     * 
     * @param writer SQL 写入器
     */
    @Override public void toSql(SqlWriter writer) {
      writer.dialect.quoteIdentifier(writer.buf, identifiers()); // 引用标识符
    }

    /**
     * 返回默认别名
     * 
     * @return 列名
     */
    @Override public String defaultAlias() {
      return column;
    }
  }

  /**
   * DerivedColumn（派生列）类：表示基于 SQL 表达式的列
   * 
   * 【类的作用】
   * DerivedColumn 表示一个基于 SQL 表达式的计算列
   * 它不是直接来自表，而是通过表达式计算得出
   * 
   * 【示例】
   * - x + y：两个列的和
   * - price * quantity：价格乘以数量
   * - UPPER(name)：大写转换
   */
  public static class DerivedColumn extends Column {
    /** 表达式（RexNode 形式） */
    public final RexNode e;
    /** 表达式使用的表别名列表 */
    final List<String> tables;

    /**
     * 构造函数
     * 
     * @param ordinal 列序号
     * @param alias 列别名
     * @param e 表达式
     * @param tables 表达式使用的表别名列表
     */
    private DerivedColumn(int ordinal, String alias, RexNode e,
        List<String> tables) {
      super(ordinal, alias);
      this.e = e; // 保存表达式
      this.tables = ImmutableList.copyOf(tables); // 保存表列表（不可变副本）
    }

    /**
     * 返回列的字符串表示
     * 
     * @return [表达式, 别名]
     */
    @Override public String toString() {
      return Arrays.toString(new Object[] {e, alias});
    }

    /**
     * 将列转换为 SQL
     * 
     * @param writer SQL 写入器
     */
    @Override public void toSql(SqlWriter writer) {
      writer.write(e); // 将表达式转换为 SQL
    }

    /**
     * 返回默认别名
     * 
     * @return null（表达式没有默认别名）
     */
    @Override public @Nullable String defaultAlias() {
      // there is no default alias for an expression
      return null;
    }
  }

  /**
   * SqlWriter 类：用于将列转换为 SQL 的上下文
   * 
   * 【类的作用】
   * SqlWriter 提供了将 RexNode 表达式转换为特定方言 SQL 的能力
   * 它封装了 Lattice、方言、缓冲区和上下文信息
   */
  public static class SqlWriter {
    /** Lattice 引用 */
    public final Lattice lattice;
    /** SQL 缓冲区 */
    public final StringBuilder buf;
    /** SQL 方言 */
    public final SqlDialect dialect;
    /** SQL 实现上下文（用于转换 RexNode 为 SqlNode） */
    private final SqlImplementor.SimpleContext context;

    /**
     * 构造函数
     * 
     * @param lattice Lattice 引用
     * @param dialect SQL 方言
     * @param buf SQL 缓冲区
     * @param context SQL 实现上下文
     */
    SqlWriter(Lattice lattice, SqlDialect dialect, StringBuilder buf,
        SqlImplementor.SimpleContext context) {
      this.lattice = lattice;
      this.context = context;
      this.buf = buf;
      this.dialect = dialect;
    }

    /**
     * 将写入器重新绑定到不同的 StringBuilder
     * 
     * @param buf 新的 StringBuilder
     * @return 新的 SqlWriter 实例
     */
    public SqlWriter with(StringBuilder buf) {
      return new SqlWriter(lattice, dialect, buf, context);
    }

    /**
     * 写入表达式
     * 
     * @param e RexNode 表达式
     * @return this（支持链式调用）
     */
    public SqlWriter write(RexNode e) {
      final SqlNode node = context.toSql(null, e); // 将 RexNode 转换为 SqlNode
      buf.append(node.toSqlString(dialect)); // 将 SqlNode 转换为 SQL 字符串
      return this;
    }
  }

  /**
   * Builder（构建器）类：用于构建 Lattice 实例
   * 
   * 【类的作用】
   * Builder 提供了逐步构建 Lattice 的能力
   * 它解析 SQL 查询，构建节点树，收集列和度量
   * 
   * 【构建流程】
   * 1. 解析 SQL 查询
   * 2. 提取 TableScan 节点和连接条件
   * 3. 构建图结构
   * 4. 将图转换为树结构
   * 5. 收集列和度量
   * 6. 构建 Lattice 实例
   */
  public static class Builder {
    /** 根节点 */
    private final LatticeRootNode rootNode;
    /** 基础列列表 */
    private final ImmutableList<BaseColumn> baseColumns;
    /** 按别名索引的列多重映射 */
    private final ImmutableListMultimap<String, Column> columnsByAlias;
    /** 默认度量集合（有序集合） */
    private final NavigableSet<Measure> defaultMeasureSet =
        new TreeSet<>();
    /** Tile 列表构建器 */
    private final ImmutableList.Builder<Tile> tileListBuilder =
        ImmutableList.builder();
    /** 列使用情况多重映射 */
    private final Multimap<Integer, Boolean> columnUses =
        LinkedHashMultimap.create();
    /** 根模式 */
    private final CalciteSchema rootSchema;
    /** 算法模式标志 */
    private boolean algorithm = false;
    /** 算法最大执行时间（毫秒） */
    private long algorithmMaxMillis = -1;
    /** 自动模式标志 */
    private boolean auto = true;
    /** 行数估计（可能为 null） */
    private @MonotonicNonNull Double rowCountEstimate;
    /** 统计信息提供者名称（可能为 null） */
    private @Nullable String statisticProvider;
    /** 派生列映射（按表达式字符串） */
    private final Map<String, DerivedColumn> derivedColumnsByName =
        new LinkedHashMap<>();

    /**
     * 构造函数（从 SQL 构建）
     * 
     * 【参数说明】
     * @param space Lattice 空间
     * @param schema 模式
     * @param sql 定义 Lattice 的 SQL 查询
     * 
     * 【构建流程】
     * 1. 保存根模式
     * 2. 解析 SQL 查询
     * 3. 提取 TableScan 节点和连接条件
     * 4. 获取表别名
     * 5. 构建图结构
     * 6. 将图转换为树结构
     * 7. 修复节点树（设置别名和列）
     */
    public Builder(LatticeSpace space, CalciteSchema schema, String sql) {
      this.rootSchema = requireNonNull(schema.root()); // 保存根模式
      checkArgument(rootSchema.isRoot(), "must be root schema"); // 确保是根模式
      
      // 解析 SQL 查询
      CalcitePrepare.ConvertResult parsed =
          Schemas.convert(MaterializedViewTable.MATERIALIZATION_CONNECTION,
              schema, schema.path(null), sql);

      // Walk the join tree.
      // 遍历连接树，提取 TableScan 节点和连接条件
      List<TableScan> relNodes = new ArrayList<>();
      List<int[][]> tempLinks = new ArrayList<>();
      populate(relNodes, tempLinks, parsed.root.rel);

      // Get aliases.
      // 获取表别名
      List<@Nullable String> aliases = new ArrayList<>();
      SqlNode from = requireNonNull(((SqlSelect) parsed.sqlNode).getFrom());
      populateAliases(from, aliases, null);

      // Build a graph.
      // 构建图结构
      final DirectedGraph<Vertex, Edge> graph =
          DefaultDirectedGraph.create(Edge.FACTORY);
      final List<Vertex> vertices = new ArrayList<>();
      // 为每个表创建顶点
      for (Pair<TableScan, @Nullable String> p : Pair.zip(relNodes, aliases)) {
        final LatticeTable table = space.register(p.left.getTable()); // 注册表
        final Vertex vertex = new Vertex(table, p.right); // 创建顶点
        graph.addVertex(vertex); // 添加顶点到图
        vertices.add(vertex);
      }
      // 为每个连接条件添加边
      for (int[][] tempLink : tempLinks) {
        final Vertex source = vertices.get(tempLink[0][0]); // 源顶点
        final Vertex target = vertices.get(tempLink[1][0]); // 目标顶点
        Edge edge = graph.getEdge(source, target);
        if (edge == null) {
          edge = castNonNull(graph.addEdge(source, target)); // 添加边
        }
        edge.pairs.add(IntPair.of(tempLink[0][1], tempLink[1][1])); // 添加列对
      }

      // Convert the graph into a tree of nodes, each connected to a parent and
      // with a join condition to that parent.
      // 将图转换为节点树，每个节点连接到父节点，并有连接条件
      MutableNode root = null;
      final IdentityHashMap<LatticeTable, MutableNode> map = new IdentityHashMap<>();
      // 按拓扑顺序遍历顶点
      for (Vertex vertex : TopologicalOrderIterator.of(graph)) {
        final List<Edge> edges = graph.getInwardEdges(vertex); // 获取入边
        MutableNode node;
        if (root == null) {
          // 第一个顶点是根节点
          if (!edges.isEmpty()) {
            throw new RuntimeException("root node must not have relationships: "
                + vertex);
          }
          root = node = new MutableNode(vertex.table);
          node.alias = vertex.alias;
        } else {
          // 其他顶点是子节点
          if (edges.size() != 1) {
            throw new RuntimeException(
                "child node must have precisely one parent: " + vertex);
          }
          final Edge edge = edges.get(0);
          final MutableNode parent = map.get(edge.getSource().table);
          // 创建 Step（连接步骤）
          final Step step =
              Step.create(edge.getSource().table,
                  edge.getTarget().table, edge.pairs, space);
          node = new MutableNode(vertex.table, parent, step);
          node.alias = vertex.alias;
        }
        map.put(vertex.table, node); // 保存节点映射
      }
      requireNonNull(root, "root"); // 确保根节点存在
      
      // Fix up the tree of mutable nodes.
      // 修复可变节点树（设置别名和列）
      final Fixer fixer = new Fixer();
      fixer.fixUp(root);
      baseColumns = fixer.columnList.build(); // 保存基础列
      columnsByAlias = fixer.columnAliasList.build(); // 保存按别名索引的列
      rootNode = new LatticeRootNode(space, root); // 创建根节点
    }

    /**
     * 构造函数（从 MutableNode 构建）
     * 
     * 【参数说明】
     * @param space Lattice 空间
     * @param schema 模式
     * @param mutableNode 可变节点
     * 
     * 【作用】
     * 用于从已存在的节点树构建 Builder
     */
    Builder(LatticeSpace space, CalciteSchema schema,
        MutableNode mutableNode) {
      this.rootSchema = schema;

      // Fix up the mutable node tree.
      // 修复可变节点树
      final Fixer fixer = new Fixer();
      fixer.fixUp(mutableNode);

      // 获取或创建 LatticeRootNode
      final LatticeRootNode node0 = new LatticeRootNode(space, mutableNode);
      final LatticeRootNode node1 = space.nodeMap.get(node0.digest);
      final LatticeRootNode node;
      if (node1 != null) {
        node = node1; // 使用已存在的节点
      } else {
        node = node0; // 使用新创建的节点
        space.nodeMap.put(node0.digest, node0); // 注册节点
      }

      this.rootNode = node;
      baseColumns = fixer.columnList.build();
      columnsByAlias = fixer.columnAliasList.build();
    }

    /**
     * 设置 "auto" 属性（默认 true）
     * 
     * @param auto 是否自动识别和推荐物化视图
     * @return this（支持链式调用）
     */
    public Builder auto(boolean auto) {
      this.auto = auto;
      return this;
    }

    /**
     * 设置 "algorithm" 属性（默认 false）
     * 
     * @param algorithm 是否使用算法自动计算最优 Tile
     * @return this（支持链式调用）
     */
    public Builder algorithm(boolean algorithm) {
      this.algorithm = algorithm;
      return this;
    }

    /**
     * 设置 "algorithmMaxMillis" 属性（默认 -1）
     * 
     * @param algorithmMaxMillis 算法最大执行时间（毫秒）
     * @return this（支持链式调用）
     */
    public Builder algorithmMaxMillis(long algorithmMaxMillis) {
      this.algorithmMaxMillis = algorithmMaxMillis;
      return this;
    }

    /**
     * 设置 "rowCountEstimate" 属性（默认 null）
     * 
     * @param rowCountEstimate 行数估计
     * @return this（支持链式调用）
     */
    public Builder rowCountEstimate(double rowCountEstimate) {
      this.rowCountEstimate = rowCountEstimate;
      return this;
    }

    /**
     * 设置 "statisticProvider" 属性
     * 
     * 【参数说明】
     * @param statisticProvider 统计信息提供者类名（可能为 null）
     * 
     * @return this（支持链式调用）
     * 
     * 【默认值】
     * 如果未设置，使用 Lattices.CACHED_SQL
     */
    public Builder statisticProvider(@Nullable String statisticProvider) {
      this.statisticProvider = statisticProvider;
      return this;
    }

    /**
     * 构建 Lattice 实例
     * 
     * @return Lattice 实例
     * 
     * 【构建流程】
     * 1. 创建统计信息提供者
     * 2. 构建列列表（基础列 + 派生列）
     * 3. 调用 Lattice 构造函数
     */
    public Lattice build() {
      // 创建统计信息提供者工厂
      LatticeStatisticProvider.Factory statisticProvider =
          this.statisticProvider != null
              ? AvaticaUtils.instantiatePlugin(
                  LatticeStatisticProvider.Factory.class,
                  this.statisticProvider)
              : Lattices.CACHED_SQL;
      checkArgument(rootSchema.isRoot(), "must be root schema");
      
      // 构建列列表（基础列 + 派生列）
      final ImmutableList.Builder<Column> columnBuilder =
          ImmutableList.<Column>builder()
          .addAll(baseColumns) // 添加基础列
          .addAll(derivedColumnsByName.values()); // 添加派生列
      
      // 创建 Lattice 实例
      return new Lattice(rootSchema, rootNode, auto,
          algorithm, algorithmMaxMillis, statisticProvider, rowCountEstimate,
          columnBuilder.build(), ImmutableSortedSet.copyOf(defaultMeasureSet),
          tileListBuilder.build(), ImmutableListMultimap.copyOf(columnUses));
    }

    /**
     * 解析度量的参数
     * 
     * 【参数说明】
     * @param args 度量参数（可能是 null、字符串或字符串列表）
     * 
     * @return 列列表
     * 
     * 【支持的格式】
     * - null：空列表
     * - String：单列
     * - List<String>：多列
     * 
     * 【异常】
     * 如果格式无效或列不存在，抛出 RuntimeException
     */
    public ImmutableList<Column> resolveArgs(@Nullable Object args) {
      if (args == null) {
        return ImmutableList.of(); // 空参数
      } else if (args instanceof String) {
        // 单个列别名
        return ImmutableList.of(resolveColumnByAlias((String) args));
      } else if (args instanceof List) {
        // 多个列别名
        final ImmutableList.Builder<Column> builder = ImmutableList.builder();
        for (Object o : (List) args) {
          if (o instanceof String) {
            builder.add(resolveColumnByAlias((String) o));
          } else {
            // 无效的参数类型
            throw new RuntimeException(
                "Measure arguments must be a string or a list of strings; argument: "
                    + o);
          }
        }
        return builder.build();
      } else {
        // 无效的参数类型
        throw new RuntimeException(
            "Measure arguments must be a string or a list of strings");
      }
    }

    /**
     * 按别名查找列
     * 
     * 【参数说明】
     * @param name 列别名
     * 
     * @return 列
     * 
     * 【异常】
     * - 如果列不存在，抛出 RuntimeException
     * - 如果别名不唯一，抛出 RuntimeException
     */
    private Column resolveColumnByAlias(String name) {
      final ImmutableList<Column> list = columnsByAlias.get(name);
      if (list.isEmpty()) {
        throw new RuntimeException("Unknown lattice column '" + name + "'");
      } else if (list.size() == 1) {
        return list.get(0); // 返回唯一的列
      } else {
        // 别名不唯一
        throw new RuntimeException("Lattice column alias '" + name
            + "' is not unique");
      }
    }

    /**
     * 解析列引用
     * 
     * 【参数说明】
     * @param name 列引用（可能是字符串或列表）
     * 
     * @return 列
     * 
     * 【支持的格式】
     * - String：列别名
     * - [String]：列别名
     * - [String, String]：[表别名, 列名]
     */
    public Column resolveColumn(Object name) {
      if (name instanceof String) {
        // 字符串：列别名
        return resolveColumnByAlias((String) name);
      }
      if (name instanceof List) {
        List list = (List) name;
        switch (list.size()) {
        case 1:
          // [alias]
          final Object alias = list.get(0);
          if (alias instanceof String) {
            return resolveColumnByAlias((String) alias);
          }
          break;
        case 2:
          // [table, column]
          final Object table = list.get(0);
          final Object column = list.get(1);
          if (table instanceof String && column instanceof String) {
            return resolveQualifiedColumn((String) table, (String) column);
          }
          break;
        default:
          break;
        }
      }
      // 无效的格式
      throw new RuntimeException(
          "Lattice column reference must be a string or a list of 1 or 2 strings; column: "
              + name);
    }

    /**
     * 按表别名和列名查找列
     * 
     * 【参数说明】
     * @param table 表别名
     * @param column 列名
     * 
     * @return 列
     * 
     * @throws RuntimeException 如果列不存在
     */
    private Column resolveQualifiedColumn(String table, String column) {
      for (BaseColumn column1 : baseColumns) {
        if (column1.table.equals(table)
            && column1.column.equals(column)) {
          return column1; // 找到匹配的列
        }
      }
      // 列不存在
      throw new RuntimeException("Unknown lattice column [" + table + ", "
          + column + "]");
    }

    /**
     * 解析度量
     * 
     * 【参数说明】
     * @param aggName 聚合函数名（如 "count"、"sum"）
     * @param distinct 是否使用 DISTINCT
     * @param args 度量参数
     * 
     * @return Measure 实例
     */
    public Measure resolveMeasure(String aggName, boolean distinct,
        @Nullable Object args) {
      final SqlAggFunction agg = resolveAgg(aggName); // 解析聚合函数
      final ImmutableList<Column> list = resolveArgs(args); // 解析参数
      return new Measure(agg, distinct, aggName, list); // 创建度量
    }

    /**
     * 解析聚合函数
     * 
     * 【参数说明】
     * @param aggName 聚合函数名
     * 
     * @return SqlAggFunction 实例
     * 
     * 【支持的函数】
     * - count：COUNT
     * - sum：SUM
     * 
     * @throws RuntimeException 如果函数不支持
     */
    private static SqlAggFunction resolveAgg(String aggName) {
      if (aggName.equalsIgnoreCase("count")) {
        return SqlStdOperatorTable.COUNT;
      } else if (aggName.equalsIgnoreCase("sum")) {
        return SqlStdOperatorTable.SUM;
      } else {
        // 不支持的聚合函数
        throw new RuntimeException("Unknown lattice aggregate function "
            + aggName);
      }
    }

    /**
     * 添加度量（如果不存在）
     * 
     * 【参数说明】
     * @param measure 度量
     * 
     * @return 如果添加成功返回 true，如果已存在返回 false
     */
    public boolean addMeasure(Measure measure) {
      return defaultMeasureSet.add(measure);
    }

    /**
     * 添加 Tile
     * 
     * @param tile Tile
     */
    public void addTile(Tile tile) {
      tileListBuilder.add(tile);
    }

    /**
     * 获取指定表和列的列对象
     * 
     * 【参数说明】
     * @param table 表索引（从 0 开始）
     * @param column 列索引（从 0 开始）
     * 
     * @return 列对象
     */
    public Column column(int table, int column) {
      int i = 0; // 当前列序号
      // 遍历所有节点，找到指定的表
      for (LatticeNode descendant : rootNode.descendants) {
        if (table-- == 0) {
          break;
        }
        i += descendant.table.t.getRowType().getFieldCount(); // 跳过当前表的列
      }
      return baseColumns.get(i + column); // 返回指定列
    }

    /**
     * 根据路径和偏移量获取列
     * 
     * 【参数说明】
     * @param path 路径
     * @param offset 列偏移量
     * 
     * @return 列对象
     */
    Column pathOffsetToColumn(Path path, int offset) {
      final int i = rootNode.paths.indexOf(path); // 找到路径索引
      final LatticeNode node = rootNode.descendants.get(i); // 获取节点
      final int c = node.startCol + offset; // 计算列序号
      if (c >= node.endCol) {
        throw new AssertionError(); // 偏移量超出范围
      }
      return baseColumns.get(c); // 返回列
    }

    /**
     * 添加基于 SQL 表达式的派生列
     * 
     * 【参数说明】
     * @param e 表达式
     * @param alias 列别名
     * @param tableAliases 表达式使用的表别名列表
     * 
     * @return 列对象（可能是新创建的，也可能是已存在的）
     * 
     * 【实现细节】
     * 使用表达式字符串作为键，确保相同表达式只创建一次
     */
    public Column expression(RexNode e, String alias,
        List<String> tableAliases) {
      return derivedColumnsByName.computeIfAbsent(e.toString(), k -> {
        final int derivedOrdinal = derivedColumnsByName.size(); // 派生列序号
        final int ordinal = baseColumns.size() + derivedOrdinal; // 全局序号
        return new DerivedColumn(ordinal,
            Util.first(alias, "e$" + derivedOrdinal), e, tableAliases); // 创建派生列
      });
    }

    /**
     * 记录列的使用情况
     * 
     * 【参数说明】
     * @param column 列
     * @param measure 是否作为度量参数使用
     * 
     * 【示例】
     * - sum(x + y)：measure = true
     * - group by x + y：measure = false
     */
    public void use(Column column, boolean measure) {
      columnUses.put(column.ordinal, measure);
    }

    /**
     * Fixer 类：用于修复可变节点树
     * 
     * 【类的作用】
     * Fixer 遍历节点树，为每个节点设置：
     * - 唯一的表别名
     * - 列的序号范围
     * - 每个列的别名
     */
    private static class Fixer {
      /** 已使用的表别名集合 */
      final Set<String> aliases = new HashSet<>();
      /** 已使用的列别名集合 */
      final Set<String> columnAliases = new HashSet<>();
      /** 已访问的节点集合（用于检测循环） */
      final Set<MutableNode> seen = new HashSet<>();
      /** 基础列列表构建器 */
      final ImmutableList.Builder<BaseColumn> columnList =
          ImmutableList.builder();
      /** 按别名索引的列多重映射构建器 */
      final ImmutableListMultimap.Builder<String, Column> columnAliasList =
          ImmutableListMultimap.builder();
      /** 当前列序号 */
      int c;

      /**
       * 修复节点树
       * 
       * 【参数说明】
       * @param node 当前节点
       * 
       * 【处理流程】
       * 1. 检测循环
       * 2. 设置表别名（确保唯一）
       * 3. 设置列序号范围
       * 4. 为每列创建 BaseColumn 并设置别名
       * 5. 递归处理子节点
       */
      void fixUp(MutableNode node) {
        // 检测循环
        if (!seen.add(node)) {
          throw new IllegalArgumentException("cyclic query graph");
        }
        // 设置表别名
        if (node.alias == null) {
          node.alias = Util.last(node.table.t.getQualifiedName());
        }
        // 确保别名唯一
        node.alias =
            SqlValidatorUtil.uniquify(node.alias, aliases,
                SqlValidatorUtil.ATTEMPT_SUGGESTER);
        
        // 设置列序号范围
        node.startCol = c;
        // 为每个列创建 BaseColumn
        for (String name : node.table.t.getRowType().getFieldNames()) {
          // 确保列别名唯一
          final String alias =
              SqlValidatorUtil.uniquify(name, columnAliases,
                  SqlValidatorUtil.ATTEMPT_SUGGESTER);
          // 创建 BaseColumn
          final BaseColumn column =
              new BaseColumn(c++, castNonNull(node.alias), name, alias);
          columnList.add(column); // 添加到列列表
          columnAliasList.put(name, column); // 添加到别名映射（使用原始名称）
        }
        node.endCol = c;

        // 确保子节点有序
        assert MutableNode.ORDERING.isStrictlyOrdered(node.children)
            : node.children;
        // 递归处理子节点
        for (MutableNode child : node.children) {
          fixUp(child);
        }
      }
    }
  }

  /**
   * Tile（瓦片）类：表示 Lattice 中的物化聚合
   * 
   * 【类的作用】
   * Tile 表示一个物化视图切片，包含：
   * - 一组维度（用于分组的列）
   * - 一组度量（聚合函数）
   * 
   * 【示例】
   * - Tile {dimensions: [country, year], measures: [SUM(sales)]}
   *   对应物化视图：SELECT country, year, SUM(sales) FROM ... GROUP BY country, year
   */
  public static class Tile {
    /** 度量列表（有序） */
    public final ImmutableList<Measure> measures;
    /** 维度列表（有序） */
    public final ImmutableList<Column> dimensions;
    /** 维度的序号 BitSet */
    public final ImmutableBitSet bitSet;

    /**
     * 构造函数
     * 
     * 【参数说明】
     * @param measures 度量列表
     * @param dimensions 维度列表
     * 
     * 【约束条件】
     * - 度量和维度必须有序
     */
    public Tile(ImmutableList<Measure> measures,
        ImmutableList<Column> dimensions) {
      this.measures = requireNonNull(measures, "measures"); // 确保度量非空
      this.dimensions = requireNonNull(dimensions, "dimensions"); // 确保维度非空
      assert Ordering.natural().isStrictlyOrdered(dimensions); // 确保维度有序
      assert Ordering.natural().isStrictlyOrdered(measures); // 确保度量有序
      bitSet = Column.toBitSet(dimensions); // 计算维度的 BitSet
    }

    /**
     * 创建 Tile 构建器
     * 
     * @return TileBuilder 实例
     */
    public static TileBuilder builder() {
      return new TileBuilder();
    }

    /**
     * 获取维度的 BitSet
     * 
     * @return 维度的序号 BitSet
     */
    public ImmutableBitSet bitSet() {
      return bitSet;
    }
  }

  /**
   * TileBuilder 类：用于构建 Tile
   * 
   * 【类的作用】
   * TileBuilder 提供逐步构建 Tile 的能力
   */
  public static class TileBuilder {
    /** 度量列表 */
    private final List<Measure> measureBuilder = new ArrayList<>();
    /** 维度列表 */
    private final List<Column> dimensionListBuilder = new ArrayList<>();

    /**
     * 构建 Tile
     * 
     * @return Tile 实例
     * 
     * 【构建流程】
     * 1. 对度量排序
     * 2. 对维度排序
     * 3. 创建 Tile 实例
     */
    public Tile build() {
      return new Tile(
          Ordering.natural().immutableSortedCopy(measureBuilder), // 排序度量
          Ordering.natural().immutableSortedCopy(dimensionListBuilder)); // 排序维度
    }

    /**
     * 添加度量
     * 
     * @param measure 度量
     */
    public void addMeasure(Measure measure) {
      measureBuilder.add(measure);
    }

    /**
     * 添加维度
     * 
     * @param column 维度列
     */
    public void addDimension(Column column) {
      dimensionListBuilder.add(column);
    }
  }
}