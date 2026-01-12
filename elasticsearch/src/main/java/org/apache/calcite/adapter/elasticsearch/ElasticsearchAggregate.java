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
// 声明包名，表示这个类属于org.apache.calcite.adapter.elasticsearch包，这是Calcite框架中Elasticsearch适配器的包
package org.apache.calcite.adapter.elasticsearch;

// 导入RelOptCluster类，表示关系表达式集群，包含优化器的共享信息
import org.apache.calcite.plan.RelOptCluster;
// 导入RelOptCost类，表示关系表达式的成本估计
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptPlanner类，表示关系表达式优化器
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.plan.RelTraitSet;
// 导入InvalidRelException类，表示无效的关系表达式异常
import org.apache.calcite.rel.InvalidRelException;
// 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rel.RelNode;
// 导入Aggregate类，表示聚合关系表达式，是本类的父类
import org.apache.calcite.rel.core.Aggregate;
// 导入AggregateCall类，表示聚合函数调用
import org.apache.calcite.rel.core.AggregateCall;
// 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入RelDataTypeField类，表示关系数据类型的字段
import org.apache.calcite.rel.type.RelDataTypeField;
// 导入SqlKind枚举，表示SQL操作的类型（如COUNT、SUM等）
import org.apache.calcite.sql.SqlKind;
// 导入SqlStdOperatorTable类，包含标准SQL操作符表
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
// 导入ImmutableBitSet类，表示不可变的位集合，用于标记字段位置
import org.apache.calcite.util.ImmutableBitSet;

// 导入Jackson的ObjectMapper类，用于JSON序列化和反序列化
import com.fasterxml.jackson.databind.ObjectMapper;
// 导入Jackson的ObjectNode类，表示JSON对象节点
import com.fasterxml.jackson.databind.node.ObjectNode;
// 导入Google Guava的ImmutableList类，表示不可变的列表
import com.google.common.collect.ImmutableList;

// 导入Checker Framework的@Nullable注解，用于标记可能为null的返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入Java标准库的ArrayList类，用于动态数组
import java.util.ArrayList;
// 导入Java标准库的EnumSet类，用于枚举类型的集合
import java.util.EnumSet;
// 导入Java标准库的List接口
import java.util.List;
// 导入Java标准库的Locale类，用于本地化
import java.util.Locale;
// 导入Java标准库的Set接口
import java.util.Set;

// 静态导入Objects.requireNonNull方法，用于非空检查
import static java.util.Objects.requireNonNull;

/**
 * Implementation of
 * {@link org.apache.calcite.rel.core.Aggregate} relational expression
 * for ElasticSearch.
 * 这是Elasticsearch适配器中聚合关系表达式的实现类，负责将SQL的聚合操作转换为Elasticsearch的聚合查询
 * 继承自Aggregate基类，实现了ElasticsearchRel接口，表示这是一个专门用于Elasticsearch的聚合节点
 * 主要功能包括：支持COUNT、MAX、MIN、AVG、SUM、ANY_VALUE等聚合函数，支持GROUP BY分组，将SQL聚合转换为Elasticsearch的aggregation语法
 */
public class ElasticsearchAggregate extends Aggregate implements ElasticsearchRel {

  // 定义支持的聚合函数类型集合，使用EnumSet存储以提高性能
  // 包含：COUNT（计数）、MAX（最大值）、MIN（最小值）、AVG（平均值）、SUM（求和）、ANY_VALUE（任意值）
  // 这些是Elasticsearch支持并能有效映射的聚合操作
  private static final Set<SqlKind> SUPPORTED_AGGREGATIONS =
      EnumSet.of(SqlKind.COUNT, SqlKind.MAX, SqlKind.MIN, SqlKind.AVG,
          SqlKind.SUM, SqlKind.ANY_VALUE);

