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
package org.apache.calcite.adapter.geode.rel; // 声明包名，该类位于 org.apache.calcite.adapter.geode.rel 包下

import org.apache.calcite.adapter.geode.util.GeodeUtils; // 导入 GeodeUtils 工具类，用于创建 Geode Region
import org.apache.calcite.schema.Table; // 导入 Table 接口，Calcite 中表的抽象表示
import org.apache.calcite.schema.impl.AbstractSchema; // 导入 AbstractSchema 抽象类，Calcite Schema 的基类

import org.apache.geode.cache.GemFireCache; // 导入 GemFireCache 接口，代表 Apache Geode 缓存实例
import org.apache.geode.cache.Region; // 导入 Region 接口，代表 Geode 中的数据区域（类似于数据库表）

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList，用于创建不可变列表
import com.google.common.collect.ImmutableMap; // 导入 Google Guava 的 ImmutableMap，用于创建不可变映射

import java.util.List; // 导入 Java 标准库的 List 接口
import java.util.Map; // 导入 Java 标准库的 Map 接口

import static java.util.Objects.requireNonNull; // 导入 Objects.requireNonNull 静态方法，用于参数非空校验

/**
 * Schema mapped onto a Geode Region. // 类说明：这是一个映射到 Geode Region 的 Schema 类
 * GeodeSchema 是 Calcite 框架中用于将 Apache Geode 分布式缓存中的 Region 映射为关系型数据库表结构的 Schema 实现
 * 它继承自 AbstractSchema，是 Calcite 适配器模式的一部分，允许 Calcite 查询引擎查询 Geode 中的数据
 * 每个 GeodeSchema 实例可以包含多个 Region，每个 Region 被映射为一个 Table 对象
 * 这样就可以使用标准 SQL 查询 Geode 中的数据，Calcite 会将 SQL 查询转换为对 Geode Region 的操作
 */
public class GeodeSchema extends AbstractSchema { // 定义 GeodeSchema 类，继承自 AbstractSchema 抽象类

  final GemFireCache cache; // 成员变量：GemFireCache 实例，代表 Apache Geode 缓存连接，final 表示引用不可变，用于访问 Geode 缓存中的 Region
  private final List<String> regionNames; // 成员变量：Region 名称列表，private final 表示私有且不可变，存储需要映射为表的 Geode Region 名称
  private ImmutableMap<String, Table> tableMap; // 成员变量：不可变映射表，将 Region 名称映射到对应的 Table 对象，使用延迟初始化（lazy initialization），在第一次调用 getTableMap() 时构建

  public GeodeSchema(final GemFireCache gemFireCache, final Iterable<String> regionNames) { // 构造方法：创建 GeodeSchema 实例，参数 gemFireCache 是 Geode 缓存连接，regionNames 是要映射的 Region 名称集合
    super(); // 调用父类 AbstractSchema 的构造方法，完成父类的初始化
    this.cache = requireNonNull(gemFireCache, "gemFireCache"); // 使用 requireNonNull 校验 gemFireCache 参数不为空，如果为空则抛出 NullPointerException，错误信息为 "gemFireCache"，然后将参数赋值给成员变量 cache
    this.regionNames = ImmutableList.copyOf(regionNames); // 使用 ImmutableList.copyOf 将 regionNames 转换为不可变列表并赋值给成员变量，确保 regionNames 在构造后不可修改
  }

  @Override protected Map<String, Table> getTableMap() { // 重写父类 AbstractSchema 的 getTableMap() 方法，返回该 Schema 中所有表的映射关系，protected 表示只能被子类或同包访问
    // 该方法采用延迟初始化模式（lazy initialization），只在第一次调用时构建 tableMap，后续调用直接返回缓存的结果
    // 这种设计可以提高性能，避免在不需要表信息时创建不必要的对象

    if (tableMap == null) { // 检查 tableMap 是否为 null，如果是 null 则需要初始化构建，如果不是 null 则直接跳过构建过程

      final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建 ImmutableMap.Builder 对象，用于构建不可变的 Map，final 表示 builder 引用不可变

      for (String regionName : regionNames) { // 遍历 regionNames 列表中的每个 Region 名称
        Region region = GeodeUtils.createRegion(cache, regionName); // 调用 GeodeUtils.createRegion 静态方法，使用 cache 和 regionName 创建或获取对应的 Geode Region 对象
        Table table = new GeodeTable(region); // 创建 GeodeTable 实例，将 Geode Region 包装成 Calcite 的 Table 对象，这样 Calcite 就可以将 Region 当作关系表来处理
        builder.put(regionName, table); // 将 regionName 和对应的 table 对象放入 builder 中，构建键值对映射
      }

      tableMap = builder.build(); // 调用 builder.build() 方法构建不可变的 ImmutableMap 并赋值给 tableMap，此时 tableMap 包含了所有 Region 名称到 Table 对象的映射
    }

    return tableMap; // 返回已构建的 tableMap，如果之前未构建则返回新构建的，如果已构建则返回缓存的，该 Map 包含该 Schema 下所有可查询的表
  }
}
