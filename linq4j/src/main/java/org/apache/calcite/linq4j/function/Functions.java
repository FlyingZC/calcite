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
package org.apache.calcite.linq4j.function;

import com.google.common.collect.Lists;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.checkerframework.framework.qual.TypeUseLocation;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.IntFunction;

/**
 * Utilities relating to functions.
 */
public abstract class Functions { // 函数工具类，提供各种函数式编程相关的静态工具方法，包括函数适配、列表转换、比较器、相等比较器等功能
  private Functions() {} // 私有构造方法，防止实例化，这是一个纯工具类

  public static final Map<Class<? extends Function>, Class> FUNCTION_RESULT_TYPES = // 函数接口到其返回类型的映射表，记录各种函数接口的返回类型，用于类型推断和函数适配
      Collections.unmodifiableMap(
          map(Function0.class, Object.class, // Function0返回Object类型
              Function1.class, Object.class, // Function1返回Object类型
              Function2.class, Object.class, // Function2返回Object类型
              BigDecimalFunction1.class, BigDecimal.class, // BigDecimalFunction1返回BigDecimal类型
              DoubleFunction1.class, Double.TYPE, // DoubleFunction1返回基本double类型
              FloatFunction1.class, Float.TYPE, // FloatFunction1返回基本float类型
              IntegerFunction1.class, Integer.TYPE, // IntegerFunction1返回基本int类型
              LongFunction1.class, Long.TYPE, // LongFunction1返回基本long类型
              NullableBigDecimalFunction1.class, BigDecimal.class, // NullableBigDecimalFunction1返回BigDecimal类型
              NullableDoubleFunction1.class, Double.class, // NullableDoubleFunction1返回包装Double类型
              NullableFloatFunction1.class, Float.class, // NullableFloatFunction1返回包装Float类型
              NullableIntegerFunction1.class, Integer.class, // NullableIntegerFunction1返回包装Integer类型
              NullableLongFunction1.class, Long.class)); // NullableLongFunction1返回包装Long类型

  private static final Map<Class, Class<? extends Function>> FUNCTION1_CLASSES = // 返回类型到函数接口的反向映射表，用于根据返回类型查找对应的函数接口
      Collections.unmodifiableMap(
          new HashMap<>(inverse(FUNCTION_RESULT_TYPES))); // 通过反转FUNCTION_RESULT_TYPES映射得到

  private static final Comparator NULLS_FIRST_COMPARATOR = // 空值优先比较器，将null值排在最前面
      new NullsFirstComparator();

  private static final Comparator NULLS_LAST_COMPARATOR = // 空值置后比较器，将null值排在最后面
      new NullsLastComparator();

  private static final Comparator NULLS_LAST_REVERSE_COMPARATOR = // 空值置后且反向排序的比较器
      new NullsLastReverseComparator();

  private static final Comparator NULLS_FIRST_REVERSE_COMPARATOR = // 空值优先且反向排序的比较器
      new NullsFirstReverseComparator();

  private static final EqualityComparer<Object> IDENTITY_COMPARER = // 对象标识比较器，使用Objects.equals进行相等性比较
      new IdentityEqualityComparer();

  private static final EqualityComparer<@Nullable Object[]> ARRAY_COMPARER = // 数组比较器，使用Arrays.deepEquals进行深度比较
      new ArrayEqualityComparer();

  private static final Function1 CONSTANT_NULL_FUNCTION1 = // 常量null函数，总是返回null的单参数函数
      (Function1<Object, @Nullable Object>) s -> null;

  private static final Function1 TO_STRING_FUNCTION1 = // 转字符串函数，调用对象的toString方法
      (Function1<Object, String>) Object::toString;

  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> map(K k, V v, Object... rest) { // 创建一个包含多个键值对的Map，参数格式为：key1, value1, key2, value2, ...
    final Map<K, V> map = new HashMap<>(); // 创建新的HashMap实例
    map.put(k, v); // 放入第一个键值对
    for (int i = 0; i < rest.length; i++) { // 遍历剩余参数
      map.put((K) rest[i++], (V) rest[i++]); // 每次读取两个参数作为键值对放入Map
    }
    return map; // 返回构建好的Map
  }

