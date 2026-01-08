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
package org.apache.calcite.plan; // 声明包名，表示这个类属于org.apache.calcite.plan包，用于关系代数优化相关功能

import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类，用于获取系统配置参数
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，代表关系代数表达式的基本构建块
import org.apache.calcite.rel.RelShuttleImpl; // 导入关系访问器实现类，用于遍历和转换关系表达式树
import org.apache.calcite.rel.core.Filter; // 导入过滤器操作类，用于实现WHERE子句
import org.apache.calcite.rel.core.Project; // 导入投影操作类，用于实现SELECT子句
import org.apache.calcite.rel.core.TableScan; // 导入表扫描操作类，用于访问数据表
import org.apache.calcite.rel.logical.LogicalJoin; // 导入逻辑连接操作类，用于实现JOIN操作
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider; // 导入默认的关系元数据提供者，用于获取关系的统计信息
import org.apache.calcite.rel.rules.CoreRules; // 导入核心优化规则集合，包含各种常用的优化规则
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口，代表SQL表达式
import org.apache.calcite.rex.RexUtil; // 导入行表达式工具类，提供表达式操作的辅助方法
import org.apache.calcite.schema.Table; // 导入表接口，代表数据表
import org.apache.calcite.schema.impl.StarTable; // 导入星型表实现类，用于星型模式的数据仓库优化
import org.apache.calcite.sql.SqlExplainFormat; // 导入SQL解释格式枚举，用于控制执行计划的输出格式
import org.apache.calcite.sql.SqlExplainLevel; // 导入SQL解释级别枚举，用于控制执行计划的详细程度
import org.apache.calcite.tools.Program; // 导入程序接口，代表一个优化程序，可以应用一系列优化规则
import org.apache.calcite.tools.Programs; // 导入程序工具类，提供创建常用优化程序的工厂方法
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种实用方法
import org.apache.calcite.util.mapping.Mappings; // 导入映射工具类，用于字段映射和转换

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数或返回值

import java.util.ArrayList; // 导入Java集合框架的动态数组类
import java.util.List; // 导入Java集合框架的列表接口
import java.util.Objects; // 导入Java工具类，提供对象操作方法

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入静态方法，用于强制转换非空类型

import static java.util.Objects.requireNonNull; // 导入静态方法，用于检查参数是否为null

/**
 * Records that a particular query is materialized by a particular table.
 * 记录特定查询被特定表物化的信息。物化视图是一种预先计算并存储的查询结果，
 * 可以加速频繁执行的相同查询。这个类封装了物化视图的核心信息，包括：
 * 1. 物化表的关系表达式（tableRel）
 * 2. 原始查询的关系表达式（queryRel）
 * 3. 可选的星型表引用（starRelOptTable和starTable）
 * 4. 物化表的限定名称（qualifiedTableName）
 * 
 * 在查询优化过程中，优化器会检查当前查询是否与某个物化视图匹配，
 * 如果匹配，则可以直接使用物化视图的结果，避免重复计算。
 * 
 * 这个类还提供了将普通查询转换为使用星型表（StarTable）的方法，
 * 星型表是数据仓库中常用的优化技术，通过将多个维度表和事实表合并，
 * 减少连接操作，提高查询性能。
 */
public class RelOptMaterialization {
  public final RelNode tableRel; // 物化表的关系表达式，表示物化视图的数据结构和访问方式，是只读的final字段
  public final @Nullable RelOptTable starRelOptTable; // 可选的星型表优化表对象，可能为null，用于星型模式优化
  public final @Nullable StarTable starTable; // 可选的星型表对象，可能为null，实际的数据仓库星型表实现
  public final List<String> qualifiedTableName; // 物化表的限定名称列表，例如["schema", "table"]
  public final RelNode queryRel; // 原始查询的关系表达式，表示物化视图所对应的原始SQL查询

