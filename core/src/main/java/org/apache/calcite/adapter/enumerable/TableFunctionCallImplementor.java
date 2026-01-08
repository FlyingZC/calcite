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
// 声明包名，表示这个接口属于 org.apache.calcite.adapter.enumerable 包
// 这个包包含了 Calcite 框架中用于将关系代数表达式转换为可枚举代码（主要是 Java 代码）的核心组件
package org.apache.calcite.adapter.enumerable;

// 导入 Experimental 注解，用于标记实验性的 API，表示这个接口可能在未来版本中发生变化
import org.apache.calcite.linq4j.function.Experimental;
// 导入 Expression 类，这是 LINQ4J 表达式树的节点类，用于表示 Java 表达式
// Expression 是一个抽象语法树（AST）的节点，可以表示方法调用、字段访问、常量等各种 Java 表达式
import org.apache.calcite.linq4j.tree.Expression;
// 导入 RexCall 类，表示关系表达式（Relational Expression）中的函数调用
// RexCall 是 Calcite 内部表示 SQL 函数调用的数据结构，包含函数名和参数列表
import org.apache.calcite.rex.RexCall;

/**
 * Implements a table-valued function call.
 * 实现表值函数（Table-Valued Function）调用的接口
 * 
 * 表值函数是返回一个表（即多行多列数据）而不是单个值的函数
 * 例如：UNNEST(array) 函数将数组展开为多行数据
 * 
 * 这个接口定义了如何将关系表达式中的表值函数调用转换为可执行的 LINQ4J 表达式
 * 这是 Calcite 查询优化和执行过程中的关键环节，它将逻辑表达式转换为物理执行代码
 */
// 使用 @Experimental 注解标记这个接口为实验性功能，表示 API 可能不稳定
@Experimental
// 定义一个名为 TableFunctionCallImplementor 的公共接口
// 接口名称直译：表函数调用实现器
// 这个接口的实现类负责将表值函数调用转换为具体的可执行表达式
public interface TableFunctionCallImplementor {
  /**
   * Implements a table-valued function call.
   * 实现表值函数调用的方法
   * 
   * 这是接口的核心方法，负责将关系表达式中的表值函数调用转换为 LINQ4J 表达式
   * 转换后的表达式可以被编译成 Java 字节码并执行
   * 
   * @param translator Translator for the call.
   *        translator：调用转换器，用于将关系表达式转换为 LINQ4J 表达式
   *        RexToLixTranslator 是一个翻译器，负责将 Calcite 的关系表达式（Rex）转换为 LINQ4J 表达式（Lix）
   *        它维护了转换上下文，包括变量映射、类型信息等
   * 
   * @param inputEnumerable Table parameter of the call.
   *        inputEnumerable：表值函数的表参数表达式
   *        如果表值函数接受一个表作为输入参数（例如：TABLE(myTable)），这个参数表示该表的枚举表达式
   *        Expression 类型表示一个可以产生可枚举数据源的表达式（如 Enumerable 对象）
   * 
   * @param call Call that should be implemented.
   *        call：需要实现的表值函数调用
   *        RexCall 对象包含了函数调用的完整信息，包括：
   *        - 函数名称（如 "UNNEST"）
   *        - 函数参数列表（每个参数都是一个 RexNode）
   *        - 函数的返回类型
   *        - 函数的操作符类型
   * 
   * @param inputPhysType Physical type of the table parameter.
   *        inputPhysType：表参数的物理类型
   *        PhysType 表示数据在物理执行层面的类型信息，包括：
   *        - Java 类类型（如 Row.class）
   *        - 字段列表及其类型
   *        - 字段的访问方式（通过索引、名称或getter方法）
   *        - 格式化信息（用于生成代码）
   * 
   * @param outputPhysType Physical type of the call.
   *        outputPhysType：表值函数调用的输出物理类型
   *        表示表值函数返回的数据的物理类型信息
   *        与 inputPhysType 类似，包含返回表的行类型、字段信息等
   *        例如：UNNEST(ARRAY[1,2,3]) 返回的表可能有一个 INTEGER 类型的列
   * 
   * @return Expression that implements the call.
   *        返回实现该调用的表达式
   *        返回的 Expression 是一个 LINQ4J 表达式树，表示如何执行这个表值函数调用
   *        这个表达式可以被编译成 Java 代码并执行，产生一个可枚举的数据源（Enumerable）
   *        例如：对于 UNNEST 调用，可能返回一个类似 "Arrays.asList(1, 2, 3)" 的表达式
   */
  // 定义 implement 方法，该方法接受多个参数并返回一个 Expression 对象
  // 这个方法没有默认实现，必须由实现类提供具体的实现逻辑
  Expression implement(
      // 第一个参数：RexToLixTranslator 类型的 translator
      // 这是关系表达式到 LINQ4J 表达式的转换器，用于辅助转换过程
      RexToLixTranslator translator,
      // 第二个参数：Expression 类型的 inputEnumerable
      // 表示表值函数的输入表参数的表达式（如果有的话）
      Expression inputEnumerable,
      // 第三个参数：RexCall 类型的 call
      // 表示需要实现的表值函数调用对象
      RexCall call,
      // 第四个参数：PhysType 类型的 inputPhysType
      // 表示输入表的物理类型信息
      PhysType inputPhysType,
      // 第五个参数：PhysType 类型的 outputPhysType
      // 表示输出表的物理类型信息
      PhysType outputPhysType);
}
