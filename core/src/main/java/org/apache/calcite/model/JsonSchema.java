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
package org.apache.calcite.model; // 定义包名，位于org.apache.calcite.model包下

import com.fasterxml.jackson.annotation.JsonSubTypes; // 导入Jackson的子类型注解，用于JSON反序列化时的多态处理

import com.fasterxml.jackson.annotation.JsonTypeInfo; // 导入Jackson的类型信息注解，用于JSON序列化和反序列化时处理多态

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的字段、参数或返回值

import java.util.ArrayList; // 导入ArrayList类，用于动态数组实现
import java.util.List; // 导入List接口，用于列表集合操作

/**
 * Schema schema element.
 * Schema模式元素类，用于在JSON模型中定义Calcite的Schema结构
 *
 * <p>Occurs within {@link JsonRoot#schemas}.
 * 该类作为元素出现在JsonRoot的schemas列表中
 *
 * @see JsonRoot Description of schema elements
 * 参见JsonRoot类以了解schema元素的完整描述
 */
@JsonTypeInfo( // Jackson多态类型处理注解，用于在JSON序列化和反序列化时识别具体的子类型
    use = JsonTypeInfo.Id.NAME, // 使用名称作为类型标识符，即通过JSON中的"type"字段值来决定反序列化成哪个子类
    property = "type", // 指定JSON中用于标识类型的属性名为"type"
    defaultImpl = JsonMapSchema.class) // 如果JSON中没有指定类型，默认使用JsonMapSchema类作为实现
@JsonSubTypes({ // 定义所有可能的子类型及其对应的类型名称
    @JsonSubTypes.Type(value = JsonMapSchema.class, name = "map"), // 当type="map"时，反序列化为JsonMapSchema类，表示基于Map的Schema
    @JsonSubTypes.Type(value = JsonJdbcSchema.class, name = "jdbc"), // 当type="jdbc"时，反序列化为JsonJdbcSchema类，表示基于JDBC的Schema
    @JsonSubTypes.Type(value = JsonCustomSchema.class, name = "custom") }) // 当type="custom"时，反序列化为JsonCustomSchema类，表示自定义的Schema
public abstract class JsonSchema { // 抽象类，定义了所有Schema类型的公共属性和行为，不能直接实例化
  /** Name of the schema.
   * Schema的名称字段，用于唯一标识这个Schema
   *
   * <p>Required.
   * 该字段是必需的，必须在JSON配置中提供
   *
   * @see JsonRoot#defaultSchema
   * 参见JsonRoot的defaultSchema字段，了解如何设置默认Schema
   */
  public final String name; // 公共不可变字段，存储Schema的名称，final表示初始化后不能修改

  /** SQL path that is used to resolve functions used in this schema.
   * SQL路径字段，用于解析在此Schema中使用的函数
   *
   * <p>May be null, or a list, each element of which is a string or a
   * string-list.
   * 该字段可能为null，也可能是一个列表，列表中的每个元素可以是字符串或字符串列表
   *
   * <p>For example,
   * 例如：
   *
   * <blockquote><pre>path: [ ['usr', 'lib'], 'lib' ]</pre></blockquote>
   * 示例配置：path: [ ['usr', 'lib'], 'lib' ]
   *
   * <p>declares a path with two elements: the schema '/usr/lib' and the schema
   * '/lib'. Most schemas are at the top level, and for these you can use a
   * string.
   * 声明了一个包含两个元素的路径：'/usr/lib' schema和'/lib' schema。大多数Schema位于顶层，对于这些可以使用字符串
   */
  public final @Nullable List<Object> path; // 公共不可变字段，存储函数解析路径，@Nullable表示可以为null，List<Object>支持字符串和字符串列表

