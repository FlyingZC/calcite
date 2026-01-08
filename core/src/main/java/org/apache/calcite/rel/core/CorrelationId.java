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
package org.apache.calcite.rel.core;

import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Set;

import static java.lang.Integer.parseInt;

/**
 * Describes the necessary parameters for an implementation in order to
 * identify and set dynamic variables.
 * 
 * CorrelationId（相关标识符）是Calcite框架中用于标识相关变量（correlated variables）的唯一标识类。
 * 相关变量通常出现在子查询中，子查询需要引用外层查询的列，这种情况下就需要使用相关变量来标识这种依赖关系。
 * 
 * 核心功能：
 * 1. 为相关变量提供唯一的标识符，用于区分不同的相关变量
 * 2. 维护相关变量的名称，名称以"$cor"为前缀，后跟数字ID（如$cor0、$cor1等）
 * 3. 提供类型安全的包装，将整数ID封装为对象，避免混淆
 * 4. 支持比较操作，便于在集合中排序和去重
 * 5. 提供静态工具方法，用于在CorrelationId集合和名称集合之间转换
 * 
 * 应用场景：
 * - 在查询优化过程中，识别子查询与外层查询之间的依赖关系
 * - 在相关子查询（correlated subquery）转换为连接操作时，标识相关变量
 * - 在执行计划生成时，为相关变量分配和传递标识符
 * 
 * 设计特点：
 * - 不可变对象（immutable），线程安全
 * - 实现了Comparable接口，支持自然排序
 * - 基于ID进行equals和hashCode判断，确保唯一性
 * - 提供了从ID和从名称两种构造方式，方便不同场景使用
 */
public class CorrelationId implements Cloneable, Comparable<CorrelationId> {
  /**
   * Prefix to the name of correlating variables.
   * 相关变量名称的前缀，所有相关变量的名称都以"$cor"开头，后跟数字ID。
   * 例如：$cor0、$cor1、$cor2等。这个前缀用于在SQL解析和代码生成时识别相关变量。
   */
  public static final String CORREL_PREFIX = "$cor";

  // 相关变量的唯一整数标识符，用于比较和哈希计算，不可变
  private final int id;
  // 相关变量的完整名称，格式为"$cor" + id，例如"$cor0"，不可变
  private final String name;

  /**
   * Creates a correlation identifier.
   * 私有构造方法，用于创建CorrelationId实例。
   此方法接受ID和名称两个参数，将它们保存到不可变的成员变量中。
   * 
   * @param id 相关变量的整数标识符，必须唯一
   * @param name 相关变量的完整名称，格式应为"$cor" + id
   */
  private CorrelationId(int id, String name) {
    this.id = id; // 初始化整数标识符
    this.name = name; // 初始化变量名称
  }

  /**
   * Creates a correlation identifier.
   * This is a type-safe wrapper over int.
   * 公共构造方法，根据整数ID创建CorrelationId实例。
   * 这是创建相关标识符的主要方式，将整数ID封装为类型安全的对象。
   * 
   * @param id     Identifier - 相关变量的唯一整数标识符，通常是递增的非负整数
   */
  public CorrelationId(int id) {
    this(id, CORREL_PREFIX + id); // 调用私有构造方法，自动生成名称为"$cor" + id
  }

  /**
   * Creates a correlation identifier from a name.
   * 根据相关变量的名称创建CorrelationId实例。
   * 此构造方法会从名称中提取数字部分作为ID，适用于需要从名称反推ID的场景。
   * 
   * @param name     variable name - 相关变量的完整名称，必须以"$cor"开头
   */
  public CorrelationId(String name) {
    this(parseInt(name.substring(CORREL_PREFIX.length())), name); // 从名称中提取数字部分作为ID，例如从"$cor3"中提取3
    assert name.startsWith(CORREL_PREFIX) // 断言名称必须以"$cor"开头，确保格式正确
        : "Correlation name should start with " + CORREL_PREFIX
        + " actual name is " + name;
  }

  /**
   * Returns the identifier.
   * 返回相关变量的整数标识符。
   * 
   * @return identifier - 相关变量的唯一整数ID，用于比较和哈希计算
   */
  public int getId() {
    return id; // 返回整数标识符
  }

  /**
   * Returns the preferred name of the variable.
   * 返回相关变量的首选名称。
   * 
   * @return name - 相关变量的完整名称，格式为"$cor" + id，例如"$cor5"
   */
  public String getName() {
    return name; // 返回变量名称
  }

  @Override public String toString() {
    return name; // 返回变量名称作为字符串表示，便于调试和日志输出
  }

  @Override public int compareTo(CorrelationId other) {
    return id - other.id; // 基于ID进行比较，返回负数、0或正数，表示小于、等于或大于
  }

  @Override public int hashCode() {
    return id; // 直接返回ID作为哈希码，确保相同的ID有相同的哈希值
  }

  @Override public boolean equals(@Nullable Object obj) {
    return this == obj // 首先检查引用是否相同
        || obj instanceof CorrelationId // 然后检查类型是否正确
        && this.id == ((CorrelationId) obj).id; // 最后比较ID是否相等
  }

  /** Converts a set of correlation ids to a set of names. */
  public static ImmutableSet<CorrelationId> setOf(Set<String> set) {
    if (set.isEmpty()) { // 如果输入集合为空，直接返回空集合
      return ImmutableSet.of();
    }
    final ImmutableSet.Builder<CorrelationId> builder = ImmutableSet.builder(); // 创建不可变集合构建器
    for (String s : set) { // 遍历名称集合
      builder.add(new CorrelationId(s)); // 将每个名称转换为CorrelationId对象并添加到构建器
    }
    return builder.build(); // 构建并返回不可变的CorrelationId集合
  }

  /** Converts a set of names to a set of correlation ids. */
  public static Set<String> names(Set<CorrelationId> set) {
    if (set.isEmpty()) { // 如果输入集合为空，直接返回空集合
      return ImmutableSet.of();
    }
    final ImmutableSet.Builder<String> builder = ImmutableSet.builder(); // 创建不可变集合构建器
    for (CorrelationId s : set) { // 遍历CorrelationId集合
      builder.add(s.name); // 提取每个CorrelationId的名称并添加到构建器
    }
    return builder.build(); // 构建并返回不可变的名称集合
  }
}