  private static <K, V> Map<V, K> inverse(Map<K, V> map) { // 反转Map的键值关系，将原Map的value作为新Map的key，原Map的key作为新Map的value
    HashMap<V, K> inverseMap = new HashMap<>(); // 创建新的HashMap用于存储反转后的映射
    for (Map.Entry<K, V> entry : map.entrySet()) { // 遍历原Map的所有条目
      inverseMap.put(entry.getValue(), entry.getKey()); // 将原value作为key，原key作为value放入新Map
    }
    return inverseMap; // 返回反转后的Map
  }

  /** Returns a 1-parameter function that always returns the same value. */
  public static <T, R> Function1<T, R> constant(final R r) { // 创建一个总是返回固定值的单参数函数
    return s -> r; // 返回一个lambda表达式，忽略输入参数，始终返回r
  }

  /** Returns a 1-parameter function that always returns null. */
  @SuppressWarnings("unchecked")
  public static <T, R> Function1<T, R> constantNull() { // 创建一个总是返回null的单参数函数
    return CONSTANT_NULL_FUNCTION1; // 返回预定义的常量null函数实例
  }

  /**
   * A predicate with one parameter that always returns {@code true}.
   *
   * @param <T> First parameter type
   *
   * @return Predicate that always returns true
   */
  public static <T> Predicate1<T> truePredicate1() { // 创建一个总是返回true的单参数谓词
    //noinspection unchecked
    return (Predicate1<T>) Predicate1.TRUE; // 返回Predicate1.TRUE常量，类型转换
  }

  /**
   * A predicate with one parameter that always returns {@code true}.
   *
   * @param <T> First parameter type
   *
   * @return Predicate that always returns true
   */
  public static <T> Predicate1<T> falsePredicate1() { // 创建一个总是返回false的单参数谓词
    //noinspection unchecked
    return (Predicate1<T>) Predicate1.FALSE; // 返回Predicate1.FALSE常量，类型转换
  }

  /**
   * A predicate with two parameters that always returns {@code true}.
   *
   * @param <T1> First parameter type
   * @param <T2> Second parameter type
   *
   * @return Predicate that always returns true
   */
  public static <T1, T2> Predicate2<T1, T2> truePredicate2() { // 创建一个总是返回true的双参数谓词
    //noinspection unchecked
    return (Predicate2<T1, T2>) Predicate2.TRUE; // 返回Predicate2.TRUE常量，类型转换
  }

  /**
   * A predicate with two parameters that always returns {@code false}.
   *
   * @param <T1> First parameter type
   * @param <T2> Second parameter type
   *
   * @return Predicate that always returns false
   */
  public static <T1, T2> Predicate2<T1, T2> falsePredicate2() { // 创建一个总是返回false的双参数谓词
    //noinspection unchecked
    return (Predicate2<T1, T2>) Predicate2.FALSE; // 返回Predicate2.FALSE常量，类型转换
  }

  public static <TSource> Function1<TSource, TSource> identitySelector() { // 创建一个恒等选择器，返回输入参数本身
    //noinspection unchecked
    return (Function1) Function1.IDENTITY; // 返回Function1.IDENTITY常量，类型转换
  }

  /** Returns a selector that calls the {@link Object#toString()} method on
   * each element. */
  public static <TSource> Function1<TSource, String> toStringSelector() { // 创建一个调用toString方法的选择器
    //noinspection unchecked
    return TO_STRING_FUNCTION1; // 返回预定义的toString函数实例
  }

