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

import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.Pair;

import com.alibaba.innodb.java.reader.comparator.ComparisonOperator;
import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;

import static java.util.Objects.requireNonNull;

/**
 * Index condition. // 索引条件类，用于封装 InnoDB 适配器中索引查询的各种条件和参数
 *
 * <p>Works in the following places: // 该类在以下场景中使用：
 *
 * <ul>
 * <li>In {@link InnodbFilterTranslator}, it is the index condition // 在 InnodbFilterTranslator 中，它是根据规划器规则通过 InnodbFilter 下推的索引条件
 * to push down according to {@link InnodbFilter} by planner rule. // 用于将过滤条件下推到存储引擎
 *
 * <li>In {@link InnodbTableScan}, it represents a full scan by a // 在 InnodbTableScan 中，它表示通过主键或辅助键进行的全表扫描
 * primary key or a secondary key. // 封装了扫描使用的索引信息和范围条件
 *
 * <li>In code generation, it indicates the storage engine which index // 在代码生成阶段，它指示存储引擎使用哪个索引以及相关条件
 * to use and the associated condition if present. // 为底层的 innodb-java-reader 库提供查询参数
 * </ul>
 */
public class IndexCondition {

  static final IndexCondition EMPTY_CONDITION = // 空索引条件常量，表示没有使用任何索引条件的默认状态
      create("", QueryType.PK_FULL_SCAN, // 创建一个空的索引条件对象，索引名为空，查询类型为主键全扫描
          null, ComparisonOperator.NOP, ComparisonOperator.NOP, // 范围查询的上下界操作符都为无操作
          ImmutableList.of(), ImmutableList.of()); // 范围查询的上下界键值都为空列表

  /** Field names per row type. */ // 按行类型的字段名称列表，用于记录表的所有字段名
  private final List<String> fieldNames; // 表的所有字段名称列表，用于在索引列名和字段索引之间建立映射关系
  private final String indexName; // 索引名称，标识使用的是主键索引还是某个辅助索引
  private final List<String> indexColumnNames; // 索引包含的列名列表，记录该索引由哪些字段组成
  private final RelCollation implicitCollation; // 隐式排序规则，表示通过该索引扫描后结果集的自然排序顺序
  private final List<RexNode> pushDownConditions; // 可下推到存储引擎的过滤条件列表，这些条件可以在索引扫描时直接应用
  private final List<RexNode> remainderConditions; // 剩余的过滤条件列表，这些条件在索引扫描后需要在内存中进一步过滤

  private final QueryType queryType; // 查询类型枚举，标识是点查询、范围查询还是全扫描等查询方式
  private final List<Object> pointQueryKey; // 点查询的键值列表，用于精确匹配索引列的值
  private final ComparisonOperator rangeQueryLowerOp; // 范围查询下界比较操作符，如 >=、> 等，用于定义范围查询的起始条件
  private final ComparisonOperator rangeQueryUpperOp; // 范围查询上界比较操作符，如 <=、< 等，用于定义范围查询的结束条件
  private final List<Object> rangeQueryLowerKey; // 范围查询下界键值列表，与 rangeQueryLowerOp 配合使用定义范围查询的起始值
  private final List<Object> rangeQueryUpperKey; // 范围查询上界键值列表，与 rangeQueryUpperOp 配合使用定义范围查询的结束值

