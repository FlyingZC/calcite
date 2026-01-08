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
// Apache Calcite 版权许可声明，说明这是一个开源软件，遵循 Apache License 2.0 协议
// 允许用户在符合协议的前提下使用、修改和分发代码
package org.apache.calcite.materialize;  // 声明所属包：org.apache.calcite.materialize，这是 Calcite 框架中负责物化视图功能的包

import org.apache.calcite.jdbc.CalciteSchema;  // 导入 CalciteSchema 类，用于表示 Calcite 的模式（Schema），是数据库对象的容器
import org.apache.calcite.rel.type.RelDataType;  // 导入 RelDataType 类，用于表示关系数据类型，描述数据的结构信息

import com.google.common.collect.HashMultimap;  // 导入 Google Guava 库的 HashMultimap，用于实现多值映射（一个键可以对应多个值）
import com.google.common.collect.Multimap;  // 导入 Multimap 接口，定义多值映射的标准接口

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入注解 @Nullable，用于标记可能为 null 的参数或返回值，帮助进行空值检查

import java.util.HashMap;  // 导入 HashMap 类，用于实现基于哈希表的 Map 集合，提供键值对存储
import java.util.List;  // 导入 List 接口，用于表示有序的元素列表
import java.util.Map;  // 导入 Map 接口，定义键值对映射的标准接口
import java.util.Objects;  // 导入 Objects 工具类，提供对象操作方法，如 equals、hashCode 等

import static com.google.common.base.Preconditions.checkArgument;  // 静态导入 checkArgument 方法，用于参数校验，如果条件不满足则抛出 IllegalArgumentException

import static java.util.Objects.requireNonNull;  // 静态导入 requireNonNull 方法，用于检查对象是否为 null，如果为 null 则抛出 NullPointerException

/**
 * Actor that manages the state of materializations in the system.
 * 物化视图执行器：负责管理系统中所有物化视图的状态和生命周期
 * 
 * 这个类是 Calcite 物化视图机制的核心组件之一，它的主要作用是：
 * 1. 维护系统中所有物化视图的注册信息
 * 2. 提供物化视图的查询和管理功能
 * 3. 支持物化视图的创建、更新和删除操作
 * 4. 管理物化视图与原始查询之间的映射关系
 * 
 * 物化视图（Materialization）是一种预先计算并存储的查询结果，可以显著提升查询性能
 * 当用户执行查询时，如果查询与某个物化视图匹配，可以直接从物化视图读取结果，而无需重新计算
 * 
 * 注意：虽然类名包含 "Actor"（执行器），但目前还不是真正的 Actor 模式实现
 * TODO 注释提示未来可能会将成员变量私有化，并添加请求/响应队列，以实现真正的 Actor 模式
 */
class MaterializationActor {  // 定义 MaterializationActor 类，使用默认访问权限（包私有），只能在同一个包内访问
  // Not an actor yet -- TODO make members private and add request/response
  // queues
  // 注释说明：目前还不是真正的 Actor 模式实现
  // TODO：未来的改进计划包括：
  // 1. 将成员变量改为私有（private），以封装内部状态
  // 2. 添加请求（request）和响应（response）队列，实现异步消息处理机制
  // 3. 这样可以实现真正的 Actor 模式，提供更好的并发控制和消息隔离

  // 成员变量定义区域：使用 final 修饰，表示这些引用在初始化后不能改变，但 Map 内部内容可以修改

  final Map<MaterializationKey, Materialization> keyMap = new HashMap<>();
  // keyMap：物化视图键到物化视图对象的映射表
  // MaterializationKey：物化视图的唯一标识符，用于区分不同的物化视图
  // Materialization：物化视图的具体实现对象，包含物化视图的详细信息
  // HashMap：使用哈希表实现的 Map，提供 O(1) 的查找性能
  // 作用：通过这个映射表，可以根据物化视图的键快速定位到对应的物化视图对象
  // 使用场景：当需要查询、更新或删除某个物化视图时，首先通过 keyMap 找到对应的 Materialization 对象

