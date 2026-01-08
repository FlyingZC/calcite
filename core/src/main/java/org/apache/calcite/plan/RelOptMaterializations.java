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
package org.apache.calcite.plan;  // 包声明：Calcite查询优化器核心包，包含关系表达式优化相关类

import org.apache.calcite.config.CalciteSystemProperty;  // 导入：Calcite系统属性配置类，用于控制调试等系统级行为
import org.apache.calcite.plan.hep.HepPlanner;  // 导入：HEP（启发式）规划器，用于基于规则的优化
import org.apache.calcite.plan.hep.HepProgram;  // 导入：HEP优化程序，定义规则应用的顺序和方式
import org.apache.calcite.plan.hep.HepProgramBuilder;  // 导入：HEP程序构建器，用于构建优化程序
import org.apache.calcite.rel.RelNode;  // 导入：关系表达式节点接口，代表关系代数操作（如扫描、过滤、连接等）
import org.apache.calcite.rel.core.RelFactories;  // 导入：关系表达式工厂类，用于创建各种关系节点
import org.apache.calcite.rel.rules.CoreRules;  // 导入：核心优化规则集合，包含常用的优化规则
import org.apache.calcite.sql2rel.RelFieldTrimmer;  // 导入：关系字段修剪器，用于移除未使用的字段
import org.apache.calcite.tools.RelBuilder;  // 导入：关系表达式构建器，用于程序化构建关系表达式树
import org.apache.calcite.util.Pair;  // 导入：键值对工具类，用于存储两个相关联的对象
import org.apache.calcite.util.Util;  // 导入：Calcite通用工具类，提供各种辅助方法
import org.apache.calcite.util.graph.DefaultDirectedGraph;  // 导入：默认有向图实现，用于构建依赖关系图
import org.apache.calcite.util.graph.DefaultEdge;  // 导入：默认边实现，表示图中的边
import org.apache.calcite.util.graph.DirectedGraph;  // 导入：有向图接口，表示节点间的有向关系
import org.apache.calcite.util.graph.Graphs;  // 导入：图工具类，提供图操作方法
import org.apache.calcite.util.graph.TopologicalOrderIterator;  // 导入：拓扑排序迭代器，用于按依赖顺序遍历图

import com.google.common.base.Suppliers;  // 导入：Guava延迟计算工具，用于按需计算值
import com.google.common.collect.ImmutableList;  // 导入：Guava不可变列表，线程安全的列表实现
import com.google.common.collect.Sets;  // 导入：Guava集合工具类，提供集合操作方法

import java.util.ArrayList;  // 导入：Java动态数组列表，可变长度的列表实现
import java.util.HashMap;  // 导入：Java哈希映射，基于键值对的映射实现
import java.util.List;  // 导入：Java列表接口，表示有序集合
import java.util.Map;  // 导入：Java映射接口，表示键值对集合
import java.util.Set;  // 导入：Java集合接口，表示无序不重复集合
import java.util.function.Supplier;  // 导入：Java函数式接口，用于延迟计算

/**
 * Utility methods for using
 * materialized views and lattices for queries.
 * 工具类：提供使用物化视图和格（lattice）来优化查询的静态方法
 * 物化视图是预先计算并存储的结果集，可以加速查询
 * 格是多维数据结构，用于加速星型模式查询
 */
public abstract class RelOptMaterializations {  // 抽象类：只包含静态方法，不需要实例化

