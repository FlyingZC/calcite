/* // Apache许可证头文件，声明版权信息和使用许可，遵循Apache 2.0许可证协议
 * Licensed to the Apache Software Foundation (ASF) under one or more // 授权给Apache软件基金会一个或多个贡献者许可协议
 * contributor license agreements.  See the NOTICE file distributed with // 参与者许可协议，查看随此工作分发的NOTICE文件
 * this work for additional information regarding copyright ownership.  // 该NOTICE文件包含关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0版许可证将此文件授权给您
 * (the "License"); you may not use this file except in compliance with // ("许可证")；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache 2.0许可证的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 在许可证下按"原样"基础分发，不提供任何形式的明示或暗示保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何种类的保证或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 请参阅许可证以了解管理权限和限制的特定语言
 * limitations under the License. // 许可证下的限制
 */
package org.apache.calcite.linq4j.function; // 声明包名，该注解位于org.apache.calcite.linq4j.function包下，属于LINQ4J函数模块

import java.lang.annotation.Retention; // 导入Retention注解，用于指定注解的保留策略（源码、编译时或运行时）
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举，定义注解的三种保留策略：SOURCE、CLASS、RUNTIME
import java.lang.annotation.Target; // 导入Target注解，用于指定注解可以应用的程序元素类型

import static java.lang.annotation.ElementType.CONSTRUCTOR; // 静态导入CONSTRUCTOR类型，表示注解可以应用于构造方法
import static java.lang.annotation.ElementType.FIELD; // 静态导入FIELD类型，表示注解可以应用于字段（成员变量）
import static java.lang.annotation.ElementType.METHOD; // 静态导入METHOD类型，表示注解可以应用于方法
import static java.lang.annotation.ElementType.PACKAGE; // 静态导入PACKAGE类型，表示注解可以应用于包声明
import static java.lang.annotation.ElementType.TYPE; // 静态导入TYPE类型，表示注解可以应用于类、接口（包括注解类型）或枚举声明

/**
 * Annotation that indicates that a class, interface, field or method // 注解类文档注释：该注解用于标记类、接口、字段或方法
 * is experimental, not part of the public API, and subject to change // 表示它们是实验性的，不属于公共API的一部分，可能会发生变化
 * or removal. // 或者被移除
 *
 * <p>And yes, it is flagged experimental. We may move it elsewhere in future, // 该注解本身也被标记为实验性的，未来可能会移动到其他位置
 * when we re-think the maturity model. // 当我们重新思考成熟度模型时
 */
@Target({PACKAGE, TYPE, FIELD, METHOD, CONSTRUCTOR }) // @Target元注解：指定Experimental注解可以应用于包、类型（类/接口/枚举）、字段、方法和构造方法
@Retention(RetentionPolicy.SOURCE) // @Retention元注解：指定注解保留策略为SOURCE，表示注解只在源代码中保留，编译后会被丢弃，不会出现在字节码中
@Experimental // 该注解自身也被标记为@Experimental，表示它本身也是实验性的，符合自我描述的原则
public @interface Experimental { // 定义一个名为Experimental的注解接口，使用@interface关键字声明这是一个注解类型而非普通接口
} // 注解定义结束，该注解是一个标记注解（marker annotation），不包含任何成员，仅用于标识实验性元素
