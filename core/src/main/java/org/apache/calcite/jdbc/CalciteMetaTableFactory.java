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
// 声明包名，表示这个类属于 org.apache.calcite.jdbc 包，是 Calcite JDBC 相关的核心包
package org.apache.calcite.jdbc;

// 导入 Avatica 框架中的 MetaTable 类，MetaTable 是 Avatica 元数据表的基础接口，用于表示数据库元数据表
import org.apache.calcite.avatica.MetaImpl.MetaTable;
// 导入 Calcite 的 Table 接口，代表 Calcite 中的表抽象
import org.apache.calcite.schema.Table;

// 导入 Java 集合框架中的 List 接口，用于存储列名列表
import java.util.List;

/**
 * 用于创建 {@link MetaTable} 实例的工厂接口
 * 
 * 类作用说明：
 * 这个接口定义了创建元数据表（MetaTable）的工厂方法。在 Calcite 和 Avatica 框架中，
 * 元数据表是用于存储数据库元数据信息（如表、列、索引等）的特殊表。
 * 
 * 为什么需要这个工厂：
 * 1. JDBC 规范要求通过 DatabaseMetaData.getTables() 等方法获取数据库元数据
 * 2. Calcite 需要将内部的 Table 对象转换为符合 JDBC 规范的 MetaTable 对象
 * 3. 不同的元数据表类型（TABLES、COLUMNS、INDEXES 等）需要不同的创建逻辑
 * 4. 工厂模式使得创建过程可配置和可扩展
 * 
 * 使用场景：
 * - 当用户通过 JDBC 连接查询数据库元数据时
 * - 当 Calcite 需要向客户端返回表信息时
 * - 当实现自定义的元数据表类型时
 *
 * @see java.sql.DatabaseMetaData#getTables 参见 JDBC DatabaseMetaData 的 getTables 方法
 */
public interface CalciteMetaTableFactory {
  /**
   * 实例化一个 MetaTable 对象
   * 
   * 方法作用：
   * 这是工厂的核心方法，负责根据传入的参数创建一个 MetaTable 实例。
   * MetaTable 是 Avatica 框架中表示元数据表的抽象，用于包装实际的表信息。
   * 
   * 参数说明：
   * @param table - Calcite 的 Table 对象，表示实际的表定义和元数据
   *               包含表的 schema、字段类型、约束等信息
   * @param tableCat - 表的目录名称（catalog），在多目录数据库系统中用于区分不同的数据库
   *                  例如：在 MySQL 中对应数据库名，在某些系统中可能为 null
   * @param tableSchem - 表的模式名称（schema），用于在目录下进一步组织表
   *                    例如：在 PostgreSQL 中对应 schema 名，在 MySQL 中通常为 null
   * @param tableName - 表的实际名称，这是用户在 SQL 语句中引用表时使用的名称
   * 
   * 返回值说明：
   * @return MetaTable - 返回创建的 MetaTable 实例，该实例包含表的完整元数据信息
   *                    可以被 Avatica 框架用于响应 JDBC 的元数据查询请求
   * 
   * 实现要点：
   * - 实现类需要根据传入的 Table 对象提取必要的元数据信息
   * - 需要将 Calcite 的类型系统映射到 JDBC 的类型系统
   * - 需要填充所有 JDBC 规范要求的元数据字段
   */
  MetaTable createTable(Table table, String tableCat, String tableSchem,
      String tableName);

  /**
   * 返回期望的列名列表
   * 
   * 方法作用：
   * 这个方法定义了元数据表应该包含哪些列。不同的元数据表类型（如 TABLES、COLUMNS 等）
   * 有不同的列结构。这个方法让工厂能够声明它创建的 MetaTable 包含哪些列。
   * 
   * 为什么需要这个方法：
   * 1. JDBC 规范定义了元数据表的标准列结构
   * 2. 不同的元数据查询需要返回不同的列集合
   * 3. 客户端需要知道元数据表的结构来正确解析结果
   * 4. 允许自定义扩展，添加额外的元数据列
   * 
   * 返回值说明：
   * @return List<String> - 列名列表，每个元素是一个列的名称
   *                       默认实现返回 JDBC 规范中定义的标准列
   *                       例如：对于 TABLES 表，返回 ["TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", ...]
   * 
   * 默认实现说明：
   * 默认返回 CalciteMetaImpl.TABLE_COLUMNS，这是 JDBC 规范中定义的标准列集合。
   * 子类可以重写此方法来提供自定义的列结构。
   * 
   * <p>The default implementation returns the columns described in the JDBC
   * specification. 默认实现返回 JDBC 规范中描述的列 */
  default List<String> getColumnNames() {
    // 返回 CalciteMetaImpl 中定义的标准 TABLE_COLUMNS 常量
    // 这个常量包含了 JDBC 规范要求的所有标准列名
    return CalciteMetaImpl.TABLE_COLUMNS;
  }

  /**
   * 返回创建的对象类型
   * 
   * 方法作用：
   * 这个方法返回工厂创建的 MetaTable 的具体类类型。
   * 这对于类型检查、反射操作和框架内部逻辑非常重要。
   * 
   * 为什么需要这个方法：
   * 1. 框架需要知道创建的具体类型来进行类型安全的操作
   * 2. 可以用于验证返回的对象是否符合预期
   * 3. 支持基于类型的条件逻辑和分支处理
   * 4. 便于在运行时进行反射和动态代理
   * 
   * 返回值说明：
   * @return Class<? extends MetaTable> - 返回创建的 MetaTable 的 Class 对象
   *                                    类型参数表示必须是 MetaTable 的子类
   *                                    例如：CalciteMetaTable.class, CustomMetaTable.class 等
   * 
   * 使用场景：
   * - Avatica 框架在序列化/反序列化时需要知道具体类型
   * - 在注册表和工厂注册时需要类型信息
   * - 在进行类型转换和适配时需要类型信息
   * 
   * 约束条件：
   * 返回的类型必须是 MetaTable 的子类，不能是 MetaTable 本身（因为它是抽象类或接口）
   */
  Class<? extends MetaTable> getMetaTableClass();
}
