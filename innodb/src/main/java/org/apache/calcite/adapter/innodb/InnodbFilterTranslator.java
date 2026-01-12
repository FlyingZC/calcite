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
package org.apache.calcite.adapter.innodb;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.DateString;
import org.apache.calcite.util.TimeString;
import org.apache.calcite.util.TimestampString;

import com.alibaba.innodb.java.reader.comparator.ComparisonOperator;
import com.alibaba.innodb.java.reader.schema.KeyMeta;
import com.alibaba.innodb.java.reader.schema.TableDef;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Translates {@link RexNode} expressions into {@link IndexCondition}
 * which might be pushed down to an InnoDB data source.
 * 将RexNode表达式翻译成IndexCondition，这些条件可能被下推到InnoDB数据源
 * 
 * 该类是Calcite适配器中负责将SQL过滤条件转换为InnoDB索引查询条件的核心翻译器
 * 主要功能包括：
 * 1. 解析SQL的WHERE条件（以RexNode形式表示）
 * 2. 根据表的主键和辅助索引结构，将过滤条件转换为可下推的索引查询条件
 * 3. 支持点查询（Point Query）和范围查询（Range Query）两种下推模式
 * 4. 遵循InnoDB的最左前缀索引规则，确保生成的查询条件能够有效利用索引
 * 
 * 核心设计原则：
 * - 只支持下推到单个索引的条件，不支持跨索引的复杂条件
 * - 优先选择主键索引，其次是辅助索引
 * - 根据索引条件的类型（点查询或范围查询）选择最优的查询策略
 * - 对于无法下推的条件，保留在remainder条件中，由上层处理
 * 
 * 使用场景：
 * 当Calcite优化器发现查询可以通过InnoDB索引下推来提高性能时，
 * 会使用此类将RexNode条件转换为InnoDB-java-reader可理解的索引查询条件
 */
class InnodbFilterTranslator {
  private final RexBuilder rexBuilder; // RexNode构建器，用于创建和操作RexNode表达式，是Calcite表达式树的核心工具类
  /** Field names per row type. */
  private final List<String> fieldNames; // 行类型中的字段名称列表，按字段在表中的顺序排列，用于将RexInputRef索引映射到实际字段名
  /** Primary key metadata. */
  private final KeyMeta pkMeta; // 主键元数据，包含主键的列名、列类型、索引长度等信息，用于生成主键查询条件
  /** Secondary key metadata. */
  private final List<KeyMeta> skMetaList; // 辅助索引元数据列表，每个元素包含一个辅助索引的列名、列类型、索引长度等信息，用于生成辅助索引查询条件
  /** If not null, force to use one specific index from hint. */
  private final @Nullable String forceIndexName; // 强制使用的索引名称，如果不为null，则只使用该指定的索引进行查询下推，通常来自SQL查询的索引提示（USE INDEX hint）

  InnodbFilterTranslator(RexBuilder rexBuilder, RelDataType rowType,
      TableDef tableDef, @Nullable String forceIndexName) { // 构造方法，初始化InnoDB过滤器翻译器，接收RexNode构建器、行类型、表定义和强制索引名称
    this.rexBuilder = rexBuilder; // 保存RexNode构建器，用于后续表达式操作
    this.fieldNames = InnodbRules.innodbFieldNames(rowType); // 从行类型中提取字段名称列表，用于后续字段名映射
    this.pkMeta = tableDef.getPrimaryKeyMeta(); // 从表定义中获取主键元数据，用于主键查询条件生成
    this.skMetaList = tableDef.getSecondaryKeyMetaList(); // 从表定义中获取辅助索引元数据列表，用于辅助索引查询条件生成
    this.forceIndexName = forceIndexName; // 保存强制索引名称，用于索引提示功能
  }

  /**
   * Produces the push down condition for the given
   * relational expression condition.
   * 为给定的关系表达式条件生成可下推的条件
   *
   * 该方法是翻译器的入口点，负责将SQL的WHERE条件（RexNode）转换为可下推到InnoDB的索引条件
   * 
   * 处理流程：
   * 1. 检查条件是否包含OR操作（析取），如果不支持则抛出异常
   * 2. 如果条件是简单的AND操作（合取），则调用translateAnd方法进行进一步处理
   * 3. 返回生成的索引条件对象
   *
   * 限制条件：
   * - 不支持OR操作（析取），因为InnoDB索引下推不支持复杂的OR条件
   * - 只支持AND操作（合取），可以将多个条件组合成一个索引查询
   *
   * @param condition condition to translate // 要翻译的关系表达式条件，通常是SQL的WHERE子句
   * @return push down condition // 返回可下推到InnoDB的索引条件，包含查询类型、索引名称、查询键等信息
   */
  public IndexCondition translateMatch(RexNode condition) { // 公共方法，翻译匹配条件，将RexNode转换为IndexCondition
    // does not support disjunctions // 不支持OR操作（析取），因为InnoDB索引下推不支持复杂的OR条件
    List<RexNode> disjunctions = RelOptUtil.disjunctions(condition); // 将条件按OR操作拆分成多个子条件列表
    if (disjunctions.size() == 1) { // 如果只有一个子条件，说明没有OR操作，可以进行翻译
      return translateAnd(disjunctions.get(0)); // 调用translateAnd方法处理AND操作（合取）
    } else { // 如果有多个子条件，说明包含OR操作，不支持翻译
      throw new AssertionError("cannot translate " + condition); // 抛出断言错误，说明无法翻译该条件
    }
  }

