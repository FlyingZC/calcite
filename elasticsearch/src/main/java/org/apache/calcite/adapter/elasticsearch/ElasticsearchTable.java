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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，该类属于Elasticsearch适配器包

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表基类，提供LINQ查询支持
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，用于表示可迭代的查询结果
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历查询结果
import org.apache.calcite.linq4j.Linq4j; // 导入LINQ4j工具类，提供LINQ操作方法
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于执行LINQ查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可被查询的数据源
import org.apache.calcite.linq4j.function.Function1; // 导入单参数函数接口，用于数据转换
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式优化集群，包含RexBuilder等优化工具
import org.apache.calcite.plan.RelOptTable; // 导入优化表接口，表示优化过程中的表
import org.apache.calcite.rel.RelFieldCollation; // 导入字段排序接口，定义字段的排序方向
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口，表示关系代数操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表或字段的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂，用于创建数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入扩展Schema接口，支持添加表和函数
import org.apache.calcite.schema.TranslatableTable; // 导入可转换表接口，支持转换为关系表达式
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类，提供表查询实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL数据类型

import com.fasterxml.jackson.databind.JsonNode; // 导入Jackson JSON节点接口，表示JSON数据结构
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson对象映射器，用于JSON序列化和反序列化
import com.fasterxml.jackson.databind.node.ArrayNode; // 导入Jackson数组节点，表示JSON数组
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson对象节点，表示JSON对象
import com.google.common.collect.ImmutableMap; // 导入Google不可变Map，提供线程安全的只读Map

import java.io.IOException; // 导入IO异常类，处理输入输出错误
import java.io.UncheckedIOException; // 导入未检查IO异常类，包装IO异常为运行时异常
import java.util.ArrayList; // 导入动态数组列表，提供可变大小的数组实现
import java.util.Collections; // 导入集合工具类，提供不可变集合和排序等操作
import java.util.HashMap; // 导入哈希映射，提供键值对存储
import java.util.LinkedHashMap; // 导入链式哈希映射，保持插入顺序
import java.util.LinkedHashSet; // 导入链式哈希集合，保持插入顺序且去重
import java.util.List; // 导入列表接口，表示有序集合
import java.util.Map; // 导入映射接口，表示键值对集合
import java.util.Set; // 导入集合接口，表示无序不重复集合
import java.util.function.Consumer; // 导入消费者函数接口，接受单个参数无返回值
import java.util.function.Predicate; // 导入谓词函数接口，用于过滤操作
import java.util.stream.Collectors; // 导入收集器工具类，用于Stream流的聚合操作

import static java.util.Objects.requireNonNull; // 导入对象工具类，静态导入requireNonNull方法用于非空检查

/**
 * Table based on an Elasticsearch index.
 * 基于Elasticsearch索引的表实现，作为Calcite与Elasticsearch之间的桥梁
 * 该类实现了TranslatableTable接口，可以将表转换为关系表达式供查询优化器使用
 * 同时继承AbstractQueryableTable，支持LINQ查询接口，允许直接在Java代码中查询
 */
public class ElasticsearchTable extends AbstractQueryableTable implements TranslatableTable { // 定义Elasticsearch表类，继承抽象可查询表并实现可转换表接口

  /**
   * Used for constructing (possibly nested) Elastic aggregation nodes.
   * 用于构建（可能嵌套的）Elasticsearch聚合节点的常量
   * 在Elasticsearch查询中，聚合结果存储在"aggregations"字段下
   */
  private static final String AGGREGATIONS = "aggregations"; // 定义聚合字段名称常量，用于构建Elasticsearch聚合查询

  private final ElasticsearchVersion version; // 存储Elasticsearch版本信息，用于处理不同版本的兼容性问题
  private final String indexName; // 存储Elasticsearch索引名称，标识要查询的索引
  final ObjectMapper mapper; // Jackson对象映射器，用于JSON数据的序列化和反序列化，包访问权限供内部类使用
  final ElasticsearchTransport transport; // Elasticsearch传输层对象，负责与Elasticsearch集群通信，包访问权限供内部类使用

