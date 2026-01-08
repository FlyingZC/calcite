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
package org.apache.calcite.adapter.enumerable; // 包声明:位于org.apache.calcite.adapter.enumerable包下,这是Calcite框架中可枚举适配器相关的包,专门用于实现可枚举的查询执行

import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类:表示Linq4j中的表达式树节点,用于构建Java代码表达式,Linq4j是Calcite用于生成可执行Java代码的库

/**
 * Information for a call to // 类注释:提供调用信息的上下文接口
 * {@link AggImplementor#implementAdd(AggContext, AggAddContext)}. // 用于AggImplementor接口的implementAdd方法调用,该方法负责实现聚合函数的累加逻辑
 *
 * <p>{@link WinAggAddContext} is used when implementing windowed aggregate. // WinAggAddContext用于实现窗口聚合(Window Aggregate)函数时,窗口聚合是SQL中的OVER子句功能
 * Typically, the aggregation implementation will use {@link #arguments()} // 通常,窗口聚合函数的实现会使用arguments()方法
 * or {@link #rexArguments()} to update aggregate value. // 或者使用rexArguments()方法来更新聚合值,这两个方法提供了聚合函数的参数
 *
 * @see AggAddContext // 参见AggAddContext接口:这是普通聚合函数的累加上下文,WinAggAddContext是其针对窗口聚合的扩展
 */
public interface WinAggAddContext extends AggAddContext, WinAggResultContext { // 定义接口WinAggAddContext,继承自AggAddContext和WinAggResultContext接口,表示窗口聚合函数累加操作的上下文信息
  /**
   * Returns current position inside for-loop of window aggregate. // 方法注释:返回窗口聚合函数内部for循环的当前位置
   * Note, the position is relative to {@link WinAggFrameContext#startIndex()}. // 注意:这个位置是相对于窗口帧起始位置(startIndex)的相对位置
   * This is NOT current row as in "rows between current row". // 这不是SQL语句中的"rows between current row"所指的当前行
   * If you need to know the relative index of the current row in the partition, // 如果你需要知道当前行在分区(Partition)中的相对索引
   * use {@link WinAggFrameContext#index()}. // 请使用WinAggFrameContext接口的index()方法
   *
   * @return current position inside for-loop of window aggregate. // 返回值:返回窗口聚合函数内部for循环的当前位置的Linq4j表达式
   * @see WinAggFrameContext#index() // 参见index()方法:获取当前行在分区中的索引
   * @see WinAggFrameContext#startIndex() // 参见startIndex()方法:获取窗口帧的起始位置
   */
  Expression currentPosition(); // 方法声明:返回当前窗口循环位置的Linq4j表达式,这个表达式可以用于生成Java代码中访问当前循环位置的代码
}
