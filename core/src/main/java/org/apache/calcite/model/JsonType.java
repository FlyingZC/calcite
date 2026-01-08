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
 */ // Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.model; // 声明包名，该类属于org.apache.calcite.model包，用于定义Calcite模型相关的类

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson注解，用于标记JSON反序列化时使用的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson注解，用于标记JSON属性与Java字段的映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework注解，用于标记可能为null的字段或参数

import java.util.ArrayList; // 导入ArrayList类，用于创建可变长度的数组列表
import java.util.List; // 导入List接口，用于定义列表类型

import static java.util.Objects.requireNonNull; // 导入Objects类的requireNonNull静态方法，用于参数非空校验

/**
 * Type schema element. // 类型模式元素，用于在JSON模型中定义自定义类型
 *
 * <p>Occurs within {@link JsonMapSchema#types}, // 该类可以出现在JsonMapSchema的types属性中
 * {@link JsonRoot#types}. // 也可以出现在JsonRoot的types属性中
 *
 * @see JsonRoot Description of schema elements // 参见JsonRoot类了解模式元素的完整描述
 */
public class JsonType { // 定义JsonType类，用于表示JSON模型中的类型定义
  /** Name of this type. // 类型的名称，用于唯一标识这个类型
   *
   * <p>Required. // 此字段是必需的，必须在JSON中提供
   */
  public final String name; // 类型名称字段，使用final修饰表示不可变，public表示可以直接访问

  /** Type if this is not a struct. // 如果这不是一个结构体类型，则此字段指定基础类型
   */
  public final @Nullable String type; // 基础类型字段，可为null，@Nullable注解表示可能为null

  /** Definition of the attributes of this type. // 定义该类型的属性列表，用于结构体类型
   */
  public final List<JsonTypeAttribute> attributes = new ArrayList<>(); // 属性列表，使用ArrayList初始化，存储JsonTypeAttribute对象

  @JsonCreator // Jackson注解，标记此构造方法为JSON反序列化的入口点
  public JsonType( // 构造方法，用于创建JsonType实例
      @JsonProperty(value = "name", required = true) String name, // name参数，从JSON的"name"属性映射，required=true表示必须提供
      @JsonProperty("type") @Nullable String type) { // type参数，从JSON的"type"属性映射，可为null
    this.name = requireNonNull(name, "name"); // 对name参数进行非空校验，如果为null则抛出NullPointerException，并提示"name"
    this.type = type; // 将type参数赋值给实例变量type
  }

  public void accept(ModelHandler handler) { // 接受方法，使用访问者模式，将自身传递给ModelHandler处理器
    handler.visit(this); // 调用ModelHandler的visit方法，传入当前JsonType对象，让处理器处理此类型
  }
} // 类定义结束
