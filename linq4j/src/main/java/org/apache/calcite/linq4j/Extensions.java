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
package org.apache.calcite.linq4j; // 包声明：定义此文件所属的包为 org.apache.calcite.linq4j，这是 Calcite LINQ4J 框架的核心包之一

import org.apache.calcite.linq4j.function.Function2; // 导入 Function2 接口，表示接受两个参数的函数，用于定义聚合操作（如求和、求最大值等）

import java.math.BigDecimal; // 导入 BigDecimal 类，用于精确的十进制数值计算，对应 .NET 的 decimal 类型
import java.util.Comparator; // 导入 Comparator 接口，用于定义对象之间的比较规则
import java.util.Map; // 导入 Map 接口，对应 .NET 的 Dictionary 接口，用于存储键值对

/**
 * Contains what, in LINQ.NET, would be extension methods. // 类作用说明：此类包含在 LINQ.NET 中会被定义为扩展方法的静态工具方法集合
 *
 * <h2>Notes on mapping from LINQ.NET to Java</h2> // 说明：从 LINQ.NET 到 Java 的映射注意事项
 *
 * <p>We have preserved most of the API. But we've changed a few things, so that // 说明：我们保留了大部分 API，但为了使 API 更符合 Java 的习惯，做了一些调整
 * the API is more typical Java API:
 *
 * <ul>
 *
 * <li>Java method names start with a lower-case letter.</li> // 说明：Java 方法名以小写字母开头（不同于 .NET 的大写开头）
 *
 * <li>A few methods became keywords when their first letter was converted // 说明：当方法首字母转换为小写后，有些方法变成了 Java 关键字
 * to lower case; hence
 * {@link org.apache.calcite.linq4j.tree.Expressions#break_}</li> // 示例：break_ 方法，因为 break 是 Java 关键字
 *
 * <li>We created a Java interface {@link Enumerable}, similar to LINQ.NET's // 说明：创建了 Enumerable 接口，类似于 .NET 的 IEnumerable
 * IEnumerable. IEnumerable is built into C#, and that gives it // 说明：IEnumerable 在 C# 中是内置的，因此标准集合都实现了它，可以在 foreach 循环中使用
 * advantages: the standard collections implement it, and you can use
 * any IEnumerable in a foreach loop. We made the Java // 说明：在 Java 中，我们让 Enumerable 继承 Iterable，使其可以在 for-each 循环中使用
 * {@code Enumerable} extend {@link Iterable},
 * so that it can be used in for-each loops. But the standard // 说明：但标准集合仍然没有实现 Enumerable 接口，因此某些方法在 LINQ4J 中接受 Iterable 而非 IEnumerable
 * collections still don't implement it. A few methods that take an
 * IEnumerable in LINQ.NET take an Iterable in LINQ4J.</li>
 *
 * <li>LINQ.NET's Dictionary interface maps to Map in Java; // 说明：LINQ.NET 的 Dictionary 接口映射到 Java 的 Map 接口
 * hence, the LINQ.NET {@code ToDictionary} methods become // 说明：因此 ToDictionary 方法在 Java 中改名为 toMap
 * {@code toMap}.</li>
 *
 * <li>LINQ.NET's decimal type changes to BigDecimal. (A little bit unnatural, // 说明：decimal 类型在 Java 中改为 BigDecimal（虽然 decimal 是基本类型而 BigDecimal 不是，有些不自然）
 * since decimal is primitive and BigDecimal is not.)</li>
 *
 * <li>There is no Nullable in Java. Therefore we distinguish between methods // 说明：Java 中没有 Nullable 类型，因此区分返回 Long（可为 null）和 long（不可为 null）的方法
 * that return, say, Long (which may be null) and long. See for example // 示例：NullableLongFunction1 和 LongFunction1，以及 Enumerable#sum 的不同变体
 * {@link org.apache.calcite.linq4j.function.NullableLongFunction1} and
 * {@link org.apache.calcite.linq4j.function.LongFunction1}, and the
 * variants of {@link Enumerable#sum} that call them.
 *
 * <li>Java erases type parameters from argument types before resolving // 说明：Java 在解析方法重载前会擦除类型参数，导致相似方法具有相同的擦除类型
 * overloading. Therefore similar methods have the same erasure. Methods // 说明：为了避免歧义，一些方法被重命名
 * {@link ExtendedQueryable#averageDouble averageDouble},
 * {@link ExtendedQueryable#averageInteger averageInteger},
 * {@link ExtendedQueryable#groupByK groupByK},
 * {@link ExtendedQueryable#selectN selectN},
 * {@link ExtendedQueryable#selectManyN selectManyN},
 * {@link ExtendedQueryable#skipWhileN skipWhileN},
 * {@link ExtendedQueryable#sumBigDecimal sumBigDecimal},
 * {@link ExtendedQueryable#sumNullableBigDecimal sumNullableBigDecimal},
 * {@link ExtendedQueryable#whereN whereN}
 * have been renamed from {@code average}, {@code groupBy}, {@code max},
 * {@code min}, {@code select}, {@code selectMany}, {@code skipWhile} and
 * {@code where} to prevent ambiguity.</li>
 *
 * <li>.NET allows <i>extension methods</i> &mdash; static methods that then // 说明：.NET 支持扩展方法（静态方法，通过编译器魔术成为第一个参数类型的方法）
 * become, via compiler magic, a method of any object whose type is the
 * same as the first parameter of the extension method. In LINQ.NET, the // 说明：在 LINQ.NET 中，IQueryable 和 IEnumerable 接口有很多这样的扩展方法
 * {@code IQueryable} and {@code IEnumerable} interfaces have many such methods.
 * In Java, those methods need to be explicitly added to the interface, and will // 说明：在 Java 中，这些方法需要显式添加到接口中，并且每个实现类都需要实现这些方法
 * need to be implemented by every class that implements that interface.
 * We can help by implementing the methods as static methods, and by // 说明：通过将这些方法实现为静态方法，并提供实现接口扩展方法的抽象基类来帮助
 * providing an abstract base class that implements the extension methods // 说明：因此 AbstractEnumerable 和 AbstractQueryable 会调用 Extensions 类中的方法
 * in the interface. Hence {@link AbstractEnumerable} and
 * {@link AbstractQueryable} call methods in {@link Extensions}.</li>
 *
 * <li>.NET Func becomes {@link org.apache.calcite.linq4j.function.Function0}, // 说明：.NET 的 Func 委托根据参数数量映射到不同的 Function 接口
 * {@link org.apache.calcite.linq4j.function.Function1},
 * {@link org.apache.calcite.linq4j.function.Function2}, depending
 * on the number of arguments to the function, because Java types cannot be // 说明：因为 Java 类型不能基于类型参数的数量进行重载
 * overloaded based on the number of type parameters.</li>
 *
 * <li>Types map as follows: // 说明：类型映射关系如下
 * {@code Int32} &rarr; {@code int} or {@link Integer}, // Int32 映射到 int 或 Integer
 * {@code Int64} &rarr; {@code long} or {@link Long}, // Int64 映射到 long 或 Long
 * {@code bool} &rarr; {@code boolean} or {@link Boolean}, // bool 映射到 boolean 或 Boolean
 * {@code Dictionary} &rarr; {@link Map}, // Dictionary 映射到 Map
 * {@code Lookup} &rarr; {@link Map} whose value type is an {@link Iterable}, // Lookup 映射到 Map，其中值类型是 Iterable
 * </li>
 *
 * <li>Function types that accept primitive types in LINQ.NET have become // 说明：在 LINQ.NET 中接受基本类型的函数类型在 LINQ4J 中变成了包装类型
 * boxed types in LINQ4J. For example, a predicate function // 示例：谓词函数 Func<T, bool> 变成了 Func1<T, Boolean>
 * {@code Func<T, bool>} becomes {@code Func1<T, Boolean>}.
 * It would be wrong to infer that the function is allowed to return null.</li> // 说明：不能错误地推断函数允许返回 null
 *
 * </ul>
 */
