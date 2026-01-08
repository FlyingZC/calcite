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
// Apache Calcite 是一个动态数据管理框架，提供了 SQL 解析、优化、执行等功能
// Arrow 是 Apache Arrow 项目，是一个跨语言的列式内存数据格式，用于高效的数据分析
package org.apache.calcite.adapter.arrow; // 定义包名，该包包含 Apache Arrow 适配器的相关类

import org.apache.calcite.plan.Convention; // 导入 Convention 类，用于定义关系代数表达式的调用约定（calling convention）
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable 类，表示优化器中的表对象
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，这是 Calcite 中所有关系表达式的基础接口

import org.apache.calcite.util.ImmutableIntList; // 导入 ImmutableIntList 类，用于创建不可变的整数列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，用于标记可能为 null 的字段

import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组列表
import java.util.List; // 导入 List 接口，表示列表集合

import static com.google.common.base.Preconditions.checkArgument; // 导入 checkArgument 方法，用于参数校验

/**
 * Relational expression that uses the Arrow calling convention.
 * 这是一个使用 Arrow 调用约定的关系表达式接口
 * ArrowRel 是所有 Arrow 适配器中关系表达式的基础接口，它扩展了 Calcite 的 RelNode 接口
 * 任何需要在 Arrow 格式上执行的查询操作都必须实现此接口
 * 该接口定义了如何将关系表达式转换为可以在 Arrow 数据上执行的操作
 */
public interface ArrowRel extends RelNode { // 定义 ArrowRel 接口，继承自 RelNode，表示 Arrow 格式的关系表达式
  void implement(Implementor implementor); // 定义 implement 方法，用于将关系表达式转换为可执行的实现

  /** Calling convention for relational operations that occur in Arrow.
   * 定义 Arrow 调用约定，用于标识所有使用 Arrow 格式的关系操作
   * 调用约定（Convention）是 Calcite 中用于区分不同数据源或执行机制的重要概念
   * 每个调用约定都有一个名称和对应的接口类型
   * 在这里，"ARROW" 是调用约定的名称，ArrowRel.class 是实现该约定的接口类型
   * 当优化器遇到使用 Arrow 约定的关系节点时，会知道这些节点需要在 Arrow 数据格式上执行
   */
  Convention CONVENTION = new Convention.Impl("ARROW", ArrowRel.class); // 创建并初始化 ARROW 调用约定常量

  /** Callback for the implementation process that converts a tree of
   * {@link ArrowRel} nodes into a SQL query.
   * Implementor 是实现过程的回调类，负责将 ArrowRel 节点树转换为可执行的查询
   * 它在整个实现过程中收集信息，包括选择字段、过滤条件、表信息等
   * 当遍历关系表达式树时，每个 ArrowRel 节点都会调用 Implementor 的方法来添加自己的实现细节
   * 最终，Implementor 会包含完整的查询执行计划
   */
  class Implementor { // 定义 Implementor 内部类，用于收集和构建查询实现
    @Nullable List<Integer> selectFields; // selectFields 字段：存储查询结果需要选择的字段索引列表，可能为 null（表示选择所有字段）
    final List<String> whereClause = new ArrayList<>(); // whereClause 字段：存储查询的所有过滤条件（WHERE 子句），使用 ArrayList 动态存储多个条件字符串
    @Nullable RelOptTable table; // table 字段：存储优化器中的表对象，包含表的元数据信息，可能为 null
    @Nullable ArrowTable arrowTable; // arrowTable 字段：存储 Arrow 表对象，包含 Arrow 特定的表信息和数据访问方法，可能为 null

    /** Adds new predicates.
     * 添加新的谓词（过滤条件）到 WHERE 子句中
     * 这个方法用于在实现过程中收集查询的过滤条件
     * 过滤条件通常来自 SQL 查询的 WHERE 子句或其他谓词下推操作
     *
     * @param predicates Predicates - 要添加的谓词列表，每个谓词是一个字符串表达式
     */
    void addFilters(List<String> predicates) { // 定义 addFilters 方法，用于添加过滤条件
      whereClause.addAll(predicates); // 将所有谓词添加到 whereClause 列表中
    }

    /** Adds newly projected fields.
     * 添加新投影的字段到选择列表中
     * 投影操作是指从表中选择特定的列，这是 SQL 查询中最常见的操作之一
     * 这个方法使用 ImmutableIntList 来确保字段索引列表是不可变的，提高安全性
     *
     * @param fields New fields to be projected from a query - 要投影的字段索引列表
     */
    void addProjectFields(List<Integer> fields) { // 定义 addProjectFields 方法，用于设置投影字段
      selectFields = ImmutableIntList.copyOf(fields); // 将字段列表转换为不可变的 ImmutableIntList 并赋值给 selectFields
    }

    /** 访问输入节点的方法
     * 这个方法用于在关系表达式树中遍历和访问输入节点
     * 它是递归遍历关系表达式树的关键方法
     * 当访问一个输入节点时，会将输入节点转换为 ArrowRel 类型并调用其 implement 方法
     * 这样可以确保整个关系表达式树都被正确地转换为 Arrow 实现
     * ordinal 参数表示输入的序号，在大多数情况下，关系操作只有一个输入（ordinal=0）
     *
     * @param ordinal 输入的序号，通常为 0（表示第一个输入）
     * @param input 输入的关系节点，需要是 ArrowRel 类型
     */
    public void visitInput(int ordinal, RelNode input) { // 定义 visitInput 方法，用于访问输入节点
      checkArgument(ordinal == 0); // 校验 ordinal 必须为 0，确保只处理第一个输入
      ((ArrowRel) input).implement(this); // 将输入节点转换为 ArrowRel 类型并调用其 implement 方法，传入当前的 Implementor 实例
    }
  }
}
