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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化和执行功能
// 本文件定义了 Cassandra 适配器的核心接口，用于将 Calcite 的关系代数转换为 Cassandra 的 CQL 查询
package org.apache.calcite.adapter.cassandra;  // 声明包名，该类位于 Cassandra 适配器包中

import org.apache.calcite.plan.Convention;  // 导入 Convention 类，用于定义关系表达式的调用约定（调用约定决定了关系表达式如何被实现）
import org.apache.calcite.plan.RelOptTable;  // 导入 RelOptTable 类，表示优化器中的表对象，包含表的元数据信息
import org.apache.calcite.rel.RelNode;  // 导入 RelNode 接口，这是所有关系表达式（如扫描、过滤、投影等）的基类

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入 Nullable 注解，用于标记可能为 null 的参数或返回值，帮助静态分析工具检测空指针异常

import java.util.ArrayList;  // 导入 ArrayList 类，用于创建动态数组列表
import java.util.LinkedHashMap;  // 导入 LinkedHashMap 类，用于创建保持插入顺序的哈希映射表
import java.util.List;  // 导入 List 接口，表示有序集合
import java.util.Map;  // 导入 Map 接口，表示键值对映射

/**
 * Relational expression that uses Cassandra calling convention.
 * 这个接口定义了使用 Cassandra 调用约定的关系表达式
 * 
 * 【类的作用】：
 * CassandraRel 是一个接口，所有需要转换为 Cassandra CQL 查询的关系表达式都必须实现这个接口
 * 它继承自 RelNode（Calcite 中所有关系表达式的基类），并添加了 Cassandra 特定的实现方法
 * 
 * 【设计模式】：
 * 使用了访问者模式（Visitor Pattern），Implementor 作为访问者，遍历关系表达式树并构建 CQL 查询
 * 
 * 【核心概念】：
 * 1. 关系表达式树：SQL 查询会被解析成一棵关系表达式树，每个节点代表一个操作（如扫描、过滤、投影等）
 * 2. 调用约定（Convention）：定义了关系表达式如何被实现，Cassandra 使用自己的调用约定
 * 3. 实现（implement）：将关系表达式树转换为目标系统的查询语言（这里是 CQL）
 */
public interface CassandraRel extends RelNode {  // 定义 CassandraRel 接口，继承自 RelNode，表示所有 Cassandra 关系表达式都必须实现此接口

  // 【方法作用】：实现方法，用于将当前关系表达式转换为 CQL 查询
  // 【参数说明】：implementor - 实现器对象，用于收集查询信息并构建最终的 CQL 查询
  // 【调用时机】：当 Calcite 优化器决定将查询下推到 Cassandra 执行时，会调用此方法
  // 【实现细节】：每个实现类（如 CassandraTableScan、CassandraFilter、CassandraProject 等）都需要实现此方法，
  //               在方法中将自己的信息（如投影字段、过滤条件等）添加到 implementor 中
  void implement(Implementor implementor);  // 声明 implement 方法，用于将关系表达式转换为 CQL 查询

  /** Calling convention for relational operations that occur in Cassandra.
   *  这是 Cassandra 关系操作的调用约定常量
   *  
   *  【成员变量作用】：定义了 Cassandra 适配器的调用约定标识
   *  【调用约定概念】：在 Calcite 中，调用约定（Convention）用于区分不同的数据源和实现方式
   *                    例如：Cassandra 的调用约定是 "CASSANDRA"，JDBC 的调用约定是 "JDBC"
   *  【使用场景】：当关系表达式使用此调用约定时，Calcite 知道需要使用 Cassandra 适配器来实现
   *  【实现方式】：使用 Convention.Impl 创建一个实现，第一个参数是约定名称，第二个参数是对应的接口类
   */
  Convention CONVENTION = new Convention.Impl("CASSANDRA", CassandraRel.class);  // 创建 Cassandra 调用约定常量，名称为 "CASSANDRA"，关联到 CassandraRel 接口

