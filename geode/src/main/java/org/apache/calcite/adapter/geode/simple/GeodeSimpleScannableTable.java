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
// 声明包名，表示该类属于 org.apache.calcite.adapter.geode.simple 包，这是 Calcite Geode 适配器中简单扫描表实现的包
package org.apache.calcite.adapter.geode.simple;

// 导入 Calcite 的 DataContext 类，用于在查询执行过程中传递上下文信息，包含会话变量、统计信息等
import org.apache.calcite.DataContext;
// 导入 AbstractEnumerable 抽象类，用于创建可枚举的数据集合，支持 LINQ 风格的数据遍历
import org.apache.calcite.linq4j.AbstractEnumerable;
// 导入 Enumerable 接口，表示可枚举的数据集合，是 LINQ4J 的核心接口之一
import org.apache.calcite.linq4j.Enumerable;
// 导入 Enumerator 接口，用于逐个遍历数据集合中的元素，类似于 Java 的 Iterator
import org.apache.calcite.linq4j.Enumerator;
// 导入 RelDataType 类，表示关系数据类型，描述表的结构（列名、列类型等）
import org.apache.calcite.rel.type.RelDataType;
// 导入 RelDataTypeFactory 接口，用于创建关系数据类型的工厂接口
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入 ScannableTable 接口，表示可扫描的表，支持全表扫描操作
import org.apache.calcite.schema.ScannableTable;
// 导入 AbstractTable 抽象类，作为 Calcite 表的基础实现类
import org.apache.calcite.schema.impl.AbstractTable;

// 导入 Geode 的 ClientCache 类，表示 Geode 客户端缓存，用于与 Geode 集群进行交互
import org.apache.geode.cache.client.ClientCache;

// 导入 Nullable 注解，用于标记可能为 null 的返回值，帮助进行静态类型检查
import org.checkerframework.checker.nullness.qual.Nullable;

// 静态导入 GeodeUtils 工具类的 convertToRowValues 方法，用于将 Geode 对象转换为行值数组
import static org.apache.calcite.adapter.geode.util.GeodeUtils.convertToRowValues;

/**
 * Geode Simple Scannable Table abstraction.
 * Geode 简单可扫描表抽象类
 * 
 * 这个类实现了 ScannableTable 接口，用于将 Apache Geode 的 Region（区域）映射为 Calcite 中可扫描的表
 * 它是 Calcite Geode 适配器的核心组件之一，负责：
 * 1. 将 Geode Region 包装为 Calcite 表
 * 2. 提供表的元数据（行类型）
 * 3. 实现全表扫描功能，返回可枚举的数据集合
 * 
 * 该类采用简单模式，不支持复杂的过滤和投影下推，所有数据都会从 Geode Region 中完全扫描出来
 */
// 定义 GeodeSimpleScannableTable 类，继承 AbstractTable 并实现 ScannableTable 接口
// AbstractTable 提供基础的表实现，ScannableTable 定义了扫描表的能力
public class GeodeSimpleScannableTable extends AbstractTable implements ScannableTable {

  // relDataType 成员变量：存储表的关系数据类型，描述表的行结构（包含所有字段的名称和类型）
  // 使用 final 修饰表示一旦初始化就不能改变，确保表结构的稳定性
  private final RelDataType relDataType;
  // regionName 成员变量：存储 Geode Region 的名称，用于标识要访问的 Geode 数据区域
  // Region 是 Geode 中数据分布和管理的逻辑单元，类似于数据库中的表
  private final String regionName;
  // clientCache 成员变量：存储 Geode 客户端缓存实例，用于与 Geode 集群进行通信
  // 通过 ClientCache 可以访问 Geode 中的 Region 数据
  private final ClientCache clientCache;

  // 构造方法：创建 GeodeSimpleScannableTable 实例
  // 参数 regionName：Geode Region 的名称，指定要扫描的 Region
  // 参数 relDataType：表的关系数据类型，描述表的行结构（字段列表和类型）
  // 参数 clientCache：Geode 客户端缓存，用于访问 Geode 集群
  public GeodeSimpleScannableTable(String regionName, RelDataType relDataType,
      ClientCache clientCache) {
    // 调用父类 AbstractTable 的构造方法，进行基础初始化
    super();

    // 将传入的 regionName 参数赋值给成员变量，保存 Region 名称
    this.regionName = regionName;
    // 将传入的 clientCache 参数赋值给成员变量，保存客户端缓存引用
    this.clientCache = clientCache;
    // 将传入的 relDataType 参数赋值给成员变量，保存表的结构信息
    this.relDataType = relDataType;
  }

  // 重写 toString 方法，返回表的字符串表示
  // @Override 注解表示这是重写父类的方法
  @Override public String toString() {
    // 返回固定的字符串 "GeodeSimpleScannableTable"，用于标识这个表的类型
    return "GeodeSimpleScannableTable";
  }

  // 实现 ScannableTable 接口的 getRowType 方法，返回表的行类型
  // 参数 typeFactory：关系数据类型工厂，用于创建新的数据类型（本方法未使用）
  // 返回值：表的行类型，包含所有字段的名称和类型信息
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    // 直接返回预先构造好的 relDataType，描述表的行结构
    return relDataType;
  }

  // 实现 ScannableTable 接口的 scan 方法，执行全表扫描并返回可枚举的数据集合
  // 参数 root：数据上下文，包含查询执行时的环境信息（如会话变量、统计信息等）
  // 返回值：可枚举的对象数组集合，每个数组代表一行数据
  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) {
    // 创建并返回一个 AbstractEnumerable 匿名子类实例
    // AbstractEnumerable 是 LINQ4J 中可枚举集合的抽象基类
    // 泛型参数 @Nullable Object[] 表示可枚举的元素是可能为 null 的对象数组（每行数据）
    return new AbstractEnumerable<@Nullable Object[]>() {
      // 实现 enumerator 方法，创建并返回一个枚举器（Enumerator）
      // Enumerator 用于逐个遍历数据集合中的元素
      @Override public Enumerator<@Nullable Object[]> enumerator() {
        // 创建并返回 GeodeSimpleEnumerator 匿名子类实例
        // GeodeSimpleEnumerator 是专门用于枚举 Geode 数据的枚举器
        // 构造参数 clientCache：Geode 客户端缓存，用于访问数据
        // 构造参数 regionName：Region 名称，指定要扫描的 Region
        return new GeodeSimpleEnumerator<@Nullable Object[]>(clientCache, regionName) {
          // 重写 convert 方法，将 Geode 对象转换为 Calcite 行格式（Object 数组）
          // 参数 obj：从 Geode Region 中获取的原始对象
          // 返回值：转换后的对象数组，表示一行数据，可能为 null
          @Override public @Nullable Object[] convert(Object obj) {
            // 调用 GeodeUtils.convertToRowValues 工具方法，将 Geode 对象转换为行值
            // 参数 relDataType.getFieldList()：获取表的所有字段列表，用于确定如何提取和转换数据
            // 参数 obj：要转换的 Geode 原始对象
            // 返回值：转换后的值，可能是对象数组（多列）或单个值（单列）
            Object values = convertToRowValues(relDataType.getFieldList(), obj);
            // 判断转换后的值是否已经是对象数组（多列情况）
            if (values instanceof Object[]) {
              // 如果是对象数组，直接强制转换并返回
              return (Object[]) values;
            }
            // 如果不是对象数组（单列情况），将其包装为单元素数组返回
            // 这样确保每一行数据都以 Object[] 的形式统一表示
            return new Object[]{values};
          }
        };
      }
    };
  }
}
