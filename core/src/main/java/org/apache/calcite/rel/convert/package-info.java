/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // 授予 Apache 软件基金会（ASF）一个或多个许可证
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的 NOTICE 文件
 * this work for additional information regarding copyright ownership.  // 以获取有关版权所有权的其他信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF 根据 Apache 许可证 2.0 版本授予您此文件
 * (the "License"); you may not use this file except in compliance with // （"许可证"）；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache 许可证 2.0 的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则软件
 * distributed under the License is distributed on an "AS IS" BASIS,  // 在"按原样"基础上分发，不附带任何明示或暗示的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 保证或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and  // 查看许可证以了解特定语言的权限和
 * limitations under the License.  // 许可证下的限制
 */

/**
 * Defines relational expressions and rules for converting between calling  // 定义用于在不同调用约定之间转换的关系表达式和规则
 * conventions.  // 调用约定（Convention）是 Calcite 中表示不同数据访问方式的概念，例如 Enumerable、Physical 等
 */  // 这个包包含了关系表达式在不同调用约定之间进行转换所需的核心组件
package org.apache.calcite.rel.convert;  // 声明当前包为 org.apache.calcite.rel.convert，这是 Calcite 中负责关系表达式转换的核心包