  /** Constructor that assigns all fields. All other constructors call this. */ // 私有构造函数，初始化所有字段，其他构造函数都调用这个主构造函数
  private IndexCondition(
      List<String> fieldNames, // 表的所有字段名称列表
      String indexName, // 索引名称
      List<String> indexColumnNames, // 索引列名列表
      @Nullable RelCollation implicitCollation, // 隐式排序规则，可为空
      @Nullable List<RexNode> pushDownConditions, // 可下推的过滤条件列表，可为空
      @Nullable List<RexNode> remainderConditions, // 剩余的过滤条件列表，可为空
      QueryType queryType, // 查询类型
      @Nullable List<Object> pointQueryKey, // 点查询键值列表，可为空
      ComparisonOperator rangeQueryLowerOp, // 范围查询下界操作符
      ComparisonOperator rangeQueryUpperOp, // 范围查询上界操作符
      List<Object> rangeQueryLowerKey, // 范围查询下界键值列表
      List<Object> rangeQueryUpperKey) { // 范围查询上界键值列表
    this.fieldNames = fieldNames; // 初始化字段名称列表
    this.indexName = indexName; // 初始化索引名称
    this.indexColumnNames = indexColumnNames; // 初始化索引列名列表
    this.implicitCollation = // 初始化隐式排序规则，如果为空则根据字段名和索引列名推导
        implicitCollation != null ? implicitCollation // 如果提供了显式的排序规则则直接使用
            : deduceImplicitCollation(fieldNames, indexColumnNames); // 否则根据索引列的顺序推导隐式排序规则
    this.pushDownConditions = // 初始化可下推条件列表，确保不可变性
        pushDownConditions == null ? ImmutableList.of() // 如果为空则使用空列表
            : ImmutableList.copyOf(pushDownConditions); // 否则创建不可变副本
    this.remainderConditions = // 初始化剩余条件列表，确保不可变性
        remainderConditions == null ? ImmutableList.of() // 如果为空则使用空列表
            : ImmutableList.copyOf(remainderConditions); // 否则创建不可变副本
    this.queryType = queryType; // 初始化查询类型
    this.pointQueryKey = // 初始化点查询键值列表，确保不可变性
        pointQueryKey == null ? ImmutableList.of() // 如果为空则使用空列表
            : ImmutableList.copyOf(pointQueryKey); // 否则创建不可变副本
    this.rangeQueryLowerOp = requireNonNull(rangeQueryLowerOp, "rangeQueryLowerOp"); // 初始化范围查询下界操作符，要求非空
    this.rangeQueryUpperOp = requireNonNull(rangeQueryUpperOp, "rangeQueryUpperOp"); // 初始化范围查询上界操作符，要求非空
    this.rangeQueryLowerKey = ImmutableList.copyOf(rangeQueryLowerKey); // 初始化范围查询下界键值列表，创建不可变副本
    this.rangeQueryUpperKey = ImmutableList.copyOf(rangeQueryUpperKey); // 初始化范围查询上界键值列表，创建不可变副本
  }

  static IndexCondition create( // 静态工厂方法，创建基本的索引条件对象
      List<String> fieldNames, // 表的所有字段名称列表
      String indexName, // 索引名称
      List<String> indexColumnNames, // 索引列名列表
      QueryType queryType) { // 查询类型
    return new IndexCondition(fieldNames, indexName, indexColumnNames, null, // 调用主构造函数，排序规则为空
        null, null, queryType, null, ComparisonOperator.NOP, // 下推条件和剩余条件为空，点查询键为空，范围操作符为无操作
        ComparisonOperator.NOP, ImmutableList.of(), ImmutableList.of()); // 范围查询的上下界键值都为空列表
  }

  /**
   * Creates a new instance for {@link InnodbFilterTranslator} to build // 为 InnodbFilterTranslator 创建新实例
   * index condition which can be pushed down. // 用于构建可以下推到存储引擎的索引条件
   */
  static IndexCondition create( // 静态工厂方法，创建包含下推条件的索引条件对象
      List<String> fieldNames, // 表的所有字段名称列表
      String indexName, // 索引名称
      List<String> indexColumnNames, // 索引列名列表
      List<RexNode> pushDownConditions, // 可下推到存储引擎的过滤条件列表
      List<RexNode> remainderConditions) { // 需要在内存中进一步过滤的剩余条件列表
    return new IndexCondition(fieldNames, indexName, indexColumnNames, null, // 调用主构造函数，排序规则为空
        pushDownConditions, remainderConditions, QueryType.PK_FULL_SCAN, null, // 设置下推条件和剩余条件，查询类型为主键全扫描
        ComparisonOperator.NOP, ComparisonOperator.NOP, ImmutableList.of(), // 点查询键为空，范围操作符为无操作
        ImmutableList.of()); // 范围查询的上下界键值都为空列表
  }

