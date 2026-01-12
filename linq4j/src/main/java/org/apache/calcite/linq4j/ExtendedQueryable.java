/* // Apache许可证头，声明该代码遵循Apache 2.0许可证，允许用户在遵守许可证条款的前提下使用、修改和分发此代码
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to you under the Apache License, Version 2.0
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
package org.apache.calcite.linq4j; // 声明该类属于org.apache.calcite.linq4j包，这是Calcite LINQ4J框架的核心包之一

import org.apache.calcite.linq4j.function.BigDecimalFunction1; // 导入BigDecimal类型的一元函数接口，用于处理BigDecimal值的转换
import org.apache.calcite.linq4j.function.DoubleFunction1; // 导入double类型的一元函数接口，用于处理double值的转换
import org.apache.calcite.linq4j.function.EqualityComparer; // 导入相等比较器接口，用于自定义对象相等的比较逻辑
import org.apache.calcite.linq4j.function.FloatFunction1; // 导入float类型的一元函数接口，用于处理float值的转换
import org.apache.calcite.linq4j.function.Function1; // 导入通用一元函数接口，接受一个参数并返回一个结果
import org.apache.calcite.linq4j.function.Function2; // 导入通用二元函数接口，接受两个参数并返回一个结果
import org.apache.calcite.linq4j.function.IntegerFunction1; // 导入int类型的一元函数接口，用于处理int值的转换
import org.apache.calcite.linq4j.function.LongFunction1; // 导入long类型的一元函数接口，用于处理long值的转换
import org.apache.calcite.linq4j.function.NullableBigDecimalFunction1; // 导入可空BigDecimal类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableDoubleFunction1; // 导入可空double类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableFloatFunction1; // 导入可空float类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableIntegerFunction1; // 导入可空int类型的一元函数接口
import org.apache.calcite.linq4j.function.NullableLongFunction1; // 导入可空long类型的一元函数接口
import org.apache.calcite.linq4j.function.Predicate1; // 导入一元谓词接口，用于测试单个元素是否满足条件
import org.apache.calcite.linq4j.function.Predicate2; // 导入二元谓词接口，用于测试两个元素是否满足条件
import org.apache.calcite.linq4j.tree.FunctionExpression; // 导入函数表达式类，用于表示可序列化的函数表达式树

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的值
import org.checkerframework.framework.qual.Covariant; // 导入协变类型注解，用于标记泛型类型参数的协变性

import java.math.BigDecimal; // 导入BigDecimal类，用于高精度十进制计算
import java.util.Comparator; // 导入比较器接口，用于定义自定义比较逻辑

/**
 * Extension methods in Queryable. // Queryable接口的扩展方法集合，定义了所有可查询对象支持的LINQ风格查询操作
 *
 * @param <TSource> Element type // 泛型参数TSource表示序列中元素的类型，这个接口为特定类型的元素提供查询能力
 */
@Covariant(0) // 使用协变注解标记第0个类型参数（即TSource），允许在返回类型中使用子类型
interface ExtendedQueryable<TSource> extends ExtendedEnumerable<TSource> { // 定义扩展可查询接口，继承自ExtendedEnumerable接口，为可查询序列提供额外的查询方法

  /**
   * Applies an accumulator function over a sequence. // 对序列应用累加器函数，将序列中的所有元素累积为一个值
   */ // 这是聚合操作的基础方法，使用指定的累加函数将序列缩减为单个值
  @Nullable TSource aggregate( // 定义聚合方法，返回类型为TSource（可能为null），接受一个函数表达式作为累加器
      FunctionExpression<Function2<@Nullable TSource, TSource, TSource>> selector); // 参数selector是一个函数表达式，接受当前累积值和序列元素，返回新的累积值

  /**
   * Applies an accumulator function over a // 对序列应用累加器函数，使用指定的种子值作为初始累加值
   * sequence. The specified seed value is used as the initial
   * accumulator value.
   */ // 这个重载版本允许指定初始种子值，使聚合操作更加灵活
  <TAccumulate> TAccumulate aggregate(TAccumulate seed, // 定义带种子值的聚合方法，TAccumulate是累加值的类型，seed是初始累加值
      FunctionExpression<Function2<TAccumulate, TSource, TAccumulate>> // 参数selector是一个函数表达式，接受当前累加值和序列元素，返回新的累加值
        selector);

  /**
   * Applies an accumulator function over a // 对序列应用累加器函数，使用指定的种子值作为初始累加值，并使用指定的函数选择结果值
   * sequence. The specified seed value is used as the initial
   * accumulator value, and the specified function is used to select
   * the result value.
   */ // 这是最完整的聚合方法，允许指定种子值、累加函数和结果选择器
  <TAccumulate, TResult> TResult aggregate(TAccumulate seed, // 定义完整的聚合方法，TAccumulate是累加值类型，TResult是最终结果类型，seed是初始累加值
      FunctionExpression<Function2<TAccumulate, TSource, TAccumulate>> func, // 参数func是累加函数表达式，接受当前累加值和序列元素，返回新的累加值
      FunctionExpression<Function1<TAccumulate, TResult>> selector); // 参数selector是结果选择器函数表达式，接受最终的累加值并转换为结果类型

  /**
   * Determines whether all the elements of a sequence // 确定序列中的所有元素是否都满足指定的条件
   * satisfy a condition.
   */ // 这是一个全量检查操作，当且仅当所有元素都满足谓词条件时返回true
  boolean all(FunctionExpression<Predicate1<TSource>> predicate); // 定义all方法，返回boolean类型，参数predicate是用于测试每个元素的谓词表达式

  /**
   * Determines whether any element of a sequence // 确定序列中是否存在任何元素满足指定的条件
   * satisfies a condition.
   */ // 这是一个存在性检查操作，只要有一个元素满足谓词条件就返回true
  boolean any(FunctionExpression<Predicate1<TSource>> predicate); // 定义any方法，返回boolean类型，参数predicate是用于测试每个元素的谓词表达式

