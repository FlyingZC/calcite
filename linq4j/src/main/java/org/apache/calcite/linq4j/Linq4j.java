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
package org.apache.calcite.linq4j; // 声明包名，该类属于org.apache.calcite.linq4j包

import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数接口，用于类型转换

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解

import java.lang.reflect.Method; // 导入Method类，用于反射获取方法
import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.Arrays; // 导入Arrays数组工具类
import java.util.Collection; // 导入Collection集合接口
import java.util.Iterator; // 导入Iterator迭代器接口
import java.util.List; // 导入List列表接口
import java.util.NoSuchElementException; // 导入NoSuchElementException异常类
import java.util.Objects; // 导入Objects工具类，用于对象操作
import java.util.RandomAccess; // 导入RandomAccess标记接口，用于支持随机访问

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于空值检查

/**
 * Utility and factory methods for Linq4j. // Linq4j的工具类和工厂方法，提供各种静态方法用于创建和操作可枚举对象
 */
public abstract class Linq4j { // Linq4j工具类，抽象类，不允许实例化，只提供静态方法
  private Linq4j() {} // 私有构造方法，防止实例化

  private static final Object DUMMY = new Object(); // 虚拟对象常量，用于标记Enumerator的初始状态，表示还没有调用moveNext()

  public static @Nullable Method getMethod(String className, String methodName, // 公共静态方法：通过反射获取指定类的方法，返回值可为null
      Class... parameterTypes) { // 参数：className-类全限定名，methodName-方法名，parameterTypes-参数类型数组
    try { // 尝试执行
      return Class.forName(className).getMethod(methodName, parameterTypes); // 通过类名获取Class对象，然后获取指定方法
    } catch (NoSuchMethodException | ClassNotFoundException e) { // 捕获方法不存在或类不存在异常
      return null; // 方法不存在时返回null
    }
  }

  /**
   * Query provider that simply executes a {@link Queryable} by calling its // 默认查询提供者：简单地通过调用Queryable的enumerator方法来执行查询，不进行优化
   * enumerator method; does not attempt optimization. // 不尝试任何优化，直接调用枚举器方法
   */
  public static final QueryProvider DEFAULT_PROVIDER = new QueryProviderImpl() { // 默认查询提供者常量，继承QueryProviderImpl并重写executeQuery方法
    @Override public <T> Enumerator<T> executeQuery(Queryable<T> queryable) { // 重写executeQuery方法，执行查询并返回枚举器
      return queryable.enumerator(); // 直接调用Queryable的enumerator方法返回枚举器，不进行优化
    }
  };

  private static final Enumerator<Object> EMPTY_ENUMERATOR = // 空枚举器常量，用于表示没有任何元素的枚举器
      new Enumerator<Object>() { // 创建匿名内部类实现Enumerator接口
        @Override public Object current() { // 重写current方法，获取当前元素
          throw new NoSuchElementException(); // 抛出无此元素异常，因为空枚举器没有元素
        }

        @Override public boolean moveNext() { // 重写moveNext方法，移动到下一个元素
          return false; // 返回false，表示没有下一个元素
        }

        @Override public void reset() { // 重写reset方法，重置枚举器到初始状态
        } // 空实现，因为空枚举器不需要重置

        @Override public void close() { // 重写close方法，关闭枚举器释放资源
        } // 空实现，因为空枚举器没有资源需要释放
      };

  public static final Enumerable<?> EMPTY_ENUMERABLE = // 空可枚举对象常量，用于表示没有任何元素的可枚举对象
      new AbstractEnumerable<Object>() { // 创建匿名内部类继承AbstractEnumerable
        @Override public Enumerator<Object> enumerator() { // 重写enumerator方法，返回枚举器
          return EMPTY_ENUMERATOR; // 返回空枚举器
        }
      };

  /**
   * Adapter that converts an enumerator into an iterator. // 适配器方法：将枚举器转换为迭代器
   *
   * <p><b>WARNING</b>: The iterator returned by this method does not call // 警告：此方法返回的迭代器不会调用
   * {@link org.apache.calcite.linq4j.Enumerator#close()}, so it is not safe to // Enumerator的close()方法，因此对于分配了资源的枚举器使用是不安全的
   * use with an enumerator that allocates resources. // 如果枚举器分配了资源，使用此方法可能导致资源泄漏
   *
   * @param enumerator Enumerator // 参数：要转换的枚举器
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return Iterator // 返回值：转换后的迭代器
   */
  public static <T> Iterator<T> enumeratorIterator(Enumerator<T> enumerator) { // 公共静态方法：将枚举器转换为迭代器
    return new EnumeratorIterator<>(enumerator); // 创建并返回EnumeratorIterator对象，包装传入的枚举器
  }

