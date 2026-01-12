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
package org.apache.calcite.linq4j; // 声明包名，该类属于 org.apache.calcite.linq4j 包，这是 Calcite LINQ4J 模块的核心包

/** Enumerator that applies a transform to each value from a backing
 * enumerator.
 * // 转换枚举器：该类是一个抽象的枚举器实现，它会对底层枚举器返回的每个元素应用转换函数
 * // 这是一种装饰器模式的应用，通过包装原始枚举器来改变其输出的元素类型
 * // 例如：将一个 List<Integer> 转换为 List<String>，其中每个整数都转换为对应的字符串表示
 *
 * @param <F> Element type of backing enumerator // 类型参数 F：底层枚举器中的元素类型（From 类型，即转换前的类型）
 * @param <E> Element type // 类型参数 E：转换后的元素类型（Element 类型，即转换后的类型）
 */
public abstract class TransformedEnumerator<F, E> implements Enumerator<E> { // 定义一个抽象类 TransformedEnumerator，实现 Enumerator<E> 接口，表示一个可枚举的转换器
  protected final Enumerator<? extends F> enumerator; // 成员变量：底层枚举器，使用 protected final 修饰，表示该枚举器在子类中可访问且不可重新赋值，类型为 Enumerator<? extends F> 表示可以是 F 或其子类型的枚举器

  protected TransformedEnumerator(Enumerator<? extends F> enumerator) { // 构造方法：接收一个底层枚举器作为参数，用于初始化转换枚举器
    this.enumerator = enumerator; // 将传入的底层枚举器赋值给成员变量，后续所有操作都委托给这个底层枚举器执行
  } // 构造方法结束

  protected abstract E transform(F from); // 抽象方法：定义转换逻辑，由子类实现具体的转换规则，将类型为 F 的元素转换为类型为 E 的元素，这是模板方法模式的核心

  @Override public boolean moveNext() { // 重写接口方法：移动到下一个元素，如果存在下一个元素则返回 true，否则返回 false
    return enumerator.moveNext(); // 直接委托给底层枚举器的 moveNext() 方法执行，不进行任何转换操作
  } // moveNext 方法结束

  @Override public E current() { // 重写接口方法：获取当前元素，返回类型为 E
    return transform(enumerator.current()); // 首先从底层枚举器获取当前元素（类型为 F），然后调用 transform() 方法将其转换为类型 E 并返回
  } // current 方法结束

  @Override public void reset() { // 重写接口方法：重置枚举器到初始状态
    enumerator.reset(); // 直接委托给底层枚举器的 reset() 方法执行，将底层枚举器重置到起始位置
  } // reset 方法结束

  @Override public void close() { // 重写接口方法：关闭枚举器，释放相关资源
    enumerator.close(); // 直接委托给底层枚举器的 close() 方法执行，关闭底层枚举器并释放其占用的资源
  } // close 方法结束
} // TransformedEnumerator 类定义结束
