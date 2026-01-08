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
// Apache License 2.0 开源许可证声明，允许在遵守许可证条款的前提下使用、修改和分发代码
package org.apache.calcite.materialize; // 定义包名，该类属于Apache Calcite的物化视图(materialize)模块

import org.apache.calcite.jdbc.CalciteSchema; // 导入CalciteSchema类，用于表示Calcite模式(schema)结构
import org.apache.calcite.plan.RelOptCostImpl; // 导入RelOptCostImpl类，用于实现关系代数优化成本模型
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil类，提供关系代数优化工具方法
import org.apache.calcite.plan.hep.HepPlanner; // 导入HepPlanner类，基于启发式的规划器，用于转换关系表达式
import org.apache.calcite.plan.hep.HepProgram; // 导入HepProgram类，定义HepPlanner的转换规则程序
import org.apache.calcite.plan.hep.HepProgramBuilder; // 导入HepProgramBuilder类，用于构建HepProgram
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系代数表达式树的基类
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate类，表示聚合操作(如SUM, COUNT等)
import org.apache.calcite.rel.core.AggregateCall; // 导入AggregateCall类，表示聚合函数调用
import org.apache.calcite.rel.core.Filter; // 导入Filter类，表示过滤操作(WHERE子句)
import org.apache.calcite.rel.core.Join; // 导入Join类，表示连接操作(JOIN)
import org.apache.calcite.rel.core.Project; // 导入Project类，表示投影操作(SELECT子句中的表达式)
import org.apache.calcite.rel.core.SetOp; // 导入SetOp类，表示集合操作(UNION, INTERSECT, EXCEPT)
import org.apache.calcite.rel.core.Sort; // 导入Sort类，表示排序操作(ORDER BY)
import org.apache.calcite.rel.core.TableScan; // 导入TableScan类，表示表扫描操作
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules类，包含Calcite核心优化规则
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型字段
import org.apache.calcite.rex.RexInputRef; // 导入RexInputRef类，表示行表达式输入引用(引用输入字段)
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式的基类
import org.apache.calcite.runtime.FlatLists; // 导入FlatLists类，提供扁平列表工具方法
import org.apache.calcite.sql.SqlAggFunction; // 导入SqlAggFunction类，表示SQL聚合函数
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SqlValidatorUtil类，提供SQL验证工具方法
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig类，表示Calcite框架配置
import org.apache.calcite.util.CompositeList; // 导入CompositeList类，表示组合列表
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合
import org.apache.calcite.util.ImmutableNullableList; // 导入ImmutableNullableList类，表示可包含null的不可变列表
import org.apache.calcite.util.Pair; // 导入Pair类，表示键值对
import org.apache.calcite.util.Util; // 导入Util类，提供通用工具方法
import org.apache.calcite.util.graph.AttributedDirectedGraph; // 导入AttributedDirectedGraph类，表示带属性的有向图
import org.apache.calcite.util.graph.CycleDetector; // 导入CycleDetector类，用于检测图中的环
import org.apache.calcite.util.graph.DefaultEdge; // 导入DefaultEdge类，表示图的默认边
import org.apache.calcite.util.graph.TopologicalOrderIterator; // 导入TopologicalOrderIterator类，用于图的拓扑排序迭代
import org.apache.calcite.util.mapping.IntPair; // 导入IntPair类，表示整数对

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合
import com.google.common.collect.LinkedListMultimap; // 导入Google Guava的链表多重映射
import com.google.common.collect.Multimap; // 导入Google Guava的多重映射接口

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可空类型

import java.util.ArrayList; // 导入Java标准库的ArrayList动态数组
import java.util.Collection; // 导入Java标准库的Collection集合接口
import java.util.HashMap; // 导入Java标准库的HashMap哈希映射
import java.util.HashSet; // 导入Java标准库的HashSet哈希集合
import java.util.IdentityHashMap; // 导入Java标准库的IdentityHashMap(使用==比较的哈希映射)
import java.util.LinkedHashMap; // 导入Java标准库的LinkedHashMap(保持插入顺序的哈希映射)
import java.util.LinkedHashSet; // 导入Java标准库的LinkedHashSet(保持插入顺序的哈希集合)
import java.util.List; // 导入Java标准库的List列表接口
import java.util.Locale; // 导入Java标准库的Locale区域设置类
import java.util.Map; // 导入Java标准库的Map映射接口
import java.util.Set; // 导入Java标准库的Set集合接口
import java.util.function.Function; // 导入Java标准库的Function函数式接口

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于非空检查

/**
 * Algorithm that suggests a set of lattices.
 * 算法：建议一组lattice(格)的算法
 * Lattice是物化视图的一种形式，表示一个星型模型或多维数据集
 * 该类通过分析SQL查询，自动建议应该创建哪些lattice来优化查询性能
 * 它能够识别查询中的表连接模式、聚合操作和维度，从而推荐最优的物化视图结构
 */
public class LatticeSuggester { // 定义LatticeSuggester类，用于建议lattice集合的算法
  final LatticeSpace space; // LatticeSpace对象，用于管理lattice空间，存储所有表、步骤和表达式的全局信息

  private static final HepProgram PROGRAM = // 定义静态常量HepProgram，表示HepPlanner的转换规则程序
      new HepProgramBuilder() // 创建HepProgramBuilder对象，用于构建HepProgram
          .addRuleInstance(CoreRules.FILTER_INTO_JOIN) // 添加规则：将过滤器推入连接操作内部(优化WHERE条件)
          .addRuleInstance(CoreRules.JOIN_CONDITION_PUSH) // 添加规则：将连接条件向下推送到叶子节点(优化JOIN条件)
          .build(); // 构建HepProgram对象

  /** Lattices, indexed by digest. Uses LinkedHashMap for determinacy. */
  // Lattice映射表，使用摘要(digest)作为键，使用LinkedHashMap保证确定性顺序
  final Map<String, Lattice> latticeMap = new LinkedHashMap<>(); // 存储所有建议的lattice，键为lattice的字符串表示，值为Lattice对象

  /** Lattices that have been made obsolete. Key is the obsolete lattice, value
   * is the lattice that superseded it. */
  // 已过时的lattice映射表，键是被废弃的lattice，值是替代它的新lattice
  private final Map<Lattice, Lattice> obsoleteLatticeMap = new HashMap<>(); // 存储已被其他lattice替代的lattice

