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
package org.apache.calcite.adapter.elasticsearch; // 包声明：Elasticsearch适配器包

import com.fasterxml.jackson.annotation.JsonCreator; // Jackson注解：指定JSON反序列化构造函数
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Jackson注解：忽略未知属性
import com.fasterxml.jackson.annotation.JsonProperty; // Jackson注解：指定JSON属性映射
import com.fasterxml.jackson.core.JsonParser; // Jackson核心：JSON解析器
import com.fasterxml.jackson.core.JsonProcessingException; // Jackson核心：JSON处理异常
import com.fasterxml.jackson.databind.DeserializationContext; // Jackson数据绑定：反序列化上下文
import com.fasterxml.jackson.databind.JsonNode; // Jackson数据绑定：JSON节点
import com.fasterxml.jackson.databind.annotation.JsonDeserialize; // Jackson数据绑定：指定反序列化器
import com.fasterxml.jackson.databind.deser.std.StdDeserializer; // Jackson数据绑定：标准反序列化器基类
import com.fasterxml.jackson.databind.node.ArrayNode; // Jackson数据绑定：数组节点
import com.fasterxml.jackson.databind.node.ObjectNode; // Jackson数据绑定：对象节点
import com.google.common.collect.ImmutableSet; // Google Guava：不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // Checker Framework：可空类型注解

import java.io.IOException; // Java IO：IO异常
import java.time.Duration; // Java时间：持续时间
import java.util.ArrayDeque; // Java集合：数组双端队列
import java.util.ArrayList; // Java集合：动态数组列表
import java.util.Collections; // Java集合：集合工具类
import java.util.Deque; // Java集合：双端队列接口
import java.util.Iterator; // Java集合：迭代器接口
import java.util.LinkedHashMap; // Java集合：链式哈希映射
import java.util.List; // Java集合：列表接口
import java.util.Locale; // Java国际化：区域设置
import java.util.Map; // Java集合：映射接口
import java.util.Objects; // Java工具：对象工具类
import java.util.Optional; // Java工具：可选值容器
import java.util.Set; // Java集合：集合接口
import java.util.function.BiConsumer; // Java函数式：双参数消费者
import java.util.function.Consumer; // Java函数式：消费者
import java.util.function.Predicate; // Java函数式：谓词
import java.util.stream.StreamSupport; // Java流：流支持工具

import static java.util.Collections.unmodifiableMap; // Java集合静态导入：不可修改映射
import static java.util.Objects.requireNonNull; // Java工具静态导入：要求非空

/**
 * Internal objects (and deserializers) used to parse Elasticsearch results
 * (which are in JSON format).
 * 用于解析Elasticsearch结果（JSON格式）的内部对象（和反序列化器）
 *
 * <p>Since we're using basic row-level rest client http response has to be
 * processed manually using JSON (jackson) library.
 * 由于我们使用基本的行级REST客户端，HTTP响应必须使用JSON（jackson）库手动处理
 */
final class ElasticsearchJson {

  private ElasticsearchJson() {} // 私有构造函数，防止实例化，这是一个工具类

  /**
   * Visits leaves of the aggregation where all values are stored.
   * 访问聚合的叶子节点，所有值都存储在这里
   */
  static void visitValueNodes(Aggregations aggregations, // 聚合结果对象
      Consumer<Map<String, Object>> consumer) { // 消费者，用于处理每一行数据
    requireNonNull(aggregations, "aggregations"); // 参数校验：聚合对象不能为空
    requireNonNull(consumer, "consumer"); // 参数校验：消费者不能为空

    Map<RowKey, List<MultiValue>> rows = new LinkedHashMap<>(); // 创建行映射，键是行键，值是多值列表

    BiConsumer<RowKey, MultiValue> cons = (r, v) -> // 创建双参数消费者：行键和多值
        rows.computeIfAbsent(r, ignore -> new ArrayList<>()).add(v); // 如果行键不存在则创建新列表，然后添加值
    aggregations.forEach(a -> visitValueNodes(a, new ArrayList<>(), cons)); // 遍历所有聚合，递归访问值节点
    rows.forEach((k, v) -> { // 遍历所有行
      if (v.stream().allMatch(val -> val instanceof GroupValue)) { // 如果所有值都是分组值
        v.forEach(tuple -> { // 遍历每个分组值
          Map<String, Object> groupRow = new LinkedHashMap<>(k.keys); // 创建分组行，复制行键
          groupRow.put(tuple.getName(), tuple.value()); // 添加聚合名称和值
          consumer.accept(groupRow); // 将分组行传递给消费者
        });
      } else { // 如果值不是分组值
        Map<String, Object> row = new LinkedHashMap<>(k.keys); // 创建普通行，复制行键
        v.forEach(val -> row.put(val.getName(), val.value())); // 添加所有聚合名称和值
        consumer.accept(row); // 将行传递给消费者
      }
    });
  }

