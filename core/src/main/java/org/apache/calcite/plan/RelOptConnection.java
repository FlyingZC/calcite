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
package org.apache.calcite.plan; // 包声明：声明该类属于org.apache.calcite.plan包，该包包含Calcite查询优化器相关的核心类

/**
 * The planner's view of a connection to a database.
 * 查询优化器对数据库连接的抽象视图
 *
 * <p>A connection contains a {@link RelOptSchema}, via which the query planner
 * can access {@link RelOptTable} objects.
 * 一个连接包含一个RelOptSchema（关系优化模式），通过这个模式，查询优化器可以访问RelOptTable（关系优化表）对象
 * 
 * 【类作用详解】：
 * RelOptConnection是Calcite查询优化器中用于表示数据库连接的接口
 * 它是优化器与底层数据源之间的桥梁，提供了访问数据库元数据和表结构的能力
 * 
 * 【核心概念】：
 * 1. RelOptConnection：关系优化连接，是优化器视角的数据库连接抽象
 * 2. RelOptSchema：关系优化模式，表示数据库的schema（模式/命名空间），包含表、视图等对象
 * 3. RelOptTable：关系优化表，表示数据库中的表，包含表的元数据信息
 * 
 * 【设计模式】：
 * - 使用接口抽象，允许不同的数据源实现不同的连接方式
 * - 遵循依赖倒置原则，优化器依赖于抽象的RelOptConnection接口，而不是具体的实现
 * 
 * 【使用场景】：
 * - 当查询优化器需要访问数据库元数据时，通过RelOptConnection获取schema信息
 * - 当优化器需要生成物理执行计划时，通过RelOptConnection了解表的存储结构和统计信息
 * - 当优化器需要进行成本估算时，通过RelOptConnection获取表的统计信息
 * 
 * 【典型实现】：
 * - DataContextImpl：Calcite提供的基本实现，基于内存数据
 * - 各种适配器实现（如Csv、JDBC、MongoDB等）各自实现RelOptConnection接口
 */
public interface RelOptConnection { // 定义公共接口RelOptConnection，表示关系优化连接，是查询优化器访问数据库的抽象接口
  /**
   * Returns the schema underlying this connection.
   * 返回此连接底层的schema（模式/命名空间）
   * 
   * 【方法作用详解】：
   * 该方法用于获取当前连接所对应的RelOptSchema对象
   * RelOptSchema是Calcite中表示数据库schema的抽象，包含了该schema下的所有表、视图等对象的元数据
   * 
   * 【返回值说明】：
   * 返回RelOptSchema对象，该对象提供了以下能力：
   * - 获取schema下的所有表（getTables方法）
   - 根据名称查找特定的表（getTable方法）
   - 获取schema的名称（getName方法）
   * 
   * 【调用时机】：
   * - 在查询解析阶段，需要验证表名是否存在
   * - 在查询优化阶段，需要访问表的元数据（如列类型、统计信息等）
   * - 在规则匹配阶段，需要了解表的结构信息
   * 
   * 【实现要点】：
   * - 实现类应该缓存RelOptSchema对象，避免重复创建
   * - RelOptSchema应该是不可变的，以确保线程安全
   * - 返回的RelOptSchema应该与连接的生命周期绑定
   * 
   * 【示例用法】：
   * RelOptConnection connection = ...;
   * RelOptSchema schema = connection.getRelOptSchema();
   * List<RelOptTable> tables = schema.getTables();
   * 
   * 【与其他组件的关系】：
   * - RelOptConnection：包含RelOptSchema
   * - RelOptSchema：包含多个RelOptTable
   * - RelOptTable：包含列信息和统计信息
   * - RelNode：在优化过程中引用RelOptTable
   */
  RelOptSchema getRelOptSchema(); // 声明抽象方法getRelOptSchema，返回类型为RelOptSchema，用于获取当前连接对应的关系优化模式对象
} // 接口定义结束