  /**
   * Translates a conjunctive predicate to a push down condition.
   * 将合取谓词（AND操作）转换为可下推的条件
   *
   * 该方法处理由AND操作连接的多个条件，尝试将其转换为可下推到InnoDB索引的查询条件
   * 
   * 核心逻辑：
   * 1. 展开SEARCH操作（如SEARCH(col, Sarg(...))）为具体的比较操作（如=, >, <等）
   * 2. 将AND连接的多个条件拆分成独立的条件列表
   * 3. 尝试使用主键索引进行下推
   * 4. 尝试使用每个辅助索引进行下推
   * 5. 从所有可能的下推条件中选择最优的一个（优先选择点查询，其次是范围查询）
   * 6. 如果有强制索引提示，则只使用该索引
   *
   * 选择策略：
   * - 优先选择可以下推的条件（canPushDown为true）
   * - 如果有强制索引，只使用该索引
   * - 按照查询类型优先级排序：点查询 > 范围查询
   * - 选择第一个（最高优先级）可下推的条件
   *
   * @param condition a conjunctive predicate // 由AND操作连接的合取谓词
   * @return push down condition // 返回最优的可下推条件，如果没有可下推的条件则返回EMPTY_CONDITION
   */
  private IndexCondition translateAnd(RexNode condition) { // 私有方法，翻译AND操作，将合取条件转换为索引条件
    // expand calls to SEARCH(..., Sarg()) to >, =, etc. // 展开SEARCH操作调用，将Sarg（Search Argument）转换为具体的比较操作
    final RexNode condition2 = // 存储展开后的条件
        RexUtil.expandSearch(rexBuilder, null, condition); // 使用RexUtil工具展开SEARCH操作，null表示没有额外的参数
    // decompose condition by AND, flatten row expression // 按AND操作分解条件，将嵌套的AND表达式扁平化
    List<RexNode> rexNodeList = RelOptUtil.conjunctions(condition2); // 获取所有由AND连接的独立条件列表

    List<IndexCondition> indexConditions = new ArrayList<>(); // 创建索引条件列表，用于存储所有可能的下推条件

    // try to push down filter by primary key // 尝试使用主键索引进行下推
    if (pkMeta != null) { // 如果存在主键元数据
      IndexCondition pkPushDownCond = findPushDownCondition(rexNodeList, pkMeta); // 尝试使用主键索引查找可下推的条件
      indexConditions.add(pkPushDownCond); // 将主键下推条件添加到列表中
    }

    // try to push down filter by secondary keys // 尝试使用辅助索引进行下推
    if (!skMetaList.isEmpty()) { // 如果存在辅助索引列表
      for (KeyMeta skMeta : skMetaList) { // 遍历每个辅助索引
        indexConditions.add(findPushDownCondition(rexNodeList, skMeta)); // 尝试使用该辅助索引查找可下推的条件，并添加到列表中
      }
    }

    // a collection of all possible push down conditions, see if it can
    // be pushed down, filter by forcing index name, then sort by comparator
    // 收集所有可能的下推条件，筛选出可以下推的条件，根据强制索引名称过滤，然后按比较器排序
    Stream<IndexCondition> pushDownConditions = indexConditions.stream() // 将索引条件列表转换为流，便于进行链式操作
        .filter(IndexCondition::canPushDown) // 过滤出可以下推的条件（canPushDown方法返回true的条件）
        .filter(this::nonForceIndexOrMatchForceIndexName) // 过滤出不使用强制索引或匹配强制索引名称的条件
        .sorted(new IndexConditionComparator()); // 按照IndexConditionComparator比较器排序，优先选择点查询

    return pushDownConditions.findFirst().orElse(IndexCondition.EMPTY_CONDITION); // 返回第一个（最优的）可下推条件，如果没有则返回空条件
  }

