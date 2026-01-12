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
package org.apache.calcite.linq4j; // 声明包名，该类属于 org.apache.calcite.linq4j 包，是 Calcite LINQ4J 框架的核心包之一

/**这是 Enumerable 接口的抽象实现类，实现了扩展方法.Enumerable 接口是 LINQ (Language Integrated Query) 风格的集合接口，提供了类似 .NET LINQ 的查询能力
 * Abstract implementation of the {@link org.apache.calcite.linq4j.Enumerable}
 * interface that implements the extension methods.该类作为抽象基类，为具体的 Enumerable 实现提供了基础功能
 *
 * <p>It is helpful to derive from this class if you are implementing
 * {@code Enumerable}, because {@code Enumerable} has so many extension methods,
 * but it is not required.
 * // 如果你要实现 Enumerable 接口，继承这个类会很有帮助，因为 Enumerable 接口有很多扩展方法
 * // 通过继承这个抽象类，可以自动获得这些扩展方法的实现，而不需要自己逐一实现
 * // 但这不是强制的，你也可以直接实现 Enumerable 接口
 * // 该类的设计遵循了模板方法模式，提供了默认实现，子类可以根据需要重写特定方法
 *
 * @param <T> Element type
 * // 泛型参数 T 表示集合中元素的类型，使得该类可以支持任意类型的集合
 * // 例如：AbstractEnumerable2<String> 表示字符串集合，AbstractEnumerable2<Integer> 表示整数集合
 */
public abstract class AbstractEnumerable2<T> extends DefaultEnumerable<T> { // 定义抽象类 AbstractEnumerable2，继承自 DefaultEnumerable<T>，使用泛型参数 T 表示元素类型
  @Override public Enumerator<T> enumerator() { // 重写父类的 enumerator() 方法，返回一个枚举器（迭代器）对象，用于遍历集合中的元素
    return new Linq4j.IterableEnumerator<>(this); // 创建并返回一个 IterableEnumerator 对象，将当前对象（this）包装为可迭代的枚举器
    // IterableEnumerator 是 Linq4j 内部提供的枚举器实现，它将 Iterable 接口适配为 Enumerator 接口
    // Enumerator 是 LINQ 风格的迭代器接口，提供了 moveNext() 和 current() 方法来遍历元素
    // 这种设计允许将任何 Iterable 对象转换为 LINQ 风格的枚举器，从而支持 LINQ 查询操作
  }
} // 类定义结束
