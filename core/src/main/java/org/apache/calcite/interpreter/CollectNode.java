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
package org.apache.calcite.interpreter; // 包声明，表示这个类属于org.apache.calcite.interpreter包，这是Calcite解释器模块的核心包

import org.apache.calcite.rel.core.Collect; // 导入Collect关系表达式类，Collect是Calcite中用于收集多行数据并返回单个集合行的操作符

import com.google.common.collect.ImmutableList; // 导入Google Guava库的ImmutableList类，用于创建不可变的列表集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Checker框架的Nullable注解，用于标记可以为null的类型，帮助进行空值检查

import java.util.ArrayList; // 导入Java标准库的ArrayList类，这是一个动态数组实现，用于存储对象列表
import java.util.List; // 导入Java标准库的List接口，定义了列表集合的通用行为

/**
 * Interpreter node that implements a // 这是一个解释器节点类，用于实现
 * {@link org.apache.calcite.rel.core.Collect}. // Calcite的Collect关系表达式（收集操作符）
 * 
 * 【CollectNode类的核心作用】：
 * CollectNode是Calcite解释器模式中用于实现Collect（收集）操作的节点类。
 * 
 * 【Collect操作符的功能】：
 * Collect操作符在SQL中对应的是将多行数据收集成一个集合（通常是数组或多值集合）。
 * 例如：SELECT array_agg(column) FROM table 这样的聚合函数操作，底层就会使用Collect操作符。
 * 
 * 【继承关系说明】：
 * CollectNode继承自AbstractSingleNode<Collect>，表示这是一个单子节点的解释器节点。
 * AbstractSingleNode是抽象基类，泛型参数<Collect>表示该节点处理的关联关系表达式类型。
 * 单子节点意味着它只有一个输入源（source）和一个输出目标（sink）。
 * 
 * 【数据流向】：
 * 输入：从source接收多行数据（每行是一个Row对象，包含多个字段值）
 * 处理：将所有接收到的行收集到一个列表中
 * 输出：将收集到的所有行作为一个包含数组的单一Row对象发送到sink
 * 
 * 【典型使用场景】：
 * 1. 数组聚合函数（array_agg, collect_list等）的底层实现
 * 2. 将子查询的结果集作为数组返回
 * 3. 需要将多行数据打包成单一行的复杂查询
 * 
 * 【执行机制】：
 * 在解释器模式下，Calcite通过解释器节点树来执行查询计划。每个节点代表一个关系操作符，
 * 节点之间通过source和sink传递数据。CollectNode在执行时会持续从source读取数据，
 * 直到source返回null（表示数据读取完毕），然后将所有收集的数据打包成一个单一行输出。
 * 
 * 【与Enumerable模式的区别】：
 * CollectNode用于解释器模式（Interpreter模式），这是Calcite提供的一种执行模式。
 * 在Enumerable模式中，类似的操作通过EnumerableCollect类实现。
 * 解释器模式更直观，适合调试和理解，而Enumerable模式性能更好，适合生产环境。
 */
public class CollectNode extends AbstractSingleNode<Collect> { // 类声明：继承自AbstractSingleNode，泛型指定处理Collect关系表达式

  /**
   * 【构造方法】
   * 创建一个CollectNode实例，用于执行收集操作。
   * 
   * @param compiler 编译器对象，负责将关系表达式编译成可执行的代码
   *                 Compiler包含了编译时所需的所有上下文信息和配置
   * @param rel Collect关系表达式对象，表示SQL中收集操作的逻辑计划节点
   *             Collect rel包含了收集操作的所有元数据信息，如输入字段、输出字段等
   * 
   * 【构造方法的作用】：
   * 1. 调用父类AbstractSingleNode的构造方法，初始化编译器和关系表达式
   * 2. 父类构造方法会设置source（数据源）和sink（数据接收器）等基础属性
   * 3. 为后续的run()方法执行做好准备
   * 
   * 【继承的成员变量说明】：
   * 从AbstractSingleNode继承的成员变量包括：
   * - protected final Compiler compiler: 编译器，用于编译子节点
   * - protected final RelNode rel: 关系表达式，表示当前节点的逻辑操作
   * - protected Node source: 输入节点，提供数据流
   * - protected Sink sink: 输出接收器，接收处理后的数据
   * 
   * 【编译器的作用】：
   * Compiler是Calcite解释器模式的核心组件，负责将关系表达式树编译成可执行的节点树。
   * 它包含了编译时需要的所有信息，如类型系统、函数注册表等。
   * 
   * 【Collect关系表达式的作用】：
   * Collect rel是逻辑计划中的收集操作节点，它包含了：
   * - 输入关系表达式（要收集的数据源）
   * - 输出类型（收集后的数据类型，通常是数组类型）
   * - 收集的字段信息（哪些字段需要被收集）
   */
  public CollectNode(Compiler compiler, Collect rel) { // 构造方法声明，接收编译器和Collect关系表达式作为参数
    super(compiler, rel); // 调用父类AbstractSingleNode的构造方法，初始化编译器和关系表达式
  } // 构造方法结束