  /**
   * Tries to translate a conjunctive predicate to push down condition.
   * 尝试将合取谓词翻译为可下推的条件
   *
   * 该方法针对特定的索引（主键或辅助索引），尝试将过滤条件转换为可下推的索引查询条件
   * 
   * 核心逻辑：
   * 1. 分析条件列表，找出匹配索引列和特定操作符的字段表达式
   * 2. 检查是否满足最左前缀索引规则（必须从索引的最左列开始）
   * 3. 尝试处理为点查询（所有索引列都使用等号操作）
   * 4. 如果点查询不适用，尝试处理为范围查询（使用>=, >, <=, <等操作）
   * 5. 将可下推的条件从原始条件列表中移除，剩余条件保留在remainder中
   *
   * 最左前缀规则：
   * - 索引条件必须从索引的最左列开始
   * - 如果索引有多个列，必须连续使用，不能跳过中间的列
   * - 例如：索引(a,b,c)，条件可以是a=1、a=1 AND b=2、a=1 AND b=2 AND c=3，但不能是b=2或c=3
   *
   * @param rexNodeList original field expressions // 原始的字段表达式列表，包含所有AND连接的条件
   * @param keyMeta     index metadata // 索引元数据，包含索引列名、列类型等信息
   * @return push down condition // 返回可下推的索引条件，如果没有可下推的条件则返回EMPTY_CONDITION
   */
  private IndexCondition findPushDownCondition(List<RexNode> rexNodeList, KeyMeta keyMeta) { // 私有方法，查找可下推的条件
    // find field expressions matching index columns and specific operators // 查找匹配索引列和特定操作符的字段表达式
    List<InternalRexNode> matchedRexNodeList = analyzePrefixMatches(rexNodeList, keyMeta); // 分析前缀匹配，找出所有匹配索引列的条件

    // none of the conditions can be pushed down // 如果没有匹配的条件，则无法下推
    if (matchedRexNodeList.isEmpty()) { // 检查匹配的条件列表是否为空
      return IndexCondition.EMPTY_CONDITION; // 返回空条件，表示无法下推
    }

    // a collection that maps ordinal in index column list
    // to multiple field expressions
    // 创建一个映射，将索引列列表中的序号映射到多个字段表达式
    Multimap<Integer, InternalRexNode> keyOrdToNodesMap = HashMultimap.create(); // 创建多重映射，一个索引列可能对应多个条件
    for (InternalRexNode node : matchedRexNodeList) { // 遍历所有匹配的条件
      keyOrdToNodesMap.put(node.ordinalInKey, node); // 将条件按其在索引中的位置添加到映射中
    }

    // left-prefix index rule not match // 检查是否满足最左前缀索引规则
    Collection<InternalRexNode> leftMostKeyNodes = keyOrdToNodesMap.get(0); // 获取索引最左列（序号为0）的所有条件
    if (leftMostKeyNodes == null || leftMostKeyNodes.isEmpty()) { // 如果最左列没有条件，则不满足最左前缀规则
      return IndexCondition.EMPTY_CONDITION; // 返回空条件，表示无法下推
    }

    // create result which might have conditions to push down // 创建可能包含可下推条件的结果对象
    List<String> indexColumnNames = keyMeta.getKeyColumnNames(); // 获取索引列名称列表
    List<RexNode> pushDownRexNodeList = new ArrayList<>(); // 创建可下推条件列表，初始为空
    List<RexNode> remainderRexNodeList = new ArrayList<>(rexNodeList); // 创建剩余条件列表，初始包含所有条件
    IndexCondition condition = // 创建索引条件对象
        IndexCondition.create(fieldNames, keyMeta.getName(), indexColumnNames, // 使用字段名、索引名、索引列名创建条件
            pushDownRexNodeList, remainderRexNodeList); // 传入可下推条件列表和剩余条件列表

    // handle point query if possible // 如果可能，尝试处理为点查询
    condition = // 更新条件对象
        handlePointQuery(condition, keyMeta, leftMostKeyNodes, // 调用handlePointQuery方法处理点查询
            keyOrdToNodesMap, pushDownRexNodeList, remainderRexNodeList); // 传入映射、可下推条件和剩余条件列表
    if (condition.canPushDown()) { // 检查条件是否可以下推
      return condition; // 如果可以下推，直接返回该条件
    }

    // handle range query // 处理范围查询
    condition = // 更新条件对象
        handleRangeQuery(condition, keyMeta, leftMostKeyNodes, // 调用handleRangeQuery处理下界范围查询（>=, >）
            pushDownRexNodeList, remainderRexNodeList, ">=", ">"); // 指定操作符为>=和>
    condition = // 更新条件对象
        handleRangeQuery(condition, keyMeta, leftMostKeyNodes, // 调用handleRangeQuery处理上界范围查询（<=, <）
            pushDownRexNodeList, remainderRexNodeList, "<=", "<"); // 指定操作符为<=和<

    return condition; // 返回最终的条件对象
  }

  /**
   * Analyzes from the first to the subsequent field expression following the
   * left-prefix rule, this will based on a specific index
   * (<code>KeyMeta</code>), check the column and its corresponding operation,
   * see if it can be translated into a push down condition.
   * 按照最左前缀规则，从第一个字段表达式开始分析后续的字段表达式，基于特定的索引（KeyMeta），
   * 检查列及其对应的操作，看是否可以转换为可下推的条件
   *
   * 该方法分析每个字段表达式，判断其是否匹配索引的某一列，并且操作符是否支持下推
   * 
   * 分析流程：
   * 1. 遍历所有字段表达式
   * 2. 对每个表达式，尝试将其翻译为InternalRexNode（包含字段名、操作符、值等信息）
   * 3. 检查字段名是否在索引列中
   * 4. 检查操作符是否支持下推（=, >, >=, <, <=等）
   * 5. 过滤掉无法翻译的表达式
   * 6. 返回所有可以匹配的表达式列表
   *
   * 注意：
   * - 该方法不检查最左前缀规则，只检查单个条件是否匹配索引列
   * - 最左前缀规则的检查在findPushDownCondition方法中进行
   * - 返回的InternalRexNode包含了字段在索引中的位置（ordinalInKey）
   *
   * @param rexNodeList Field expressions // 字段表达式列表，包含所有AND连接的条件
   * @param keyMeta     Index metadata // 索引元数据，包含索引列名、列类型等信息
   * @return a collection of matched field expressions // 返回匹配的字段表达式列表，每个元素包含字段名、操作符、值等信息
   */
  private List<InternalRexNode> analyzePrefixMatches(List<RexNode> rexNodeList, KeyMeta keyMeta) { // 私有方法，分析前缀匹配
    return rexNodeList.stream() // 将表达式列表转换为流，便于进行链式操作
        .map(rexNode -> translateMatch2(rexNode, keyMeta)) // 对每个表达式调用translateMatch2方法，尝试翻译为InternalRexNode
        .filter(Optional::isPresent) // 过滤掉翻译失败的表达式（Optional为空）
        .map(Optional::get) // 从Optional中提取InternalRexNode对象
        .collect(Collectors.toList()); // 将结果收集为列表并返回
  }