  /**
   * Creates a new instance for code generation to build query parameters // 为代码生成创建新实例，用于构建查询参数
   * for underlying storage engine <code>Innodb-java-reader</code>. // 这些参数将传递给底层的 innodb-java-reader 存储引擎
   */
  public static IndexCondition create( // 公共静态工厂方法，用于代码生成阶段创建索引条件
      String indexName, // 索引名称
      QueryType queryType, // 查询类型
      @Nullable List<Object> pointQueryKey, // 点查询的键值列表，可为空
      ComparisonOperator rangeQueryLowerOp, // 范围查询下界比较操作符
      ComparisonOperator rangeQueryUpperOp, // 范围查询上界比较操作符
      List<Object> rangeQueryLowerKey, // 范围查询下界键值列表
      List<Object> rangeQueryUpperKey) { // 范围查询上界键值列表
    return new IndexCondition(ImmutableList.of(), indexName, ImmutableList.of(), // 调用主构造函数，字段名和索引列名为空（代码生成阶段不需要）
        null, null, null, queryType, pointQueryKey, rangeQueryLowerOp, // 排序规则和条件列表为空，设置查询类型和键值参数
        rangeQueryUpperOp, rangeQueryLowerKey, rangeQueryUpperKey); // 设置范围查询的操作符和键值
  }

  /** Returns whether there are any push down conditions. */ // 判断是否存在可下推到存储引擎的过滤条件
  boolean canPushDown() { // 返回布尔值，表示是否有条件可以下推
    return !pushDownConditions.isEmpty(); // 如果下推条件列表不为空则返回 true，否则返回 false
  }

  public RelCollation getImplicitCollation() { // 获取隐式排序规则
    return implicitCollation; // 返回通过索引列顺序推导出的排序规则
  }

  /**
   * Infers the implicit correlation from the index. // 从索引推导隐式排序规则
   *
   * @param indexColumnNames index column names // 索引列名列表参数
   * @return the collation of the filtered results // 返回过滤结果的排序规则
   */
  private static RelCollation deduceImplicitCollation(List<String> fieldNames, // 表的所有字段名称列表
      List<String> indexColumnNames) { // 索引列名列表
    requireNonNull(fieldNames, "field names must not be null"); // 检查字段名列表不能为空
    List<RelFieldCollation> keyCollations = new ArrayList<>(indexColumnNames.size()); // 创建字段排序规则列表，预分配索引列数量的大小
    for (String keyColumnName : indexColumnNames) { // 遍历索引中的每一列
      int fieldIndex = fieldNames.indexOf(keyColumnName); // 在字段名列表中查找索引列对应的字段索引位置
      keyCollations.add( // 将该字段的排序规则添加到列表中
          new RelFieldCollation(fieldIndex, RelFieldCollation.Direction.ASCENDING)); // 创建字段排序对象，默认为升序
    }
    return RelCollations.of(keyCollations); // 根据字段排序规则列表创建完整的排序规则对象并返回
  }

