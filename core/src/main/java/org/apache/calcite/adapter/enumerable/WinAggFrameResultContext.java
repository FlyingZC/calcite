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
package org.apache.calcite.adapter.enumerable;

import org.apache.calcite.linq4j.tree.Expression;

/**
 * 窗口聚合框架结果上下文接口
 * 
 * 【类的作用】
 * 该接口提供了在计算窗口聚合结果时所需的窗口信息，是Calcite框架中用于窗口函数实现的核心接口之一。
 * 它扩展了WinAggFrameContext接口，专门用于处理窗口聚合结果的计算阶段。
 * 
 * 【窗口函数背景知识】
 * 窗口函数是SQL中的一种高级功能，允许在对行集进行聚合计算的同时，保持行的独立性。
 * 例如：SUM() OVER (PARTITION BY dept ORDER BY salary ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
 * 
 * 【核心概念】
 * 1. Partition（分区）：窗口函数作用的行集，由PARTITION BY子句定义
 * 2. Frame（框架）：分区内的一个子集，由ROWS/RANGE/BETWEEN子句定义
 * 3. Window（窗口）：包含分区和框架的完整上下文
 * 
 * 【接口职责】
 * 该接口提供了在计算窗口聚合结果时所需的以下能力：
 * - 计算相对位置对应的绝对索引
 * - 检查给定索引是否在框架边界内
 * - 检查给定索引是否在分区边界内
 * - 获取指定行的数据转换器
 * - 比较两行在窗口排序中的相对顺序
 * 
 * 【使用场景】
 * 当Calcite优化器将窗口函数转换为可执行的Enumerable代码时，需要使用此接口来：
 * 1. 确定当前处理行的窗口范围
 * 2. 访问窗口内的其他行数据
 * 3. 执行窗口聚合计算（如SUM、AVG、ROW_NUMBER等）
 * 
 * 【实现类】
 * 该接口通常由EnumerableWindow等具体实现类实现，在代码生成阶段使用。
 */
public interface WinAggFrameResultContext extends WinAggFrameContext {
  /**
   * 计算相对位置对应的绝对索引位置
   * 
   * 【方法作用】
   * 将相对偏移量转换为绝对行索引。这是窗口函数实现中的关键方法，
   * 用于定位窗口内相对于当前行的其他行。
   * 
   * 【相对位置概念】
   * 在窗口函数中，经常需要引用相对于当前行的位置，例如：
   * - ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING
   * - ROW_NUMBER() OVER (ORDER BY col)
   * 
   * 【参数说明】
   * @param offset 请求行的偏移量，表示相对于某个参考点的位置
   *              例如：offset=1表示下一行，offset=-1表示上一行
   *              这是一个表达式对象，在代码生成时会被替换为实际的变量或常量
   * 
   * @param seekType 偏移量的类型，指定offset是相对于哪个参考点的
   *                 枚举值包括：
   *                 - START: 相对于窗口框架的起始位置
   *                 - END: 相对于窗口框架的结束位置
   *                 - CURRENT: 相对于当前行
   *                 - UNBOUNDED_PRECEDING: 相对于分区开始
   *                 - UNBOUNDED_FOLLOWING: 相对于分区结束
   * 
   * 【返回值说明】
   * @return 请求行的绝对位置，返回一个表达式对象
   *         这个表达式在运行时会计算出实际的行索引
   *         索引通常是0-based的整数，表示在分区中的绝对位置
   * 
   * 【使用示例】
   * 假设当前行索引为5，窗口定义为ROWS BETWEEN 2 PRECEDING AND 2 FOLLOWING：
   * - computeIndex(Expressions.constant(-2), SeekType.CURRENT) 返回 3（上一行）
   * - computeIndex(Expressions.constant(0), SeekType.CURRENT) 返回 5（当前行）
   * - computeIndex(Expressions.constant(2), SeekType.CURRENT) 返回 7（下一行）
   * 
   * 【实现要点】
   * 1. 需要考虑窗口框架的边界条件
   * 2. 需要处理UNBOUNDED PRECEDING/FOLLOWING等特殊情况
   * 3. 返回的表达式应该能够正确处理边界外的索引
   */
  Expression computeIndex(Expression offset,
      WinAggImplementor.SeekType seekType);

