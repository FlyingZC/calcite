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
package org.apache.calcite.prepare;

import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Grouping;
import org.apache.calcite.linq4j.OrderedQueryable;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.QueryableDefaults;
import org.apache.calcite.linq4j.QueryableFactory;
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
import org.apache.calcite.linq4j.tree.FunctionExpression;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.schema.QueryableTable;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTableQueryable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.apache.calcite.linq4j.Nullness.castNonNull;

import static java.util.Objects.requireNonNull;

/**
 * Implementation of {@link QueryableFactory}
 * that builds a tree of {@link RelNode} planner nodes. Used by
 * {@link LixToRelTranslator}.
 * QueryableFactory接口的实现类，用于构建RelNode规划器节点树。由LixToRelTranslator使用。
 *
 * <p>Each of the methods that implements a {@code Replayer} method creates
 * a tree of {@code RelNode}s equivalent to the arguments, and calls
 * {@link #setRel} to assign the root of that tree to the {@link #rel} member
 * variable.
 * 每个实现Replayer方法的方法都会创建一个与参数等效的RelNode树，并调用setRel方法将该树的根节点赋值给rel成员变量。
 *
 * <p>To comply with the {@link org.apache.calcite.linq4j.QueryableFactory}
 * interface, which is after all a factory, each method returns a dummy result
 * such as {@code null} or {@code 0}.
 * The caller will not use the result.
 * The real effect of the method is to
 * call {@link #setRel} with a {@code RelNode}.
 * 为了符合QueryableFactory接口（毕竟是一个工厂接口），每个方法返回一个虚拟结果，如null或0。
 * 调用者不会使用这个结果。方法的实际效果是调用setRel方法传入一个RelNode。
 *
 * <p>NOTE: Many methods currently throw {@link UnsupportedOperationException}.
 * These method need to be implemented.
 * 注意：许多方法当前抛出UnsupportedOperationException。这些方法需要实现。
 *
 * @param <T> Element type 元素类型
 */
class QueryableRelBuilder<T> implements QueryableFactory<T> {
  private final LixToRelTranslator translator; // Linq4j到RelNode的转换器，负责将LINQ表达式转换为Calcite的关系表达式树
  private @Nullable RelNode rel; // 当前构建的关系表达式树的根节点，可以为null，表示还没有构建任何关系表达式

  QueryableRelBuilder(LixToRelTranslator translator) { // 构造方法，接收一个LixToRelTranslator转换器作为参数
    this.translator = translator; // 将传入的转换器保存到成员变量中，后续所有转换操作都使用这个转换器
  }

  RelNode toRel(Queryable<T> queryable) { // 将Queryable对象转换为RelNode关系表达式树，这是整个转换过程的核心方法
    if (queryable instanceof QueryableDefaults.Replayable) { // 如果Queryable是可重放的类型（即可以重放操作序列）
      //noinspection unchecked // 忽略未检查的类型转换警告
      ((QueryableDefaults.Replayable) queryable).replay(this); // 重放操作序列，调用本类的各个方法来构建RelNode树
      return requireNonNull(rel, "rel"); // 返回构建好的RelNode，确保rel不为null
    }
    if (queryable instanceof AbstractTableQueryable) { // 如果Queryable是表查询类型（直接查询表）
      final AbstractTableQueryable tableQueryable = // 将Queryable转换为AbstractTableQueryable类型
          (AbstractTableQueryable) queryable;
      final QueryableTable table = tableQueryable.table; // 获取底层的QueryableTable对象
      final CalciteSchema.TableEntry tableEntry = // 在CalciteSchema中创建或获取表条目
          CalciteSchema.from(tableQueryable.schema) // 从schema中获取CalciteSchema对象
              .add(tableQueryable.tableName, tableQueryable.table); // 添加表名和表对象到schema中
      final RelOptTableImpl relOptTable = // 创建RelOptTableImpl对象，表示优化器可操作的表
          RelOptTableImpl.create(null, table.getRowType(translator.typeFactory), // 使用表的行类型创建
              tableEntry, null); // 传入表条目，最后一个参数为null表示没有额外的表属性
      if (table instanceof TranslatableTable) { // 如果表实现了TranslatableTable接口（可以自行转换为RelNode）
        return ((TranslatableTable) table).toRel(translator.toRelContext(), // 调用表的toRel方法自行转换
            relOptTable); // 传入RelOptTable参数
      } else { // 如果表没有实现TranslatableTable接口
        return LogicalTableScan.create(translator.cluster, relOptTable, ImmutableList.of()); // 创建一个逻辑表扫描节点
      }
    }
    return translator.translate( // 对于其他类型的Queryable，使用转换器进行转换
        requireNonNull( // 确保表达式不为null
            queryable.getExpression(), // 获取Queryable的表达式
            () -> "null expression from " + queryable)); // 如果为null，提供错误信息
  }

