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
package org.apache.calcite.model; // 声明包名，表示这个类属于org.apache.calcite.model包，该包包含了Calcite框架中用于表示JSON模型的各种类

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson注解，用于标记构造函数，表示Jackson在反序列化JSON时应该使用这个构造函数
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson注解，用于标记属性，表示JSON字段与Java属性之间的映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解，用于标记参数可能为null，帮助进行静态空值检查

import java.util.ArrayList; // 导入ArrayList类，用于创建动态数组列表
import java.util.List; // 导入List接口，表示有序的集合

/**
 * JSON object representing a schema whose tables are explicitly specified.
 * // 这个类是一个JSON对象，用于表示一个schema（模式/架构），其中的表是显式指定的
 *
 * <p>Like the base class {@link JsonSchema},
 * // 它继承自JsonSchema基类，具有基类的所有属性和行为
 * occurs within {@link JsonRoot#schemas}.
 * // 这个类的实例会出现在JsonRoot的schemas字段中，作为根模型的一部分
 *
 * @see JsonRoot Description of JSON schema elements
 * // 参见JsonRoot类，了解JSON schema元素的详细描述
 */
public class JsonMapSchema extends JsonSchema { // 定义JsonMapSchema类，继承自JsonSchema，表示一个显式指定表的JSON schema
  /** Tables in this schema.
   * // 这个成员变量用于存储该schema中包含的所有表
   *
   * <p>The list may be empty.
   * // 这个列表可能为空，表示该schema中没有定义任何表
   */
  public final List<JsonTable> tables = new ArrayList<>(); // 定义一个公共的final列表，用于存储JsonTable对象，初始化为空的ArrayList

  /** Types in this schema.
   * // 这个成员变量用于存储该schema中定义的所有自定义类型
   *
   * <p>The list may be empty.
   * // 这个列表可能为空，表示该schema中没有定义任何自定义类型
   */
  public final List<JsonType> types = new ArrayList<>(); // 定义一个公共的final列表，用于存储JsonType对象，初始化为空的ArrayList

  /** Functions in this schema.
   * // 这个成员变量用于存储该schema中定义的所有函数
   *
   * <p>The list may be empty.
   * // 这个列表可能为空，表示该schema中没有定义任何函数
   */
  public final List<JsonFunction> functions = new ArrayList<>(); // 定义一个公共的final列表，用于存储JsonFunction对象，初始化为空的ArrayList

  @JsonCreator // 使用Jackson注解标记构造函数，表示这是JSON反序列化时使用的构造函数
  public JsonMapSchema( // 定义构造函数，用于创建JsonMapSchema实例
      @JsonProperty(value = "name", required = true) String name, // 使用JsonProperty注解标记name参数，表示从JSON的"name"字段读取，且该字段是必需的，参数名为name，类型为String
      @JsonProperty("path") @Nullable List<Object> path, // 使用JsonProperty注解标记path参数，表示从JSON的"path"字段读取，该字段可选，参数可能为null，类型为List<Object>
      @JsonProperty("cache") @Nullable Boolean cache, // 使用JsonProperty注解标记cache参数，表示从JSON的"cache"字段读取，该字段可选，参数可能为null，类型为Boolean，用于控制是否缓存
      @JsonProperty("autoLattice") @Nullable Boolean autoLattice) { // 使用JsonProperty注解标记autoLattice参数，表示从JSON的"autoLattice"字段读取，该字段可选，参数可能为null，类型为Boolean，用于控制是否自动创建lattice（立方体）
    super(name, path, cache, autoLattice); // 调用父类JsonSchema的构造函数，传递name、path、cache和autoLattice参数，初始化父类的成员变量
  } // 构造函数结束

  @Override public void accept(ModelHandler handler) { // 重写accept方法，实现访问者模式，接收一个ModelHandler对象作为参数
    handler.visit(this); // 调用handler的visit方法，将当前JsonMapSchema对象传递给handler进行处理，这是访问者模式的核心
  } // accept方法结束

  @Override public void visitChildren(ModelHandler modelHandler) { // 重写visitChildren方法，用于遍历并处理当前schema的所有子元素
    super.visitChildren(modelHandler); // 首先调用父类的visitChildren方法，处理父类定义的子元素
    for (JsonTable jsonTable : tables) { // 遍历tables列表中的每个JsonTable对象
      jsonTable.accept(modelHandler); // 对每个JsonTable对象调用accept方法，让modelHandler处理该表
    } // tables遍历结束
    for (JsonFunction jsonFunction : functions) { // 遍历functions列表中的每个JsonFunction对象
      jsonFunction.accept(modelHandler); // 对每个JsonFunction对象调用accept方法，让modelHandler处理该函数
    } // functions遍历结束
    for (JsonType jsonType : types) { // 遍历types列表中的每个JsonType对象
      jsonType.accept(modelHandler); // 对每个JsonType对象调用accept方法，让modelHandler处理该类型
    } // types遍历结束
  } // visitChildren方法结束
} // JsonMapSchema类定义结束
