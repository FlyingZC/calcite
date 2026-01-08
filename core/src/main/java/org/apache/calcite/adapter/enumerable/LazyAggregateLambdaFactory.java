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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于Calcite的枚举适配器包

import org.apache.calcite.linq4j.function.Function0; // 导入Function0接口，表示无参函数，用于初始化累加器
import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口，表示单参函数，用于转换结果
import org.apache.calcite.linq4j.function.Function2; // 导入Function2接口，表示双参函数，用于累加和结果选择

import java.util.ArrayList; // 导入ArrayList类，用于实现LazySource的列表缓存
import java.util.Iterator; // 导入Iterator接口，用于遍历LazySource中的元素
import java.util.List; // 导入List接口，用于存储多个LazyAccumulator

/**
 * 生成聚合Lambda表达式的工厂类，该实现在调用每个聚合添加器之前会保留输入源，
 * 这种实现通常用于需要在执行聚合之前对输入进行排序的场景。
 * 
 * 核心思想：延迟聚合（Lazy Aggregation）- 先收集所有输入数据，然后在结果选择阶段才执行聚合操作
 * 这样可以在聚合前对数据进行排序或其他预处理操作
 *
 * @param <TSource> 可枚举输入源的类型，表示输入数据的类型
 * @param <TKey> 分组键的类型，用于GROUP BY操作
 * @param <TOrigAccumulate> 原始累加器的类型，用于存储聚合中间结果
 * @param <TResult> 可枚举输出结果的类型，表示最终聚合结果的类型
 */