  /** Creates an ElasticsearchAggregate. */
  // 构造方法：创建一个ElasticsearchAggregate实例
  // 参数列表：
  // - cluster: 关系表达式集群，包含优化器的共享信息
  // - traitSet: 关系表达式的特征集合，定义了物理属性（如调用约定）
  // - input: 输入的关系表达式节点（子节点）
  // - groupSet: 分组字段的位集合，标记哪些字段用于GROUP BY
  // - groupSets: 分组集合列表，支持多级分组（本类只支持单个分组集合）
  // - aggCalls: 聚合函数调用列表，包含所有要执行的聚合操作
  // 抛出InvalidRelException异常，当聚合操作不被支持时抛出
  ElasticsearchAggregate(RelOptCluster cluster,
      RelTraitSet traitSet,
      RelNode input,
      ImmutableBitSet groupSet,
      List<ImmutableBitSet> groupSets,
      List<AggregateCall> aggCalls) throws InvalidRelException  {
    // 调用父类Aggregate的构造方法，传入空列表作为indicator参数
    // indicator是旧版本的分组指示器，已废弃，这里传入空列表
    super(cluster, traitSet, ImmutableList.of(), input, groupSet, groupSets, aggCalls);

    // 检查当前节点的calling convention（调用约定）是否与输入节点的调用约定一致
    // 调用约定定义了如何执行这个关系表达式（如Elasticsearch、JDBC等）
    // 如果不一致，说明优化器出现了错误，抛出断言错误
    if (getConvention() != input.getConvention()) {
      // 格式化错误消息，显示两个调用约定不匹配
      String message =
          String.format(Locale.ROOT, "%s != %s", getConvention(),
              input.getConvention());
      // 抛出断言错误，因为这是一个不应该发生的内部错误
      throw new AssertionError(message);
    }

    // 断言检查：确保当前节点的调用约定与输入节点一致
    assert getConvention() == input.getConvention();
    // 断言检查：确保当前节点的调用约定是Elasticsearch的调用约定
    assert getConvention() == ElasticsearchRel.CONVENTION;
    // 断言检查：确保只有一个分组集合，不支持grouping sets（多级分组）
    // grouping sets是SQL标准特性，允许在一个查询中定义多个分组方式
    assert this.groupSets.size() == 1 : "Grouping sets not supported";

    // 遍历所有的聚合函数调用，验证每个聚合操作是否被支持
    for (AggregateCall aggCall : aggCalls) {
      // 检查是否是DISTINCT聚合且不是近似聚合
      // DISTINCT表示去重聚合（如COUNT(DISTINCT x)），近似聚合表示允许误差
      // Elasticsearch只支持近似去重聚合（使用cardinality函数），不支持精确去重
      if (aggCall.isDistinct() && !aggCall.isApproximate()) {
        // 构造错误消息，提示用户使用APPROX_COUNT_DISTINCT函数
        final String message = String.format(Locale.ROOT, "Only approximate distinct "
            + "aggregations are supported in Elastic (cardinality aggregation). Use %s function",
            SqlStdOperatorTable.APPROX_COUNT_DISTINCT.getName());
        // 抛出无效关系表达式异常
        throw new InvalidRelException(message);
      }

      // 获取聚合函数的类型（如COUNT、SUM等）
      final SqlKind kind = aggCall.getAggregation().getKind();
      // 检查聚合类型是否在支持的聚合集合中
      if (!SUPPORTED_AGGREGATIONS.contains(kind)) {
        // 构造错误消息，显示不支持的聚合类型并列出所有支持的类型
        final String message =
            String.format(Locale.ROOT,
                "Aggregation %s not supported (use one of %s)", kind,
                SUPPORTED_AGGREGATIONS);
        // 抛出无效关系表达式异常
        throw new InvalidRelException(message);
      }
    }

    // 检查分组类型，Elasticsearch只支持SIMPLE（简单）分组
    // Group枚举包括：SIMPLE（简单分组）、ROLLUP（上卷）、CUBE（立方体）等
    if (getGroupType() != Group.SIMPLE) {
      // 构造错误消息，提示只支持SIMPLE分组
      final String message = String.format(Locale.ROOT, "Only %s grouping is supported. "
              + "Yours is %s", Group.SIMPLE, getGroupType());
      // 抛出无效关系表达式异常
      throw new InvalidRelException(message);
    }
  }

