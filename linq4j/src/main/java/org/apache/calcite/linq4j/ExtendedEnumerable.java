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
import org.apache.calcite.linq4j.function.IntegerFunction1;
import org.apache.calcite.linq4j.function.LongFunction1;
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1;
import org.apache.calcite.linq4j.function.NullableDoubleFunction1;
import org.apache.calcite.linq4j.function.NullableFloatFunction1;
import org.apache.calcite.linq4j.function.NullableIntegerFunction1;
import org.apache.calcite.linq4j.function.NullableLongFunction1;
import org.apache.calcite.linq4j.function.Predicate1;
import org.apache.calcite.linq4j.function.Predicate2;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.framework.qual.Covariant;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Extension methods in {@link Enumerable}. // Enumerable接口的扩展方法集合
 * // 这个接口定义了LINQ(Language Integrated Query)风格的扩展方法，提供了丰富的数据查询和操作功能
 * // 它是对基础Enumerable接口的增强，支持更复杂的查询操作，如聚合、分组、连接、排序等
 * // 所有方法都是延迟执行的，返回一个新的Enumerable对象，不会立即处理数据
 * // 这种设计允许链式调用，类似于SQL的查询语法
 *
 * @param <TSource> Element type // 泛型参数TSource表示集合中元素的类型
 */
@Covariant(0) // 注解表示该接口的泛型参数是协变的，允许子类型赋值给父类型
public interface ExtendedEnumerable<TSource> { // 定义扩展Enumerable功能的接口

  /**
   * Performs an operation for each member of this enumeration. // 对枚举中的每个成员执行指定操作
   * // 这个方法会遍历集合中的每个元素，并对每个元素应用指定的函数
   * // 与传统的forEach不同，这个方法会返回最后一个元素执行函数后的结果值
   * // 如果集合为空，则返回null
   * // 这是一个立即执行的方法，会立即遍历集合
   *
   * <p>Returns the value returned by the function for the last element in
   * this enumeration, or null if this enumeration is empty. // 返回集合中最后一个元素执行函数后的结果值，如果集合为空则返回null
   *
   * @param func Operation // 要对每个元素执行的操作函数，接收一个TSource类型参数，返回R类型结果
   * @param <R> Return type // 函数返回值的类型
   */
  <R> @Nullable R foreach(Function1<TSource, R> func); // 声明foreach方法，返回类型为可空的R类型

  /**
   * Applies an accumulator function over a
   * sequence. // 对序列应用累加器函数
   * // 这是一个聚合操作，将序列中的所有元素通过累加器函数合并为一个值
   * // 累加器函数接收两个参数：当前累加值和当前元素，返回新的累加值
   * // 第一个元素作为初始累加值，后续每个元素与累加值进行计算
   * // 例如：对数字序列求和、求积、拼接字符串等
   * // 如果序列为空，返回null
   * // 这是一个立即执行的方法
   */
  @Nullable TSource aggregate(Function2<@Nullable TSource, TSource, TSource> func); // 声明aggregate方法，累加器函数接收当前累加值和当前元素，返回新的累加值

  /**
   * Applies an accumulator function over a
   * sequence. The specified seed value is used as the initial
   * accumulator value. // 对序列应用累加器函数，使用指定的种子值作为初始累加值
   * // 这个方法与上一个aggregate方法的区别在于可以指定初始累加值(seed)
   * // 种子值作为累加器的初始状态，然后对序列中的每个元素应用累加函数
   * // 这样可以处理空序列的情况，因为种子值总是存在的
   * // 适用场景：当需要特定的初始值或当序列可能为空时
   *
   * <p>If {@code seed} is not null, the result is never null. // 如果种子值不为null，则结果永远不会为null
   *
   * @param seed 初始累加值，作为累加过程的起始状态
   * @param func 累加器函数，接收当前累加值和当前元素，返回新的累加值
   * @param <TAccumulate> 累加值的类型
   * @return 聚合后的最终累加值
   */
  <TAccumulate> @PolyNull TAccumulate aggregate(@PolyNull TAccumulate seed, // 声明带种子值的aggregate方法
      Function2<@PolyNull TAccumulate, TSource, @PolyNull TAccumulate> func); // 累加器函数，接收累加值和元素，返回新累加值

  /**
   * Applies an accumulator function over a
   * sequence. The specified seed value is used as the initial
   * accumulator value, and the specified function is used to select
   * the result value. // 对序列应用累加器函数，使用种子值作为初始累加值，并使用选择器函数转换最终结果
   * // 这是最灵活的aggregate方法，包含三个步骤：
   * // 1. 使用seed作为初始累加值
   * // 2. 对每个元素应用累加函数，更新累加值
   * // 3. 使用选择器函数将最终的累加值转换为所需的结果类型
   * // 适用场景：需要将累加结果转换为不同类型的情况
   * // 例如：计算平均值(累加求和后除以元素数量)、计算统计信息等
   *
   * @param seed 初始累加值
   * @param func 累加器函数，接收累加值和元素，返回新累加值
   * @param selector 结果选择器函数，将最终累加值转换为结果类型
   * @param <TAccumulate> 累加值类型
   * @param <TResult> 最终结果类型
   * @return 转换后的最终结果
   */
  <TAccumulate, TResult> TResult aggregate(TAccumulate seed, // 声明带选择器的aggregate方法
      Function2<TAccumulate, TSource, TAccumulate> func, // 累加器函数
      Function1<TAccumulate, TResult> selector); // 结果选择器函数

  /**
   * Determines whether all elements of a sequence
   * satisfy a condition. // 确定序列中的所有元素是否都满足指定条件
   * // 这是一个量词操作符，用于检查集合中的每个元素是否符合某个条件
   * // 如果序列为空，返回true（空集合的所有元素都满足任何条件）
   * // 如果所有元素都满足条件，返回true；只要有一个元素不满足条件，就返回false
   * // 这是一个短路求值方法，一旦发现不满足条件的元素就立即返回
   * // 适用场景：验证数据完整性、检查约束条件等
   * // 这是一个立即执行的方法
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 如果所有元素都满足条件或序列为空，返回true；否则返回false
   */
  boolean all(Predicate1<TSource> predicate); // 声明all方法，返回布尔值

  /**
   * Determines whether a sequence contains any
   * elements. (Defined by Enumerable.) // 确定序列是否包含任何元素
   * // 这是一个量词操作符，用于检查集合是否非空
   * // 如果序列包含至少一个元素，返回true；如果序列为空，返回false
   * // 这是一个立即执行的方法
   * // 适用场景：检查数据是否存在、验证集合非空等
   *
   * @return 如果序列包含至少一个元素，返回true；否则返回false
   */
  boolean any(); // 声明any方法的无参版本

  /**
   * Determines whether any element of a sequence
   * satisfies a condition. // 确定序列中是否有任何元素满足指定条件
   * // 这是一个量词操作符，用于检查集合中是否存在满足条件的元素
   * // 如果序列为空，返回false
   * // 如果至少有一个元素满足条件，返回true；如果所有元素都不满足条件，返回false
   * // 这是一个短路求值方法，一旦发现满足条件的元素就立即返回
   * // 适用场景：检查是否存在特定数据、验证条件是否可能满足等
   * // 这是一个立即执行的方法
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 如果至少有一个元素满足条件，返回true；否则返回false
   */
  boolean any(Predicate1<TSource> predicate); // 声明any方法的带谓词版本

    /**

     * Returns the input typed as {@code Enumerable<TSource>}. // 返回类型为Enumerable<TSource>的输入

     * // 这个方法的主要作用是将具体类型转换为Enumerable接口类型

     * // 在运行时，它只是返回this本身，不执行任何实际操作

     * // 但在编译时，它会改变类型，使得后续调用使用Enumerable接口的方法而不是具体类型的方法

     * // 这是一个类型转换方法，运行时开销为零

     *

     * <p>This method has no effect

     * other than to change the compile-time type of source from a type that

     * implements {@code Enumerable<TSource>} to {@code Enumerable<TSource>}

     * itself. // 这个方法除了将源类型从实现Enumerable<TSource>的类型更改为Enumerable<TSource>本身外，没有其他效果

     *

     * <p>{@code asEnumerable<TSource>(Enumerable<TSource>)} can be used to choose

     * between query implementations when a sequence implements

     * {@code Enumerable<TSource>} but also has a different set of public query

     * methods available. For example, given a generic class Table that implements

     * {@code Enumerable<TSource>} and has its own methods such as {@code where},

     * {@code select}, and {@code selectMany}, a call to {@code where} would

     * invoke the public {@code where} method of {@code Table}. A {@code Table}

     * type that represents a database table could have a {@code where} method

     * that takes the predicate argument as an expression tree and converts the

     * tree to SQL for remote execution. If remote execution is not desired, for

     * example because the predicate invokes a local method, the

     * {@code asEnumerable<TSource>} method can be used to hide the custom methods

     * and instead make the standard query operators available. // 当序列实现了Enumerable<TSource>但也有不同的公共查询方法集时，asEnumerable可用于选择查询实现。例如，一个实现Enumerable<TSource>的泛型类Table有自己的方法如where、select和selectMany，调用where会调用Table的公共where方法。表示数据库表的Table类型可能有where方法，它将谓词参数作为表达式树接收，并将树转换为SQL以进行远程执行。如果不希望远程执行，例如谓词调用本地方法，可以使用asEnumerable<TSource>方法来隐藏自定义方法，而是使标准查询操作符可用。

     * // 适用场景：

     * // 1. 当需要强制使用标准LINQ操作符而不是自定义实现时

     * // 2. 当需要避免远程执行，改用本地内存操作时

     * // 3. 当需要改变编译时类型以调用不同的方法重载时

     *

     * @return 返回类型为Enumerable<TSource>的当前对象

     */

    Enumerable<TSource> asEnumerable(); // 声明asEnumerable方法

  /**
   * Converts an Enumerable to a {@link Queryable}. // 将Enumerable转换为Queryable
   * // 这个方法将内存中的可枚举集合转换为可查询对象
   * // Queryable接口允许构建表达式树，可以用于远程查询（如数据库查询）
   * // 与Enumerable不同，Queryable可以延迟执行并生成表达式树
   *
   * <p>If the type of source implements {@code Queryable}, this method
   * returns it directly. Otherwise, it returns a {@code Queryable} that
   * executes queries by calling the equivalent query operator methods in
   * {@code Enumerable} instead of those in {@code Queryable}. // 如果源类型实现了Queryable，此方法直接返回它。否则，它返回一个Queryable，通过调用Enumerable中的等效查询操作符方法而不是Queryable中的方法来执行查询。
   * // 这意味着如果源已经是Queryable，就直接返回；否则创建一个包装器
   * // 包装器会调用Enumerable的方法而不是生成表达式树
   *
   * <p>Analogous to the LINQ's Enumerable.AsQueryable extension method. // 类似于LINQ的Enumerable.AsQueryable扩展方法
   * // 在LINQ中，AsQueryable用于将内存集合转换为可查询对象，以支持远程查询
   *
   * @return A queryable // 返回一个Queryable对象
   */
  Queryable<TSource> asQueryable(); // 声明asQueryable方法

  /**
   * Computes the average of a sequence of Decimal
   * values that are obtained by invoking a transform function on
   * each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的Decimal值序列的平均值
   * // 这是一个聚合操作，计算序列中元素的平均值
   * // 首先对每个元素应用选择器函数，提取出Decimal值，然后计算这些值的平均值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   * // 适用场景：计算数值型数据的平均值，如平均年龄、平均价格等
   *
   * @param selector 转换函数，从每个元素中提取Decimal值
   * @return 平均值
   */
  BigDecimal average(BigDecimalFunction1<TSource> selector); // 声明计算BigDecimal平均值的average方法

  /**
   * Computes the average of a sequence of nullable
   * Decimal values that are obtained by invoking a transform
   * function on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空Decimal值序列的平均值
   * // 与上一个方法类似，但处理的是可空的Decimal值
   * // 如果序列为空或所有值都为null，返回null
   * // 计算平均值时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的Decimal值
   * @return 平均值，如果序列为空或所有值都为null则返回null
   */
  BigDecimal average(NullableBigDecimalFunction1<TSource> selector); // 声明计算可空BigDecimal平均值的average方法

