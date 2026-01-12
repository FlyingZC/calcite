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
package org.apache.calcite.linq4j; // 定义包名，该接口属于linq4j模块

/**
 * Represents the result of applying a sorting operation to an // 表示对可枚举集合应用排序操作的结果
 * {@link org.apache.calcite.linq4j.Enumerable}. // 该接口是Enumerable接口的扩展，专门用于表示已排序的集合
 * // OrderedEnumerable接口是Calcite LINQ4j框架中的核心接口之一，它继承自Enumerable<T>和ExtendedOrderedEnumerable<T>
 * // 作用：1) 表示一个已经应用了排序操作的可枚举集合 2) 提供了对已排序集合进行进一步排序的能力（二级排序、多级排序）
 * // 3) 在LINQ查询中，当使用OrderBy、OrderByDescending等方法时，返回的就是OrderedEnumerable类型
 * // 4) 支持链式排序操作，例如：query.OrderBy(x -> x.A).ThenBy(x -> x.B).ThenByDescending(x -> x.C)
 * // 5) 该接口本身没有定义新的方法，所有排序相关的方法都继承自ExtendedOrderedEnumerable接口
 * // 6) 泛型参数T表示集合中元素的类型
 *
 * @param <T> element type // 泛型参数T，表示集合中元素的类型
 */
public interface OrderedEnumerable<T> // 定义OrderedEnumerable接口，这是一个泛型接口，T是元素类型
    extends Enumerable<T>, ExtendedOrderedEnumerable<T> { // 该接口继承自两个接口：1) Enumerable<T> - 提供基本的可枚举集合功能 2) ExtendedOrderedEnumerable<T> - 提供扩展的排序功能，如ThenBy、ThenByDescending等方法
} // 接口定义结束，OrderedEnumerable是一个标记接口，主要作用是类型安全和语义表达