  /**
   * Adapter that converts an iterable into an enumerator. // 适配器方法：将可迭代对象转换为枚举器
   *
   * @param iterable Iterable // 参数：要转换的可迭代对象
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return enumerator // 返回值：转换后的枚举器
   */
  public static <T> Enumerator<T> iterableEnumerator( // 公共静态方法：将可迭代对象转换为枚举器
      final Iterable<? extends T> iterable) { // 参数：可迭代对象，使用final修饰确保不可更改
    if (iterable instanceof Enumerable) { // 如果可迭代对象本身就是Enumerable实例
      @SuppressWarnings("unchecked") final Enumerable<T> enumerable = // 抑制未检查的类型转换警告
          (Enumerable) iterable; // 将iterable强转为Enumerable类型
      return enumerable.enumerator(); // 直接调用Enumerable的enumerator方法返回枚举器
    }
    return new IterableEnumerator<>(iterable); // 否则创建IterableEnumerator对象包装可迭代对象
  }

  /**
   * Adapter that converts an {@link List} into an {@link Enumerable}. // 适配器方法：将列表转换为可枚举对象
   *
   * @param list List // 参数：要转换的列表
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return enumerable // 返回值：转换后的可枚举对象
   */
  public static <T> Enumerable<T> asEnumerable(final List<T> list) { // 公共静态方法：将列表转换为可枚举对象
    return new ListEnumerable<>(list); // 创建并返回ListEnumerable对象，包装传入的列表
  }

  /**
   * Adapter that converts an {@link Collection} into an {@link Enumerable}. // 适配器方法：将集合转换为可枚举对象
   *
   * <p>It uses more efficient implementations if the iterable happens to // 如果可迭代对象恰好是List，则使用更高效的实现
   * be a {@link List}. // 因为List支持随机访问，可以有更优化的枚举器实现
   *
   * @param collection Collection // 参数：要转换的集合
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return enumerable // 返回值：转换后的可枚举对象
   */
  public static <T> Enumerable<T> asEnumerable(final Collection<T> collection) { // 公共静态方法：将集合转换为可枚举对象
    if (collection instanceof List) { // 如果集合是List的实例
      //noinspection unchecked // 抑制未检查的类型转换警告
      return asEnumerable((List) collection); // 调用asEnumerable的List版本，使用更高效的ListEnumerable
    }
    return new CollectionEnumerable<>(collection); // 否则创建CollectionEnumerable对象包装集合
  }

  /**
   * Adapter that converts an {@link Iterable} into an {@link Enumerable}. // 适配器方法：将可迭代对象转换为可枚举对象
   *
   * <p>It uses more efficient implementations if the iterable happens to // 如果可迭代对象恰好是Collection或List，则使用更高效的实现
   * be a {@link Collection} or a {@link List}. // 因为Collection和List有特定的优化实现
   *
   * @param iterable Iterable // 参数：要转换的可迭代对象
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return enumerable // 返回值：转换后的可枚举对象
   */
  public static <T> Enumerable<T> asEnumerable(final Iterable<T> iterable) { // 公共静态方法：将可迭代对象转换为可枚举对象
    if (iterable instanceof Collection) { // 如果可迭代对象是Collection的实例
      //noinspection unchecked // 抑制未检查的类型转换警告
      return asEnumerable((Collection) iterable); // 调用asEnumerable的Collection版本，可能使用更高效的实现
    }
    return new IterableEnumerable<>(iterable); // 否则创建IterableEnumerable对象包装可迭代对象
  }

  /**
   * Adapter that converts an array into an enumerable. // 适配器方法：将数组转换为可枚举对象
   *
   * @param ts Array // 参数：要转换的数组
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return enumerable // 返回值：转换后的可枚举对象
   */
  public static <T> Enumerable<T> asEnumerable(final T[] ts) { // 公共静态方法：将数组转换为可枚举对象
    return new ListEnumerable<>(Arrays.asList(ts)); // 将数组转换为List，然后创建ListEnumerable对象
  }

  /**
   * Adapter that converts a collection into an enumerator. // 适配器方法：将集合转换为枚举器
   *
   * @param values Collection // 参数：要转换的集合
   * @param <V> Element type // 泛型参数：元素类型
   *
   * @return Enumerator over the collection // 返回值：遍历集合的枚举器
   */
  public static <V> Enumerator<V> enumerator(Collection<? extends V> values) { // 公共静态方法：将集合转换为枚举器
    if (values instanceof List && values instanceof RandomAccess) { // 如果集合是List并且支持随机访问
      //noinspection unchecked // 抑制未检查的类型转换警告
      return listEnumerator((List) values); // 使用优化的ListEnumerator，支持随机访问
    }
    return iterableEnumerator(values); // 否则使用通用的iterableEnumerator方法
  }

