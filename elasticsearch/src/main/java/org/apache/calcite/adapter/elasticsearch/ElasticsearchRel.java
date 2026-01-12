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
package org.apache.calcite.adapter.elasticsearch; // 定义包名，该类位于Elasticsearch适配器包中

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系代数的调用约定
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，表示优化器中的表对象
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类，表示字段排序规则
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式节点
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.LinkedHashMap; // 导入LinkedHashMap类，用于保持插入顺序的哈希映射
import java.util.List; // 导入List接口，用于列表集合
import java.util.Map; // 导入Map接口，用于键值对映射

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Relational expression that uses Elasticsearch calling convention. // 使用Elasticsearch调用约定的关系表达式接口
 * // 该接口定义了所有Elasticsearch适配器的关系代数节点必须实现的方法，是Elasticsearch适配器的核心接口
 */
public interface ElasticsearchRel extends RelNode {
  void implement(Implementor implementor);

  /**
   * Calling convention for relational operations that occur in Elasticsearch.
   */
  Convention CONVENTION = new Convention.Impl("ELASTICSEARCH", ElasticsearchRel.class); // 创建Elasticsearch调用约定实例，名称为"ELASTICSEARCH"，类型为ElasticsearchRel.class

  /**
   * Callback for the implementation process that converts a tree of // 实现过程的回调类，用于将ElasticsearchRel节点树转换为Elasticsearch查询
   * {@link ElasticsearchRel} nodes into an Elasticsearch query. // 该类作为访问者模式的实现器，负责收集和构建Elasticsearch查询的各个部分
   */
  class Implementor {

    final List<String> list = new ArrayList<>();

    /**
     * Sorting clauses. // 排序子句列表，存储字段名称和排序方向的键值对
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-sort.html">Sort</a>
     */
    final List<Map.Entry<String, RelFieldCollation.Direction>> sort = new ArrayList<>();

    /**
     * Sorting missing values. // 空值排序规则列表，处理字段值为null时的排序行为
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/sort-search-results.html#_missing_values">Missing Values</a>
     */
    final List<Map.Entry<String, RelFieldCollation.NullDirection>> nullsSort = new ArrayList<>();

    /**
     * Elastic aggregation ({@code MIN / MAX / COUNT} etc.) statements (functions). // Elasticsearch聚合语句列表，包含MIN/MAX/COUNT等聚合函数
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations.html">aggregations</a>
     */
    final List<Map.Entry<String, String>> aggregations = new ArrayList<>();

    /**
     * Allows bucketing documents together. Similar to {@code select ... from table group by field1} // 分组字段列表，将文档按照指定字段进行分组，类似SQL中的GROUP BY子句
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/6.3/search-aggregations-bucket.html">Bucket Aggregrations</a>
     */
    final List<String> groupBy = new ArrayList<>();

    /**
     * Keeps mapping between calcite expression identifier (like {@code EXPR$0}) and // 表达式标识映射表，保持Calcite表达式标识符与原始ITEM调用之间的映射关系
     * original item call like {@code _MAP['foo.bar']} ({@code foo.bar} really). // 例如：将EXPR$0映射到foo.bar，避免查询转换过程中丢失原始字段信息
     * This information otherwise might be lost during query translation. // 这是为了解决在查询转换过程中可能丢失原始字段信息的问题
     *
     * @see SqlStdOperatorTable#ITEM
     */
    final Map<String, String> expressionItemMap = new LinkedHashMap<>();

    /**
     * Starting index (default {@code 0}). Equivalent to {@code start} in ES query. // 偏移量，指定查询结果的起始位置，默认为0，对应ES查询中的from参数
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html">From/Size</a>
     */
    Long offset;

    /**
     * Number of records to return. Equivalent to {@code size} in ES query. // 获取记录数，指定返回结果的最大数量，对应ES查询中的size参数
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-from-size.html">From/Size</a>
     */
    Long fetch; // fetch成员变量：Long类型，存储返回结果的最大记录数，用于限制结果集大小

    RelOptTable table; // table成员变量：RelOptTable类型，表示优化器中的表对象，包含表的元数据信息
    ElasticsearchTable elasticsearchTable; // elasticsearchTable成员变量：ElasticsearchTable类型，表示Elasticsearch表的实现，包含ES索引和映射信息

    void add(String findOp) { // add方法：向查询条件列表中添加一个查询条件，findOp参数是查询条件字符串
      list.add(findOp); // 将查询条件添加到list列表中，用于构建Elasticsearch的bool查询
    }

    void addGroupBy(String field) { // addGroupBy方法：添加分组字段，field参数是要分组的字段名称
      requireNonNull(field, "field"); // 校验field参数不能为null，否则抛出NullPointerException
      groupBy.add(field); // 将字段添加到groupBy列表中，用于GROUP BY操作
    }

    void addSort(String field, RelFieldCollation.Direction direction) { // addSort方法：添加排序规则，field是字段名，direction是排序方向
      requireNonNull(field, "field"); // 校验field参数不能为null
      sort.add(Pair.of(field, direction)); // 创建字段名和排序方向的键值对，添加到sort列表中
    }

    void addNullsSort(String field, RelFieldCollation.NullDirection nullDirection) { // addNullsSort方法：添加空值排序规则，field是字段名，nullDirection是空值排序方向
      requireNonNull(field, "field"); // 校验field参数不能为null
      nullsSort.add(new Pair<>(field, nullDirection)); // 创建字段名和空值排序方向的键值对，添加到nullsSort列表中
    }

    void addAggregation(String field, String expression) { // addAggregation方法：添加聚合函数，field是聚合字段，expression是聚合表达式
      requireNonNull(field, "field"); // 校验field参数不能为null
      requireNonNull(expression, "expression"); // 校验expression参数不能为null
      aggregations.add(Pair.of(field, expression)); // 创建字段名和聚合表达式的键值对，添加到aggregations列表中
    }

    void addExpressionItemMapping(String expressionId, String item) { // addExpressionItemMapping方法：添加表达式到字段的映射，expressionId是表达式ID，item是原始字段
      requireNonNull(expressionId, "expressionId"); // 校验expressionId参数不能为null
      requireNonNull(item, "item"); // 校验item参数不能为null
      expressionItemMap.put(expressionId, item); // 将表达式ID和原始字段的映射存入expressionItemMap
    }

    void offset(long offset) { // offset方法：设置查询结果的起始索引，offset参数是起始位置
      this.offset = offset; // 将offset值赋给成员变量offset，用于分页查询
    }

    void fetch(long fetch) { // fetch方法：设置返回结果的最大记录数，fetch参数是记录数
      this.fetch = fetch; // 将fetch值赋给成员变量fetch，用于限制结果集大小
    }

    void visitChild(int ordinal, RelNode input) { // visitChild方法：访问子节点，ordinal是子节点序号，input是子节点关系表达式
      assert ordinal == 0; // 断言ordinal必须为0，因为ElasticsearchRel通常只有一个子节点
      ((ElasticsearchRel) input).implement(this); // 将子节点转换为ElasticsearchRel类型，调用其implement方法，传入当前implementor对象
    }

  }
}
