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
// Apache Calcite JDBC适配器包，包含JDBC相关的适配器实现
package org.apache.calcite.adapter.jdbc;

// 导入CorrelationId类，用于标识关联变量（correlation variable），在子查询相关联的场景中使用
import org.apache.calcite.rel.core.CorrelationId;

// 导入Type类，用于表示Java类型系统中的类型信息
import java.lang.reflect.Type;

/**
 * JdbcCorrelationDataContextBuilder是一个接口，用于收集创建JdbcCorrelationDataContext所需的所有关联变量
 * 
 * 【背景知识】：
 * 1. 关联变量（Correlation Variable）：在SQL中，当子查询引用外部查询的列时，这些外部列被称为关联变量
 *    例如：SELECT * FROM emp WHERE dept_id IN (SELECT id FROM dept WHERE emp.dept_id = dept.id)
 *    这里的emp.dept_id就是一个关联变量，它将子查询与外部查询关联起来
 * 
 * 2. JdbcCorrelationDataContext：这是一个特殊的数据上下文，用于在JDBC适配器中处理关联变量
 *    它允许在执行SQL查询时，将关联变量的值传递给子查询
 * 
 * 3. 为什么需要这个Builder接口？
 *    - 在Calcite将关系代数转换为SQL时，需要识别和收集所有关联变量
 *    - 这些关联变量需要被组织成一个数据上下文，以便在执行时能够访问它们的值
 *    - 这个Builder接口提供了一种标准的方式来收集这些关联变量
 * 
 * 【设计模式】：这是一个Builder模式的变体，用于构建复杂对象（JdbcCorrelationDataContext）
 * 
 * 【使用场景】：
 * - 当JDBC适配器需要处理包含子查询的SQL时
 * - 当子查询引用外部查询的列时（即存在关联变量）
 * - 需要将这些关联变量传递给JDBC数据源执行时
 */
interface JdbcCorrelationDataContextBuilder {
  /**
   * 收集一个关联变量（correlation variable）并添加到构建器中
   * 
   * 【方法作用】：
   * 将一个关联变量添加到构建器中，以便后续创建JdbcCorrelationDataContext时使用
   * 每次调用这个方法都会添加一个新的关联变量到构建器中
   * 
   * 【参数说明】：
   * @param id 关联变量的唯一标识符（CorrelationId）
   *        - 每个关联变量都有一个唯一的ID，用于在关系代数中标识它
   *        - CorrelationId是Calcite中用于标识关联变量的类
   *        - 例如：在关系代数树中，同一个关联变量可能在多个地方被引用，但它们共享同一个CorrelationId
   * 
   * @param ordinal 关联变量在关联集合中的序号（位置索引）
   *        - 表示这个关联变量在所有关联变量中的位置
   *        - 从0开始计数，第一个关联变量的ordinal为0
   *        - 这个序号用于在运行时快速定位关联变量的值
   *        - 例如：如果有3个关联变量，它们的ordinal分别是0、1、2
   * 
   * @param type 关联变量的Java类型（Type）
   *        - 表示这个关联变量的数据类型
   *        - 使用Java反射的Type接口来表示类型信息
   *        - 例如：Integer.class、String.class等
   *        - 这个类型信息用于类型检查和类型转换
   * 
   * 【返回值说明】：
   * @return 返回添加的关联变量的索引（int）
   *         - 这个返回值通常是关联变量在构建器中的索引
   *         - 可以用于后续引用这个关联变量
   *         - 通常返回值与ordinal参数相同，但不保证总是如此
   * 
   * 【使用示例】：
   * JdbcCorrelationDataContextBuilder builder = ...;
   * int index1 = builder.add(correlationId1, 0, Integer.class);  // 添加第一个关联变量
   * int index2 = builder.add(correlationId2, 1, String.class);   // 添加第二个关联变量
   * 
   * 【注意事项】：
   * - 同一个CorrelationId可以被多次添加（如果它被多次引用）
   * - ordinal参数应该按照关联变量的逻辑顺序递增
   * - type参数必须与关联变量的实际类型匹配
   * - 这个方法通常在遍历关系代数树时被调用，以收集所有关联变量
   */
  int add(CorrelationId id, int ordinal, Type type);
}