  /** Whether to try to extend an existing lattice when adding a lattice. */
  // 布尔标志，指示是否尝试扩展现有lattice而不是创建新的
  private final boolean evolve; // 如果为true，则在添加lattice时会尝试扩展现有lattice；如果为false，则总是创建新lattice

  /** Creates a LatticeSuggester. */
  // 构造方法：创建LatticeSuggester实例
  public LatticeSuggester(FrameworkConfig config) { // 接收FrameworkConfig参数，包含Calcite框架的配置信息
    this.evolve = config.isEvolveLattice(); // 从配置中获取是否启用lattice演进的标志
    space = new LatticeSpace(config.getStatisticProvider()); // 创建LatticeSpace对象，传入统计提供器用于获取表的统计信息
  }

  /** Returns the minimal set of lattices necessary to cover all of the queries
   * seen. Any lattices that are subsumed by other lattices are not included. */
  // 方法：返回覆盖所有已见查询所需的最小lattice集合
  // 被其他lattice包含的lattice不会被包含在返回结果中
  public Set<Lattice> getLatticeSet() { // 返回类型为Lattice的Set集合
    final Set<Lattice> set = new LinkedHashSet<>(latticeMap.values()); // 创建LinkedHashSet，复制latticeMap中的所有值
    set.removeAll(obsoleteLatticeMap.keySet()); // 移除所有已过时的lattice(它们被其他lattice替代了)
    return ImmutableSet.copyOf(set); // 返回不可变的集合副本，防止外部修改
  }

  /** Converts a column reference to an expression. */
  // 方法：将列引用转换为RexNode表达式
  public RexNode toRex(LatticeTable table, int column) { // 接收lattice表和列索引参数
    final List<RelDataTypeField> fieldList = // 获取表的字段列表
        table.t.getRowType().getFieldList(); // 通过表的行类型获取所有字段
    if (column < fieldList.size()) { // 如果列索引在基础字段范围内
      return new RexInputRef(column, fieldList.get(column).getType()); // 返回输入引用表达式，引用基础表列
    } else { // 如果列索引超出基础字段范围，说明是派生表达式列
      return requireNonNull(space.tableExpressions.get(table), // 从LatticeSpace获取该表的派生表达式列表
          () -> "space.tableExpressions.get(table) is null for " + table) // 如果为null则抛出异常并显示错误信息
          .get(column - fieldList.size()); // 获取对应的派生表达式(减去基础字段数量得到偏移量)
    }
  }

  /** Adds a query.
   *
   * <p>It may fit within an existing lattice (or lattices). Or it may need a
   * new lattice, or an extension to an existing lattice.
   *
   * @param r Relational expression for a query
   *
   * @return A list of join graphs: usually 1; more if the query contains a
   * cartesian product; zero if the query graph is cyclic
   */
  // 方法：添加一个查询到lattice建议器
  // 该查询可能适合现有的lattice，或者需要新的lattice，或者扩展现有lattice
  // 参数r：表示查询的关系代数表达式
  // 返回值：连接图的列表，通常为1个；如果查询包含笛卡尔积则更多；如果查询图有环则为0
  public List<Lattice> addQuery(RelNode r) { // 接收关系表达式参数
    // Push filters into joins and towards leaves
    // 注释：将过滤器推入连接操作并向叶子节点推送(优化步骤)
    final HepPlanner planner = // 创建HepPlanner规划器实例
        new HepPlanner(PROGRAM, null, true, null, RelOptCostImpl.FACTORY); // 使用预定义的PROGRAM规则程序，启用成本估算
    planner.setRoot(r); // 设置规划器的根节点为输入的关系表达式
    final RelNode r2 = planner.findBestExp(); // 执行优化，获取优化后的关系表达式(过滤器已下推)

    final Query q = new Query(space); // 创建Query对象，用于表示查询图的状态
    final List<Frame> frameList = new ArrayList<>(); // 创建帧列表，用于存储查询的各个部分
    frames(frameList, q, r2); // 调用frames方法，将关系表达式分解为帧列表
    final List<Lattice> lattices = new ArrayList<>(); // 创建lattice列表，用于存储建议的lattice
    frameList.forEach(frame -> addFrame(q, frame, lattices)); // 遍历每个帧，调用addFrame方法处理
    return ImmutableList.copyOf(lattices); // 返回不可变的lattice列表
  }

