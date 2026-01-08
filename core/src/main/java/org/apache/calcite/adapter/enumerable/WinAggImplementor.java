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
// Apache许可证声明，版权归属Apache软件基金会

package org.apache.calcite.adapter.enumerable; // 声明包名：位于Calcite的可枚举适配器包中，该包提供了将关系代数转换为可枚举代码的功能

/**
 * Implements a windowed aggregate function by generating expressions to
 * initialize, add to, and get a result from, an accumulator.
 * Windowed aggregate is more powerful than regular aggregate since it can
 * access rows in the current partition by row indices.
 * Regular aggregate can be used to implement windowed aggregate.
 *
 * <p>This interface does not define new methods: window-specific
 * sub-interfaces are passed when implementing window aggregate.
 *
 * @see org.apache.calcite.adapter.enumerable.StrictWinAggImplementor
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.FirstLastValueImplementor
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.RankImplementor
 * @see org.apache.calcite.adapter.enumerable.RexImpTable.RowNumberImplementor
 */
// 类的JavaDoc文档注释，详细说明该接口的作用：
// 1. 通过生成表达式来实现窗口聚合函数，这些表达式用于初始化累加器、向累加器添加数据、从累加器获取结果
// 2. 窗口聚合比普通聚合更强大，因为它可以通过行索引访问当前分区中的行
// 3. 普通聚合可以用来实现窗口聚合
// 4. 该接口没有定义新的方法：在实现窗口聚合时，会传递窗口特定的子接口
// 5. 提供了相关的参考链接，指向具体的窗口聚合实现类

public interface WinAggImplementor extends AggImplementor { // 定义WinAggImplementor接口，继承自AggImplementor接口，表示这是一个窗口聚合实现器接口
  /**
   * Allows to access rows in window partition relative to first/last and
   * current row.
   */
  // 枚举类型的JavaDoc文档注释，说明该枚举的作用：
  // 允许访问窗口分区中相对于第一行/最后一行和当前行的行

  enum SeekType { // 定义SeekType枚举类型，用于指定在窗口中定位行的方式
    /**
     * Start of window.
     *
     * @see WinAggFrameContext#startIndex()
     */
    // START枚举常量的JavaDoc：表示窗口的起始位置
    // 引用WinAggFrameContext接口的startIndex()方法，该方法返回窗口的起始索引

    START, // 枚举值START：表示窗口的起始位置，对应窗口框架的第一个行位置

    /**
     * Row position in the frame.
     *
     * @see WinAggFrameContext#index()
     */
    // SET枚举常量的JavaDoc：表示框架中的行位置
    // 引用WinAggFrameContext接口的index()方法，该方法返回当前框架中的行索引

    SET, // 枚举值SET：表示框架中指定的行位置，用于定位到特定的行

    /**
     * The index of row that is aggregated.
     * Valid only in {@link WinAggAddContext}.
     *
     * @see WinAggAddContext#currentPosition()
     */
    // AGG_INDEX枚举常量的JavaDoc：表示被聚合的行的索引
    // 该值仅在WinAggAddContext上下文中有效
    // 引用WinAggAddContext接口的currentPosition()方法，该方法返回当前正在聚合的行的位置

    AGG_INDEX, // 枚举值AGG_INDEX：表示当前正在被聚合处理的行的索引位置

    /**
     * End of window.
     *
     * @see WinAggFrameContext#endIndex()
     */
    // END枚举常量的JavaDoc：表示窗口的结束位置
    // 引用WinAggFrameContext接口的endIndex()方法，该方法返回窗口的结束索引

    END // 枚举值END：表示窗口的结束位置，对应窗口框架的最后一个行位置
  } // SeekType枚举定义结束

  boolean needCacheWhenFrameIntact(); // 定义抽象方法：判断当窗口框架保持不变时是否需要缓存
  // 返回值：true表示需要缓存，false表示不需要缓存
  // 该方法用于优化窗口聚合的性能，当窗口框架不变时，某些聚合函数可能需要缓存中间结果
  // 例如：RANK函数需要缓存所有行的排名信息，而SUM函数只需要维护一个累加值
} // WinAggImplementor接口定义结束
