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
 */ // Apache 许可证头部，声明版权和使用许可
package org.apache.calcite.config; // 包声明，该类属于 org.apache.calcite.config 包，用于配置相关的定义

/** Styles of character literal.
 *
 * @see Lex#charLiteralStyles */ // 枚举类文档注释：定义字符字面量的不同风格，参考 Lex 类中的 charLiteralStyles 属性
public enum CharLiteralStyle { // 声明一个公共枚举类 CharLiteralStyle，用于表示字符字面量的不同风格
  /** Standard character literal. Enclosed in single quotes, using single quotes
   * to escape. Example: {@code 'Won''t'}. */ // STANDARD 枚举值文档注释：标准字符字面量风格，使用单引号包围，内部的单引号通过重复单引号进行转义
  STANDARD, // 枚举值 STANDARD：标准风格，例如 'Won''t' 表示 Won't
  /** Single-quoted character literal with backslash escapes, as in BigQuery.
   * Example: {@code 'Won\'t'}. */ // BQ_SINGLE 枚举值文档注释：BigQuery 风格的单引号字符字面量，使用反斜杠进行转义
  BQ_SINGLE, // 枚举值 BQ_SINGLE：BigQuery 单引号风格，例如 'Won\'t' 表示 Won't
  /** Double-quoted character literal with backslash escapes, as in BigQuery.
   * Example: {@code "Won\'t"}. */ // BQ_DOUBLE 枚举值文档注释：BigQuery 风格的双引号字符字面量，使用反斜杠进行转义
  BQ_DOUBLE // 枚举值 BQ_DOUBLE：BigQuery 双引号风格，例如 "Won\'t" 表示 Won't
}
