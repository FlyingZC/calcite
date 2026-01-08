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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.adapter.enumerable;  // 定义包名，该接口位于calcite框架的枚举适配器包中，用于构建可枚举的代码块

import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入BlockBuilder类，用于构建代码块的构建器，是LINQ4J表达式树的一部分

/**
 * Allows to build nested code blocks with tracking of current context.
 * 允许构建嵌套的代码块，并跟踪当前的上下文环境
 * 这个接口主要用于在代码生成过程中管理嵌套的代码块结构
 * 它提供了一种机制来创建、切换和退出代码块，同时保持对父级代码块的引用
 * 这样可以在嵌套的代码块中重用父级代码块中已经计算过的值，从而优化表达式
 *
 * @see org.apache.calcite.adapter.enumerable.StrictAggImplementor#implementAdd(AggContext, AggAddContext)
 * 参见StrictAggImplementor类的implementAdd方法，该方法使用了这个接口来构建聚合操作的代码块
 */
public interface NestedBlockBuilder {  // 定义一个公共接口，名为NestedBlockBuilder，用于构建嵌套的代码块
  /**
   * Starts nested code block. The resulting block can optimize expressions
   * and reuse already calculated values from the parent blocks.
   * 开始一个新的嵌套代码块，返回的代码块可以优化表达式并重用父级代码块中已经计算过的值
   * 这个方法会创建一个新的代码块，并将其设置为当前代码块
   * 新创建的代码块会继承父级代码块的上下文，可以访问父级代码块中定义的变量和表达式
   * 这样可以避免重复计算相同的表达式，提高代码生成的效率
   *
   * @return new code block that can optimize expressions and reuse already
   * calculated values from the parent blocks.
   * 返回一个新的代码块构建器，该构建器可以优化表达式并重用父级代码块中已经计算过的值
   * 返回的BlockBuilder实例可以用于在当前嵌套代码块中添加语句和表达式
   */
  BlockBuilder nestBlock();  // 声明一个方法，用于开始一个新的嵌套代码块，返回BlockBuilder类型

  /**
   * Uses given block as the new code context.
   * The current block will be restored after {@link #exitBlock()} call.
   * 使用给定的代码块作为新的代码上下文环境
   * 这个方法会将指定的代码块设置为当前代码块，并将之前的代码块保存在栈中
   * 这样可以在需要时通过调用exitBlock()方法恢复到之前的代码块
   * 这种机制允许在不同的代码块之间切换，实现代码块的嵌套和层次化管理
   *
   * @param block new code block
   * 参数block表示要设置为当前代码上下文的新代码块
   * 这个参数是一个BlockBuilder实例，包含了要使用的代码块的所有信息
   * @see #exitBlock()
   * 参见exitBlock()方法，该方法用于退出当前的代码块并恢复到之前的代码块
   */
  void nestBlock(BlockBuilder block);  // 声明一个重载方法，使用给定的BlockBuilder作为新的代码上下文

  /**
   * Returns the current code block.
   * 返回当前的代码块
   * 这个方法用于获取当前正在使用的代码块构建器
   * 可以通过这个方法来访问当前代码块的所有功能，如添加语句、表达式等
   * 这个方法在需要知道当前代码上下文时非常有用
   *
   * @return the current code block builder
   * 返回当前的BlockBuilder实例，表示当前正在使用的代码块
   */
  BlockBuilder currentBlock();  // 声明一个方法，用于返回当前的代码块构建器

  /**
   * Leaves the current code block.
   * 退出当前的代码块
   * 这个方法会结束当前代码块的使用，并恢复到之前的代码块（如果存在）
   * 它会清理当前代码块的上下文，并将栈顶的代码块设置为当前代码块
   * 这个方法通常与nestBlock()方法配对使用，用于管理代码块的嵌套层次
   *
   * @see #nestBlock()
   * 参见nestBlock()方法，该方法用于开始一个新的嵌套代码块
   */
  void exitBlock();  // 声明一个方法，用于退出当前的代码块
}  // 接口定义结束
