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
package org.apache.calcite.model; // 定义包名：org.apache.calcite.model，这是Calcite框架中用于JSON模型定义的包

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson注解：标记构造函数为JSON反序列化的创建器，用于从JSON字符串创建对象
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson注解：标记字段属性，用于JSON序列化和反序列化时映射JSON字段名

import static java.util.Objects.requireNonNull; // 静态导入Objects.requireNonNull方法，用于参数非空校验

/**
 * JSON object representing a type attribute. // 类的作用：表示类型属性的JSON对象，用于在Calcite的JSON模型中定义数据类型的属性信息
 * 
 * 详细说明：
 * 1. 这是一个不可变的数据类（immutable class），用于表示数据类型的属性
 * 2. 该类主要用于Calcite的JSON模型配置中，例如在定义自定义类型时需要指定类型的属性
 * 3. 通过Jackson注解实现JSON序列化和反序列化，可以从JSON字符串自动创建对象
 * 4. 该类是Calcite模型层的一部分，用于连接外部JSON配置和内部数据结构
 * 
 * 使用场景：
 * - 在Calcite的model.json配置文件中定义自定义类型时使用
 * - 用于描述复合类型或自定义类型的属性信息
 * - 作为JsonType类的属性来构建完整的类型定义
 * 
 * 示例JSON格式：
 * {
 *   "name": "attributeName",
 *   "type": "VARCHAR"
 * }
 */
public class JsonTypeAttribute { // 定义类名：JsonTypeAttribute，表示JSON类型属性
  /** Name of this attribute. // 成员变量作用：属性的名称，用于标识这个属性
   *
   * <p>Required. // 必填字段：该属性是必须提供的
   * 
   * 详细说明：
   * - name字段存储属性的唯一标识符
   * - 在类型定义中，name用于区分不同的属性
   * - 例如：在定义一个"Person"类型时，可能有"name"、"age"等属性名称
   * - 该字段被声明为final，表示一旦初始化就不能修改，保证不可变性
   * - 访问修饰符为public，允许外部直接访问
   * - 类型为String，使用字符串表示属性名称
   */
  public final String name; // 声明公共不可变成员变量name，存储属性名称

  /** Type of this attribute. // 成员变量作用：属性的数据类型，指定该属性值的类型
   *
   * <p>Required. // 必填字段：该属性是必须提供的
   * 
   * 详细说明：
   * - type字段指定属性的数据类型，如VARCHAR、INTEGER、BOOLEAN等
   * - 该类型可以是Calcite支持的任何SQL类型
   * - 也可以是用户自定义的类型（如果已注册）
   * - 该字段被声明为final，表示一旦初始化就不能修改，保证不可变性
   * - 访问修饰符为public，允许外部直接访问
   * - 类型为String，使用字符串表示类型名称
   * 
   * 常见类型示例：
   * - "VARCHAR" - 可变长度字符串
   * - "INTEGER" - 整数类型
   * - "DOUBLE" - 双精度浮点数
   * - "BOOLEAN" - 布尔类型
   * - "DATE" - 日期类型
   * - "TIMESTAMP" - 时间戳类型
   */
  public final String type; // 声明公共不可变成员变量type，存储属性的数据类型

  @JsonCreator // Jackson注解：标记此构造函数为JSON反序列化的创建器，当从JSON字符串创建对象时会调用此构造函数
  public JsonTypeAttribute( // 构造函数：创建JsonTypeAttribute对象，初始化name和type字段
      @JsonProperty(value = "name", required = true) String name, // 参数：name - 属性名称，从JSON的"name"字段映射，required=true表示该字段必须存在
      @JsonProperty(value = "type", required = true) String type) { // 参数：type - 属性类型，从JSON的"type"字段映射，required=true表示该字段必须存在
    this.name = requireNonNull(name, "name"); // 将name参数赋值给成员变量，并使用requireNonNull进行非空校验，如果为null则抛出NullPointerException，错误信息为"name"
    this.type = requireNonNull(type, "type"); // 将type参数赋值给成员变量，并使用requireNonNull进行非空校验，如果为null则抛出NullPointerException，错误信息为"type"
  } // 构造函数结束，完成对象的初始化
} // 类定义结束
