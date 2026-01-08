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
package org.apache.calcite.rel.metadata; // 包声明：元数据相关的包，包含关系表达式元数据的定义和实现

import org.apache.calcite.rel.RelNode; // 导入关系表达式接口，代表关系代数中的一个节点（如TableScan、Filter、Project等）

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标注可能为null的类型，帮助进行空值检查

/**
 * Source of metadata about relational expressions.
 * 关系表达式元数据的来源接口
 *
 * <p>The metadata is typically various kinds of statistics used to estimate
 * costs.
 * 元数据通常是用于估算成本的各种统计信息，比如行数、选择性、唯一性等，这些信息对于优化器选择最优执行计划至关重要
 *
 * <p>Each kind of metadata has an interface that extends {@link Metadata} and
 * has a method. Some examples: {@link BuiltInMetadata.Selectivity},
 * {@link BuiltInMetadata.ColumnUniqueness}.
 * 每种元数据都有一个继承自Metadata接口的接口，并且包含相关的方法。例如：
 * - Selectivity（选择性）：表示谓词条件的过滤比例，范围在0到1之间，1表示保留100%的行，0表示保留0%的行
 * - ColumnUniqueness（列唯一性）：表示某列的值是否唯一，对于估算连接操作的成本很重要
 * - RowCount（行数）：表示关系表达式处理后预计产生的行数
 * 这些元数据通过RelMetadataQuery进行查询，查询结果会被缓存以提高性能
 */
public interface MetadataFactory { // 定义元数据工厂接口，负责创建和提供特定关系表达式的元数据对象
  /** Returns a metadata interface to get a particular kind of metadata
   * from a particular relational expression. Returns null if that kind of
   * metadata is not available.
   * 返回一个元数据接口，用于从特定的关系表达式中获取特定类型的元数据。如果该类型的元数据不可用，则返回null。
   *
   * @param <M> Metadata type
   * 泛型参数M表示元数据的类型，必须是Metadata接口或其子接口的实例
   *
   * @param rel Relational expression
   * rel参数表示要查询元数据的关系表达式节点，比如TableScan、Filter、Project等
   *
   * @param mq Metadata query
   * mq参数表示元数据查询对象，它提供了查询元数据的统一入口，并且维护了元数据的缓存
   *
   * @param metadataClazz Metadata class
   * metadataClazz参数表示要查询的元数据类型的Class对象，比如Selectivity.class、RowCount.class等
   *
   * @return Metadata bound to {@code rel} and {@code query}
   * 返回值：绑定到指定关系表达式和查询对象的元数据实例，如果该类型的元数据不可用则返回null
   * 返回的元数据对象可以调用其特定方法来获取实际的统计信息，例如getSelectivity()、getRowCount()等
   */
  <@Nullable M extends @Nullable Metadata> M query(RelNode rel, RelMetadataQuery mq, // 泛型方法，查询并返回指定类型的元数据对象，M必须是Metadata的子类型，并且可能为null
      Class<M> metadataClazz); // metadataClazz参数：指定要查询的元数据类型的Class对象，用于反射或类型匹配
} // 接口定义结束
