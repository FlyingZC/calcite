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
package org.apache.calcite.adapter.elasticsearch;

import com.fasterxml.jackson.annotation.JsonCreator; // Jackson注解：标记用于JSON反序列化的构造方法或工厂方法
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // Jackson注解：在反序列化时忽略JSON中未映射到Java对象的属性
import com.fasterxml.jackson.annotation.JsonProperty; // Jackson注解：指定JSON属性名与Java字段/参数之间的映射关系

import java.time.Duration; // Java 8时间类：表示时间段，用于处理查询耗时
import java.util.Iterator; // Java集合接口：用于迭代遍历集合元素
import java.util.List; // Java集合接口：表示有序列表，用于存储命中的文档列表
import java.util.Locale; // Java国际化类：用于本地化字符串格式化
import java.util.Map; // Java集合接口：表示键值对映射，用于存储文档的源数据和字段数据

import static java.util.Objects.requireNonNull; // 静态导入：Objects工具类的requireNonNull方法，用于参数非空校验

/**
 * Internal object used to parse elastic search result. Similar to {@code SearchHit}.
 * Since we're using row-level rest client the response has to be processed manually.
 * // 用于解析Elasticsearch搜索结果的内部对象，类似于ES中的SearchHit
 * // 由于使用行级REST客户端，响应需要手动处理
 * // 该类是Calcite适配器与Elasticsearch交互的核心数据结构，负责将ES的JSON响应转换为Java对象
 * // 主要功能：封装ES查询返回的完整结果，包括命中的文档列表、查询耗时等元数据
 * // 设计模式：使用Jackson注解进行JSON反序列化，支持忽略未知属性以保持向前兼容性
 * // 使用场景：当Calcite执行针对Elasticsearch的查询时，通过该类解析ES返回的JSON响应
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElasticsearchSearchResult {

  private final SearchHits hits; // ES搜索命中的文档集合，包含所有匹配的文档及其详细信息
  private final long took; // 查询执行耗时，单位为毫秒，用于性能监控和优化

  /**
   * Constructor for this instance.
   *
   * @param hits list of matched documents
   * @param took time taken (in took) for this query to execute
   * // 构造方法：创建Elasticsearch搜索结果实例
   * // @JsonCreator注解表示这是Jackson反序列化时使用的构造方法
   * // @JsonProperty注解指定JSON字段名与Java参数的映射关系
   * // @param hits 匹配的文档列表，对应ES响应中的hits字段
   * // @param took 查询执行耗时（毫秒），对应ES响应中的took字段
   */
  @JsonCreator
  ElasticsearchSearchResult(@JsonProperty("hits") SearchHits hits,
      @JsonProperty("took") long took) {
    this.hits = requireNonNull(hits, "hits"); // 确保hits不为null，否则抛出NullPointerException
    this.took = took; // 记录查询耗时
  }

  public SearchHits searchHits() {
    return hits; // 返回搜索命中的文档集合对象，包含所有匹配的文档
  }

  public Duration took() {
    return Duration.ofMillis(took); // 将毫秒数转换为Duration对象返回，便于进行时间计算和格式化
  }

  /**
   * Similar to {@code SearchHits} in ES. Container for {@link SearchHit}
   * // 内部静态类：表示ES搜索命中的文档集合，类似于ES中的SearchHits对象
   * // 主要功能：封装所有匹配的文档列表和总数统计信息
   * // 包含两个核心信息：命中的文档总数（total）和具体的文档列表（hits）
   * // @JsonIgnoreProperties(ignoreUnknown = true) 忽略JSON中的未知属性，提高兼容性
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SearchHits {

    private final long total; // 命中文档的总数，对应ES响应中的hits.total字段
    private final List<SearchHit> hits; // 命中文档的详细列表，每个元素代表一个匹配的文档

    @JsonCreator
    SearchHits(@JsonProperty("total")final long total,
               @JsonProperty("hits") final List<SearchHit> hits) {
      this.total = total; // 记录命中文档的总数
      this.hits = requireNonNull(hits, "hits"); // 确保文档列表不为null，否则抛出异常
    }

    public List<SearchHit> hits() {
      return this.hits; // 返回命中文档的详细列表，供调用方遍历和处理
    }

    public long total() {
      return total; // 返回命中文档的总数，用于分页和结果统计
    }

  }

  /**
   * Concrete result record which matched the query. Similar to {@code SearchHit} in ES.
   * // 内部静态类：表示单个匹配的文档记录，类似于ES中的SearchHit对象
   * // 主要功能：封装单个文档的完整信息，包括文档ID、源数据(_source)和字段(fields)
   * // ES有两种返回字段的方式：_source（完整文档）和fields（指定字段）
   // // 该类确保同一时间只存在一种方式，避免数据冗余
   * // @JsonIgnoreProperties(ignoreUnknown = true) 忽略JSON中的未知属性
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SearchHit {
    private final String id; // 文档的唯一标识符，对应ES中的_id字段
    private final Map<String, Object> source; // 文档的完整源数据，对应ES中的_source字段，包含文档的所有字段和值
    private final Map<String, Object> fields; // 文档的指定字段数据，对应ES中的fields字段，用于只查询特定字段的场景

    @JsonCreator
    private SearchHit(@JsonProperty("_id") final String id,
                      @JsonProperty("_source") final Map<String, Object> source,
                      @JsonProperty("fields") final Map<String, Object> fields) {
      this.id = requireNonNull(id, "id"); // 确保文档ID不为null，否则抛出异常

      // both can't be null // _source和fields不能同时为null，至少需要一个有值
      if (source == null && fields == null) {
        final String message =
            String.format(Locale.ROOT,
                "Both '_source' and 'fields' are missing for %s", id);
        throw new IllegalArgumentException(message); // 抛出异常，提示文档缺少数据源
      }

      // both can't be non-null // _source和fields不能同时非null，避免数据冗余
      if (source != null && fields != null) {
        final String message =
            String.format(Locale.ROOT,
                "Both '_source' and 'fields' are populated (non-null) for %s", id);
        throw new IllegalArgumentException(message); // 抛出异常，提示数据源冲突
      }

      this.source = source; // 保存源数据引用
      this.fields = fields; // 保存字段数据引用
    }

    /**
     * Returns the id of this hit (usually document id).
     *
     * @return unique id
     * // 获取文档的唯一标识符
     * // @return 文档ID字符串，通常对应ES中的_id字段
     */
    public String id() {
      return id; // 返回文档的唯一标识符
    }

    /**
     * Finds a specific attribute from ES search result.
     *
     * @param name attribute name
     * @return value from result (_source or fields)
     * // 根据属性名从ES搜索结果中查找特定属性的值
     * // 该方法会自动从_source或fields中查找，无需调用方关心数据来源
     * // 对于fields模式，如果字段值是集合类型，会自动返回第一个元素
     * // @param name 要查找的属性名称
     * // @return 属性值，如果找到则返回对应值，否则抛出异常
     */
    Object value(String name) {
      requireNonNull(name, "name"); // 确保属性名不为null

      if (!sourceOrFields().containsKey(name)) { // 检查属性是否存在
        final String message =
            String.format(Locale.ROOT,
                "Attribute %s not found in search result %s", name, id);
        throw new IllegalArgumentException(message); // 属性不存在，抛出异常
      }

      if (source != null) { // 如果使用_source模式
        return source.get(name); // 直接从source中获取属性值
      } else if (fields != null) { // 如果使用fields模式
        Object field = fields.get(name); // 获取字段值
        if (field instanceof Iterable) { // 如果字段值是可迭代对象（通常是数组）
          // return first element (or null) // 返回第一个元素（或null）
          Iterator<?> iter = ((Iterable<?>) field).iterator();
          return iter.hasNext() ? iter.next() : null; // 有元素则返回第一个，否则返回null
        }

        return field; // 返回字段值
      }

      throw new AssertionError("Shouldn't get here: " + id); // 理论上不会执行到这里，如果执行说明代码逻辑错误

    }

    public Map<String, Object> source() {
      return source; // 返回文档的完整源数据(_source)，可能为null
    }

    public Map<String, Object> fields() {
      return fields; // 返回文档的字段数据(fields)，可能为null
    }

    public Map<String, Object> sourceOrFields() {
      return source != null ? source : fields; // 返回非null的数据源，优先返回source，如果source为null则返回fields
    }
  }

}
