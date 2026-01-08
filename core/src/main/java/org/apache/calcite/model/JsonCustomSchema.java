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
 */ // Apache许可证声明，说明代码遵循Apache 2.0许可证
package org.apache.calcite.model; // 声明包名，该类属于org.apache.calcite.model包，用于定义Calcite模型相关的JSON数据结构

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson库的JsonCreator注解，用于标记JSON反序列化时使用的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson库的JsonProperty注解，用于标记JSON属性与Java字段的映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker框架的Nullable注解，用于标记可能为null的字段或参数

import java.util.List; // 导入Java集合框架的List接口，用于表示有序的元素列表
import java.util.Map; // 导入Java集合框架的Map接口，用于表示键值对映射

import static java.util.Objects.requireNonNull; // 导入Objects类的requireNonNull静态方法，用于非空校验

/**
 * JSON schema element that represents a custom schema.
 *
 * <p>Like the base class {@link JsonSchema},
 * occurs within {@link JsonRoot#schemas}.
 *
 * @see org.apache.calcite.model.JsonRoot Description of schema elements
 */ // 类的JavaDoc注释：说明这是一个表示自定义schema的JSON schema元素，继承自JsonMapSchema基类，出现在JsonRoot的schemas列表中
public class JsonCustomSchema extends JsonMapSchema { // 定义JsonCustomSchema类，继承自JsonMapSchema，用于表示通过工厂类创建的自定义schema
  /** Name of the factory class for this schema.
   *
   * <p>Required. Must implement interface
   * {@link org.apache.calcite.schema.SchemaFactory} and have a public default
   * constructor.
   */ // factory字段的JavaDoc注释：说明这是用于创建此schema的工厂类的完全限定名，是必填字段，必须实现SchemaFactory接口并有公共默认构造方法
  public final String factory; // 定义工厂类名字段，使用final修饰表示不可变，存储实现SchemaFactory接口的工厂类的完全限定名

  /** Contains attributes to be passed to the factory.
   *
   * <p>May be a JSON object (represented as Map) or null.
   */ // operand字段的JavaDoc注释：说明这是传递给工厂类的属性集合，可以是JSON对象（表示为Map）或null
  public final @Nullable Map<String, Object> operand; // 定义操作数字段，使用final修饰表示不可变，使用@Nullable注解表示可以为null，存储传递给工厂类的键值对参数

  @JsonCreator // JsonCreator注解：标记此构造方法为Jackson反序列化JSON对象时使用的构造方法
  public JsonCustomSchema( // 构造方法：用于创建JsonCustomSchema实例，从JSON对象反序列化时调用
      @JsonProperty(value = "name", required = true) String name, // name参数：使用JsonProperty注解映射JSON中的"name"字段，required=true表示必填，存储schema的名称
      @JsonProperty("path") @Nullable List<Object> path, // path参数：使用JsonProperty注解映射JSON中的"path"字段，@Nullable表示可为null，存储schema的路径列表
      @JsonProperty("cache") @Nullable Boolean cache, // cache参数：使用JsonProperty注解映射JSON中的"cache"字段，@Nullable表示可为null，控制是否缓存schema
      @JsonProperty("autoLattice") @Nullable Boolean autoLattice, // autoLattice参数：使用JsonProperty注解映射JSON中的"autoLattice"字段，@Nullable表示可为null，控制是否自动创建lattice
      @JsonProperty(value = "factory", required = true) String factory, // factory参数：使用JsonProperty注解映射JSON中的"factory"字段，required=true表示必填，存储工厂类的完全限定名
      @JsonProperty("operand") @Nullable Map<String, Object> operand) { // operand参数：使用JsonProperty注解映射JSON中的"operand"字段，@Nullable表示可为null，存储传递给工厂类的参数
    super(name, path, cache, autoLattice); // 调用父类JsonMapSchema的构造方法，初始化继承的字段name、path、cache和autoLattice
    this.factory = requireNonNull(factory, "factory"); // 使用requireNonNull方法校验factory参数不为null，如果为null则抛出NullPointerException，然后赋值给factory字段
    this.operand = operand; // 将operand参数赋值给operand字段，允许为null
  } // 构造方法结束

  @Override public void accept(ModelHandler handler) { // accept方法：重写基类的accept方法，实现访问者模式，接受ModelHandler访问者对象
    handler.visit(this); // 调用ModelHandler的visit方法，将当前JsonCustomSchema对象传递给处理器，由处理器执行相应的处理逻辑
  } // accept方法结束

  @Override public String toString() { // toString方法：重写Object类的toString方法，用于返回对象的字符串表示
    return "JsonCustomSchema(name=" + name + ")"; // 返回包含schema名称的字符串表示，格式为"JsonCustomSchema(name=xxx)"
  } // toString方法结束
} // 类定义结束
