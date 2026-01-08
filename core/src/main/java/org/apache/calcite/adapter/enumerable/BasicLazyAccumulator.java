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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于 org.apache.calcite.adapter.enumerable 包，这是 Calcite 框架中可枚举适配器的核心包

import org.apache.calcite.linq4j.function.Function2; // 导入 Function2 接口，这是一个接受两个参数并返回一个结果的函数式接口，用于定义累加操作

/**
 * Performs accumulation against a pre-collected list of input sources,
 * used with {@link LazyAggregateLambdaFactory}.
 * 该类的作用是：对预先收集的输入源列表执行累加操作，通常与 LazyAggregateLambdaFactory 配合使用
 * LazyAggregateLambdaFactory 是一个用于延迟聚合的工厂类，允许在运行时动态创建聚合函数
 * BasicLazyAccumulator 实现了 LazyAccumulator 接口，提供了最基本的累加功能实现
 * 在 Calcite 的聚合查询中，当需要将多行数据聚合成单个结果时（如 SUM、COUNT、AVG 等），就需要使用累加器
 * 该类采用"惰性"（Lazy）的设计模式，意味着它不会立即处理数据，而是在需要时才对预先收集的数据源进行累加
 * 这种设计可以提高性能，特别是在处理大量数据时，可以避免重复计算
 *
 * @param <TAccumulate> Type of the accumulator // 泛型参数 TAccumulate 表示累加器的类型，即累加过程中维护的中间结果类型
 * @param <TSource>     Type of the enumerable input source // 泛型参数 TSource 表示可枚举输入源的类型，即要被累加的每条数据的类型
 */
public class BasicLazyAccumulator<TAccumulate, TSource> // 定义 BasicLazyAccumulator 类，使用两个泛型参数：TAccumulate（累加器类型）和 TSource（输入源类型）
    implements LazyAggregateLambdaFactory.LazyAccumulator<TAccumulate, TSource> { // 实现 LazyAggregateLambdaFactory.LazyAccumulator 接口，该接口定义了累加器的契约，要求实现 accumulate 方法

  private final Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder; // 成员变量：存储累加器添加操作的函数，这是一个 Function2 类型的函数，接受三个泛型参数：TAccumulate（当前累加值）、TSource（输入值）、TAccumulate（返回新的累加值），final 表示该引用不可变，确保线程安全性

  public BasicLazyAccumulator(Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder) { // 构造方法：创建 BasicLazyAccumulator 实例，参数 accumulatorAdder 是一个函数对象，定义了如何将一个输入值累加到累加器中，例如对于 SUM 操作，这个函数可能是 (acc, val) -> acc + val
    this.accumulatorAdder = accumulatorAdder; // 将传入的 accumulatorAdder 函数赋值给成员变量，这样在 accumulate 方法中就可以调用这个函数来执行实际的累加操作
  } // 构造方法结束

  @Override public void accumulate(Iterable<TSource> sourceIterable, TAccumulate accumulator) { // 实现 LazyAccumulator 接口的 accumulate 方法，该方法负责对输入源集合执行累加操作，参数 sourceIterable 是一个可迭代的输入源集合，包含了所有需要被累加的数据；参数 accumulator 是初始的累加器值，可能是 0、空集合或其他初始值
    TAccumulate accumulator1 = accumulator; // 创建一个局部变量 accumulator1 并初始化为传入的 accumulator 值，这是为了在循环中维护累加状态，每次迭代都会更新这个变量
    for (TSource tSource : sourceIterable) { // 使用增强 for 循环遍历 sourceIterable 中的每一个元素 TSource，这个迭代过程会处理集合中的所有输入值
      accumulator1 = accumulatorAdder.apply(accumulator1, tSource); // 调用 accumulatorAdder 函数，将当前的累加值 accumulator1 和当前输入值 tSource 作为参数传入，函数返回新的累加值并赋值给 accumulator1，这样就完成了一次累加操作
    } // for 循环结束，此时 accumulator1 中已经包含了所有输入值的累加结果
  } // accumulate 方法结束，注意该方法没有返回值，因为累加结果是通过修改传入的 accumulator 对象来实现的（虽然在这个实现中使用了局部变量，但 Java 的对象引用特性使得修改会对调用者可见）
} // BasicLazyAccumulator 类定义结束
