/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache 软件基金会许可证声明：本代码在 Apache 许可证 2.0 版本下授权
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议：查看随本工作分发的 NOTICE 文件，了解版权所有权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF 授予您 Apache 许可证 2.0 版本
 * (the "License"); you may not use this file except in compliance with  // ("许可证")：除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache 许可证 2.0 的官方网站地址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证分发的软件是按"原样"基础分发的
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何形式的明示或暗示保证或条件
 * See the License for the specific language governing permissions and  // 查看许可证以了解特定的语言管理权限
 * limitations under the License.  // 以及许可证下的限制
 */

/**
 * Provides model files, in JSON format, defining schemas and other metadata.  // 提供 JSON 格式的模型文件，用于定义模式（schema）和其他元数据
 *
 * <p>Models are specified using a <code>model=&lt;uri&gt;</code> parameter on  // 模型通过 JDBC 连接字符串中的 model=<uri> 参数指定
 * the JDBC connect string. Calcite loads the model while initializing the  // Calcite 在初始化连接时加载模型
 * connection. It first parses the JSON, then uses a  // 它首先解析 JSON，然后使用
 * {@link org.apache.calcite.model.ModelHandler} as visitor over the parse  // ModelHandler 作为访问者遍历解析树
 * tree.  // 解析树是一种表示 JSON 结构的树形数据结构
 *
 * <p>There are standard implementations of schema and table, but the user can  // 有标准的 schema 和 table 实现，但用户可以
 * provide their own by implementing the  // 通过实现以下接口提供自己的实现
 * {@link org.apache.calcite.schema.SchemaFactory}  // SchemaFactory 接口：用于创建自定义 Schema
 * or {@link org.apache.calcite.schema.TableFactory}  // 或 TableFactory 接口：用于创建自定义 Table
 * interfaces and including a custom schema in the model.  // 接口并在模型中包含自定义 schema
 *
 * <p>There are several examples of schemas in the  // 在教程中有多个 schema 示例
 * <a href="https://calcite.apache.org/docs/tutorial.html">tutorial</a>.  // 教程链接：https://calcite.apache.org/docs/tutorial.html
 */
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.FIELD)  // 默认限定符注解：指定字段（FIELD）类型默认为非空（NonNull），用于静态代码检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.PARAMETER)  // 默认限定符注解：指定参数（PARAMETER）类型默认为非空（NonNull），用于静态代码检查
@DefaultQualifier(value = NonNull.class, locations = TypeUseLocation.RETURN)  // 默认限定符注解：指定返回值（RETURN）类型默认为非空（NonNull），用于静态代码检查
package org.apache.calcite.model;  // 包声明：声明此文件属于 org.apache.calcite.model 包，该包包含模型文件相关的类

import org.checkerframework.checker.nullness.qual.NonNull;  // 导入 Checker Framework 的 NonNull 注解，用于标记类型不为 null
import org.checkerframework.framework.qual.DefaultQualifier;  // 导入 Checker Framework 的 DefaultQualifier 注解，用于设置默认的非空限定符
import org.checkerframework.framework.qual.TypeUseLocation;  // 导入 Checker Framework 的 TypeUseLocation 枚举，用于指定类型使用的位置（字段、参数、返回值等）