  private static <V> Enumerator<V> listEnumerator(List<? extends V> list) { // 私有静态方法：创建列表枚举器
    return new ListEnumerator<>(list); // 创建并返回ListEnumerator对象，优化列表的枚举
  }

  /** Applies a function to each element of an Enumerator. // 对枚举器的每个元素应用转换函数
   *
   * @param enumerator Backing enumerator // 参数：底层枚举器
   * @param func Transform function // 参数：转换函数，将F类型转换为E类型
   * @param <F> Backing element type // 泛型参数：底层元素类型
   * @param <E> Element type // 泛型参数：转换后的元素类型
   * @return Enumerator // 返回值：转换后的枚举器
   */
  public static <F, E> Enumerator<E> transform(Enumerator<? extends F> enumerator, // 公共静态方法：转换枚举器的元素类型
      final Function1<? super F, ? extends E> func) { // 参数：转换函数，使用final修饰确保不可更改
    return new TransformedEnumerator<F, E>(enumerator) { // 创建TransformedEnumerator匿名子类，包装底层枚举器
      @Override protected E transform(F from) { // 重写transform方法，定义转换逻辑
        return func.apply(from); // 应用转换函数，将F类型元素转换为E类型
      }
    };
  }

  /**
   * Converts the elements of a given Iterable to the specified type. // 将给定可迭代对象的元素转换为指定类型
   *
   * <p>This method is implemented by using deferred execution. The immediate // 此方法使用延迟执行实现。立即返回值是一个存储执行所需所有信息的对象
   * return value is an object that stores all the information that is // 此方法表示的查询不会立即执行，直到通过直接调用其
   * required to perform the action. The query represented by this method is // {@link Enumerable#enumerator}方法或使用
   * not executed until the object is enumerated either by calling its // {@code for (... in ...)}来枚举对象时才执行
   * {@link Enumerable#enumerator} method directly or by using
   * {@code for (... in ...)}.
   *
   * <p>Since standard Java {@link Collection} objects implement the // 由于标准Java {@link Collection}对象实现了
   * {@link Iterable} interface, the {@code cast} method enables the standard // {@link Iterable}接口，{@code cast}方法使得可以在集合上调用标准查询操作符
   * query operators to be invoked on collections // （包括 {@link java.util.List} 和 {@link java.util.Set}），只需提供必要的类型信息
   * (including {@link java.util.List} and {@link java.util.Set}) by supplying
   * the necessary type information. For example, {@link ArrayList} does not // 例如，{@link ArrayList} 不实现 {@link Enumerable}&lt;F&gt;，但你可以调用
   * implement {@link Enumerable}&lt;F&gt;, but you can invoke
   *
   * <blockquote><code>Linq4j.cast(list, Integer.class)</code></blockquote> // <blockquote><code>Linq4j.cast(list, Integer.class)</code></blockquote>
   *
   * <p>to convert the list of an enumerable that can be queried using the // 来将列表转换为可以使用标准查询操作符查询的可枚举对象
   * standard query operators.
   *
   * <p>If an element cannot be cast to type &lt;TResult&gt;, this method will // 如果元素无法转换为类型&lt;TResult&gt;，此方法将抛出 {@link ClassCastException}
   * throw a {@link ClassCastException}. To obtain only those elements that // 要仅获取可以转换为类型TResult的元素，请改用 {@link #ofType} 方法
   * can be cast to type TResult, use the {@link #ofType} method instead.
   *
   * @see Enumerable#cast(Class) // 参见：Enumerable的cast方法
   * @see #ofType // 参见：ofType方法
   * @see #asEnumerable(Iterable) // 参见：asEnumerable方法
   */
  public static <TSource, TResult> Enumerable<TResult> cast( // 公共静态方法：将可迭代对象的元素转换为指定类型
      Iterable<TSource> source, Class<TResult> clazz) { // 参数：source-源可迭代对象，clazz-目标类型Class对象
    return asEnumerable(source).cast(clazz); // 先将source转换为Enumerable，然后调用其cast方法进行类型转换
  }