  /**
   * Creates a RelOptMaterialization.
   * 创建一个RelOptMaterialization实例。构造函数会验证并初始化物化视图的所有核心信息。
   * 
   * @param tableRel 物化表的关系表达式，不能为null，代表物化视图的物理存储结构
   * @param queryRel 原始查询的关系表达式，不能为null，代表物化视图所对应的查询逻辑
   * @param starRelOptTable 可选的星型表优化表对象，可能为null，用于星型模式优化
   * @param qualifiedTableName 物化表的限定名称列表，用于标识物化视图的位置
   */
  public RelOptMaterialization(RelNode tableRel, RelNode queryRel,
      @Nullable RelOptTable starRelOptTable, List<String> qualifiedTableName) {
    this.queryRel = requireNonNull(queryRel, "queryRel"); // 验证queryRel不为null，否则抛出NullPointerException
    this.tableRel = // 初始化tableRel字段，创建一个类型转换关系表达式
        RelOptUtil.createCastRel(requireNonNull(tableRel, "tableRel"), // 验证tableRel不为null，否则抛出NullPointerException
            queryRel.getRowType(), false); // 将tableRel转换为queryRel的行类型，确保类型匹配，false表示不进行强类型转换
    this.starRelOptTable = starRelOptTable; // 直接赋值，可能为null
    if (starRelOptTable == null) { // 如果starRelOptTable为null
      this.starTable = null; // 则starTable也为null
    } else { // 如果starRelOptTable不为null
      this.starTable = starRelOptTable.unwrapOrThrow(StarTable.class); // 尝试从starRelOptTable中解包出StarTable对象，如果失败则抛出异常
    }
    this.qualifiedTableName = qualifiedTableName; // 直接赋值，存储物化表的限定名称
  }