  /**
   * Computes the average of a sequence of Decimal // 计算序列中Decimal值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   */ // 这是一个数值聚合操作，计算BigDecimal类型元素的平均值
  BigDecimal averageBigDecimal( // 定义BigDecimal平均值计算方法，返回BigDecimal类型的结果
      FunctionExpression<BigDecimalFunction1<TSource>> selector); // 参数selector是投影函数表达式，从每个元素中提取BigDecimal值

  /**
   * Computes the average of a sequence of nullable // 计算序列中可空Decimal值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * Decimal values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */ // 这个版本处理可能为null的BigDecimal值，计算时会忽略null值
  BigDecimal averageNullableBigDecimal( // 定义可空BigDecimal平均值计算方法，返回BigDecimal类型的结果
      FunctionExpression<NullableBigDecimalFunction1<TSource>> selector); // 参数selector是可空BigDecimal投影函数表达式，从每个元素中提取可能为null的BigDecimal值

  /**
   * Computes the average of a sequence of Double // 计算序列中Double值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   */ // 这是一个数值聚合操作，计算double类型元素的平均值
  double averageDouble(FunctionExpression<DoubleFunction1<TSource>> selector); // 定义double平均值计算方法，返回double类型的结果
  // 参数selector是投影函数表达式，从每个元素中提取double值

  /**
   * Computes the average of a sequence of nullable // 计算序列中可空Double值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * Double values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */ // 这个版本处理可能为null的Double值，计算时会忽略null值
  Double averageNullableDouble( // 定义可空Double平均值计算方法，返回Double包装类型的结果（可能为null）
      FunctionExpression<NullableDoubleFunction1<TSource>> selector); // 参数selector是可空double投影函数表达式，从每个元素中提取可能为null的double值

  /**
   * Computes the average of a sequence of int values // 计算序列中int值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */ // 这是一个数值聚合操作，计算int类型元素的平均值
  int averageInteger(FunctionExpression<IntegerFunction1<TSource>> selector); // 定义int平均值计算方法，返回int类型的结果
  // 参数selector是投影函数表达式，从每个元素中提取int值

  /**
   * Computes the average of a sequence of nullable // 计算序列中可空int值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * int values that is obtained by invoking a projection function
   * on each element of the input sequence.
   */ // 这个版本处理可能为null的int值，计算时会忽略null值
  Integer averageNullableInteger( // 定义可空int平均值计算方法，返回Integer包装类型的结果（可能为null）
      FunctionExpression<NullableIntegerFunction1<TSource>> selector); // 参数selector是可空int投影函数表达式，从每个元素中提取可能为null的int值

  /**
   * Computes the average of a sequence of Float // 计算序列中Float值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   */ // 这是一个数值聚合操作，计算float类型元素的平均值
  float averageFloat(FunctionExpression<FloatFunction1<TSource>> selector); // 定义float平均值计算方法，返回float类型的结果
  // 参数selector是投影函数表达式，从每个元素中提取float值

  /**
   * Computes the average of a sequence of nullable // 计算序列中可空Float值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * Float values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */ // 这个版本处理可能为null的Float值，计算时会忽略null值
  Float averageNullableFloat( // 定义可空float平均值计算方法，返回Float包装类型的结果（可能为null）
      FunctionExpression<NullableFloatFunction1<TSource>> selector); // 参数selector是可空float投影函数表达式，从每个元素中提取可能为null的float值

  /**
   * Computes the average of a sequence of long values // 计算序列中long值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */ // 这是一个数值聚合操作，计算long类型元素的平均值
  long averageLong(FunctionExpression<LongFunction1<TSource>> selector); // 定义long平均值计算方法，返回long类型的结果
  // 参数selector是投影函数表达式，从每个元素中提取long值

  /**
   * Computes the average of a sequence of nullable // 计算序列中可空long值的平均值，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * long values that is obtained by invoking a projection function
   * on each element of the input sequence.
   */ // 这个版本处理可能为null的long值，计算时会忽略null值
  Long averageNullableLong( // 定义可空long平均值计算方法，返回Long包装类型的结果（可能为null）
      FunctionExpression<NullableLongFunction1<TSource>> selector); // 参数selector是可空long投影函数表达式，从每个元素中提取可能为null的long值

  /**
   * Concatenates two sequences. // 连接两个序列，将两个序列的所有元素按顺序合并为一个新序列
   */ // 这是一个序列组合操作，将当前序列与另一个序列连接起来
  @Override Queryable<TSource> concat(Enumerable<TSource> source2); // 重写父类的concat方法，返回连接后的Queryable序列，参数source2是要连接的第二个序列

  /**
   * Returns the number of elements in the specified // 返回指定序列中满足条件的元素数量
   * sequence that satisfies a condition.
   */ // 这是一个计数操作，统计满足谓词条件的元素个数
  int count(FunctionExpression<Predicate1<TSource>> predicate); // 定义count方法，返回int类型的计数结果，参数predicate是用于筛选元素的谓词表达式

  /**
   * Returns the elements of the specified sequence or // 返回指定序列的元素，如果序列为空则返回包含类型参数默认值的单例集合
   * the type parameter's default value in a singleton collection if
   * the sequence is empty.
   */ // 这是一个安全访问操作，避免空序列导致的异常
  @Override Queryable<@Nullable TSource> defaultIfEmpty(); // 重写父类的defaultIfEmpty方法，返回可能包含默认值的Queryable序列

  /**
   * Returns distinct elements from a sequence by using // 使用默认的相等比较器返回序列中的不重复元素
   * the default equality comparer to compare values.
   */ // 这是一个去重操作，移除序列中的重复元素
  @Override Queryable<TSource> distinct(); // 重写父类的distinct方法，返回去重后的Queryable序列

  /**
   * Returns distinct elements from a sequence by using // 使用指定的相等比较器返回序列中的不重复元素
   * a specified EqualityComparer&lt;TSource&gt; to compare values.
   */ // 这个版本允许自定义相等比较逻辑来识别重复元素
  @Override Queryable<TSource> distinct(EqualityComparer<TSource> comparer); // 重写父类的distinct方法，使用自定义比较器进行去重，参数comparer是自定义的相等比较器

