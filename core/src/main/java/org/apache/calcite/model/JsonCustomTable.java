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
package org.apache.calcite.model; // 包声明，该类属于org.apache.calcite.model包，用于定义Calcite模型相关的类

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson注解，用于标记JSON反序列化时使用的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson注解，用于标记JSON属性与Java字段的映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性检查注解，用于标记可能为null的字段

import java.util.Map; // 导入Map接口，用于存储键值对数据

import static java.util.Objects.requireNonNull; // 导入Objects工具类的requireNonNull方法，用于非空检查

/**
 * Custom table schema element. // 自定义表模式元素，用于在JSON模型中定义自定义表
 *
 * <p>Like base class {@link JsonTable}, // 与基类JsonTable类似，继承自JsonTable
 * occurs within {@link JsonMapSchema#tables}. // 出现在JsonMapSchema的tables集合中
 *
 * @see JsonRoot Description of schema elements // 参见JsonRoot了解模式元素的完整描述
 */
public class JsonCustomTable extends JsonTable { // JsonCustomTable类，继承自JsonTable，表示自定义表的JSON模型定义
  /** Name of the factory class for this table. // 用于创建此表的工厂类的名称
   *
   * <p>Required. Must implement interface // 必需字段，必须实现TableFactory接口
   * {@link org.apache.calcite.schema.TableFactory} and have a public default // 并且具有公共的默认构造函数
   * constructor.
   */
  public final String factory; // 工厂类名字段，final修饰表示不可变，存储创建表的工厂类的全限定名

  /** Contains attributes to be passed to the factory. // 包含要传递给工厂的属性
   *
   * <p>May be a JSON object (represented as Map) or null. // 可以是JSON对象（表示为Map）或null
   */
  public final @Nullable Map<String, Object> operand; // 操作数字段，final修饰表示不可变，存储传递给工厂的参数键值对，可能为null

  @JsonCreator // Jackson注解，标记此构造方法为JSON反序列化的入口点
  public JsonCustomTable( // 构造方法，用于创建JsonCustomTable实例
      @JsonProperty(value = "name", required = true) String name, // name参数，从JSON的"name"属性映射，required=true表示必须提供
      @JsonProperty("stream") JsonStream stream, // stream参数，从JSON的"stream"属性映射，可选参数
      @JsonProperty(value = "factory", required = true) String factory, // factory参数，从JSON的"factory"属性映射，required=true表示必须提供
      @JsonProperty("operand") @Nullable Map<String, Object> operand) { // operand参数，从JSON的"operand"属性映射，可能为null
    super(name, stream); // 调用父类JsonTable的构造方法，传递name和stream参数
    this.factory = requireNonNull(factory, "factory"); // 使用requireNonNull检查factory不为null，否则抛出NullPointerException
    this.operand = operand; // 将operand参数赋值给实例变量，允许为null
  }


  @Override public void accept(ModelHandler handler) { // 重写accept方法，实现访问者模式，接受ModelHandler访问者
    handler.visit(this); // 调用handler的visit方法，将当前JsonCustomTable实例传递给访问者处理
  }
}
