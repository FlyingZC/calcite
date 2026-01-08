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
// Apache许可证声明，说明代码归属和使用权限
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，这是Calcite框架中用于可枚举适配器的包

import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类，用于构建代码块，是LINQ4J库中的核心类

import java.util.ArrayList; // 导入ArrayList类，用于实现动态数组
import java.util.List; // 导入List接口，用于定义列表类型

/**
 * Allows to build nested code blocks with tracking of current context.
 * 允许构建嵌套的代码块，并跟踪当前的上下文环境
 * 这个类是NestedBlockBuilder接口的实现，主要用于在代码生成过程中管理嵌套的代码块层级
 * 核心功能是维护一个BlockBuilder的栈结构，支持进入和退出不同的代码块作用域
 *
 * @see org.apache.calcite.adapter.enumerable.StrictAggImplementor#implementAdd(AggContext, AggAddContext)
 * 参见StrictAggImplementor的implementAdd方法，该方法在聚合函数实现时使用此构建器来管理代码块
 */
public class NestedBlockBuilderImpl implements NestedBlockBuilder { // 定义NestedBlockBuilderImpl类，实现NestedBlockBuilder接口
  private final List<BlockBuilder> blocks = new ArrayList<>(); // 定义一个BlockBuilder列表，用于维护嵌套的代码块栈，每个BlockBuilder代表一个代码块上下文，使用ArrayList实现以便快速访问和修改

  /**
   * Constructs nested block builders starting of a given code block.
   * 构造函数：基于给定的代码块初始化嵌套代码块构建器
   * 这个构造方法会创建一个新的NestedBlockBuilderImpl实例，并将给定的BlockBuilder作为初始的根代码块
   *
   * @param block root code block 根代码块，作为嵌套结构的起点
   */
  @SuppressWarnings("method.invocation.invalid") // 抑制编译器警告，因为这里调用nestBlock方法时，对象可能还未完全初始化
  public NestedBlockBuilderImpl(BlockBuilder block) { // 构造方法，接收一个BlockBuilder参数作为根代码块
    nestBlock(block); // 调用nestBlock方法，将给定的block添加到blocks列表中，作为初始的当前代码块
  }

  /**
   * Starts nested code block. The resulting block can optimize expressions
   * and reuse already calculated values from the parent blocks.
   * 启动一个新的嵌套代码块，生成的代码块可以优化表达式并重用父代码块中已计算的值
   * 这个方法会创建一个新的BlockBuilder实例，并将其父块设置为当前代码块，从而形成嵌套结构
   * 新创建的代码块可以访问和复用父块中的变量和表达式，这是代码优化的关键机制
   *
   * @return new code block that can optimize expressions and reuse already
   * calculated values from the parent blocks.
   * 返回新的代码块，该代码块可以优化表达式并重用父代码块中已计算的值
   */
  @Override public final BlockBuilder nestBlock() { // 重写接口方法，创建并进入一个新的嵌套代码块
    BlockBuilder block = new BlockBuilder(true, currentBlock()); // 创建新的BlockBuilder实例，第一个参数true表示启用优化，第二个参数是当前块作为父块，这样新块可以继承父块的上下文
    nestBlock(block); // 调用nestBlock方法，将新创建的block添加到blocks列表中，使其成为当前代码块
    return block; // 返回新创建的代码块，供调用者使用
  }

  /**
   * Uses given block as the new code context.
   * 使用给定的代码块作为新的代码上下文
   * 这个方法将指定的BlockBuilder压入栈中，使其成为当前的活跃代码块
   * 调用此方法后，所有的代码生成操作都会在这个新的上下文中进行
   * The current block will be restored after {@link #exitBlock()} call.
   * 当前代码块将在调用exitBlock()方法后恢复，即退出当前块后回到之前的上下文
   *
   * @param block new code block 新的代码块，将被设置为当前上下文
   * @see #exitBlock() 参见exitBlock方法，用于退出当前代码块
   */
  @Override public final void nestBlock(BlockBuilder block) { // 重写接口方法，将给定的代码块设置为当前上下文
    blocks.add(block); // 将给定的block添加到blocks列表末尾，使其成为最新的当前代码块（栈的push操作）
  }

  /**
   * Returns the current code block.
   * 返回当前活跃的代码块
   * 这个方法用于获取当前正在使用的代码块，以便在其中添加代码或表达式
   * 返回的是blocks列表中的最后一个元素，这是栈顶元素
   *
   * @return the current BlockBuilder instance 当前的BlockBuilder实例
   */
  @Override public final BlockBuilder currentBlock() { // 重写接口方法，获取当前代码块
    return blocks.get(blocks.size() - 1); // 返回blocks列表中的最后一个元素，即栈顶的代码块，这是当前活跃的上下文
  }

  /**
   * Leaves the current code block.
   * 退出当前的代码块，返回到上一层代码块
   * 这个方法会从栈中移除当前的代码块，使前一个代码块成为当前活跃的代码块
   * 这在完成某个代码块的代码生成后调用，以便返回到父代码块继续生成代码
   *
   * @see #nestBlock() 参见nestBlock方法，用于进入新的代码块
   */
  @Override public final void exitBlock() { // 重写接口方法，退出当前代码块
    blocks.remove(blocks.size() - 1); // 移除blocks列表中的最后一个元素，即退出当前的代码块（栈的pop操作），恢复到上一个代码块上下文
  }
} // 类定义结束
