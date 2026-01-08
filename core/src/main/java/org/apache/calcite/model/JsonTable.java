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
package org.apache.calcite.model; // 定义包名，表示这个类属于org.apache.calcite.model包，用于Calcite框架的模型定义

import com.fasterxml.jackson.annotation.JsonSubTypes; // 导入Jackson注解，用于JSON反序列化时识别子类型，支持多态
import com.fasterxml.jackson.annotation.JsonTypeInfo; // 导入Jackson注解，用于JSON序列化和反序列化时的类型信息处理

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework注解，用于标记可空类型，帮助静态分析工具检测空指针

import java.util.ArrayList; // 导入Java集合类ArrayList，用于存储列定义的动态数组
import java.util.List; // 导入Java集合接口List，用于定义列列表的类型

import static java.util.Objects.requireNonNull; // 导入Objects工具类的静态方法，用于参数非空校验

/**
 * Table schema element. // 类作用：表示表模式元素的抽象基类，定义了Calcite中表的基本结构和属性
 *
 * <p>Occurs within {@link JsonMapSchema#tables}. // 说明此类出现在JsonMapSchema的tables集合中，是模式中表的定义
 *
 * @see JsonRoot Description of schema elements // 参考文档：查看JsonRoot类了解模式元素的完整描述
 */
@JsonTypeInfo( // Jackson注解：配置JSON类型信息处理，支持多态序列化和反序列化
    use = JsonTypeInfo.Id.NAME, // 使用类型名称作为标识符，即根据"type"字段的值来确定具体子类
    property = "type", // 指定JSON中用于识别类型的属性名为"type"
    defaultImpl = JsonCustomTable.class) // 如果没有指定类型，默认使用JsonCustomTable作为实现类
@JsonSubTypes({ // Jackson注解：声明所有可能的子类型，用于JSON反序列化时的类型映射
    @JsonSubTypes.Type(value = JsonCustomTable.class, name = "custom"), // 当type字段值为"custom"时，反序列化为JsonCustomTable对象
    @JsonSubTypes.Type(value = JsonView.class, name = "view") }) // 当type字段值为"view"时，反序列化为JsonView对象
public abstract class JsonTable { // 定义抽象类JsonTable，作为所有表类型的基类，不能直接实例化
  /** Name of this table. // 成员变量作用：表的名称，用于在模式中唯一标识这个表
   *
   * <p>Required. Must be unique within the schema. // 必须提供，且在同一个模式中必须唯一，不能有重名的表
   */
  public final String name; // 表名，使用public final修饰，表示公开访问且不可修改，必须通过构造函数初始化

  /** Definition of the columns of this table. // 成员变量作用：定义表的列结构，包含所有列的元数据信息
   *
   * <p>Required for some kinds of type, // 对于某些表类型是必需的，比如自定义表需要明确列定义
   * optional for others (such as {@link JsonView}). // 对于其他表类型是可选的，比如视图表可能不需要显式定义列，列可以从查询中推导
   */
  public final List<JsonColumn> columns = new ArrayList<>(); // 列定义列表，使用ArrayList存储，每个JsonColumn对象描述一列的属性

  /** Information about whether the table can be streamed, and if so, whether // 成员变量作用：流式表的信息，描述表是否支持流式处理
   * the history of the table is also available. */ // 如果支持流式，还说明是否可以访问表的历史数据
  public final @Nullable JsonStream stream; // 流式配置对象，使用@Nullable标记表示可以为null，即表可以不是流式表

  protected JsonTable(String name, @Nullable JsonStream stream) { // 构造方法：受保护的构造函数，只能被子类调用，初始化表的基本属性
    this.name = requireNonNull(name, "name"); // 使用requireNonNull校验name参数不为null，如果为null抛出NullPointerException，提示信息为"name"
    this.stream = stream; // 直接赋值stream参数，允许为null，表示表可以不配置流式属性
  }

  public abstract void accept(ModelHandler handler); // 抽象方法：接受模式处理器访问，使用访问者模式处理不同类型的表，子类必须实现此方法
} // 类定义结束
