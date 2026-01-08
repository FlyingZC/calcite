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
// 声明包名：org.apache.calcite.adapter.enumerable，表示这个类属于Calcite框架的可枚举适配器模块
package org.apache.calcite.adapter.enumerable;

// 导入 RexWindowExclusion 类，这是Calcite中用于表示窗口函数排除子句（EXCLUDE）的类
// EXCLUDE 子句用于在窗口函数中指定哪些行应该被排除在聚合计算之外
import org.apache.calcite.rex.RexWindowExclusion;

/**
 * 标记接口，用于允许 {@link org.apache.calcite.adapter.enumerable.AggImplementor}（聚合实现器）
 * 判断它是在常规聚合上下文中使用还是在窗口聚合上下文中使用
 * 
 * 【类的作用和设计意图】：
 * 1. 这是一个标记接口（Marker Interface），本身不定义任何具体行为，主要用于类型标识
 * 2. 在Calcite中，聚合函数可以在两种上下文中使用：
 *    - 常规聚合（GROUP BY）：将多行数据聚合成单行结果
 *    - 窗口聚合（OVER窗口）：为每一行数据计算一个聚合值，不减少行数
 * 3. AggImplementor 是聚合函数的实现器，需要根据不同的上下文生成不同的实现代码
 * 4. 通过检查 AggContext 是否实现了 WinAggContext 接口，可以判断当前是否处于窗口聚合上下文
 * 5. 如果实现了 WinAggContext，说明是窗口聚合，需要使用窗口相关的逻辑（如窗口边界、排除子句等）
 * 
 * 【继承关系】：
 * - 继承自 AggContext 接口，WinAggContext 是 AggContext 的特化版本
 * - AggContext 提供了聚合函数实现所需的基本上下文信息（如参数类型、返回类型等）
 * - WinAggContext 在 AggContext 的基础上增加了窗口函数特有的信息（如排除子句）
 * 
 * 【使用场景示例】：
 * - 当 Calcite 的查询优化器遇到窗口函数（如 SUM(x) OVER (PARTITION BY y ORDER BY z)）时
 * - 会创建一个实现了 WinAggContext 的上下文对象
 * - AggImplementor 检测到上下文是 WinAggContext 类型，就知道需要生成窗口聚合的实现代码
 * - 而不是普通的 GROUP BY 聚合实现代码
 * 
 * 【窗口函数基础知识】：
 * - 窗口函数允许在不减少行数的情况下进行聚合计算
 * - 例如：SELECT salary, SUM(salary) OVER (ORDER BY salary) FROM emp
 * - 结果会保留所有行，同时计算累计和
 * - 窗口函数的关键组成部分：
 *   1. PARTITION BY：分组，类似 GROUP BY，但不减少行数
 *   2. ORDER BY：定义窗口内行的排序
 *   3. ROWS/RANGE/BETWEEN：定义窗口边界
 *   4. EXCLUDE：排除某些行（本接口的核心功能）
 * 
 * 【EXCLUDE 子句的作用】：
 * - EXCLUDE 子句允许在窗口聚合中排除特定的行
 * - 例如：SUM(salary) OVER (ORDER BY hire_date EXCLUDE CURRENT ROW)
 * - 这表示在计算累计和时，排除当前行
 * - 常见的 EXCLUDE 选项：
 *   - CURRENT ROW：排除当前行
 *   - GROUP：排除与当前行在同一组的所有行
 *   - TIES：排除与当前行在 ORDER BY 键上值相同的所有行
 *   - NO OTHERS：不排除任何行（默认）
 * - 这个接口的 getExclude() 方法就是用于获取这个排除子句信息
 */
public interface WinAggContext extends AggContext {
  /**
   * 获取窗口函数分组（group）的排除子句（EXCLUDE）
   * 
   * 【方法作用】：
   * - 返回窗口函数中 EXCLUDE 子句的定义
   * - EXCLUDE 子句指定了在窗口聚合计算中应该排除哪些行
   * - 这个信息对于正确实现窗口聚合逻辑至关重要
   * 
   * 【返回值说明】：
   * - RexWindowExclusion：表示窗口排除子句的对象
   * - 可能的值包括：
   *   - RexWindowExclusion.CURRENT_ROW：排除当前行
   *   - RexWindowExclusion.GROUP：排除当前组
   *   - RexWindowExclusion.TIES：排除并列行
   *   - RexWindowExclusion.NO_OTHERS：不排除任何行
   * - 如果 SQL 中没有指定 EXCLUDE 子句，默认返回 NO_OTHERS
   * 
   * 【使用示例】：
   * - SQL: SUM(salary) OVER (ORDER BY hire_date EXCLUDE CURRENT ROW)
   * - getExclude() 返回: RexWindowExclusion.CURRENT_ROW
   * - 实现时需要在计算窗口和时跳过当前行
   * 
   * 【实现注意事项】：
   * - AggImplementor 在生成窗口聚合代码时，需要根据返回的排除规则调整计算逻辑
   * - 例如，如果排除当前行，计算时需要先计算包含当前行的窗口，再减去当前行的值
   * - 或者直接在遍历窗口时跳过当前行
   * 
   * 【与其他方法的关系】：
   * - 这个方法是 WinAggContext 特有的，AggContext 接口没有这个方法
   * - 与 AggContext 中的其他方法（如 getReturnRelDataType()、getParameterRelDataTypes() 等）
   *   配合使用，提供完整的窗口聚合实现所需信息
   */
  RexWindowExclusion getExclude();
}