  /**
   * Returns a list of RelNode transformed from all possible combination of
   * materialized view uses. Big queries will likely have more than one
   * transformed RelNode, e.g., (t1 group by c1) join (t2 group by c2).
   * 返回通过所有可能的物化视图使用组合转换后的RelNode列表
   * 复杂查询可能会产生多个转换后的RelNode，例如：(t1 group by c1) join (t2 group by c2)
   * 这个方法会尝试用不同的物化视图组合来重写查询，生成多个备选计划
   *
   * @param rel               the original RelNode 原始的关系表达式节点，代表待优化的查询
   * @param materializations  the materialized view list 物化视图列表，每个物化视图包含查询定义和结果表
   * @return the list of transformed RelNode together with their corresponding
   *         materialized views used in the transformation.
   *         返回转换后的RelNode列表，每个RelNode配对使用的物化视图列表
   *         返回值是Pair对象，第一个元素是转换后的查询计划，第二个元素是使用的物化视图集合
   */
  public static List<Pair<RelNode, List<RelOptMaterialization>>> useMaterializedViews(  // 静态方法：使用物化视图优化查询
      final RelNode rel, List<RelOptMaterialization> materializations) {  // 参数：原始查询和物化视图列表
    return useMaterializedViews(rel, materializations, SubstitutionVisitor.DEFAULT_RULES);  // 调用重载方法，使用默认的物化视图识别规则
  }

  /**
   * Returns a list of RelNode transformed from all possible combination of
   * materialized view uses. Big queries will likely have more than one
   * transformed RelNode, e.g., (t1 group by c1) join (t2 group by c2).
   * In addition, you can add custom materialized view recognition rules.
   * 返回通过所有可能的物化视图使用组合转换后的RelNode列表
   * 复杂查询可能会产生多个转换后的RelNode，例如：(t1 group by c1) join (t2 group by c2)
   * 此外，可以添加自定义的物化视图识别规则来扩展匹配能力
   *
   * @param rel               the original RelNode 原始的关系表达式节点，代表待优化的查询
   * @param materializations  the materialized view list 物化视图列表，每个物化视图包含查询定义和结果表
   * @param materializationRules the materialized view recognition rules 物化视图识别规则列表，用于匹配查询和物化视图
   * @return the list of transformed RelNode together with their corresponding
   *         materialized views used in the transformation.
   *         返回转换后的RelNode列表，每个RelNode配对使用的物化视图列表
   *         返回值是Pair对象，第一个元素是转换后的查询计划，第二个元素是使用的物化视图集合
   */
  public static List<Pair<RelNode, List<RelOptMaterialization>>> useMaterializedViews(  // 静态方法：使用物化视图优化查询，支持自定义规则
      final RelNode rel, List<RelOptMaterialization> materializations,  // 参数：原始查询和物化视图列表
      List<SubstitutionVisitor.UnifyRule> materializationRules) {  // 参数：物化视图识别规则列表
    final List<RelOptMaterialization> applicableMaterializations =  // 声明：适用的物化视图列表
        getApplicableMaterializations(rel, materializations);  // 调用：筛选出适用于当前查询的物化视图（基于表依赖关系）
    final List<Pair<RelNode, List<RelOptMaterialization>>> applied =  // 声明：已应用的物化视图列表，存储转换后的查询计划
        new ArrayList<>();  // 初始化：创建动态数组列表
    applied.add(Pair.of(rel, ImmutableList.of()));  // 添加：原始查询作为起点，使用空物化视图列表
    for (RelOptMaterialization m : applicableMaterializations) {  // 循环：遍历每个适用的物化视图
      int count = applied.size();  // 记录：当前已应用计划的数量（用于避免重复处理新添加的计划）
      for (int i = 0; i < count; i++) {  // 循环：遍历每个已生成的计划
        Pair<RelNode, List<RelOptMaterialization>> current = applied.get(i);  // 获取：当前待处理的计划（查询和已使用的物化视图）
        List<RelNode> sub = substitute(current.left, m, materializationRules);  // 调用：尝试用物化视图m替换当前查询中的部分
        if (!sub.isEmpty()) {  // 判断：如果替换成功（生成新的查询计划）
          ImmutableList.Builder<RelOptMaterialization> builder =  // 创建：不可变列表构建器，用于构建物化视图使用列表
              ImmutableList.builder();  // 初始化：构建器实例
          builder.addAll(current.right);  // 添加：当前计划已使用的物化视图
          builder.add(m);  // 添加：新使用的物化视图m
          List<RelOptMaterialization> uses = builder.build();  // 构建：物化视图使用列表（不可变）
          for (RelNode rel2 : sub) {  // 循环：遍历每个替换后的查询计划
            applied.add(Pair.of(rel2, uses));  // 添加：新的查询计划及其使用的物化视图列表
          }
        }
      }
    }

    return applied.subList(1, applied.size());  // 返回：跳过第一个元素（原始查询），返回所有使用物化视图的转换计划
  }