  /**
   * Returns elements of a given {@link Iterable} that are of the specified // 返回给定 {@link Iterable} 中属于指定类型的元素
   * type.
   *
   * <p>This method is implemented by using deferred execution. The immediate // 此方法使用延迟执行实现。立即返回值是一个存储执行所需所有信息的对象
   * return value is an object that stores all the information that is // 此方法表示的查询不会立即执行，直到通过直接调用其
   * required to perform the action. The query represented by this method is // {@link Enumerable#enumerator}方法或使用
   * not executed until the object is enumerated either by calling its // {@code for (... in ...)}来枚举对象时才执行
   * {@link Enumerable#enumerator} method directly or by using
   * {@code for (... in ...)}.
   *
   * <p>The {@code ofType} method returns only those elements in source that // {@code ofType} 方法仅返回源中可以转换为类型TResult的元素
   * can be cast to type TResult. To instead receive an exception if an // 如果元素无法转换为类型TResult时希望收到异常，请改用
   * element cannot be cast to type TResult, use // {@link #cast(Iterable, Class)} 方法
   * {@link #cast(Iterable, Class)}.
   *
   * <p>Since standard Java {@link Collection} objects implement the // 由于标准Java {@link Collection}对象实现了 {@link Iterable}接口，{@code cast}方法使得可以在集合上调用标准查询操作符
   * {@link Iterable} interface, the {@code cast} method enables the standard // （包括 {@link java.util.List} 和 {@link java.util.Set}），只需提供必要的类型信息
   * query operators to be invoked on collections // 例如，{@link ArrayList} 不实现 {@link Enumerable}&lt;F&gt;，但你可以调用
   * (including {@link java.util.List} and {@link java.util.Set}) by supplying
   * the necessary type information. For example, {@link ArrayList} does not
   * implement {@link Enumerable}&lt;F&gt;, but you can invoke
   *
   * <blockquote><code>Linq4j.ofType(list, Integer.class)</code></blockquote> // <blockquote><code>Linq4j.ofType(list, Integer.class)</code></blockquote>
   *
   * <p>to convert the list of an enumerable that can be queried using the // 来将列表转换为可以使用标准查询操作符查询的可枚举对象
   * standard query operators.
   *
   * @see Enumerable#cast(Class) // 参见：Enumerable的cast方法
   * @see #cast // 参见：cast方法
   */
  public static <TSource, TResult> Enumerable<TResult> ofType( // 公共静态方法：返回可迭代对象中属于指定类型的元素
      Iterable<TSource> source, Class<TResult> clazz) { // 参数：source-源可迭代对象，clazz-目标类型Class对象
    return asEnumerable(source).ofType(clazz); // 先将source转换为Enumerable，然后调用其ofType方法进行类型过滤
  }

  /**
   * Returns an {@link Enumerable} that has one element. // 返回只有一个元素的可枚举对象
   *
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return Singleton enumerable // 返回值：单元素可枚举对象
   */
  public static <T> Enumerable<T> singletonEnumerable(final T element) { // 公共静态方法：创建包含单个元素的可枚举对象
    return new AbstractEnumerable<T>() { // 创建匿名AbstractEnumerable子类
      @Override public Enumerator<T> enumerator() { // 重写enumerator方法
        return singletonEnumerator(element); // 返回单元素枚举器
      }
    };
  }

  /**
   * Returns an {@link Enumerator} that has one element. // 返回只有一个元素的枚举器
   *
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return Singleton enumerator // 返回值：单元素枚举器
   */
  public static <T> Enumerator<T> singletonEnumerator(T element) { // 公共静态方法：创建包含单个元素的枚举器
    return new SingletonEnumerator<>(element); // 创建并返回SingletonEnumerator对象
  }

  /**
   * Returns an {@link Enumerator} that has one null element. // 返回只有一个null元素的枚举器
   *
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return Singleton enumerator // 返回值：单元素枚举器（元素为null）
   */
  public static <T> Enumerator<T> singletonNullEnumerator() { // 公共静态方法：创建包含单个null元素的枚举器
    return new SingletonNullEnumerator<>(); // 创建并返回SingletonNullEnumerator对象
  }

  /**
   * Returns an {@link Enumerable} that has no elements. // 返回空的可枚举对象（没有任何元素）
   *
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return Empty enumerable // 返回值：空可枚举对象
   */
  public static <T> Enumerable<T> emptyEnumerable() { // 公共静态方法：创建空的可枚举对象
    //noinspection unchecked // 抑制未检查的类型转换警告
    return (Enumerable<T>) EMPTY_ENUMERABLE; // 强制类型转换并返回空可枚举对象常量
  }

  /**
   * Returns an {@link Enumerator} that has no elements. // 返回空的枚举器（没有任何元素）
   *
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return Empty enumerator // 返回值：空枚举器
   */
  public static <T> Enumerator<T> emptyEnumerator() { // 公共静态方法：创建空的枚举器
    //noinspection unchecked // 抑制未检查的类型转换警告
    return (Enumerator<T>) EMPTY_ENUMERATOR; // 强制类型转换并返回空枚举器常量
  }