  /**
   * Creates an ElasticsearchTable.
   * 创建Elasticsearch表实例的构造方法
   * 通过传输层对象初始化表的基本配置和连接信息
   *
   * @param transport Elasticsearch传输层对象，包含索引名称、版本信息和JSON映射器
   */
  ElasticsearchTable(ElasticsearchTransport transport) { // 构造方法，接收传输层对象作为参数
    super(Object[].class); // 调用父类构造方法，指定行类型为Object数组，表示每行数据是对象数组
    this.transport = requireNonNull(transport, "transport"); // 设置传输层对象，使用requireNonNull进行非空校验
    this.version = transport.version; // 从传输层获取Elasticsearch版本信息，用于处理版本兼容性
    this.indexName = transport.indexName; // 从传输层获取索引名称，标识要操作的Elasticsearch索引
    this.mapper = transport.mapper(); // 从传输层获取JSON映射器，用于处理JSON数据的读写
  }

  /**
   * In ES 5.x scripted fields start with {@code params._source.foo} while in ES2.x
   * {@code _source.foo}. Helper method to build correct query based on runtime version of elastic.
   * Used to keep backwards compatibility with ES2.
   * 在ES 5.x中，脚本字段以params._source.foo开头，而在ES2.x中是_source.foo
   * 这是一个辅助方法，根据Elasticsearch运行时版本构建正确的查询
   * 用于保持与ES2的向后兼容性
   *
   * @see <a href="https://github.com/elastic/elasticsearch/issues/20068">_source variable</a>
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/master/modules-scripting-fields.html">Scripted Fields</a>
   * @return string to be used for scripted fields
   * @return 用于脚本字段的前缀字符串，根据版本返回不同的前缀
   */
  String scriptedFieldPrefix() { // 根据Elasticsearch版本获取脚本字段的前缀字符串
    // ES2 vs ES5 scripted field difference
    // ES2和ES5脚本字段语法的差异
    return version == ElasticsearchVersion.ES2 // 判断当前版本是否为ES2
        ? ElasticsearchConstants.SOURCE_GROOVY // 如果是ES2，返回Groovy语法的_source前缀
        : ElasticsearchConstants.SOURCE_PAINLESS; // 如果是ES5+，返回Painless语法的params._source前缀
  }

