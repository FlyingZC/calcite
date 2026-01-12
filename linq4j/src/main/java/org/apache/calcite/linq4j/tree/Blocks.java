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
// Apache许可证头，声明代码版权和使用许可
package org.apache.calcite.linq4j.tree; // 定义包名，这是linq4j树形结构包，包含表达式树相关的类

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于对象非空检查

/**
 * Helper methods concerning {@link BlockStatement}s.
 * 关于BlockStatement（块语句）的辅助方法类
 * Blocks类是一个工具类，提供了创建、转换和操作代码块的各种静态方法
 * 它主要用于在生成Java代码时处理代码块的结构和转换
 *
 * @see BlockBuilder // 参见BlockBuilder类，BlockBuilder是构建代码块的主要工具
 */
public final class Blocks { // 定义Blocks类，final表示不能被继承
  private Blocks() { // 私有构造函数，防止实例化，因为这是一个工具类
    throw new AssertionError("no blocks for you!"); // 抛出断言错误，阻止通过反射创建实例
  } // 私有构造函数结束，确保此类只能通过静态方法使用

  private static BlockStatement toFunctionBlock(Node body, boolean function) { // 私有静态方法，将Node转换为函数块语句，body是节点内容，function表示是否为函数上下文
    if (body instanceof BlockStatement) { // 如果body已经是BlockStatement类型（代码块）
      return (BlockStatement) body; // 直接返回该代码块，无需转换
    } // 类型检查结束
    Statement statement; // 声明Statement变量，用于存储转换后的语句
    if (body instanceof Statement) { // 如果body是Statement类型（语句）
      statement = (Statement) body; // 直接将body转换为Statement赋值
    } else if (body instanceof Expression) { // 如果body是Expression类型（表达式）
      if (((Expression) body).getType() == Void.TYPE && function) { // 检查表达式类型是否为void且在函数上下文中
        statement = Expressions.statement((Expression) body); // 将表达式转换为语句形式（表达式后加分号）
      } else { // 如果表达式有返回值或者不在函数上下文中
        statement = Expressions.return_(null, (Expression) body); // 将表达式包装成return语句返回该表达式
      } // 类型判断结束
    } else { // 如果body既不是Statement也不是Expression
      throw new AssertionError("block cannot contain node that is neither " // 抛出断言错误，说明节点类型不支持
          + "statement nor expression: " + body); // 错误消息中包含body的类型信息
    } // 类型检查结束
    return Expressions.block(statement); // 将语句包装成代码块并返回
  } // toFunctionBlock方法结束

  public static BlockStatement toFunctionBlock(Node body) { // 公共静态方法，将Node转换为函数块语句（function=true）
    return toFunctionBlock(body, true); // 调用私有方法，function参数设为true，表示函数上下文
  } // toFunctionBlock方法结束，用于函数体转换

  public static BlockStatement toBlock(Node body) { // 公共静态方法，将Node转换为普通块语句（function=false）
    return toFunctionBlock(body, false); // 调用私有方法，function参数设为false，表示非函数上下文
  } // toBlock方法结束，用于普通代码块转换

  /**
   * Prepends a statement to a block.
   * 在代码块前面添加一条语句
   * 这个方法用于在现有代码块之前插入新的语句，保持原有语句顺序不变
   */
  public static BlockStatement create(Statement statement, // 参数statement是要添加到前面的语句
      BlockStatement block) { // 参数block是原有的代码块
    return Expressions.block( // 创建新的代码块
        Expressions.list(statement).appendAll(block.statements)); // 先创建包含新语句的列表，然后追加原代码块的所有语句
  } // create方法结束，返回包含前置语句的新代码块

  /**
   * Converts a simple "{ return expr; }" block into "expr"; otherwise
   * throws.
   * 将简单的"{ return expr; }"代码块转换为表达式"expr"；否则抛出异常
   * 这个方法用于从只包含一个return语句的代码块中提取返回的表达式
   */
  public static Expression simple(BlockStatement block) { // 公共静态方法，从简单代码块中提取表达式
    if (block.statements.size() == 1) { // 检查代码块是否只包含一条语句
      Statement statement = block.statements.get(0); // 获取第一条语句
      if (statement instanceof GotoStatement) { // 检查该语句是否为GotoStatement（return语句在内部表示为GotoStatement）
        return requireNonNull(((GotoStatement) statement).expression); // 返回GotoStatement中的表达式，requireNonNull确保表达式不为null
      } // 类型检查结束
    } // 大小检查结束
    throw new AssertionError("not a simple block: " + block); // 如果不是简单代码块，抛出断言错误
  } // simple方法结束，返回提取出的表达式
} // Blocks类结束
