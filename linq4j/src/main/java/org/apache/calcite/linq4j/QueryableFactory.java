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
package org.apache.calcite.linq4j; // 指定当前类所在的包路径，属于 linq4j 模块

// 导入 BigDecimalFunction1：用于将元素转换为 BigDecimal 类型的函数接口
import org.apache.calcite.linq4j.function.BigDecimalFunction1;
// 导入 DoubleFunction1：用于将元素转换为 Double 类型的函数接口
import org.apache.calcite.linq4j.function.DoubleFunction1;
// 导入 EqualityComparer：用于比较两个元素是否相等的比较器接口
import org.apache.calcite.linq4j.function.EqualityComparer;
// 导入 FloatFunction1：用于将元素转换为 Float 类型的函数接口
import org.apache.calcite.linq4j.function.FloatFunction1;
// 导入 Function1：接受一个参数并返回结果的函数接口
import org.apache.calcite.linq4j.function.Function1;
// 导入 Function2：接受两个参数并返回结果的函数接口
import org.apache.calcite.linq4j.function.Function2;
// 导入 IntegerFunction1：用于将元素转换为 Integer 类型的函数接口
import org.apache.calcite.linq4j.function.IntegerFunction1;
// 导入 LongFunction1：用于将元素转换为 Long 类型的函数接口
import org.apache.calcite.linq4j.function.LongFunction1;
// 导入 NullableBigDecimalFunction1：用于将元素转换为可空 BigDecimal 类型的函数接口
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1;
// 导入 NullableDoubleFunction1：用于将元素转换为可空 Double 类型的函数接口
import org.apache.calcite.linq4j.function.NullableDoubleFunction1;
// 导入 NullableFloatFunction1：用于将元素转换为可空 Float 类型的函数接口
import org.apache.calcite.linq4j.function.NullableFloatFunction1;
// 导入 NullableIntegerFunction1：用于将元素转换为可空 Integer 类型的函数接口
import org.apache.calcite.linq4j.function.NullableIntegerFunction1;
// 导入 NullableLongFunction1：用于将元素转换为可空 Long 类型的函数接口
import org.apache.calcite.linq4j.function.NullableLongFunction1;
// 导入 Predicate1：接受一个参数并返回布尔值的谓词接口
import org.apache.calcite.linq4j.function.Predicate1;
// 导入 Predicate2：接受两个参数并返回布尔值的谓词接口
import org.apache.calcite.linq4j.function.Predicate2;
// 导入 FunctionExpression：表示函数表达式的类，用于 LINQ 表达式树
import org.apache.calcite.linq4j.tree.FunctionExpression;

// 导入 Nullable：表示值可以为 null 的注解
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入 PolyNull：表示多态 null 的注解，用于类型系统中
import org.checkerframework.checker.nullness.qual.PolyNull;
// 导入 Covariant：表示协变类型的注解，用于泛型协变
import org.checkerframework.framework.qual.Covariant;

// 导入 BigDecimal：用于高精度计算的数学类
import java.math.BigDecimal;
// 导入 Comparator：用于比较两个对象的接口
import java.util.Comparator;

/**
 * Queryable 对象的工厂接口，用于构建和操作可查询的数据集合
 * 该接口定义了一系列方法，用于对 Queryable 集合执行各种查询操作，如过滤、排序、分组、聚合等
 * 这些方法类似于 LINQ (Language Integrated Query) 的标准查询操作符
 *
 * @param <T> 元素类型，表示 Queryable 中元素的类型
 */
@Covariant(0) // 标记第一个类型参数为协变，允许子类返回更具体的类型
public interface QueryableFactory<T> { // 定义 QueryableFactory 接口，泛型 T 表示元素类型

  /**
   * 对序列应用累加器函数，计算聚合结果
   * 该方法从序列的第一个元素开始，依次对每个元素应用累加器函数，最终返回聚合结果
   * 如果序列为空，返回 null
   *
   * @param source 源 Queryable 序列，包含要聚合的元素
   * @param selector 累加器函数表达式，接受当前累加值和下一个元素，返回新的累加值
   * @return 聚合结果，如果序列为空则返回 null
   */
  @Nullable T aggregate(Queryable<T> source, // 源序列
      FunctionExpression<Function2<@Nullable T, T, T>> selector); // 累加器函数表达式

  /**
   * 对序列应用累加器函数，使用指定的种子值作为初始累加值
   * 该方法从指定的种子值开始，依次对每个元素应用累加器函数，最终返回聚合结果
   * 种子值作为累加的初始值，确保即使序列为空也能返回种子值
   *
   * @param source 源 Queryable 序列，包含要聚合的元素
   * @param seed 初始累加值，作为聚合的起始值
   * @param selector 累加器函数表达式，接受当前累加值和下一个元素，返回新的累加值
   * @param <TAccumulate> 累加值的类型
   * @return 聚合结果
   */
  <TAccumulate> TAccumulate aggregate(Queryable<T> source, TAccumulate seed, // 源序列和初始种子值
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> selector); // 累加器函数表达式

  /**
   * 对序列应用累加器函数，使用指定的种子值作为初始累加值，并使用选择器函数选择最终结果
   * 该方法从指定的种子值开始，依次对每个元素应用累加器函数，最后使用选择器函数从累加结果中提取最终值
   * 这种模式允许累加过程和结果转换过程分离，提供更大的灵活性
   *
   * @param source 源 Queryable 序列，包含要聚合的元素
   * @param seed 初始累加值，作为聚合的起始值
   * @param func 累加器函数表达式，接受当前累加值和下一个元素，返回新的累加值
   * @param selector 结果选择器函数表达式，接受最终的累加值，返回转换后的结果
   * @param <TAccumulate> 累加值的类型
   * @param <TResult> 最终结果的类型
   * @return 转换后的聚合结果
   */
  <TAccumulate, TResult> TResult aggregate(Queryable<T> source, // 源序列
      TAccumulate seed, // 初始种子值
      FunctionExpression<Function2<TAccumulate, T, TAccumulate>> func, // 累加器函数表达式
      FunctionExpression<Function1<TAccumulate, TResult>> selector); // 结果选择器函数表达式

  /**
   * 确定序列中的所有元素是否都满足指定条件
   * 该方法对序列中的每个元素应用谓词函数，如果所有元素都使谓词返回 true，则返回 true
   * 如果序列为空，该方法返回 true（空集的所有元素都满足任何条件）
   * 该方法在遇到第一个不满足条件的元素时会立即返回 false
   *
   * @param source 源 Queryable 序列，包含要检查的元素
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 如果所有元素都满足条件或序列为空，返回 true；否则返回 false
   */
  boolean all(Queryable<T> source, FunctionExpression<Predicate1<T>> predicate); // 源序列和谓词函数

  /**
   * 确定序列是否包含任何元素
   * 该方法检查序列是否为空，如果序列中至少有一个元素，则返回 true
   * 这是一个快速检查序列是否有元素的方法，常用于条件判断
   *
   * @param source 源 Queryable 序列，要检查的序列
   * @return 如果序列包含至少一个元素，返回 true；否则返回 false
   */
  boolean any(Queryable<T> source); // 源序列

  /**
   * 确定序列中是否存在任何元素满足指定条件
   * 该方法对序列中的每个元素应用谓词函数，如果至少有一个元素使谓词返回 true，则返回 true
   * 该方法在遇到第一个满足条件的元素时会立即返回 true
   * 如果序列为空或没有元素满足条件，则返回 false
   *
   * @param source 源 Queryable 序列，包含要检查的元素
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 如果至少有一个元素满足条件，返回 true；否则返回 false
   */
  boolean any(Queryable<T> source, FunctionExpression<Predicate1<T>> predicate); // 源序列和谓词函数

