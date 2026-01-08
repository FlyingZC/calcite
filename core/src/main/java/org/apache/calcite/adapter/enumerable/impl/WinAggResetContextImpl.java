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
package org.apache.calcite.adapter.enumerable.impl; // 定义包路径，位于Calcite框架的可枚举适配器模块的impl包下，存放实现类

import org.apache.calcite.adapter.enumerable.WinAggResetContext; // 导入窗口聚合重置上下文接口，定义了窗口聚合重置阶段的标准接口
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建Java代码块（包含多条语句的代码块）
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，表示Java表达式（如变量引用、方法调用、字面量等）

import java.util.List; // 导入List接口，用于存储表达式列表

/**
 * Implementation of
 * {@link org.apache.calcite.adapter.enumerable.WinAggResetContext}.
 * WinAggResetContextImpl是窗口聚合重置上下文的实现类，用于在窗口函数聚合的重置阶段提供完整的上下文信息
 * 
 * 【类的核心作用详解】：
 * 1. 窗口函数（Window Function）是SQL的高级特性，允许在不改变行数的情况下进行聚合计算
 * 2. 常见窗口函数：ROW_NUMBER()、RANK()、SUM() OVER()、AVG() OVER()等
 * 3. 窗口聚合的生命周期包括三个阶段：
 *    - Reset（重置）：在窗口帧移动或新分区开始时，重置累加器状态
 *    - Add（添加）：将窗口帧内的行数据添加到累加器中
 *    - Result（结果）：根据累加器状态计算最终结果
 * 4. 这个类专门负责Reset阶段，提供重置所需的所有信息
 * 
 * 【继承关系】：
 * - 继承自 AggResetContextImpl：获得聚合重置的基础功能（如访问累加器变量、构建代码块）
 * - 实现 WinAggResetContext 接口：提供窗口聚合特有的重置功能
 * - WinAggResetContext 接口组合了 AggResetContext 和 WinAggFrameContext 两个接口
 * 
 * 【与普通聚合的区别】：
 * 普通聚合（AggResetContextImpl）：
 *   - 每个分组独立计算，重置时只需重置累加器
 *   - 不需要考虑窗口帧、分区边界、当前行位置等信息
 *   - 例如：GROUP BY dept_id 后计算每个部门的 SUM(salary)
 * 
 * 窗口聚合（WinAggResetContextImpl）：
 *   - 需要考虑窗口帧的边界（ROWS BETWEEN ... AND ...）
 *   - 需要知道当前行在分区中的位置
 *   - 需要知道分区的范围（起始和结束索引）
 *   - 需要知道窗口帧和分区的行数
 *   - 例如：SUM(salary) OVER (PARTITION BY dept_id ORDER BY emp_id ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)
 * 
 * 【成员变量详解】：
 * 1. index：当前行在分区中的索引，用于确定当前处理的是哪一行
 * 2. startIndex：分区第一行的索引，用于确定分区的起始位置
 * 3. endIndex：分区最后一行的索引，用于确定分区的结束位置
 * 4. frameRowCount：当前窗口帧中的行数，用于确定窗口帧的大小
 * 5. partitionRowCount：当前分区中的总行数，用于确定分区的总大小
 * 6. hasRows：布尔表达式，判断分区是否为空，避免对空分区进行操作
 * 
 * 【使用场景示例】：
 * SQL示例：
 *   SELECT 
 *     emp_id,
 *     dept_id,
 *     salary,
 *     SUM(salary) OVER (
 *       PARTITION BY dept_id  -- 按部门分区
 *       ORDER BY emp_id       -- 按员工ID排序
 *       ROWS BETWEEN 2 PRECEDING AND CURRENT ROW  -- 窗口帧：当前行及前2行
 *     ) as moving_sum
 *   FROM employees;
 * 
 * 执行流程：
 * 1. 按 dept_id 分区，每个部门形成一个独立的分区
 * 2. 在每个分区内按 emp_id 排序
 * 3. 对于每一行，定义一个窗口帧（当前行及前2行）
 * 4. 当窗口帧移动时，需要重置累加器并重新计算 SUM
 * 5. WinAggResetContextImpl 提供了重置所需的所有信息：
 *    - 当前行的索引（index）
 *    - 窗口帧的起始和结束索引（通过 index 计算）
 *    - 窗口帧的行数（frameRowCount = 3）
 *    - 分区的总行数（partitionRowCount）
 * 
 * 【代码生成流程】：
 * 1. Calcite 在生成窗口聚合的 Java 代码时，会创建 WinAggResetContextImpl 实例
 * 2. 将窗口帧的元数据信息（索引、行数等）封装为 Expression 对象
 * 3. 调用 AggImplementor.implementReset() 方法，传入这个上下文
 * 4. AggImplementor 根据窗口函数类型（SUM、AVG、ROW_NUMBER等）生成相应的重置代码
 * 5. 生成的代码会在运行时执行，重置累加器状态
 * 
 * 【重要特性】：
 * - 第一次重置时，索引和行数信息可能不准确，只能作为预分配大小的提示
 * - 实现者应该能够处理这种不确定性
 * - 后续的重置操作会提供准确的索引和行数信息
 * - 所有成员变量都是 Expression 类型，表示代码生成时的表达式，而非运行时的实际值
 */