  /**
   * Executes a "find" operation on the underlying index.
   * 在底层索引上执行"查找"操作，构建并执行Elasticsearch查询
   * 支持条件过滤、字段投影、排序、分页等功能
   * 如果包含聚合或分组操作，则委托给aggregate方法处理
   *
   * @param ops List of operations represented as Json strings.
   * @param ops 操作列表，表示为JSON字符串，包含查询条件等
   * @param fields List of fields to project; or null to return map
   * @param fields 要投影的字段列表；如果为null则返回完整Map
   * @param sort list of fields to sort and their direction (asc/desc)
   * @param sort 排序字段列表及其方向（升序/降序）
   * @param nullsSort List of fields to sort null value and their direction (asc/desc)
   * @param nullsSort 空值排序字段列表及其方向（排在前面/后面）
   * @param aggregations aggregation functions
   * @param aggregations 聚合函数列表
   * @return Enumerator of results
   * @return 结果的可枚举对象，包含查询返回的数据
   */
  private Enumerable<Object> find(List<String> ops, // 私有方法，执行查找操作，接收操作列表
      List<Map.Entry<String, Class>> fields, // 要投影的字段列表，键为字段名，值为字段类型
      List<Map.Entry<String, RelFieldCollation.Direction>> sort, // 排序字段列表，键为字段名，值为排序方向
      List<Map.Entry<String, RelFieldCollation.NullDirection>> nullsSort, // 空值排序字段列表，键为字段名，值为空值排序方向
      List<String> groupBy, // 分组字段列表
      List<Map.Entry<String, String>> aggregations, // 聚合函数列表，键为别名，值为聚合表达式
      Map<String, String> mappings, // 字段映射关系，用于类型转换
      Long offset, Long fetch) throws IOException { // 偏移量和获取数量，用于分页，可能抛出IO异常

    if (!aggregations.isEmpty() || !groupBy.isEmpty()) { // 检查是否有聚合或分组操作
      // process aggregations separately
      // 如果有聚合或分组，单独处理聚合操作
      return aggregate(ops, fields, sort, groupBy, aggregations, mappings, offset, fetch); // 调用aggregate方法处理聚合查询
    }

    final ObjectNode query = mapper.createObjectNode(); // 创建根查询节点，用于构建Elasticsearch查询JSON
    // manually parse from previously concatenated string
    // 手动从之前拼接的字符串中解析JSON
    for (String op : ops) { // 遍历所有操作字符串
      query.setAll((ObjectNode) mapper.readTree(op)); // 将每个操作解析为JSON节点并合并到查询对象中
    }

    if (!sort.isEmpty()) { // 检查是否有排序字段
      ArrayNode sortNode = query.withArray("sort"); // 在查询对象中创建或获取sort数组节点
      for (int i = 0; i < sort.size(); i++) { // 遍历所有排序字段
        Map.Entry<String, RelFieldCollation.Direction> sortField = sort.get(i); // 获取当前排序字段及其方向
        ObjectNode fieldSortProp = mapper.createObjectNode(); // 创建字段排序属性对象
        String nullsDirection = nullsSort.get(i).getValue() == RelFieldCollation.NullDirection.FIRST // 检查空值排序方向
            ? "_first" : "_last"; // 如果空值排在前面则使用_first，否则使用_last
        fieldSortProp.put("missing", nullsDirection) // 设置missing属性，控制缺失值的排序位置
            .put("order", sortField.getValue().isDescending() ? "desc" : "asc"); // 设置order属性，降序为desc，升序为asc
        sortNode.add(mapper.createObjectNode().set(sortField.getKey(), fieldSortProp)); // 将排序字段和属性添加到sort数组中
      }
    }

    if (offset != null) { // 检查是否设置了偏移量
      query.put("from", offset); // 设置from参数，指定查询起始位置
    }

    if (fetch != null) { // 检查是否设置了获取数量
      query.put("size", fetch); // 设置size参数，指定返回的文档数量
    }

    final Function1<ElasticsearchJson.SearchHit, Object> getter = // 创建结果转换函数，将SearchHit转换为Object
        ElasticsearchEnumerators.getter(fields, ImmutableMap.copyOf(mappings)); // 获取转换函数，根据字段和映射进行转换

    Iterable<ElasticsearchJson.SearchHit> iter; // 声明可迭代对象，用于保存查询结果
    if (offset == null) { // 检查是否没有偏移量
      // apply scrolling when there is no offsets
      // 如果没有偏移量，使用滚动查询以处理大量数据
      iter = () -> new Scrolling(transport).query(query); // 创建滚动查询迭代器，支持大数据量的分批获取
    } else { // 如果有偏移量
      final ElasticsearchJson.Result search = transport.search().apply(query); // 执行普通搜索查询
      iter = () -> search.searchHits().hits().iterator(); // 创建迭代器，遍历搜索命中的文档
    }

    return Linq4j.asEnumerable(iter).select(getter); // 将迭代器转换为可枚举对象，并应用转换函数
  }

