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
// Apache软件基金会许可证声明，定义代码的使用权限和限制
package org.apache.calcite.rel; // 声明包名，该类位于org.apache.calcite.rel包下，是Calcite关系表达式体系的核心包

import org.apache.calcite.sql.SqlExplainLevel; // 导入SqlExplainLevel枚举类，用于定义EXPLAIN计划的详细程度级别
import org.apache.calcite.util.Pair; // 导入Pair工具类，用于存储键值对，在解释计划时用于存储术语-值对

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的参数

import java.util.List; // 导入List接口，用于存储术语-值对的列表

/**
 * Callback for an expression to dump itself to. // 回调接口，用于表达式将自身信息转储到输出目标
 *
 * <p>It is used for generating EXPLAIN PLAN output, and also for serializing // 该接口主要用于生成EXPLAIN PLAN输出，也可以用于将关系表达式树序列化为JSON格式
 * a tree of relational expressions to JSON. // 支持将关系代数表达式树序列化为JSON格式，便于调试和可视化
 */
public interface RelWriter { // 定义RelWriter接口，这是一个函数式接口，用于写入关系表达式的解释信息
  /**
   * Prints an explanation of a node, with a list of (term, value) pairs. // 打印节点的解释信息，包含一个术语-值对的列表
   *
   * <p>The term-value pairs are generally gathered by calling // 术语-值对通常通过调用RelNode的explain方法收集
   * {@link org.apache.calcite.rel.RelNode#explain(RelWriter)}. // RelNode的explain方法会调用此方法来输出解释信息
   * Each sub-class of {@link org.apache.calcite.rel.RelNode} // RelNode的每个子类都会调用input和item方法
   * calls {@link #input(String, org.apache.calcite.rel.RelNode)} // 调用input方法声明输入关系表达式
   * and {@link #item(String, Object)} to declare term-value pairs. // 调用item方法声明术语-值对
   *
   * @param rel       Relational expression // 参数rel：要解释的关系表达式节点
   * @param valueList List of term-value pairs // 参数valueList：术语-值对的列表，包含该节点的所有属性信息
   */
  void explain(RelNode rel, List<Pair<String, @Nullable Object>> valueList); // explain方法：输出关系表达式的解释信息，这是核心方法

  /** Returns detail level at which plan should be generated. */ // 返回生成计划时应该使用的详细程度级别
  SqlExplainLevel getDetailLevel(); // getDetailLevel方法：获取EXPLAIN计划的详细级别，如EXPAND_ATTRIBUTES、ALL_ATTRIBUTES等

  /**
   * Adds an input to the explanation of the current node. // 为当前节点的解释添加一个输入关系表达式
   *
   * @param term  Term for input, e.g. "left" or "input #1". // 参数term：输入的术语名称，例如"left"表示左输入，"input #1"表示第一个输入
   * @param input Input relational expression // 参数input：输入的关系表达式节点
   */
  default RelWriter input(String term, RelNode input) { // input方法：添加输入关系表达式到解释信息中，默认实现调用item方法
    return item(term, input); // 将输入关系表达式作为一个术语-值对添加，返回当前RelWriter实例以支持链式调用
  }

  /**
   * Adds an attribute to the explanation of the current node. // 为当前节点的解释添加一个属性
   *
   * @param term  Term for attribute, e.g. "joinType" // 参数term：属性的术语名称，例如"joinType"表示连接类型
   * @param value Attribute value // 参数value：属性值，可以是任何对象类型，允许为null
   */
  RelWriter item(String term, @Nullable Object value); // item方法：添加属性到解释信息中，返回当前RelWriter实例以支持链式调用

  /**
   * Adds an input to the explanation of the current node, if a condition // 如果条件满足，则向当前节点的解释添加一个输入
   * holds. // 只有当condition为true时才会添加属性
   */
  default RelWriter itemIf(String term, @Nullable Object value, boolean condition) { // itemIf方法：条件性地添加属性，用于只在特定情况下显示某些属性
    return condition ? item(term, value) : this; // 如果条件为true则调用item方法添加属性，否则返回当前RelWriter实例
  }

  /**
   * Writes the completed explanation. // 写入已完成的解释信息
   */
  RelWriter done(RelNode node); // done方法：标记当前节点的解释已完成，返回当前RelWriter实例以支持链式调用

  /**
   * Returns whether the writer prefers nested values. Traditional explain // 返回该写入器是否偏好嵌套值，传统的explain写入器偏好扁平化的值
   * writers prefer flattened values. // 嵌套值可以更好地表达层次结构，而扁平化值更易于阅读
   */
  default boolean nest() { // nest方法：判断是否使用嵌套格式输出，默认返回false表示使用扁平化格式
    return false; // 默认返回false，表示不使用嵌套格式，使用传统的扁平化格式
  }

  /**
   * Returns whether the writer needs to expand node's detail information when printing plan. // 返回写入器在打印计划时是否需要展开节点的详细信息
   * For example, LogicalSort(sort0=[$0], dir0=[ASC]) will be expanded to // 例如，LogicalSort(sort0=[$0], dir0=[ASC])将被展开为
   * LogicalSort(sort0=[$0], dir0=[ASC-nulls-last]). // LogicalSort(sort0=[$0], dir0=[ASC-nulls-last])，显示更完整的排序信息
   */
  default boolean expand() { // expand方法：判断是否展开节点的详细信息，默认返回false表示不展开
    return false; // 默认返回false，表示不展开详细信息，使用简洁的格式
  }
}
