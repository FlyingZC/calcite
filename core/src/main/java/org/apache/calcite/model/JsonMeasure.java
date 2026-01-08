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
package org.apache.calcite.model; // 定义包名，该类属于 org.apache.calcite.model 包，用于存放 Calcite 模型相关的类

import com.fasterxml.jackson.annotation.JsonCreator; // 导入 Jackson 注解，用于标记 JSON 反序列化时使用的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入 Jackson 注解，用于标记 JSON 属性与 Java 字段的映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 注解，用于标记可能为 null 的字段或参数

import static java.util.Objects.requireNonNull; // 导入 Objects 类的静态方法，用于非空检查

/**
 * An aggregate function applied to a column (or columns) of a lattice.
 * 这是一个应用于 lattice（立方体）的一个或多个列上的聚合函数
 *
 * <p>Occurs in a {@link org.apache.calcite.model.JsonTile},
 * 该类出现在 JsonTile（JSON 瓦片）中
 * and there is a default list in
 * 并且在 JsonLattice（JSON 立方体）中有一个默认列表
 * {@link org.apache.calcite.model.JsonLattice}.
 *
 * @see JsonRoot Description of schema elements
 * @see JsonRoot 参考 JsonRoot 类了解模式元素的描述
 */
public class JsonMeasure { // 定义 JsonMeasure 类，表示 JSON 格式的度量（measure），用于描述聚合函数
  /** The name of an aggregate function.
   * 聚合函数的名称
   *
   * <p>Required. Usually {@code count}, {@code sum},
   * 必需字段。通常是 count（计数）、sum（求和）
   * {@code min}, {@code max}.
   * min（最小值）、max（最大值）等聚合函数
   */
  public final String agg; // 定义公共常量字段 agg，存储聚合函数的名称，如 "COUNT"、"SUM"、"AVG"、"MIN"、"MAX" 等

  /** Arguments to the measure.
   * 度量的参数，即聚合函数作用的列
   *
   * <p>Valid values are:
   * 有效的值包括：
   * <ul>
   *   <li>Not specified: no arguments</li>
   *   未指定：表示没有参数，适用于 COUNT(*) 这种不需要列名的聚合
   *   <li>null: no arguments</li>
   *   null 值：表示没有参数，同样适用于不需要列名的聚合函数
   *   <li>Empty list: no arguments</li>
   *   空列表：表示没有参数，用于明确指定无参数的情况
   *   <li>String: single argument, the name of a lattice column</li>
   *   字符串：单个参数，表示 lattice 中某列的名称，如 "salary"、"amount" 等
   *   <li>List: multiple arguments, each a column name</li>
   *   列表：多个参数，每个参数都是列名，用于需要多列的聚合函数，如某些自定义聚合函数
   * </ul>
   *
   * <p>Unlike lattice dimensions, measures can not be specified in qualified
   * 与 lattice 维度不同，度量不能使用限定格式
   * format, {@code ["table", "column"]}. When you define a lattice, make sure
   * 即不能写成 ["table", "column"] 这种表名加列名的格式。当你定义一个 lattice 时，确保
   * that each column you intend to use as a measure has a unique name within
   * 你打算用作度量的每一列在 lattice 内都有唯一的名称
   * the lattice (using "{@code AS alias}" if necessary).
   * （如果需要，可以使用 "AS alias" 别名来确保唯一性）
   */
  public final @Nullable Object args; // 定义公共常量字段 args，存储聚合函数的参数，可以是 null、字符串或列表，使用 @Nullable 注解表示可以为 null

  @JsonCreator // 标记该构造方法为 JSON 反序列化时使用的构造方法，Jackson 会调用此构造方法来创建对象
  public JsonMeasure( // 定义构造方法，用于创建 JsonMeasure 对象
      @JsonProperty(value = "agg", required = true) String agg, // 参数 agg：聚合函数名称，使用 @JsonProperty 注解指定 JSON 属性名为 "agg"，required = true 表示该属性是必需的
      @JsonProperty("args") @Nullable Object args) { // 参数 args：聚合函数的参数，使用 @JsonProperty 注解指定 JSON 属性名为 "args"，@Nullable 注解表示该参数可以为 null
    this.agg = requireNonNull(agg, "agg"); // 使用 requireNonNull 方法检查 agg 参数是否为 null，如果为 null 则抛出 NullPointerException，错误信息为 "agg"
    this.args = args; // 将 args 参数赋值给实例变量 args，args 可以为 null
  }

  public void accept(ModelHandler modelHandler) { // 定义 accept 方法，用于访问者模式，接受一个 ModelHandler 访问者对象
    modelHandler.visit(this); // 调用 ModelHandler 的 visit 方法，将当前 JsonMeasure 对象传递给访问者进行处理，这是访问者模式的标准用法
  }
}