  private void addFrame(Query q, Frame frame, List<Lattice> lattices) { // 私有方法：将帧添加到lattice列表
    final AttributedDirectedGraph<TableRef, StepRef> g = // 创建带属性的有向图，顶点是TableRef，边是StepRef
        AttributedDirectedGraph.create(new StepRef.Factory()); // 使用StepRef.Factory作为边的工厂
    final Multimap<Pair<TableRef, TableRef>, IntPair> map = // 创建多重映射，键是表引用对，值是列索引对
        LinkedListMultimap.create(); // 使用LinkedListMultimap实现，允许一对多映射
    for (TableRef tableRef : frame.tableRefs) { // 遍历帧中的所有表引用
      g.addVertex(tableRef); // 将每个表引用作为顶点添加到图中
    }
    for (Hop hop : frame.hops) { // 遍历帧中的所有跳跃(连接条件)
      map.put(Pair.of(hop.source.tableRef(), hop.target.tableRef()), // 将源表和目标表的引用对作为键
          IntPair.of(hop.source.col(space), hop.target.col(space))); // 将源列和目标列的索引对作为值
    }
    for (Map.Entry<Pair<TableRef, TableRef>, Collection<IntPair>> e // 遍历映射的每个条目
        : map.asMap().entrySet()) { // 获取映射的视图
      final TableRef source = e.getKey().left; // 获取源表引用
      final TableRef target = e.getKey().right; // 获取目标表引用
      final StepRef stepRef = // 创建步骤引用
          q.stepRef(source, target, ImmutableList.copyOf(e.getValue())); // 调用Query的stepRef方法，传入源表、目标表和键列表
      g.addVertex(stepRef.source()); // 确保源顶点存在
      g.addVertex(stepRef.target()); // 确保目标顶点存在
      g.addEdge(stepRef.source(), stepRef.target(), stepRef.step, // 添加边，属性是步骤和查询中的序号
          stepRef.ordinalInQuery);
    }

    // If the join graph is cyclic, we can't use it.
    // 注释：如果连接图有环，则无法使用(因为lattice要求无环图)
    final Set<TableRef> cycles = new CycleDetector<>(g).findCycles(); // 使用CycleDetector检测图中的环
    if (!cycles.isEmpty()) { // 如果存在环
      return; // 直接返回，不处理此帧
    }

    // Translate the query graph to mutable nodes
    // 注释：将查询图转换为可变节点(MutableNode)结构
    final IdentityHashMap<TableRef, @Nullable MutableNode> nodes = new IdentityHashMap<>(); // 使用IdentityHashMap存储表引用到可变节点的映射(使用==比较)
    final Map<List, MutableNode> nodesByParent = new HashMap<>(); // 存储根据父节点、表和键组合的节点映射，用于去重
    final List<MutableNode> rootNodes = new ArrayList<>(); // 存储根节点列表(没有父节点的节点)
    for (TableRef tableRef : TopologicalOrderIterator.of(g)) { // 按拓扑顺序遍历图中的每个表引用
      final List<StepRef> edges = g.getInwardEdges(tableRef); // 获取指向该表的所有入边
      final MutableNode node; // 声明可变节点变量
      switch (edges.size()) { // 根据入边数量进行分支处理
      case 0: // 如果没有入边，说明是根节点
        node = new MutableNode(tableRef.table); // 创建新的可变节点，只有表信息
        rootNodes.add(node); // 将节点添加到根节点列表
        break; // 跳出switch
      case 1: // 如果只有一条入边，说明是单父节点
        final StepRef edge = edges.get(0); // 获取唯一的入边
        final MutableNode parent = nodes.get(edge.source()); // 获取父节点
        final List key = // 创建键用于去重
            FlatLists.of(parent, tableRef.table, edge.step.keys); // 组合父节点、表和键作为键
        final MutableNode existingNode = nodesByParent.get(key); // 检查是否已存在相同的节点
        if (existingNode == null) { // 如果不存在
          node = new MutableNode(tableRef.table, parent, edge.step); // 创建新的可变节点，包含父节点和步骤信息
          nodesByParent.put(key, node); // 将新节点存入去重映射
        } else { // 如果已存在
          node = existingNode; // 复用已存在的节点
        }
        break; // 跳出switch
      default: // 如果有多条入边，说明是钻石形结构(多个父节点指向同一子节点)
        for (StepRef edge2 : edges) { // 遍历所有入边
          final MutableNode parent2 = nodes.get(edge2.source()); // 获取每个边的父节点
          requireNonNull( // 确保父节点不为null
              parent2, // 如果为null则抛出异常
              () -> "parent for " + edge2.source()); // 错误信息
          final MutableNode node2 = // 为每个父节点创建子节点
              new MutableNode(tableRef.table, parent2, edge2.step); // 创建新的可变节点
          parent2.children.add(node2); // 将子节点添加到父节点的子节点列表
        }
        node = null; // 不创建主节点，因为已经为每个父节点创建了子节点
        break; // 跳出switch
      }
      nodes.put(tableRef, node); // 将表引用映射到节点(可能为null)
    }

    // Transcribe the hierarchy of mutable nodes to immutable nodes
    // 注释：将可变节点的层次结构转换为不可变节点，并构建lattice
    for (MutableNode rootNode : rootNodes) { // 遍历所有根节点
      if (rootNode.isCyclic()) { // 检查节点树是否有环
        continue; // 如果有环则跳过此根节点
      }
      final CalciteSchema rootSchema = CalciteSchema.createRootSchema(false); // 创建根模式(不缓存)
      final Lattice.Builder latticeBuilder = // 创建lattice构建器
          new Lattice.Builder(space, rootSchema, rootNode); // 传入lattice空间、根模式和根节点

      final List<MutableNode> flatNodes = new ArrayList<>(); // 创建扁平节点列表
      rootNode.flatten(flatNodes); // 将节点树扁平化为列表，方便索引

      for (MutableMeasure measure : frame.measures) { // 遍历帧中的所有度量(聚合函数)
        for (ColRef arg : measure.arguments) { // 遍历度量的所有参数(列引用)
          if (arg == null) { // 如果参数为null
            // Cannot handle expressions, e.g. "sum(x + 1)" yet
            // 注释：还无法处理表达式，例如"sum(x + 1)"
            return; // 直接返回，不处理此帧
          }
        }
        latticeBuilder.addMeasure( // 向lattice构建器添加度量
            new Lattice.Measure(measure.aggregate, measure.distinct, // 创建Lattice.Measure对象
                measure.name, // 使用聚合函数、是否去重、名称
                Util.transform(measure.arguments, colRef -> { // 转换参数列表
                  final Lattice.Column column; // 声明lattice列变量
                  if (colRef instanceof BaseColRef) { // 如果是基础列引用
                    final BaseColRef baseColRef = (BaseColRef) colRef; // 强制类型转换
                    final MutableNode node = nodes.get(baseColRef.t); // 获取对应的可变节点
                    final int table = flatNodes.indexOf(node); // 获取节点在扁平列表中的索引
                    column = latticeBuilder.column(table, baseColRef.c); // 创建基础列
                  } else if (colRef instanceof DerivedColRef) { // 如果是派生列引用(表达式)
                    final DerivedColRef derivedColRef = // 强制类型转换
                        (DerivedColRef) colRef;
                    final String alias = deriveAlias(measure, derivedColRef); // 派生别名
                    column = // 创建表达式列
                        latticeBuilder.expression(derivedColRef.e, alias, // 使用表达式和别名
                            derivedColRef.tableAliases()); // 和表别名列表
                  } else { // 其他情况
                    throw new AssertionError("expression in measure"); // 抛出断言错误
                  }
                  latticeBuilder.use(column, true); // 标记该列被度量使用
                  return column; // 返回lattice列
                }))); // 结束transform和addMeasure
      }

      for (int i = 0; i < frame.columnCount; i++) { // 遍历帧的所有列
        final ColRef c = frame.column(i); // 获取第i列的列引用
        if (c instanceof DerivedColRef) { // 如果是派生列引用(表达式)
          final DerivedColRef derivedColRef = (DerivedColRef) c; // 强制类型转换
          final Lattice.Column expression = // 创建表达式列
              latticeBuilder.expression(derivedColRef.e, // 使用表达式
                  derivedColRef.alias, derivedColRef.tableAliases()); // 别名和表别名
          latticeBuilder.use(expression, false); // 标记该列被维度使用(非度量)
        }
      }

      final Lattice lattice0 = latticeBuilder.build(); // 构建lattice0
      final Lattice lattice1 = findMatch(lattice0, rootNode); // 查找匹配的lattice(可能复用或扩展现有lattice)
      lattices.add(lattice1); // 将lattice添加到结果列表
    }
  }

