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
package org.apache.calcite.linq4j.tree; // 声明包名，该接口位于org.apache.calcite.linq4j.tree包下，属于LINQ4J树形结构的一部分

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的空值检查注解，用于标记可能为null的参数或返回值

import java.lang.reflect.Type; // 导入Java反射API中的Type接口，用于表示Java类型

/**
 * Contains the parts of the {@link java.lang.reflect.Field} class needed
 * for code generation, but might be implemented differently.
 */ // 接口文档注释：该接口包含了java.lang.reflect.Field类中代码生成所需的部分，但可能以不同的方式实现
public interface PseudoField { // 定义PseudoField接口，这是一个"伪字段"接口，用于在代码生成过程中模拟字段的访问行为，但不一定对应真实的Java字段
  String getName(); // 声明获取字段名称的方法，返回字段的名称字符串，例如"age"、"name"等

  Type getType(); // 声明获取字段类型的方法，返回字段的Type对象，表示字段的数据类型，如Integer.class、String.class等

  int getModifiers(); // 声明获取字段修饰符的方法，返回字段的访问修饰符标志位，如public、private、static、final等，使用位掩码表示

  @Nullable Object get(@Nullable Object o) throws IllegalAccessException; // 声明获取字段值的方法，参数o是目标对象（如果是实例字段）或null（如果是静态字段），返回字段的值，可能为null，可能抛出IllegalAccessException异常表示访问权限不足

  Type getDeclaringClass(); // 声明获取声明类的方法，返回声明该字段的类的Type对象，表示该字段属于哪个类
} // 接口定义结束
