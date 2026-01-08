/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache 软件基金会许可证声明，说明代码遵循 Apache 2.0 许可证
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，NOTICE 文件包含版权所有权的相关信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF 根据 Apache 2.0 许可证授权您使用此文件
 * (the "License"); you may not use this file except in compliance with    // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at                  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0                              // Apache 许可证的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software      // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,        // 根据许可证分发的软件按"原样"基础分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and      // 请参阅许可证以了解特定语言的权限和
 * limitations under the License.                                         // 限制条件
 */

/**
 * Defines interfaces for constructing rule-based optimizers of  // 定义用于构建关系表达式基于规则的优化器的接口
 * relational expressions.                                        // 关系表达式是 Calcite 中表示 SQL 查询计划的核心数据结构
 *                                                                  // 本包包含优化器规则、代价模型、表达式匹配等核心功能
 *                                                                  // 主要类包括：RelOptRule（优化规则）、RelOptPlanner（优化器）、RelOptCost（代价模型）
 *                                                                  // 基于规则的优化器通过应用一系列转换规则来改进查询计划
 *                                                                  // 这些规则可以将一种关系表达式转换为另一种等价但更高效的表达式
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD)  // 使用 CheckerFramework 的空值检查注解，指定字段默认为非空
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER)  // 指定方法参数默认为非空，避免空指针异常
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN)  // 指定方法返回值默认为非空，增强代码安全性
package org.apache.calcite.plan;  // 声明当前文件属于 org.apache.calcite.plan 包，这是 Calcite 查询优化器的核心包

import org.checkerframework.checker.nullness.qual.NonNull;  // 导入 CheckerFramework 的非空注解，用于标记非空类型
import org.checkerframework.framework.qual.DefaultQualifier;  // 导入默认限定符注解，用于设置包级别的默认类型限定
import org.checkerframework.framework.qual.TypeUseLocation;  // 导入类型使用位置枚举，用于指定注解应用的位置（字段、参数、返回值等）
