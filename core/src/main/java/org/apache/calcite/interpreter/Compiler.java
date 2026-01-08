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
// 声明包名，表明该类属于 org.apache.calcite.interpreter 包，这个包包含了 Calcite 解释器相关的类
package org.apache.calcite.interpreter;

// 导入 DataContext 类，提供了执行 SQL 查询所需的上下文信息（如表、函数等）
import org.apache.calcite.DataContext;
// 导入 Enumerable 接口，这是 LINQ4J 库中的核心接口，表示可枚举的数据集合
import org.apache.calcite.linq4j.Enumerable;
// 导入 RelNode 接口，这是 Calcite 中关系代数表达式树的节点接口，表示关系运算符
import org.apache.calcite.rel.RelNode;
// 导入 RelDataType 接口，表示关系数据类型（如表结构、列类型等）
import org.apache.calcite.rel.type.RelDataType;
// 导入 RexNode 接口，表示行表达式（Row Expression），即 SQL 中的表达式（如 a + b, c > 10 等）
import org.apache.calcite.rex.RexNode;

// 导入 Nullable 注解，用于标记参数或返回值可以为 null
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 List 接口，用于处理列表集合
import java.util.List;

/**
 * Context while converting a tree of {@link RelNode} to a program
 * that can be run by an {@link Interpreter}.
 * 【类的作用】：编译器接口，在将 RelNode 关系代数表达式树转换为可由 Interpreter 执行的程序时使用的上下文
 * 【详细说明】：这个接口是 Calcite 解释器模式的核心接口之一，它定义了将关系代数树编译为可执行代码所需的各种方法
 * 【核心功能】：
 *   1. 编译 RexNode 表达式为可执行的 Scalar（标量）函数
 *   2. 创建数据源（Source）和数据汇（Sink）用于节点间的数据传递
 *   3. 合并多个输入的行类型
 *   4. 提供数据上下文和执行上下文
 * 【使用场景】：当需要以解释器模式执行查询计划时，会使用此接口来编译和连接各个 RelNode 节点
 */
public interface Compiler {

  /**
   * Compiles an expression to an executable form.
   * 【方法作用】：将 RexNode 表达式列表编译为可执行的 Scalar（标量）对象
   * 【详细说明】：此方法将 SQL 表达式（如 WHERE 条件、SELECT 列表等）编译为可以在运行时求值的代码
   * 【参数说明】：
   *   @param nodes - RexNode 表达式列表，包含需要编译的行表达式（例如：[a + b, c * 2]）
   *   @param inputRowType - 输入行的类型，用于确定表达式中引用的变量的类型，可为 null
   * 【返回值】：编译后的 Scalar 对象，可以在运行时对输入行求值并返回结果
   * 【使用示例】：编译 WHERE 条件表达式，然后在运行时对每一行数据判断是否满足条件
   */
  Scalar compile(List<RexNode> nodes, @Nullable RelDataType inputRowType);

  /**
   * 【方法作用】：计算多个 RelNode 输入的合并行类型
   * 【详细说明】：当一个节点有多个输入时（如 JOIN 操作），此方法用于计算合并后的行结构
   * 【参数说明】：
   *   @param inputs - 输入的 RelNode 列表，每个 RelNode 都有自己的行类型
   * 【返回值】：合并后的行类型，包含所有输入的列
   * 【使用场景】：用于确定多个输入连接后的结果集结构
   * 【示例】：输入1有 [id, name]，输入2有 [id, age]，合并后为 [id, name, id, age]
   */
  RelDataType combinedRowType(List<RelNode> inputs);

  /**
   * 【方法作用】：为指定的 RelNode 创建数据源（Source）
   * 【详细说明】：数据源用于从父节点读取数据，每个节点通过 Source 获取输入数据
   * 【参数说明】：
   *   @param rel - 需要创建数据源的关系表达式节点
   *   @param ordinal - 该节点作为输入的序号（当父节点有多个输入时区分是第几个输入）
   * 【返回值】：Source 对象，用于从该节点读取数据行
   * 【使用场景】：在构建节点树时，为每个子节点创建 Source 以供父节点读取数据
   */
  Source source(RelNode rel, int ordinal);

  /**
   * Creates a Sink for a relational expression to write into.
   * 【方法作用】：为关系表达式创建数据汇（Sink），用于写入输出数据
   * 【详细说明】：Sink 是数据流的终点，节点通过 Sink 将处理后的数据传递给下游节点
   * 【调用时机】：通常在 Node 的构造函数中调用此方法
   * 【替代方案】：也可以调用 {@link #enumerable(RelNode, Enumerable)} 方法来提供输出数据
   * 【参数说明】：
   *   @param rel - 需要创建 Sink 的关系表达式节点
   * 【返回值】：Sink 对象，节点可以向其中写入数据行
   * 【使用场景】：当节点需要将数据传递给下一个节点时，创建 Sink 并写入数据
   */
  Sink sink(RelNode rel);

  /**
   * Tells the interpreter that a given relational expression wishes to
   * give its output as an enumerable.
   * 【方法作用】：通知解释器某个关系表达式希望以 Enumerable（可枚举集合）的形式提供输出
   * 【详细说明】：这是另一种提供数据的方式，与调用 {@link #sink(RelNode)} 的常规方式不同
   * 【常规方式】：节点调用 sink()，然后在 run() 方法中向 sink 写入数据
   * 【本方式】：节点直接提供一个 Enumerable，解释器从中读取数据
   * 【参数说明】：
   *   @param rel - 关系表达式节点
   *   @param rowEnumerable - 包含该关系表达式输出数据的可枚举集合
   * 【使用场景】：当节点可以方便地提供 Enumerable 时使用，例如某些优化后的节点可以直接返回数据集合
   * 【优势】：避免了显式调用 run() 方法写入数据的步骤，更简洁
   */
  void enumerable(RelNode rel, Enumerable<Row> rowEnumerable);

  /**
   * 【方法作用】：获取数据上下文（DataContext）
   * 【详细说明】：DataContext 提供了执行查询所需的环境信息，包括表、函数、变量等
   * 【返回值】：DataContext 对象，包含执行查询所需的全部上下文信息
   * 【使用场景】：在执行过程中需要访问表数据、调用 UDF 函数等时使用
   * 【包含信息】：表映射、用户定义函数、会话变量、时间戳等
   */
  DataContext getDataContext();

  /**
   * 【方法作用】：创建执行上下文（Context）
   * 【详细说明】：Context 是执行过程中的临时上下文，用于存储运行时状态
   * 【返回值】：Context 对象，用于在执行过程中传递和存储状态信息
   * 【使用场景】：在执行查询时，需要维护当前的执行状态，如当前行、变量值等
   * 【与 DataContext 的区别】：DataContext 是静态的配置信息，Context 是动态的运行时状态
   */
  Context createContext();

}