  /**
   * Computes the average of a sequence of Double
   * values that are obtained by invoking a transform function on
   * each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的Double值序列的平均值
   * // 计算Double类型值的平均值
   * // 如果序列为空，抛出异常
   *
   * @param selector 转换函数，从每个元素中提取Double值
   * @return 平均值
   */
  double average(DoubleFunction1<TSource> selector); // 声明计算double平均值的average方法

  /**
   * Computes the average of a sequence of nullable
   * Double values that are obtained by invoking a transform
   * function on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空Double值序列的平均值
   * // 计算可空Double类型值的平均值
   * // 如果序列为空或所有值都为null，返回null
   * // 计算平均值时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的Double值
   * @return 平均值，如果序列为空或所有值都为null则返回null
   */
  Double average(NullableDoubleFunction1<TSource> selector); // 声明计算可空Double平均值的average方法

  /**
   * Computes the average of a sequence of int values
   * that are obtained by invoking a transform function on each
   * element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的int值序列的平均值
   * // 计算int类型值的平均值，结果为int类型（会截断小数部分）
   * // 如果序列为空，抛出异常
   *
   * @param selector 转换函数，从每个元素中提取int值
   * @return 平均值（整数部分）
   */
  int average(IntegerFunction1<TSource> selector); // 声明计算int平均值的average方法

  /**
   * Computes the average of a sequence of nullable
   * int values that are obtained by invoking a transform function
   * on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空int值序列的平均值
   * // 计算可空Integer类型值的平均值
   * // 如果序列为空或所有值都为null，返回null
   * // 计算平均值时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的int值
   * @return 平均值，如果序列为空或所有值都为null则返回null
   */
  Integer average(NullableIntegerFunction1<TSource> selector); // 声明计算可空Integer平均值的average方法

  /**
   * Computes the average of a sequence of long values
   * that are obtained by invoking a transform function on each
   * element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的long值序列的平均值
   * // 计算long类型值的平均值，结果为long类型（会截断小数部分）
   * // 如果序列为空，抛出异常
   *
   * @param selector 转换函数，从每个元素中提取long值
   * @return 平均值（整数部分）
   */
  long average(LongFunction1<TSource> selector); // 声明计算long平均值的average方法

  /**
   * Computes the average of a sequence of nullable
   * long values that are obtained by invoking a transform function
   * on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空long值序列的平均值
   * // 计算可空Long类型值的平均值
   * // 如果序列为空或所有值都为null，返回null
   * // 计算平均值时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的long值
   * @return 平均值，如果序列为空或所有值都为null则返回null
   */
  Long average(NullableLongFunction1<TSource> selector); // 声明计算可空Long平均值的average方法

  /**
   * Computes the average of a sequence of Float
   * values that are obtained by invoking a transform function on
   * each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的Float值序列的平均值
   * // 计算Float类型值的平均值
   * // 如果序列为空，抛出异常
   *
   * @param selector 转换函数，从每个元素中提取Float值
   * @return 平均值
   */
  float average(FloatFunction1<TSource> selector); // 声明计算float平均值的average方法

  /**
   * Computes the average of a sequence of nullable
   * Float values that are obtained by invoking a transform
   * function on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空Float值序列的平均值
   * // 计算可空Float类型值的平均值
   * // 如果序列为空或所有值都为null，返回null
   * // 计算平均值时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的Float值
   * @return 平均值，如果序列为空或所有值都为null则返回null
   */
  Float average(NullableFloatFunction1<TSource> selector); // 声明计算可空Float平均值的average方法

  /**
   * Converts the elements of this Enumerable to the specified type. // 将此Enumerable的元素转换为指定类型
   * // 这个方法将集合中的每个元素强制转换为指定的类型
   * // 如果元素无法转换为目标类型，在访问该元素时会抛出ClassCastException
   * // 这是一个延迟执行的方法，只有在遍历时才会执行实际的类型转换
   *
   * <p>This method is implemented by using deferred execution. The immediate
   * return value is an object that stores all the information that is
   * required to perform the action. The query represented by this method is
   * not executed until the object is enumerated either by calling its
   * {@link Enumerable#enumerator} method directly or by using
   * {@code for (... in ...)}. // 此方法通过使用延迟执行来实现。立即返回值是一个对象，该对象存储执行操作所需的所有信息。此方法表示的查询在通过直接调用其enumerator方法或使用for (... in ...)枚举对象之前不会执行。
   * // 延迟执行意味着方法调用时不会立即处理数据，而是在遍历结果时才处理
   * // 这种设计可以提高性能，特别是在处理大型数据集时
   *
   * <p>If an element cannot be cast to type TResult, the
   * {@link Enumerator#current()} method will throw a
   * {@link ClassCastException} a exception when the element it accessed. To
   * obtain only those elements that can be cast to type TResult, use the
   * {@link #ofType(Class)} method instead. // 如果元素无法转换为TResult类型，在访问该元素时，Enumerator#current()方法将抛出ClassCastException异常。要获取只能转换为TResult类型的那些元素，请改用#ofType(Class)方法。
   * // cast方法会尝试转换所有元素，如果转换失败则抛出异常
   * // ofType方法会过滤掉无法转换的元素，只保留可以成功转换的元素
   *
   * @param clazz 目标类型的Class对象
   * @param <T2> 目标类型
   * @return 转换后的Enumerable
   * @see EnumerableDefaults#cast 参考实现
   * @see #ofType(Class) 用于过滤特定类型的方法
   */
  <T2> Enumerable<T2> cast(Class<T2> clazz); // 声明cast方法，将元素转换为指定类型

  /**
   * Concatenates two sequences. // 连接两个序列
   * // 这个方法将两个序列连接成一个序列
   * // 结果序列包含第一个序列的所有元素，后面跟着第二个序列的所有元素
   * // 这是一个延迟执行的方法
   * // 适用场景：合并多个数据源、拼接列表等
   *
   * @param enumerable1 要连接的第二个序列
   * @return 连接后的序列
   */
  Enumerable<TSource> concat(Enumerable<TSource> enumerable1); // 声明concat方法，连接两个序列

  /**
   * Determines whether a sequence contains a specified
   * element by using the default equality comparer. // 确定序列是否包含使用默认相等比较器的指定元素
   * // 这个方法检查序列中是否包含指定的元素
   * // 使用默认的相等比较器（通常是equals方法）来比较元素
   * // 如果找到匹配的元素，返回true；否则返回false
   * // 这是一个立即执行的方法
   * // 适用场景：检查元素是否存在、验证数据包含性等
   *
   * @param element 要查找的元素
   * @return 如果序列包含该元素，返回true；否则返回false
   */
  boolean contains(TSource element); // 声明contains方法的无参版本

  /**
   * Determines whether a sequence contains a specified
   * element by using a specified {@code EqualityComparer<TSource>}. // 确定序列是否包含使用指定相等比较器的指定元素
   * // 与上一个方法类似，但可以使用自定义的相等比较器
   * // 这允许使用不同的相等性判断逻辑
   * // 例如：忽略大小写、比较对象的部分属性等
   *
   * @param element 要查找的元素
   * @param comparer 自定义的相等比较器
   * @return 如果序列包含该元素，返回true；否则返回false
   */
  boolean contains(TSource element, EqualityComparer<TSource> comparer); // 声明contains方法的带比较器版本

  /**
   * Returns the number of elements in a
   * sequence. // 返回序列中的元素数量
   * // 这个方法计算序列中元素的总数
   * // 如果序列为空，返回0
   * // 这是一个立即执行的方法
   * // 适用场景：获取数据集大小、验证数据是否为空等
   *
   * @return 序列中的元素数量
   */
  int count(); // 声明count方法的无参版本

  /**
   * Returns a number that represents how many elements
   * in the specified sequence satisfy a condition. // 返回一个数字，表示指定序列中有多少元素满足条件
   * // 这个方法计算序列中满足指定条件的元素数量
   * // 对每个元素应用谓词函数，统计返回true的元素数量
   * // 如果序列为空或没有元素满足条件，返回0
   * // 这是一个立即执行的方法
   * // 适用场景：统计符合条件的数据数量、计算满足条件的记录数等
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 满足条件的元素数量
   */
  int count(Predicate1<TSource> predicate); // 声明count方法的带谓词版本

  /**
   * Returns the elements of the specified sequence or
   * the type parameter's default value in a singleton collection if
   * the sequence is empty. // 返回指定序列的元素，如果序列为空，则返回包含类型参数默认值的单例集合
   * // 这个方法用于处理空序列的情况
   * // 如果序列不为空，返回原序列
   * // 如果序列为空，返回一个只包含默认值的序列（对于引用类型，默认值为null）
   * // 这是一个延迟执行的方法
   * // 适用场景：为空序列提供默认值、避免空指针异常等
   *
   * @return 原序列或包含默认值的单例集合
   */
  Enumerable<@Nullable TSource> defaultIfEmpty(); // 声明defaultIfEmpty方法的无参版本

  /**
   * Returns the elements of the specified sequence or
   * the specified value in a singleton collection if the sequence
   * is empty. // 返回指定序列的元素，如果序列为空，则返回包含指定值的单例集合
   * // 与上一个方法类似，但可以指定默认值
   * // 如果序列不为空，返回原序列
   * // 如果序列为空，返回一个只包含指定值的序列
   * // 这是一个延迟执行的方法
   * // 适用场景：为空序列提供特定的默认值
   *
   * <p>If {@code value} is not null, the result is never null. // 如果value不为null，则结果永远不会为null
   *
   * @param value 当序列为空时使用的默认值
   * @return 原序列或包含指定值的单例集合
   */
  Enumerable<@PolyNull TSource> defaultIfEmpty(@PolyNull TSource value); // 声明defaultIfEmpty方法的带值版本

  /**
   * Returns distinct elements from a sequence by using
   * the default equality comparer to compare values. // 通过使用默认相等比较器比较值，从序列中返回不同的元素
   * // 这个方法去除序列中的重复元素
   * // 使用默认的相等比较器（通常是equals方法）来判断元素是否相等
   * // 返回的序列只包含第一次出现的元素，后续重复的元素被过滤掉
   * // 这是一个延迟执行的方法
   * // 适用场景：数据去重、获取唯一值等
   *
   * @return 不包含重复元素的序列
   */
  Enumerable<TSource> distinct(); // 声明distinct方法的无参版本

  /**
   * Returns distinct elements from a sequence by using
   * a specified {@code EqualityComparer<TSource>} to compare values. // 通过使用指定的相等比较器比较值，从序列中返回不同的元素
   * // 与上一个方法类似，但可以使用自定义的相等比较器
   * // 这允许使用不同的相等性判断逻辑
   * // 例如：忽略大小写去重、比较对象的部分属性等
   *
   * @param comparer 自定义的相等比较器
   * @return 不包含重复元素的序列
   */
  Enumerable<TSource> distinct(EqualityComparer<TSource> comparer); // 声明distinct方法的带比较器版本

  /**
   * Returns the element at a specified index in a
   * sequence. // 返回序列中指定索引处的元素
   * // 这个方法返回序列中指定位置的元素
   * // 索引从0开始
   * // 如果索引超出范围，抛出IndexOutOfBoundsException
   * // 这是一个立即执行的方法
   * // 适用场景：访问序列中特定位置的元素
   *
   * @param index 要获取的元素的索引（从0开始）
   * @return 指定索引处的元素
   */
  TSource elementAt(int index); // 声明elementAt方法

  /**
   * Returns the element at a specified index in a
   * sequence or a default value if the index is out of
   * range. // 返回序列中指定索引处的元素，如果索引超出范围，则返回默认值
   * // 与上一个方法类似，但如果索引超出范围，返回默认值而不是抛出异常
   * // 对于引用类型，默认值为null；对于值类型，默认值为0或false等
   * // 这是一个立即执行的方法
   * // 适用场景：安全地访问序列中特定位置的元素
   *
   * @param index 要获取的元素的索引（从0开始）
   * @return 指定索引处的元素，如果索引超出范围则返回默认值
   */
  @Nullable TSource elementAtOrDefault(int index); // 声明elementAtOrDefault方法