  /** Callback for the implementation process that converts a tree of
   * {@link CassandraRel} nodes into a CQL query.
   *  这是实现过程的回调类，用于将 CassandraRel 节点树转换为 CQL 查询
   *  
   *  【类的作用】：Implementor 是一个访问者类，负责遍历关系表达式树并收集构建 CQL 查询所需的所有信息
   *  【设计模式】：访问者模式（Visitor Pattern），Implementor 访问每个 CassandraRel 节点，收集查询信息
   *  【核心功能】：
   *    1. 收集 SELECT 子句中的字段（selectFields）
   *    2. 收集 WHERE 子句中的条件（whereClause）
   *    3. 收集 ORDER BY 子句中的排序字段（order）
   *    4. 收集 LIMIT 和 OFFSET 信息（fetch 和 offset）
   *    5. 保存表信息（table 和 cassandraTable）
   *  【工作流程】：
   *    1. 从根节点开始调用 implement 方法
   *    2. 每个节点将自己的信息添加到 Implementor 中
   *    3. 通过 visitChild 方法递归访问子节点
   *    4. 最终收集到的所有信息用于构建 CQL 查询
   */
  class Implementor {  // 定义 Implementor 内部类，用于实现 CQL 查询的构建

    // 【成员变量作用】：存储 SELECT 子句中的投影字段
    // 【数据结构】：使用 LinkedHashMap 保持字段的插入顺序，这对某些场景很重要
    // 【键值含义】：键是字段在 Cassandra 中的原始名称，值是字段在查询结果中的别名（可能相同）
    // 【示例】：{"name": "user_name", "age": "user_age"} 表示查询 name 字段并别名为 user_name
    final Map<String, String> selectFields = new LinkedHashMap<>();  // 创建有序映射表，用于存储 SELECT 子句的字段映射关系

    // 【成员变量作用】：存储 WHERE 子句中的过滤条件
    // 【数据结构】：使用 ArrayList 存储多个条件，每个条件是一个字符串
    // 【内容格式】：每个字符串是一个 CQL 条件表达式，如 "age > 18" 或 "name = 'John'"
    // 【组合方式】：多个条件最终会用 AND 连接
    final List<String> whereClause = new ArrayList<>();  // 创建动态列表，用于存储 WHERE 子句的所有过滤条件

    // 【成员变量作用】：存储 OFFSET 子句的值，表示跳过的行数
    // 【默认值】：0 表示不跳过任何行
    // 【使用场景】：用于分页查询，如 "OFFSET 10" 表示跳过前 10 行
    // 【CQL 支持】：Cassandra 原生不支持 OFFSET，需要通过其他方式实现（如跳过前 N 行）
    int offset = 0;  // 初始化 offset 为 0，表示默认不跳过任何行

    // 【成员变量作用】：存储 FETCH/LIMIT 子句的值，表示返回的最大行数
    // 【默认值】：-1 表示没有限制，返回所有符合条件的行
    // 【使用场景】：用于限制查询结果的数量，如 "LIMIT 100" 表示最多返回 100 行
    // 【CQL 支持】：Cassandra 支持 LIMIT 子句
    int fetch = -1;  // 初始化 fetch 为 -1，表示默认不限制返回的行数

    // 【成员变量作用】：存储 ORDER BY 子句中的排序字段
    // 【数据结构】：使用 ArrayList 存储多个排序字段
    // 【内容格式】：每个字符串是一个排序表达式，如 "name ASC" 或 "age DESC"
    // 【顺序重要】：列表中的顺序决定了排序的优先级
    final List<String> order = new ArrayList<>();  // 创建动态列表，用于存储 ORDER BY 子句的所有排序字段

    // 【成员变量作用】：存储当前查询涉及的 Calcite 表对象
    // 【类型】：RelOptTable 是 Calcite 优化器中的表表示，包含表的元数据信息
    // 【用途】：用于获取表的 schema、字段信息等
    // 【可为空】：使用 @Nullable 注解标记，表示可能为 null
    @Nullable RelOptTable table;  // 声明表对象变量，可能为 null，用于存储 Calcite 中的表信息