  /**
   * 计算 BigDecimal 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取 BigDecimal 值序列，然后计算这些值的平均值
   * 平均值是所有值的总和除以值的数量
   * 如果序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为 BigDecimal 值
   * @return BigDecimal 值序列的平均值
   */
  BigDecimal averageBigDecimal(Queryable<T> source, // 源序列
      FunctionExpression<BigDecimalFunction1<T>> selector); // BigDecimal 投影函数

  /**
   * 计算可空 BigDecimal 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 BigDecimal 值序列，然后计算这些值的平均值
   * 平均值是所有非 null 值的总和除以非 null 值的数量
   * 如果所有值都为 null 或序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为可空 BigDecimal 值
   * @return BigDecimal 值序列的平均值
   */
  BigDecimal averageNullableBigDecimal(Queryable<T> source, // 源序列
      FunctionExpression<NullableBigDecimalFunction1<T>> selector); // 可空 BigDecimal 投影函数

  /**
   * 计算 Double 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取 Double 值序列，然后计算这些值的平均值
   * 平均值是所有值的总和除以值的数量
   * 如果序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为 Double 值
   * @return Double 值序列的平均值
   */
  double averageDouble(Queryable<T> source, // 源序列
      FunctionExpression<DoubleFunction1<T>> selector); // Double 投影函数

  /**
   * 计算可空 Double 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 Double 值序列，然后计算这些值的平均值
   * 平均值是所有非 null 值的总和除以非 null 值的数量
   * 如果所有值都为 null 或序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为可空 Double 值
   * @return Double 值序列的平均值，如果所有值都为 null 则返回 null
   */
  Double averageNullableDouble(Queryable<T> source, // 源序列
      FunctionExpression<NullableDoubleFunction1<T>> selector); // 可空 Double 投影函数

  /**
   * 计算 int 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取 int 值序列，然后计算这些值的平均值
   * 平均值是所有值的总和除以值的数量，结果为整数（向下取整）
   * 如果序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为 int 值
   * @return int 值序列的平均值
   */
  int averageInteger(Queryable<T> source, // 源序列
      FunctionExpression<IntegerFunction1<T>> selector); // int 投影函数

  /**
   * 计算可空 int 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 int 值序列，然后计算这些值的平均值
   * 平均值是所有非 null 值的总和除以非 null 值的数量，结果为整数（向下取整）
   * 如果所有值都为 null 或序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为可空 int 值
   * @return int 值序列的平均值，如果所有值都为 null 则返回 null
   */
  Integer averageNullableInteger(Queryable<T> source, // 源序列
      FunctionExpression<NullableIntegerFunction1<T>> selector); // 可空 int 投影函数

  /**
   * 计算 Float 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取 Float 值序列，然后计算这些值的平均值
   * 平均值是所有值的总和除以值的数量
   * 如果序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为 Float 值
   * @return Float 值序列的平均值
   */
  float averageFloat(Queryable<T> source, // 源序列
      FunctionExpression<FloatFunction1<T>> selector); // Float 投影函数

  /**
   * 计算可空 Float 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 Float 值序列，然后计算这些值的平均值
   * 平均值是所有非 null 值的总和除以非 null 值的数量
   * 如果所有值都为 null 或序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为可空 Float 值
   * @return Float 值序列的平均值，如果所有值都为 null 则返回 null
   */
  Float averageNullableFloat(Queryable<T> source, // 源序列
      FunctionExpression<NullableFloatFunction1<T>> selector); // 可空 Float 投影函数

  /**
   * 计算 long 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取 long 值序列，然后计算这些值的平均值
   * 平均值是所有值的总和除以值的数量，结果为长整数（向下取整）
   * 如果序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为 long 值
   * @return long 值序列的平均值
   */
  long averageLong(Queryable<T> source, // 源序列
      FunctionExpression<LongFunction1<T>> selector); // long 投影函数

  /**
   * 计算可空 long 值序列的平均值
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 long 值序列，然后计算这些值的平均值
   * 平均值是所有非 null 值的总和除以非 null 值的数量，结果为长整数（向下取整）
   * 如果所有值都为 null 或序列为空，可能会抛出异常
   *
   * @param source 源 Queryable 序列，包含要计算平均值的元素
   * @param selector 投影函数表达式，将每个元素转换为可空 long 值
   * @return long 值序列的平均值，如果所有值都为 null 则返回 null
   */
  Long averageNullableLong(Queryable<T> source, // 源序列
      FunctionExpression<NullableLongFunction1<T>> selector); // 可空 long 投影函数

  /**
   * 连接两个序列，将两个序列的元素合并为一个序列
   * 该方法将第二个序列的所有元素追加到第一个序列的末尾，返回一个新的序列
   * 原始序列不会被修改，两个序列的相对顺序保持不变
   *
   * @param source 第一个源 Queryable 序列
   * @param source2 第二个源 Enumerable 序列
   * @return 包含两个序列所有元素的新 Queryable 序列
   */
  Queryable<T> concat(Queryable<T> source, Enumerable<T> source2); // 第一个源序列和第二个源序列

  /**
   * 确定序列是否包含指定元素，使用默认相等比较器
   * 该方法使用元素类型的默认相等比较器（通常是 equals 方法）来检查序列中是否存在指定元素
   * 该方法会遍历序列，直到找到匹配的元素或遍历完整个序列
   *
   * @param source 源 Queryable 序列，包含要检查的元素
   * @param element 要查找的元素
   * @return 如果序列中包含该元素，返回 true；否则返回 false
   */
  boolean contains(Queryable<T> source, T element); // 源序列和要查找的元素

  /**
   * 确定序列是否包含指定元素，使用指定的相等比较器
   * 该方法使用指定的 EqualityComparer 来检查序列中是否存在指定元素
   * 这允许自定义相等比较逻辑，例如比较对象的特定属性
   *
   * @param source 源 Queryable 序列，包含要检查的元素
   * @param element 要查找的元素
   * @param comparer 用于比较元素是否相等的 EqualityComparer
   * @return 如果序列中包含该元素，返回 true；否则返回 false
   */
  boolean contains(Queryable<T> source, T element, // 源序列和要查找的元素
      EqualityComparer<T> comparer); // 相等比较器

  /**
   * 返回序列中的元素数量
   * 该方法计算并返回序列中元素的总数
   * 如果序列为空，返回 0
   *
   * @param source 源 Queryable 序列，包含要计数的元素
   * @return 序列中的元素数量
   */
  int count(Queryable<T> source); // 源序列

  /**
   * 返回序列中满足指定条件的元素数量
   * 该方法对序列中的每个元素应用谓词函数，计算使谓词返回 true 的元素数量
   * 如果没有元素满足条件或序列为空，返回 0
   *
   * @param source 源 Queryable 序列，包含要计数的元素
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 满足条件的元素数量
   */
  int count(Queryable<T> source, FunctionExpression<Predicate1<T>> predicate); // 源序列和谓词函数

  /**
   * 返回指定序列的元素，如果序列为空则返回包含类型参数默认值的单例集合
   * 该方法检查序列是否为空，如果序列不为空，返回原序列
   * 如果序列为空，返回一个只包含一个元素的序列，该元素为类型 T 的默认值（对于引用类型为 null）
   *
   * @param source 源 Queryable 序列
   * @return 如果源序列不为空，返回源序列；否则返回包含默认值的单例序列
   */
  Queryable<@Nullable T> defaultIfEmpty(Queryable<T> source); // 源序列

  /**
   * 返回指定序列的元素，如果序列为空则返回包含指定值的单例集合
   * 该方法检查序列是否为空，如果序列不为空，返回原序列
   * 如果序列为空，返回一个只包含一个元素的序列，该元素为指定的值
   * 如果指定的值不为 null，则结果序列永远不会包含 null 元素
   *
   * @param source 源 Queryable 序列
   * @param value 如果源序列为空时要使用的默认值
   * @return 如果源序列不为空，返回源序列；否则返回包含指定值的单例序列
   */
  Queryable<@PolyNull T> defaultIfEmpty(Queryable<T> source, @PolyNull T value); // 源序列和默认值

