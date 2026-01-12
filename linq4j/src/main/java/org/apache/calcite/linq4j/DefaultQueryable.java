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
package org.apache.calcite.linq4j; // 包声明，属于Apache Calcite的LINQ4J（Language Integrated Query for Java）模块

import org.apache.calcite.linq4j.function.BigDecimalFunction1; // 导入BigDecimal类型的一元函数接口
import org.apache.calcite.linq4j.function.DoubleFunction1; // 导入double类型的一元函数接口
import org.apache.calcite.linq4j.function.EqualityComparer; // 导入相等比较器接口，用于自定义对象相等性比较
import org.apache.calcite.linq4j.function.FloatFunction1; // 导入float类型的一元函数接口
import org.apache.calcite.linq4j.function.Function1; // 导入通用一元函数接口，接受一个参数并返回结果
import org.apache.calcite.linq4j.function.Function2; // 导入通用二元函数接口，接受两个参数并返回结果
import org.apache.calcite.linq4j.function.IntegerFunction1; // 导入int类型的一元函数接口
import org.apache.calcite.linq4j.function.LongFunction1; // 导入long类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1; // 导入可空BigDecimal类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableDoubleFunction1; // 导入可空Double类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableFloatFunction1; // 导入可空Float类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableIntegerFunction1; // 导入可空Integer类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableLongFunction1; // 导入可空Long类型的一元函数接口
import org.apache.calcite.linq4j.function.Predicate1; // 导入一元谓词接口，接受一个参数返回布尔值
import org.apache.calcite.linq4j.function.Predicate2; // 导入二元谓词接口，接受两个参数返回布尔值
import org.apache.calcite.linq4j.tree.FunctionExpression; // 导入函数表达式类，用于表示可序列化的函数表达式

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的类型

import java.math.BigDecimal; // 导入BigDecimal类，用于高精度十进制运算
import java.util.Comparator; // 导入比较器接口，用于自定义对象排序

/**
 * Implementation of the {@link Queryable} interface that
 * implements the extension methods by calling into the {@link Extensions}
 * class.
 * Queryable接口的默认实现类，通过调用Extensions类来实现扩展方法
 * 这个类是LINQ4J框架的核心组件之一，提供了可查询序列的基础实现
 * 它继承自DefaultEnumerable以获得可枚举的能力，同时实现Queryable接口以支持查询表达式
 * 还实现了OrderedQueryable接口以支持有序查询操作
 *
 * @param <T> Element type 元素类型，表示序列中元素的类型
 */