  /**
   * Produces the set difference of two sequences by
   * using the default equality comparer to compare values,
   * eliminate duplicates. (Defined by Enumerable.) // 通过使用默认相等比较器比较值，生成两个序列的差集，消除重复项
   * // 这个方法返回第一个序列中不存在于第二个序列中的元素
   * // 相当于集合的差集操作（A - B）
   * // 使用默认的相等比较器来判断元素是否相等
   * // 结果中不包含重复元素
   * // 这是一个延迟执行的方法
   * // 适用场景：找出在一个集合中但不在另一个集合中的元素
   *
   * @param enumerable1 要从中减去的第二个序列
   * @return 差集序列
   */
  Enumerable<TSource> except(Enumerable<TSource> enumerable1); // 声明except方法的无参版本

  /**
   * Produces the set difference of two sequences by
   * using the default equality comparer to compare values,
   * using {@code all} to indicate whether to eliminate duplicates.
   * (Defined by Enumerable.) // 通过使用默认相等比较器比较值，生成两个序列的差集，使用all指示是否消除重复项
   * // 与上一个方法类似，但可以通过all参数控制是否消除重复项
   * // 如果all为true，消除重复项（类似SQL的EXCEPT）
   * // 如果all为false，保留所有重复项（类似SQL的EXCEPT ALL）
   *
   * @param enumerable1 要从中减去的第二个序列
   * @param all 如果为true，消除重复项；如果为false，保留重复项
   * @return 差集序列
   */
  Enumerable<TSource> except(Enumerable<TSource> enumerable1, boolean all); // 声明except方法的带all参数版本

  /**
   * Produces the set difference of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, eliminate duplicates. // 通过使用指定的相等比较器比较值，生成两个序列的差集，消除重复项
   * // 与第一个except方法类似，但可以使用自定义的相等比较器
   *
   * @param enumerable1 要从中减去的第二个序列
   * @param comparer 自定义的相等比较器
   * @return 差集序列
   */
  Enumerable<TSource> except(Enumerable<TSource> enumerable1, // 声明except方法的带比较器版本
      EqualityComparer<TSource> comparer);

  /**
   * Produces the set difference of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, using {@code all} to indicate whether to eliminate duplicates. // 通过使用指定的相等比较器比较值，生成两个序列的差集，使用all指示是否消除重复项
   * // 这是except方法最完整的版本，支持自定义比较器和all参数
   *
   * @param enumerable1 要从中减去的第二个序列
   * @param comparer 自定义的相等比较器
   * @param all 如果为true，消除重复项；如果为false，保留重复项
   * @return 差集序列
   */
  Enumerable<TSource> except(Enumerable<TSource> enumerable1, // 声明except方法的完整版本
      EqualityComparer<TSource> comparer, boolean all);

  /**
   * Returns the first element of a sequence. (Defined
   * by Enumerable.) // 返回序列的第一个元素
   * // 这个方法返回序列中的第一个元素
   * // 如果序列为空，抛出NoSuchElementException
   * // 这是一个立即执行的方法
   * // 适用场景：获取序列的第一个元素
   *
   * @return 序列的第一个元素
   */
  TSource first(); // 声明first方法的无参版本

  /**
   * Returns the first element in a sequence that
   * satisfies a specified condition. // 返回序列中满足指定条件的第一个元素
   * // 这个方法返回序列中第一个满足指定条件的元素
   * // 如果序列为空或没有元素满足条件，抛出NoSuchElementException
   * // 这是一个立即执行的方法
   * // 适用场景：查找满足特定条件的第一个元素
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 第一个满足条件的元素
   */
  TSource first(Predicate1<TSource> predicate); // 声明first方法的带谓词版本

  /**
   * Returns the first element of a sequence, or a
   * default value if the sequence contains no elements. // 返回序列的第一个元素，如果序列不包含任何元素，则返回默认值
   * // 与first方法类似，但如果序列为空，返回默认值而不是抛出异常
   * // 对于引用类型，默认值为null；对于值类型，默认值为0或false等
   * // 这是一个立即执行的方法
   * // 适用场景：安全地获取序列的第一个元素
   *
   * @return 序列的第一个元素，如果序列为空则返回默认值
   */
  @Nullable TSource firstOrDefault(); // 声明firstOrDefault方法的无参版本