  /**
   * 通过使用默认相等比较器对值进行比较返回序列中的非重复元素
   * 该方法使用元素类型的默认相等比较器（通常是 equals 方法）来去除序列中的重复元素
   * 返回的序列中每个元素都是唯一的，重复的元素只保留第一个出现的
   *
   * @param source 源 Queryable 序列，可能包含重复元素
   * @return 包含非重复元素的新 Queryable 序列
   */
  Queryable<T> distinct(Queryable<T> source); // 源序列

  /**
   * 通过使用指定的 EqualityComparer 对值进行比较返回序列中的非重复元素
   * 该方法使用指定的 EqualityComparer 来去除序列中的重复元素
   * 这允许自定义相等比较逻辑，例如基于对象的特定属性来判断是否重复
   *
   * @param source 源 Queryable 序列，可能包含重复元素
   * @param comparer 用于比较元素是否相等的 EqualityComparer
   * @return 包含非重复元素的新 Queryable 序列
   */
  Queryable<T> distinct(Queryable<T> source, EqualityComparer<T> comparer); // 源序列和相等比较器

  /**
   * 返回序列中指定索引处的元素
   * 该方法返回序列中位于指定索引位置的元素
   * 如果索引超出范围（小于 0 或大于等于序列长度），会抛出异常
   *
   * @param source 源 Queryable 序列
   * @param index 要返回的元素的从零开始的索引
   * @return 序列中指定索引处的元素
   */
  T elementAt(Queryable<T> source, int index); // 源序列和索引

  /**
   * 返回序列中指定索引处的元素，如果索引超出范围则返回默认值
   * 该方法返回序列中位于指定索引位置的元素
   * 如果索引超出范围（小于 0 或大于等于序列长度），返回类型 T 的默认值（对于引用类型为 null）
   *
   * @param source 源 Queryable 序列
   * @param index 要返回的元素的从零开始的索引
   * @return 序列中指定索引处的元素，如果索引超出范围则返回默认值
   */
  T elementAtOrDefault(Queryable<T> source, int index); // 源序列和索引

  /**
   * 生成两个序列的差集，使用默认相等比较器比较值，消除重复项
   * 该方法返回第一个序列中不存在于第二个序列中的元素
   * 使用默认相等比较器来判断元素是否相等，并自动去除结果中的重复项
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列（要从中减去元素的序列）
   * @param enumerable 第二个源 Enumerable 序列（要减去的元素）
   * @return 包含差集元素的新 Queryable 序列，无重复项
   */
  Queryable<T> except(Queryable<T> source, Enumerable<T> enumerable); // 第一个源序列和第二个源序列

  /**
   * 生成两个序列的差集，使用默认相等比较器比较值
   * 该方法返回第一个序列中不存在于第二个序列中的元素
   * 使用默认相等比较器来判断元素是否相等
   * all 参数控制是否消除结果中的重复项：如果为 true，则去除重复项；如果为 false，则保留所有重复项
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列（要从中减去元素的序列）
   * @param enumerable 第二个源 Enumerable 序列（要减去的元素）
   * @param all 如果为 true，则消除结果中的重复项；如果为 false，则保留重复项
   * @return 包含差集元素的新 Queryable 序列
   */
  Queryable<T> except(Queryable<T> source, Enumerable<T> enumerable, boolean all); // 源序列、第二个序列和是否消除重复标志

  /**
   * 生成两个序列的差集，使用指定的 EqualityComparer 比较值，消除重复项
   * 该方法返回第一个序列中不存在于第二个序列中的元素
   * 使用指定的 EqualityComparer 来判断元素是否相等，并自动去除结果中的重复项
   * 这允许自定义相等比较逻辑
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列（要从中减去元素的序列）
   * @param enumerable 第二个源 Enumerable 序列（要减去的元素）
   * @param comparer 用于比较元素是否相等的 EqualityComparer
   * @return 包含差集元素的新 Queryable 序列，无重复项
   */
  Queryable<T> except(Queryable<T> source, Enumerable<T> enumerable, // 源序列和第二个序列
      EqualityComparer<T> comparer); // 相等比较器

  /**
   * 生成两个序列的差集，使用指定的 EqualityComparer 比较值
   * 该方法返回第一个序列中不存在于第二个序列中的元素
   * 使用指定的 EqualityComparer 来判断元素是否相等
   * all 参数控制是否消除结果中的重复项：如果为 true，则去除重复项；如果为 false，则保留所有重复项
   * 这允许自定义相等比较逻辑
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列（要从中减去元素的序列）
   * @param enumerable 第二个源 Enumerable 序列（要减去的元素）
   * @param comparer 用于比较元素是否相等的 EqualityComparer
   * @param all 如果为 true，则消除结果中的重复项；如果为 false，则保留重复项
   * @return 包含差集元素的新 Queryable 序列
   */
  Queryable<T> except(Queryable<T> source, Enumerable<T> enumerable, // 源序列和第二个序列
      EqualityComparer<T> comparer, boolean all); // 相等比较器和是否消除重复标志

  /**
   * 返回序列的第一个元素
   * 该方法返回序列中的第一个元素
   * 如果序列为空，会抛出异常
   *
   * @param source 源 Queryable 序列
   * @return 序列的第一个元素
   */
  T first(Queryable<T> source); // 源序列

  /**
   * 返回序列中满足指定条件的第一个元素
   * 该方法遍历序列，返回第一个使谓词函数返回 true 的元素
   * 如果序列为空或没有元素满足条件，会抛出异常
   *
   * @param source 源 Queryable 序列
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 序列中第一个满足条件的元素
   */
  T first(Queryable<T> source, FunctionExpression<Predicate1<T>> predicate); // 源序列和谓词函数

  /**
   * 返回序列的第一个元素，如果序列不包含任何元素则返回默认值
   * 该方法返回序列中的第一个元素
   * 如果序列为空，返回类型 T 的默认值（对于引用类型为 null）
   *
   * @param source 源 Queryable 序列
   * @return 序列的第一个元素，如果序列为空则返回默认值
   */
  @Nullable T firstOrDefault(Queryable<T> source); // 源序列

  /**
   * 返回序列中满足指定条件的第一个元素，如果没有这样的元素则返回默认值
   * 该方法遍历序列，返回第一个使谓词函数返回 true 的元素
   * 如果序列为空或没有元素满足条件，返回类型 T 的默认值（对于引用类型为 null）
   *
   * @param source 源 Queryable 序列
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 序列中第一个满足条件的元素，如果没有则返回默认值
   */
  @Nullable T firstOrDefault(Queryable<T> source, // 源序列
      FunctionExpression<Predicate1<T>> predicate); // 谓词函数

  /**
   * 根据指定的键选择器函数对序列的元素进行分组
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 返回一个 Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有元素
   * 使用默认相等比较器来比较键
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param <TKey> 键的类型
   * @return Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有元素
   */
  <TKey> Queryable<Grouping<TKey, T>> groupBy(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector); // 键选择器函数

  /**
   * 根据指定的键选择器函数对序列的元素进行分组，并使用指定的比较器比较键
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 返回一个 Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有元素
   * 使用指定的 EqualityComparer 来比较键，允许自定义键的相等比较逻辑
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param comparer 用于比较键是否相等的 EqualityComparer
   * @param <TKey> 键的类型
   * @return Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有元素
   */
  <TKey> Queryable<Grouping<TKey, T>> groupBy(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * 根据指定的键选择器函数对序列的元素进行分组，并使用指定的函数投影每个组的元素
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 使用元素选择器函数对每个组的元素进行投影转换
   * 返回一个 Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有投影后的元素
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param elementSelector 元素选择器函数表达式，用于投影每个组的元素
   * @param <TKey> 键的类型
   * @param <TElement> 投影后元素的类型
   * @return Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有投影后的元素
   */
  <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 泛型方法，TKey 是键类型，TElement 是元素类型
      Queryable<T> source, FunctionExpression<Function1<T, TKey>> keySelector, // 源序列和键选择器
      FunctionExpression<Function1<T, TElement>> elementSelector); // 元素选择器