  /**
   * 检查给定索引是否在窗口框架边界内
   * 
   * 【方法作用】
   * 判断指定的行索引是否在当前窗口框架的有效范围内。
   * 这是实现窗口函数时的重要边界检查方法，确保只处理框架内的行。
   * 
   * 【窗口框架概念】
   * 窗口框架是分区内的一个行子集，由以下因素定义：
   * - FRAME子句：ROWS/RANGE/GROUPS
   * - 边界：UNBOUNDED PRECEDING, CURRENT ROW, UNBOUNDED FOLLOWING等
   * - 偏移：n PRECEDING, n FOLLOWING
   * 
   * 【参数说明】
   * @param rowIndex 要检查的行的索引
   *                 这是一个表达式对象，代表要验证的行位置
   *                 索引是相对于整个分区的绝对位置
   * 
   * 【返回值说明】
   * @return 布尔表达式，用于验证给定索引是否在框架边界内
   *         如果索引在框架内，表达式计算结果为true
   *         如果索引在框架外，表达式计算结果为false
   * 
   * 【使用场景】
   * 1. 在遍历窗口内的行时，跳过框架外的行
   * 2. 在计算聚合函数时，只统计框架内的行
   * 3. 在访问行数据前进行边界检查，避免数组越界
   * 
   * 【示例】
   * 假设分区有10行（索引0-9），当前行是第5行，
   * 窗口框架是ROWS BETWEEN 2 PRECEDING AND 1 FOLLOWING：
   * - rowInFrame(0) 返回 false（超出框架下界）
   * - rowInFrame(3) 返回 true（在框架内）
   * - rowInFrame(5) 返回 true（当前行）
   * - rowInFrame(6) 返回 true（在框架内）
   * - rowInFrame(9) 返回 false（超出框架上界）
   * 
   * 【实现要点】
   * 1. 需要考虑不同类型的FRAME（ROWS/RANGE/GROUPS）
   * 2. 需要处理UNBOUNDED边界的情况
   * 3. 表达式应该能够高效地执行边界检查
   */
  Expression rowInFrame(Expression rowIndex);

  /**
   * 检查给定索引是否在分区边界内
   * 
   * 【方法作用】
   * 判断指定的行索引是否在当前分区的有效范围内。
   * 分区是窗口函数作用的最大行集，由PARTITION BY子句定义。
   * 
   * 【分区概念】
   * 分区是将数据按照某个或某些列的值分组后的结果。
   * 例如：PARTITION BY dept_id会将数据按部门ID分成多个分区。
   * 每个分区都是独立的，窗口函数在每个分区内单独计算。
   * 
   * 【参数说明】
   * @param rowIndex 要检查的行的索引
   *                 这是一个表达式对象，代表要验证的行位置
   *                 索引是相对于整个数据集的绝对位置
   * 
   * 【返回值说明】
   * @return 布尔表达式，用于验证给定索引是否在分区边界内
   *         如果索引在分区内，表达式计算结果为true
   *         如果索引在分区外，表达式计算结果为false
   * 
   * 【使用场景】
   * 1. 在跨分区访问行数据时，确保不越界
   * 2. 在处理分区边界时，避免访问其他分区的数据
   * 3. 在实现某些窗口函数时（如LEAD/LAG），需要检查分区边界
   * 
   * 【示例】
   * 假设有两个分区：
   * - 分区1包含行索引0-4（5行数据）
   * - 分区2包含行索引5-9（5行数据）
   * 
   * 如果当前在分区1中：
   * - rowInPartition(0) 返回 true（在分区1内）
   * - rowInPartition(4) 返回 true（在分区1内）
   * - rowInPartition(5) 返回 false（在分区2中）
   * - rowInPartition(9) 返回 false（在分区2中）
   * 
   * 【与rowInFrame的区别】
   * - rowInPartition检查的是分区边界（更大的范围）
   * - rowInFrame检查的是框架边界（分区内的子集）
   * - 行如果在框架内，一定在分区内
   * - 行如果在分区内，不一定在框架内
   * 
   * 【实现要点】
   * 1. 需要知道分区的起始和结束索引
   * 2. 表达式应该能够高效地执行边界检查
   * 3. 需要考虑空分区的情况
   */
  Expression rowInPartition(Expression rowIndex);

