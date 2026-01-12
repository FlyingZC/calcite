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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.linq4j; // 定义包名，该类属于calcite的linq4j模块，提供类似.NET LINQ的功能

import org.checkerframework.framework.qual.Covariant; // 导入Checker框架的协变注解，用于类型系统的协变标记

/**
 * Exposes the enumerator, which supports a simple iteration over a collection. // 暴露枚举器，支持对集合的简单迭代
 *
 * <p>Analogous to LINQ's System.Collections.IEnumerable (both generic // 类似于LINQ的System.Collections.IEnumerable接口（包括泛型和非泛型版本）
 * and non-generic variants). // 以及非泛型变体）
 *
 * <p>Also implements {@link Iterable}, to enable use in Java foreach loops. // 同时实现Iterable接口，以支持在Java的foreach循环中使用
 *
 * @param <T> Element type // 泛型参数T表示集合中元素的类型
 */
@Covariant(0) // 标记第一个泛型参数T是协变的，允许子类返回更具体的类型
public interface Enumerable<T> // 定义泛型接口Enumerable，T是元素类型参数
    extends RawEnumerable<T>, Iterable<T>, ExtendedEnumerable<T> { // 继承三个接口：RawEnumerable提供基础枚举功能，Iterable支持Java迭代，ExtendedEnumerable提供扩展查询方法
  /**
   * Converts this Enumerable to a Queryable. // 将此Enumerable转换为Queryable，Queryable支持表达式树和延迟执行
   *
   * @see EnumerableDefaults#asQueryable(Enumerable) // 参见EnumerableDefaults类的asQueryable方法实现
   */
  @Override Queryable<T> asQueryable(); // 重写asQueryable方法，返回可查询的Queryable对象，用于构建和执行表达式树

} // 接口定义结束
