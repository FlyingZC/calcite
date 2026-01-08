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
 * JDBC driver for Calcite. // Apache Calcite 的 JDBC 驱动程序包的包级别文档注释
 * 
 * 这个包包含了 Calcite 框架中所有与 JDBC（Java Database Connectivity）相关的类和接口。
 * JDBC 是 Java 语言中用于连接和操作数据库的标准 API，Calcite 通过提供 JDBC 驱动，
 * 使得应用程序可以使用标准的 JDBC 接口来查询和操作 Calcite 管理的数据源。
 * 
 * 核心功能包括：
 * 1. CalciteDriver：JDBC 驱动的入口点，实现了 java.sql.Driver 接口
 * 2. CalciteConnection：表示与 Calcite 数据库的连接，扩展了标准 JDBC 连接
 * 3. CalciteStatement、CalcitePreparedStatement：用于执行 SQL 语句
 * 4. CalciteResultSet：封装查询结果集
 * 5. 元数据管理：通过 DatabaseMetaData 提供数据库结构信息
 * 6. 类型系统：处理 SQL 类型和 Java 类型之间的转换
 * 
 * 使用场景：
 * - 应用程序通过 JDBC URL "jdbc:calcite:" 连接到 Calcite
 * - 支持标准 SQL 查询语法
 * - 可以连接多种数据源（CSV、JSON、数据库等）并进行联邦查询
 * - 提供完整的 JDBC 4.2 规范支持
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD) // 注解：指定该包中所有类的字段默认为非空（NonNull），即不能为 null，由 CheckerFramework 静态分析工具使用
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER) // 注解：指定该包中所有方法参数默认为非空，即不能为 null，由 CheckerFramework 静态分析工具使用
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN) // 注解：指定该包中所有方法返回值默认为非空，即不能为 null，由 CheckerFramework 静态分析工具使用
package org.apache.calcite.jdbc; // 声明此文件属于 org.apache.calcite.jdbc 包，这是 Calcite JDBC 驱动的核心包

// 导入 CheckerFramework 的非空类型注解，用于静态分析时标记类型不能为 null
import org.checkerframework.checker.nullness.qual.NonNull; // 导入 NonNull 注解，表示被注解的元素不能为 null，用于编译时静态检查
// 导入 CheckerFramework 的默认限定符注解，用于设置包级别的默认类型限定规则
import org.checkerframework.framework.qual.DefaultQualifier; // 导入 DefaultQualifier 注解，用于为包中的特定类型使用位置设置默认的非空限定
// 导入 CheckerFramework 的类型使用位置枚举，用于指定限定符应用的位置（字段、参数、返回值等）
import org.checkerframework.framework.qual.TypeUseLocation; // 导入 TypeUseLocation 枚举，包含 FIELD、PARAMETER、RETURN 等常量，用于限定非空注解的应用范围
