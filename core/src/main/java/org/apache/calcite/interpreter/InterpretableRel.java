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
// 声明当前类所在的包，属于Calcite的interpreter（解释器）包，该包负责解释执行关系代数表达式
package org.apache.calcite.interpreter;

// 导入DataContext类，用于在执行SQL时提供上下文信息（如变量、时间戳等）
import org.apache.calcite.DataContext;
// 导入CalcitePrepare类，用于SQL准备阶段，包含SparkHandler等内部接口
import org.apache.calcite.jdbc.CalcitePrepare;
// 导入RelNode接口，这是Calcite中所有关系表达式节点的基类接口
import org.apache.calcite.rel.RelNode;

// 导入Nullable注解，用于标记可能为null的值，帮助进行空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入HashMap，用于存储键值对映射，底层实现是哈希表，查询效率高
import java.util.HashMap;
// 导入LinkedHashMap，用于存储键值对映射，保持插入顺序，继承自HashMap
import java.util.LinkedHashMap;
// 导入List接口，表示有序集合，可以包含重复元素
import java.util.List;
// 导入Map接口，表示键值对映射的集合
import java.util.Map;

/**
 * Relational expression that can implement itself using an interpreter.
 * 可使用解释器实现自身的关系表达式接口
 * 
 * 这个接口定义了关系表达式（RelNode）的一种特殊能力：能够通过解释器模式来实现自己的执行逻辑
 * 
 * 核心概念：
 * 1. 解释器模式：一种设计模式，通过解释执行语法树来执行操作，而不是生成代码
 * 2. 关系表达式：代表SQL查询中的各种操作（如扫描、过滤、投影、连接等）
 * 3. 在Calcite中，RelNode通常有两种执行方式：
 *    - 代码生成：生成Java代码并编译执行（性能高但启动慢）
 *    - 解释执行：通过解释器逐行执行（性能稍低但启动快，适合小查询或调试）
 * 
 * 使用场景：
 * - 调试SQL查询的执行过程
 * - 快速原型验证
 * - 不适合代码生成的场景
 * - 需要动态执行的场景
 * 
 * 实现类包括：
 * - EnumerableInterpretable：可枚举的解释器实现
 * - 各种适配器可能提供自己的解释器实现
 */
public interface InterpretableRel extends RelNode {
  /** Creates an interpreter node to implement this relational expression.
   * 创建一个解释器节点来实现当前的关系表达式
   * 
   * @param implementor 解释器实现上下文对象，包含编译器、参数、数据上下文等信息
   * @return Node 返回一个解释器节点，该节点知道如何解释执行当前的关系表达式
   * 
   * 方法作用：
   * 1. 将关系表达式（RelNode）转换为可执行的解释器节点（Node）
   * 2. Node接口定义了执行逻辑，通过解释器模式逐行处理数据
   * 3. 这个方法是解释执行模式的核心入口点
   * 
   * 执行流程：
   * - 调用者提供InterpreterImplementor上下文
   * - 当前RelNode根据自身类型创建对应的Node实现
   * - Node会递归地创建子节点的解释器实现
   * - 最终形成完整的解释执行树
   * 
   * 示例：
   * - TableScanRel创建一个扫描数据的Node
   * - FilterRel创建一个过滤数据的Node
   * - ProjectRel创建一个投影数据的Node
   */
  Node implement(InterpreterImplementor implementor);

  /** Context when a {@link RelNode} is being converted to an interpreter
   * {@link Node}.
   * 当RelNode被转换为解释器Node时的上下文对象
   * 
   * 这个内部类封装了在解释执行过程中需要的所有上下文信息
   * 它在整个解释执行过程中被传递，确保所有节点都能访问必要的资源和配置
   * 
   * 核心职责：
   * 1. 提供编译器，用于生成和编译表达式
   * 2. 存储内部参数，用于在执行过程中传递变量
   * 3. 提供Spark处理器，支持Spark分布式执行
   * 4. 提供数据上下文，包含执行环境信息
   * 5. 管理关系节点到数据接收器的映射
   */
  class InterpreterImplementor {
    /** 编译器，用于编译表达式和生成可执行代码
     * 
     * 作用：
     * - 将SQL表达式编译为可执行的Java代码
     * - 处理类型转换和类型检查
     * - 优化表达式执行
     * 
     * 在解释执行模式中，Compiler主要用于：
     * - 编译WHERE条件中的表达式
     * - 编译SELECT列表中的表达式
     * - 编译JOIN条件等复杂表达式
     * 
     * final修饰表示该引用在构造后不可变，保证线程安全
     */
    public final Compiler compiler;
    