  /**
   * Visits Elasticsearch
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html">mapping
   * properties</a> and calls consumer for each {@code field / type} pair.
   * 访问Elasticsearch映射属性，并为每个字段/类型对调用消费者
   *
   * <p>Nested fields are represented as {@code foo.bar.qux}.
   * 嵌套字段表示为foo.bar.qux
   *
   * <p>Also supports
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/multi-fields.html">
   * multi-field mappings</a>.
   * 也支持多字段映射
   * These fields are also represented as {@code foo.bar} with the difference
   * that the type of the parent cannot be "nested".
   * 这些字段也表示为foo.bar，区别是父类型不能是"nested"
   */
  static void visitMappingProperties(ObjectNode mapping, // 映射对象节点
      BiConsumer<String, String> consumer) { // 消费者，接收字段名和类型
    requireNonNull(mapping, "mapping"); // 参数校验：映射对象不能为空
    requireNonNull(consumer, "consumer"); // 参数校验：消费者不能为空
    if (mapping.has("properties")) { // 如果映射包含properties属性
      visitMappingProperties(new ArrayDeque<>(), mapping, consumer); // 递归访问映射属性
    }
  }

  private static void visitMappingProperties(Deque<String> path, // 当前字段路径栈
      ObjectNode mapping, BiConsumer<String, String> consumer) { // 映射节点和消费者
    requireNonNull(mapping, "mapping"); // 参数校验：映射对象不能为空
    if (mapping.isMissingNode()) { // 如果节点缺失
      return; // 直接返回
    }

    // check if we've reached a leaf
    // 检查是否到达叶子节点
    Predicate<JsonNode> isLeaf = node -> node.path("type").isValueNode(); // 叶子节点判断条件：有type属性且是值节点

    // "properties" is present under the root or under "nested" fields
    // "properties"存在于根节点或"nested"字段下
    if (mapping.path("properties").isObject() // 如果properties是对象
        && !isLeaf.test(mapping.path("properties"))) { // 且不是叶子节点
      // recurse on "nested" field
      // 递归处理"nested"字段
      visitMappingProperties(path, (ObjectNode) mapping.get("properties"), // 递归访问properties
          consumer);
      return; // 返回
    }

    // "fields" is used for multi-fields
    // "fields"用于多字段
    if (mapping.path("fields").isObject() // 如果fields是对象
        && !isLeaf.test(mapping.path("fields"))) { // 且不是叶子节点
      // recurse on multi-field
      // 递归处理多字段
      visitMappingProperties(path, (ObjectNode) mapping.get("fields"), // 递归访问fields
          consumer);
      return; // 返回
    }

    if (isLeaf.test(mapping)) { // 如果当前节点是叶子节点
      // if we reached a leaf we can stop as we've already registered the type
      // mapping
      // 如果到达叶子节点，可以停止，因为我们已经注册了类型映射
      return; // 返回
    }

    // otherwise continue visiting mapping(s)
    // 否则继续访问映射
    Iterable<Map.Entry<String, JsonNode>> iter = mapping::fields; // 获取字段迭代器
    for (Map.Entry<String, JsonNode> entry : iter) { // 遍历所有字段
      final String name = entry.getKey(); // 获取字段名
      final ObjectNode node = (ObjectNode) entry.getValue(); // 获取字段节点
      path.add(name); // 将字段名添加到路径栈

      // type is present
      // 如果存在type属性
      if (node.get("type") != null) { // 如果type不为空
        consumer.accept(String.join(".", path), node.get("type").asText()); // 将完整路径和类型传递给消费者
      }

      visitMappingProperties(path, node, consumer); // 递归访问子节点
      path.removeLast(); // 从路径栈中移除当前字段名
    }
  }


