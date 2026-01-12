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
package org.apache.calcite.linq4j; // DefaultEnumerable类所在的包，属于Calcite LINQ4J框架的核心包

import org.apache.calcite.linq4j.function.BigDecimalFunction1; // 导入BigDecimal类型的选择器函数接口，用于聚合操作
import org.apache.calcite.linq4j.function.DoubleFunction1; // 导入double类型的选择器函数接口，用于聚合操作
import org.apache.calcite.linq4j.function.EqualityComparer; // 导入相等比较器接口，用于自定义元素比较逻辑
import org.apache.calcite.linq4j.function.FloatFunction1; // 导入float类型的选择器函数接口，用于聚合操作
import org.apache.calcite.linq4j.function.Function0; // 导入无参数函数接口，用于初始化累加器等场景
import org.apache.calcite.linq4j.function.Function1; // 导入单参数函数接口，用于元素转换、选择等操作
import org.apache.calcite.linq4j.function.Function2; // 导入双参数函数接口，用于聚合、连接等操作
import org.apache.calcite.linq4j.function.IntegerFunction1; // 导入int类型的选择器函数接口，用于聚合操作
import org.apache.calcite.linq4j.function.LongFunction1; // 导入long类型的选择器函数接口，用于聚合操作
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1; // 导入可空BigDecimal类型的选择器函数接口
import org.apache.calcite.linq4j.function.NullableDoubleFunction1; // 导入可空Double类型的选择器函数接口
import org.apache.calcite.linq4j.function.NullableFloatFunction1; // 导入可空Float类型的选择器函数接口
import org.apache.calcite.linq4j.function.NullableIntegerFunction1; // 导入可空Integer类型的选择器函数接口
import org.apache.calcite.linq4j.function.NullableLongFunction1; // 导入可空Long类型的选择器函数接口
import org.apache.calcite.linq4j.function.Predicate1; // 导入单参数谓词接口，用于过滤操作
import org.apache.calcite.linq4j.function.Predicate2; // 导入双参数谓词接口，用于带索引的过滤操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的返回值
import org.checkerframework.checker.nullness.qual.PolyNull; // 导入多态可空类型注解，用于根据上下文确定是否可空

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的数值计算
import java.util.Collection; // 导入Collection接口，用于into和removeAll等操作
import java.util.Comparator; // 导入Comparator接口，用于自定义排序逻辑
import java.util.List; // 导入List接口，用于toList方法
import java.util.Map; // 导入Map接口，用于toMap方法

/**
 * DefaultEnumerable类是Calcite LINQ4J框架中Enumerable接口的默认抽象实现类
 * 该类提供了LINQ(Language Integrated Query)风格的集合操作方法的默认实现
 * 通过委托调用Extensions类或EnumerableDefaults类来实现各种查询操作
 * 
 * 核心职责：
 * 1. 为所有Enumerable接口的扩展方法提供默认实现
 * 2. 作为其他具体Enumerable实现类的基类
 * 3. 提供统一的LINQ查询操作入口
 * 
 * 设计模式：
 * - 模板方法模式：定义了抽象方法enumerator()和iterator()，由子类实现具体的数据遍历逻辑
 * - 委托模式：将具体的查询操作委托给EnumerableDefaults类执行
 * 
 * 使用场景：
 * - 当需要实现自定义的Enumerable时，可以继承此类并实现enumerator()或iterator()方法
 * - 提供了丰富的集合操作方法，如过滤、投影、排序、分组、聚合等
 * 
 * 抽象方法：
 * - enumerator(): 返回一个Enumerator对象，用于遍历集合元素
 * - iterator(): 返回一个Java Iterator对象，用于遍历集合元素
 * 子类可以实现其中一个方法，然后基于该方法实现另一个方法
 * 
 * @param <T> 集合中元素的类型，支持泛型，可以存储任意类型的对象
 */
public abstract class DefaultEnumerable<T> implements OrderedEnumerable<T> {

  /**
   * 获取当前Enumerable对象的引用
   * 
   * 设计目的：
   * - 提供一个可重写的方法，允许派生类返回"外部"的Enumerable对象
   * - 在链式调用中保持正确的上下文引用
   * - 支持装饰器模式，允许在包装类中返回被包装的对象
   * 
   * 使用场景：
   * - 当使用装饰器模式包装Enumerable时，可以重写此方法返回原始对象
   * - 在某些复杂的查询场景中，需要返回外层上下文而非当前对象
   * 
   * @return 当前Enumerable对象的引用，默认返回this
   */
  protected Enumerable<T> getThis() {
    return this;
  }

