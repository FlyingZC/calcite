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
// 定义当前类所在的包路径，属于 org.apache.calcite.interpreter 包，这是 Calcite 解释器模块的包
package org.apache.calcite.interpreter;
// 导入 Values 类，这是 Calcite 关系代数中表示 VALUES 子句的 RelNode，对应 SQL 中的 VALUES (1,2), (3,4) 这样的常量表达式
import org.apache.calcite.rel.core.Values;
// 导入 RexLiteral 类，这是 Calcite 中表示字面常量的 RexNode，如数字 1、字符串 'abc' 等
import org.apache.calcite.rex.RexLiteral;
// 导入 RexNode 类，这是 Calcite 中行表达式的基类，用于表示关系表达式中的各种表达式
import org.apache.calcite.rex.RexNode;
// 导入 ImmutableList 类，这是 Google Guava 库提供的不可变列表实现，保证线程安全和不可变性
import com.google.common.collect.ImmutableList;
// 导入 ArrayList 类，Java 标准库中的动态数组实现，用于存储可变的元素列表
import java.util.ArrayList;
// 导入 List 接口，Java 标准库中的列表接口，定义了列表操作的基本方法
import java.util.List;

/**
 * Interpreter node that implements a
 * {@link org.apache.calcite.rel.core.Values}.
 * ValuesNode 是 Calcite 解释器模式下的节点实现类，用于在解释器执行模式下实现 Values 关系节点
 * Values 节点对应 SQL 中的 VALUES 子句，用于创建常量行集合，例如：SELECT * FROM (VALUES (1, 'a'), (2, 'b'))
 * 该类实现了 Node 接口，遵循 Calcite 解释器模式的节点接口规范
 * 解释器模式是 Calcite 提供的一种直接执行关系代数树的方式，不同于通过规则转换为可执行代码的方式
 */
public class ValuesNode implements Node {
  // sink 是一个数据接收器，用于将 ValuesNode 生成的行数据发送到下游节点
  // Sink 是解释器模式中节点之间传递数据的接口，类似生产者-消费者模式中的消费者
  // 通过调用 sink.send(row) 方法将每一行数据发送到下一个节点进行处理
  // private 修饰符表示该成员变量只能在类内部访问，final 修饰符表示该引用初始化后不能再指向其他对象
  private final Sink sink;
  // fieldCount 表示每行数据的字段（列）数量
  // 这个值从 Values RelNode 的行类型中获取，例如 VALUES (1, 'a', 100.0) 有 3 个字段，则 fieldCount = 3
  // 这个字段用于在创建行数据时确定每个子数组的大小，以及在复制数据时确定每次复制的元素个数
  // private 修饰符表示该成员变量只能在类内部访问，final 修饰符表示该值初始化后不能改变
  private final int fieldCount;
  // rows 是一个不可变的行列表，存储了 Values 节点要输出的所有行数据
  // Row 是解释器模式中表示一行数据的类，内部使用 Object[] 数组存储各个字段的值
  // ImmutableList 的使用保证了行集合的不可变性，避免了在执行过程中被意外修改
  // 这些行数据在构造函数中通过 createRows 方法预先创建并计算完成，run() 方法只需遍历发送即可
  // private 修饰符表示该成员变量只能在类内部访问，final 修饰符表示该引用初始化后不能再指向其他对象
  private final ImmutableList<Row> rows;