public class WinAggResetContextImpl extends AggResetContextImpl // 定义类，继承聚合重置上下文实现类，获得基础重置功能
    implements WinAggResetContext { // 实现窗口聚合重置上下文接口，提供窗口特有的重置功能
  private final Expression index; // 成员变量：当前行在分区中的索引表达式，用于确定当前处理的是哪一行数据
  private final Expression startIndex; // 成员变量：分区第一行的索引表达式，表示分区的起始位置，用于计算窗口帧边界
  private final Expression endIndex; // 成员变量：分区最后一行的索引表达式，表示分区的结束位置，用于确定分区的范围
  private final Expression frameRowCount; // 成员变量：当前窗口帧中的行数表达式，表示窗口帧包含多少行数据，受 ROWS/RANGE 子句限制
  private final Expression partitionRowCount; // 成员变量：当前分区中的总行数表达式，表示整个分区包含多少行数据，由 PARTITION BY 子句确定
  private final Expression hasRows; // 成员变量：布尔表达式，用于判断当前分区是否包含行数据，避免对空分区进行操作

  /**
   * Creates window aggregate reset context.
   * 创建窗口聚合重置上下文的构造方法
   *
   * @param block code block that will contain the added initialization // 参数：代码块构建器，用于包含添加的初始化代码，所有生成的重置代码都会添加到这个代码块中
   * @param accumulator accumulator variables that store the intermediate // 参数：累加器变量列表，存储聚合计算的中间状态，如SUM累加器存储累加和，AVG累加器存储sum和count
   *                    aggregate state // 累加器是窗口聚合计算的核心，用于在窗口帧内累积数据
   * @param index index of the current row in the partition // 参数：当前行在分区中的索引表达式，用于确定当前处理的是哪一行，通常是一个整数变量引用
   * @param startIndex index of the very first row in partition // 参数：分区第一行的索引表达式，表示分区的起始位置，通常是0或某个整数变量引用
   * @param endIndex index of the very last row in partition // 参数：分区最后一行的索引表达式，表示分区的结束位置，通常是 partitionRowCount - 1
   * @param hasRows boolean expression that tells if the partition has rows // 参数：布尔表达式，判断分区是否为空，如果分区为空则为 false，否则为 true
   * @param frameRowCount number of rows in the current frame // 参数：当前窗口帧中的行数表达式，表示窗口帧包含的行数，如 ROWS BETWEEN 2 PRECEDING AND CURRENT ROW 则为 3
   * @param partitionRowCount number of rows in the current partition // 参数：当前分区中的总行数表达式，表示分区包含的所有行数，用于确定分区的总大小
   * 
   * 构造方法的作用：
   * 1. 初始化窗口聚合重置上下文的所有必要信息
   * 2. 调用父类构造方法，初始化代码块构建器和累加器列表
   * 3. 保存窗口帧的元数据信息（索引、行数等）
   * 4. 这些信息将在后续的代码生成过程中被使用
   * 
   * 参数说明：
   * - block：代码块构建器，用于生成 Java 代码块
   * - accumulator：累加器变量列表，用于存储聚合计算的中间状态
   * - index：当前行索引，用于确定当前处理的是哪一行
   * - startIndex：分区起始索引，用于确定分区的起始位置
   * - endIndex：分区结束索引，用于确定分区的结束位置
   * - hasRows：布尔表达式，判断分区是否为空
   * - frameRowCount：窗口帧行数，表示窗口帧包含的行数
   * - partitionRowCount：分区总行数，表示分区包含的所有行数
   * 
   * 使用示例：
   * BlockBuilder block = new BlockBuilder();
   * List<Expression> accumulator = Arrays.asList(Expressions.variable(int.class, "sum"));
   * Expression index = Expressions.variable(int.class, "currentIndex");
   * Expression startIndex = Expressions.variable(int.class, "partitionStart");
   * Expression endIndex = Expressions.variable(int.class, "partitionEnd");
   * Expression hasRows = Expressions.variable(boolean.class, "hasRows");
   * Expression frameRowCount = Expressions.variable(int.class, "frameRowCount");
   * Expression partitionRowCount = Expressions.variable(int.class, "partitionRowCount");
   * WinAggResetContextImpl context = new WinAggResetContextImpl(
   *   block, accumulator, index, startIndex, endIndex, hasRows, frameRowCount, partitionRowCount
   * );
   */
  public WinAggResetContextImpl(BlockBuilder block, // 构造方法参数：代码块构建器，用于包含添加的初始化代码
      List<Expression> accumulator, Expression index, // 参数：累加器变量列表和当前行索引表达式
      Expression startIndex, Expression endIndex, // 参数：分区起始索引和结束索引表达式
      Expression hasRows, // 参数：布尔表达式，判断分区是否为空
      Expression frameRowCount, Expression partitionRowCount) { // 参数：窗口帧行数和分区总行数表达式
    super(block, accumulator); // 调用父类 AggResetContextImpl 的构造方法，初始化代码块构建器和累加器列表
    this.index = index; // 将当前行索引表达式赋值给成员变量，保存当前行的位置信息
    this.startIndex = startIndex; // 将分区起始索引表达式赋值给成员变量，保存分区的起始位置信息
    this.endIndex = endIndex; // 将分区结束索引表达式赋值给成员变量，保存分区的结束位置信息
    this.frameRowCount = frameRowCount; // 将窗口帧行数表达式赋值给成员变量，保存窗口帧的大小信息
    this.partitionRowCount = partitionRowCount; // 将分区总行数表达式赋值给成员变量，保存分区的总大小信息
    this.hasRows = hasRows; // 将布尔表达式赋值给成员变量，保存分区是否为空的信息
  }

  @Override public Expression index() { // 重写接口方法，返回当前行在分区中的索引表达式
    return index; // 返回当前行索引表达式，供调用者访问当前行的位置信息
  } // 方法结束，返回当前行索引表达式

  @Override public Expression startIndex() { // 重写接口方法，返回分区第一行的索引表达式
    return startIndex; // 返回分区起始索引表达式，供调用者访问分区的起始位置信息
  } // 方法结束，返回分区起始索引表达式

  @Override public Expression endIndex() { // 重写接口方法，返回分区最后一行的索引表达式
    return endIndex; // 返回分区结束索引表达式，供调用者访问分区的结束位置信息
  } // 方法结束，返回分区结束索引表达式

  @Override public Expression hasRows() { // 重写接口方法，返回判断分区是否为空的布尔表达式
    return hasRows; // 返回布尔表达式，供调用者判断分区是否包含行数据，避免对空分区进行操作
  } // 方法结束，返回布尔表达式

  @Override public Expression getFrameRowCount() { // 重写接口方法，返回当前窗口帧中的行数表达式
    return frameRowCount; // 返回窗口帧行数表达式，供调用者获取当前窗口帧包含的行数
  } // 方法结束，返回窗口帧行数表达式

  @Override public Expression getPartitionRowCount() { // 重写接口方法，返回当前分区中的总行数表达式
    return partitionRowCount; // 返回分区总行数表达式，供调用者获取当前分区包含的所有行数
  } // 方法结束，返回分区总行数表达式
} // 类定义结束，WinAggResetContextImpl 提供了窗口聚合重置阶段所需的完整上下文信息
