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
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.FunctionExpression;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Iterator;

import static java.util.Objects.requireNonNull;

/**
 * Default implementations for methods in the {@link Queryable} interface.
 * Queryable接口方法的默认实现类。这个类提供了LINQ（Language Integrated Query）风格查询操作符的默认实现，
 * 它是Calcite的Linq4j库的核心组件之一，用于在Java中实现类似.NET LINQ的查询功能。
 * 
 * 主要功能：
 * 1. 提供各种查询操作符的默认实现，如过滤、投影、排序、分组、聚合等
 * 2. 支持延迟执行（lazy evaluation）和表达式树（expression tree）构建
 * 3. 作为Queryable接口的扩展方法集合，提供静态方法来实现各种查询操作
 * 4. 与EnumerableDefaults配合使用，提供内存中和可查询数据源的统一查询接口
 * 
 * 设计特点：
 * - 所有方法都是静态的，可以直接调用而无需实例化
 * - 使用泛型支持类型安全的查询操作
 * - 大部分方法返回Queryable接口，支持链式调用
 * - 通过FunctionExpression封装Lambda表达式，支持表达式树构建
 * 
 * 使用场景：
 * - 在构建SQL查询时，用于表示查询操作的语义
 * - 在需要延迟执行的场景中，用于构建查询表达式树
 * - 作为其他Queryable实现的基类，提供通用的查询操作实现
 */
public abstract class QueryableDefaults {

  /**
   * Applies an accumulator function over a
   * sequence.
   * 对序列应用累加器函数。该方法遍历序列中的每个元素，使用累加器函数将元素累积到一个单一的结果值中。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要聚合的查询序列
   * @param func 累加器函数，接受两个参数：当前累积值和序列中的下一个元素，返回新的累积值
   * @return 序列的最终聚合结果
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：对数字序列求和
   * aggregate([1, 2, 3, 4], (acc, x) -> acc + x) 返回 10
   * 
   * 注意：这是聚合操作的最简单形式，使用序列的第一个元素作为初始累积值
   */
  public static <T> T aggregate(Queryable<T> queryable,
      FunctionExpression<Function2<T, T, T>> func) {
    throw Extensions.todo();
  }

  /**
   * Applies an accumulator function over a
   * sequence. The specified seed value is used as the initial
   * accumulator value.
   * 对序列应用累加器函数。使用指定的种子值作为初始累积值，然后遍历序列中的每个元素进行累积。
   * 
   * @param <T> 序列中元素的类型
   * @param <TAccumulate> 累积值的类型，可以与元素类型不同
   * @param queryable 要聚合的查询序列
   * @param seed 初始累积值，即累加器的起始值
   * @param func 累加器函数，接受当前累积值和序列中的下一个元素，返回新的累积值
   * @return 序列的最终聚合结果
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：计算数字序列的乘积
   * aggregate([1, 2, 3, 4], 1, (acc, x) -> acc * x) 返回 24
   * 
   * 示例：将字符串序列连接起来
   * aggregate(["a", "b", "c"], "", (acc, x) -> acc + x) 返回 "abc"
   * 
   * 注意：种子值允许累积类型与元素类型不同，提供更大的灵活性
   */
  public static <T, TAccumulate> TAccumulate aggregate(Queryable<T> queryable,
      TAccumulate seed,
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func) {
    throw Extensions.todo();
  }

  /**
   * Applies an accumulator function over a
   * sequence. The specified seed value is used as the initial
   * accumulator value, and the specified function is used to select
   * the result value.
   * 对序列应用累加器函数。使用指定的种子值作为初始累积值，遍历序列进行累积，最后使用结果选择器函数转换最终结果。
   * 
   * @param <T> 序列中元素的类型
   * @param <TAccumulate> 累积值的类型
   * @param <TResult> 最终返回结果的类型
   * @param queryable 要聚合的查询序列
   * @param seed 初始累积值
   * @param func 累加器函数，接受当前累积值和序列中的下一个元素，返回新的累积值
   * @param selector 结果选择器函数，接受最终的累积值，返回转换后的结果
   * @return 经过结果选择器转换后的最终结果
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：计算数字序列的平均值
   * aggregate([1, 2, 3, 4], 0, (acc, x) -> acc + x, sum -> sum / 4.0) 返回 2.5
   * 
   * 示例：统计字符串序列的总字符数
   * aggregate(["hello", "world"], 0, (acc, x) -> acc + x.length(), count -> count) 返回 10
   * 
   * 注意：结果选择器允许对累积结果进行任意转换，提供最大的灵活性
   */
  public static <T, TAccumulate, TResult> TResult aggregate(
      Queryable<T> queryable, TAccumulate seed,
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func,
      FunctionExpression<Function1<TAccumulate, TResult>> selector) {
    throw Extensions.todo();
  }

