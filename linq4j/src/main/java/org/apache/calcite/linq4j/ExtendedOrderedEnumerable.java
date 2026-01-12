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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证，允许在特定条件下使用和分发
package org.apache.calcite.linq4j; // 定义包名，该类属于org.apache.calcite.linq4j包，是Calcite LINQ4J框架的一部分

import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口，用于定义从T类型到TKey类型的转换函数

import java.util.Comparator; // 导入Comparator接口，用于自定义对象比较逻辑

/**
 * Extension methods for {@link OrderedEnumerable}.
 * 为OrderedEnumerable接口提供扩展方法的接口
 * 
 * 【类作用详细说明】：
 * ExtendedOrderedEnumerable是OrderedEnumerable的扩展接口，提供了多级排序的功能。
 * 在LINQ(Language Integrated Query)中，排序操作通常分为两种：
 * 1. 主排序(OrderBy/OrderByDescending)：对序列进行首次排序
 * 2. 次级排序(ThenBy/ThenByDescending)：在已有排序的基础上进行后续排序
 * 
 * 该接口的作用是允许在已经排序的序列上添加额外的排序条件，实现多级排序。
 * 例如：先按年龄排序，年龄相同时再按姓名排序
 * 
 * 【使用场景】：
 * - 需要对数据进行多级排序时使用
 * - 在已有序列上添加后续排序条件
 * - 保持已有的排序顺序，添加次要排序键
 * 
 * 【实现原理】：
 * - 继承自Enumerable<T>，保持可枚举特性
 * - 提供thenBy和thenByDescending方法进行次级排序
 * - 内部维护排序规则链，确保排序的优先级
 * 
 * 【泛型参数】：
 * @param <T> Element type - 序列中元素的类型，表示可枚举集合中元素的类型
 */
