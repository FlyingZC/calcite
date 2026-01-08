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
// 声明包名，该类属于 org.apache.calcite.interpreter 包，表示它是 Calcite 解释器模块的一部分
package org.apache.calcite.interpreter;

// 导入 Union 类，这是 Calcite 关系代数中表示 UNION 操作的核心类
import org.apache.calcite.rel.core.Union;

// 导入 Google Guava 库中的 ImmutableList，用于构建不可变列表，确保线程安全和数据一致性
import com.google.common.collect.ImmutableList;

// 导入 Java 标准库中的 HashSet，用于实现去重功能
import java.util.HashSet;
// 导入 Java 标准库中的 Set 接口，用于存储已处理的行，实现 UNION ALL 或 UNION DISTINCT
import java.util.Set;

/**
 * 解释器节点类，用于实现 SQL 中的 UNION 操作
 * Interpreter node that implements a
 * {@link org.apache.calcite.rel.core.Union}.
 *
 * 该类负责在解释器模式下执行 UNION 操作，包括：
 * 1. 从多个输入源（Source）中读取数据行
 * 2. 根据 UNION 的类型（ALL 或 DISTINCT）决定是否去重
 * 3. 将处理后的结果行发送到输出目标（Sink）
 *
 * @deprecated 该类已被弃用，建议使用 {@link org.apache.calcite.interpreter.SetOpNode} 替代
 * SetOpNode 提供了更通用的集合操作实现，支持 UNION、INTERSECT、EXCEPT 等多种操作
 */
// 使用 @Deprecated 注解标记该类已过时，编译器会发出警告，提醒开发者使用新的替代类
@Deprecated // to be removed before 2.0
// 声明 UnionNode 类，实现 Node 接口，表示它是一个可执行的解释器节点
public class UnionNode implements Node {
  // 成员变量：sources，不可变的列表，存储所有输入数据源
  // 每个 Source 代表 UNION 操作的一个输入表或子查询结果
  // 使用 ImmutableList 确保在运行时不会被意外修改，保证线程安全
  private final ImmutableList<Source> sources;
  
  // 成员变量：sink，输出目标，用于接收 UNION 操作后的结果行
  // Sink 是解释器模式的输出端点，可以将数据发送到下一个节点或最终消费者
  private final Sink sink;
  
  // 成员变量：rel，对应的 Union 关系表达式
  // 保存原始的 Union RelNode 对象，用于访问其属性（如 all 标志、输入数量等）
  // rel.all 标志决定是执行 UNION ALL（保留重复行）还是 UNION DISTINCT（去重）
  private final Union rel;

  /**
   * 构造方法：创建 UnionNode 实例
   * @param compiler 编译器对象，负责将关系表达式编译为可执行的解释器节点
   * @param rel Union 关系表达式，包含 UNION 操作的元数据（如是否去重、输入列表等）
   */
  public UnionNode(Compiler compiler, Union rel) {
    // 创建 ImmutableList.Builder，用于构建不可变的 Source 列表
    // Builder 模式提供了一种灵活的方式来逐步添加元素，最后构建不可变集合
    ImmutableList.Builder<Source> builder = ImmutableList.builder();
    
    // 遍历 Union 关系表达式的所有输入，为每个输入创建对应的 Source 对象
    // rel.getInputs() 返回该 UNION 操作的所有输入 RelNode 列表
    // 例如：SELECT * FROM t1 UNION SELECT * FROM t2，则有两个输入
    for (int i = 0; i < rel.getInputs().size(); i++) {
      // 调用编译器的 source 方法，将第 i 个输入 RelNode 编译为 Source 对象
      // Source 是解释器模式中的数据源接口，提供 receive() 方法逐行读取数据
      // compiler.source() 方法会递归地编译输入的子树，生成完整的执行计划
      builder.add(compiler.source(rel, i));
    }
    // 构建 Source 的不可变列表，赋值给成员变量 sources
    // 至此，sources 包含了 UNION 操作所有输入的数据源，按顺序排列
    this.sources = builder.build();
    
    // 调用编译器的 sink 方法，为当前 Union 创建输出目标
    // sink 负责接收处理后的行，并将其传递给下游节点或最终消费者
    this.sink = compiler.sink(rel);
    
    // 保存 Union 关系表达式引用，用于在 run() 方法中访问其属性（如 all 标志）
    this.rel = rel;
  }

  /**
   * 执行 UNION 操作的核心方法
   * 该方法实现了 UNION 的执行逻辑，包括数据读取、去重（如果需要）和结果输出
   * 
   * @throws InterruptedException 如果线程在执行过程中被中断，抛出此异常
   * 
   * 执行流程：
   * 1. 根据 rel.all 标志决定是否需要去重
   * 2. 遍历所有输入源，逐行读取数据
   * 3. 如果需要去重，使用 Set 过滤重复行；否则直接输出所有行
   * 4. 将符合条件的行发送到输出目标
   */
  @Override public void run() throws InterruptedException {
    // 声明用于去重的 Set 集合
    // 如果 rel.all 为 true，表示 UNION ALL，不需要去重，rows 设为 null
    // 如果 rel.all 为 false，表示 UNION DISTINCT，需要去重，创建 HashSet 存储已处理的行
    // HashSet 基于哈希表，提供 O(1) 的查找和插入性能，适合快速判断行是否重复
    final Set<Row> rows = rel.all ? null : new HashSet<>();
    
    // 遍历所有输入源，按顺序处理每个输入的数据
    // sources 列表中的顺序与 SQL 语句中 UNION 的输入顺序一致
    for (Source source : sources) {
      // 声明 row 变量，用于存储从 Source 中读取的当前行
      Row row;
      
      // 循环从当前 Source 中读取行，直到返回 null 表示数据读取完毕
      // source.receive() 是阻塞方法，会等待下一行数据或返回 null
      // 这种设计允许处理流式数据，不需要一次性加载所有数据到内存
      while ((row = source.receive()) != null) {
        // 判断当前行是否应该输出到结果集
        // 条件 1：rows == null，表示 UNION ALL，不需要去重，直接输出
        // 条件 2：rows.add(row) 返回 true，表示该行是首次出现（未重复），应该输出
        // rows.add(row) 方法会返回 boolean：true 表示添加成功（行不存在），false 表示行已存在
        // 使用短路或运算符，如果 rows == null 为 true，则不会执行 rows.add(row)，避免 NPE
        if (rows == null || rows.add(row)) {
          // 将符合条件的行发送到输出目标
          // sink.send() 方法会将行传递给下游节点或最终消费者
          // 这是解释器模式中数据流动的关键步骤，实现了节点间的数据传递
          sink.send(row);
        }
      }
    }
  }
}