  /** Derives the alias of an expression that is the argument to a measure.
   *
   * <p>For example, if the measure is called "sum_profit" and the aggregate
   * function is "sum", returns "profit".
   */
  // 私有静态方法：派生度量参数表达式的别名
  // 例如，如果度量名为"sum_profit"且聚合函数为"sum"，则返回"profit"
  private static String deriveAlias(MutableMeasure measure, // 度量对象
      DerivedColRef derivedColRef) { // 派生列引用
    if (!derivedColRef.alias.contains("$")) { // 如果别名不包含$(不是系统生成的)
      // User specified an alias. Use that.
      // 注释：用户指定了别名，直接使用
      return derivedColRef.alias; // 返回用户指定的别名
    }
    String alias = requireNonNull(measure.name, "measure.name"); // 获取度量名称，确保不为null
    if (alias.contains("$")) { // 如果度量名称包含$(系统生成的)
      // User did not specify an alias for the aggregate function, and it got a
      // system-generated name like 'EXPR$2'. Don't try to derive anything from
      // it.
      // 注释：用户没有为聚合函数指定别名，系统生成了类似'EXPR$2'的名称，不要尝试从中派生
      return derivedColRef.alias; // 直接返回派生列的别名
    }
    final String aggUpper = // 将聚合函数名转为大写
        measure.aggregate.getName().toUpperCase(Locale.ROOT); // 使用ROOT区域设置
    final String aliasUpper = alias.toUpperCase(Locale.ROOT); // 将别名转为大写
    if (aliasUpper.startsWith(aggUpper + "_")) { // 如果别名以"聚合函数名_"开头
      // Convert "sum_profit" to "profit"
      // 注释：将"sum_profit"转换为"profit"
      return alias.substring((aggUpper + "_").length()); // 截取前缀，返回剩余部分
    } else if (aliasUpper.startsWith(aggUpper)) { // 如果别名以聚合函数名开头(无下划线)
      // Convert "sumprofit" to "profit"
      // 注释：将"sumprofit"转换为"profit"
      return alias.substring(aggUpper.length()); // 截取前缀，返回剩余部分
    } else if (aliasUpper.endsWith("_" + aggUpper)) { // 如果别名以"_聚合函数名"结尾
      // Convert "profit_sum" to "profit"
      // 注释：将"profit_sum"转换为"profit"
      return alias.substring(0, alias.length() - ("_" + aggUpper).length()); // 截取后缀，返回前缀
    } else if (aliasUpper.endsWith(aggUpper)) { // 如果别名以聚合函数名结尾(无下划线)
      // Convert "profitsum" to "profit"
      // 注释：将"profitsum"转换为"profit"
      return alias.substring(0, alias.length() - aggUpper.length()); // 截取后缀，返回前缀
    } else { // 其他情况
      return alias; // 直接返回原别名
    }
  }

  /** Returns the best match for a lattice. If no match, registers the lattice
   * and returns it. Never returns null. */
  // 私有方法：返回lattice的最佳匹配。如果没有匹配，则注册该lattice并返回。永不返回null
  private Lattice findMatch(final Lattice lattice, MutableNode mutableNode) { // 接收lattice和可变节点参数
    final Lattice lattice1 = latticeMap.get(lattice.toString()); // 尝试从映射中获取精确匹配的lattice
    if (lattice1 != null) { // 如果找到精确匹配
      // Exact match for an existing lattice
      // 注释：精确匹配现有的lattice
      return lattice1; // 返回现有的lattice
    }

    if (evolve) { // 如果启用了lattice演进
      // No exact match. Scan existing lattices for a sub-set.
      // 注释：没有精确匹配，扫描现有lattice寻找子集关系
      int bestMatchQuality = 0; // 初始化最佳匹配质量为0
      Lattice bestMatch = null; // 初始化最佳匹配为null
      for (Lattice lattice2 : latticeMap.values()) { // 遍历所有现有的lattice
        int q = matchQuality(lattice2, lattice); // 计算匹配质量
        if (q > bestMatchQuality) { // 如果质量更好
          bestMatch = lattice2; // 更新最佳匹配
          bestMatchQuality = q; // 更新最佳质量
        } else if (q == bestMatchQuality // 如果质量相等
            && bestMatch != null // 且最佳匹配不为null
            && !lattice2.rootNode.paths.equals(bestMatch.rootNode.paths) // 且路径不同
            && lattice2.rootNode.paths.containsAll(bestMatch.rootNode.paths)) { // 且lattice2包含bestMatch的所有路径
          bestMatch = lattice2; // 选择包含更多路径的lattice
        }
      }

      if (bestMatch != null) { // 如果找到了最佳匹配
        // Fix up the best batch
        // 注释：修复最佳批次
        for (Path path // 遍历需要添加的路径
            : minus(bestMatch.rootNode.paths, lattice.rootNode.paths)) { // 计算路径差集
          // TODO: assign alias based on node in bestMatch
          // TODO：根据bestMatch中的节点分配别名
          mutableNode.addPath(path, null); // 将路径添加到可变节点(别名为null)
        }
        final CalciteSchema rootSchema = CalciteSchema.createRootSchema(false); // 创建根模式
        final Lattice.Builder builder = // 创建lattice构建器
            new Lattice.Builder(space, rootSchema, mutableNode); // 传入空间、模式和节点
        copyMeasures(builder, bestMatch); // 复制最佳匹配的度量
        copyMeasures(builder, lattice); // 复制新lattice的度量
        final Lattice lattice2 = builder.build(); // 构建合并后的lattice
        latticeMap.remove(bestMatch.toString()); // 从映射中移除旧的bestMatch
        obsoleteLatticeMap.put(bestMatch, lattice2); // 标记bestMatch为过时，被lattice2替代
        latticeMap.put(lattice2.toString(), lattice2); // 将新lattice添加到映射
        return lattice2; // 返回合并后的lattice
      }
    }

    // No suitable existing lattice. Register this one.
    // 注释：没有合适的现有lattice，注册这个新的
    latticeMap.put(lattice.toString(), lattice); // 将新lattice添加到映射
    return lattice; // 返回新lattice
  }

