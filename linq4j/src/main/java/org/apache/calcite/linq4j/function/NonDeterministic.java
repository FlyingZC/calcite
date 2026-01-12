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
package org.apache.calcite.linq4j.function; // 定义包名为org.apache.calcite.linq4j.function，这个包包含了LINQ4J框架中使用的各种函数注解和工具类，LINQ4J是Calcite项目中用于实现类似.NET LINQ查询功能的Java库

import java.lang.annotation.ElementType; // 导入ElementType枚举类，用于指定注解可以应用的程序元素类型（如类、方法、字段等）
import java.lang.annotation.Retention; // 导入Retention注解，用于指定自定义注解的保留策略（即注解在哪个生命周期有效）
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举类，定义了三种注解保留策略：SOURCE（源码级）、CLASS（字节码级）、RUNTIME（运行时）
import java.lang.annotation.Target; // 导入Target注解，用于指定自定义注解可以应用在哪些程序元素上

/**
 * Specifies that function is NOT deterministic (i.e. it can return // 指定函数是非确定性的（即给定相同的输入，函数可以返回不同的输出）
 * different output given the same inputs). // 非确定性函数的特点是对于相同的输入参数，每次调用可能会返回不同的结果值
 *
 * <p>The function is treated as non-deterministic even if // 即使在类级别存在@Deterministic注解，该函数也会被视为非确定性的（即方法级别的注解优先级高于类级别）
 * {@code @Deterministic} annotation is present at class level. // 这种设计允许在一个类中同时包含确定性函数和非确定性函数，即使整个类被标记为确定性的
 */ // 非确定性函数的典型示例包括：RAND()随机数函数、NOW()获取当前时间函数、USER()获取当前用户函数等
@Retention(RetentionPolicy.RUNTIME) // 指定此注解的保留策略为RUNTIME，意味着注解信息会被编译到class文件中，并且在运行时可以通过反射机制读取到，这对于查询优化器在编译时判断函数性质非常重要
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.TYPE }) // 指定此注解可以应用的目标元素类型：CONSTRUCTOR（构造函数）、METHOD（方法）、TYPE（类、接口、枚举等类型声明），这允许在方法、构造函数或整个类上标记非确定性
public @interface NonDeterministic { // 声明NonDeterministic为一个公共注解接口，使用@interface关键字定义，这是一个标记注解（marker annotation），不包含任何成员变量，仅用于标记被注解的元素具有非确定性特性
} // NonDeterministic注解的结束花括号，此注解主要用于帮助Calcite查询优化器理解函数的行为特性，以便做出正确的优化决策（例如避免对非确定性函数的结果进行缓存或常量折叠）