  final Map<QueryKey, MaterializationKey> keyBySql = new HashMap<>();
  // keyBySql：查询键到物化视图键的映射表
  // QueryKey：查询的标识符，包含 SQL 语句、Schema 和解析路径等信息，用于唯一标识一个查询
  // MaterializationKey：物化视图的唯一标识符
  // 作用：这个映射表用于判断一个 SQL 查询是否已经有对应的物化视图
  // 使用场景：当执行一个查询时，可以通过 QueryKey 查找是否已有物化视图，如果有则可以直接使用物化视图
  // 这是物化视图匹配的关键数据结构，支持基于 SQL 语句的快速查找

  final Map<TileKey, MaterializationKey> keyByTile = new HashMap<>();
  // keyByTile：瓦片键到物化视图键的映射表
  // TileKey：瓦片的标识符，瓦片（Tile）是物化视图的一种切片表示，用于支持部分物化和多维分析
  // MaterializationKey：物化视图的唯一标识符
  // 作用：这个映射表用于管理物化视图的瓦片结构，支持基于瓦片的查询优化
  // 使用场景：在 OLAP（联机分析处理）场景中，可以将物化视图分解为多个瓦片，每个瓦片包含特定维度的数据
  // 通过瓦片键可以快速定位到对应的物化视图，实现更灵活的数据访问

  /** Tiles grouped by dimensionality. We use a
   * {@link TileKey} with no measures to represent a
   * dimensionality. */
  // 注释：按维度分组的瓦片集合。我们使用不包含度量（measures）的 TileKey 来表示一个维度
  // 解释：这个多值映射用于管理瓦片的维度分组关系
  // TileKey（键）：表示维度信息的瓦片键（不包含度量），用于标识一组具有相同维度的瓦片
  // TileKey（值）：具体的瓦片键，表示属于该维度的实际瓦片
  // 作用：支持按维度对瓦片进行分类和查询，便于在多维分析场景中找到相关维度的所有瓦片
  // 使用场景：当查询涉及特定维度时，可以通过这个映射快速找到所有相关的瓦片，提高查询优化效率
  final Multimap<TileKey, TileKey> tilesByDimensionality =
      HashMultimap.create();
  // tilesByDimensionality：按维度分组的瓦片集合
  // Multimap<TileKey, TileKey>：多值映射，一个维度键可以对应多个瓦片键
  // HashMultimap.create()：创建基于哈希表的多值映射实现，提供 O(1) 的插入和查找性能
  // 数据结构示例：
  //   维度键1 -> [瓦片键A, 瓦片键B, 瓦片键C]  // 这三个瓦片属于同一个维度
  //   维度键2 -> [瓦片键D, 瓦片键E]          // 这两个瓦片属于另一个维度
  // 这种结构支持高效的维度查询和瓦片聚合操作

  /** A query materialized in a table, so that reading from the table gives the
   * same results as executing the query. */
  // 注释：物化在表中的查询，从表中读取数据的结果与执行该查询的结果相同
  // 解释：Materialization 是物化视图的核心数据结构，表示一个查询的物化结果
  // 物化视图是一个预先计算并存储的查询结果，通常存储在物理表中
  // 当原始数据变化时，物化视图也需要更新以保持一致性
  // 使用物化视图可以避免重复执行昂贵的查询操作，显著提升查询性能
  static class Materialization {  // 定义静态内部类 Materialization，表示一个物化视图
    // 成员变量：使用 final 修饰，表示这些引用在构造后不能修改

    final MaterializationKey key;  // 物化视图的唯一标识符，用于在系统中唯一标识这个物化视图
    // MaterializationKey：包含物化视图的元数据信息，如表名、模式等
    // 作用：作为物化视图的主键，用于在各种映射表中查找和引用这个物化视图

