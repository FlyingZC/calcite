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
package org.apache.calcite.adapter.enumerable; // 声明接口所在的包路径，org.apache.calcite.adapter.enumerable是Calcite中处理可枚举适配器的核心包

import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，这是LINQ4J库中的表达式类，用于表示代码表达式树
import org.apache.calcite.rex.RexCall; // 导入RexCall类，这是Calcite中用于表示关系表达式调用的核心类

import java.util.List; // 导入List接口，用于存储操作数列表

/**
 * 这是一个简化版的 {@link org.apache.calcite.adapter.enumerable.CallImplementor} 接口
 * 
 * 类作用说明：
 * NotNullImplementor是Calcite可枚举适配器中的一个核心接口，它的主要作用是实现函数调用的代码生成。
 * 与CallImplementor不同，这个接口假设所有操作数都不为null，因此不需要处理null语义。
 * 
 * 在Calcite的代码生成过程中，当需要将RexNode（关系表达式树）转换为可执行的Java代码时，
 * 会使用各种Implementor接口来生成对应的代码表达式。NotNullImplementor专门用于那些
 * 不需要处理null值的场景，这样可以简化代码生成逻辑，提高执行效率。
 * 
 * 使用场景：
 * 1. 当调用者已经确保所有操作数都不为null时，可以使用NotNullImplementor来生成更简洁的代码
 * 2. 在RexImpTable中注册函数实现时，可以为那些不支持null值的函数提供NotNullImplementor
 * 3. 适用于那些在SQL标准中定义为NOT NULL的列或表达式
 * 
 * 核心概念：
 * - RexNode：Calcite中的关系表达式节点，表示SQL查询的逻辑结构
 * - Expression：LINQ4J中的表达式树节点，表示Java代码的抽象语法树
 * - Translator：将RexNode转换为Expression的转换器
 * - Null semantics：处理null值的语义，包括null的传播、比较等规则
 *
 * @see org.apache.calcite.adapter.enumerable.RexImpTable // RexImpTable是存储各种函数实现的表，NotNullImplementor在其中被注册和使用
 * @see org.apache.calcite.adapter.enumerable.CallImplementor // CallImplementor是更完整的实现器接口，支持null语义处理
 */
// 定义NotNullImplementor接口，这是一个函数式接口，用于实现不考虑null语义的函数调用代码生成
public interface NotNullImplementor {
  /**
   * 实现一个函数调用的代码生成
   * 
   * 方法作用说明：
   * 这个方法是NotNullImplementor接口的核心方法，用于将一个RexCall（关系表达式调用）
   * 转换为对应的Expression（Java表达式）。与CallImplementor不同，这个方法假设
   * 所有操作数都不为null，因此不需要生成null检查代码。
   * 
   * 实现原理：
   * 1. 接收一个RexToLixTranslator转换器，用于辅助生成代码
   * 2. 接收一个RexCall对象，表示要实现的函数调用
   * 3. 接收已转换的操作数列表，这些操作数已经从RexNode转换为Expression
   * 4. 根据函数类型和操作数，生成对应的Java表达式
   * 5. 返回生成的Expression对象，该对象可以被进一步编译为可执行代码
   * 
   * 参数详解：
   * @param translator // RexToLixTranslator类型的转换器对象，用于将RexNode转换为LINQ4J表达式
   *                   // 这个转换器维护了转换上下文，包括变量绑定、类型映射等信息
   *                   // 是代码生成过程中的核心工具类
   * @param call       // RexCall类型的对象，表示要实现的函数调用
   *                   // RexCall包含函数类型、操作数列表等信息
   *                   // 例如：ADD(a, b)、SUB(c, d)等
   * @param translatedOperands // List<Expression>类型的操作数列表
   *                            // 这些操作数已经从RexNode转换为Expression
   *                            // 例如：如果call是ADD(a, b)，则translatedOperands包含a和b对应的Expression
   *                            // 调用者需要确保这些操作数都不为null
   * 
   * 返回值详解：
   * @return // Expression对象，表示生成的Java表达式
   *         // 这个表达式可以直接用于生成Java代码
   *         // 例如：对于ADD(a, b)，可能返回类似Expressions.add(aExpr, bExpr)的表达式
   *         // 返回的表达式不包含null检查逻辑
   * 
   * 使用示例：
   * 假设要实现一个简单的加法函数：
   * 1. call = ADD($0, $1)  // 表示对第0列和第1列进行加法
   * 2. translatedOperands = [Expressions.parameter(Integer.class, "a"), 
   *                           Expressions.parameter(Integer.class, "b")]
   * 3. 返回值 = Expressions.add(translatedOperands.get(0), translatedOperands.get(1))
   * 4. 最终生成的代码类似：a + b
   * 
   * 注意事项：
   * 1. 此方法不处理null值，调用者必须确保所有操作数都不为null
   * 2. 生成的表达式应该与原始RexCall的语义一致
   * 3. 需要考虑操作数的类型，确保类型兼容性
   * 4. 对于复杂的函数，可能需要生成多个表达式组合
   */
  // 定义implement方法，用于实现函数调用的代码生成，接收转换器、调用对象和已转换的操作数列表
  Expression implement( // 返回Expression对象，表示生成的Java表达式
      RexToLixTranslator translator, // 参数1：RexToLixTranslator转换器，用于辅助代码生成
      RexCall call, // 参数2：RexCall对象，表示要实现的函数调用
      List<Expression> translatedOperands); // 参数3：已转换的操作数列表，假设都不为null
}