  /**
   * Concatenates two or more {@link Enumerable}s to form a composite // 连接两个或多个 {@link Enumerable} 对象，形成一个包含它们所有元素的复合可枚举对象
   * enumerable that contains the union of their elements. // 结果包含所有输入可枚举对象的元素
   *
   * @param enumerableList List of enumerable objects // 参数：可枚举对象列表
   * @param <E> Element type // 泛型参数：元素类型
   *
   * @return Composite enumerator // 返回值：复合枚举器
   */
  public static <E> Enumerable<E> concat( // 公共静态方法：连接多个可枚举对象
      final List<Enumerable<E>> enumerableList) { // 参数：可枚举对象列表
    return new CompositeEnumerable<>(enumerableList); // 创建并返回CompositeEnumerable对象
  }

  /**
   * Returns an enumerator that is the cartesian product of the given // 返回给定枚举器的笛卡尔积枚举器
   * enumerators.
   *
   * <p>For example, given enumerator A that returns {"a", "b", "c"} and // 例如，给定枚举器A返回{"a", "b", "c"}，枚举器B返回{"x", "y"}
   * enumerator B that returns {"x", "y"}, product(List(A, B)) will return // product(List(A, B)) 将返回
   * {List("a", "x"), List("a", "y"), // {List("a", "x"), List("a", "y"),
   * List("b", "x"), List("b", "y"), // List("b", "x"), List("b", "y"),
   * List("c", "x"), List("c", "y")}. // List("c", "x"), List("c", "y")}
   *
   * <p>Notice that the cardinality of the result is the product of the // 注意结果的基数是输入基数的乘积。枚举器A和B分别有3和2个元素
   * cardinality of the inputs. The enumerators A and B have 3 and 2 // 结果有3 * 2 = 6个元素。情况总是如此。特别是，
   * elements respectively, and the result has 3 * 2 = 6 elements. // 如果任何一个枚举器为空，结果也为空
   * This is always the case. In
   * particular, if any of the enumerators is empty, the result is empty.
   *
   * @param enumerators List of enumerators // 参数：枚举器列表
   * @param <T> Element type // 泛型参数：元素类型
   *
   * @return Enumerator over the cartesian product // 返回值：笛卡尔积的枚举器
   */
  public static <T> Enumerator<List<T>> product( // 公共静态方法：计算枚举器的笛卡尔积
      List<Enumerator<T>> enumerators) { // 参数：枚举器列表
    return new CartesianProductListEnumerator<>(enumerators); // 创建并返回CartesianProductListEnumerator对象
  }

  /** Returns the cartesian product of an iterable of iterables. */ // 返回可迭代对象的笛卡尔积
  public static <T> Iterable<List<T>> product( // 公共静态方法：计算可迭代对象的笛卡尔积
      final Iterable<? extends Iterable<T>> iterables) { // 参数：可迭代对象的可迭代对象
    return () -> { // 返回一个匿名Iterable对象
      final List<Enumerator<T>> enumerators = new ArrayList<>(); // 创建枚举器列表
      for (Iterable<T> iterable : iterables) { // 遍历每个可迭代对象
        enumerators.add(iterableEnumerator(iterable)); // 将可迭代对象转换为枚举器并添加到列表
      }
      return enumeratorIterator( // 返回迭代器
          new CartesianProductListEnumerator<>(enumerators)); // 包装笛卡尔积枚举器
    };
  }

  /**
   * Returns whether the arguments are equal to each other. // 返回参数是否相等
   *
   * <p>Equivalent to {@link java.util.Objects#equals} in JDK 1.7 and above. // 等同于JDK 1.7及以上版本的 {@link java.util.Objects#equals}
   */
  @Deprecated // to be removed before 2.0 // 已废弃：将在2.0版本之前移除
  public static <T> boolean equals(T t0, T t1) { // 公共静态方法：比较两个对象是否相等
    return Objects.equals(t0, t1); // 使用Objects.equals方法进行比较，处理null情况
  }

  /** Closes an iterator, if it can be closed. */ // 如果迭代器可以关闭，则关闭它
  private static <T> void closeIterator(@Nullable Iterator<? extends T> iterator) { // 私有静态方法：关闭迭代器
    if (iterator instanceof AutoCloseable) { // 如果迭代器实现了AutoCloseable接口
      try { // 尝试执行
        ((AutoCloseable) iterator).close(); // 调用close方法关闭迭代器
      } catch (RuntimeException e) { // 捕获运行时异常
        throw e; // 重新抛出运行时异常
      } catch (Exception e) { // 捕获其他异常
        throw new RuntimeException(e); // 包装为运行时异常并抛出
      }
    }
  }

  /** Iterable enumerator. // 可迭代对象的枚举器实现
   *
   * @param <T> element type */ // 泛型参数：元素类型
  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告
  static class IterableEnumerator<T> implements Enumerator<T> { // 静态内部类：将Iterable转换为Enumerator
    private final Iterable<? extends T> iterable; // 成员变量：底层的可迭代对象，使用final修饰不可更改
    @Nullable Iterator<? extends T> iterator; // 成员变量：从可迭代对象获取的迭代器，可为null
    T current; // 成员变量：当前元素，初始值为DUMMY表示未初始化

