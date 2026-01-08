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
package org.apache.calcite.sql.babel; // 声明包名，该类属于 org.apache.calcite.sql.babel 包，这是用于支持不同数据库方言（Babel）的SQL解析和处理包

/**
 * Enumerates the collection type of a table: {@code MULTISET} allows duplicates
 * and {@code SET} does not.
 * 枚举表的集合类型：MULTISET 允许重复行，SET 不允许重复行
 *
 * <p>This feature is supported in Teradata, which originally required rows in a
 * table to be unique, and later added the {@code MULTISET} keyword to
 * its {@code CREATE TABLE} command to allow the duplicate rows.
 * 这个特性在 Teradata 数据库中得到支持，Teradata 最初要求表中的行必须是唯一的，后来在其 CREATE TABLE 命令中添加了 MULTISET 关键字来允许重复行
 *
 * <p>In other databases and in the SQL standard, {@code MULTISET} is the only
 * supported option, so there is no explicit syntax.
 * 在其他数据库和 SQL 标准中，MULTISET 是唯一支持的选项，因此没有显式的语法
 */
public enum TableCollectionType { // 定义一个名为 TableCollectionType 的公共枚举类型，用于表示表的集合类型（是否允许重复行）
  /**
   * Table collection type is not specified.
   * 表集合类型未指定
   *
   * <p>Defaults to {@code MULTISET} in ANSI mode,
   * and {@code SET} in Teradata mode.
   * 在 ANSI 模式下默认为 MULTISET，在 Teradata 模式下默认为 SET
   */
  UNSPECIFIED, // 枚举常量：未指定集合类型，表示用户在创建表时没有明确指定是 SET 还是 MULTISET，系统会根据当前的模式自动选择默认值

  /**
   * Duplicate rows are not permitted.
   * 不允许重复行
   */
  SET, // 枚举常量：集合类型为 SET，表示该表不允许存在重复的行，类似于数学集合的概念，每行数据必须唯一

  /**
   * Duplicate rows are permitted, in compliance with the ANSI SQL:2011 standard.
   * 允许重复行，符合 ANSI SQL:2011 标准
   */
  MULTISET, // 枚举常量：集合类型为 MULTISET，表示该表允许存在重复的行，这是 ANSI SQL 标准的默认行为，也是大多数数据库的默认行为
} // 枚举定义结束
