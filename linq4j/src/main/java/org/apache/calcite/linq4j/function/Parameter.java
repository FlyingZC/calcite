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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证
package org.apache.calcite.linq4j.function; // 定义包名，该类属于org.apache.calcite.linq4j.function包，是Calcite的LINQ4J功能模块的一部分

import java.lang.annotation.ElementType; // 导入ElementType枚举，用于指定注解可以应用的目标类型（如类、方法、参数等）
import java.lang.annotation.Retention; // 导入Retention注解，用于指定注解的保留策略（源码、编译时、运行时）
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举，定义注解的保留策略枚举值
import java.lang.annotation.Target; // 导入Target注解，用于指定自定义注解可以应用的目标类型

/**
 * Annotation that supplies metadata about a function parameter. // 这是一个注解类，用于提供函数参数的元数据信息
 *
 * <p>A typical use is to derive names for the parameters of user-defined // 典型用途是推导用户自定义函数的参数名称
 * functions. // 用于为用户定义的函数提供参数命名
 *
 * <p>Here is an example: // 这是一个使用示例
 *
 * <blockquote><pre> // 代码块开始
 * public static class MyLeftFunction { // 定义一个公共静态类MyLeftFunction，用于演示Parameter注解的使用
 *   public String eval( // 定义公共方法eval，返回String类型
 *       &#64;Parameter(name = "s") String s, // 第一个参数s，使用@Parameter注解指定参数名为"s"，是必填参数
 *       &#64;Parameter(name = "n", optional = true) Integer n) { // 第二个参数n，使用@Parameter注解指定参数名为"n"，标记为可选参数
 *     return s.substring(0, n == null ? 1 : n); // 返回字符串s的子串，从索引0开始，长度为n（如果n为null则长度为1）
 *   }
 * }</pre></blockquote> // 代码块结束
 *
 * <p>The first parameter is named "s" and is mandatory, // 第一个参数名为"s"，是必填参数（optional默认为false）
 * and the second parameter is named "n" and is optional. // 第二个参数名为"n"，是可选参数（optional=true）
 *
 * <p>If this annotation is present, it supersedes information that might be // 如果存在此注解，它将覆盖可能通过其他方式获得的信息
 * available via // 这些信息原本可以通过以下方式获得
 * {@code Executable.getParameters()} (JDK 1.8 and above). // 使用JDK 1.8及以上版本的Executable.getParameters()方法
 *
 * <p>If the annotation is not specified, parameters will be named "arg0", // 如果未指定此注解，参数将被命名为"arg0"
 * "arg1" et cetera, and will be mandatory. // 或"arg1"等，并且都是必填参数
 */
@Retention(RetentionPolicy.RUNTIME) // 指定注解的保留策略为RUNTIME，表示注解在运行时仍然可用，可以通过反射获取
@Target({ElementType.PARAMETER }) // 指定此注解只能应用于参数（方法参数、构造函数参数等）
public @interface Parameter { // 定义一个名为Parameter的注解接口，public表示可以在任何地方访问
  /** The name of the parameter. // 参数的名称属性
   *
   * <p>The name is used when the function is called // 该名称在函数被调用时使用
   * with parameter assignment, for example {@code foo(x -> 1, y -> 'a')}. // 特别是在使用参数赋值语法调用函数时，例如foo(x -> 1, y -> 'a')
   */
  String name(); // 定义注解属性name，类型为String，用于指定参数的名称，没有默认值，因此必须指定

  /** Returns whether the parameter is optional. // 返回参数是否为可选的
   *
   * <p>An optional parameter does not need to be specified when you call the // 可选参数在调用函数时不需要必须指定
   * function. // 可以省略不传
   *
   * <p>If you call a function using positional parameter syntax, you can omit // 如果使用位置参数语法调用函数，可以省略
   * optional parameters on the trailing edge. For example, if you have a // 末尾的可选参数。例如，如果有一个函数
   * function // 函数签名为
   * {@code baz(int a, int b optional, int c, int d optional, int e optional)} // baz(int a, int b可选, int c, int d可选, int e可选)
   * then you can call {@code baz(a, b, c, d, e)} // 那么可以调用baz(a, b, c, d, e)传递所有参数
   * or {@code baz(a, b, c, d)} // 或者baz(a, b, c, d)省略最后一个可选参数e
   * or {@code baz(a, b, c)} // 或者baz(a, b, c)省略最后两个可选参数d和e
   * but not {@code baz(a, b)} because {@code c} is not optional. // 但不能调用baz(a, b)，因为c不是可选参数
   *
   * <p>If you call a function using parameter name assignment syntax, you can // 如果使用参数名赋值语法调用函数，可以
   * omit any parameter that has a default value. For example, you can call // 省略任何具有默认值的参数。例如，可以调用
   * {@code baz(a -> 1, e -> 5, c -> 3)}, omitting optional parameters {@code b} // baz(a -> 1, e -> 5, c -> 3)，省略可选参数b
   * and {@code d}. // 和d
   *
   * <p>Currently, the default value used when a parameter is not specified // 当前，当参数未指定时使用的默认值是NULL
   * is NULL, and therefore optional parameters must be nullable. // 因此可选参数必须可空（能够接受null值）
   */
  boolean optional() default false; // 定义注解属性optional，类型为boolean，默认值为false，表示参数默认是必填的
} // 注解定义结束