  /**
   * Converts a relational expression to one that uses a
   * {@link org.apache.calcite.schema.impl.StarTable}.
   * 将关系表达式转换为使用星型表（StarTable）的形式。
   * 
   * <p>The relational expression is already in leaf-join-form, per
   * {@link #toLeafJoinForm(org.apache.calcite.rel.RelNode)}.
   * 关系表达式已经是leaf-join-form（叶子连接形式），即连接操作尽可能靠近叶子节点。
   * 这种形式适合进行星型表优化，因为可以更容易地识别和替换连接模式。
   * 
   * <p>星型表是数据仓库中的一种优化技术，它将维度表和事实表合并成一个宽表，
   * 避免了多次连接操作，提高了查询性能。这个方法尝试将普通的关系表达式树
   * 转换为使用星型表的形式。
   * 
   * <p>转换过程：
   * 1. 遍历关系表达式树，查找TableScan和LogicalJoin节点
   * 2. 对于TableScan节点，如果扫描的是星型表的第一个表，则替换为扫描整个星型表
   * 3. 对于LogicalJoin节点，尝试将连接的两个表合并为一个星型表访问
   * 4. 如果转换成功，应用一系列优化规则来简化结果
   * 
   * @param rel 要转换的关系表达式，必须是leaf-join-form
   * @param starRelOptTable 星型表的优化表对象，包含星型表的元数据信息
   * @return 重写后的关系表达式，如果无法转换为使用星型表则返回null
   */
  public static @Nullable RelNode tryUseStar(RelNode rel,
      final RelOptTable starRelOptTable) {
    final StarTable starTable = starRelOptTable.unwrapOrThrow(StarTable.class); // 从优化表对象中解包出StarTable，如果失败则抛出异常
    RelNode rel2 = // 初始化转换后的关系表达式
        rel.accept(new RelShuttleImpl() { // 使用关系访问器遍历关系表达式树
          @Override public RelNode visit(TableScan scan) { // 重写visit方法处理TableScan节点
            RelOptTable relOptTable = scan.getTable(); // 获取表扫描操作的表对象
            final Table table = relOptTable.unwrap(Table.class); // 从优化表对象中解包出Table对象
            if (Objects.equals(table, starTable.tables.get(0))) { // 如果扫描的表是星型表的第一个表
              Mappings.TargetMapping mapping = // 创建目标映射，用于字段映射
                  Mappings.createShiftMapping( // 创建偏移映射
                      starRelOptTable.getRowType().getFieldCount(), // 目标的总字段数
                      0, 0, relOptTable.getRowType().getFieldCount()); // 源从0开始，目标从0开始，映射所有字段

              final RelOptCluster cluster = scan.getCluster(); // 获取关系表达式所在的集群
              final RelNode scan2 = // 创建星型表的扫描关系表达式
                  starRelOptTable.toRel(ViewExpanders.simpleContext(cluster)); // 使用简单的视图扩展器上下文转换
              return RelOptUtil.createProject(scan2, // 创建投影操作，选择需要的字段
                  Mappings.asListNonNull(mapping.inverse())); // 使用映射的逆映射来选择字段
            }
            return scan; // 如果不是星型表的第一个表，返回原始扫描节点
          }

          @Override public RelNode visit(LogicalJoin join) { // 重写visit方法处理LogicalJoin节点
            for (;;) { // 无限循环，直到无法继续转换
              RelNode rel = super.visit(join); // 递归处理连接的左右子节点
              if (rel == join || !(rel instanceof LogicalJoin)) { // 如果没有变化或不再是连接节点
                return rel; // 返回结果
              }
              join = (LogicalJoin) rel; // 更新join引用为处理后的连接节点
              final ProjectFilterTable left = // 尝试将左子节点转换为ProjectFilterTable
                  ProjectFilterTable.of(join.getLeft()); // 解析左子节点，提取投影、过滤和表扫描信息
              if (left != null) { // 如果左子节点成功转换
                final ProjectFilterTable right = // 尝试将右子节点转换为ProjectFilterTable
                    ProjectFilterTable.of(join.getRight()); // 解析右子节点，提取投影、过滤和表扫描信息
                if (right != null) { // 如果右子节点也成功转换
                  try { // 尝试匹配和转换
                    match(left, right, join.getCluster()); // 尝试将两个表合并为星型表访问
                  } catch (Util.FoundOne e) { // 如果匹配成功，会抛出FoundOne异常（这是一种特殊的控制流）
                    return (RelNode) requireNonNull(e.getNode(), // 返回找到的转换后的关系表达式
                        "FoundOne.getNode"); // 确保节点不为null
                  }
                }
              }
            }
          }

          /** Throws a {@link org.apache.calcite.util.Util.FoundOne} containing
           * a {@link org.apache.calcite.rel.logical.LogicalTableScan} on
           * success.  (Yes, an exception for normal operation.)
           * 在成功时抛出包含LogicalTableScan的Util.FoundOne异常。
           * （是的，这是一种使用异常进行正常流程控制的技巧）
           * 
           * <p>这个方法尝试将两个表的连接操作合并为一个星型表访问。
           * 它检查两个表是否都属于同一个星型表，如果是，则可以替换为单个星型表扫描。
           * 
           * <p>匹配逻辑：
           * 1. 检查左表是否是StarTable，右表是否是其包含的表
           * 2. 检查右表是否是StarTable，左表是否是其包含的表
           * 3. 如果匹配成功，创建新的投影和过滤操作，使用星型表作为数据源
           * 4. 抛出FoundOne异常来返回转换后的关系表达式
           * 
           * @param left 左边的ProjectFilterTable，包含表扫描、投影和过滤信息
           * @param right 右边的ProjectFilterTable，包含表扫描、投影和过滤信息
           * @param cluster 关系表达式集群，用于创建新的关系节点
           * @throws Util.FoundOne 如果成功匹配并转换，抛出此异常包含转换后的关系表达式
           */
          private void match(ProjectFilterTable left, ProjectFilterTable right,
              RelOptCluster cluster) {
            final Mappings.TargetMapping leftMapping = left.mapping(); // 获取左表的字段映射
            final Mappings.TargetMapping rightMapping = right.mapping(); // 获取右表的字段映射
            final RelOptTable leftRelOptTable = left.getTable(); // 获取左表的优化表对象
            final Table leftTable = leftRelOptTable.unwrap(Table.class); // 解包出左表的Table对象
            final int leftCount = leftRelOptTable.getRowType().getFieldCount(); // 获取左表的字段数量
            final RelOptTable rightRelOptTable = right.getTable(); // 获取右表的优化表对象
            final Table rightTable = rightRelOptTable.unwrap(Table.class); // 解包出右表的Table对象
            if (leftTable instanceof StarTable // 如果左表是星型表
                && rightTable != null // 且右表不为null
                && ((StarTable) leftTable).tables.contains(rightTable)) { // 且右表包含在左星型表中
              final int offset = // 计算右表在星型表中的列偏移量
                  ((StarTable) leftTable).columnOffset(rightTable); // 获取右表在星型表中的起始位置
              Mappings.TargetMapping mapping = // 合并左右表的字段映射
                  Mappings.merge(leftMapping, // 合并左表映射
                      Mappings.offsetTarget( // 对右表映射的目标进行偏移
                          Mappings.offsetSource(rightMapping, offset), // 对右表映射的源进行偏移
                          leftMapping.getTargetCount())); // 偏移量为左表的目标字段数
              final RelNode project = // 创建投影操作，从星型表中选择需要的字段
                  RelOptUtil.createProject( // 创建投影关系表达式
                      leftRelOptTable.toRel(ViewExpanders.simpleContext(cluster)), // 使用星型表作为数据源
                      Mappings.asListNonNull(mapping.inverse())); // 使用映射的逆映射来选择字段
              final List<RexNode> conditions = new ArrayList<>(); // 创建条件列表，用于合并过滤条件
              if (left.condition != null) { // 如果左表有过滤条件
                conditions.add(left.condition); // 添加左表的过滤条件
              }
              if (right.condition != null) { // 如果右表有过滤条件
                conditions.add( // 添加右表的过滤条件，需要应用映射和偏移
                    RexUtil.apply(mapping, // 应用字段映射
                        RexUtil.shift(right.condition, offset))); // 将条件中的字段引用偏移offset
              }
              final RelNode filter = // 创建过滤操作，应用合并后的条件
                  RelOptUtil.createFilter(project, conditions); // 在投影结果上应用过滤条件
              throw new Util.FoundOne(filter); // 抛出FoundOne异常，返回转换后的关系表达式
            }
            if (rightTable instanceof StarTable // 如果右表是星型表
                && leftTable != null // 且左表不为null
                && ((StarTable) rightTable).tables.contains(leftTable)) { // 且左表包含在右星型表中
              final int offset = // 计算左表在星型表中的列偏移量
                  ((StarTable) rightTable).columnOffset(leftTable); // 获取左表在星型表中的起始位置
              Mappings.TargetMapping mapping = // 合并左右表的字段映射
                  Mappings.merge( // 合并映射
                      Mappings.offsetSource(leftMapping, offset), // 对左表映射的源进行偏移
                      Mappings.offsetTarget(rightMapping, leftCount)); // 对右表映射的目标进行偏移
              final RelNode project = // 创建投影操作，从星型表中选择需要的字段
                  RelOptUtil.createProject( // 创建投影关系表达式
                      rightRelOptTable.toRel(ViewExpanders.simpleContext(cluster)), // 使用星型表作为数据源
                      Mappings.asListNonNull(mapping.inverse())); // 使用映射的逆映射来选择字段
              final List<RexNode> conditions = new ArrayList<>(); // 创建条件列表，用于合并过滤条件
              if (left.condition != null) { // 如果左表有过滤条件
                conditions.add( // 添加左表的过滤条件，需要应用映射和偏移
                    RexUtil.apply(mapping, // 应用字段映射
                        RexUtil.shift(left.condition, offset))); // 将条件中的字段引用偏移offset
              }
              if (right.condition != null) { // 如果右表有过滤条件
                conditions.add(RexUtil.apply(mapping, right.condition)); // 添加右表的过滤条件，应用映射
              }
              final RelNode filter = // 创建过滤操作，应用合并后的条件
                  RelOptUtil.createFilter(project, conditions); // 在投影结果上应用过滤条件
              throw new Util.FoundOne(filter); // 抛出FoundOne异常，返回转换后的关系表达式
            }
          }
        });
    if (rel2 == rel) { // 如果转换后的关系表达式与原始表达式相同
      // No rewrite happened. // 没有发生重写
      return null; // 返回null表示无法转换为使用星型表
    }
    final Program program = // 创建优化程序，用于进一步优化转换后的关系表达式
        Programs.hep( // 使用HepPlanner（启发式优化器）
            ImmutableList.of( // 优化规则列表
                CoreRules.PROJECT_FILTER_TRANSPOSE, // 规则：将Project和Filter操作转换，优化执行顺序
                CoreRules.AGGREGATE_PROJECT_MERGE, // 规则：合并Aggregate和Project操作
                CoreRules.AGGREGATE_FILTER_TRANSPOSE), // 规则：将Aggregate和Filter操作转换
        false, // 不使用固定点，只应用一次规则
        DefaultRelMetadataProvider.INSTANCE); // 使用默认的元数据提供者
    return program.run(castNonNull(null), rel2, castNonNull(null), // 运行优化程序，优化转换后的关系表达式
        ImmutableList.of(), // 空的输入关系表达式列表
        ImmutableList.of()); // 空的输入材料化视图列表
  }