  /**
   * Returns a list of RelNode transformed from all possible lattice uses.
   * 返回通过所有可能的格（lattice）使用转换后的RelNode列表
   * 格是星型模式的多维数据结构，可以加速聚合查询
   *
   * @param rel       the original RelNode 原始的关系表达式节点，代表待优化的查询
   * @param lattices  the lattice list 格列表，每个格代表一个星型模式的预聚合结构
   * @return the list of transformed RelNode together with their corresponding
   *         lattice used in the transformation.
   *         返回转换后的RelNode列表，每个RelNode配对使用的格
   *         返回值是Pair对象，第一个元素是转换后的查询计划，第二个元素是使用的格
   */
  public static List<Pair<RelNode, RelOptLattice>> useLattices(  // 静态方法：使用格优化查询
      final RelNode rel, List<RelOptLattice> lattices) {  // 参数：原始查询和格列表
    final Set<RelOptTable> queryTables = RelOptUtil.findTables(rel);  // 获取：查询中使用的所有表（RelOptTable对象集合）
    // Use a lattice if the query uses at least the central (fact) table of the
    // lattice.
    // 注释：如果查询使用了格的中心表（事实表），则使用该格来优化查询
    final List<Pair<RelNode, RelOptLattice>> latticeUses = new ArrayList<>();  // 声明：格使用列表，存储转换后的查询计划
    final Set<List<String>> queryTableNames =  // 声明：查询表名集合，存储表的完全限定名（如["catalog", "schema", "table"]）
        Sets.newHashSet(  // 创建：Guava不可变集合
            Util.transform(queryTables, RelOptTable::getQualifiedName));  // 转换：将RelOptTable集合转换为完全限定名集合
    // Remember leaf-join form of root so we convert at most once.
    // 注释：记住根节点的叶子连接形式，确保最多转换一次（使用延迟计算）
    final Supplier<RelNode> leafJoinRoot =  // 声明：延迟计算的叶子连接形式供应商
        Suppliers.memoize(() -> RelOptMaterialization.toLeafJoinForm(rel));  // 延迟计算：将查询转换为叶子连接形式（只计算一次）
    for (RelOptLattice lattice : lattices) {  // 循环：遍历每个格
      if (queryTableNames.contains(lattice.rootTable().getQualifiedName())) {  // 判断：如果查询使用了格的根表（事实表）
        RelNode rel2 = lattice.rewrite(leafJoinRoot.get());  // 调用：使用格重写查询（将查询转换为使用格的聚合表）
        if (rel2 != null) {  // 判断：如果重写成功（返回非null）
          if (CalciteSystemProperty.DEBUG.value()) {  // 判断：如果开启调试模式
            System.out.println("use lattice:\n"  // 输出：打印使用格的调试信息
                + RelOptUtil.toString(rel2));  // 输出：打印转换后的查询计划
          }
          latticeUses.add(Pair.of(rel2, lattice));  // 添加：将转换后的查询和使用的格加入结果列表
        }
      }
    }

    return latticeUses;  // 返回：所有使用格转换后的查询计划列表
  }

