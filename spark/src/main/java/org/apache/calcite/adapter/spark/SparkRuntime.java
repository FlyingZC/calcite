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
package org.apache.calcite.adapter.spark; // 声明包名，该类位于 org.apache.calcite.adapter.spark 包中，是 Calcite Spark 适配器的一部分

import org.apache.calcite.DataContext; // 导入 DataContext 接口，用于在 Calcite 执行过程中传递上下文信息，如变量、Schema 等
import org.apache.calcite.linq4j.Enumerable; // 导入 Enumerable 接口，是 Calcite LINQ4J 框架中的核心接口，表示可枚举的数据集合
import org.apache.calcite.linq4j.Linq4j; // 导入 Linq4j 工具类，提供创建 Enumerable 等静态方法

import org.apache.spark.api.java.JavaRDD; // 导入 JavaRDD 接口，是 Spark Java API 中表示弹性分布式数据集的核心接口
import org.apache.spark.api.java.JavaSparkContext; // 导入 JavaSparkContext 类，是 Spark Java API 的主要入口点，用于创建 RDD、累加器等
import org.apache.spark.api.java.function.FlatMapFunction; // 导入 FlatMapFunction 接口，是 Spark 中的函数式接口，用于将输入元素映射为零个或多个输出元素

import java.util.Arrays; // 导入 Arrays 工具类，提供操作数组的各种静态方法
import java.util.List; // 导入 List 接口，表示有序的集合

/**
 * Runtime utilities for Calcite's Spark adapter. Generated code calls these
 * methods.
 */
// 类注释：SparkRuntime 是 Calcite 的 Spark 适配器的运行时工具类
// 作用：提供在 Calcite 和 Apache Spark 之间转换数据的实用方法，生成的代码会调用这些方法
// 主要功能：
// 1. 将数组或 Enumerable 转换为 Spark RDD
// 2. 将 Spark RDD 转换为 Enumerable
// 3. 获取当前执行的 Spark 上下文
// 4. 提供结合 Calcite LINQ4J 和 Spark FlatMapFunction 的抽象类
// 这是一个抽象类，不能直接实例化，所有方法都是静态方法
public abstract class SparkRuntime { // 定义抽象类 SparkRuntime，提供 Spark 适配器的运行时工具方法
  private SparkRuntime() {} // 私有构造方法，防止实例化该类，因为这是一个纯工具类，所有方法都是静态的

  /** Converts an array into an RDD. */
  // 方法注释：将数组转换为 Spark RDD（弹性分布式数据集）
  // 参数说明：
  //   - sc: JavaSparkContext 对象，Spark 上下文，用于创建 RDD
  //   - ts: 泛型数组，包含要转换为 RDD 的元素
  // 返回值：JavaRDD<T>，包含数组中所有元素的 RDD
  // 实现细节：
  //   1. 使用 Arrays.asList 将数组转换为 List
  //   2. 使用 sc.parallelize 将 List 并行化为 RDD
  // 这个方法允许将内存中的数组数据分布到 Spark 集群上进行并行处理
  public static <T> JavaRDD<T> createRdd(JavaSparkContext sc, T[] ts) { // 定义静态泛型方法，将数组转换为 RDD
    final List<T> list = Arrays.asList(ts); // 将输入数组转换为 List 集合，使用 final 确保不可重新赋值
    return sc.parallelize(list); // 调用 SparkContext 的 parallelize 方法，将 List 转换为并行处理的 RDD 并返回
  }

  /** Converts an enumerable into an RDD. */
  // 方法注释：将 Calcite 的 Enumerable 转换为 Spark RDD
  // 参数说明：
  //   - sc: JavaSparkContext 对象，Spark 上下文，用于创建 RDD
  //   - enumerable: Enumerable<T> 对象，Calcite LINQ4J 框架中的可枚举集合
  // 返回值：JavaRDD<T>，包含 Enumerable 中所有元素的 RDD
  // 实现细节：
  //   1. 调用 enumerable.toList() 将 Enumerable 转换为内存中的 List
  //   2. 使用 sc.parallelize 将 List 并行化为 RDD
  // 这个方法用于在 Calcite 和 Spark 之间进行数据转换，允许 Calcite 的查询结果在 Spark 中进行处理
  public static <T> JavaRDD<T> createRdd(JavaSparkContext sc,
      Enumerable<T> enumerable) { // 定义重载的静态泛型方法，将 Enumerable 转换为 RDD
    final List<T> list = enumerable.toList(); // 将 Enumerable 转换为 List，这可能触发数据求值
    return sc.parallelize(list); // 将 List 并行化为 RDD 并返回
  }