  /**
   * Identifies a Calcite row (as in relational algebra).
   * 标识Calcite行（如关系代数中的行）
   */
  private static class RowKey {
    private final Map<String, Object> keys; // 行键映射：字段名到值的映射
    private final int hashCode; // 缓存的哈希码，提高性能

    private RowKey(final Map<String, Object> keys) { // 构造函数：接收键映射
      this.keys = requireNonNull(keys, "keys"); // 参数校验：键不能为空
      this.hashCode = Objects.hashCode(keys); // 计算并缓存哈希码
    }

    private RowKey(List<Bucket> buckets) { // 构造函数：从桶列表创建
      this(toMap(buckets)); // 调用另一个构造函数，将桶转换为映射
    }

    private static Map<String, Object> toMap(Iterable<Bucket> buckets) { // 将桶列表转换为映射
      return StreamSupport.stream(buckets.spliterator(), false) // 创建流
          .collect(LinkedHashMap::new, // 使用LinkedHashMap保持顺序
              (m, v) -> m.put(v.getName(), v.key()), // 将桶的名称和键放入映射
              LinkedHashMap::putAll); // 合并映射
    }

    @Override public boolean equals(final Object o) { // 重写equals方法
      if (this == o) { // 如果是同一个对象
        return true; // 返回true
      }
      if (o == null || getClass() != o.getClass()) { // 如果为空或类型不同
        return false; // 返回false
      }
      final RowKey rowKey = (RowKey) o; // 强制类型转换
      return hashCode == rowKey.hashCode // 比较哈希码
          && Objects.equals(keys, rowKey.keys); // 比较键映射
    }

    @Override public int hashCode() { // 重写hashCode方法
      return this.hashCode; // 返回缓存的哈希码
    }
  }

  private static void visitValueNodes(Aggregation aggregation, List<Bucket> parents, // 聚合对象和父桶列表
      BiConsumer<RowKey, MultiValue> consumer) { // 消费者，接收行键和多值

    if (aggregation instanceof MultiValue) { // 如果聚合是多值类型
      // this is a leaf. publish value of the row.
      // 这是叶子节点，发布行的值
      RowKey key = new RowKey(parents); // 从父桶创建行键
      consumer.accept(key, (MultiValue) aggregation); // 将行键和多值传递给消费者
      return; // 返回
    }

    if (aggregation instanceof Bucket) { // 如果聚合是桶类型
      Bucket bucket = (Bucket) aggregation; // 强制类型转换
      if (bucket.hasNoAggregations()) { // 如果桶没有子聚合
        // bucket with no aggregations is also considered a leaf node
        // 没有子聚合的桶也被视为叶子节点
        visitValueNodes(GroupValue.of(bucket.getName(), bucket.key()), parents, consumer); // 创建分组值并访问
        return; // 返回
      }
      parents.add(bucket); // 将桶添加到父桶列表
      bucket.getAggregations().forEach(a -> visitValueNodes(a, parents, consumer)); // 递归访问所有子聚合
      parents.remove(parents.size() - 1); // 从父桶列表中移除当前桶
    } else if (aggregation instanceof HasAggregations) { // 如果聚合有子聚合
      HasAggregations children = (HasAggregations) aggregation; // 强制类型转换
      children.getAggregations().forEach(a -> visitValueNodes(a, parents, consumer)); // 递归访问所有子聚合
    } else if (aggregation instanceof MultiBucketsAggregation) { // 如果聚合是多桶聚合
      MultiBucketsAggregation multi = (MultiBucketsAggregation) aggregation; // 强制类型转换
      multi.buckets().forEach(b -> visitValueNodes(b, parents, consumer)); // 递归访问所有桶
    }

  }

  /**
   * Response from Elastic.
   * Elasticsearch的响应结果
   */
  @JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知属性
  static class Result {
    private final SearchHits hits; // 搜索命中结果
    private final Aggregations aggregations; // 聚合结果
    private final String scrollId; // 滚动查询ID
    private final long took; // 查询耗时（毫秒）

