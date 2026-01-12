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
package org.apache.calcite.linq4j.tree; // 声明该接口所在的包路径，位于linq4j组件的tree子包中，该包主要包含表达式树相关的类和接口

import org.apache.calcite.linq4j.function.Function; // 导入Function类，用于定义函数式接口的泛型约束

/**
 * Represents a visitor or rewriter for expression trees. // 表达式访问者接口，用于定义访问或重写表达式树的行为模式，这是访问者设计模式在表达式树处理中的应用
 * 
 * ExpressionVisitor是LINQ4J框架中表达式树访问的抽象接口，它定义了访问表达式树的统一标准。
 * 表达式树在编译器、查询优化器、代码生成等领域广泛应用，通过访问者模式可以方便地遍历、
 * 分析、转换表达式树而无需修改表达式树本身的类结构。该接口允许不同的访问者实现类对表达式树
 * 执行不同的操作，如：表达式求值、类型检查、代码生成、优化转换等。
 * 
 * 主要应用场景：
 * 1. 表达式树的深度优先遍历
 * 2. 表达式树的重写和优化
 * 3. 表达式树的静态分析和验证
 * 4. 表达式树转换为可执行代码或SQL语句
 * 
 * 设计模式说明：
 * - 采用访问者模式，将数据结构与操作分离
 * - 表达式树的节点类型相对稳定，但操作可能频繁变化
 * - 通过实现该接口，可以灵活添加新的操作而不影响表达式树的结构
 * 
 * 与其他类的关系：
 * - FunctionExpression: 表达式树的根节点类型，包含委托函数
 * - Expression: 所有表达式节点的基类
 * - ExpressionWriter: 表达式树的写入器，用于输出表达式树的文本表示
 */
public interface ExpressionVisitor { // 定义表达式访问者接口，所有具体的表达式访问者实现类都需要实现该接口
  /**
   * Visits the children of the delegate expression. // 访问委托表达式的子节点，这是访问者模式的核心方法，用于遍历和处理表达式树中的Lambda表达式节点
   * 
   * 该方法定义了访问Lambda表达式（函数表达式）的标准行为。Lambda表达式是表达式树中的一种特殊节点，
   * 它代表一个函数或委托，包含参数列表和函数体。通过访问Lambda表达式，可以递归地遍历其内部的子表达式，
   * 从而实现对整个表达式树的完整遍历。
   * 
   * 方法参数说明：
   * - expression: 要访问的函数表达式对象，类型为FunctionExpression<T>，其中T是泛型类型参数，必须继承自Function接口
   * 
   * 泛型参数说明：
   * - <T extends Function<?>>: 泛型类型T必须是Function接口的子类型，Function是LINQ4J框架中定义的函数式接口
   * - 问号?表示Function的类型参数可以是任意类型，这样可以支持各种不同签名的函数表达式
   * 
   * 访问模式说明：
   * - 当访问者遇到Lambda表达式节点时，会调用该方法
   * - 该方法应该递归地访问Lambda表达式的参数和函数体中的所有子表达式
   * - 具体的访问行为由实现该接口的类决定，可以是读取、修改、验证或转换表达式
   * 
   * 应用示例：
   * 1. 表达式求值器：遍历表达式树并计算最终结果
   * 2. 表达式优化器：识别并优化表达式树中的模式（如常量折叠）
   * 3. 表达式转换器：将表达式树转换为其他形式（如SQL查询语句）
   * 4. 表达式验证器：检查表达式树的类型正确性和语义合法性
   * 
   * 注意事项：
   * - 该方法不返回值，访问者通常通过内部状态或外部收集器来保存访问结果
   * - 实现类需要确保正确处理表达式树的递归结构，避免无限循环
   * - 如果需要修改表达式树，应该创建新的表达式节点而不是修改现有节点（不可变对象模式）
   * 
   * @param expression 要访问的函数表达式，包含委托函数和相关的表达式树结构
   * @param <T> 函数表达式的泛型类型，必须继承自Function接口
   * @see FunctionExpression 函数表达式类，表示包含委托的表达式树节点
   * @see Function LINQ4J框架中的函数式接口，定义函数的基本契约
   */
  <T extends Function<?>> void visitLambda(FunctionExpression<T> expression); // 定义访问Lambda表达式的方法，泛型T表示函数类型必须继承自Function接口，参数expression是要访问的函数表达式对象
}
