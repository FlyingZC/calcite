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
package org.apache.calcite.materialize;  // 声明包名，该类属于org.apache.calcite.materialize包，提供物化视图相关功能

import org.apache.calcite.DataContexts;  // 导入DataContexts工具类，用于创建数据上下文对象
import org.apache.calcite.linq4j.Enumerable;  // 导入Enumerable接口，提供LINQ风格的集合操作
import org.apache.calcite.profile.Profiler;  // 导入Profiler接口，用于分析表数据的统计信息
import org.apache.calcite.profile.ProfilerImpl;  // 导入ProfilerImpl实现类，Profiler的具体实现
import org.apache.calcite.rel.metadata.NullSentinel;  // 导入NullSentinel类，用于表示NULL值的特殊标记
import org.apache.calcite.schema.ScannableTable;  // 导入ScannableTable接口，表示可扫描的表
import org.apache.calcite.schema.Table;  // 导入Table接口，表示数据库表
import org.apache.calcite.schema.impl.MaterializedViewTable;  // 导入MaterializedViewTable类，物化视图表实现
import org.apache.calcite.util.ImmutableBitSet;  // 导入ImmutableBitSet类，不可变的位集合，用于高效表示列集合

import com.google.common.base.Suppliers;  // 导入Guava的Suppliers工具类，提供延迟计算和缓存功能
import com.google.common.collect.ImmutableList;  // 导入Guava的ImmutableList类，不可变的列表

import java.util.ArrayList;  // 导入ArrayList类，动态数组实现
import java.util.Arrays;  // 导入Arrays工具类，提供数组操作方法
import java.util.List;  // 导入List接口，有序集合
import java.util.function.Supplier;  // 导入Supplier函数式接口，提供延迟计算功能

import static java.util.Objects.requireNonNull;  // 导入Objects的requireNonNull方法，用于参数非空校验

/**
 * Implementation of {@link LatticeStatisticProvider} that uses a
 * {@link org.apache.calcite.profile.Profiler}.
 */  // 类文档注释：这是LatticeStatisticProvider接口的实现类，使用Profiler来提供Lattice的统计信息
class ProfilerLatticeStatisticProvider implements LatticeStatisticProvider {  // 定义类ProfilerLatticeStatisticProvider，实现LatticeStatisticProvider接口
  static final Factory FACTORY = ProfilerLatticeStatisticProvider::new;  // 静态工厂常量，使用方法引用创建Provider实例的工厂

  private final Supplier<Profiler.Profile> profile;  // 成员变量：延迟加载的Profiler.Profile对象，Supplier确保只计算一次并缓存结果

  /** Creates a ProfilerLatticeStatisticProvider. */  // 构造方法文档注释：创建ProfilerLatticeStatisticProvider实例
  private ProfilerLatticeStatisticProvider(Lattice lattice) {  // 私有构造方法，接收Lattice对象作为参数，通过工厂创建实例
    requireNonNull(lattice, "lattice");  // 校验lattice参数不为null，否则抛出NullPointerException
    this.profile = Suppliers.memoize(() -> {  // 使用Guava的Suppliers.memoize创建延迟计算并缓存的Supplier，lambda表达式定义如何创建Profile
      final ProfilerImpl profiler =  // 创建ProfilerImpl实例，用于分析数据统计信息
          ProfilerImpl.builder()  // 使用Builder模式创建ProfilerImpl
              .withPassSize(200)  // 设置每次扫描的行数为200，控制分析粒度
              .withMinimumSurprise(0.3D)  // 设置最小惊喜度为0.3，用于控制统计信息的质量阈值
              .build();  // 构建ProfilerImpl实例
      final List<Profiler.Column> columns = new ArrayList<>();  // 创建ArrayList用于存储Profiler的列定义
      for (Lattice.Column column : lattice.columns) {  // 遍历Lattice的所有列
        columns.add(new Profiler.Column(column.ordinal, column.alias));  // 为每列创建Profiler.Column对象，包含列序号和别名
      }
      final String sql =  // 生成SQL查询语句，用于获取Lattice的所有列数据
          lattice.sql(ImmutableBitSet.range(lattice.columns.size()),  // 创建包含所有列的位集合
              false,  // 不使用星号展开
              ImmutableList.of());  // 不添加额外的where条件
      final Table table =  // 创建Table对象，用于执行SQL查询
          new MaterializationService.DefaultTableFactory()  // 创建默认的表工厂
              .createTable(lattice.rootSchema, sql, ImmutableList.of());  // 在rootSchema中创建表，执行SQL查询
      final ImmutableList<ImmutableBitSet> initialGroups =  // 创建初始分组列表，空列表表示不预设分组
          ImmutableList.of();  // 返回空的不可变列表
      final Enumerable<List<Comparable>> rows =  // 获取可枚举的行数据，每行是一个Comparable对象的列表
          ((ScannableTable) table).scan(  // 将table转换为ScannableTable并扫描数据
              DataContexts.of(MaterializedViewTable.MATERIALIZATION_CONNECTION,  // 创建数据上下文，使用物化视图连接
                  lattice.rootSchema.plus()))  // 添加rootSchema到上下文
              .select(values -> {  // 使用select转换每一行数据
                for (int i = 0; i < values.length; i++) {  // 遍历行中的每个值
                  if (values[i] == null) {  // 如果值为null
                    values[i] = NullSentinel.INSTANCE;  // 替换为NullSentinel实例，用于特殊处理NULL
                  }
                }
                //noinspection unchecked  // 抑制未检查的类型转换警告
                return (List<Comparable>) (List) Arrays.asList(values);  // 将数组转换为List<Comparable>类型
              });
      return profiler.profile(rows, columns, initialGroups);  // 使用profiler分析行数据、列定义和初始分组，返回Profile对象
    });  // memoize方法结束，返回Supplier对象
  }  // 构造方法结束

  @Override public double cardinality(List<Lattice.Column> columns) {  // 重写接口方法，计算给定列集合的基数（唯一值数量）
    final ImmutableBitSet build = Lattice.Column.toBitSet(columns);  // 将Lattice.Column列表转换为ImmutableBitSet，用于高效表示列集合
    final double cardinality = profile.get().cardinality(build);  // 从缓存的Profile中获取指定列集合的基数估计值
//    System.out.println(columns + ": " + cardinality);  // 注释掉的调试输出，打印列集合和对应的基数
    return cardinality;  // 返回计算得到的基数
  }  // 方法结束
}  // 类结束
