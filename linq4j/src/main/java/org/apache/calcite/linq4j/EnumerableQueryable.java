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
package org.apache.calcite.linq4j;

import org.apache.calcite.linq4j.function.BigDecimalFunction1;
import org.apache.calcite.linq4j.function.DoubleFunction1;
import org.apache.calcite.linq4j.function.EqualityComparer;
import org.apache.calcite.linq4j.function.FloatFunction1;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.function.Function2;
import org.apache.calcite.linq4j.function.IntegerFunction1;
import org.apache.calcite.linq4j.function.LongFunction1;
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1;
import org.apache.calcite.linq4j.function.NullableDoubleFunction1;
import org.apache.calcite.linq4j.function.NullableFloatFunction1;
import org.apache.calcite.linq4j.function.NullableIntegerFunction1;
import org.apache.calcite.linq4j.function.NullableLongFunction1;
import org.apache.calcite.linq4j.function.Predicate1;
import org.apache.calcite.linq4j.function.Predicate2;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.FunctionExpression;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Implementation of {@link Queryable} by a {@link Enumerable}.
 * 通过 {@link Enumerable} 实现 {@link Queryable} 接口的类
 * 这个类是 LINQ4J 框架中的核心实现之一，它将 Enumerable（可枚举集合）包装成 Queryable（可查询集合）
 * Queryable 接口支持表达式树（Expression Tree）的构建和执行，这使得查询可以被延迟执行、优化和转换为其他形式（如 SQL）
 * 与普通的 Enumerable 不同，Queryable 可以记录查询操作的完整表达式树，从而支持更高级的查询优化和远程执行
 * 这个类继承自 DefaultEnumerable，提供了 Enumerable 接口的所有默认实现，同时实现了 Queryable 接口
 * 它是连接 LINQ 查询表达式和实际数据源之间的桥梁，支持在内存集合上执行类似数据库的查询操作
 *
 * @param <T> Element type 元素类型，表示集合中元素的 Java 类型
 */
