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
// 声明包名，表示该类属于 org.apache.calcite.interpreter 包，这是 Calcite 解释器模块的核心包之一
package org.apache.calcite.interpreter;

// 导入 DataContext 类，用于提供数据上下文信息，如表、变量、函数等运行时环境
import org.apache.calcite.DataContext;

// 导入 Nullable 注解，用于标记可能为 null 的返回值或参数，这是 CheckerFramework 的空值检查注解
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 Function 接口，用于支持函数式编程，Producer 接口将继承此接口
import java.util.function.Function;

/**
 * Compiled scalar expression. // 已编译的标量表达式接口
 * 
 * Scalar 是 Calcite 解释器中用于表示标量表达式（即返回单个值的表达式）的核心接口
 * 标量表达式包括：字面量、列引用、算术运算、逻辑运算、函数调用等
 * 
 * 该接口定义了标量表达式在解释执行时的行为规范，所有标量表达式都需要实现此接口
 * 解释器模式是 Calcite 的一种执行引擎实现方式，与传统的火山迭代模型不同
 * 
 * 主要特点：
 * 1. 标量表达式：返回单个值，不是关系（表）
 * 2. 编译后执行：表达式在查询编译阶段被转换为 Scalar 实例
 * 3. 解释执行：通过 execute 方法在运行时计算表达式的值
 * 4. 支持上下文：通过 Context 参数访问运行时环境
 * 
 * 使用场景：
 * - 在 Project（投影）操作中计算输出列的值
 * - 在 Filter（过滤）操作中计算条件表达式的值
 * - 在 Join 连接条件中计算连接条件的值
 * - 在聚合函数中计算参数表达式的值
 * 
 * 与 Calcite 其他组件的关系：
 * - RexNode：逻辑表达式树，在编译阶段转换为 Scalar
 * - Interpreter：解释器执行引擎，使用 Scalar 来执行表达式
 * - Context：执行上下文，提供变量、表等运行时信息
 */
public interface Scalar { // 定义 Scalar 接口，所有标量表达式实现类都需要实现此接口
  @Nullable Object execute(Context context); // 执行标量表达式并返回计算结果，参数 context 提供执行上下文，返回值可能为 null（使用 @Nullable 注解标记）
  void execute(Context context, @Nullable Object[] results); // 执行标量表达式并将结果存储到 results 数组中，参数 context 提供执行上下文，results 数组用于存储计算结果

  /** Produces a {@link Scalar} when a query is executed. // 在查询执行时产生 Scalar 实例的工厂接口
   *
   * <p>Call {@code producer.apply(DataContext)} to get a Scalar. // 调用 producer.apply(DataContext) 方法来获取 Scalar 实例
   * 
   * Producer 是一个函数式接口，继承自 Function<DataContext, Scalar>
   * 它的作用是在查询执行时，根据 DataContext 创建 Scalar 实例
   * 
   * 设计原因：
   * 1. 延迟创建：Scalar 实例可能需要在运行时根据 DataContext 的信息来创建
   * 2. 上下文依赖：某些标量表达式可能依赖 DataContext 中的信息（如用户变量、会话参数等）
   * 3. 缓存优化：可以将 Producer 缓存起来，在需要时才创建 Scalar 实例
   * 
   * 使用流程：
   * 1. 查询编译阶段：创建 Producer 实例
   * 2. 查询执行阶段：传入 DataContext，调用 producer.apply(context) 获取 Scalar
   * 3. 表达式求值：调用 scalar.execute(context) 计算表达式的值
   * 
   * 典型实现：
   * - NodeCompiler：编译 RexNode 为 Producer
   * - 生成代码：根据表达式类型生成对应的 Scalar 实现类
   * 
   * 示例：
   * Producer producer = compiler.compile(rexNode);
   * Scalar scalar = producer.apply(dataContext);
   * Object result = scalar.execute(context);
   */
  interface Producer extends Function<DataContext, Scalar> { // 定义 Producer 内部接口，继承 Function<DataContext, Scalar>，表示这是一个从 DataContext 到 Scalar 的转换函数
  } // Producer 接口结束
} // Scalar 接口结束
