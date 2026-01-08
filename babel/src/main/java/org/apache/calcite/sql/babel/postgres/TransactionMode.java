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
package org.apache.calcite.sql.babel.postgres; // 定义包路径：org.apache.calcite.sql.babel.postgres，这是Calcite SQL解析器中用于处理PostgreSQL方言的包

import org.apache.calcite.sql.Symbolizable; // 导入Symbolizable接口，该接口允许枚举类型被转换为符号表示，用于SQL语法解析和生成

/**
 * Defines transaction mode keywords. // 定义事务模式关键字枚举类，用于表示PostgreSQL数据库中事务的各种模式配置
 * 
 * 这个枚举类实现了Symbolizable接口，表示它可以在SQL语法树中被识别和序列化。
 * 它定义了PostgreSQL中所有可能的事务模式关键字，包括：
 * 1. 读写模式（READ_WRITE/READ_ONLY）
 * 2. 可延迟性（DEFERRABLE/NOT_DEFERRABLE）
 * 3. 隔离级别（ISOLATION_LEVEL_*）
 * 
 * 这些模式关键字通常用于BEGIN、SET TRANSACTION等SQL语句中，用于控制事务的行为特性。
 * 例如：BEGIN TRANSACTION READ ONLY ISOLATION LEVEL SERIALIZABLE;
 */
public enum TransactionMode implements Symbolizable { // 定义一个公共枚举类TransactionMode，实现Symbolizable接口，表示PostgreSQL事务模式的各种选项
  READ_WRITE, // 枚举常量：读写模式，表示事务可以进行读写操作，这是默认的事务模式
  READ_ONLY, // 枚举常量：只读模式，表示事务只能进行读操作，不能修改数据，这可以用于优化查询性能
  DEFERRABLE, // 枚举常量：可延迟模式，表示事务可以延迟约束检查到事务提交时，仅用于READ ONLY事务
  NOT_DEFERRABLE, // 枚举常量：不可延迟模式，表示事务不能延迟约束检查，约束会在每次操作时立即检查，这是默认行为
  ISOLATION_LEVEL_SERIALIZABLE, // 枚举常量：可序列化隔离级别，最高隔离级别，完全隔离事务，防止脏读、不可重复读和幻读，但并发性能最低
  ISOLATION_LEVEL_REPEATABLE_READ, // 枚举常量：可重复读隔离级别，防止脏读和不可重复读，但可能出现幻读，PostgreSQL通过快照实现
  ISOLATION_LEVEL_READ_COMMITTED, // 枚举常量：读已提交隔离级别，防止脏读，但可能出现不可重复读和幻读，这是PostgreSQL的默认隔离级别
  ISOLATION_LEVEL_READ_UNCOMMITTED; // 枚举常量：读未提交隔离级别，最低隔离级别，允许读取未提交的数据，可能出现脏读、不可重复读和幻读

  @Override public String toString() { // 重写toString()方法，将枚举常量转换为字符串表示，用于SQL语句的生成
    return super.toString().replace("_", " "); // 调用父类Enum的toString()方法获取枚举常量名称，然后将下划线替换为空格，例如"READ_WRITE"变成"READ WRITE"
  } // toString()方法结束，返回格式化后的字符串，符合PostgreSQL SQL语法要求
} // TransactionMode枚举类定义结束