public abstract class Extensions { // 定义一个抽象类 Extensions，包含各种扩展方法的静态实现，此类不能被实例化
  private Extensions() {} // 私有构造方法，防止类被实例化，因为这是一个工具类，只包含静态方法和静态变量

  static final Function2<BigDecimal, BigDecimal, BigDecimal> BIG_DECIMAL_SUM = // 成员变量：BigDecimal 类型的求和函数，接受两个 BigDecimal 参数，返回它们的和
      BigDecimal::add; // 使用方法引用，调用 BigDecimal 的 add 方法进行加法运算

  static final Function2<Float, Float, Float> FLOAT_SUM = // 成员变量：Float 类型的求和函数，接受两个 Float 参数，返回它们的和
      (v1, v2) -> v1 + v2; // 使用 Lambda 表达式，返回两个 Float 值的和

  static final Function2<Double, Double, Double> DOUBLE_SUM = // 成员变量：Double 类型的求和函数，接受两个 Double 参数，返回它们的和
      (v1, v2) -> v1 + v2; // 使用 Lambda 表达式，返回两个 Double 值的和

  static final Function2<Integer, Integer, Integer> INTEGER_SUM = // 成员变量：Integer 类型的求和函数，接受两个 Integer 参数，返回它们的和
      (v1, v2) -> v1 + v2; // 使用 Lambda 表达式，返回两个 Integer 值的和