public interface ExtendedOrderedEnumerable<T> extends Enumerable<T> { // 定义接口，继承自Enumerable<T>，表示这是一个可枚举的有序集合接口
  /**
   * Performs a subsequent ordering of the elements in an
   * {@link OrderedEnumerable} according to a key, using a specified
   * comparator.
   * 根据指定的键和比较器，对OrderedEnumerable中的元素执行后续排序操作
   * 
   * 【方法作用详细说明】：
   * 这是一个核心的排序方法，用于在已有排序的基础上添加新的排序条件。
   * 该方法是thenBy和thenByDescending的底层实现，通过boolean参数控制排序方向。
   * 
   * 【工作流程】：
   * 1. 接收一个键选择器函数，用于从元素中提取排序键
   * 2. 接收一个比较器，用于比较两个键的大小
   * 3. 接收一个降序标志，决定是升序还是降序排序
   * 4. 返回一个新的OrderedEnumerable，包含了原有的排序规则和新的排序规则
   * 
   * 【参数说明】：
   * @param <TKey> 排序键的类型，泛型参数，表示用于排序的键的类型
   * @param keySelector Function1<T, TKey> - 键选择器函数，从元素T中提取排序键TKey的函数
   * @param comparator Comparator<TKey> - 比较器，用于比较两个TKey类型对象的大小
   * @param descending boolean - 是否降序排序，true表示降序，false表示升序
   * 
   * 【返回值说明】：
   * @return OrderedEnumerable<T> - 返回一个新的有序可枚举对象，包含原有的排序规则和新添加的排序规则
   * 
   * 【使用示例】：
   * // 先按年龄升序排序，再按姓名降序排序
   * orderedEnumerable.createOrderedEnumerable(p -> p.getAge(), Comparator.naturalOrder(), false)
   *                  .createOrderedEnumerable(p -> p.getName(), Comparator.naturalOrder(), true)
   * 
   * 【实现细节】：
   * - 该方法是多级排序的基础
   * - 调用该方法不会立即执行排序，而是在实际遍历时才执行
   * - 每次调用都创建一个新的排序规则，形成排序规则链
   * - 排序规则的执行顺序与添加顺序一致，先添加的优先级高
   *
   * <p>The functionality provided by this method is like that provided by
   * {@link #thenBy(org.apache.calcite.linq4j.function.Function1, java.util.Comparator) thenBy}
   * or
   * {@link #thenByDescending(org.apache.calcite.linq4j.function.Function1, java.util.Comparator) thenByDescending},
   * depending on whether descending is true or false. They both perform a
   * subordinate ordering of an already sorted sequence of type
   * {@link OrderedEnumerable}.
   * 该方法提供的功能类似于thenBy或thenByDescending方法，具体取决于descending参数的值
   * 它们都是对已排序的OrderedEnumerable序列执行次级排序操作
   */
  <TKey> OrderedEnumerable<T> createOrderedEnumerable( // 定义泛型方法，创建有序可枚举对象，TKey是排序键的类型
      Function1<T, TKey> keySelector, Comparator<TKey> comparator, // 参数：键选择器函数和比较器，用于从元素中提取键并比较键的大小
      boolean descending); // 参数：降序标志，true表示降序排序，false表示升序排序

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key.
   * 根据键对序列中的元素执行升序的后续排序操作
   * 
   * 【方法作用详细说明】：
   * 这是一个便捷方法，用于在已有排序的基础上添加升序排序条件。
   * 该方法内部会调用createOrderedEnumerable方法，将descending参数设为false。
   * 
   * 【工作流程】：
   * 1. 接收一个键选择器函数，键类型必须实现Comparable接口
   * 2. 使用键的自然顺序进行升序排序
   * 3. 返回一个新的OrderedEnumerable，包含原有的排序规则和新的升序排序规则
   * 
   * 【参数说明】：
   * @param <TKey> 排序键的类型，必须实现Comparable接口，泛型参数，表示用于排序的键的类型
   * @param keySelector Function1<T, TKey> - 键选择器函数，从元素T中提取排序键TKey的函数
   * 
   * 【返回值说明】：
   * @return OrderedEnumerable<T> - 返回一个新的有序可枚举对象，包含原有的排序规则和新添加的升序排序规则
   * 
   * 【使用示例】：
   * // 先按部门排序，再按薪资升序排序（薪资实现了Comparable）
   * orderedEnumerable.thenBy(Person::getSalary)
   * 
   * 【与createOrderedEnumerable的区别】：
   * - 该方法要求键类型实现Comparable接口
   * - 该方法使用自然顺序排序，不需要提供比较器
   * - 该方法固定为升序排序
   * 
   * 【实现细节】：
   * - 内部实现会使用Comparator.naturalOrder()作为比较器
   * - 适用于基本类型或已实现Comparable的自定义类型
   * - 调用该方法不会立即执行排序，延迟到实际遍历时执行
   */
  <TKey extends Comparable<TKey>> OrderedEnumerable<T> thenBy( // 定义泛型方法，执行升序的后续排序，TKey必须实现Comparable接口
      Function1<T, TKey> keySelector); // 参数：键选择器函数，从元素中提取用于排序的键

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key, using a specified comparator.
   * 根据指定的键和比较器，对序列中的元素执行升序的后续排序操作
   * 
   * 【方法作用详细说明】：
   * 这是一个灵活的排序方法，允许使用自定义比较器进行升序排序。
   * 该方法内部会调用createOrderedEnumerable方法，将descending参数设为false。
   * 
   * 【工作流程】：
   * 1. 接收一个键选择器函数，从元素中提取排序键
   * 2. 接收一个自定义比较器，用于比较键的大小
   * 3. 使用指定的比较器进行升序排序
   * 4. 返回一个新的OrderedEnumerable，包含原有的排序规则和新的升序排序规则
   * 
   * 【参数说明】：
   * @param <TKey> 排序键的类型，泛型参数，表示用于排序的键的类型
   * @param keySelector Function1<T, TKey> - 键选择器函数，从元素T中提取排序键TKey的函数
   * @param comparator Comparator<TKey> - 比较器，用于比较两个TKey类型对象的大小
   * 
   * 【返回值说明】：
   * @return OrderedEnumerable<T> - 返回一个新的有序可枚举对象，包含原有的排序规则和新添加的升序排序规则
   * 
   * 【使用示例】：
   * // 先按部门排序，再按姓名升序排序（使用自定义比较器，忽略大小写）
   * orderedEnumerable.thenBy(Person::getName, String.CASE_INSENSITIVE_ORDER)
   * 
   * 【与thenBy(无comparator版本)的区别】：
   * - 该方法允许使用自定义比较器，不要求键实现Comparable接口
   * - 该方法更灵活，可以定义复杂的比较逻辑
   * - 该方法固定为升序排序
   * 
   * 【适用场景】：
   * - 需要自定义比较逻辑时使用
   * - 键类型未实现Comparable接口时使用
   * - 需要特殊的排序规则时使用（如忽略大小写、按特定规则排序等）
   * 
   * 【实现细节】：
   * - 内部调用createOrderedEnumerable(keySelector, comparator, false)
   * - 比较器可以是null，此时使用键的自然顺序
   * - 调用该方法不会立即执行排序，延迟到实际遍历时执行
   */
  <TKey> OrderedEnumerable<T> thenBy(Function1<T, TKey> keySelector, // 定义泛型方法，执行升序的后续排序，TKey是排序键的类型
      Comparator<TKey> comparator); // 参数：比较器，用于比较两个键的大小

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * descending order according to a key.
   * 根据键对序列中的元素执行降序的后续排序操作
   * 
   * 【方法作用详细说明】：
   * 这是一个便捷方法，用于在已有排序的基础上添加降序排序条件。
   * 该方法内部会调用createOrderedEnumerable方法，将descending参数设为true。
   * 
   * 【工作流程】：
   * 1. 接收一个键选择器函数，键类型必须实现Comparable接口
   * 2. 使用键的自然顺序进行降序排序
   * 3. 返回一个新的OrderedEnumerable，包含原有的排序规则和新的降序排序规则
   * 
   * 【参数说明】：
   * @param <TKey> 排序键的类型，必须实现Comparable接口，泛型参数，表示用于排序的键的类型
   * @param keySelector Function1<T, TKey> - 键选择器函数，从元素T中提取排序键TKey的函数
   * 
   * 【返回值说明】：
   * @return OrderedEnumerable<T> - 返回一个新的有序可枚举对象，包含原有的排序规则和新添加的降序排序规则
   * 
   * 【使用示例】：
   * // 先按部门排序，再按薪资降序排序（薪资实现了Comparable）
   * orderedEnumerable.thenByDescending(Person::getSalary)
   * 
   * 【与thenBy的区别】：
   * - 该方法执行降序排序，thenBy执行升序排序
   * - 其他使用方式和限制相同
   * 
   * 【实现细节】：
   * - 内部实现会使用Comparator.naturalOrder()作为比较器
   * - 适用于基本类型或已实现Comparable的自定义类型
   * - 调用该方法不会立即执行排序，延迟到实际遍历时执行
   * - 降序排序通过反转比较器的结果实现
   */
  <TKey extends Comparable<TKey>> OrderedEnumerable<T> thenByDescending( // 定义泛型方法，执行降序的后续排序，TKey必须实现Comparable接口
      Function1<T, TKey> keySelector); // 参数：键选择器函数，从元素中提取用于排序的键

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * descending order according to a key, using a specified comparator.
   * 根据指定的键和比较器，对序列中的元素执行降序的后续排序操作
   * 
   * 【方法作用详细说明】：
   * 这是一个灵活的排序方法，允许使用自定义比较器进行降序排序。
   * 该方法内部会调用createOrderedEnumerable方法，将descending参数设为true。
   * 
   * 【工作流程】：
   * 1. 接收一个键选择器函数，从元素中提取排序键
   * 2. 接收一个自定义比较器，用于比较键的大小
   * 3. 使用指定的比较器进行降序排序
   * 4. 返回一个新的OrderedEnumerable，包含原有的排序规则和新的降序排序规则
   * 
   * 【参数说明】：
   * @param <TKey> 排序键的类型，泛型参数，表示用于排序的键的类型
   * @param keySelector Function1<T, TKey> - 键选择器函数，从元素T中提取排序键TKey的函数
   * @param comparator Comparator<TKey> - 比较器，用于比较两个TKey类型对象的大小
   * 
   * 【返回值说明】：
   * @return OrderedEnumerable<T> - 返回一个新的有序可枚举对象，包含原有的排序规则和新添加的降序排序规则
   * 
   * 【使用示例】：
   * // 先按部门排序，再按姓名降序排序（使用自定义比较器，忽略大小写）
   * orderedEnumerable.thenByDescending(Person::getName, String.CASE_INSENSITIVE_ORDER)
   * 
   * 【与thenByDescending(无comparator版本)的区别】：
   * - 该方法允许使用自定义比较器，不要求键实现Comparable接口
   * - 该方法更灵活，可以定义复杂的比较逻辑
   * - 该方法固定为降序排序
   * 
   * 【适用场景】：
   * - 需要自定义比较逻辑且要降序排序时使用
   * - 键类型未实现Comparable接口时使用
   * - 需要特殊的排序规则时使用（如忽略大小写、按特定规则排序等）
   * 
   * 【实现细节】：
   * - 内部调用createOrderedEnumerable(keySelector, comparator, true)
   * - 比较器可以是null，此时使用键的自然顺序
   * - 调用该方法不会立即执行排序，延迟到实际遍历时执行
   * - 降序排序通过反转比较器的结果实现
   */
  <TKey> OrderedEnumerable<T> thenByDescending(Function1<T, TKey> keySelector, // 定义泛型方法，执行降序的后续排序，TKey是排序键的类型
      Comparator<TKey> comparator); // 参数：比较器，用于比较两个键的大小
} // 接口定义结束
