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
 */ // Apache许可证头，声明代码版权和使用条款
package org.apache.calcite.linq4j.function; // 定义包名，该注解属于LINQ4J函数包

import java.lang.annotation.Retention; // 导入Retention注解，用于指定自定义注解的保留策略
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举，定义注解保留策略（SOURCE/CLASS/RUNTIME）
import java.lang.annotation.Target; // 导入Target注解，用于指定自定义注解可以应用的程序元素类型

import static java.lang.annotation.ElementType.METHOD; // 静态导入METHOD常量，表示注解可应用于方法
import static java.lang.annotation.ElementType.TYPE; // 静态导入TYPE常量，表示注解可应用于类、接口（包括注解类型）或枚举声明

/**
 * Annotation applied to a user-defined function that indicates that
 * the function returns null if and only if one or more of its arguments
 * are null.
 * // 应用于用户定义函数的注解，表示该函数当且仅当其一个或多个参数为null时返回null
 * // 这是SQL中STRICT函数的语义，类似于SQL中的"严格"函数行为
 * // 例如：如果函数f(x, y)被标记为Strict，那么当x或y任一为null时，f(x, y)返回null
 * // 这种行为也被称为"空值传播"（null propagation），即null值会通过函数调用传播到结果中
 * // 这与SQL中的某些函数行为一致，如COALESCE、NULLIF等函数
 * // 注意：Strict注解并不意味着函数不能处理null参数，而是定义了null参数如何影响返回值
 * // 当所有参数都不为null时，函数应该正常执行并返回非null结果
 * // 这是Calcite优化器进行查询优化时的重要信息，可以帮助优化器进行空值传播分析和谓词下推等优化
 *
 * @see SemiStrict
 * // 参见SemiStrict注解，它是另一种空值处理策略，表示函数可能对某些null参数返回非null结果
 */ // 类级别的JavaDoc注释，详细说明了Strict注解的用途和语义
@Target({METHOD, TYPE }) // 指定该注解可以应用于方法和类型（类、接口、枚举）
@Retention(RetentionPolicy.RUNTIME) // 指定该注解在运行时保留，可以通过反射机制读取
@Experimental // 标记该注解为实验性功能，表示API可能在未来版本中发生变化
public @interface Strict { // 定义Strict注解，使用@interface关键字表示这是一个注解类型
} // 注解体结束，该注解没有定义任何成员，是一个标记注解（marker annotation）
