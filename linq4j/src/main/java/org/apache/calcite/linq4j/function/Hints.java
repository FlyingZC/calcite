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
package org.apache.calcite.linq4j.function; // 定义包名，该类属于org.apache.calcite.linq4j.function包，是Calcite的LINQ4J功能模块的一部分

import java.lang.annotation.Retention; // 导入Retention注解，用于指定自定义注解的保留策略（源码、编译时或运行时）
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举，定义了注解的三种保留策略：SOURCE、CLASS、RUNTIME
import java.lang.annotation.Target; // 导入Target注解，用于指定自定义注解可以应用的目标（类、方法、字段等）

import static java.lang.annotation.ElementType.METHOD; // 静态导入METHOD枚举值，表示注解可以应用于方法上
import static java.lang.annotation.ElementType.TYPE; // 静态导入TYPE枚举值，表示注解可以应用于类、接口或枚举类型上

/**
 * Annotation applied to a user-defined function that gives extra metadata
 * about that function.
 * // 这是一个应用于用户自定义函数的注解，用于提供关于该函数的额外元数据信息
 * // 该注解允许开发者向Calcite传递额外的配置和提示信息，以便更好地理解和处理用户定义的函数
 *
 * <p>Examples:
 * // 使用示例：
 * <ul>
 *   <li>@Hints("SqlKind:ST_DWithin") public static void myFun()</li>
 *   // 示例1：在方法上使用@Hints注解，指定该函数对应的SQL操作类型为ST_DWithin（空间距离判断函数）
 *   // 这种提示信息可以帮助Calcite在SQL解析和优化阶段正确识别和处理该函数
 * </ul>
 */
@Target({METHOD, TYPE }) // 指定该注解可以应用于方法和类/接口/枚举类型上，允许在函数定义或类级别添加元数据提示
@Retention(RetentionPolicy.RUNTIME) // 指定该注解在运行时仍然有效，可以通过反射机制读取，这对于动态函数注册和调用至关重要
@Experimental // 标记该注解为实验性特性，意味着其API可能会在未来版本中发生变化，不建议在生产环境中完全依赖
public @interface Hints { // 定义Hints注解接口，使用@interface关键字声明这是一个注解类型
  String[] value(); // 定义注解的value属性，类型为字符串数组，用于存储多个提示信息，每个提示字符串通常以"键:值"的格式提供元数据
  // 例如：@Hints({"SqlKind:ST_DWithin", "Deterministic:true"})可以同时指定函数的SQL类型和确定性属性
  // 该属性是注解的唯一成员，使用时可以省略"value="前缀，直接写成@Hints({"hint1", "hint2"})的形式
}
