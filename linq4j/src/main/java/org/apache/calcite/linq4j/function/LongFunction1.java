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
// Apache 开源许可证声明，允许在符合 Apache 2.0 许可证条款下使用、修改和分发此代码
package org.apache.calcite.linq4j.function; // 定义包名，表明这个接口属于 Calcite LINQ4J 框架的函数功能包

/**
 * Function that takes one parameter and returns a native {@code long} value.
 * // 接受一个参数并返回原生 long 值的函数接口，这是 LINQ4J 框架中用于函数式编程的核心接口之一
 * // 该接口定义了单参数函数的抽象行为，允许将函数作为一等公民在代码中传递和使用
 * // 原生 long 类型意味着返回值不是 Long 包装对象，而是基本数据类型 long，避免了自动装箱的开销
 * // 这种设计在性能敏感的场景下非常重要，特别是在大数据处理和流式计算中
 * // 该接口是函数式接口（Functional Interface），只包含一个抽象方法，可以用 Lambda 表达式或方法引用实现
 * // 它继承自 Function<Long>，表明它是一个特殊的函数类型，专门用于返回 long 类型的结果
 * // 在 Calcite 的查询优化和执行过程中，这种函数接口被广泛用于定义各种转换和计算逻辑
 *
 * @param <T0> Type of argument #0
 * // 泛型参数 T0 表示函数第一个参数的类型，可以是任何 Java 类型
 * // 使用泛型使得这个接口可以接受任意类型的输入参数，提供了极大的灵活性
 * // 例如：LongFunction1<String> 表示接受 String 参数并返回 long 的函数
 * // 例如：LongFunction1<Integer> 表示接受 Integer 参数并返回 long 的函数
 * // 泛型的使用使得类型安全在编译时就能得到保证，避免了运行时的类型转换错误
 */
public interface LongFunction1<T0> extends Function<Long> { // 定义一个公共接口 LongFunction1，使用泛型 T0 作为参数类型，继承自 Function<Long> 接口
  long apply(T0 v0); // 抽象方法：接受一个 T0 类型的参数 v0，返回一个原生 long 类型的结果，这是接口的核心方法，需要由实现类提供具体实现
  // apply 方法是函数式接口的唯一抽象方法，定义了函数的执行逻辑
  // 参数 v0 是函数的输入值，类型由泛型 T0 决定
  // 返回值是原生 long 类型，不是 Long 包装类，这样可以避免不必要的对象创建和垃圾回收
  // 在实际使用中，这个方法会被 Calcite 的查询引擎调用，用于执行各种计算和转换操作
  // 例如：可以将字符串转换为 long 值，或者从对象中提取某个 long 类型的属性
  // 方法名 apply 是函数式编程中的标准命名，表示"应用"函数到给定的参数上
} // 接口定义结束
