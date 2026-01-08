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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于org.apache.calcite.adapter.enumerable包，用于可枚举适配器相关功能

import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ风格的集合操作方法
import org.apache.calcite.linq4j.function.Function1; // 导入Function1函数式接口，表示接受一个参数并返回一个结果的函数
import org.apache.calcite.linq4j.function.Function2; // 导入Function2函数式接口，表示接受两个参数并返回一个结果的函数

import java.util.Comparator; // 导入Comparator接口，用于定义对象之间的比较规则
import java.util.List; // 导入List接口，表示有序的元素集合

/**
 * Helper that combines the sorting process and accumulating process against the
 * aggregate execution, used with {@link LazyAggregateLambdaFactory}.
 * 辅助类，将排序过程和累加过程结合起来，用于聚合执行，与LazyAggregateLambdaFactory配合使用
 *
 * @param <TAccumulate> Type of the accumulator // 累加器的类型参数，用于存储聚合计算的中间结果
 * @param <TSource>     Type of the enumerable input source // 可枚举输入源的类型参数，表示待处理的数据源
 * @param <TSortKey>    Type of the sort key // 排序键的类型参数，用于指定排序依据的字段
 */
public class SourceSorter<TAccumulate, TSource, TSortKey> // 定义SourceSorter泛型类，实现LazyAccumulator接口
    implements LazyAggregateLambdaFactory.LazyAccumulator<TAccumulate, TSource> { // 实现LazyAccumulator接口，提供延迟累加功能

  private final Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder; // 累加器添加函数，接受累加器和源元素，返回更新后的累加器，用于将源元素添加到累加器中
  private final Function1<TSource, TSortKey> keySelector; // 键选择器函数，从源元素中提取排序键，用于确定排序依据
  private final Comparator<TSortKey> comparator; // 比较器，用于比较两个排序键的大小，决定排序顺序

  public SourceSorter( // 构造方法，创建SourceSorter实例
      Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder, // 参数：累加器添加函数，定义如何将元素添加到累加器中
      Function1<TSource, TSortKey> keySelector, // 参数：键选择器函数，定义如何从元素中提取排序键
      Comparator<TSortKey> comparator) { // 参数：比较器，定义排序键的比较规则
    this.accumulatorAdder = accumulatorAdder; // 将传入的累加器添加函数赋值给成员变量
    this.keySelector = keySelector; // 将传入的键选择器函数赋值给成员变量
    this.comparator = comparator; // 将传入的比较器赋值给成员变量
  }

  @Override public void accumulate(Iterable<TSource> sourceIterable, // 重写accumulate方法，对源数据进行累加处理
      TAccumulate accumulator) { // 参数：累加器，用于存储聚合结果
    sortAndAccumulate(sourceIterable, accumulator); // 调用sortAndAccumulate方法，先排序后累加
  }

  private void sortAndAccumulate(Iterable<TSource> sourceIterable, // 私有方法，执行排序和累加操作
      TAccumulate accumulator) { // 参数：累加器，用于存储聚合结果
    List<TSource> sorted = Linq4j.asEnumerable(sourceIterable) // 将可迭代对象转换为Linq4j的Enumerable，以便使用LINQ操作
        .orderBy(keySelector, comparator) // 使用键选择器和比较器对元素进行排序，返回排序后的Enumerable
        .toList(); // 将排序后的Enumerable转换为List，以便进行迭代处理
    TAccumulate accumulator1 = accumulator; // 创建累加器的副本，用于在循环中更新
    for (TSource source : sorted) { // 遍历排序后的源元素列表
      accumulator1 = accumulatorAdder.apply(accumulator1, source); // 将当前元素添加到累加器中，更新累加器状态
    } // 循环结束，所有元素都已按顺序添加到累加器中
  } // 方法结束
} // 类结束