  /**
   * 获取当前OrderedEnumerable对象的引用
   * 
   * 设计目的：
   * - 提供一个可重写的方法，允许派生类返回"外部"的OrderedEnumerable对象
   * - 在有序集合的链式调用中保持正确的上下文引用
   * - 支持装饰器模式，允许在包装类中返回被包装的有序对象
   * 
   * 与getThis()的区别：
   * - 返回类型是OrderedEnumerable而非普通的Enumerable
   * - 用于需要保持排序信息的场景
   * 
   * 使用场景：
   * - 在orderBy、thenBy等排序操作后，需要返回有序的Enumerable
   * - 当使用装饰器模式包装OrderedEnumerable时，可以重写此方法
   * 
   * @return 当前OrderedEnumerable对象的引用，默认返回this
   */
  protected OrderedEnumerable<T> getThisOrdered() {
    return this;
  }

  @Override public <R> @Nullable R foreach(Function1<T, R> func) {
    R result = null; // 初始化结果变量，用于存储最后一次函数调用的返回值
    try (Enumerator<T> enumerator = enumerator()) { // 获取枚举器，使用try-with-resources确保资源自动释放
      while (enumerator.moveNext()) { // 遍历集合中的每个元素，moveNext()移动到下一个元素
        T t = enumerator.current(); // 获取当前元素
        result = func.apply(t); // 对当前元素应用函数，更新结果值
      }
      return result; // 返回最后一次函数调用的结果，如果集合为空则返回null
    }
  }

  @Override public Queryable<T> asQueryable() {
    return Extensions.asQueryable(this); // 将当前Enumerable转换为Queryable对象，支持表达式树和延迟查询
  }

  // 注意：此方法不是Queryable接口的一部分，是内部使用的辅助方法
  protected OrderedQueryable<T> asOrderedQueryable() {
    return EnumerableDefaults.asOrderedQueryable(this); // 将当前Enumerable转换为有序的Queryable对象，保持排序信息
  }

  @Override public @Nullable T aggregate(Function2<@Nullable T, T, T> func) {
    return EnumerableDefaults.aggregate(getThis(), func); // 对集合中的元素进行聚合操作，使用累加函数依次处理每个元素
  }

  @Override public <TAccumulate> @PolyNull TAccumulate aggregate(@PolyNull TAccumulate seed,
      Function2<@PolyNull TAccumulate, T, @PolyNull TAccumulate> func) {
    return EnumerableDefaults.aggregate(getThis(), seed, func); // 使用指定的种子值作为初始值，对集合进行聚合操作
  }

  @Override public <TAccumulate, TResult> TResult aggregate(TAccumulate seed,
      Function2<TAccumulate, T, TAccumulate> func,
      Function1<TAccumulate, TResult> selector) {
    return EnumerableDefaults.aggregate(getThis(), seed, func, selector); // 聚合操作后，通过选择器函数转换最终结果
  }

  @Override public boolean all(Predicate1<T> predicate) {
    return EnumerableDefaults.all(getThis(), predicate); // 检查集合中的所有元素是否都满足指定的谓词条件
  }

  @Override public boolean any() {
    return EnumerableDefaults.any(getThis()); // 检查集合中是否包含任何元素
  }

  @Override public boolean any(Predicate1<T> predicate) {
    return EnumerableDefaults.any(getThis(), predicate); // 检查集合中是否存在满足指定谓词条件的元素
  }

  @Override public Enumerable<T> asEnumerable() {
    return EnumerableDefaults.asEnumerable(getThis()); // 返回当前对象本身，确保类型为Enumerable，用于类型转换
  }

  @Override public BigDecimal average(BigDecimalFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中元素的BigDecimal类型平均值
  }

  @Override public BigDecimal average(NullableBigDecimalFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中可空BigDecimal类型元素的平均值
  }

  @Override public double average(DoubleFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中元素的double类型平均值
  }

  @Override public Double average(NullableDoubleFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中可空Double类型元素的平均值
  }

  @Override public int average(IntegerFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中元素的int类型平均值
  }

  @Override public Integer average(NullableIntegerFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中可空Integer类型元素的平均值
  }

  @Override public long average(LongFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中元素的long类型平均值
  }

  @Override public Long average(NullableLongFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中可空Long类型元素的平均值
  }

  @Override public float average(FloatFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中元素的float类型平均值
  }

  @Override public Float average(NullableFloatFunction1<T> selector) {
    return EnumerableDefaults.average(getThis(), selector); // 计算集合中可空Float类型元素的平均值
  }

  @Override public <T2> Enumerable<T2> cast(Class<T2> clazz) {
    return EnumerableDefaults.cast(getThis(), clazz); // 将集合中的元素转换为指定的类型，如果转换失败则抛出异常
  }