  /** A table scan and optional project mapping and filter condition.
 * 一个表扫描操作，包含可选的投影映射和过滤条件。
 * 
 * <p>这个内部类用于解析和封装关系表达式树中的"表扫描+投影+过滤"模式。
 * 在星型表优化过程中，需要识别这种模式以便进行合并和转换。
 * 
 * <p>典型模式：
 * - Filter(Project(TableScan)) - 有过滤和投影
 * - Filter(TableScan) - 只有过滤
 * - Project(TableScan) - 只有投影
 * - TableScan - 只有表扫描
 * 
 * <p>字段说明：
 * - condition: 可选的过滤条件，可能为null
 * - mapping: 可选的投影映射，可能为null
 * - scan: 必须存在的表扫描操作
 */
  private static class ProjectFilterTable {
    final @Nullable RexNode condition; // 可选的过滤条件，可能为null，表示WHERE子句中的布尔表达式
    final Mappings.@Nullable TargetMapping mapping; // 可选的投影映射，可能为null，表示SELECT子句中的字段映射关系
    final TableScan scan; // 表扫描操作，必须存在，代表访问数据表的操作

    /**
     * 私有构造函数，创建ProjectFilterTable实例。
     * 
     * @param condition 可选的过滤条件，可能为null
     * @param mapping 可选的投影映射，可能为null
     * @param scan 表扫描操作，不能为null
     */
    private ProjectFilterTable(@Nullable RexNode condition,
        Mappings.@Nullable TargetMapping mapping, TableScan scan) {
      this.condition = condition; // 保存过滤条件
      this.mapping = mapping; // 保存投影映射
      this.scan = requireNonNull(scan, "scan"); // 验证scan不为null，否则抛出NullPointerException
    }

