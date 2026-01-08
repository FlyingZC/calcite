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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.sql.babel.postgres; // 定义包名，该类属于org.apache.calcite.sql.babel.postgres包，用于处理PostgreSQL方言

import org.apache.calcite.sql.Symbolizable; // 导入Symbolizable接口，用于标记可转换为SqlLiteral符号的枚举类型

/**
 * Contains enums convertible to {@link org.apache.calcite.sql.SqlLiteral}, used for
 * {@link org.apache.calcite.sql.SqlSetOption#name} and
 * {@link org.apache.calcite.sql.SqlSetOption#value} fields.
 */
// 类功能说明：SqlSetOptions类是一个工具类，包含可转换为SqlLiteral（SQL字面量）的枚举类型
// 这些枚举用于SqlSetOption的name和value字段，主要用于处理PostgreSQL的SET和RESET语句
// SqlLiteral是Calcite中表示SQL字面量的类，通过枚举可以精确表示特定的SQL关键字
public class SqlSetOptions { // 定义SqlSetOptions类，这是一个工具类，用于提供PostgreSQL SET/RESET语句相关的枚举常量
  private SqlSetOptions() { // 私有构造方法，防止实例化该类，因为这是一个纯工具类，只包含静态枚举定义
  } // 构造方法体为空，确保该类不能被实例化

  /**
   * {@link org.apache.calcite.sql.SqlIdentifier} can not be used
   * as a name parameter. For example, in PostgreSQL, these two SQL commands
   * have different meanings:
   * <ul>
   *   <li><code>RESET ALL</code> resets all settable run-time parameters to default values.</li>
   *   <li><code>RESET "ALL"</code> resets parameter "ALL".</li>
   * </ul>
   * Using only {@link org.apache.calcite.sql.SqlIdentifier} makes
   * it impossible to distinguish which case is being referred to.
   * This enum has been introduced to avoid this problem.
   */
  // Names枚举说明：定义了PostgreSQL SET/RESET语句中可以作为参数名的特殊关键字
  // 问题背景：如果只使用SqlIdentifier（SQL标识符），无法区分关键字和标识符
  // 例如：RESET ALL（重置所有参数）vs RESET "ALL"（重置名为"ALL"的参数）
  // 通过定义枚举，可以明确区分关键字和标识符，避免歧义
  // 实现Symbolizable接口，使得枚举值可以转换为SqlLiteral符号
  public enum Names implements Symbolizable { // 定义Names枚举，表示PostgreSQL SET/RESET语句中可用的参数名称关键字
    ALL, // 枚举常量ALL：表示"所有"参数，用于RESET ALL语句，重置所有运行时参数为默认值
    TRANSACTION, // 枚举常量TRANSACTION：表示事务相关参数，用于SET TRANSACTION语句，设置事务特性
    TRANSACTION_SNAPSHOT, // 枚举常量TRANSACTION_SNAPSHOT：表示事务快照，用于SET TRANSACTION SNAPSHOT语句，指定事务快照ID
    SESSION_CHARACTERISTICS_AS_TRANSACTION, // 枚举常量SESSION_CHARACTERISTICS_AS_TRANSACTION：表示会话特性，用于SET SESSION CHARACTERISTICS AS TRANSACTION语句，设置会话级别的事务特性
    TIME_ZONE, // 枚举常量TIME_ZONE：表示时区参数，用于SET TIME ZONE语句，设置会话的时区
    ROLE; // 枚举常量ROLE：表示角色参数，用于SET ROLE语句，设置当前会话的角色

    @Override public String toString() { // 重写toString方法，用于将枚举值转换为字符串表示
      return super.toString().replace("_", " "); // 将枚举名称中的下划线替换为空格，例如TRANSACTION_SNAPSHOT转换为"TRANSACTION SNAPSHOT"，以匹配PostgreSQL语法
    } // toString方法结束，返回格式化后的字符串
  } // Names枚举定义结束

  /**
   * Makes possible to represent NONE, LOCAL as {@link org.apache.calcite.sql.SqlLiteral}.
   * It is needed for the following SQL statements:
   * <ul>
   *   <li><code>SET TIME ZONE LOCAL</code></li>
   *   <li><code>SET ROLE NONE</code></li>
   * </ul>
   *
   * @see <a href="https://www.postgresql.org/docs/current/sql-set.html">
   * PostgreSQL SET documentation</a>
   * @see <a href="https://www.postgresql.org/docs/current/sql-set-role.html">
   * PostgreSQL SET ROLE documentation</a>
   */
  // Values枚举说明：定义了PostgreSQL SET语句中可以作为参数值的特殊关键字
  // 作用：NONE和LOCAL是PostgreSQL中特殊的保留字，不能作为普通标识符使用
  // 使用场景：
  // 1. SET TIME ZONE LOCAL：将时区设置为本地时区
  // 2. SET ROLE NONE：取消当前角色设置，恢复为默认角色
  // 实现Symbolizable接口，使得枚举值可以转换为SqlLiteral符号
  public enum Values implements Symbolizable { // 定义Values枚举，表示PostgreSQL SET语句中可用的特殊参数值关键字
    NONE, // 枚举常量NONE：表示"无"或"空"，用于SET ROLE NONE语句，取消当前角色设置
    LOCAL // 枚举常量LOCAL：表示"本地"，用于SET TIME ZONE LOCAL语句，将时区设置为本地时区
  } // Values枚举定义结束
} // SqlSetOptions类定义结束
