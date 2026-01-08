/*
 * Licensed to the Apache Software Foundation (ASF) under one or more  // Apache软件基金会许可证声明，允许在Apache许可证2.0下使用
 * contributor license agreements.  See the NOTICE file distributed with  // 贡献者许可协议，查看NOTICE文件了解更多版权信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0  // 关于版权所有权的附加信息。ASF根据Apache许可证2.0版授权此文件给您
 * (the "License"); you may not use this file except in compliance with  // ("许可证")；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下位置获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache许可证2.0的在线地址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则软件
 * distributed under the License is distributed on an "AS IS" BASIS,  // 按原样分发，不提供任何形式的明示或暗示保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何类型的保证或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解特定的语言管理权限和
 * limitations under the License.  // 许可证下的限制
 */

/**
 * Query provider based on a JDBC data source.  // 基于JDBC数据源的查询提供器。这个包提供了通过JDBC连接到各种关系型数据库的功能，允许Calcite查询这些数据库中的数据
 * 
 * 【包作用详解】：
 * org.apache.calcite.adapter.jdbc包是Calcite框架中最重要的适配器包之一，它提供了与JDBC数据源集成的能力。
 * 
 * 【核心功能】：
 * 1. JDBC适配器：允许Calcite通过JDBC连接到任何支持JDBC的关系型数据库（如MySQL、PostgreSQL、Oracle等）
 * 2. 查询下推：将Calcite的逻辑查询计划转换为SQL语句，下推到数据库执行，利用数据库的查询优化能力
 * 3. 数据源抽象：将外部JDBC数据源抽象为Calcite的Schema和Table，可以在Calcite中像操作本地表一样操作远程数据库表
 * 4. 类型映射：处理Calcite类型系统与JDBC类型系统之间的映射转换
 * 5. 规则转换：提供优化规则，将Calcite的RelNode转换为JDBC可执行的SQL
 * 
 * 【主要类说明】：
 * - JdbcSchema：表示JDBC数据源的Schema，包含表定义和元数据
 * - JdbcTable：表示JDBC数据库中的一张表，提供表的元数据信息
 * - JdbcConvention：JDBC适配器的调用约定，定义了如何将RelNode转换为SQL
 * - JdbcToEnumerableConverter：将JDBC RelNode转换为可枚举的RelNode
 * - JdbcTableScan：表示对JDBC表的扫描操作
 * - JdbcRules：包含JDBC适配器的各种优化规则
 * - JdbcRel：JDBC关系表达式的基类
 * - JdbcImplementor：将RelNode实现为SQL语句的实现器
 * - JdbcAggregate：JDBC聚合操作的实现
 * - JdbcFilter：JDBC过滤条件的实现
 * - JdbcJoin：JDBC连接操作的实现
 * - JdbcProject：JDBC投影操作的实现
 * - JdbcSort：JDBC排序操作的实现
 * - JdbcValues：JDBC VALUES子句的实现
 * - JdbcTableModify：JDBC表修改操作（INSERT/UPDATE/DELETE）的实现
 * 
 * 【工作流程】：
 * 1. 用户通过JdbcSchemaFactory创建JdbcSchema，配置JDBC连接信息
 * 2. JdbcSchema从数据库获取元数据，创建JdbcTable对象
 * 3. Calcite优化器生成逻辑计划
 * 4. JdbcRules规则将逻辑计划中的部分操作转换为JdbcRel节点
 * 5. JdbcImplementor将JdbcRel节点转换为SQL语句
 * 6. 执行SQL，通过JDBC获取结果集
 * 7. 将结果集转换为Calcite的Enumerable格式
 * 
 * 【查询下推优化】：
 * JDBC适配器的一个重要特性是查询下推。它可以将以下操作下推到数据库执行：
 * - 过滤条件（WHERE子句）
 * - 投影（SELECT子句）
 * - 聚合（GROUP BY、聚合函数）
 * - 排序（ORDER BY）
 * - 连接（JOIN）
 * - 限制（LIMIT、OFFSET）
 * 
 * 这样可以减少数据传输量，利用数据库的优化器，提高查询性能。
 * 
 * 【类型映射】：
 * Calcite类型系统与JDBC类型之间的映射关系：
 * - INTEGER -> java.sql.Types.INTEGER
 * - BIGINT -> java.sql.Types.BIGINT
 * - VARCHAR -> java.sql.Types.VARCHAR
 * - DATE -> java.sql.Types.DATE
 * - TIMESTAMP -> java.sql.Types.TIMESTAMP
 * - DECIMAL -> java.sql.Types.DECIMAL
 * - 等等...
 * 
 * 【使用场景】：
 * 1. 跨数据源查询：将JDBC数据库与其他数据源（如CSV、MongoDB等）进行联邦查询
 * 2. 查询加速：利用数据库的索引和优化能力加速查询
 * 3. 数据虚拟化：无需将数据迁移到Calcite，直接在原数据库上查询
 * 4. SQL统一：使用Calcite的SQL方言查询不同的数据库
 * 
 * 【注意事项】：
 * 1. 不同的JDBC驱动可能有不同的SQL方言特性
 * 2. 某些Calcite特性可能无法下推到特定数据库
 * 3. 需要正确配置JDBC连接池以提高性能
 * 4. 大数据量查询时注意内存管理
 */
package org.apache.calcite.adapter.jdbc;  // 定义JDBC适配器包的包声明，所有JDBC相关的类都在这个包下