  /**
   * ValuesNode 的构造函数，用于初始化 ValuesNode 实例
   * @param compiler 编译器对象，用于编译表达式和创建上下文，是解释器模式的核心组件
   *        Compiler 负责将 RexNode 表达式编译为可执行的 Scalar 对象
   * @param rel Values 关系节点，包含 VALUES 子句的元数据和表达式信息
   *        Values 是 RelNode 的子类，表示关系代数中的常量值操作
   *        Values 对象中包含了行类型信息、字段信息和具体的元组数据（tuples）
   */
  public ValuesNode(Compiler compiler, Values rel) {
    // 通过编译器获取当前 Values 节点对应的数据接收器 Sink
    // compiler.sink(rel) 方法会根据当前节点创建一个合适的 Sink 实例
    // Sink 负责接收当前节点产生的数据并传递给下游节点
    // 这个 Sink 可能在构造过程中已经链接到了下一个节点的输入端
    this.sink = compiler.sink(rel);
    // 从 Values RelNode 的行类型中获取字段数量
    // rel.getRowType() 返回该 Values 节点的行类型，包含所有字段的名称和类型信息
    // getFieldCount() 方法返回该行类型中字段的总数
    // 例如：VALUES (1, 'a', 100.0) 的行类型有 3 个字段，fieldCount = 3
    this.fieldCount = rel.getRowType().getFieldCount();
    // 调用 createRows 静态方法创建所有行数据并存储到 rows 成员变量中
    // compiler 参数用于编译表达式，fieldCount 用于确定每行的字段数
    // rel.getTuples() 返回 Values 节点中定义的所有元组（行）的原始表达式列表
    // 每个元组是一个 ImmutableList<RexLiteral>，包含该行所有字段的字面量表达式
    // createRows 方法会将这些 RexLiteral 表达式编译并求值，转换为实际的 Row 对象
    this.rows = createRows(compiler, fieldCount, rel.getTuples());
  }

