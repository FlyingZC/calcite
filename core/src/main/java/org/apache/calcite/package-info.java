// Apache 软件基金会许可证声明头部，表明此代码遵循 Apache 2.0 许可协议
// 许可证允许用户在遵守特定条件的情况下自由使用、修改和分发代码
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

/**
 * Calcite 主包的 Javadoc 文档注释，描述了整个 org.apache.calcite 包的用途
 * Calcite 是一个动态数据管理平台，提供了 SQL 解析、优化、执行等核心功能
 * 这个包包含了 Calcite 的核心接口和类，是整个框架的入口点
 * 主要功能包括：
 * 1. SQL 解析：将 SQL 语句解析为抽象语法树（AST）
 * 2. 查询优化：基于成本的优化器（CBO）对查询计划进行优化
 * 3. 数据模型：提供关系代数和表达式系统
 * 4. 适配器机制：支持多种数据源的统一访问
 * 5. 元数据管理：管理表、列、函数等元数据信息
 * 
 * Main package for Calcite, the dynamic data management platform.
 */
@CalciteImmutable  // 这是一个注解，标记整个包为不可变的（immutable），意味着包中的类应该是线程安全的，其对象创建后状态不可改变
// 声明当前文件对应的包名为 org.apache.calcite
// package-info.java 是 Java 特殊文件，用于在包级别添加文档、注解等
// 它不包含任何类或接口定义，仅用于包级别的元数据
package org.apache.calcite;  // 定义包名，org.apache.calcite 是 Calcite 框架的根包，所有核心功能都在这个包及其子包中
