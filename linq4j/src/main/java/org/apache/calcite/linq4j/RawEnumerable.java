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
// Apache License 2.0 许可证声明，说明本代码遵循 Apache 开源协议
package org.apache.calcite.linq4j; // 定义包名，本接口属于 org.apache.calcite.linq4j 包，该包提供了 LINQ(Language Integrated Query) 风格的查询功能

import org.checkerframework.framework.qual.Covariant; // 导入 Checker Framework 的 Covariant 注解，用于标记协变类型参数

/**
 * Exposes the enumerator, which supports a simple iteration over a collection,
 * without the extension methods.
 * // 暴露枚举器，支持对集合的简单迭代，但不包含扩展方法
 *
 * <p>Just the bare methods, to make it easier to implement. Code that requires
 * the extension methods can use the static methods in {@link Extensions}.
 * // 只包含最基本的方法，使实现更容易。需要扩展方法的代码可以使用 {@link Extensions} 中的静态方法
 *
 * <p>Analogous to LINQ's System.Collections.IEnumerable (both generic
 * and non-generic variants), without the extension methods.
 * // 类似于 LINQ 的 System.Collections.IEnumerable（包括泛型和非泛型变体），但不包含扩展方法
 *
 * @param <T> Element type // 类型参数 T 表示集合中元素的类型
 * @see Enumerable // 参见 Enumerable 接口，它是 RawEnumerable 的扩展版本，包含更多查询方法
 */
@Covariant(0) // 使用 Checker Framework 的注解标记第 0 个类型参数（即 T）是协变的，允许 RawEnumerable<子类型> 赋值给 RawEnumerable<父类型>
public interface RawEnumerable<T> { // 定义 RawEnumerable 接口，这是一个泛型接口，T 表示元素的类型，该接口提供了最基础的集合迭代功能
  /**
   * Returns an enumerator that iterates through a collection.
   * // 返回一个枚举器，该枚举器用于遍历集合中的元素
   */
  Enumerator<T> enumerator(); // 声明抽象方法，返回一个 Enumerator<T> 对象，该对象实现了迭代器模式，用于逐个访问集合中的元素
}
