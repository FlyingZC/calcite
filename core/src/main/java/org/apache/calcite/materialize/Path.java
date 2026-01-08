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
// Apache 许可证头，声明此代码的版权和使用许可
package org.apache.calcite.materialize; // 声明所属包：Apache Calcite 物化视图相关包

import com.google.common.collect.ImmutableList; // 导入 Google Guava 库的不可变列表类，用于创建不可修改的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的空值检查注解，用于标记可能为 null 的参数

import java.util.List; // 导入 Java 标准库的 List 接口，用于表示有序集合

/** A sequence of {@link Step}s from a root node (fact table) to another node
 * (dimension table), possibly via intermediate dimension tables. */
// 类的 JavaDoc 注释：表示从根节点（事实表）到另一个节点（维度表）的 Step 序列，可能通过中间维度表
// Path 类代表在物化视图优化过程中，从事实表到维度表的一条访问路径
// 这条路径由多个 Step（步骤）组成，每个 Step 代表一次表之间的连接操作
// 例如：事实表 -> 中间维度表 -> 目标维度表，这样的路径包含两个 Step
// Path 是 Calcite 物化视图优化器中的核心概念，用于识别和优化星型模型或雪花模型中的查询路径
class Path { // 定义 Path 类，包级私有（无 public 修饰），仅在 materialize 包内使用
  final List<Step> steps; // 成员变量：存储路径中的所有步骤，使用不可变 List，final 修饰表示引用不可变
  // steps 是一个 Step 对象的列表，每个 Step 代表从一张表到另一张表的连接
  // 例如：Step1: fact_table -> dim_table1, Step2: dim_table1 -> dim_table2
  // final 修饰确保 steps 列表引用在对象创建后不能被改变（但列表内容如果是可变的仍可改变，这里使用 ImmutableList 保证完全不可变）
  private final int id; // 成员变量：路径的唯一标识符，private 修饰表示外部无法直接访问，final 修饰表示创建后不可改变
  // id 用于唯一标识一条路径，在哈希和相等性比较中使用
  // 每条 Path 对象都有一个唯一的 id，即使两条路径的 steps 内容相同，id 也不同
  // 这在物化视图优化中很重要，因为不同的路径可能代表不同的优化策略

  Path(List<Step> steps, int id) { // 构造方法：创建 Path 对象，接收步骤列表和 id 参数
    this.steps = ImmutableList.copyOf(steps); // 将传入的 steps 列表转换为不可变列表并赋值给成员变量
    // ImmutableList.copyOf() 创建原始列表的防御性副本，确保外部无法通过原始引用修改 steps
    // 这保证了 Path 对象的不可变性，符合函数式编程的最佳实践
    this.id = id; // 将传入的 id 参数赋值给成员变量
    // id 是路径的唯一标识符，通常由调用方（如 PathFactory）生成并保证唯一性
  } // 构造方法结束

  @Override public int hashCode() { // 重写 Object 类的 hashCode 方法，用于计算 Path 对象的哈希码
    return id; // 直接返回 id 作为哈希码
    // 由于 id 是唯一的，直接使用 id 作为哈希码既简单又高效
    // 这意味着所有 Path 对象的哈希码都是唯一的（假设 id 不重复）
    // 注意：这种实现要求 equals 方法也基于 id 进行比较，否则违反 hashCode/equals 契约
  } // hashCode 方法结束

  @Override public boolean equals(@Nullable Object obj) { // 重写 Object 类的 equals 方法，用于比较两个 Path 对象是否相等
    return this == obj // 首先检查是否是同一个对象引用（内存地址相同），如果是则直接返回 true
        || obj instanceof Path // 如果不是同一个引用，检查 obj 是否是 Path 类的实例
        && id == ((Path) obj).id; // 如果是 Path 实例，比较两者的 id 是否相同，相同则返回 true
    // equals 方法的实现基于 id 比较，而不是基于 steps 内容比较
    // 这意味着即使两条路径的 steps 内容完全相同，只要 id 不同，它们就不相等
    // 这种设计在物化视图优化中是有意义的，因为不同的 id 代表不同的路径实例
    // @Nullable 注解表示 obj 参数可能为 null，但在 instanceof 检查中会自动处理 null 情况
  } // equals 方法结束
} // Path 类定义结束
