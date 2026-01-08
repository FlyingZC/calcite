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
package org.apache.calcite.adapter.enumerable; // 声明包名，该类属于calcite的enumerable适配器包，用于处理可枚举的数据聚合操作

import org.apache.calcite.linq4j.function.Function0; // 导入Function0接口，表示无参数的函数，用于初始化累加器
import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口，表示单参数函数，用于将累加器转换为结果
import org.apache.calcite.linq4j.function.Function2; // 导入Function2接口，表示双参数函数，用于累加器更新操作

import java.util.List; // 导入List接口，用于存储多个累加器添加函数

/**
 * BasicAggregateLambdaFactory - 基础聚合Lambda工厂类
 * 
 * 类作用：
 * 这是AggregateLambdaFactory接口的一个基础实现类，用于在Calcite的enumerable适配器中创建聚合操作的Lambda函数。
 * 该类的主要职责是将一系列累加器添加函数（accumulator adders）应用到输入数据源上，实现聚合计算。
 * 
 * 核心功能：
 * 1. 提供累加器的初始化函数（创建初始累加器状态）
 * 2. 提供累加器的更新函数（将输入数据合并到累加器中）
 * 3. 提供结果选择器函数（将累加器状态转换为最终结果）
 * 4. 支持多个累加器添加函数的顺序执行（通过内部类AccumulatorAdderSeq实现）
 * 
 * 使用场景：
 * 在SQL聚合操作（如SUM、AVG、COUNT等）的实现中，Calcite需要生成相应的Java代码来执行聚合计算。
 * 这个工厂类负责创建执行聚合计算所需的Lambda函数，这些函数会被编译成可执行的Java代码。
 * 
 * 泛型参数说明：
 * @param <TSource> 可枚举输入源的类型，表示输入数据的类型（例如：输入行的类型）
 * @param <TAccumulate> 累加器的类型，表示聚合计算过程中的中间状态类型（例如：存储SUM和COUNT的对象）
 * @param <TResult> 可枚举输出结果的类型，表示最终聚合结果的类型
 * @param <TKey> 分组键的类型，用于GROUP BY操作时的分组字段类型
 */