  static final Function2<Long, Long, Long> LONG_SUM = // 成员变量：Long 类型的求和函数，接受两个 Long 参数，返回它们的和
      (v1, v2) -> v1 + v2; // 使用 Lambda 表达式，返回两个 Long 值的和

  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告，因为 Comparable 是原始类型
  static final Function2<Comparable, Comparable, Comparable> COMPARABLE_MIN = // 成员变量：Comparable 类型的最小值函数，接受两个 Comparable 参数，返回较小的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) > 0 ? v2 : v1; // 如果 v1 为 null 或 v1 大于 v2，返回 v2，否则返回 v1，处理了 null 值情况

  @SuppressWarnings("unchecked") // 抑制未检查的类型转换警告，因为 Comparable 是原始类型
  static final Function2<Comparable, Comparable, Comparable> COMPARABLE_MAX = // 成员变量：Comparable 类型的最大值函数，接受两个 Comparable 参数，返回较大的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) < 0 ? v2 : v1; // 如果 v1 为 null 或 v1 小于 v2，返回 v2，否则返回 v1，处理了 null 值情况

  static final Function2<Float, Float, Float> FLOAT_MIN = // 成员变量：Float 类型的最小值函数，接受两个 Float 参数，返回较小的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) > 0 ? v2 : v1; // 如果 v1 为 null 或 v1 大于 v2，返回 v2，否则返回 v1

  static final Function2<Float, Float, Float> FLOAT_MAX = // 成员变量：Float 类型的最大值函数，接受两个 Float 参数，返回较大的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) < 0 ? v2 : v1; // 如果 v1 为 null 或 v1 小于 v2，返回 v2，否则返回 v1

  static final Function2<Double, Double, Double> DOUBLE_MIN = // 成员变量：Double 类型的最小值函数，接受两个 Double 参数，返回较小的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) > 0 ? v2 : v1; // 如果 v1 为 null 或 v1 大于 v2，返回 v2，否则返回 v1

  static final Function2<Double, Double, Double> DOUBLE_MAX = // 成员变量：Double 类型的最大值函数，接受两个 Double 参数，返回较大的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) < 0 ? v2 : v1; // 如果 v1 为 null 或 v1 小于 v2，返回 v2，否则返回 v1

  static final Function2<Integer, Integer, Integer> INTEGER_MIN = // 成员变量：Integer 类型的最小值函数，接受两个 Integer 参数，返回较小的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) > 0 ? v2 : v1; // 如果 v1 为 null 或 v1 大于 v2，返回 v2，否则返回 v1

  static final Function2<Integer, Integer, Integer> INTEGER_MAX = // 成员变量：Integer 类型的最大值函数，接受两个 Integer 参数，返回较大的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) < 0 ? v2 : v1; // 如果 v1 为 null 或 v1 小于 v2，返回 v2，否则返回 v1

  static final Function2<Long, Long, Long> LONG_MIN = // 成员变量：Long 类型的最小值函数，接受两个 Long 参数，返回较小的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) > 0 ? v2 : v1; // 如果 v1 为 null 或 v1 大于 v2，返回 v2，否则返回 v1

  static final Function2<Long, Long, Long> LONG_MAX = // 成员变量：Long 类型的最大值函数，接受两个 Long 参数，返回较大的一个
      (v1, v2) -> v1 == null || v1.compareTo(v2) < 0 ? v2 : v1; // 如果 v1 为 null 或 v1 小于 v2，返回 v2，否则返回 v1

  // flags a piece of code we're yet to implement // 方法说明：标记尚未实现的代码片段，用于开发过程中标记待完成的功能
  public static RuntimeException todo() { // 方法作用：返回一个 RuntimeException，用于标记尚未实现的功能，在开发过程中作为占位符
    return new RuntimeException(); // 创建并返回一个新的 RuntimeException 实例，表示功能尚未实现
  }

  public static <T> Queryable<T> asQueryable(DefaultEnumerable<T> source) { // 方法作用：将 DefaultEnumerable 转换为 Queryable，使其具有查询能力
    //noinspection unchecked // 抑制未检查的类型转换警告
    return source instanceof Queryable // 检查 source 是否已经是 Queryable 的实例
        ? ((Queryable<T>) source) // 如果已经是 Queryable，直接强制转换并返回
        : new EnumerableQueryable<T>( // 如果不是 Queryable，创建一个新的 EnumerableQueryable 实例
            Linq4j.DEFAULT_PROVIDER, (Class) Object.class, null, source); // 使用默认提供者、Object 类、null 表达式和 source 创建可查询对象
  }

  static <T extends Comparable<T>> Comparator<T> comparableComparator() { // 方法作用：返回一个基于 Comparable 接口的比较器，用于比较实现了 Comparable 接口的对象
    return Comparable::compareTo; // 使用方法引用，返回 Comparable 的 compareTo 方法作为比较器
  }
}
