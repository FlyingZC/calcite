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
 * Cassandra query provider. // Cassandra 查询提供者：这是 Apache Calcite 框架中用于连接和查询 Apache Cassandra 数据库的适配器包，它将 Cassandra 作为 Calcite 的数据源，允许用户使用标准 SQL 查询 Cassandra 中的数据
 *
 * <p>There is one table for each Cassandra column family. // 每个 Cassandra 列族（Column Family，在较新版本中称为表）对应一个 Calcite 表，这意味着 Cassandra 中的每个数据表都会被映射为 Calcite 中的一个可查询表对象，从而实现 SQL 到 Cassandra 查询的转换
 */ // 该包实现了以下核心功能：1. 将 Cassandra 的表结构映射为 Calcite 的 RelOptTable（关系优化表）；2. 将 SQL 查询转换为 Cassandra 的 CQL（Cassandra Query Language）查询；3. 提供 Cassandra 数据的枚举器（Enumerator）用于结果集遍历；4. 实现了过滤（Filter）、投影（Project）、扫描（Scan）、排序（Sort）、限制（Limit）等关系代数操作符的 Cassandra 特定实现；5. 通过规则（Rules）将逻辑关系表达式转换为物理的 Cassandra 执行计划
package org.apache.calcite.adapter.cassandra; // 定义包名为 org.apache.calcite.adapter.cassandra，这是 Calcite 框架中专门用于 Cassandra 适配器的包，该包包含所有与 Cassandra 数据源相关的类，包括：CassandraTable（表定义）、CassandraTableScan（表扫描）、CassandraFilter（过滤操作）、CassandraProject（投影操作）、CassandraSort（排序操作）、CassandraLimit（限制操作）、CassandraEnumerator（结果集枚举器）、CassandraToEnumerableConverter（到可枚举转换器的规则）、CassandraMethod（方法定义）、CqlToSqlTypeConversionRules（CQL 到 SQL 类型转换规则）等