  /**
   * Produces the set difference of two sequences by // 使用默认的相等比较器生成两个序列的差集，移除重复项（由Enumerable定义）
   * using the default equality comparer to compare values,
   * eliminate duplicates. (Defined by Enumerable.)
   */ // 这是一个集合差集操作，返回在当前序列中但不在第二个序列中的元素
  @Override Queryable<TSource> except(Enumerable<TSource> enumerable); // 重写父类的except方法，返回差集Queryable序列，参数enumerable是要排除的序列

  /**
   * Produces the set difference of two sequences by // 使用默认的相等比较器生成两个序列的差集，使用all参数指示是否移除重复项（由Enumerable定义）
   * using the default equality comparer to compare values,
   * using {@code all} to indicate whether to eliminate duplicates.
   * (Defined by Enumerable.)
   */ // 这个版本允许控制是否保留结果中的重复元素
  @Override Queryable<TSource> except(Enumerable<TSource> enumerable, boolean all); // 重写父类的except方法，参数all为true时保留重复元素，为false时移除重复元素

  /**
   * Produces the set difference of two sequences by // 使用指定的EqualityComparer<TSource>生成两个序列的差集，移除重复项
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, eliminate duplicates.
   */ // 这个版本使用自定义比较器来识别相等元素
  @Override Queryable<TSource> except(Enumerable<TSource> enumerable, // 重写父类的except方法，使用自定义比较器计算差集
      EqualityComparer<TSource> comparer); // 参数comparer是自定义的相等比较器

  /**
   * Produces the set difference of two sequences by // 使用指定的EqualityComparer<TSource>生成两个序列的差集，使用all参数指示是否移除重复项
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, using {@code all} to indicate whether to eliminate duplicates.
   */ // 这是最完整的差集操作，同时支持自定义比较器和重复元素控制
  @Override Queryable<TSource> except(Enumerable<TSource> enumerable, // 重写父类的except方法，提供最灵活的差集计算
      EqualityComparer<TSource> comparer, boolean all); // 参数comparer是自定义比较器，参数all控制是否保留重复元素
  /**
   * Returns the first element of a sequence that // 返回序列中满足指定条件的第一个元素
   * satisfies a specified condition.
   */ // 这是一个元素访问操作，返回第一个匹配条件的元素，如果没有找到则抛出异常
  TSource first(FunctionExpression<Predicate1<TSource>> predicate); // 定义first方法，返回TSource类型的元素，参数predicate是用于筛选元素的谓词表达式

  /**
   * Returns the first element of a sequence that // 返回序列中满足指定条件的第一个元素，如果没有找到这样的元素则返回默认值
   * satisfies a specified condition or a default value if no such
   * element is found.
   */ // 这是一个安全的元素访问操作，避免没有找到元素时的异常
  @Nullable TSource firstOrDefault(FunctionExpression<Predicate1<TSource>> predicate); // 定义firstOrDefault方法，返回可能为null的TSource类型元素，参数predicate是筛选谓词表达式

  /**
   * Groups the elements of a sequence according to a // 根据指定的键选择器函数对序列的元素进行分组
   * specified key selector function.
   */ // 这是一个分组操作，将序列中的元素按照键值进行分组，返回分组集合
  <TKey> Queryable<Grouping<TKey, TSource>> groupBy( // 定义groupBy方法，TKey是键的类型，返回包含分组的Queryable序列
      FunctionExpression<Function1<TSource, TKey>> keySelector); // 参数keySelector是键选择器函数表达式，从每个元素中提取分组键

  /**
   * Groups the elements of a sequence according to a // 根据指定的键选择器函数对序列的元素进行分组，并使用指定的比较器比较键
   * specified key selector function and compares the keys by using
   * a specified comparer.
   */ // 这个版本允许自定义键的比较逻辑
  <TKey> Queryable<Grouping<TKey, TSource>> groupBy( // 定义带比较器的groupBy方法，TKey是键的类型
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      EqualityComparer<TKey> comparer); // 参数comparer是用于比较键的自定义相等比较器