    /** 内部参数映射表，用于存储执行过程中的参数和变量
     * 
     * 作用：
     * - 存储SQL中的参数值（如?占位符的值）
     * - 存储系统变量（如CURRENT_TIMESTAMP）
     * - 存储用户定义的变量
     * - 在不同节点间传递数据
     * 
     * 使用LinkedHashMap的原因：
     * - 保持参数的插入顺序，确保执行顺序的可预测性
     * - 继承HashMap的高效查询性能
     * 
     * 示例：
     * - 参数名: "param1" -> 值: 100
     * - 参数名: "param2" -> 值: "test"
     * - 参数名: "CURRENT_TIMESTAMP" -> 值: 2026-01-07 10:00:00
     */
    public final Map<String, Object> internalParameters =
        new LinkedHashMap<>();
    
    /** Spark处理器，用于支持Spark分布式执行框架
     * 
     * 作用：
     * - 当Calcite与Spark集成时，提供Spark特定的执行逻辑
     * - 处理Spark RDD/DataFrame的转换
     * - 支持Spark SQL的执行
     * 
     * @Nullable注解表示该字段可能为null，因为：
     * - 并非所有Calcite环境都使用Spark
     * - 在非Spark环境下，该字段为null
     * - 使用前需要进行null检查
     * 
     * 使用场景：
     * - 大规模分布式数据处理
     * - 需要利用Spark的并行计算能力
     * - 与Spark SQL集成
     */
    public final CalcitePrepare.@Nullable SparkHandler spark;
    
    /** 数据上下文，提供执行SQL查询时的环境信息
     * 
     * 作用：
     * - 提供数据源连接信息
     * - 提供用户会话信息
     * - 提供时间戳等系统信息
     * - 提供变量存储
     * - 提供类型工厂
     * 
     * DataContext是Calcite执行时的核心上下文对象，包含：
     * - 数据源管理器（SchemaPlus）
     * - 变量映射（Map<String, Object>）
     * - 类型系统（RelDataTypeFactory）
     * - 其他执行环境信息
     * 
     * 在解释执行过程中，DataContext用于：
     * - 获取表的数据源
     * - 访问用户定义的变量
     * - 获取当前时间等系统信息
     */
    public final DataContext dataContext;
    
    /** 关系节点到数据接收器列表的映射
     * 
     * 作用：
     * - 管理每个RelNode对应的输出目标（Sink）
     * - 支持一个RelNode的输出被多个消费者接收
     * - 在解释执行树中传递数据流
     * 
     * 数据结构：
     * - Key: RelNode - 关系表达式节点
     * - Value: List<Sink> - 该节点的所有输出接收器列表
     * 
     * 使用场景：
     * - 当一个查询的结果需要被多个操作使用时
     * - 实现数据流的分支
     * - 支持复杂的查询计划（如UNION、INTERSECT等）
     * 
     * 示例：
     * - 一个TableScan的输出可能被Filter和Sort同时使用
     * - 一个JOIN的结果可能被多个后续操作消费
     * 
     * 使用HashMap的原因：
     * - 快速查找RelNode对应的Sink列表
     * - 不需要保持插入顺序
     */
    public final Map<RelNode, List<Sink>> relSinks = new HashMap<>();

    /** 构造方法，创建解释器实现上下文对象
     * 
     * @param compiler 编译器，用于编译表达式，不能为null
     * @param spark Spark处理器，可能为null（非Spark环境）
     * @param dataContext 数据上下文，提供执行环境信息，不能为null
     * 
     * 构造方法的作用：
     * 1. 初始化所有必需的上下文信息
     * 2. 创建一个可传递的解释执行上下文
     * 3. 确保所有依赖资源都已正确设置
     * 
     * 设计考虑：
     * - 所有参数都是final的，保证上下文的不可变性
     * - internalParameters在声明时初始化为空LinkedHashMap
     * - relSinks在声明时初始化为空HashMap
     * - 这些集合会在执行过程中动态填充
     * 
     * 使用场景：
     * - 在开始解释执行前创建此上下文
     * - 将此上下文传递给所有需要解释执行的RelNode
     * - 整个执行过程共享同一个上下文
     */
    public InterpreterImplementor(Compiler compiler,
        CalcitePrepare.@Nullable SparkHandler spark,
        DataContext dataContext) {
      // 保存编译器引用，用于后续表达式编译
      this.compiler = compiler;
      // 保存Spark处理器引用，可能为null
      this.spark = spark;
      // 保存数据上下文引用，用于访问执行环境信息
      this.dataContext = dataContext;
    }
  }
}