    final CalciteSchema rootSchema;  // 根模式（Root Schema），表示 Calcite 模式树的根节点
    // CalciteSchema：Calcite 的模式类，包含表、视图、函数等数据库对象
    // rootSchema：必须是根模式（通过构造函数中的 checkArgument 验证）
    // 作用：提供模式解析的上下文，用于解析表名、函数名等标识符
    // 使用场景：在执行 SQL 查询时，需要通过 rootSchema 来定位表和函数

    final CalciteSchema.@Nullable TableEntry materializedTable;  // 物化表的表条目，表示实际存储物化结果的表
    // @Nullable：表示这个字段可能为 null
    // TableEntry：表条目，包含表的引用和元数据信息
    // materializedTable：指向实际存储物化查询结果的表
    // 作用：提供对物化表的访问，执行 "SELECT * FROM table" 可以获得与执行查询相同的结果
    // 注意：在物化视图创建时，这个字段可能为 null，后续由物化服务填充

    final String sql;  // 被物化的 SQL 查询语句
    // String：原始的 SQL 查询文本
    // sql：物化视图所对应的原始查询语句
    // 作用：记录物化视图的来源查询，用于查询匹配和物化视图维护
    // 使用场景：当用户执行查询时，系统会将查询的 SQL 与物化视图的 sql 进行比较，判断是否可以重用物化视图

    final RelDataType rowType;  // 行类型，描述物化视图结果集的数据类型结构
    // RelDataType：关系数据类型，包含列名、数据类型、是否可空等信息
    // rowType：描述物化视图查询结果的每一行的结构
    // 作用：提供类型信息，用于查询优化和类型检查
    // 使用场景：在查询重写时，需要确保物化视图的 rowType 与查询要求的类型兼容

    final @Nullable List<String> viewSchemaPath;  // 视图模式路径，用于解析函数和类型
    // @Nullable：表示这个字段可能为 null
    // List<String>：字符串列表，表示模式路径
    // viewSchemaPath：用于解析视图中的函数和类型的路径列表
    // 作用：提供解析上下文，确保在正确的模式中查找函数和类型
    // 使用场景：当视图中引用了特定模式中的函数时，需要通过这个路径来定位函数

    /** Creates a materialization.
     *
     * @param key  Unique identifier of this materialization
     * @param materializedTable Table that currently materializes the query.
     *                          That is, executing "select * from table" will
     *                          give the same results as executing the query.
     *                          May be null when the materialization is created;
     *                          materialization service will change the value as
     * @param sql  Query that is materialized
     * @param rowType Row type
     */
    // 注释：创建一个物化视图对象
    // 参数说明：
    //   key：物化视图的唯一标识符，用于在系统中区分不同的物化视图
    //   materializedTable：当前物化该查询的表。执行 "SELECT * FROM table" 会得到与执行查询相同的结果。
    //                     在物化视图创建时可能为 null，物化服务会更新这个值
    //   sql：被物化的查询语句
    //   rowType：行类型，描述结果集的数据结构
    Materialization(MaterializationKey key,  // 构造方法：创建 Materialization 对象
        CalciteSchema rootSchema,  // 参数：根模式，必须是根模式（通过 checkArgument 验证）
        CalciteSchema.@Nullable TableEntry materializedTable,  // 参数：物化表的表条目，可能为 null
        String sql,  // 参数：SQL 查询语句
        RelDataType rowType,  // 参数：行类型，描述结果集结构
        @Nullable List<String> viewSchemaPath) {  // 参数：视图模式路径，可能为 null
      this.key = requireNonNull(key, "key");  // 初始化 key 字段，使用 requireNonNull 检查参数是否为 null，如果为 null 则抛出 NullPointerException
      this.rootSchema = requireNonNull(rootSchema, "rootSchema");  // 初始化 rootSchema 字段，检查参数是否为 null
      checkArgument(rootSchema.isRoot(), "must be root schema");  // 验证 rootSchema 必须是根模式，如果不是则抛出 IllegalArgumentException
      this.materializedTable = materializedTable;  // 初始化 materializedTable 字段，允许为 null（已通过 @Nullable 注释说明）
      this.sql = requireNonNull(sql, "sql");  // 初始化 sql 字段，检查参数是否为 null
      this.rowType = requireNonNull(rowType, "rowType");  // 初始化 rowType 字段，检查参数是否为 null
      this.viewSchemaPath = viewSchemaPath;  // 初始化 viewSchemaPath 字段，允许为 null
    }  // 构造方法结束
  }  // Materialization 内部类结束