    /**
     * 工厂方法，从关系表达式节点创建ProjectFilterTable。
     * 这个方法递归地解析关系表达式树，提取表扫描、投影和过滤信息。
     * 
     * <p>解析逻辑：
     * 1. 如果节点是Filter，提取过滤条件并递归处理输入
     * 2. 如果节点不是Filter，将过滤条件设为null并继续处理
     * 
     * @param node 要解析的关系表达式节点
     * @return ProjectFilterTable实例，如果无法解析则返回null
     */
    static @Nullable ProjectFilterTable of(RelNode node) {
      if (node instanceof Filter) { // 如果节点是过滤器操作
        final Filter filter = (Filter) node; // 强制转换为Filter对象
        return of2(filter.getCondition(), filter.getInput()); // 提取过滤条件并递归处理输入节点
      } else { // 如果节点不是过滤器
        return of2(null, node); // 将过滤条件设为null，继续处理节点
      }
    }

    /**
     * 第二步解析，处理投影操作。
     * 
     * <p>解析逻辑：
     * 1. 如果节点是Project，提取投影映射并递归处理输入
     * 2. 如果节点不是Project，将映射设为null并继续处理
     * 
     * @param condition 已经提取的过滤条件（可能为null）
     * @param node 要解析的关系表达式节点
     * @return ProjectFilterTable实例，如果无法解析则返回null
     */
    private static @Nullable ProjectFilterTable of2(@Nullable RexNode condition, RelNode node) {
      if (node instanceof Project) { // 如果节点是投影操作
        final Project project = (Project) node; // 强制转换为Project对象
        return of3(condition, project.getMapping(), project.getInput()); // 提取投影映射并递归处理输入节点
      } else { // 如果节点不是投影
        return of3(condition, null, node); // 将映射设为null，继续处理节点
      }
    }

    /**
     * 第三步解析，处理表扫描操作。
     * 
     * <p>解析逻辑：
     * 1. 如果节点是TableScan，创建ProjectFilterTable实例
     * 2. 如果节点不是TableScan，返回null表示无法解析
     * 
     * @param condition 已经提取的过滤条件（可能为null）
     * @param mapping 已经提取的投影映射（可能为null）
     * @param node 要解析的关系表达式节点
     * @return ProjectFilterTable实例，如果节点不是TableScan则返回null
     */
    private static @Nullable ProjectFilterTable of3(@Nullable RexNode condition,
        Mappings.@Nullable TargetMapping mapping, RelNode node) {
      if (node instanceof TableScan) { // 如果节点是表扫描操作
        return new ProjectFilterTable(condition, mapping, // 创建ProjectFilterTable实例
            (TableScan) node); // 强制转换为TableScan对象
      } else { // 如果节点不是表扫描
        return null; // 返回null表示无法解析为ProjectFilterTable
      }
    }