  /**
   * Returns a list of materializations that can potentially be used by the query.
   * 返回可能被查询使用的物化视图列表
   * 这个方法通过分析物化视图的表依赖关系，筛选出与查询相关的物化视图
   * 使用有向图来表示物化视图之间的依赖关系，确保按拓扑顺序处理
   *
   * @param rel 待优化的查询关系表达式
   * @param materializations 所有可用的物化视图列表
   * @return 适用于当前查询的物化视图列表
   */
  public static List<RelOptMaterialization> getApplicableMaterializations(  // 静态方法：获取适用于查询的物化视图
      RelNode rel, List<RelOptMaterialization> materializations) {  // 参数：查询和物化视图列表
    DirectedGraph<List<String>, DefaultEdge> usesGraph =  // 声明：有向图，表示物化视图的表依赖关系（顶点是表名，边表示依赖）
        DefaultDirectedGraph.create();  // 创建：默认有向图实例
    final Map<List<String>, RelOptMaterialization> qnameMap = new HashMap<>();  // 声明：表名到物化视图的映射
    for (RelOptMaterialization materialization : materializations) {  // 循环：遍历每个物化视图
      // If materialization is a tile in a lattice, we will deal with it shortly.
      // 注释：如果物化视图是格中的瓦片（tile），我们稍后会处理（跳过格瓦片）
      if (materialization.qualifiedTableName != null  // 判断：如果物化视图有完全限定表名
          && materialization.starTable == null) {  // 判断：并且不是格瓦片（starTable为null）
        final List<String> qname = materialization.qualifiedTableName;  // 获取：物化视图的完全限定表名
        qnameMap.put(qname, materialization);  // 存储：表名到物化视图的映射
        for (RelOptTable usedTable  // 循环：遍历物化视图查询中使用的所有表
            : RelOptUtil.findTables(materialization.queryRel)) {  // 获取：物化视图查询定义中使用的所有表
          usesGraph.addVertex(qname);  // 添加：物化视图表名作为图的顶点
          usesGraph.addVertex(usedTable.getQualifiedName());  // 添加：物化视图使用的表名作为图的顶点
          usesGraph.addEdge(usedTable.getQualifiedName(), qname);  // 添加：从基础表到物化视图的边（表示依赖关系）
        }
      }
    }

    // Use a materialization if uses at least one of the tables are used by
    // the query. (Simple rule that includes some materializations we won't
    // actually use.)
    // 注释：如果物化视图使用了查询中使用的至少一个表，则使用该物化视图
    // （简单规则，可能会包含一些实际不会使用的物化视图）
    // For example, given materializations:
    //   T = Emps Join Depts
    //   T2 = T Group by C1
    // the graph will contain
    //   (T, Emps), (T, Depts), (T2, T)
    // and therefore we can deduce T2 uses Emps.
    // 注释：例如，给定物化视图：
    //   T = Emps Join Depts
    //   T2 = T Group by C1
    // 图将包含
    //   (T, Emps), (T, Depts), (T2, T)
    // 因此我们可以推断T2使用了Emps
    final Graphs.FrozenGraph<List<String>, DefaultEdge> frozenGraph =  // 声明：冻结的不可变图，用于高效的图查询
        Graphs.makeImmutable(usesGraph);  // 创建：将可变图转换为不可变图（提高查询性能）
    final Set<RelOptTable> queryTablesUsed = RelOptUtil.findTables(rel);  // 获取：查询中使用的所有表
    final List<RelOptMaterialization> applicableMaterializations =  // 声明：适用的物化视图列表
        new ArrayList<>();  // 初始化：动态数组列表
    for (List<String> qname : TopologicalOrderIterator.of(usesGraph)) {  // 循环：按拓扑顺序遍历图中的顶点（确保先处理基础物化视图）
      RelOptMaterialization materialization = qnameMap.get(qname);  // 获取：对应表名的物化视图
      if (materialization != null  // 判断：如果物化视图存在（不是基础表）
          && usesTable(materialization.qualifiedTableName, queryTablesUsed, frozenGraph)) {  // 判断：并且物化视图使用了查询中的表（通过图查询）
        applicableMaterializations.add(materialization);  // 添加：物化视图到适用列表
      }
    }
    return applicableMaterializations;  // 返回：适用的物化视图列表
  }

