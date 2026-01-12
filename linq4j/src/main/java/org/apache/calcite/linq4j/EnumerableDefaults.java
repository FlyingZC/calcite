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
import org.apache.calcite.linq4j.function.Function0;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.function.Function2;
import org.apache.calcite.linq4j.function.Functions;
import org.apache.calcite.linq4j.function.IntegerFunction1;
import org.apache.calcite.linq4j.function.LongFunction1;
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1;
import org.apache.calcite.linq4j.function.NullableDoubleFunction1;
import org.apache.calcite.linq4j.function.NullableFloatFunction1;
import org.apache.calcite.linq4j.function.NullableIntegerFunction1;
import org.apache.calcite.linq4j.function.NullableLongFunction1;
import org.apache.calcite.linq4j.function.Predicate1;
import org.apache.calcite.linq4j.function.Predicate2;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.framework.qual.HasQualifierParameter;

import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.apache.calcite.linq4j.Linq4j.CollectionEnumerable;
import static org.apache.calcite.linq4j.Linq4j.ListEnumerable;
import static org.apache.calcite.linq4j.Nullness.castNonNull;
import static org.apache.calcite.linq4j.function.Functions.adapt;

import static java.util.Objects.requireNonNull;

/**
 * Default implementations of methods in the {@link Enumerable} interface.
 * // Enumerable接口中方法的默认实现类
 * // 这个类提供了LINQ(Language Integrated Query)操作的标准实现
 * // 包括聚合、过滤、投影、连接、排序等常用操作
 * // 所有方法都是静态的,可以直接通过类名调用,无需实例化
 * // 这个类是Calcite框架中linq4j模块的核心类之一
 * // 提供了类似.NET LINQ的查询操作符实现
 */
public abstract class EnumerableDefaults {

  /**
   * Applies an accumulator function over a sequence.
   * // 对序列应用累加器函数
   * // 这是最简单的聚合操作,从第一个元素开始,依次对每个元素应用累加器函数
   * // 如果序列为空,返回null
   */
  public static <TSource> @Nullable TSource aggregate(Enumerable<TSource> source, // 源序列
      Function2<@Nullable TSource, TSource, TSource> func) { // 累加器函数,接收当前累加值和下一个元素,返回新的累加值
    try (Enumerator<TSource> os = source.enumerator()) { // 获取序列的枚举器,使用try-with-resources确保资源释放
      if (!os.moveNext()) { // 尝试移动到第一个元素
        return null; // 如果序列为空,返回null
      }
      TSource result = os.current(); // 获取第一个元素作为初始累加值
      while (os.moveNext()) { // 遍历剩余的元素
        TSource o = os.current(); // 获取当前元素
        result = func.apply(result, o); // 应用累加器函数,更新累加值
      }
      return result; // 返回最终的累加值
    } // 枚举器自动关闭
  }

  /**
   * Applies an accumulator function over a
   * sequence. The specified seed value is used as the initial
   * accumulator value.
   * // 对序列应用累加器函数,使用指定的种子值作为初始累加值
   * // 种子值允许累加值的类型与源序列元素的类型不同
   */
  public static <TSource, TAccumulate> TAccumulate aggregate( // TSource:源元素类型,TAccumulate:累加值类型
      Enumerable<TSource> source, // 源序列
      TAccumulate seed, // 种子值,作为初始累加值
      Function2<TAccumulate, TSource, TAccumulate> func) { // 累加器函数,接收当前累加值和下一个元素
    TAccumulate result = seed; // 使用种子值初始化累加结果
    try (Enumerator<TSource> os = source.enumerator()) { // 获取序列的枚举器
      while (os.moveNext()) { // 遍历所有元素
        TSource o = os.current(); // 获取当前元素
        result = func.apply(result, o); // 应用累加器函数,更新累加值
      }
      return result; // 返回最终的累加值
    } // 枚举器自动关闭
  }

  /**
   * Applies an accumulator function over a
   * sequence. The specified seed value is used as the initial
   * accumulator value, and the specified function is used to select
   * the result value.
   * // 对序列应用累加器函数,使用指定的种子值作为初始累加值,并使用指定函数选择结果值
   * // 这是最灵活的聚合形式,允许累加值类型、源元素类型和结果类型都不同
   */
  public static <TSource, TAccumulate, TResult> TResult aggregate( // TSource:源元素类型,TAccumulate:累加值类型,TResult:结果类型
      Enumerable<TSource> source, // 源序列
      TAccumulate seed, // 种子值,作为初始累加值
      Function2<TAccumulate, TSource, TAccumulate> func, // 累加器函数,接收当前累加值和下一个元素
      Function1<TAccumulate, TResult> selector) { // 结果选择器函数,将最终累加值转换为结果
    TAccumulate accumulate = seed; // 使用种子值初始化累加值
    try (Enumerator<TSource> os = source.enumerator()) { // 获取序列的枚举器
      while (os.moveNext()) { // 遍历所有元素
        TSource o = os.current(); // 获取当前元素
        accumulate = func.apply(accumulate, o); // 应用累加器函数,更新累加值
      }
      return selector.apply(accumulate); // 应用结果选择器,将累加值转换为最终结果
    } // 枚举器自动关闭
  }