  /** A materialization can be re-used if it is the same SQL, on the same
   * schema, with the same path for resolving functions. */
  // 注释：如果 SQL 语句相同、模式相同、函数解析路径相同，则可以重用物化视图
  // 解释：QueryKey 是物化视图匹配的关键类，用于判断一个查询是否可以重用现有的物化视图
  // 物化视图重用的条件：
  // 1. SQL 语句完全相同（包括空格、大小写等）
  // 2. 在同一个模式（Schema）中执行
  // 3. 使用相同的函数解析路径
  // 只有满足这三个条件，才能确定查询结果与物化视图结果一致，从而安全地重用物化视图
  static class QueryKey {  // 定义静态内部类 QueryKey，作为查询的唯一标识符
    // 成员变量：使用 final 修饰，表示这些引用在构造后不能修改

    final String sql;  // SQL 查询语句
    // String：查询的原始 SQL 文本
    // sql：用于标识查询的 SQL 语句
    // 作用：作为查询匹配的核心条件，必须完全相同才能重用物化视图
    // 注意：SQL 语句的任何差异（包括空格、注释等）都会导致 QueryKey 不同

    final CalciteSchema schema;  // 查询所在的模式
    // CalciteSchema：查询执行的模式上下文
    // schema：标识查询在哪个模式中执行
    // 作用：确保物化视图和查询在同一个模式中，避免跨模式的错误重用
    // 使用场景：同一个 SQL 在不同模式中执行可能访问不同的表，因此不能重用

    final @Nullable List<String> path;  // 函数解析路径
    // @Nullable：表示这个字段可能为 null
    // List<String>：用于解析函数的路径列表
    // path：函数解析的搜索路径
    // 作用：确保使用相同的函数解析逻辑，避免同名函数在不同路径下的歧义
    // 使用场景：当 SQL 中引用了自定义函数时，需要通过这个路径来定位函数

    QueryKey(String sql, CalciteSchema schema, @Nullable List<String> path) {  // 构造方法：创建 QueryKey 对象
      this.sql = sql;  // 初始化 sql 字段
      this.schema = schema;  // 初始化 schema 字段
      this.path = path;  // 初始化 path 字段，允许为 null
    }  // 构造方法结束

    @Override public boolean equals(@Nullable Object obj) {  // 重写 equals 方法，用于比较两个 QueryKey 是否相等
      return obj == this  // 首先检查是否是同一个对象引用（快速路径）
          || obj instanceof QueryKey  // 检查 obj 是否是 QueryKey 类型的实例
          && sql.equals(((QueryKey) obj).sql)  // 比较 sql 字段是否相等
          && schema.equals(((QueryKey) obj).schema)  // 比较 schema 字段是否相等
          && Objects.equals(path, ((QueryKey) obj).path);  // 比较 path 字段是否相等（使用 Objects.equals 处理 null 情况）
    }  // equals 方法结束

    @Override public int hashCode() {  // 重写 hashCode 方法，用于在哈希表中计算 QueryKey 的哈希值
      return Objects.hash(sql, schema, path);  // 使用 Objects.hash 方法计算所有字段的组合哈希值
    }  // hashCode 方法结束
  }  // QueryKey 内部类结束
}  // MaterializationActor 类结束
