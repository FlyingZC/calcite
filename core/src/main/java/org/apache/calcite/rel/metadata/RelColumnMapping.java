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
package org.apache.calcite.rel.metadata; // 声明包名，该类位于org.apache.calcite.rel.metadata包中，属于Calcite关系代数元数据模块

/**
 * Mapping from an input column of a {@link org.apache.calcite.rel.RelNode} to // 类说明：定义了从RelNode（关系节点）的输入列到其输出列的映射关系
 * one of its output columns. // 在Calcite的查询优化过程中，关系节点可能会对输入数据进行转换（如投影、过滤等）
 */ // RelColumnMapping用于追踪输出列与输入列之间的对应关系，这对于元数据推导、列引用分析等场景非常重要
public class RelColumnMapping { // 类定义：RelColumnMapping是一个不可变类，用于表示列映射关系
  public RelColumnMapping( // 构造方法：创建一个RelColumnMapping实例，初始化列映射关系
      int iOutputColumn, int iInputRel, int iInputColumn, boolean derived) { // 参数说明：iOutputColumn-输出列的索引；iInputRel-输入关系节点的索引；iInputColumn-输入列的索引；derived-是否为派生列
    this.iOutputColumn = iOutputColumn; // 初始化输出列索引字段
    this.iInputRel = iInputRel; // 初始化输入关系节点索引字段
    this.iInputColumn = iInputColumn; // 初始化输入列索引字段
    this.derived = derived; // 初始化派生标志字段
  }

  //~ Instance fields -------------------------------------------------------- // 成员变量区域开始标记

  /**
   * 0-based ordinal of mapped output column. // 字段说明：输出列的索引（从0开始），表示当前映射关系对应于输出关系中的哪一列
   */
  public final int iOutputColumn; // 输出列索引，final修饰表示不可变，public修饰表示外部可以访问

  /**
   * 0-based ordinal of mapped input rel. // 字段说明：输入关系节点的索引（从0开始），表示当前映射关系对应于哪个输入关系节点
   * 在具有多个输入的关系节点（如Join）中，这个索引用于标识是第几个输入
   */
  public final int iInputRel; // 输入关系节点索引，final修饰表示不可变，public修饰表示外部可以访问

  /**
   * 0-based ordinal of mapped column within input rel. // 字段说明：输入列的索引（从0开始），表示在指定的输入关系节点中，映射的是哪一列
   * 这个索引与iInputRel配合使用，可以精确定位到具体的输入列
   */
  public final int iInputColumn; // 输入列索引，final修饰表示不可变，public修饰表示外部可以访问

  /**
   * Whether the column mapping transforms the input. // 字段说明：布尔标志，表示当前列映射是否对输入进行了转换或派生
   * 如果为true，表示输出列不是直接从输入列复制的，而是经过某种转换（如表达式计算、函数应用等）
   * 如果为false，表示输出列是输入列的直接映射，没有经过任何转换
   * 这个标志对于理解列的来源和性质非常重要，影响元数据推导和优化决策
   */
  public final boolean derived; // 派生标志，final修饰表示不可变，public修饰表示外部可以访问
}
