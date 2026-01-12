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
package org.apache.calcite.linq4j; // 包声明：org.apache.calcite.linq4j，属于Calcite的LINQ4J模块

import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数接口，表示接受一个参数并返回结果的函数
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression表达式类，用于表示表达式树中的节点
import org.apache.calcite.linq4j.tree.FunctionExpression; // 导入FunctionExpression函数表达式类，用于表示Lambda表达式

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数

import java.util.Comparator; // 导入Comparator比较器接口，用于自定义排序规则

/**
 * Implementation of {@link OrderedQueryable} by an // 类注释：这是OrderedQueryable接口的实现类，基于Enumerable实现
 * {@link org.apache.calcite.linq4j.Enumerable}. // 该类为已排序的可查询集合提供了LINQ风格的操作能力
 *
 * @param <T> Element type // 泛型参数T：表示集合中元素的类型
 */
class EnumerableOrderedQueryable<T> extends EnumerableQueryable<T> // 类声明：继承自EnumerableQueryable<T>基类，实现OrderedQueryable<T>接口
    implements OrderedQueryable<T> { // 实现OrderedQueryable接口，提供有序查询功能
  EnumerableOrderedQueryable(Enumerable<T> enumerable, Class<T> rowType, // 构造方法：创建一个有序可查询对象，参数包括可枚举集合、元素类型、查询提供者和可选的表达式
      QueryProvider provider, @Nullable Expression expression) { // QueryProvider：查询提供者，用于执行查询操作；Expression：可选的表达式，用于表示查询的原始形式
    super(provider, rowType, expression, enumerable); // 调用父类EnumerableQueryable的构造方法，初始化查询提供者、元素类型、表达式和可枚举集合
  } // 构造方法结束

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenBy( // 方法：thenBy，对已排序的序列进行次要升序排序，TKey必须是Comparable类型
      FunctionExpression<Function1<T, TKey>> keySelector) { // 参数：keySelector，一个函数表达式，用于从元素中提取排序键
    return QueryableDefaults.thenBy(asOrderedQueryable(), keySelector); // 调用QueryableDefaults的thenBy方法，传入当前有序查询对象和键选择器，返回新的有序查询对象
  } // 方法结束

  @Override public <TKey> OrderedQueryable<T> thenBy( // 方法：thenBy，对已排序的序列进行次要升序排序，使用自定义比较器
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：keySelector，一个函数表达式，用于从元素中提取排序键
      Comparator<TKey> comparator) { // 参数：comparator，自定义比较器，用于比较排序键
    return QueryableDefaults.thenBy(asOrderedQueryable(), keySelector, // 调用QueryableDefaults的thenBy方法，传入当前有序查询对象、键选择器和比较器，返回新的有序查询对象
        comparator); // 传入比较器参数
  } // 方法结束

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenByDescending( // 方法：thenByDescending，对已排序的序列进行次要降序排序，TKey必须是Comparable类型
      FunctionExpression<Function1<T, TKey>> keySelector) { // 参数：keySelector，一个函数表达式，用于从元素中提取排序键
    return QueryableDefaults.thenByDescending(asOrderedQueryable(), // 调用QueryableDefaults的thenByDescending方法，传入当前有序查询对象和键选择器，返回新的有序查询对象
        keySelector); // 传入键选择器参数
  } // 方法结束

  @Override public <TKey> OrderedQueryable<T> thenByDescending( // 方法：thenByDescending，对已排序的序列进行次要降序排序，使用自定义比较器
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：keySelector，一个函数表达式，用于从元素中提取排序键
      Comparator<TKey> comparator) { // 参数：comparator，自定义比较器，用于比较排序键
    return QueryableDefaults.thenByDescending(asOrderedQueryable(), keySelector, // 调用QueryableDefaults的thenByDescending方法，传入当前有序查询对象、键选择器和比较器，返回新的有序查询对象
        comparator); // 传入比较器参数
  } // 方法结束
} // 类结束