  /** Copies measures and column usages from an existing lattice into a builder,
   * using a mapper to translate old-to-new columns, so that the new lattice can
   * inherit from the old. */
  // 私有静态方法：将现有lattice的度量和列使用情况复制到构建器中
  // 使用映射器将旧列转换为新列，使新lattice可以继承旧lattice的属性
  private static void copyMeasures(Lattice.Builder builder, Lattice lattice) { // 接收构建器和源lattice
    final Function<Lattice.Column, Lattice.Column> mapper = // 创建列映射函数，将旧列映射到新列
        (Lattice.Column c) -> { // 接收旧列，返回新列
          if (c instanceof Lattice.BaseColumn) { // 如果是基础列
            Lattice.BaseColumn baseColumn = (Lattice.BaseColumn) c; // 强制类型转换
            Pair<Path, Integer> p = lattice.columnToPathOffset(baseColumn); // 将列转换为路径和偏移量
            return builder.pathOffsetToColumn(p.left, p.right); // 在构建器中根据路径和偏移量创建新列
          } else { // 如果是派生列(表达式)
            final Lattice.DerivedColumn derivedColumn = (Lattice.DerivedColumn) c; // 强制类型转换
            return builder.expression(derivedColumn.e, derivedColumn.alias, // 在构建器中创建表达式列
                derivedColumn.tables); // 使用表达式、别名和表列表
          }
        };
    for (Lattice.Measure measure : lattice.defaultMeasures) { // 遍历源lattice的所有默认度量
      builder.addMeasure(measure.copy(mapper)); // 复制度量，使用映射器转换列引用
    }
    for (Map.Entry<Integer, Boolean> entry : lattice.columnUses.entries()) { // 遍历所有列使用情况
      final Lattice.Column column = lattice.columns.get(entry.getKey()); // 获取列对象
      builder.use(mapper.apply(column), entry.getValue()); // 标记列的使用情况(度量或维度)
    }
  }

  private static int matchQuality(Lattice lattice, Lattice target) { // 私有静态方法：计算两个lattice的匹配质量
    if (!lattice.rootNode.table.equals(target.rootNode.table)) { // 如果根表不同
      return 0; // 返回0，完全不匹配
    }
    if (lattice.rootNode.paths.equals(target.rootNode.paths)) { // 如果路径完全相同
      return 3; // 返回3，完全匹配
    }
    if (lattice.rootNode.paths.containsAll(target.rootNode.paths)) { // 如果lattice包含target的所有路径(超集)
      return 2; // 返回2，lattice是target的超集
    }
    return 1; // 返回1，根表相同但路径关系不明确
  }

  private static <E> Set<E> minus(Collection<E> c, Collection<E> c2) { // 私有静态方法：计算集合差集(c - c2)
    final LinkedHashSet<E> c3 = new LinkedHashSet<>(c); // 创建c的副本
    c3.removeAll(c2); // 移除c2中包含的所有元素
    return c3; // 返回差集
  }

  private static void frames(List<Frame> frames, final Query q, RelNode r) { // 私有静态方法：将关系表达式分解为帧列表
    if (r instanceof SetOp) { // 如果是集合操作(UNION等)
      r.getInputs().forEach(input -> frames(frames, q, input)); // 递归处理每个输入
    } else { // 如果不是集合操作
      final Frame frame = frame(q, r); // 调用frame方法创建帧
      if (frame != null) { // 如果帧创建成功
        frames.add(frame); // 将帧添加到列表
      }
    }
  }

