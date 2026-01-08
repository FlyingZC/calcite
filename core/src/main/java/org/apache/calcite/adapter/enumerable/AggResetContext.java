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

import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，用于表示表达式树，这是LINQ4J库中的核心类，用于构建代码表达式

import java.util.List; // 导入List接口，用于存储累加器变量列表

/**
 * Information for a call to
 * {@link AggImplementor#implementReset(AggContext, AggResetContext)}.
 * 这是一个上下文接口，为调用AggImplementor的implementReset方法提供必要的信息
 *
 * <p>{@link AggResetContext} provides access to the accumulator variables
 * that should be reset.
 * AggResetContext提供了对需要重置的累加器变量的访问权限
 * 
 * 类的作用详解：
 * 1. AggResetContext是Calcite聚合函数实现框架中的一个核心接口
 * 2. 它用于在聚合操作的重置阶段提供上下文信息
 * 3. 当聚合函数需要重置累加器时（例如在处理新的分组时），会使用这个接口
 * 4. 该接口继承自NestedBlockBuilder，说明它具有构建嵌套代码块的能力
 * 5. 主要功能是提供需要重置的累加器变量列表，以便实现器可以生成重置这些变量的代码
 * 
 * 在Calcite聚合流程中的位置：
 * - 聚合函数实现时，需要维护累加器状态
 * - 在处理新分组时，需要重置累加器到初始状态
 * - AggResetContext提供了重置阶段所需的所有信息
 * - AggImplementor使用此上下文来实现具体的重置逻辑
 */
public interface AggResetContext extends NestedBlockBuilder { // 定义AggResetContext接口，继承自NestedBlockBuilder，使其具备构建嵌套代码块的能力
  /**
   * Returns accumulator variables that should be reset.
   * 返回需要重置的累加器变量列表
   * There MUST be an assignment even if you just assign the default value.
   * 必须进行赋值操作，即使只是赋默认值
   *
   * @return accumulator variables that should be reset or empty list when no
   *   accumulator variables are used by the aggregate implementation.
   * 返回需要重置的累加器变量列表，如果聚合实现不使用累加器变量则返回空列表
   *
   * @see AggImplementor#getStateType(org.apache.calcite.adapter.enumerable.AggContext)
   * 参见AggImplementor的getStateType方法，该方法定义了累加器的状态类型
   * 
   * 方法作用详解：
   * 1. 此方法是接口的核心方法，用于获取所有需要重置的累加器变量
   * 2. 返回的Expression对象表示这些累加器变量的表达式形式
   * 3. 这些表达式可以用于生成代码来重置累加器的值
   * 4. 在聚合函数实现中，累加器用于存储中间计算结果（如SUM、COUNT等）
   * 5. 当开始处理新的分组时，必须重置这些累加器到初始状态
   * 6. 即使某些聚合函数不需要累加器，也必须返回空列表而不是null
   * 
   * 使用场景：
   * - 在GROUP BY操作中，每当开始处理新的分组键时调用
   - 在聚合函数的初始化阶段使用
   - 用于生成分组切换时的重置逻辑代码
   * 
   * 注意事项：
   * - 必须确保每个累加器都被正确赋值
   - 即使是简单的默认值赋值也是必须的
   * - 返回的列表不能为null，应该是空列表
   * - Expression对象必须指向有效的变量引用
   */
  List<Expression> accumulator(); // 声明accumulator方法，返回需要重置的累加器变量表达式列表，这些表达式用于生成重置代码
}