    /**
     * Constructor for this instance.
     * 构造函数
     *
     * @param hits list of matched documents
     * 命中的文档列表
     * @param took time taken (in took) for this query to execute
     * 查询执行耗时（毫秒）
     */
    @JsonCreator // Jackson注解：指定JSON反序列化构造函数
    Result(@JsonProperty("hits") SearchHits hits, // 从JSON的hits属性映射
        @JsonProperty("aggregations") Aggregations aggregations, // 从JSON的aggregations属性映射
        @JsonProperty("_scroll_id") String scrollId, // 从JSON的_scroll_id属性映射
        @JsonProperty("took") long took) { // 从JSON的took属性映射
      this.hits = requireNonNull(hits, "hits"); // 参数校验：hits不能为空
      this.aggregations = aggregations; // 设置聚合结果
      this.scrollId = scrollId; // 设置滚动ID
      this.took = took; // 设置耗时
    }

    SearchHits searchHits() { // 获取搜索命中结果
      return hits; // 返回hits
    }

    Aggregations aggregations() { // 获取聚合结果
      return aggregations; // 返回aggregations
    }

    Duration took() { // 获取查询耗时
      return Duration.ofMillis(took); // 将毫秒转换为Duration对象
    }

    Optional<String> scrollId() { // 获取滚动ID
      return Optional.ofNullable(scrollId); // 返回可能为空的Optional
    }

  }

  /**
   * Similar to {@code SearchHits} in ES. Container for {@link SearchHit}
   * 类似于Elasticsearch中的SearchHits，是SearchHit的容器
   */
  @JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知属性
  static class SearchHits {

    private final SearchTotal total; // 总命中数
    private final List<SearchHit> hits; // 命中的文档列表

    @JsonCreator // Jackson注解：指定JSON反序列化构造函数
    SearchHits(@JsonProperty("total")final SearchTotal total, // 从JSON的total属性映射
               @JsonProperty("hits") final List<SearchHit> hits) { // 从JSON的hits属性映射
      this.total = total; // 设置总命中数
      this.hits = requireNonNull(hits, "hits"); // 参数校验并设置命中列表
    }

    public List<SearchHit> hits() { // 获取命中列表
      return this.hits; // 返回hits
    }

    public SearchTotal total() { // 获取总命中数
      return total; // 返回total
    }

  }

  /**
   * Container for total hits.
   * 总命中数的容器
   */
  @JsonDeserialize(using = SearchTotalDeserializer.class) // 使用自定义反序列化器
  static class SearchTotal {

    private final long value; // 总命中数值

    SearchTotal(final long value) { // 构造函数
      this.value = value; // 设置总命中数
    }

    public long value() { // 获取总命中数
      return value; // 返回value
    }

  }

  /**
   * Allows to de-serialize total hits structures.
   * 允许反序列化总命中数结构
   */
  static class SearchTotalDeserializer extends StdDeserializer<SearchTotal> { // 继承标准反序列化器

    SearchTotalDeserializer() { // 构造函数
      super(SearchTotal.class); // 调用父类构造函数，指定目标类
    }

    @Override public SearchTotal deserialize(final JsonParser parser, // JSON解析器
                                             final DeserializationContext ctxt) // 反序列化上下文
        throws IOException  { // 可能抛出IO异常

      JsonNode node = parser.getCodec().readTree(parser); // 读取JSON树节点
      return parseSearchTotal(node); // 解析并返回SearchTotal对象
    }

    private static SearchTotal parseSearchTotal(JsonNode node) { // 解析SearchTotal的静态方法

      final Number value; // 声明数值变量
      if (node.isNumber()) { // 如果节点是数字类型
        value = node.numberValue(); // 直接获取数值
      } else { // 如果节点是对象类型
        value = node.get("value").numberValue(); // 从value属性获取数值
      }

      return new SearchTotal(value.longValue()); // 返回SearchTotal对象
    }

  }

  /**
   * Concrete result record which matched the query. Similar to {@code SearchHit} in ES.
   * 匹配查询的具体结果记录，类似于Elasticsearch中的SearchHit
   */
  @JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知属性
  static class SearchHit {

    /**
     * ID of the document (not available in aggregations).
     * 文档ID（在聚合中不可用）
     */
    private final String id; // 文档ID
    private final Map<String, Object> source; // 文档源数据（_source）
    private final Map<String, Object> fields; // 文档字段数据（fields）

