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
 * Defines core relational expressions. // 定义核心关系表达式，这是Calcite框架中最基础的关系代数操作集合
 *
 * <h2>Related packages and classes</h2> // 相关包和类的详细说明
 * <ul>
 *
 * <li>Package <code>
 * <a href="../logical/package-summary.html">org.apache.calcite.rel.logical</a></code>
 * contains logical relational expressions // org.apache.calcite.rel.logical包包含逻辑关系表达式，这些是查询优化过程中的逻辑表示，不涉及具体的物理实现细节
 *
 * <li>Package <code>
 * <a href="../package-summary.html">org.apache.calcite.rex</a></code>
 * defines the relational expression API // org.apache.calcite.rex包定义了行表达式(Row Expression)API，用于表示标量表达式、谓词等，是构建关系表达式的基础
 *
 * </ul>
 */
package org.apache.calcite.rel.core; // 声明当前包为org.apache.calcite.rel.core，这是Calcite框架中核心关系表达式的包路径
