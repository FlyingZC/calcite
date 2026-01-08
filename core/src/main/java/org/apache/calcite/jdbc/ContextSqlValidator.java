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
package org.apache.calcite.jdbc; // 声明包名，此类属于 org.apache.calcite.jdbc 包，该包包含与 JDBC 相关的 Calcite 类

import org.apache.calcite.config.CalciteConnectionConfig; // 导入 Calcite 连接配置类，用于配置 Calcite 连接的各种参数
import org.apache.calcite.prepare.CalciteCatalogReader; // 导入 Calcite 目录读取器类，用于读取和处理数据库目录信息（如表、视图等）
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入 SQL 标准操作符表类，包含所有标准 SQL 操作符（如 SELECT、JOIN、WHERE 等）
import org.apache.calcite.sql.validate.SqlValidatorImpl; // 导入 SQL 验证器实现类，这是 SQL 验证的核心实现，ContextSqlValidator 将继承此类

import com.google.common.collect.ImmutableList; // 导入 Google Guava 库的不可变列表类，用于创建不可变的列表对象

/**
 * A SqlValidator with schema and type factory of the given
 * {@link org.apache.calcite.jdbc.CalcitePrepare.Context}.
 * 这是一个带有给定 CalcitePrepare.Context 的 schema（模式）和 type factory（类型工厂）的 SQL 验证器
 * 
 * <p>This class is only used to derive data type for DDL sql node.
 * 这个类仅用于为 DDL（数据定义语言）SQL 节点推导数据类型
 * 
 * Usually we deduce query sql node data type(i.e. the {@code SqlSelect})
 * during the validation phrase.
 * 通常我们在验证阶段推导查询 SQL 节点的数据类型（即 SqlSelect 节点）
 * 
 * DDL nodes don't have validation,
 * DDL 节点没有验证过程
 * 
 * they can be executed directly through
 * {@link org.apache.calcite.server.DdlExecutor}.
 * 它们可以通过 DdlExecutor 直接执行
 * 
 * <p>During the execution, {@link org.apache.calcite.sql.SqlDataTypeSpec} uses
 * this validator to derive its type.
 * 在执行过程中，SqlDataTypeSpec（SQL 数据类型规范）使用此验证器来推导其类型
 * 
 * 类作用总结：
 * 1. ContextSqlValidator 是一个专门为 DDL 语句设计的轻量级 SQL 验证器
 * 2. 它继承自 SqlValidatorImpl，但主要用于数据类型推导而非完整的 SQL 验证
 * 3. DDL 语句（如 CREATE TABLE、ALTER TABLE 等）不需要完整的验证过程，但需要确定数据类型
 * 4. 这个验证器使用给定的 Context（上下文）来获取 schema 和 type factory，从而能够正确推导数据类型
 * 5. 它是 Calcite 处理 DDL 语句时的关键组件，确保数据类型能够正确解析和执行
 */
public class ContextSqlValidator extends SqlValidatorImpl { // 定义 ContextSqlValidator 类，继承自 SqlValidatorImpl，获得 SQL 验证的核心功能

  /**
   * Create a {@code ContextSqlValidator}.
   * 创建一个 ContextSqlValidator 实例
   * 
   * @param context Prepare context. 准备上下文，包含 schema、type factory 等必要信息
   * @param mutable Whether to get the mutable schema. 是否获取可变的 schema（可修改的 schema）
   */
  public ContextSqlValidator(CalcitePrepare.Context context, boolean mutable) { // 构造方法，接收准备上下文和是否可变的标志
    super(SqlStdOperatorTable.instance(), getCatalogReader(context, mutable), // 调用父类 SqlValidatorImpl 的构造方法，传入标准操作符表、目录读取器、类型工厂和默认配置
        context.getTypeFactory(), Config.DEFAULT); // context.getTypeFactory() 获取类型工厂，Config.DEFAULT 使用默认配置
  } // 构造方法结束

  /**
   * Get a CalciteCatalogReader based on the context and mutable flag.
   * 根据上下文和可变标志获取 CalciteCatalogReader（目录读取器）
   * 
   * @param context Prepare context. 准备上下文，包含 schema 信息
   * @param mutable Whether to get the mutable schema. 是否获取可变的 schema
   * @return A CalciteCatalogReader instance. 返回一个 CalciteCatalogReader 实例
   */
  private static CalciteCatalogReader getCatalogReader( // 私有静态方法，用于创建目录读取器
      CalcitePrepare.Context context, boolean mutable) { // 参数：准备上下文和是否可变的标志
    return new CalciteCatalogReader( // 创建并返回一个新的 CalciteCatalogReader 实例
        mutable ? context.getMutableRootSchema() : context.getRootSchema(), // 如果 mutable 为 true，获取可变的根 schema；否则获取普通的根 schema。getMutableRootSchema() 返回可以修改的 schema，getMutableRootSchema() 返回只读的 schema
        ImmutableList.of(), // 传入空列表，表示不使用特定的 schema 路径，使用默认的 schema 搜索路径
        context.getTypeFactory(), // 传入类型工厂，用于创建和操作 SQL 数据类型
        CalciteConnectionConfig.DEFAULT); // 传入默认的连接配置，使用 Calcite 的默认连接设置
  } // 方法结束
} // 类结束