    @JsonCreator // Jackson注解：指定JSON反序列化构造函数
    SearchHit(@JsonProperty(ElasticsearchConstants.ID) final String id, // 从JSON的_id属性映射
                      @JsonProperty("_source") final Map<String, Object> source, // 从JSON的_source属性映射
                      @JsonProperty("fields") final Map<String, Object> fields) { // 从JSON的fields属性映射
      this.id = requireNonNull(id, "id"); // 参数校验：id不能为空

      // both can't be null
      // source和fields不能同时为空
      if (source == null && fields == null) { // 如果两者都为空
        final String message = // 创建错误消息
            String.format(Locale.ROOT,
                "Both '_source' and 'fields' are missing for %s", id);
        throw new IllegalArgumentException(message); // 抛出非法参数异常
      }

      // both can't be non-null
      // source和fields不能同时非空
      if (source != null && fields != null) { // 如果两者都非空
        final String message = // 创建错误消息
            String.format(Locale.ROOT,
                "Both '_source' and 'fields' are populated (non-null) for %s", id);
        throw new IllegalArgumentException(message); // 抛出非法参数异常
      }

      this.source = source; // 设置源数据
      this.fields = fields; // 设置字段数据
    }

    /**
     * Returns id of this hit (usually document id).
     * 返回此命中的ID（通常是文档ID）
     *
     * @return unique id
     * 唯一ID
     */
    public String id() { // 获取文档ID
      return id; // 返回id
    }

    Object valueOrNull(String name) { // 根据名称获取值，如果不存在则返回null
      requireNonNull(name, "name"); // 参数校验：名称不能为空

      // for "select *" return whole document
      // 对于"select *"返回整个文档
      if (ElasticsearchConstants.isSelectAll(name)) { // 如果是选择所有
        return sourceOrFields(); // 返回源数据或字段数据
      }

      if (fields != null && fields.containsKey(name)) { // 如果fields不为空且包含该名称
        Object field = fields.get(name); // 获取字段值
        if (field instanceof Iterable) { // 如果字段是可迭代对象
          // return first element (or null)
          // 返回第一个元素（或null）
          Iterator<?> iter = ((Iterable<?>) field).iterator(); // 获取迭代器
          return iter.hasNext() ? iter.next() : null; // 返回下一个元素或null
        }

        return field; // 返回字段值
      }

      return valueFromPath(source, name); // 从源数据中根据路径获取值
    }

    /**
     * Returns property from nested maps given a path like {@code a.b.c}.
     * 从嵌套映射中返回属性，给定如a.b.c的路径
     *
     * @param map current map
     * 当前映射
     * @param path field path(s), optionally with dots ({@code a.b.c}).
     * 字段路径，可选带点（如a.b.c）
     * @return value located at path {@code path} or {@code null} if not found.
     * 位于路径path的值，如果未找到则返回null
     */
    private static @Nullable Object valueFromPath( // 从路径获取值的静态方法
        @Nullable Map<String, Object> map, String path) { // 映射和路径
      if (map == null) { // 如果映射为空
        return null; // 返回null
      }

      if (map.containsKey(path)) { // 如果映射包含该路径
        return map.get(path); // 返回对应的值
      }

      // maybe pattern of type a.b.c
      // 可能是a.b.c类型的模式
      final int index = path.indexOf('.'); // 查找点的位置
      if (index == -1) { // 如果没有点
        return null; // 返回null
      }

      final String prefix = path.substring(0, index); // 获取前缀（点之前的部分）
      final String suffix = path.substring(index + 1); // 获取后缀（点之后的部分）

      Object maybeMap = map.get(prefix); // 获取前缀对应的值
      if (maybeMap instanceof Map) { // 如果值是映射类型
        return valueFromPath((Map<String, Object>) maybeMap, suffix); // 递归处理剩余路径
      }

      return null; // 返回null
    }

    Map<String, Object> source() { // 获取源数据
      return source; // 返回source
    }

    Map<String, Object> fields() { // 获取字段数据
      return fields; // 返回fields
    }

    Map<String, Object> sourceOrFields() { // 获取源数据或字段数据
      return source != null ? source : fields; // 如果source不为空返回source，否则返回fields
    }
  }


  /**
   * {@link Aggregation} container.
   * 聚合容器
   */
  @JsonDeserialize(using = AggregationsDeserializer.class) // 使用自定义反序列化器
  static class Aggregations implements Iterable<Aggregation> { // 实现Iterable接口