  /**
   * Returns the first element of the sequence that
   * satisfies a condition or a default value if no such element is
   * found. // 返回序列中满足条件的第一个元素，如果没有找到此类元素，则返回默认值
   * // 与first(predicate)方法类似，但如果序列为空或没有元素满足条件，返回默认值而不是抛出异常
   * // 这是一个立即执行的方法
   * // 适用场景：安全地查找满足特定条件的第一个元素
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 第一个满足条件的元素，如果没有找到则返回默认值
   */
  @Nullable TSource firstOrDefault(Predicate1<TSource> predicate); // 声明firstOrDefault方法的带谓词版本

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function. // 根据指定的键选择器函数对序列的元素进行分组
   * // 这个方法将序列中的元素按照指定的键进行分组
   // // 每个组包含一个键和该键对应的所有元素
   // // 返回一个Grouping对象的序列，每个Grouping包含一个键和该键对应的所有元素
   // // 使用默认的相等比较器来比较键
   // // 这是一个延迟执行的方法
   // // 适用场景：按类别分组、按时间分组、按属性分组等
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param <TKey> 分组键的类型
   * @return 分组后的序列，每个元素是一个Grouping对象
   */
  <TKey> Enumerable<Grouping<TKey, TSource>> groupBy( // 声明groupBy方法的最简单版本
      Function1<TSource, TKey> keySelector); // 键选择器函数

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and compares the keys by using
   * a specified comparer. // 根据指定的键选择器函数对序列的元素进行分组，并使用指定的比较器比较键
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较键
   * // 这允许使用不同的相等性判断逻辑
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 分组键的类型
   * @return 分组后的序列，每个元素是一个Grouping对象
   */
  <TKey> Enumerable<Grouping<TKey, TSource>> groupBy( // 声明groupBy方法的带比较器版本
      Function1<TSource, TKey> keySelector, EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and projects the elements for
   * each group by using a specified function. // 根据指定的键选择器函数对序列的元素进行分组，并使用指定的函数投影每个组的元素
   * // 与第一个groupBy方法类似，但可以使用元素选择器来转换组中的元素
   * // 这样可以改变组中元素的类型
   * // 例如：对Person对象按年龄分组，但组中只包含Person的姓名
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param elementSelector 元素选择器函数，将元素转换为指定的元素类型
   * @param <TKey> 分组键的类型
   * @param <TElement> 组中元素的类型
   * @return 分组后的序列，每个元素是一个Grouping对象
   */
  <TKey, TElement> Enumerable<Grouping<TKey, TElement>> groupBy( // 声明groupBy方法的带元素选择器版本
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function1<TSource, TElement> elementSelector); // 元素选择器函数

  /**
   * Groups the elements of a sequence according to a
   * key selector function. The keys are compared by using a
   * comparer and each group's elements are projected by using a
   * specified function. // 根据键选择器函数对序列的元素进行分组，使用比较器比较键，并使用指定的函数投影每个组的元素
   * // 这是groupBy方法的完整版本，同时支持元素选择器和键比较器
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param elementSelector 元素选择器函数，将元素转换为指定的元素类型
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 分组键的类型
   * @param <TElement> 组中元素的类型
   * @return 分组后的序列，每个元素是一个Grouping对象
   */
  <TKey, TElement> Enumerable<Grouping<TKey, TElement>> groupBy( // 声明groupBy方法的完整版本
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function1<TSource, TElement> elementSelector, EqualityComparer<TKey> comparer); // 元素选择器和键比较器

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值
   * // 这个方法不仅分组，还可以使用结果选择器将每个组转换为自定义的结果类型
   * // 结果选择器接收组的键和组的元素序列，返回一个结果值
   * // 这样可以灵活地定义分组后的输出格式
   * // 例如：按部门分组员工，然后计算每个部门的员工数量
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param resultSelector 结果选择器函数，接收组的键和组的元素序列，返回结果值
   * @param <TKey> 分组键的类型
   * @param <TResult> 结果的类型
   * @return 结果序列
   */
  <TKey, TResult> Enumerable<TResult> groupBy( // 声明groupBy方法的带结果选择器版本
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function2<TKey, Enumerable<TSource>, TResult> resultSelector); // 结果选择器函数

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. The keys are compared by using a
   * specified comparer. // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值，使用指定的比较器比较键
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较键
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param resultSelector 结果选择器函数，接收组的键和组的元素序列，返回结果值
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 分组键的类型
   * @param <TResult> 结果的类型
   * @return 结果序列
   */
  <TKey, TResult> Enumerable<TResult> groupBy( // 声明groupBy方法的带结果选择器和比较器版本
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function2<TKey, Enumerable<TSource>, TResult> resultSelector, // 结果选择器函数
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. The elements of each group are
   * projected by using a specified function. // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值，使用指定的函数投影每个组的元素
   * // 这个方法结合了元素选择器和结果选择器
   * // 首先使用元素选择器转换组中的元素，然后使用结果选择器将组转换为结果值
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param elementSelector 元素选择器函数，将元素转换为指定的元素类型
   * @param resultSelector 结果选择器函数，接收组的键和组的元素序列，返回结果值
   * @param <TKey> 分组键的类型
   * @param <TElement> 组中元素的类型
   * @param <TResult> 结果的类型
   * @return 结果序列
   */
  <TKey, TElement, TResult> Enumerable<TResult> groupBy( // 声明groupBy方法的带元素选择器和结果选择器版本
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function1<TSource, TElement> elementSelector, // 元素选择器函数
      Function2<TKey, Enumerable<TElement>, TResult> resultSelector); // 结果选择器函数

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. Key values are compared by using a
   * specified comparer, and the elements of each group are
   * projected by using a specified function. // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值，使用指定的比较器比较键，并使用指定的函数投影每个组的元素
   * // 这是groupBy方法最完整的版本，同时支持元素选择器、结果选择器和键比较器
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param elementSelector 元素选择器函数，将元素转换为指定的元素类型
   * @param resultSelector 结果选择器函数，接收组的键和组的元素序列，返回结果值
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 分组键的类型
   * @param <TElement> 组中元素的类型
   * @param <TResult> 结果的类型
   * @return 结果序列
   */
  <TKey, TElement, TResult> Enumerable<TResult> groupBy( // 声明groupBy方法的完整版本
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function1<TSource, TElement> elementSelector, // 元素选择器函数
      Function2<TKey, Enumerable<TElement>, TResult> resultSelector, // 结果选择器函数
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function, initializing an accumulator for each
   * group and adding to it each time an element with the same key is seen.
   * Creates a result value from each accumulator and its key using a
   * specified function. // 根据指定的键选择器函数对序列的元素进行分组，为每个组初始化一个累加器，每次看到具有相同键的元素时向其添加元素。使用指定的函数从每个累加器及其键创建结果值
   * // 这个方法是最灵活的groupBy方法，支持自定义累加器
   * // 工作流程：
   * // 1. 按键将元素分组
   * // 2. 为每个组创建一个初始累加器
   * // 3. 每次遇到具有相同键的元素时，将元素添加到累加器中
   * // 4. 使用结果选择器将累加器和键转换为最终结果
   * // 这种方法可以实现复杂的聚合操作，如计算总和、平均值、字符串拼接等
   * // 适用场景：窗口函数、分组聚合、复杂统计等
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param accumulatorInitializer 累加器初始化函数，为每个组创建初始累加器
   * @param accumulatorAdder 累加器添加函数，将元素添加到累加器中
   * @param resultSelector 结果选择器函数，将累加器和键转换为结果值
   * @param <TKey> 分组键的类型
   * @param <TAccumulate> 累加器的类型
   * @param <TResult> 结果的类型
   * @return 结果序列
   */
  <TKey, TAccumulate, TResult> Enumerable<TResult> groupBy( // 声明带累加器的groupBy方法
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function0<TAccumulate> accumulatorInitializer, // 累加器初始化函数
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder, // 累加器添加函数
      Function2<TKey, TAccumulate, TResult> resultSelector); // 结果选择器函数

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function, initializing an accumulator for each
   * group and adding to it each time an element with the same key is seen.
   * Creates a result value from each accumulator and its key using a
   * specified function. Key values are compared by using a
   * specified comparer. // 根据指定的键选择器函数对序列的元素进行分组，为每个组初始化一个累加器，每次看到具有相同键的元素时向其添加元素。使用指定的函数从每个累加器及其键创建结果值，使用指定的比较器比较键
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较键
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param accumulatorInitializer 累加器初始化函数，为每个组创建初始累加器
   * @param accumulatorAdder 累加器添加函数，将元素添加到累加器中
   * @param resultSelector 结果选择器函数，将累加器和键转换为结果值
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 分组键的类型
   * @param <TAccumulate> 累加器的类型
   * @param <TResult> 结果的类型
   * @return 结果序列
   */
  <TKey, TAccumulate, TResult> Enumerable<TResult> groupBy( // 声明带累加器和比较器的groupBy方法
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function0<TAccumulate> accumulatorInitializer, // 累加器初始化函数
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder, // 累加器添加函数
      Function2<TKey, TAccumulate, TResult> resultSelector, // 结果选择器函数
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Group keys are sorted already. Key values are compared by using a
   * specified comparator. Groups the elements of a sequence according to a
   * specified key selector function and initializing one accumulator at a time.
   * Go over elements sequentially, adding to accumulator each time an element
   * with the same key is seen. When key changes, creates a result value from the
   * accumulator and then re-initializes the accumulator. In the case of NULL values
   * in group keys, the comparator must be able to support NULL values by giving a
   * consistent sort ordering. // 分组键已经排序。使用指定的比较器比较键值。根据指定的键选择器函数对序列的元素进行分组，一次初始化一个累加器。顺序遍历元素，每次看到具有相同键的元素时将其添加到累加器中。当键改变时，从累加器创建结果值，然后重新初始化累加器。在分组键中存在NULL值的情况下，比较器必须能够通过提供一致的排序顺序来支持NULL值
   * // 这是一个优化的分组方法，适用于键已经排序的情况
   * // 与普通的groupBy不同，这个方法不需要存储所有组，可以一次只维护一个累加器
   * // 工作流程：
   * // 1. 顺序遍历元素（假设按键已排序）
   * // 2. 为第一个键初始化累加器
   * // 3. 当键相同时，将元素添加到累加器
   * // 4. 当键改变时，从累加器创建结果，然后重新初始化累加器
   * // 这种方法内存效率更高，因为不需要同时维护所有组的累加器
   * // 适用场景：处理已排序的数据、窗口函数、滚动聚合等
   *
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param accumulatorInitializer 累加器初始化函数，为每个组创建初始累加器
   * @param accumulatorAdder 累加器添加函数，将元素添加到累加器中
   * @param resultSelector 结果选择器函数，将累加器和键转换为结果值
   * @param comparator 比较器，用于比较键是否相等
   * @param <TKey> 分组键的类型
   * @param <TAccumulate> 累加器的类型
   * @param <TResult> 结果的类型
   * @return 结果序列
   */
  <TKey, TAccumulate, TResult> Enumerable<TResult> sortedGroupBy( // 声明sortedGroupBy方法
      Function1<TSource, TKey> keySelector, // 键选择器函数
      Function0<TAccumulate> accumulatorInitializer, // 累加器初始化函数
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder, // 累加器添加函数
      Function2<TKey, TAccumulate, TResult> resultSelector, // 结果选择器函数
      Comparator<TKey> comparator); // 键比较器

  /**
   * Correlates the elements of two sequences based on
   * equality of keys and groups the results. The default equality
   * comparer is used to compare keys. // 基于键的相等性关联两个序列的元素并对结果进行分组。使用默认的相等比较器比较键
   * // 这是一个分组连接操作，类似于SQL的LEFT OUTER JOIN
   * // 工作流程：
   * // 1. 对外序列的每个元素，根据外键选择器提取键
   * // 2. 在内序列中查找所有具有相同键的元素
   * // 3. 使用结果选择器将外元素和匹配的内元素序列组合成结果
   * // 即使内序列中没有匹配的元素，也会产生一个结果（内元素序列为空）
   * // 这是一个延迟执行的方法
   * // 适用场景：主从表关联、一对多关系查询等
   *
   * @param inner 内序列，要连接的第二个序列
   * @param outerKeySelector 外键选择器函数，从外序列元素中提取键
   * @param innerKeySelector 内键选择器函数，从内序列元素中提取键
   * @param resultSelector 结果选择器函数，将外元素和匹配的内元素序列组合成结果
   * @param <TInner> 内序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TKey, TResult> Enumerable<TResult> groupJoin( // 声明groupJoin方法的无参版本
      Enumerable<TInner> inner, Function1<TSource, TKey> outerKeySelector, // 内序列和外键选择器
      Function1<TInner, TKey> innerKeySelector, // 内键选择器
      Function2<TSource, Enumerable<TInner>, TResult> resultSelector); // 结果选择器

  /**
   * Correlates the elements of two sequences based on
   * key equality and groups the results. A specified
   * {@code EqualityComparer<TSource>} is used to compare keys. // 基于键的相等性关联两个序列的元素并对结果进行分组。使用指定的相等比较器比较键
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较键
   *
   * @param inner 内序列，要连接的第二个序列
   * @param outerKeySelector 外键选择器函数，从外序列元素中提取键
   * @param innerKeySelector 内键选择器函数，从内序列元素中提取键
   * @param resultSelector 结果选择器函数，将外元素和匹配的内元素序列组合成结果
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TInner> 内序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TKey, TResult> Enumerable<TResult> groupJoin( // 声明groupJoin方法的带比较器版本
      Enumerable<TInner> inner, Function1<TSource, TKey> outerKeySelector, // 内序列和外键选择器
      Function1<TInner, TKey> innerKeySelector, // 内键选择器
      Function2<TSource, Enumerable<TInner>, TResult> resultSelector, // 结果选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Produces the set intersection of two sequences by
   * using the default equality comparer to compare values,
   * eliminate duplicates. (Defined by Enumerable.) // 通过使用默认相等比较器比较值，生成两个序列的交集，消除重复项
   * // 这个方法返回两个序列中都存在的元素
   * // 相当于集合的交集操作（A ∩ B）
   * // 使用默认的相等比较器来判断元素是否相等
   * // 结果中不包含重复元素
   * // 这是一个延迟执行的方法
   * // 适用场景：找出两个集合的共同元素
   *
   * @param enumerable1 要计算交集的第二个序列
   * @return 交集序列
   */
  Enumerable<TSource> intersect(Enumerable<TSource> enumerable1); // 声明intersect方法的无参版本

  /**
   * Produces the set intersection of two sequences by
   * using the default equality comparer to compare values,
   * using {@code all} to indicate whether to eliminate duplicates.
   * (Defined by Enumerable.) // 通过使用默认相等比较器比较值，生成两个序列的交集，使用all指示是否消除重复项
   * // 与上一个方法类似，但可以通过all参数控制是否消除重复项
   * // 如果all为true，消除重复项（类似SQL的INTERSECT）
   * // 如果all为false，保留所有重复项（类似SQL的INTERSECT ALL）
   *
   * @param enumerable1 要计算交集的第二个序列
   * @param all 如果为true，消除重复项；如果为false，保留重复项
   * @return 交集序列
   */
  Enumerable<TSource> intersect(Enumerable<TSource> enumerable1, boolean all); // 声明intersect方法的带all参数版本

  /**
   * Produces the set intersection of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, eliminate duplicates. // 通过使用指定的相等比较器比较值，生成两个序列的交集，消除重复项
   * // 与第一个intersect方法类似，但可以使用自定义的相等比较器
   *
   * @param enumerable1 要计算交集的第二个序列
   * @param comparer 自定义的相等比较器
   * @return 交集序列
   */
  Enumerable<TSource> intersect(Enumerable<TSource> enumerable1, // 声明intersect方法的带比较器版本
      EqualityComparer<TSource> comparer);

  /**
   * Produces the set intersection of two sequences by
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, using {@code all} to indicate whether to eliminate duplicates. // 通过使用指定的相等比较器比较值，生成两个序列的交集，使用all指示是否消除重复项
   * // 这是intersect方法最完整的版本，支持自定义比较器和all参数
   *
   * @param enumerable1 要计算交集的第二个序列
   * @param comparer 自定义的相等比较器
   * @param all 如果为true，消除重复项；如果为false，保留重复项
   * @return 交集序列
   */
  Enumerable<TSource> intersect(Enumerable<TSource> enumerable1, // 声明intersect方法的完整版本
      EqualityComparer<TSource> comparer, boolean all);
  /**
   * Copies the contents of this sequence into a collection. // 将此序列的内容复制到集合中
   * // 这个方法将序列中的所有元素添加到指定的集合中
   * // 这是一个立即执行的方法
   * // 适用场景：将序列转换为集合、填充集合等
   *
   * @param sink 目标集合，元素将被添加到此集合
   * @param <C> 集合的类型，必须是Collection的子类，且元素类型必须是TSource或其父类型
   * @return 添加了元素后的集合（返回传入的同一个集合）
   */
  <C extends Collection<? super TSource>> C into(C sink); // 声明into方法

  /**
   * Removes the contents of this sequence from a collection. // 从集合中移除此序列的内容
   * // 这个方法从指定的集合中移除所有在序列中出现的元素
   * // 这是一个立即执行的方法
   * // 适用场景：批量删除集合中的元素
   *
   * @param sink 源集合，将从此集合中移除元素
   * @param <C> 集合的类型，必须是Collection的子类，且元素类型必须是TSource或其父类型
   * @return 移除了元素后的集合（返回传入的同一个集合）
   */
  <C extends Collection<? super TSource>> C removeAll(C sink); // 声明removeAll方法

  /**
   * Correlates the elements of two sequences based on
   * matching keys. The default equality comparer is used to compare
   * keys. // 基于匹配的键关联两个序列的元素。使用默认的相等比较器比较键
   * // 这是一个哈希连接操作，类似于SQL的INNER JOIN
   * // 使用哈希表来提高连接性能，适合处理大型数据集
   * // 工作流程：
   * // 1. 构建内序列的哈希表（键到元素列表的映射）
   * // 2. 遍历外序列，对每个元素查找哈希表中的匹配项
   * // 3. 使用结果选择器将匹配的元素对组合成结果
   * // 只返回两个序列中都存在匹配键的元素对
   * // 这是一个延迟执行的方法
   * // 适用场景：等值连接、主外键关联等
   *
   * @param inner 内序列，要连接的第二个序列
   * @param outerKeySelector 外键选择器函数，从外序列元素中提取键
   * @param innerKeySelector 内键选择器函数，从内序列元素中提取键
   * @param resultSelector 结果选择器函数，将匹配的元素对组合成结果
   * @param <TInner> 内序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TKey, TResult> Enumerable<TResult> hashJoin(Enumerable<TInner> inner, // 声明hashJoin方法的无参版本
      Function1<TSource, TKey> outerKeySelector, // 外键选择器
      Function1<TInner, TKey> innerKeySelector, // 内键选择器
      Function2<TSource, TInner, TResult> resultSelector); // 结果选择器

  /**
   * Correlates the elements of two sequences based on
   * matching keys. A specified {@code EqualityComparer<TSource>} is used to
   * compare keys. // 基于匹配的键关联两个序列的元素。使用指定的相等比较器比较键
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较键
   *
   * @param inner 内序列，要连接的第二个序列
   * @param outerKeySelector 外键选择器函数，从外序列元素中提取键
   * @param innerKeySelector 内键选择器函数，从内序列元素中提取键
   * @param resultSelector 结果选择器函数，将匹配的元素对组合成结果
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TInner> 内序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TKey, TResult> Enumerable<TResult> hashJoin(Enumerable<TInner> inner, // 声明hashJoin方法的带比较器版本
      Function1<TSource, TKey> outerKeySelector, // 外键选择器
      Function1<TInner, TKey> innerKeySelector, // 内键选择器
      Function2<TSource, TInner, TResult> resultSelector, // 结果选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Correlates elements of two sequences based on
   * - matching keys
   * - a comparator for timestamps. // 基于以下条件关联两个序列的元素：匹配的键和时间戳比较器
   * // 这是一个ASOF连接操作，用于基于时间戳的近似匹配
   // // ASOF连接常用于金融和时间序列数据处理
   * // 工作流程：
   * // 1. 首先按键匹配元素
   * // 2. 对于匹配的键，使用时间戳比较器找到最接近的外元素
   * // 3. 使用结果选择器组合元素
   * // 支持左连接（generateNullsOnRight为true时）
   * // 这是一个延迟执行的方法
   * // 适用场景：时间序列数据对齐、历史数据查找、金融数据处理等
   *
   * @param inner 内序列，要连接的第二个序列
   * @param outerKeySelector 函数，从外集合中提取键
   * @param innerKeySelector 函数，从内集合中提取键
   * @param resultSelector 函数，计算连接结果
   * @param matchComparator 函数，比较外行和内行的时间戳
   * @param timestampComparator 函数，比较两个内行的时间戳
   * @param generateNullsOnRight 如果为true，这是左连接
   * @param <TInner> 内序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TKey, TResult> Enumerable<TResult> asofJoin( // 声明asofJoin方法
      Enumerable<TInner> inner, // 内序列
      Function1<TSource, TKey> outerKeySelector, // 外键选择器
      Function1<TInner, TKey> innerKeySelector, // 内键选择器
      Function2<TSource, @Nullable TInner, TResult> resultSelector, // 结果选择器
      Predicate2<TSource, TInner> matchComparator, // 行匹配比较器
      Comparator<TInner>  timestampComparator, // 时间戳比较器
      boolean generateNullsOnRight); // 是否生成右侧null值

  /**
   * Correlates the elements of two sequences based on matching keys, with
   * optional outer join semantics. A specified
   * {@code EqualityComparer<TSource>} is used to compare keys. // 基于匹配的键关联两个序列的元素，支持可选的外连接语义。使用指定的相等比较器比较键
   * // 这是一个支持多种连接类型的哈希连接方法
   * // 通过generateNullsOnLeft和generateNullsOnRight参数控制连接类型
   * // 这是一个延迟执行的方法
   *
   * <p>A left join generates nulls on right, and vice versa: // 左连接在右侧生成null值，反之亦然
   *
   * <table>
   *   <caption>Join types</caption> // 连接类型表
   *   <tr>
   *     <td>Join type</td> // 连接类型
   *     <td>generateNullsOnLeft</td> // 在左侧生成null
   *     <td>generateNullsOnRight</td> // 在右侧生成null
   *   </tr>
   *   <tr><td>INNER</td><td>false</td><td>false</td></tr> // 内连接
   *   <tr><td>LEFT</td><td>false</td><td>true</td></tr> // 左连接
   *   <tr><td>RIGHT</td><td>true</td><td>false</td></tr> // 右连接
   *   <tr><td>FULL</td><td>true</td><td>true</td></tr> // 全连接
   * </table>
   *
   * @param inner 内序列，要连接的第二个序列
   * @param outerKeySelector 外键选择器函数，从外序列元素中提取键
   * @param innerKeySelector 内键选择器函数，从内序列元素中提取键
   * @param resultSelector 结果选择器函数，将匹配的元素对组合成结果
   * @param comparer 自定义的相等比较器，用于比较键
   * @param generateNullsOnLeft 如果为true，在外序列没有匹配时生成null
   * @param generateNullsOnRight 如果为true，在内序列没有匹配时生成null
   * @param <TInner> 内序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TKey, TResult> Enumerable<TResult> hashJoin(Enumerable<TInner> inner, // 声明支持多种连接类型的hashJoin方法
      Function1<TSource, TKey> outerKeySelector, // 外键选择器
      Function1<TInner, TKey> innerKeySelector, // 内键选择器
      Function2<TSource, TInner, TResult> resultSelector, // 结果选择器
      @Nullable EqualityComparer<TKey> comparer, // 键比较器
      boolean generateNullsOnLeft, boolean generateNullsOnRight); // 控制连接类型的参数

  /**
   * Correlates the elements of two sequences based on matching keys, with
   * optional outer join semantics. A specified
   * {@code EqualityComparer<TSource>} is used to compare keys. // 基于匹配的键关联两个序列的元素，支持可选的外连接语义。使用指定的相等比较器比较键
   * // 与上一个方法类似，但还支持行级别的谓词过滤
   * // 在连接结果上应用谓词，进一步过滤匹配的元素对
   *
   * <p>A left join generates nulls on right, and vice versa: // 左连接在右侧生成null值，反之亦然
   *
   * <table>
   *   <caption>Join types</caption> // 连接类型表
   *   <tr>
   *     <td>Join type</td> // 连接类型
   *     <td>generateNullsOnLeft</td> // 在左侧生成null
   *     <td>generateNullsOnRight</td> // 在右侧生成null
   *   </tr>
   *   <tr><td>INNER</td><td>false</td><td>false</td></tr> // 内连接
   *   <tr><td>LEFT</td><td>false</td><td>true</td></tr> // 左连接
   *   <tr><td>RIGHT</td><td>true</td><td>false</td></tr> // 右连接
   *   <tr><td>FULL</td><td>true</td><td>true</td></tr> // 全连接
   * </table>
   *
   * <p>A predicate is used to filter the join result per-row // 使用谓词逐行过滤连接结果
   * // 谓词函数对每个匹配的元素对进行评估，只有满足条件的元素对才会被包含在结果中
   *
   * @param inner 内序列，要连接的第二个序列
   * @param outerKeySelector 外键选择器函数，从外序列元素中提取键
   * @param innerKeySelector 内键选择器函数，从内序列元素中提取键
   * @param resultSelector 结果选择器函数，将匹配的元素对组合成结果
   * @param comparer 自定义的相等比较器，用于比较键
   * @param generateNullsOnLeft 如果为true，在外序列没有匹配时生成null
   * @param generateNullsOnRight 如果为true，在内序列没有匹配时生成null
   * @param predicate 谓词函数，用于过滤连接结果
   * @param <TInner> 内序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TKey, TResult> Enumerable<TResult> hashJoin(Enumerable<TInner> inner, // 声明带谓词过滤的hashJoin方法
      Function1<TSource, TKey> outerKeySelector, // 外键选择器
      Function1<TInner, TKey> innerKeySelector, // 内键选择器
      Function2<TSource, TInner, TResult> resultSelector, // 结果选择器
      EqualityComparer<TKey> comparer, // 键比较器
      boolean generateNullsOnLeft, boolean generateNullsOnRight, // 控制连接类型的参数
      Predicate2<TSource, TInner> predicate); // 谓词函数

  /**
   * For each row of the current enumerable returns the correlated rows
   * from the {@code inner} enumerable (nested loops join). // 对于当前可枚举的每一行，从内可枚举中返回相关行（嵌套循环连接）
   * // 这是一个嵌套循环连接操作，类似于SQL的CORRELATED SUBQUERY
   * // 工作流程：
   * // 1. 遍历外序列的每个元素
   * // 2. 对每个元素，调用inner函数生成一个内序列
   * // 3. 对内序列的每个元素，使用结果选择器组合成结果
   * // 支持多种连接类型：内连接、左连接、半连接、反连接
   * // 这是一个延迟执行的方法
   * // 适用场景：相关子查询、复杂条件连接等
   *
   * @param joinType 连接类型：inner、left、semi或anti
   * @param inner 内可枚举的生成器，函数接收外元素，返回内序列
   * @param resultSelector 结果选择器。对于半连接/反连接，inner参数始终为null
   * @param <TInner> 内序列元素的类型
   * @param <TResult> 结果的类型
   * @return 连接后的序列
   */
  <TInner, TResult> Enumerable<TResult> correlateJoin( // 声明correlateJoin方法
      JoinType joinType, Function1<TSource, Enumerable<TInner>> inner, // 连接类型和内序列生成器
      Function2<TSource, TInner, TResult> resultSelector); // 结果选择器

  /**
   * Returns the last element of a sequence. (Defined
   * by Enumerable.) // 返回序列的最后一个元素
   * // 这个方法返回序列中的最后一个元素
   * // 如果序列为空，抛出NoSuchElementException
   * // 这是一个立即执行的方法
   * // 适用场景：获取序列的最后一个元素
   *
   * @return 序列的最后一个元素
   */
  TSource last(); // 声明last方法的无参版本

  /**
   * Returns the last element of a sequence that
   * satisfies a specified condition. // 返回序列中满足指定条件的最后一个元素
   * // 这个方法返回序列中最后一个满足指定条件的元素
   * // 如果序列为空或没有元素满足条件，抛出NoSuchElementException
   * // 这是一个立即执行的方法
   * // 适用场景：查找满足特定条件的最后一个元素
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 最后一个满足条件的元素
   */
  TSource last(Predicate1<TSource> predicate); // 声明last方法的带谓词版本

  /**
   * Returns the last element of a sequence, or a
   * default value if the sequence contains no elements. // 返回序列的最后一个元素，如果序列不包含任何元素，则返回默认值
   * // 与last方法类似，但如果序列为空，返回默认值而不是抛出异常
   * // 对于引用类型，默认值为null；对于值类型，默认值为0或false等
   * // 这是一个立即执行的方法
   * // 适用场景：安全地获取序列的最后一个元素
   *
   * @return 序列的最后一个元素，如果序列为空则返回默认值
   */
  @Nullable TSource lastOrDefault(); // 声明lastOrDefault方法的无参版本

  /**
   * Returns the last element of a sequence that
   * satisfies a condition or a default value if no such element is
   * found. // 返回序列中满足条件的最后一个元素，如果没有找到此类元素，则返回默认值
   * // 与last(predicate)方法类似，但如果序列为空或没有元素满足条件，返回默认值而不是抛出异常
   * // 这是一个立即执行的方法
   * // 适用场景：安全地查找满足特定条件的最后一个元素
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 最后一个满足条件的元素，如果没有找到则返回默认值
   */
  @Nullable TSource lastOrDefault(Predicate1<TSource> predicate); // 声明lastOrDefault方法的带谓词版本

  /**
   * Returns an long that represents the total number
   * of elements in a sequence. // 返回一个long值，表示序列中的元素总数
   * // 与count方法类似，但返回long类型，可以处理更大的数量
   * // 如果序列为空，返回0
   * // 这是一个立即执行的方法
   * // 适用场景：获取大型数据集的大小
   *
   * @return 序列中的元素总数
   */
  long longCount(); // 声明longCount方法的无参版本

  /**
   * Returns an long that represents how many elements
   * in a sequence satisfy a condition. // 返回一个long值，表示序列中有多少元素满足条件
   * // 与count(predicate)方法类似，但返回long类型
   * // 如果序列为空或没有元素满足条件，返回0
   * // 这是一个立即执行的方法
   * // 适用场景：统计大型数据集中满足条件的元素数量
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 满足条件的元素数量
   */
  long longCount(Predicate1<TSource> predicate); // 声明longCount方法的带谓词版本

  /**
   * Returns the maximum value in a generic
   * sequence. // 返回泛型序列中的最大值
   * // 这个方法返回序列中的最大元素
   * // 元素必须实现Comparable接口
   * // 如果序列为空，返回null
   * // 这是一个立即执行的方法
   * // 适用场景：查找序列中的最大值
   *
   * @return 序列中的最大值，如果序列为空则返回null
   */
  @Nullable TSource max(); // 声明max方法的无参版本

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum Decimal value. // 对序列的每个元素调用转换函数，并返回最大Decimal值
   * // 首先对每个元素应用选择器函数，提取出BigDecimal值，然后找出这些值中的最大值
   * // 如果序列为空，返回null
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取BigDecimal值
   * @return 最大值，如果序列为空则返回null
   */
  @Nullable BigDecimal max(BigDecimalFunction1<TSource> selector); // 声明计算BigDecimal最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable Decimal
   * value. // 对序列的每个元素调用转换函数，并返回最大可空Decimal值
   * // 与上一个方法类似，但处理的是可空的BigDecimal值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的BigDecimal值
   * @return 最大值，如果序列为空或所有值都为null则返回null
   */
  @Nullable BigDecimal max(NullableBigDecimalFunction1<TSource> selector); // 声明计算可空BigDecimal最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum Double value. // 对序列的每个元素调用转换函数，并返回最大Double值
   * // 首先对每个元素应用选择器函数，提取出double值，然后找出这些值中的最大值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取double值
   * @return 最大值
   */
  double max(DoubleFunction1<TSource> selector); // 声明计算double最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable Double
   * value. // 对序列的每个元素调用转换函数，并返回最大可空Double值
   * // 计算可空Double类型值的最大值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的Double值
   * @return 最大值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Double max(NullableDoubleFunction1<TSource> selector); // 声明计算可空Double最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum int value. // 对序列的每个元素调用转换函数，并返回最大int值
   * // 首先对每个元素应用选择器函数，提取出int值，然后找出这些值中的最大值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取int值
   * @return 最大值
   */
  int max(IntegerFunction1<TSource> selector); // 声明计算int最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable int value. (Defined
   * by Enumerable.) // 对序列的每个元素调用转换函数，并返回最大可空int值
   * // 计算可空Integer类型值的最大值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的int值
   * @return 最大值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Integer max(NullableIntegerFunction1<TSource> selector); // 声明计算可空Integer最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum long value. // 对序列的每个元素调用转换函数，并返回最大long值
   * // 首先对每个元素应用选择器函数，提取出long值，然后找出这些值中的最大值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取long值
   * @return 最大值
   */
  long max(LongFunction1<TSource> selector); // 声明计算long最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable long value. (Defined
   * by Enumerable.) // 对序列的每个元素调用转换函数，并返回最大可空long值
   * // 计算可空Long类型值的最大值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的long值
   * @return 最大值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Long max(NullableLongFunction1<TSource> selector); // 声明计算可空Long最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum Float value. // 对序列的每个元素调用转换函数，并返回最大Float值
   * // 首先对每个元素应用选择器函数，提取出float值，然后找出这些值中的最大值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取Float值
   * @return 最大值
   */
  float max(FloatFunction1<TSource> selector); // 声明计算float最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the maximum nullable Float
   * value. // 对序列的每个元素调用转换函数，并返回最大可空Float值
   * // 计算可空Float类型值的最大值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的Float值
   * @return 最大值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Float max(NullableFloatFunction1<TSource> selector); // 声明计算可空Float最大值的max方法

  /**
   * Invokes a transform function on each element of a
   * generic sequence and returns the maximum resulting
   * value. // 对泛型序列的每个元素调用转换函数，并返回最大结果值
   * // 这是最通用的max方法，适用于任何实现了Comparable接口的类型
   * // 首先对每个元素应用选择器函数，提取出Comparable值，然后找出这些值中的最大值
   * // 如果序列为空，返回null
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取Comparable值
   * @param <TResult> 结果类型，必须实现Comparable接口
   * @return 最大值，如果序列为空则返回null
   */
  <TResult extends Comparable<TResult>> @Nullable TResult max( // 声明计算Comparable类型最大值的max方法
      Function1<TSource, TResult> selector); // 选择器函数

  /**
   * Returns the minimum value in a generic
   * sequence. // 返回泛型序列中的最小值
   * // 这个方法返回序列中的最小元素
   * // 元素必须实现Comparable接口
   * // 如果序列为空，返回null
   * // 这是一个立即执行的方法
   * // 适用场景：查找序列中的最小值
   *
   * @return 序列中的最小值，如果序列为空则返回null
   */
  @Nullable TSource min(); // 声明min方法的无参版本

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum Decimal value. // 对序列的每个元素调用转换函数，并返回最小Decimal值
   * // 首先对每个元素应用选择器函数，提取出BigDecimal值，然后找出这些值中的最小值
   * // 如果序列为空，返回null
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取BigDecimal值
   * @return 最小值，如果序列为空则返回null
   */
  @Nullable BigDecimal min(BigDecimalFunction1<TSource> selector); // 声明计算BigDecimal最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable Decimal
   * value. // 对序列的每个元素调用转换函数，并返回最小可空Decimal值
   * // 与上一个方法类似，但处理的是可空的BigDecimal值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的BigDecimal值
   * @return 最小值，如果序列为空或所有值都为null则返回null
   */
  @Nullable BigDecimal min(NullableBigDecimalFunction1<TSource> selector); // 声明计算可空BigDecimal最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum Double value. // 对序列的每个元素调用转换函数，并返回最小Double值
   * // 首先对每个元素应用选择器函数，提取出double值，然后找出这些值中的最小值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取double值
   * @return 最小值
   */
  double min(DoubleFunction1<TSource> selector); // 声明计算double最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable Double
   * value. // 对序列的每个元素调用转换函数，并返回最小可空Double值
   * // 计算可空Double类型值的最小值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的Double值
   * @return 最小值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Double min(NullableDoubleFunction1<TSource> selector); // 声明计算可空Double最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum int value. // 对序列的每个元素调用转换函数，并返回最小int值
   * // 首先对每个元素应用选择器函数，提取出int值，然后找出这些值中的最小值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取int值
   * @return 最小值
   */
  int min(IntegerFunction1<TSource> selector); // 声明计算int最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable int value. (Defined
   * by Enumerable.) // 对序列的每个元素调用转换函数，并返回最小可空int值
   * // 计算可空Integer类型值的最小值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的int值
   * @return 最小值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Integer min(NullableIntegerFunction1<TSource> selector); // 声明计算可空Integer最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum long value. // 对序列的每个元素调用转换函数，并返回最小long值
   * // 首先对每个元素应用选择器函数，提取出long值，然后找出这些值中的最小值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取long值
   * @return 最小值
   */
  long min(LongFunction1<TSource> selector); // 声明计算long最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable long value. (Defined
   * by Enumerable.) // 对序列的每个元素调用转换函数，并返回最小可空long值
   * // 计算可空Long类型值的最小值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的long值
   * @return 最小值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Long min(NullableLongFunction1<TSource> selector); // 声明计算可空Long最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum Float value. // 对序列的每个元素调用转换函数，并返回最小Float值
   * // 首先对每个元素应用选择器函数，提取出float值，然后找出这些值中的最小值
   * // 如果序列为空，抛出异常
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取Float值
   * @return 最小值
   */
  float min(FloatFunction1<TSource> selector); // 声明计算float最小值的min方法

