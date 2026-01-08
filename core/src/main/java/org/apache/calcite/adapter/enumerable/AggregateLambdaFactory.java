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
package org.apache.calcite.adapter.enumerable; // 包声明：该类位于org.apache.calcite.adapter.enumerable包下，属于Calcite的可枚举适配器模块

import org.apache.calcite.linq4j.function.Function0; // 导入Function0接口：表示无参数的函数，用于生成初始累加器
import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口：表示单参数函数，用于结果选择
import org.apache.calcite.linq4j.function.Function2; // 导入Function2接口：表示双参数函数，用于累加操作

/**
 * Generates lambda functions used in {@link EnumerableAggregate}. // 类作用：生成用于EnumerableAggregate（可枚举聚合）的lambda函数
 *
 * <p>This interface allows a implicit accumulator type variation. // 该接口允许隐式的累加器类型转换
 * ({@code OAccumulate} {@literal ->} {@code TAccumulate}) // 从原始累加器类型(OAccumulate)转换到变化后的累加器类型(TAccumulate)
 *
 * @param <TSource> Type of the enumerable input source // 泛型参数TSource：可枚举输入源的类型，即聚合操作要处理的数据元素类型
 * @param <TOrigAccumulate> Type of the original accumulator // 泛型参数TOrigAccumulate：原始累加器的类型，即用户定义的聚合函数使用的累加器类型
 * @param <TAccumulate> Type of the varied accumulator // 泛型参数TAccumulate：变化后的累加器类型，用于优化或特殊场景的累加器实现
 * @param <TResult> Type of the enumerable output result // 泛型参数TResult：聚合操作的结果类型，即最终输出的数据类型
 * @param <TKey> Type of the group-by key // 泛型参数TKey：分组键的类型，用于GROUP BY操作时标识不同的分组
 */
public interface AggregateLambdaFactory<TSource, TOrigAccumulate, TAccumulate, // 接口声明：聚合Lambda工厂接口，定义了生成聚合操作所需lambda函数的方法
    TResult, TKey> { // 接口声明的泛型参数列表继续
  Function0<TAccumulate> accumulatorInitializer(); // 方法：创建累加器初始化函数，返回一个无参数函数，该函数调用时创建一个新的累加器实例，用于每个分组或整个聚合的初始状态

  Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder(); // 方法：创建累加器添加函数，返回一个双参数函数，接收当前累加器值和输入元素，返回更新后的累加器值，实现累加逻辑

  Function1<TAccumulate, TResult> singleGroupResultSelector( // 方法：创建单分组结果选择器，用于没有GROUP BY或只有一个分组的场景，接收原始结果选择器并适配到变化后的累加器类型
      Function1<TOrigAccumulate, TResult> resultSelector); // 参数：原始结果选择器函数，将原始累加器类型转换为最终结果类型

  Function2<TKey, TAccumulate, TResult> resultSelector( // 方法：创建多分组结果选择器，用于GROUP BY场景，接收原始结果选择器并适配到变化后的累加器类型
      Function2<TKey, TOrigAccumulate, TResult> resultSelector); // 参数：原始结果选择器函数，接收分组键和原始累加器，返回该分组的最终结果
}