  /**
   * Creates a predicate that returns whether an object is an instance of a
   * particular type or is null.
   *
   * @param clazz Desired type
   * @param <T> Type of objects to test
   * @param <T2> Desired type
   *
   * @return Predicate that tests for desired type
   */
  public static <T, T2> Predicate1<T> ofTypePredicate(final Class<T2> clazz) { // 创建一个类型判断谓词，判断对象是否为指定类型或null
    return v1 -> v1 == null || clazz.isInstance(v1); // 返回lambda表达式，检查v1是否为null或clazz的实例
  }

  public static <T1, T2> Predicate2<T1, T2> toPredicate2(
      final Predicate1<T1> p1) { // 将单参数谓词转换为双参数谓词，忽略第二个参数
    return (v1, v2) -> p1.apply(v1); // 返回lambda表达式，只对第一个参数应用p1谓词
  }

  /**
   * Converts a 2-parameter function to a predicate.
   */
  public static <T1, T2> Predicate2<T1, T2> toPredicate(
      final Function2<T1, T2, Boolean> function) { // 将返回Boolean的双参数函数转换为双参数谓词
    return function::apply; // 返回函数的方法引用，直接调用function的apply方法
  }

  /**
   * Returns the appropriate interface for a lambda function with
   * 1 argument and the given return type.
   *
   * <p>For example:
   * functionClass(Integer.TYPE) returns IntegerFunction1.class;
   * functionClass(String.class) returns Function1.class.
   *
   * @param aClass Return type
   *
   * @return Function class
   */
  public static Class<? extends Function> functionClass(Type aClass) { // 根据返回类型获取对应的函数接口类
    Class<? extends Function> c = FUNCTION1_CLASSES.get(aClass); // 从映射表中查找对应的函数接口
    if (c != null) { // 如果找到了对应的函数接口
      return c; // 返回找到的函数接口
    }
    return Function1.class; // 如果没找到，返回通用的Function1接口
  }

  /**
   * Adapts an {@link IntegerFunction1} (that returns an {@code int}) to
   * an {@link Function1} returning an {@link Integer}.
   */
  public static <T1> Function1<T1, Integer> adapt(
      final IntegerFunction1<T1> f) { // 将返回基本int类型的IntegerFunction1适配为返回包装Integer类型的Function1
    return f::apply; // 返回函数的方法引用，Java会自动进行int到Integer的装箱
  }

  /**
   * Adapts a {@link DoubleFunction1} (that returns a {@code double}) to
   * an {@link Function1} returning a {@link Double}.
   */
  public static <T1> Function1<T1, Double> adapt(final DoubleFunction1<T1> f) { // 将返回基本double类型的DoubleFunction1适配为返回包装Double类型的Function1
    return f::apply; // 返回函数的方法引用，Java会自动进行double到Double的装箱
  }

  /**
   * Adapts a {@link LongFunction1} (that returns a {@code long}) to
   * an {@link Function1} returning a {@link Long}.
   */
  public static <T1> Function1<T1, Long> adapt(final LongFunction1<T1> f) { // 将返回基本long类型的LongFunction1适配为返回包装Long类型的Function1
    return f::apply; // 返回函数的方法引用，Java会自动进行long到Long的装箱
  }

  /**
   * Adapts a {@link FloatFunction1} (that returns a {@code float}) to
   * an {@link Function1} returning a {@link Float}.
   */
  public static <T1> Function1<T1, Float> adapt(final FloatFunction1<T1> f) { // 将返回基本float类型的FloatFunction1适配为返回包装Float类型的Function1
    return f::apply; // 返回函数的方法引用，Java会自动进行float到Float的装箱
  }

  /**
   * Creates a view of a list that applies a function to each element.
   *
   * @deprecated Use {@link com.google.common.collect.Lists#transform}
   */
  @Deprecated // to be removed before 2.0
  public static <T1, R> List<R> adapt(final List<T1> list,
      final Function1<T1, R> f) { // 创建一个列表视图，对每个元素应用函数转换（懒加载）
    return new AbstractList<R>() { // 返回一个AbstractList的匿名子类
      @Override public R get(int index) { // 重写get方法
        return f.apply(list.get(index)); // 获取原始列表元素并应用函数转换
      }

      @Override public int size() { // 重写size方法
        return list.size(); // 返回原始列表的大小
      }
    };
  }