  @Override public Enumerable<T> concat(Enumerable<T> enumerable1) {
    return EnumerableDefaults.concat(getThis(), enumerable1); // 将当前集合与另一个集合连接，返回包含所有元素的新集合
  }

  @Override public boolean contains(T element) {
    return EnumerableDefaults.contains(getThis(), element); // 检查集合中是否包含指定的元素，使用默认的相等比较器
  }

  @Override public boolean contains(T element, EqualityComparer<T> comparer) {
    return EnumerableDefaults.contains(getThis(), element, comparer); // 使用指定的相等比较器检查集合中是否包含指定元素
  }

  @Override public int count() {
    return EnumerableDefaults.count(getThis()); // 返回集合中元素的总数
  }

  @Override public int count(Predicate1<T> predicate) {
    return EnumerableDefaults.count(getThis(), predicate); // 返回集合中满足指定谓词条件的元素数量
  }

  @Override public <TKey> OrderedEnumerable<T> createOrderedEnumerable(
      Function1<T, TKey> keySelector, Comparator<TKey> comparator,
      boolean descending) {
    return EnumerableDefaults.createOrderedEnumerable(getThisOrdered(),
        keySelector, comparator, descending); // 创建有序的可枚举集合，根据指定的键选择器、比较器和排序方向
  }

  @Override public Enumerable<@Nullable T> defaultIfEmpty() {
    return EnumerableDefaults.defaultIfEmpty(getThis()); // 如果集合为空，则返回包含单个null元素的集合；否则返回原集合
  }

  @Override public Enumerable<@PolyNull T> defaultIfEmpty(@PolyNull T value) {
    return EnumerableDefaults.defaultIfEmpty(getThis(), value); // 如果集合为空，则返回包含指定值的集合；否则返回原集合
  }

  @Override public Enumerable<T> distinct() {
    return EnumerableDefaults.distinct(getThis()); // 返回去重后的集合，移除重复元素，使用默认的相等比较器
  }

  @Override public Enumerable<T> distinct(EqualityComparer<T> comparer) {
    return EnumerableDefaults.distinct(getThis(), comparer); // 使用指定的相等比较器返回去重后的集合
  }

  @Override public T elementAt(int index) {
    return EnumerableDefaults.elementAt(getThis(), index); // 返回集合中指定索引位置的元素，如果索引越界则抛出异常
  }

  @Override public @Nullable T elementAtOrDefault(int index) {
    return EnumerableDefaults.elementAtOrDefault(getThis(), index); // 返回集合中指定索引位置的元素，如果索引越界则返回默认值null
  }

  @Override public Enumerable<T> except(Enumerable<T> enumerable1) {
    return except(enumerable1, false); // 返回在当前集合中但不在指定集合中的元素，不包含重复元素
  }

  @Override public Enumerable<T> except(Enumerable<T> enumerable1, boolean all) {
    return EnumerableDefaults.except(getThis(), enumerable1, all); // 返回差集，all参数控制是否包含重复元素
  }

  @Override public Enumerable<T> except(Enumerable<T> enumerable1,
      EqualityComparer<T> comparer) {
    return except(enumerable1, comparer, false); // 使用指定的相等比较器返回差集，不包含重复元素
  }

  @Override public Enumerable<T> except(Enumerable<T> enumerable1,
      EqualityComparer<T> comparer, boolean all) {
    return EnumerableDefaults.except(getThis(), enumerable1, comparer, all); // 使用指定的相等比较器返回差集，all参数控制是否包含重复元素
  }

  @Override public T first() {
    return EnumerableDefaults.first(getThis()); // 返回集合中的第一个元素，如果集合为空则抛出异常
  }

  @Override public T first(Predicate1<T> predicate) {
    return EnumerableDefaults.first(getThis(), predicate); // 返回集合中满足谓词条件的第一个元素，如果没有则抛出异常
  }

  @Override public @Nullable T firstOrDefault() {
    return EnumerableDefaults.firstOrDefault(getThis()); // 返回集合中的第一个元素，如果集合为空则返回默认值null
  }

  @Override public @Nullable T firstOrDefault(Predicate1<T> predicate) {
    return EnumerableDefaults.firstOrDefault(getThis(), predicate); // 返回集合中满足谓词条件的第一个元素，如果没有则返回默认值null
  }