  private static @Nullable Frame frame(final Query q, RelNode r) { // 私有静态方法：将关系表达式转换为帧
    if (r instanceof Sort) { // 如果是排序操作
      final Sort sort = (Sort) r; // 强制类型转换
      return frame(q, sort.getInput()); // 递归处理输入(忽略排序)
    } else if (r instanceof Filter) { // 如果是过滤操作
      final Filter filter = (Filter) r; // 强制类型转换
      return frame(q, filter.getInput()); // 递归处理输入(忽略过滤器)
    } else if (r instanceof Aggregate) { // 如果是聚合操作
      final Aggregate aggregate = (Aggregate) r; // 强制类型转换
      final Frame h = frame(q, aggregate.getInput()); // 递归处理输入
      if (h == null) { // 如果输入帧为null
        return null; // 返回null
      }
      final List<MutableMeasure> measures = new ArrayList<>(); // 创建度量列表
      for (AggregateCall call : aggregate.getAggCallList()) { // 遍历所有聚合调用
        String name = // 生成唯一的度量名称
            SqlValidatorUtil.uniquify(call.name, q.usedNames, SqlValidatorUtil.ATTEMPT_SUGGESTER); // 确保名称唯一
        measures.add( // 添加度量
            new MutableMeasure(call.getAggregation(), call.isDistinct(), // 使用聚合函数、是否去重
                Util.<Integer, @Nullable ColRef>transform(call.getArgList(), h::column), // 转换参数列表为列引用
                name)); // 使用生成的名称
      }
      final int fieldCount = r.getRowType().getFieldCount(); // 获取输出字段数量
      return new Frame(fieldCount, h.hops, measures, ImmutableList.of(h)) { // 创建新的帧
        @Override @Nullable ColRef column(int offset) { // 重写column方法
          if (offset < aggregate.getGroupSet().cardinality()) { // 如果是分组列
            return h.column(aggregate.getGroupSet().nth(offset)); // 返回分组列的引用
          }
          return null; // 返回null，表示这是聚合函数结果，没有直接映射
        }
      };
    } else if (r instanceof Project) { // 如果是投影操作
      final Project project = (Project) r; // 强制类型转换
      final Frame h = frame(q, project.getInput()); // 递归处理输入
      if (h == null) { // 如果输入帧为null
        return null; // 返回null
      }
      final int fieldCount = r.getRowType().getFieldCount(); // 获取输出字段数量
      return new Frame(fieldCount, h.hops, h.measures, ImmutableList.of(h)) { // 创建新的帧
        final List<@Nullable ColRef> columns; // 声明列引用列表

        { // 初始化块
          final ImmutableNullableList.Builder<@Nullable ColRef> columnBuilder = // 创建可空列构建器
              ImmutableNullableList.builder();
          for (Pair<RexNode, String> p : project.getNamedProjects()) { // 遍历所有命名的投影
            @SuppressWarnings("method.invocation.invalid") // 抑制警告
            ColRef colRef = toColRef(p.left, p.right); // 将表达式转换为列引用
            columnBuilder.add(colRef); // 添加到构建器
          }
          columns = columnBuilder.build(); // 构建不可变列表
        }

        @Override @Nullable ColRef column(int offset) { // 重写column方法
          return columns.get(offset); // 返回对应偏移量的列引用
        }

        /** Converts an expression to a base or derived column reference.
         * The alias is optional, but if the derived column reference becomes
         * a dimension or measure, the alias will be used to choose a name. */
        // 私有方法：将表达式转换为基础列或派生列引用
        // 别名是可选的，但如果派生列引用成为维度或度量，别名将用于选择名称
        private @Nullable ColRef toColRef(RexNode e, String alias) { // 接收表达式和别名
          if (e instanceof RexInputRef) { // 如果是输入引用(基础列)
            return h.column(((RexInputRef) e).getIndex()); // 返回基础列引用
          }

          final ImmutableBitSet bits = RelOptUtil.InputFinder.bits(e); // 查找表达式中使用的所有输入字段
          final ImmutableList.Builder<TableRef> tableRefs = // 创建表引用列表构建器
              ImmutableList.builder();
          int c = 0; // offset within lattice of first column in a table
          // 注释：表中第一列在lattice中的偏移量
          for (TableRef tableRef : h.tableRefs) { // 遍历所有表引用
            final int prev = c; // 保存当前偏移量
            c += tableRef.table.t.getRowType().getFieldCount(); // 增加表的字段数量
            if (bits.intersects(ImmutableBitSet.range(prev, c))) { // 如果表达式使用了该表的字段
              tableRefs.add(tableRef); // 添加表引用
            }
          }
          final List<TableRef> tableRefList = tableRefs.build(); // 构建表引用列表
          switch (tableRefList.size()) { // 根据涉及的表数量分支
          case 1: // 如果只涉及一个表
            return new SingleTableDerivedColRef(tableRefList.get(0), e, alias); // 创建单表派生列引用
          default: // 如果涉及多个表
            return new DerivedColRef(tableRefList, e, alias); // 创建多表派生列引用
          }
        }
      };
    } else if (r instanceof Join) { // 如果是连接操作
      final Join join = (Join) r; // 强制类型转换
      final int leftCount = join.getLeft().getRowType().getFieldCount(); // 获取左表的字段数量
      final Frame left = frame(q, join.getLeft()); // 递归处理左输入
      final Frame right = frame(q, join.getRight()); // 递归处理右输入
      if (left == null || right == null) { // 如果任一输入为null
        return null; // 返回null
      }
      final ImmutableList.Builder<Hop> builder = ImmutableList.builder(); // 创建跳跃列表构建器
      builder.addAll(left.hops); // 添加左边的所有跳跃
      for (IntPair p : join.analyzeCondition().pairs()) { // 遍历连接条件中的所有列对
        final ColRef source = left.column(p.source); // 获取左边的列引用
        final ColRef target = right.column(p.target); // 获取右边的列引用
        assert source instanceof SingleTableColRef; // 断言源列是单表列引用
        assert target instanceof SingleTableColRef; // 断言目标列是单表列引用
        builder.add( // 添加跳跃
            new Hop((SingleTableColRef) source, // 使用源列
                (SingleTableColRef) target)); // 和目标列
      }
      builder.addAll(right.hops); // 添加右边的所有跳跃
      final int fieldCount = r.getRowType().getFieldCount(); // 获取输出字段数量
      return new Frame(fieldCount, builder.build(), // 创建新的帧
          CompositeList.of(left.measures, right.measures), // 合并左右两边的度量
          ImmutableList.of(left, right)) { // 左右两边的输入
        @Override @Nullable ColRef column(int offset) { // 重写column方法
          if (offset < leftCount) { // 如果偏移量在左边范围内
            return left.column(offset); // 返回左边的列引用
          } else { // 如果偏移量在右边范围内
            return right.column(offset - leftCount); // 返回右边的列引用(调整偏移量)
          }
        }
      };
    } else if (r instanceof TableScan) { // 如果是表扫描操作
      final TableScan scan = (TableScan) r; // 强制类型转换
      final TableRef tableRef = q.tableRef(scan); // 获取表引用
      final int fieldCount = r.getRowType().getFieldCount(); // 获取字段数量
      return new Frame(fieldCount, ImmutableList.of(), // 创建新的帧，没有跳跃
          ImmutableList.of(), ImmutableSet.of(tableRef)) { // 没有度量，只有表引用
        @Override ColRef column(int offset) { // 重写column方法
          if (offset >= scan.getTable().getRowType().getFieldCount()) { // 检查偏移量是否越界
            throw new IndexOutOfBoundsException("field " + offset // 抛出越界异常
                + " out of range in " + scan.getTable().getRowType()); // 显示错误信息
          }
          return new BaseColRef(tableRef, offset); // 返回基础列引用
        }
      };
    } else { // 其他情况
      return null; // 返回null，表示无法处理
    }
  }

  /** Holds state for a particular query graph. In particular table and step
   * references count from zero each query. */
  // 私有静态内部类：保存特定查询图的状态
  // 特别是表引用和步骤引用从零开始计数，每个查询独立
  private static class Query {
    final LatticeSpace space; // LatticeSpace对象，提供全局的lattice空间信息
    final Map<Integer, TableRef> tableRefs = new HashMap<>(); // 表引用映射，键是表扫描ID，值是TableRef对象
    int stepRefCount = 0; // 步骤引用计数器，从0开始
    final Set<String> usedNames = new HashSet<>(); // 已使用的名称集合，用于确保名称唯一

    Query(LatticeSpace space) { // 构造方法
      this.space = space; // 保存lattice空间引用
    }