  private Enumerable<Object> aggregate(List<String> ops, // 私有方法，执行聚合查询，接收操作列表
      List<Map.Entry<String, Class>> fields, // 要投影的字段列表
      List<Map.Entry<String, RelFieldCollation.Direction>> sort, // 排序字段列表
      List<String> groupBy, // 分组字段列表
      List<Map.Entry<String, String>> aggregations, // 聚合函数列表
      Map<String, String> mapping, // 字段映射关系
      Long offset, Long fetch) throws IOException { // 偏移量和获取数量，可能抛出IO异常

    if (!groupBy.isEmpty() && offset != null) { // 检查是否有分组且有偏移量
      String message = "Currently ES doesn't support generic pagination " // 构造错误消息
          + "with aggregations. You can still use LIMIT keyword (without OFFSET). " // 说明ES不支持聚合的通用分页
          + "For more details see https://github.com/elastic/elasticsearch/issues/4915"; // 提供参考链接
      throw new IllegalStateException(message); // 抛出非法状态异常
    }

    final ObjectNode query = mapper.createObjectNode(); // 创建根查询节点
    // manually parse into JSON from previously concatenated strings
    // 手动从之前拼接的字符串中解析为JSON
    for (String op : ops) { // 遍历所有操作字符串
      query.setAll((ObjectNode) mapper.readTree(op)); // 将每个操作解析为JSON节点并合并
    }

    // remove / override attributes which are not applicable to aggregations
    // 移除或重写不适用于聚合的属性
    query.put("_source", false); // 设置_source为false，不返回源文档字段
    query.put("size", 0); // 设置size为0，不返回文档内容
    query.remove("script_fields"); // 移除脚本字段，聚合不需要
    // set _source = false and size = 0, `FetchPhase` would still be executed
    // to fetch the metadata fields and visit the Lucene stored_fields,
    // which would lead to performance declined dramatically.
    // `stored_fields = _none` can prohibit such behavior entirely
    // 设置_source=false和size=0后，FetchPhase仍会执行以获取元数据字段
    // 访问Lucene的stored_fields会导致性能大幅下降
    // 设置stored_fields=_none可以完全禁止这种行为
    query.put("stored_fields", "_none_"); // 设置stored_fields为_none_，完全不获取存储字段

    // allows to detect aggregation for count(*)
    // 允许检测count(*)聚合
    final Predicate<Map.Entry<String, String>> isCountStar = e -> e.getValue() // 创建谓词，检查是否为count(*)聚合
            .contains("\"" + ElasticsearchConstants.ID + "\""); // 检查聚合值是否包含_id字段

    // list of expressions which are count(*)
    // 提取所有count(*)表达式的别名列表
    final Set<String> countAll = aggregations.stream() // 获取聚合函数流
            .filter(isCountStar) // 过滤出count(*)聚合
        .map(Map.Entry::getKey).collect(Collectors.toSet()); // 提取别名并收集到Set中

    final Map<String, String> fieldMap = new HashMap<>(); // 创建字段映射Map，用于重命名字段

    // due to ES aggregation format. fields in "order by" clause should go first
    // if "order by" is missing. order in "group by" is un-important
    // 由于ES聚合格式要求，order by子句中的字段应该放在前面
    // 如果没有order by，group by中的顺序不重要
    final Set<String> orderedGroupBy = new LinkedHashSet<>(); // 创建有序分组字段集，保持插入顺序
    orderedGroupBy.addAll(sort.stream().map(Map.Entry::getKey).collect(Collectors.toList())); // 先添加排序字段
    orderedGroupBy.addAll(groupBy); // 再添加分组字段

    // construct nested aggregations node(s)
    // 构建嵌套聚合节点
    ObjectNode parent = query.withObject("/" + AGGREGATIONS); // 在查询对象中创建聚合根节点
    for (String name : orderedGroupBy) { // 遍历所有分组字段
      final String aggName = "g_" + name; // 为每个分组字段生成聚合名称，添加g_前缀
      fieldMap.put(aggName, name); // 将聚合名称映射到原始字段名

      final ObjectNode section = parent.withObject("/" + aggName); // 在父节点下创建当前聚合节点
      final ObjectNode terms = section.withObject("/terms"); // 创建terms聚合子节点
      terms.put("field", name); // 设置terms聚合的字段名

      transport.mapping.missingValueFor(name).ifPresent(m -> { // 检查字段是否有缺失值处理
        // expose missing terms. each type has a different missing value
        // 暴露缺失的分组键，每种类型有不同的缺失值
        terms.set("missing", m); // 设置missing属性，指定缺失值的表示方式
      });

      if (fetch != null) { // 检查是否设置了获取数量
        terms.put("size", fetch); // 设置size属性，限制返回的分组数量
      }

      sort.stream().filter(e -> e.getKey().equals(name)).findAny() // 查找当前字段的排序配置
          .ifPresent(s -> // 如果找到排序配置
              terms.withObject("/order") // 创建order子节点
                  .put("_key", s.getValue().isDescending() ? "desc" : "asc")); // 设置按键值排序的方向

      parent = section.withObject("/" + AGGREGATIONS); // 将父节点移动到当前聚合的aggregations子节点，支持嵌套聚合
    }

    // simple version for queries like "select count(*), max(col1) from table" (no GROUP BY cols)
    // 简单版本，用于"select count(*), max(col1) from table"这样的查询（没有GROUP BY列）
    if (!groupBy.isEmpty() || !aggregations.stream().allMatch(isCountStar)) { // 检查是否有分组或非count(*)聚合
      for (Map.Entry<String, String> aggregation : aggregations) { // 遍历所有聚合函数
        JsonNode value = mapper.readTree(aggregation.getValue()); // 解析聚合函数的JSON值
        parent.set(aggregation.getKey(), value); // 将聚合函数添加到父节点中
      }
    }

    final Consumer<JsonNode> emptyAggRemover = new Consumer<JsonNode>() { // 创建消费者，用于清理空的聚合节点
      @Override public void accept(JsonNode node) { // 实现accept方法
        if (!node.has(AGGREGATIONS)) { // 检查节点是否有aggregations子节点
          node.elements().forEachRemaining(this); // 如果没有，递归处理所有子节点
          return; // 返回
        }
        JsonNode agg = node.get(AGGREGATIONS); // 获取aggregations子节点
        if (agg.isEmpty()) { // 检查聚合节点是否为空
          ((ObjectNode) node).remove(AGGREGATIONS); // 如果为空，移除该节点
        } else { // 如果不为空
          this.accept(agg); // 递归处理聚合节点
        }
      }
    };

    // cleanup query. remove empty AGGREGATIONS element (if empty)
    // 清理查询，移除空的AGGREGATIONS元素（如果为空）
    emptyAggRemover.accept(query); // 应用清理函数，移除所有空的聚合节点

    // This must be set to true or else in 7.X and 6/7 mixed clusters
    // will return lower bounded count values instead of an accurate count.
    // 这必须设置为true，否则在7.X和6/7混合集群中
    // 将返回下限计数值而不是精确计数
    if (groupBy.isEmpty() // 检查是否没有分组
        && version.elasticVersionMajor() >= ElasticsearchVersion.ES6.elasticVersionMajor()) { // 检查版本是否为ES6及以上
      query.put("track_total_hits", true); // 设置track_total_hits为true，确保返回精确的文档总数
    }

    ElasticsearchJson.Result res = transport.search(Collections.emptyMap()).apply(query); // 执行聚合查询

    final List<Map<String, Object>> result = new ArrayList<>(); // 创建结果列表，存储聚合结果
    if (res.aggregations() != null) { // 检查是否有聚合结果
      // collect values
      // 收集聚合值
      ElasticsearchJson.visitValueNodes(res.aggregations(), m -> { // 遍历聚合结果中的值节点
        // using 'Collectors.toMap' will trigger Java 8 bug here
        // 使用Collectors.toMap会触发Java 8的bug
        Map<String, Object> newMap = new LinkedHashMap<>(); // 创建新的有序Map
        for (String key : m.keySet()) { // 遍历所有键
          newMap.put(fieldMap.getOrDefault(key, key), m.get(key)); // 使用字段映射重命名键，保持值不变
        }
        result.add(newMap); // 将新Map添加到结果列表
      });
    } else { // 如果没有聚合结果
      // probably no group by. add single result
      // 可能没有分组，添加单个结果
      result.add(new LinkedHashMap<>()); // 添加一个空的Map作为结果
    }

    // elastic exposes total number of documents matching a query in "/hits/total" path
    // this can be used for simple "select count(*) from table"
    // Elasticsearch在"/hits/total"路径中暴露匹配查询的文档总数
    // 这可以用于简单的"select count(*) from table"查询
    final long total = res.searchHits().total().value(); // 获取匹配文档的总数

    if (groupBy.isEmpty()) { // 检查是否没有分组
      // put totals automatically for count(*) expression(s), unless they contain group by
      // 自动为count(*)表达式设置总数，除非包含分组
      for (String expr : countAll) { // 遍历所有count(*)表达式
        result.forEach(m -> m.put(expr, total)); // 将总数设置到每个结果的count(*)字段中
      }
    }

    final Function1<ElasticsearchJson.SearchHit, Object> getter = // 创建结果转换函数
        ElasticsearchEnumerators.getter(fields, ImmutableMap.copyOf(mapping)); // 获取转换函数

    ElasticsearchJson.SearchHits hits = // 创建搜索命中对象
        new ElasticsearchJson.SearchHits(res.searchHits().total(), result.stream() // 使用总数和结果流创建
            .map(r -> new ElasticsearchJson.SearchHit("_id", r, null)) // 将每个结果Map转换为SearchHit对象
            .collect(Collectors.toList())); // 收集为列表

    return Linq4j.asEnumerable(hits.hits()).select(getter); // 将SearchHit列表转换为可枚举对象并应用转换函数
  }