    IterableEnumerator(Iterable<? extends T> iterable) { // 构造方法：接收可迭代对象
      this.iterable = iterable; // 保存可迭代对象引用
      iterator = iterable.iterator(); // 从可迭代对象获取迭代器
      current = (T) DUMMY; // 将current设置为DUMMY，表示还未调用moveNext()
    }

    @Override public T current() { // 重写current方法：获取当前元素
      if (current == DUMMY) { // 如果current还是DUMMY，说明没有调用moveNext()或已遍历完
        throw new NoSuchElementException(); // 抛出无此元素异常
      }
      return current; // 返回当前元素
    }

    @Override public boolean moveNext() { // 重写moveNext方法：移动到下一个元素
      if (requireNonNull(iterator, "iterator").hasNext()) { // 检查迭代器是否有下一个元素
        current = iterator.next(); // 获取下一个元素并保存到current
        return true; // 返回true表示成功移动
      }
      current = (T) DUMMY; // 没有更多元素，将current重置为DUMMY
      return false; // 返回false表示没有更多元素
    }

    @Override public void reset() { // 重写reset方法：重置枚举器到初始状态
      iterator = iterable.iterator(); // 从可迭代对象重新获取迭代器
      current = (T) DUMMY; // 将current重置为DUMMY
    }

    @Override public void close() { // 重写close方法：关闭枚举器释放资源
      final Iterator<? extends T> iterator1 = this.iterator; // 保存迭代器引用到局部变量
      this.iterator = null; // 将成员变量设为null，帮助GC
      closeIterator(iterator1); // 调用closeIterator方法尝试关闭迭代器
    }
  }

  /** Composite enumerable. // 复合可枚举对象，将多个可枚举对象连接成一个
   *
   * @param <E> element type */ // 泛型参数：元素类型
  static class CompositeEnumerable<E> extends AbstractEnumerable<E> { // 静态内部类：组合多个可枚举对象
    private final List<Enumerable<E>> enumerableList; // 成员变量：可枚举对象列表，使用final修饰不可更改

    CompositeEnumerable(List<Enumerable<E>> enumerableList) { // 构造方法：接收可枚举对象列表
      this.enumerableList = enumerableList; // 保存可枚举对象列表引用
    }

    @Override public Enumerator<E> enumerator() { // 重写enumerator方法：返回复合枚举器
      return new Enumerator<E>() { // 创建匿名Enumerator实现
        // Never null. // 永远不为null
        Enumerator<E> current = emptyEnumerator(); // 当前正在遍历的枚举器，初始为空枚举器
        final Enumerator<Enumerable<E>> enumerableEnumerator = iterableEnumerator(enumerableList); // 枚举可枚举对象列表的枚举器

        @Override public E current() { // 重写current方法：获取当前元素
          return current.current(); // 返回当前枚举器的当前元素
        }

        @Override public boolean moveNext() { // 重写moveNext方法：移动到下一个元素
          for (;;) { // 无限循环，直到找到元素或遍历完所有可枚举对象
            if (current.moveNext()) { // 如果当前枚举器还有元素
              return true; // 返回true表示成功移动
            }
            current.close(); // 当前枚举器已遍历完，关闭它释放资源
            if (!enumerableEnumerator.moveNext()) { // 尝试移动到下一个可枚举对象
              current = emptyEnumerator(); // 没有更多可枚举对象，重置为空枚举器
              return false; // 返回false表示没有更多元素
            }
            current = enumerableEnumerator.current().enumerator(); // 获取下一个可枚举对象并创建其枚举器
          }
        }

        @Override public void reset() { // 重写reset方法：重置枚举器
          enumerableEnumerator.reset(); // 重置可枚举对象列表的枚举器
          current = emptyEnumerator(); // 重置当前枚举器为空枚举器
        }

        @Override public void close() { // 重写close方法：关闭枚举器
          current.close(); // 关闭当前枚举器
          current = emptyEnumerator(); // 重置为空枚举器
        }
      };
    }
  }

  /** Iterable enumerable. // 可迭代对象的可枚举实现
   *
   * @param <T> element type */ // 泛型参数：元素类型
  static class IterableEnumerable<T> extends AbstractEnumerable2<T> { // 静态内部类：将Iterable转换为Enumerable
    protected final Iterable<T> iterable; // 成员变量：底层的可迭代对象，使用protected修饰，子类可访问

    IterableEnumerable(Iterable<T> iterable) { // 构造方法：接收可迭代对象
      this.iterable = iterable; // 保存可迭代对象引用
    }

