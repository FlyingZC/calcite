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
package org.apache.calcite.model;

import com.fasterxml.jackson.annotation.JsonCreator; // Jackson注解：标识一个构造函数或工厂方法，用于在JSON反序列化时创建对象实例
import com.fasterxml.jackson.annotation.JsonProperty; // Jackson注解：标识构造函数参数与JSON属性之间的映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // Checker Framework注解：标记类型可为null，用于静态空值检查

import java.util.List; // Java集合框架：List接口，用于存储有序的元素集合

import static java.util.Objects.requireNonNull; // Java工具类：静态方法，检查对象是否为null，如果为null则抛出NullPointerException

/**
 * 视图（View）模式的元素类 - 表示Calcite中的视图定义
 *
 * <p>这个类继承自基类 {@link JsonTable}，通常出现在 {@link JsonMapSchema#tables} 集合中
 * 视图是SQL中一种虚拟表，基于一个或多个表或其他视图的查询结果创建，不存储实际数据
 *
 * <h2>可修改视图（Modifiable Views）</h2>
 *
 * <p>一个视图被认为是可修改的，需要满足以下条件：只包含SELECT、FROM、WHERE子句（不能有JOIN、聚合或子查询）
 * 并且每一列必须满足以下三个条件之一：
 *
 * <ul>
 *   <li>在SELECT子句中只出现一次；或者
 *   <li>在WHERE子句中以"列 = 字面量"的谓词形式出现；或者
 *   <li>该列是可为空的（nullable）
 * </ul>
 *
 * <p>第二个条件允许Calcite自动为隐藏列提供正确的值
 * 这在多租户环境中非常有用，例如 {@code tenantId} 列是隐藏的、必填的（NOT NULL），并且在特定视图中具有常量值
 *
 * <p>关于可修改视图的错误处理：
 *
 * <ul>
 *   <li>如果一个视图被标记为 modifiable: true 但实际上不可修改，Calcite在读取模式时会抛出错误
 *   <li>如果向不可修改的视图提交INSERT、UPDATE或UPSERT命令，Calcite在验证语句时会抛出错误
 *   <li>如果DML语句创建的行不会出现在视图中（例如，在female_emps视图中插入gender = 'M'的行），Calcite在执行语句时会抛出错误
 * </ul>
 *
 * @see JsonRoot 模式元素的描述
 */
public class JsonView extends JsonTable { // 定义JsonView类，继承自JsonTable基类，表示一个视图定义
  /** 定义视图的SQL查询语句
   *
   * <p>这个字段可以是字符串或字符串列表（字符串列表会被连接成多行SQL字符串，用换行符分隔）
   * 例如，可以是 "SELECT * FROM emp WHERE deptno = 10" 或者 ["SELECT *", "FROM emp", "WHERE deptno = 10"]
   * 这个SQL语句定义了视图的数据来源和过滤条件，当查询视图时，Calcite会执行这个SQL语句
   */
  public final Object sql; // 视图定义的SQL查询，类型为Object以支持字符串或字符串列表

  /** 在解析查询时使用的模式名称列表（schema path）
   *
   * <p>如果未指定，则默认使用当前模式
   * 这个字段用于指定视图解析时应该查找的模式路径，类似于SQL中的SET search_path
   * 例如，["sales", "public"] 表示先在sales模式中查找，如果找不到则在public模式中查找
   * 这对于在视图中引用其他模式的表非常有用
   */
  public final @Nullable List<String> path; // 模式路径列表，可为null表示使用默认模式

  /** 此视图是否应该允许INSERT请求（是否可修改）
   *
   * <p>这个字段的值具有以下含义：
   * <ul>
   * <li>如果为true：Calcite会在验证模式时检查视图是否可修改，如果不可修改则抛出错误，强制要求视图必须可修改
   * <li>如果为null：Calcite会自动推断视图是否可修改，这是默认行为，允许Calcite根据视图定义的SQL语句判断
   * <li>如果为false：Calcite将不允许对此视图执行插入操作，即使视图技术上可修改
   * </ul>
   *
   * <p>默认值是 {@code null}，表示让Calcite自动判断
   * 这个设计提供了灵活性：可以强制要求可修改性、禁止修改，或让系统自动推断
   */
  public final @Nullable Boolean modifiable; // 视图是否可修改的标志，可为null表示自动推断

  @JsonCreator // Jackson注解：标记这是一个JSON反序列化时使用的构造函数
  public JsonView( // JsonView类的构造函数，用于从JSON数据创建JsonView对象
      @JsonProperty(value = "name", required = true) String name, // 视图名称，required=true表示JSON中必须包含此字段
      @JsonProperty("stream") JsonStream stream, // 流定义，可选参数，表示视图是否为流式视图
      @JsonProperty(value = "sql", required = true) Object sql, // 视图的SQL定义，required=true表示必须提供
      @JsonProperty("path") @Nullable List<String> path, // 模式路径，可选参数，可为null
      @JsonProperty("modifiable") @Nullable Boolean modifiable) { // 可修改标志，可选参数，可为null
    super(name, stream); // 调用父类JsonTable的构造函数，传递视图名称和流定义
    this.sql = requireNonNull(sql, "sql"); // 使用requireNonNull检查sql是否为null，如果为null则抛出NullPointerException，错误信息为"sql"
    this.path = path; // 初始化path字段，直接赋值，可为null
    this.modifiable = modifiable; // 初始化modifiable字段，直接赋值，可为null
  }

  @Override public void accept(ModelHandler handler) { // 重写accept方法，实现访问者模式
    handler.visit(this); // 调用处理器handler的visit方法，将当前JsonView对象传入，让处理器处理视图
  }

  @Override public String toString() { // 重写toString方法，提供对象的字符串表示
    return "JsonView(name=" + name + ")"; // 返回包含视图名称的字符串表示，格式为"JsonView(name=视图名)"
  }

  /** 将SQL查询返回为字符串，如果需要则连接字符串列表
   *
   * <p>这个方法处理sql字段可能为字符串或字符串列表的情况
   * 如果sql是字符串列表，会将其连接成多行SQL字符串，用换行符分隔
   * 如果sql已经是字符串，则直接返回
   * 这确保了无论sql字段以何种形式提供，都能得到统一的字符串表示
   */
  public String getSql() { // 获取视图的SQL查询字符串的方法
    return JsonLattice.toString(sql); // 调用JsonLattice工具类的toString方法，将sql对象转换为字符串
  }
}
