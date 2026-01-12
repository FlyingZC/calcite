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
package org.apache.calcite.adapter.elasticsearch; // 声明包名，该类属于Calcite的Elasticsearch适配器包

import com.fasterxml.jackson.databind.JsonNode; // 导入Jackson库的JsonNode类，用于表示JSON节点
import com.fasterxml.jackson.databind.node.JsonNodeFactory; // 导入Jackson的JsonNodeFactory类，用于创建JsonNode实例
import com.google.common.collect.ImmutableMap; // 导入Guava库的ImmutableMap类，用于创建不可变Map

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的Nullable注解，用于标记可能为null的值

import java.time.LocalDate; // 导入Java 8的LocalDate类，用于表示日期
import java.time.ZoneOffset; // 导入Java 8的ZoneOffset类，用于表示时区偏移
import java.util.Locale; // 导入Java的Locale类，用于本地化操作
import java.util.Map; // 导入Java的Map接口，用于键值对映射
import java.util.Optional; // 导入Java 8的Optional类，用于可能为空的值
import java.util.Set; // 导入Java的Set接口，用于集合操作
import java.util.stream.Collectors; // 导入Java 8的Collectors类，用于Stream流的收集操作
import java.util.stream.Stream; // 导入Java 8的Stream接口，用于流式处理

import static java.util.Objects.requireNonNull; // 静态导入Objects的requireNonNull方法，用于参数非空检查

/**
 * Stores Elasticsearch
 * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html">
 * mapping</a> information for particular index. This information is
 * extracted from {@code /$index/_mapping} endpoint.
 * 存储特定索引的Elasticsearch映射信息。这些信息是从{@code /$index/_mapping}端点提取的。
 *
 * <p>Instances of this class are immutable.
 * 该类的实例是不可变的。
 */
class ElasticsearchMapping { // ElasticsearchMapping类：用于存储和管理Elasticsearch索引的字段映射信息

  private final String index; // 成员变量：存储Elasticsearch索引名称，表示该映射所属的索引

  private final Map<String, Datatype> mapping; // 成员变量：存储字段名到数据类型的映射，键是字段名（如"foo.bar.qux"），值是Datatype对象

  ElasticsearchMapping(final String index,
      final Map<String, String> mapping) { // 构造方法：创建ElasticsearchMapping实例，接收索引名称和字段映射
    this.index = requireNonNull(index, "index"); // 验证索引名称不为空，并赋值给成员变量
    requireNonNull(mapping, "mapping"); // 验证映射Map不为空

    final Map<String, Datatype> transformed = mapping.entrySet().stream() // 将输入的String类型映射转换为Datatype对象的映射
        .collect(Collectors.toMap(Map.Entry::getKey, e -> new Datatype(e.getValue()))); // 使用Stream流处理，将每个字段类型字符串转换为Datatype对象
    this.mapping = ImmutableMap.copyOf(transformed); // 创建转换后映射的不可变副本，确保线程安全和不可变性
  }

  /**
   * Returns ES schema for each field. Mapping is represented as field name
   * {@code foo.bar.qux} and type ({@code keyword}, {@code boolean},
   * {@code long}).
   * 返回每个字段的ES模式。映射表示为字段名{@code foo.bar.qux}和类型（{@code keyword}、{@code boolean}、{@code long}）。
   *
   * @return immutable mapping between field and ES type
   * 返回字段和ES类型之间的不可变映射
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html">Mapping Types</a>
   * 参见Elasticsearch映射类型文档
   */
  Map<String, Datatype> mapping() { // 方法：返回字段到数据类型的不可变映射
    return this.mapping; // 返回存储的不可变映射对象
  }

  /**
   * Used as special aggregation key for missing values (documents that are
   * missing a field).
   * 用作缺失值（缺少字段的文档）的特殊聚合键。
   *
   * <p>Buckets with that value are then converted to {@code null}s in flat
   * tabular format.
   * 具有该值的桶随后在扁平表格格式中被转换为{@code null}。
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-metrics-sum-aggregation.html">Missing Value</a>
   * 参见Elasticsearch缺失值文档
   */
  Optional<JsonNode> missingValueFor(String fieldName) { // 方法：获取指定字段的缺失值表示，用于聚合操作
    if (!mapping().containsKey(fieldName)) { // 检查指定字段是否存在于映射中
      final String message =
          String.format(Locale.ROOT,
              "Field %s not defined for %s", fieldName, index); // 如果字段不存在，创建错误消息
      throw new IllegalArgumentException(message); // 抛出非法参数异常，指示字段未定义
    }

    return mapping().get(fieldName).missingValue(); // 返回该字段类型的缺失值表示
  }

  String index() { // 方法：返回此映射所属的索引名称
    return this.index; // 返回索引名称
  }

  /**
   * Represents elastic data-type, like {@code long}, {@code keyword},
   * {@code date} etc.
   * 表示Elasticsearch数据类型，如{@code long}、{@code keyword}、{@code date}等。
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html">Mapping Types</a>
   * 参见Elasticsearch映射类型文档
   */
  static class Datatype { // 静态内部类Datatype：表示Elasticsearch的数据类型，如long、keyword、date等
    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance; // 静态常量：JsonNode工厂实例，用于创建各种类型的JsonNode对象