    TableRef tableRef(TableScan scan) { // 方法：获取或创建表引用
      final TableRef r = tableRefs.get(scan.getId()); // 尝试从映射中获取现有的表引用
      if (r != null) { // 如果已存在
        return r; // 返回现有的表引用
      }
      final LatticeTable t = space.register(scan.getTable()); // 在lattice空间中注册表
      final TableRef r2 = new TableRef(t, tableRefs.size()); // 创建新的表引用，使用当前大小作为序号
      tableRefs.put(scan.getId(), r2); // 将表引用存入映射
      return r2; // 返回新的表引用
    }

    StepRef stepRef(TableRef source, TableRef target, List<IntPair> keys) { // 方法：获取或创建步骤引用
      keys = LatticeSpace.sortUnique(keys); // 对键进行排序并去重
      final Step h = Step.create(source.table, target.table, keys, space); // 创建步骤对象
      if (h.isBackwards(space.statisticProvider)) { // 如果步骤是反向的(基于统计信息)
        final List<IntPair> keys1 = LatticeSpace.swap(h.keys); // 交换键的顺序
        final Step h2 = space.addEdge(h.target(), h.source(), keys1); // 添加反向边
        return new StepRef(target, source, h2, stepRefCount++); // 创建反向的步骤引用
      } else { // 如果步骤是正向的
        final Step h2 = space.addEdge(h.source(), h.target(), h.keys); // 添加正向边
        return new StepRef(source, target, h2, stepRefCount++); // 创建正向的步骤引用
      }
    }
  }

  /** Information about the parent of fields from a relational expression. */
  // 抽象静态内部类：保存关系表达式字段父级信息
  abstract static class Frame {
    final List<Hop> hops; // 跳跃列表，表示表之间的连接条件
    final List<MutableMeasure> measures; // 度量列表，表示聚合函数
    final Set<TableRef> tableRefs; // 表引用集合，表示涉及的所有表
    final int columnCount; // 列数量，表示输出字段的个数

    Frame(int columnCount, List<Hop> hops, List<MutableMeasure> measures, // 构造方法
          Collection<TableRef> tableRefs) { // 接收列数、跳跃、度量和表引用
      this.hops = ImmutableList.copyOf(hops); // 创建不可变的跳跃列表副本
      this.measures = ImmutableList.copyOf(measures); // 创建不可变的度量列表副本
      this.tableRefs = ImmutableSet.copyOf(tableRefs); // 创建不可变的表引用集合副本
      this.columnCount = columnCount; // 保存列数量
    }

    Frame(int columnCount, List<Hop> hops, List<MutableMeasure> measures, // 构造方法重载
          List<Frame> inputs) { // 接收输入帧列表
      this(columnCount, hops, measures, collectTableRefs(inputs, hops)); // 调用主构造方法，收集表引用
    }

    abstract @Nullable ColRef column(int offset); // 抽象方法：根据偏移量获取列引用

    @Override public String toString() { // 重写toString方法
      return "Frame(" + hops + ")"; // 返回帧的字符串表示
    }

    static Set<TableRef> collectTableRefs(List<Frame> inputs, List<Hop> hops) { // 静态方法：收集所有表引用
      final LinkedHashSet<TableRef> set = new LinkedHashSet<>(); // 创建有序集合
      for (Hop hop : hops) { // 遍历所有跳跃
        set.add(hop.source.tableRef()); // 添加源表引用
        set.add(hop.target.tableRef()); // 添加目标表引用
      }
      for (Frame frame : inputs) { // 遍历所有输入帧
        set.addAll(frame.tableRefs); // 添加帧中的所有表引用
      }
      return set; // 返回表引用集合
    }
  }

  /** Use of a table within a query. A table can be used more than once. */
  // 私有静态内部类：表示查询中表的引用，一个表可以在查询中被多次使用
  private static class TableRef {
    final LatticeTable table; // LatticeTable对象，表示lattice中的表
    private final int ordinalInQuery; // 表在查询中的序号(从0开始)

    private TableRef(LatticeTable table, int ordinalInQuery) { // 私有构造方法
      this.table = requireNonNull(table, "table"); // 确保表不为null
      this.ordinalInQuery = ordinalInQuery; // 保存序号
    }

    @Override public int hashCode() { // 重写hashCode方法
      return ordinalInQuery; // 使用序号作为哈希码
    }

    @Override public boolean equals(@Nullable Object obj) { // 重写equals方法
      return this == obj // 如果是同一个对象
          || obj instanceof TableRef // 或者是TableRef类型
          && ordinalInQuery == ((TableRef) obj).ordinalInQuery; // 且序号相同
    }

    @Override public String toString() { // 重写toString方法
      return table + ":" + ordinalInQuery; // 返回"表名:序号"格式
    }
  }

  /** Use of a step within a query. A step can be used more than once. */
  // 私有静态内部类：表示查询中步骤的引用，一个步骤可以在查询中被多次使用
  private static class StepRef extends DefaultEdge { // 继承DefaultEdge，表示图的边
    final Step step; // Step对象，表示lattice中的步骤(连接路径)
    private final int ordinalInQuery; // 步骤在查询中的序号(从0开始)

    StepRef(TableRef source, TableRef target, Step step, int ordinalInQuery) { // 构造方法
      super(source, target); // 调用父类构造方法，设置源和目标顶点
      this.step = requireNonNull(step, "step"); // 确保步骤不为null
      this.ordinalInQuery = ordinalInQuery; // 保存序号
    }

    @Override public int hashCode() { // 重写hashCode方法
      return ordinalInQuery; // 使用序号作为哈希码
    }

    @Override public boolean equals(@Nullable Object obj) { // 重写equals方法
      return this == obj // 如果是同一个对象
          || obj instanceof StepRef // 或者是StepRef类型
          && ((StepRef) obj).ordinalInQuery == ordinalInQuery; // 且序号相同
    }

    @Override public String toString() { // 重写toString方法
      return "StepRef(" + source + ", " + target + "," + step.keyString + "):" // 返回详细字符串
          + ordinalInQuery; // 包含源、目标、键字符串和序号
    }

    TableRef source() { // 方法：获取源表引用
      return (TableRef) source; // 强制类型转换并返回
    }

    TableRef target() { // 方法：获取目标表引用
      return (TableRef) target; // 强制类型转换并返回
    }