  private static List<RelNode> substitute(  // 私有静态方法：尝试用物化视图替换查询中的部分
      RelNode root, RelOptMaterialization materialization,  // 参数：查询根节点和物化视图
      List<SubstitutionVisitor.UnifyRule> materializationRules) {  // 参数：物化视图识别规则列表
    // First, if the materialization is in terms of a star table, rewrite
    // the query in terms of the star table.
    // 注释：首先，如果物化视图基于星型表，则将查询重写为使用星型表
    if (materialization.starRelOptTable != null) {  // 判断：如果物化视图关联了星型表（格瓦片）
      RelNode newRoot =  // 声明：新的查询根节点
          RelOptMaterialization.tryUseStar(root, materialization.starRelOptTable);  // 调用：尝试使用星型表重写查询
      if (newRoot != null) {  // 判断：如果重写成功
        root = newRoot;  // 更新：使用重写后的查询根节点
      }
    }

    // Push filters to the bottom, and combine projects on top.
    // 注释：将过滤器推到底部，合并顶部的投影
    RelNode target = materialization.queryRel;  // 获取：物化视图的查询定义（目标表达式）
    // try to trim unused field in relational expressions.
    // 注释：尝试修剪关系表达式中未使用的字段
    root = trimUnusedfields(root);  // 调用：修剪查询中未使用的字段
    target = trimUnusedfields(target);  // 调用：修剪物化视图查询中未使用的字段
    HepProgram program =  // 声明：HEP优化程序，定义规则应用顺序
        new HepProgramBuilder()  // 创建：HEP程序构建器
            .addRuleInstance(CoreRules.FILTER_PROJECT_TRANSPOSE)  // 添加：过滤器和投影交换规则（将投影下推到过滤器下）
            .addRuleInstance(CoreRules.FILTER_MERGE)  // 添加：过滤器合并规则（合并相邻的过滤器）
            .addRuleInstance(CoreRules.FILTER_INTO_JOIN)  // 添加：过滤器下推到连接规则
            .addRuleInstance(CoreRules.JOIN_CONDITION_PUSH)  // 添加：连接条件下推规则
            .addRuleInstance(CoreRules.FILTER_AGGREGATE_TRANSPOSE)  // 添加：过滤器和聚合交换规则
            .addRuleInstance(CoreRules.PROJECT_MERGE)  // 添加：投影合并规则（合并相邻的投影）
            .addRuleInstance(CoreRules.PROJECT_REMOVE)  // 添加：移除冗余投影规则
            .addRuleInstance(CoreRules.PROJECT_JOIN_TRANSPOSE)  // 添加：投影和连接交换规则
            .addRuleInstance(CoreRules.PROJECT_SET_OP_TRANSPOSE)  // 添加：投影和集合操作交换规则
            .addRuleInstance(CoreRules.AGGREGATE_PROJECT_PULL_UP_CONSTANTS)  // 添加：聚合投影上拉常量规则
            .addRuleInstance(CoreRules.FILTER_TO_CALC)  // 添加：过滤器转换为Calc规则
            .addRuleInstance(CoreRules.PROJECT_TO_CALC)  // 添加：投影转换为Calc规则
            .addRuleInstance(CoreRules.FILTER_CALC_MERGE)  // 添加：过滤器和Calc合并规则
            .addRuleInstance(CoreRules.PROJECT_CALC_MERGE)  // 添加：投影和Calc合并规则
            .addRuleInstance(CoreRules.CALC_MERGE)  // 添加：Calc合并规则
            .build();  // 构建：优化程序

    // We must use the same HEP planner for the two optimizations below.
    // Thus different nodes with the same digest will share the same vertex in
    // the plan graph. This is important for the matching process.
    // 注释：必须使用相同的HEP规划器进行以下两次优化
    // 因此具有相同摘要的不同节点将在计划图中共享相同的顶点
    // 这对于匹配过程很重要
    final HepPlanner hepPlanner = new HepPlanner(program);  // 创建：HEP规划器实例，使用上述优化程序
    hepPlanner.setRoot(target);  // 设置：物化视图查询作为根节点
    target = hepPlanner.findBestExp();  // 执行：优化物化视图查询（应用所有规则）

    hepPlanner.setRoot(root);  // 设置：用户查询作为根节点
    root = hepPlanner.findBestExp();  // 执行：优化用户查询（应用所有规则）

    return new SubstitutionVisitor(target, root, ImmutableList.  // 返回：使用替换访问器尝试将物化视图替换到查询中
        <SubstitutionVisitor.UnifyRule>builder()  // 创建：不可变列表构建器
        .addAll(materializationRules)  // 添加：所有物化视图识别规则
        .build()).go(materialization.tableRel);  // 构建：规则列表并调用替换方法，返回替换后的查询列表
  }

