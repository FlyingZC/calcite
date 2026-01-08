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
package org.apache.calcite.model;  // 声明包名，该类属于org.apache.calcite.model包，用于模型定义

import com.fasterxml.jackson.annotation.JsonCreator;  // 导入Jackson注解，标记JSON反序列化时的构造方法
import com.fasterxml.jackson.annotation.JsonProperty;  // 导入Jackson注解，标记JSON属性映射
import com.google.common.collect.ImmutableList;  // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入注解，标记可能为null的类型

import java.util.ArrayList;  // 导入Java标准库的动态数组类
import java.util.List;  // 导入Java标准库的列表接口
import java.util.StringJoiner;  // 导入Java标准库的字符串连接工具类

import static java.util.Objects.requireNonNull;  // 导入静态方法，用于对象非空检查

/**
 * Element that describes a star schema and provides a framework for defining,
 * recognizing, and recommending materialized views at various levels of
 * aggregation.
 * 描述星型模式的元素，并提供一个框架来定义、识别和推荐各种聚合级别的物化视图
 *
 * <p>Occurs within {@link JsonSchema#lattices}.
 * 该元素出现在JsonSchema的lattices字段中
 *
 * @see JsonRoot Description of schema elements
 * 参见JsonRoot了解schema元素的描述
 */
public class JsonLattice {  // 定义JsonLattice类，用于表示JSON格式的Lattice（格）模型，Lattice是Calcite中用于物化视图优化的核心概念
  /** The name of this lattice.
   * 这个Lattice的名称
   *
   * <p>Required.
   * 必填字段
   */
  public final String name;  // Lattice的名称，使用final修饰表示不可变，public表示可以直接访问

  /** SQL query that defines the lattice.
   * 定义这个Lattice的SQL查询语句
   *
   * <p>Must be a string or a list of strings (which are concatenated into a
   * multi-line SQL string, separated by newlines).
   * 必须是一个字符串或字符串列表（列表会被连接成一个多行SQL字符串，用换行符分隔）
   *
   * <p>The structure of the SQL statement, and in particular the order of
   * items in the FROM clause, defines the fact table, dimension tables, and
   * join paths for this lattice.
   * SQL语句的结构，特别是FROM子句中项的顺序，定义了这个Lattice的事实表、维度表和连接路径
   */
  public final Object sql;  // SQL查询，使用Object类型是因为可以是String或List<String>

  /** Whether to materialize tiles on demand as queries are executed.
   * 是否在查询执行时按需物化tiles（tiles是Lattice中的物化聚合视图）
   *
   * <p>Optional; default is true.
   * 可选字段，默认值为true
   */
  public final boolean auto;  // 自动物化标志，true表示在查询时自动创建物化视图

  /** Whether to use an optimization algorithm to suggest and populate an
   * initial set of tiles.
   * 是否使用优化算法来建议并填充初始的tiles集合
   *
   * <p>Optional; default is false.
   * 可选字段，默认值为false
   */
  public final boolean algorithm;  // 算法标志，true表示使用优化算法自动推荐tiles

  /** Maximum time (in milliseconds) to run the algorithm.
   * 运行算法的最大时间（毫秒）
   *
   * <p>Optional; default is -1, meaning no timeout.
   * 可选字段，默认值为-1，表示没有超时限制
   *
   * <p>When the timeout is reached, Calcite uses the best result that has
   * been obtained so far.
   * 当达到超时时间时，Calcite使用到目前为止获得的最佳结果
   */
  public final long algorithmMaxMillis;  // 算法最大运行时间，-1表示无限制

  /** Estimated number of rows.
   * 估计的行数
   *
   * <p>If null, Calcite will a query to find the real value.
   * 如果为null，Calcite会执行查询来获取真实值
   */
  public final @Nullable Double rowCountEstimate;  // 行数估计值，可为null

  /** Name of a class that provides estimates of the number of distinct values
   * in each column.
   * 提供每列不同值数量估计的类名
   *
   * <p>The class must implement the
   * {@link org.apache.calcite.materialize.LatticeStatisticProvider} interface.
   * 该类必须实现LatticeStatisticProvider接口
   *
   * <p>Or, you can use a class name plus a static field, for example
   * "org.apache.calcite.materialize.Lattices#CACHING_SQL_STATISTIC_PROVIDER".
   * 或者，可以使用类名加静态字段的形式，例如"org.apache.calcite.materialize.Lattices#CACHING_SQL_STATISTIC_PROVIDER"
   *
   * <p>If not set, Calcite will generate and execute a SQL query to find the
   * real value, and cache the results.
   * 如果未设置，Calcite会生成并执行SQL查询来获取真实值，并缓存结果
   */
  public final @Nullable String statisticProvider;  // 统计提供者类名，可为null

  /** List of materialized aggregates to create up front.
   * 预先创建的物化聚合列表
   */
  public final List<JsonTile> tiles = new ArrayList<>();  // tiles列表，存储JsonTile对象，初始为空ArrayList