  /**
   * Groups the elements of a sequence according to a // 根据指定的键选择器函数对序列的元素进行分组，并使用指定的函数投影每个组的元素
   * specified key selector function and projects the elements for
   * each group by using a specified function.
   */ // 这个版本允许对分组后的元素进行转换
  <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 定义带元素投影的groupBy方法，TKey是键类型，TElement是元素类型
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      FunctionExpression<Function1<TSource, TElement>> elementSelector); // 参数elementSelector是元素选择器函数表达式，用于转换分组中的元素

  /**
   * Groups the elements of a sequence and projects the // 对序列的元素进行分组，并使用指定的函数投影每个组的元素，键值使用指定的比较器进行比较
   * elements for each group by using a specified function. Key
   * values are compared by using a specified comparer.
   */ // 这个版本同时支持自定义键比较和元素转换
  <TKey, TElement> Queryable<Grouping<TKey, TElement>> groupBy( // 定义带比较器和元素投影的groupBy方法
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      FunctionExpression<Function1<TSource, TElement>> elementSelector, // 参数elementSelector是元素选择器函数表达式
      EqualityComparer<TKey> comparer); // 参数comparer是用于比较键的自定义相等比较器

  /**
   * Groups the elements of a sequence according to a // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值
   * specified key selector function and creates a result value from
   * each group and its key.
   *
   * <p>NOTE: Renamed from {@code groupBy} to distinguish from // 注意：从groupBy重命名为groupByK，以区分与另一个具有相同擦除的方法
   * {@link #groupBy(org.apache.calcite.linq4j.tree.FunctionExpression, org.apache.calcite.linq4j.tree.FunctionExpression)},
   * which has the same erasure.
   */ // 这个方法直接从分组生成结果，不返回分组对象
  <TKey, TResult> Queryable<TResult> groupByK( // 定义groupByK方法（避免类型擦除冲突），TKey是键类型，TResult是结果类型
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      FunctionExpression<Function2<TKey, Enumerable<TSource>, TResult>> resultSelector); // 参数resultSelector是结果选择器函数表达式，接受键和元素集合，生成结果

  /**
   * Groups the elements of a sequence according to a // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值，键使用指定的比较器进行比较
   * specified key selector function and creates a result value from
   * each group and its key. Keys are compared by using a specified
   * comparer.
   */ // 这个版本同时支持自定义键比较和结果生成
  <TKey, TResult> Queryable<TResult> groupByK( // 定义带比较器的groupByK方法
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      FunctionExpression<Function2<TKey, Enumerable<TSource>, TResult>> resultSelector, // 参数resultSelector是结果选择器函数表达式
      EqualityComparer<TKey> comparer); // 参数comparer是用于比较键的自定义相等比较器

  /**
   * Groups the elements of a sequence according to a // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值，每个组的元素使用指定的函数进行投影
   * specified key selector function and creates a result value from
   * each group and its key. The elements of each group are
   * projected by using a specified function.
   */ // 这是最完整的分组操作，支持元素投影和结果生成
  <TKey, TElement, TResult> Queryable<TResult> groupBy( // 定义完整的groupBy方法，支持元素投影和结果选择
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      FunctionExpression<Function1<TSource, TElement>> elementSelector, // 参数elementSelector是元素选择器函数表达式
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> // 参数resultSelector是结果选择器函数表达式，接受键和投影后的元素集合
        resultSelector);

  /**
   * Groups the elements of a sequence according to a // 根据指定的键选择器函数对序列的元素进行分组，并从每个组及其键创建结果值，键使用指定的比较器进行比较，每个组的元素使用指定的函数进行投影
   * specified key selector function and creates a result value from
   * each group and its key. Keys are compared by using a specified
   * comparer and the elements of each group are projected by using
   * a specified function.
   */ // 这是所有分组操作中最灵活的版本，支持所有自定义选项
  <TKey, TElement, TResult> Queryable<TResult> groupBy( // 定义最完整的groupBy方法，支持所有自定义选项
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      FunctionExpression<Function1<TSource, TElement>> elementSelector, // 参数elementSelector是元素选择器函数表达式
      FunctionExpression<Function2<TKey, Enumerable<TElement>, TResult>> // 参数resultSelector是结果选择器函数表达式
        resultSelector,
      EqualityComparer<TKey> comparer); // 参数comparer是用于比较键的自定义相等比较器

  /**
   * Correlates the elements of two sequences based on // 基于键相等性关联两个序列的元素并对结果分组，使用默认的相等比较器比较键
   * key equality and groups the results. The default equality
   * comparer is used to compare keys.
   */ // 这是一个分组连接操作，类似于SQL中的LEFT JOIN，将外部序列的每个元素与内部序列中匹配的所有元素关联
  <TInner, TKey, TResult> Queryable<TResult> groupJoin(Enumerable<TInner> inner, // 定义groupJoin方法，TInner是内部序列元素类型，TKey是键类型，TResult是结果类型
      FunctionExpression<Function1<TSource, TKey>> outerKeySelector, // 参数outerKeySelector是外部键选择器函数表达式，从外部序列元素提取键
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 参数innerKeySelector是内部键选择器函数表达式，从内部序列元素提取键
      FunctionExpression<Function2<TSource, Enumerable<TInner>, TResult>> // 参数resultSelector是结果选择器函数表达式，接受外部元素和匹配的内部元素集合，生成结果
        resultSelector);

  /**
   * Correlates the elements of two sequences based on // 基于键相等性关联两个序列的元素并对结果分组，使用指定的EqualityComparer<TSource>比较键
   * key equality and groups the results. A specified
   * {@code EqualityComparer<TSource>} is used to compare keys.
   */ // 这个版本允许自定义键的比较逻辑
  <TInner, TKey, TResult> Queryable<TResult> groupJoin(Enumerable<TInner> inner, // 定义带比较器的groupJoin方法
      FunctionExpression<Function1<TSource, TKey>> outerKeySelector, // 参数outerKeySelector是外部键选择器函数表达式
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 参数innerKeySelector是内部键选择器函数表达式
      FunctionExpression<Function2<TSource, Enumerable<TInner>, TResult>> // 参数resultSelector是结果选择器函数表达式
        resultSelector,
      EqualityComparer<TKey> comparer); // 参数comparer是用于比较键的自定义相等比较器

  /**
   * Produces the set intersection of two sequences by // 使用默认的相等比较器生成两个序列的交集，移除重复项（由Queryable定义）
   * using the default equality comparer to compare values,
   * eliminate duplicates.(Defined by Queryable.)
   */ // 这是一个集合交集操作，返回同时存在于两个序列中的元素
  @Override Queryable<TSource> intersect(Enumerable<TSource> enumerable); // 重写父类的intersect方法，返回交集Queryable序列，参数enumerable是要取交集的序列

  /**
   * Produces the set intersection of two sequences by // 使用默认的相等比较器生成两个序列的交集，使用all参数指示是否移除重复项（由Queryable定义）
   * using the default equality comparer to compare values,
   * using {@code all} to indicate whether to eliminate duplicates.
   * (Defined by Queryable.)
   */ // 这个版本允许控制是否保留结果中的重复元素
  @Override Queryable<TSource> intersect(Enumerable<TSource> enumerable, boolean all); // 重写父类的intersect方法，参数all为true时保留重复元素，为false时移除重复元素

  /**
   * Produces the set intersection of two sequences by // 使用指定的EqualityComparer<TSource>生成两个序列的交集，移除重复项
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, eliminate duplicates.
   */ // 这个版本使用自定义比较器来识别相等元素
  @Override Queryable<TSource> intersect(Enumerable<TSource> enumerable, // 重写父类的intersect方法，使用自定义比较器计算交集
      EqualityComparer<TSource> comparer); // 参数comparer是自定义的相等比较器

  /**
   * Produces the set intersection of two sequences by // 使用指定的EqualityComparer<TSource>生成两个序列的交集，使用all参数指示是否移除重复项
   * using the specified {@code EqualityComparer<TSource>} to compare
   * values, using {@code all} to indicate whether to eliminate duplicates.
   */ // 这是最完整的交集操作，同时支持自定义比较器和重复元素控制
  @Override Queryable<TSource> intersect(Enumerable<TSource> enumerable, // 重写父类的intersect方法，提供最灵活的交集计算
      EqualityComparer<TSource> comparer, boolean all); // 参数comparer是自定义比较器，参数all控制是否保留重复元素

  /**
   * Correlates the elements of two sequences based on // 基于匹配的键关联两个序列的元素，使用默认的相等比较器比较键
   * matching keys. The default equality comparer is used to compare
   * keys.
   */ // 这是一个内连接操作，类似于SQL中的INNER JOIN，只返回两个序列中键匹配的元素对
  <TInner, TKey, TResult> Queryable<TResult> join(Enumerable<TInner> inner, // 定义join方法，TInner是内部序列元素类型，TKey是键类型，TResult是结果类型
      FunctionExpression<Function1<TSource, TKey>> outerKeySelector, // 参数outerKeySelector是外部键选择器函数表达式，从外部序列元素提取键
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 参数innerKeySelector是内部键选择器函数表达式，从内部序列元素提取键
      FunctionExpression<Function2<TSource, TInner, TResult>> resultSelector); // 参数resultSelector是结果选择器函数表达式，接受外部元素和内部元素，生成结果

  /**
   * Correlates the elements of two sequences based on // 基于匹配的键关联两个序列的元素，使用指定的EqualityComparer<TSource>比较键
   * matching keys. A specified {@code EqualityComparer<TSource>} is used to
   * compare keys.
   */ // 这个版本允许自定义键的比较逻辑
  <TInner, TKey, TResult> Queryable<TResult> join(Enumerable<TInner> inner, // 定义带比较器的join方法
      FunctionExpression<Function1<TSource, TKey>> outerKeySelector, // 参数outerKeySelector是外部键选择器函数表达式
      FunctionExpression<Function1<TInner, TKey>> innerKeySelector, // 参数innerKeySelector是内部键选择器函数表达式
      FunctionExpression<Function2<TSource, TInner, TResult>> resultSelector, // 参数resultSelector是结果选择器函数表达式
      EqualityComparer<TKey> comparer); // 参数comparer是用于比较键的自定义相等比较器

  /**
   * Returns the last element of a sequence that // 返回序列中满足指定条件的最后一个元素
   * satisfies a specified condition.
   */ // 这是一个元素访问操作，返回最后一个匹配条件的元素，如果没有找到则抛出异常
  TSource last(FunctionExpression<Predicate1<TSource>> predicate); // 定义last方法，返回TSource类型的元素，参数predicate是用于筛选元素的谓词表达式

  /**
   * Returns the last element of a sequence that // 返回序列中满足条件的最后一个元素，如果没有找到这样的元素则返回默认值
   * satisfies a condition or a default value if no such element is
   * found.
   */ // 这是一个安全的元素访问操作，避免没有找到元素时的异常
  @Nullable TSource lastOrDefault(FunctionExpression<Predicate1<TSource>> predicate); // 定义lastOrDefault方法，返回可能为null的TSource类型元素，参数predicate是筛选谓词表达式

  /**
   * Returns an long that represents the number of // 返回一个long值，表示序列中满足条件的元素数量
   * elements in a sequence that satisfy a condition.
   */ // 这是一个计数操作，与count方法类似但返回long类型，适用于大型集合
  long longCount(FunctionExpression<Predicate1<TSource>> predicate); // 定义longCount方法，返回long类型的计数结果，参数predicate是用于筛选元素的谓词表达式

  /**
   * Invokes a projection function on each element of a // 在泛型IQueryable<TSource>的每个元素上调用投影函数，并返回最大的结果值
   * generic {@code IQueryable<TSource>} and returns the maximum resulting
   * value.
   */ // 这是一个聚合操作，找出序列中的最大值
  <TResult extends Comparable<TResult>> @Nullable TResult max( // 定义max方法，TResult必须实现Comparable接口以便比较，返回可能为null的最大值
      FunctionExpression<Function1<TSource, TResult>> selector); // 参数selector是投影函数表达式，从每个元素中提取用于比较的值

  /**
   * Invokes a projection function on each element of a // 在泛型IQueryable<TSource>的每个元素上调用投影函数，并返回最小的结果值
   * generic {@code IQueryable<TSource>} and returns the minimum resulting
   * value.
   */ // 这是一个聚合操作，找出序列中的最小值
  <TResult extends Comparable<TResult>> @Nullable TResult min( // 定义min方法，TResult必须实现Comparable接口以便比较，返回可能为null的最小值
      FunctionExpression<Function1<TSource, TResult>> selector); // 参数selector是投影函数表达式，从每个元素中提取用于比较的值

  /**
   * Filters the elements of an IQueryable based on a // 根据指定类型过滤IQueryable的元素
   * specified type.
   *
   * <p>The OfType method generates a // OfType方法生成一个表示调用OfType本身的MethodCallExpression（作为构造的泛型方法）
   * {@link org.apache.calcite.linq4j.tree.MethodCallExpression} that represents
   * calling OfType itself as a constructed generic method. It then passes the
   * MethodCallExpression to the CreateQuery(Expression) method of the
   * {@link QueryProvider} represented by the Provider property of the source
   * parameter.
   *
   * <p>The query behavior that occurs as a result of executing an expression // 执行表示调用OfType的表达式树所产生的查询行为取决于source参数类型的实现
   * tree that represents calling OfType depends on the implementation of the
   * type of the source parameter. The expected behavior is that it filters
   * out any elements in source that are not of type TResult.
   *
   * <p>NOTE: clazz parameter not present in C# LINQ; necessary because of // 注意：clazz参数在C# LINQ中不存在；由于Java类型擦除而必须存在
   * Java type erasure.
   */ // 这是一个类型过滤操作，只保留指定类型的元素
  @Override <TResult> Queryable<TResult> ofType(Class<TResult> clazz); // 重写父类的ofType方法，返回只包含指定类型元素的Queryable序列，参数clazz是要过滤的目标类型

  @Override <T2> Queryable<T2> cast(Class<T2> clazz); // 重写父类的cast方法，将序列中的元素强制转换为指定类型，参数clazz是要转换的目标类型

  /**
   * Sorts the elements of a sequence in ascending // 按照键对序列的元素进行升序排序
   * order according to a key.
   */ // 这是一个排序操作，返回有序的可查询序列
  <TKey extends Comparable> OrderedQueryable<TSource> orderBy( // 定义orderBy方法，TKey必须实现Comparable接口以便比较，返回OrderedQueryable以支持后续排序操作
      FunctionExpression<Function1<TSource, TKey>> keySelector); // 参数keySelector是键选择器函数表达式，从每个元素中提取用于排序的键

  /**
   * Sorts the elements of a sequence in ascending // 使用指定的比较器对序列的元素进行升序排序
   * order by using a specified comparer.
   */ // 这个版本允许自定义键的比较逻辑
  <TKey> OrderedQueryable<TSource> orderBy( // 定义带比较器的orderBy方法
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      Comparator<TKey> comparator); // 参数comparator是自定义的比较器，用于定义键的排序规则

  /**
   * Sorts the elements of a sequence in descending // 按照键对序列的元素进行降序排序
   * order according to a key.
   */ // 这是一个降序排序操作，返回有序的可查询序列
  <TKey extends Comparable> OrderedQueryable<TSource> orderByDescending( // 定义orderByDescending方法，TKey必须实现Comparable接口，返回OrderedQueryable
      FunctionExpression<Function1<TSource, TKey>> keySelector); // 参数keySelector是键选择器函数表达式，从每个元素中提取用于排序的键

  /**
   * Sorts the elements of a sequence in descending // 使用指定的比较器对序列的元素进行降序排序
   * order by using a specified comparer.
   */ // 这个版本允许自定义键的比较逻辑
  <TKey> OrderedQueryable<TSource> orderByDescending( // 定义带比较器的orderByDescending方法
      FunctionExpression<Function1<TSource, TKey>> keySelector, // 参数keySelector是键选择器函数表达式
      Comparator<TKey> comparator); // 参数comparator是自定义的比较器，用于定义键的排序规则

  /**
   * Inverts the order of the elements in a sequence. // 反转序列中元素的顺序
   */ // 这是一个序列操作，将序列中的元素按相反顺序排列
  @Override Queryable<TSource> reverse(); // 重写父类的reverse方法，返回元素顺序反转的Queryable序列


  /**
   * Projects each element of a sequence into a new form. // 将序列中的每个元素投影（转换）为新形式
   */ // 这是一个转换操作，对序列中的每个元素应用转换函数，生成新类型的序列
  <TResult> Queryable<TResult> select( // 定义select方法，TResult是转换后的元素类型，返回包含转换后元素的Queryable序列
      FunctionExpression<Function1<TSource, TResult>> selector); // 参数selector是转换函数表达式，接受源元素并返回转换后的元素

  /**
   * Projects each element of a sequence into a new // 将序列中的每个元素投影为新形式，并在元素的投影中结合该元素的索引
   * form by incorporating the element's index.
   *
   * <p>NOTE: Renamed from {@code select} because had same erasure as // 注意：从select重命名为selectN，因为与另一个方法具有相同的类型擦除
   * {@link #select(org.apache.calcite.linq4j.tree.FunctionExpression)}.
   */ // 这个版本在转换函数中提供了元素的索引信息
  <TResult> Queryable<TResult> selectN( // 定义selectN方法（避免类型擦除冲突），TResult是转换后的元素类型
      FunctionExpression<Function2<TSource, Integer, TResult>> selector); // 参数selector是转换函数表达式，接受源元素和索引，返回转换后的元素


  /**
   * Projects each element of a sequence to an // 将序列中的每个元素投影为Enumerable<TResult>，并将生成的序列合并为一个序列
   * {@code Enumerable<TSource>} and combines the resulting sequences into one
   * sequence.
   */ // 这是一个一对多映射操作，将每个元素转换为一个序列，然后将所有序列扁平化合并
  <TResult> Queryable<TResult> selectMany( // 定义selectMany方法，TResult是内部序列的元素类型，返回扁平化后的Queryable序列
      FunctionExpression<Function1<TSource, Enumerable<TResult>>> selector); // 参数selector是一对多映射函数表达式，接受源元素并返回一个序列

  /**
   * Projects each element of a sequence to an // 将序列中的每个元素投影为Enumerable<TResult>，并将生成的序列合并为一个序列，每个源元素的索引用于该元素的投影形式
   * {@code Enumerable<TSource>} and combines the resulting sequences into one
   * sequence. The index of each source element is used in the
   * projected form of that element.
   *
   * <p>NOTE: Renamed from {@code selectMany} because had same erasure as // 注意：从selectMany重命名为selectManyN，因为与另一个方法具有相同的类型擦除
   * {@link #selectMany(org.apache.calcite.linq4j.tree.FunctionExpression)}.
   */ // 这个版本在映射函数中提供了元素的索引信息
  <TResult> Queryable<TResult> selectManyN( // 定义selectManyN方法（避免类型擦除冲突）
      FunctionExpression<Function2<TSource, Integer, Enumerable<TResult>>> // 参数selector是一对多映射函数表达式，接受源元素和索引，返回一个序列
        selector);

  /**
   * Projects each element of a sequence to an // 将序列中的每个元素投影为Enumerable<TSource>，该序列结合了产生它的源元素的索引，对每个中间序列的每个元素调用结果选择器函数，并将结果值合并为一个单一的一维序列并返回
   * {@code Enumerable<TSource>} that incorporates the index of the source
   * element that produced it. A result selector function is invoked
   * on each element of each intermediate sequence, and the
   * resulting values are combined into a single, one-dimensional
   * sequence and returned.
   */ // 这是一个完整的一对多映射操作，支持索引和结果选择
  <TCollection, TResult> Queryable<TResult> selectMany( // 定义带索引和结果选择的selectMany方法
      FunctionExpression<Function2<TSource, Integer, Enumerable<TCollection>>> // 参数collectionSelector是一对多映射函数表达式，接受源元素和索引，返回集合序列
        collectionSelector,
      FunctionExpression<Function2<TSource, TCollection, TResult>> // 参数resultSelector是结果选择器函数表达式，接受源元素和集合元素，生成最终结果
        resultSelector);

  /**
   * Projects each element of a sequence to an // 将序列中的每个元素投影为Enumerable<TSource>，并对其中每个元素调用结果选择器函数，来自每个中间序列的结果值被合并为一个单一的一维序列并返回
   * {@code Enumerable<TSource>} and invokes a result selector function on each
   * element therein. The resulting values from each intermediate
   * sequence are combined into a single, one-dimensional sequence
   * and returned.
   *
   * <p>NOTE: Renamed from {@code selectMany} because had same erasure as // 注意：从selectMany重命名为selectManyN，因为与另一个方法具有相同的类型擦除
   * {@link #selectMany(org.apache.calcite.linq4j.tree.FunctionExpression, org.apache.calcite.linq4j.tree.FunctionExpression)}
   */ // 这个版本支持结果选择但不使用索引
  <TCollection, TResult> Queryable<TResult> selectManyN( // 定义带结果选择的selectManyN方法（避免类型擦除冲突）
      FunctionExpression<Function1<TSource, Enumerable<TCollection>>> // 参数collectionSelector是一对多映射函数表达式，接受源元素，返回集合序列
        collectionSelector,
      FunctionExpression<Function2<TSource, TCollection, TResult>> // 参数resultSelector是结果选择器函数表达式，接受源元素和集合元素，生成最终结果
        resultSelector);

  /**
   * Returns the only element of a sequence that // 返回序列中满足指定条件的唯一元素，如果存在多个这样的元素则抛出异常
   * satisfies a specified condition, and throws an exception if
   * more than one such element exists.
   */ // 这是一个严格的元素访问操作，要求序列中有且只有一个匹配元素
  TSource single(FunctionExpression<Predicate1<TSource>> predicate); // 定义single方法，返回TSource类型的唯一元素，参数predicate是筛选谓词表达式

  /**
   * Returns the only element of a sequence, or a // 返回序列中的唯一元素，如果序列为空则返回默认值；如果序列中有多个元素，此方法将抛出异常
   * default value if the sequence is empty; this method throws an
   * exception if there is more than one element in the
   * sequence.
   */ // 这是一个安全的元素访问操作，但仍然要求序列最多只有一个元素
  @Override @Nullable TSource singleOrDefault(); // 重写父类的singleOrDefault方法，返回可能为null的唯一元素或默认值

  /**
   * Returns the only element of a sequence that // 返回序列中满足指定条件的唯一元素，如果没有这样的元素则返回默认值；如果有多个元素满足条件，此方法将抛出异常
   * satisfies a specified condition or a default value if no such
   * element exists; this method throws an exception if more than
   * one element satisfies the condition.
   */ // 这是一个带条件的安全元素访问操作
  @Nullable TSource singleOrDefault(FunctionExpression<Predicate1<TSource>> predicate); // 定义带条件的singleOrDefault方法，返回可能为null的唯一元素或默认值，参数predicate是筛选谓词表达式

  /**
   * Bypasses a specified number of elements in a // 跳过序列中指定数量的元素，然后返回剩余的元素
   * sequence and then returns the remaining elements.
   */ // 这是一个分页操作，用于跳过序列开头的指定数量元素
  @Override Queryable<TSource> skip(int count); // 重写父类的skip方法，返回跳过指定数量元素后的Queryable序列，参数count是要跳过的元素数量

  /**
   * Bypasses elements in a sequence as long as a // 只要满足指定条件就跳过序列中的元素，然后返回剩余的元素
   * specified condition is true and then returns the remaining
   * elements.
   */ // 这是一个条件跳过操作，从第一个不满足条件的元素开始返回
  Queryable<TSource> skipWhile( // 定义skipWhile方法，返回跳过满足条件元素后的Queryable序列
      FunctionExpression<Predicate1<TSource>> predicate); // 参数predicate是谓词表达式，用于确定是否跳过元素

  /**
   * Bypasses elements in a sequence as long as a // 只要满足指定条件就跳过序列中的元素，然后返回剩余的元素，元素的索引用于谓词函数的逻辑
   * specified condition is true and then returns the remaining
   * elements. The element's index is used in the logic of the
   * predicate function.
   */ // 这个版本在谓词函数中提供了元素的索引信息
  Queryable<TSource> skipWhileN( // 定义skipWhileN方法，支持索引的条件跳过
      FunctionExpression<Predicate2<TSource, Integer>> predicate); // 参数predicate是二元谓词表达式，接受元素和索引，用于确定是否跳过元素

  /**
   * Computes the sum of the sequence of Decimal values // 计算Decimal值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */ // 这是一个数值聚合操作，计算BigDecimal类型元素的总和
  BigDecimal sumBigDecimal( // 定义sumBigDecimal方法，返回BigDecimal类型的总和
      FunctionExpression<BigDecimalFunction1<TSource>> selector); // 参数selector是投影函数表达式，从每个元素中提取BigDecimal值

  /**
   * Computes the sum of the sequence of nullable // 计算可空Decimal值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * Decimal values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */ // 这个版本处理可能为null的BigDecimal值，计算时会忽略null值
  BigDecimal sumNullableBigDecimal( // 定义sumNullableBigDecimal方法，返回BigDecimal类型的总和
      FunctionExpression<NullableBigDecimalFunction1<TSource>> selector); // 参数selector是可空BigDecimal投影函数表达式，从每个元素中提取可能为null的BigDecimal值

  /**
   * Computes the sum of the sequence of Double values // 计算Double值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */ // 这是一个数值聚合操作，计算double类型元素的总和
  double sumDouble(FunctionExpression<DoubleFunction1<TSource>> selector); // 定义sumDouble方法，返回double类型的总和
  // 参数selector是投影函数表达式，从每个元素中提取double值

  /**
   * Computes the sum of the sequence of nullable // 计算可空Double值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * Double values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */ // 这个版本处理可能为null的Double值，计算时会忽略null值
  Double sumNullableDouble( // 定义sumNullableDouble方法，返回Double包装类型的总和（可能为null）
      FunctionExpression<NullableDoubleFunction1<TSource>> selector); // 参数selector是可空double投影函数表达式，从每个元素中提取可能为null的double值

  /**
   * Computes the sum of the sequence of int values // 计算int值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */ // 这是一个数值聚合操作，计算int类型元素的总和
  int sumInteger(FunctionExpression<IntegerFunction1<TSource>> selector); // 定义sumInteger方法，返回int类型的总和
  // 参数selector是投影函数表达式，从每个元素中提取int值

  /**
   * Computes the sum of the sequence of nullable int // 计算可空int值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   */ // 这个版本处理可能为null的int值，计算时会忽略null值
  Integer sumNullableInteger( // 定义sumNullableInteger方法，返回Integer包装类型的总和（可能为null）
      FunctionExpression<NullableIntegerFunction1<TSource>> selector); // 参数selector是可空int投影函数表达式，从每个元素中提取可能为null的int值

  /**
   * Computes the sum of the sequence of long values // 计算long值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */ // 这是一个数值聚合操作，计算long类型元素的总和
  long sumLong(FunctionExpression<LongFunction1<TSource>> selector); // 定义sumLong方法，返回long类型的总和
  // 参数selector是投影函数表达式，从每个元素中提取long值

  /**
   * Computes the sum of the sequence of nullable long // 计算可空long值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * values that is obtained by invoking a projection function on
   * each element of the input sequence.
   */ // 这个版本处理可能为null的long值，计算时会忽略null值
  Long sumNullableLong( // 定义sumNullableLong方法，返回Long包装类型的总和（可能为null）
      FunctionExpression<NullableLongFunction1<TSource>> selector); // 参数selector是可空long投影函数表达式，从每个元素中提取可能为null的long值

  /**
   * Computes the sum of the sequence of Float values // 计算Float值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * that is obtained by invoking a projection function on each
   * element of the input sequence.
   */ // 这是一个数值聚合操作，计算float类型元素的总和
  float sumFloat(FunctionExpression<FloatFunction1<TSource>> selector); // 定义sumFloat方法，返回float类型的总和
  // 参数selector是投影函数表达式，从每个元素中提取float值

  /**
   * Computes the sum of the sequence of nullable // 计算可空Float值序列的总和，这些值是通过在输入序列的每个元素上调用投影函数获得的
   * Float values that is obtained by invoking a projection
   * function on each element of the input sequence.
   */ // 这个版本处理可能为null的Float值，计算时会忽略null值
  Float sumNullableFloat( // 定义sumNullableFloat方法，返回Float包装类型的总和（可能为null）
      FunctionExpression<NullableFloatFunction1<TSource>> selector); // 参数selector是可空float投影函数表达式，从每个元素中提取可能为null的float值

  /**
   * Returns a specified number of contiguous elements // 从序列开头返回指定数量的连续元素
   * from the start of a sequence.
   */ // 这是一个分页操作，用于获取序列开头的指定数量元素
  @Override Queryable<TSource> take(int count); // 重写父类的take方法，返回包含指定数量元素的Queryable序列，参数count是要获取的元素数量

  /**
   * Returns elements from a sequence as long as a // 只要满足指定条件就从序列中返回元素
   * specified condition is true.
   */ // 这是一个条件获取操作，从第一个不满足条件的元素开始停止返回
  Queryable<TSource> takeWhile( // 定义takeWhile方法，返回满足条件元素的Queryable序列
      FunctionExpression<Predicate1<TSource>> predicate); // 参数predicate是谓词表达式，用于确定是否包含元素

  /**
   * Returns elements from a sequence as long as a // 只要满足指定条件就从序列中返回元素，元素的索引用于谓词函数的逻辑
   * specified condition is true. The element's index is used in the
   * logic of the predicate function.
   */ // 这个版本在谓词函数中提供了元素的索引信息
  Queryable<TSource> takeWhileN( // 定义takeWhileN方法，支持索引的条件获取
      FunctionExpression<Predicate2<TSource, Integer>> predicate); // 参数predicate是二元谓词表达式，接受元素和索引，用于确定是否包含元素

  /**
   * Produces the set union of two sequences by using // 使用默认的相等比较器生成两个序列的并集
   * the default equality comparer.
   */ // 这是一个集合并集操作，返回两个序列中所有不重复的元素
  @Override Queryable<TSource> union(Enumerable<TSource> source1); // 重写父类的union方法，返回并集Queryable序列，参数source1是要合并的序列

  /**
   * Produces the set union of two sequences by using a // 使用指定的EqualityComparer<TSource>生成两个序列的并集
   * specified {@code EqualityComparer<TSource>}.
   */ // 这个版本允许自定义元素的比较逻辑
  @Override Queryable<TSource> union(Enumerable<TSource> source1, // 重写父类的union方法，使用自定义比较器计算并集
      EqualityComparer<TSource> comparer); // 参数comparer是自定义的相等比较器

  /**
   * Filters a sequence of values based on a // 基于谓词过滤值序列
   * predicate.
   */ // 这是一个过滤操作，只保留满足谓词条件的元素
  Queryable<TSource> where( // 定义where方法，返回过滤后的Queryable序列
      FunctionExpression<? extends Predicate1<TSource>> predicate); // 参数predicate是谓词表达式，用于测试每个元素是否满足条件

  /**
   * Filters a sequence of values based on a // 基于谓词过滤值序列，每个元素的索引用于谓词函数的逻辑
   * predicate. Each element's index is used in the logic of the
   * predicate function.
   */ // 这个版本在谓词函数中提供了元素的索引信息
  Queryable<TSource> whereN( // 定义whereN方法，支持索引的过滤
      FunctionExpression<? extends Predicate2<TSource, Integer>> predicate); // 参数predicate是二元谓词表达式，接受元素和索引，用于测试是否满足条件

  /**
   * Merges two sequences by using the specified // 使用指定的谓词函数合并两个序列
   * predicate function.
   */ // 这是一个合并操作，将两个序列的元素按位置配对并应用转换函数
  <T1, TResult> Queryable<TResult> zip(Enumerable<T1> source1, // 定义zip方法，T1是第二个序列的元素类型，TResult是结果类型，参数source1是要合并的第二个序列
      FunctionExpression<Function2<TSource, T1, TResult>> resultSelector); // 参数resultSelector是结果选择器函数表达式，接受两个序列的对应元素，生成结果
}