  public IndexCondition withFieldNames(List<String> fieldNames) { // 创建一个新的 IndexCondition 对象，使用新的字段名列表
    if (Objects.equals(fieldNames, this.fieldNames)) { // 如果新字段名列表与当前字段名列表相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的字段名列表
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持查询类型和范围查询参数不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public String getIndexName() { // 获取索引名称
    return indexName; // 返回当前索引条件的索引名称
  }

  public IndexCondition withIndexName(String indexName) { // 创建一个新的 IndexCondition 对象，使用新的索引名称
    if (Objects.equals(indexName, this.indexName)) { // 如果新索引名称与当前索引名称相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的索引名称
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持查询类型和范围查询参数不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public IndexCondition withIndexColumnNames(List<String> indexColumnNames) { // 创建一个新的 IndexCondition 对象，使用新的索引列名列表
    if (Objects.equals(indexColumnNames, this.indexColumnNames)) { // 如果新索引列名列表与当前索引列名列表相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的索引列名列表
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持查询类型和范围查询参数不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public List<RexNode> getPushDownConditions() { // 获取可下推到存储引擎的过滤条件列表
    return pushDownConditions; // 返回下推条件列表
  }

  public IndexCondition withPushDownConditions(List<RexNode> pushDownConditions) { // 创建一个新的 IndexCondition 对象，使用新的下推条件列表
    if (Objects.equals(pushDownConditions, this.pushDownConditions)) { // 如果新下推条件列表与当前下推条件列表相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的下推条件列表
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持查询类型和范围查询参数不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public List<RexNode> getRemainderConditions() { // 获取剩余的过滤条件列表，这些条件需要在内存中进一步过滤
    return remainderConditions; // 返回剩余条件列表
  }

  public IndexCondition withRemainderConditions(List<RexNode> remainderConditions) { // 创建一个新的 IndexCondition 对象，使用新的剩余条件列表
    if (Objects.equals(remainderConditions, this.remainderConditions)) { // 如果新剩余条件列表与当前剩余条件列表相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的剩余条件列表
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持查询类型和范围查询参数不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public QueryType getQueryType() { // 获取查询类型枚举值
    return queryType; // 返回查询类型，如点查询、范围查询或全扫描等
  }

  public IndexCondition withQueryType(QueryType queryType) { // 创建一个新的 IndexCondition 对象，使用新的查询类型
    if (queryType == this.queryType) { // 如果新查询类型与当前查询类型相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的查询类型
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 使用新的查询类型，保持范围查询参数不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public List<Object> getPointQueryKey() { // 获取点查询的键值列表
    return pointQueryKey; // 返回点查询的键值列表，用于精确匹配索引列
  }

  public IndexCondition withPointQueryKey(List<Object> pointQueryKey) { // 创建一个新的 IndexCondition 对象，使用新的点查询键值列表
    if (pointQueryKey == this.pointQueryKey) { // 如果新点查询键值列表与当前点查询键值列表相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的点查询键值列表
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 使用新的点查询键值，保持范围查询参数不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public ComparisonOperator getRangeQueryLowerOp() { // 获取范围查询下界比较操作符
    return rangeQueryLowerOp; // 返回范围查询下界操作符，如 >=、> 等
  }

  public IndexCondition withRangeQueryLowerOp(ComparisonOperator rangeQueryLowerOp) { // 创建一个新的 IndexCondition 对象，使用新的范围查询下界操作符
    if (rangeQueryLowerOp == this.rangeQueryLowerOp) { // 如果新下界操作符与当前下界操作符相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的下界操作符
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 使用新的下界操作符，保持上界操作符不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public ComparisonOperator getRangeQueryUpperOp() { // 获取范围查询上界比较操作符
    return rangeQueryUpperOp; // 返回范围查询上界操作符，如 <=、< 等
  }

  public IndexCondition withRangeQueryUpperOp(ComparisonOperator rangeQueryUpperOp) { // 创建一个新的 IndexCondition 对象，使用新的范围查询上界操作符
    if (rangeQueryUpperOp == this.rangeQueryUpperOp) { // 如果新上界操作符与当前上界操作符相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的上界操作符
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持下界操作符不变，使用新的上界操作符
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持范围查询键值不变
  }

  public List<Object> getRangeQueryLowerKey() { // 获取范围查询下界键值列表
    return rangeQueryLowerKey; // 返回范围查询下界键值列表，与下界操作符配合使用
  }

  public IndexCondition withRangeQueryLowerKey(List<Object> rangeQueryLowerKey) { // 创建一个新的 IndexCondition 对象，使用新的范围查询下界键值列表
    if (rangeQueryLowerKey == this.rangeQueryLowerKey) { // 如果新下界键值列表与当前下界键值列表相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的下界键值列表
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持范围查询操作符不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 使用新的下界键值，保持上界键值不变
  }

  public List<Object> getRangeQueryUpperKey() { // 获取范围查询上界键值列表
    return rangeQueryUpperKey; // 返回范围查询上界键值列表，与上界操作符配合使用
  }

  public IndexCondition withRangeQueryUpperKey(List<Object> rangeQueryUpperKey) { // 创建一个新的 IndexCondition 对象，使用新的范围查询上界键值列表
    if (rangeQueryUpperKey == this.rangeQueryUpperKey) { // 如果新上界键值列表与当前上界键值列表相同
      return this; // 直接返回当前对象，避免不必要的对象创建
    }
    return new IndexCondition(fieldNames, indexName, indexColumnNames, // 否则创建新对象，使用新的上界键值列表
        implicitCollation, pushDownConditions, remainderConditions, // 保持其他所有字段不变
        queryType, pointQueryKey, rangeQueryLowerOp, rangeQueryUpperOp, // 保持范围查询操作符不变
        rangeQueryLowerKey, rangeQueryUpperKey); // 保持下界键值不变，使用新的上界键值
  }

  public boolean nameMatch(String name) { // 检查给定的索引名称是否与当前索引条件匹配
    return name != null && name.equalsIgnoreCase(indexName); // 如果名称不为空且忽略大小写后相等则返回 true
  }

  @Override public String toString() { // 重写 toString 方法，返回索引条件的字符串表示
    final StringBuilder builder = new StringBuilder("("); // 创建字符串构建器，以左括号开始
    builder.append(queryType).append(", index=").append(indexName); // 添加查询类型和索引名称
    if (queryType == QueryType.PK_POINT_QUERY // 如果是主键点查询
        || queryType == QueryType.SK_POINT_QUERY) { // 或者是辅助键点查询
      checkState(pointQueryKey.size() == indexColumnNames.size()); // 检查点查询键值数量与索引列数量是否一致
      append(builder, indexColumnNames, pointQueryKey, "="); // 添加点查询的列名和键值，使用等号连接
    } else { // 如果是范围查询或全扫描
      if (!rangeQueryLowerKey.isEmpty()) { // 如果下界键值列表不为空
        append(builder, indexColumnNames, rangeQueryLowerKey, rangeQueryLowerOp.value()); // 添加下界条件，使用下界操作符连接
      }
      if (!rangeQueryUpperKey.isEmpty()) { // 如果上界键值列表不为空
        append(builder, indexColumnNames, rangeQueryUpperKey, rangeQueryUpperOp.value()); // 添加上界条件，使用上界操作符连接
      }
    }
    builder.append(")"); // 添加右括号结束
    return builder.toString(); // 返回构建好的字符串
  }

  private static void append(StringBuilder builder, List<String> keyColumnNames, // 私有辅助方法，将键值对追加到字符串构建器
      List<Object> key, String op) { // 参数：字符串构建器、键列名列表、键值列表、操作符
    builder.append(", "); // 添加逗号和空格作为分隔符
    for (Pair<String, Object> value : Pair.zip(keyColumnNames, key)) { // 将键列名和键值配对并遍历
      builder.append(value.getKey()); // 添加键列名
      builder.append(op); // 添加操作符（如 =、>=、<= 等）
      builder.append(value.getValue()); // 添加键值
      builder.append(","); // 添加逗号分隔符
    }
    builder.deleteCharAt(builder.length() - 1); // 删除最后一个多余的逗号
  }
}
