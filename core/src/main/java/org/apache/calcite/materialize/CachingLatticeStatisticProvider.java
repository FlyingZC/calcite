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
package org.apache.calcite.materialize; // 声明包名，该类属于org.apache.calcite.materialize包，用于Calcite的物化视图相关功能

import org.apache.calcite.util.Util; // 导入Calcite工具类，提供异常处理等通用方法

import com.google.common.cache.CacheBuilder; // 导入Google Guava的缓存构建器，用于创建缓存对象
import com.google.common.cache.CacheLoader; // 导入Google Guava的缓存加载器，用于定义缓存值的加载逻辑
import com.google.common.cache.LoadingCache; // 导入Google Guava的加载缓存接口，支持自动加载缓存值
import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可修改的列表
import com.google.common.util.concurrent.UncheckedExecutionException; // 导入Google Guava的未检查执行异常类，用于处理缓存加载时的异常

import java.util.ArrayList; // 导入Java的ArrayList类，用于动态数组列表
import java.util.List; // 导入Java的List接口，表示有序集合
import java.util.concurrent.ExecutionException; // 导入Java的执行异常类，用于处理并发执行时的异常

/**
 * Implementation of {@link LatticeStatisticProvider} that caches single-column // 实现LatticeStatisticProvider接口，该实现缓存单列统计信息
 * statistics and computes multi-column statistics from these. // 并基于这些单列统计信息计算多列统计信息
 * // 这个类的主要作用是提高Lattice统计信息查询的性能，通过缓存单列的基数（cardinality）来避免重复计算
 * // 当需要多列的基数时，它会利用缓存的单列基数进行估算，从而提高查询优化器的效率
 */
class CachingLatticeStatisticProvider implements LatticeStatisticProvider { // 定义CachingLatticeStatisticProvider类，实现LatticeStatisticProvider接口
  private final Lattice lattice; // 成员变量：Lattice对象，表示当前正在处理的Lattice（数据立方体结构），用于存储事实表和维度表的关系
  private final LoadingCache<Lattice.Column, Double> cache; // 成员变量：加载缓存，键是Lattice的列（Lattice.Column），值是该列的基数（Double类型），用于缓存单列的统计信息以提高性能

  /** Creates a CachingStatisticProvider. */ // 创建一个CachingStatisticProvider实例的构造方法注释
  CachingLatticeStatisticProvider(final Lattice lattice, // 构造方法：接收一个Lattice对象作为参数，表示要统计的Lattice结构
      final LatticeStatisticProvider provider) { // 构造方法：接收一个LatticeStatisticProvider对象作为参数，这是底层的统计信息提供者，用于实际计算列的基数
    this.lattice = lattice; // 将传入的Lattice对象赋值给成员变量lattice，保存Lattice引用以便后续使用
    cache = // 初始化缓存对象，使用Google Guava的CacheBuilder来构建
        CacheBuilder.newBuilder().build( // 创建一个新的CacheBuilder并构建缓存对象
            CacheLoader.from(key -> // 创建一个CacheLoader，定义当缓存中不存在某个键时如何加载对应的值
                provider.cardinality(ImmutableList.of(key)))); // 当需要加载某个列的基数时，调用底层provider的cardinality方法，将该列包装成不可变列表传入，获取该列的基数并缓存
  } // 构造方法结束

  @Override public double cardinality(List<Lattice.Column> columns) { // 重写接口方法：计算给定列列表的基数（唯一值数量），返回double类型的基数估计值
    final List<Double> counts = new ArrayList<>(); // 创建一个Double类型的列表，用于存储每一列的基数
    for (Lattice.Column column : columns) { // 遍历传入的列列表，逐个处理每一列
      try { // 尝试从缓存中获取列的基数
        counts.add(cache.get(column)); // 从缓存中获取当前列的基数，如果缓存中没有，会自动调用CacheLoader加载并缓存
      } catch (UncheckedExecutionException | ExecutionException e) { // 捕获缓存加载时可能出现的异常（未检查执行异常或执行异常）
        throw Util.throwAsRuntime(Util.causeOrSelf(e)); // 将捕获的异常转换为运行时异常抛出，Util.causeOrSelf方法会获取异常的根本原因
      } // 异常处理结束
    } // 循环结束，已经获取了所有列的基数
    return (int) Lattice.getRowCount(lattice.getFactRowCount(), counts); // 调用Lattice的静态方法getRowCount，根据事实表的总行数和各列的基数列表，计算这些列组合后的基数估计值，并转换为int返回
  } // 方法结束
} // 类定义结束