  /**
   * Creates a view of an array that applies a function to each element.
   *
   * @deprecated Use {@link com.google.common.collect.Lists#transform}
   * and {@link Arrays#asList(Object[])}
   */
  @Deprecated // to be removed before 2.0
  public static <T, R> List<R> adapt(final T[] ts,
      final Function1<T, R> f) { // 创建一个数组视图，对每个元素应用函数转换（懒加载）
    return new AbstractList<R>() { // 返回一个AbstractList的匿名子类
      @Override public R get(int index) { // 重写get方法
        return f.apply(ts[index]); // 获取数组元素并应用函数转换
      }

      @Override public int size() { // 重写size方法
        return ts.length; // 返回数组的长度
      }
    };
  }

  /**
   * Creates a copy of a list, applying a function to each element.
   */
  public static <T1, R> List<R> apply(final List<T1> list,
      final Function1<T1, R> f) { // 创建列表的副本，对每个元素应用函数转换（立即执行）
    final List<R> list2 = new ArrayList<>(list.size()); // 创建新的ArrayList，预分配容量
    for (T1 t : list) { // 遍历原始列表
      list2.add(f.apply(t)); // 对每个元素应用函数并添加到新列表
    }
    return list2; // 返回转换后的新列表
  }

  /** Returns a list that contains only elements of {@code list} that match
   * {@code predicate}. Avoids allocating a list if all elements match or no
   * elements match. */
  @SuppressWarnings("MixedMutabilityReturnType")
  public static <E> List<E> filter(List<E> list, Predicate1<E> predicate) { // 过滤列表，只保留满足谓词条件的元素，优化了全部匹配或全部不匹配的情况
  sniff: // 标签，用于跳出嵌套循环
    {
      int hitCount = 0; // 匹配计数器
      int missCount = 0; // 不匹配计数器
      for (E e : list) { // 遍历列表
        if (predicate.apply(e)) { // 如果元素满足谓词条件
          if (missCount > 0) { // 如果之前有不匹配的元素
            break sniff; // 跳出嗅探阶段，进入正常过滤
          }
          ++hitCount; // 增加匹配计数
        } else { // 如果元素不满足谓词条件
          if (hitCount > 0) { // 如果之前有匹配的元素
            break sniff; // 跳出嗅探阶段，进入正常过滤
          }
          ++missCount; // 增加不匹配计数
        }
      }
      if (hitCount == 0) { // 如果没有任何元素匹配
        return Collections.emptyList(); // 返回空列表
      }
      if (missCount == 0) { // 如果所有元素都匹配
        return list; // 直接返回原列表
      }
    }
    final List<E> list2 = new ArrayList<>(list.size()); // 创建新的ArrayList，预分配容量
    for (E e : list) { // 遍历原始列表
      if (predicate.apply(e)) { // 如果元素满足谓词条件
        list2.add(e); // 添加到新列表
      }
    }
    return list2; // 返回过滤后的列表
  }

  /** Returns whether there is an element in {@code list} for which
   * {@code predicate} is true. */
  public static <E> boolean exists(List<? extends E> list,
      Predicate1<E> predicate) { // 检查列表中是否存在满足谓词条件的元素
    for (E e : list) { // 遍历列表
      if (predicate.apply(e)) { // 如果元素满足谓词条件
        return true; // 立即返回true，找到就停止
      }
    }
    return false; // 遍历完都没找到，返回false
  }

  /** Returns whether {@code predicate} is true for all elements of
   * {@code list}. */
  public static <E> boolean all(List<? extends E> list,
      Predicate1<E> predicate) { // 检查列表中所有元素是否都满足谓词条件
    for (E e : list) { // 遍历列表
      if (!predicate.apply(e)) { // 如果元素不满足谓词条件
        return false; // 立即返回false，发现不满足就停止
      }
    }
    return true; // 所有元素都满足，返回true
  }

