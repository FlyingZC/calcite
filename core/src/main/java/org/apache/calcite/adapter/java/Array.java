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
// 声明包名：org.apache.calcite.adapter.java，表示这个类属于Calcite框架的Java适配器模块
package org.apache.calcite.adapter.java;

// 导入ElementType枚举，用于指定注解可以应用的程序元素类型（如字段、方法、类等）
import java.lang.annotation.ElementType;
// 导入Retention注解，用于指定自定义注解的保留策略（源码、编译时、运行时）
import java.lang.annotation.Retention;
// 导入RetentionPolicy枚举，定义注解的保留策略（SOURCE、CLASS、RUNTIME）
import java.lang.annotation.RetentionPolicy;
// 导入Target注解，用于指定自定义注解可以应用的目标元素类型
import java.lang.annotation.Target;

/**
 * Annotation that indicates that a field is an array type.
 * 这是一个注解类，用于标记某个字段是数组类型
 * 在Calcite框架中，这个注解主要用于Java适配器场景，帮助Calcite理解Java对象中的数组字段结构
 * 通过这个注解，Calcite可以获取数组的组件类型、是否允许为null、最大元素数量等元数据信息
 * 这些信息对于SQL查询优化、类型推断、数据转换等操作至关重要
 */
// @Target注解指定这个Array注解只能应用于字段（FIELD）上，不能应用于类、方法等其他元素
// ElementType.FIELD表示这个注解只能用在成员变量上
@Target(ElementType.FIELD)
// @Retention注解指定这个Array注解的保留策略为RUNTIME（运行时）
// RetentionPolicy.RUNTIME表示这个注解会被编译器保留到class文件中，并且在运行时可以通过反射机制读取到
// 这样Calcite框架在运行时就能够通过反射获取到这个注解的信息，从而进行相应的处理
@Retention(RetentionPolicy.RUNTIME)
// 声明Array是一个注解接口（@interface），使用@interface关键字定义注解类型
// 注解接口是一种特殊的接口，编译器会自动为它生成实现类
public @interface Array {
  /** Component type. */
  // 定义一个名为component的注解属性，类型为Class<?>（表示任意类型的Class对象）
  // 这个属性用于指定数组元素的类型（组件类型）
  // 例如：如果数组是String[]，那么component()应该返回String.class
  // 如果数组是Integer[]，那么component()应该返回Integer.class
  // 这个属性是必须的，没有默认值，使用Array注解时必须指定
  Class<?> component();

  /** Whether components may be null. */
  // 定义一个名为componentIsNullable的注解属性，类型为boolean（布尔类型）
  // 这个属性用于指定数组中的元素是否可以为null值
  // 默认值为false，表示数组元素不能为null
  // 如果设置为true，表示数组元素允许为null值
  // 这个信息对于Calcite进行SQL查询优化和空值处理非常重要
  boolean componentIsNullable() default false;

  /** Maximum number of elements in the array. -1 means no maximum. */
  // 定义一个名为maximumCardinality的注解属性，类型为long（长整型）
  // 这个属性用于指定数组的最大元素数量（基数）
  // 默认值为-1L，表示没有最大元素数量限制
  // 如果设置为正数（如10L），则表示数组最多只能包含10个元素
  // 这个信息对于Calcite进行查询优化、内存估算、结果集大小预测等操作很有帮助
  long maximumCardinality() default -1L;
}