  /** Converts an RDD into an enumerable. */
  // 方法注释：将 Spark RDD 转换为 Calcite 的 Enumerable
  // 参数说明：
  //   - rdd: JavaRDD<T> 对象，Spark 的弹性分布式数据集
  // 返回值：Enumerable<T>，包含 RDD 中所有元素的 Enumerable
  // 实现细节：
  //   1. 调用 rdd.collect() 触发 RDD 的计算，将所有数据收集到 Driver 端的数组中
  //   2. 使用 Linq4j.asEnumerable 将数组转换为 Enumerable
  // 注意：collect() 操作会将所有数据拉取到 Driver 端，对于大数据集可能导致内存溢出
  // 这个方法用于将 Spark 的处理结果返回给 Calcite 继续处理
  public static <T> Enumerable<T> asEnumerable(JavaRDD<T> rdd) { // 定义静态泛型方法，将 RDD 转换为 Enumerable
    return Linq4j.asEnumerable(rdd.collect()); // 调用 RDD 的 collect 方法收集所有数据，然后转换为 Enumerable 返回
  }

  /** Returns the Spark context for the current execution.
   *
   * <p>Currently a global variable; maybe later held within {@code root}.
   */
  // 方法注释：获取当前执行的 Spark 上下文
  // 参数说明：
  //   - root: DataContext 对象，Calcite 的数据上下文，包含执行环境的各种信息
  // 返回值：JavaSparkContext，当前执行的 Spark 上下文
  // 实现细节：
  //   1. 调用 SparkHandlerImpl.instance() 获取 Spark 处理器的单例实例
  //   2. 调用 sparkContext() 方法获取 Spark 上下文
  //   3. 将返回的上下文强制转换为 JavaSparkContext
  // 注意：目前 Spark 上下文作为全局变量存储，未来可能会存储在 DataContext 中
  // 这个方法允许生成的代码访问 Spark 执行环境
  public static JavaSparkContext getSparkContext(DataContext root) { // 定义静态方法，获取 Spark 上下文
    return (JavaSparkContext) SparkHandlerImpl.instance().sparkContext(); // 获取 Spark 处理器单例的 Spark 上下文并返回
  }

  /** Combines linq4j {@link org.apache.calcite.linq4j.function.Function}
   * and Spark {@link org.apache.spark.api.java.function.FlatMapFunction}.
   *
   * @param <T> argument type
   * @param <R> result type */
  // 类注释：CalciteFlatMapFunction 是一个抽象类，结合了 Calcite LINQ4J 的 Function 和 Spark 的 FlatMapFunction
  // 作用：作为适配器，使同一个函数可以在 Calcite LINQ4J 和 Spark 两种框架中使用
  // 实现细节：
  //   1. 实现 Spark 的 FlatMapFunction 接口，允许在 Spark 中使用
  //   2. 实现 Calcite LINQ4J 的 Function 接口，允许在 Calcite 中使用
  // 泛型参数：
  //   - T: 输入参数类型，表示函数的输入元素类型
  //   - R: 结果类型，表示函数的输出元素类型
  // 使用场景：当生成的代码需要在 Spark 和 Calcite 两种环境中执行时，可以使用这个类
  // 这是一个抽象静态内部类，不能直接实例化，需要子类实现具体逻辑
  public abstract static class CalciteFlatMapFunction<T, R> // 定义抽象静态内部类，结合两种框架的函数接口
      implements FlatMapFunction<T, R>, // 实现 Spark 的 FlatMapFunction 接口，支持一对多的映射操作
      org.apache.calcite.linq4j.function.Function { // 实现 Calcite LINQ4J 的 Function 接口，支持函数式操作
  } // 类结束，没有字段和方法，仅作为接口组合使用
} // 类结束，SparkRuntime 类定义结束
