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
package org.apache.calcite.linq4j; // 声明包名，表示这个类属于 org.apache.calcite.linq4j 包，这是 Calcite LINQ4J 模块的核心包

/**
 * Enumerable that has a (limited) memory for n past and m future steps.
 * 这是一个具有有限记忆功能的 Enumerable，能够记住过去 n 个步骤和未来 m 个步骤的数据
 * MemoryEnumerable 是一个包装类，它将普通的 Enumerable 转换为具有记忆能力的 Enumerable
 * 这个类主要用于在遍历序列时，能够访问当前元素之前的历史元素和之后的未来元素
 * 这种功能在处理需要上下文信息的场景非常有用，比如移动窗口计算、序列分析等
 *
 * @param <E> Type of the Enumerable items to remember // 泛型参数 E 表示要记住的 Enumerable 元素的类型
 */
public class MemoryEnumerable<E> extends AbstractEnumerable<MemoryFactory.Memory<E>> { // 定义 MemoryEnumerable 类，继承自 AbstractEnumerable，泛型参数为 MemoryFactory.Memory<E>，表示枚举器返回的是包含历史和未来信息的 Memory 对象
  private final Enumerable<E> input; // 成员变量：input，表示被包装的原始 Enumerable，这是数据源，所有的元素都从这里获取
  private final int history; // 成员变量：history，表示需要记住的历史元素数量，即当前元素之前要保留多少个元素
  private final int future; // 成员变量：future，表示需要预取的未来元素数量，即当前元素之后要预取多少个元素

  /**
   * Creates a MemoryEnumerable.
   * 构造方法：创建一个 MemoryEnumerable 实例
   * 这个构造方法会初始化 MemoryEnumerable，设置输入数据源、历史记忆长度和未来预取长度
   *
   * @param input The Enumerable which the memory should be "wrapped" around // 参数：input，表示要被包装的原始 Enumerable，这是数据源
   * @param history Number of present steps to remember // 参数：history，表示要记住的历史步骤数量，即当前元素之前的元素数量
   * @param future Number of future steps to remember // 参数：future，表示要预取的未来步骤数量，即当前元素之后的元素数量
   */
  MemoryEnumerable(Enumerable<E> input, int history, int future) { // 构造方法定义，接收三个参数：输入 Enumerable、历史数量、未来数量
    this.input = input; // 将传入的 input 参数赋值给成员变量 input，保存原始数据源的引用
    this.history = history; // 将传入的 history 参数赋值给成员变量 history，保存需要记住的历史元素数量
    this.future = future; // 将传入的 future 参数赋值给成员变量 future，保存需要预取的未来元素数量
  } // 构造方法结束

  @Override public Enumerator<MemoryFactory.Memory<E>> enumerator() { // 重写父类的 enumerator 方法，返回一个能够遍历 Memory 对象的枚举器
    return new MemoryEnumerator<>(input.enumerator(), history, future); // 创建并返回一个新的 MemoryEnumerator 实例，传入输入枚举器、历史数量和未来数量
  } // enumerator 方法结束

} // MemoryEnumerable 类定义结束