  /**
   * 根据指定的键选择器函数对序列的元素进行分组，使用指定的函数投影每个组的元素，并使用指定的比较器比较键
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 使用元素选择器函数对每个组的元素进行投影转换
   * 使用指定的 EqualityComparer 来比较键，允许自定义键的相等比较逻辑
   * 返回一个 Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有投影后的元素
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param elementSelector 元素选择器函数表达式，用于投影每个组的元素
   * @param comparer 用于比较键是否相等的 EqualityComparer
   * @param <TKey> 键的类型
   * @param <TElement> 投影后元素的类型
   * @return Grouping 对象的序列，每个 Grouping 包含一个键和该键对应的所有投影后的元素
   */
  <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 泛型方法，TKey 是键类型，TElement 是元素类型
      Queryable<T> source, FunctionExpression<Function1<T, TKey>> keySelector, // 源序列和键选择器
      FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 使用结果选择器函数从每个组的键和元素序列中创建结果值
   * 返回一个结果值的序列，每个结果值对应一个组
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param resultSelector 结果选择器函数表达式，接受组的键和元素序列，返回结果值
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个组
   */
  <TKey, TResult> Queryable<TResult> groupByK( // 泛型方法，TKey 是键类型，TResult 是结果类型
      Queryable<T> source, FunctionExpression<Function1<T, TKey>> keySelector, // 源序列和键选择器
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector); // 结果选择器