abstract class DefaultQueryable<T> extends DefaultEnumerable<T> // 抽象类，继承DefaultEnumerable获得基础的枚举功能
    implements Queryable<T>, OrderedQueryable<T> { // 实现Queryable接口以支持查询操作，实现OrderedQueryable接口以支持有序查询
  private final QueryableFactory<T> factory; // 成员变量：查询工厂，用于创建各种查询操作的结果，工厂模式的核心组件

  /**
   * Creates a DefaultQueryable using a factory that records events.
   * 无参构造方法，创建一个使用事件记录工厂的DefaultQueryable实例
   * 默认使用QueryableRecorder作为工厂，该工厂会记录所有的查询操作事件
   * 这种设计允许对查询操作进行跟踪和调试
   */
  protected DefaultQueryable() { // 受保护的构造方法，供子类调用
    this(QueryableRecorder.instance()); // 调用带参数的构造方法，传入QueryableRecorder的单例实例作为工厂
  }

  /**
   * Creates a DefaultQueryable using a particular factory.
   * 带参数的构造方法，使用指定的工厂创建DefaultQueryable实例
   * 这种设计允许灵活地替换工厂实现，支持不同的查询执行策略
   *
   * @param factory the factory 查询工厂，用于创建各种查询操作的结果
   */
  protected DefaultQueryable(QueryableFactory<T> factory) { // 受保护的构造方法，接受一个Queryabl eFactory参数
    this.factory = factory; // 将传入的工厂赋值给成员变量factory，后续所有查询操作都通过这个工厂执行
  }

  // override return type 重写父类方法的返回类型，从Enumerable<T>改为更具体的Queryable<T>
  @Override protected Queryable<T> getThis() { // 重写父类的getThis方法，返回当前对象的Queryable类型引用
    return this; // 返回this对象本身，类型为Queryable<T>，支持链式调用
  }

  protected OrderedQueryable<T> getThisOrderedQueryable() { // 受保护的方法，获取当前对象作为OrderedQueryable类型的引用
    return this; // 返回this对象本身，类型为OrderedQueryable<T>，用于支持有序查询操作的链式调用
  }

  @Override public Enumerable<T> asEnumerable() { // 重写接口方法，将Queryable转换为Enumerable
    return new AbstractEnumerable<T>() { // 创建一个匿名内部类，继承自AbstractEnumerable
      @Override public Enumerator<T> enumerator() { // 实现抽象方法，返回一个枚举器
        return DefaultQueryable.this.enumerator(); // 调用当前对象的enumerator()方法，获取枚举器
      }
    };
  }

  // Disambiguate 消除歧义部分，用于处理从父接口继承的多个同名方法

  @Override public Queryable<T> union(Enumerable<T> source1) { // 重写union方法，计算两个序列的并集（去重）
    return factory.union(getThis(), source1); // 委托给工厂执行并集操作，传入当前序列和第二个序列
  }

  @Override public Queryable<T> union(Enumerable<T> source1, // 重写union方法，使用自定义比较器计算并集
      EqualityComparer<T> comparer) { // 参数：第二个序列和自定义相等比较器
    return factory.union(getThis(), source1, comparer); // 委托给工厂执行并集操作，使用指定的比较器判断元素相等性
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1) { // 重写intersect方法，计算两个序列的交集
    return intersect(source1, false); // 调用重载方法，all参数设为false表示不去重
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1, boolean all) { // 重写intersect方法，计算交集，all控制是否保留重复元素
    return factory.intersect(getThis(), source1, all); // 委托给工厂执行交集操作，all为true时保留重复元素
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1, // 重写intersect方法，使用自定义比较器计算交集
      EqualityComparer<T> comparer) { // 参数：第二个序列和自定义相等比较器
    return intersect(source1, comparer, false); // 调用重载方法，all参数设为false
  }

  @Override public Queryable<T> intersect(Enumerable<T> source1, // 重写intersect方法，使用自定义比较器和all参数
      EqualityComparer<T> comparer, boolean all) { // 参数：第二个序列、自定义比较器和是否保留重复元素的标志
    return factory.intersect(getThis(), source1, comparer, all); // 委托给工厂执行交集操作
  }

  @Override public Queryable<T> except(Enumerable<T> enumerable1, // 重写except方法，计算差集（当前序列减去第二个序列）
      EqualityComparer<T> comparer) { // 参数：第二个序列和自定义相等比较器
    return except(enumerable1, comparer, false); // 调用重载方法，all参数设为false
  }
  @Override public Queryable<T> except(Enumerable<T> enumerable1, // 重写except方法，使用自定义比较器和all参数计算差集
      EqualityComparer<T> comparer, boolean all) { // 参数：第二个序列、自定义比较器和是否保留重复元素的标志
    return factory.except(getThis(), enumerable1, comparer, all); // 委托给工厂执行差集操作
  }

  @Override public Queryable<T> except(Enumerable<T> enumerable1) { // 重写except方法，计算差集
    return except(enumerable1, false); // 调用重载方法，all参数设为false
  }

  @Override public Queryable<T> except(Enumerable<T> enumerable1, boolean all) { // 重写except方法，all控制是否保留重复元素
    return factory.except(getThis(), enumerable1, all); // 委托给工厂执行差集操作
  }

  @Override public Queryable<T> take(int count) { // 重写take方法，从序列开头返回指定数量的元素
    return factory.take(getThis(), count); // 委托给工厂执行take操作，count指定要返回的元素数量
  }

  @Override public Queryable<T> skip(int count) { // 重写skip方法，跳过序列开头指定数量的元素，返回剩余元素
    return factory.skip(getThis(), count); // 委托给工厂执行skip操作，count指定要跳过的元素数量
  }

  @Override public Queryable<T> reverse() { // 重写reverse方法，反转序列中元素的顺序
    return factory.reverse(getThis()); // 委托给工厂执行reverse操作
  }

  @Override public Queryable<T> distinct() { // 重写distinct方法，返回序列中的不重复元素（去重）
    return factory.distinct(getThis()); // 委托给工厂执行distinct操作，使用默认的相等比较器
  }

  @Override public Queryable<T> distinct(EqualityComparer<T> comparer) { // 重写distinct方法，使用自定义比较器去重
    return factory.distinct(getThis(), comparer); // 委托给工厂执行distinct操作，使用指定的比较器判断元素相等性
  }

  @Override public <TResult> Queryable<TResult> ofType(Class<TResult> clazz) { // 重写ofType方法，筛选出指定类型的元素
    return factory.ofType(getThis(), clazz); // 委托给工厂执行ofType操作，clazz指定要筛选的类型
  }

  @Override public Queryable<@Nullable T> defaultIfEmpty() { // 重写defaultIfEmpty方法，如果序列为空则返回包含默认值的序列
    return factory.defaultIfEmpty(getThis()); // 委托给工厂执行defaultIfEmpty操作
  }

  @Override public Queryable<T> asQueryable() { // 重写asQueryable方法，将当前对象作为Queryable返回
    return this; // 直接返回this对象本身，因为当前类已经实现了Queryable接口
  }

  @Override public <T2> Queryable<T2> cast(Class<T2> clazz) { // 重写cast方法，将序列中的元素转换为指定类型
    return factory.cast(getThis(), clazz); // 委托给工厂执行cast操作，clazz指定目标类型
  }

  // End disambiguate 消除歧义部分结束

  @Override public @Nullable T aggregate( // 重写aggregate方法，对序列元素执行累加器函数
      FunctionExpression<Function2<@Nullable T, T, T>> selector) { // 参数：累加器函数，接受当前累加值和下一个元素，返回新的累加值
    return factory.aggregate(getThis(), selector); // 委托给工厂执行aggregate操作
  }

  @Override public <TAccumulate> TAccumulate aggregate(TAccumulate seed, // 重写aggregate方法，使用初始种子值执行累加
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> selector) { // 参数：初始种子值和累加器函数
    return factory.aggregate(getThis(), seed, selector); // 委托给工厂执行aggregate操作，从种子值开始累加
  }

  @Override public <TAccumulate, TResult> TResult aggregate(TAccumulate seed, // 重写aggregate方法，使用种子值、累加函数和结果转换函数
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func, // 参数：累加函数
      FunctionExpression<Function1<TAccumulate, TResult>> selector) { // 参数：结果转换函数，将最终累加值转换为结果类型
    return factory.aggregate(getThis(), seed, func, selector); // 委托给工厂执行aggregate操作
  }

  @Override public boolean all(FunctionExpression<Predicate1<T>> predicate) { // 重写all方法，判断序列中的所有元素是否都满足指定条件
    return factory.all(getThis(), predicate); // 委托给工厂执行all操作，predicate是谓词函数，返回true如果所有元素都满足条件
  }

  @Override public boolean any(FunctionExpression<Predicate1<T>> predicate) { // 重写any方法，判断序列中是否存在满足指定条件的元素
    return factory.any(getThis(), predicate); // 委托给工厂执行any操作，predicate是谓词函数，返回true如果存在元素满足条件
  }

  @Override public BigDecimal averageBigDecimal( // 重写averageBigDecimal方法，计算序列中BigDecimal类型元素的平均值
      FunctionExpression<BigDecimalFunction1<T>> selector) { // 参数：选择器函数，从元素中提取BigDecimal值
    return factory.averageBigDecimal(getThis(), selector); // 委托给工厂执行averageBigDecimal操作
  }

  @Override public BigDecimal averageNullableBigDecimal( // 重写averageNullableBigDecimal方法，计算可空BigDecimal元素的平均值
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空BigDecimal值
    return factory.averageNullableBigDecimal(getThis(), selector); // 委托给工厂执行averageNullableBigDecimal操作
  }

  @Override public double averageDouble(FunctionExpression<DoubleFunction1<T>> selector) { // 重写averageDouble方法，计算序列中double类型元素的平均值
    return factory.averageDouble(getThis(), selector); // 委托给工厂执行averageDouble操作，selector是提取double值的选择器函数
  }

  @Override public Double averageNullableDouble( // 重写averageNullableDouble方法，计算可空Double元素的平均值
      FunctionExpression<NullableDoubleFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Double值
    return factory.averageNullableDouble(getThis(), selector); // 委托给工厂执行averageNullableDouble操作
  }

  @Override public int averageInteger(FunctionExpression<IntegerFunction1<T>> selector) { // 重写averageInteger方法，计算序列中int类型元素的平均值
    return factory.averageInteger(getThis(), selector); // 委托给工厂执行averageInteger操作，selector是提取int值的选择器函数
  }

  @Override public Integer averageNullableInteger( // 重写averageNullableInteger方法，计算可空Integer元素的平均值
      FunctionExpression<NullableIntegerFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Integer值
    return factory.averageNullableInteger(getThis(), selector); // 委托给工厂执行averageNullableInteger操作
  }

  @Override public float averageFloat(FunctionExpression<FloatFunction1<T>> selector) { // 重写averageFloat方法，计算序列中float类型元素的平均值
    return factory.averageFloat(getThis(), selector); // 委托给工厂执行averageFloat操作，selector是提取float值的选择器函数
  }

  @Override public Float averageNullableFloat( // 重写averageNullableFloat方法，计算可空Float元素的平均值
      FunctionExpression<NullableFloatFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Float值
    return factory.averageNullableFloat(getThis(), selector); // 委托给工厂执行averageNullableFloat操作
  }

  @Override public long averageLong(FunctionExpression<LongFunction1<T>> selector) { // 重写averageLong方法，计算序列中long类型元素的平均值
    return factory.averageLong(getThis(), selector); // 委托给工厂执行averageLong操作，selector是提取long值的选择器函数
  }

  @Override public Long averageNullableLong( // 重写averageNullableLong方法，计算可空Long元素的平均值
      FunctionExpression<NullableLongFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Long值
    return factory.averageNullableLong(getThis(), selector); // 委托给工厂执行averageNullableLong操作
  }

  @Override public Queryable<T> concat(Enumerable<T> source2) { // 重写concat方法，连接两个序列
    return factory.concat(getThis(), source2); // 委托给工厂执行concat操作，将当前序列与source2连接起来
  }

  @Override public int count(FunctionExpression<Predicate1<T>> func) { // 重写count方法，计算满足指定条件的元素数量
    return factory.count(getThis(), func); // 委托给工厂执行count操作，func是谓词函数，返回满足条件的元素数量
  }

  @Override public T first(FunctionExpression<Predicate1<T>> predicate) { // 重写first方法，返回序列中满足指定条件的第一个元素
    return factory.first(getThis(), predicate); // 委托给工厂执行first操作，predicate是谓词函数
  }

  @Override public @Nullable T firstOrDefault(FunctionExpression<Predicate1<T>> predicate) { // 重写firstOrDefault方法，返回序列中满足条件的第一个元素，如果没有则返回默认值
    return factory.firstOrDefault(getThis(), predicate); // 委托给工厂执行firstOrDefault操作，如果没有满足条件的元素则返回null
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy( // 重写groupBy方法，根据键选择器对序列元素进行分组
      FunctionExpression<Function1<T, TKey>> keySelector) { // 参数：键选择器函数，从元素中提取分组键
    return factory.groupBy(getThis(), keySelector); // 委托给工厂执行groupBy操作，返回分组结果
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy( // 重写groupBy方法，使用自定义比较器进行分组
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      EqualityComparer<TKey> comparer) { // 参数：自定义相等比较器，用于比较键的相等性
    return factory.groupBy(getThis(), keySelector, comparer); // 委托给工厂执行groupBy操作
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 重写groupBy方法，分组并转换元素
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector) { // 参数：元素选择器函数，转换每个元素
    return factory.groupBy(getThis(), keySelector, elementSelector); // 委托给工厂执行groupBy操作
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 重写groupBy方法，分组、转换元素并使用自定义比较器
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector, // 参数：元素选择器函数
      EqualityComparer<TKey> comparer) { // 参数：自定义相等比较器
    return factory.groupBy(getThis(), keySelector, elementSelector, comparer); // 委托给工厂执行groupBy操作
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK( // 重写groupByK方法，分组并使用结果选择器转换分组结果
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数，从元素中提取分组键
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector) { // 参数：结果选择器函数，接受键和元素枚举，返回结果
    return factory.groupByK(getThis(), keySelector, resultSelector); // 委托给工厂执行groupByK操作
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK( // 重写groupByK方法，分组并使用结果选择器和自定义比较器
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector, // 参数：结果选择器函数
      EqualityComparer<TKey> comparer) { // 参数：自定义相等比较器
    return factory.groupByK(getThis(), keySelector, resultSelector, comparer); // 委托给工厂执行groupByK操作
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy( // 重写groupBy方法，分组、转换元素并使用结果选择器
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector, // 参数：元素选择器函数
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector) { // 参数：结果选择器函数
    return factory.groupBy(getThis(), keySelector, elementSelector, // 委托给工厂执行groupBy操作
        resultSelector);
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy( // 重写groupBy方法，使用自定义比较器
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector, // 参数：元素选择器函数
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector, // 参数：结果选择器函数
      EqualityComparer<TKey> comparer) { // 参数：自定义相等比较器
    return factory.groupBy(getThis(), keySelector, elementSelector, // 委托给工厂执行groupBy操作
        resultSelector, comparer);
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin( // 重写groupJoin方法，执行分组连接操作
      Enumerable<TInner> inner, // 参数：要连接的内部序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 参数：外部序列的键选择器
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 参数：内部序列的键选择器
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector) { // 参数：结果选择器，接受外部元素和匹配的内部元素集合
    return factory.groupJoin(getThis(), inner, outerKeySelector, // 委托给工厂执行groupJoin操作
        innerKeySelector, resultSelector);
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin( // 重写groupJoin方法，使用自定义比较器
      Enumerable<TInner> inner, // 参数：要连接的内部序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 参数：外部序列的键选择器
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 参数：内部序列的键选择器
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector, // 参数：结果选择器
      EqualityComparer<TKey> comparer) { // 参数：自定义相等比较器
    return factory.groupJoin(getThis(), inner, outerKeySelector, // 委托给工厂执行groupJoin操作
        innerKeySelector, resultSelector, comparer);
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join(
      Enumerable<TInner> inner,
      FunctionExpression<Function1<T, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector) {
    return factory.join(getThis(), inner, outerKeySelector, innerKeySelector,
        resultSelector);
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join( // 重写join方法，执行内连接操作
      Enumerable<TInner> inner, // 参数：要连接的内部序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 参数：外部序列的键选择器
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 参数：内部序列的键选择器
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector, // 参数：结果选择器，接受匹配的外部和内部元素
      EqualityComparer<TKey> comparer) { // 参数：自定义相等比较器
    return factory.join(getThis(), inner, outerKeySelector, innerKeySelector, // 委托给工厂执行join操作
        resultSelector, comparer);
  }

  @Override public T last(FunctionExpression<Predicate1<T>> predicate) { // 重写last方法，返回序列中满足指定条件的最后一个元素
    return factory.last(getThis(), predicate); // 委托给工厂执行last操作，predicate是谓词函数
  }

  @Override public T lastOrDefault(FunctionExpression<Predicate1<T>> predicate) { // 重写lastOrDefault方法，返回满足条件的最后一个元素，如果没有则返回默认值
    return factory.lastOrDefault(getThis(), predicate); // 委托给工厂执行lastOrDefault操作
  }

  @Override public long longCount(FunctionExpression<Predicate1<T>> predicate) { // 重写longCount方法，计算满足条件的元素数量，返回long类型
    return factory.longCount(getThis(), predicate); // 委托给工厂执行longCount操作，适用于大型序列
  }

  @Override public <TResult extends Comparable<TResult>> TResult max( // 重写max方法，返回序列中的最大值
      FunctionExpression<Function1<T, TResult>> selector) { // 参数：选择器函数，从元素中提取可比较的值
    return factory.max(getThis(), selector); // 委托给工厂执行max操作
  }

  @Override public <TResult extends Comparable<TResult>> TResult min( // 重写min方法，返回序列中的最小值
      FunctionExpression<Function1<T, TResult>> selector) { // 参数：选择器函数，从元素中提取可比较的值
    return factory.min(getThis(), selector); // 委托给工厂执行min操作
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderBy( // 重写orderBy方法，按升序对序列元素进行排序
      FunctionExpression<Function1<T, TKey>> keySelector) { // 参数：键选择器函数，从元素中提取用于排序的键，键必须实现Comparable接口
    return factory.orderBy(getThis(), keySelector); // 委托给工厂执行orderBy操作，返回有序查询对象
  }

  @Override public <TKey> OrderedQueryable<T> orderBy( // 重写orderBy方法，使用自定义比较器按升序排序
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      Comparator<TKey> comparator) { // 参数：自定义比较器，用于比较键的大小
    return factory.orderBy(getThis(), keySelector, comparator); // 委托给工厂执行orderBy操作
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderByDescending( // 重写orderByDescending方法，按降序对序列元素进行排序
      FunctionExpression<Function1<T, TKey>> keySelector) { // 参数：键选择器函数，键必须实现Comparable接口
    return factory.orderByDescending(getThis(), keySelector); // 委托给工厂执行orderByDescending操作
  }

  @Override public <TKey> OrderedQueryable<T> orderByDescending( // 重写orderByDescending方法，使用自定义比较器按降序排序
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      Comparator<TKey> comparator) { // 参数：自定义比较器
    return factory.orderByDescending(getThis(), keySelector, comparator); // 委托给工厂执行orderByDescending操作
  }

  @Override public <TResult> Queryable<TResult> select( // 重写select方法，将序列中的每个元素投影为新形式
      FunctionExpression<Function1<T, TResult>> selector) { // 参数：转换函数，接受元素并返回转换后的结果
    return factory.select(getThis(), selector); // 委托给工厂执行select操作
  }

  @Override public <TResult> Queryable<TResult> selectN( // 重写selectN方法，将每个元素及其索引投影为新形式
      FunctionExpression<Function2<T, Integer, TResult>> selector) { // 参数：转换函数，接受元素和索引，返回转换后的结果
    return factory.selectN(getThis(), selector); // 委托给工厂执行selectN操作
  }

  @Override public <TResult> Queryable<TResult> selectMany( // 重写selectMany方法，将序列的每个元素投影到一个序列，并将这些序列展平为一个序列
      FunctionExpression<Function1<T, Enumerable<TResult>>> selector) { // 参数：转换函数，接受元素并返回一个序列
    return factory.selectMany(getThis(), selector); // 委托给工厂执行selectMany操作
  }

  @Override public <TResult> Queryable<TResult> selectManyN( // 重写selectManyN方法，将每个元素及其索引投影到一个序列，并展平
      FunctionExpression<Function2<T, Integer, Enumerable<TResult>>> selector) { // 参数：转换函数，接受元素和索引，返回一个序列
    return factory.selectManyN(getThis(), selector); // 委托给工厂执行selectManyN操作
  }

  @Override public <TCollection, TResult> Queryable<TResult> selectMany( // 重写selectMany方法，使用索引和结果选择器
      FunctionExpression<Function2<T, Integer, Enumerable<TCollection>>> // 参数：集合选择器函数，接受元素和索引
        collectionSelector,
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) { // 参数：结果选择器函数，接受原始元素和集合元素
    return factory.selectMany(getThis(), collectionSelector, resultSelector); // 委托给工厂执行selectMany操作
  }

  @Override public <TCollection, TResult> Queryable<TResult> selectManyN( // 重写selectManyN方法，使用结果选择器
      FunctionExpression<Function1<T, Enumerable<TCollection>>> // 参数：集合选择器函数
        collectionSelector,
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) { // 参数：结果选择器函数
    return factory.selectManyN(getThis(), collectionSelector, resultSelector); // 委托给工厂执行selectManyN操作
  }

  @Override public T single(FunctionExpression<Predicate1<T>> predicate) { // 重写single方法，返回序列中满足指定条件的唯一元素
    return factory.single(getThis(), predicate); // 委托给工厂执行single操作，如果没有元素或有多个元素会抛出异常
  }

  @Override public @Nullable T singleOrDefault(FunctionExpression<Predicate1<T>> predicate) { // 重写singleOrDefault方法，返回满足条件的唯一元素，如果没有则返回默认值
    return factory.singleOrDefault(getThis(), predicate); // 委托给工厂执行singleOrDefault操作
  }

  @Override public Queryable<T> skipWhile(FunctionExpression<Predicate1<T>> predicate) { // 重写skipWhile方法，跳过满足条件的元素，直到遇到不满足条件的元素
    return factory.skipWhile(getThis(), predicate); // 委托给工厂执行skipWhile操作，predicate是谓词函数
  }

  @Override public Queryable<T> skipWhileN( // 重写skipWhileN方法，使用元素索引跳过满足条件的元素
      FunctionExpression<Predicate2<T, Integer>> predicate) { // 参数：谓词函数，接受元素和索引
    return factory.skipWhileN(getThis(), predicate); // 委托给工厂执行skipWhileN操作
  }

  @Override public BigDecimal sumBigDecimal( // 重写sumBigDecimal方法，计算序列中BigDecimal类型元素的总和
      FunctionExpression<BigDecimalFunction1<T>> selector) { // 参数：选择器函数，从元素中提取BigDecimal值
    return factory.sumBigDecimal(getThis(), selector); // 委托给工厂执行sumBigDecimal操作
  }

  @Override public BigDecimal sumNullableBigDecimal( // 重写sumNullableBigDecimal方法，计算可空BigDecimal元素的总和
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空BigDecimal值
    return factory.sumNullableBigDecimal(getThis(), selector); // 委托给工厂执行sumNullableBigDecimal操作
  }

  @Override public double sumDouble(FunctionExpression<DoubleFunction1<T>> selector) { // 重写sumDouble方法，计算序列中double类型元素的总和
    return factory.sumDouble(getThis(), selector); // 委托给工厂执行sumDouble操作，selector是提取double值的选择器函数
  }

  @Override public Double sumNullableDouble( // 重写sumNullableDouble方法，计算可空Double元素的总和
      FunctionExpression<NullableDoubleFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Double值
    return factory.sumNullableDouble(getThis(), selector); // 委托给工厂执行sumNullableDouble操作
  }

  @Override public int sumInteger(FunctionExpression<IntegerFunction1<T>> selector) { // 重写sumInteger方法，计算序列中int类型元素的总和
    return factory.sumInteger(getThis(), selector); // 委托给工厂执行sumInteger操作，selector是提取int值的选择器函数
  }

  @Override public Integer sumNullableInteger( // 重写sumNullableInteger方法，计算可空Integer元素的总和
      FunctionExpression<NullableIntegerFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Integer值
    return factory.sumNullableInteger(getThis(), selector); // 委托给工厂执行sumNullableInteger操作
  }

  @Override public long sumLong(FunctionExpression<LongFunction1<T>> selector) { // 重写sumLong方法，计算序列中long类型元素的总和
    return factory.sumLong(getThis(), selector); // 委托给工厂执行sumLong操作，selector是提取long值的选择器函数
  }

  @Override public Long sumNullableLong( // 重写sumNullableLong方法，计算可空Long元素的总和
      FunctionExpression<NullableLongFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Long值
    return factory.sumNullableLong(getThis(), selector); // 委托给工厂执行sumNullableLong操作
  }

  @Override public float sumFloat(FunctionExpression<FloatFunction1<T>> selector) { // 重写sumFloat方法，计算序列中float类型元素的总和
    return factory.sumFloat(getThis(), selector); // 委托给工厂执行sumFloat操作，selector是提取float值的选择器函数
  }

  @Override public Float sumNullableFloat( // 重写sumNullableFloat方法，计算可空Float元素的总和
      FunctionExpression<NullableFloatFunction1<T>> selector) { // 参数：选择器函数，从元素中提取可空Float值
    return factory.sumNullableFloat(getThis(), selector); // 委托给工厂执行sumNullableFloat操作
  }

  @Override public Queryable<T> takeWhile(FunctionExpression<Predicate1<T>> predicate) { // 重写takeWhile方法，获取满足条件的元素，直到遇到不满足条件的元素
    return factory.takeWhile(getThis(), predicate); // 委托给工厂执行takeWhile操作，predicate是谓词函数
  }

  @Override public Queryable<T> takeWhileN( // 重写takeWhileN方法，使用元素索引获取满足条件的元素
      FunctionExpression<Predicate2<T, Integer>> predicate) { // 参数：谓词函数，接受元素和索引
    return factory.takeWhileN(getThis(), predicate); // 委托给工厂执行takeWhileN操作
  }

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenBy( // 重写thenBy方法，在已排序的序列上执行次要升序排序
      FunctionExpression<Function1<T, TKey>> keySelector) { // 参数：键选择器函数，键必须实现Comparable接口
    return factory.thenBy(getThisOrderedQueryable(), keySelector); // 委托给工厂执行thenBy操作，用于多级排序
  }

  @Override public <TKey> OrderedQueryable<T> thenBy( // 重写thenBy方法，使用自定义比较器执行次要升序排序
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      Comparator<TKey> comparator) { // 参数：自定义比较器
    return factory.thenByDescending(getThisOrderedQueryable(), keySelector, // 委托给工厂执行thenByDescending操作
        comparator);
  }

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenByDescending( // 重写thenByDescending方法，在已排序的序列上执行次要降序排序
      FunctionExpression<Function1<T, TKey>> keySelector) { // 参数：键选择器函数，键必须实现Comparable接口
    return factory.thenByDescending(getThisOrderedQueryable(), keySelector); // 委托给工厂执行thenByDescending操作
  }

  @Override public <TKey> OrderedQueryable<T> thenByDescending( // 重写thenByDescending方法，使用自定义比较器执行次要降序排序
      FunctionExpression<Function1<T, TKey>> keySelector, // 参数：键选择器函数
      Comparator<TKey> comparator) { // 参数：自定义比较器
    return factory.thenBy(getThisOrderedQueryable(), keySelector, comparator); // 委托给工厂执行thenBy操作
  }

  @Override public Queryable<T> where( // 重写where方法，根据谓词筛选序列中的元素
      FunctionExpression<? extends Predicate1<T>> predicate) { // 参数：谓词函数，用于判断元素是否满足条件
    return factory.where(getThis(), predicate); // 委托给工厂执行where操作，返回满足条件的元素序列
  }

  @Override public Queryable<T> whereN( // 重写whereN方法，使用元素索引根据谓词筛选元素
      FunctionExpression<? extends Predicate2<T, Integer>> predicate) { // 参数：谓词函数，接受元素和索引
    return factory.whereN(getThis(), predicate); // 委托给工厂执行whereN操作
  }

  @Override public <T1, TResult> Queryable<TResult> zip( // 重写zip方法，将两个序列合并为一个序列
      Enumerable<T1> source1, // 参数：要合并的第二个序列
      FunctionExpression<Function2<T, T1, TResult>> resultSelector) { // 参数：结果选择器函数，接受两个序列中对应位置的元素
    return factory.zip(getThis(), source1, resultSelector); // 委托给工厂执行zip操作，两个序列按位置配对
  }
} // 类定义结束，DefaultQueryable类提供了Queryable接口的默认实现
