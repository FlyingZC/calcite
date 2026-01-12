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
package org.apache.calcite.linq4j; // 定义包名，该接口属于org.apache.calcite.linq4j包，这是Calcite项目中LINQ4J（Language Integrated Query for Java）模块的核心包之一

import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口，用于表示接受一个参数并返回结果的函数
import org.apache.calcite.linq4j.tree.FunctionExpression; // 导入FunctionExpression类，用于表示函数表达式，是LINQ4J中表达式树的核心组成部分

import java.util.Comparator; // 导入Comparator接口，用于自定义比较逻辑，支持非Comparable类型的排序

/**
 * Extension methods for {@link OrderedQueryable}.
 * 这是OrderedQueryable接口的扩展方法接口，提供多级排序功能
 * OrderedQueryable表示已经按照某个键排序的可查询序列，而此接口允许在此基础上进行后续的二级、三级等多级排序
 * 例如：先按年龄排序，再按姓名排序，最后按地址排序，就需要使用thenBy和thenByDescending方法来实现多级排序
 *
 * @param <T> Element type // 泛型参数T表示序列中元素的类型，例如String、Integer或自定义的Employee对象等
 */
public interface ExtendedOrderedQueryable<T> extends Queryable<T> { // 定义ExtendedOrderedQueryable接口，继承自Queryable<T>，表示这是一个可查询的有序序列接口
  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key.
   * 对序列中的元素执行后续的升序排序，根据指定的键选择器函数提取排序键
   * 这是多级排序的核心方法，通常在orderBy或orderByDescending之后调用，用于实现二级、三级等后续排序
   * 例如：query.orderBy(e -> e.getAge()).thenBy(e -> e.getName()) 表示先按年龄升序排序，年龄相同的再按姓名升序排序
   *
   * @param <TKey> 键的类型，必须实现Comparable接口以便进行自然排序，例如String、Integer等
   * @param keySelector 键选择器函数表达式，用于从元素T中提取排序键TKey，例如 e -> e.getName() 提取姓名作为排序键
   * @return 返回一个新的OrderedQueryable<T>对象，该对象包含按照当前排序规则排序后的元素序列
   */
  <TKey extends Comparable<TKey>> OrderedQueryable<T> thenBy( // 定义thenBy方法，接受一个键选择器函数表达式，返回排序后的OrderedQueryable
      FunctionExpression<Function1<T, TKey>> keySelector); // 参数keySelector是函数表达式，用于从元素T中提取Comparable类型的键TKey，用于后续的升序排序

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key, using a specified comparator.
   * 对序列中的元素执行后续的升序排序，根据指定的键选择器函数提取排序键，并使用自定义的比较器进行排序
   * 与上一个thenBy方法不同，此方法允许使用自定义的Comparator来定义排序规则，不要求键类型必须实现Comparable接口
   * 例如：可以使用自定义的Comparator来实现不区分大小写的字符串排序，或者按照特定的业务规则排序
   *
   * @param <TKey> 键的类型，可以是任意类型，因为排序逻辑由comparator参数控制
   * @param keySelector 键选择器函数表达式，用于从元素T中提取排序键TKey，例如 e -> e.getDepartment() 提取部门作为排序键
   * @param comparator 自定义比较器，用于定义键TKey的比较规则，例如 String.CASE_INSENSITIVE_ORDER 实现不区分大小写的排序
   * @return 返回一个新的OrderedQueryable<T>对象，该对象包含按照当前排序规则排序后的元素序列
   */
  <TKey> OrderedQueryable<T> thenBy( // 定义thenBy方法，接受键选择器函数表达式和自定义比较器，返回排序后的OrderedQueryable
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数keySelector是函数表达式，用于从元素T中提取键TKey
      Comparator<TKey> comparator); // 参数comparator是自定义比较器，用于定义键TKey的排序规则，支持非Comparable类型的排序

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * descending order according to a key.
   * 对序列中的元素执行后续的降序排序，根据指定的键选择器函数提取排序键
   * 与thenBy方法类似，但排序方向相反，用于实现降序的多级排序
   * 例如：query.orderBy(e -> e.getAge()).thenByDescending(e -> e.getSalary()) 表示先按年龄升序排序，年龄相同的再按工资降序排序
   * 这种组合排序在实际业务中非常常见，例如先按主要指标升序，再按次要指标降序
   *
   * @param <TKey> 键的类型，必须实现Comparable接口以便进行自然排序，例如String、Integer等
   * @param keySelector 键选择器函数表达式，用于从元素T中提取排序键TKey，例如 e -> e.getScore() 提取分数作为排序键
   * @return 返回一个新的OrderedQueryable<T>对象，该对象包含按照当前降序排序规则排序后的元素序列
   */
  <TKey extends Comparable<TKey>> OrderedQueryable<T> thenByDescending( // 定义thenByDescending方法，接受一个键选择器函数表达式，返回降序排序后的OrderedQueryable
      FunctionExpression<Function1<T, TKey>> keySelector); // 参数keySelector是函数表达式，用于从元素T中提取Comparable类型的键TKey，用于后续的降序排序

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * descending order according to a key, using a specified comparator.
   * 对序列中的元素执行后续的降序排序，根据指定的键选择器函数提取排序键，并使用自定义的比较器进行排序
   * 与thenByDescending方法类似，但允许使用自定义的Comparator来定义排序规则，不要求键类型必须实现Comparable接口
   * 例如：可以使用自定义的Comparator来实现按照特定业务规则的降序排序
   *
   * @param <TKey> 键的类型，可以是任意类型，因为排序逻辑由comparator参数控制
   * @param keySelector 键选择器函数表达式，用于从元素T中提取排序键TKey，例如 e -> e.getPriority() 提取优先级作为排序键
   * @param comparator 自定义比较器，用于定义键TKey的比较规则，注意降序排序通常需要反转comparator的比较逻辑
   * @return 返回一个新的OrderedQueryable<T>对象，该对象包含按照当前降序排序规则排序后的元素序列
   */
  <TKey> OrderedQueryable<T> thenByDescending( // 定义thenByDescending方法，接受键选择器函数表达式和自定义比较器，返回降序排序后的OrderedQueryable
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数keySelector是函数表达式，用于从元素T中提取键TKey
      Comparator<TKey> comparator); // 参数comparator是自定义比较器，用于定义键TKey的排序规则，支持非Comparable类型的降序排序
} // 接口定义结束，ExtendedOrderedQueryable接口提供了四个核心方法用于多级排序，支持升序和降序，支持自然排序和自定义比较器排序