    // pre-cache missing values // 预缓存缺失值
    private static final Set<JsonNode> MISSING_VALUES = // 静态常量：预缓存的缺失值集合，用于快速判断某个JsonNode是否为缺失值
        Stream.of("string", // for ES2，包括ES2的string类型
            "text", "keyword", // 文本类型
            "date", "long", "integer", "double", "float") // 日期和数值类型
            .map(Datatype::missingValueForType) // 将类型名称转换为对应的缺失值JsonNode
            .collect(Collectors.toSet()); // 收集到Set中

    private final String name; // 成员变量：数据类型名称，如"text"、"integer"、"float"等
    private final JsonNode missingValue; // 成员变量：该类型对应的缺失值表示，用于聚合操作中表示SQL的null

    private Datatype(final String name) { // 构造方法：创建Datatype实例，接收类型名称
      this.name = requireNonNull(name, "name"); // 验证类型名称不为空，并赋值给成员变量
      this.missingValue = missingValueForType(name); // 根据类型名称计算并存储对应的缺失值
    }

    /**
     * Mapping between ES type and json value that represents
     * {@code missing value} during aggregations. This value can't be
     * {@code null} and should match type or the field (for ES long type it
     * also has to be json integer, for date it has to match date format or be
     * integer (millis epoch) etc.
     * ES类型和JSON值之间的映射，表示聚合期间的缺失值。此值不能为{@code null}，并且应该匹配字段类型（对于ES long类型，它也必须是json整数，对于date，它必须匹配日期格式或是整数（毫秒纪元）等。
     *
     * <p>It is used for terms aggregations to represent SQL {@code null}.
     * 它用于terms聚合以表示SQL {@code null}。
     *
     * @param name name of the type ({@code long}, {@code keyword} ...)
     * 类型名称（{@code long}、{@code keyword}等）
     *
     * @return json that will be used in elastic search terms aggregation for
     * missing value
     * 将在Elasticsearch terms聚合中用于缺失值的json
     *
     * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html#_missing_value_13">Missing Value</a>
     * 参见Elasticsearch缺失值文档
     */
    private static @Nullable JsonNode missingValueForType(String name) { // 静态方法：根据类型名称返回对应的缺失值JsonNode
      switch (name) { // 根据类型名称进行分支处理
      case "string": // for ES2，处理ES2版本的string类型
      case "text": // 处理text类型
      case "keyword": // 处理keyword类型
        return FACTORY.textNode("__MISSING__"); // 对于文本类型，返回特殊字符串"__MISSING__"作为缺失值
      case "long": // 处理long类型
        return FACTORY.numberNode(Long.MIN_VALUE); // 对于long类型，返回Long的最小值作为缺失值
      case "integer": // 处理integer类型
        return FACTORY.numberNode(Integer.MIN_VALUE); // 对于integer类型，返回Integer的最小值作为缺失值
      case "short": // 处理short类型
        return FACTORY.numberNode(Short.MIN_VALUE); // 对于short类型，返回Short的最小值作为缺失值
      case "double": // 处理double类型
        return FACTORY.numberNode(Double.MIN_VALUE); // 对于double类型，返回Double的最小值作为缺失值
      case "float": // 处理float类型
        return FACTORY.numberNode(Float.MIN_VALUE); // 对于float类型，返回Float的最小值作为缺失值
      case "date": // 处理date类型
        // sentinel for missing dates: 9999-12-31 // 缺失日期的哨兵值：9999-12-31
        final long millisEpoch = LocalDate.of(9999, 12, 31) // 创建日期9999-12-31作为缺失日期
            .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(); // 转换为UTC时区的毫秒纪元
        // by default elastic returns dates as longs // 默认情况下，Elasticsearch将日期作为long返回
        return FACTORY.numberNode(millisEpoch); // 返回9999-12-31的毫秒纪元作为日期类型的缺失值
      default: // 默认分支，处理未知类型
        break; // 跳出switch语句
      }

      // this is unknown type // 这是未知类型
      return null; // 对于未知类型，返回null表示没有预定义的缺失值
    }

    /**
     * Name of the type: {@code text}, {@code integer}, {@code float} etc.
     * 类型名称：{@code text}、{@code integer}、{@code float}等。
     */
    String name() { // 方法：返回数据类型名称
      return this.name; // 返回类型名称
    }

    Optional<JsonNode> missingValue() { // 方法：返回该类型的缺失值，包装在Optional中
      return Optional.ofNullable(missingValue); // 返回可能为null的缺失值的Optional包装
    }

    static boolean isMissingValue(JsonNode node) { // 静态方法：判断给定的JsonNode是否为预定义的缺失值之一
      return MISSING_VALUES.contains(node); // 检查节点是否在预缓存的缺失值集合中
    }
  } // 结束Datatype类定义

} // 结束ElasticsearchMapping类定义