    private final List<? extends Aggregation> aggregations; // 聚合列表
    private Map<String, Aggregation> aggregationsAsMap; // 聚合映射（按名称索引），延迟初始化

    Aggregations(List<? extends Aggregation> aggregations) { // 构造函数
      this.aggregations = requireNonNull(aggregations, "aggregations"); // 参数校验并设置聚合列表
    }

    /**
     * Iterates over the {@link Aggregation}s.
     * 迭代所有聚合
     */
    @Override public final Iterator<Aggregation> iterator() { // 实现iterator方法
      return asList().iterator(); // 返回列表的迭代器
    }

    /**
     * The list of {@link Aggregation}s.
     * 聚合列表
     */
    final List<Aggregation> asList() { // 获取聚合列表
      return Collections.unmodifiableList(aggregations); // 返回不可修改的列表
    }

    /**
     * Returns the {@link Aggregation}s keyed by aggregation name. Lazy init.
     * 返回按聚合名称键控的聚合映射，延迟初始化
     */
    final Map<String, Aggregation> asMap() { // 获取聚合映射
      if (aggregationsAsMap == null) { // 如果映射未初始化
        Map<String, Aggregation> map = new LinkedHashMap<>(aggregations.size()); // 创建LinkedHashMap
        for (Aggregation aggregation : aggregations) { // 遍历所有聚合
          map.put(aggregation.getName(), aggregation); // 将聚合名称和聚合对象放入映射
        }
        this.aggregationsAsMap = unmodifiableMap(map); // 创建不可修改的映射
      }
      return aggregationsAsMap; // 返回映射
    }

    /**
     * Returns the aggregation that is associated with the specified name.
     * 返回与指定名称关联的聚合
     */
    @SuppressWarnings("unchecked") // 抑制类型转换警告
    public final <A extends Aggregation> A get(String name) { // 泛型方法：获取指定名称的聚合
      return (A) asMap().get(name); // 从映射中获取并强制类型转换
    }

    @Override public final boolean equals(Object obj) { // 重写equals方法
      if (obj == null || getClass() != obj.getClass()) { // 如果为空或类型不同
        return false; // 返回false
      }
      return aggregations.equals(((Aggregations) obj).aggregations); // 比较聚合列表
    }

    @Override public final int hashCode() { // 重写hashCode方法
      return Objects.hash(getClass(), aggregations); // 计算哈希码
    }

  }

  /**
   * Identifies all aggregations.
   * 标识所有聚合
   */
  interface Aggregation { // 聚合接口

    /**
     * Returns the name of this aggregation.
     * 返回此聚合的名称
     */
    String getName(); // 获取聚合名称的方法

  }

  /**
   * Allows traversing aggregations tree.
   * 允许遍历聚合树
   */
  interface HasAggregations { // 有子聚合的接口
    Aggregations getAggregations(); // 获取子聚合的方法
  }

  /**
   * An aggregation that returns multiple buckets.
   * 返回多个桶的聚合
   */
  static class MultiBucketsAggregation implements Aggregation { // 实现Aggregation接口

    private final String name; // 聚合名称
    private final List<Bucket> buckets; // 桶列表

    MultiBucketsAggregation(final String name, // 构造函数
        final List<Bucket> buckets) { // 接收名称和桶列表
      this.name = name; // 设置名称
      this.buckets = buckets; // 设置桶列表
    }

    /**
     * Returns the buckets of this aggregation.
     * 返回此聚合的桶
     */
    List<Bucket> buckets() { // 获取桶列表
      return buckets; // 返回buckets
    }

    @Override public String getName() { // 实现getName方法
      return name; // 返回name
    }
  }

  /**
   * A bucket represents a criteria to which all documents that fall in it adhere to.
   * It is also uniquely identified
   * by a key, and can potentially hold sub-aggregations computed over all documents in it.
   * 桶代表一个标准，所有落入其中的文档都符合该标准。它也由键唯一标识，并且可能包含在桶中所有文档上计算的子聚合
   */
  static class Bucket implements HasAggregations, Aggregation { // 实现HasAggregations和Aggregation接口
    private final Object key; // 桶的键
    private final String name; // 桶的名称
    private final Aggregations aggregations; // 子聚合