  /**
   * 根据指定的键选择器函数对序列的元素进行分组，从每个组及其键创建结果值，并使用指定的比较器比较键
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 使用结果选择器函数从每个组的键和元素序列中创建结果值
   * 使用指定的 EqualityComparer 来比较键，允许自定义键的相等比较逻辑
   * 返回一个结果值的序列，每个结果值对应一个组
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param resultSelector 结果选择器函数表达式，接受组的键和元素序列，返回结果值
   * @param comparer 用于比较键是否相等的 EqualityComparer
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个组
   */
  <TKey, TResult> Queryable<TResult> groupByK( // 泛型方法，TKey 是键类型，TResult 是结果类型
      Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器
      FunctionExpression<Function2<TKey, Enumerable<T>, TResult>> resultSelector, // 结果选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * 根据指定的键选择器函数对序列的元素进行分组，使用指定的函数投影每个组的元素，并从每个组及其键创建结果值
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 使用元素选择器函数对每个组的元素进行投影转换
   * 使用结果选择器函数从每个组的键和投影后的元素序列中创建结果值
   * 返回一个结果值的序列，每个结果值对应一个组
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param elementSelector 元素选择器函数表达式，用于投影每个组的元素
   * @param resultSelector 结果选择器函数表达式，接受组的键和投影后的元素序列，返回结果值
   * @param <TKey> 键的类型
   * @param <TElement> 投影后元素的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个组
   */
  <TKey, TElement, TResult> Queryable<TResult> groupBy(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器
      FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector); // 结果选择器

  /**
   * 根据指定的键选择器函数对序列的元素进行分组，使用指定的函数投影每个组的元素，从每个组及其键创建结果值，并使用指定的比较器比较键
   * 该方法根据键选择器函数计算每个元素的键，将具有相同键的元素分组到一起
   * 使用元素选择器函数对每个组的元素进行投影转换
   * 使用结果选择器函数从每个组的键和投影后的元素序列中创建结果值
   * 使用指定的 EqualityComparer 来比较键，允许自定义键的相等比较逻辑
   * 返回一个结果值的序列，每个结果值对应一个组
   *
   * @param source 源 Queryable 序列，包含要分组的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param elementSelector 元素选择器函数表达式，用于投影每个组的元素
   * @param resultSelector 结果选择器函数表达式，接受组的键和投影后的元素序列，返回结果值
   * @param comparer 用于比较键是否相等的 EqualityComparer
   * @param <TKey> 键的类型
   * @param <TElement> 投影后元素的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个组
   */
  <TKey, TElement, TResult> Queryable<TResult> groupBy(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器
      FunctionExpression<Function1<T, TElement>> elementSelector, // 元素选择器
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> resultSelector, // 结果选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * 基于键相等性关联两个序列的元素，并对结果进行分组，使用默认相等比较器比较键
   * 该方法类似于 SQL 的 LEFT OUTER JOIN，但将匹配的内部元素分组
   * 对于外部序列中的每个元素，该方法查找内部序列中所有匹配的元素，并将它们分组
   * 使用结果选择器函数从外部元素和匹配的内部元素序列中创建结果值
   * 即使外部元素在内部序列中没有匹配项，也会包含在结果中（匹配的内部元素序列为空）
   *
   * @param source 外部源 Queryable 序列
   * @param inner 内部源 Enumerable 序列
   * @param outerKeySelector 外部键选择器函数表达式，用于从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数表达式，用于从内部元素中提取键
   * @param resultSelector 结果选择器函数表达式，接受外部元素和匹配的内部元素序列，返回结果值
   * @param <TInner> 内部序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个外部元素及其匹配的内部元素序列
   */
  <TInner, TKey, TResult> Queryable<TResult> groupJoin(Queryable<T> source, // 外部源序列
      Enumerable<TInner> inner, // 内部源序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector); // 结果选择器

  /**
   * 基于键相等性关联两个序列的元素，并对结果进行分组，使用指定的 EqualityComparer 比较键
   * 该方法类似于 SQL 的 LEFT OUTER JOIN，但将匹配的内部元素分组
   * 对于外部序列中的每个元素，该方法查找内部序列中所有匹配的元素，并将它们分组
   * 使用结果选择器函数从外部元素和匹配的内部元素序列中创建结果值
   * 即使外部元素在内部序列中没有匹配项，也会包含在结果中（匹配的内部元素序列为空）
   * 使用指定的 EqualityComparer 来比较键，允许自定义键的相等比较逻辑
   *
   * @param source 外部源 Queryable 序列
   * @param inner 内部源 Enumerable 序列
   * @param outerKeySelector 外部键选择器函数表达式，用于从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数表达式，用于从内部元素中提取键
   * @param resultSelector 结果选择器函数表达式，接受外部元素和匹配的内部元素序列，返回结果值
   * @param comparer 用于比较键是否相等的 EqualityComparer
   * @param <TInner> 内部序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个外部元素及其匹配的内部元素序列
   */
  <TInner, TKey, TResult> Queryable<TResult> groupJoin(Queryable<T> source, // 外部源序列
      Enumerable<TInner> inner, // 内部源序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器
      FunctionExpression<Function2<T, Enumerable<TInner>, TResult>> resultSelector, // 结果选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * 生成两个序列的交集，使用默认相等比较器比较值，消除重复项
   * 该方法返回两个序列中都存在的元素
   * 使用默认相等比较器来判断元素是否相等，并自动去除结果中的重复项
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列
   * @param enumerable 第二个源 Enumerable 序列
   * @return 包含交集元素的新 Queryable 序列，无重复项
   */
  Queryable<T> intersect(Queryable<T> source, Enumerable<T> enumerable); // 第一个源序列和第二个源序列

  /**
   * 生成两个序列的交集，使用默认相等比较器比较值
   * 该方法返回两个序列中都存在的元素
   * 使用默认相等比较器来判断元素是否相等
   * all 参数控制是否消除结果中的重复项：如果为 true，则去除重复项；如果为 false，则保留所有重复项
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列
   * @param enumerable 第二个源 Enumerable 序列
   * @param all 如果为 true，则消除结果中的重复项；如果为 false，则保留重复项
   * @return 包含交集元素的新 Queryable 序列
   */
  Queryable<T> intersect(Queryable<T> source, Enumerable<T> enumerable, boolean all); // 源序列、第二个序列和是否消除重复标志

  /**
   * 生成两个序列的交集，使用指定的 EqualityComparer 比较值，消除重复项
   * 该方法返回两个序列中都存在的元素
   * 使用指定的 EqualityComparer 来判断元素是否相等，并自动去除结果中的重复项
   * 这允许自定义相等比较逻辑
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列
   * @param enumerable 第二个源 Enumerable 序列
   * @param comparer 用于比较元素是否相等的 EqualityComparer
   * @return 包含交集元素的新 Queryable 序列，无重复项
   */
  Queryable<T> intersect(Queryable<T> source, Enumerable<T> enumerable, // 源序列和第二个序列
      EqualityComparer<T> comparer); // 相等比较器

  /**
   * 生成两个序列的交集，使用指定的 EqualityComparer 比较值
   * 该方法返回两个序列中都存在的元素
   * 使用指定的 EqualityComparer 来判断元素是否相等
   * all 参数控制是否消除结果中的重复项：如果为 true，则去除重复项；如果为 false，则保留所有重复项
   * 这允许自定义相等比较逻辑
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致
   *
   * @param source 第一个源 Queryable 序列
   * @param enumerable 第二个源 Enumerable 序列
   * @param comparer 用于比较元素是否相等的 EqualityComparer
   * @param all 如果为 true，则消除结果中的重复项；如果为 false，则保留重复项
   * @return 包含交集元素的新 Queryable 序列
   */
  Queryable<T> intersect(Queryable<T> source, Enumerable<T> enumerable, // 源序列和第二个序列
      EqualityComparer<T> comparer, boolean all); // 相等比较器和是否消除重复标志

  /**
   * 基于匹配键关联两个序列的元素，使用默认相等比较器比较键
   * 该方法类似于 SQL 的 INNER JOIN，只返回两个序列中键相等的元素对
   * 对于外部序列中的每个元素，该方法查找内部序列中所有匹配的元素
   * 使用结果选择器函数从匹配的外部元素和内部元素对中创建结果值
   * 只有当外部元素在内部序列中有匹配项时，才会产生结果
   *
   * @param source 外部源 Queryable 序列
   * @param inner 内部源 Enumerable 序列
   * @param outerKeySelector 外部键选择器函数表达式，用于从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数表达式，用于从内部元素中提取键
   * @param resultSelector 结果选择器函数表达式，接受匹配的外部元素和内部元素，返回结果值
   * @param <TInner> 内部序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个匹配的元素对
   */
  <TInner, TKey, TResult> Queryable<TResult> join(Queryable<T> source, // 外部源序列
      Enumerable<TInner> inner, // 内部源序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector); // 结果选择器

  /**
   * 基于匹配键关联两个序列的元素，使用指定的 EqualityComparer 比较键
   * 该方法类似于 SQL 的 INNER JOIN，只返回两个序列中键相等的元素对
   * 对于外部序列中的每个元素，该方法查找内部序列中所有匹配的元素
   * 使用结果选择器函数从匹配的外部元素和内部元素对中创建结果值
   * 只有当外部元素在内部序列中有匹配项时，才会产生结果
   * 使用指定的 EqualityComparer 来比较键，允许自定义键的相等比较逻辑
   *
   * @param source 外部源 Queryable 序列
   * @param inner 内部源 Enumerable 序列
   * @param outerKeySelector 外部键选择器函数表达式，用于从外部元素中提取键
   * @param innerKeySelector 内部键选择器函数表达式，用于从内部元素中提取键
   * @param resultSelector 结果选择器函数表达式，接受匹配的外部元素和内部元素，返回结果值
   * @param comparer 用于比较键是否相等的 EqualityComparer
   * @param <TInner> 内部序列元素的类型
   * @param <TKey> 键的类型
   * @param <TResult> 结果的类型
   * @return 结果值的序列，每个结果值对应一个匹配的元素对
   */
  <TInner, TKey, TResult> Queryable<TResult> join(Queryable<T> source, // 外部源序列
      Enumerable<TInner> inner, // 内部源序列
      FunctionExpression<Function1<T, TKey>> outerKeySelector, // 外部键选择器
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 内部键选择器
      FunctionExpression<Function2<T, TInner, TResult>> resultSelector, // 结果选择器
      EqualityComparer<TKey> comparer); // 键比较器

  /**
   * 返回序列的最后一个元素
   * 该方法返回序列中的最后一个元素
   * 如果序列为空，会抛出异常
   *
   * @param source 源 Queryable 序列
   * @return 序列的最后一个元素
   */
  T last(Queryable<T> source); // 源序列

  /**
   * 返回序列中满足指定条件的最后一个元素
   * 该方法遍历序列，返回最后一个使谓词函数返回 true 的元素
   * 如果序列为空或没有元素满足条件，会抛出异常
   *
   * @param source 源 Queryable 序列
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 序列中最后一个满足条件的元素
   */
  T last(Queryable<T> source, FunctionExpression<Predicate1<T>> predicate); // 源序列和谓词函数

  /**
   * 返回序列的最后一个元素，如果序列不包含任何元素则返回默认值
   * 该方法返回序列中的最后一个元素
   * 如果序列为空，返回类型 T 的默认值（对于引用类型为 null）
   *
   * @param source 源 Queryable 序列
   * @return 序列的最后一个元素，如果序列为空则返回默认值
   */
  T lastOrDefault(Queryable<T> source); // 源序列

  /**
   * 返回序列中满足指定条件的最后一个元素，如果没有这样的元素则返回默认值
   * 该方法遍历序列，返回最后一个使谓词函数返回 true 的元素
   * 如果序列为空或没有元素满足条件，返回类型 T 的默认值（对于引用类型为 null）
   *
   * @param source 源 Queryable 序列
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 序列中最后一个满足条件的元素，如果没有则返回默认值
   */
  T lastOrDefault(Queryable<T> source, // 源序列
      FunctionExpression<Predicate1<T>> predicate); // 谓词函数

  /**
   * 返回表示序列中元素总数的 long 值
   * 该方法计算并返回序列中元素的总数，返回类型为 long
   * 如果序列为空，返回 0
   * 该方法适用于元素数量可能超过 int 范围的大序列
   *
   * @param source 源 Queryable 序列，包含要计数的元素
   * @return 序列中的元素数量，以 long 类型返回
   */
  long longCount(Queryable<T> source); // 源序列

  /**
   * 返回表示序列中满足指定条件的元素数量的 long 值
   * 该方法对序列中的每个元素应用谓词函数，计算使谓词返回 true 的元素数量，返回类型为 long
   * 如果没有元素满足条件或序列为空，返回 0
   * 该方法适用于元素数量可能超过 int 范围的大序列
   *
   * @param source 源 Queryable 序列，包含要计数的元素
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 满足条件的元素数量，以 long 类型返回
   */
  long longCount(Queryable<T> source, // 源序列
      FunctionExpression<Predicate1<T>> predicate); // 谓词函数

  /**
   * 返回泛型序列中的最大值
   * 该方法返回序列中的最大元素
   * 元素类型 T 必须实现 Comparable 接口，以便进行比较
   * 如果序列为空，会抛出异常
   *
   * @param source 源 Queryable 序列，包含要查找最大值的元素
   * @return 序列中的最大元素
   */
  T max(Queryable<T> source); // 源序列

  /**
   * 对泛型序列的每个元素调用投影函数，并返回最大结果值
   * 该方法对序列中的每个元素应用投影函数，获取转换后的值，然后返回最大的值
   * 投影结果类型 TResult 必须实现 Comparable 接口，以便进行比较
   * 如果序列为空，会抛出异常
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param selector 投影函数表达式，将每个元素转换为可比较的值
   * @param <TResult> 投影结果的类型，必须实现 Comparable 接口
   * @return 投影后的最大值
   */
  <TResult extends Comparable<TResult>> TResult max(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TResult>> selector); // 投影函数

  /**
   * 返回泛型序列中的最小值
   * 该方法返回序列中的最小元素
   * 元素类型 T 必须实现 Comparable 接口，以便进行比较
   * 如果序列为空，会抛出异常
   *
   * @param source 源 Queryable 序列，包含要查找最小值的元素
   * @return 序列中的最小元素
   */
  T min(Queryable<T> source); // 源序列

  /**
   * 对泛型序列的每个元素调用投影函数，并返回最小结果值
   * 该方法对序列中的每个元素应用投影函数，获取转换后的值，然后返回最小的值
   * 投影结果类型 TResult 必须实现 Comparable 接口，以便进行比较
   * 如果序列为空，会抛出异常
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param selector 投影函数表达式，将每个元素转换为可比较的值
   * @param <TResult> 投影结果的类型，必须实现 Comparable 接口
   * @return 投影后的最小值
   */
  <TResult extends Comparable<TResult>> TResult min(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TResult>> selector); // 投影函数

  /**
   * 根据指定类型过滤 IQueryable 的元素
   * 该方法返回序列中属于指定类型的元素
   * 只有能够成功转换为指定类型的元素才会被包含在结果中
   * 这是一种类型安全的过滤方法，常用于处理多态集合
   *
   * @param source 源 Queryable 序列，包含要过滤的元素
   * @param clazz 要筛选的目标类型
   * @param <TResult> 目标类型
   * @return 包含指定类型元素的 Queryable 序列
   */
  <TResult> Queryable<TResult> ofType(Queryable<T> source, // 源序列
      Class<TResult> clazz); // 目标类型

  /**
   * 将序列的元素转换为指定类型
   * 该方法尝试将序列中的每个元素转换为指定类型
   * 如果元素无法转换为目标类型，会抛出异常
   * 这是一种强制类型转换，要求所有元素都能成功转换
   *
   * @param source 源 Queryable 序列，包含要转换的元素
   * @param clazz 目标类型
   * @param <T2> 目标类型
   * @return 包含转换后元素的 Queryable 序列
   */
  <T2> Queryable<T2> cast(Queryable<T> source, Class<T2> clazz); // 源序列和目标类型

  /**
   * 根据键按升序对序列的元素进行排序
   * 该方法根据键选择器函数计算每个元素的键，然后按键的升序对元素进行排序
   * 键类型必须实现 Comparable 接口，以便进行比较
   * 返回一个 OrderedQueryable，支持后续的排序操作（如 thenBy）
   *
   * @param source 源 Queryable 序列，包含要排序的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param <TKey> 键的类型，必须实现 Comparable 接口
   * @return 按升序排序的 OrderedQueryable 序列
   */
  <TKey extends Comparable> OrderedQueryable<T> orderBy(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector); // 键选择器函数

  /**
   * 使用指定的比较器按升序对序列的元素进行排序
   * 该方法根据键选择器函数计算每个元素的键，然后使用指定的比较器按键的升序对元素进行排序
   * 这允许自定义键的比较逻辑，不要求键类型实现 Comparable 接口
   * 返回一个 OrderedQueryable，支持后续的排序操作（如 thenBy）
   *
   * @param source 源 Queryable 序列，包含要排序的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param comparator 用于比较键的 Comparator
   * @param <TKey> 键的类型
   * @return 按升序排序的 OrderedQueryable 序列
   */
  <TKey> OrderedQueryable<T> orderBy(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator); // 键比较器

  /**
   * 根据键按降序对序列的元素进行排序
   * 该方法根据键选择器函数计算每个元素的键，然后按键的降序对元素进行排序
   * 键类型必须实现 Comparable 接口，以便进行比较
   * 返回一个 OrderedQueryable，支持后续的排序操作（如 thenByDescending）
   *
   * @param source 源 Queryable 序列，包含要排序的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param <TKey> 键的类型，必须实现 Comparable 接口
   * @return 按降序排序的 OrderedQueryable 序列
   */
  <TKey extends Comparable> OrderedQueryable<T> orderByDescending( // 泛型方法，TKey 必须实现 Comparable
      Queryable<T> source, FunctionExpression<Function1<T, TKey>> keySelector); // 源序列和键选择器函数

  /**
   * 使用指定的比较器按降序对序列的元素进行排序
   * 该方法根据键选择器函数计算每个元素的键，然后使用指定的比较器按键的降序对元素进行排序
   * 这允许自定义键的比较逻辑，不要求键类型实现 Comparable 接口
   * 返回一个 OrderedQueryable，支持后续的排序操作（如 thenByDescending）
   *
   * @param source 源 Queryable 序列，包含要排序的元素
   * @param keySelector 键选择器函数表达式，用于从每个元素中提取键
   * @param comparator 用于比较键的 Comparator
   * @param <TKey> 键的类型
   * @return 按降序排序的 OrderedQueryable 序列
   */
  <TKey> OrderedQueryable<T> orderByDescending(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator); // 键比较器

  /**
   * 反转序列中元素的顺序
   * 该方法返回一个新序列，其中元素的顺序与原序列相反
   * 原序列的第一个元素成为新序列的最后一个元素，原序列的最后一个元素成为新序列的第一个元素
   * 原序列不会被修改
   *
   * @param source 源 Queryable 序列，包含要反转的元素
   * @return 元素顺序反转的新 Queryable 序列
   */
  Queryable<T> reverse(Queryable<T> source); // 源序列

  /**
   * 将序列的每个元素投影到新形式
   * 该方法对序列中的每个元素应用投影函数，将元素转换为新的形式
   * 投影函数接受一个元素，返回转换后的结果
   * 这是一种转换操作，可以用于修改元素的结构或提取元素的特定属性
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param selector 投影函数表达式，将每个元素转换为新的形式
   * @param <TResult> 投影结果的类型
   * @return 包含投影后元素的 Queryable 序列
   */
  <TResult> Queryable<TResult> select(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, TResult>> selector); // 投影函数

  /**
   * 通过结合元素的索引将序列的每个元素投影到新形式
   * 该方法对序列中的每个元素应用投影函数，将元素及其索引转换为新的形式
   * 投影函数接受一个元素和该元素的索引（从零开始），返回转换后的结果
   * 这使得可以在投影过程中使用元素的位置信息
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param selector 投影函数表达式，接受元素和索引，返回转换后的结果
   * @param <TResult> 投影结果的类型
   * @return 包含投影后元素的 Queryable 序列
   */
  <TResult> Queryable<TResult> selectN(Queryable<T> source, // 源序列
      FunctionExpression<Function2<T, Integer, TResult>> selector); // 带索引的投影函数


  /**
   * 将序列的每个元素投影到 Enumerable<TResult>，并将结果序列合并为一个序列
   * 该方法对序列中的每个元素应用集合选择器函数，将元素转换为一个序列
   * 然后将所有生成的序列合并为一个扁平化的序列
   * 这是一种一对多转换操作，常用于处理层次结构数据
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param selector 集合选择器函数表达式，将每个元素转换为一个序列
   * @param <TResult> 结果序列中元素的类型
   * @return 包含所有中间序列元素合并后的 Queryable 序列
   */
  <TResult> Queryable<TResult> selectMany(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, Enumerable<TResult>>> selector); // 集合选择器函数

  /**
   * 将序列的每个元素投影到 Enumerable<TResult>，并将结果序列合并为一个序列，使用源元素的索引
   * 该方法对序列中的每个元素应用集合选择器函数，将元素及其索引转换为一个序列
   * 然后将所有生成的序列合并为一个扁平化的序列
   * 这使得可以在投影过程中使用元素的位置信息
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param selector 集合选择器函数表达式，接受元素和索引，返回一个序列
   * @param <TResult> 结果序列中元素的类型
   * @return 包含所有中间序列元素合并后的 Queryable 序列
   */
  <TResult> Queryable<TResult> selectManyN(Queryable<T> source, // 源序列
      FunctionExpression<Function2<T, Integer, Enumerable<TResult>>> selector); // 带索引的集合选择器函数

  /**
   * 将序列的每个元素投影到 Enumerable<TCollection>，该序列包含生成它的源元素的索引
   * 对每个中间序列的每个元素调用结果选择器函数，并将结果值合并为一个单一的一维序列并返回
   * 该方法首先使用集合选择器函数将每个元素及其索引转换为一个中间序列
   * 然后对中间序列的每个元素应用结果选择器函数，将原始元素和集合元素转换为最终结果
   * 最后将所有结果合并为一个扁平化的序列
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param collectionSelector 集合选择器函数表达式，接受元素和索引，返回一个中间序列
   * @param resultSelector 结果选择器函数表达式，接受原始元素和集合元素，返回最终结果
   * @param <TCollection> 中间集合元素的类型
   * @param <TResult> 结果序列中元素的类型
   * @return 包含所有转换后元素合并后的 Queryable 序列
   */
  <TCollection, TResult> Queryable<TResult> selectMany(Queryable<T> source, // 源序列
      FunctionExpression<Function2<T, Integer, Enumerable<TCollection>>> // 集合选择器函数类型
        collectionSelector, // 集合选择器函数
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector); // 结果选择器函数

  /**
   * 将序列的每个元素投影到 Enumerable<TCollection>，并对其中每个元素调用结果选择器函数
   * 将每个中间序列的结果值合并为一个单一的一维序列并返回
   * 该方法首先使用集合选择器函数将每个元素转换为一个中间序列
   * 然后对中间序列的每个元素应用结果选择器函数，将原始元素和集合元素转换为最终结果
   * 最后将所有结果合并为一个扁平化的序列
   *
   * @param source 源 Queryable 序列，包含要投影的元素
   * @param collectionSelector 集合选择器函数表达式，将每个元素转换为一个中间序列
   * @param resultSelector 结果选择器函数表达式，接受原始元素和集合元素，返回最终结果
   * @param <TCollection> 中间集合元素的类型
   * @param <TResult> 结果序列中元素的类型
   * @return 包含所有转换后元素合并后的 Queryable 序列
   */
  <TCollection, TResult> Queryable<TResult> selectManyN(Queryable<T> source, // 源序列
      FunctionExpression<Function1<T, Enumerable<TCollection>>> // 集合选择器函数类型
        collectionSelector, // 集合选择器函数
      FunctionExpression<Function2<T, TCollection, TResult>> resultSelector); // 结果选择器函数

  /**
   * 通过使用默认相等比较器比较元素来确定两个序列是否相等
   * 该方法逐个比较两个序列的元素，如果两个序列的长度相同且所有对应位置的元素都相等，则返回 true
   * 使用元素类型的默认相等比较器（通常是 equals 方法）来比较元素
   *
   * @param source 第一个源 Queryable 序列
   * @param enumerable 第二个源 Enumerable 序列
   * @return 如果两个序列相等，返回 true；否则返回 false
   */
  boolean sequenceEqual(Queryable<T> source, Enumerable<T> enumerable); // 第一个源序列和第二个源序列

  /**
   * 通过使用指定的 EqualityComparer 比较元素来确定两个序列是否相等
   * 该方法逐个比较两个序列的元素，如果两个序列的长度相同且所有对应位置的元素都相等，则返回 true
   * 使用指定的 EqualityComparer 来比较元素，允许自定义相等比较逻辑
   *
   * @param source 第一个源 Queryable 序列
   * @param enumerable 第二个源 Enumerable 序列
   * @param comparer 用于比较元素是否相等的 EqualityComparer
   * @return 如果两个序列相等，返回 true；否则返回 false
   */
  boolean sequenceEqual(Queryable<T> source, Enumerable<T> enumerable, // 第一个源序列和第二个源序列
      EqualityComparer<T> comparer); // 相等比较器

  /**
   * 返回序列的唯一元素，如果序列中不只有一个元素则抛出异常
   * 该方法返回序列中的唯一元素
   * 如果序列为空或包含多个元素，会抛出异常
   * 该方法用于确保序列中恰好有一个元素
   *
   * @param source 源 Queryable 序列
   * @return 序列的唯一元素
   */
  T single(Queryable<T> source); // 源序列

  /**
   * 返回序列中满足指定条件的唯一元素，如果存在多个这样的元素则抛出异常
   * 该方法遍历序列，返回唯一一个使谓词函数返回 true 的元素
   * 如果序列为空、没有元素满足条件或多个元素满足条件，会抛出异常
   * 该方法用于确保序列中恰好有一个元素满足条件
   *
   * @param source 源 Queryable 序列
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 序列中唯一满足条件的元素
   */
  T single(Queryable<T> source, FunctionExpression<Predicate1<T>> predicate); // 源序列和谓词函数

  /**
   * 返回序列的唯一元素，如果序列为空则返回默认值；如果序列中有多个元素则抛出异常
   * 该方法返回序列中的唯一元素
   * 如果序列为空，返回类型 T 的默认值（对于引用类型为 null）
   * 如果序列包含多个元素，会抛出异常
   *
   * @param source 源 Queryable 序列
   * @return 序列的唯一元素，如果序列为空则返回默认值
   */
  T singleOrDefault(Queryable<T> source); // 源序列

  /**
   * 返回序列中满足指定条件的唯一元素，如果没有这样的元素则返回默认值；如果多个元素满足条件则抛出异常
   * 该方法遍历序列，返回唯一一个使谓词函数返回 true 的元素
   * 如果序列为空或没有元素满足条件，返回类型 T 的默认值（对于引用类型为 null）
   * 如果多个元素满足条件，会抛出异常
   *
   * @param source 源 Queryable 序列
   * @param predicate 谓词函数表达式，用于测试每个元素是否满足条件
   * @return 序列中唯一满足条件的元素，如果没有则返回默认值
   */
  T singleOrDefault(Queryable<T> source, // 源序列
      FunctionExpression<Predicate1<T>> predicate); // 谓词函数

  /**
   * 跳过序列中指定数量的元素，然后返回剩余元素
   * 该方法从序列开头跳过指定数量的元素，返回包含剩余元素的新序列
   * 如果跳过的数量大于或等于序列长度，返回空序列
   */
  Queryable<T> skip(Queryable<T> source, int count); // 源序列和要跳过的元素数量

  /**
   * 只要满足指定条件就跳过序列中的元素，然后返回剩余元素
   * 该方法从序列开头开始，跳过所有使谓词函数返回 true 的元素
   * 当遇到第一个不满足条件的元素时，停止跳过并返回该元素及其后的所有元素
   */
  Queryable<T> skipWhile(Queryable<T> source, // 源序列
      FunctionExpression<Predicate1<T>> predicate); // 谓词函数

  /**
   * 只要满足指定条件就跳过序列中的元素，然后返回剩余元素，在谓词函数的逻辑中使用元素的索引
   * 该方法从序列开头开始，跳过所有使谓词函数返回 true 的元素
   * 谓词函数接受元素及其索引，可以根据索引信息决定是否跳过元素
   * 当遇到第一个不满足条件的元素时，停止跳过并返回该元素及其后的所有元素
   */
  Queryable<T> skipWhileN(Queryable<T> source, // 源序列
      FunctionExpression<Predicate2<T, Integer>> predicate); // 带索引的谓词函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的 BigDecimal 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取 BigDecimal 值序列，然后计算这些值的总和
   * 如果序列为空，返回 0
   */
  BigDecimal sumBigDecimal(Queryable<T> source, // 源序列
      FunctionExpression<BigDecimalFunction1<T>> selector); // BigDecimal 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的可空 BigDecimal 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 BigDecimal 值序列，然后计算这些值的总和
   * 总和是所有非 null 值的总和，null 值被忽略
   * 如果序列为空或所有值都为 null，返回 0
   */
  BigDecimal sumNullableBigDecimal(Queryable<T> source, // 源序列
      FunctionExpression<NullableBigDecimalFunction1<T>> selector); // 可空 BigDecimal 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的 Double 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取 Double 值序列，然后计算这些值的总和
   * 如果序列为空，返回 0.0
   */
  double sumDouble(Queryable<T> source, // 源序列
      FunctionExpression<DoubleFunction1<T>> selector); // Double 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的可空 Double 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 Double 值序列，然后计算这些值的总和
   * 总和是所有非 null 值的总和，null 值被忽略
   * 如果序列为空或所有值都为 null，返回 0.0
   */
  Double sumNullableDouble(Queryable<T> source, // 源序列
      FunctionExpression<NullableDoubleFunction1<T>> selector); // 可空 Double 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的 int 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取 int 值序列，然后计算这些值的总和
   * 如果序列为空，返回 0
   */
  int sumInteger(Queryable<T> source, // 源序列
      FunctionExpression<IntegerFunction1<T>> selector); // int 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的可空 int 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 int 值序列，然后计算这些值的总和
   * 总和是所有非 null 值的总和，null 值被忽略
   * 如果序列为空或所有值都为 null，返回 0
   */
  Integer sumNullableInteger(Queryable<T> source, // 源序列
      FunctionExpression<NullableIntegerFunction1<T>> selector); // 可空 int 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的 long 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取 long 值序列，然后计算这些值的总和
   * 如果序列为空，返回 0
   */
  long sumLong(Queryable<T> source, // 源序列
      FunctionExpression<LongFunction1<T>> selector); // long 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的可空 long 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 long 值序列，然后计算这些值的总和
   * 总和是所有非 null 值的总和，null 值被忽略
   * 如果序列为空或所有值都为 null，返回 0
   */
  Long sumNullableLong(Queryable<T> source, // 源序列
      FunctionExpression<NullableLongFunction1<T>> selector); // 可空 long 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的 Float 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取 Float 值序列，然后计算这些值的总和
   * 如果序列为空，返回 0.0f
   */
  float sumFloat(Queryable<T> source, // 源序列
      FunctionExpression<FloatFunction1<T>> selector); // Float 投影函数

  /**
   * 计算通过对输入序列的每个元素调用投影函数获得的可空 Float 值序列的总和
   * 该方法通过对输入序列的每个元素调用投影函数来获取可空 Float 值序列，然后计算这些值的总和
   * 总和是所有非 null 值的总和，null 值被忽略
   * 如果序列为空或所有值都为 null，返回 0.0f
   */
  Float sumNullableFloat(Queryable<T> source, // 源序列
      FunctionExpression<NullableFloatFunction1<T>> selector); // 可空 Float 投影函数

  /**
   * 从序列开头返回指定数量的连续元素
   * 该方法从序列开头返回指定数量的元素
   * 如果序列中的元素数量少于指定的数量，返回序列中的所有元素
   */
  Queryable<T> take(Queryable<T> source, int count); // 源序列和要获取的元素数量

  /**
   * 只要满足指定条件就返回序列中的元素
   * 该方法从序列开头开始，返回所有使谓词函数返回 true 的元素
   * 当遇到第一个不满足条件的元素时，停止返回元素
   */
  Queryable<T> takeWhile(Queryable<T> source, // 源序列
      FunctionExpression<Predicate1<T>> predicate); // 谓词函数

  /**
   * 只要满足指定条件就返回序列中的元素，在谓词函数的逻辑中使用元素的索引
   * 该方法从序列开头开始，返回所有使谓词函数返回 true 的元素
   * 谓词函数接受元素及其索引，可以根据索引信息决定是否返回元素
   * 当遇到第一个不满足条件的元素时，停止返回元素
   */
  Queryable<T> takeWhileN(Queryable<T> source, // 源序列
      FunctionExpression<Predicate2<T, Integer>> predicate); // 带索引的谓词函数

  // 对已排序的序列进行次要升序排序，键类型必须实现 Comparable 接口
  <TKey extends Comparable<TKey>> OrderedQueryable<T> thenBy( // 泛型方法，TKey 必须实现 Comparable 接口
      OrderedQueryable<T> source, // 已排序的源序列
      FunctionExpression<Function1<T, TKey>> keySelector); // 键选择器函数

  // 对已排序的序列进行次要升序排序，使用指定的比较器
  <TKey> OrderedQueryable<T> thenBy(OrderedQueryable<T> source, // 已排序的源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator); // 键比较器

  // 对已排序的序列进行次要降序排序，键类型必须实现 Comparable 接口
  <TKey extends Comparable<TKey>> OrderedQueryable<T> thenByDescending( // 泛型方法，TKey 必须实现 Comparable 接口
      OrderedQueryable<T> source, // 已排序的源序列
      FunctionExpression<Function1<T, TKey>> keySelector); // 键选择器函数

  // 对已排序的序列进行次要降序排序，使用指定的比较器
  <TKey> OrderedQueryable<T> thenByDescending(OrderedQueryable<T> source, // 已排序的源序列
      FunctionExpression<Function1<T, TKey>> keySelector, // 键选择器函数
      Comparator<TKey> comparator); // 键比较器

  /**
   * 使用默认相等比较器生成两个序列的并集
   * 该方法返回两个序列中所有不重复的元素
   * 使用默认相等比较器来判断元素是否相等，并自动去除结果中的重复项
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致，然后是第二个序列中出现的顺序
   */
  Queryable<T> union(Queryable<T> source, Enumerable<T> source1); // 第一个源序列和第二个源序列

  /**
   * 使用指定的 EqualityComparer 生成两个序列的并集
   * 该方法返回两个序列中所有不重复的元素
   * 使用指定的 EqualityComparer 来判断元素是否相等，并自动去除结果中的重复项
   * 这允许自定义相等比较逻辑
   * 结果序列中元素的顺序与它们在第一个序列中出现的顺序一致，然后是第二个序列中出现的顺序
   */
  Queryable<T> union(Queryable<T> source, Enumerable<T> source1, // 第一个源序列和第二个源序列
      EqualityComparer<T> comparer); // 相等比较器

  /**
   * 基于谓词过滤值序列
   * 该方法对序列中的每个元素应用谓词函数，只返回使谓词返回 true 的元素
   * 这是一种过滤操作，常用于根据条件筛选数据
   */
  Queryable<T> where(Queryable<T> source, // 源序列
      FunctionExpression<? extends Predicate1<T>> predicate); // 谓词函数

  /**
   * 基于谓词过滤值序列，在谓词函数的逻辑中使用每个元素的索引
   * 该方法对序列中的每个元素应用谓词函数，只返回使谓词返回 true 的元素
   * 谓词函数接受元素及其索引，可以根据索引信息决定是否保留元素
   */
  Queryable<T> whereN(Queryable<T> source, // 源序列
      FunctionExpression<? extends Predicate2<T, Integer>> predicate); // 带索引的谓词函数

  /**
   * 使用指定的结果选择器函数合并两个序列
   * 该方法将两个序列中的元素按位置配对，对每对元素应用结果选择器函数
   * 结果序列的长度等于两个输入序列中较短的那个的长度
   * 这是一种合并操作，常用于将两个相关联的序列组合在一起
   */
  <T1, TResult> Queryable<TResult> zip(Queryable<T> source, // 第一个源序列
      Enumerable<T1> source1, // 第二个源序列
      FunctionExpression<Function2<T, T1, TResult>> resultSelector); // 结果选择器函数
}