  /**
   * 【核心执行方法】
   * 执行收集操作，将输入的多行数据收集成一个单一行输出。
   * 
   * @throws InterruptedException 如果线程在执行过程中被中断，抛出此异常
   * 
   * 【方法执行流程详解】：
   * 1. 声明一个Row变量，用于临时存储从source接收到的每一行数据
   * 2. 创建一个ArrayList列表，用于存储所有收集到的行的值（每行的值是一个Object数组）
   * 3. 进入while循环，持续从source接收数据行，直到source返回null（表示数据读取完毕）
   * 4. 在循环中，将每一行的值（row.getValues()返回Object[]）添加到values列表中
   * 5. 循环结束后，将values列表转换成不可变的ImmutableList，并创建一个新的Row对象
   * 6. 将这个包含所有收集数据的Row对象发送到sink，完成数据输出
   * 
   * 【数据结构说明】：
   * - Row: Calcite中的行对象，表示一行数据，可以包含多个字段值
   * - Object[]: 表示一行中所有字段的值数组，每个元素对应一个字段
   * - List<Object[]>: 表示多行数据，每个元素是一个Object[]，代表一行
   * - ImmutableList: 不可变列表，确保数据在创建后不会被修改，提高安全性
   * 
   * 【为什么使用ImmutableList】：
   * 1. 线程安全：不可变对象天然是线程安全的
   * 2. 防止意外修改：确保收集的数据在传递过程中不会被修改
   * 3. 优化性能：ImmutableList在内部实现上有一些优化
   * 4. 符合函数式编程原则：数据一旦创建就不应该被修改
   * 
   * 【Nullable注解的作用】：
   * List<@Nullable Object[]> 表示列表中的每个Object[]元素可以为null。
   * 这是因为某些行可能包含null值，或者某些字段本身可能就是null。
   * @Nullable注解帮助静态分析工具进行空值检查，提高代码的健壮性。
   * 
   * 【中断处理】：
   * 方法声明了throws InterruptedException，表示在执行过程中可能会被中断。
   * 这是Java多线程编程的标准实践，允许外部线程中断长时间运行的操作。
   * 在实际执行中，如果线程被中断，会抛出InterruptedException异常，调用者需要处理这个异常。
   * 
   * 【性能考虑】：
   * 1. 使用ArrayList而不是LinkedList，因为ArrayList在随机访问和遍历上性能更好
   * 2. 在循环结束后一次性创建ImmutableList，避免在循环中频繁创建不可变对象
   * 3. 将所有数据收集到内存中，可能会消耗较多内存，适用于中小数据集
   * 
   * 【与其他节点的协作】：
   * - source: 数据源节点，可能是TableScan、Filter、Project等其他解释器节点
   * - sink: 数据接收器，可能是另一个节点的输入，或者是最终结果接收器
   * 通过source和sink的连接，形成一个解释器节点树，数据从叶子节点流向根节点
   * 
   * 【实际应用示例】：
   * 假设有SQL查询：SELECT array_agg(emp_id) FROM employees WHERE department = 'IT'
   * 执行流程：
   * 1. TableScanNode扫描employees表
   * 2. FilterNode过滤出department='IT'的行
   * 3. ProjectNode只选择emp_id字段
   * 4. CollectNode收集所有emp_id值
   * 5. 最终输出一个包含emp_id数组 的单一行
   */
  @Override public void run() throws InterruptedException { // 重写父类的run方法，声明可能抛出中断异常
    Row row; // 声明一个Row变量row，用于临时存储从source接收到的每一行数据
    List<@Nullable Object[]> values = new ArrayList<>(); // 创建一个ArrayList列表values，用于存储所有收集到的行的值，每个元素是一个Object[]数组，可以为null
    while ((row = source.receive()) != null) { // while循环：从source接收数据行，赋值给row，判断row是否不为null（null表示数据读取完毕）
      values.add(row.getValues()); // 将当前行的值数组（Object[]）添加到values列表中，row.getValues()返回该行所有字段的值
    } // while循环结束，此时values列表包含了所有从source接收到的行的值
    row = Row.of(ImmutableList.copyOf(values)); // 创建一个新的Row对象，将values列表转换成不可变的ImmutableList，作为该行的唯一值（这个值是一个包含所有收集数据的列表）
    sink.send(row); // 将包含所有收集数据的Row对象发送到sink，完成数据输出，sink可能是下一个节点的输入或最终结果接收器
  } // run方法结束
} // CollectNode类定义结束