  /**
   * Invokes a transform function on each element of a
   * sequence and returns the minimum nullable Float
   * value. // 对序列的每个元素调用转换函数，并返回最小可空Float值
   * // 计算可空Float类型值的最小值
   * // 如果序列为空或所有值都为null，返回null
   *
   * @param selector 转换函数，从每个元素中提取可空的Float值
   * @return 最小值，如果序列为空或所有值都为null则返回null
   */
  @Nullable Float min(NullableFloatFunction1<TSource> selector); // 声明计算可空Float最小值的min方法

  /**

     * Invokes a transform function on each element of a

  

     * generic sequence and returns the minimum resulting

     * value. // 对泛型序列的每个元素调用转换函数，并返回最小结果值

     * // 这是最通用的min方法，适用于任何实现了Comparable接口的类型

     * // 首先对每个元素应用选择器函数，提取出Comparable值，然后找出这些值中的最小值

     * // 如果序列为空，返回null

     * // 这是一个立即执行的方法

     *

     * @param selector 转换函数，从每个元素中提取Comparable值

     * @param <TResult> 结果类型，必须实现Comparable接口

     * @return 最小值，如果序列为空则返回null

     */

    <TResult extends Comparable<TResult>> @Nullable TResult min( // 声明计算Comparable类型最小值的min方法

        Function1<TSource, TResult> selector); // 选择器函数

  