    Bucket(final Object key, // 构造函数
        final String name, // 接收键、名称和子聚合
        final Aggregations aggregations) {
      this.key = key; // key can be set after construction // 设置键（可以在构造后设置）
      this.name = requireNonNull(name, "name"); // 参数校验并设置名称
      this.aggregations = requireNonNull(aggregations, "aggregations"); // 参数校验并设置子聚合
    }

    /**
     * Returns the key associated with the bucket.
     * 返回与桶关联的键
     */
    Object key() { // 获取桶的键
      return key; // 返回key
    }

    /**
     * Returns the key associated with the bucket as a string.
     * 返回与桶关联的键的字符串形式
     */
    String keyAsString() { // 获取桶键的字符串形式
      return Objects.toString(key()); // 将键转换为字符串
    }

    /**
     * Means current bucket has no aggregations.
     * 表示当前桶没有子聚合
     */
    boolean hasNoAggregations() { // 检查桶是否有子聚合
      return aggregations.asList().isEmpty(); // 返回聚合列表是否为空
    }

    /**
     * Returns the sub-aggregations of this bucket.
     * 返回此桶的子聚合
     */
    @Override public Aggregations getAggregations() { // 实现getAggregations方法
      return aggregations; // 返回aggregations
    }

    @Override public String getName() { // 实现getName方法
      return name; // 返回name
    }
  }

  /**
   * Multi-value aggregation, like
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-metrics-stats-aggregation.html">Stats</a>.
   * 多值聚合，如Stats聚合
   */
  static class MultiValue implements Aggregation { // 实现Aggregation接口
    private final String name; // 聚合名称
    private final Map<String, Object> values; // 值映射

    MultiValue(final String name, final Map<String, Object> values) { // 构造函数
      this.name = requireNonNull(name, "name"); // 参数校验并设置名称
      this.values = requireNonNull(values, "values"); // 参数校验并设置值映射
    }

    @Override public String getName() { // 实现getName方法
      return name; // 返回name
    }

    Map<String, Object> values() { // 获取值映射
      return values; // 返回values
    }

    /**
     * For single value. Returns single value represented by this leaf aggregation.
     * 用于单值。返回由此叶子聚合表示的单值
     *
     * @return value corresponding to {@code value}
     * 对应"value"的值
     */
    Object value() { // 获取单值
      if (!values().containsKey("value")) { // 如果values不包含"value"键
        String message = String.format(Locale.ROOT, "'value' field not present in "
            + "%s aggregation", getName()); // 创建错误消息

        throw new IllegalStateException(message); // 抛出非法状态异常
      }

      return values().get("value"); // 返回values中的"value"
    }

  }

  /**
   * Distinguishes from {@link MultiValue}.
   * In order that rows which have the same key can be put into result map.
   * 区别于MultiValue。为了使具有相同键的行可以放入结果映射
   */
  static class GroupValue extends MultiValue { // 继承MultiValue
    GroupValue(String name, Map<String, Object> values) { // 构造函数
      super(name, values); // 调用父类构造函数
    }

    /**
     * Constructs a {@link GroupValue} instance with a single value.
     * 构造具有单个值的GroupValue实例
     */
    static GroupValue of(String name, Object value) { // 静态工厂方法
      return new GroupValue(name, Collections.singletonMap("value", value)); // 创建包含单个值的GroupValue
    }
  }

  /**
   * Allows to de-serialize nested aggregation structures.
   * 允许反序列化嵌套聚合结构
   */
  static class AggregationsDeserializer extends StdDeserializer<Aggregations> { // 继承标准反序列化器

    private static final Set<String> IGNORE_TOKENS = // 需要忽略的令牌集合
        ImmutableSet.of("meta", "buckets", "value", "values", "value_as_string",
            "doc_count", "key", "key_as_string");

    AggregationsDeserializer() { // 构造函数
      super(Aggregations.class); // 调用父类构造函数，指定目标类
    }

    @Override public Aggregations deserialize(final JsonParser parser, // JSON解析器
        final DeserializationContext ctxt) // 反序列化上下文
        throws IOException  { // 可能抛出IO异常

      ObjectNode node = parser.getCodec().readTree(parser); // 读取JSON树节点
      return parseAggregations(parser, node); // 解析并返回聚合对象
    }

