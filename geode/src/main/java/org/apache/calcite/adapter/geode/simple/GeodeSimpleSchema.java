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
package org.apache.calcite.adapter.geode.simple; // Geode简单适配器模式的包路径，包含Geode简单Schema相关的类

import org.apache.calcite.adapter.geode.util.GeodeUtils; // Geode工具类，提供创建客户端缓存、Region等实用方法
import org.apache.calcite.schema.Table; // Calcite表接口，定义了表的基本行为
import org.apache.calcite.schema.impl.AbstractSchema; // Calcite抽象Schema基类，提供了Schema的基本实现

import org.apache.geode.cache.Region; // Geode Region接口，代表Geode中的数据区域，类似于数据库表
import org.apache.geode.cache.client.ClientCache; // Geode客户端缓存接口，用于连接Geode集群

import com.google.common.collect.ImmutableMap; // Google Guava提供的不可变Map构建器，用于创建线程安全的不可变Map

import java.util.Map; // Java标准Map接口

import static org.apache.calcite.adapter.geode.util.GeodeUtils.autodetectRelTypeFromRegion; // 静态导入，从Region自动检测关系类型的方法

/**
 * Geode Simple Schema. // Geode简单Schema类，用于将Geode Region映射为Calcite可查询的表
 * 
 * 这个类是Calcite适配器模式中的Schema实现，负责将Apache Geode的Region（数据区域）
 * 映射为Calcite可以查询的Table对象。它继承自AbstractSchema，实现了Calcite的Schema接口，
 * 使得Calcite可以通过SQL查询Geode中的数据。
 * 
 * 主要功能：
 * 1. 管理Geode客户端缓存连接
 * 2. 将指定的Geode Region映射为Calcite Table
 * 3. 提供延迟加载的表映射机制（通过getTableMap方法）
 * 4. 支持自动检测Region的关系类型（行类型）
 * 
 * 使用场景：
 * 当需要在Calcite中查询Geode数据存储时，通过创建GeodeSimpleSchema实例，
 * 将Geode的Region暴露为SQL可查询的表。
 */
public class GeodeSimpleSchema extends AbstractSchema { // 继承AbstractSchema，实现Calcite的Schema接口

  @SuppressWarnings("unused") // 抑制未使用警告，虽然regionNames在构造函数中使用，但后续可能通过反射访问
  private final String[] regionNames; // 存储要映射为表的Geode Region名称数组，每个Region名称对应一个表名
  @SuppressWarnings("unused") // 抑制未使用警告，clientCache在创建Region时使用
  private final ClientCache clientCache; // Geode客户端缓存实例，用于连接Geode集群并访问Region数据
  private ImmutableMap<String, Table> tableMap; // 不可变的表映射Map，键为Region名称（表名），值为对应的Table对象，使用延迟加载模式

  // 构造方法：创建GeodeSimpleSchema实例并初始化Geode客户端连接
  // 参数说明：
  // - locatorHost: Geode定位器主机地址，用于连接Geode集群
  // - locatorPort: Geode定位器端口号，用于连接Geode集群
  // - regionNames: 要映射为表的Region名称数组
  // - pdxAutoSerializerPackageExp: PDX自动序列化包表达式，用于指定哪些包的类需要自动序列化
  public GeodeSimpleSchema(String locatorHost, int locatorPort, // 构造方法接收定位器主机和端口
      String[] regionNames, String pdxAutoSerializerPackageExp) { // 接收Region名称数组和PDX序列化包表达式
    super(); // 调用父类AbstractSchema的构造方法进行初始化
    this.regionNames = regionNames; // 保存Region名称数组，后续用于创建表映射
    this.clientCache = // 创建并初始化Geode客户端缓存，用于连接Geode集群
        GeodeUtils.createClientCache(locatorHost, locatorPort, // 调用工具方法创建客户端缓存，传入定位器主机和端口
            pdxAutoSerializerPackageExp, true); // 传入PDX序列化包表达式和true表示启用PDX自动序列化
  }

  // 重写父类方法：获取Schema中所有表的映射关系（延迟加载）
  // 返回值：Map<String, Table>，键为表名（Region名称），值为对应的Table对象
  // 实现原理：使用延迟加载模式，只在第一次调用时创建表映射，后续直接返回缓存的映射
  @Override protected Map<String, Table> getTableMap() { // 重写AbstractSchema的getTableMap方法，返回表映射

    if (tableMap == null) { // 检查表映射是否已创建，实现延迟加载
      final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建不可变Map的构建器，用于线程安全地构建表映射

      for (String regionName : regionNames) { // 遍历所有Region名称，为每个Region创建对应的Table

        Region region = GeodeUtils.createRegion(clientCache, regionName); // 根据Region名称从客户端缓存中获取或创建Region对象

        Table table = // 创建Geode简单可扫描表对象，将Region封装为Calcite可查询的Table
            new GeodeSimpleScannableTable(regionName, // 传入Region名称作为表名
                autodetectRelTypeFromRegion(region), // 自动从Region中检测关系类型（行类型），确定表的列结构
                clientCache); // 传入客户端缓存，用于访问Region数据

        builder.put(regionName, table); // 将Region名称和对应的Table对象添加到Map构建器中
      }

      tableMap = builder.build(); // 构建不可变的表映射Map并缓存，避免重复创建
    }
    return tableMap; // 返回表映射Map，供Calcite查询引擎使用
  }
}
