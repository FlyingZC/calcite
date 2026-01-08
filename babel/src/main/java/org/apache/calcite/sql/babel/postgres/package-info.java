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
 * Parse tree for PostgreSQL extensions used by the Babel parser.
 * 用于 Babel 解析器的 PostgreSQL 扩展的解析树
 * 
 * 本包(org.apache.calcite.sql.babel.postgres)包含了 Apache Calcite 框架中专门用于支持 PostgreSQL 数据库方言的扩展功能。
 * 
 * 核心作用和功能说明：
 * 1. PostgreSQL 方言支持：本包提供了对 PostgreSQL 特有 SQL 语法的解析和处理能力，使 Calcite 能够理解和执行 PostgreSQL 特定的 SQL 语句
 * 
 * 2. Babel 解析器集成：Babel 是 Calcite 的一个多方言 SQL 解析器，本包作为 Babel 解析器的 PostgreSQL 扩展模块，负责处理 PostgreSQL 特有的语法结构
 * 
 * 3. 解析树节点：本包中包含的类构成了 PostgreSQL SQL 语句的抽象语法树(AST)节点，每个节点代表 PostgreSQL SQL 语法中的一个特定结构
 * 
 * 4. 事务控制支持：PostgreSQL 有丰富的事务控制语法，本包提供了对 BEGIN、COMMIT、ROLLBACK、SAVEPOINT 等事务控制语句的支持
 * 
 * 5. 会话配置支持：PostgreSQL 提供了大量的会话级配置选项(SET 命令)，本包提供了对这些配置语句的解析和处理
 * 
 * 6. 特殊命令支持：包括 DISCARD、SHOW 等 PostgreSQL 特有的管理命令
 * 
 * 主要包含的类和功能：
 * - SqlBegin: 解析 PostgreSQL 的 BEGIN 事务开始语句
 * - SqlCommit: 解析 PostgreSQL 的 COMMIT 事务提交语句
 * - SqlRollback: 解析 PostgreSQL 的 ROLLBACK 事务回滚语句
 * - SqlDiscard: 解析 PostgreSQL 的 DISCARD 命令，用于丢弃会话状态
 * - SqlSetOptions: 解析 PostgreSQL 的 SET 命令，用于设置运行时参数
 * - SqlShow: 解析 PostgreSQL 的 SHOW 命令，用于显示运行时参数的值
 * - TransactionMode: 定义 PostgreSQL 的事务隔离级别和访问模式
 * - TransactionChainingMode: 定义 PostgreSQL 的事务链模式
 * 
 * 技术架构说明：
 * - 所有类都继承自 Calcite 的 SqlNode 基类，遵循 Calcite 的 SQL 解析树架构
 * - 每个类实现了 unparse 方法，用于将解析树转换回 PostgreSQL 格式的 SQL 文本
 * - 每个类实现了 validate 方法，用于语义验证
 * - 每个类实现了 getOperator 方法，返回对应的 SqlOperator，用于查询优化
 * 
 * 使用场景：
 * 1. 当需要将 PostgreSQL SQL 语句转换为 Calcite 的逻辑计划时，Babel 解析器会使用本包中的类来构建解析树
 * 2. 当需要将 Calcite 的逻辑计划转换为 PostgreSQL SQL 语句时，本包中的类负责生成符合 PostgreSQL 语法的 SQL 文本
 * 3. 在异构数据源查询场景中，Calcite 可以通过本包的支持，统一处理 PostgreSQL 数据源的查询请求
 * 4. 在数据迁移和同步场景中，本包帮助理解和转换 PostgreSQL 特有的 SQL 语句
 * 
 * 与 Calcite 核心的关系：
 * - 本包是 Calcite SQL 解析层的一部分，位于 org.apache.calcite.sql 包体系下
 * - 本包依赖于 Calcite 的核心解析框架，包括 SqlParser、SqlNode、SqlOperator 等
 * - 本包与 babel 包的其他模块(如通用 SQL 解析)协同工作，共同构成完整的多方言 SQL 解析能力
 * 
 * 扩展性说明：
 * - 开发者可以通过继承本包中的类来支持 PostgreSQL 的其他特有语法
 * - 可以通过注册新的 SqlOperator 来扩展 PostgreSQL 特有的函数和操作符
 * - 可以通过自定义 SqlVisitor 来实现针对 PostgreSQL 的特定优化规则
 * 
 * 性能考虑：
 * - 解析树的设计考虑了内存效率，避免了不必要的对象创建
 * - unparse 操作采用流式生成，减少字符串拼接的开销
 * - 验证操作采用延迟执行，只在需要时进行语义检查
 * 
 * 兼容性说明：
 * - 本包支持 PostgreSQL 的主流版本，包括 9.x、10.x、11.x、12.x、13.x、14.x、15.x
 * - 对于不同版本间的语法差异，通过版本检测和条件编译来处理
 * - 对于已废弃的语法，仍然保持向后兼容性
 */
package org.apache.calcite.sql.babel.postgres;