  /**
   * Handles point query push down. The operation of the leftmost nodes
   * should be "=", then we try to find as many "=" operations as
   * possible, if "=" operation found on all index columns, then it is a
   * point query on key (both primary key or composite key), else it will
   * transform to a range query.
   * 处理点查询下推。最左节点的操作应该是"="，然后我们尝试找到尽可能多的"="操作，
   * 如果在所有索引列上都找到"="操作，那么这就是一个键上的点查询（主键或复合键），
   * 否则它将转换为范围查询
   *
   * 该方法尝试将条件处理为点查询或范围查询
   * 
   * 点查询条件：
   * - 最左列必须使用等号（=）操作
   * - 从最左列开始，尽可能多地找到连续的等号操作
   * - 如果所有索引列都使用等号，则为完全点查询
   * - 如果只有部分索引列使用等号，则转换为范围查询
   *
   * 范围查询转换：
   * - 当只有部分索引列使用等号时，将等号操作转换为范围查询
   * - 下界操作符为>=，上界操作符为<=
   * - 查询键值为等号操作的值
   *
   * 下推条件处理：
   * - 对于范围查询，只移除第一个节点（最左列的条件），因为InnoDB-java-reader只支持范围查询，不支持完整的索引条件下推
   * - 对于点查询，可以移除所有等号操作的条件，因为点查询可以直接定位到记录
   *
   * @param condition the index condition to update // 要更新的索引条件对象
   * @param keyMeta index metadata // 索引元数据
   * @param leftMostKeyNodes collection of nodes matching the leftmost index column // 匹配最左索引列的节点集合
   * @param keyOrdToNodesMap map from index column ordinal to matching nodes // 从索引列序号到匹配节点的映射
   * @param pushDownRexNodeList list of conditions to push down // 要下推的条件列表
   * @param remainderRexNodeList list of remaining conditions // 剩余条件列表
   * @return updated index condition // 返回更新后的索引条件
   */
  private static IndexCondition handlePointQuery(IndexCondition condition, // 私有静态方法，处理点查询下推
      KeyMeta keyMeta, Collection<InternalRexNode> leftMostKeyNodes, // 索引元数据和最左列节点集合
      Multimap<Integer, InternalRexNode> keyOrdToNodesMap, // 索引列序号到节点的映射
      List<RexNode> pushDownRexNodeList, // 可下推条件列表
      List<RexNode> remainderRexNodeList) { // 剩余条件列表
    Optional<InternalRexNode> leftMostEqOpNode = findFirstOp(leftMostKeyNodes, "="); // 在最左列节点中查找第一个等号操作
    if (leftMostEqOpNode.isPresent()) { // 如果找到等号操作
      InternalRexNode node = leftMostEqOpNode.get(); // 获取等号操作的节点

      List<InternalRexNode> matchNodes = Lists.newArrayList(node); // 创建匹配节点列表，初始包含最左列的等号节点
      findSubsequentMatches(matchNodes, keyMeta.getNumOfColumns(), keyOrdToNodesMap, "="); // 查找后续的等号操作节点
      List<Object> key = createKey(matchNodes); // 根据匹配节点创建查询键值
      pushDownRexNodeList.add(node.node); // 将最左列的节点添加到可下推条件列表
      remainderRexNodeList.remove(node.node); // 从剩余条件列表中移除该节点

      if (matchNodes.size() != keyMeta.getNumOfColumns()) { // 如果匹配的节点数不等于索引列数
        // "=" operation does not apply on all index columns // 等号操作没有应用到所有索引列
        return condition // 返回更新后的条件对象
            .withQueryType(QueryType.getRangeQuery(keyMeta.isSecondaryKey())) // 设置查询类型为范围查询
            .withRangeQueryLowerOp(ComparisonOperator.GTE) // 设置下界操作符为>=
            .withRangeQueryLowerKey(key) // 设置下界键值
            .withRangeQueryUpperOp(ComparisonOperator.LTE) // 设置上界操作符为<=
            .withRangeQueryUpperKey(key) // 设置上界键值
            .withPushDownConditions(pushDownRexNodeList) // 设置可下推条件列表
            .withRemainderConditions(remainderRexNodeList); // 设置剩余条件列表
      } else { // 如果匹配的节点数等于索引列数
        for (InternalRexNode n : matchNodes) { // 遍历所有匹配的节点
          pushDownRexNodeList.add(n.node); // 将节点添加到可下推条件列表
          remainderRexNodeList.remove(n.node); // 从剩余条件列表中移除该节点
        }
        return condition // 返回更新后的条件对象
            .withQueryType(QueryType.getPointQuery(keyMeta.isSecondaryKey())) // 设置查询类型为点查询
            .withPointQueryKey(key) // 设置点查询键值
            .withPushDownConditions(pushDownRexNodeList) // 设置可下推条件列表
            .withRemainderConditions(remainderRexNodeList); // 设置剩余条件列表
      }
    }
    return condition; // 如果没有找到等号操作，返回原始条件
  }

