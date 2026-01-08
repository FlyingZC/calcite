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
// Apache许可证声明，说明代码的版权归属和使用许可
package org.apache.calcite.model; // 声明包名，该类位于org.apache.calcite.model包中，属于Calcite框架的模型层

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson注解，用于标记JSON反序列化的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson注解，用于标记JSON属性映射
import com.google.common.collect.ImmutableList; // 导入Guava库的不可变列表类，用于创建不可修改的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查框架的注解，标记可能为null的参数

import java.util.ArrayList; // 导入Java标准库的动态数组类，用于存储可变长度的元素列表
import java.util.List; // 导入Java标准库的列表接口，定义列表的通用行为

/**
 * Materialized view within a {@link org.apache.calcite.model.JsonLattice}. // 类的Javadoc注释：这是JsonLattice（格）中的物化视图
 *
 * <p>A tile is defined in terms of its dimensionality (the grouping columns, // Tile（瓦片/分片）通过其维度（分组列）来定义
 * drawn from the lattice) and measures (aggregate functions applied to // 这些维度从格（lattice）中提取，度量（measures）是应用于格列的聚合函数
 * lattice columns). // 度量是聚合函数，对格中的列进行聚合计算
 *
 * <p>Occurs within {@link JsonLattice#tiles}. // 出现在JsonLattice的tiles集合中
 *
 * @see JsonRoot Description of schema elements // 参见JsonRoot以了解架构元素的描述
 */
public class JsonTile { // 定义JsonTile类，表示一个物化视图瓦片，用于在格（lattice）中预计算和存储聚合结果
  /** List of dimensions that define this tile. // 成员变量注释：定义此瓦片的维度列表
   *
   * <p>Each dimension is a column from the lattice. The list of dimensions // 每个维度都是格中的一列。维度列表定义了聚合的级别
   * defines the level of aggregation, like a {@code GROUP BY} clause. // 类似于SQL中的GROUP BY子句，决定了数据的分组粒度
   *
   * <p>Required, but may be empty. Each element is either a string // 必需字段，但可以为空列表。每个元素要么是字符串
   * (the unique label of the column within the lattice) // （格中列的唯一标签）
   * or a string list (a pair consisting of a table alias and a column name). // 要么是字符串列表（由表别名和列名组成的对）
   */
  public final List dimensions = new ArrayList(); // 维度列表，使用ArrayList实现，存储分组列的定义，可以是字符串或字符串列表

  /** List of measures in this tile. // 成员变量注释：此瓦片中的度量列表
   *
   * <p>If not specified, uses {@link JsonLattice#defaultMeasures}. // 如果未指定，则使用JsonLattice的defaultMeasures默认度量
   */
  public final List<JsonMeasure> measures; // 度量列表，存储JsonMeasure对象，每个对象定义了一个聚合函数及其参数

  @JsonCreator // Jackson注解，标记此构造方法为JSON反序列化时使用的构造方法
  public JsonTile(@JsonProperty("measures") @Nullable List<JsonMeasure> measures) { // 构造方法：接受可选的measures参数，JsonProperty注解指定JSON属性名"measures"，Nullable注解表示参数可能为null
    this.measures = measures == null // 如果传入的measures参数为null，则使用默认度量
        ? ImmutableList.of(new JsonMeasure("count", null)) : measures; // 创建一个包含count聚合函数的不可变列表作为默认度量，count函数不需要参数；否则使用传入的measures列表
  }

  public void accept(ModelHandler handler) { // 接受方法：访问者模式的一部分，接受ModelHandler访问者对象
    handler.visit(this); // 调用handler的visit方法，将当前JsonTile对象传递给访问者进行处理，实现模型遍历和处理逻辑
  }
}
