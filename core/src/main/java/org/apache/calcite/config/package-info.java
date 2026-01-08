/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache 软件基金会许可声明：本代码在 Apache License 2.0 许可下发布
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议：查看随本工作分发的 NOTICE 文件，了解版权所有权信息
 * this work for additional information regarding copyright ownership.  // NOTICE 文件包含关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF 在 Apache License 2.0 版本下将本文件许可给你使用
 * (the "License"); you may not use this file except in compliance with  // ("许可证")：除非遵守许可证，否则你不能使用本文件
 * the License.  You may obtain a copy of the License at  // 你可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache License 2.0 的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则软件按"原样"分发
 * distributed under the License is distributed on an "AS IS" BASIS,  // 在"AS IS"（按原样）基础上分发软件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何形式的明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解特定语言的权限和
 * limitations under the License.  // 许可证下的限制
 */

/**
 * Configuration.  // 包级 Javadoc 注释：说明本包的作用是配置相关
 * 本包（org.apache.calcite.config）包含 Calcite 框架的配置管理相关类和接口
 * 主要功能包括：
 * - 管理 Calcite 的各种配置选项和参数
 * - 提供配置项的默认值和验证机制
 * - 支持运行时配置的动态修改
 * - 定义配置相关的常量和枚举类型
 * 
 * 配置包是 Calcite 框架的核心组件之一，为整个框架提供统一的配置管理能力
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD)  // 设置默认限定符：字段（FIELD）类型默认为非空（NonNull），即所有字段默认不允许为 null
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER)  // 设置默认限定符：参数（PARAMETER）类型默认为非空（NonNull），即所有方法参数默认不允许为 null
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN)  // 设置默认限定符：返回值（RETURN）类型默认为非空（NonNull），即所有方法返回值默认不允许为 null
package org.apache.calcite.config;  // 包声明：声明本文件属于 org.apache.calcite.config 包，这是 Calcite 框架中专门用于配置管理的包

import org.checkerframework.checker.nullness.qual.NonNull;  // 导入 Checker Framework 的 NonNull 注解：用于标记类型或元素不能为 null，帮助进行空值检查
import org.checkerframework.framework.qual.DefaultQualifier;  // 导入 Checker Framework 的 DefaultQualifier 注解：用于为特定位置设置默认的空值限定符
import org.checkerframework.framework.qual.TypeUseLocation;  // 导入 Checker Framework 的 TypeUseLocation 枚举：指定默认限定符应用的位置（如字段、参数、返回值等）