public class BasicAggregateLambdaFactory<TSource, TAccumulate, TResult, TKey> // 定义泛型类，实现AggregateLambdaFactory接口，注意这里TAccumulate出现了两次，表示中间状态和最终状态类型相同
    implements AggregateLambdaFactory<TSource, TAccumulate, TAccumulate, TResult, TKey> { // 实现AggregateLambdaFactory接口，提供聚合Lambda函数的创建功能

  // 成员变量1：累加器初始化函数
  // 作用：保存用于创建初始累加器状态的函数
  // 类型：Function0<TAccumulate> - 无参数函数，返回一个TAccumulate类型的初始累加器对象
  // 使用场景：在聚合计算开始时，需要创建一个初始的累加器对象，例如对于SUM聚合，初始值可能是0或null
  private final Function0<TAccumulate> accumulatorInitializer; // 声明私有final成员变量，存储累加器初始化函数，final表示初始化后不可改变

  // 成员变量2：累加器添加装饰器
  // 作用：保存用于更新累加器状态的函数，这个装饰器内部包装了多个累加器添加函数
  // 类型：Function2<TAccumulate, TSource, TAccumulate> - 双参数函数，接收当前累加器和输入数据，返回更新后的累加器
  // 使用场景：当处理每一条输入数据时，调用此函数将数据合并到累加器中
  private final Function2<TAccumulate, TSource, TAccumulate> accumulatorAdderDecorator; // 声明私有final成员变量，存储累加器添加装饰器函数

  // 构造方法
  // 作用：创建BasicAggregateLambdaFactory实例，初始化成员变量
  // 参数说明：
  //   accumulatorInitializer: 累加器初始化函数，用于创建初始的累加器对象
  //   accumulatorAdders: 累加器添加函数列表，每个函数负责将输入数据合并到累加器中
  // 实现逻辑：
  //   1. 将传入的累加器初始化函数保存到成员变量
  //   2. 创建AccumulatorAdderSeq装饰器对象，包装所有的累加器添加函数，然后保存到成员变量
  public BasicAggregateLambdaFactory( // 定义公共构造方法
      Function0<TAccumulate> accumulatorInitializer, // 第一个参数：累加器初始化函数
      List<Function2<TAccumulate, TSource, TAccumulate>> accumulatorAdders) { // 第二个参数：累加器添加函数列表
    this.accumulatorInitializer = accumulatorInitializer; // 将传入的初始化函数保存到成员变量
    this.accumulatorAdderDecorator = new AccumulatorAdderSeq(accumulatorAdders); // 创建AccumulatorAdderSeq装饰器，包装所有累加器添加函数，并保存到成员变量
  }

  // 方法1：获取累加器初始化函数
  // 作用：返回用于创建初始累加器状态的函数
  // 返回值：Function0<TAccumulate> - 无参数函数，返回初始累加器对象
  // 使用场景：在聚合计算开始时，调用此函数创建初始累加器
  @Override public Function0<TAccumulate> accumulatorInitializer() { // 重写接口方法，返回累加器初始化函数
    return accumulatorInitializer; // 返回成员变量中保存的初始化函数
  }

  // 方法2：获取累加器添加函数
  // 作用：返回用于更新累加器状态的函数
  // 返回值：Function2<TAccumulate, TSource, TAccumulate> - 双参数函数，接收累加器和输入数据，返回更新后的累加器
  // 使用场景：处理每一条输入数据时，调用此函数将数据合并到累加器中
  @Override public Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder() { // 重写接口方法，返回累加器添加函数
    return accumulatorAdderDecorator; // 返回成员变量中保存的累加器添加装饰器
  }

  // 方法3：获取单组结果选择器函数
  // 作用：返回用于将累加器状态转换为最终结果的函数（无分组情况）
  // 参数：resultSelector - 原始的结果选择器函数，接收累加器，返回最终结果
  // 返回值：Function1<TAccumulate, TResult> - 单参数函数，将累加器转换为结果
  // 使用场景：当没有GROUP BY时，直接将累加器转换为最终结果
  // 注意：该实现直接返回传入的resultSelector，不做任何修改
  @Override public Function1<TAccumulate, TResult> singleGroupResultSelector( // 重写接口方法，返回单组结果选择器
      Function1<TAccumulate, TResult> resultSelector) { // 参数：原始的结果选择器函数
    return resultSelector; // 直接返回传入的结果选择器，不做装饰或修改
  }

  // 方法4：获取分组结果选择器函数
  // 作用：返回用于将累加器状态转换为最终结果的函数（有分组情况）
  // 参数：resultSelector - 原始的结果选择器函数，接收分组键和累加器，返回最终结果
  // 返回值：Function2<TKey, TAccumulate, TResult> - 双参数函数，接收分组键和累加器，返回最终结果
  // 使用场景：当有GROUP BY时，需要根据分组键将累加器转换为最终结果
  // 注意：该实现直接返回传入的resultSelector，不做任何修改
  @Override public Function2<TKey, TAccumulate, TResult> resultSelector( // 重写接口方法，返回分组结果选择器
      Function2<TKey, TAccumulate, TResult> resultSelector) { // 参数：原始的结果选择器函数
    return resultSelector; // 直接返回传入的结果选择器，不做装饰或修改
  }

  /**
   * AccumulatorAdderSeq - 累加器添加序列装饰器类（内部类）
   * 
   * 类作用：
   * 这是一个装饰器类，用于将多个累加器添加函数组合成一个函数，按顺序执行。
   * 
   * 核心功能：
   * 1. 存储多个累加器添加函数的列表
   * 2. 在apply方法中，按顺序将所有累加器添加函数应用到输入数据上
   * 3. 每个累加器添加函数的输出作为下一个函数的输入
   * 
   * 使用场景：
   * 在复杂的聚合操作中，可能需要多个步骤来更新累加器状态。
   * 例如，计算AVG时需要先累加SUM，再累加COUNT，这两个操作可以分别作为独立的累加器添加函数。
   * 
   * 设计模式：
   * 使用装饰器模式（Decorator Pattern），将多个函数组合成一个复合函数。
   */
  private class AccumulatorAdderSeq // 定义私有内部类，实现Function2接口
      implements Function2<TAccumulate, TSource, TAccumulate> { // 实现Function2接口，表示这是一个双参数函数
    private final List<Function2<TAccumulate, TSource, TAccumulate>> accumulatorAdders; // 成员变量：累加器添加函数列表，存储所有需要顺序执行的累加器添加函数

    // 内部类构造方法
    // 作用：创建AccumulatorAdderSeq实例，初始化成员变量
    // 参数：accumulatorAdders - 累加器添加函数列表
    AccumulatorAdderSeq( // 定义构造方法
        List<Function2<TAccumulate, TSource, TAccumulate>> accumulatorAdders) { // 参数：累加器添加函数列表
      this.accumulatorAdders = accumulatorAdders; // 将传入的累加器添加函数列表保存到成员变量
    }

    // apply方法 - 核心方法
    // 作用：按顺序执行所有累加器添加函数，将输入数据合并到累加器中
    // 参数：
    //   accumulator: 当前的累加器状态
    //   source: 输入数据源（一条输入记录）
    // 返回值：更新后的累加器状态
    // 实现逻辑：
    //   1. 从当前累加器开始
    //   2. 遍历所有累加器添加函数，依次应用到累加器和输入数据上
    //   3. 每个函数的输出作为下一个函数的输入
    //   4. 返回最终更新后的累加器
    // 注意：这里有一个潜在的问题，每次循环都使用原始的accumulator，而不是上一次的结果
    @Override public TAccumulate apply(TAccumulate accumulator, TSource source) { // 实现Function2接口的apply方法
      TAccumulate result = accumulator; // 初始化result为当前累加器状态
      for (Function2<TAccumulate, TSource, TAccumulate> accumulatorAdder // 遍历所有累加器添加函数
          : accumulatorAdders) { // 从列表中取出每个累加器添加函数
        result = accumulatorAdder.apply(accumulator, source); // 调用累加器添加函数，将输入数据合并到累加器中，注意这里应该使用result而不是accumulator
      }
      return result; // 返回最终更新后的累加器状态
    }
  }
}