  // 废弃的构造方法，保留用于向后兼容，将在2.0版本前移除
  // 参数说明：
  // - cluster: 关系表达式集群
  // - traitSet: 特征集合
  // - input: 输入节点
  // - indicator: 分组指示器（已废弃）
  // - groupSet: 分组字段集合
  // - groupSets: 分组集合列表
  // - aggCalls: 聚合函数调用列表
  @Deprecated // to be removed before 2.0
  ElasticsearchAggregate(RelOptCluster cluster,
      RelTraitSet traitSet,
      RelNode input,
      boolean indicator,
      ImmutableBitSet groupSet,
      List<ImmutableBitSet> groupSets,
      List<AggregateCall> aggCalls) throws InvalidRelException {
    // 调用新的构造方法，忽略indicator参数
    this(cluster, traitSet, input, groupSet, groupSets, aggCalls);
    // 检查indicator参数，确保其为false（因为新构造方法不支持indicator）
    checkIndicator(indicator);
  }

  // 重写copy方法，用于创建当前节点的副本
  // 参数说明：
  // - traitSet: 新的特征集合
  // - input: 新的输入节点
  // - groupSet: 新的分组字段集合
  // - groupSets: 新的分组集合列表
  // - aggCalls: 新的聚合函数调用列表
  // 返回值：新的ElasticsearchAggregate实例
  @Override public Aggregate copy(RelTraitSet traitSet, RelNode input,
      ImmutableBitSet groupSet, List<ImmutableBitSet> groupSets,
      List<AggregateCall> aggCalls) {
    try {
      // 创建并返回新的ElasticsearchAggregate实例
      // 使用当前节点的集群，传入新的参数
      return new ElasticsearchAggregate(getCluster(), traitSet, input,
          groupSet, groupSets, aggCalls);
    } catch (InvalidRelException e) {
      // 如果创建失败，抛出断言错误
      // 这不应该发生，因为原节点已经通过了验证
      throw new AssertionError(e);
    }
  }