  /** Returns a list generated by applying a function to each index between
   * 0 and {@code size} - 1. */
  public static <E> List<E> generate(final int size,
      final IntFunction<E> fn) { // 生成一个列表，通过函数根据索引生成元素
    if (size < 0) { // 如果size为负数
      throw new IllegalArgumentException(); // 抛出非法参数异常
    }
    return new GeneratingList<>(size, fn); // 返回一个GeneratingList，懒加载生成元素
  }

  /**
   * Returns a function of arity 0 that does nothing.
   *
   * @param <R> Return type
   * @return Function that does nothing.
   */
  public static <R> Function0<R> ignore0() { // 返回一个无参数的空函数，总是返回null
    //noinspection unchecked
    return Ignore.INSTANCE; // 返回Ignore单例实例，类型转换
  }

  /**
   * Returns a function of arity 1 that does nothing.
   *
   * @param <R> Return type
   * @param <T0> Type of parameter 0
   * @return Function that does nothing.
   */
  public static <R, T0> Function1<R, T0> ignore1() { // 返回一个单参数的空函数，总是返回null
    //noinspection unchecked
    return Ignore.INSTANCE; // 返回Ignore单例实例，类型转换
  }

  /**
   * Returns a function of arity 2 that does nothing.
   *
   * @param <R> Return type
   * @param <T0> Type of parameter 0
   * @param <T1> Type of parameter 1
   * @return Function that does nothing.
   */
  public static <R, T0, T1> Function2<R, T0, T1> ignore2() { // 返回一个双参数的空函数，总是返回null
    //noinspection unchecked
    return Ignore.INSTANCE; // 返回Ignore单例实例，类型转换
  }

  /**
   * Returns a {@link Comparator} that handles null values.
   *
   * @param nullsFirst Whether nulls come before all other values
   * @param reverse Whether to reverse the usual order of {@link Comparable}s
   */
  @SuppressWarnings("unchecked")
  public static <T extends Comparable<T>> Comparator<T> nullsComparator(
      boolean nullsFirst,
      boolean reverse) { // 返回一个处理null值的比较器，支持指定null值位置和排序方向
    return (Comparator<T>) // 返回比较器，类型转换
        (reverse // 如果需要反向排序
        ? (nullsFirst // 如果null值在前
          ? NULLS_FIRST_REVERSE_COMPARATOR // 返回null在前且反向的比较器
          : NULLS_LAST_REVERSE_COMPARATOR) // 返回null在后且反向的比较器
        : (nullsFirst // 如果不需要反向排序
          ? NULLS_FIRST_COMPARATOR // 返回null在前且正向的比较器
          : NULLS_LAST_COMPARATOR)); // 返回null在后且正向的比较器
  }

  /**
   * Returns a {@link Comparator} that handles null values.
   *
   * @param nullsFirst Whether nulls come before all other values
   * @param reverse Whether to reverse the usual order of {@link Comparable}s
   * @param comparator Comparator to be used for comparison
   */
  @SuppressWarnings("unchecked")
  public static <T extends Comparable<T>> Comparator<T> nullsComparator(
      boolean nullsFirst,
      boolean reverse,
      Comparator<T> comparator) { // 返回一个处理null值的比较器，使用指定的比较器进行非null值的比较
    return (T o1, T o2) -> { // 返回lambda表达式作为比较器
      if (o1 == o2) { // 如果两个对象相同（包括都是null）
        return 0; // 返回相等
      }
      if (o1 == null) { // 如果o1为null
        return nullsFirst ? -1 : 1; // null在前返回-1，否则返回1
      }
      if (o2 == null) { // 如果o2为null
        return nullsFirst ? 1 : -1; // null在前返回1，否则返回-1
      }
      return reverse ? -comparator.compare(o1, o2) : comparator.compare(o1, o2); // 使用指定的比较器比较，根据reverse决定是否反转结果
    };
  }