  @Override public <TKey> Enumerable<Grouping<TKey, T>> groupBy(
      Function1<T, TKey> keySelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector); // 根据指定的键选择器对集合元素进行分组，返回分组结果
  }

  @Override public <TKey> Enumerable<Grouping<TKey, T>> groupBy(
      Function1<T, TKey> keySelector, EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector, comparer); // 使用指定的相等比较器对集合元素进行分组
  }

  @Override public <TKey, TElement> Enumerable<Grouping<TKey, TElement>> groupBy(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector, elementSelector); // 分组后使用元素选择器转换每个元素
  }

  @Override public <TKey, TElement> Enumerable<Grouping<TKey, TElement>> groupBy(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector, elementSelector,
        comparer); // 使用指定的相等比较器分组，并使用元素选择器转换元素
  }

  @Override public <TKey, TResult> Enumerable<TResult> groupBy(
      Function1<T, TKey> keySelector,
      Function2<TKey, Enumerable<T>, TResult> elementSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector, elementSelector,
        comparer); // 分组后使用结果选择器将每个分组转换为指定的结果类型
  }

  @Override public <TKey, TResult> Enumerable<TResult> groupBy(
      Function1<T, TKey> keySelector,
      Function2<TKey, Enumerable<T>, TResult> resultSelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector, resultSelector); // 分组后使用结果选择器转换分组结果
  }

  @Override public <TKey, TElement, TResult> Enumerable<TResult> groupBy(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector,
      Function2<TKey, Enumerable<TElement>, TResult> resultSelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector, elementSelector,
        resultSelector); // 分组、转换元素，然后使用结果选择器将分组转换为结果
  }

  @Override public <TKey, TElement, TResult> Enumerable<TResult> groupBy(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector,
      Function2<TKey, Enumerable<TElement>, TResult> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector, elementSelector,
        resultSelector, comparer); // 使用指定的相等比较器分组，转换元素，然后使用结果选择器
  }

  @Override public <TKey, TAccumulate, TResult> Enumerable<TResult> groupBy(
      Function1<T, TKey> keySelector,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, T, TAccumulate> accumulatorAdder,
      Function2<TKey, TAccumulate, TResult> resultSelector) {
    return EnumerableDefaults.groupBy(getThis(), keySelector,
        accumulatorInitializer, accumulatorAdder, resultSelector); // 分组并使用累加器对每个分组进行聚合操作
  }

  @Override public <TKey, TAccumulate, TResult> Enumerable<TResult> groupBy(
      Function1<T, TKey> keySelector,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, T, TAccumulate> accumulatorAdder,
      Function2<TKey, TAccumulate, TResult> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupBy(getThis(), keySelector,
        accumulatorInitializer, accumulatorAdder, resultSelector, comparer); // 使用指定的相等比较器分组，并使用累加器聚合
  }

  @Override public <TKey, TAccumulate, TResult> Enumerable<TResult> sortedGroupBy(
      Function1<T, TKey> keySelector,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, T, TAccumulate> accumulatorAdder,
      Function2<TKey, TAccumulate, TResult> resultSelector,
      Comparator<TKey> comparator) {
    return EnumerableDefaults.sortedGroupBy(
        getThis(), keySelector, accumulatorInitializer,
        accumulatorAdder, resultSelector, comparator); // 分组并使用累加器聚合，结果按键排序
  }

  @Override public <TInner, TKey, TResult> Enumerable<TResult> groupJoin(
      Enumerable<TInner> inner, Function1<T, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<T, Enumerable<TInner>, TResult> resultSelector) {
    return EnumerableDefaults.groupJoin(getThis(), inner, outerKeySelector,
        innerKeySelector, resultSelector); // 基于键匹配进行分组连接，将匹配的内部集合元素分组
  }

  @Override public <TInner, TKey, TResult> Enumerable<TResult> groupJoin(
      Enumerable<TInner> inner, Function1<T, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<T, Enumerable<TInner>, TResult> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.groupJoin(getThis(), inner, outerKeySelector,
        innerKeySelector, resultSelector, comparer); // 使用指定的相等比较器进行分组连接
  }

  @Override public Enumerable<T> intersect(Enumerable<T> enumerable1) {
    return intersect(enumerable1, false); // 返回两个集合的交集，不包含重复元素
  }

  @Override public Enumerable<T> intersect(Enumerable<T> enumerable1, boolean all) {
    return EnumerableDefaults.intersect(getThis(), enumerable1, all); // 返回两个集合的交集，all参数控制是否包含重复元素
  }

  @Override public Enumerable<T> intersect(Enumerable<T> enumerable1,
      EqualityComparer<T> comparer) {
    return intersect(enumerable1, comparer, false); // 使用指定的相等比较器返回交集，不包含重复元素
  }

  @Override public Enumerable<T> intersect(Enumerable<T> enumerable1,
      EqualityComparer<T> comparer, boolean all) {
    return EnumerableDefaults.intersect(getThis(), enumerable1, comparer, all); // 使用指定的相等比较器返回交集，all参数控制是否包含重复元素
  }

  @Override public <C extends Collection<? super T>> C into(C sink) {
    return EnumerableDefaults.into(getThis(), sink); // 将集合中的所有元素添加到指定的集合中，并返回该集合
  }

  @Override public <C extends Collection<? super T>> C removeAll(C sink) {
    return EnumerableDefaults.remove(getThis(), sink); // 从指定的集合中移除当前集合包含的所有元素，并返回该集合
  }

  @Override public <TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      Enumerable<TInner> inner, Function1<T, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<T, TInner, TResult> resultSelector) {
    return EnumerableDefaults.hashJoin(getThis(), inner, outerKeySelector,
        innerKeySelector, resultSelector); // 基于哈希表的等值连接，将两个集合中键匹配的元素连接
  }

  @Override public <TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      Enumerable<TInner> inner, Function1<T, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<T, TInner, TResult> resultSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.hashJoin(getThis(), inner, outerKeySelector,
        innerKeySelector, resultSelector, comparer); // 使用指定的相等比较器进行哈希连接
  }

  @Override public <TInner, TKey, TResult> Enumerable<TResult> asofJoin(
      Enumerable<TInner> inner,
      Function1<T, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<T, @Nullable TInner, TResult> resultSelector,
      Predicate2<T, TInner> matchComparator,
      Comparator<TInner> timestampComparator,
      boolean generateNullsOnRight) {
    return EnumerableDefaults.asofJoin(getThis(), inner, outerKeySelector,
        innerKeySelector, resultSelector, matchComparator,
        timestampComparator, generateNullsOnRight); // 执行as-of连接，匹配最近的记录，常用于时间序列数据
  }

  @Override public <TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      Enumerable<TInner> inner, Function1<T, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<T, TInner, TResult> resultSelector,
      @Nullable EqualityComparer<TKey> comparer,
      boolean generateNullsOnLeft, boolean generateNullsOnRight) {
    return EnumerableDefaults.hashJoin(getThis(), inner, outerKeySelector,
        innerKeySelector, resultSelector, comparer, generateNullsOnLeft,
        generateNullsOnRight); // 支持左外连接和右外连接的哈希连接
  }

  @Override public <TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      Enumerable<TInner> inner, Function1<T, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<T, TInner, TResult> resultSelector,
      EqualityComparer<TKey> comparer,
      boolean generateNullsOnLeft, boolean generateNullsOnRight,
      @Nullable Predicate2<T, TInner> predicate) {
    return EnumerableDefaults.hashJoin(getThis(), inner, outerKeySelector,
        innerKeySelector, resultSelector, comparer, generateNullsOnLeft,
        generateNullsOnRight, predicate); // 支持外连接和额外谓词过滤的哈希连接
  }

  @Override public <TInner, TResult> Enumerable<TResult> correlateJoin(
      JoinType joinType, Function1<T, Enumerable<TInner>> inner,
      Function2<T, TInner, TResult> resultSelector) {
    return EnumerableDefaults.correlateJoin(joinType, getThis(), inner,
        resultSelector); // 执行相关子查询连接，根据连接类型（内连接、左外连接等）连接两个集合
  }

  @Override public T last() {
    return EnumerableDefaults.last(getThis()); // 返回集合中的最后一个元素，如果集合为空则抛出异常
  }

  @Override public T last(Predicate1<T> predicate) {
    return EnumerableDefaults.last(getThis(), predicate); // 返回集合中满足谓词条件的最后一个元素，如果没有则抛出异常
  }

  @Override public @Nullable T lastOrDefault() {
    return EnumerableDefaults.lastOrDefault(getThis()); // 返回集合中的最后一个元素，如果集合为空则返回默认值null
  }

  @Override public @Nullable T lastOrDefault(Predicate1<T> predicate) {
    return EnumerableDefaults.lastOrDefault(getThis(), predicate); // 返回集合中满足谓词条件的最后一个元素，如果没有则返回默认值null
  }

  @Override public long longCount() {
    return EnumerableDefaults.longCount(getThis()); // 返回集合中元素的总数，使用long类型以支持大集合
  }

  @Override public long longCount(Predicate1<T> predicate) {
    return EnumerableDefaults.longCount(getThis(), predicate); // 返回集合中满足指定谓词条件的元素数量，使用long类型
  }

  @SuppressWarnings("unchecked")
  @Override public @Nullable T max() {
    return (@Nullable T) EnumerableDefaults.max((Enumerable) getThis()); // 返回集合中的最大值，元素必须实现Comparable接口
  }

  @Override public @Nullable BigDecimal max(BigDecimalFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中元素的BigDecimal类型最大值
  }

  @Override public @Nullable BigDecimal max(NullableBigDecimalFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中可空BigDecimal类型元素的最大值
  }

  @Override public double max(DoubleFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中元素的double类型最大值
  }

  @Override public @Nullable Double max(NullableDoubleFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中可空Double类型元素的最大值
  }

  @Override public int max(IntegerFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中元素的int类型最大值
  }

  @Override public @Nullable Integer max(NullableIntegerFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中可空Integer类型元素的最大值
  }

  @Override public long max(LongFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中元素的long类型最大值
  }

  @Override public @Nullable Long max(NullableLongFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中可空Long类型元素的最大值
  }

  @Override public float max(FloatFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中元素的float类型最大值
  }

  @Override public @Nullable Float max(NullableFloatFunction1<T> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中可空Float类型元素的最大值
  }

  @Override public <TResult extends Comparable<TResult>> @Nullable TResult max(
      Function1<T, TResult> selector) {
    return EnumerableDefaults.max(getThis(), selector); // 返回集合中元素通过选择器转换后的最大值
  }

  @SuppressWarnings("unchecked")
  @Override public @Nullable T min() {
    return (@Nullable T) EnumerableDefaults.min((Enumerable) getThis()); // 返回集合中的最小值，元素必须实现Comparable接口
  }

  @Override public @Nullable BigDecimal min(BigDecimalFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中元素的BigDecimal类型最小值
  }

  @Override public @Nullable BigDecimal min(NullableBigDecimalFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中可空BigDecimal类型元素的最小值
  }

  @Override public double min(DoubleFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中元素的double类型最小值
  }

  @Override public @Nullable Double min(NullableDoubleFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中可空Double类型元素的最小值
  }

  @Override public int min(IntegerFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中元素的int类型最小值
  }

  @Override public @Nullable Integer min(NullableIntegerFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中可空Integer类型元素的最小值
  }

  @Override public long min(LongFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中元素的long类型最小值
  }

  @Override public @Nullable Long min(NullableLongFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中可空Long类型元素的最小值
  }

  @Override public float min(FloatFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中元素的float类型最小值
  }

  @Override public @Nullable Float min(NullableFloatFunction1<T> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中可空Float类型元素的最小值
  }

  @Override public <TResult extends Comparable<TResult>> @Nullable TResult min(
      Function1<T, TResult> selector) {
    return EnumerableDefaults.min(getThis(), selector); // 返回集合中元素通过选择器转换后的最小值
  }

  @Override public <TResult> Enumerable<TResult> ofType(Class<TResult> clazz) {
    return EnumerableDefaults.ofType(getThis(), clazz); // 筛选出集合中指定类型的元素，过滤掉其他类型的元素
  }

  @Override public <TKey extends Comparable> Enumerable<T> orderBy(
      Function1<T, TKey> keySelector) {
    return EnumerableDefaults.orderBy(getThis(), keySelector); // 根据键选择器按升序对集合元素进行排序
  }

  @Override public <TKey> Enumerable<T> orderBy(Function1<T, TKey> keySelector,
      Comparator<TKey> comparator) {
    return EnumerableDefaults.orderBy(getThis(), keySelector, comparator); // 使用指定的比较器根据键选择器对集合元素进行升序排序
  }

  @Override public <TKey extends Comparable> Enumerable<T> orderByDescending(
      Function1<T, TKey> keySelector) {
    return EnumerableDefaults.orderByDescending(getThis(), keySelector); // 根据键选择器按降序对集合元素进行排序
  }

  @Override public <TKey> Enumerable<T> orderByDescending(Function1<T, TKey> keySelector,
      Comparator<TKey> comparator) {
    return EnumerableDefaults.orderByDescending(getThis(), keySelector,
        comparator); // 使用指定的比较器根据键选择器对集合元素进行降序排序
  }

  @Override public Enumerable<T> reverse() {
    return EnumerableDefaults.reverse(getThis()); // 反转集合中元素的顺序
  }

  @Override public <TResult> Enumerable<TResult> select(Function1<T, TResult> selector) {
    return EnumerableDefaults.select(getThis(), selector); // 对集合中的每个元素应用选择器函数，将元素转换为新的类型
  }

  @Override public <TResult> Enumerable<TResult> select(
      Function2<T, Integer, TResult> selector) {
    return EnumerableDefaults.select(getThis(), selector); // 对集合中的每个元素应用选择器函数，选择器可以访问元素索引
  }

  @Override public <TResult> Enumerable<TResult> selectMany(
      Function1<T, Enumerable<TResult>> selector) {
    return EnumerableDefaults.selectMany(getThis(), selector); // 将集合中的每个元素投影到一个序列，然后将所有序列连接为一个序列
  }

  @Override public <TResult> Enumerable<TResult> selectMany(
      Function2<T, Integer, Enumerable<TResult>> selector) {
    return EnumerableDefaults.selectMany(getThis(), selector); // 将集合中的每个元素投影到一个序列，选择器可以访问元素索引
  }

  @Override public <TCollection, TResult> Enumerable<TResult> selectMany(
      Function2<T, Integer, Enumerable<TCollection>> collectionSelector,
      Function2<T, TCollection, TResult> resultSelector) {
    return EnumerableDefaults.selectMany(getThis(), collectionSelector,
        resultSelector); // 将元素投影到序列，然后使用结果选择器将每个元素和子序列元素组合
  }

  @Override public <TCollection, TResult> Enumerable<TResult> selectMany(
      Function1<T, Enumerable<TCollection>> collectionSelector,
      Function2<T, TCollection, TResult> resultSelector) {
    return EnumerableDefaults.selectMany(getThis(), collectionSelector,
        resultSelector); // 将元素投影到序列，然后使用结果选择器将每个元素和子序列元素组合
  }

  @Override public boolean sequenceEqual(Enumerable<T> enumerable1) {
    return EnumerableDefaults.sequenceEqual(getThis(), enumerable1); // 检查两个集合是否按顺序包含相同的元素，使用默认的相等比较器
  }

  @Override public boolean sequenceEqual(Enumerable<T> enumerable1,
      EqualityComparer<T> comparer) {
    return EnumerableDefaults.sequenceEqual(getThis(), enumerable1, comparer); // 使用指定的相等比较器检查两个集合是否按顺序包含相同的元素
  }

  @Override public T single() {
    return EnumerableDefaults.single(getThis()); // 返回集合中的唯一元素，如果集合不包含恰好一个元素则抛出异常
  }

  @Override public T single(Predicate1<T> predicate) {
    return EnumerableDefaults.single(getThis(), predicate); // 返回集合中满足谓词条件的唯一元素，如果没有或多个则抛出异常
  }

  @Override public @Nullable T singleOrDefault() {
    return EnumerableDefaults.singleOrDefault(getThis()); // 返回集合中的唯一元素，如果集合为空则返回默认值null
  }

  @Override public @Nullable T singleOrDefault(Predicate1<T> predicate) {
    return EnumerableDefaults.singleOrDefault(getThis(), predicate); // 返回集合中满足谓词条件的唯一元素，如果没有则返回默认值null
  }

  @Override public Enumerable<T> skip(int count) {
    return EnumerableDefaults.skip(getThis(), count); // 跳过集合中指定数量的元素，返回剩余的元素
  }

  @Override public Enumerable<T> skipWhile(Predicate1<T> predicate) {
    return EnumerableDefaults.skipWhile(getThis(), predicate); // 跳过满足谓词条件的元素，直到遇到不满足条件的元素
  }

  @Override public Enumerable<T> skipWhile(Predicate2<T, Integer> predicate) {
    return EnumerableDefaults.skipWhile(getThis(), predicate); // 跳过满足谓词条件的元素，谓词可以访问元素索引
  }

  @Override public BigDecimal sum(BigDecimalFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中元素的BigDecimal类型总和
  }

  @Override public BigDecimal sum(NullableBigDecimalFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中可空BigDecimal类型元素的总和
  }

  @Override public double sum(DoubleFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中元素的double类型总和
  }

  @Override public Double sum(NullableDoubleFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中可空Double类型元素的总和
  }

  @Override public int sum(IntegerFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中元素的int类型总和
  }

  @Override public Integer sum(NullableIntegerFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中可空Integer类型元素的总和
  }

  @Override public long sum(LongFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中元素的long类型总和
  }

  @Override public Long sum(NullableLongFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中可空Long类型元素的总和
  }

  @Override public float sum(FloatFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中元素的float类型总和
  }

  @Override public Float sum(NullableFloatFunction1<T> selector) {
    return EnumerableDefaults.sum(getThis(), selector); // 计算集合中可空Float类型元素的总和
  }

  @Override public Enumerable<T> take(int count) {
    return EnumerableDefaults.take(getThis(), count); // 从集合开头返回指定数量的元素
  }

  @Override public Enumerable<T> takeWhile(Predicate1<T> predicate) {
    return EnumerableDefaults.takeWhile(getThis(), predicate); // 从集合开头返回满足谓词条件的元素，直到遇到不满足条件的元素
  }

  @Override public Enumerable<T> takeWhile(Predicate2<T, Integer> predicate) {
    return EnumerableDefaults.takeWhile(getThis(), predicate); // 从集合开头返回满足谓词条件的元素，谓词可以访问元素索引
  }

  @Override public <TKey extends Comparable<TKey>> OrderedEnumerable<T> thenBy(
      Function1<T, TKey> keySelector) {
    return EnumerableDefaults.thenBy(getThisOrdered(), keySelector); // 在已有排序的基础上，按次要键进行升序排序
  }

  @Override public <TKey> OrderedEnumerable<T> thenBy(Function1<T, TKey> keySelector,
      Comparator<TKey> comparator) {
    return EnumerableDefaults.thenByDescending(getThisOrdered(), keySelector,
        comparator); // 使用指定的比较器在已有排序的基础上，按次要键进行排序
  }

  @Override public <TKey extends Comparable<TKey>> OrderedEnumerable<T> thenByDescending(
      Function1<T, TKey> keySelector) {
    return EnumerableDefaults.thenByDescending(getThisOrdered(), keySelector); // 在已有排序的基础上，按次要键进行降序排序
  }

  @Override public <TKey> OrderedEnumerable<T> thenByDescending(
      Function1<T, TKey> keySelector, Comparator<TKey> comparator) {
    return EnumerableDefaults.thenBy(getThisOrdered(), keySelector, comparator); // 使用指定的比较器在已有排序的基础上，按次要键进行降序排序
  }

  @Override public <TKey> Map<TKey, T> toMap(Function1<T, TKey> keySelector) {
    return EnumerableDefaults.toMap(getThis(), keySelector); // 根据键选择器将集合转换为Map，键为选择器结果，值为元素本身
  }

  @Override public <TKey> Map<TKey, T> toMap(Function1<T, TKey> keySelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.toMap(getThis(), keySelector, comparer); // 使用指定的相等比较器将集合转换为Map
  }

  @Override public <TKey, TElement> Map<TKey, TElement> toMap(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector) {
    return EnumerableDefaults.toMap(getThis(), keySelector, elementSelector); // 根据键选择器和元素选择器将集合转换为Map
  }

  @Override public <TKey, TElement> Map<TKey, TElement> toMap(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.toMap(getThis(), keySelector, elementSelector,
        comparer); // 使用指定的相等比较器、键选择器和元素选择器将集合转换为Map
  }

  @Override public List<T> toList() {
    return EnumerableDefaults.toList(getThis()); // 将集合转换为List
  }

  @Override public <TKey> Lookup<TKey, T> toLookup(Function1<T, TKey> keySelector) {
    return EnumerableDefaults.toLookup(getThis(), keySelector); // 根据键选择器将集合转换为Lookup（一种一对多的映射）
  }

  @Override public <TKey> Lookup<TKey, T> toLookup(Function1<T, TKey> keySelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.toLookup(getThis(), keySelector, comparer); // 使用指定的相等比较器将集合转换为Lookup
  }

  @Override public <TKey, TElement> Lookup<TKey, TElement> toLookup(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector) {
    return EnumerableDefaults.toLookup(getThis(), keySelector, elementSelector); // 根据键选择器和元素选择器将集合转换为Lookup
  }

  @Override public <TKey, TElement> Lookup<TKey, TElement> toLookup(
      Function1<T, TKey> keySelector, Function1<T, TElement> elementSelector,
      EqualityComparer<TKey> comparer) {
    return EnumerableDefaults.toLookup(getThis(), keySelector, elementSelector,
        comparer); // 使用指定的相等比较器、键选择器和元素选择器将集合转换为Lookup
  }

  @Override public Enumerable<T> union(Enumerable<T> source1) {
    return EnumerableDefaults.union(getThis(), source1); // 返回两个集合的并集，包含所有不重复的元素
  }

  @Override public Enumerable<T> union(Enumerable<T> source1,
      EqualityComparer<T> comparer) {
    return EnumerableDefaults.union(getThis(), source1, comparer); // 使用指定的相等比较器返回两个集合的并集
  }

  @Override public Enumerable<T> where(Predicate1<T> predicate) {
    return EnumerableDefaults.where(getThis(), predicate); // 根据谓词条件过滤集合中的元素
  }

  @Override public Enumerable<T> where(Predicate2<T, Integer> predicate) {
    return EnumerableDefaults.where(getThis(), predicate); // 根据谓词条件过滤集合中的元素，谓词可以访问元素索引
  }

  @Override public <T1, TResult> Enumerable<TResult> zip(Enumerable<T1> source1,
      Function2<T, T1, TResult> resultSelector) {
    return EnumerableDefaults.zip(getThis(), source1, resultSelector); // 将两个集合的元素按位置合并，使用结果选择器生成新元素
  }
}