  // 重写computeSelfCost方法，计算当前节点的执行成本
  // 参数说明：
  // - planner: 优化器，用于获取成本信息
  // - mq: 元数据查询，用于获取关系表达式的元数据
  // 返回值：计算后的成本对象，可能为null
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类的成本计算方法，获取基础成本
    // requireNonNull确保返回值不为null
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));
    // 将成本乘以0.1，表示Elasticsearch的聚合操作比内存聚合更高效
    // 这是因为Elasticsearch在分布式环境下并行执行聚合，性能更好
    // 较低的成本会使优化器更倾向于使用这个规则
    return cost.multiplyBy(0.1);
  }

  // 重写implement方法，实现Elasticsearch查询的生成
  // 这是核心方法，负责将SQL聚合操作转换为Elasticsearch的aggregation查询
  // 参数说明：
  // - implementor: 实现器，用于构建Elasticsearch查询和收集信息
  @Override public void implement(Implementor implementor) {
    // 访问子节点（输入节点），让它先生成自己的查询
    // visitChild方法会递归调用子节点的implement方法
    // 参数0表示这是第一个（也是唯一一个）子节点
    implementor.visitChild(0, getInput());
    // 获取输入节点的字段名称列表
    // getRowType()获取输入的行类型，fieldNames方法提取字段名称
    final List<String> inputFields = fieldNames(getInput().getRowType());
    // 遍历分组字段集合（groupSet中的每个位代表一个字段索引）
    for (int group : groupSet) {
      // 获取分组字段的名称
      final String name = inputFields.get(group);
      // 将分组字段添加到实现器的GROUP BY列表中
      // expressionItemMap存储了字段名到Elasticsearch字段名的映射
      // 如果映射中存在该字段，使用映射后的名称；否则使用原始名称
      implementor.addGroupBy(implementor.expressionItemMap.getOrDefault(name, name));
    }

    // 获取Elasticsearch表的JSON映射器，用于构建Elasticsearch查询的JSON结构
    final ObjectMapper mapper = implementor.elasticsearchTable.mapper;

    // 遍历所有的聚合函数调用
    for (AggregateCall aggCall : aggCalls) {
      // 创建聚合函数参数名称的列表
      final List<String> names = new ArrayList<>();
      // 遍历聚合函数的参数索引列表
      // getArgList()返回聚合函数参数在输入字段中的位置索引
      for (int i : aggCall.getArgList()) {
        // 根据索引获取字段名称，添加到列表中
        names.add(inputFields.get(i));
      }

      // 创建一个JSON对象节点，表示Elasticsearch的聚合配置
      final ObjectNode aggregation = mapper.createObjectNode();
      // 在聚合对象中创建一个子对象，子对象的键是Elasticsearch聚合类型
      // toElasticAggregate方法将SQL聚合类型转换为Elasticsearch聚合类型
      final ObjectNode field = aggregation.withObject("/" + toElasticAggregate(aggCall));

      // 确定聚合字段名称
      // 如果参数列表为空（如COUNT(*)），使用Elasticsearch的ID字段
      // 否则使用第一个参数的字段名
      final String name = names.isEmpty() ? ElasticsearchConstants.ID : names.get(0);
      // 设置聚合字段的名称
      // 使用expressionItemMap获取映射后的字段名，如果不存在则使用原始名称
      field.put("field", implementor.expressionItemMap.getOrDefault(name, name));
      // 如果聚合类型是ANY_VALUE（任意值），设置size为1
      // ANY_VALUE在Elasticsearch中使用terms聚合，size=1表示只返回一个值
      if (aggCall.getAggregation().getKind() == SqlKind.ANY_VALUE) {
        field.put("size", 1);
      }

      // 将聚合配置添加到实现器中
      // aggCall.getName()获取聚合结果的别名
      // aggregation.toString()将JSON对象转换为字符串
      implementor.addAggregation(aggCall.getName(), aggregation.toString());
    }
  }

  /**
   * Most of the aggregations can be retrieved with single
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-metrics-stats-aggregation.html">stats</a>
   * function. But currently only one-to-one mapping is supported between sql agg and elastic
   * aggregation.
   * 大多数聚合可以通过单个stats函数获取（Elasticsearch的stats聚合可以一次性返回count、sum、min、max、avg）
   * 但目前只支持SQL聚合和Elasticsearch聚合之间的一对一映射
   * 
   * 这个方法负责将SQL聚合函数类型转换为Elasticsearch聚合类型
   * 参数说明：
   * - call: 聚合函数调用对象，包含聚合函数的类型和参数信息
   * 返回值：对应的Elasticsearch聚合类型字符串
   */
  private static String toElasticAggregate(AggregateCall call) {
    // 获取聚合函数的类型（如COUNT、SUM等）
    final SqlKind kind = call.getAggregation().getKind();
    // 根据聚合类型进行匹配和转换
    switch (kind) {
    // COUNT聚合：计数函数
    case COUNT:
      // 如果是DISTINCT且近似聚合，使用cardinality（基数估计）
      // 否则使用value_count（值计数）
      // approx_count_distinct() vs count()
      return call.isDistinct() && call.isApproximate() ? "cardinality" : "value_count";
    // SUM聚合：求和函数
    case SUM:
      return "sum";
    // MIN聚合：最小值函数
    case MIN:
      return "min";
    // MAX聚合：最大值函数
    case MAX:
      return "max";
    // AVG聚合：平均值函数
    case AVG:
      return "avg";
    // ANY_VALUE聚合：任意值函数
    case ANY_VALUE:
      return "terms";
    // 未知聚合类型，抛出异常
    default:
      throw new IllegalArgumentException("Unknown aggregation kind " + kind + " for " + call);
    }
  }

  // 辅助方法：从关系数据类型中提取字段名称列表
  // 参数说明：
  // - relDataType: 关系数据类型对象，包含字段信息
  // 返回值：字段名称的列表
  private static List<String> fieldNames(RelDataType relDataType) {
    // 创建字段名称列表
    List<String> names = new ArrayList<>();

    // 遍历关系数据类型的所有字段
    // getFieldList()返回字段列表，每个字段包含名称和类型信息
    for (RelDataTypeField rdtf : relDataType.getFieldList()) {
      // 将字段名称添加到列表中
      names.add(rdtf.getName());
    }
    // 返回字段名称列表
    return names;
  }

}
