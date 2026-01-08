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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证，允许在特定条件下使用和修改
package org.apache.calcite.model; // 声明该类属于org.apache.calcite.model包，该包包含用于定义Calcite模型的各种JSON映射类

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson库的JsonCreator注解，用于标记JSON反序列化时使用的构造方法
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson库的JsonProperty注解，用于标记JSON属性与Java字段的映射关系

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker Framework的Nullable注解，用于标记可能为null的字段或参数

import java.util.List; // 导入Java集合框架的List接口，用于存储有序的元素列表

import static java.util.Objects.requireNonNull; // 导入Objects类的requireNonNull静态方法，用于非空检查

/**
 * Function schema element.
 * 函数模式元素类，用于表示Calcite模型中的函数定义
 * 该类通过JSON格式定义用户自定义函数（UDF）或内置函数的元数据信息
 * 它是Calcite模型系统的一部分，允许用户通过JSON配置文件来声明函数，而无需编写Java代码
 *
 * @see JsonRoot Description of schema elements
 * 参见JsonRoot类，该类描述了所有模式元素的详细信息
 */
public class JsonFunction { // 定义JsonFunction类，这是一个不可变（immutable）的POJO类，用于封装函数的配置信息
  /** Name of this function.
   * 函数的名称，用于在SQL语句中调用该函数
   * 例如：如果name为"MY_FUNC"，则SQL中可以使用SELECT MY_FUNC(...)来调用
   *
   * <p>Required.
   * 必填字段，每个函数必须有一个唯一的名称
   */
  public final String name; // 函数名称字段，使用public final修饰，表示该字段对外可见且不可修改

  /** Name of the class that implements this function.
   * 实现该函数的Java类的全限定名（包括包路径）
   * 例如："com.example.functions.MyFunctions"
   * Calcite会通过反射机制加载这个类，并查找其中的方法来创建函数
   *
   * <p>Required.
   * 必填字段，必须指定一个存在的Java类
   */
  public final String className; // 实现类的全限定名字段，使用public final修饰，确保类名不可修改

  /** Name of the method that implements this function.
   * 实现该函数的Java方法的名称
   * 该字段控制Calcite如何从className指定的类中查找和创建函数
   *
   * <p>Optional.
   * 可选字段，如果不指定，Calcite会使用默认的查找策略
   *
   * <p>If specified, the method must exist (case-sensitive) and Calcite
   * will create a scalar function. The method may be static or non-static, but
   * if non-static, the class must have a public constructor with no parameters.
   * 如果指定了方法名，该方法必须存在（区分大小写），Calcite会创建一个标量函数
   * 该方法可以是静态方法或实例方法，如果是实例方法，该类必须有一个无参的公共构造函数
   * 标量函数是指对每一行输入返回一个值的函数，如ABS、UPPER等
   *
   * <p>If "*", Calcite creates a function for every method
   * in this class.
   * 如果值为"*"，Calcite会为该类中的每个公共方法创建一个函数
   * 这是一种批量创建函数的方式，适用于工具类或函数集合类
   *
   * <p>If not specified, Calcite looks for a method called "eval", and
   * if found, creates a a table macro or scalar function.
   * 如果没有指定，Calcite会查找名为"eval"的方法，如果找到，会创建表宏或标量函数
   * 表宏是一种特殊的函数，可以返回一个表（结果集），类似于SQL中的表值函数
   * 
   * It also looks for methods "init", "add", "merge", "result", and
   * if found, creates an aggregate function.
   * Calcite还会查找"init"、"add"、"merge"、"result"这些方法，如果都找到，会创建一个聚合函数
   * 聚合函数是对一组值进行计算并返回单个值的函数，如SUM、AVG、COUNT等
   * 这些方法对应聚合函数的不同阶段：
   * - init: 初始化累加器
   * - add: 将新值添加到累加器
   * - merge: 合并两个累加器（用于并行计算）
   * - result: 从累加器获取最终结果
   */
  public final @Nullable String methodName; // 方法名字段，使用@Nullable注解标记为可空，使用public final修饰

  /** Path for resolving this function.
   * 用于解析该函数的路径列表
   * 这个路径用于指定类加载器在哪些位置查找实现类
   * 通常用于从外部JAR文件或自定义目录加载函数实现类
   *
   * <p>Optional.
   * 可选字段，如果不指定，Calcite会使用默认的类加载路径
   */
  public final @Nullable List<String> path; // 路径列表字段，使用@Nullable注解标记为可空，使用public final修饰

  @JsonCreator // 标记该构造方法为JSON反序列化的入口点，Jackson会使用此构造方法从JSON创建对象
  public JsonFunction( // 构造方法，用于创建JsonFunction实例，所有参数都通过JsonProperty注解映射JSON字段
      @JsonProperty("name") String name, // name参数，对应JSON中的"name"字段
      @JsonProperty(value = "className", required = true) String className, // className参数，对应JSON中的"className"字段，required=true表示必填
      @JsonProperty("methodName") @Nullable String methodName, // methodName参数，对应JSON中的"methodName"字段，可为null
      @JsonProperty("path") @Nullable List<String> path) { // path参数，对应JSON中的"path"字段，可为null
    this.name = name; // 将传入的name参数赋值给实例字段name
    this.className = requireNonNull(className, "className"); // 将传入的className参数赋值给实例字段，并使用requireNonNull确保不为null
    this.methodName = methodName; // 将传入的methodName参数赋值给实例字段methodName
    this.path = path; // 将传入的path参数赋值给实例字段path
  } // 构造方法结束

  public void accept(ModelHandler handler) { // accept方法，实现访问者模式，允许ModelHandler访问和处理这个JsonFunction对象
    handler.visit(this); // 调用ModelHandler的visit方法，将当前JsonFunction对象传递给处理器进行注册和处理
  } // accept方法结束，这是访问者模式的标准实现，用于将处理逻辑委托给处理器
} // JsonFunction类结束