  /**
   * Handles range query push down. We try to find operation of GTE, GT, LT
   * or LTE in the left most key.
   * 处理范围查询下推。我们尝试在最左键中找到GTE、GT、LT或LTE操作
   *
   * 该方法处理范围查询的下推，只支持下界（>=, >）或上界（<=, <）操作
   * 
   * 限制条件：
   * - InnoDB-java-reader只支持下界和上界的范围查询，不支持完整的索引条件下推
   * - 只能下推最左列的条件，不能下推后续列的条件
   * - 这是因为InnoDB的B+树索引结构决定了范围查询只能从最左列开始
   *
   * 示例说明：
   * 假设有以下7行数据，(a,b)为辅助索引：
   *   a=100,b=200
   *   a=100,b=300
   *   a=100,b=500
   *   a=200,b=100
   *   a=200,b=400
   *   a=300,b=300
   *   a=500,b=600
   *
   * 如果条件是 a>200 AND b>300，正确的下界应该是 a=300,b=300
   * 但我们只能下推一个条件 a>200 作为下界条件
   * 不能下推 a>200 AND b>300，因为这会错误地包含 a=200,b=400 这条记录
   *
   * 下推条件处理：
   * - 如果条件可以下推，只移除第一个节点（最左列的条件）
   * - 因为InnoDB-java-reader只支持范围查询，不支持完整的索引条件下推
   *
   * @param condition the index condition to update // 要更新的索引条件对象
   * @param keyMeta index metadata // 索引元数据
   * @param leftMostKeyNodes collection of nodes matching the leftmost index column // 匹配最左索引列的节点集合
   * @param pushDownRexNodeList list of conditions to push down // 要下推的条件列表
   * @param remainderRexNodeList list of remaining conditions // 剩余条件列表
   * @param opList list of operators to look for (e.g., ">=", ">") // 要查找的操作符列表（如">=", ">"）
   * @return updated index condition // 返回更新后的索引条件
   */
  private static IndexCondition handleRangeQuery(IndexCondition condition, // 私有静态方法，处理范围查询下推
      KeyMeta keyMeta, Collection<InternalRexNode> leftMostKeyNodes, // 索引元数据和最左列节点集合
      List<RexNode> pushDownRexNodeList, // 可下推条件列表
      List<RexNode> remainderRexNodeList, // 剩余条件列表
      String... opList) { // 可变参数，指定要查找的操作符列表
    Optional<InternalRexNode> node = findFirstOp(leftMostKeyNodes, opList); // 在最左列节点中查找第一个匹配的操作符
    if (node.isPresent()) { // 如果找到匹配的操作符
      pushDownRexNodeList.add(node.get().node); // 将节点添加到可下推条件列表
      remainderRexNodeList.remove(node.get().node); // 从剩余条件列表中移除该节点
      List<Object> key = createKey(Lists.newArrayList(node.get())); // 根据节点创建查询键值
      ComparisonOperator op = ComparisonOperator.parse(node.get().op); // 将操作符字符串解析为ComparisonOperator枚举
      if (ComparisonOperator.isLowerBoundOp(opList)) { // 如果操作符是下界操作符（>=, >）
        return condition // 返回更新后的条件对象
            .withQueryType(QueryType.getRangeQuery(keyMeta.isSecondaryKey())) // 设置查询类型为范围查询
            .withRangeQueryLowerOp(op) // 设置下界操作符
            .withRangeQueryLowerKey(key) // 设置下界键值
            .withPushDownConditions(pushDownRexNodeList) // 设置可下推条件列表
            .withRemainderConditions(remainderRexNodeList); // 设置剩余条件列表
      } else if (ComparisonOperator.isUpperBoundOp(opList)) { // 如果操作符是上界操作符（<=, <）
        return condition // 返回更新后的条件对象
            .withQueryType(QueryType.getRangeQuery(keyMeta.isSecondaryKey())) // 设置查询类型为范围查询
            .withRangeQueryUpperOp(op) // 设置上界操作符
            .withRangeQueryUpperKey(key) // 设置上界键值
            .withPushDownConditions(pushDownRexNodeList) // 设置可下推条件列表
            .withRemainderConditions(remainderRexNodeList); // 设置剩余条件列表
      } else { // 如果操作符既不是下界也不是上界
        throw new AssertionError("comparison operation is invalid " + op); // 抛出断言错误，表示操作符无效
      }
    }
    return condition; // 如果没有找到匹配的操作符，返回原始条件
  }

  /**
   * Translates a binary relation.
   * 翻译二元关系表达式
   *
   * 该方法根据RexNode的类型（操作符），调用相应的翻译方法将其转换为InternalRexNode
   * 
   * 支持的操作符：
   * - EQUALS (=)：等号操作
   * - LESS_THAN (<)：小于操作
   * - LESS_THAN_OR_EQUAL (<=)：小于等于操作
   * - GREATER_THAN (>)：大于操作
   * - GREATER_THAN_OR_EQUAL (>=)：大于等于操作
   *
   * 操作符反转：
   * - 对于LESS_THAN和LESS_THAN_OR_EQUAL，传入反转操作符（>和>=）作为第二个参数
   * - 对于GREATER_THAN和GREATER_THAN_OR_EQUAL，传入反转操作符（<和<=）作为第二个参数
   * - 这是为了处理操作数顺序反转的情况（如col < 5和5 > col是等价的）
   *
   * @param node the RexNode to translate // 要翻译的RexNode节点
   * @param keyMeta index metadata // 索引元数据
   * @return Optional containing InternalRexNode if translation succeeds, empty otherwise // 如果翻译成功返回包含InternalRexNode的Optional，否则返回空Optional
   */
  private Optional<InternalRexNode> translateMatch2(RexNode node, KeyMeta keyMeta) { // 私有方法，翻译匹配的二元关系表达式
    switch (node.getKind()) { // 根据节点的类型（操作符）进行分支处理
    case EQUALS: // 如果是等号操作
      return translateBinary("=", "=", (RexCall) node, keyMeta); // 调用translateBinary方法，传入操作符"="和反转操作符"="
    case LESS_THAN: // 如果是小于操作
      return translateBinary("<", ">", (RexCall) node, keyMeta); // 调用translateBinary方法，传入操作符"<"和反转操作符">"
    case LESS_THAN_OR_EQUAL: // 如果是小于等于操作
      return translateBinary("<=", ">=", (RexCall) node, keyMeta); // 调用translateBinary方法，传入操作符"<="和反转操作符">="
    case GREATER_THAN: // 如果是大于操作
      return translateBinary(">", "<", (RexCall) node, keyMeta); // 调用translateBinary方法，传入操作符">"和反转操作符"<"
    case GREATER_THAN_OR_EQUAL: // 如果是大于等于操作
      return translateBinary(">=", "<=", (RexCall) node, keyMeta); // 调用translateBinary方法，传入操作符">="和反转操作符"<="
    default: // 如果不是支持的操作符
      return Optional.empty(); // 返回空Optional，表示翻译失败
    }
  }

