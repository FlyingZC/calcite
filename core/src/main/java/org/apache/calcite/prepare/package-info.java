/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache 软件基金会许可证声明，本代码遵循 Apache 2.0 许可证
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，详见随此工作分发的 NOTICE 文件，了解版权所有权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF 根据 Apache 许可证 2.0 版本授权您使用此文件
 * (the "License"); you may not use this file except in compliance with  // （"许可证"）；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache 许可证 2.0 的在线地址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * distributed under the License is distributed on an "AS IS" BASIS,  // 是按"原样"基础分发的，不附带任何明示或暗示的担保或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不承担任何形式的担保或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解管理权限和
 * limitations under the License.  // 许可证下的限制的具体语言
 */

/**
 * Preparation of queries (parsing, planning and implementation).  // 查询准备（解析、规划和实现）- 本包负责 SQL 查询的准备阶段工作，包括将 SQL 文本解析为抽象语法树、进行查询优化规划、以及生成可执行的计划实现；这是 Calcite 查询处理流程的核心入口包
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD)  // 使用 Checker Framework 的默认类型限定符，指定所有字段（FIELD）默认为非空（NonNull），帮助静态检查器进行空值分析，提高代码安全性
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER)  // 指定所有方法参数（PARAMETER）默认为非空，确保方法调用时参数不能为 null
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN)  // 指定所有方法返回值（RETURN）默认为非空，确保方法调用返回的结果不为 null
package org.apache.calcite.prepare;  // 声明此文件属于 org.apache.calcite.prepare 包，该包包含查询准备相关的所有类，如 Prepare、CalcitePrepareImpl 等

import org.checkerframework.checker.nullness.qual.NonNull;  // 导入 Checker Framework 的 NonNull 注解，用于标记类型或元素不允许为 null，配合 @DefaultQualifier 使用
import org.checkerframework.framework.qual.DefaultQualifier;  // 导入 Checker Framework 的 DefaultQualifier 注解，用于设置包级别的默认类型限定符
import org.checkerframework.framework.qual.TypeUseLocation;  // 导入 TypeUseLocation 枚举，用于指定默认限定符应用的类型使用位置（如字段、参数、返回值等）
