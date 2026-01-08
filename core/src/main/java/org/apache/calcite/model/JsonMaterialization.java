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
 */ // Apache许可证声明，允许在遵守许可证条款的前提下使用和分发代码
package org.apache.calcite.model; // 声明当前类所在的包，属于org.apache.calcite.model模型包

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson库的JsonCreator注解，用于标记JSON反序列化的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson库的JsonProperty注解，用于标记JSON属性映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的Nullable注解，用于标记可能为null的字段

import java.util.List; // 导入Java集合框架的List接口，用于存储字符串列表

import static java.util.Objects.requireNonNull; // 导入Objects类的requireNonNull静态方法，用于参数非空校验

/**
 * Element that describes how a table is a materialization of a query.
 * 描述表如何作为查询的物化视图的元素
 *
 * <p>Occurs within {@link JsonSchema#materializations}.
 * 该元素出现在JsonSchema的materializations集合中
 *
 * @see JsonRoot Description of schema elements
 * 参见JsonRoot以了解schema元素的完整描述
 */
public class JsonMaterialization { // 定义JsonMaterialization类，用于在JSON模型中表示物化视图的定义
  public final @Nullable String view; // 视图名称字段，表示物化视图对应的逻辑视图名称，可以为null，final表示该字段不可变
  public final @Nullable String table; // 表名称字段，表示物化视图实际存储的物理表名称，可以为null，final表示该字段不可变

  /** SQL query that defines the materialization.
   * 定义物化视图的SQL查询语句
   *
   * <p>Must be a string or a list of strings (which are concatenated into a
   * multi-line SQL string, separated by newlines).
   * 必须是一个字符串或字符串列表（如果是列表，会连接成一个多行SQL字符串，用换行符分隔）
   */
  public final Object sql; // SQL查询字段，存储定义物化视图的SQL语句，类型为Object可以是String或List<String>，final表示该字段不可变

  public final @Nullable List<String> viewSchemaPath; // 视图schema路径字段，表示视图所在的schema路径列表，可以为null，final表示该字段不可变

  @JsonCreator // 使用Jackson注解标记此构造方法为JSON反序列化的工厂方法
  public JsonMaterialization( // JsonMaterialization类的构造方法，用于从JSON数据创建对象实例
      @JsonProperty("view") @Nullable String view, // view参数：从JSON的"view"属性映射而来，表示视图名称，可以为null
      @JsonProperty("table") @Nullable String table, // table参数：从JSON的"table"属性映射而来，表示表名称，可以为null
      @JsonProperty(value = "sql", required = true) Object sql, // sql参数：从JSON的"sql"属性映射而来，required=true表示此参数必须提供，存储SQL查询语句
      @JsonProperty("viewSchemaPath") @Nullable List<String> viewSchemaPath) { // viewSchemaPath参数：从JSON的"viewSchemaPath"属性映射而来，表示视图schema路径，可以为null
    this.view = view; // 将传入的view参数值赋给类的view字段
    this.table = table; // 将传入的table参数值赋给类的table字段
    this.sql = requireNonNull(sql, "sql"); // 将传入的sql参数值赋给类的sql字段，并使用requireNonNull确保sql不为null，否则抛出NullPointerException
    this.viewSchemaPath = viewSchemaPath; // 将传入的viewSchemaPath参数值赋给类的viewSchemaPath字段
  }

  public void accept(ModelHandler handler) { // accept方法：接受访问者模式的处理器，用于处理当前物化视图对象
    handler.visit(this); // 调用处理器的visit方法，将当前JsonMaterialization对象传递给处理器进行处理
  }

  @Override public String toString() { // 重写Object类的toString方法，用于返回对象的字符串表示
    return "JsonMaterialization(table=" + table + ", view=" + view + ")"; // 返回包含table和view字段值的格式化字符串，便于调试和日志输出
  }

  /** Returns the SQL query as a string, concatenating a list of lines if
   * necessary. 返回SQL查询的字符串形式，如果sql是列表则将多行连接成一个字符串
   */
  public String getSql() { // getSql方法：获取SQL查询语句的字符串形式
    return JsonLattice.toString(sql); // 调用JsonLattice工具类的toString方法，将sql字段（可能是String或List<String>）转换为统一的字符串格式
  }
} // 类定义结束
