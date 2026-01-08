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
// Apache许可证声明,说明该代码遵循Apache 2.0许可证,允许在遵守许可证条款的前提下使用和修改
package org.apache.calcite.adapter.java; // 定义包名,表示该类属于Calcite框架的Java适配器模块,用于将Java对象作为数据源

import java.lang.annotation.ElementType; // 导入ElementType枚举,用于指定注解可以应用的目标类型(如字段、方法、类等)
import java.lang.annotation.Retention; // 导入Retention注解,用于指定注解的保留策略(源码、编译时、运行时)
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举,定义注解的三种保留策略:SOURCE、CLASS、RUNTIME
import java.lang.annotation.Target; // 导入Target注解,用于指定自定义注解可以应用在哪些程序元素上

/**
 * Annotation that indicates that a field is a map type.
 * 这是一个注解类,用于标记字段为Map类型,即该字段是一个键值对集合
 * 在Calcite的Java适配器中,当需要将Java类的字段作为表结构展示时,
 * 如果某个字段是Map类型,需要使用此注解来明确指定键和值的类型以及可空性
 * 这样Calcite才能正确地推断出该字段的SQL类型信息,从而支持SQL查询
 */
@Target(ElementType.FIELD) // 指定该注解只能应用于字段上,不能应用于类、方法等其他元素
@Retention(RetentionPolicy.RUNTIME) // 指定该注解在运行时仍然保留,可以通过反射机制读取注解信息,这对于Calcite在运行时动态扫描Java类结构至关重要
public @interface Map { // 定义一个名为Map的注解类型,在Java中注解使用@interface关键字声明
  /** Key type. */
  // 键的类型,指定Map中键的Java类型(如String.class、Integer.class等)
  // Calcite会根据这个类型推断出对应的SQL类型(如VARCHAR、INTEGER等)
  Class<?> key(); // 注解元素,使用Class<?>表示可以是任何类型,使用问号表示通配符,允许传入任意类型的Class对象

  /** Value type. */
  // 值的类型,指定Map中值的Java类型(如String.class、Integer.class等)
  // Calcite会根据这个类型推断出对应的SQL类型(如VARCHAR、INTEGER等)
  Class<?> value(); // 注解元素,使用Class<?>表示可以是任何类型,允许传入任意类型的Class对象

  /** Whether keys may be null. */
  // 键是否可以为null,指定Map中的键是否允许为null值
  // 默认值为true,表示键可以为null,这会影响SQL查询时的null处理逻辑
  boolean keyIsNullable() default true; // 注解元素,返回布尔值,default关键字指定默认值为true,即如果不显式设置,键可以为null

  /** Whether values may be null. */
  // 值是否可以为null,指定Map中的值是否允许为null值
  // 默认值为true,表示值可以为null,这会影响SQL查询时的null处理逻辑
  boolean valueIsNullable() default true; // 注解元素,返回布尔值,default关键字指定默认值为true,即如果不显式设置,值可以为null
}