  /**
   * List of tables in this schema that are materializations of queries.
   * 物化化表列表字段，存储本Schema中作为查询物化结果的表
   *
   * <p>The list may be empty.
   * 该列表可能为空，表示没有物化表
   */
  public final List<JsonMaterialization> materializations = new ArrayList<>(); // 公共不可变字段，存储物化表配置列表，使用ArrayList初始化

  public final List<JsonLattice> lattices = new ArrayList<>(); // 公共不可变字段，存储Lattice（格）配置列表，Lattice是Calcite的优化结构，用于物化视图和查询优化

  /** Whether to cache metadata (tables, functions and sub-schemas) generated
   * by this schema. Default value is {@code true}.
   * 是否缓存此Schema生成的元数据（表、函数和子Schema）。默认值为true
   *
   * <p>If {@code false}, Calcite will go back to the schema each time it needs
   * metadata, for example, each time it needs a list of tables in order to
   * validate a query against the schema.
   * 如果为false，Calcite每次需要元数据时都会回到Schema获取，例如每次需要表列表来验证查询时
   *
   * <p>If {@code true}, Calcite will cache the metadata the first time it reads
   * it. This can lead to better performance, especially if name-matching is
   * case-insensitive
   * (see {@link org.apache.calcite.config.Lex#caseSensitive}).
   * 如果为true，Calcite会在第一次读取时缓存元数据。这可以提高性能，特别是在名称匹配不区分大小写的情况下
   *
   * <p>Tables, functions and sub-schemas explicitly created in a schema are
   * not affected by this caching mechanism. They always appear in the schema
   * immediately, and are never flushed.
   * 在Schema中显式创建的表、函数和子Schema不受此缓存机制影响。它们会立即出现在Schema中，永远不会被刷新
   */
  public final @Nullable Boolean cache; // 公共不可变字段，控制是否缓存元数据，@Nullable表示可以为null，Boolean包装类支持null值

  /** Whether to create lattices in this schema based on queries occurring in
   * other schemas. Default value is {@code false}. */
  public final @Nullable Boolean autoLattice; // 公共不可变字段，控制是否基于其他Schema中的查询自动创建Lattice，默认为false，@Nullable表示可以为null

  protected JsonSchema(String name, @Nullable List<Object> path, @Nullable Boolean cache, // 受保护的构造方法，用于初始化JsonSchema对象，只能被子类调用
      @Nullable Boolean autoLattice) { // 构造方法参数：autoLattice控制是否自动创建Lattice
    this.name = name; // 将传入的name参数赋值给实例变量name
    this.path = path; // 将传入的path参数赋值给实例变量path
    this.cache = cache; // 将传入的cache参数赋值给实例变量cache
    this.autoLattice = autoLattice; // 将传入的autoLattice参数赋值给实例变量autoLattice
  }

  public abstract void accept(ModelHandler handler); // 抽象方法，接受访问者模式的处理器，用于处理Schema对象的具体逻辑，子类必须实现此方法

  public void visitChildren(ModelHandler modelHandler) { // 公共方法，访问此Schema的所有子元素（Lattice和物化表），使用访问者模式
    for (JsonLattice jsonLattice : lattices) { // 遍历所有的Lattice配置
      jsonLattice.accept(modelHandler); // 让每个Lattice接受处理器访问，调用其accept方法
    }
    for (JsonMaterialization jsonMaterialization : materializations) { // 遍历所有的物化表配置
      jsonMaterialization.accept(modelHandler); // 让每个物化表接受处理器访问，调用其accept方法
    }
  }

  /** Built-in schema types.
   * 内置Schema类型枚举，定义了Calcite支持的三种Schema类型
   */
  public enum Type { // 公共枚举，定义Schema的类型
    MAP, // MAP类型，表示基于内存Map的Schema，数据存储在内存的Map结构中
    JDBC, // JDBC类型，表示基于JDBC连接的Schema，数据存储在外部关系型数据库中
    CUSTOM // CUSTOM类型，表示自定义的Schema，用户可以实现自定义的Schema逻辑
  }
}
