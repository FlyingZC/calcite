/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会(ASF)许可证声明，允许在特定条件下使用和分发代码
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的NOTICE文件以获取版权所有权相关信息
 * this work for additional information regarding copyright ownership.  // The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache许可证2.0版授权您使用此文件
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0版的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 按"原样"基础分发，不附带任何明示或暗示的担保或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 无论是明示的还是暗示的担保或条件，包括但不限于对适销性、特定用途适用性的担保
 * See the License for the specific language governing permissions and // 查看许可证以了解许可证下特定的语言管理权限和
 * limitations under the License. // 许可证下的限制
 */

/**
 * Utilities to analyze data sets. // 用于分析数据集的工具集合，提供数据统计、分布分析等功能
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD) // 指定字段位置默认为非空类型，使用CheckerFramework进行空值检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER) // 指定方法参数位置默认为非空类型，防止空指针异常
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN) // 指定方法返回值位置默认为非空类型，确保返回值不为null
package org.apache.calcite.profile; // 声明此包为org.apache.calcite.profile，Apache Calcite的数据分析工具包，包含数据集分析相关的类和接口

import org.checkerframework.checker.nullness.qual.NonNull; // 导入CheckerFramework的非空注解，用于标记不能为null的类型
import org.checkerframework.framework.qual.DefaultQualifier; // 导入CheckerFramework的默认限定符注解，用于设置包级别的默认类型限定
import org.checkerframework.framework.qual.TypeUseLocation; // 导入CheckerFramework的类型使用位置枚举，指定限定符应用的具体位置（字段、参数、返回值等）