  /**
   * Translates a call to a binary operator, reversing arguments if
   * necessary.
   * 翻译二元操作符调用，如果需要则反转参数
   *
   * 该方法尝试翻译二元操作符表达式，支持操作数顺序反转的情况
   * 
   * 处理逻辑：
   * 1. 首先尝试按原始顺序翻译（left op right）
   * 2. 如果原始顺序翻译失败，尝试按反转顺序翻译（right rop left）
   * 3. 例如：col < 5和5 > col是等价的，需要支持两种顺序
   *
   * 参数说明：
   * - op：原始操作符（如"<"）
   * - rop：反转操作符（如">"），用于处理操作数顺序反转的情况
   * 
   * @param op the original operator (e.g., "<") // 原始操作符（如"<"）
   * @param rop the reversed operator (e.g., ">") // 反转操作符（如">"）
   * @param call the RexCall representing the binary operation // 表示二元操作的RexCall对象
   * @param keyMeta index metadata // 索引元数据
   * @return Optional containing InternalRexNode if translation succeeds, empty otherwise // 如果翻译成功返回包含InternalRexNode的Optional，否则返回空Optional
   */
  private Optional<InternalRexNode> translateBinary(String op, String rop, // 私有方法，翻译二元操作符
      RexCall call, KeyMeta keyMeta) { // RexCall对象和索引元数据
    final RexNode left = call.operands.get(0); // 获取左操作数（第一个操作数）
    final RexNode right = call.operands.get(1); // 获取右操作数（第二个操作数）
    Optional<InternalRexNode> expression = // 创建Optional变量存储翻译结果
        translateBinary2(op, left, right, call, keyMeta); // 尝试按原始顺序翻译（left op right）
    if (expression.isPresent()) { // 如果原始顺序翻译成功
      return expression; // 返回翻译结果
    }
    expression = translateBinary2(rop, right, left, call, keyMeta); // 尝试按反转顺序翻译（right rop left）
    return expression; // 返回翻译结果
  }

  /**
   * Translates a call to a binary operator. Returns null on failure.
   * 翻译二元操作符调用。失败时返回null
   *
   * 该方法尝试将二元操作符表达式翻译为InternalRexNode
   * 
   * 处理逻辑：
   * 1. 检查右操作数是否为字面量（RexLiteral）
   * 2. 如果不是字面量，检查是否为CAST操作（用于处理TIMESTAMP类型）
   * 3. 检查左操作数的类型：
   *    - INPUT_REF：字段引用，获取字段名并检查是否在索引列中
   *    - CAST：类型转换，递归处理转换后的表达式
   *    - 其他：不支持，返回空Optional
   *
   * TIMESTAMP特殊处理：
   * - MySQL的TIMESTAMP类型被映射为TIMESTAMP_WITH_TIME_ZONE SQL类型
   * - 需要将CAST操作中的值转换为字面量
   *
   * @param op the operator string (e.g., "=", ">") // 操作符字符串（如"=", ">"）
   * @param left the left operand // 左操作数
   * @param right the right operand // 右操作数
   * @param originNode the original RexNode // 原始RexNode节点
   * @param keyMeta index metadata // 索引元数据
   * @return Optional containing InternalRexNode if translation succeeds, empty otherwise // 如果翻译成功返回包含InternalRexNode的Optional，否则返回空Optional
   */
  private Optional<InternalRexNode> translateBinary2(String op, RexNode left, // 私有方法，翻译二元操作符的具体实现
      RexNode right, RexNode originNode, KeyMeta keyMeta) { // 右操作数、原始节点和索引元数据
    RexLiteral rightLiteral; // 声明右字面量变量
    if (right.isA(SqlKind.LITERAL)) { // 如果右操作数是字面量
      rightLiteral = (RexLiteral) right; // 直接转换为RexLiteral
    } else { // 如果右操作数不是字面量
      // because MySQL's TIMESTAMP is mapped to TIMESTAMP_WITH_TIME_ZONE sql type,
      // we should cast the value to literal.
      // 因为MySQL的TIMESTAMP被映射为TIMESTAMP_WITH_TIME_ZONE SQL类型，我们需要将值转换为字面量
      if (right.isA(SqlKind.CAST) // 如果右操作数是CAST操作
          && isSqlTypeMatch((RexCall) right, SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE)) { // 并且CAST的目标类型是TIMESTAMP_WITH_LOCAL_TIME_ZONE
        rightLiteral = (RexLiteral) ((RexCall) right).operands.get(0); // 获取CAST操作的操作数（应该是字面量）
      } else { // 如果不是TIMESTAMP的CAST操作
        return Optional.empty(); // 返回空Optional，表示翻译失败
      }
    }
    switch (left.getKind()) { // 根据左操作数的类型进行分支处理
    case INPUT_REF: // 如果左操作数是字段引用
      final RexInputRef left1 = (RexInputRef) left; // 转换为RexInputRef对象
      String name = fieldNames.get(left1.getIndex()); // 根据字段索引获取字段名称
      // filter out field does not show in index column // 过滤掉不在索引列中的字段
      if (!keyMeta.getKeyColumnNames().contains(name)) { // 如果字段名不在索引列名列表中
        return Optional.empty(); // 返回空Optional，表示翻译失败
      }
      return translateOp2(op, name, rightLiteral, originNode, keyMeta); // 调用translateOp2方法，创建InternalRexNode
    case CAST: // 如果左操作数是类型转换
      return translateBinary2(op, ((RexCall) left).operands.get(0), right, // 递归处理，获取CAST操作的操作数
          originNode, keyMeta); // 继续翻译转换后的表达式
    default: // 如果左操作数不是INPUT_REF或CAST
      return Optional.empty(); // 返回空Optional，表示翻译失败
    }
  }