public class LazyAggregateLambdaFactory<TSource, TKey, TOrigAccumulate, TResult> // 定义泛型类，实现AggregateLambdaFactory接口
    implements AggregateLambdaFactory<TSource, TOrigAccumulate, // 实现AggregateLambdaFactory接口，指定泛型参数
    LazyAggregateLambdaFactory.LazySource<TSource>, TResult, TKey> { // 使用LazySource作为累加器类型，而不是直接使用TOrigAccumulate

  // 成员变量：累加器初始化函数，这是一个无参函数，调用后会创建一个新的累加器实例
  // 用于在开始聚合时初始化累加器，例如创建一个初始值为0的计数器或初始值为null的求和器
  private final Function0<TOrigAccumulate> accumulatorInitializer; // final修饰表示初始化后不可变，确保线程安全
  
  // 成员变量：延迟累加器列表，存储了所有需要执行的聚合操作
  // 每个LazyAccumulator代表一个聚合函数（如SUM、COUNT、AVG等），它们会在结果选择阶段被依次执行
  // 使用List是因为一个SQL查询可能包含多个聚合函数，例如"SELECT SUM(salary), COUNT(*) FROM employees"
  private final List<LazyAccumulator<TOrigAccumulate, TSource>> accumulators; // final修饰表示初始化后不可变

  // 构造方法：创建LazyAggregateLambdaFactory实例
  // @param accumulatorInitializer 累加器初始化函数，用于创建新的累加器实例
  // @param accumulators 延迟累加器列表，包含所有需要执行的聚合操作
  public LazyAggregateLambdaFactory( // 构造方法定义
      Function0<TOrigAccumulate> accumulatorInitializer, // 参数1：累加器初始化函数
      List<LazyAccumulator<TOrigAccumulate, TSource>> accumulators) { // 参数2：延迟累加器列表
    this.accumulatorInitializer = accumulatorInitializer; // 将传入的初始化函数赋值给成员变量
    this.accumulators = accumulators; // 将传入的累加器列表赋值给成员变量
  }

  // 重写方法：返回累加器初始化函数
  // 该方法返回一个函数，该函数创建LazySource实例而不是直接创建TOrigAccumulate实例
  // LazySource是一个包装器，它会在内部缓存所有输入数据，直到结果选择阶段才执行聚合
  // @return 返回一个创建LazySource实例的函数
  @Override public Function0<LazySource<TSource>> accumulatorInitializer() { // 重写接口方法
    return LazySource::new; // 返回方法引用，等价于 () -> new LazySource<>()，创建新的LazySource实例
  }

  // 重写方法：返回累加器添加函数
  // 该函数接收一个LazySource和一个输入元素，将输入元素添加到LazySource中
  // 注意：这里并不执行实际的聚合操作，只是将输入数据缓存到LazySource中
  // 实际的聚合操作会在结果选择阶段（resultSelector）中执行
  // @return 返回一个双参函数，第一个参数是LazySource，第二个参数是输入元素，返回更新后的LazySource
  @Override public Function2<LazySource<TSource>, // 重写接口方法，返回类型是Function2
      TSource, LazySource<TSource>> accumulatorAdder() { // 泛型参数：输入是LazySource和TSource，输出是LazySource
    return (lazySource, source) -> { // Lambda表达式：接收lazySource和source两个参数
      lazySource.add(source); // 将输入元素添加到LazySource的缓存列表中，不执行聚合
      return lazySource; // 返回更新后的lazySource，支持链式调用
    };
  }

  // 重写方法：返回单组结果选择函数（没有GROUP BY的情况）
  // 该函数在所有数据收集完成后执行，它会：
  // 1. 创建一个新的累加器实例
  // 2. 遍历所有延迟累加器，对缓存的数据执行聚合操作
  // 3. 将累加器结果转换为最终结果
  // @param resultSelector 结果选择函数，用于将累加器转换为最终结果
  // @return 返回一个单参函数，接收LazySource，返回最终聚合结果
  @Override public Function1<LazySource<TSource>, TResult> singleGroupResultSelector( // 重写接口方法
      Function1<TOrigAccumulate, TResult> resultSelector) { // 参数：将累加器转换为最终结果的函数
    return lazySource -> { // Lambda表达式：接收缓存了所有数据的lazySource
      final TOrigAccumulate accumulator = accumulatorInitializer.apply(); // 调用初始化函数，创建新的累加器实例
      for (LazyAccumulator<TOrigAccumulate, TSource> acc : accumulators) { // 遍历所有延迟累加器（每个代表一个聚合函数）
        acc.accumulate(lazySource, accumulator); // 对缓存的数据执行聚合操作，更新累加器状态
      } // 循环结束后，accumulator包含了所有聚合函数的结果
      return resultSelector.apply(accumulator); // 将累加器转换为最终结果并返回
    };
  }

  // 重写方法：返回分组结果选择函数（有GROUP BY的情况）
  // 该函数与singleGroupResultSelector类似，但额外接收分组键参数
  // 它会在每个分组上执行聚合操作，然后返回该分组的聚合结果
  // @param resultSelector 结果选择函数，接收分组键和累加器，返回最终结果
  // @return 返回一个双参函数，接收分组键和LazySource，返回该分组的聚合结果
  @Override public Function2<TKey, LazySource<TSource>, TResult> resultSelector( // 重写接口方法
      Function2<TKey, TOrigAccumulate, TResult> resultSelector) { // 参数：接收分组键和累加器，返回最终结果
    return (groupByKey, lazySource) -> { // Lambda表达式：接收分组键和该分组的数据缓存
      final TOrigAccumulate accumulator = accumulatorInitializer.apply(); // 调用初始化函数，创建新的累加器实例
      for (LazyAccumulator<TOrigAccumulate, TSource> acc : accumulators) { // 遍历所有延迟累加器
        acc.accumulate(lazySource, accumulator); // 对该分组的数据执行聚合操作
      } // 循环结束后，accumulator包含该分组所有聚合函数的结果
      return resultSelector.apply(groupByKey, accumulator); // 将分组键和累加器转换为最终结果并返回
    };
  }

  /**
   * 静态内部类：LazySource - 用于缓存输入源的数据
   * 该类实现了Iterable接口，可以像集合一样遍历
   * 它的作用是在聚合前收集所有输入数据，然后在结果选择阶段才执行聚合操作
   * 这样可以在聚合前对数据进行排序或其他预处理操作
   *
   * @param <TSource> 可枚举输入源的类型
   */
  public static class LazySource<TSource> implements Iterable<TSource> { // 静态内部类，实现Iterable接口
    // 成员变量：使用ArrayList缓存所有输入数据
    // ArrayList提供了动态数组的功能，可以高效地添加和遍历元素
    // final修饰表示引用不可变，但列表内容可以修改
    private final List<TSource> list = new ArrayList<>(); // 初始化为空的ArrayList，用于存储输入数据

    // 私有方法：向缓存列表中添加一个输入元素
    // 该方法只能在LazyAggregateLambdaFactory内部调用（通过accumulatorAdder）
    // @param source 要添加的输入元素
    private void add(TSource source) { // 私有方法，外部无法直接调用
      list.add(source); // 将元素添加到ArrayList末尾，时间复杂度O(1)
    }

    // 重写方法：返回迭代器，用于遍历缓存的数据
    // 该方法允许LazySource支持for-each循环和其他迭代操作
    // 在聚合阶段，LazyAccumulator会通过这个迭代器遍历所有缓存的数据
    // @return 返回列表的迭代器
    @Override public Iterator<TSource> iterator() { // 重写Iterable接口方法
      return list.iterator(); // 返回ArrayList的迭代器，支持安全的遍历操作
    }
  }

  /**
   * 接口：LazyAccumulator - 在缓存的输入源上执行聚合操作
   * 该接口定义了如何在LazySource（缓存的数据）上执行聚合操作
   * 每个LazyAccumulator实例代表一个聚合函数（如SUM、COUNT、AVG等）
   * 
   * 工作流程：
   * 1. 在accumulatorAdder阶段，数据被收集到LazySource中（不执行聚合）
   * 2. 在resultSelector阶段，调用LazyAccumulator.accumulate方法执行实际的聚合操作
   *
   * @param <TOrigAccumulate> 原始累加器的类型，用于存储聚合中间结果
   * @param <TSource> 可枚举输入源的类型
   */
  public interface LazyAccumulator<TOrigAccumulate, TSource> { // 接口定义，定义聚合操作的标准
    // 方法：对缓存的数据执行聚合操作
    // 该方法会遍历sourceIterable中的所有数据，并将聚合结果更新到accumulator中
    // @param sourceIterable 缓存的输入数据，通常是一个LazySource实例
    // @param accumulator 累加器实例，用于存储聚合中间结果，方法会修改这个对象的状态
    void accumulate(Iterable<TSource> sourceIterable, TOrigAccumulate accumulator); // 接口方法，由具体实现类提供
  }
}
