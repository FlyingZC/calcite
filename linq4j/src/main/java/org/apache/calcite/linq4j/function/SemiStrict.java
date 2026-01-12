/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，说明该文件遵循Apache 2.0许可证
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，详见随此文件分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权您使用此文件
 * (the "License"); you may not use this file except in compliance with  // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache许可证2.0的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS,  // 否则根据许可证分发的软件按"原样"基础分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不附带任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解许可的语言
 * limitations under the License.  // 以及许可证下的限制
 */  // 许可证声明结束
package org.apache.calcite.linq4j.function;  // 声明该类所在的包：org.apache.calcite.linq4j.function，属于Calcite的LINQ4J功能包

import java.lang.annotation.Retention;  // 导入Java注解Retention，用于指定注解的保留策略
import java.lang.annotation.RetentionPolicy;  // 导入Java注解RetentionPolicy，定义注解保留策略的枚举
import java.lang.annotation.Target;  // 导入Java注解Target，用于指定注解可以应用的目标

import static java.lang.annotation.ElementType.METHOD;  // 静态导入METHOD元素类型，表示注解可以应用于方法
import static java.lang.annotation.ElementType.TYPE;  // 静态导入TYPE元素类型，表示注解可以应用于类、接口等类型

/**  // JavaDoc注释开始，用于文档化此注解
 * Annotation applied to a user-defined function that indicates that  // 应用于用户自定义函数的注解，表示该函数具有特定的null处理行为
 * the function always returns null if one or more of its arguments  // 当函数的一个或多个参数为null时，该函数总是返回null
 * are null but also may return null at other times.  // 但在其他情况下也可能返回null（这是与Strict注解的关键区别）
 *
 * <p>Compare with {@link Strict}:  // 与Strict注解进行对比说明
 * <ul>  // 无序列表开始
 *   <li>A strict function returns null if and only if it has a null argument  // Strict函数当且仅当有null参数时才返回null（严格的双向条件）
 *   <li>A semi-strict function returns null if it has a null argument  // SemiStrict函数当有null参数时返回null（单向条件，也可能在其他情况返回null）
 * </ul>  // 无序列表结束
 */  // JavaDoc注释结束
@Target({METHOD, TYPE })  // 指定该注解可以应用于方法和类型（类、接口、枚举等）
@Retention(RetentionPolicy.RUNTIME)  // 指定该注解在运行时保留，可以通过反射获取
@Experimental  // 标记该注解为实验性特性，表示API可能会在未来版本中发生变化
public @interface SemiStrict {  // 声明SemiStrict为公共注解接口，用于标记半严格函数
}  // 注解定义结束