  /**
   * 静态工厂方法，用于创建包含所有行数据的不可变列表
   * 这个方法负责将 Values 节点中的 RexLiteral 表达式编译并求值，转换为实际的 Row 对象
   * @param compiler 编译器对象，用于将 RexNode 表达式编译为可执行的 Scalar
   *        Compiler 提供表达式编译和上下文创建功能
   * @param fieldCount 每行数据的字段数量，用于确定每个子数组的大小和复制的步长
   * @param tuples Values 节点中定义的所有元组（行）的原始表达式列表
   *        每个元组是一个 ImmutableList<RexLiteral>，包含该行所有字段的字面量表达式
   *        例如：[(1, 'a'), (2, 'b')] 是一个包含两个元组的列表
   * @return 包含所有行数据的不可变列表，每个元素是一个 Row 对象
   */
  private static ImmutableList<Row> createRows(Compiler compiler,
      int fieldCount,
      ImmutableList<ImmutableList<RexLiteral>> tuples) {
    // 创建一个动态列表用于存储所有的 RexNode 表达式
    // List<RexNode> 是一个可变列表，用于收集所有元组中的所有表达式
    // 这些表达式将被打平成一个一维列表，然后一次性编译
    // 例如：[(1, 'a'), (2, 'b')] 会被收集为 [1, 'a', 2, 'b']
    final List<RexNode> nodes = new ArrayList<>();
    // 遍历所有元组（行），将每个元组中的所有表达式添加到 nodes 列表中
    // tuple 是一个 ImmutableList<RexLiteral>，表示一行中的所有字段表达式
    // nodes.addAll(tuple) 将当前元组中的所有表达式添加到 nodes 列表的末尾
    // 这样可以将二维的元组列表打平成一维的表达式列表
    for (ImmutableList<RexLiteral> tuple : tuples) {
      // 将当前元组中的所有 RexLiteral 表达式添加到 nodes 列表中
      // addAll 方法会将 tuple 中的所有元素按顺序添加到 nodes 的末尾
      nodes.addAll(tuple);
    }
    // 使用编译器将所有表达式编译为一个可执行的 Scalar 对象
    // compiler.compile(nodes, null) 方法将 RexNode 列表编译为 Scalar
    // Scalar 是解释器模式中表示可执行表达式的接口，可以执行并返回结果
    // 第二个参数 null 表示不需要特定的输出类型，编译器会自动推断
    // 编译后的 Scalar 可以一次性计算所有表达式的值
    final Scalar scalar = compiler.compile(nodes, null);
    // 创建一个对象数组用于存储所有表达式的计算结果
    // 数组的大小等于表达式的总数，即所有元组的字段总数
    // 例如：2 个元组，每个 3 个字段，则 values 数组大小为 6
    // 这个数组将作为 Scalar.execute() 方法的输出参数
    final Object[] values = new Object[nodes.size()];
    // 创建执行上下文，用于 Scalar 执行时的环境
    // 上下文可能包含变量绑定、函数注册等执行环境信息
    // 对于 Values 节点，上下文通常比较简单，因为只涉及字面量表达式
    final Context context = compiler.createContext();
    // 执行编译后的 Scalar，将计算结果填充到 values 数组中
    // scalar.execute(context, values) 方法会在给定的上下文中执行表达式
    // 计算结果会按顺序填充到 values 数组的对应位置
    // 例如：表达式 [1, 'a', 2, 'b'] 的计算结果会填充为 [1, 'a', 2, 'b']
    scalar.execute(context, values);
    // 创建一个不可变列表的构建器，用于构建最终的行列表
    // ImmutableList.Builder 提供了链式调用的方式来构建不可变列表
    // 使用构建器模式可以更高效地构建集合，避免中间对象的创建
    final ImmutableList.Builder<Row> rows = ImmutableList.builder();
    // 创建一个临时数组，用于存储单行数据的字段值
    // 数组大小为 fieldCount，即每行的字段数量
    // 这个数组会在循环中重复使用，每次复制一行数据后创建 Row 对象
    // 使用临时数组可以避免在每次迭代时都创建新的数组，提高性能
    Object[] subValues = new Object[fieldCount];
    // 遍历所有元组，将 values 数组中的数据按行分割并创建 Row 对象
    // r 是当前行的索引，从 0 开始
    // n 是元组的总数，即总行数
    // 每次循环处理一行数据
    for (int r = 0, n = tuples.size(); r < n; ++r) {
      // 从 values 数组中复制当前行的数据到 subValues 数组
      // values 是一个一维数组，包含了所有行的所有字段值
      // r * fieldCount 是当前行在 values 数组中的起始索引
      // subValues 是目标数组，从索引 0 开始接收数据
      // fieldCount 是要复制的元素个数，即每行的字段数
      // 例如：fieldCount=3，r=0 时，复制 values[0:2] 到 subValues[0:2]
      // r=1 时，复制 values[3:5] 到 subValues[0:2]
      System.arraycopy(values, r * fieldCount, subValues, 0, fieldCount);
      // 将 subValues 数组包装为 Row 对象并添加到行列表中
      // Row.asCopy(subValues) 方法会创建 subValues 数组的副本并包装为 Row 对象
      // 使用副本可以确保每个 Row 对象拥有独立的数据，避免共享引用
      // rows.add() 将创建的 Row 对象添加到构建器中
      rows.add(Row.asCopy(subValues));
    }
    // 构建并返回不可变的行列表
    // build() 方法会根据已添加的所有 Row 对象构建一个 ImmutableList<Row>
    // 返回的列表是不可变的，保证了数据的安全性和线程安全性
    return rows.build();
  }

  /**
   * 执行 ValuesNode 节点，将所有行数据发送到下游
   * 这是 Node 接口要求实现的核心方法，用于在解释器模式下执行节点逻辑
   * @throws InterruptedException 如果执行过程中被中断，抛出中断异常
   *         解释器模式支持中断机制，允许在长时间运行的操作中响应中断信号
   */
  @Override public void run() throws InterruptedException {
    // 遍历所有行数据，将每一行发送到下游节点
    // rows 是在构造函数中预先创建的不可变行列表
    // for-each 循环会依次取出每个 Row 对象
    // 每次迭代处理一行数据
    for (Row row : rows) {
      // 将当前行数据发送到 Sink，由 Sink 转发到下游节点
      // sink.send(row) 方法会将 row 对象传递给下一个节点进行处理
      // 这是解释器模式中节点间数据传递的标准方式
      // 下游节点会接收到这个 row 并继续处理
      sink.send(row);
    }
    // 通知 Sink 数据发送完成，没有更多数据了
    // sink.end() 方法会标记数据流的结束，通知下游节点可以结束处理
    // 这对于某些需要知道数据流结束的节点很重要，例如聚合节点
    // 调用 end() 后，下游节点可以执行最终的清理或结果输出操作
    sink.end();
  }
}
