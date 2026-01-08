/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，允许在ASF许可证下使用本代码
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的NOTICE文件以获取版权所有权信息
 * this work for additional information regarding copyright ownership.  // ASF根据Apache许可证2.0版将此文件许可给您
 * The ASF licenses this file to you under the Apache License, Version 2.0 // 您只能在遵守许可证的情况下使用此文件
 * (the "License"); you may not use this file except in compliance with // 您可以在以下网址获取许可证副本
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则根据许可证分发的软件是按"原样"分发的
 * distributed under the License is distributed on an "AS IS" BASIS, // 不附带任何明示或暗示的保证或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 有关许可证下的特定语言管理权限和限制，请参阅许可证
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.model; // 声明本类属于org.apache.calcite.model包，这是Calcite模型定义的核心包

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson注解，用于标记JSON反序列化的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson注解，用于标记JSON属性映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解，用于标记可空类型

import java.util.ArrayList; // 导入Java集合框架的ArrayList类，用于存储可变长度的列表
import java.util.List; // 导入Java集合框架的List接口，用于定义列表类型

import static java.util.Objects.requireNonNull; // 导入Objects工具类的静态方法，用于非空检查

/**
 * Root schema element. // 根模式元素，这是Calcite JSON模型的根节点类
 *
 * <p>A POJO with fields of {@link Boolean}, {@link String}, {@link ArrayList}, // 这是一个纯Java对象（POJO），包含Boolean、String、ArrayList等类型的字段
 * {@link java.util.LinkedHashMap LinkedHashMap}, per Jackson simple data // 以及LinkedHashMap，符合Jackson简单数据绑定规范
 * binding.
 *
 * <p>Schema structure is as follows: // 模式结构如下所示：
 *
 * <!-- CHECKSTYLE: OFF --> // 禁用Checkstyle检查，因为下面的代码块格式特殊
 * <pre>{@code Root} // 根节点，即JsonRoot类
 *   {@link JsonSchema} (in collection {@link JsonRoot#schemas schemas}) // JsonSchema对象，存储在schemas集合中，代表一个模式
 *     {@link JsonType} (in collection {@link JsonMapSchema#types types}) // JsonType对象，存储在types集合中，代表自定义类型定义
 *     {@link JsonTable} (in collection {@link JsonMapSchema#tables tables}) // JsonTable对象，存储在tables集合中，代表表定义
 *       {@link JsonColumn} (in collection {@link JsonTable#columns columns}) // JsonColumn对象，存储在columns集合中，代表表的列定义
 *       {@link JsonStream} (in field {@link JsonTable#stream stream}) // JsonStream对象，存储在stream字段中，代表流式数据源
 *     {@link JsonView} // JsonView对象，代表视图定义
 *     {@link JsonFunction} (in collection {@link JsonMapSchema#functions functions}) // JsonFunction对象，存储在functions集合中，代表自定义函数
 *     {@link JsonLattice} (in collection {@link JsonSchema#lattices lattices}) // JsonLattice对象，存储在lattices集合中，代表立方体定义
 *       {@link JsonMeasure} (in collection {@link JsonLattice#defaultMeasures defaultMeasures}) // JsonMeasure对象，存储在defaultMeasures集合中，代表默认度量指标
 *       {@link JsonTile} (in collection {@link JsonLattice#tiles tiles}) // JsonTile对象，存储在tiles集合中，代表立方体的切片
 *         {@link JsonMeasure} (in collection {@link JsonTile#measures measures}) // JsonMeasure对象，存储在measures集合中，代表切片的度量指标
 *     {@link JsonMaterialization} (in collection {@link JsonSchema#materializations materializations}) // JsonMaterialization对象，存储在materializations集合中，代表物化视图
 *   {@link JsonType} (in collection {@link JsonRoot#types types}) // JsonType对象，存储在types集合中，代表全局类型定义
 * </pre>
 * <!-- CHECKSTYLE: ON --> // 重新启用Checkstyle检查
 *
 * <p>See the <a href="https://calcite.apache.org/docs/model.html">JSON // 参考Calcite官方文档中的JSON模型说明部分，了解更多详细信息
 * model reference</a>.
 */
public class JsonRoot { // 定义JsonRoot类，这是Calcite JSON模型的根节点类，用于从JSON文件加载模型配置
  /** Schema model version number. Required, must have value "1.0". */ // 模式模型版本号，必填字段，值必须为"1.0"
  public final String version; // 版本号字段，使用final修饰表示一旦初始化就不能修改，public修饰符表示可以从外部访问

  /** Name of the schema that will become the default schema for connections // 将成为使用此模型的Calcite连接的默认模式的名称
   * to Calcite that use this model.
   *
   * <p>Optional, case-sensitive. If specified, there must be a schema in this // 可选字段，区分大小写。如果指定了，则在此模型中必须存在同名的模式
   * model with this name.
   */
  public final @Nullable String defaultSchema; // 默认模式名称字段，@Nullable注解表示该字段可以为null，final修饰表示不可变

  /** List of schema elements. // 模式元素列表，用于存储所有的模式定义
   *
   * <p>The list may be empty. // 该列表可以为空列表
   */
  public final List<JsonSchema> schemas = new ArrayList<>(); // schemas字段，存储JsonSchema对象的列表，使用ArrayList实现，初始化为空列表

  /** List of types in the root schema. // 根模式中的类型列表，用于存储全局类型定义
   *
   * <p>Such types global, that is, shared by all schemas in the model. // 这些类型是全局的，即被模型中的所有模式共享
   *
   * <p>The list may be empty. // 该列表可以为空列表
   */
  public final List<JsonType> types = new ArrayList<>(); // types字段，存储JsonType对象的列表，使用ArrayList实现，初始化为空列表

  @JsonCreator // Jackson注解，标记此构造方法为JSON反序列化时使用的构造方法
  public JsonRoot( // JsonRoot构造方法，用于从JSON数据创建JsonRoot对象
      @JsonProperty(value = "version", required = true) String version, // version参数，@JsonProperty注解指定JSON字段名为"version"，required=true表示此字段必填
      @JsonProperty("defaultSchema") @Nullable String defaultSchema) { // defaultSchema参数，@JsonProperty注解指定JSON字段名为"defaultSchema"，@Nullable表示可为null
    this.version = requireNonNull(version, "version"); // 将version参数赋值给成员变量，并使用requireNonNull进行非空检查，如果为null则抛出NullPointerException
    this.defaultSchema = defaultSchema; // 将defaultSchema参数赋值给成员变量，允许为null
  } // 构造方法结束
} // 类定义结束