    // 【成员变量作用】：存储当前查询涉及的 Cassandra 表对象
    // 【类型】：CassandraTable 是 Cassandra 适配器特有的表表示，包含 Cassandra 特定的元数据
    // 【用途】：用于访问 Cassandra 表的键空间、表名、分区键等信息
    // 【可为空】：使用 @Nullable 注解标记，表示可能为 null
    @Nullable CassandraTable cassandraTable;  // 声明 Cassandra 表对象变量，可能为 null，用于存储 Cassandra 特定的表信息

    /** Adds newly projected fields and restricted predicates.
     *  添加新投影字段和限制谓词（过滤条件）
     *
     * 【方法作用】：将新的投影字段和过滤条件添加到实现器中
     * 【调用时机】：当关系表达式（如 Project 或 Filter）需要将自己的信息添加到查询时调用
     * 【参数说明】：
     *   - fields: 要投影的新字段映射，键是字段名，值是别名，可以为 null
     *   - predicates: 要应用的新过滤条件列表，每个条件是一个字符串，可以为 null
     * 【实现细节】：
     *   1. 如果 fields 不为 null，将其所有键值对添加到 selectFields 中
     *   2. 如果 predicates 不为 null，将其所有条件添加到 whereClause 中
     * 【注意事项】：使用 putAll 和 addAll 方法，不会覆盖已存在的内容
     */
    public void add(@Nullable Map<String, String> fields, @Nullable List<String> predicates) {  // 定义 add 方法，用于添加投影字段和过滤条件
      if (fields != null) {  // 检查 fields 参数是否不为 null
        selectFields.putAll(fields);  // 将 fields 中的所有字段映射添加到 selectFields 中
      }  // 结束 if 块
      if (predicates != null) {  // 检查 predicates 参数是否不为 null
        whereClause.addAll(predicates);  // 将 predicates 中的所有过滤条件添加到 whereClause 中
      }  // 结束 if 块
    }  // 结束 add 方法

    // 【方法作用】：添加排序字段到实现器中
    // 【调用时机】：当遇到 OrderBy 关系表达式时调用
    // 【参数说明】：newOrder - 要添加的排序字段列表，每个元素是一个排序表达式（如 "name ASC"）
    // 【实现细节】：使用 addAll 方法将新的排序字段添加到 order 列表中
    // 【排序顺序】：列表中的顺序决定了排序的优先级，先添加的优先级更高
    public void addOrder(List<String> newOrder) {  // 定义 addOrder 方法，用于添加排序字段
      order.addAll(newOrder);  // 将 newOrder 中的所有排序字段添加到 order 列表中
    }  // 结束 addOrder 方法

    // 【方法作用】：访问子节点并递归实现
    // 【调用时机】：当关系表达式需要访问并实现其子节点时调用
    // 【参数说明】：
    //   - ordinal: 子节点的序号，通常为 0（因为大多数关系表达式只有一个子节点）
    //   - input: 子节点关系表达式
    // 【实现细节】：
    //   1. 断言 ordinal 必须为 0，确保只处理第一个子节点
    *   2. 将 input 强制转换为 CassandraRel 类型
    *   3. 调用子节点的 implement 方法，传入当前实现器，实现递归遍历
    * 【设计目的】：通过递归遍历关系表达式树，从叶子节点到根节点逐步构建查询
    public void visitChild(int ordinal, RelNode input) {  // 定义 visitChild 方法，用于访问子节点
      assert ordinal == 0;  // 断言子节点序号必须为 0，确保只处理第一个子节点
      ((CassandraRel) input).implement(this);  // 将子节点转换为 CassandraRel 并调用其 implement 方法，传入当前实现器
    }  // 结束 visitChild 方法
  }  // 结束 Implementor 内部类
}  // 结束 CassandraRel 接口