    @Override public Iterator<T> iterator() { // 重写iterator方法：返回迭代器
      return iterable.iterator(); // 直接返回可迭代对象的迭代器
    }

    @Override public boolean any() { // 重写any方法：判断是否有任何元素
      return iterable.iterator().hasNext(); // 通过迭代器的hasNext方法判断是否有元素
    }
  }

  /** Collection enumerable. // 集合的可枚举实现，提供集合特定的优化方法
   *
   * @param <T> element type */ // 泛型参数：元素类型
  static class CollectionEnumerable<T> extends IterableEnumerable<T> { // 静态内部类：将Collection转换为Enumerable，继承IterableEnumerable
    CollectionEnumerable(Collection<T> iterable) { // 构造方法：接收集合对象
      super(iterable); // 调用父类构造方法，传入可迭代对象
    }

    protected Collection<T> getCollection() { // 受保护方法：获取集合对象
      return (Collection<T>) iterable; // 将iterable强转为Collection类型并返回
    }

    @Override public int count() { // 重写count方法：返回元素数量
      return getCollection().size(); // 直接调用集合的size方法，高效获取元素数量
    }

    @Override public long longCount() { // 重写longCount方法：返回元素数量（long类型）
      return getCollection().size(); // 直接调用集合的size方法
    }

    @SuppressWarnings("argument.type.incompatible") // 抑制参数类型不兼容警告
    @Override public boolean contains(T element) { // 重写contains方法：判断是否包含指定元素
      return getCollection().contains(element); // 直接调用集合的contains方法
    }

    @Override public boolean any() { // 重写any方法：判断是否有任何元素
      return !getCollection().isEmpty(); // 通过集合的isEmpty方法判断，比创建迭代器更高效
    }
  }

  /** List enumerable. // 列表的可枚举实现，提供列表特定的优化方法
   *
   * @param <T> element type */ // 泛型参数：元素类型
  static class ListEnumerable<T> extends CollectionEnumerable<T> { // 静态内部类：将List转换为Enumerable，继承CollectionEnumerable
    ListEnumerable(List<T> list) { // 构造方法：接收列表对象
      super(list); // 调用父类构造方法，传入集合对象
    }

    @Override public Enumerator<T> enumerator() { // 重写enumerator方法：返回枚举器
      if (iterable instanceof RandomAccess) { // 如果列表支持随机访问（如ArrayList）
        //noinspection unchecked // 抑制未检查的类型转换警告
        return new ListEnumerator<>((List) iterable); // 使用优化的ListEnumerator，支持随机访问
      }
      return super.enumerator(); // 否则使用父类的枚举器实现
    }

    @Override public List<T> toList() { // 重写toList方法：转换为List
      return (List<T>) iterable; // 直接返回底层列表，无需转换
    }

    @Override public Enumerable<T> skip(int count) { // 重写skip方法：跳过指定数量的元素
      final List<T> list = toList(); // 获取底层列表
      if (count >= list.size()) { // 如果跳过的数量大于等于列表大小
        return Linq4j.emptyEnumerable(); // 返回空可枚举对象
      }
      return new ListEnumerable<>(list.subList(count, list.size())); // 返回从count位置开始的子列表
    }

    @Override public Enumerable<T> take(int count) { // 重写take方法：获取指定数量的元素
      final List<T> list = toList(); // 获取底层列表
      if (count >= list.size()) { // 如果获取的数量大于等于列表大小
        return this; // 返回当前对象，无需截取
      }
      return new ListEnumerable<>(list.subList(0, count)); // 返回从0到count位置的子列表
    }

    @Override public T elementAt(int index) { // 重写elementAt方法：获取指定索引的元素
      return toList().get(index); // 直接调用列表的get方法，O(1)时间复杂度
    }
  }

  /** Enumerator that returns one element. // 单元素枚举器，只返回一个元素
   *
   * @param <E> element type */ // 泛型参数：元素类型
  private static class SingletonEnumerator<E> implements Enumerator<E> { // 私有静态内部类：实现单元素枚举器
    final E e; // 成员变量：要返回的元素，使用final修饰不可更改
    int i = 0; // 成员变量：计数器，用于控制只返回一次元素

    SingletonEnumerator(E e) { // 构造方法：接收元素
      this.e = e; // 保存元素引用
    }

    @Override public E current() { // 重写current方法：获取当前元素
      return e; // 返回保存的元素
    }

    @Override public boolean moveNext() { // 重写moveNext方法：移动到下一个元素
      return i++ == 0; // 第一次调用时返回true（i从0开始，i++后为1，0==0为true），之后返回false
    }

