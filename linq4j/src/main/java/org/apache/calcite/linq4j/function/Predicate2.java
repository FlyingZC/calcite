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
package org.apache.calcite.linq4j.function; // 包声明，指定该接口位于 org.apache.calcite.linq4j.function 包中，属于 Calcite LINQ4J 函数模块

/**
 * Function with two parameters returning a native {@code boolean} value. // 接口功能说明：这是一个带有两个参数的函数接口，返回原生 boolean 值（非 Boolean 对象）
 * // 该接口是 Calcite LINQ4J 框架中用于表示二元谓词的核心接口，用于对两个参数进行条件判断
 * // Predicate2 是函数式接口，可以用于 Lambda 表达式和方法引用，常用于过滤、筛选等场景
 * // 继承自 Function<Boolean>，使其可以统一作为函数使用
 *
 * @param <T0> Type of argument #0 // 泛型参数 T0 表示第一个参数的类型，可以是任意类型
 * @param <T1> Type of argument #1 // 泛型参数 T1 表示第二个参数的类型，可以是任意类型
 */
public interface Predicate2<T0, T1> extends Function<Boolean> { // 定义 Predicate2 接口，使用两个泛型参数 T0 和 T1，继承自 Function<Boolean> 接口
  /**
   * Predicate that always evaluates to {@code true}. // 成员变量说明：这是一个常量，表示总是返回 true 的谓词
   * // TRUE 是一个静态常量，类型为 Predicate2<Object, Object>，接受两个任意类型的参数，永远返回 true
   * // 使用 Lambda 表达式 (v0, v1) -> true 实现，忽略所有输入参数，直接返回 true
   * // 常用于需要无条件通过的场景，比如在测试或默认配置中
   *
   * @see org.apache.calcite.linq4j.function.Functions#truePredicate1() // 参见 Functions 类中的 truePredicate1() 方法
   */
  Predicate2<Object, Object> TRUE = (v0, v1) -> true; // 使用 Lambda 表达式定义一个总是返回 true 的 Predicate2 实例

  /**
   * Predicate that always evaluates to {@code false}. // 成员变量说明：这是一个常量，表示总是返回 false 的谓词
   * // FALSE 是一个静态常量，类型为 Predicate2<Object, Object>，接受两个任意类型的参数，永远返回 false
   * // 使用 Lambda 表达式 (v0, v1) -> false 实现，忽略所有输入参数，直接返回 false
   * // 常用于需要无条件拒绝的场景，比如在测试或默认配置中
   *
   * @see org.apache.calcite.linq4j.function.Functions#falsePredicate1() // 参见 Functions 类中的 falsePredicate1() 方法
   */
  Predicate2<Object, Object> FALSE = (v0, v1) -> false; // 使用 Lambda 表达式定义一个总是返回 false 的 Predicate2 实例

  boolean apply(T0 v0, T1 v1); // 接口方法：抽象方法，用于对两个参数进行条件判断并返回 boolean 结果
  // apply 方法是 Predicate2 接口的核心方法，接受两个参数 v0（类型为 T0）和 v1（类型为 T1）
  // 该方法由实现类提供具体逻辑，根据两个参数的值返回 true 或 false
  // 该方法是函数式接口的唯一抽象方法，可以用 Lambda 表达式或方法引用实现
  // 典型使用场景包括：对象比较、范围判断、条件过滤、关联匹配等
} // 接口定义结束