  /** List of measures that a tile should have by default.
   * tile默认应该包含的度量列表
   *
   * <p>A tile can define its own measures, including measures not in this list.
   * 一个tile可以定义自己的度量，包括不在此列表中的度量
   *
   * <p>Optional. The default list is just "count(*)".
   * 可选字段，默认列表只有"count(*)"
   */
  public final List<JsonMeasure> defaultMeasures;  // 默认度量列表，存储JsonMeasure对象

  @JsonCreator  // Jackson注解，标记该构造方法用于JSON反序列化
  public JsonLattice(  // JsonLattice构造方法，用于创建Lattice实例
      @JsonProperty(value = "name", required = true) String name,  // 从JSON中读取name属性，必填
      @JsonProperty(value = "sql", required = true) Object sql,  // 从JSON中读取sql属性，必填
      @JsonProperty("auto") @Nullable Boolean auto,  // 从JSON中读取auto属性，可选
      @JsonProperty("algorithm") @Nullable Boolean algorithm,  // 从JSON中读取algorithm属性，可选
      @JsonProperty("algorithmMaxMillis") @Nullable Long algorithmMaxMillis,  // 从JSON中读取algorithmMaxMillis属性，可选
      @JsonProperty("rowCountEstimate") @Nullable Double rowCountEstimate,  // 从JSON中读取rowCountEstimate属性，可选
      @JsonProperty("statisticProvider") @Nullable String statisticProvider,  // 从JSON中读取statisticProvider属性，可选
      @JsonProperty("defaultMeasures") @Nullable List<JsonMeasure> defaultMeasures) {  // 从JSON中读取defaultMeasures属性，可选
    this.name = requireNonNull(name, "name");  // 设置name字段，要求非空
    this.sql = requireNonNull(sql, "sql");  // 设置sql字段，要求非空
    this.auto = auto == null || auto;  // 设置auto字段，如果为null则默认为true
    this.algorithm = algorithm != null && algorithm;  // 设置algorithm字段，如果为null则默认为false
    this.algorithmMaxMillis = algorithmMaxMillis == null ? -1 : algorithmMaxMillis;  // 设置algorithmMaxMillis字段，如果为null则默认为-1
    this.rowCountEstimate = rowCountEstimate;  // 设置rowCountEstimate字段
    this.statisticProvider = statisticProvider;  // 设置statisticProvider字段
    this.defaultMeasures = defaultMeasures == null  // 设置defaultMeasures字段
        ? ImmutableList.of(new JsonMeasure("count", null)) : defaultMeasures;  // 如果为null，则创建包含count度量的默认列表
  }

  public void accept(ModelHandler handler) {  // 接受访问者模式，让ModelHandler处理这个Lattice
    handler.visit(this);  // 调用ModelHandler的visit方法，传入当前JsonLattice对象
  }

  @Override public String toString() {  // 重写toString方法，返回对象的字符串表示
    return "JsonLattice(name=" + name + ", sql=" + getSql() + ")";  // 返回包含name和sql的字符串
  }

  /** Returns the SQL query as a string, concatenating a list of lines if
   * necessary.
   * 返回SQL查询字符串，如果需要则连接列表中的行
   */
  public String getSql() {  // 获取SQL查询字符串的方法
    return toString(sql);  // 调用静态toString方法将sql对象转换为字符串
  }

  /** Converts a string or a list of strings to a string. The list notation
   * is a convenient way of writing long multi-line strings in JSON.
   * 将字符串或字符串列表转换为字符串。列表表示法是在JSON中编写长多行字符串的便捷方式
   */
  static String toString(Object o) {  // 静态方法，将对象转换为字符串
    requireNonNull(o, "argument must not be null");  // 检查参数非空
    //noinspection unchecked  // 忽略未检查的类型转换警告
    return o instanceof String ? (String) o  // 如果是String类型，直接返回
        : concatenate((List<?>) o);  // 如果是List类型，调用concatenate方法连接
  }

  /** Converts a list of strings into a multi-line string.
   * 将字符串列表转换为多行字符串
   */
  private static String concatenate(List<?> list) {  // 私有静态方法，连接列表中的字符串
    final StringJoiner buf = new StringJoiner("\n", "", "\n");  // 创建StringJoiner，用换行符连接，前后各加一个换行符
    for (Object o : list) {  // 遍历列表中的每个元素
      if (!(o instanceof String)) {  // 检查元素是否为String类型
        throw new RuntimeException(  // 如果不是String，抛出运行时异常
            "each element of a string list must be a string; found: " + o);  // 异常信息说明必须是字符串
      }
      buf.add((String) o);  // 将字符串添加到StringJoiner中
    }
    return buf.toString();  // 返回连接后的字符串
  }

  public void visitChildren(ModelHandler modelHandler) {  // 访问子节点方法，让ModelHandler遍历处理所有子元素
    for (JsonMeasure jsonMeasure : defaultMeasures) {  // 遍历defaultMeasures列表中的每个JsonMeasure
      jsonMeasure.accept(modelHandler);  // 让每个JsonMeasure接受ModelHandler的访问
    }
    for (JsonTile jsonTile : tiles) {  // 遍历tiles列表中的每个JsonTile
      jsonTile.accept(modelHandler);  // 让每个JsonTile接受ModelHandler的访问
    }
  }
}  // JsonLattice类结束