  /**
   * 获取指定绝对行位置的数据转换器
   * 
   * 【方法作用】
   * 为指定索引的行创建一个RexToLixTranslator对象，用于将Calcite的表达式
   * 转换为LINQ4J表达式。这是访问窗口内其他行数据的关键方法。
   * 
   * 【RexToLixTranslator概念】
   * RexToLixTranslator是Calcite中用于表达式转换的工具类：
   * - Rex：Calcite的关系表达式（Relational Expression）
   * - Lix：LINQ4J表达式（LINQ for Java）
   * - Translator：转换器，负责将Rex表达式转换为可执行的Java代码
   * 
   * 【转换过程】
   * 1. Calcite使用RexNode表示SQL表达式（如col1 + col2）
   * 2. 在代码生成阶段，需要将RexNode转换为Java表达式
   * 3. RexToLixTranslator负责这个转换过程
   * 4. 生成的表达式可以直接在生成的Java代码中使用
   * 
   * 【参数说明】
   * @param rowIndex 行的绝对索引
   *                 这是一个表达式对象，代表要访问的行位置
   *                 索引是相对于整个分区的绝对位置
   * 
   * 【返回值说明】
   * @return 请求行的转换器，用于访问该行的列数据
   *         返回的转换器配置为访问指定行的数据
   *         可以通过转换器将Rex表达式转换为访问该行数据的LINQ4J表达式
   * 
   * 【使用场景】
   * 1. 在计算窗口聚合时，访问窗口内其他行的列值
   * 2. 在实现LEAD/LAG函数时，访问前驱或后继行的数据
   * 3. 在实现FIRST_VALUE/LAST_VALUE时，访问框架内第一行或最后一行的数据
   * 
   * 【使用示例】
   * 假设要计算SUM(col1) OVER (ORDER BY col2 ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)：
   * 1. 获取当前行的转换器：rowTranslator(currentIndex)
   * 2. 获取前一行的转换器：rowTranslator(currentIndex - 1)
   * 3. 使用转换器访问col1列的值
   * 4. 将两行的col1值相加
   * 
   * 【实现要点】
   * 1. 转换器需要知道行的索引，以便访问正确的行数据
   * 2. 转换器需要知道列的类型和位置
   * 3. 转换器应该能够处理不同类型的表达式
   * 4. 转换器的性能影响窗口函数的执行效率
   */
  RexToLixTranslator rowTranslator(Expression rowIndex);

  /**
   * 根据当前窗口的排序规则比较两行
   * 
   * 【方法作用】
   * 按照窗口的ORDER BY子句定义的排序规则，比较两个绝对索引位置的行。
   * 这是实现需要排序的窗口函数（如RANK、DENSE_RANK、ROW_NUMBER）的关键方法。
   * 
   * 【排序概念】
   * 窗口的ORDER BY子句定义了分区内行的顺序：
   * 例如：ORDER BY salary DESC, hire_date ASC
   * 这个顺序决定了：
   * - ROW_NUMBER的编号顺序
   * - RANK和DENSE_RANK的计算
   * - LEAD/LAG引用的行
   * 
   * 【比较规则】
   * 比较遵循Comparable#compareTo的约定：
   * - 返回负数：a < b（a排在b前面）
   * - 返回0：a == b（a和b排序键相同）
   * - 返回正数：a > b（a排在b后面）
   * 
   * 【参数说明】
   * @param a 第一行的绝对索引
   *            这是一个表达式对象，代表第一行的位置
   * 
   * @param b 第二行的绝对索引
   *            这是一个表达式对象，代表第二行的位置
   * 
   * 【返回值说明】
   * @return 比较结果，返回一个表达式对象
   *         表达式的值遵循Comparable#compareTo的约定
   *         返回值类型通常是int
   * 
   * 【使用场景】
   * 1. 实现RANK函数：找出有多少行的排序键小于当前行
   * 2. 实现DENSE_RANK函数：计算不同的排序键值
   * 3. 实现ROW_NUMBER函数：确定行的顺序
   * 4. 实现PERCENT_RANK和CUME_DIST函数
   * 5. 在自定义聚合函数中，需要按顺序访问行
   * 
   * 【使用示例】
   * 假设窗口定义为ORDER BY salary DESC：
   * - compareRows(0, 1) 返回负数（第0行的salary大于第1行）
   * - compareRows(0, 0) 返回 0（同一行）
   * - compareRows(1, 0) 返回正数（第1行的salary小于第0行）
   * 
   * 如果有多列排序（ORDER BY salary DESC, hire_date ASC）：
   * - 先比较salary，如果salary不同，返回比较结果
   * - 如果salary相同，再比较hire_date，返回比较结果
   * 
   * 【实现要点】
   * 1. 需要考虑ORDER BY中的所有排序列
   * 2. 需要处理ASC和DESC两种排序方向
   * 3. 需要处理NULL值在排序中的位置（NULLS FIRST/LAST）
   * 4. 比较操作应该高效，因为它可能被频繁调用
   * 5. 对于多列排序，需要按照优先级逐列比较
   */
  Expression compareRows(Expression a, Expression b);
}