    /** Creates {@link StepRef} instances. */
    // 私有静态内部类：创建StepRef实例的工厂类
    private static class Factory // 实现带属性的有向图边工厂接口
        implements AttributedDirectedGraph.AttributedEdgeFactory< // 泛型参数：顶点类型TableRef，边类型StepRef
            TableRef, StepRef> {
      @Override public StepRef createEdge(TableRef source, TableRef target) { // 创建边方法(无属性)
        throw new UnsupportedOperationException(); // 抛出不支持操作异常(必须使用带属性的版本)
      }

      @Override public StepRef createEdge(TableRef source, TableRef target, // 创建边方法(带属性)
            Object... attributes) { // 可变参数：属性列表
        final Step step = (Step) attributes[0]; // 第一个属性是Step对象
        final Integer ordinalInQuery = (Integer) attributes[1]; // 第二个属性是序号
        return new StepRef(source, target, step, ordinalInQuery); // 创建并返回StepRef实例
      }
    }
  }

  /** A hop is a join condition. One or more hops between the same source and
   * target combine to form a {@link Step}.
   *
   * <p>The tables are registered but the step is not. After we have gathered
   * several join conditions we may discover that the keys are composite: e.g.
   *
   * <blockquote>
   *   <pre>
   *     x.a = y.a
   *     AND x.b = z.b
   *     AND x.c = y.c
   *   </pre>
   * </blockquote>
   *
   * <p>has 3 semi-hops:
   *
   * <ul>
   *   <li>x.a = y.a
   *   <li>x.b = z.b
   *   <li>x.c = y.c
   * </ul>
   *
   * <p>which turn into 2 steps, the first of which is composite:
   *
   * <ul>
   *   <li>x.[a, c] = y.[a, c]
   *   <li>x.b = z.b
   * </ul>
   */
// 私有静态内部类：表示跳跃，即一个连接条件
// 相同源和目标之间的一或多个跳跃组合形成一个Step
// 表已注册但步骤未注册。在收集多个连接条件后，可能会发现键是复合的
// 例如：x.a = y.a AND x.b = z.b AND x.c = y.c 有3个半跳跃
// 它们转化为2个步骤，第一个是复合的：x.[a, c] = y.[a, c] 和 x.b = z.b
  private static class Hop {
    final SingleTableColRef source; // 源列引用(单表列引用)
    final SingleTableColRef target; // 目标列引用(单表列引用)

    private Hop(SingleTableColRef source, SingleTableColRef target) { // 私有构造方法
      this.source = source; // 保存源列引用
      this.target = target; // 保存目标列引用
    }
  }

  /** Column reference. */
// 私有抽象静态内部类：列引用的基类
  private abstract static class ColRef {
  }

  /** Column reference that is within a single table. */
// 私有接口：单表列引用，表示在单个表内的列引用
  private interface SingleTableColRef {
    TableRef tableRef(); // 方法：获取表引用

    int col(LatticeSpace space); // 方法：获取列索引
  }

  /** Reference to a base column. */
// 私有静态内部类：基础列引用，表示表的实际列
  private static class BaseColRef extends ColRef implements SingleTableColRef { // 继承ColRef并实现SingleTableColRef接口
    final TableRef t; // 表引用
    final int c; // 列索引

    private BaseColRef(TableRef t, int c) { // 私有构造方法
      this.t = t; // 保存表引用
      this.c = c; // 保存列索引
    }

    @Override public TableRef tableRef() { // 实现接口方法
      return t; // 返回表引用
    }

    @Override public int col(LatticeSpace space) { // 实现接口方法
      return c; // 返回列索引
    }
  }

  /** Reference to a derived column (that is, an expression). */
// 私有静态内部类：派生列引用，表示表达式列(即派生列)
  private static class DerivedColRef extends ColRef { // 继承ColRef
    final List<TableRef> tableRefs; // 涉及的表引用列表
    final RexNode e; // 表达式节点
    final String alias; // 别名

    DerivedColRef(Iterable<TableRef> tableRefs, RexNode e, String alias) { // 构造方法
      this.tableRefs = ImmutableList.copyOf(tableRefs); // 创建不可变的表引用列表
      this.e = e; // 保存表达式
      this.alias = alias; // 保存别名
    }

    List<String> tableAliases() { // 方法：获取表别名列表
      return Util.transform(tableRefs, tableRef -> tableRef.table.alias); // 转换为别名列表
    }
  }

  /** Variant of {@link DerivedColRef} where all referenced expressions are in
   * the same table. */
// 私有静态内部类：单表派生列引用，派生列引用的变体，其中所有引用的表达式都在同一个表中
  private static class SingleTableDerivedColRef extends DerivedColRef // 继承DerivedColRef
      implements SingleTableColRef { // 实现SingleTableColRef接口
    SingleTableDerivedColRef(TableRef tableRef, RexNode e, String alias) { // 构造方法
      super(ImmutableList.of(tableRef), e, alias); // 调用父类构造方法，只包含一个表
    }

    @Override public TableRef tableRef() { // 实现接口方法
      return tableRefs.get(0); // 返回第一个(也是唯一一个)表引用
    }

    @Override public int col(LatticeSpace space) { // 实现接口方法
      return space.registerExpression(tableRef().table, e); // 在lattice空间中注册表达式并返回索引
    }
  }

  /** An aggregate call. Becomes a measure in the final lattice. */

  // 私有静态内部类：可变度量，表示聚合函数调用，将成为最终lattice中的度量

    private static class MutableMeasure {

      final SqlAggFunction aggregate; // SQL聚合函数(如SUM, COUNT, AVG等)

      final boolean distinct; // 是否为去重聚合(如COUNT DISTINCT)

      final List<? extends @Nullable ColRef> arguments; // 参数列表，是列引用的列表(可能包含null)

      final @Nullable String name; // 度量名称(可能为null)

  

      private MutableMeasure(SqlAggFunction aggregate, boolean distinct, // 私有构造方法

          List<? extends @Nullable ColRef> arguments, @Nullable String name) { // 接收聚合函数、是否去重、参数列表和名称

        this.aggregate = aggregate; // 保存聚合函数

        this.arguments = arguments; // 保存参数列表

        this.distinct = distinct; // 保存是否去重标志

        this.name = name; // 保存名称

      }

  

    }

}