  @Override public RelDataType getRowType(RelDataTypeFactory relDataTypeFactory) { // 重写方法，获取表的行类型
    final RelDataType mapType = // 创建Map类型，用于存储动态字段
        relDataTypeFactory.createMapType( // 创建Map类型，键为VARCHAR，值为ANY
            relDataTypeFactory.createSqlType(SqlTypeName.VARCHAR), // Map的键类型为VARCHAR（字符串）
            relDataTypeFactory.createTypeWithNullability( // Map的值类型为ANY，且可为空
                relDataTypeFactory.createSqlType(SqlTypeName.ANY), // 创建ANY类型，表示任意类型
                true)); // 设置值类型可为空
    return relDataTypeFactory.builder().add("_MAP", mapType).build(); // 构建行类型，包含一个名为_MAP的Map字段
  }

  @Override public String toString() { // 重写toString方法，返回表的字符串表示
    return "ElasticsearchTable{" + indexName + "}"; // 返回包含索引名称的字符串
  }

  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schema, // 重写方法，将表转换为可查询对象
      String tableName) { // 表名参数
    return new ElasticsearchQueryable<>(queryProvider, schema, this, tableName); // 创建并返回Elasticsearch可查询对象
  }

  @Override public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) { // 重写方法，将表转换为关系表达式节点
    final RelOptCluster cluster = context.getCluster(); // 从上下文中获取优化集群
    return new ElasticsearchTableScan(cluster, cluster.traitSetOf(ElasticsearchRel.CONVENTION), // 创建Elasticsearch表扫描节点
        relOptTable, this, null); // 传入集群、特征集、优化表、当前表和空条件
  }

  /**
   * Implementation of {@link Queryable} based on
   * a {@link ElasticsearchTable}.
   * 基于ElasticsearchTable的Queryable接口实现
   * 该类提供了对Elasticsearch表的查询能力，通过代码生成的方式调用
   * 支持LINQ查询接口，允许在Java代码中直接查询Elasticsearch数据
   *
   * @param <T> element type
   * @param <T> 元素类型，表示查询结果的类型
   */
  public static class ElasticsearchQueryable<T> extends AbstractTableQueryable<T> { // 定义Elasticsearch可查询类，继承抽象表可查询类
    ElasticsearchQueryable(QueryProvider queryProvider, SchemaPlus schema, // 构造方法，接收查询提供者、Schema、表和表名
        ElasticsearchTable table, String tableName) { // 表对象和表名参数
      super(queryProvider, schema, table, tableName); // 调用父类构造方法初始化
    }

    @Override public Enumerator<T> enumerator() { // 重写方法，获取枚举器
      throw new UnsupportedOperationException("enumerator"); // 抛出不支持操作异常，因为枚举器不直接使用
    }

    private ElasticsearchTable getTable() { // 私有方法，获取Elasticsearch表对象
      return (ElasticsearchTable) table; // 将父类的table字段强制转换为ElasticsearchTable类型并返回
    }

    /** Called via code-generation.
     * 通过代码生成调用此方法
     * 该方法是查询的入口点，由Calcite的代码生成机制自动调用
     * 将查询参数传递给ElasticsearchTable的find方法执行实际查询
     *
     * @param ops list of queries (as strings)
     * @param ops 查询列表，表示为字符串
     * @param fields projection
     * @param fields 字段投影，指定要返回的字段
     * @see ElasticsearchMethod#ELASTICSEARCH_QUERYABLE_FIND
     * @see 参见ElasticsearchMethod中的ELASTICSEARCH_QUERYABLE_FIND方法定义
     * @return result as enumerable
     * @return 结果的可枚举对象
     */
    @SuppressWarnings("UnusedDeclaration") // 抑制未使用声明警告，因为该方法通过代码生成调用
    public Enumerable<Object> find(List<String> ops, // 公共方法，执行查找操作，接收查询操作列表
         List<Map.Entry<String, Class>> fields, // 要投影的字段列表
         List<Map.Entry<String, RelFieldCollation.Direction>> sort, // 排序字段列表
         List<Map.Entry<String, RelFieldCollation.NullDirection>> nullsSort, // 空值排序字段列表
         List<String> groupBy, // 分组字段列表
         List<Map.Entry<String, String>> aggregations, // 聚合函数列表
         Map<String, String> mappings, // 字段映射关系
         Long offset, Long fetch) { // 偏移量和获取数量
      try { // 开始try块，捕获IO异常
        return getTable().find(ops, fields, sort, nullsSort, groupBy, aggregations, mappings, // 调用表的find方法执行查询
            offset, fetch); // 传入偏移量和获取数量
      } catch (IOException e) { // 捕获IO异常
        throw new UncheckedIOException("Failed to query " + getTable().indexName, e); // 包装为未检查IO异常并抛出
      }
    }

  }
}
