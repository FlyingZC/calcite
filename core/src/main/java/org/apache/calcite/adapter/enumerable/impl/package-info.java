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
// Apache 许可证头文件，声明代码版权和授权信息，遵循 Apache 2.0 许可协议

/**
 * Calcite-specific classes for implementation of regular and window aggregates.
 */
// 包级别的 JavaDoc 注释，说明此包的作用：包含用于实现常规聚合函数和窗口聚合函数的 Calcite 特定类
// regular aggregates: 常规聚合函数，如 SUM、COUNT、AVG 等，对整个结果集进行聚合
// window aggregates: 窗口聚合函数，如 ROW_NUMBER、RANK、SUM OVER 等，对窗口内的数据行进行聚合
// Calcite-specific: 这些类是 Calcite 框架特有的实现，不是通用的 Java 类
// implementation: 这些类用于实现聚合功能，包括聚合逻辑、累加器管理、结果计算等

package org.apache.calcite.adapter.enumerable.impl;
// 包声明语句，定义当前包的完整路径
// org.apache.calcite: Apache Calcite 项目的顶级包名
// adapter: 适配器模块，用于将不同数据源适配到 Calcite 查询引擎
// enumerable: 可枚举适配器，将关系代数转换为可枚举的 Java 代码（使用 linq4j 库）
// impl: 实现包，包含具体的实现类，通常是内部使用的实现细节
// 此包专门包含聚合函数（包括常规聚合和窗口聚合）的实现类