  /**
   * Returns an {@link EqualityComparer} that uses object identity and hash
   * code.
   */
  @SuppressWarnings("unchecked")
  public static <T> EqualityComparer<T> identityComparer() { // 返回一个使用对象标识和哈希码的相等比较器
    return (EqualityComparer) IDENTITY_COMPARER; // 返回IDENTITY_COMPARER单例，类型转换
  }

  /**
   * Returns an {@link EqualityComparer} that works on arrays of objects.
   */
  @SuppressWarnings("unchecked")
  public static <T> EqualityComparer<T[]> arrayComparer() { // 返回一个用于对象数组的相等比较器，使用深度比较
    return (EqualityComparer) ARRAY_COMPARER; // 返回ARRAY_COMPARER单例，类型转换
  }

  /**
   * Returns an {@link EqualityComparer} that uses a selector function.
   */
  public static <T, T2> EqualityComparer<T> selectorComparer(
      Function1<T, T2> selector) { // 返回一个使用选择器函数的相等比较器，通过选择器提取值进行比较
    return new SelectorEqualityComparer<>(selector); // 创建并返回SelectorEqualityComparer实例
  }

  /** Array equality comparer. */
  private static class ArrayEqualityComparer // 数组相等比较器内部类，使用深度比较
      implements EqualityComparer<@Nullable Object[]> {
    @Override public boolean equal(@Nullable Object[] v1, @Nullable Object[] v2) { // 比较两个对象数组是否相等
      return Arrays.deepEquals(v1, v2); // 使用Arrays.deepEquals进行深度比较
    }

    @Override public int hashCode(@Nullable Object[] t) { // 计算对象数组的哈希码
      return Arrays.deepHashCode(t); // 使用Arrays.deepHashCode计算深度哈希码
    }
  }

  /** Identity equality comparer. */
  private static class IdentityEqualityComparer // 对象标识相等比较器内部类
      implements EqualityComparer<Object> {
    @Override public boolean equal(Object v1, Object v2) { // 比较两个对象是否相等
      return Objects.equals(v1, v2); // 使用Objects.equals进行比较
    }

    @Override public int hashCode(Object t) { // 计算对象的哈希码
      return t == null ? 0x789d : t.hashCode(); // 如果为null返回固定值0x789d，否则返回对象的hashCode
    }
  }

  /** Selector equality comparer.
   *
   * @param <T> element type
   * @param <T2> target type */
  private static final class SelectorEqualityComparer<T, T2> // 选择器相等比较器内部类，通过选择器函数提取值进行比较
      implements EqualityComparer<T> {
    private final Function1<T, T2> selector; // 选择器函数，用于从对象中提取比较值

    SelectorEqualityComparer(Function1<T, T2> selector) { // 构造方法
      this.selector = selector; // 保存选择器函数
    }

    @Override public boolean equal(T v1, T v2) { // 比较两个对象是否相等
      return v1 == v2 // 如果是同一个对象引用
          || v1 != null // 且v1不为null
          && v2 != null // 且v2不为null
          && Objects.equals(selector.apply(v1), selector.apply(v2)); // 且选择器提取的值相等
    }

    @Override public int hashCode(T t) { // 计算对象的哈希码
      return t == null ? 0x789d : Objects.hashCode(selector.apply(t)); // 如果为null返回固定值，否则返回选择器提取值的哈希码
    }
  }

  /** Nulls first comparator. */
  private static class NullsFirstComparator // 空值优先比较器内部类，将null值排在最前面
      implements Comparator<Comparable>, Serializable {
    @Override public int compare(Comparable o1, Comparable o2) { // 比较两个可比较对象
      if (o1 == o2) { // 如果是同一个对象（包括都是null）
        return 0; // 返回相等
      }
      if (o1 == null) { // 如果o1为null
        return -1; // o1排在前面
      }
      if (o2 == null) { // 如果o2为null
        return 1; // o2排在前面
      }
      //noinspection unchecked
      return o1.compareTo(o2); // 使用Comparable的compareTo方法比较
    }
  }

