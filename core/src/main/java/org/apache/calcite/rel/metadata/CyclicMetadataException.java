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
// Apache许可证声明，定义了该源代码的使用权限和限制条件
package org.apache.calcite.rel.metadata; // 声明该类所属的包：org.apache.calcite.rel.metadata，这是Calcite框架中处理关系表达式元数据的包

/**
 * Exception that indicates that a cycle has been detected while
 * computing metadata.
 * 表示在计算元数据时检测到循环依赖的异常类
 * 
 * 【类作用详解】：
 * CyclicMetadataException是Calcite元数据系统中的一个关键异常类，用于处理元数据计算过程中的循环依赖问题。
 * 
 * 【背景知识】：
 * 在Calcite中，元数据（Metadata）是指关于关系表达式（RelNode）的各种统计信息，例如：
 * - 行数（RowCount）：查询结果预计有多少行
 * - 唯一键数（UniqueKeys）：结果集有多少个唯一键
 * - 分布（Distribution）：数据如何分布
 * - 大小（Size）：数据占用空间大小
 * - 选择性（Selectivity）：过滤条件的选择性
 * 
 * 这些元数据通过递归计算获得，例如：
 * - Join的行数 = 左子节点行数 * 右子节点行数 * 选择性
 * - Filter的行数 = 输入行数 * 过滤条件的选择性
 * - Aggregate的行数 = 输入行数 / 分组键的基数
 * 
 * 【循环依赖问题】：
 * 在某些情况下，元数据计算可能形成循环依赖，例如：
 * 1. 自引用查询：表通过外键引用自身
 * 2. 复杂的查询计划：多个RelNode相互依赖
 * 3. 递归查询：CTE（Common Table Expression）或递归视图
 * 
 * 示例场景：
 * - RelNode A的元数据依赖于RelNode B
 * - RelNode B的元数据依赖于RelNode C
 * - RelNode C的元数据又依赖于RelNode A
 * 这就形成了A -> B -> C -> A的循环依赖
 * 
 * 【异常处理机制】：
 * Calcite的元数据系统使用缓存机制来避免重复计算：
 * 1. 当计算某个RelNode的元数据时，首先检查缓存
 * 2. 如果缓存中没有，则进行计算并将结果存入缓存
 * 3. 在计算过程中，如果发现正在计算某个已经在计算栈中的RelNode，说明存在循环
 * 4. 此时抛出CyclicMetadataException来中断计算
 * 
 * 【继承关系】：
 * 继承自RuntimeException，表示这是一个运行时异常，不需要强制捕获
 * 
 * 【使用场景】：
 * - JaninoRelMetadataProvider：使用Janino编译器生成元数据提供者
 * - RelMetadataQuery：查询元数据的主要入口
 * - DefaultRelMetadataProvider：默认的元数据提供者实现
 * 
 * 【相关类】：
 * - RelMetadataProvider：元数据提供者接口
 * - Metadata：元数据定义接口
 * - JaninoRelMetadataProvider：基于Janino的元数据提供者
 * - CyclicMetadataException：本类，用于处理循环依赖
 * 
 * 【设计模式】：
 * 使用异常处理模式来处理特殊情况（循环依赖），避免复杂的循环检测逻辑
 */
public class CyclicMetadataException extends RuntimeException { // 定义CyclicMetadataException类，继承自RuntimeException，表示这是一个运行时异常

  /** Creates a CyclicMetadataException. */ // JavaDoc注释：创建一个CyclicMetadataException实例的说明
  // 【无参构造方法详解】：
  // 1. 作用：创建一个CyclicMetadataException实例，不包含任何详细信息
  // 2. 调用super()：调用父类RuntimeException的无参构造方法
  // 3. 使用场景：当检测到循环依赖时，抛出此异常即可，不需要额外的错误信息
  // 4. 设计考虑：循环依赖是明确的错误类型，异常类型本身已经说明了问题，因此不需要额外信息
  // 
  // 【执行流程】：
  // - 当元数据计算过程中检测到循环依赖时
  // - 创建此异常实例
  // - 抛出异常中断计算
  // - 上层调用者捕获异常并处理（通常返回null或默认值）
  //
  // 【示例代码】：
  // if (currentlyComputing.contains(relNode)) {
  //   throw new CyclicMetadataException();
  // }
  public CyclicMetadataException() { // 定义无参构造方法，用于创建CyclicMetadataException实例
    super(); // 调用父类RuntimeException的无参构造方法，初始化异常对象
  } // 构造方法结束
}
