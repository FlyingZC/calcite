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
package org.apache.calcite.materialize; // 声明包名，org.apache.calcite.materialize 包包含了与物化视图和格（Lattice）相关的类

/**
 * Utilities for {@link Lattice}, {@link LatticeStatisticProvider}.
 * 这是 Lattice（格）和 LatticeStatisticProvider（格统计提供者）的工具类
 * 
 * 类作用说明：
 * Lattices 是一个工具类，提供了创建不同类型 LatticeStatisticProvider 的工厂方法
 * Lattice（格）是 Calcite 中用于表示多维数据结构的概念，常用于物化视图和 OLAP 场景
 * LatticeStatisticProvider 是一个接口，用于提供格的统计信息，例如行数、基数等
 * 
 * 这个类主要提供了三种统计提供者工厂：
 * 1. SQL：直接使用 SQL 查询获取统计信息
 * 2. CACHED_SQL：使用 SQL 查询获取统计信息，并将结果缓存起来
 * 3. PROFILER：使用性能分析器（profiler）获取统计信息
 * 
 * 使用场景：
 * 在创建物化视图或优化查询时，需要了解数据的统计信息以做出更好的决策
 * 不同的统计提供者适用于不同的场景，例如 SQL 适用于实时查询，CACHED_SQL 适用于频繁查询相同统计信息的场景
 */
public class Lattices { // 定义 Lattices 工具类，这是一个公共类，可以被其他类访问和使用
  private Lattices() {} // 私有构造方法，防止实例化，因为这是一个工具类，所有成员都是静态的，不需要创建实例

  /** Statistics provider that uses SQL. */
  // 定义一个公共静态常量 SQL，类型为 LatticeStatisticProvider.Factory
  // 这是一个工厂对象，用于创建使用 SQL 查询获取统计信息的 LatticeStatisticProvider 实例
  // SQL 统计提供者会直接执行 SQL 查询来获取格的统计信息，例如行数、列的基数等
  // 这种方式的优点是获取的统计信息是实时的，缺点是每次都需要执行查询，可能会有性能开销
  public static final LatticeStatisticProvider.Factory SQL =
      SqlLatticeStatisticProvider.FACTORY; // 将 SQL 常量初始化为 SqlLatticeStatisticProvider 的 FACTORY 静态字段，这是一个工厂实例

  /** Statistics provider that uses SQL then stores the results in a cache. */
  // 定义一个公共静态常量 CACHED_SQL，类型为 LatticeStatisticProvider.Factory
  // 这是一个工厂对象，用于创建使用 SQL 查询获取统计信息并缓存的 LatticeStatisticProvider 实例
  // CACHED_SQL 统计提供者会先执行 SQL 查询获取统计信息，然后将结果缓存起来
  // 后续查询相同的统计信息时，直接从缓存中读取，避免重复执行 SQL 查询
  // 这种方式的优点是减少了重复查询的开销，提高了性能，缺点是统计信息可能不是最新的
  public static final LatticeStatisticProvider.Factory CACHED_SQL =
      SqlLatticeStatisticProvider.CACHED_FACTORY; // 将 CACHED_SQL 常量初始化为 SqlLatticeStatisticProvider 的 CACHED_FACTORY 静态字段，这是一个带缓存的工厂实例

  /** Statistics provider that uses a profiler. */
  // 定义一个公共静态常量 PROFILER，类型为 LatticeStatisticProvider.Factory
  // 这是一个工厂对象，用于创建使用性能分析器（profiler）获取统计信息的 LatticeStatisticProvider 实例
  // PROFILER 统计提供者通过分析数据分布和查询模式来获取统计信息
  // 这种方式可能比直接执行 SQL 查询更高效，特别是在处理大量数据时
  // 性能分析器可以智能地选择采样策略，以最小的代价获取尽可能准确的统计信息
  public static final LatticeStatisticProvider.Factory PROFILER =
      ProfilerLatticeStatisticProvider.FACTORY; // 将 PROFILER 常量初始化为 ProfilerLatticeStatisticProvider 的 FACTORY 静态字段，这是一个基于性能分析器的工厂实例
}