  /** Nulls last comparator. */
  private static class NullsLastComparator // 空值置后比较器内部类，将null值排在最后面
      implements Comparator<Object>, Serializable {
    @Override public int compare(@Nullable Object o1, @Nullable Object o2) { // 比较两个对象
      if (o1 == o2) { // 如果是同一个对象（包括都是null）
        return 0; // 返回相等
      }
      if (o1 == null) { // 如果o1为null
        return 1; // o1排在后面
      }
      if (o2 == null) { // 如果o2为null
        return -1; // o2排在后面
      }
      if (o1 instanceof Comparable && o2 instanceof Comparable) { // 如果两个对象都是可比较的
        //noinspection unchecked
        return ((Comparable) o1).compareTo(o2); // 使用Comparable的compareTo方法比较
      } else if (o1 instanceof List && o2 instanceof List) { // 如果两个对象都是List
        return compareLists((List<?>) o1, (List<?>) o2); // 使用compareLists方法比较列表
      } else if (o1 instanceof Object[] && o2 instanceof Object[]) { // 如果两个对象都是数组
        final List<Object> list1 = Lists.newArrayList((Object[]) o1); // 将数组转换为List
        final List<Object> list2 = Lists.newArrayList((Object[]) o2); // 将数组转换为List
        return compareLists(list1, list2); // 使用compareLists方法比较列表
      } else { // 其他情况
        throw new IllegalArgumentException(); // 抛出非法参数异常
      }
    }
  }

  /** Nulls first reverse comparator. */
  private static class NullsFirstReverseComparator // 空值优先且反向排序的比较器内部类
      implements Comparator<Object>, Serializable  {
    @Override public int compare(Object o1, Object o2) { // 比较两个对象
      if (o1 == o2) { // 如果是同一个对象（包括都是null）
        return 0; // 返回相等
      }
      if (o1 == null) { // 如果o1为null
        return -1; // o1排在前面
      }
      if (o2 == null) { // 如果o2为null
        return 1; // o2排在前面
      }
      if (o1 instanceof Comparable && o2 instanceof Comparable) { // 如果两个对象都是可比较的
        //noinspection unchecked
        return -((Comparable) o1).compareTo(o2); // 使用Comparable的compareTo方法比较，结果取反
      } else if (o1 instanceof List && o2 instanceof List) { // 如果两个对象都是List
        return -compareLists((List<?>) o1, (List<?>) o2); // 使用compareLists方法比较，结果取反
      } else if (o1 instanceof Object[] && o2 instanceof Object[]) { // 如果两个对象都是数组
        final List<Object> list1 = Lists.newArrayList((Object[]) o1); // 将数组转换为List
        final List<Object> list2 = Lists.newArrayList((Object[]) o2); // 将数组转换为List
        return -compareLists(list1, list2); // 使用compareLists方法比较，结果取反
      } else { // 其他情况
        throw new IllegalArgumentException(); // 抛出非法参数异常
      }
    }
  }

  public static int compareLists(List<?> b0, List<?> b1) { // 比较两个列表，支持嵌套列表和数组的递归比较
    if (b0.isEmpty() && b1.isEmpty()) { // 如果两个列表都为空
      return 0; // 返回相等
    }
    for (int i = 0; i < b0.size() && i < b1.size(); i++) { // 遍历两个列表的公共长度部分
      final int comparison = compareListItems(b0.get(i), b1.get(i)); // 比较对应位置的元素
      if (comparison != 0) { // 如果元素不相等
        return comparison; // 返回比较结果
      }
    }
    return Integer.compare(b0.size(), b1.size()); // 所有对应元素都相等，比较列表长度
  }

