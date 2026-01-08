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
// Apache许可证声明，允许在遵守Apache 2.0许可证的前提下使用和修改此代码

package org.apache.calcite.adapter.enumerable; // 定义包名，该接口位于org.apache.calcite.adapter.enumerable包中，属于Calcite的可枚举适配器模块

/**
 * Information for a call to
 * {@link AggImplementor#implementReset(AggContext, AggResetContext)}.
 * 这是一个窗口聚合重置上下文接口，为调用AggImplementor的implementReset方法提供必要的信息
 *
 * <p>The {@link AggResetContext} provides access to the accumulator variables
 * that should be reset.
 * AggResetContext提供了对需要重置的累加器变量的访问权限
 * 
 * <p>Note: the very first reset of windowed aggregates is performed with null
 * knowledge of indices and row count in the partition.
 * 注意：窗口聚合的第一次重置是在对分区中的索引和行数一无所知的情况下执行的
 * 
 * <p>In other words, the implementation should treat indices and partition row
 * count as a hint to pre-size the collections.
 * 换句话说，实现应该将索引和分区行数视为预分配集合大小的提示
 *
 * 类的作用详解：
 * 1. WinAggResetContext是Calcite窗口函数聚合实现框架中的核心接口
 * 2. 它专门用于窗口函数（Window Function）聚合的重置阶段
 * 3. 该接口继承自两个重要接口：AggResetContext和WinAggFrameContext
 * 4. AggResetContext提供了聚合重置的通用功能（如访问累加器变量）
 * 5. WinAggFrameContext提供了窗口帧的元数据信息（如分区索引、行数等）
 * 6. 组合这两个接口，使得窗口聚合的重置操作能够同时访问累加器和窗口帧信息
 * 
 * 在Calcite窗口聚合流程中的位置：
 * - 窗口函数（如SUM、AVG、ROW_NUMBER等）需要对窗口帧内的数据进行聚合计算
 * - 当滑动窗口移动或处理新的分区时，需要重置累加器状态
 * - WinAggResetContext提供了重置阶段所需的所有信息
 * - AggImplementor使用此上下文来实现具体的重置逻辑
 * 
 * 与普通聚合的区别：
 * - 普通聚合：每个分组独立计算，重置时只需重置累加器
 * - 窗口聚合：需要考虑窗口帧的边界、分区信息、当前行位置等
 * - WinAggResetContext额外提供了窗口帧的索引和行数信息
 * 
 * 重要特性：
 * - 第一次重置时，索引和行数信息可能不准确，只能作为预分配大小的提示
 * - 实现者应该能够处理这种不确定性
 * - 后续的重置操作会提供准确的索引和行数信息
 * 
 * 使用场景：
 * - 实现窗口函数的聚合逻辑时
 * - 在窗口帧移动时重置累加器
 * - 在处理新的分区时初始化聚合状态
 * 
 * 实现注意事项：
 * - 必须实现继承自AggResetContext的accumulator()方法
 * - 必须实现继承自WinAggFrameContext的所有方法（index、startIndex、endIndex等）
 * - 第一次重置时不要依赖索引和行数的准确性
 * - 可以使用这些信息来预分配数据结构的大小
 * 
 * 示例场景：
 * - SQL: SUM(salary) OVER (PARTITION BY dept_id ORDER BY emp_id ROWS BETWEEN 2 PRECEDING AND CURRENT ROW)
 * - 当窗口移动时，需要重置累加器并重新计算窗口内的SUM
 * - WinAggResetContext提供了当前行的索引、窗口帧的起始和结束索引
 * - 实现者可以使用这些信息来高效地更新聚合结果
 */
public interface WinAggResetContext // 定义WinAggResetContext接口，这是窗口聚合重置上下文的接口
    extends AggResetContext, WinAggFrameContext { // 继承AggResetContext接口，获得访问累加器变量的能力；继承WinAggFrameContext接口，获得访问窗口帧信息的能力
} // 接口定义结束，这是一个标记接口，通过继承组合了两个父接口的功能，为窗口聚合的重置操作提供完整的上下文信息
