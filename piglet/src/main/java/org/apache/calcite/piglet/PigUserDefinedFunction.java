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
package org.apache.calcite.piglet; // Piglet 包，包含 Calcite 与 Apache Pig 集成的相关类

import org.apache.calcite.schema.Function; // Calcite 函数接口，定义函数的基本行为
import org.apache.calcite.sql.SqlFunctionCategory; // SQL 函数分类枚举，标识函数的类别
import org.apache.calcite.sql.SqlIdentifier; // SQL 标识符类，表示 SQL 中的名称
import org.apache.calcite.sql.SqlKind; // SQL 操作类型枚举，定义各种 SQL 操作的种类
import org.apache.calcite.sql.SqlSyntax; // SQL 语法枚举，定义函数的调用语法
import org.apache.calcite.sql.parser.SqlParserPos; // SQL 解析位置类，记录 SQL 解析时的位置信息
import org.apache.calcite.sql.type.SqlOperandMetadata; // SQL 操作数元数据接口，描述函数操作数的类型和约束
import org.apache.calcite.sql.type.SqlOperandTypeInference; // SQL 操作数类型推断接口，推断操作数的类型
import org.apache.calcite.sql.type.SqlReturnTypeInference; // SQL 返回值类型推断接口，推断函数的返回值类型
import org.apache.calcite.sql.validate.SqlUserDefinedFunction; // Calcite 用户自定义函数基类，提供自定义函数的基本实现

import org.apache.pig.FuncSpec; // Pig 函数规范类，描述 Pig UDF 的规范信息（类名、参数等）

import com.google.common.collect.ImmutableList; // Google Guava 不可变列表类，提供线程安全的不可变列表实现

/** Pig user-defined function. */ // Pig 用户自定义函数，用于在 Calcite 中表示和调用 Apache Pig 的用户定义函数
public class PigUserDefinedFunction extends SqlUserDefinedFunction { // 继承 Calcite 的 SqlUserDefinedFunction 基类，实现 Pig UDF 与 Calcite 的集成
  public final FuncSpec funcSpec; // Pig 函数规范对象，存储 Pig UDF 的完整规范信息（包括类名、构造函数参数等），final 表示一旦初始化后不可修改

  private PigUserDefinedFunction(SqlIdentifier opName, // 函数操作名称标识符，表示函数在 SQL 中的名称
      SqlReturnTypeInference returnTypeInference, // 返回值类型推断器，用于推断函数返回值的 SQL 类型
      SqlOperandTypeInference operandTypeInference, // 操作数类型推断器，用于推断函数参数的类型（可为 null）
      SqlOperandMetadata operandMetadata, // 操作数元数据，描述函数参数的类型、数量和约束
      Function function, // Calcite 函数对象，实际执行函数逻辑的函数实例
      FuncSpec funcSpec) { // Pig 函数规范对象，包含 Pig UDF 的完整规范信息
    super(opName, SqlKind.OTHER_FUNCTION, returnTypeInference, // 调用父类 SqlUserDefinedFunction 构造函数，传入函数名称、类型（其他函数）、返回值类型推断器
        operandTypeInference, operandMetadata, function, // 传入操作数类型推断器、操作数元数据、函数对象
        SqlFunctionCategory.USER_DEFINED_CONSTRUCTOR, SqlSyntax.FUNCTION); // 指定函数分类为用户定义构造函数，语法为普通函数调用语法
    this.funcSpec = funcSpec; // 将传入的 Pig 函数规范对象赋值给成员变量，保存 Pig UDF 的规范信息
  }

  public PigUserDefinedFunction(String name, // 函数名称字符串，表示函数在 SQL 中的标识名称
      SqlReturnTypeInference returnTypeInference, // 返回值类型推断器，用于推断函数返回值的 SQL 类型
      SqlOperandMetadata operandMetadata, Function function, // 操作数元数据（描述参数类型和约束）和 Calcite 函数对象
      FuncSpec funcSpec) { // Pig 函数规范对象，包含 Pig UDF 的完整规范信息
    this(new SqlIdentifier(ImmutableList.of(name), SqlParserPos.ZERO), // 调用私有构造函数，将函数名转换为 SqlIdentifier 对象（使用不可变列表和零位置）
        returnTypeInference, null, operandMetadata, function, funcSpec); // 操作数类型推断器设为 null，其他参数原样传递
  }

  public PigUserDefinedFunction(String name, // 函数名称字符串，表示函数在 SQL 中的标识名称
      SqlReturnTypeInference returnTypeInference, // 返回值类型推断器，用于推断函数返回值的 SQL 类型
      SqlOperandMetadata operandMetadata, Function function) { // 操作数元数据（描述参数类型和约束）和 Calcite 函数对象
    this(name, returnTypeInference, operandMetadata, function, null); // 调用上一个构造函数，将 Pig 函数规范对象设为 null（表示不指定具体的 Pig 函数规范）
  }
}
