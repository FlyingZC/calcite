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
package org.apache.calcite.jdbc; // 声明该类属于 org.apache.calcite.jdbc 包，这是 Calcite JDBC 驱动的核心包

import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历查询结果集
import org.apache.calcite.schema.Schema; // 导入 Schema 接口，定义了模式的基本行为
import org.apache.calcite.schema.Table; // 导入 Table 接口，定义了表的基本行为
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象模式基类，提供默认实现

import com.google.common.collect.ImmutableMap; // 导入 Google Guava 的不可变 Map 类，用于创建不可变的映射表

import java.sql.SQLException; // 导入 SQL 异常类，用于处理数据库操作中的错误
import java.util.Map; // 导入 Map 接口，用于存储键值对映射

import static org.apache.calcite.avatica.MetaImpl.MetaColumn; // 导入 Avatica 的 MetaColumn 类，表示列的元数据
import static org.apache.calcite.avatica.MetaImpl.MetaTable; // 导入 Avatica 的 MetaTable 类，表示表的元数据

/** Schema that contains metadata tables such as "TABLES" and "COLUMNS". */ // 元数据模式，包含 "TABLES" 和 "COLUMNS" 等元数据表
class MetadataSchema extends AbstractSchema { // 定义 MetadataSchema 类，继承自 AbstractSchema，表示包含元数据表的特殊模式
  private static final Map<String, Table> TABLE_MAP = // 定义一个静态不可变的映射表，存储表名到 Table 对象的映射，键是表名（字符串），值是 Table 对象
      ImmutableMap.of( // 使用 Guava 的 ImmutableMap 创建不可变映射，这是线程安全的，创建后不能修改
          "COLUMNS", // 第一个键值对，表名为 "COLUMNS"，表示列元数据表
          new CalciteMetaImpl.MetadataTable<MetaColumn>(MetaColumn.class) { // 创建一个 MetadataTable 实例，泛型类型为 MetaColumn，用于表示列的元数据表
            @Override public Enumerator<MetaColumn> enumerator( // 重写 enumerator 方法，返回用于遍历 MetaColumn 对象的枚举器
                final CalciteMetaImpl meta) { // 参数 meta 是 CalciteMetaImpl 实例，提供了访问数据库元数据的方法
              final String catalog; // 声明一个字符串变量 catalog，用于存储当前数据库的目录名称
              try { // 开始 try 块，用于捕获可能抛出的 SQLException
                catalog = meta.getConnection().getCatalog(); // 通过 meta 对象获取数据库连接，然后调用 getCatalog() 方法获取当前目录名称
              } catch (SQLException e) { // 捕获 SQL 异常，当获取目录名称失败时执行
                throw new RuntimeException(e); // 将 SQLException 包装为 RuntimeException 抛出，因为 enumerator 方法不能抛出检查型异常
              } // 结束 try-catch 块
              return meta.tables(catalog) // 调用 meta.tables(catalog) 方法获取指定目录下的所有表，返回一个可枚举的 MetaTable 集合
                  .selectMany(meta::columns).enumerator(); // 对每个表调用 meta.columns 方法获取该表的所有列，然后使用 selectMany 展开结果，最后返回枚举器
            } // 结束 enumerator 方法
          }, // 结束 "COLUMNS" 表的定义
          "TABLES", // 第二个键值对，表名为 "TABLES"，表示表元数据表
          new CalciteMetaImpl.MetadataTable<MetaTable>(MetaTable.class) { // 创建一个 MetadataTable 实例，泛型类型为 MetaTable，用于表示表的元数据表
            @Override public Enumerator<MetaTable> enumerator(CalciteMetaImpl meta) { // 重写 enumerator 方法，返回用于遍历 MetaTable 对象的枚举器
              final String catalog; // 声明一个字符串变量 catalog，用于存储当前数据库的目录名称
              try { // 开始 try 块，用于捕获可能抛出的 SQLException
                catalog = meta.getConnection().getCatalog(); // 通过 meta 对象获取数据库连接，然后调用 getCatalog() 方法获取当前目录名称
              } catch (SQLException e) { // 捕获 SQL 异常，当获取目录名称失败时执行
                throw new RuntimeException(e); // 将 SQLException 包装为 RuntimeException 抛出，因为 enumerator 方法不能抛出检查型异常
              } // 结束 try-catch 块
              return meta.tables(catalog).enumerator(); // 调用 meta.tables(catalog) 方法获取指定目录下的所有表，并返回遍历这些表的枚举器
            } // 结束 enumerator 方法
          }); // 结束 ImmutableMap.of 方法调用

  public static final Schema INSTANCE = new MetadataSchema(); // 定义一个静态常量 INSTANCE，是 MetadataSchema 的单例实例，全局只有一个元数据模式对象

  /** Creates the data dictionary, also called the information schema. It is a
   * schema called "metadata" that contains tables "TABLES", "COLUMNS" etc. */ // 构造方法的文档注释：创建数据字典，也称为信息模式。这是一个名为 "metadata" 的模式，包含 "TABLES"、"COLUMNS" 等表
  private MetadataSchema() {} // 私有构造方法，防止外部创建实例，强制使用单例 INSTANCE，这是单例模式的典型实现

  @Override protected Map<String, Table> getTableMap() { // 重写父类 AbstractSchema 的 getTableMap 方法，返回该模式中的表映射
    return TABLE_MAP; // 返回静态常量 TABLE_MAP，包含 "COLUMNS" 和 "TABLES" 两个元数据表
  } // 结束 getTableMap 方法
} // 结束 MetadataSchema 类定义