  /** Sets the output of this event. 设置当前事件的输出关系表达式 */
  private void setRel(RelNode rel) { // 设置rel成员变量的值
    this.rel = rel; // 将传入的RelNode赋值给成员变量rel，这是构建关系表达式树的关键步骤
  }

  // ~ Methods from QueryableFactory ----------------------------------------- 以下方法来自QueryableFactory接口

  @Override public <TAccumulate, TResult> TResult aggregate( // 聚合函数：对序列应用累加器函数和结果选择器
      Queryable<T> source, // 源查询序列
      TAccumulate seed, // 累加器的初始值
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func, // 累加器函数，接收累加器值和元素，返回新的累加器值
      FunctionExpression<Function1<TAccumulate, TResult>> selector) { // 结果选择器函数，将累加器值转换为最终结果
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T aggregate( // 聚合函数：对序列应用累加器函数
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function2<@Nullable T, T, T>> selector) { // 累加器函数，接收两个元素值，返回聚合结果
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TAccumulate> TAccumulate aggregate( // 聚合函数：使用种子值和累加器函数
      Queryable<T> source, // 源查询序列
      TAccumulate seed, // 累加器的初始值
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> selector) { // 累加器函数，接收累加器值和元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public boolean all( // 判断序列中的所有元素是否都满足条件
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试每个元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public boolean any(Queryable<T> source) { // 判断序列是否包含任何元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public boolean any( // 判断序列中是否有任何元素满足条件
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public BigDecimal averageBigDecimal( // 计算BigDecimal类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<BigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取BigDecimal值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public BigDecimal averageNullableBigDecimal( // 计算可空BigDecimal类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取可空BigDecimal值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public double averageDouble( // 计算double类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<DoubleFunction1<T>> selector) { // 选择器函数，从元素中提取double值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Double averageNullableDouble( // 计算可空Double类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableDoubleFunction1<T>> selector) { // 选择器函数，从元素中提取可空Double值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public int averageInteger( // 计算int类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<IntegerFunction1<T>> selector) { // 选择器函数，从元素中提取int值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Integer averageNullableInteger( // 计算可空Integer类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableIntegerFunction1<T>> selector) { // 选择器函数，从元素中提取可空Integer值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public float averageFloat( // 计算float类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<FloatFunction1<T>> selector) { // 选择器函数，从元素中提取float值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Float averageNullableFloat( // 计算可空Float类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableFloatFunction1<T>> selector) { // 选择器函数，从元素中提取可空Float值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public long averageLong( // 计算long类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<LongFunction1<T>> selector) { // 选择器函数，从元素中提取long值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Long averageNullableLong( // 计算可空Long类型值的平均值
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableLongFunction1<T>> selector) { // 选择器函数，从元素中提取可空Long值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> concat( // 连接两个序列
      Queryable<T> source, Enumerable<T> source2) { // 第一个源查询序列，第二个可枚举序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public boolean contains( // 判断序列是否包含指定元素
      Queryable<T> source, T element) { // 源查询序列，要查找的元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public boolean contains( // 使用自定义比较器判断序列是否包含指定元素
      Queryable<T> source, T element, EqualityComparer<T> comparer) { // 源查询序列，要查找的元素，自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public int count(Queryable<T> source) { // 计算序列中的元素数量
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public int count( // 计算满足条件的元素数量
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<@Nullable T> defaultIfEmpty(Queryable<T> source) { // 如果序列为空，返回包含默认值的序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<@PolyNull T> defaultIfEmpty(Queryable<T> source, // 如果序列为空，返回包含指定默认值的序列
      @PolyNull T value) { // 源查询序列，指定的默认值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> distinct( // 返回序列中的不重复元素（去重）
      Queryable<T> source) { // 源查询序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> distinct( // 使用自定义比较器返回不重复元素
      Queryable<T> source, EqualityComparer<T> comparer) { // 源查询序列，自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T elementAt(Queryable<T> source, int index) { // 返回序列中指定索引位置的元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T elementAtOrDefault(Queryable<T> source, int index) { // 返回指定索引位置的元素，如果索引越界则返回默认值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> except( // 返回差集（在第一个序列中但不在第二个序列中的元素）
      Queryable<T> source, Enumerable<T> enumerable) { // 第一个源查询序列，第二个可枚举序列
    return except(source, enumerable, false); // 调用重载方法，all参数为false表示不保留重复项
  }

  @Override public Queryable<T> except( // 返回差集，可选择是否保留重复项
      Queryable<T> source, Enumerable<T> enumerable, boolean all) { // 第一个源查询序列，第二个可枚举序列，是否保留重复项
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> except( // 使用自定义比较器返回差集
      Queryable<T> source, // 第一个源查询序列
      Enumerable<T> enumerable, // 第二个可枚举序列
      EqualityComparer<T> tEqualityComparer) { // 自定义相等比较器
    return except(source, enumerable, tEqualityComparer, false); // 调用重载方法，all参数为false
  }

  @Override public Queryable<T> except( // 使用自定义比较器返回差集，可选择是否保留重复项
      Queryable<T> source, // 第一个源查询序列
      Enumerable<T> enumerable, // 第二个可枚举序列
      EqualityComparer<T> tEqualityComparer, // 自定义相等比较器
      boolean all) { // 是否保留重复项
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T first(Queryable<T> source) { // 返回序列的第一个元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T first( // 返回满足条件的第一个元素
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T firstOrDefault(Queryable<T> source) { // 返回序列的第一个元素，如果序列为空则返回默认值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T firstOrDefault( // 返回满足条件的第一个元素，如果没有则返回默认值
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy( // 根据键选择器对序列元素进行分组
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取分组键
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey> Queryable<Grouping<TKey, T>> groupBy( // 使用自定义比较器根据键选择器分组
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      EqualityComparer<TKey> comparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 根据键选择器分组，并使用元素选择器转换元素
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector) { // 元素选择器函数，转换每个元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK( // 根据键选择器分组，并使用结果选择器创建结果序列
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> // 结果选择器函数，接收键和元素组
          resultSelector) { // 返回 TResult 类型的结果
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 使用自定义比较器分组并转换元素
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器函数
      EqualityComparer<TKey> comparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey, TResult> Queryable<TResult> groupByK( // 使用自定义比较器分组并创建结果
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> // 元素选择器函数（这里命名可能有误，实际是结果选择器）
          elementSelector, // 接收键和元素组
      EqualityComparer<TKey> comparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy( // 分组、转换元素并创建结果序列
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器函数
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> // 结果选择器函数，接收键和转换后的元素组
          resultSelector) { // 返回 TResult 类型的结果
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey, TElement, TResult> Queryable<TResult> groupBy( // 使用自定义比较器分组、转换元素并创建结果
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器函数
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> // 结果选择器函数
          resultSelector, // 接收键和转换后的元素组
      EqualityComparer<TKey> comparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin( // 分组连接：基于键匹配两个序列，并将匹配的内部序列元素分组
      Queryable<T> source, // 外部序列
      Enumerable<TInner> inner, // 内部可枚举序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> // 结果选择器函数，接收外部元素和匹配的内部元素组
          resultSelector) { // 返回 TResult 类型的结果
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> groupJoin( // 使用自定义比较器的分组连接
      Queryable<T> source, // 外部序列
      Enumerable<TInner> inner, // 内部可枚举序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> // 结果选择器函数
          resultSelector, // 接收外部元素和匹配的内部元素组
      EqualityComparer<TKey> comparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> intersect( // 返回交集（两个序列中都存在的元素）
      Queryable<T> source, Enumerable<T> enumerable) { // 第一个源查询序列，第二个可枚举序列
    return intersect(source, enumerable, false); // 调用重载方法，all参数为false表示不保留重复项
  }

  @Override public Queryable<T> intersect( // 返回交集，可选择是否保留重复项
      Queryable<T> source, Enumerable<T> enumerable, boolean all) { // 第一个源查询序列，第二个可枚举序列，是否保留重复项
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> intersect( // 使用自定义比较器返回交集
      Queryable<T> source, // 第一个源查询序列
      Enumerable<T> enumerable, // 第二个可枚举序列
      EqualityComparer<T> tEqualityComparer) { // 自定义相等比较器
    return intersect(source, enumerable, tEqualityComparer, false); // 调用重载方法，all参数为false
  }

  @Override public Queryable<T> intersect( // 使用自定义比较器返回交集，可选择是否保留重复项
      Queryable<T> source, // 第一个源查询序列
      Enumerable<T> enumerable, // 第二个可枚举序列
      EqualityComparer<T> tEqualityComparer, boolean all) { // 自定义相等比较器，是否保留重复项
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join( // 内连接：基于键匹配两个序列的元素
      Queryable<T> source, // 外部序列
      Enumerable<TInner> inner, // 内部可枚举序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector) { // 结果选择器函数，接收匹配的元素对
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TInner, TKey, TResult> Queryable<TResult> join( // 使用自定义比较器的内连接
      Queryable<T> source, // 外部序列
      Enumerable<TInner> inner, // 内部可枚举序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器函数
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器函数
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector, // 结果选择器函数
      EqualityComparer<TKey> comparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T last(Queryable<T> source) { // 返回序列的最后一个元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T last( // 返回满足条件的最后一个元素
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T lastOrDefault(Queryable<T> source) { // 返回序列的最后一个元素，如果序列为空则返回默认值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T lastOrDefault( // 返回满足条件的最后一个元素，如果没有则返回默认值
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public long longCount(Queryable<T> source) { // 计算序列中的元素数量（返回long类型）
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public long longCount( // 计算满足条件的元素数量（返回long类型）
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T max(Queryable<T> source) { // 返回序列中的最大值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TResult extends Comparable<TResult>> TResult max( // 使用选择器函数返回最大值
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TResult>> selector) { // 选择器函数，从元素中提取可比较的值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T min(Queryable<T> source) { // 返回序列中的最小值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TResult extends Comparable<TResult>> TResult min( // 使用选择器函数返回最小值
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TResult>> selector) { // 选择器函数，从元素中提取可比较的值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TResult> Queryable<TResult> ofType( // 筛选指定类型的元素
      Queryable<T> source, Class<TResult> clazz) { // 源查询序列，要筛选的类型
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <T2> Queryable<T2> cast( // 将序列的元素强制转换为指定类型
      Queryable<T> source, // 源查询序列
      Class<T2> clazz) { // 目标类型
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderBy( // 按升序排序序列
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取排序键
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey> OrderedQueryable<T> orderBy( // 使用自定义比较器按升序排序
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator) { // 自定义比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey extends Comparable> OrderedQueryable<T> orderByDescending( // 按降序排序序列
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取排序键
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey> OrderedQueryable<T> orderByDescending( // 使用自定义比较器按降序排序
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator) { // 自定义比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> reverse( // 反转序列中元素的顺序
      Queryable<T> source) { // 源查询序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TResult> Queryable<TResult> select( // 投影操作：将序列中的每个元素投影为新形式（SELECT子句）
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, TResult>> selector) { // 选择器函数，将元素转换为新形式
    RelNode child = toRel(source); // 将源Queryable转换为RelNode，作为子节点
    List<RexNode> nodes = translator.toRexList(selector, child); // 将选择器表达式转换为RexNode列表（表达式节点）
    setRel( // 设置当前的关系表达式节点
        LogicalProject.create(child, ImmutableList.of(), nodes, (List<String>)  null, // 创建逻辑投影节点，传入子节点、空提示、投影表达式列表、字段名列表为null
            ImmutableSet.of())); // 空的标记集合
    return castNonNull(null); // 返回null（虚拟结果，实际效果是设置了rel成员变量）
  }

  @Override public <TResult> Queryable<TResult> selectN( // 投影操作，选择器函数包含元素索引
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function2<T, Integer, TResult>> selector) { // 选择器函数，接收元素和索引
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TResult> Queryable<TResult> selectMany( // 多级投影：将每个元素投影到一个序列，然后将这些序列展平为一个序列
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, Enumerable<TResult>>> selector) { // 选择器函数，将元素转换为可枚举序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TResult> Queryable<TResult> selectManyN( // 多级投影，选择器函数包含元素索引
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function2<T, Integer, Enumerable<TResult>>> selector) { // 选择器函数，接收元素和索引，返回可枚举序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TCollection, TResult> Queryable<TResult> selectMany( // 多级投影并应用结果选择器
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function2<T, Integer, Enumerable<TCollection>>> // 集合选择器函数
          collectionSelector, // 接收元素和索引，返回可枚举序列
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) { // 结果选择器函数，接收源元素和集合元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TCollection, TResult> Queryable<TResult> selectManyN( // 多级投影并应用结果选择器（带索引）
      Queryable<T> source, // 源查询序列
      FunctionExpression<Function1<T, Enumerable<TCollection>>> // 集合选择器函数
          collectionSelector, // 接收元素，返回可枚举序列
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) { // 结果选择器函数
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public boolean sequenceEqual( // 判断两个序列是否相等（元素顺序和值都相同）
      Queryable<T> source, Enumerable<T> enumerable) { // 第一个源查询序列，第二个可枚举序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public boolean sequenceEqual( // 使用自定义比较器判断两个序列是否相等
      Queryable<T> source, // 第一个源查询序列
      Enumerable<T> enumerable, // 第二个可枚举序列
      EqualityComparer<T> tEqualityComparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T single(Queryable<T> source) { // 返回序列的唯一元素，如果序列不只有一个元素则抛出异常
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T single( // 返回满足条件的唯一元素，如果没有或超过一个则抛出异常
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T singleOrDefault(Queryable<T> source) { // 返回序列的唯一元素，如果序列为空则返回默认值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public T singleOrDefault( // 返回满足条件的唯一元素，如果没有则返回默认值，如果有多个则抛出异常
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> skip( // 跳过序列中指定数量的元素，返回剩余元素（OFFSET）
      Queryable<T> source, int count) { // 源查询序列，要跳过的元素数量
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> skipWhile( // 跳过满足条件的元素，直到遇到不满足条件的元素
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> skipWhileN( // 跳过满足条件的元素，谓词函数包含元素索引
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate2<T, Integer>> predicate) { // 谓词函数，接收元素和索引
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public BigDecimal sumBigDecimal( // 计算BigDecimal类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<BigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取BigDecimal值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public BigDecimal sumNullableBigDecimal( // 计算可空BigDecimal类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) { // 选择器函数，从元素中提取可空BigDecimal值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public double sumDouble( // 计算double类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<DoubleFunction1<T>> selector) { // 选择器函数，从元素中提取double值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Double sumNullableDouble( // 计算可空Double类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableDoubleFunction1<T>> selector) { // 选择器函数，从元素中提取可空Double值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public int sumInteger( // 计算int类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<IntegerFunction1<T>> selector) { // 选择器函数，从元素中提取int值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Integer sumNullableInteger( // 计算可空Integer类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableIntegerFunction1<T>> selector) { // 选择器函数，从元素中提取可空Integer值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public long sumLong( // 计算long类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<LongFunction1<T>> selector) { // 选择器函数，从元素中提取long值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Long sumNullableLong( // 计算可空Long类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableLongFunction1<T>> selector) { // 选择器函数，从元素中提取可空Long值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public float sumFloat( // 计算float类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<FloatFunction1<T>> selector) { // 选择器函数，从元素中提取float值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Float sumNullableFloat( // 计算可空Float类型值的总和
      Queryable<T> source, // 源查询序列
      FunctionExpression<NullableFloatFunction1<T>> selector) { // 选择器函数，从元素中提取可空Float值
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> take( // 返回序列中指定数量的元素（LIMIT）
      Queryable<T> source, int count) { // 源查询序列，要返回的元素数量
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> takeWhile( // 返回满足条件的元素，直到遇到不满足条件的元素
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> takeWhileN( // 返回满足条件的元素，谓词函数包含元素索引
      Queryable<T> source, // 源查询序列
      FunctionExpression<Predicate2<T, Integer>> predicate) { // 谓词函数，接收元素和索引
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenBy( // 按升序对已排序序列进行后续排序（二级排序）
      OrderedQueryable<T> source, // 已排序的序列
      FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取排序键
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey> OrderedQueryable<T> thenBy( // 使用自定义比较器按升序进行后续排序
      OrderedQueryable<T> source, // 已排序的序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator) { // 自定义比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey extends Comparable<TKey>> OrderedQueryable<T> thenByDescending( // 按降序对已排序序列进行后续排序
      OrderedQueryable<T> source, // 已排序的序列
      FunctionExpression<Function1<T, TKey>> keySelector) { // 键选择器函数，从元素中提取排序键
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <TKey> OrderedQueryable<T> thenByDescending( // 使用自定义比较器按降序进行后续排序
      OrderedQueryable<T> source, // 已排序的序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator) { // 自定义比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> union( // 返回并集（两个序列中所有不重复的元素）
      Queryable<T> source, Enumerable<T> source1) { // 第一个源查询序列，第二个可枚举序列
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> union( // 使用自定义比较器返回并集
      Queryable<T> source, // 第一个源查询序列
      Enumerable<T> source1, // 第二个可枚举序列
      EqualityComparer<T> tEqualityComparer) { // 自定义相等比较器
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public Queryable<T> where( // 过滤操作：根据谓词筛选序列中的元素（WHERE子句）
      Queryable<T> source, // 源查询序列
      FunctionExpression<? extends Predicate1<T>> predicate) { // 谓词函数，用于测试元素是否满足条件
    RelNode child = toRel(source); // 将源Queryable转换为RelNode，作为子节点
    RexNode node = translator.toRex(predicate, child); // 将谓词表达式转换为RexNode（条件表达式节点）
    setRel(LogicalFilter.create(child, node)); // 设置当前的关系表达式节点，创建逻辑过滤节点（WHERE条件）
    return source; // 返回源Queryable（虚拟结果，实际效果是设置了rel成员变量）
  }

  @Override public Queryable<T> whereN( // 过滤操作，谓词函数包含元素索引
      Queryable<T> source, // 源查询序列
      FunctionExpression<? extends Predicate2<T, Integer>> predicate) { // 谓词函数，接收元素和索引
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }

  @Override public <T1, TResult> Queryable<TResult> zip( // 将两个序列的元素按索引配对，并应用结果选择器
      Queryable<T> source, // 第一个源查询序列
      Enumerable<T1> source1, // 第二个可枚举序列
      FunctionExpression<Function2<T, T1, TResult>> resultSelector) { // 结果选择器函数，接收两个序列的配对元素
    throw new UnsupportedOperationException(); // 当前未实现，抛出不支持操作异常
  }
}