    private static Aggregations parseAggregations(JsonParser parser, ObjectNode node) // 解析聚合的静态方法
        throws JsonProcessingException { // 可能抛出JSON处理异常

      List<Aggregation> aggregations = new ArrayList<>(); // 创建聚合列表

      Iterable<Map.Entry<String, JsonNode>> iter = node::fields; // 获取字段迭代器
      for (Map.Entry<String, JsonNode> entry : iter) { // 遍历所有字段
        final String name = entry.getKey(); // 获取字段名
        final JsonNode value = entry.getValue(); // 获取字段值

        Aggregation agg = null; // 声明聚合变量
        if (value.has("buckets")) { // 如果值包含buckets属性
          agg = parseBuckets(parser, name, (ArrayNode) value.get("buckets")); // 解析桶
        } else if (value.isObject() && !IGNORE_TOKENS.contains(name)) { // 如果值是对象且名称不在忽略列表中
          // leaf
          // 叶子节点
          agg = parseValue(parser, name, (ObjectNode) value); // 解析值
        }

        if (agg != null) { // 如果聚合不为空
          aggregations.add(agg); // 添加到聚合列表
        }
      }

      return new Aggregations(aggregations); // 返回聚合对象
    }



    private static MultiValue parseValue(JsonParser parser, String name, ObjectNode node) // 解析值的静态方法
        throws JsonProcessingException { // 可能抛出JSON处理异常

      return new MultiValue(name, parser.getCodec().treeToValue(node, Map.class)); // 创建多值对象
    }

    private static Aggregation parseBuckets(JsonParser parser, String name, ArrayNode nodes) // 解析桶的静态方法
        throws JsonProcessingException { // 可能抛出JSON处理异常

      List<Bucket> buckets = new ArrayList<>(nodes.size()); // 创建桶列表
      for (JsonNode b : nodes) { // 遍历所有节点
        buckets.add(parseBucket(parser, name, (ObjectNode) b)); // 解析每个桶
      }

      return new MultiBucketsAggregation(name, buckets); // 返回多桶聚合
    }

    /**
     * Determines if current key is a missing field key. Missing key is returned when document
     * does not have pivoting attribute (example {@code GROUP BY _MAP['a.b.missing']}). It helps
     * grouping documents which don't have a field. In relational algebra this
     * would normally be {@code null}.
     * 确定当前键是否是缺失字段键。当文档没有透视属性（例如GROUP BY _MAP['a.b.missing']）时返回缺失键。它有助于对没有字段的文档进行分组。在关系代数中，这通常是null
     *
     * <p>Please note that missing value is different for each type.
     * 请注意，每种类型的缺失值都不同
     *
     * @param key current {@code key} (usually string) as returned by ES
     * 当前键（通常是字符串），由ES返回
     * @return {@code true} if this value
     * 如果此值是缺失值则返回true
     */
    private static boolean isMissingBucket(JsonNode key) { // 检查是否是缺失桶的静态方法
      return ElasticsearchMapping.Datatype.isMissingValue(key); // 调用Datatype的isMissingValue方法
    }

    private static Bucket parseBucket(JsonParser parser, String name, ObjectNode node) // 解析桶的静态方法
        throws JsonProcessingException  { // 可能抛出JSON处理异常

      if (!node.has("key")) { // 如果节点没有key属性
        throw new IllegalArgumentException("No 'key' attribute for " + node); // 抛出非法参数异常
      }

      final JsonNode keyNode = node.get("key"); // 获取key节点
      final Object key; // 声明键变量
      if (isMissingBucket(keyNode) || keyNode.isNull()) { // 如果是缺失桶或键为空
        key = null; // 设置键为null
      } else if (keyNode.isTextual()) { // 如果键是文本类型
        key = keyNode.textValue(); // 获取文本值
      } else if (keyNode.isNumber()) { // 如果键是数字类型
        key = keyNode.numberValue(); // 获取数值
      } else if (keyNode.isBoolean()) { // 如果键是布尔类型
        key = keyNode.booleanValue(); // 获取布尔值
      } else { // 其他情况
        // don't usually expect keys to be Objects
        // 通常不期望键是对象
        key = parser.getCodec().treeToValue(node, Map.class); // 转换为映射
      }

      return new Bucket(key, name, parseAggregations(parser, node)); // 创建并返回桶对象
    }

  }

}