  private static int compareListItems(@Nullable Object item0, @Nullable Object item1) { // 比较列表中的两个元素，支持嵌套列表和数组的递归比较
    if (item0 == null && item1 == null) { // 如果两个元素都为null
      return 0; // 返回相等
    } else if (item0 == null) { // 如果item0为null
      return 1; // item0排在后面
    } else if (item1 == null) { // 如果item1为null
      return -1; // item1排在后面
    }
    if (item0 instanceof List && item1 instanceof List) { // 如果两个元素都是List
      final List<?> b0ItemList = (List<?>) item0; // 强制转换为List
      final List<?> b1ItemList = (List<?>) item1; // 强制转换为List
      return compareLists(b0ItemList, b1ItemList); // 递归调用compareLists比较
    } else if (item0 instanceof Object[] && item1 instanceof Object[]) { // 如果两个元素都是数组
      return compareObjectArrays((Object[]) item0, (Object[]) item1); // 调用compareObjectArrays比较
    } else if (item0.getClass().equals(item1.getClass()) && item0 instanceof Comparable<?>) { // 如果两个元素类型相同且可比较
      final Comparable b0Comparable = (Comparable) item0; // 强制转换为Comparable
      final Comparable b1Comparable = (Comparable) item1; // 强制转换为Comparable
      return b0Comparable.compareTo(b1Comparable); // 使用compareTo比较
    } else { // 其他情况
      throw new IllegalArgumentException("Item types do not match"); // 抛出类型不匹配异常
    }
  }

  public static int compareObjectArrays(Object[] b0, Object[] b1) { // 比较两个对象数组，将数组转换为List后比较
    final List<Object> b0List = Lists.newArrayList(b0); // 将第一个数组转换为List
    final List<Object> b1List = Lists.newArrayList(b1); // 将第二个数组转换为List
    return compareLists(b0List, b1List); // 调用compareLists比较
  }

  /** Nulls last reverse comparator. */
  private static class NullsLastReverseComparator // 空值置后且反向排序的比较器内部类
      implements Comparator<Comparable>, Serializable  {
    @Override public int compare(Comparable o1, Comparable o2) { // 比较两个可比较对象
      if (o1 == o2) { // 如果是同一个对象（包括都是null）
        return 0; // 返回相等
      }
      if (o1 == null) { // 如果o1为null
        return 1; // o1排在后面
      }
      if (o2 == null) { // 如果o2为null
        return -1; // o2排在后面
      }
      //noinspection unchecked
      return -o1.compareTo(o2); // 使用Comparable的compareTo方法比较，结果取反
    }
  }

  /** Ignore.
   *
   * @param <R> result type
   * @param <T0> first argument type
   * @param <T1> second argument type */
  private static final class Ignore<@Nullable R, T0, T1> // 空函数内部类，实现0、1、2参数的函数接口，总是返回null
      implements Function0<R>, Function1<T0, R>, Function2<T0, T1, R> {
    @Override public R apply() { // 无参数函数实现
      return null; // 返回null
    }

    @Override public R apply(T0 p0) { // 单参数函数实现
      return null; // 返回null
    }

    @Override public R apply(T0 p0, T1 p1) { // 双参数函数实现
      return null; // 返回null
    }

    @DefaultQualifier( // 默认类型限定符注解
        value = Nullable.class, // 指定可空类型
        locations = { // 应用位置
        TypeUseLocation.LOWER_BOUND, // 下界
        TypeUseLocation.UPPER_BOUND, // 上界
    })
    static final Ignore INSTANCE = new Ignore<>(); // 单例实例
  }

  /** List that generates each element using a function.
   *
   * @param <E> element type */
  private static class GeneratingList<E> extends AbstractList<E> // 生成列表内部类，通过函数根据索引懒加载生成元素
      implements RandomAccess {
    private final int size; // 列表大小
    private final IntFunction<E> fn; // 元素生成函数

    GeneratingList(int size, IntFunction<E>  fn) { // 构造方法
      this.size = size; // 保存列表大小
      this.fn = fn; // 保存生成函数
    }

    @Override public int size() { // 返回列表大小
      return size; // 返回保存的大小
    }

    @Override public E get(int index) { // 获取指定索引的元素
      return fn.apply(index); // 使用生成函数根据索引生成元素
    }
  }
}
