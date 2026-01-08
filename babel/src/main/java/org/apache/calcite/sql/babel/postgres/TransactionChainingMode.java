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
 */ // Apache许可证声明，说明代码遵循Apache 2.0许可证，包含版权和使用条款
package org.apache.calcite.sql.babel.postgres; // 声明包名，表示该类属于org.apache.calcite.sql.babel.postgres包，专门用于处理PostgreSQL方言相关的SQL语法

import org.apache.calcite.sql.Symbolizable; // 导入Symbolizable接口，该接口用于标记可以被符号化的SQL元素，使枚举值能够以符号形式表示

/**
 * Defines the keywords that can occur immediately after the "ROLLBACK" or "COMMIT" keywords.
 * 定义了可以紧跟在"ROLLBACK"或"COMMIT"关键字之后的关键字
 *
 * 这个枚举类定义了PostgreSQL数据库中事务链式控制模式，用于控制事务提交或回滚后的行为
 * 事务链式模式决定了在当前事务结束后是否立即开始一个新的事务
 * 这对于需要连续执行多个事务的场景非常重要，可以减少事务管理的开销
 *
 * @see SqlCommit // 参见SqlCommit类，该类处理COMMIT语句的解析和执行
 * @see SqlRollback // 参见SqlRollback类，该类处理ROLLBACK语句的解析和执行
 * @see <a href="https://www.postgresql.org/docs/current/sql-commit.html">COMMIT specification</a> // PostgreSQL官方COMMIT语句规范文档链接
 * @see <a href="https://www.postgresql.org/docs/current/sql-rollback.html">ROLLBACK specification</a> // PostgreSQL官方ROLLBACK语句规范文档链接
 */
public enum TransactionChainingMode implements Symbolizable { // 定义一个名为TransactionChainingMode的枚举类，实现Symbolizable接口，表示事务链式控制模式
  AND_CHAIN, // 枚举常量：表示AND CHAIN模式，在COMMIT或ROLLBACK后立即开始一个新的事务，新事务继承当前事务的事务特性（如隔离级别等），这样可以保持事务的连续性，减少事务管理的开销
  AND_NO_CHAIN; // 枚举常量：表示AND NO CHAIN模式，在COMMIT或ROLLBACK后不立即开始新的事务，当前事务结束后会回到默认的事务模式，这是PostgreSQL的默认行为，需要显式使用BEGIN语句才能开始新的事务

  @Override public String toString() { // 重写Object类的toString方法，使用@Override注解确保正确覆盖父类方法，该方法用于将枚举值转换为字符串表示
    return super.toString().replace("_", " "); // 调用父类Enum的toString()方法获取枚举名称，然后将下划线"_"替换为空格" "，例如"AND_CHAIN"会变成"AND CHAIN"，这样更符合SQL语法的可读性要求
  } // toString方法结束，返回格式化后的字符串表示
} // TransactionChainingMode枚举类定义结束