    /**
     * 获取投影映射。如果mapping为null，则创建一个恒等映射。
     * 
     * <p>恒等映射表示输出字段与输入字段一一对应，没有进行字段选择或重命名。
     * 
     * @return 投影映射，如果原始mapping为null则返回恒等映射
     */
    public Mappings.TargetMapping mapping() {
      return mapping != null // 如果mapping不为null
          ? mapping // 直接返回mapping
          : Mappings.createIdentity(scan.getRowType().getFieldCount()); // 否则创建恒等映射，字段数量与表扫描的行类型字段数相同
    }

    /**
     * 获取表扫描操作的优化表对象。
     * 
     * @return 表扫描操作的优化表对象，包含表的元数据信息
     */
    public RelOptTable getTable() {
      return scan.getTable(); // 返回表扫描操作的表对象
    }
  }

  /**
   * Converts a relational expression to a form where
   * {@link org.apache.calcite.rel.logical.LogicalJoin}s are
   * as close to leaves as possible.
   * 将关系表达式转换为leaf-join-form（叶子连接形式），即连接操作尽可能靠近叶子节点。
   * 
   * <p>leaf-join-form是一种优化的关系表达式形式，其中连接操作被推到表达式树的底部，
   * 接近数据源（表扫描）。这种形式对于星型表优化非常重要，因为：
   * 1. 连接操作靠近叶子节点使得更容易识别可以合并的连接模式
   * 2. 减少了连接操作之上的投影和过滤操作，简化了优化逻辑
   * 3. 提高了后续优化规则（如星型表替换）的成功率
   * 
   * <p>转换过程应用以下优化规则：
   * 1. JOIN_PROJECT_RIGHT_TRANSPOSE: 将连接右侧的投影操作移到连接上方
   * 2. JOIN_PROJECT_LEFT_TRANSPOSE: 将连接左侧的投影操作移到连接上方
   * 3. FILTER_INTO_JOIN: 将过滤操作推入连接中，尽可能早地过滤数据
   * 4. PROJECT_REMOVE: 移除不必要的投影操作
   * 5. PROJECT_MERGE: 合并相邻的投影操作
   * 
   * <p>示例转换：
   * 原始形式: Project(Filter(Join(Scan1, Scan2)))
   * 转换后: Join(Project(Scan1), Project(Scan2)) + 上层的过滤
   * 
   * @param rel 要转换的关系表达式
   * @return 转换后的关系表达式，连接操作尽可能靠近叶子节点
   */
  public static RelNode toLeafJoinForm(RelNode rel) {
    final Program program = // 创建优化程序，用于将关系表达式转换为leaf-join-form
        Programs.hep( // 使用HepPlanner（启发式优化器）
            ImmutableList.of( // 优化规则列表，按顺序应用
                CoreRules.JOIN_PROJECT_RIGHT_TRANSPOSE, // 规则：将连接右侧的投影操作移到连接上方，使连接更靠近叶子
                CoreRules.JOIN_PROJECT_LEFT_TRANSPOSE, // 规则：将连接左侧的投影操作移到连接上方，使连接更靠近叶子
                CoreRules.FILTER_INTO_JOIN, // 规则：将过滤条件推入连接操作中，减少处理的数据量
                CoreRules.PROJECT_REMOVE, // 规则：移除不必要的投影操作，简化表达式树
                CoreRules.PROJECT_MERGE), // 规则：合并相邻的投影操作，减少投影节点数量
        false, // 不使用固定点，只应用一次规则
        DefaultRelMetadataProvider.INSTANCE); // 使用默认的元数据提供者
    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了调试模式
      System.out.println( // 打印转换前的执行计划
          RelOptUtil.dumpPlan("before", rel, SqlExplainFormat.TEXT, // 使用文本格式输出
              SqlExplainLevel.DIGEST_ATTRIBUTES)); // 输出摘要级别的属性信息
    }
    final RelNode rel2 = // 运行优化程序，转换关系表达式
        program.run(castNonNull(null), rel, castNonNull(null), // 运行优化，参数为null表示使用默认值
            ImmutableList.of(), // 空的输入关系表达式列表
            ImmutableList.of()); // 空的输入材料化视图列表
    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了调试模式
      System.out.println( // 打印转换后的执行计划
          RelOptUtil.dumpPlan("after", rel2, SqlExplainFormat.TEXT, // 使用文本格式输出
              SqlExplainLevel.DIGEST_ATTRIBUTES)); // 输出摘要级别的属性信息
    }
    return rel2; // 返回转换后的关系表达式
  }
}