  /**
   * Determines whether all the elements of a sequence
   * satisfy a condition.
   * 确定序列中的所有元素是否都满足指定的条件。如果序列为空，返回true。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要检查的查询序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 如果所有元素都满足条件或序列为空，返回true；否则返回false
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * all([2, 4, 6, 8], x -> x % 2 == 0) 返回 true（所有元素都是偶数）
   * all([2, 4, 6, 7], x -> x % 2 == 0) 返回 false（7不是偶数）
   * all([], x -> x > 0) 返回 true（空序列返回true）
   * 
   * 注意：这是全称量词操作，对应逻辑中的"对于所有"（∀）
   */
  public static <T> boolean all(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Determines whether a sequence contains any
   * elements.
   * 确定序列是否包含任何元素。用于检查序列是否非空。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要检查的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * any([1, 2, 3]) 返回 true（序列非空）
   * any([]) 返回 false（序列为空）
   * 
   * 注意：此方法返回void，可能用于触发查询或检查序列状态
   * 通常用于需要延迟执行的场景，实际使用时可能需要检查返回值
   */
  public static <T> void any(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Determines whether any element of a sequence
   * satisfies a condition.
   * 确定序列中是否有任何元素满足指定的条件。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要检查的查询序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 如果至少有一个元素满足条件，返回true；否则返回false
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * any([1, 2, 3, 4], x -> x > 3) 返回 true（4满足条件）
   * any([1, 2, 3], x -> x > 3) 返回 false（没有元素满足条件）
   * any([], x -> x > 0) 返回 false（空序列返回false）
   * 
   * 注意：这是存在量词操作，对应逻辑中的"存在"（∃）
   * 与all方法相对，all要求所有元素都满足条件，any只要求至少一个元素满足
   */
  public static <T> boolean any(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Converts a generic {@code Enumerable<T>} to a generic
   * {@code IQueryable<T>}.
   * 将泛型的Enumerable<T>转换为泛型的IQueryable<T>。这个方法用于将内存中的可枚举集合转换为可查询集合，
   * 从而支持表达式树构建和延迟执行。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要转换的查询序列，实际上已经是Queryable类型
   * @return 转换后的Queryable对象
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * List<Integer> list = Arrays.asList(1, 2, 3);
   * Queryable<Integer> queryable = asQueryable(EnumerableDefaults.asEnumerable(list));
   * 
   * 注意：
   * - Enumerable是内存中的集合，支持立即执行
   * - Queryable支持表达式树和延迟执行，可以转换为远程查询（如SQL）
   * - 此方法通常用于桥接内存集合和可查询数据源
   * - 在Calcite中，这个方法用于构建查询表达式树
   */
  public static <T> Queryable<T> asQueryable(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of Decimal
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   * 计算序列中BigDecimal值的平均值。通过对输入序列的每个元素调用投影函数获得BigDecimal值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取BigDecimal值
   * @return 序列中BigDecimal值的平均值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageBigDecimal(["1.5", "2.5", "3.5"], BigDecimal::new) 返回 2.5
   * 
   * 注意：
   * - BigDecimal提供高精度的十进制运算，适合财务计算
   * - 如果序列为空，可能抛出异常或返回null（取决于具体实现）
   * - 平均值 = 所有值的总和 / 元素数量
   * - 使用BigDecimal可以避免浮点数精度问题
   */
  public static <T> BigDecimal averageBigDecimal(Queryable<T> queryable,
      FunctionExpression<BigDecimalFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of nullable
   * Decimal values that is obtained by invoking a projection
   * function on each element of the input sequence.
   * 计算可空BigDecimal值序列的平均值。通过对输入序列的每个元素调用投影函数获得可空BigDecimal值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取可空BigDecimal值（可能返回null）
   * @return 序列中可空BigDecimal值的平均值，如果所有值都为null或序列为空，返回null
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageNullableBigDecimal([1.5, null, 3.5], x -> x) 返回 2.5（忽略null值）
   * averageNullableBigDecimal([null, null], x -> x) 返回 null
   * 
   * 注意：
   * - 可空值序列中的null值在计算平均值时会被忽略
   * - 如果所有值都是null，返回null
   * - 如果序列为空，返回null
   * - 只计算非null值的平均值
   */
  public static <T> BigDecimal averageNullableBigDecimal(Queryable<T> queryable,
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of Double
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   * 计算序列中Double值的平均值。通过对输入序列的每个元素调用投影函数获得Double值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取Double值
   * @return 序列中Double值的平均值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageDouble([1.5, 2.5, 3.5], x -> x) 返回 2.5
   * 
   * 注意：
   * - Double是64位浮点数，提供双精度计算
   * - 如果序列为空，可能抛出异常
   * - 平均值 = 所有值的总和 / 元素数量
   * - 浮点数运算可能存在精度误差
   */
  public static <T> double averageDouble(Queryable<T> queryable,
      FunctionExpression<DoubleFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of nullable
   * Double values that is obtained by invoking a projection
   * function on each element of the input sequence.
   * 计算可空Double值序列的平均值。通过对输入序列的每个元素调用投影函数获得可空Double值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取可空Double值（可能返回null）
   * @return 序列中可空Double值的平均值，如果所有值都为null或序列为空，返回null
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageNullableDouble([1.5, null, 3.5], x -> x) 返回 2.5（忽略null值）
   * averageNullableDouble([null, null], x -> x) 返回 null
   * 
   * 注意：
   * - 可空值序列中的null值在计算平均值时会被忽略
   * - 如果所有值都是null，返回null
   * - 如果序列为空，返回null
   * - 只计算非null值的平均值
   */
  public static <T> Double averageNullableDouble(Queryable<T> queryable,
      FunctionExpression<NullableDoubleFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of int values
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   * 计算序列中int值的平均值。通过对输入序列的每个元素调用投影函数获得int值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取int值
   * @return 序列中int值的平均值（整数除法，结果可能不是精确的平均值）
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageInteger([1, 2, 3, 4, 5], x -> x) 返回 3（(1+2+3+4+5)/5 = 3）
   * averageInteger([1, 2, 3], x -> x) 返回 2（(1+2+3)/3 = 2）
   * 
   * 注意：
   * - 返回值是int类型，使用整数除法，会丢失小数部分
   * - 如果需要精确的平均值，应使用averageDouble方法
   * - 如果序列为空，可能抛出异常
   * - 平均值 = 所有值的总和 / 元素数量（整数除法）
   */
  public static <T> int averageInteger(Queryable<T> queryable,
      FunctionExpression<IntegerFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of nullable
   * int values that is obtained by invoking a projection function
   * on each element of the input sequence.
   * 计算可空int值序列的平均值。通过对输入序列的每个元素调用投影函数获得可空int值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取可空int值（可能返回null）
   * @return 序列中可空int值的平均值，如果所有值都为null或序列为空，返回null
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageNullableInteger([1, null, 3], x -> x) 返回 2（忽略null值，(1+3)/2 = 2）
   * averageNullableInteger([null, null], x -> x) 返回 null
   * 
   * 注意：
   * - 可空值序列中的null值在计算平均值时会被忽略
   * - 如果所有值都是null，返回null
   * - 如果序列为空，返回null
   * - 返回值是Integer类型，使用整数除法，会丢失小数部分
   * - 只计算非null值的平均值
   */
  public static <T> Integer averageNullableInteger(Queryable<T> queryable,
      FunctionExpression<NullableIntegerFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of Float
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   * 计算序列中Float值的平均值。通过对输入序列的每个元素调用投影函数获得Float值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取Float值
   * @return 序列中Float值的平均值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageFloat([1.5f, 2.5f, 3.5f], x -> x) 返回 2.5f
   * 
   * 注意：
   * - Float是32位浮点数，提供单精度计算
   * - 如果序列为空，可能抛出异常
   * - 平均值 = 所有值的总和 / 元素数量
   * - 浮点数运算可能存在精度误差
   * - 相比Double，Float精度较低但占用内存更小
   */
  public static <T> float averageFloat(Queryable<T> queryable,
      FunctionExpression<FloatFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of nullable
   * Float values that is obtained by invoking a projection
   * function on each element of the input sequence.
   * 计算可空Float值序列的平均值。通过对输入序列的每个元素调用投影函数获得可空Float值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取可空Float值（可能返回null）
   * @return 序列中可空Float值的平均值，如果所有值都为null或序列为空，返回null
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageNullableFloat([1.5f, null, 3.5f], x -> x) 返回 2.5f（忽略null值）
   * averageNullableFloat([null, null], x -> x) 返回 null
   * 
   * 注意：
   * - 可空值序列中的null值在计算平均值时会被忽略
   * - 如果所有值都是null，返回null
   * - 如果序列为空，返回null
   * - 只计算非null值的平均值
   */
  public static <T> Float averageNullableFloat(Queryable<T> queryable,
      FunctionExpression<NullableFloatFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of long values
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   * 计算序列中long值的平均值。通过对输入序列的每个元素调用投影函数获得long值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取long值
   * @return 序列中long值的平均值（整数除法，结果可能不是精确的平均值）
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageLong([1L, 2L, 3L, 4L, 5L], x -> x) 返回 3L（(1+2+3+4+5)/5 = 3）
   * averageLong([10L, 20L, 30L], x -> x) 返回 20L（(10+20+30)/3 = 20）
   * 
   * 注意：
   * - long是64位整数，可以表示更大的数值范围
   * - 返回值是long类型，使用整数除法，会丢失小数部分
   * - 如果需要精确的平均值，应使用averageDouble方法
   * - 如果序列为空，可能抛出异常
   * - 平均值 = 所有值的总和 / 元素数量（整数除法）
   */
  public static <T> long averageLong(Queryable<T> queryable,
      FunctionExpression<LongFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the average of a sequence of nullable
   * long values that is obtained by invoking a projection function
   * on each element of the input sequence.
   * 计算可空long值序列的平均值。通过对输入序列的每个元素调用投影函数获得可空long值序列，然后计算平均值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计算平均值的查询序列
   * @param selector 投影函数，从每个元素中提取可空long值（可能返回null）
   * @return 序列中可空long值的平均值，如果所有值都为null或序列为空，返回null
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * averageNullableLong([1L, null, 3L], x -> x) 返回 2L（忽略null值，(1+3)/2 = 2）
   * averageNullableLong([null, null], x -> x) 返回 null
   * 
   * 注意：
   * - 可空值序列中的null值在计算平均值时会被忽略
   * - 如果所有值都是null，返回null
   * - 如果序列为空，返回null
   * - 返回值是Long类型，使用整数除法，会丢失小数部分
   * - 只计算非null值的平均值
   */
  public static <T> Long averageNullableLong(Queryable<T> queryable,
      FunctionExpression<NullableLongFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Analogous to LINQ's Enumerable.Cast extension method.
   * 类似于LINQ的Enumerable.Cast扩展方法。将序列中的元素转换为指定的类型。
   *
   * @param <T> 源序列中元素的类型
   * @param <T2> 目标类型
   * @param source 源查询序列
   * @param clazz 目标类型的Class对象
   * @return 转换后的T2类型的集合
   * 
   * 示例：
   * cast([1, 2, 3], Number.class) 返回 Number类型的序列
   * cast(["a", "b", "c"], Object.class) 返回 Object类型的序列
   * 
   * 注意：
   * - 如果元素无法转换为目标类型，将抛出ClassCastException
   * - 这个方法不会过滤元素，而是尝试转换每个元素
   * - 与ofType方法不同，cast会尝试转换所有元素，而ofType只保留可以转换的元素
   * - 使用CastingEnumerator来执行实际的类型转换
   * - 创建新的BaseQueryable实例，保留原始查询的提供者和表达式
   */
  public static <T, T2> Queryable<T2> cast(final Queryable<T> source,
      final Class<T2> clazz) {
    return new BaseQueryable<T2>(source.getProvider(), clazz,
        source.getExpression()) {
      @Override public Enumerator<T2> enumerator() {
        return new EnumerableDefaults.CastingEnumerator<>(source.enumerator(),
            clazz);
      }
    };
  }

  /**
   * Concatenates two sequences.
   * 连接两个序列。将两个序列合并为一个序列，第一个序列的元素在前，第二个序列的元素在后。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable0 第一个查询序列
   * @param source2 第二个可枚举序列
   * @return 连接后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * concat([1, 2, 3], [4, 5, 6]) 返回 [1, 2, 3, 4, 5, 6]
   * concat(["a", "b"], ["c", "d"]) 返回 ["a", "b", "c", "d"]
   * 
   * 注意：
   * - 原始序列不会被修改
   * - 两个序列的元素类型必须相同
   * - 如果两个序列都为空，返回空序列
   * - 重复的元素会被保留（不执行去重操作）
   * - 与union方法不同，concat保留所有重复元素
   */
  public static <T> Queryable<T> concat(Queryable<T> queryable0,
      Enumerable<T> source2) {
    throw Extensions.todo();
  }

  /**
   * Determines whether a sequence contains a specified
   * element by using the default equality comparer.
   * 使用默认的相等比较器确定序列是否包含指定的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要检查的查询序列
   * @param element 要查找的元素
   * @return 如果序列包含该元素，返回true；否则返回false
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * contains([1, 2, 3, 4, 5], 3) 返回 true
   * contains([1, 2, 3, 4, 5], 6) 返回 false
   * contains(["a", "b", "c"], "b") 返回 true
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）进行比较
   * - 对于null元素，使用默认的null处理逻辑
   * - 如果序列为空，返回false
   * - 如果要使用自定义比较器，使用contains的重载版本
   */
  public static <T> boolean contains(Queryable<T> queryable, T element) {
    throw Extensions.todo();
  }

  /**
   * Determines whether a sequence contains a specified
   * element by using a specified {@code EqualityComparer<T>}.
   * 使用指定的相等比较器确定序列是否包含指定的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要检查的查询序列
   * @param element 要查找的元素
   * @param comparer 用于比较元素的相等比较器
   * @return 如果序列包含该元素，返回true；否则返回false
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * contains(["A", "B", "C"], "a", String.CASE_INSENSITIVE_ORDER) 返回 true（忽略大小写）
   * contains([1, 2, 3], 2, (a, b) -> a.equals(b)) 返回 true
   * 
   * 注意：
   * - 使用指定的比较器进行比较，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 对于null元素，比较器需要处理null情况
   * - 如果序列为空，返回false
   */
  public static <T> boolean contains(Queryable<T> queryable, T element,
      EqualityComparer comparer) {
    throw Extensions.todo();
  }

  /**
   * Returns the number of elements in a
   * sequence.
   * 返回序列中的元素数量。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计数的查询序列
   * @return 序列中的元素数量
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * count([1, 2, 3, 4, 5]) 返回 5
   * count(["a", "b", "c"]) 返回 3
   * count([]) 返回 0
   * 
   * 注意：
   * - 返回int类型，如果元素数量超过Integer.MAX_VALUE，应使用longCount方法
   * - 如果序列为空，返回0
   * - 这是一个聚合操作，需要遍历整个序列
   * - 在某些实现中，可能会利用源数据源的计数功能优化性能
   */
  public static <T> int count(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Returns the number of elements in the specified
   * sequence that satisfies a condition.
   * 返回指定序列中满足条件的元素数量。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计数的查询序列
   * @param func 谓词函数，用于测试每个元素是否满足条件
   * @return 满足条件的元素数量
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * count([1, 2, 3, 4, 5], x -> x > 2) 返回 3（3, 4, 5满足条件）
   * count(["a", "bb", "ccc", "dd"], s -> s.length() > 1) 返回 3
   * 
   * 注意：
   * - 返回int类型，如果满足条件的元素数量超过Integer.MAX_VALUE，应使用longCount方法
   * - 如果没有元素满足条件，返回0
   * - 如果序列为空，返回0
   * - 这是一个聚合操作，需要遍历整个序列
   * - 相当于先使用where过滤，再使用count计数
   */
  public static <T> int count(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> func) {
    throw Extensions.todo();
  }

  /**
   * Returns the elements of the specified sequence or
   * the type parameter's default value in a singleton collection if
   * the sequence is empty.
   * 返回指定序列的元素，如果序列为空，则返回类型参数的默认值构成的单元素集合。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要检查的查询序列
   * @return 如果序列非空，返回原序列；如果序列为空，返回包含类型默认值的单元素序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * defaultIfEmpty([1, 2, 3]) 返回 [1, 2, 3]
   * defaultIfEmpty([]) 返回 [null]（对于引用类型，默认值为null）
   * defaultIfEmpty([]) 返回 [0]（对于int类型，默认值为0）
   * 
   * 注意：
   * - 对于引用类型，默认值为null
   * - 对于基本类型，默认值为0、false等
   * - 常用于避免空序列导致的异常
   * - 与SQL中的COALESCE函数类似
   * - 不会修改原始序列
   */
  public static <T> Queryable<T> defaultIfEmpty(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Returns the elements of the specified sequence or
   * the specified value in a singleton collection if the sequence
   * is empty.
   * 返回指定序列的元素，如果序列为空，则返回包含指定值的单元素集合。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要检查的查询序列
   * @param value 当序列为空时返回的默认值
   * @return 如果序列非空，返回原序列；如果序列为空，返回包含指定值的单元素序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * defaultIfEmpty([1, 2, 3], 99) 返回 [1, 2, 3]
   * defaultIfEmpty([], 99) 返回 [99]
   * defaultIfEmpty([], "default") 返回 ["default"]
   * 
   * 注意：
   * - 允许指定自定义的默认值，而不是使用类型的默认值
   * - 默认值可以是任何与元素类型兼容的值
   * - 常用于为空序列提供有意义的默认值
   * - 不会修改原始序列
   */
  public static <T> T defaultIfEmpty(Queryable<T> queryable, T value) {
    throw Extensions.todo();
  }

  /**
   * Returns distinct elements from a sequence by using
   * the default equality comparer to compare values.
   * 使用默认的相等比较器返回序列中的不同元素（去重）。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要去重的查询序列
   * @return 包含不同元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * distinct([1, 2, 2, 3, 3, 3]) 返回 [1, 2, 3]
   * distinct(["a", "b", "a", "c", "b"]) 返回 ["a", "b", "c"]
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）来判断元素是否相同
   * - 保留每个不同元素的第一次出现
   * - 元素的顺序会被保留
   * - 对于null元素，使用默认的null处理逻辑
   * - 如果要使用自定义比较器，使用distinct的重载版本
   * - 与SQL中的DISTINCT关键字功能相同
   */
  public static <T> Queryable<T> distinct(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Returns distinct elements from a sequence by using
   * a specified {@code EqualityComparer<T>} to compare values.
   * 使用指定的相等比较器返回序列中的不同元素（去重）。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要去重的查询序列
   * @param comparer 用于比较元素的相等比较器
   * @return 包含不同元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * distinct(["A", "a", "B", "b"], String.CASE_INSENSITIVE_ORDER) 返回 ["A", "B"]（忽略大小写）
   * 
   * 注意：
   * - 使用指定的比较器来判断元素是否相同，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 保留每个不同元素的第一次出现
   * - 元素的顺序会被保留
   * - 对于null元素，比较器需要处理null情况
   */
  public static <T> Queryable<T> distinct(Queryable<T> queryable,
      EqualityComparer comparer) {
    throw Extensions.todo();
  }

  /**
   * Returns the element at a specified index in a
   * sequence.
   * 返回序列中指定索引处的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @param index 要获取的元素的从零开始的索引
   * @return 序列中指定位置处的元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * elementAt([10, 20, 30, 40], 2) 返回 30
   * elementAt(["a", "b", "c"], 0) 返回 "a"
   * 
   * 注意：
   * - 索引从0开始
   * - 如果索引超出范围，会抛出异常
   * - 如果索引为负数，会抛出异常
   * - 如果序列为空，会抛出异常
   * - 如果需要安全的元素访问（不抛出异常），使用elementAtOrDefault方法
   * - 与数组的索引访问类似，但支持任意序列类型
   */
  public static <T> T elementAt(Queryable<T> queryable, int index) {
    throw Extensions.todo();
  }

  /**
   * Returns the element at a specified index in a
   * sequence or a default value if the index is out of
   * range.
   * 返回序列中指定索引处的元素，如果索引超出范围，则返回默认值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @param index 要获取的元素的从零开始的索引
   * @return 序列中指定位置处的元素，如果索引超出范围，返回类型默认值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * elementAtOrDefault([10, 20, 30], 2) 返回 30
   * elementAtOrDefault([10, 20, 30], 5) 返回 null（对于引用类型）
   * elementAtOrDefault([10, 20, 30], -1) 返回 null
   * 
   * 注意：
   * - 索引从0开始
   * - 如果索引超出范围，返回类型默认值（引用类型为null，基本类型为0、false等）
   * - 如果索引为负数，返回类型默认值
   * - 如果序列为空，返回类型默认值
   * - 与elementAt方法不同，此方法不会抛出异常
   * - 提供安全的元素访问方式
   */
  public static <T> T elementAtOrDefault(Queryable<T> queryable, int index) {
    throw Extensions.todo();
  }

  /**
   * Produces the set difference of two sequences by
   * using the default equality comparer to compare values. (Defined
   * by Queryable.)
   * 生成两个序列的集合差集，使用默认的相等比较器来比较值。返回只出现在第一个序列中而不出现在第二个序列中的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 第一个查询序列（要从中减去元素的序列）
   * @param enumerable 第二个可枚举序列（要减去的元素）
   * @return 包含在第一个序列中但不在第二个序列中的元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * except([1, 2, 3, 4, 5], [2, 4]) 返回 [1, 3, 5]
   * except(["a", "b", "c", "d"], ["b", "d"]) 返回 ["a", "c"]
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）来判断元素是否相等
   * - 重复元素会被保留（如果第一个序列中有重复且不在第二个序列中）
   * - 元素的顺序会被保留
   * - 如果第一个序列为空，返回空序列
   * - 如果第二个序列为空，返回第一个序列的副本
   * - 与SQL中的EXCEPT操作符功能相同
   */
  public static <T> Queryable<T> except(Queryable<T> queryable,
      Enumerable<T> enumerable) {
    throw Extensions.todo();
  }

  /**
   * Produces the set difference of two sequences by
   * using the specified {@code EqualityComparer<T>} to compare
   * values.
   * 生成两个序列的集合差集，使用指定的相等比较器来比较值。返回只出现在第一个序列中而不出现在第二个序列中的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 第一个查询序列（要从中减去元素的序列）
   * @param enumerable 第二个可枚举序列（要减去的元素）
   * @param comparer 用于比较元素的相等比较器
   * @return 包含在第一个序列中但不在第二个序列中的元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * except(["A", "B", "C"], ["b"], String.CASE_INSENSITIVE_ORDER) 返回 ["A", "C"]（忽略大小写）
   * 
   * 注意：
   * - 使用指定的比较器来判断元素是否相等，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 重复元素会被保留（如果第一个序列中有重复且不在第二个序列中）
   * - 元素的顺序会被保留
   * - 对于null元素，比较器需要处理null情况
   */
  public static <T> Queryable<T> except(Queryable<T> queryable,
      Enumerable<T> enumerable, EqualityComparer<T> comparer) {
    throw Extensions.todo();
  }

  /**
   * Returns the first element of a sequence. (Defined
   * by Queryable.)
   * 返回序列的第一个元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @return 序列的第一个元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * first([10, 20, 30]) 返回 10
   * first(["a", "b", "c"]) 返回 "a"
   * 
   * 注意：
   * - 如果序列为空，会抛出异常
   * - 返回第一个元素，不修改序列
   * - 与last方法相对，last返回最后一个元素
   * - 如果需要安全的访问（不抛出异常），使用firstOrDefault方法
   */
  public static <T> T first(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Returns the first element of a sequence that
   * satisfies a specified condition.
   * 返回序列中满足指定条件的第一个元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 第一个满足条件的元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * first([1, 2, 3, 4, 5], x -> x > 2) 返回 3
   * first(["apple", "banana", "cherry"], s -> s.startsWith("b")) 返回 "banana"
   * 
   * 注意：
   * - 如果没有元素满足条件，会抛出异常
   * - 如果序列为空，会抛出异常
   * - 返回第一个满足条件的元素，不修改序列
   * - 如果需要安全的访问（不抛出异常），使用firstOrDefault方法
   * - 相当于先使用where过滤，再使用first获取第一个元素
   */
  public static <T> T first(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Returns the first element of a sequence, or a
   * default value if the sequence contains no elements.
   * 返回序列的第一个元素，如果序列不包含任何元素，则返回默认值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @return 序列的第一个元素，如果序列为空，返回类型默认值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * firstOrDefault([10, 20, 30]) 返回 10
   * firstOrDefault([]) 返回 null（对于引用类型）
   * firstOrDefault([]) 返回 0（对于int类型）
   * 
   * 注意：
   * - 如果序列为空，返回类型默认值（引用类型为null，基本类型为0、false等）
   * - 与first方法不同，此方法不会抛出异常
   * - 提供安全的第一个元素访问方式
   * - 常用于避免空序列导致的异常
   */
  public static <T> T firstOrDefault(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Returns the first element of a sequence that
   * satisfies a specified condition or a default value if no such
   * element is found.
   * 返回序列中满足指定条件的第一个元素，如果没有找到这样的元素，则返回默认值。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 第一个满足条件的元素，如果没有找到，返回类型默认值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * firstOrDefault([1, 2, 3, 4, 5], x -> x > 2) 返回 3
   * firstOrDefault([1, 2, 3], x -> x > 5) 返回 null（没有元素满足条件）
   * firstOrDefault([], x -> x > 0) 返回 null（空序列）
   * 
   * 注意：
   * - 如果没有元素满足条件，返回类型默认值
   * - 如果序列为空，返回类型默认值
   * - 与first方法不同，此方法不会抛出异常
   * - 提供安全的第一个满足条件元素访问方式
   */
  public static <T> T firstOrDefault(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function.
   * 根据指定的键选择器函数对序列的元素进行分组。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 分组键的类型
   * @param queryable 要分组的查询序列
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @return 分组后的序列，每个分组包含键和该键对应的所有元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * groupBy(["apple", "banana", "apricot", "blueberry"], s -> s.charAt(0)) 
   *   返回 [['a' -> ["apple", "apricot"]], ['b' -> ["banana", "blueberry"]]]
   * 
   * 注意：
   * - 使用默认的相等比较器来比较键
   * - 返回的分组序列中，每个分组是一个Grouping对象，包含键和该键对应的所有元素
   * - 分组内元素的顺序与原始序列中的顺序相同
   * - 分组的顺序取决于键的首次出现顺序
   * - 与SQL中的GROUP BY子句功能相同
   */
  public static <T, TKey> Queryable<Grouping<TKey, T>> groupBy(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and compares the keys by using
   * a specified comparer.
   * 根据指定的键选择器函数对序列的元素进行分组，并使用指定的比较器来比较键。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 分组键的类型
   * @param queryable 要分组的查询序列
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param comparer 用于比较键的相等比较器
   * @return 分组后的序列，每个分组包含键和该键对应的所有元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * groupBy(["Apple", "apple", "Banana"], s -> s, String.CASE_INSENSITIVE_ORDER)
   *   返回 [['Apple' -> ["Apple", "apple"]], ['Banana' -> ["Banana"]]]
   * 
   * 注意：
   * - 使用指定的比较器来比较键，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 返回的分组序列中，每个分组是一个Grouping对象
   * - 对于null键，比较器需要处理null情况
   */
  public static <T, TKey> Queryable<Grouping<TKey, T>> groupBy(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector,
      EqualityComparer comparer) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and projects the elements for
   * each group by using a specified function.
   * 根据指定的键选择器函数对序列的元素进行分组，并使用指定的函数对每个组的元素进行投影。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 分组键的类型
   * @param <TElement> 分组中投影后的元素类型
   * @param queryable 要分组的查询序列
   * @param keySelector 键选择器函数，从每个元素中提取分组键
   * @param elementSelector 元素选择器函数，将每个元素转换为投影后的元素
   * @return 分组后的序列，每个分组包含键和投影后的元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * groupBy(["apple", "banana", "apricot"], 
   *         s -> s.charAt(0), 
   *         s -> s.toUpperCase())
   *   返回 [['a' -> ["APPLE", "APRICOT"]], ['b' -> ["BANANA"]]]
   * 
   * 注意：
   * - 元素选择器允许对分组内的元素进行转换
   * - 可以用于提取元素的特定属性或计算衍生值
   * - 分组内投影后元素的顺序与原始序列中的顺序相同
   */
  public static <T, TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key.
   *
   * <p>NOTE: Renamed from {@code groupBy} to distinguish from
   * {@link #groupBy(org.apache.calcite.linq4j.Queryable, org.apache.calcite.linq4j.tree.FunctionExpression, org.apache.calcite.linq4j.tree.FunctionExpression)},
   * which has the same erasure.
   */
  public static <T, TKey, TResult> Queryable<Grouping<TKey, TResult>> groupByK(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>>
        elementSelector) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence and projects the
   * elements for each group by using a specified function. Key
   * values are compared by using a specified comparer.
   */
  public static <T, TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector,
      EqualityComparer comparer) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. Keys are compared by using a specified
   * comparer.
   *
   * <p>NOTE: Renamed from {@code groupBy} to distinguish from
   * {@link #groupBy(org.apache.calcite.linq4j.Queryable, org.apache.calcite.linq4j.tree.FunctionExpression, org.apache.calcite.linq4j.tree.FunctionExpression, org.apache.calcite.linq4j.function.EqualityComparer)},
   * which has the same erasure.
   */
  public static <T, TKey, TResult> Queryable<TResult> groupByK(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>>
        elementSelector,
      EqualityComparer comparer) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. The elements of each group are
   * projected by using a specified function.
   */
  public static <T, TKey, TElement, TResult> Queryable<TResult> groupBy(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector,
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>>
        resultSelector) {
    throw Extensions.todo();
  }

  /**
   * Groups the elements of a sequence according to a
   * specified key selector function and creates a result value from
   * each group and its key. Keys are compared by using a specified
   * comparer and the elements of each group are projected by using
   * a specified function.
   */
  public static <T, TKey, TElement, TResult> Queryable<TResult> groupBy(
      Queryable<T> queryable,
      FunctionExpression<Function1<T, TKey>> keySelector,
      FunctionExpression<Function1<T, TElement>> elementSelector,
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>>
        resultSelector,
      EqualityComparer<TKey> comparer) {
    throw Extensions.todo();
  }

  /**
   * Correlates the elements of two sequences based on
   * key equality and groups the results. The default equality
   * comparer is used to compare keys.
   * 基于键相等性关联两个序列的元素并对结果进行分组。使用默认的相等比较器来比较键。
   * 
   * @param <TOuter> 外部序列中元素的类型
   * @param <TInner> 内部序列中元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果元素的类型
   * @param outer 外部查询序列
   * @param inner 内部可枚举序列
   * @param outerKeySelector 外部键选择器函数，从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数，从内部元素中提取键
   * @param resultSelector 结果选择器函数，接受外部元素和匹配的内部元素集合，返回结果元素
   * @return 关联并分组后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * groupJoin(orders, products, 
   *          o -> o.productId, 
   *          p -> p.id, 
   *          (o, ps) -> new OrderWithProducts(o, ps))
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）来比较键
   * - 这是左外连接（LEFT OUTER JOIN），每个外部元素都会出现在结果中
   * - 内部元素被分组为集合，即使没有匹配的元素也会返回空集合
   * - 与普通的join不同，groupJoin保留所有外部元素
   * - 与SQL中的LEFT JOIN + GROUP BY功能类似
   * - 结果选择器接受外部元素和匹配的内部元素集合
   */
  public static <TOuter, TInner, TKey, TResult> Queryable<TResult> groupJoin(
      Queryable<TOuter> outer, Enumerable<TInner> inner,
      FunctionExpression<Function1<TOuter, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<TOuter, Enumerable<TInner>, TResult>>
        resultSelector) {
    throw Extensions.todo();
  }

  /**
   * Correlates the elements of two sequences based on
   * key equality and groups the results. A specified
   * {@code EqualityComparer<T>} is used to compare keys.
   * 基于键相等性关联两个序列的元素并对结果进行分组。使用指定的相等比较器来比较键。
   * 
   * @param <TOuter> 外部序列中元素的类型
   * @param <TInner> 内部序列中元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果元素的类型
   * @param outer 外部查询序列
   * @param inner 内部可枚举序列
   * @param outerKeySelector 外部键选择器函数，从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数，从内部元素中提取键
   * @param resultSelector 结果选择器函数，接受外部元素和匹配的内部元素集合，返回结果元素
   * @param comparer 用于比较键的相等比较器
   * @return 关联并分组后的可枚举序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 使用指定的比较器来比较键，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 对于null键，比较器需要处理null情况
   * - 这是左外连接（LEFT OUTER JOIN），每个外部元素都会出现在结果中
   * - 内部元素被分组为集合
   */
  public static <TOuter, TInner, TKey, TResult> Enumerable<TResult> groupJoin(
      Queryable<TOuter> outer, Enumerable<TInner> inner,
      FunctionExpression<Function1<TOuter, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<TOuter, Enumerable<TInner>, TResult>>
        resultSelector,
      EqualityComparer<TKey> comparer) {
    throw Extensions.todo();
  }

  /**
   * Produces the set intersection of two sequences by
   * using the default equality comparer to compare values. (Defined
   * by Queryable.)
   * 使用默认的相等比较器生成两个序列的集合交集。返回同时出现在两个序列中的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 第一个查询序列
   * @param enumerable 第二个可枚举序列
   * @return 包含两个序列中共同元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * intersect([1, 2, 3, 4, 5], [3, 4, 5, 6, 7]) 返回 [3, 4, 5]
   * intersect(["a", "b", "c"], ["b", "c", "d"]) 返回 ["b", "c"]
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）来判断元素是否相等
   * - 返回的序列中每个元素只出现一次（去重）
   * - 保留元素首次出现的顺序（通常是第一个序列中的顺序）
   * - 如果两个序列没有共同元素，返回空序列
   * - 与SQL中的INTERSECT操作符功能相同
   */
  public static <T> Queryable<T> intersect(Queryable<T> queryable,
      Enumerable<T> enumerable) {
    throw Extensions.todo();
  }

  /**
   * Produces the set intersection of two sequences by
   * using the specified {@code EqualityComparer<T>} to compare
   * values.
   * 使用指定的相等比较器生成两个序列的集合交集。返回同时出现在两个序列中的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 第一个查询序列
   * @param enumerable 第二个可枚举序列
   * @param comparer 用于比较元素的相等比较器
   * @return 包含两个序列中共同元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 使用指定的比较器来判断元素是否相等，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 返回的序列中每个元素只出现一次（去重）
   * - 保留元素首次出现的顺序
   * - 对于null元素，比较器需要处理null情况
   */
  public static <T> Queryable<T> intersect(Queryable<T> queryable,
      Enumerable<T> enumerable, EqualityComparer<T> comparer) {
    throw Extensions.todo();
  }

  /**
   * Correlates the elements of two sequences based on
   * matching keys. The default equality comparer is used to compare
   * keys.
   * 基于匹配键关联两个序列的元素。使用默认的相等比较器来比较键。
   * 
   * @param <TOuter> 外部序列中元素的类型
   * @param <TInner> 内部序列中元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果元素的类型
   * @param outer 外部查询序列
   * @param inner 内部可枚举序列
   * @param outerKeySelector 外部键选择器函数，从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数，从内部元素中提取键
   * @param resultSelector 结果选择器函数，接受外部和内部元素，返回结果元素
   * @return 关联后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * join(orders, products, 
   *      o -> o.productId, 
   *      p -> p.id, 
   *      (o, p) -> new OrderWithProduct(o, p))
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）来比较键
   * - 这是内连接（INNER JOIN），只返回两个序列中键匹配的元素
   * - 每个外部元素可能与多个内部元素匹配
   * - 与SQL中的JOIN语句功能相同
   * - 结果序列的长度可能大于任一输入序列
   */
  public static <TOuter, TInner, TKey, TResult> Queryable<TResult> join(
      Queryable<TOuter> outer, Enumerable<TInner> inner,
      FunctionExpression<Function1<TOuter, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<TOuter, TInner, TResult>> resultSelector) {
    throw Extensions.todo();
  }

  /**
   * Correlates the elements of two sequences based on
   * matching keys. A specified {@code EqualityComparer<T>} is used to
   * compare keys.
   * 基于匹配键关联两个序列的元素。使用指定的相等比较器来比较键。
   * 
   * @param <TOuter> 外部序列中元素的类型
   * @param <TInner> 内部序列中元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果元素的类型
   * @param outer 外部查询序列
   * @param inner 内部可枚举序列
   * @param outerKeySelector 外部键选择器函数，从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数，从内部元素中提取键
   * @param resultSelector 结果选择器函数，接受外部和内部元素，返回结果元素
   * @param comparer 用于比较键的相等比较器
   * @return 关联后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 使用指定的比较器来比较键，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 对于null键，比较器需要处理null情况
   * - 这是内连接（INNER JOIN），只返回两个序列中键匹配的元素
   */
  public static <TOuter, TInner, TKey, TResult> Queryable<TResult> join(
      Queryable<TOuter> outer, Enumerable<TInner> inner,
      FunctionExpression<Function1<TOuter, TKey>> outerKeySelector,
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector,
      FunctionExpression<Function2<TOuter, TInner, TResult>> resultSelector,
      EqualityComparer<TKey> comparer) {
    throw Extensions.todo();
  }

  /**
   * Returns the last element in a sequence. (Defined
   * by Queryable.)
   * 返回序列中的最后一个元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @return 序列的最后一个元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * last([10, 20, 30]) 返回 30
   * last(["a", "b", "c"]) 返回 "c"
   * 
   * 注意：
   * - 如果序列为空，会抛出异常
   * - 返回最后一个元素，不修改序列
   * - 与first方法相对，first返回第一个元素
   * - 如果需要安全的访问（不抛出异常），使用lastOrDefault方法
   */
  public static <T> T last(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Returns the last element of a sequence that
   * satisfies a specified condition.
   * 返回序列中满足指定条件的最后一个元素。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要查询的序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 最后一个满足条件的元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * last([1, 2, 3, 4, 5], x -> x < 4) 返回 3
   * last(["apple", "banana", "cherry"], s -> s.startsWith("a")) 返回 "apple"
   * 
   * 注意：
   * - 如果没有元素满足条件，会抛出异常
   * - 如果序列为空，会抛出异常
   * - 返回最后一个满足条件的元素，不修改序列
   * - 如果需要安全的访问（不抛出异常），使用lastOrDefault方法
   */
  public static <T> T last(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Returns the last element in a sequence, or a
   * default value if the sequence contains no elements.
   */
  public static <T> T lastOrDefault(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Returns the last element of a sequence that
   * satisfies a condition or a default value if no such element is
   * found.
   */
  public static <T> T lastOrDefault(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Returns an long that represents the total number
   * of elements in a sequence.
   * 返回一个long值，表示序列中的元素总数。
   * 
   * @param <T> 序列中元素的类型
   * @param xable 要计数的查询序列
   * @return 序列中的元素数量
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * longCount([1, 2, 3, 4, 5]) 返回 5L
   * longCount(["a", "b", "c"]) 返回 3L
   * 
   * 注意：
   * - 返回long类型，可以处理超过Integer.MAX_VALUE的元素数量
   * - 如果序列为空，返回0L
   * - 这是一个聚合操作，需要遍历整个序列
   * - 与count方法不同，count返回int，longCount返回long
   * - 在某些实现中，可能会利用源数据源的计数功能优化性能
   */
  public static <T> long longCount(Queryable<T> xable) {
    throw Extensions.todo();
  }

  /**
   * Returns an long that represents the number of
   * elements in a sequence that satisfy a condition.
   * 返回一个long值，表示序列中满足条件的元素数量。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 要计数的查询序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 满足条件的元素数量
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * longCount([1, 2, 3, 4, 5], x -> x > 2) 返回 3L（3, 4, 5满足条件）
   * 
   * 注意：
   * - 返回long类型，可以处理超过Integer.MAX_VALUE的元素数量
   * - 如果没有元素满足条件，返回0L
   * - 如果序列为空，返回0L
   * - 这是一个聚合操作，需要遍历整个序列
   * - 相当于先使用where过滤，再使用longCount计数
   */
  public static <T> long longCount(Queryable<T> queryable,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Returns the maximum value in a generic
   * {@code IQueryable<T>}.
   * 返回泛型IQueryable<T>中的最大值。
   * 
   * @param <T> 序列中元素的类型，必须实现Comparable接口
   * @param queryable 要查找最大值的查询序列
   * @return 序列中的最大值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * max([1, 5, 3, 9, 2]) 返回 9
   * max(["apple", "banana", "cherry"]) 返回 "cherry"（按字母顺序）
   * 
   * 注意：
   * - 元素类型必须实现Comparable接口
   * - 如果序列为空，可能抛出异常
   * - 使用默认的比较逻辑（Comparable.compareTo）
   * - 如果有多个相同的最大值，返回第一个
   * - 与SQL中的MAX聚合函数功能相同
   */
  public static <T> T max(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Invokes a projection function on each element of a
   * generic {@code IQueryable<T>} and returns the maximum resulting
   * value.
   * 对泛型IQueryable<T>的每个元素调用投影函数，并返回最大的结果值。
   * 
   * @param <T> 序列中元素的类型
   * @param <TResult> 投影后值的类型，必须实现Comparable接口
   * @param queryable 要查找最大值的查询序列
   * @param selector 投影函数，从每个元素中提取用于比较的值
   * @return 投影后的最大值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * max(["apple", "banana", "cherry"], s -> s.length()) 返回 6（"banana"的长度）
   * max([1, 5, 3], x -> x * x) 返回 25（5的平方）
   * 
   * 注意：
   * - 投影后的值类型必须实现Comparable接口
   * - 如果序列为空，可能抛出异常
   * - 先对每个元素应用投影函数，然后比较投影后的值
   * - 允许基于元素的某个属性或计算结果查找最大值
   */
  public static <T, TResult> TResult max(Queryable<T> queryable,
      FunctionExpression<Function1<T, TResult>> selector) {
    throw Extensions.todo();
  }

  /**
   * Returns the minimum value in a generic
   * {@code IQueryable<T>}.
   * 返回泛型IQueryable<T>中的最小值。
   * 
   * @param <T> 序列中元素的类型，必须实现Comparable接口
   * @param queryable 要查找最小值的查询序列
   * @return 序列中的最小值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * min([1, 5, 3, 9, 2]) 返回 1
   * min(["apple", "banana", "cherry"]) 返回 "apple"（按字母顺序）
   * 
   * 注意：
   * - 元素类型必须实现Comparable接口
   * - 如果序列为空，可能抛出异常
   * - 使用默认的比较逻辑（Comparable.compareTo）
   * - 如果有多个相同的最小值，返回第一个
   * - 与SQL中的MIN聚合函数功能相同
   */
  public static <T> T min(Queryable<T> queryable) {
    throw Extensions.todo();
  }

  /**
   * Invokes a projection function on each element of a
   * generic {@code IQueryable<T>} and returns the minimum resulting
   * value.
   * 对泛型IQueryable<T>的每个元素调用投影函数，并返回最小的结果值。
   * 
   * @param <T> 序列中元素的类型
   * @param <TResult> 投影后值的类型，必须实现Comparable接口
   * @param queryable 要查找最小值的查询序列
   * @param selector 投影函数，从每个元素中提取用于比较的值
   * @return 投影后的最小值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * min(["apple", "banana", "cherry"], s -> s.length()) 返回 5（"apple"的长度）
   * min([1, 5, 3], x -> x * x) 返回 1（1的平方）
   * 
   * 注意：
   * - 投影后的值类型必须实现Comparable接口
   * - 如果序列为空，可能抛出异常
   * - 先对每个元素应用投影函数，然后比较投影后的值
   * - 允许基于元素的某个属性或计算结果查找最小值
   */
  public static <T, TResult> TResult min(Queryable<T> queryable,
      FunctionExpression<Function1<T, TResult>> selector) {
    throw Extensions.todo();
  }

  /**
   * Filters the elements of an IQueryable based on a
   * specified type.
   * 根据指定类型过滤IQueryable的元素。返回序列中可以转换为指定类型的元素。
   *
   * <p>This method generates a
   * {@link org.apache.calcite.linq4j.tree.MethodCallExpression} that
   * represents calling {@code ofType} itself as a constructed generic method.
   * It then passes the {@code MethodCallExpression} to the
   * {@link org.apache.calcite.linq4j.QueryProvider#createQuery createQuery}
   * method of the
   * {@link org.apache.calcite.linq4j.QueryProvider} represented by
   * the Provider property of the source parameter.
   * 此方法生成一个MethodCallExpression，表示调用ofType本身作为一个构造的泛型方法。
   * 然后将MethodCallExpression传递给源参数的Provider属性表示的QueryProvider的createQuery方法。
   *
   * <p>The query behavior that occurs as a result of executing an expression
   * tree that represents calling OfType depends on the implementation of the
   * type of the source parameter. The expected behavior is that it filters
   * out any elements in source that are not of type TResult.
   * 执行表示调用OfType的表达式树所产生的查询行为取决于源参数类型的实现。
   * 预期行为是过滤掉源中所有不是TResult类型的元素。
   *
   * <p>NOTE: clazz parameter not present in C# LINQ; necessary because of
   * Java type erasure.
   * 注意：clazz参数在C# LINQ中不存在；由于Java类型擦除，这是必需的。
   * 
   * @param <TResult> 目标类型
   * @param queryable 要过滤的查询序列
   * @param clazz 目标类型的Class对象
   * @return 包含可以转换为目标类型的元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * ofType([1, "hello", 2.5, "world"], String.class) 返回 ["hello", "world"]
   * ofType([1, 2, 3, 4.5], Integer.class) 返回 [1, 2, 3]
   * 
   * 注意：
   * - 只保留可以转换为目标类型的元素
   * - 与cast方法不同，ofType会过滤掉无法转换的元素，而cast会抛出异常
   * - null元素会被保留（如果目标类型是引用类型）
   * - 使用instanceof操作符进行类型检查
   * - 常用于从混合类型的集合中提取特定类型的元素
   */
  public static <TResult> Queryable<TResult> ofType(Queryable<?> queryable,
      Class<TResult> clazz) {
    throw Extensions.todo();
  }

  /**
   * Sorts the elements of a sequence in ascending
   * order according to a key.
   * 根据键按升序对序列的元素进行排序。
   *
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型，必须实现Comparable接口
   * @param source 要排序的查询序列
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * orderBy([3, 1, 4, 1, 5], x -> x) 返回 [1, 1, 3, 4, 5]
   * orderBy(["banana", "apple", "cherry"], s -> s) 返回 ["apple", "banana", "cherry"]
   * 
   * 注意：
   * - 键类型必须实现Comparable接口
   * - 返回OrderedQueryable，支持后续的thenBy操作进行多级排序
   * - 使用默认的比较逻辑（Comparable.compareTo）
   * - 升序排序
   * - 与SQL中的ORDER BY ASC子句功能相同
   * 
   * @see #thenBy 用于多级排序
   */
  public static <T, TKey extends Comparable> OrderedQueryable<T> orderBy(
      Queryable<T> source, FunctionExpression<Function1<T, TKey>> keySelector) {
    throw Extensions.todo();
  }

  /**
   * Sorts the elements of a sequence in ascending
   * order by using a specified comparer.
   * 使用指定的比较器按升序对序列的元素进行排序。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型
   * @param source 要排序的查询序列
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @param comparator 用于比较键的比较器
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 使用指定的比较器来比较键，而不是默认的Comparable
   * - 允许自定义比较逻辑，例如忽略大小写、自定义排序规则等
   * - 返回OrderedQueryable，支持后续的thenBy操作进行多级排序
   * - 升序排序
   * - 对于null键，比较器需要处理null情况
   */
  public static <T, TKey> OrderedQueryable<T> orderBy(Queryable<T> source,
      FunctionExpression<Function1<T, TKey>> keySelector,
      Comparator<TKey> comparator) {
    throw Extensions.todo();
  }

  /**
   * Sorts the elements of a sequence in descending
   * order according to a key.
   * 根据键按降序对序列的元素进行排序。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型，必须实现Comparable接口
   * @param source 要排序的查询序列
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * orderByDescending([3, 1, 4, 1, 5], x -> x) 返回 [5, 4, 3, 1, 1]
   * orderByDescending(["banana", "apple", "cherry"], s -> s) 返回 ["cherry", "banana", "apple"]
   * 
   * 注意：
   * - 键类型必须实现Comparable接口
   * - 返回OrderedQueryable，支持后续的thenByDescending操作进行多级排序
   * - 使用默认的比较逻辑（Comparable.compareTo）
   * - 降序排序
   * - 与SQL中的ORDER BY DESC子句功能相同
   */
  public static <T, TKey extends Comparable> OrderedQueryable<T> orderByDescending(
      Queryable<T> source,
      FunctionExpression<Function1<T, TKey>> keySelector) {
    throw Extensions.todo();
  }

  /**
   * Sorts the elements of a sequence in descending
   * order by using a specified comparer.
   * 使用指定的比较器按降序对序列的元素进行排序。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型
   * @param source 要排序的查询序列
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @param comparator 用于比较键的比较器
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 使用指定的比较器来比较键，而不是默认的Comparable
   * - 允许自定义比较逻辑，例如忽略大小写、自定义排序规则等
   * - 返回OrderedQueryable，支持后续的thenByDescending操作进行多级排序
   * - 降序排序
   * - 对于null键，比较器需要处理null情况
   */
  public static <T, TKey> OrderedQueryable<T> orderByDescending(
      Queryable<T> source, FunctionExpression<Function1<T, TKey>> keySelector,
      Comparator<TKey> comparator) {
    throw Extensions.todo();
  }

  /**
   * Inverts the order of the elements in a
   * sequence.
   * 反转序列中元素的顺序。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要反转的查询序列
   * @return 元素顺序反转后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * reverse([1, 2, 3, 4, 5]) 返回 [5, 4, 3, 2, 1]
   * reverse(["a", "b", "c"]) 返回 ["c", "b", "a"]
   * 
   * 注意：
   * - 完全反转序列的顺序
   * - 不修改原始序列
   * - 如果序列为空，返回空序列
   * - 如果序列只有一个元素，返回相同的序列
   * - 常用于需要反向遍历序列的场景
   */
  public static <T> Queryable<T> reverse(Queryable<T> source) {
    throw Extensions.todo();
  }

  /**
   * Projects each element of a sequence into a new form.
   * 将序列中的每个元素投影到新形式。这是LINQ中最常用的操作之一，用于转换序列中的每个元素。
   * 
   * @param <T> 源序列中元素的类型
   * @param <TResult> 投影后元素的类型
   * @param source 要投影的查询序列
   * @param selector 投影函数，将每个元素转换为新形式
   * @return 投影后的查询序列
   * 
   * 示例：
   * select([1, 2, 3], x -> x * 2) 返回 [2, 4, 6]
   * select(["a", "b", "c"], s -> s.toUpperCase()) 返回 ["A", "B", "C"]
   * select([1, 2, 3], x -> "Number: " + x) 返回 ["Number: 1", "Number: 2", "Number: 3"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 使用表达式树构建select调用，支持延迟执行
   * - 通过source.getProvider().createQuery创建新的查询
   * - 使用Expressions.call构建方法调用表达式
   * - functionResultType方法从selector中提取返回类型
   * - 与SQL中的SELECT子句功能相同
   * - 可以用于提取对象的属性、计算衍生值或完全转换数据结构
   */
  public static <T, TResult> Queryable<TResult> select(Queryable<T> source,
      FunctionExpression<Function1<T, TResult>> selector) {
    return source.getProvider().createQuery(
        Expressions.call(requireNonNull(source.getExpression()), "select", selector),
        functionResultType(selector));
  }

  private static <P0, R> Type functionResultType(
      FunctionExpression<Function1<P0, R>> selector) {
    return requireNonNull(selector.body, "selector.body").getType();
  } // 从函数表达式中提取返回类型的私有辅助方法，用于确定select操作的返回类型

  /**
   * Projects each element of a sequence into a new
   * form by incorporating the element's index.
   *
   * <p>NOTE: Renamed from {@code select} because had same erasure as
   * {@link #select(org.apache.calcite.linq4j.Queryable, org.apache.calcite.linq4j.tree.FunctionExpression)}.
   * 将序列中的每个元素投影到新形式，并在投影中包含元素的索引。
   * 
   * @param <T> 源序列中元素的类型
   * @param <TResult> 投影后元素的类型
   * @param source 要投影的查询序列
   * @param selector 投影函数，接受元素和索引，返回转换后的元素
   * @return 投影后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * selectN([10, 20, 30], (x, i) -> i + ": " + x) 返回 ["0: 10", "1: 20", "2: 30"]
   * selectN(["a", "b", "c"], (s, i) -> s + i) 返回 ["a0", "b1", "c2"]
   * 
   * 注意：
   * - 索引从0开始
   * - selector函数接受两个参数：元素和索引
   * - 重命名为selectN是因为与select方法有相同的类型擦除
   * - Java的类型擦除导致不能通过参数类型区分这两个方法
   * - 常用于需要知道元素位置的场景
   */
  public static <T, TResult> Queryable<TResult> selectN(Queryable<T> source,
      FunctionExpression<Function2<T, Integer, TResult>> selector) {
    throw Extensions.todo();
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<T>} and combines the resulting sequences into one
   * sequence.
   * 将序列的每个元素投影到一个Enumerable<T>，并将结果序列合并为一个序列。
   * 
   * @param <T> 源序列中元素的类型
   * @param <TResult> 投影后序列中元素的类型
   * @param source 要投影的查询序列
   * @param selector 投影函数，将每个元素转换为一个Enumerable序列
   * @return 合并后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * selectMany([[1, 2], [3, 4], [5]], list -> list) 返回 [1, 2, 3, 4, 5]
   * selectMany(["ab", "cd"], s -> s.toCharArray()) 返回 ['a', 'b', 'c', 'd']
   * 
   * 注意：
   * - 将二维结构展平为一维结构
   * - 保留原始顺序
   * - 常用于处理嵌套的集合结构
   * - 与SQL中的JOIN操作类似，但更简单
   * - 也称为flatMap操作
   */
  public static <T, TResult> Queryable<TResult> selectMany(Queryable<T> source,
      FunctionExpression<Function1<T, Enumerable<TResult>>> selector) {
    throw Extensions.todo();
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<T>} and combines the resulting sequences into one
   * sequence. The index of each source element is used in the
   * projected form of that element.
   * 将序列的每个元素投影到一个Enumerable<T>，并将结果序列合并为一个序列。源元素的索引用于该元素的投影形式。
   *
   * <p>NOTE: Renamed from {@code selectMany} because had same erasure as
   * {@link #selectMany(org.apache.calcite.linq4j.Queryable, org.apache.calcite.linq4j.tree.FunctionExpression)}.
   * 
   * @param <T> 源序列中元素的类型
   * @param <TResult> 投影后序列中元素的类型
   * @param source 要投影的查询序列
   * @param selector 投影函数，接受元素和索引，返回一个Enumerable序列
   * @return 合并后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * selectManyN([[1, 2], [3, 4]], (list, i) -> list.map(x -> x + i)) 返回 [1, 3, 4, 6]
   * 
   * 注意：
   * - 索引从0开始
   * - selector函数接受两个参数：元素和索引
   * - 重命名为selectManyN是因为与selectMany方法有相同的类型擦除
   * - Java的类型擦除导致不能通过参数类型区分这两个方法
   * - 将二维结构展平为一维结构，同时考虑元素索引
   */
  public static <T, TResult> Queryable<TResult> selectManyN(Queryable<T> source,
      FunctionExpression<Function2<T, Integer, Enumerable<TResult>>> selector) {
    throw Extensions.todo();
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<T>} that incorporates the index of the source
   * element that produced it. A result selector function is invoked
   * on each element of each intermediate sequence, and the
   * resulting values are combined into a single, one-dimensional
   * sequence and returned.
   */
  public static <T, TCollection, TResult> Queryable<TResult> selectMany(
      Queryable<T> source,
      FunctionExpression<Function2<T, Integer, Enumerable<TCollection>>>
        collectionSelector,
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) {
    throw Extensions.todo();
  }

  /**
   * Projects each element of a sequence to an
   * {@code Enumerable<T>} and invokes a result selector function on each
   * element therein. The resulting values from each intermediate
   * sequence are combined into a single, one-dimensional sequence
   * and returned.
   *
   * <p>NOTE: Renamed from {@code selectMany} because had same erasure as
   * {@link #selectMany(org.apache.calcite.linq4j.Queryable, org.apache.calcite.linq4j.tree.FunctionExpression, org.apache.calcite.linq4j.tree.FunctionExpression)}.
   */
  public static <T, TCollection, TResult> Queryable<TResult> selectManyN(
      Queryable<T> source,
      FunctionExpression<Function1<T, Enumerable<TCollection>>>
        collectionSelector,
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector) {
    throw Extensions.todo();
  }

  /**
   * Determines whether two sequences are equal by
   * using the default equality comparer to compare
   * elements.
   * 使用默认的相等比较器确定两个序列是否相等。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 第一个查询序列
   * @param enumerable 第二个可枚举序列
   * @return 如果两个序列相等，返回true；否则返回false
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * sequenceEqual([1, 2, 3], [1, 2, 3]) 返回 true
   * sequenceEqual([1, 2, 3], [1, 2, 4]) 返回 false
   * sequenceEqual([1, 2, 3], [1, 2]) 返回 false（长度不同）
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）来比较元素
   * - 两个序列必须长度相同且对应位置的元素都相等才返回true
   * - 元素的顺序也很重要，[1, 2]和[2, 1]不相等
   * - 如果两个序列都为空，返回true
   * - 这是一个严格的序列相等性检查
   */
  public static <T> boolean sequenceEqual(Queryable<T> queryable,
      Enumerable<T> enumerable) {
    throw Extensions.todo();
  }

  /**
   * Determines whether two sequences are equal by
   * using a specified {@code EqualityComparer<T>} to compare
   * elements.
   * 使用指定的相等比较器确定两个序列是否相等。
   * 
   * @param <T> 序列中元素的类型
   * @param queryable 第一个查询序列
   * @param enumerable 第二个可枚举序列
   * @param comparer 用于比较元素的相等比较器
   * @return 如果两个序列相等，返回true；否则返回false
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 使用指定的比较器来比较元素，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 两个序列必须长度相同且对应位置的元素都相等才返回true
   * - 元素的顺序也很重要
   * - 对于null元素，比较器需要处理null情况
   */
  public static <T> boolean sequenceEqual(Queryable<T> queryable,
      Enumerable<T> enumerable, EqualityComparer<T> comparer) {
    throw Extensions.todo();
  }

  /**
   * Returns the only element of a sequence, and throws
   * an exception if there is not exactly one element in the
   * sequence.
   * 返回序列中的唯一元素，如果序列中不恰好包含一个元素，则抛出异常。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要查询的序列
   * @return 序列中的唯一元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * single([42]) 返回 42
   * single(["hello"]) 返回 "hello"
   * single([]) 抛出异常（序列为空）
   * single([1, 2]) 抛出异常（序列有多个元素）
   * 
   * 注意：
   * - 如果序列为空，抛出异常
   * - 如果序列包含多个元素，抛出异常
   * - 只有当序列恰好包含一个元素时，才返回该元素
   * - 与first或last不同，single要求序列必须恰好有一个元素
   * - 常用于确保查询结果唯一性的场景
   */
  public static <T> T single(Queryable<T> source) {
    throw Extensions.todo();
  }

  /**
   * Returns the only element of a sequence that
   * satisfies a specified condition, and throws an exception if
   * more than one such element exists.
   * 返回序列中满足指定条件的唯一元素，如果有多个这样的元素存在，则抛出异常。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要查询的序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 唯一一个满足条件的元素
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * single([1, 2, 3], x -> x == 2) 返回 2
   * single([1, 2, 3], x -> x > 5) 抛出异常（没有元素满足条件）
   * single([1, 2, 3], x -> x > 0) 抛出异常（多个元素满足条件）
   * 
   * 注意：
   * - 如果没有元素满足条件，抛出异常
   * - 如果有多个元素满足条件，抛出异常
   * - 只有当恰好有一个元素满足条件时，才返回该元素
   * - 常用于确保查询结果唯一性的场景
   */
  public static <T> T single(Queryable<T> source,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Returns the only element of a sequence, or a
   * default value if the sequence is empty; this method throws an
   * exception if there is more than one element in the
   * sequence.
   * 返回序列中的唯一元素，如果序列为空，则返回默认值；如果序列中有多个元素，则抛出异常。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要查询的序列
   * @return 序列中的唯一元素，如果序列为空，返回类型默认值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * singleOrDefault([42]) 返回 42
   * singleOrDefault([]) 返回 null（对于引用类型）
   * singleOrDefault([]) 返回 0（对于int类型）
   * singleOrDefault([1, 2]) 抛出异常（序列有多个元素）
   * 
   * 注意：
   * - 如果序列为空，返回类型默认值（引用类型为null，基本类型为0、false等）
   * - 如果序列包含多个元素，抛出异常
   * - 只有当序列恰好包含一个元素时，才返回该元素
   * - 与single方法不同，此方法允许序列为空
   */
  public static <T> T singleOrDefault(Queryable<T> source) {
    throw Extensions.todo();
  }

  /**
   * Returns the only element of a sequence that
   * satisfies a specified condition or a default value if no such
   * element exists; this method throws an exception if more than
   * one element satisfies the condition.
   * 返回序列中满足指定条件的唯一元素，如果没有这样的元素存在，则返回默认值；
   * 如果有多个元素满足条件，则抛出异常。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要查询的序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 唯一一个满足条件的元素，如果没有，返回类型默认值
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * singleOrDefault([1, 2, 3], x -> x == 2) 返回 2
   * singleOrDefault([1, 2, 3], x -> x > 5) 返回 null（没有元素满足条件）
   * singleOrDefault([1, 2, 3], x -> x > 0) 抛出异常（多个元素满足条件）
   * 
   * 注意：
   * - 如果没有元素满足条件，返回类型默认值
   * - 如果有多个元素满足条件，抛出异常
   * - 只有当恰好有一个元素满足条件时，才返回该元素
   * - 与single方法不同，此方法允许没有元素满足条件
   */
  public static <T> T singleOrDefault(Queryable<T> source,
      FunctionExpression<Predicate1<T>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Bypasses a specified number of elements in a
   * sequence and then returns the remaining elements.
   * 跳过序列中指定数量的元素，然后返回剩余的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要跳过元素的查询序列
   * @param count 要跳过的元素数量
   * @return 跳过指定数量元素后的查询序列
   * 
   * 示例：
   * skip([1, 2, 3, 4, 5], 2) 返回 [3, 4, 5]
   * skip(["a", "b", "c", "d"], 1) 返回 ["b", "c", "d"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 使用EnumerableDefaults.skip实现实际跳过逻辑
   * - 将Queryable转换为Enumerable，执行跳过操作，再转换回Queryable
   * - 如果count大于或等于序列长度，返回空序列
   * - 如果count为0或负数，返回原序列
   * - 与SQL中的OFFSET子句功能相同
   */
  public static <T> Queryable<T> skip(Queryable<T> source, int count) {
    return EnumerableDefaults.skip(source.asEnumerable(), count).asQueryable();
  }

  /**
   * Bypasses elements in a sequence as long as a
   * specified condition is true and then returns the remaining
   * elements.
   * 只要满足指定条件就跳过序列中的元素，然后返回剩余的元素。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要跳过元素的查询序列
   * @param predicate 谓词函数，用于测试元素是否应该被跳过
   * @return 跳过满足条件的元素后的查询序列
   * 
   * 示例：
   * skipWhile([1, 2, 3, 4, 5], x -> x < 3) 返回 [3, 4, 5]
   * skipWhile(["a", "b", "c", "d"], s -> s.compareTo("c") < 0) 返回 ["c", "d"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 一旦遇到不满足条件的元素，就停止跳过，返回剩余的所有元素
   * - 使用Functions.toPredicate2将Predicate1转换为Predicate2
   * - 使用Expressions.lambda创建新的lambda表达式
   * - 委托给skipWhileN方法执行实际跳过操作
   */
  public static <T> Queryable<T> skipWhile(Queryable<T> source,
      FunctionExpression<Predicate1<T>> predicate) {
    return skipWhileN(source,
        Expressions.lambda(
            Functions.toPredicate2(predicate.getFunction())));
  }

  /**
   * Bypasses elements in a sequence as long as a
   * specified condition is true and then returns the remaining
   * elements. The element's index is used in the logic of the
   * predicate function.
   * 只要满足指定条件就跳过序列中的元素，然后返回剩余的元素。谓词函数的逻辑中使用元素的索引。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要跳过元素的查询序列
   * @param predicate 谓词函数，接受元素和索引，返回是否应该跳过该元素
   * @return 跳过满足条件的元素后的查询序列
   * 
   * 示例：
   * skipWhileN([10, 20, 30, 40], (x, i) -> x < i * 15) 返回 [30, 40]
   * skipWhileN(["a", "b", "c"], (s, i) -> i < 2) 返回 ["c"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 一旦遇到不满足条件的元素，就停止跳过，返回剩余的所有元素
   * - 索引从0开始
   * - 使用BaseQueryable创建新的查询实例
   * - 使用EnumerableDefaults.SkipWhileEnumerator执行实际的跳过逻辑
   * - 重命名为skipWhileN是因为与skipWhile方法有相同的类型擦除
   */
  public static <T> Queryable<T> skipWhileN(final Queryable<T> source,
      final FunctionExpression<Predicate2<T, Integer>> predicate) {
    return new BaseQueryable<T>(source.getProvider(), source.getElementType(),
        source.getExpression()) {
      @Override public Enumerator<T> enumerator() {
        return new EnumerableDefaults.SkipWhileEnumerator<>(
            source.enumerator(), predicate.getFunction());
      } // 创建SkipWhileEnumerator，使用谓词函数执行跳过逻辑
    }; // 创建新的BaseQueryable实例，保留原始查询的提供者、元素类型和表达式
  }

  /**
   * Computes the sum of the sequence of Decimal values
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   * 计算BigDecimal值序列的总和。通过对输入序列的每个元素调用投影函数获得BigDecimal值序列，然后计算总和。
   * 
   * @param <T> 序列中元素的类型
   * @param sources 要计算总和的查询序列
   * @param selector 投影函数，从每个元素中提取BigDecimal值
   * @return 序列中BigDecimal值的总和
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * sumBigDecimal(["1.5", "2.5", "3.5"], BigDecimal::new) 返回 7.5
   * 
   * 注意：
   * - BigDecimal提供高精度的十进制运算，适合财务计算
   * - 如果序列为空，返回0
   * - 总和 = 所有值的累加
   * - 使用BigDecimal可以避免浮点数精度问题
   * - 与SQL中的SUM聚合函数功能相同
   */
  public static <T> BigDecimal sumBigDecimal(Queryable<T> sources,
      FunctionExpression<BigDecimalFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of nullable
   * Decimal values that is obtained by invoking a projection
   * function on each element of the input sequence.
   * 计算可空BigDecimal值序列的总和。通过对输入序列的每个元素调用投影函数获得可空BigDecimal值序列，然后计算总和。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要计算总和的查询序列
   * @param selector 投影函数，从每个元素中提取可空BigDecimal值（可能返回null）
   * @return 序列中可空BigDecimal值的总和，如果所有值都为null或序列为空，返回null
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * sumNullableBigDecimal([1.5, null, 3.5], x -> x) 返回 5.0（忽略null值）
   * sumNullableBigDecimal([null, null], x -> x) 返回 null
   * 
   * 注意：
   * - 可空值序列中的null值在计算总和时会被忽略
   * - 如果所有值都是null，返回null
   * - 如果序列为空，返回null
   * - 只计算非null值的总和
   */
  public static <T> BigDecimal sumNullableBigDecimal(Queryable<T> source,
      FunctionExpression<NullableBigDecimalFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of Double values
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   * 计算Double值序列的总和。通过对输入序列的每个元素调用投影函数获得Double值序列，然后计算总和。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要计算总和的查询序列
   * @param selector 投影函数，从每个元素中提取Double值
   * @return 序列中Double值的总和
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * sumDouble([1.5, 2.5, 3.5], x -> x) 返回 7.5
   * 
   * 注意：
   * - Double是64位浮点数，提供双精度计算
   * - 如果序列为空，返回0
   * - 总和 = 所有值的累加
   * - 浮点数运算可能存在精度误差
   * - 与SQL中的SUM聚合函数功能相同
   */
  public static <T> double sumDouble(Queryable<T> source,
      FunctionExpression<DoubleFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of nullable
   * Double values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */
  public static <T> Double sumNullableDouble(Queryable<T> source,
      FunctionExpression<NullableDoubleFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of int values
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */
  public static <T> int sumInteger(Queryable<T> source,
      FunctionExpression<IntegerFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of nullable int
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   */
  public static <T> Integer sumNullableInteger(Queryable<T> source,
      FunctionExpression<NullableIntegerFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of long values
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */
  public static <T> long sumLong(Queryable<T> source,
      FunctionExpression<LongFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of nullable long
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   */
  public static <T> Long sumNullableLong(Queryable<T> source,
      FunctionExpression<NullableLongFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of Float values
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */
  public static <T> float sumFloat(Queryable<T> source,
      FunctionExpression<FloatFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Computes the sum of the sequence of nullable
   * Float values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */
  public static <T> Float sumNullableFloat(Queryable<T> source,
      FunctionExpression<NullableFloatFunction1<T>> selector) {
    throw Extensions.todo();
  }

  /**
   * Returns a specified number of contiguous elements
   * from the start of a sequence.
   * 从序列开头返回指定数量的连续元素。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要获取元素的查询序列
   * @param count 要获取的元素数量
   * @return 包含指定数量元素的查询序列
   * 
   * 示例：
   * take([1, 2, 3, 4, 5], 3) 返回 [1, 2, 3]
   * take(["a", "b", "c", "d"], 2) 返回 ["a", "b"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 使用EnumerableDefaults.take实现实际获取逻辑
   * - 将Queryable转换为Enumerable，执行获取操作，再转换回Queryable
   * - 如果count大于或等于序列长度，返回整个序列
   * - 如果count为0或负数，返回空序列
   * - 与SQL中的LIMIT子句功能相同
   */
  public static <T> Queryable<T> take(Queryable<T> source, int count) {
    return EnumerableDefaults.take(source.asEnumerable(), count).asQueryable();
  }

  /**
   * Returns elements from a sequence as long as a
   * specified condition is true.
   * 只要满足指定条件就从序列中返回元素。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要获取元素的查询序列
   * @param predicate 谓词函数，用于测试元素是否应该被包含
   * @return 包含满足条件元素的查询序列，直到第一个不满足条件的元素
   * 
   * 示例：
   * takeWhile([1, 2, 3, 4, 5], x -> x < 4) 返回 [1, 2, 3]
   * takeWhile(["a", "b", "c", "d"], s -> s.compareTo("c") < 0) 返回 ["a", "b"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 一旦遇到不满足条件的元素，就停止返回，即使后面还有满足条件的元素
   * - 使用Functions.toPredicate2将Predicate1转换为Predicate2
   * - 使用Expressions.lambda创建新的lambda表达式
   * - 委托给takeWhileN方法执行实际获取操作
   */
  public static <T> Queryable<T> takeWhile(Queryable<T> source,
      FunctionExpression<Predicate1<T>> predicate) {
    return takeWhileN(source,
        Expressions.lambda(
            Functions.toPredicate2(predicate.getFunction())));
  }

  /**
   * Returns elements from a sequence as long as a
   * specified condition is true. The element's index is used in the
   * logic of the predicate function.
   * 只要满足指定条件就从序列中返回元素。谓词函数的逻辑中使用元素的索引。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要获取元素的查询序列
   * @param predicate 谓词函数，接受元素和索引，返回是否应该包含该元素
   * @return 包含满足条件元素的查询序列，直到第一个不满足条件的元素
   * 
   * 示例：
   * takeWhileN([10, 20, 30, 40], (x, i) -> x < i * 15) 返回 [10, 20]
   * takeWhileN(["a", "b", "c"], (s, i) -> i < 2) 返回 ["a", "b"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 一旦遇到不满足条件的元素，就停止返回，即使后面还有满足条件的元素
   * - 索引从0开始
   * - 使用BaseQueryable创建新的查询实例
   * - 使用EnumerableDefaults.TakeWhileEnumerator执行实际的获取逻辑
   * - 重命名为takeWhileN是因为与takeWhile方法有相同的类型擦除
   */
  public static <T> Queryable<T> takeWhileN(final Queryable<T> source,
      final FunctionExpression<Predicate2<T, Integer>> predicate) {
    return new BaseQueryable<T>(source.getProvider(), source.getElementType(),
        source.getExpression()) {
      @Override public Enumerator<T> enumerator() {
        return new EnumerableDefaults.TakeWhileEnumerator<>(
            source.enumerator(), predicate.getFunction());
      } // 创建TakeWhileEnumerator，使用谓词函数执行获取逻辑
    }; // 创建新的BaseQueryable实例，保留原始查询的提供者、元素类型和表达式
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key.
   * 对序列中的元素执行后续的升序排序，根据键进行排序。用于多级排序。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型，必须实现Comparable接口
   * @param source 已排序的查询序列（OrderedQueryable）
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * orderBy(p -> p.age).thenBy(p -> p.name) 先按年龄排序，年龄相同的按姓名排序
   * 
   * 注意：
   * - 只能对OrderedQueryable调用，不能对普通Queryable调用
   * - 必须在orderBy或orderByDescending之后使用
   * - 用于实现多级排序（二级、三级等）
   * - 升序排序
   * - 如果前面的排序键相同，则使用此键进行排序
   */
  public static <T, TKey extends Comparable<TKey>> OrderedQueryable<T> thenBy(
      OrderedQueryable<T> source,
      FunctionExpression<Function1<T, TKey>> keySelector) {
    throw Extensions.todo();
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * ascending order according to a key, using a specified comparator.
   * 使用指定的比较器对序列中的元素执行后续的升序排序，根据键进行排序。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型
   * @param source 已排序的查询序列（OrderedQueryable）
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @param comparator 用于比较键的比较器
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 只能对OrderedQueryable调用
   * - 必须在orderBy或orderByDescending之后使用
   * - 使用指定的比较器来比较键
   * - 允许自定义比较逻辑
   * - 用于实现多级排序
   */
  public static <T, TKey> OrderedQueryable<T> thenBy(OrderedQueryable<T> source,
      FunctionExpression<Function1<T, TKey>> keySelector,
      Comparator<TKey> comparator) {
    throw Extensions.todo();
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * descending order according to a key.
   * 对序列中的元素执行后续的降序排序，根据键进行排序。用于多级排序。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型，必须实现Comparable接口
   * @param source 已排序的查询序列（OrderedQueryable）
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * orderBy(p -> p.age).thenByDescending(p -> p.salary) 先按年龄升序，年龄相同的按薪资降序
   * 
   * 注意：
   * - 只能对OrderedQueryable调用
   * - 必须在orderBy或orderByDescending之后使用
   * - 用于实现多级排序
   * - 降序排序
   * - 如果前面的排序键相同，则使用此键进行排序
   */
  public static <T, TKey extends Comparable<TKey>> OrderedQueryable<T> thenByDescending(
      OrderedQueryable<T> source,
      FunctionExpression<Function1<T, TKey>> keySelector) {
    throw Extensions.todo();
  }

  /**
   * Performs a subsequent ordering of the elements in a sequence in
   * dscending order according to a key, using a specified comparator.
   * 使用指定的比较器对序列中的元素执行后续的降序排序，根据键进行排序。
   * 
   * @param <T> 序列中元素的类型
   * @param <TKey> 键的类型
   * @param source 已排序的查询序列（OrderedQueryable）
   * @param keySelector 键选择器函数，从每个元素中提取排序键
   * @param comparator 用于比较键的比较器
   * @return 排序后的可排序查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 只能对OrderedQueryable调用
   * - 必须在orderBy或orderByDescending之后使用
   * - 使用指定的比较器来比较键
   * - 允许自定义比较逻辑
   * - 用于实现多级排序
   */
  public static <T, TKey> OrderedQueryable<T> thenByDescending(
      OrderedQueryable<T> source,
      FunctionExpression<Function1<T, TKey>> keySelector,
      Comparator<TKey> comparator) {
    throw Extensions.todo();
  }

  /**
   * Produces the set union of two sequences by using
   * the default equality comparer.
   * 使用默认的相等比较器生成两个序列的集合并集。
   * 
   * @param <T> 序列中元素的类型
   * @param source0 第一个查询序列
   * @param source1 第二个可枚举序列
   * @return 包含两个序列中所有不同元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * union([1, 2, 3], [3, 4, 5]) 返回 [1, 2, 3, 4, 5]（3只出现一次）
   * union(["a", "b"], ["b", "c"]) 返回 ["a", "b", "c"]
   * 
   * 注意：
   * - 使用默认的相等比较器（通常是equals方法）来判断元素是否相等
   * - 返回的序列中每个元素只出现一次（去重）
   * - 保留元素首次出现的顺序
   * - 与concat方法不同，union会去重，concat保留所有元素
   * - 与SQL中的UNION操作符功能相同
   */
  public static <T> Queryable<T> union(Queryable<T> source0,
      Enumerable<T> source1) {
    throw Extensions.todo();
  }

  /**
   * Produces the set union of two sequences by using a
   * specified {@code EqualityComparer<T>}.
   * 使用指定的相等比较器生成两个序列的集合并集。
   * 
   * @param <T> 序列中元素的类型
   * @param source0 第一个查询序列
   * @param source1 第二个可枚举序列
   * @param comparer 用于比较元素的相等比较器
   * @return 包含两个序列中所有不同元素的序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 注意：
   * - 使用指定的比较器来判断元素是否相等，而不是默认的equals方法
   * - 允许自定义比较逻辑，例如忽略大小写、自定义相等规则等
   * - 返回的序列中每个元素只出现一次（去重）
   * - 保留元素首次出现的顺序
   * - 对于null元素，比较器需要处理null情况
   */
  public static <T> Queryable<T> union(Queryable<T> source0,
      Enumerable<T> source1, EqualityComparer<T> comparer) {
    throw Extensions.todo();
  }

  /**
   * Filters a sequence of values based on a
   * predicate.
   * 基于谓词过滤值序列。返回满足指定条件的元素序列。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要过滤的查询序列
   * @param predicate 谓词函数，用于测试每个元素是否满足条件
   * @return 包含满足条件的元素的查询序列
   * 
   * 示例：
   * where([1, 2, 3, 4, 5], x -> x > 2) 返回 [3, 4, 5]
   * where(["apple", "banana", "cherry"], s -> s.startsWith("a")) 返回 ["apple", "apricot"]
   * 
   * 注意：
   * - 这是已实现的少数方法之一，不是抛出todo异常
   * - 使用NonLeafReplayableQueryable创建可重放的查询
   * - 支持延迟执行和查询重放
   * - replay方法用于在QueryableFactory上重新创建查询
   * - 与SQL中的WHERE子句功能相同
   * - 只保留满足谓词条件的元素
   * - 不修改原始序列
   */
  public static <T> Queryable<T> where(final Queryable<T> source,
      final FunctionExpression<Predicate1<T>> predicate) {
    return new NonLeafReplayableQueryable<T>(source) {
      @Override public void replay(QueryableFactory<T> factory) {
        factory.where(source, predicate);
      }
    };
  }

  /**
   * Filters a sequence of values based on a
   * predicate. Each element's index is used in the logic of the
   * predicate function.
   * 基于谓词过滤值序列，谓词函数中使用每个元素的索引。
   * 
   * @param <T> 序列中元素的类型
   * @param source 要过滤的查询序列
   * @param predicate 谓词函数，接受元素和索引，返回是否满足条件
   * @return 包含满足条件的元素的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * whereN([10, 20, 30, 40, 50], (x, i) -> x > i * 10) 返回 [20, 30, 40, 50]
   * whereN(["a", "b", "c"], (s, i) -> i > 0) 返回 ["b", "c"]
   * 
   * 注意：
   * - 索引从0开始
   * - predicate函数接受两个参数：元素和索引
   * - 重命名为whereN是因为与where方法有相同的类型擦除
   * - Java的类型擦除导致不能通过参数类型区分这两个方法
   * - 常用于需要根据元素位置进行过滤的场景
   */
  public static <T> Queryable<T> whereN(Queryable<T> source,
      FunctionExpression<Predicate2<T, Integer>> predicate) {
    throw Extensions.todo();
  }

  /**
   * Merges two sequences by using the specified
   * predicate function.
   * 使用指定的谓词函数合并两个序列。将两个序列中对应位置的元素组合成一个新的序列。
   * 
   * @param <T0> 第一个序列中元素的类型
   * @param <T1> 第二个序列中元素的类型
   * @param <TResult> 结果序列中元素的类型
   * @param source0 第一个查询序列
   * @param source1 第二个可枚举序列
   * @param resultSelector 结果选择器函数，接受两个序列中对应位置的元素，返回结果元素
   * @return 合并后的查询序列
   * @throws Extensions.todo 表示该方法尚未实现，抛出待办异常
   * 
   * 示例：
   * zip([1, 2, 3], ["a", "b", "c"], (n, s) -> n + ": " + s) 返回 ["1: a", "2: b", "3: c"]
   * zip([1, 2], [10, 20, 30], (a, b) -> a + b) 返回 [11, 22]（以较短的序列为准）
   * 
   * 注意：
   * - 两个序列的对应位置元素会被组合
   * - 结果序列的长度等于两个输入序列中较短的那个的长度
   * - 如果一个序列比另一个长，多余的元素会被忽略
   * - 常用于将两个相关联的数据序列合并在一起
   * - 类似于Python的zip函数
   */
  public static <T0, T1, TResult> Queryable<TResult> zip(Queryable<T0> source0,
      Enumerable<T1> source1,
      FunctionExpression<Function2<T0, T1, TResult>> resultSelector) {
    throw Extensions.todo();
  }

  /** Replayable.
   * 可重放的查询接口。标记一个查询可以被重新创建或重放，这对于查询优化和表达式树构建非常重要。
   *
   * @param <T> element type 元素类型
   * 
   * 主要功能：
   * - 允许查询被重新创建，而不是立即执行
   * - 支持查询表达式树的构建和转换
   * - 用于QueryableRecorder等需要记录和重放查询的场景
   * - 提供查询的延迟执行和优化能力
   * 
   * 使用场景：
   * - 在构建SQL查询时，用于表示查询操作的语义
   * - 在需要多次执行相同查询的场景中，避免重复计算
   * - 在查询优化器中，用于分析和转换查询表达式
   */
  public interface Replayable<T> extends Queryable<T> {
    void replay(QueryableFactory<T> factory); // 在指定的工厂上重放查询，重新创建查询操作
  }

  /** Replayable queryable.
   * 可重放的查询抽象基类。提供可重放查询的默认实现，继承自DefaultQueryable并实现Replayable接口。
   *
   * @param <T> element type 元素类型
   * 
   * 主要功能：
   * - 提供可重放查询的基础实现
   * - 支持查询的延迟执行
   * - 提供类型转换的便捷方法
   * - 实现标准的查询接口方法
   * 
   * 成员方法：
   * - replay: 默认空实现，子类可以重写
   * - iterator: 返回查询的迭代器
   * - enumerator: 返回查询的枚举器，通过提供者执行查询
   * - castSingle: 将查询转换为单一结果的便捷方法
   * - castQueryable: 将查询转换为不同元素类型的便捷方法
   */
  public abstract static class ReplayableQueryable<T>
      extends DefaultQueryable<T> implements Replayable<T> {
    @Override public void replay(QueryableFactory<T> factory) {
    } // 默认空实现，子类可以重写以提供具体的重放逻辑

    @Override public Iterator<T> iterator() {
      return Linq4j.enumeratorIterator(enumerator()); // 通过枚举器创建迭代器
    } // 返回查询的迭代器，使用Linq4j工具类将枚举器转换为迭代器

    @Override public Enumerator<T> enumerator() {
      return getProvider().executeQuery(this); // 通过查询提供者执行查询并返回枚举器
    } // 返回查询的枚举器，实际执行查询操作

    /**
     * Convenience method, for {@link QueryableRecorder} methods that
     * return a scalar value such as {@code boolean} or
     * {@link BigDecimal}.
     * 便捷方法，用于QueryableRecorder中返回标量值（如boolean或BigDecimal）的方法。
     */
    @SuppressWarnings("unchecked")
    <U> U castSingle() {
      return ((Queryable<U>) (Queryable) this).single(); // 将查询转换为单一结果
    } // 将查询转换为单一结果，用于类型转换场景

    /**
     * Convenience method, for {@link QueryableRecorder} methods that
     * return a Queryable of a different element type than the source.
     * 便捷方法，用于QueryableRecorder中返回与源元素类型不同的Queryable的方法。
     */
    @SuppressWarnings("unchecked")
    public <U> Queryable<U> castQueryable() {
      return (Queryable<U>) (Queryable) this; // 将查询转换为不同元素类型的查询
    } // 将查询转换为不同元素类型的查询，用于类型转换场景
  }

  /** Non-leaf replayable queryable.
   * 非叶子可重放查询抽象类。继承自ReplayableQueryable，用于表示非叶子节点的查询操作。
   *
   * @param <T> element type 元素类型
   * 
   * 主要功能：
   * - 表示非叶子节点的查询操作（如where、select等）
   * - 保留原始查询的元数据（元素类型、表达式、提供者）
   * - 支持查询的链式组合
   * - 提供查询重放的基础设施
   * 
   * 与ReplayableQueryable的区别：
   * - ReplayableQueryable是基类，提供通用实现
   * - NonLeafReplayableQueryable专门用于非叶子节点，依赖原始查询
   * - 非叶子节点表示有子查询的查询操作
   * 
   * 成员变量：
   * - original: 原始查询，保留查询的元数据
   * 
   * 使用场景：
   * - where方法返回NonLeafReplayableQueryable实例
   * - 其他需要保留原始查询信息的操作
   */
  public abstract static class NonLeafReplayableQueryable<T>
      extends ReplayableQueryable<T> {
    private final Queryable<T> original; // 原始查询，保留查询的元数据（元素类型、表达式、提供者）

    protected NonLeafReplayableQueryable(Queryable<T> original) {
      this.original = original; // 保存原始查询引用
    } // 构造函数，接受原始查询作为参数

    @Override public Type getElementType() {
      return original.getElementType(); // 返回原始查询的元素类型
    } // 返回查询的元素类型，从原始查询中获取

    @Override public @Nullable Expression getExpression() {
      return original.getExpression(); // 返回原始查询的表达式
    } // 返回查询的表达式，从原始查询中获取，可能为null

    @Override public QueryProvider getProvider() {
      return original.getProvider(); // 返回原始查询的提供者
    } // 返回查询的提供者，从原始查询中获取
  }
}