    @Override public void reset() { // 重写reset方法：重置枚举器
      i = 0; // 将计数器重置为0，可以再次遍历
    }

    @Override public void close() { // 重写close方法：关闭枚举器
    } // 空实现，单元素枚举器没有资源需要释放
  }

  /** Enumerator that returns one null element. // 单null元素枚举器，只返回一个null元素
   *
   * @param <E> element type */ // 泛型参数：元素类型
  private static class SingletonNullEnumerator<@Nullable E> implements Enumerator<E> { // 私有静态内部类：实现单null元素枚举器
    int i = 0; // 成员变量：计数器，用于控制只返回一次null元素

    @Override public E current() { // 重写current方法：获取当前元素
      return null; // 返回null
    }

    @Override public boolean moveNext() { // 重写moveNext方法：移动到下一个元素
      return i++ == 0; // 第一次调用时返回true（i从0开始，i++后为1，0==0为true），之后返回false
    }

    @Override public void reset() { // 重写reset方法：重置枚举器
      i = 0; // 将计数器重置为0，可以再次遍历
    }

    @Override public void close() { // 重写close方法：关闭枚举器
    } // 空实现，单null元素枚举器没有资源需要释放
  }

  /** Iterator that reads from an underlying {@link Enumerator}. // 从底层 {@link Enumerator} 读取数据的迭代器
   *
   * @param <T> element type */ // 泛型参数：元素类型
  private static class EnumeratorIterator<T> // 私有静态内部类：将Enumerator转换为Iterator
      implements Iterator<T>, AutoCloseable { // 实现Iterator和AutoCloseable接口
    private final Enumerator<T> enumerator; // 成员变量：底层的枚举器，使用final修饰不可更改
    boolean hasNext; // 成员变量：缓存是否有下一个元素的状态

    EnumeratorIterator(Enumerator<T> enumerator) { // 构造方法：接收枚举器
      this.enumerator = enumerator; // 保存枚举器引用
      hasNext = enumerator.moveNext(); // 预先调用moveNext()，初始化hasNext状态
    }

    @Override public boolean hasNext() { // 重写hasNext方法：判断是否有下一个元素
      return hasNext; // 返回缓存的hasNext状态
    }

    @Override public T next() { // 重写next方法：获取下一个元素
      T t = enumerator.current(); // 获取当前元素
      hasNext = enumerator.moveNext(); // 移动到下一个元素，更新hasNext状态
      return t; // 返回之前保存的当前元素
    }

    @Override public void remove() { // 重写remove方法：移除当前元素
      throw new UnsupportedOperationException(); // 抛出不支持操作异常，因为枚举器不支持移除
    }

    @Override public void close() { // 重写close方法：关闭迭代器
      enumerator.close(); // 关闭底层的枚举器，释放资源
    }
  }

  /** Enumerator optimized for random-access list. // 为支持随机访问的列表优化的枚举器
   *
   * @param <V> element type */ // 泛型参数：元素类型
  private static class ListEnumerator<V> implements Enumerator<V> { // 私有静态内部类：优化列表的枚举
    private final List<? extends V> list; // 成员变量：底层列表，使用final修饰不可更改
    int i = -1; // 成员变量：当前索引，初始为-1表示还未开始

    ListEnumerator(List<? extends V> list) { // 构造方法：接收列表
      this.list = list; // 保存列表引用
    }

    @Override public V current() { // 重写current方法：获取当前元素
      return list.get(i); // 通过索引直接获取元素，O(1)时间复杂度
    }

    @Override public boolean moveNext() { // 重写moveNext方法：移动到下一个元素
      return ++i < list.size(); // 先递增索引，然后检查是否小于列表大小
    }

    @Override public void reset() { // 重写reset方法：重置枚举器
      i = -1; // 将索引重置为-1
    }

    @Override public void close() { // 重写close方法：关闭枚举器
    } // 空实现，列表枚举器没有资源需要释放
  }

  /** Enumerates over the cartesian product of the given lists, returning // 枚举给定列表的笛卡尔积，每一行返回一个列表
   * a list for each row.
   *
   * @param <E> element type */ // 泛型参数：元素类型
  private static class CartesianProductListEnumerator<E> // 私有静态内部类：实现笛卡尔积列表枚举器
      extends CartesianProductEnumerator<E, List<E>> { // 继承CartesianProductEnumerator基类
    CartesianProductListEnumerator(List<Enumerator<E>> enumerators) { // 构造方法：接收枚举器列表
      super(enumerators); // 调用父类构造方法，传入枚举器列表
    }

    @Override public List<E> current() { // 重写current方法：获取当前元素（笛卡尔积的一行）
      return Arrays.asList(elements.clone()); // 克隆元素数组并转换为列表返回，避免外部修改影响内部状态
    }
  }
}