  /**
   * Trim unused fields in relational expressions.
   * 修剪关系表达式中未使用的字段
   * 这个方法通过分析查询的输出字段，移除所有未被使用的字段
   * 这可以减少数据传输量，提高查询性能
   *
   * @param relNode 待修剪的关系表达式节点
   * @return 修剪后的关系表达式节点
   */
  private static RelNode trimUnusedfields(RelNode relNode) {  // 私有静态方法：修剪未使用的字段
    final List<RelOptTable> relOptTables = RelOptUtil.findAllTables(relNode);  // 获取：关系表达式中使用的所有表
    RelOptSchema relOptSchema = null;  // 声明：关系优化模式（schema），初始化为null
    if (relOptTables.size() != 0) {  // 判断：如果使用了至少一个表
      relOptSchema = relOptTables.get(0).getRelOptSchema();  // 获取：第一个表的关系优化模式
    }
    final RelBuilder relBuilder =  // 声明：关系表达式构建器，用于构建新的关系表达式
        RelFactories.LOGICAL_BUILDER.create(relNode.getCluster(), relOptSchema);  // 创建：逻辑构建器实例
    final RelFieldTrimmer relFieldTrimmer = new RelFieldTrimmer(null, relBuilder);  // 创建：字段修剪器实例
    return relFieldTrimmer.trim(relNode);  // 返回：修剪后的关系表达式
  }

  /**
   * Returns whether {@code table} uses one or more of the tables in
   * {@code usedTables}.
   * 返回指定表是否使用了usedTables中的一个或多个表
   * 通过图的最短路径算法来判断表之间的依赖关系
   * 如果从查询表到物化视图表存在路径，则说明物化视图依赖于查询表
   *
   * @param qualifiedName 待检查的表的完全限定名（如["catalog", "schema", "table"]）
   * @param usedTables 查询中使用的表集合
   * @param usesGraph 表依赖关系图（冻结的有向图）
   * @return 如果物化视图使用了查询中的表，返回true；否则返回false
   */
  private static boolean usesTable(  // 私有静态方法：判断表是否使用了查询中的表
      List<String> qualifiedName,  // 参数：待检查表的完全限定名
      Set<RelOptTable> usedTables,  // 参数：查询中使用的表集合
      Graphs.FrozenGraph<List<String>, DefaultEdge> usesGraph) {  // 参数：表依赖关系图
    for (RelOptTable queryTable : usedTables) {  // 循环：遍历查询中使用的每个表
      if (usesGraph.getShortestDistance(queryTable.getQualifiedName(), qualifiedName)  // 调用：计算从查询表到物化视图表的最短路径
          != -1) {  // 判断：如果存在路径（距离不为-1）
        return true;  // 返回：物化视图使用了查询中的表
      }
    }
    return false;  // 返回：物化视图没有使用查询中的任何表
  }
}
