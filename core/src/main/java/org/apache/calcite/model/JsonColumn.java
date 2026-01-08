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
package org.apache.calcite.model; // 声明包名，表示这个类属于 org.apache.calcite.model 包，该包包含了用于表示 Calcite 模型（模型是 Calcite 中用于描述数据源和表结构的抽象表示）的 JSON 相关类

import com.fasterxml.jackson.annotation.JsonCreator; // 导入 Jackson 注解 @JsonCreator，用于标记构造方法，指示 Jackson 在反序列化 JSON 时使用此构造方法来创建对象实例
import com.fasterxml.jackson.annotation.JsonProperty; // 导入 Jackson 注解 @JsonProperty，用于标记构造方法参数，指示 JSON 属性名与 Java 字段之间的映射关系

import static java.util.Objects.requireNonNull; // 静态导入 requireNonNull 方法，用于在运行时检查对象引用是否为 null，如果为 null 则抛出 NullPointerException 异常，这是一种防御性编程的最佳实践

/**
 * JSON object representing a column. // 类的 JavaDoc 注释：说明这个类表示一个列的 JSON 对象，是 Calcite 模型中用于描述表结构的基本组件之一
 *
 * <p>Occurs within {@link JsonTable#columns}. // 说明 JsonColumn 对象出现在 JsonTable 的 columns 字段中，即列是表的组成部分，一个表包含多个列
 *
 * @see JsonRoot Description of JSON schema elements // 参考 JsonRoot 类，该类描述了整个 JSON 模式的元素结构，帮助理解 JsonColumn 在整个模型体系中的位置
 */
public class JsonColumn { // 定义 JsonColumn 类，这是一个公共类，可以被其他包访问，用于在 JSON 模型中表示数据库表的列定义
  /** Column name. // 字段注释：说明这个字段表示列的名称
   *
   * <p>Required, and must be unique within the table. // 详细说明：列名是必需的，并且在同一个表内必须唯一，这是数据库表的基本约束，确保每一列都可以被唯一标识
   */
  public final String name; // 定义公共 final 字段 name，存储列的名称字符串，final 表示一旦赋值就不能修改，public 表示可以从外部直接访问，这是 JSON 模型类的典型设计模式

  @JsonCreator // Jackson 注解，标记下面的构造方法为 JSON 反序列化的创建器，当 Jackson 从 JSON 字符串创建 JsonColumn 对象时会调用这个构造方法
  public JsonColumn(@JsonProperty(value = "name", required = true) String name) { // 构造方法定义：接收一个 String 类型的 name 参数，@JsonProperty 注解指定 JSON 属性名为 "name"，required = true 表示这个属性是必需的，JSON 中必须包含此属性
    this.name = requireNonNull(name, "name"); // 将传入的 name 参数赋值给实例字段 this.name，使用 requireNonNull 方法检查 name 是否为 null，如果为 null 则抛出异常，错误信息为 "name"，确保列名不为空
  } // 构造方法结束

  public void accept(ModelHandler handler) { // 定义 accept 方法，这是访问者模式（Visitor Pattern）的实现，接收一个 ModelHandler 类型的参数 handler，ModelHandler 是模型处理器，用于遍历和处理模型对象
    handler.visit(this); // 调用 handler 的 visit 方法，将当前 JsonColumn 对象作为参数传递，让 ModelHandler 处理当前列对象，这种设计允许在不修改 JsonColumn 类的情况下扩展对列的处理逻辑
  } // accept 方法结束
} // JsonColumn 类定义结束
