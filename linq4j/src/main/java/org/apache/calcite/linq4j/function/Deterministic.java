/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权此文件
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下网址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // 许可证URL地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则分发软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 按"原样"基础分发，不提供任何形式的担保
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 无论是明示的还是暗示的担保或条件
 * See the License for the specific language governing permissions and // 查看许可证以了解特定语言的权限和
 * limitations under the License. // 使用限制
 */ // 许可证声明结束
package org.apache.calcite.linq4j.function; // 包声明：此注解属于org.apache.calcite.linq4j.function包

import java.lang.annotation.ElementType; // 导入ElementType枚举，用于指定注解可以应用的程序元素类型
import java.lang.annotation.Retention; // 导入Retention注解，用于指定注解的保留策略
import java.lang.annotation.RetentionPolicy; // 导入RetentionPolicy枚举，定义注解的保留策略
import java.lang.annotation.Target; // 导入Target注解，用于指定注解可以应用的程序元素类型

/**
 * Specifies that function is deterministic (i.e. returns the same output // 指定函数是确定性的（即对于相同的输入总是返回相同的输出）
 * given the same inputs). // 给定相同的输入条件
 *
 * <p>Deterministic functions can be factored out by optimizer to static fields. // 确定性函数可以被优化器提取为静态字段，以避免重复计算
 */ // 类级Javadoc注释结束
@Retention(RetentionPolicy.RUNTIME) // 指定此注解在运行时可用，通过反射机制可以读取
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.TYPE }) // 指定此注解可以应用于构造方法、方法和类型（类、接口、枚举）
public @interface Deterministic { // 声明Deterministic为public注解接口，用于标记确定性函数
} // 注解接口定义结束，此注解没有成员变量，是一个标记注解