    /**

     * Filters the elements of an Enumerable based on a

     * specified type. // 根据指定类型过滤Enumerable的元素

     * // 这个方法只保留可以转换为指定类型的元素

     * // 与cast方法不同，ofType不会抛出异常，而是过滤掉无法转换的元素

     * // 这是一个延迟执行的方法

     * // 适用场景：过滤特定类型的对象、类型安全的数据处理等

     *

     * <p>Analogous to LINQ's Enumerable.OfType extension method. // 类似于LINQ的Enumerable.OfType扩展方法

     *

     * @param clazz 目标类型

     * @param <TResult> 目标类型

     *

     * @return Collection of T2 // T2的集合

     */

    <TResult> Enumerable<TResult> ofType(Class<TResult> clazz); // 声明ofType方法

  

    /**

     * Sorts the elements of a sequence in ascending

     * order according to a key. // 根据键按升序对序列的元素进行排序

     * // 这个方法根据指定的键对序列中的元素进行升序排序

     * // 键必须实现Comparable接口

     * // 这是一个延迟执行的方法

     * // 适用场景：数据排序、按字段排序等

     *

     * @param keySelector 键选择器函数，从每个元素中提取排序键

     * @param <TKey> 键的类型，必须实现Comparable接口

     * @return 排序后的序列

     */

    <TKey extends Comparable> Enumerable<TSource> orderBy( // 声明orderBy方法的无参版本

        Function1<TSource, TKey> keySelector); // 键选择器函数

  

    /**

     * Sorts the elements of a sequence in ascending

     * order by using a specified comparer. // 使用指定的比较器按升序对序列的元素进行排序

     * // 与上一个方法类似，但可以使用自定义的比较器来比较键

     * // 这允许使用不同的排序逻辑

     *

     * @param keySelector 键选择器函数，从每个元素中提取排序键

     * @param comparator 自定义的比较器，用于比较键

     * @param <TKey> 键的类型

     * @return 排序后的序列

     */

    <TKey> Enumerable<TSource> orderBy(Function1<TSource, TKey> keySelector, // 声明orderBy方法的带比较器版本

        Comparator<TKey> comparator); // 键比较器

  

    /**

     * Sorts the elements of a sequence in descending

     * order according to a key. // 根据键按降序对序列的元素进行排序

     * // 这个方法根据指定的键对序列中的元素进行降序排序

     * // 键必须实现Comparable接口

     * // 这是一个延迟执行的方法

     * // 适用场景：数据降序排序、按字段降序排序等

     *

     * @param keySelector 键选择器函数，从每个元素中提取排序键

     * @param <TKey> 键的类型，必须实现Comparable接口

     * @return 排序后的序列

     */

    <TKey extends Comparable> Enumerable<TSource> orderByDescending( // 声明orderByDescending方法的无参版本

        Function1<TSource, TKey> keySelector); // 键选择器函数

  

    /**

     * Sorts the elements of a sequence in descending

     * order by using a specified comparer. // 使用指定的比较器按降序对序列的元素进行排序

     * // 与上一个方法类似，但可以使用自定义的比较器来比较键

     *

     * @param keySelector 键选择器函数，从每个元素中提取排序键

     * @param comparator 自定义的比较器，用于比较键

     * @param <TKey> 键的类型

     * @return 排序后的序列

     */

    <TKey> Enumerable<TSource> orderByDescending( // 声明orderByDescending方法的带比较器版本

        Function1<TSource, TKey> keySelector, Comparator<TKey> comparator); // 键选择器和比较器

  

    /**

     * Inverts the order of the elements in a

     * sequence. // 反转序列中元素的顺序

     * // 这个方法将序列中的元素顺序颠倒

     * // 这是一个延迟执行的方法

     * // 适用场景：倒序显示、反向遍历等

     *

     * @return 反转后的序列

     */

    Enumerable<TSource> reverse(); // 声明reverse方法

  /**
   * Projects each element of a sequence into a new
   * form. // 将序列的每个元素投影到一个新形式
   * // 这个方法对序列中的每个元素应用转换函数，生成一个新的序列
   * // 类似于SQL的SELECT子句，可以转换、提取或计算元素的值
   * // 这是一个延迟执行的方法
   * // 适用场景：数据转换、属性提取、计算派生值等
   *
   * @param selector 转换函数，接收一个元素，返回转换后的结果
   * @param <TResult> 结果的类型
   * @return 投影后的序列
   */
  <TResult> Enumerable<TResult> select(Function1<TSource, TResult> selector); // 声明select方法的无参版本