class EnumerableQueryable<T> extends DefaultEnumerable<T>
    implements Queryable<T> {
  private final QueryProvider provider; // 查询提供者，负责执行查询操作，是 Queryable 接口的核心组件，提供了创建和执行查询的能力
  private final Class<T> elementType; // 元素类型，表示集合中元素的 Java 类型，用于类型安全和反射操作
  private final Enumerable<T> enumerable; // 底层的可枚举集合，实际存储和提供数据的对象，所有查询操作最终都会委托给这个对象执行
  private final @Nullable Expression expression; // 表达式树，表示构建当前查询的完整表达式链，可以为空，用于查询优化和远程执行

  EnumerableQueryable(QueryProvider provider, Class<T> elementType,
      @Nullable Expression expression, Enumerable<T> enumerable) {
    this.enumerable = enumerable; // 初始化底层的可枚举集合对象
    this.elementType = elementType; // 初始化元素类型
    this.provider = provider; // 初始化查询提供者
    this.expression = expression; // 初始化表达式树，可能为空
  }

  @Override protected Enumerable<T> getThis() {
    return enumerable; // 返回底层的可枚举集合对象，用于 DefaultEnumerable 中的默认实现调用
  }

  /**
   * Returns the target queryable. Usually this.
   * 返回目标可查询对象，通常是 this
   * 这个方法用于在某些操作中获取当前的可查询对象，通常返回自身
   *
   * @return Target queryable 目标可查询对象
   */
    protected Queryable<T> queryable() {
      return this; // 返回当前对象本身
    }
  @Override public Iterator<T> iterator() {
    return enumerable.iterator(); // 返回底层可枚举集合的迭代器，用于遍历集合中的元素
  }

  @Override public Enumerator<T> enumerator() {
    return enumerable.enumerator(); // 返回底层可枚举集合的枚举器，Enumerator 是 LINQ4J 特有的迭代器接口
  }

  // Disambiguate 消除歧义，以下是集合操作方法的实现

  @Override public Queryable<T> union(Enumerable<T> source1) {
    return EnumerableDefaults.union(getThis(), source1).asQueryable(); // 计算两个集合的并集，去重后返回可查询对象
  }

  @Override public Queryable<T> union(Enumerable<T> source1,
      EqualityComparer<T> comparer) {
    return EnumerableDefaults.union(getThis(), source1, comparer).asQueryable(); // 使用自定义比较器计算两个集合的并集
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1) {
    return intersect(source1, false); // 计算两个集合的交集，默认不保留重复元素
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1, boolean all) {
    return EnumerableDefaults.intersect(getThis(), source1, all).asQueryable(); // 计算两个集合的交集，all 参数控制是否保留重复元素
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1,
      EqualityComparer<T> comparer) {
    return intersect(source1, comparer, false); // 使用自定义比较器计算两个集合的交集，默认不保留重复元素
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1,
      EqualityComparer<T> comparer, boolean all) {
    return EnumerableDefaults.intersect(getThis(), source1, comparer, all)
        .asQueryable(); // 使用自定义比较器计算两个集合的交集，all 参数控制是否保留重复元素
  }

  @Override public Queryable<T> except(Enumerable<T> enumerable1,
      EqualityComparer<T> comparer) {
    return except(enumerable1, comparer, false); // 计算差集（当前集合减去另一个集合），使用自定义比较器，默认不保留重复元素
  }

  @Override public Queryable<T> except(Enumerable<T> enumerable1,
      EqualityComparer<T> comparer, boolean all) {
    return EnumerableDefaults.except(getThis(), enumerable1, comparer, all)
        .asQueryable(); // 计算差集，使用自定义比较器，all 参数控制是否保留重复元素
  }

  @Override public Queryable<T> except(Enumerable<T> enumerable1) {
    return except(enumerable1, false); // 计算差集（当前集合减去另一个集合），默认不保留重复元素
  }

  @Override public Queryable<T> except(Enumerable<T> enumerable1, boolean all) {
    return EnumerableDefaults.except(getThis(), enumerable1, all).asQueryable(); // 计算差集，all 参数控制是否保留重复元素
  }

  @Override public Queryable<T> take(int count) {
    return EnumerableDefaults.take(getThis(), count).asQueryable(); // 从集合开头返回指定数量的元素
  }

  @Override public Queryable<T> skip(int count) {
    return EnumerableDefaults.skip(getThis(), count).asQueryable(); // 跳过集合开头指定数量的元素，返回剩余元素
  }

  @Override public Queryable<T> reverse() {
    return EnumerableDefaults.reverse(getThis()).asQueryable(); // 反转集合中元素的顺序
  }

  @Override public Queryable<T> distinct() {
    return EnumerableDefaults.distinct(getThis()).asQueryable(); // 去除集合中的重复元素
  }

  @Override public Queryable<T> distinct(EqualityComparer<T> comparer) {
    return EnumerableDefaults.distinct(getThis(), comparer).asQueryable(); // 使用自定义比较器去除集合中的重复元素
  }

  @Override public <TResult> Queryable<TResult> ofType(Class<TResult> clazz) {
    return EnumerableDefaults.ofType(getThis(), clazz).asQueryable(); // 筛选出指定类型的元素
  }

  @Override public Queryable<@Nullable T> defaultIfEmpty() {
    return EnumerableDefaults.defaultIfEmpty(getThis()).asQueryable(); // 如果集合为空，返回包含单个 null 元素的集合
  }

  @Override public <T2> Queryable<T2> cast(Class<T2> clazz) {
    return EnumerableDefaults.cast(getThis(), clazz).asQueryable(); // 将集合中的元素强制转换为指定类型
  }

  // Queryable methods Queryable 接口的核心方法实现

  @Override public Type getElementType() {
    return elementType; // 返回集合中元素的类型
  }

  @Override public @Nullable Expression getExpression() {
    return expression; // 返回构建当前查询的表达式树，可能为空
  }

  @Override public QueryProvider getProvider() {
    return provider; // 返回查询提供者，用于执行查询操作
  }

  // ............. 以下是聚合操作方法的实现

  @Override public @Nullable T aggregate(
      FunctionExpression<Function2<@Nullable T, T, T>> selector) {
    return EnumerableDefaults.aggregate(getThis(), selector.getFunction()); // 对集合元素执行聚合操作，累加器函数将前一个结果和当前元素合并
  }

  @Override public <TAccumulate> TAccumulate aggregate(TAccumulate seed,
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> selector) {
    return EnumerableDefaults.aggregate(getThis(), seed,
        selector.getFunction()); // 使用指定的种子值对集合元素执行聚合操作
  }

  @Override public <TAccumulate, TResult> TResult aggregate(TAccumulate seed,
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func,
      FunctionExpression<Function1<TAccumulate, TResult>> selector) {
    return EnumerableDefaults.aggregate(getThis(), seed, func.getFunction(),
        selector.getFunction()); // 使用种子值执行聚合操作，然后将结果通过选择器函数转换为最终结果
  }

  @Override public boolean all(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.all(getThis(), predicate.getFunction()); // 判断集合中的所有元素是否都满足指定的谓词条件
  }

  @Override public boolean any(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.any(getThis(), predicate.getFunction()); // 判断集合中是否存在至少一个元素满足指定的谓词条件
  }

  @Override public BigDecimal averageBigDecimal(
      FunctionExpression<BigDecimalFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为 BigDecimal
  }

  @Override public BigDecimal averageNullableBigDecimal(
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为可空的 BigDecimal
  }

  @Override public double averageDouble(FunctionExpression<DoubleFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为 double
  }

  @Override public Double averageNullableDouble(
      FunctionExpression<NullableDoubleFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为可空的 Double
  }

  @Override public int averageInteger(FunctionExpression<IntegerFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为 int
  }

  @Override public Integer averageNullableInteger(
      FunctionExpression<NullableIntegerFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为可空的 Integer
  }

  @Override public float averageFloat(FunctionExpression<FloatFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为 float
  }

  @Override public Float averageNullableFloat(
      FunctionExpression<NullableFloatFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为可空的 Float
  }

  @Override public long averageLong(FunctionExpression<LongFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为 long
  }

  @Override public Long averageNullableLong(
      FunctionExpression<NullableLongFunction1<T>> selector) {
    return EnumerableDefaults.average(getThis(), selector.getFunction()); // 计算集合中元素的平均值，元素类型为可空的 Long
  }

  @Override public Queryable<T> concat(Enumerable<T> source2) {
    return EnumerableDefaults.concat(getThis(), source2).asQueryable(); // 将当前集合与另一个集合连接起来
  }

  @Override public int count(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.count(getThis(), predicate.getFunction()); // 统计满足指定谓词条件的元素数量
  }

  @Override public T first(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.first(getThis(), predicate.getFunction()); // 返回满足指定谓词条件的第一个元素，如果不存在则抛出异常
  }

  @Override public @Nullable T firstOrDefault(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.firstOrDefault(getThis(),
        predicate.getFunction()); // 返回满足指定谓词条件的第一个元素，如果不存在则返回默认值（null）
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy(
      FunctionExpression<Function1<T, TKey>> keySelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction())
        .asQueryable(); // 根据键选择器对元素进行分组，返回分组集合
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy(
      FunctionExpression<Function1<T, TKey>> keySelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction(),
        comparer).asQueryable(); // 使用自定义比较器根据键选择器对元素进行分组
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy(
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction(),
        elementSelector.getFunction()).asQueryable(); // 根据键选择器对元素进行分组，并使用元素选择器转换每个元素
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy(
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction(),
        elementSelector.getFunction(), comparer).asQueryable(); // 使用自定义比较器根据键选择器对元素进行分组，并使用元素选择器转换每个元素
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK(
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction(),
        resultSelector.getFunction()).asQueryable(); // 根据键选择器对元素进行分组，并使用结果选择器将每个分组转换为结果对象
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK(
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction(),
        resultSelector.getFunction(), comparer).asQueryable(); // 使用自定义比较器根据键选择器对元素进行分组，并使用结果选择器将每个分组转换为结果对象
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy(
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector,
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction(),
        elementSelector.getFunction(), resultSelector.getFunction())
        .asQueryable(); // 根据键选择器对元素进行分组，使用元素选择器转换每个元素，然后使用结果选择器将每个分组转换为结果对象
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy(
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector,
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector.getFunction(),
        elementSelector.getFunction(), resultSelector.getFunction(), comparer)
        .asQueryable(); // 使用自定义比较器根据键选择器对元素进行分组，使用元素选择器转换每个元素，然后使用结果选择器将每个分组转换为结果对象
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin(
      Enumerable<TInner> inner,
      FunctionExpression<Function1<T, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector) {
    return EnumerableDefaults.groupJoin(getThis(), inner,
        outerKeySelector.getFunction(), innerKeySelector.getFunction(),
        resultSelector.getFunction()).asQueryable(); // 基于键相等性将两个序列的元素进行关联，并对结果进行分组，类似于 SQL 的 LEFT OUTER JOIN，但将匹配的元素分组
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin(
      Enumerable<TInner> inner,
      FunctionExpression<Function1<T, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupJoin(getThis(), inner,
        outerKeySelector.getFunction(), innerKeySelector.getFunction(),
        resultSelector.getFunction(), comparer).asQueryable(); // 使用自定义比较器基于键相等性将两个序列的元素进行关联，并对结果进行分组
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join(
      Enumerable<TInner> inner,
      FunctionExpression<Function1<T, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector) {
    return EnumerableDefaults.hashJoin(getThis(), inner,
        outerKeySelector.getFunction(), innerKeySelector.getFunction(),
        resultSelector.getFunction()).asQueryable(); // 基于键相等性将两个序列的元素进行关联，类似于 SQL 的 INNER JOIN，使用哈希连接算法
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join(
      Enumerable<TInner> inner,
      FunctionExpression<Function1<T, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.hashJoin(getThis(), inner,
        outerKeySelector.getFunction(), innerKeySelector.getFunction(),
        resultSelector.getFunction(), comparer).asQueryable(); // 使用自定义比较器基于键相等性将两个序列的元素进行关联，使用哈希连接算法
  }

  @Override public T last(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.last(getThis(), predicate.getFunction()); // 返回满足指定谓词条件的最后一个元素，如果不存在则抛出异常
  }

  @Override public @Nullable T lastOrDefault(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.lastOrDefault(getThis(), predicate.getFunction()); // 返回满足指定谓词条件的最后一个元素，如果不存在则返回默认值（null）
  }

  @Override public long longCount(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.longCount(getThis(), predicate.getFunction()); // 统计满足指定谓词条件的元素数量，返回 long 类型
  }

  @Override public <TResult extends Comparable<TResult>> @Nullable TResult max(
      FunctionExpression<Function1<T, TResult>> selector) {
    return EnumerableDefaults.max(getThis(), selector.getFunction()); // 返回集合中的最大值，通过选择器函数提取比较值
  }

  @Override public <TResult extends Comparable<TResult>> @Nullable TResult min(
      FunctionExpression<Function1<T, TResult>> selector) {
    return EnumerableDefaults.min(getThis(), selector.getFunction()); // 返回集合中的最小值，通过选择器函数提取比较值
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderBy(
      FunctionExpression<Function1<T, TKey>> keySelector) {
    return EnumerableDefaults.asOrderedQueryable(
        EnumerableDefaults.orderBy(getThis(), keySelector.getFunction())); // 根据键选择器对元素进行升序排序，返回有序可查询对象
  }

  @Override public <TKey> OrderedQueryable<T> orderBy(
      FunctionExpression<Function1<T, TKey>> keySelector,
      Comparator<TKey> comparator) {
    return EnumerableDefaults.asOrderedQueryable(
        EnumerableDefaults.orderBy(getThis(), keySelector.getFunction(),
            comparator)); // 使用自定义比较器根据键选择器对元素进行升序排序
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderByDescending(
      FunctionExpression<Function1<T, TKey>> keySelector) {
    return EnumerableDefaults.asOrderedQueryable(
        EnumerableDefaults.orderByDescending(getThis(),
            keySelector.getFunction())); // 根据键选择器对元素进行降序排序，返回有序可查询对象
  }

  @Override public <TKey> OrderedQueryable<T> orderByDescending(
      FunctionExpression<Function1<T, TKey>> keySelector,
      Comparator<TKey> comparator) {
    return EnumerableDefaults.asOrderedQueryable(
        EnumerableDefaults.orderByDescending(getThis(),
            keySelector.getFunction(), comparator)); // 使用自定义比较器根据键选择器对元素进行降序排序
  }

  @Override public <TResult> Queryable<TResult> select(
      FunctionExpression<Function1<T, TResult>> selector) {
    return EnumerableDefaults.select(getThis(), selector.getFunction())
        .asQueryable(); // 将每个元素通过选择器函数转换为新的形式
  }

  @Override public <TResult> Queryable<TResult> selectN(
      FunctionExpression<Function2<T, Integer, TResult>> selector) {
    return EnumerableDefaults.select(getThis(), selector.getFunction())
        .asQueryable(); // 将每个元素和其索引通过选择器函数转换为新的形式
  }

  @Override public <TResult> Queryable<TResult> selectMany(
      FunctionExpression<Function1<T, Enumerable<TResult>>> selector) {
    return EnumerableDefaults.selectMany(getThis(), selector.getFunction())
        .asQueryable(); // 将每个元素通过选择器函数映射到一个序列，然后将这些序列合并为一个序列
  }

  @Override public <TResult> Queryable<TResult> selectManyN(
      FunctionExpression<Function2<T, Integer, Enumerable<TResult>>> selector) {
    return EnumerableDefaults.selectMany(getThis(), selector.getFunction())
        .asQueryable(); // 将每个元素和其索引通过选择器函数映射到一个序列，然后将这些序列合并为一个序列
  }

  @Override public <TCollection, TResult> Queryable<TResult> selectMany(
      FunctionExpression<Function2<T, Integer, Enumerable<TCollection>>>
        collectionSelector,
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) {
    return EnumerableDefaults.selectMany(getThis(),
        collectionSelector.getFunction(), resultSelector.getFunction())
        .asQueryable(); // 将每个元素和其索引通过集合选择器映射到一个序列，然后将这些序列的元素通过结果选择器转换为最终结果
  }

  @Override public <TCollection, TResult> Queryable<TResult> selectManyN(
      FunctionExpression<Function1<T, Enumerable<TCollection>>>
        collectionSelector,
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) {
    return EnumerableDefaults.selectMany(getThis(),
        collectionSelector.getFunction(), resultSelector.getFunction())
        .asQueryable(); // 将每个元素通过集合选择器映射到一个序列，然后将这些序列的元素通过结果选择器转换为最终结果
  }

  @Override public T single(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.single(getThis(), predicate.getFunction()); // 返回满足指定谓词条件的唯一元素，如果没有元素或多个元素满足条件则抛出异常
  }

  @Override public @Nullable T singleOrDefault(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.singleOrDefault(getThis(),
        predicate.getFunction()); // 返回满足指定谓词条件的唯一元素，如果没有元素则返回默认值（null），如果有多个元素则抛出异常
  }

  @Override public Queryable<T> skipWhile(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.skipWhile(getThis(), predicate.getFunction())
        .asQueryable(); // 跳过满足指定谓词条件的元素，从第一个不满足条件的元素开始返回剩余元素
  }

  @Override public Queryable<T> skipWhileN(
      FunctionExpression<Predicate2<T, Integer>> predicate) {
    return EnumerableDefaults.skipWhile(getThis(), predicate.getFunction())
        .asQueryable(); // 跳过满足指定谓词条件的元素（谓词包含元素索引），从第一个不满足条件的元素开始返回剩余元素
  }

  @Override public BigDecimal sumBigDecimal(
      FunctionExpression<BigDecimalFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为 BigDecimal
  }

  @Override public BigDecimal sumNullableBigDecimal(
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为可空的 BigDecimal
  }

  @Override public double sumDouble(FunctionExpression<DoubleFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为 double
  }

  @Override public Double sumNullableDouble(
      FunctionExpression<NullableDoubleFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为可空的 Double
  }

  @Override public int sumInteger(FunctionExpression<IntegerFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为 int
  }

  @Override public Integer sumNullableInteger(
      FunctionExpression<NullableIntegerFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为可空的 Integer
  }

  @Override public long sumLong(FunctionExpression<LongFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为 long
  }

  @Override public Long sumNullableLong(
      FunctionExpression<NullableLongFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为可空的 Long
  }

  @Override public float sumFloat(FunctionExpression<FloatFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为 float
  }

  @Override public Float sumNullableFloat(
      FunctionExpression<NullableFloatFunction1<T>> selector) {
    return EnumerableDefaults.sum(getThis(), selector.getFunction()); // 计算集合中元素的总和，元素类型为可空的 Float
  }

  @Override public Queryable<T> takeWhile(FunctionExpression<Predicate1<T>> predicate) {
    return EnumerableDefaults.takeWhile(getThis(), predicate.getFunction())
        .asQueryable(); // 返回满足指定谓词条件的元素，从第一个不满足条件的元素开始停止
  }

  @Override public Queryable<T> takeWhileN(
      FunctionExpression<Predicate2<T, Integer>> predicate) {
    return EnumerableDefaults.takeWhile(getThis(), predicate.getFunction())
        .asQueryable(); // 返回满足指定谓词条件的元素（谓词包含元素索引），从第一个不满足条件的元素开始停止
  }

  @Override public Queryable<T> where(
      FunctionExpression<? extends Predicate1<T>> predicate) {
    return EnumerableDefaults.where(getThis(), predicate.getFunction())
        .asQueryable(); // 根据指定的谓词条件筛选集合中的元素
  }

  @Override public Queryable<T> whereN(
      FunctionExpression<? extends Predicate2<T, Integer>> predicate) {
    return EnumerableDefaults.where(getThis(), predicate.getFunction())
        .asQueryable(); // 根据指定的谓词条件（包含元素索引）筛选集合中的元素
  }

  @Override public <T1, TResult> Queryable<TResult> zip(Enumerable<T1> source1,
      FunctionExpression<Function2<T, T1, TResult>> resultSelector) {
    return EnumerableDefaults.zip(getThis(), source1,
        resultSelector.getFunction()).asQueryable(); // 将当前集合与另一个集合按位置合并，使用结果选择器函数将每对元素转换为结果
  }

  @Override public @Nullable T aggregate(Function2<@Nullable T, T, T> func) {
    return EnumerableDefaults.aggregate(getThis(), func); // 对集合元素执行聚合操作，累加器函数将前一个结果和当前元素合并
  }


  @Override public <TAccumulate, TResult> TResult aggregate(TAccumulate seed,
      Function2<TAccumulate, T, TAccumulate> func,
      Function1<TAccumulate, TResult> selector) {
    return EnumerableDefaults.aggregate(getThis(), seed, func, selector); // 使用种子值执行聚合操作，然后将结果通过选择器函数转换为最终结果
  }
} // 类定义结束
