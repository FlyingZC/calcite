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
package org.apache.calcite.sql.test; // 指定该类所在的包路径，属于 org.apache.calcite.sql.test 包

/**
 * Implementation of {@link SqlTester} that can parse and validate SQL, // 实现 SqlTester 接口，能够解析和验证 SQL 语句
 * and convert it to relational algebra. // 并将 SQL 转换为关系代数（Relational Algebra，即 Calcite 中的 RelNode）
 *
 * <p>This tester is therefore suitable for many general-purpose tests, // 因此该测试器适用于多种通用测试场景
 * including SQL parsing, validation, and SQL-to-Rel conversion. // 包括 SQL 解析、验证以及 SQL 到 Rel 的转换测试
 */
public class SqlValidatorTester extends AbstractSqlTester { // 定义 SqlValidatorTester 类，继承自 AbstractSqlTester 抽象类，AbstractSqlTester 提供了 SQL 测试的基础框架和通用功能
  /** Default instance of this tester. */ // 定义该测试器的默认实例，这是一个静态常量，可以在整个项目中共享使用
  public static final SqlValidatorTester DEFAULT = new SqlValidatorTester(); // 创建并初始化 SqlValidatorTester 的默认实例，使用默认构造函数，该实例是静态最终的，不可被修改，提供全局唯一的默认测试器实例
}