  /**
   * Projects each element of a sequence into a new
   * form by incorporating the element's index. // 通过包含元素的索引将序列的每个元素投影到一个新形式
   * // 与上一个方法类似，但转换函数还可以接收元素的索引
   * // 索引从0开始
   * // 适用场景：需要索引信息的转换、添加序号等
   *
   * @param selector 转换函数，接收元素和索引，返回转换后的结果
   * @param <TResult> 结果的类型
   * @return 投影后的序列
   */
  <TResult> Enumerable<TResult> select( // 声明select方法的带索引版本
      Function2<TSource, Integer, TResult> selector); // 转换函数，接收元素和索引

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>} and flattens the resulting sequences into one
   * sequence. // 将序列的每个元素投影到一个Enumerable，并将结果序列扁平化为一个序列
   * // 这个方法将序列中的每个元素转换为一个序列，然后将所有这些序列合并成一个扁平的序列
   * // 类似于SQL的JOIN或UNION ALL操作
   * // 这是一个延迟执行的方法
   * // 适用场景：一对多关系展开、嵌套集合扁平化等
   *
   * @param selector 转换函数，接收一个元素，返回一个序列
   * @param <TResult> 结果序列中元素的类型
   * @return 扁平化后的序列
   */
  <TResult> Enumerable<TResult> selectMany( // 声明selectMany方法的无参版本
      Function1<TSource, Enumerable<TResult>> selector); // 转换函数，返回序列

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>}, and flattens the resulting sequences into one
   * sequence. The index of each source element is used in the
   * projected form of that element. // 将序列的每个元素投影到一个Enumerable，并将结果序列扁平化为一个序列。每个源元素的索引用于该元素的投影形式
   * // 与上一个方法类似，但转换函数还可以接收元素的索引
   *
   * @param selector 转换函数，接收元素和索引，返回一个序列
   * @param <TResult> 结果序列中元素的类型
   * @return 扁平化后的序列
   */
  <TResult> Enumerable<TResult> selectMany( // 声明selectMany方法的带索引版本
      Function2<TSource, Integer, Enumerable<TResult>> selector); // 转换函数，接收元素和索引

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>}, flattens the resulting sequences into one
   * sequence, and invokes a result selector function on each
   * element therein. The index of each source element is used in
   * the intermediate projected form of that element. // 将序列的每个元素投影到一个Enumerable，将结果序列扁平化为一个序列，并对其中的每个元素调用结果选择器函数。每个源元素的索引用于该元素的中间投影形式
   * // 这是selectMany方法最灵活的版本，支持索引和结果选择器
   * // 工作流程：
   * // 1. 对每个元素应用集合选择器，生成一个序列
   * // 2. 扁平化所有序列
   * // 3. 对每个元素应用结果选择器，将源元素和集合元素组合成结果
   *
   * @param collectionSelector 集合选择器函数，接收元素和索引，返回一个序列
   * @param resultSelector 结果选择器函数，接收源元素和集合元素，返回结果
   * @param <TCollection> 中间序列中元素的类型
   * @param <TResult> 结果的类型
   * @return 扁平化并转换后的序列
   */
  <TCollection, TResult> Enumerable<TResult> selectMany( // 声明selectMany方法的完整版本
      Function2<TSource, Integer, Enumerable<TCollection>> collectionSelector, // 集合选择器
      Function2<TSource, TCollection, TResult> resultSelector); // 结果选择器

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<TSource>}, flattens the resulting sequences into one
   * sequence, and invokes a result selector function on each
   * element therein. // 将序列的每个元素投影到一个Enumerable，将结果序列扁平化为一个序列，并对其中的每个元素调用结果选择器函数
   * // 与上一个方法类似，但不使用索引
   *
   * @param collectionSelector 集合选择器函数，接收元素，返回一个序列
   * @param resultSelector 结果选择器函数，接收源元素和集合元素，返回结果
   * @param <TCollection> 中间序列中元素的类型
   * @param <TResult> 结果的类型
   * @return 扁平化并转换后的序列
   */
  <TCollection, TResult> Enumerable<TResult> selectMany( // 声明selectMany方法的带结果选择器版本
      Function1<TSource, Enumerable<TCollection>> collectionSelector, // 集合选择器
      Function2<TSource, TCollection, TResult> resultSelector); // 结果选择器

  /**
   * Determines whether two sequences are equal by
   * comparing the elements by using the default equality comparer
   * for their type. // 通过使用其类型的默认相等比较器比较元素，确定两个序列是否相等
   * // 这个方法比较两个序列是否完全相等
   * // 相等的条件：两个序列具有相同数量的元素，且对应位置的元素相等
   * // 使用默认的相等比较器（通常是equals方法）来比较元素
   * // 这是一个立即执行的方法
   * // 适用场景：验证两个序列是否相同、数据一致性检查等
   *
   * @param enumerable1 要比较的第二个序列
   * @return 如果两个序列相等，返回true；否则返回false
   */
  boolean sequenceEqual(Enumerable<TSource> enumerable1); // 声明sequenceEqual方法的无参版本

  /**
   * Determines whether two sequences are equal by
   * comparing their elements by using a specified
   * {@code EqualityComparer<TSource>}. // 通过使用指定的相等比较器比较元素，确定两个序列是否相等
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较元素
   *
   * @param enumerable1 要比较的第二个序列
   * @param comparer 自定义的相等比较器
   * @return 如果两个序列相等，返回true；否则返回false
   */
  boolean sequenceEqual(Enumerable<TSource> enumerable1, // 声明sequenceEqual方法的带比较器版本
      EqualityComparer<TSource> comparer); // 相等比较器

  /**
   * Returns the only element of a sequence, and throws
   * an exception if there is not exactly one element in the
   * sequence. // 返回序列的唯一元素，如果序列中不恰好有一个元素，则抛出异常
   * // 这个方法返回序列中的唯一元素
   * // 如果序列为空或包含多个元素，抛出NoSuchElementException
   * // 这是一个立即执行的方法
   * // 适用场景：获取序列中唯一的元素，验证序列只包含一个元素
   *
   * @return 序列中的唯一元素
   */
  TSource single(); // 声明single方法的无参版本

  /**
   * Returns the only element of a sequence that
   * satisfies a specified condition, and throws an exception if
   * more than one such element exists. // 返回序列中满足指定条件的唯一元素，如果存在多个此类元素，则抛出异常
   * // 这个方法返回序列中唯一满足指定条件的元素
   * // 如果序列为空、没有元素满足条件或有多个元素满足条件，抛出NoSuchElementException
   * // 这是一个立即执行的方法
   * // 适用场景：获取序列中唯一满足条件的元素
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 唯一满足条件的元素
   */
  TSource single(Predicate1<TSource> predicate); // 声明single方法的带谓词版本

  /**
   * Returns the only element of a sequence, or a
   * default value if the sequence is empty; this method throws an
   * exception if there is more than one element in the
   * sequence. // 返回序列的唯一元素，如果序列为空，则返回默认值；如果序列中有多个元素，此方法抛出异常
   * // 与single方法类似，但如果序列为空，返回默认值而不是抛出异常
   * // 如果序列包含多个元素，仍然抛出NoSuchElementException
   * // 对于引用类型，默认值为null；对于值类型，默认值为0或false等
   * // 这是一个立即执行的方法
   *
   * @return 序列中的唯一元素，如果序列为空则返回默认值
   */
  @Nullable TSource singleOrDefault(); // 声明singleOrDefault方法的无参版本

  /**
   * Returns the only element of a sequence that
   * satisfies a specified condition or a default value if no such
   * element exists; this method throws an exception if more than
   * one element satisfies the condition. // 返回序列中满足指定条件的唯一元素，如果不存在此类元素，则返回默认值；如果有多个元素满足条件，此方法抛出异常
   * // 与single(predicate)方法类似，但如果序列为空或没有元素满足条件，返回默认值而不是抛出异常
   * // 如果有多个元素满足条件，仍然抛出NoSuchElementException
   * // 这是一个立即执行的方法
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否满足条件
   * @return 唯一满足条件的元素，如果没有找到则返回默认值
   */
  @Nullable TSource singleOrDefault(Predicate1<TSource> predicate); // 声明singleOrDefault方法的带谓词版本

  /**
   * Bypasses a specified number of elements in a
   * sequence and then returns the remaining elements. // 跳过序列中指定数量的元素，然后返回剩余的元素
   * // 这个方法跳过序列开头的指定数量的元素，返回剩余的元素
   * // 如果跳过的数量大于或等于序列长度，返回空序列
   * // 这是一个延迟执行的方法
   * // 适用场景：分页、跳过已知的前N个元素等
   *
   * @param count 要跳过的元素数量
   * @return 跳过指定数量元素后的序列
   */
  Enumerable<TSource> skip(int count); // 声明skip方法

  /**
   * Bypasses elements in a sequence as long as a
   * specified condition is true and then returns the remaining
   * elements. // 跳过序列中满足指定条件的元素，直到条件为false，然后返回剩余的元素
   * // 这个方法跳过序列开头满足条件的元素，直到遇到第一个不满足条件的元素，然后返回剩余的元素
   * // 如果所有元素都满足条件，返回空序列
   * // 这是一个延迟执行的方法
   * // 适用场景：跳过满足特定条件的前导元素等
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否跳过该元素
   * @return 跳过满足条件元素后的序列
   */
  Enumerable<TSource> skipWhile(Predicate1<TSource> predicate); // 声明skipWhile方法的无参版本

  /**
   * Bypasses elements in a sequence as long as a
   * specified condition is true and then returns the remaining
   * elements. The element's index is used in the logic of the
   * predicate function. // 跳过序列中满足指定条件的元素，直到条件为false，然后返回剩余的元素。元素的索引用于谓词函数的逻辑
   * // 与上一个方法类似，但谓词函数还可以接收元素的索引
   *
   * @param predicate 谓词函数，接收元素和索引，返回布尔值表示是否跳过该元素
   * @return 跳过满足条件元素后的序列
   */
  Enumerable<TSource> skipWhile(Predicate2<TSource, Integer> predicate); // 声明skipWhile方法的带索引版本

  /**
   * Computes the sum of the sequence of Decimal values
   * that are obtained by invoking a transform function on each
   * element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的Decimal值序列的总和
   * // 这是一个聚合操作，计算序列中元素的总和
   * // 首先对每个元素应用选择器函数，提取出BigDecimal值，然后计算这些值的总和
   * // 如果序列为空，返回0
   * // 这是一个立即执行的方法
   * // 适用场景：计算数值型数据的总和
   *
   * @param selector 转换函数，从每个元素中提取BigDecimal值
   * @return 总和
   */
  BigDecimal sum(BigDecimalFunction1<TSource> selector); // 声明计算BigDecimal总和的sum方法

  /**
   * Computes the sum of the sequence of nullable
   * Decimal values that are obtained by invoking a transform
   * function on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空Decimal值序列的总和
   * // 与上一个方法类似，但处理的是可空的BigDecimal值
   * // 如果序列为空或所有值都为null，返回0
   * // 计算总和时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的BigDecimal值
   * @return 总和
   */
  BigDecimal sum(NullableBigDecimalFunction1<TSource> selector); // 声明计算可空BigDecimal总和的sum方法

  /**
   * Computes the sum of the sequence of Double values
   * that are obtained by invoking a transform function on each
   * element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的Double值序列的总和
   * // 计算Double类型值的总和
   * // 如果序列为空，返回0.0
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取double值
   * @return 总和
   */
  double sum(DoubleFunction1<TSource> selector); // 声明计算double总和的sum方法

  /**
   * Computes the sum of the sequence of nullable
   * Double values that are obtained by invoking a transform
   * function on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空Double值序列的总和
   * // 计算可空Double类型值的总和
   * // 如果序列为空或所有值都为null，返回0.0
   * // 计算总和时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的Double值
   * @return 总和
   */
  Double sum(NullableDoubleFunction1<TSource> selector); // 声明计算可空Double总和的sum方法

  /**
   * Computes the sum of the sequence of int values
   * that are obtained by invoking a transform function on each
   * element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的int值序列的总和
   * // 计算int类型值的总和
   * // 如果序列为空，返回0
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取int值
   * @return 总和
   */
  int sum(IntegerFunction1<TSource> selector); // 声明计算int总和的sum方法

  /**
   * Computes the sum of the sequence of nullable int
   * values that are obtained by invoking a transform function
   * on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空int值序列的总和
   * // 计算可空Integer类型值的总和
   * // 如果序列为空或所有值都为null，返回0
   * // 计算总和时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的int值
   * @return 总和
   */
  Integer sum(NullableIntegerFunction1<TSource> selector); // 声明计算可空Integer总和的sum方法

  /**
   * Computes the sum of the sequence of long values
   * that are obtained by invoking a transform function on each
   * element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的long值序列的总和
   * // 计算long类型值的总和
   * // 如果序列为空，返回0
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取long值
   * @return 总和
   */
  long sum(LongFunction1<TSource> selector); // 声明计算long总和的sum方法

  /**
   * Computes the sum of the sequence of nullable long
   * values that are obtained by invoking a transform function
   * on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空long值序列的总和
   * // 计算可空Long类型值的总和
   * // 如果序列为空或所有值都为null，返回0
   * // 计算总和时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的long值
   * @return 总和
   */
  Long sum(NullableLongFunction1<TSource> selector); // 声明计算可空Long总和的sum方法

  /**
   * Computes the sum of the sequence of Float values
   * that are obtained by invoking a transform function on each
   * element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的Float值序列的总和
   * // 计算Float类型值的总和
   * // 如果序列为空，返回0.0
   * // 这是一个立即执行的方法
   *
   * @param selector 转换函数，从每个元素中提取Float值
   * @return 总和
   */
  float sum(FloatFunction1<TSource> selector); // 声明计算float总和的sum方法

  /**
   * Computes the sum of the sequence of nullable
   * Float values that are obtained by invoking a transform
   * function on each element of the input sequence. // 计算通过对输入序列的每个元素调用转换函数获得的可空Float值序列的总和
   * // 计算可空Float类型值的总和
   * // 如果序列为空或所有值都为null，返回0.0
   * // 计算总和时会忽略null值
   *
   * @param selector 转换函数，从每个元素中提取可空的Float值
   * @return 总和
   */
  Float sum(NullableFloatFunction1<TSource> selector); // 声明计算可空Float总和的sum方法

  /**
   * Returns a specified number of contiguous elements
   * from the start of a sequence. // 从序列的开头返回指定数量的连续元素
   * // 这个方法返回序列开头的指定数量的元素
   * // 如果序列的元素数量小于指定的数量，返回整个序列
   * // 这是一个延迟执行的方法
   * // 适用场景：分页、获取前N个元素等
   *
   * @param count 要返回的元素数量
   * @return 包含指定数量元素的序列
   */
  Enumerable<TSource> take(int count); // 声明take方法

  /**
   * Returns elements from a sequence as long as a
   * specified condition is true. // 只要满足指定条件，就从序列中返回元素
   * // 这个方法返回序列开头满足条件的元素，直到遇到第一个不满足条件的元素
   * // 如果第一个元素就不满足条件，返回空序列
   * // 这是一个延迟执行的方法
   * // 适用场景：获取满足特定条件的前导元素等
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否包含该元素
   * @return 满足条件的元素序列
   */
  Enumerable<TSource> takeWhile(Predicate1<TSource> predicate); // 声明takeWhile方法的无参版本

  /**
   * Returns elements from a sequence as long as a
   * specified condition is true. The element's index is used in the
   * logic of the predicate function. // 只要满足指定条件，就从序列中返回元素。元素的索引用于谓词函数的逻辑
   * // 与上一个方法类似，但谓词函数还可以接收元素的索引
   *
   * @param predicate 谓词函数，接收元素和索引，返回布尔值表示是否包含该元素
   * @return 满足条件的元素序列
   */
  Enumerable<TSource> takeWhile(Predicate2<TSource, Integer> predicate); // 声明takeWhile方法的带索引版本

  /**
   * Creates a {@code Map<TKey, TValue>} from an
   * {@code Enumerable<TSource>} according to a specified key selector
   * function. // 根据指定的键选择器函数，从Enumerable<TSource>创建Map<TKey, TValue>
   * // 这个方法将序列转换为Map
   * // 键由键选择器函数从元素中提取，值为元素本身
   * // 如果有重复的键，后面的元素会覆盖前面的元素
   * // 这是一个立即执行的方法
   * // 适用场景：构建查找表、创建索引等
   *
   * <p>NOTE: Called {@code toDictionary} in LINQ.NET. // 注意：在LINQ.NET中称为toDictionary
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param <TKey> 键的类型
   * @return 包含键值对的Map
   */
  <TKey> Map<TKey, TSource> toMap(Function1<TSource, TKey> keySelector); // 声明toMap方法的最简单版本

  /**
   * Creates a {@code Map<TKey, TValue>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function
   * and key comparer. // 根据指定的键选择器函数和键比较器，从Enumerable<TSource>创建Map<TKey, TValue>
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较键
   * // 这允许使用不同的相等性判断逻辑
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 键的类型
   * @return 包含键值对的Map
   */
  <TKey> Map<TKey, TSource> toMap(Function1<TSource, TKey> keySelector, // 声明toMap方法的带比较器版本
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Creates a {@code Map<TKey, TValue>} from an
   * {@code Enumerable<TSource>} according to specified key selector and element
   * selector functions. // 根据指定的键选择器和元素选择器函数，从Enumerable<TSource>创建Map<TKey, TValue>
   * // 与第一个toMap方法类似，但可以使用元素选择器来转换值
   * // 键由键选择器函数提取，值由元素选择器函数从元素中提取
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param elementSelector 元素选择器函数，从每个元素中提取值
   * @param <TKey> 键的类型
   * @param <TElement> 值的类型
   * @return 包含键值对的Map
   */
  <TKey, TElement> Map<TKey, TElement> toMap( // 声明toMap方法的带元素选择器版本
      Function1<TSource, TKey> keySelector, // 键选择器
      Function1<TSource, TElement> elementSelector); // 元素选择器

  /**
   * Creates a {@code Map<TKey, TValue>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function,
   * a comparer, and an element selector function. // 根据指定的键选择器函数、比较器和元素选择器函数，从Enumerable<TSource>创建Map<TKey, TValue>
   * // 这是toMap方法最完整的版本，同时支持元素选择器和键比较器
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param elementSelector 元素选择器函数，从每个元素中提取值
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 键的类型
   * @param <TElement> 值的类型
   * @return 包含键值对的Map
   */
  <TKey, TElement> Map<TKey, TElement> toMap( // 声明toMap方法的完整版本
      Function1<TSource, TKey> keySelector, // 键选择器
      Function1<TSource, TElement> elementSelector, // 元素选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Creates a {@code List<TSource>} from an {@code Enumerable<TSource>}. // 从Enumerable<TSource>创建List<TSource>
   * // 这个方法将序列转换为List
   * // List是一个有序的集合，允许重复元素
   * // 这是一个立即执行的方法
   * // 适用场景：将序列转换为列表、需要随机访问元素等
   *
   * @return 包含序列中所有元素的List
   */
  List<TSource> toList(); // 声明toList方法

  /**
   * Creates a {@code Lookup<TKey, TElement>} from an
   * {@code Enumerable<TSource>} according to a specified key selector
   * function. // 根据指定的键选择器函数，从Enumerable<TSource>创建Lookup<TKey, TElement>
   * // Lookup是一个类似Map的数据结构，但每个键可以对应多个值
   * // 类似于SQL的GROUP BY操作的结果
   * // 键由键选择器函数从元素中提取，值为元素本身
   * // 这是一个立即执行的方法
   * // 适用场景：分组查找、一对多关系等
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param <TKey> 键的类型
   * @return 包含键到元素集合映射的Lookup
   */
  <TKey> Lookup<TKey, TSource> toLookup(Function1<TSource, TKey> keySelector); // 声明toLookup方法的最简单版本

  /**
   * Creates a {@code Lookup<TKey, TElement>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function
   * and key comparer. // 根据指定的键选择器函数和键比较器，从Enumerable<TSource>创建Lookup<TKey, TElement>
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较键
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 键的类型
   * @return 包含键到元素集合映射的Lookup
   */
  <TKey> Lookup<TKey, TSource> toLookup(Function1<TSource, TKey> keySelector, // 声明toLookup方法的带比较器版本
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Creates a {@code Lookup<TKey, TElement>} from an
   * {@code Enumerable<TSource>} according to specified key selector and element
   * selector functions. // 根据指定的键选择器和元素选择器函数，从Enumerable<TSource>创建Lookup<TKey, TElement>
   * // 与第一个toLookup方法类似，但可以使用元素选择器来转换值
   * // 键由键选择器函数提取，值由元素选择器函数从元素中提取
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param elementSelector 元素选择器函数，从每个元素中提取值
   * @param <TKey> 键的类型
   * @param <TElement> 值的类型
   * @return 包含键到元素集合映射的Lookup
   */
  <TKey, TElement> Lookup<TKey, TElement> toLookup( // 声明toLookup方法的带元素选择器版本
      Function1<TSource, TKey> keySelector, // 键选择器
      Function1<TSource, TElement> elementSelector); // 元素选择器

  /**
   * Creates a {@code Lookup<TKey, TElement>} from an
   * {@code Enumerable<TSource>} according to a specified key selector function,
   * a comparer and an element selector function. // 根据指定的键选择器函数、比较器和元素选择器函数，从Enumerable<TSource>创建Lookup<TKey, TElement>
   * // 这是toLookup方法最完整的版本，同时支持元素选择器和键比较器
   *
   * @param keySelector 键选择器函数，从每个元素中提取键
   * @param elementSelector 元素选择器函数，从每个元素中提取值
   * @param comparer 自定义的相等比较器，用于比较键
   * @param <TKey> 键的类型
   * @param <TElement> 值的类型
   * @return 包含键到元素集合映射的Lookup
   */
  <TKey, TElement> Lookup<TKey, TElement> toLookup( // 声明toLookup方法的完整版本
      Function1<TSource, TKey> keySelector, // 键选择器
      Function1<TSource, TElement> elementSelector, // 元素选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * Produces the set union of two sequences by using
   * the default equality comparer. // 通过使用默认相等比较器生成两个序列的并集
   * // 这个方法返回两个序列中所有不重复的元素
   * // 相当于集合的并集操作（A ∪ B）
   * // 使用默认的相等比较器来判断元素是否相等
   * // 结果中不包含重复元素
   * // 这是一个延迟执行的方法
   * // 适用场景：合并两个集合、获取所有唯一值等
   *
   * @param source1 要合并的第二个序列
   * @return 并集序列
   */
  Enumerable<TSource> union(Enumerable<TSource> source1); // 声明union方法的无参版本

  /**
   * Produces the set union of two sequences by using a
   * specified {@code EqualityComparer<TSource>}. // 通过使用指定的相等比较器生成两个序列的并集
   * // 与上一个方法类似，但可以使用自定义的相等比较器来比较元素
   *
   * @param source1 要合并的第二个序列
   * @param comparer 自定义的相等比较器
   * @return 并集序列
   */
  Enumerable<TSource> union(Enumerable<TSource> source1, // 声明union方法的带比较器版本
      EqualityComparer<TSource> comparer); // 相等比较器

  /**
   * Filters a sequence of values based on a
   * predicate. // 基于谓词过滤值序列
   * // 这个方法返回序列中满足指定条件的元素
   * // 类似于SQL的WHERE子句
   * // 这是一个延迟执行的方法
   * // 适用场景：数据过滤、条件查询等
   *
   * @param predicate 谓词函数，接收一个元素，返回布尔值表示是否包含该元素
   * @return 过滤后的序列
   */
  Enumerable<TSource> where(Predicate1<TSource> predicate); // 声明where方法的无参版本

  /**
   * Filters a sequence of values based on a
   * predicate. Each element's index is used in the logic of the
   * predicate function. // 基于谓词过滤值序列。每个元素的索引用于谓词函数的逻辑
   * // 与上一个方法类似，但谓词函数还可以接收元素的索引
   * // 索引从0开始
   * // 适用场景：需要索引信息的过滤等
   *
   * @param predicate 谓词函数，接收元素和索引，返回布尔值表示是否包含该元素
   * @return 过滤后的序列
   */
  Enumerable<TSource> where(Predicate2<TSource, Integer> predicate); // 声明where方法的带索引版本

  /**
   * Applies a specified function to the corresponding
   * elements of two sequences, producing a sequence of the
   * results. // 将指定的函数应用于两个序列的对应元素，生成结果序列
   * // 这个方法将两个序列中对应位置的元素组合成新的元素
   * // 如果两个序列长度不同，结果序列的长度为较短序列的长度
   * // 类似于Python的zip函数
   * // 这是一个延迟执行的方法
   * // 适用场景：合并两个序列、同时遍历多个序列等
   *
   * @param source1 要合并的第二个序列
   * @param resultSelector 结果选择器函数，接收两个序列的对应元素，返回结果
   * @param <T1> 第二个序列元素的类型
   * @param <TResult> 结果的类型
   * @return 合并后的序列
   */
  <T1, TResult> Enumerable<TResult> zip(Enumerable<T1> source1, // 声明zip方法
      Function2<TSource, T1, TResult> resultSelector); // 结果选择器
}
