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
package org.apache.calcite.adapter.enumerable; // 定义该类所在的包路径，位于可枚举适配器包下，用于处理窗口函数的枚举操作

import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于构建代码生成时的表达式树，是LINQ4J框架的核心组件

/**
 * Provides information on the current window. // 窗口聚合帧上下文接口：提供关于当前窗口（window）的完整信息
 *
 * <p>All the indexes are ready to be used in // 所有返回的索引都经过预处理，可以直接用于以下方法中：
 * {@link WinAggResultContext#arguments(org.apache.calcite.linq4j.tree.Expression)}, // 窗口聚合结果上下文的参数方法，用于访问聚合函数的参数
 * {@link WinAggFrameResultContext#rowTranslator(org.apache.calcite.linq4j.tree.Expression)} // 窗口聚合帧结果上下文的行转换器方法，用于访问行数据
 * and similar methods. // 以及其他类似的方法中，这些方法需要使用索引来访问窗口内的数据
 */
public interface WinAggFrameContext { // 定义窗口聚合帧上下文接口，这是Calcite窗口函数实现中的核心接口之一，用于在代码生成时提供窗口帧的各种元数据信息

  /**
   * Returns the index of the current row in the partition. // 返回当前行在分区（partition）中的索引位置
   * In other words, it is close to ~ROWS BETWEEN CURRENT ROW. // 换句话说，这相当于 SQL 中的 "ROWS BETWEEN CURRENT ROW" 概念
   * Note to use {@link #startIndex()} when you need zero-based row position. // 注意：如果你需要从0开始的行位置，应该使用 startIndex() 方法
   *
   * @return the index of the very first row in partition // 返回当前行在分区中的索引（这个注释似乎有误，应该返回当前行索引，不是第一行的索引）
   */
  Expression index(); // 返回表示当前行索引的表达式对象（Expression），该表达式可以在代码生成时被编译成实际的索引访问代码

  /**
   * Returns the index of the very first row in partition. // 返回分区中第一行（最前一行）的索引位置
   *
   * @return index of the very first row in partition // 返回分区第一行的索引表达式，通常用于确定窗口帧的起始边界
   */
  Expression startIndex(); // 返回表示分区起始行索引的表达式对象，这是窗口帧计算的重要参考点

  /**
   * Returns the index of the very last row in partition. // 返回分区中最后一行（最后一行）的索引位置
   *
   * @return index of the very last row in partition // 返回分区最后一行的索引表达式，用于确定窗口帧的结束边界
   */
  Expression endIndex(); // 返回表示分区结束行索引的表达式对象，结合 startIndex() 可以确定整个分区的范围

  /**
   * Returns the boolean expression that tells if the partition has rows. // 返回一个布尔表达式，用于判断当前分区中是否存在行数据
   * The partition might lack rows in cases like ROWS BETWEEN 1000 PRECEDING // 分区可能没有行的情况例如：当窗口定义为 "ROWS BETWEEN 1000 PRECEDING"
   * AND 900 PRECEDING. // AND 900 PRECEDING" 时，如果当前行之前没有足够的行，则窗口帧可能为空
   *
   * @return boolean expression that tells if the partition has rows // 返回布尔表达式，如果分区包含行则为 true，否则为 false
   */
  Expression hasRows(); // 返回布尔表达式，用于在代码生成时判断分区是否为空，避免对空分区进行操作

  /**
   * Returns the number of rows in the current frame (subject to framing // 返回当前窗口帧（frame）中的行数，该行数受窗口帧条款（framing clause）的限制
   * clause). // 窗口帧条款是指 SQL 中 OVER 子句中的 ROWS/RANGE BETWEEN ... AND ... 定义
   *
   * @return number of rows in the current partition or 0 if the partition // 返回当前窗口帧中的行数，如果窗口帧为空则返回 0
   *   is empty // 这是计算窗口函数时的重要信息，因为某些聚合函数（如 SUM、AVG）需要知道帧内有多少行
   */
  Expression getFrameRowCount(); // 返回表示窗口帧行数的表达式对象，用于在运行时动态获取当前窗口帧包含的行数

  /**
   * Returns the number of rows in the current partition (as determined by // 返回当前分区（partition）中的总行数，该行数由 PARTITION BY 子句确定
   * PARTITION BY clause). // PARTITION BY 子句用于将数据集分成多个分区，每个分区独立计算窗口函数
   *
   * @return number of rows in the current partition or 0 if the partition // 返回当前分区的总行数，如果分区为空则返回 0
   *   is empty // 这与 getFrameRowCount() 不同，getFrameRowCount() 返回的是窗口帧内的行数，而这里返回的是整个分区的行数
   */
  Expression getPartitionRowCount(); // 返回表示分区总行数的表达式对象，用于在运行时动态获取当前分区包含的所有行数
}