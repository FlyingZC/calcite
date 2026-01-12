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
package org.apache.calcite.linq4j.tree; // 声明包名，该类属于 org.apache.calcite.linq4j.tree 包，用于 LINQ 表达式树的处理

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 CheckerFramework 的注解，用于标记可能为 null 的值，帮助进行空值检查

import java.util.ArrayList; // 导入 ArrayList 类，用于创建动态数组列表
import java.util.List; // 导入 List 接口，用于定义列表类型

/**
 * Holds context for evaluating expressions. // 该类用于保存表达式求值时的上下文环境，维护参数和值的栈结构
 */
class Evaluator { // 定义 Evaluator 类，包级私有类，用于表达式求值的上下文管理
  final List<ParameterExpression> parameters = new ArrayList<>(); // 参数表达式列表，使用栈结构存储参数表达式，用于在表达式求值时跟踪参数
  final List<@Nullable Object> values = new ArrayList<>(); // 参数值列表，与 parameters 列表一一对应，存储每个参数表达式的实际值，可能为 null

  Evaluator() { // 默认构造方法，创建一个空的 Evaluator 实例，初始化时参数栈和值栈都为空
  } // 构造方法结束

  void push(ParameterExpression parameter, @Nullable Object value) { // 将参数表达式和对应的值压入栈顶，用于在求值过程中保存新的参数绑定
    parameters.add(parameter); // 将参数表达式添加到参数列表末尾（栈顶）
    values.add(value); // 将对应的值添加到值列表末尾（栈顶），保持与参数列表的对应关系
  } // push 方法结束

  void pop(int n) { // 从栈顶弹出 n 个参数和对应的值，用于在求值完成后清理不再需要的参数绑定
    while (n > 0) { // 循环 n 次，每次弹出一个参数和值
      parameters.remove(parameters.size() - 1); // 移除参数列表的最后一个元素（栈顶的参数表达式）
      values.remove(values.size() - 1); // 移除值列表的最后一个元素（栈顶的值），保持两个列表的同步
      --n; // 递减计数器，控制循环次数
    } // while 循环结束
  } // pop 方法结束

  @Nullable Object peek(ParameterExpression param) { // 查找并返回指定参数表达式的值，从栈顶向栈底搜索，支持参数的嵌套作用域
    for (int i = parameters.size() - 1; i >= 0; i--) { // 从栈顶（列表末尾）开始向栈底（列表开头）遍历参数列表
      if (parameters.get(i) == param) { // 检查当前位置的参数表达式是否与要查找的参数相同（使用 == 比较引用）
        return values.get(i); // 如果找到匹配的参数，返回对应的值
      } // if 判断结束
    } // for 循环结束
    throw new RuntimeException("parameter " + param + " not on stack"); // 如果遍历完所有参数都没有找到，抛出运行时异常，表示参数不在栈中
  } // peek 方法结束

  @Nullable Object evaluate(Node expression) { // 对表达式节点进行求值，委托给表达式节点自身的 evaluate 方法
    return ((AbstractNode) expression).evaluate(this); // 将表达式节点转换为 AbstractNode 类型，并调用其 evaluate 方法，传入当前 Evaluator 实例作为上下文
  } // evaluate 方法结束

  void clear() { // 清空所有参数和值，重置 Evaluator 到初始状态
    parameters.clear(); // 清空参数列表，移除所有参数表达式
    values.clear(); // 清空值列表，移除所有对应的值
  } // clear 方法结束
} // Evaluator 类结束