  /**
   * Combines a field name, operator, and literal to produce a predicate string.
   * 将字段名、操作符和字面量组合成谓词字符串
   *
   * 该方法创建InternalRexNode对象，包含字段名、操作符、值等信息
   * 
   * 处理逻辑：
   * 1. 将字面量转换为字符串值
   * 2. 创建InternalRexNode对象
   * 3. 设置节点属性：原始节点、在索引中的位置、字段名、操作符、右值
   * 4. 检查变长列的索引长度限制
   * 
   * 变长列索引长度限制：
   * - Innodb-java-reader有一个限制：前缀索引长度应该小于搜索值的字面量长度
   * - 例如：不能在EMAIL(3)索引上使用搜索值`someone@apache.org`，因为值长度大于3
   * - 这个限制是为了确保索引能够正确匹配
   *
   * @param op the operator string (e.g., "=", ">") // 操作符字符串（如"=", ">"）
   * @param name the field name // 字段名称
   * @param right the literal value // 字面量值
   * @param originNode the original RexNode // 原始RexNode节点
   * @param keyMeta index metadata // 索引元数据
   * @return Optional containing InternalRexNode if translation succeeds, empty otherwise // 如果翻译成功返回包含InternalRexNode的Optional，否则返回空Optional
   */
  private static Optional<InternalRexNode> translateOp2(String op, String name, // 私有静态方法，创建InternalRexNode对象
      RexLiteral right, RexNode originNode, KeyMeta keyMeta) { // 字面量、原始节点和索引元数据
    String value = literalValue(right); // 将字面量转换为字符串值
    InternalRexNode node = new InternalRexNode(); // 创建新的InternalRexNode对象
    node.node = originNode; // 设置原始RexNode节点
    node.ordinalInKey = keyMeta.getKeyColumnNames().indexOf(name); // 设置字段在索引列中的位置（从0开始）
    // For variable length column, Innodb-java-reader have a limitation,
    // left-prefix index length should be less than search value literal.
    // For example, we cannot leverage index of EMAIL(3) upon search value
    // `someone@apache.org`, because the value length is longer than 3.
    // 对于变长列，Innodb-java-reader有一个限制：前缀索引长度应该小于搜索值的字面量长度
    // 例如：不能在EMAIL(3)索引上使用搜索值`someone@apache.org`，因为值长度大于3
    if (keyMeta.getVarLen(name).isPresent() // 如果字段有变长限制
        && keyMeta.getVarLen(name).get() < value.length()) { // 并且限制长度小于值的长度
      return Optional.empty(); // 返回空Optional，表示翻译失败
    }
    node.fieldName = name; // 设置字段名称
    node.op = op; // 设置操作符
    node.right = value; // 设置右值
    return Optional.of(node); // 返回包含InternalRexNode的Optional
  }

  /**
   * Converts the value of a literal to a string.
   * 将字面量的值转换为字符串
   *
   * 该方法根据字面量的类型，将其转换为字符串表示
   * 
   * 支持的类型：
   * - DATE：日期类型，转换为DateString
   * - TIMESTAMP：时间戳类型，转换为TimestampString
   * - TIMESTAMP_WITH_LOCAL_TIME_ZONE：带时区的时间戳类型，转换为TimestampString
   * - TIME：时间类型，转换为TimeString
   * - TIME_WITH_LOCAL_TIME_ZONE：带时区的时间类型，转换为TimeString
   * - DECIMAL：十进制数，直接转换为字符串
   * - 其他类型：使用getValue2()方法获取值并转换为字符串
   *
   * 类型转换说明：
   * - 日期时间类型使用Calcite的专用类型（DateString、TimeString、TimestampString）
   * - 这些类型提供了更好的格式化和解析支持
   * - getValue2()是通用的值获取方法，适用于大多数基本类型
   *
   * @param literal Literal to translate // 要翻译的字面量
   * @return String representation of the literal // 字面量的字符串表示
   */
  private static String literalValue(RexLiteral literal) { // 私有静态方法，将字面量转换为字符串
    switch (literal.getTypeName()) { // 根据字面量的类型进行分支处理
    case DATE: // 如果是日期类型
      return String.valueOf(literal.getValueAs(DateString.class)); // 转换为DateString并转换为字符串
    case TIMESTAMP: // 如果是时间戳类型
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE: // 如果是带时区的时间戳类型
      return String.valueOf(literal.getValueAs(TimestampString.class)); // 转换为TimestampString并转换为字符串
    case TIME: // 如果是时间类型
    case TIME_WITH_LOCAL_TIME_ZONE: // 如果是带时区的时间类型
      return String.valueOf(literal.getValueAs(TimeString.class)); // 转换为TimeString并转换为字符串
    case DECIMAL: // 如果是十进制数类型
      return String.valueOf(literal.getValue()); // 直接获取值并转换为字符串
    default: // 其他类型
      return String.valueOf(literal.getValue2()); // 使用getValue2()方法获取值并转换为字符串
    }
  }

