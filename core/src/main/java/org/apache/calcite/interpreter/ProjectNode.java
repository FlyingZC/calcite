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
package org.apache.calcite.interpreter; // 声明包名，这个类属于 org.apache.calcite.interpreter 包，是 Calcite 解释器模块的一部分

import org.apache.calcite.rel.core.Project; // 导入 Project 类，这是 Calcite 关系代数中的投影操作符，用于选择和转换列

/**
 * Interpreter node that implements a // 解释器节点，实现了投影操作的功能
 * {@link org.apache.calcite.rel.core.Project}. // 对应关系代数中的 Project 节点，用于执行 SQL 中的 SELECT 子句和表达式计算
 * 这个类是 Calcite 解释器模式下执行投影操作的核心实现类
 * 投影操作（Project）是关系代数的基本操作之一，用于：
 * 1. 选择输入数据中的某些列
 * 2. 对列进行计算和转换（如使用表达式、函数等）
 * 3. 生成新的输出行
 * 
 * 在 SQL 中，投影操作对应 SELECT 子句，例如：
 * SELECT a, b + c AS sum FROM table
 * 这里 a 和 b+c 就是投影表达式
 * 
 * 这个类继承自 AbstractSingleNode<Project>，表示它是一个单输入节点的解释器实现
 * 它通过解释器模式来执行投影操作，而不是通过代码生成
 * 这种方式适合调试、测试和某些特殊场景
 */
public class ProjectNode extends AbstractSingleNode<Project> { // ProjectNode 类定义，继承自抽象单节点类，泛型参数为 Project（关系代数投影节点）
  private final Scalar scalar; // 标量表达式编译器，用于编译和执行投影表达式（如列引用、函数调用、算术运算等）
                              // Scalar 接口封装了表达式求值的逻辑，可以高效地计算多个表达式
                              // 这个对象在构造时由 Compiler 编译生成，包含了所有投影表达式的执行计划
  private final Context context; // 执行上下文，用于在表达式求值时存储和访问变量
                                 // Context 包含 values 字段，保存当前输入行的值
                                 // 在表达式求值时，Scalar 会从 context 中读取输入值
                                 // 这个上下文在整个执行过程中被重复使用，避免重复创建对象
  private final int projectCount; // 投影表达式的数量，即输出列的个数
                                  // 这个值用于创建输出数组的大小
                                  // 从 Project 节点的 projects 列表获取

  public ProjectNode(Compiler compiler, Project rel) { // 构造方法，接收编译器和 Project 关系节点作为参数
                                                       // compiler: 编译器，用于将表达式编译为可执行的 Scalar
                                                       // rel: Project 关系节点，包含投影表达式和输入信息
    super(compiler, rel); // 调用父类 AbstractSingleNode 的构造方法，初始化基础信息
                          // 父类会保存 compiler 和 rel，并创建 source（输入流）和 sink（输出流）
    this.projectCount = rel.getProjects().size(); // 获取投影表达式的数量并保存
                                                   // rel.getProjects() 返回投影表达式列表（RexNode 列表）
                                                   // 每个表达式对应输出的一列
                                                   // 这个数量决定了输出行数组的长度
    this.scalar = // 初始化标量表达式编译器，用于执行投影表达式
        compiler.compile(rel.getProjects(), rel.getInput().getRowType()); // 编译投影表达式为可执行的 Scalar
                                                                          // rel.getProjects(): 投影表达式列表
                                                                          // rel.getInput().getRowType(): 输入行的类型信息
                                                                          // 编译器会根据输入行类型和表达式列表生成高效的执行代码
                                                                          // Scalar 可以一次性计算所有表达式，避免重复遍历
    this.context = compiler.createContext(); // 创建执行上下文对象
                                             // 这个上下文会在每次处理一行时被重用
                                             // context.values 字段会被设置为当前输入行的值
                                             // Scalar 在执行时会从这个 context 读取输入值
  }

  @Override public void run() throws InterruptedException { // 执行方法，实现投影操作的核心逻辑
                                                            // 这个方法会被解释器调用，开始处理数据流
                                                            // 从 source 接收输入行，应用投影表达式，发送到 sink
    Row row; // 声明行变量，用于存储从输入流接收的每一行数据
             // Row 是 Calcite 中表示一行数据的包装类，包含一个 Object[] 数组
    while ((row = source.receive()) != null) { // 循环处理输入流中的每一行数据
                                               // source.receive() 从输入流获取一行数据
                                               // 当返回 null 时表示输入流结束
                                               // source 是父类 AbstractSingleNode 提供的输入源
      context.values = row.getValues(); // 将当前输入行的值设置到执行上下文中
                                        // row.getValues() 返回该行的所有列值（Object[]）
                                        // context.values 会被 Scalar 在表达式求值时引用
                                        // 这样表达式就可以通过索引访问输入列的值
      Object[] values = new Object[projectCount]; // 创建输出数组，用于存储投影表达式的计算结果
                                                  // 数组大小等于投影表达式的数量
                                                  // 每个元素对应一个投影表达式的结果
                                                  // 这个数组会被包装成新的 Row 对象发送到输出流
      scalar.execute(context, values); // 执行所有投影表达式，将结果写入输出数组
                                       // context: 包含输入行值的上下文
                                       // values: 输出数组，Scalar 会将计算结果写入这个数组
                                       // Scalar.execute 方法会遍历所有表达式，依次计算并写入结果
                                       // 这种设计避免了对每行重复创建表达式对象，提高性能
      sink.send(new Row(values)); // 将投影后的行发送到输出流
                                  // sink 是父类 AbstractSingleNode 提供的输出目标
                                  // new Row(values) 将输出数组包装成 Row 对象
                                  // 这一行会被下一个解释器节点接收处理
    } // 循环结束，所有输入行都已处理完毕
  } // 方法结束
} // 类结束