  /**
   * Determines whether all elements of a sequence
   * satisfy a condition.
   * // 确定序列中的所有元素是否都满足条件
   * // 如果序列为空,返回true(空集合的所有元素都满足任何条件)
   * // 一旦发现不满足条件的元素,立即返回false(短路求值)
   */
  public static <TSource> boolean all(Enumerable<TSource> enumerable, // 源序列
      Predicate1<TSource> predicate) { // 谓词函数,用于测试每个元素是否满足条件
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      while (os.moveNext()) { // 遍历所有元素
        TSource o = os.current(); // 获取当前元素
        if (!predicate.apply(o)) { // 测试元素是否满足条件
          return false; // 如果不满足,立即返回false
        }
      }
      return true; // 所有元素都满足条件,返回true
    } // 枚举器自动关闭
  }

  /**
   * Determines whether a sequence contains any
   * elements.
   * // 确定序列是否包含任何元素
   * // 等价于检查序列长度是否大于0
   */
  public static boolean any(Enumerable enumerable) { // 源序列
    return enumerable.enumerator().moveNext(); // 尝试移动到第一个元素,返回是否成功
  }

  /**
   * Determines whether any element of a sequence
   * satisfies a condition.
   * // 确定序列中是否有任何元素满足条件
   * // 一旦发现满足条件的元素,立即返回true(短路求值)
   */
  public static <TSource> boolean any(Enumerable<TSource> enumerable, // 源序列
      Predicate1<TSource> predicate) { // 谓词函数,用于测试每个元素是否满足条件
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      while (os.moveNext()) { // 遍历所有元素
        TSource o = os.current(); // 获取当前元素
        if (predicate.apply(o)) { // 测试元素是否满足条件
          return true; // 如果满足,立即返回true
        }
      }
      return false; // 没有元素满足条件,返回false
    } // 枚举器自动关闭
  }

  /**
   * Returns the input typed as {@code Enumerable<TSource>}.
   *
   * <p>This method has no effect other than to change the compile-time type of
   * source from a type that implements {@code Enumerable<TSource>} to
   * {@code Enumerable<TSource>} itself.
   * // 返回输入的Enumerable<TSource>类型
   * // 这个方法除了改变编译时类型外没有其他效果
   * // 当一个类型实现了Enumerable<TSource>但有自己的查询方法时,可以使用此方法隐藏自定义方法
   *
   * <p>{@code AsEnumerable<TSource>(Enumerable<TSource>)} can be used to choose
   * between query implementations when a sequence implements
   * {@code Enumerable<TSource>} but also has a different set of public query
   * methods available. For example, given a generic class {@code Table} that
   * implements {@code Enumerable<TSource>} and has its own methods such as
   * {@code where}, {@code select}, and {@code selectMany}, a call to
   * {@code where} would invoke the public {@code where} method of
   * {@code Table}. A {@code Table} type that represents a database table could
   * have a {@code where} method that takes the predicate argument as an
   * expression tree and converts the tree to SQL for remote execution. If
   * remote execution is not desired, for example because the predicate invokes
   * a local method, the {@code asEnumerable<TSource>} method can be used to
   * hide the custom methods and instead make the standard query operators
   * available.
   * // 例如,数据库表类可能有自己的where方法将谓词转换为SQL执行
   * // 如果不想远程执行(比如谓词调用本地方法),可以使用asEnumerable隐藏自定义方法
   */
  public static <TSource> Enumerable<TSource> asEnumerable( // 返回Enumerable<TSource>类型
      Enumerable<TSource> enumerable) { // 源序列
    return enumerable; // 直接返回输入,仅改变编译时类型
  }

  /**
   * Converts an Enumerable to an IQueryable.
   *
   * <p>Analogous to the LINQ's Enumerable.AsQueryable extension method.
   * // 将Enumerable转换为IQueryable
   * // 类似于LINQ的Enumerable.AsQueryable扩展方法
   * // IQueryable允许构建表达式树用于远程查询(如数据库查询)
   *
   * @param enumerable Enumerable
   * @param <TSource> Element type
   *
   * @return A queryable
   */
  public static <TSource> Queryable<TSource> asQueryable( // 返回IQueryable<TSource>类型
      Enumerable<TSource> enumerable) { // 源序列
    throw Extensions.todo(); // 尚未实现
  }

  /**
   * Computes the average of a sequence of Decimal
   * values that are obtained by invoking a transform function on
   * each element of the input sequence.
   * // 计算序列中BigDecimal值的平均值
   * // 通过对输入序列的每个元素调用转换函数获得BigDecimal值
   */
  public static <TSource> BigDecimal average(Enumerable<TSource> source, // 源序列
      BigDecimalFunction1<TSource> selector) { // 转换函数,将每个元素转换为BigDecimal
    return sum(source, selector).divide(BigDecimal.valueOf(longCount(source))); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of nullable
   * Decimal values that are obtained by invoking a transform
   * function on each element of the input sequence.
   * // 计算序列中可空BigDecimal值的平均值
   */
  public static <TSource> BigDecimal average(Enumerable<TSource> source, // 源序列
      NullableBigDecimalFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空BigDecimal
    return sum(source, selector).divide(BigDecimal.valueOf(longCount(source))); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of Double
   * values that are obtained by invoking a transform function on
   * each element of the input sequence.
   * // 计算序列中double值的平均值
   */
  public static <TSource> double average(Enumerable<TSource> source, // 源序列
      DoubleFunction1<TSource> selector) { // 转换函数,将每个元素转换为double
    return sum(source, selector) / longCount(source); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of nullable
   * Double values that are obtained by invoking a transform
   * function on each element of the input sequence.
   * // 计算序列中可空Double值的平均值
   */
  public static <TSource> Double average(Enumerable<TSource> source, // 源序列
      NullableDoubleFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Double
    return sum(source, selector) / longCount(source); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of int values
   * that are obtained by invoking a transform function on each
   * element of the input sequence.
   * // 计算序列中int值的平均值
   */
  public static <TSource> int average(Enumerable<TSource> source, // 源序列
      IntegerFunction1<TSource> selector) { // 转换函数,将每个元素转换为int
    return sum(source, selector) / count(source); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of nullable
   * int values that are obtained by invoking a transform function
   * on each element of the input sequence.
   * // 计算序列中可空Integer值的平均值
   */
  public static <TSource> Integer average(Enumerable<TSource> source, // 源序列
      NullableIntegerFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Integer
    return sum(source, selector) / count(source); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of long values
   * that are obtained by invoking a transform function on each
   * element of the input sequence.
   * // 计算序列中long值的平均值
   */
  public static <TSource> long average(Enumerable<TSource> source, // 源序列
      LongFunction1<TSource> selector) { // 转换函数,将每个元素转换为long
    return sum(source, selector) / longCount(source); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of nullable
   * long values that are obtained by invoking a transform function
   * on each element of the input sequence.
   * // 计算序列中可空Long值的平均值
   */
  public static <TSource> Long average(Enumerable<TSource> source, // 源序列
      NullableLongFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Long
    return sum(source, selector) / longCount(source); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of Float
   * values that are obtained by invoking a transform function on
   * each element of the input sequence.
   * // 计算序列中float值的平均值
   */
  public static <TSource> float average(Enumerable<TSource> source, // 源序列
      FloatFunction1<TSource> selector) { // 转换函数,将每个元素转换为float
    return sum(source, selector) / longCount(source); // 总和除以元素数量
  }

  /**
   * Computes the average of a sequence of nullable
   * Float values that are obtained by invoking a transform
   * function on each element of the input sequence.
   * // 计算序列中可空Float值的平均值
   */
  public static <TSource> Float average(Enumerable<TSource> source, // 源序列
      NullableFloatFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Float
    return sum(source, selector) / longCount(source); // 总和除以元素数量
  }

  /**
   * Analogous to LINQ's Enumerable.Cast extension method.
   * // 类似于LINQ的Enumerable.Cast扩展方法
   * // 将序列中的元素转换为指定类型
   *
   * @param clazz Target type
   * @param <T2> Target type
   *
   * @return Collection of T2
   */
  public static <TSource, T2> Enumerable<T2> cast( // 返回指定类型的序列
      final Enumerable<TSource> source, final Class<T2> clazz) { // 源序列和目标类型
    return new AbstractEnumerable<T2>() { // 创建抽象的可枚举对象
      @Override public Enumerator<T2> enumerator() { // 创建枚举器
        return new CastingEnumerator<>(source.enumerator(), clazz); // 返回类型转换枚举器
      }
    };
  }

  /**
   * Concatenates two sequences.
   * // 连接两个序列
   * // 将两个序列按顺序合并为一个序列
   */
  public static <TSource> Enumerable<TSource> concat( // 返回连接后的序列
      Enumerable<TSource> enumerable0, Enumerable<TSource> enumerable1) { // 两个要连接的序列
    //noinspection unchecked
    return Linq4j.concat( // 使用Linq4j的concat方法连接序列
        Arrays.asList(enumerable0, enumerable1)); // 将两个序列作为列表传递
  }

  /**
   * Determines whether a sequence contains a specified
   * element by using the default equality comparer.
   * // 确定序列是否包含指定元素,使用默认相等比较器
   */
  public static <TSource> boolean contains(Enumerable<TSource> enumerable, // 源序列
      TSource element) { // 要查找的元素
    // Implementations of Enumerable backed by a Collection call
    // Collection.contains, which may be more efficient, not this method.
    // 由Collection支持的Enumerable实现会调用Collection.contains,可能更高效
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      while (os.moveNext()) { // 遍历所有元素
        TSource o = os.current(); // 获取当前元素
        if (Objects.equals(o, element)) { // 使用Objects.equals比较元素是否相等
          return true; // 找到匹配元素,返回true
        }
      }
      return false; // 未找到匹配元素,返回false
    } // 枚举器自动关闭
  }

  /**
   * Determines whether a sequence contains a specified
   * element by using a specified {@code EqualityComparer<TSource>}.
   * // 确定序列是否包含指定元素,使用指定的相等比较器
   */
  public static <TSource> boolean contains(Enumerable<TSource> enumerable, // 源序列
      TSource element, EqualityComparer<TSource> comparer) { // 要查找的元素和相等比较器
    for (TSource o : enumerable) { // 遍历所有元素
      if (comparer.equal(o, element)) { // 使用指定的比较器比较元素
        return true; // 找到匹配元素,返回true
      }
    }
    return false; // 未找到匹配元素,返回false
  }

  /**
   * Returns the number of elements in a
   * sequence.
   * // 返回序列中的元素数量
   */
  public static <TSource> int count(Enumerable<TSource> enumerable) { // 源序列
    return (int) longCount(enumerable, Functions.truePredicate1()); // 调用longCount并强制转换为int
  }

  /**
   * Returns a number that represents how many elements
   * in the specified sequence satisfy a condition.
   * // 返回指定序列中满足条件的元素数量
   */
  public static <TSource> int count(Enumerable<TSource> enumerable, // 源序列
      Predicate1<TSource> predicate) { // 谓词函数,用于测试元素是否满足条件
    return (int) longCount(enumerable, predicate); // 调用longCount并强制转换为int
  }

  /**
   * Returns the elements of the specified sequence or
   * the type parameter's default value in a singleton collection if
   * the sequence is empty.
   * // 返回指定序列的元素,如果序列为空则返回类型参数的默认值
   */
  public static <TSource> Enumerable<@Nullable TSource> defaultIfEmpty( // 返回可空的序列
      Enumerable<TSource> enumerable) { // 源序列
    return defaultIfEmpty(enumerable, null); // 调用重载方法,默认值为null
  }

  /**
   * Returns the elements of the specified sequence or
   * the specified value in a singleton collection if the sequence
   * is empty.
   * // 返回指定序列的元素,如果序列为空则返回指定值
   *
   * <p>If {@code value} is not null, the result is never null.
   * // 如果value不为null,结果永远不会为null
   */
  @SuppressWarnings("return.type.incompatible")
  public static <TSource> Enumerable<@PolyNull TSource> defaultIfEmpty( // 返回序列
      Enumerable<TSource> enumerable, // 源序列
      @PolyNull TSource value) { // 如果序列为空时返回的默认值
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      if (os.moveNext()) { // 尝试移动到第一个元素
        return Linq4j.<TSource>asEnumerable(() -> new Iterator<TSource>() { // 序列不为空,返回原始序列

          private boolean nonFirst; // 标记是否已经处理过第一个元素

          private @Nullable Iterator<TSource> rest; // 剩余元素的迭代器

          @Override public boolean hasNext() { // 检查是否还有下一个元素
            return !nonFirst || requireNonNull(rest, "rest").hasNext(); // 第一个元素或剩余元素
          }

          @Override public TSource next() { // 获取下一个元素
            if (nonFirst) { // 如果不是第一个元素
              return requireNonNull(rest, "rest").next(); // 从剩余迭代器获取
            } else { // 如果是第一个元素
              final TSource first = os.current(); // 获取第一个元素
              nonFirst = true; // 标记已处理第一个元素
              rest = Linq4j.enumeratorIterator(os); // 创建剩余元素的迭代器
              return first; // 返回第一个元素
            }
          }

          @Override public void remove() { // 不支持删除操作
            throw new UnsupportedOperationException("remove"); // 抛出异常
          }
        });
      } else { // 序列为空
        return Linq4j.singletonEnumerable(value); // 返回只包含默认值的单元素序列
      }
    } // 枚举器自动关闭
  }

  /**
   * Returns distinct elements from a sequence by using
   * the default {@link EqualityComparer} to compare values.
   * // 返回序列中的不同元素,使用默认相等比较器比较值
   */
  public static <TSource> Enumerable<TSource> distinct( // 返回去重后的序列
      Enumerable<TSource> enumerable) { // 源序列
    final Enumerator<TSource> os = enumerable.enumerator(); // 获取序列的枚举器
    final Set<TSource> set = new HashSet<>(); // 创建HashSet用于去重
    while (os.moveNext()) { // 遍历所有元素
      set.add(os.current()); // 将元素添加到Set中,自动去重
    }
    os.close(); // 关闭枚举器
    return Linq4j.asEnumerable(set); // 将Set转换为Enumerable返回
  }

  /**
   * Returns distinct elements from a sequence by using
   * a specified {@link EqualityComparer} to compare values.
   * // 返回序列中的不同元素,使用指定的相等比较器比较值
   */
  public static <TSource> Enumerable<TSource> distinct( // 返回去重后的序列
      Enumerable<TSource> enumerable, EqualityComparer<TSource> comparer) { // 源序列和相等比较器
    if (comparer == Functions.identityComparer()) { // 如果使用默认比较器
      return distinct(enumerable); // 调用不带比较器的版本
    }
    final Set<Wrapped<TSource>> set = new HashSet<>(); // 创建包装元素的Set
    Function1<TSource, Wrapped<TSource>> wrapper = wrapperFor(comparer); // 创建包装函数
    Function1<Wrapped<TSource>, TSource> unwrapper = unwrapper(); // 创建解包函数
    enumerable.select(wrapper).into(set); // 将元素包装后添加到Set中
    return Linq4j.asEnumerable(set).select(unwrapper); // 解包后返回
  }

  /**
   * Returns the element at a specified index in a
   * sequence.
   * // 返回序列中指定索引处的元素
   */
  public static <TSource> TSource elementAt(Enumerable<TSource> enumerable, // 源序列
      int index) { // 要获取的元素索引
    final ListEnumerable<TSource> list = enumerable instanceof ListEnumerable // 检查是否为ListEnumerable
        ? ((ListEnumerable<TSource>) enumerable) // 如果是,转换为ListEnumerable
        : null; // 否则为null
    if (list != null) { // 如果是ListEnumerable
      return list.toList().get(index); // 直接从列表中获取元素,效率更高
    }
    if (index < 0) { // 如果索引为负数
      throw new IndexOutOfBoundsException(); // 抛出索引越界异常
    }
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      while (true) { // 无限循环
        if (!os.moveNext()) { // 如果没有下一个元素
          throw new IndexOutOfBoundsException(); // 抛出索引越界异常
        }
        if (index == 0) { // 如果索引减到0
          return os.current(); // 返回当前元素
        }
        index--; // 索引减1
      }
    } // 枚举器自动关闭
  }

  /**
   * Returns the element at a specified index in a
   * sequence or a default value if the index is out of
   * range.
   * // 返回序列中指定索引处的元素,如果索引超出范围则返回默认值
   */
  public static <TSource> @Nullable TSource elementAtOrDefault( // 返回可空的元素
      Enumerable<TSource> enumerable, int index) { // 源序列和索引
    final ListEnumerable<TSource> list = enumerable instanceof ListEnumerable // 检查是否为ListEnumerable
        ? ((ListEnumerable<TSource>) enumerable) // 如果是,转换为ListEnumerable
        : null; // 否则为null
    if (index >= 0) { // 如果索引非负
      if (list != null) { // 如果是ListEnumerable
        final List<TSource> rawList = list.toList(); // 转换为列表
        if (index < rawList.size()) { // 如果索引在列表范围内
          return rawList.get(index); // 返回指定索引的元素
        }
      } else { // 如果不是ListEnumerable
        try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
          while (true) { // 无限循环
            if (!os.moveNext()) { // 如果没有下一个元素
              break; // 跳出循环
            }
            if (index == 0) { // 如果索引减到0
              return os.current(); // 返回当前元素
            }
            index--; // 索引减1
          }
        } // 枚举器自动关闭
      }
    }
    return null; // 索引超出范围,返回null
  }

  /**
   * Produces the set difference of two sequences by
   * using the default equality comparer to compare values,
   * eliminate duplicates. (Defined by Enumerable.)
   * // 生成两个序列的集合差集,使用默认相等比较器比较值,消除重复项
   */
  public static <TSource> Enumerable<TSource> except( // 返回差集序列
      Enumerable<TSource> source0, Enumerable<TSource> source1) { // 第一个序列和第二个序列
    return except(source0, source1, false); // 调用重载方法,不保留重复项
  }

  /**
   * Produces the set difference of two sequences by
   * using the default equality comparer to compare values,
   * using {@code all} to indicate whether to eliminate duplicates.
   * (Defined by Enumerable.)
   * // 生成两个序列的集合差集,使用默认相等比较器比较值,all参数指示是否消除重复项
   */
  public static <TSource> Enumerable<TSource> except( // 返回差集序列
      Enumerable<TSource> source0, Enumerable<TSource> source1, boolean all) { // 两个序列和是否保留重复项
    Collection<TSource> collection = all ? HashMultiset.create() : new HashSet<>(); // 根据all创建集合
    source0.into(collection); // 将第一个序列的所有元素添加到集合中
    try (Enumerator<TSource> os = source1.enumerator()) { // 获取第二个序列的枚举器
      while (os.moveNext()) { // 遍历第二个序列的所有元素
        TSource o = os.current(); // 获取当前元素
        @SuppressWarnings("argument.type.incompatible")
        boolean unused = collection.remove(o); // 从集合中移除该元素(如果存在)
      }
      return Linq4j.asEnumerable(collection); // 返回剩余元素的序列
    } // 枚举器自动关闭
  }

  /**
   * Produces the set difference of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, eliminate duplicates.
   * // 生成两个序列的集合差集,使用指定的相等比较器比较值,消除重复项
   */
  public static <TSource> Enumerable<TSource> except( // 返回差集序列
      Enumerable<TSource> source0, Enumerable<TSource> source1, // 两个序列
      EqualityComparer<TSource> comparer) { // 相等比较器
    return except(source0, source1, comparer, false); // 调用重载方法,不保留重复项
  }

  /**
   * Produces the set difference of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, using {@code all} to indicate whether to eliminate duplicates.
   * // 生成两个序列的集合差集,使用指定的相等比较器比较值,all参数指示是否消除重复项
   */
  public static <TSource> Enumerable<TSource> except( // 返回差集序列
      Enumerable<TSource> source0, Enumerable<TSource> source1, // 两个序列
      EqualityComparer<TSource> comparer, boolean all) { // 相等比较器和是否保留重复项
    if (comparer == Functions.identityComparer()) { // 如果使用默认比较器
      return except(source0, source1, all); // 调用不带比较器的版本
    }
    Collection<Wrapped<TSource>> collection = all ? HashMultiset.create() : new HashSet<>(); // 创建包装元素的集合
    Function1<TSource, Wrapped<TSource>> wrapper = wrapperFor(comparer); // 创建包装函数
    source0.select(wrapper).into(collection); // 将第一个序列的元素包装后添加到集合
    try (Enumerator<Wrapped<TSource>> os = // 获取第二个序列包装后的枚举器
             source1.select(wrapper).enumerator()) {
      while (os.moveNext()) { // 遍历所有元素
        Wrapped<TSource> o = os.current(); // 获取当前包装元素
        collection.remove(o); // 从集合中移除该元素
      }
    } // 枚举器自动关闭
    Function1<Wrapped<TSource>, TSource> unwrapper = unwrapper(); // 创建解包函数
    return Linq4j.asEnumerable(collection).select(unwrapper); // 解包后返回
  }

  /**
   * Returns the first element of a sequence. (Defined
   * by Enumerable.)
   * // 返回序列的第一个元素
   */
  public static <TSource> TSource first(Enumerable<TSource> enumerable) { // 源序列
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      if (os.moveNext()) { // 尝试移动到第一个元素
        return os.current(); // 返回第一个元素
      }
      throw new NoSuchElementException(); // 序列为空,抛出异常
    } // 枚举器自动关闭
  }

  /**
   * Returns the first element in a sequence that
   * satisfies a specified condition.
   * // 返回序列中满足指定条件的第一个元素
   */
  public static <TSource> TSource first(Enumerable<TSource> enumerable, // 源序列
      Predicate1<TSource> predicate) { // 谓词函数,用于测试元素是否满足条件
    for (TSource o : enumerable) { // 遍历所有元素
      if (predicate.apply(o)) { // 测试元素是否满足条件
        return o; // 返回第一个满足条件的元素
      }
    }
    throw new NoSuchElementException(); // 没有元素满足条件,抛出异常
  }

  /**
   * Returns the first element of a sequence, or a
   * default value if the sequence contains no elements.
   * // 返回序列的第一个元素,如果序列不包含元素则返回默认值
   */
  public static <TSource> @Nullable TSource firstOrDefault( // 返回可空的第一个元素
        Enumerable<TSource> enumerable) { // 源序列
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      if (os.moveNext()) { // 尝试移动到第一个元素
        return os.current(); // 返回第一个元素
      }
      return null; // 序列为空,返回null
    } // 枚举器自动关闭
  }

  /**
   * Returns the first element of the sequence that
   * satisfies a condition or a default value if no such element is
   * found.
   * // 返回序列中满足指定条件的第一个元素,如果没有找到则返回默认值
   */
  public static <TSource> @Nullable TSource firstOrDefault(Enumerable<TSource> enumerable, // 源序列
      Predicate1<TSource> predicate) { // 谓词函数,用于测试元素是否满足条件
    for (TSource o : enumerable) { // 遍历所有元素
      if (predicate.apply(o)) { // 测试元素是否满足条件
        return o; // 返回第一个满足条件的元素
      }
    }
    return null; // 没有元素满足条件,返回null
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function.
   * // 根据指定的键选择器函数对序列的元素进行分组
   */
  public static <TSource, TKey> Enumerable<Grouping<TKey, TSource>> groupBy( // 返回分组序列
      final Enumerable<TSource> enumerable, // 源序列
      final Function1<TSource, TKey> keySelector) { // 键选择器函数,从每个元素提取分组键
    return enumerable.toLookup(keySelector); // 调用toLookup方法创建查找表
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and compares the keys by using
   * a specified comparer.
   * // 根据指定的键选择器函数对序列的元素进行分组,并使用指定的比较器比较键
   */
  public static <TSource, TKey> Enumerable<Grouping<TKey, TSource>> groupBy( // 返回分组序列
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector, // 源序列和键选择器
      EqualityComparer<TKey> comparer) { // 键相等比较器
    return enumerable.toLookup(keySelector, comparer); // 调用toLookup方法创建查找表
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and projects the elements for
   * each group by using a specified function.
   * // 根据指定的键选择器函数对序列的元素进行分组,并使用指定的函数投影每个组的元素
   */
  public static <TSource, TKey, TElement> Enumerable<Grouping<TKey, TElement>> groupBy( // 返回分组序列
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector, // 源序列和键选择器
      Function1<TSource, TElement> elementSelector) { // 元素选择器函数,投影每个元素
    return enumerable.toLookup(keySelector, elementSelector); // 调用toLookup方法创建查找表
  }

  /**
   * Groups the elements of a sequence according to a
   * key selector function. The keys are compared by using a
   * comparer and each group's elements are projected by using a
   * specified function.
   * // 根据键选择器函数对序列的元素进行分组,使用比较器比较键,并使用指定的函数投影每个组的元素
   */
  public static <TSource, TKey, TElement> Enumerable<Grouping<TKey, TElement>> groupBy( // 返回分组序列
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector, // 源序列和键选择器
      Function1<TSource, TElement> elementSelector, // 元素选择器函数
      EqualityComparer<TKey> comparer) { // 键相等比较器
    return enumerable.toLookup(keySelector, elementSelector, comparer); // 调用toLookup方法创建查找表
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key.
   * // 根据指定的键选择器函数对序列的元素进行分组,并从每个组及其键创建结果值
   */
  public static <TSource, TKey, TResult> Enumerable<TResult> groupBy( // 返回结果序列
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector, // 源序列和键选择器
      final Function2<TKey, Enumerable<TSource>, TResult> resultSelector) { // 结果选择器函数
    return enumerable.toLookup(keySelector) // 创建查找表
        .select(group -> resultSelector.apply(group.getKey(), group)); // 对每个组应用结果选择器
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. The keys are compared by using a
   * specified comparer.
   */
  public static <TSource, TKey, TResult> Enumerable<TResult> groupBy(
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector,
      final Function2<TKey, Enumerable<TSource>, TResult> resultSelector,
      EqualityComparer<TKey> comparer) {
    return enumerable.toLookup(keySelector, comparer)
        .select(group -> resultSelector.apply(group.getKey(), group));
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. The elements of each group are
   * projected by using a specified function.
   */
  public static <TSource, TKey, TElement, TResult> Enumerable<TResult> groupBy(
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector,
      Function1<TSource, TElement> elementSelector,
      final Function2<TKey, Enumerable<TElement>, TResult> resultSelector) {
    return enumerable.toLookup(keySelector, elementSelector)
        .select(group -> resultSelector.apply(group.getKey(), group));
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. Key values are compared by using a
   * specified comparer, and the elements of each group are
   * projected by using a specified function.
   */
  public static <TSource, TKey, TElement, TResult> Enumerable<TResult> groupBy(
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector,
      Function1<TSource, TElement> elementSelector,
      final Function2<TKey, Enumerable<TElement>, TResult> resultSelector,
      EqualityComparer<TKey> comparer) {
    return enumerable.toLookup(keySelector, elementSelector, comparer)
        .select(group -> resultSelector.apply(group.getKey(), group));
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function, initializing an accumulator for each
   * group and adding to it each time an element with the same key is seen.
   * Creates a result value from each accumulator and its key using a
   * specified function.
   */
  public static <TSource, TKey, TAccumulate, TResult> Enumerable<TResult> groupBy(
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder,
      final Function2<TKey, TAccumulate, TResult> resultSelector) {
    return groupBy_(new HashMap<>(), enumerable, keySelector,
        accumulatorInitializer, accumulatorAdder, resultSelector);
  }

  /**
   * Groups the elements of a sequence according to a list of
   * specified key selector functions, initializing an accumulator for each
   * group and adding to it each time an element with the same key is seen.
   * Creates a result value from each accumulator and its key using a
   * specified function.
   *
   * <p>This method exists to support SQL {@code GROUPING SETS}.
   * It does not correspond to any method in {@link Enumerable}.
   */
  public static <TSource, TKey, TAccumulate, TResult> Enumerable<TResult> groupByMultiple(
      Enumerable<TSource> enumerable, List<Function1<TSource, TKey>> keySelectors,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder,
      final Function2<TKey, TAccumulate, TResult> resultSelector) {
    return groupByMultiple_(
        new HashMap<>(),
        enumerable,
        keySelectors,
        accumulatorInitializer,
        accumulatorAdder,
        resultSelector);
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function, initializing an accumulator for each
   * group and adding to it each time an element with the same key is seen.
   * Creates a result value from each accumulator and its key using a
   * specified function. Key values are compared by using a
   * specified comparer.
   */
  public static <TSource, TKey, TAccumulate, TResult> Enumerable<TResult> groupBy(
      Enumerable<TSource> enumerable, Function1<TSource, TKey> keySelector,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder,
      Function2<TKey, TAccumulate, TResult> resultSelector,
      EqualityComparer<TKey> comparer) {
    return groupBy_(
        new WrapMap<>(
            // Java 8 cannot infer return type with HashMap::new is used
            () -> new HashMap<Wrapped<TKey>, TAccumulate>(),
          comparer),
        enumerable,
        keySelector,
        accumulatorInitializer,
        accumulatorAdder,
        resultSelector);
  }

  /**
   * Group keys are sorted already. Key values are compared by using a
   * specified comparator. Groups the elements of a sequence according to a
   * specified key selector function and initializing one accumulator at a time.
   * Go over elements sequentially, adding to accumulator each time an element
   * with the same key is seen. When key changes, creates a result value from the
   * accumulator and then re-initializes the accumulator. In the case of NULL values
   * in group keys, the comparator must be able to support NULL values by giving a
   * consistent sort ordering.
   */
  public static <TSource, TKey, TAccumulate, TResult> Enumerable<TResult> sortedGroupBy(
      Enumerable<TSource> enumerable,
      Function1<TSource, TKey> keySelector,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder,
      final Function2<TKey, TAccumulate, TResult> resultSelector,
      final Comparator<TKey> comparator) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new SortedAggregateEnumerator(
          enumerable, keySelector, accumulatorInitializer,
          accumulatorAdder, resultSelector, comparator);
      }
    };
  }

  /**
   * ASOF join implementation.  For each row in the source enumerable produce exactly one
   * result, similar to a LEFT join.  The row on the right is the one that is the "largest"
   * according to the timestampComparer that matches in key and satisfies the timestampComparator.
   *
   * @param outer               Left input
   * @param inner               Right input
   * @param outerKeySelector    Selects a key from a left input record
   * @param innerKeySelector    Selects a key from the right input record
   * @param resultSelector      Produces the result from a pair (left, right)
   * @param matchComparator     Compares an element from the left input with one from the right
   *                            input and returns 'true' if the timestamp are appropriate
   * @param timestampComparator Compares two elements from the right input and returns
   *                            true if the second is in the right order with respect
   *                            to the ASOF comparison.
   * @param emitNullsOnRight    If true this is a left join.
   */
  public static <TResult, TSource, TInner, TKey> Enumerable<TResult> asofJoin(
      Enumerable<TSource> outer, Enumerable<TInner> inner,
      Function1<TSource, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<TSource, @Nullable TInner, TResult> resultSelector,
      Predicate2<TSource, TInner> matchComparator,
      Comparator<TInner> timestampComparator,
      boolean emitNullsOnRight) {

    // The basic algorithm is simple:
    // - scan and index left collection by key
    // - for each left record keep the best right record, initialized to 'null'
    // - scan the right collection and for each record
    //   - match it against all left collection records with the same key
    //   - if the timestamp is closer, update the right record
    // - emit all items in the index
    Map<TKey, List<TSource>> leftIndex = new HashMap<>();
    // For each left element the corresponding best right element
    Map<TKey, List<@Nullable TInner>> rightIndex = new HashMap<>();
    // Outer elements that have null keys.  Will remain empty if !emitNullsOnRight.
    List<TSource> outerWithNullKeys = new ArrayList<>();
    try (Enumerator<TSource> os = outer.enumerator()) {
      while (os.moveNext()) {
        TSource l = os.current();
        TKey key = outerKeySelector.apply(l);
        if (key == null) {
          // key contains null fields (result of key selector is null)
          if (emitNullsOnRight) {
            outerWithNullKeys.add(l);
          }
        } else {
          List<TSource> left;
          List<@Nullable TInner> right;
          if (!leftIndex.containsKey(key)) {
            left = new ArrayList<>();
            right = new ArrayList<>();
            leftIndex.put(key, left);
            rightIndex.put(key, right);
          } else {
            left = leftIndex.get(key);
            right = rightIndex.get(key);
          }
          left.add(l);
          requireNonNull(right, "right").add(null);
        }
      }
    }
    // Scan right collection
    try (Enumerator<TInner> is = inner.enumerator()) {
      while (is.moveNext()) {
        TInner r = is.current();
        TKey key = innerKeySelector.apply(r);
        if (key == null) {
          // key contains null fields (result of key selector is null)
          continue;
        }
        List<TSource> left = leftIndex.get(key);
        if (left == null) {
          continue;
        }
        assert !left.isEmpty();
        List<@Nullable TInner> best = requireNonNull(rightIndex.get(key));
        assert left.size() == best.size();
        for (int i = 0; i < left.size(); i++) {
          TSource leftElement = left.get(i);
          boolean matches = matchComparator.apply(leftElement, r);
          if (!matches) {
            continue;
          }
          @Nullable TInner bestElement = best.get(i);
          if (bestElement == null) {
            best.set(i, r);
          } else {
            boolean isCloser = timestampComparator.compare(bestElement, r) < 0;
            if (isCloser) {
              best.set(i, r);
            }
          }
        }
      }
    }

    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          final Enumerator<Map.Entry<TKey, List<TSource>>> enumerator =
              new Linq4j.IterableEnumerator<>(leftIndex.entrySet());

          boolean emittingNullKeys = false;  // True when we emit the records with null keys
          @Nullable Enumerator<TSource> left = null;  // Iterates over values with same key
          @Nullable Enumerator<@Nullable TInner> right = null;
          final Enumerator<TSource> leftNullKeys =    // not used for inner ASOF joins
              new Linq4j.IterableEnumerator<>(outerWithNullKeys);

          // This is a small state machine
          // if (emittingNullKeys) {
          //    we are iterating over 'outerWithNullKeys' using 'leftNullKeys'
          // } else {
          //    we are iterating over the 'leftIndex' using 'enumerator'
          //    for each value of the key we iterate advancing
          //        concurrently using 'left' and 'right'
          //    when finished set emittingNullKeys = true
          // }

          @Override public TResult current() {
            if (emittingNullKeys) {
              TSource l = leftNullKeys.current();
              return resultSelector.apply(l, null);
            }

            TSource l = requireNonNull(left, "left").current();
            @Nullable TInner r = requireNonNull(right, "right").current();
            return resultSelector.apply(l, r);
          }

          @Override public boolean moveNext() {
            while (true) {
              boolean hasNext = false;
              if (emittingNullKeys) {
                return leftNullKeys.moveNext();
              } else {
                if (left != null) {
                  // Advance left, right
                  hasNext = left.moveNext();
                  boolean rightHasNext =
                      requireNonNull(right, "right").moveNext();
                  assert hasNext == rightHasNext;
                }
                if (hasNext) {
                  if (!emitNullsOnRight) {
                    @Nullable TInner r =
                        requireNonNull(right, "right").current();
                    if (r == null) {
                      continue;
                    }
                  }
                  return true;
                }
                // Advance enumerator
                hasNext = enumerator.moveNext();
                if (hasNext) {
                  Map.Entry<TKey, List<TSource>> current = enumerator.current();
                  TKey key = current.getKey();
                  List<TSource> value = current.getValue();
                  left = new Linq4j.IterableEnumerator<>(value);
                  List<@Nullable TInner> rightList =
                      requireNonNull(rightIndex.get(key));
                  right = new Linq4j.IterableEnumerator<>(rightList);
                } else {
                  // Done with the data, start emitting records with null keys
                  emittingNullKeys = true;
                }
              }
            }
          }

          @Override public void reset() {
            enumerator.reset();
            left = null;
            right = null;
          }

          @Override public void close() {
            enumerator.close();
            left = null;
            right = null;
          }
        };
      }
    };
  }

  /** Enumerator that evaluates aggregate functions over an input that is sorted
   * by the group key.
   *
   * @param <TSource> left input record type
   * @param <TKey> key type
   * @param <TAccumulate> accumulator type
   * @param <TResult> result type */
  private static class SortedAggregateEnumerator<TSource, TKey, TAccumulate, TResult>
      implements Enumerator<TResult> {
    private final Function1<TSource, TKey> keySelector;
    private final Function0<TAccumulate> accumulatorInitializer;
    private final Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder;
    private final Function2<TKey, TAccumulate, TResult> resultSelector;
    private final Comparator<TKey> comparator;
    private boolean isInitialized;
    private boolean isLastMoveNextFalse;
    private @Nullable TAccumulate curAccumulator;
    private final Enumerator<TSource> enumerator;
    private @Nullable TResult curResult;

    SortedAggregateEnumerator(
        Enumerable<TSource> enumerable,
        Function1<TSource, TKey> keySelector,
        Function0<TAccumulate> accumulatorInitializer,
        Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder,
        final Function2<TKey, TAccumulate, TResult> resultSelector,
        final Comparator<TKey> comparator) {
      this.keySelector = keySelector;
      this.accumulatorInitializer = accumulatorInitializer;
      this.accumulatorAdder = accumulatorAdder;
      this.resultSelector = resultSelector;
      this.comparator = comparator;
      isInitialized = false;
      curAccumulator = null;
      enumerator = enumerable.enumerator();
      curResult = null;
      isLastMoveNextFalse = false;
    }

    @Override public TResult current() {
      if (isLastMoveNextFalse) {
        throw new NoSuchElementException();
      }
      return castNonNull(curResult);
    }

    @Override public boolean moveNext() {
      if (!isInitialized) {
        isInitialized = true;
        // input is empty
        if (!enumerator.moveNext()) {
          isLastMoveNextFalse = true;
          return false;
        }
      } else if (curAccumulator == null) {
        // input has been exhausted.
        isLastMoveNextFalse = true;
        return false;
      }

      if (curAccumulator == null) {
        // TODO: the implementation assumes accumulatorAdder always produces non-nullable values
        // Should a separate boolean field be used to track initialization?
        curAccumulator = accumulatorInitializer.apply();
      }

      // reset result because now it can move to next aggregated result.
      curResult = null;
      TSource o = enumerator.current();
      TKey prevKey = keySelector.apply(o);
      curAccumulator = accumulatorAdder.apply(castNonNull(curAccumulator), o);
      while (enumerator.moveNext()) {
        o = enumerator.current();
        TKey curKey = keySelector.apply(o);
        if (comparator.compare(prevKey, curKey) != 0) {
          // current key is different from previous key, get accumulated results and re-create
          // accumulator for current key.
          curResult = resultSelector.apply(prevKey, castNonNull(curAccumulator));
          curAccumulator = accumulatorInitializer.apply();
          break;
        }
        curAccumulator = accumulatorAdder.apply(castNonNull(curAccumulator), o);
        prevKey = curKey;
      }

      if (curResult == null) {
        // current key is the last key.
        curResult = resultSelector.apply(prevKey, requireNonNull(curAccumulator, "curAccumulator"));
        // no need to keep accumulator for the last key.
        curAccumulator = null;
      }

      return true;
    }

    @Override public void reset() {
      enumerator.reset();
      isInitialized = false;
      curResult = null;
      curAccumulator = null;
      isLastMoveNextFalse = false;
    }

    @Override public void close() {
      enumerator.close();
    }
  }

  private static <TSource, TKey, TAccumulate, TResult> Enumerable<TResult> groupBy_(
      final Map<TKey, TAccumulate> map, Enumerable<TSource> enumerable,
      Function1<TSource, TKey> keySelector,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder,
      final Function2<TKey, TAccumulate, TResult> resultSelector) {
    try (Enumerator<TSource> os = enumerable.enumerator()) {
      while (os.moveNext()) {
        TSource o = os.current();
        TKey key = keySelector.apply(o);
        @SuppressWarnings("argument.type.incompatible")
        TAccumulate accumulator = map.get(key);
        if (accumulator == null) {
          accumulator = accumulatorInitializer.apply();
          accumulator = accumulatorAdder.apply(accumulator, o);
          map.put(key, accumulator);
        } else {
          TAccumulate accumulator0 = accumulator;
          accumulator = accumulatorAdder.apply(accumulator, o);
          if (accumulator != accumulator0) {
            map.put(key, accumulator);
          }
        }
      }
    }
    return new LookupResultEnumerable<>(map, resultSelector);
  }

  private static <TSource, TKey, TAccumulate, TResult> Enumerable<TResult> groupByMultiple_(
      final Map<TKey, TAccumulate> map, Enumerable<TSource> enumerable,
      List<Function1<TSource, TKey>> keySelectors,
      Function0<TAccumulate> accumulatorInitializer,
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder,
      final Function2<TKey, TAccumulate, TResult> resultSelector) {
    try (Enumerator<TSource> os = enumerable.enumerator()) {
      while (os.moveNext()) {
        for (Function1<TSource, TKey> keySelector : keySelectors) {
          TSource o = os.current();
          TKey key = keySelector.apply(o);
          @SuppressWarnings("argument.type.incompatible")
          TAccumulate accumulator = map.get(key);
          if (accumulator == null) {
            accumulator = accumulatorInitializer.apply();
            accumulator = accumulatorAdder.apply(accumulator, o);
            map.put(key, accumulator);
          } else {
            TAccumulate accumulator0 = accumulator;
            accumulator = accumulatorAdder.apply(accumulator, o);
            if (accumulator != accumulator0) {
              map.put(key, accumulator);
            }
          }
        }
      }
    }
    return new LookupResultEnumerable<>(map, resultSelector);
  }

  @SuppressWarnings("unused")
  private static <TSource, TKey, TResult> Enumerable<TResult> groupBy_(
      final Set<TKey> map, Enumerable<TSource> enumerable,
      Function1<TSource, TKey> keySelector,
      final Function1<TKey, TResult> resultSelector) {
    try (Enumerator<TSource> os = enumerable.enumerator()) {
      while (os.moveNext()) {
        TSource o = os.current();
        TKey key = keySelector.apply(o);
        map.add(key);
      }
    }
    return Linq4j.asEnumerable(map).select(resultSelector);
  }

  /**
   * Correlates the elements of two sequences based on
   * equality of keys and groups the results. The default equality
   * comparer is used to compare keys.
   */
  public static <TSource, TInner, TKey, TResult> Enumerable<TResult> groupJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final Function2<TSource, Enumerable<TInner>, TResult> resultSelector) {
    return new AbstractEnumerable<TResult>() {
      final Map<TKey, TSource> outerMap = outer.toMap(outerKeySelector);
      final Lookup<TKey, TInner> innerLookup = inner.toLookup(innerKeySelector);
      final Enumerator<Map.Entry<TKey, TSource>> entries =
          Linq4j.enumerator(outerMap.entrySet());

      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          @Override public TResult current() {
            final Map.Entry<TKey, TSource> entry = entries.current();
            @SuppressWarnings("argument.type.incompatible")
            final Enumerable<TInner> inners = innerLookup.get(entry.getKey());
            return resultSelector.apply(entry.getValue(),
                inners == null ? Linq4j.emptyEnumerable() : inners);
          }

          @Override public boolean moveNext() {
            return entries.moveNext();
          }

          @Override public void reset() {
            entries.reset();
          }

          @Override public void close() {
          }
        };
      }
    };
  }

  /**
   * Correlates the elements of two sequences based on
   * key equality and groups the results. A specified
   * {@code EqualityComparer<TSource>} is used to compare keys.
   */
  public static <TSource, TInner, TKey, TResult> Enumerable<TResult> groupJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final Function2<TSource, Enumerable<TInner>, TResult> resultSelector,
      final EqualityComparer<TKey> comparer) {
    return new AbstractEnumerable<TResult>() {
      final Map<TKey, TSource> outerMap = outer.toMap(outerKeySelector, comparer);
      final Lookup<TKey, TInner> innerLookup = inner.toLookup(innerKeySelector, comparer);
      final Enumerator<Map.Entry<TKey, TSource>> entries =
          Linq4j.enumerator(outerMap.entrySet());

      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          @Override public TResult current() {
            final Map.Entry<TKey, TSource> entry = entries.current();
            @SuppressWarnings("argument.type.incompatible")
            final Enumerable<TInner> inners = innerLookup.get(entry.getKey());
            return resultSelector.apply(entry.getValue(),
                inners == null ? Linq4j.emptyEnumerable() : inners);
          }

          @Override public boolean moveNext() {
            return entries.moveNext();
          }

          @Override public void reset() {
            entries.reset();
          }

          @Override public void close() {
          }
        };
      }
    };
  }

  /**
   * Produces the set intersection of two sequences by
   * using the default equality comparer to compare values,
   * eliminate duplicates.(Defined by Enumerable.)
   */
  public static <TSource> Enumerable<TSource> intersect(
      Enumerable<TSource> source0, Enumerable<TSource> source1) {
    return intersect(source0, source1, false);
  }

  /**
   * Produces the set intersection of two sequences by
   * using the default equality comparer to compare values,
   * using {@code all} to indicate whether to eliminate duplicates.
   * (Defined by Enumerable.)
   */
  public static <TSource> Enumerable<TSource> intersect(
      Enumerable<TSource> source0, Enumerable<TSource> source1, boolean all) {
    Collection<TSource> set1 = all ? HashMultiset.create() : new HashSet<>();
    source1.into(set1);
    Collection<TSource> resultCollection = all ? HashMultiset.create() : new HashSet<>();
    try (Enumerator<TSource> os = source0.enumerator()) {
      while (os.moveNext()) {
        TSource o = os.current();
        @SuppressWarnings("argument.type.incompatible")
        boolean removed = set1.remove(o);
        if (removed) {
          resultCollection.add(o);
        }
      }
    }
    return Linq4j.asEnumerable(resultCollection);
  }

  /**
   * Produces the set intersection of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, eliminate duplicates.
   */
  public static <TSource> Enumerable<TSource> intersect(
      Enumerable<TSource> source0, Enumerable<TSource> source1,
      EqualityComparer<TSource> comparer) {
    return intersect(source0, source1, comparer, false);
  }

  /**
   * Produces the set intersection of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, using {@code all} to indicate whether to eliminate duplicates.
   */
  public static <TSource> Enumerable<TSource> intersect(
      Enumerable<TSource> source0, Enumerable<TSource> source1,
      EqualityComparer<TSource> comparer, boolean all) {
    if (comparer == Functions.identityComparer()) {
      return intersect(source0, source1, all);
    }
    Collection<Wrapped<TSource>> collection = all ? HashMultiset.create() : new HashSet<>();
    Function1<TSource, Wrapped<TSource>> wrapper = wrapperFor(comparer);
    source1.select(wrapper).into(collection);
    Collection<Wrapped<TSource>> resultCollection = all ? HashMultiset.create() : new HashSet<>();
    try (Enumerator<Wrapped<TSource>> os = source0.select(wrapper).enumerator()) {
      while (os.moveNext()) {
        Wrapped<TSource> o = os.current();
        if (collection.remove(o)) {
          resultCollection.add(o);
        }
      }
    }
    Function1<Wrapped<TSource>, TSource> unwrapper = unwrapper();
    return Linq4j.asEnumerable(resultCollection).select(unwrapper);
  }

  /**
   * Correlates the elements of two sequences based on
   * matching keys. The default equality comparer is used to compare
   * keys.
   */
  public static <TSource, TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final Function2<TSource, TInner, TResult> resultSelector) {
    return hashJoin(
        outer,
        inner,
        outerKeySelector,
        innerKeySelector,
        resultSelector,
        null,
        false,
        false);
  }

  /**
   * Correlates the elements of two sequences based on
   * matching keys. A specified {@code EqualityComparer<TSource>} is used to
   * compare keys.
   */
  public static <TSource, TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      Enumerable<TSource> outer, Enumerable<TInner> inner,
      Function1<TSource, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<TSource, TInner, TResult> resultSelector,
      @Nullable EqualityComparer<TKey> comparer) {
    return hashJoin(
        outer,
        inner,
        outerKeySelector,
        innerKeySelector,
        resultSelector,
        comparer,
        false,
        false);
  }

  /**
   * Correlates the elements of two sequences based on
   * matching keys. A specified {@code EqualityComparer<TSource>} is used to
   * compare keys.
   */
  public static <TSource, TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      Enumerable<TSource> outer, Enumerable<TInner> inner,
      Function1<TSource, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<TSource, TInner, TResult> resultSelector,
      @Nullable EqualityComparer<TKey> comparer, boolean generateNullsOnLeft,
      boolean generateNullsOnRight) {
    return hashEquiJoin_(
        outer,
        inner,
        outerKeySelector,
        innerKeySelector,
        resultSelector,
        comparer,
        generateNullsOnLeft,
        generateNullsOnRight);
  }

  /**
   * Correlates the elements of two sequences based on
   * matching keys. A specified {@code EqualityComparer<TSource>} is used to
   * compare keys.A predicate is used to filter the join result per-row.
   */
  public static <TSource, TInner, TKey, TResult> Enumerable<TResult> hashJoin(
      Enumerable<TSource> outer, Enumerable<TInner> inner,
      Function1<TSource, TKey> outerKeySelector,
      Function1<TInner, TKey> innerKeySelector,
      Function2<TSource, TInner, TResult> resultSelector,
      @Nullable EqualityComparer<TKey> comparer, boolean generateNullsOnLeft,
      boolean generateNullsOnRight,
      @Nullable Predicate2<TSource, TInner> predicate) {
    if (predicate == null) {
      return hashEquiJoin_(
          outer,
          inner,
          outerKeySelector,
          innerKeySelector,
          resultSelector,
          comparer,
          generateNullsOnLeft,
          generateNullsOnRight);
    } else {
      return hashJoinWithPredicate_(
          outer,
          inner,
          outerKeySelector,
          innerKeySelector,
          resultSelector,
          comparer,
          generateNullsOnLeft,
          generateNullsOnRight, predicate);
    }
  }

  /** Implementation of join that builds the right input and probes with the
   * left. */
  private static <TSource, TInner, TKey, TResult> Enumerable<TResult> hashEquiJoin_(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final Function2<TSource, TInner, TResult> resultSelector,
      final @Nullable EqualityComparer<TKey> comparer,
      final boolean generateNullsOnLeft,
      final boolean generateNullsOnRight) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        final Lookup<TKey, TInner> innerLookup =
            comparer == null
                ? inner.toLookup(innerKeySelector)
                : inner.toLookup(innerKeySelector, comparer);

        return new Enumerator<TResult>() {
          Enumerator<TSource> outers = outer.enumerator();
          Enumerator<TInner> inners = Linq4j.emptyEnumerator();
          @Nullable Set<TKey> unmatchedKeys =
              generateNullsOnLeft
                  ? new HashSet<>(innerLookup.keySet())
                  : null;

          @Override public TResult current() {
            return resultSelector.apply(outers.current(), inners.current());
          }

          @Override public boolean moveNext() {
            for (;;) {
              if (inners.moveNext()) {
                return true;
              }
              if (!outers.moveNext()) {
                if (unmatchedKeys != null) {
                  // We've seen everything else. If we are doing a RIGHT or FULL
                  // join (leftNull = true) there are any keys which right but
                  // not the left.
                  List<TInner> list = new ArrayList<>();
                  for (TKey key : unmatchedKeys) {
                    @SuppressWarnings("argument.type.incompatible")
                    Enumerable<TInner> innerValues = requireNonNull(innerLookup.get(key));
                    for (TInner tInner : innerValues) {
                      list.add(tInner);
                    }
                  }
                  inners = Linq4j.enumerator(list);
                  outers.close();
                  outers = Linq4j.singletonNullEnumerator();
                  outers.moveNext();
                  unmatchedKeys = null; // don't do the 'leftovers' again
                  continue;
                }
                return false;
              }
              final TSource outer = outers.current();
              final Enumerable<TInner> innerEnumerable;
              if (outer == null) {
                innerEnumerable = null;
              } else {
                final TKey outerKey = outerKeySelector.apply(outer);
                if (outerKey == null) {
                  innerEnumerable = null;
                } else {
                  if (unmatchedKeys != null) {
                    unmatchedKeys.remove(outerKey);
                  }
                  innerEnumerable = innerLookup.get(outerKey);
                }
              }
              if (innerEnumerable == null
                  || !innerEnumerable.any()) {
                if (generateNullsOnRight) {
                  inners = Linq4j.singletonNullEnumerator();
                } else {
                  inners = Linq4j.emptyEnumerator();
                }
              } else {
                inners = innerEnumerable.enumerator();
              }
            }
          }

          @Override public void reset() {
            outers.reset();
          }

          @Override public void close() {
            outers.close();
          }
        };
      }
    };
  }

  /** Implementation of join that builds the right input and probes with the
   * left. */
  private static <TSource, TInner, TKey, TResult> Enumerable<TResult> hashJoinWithPredicate_(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final Function2<TSource, TInner, TResult> resultSelector,
      final @Nullable EqualityComparer<TKey> comparer,
      final boolean generateNullsOnLeft,
      final boolean generateNullsOnRight, final Predicate2<TSource, TInner> predicate) {

    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        /**
         * the innerToLookUp will refer the inner , if current join
         * is a right join, we should figure out the right list first, if
         * not, then keep the original inner here.
         */
        final Enumerable<TInner> innerToLookUp = generateNullsOnLeft
            ? Linq4j.asEnumerable(inner.toList())
            : inner;

        final Lookup<TKey, TInner> innerLookup =
            comparer == null
                ? innerToLookUp.toLookup(innerKeySelector)
                : innerToLookUp
                    .toLookup(innerKeySelector, comparer);

        return new Enumerator<TResult>() {
          Enumerator<TSource> outers = outer.enumerator();
          Enumerator<TInner> inners = Linq4j.emptyEnumerator();
          @Nullable List<TInner> innersUnmatched =
              generateNullsOnLeft
                  ? new ArrayList<>(innerToLookUp.toList())
                  : null;

          @Override public TResult current() {
            return resultSelector.apply(outers.current(), inners.current());
          }

          @Override public boolean moveNext() {
            for (;;) {
              if (inners.moveNext()) {
                return true;
              }
              if (!outers.moveNext()) {
                if (innersUnmatched != null) {
                  inners = Linq4j.enumerator(innersUnmatched);
                  outers.close();
                  outers = Linq4j.singletonNullEnumerator();
                  outers.moveNext();
                  innersUnmatched = null; // don't do the 'leftovers' again
                  continue;
                }
                return false;
              }
              final TSource outer = outers.current();
              Enumerable<TInner> innerEnumerable;
              if (outer == null) {
                innerEnumerable = null;
              } else {
                final TKey outerKey = outerKeySelector.apply(outer);
                if (outerKey == null) {
                  innerEnumerable = null;
                } else {
                  innerEnumerable = innerLookup.get(outerKey);
                  // apply predicate to filter per-row
                  if (innerEnumerable != null) {
                    final List<TInner> matchedInners = new ArrayList<>();
                    try (Enumerator<TInner> innerEnumerator =
                        innerEnumerable.enumerator()) {
                      while (innerEnumerator.moveNext()) {
                        final TInner inner = innerEnumerator.current();
                        if (predicate.apply(outer, inner)) {
                          matchedInners.add(inner);
                        }
                      }
                    }
                    innerEnumerable = Linq4j.asEnumerable(matchedInners);
                    if (innersUnmatched != null) {
                      innersUnmatched.removeAll(matchedInners);
                    }
                  }
                }
              }
              if (innerEnumerable == null
                  || !innerEnumerable.any()) {
                if (generateNullsOnRight) {
                  inners = Linq4j.singletonNullEnumerator();
                } else {
                  inners = Linq4j.emptyEnumerator();
                }
              } else {
                inners = innerEnumerable.enumerator();
              }
            }
          }

          @Override public void reset() {
            outers.reset();
          }

          @Override public void close() {
            outers.close();
          }
        };
      }
    };
  }

  /**
   * For each row of the {@code outer} enumerable returns the correlated rows
   * from the {@code inner} enumerable.
   */
  public static <TSource, TInner, TResult> Enumerable<TResult> correlateJoin(
      final JoinType joinType, final Enumerable<TSource> outer,
      final Function1<TSource, Enumerable<TInner>> inner,
      final Function2<TSource, ? super @Nullable TInner, TResult> resultSelector) {
    if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
      throw new IllegalArgumentException("JoinType " + joinType + " is not valid for correlation");
    }

    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          private final Enumerator<TSource> outerEnumerator = outer.enumerator();
          private @Nullable Enumerator<TInner> innerEnumerator;
          @Nullable TSource outerValue;
          @Nullable TInner innerValue;
          int state = 0; // 0 -- moving outer, 1 moving inner;

          @Override public TResult current() {
            return resultSelector.apply(castNonNull(outerValue), innerValue);
          }

          @Override public boolean moveNext() {
            while (true) {
              switch (state) {
              case 0:
                // move outer
                if (!outerEnumerator.moveNext()) {
                  return false;
                }
                outerValue = outerEnumerator.current();
                // initial move inner
                Enumerable<TInner> innerEnumerable = inner.apply(outerValue);
                if (innerEnumerable == null) {
                  innerEnumerable = Linq4j.emptyEnumerable();
                }
                if (innerEnumerator != null) {
                  innerEnumerator.close();
                }
                innerEnumerator = innerEnumerable.enumerator();
                if (innerEnumerator.moveNext()) {
                  switch (joinType) {
                  case ANTI:
                    // For anti-join need to try next outer row
                    // Current does not match
                    continue;
                  case SEMI:
                    return true; // current row matches
                  default:
                    break;
                  }
                  // INNER and LEFT just return result
                  innerValue = innerEnumerator.current();
                  state = 1; // iterate over inner results
                  return true;
                }
                // No match detected
                innerValue = null;
                switch (joinType) {
                case LEFT:
                case ANTI:
                  return true;
                default:
                  break;
                }
                // For INNER and LEFT need to find another outer row
                continue;
              case 1:
                // subsequent move inner
                Enumerator<TInner> innerEnumerator = requireNonNull(this.innerEnumerator);
                if (innerEnumerator.moveNext()) {
                  innerValue = innerEnumerator.current();
                  return true;
                }
                state = 0;
                // continue loop, move outer
                break;
              default:
                break;
              }
            }
          }

          @Override public void reset() {
            state = 0;
            outerEnumerator.reset();
            closeInner();
          }

          @Override public void close() {
            outerEnumerator.close();
            closeInner();
            outerValue = null;
          }

          private void closeInner() {
            innerValue = null;
            if (innerEnumerator != null) {
              innerEnumerator.close();
              innerEnumerator = null;
            }
          }
        };
      }
    };
  }

  /**
   * Returns the last element of a sequence. (Defined
   * by Enumerable.)
   * // 返回序列的最后一个元素
   */
  public static <TSource> TSource last(Enumerable<TSource> enumerable) { // 源序列
    final ListEnumerable<TSource> list = enumerable instanceof ListEnumerable // 检查是否为ListEnumerable
        ? ((ListEnumerable<TSource>) enumerable) // 如果是,转换为ListEnumerable
        : null; // 否则为null
    if (list != null) { // 如果是ListEnumerable
      final List<TSource> rawList = list.toList(); // 转换为列表
      final int count = rawList.size(); // 获取列表大小
      if (count > 0) { // 如果列表不为空
        return rawList.get(count - 1); // 返回最后一个元素
      }
    } else { // 如果不是ListEnumerable
      try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
        if (os.moveNext()) { // 尝试移动到第一个元素
          TSource result; // 保存最后一个元素
          do { // 循环遍历所有元素
            result = os.current(); // 更新为当前元素
          } while (os.moveNext()); // 继续移动到下一个元素
          return result; // 返回最后一个元素
        }
      } // 枚举器自动关闭
    }
    throw new NoSuchElementException(); // 序列为空,抛出异常
  }

  /**
   * Fetches blocks of size {@code batchSize} from {@code outer},
   * storing each block into a list ({@code outerValues}).
   * For each block, it uses the {@code inner} function to
   * obtain an enumerable with the correlated rows from the right (inner) input.
   *
   * <p>Each result present in the {@code innerEnumerator} has matched at least one
   * value from the block {@code outerValues}.
   * At this point a mini nested loop is performed between the outer values
   * and inner values using the {@code predicate} to find out the actual matching join results.
   *
   * <p>In order to optimize this mini nested loop, during the first iteration
   * (the first value from {@code outerValues}) we use the {@code innerEnumerator}
   * to compare it to inner rows, and at the same time we fill a list ({@code innerValues})
   * with said {@code innerEnumerator} rows. In the subsequent iterations
   * (2nd, 3rd, etc. value from {@code outerValues}) the list {@code innerValues} is used,
   * since it contains all the {@code innerEnumerator} values,
   * which were stored in the first iteration.
   */
  public static <TSource, TInner, TResult> Enumerable<TResult> correlateBatchJoin(
      final JoinType joinType,
      final Enumerable<TSource> outer,
      final Function1<List<TSource>, Enumerable<TInner>> inner,
      final Function2<TSource, TInner, TResult> resultSelector,
      final Predicate2<TSource, TInner> predicate,
      final int batchSize) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          final Enumerator<TSource> outerEnumerator = outer.enumerator();
          final List<TSource> outerValues = new ArrayList<>(batchSize);
          final List<TInner> innerValues = new ArrayList<>();
          @Nullable TSource outerValue;
          @Nullable TInner innerValue;
          @Nullable Enumerable<TInner> innerEnumerable;
          @Nullable Enumerator<TInner> innerEnumerator;
          boolean innerEnumHasNext = false;
          boolean atLeastOneResult = false;
          int i = -1; // outer position
          int j = -1; // inner position

          @SuppressWarnings("argument.type.incompatible")
          @Override public TResult current() {
            return resultSelector.apply(outerValue, innerValue);
          }

          @Override public boolean moveNext() {
            while (true) {
              // Fetch a new batch
              if (i == outerValues.size() || i == -1) {
                i = 0;
                j = 0;
                outerValues.clear();
                innerValues.clear();
                while (outerValues.size() < batchSize && outerEnumerator.moveNext()) {
                  TSource tSource = outerEnumerator.current();
                  outerValues.add(tSource);
                }
                if (outerValues.isEmpty()) {
                  return false;
                }
                innerEnumerable = inner.apply(new AbstractList<TSource>() {
                  // If the last batch isn't complete fill it with the first value
                  // No harm since it's a disjunction
                  @Override public TSource get(final int index) {
                    return index < outerValues.size() ? outerValues.get(index) : outerValues.get(0);
                  }
                  @Override public int size() {
                    return batchSize;
                  }
                });
                if (innerEnumerable == null) {
                  innerEnumerable = Linq4j.emptyEnumerable();
                }
                closeInner();
                Enumerable<TInner> innerEnumerable = requireNonNull(this.innerEnumerable);
                innerEnumerator = innerEnumerable.enumerator();
                innerEnumHasNext = innerEnumerator.moveNext();

                // If no inner values skip the whole batch
                // in case of SEMI and INNER join
                if (!innerEnumHasNext
                    && (joinType == JoinType.SEMI || joinType == JoinType.INNER)) {
                  i = outerValues.size();
                  continue;
                }
              }
              if (innerHasNext()) {
                outerValue = outerValues.get(i); // get current outer value
                nextInnerValue();
                // Compare current block row to current inner value
                if (predicate.apply(castNonNull(outerValue), castNonNull(innerValue))) {
                  atLeastOneResult = true;
                  // Skip the rest of inner values in case of
                  // ANTI and SEMI when a match is found
                  if (joinType == JoinType.ANTI || joinType == JoinType.SEMI) {
                    // Two ways of skipping inner values,
                    // enumerator way and ArrayList way
                    if (i == 0) {
                      Enumerator<TInner> innerEnumerator = requireNonNull(this.innerEnumerator);
                      while (innerEnumHasNext) {
                        innerValues.add(innerEnumerator.current());
                        innerEnumHasNext = innerEnumerator.moveNext();
                      }
                    } else {
                      j = innerValues.size();
                    }
                    if (joinType == JoinType.ANTI) {
                      continue;
                    }
                  }
                  return true;
                }
              } else { // End of inner
                if (!atLeastOneResult
                    && (joinType == JoinType.LEFT
                    || joinType == JoinType.ANTI)) {
                  outerValue = outerValues.get(i); // get current outer value
                  innerValue = null;
                  nextOuterValue();
                  return true;
                }
                nextOuterValue();
              }
            }
          }

          public void nextOuterValue() {
            i++; // next outerValue
            j = 0; // rewind innerValues
            atLeastOneResult = false;
          }

          private void nextInnerValue() {
            if (i == 0) {
              Enumerator<TInner> innerEnumerator = requireNonNull(this.innerEnumerator);
              innerValue = innerEnumerator.current();
              innerValues.add(innerValue);
              innerEnumHasNext = innerEnumerator.moveNext(); // next enumerator inner value
            } else {
              innerValue = innerValues.get(j++); // next ArrayList inner value
            }
          }

          private boolean innerHasNext() {
            return i == 0 ? innerEnumHasNext : j < innerValues.size();
          }

          @Override public void reset() {
            outerEnumerator.reset();
            innerValue = null;
            outerValue = null;
            outerValues.clear();
            innerValues.clear();
            atLeastOneResult = false;
            i = -1;
          }

          private void closeInner() {
            if (innerEnumerator != null) {
              innerEnumerator.close();
              innerEnumerator = null;
            }
          }

          @Override public void close() {
            outerEnumerator.close();
            closeInner();
            outerValue = null;
            innerValue = null;
          }
        };
      }
    };
  }

  /**
   * Returns elements of {@code outer} for which there is a member of
   * {@code inner} with a matching key.
   */
  public static <TSource, TInner, TKey> Enumerable<TSource> semiJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector) {
    return semiEquiJoin_(outer, inner, outerKeySelector, innerKeySelector, null,
        false);
  }

  public static <TSource, TInner, TKey> Enumerable<TSource> semiJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final EqualityComparer<TKey> comparer) {
    return semiEquiJoin_(outer, inner, outerKeySelector, innerKeySelector, comparer,
        false);
  }

  public static <TSource, TInner, TKey> Enumerable<TSource> semiJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final EqualityComparer<TKey> comparer,
      final Predicate2<TSource, TInner> nonEquiPredicate) {
    return semiJoin(outer, inner, outerKeySelector,
        innerKeySelector, comparer,
        false, nonEquiPredicate);
  }

  /**
   * Returns elements of {@code outer} for which there is NOT a member of
   * {@code inner} with a matching key.
   */
  public static <TSource, TInner, TKey> Enumerable<TSource> antiJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector) {
    return semiEquiJoin_(outer, inner, outerKeySelector, innerKeySelector, null,
        true);
  }

  public static <TSource, TInner, TKey> Enumerable<TSource> antiJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final EqualityComparer<TKey> comparer) {
    return semiEquiJoin_(outer, inner, outerKeySelector, innerKeySelector, comparer,
        true);
  }

  public static <TSource, TInner, TKey> Enumerable<TSource> antiJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final EqualityComparer<TKey> comparer,
      final Predicate2<TSource, TInner> nonEquiPredicate) {
    return semiJoin(outer, inner, outerKeySelector,
        innerKeySelector, comparer,
        true, nonEquiPredicate);
  }

  /**
   * Returns elements of {@code outer} for which there is (semi-join) / is not (anti-semi-join)
   * a member of {@code inner} with a matching key. A specified
   * {@code EqualityComparer<TSource>} is used to compare keys.
   * A predicate is used to filter the join result per-row.
   */
  public static <TSource, TInner, TKey> Enumerable<TSource> semiJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final EqualityComparer<TKey> comparer,
      final boolean anti,
      final Predicate2<TSource, TInner> nonEquiPredicate) {
    if (nonEquiPredicate == null) {
      return semiEquiJoin_(outer, inner, outerKeySelector, innerKeySelector,
          comparer,
          anti);
    } else {
      return semiJoinWithPredicate_(outer, inner, outerKeySelector,
          innerKeySelector,
          comparer,
          anti, nonEquiPredicate);
    }
  }

  private static <TSource, TInner, TKey> Enumerable<TSource> semiJoinWithPredicate_(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final EqualityComparer<TKey> comparer,
      final boolean anti,
      final Predicate2<TSource, TInner> nonEquiPredicate) {

    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        // CALCITE-2909 Delay the computation of the innerLookup until the
        // moment when we are sure
        // that it will be really needed, i.e. when the first outer
        // enumerator item is processed
        final Supplier<Lookup<TKey, TInner>> innerLookup =
            Suppliers.memoize(() ->
                comparer == null
                    ? inner.toLookup(innerKeySelector)
                    : inner.toLookup(innerKeySelector, comparer));

        final Predicate1<TSource> predicate = v0 -> {
          TKey key = outerKeySelector.apply(v0);
          @SuppressWarnings("argument.type.incompatible")
          Enumerable<TInner> innersOfKey = key == null ? null : innerLookup.get().get(key);
          if (innersOfKey == null) {
            return anti;
          }
          try (Enumerator<TInner> os = innersOfKey.enumerator()) {
            while (os.moveNext()) {
              TInner v1 = os.current();
              if (nonEquiPredicate.apply(v0, v1)) {
                return !anti;
              }
            }
            return anti;
          }
        };
        return EnumerableDefaults.where(outer.enumerator(), predicate);
      }
    };
  }

  /**
   * Returns elements of {@code outer} for which there is (semi-join) / is not (anti-semi-join)
   * a member of {@code inner} with a matching key. A specified
   * {@code EqualityComparer<TSource>} is used to compare keys.
   */
  private static <TSource, TInner, TKey> Enumerable<TSource> semiEquiJoin_(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final @Nullable EqualityComparer<TKey> comparer,
      final boolean anti) {
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        // CALCITE-2909 Delay the computation of the innerLookup until the moment when we are sure
        // that it will be really needed, i.e. when the first outer enumerator item is processed
        final Supplier<Enumerable<TKey>> innerLookup = Suppliers.memoize(() ->
            comparer == null
                ? inner.select(innerKeySelector).distinct()
                : inner.select(innerKeySelector).distinct(comparer));

        final Predicate1<TSource> predicate = v0 -> {
          TKey key = outerKeySelector.apply(v0);
          boolean found = key != null && innerLookup.get().contains(key);
          return anti ? !found : found;
        };

        return EnumerableDefaults.where(outer.enumerator(), predicate);
      }
    };
  }

  /**
   * Correlates the elements of two sequences based on a predicate.
   */
  public static <TSource, TInner, TResult> Enumerable<TResult> nestedLoopJoin(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Predicate2<TSource, TInner> predicate,
      Function2<? super @Nullable TSource, ? super @Nullable TInner, TResult> resultSelector,
      final JoinType joinType) {
    if (!joinType.generatesNullsOnLeft()) {
      return nestedLoopJoinOptimized(outer, inner, predicate, resultSelector, joinType);
    }
    return nestedLoopJoinAsList(outer, inner, predicate, resultSelector, joinType);
  }

  /**
   * Implementation of nested loop join that builds the complete result as a list
   * and then returns it. This is an easy-to-implement solution, but hogs memory.
   */
  private static <TSource, TInner, TResult> Enumerable<TResult> nestedLoopJoinAsList(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Predicate2<TSource, TInner> predicate,
      Function2<? super @Nullable TSource, ? super @Nullable TInner, TResult> resultSelector,
      final JoinType joinType) {
    final boolean generateNullsOnLeft = joinType.generatesNullsOnLeft();
    final boolean generateNullsOnRight = joinType.generatesNullsOnRight();
    final List<TResult> result = new ArrayList<>();
    final List<TInner> rightList = inner.toList();
    final Set<TInner> rightUnmatched;
    if (generateNullsOnLeft) {
      rightUnmatched = Sets.newIdentityHashSet();
      rightUnmatched.addAll(rightList);
    } else {
      rightUnmatched = null;
    }
    try (Enumerator<TSource> lefts = outer.enumerator()) {
      while (lefts.moveNext()) {
        int leftMatchCount = 0;
        final TSource left = lefts.current();
        for (TInner right : rightList) {
          if (predicate.apply(left, right)) {
            ++leftMatchCount;
            if (joinType == JoinType.ANTI) {
              break;
            } else {
              if (rightUnmatched != null) {
                rightUnmatched.remove(right);
              }
              result.add(resultSelector.apply(left, right));
              if (joinType == JoinType.SEMI) {
                break;
              }
            }
          }
        }
        if (leftMatchCount == 0 && (generateNullsOnRight || joinType == JoinType.ANTI)) {
          result.add(resultSelector.apply(left, null));
        }
      }
      if (rightUnmatched != null) {
        for (TInner right : rightUnmatched) {
          result.add(resultSelector.apply(null, right));
        }
      }
      return Linq4j.asEnumerable(result);
    }
  }

  /**
   * Implementation of nested loop join that, unlike {@link #nestedLoopJoinAsList}, does not
   * require to build the complete result as a list before returning it. Instead, it iterates
   * through the outer enumerable and inner enumerable and returns the results step by step.
   * It does not support RIGHT / FULL join.
   */
  private static <TSource, TInner, TResult> Enumerable<TResult> nestedLoopJoinOptimized(
      final Enumerable<TSource> outer, final Enumerable<TInner> inner,
      final Predicate2<TSource, TInner> predicate,
      Function2<? super TSource, ? super @Nullable TInner, TResult> resultSelector,
      final JoinType joinType) {
    if (joinType == JoinType.RIGHT || joinType == JoinType.FULL) {
      throw new IllegalArgumentException("JoinType " + joinType + " is unsupported");
    }

    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          private final Enumerator<TSource> outerEnumerator =
              outer.enumerator();
          private @Nullable Enumerator<TInner> innerEnumerator = null;
          private boolean outerMatch = false; // whether the outerValue has matched an innerValue
          private @Nullable TSource outerValue;
          private @Nullable TInner innerValue;
          private int state = 0; // 0 moving outer, 1 moving inner

          @Override public TResult current() {
            return resultSelector.apply(castNonNull(outerValue), innerValue);
          }

          @Override public boolean moveNext() {
            while (true) {
              switch (state) {
              case 0:
                // move outer
                if (!outerEnumerator.moveNext()) {
                  return false;
                }
                outerValue = outerEnumerator.current();
                closeInner();
                innerEnumerator = inner.enumerator();
                outerMatch = false;
                state = 1;
                continue;
              case 1:
                // move inner
                Enumerator<TInner> innerEnumerator = requireNonNull(this.innerEnumerator);
                if (innerEnumerator.moveNext()) {
                  TInner innerValue = innerEnumerator.current();
                  this.innerValue = innerValue;
                  if (predicate.apply(castNonNull(outerValue), innerValue)) {
                    outerMatch = true;
                    switch (joinType) {
                    case ANTI: // try next outer row
                      state = 0;
                      continue;
                    case SEMI: // return result, and try next outer row
                      state = 0;
                      return true;
                    case INNER:
                    case LEFT: // INNER and LEFT just return result
                      return true;
                    default:
                      break;
                    }
                  } // else (predicate returned false) continue: move inner
                } else { // innerEnumerator is over
                  state = 0;
                  innerValue = null;
                  if (!outerMatch
                      && (joinType == JoinType.LEFT || joinType == JoinType.ANTI)) {
                    // No match detected: outerValue is a result for LEFT / ANTI join
                    return true;
                  }
                }
                break;
              default:
                break;
              }
            }
          }

          @Override public void reset() {
            state = 0;
            outerMatch = false;
            outerEnumerator.reset();
            closeInner();
          }

          @Override public void close() {
            outerEnumerator.close();
            closeInner();
          }

          private void closeInner() {
            if (innerEnumerator != null) {
              innerEnumerator.close();
              innerEnumerator = null;
            }
          }
        };
      }
    };
  }

  /**
   * Joins two inputs that are sorted on the key.
   * Inputs must be sorted in ascending order, nulls last.
   *
   * @deprecated Use {@link #mergeJoin(Enumerable, Enumerable, Function1, Function1, Predicate2, Function2, JoinType, Comparator, EqualityComparer)}
   */
  @Deprecated // to be removed before 2.0
  public static <TSource, TInner, TKey extends Comparable<TKey>, TResult> Enumerable<TResult>
      mergeJoin(final Enumerable<TSource> outer,
      final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final Function2<TSource, @Nullable TInner, TResult> resultSelector,
      boolean generateNullsOnLeft,
      boolean generateNullsOnRight) {
    if (generateNullsOnLeft) {
      throw new UnsupportedOperationException(
          "not implemented, mergeJoin with generateNullsOnLeft");
    }
    if (generateNullsOnRight) {
      throw new UnsupportedOperationException(
          "not implemented, mergeJoin with generateNullsOnRight");
    }
    return mergeJoin(outer, inner, outerKeySelector, innerKeySelector, null, resultSelector,
        JoinType.INNER, null, null);
  }

  /**
   * Returns if certain join type is supported by Enumerable Merge
   * Join implementation.
   *
   * <p>NOTE: This method is subject to change or be removed without notice.
   */
  public static boolean isMergeJoinSupported(JoinType joinType) {
    switch (joinType) {
    case INNER:
    case SEMI:
    case ANTI:
    case LEFT:
      return true;
    default:
      return false;
    }
  }

  /**
   * Joins two inputs that are sorted on the key.
   * Inputs must be sorted in ascending order, nulls last.
   */
  public static <TSource, TInner, TKey extends Comparable<TKey>, TResult> Enumerable<TResult>
      mergeJoin(final Enumerable<TSource> outer,
      final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final Function2<TSource, @Nullable TInner, TResult> resultSelector,
      final JoinType joinType,
      final @Nullable Comparator<TKey> comparator) {
    return mergeJoin(outer, inner, outerKeySelector, innerKeySelector, null, resultSelector,
        joinType, comparator, null);
  }

  /**
   * Joins two inputs that are sorted on the key, with an extra predicate for
   * non equi-join conditions.
   *
   * <p>Inputs be must sorted in ascending order, nulls last.
   *
   * @param extraPredicate predicate for non equi-join conditions. In case of equi-join,
   *                       it will be null. In case of non-equi join, the non-equi conditions
   *                       will be evaluated using this extra predicate within the join process,
   *                       and not applying a filter on top of the join results, because the latter
   *                       strategy can only work on inner joins, and we aim to support other join
   *                       types in the future (e.g. semi or anti joins).
   * @param comparator key comparator, possibly null (in which case {@link Comparable#compareTo}
   *                   will be used).
   * @param equalityComparer key equality comparer, possibly null (in which case equals
   *                         will be used), required to compare keys from the same input.
   *
   * <p>NOTE: The current API is experimental and subject to change without
   * notice.
   */
  @API(since = "1.23", status = API.Status.EXPERIMENTAL)
  public static <TSource, TInner, TKey extends Comparable<TKey>, TResult> Enumerable<TResult>
      mergeJoin(final Enumerable<TSource> outer,
      final Enumerable<TInner> inner,
      final Function1<TSource, TKey> outerKeySelector,
      final Function1<TInner, TKey> innerKeySelector,
      final @Nullable Predicate2<TSource, TInner> extraPredicate,
      final Function2<TSource, @Nullable TInner, TResult> resultSelector,
      final JoinType joinType,
      final @Nullable Comparator<TKey> comparator,
      final @Nullable EqualityComparer<TKey> equalityComparer) {
    if (!isMergeJoinSupported(joinType)) {
      throw new UnsupportedOperationException("MergeJoin unsupported for join type " + joinType);
    }
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new MergeJoinEnumerator<>(outer, inner, outerKeySelector, innerKeySelector,
            extraPredicate, resultSelector, joinType, comparator, equalityComparer);
      }
    };
  }

  /**
   * Returns the last element of a sequence that
   * satisfies a specified condition.
   */
  public static <TSource> TSource last(Enumerable<TSource> enumerable,
      Predicate1<TSource> predicate) {
    final ListEnumerable<TSource> list = enumerable instanceof ListEnumerable
        ? ((ListEnumerable<TSource>) enumerable)
        : null;
    if (list != null) {
      final List<TSource> rawList = list.toList();
      final int count = rawList.size();
      for (int i = count - 1; i >= 0; --i) {
        TSource result = rawList.get(i);
        if (predicate.apply(result)) {
          return result;
        }
      }
    } else {
      try (Enumerator<TSource> os = enumerable.enumerator()) {
        while (os.moveNext()) {
          TSource result = os.current();
          if (predicate.apply(result)) {
            while (os.moveNext()) {
              TSource element = os.current();
              if (predicate.apply(element)) {
                result = element;
              }
            }
            return result;
          }
        }
      }
    }
    throw new NoSuchElementException();
  }

  /**
   * Returns the last element of a sequence, or a
   * default value if the sequence contains no elements.
   */
  public static <TSource> @Nullable TSource lastOrDefault(
      Enumerable<TSource> enumerable) {
    final ListEnumerable<TSource> list = enumerable instanceof ListEnumerable
        ? ((ListEnumerable<TSource>) enumerable)
        : null;
    if (list != null) {
      final List<TSource> rawList = list.toList();
      final int count = rawList.size();
      if (count > 0) {
        return rawList.get(count - 1);
      }
    } else {
      try (Enumerator<TSource> os = enumerable.enumerator()) {
        if (os.moveNext()) {
          TSource result;
          do {
            result = os.current();
          } while (os.moveNext());
          return result;
        }
      }
    }
    return null;
  }

  /**
   * Returns the last element of a sequence that
   * satisfies a condition or a default value if no such element is
   * found.
   */
  public static <TSource> @Nullable TSource lastOrDefault(Enumerable<TSource> enumerable,
      Predicate1<TSource> predicate) {
    final ListEnumerable<TSource> list = enumerable instanceof ListEnumerable
        ? ((ListEnumerable<TSource>) enumerable)
        : null;
    if (list != null) {
      final List<TSource> rawList = list.toList();
      final int count = rawList.size();
      for (int i = count - 1; i >= 0; --i) {
        TSource result = rawList.get(i);
        if (predicate.apply(result)) {
          return result;
        }
      }
    } else {
      try (Enumerator<TSource> os = enumerable.enumerator()) {
        while (os.moveNext()) {
          TSource result = os.current();
          if (predicate.apply(result)) {
            while (os.moveNext()) {
              TSource element = os.current();
              if (predicate.apply(element)) {
                result = element;
              }
            }
            return result;
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns an long that represents the total number
   * of elements in a sequence.
   * // 返回一个long值,表示序列中的元素总数
   */
  public static <TSource> long longCount(Enumerable<TSource> source) { // 源序列
    return longCount(source, Functions.truePredicate1()); // 调用重载方法,使用总是返回true的谓词
  }

  /**
   * Returns an long that represents how many elements
   * in a sequence satisfy a condition.
   * // 返回一个long值,表示序列中满足条件的元素数量
   */
  public static <TSource> long longCount(Enumerable<TSource> enumerable, // 源序列
      Predicate1<TSource> predicate) { // 谓词函数,用于测试元素是否满足条件
    // Shortcut if this is a collection and the predicate is always true.
    // 如果是集合且谓词总是为true,使用快捷方式直接返回集合大小
    if (predicate == Predicate1.TRUE && enumerable instanceof Collection) { // 检查是否为集合且谓词总是为true
      return ((Collection) enumerable).size(); // 直接返回集合大小
    }
    int n = 0; // 计数器
    try (Enumerator<TSource> os = enumerable.enumerator()) { // 获取序列的枚举器
      while (os.moveNext()) { // 遍历所有元素
        TSource o = os.current(); // 获取当前元素
        if (predicate.apply(o)) { // 测试元素是否满足条件
          ++n; // 计数器加1
        }
      }
    } // 枚举器自动关闭
    return n; // 返回计数结果
  }

  /**
   * Returns the maximum value in a generic
   * sequence.
   * // 返回泛型序列中的最大值
   */
  public static <TSource extends Comparable<TSource>> TSource max( // 返回最大值
      Enumerable<TSource> source) { // 源序列
    return aggregate(source, maxFunction()); // 使用聚合函数和max函数计算最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum Decimal value.
   * // 对序列的每个元素调用转换函数,返回最大的BigDecimal值
   */
  public static <TSource> BigDecimal max(Enumerable<TSource> source, // 源序列
      BigDecimalFunction1<TSource> selector) { // 转换函数,将每个元素转换为BigDecimal
    return aggregate(source.select(selector), maxFunction()); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable Decimal
   * value.
   * // 对序列的每个元素调用转换函数,返回最大的可空BigDecimal值
   */
  public static <TSource> BigDecimal max(Enumerable<TSource> source, // 源序列
      NullableBigDecimalFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空BigDecimal
    return aggregate(source.select(selector), maxFunction()); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum Double value.
   * // 对序列的每个元素调用转换函数,返回最大的double值
   */
  public static <TSource> double max(Enumerable<TSource> source, // 源序列
      DoubleFunction1<TSource> selector) { // 转换函数,将每个元素转换为double
    return requireNonNull(aggregate(source.select(adapt(selector)), Extensions.DOUBLE_MAX)); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable Double
   * value.
   * // 对序列的每个元素调用转换函数,返回最大的可空Double值
   */
  public static <TSource> Double max(Enumerable<TSource> source, // 源序列
      NullableDoubleFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Double
    return aggregate(source.select(selector), Extensions.DOUBLE_MAX); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum int value.
   * // 对序列的每个元素调用转换函数,返回最大的int值
   */
  public static <TSource> int max(Enumerable<TSource> source, // 源序列
      IntegerFunction1<TSource> selector) { // 转换函数,将每个元素转换为int
    return requireNonNull(aggregate(source.select(adapt(selector)), Extensions.INTEGER_MAX)); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable int value. (Defined
   * by Enumerable.)
   * // 对序列的每个元素调用转换函数,返回最大的可空Integer值
   */
  public static <TSource> Integer max(Enumerable<TSource> source, // 源序列
      NullableIntegerFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Integer
    return aggregate(source.select(selector), Extensions.INTEGER_MAX); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum long value.
   * // 对序列的每个元素调用转换函数,返回最大的long值
   */
  public static <TSource> long max(Enumerable<TSource> source, // 源序列
      LongFunction1<TSource> selector) { // 转换函数,将每个元素转换为long
    return requireNonNull(aggregate(source.select(adapt(selector)), Extensions.LONG_MAX)); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable long value. (Defined
   * by Enumerable.)
   // 对序列的每个元素调用转换函数,返回最大的可空Long值
   */
  public static <TSource> @Nullable Long max(Enumerable<TSource> source, // 源序列
      NullableLongFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Long
    return aggregate(source.select(selector), Extensions.LONG_MAX); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum Float value.
   * // 对序列的每个元素调用转换函数,返回最大的float值
   */
  public static <TSource> float max(Enumerable<TSource> source, // 源序列
      FloatFunction1<TSource> selector) { // 转换函数,将每个元素转换为float
    return requireNonNull(aggregate(source.select(adapt(selector)), Extensions.FLOAT_MAX)); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable Float
   * value.
   * // 对序列的每个元素调用转换函数,返回最大的可空Float值
   */
  public static <TSource> @Nullable Float max(Enumerable<TSource> source, // 源序列
      NullableFloatFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Float
    return aggregate(source.select(selector), Extensions.FLOAT_MAX); // 先转换再聚合求最大值
  }

  /**
   * Invokes a transform function on each element of a
   * generic sequence and returns the maximum resulting
   * value.
   * // 对泛型序列的每个元素调用转换函数,返回最大的结果值
   */
  public static <TSource, TResult extends Comparable<TResult>> @Nullable TResult max( // 返回最大值
      Enumerable<TSource> source, Function1<TSource, TResult> selector) { // 源序列和转换函数
    return aggregate(source.select(selector), maxFunction()); // 先转换再聚合求最大值
  }

  /**
   * Returns the minimum value in a generic
   * sequence.
   * // 返回泛型序列中的最小值
   */
  public static <TSource extends Comparable<TSource>> @Nullable TSource min( // 返回最小值
      Enumerable<TSource> source) { // 源序列
    return aggregate(source, minFunction()); // 使用聚合函数和min函数计算最小值
  }

  @SuppressWarnings("unchecked")
  private static <TSource extends Comparable<TSource>> Function2<TSource, TSource, TSource>
      minFunction() { // 返回最小值函数
    return (Function2<TSource, TSource, TSource>) (Function2) Extensions.COMPARABLE_MIN; // 使用Extensions中的最小值函数
  }

  @SuppressWarnings("unchecked")
  private static <TSource extends Comparable<TSource>> Function2<TSource, TSource, TSource>
      maxFunction() { // 返回最大值函数
    return (Function2<TSource, TSource, TSource>) (Function2) Extensions.COMPARABLE_MAX; // 使用Extensions中的最大值函数
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum Decimal value.
   */
  public static <TSource> BigDecimal min(Enumerable<TSource> source,
      BigDecimalFunction1<TSource> selector) {
    Function2<BigDecimal, BigDecimal, BigDecimal> min = minFunction();
    return aggregate(source.select(selector), null, min);
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable Decimal
   * value.
   */
  public static <TSource> BigDecimal min(Enumerable<TSource> source,
      NullableBigDecimalFunction1<TSource> selector) {
    return aggregate(source.select(selector), minFunction());
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum Double value.
   */
  public static <TSource> double min(Enumerable<TSource> source,
      DoubleFunction1<TSource> selector) {
    return requireNonNull(aggregate(source.select(adapt(selector)), Extensions.DOUBLE_MIN));
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable Double
   * value.
   */
  public static <TSource> Double min(Enumerable<TSource> source,
      NullableDoubleFunction1<TSource> selector) {
    return aggregate(source.select(selector), Extensions.DOUBLE_MIN);
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum int value.
   */
  public static <TSource> int min(Enumerable<TSource> source,
      IntegerFunction1<TSource> selector) {
    return requireNonNull(aggregate(source.select(adapt(selector)), Extensions.INTEGER_MIN));
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable int value. (Defined
   * by Enumerable.)
   */
  public static <TSource> Integer min(Enumerable<TSource> source,
      NullableIntegerFunction1<TSource> selector) {
    return aggregate(source.select(selector), null, Extensions.INTEGER_MIN);
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum long value.
   */
  public static <TSource> long min(Enumerable<TSource> source,
      LongFunction1<TSource> selector) {
    return aggregate(source.select(adapt(selector)), null, Extensions.LONG_MIN);
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable long value. (Defined
   * by Enumerable.)
   */
  public static <TSource> Long min(Enumerable<TSource> source,
      NullableLongFunction1<TSource> selector) {
    return aggregate(source.select(selector), null, Extensions.LONG_MIN);
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum Float value.
   */
  public static <TSource> float min(Enumerable<TSource> source,
      FloatFunction1<TSource> selector) {
    return aggregate(source.select(adapt(selector)), null,
        Extensions.FLOAT_MIN);
  }

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable Float
   * value.
   */
  public static <TSource> Float min(Enumerable<TSource> source,
      NullableFloatFunction1<TSource> selector) {
    return aggregate(source.select(selector), null, Extensions.FLOAT_MIN);
  }

  /**
   * Invokes a transform function on each element of a
   * generic sequence and returns the minimum resulting
   * value.
   */
  public static <TSource, TResult extends Comparable<TResult>> @Nullable TResult min(
      Enumerable<TSource> source, Function1<TSource, TResult> selector) {
    Function2<TResult, TResult, TResult> min = minFunction();
    return aggregate(source.select(selector), null, min);
  }

  /**
   * Filters the elements of an Enumerable based on a
   * specified type.
   *
   * <p>Analogous to LINQ's Enumerable.OfType extension method.
   *
   * @param clazz Target type
   * @param <TResult> Target type
   *
   * @return Collection of T2
   */
  public static <TSource, TResult> Enumerable<TResult> ofType(
      Enumerable<TSource> enumerable, Class<TResult> clazz) {
    //noinspection unchecked
    return (Enumerable) where(enumerable,
        Functions.ofTypePredicate(clazz));
  }

  /**
   * Sorts the elements of a sequence in ascending
   * order according to a key.
   */
  public static <TSource, TKey extends Comparable> Enumerable<TSource> orderBy(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector) {
    return orderBy(source, keySelector, null);
  }

  /**
   * Sorts the elements of a sequence in ascending
   * order by using a specified comparer.
   */
  public static <TSource, TKey> Enumerable<TSource> orderBy(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      @Nullable Comparator<TKey> comparator) {
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        // NOTE: TreeMap allows null comparator. But the caller of this method
        // must supply a comparator if the key does not extend Comparable.
        // Otherwise there will be a ClassCastException while retrieving.
        final Map<TKey, List<TSource>> map = new TreeMap<>(comparator);
        final LookupImpl<TKey, TSource> lookup =
            toLookup_(map, source, keySelector, Functions.identitySelector());
        return lookup.valuesEnumerable().enumerator();
      }
    };
  }


  /**
   * A sort implementation optimized for a sort with a fetch size (LIMIT).
   *
   * @param offset how many rows are skipped from the sorted output.
   *               Must be greater than or equal to 0.
   * @param fetch how many rows are retrieved. Must be greater than or equal to 0.
   */
  public static <TSource, TKey> Enumerable<TSource> orderBy(
      Enumerable<TSource> source,
      Function1<TSource, TKey> keySelector,
      Comparator<TKey> comparator,
      int offset, int fetch) {
    // As discussed in CALCITE-3920 and CALCITE-4157, this method avoids to sort the complete input,
    // if only the first N rows are actually needed. A TreeMap implementation has been chosen,
    // so that it behaves similar to the orderBy method without fetch/offset.
    // The TreeMap has a better performance if there are few distinct sort keys.
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        if (fetch == 0) {
          return Linq4j.emptyEnumerator();
        }

        TreeMap<TKey, List<TSource>> map = new TreeMap<>(comparator);
        long size = 0;
        long needed = fetch + (long) offset;

        // read the input into a tree map
        try (Enumerator<TSource> os = source.enumerator()) {
          while (os.moveNext()) {
            TSource o = os.current();
            TKey key = keySelector.apply(o);
            if (needed >= 0 && size >= needed) {
              // the current row will never appear in the output, so just skip it
              @KeyFor("map") TKey lastKey = map.lastKey();
              if (comparator.compare(key, lastKey) >= 0) {
                continue;
              }
              // remove last entry from tree map, so that we keep at most 'needed' rows
              @SuppressWarnings("argument.type.incompatible")
              List<TSource> l = map.get(lastKey);
              if (l.size() == 1) {
                map.remove(lastKey);
              } else {
                l.remove(l.size() - 1);
              }
              size--;
            }
            // add the current element to the map
            map.compute(key, (k, l) -> {
              // for first entry, use a singleton list to save space
              // when we go from 1 to 2 elements, switch to array list
              if (l == null) {
                return Collections.singletonList(o);
              }
              if (l.size() == 1) {
                l = new ArrayList<>(l);
              }
              l.add(o);
              return l;
            });
            size++;
          }
        }

        // skip the first 'offset' rows by deleting them from the map
        if (offset > 0) {
          // search the key up to (but excluding) which we have to remove entries from the map
          int skipped = 0;
          TKey until = (TKey) DUMMY;
          for (Map.Entry<TKey, List<TSource>> e : map.entrySet()) {
            skipped += e.getValue().size();

            if (skipped > offset) {
              // we might need to remove entries from the list
              List<TSource> l = e.getValue();
              int toKeep = skipped - offset;
              if (toKeep < l.size()) {
                l.subList(0, l.size() - toKeep).clear();
              }

              until = e.getKey();
              break;
            }
          }
          if (until == DUMMY) {
            // the offset is bigger than the number of rows in the map
            return Linq4j.emptyEnumerator();
          }
          map.headMap(until, false).clear();
        }

        return new LookupImpl<>(map).valuesEnumerable().enumerator();
      }
    };
  }

  /**
   * Sorts the elements of a sequence in descending
   * order according to a key.
   */
  public static <TSource, TKey extends Comparable> Enumerable<TSource> orderByDescending(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector) {
    return orderBy(source, keySelector, Collections.reverseOrder());
  }

  /**
   * Sorts the elements of a sequence in descending
   * order by using a specified comparer.
   */
  public static <TSource, TKey> Enumerable<TSource> orderByDescending(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Comparator<TKey> comparator) {
    return orderBy(source, keySelector, Collections.reverseOrder(comparator));
  }

  /**
   * Inverts the order of the elements in a
   * sequence.
   */
  public static <TSource> Enumerable<TSource> reverse(
      Enumerable<TSource> source) {
    final List<TSource> list = toList(source);
    final int n = list.size();
    return Linq4j.asEnumerable(
        new AbstractList<TSource>() {
          @Override public TSource get(int index) {
            return list.get(n - 1 - index);
          }

          @Override public int size() {
            return n;
          }
        });
  }

  /**
   * Projects each element of a sequence into a new form.
   * // 将序列中的每个元素投影为新形式
   // 这是LINQ中最常用的操作之一,用于转换数据
   */
  public static <TSource, TResult> Enumerable<TResult> select( // 返回投影后的序列
      final Enumerable<TSource> source, // 源序列
      final Function1<TSource, TResult> selector) { // 选择器函数,将每个元素转换为新形式
    if (selector == Functions.identitySelector()) { // 如果选择器是恒等函数(不改变元素)
      //noinspection unchecked
      return (Enumerable<TResult>) source; // 直接返回源序列,避免不必要的转换
    }
    return new AbstractEnumerable<TResult>() { // 创建抽象的可枚举对象
      @Override public Enumerator<TResult> enumerator() { // 创建枚举器
        return new Enumerator<TResult>() { // 创建新的枚举器
          final Enumerator<TSource> enumerator = source.enumerator(); // 获取源序列的枚举器

          @Override public TResult current() { // 获取当前元素
            return selector.apply(enumerator.current()); // 应用选择器函数转换当前元素
          }

          @Override public boolean moveNext() { // 移动到下一个元素
            return enumerator.moveNext(); // 委托给源枚举器
          }

          @Override public void reset() { // 重置枚举器
            enumerator.reset(); // 委托给源枚举器
          }

          @Override public void close() { // 关闭枚举器
            enumerator.close(); // 委托给源枚举器
          }
        };
      }
    };
  }

  /**
   * Projects each element of a sequence into a new
   * form by incorporating the element's index.
   * // 将序列中的每个元素投影为新形式,并包含元素的索引
   // 索引从0开始
   */
  public static <TSource, TResult> Enumerable<TResult> select( // 返回投影后的序列
      final Enumerable<TSource> source, // 源序列
      final Function2<TSource, Integer, TResult> selector) { // 选择器函数,接收元素和索引
    return new AbstractEnumerable<TResult>() { // 创建抽象的可枚举对象
      @Override public Enumerator<TResult> enumerator() { // 创建枚举器
        return new Enumerator<TResult>() { // 创建新的枚举器
          final Enumerator<TSource> enumerator = source.enumerator(); // 获取源序列的枚举器
          int n = -1; // 索引计数器,初始值为-1

          @Override public TResult current() { // 获取当前元素
            return selector.apply(enumerator.current(), n); // 应用选择器函数,传入元素和索引
          }

          @Override public boolean moveNext() { // 移动到下一个元素
            if (enumerator.moveNext()) { // 如果源枚举器能移动到下一个元素
              ++n; // 索引加1
              return true; // 返回true表示成功移动
            } else { // 如果源枚举器没有下一个元素
              return false; // 返回false表示已到达末尾
            }
          }

          @Override public void reset() { // 重置枚举器
            enumerator.reset(); // 委托给源枚举器
          }

          @Override public void close() { // 关闭枚举器
            enumerator.close(); // 委托给源枚举器
          }
        };
      }
    };
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>} and flattens the resulting sequences into one
   * sequence.
   */
  public static <TSource, TResult> Enumerable<TResult> selectMany(
      final Enumerable<TSource> source,
      final Function1<TSource, Enumerable<TResult>> selector) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          final Enumerator<TSource> sourceEnumerator = source.enumerator();
          Enumerator<TResult> resultEnumerator = Linq4j.emptyEnumerator();

          @Override public TResult current() {
            return resultEnumerator.current();
          }

          @Override public boolean moveNext() {
            for (;;) {
              if (resultEnumerator.moveNext()) {
                return true;
              }
              if (!sourceEnumerator.moveNext()) {
                return false;
              }
              resultEnumerator = selector.apply(sourceEnumerator.current())
                  .enumerator();
            }
          }

          @Override public void reset() {
            sourceEnumerator.reset();
            resultEnumerator = Linq4j.emptyEnumerator();
          }

          @Override public void close() {
            sourceEnumerator.close();
            resultEnumerator.close();
          }
        };
      }
    };
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>}, and flattens the resulting sequences into one
   * sequence. The index of each source element is used in the
   * projected form of that element.
   */
  public static <TSource, TResult> Enumerable<TResult> selectMany(
      final Enumerable<TSource> source,
      final Function2<TSource, Integer, Enumerable<TResult>> selector) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          int index = -1;
          final Enumerator<TSource> sourceEnumerator = source.enumerator();
          Enumerator<TResult> resultEnumerator = Linq4j.emptyEnumerator();

          @Override public TResult current() {
            return resultEnumerator.current();
          }

          @Override public boolean moveNext() {
            for (;;) {
              if (resultEnumerator.moveNext()) {
                return true;
              }
              if (!sourceEnumerator.moveNext()) {
                return false;
              }
              index += 1;
              resultEnumerator = selector.apply(sourceEnumerator.current(), index)
                  .enumerator();
            }
          }

          @Override public void reset() {
            sourceEnumerator.reset();
            resultEnumerator = Linq4j.emptyEnumerator();
          }

          @Override public void close() {
            sourceEnumerator.close();
            resultEnumerator.close();
          }
        };
      }
    };
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>}, flattens the resulting sequences into one
   * sequence, and invokes a result selector function on each
   * element therein. The index of each source element is used in
   * the intermediate projected form of that element.
   */
  public static <TSource, TCollection, TResult> Enumerable<TResult> selectMany(
      final Enumerable<TSource> source,
      final Function2<TSource, Integer, Enumerable<TCollection>> collectionSelector,
      final Function2<TSource, TCollection, TResult> resultSelector) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          int index = -1;
          final Enumerator<TSource> sourceEnumerator = source.enumerator();
          Enumerator<TCollection> collectionEnumerator = Linq4j.emptyEnumerator();
          Enumerator<TResult> resultEnumerator = Linq4j.emptyEnumerator();

          @Override public TResult current() {
            return resultEnumerator.current();
          }

          @Override public boolean moveNext() {
            for (;;) {
              if (resultEnumerator.moveNext()) {
                return true;
              }
              if (!sourceEnumerator.moveNext()) {
                return false;
              }
              index += 1;
              final TSource sourceElement = sourceEnumerator.current();
              collectionEnumerator = collectionSelector.apply(sourceElement, index)
                  .enumerator();
              resultEnumerator =
                  new TransformedEnumerator<TCollection, TResult>(collectionEnumerator) {
                    @Override protected TResult transform(TCollection collectionElement) {
                      return resultSelector.apply(sourceElement, collectionElement);
                    }
                  };
            }
          }

          @Override public void reset() {
            sourceEnumerator.reset();
            resultEnumerator = Linq4j.emptyEnumerator();
          }

          @Override public void close() {
            sourceEnumerator.close();
            resultEnumerator.close();
          }
        };
      }
    };
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>}, flattens the resulting sequences into one
   * sequence, and invokes a result selector function on each
   * element therein.
   */
  public static <TSource, TCollection, TResult> Enumerable<TResult> selectMany(
      final Enumerable<TSource> source,
      final Function1<TSource, Enumerable<TCollection>> collectionSelector,
      final Function2<TSource, TCollection, TResult> resultSelector) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          final Enumerator<TSource> sourceEnumerator = source.enumerator();
          Enumerator<TCollection> collectionEnumerator = Linq4j.emptyEnumerator();
          Enumerator<TResult> resultEnumerator = Linq4j.emptyEnumerator();

          @Override public TResult current() {
            return resultEnumerator.current();
          }

          @Override public boolean moveNext() {
            for (;;) {
              if (resultEnumerator.moveNext()) {
                return true;
              }
              if (!sourceEnumerator.moveNext()) {
                return false;
              }
              final TSource sourceElement = sourceEnumerator.current();
              collectionEnumerator = collectionSelector.apply(sourceElement)
                  .enumerator();
              resultEnumerator =
                  new TransformedEnumerator<TCollection, TResult>(collectionEnumerator) {
                    @Override protected TResult transform(TCollection collectionElement) {
                      return resultSelector.apply(sourceElement, collectionElement);
                    }
                  };
            }
          }

          @Override public void reset() {
            sourceEnumerator.reset();
            resultEnumerator = Linq4j.emptyEnumerator();
          }

          @Override public void close() {
            sourceEnumerator.close();
            resultEnumerator.close();
          }
        };
      }
    };
  }

  /**
   * Determines whether two sequences are equal by
   * comparing the elements by using the default equality comparer
   * for their type.
   */
  public static <TSource> boolean sequenceEqual(Enumerable<TSource> first,
      Enumerable<TSource> second) {
    return sequenceEqual(first, second, null);
  }

  /**
   * Determines whether two sequences are equal by
   * comparing their elements by using a specified
   * {@code EqualityComparer<TSource>}.
   */
  public static <TSource> boolean sequenceEqual(Enumerable<TSource> first,
      Enumerable<TSource> second, @Nullable EqualityComparer<TSource> comparer) {
    requireNonNull(first, "first");
    requireNonNull(second, "second");
    if (comparer == null) {
      comparer = new EqualityComparer<TSource>() {
        @Override public boolean equal(TSource v1, TSource v2) {
          return Objects.equals(v1, v2);
        }
        @Override public int hashCode(TSource tSource) {
          return Objects.hashCode(tSource);
        }
      };
    }

    final CollectionEnumerable<TSource> firstCollection = first instanceof CollectionEnumerable
        ? ((CollectionEnumerable<TSource>) first)
        : null;
    if (firstCollection != null) {
      final CollectionEnumerable<TSource> secondCollection = second instanceof CollectionEnumerable
          ? ((CollectionEnumerable<TSource>) second)
          : null;
      if (secondCollection != null) {
        if (firstCollection.getCollection().size() != secondCollection.getCollection().size()) {
          return false;
        }
      }
    }

    try (Enumerator<TSource> os1 = first.enumerator();
         Enumerator<TSource> os2 = second.enumerator()) {
      while (os1.moveNext()) {
        if (!(os2.moveNext() && comparer.equal(os1.current(), os2.current()))) {
          return false;
        }
      }
      return !os2.moveNext();
    }
  }

  /**
   * Returns the only element of a sequence, and throws
   * an exception if there is not exactly one element in the
   * sequence.
   */
  public static <TSource> TSource single(Enumerable<TSource> source) {
    TSource toRet = null;
    try (Enumerator<TSource> os = source.enumerator()) {
      if (os.moveNext()) {
        toRet = os.current();

        if (os.moveNext()) {
          throw new IllegalStateException();
        }
      }
      if (toRet != null) {
        return toRet;
      }
      throw new IllegalStateException();
    }
  }

  /**
   * Returns the only element of a sequence that
   * satisfies a specified condition, and throws an exception if
   * more than one such element exists.
   */
  public static <TSource> TSource single(Enumerable<TSource> source,
      Predicate1<TSource> predicate) {
    TSource toRet = null;
    try (Enumerator<TSource> os = source.enumerator()) {
      while (os.moveNext()) {
        if (predicate.apply(os.current())) {
          if (toRet == null) {
            toRet = os.current();
          } else {
            throw new IllegalStateException();
          }
        }
      }
      if (toRet != null) {
        return toRet;
      }
      throw new IllegalStateException();
    }
  }

  /**
   * Returns the only element of a sequence, or a
   * default value if the sequence is empty; this method throws an
   * exception if there is more than one element in the
   * sequence.
   */
  public static <TSource> @Nullable TSource singleOrDefault(Enumerable<TSource> source) {
    TSource toRet = null;
    try (Enumerator<TSource> os = source.enumerator()) {
      if (os.moveNext()) {
        toRet = os.current();
      }

      if (os.moveNext()) {
        return null;
      }

      return toRet;
    }
  }

  /**
   * Returns the only element of a sequence that
   * satisfies a specified condition or a default value if no such
   * element exists; this method throws an exception if more than
   * one element satisfies the condition.
   */
  public static <TSource> @Nullable TSource singleOrDefault(Enumerable<TSource> source,
      Predicate1<TSource> predicate) {
    TSource toRet = null;
    for (TSource s : source) {
      if (predicate.apply(s)) {
        if (toRet != null) {
          return null;
        } else {
          toRet = s;
        }
      }
    }
    return toRet;
  }

  /**
   * Bypasses a specified number of elements in a
   * sequence and then returns the remaining elements.
   */
  public static <TSource> Enumerable<TSource> skip(Enumerable<TSource> source,
      final int count) {
    return skipWhile(source, (v1, v2) -> {
      // Count is 1-based
      return v2 < count;
    });
  }

  /**
   * Bypasses elements in a sequence as long as a
   * specified condition is true and then returns the remaining
   * elements.
   */
  public static <TSource> Enumerable<TSource> skipWhile(
      Enumerable<TSource> source, Predicate1<TSource> predicate) {
    return skipWhile(source,
        Functions.toPredicate2(predicate));
  }

  /**
   * Bypasses elements in a sequence as long as a
   * specified condition is true and then returns the remaining
   * elements. The element's index is used in the logic of the
   * predicate function.
   */
  public static <TSource> Enumerable<TSource> skipWhile(
      final Enumerable<TSource> source,
      final Predicate2<TSource, Integer> predicate) {
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        return new SkipWhileEnumerator<>(source.enumerator(), predicate);
      }
    };
  }

  /**
   * Computes the sum of the sequence of Decimal values
   * that are obtained by invoking a transform function on each
   * element of the input sequence.
   * // 计算序列中BigDecimal值的总和
   // 通过对输入序列的每个元素调用转换函数获得BigDecimal值
   */
  public static <TSource> BigDecimal sum(Enumerable<TSource> source, // 源序列
      BigDecimalFunction1<TSource> selector) { // 转换函数,将每个元素转换为BigDecimal
    return aggregate(source.select(selector), BigDecimal.ZERO, // 转换后从0开始聚合求和
        Extensions.BIG_DECIMAL_SUM); // 使用BigDecimal求和函数
  }

  /**
   * Computes the sum of the sequence of nullable
   * Decimal values that are obtained by invoking a transform
   * function on each element of the input sequence.
   * // 计算序列中可空BigDecimal值的总和
   */
  public static <TSource> BigDecimal sum(Enumerable<TSource> source, // 源序列
      NullableBigDecimalFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空BigDecimal
    return aggregate(source.select(selector), BigDecimal.ZERO, // 转换后从0开始聚合求和
        Extensions.BIG_DECIMAL_SUM); // 使用BigDecimal求和函数
  }

  /**
   * Computes the sum of the sequence of Double values
   * that are obtained by invoking a transform function on each
   * element of the input sequence.
   * // 计算序列中double值的总和
   */
  public static <TSource> double sum(Enumerable<TSource> source, // 源序列
      DoubleFunction1<TSource> selector) { // 转换函数,将每个元素转换为double
    return aggregate(source.select(adapt(selector)), 0d, Extensions.DOUBLE_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Computes the sum of the sequence of nullable
   * Double values that are obtained by invoking a transform
   * function on each element of the input sequence.
   // 计算序列中可空Double值的总和
   */
  public static <TSource> Double sum(Enumerable<TSource> source, // 源序列
      NullableDoubleFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Double
    return aggregate(source.select(selector), 0d, Extensions.DOUBLE_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Computes the sum of the sequence of int values
   * that are obtained by invoking a transform function on each
   * element of the input sequence.
   // 计算序列中int值的总和
   */
  public static <TSource> int sum(Enumerable<TSource> source, // 源序列
      IntegerFunction1<TSource> selector) { // 转换函数,将每个元素转换为int
    return aggregate(source.select(adapt(selector)), 0, Extensions.INTEGER_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Computes the sum of the sequence of nullable int
   * values that are obtained by invoking a transform function on
   * each element of the input sequence.
   // 计算序列中可空Integer值的总和
   */
  public static <TSource> Integer sum(Enumerable<TSource> source, // 源序列
      NullableIntegerFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Integer
    return aggregate(source.select(selector), 0, Extensions.INTEGER_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Computes the sum of the sequence of long values
   * that are obtained by invoking a transform function on each
   * element of the input sequence.
   // 计算序列中long值的总和
   */
  public static <TSource> long sum(Enumerable<TSource> source, // 源序列
      LongFunction1<TSource> selector) { // 转换函数,将每个元素转换为long
    return aggregate(source.select(adapt(selector)), 0L, Extensions.LONG_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Computes the sum of the sequence of nullable long
   * values that are obtained by invoking a transform function on
   * each element of the input sequence.
   // 计算序列中可空Long值的总和
   */
  public static <TSource> Long sum(Enumerable<TSource> source, // 源序列
      NullableLongFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Long
    return aggregate(source.select(selector), 0L, Extensions.LONG_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Computes the sum of the sequence of Float values
   * that are obtained by invoking a transform function on each
   * element of the input sequence.
   // 计算序列中float值的总和
   */
  public static <TSource> float sum(Enumerable<TSource> source, // 源序列
      FloatFunction1<TSource> selector) { // 转换函数,将每个元素转换为float
    return aggregate(source.select(adapt(selector)), 0F, Extensions.FLOAT_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Computes the sum of the sequence of nullable
   * Float values that are obtained by invoking a transform
   * function on each element of the input sequence.
   // 计算序列中可空Float值的总和
   */
  public static <TSource> Float sum(Enumerable<TSource> source, // 源序列
      NullableFloatFunction1<TSource> selector) { // 转换函数,将每个元素转换为可空Float
    return aggregate(source.select(selector), 0F, Extensions.FLOAT_SUM); // 转换后从0开始聚合求和
  }

  /**
   * Returns a specified number of contiguous elements
   * from the start of a sequence.
   */
  public static <TSource> Enumerable<TSource> take(Enumerable<TSource> source,
      final int count) {
    return takeWhile(
        source, (v1, v2) -> {
          // Count is 1-based
          return v2 < count;
        });
  }

  /**
   * Returns a specified number of contiguous elements
   * from the start of a sequence.
   */
  public static <TSource> Enumerable<TSource> take(Enumerable<TSource> source,
      final long count) {
    return takeWhileLong(
        source, (v1, v2) -> {
          // Count is 1-based
          return v2 < count;
        });
  }

  /**
   * Returns elements from a sequence as long as a
   * specified condition is true.
   */
  public static <TSource> Enumerable<TSource> takeWhile(
      Enumerable<TSource> source, final Predicate1<TSource> predicate) {
    return takeWhile(source,
        Functions.toPredicate2(predicate));
  }

  /**
   * Returns elements from a sequence as long as a
   * specified condition is true. The element's index is used in the
   * logic of the predicate function.
   */
  public static <TSource> Enumerable<TSource> takeWhile(
      final Enumerable<TSource> source,
      final Predicate2<TSource, Integer> predicate) {
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        return new TakeWhileEnumerator<>(source.enumerator(), predicate);
      }
    };
  }

  /**
   * Returns elements from a sequence as long as a
   * specified condition is true. The element's index is used in the
   * logic of the predicate function.
   */
  public static <TSource> Enumerable<TSource> takeWhileLong(
      final Enumerable<TSource> source,
      final Predicate2<TSource, Long> predicate) {
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        return new TakeWhileLongEnumerator<>(source.enumerator(), predicate);
      }
    };
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence according
   * to a key.
   */
  public static <TSource, TKey> OrderedEnumerable<TSource> createOrderedEnumerable(
      OrderedEnumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Comparator<TKey> comparator, boolean descending) {
    throw Extensions.todo();
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key.
   */
  public static <TSource, TKey extends Comparable<TKey>> OrderedEnumerable<TSource> thenBy(
      OrderedEnumerable<TSource> source, Function1<TSource, TKey> keySelector) {
    return createOrderedEnumerable(source, keySelector,
        Extensions.comparableComparator(), false);
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key, using a specified comparator.
   */
  public static <TSource, TKey> OrderedEnumerable<TSource> thenBy(
      OrderedEnumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Comparator<TKey> comparator) {
    return createOrderedEnumerable(source, keySelector, comparator, false);
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * descending order according to a key.
   */
  public static <TSource, TKey extends Comparable<TKey>>
      OrderedEnumerable<TSource> thenByDescending(
      OrderedEnumerable<TSource> source, Function1<TSource, TKey> keySelector) {
    return createOrderedEnumerable(source, keySelector,
        Extensions.comparableComparator(), true);
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * descending order according to a key, using a specified comparator.
   */
  public static <TSource, TKey> OrderedEnumerable<TSource> thenByDescending(
      OrderedEnumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Comparator<TKey> comparator) {
    return createOrderedEnumerable(source, keySelector, comparator, true);
  }

  /**
   * Creates a Map&lt;TKey, TValue&gt; from an
   * Enumerable&lt;TSource&gt; according to a specified key selector
   * function.
   *
   * <p>NOTE: Called {@code toDictionary} in LINQ.NET.
   */
  public static <TSource, TKey> Map<TKey, TSource> toMap(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector) {
    return toMap(source, keySelector, Functions.identitySelector());
  }

  /**
   * Creates a {@code Map<TKey, TValue>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function
   * and key comparer.
   */
  public static <TSource, TKey> Map<TKey, TSource> toMap(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      EqualityComparer<TKey> comparer) {
    return toMap(source, keySelector, Functions.identitySelector(), comparer);
  }

  /**
   * Creates a {@code Map<TKey, TValue>} from an
   * {@code Enumerable<TSource>} according to specified key selector and element
   * selector functions.
   */
  public static <TSource, TKey, TElement> Map<TKey, TElement> toMap(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Function1<TSource, TElement> elementSelector) {
    // Use LinkedHashMap because groupJoin requires order of keys to be
    // preserved.
    final Map<TKey, TElement> map = new LinkedHashMap<>();
    try (Enumerator<TSource> os = source.enumerator()) {
      while (os.moveNext()) {
        TSource o = os.current();
        map.put(keySelector.apply(o), elementSelector.apply(o));
      }
    }
    return map;
  }

  /**
   * Creates a {@code Map<TKey, TValue>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function,
   * a comparer, and an element selector function.
   */
  public static <TSource, TKey, TElement> Map<TKey, TElement> toMap(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Function1<TSource, TElement> elementSelector,
      EqualityComparer<TKey> comparer) {
    // Use LinkedHashMap because groupJoin requires order of keys to be
    // preserved.
    // Java 8 cannot infer return type with LinkedHashMap::new is used
    @SuppressWarnings("Convert2MethodRef")
    final Map<TKey, TElement> map =
        new WrapMap<>(() -> new LinkedHashMap<>(), comparer);
    try (Enumerator<TSource> os = source.enumerator()) {
      while (os.moveNext()) {
        TSource o = os.current();
        map.put(keySelector.apply(o), elementSelector.apply(o));
      }
    }
    return map;
  }

  /**
   * Creates a {@code List<TSource>} from an {@code Enumerable<TSource>}.
   */
  @SuppressWarnings("unchecked")
  public static <TSource> List<TSource> toList(Enumerable<TSource> source) {
    if (source instanceof List && source instanceof RandomAccess) {
      return (List<TSource>) source;
    } else {
      return source.into(
          source instanceof Collection
              ? new ArrayList<>(((Collection<TSource>) source).size())
              : new ArrayList<>());
    }
  }

  /**
   * Creates a Lookup&lt;TKey, TElement&gt; from an
   * Enumerable&lt;TSource&gt; according to a specified key selector
   * function.
   */
  public static <TSource, TKey> Lookup<TKey, TSource> toLookup(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector) {
    return toLookup(source, keySelector, Functions.identitySelector());
  }

  /**
   * Creates a {@code Lookup<TKey, TElement>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function
   * and key comparer.
   */
  public static <TSource, TKey> Lookup<TKey, TSource> toLookup(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      EqualityComparer<TKey> comparer) {
    return toLookup(
        source, keySelector, Functions.identitySelector(), comparer);
  }

  /**
   * Creates a {@code Lookup<TKey, TElement>} from an
   * {@code Enumerable<TSource>} according to specified key selector and element
   * selector functions.
   */
  public static <TSource, TKey, TElement> Lookup<TKey, TElement> toLookup(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Function1<TSource, TElement> elementSelector) {
    final Map<TKey, List<TElement>> map = new HashMap<>();
    return toLookup_(map, source, keySelector, elementSelector);
  }

  static <TSource, TKey, TElement> LookupImpl<TKey, TElement> toLookup_(
      Map<TKey, List<TElement>> map, Enumerable<TSource> source,
      Function1<TSource, TKey> keySelector,
      Function1<TSource, TElement> elementSelector) {
    try (Enumerator<TSource> os = source.enumerator()) {
      while (os.moveNext()) {
        TSource o = os.current();
        final TKey key = keySelector.apply(o);
        @SuppressWarnings("nullness")
        List<TElement> list = map.get(key);
        if (list == null) {
          // for first entry, use a singleton list to save space
          list = Collections.singletonList(elementSelector.apply(o));
        } else {
          if (list.size() == 1) {
            // when we go from 1 to 2 elements, switch to array list
            TElement element = list.get(0);
            list = new ArrayList<>();
            list.add(element);
          }
          list.add(elementSelector.apply(o));
        }
        map.put(key, list);
      }
    }
    return new LookupImpl<>(map);
  }

  /**
   * Creates a {@code Lookup<TKey, TElement>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function,
   * a comparer and an element selector function.
   */
  public static <TSource, TKey, TElement> Lookup<TKey, TElement> toLookup(
      Enumerable<TSource> source, Function1<TSource, TKey> keySelector,
      Function1<TSource, TElement> elementSelector,
      EqualityComparer<TKey> comparer) {
    return toLookup_(
        new WrapMap<>(
            // Java 8 cannot infer return type with HashMap::new is used
            () -> new HashMap<Wrapped<TKey>, List<TElement>>(),
          comparer),
        source,
        keySelector,
        elementSelector);
  }

  /**
   * Produces the set union of two sequences by using
   * the default equality comparer.
   */
  public static <TSource> Enumerable<TSource> union(Enumerable<TSource> source0,
      Enumerable<TSource> source1) {
    Set<TSource> set = new HashSet<>();
    source0.into(set);
    source1.into(set);
    return Linq4j.asEnumerable(set);
  }

  /**
   * Produces the set union of two sequences by using a
   * specified EqualityComparer&lt;TSource&gt;.
   */
  public static <TSource> Enumerable<TSource> union(Enumerable<TSource> source0,
      Enumerable<TSource> source1, final EqualityComparer<TSource> comparer) {
    if (comparer == Functions.identityComparer()) {
      return union(source0, source1);
    }
    Set<Wrapped<TSource>> set = new HashSet<>();
    Function1<TSource, Wrapped<TSource>> wrapper = wrapperFor(comparer);
    Function1<Wrapped<TSource>, TSource> unwrapper = unwrapper();
    source0.select(wrapper).into(set);
    source1.select(wrapper).into(set);
    return Linq4j.asEnumerable(set).select(unwrapper);
  }

  private static <TSource> Function1<Wrapped<TSource>, TSource> unwrapper() {
    return a0 -> a0.element;
  }

  static <TSource> Function1<TSource, Wrapped<TSource>> wrapperFor(
      final EqualityComparer<TSource> comparer) {
    return a0 -> Wrapped.upAs(comparer, a0);
  }

  /**
   * Filters a sequence of values based on a
   * predicate.
   * // 基于谓词过滤序列中的值
   // 只保留满足谓词条件的元素
   */
  public static <TSource> Enumerable<TSource> where( // 返回过滤后的序列
      final Enumerable<TSource> source, final Predicate1<TSource> predicate) { // 源序列和谓词函数
    requireNonNull(predicate, "predicate"); // 确保谓词不为null
    return new AbstractEnumerable<TSource>() { // 创建抽象的可枚举对象
      @Override public Enumerator<TSource> enumerator() { // 创建枚举器
        final Enumerator<TSource> enumerator = source.enumerator(); // 获取源序列的枚举器
        return EnumerableDefaults.where(enumerator, predicate); // 调用私有的where方法创建过滤枚举器
      }
    };
  }

  private static <TSource> Enumerator<TSource> where( // 私有方法,创建过滤枚举器
      final Enumerator<TSource> enumerator, // 源枚举器
      final Predicate1<TSource> predicate) { // 谓词函数
    return new Enumerator<TSource>() { // 创建新的枚举器
      @Override public TSource current() { // 获取当前元素
        return enumerator.current(); // 返回源枚举器的当前元素
      }

      @Override public boolean moveNext() { // 移动到下一个元素
        while (enumerator.moveNext()) { // 循环遍历源枚举器的所有元素
          if (predicate.apply(enumerator.current())) { // 测试元素是否满足谓词条件
            return true; // 满足条件,返回true
          }
        }
        return false; // 没有更多满足条件的元素,返回false
      }

      @Override public void reset() { // 重置枚举器
        enumerator.reset(); // 委托给源枚举器
      }

      @Override public void close() { // 关闭枚举器
        enumerator.close(); // 委托给源枚举器
      }
    };
  }

  /**
   * Filters a sequence of values based on a
   * predicate. Each element's index is used in the logic of the
   * predicate function.
   * // 基于谓词过滤序列中的值,谓词函数中使用每个元素的索引
   // 索引从0开始
   */
  public static <TSource> Enumerable<TSource> where( // 返回过滤后的序列
      final Enumerable<TSource> source, // 源序列
      final Predicate2<TSource, Integer> predicate) { // 谓词函数,接收元素和索引
    return new AbstractEnumerable<TSource>() { // 创建抽象的可枚举对象
      @Override public Enumerator<TSource> enumerator() { // 创建枚举器
        return new Enumerator<TSource>() { // 创建新的枚举器
          final Enumerator<TSource> enumerator = source.enumerator(); // 获取源序列的枚举器
          int n = -1; // 索引计数器,初始值为-1

          @Override public TSource current() { // 获取当前元素
            return enumerator.current(); // 返回源枚举器的当前元素
          }

          @Override public boolean moveNext() { // 移动到下一个元素
            while (enumerator.moveNext()) { // 循环遍历源枚举器的所有元素
              ++n; // 索引加1
              if (predicate.apply(enumerator.current(), n)) { // 测试元素和索引是否满足谓词条件
                return true; // 满足条件,返回true
              }
            }
            return false; // 没有更多满足条件的元素,返回false
          }

          @Override public void reset() { // 重置枚举器
            enumerator.reset(); // 委托给源枚举器
            n = -1; // 重置索引计数器
          }

          @Override public void close() { // 关闭枚举器
            enumerator.close(); // 委托给源枚举器
          }
        };
      }
    };
  }

  /**
   * Applies a specified function to the corresponding
   * elements of two sequences, producing a sequence of the
   * results.
   */
  public static <T0, T1, TResult> Enumerable<TResult> zip(
      final Enumerable<T0> first, final Enumerable<T1> second,
      final Function2<T0, T1, TResult> resultSelector) {
    return new AbstractEnumerable<TResult>() {
      @Override public Enumerator<TResult> enumerator() {
        return new Enumerator<TResult>() {
          final Enumerator<T0> e1 = first.enumerator();
          final Enumerator<T1> e2 = second.enumerator();

          @Override public TResult current() {
            return resultSelector.apply(e1.current(), e2.current());
          }
          @Override public boolean moveNext() {
            return e1.moveNext() && e2.moveNext();
          }
          @Override public void reset() {
            e1.reset();
            e2.reset();
          }
          @Override public void close() {
            e1.close();
            e2.close();
          }
        };
      }
    };
  }

  public static <T> OrderedQueryable<T> asOrderedQueryable(
      Enumerable<T> source) {
    //noinspection unchecked
    return source instanceof OrderedQueryable
        ? ((OrderedQueryable<T>) source)
        : new EnumerableOrderedQueryable<>(
            source, (Class) Object.class, requireNonNull(null, "null"), null);
  }

  /** Default implementation of {@link ExtendedEnumerable#into(Collection)}. */
  public static <T, C extends Collection<? super T>> C into(
      Enumerable<T> source, C sink) {
    try (Enumerator<T> enumerator = source.enumerator()) {
      while (enumerator.moveNext()) {
        T t = enumerator.current();
        sink.add(t);
      }
    }
    return sink;
  }

  /** Default implementation of {@link ExtendedEnumerable#removeAll(Collection)}. */
  public static <T, C extends Collection<? super T>> C remove(
      Enumerable<T> source, C sink) {
    List<T> tempList = new ArrayList<>();
    source.into(tempList);
    sink.removeAll(tempList);
    return sink;
  }

  /** Enumerable that implements take-while.
   *
   * @param <TSource> element type */
  static class TakeWhileEnumerator<TSource> implements Enumerator<TSource> {
    private final Enumerator<TSource> enumerator;
    private final Predicate2<TSource, Integer> predicate;

    boolean done = false;
    int n = -1;

    TakeWhileEnumerator(Enumerator<TSource> enumerator,
        Predicate2<TSource, Integer> predicate) {
      this.enumerator = enumerator;
      this.predicate = predicate;
    }

    @Override public TSource current() {
      return enumerator.current();
    }

    @Override public boolean moveNext() {
      if (!done) {
        if (enumerator.moveNext()
            && predicate.apply(enumerator.current(), ++n)) {
          return true;
        } else {
          done = true;
        }
      }
      return false;
    }

    @Override public void reset() {
      enumerator.reset();
      done = false;
      n = -1;
    }

    @Override public void close() {
      enumerator.close();
    }
  }

  /** Enumerable that implements take-while.
   *
   * @param <TSource> element type */
  static class TakeWhileLongEnumerator<TSource> implements Enumerator<TSource> {
    private final Enumerator<TSource> enumerator;
    private final Predicate2<TSource, Long> predicate;

    boolean done = false;
    long n = -1;

    TakeWhileLongEnumerator(Enumerator<TSource> enumerator,
        Predicate2<TSource, Long> predicate) {
      this.enumerator = enumerator;
      this.predicate = predicate;
    }

    @Override public TSource current() {
      return enumerator.current();
    }

    @Override public boolean moveNext() {
      if (!done) {
        if (enumerator.moveNext()
            && predicate.apply(enumerator.current(), ++n)) {
          return true;
        } else {
          done = true;
        }
      }
      return false;
    }

    @Override public void reset() {
      enumerator.reset();
      done = false;
      n = -1;
    }

    @Override public void close() {
      enumerator.close();
    }
  }

  /** Enumerator that implements skip-while.
   *
   * @param <TSource> element type */
  static class SkipWhileEnumerator<TSource> implements Enumerator<TSource> {
    private final Enumerator<TSource> enumerator;
    private final Predicate2<TSource, Integer> predicate;

    boolean started = false;
    int n = -1;

    SkipWhileEnumerator(Enumerator<TSource> enumerator,
        Predicate2<TSource, Integer> predicate) {
      this.enumerator = enumerator;
      this.predicate = predicate;
    }

    @Override public TSource current() {
      return enumerator.current();
    }

    @Override public boolean moveNext() {
      for (;;) {
        if (!enumerator.moveNext()) {
          return false;
        }
        if (started) {
          return true;
        }
        if (!predicate.apply(enumerator.current(), ++n)) {
          started = true;
          return true;
        }
      }
    }

    @Override public void reset() {
      enumerator.reset();
      started = false;
      n = -1;
    }

    @Override public void close() {
      enumerator.close();
    }
  }

  /** Enumerator that casts each value.
   *
   * <p>If the source type {@code F} is not nullable, the target element type
   * {@code T} is not nullable. In other words, an enumerable over elements that
   * are not null will yield another enumerable over elements that are not null.
   *
   * @param <F> source element type
   * @param <T> element type
   */
  @HasQualifierParameter(Nullable.class)
  static class CastingEnumerator<F extends @PolyNull Object, @PolyNull T extends @PolyNull Object>
      implements Enumerator<T> {
    private final Enumerator<F> enumerator;
    private final Class<T> clazz;

    CastingEnumerator(Enumerator<F> enumerator, Class<T> clazz) {
      this.enumerator = enumerator;
      this.clazz = clazz;
    }

    @Override public T current() {
      return clazz.cast(enumerator.current());
    }

    @Override public boolean moveNext() {
      return enumerator.moveNext();
    }

    @Override public void reset() {
      enumerator.reset();
    }

    @Override public void close() {
      enumerator.close();
    }
  }

  /** Value wrapped with a comparer.
   *
   * @param <T> element type */
  static class Wrapped<T> {
    private final EqualityComparer<T> comparer;
    private final T element;

    private Wrapped(EqualityComparer<T> comparer, T element) {
      this.comparer = comparer;
      this.element = element;
    }

    static <T> Wrapped<T> upAs(EqualityComparer<T> comparer, T element) {
      return new Wrapped<>(comparer, element);
    }

    @Override public int hashCode() {
      return comparer.hashCode(element);
    }

    @Override public boolean equals(@Nullable Object obj) {
      //noinspection unchecked
      return obj == this || obj instanceof Wrapped && comparer.equal(element,
          ((Wrapped<T>) obj).element);
    }

    public T unwrap() {
      return element;
    }
  }

  /** Map that wraps each value.
   *
   * @param <K> key type
   * @param <V> value type */
  private static class WrapMap<K, V> extends AbstractMap<K, V> {
    private final Map<Wrapped<K>, V> map;
    private final EqualityComparer<K> comparer;

    protected WrapMap(Function0<Map<Wrapped<K>, V>> mapProvider, EqualityComparer<K> comparer) {
      this.map = mapProvider.apply();
      this.comparer = comparer;
    }

    @Override public Set<Entry<@KeyFor("this") K, V>> entrySet() {
      return new AbstractSet<Entry<@KeyFor("this") K, V>>() {
        @SuppressWarnings("override.return.invalid")
        @Override public Iterator<Entry<K, V>> iterator() {
          final Iterator<Entry<Wrapped<K>, V>> iterator =
              map.entrySet().iterator();

          return new Iterator<Entry<K, V>>() {
            @Override public boolean hasNext() {
              return iterator.hasNext();
            }

            @Override public Entry<K, V> next() {
              Entry<Wrapped<K>, V> next = iterator.next();
              return new SimpleEntry<>(next.getKey().element, next.getValue());
            }

            @Override public void remove() {
              iterator.remove();
            }
          };
        }

        @Override public int size() {
          return map.size();
        }
      };
    }

    @SuppressWarnings("contracts.conditional.postcondition.not.satisfied")
    @Override public boolean containsKey(@Nullable Object key) {
      return map.containsKey(wrap((K) key));
    }

    @Pure
    private Wrapped<K> wrap(K key) {
      return Wrapped.upAs(comparer, key);
    }

    @Override public @Nullable V get(@Nullable Object key) {
      return map.get(wrap((K) key));
    }

    @SuppressWarnings("contracts.postcondition.not.satisfied")
    @Override public @Nullable V put(K key, V value) {
      return map.put(wrap(key), value);
    }

    @Override public @Nullable V remove(@Nullable Object key) {
      return map.remove(wrap((K) key));
    }

    @Override public void clear() {
      map.clear();
    }

    @Override public Collection<V> values() {
      return map.values();
    }
  }

  /** Reads a populated map, applying a selector function.
   *
   * @param <TResult> result type
   * @param <TKey> key type
   * @param <TAccumulate> accumulator type */
  private static class LookupResultEnumerable<TResult, TKey, TAccumulate>
      extends AbstractEnumerable2<TResult> {
    private final Map<TKey, TAccumulate> map;
    private final Function2<TKey, TAccumulate, TResult> resultSelector;

    LookupResultEnumerable(Map<TKey, TAccumulate> map,
        Function2<TKey, TAccumulate, TResult> resultSelector) {
      this.map = map;
      this.resultSelector = resultSelector;
    }

    @Override public Iterator<TResult> iterator() {
      final Iterator<Map.Entry<TKey, TAccumulate>> iterator =
          map.entrySet().iterator();
      return new Iterator<TResult>() {
        @Override public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override public TResult next() {
          final Map.Entry<TKey, TAccumulate> entry = iterator.next();
          return resultSelector.apply(entry.getKey(), entry.getValue());
        }

        @Override public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }
  }

  /** Enumerator that performs a merge join on its sorted inputs.
   * Inputs must be sorted in ascending order, nulls last.
   *
   * @param <TResult> result type
   * @param <TSource> left input record type
   * @param <TKey> key type
   * @param <TInner> right input record type */
  @SuppressWarnings("unchecked")
  private static class MergeJoinEnumerator<TResult, TSource, TInner, TKey extends Comparable<TKey>>
      implements Enumerator<TResult> {
    private final List<TSource> lefts = new ArrayList<>();
    private final List<TInner> rights = new ArrayList<>();
    private final Enumerable<TSource> leftEnumerable;
    private final Enumerable<TInner> rightEnumerable;
    private @Nullable Enumerator<TSource> leftEnumerator = null;
    private @Nullable Enumerator<TInner> rightEnumerator = null;
    private final Function1<TSource, TKey> outerKeySelector;
    private final Function1<TInner, TKey> innerKeySelector;
    // extra predicate in case of non equi-join, in case of equi-join it will be null
    private final @Nullable Predicate2<TSource, TInner> extraPredicate;
    private final Function2<TSource, @Nullable TInner, TResult> resultSelector;
    private final JoinType joinType;
    // key comparator, possibly null (Comparable#compareTo to be used in that case)
    private final @Nullable Comparator<TKey> comparator;
    // key equality comparer, possibly null (equals to be used in that case)
    private final @Nullable EqualityComparer<TKey> equalityComparer;
    private boolean done;
    private @Nullable Enumerator<TResult> results = null;
    // used for LEFT/ANTI join: if right input is over, all remaining elements from left are results
    private boolean remainingLeft;
    private TResult current = (TResult) DUMMY;

    @SuppressWarnings("method.invocation.invalid")
    MergeJoinEnumerator(Enumerable<TSource> leftEnumerable,
        Enumerable<TInner> rightEnumerable,
        Function1<TSource, TKey> outerKeySelector,
        Function1<TInner, TKey> innerKeySelector,
        @Nullable Predicate2<TSource, TInner> extraPredicate,
        Function2<TSource, @Nullable TInner, TResult> resultSelector,
        JoinType joinType,
        @Nullable Comparator<TKey> comparator,
        @Nullable EqualityComparer<TKey> equalityComparer) {
      this.leftEnumerable = leftEnumerable;
      this.rightEnumerable = rightEnumerable;
      this.outerKeySelector = outerKeySelector;
      this.innerKeySelector = innerKeySelector;
      this.extraPredicate = extraPredicate;
      this.resultSelector = resultSelector;
      this.joinType = joinType;
      this.comparator = comparator;
      this.equalityComparer = equalityComparer;
      start();
    }

    private Enumerator<TSource> getLeftEnumerator() {
      if (leftEnumerator == null) {
        leftEnumerator = leftEnumerable.enumerator();
      }
      return leftEnumerator;
    }

    private Enumerator<TInner> getRightEnumerator() {
      if (rightEnumerator == null) {
        rightEnumerator = rightEnumerable.enumerator();
      }
      return rightEnumerator;
    }

    /** Returns whether the left enumerator was successfully advanced to the next
     * element, and it does not have a null key (except for LEFT join, that needs to process
     * all elements from left. */
    private boolean leftMoveNext() {
      return getLeftEnumerator().moveNext()
          && (joinType == JoinType.LEFT
              || outerKeySelector.apply(getLeftEnumerator().current()) != null);
    }

    /** Returns whether the right enumerator was successfully advanced to the
     * next element, and it does not have a null key. */
    private boolean rightMoveNext() {
      return getRightEnumerator().moveNext()
          && innerKeySelector.apply(getRightEnumerator().current()) != null;
    }

    private boolean isLeftOrAntiJoin() {
      return joinType == JoinType.LEFT || joinType == JoinType.ANTI;
    }

    private void start() {
      if (isLeftOrAntiJoin()) {
        startLeftOrAntiJoin();
      } else {
        // joinType INNER or SEMI
        if (!leftMoveNext() || !rightMoveNext() || !advance()) {
          finish();
        }
      }
    }

    private void startLeftOrAntiJoin() {
      if (!leftMoveNext()) {
        finish();
      } else {
        if (!rightMoveNext()) {
          // all remaining items in left are results for anti join
          remainingLeft = true;
        } else {
          if (!advance()) {
            finish();
          }
        }
      }
    }

    private void finish() {
      done = true;
      results = Linq4j.emptyEnumerator();
    }

    /** Method to compare keys from left and right input (nulls must not be considered equal). */
    private int compare(TKey key1, TKey key2) {
      return comparator != null
          ? comparator.compare(key1, key2)
          : compareNullsLastForMergeJoin(key1, key2);
    }

    /** Method to compare keys from the same input (nulls must be considered equal). */
    private boolean compareEquals(TKey key1, TKey key2) {
      return equalityComparer != null
          ? equalityComparer.equal(key1, key2)
          : Objects.equals(key1, key2);
    }

    /** Moves to the next key that is present in both sides. Populates
     * lefts and rights with the rows. Restarts the cross-join
     * enumerator. */
    private boolean advance() {
      for (;;) {
        TSource left = requireNonNull(leftEnumerator, "leftEnumerator").current();
        TKey leftKey = outerKeySelector.apply(left);
        TInner right = requireNonNull(rightEnumerator, "rightEnumerator").current();
        TKey rightKey = innerKeySelector.apply(right);
        // iterate until finding matching keys (or ANTI join results)
        for (;;) {
          // mergeJoin assumes inputs sorted in ascending order with nulls last,
          // if we reach a null key, we are done.
          if (leftKey == null || rightKey == null) {
            if (joinType == JoinType.LEFT || (joinType == JoinType.ANTI && leftKey != null)) {
              // all remaining items in left are results for left/anti join
              remainingLeft = true;
              return true;
            }
            done = true;
            return false;
          }
          int c;
          try {
            c = compare(leftKey, rightKey);
          } catch (BothValuesAreNullException e) {
            // consider the first (left) value as "bigger", to advance on the right value
            // and continue with the algorithm
            c = 1;
          }
          if (c == 0) {
            break;
          }
          if (c < 0) {
            if (isLeftOrAntiJoin()) {
              // left (and all other items with the same key) are results for left/anti join
              if (!advanceLeft(left, leftKey)) {
                done = true;
              }
              results =
                  new CartesianProductJoinEnumerator<>(resultSelector,
                      Linq4j.enumerator(lefts),
                      Linq4j.enumerator(Collections.singletonList(null)));
              return true;
            }
            if (!getLeftEnumerator().moveNext()) {
              done = true;
              return false;
            }
            left = getLeftEnumerator().current();
            leftKey = outerKeySelector.apply(left);
          } else {
            if (!getRightEnumerator().moveNext()) {
              if (isLeftOrAntiJoin()) {
                // all remaining items in left are results for left/anti join
                remainingLeft = true;
                return true;
              }
              done = true;
              return false;
            }
            right = getRightEnumerator().current();
            rightKey = innerKeySelector.apply(right);
          }
        }

        if (!advanceLeft(left, leftKey)) {
          done = true;
        }

        if (!advanceRight(right, rightKey)) {
          if (!done && isLeftOrAntiJoin()) {
            // all remaining items in left are results for left/anti join
            remainingLeft = true;
          } else {
            done = true;
          }
        }

        if (extraPredicate == null) {
          if (joinType == JoinType.ANTI) {
            if (done) {
              return false;
            }
            if (remainingLeft) {
              return true;
            }
            continue;
          }

          // SEMI join must not have duplicates, in that case take just one element from rights
          results = joinType == JoinType.SEMI
              ? new CartesianProductJoinEnumerator<>(resultSelector, Linq4j.enumerator(lefts),
              Linq4j.enumerator(Collections.singletonList(rights.get(0))))
              : new CartesianProductJoinEnumerator<>(resultSelector, Linq4j.enumerator(lefts),
                  Linq4j.enumerator(rights));
        } else {
          // we must verify the non equi-join predicate, use nested loop join for that
          results =
              nestedLoopJoin(Linq4j.asEnumerable(lefts),
                  Linq4j.asEnumerable(rights), extraPredicate, resultSelector,
                  joinType).enumerator();
        }
        return true;
      }
    }



    /**
     * Clears {@code left} list, adds {@code left} into it, and advance left enumerator,
     * adding all items with the same key to {@code left} list too, until left enumerator
     * is over or a different key is found.
     *
     * @return {@code true} if there are still elements to be processed on the left enumerator,
     * {@code false} otherwise (left enumerator is over or null key is found).
     */
    private boolean advanceLeft(TSource left, TKey leftKey) {
      lefts.clear();
      lefts.add(left);
      while (getLeftEnumerator().moveNext()) {
        left = getLeftEnumerator().current();
        TKey leftKey2 = outerKeySelector.apply(left);
        if (leftKey2 == null && joinType != JoinType.LEFT) {
          // mergeJoin assumes inputs sorted in ascending order with nulls last,
          // if we reach a null key, we are done (except LEFT join, that needs to process LHS fully)
          break;
        }
        if (!compareEquals(leftKey, leftKey2)) {
          return true;
        }
        lefts.add(left);
      }
      return false;
    }

    /**
     * Clears {@code right} list, adds {@code right} into it, and advance right enumerator,
     * adding all items with the same key to {@code right} list too, until right enumerator
     * is over or a different key is found.
     *
     * @return {@code true} if there are still elements to be processed on the right enumerator,
     * {@code false} otherwise (right enumerator is over or null key is found).
     */
    private boolean advanceRight(TInner right, TKey rightKey) {
      rights.clear();
      rights.add(right);
      while (getRightEnumerator().moveNext()) {
        right = getRightEnumerator().current();
        TKey rightKey2 = innerKeySelector.apply(right);
        if (rightKey2 == null) {
          // mergeJoin assumes inputs sorted in ascending order with nulls last,
          // if we reach a null key, we are done
          break;
        }
        if (!compareEquals(rightKey, rightKey2)) {
          return true;
        }
        rights.add(right);
      }
      return false;
    }

    @Override public TResult current() {
      if (current == DUMMY) {
        throw new NoSuchElementException();
      }
      return current;
    }

    @Override public boolean moveNext() {
      for (;;) {
        if (results != null) {
          if (results.moveNext()) {
            current = results.current();
            return true;
          } else {
            results = null;
          }
        }
        if (remainingLeft) {
          current = resultSelector.apply(getLeftEnumerator().current(), null);
          if (!leftMoveNext()) {
            remainingLeft = false;
            done = true;
          }
          return true;
        }
        if (done) {
          return false;
        }
        if (!advance()) {
          return false;
        }
      }
    }

    @Override public void reset() {
      done = false;
      results = null;
      current = (TResult) DUMMY;
      remainingLeft = false;
      if (leftEnumerator != null) {
        leftEnumerator.reset();
      }
      if (rightEnumerator != null) {
        rightEnumerator.reset();
      }
      start();
    }

    @Override public void close() {
      if (leftEnumerator != null) {
        leftEnumerator.close();
      }
      if (rightEnumerator != null) {
        rightEnumerator.close();
      }
    }
  }

  /**
   * Exception used for control flow (it does not populate the stack trace to be more efficient)
   * to signal that both values are <code>null</code>, so that the caller method (i.e. MergeJoin
   * algorithm) can act accordingly.
   */
  private static class BothValuesAreNullException extends RuntimeException {
    @Override public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  public static int compareNullsLastForMergeJoin(@Nullable Comparable v0, @Nullable Comparable v1) {
    return compareNullsLastForMergeJoin(v0, v1, null);
  }

  public static int compareNullsLastForMergeJoin(@Nullable Comparable v0, @Nullable Comparable v1,
      @Nullable Comparator comparator) {
    // Special code for mergeJoin algorithm: in case of two null values, they must not be
    // considered as equal (otherwise the join would return incorrect results)
    if (v0 == null && v1 == null) {
      throw new BothValuesAreNullException();
    }

    //noinspection unchecked
    return v0 == v1 ? 0
        : v0 == null ? 1
            : v1 == null ? -1
                : comparator == null ? v0.compareTo(v1) : comparator.compare(v0, v1);
  }

  /** Enumerates the elements of a cartesian product of two inputs.
   *
   * @param <TResult> result type
   * @param <TOuter> left input record type
   * @param <TInner> right input record type */
  private static class CartesianProductJoinEnumerator<TResult, TOuter, TInner>
      extends CartesianProductEnumerator<Object, TResult> {
    private final Function2<TOuter, @Nullable TInner, TResult> resultSelector;

    @SuppressWarnings("unchecked")
    CartesianProductJoinEnumerator(Function2<TOuter, @Nullable TInner, TResult> resultSelector,
        Enumerator<TOuter> outer, Enumerator<TInner> inner) {
      super(ImmutableList.of((Enumerator<Object>) outer, (Enumerator<Object>) inner));
      this.resultSelector = resultSelector;
    }

    @SuppressWarnings("unchecked")
    @Override public TResult current() {
      final TOuter outer = (TOuter) elements[0];
      final TInner inner = (TInner) elements[1];
      return this.resultSelector.apply(outer, inner);
    }
  }

  private static final Object DUMMY = new Object();

  /**
   * Repeat Union enumerable. Evaluates the seed enumerable once, and then starts
   * to evaluate the iteration enumerable over and over, until either it returns
   * no results, or it reaches an optional maximum number of iterations.
   *
   * @param seed seed enumerable
   * @param iteration iteration enumerable
   * @param iterationLimit maximum numbers of repetitions for the iteration enumerable
   *                       (negative value means no limit)
   * @param all whether duplicates will be considered or not
   * @param comparer {@link EqualityComparer} to control duplicates,
   *                 only used if {@code all} is {@code false}
   * @param cleanUpFunction optional clean-up actions (e.g. delete temporary table)
   * @param <TSource> record type
   */
  @SuppressWarnings("unchecked")
  public static <TSource> Enumerable<TSource> repeatUnion(
      Enumerable<TSource> seed,
      Enumerable<TSource> iteration,
      int iterationLimit,
      boolean all,
      EqualityComparer<TSource> comparer,
      @Nullable Function0<Boolean> cleanUpFunction) {
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        return new Enumerator<TSource>() {
          private TSource current = (TSource) DUMMY;
          private boolean seedProcessed = false;
          private int currentIteration = 0;
          private final Enumerator<TSource> seedEnumerator = seed.enumerator();
          private @Nullable Enumerator<TSource> iterativeEnumerator = null;

          // Set to control duplicates, only used if "all" is false
          private final Set<Wrapped<TSource>> processed = new HashSet<>();
          private final Function1<TSource, Wrapped<TSource>> wrapper = wrapperFor(comparer);

          @Override public TSource current() {
            if (current == DUMMY) {
              throw new NoSuchElementException();
            }
            return current;
          }

          private boolean checkValue(TSource value) {
            if (all) {
              return true; // no need to check duplicates
            }

            // check duplicates
            final Wrapped<TSource> wrapped = wrapper.apply(value);
            if (!processed.contains(wrapped)) {
              processed.add(wrapped);
              return true;
            }

            return false;
          }

          @Override public boolean moveNext() {
            // if we are not done with the seed moveNext on it
            while (!seedProcessed) {
              if (seedEnumerator.moveNext()) {
                TSource value = seedEnumerator.current();
                if (checkValue(value)) {
                  current = value;
                  return true;
                }
              } else {
                seedProcessed = true;
              }
            }

            // if we are done with the seed, moveNext on the iterative part
            while (true) {
              if (iterationLimit >= 0 && currentIteration == iterationLimit) {
                // max number of iterations reached, we are done
                current = (TSource) DUMMY;
                return false;
              }

              Enumerator<TSource> iterativeEnumerator = this.iterativeEnumerator;
              if (iterativeEnumerator == null) {
                this.iterativeEnumerator = iterativeEnumerator = iteration.enumerator();
              }

              while (iterativeEnumerator.moveNext()) {
                TSource value = iterativeEnumerator.current();
                if (checkValue(value)) {
                  current = value;
                  return true;
                }
              }

              if (current == DUMMY) {
                // current iteration did not return any value, we are done
                return false;
              }

              // current iteration level (which returned some values) is finished, go to next one
              current = (TSource) DUMMY;
              iterativeEnumerator.close();
              this.iterativeEnumerator = null;
              currentIteration++;
            }
          }

          @Override public void reset() {
            seedEnumerator.reset();
            seedProcessed = false;
            processed.clear();
            if (iterativeEnumerator != null) {
              iterativeEnumerator.close();
              iterativeEnumerator = null;
            }
            currentIteration = 0;
          }

          @Override public void close() {
            if (cleanUpFunction != null) {
              cleanUpFunction.apply();
            }
            seedEnumerator.close();
            if (iterativeEnumerator != null) {
              iterativeEnumerator.close();
            }
          }
        };
      }
    };
  }

  /** Lazy read and lazy write spool that stores data into a collection. */
  @SuppressWarnings("unchecked")
  public static <TSource> Enumerable<TSource> lazyCollectionSpool(
      Collection<TSource> outputCollection,
      Enumerable<TSource> input) {

    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        return new Enumerator<TSource>() {
          private TSource current = (TSource) DUMMY;
          private final Enumerator<TSource> inputEnumerator = input.enumerator();
          private final Collection<TSource> collection = outputCollection;
          private final Collection<TSource> tempCollection = new ArrayList<>();

          @Override public TSource current() {
            if (current == DUMMY) {
              throw new NoSuchElementException();
            }
            return current;
          }

          @Override public boolean moveNext() {
            if (inputEnumerator.moveNext()) {
              current = inputEnumerator.current();
              tempCollection.add(current);
              return true;
            }
            flush();
            return false;
          }

          private void flush() {
            collection.clear();
            collection.addAll(tempCollection);
            tempCollection.clear();
          }

          @Override public void reset() {
            inputEnumerator.reset();
            collection.clear();
            tempCollection.clear();
          }

          @Override public void close() {
            inputEnumerator.close();
          }
        };
      }
    };
  }

  /**
   * Merge Union Enumerable.
   * Performs a union (or union all) of all its inputs (which must be already sorted),
   * respecting the order.
   *
   * @param sources input enumerables (must be already sorted)
   * @param sortKeySelector sort key selector
   * @param sortComparator sort comparator to decide the next item
   * @param all whether duplicates will be considered or not
   * @param equalityComparer {@link EqualityComparer} to control duplicates,
   *                         only used if {@code all} is {@code false}
   * @param <TSource> record type
   * @param <TKey> sort key
   */
  public static <TSource, TKey> Enumerable<TSource> mergeUnion(
      List<Enumerable<TSource>> sources,
      Function1<TSource, TKey> sortKeySelector,
      Comparator<TKey> sortComparator,
      boolean all,
      EqualityComparer<TSource> equalityComparer) {
    return new AbstractEnumerable<TSource>() {
      @Override public Enumerator<TSource> enumerator() {
        return new MergeUnionEnumerator<>(
            sources,
            sortKeySelector,
            sortComparator,
            all,
            equalityComparer);
      }
    };
  }
}