  private static void findSubsequentMatches(List<InternalRexNode> nodes, int numOfKeyColumns, // 私有静态方法，查找后续匹配的节点
          Multimap<Integer, InternalRexNode> keyOrdToNodesMap, String op) { // 索引列序号到节点的映射和操作符
    for (int i = nodes.size(); i < numOfKeyColumns; i++) { // 从当前匹配的节点数开始，遍历剩余的索引列
      Optional<InternalRexNode> eqOpNode = findFirstOp(keyOrdToNodesMap.get(i), op); // 在当前索引列中查找指定操作符的节点
      if (eqOpNode.isPresent()) { // 如果找到匹配的节点
        nodes.add(eqOpNode.get()); // 将节点添加到匹配节点列表中
      } else { // 如果没有找到匹配的节点
        break; // 跳出循环，因为必须连续匹配（最左前缀规则）
      }
    }
  }

  private static List<Object> createKey(List<InternalRexNode> nodes) { // 私有静态方法，根据匹配的节点创建查询键值
    return nodes.stream().map(n -> n.right).collect(Collectors.toList()); // 将节点流映射为右值流，并收集为列表
  }

  /**
   * Finds first node from field expression nodes which match specific
   * operations.
   * 从字段表达式节点中查找第一个匹配特定操作的节点
   *
   * 该方法在给定的节点集合中查找第一个操作符匹配指定操作符列表的节点
   * 
   * 查找逻辑：
   * 1. 如果节点集合为空，直接返回空Optional
   * 2. 遍历节点集合，对每个节点检查其操作符
   * 3. 如果节点的操作符在操作符列表中，返回该节点
   * 4. 如果遍历完所有节点都没有找到匹配的，返回空Optional
   *
   * 使用场景：
   * - 在handlePointQuery中查找等号操作的节点
   * - 在handleRangeQuery中查找范围操作符（>=, >, <=, <）的节点
   *
   * @param nodes collection of InternalRexNode to search // 要搜索的InternalRexNode集合
   * @param opList list of operators to match // 要匹配的操作符列表
   * @return Optional containing the first matching node, or empty if not found // 如果找到匹配的节点返回包含该节点的Optional，否则返回空Optional
   */
  private static Optional<InternalRexNode> findFirstOp(Collection<InternalRexNode> nodes, // 私有静态方法，查找第一个匹配的节点
      String... opList) { // 可变参数，指定要匹配的操作符列表
    if (nodes.isEmpty()) { // 如果节点集合为空
      return Optional.empty(); // 返回空Optional
    }
    for (InternalRexNode node : nodes) { // 遍历节点集合
      for (String op : opList) { // 遍历操作符列表
        if (op.equals(node.op)) { // 如果节点的操作符等于当前操作符
          return Optional.of(node); // 返回包含该节点的Optional
        }
      }
    }
    return Optional.empty(); // 遍历完所有节点都没有找到匹配的，返回空Optional
  }

  private boolean nonForceIndexOrMatchForceIndexName(IndexCondition indexCondition) { // 私有方法，检查是否不使用强制索引或匹配强制索引名称
    return Optional.ofNullable(forceIndexName) // 将forceIndexName包装为Optional，处理可能为null的情况
        .map(indexCondition::nameMatch). // 如果forceIndexName不为null，调用indexCondition的nameMatch方法检查索引名称是否匹配
        orElse(true); // 如果forceIndexName为null，返回true，表示不使用强制索引
  }

  /** Internal representation of a row expression. */
  private static class InternalRexNode { // 私有静态内部类，行表达式的内部表示
    /** Relation expression node. */
    RexNode node; // 关系表达式节点，原始的RexNode对象
    /** Field ordinal in indexes. */
    int ordinalInKey; // 字段在索引列中的位置（从0开始），用于判断是否满足最左前缀规则
    /** Field name. */
    String fieldName; // 字段名称，对应表中的列名
    /** Binary operation like =, >=, <=, > or <.*/
    String op; // 二元操作符，如=、>=、<=、>、<等
    /** Binary operation right literal value. */
    Object right; // 二元操作的右值（字面量），如数字、字符串等
  }

  /** Index condition comparator. */
  static class IndexConditionComparator implements Comparator<IndexCondition> { // 静态内部类，索引条件比较器，用于排序索引条件

    @Override public int compare(IndexCondition o1, IndexCondition o2) { // 实现compare方法，比较两个索引条件
      return Integer.compare(o1.getQueryType().priority(), o2.getQueryType().priority()); // 比较查询类型的优先级，返回比较结果
    }
  }

  private static boolean isSqlTypeMatch(RexCall rexCall, // 私有静态方法，检查RexCall的类型是否匹配指定的SQL类型
      SqlTypeName sqlTypeName) { // 要匹配的SQL类型名称
    return requireNonNull(rexCall, "rexCall").type.getSqlTypeName() // 获取RexCall的类型名称，并与指定的SQL类型名称比较
        == requireNonNull(sqlTypeName, "sqlTypeName"); // 使用requireNonNull确保参数不为null，然后比较类型名称
  }
}
