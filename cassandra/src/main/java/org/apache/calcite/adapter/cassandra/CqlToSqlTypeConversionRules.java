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
package org.apache.calcite.adapter.cassandra; // 指定当前类所在的包路径，属于Cassandra适配器模块

import org.apache.calcite.sql.type.SqlTypeName; // 导入Calcite SQL类型名称枚举，表示标准SQL数据类型（如VARCHAR, INTEGER等）

import com.datastax.oss.driver.api.core.type.DataType; // 导入Cassandra驱动中的DataType接口，表示CQL（Cassandra Query Language）数据类型
import com.datastax.oss.driver.api.core.type.DataTypes; // 导入Cassandra驱动中的DataTypes工具类，提供各种CQL数据类型的常量定义
import com.google.common.collect.ImmutableMap; // 导入Google Guava库的不可变Map类，用于创建不可修改的类型映射关系

import java.util.Map; // 导入Java标准库的Map接口，用于存储键值对映射关系

/**
 * CqlToSqlTypeConversionRules defines mappings from CQL types to
 * corresponding SQL types.
 * CqlToSqlTypeConversionRules类定义了从CQL（Cassandra Query Language）类型到对应SQL类型的映射规则
 * 这个类的作用是将Cassandra数据库的CQL数据类型转换为Calcite框架中使用的标准SQL数据类型
 * 这样Calcite就可以正确地理解和处理Cassandra表中的数据类型
 */
public class CqlToSqlTypeConversionRules { // 定义一个公共类，负责CQL类型到SQL类型的转换规则
  //~ Static fields/initializers --------------------------------------------- // 静态字段/初始化器区域的分隔标记

  private static final CqlToSqlTypeConversionRules INSTANCE = // 声明一个私有的静态常量INSTANCE，这是单例模式的实现，存储该类的唯一实例
      new CqlToSqlTypeConversionRules(); // 在类加载时就创建该类的实例，确保全局只有一个实例存在

  //~ Instance fields -------------------------------------------------------- // 实例字段区域的分隔标记

  private final Map<DataType, SqlTypeName> rules = // 声明一个私有的最终成员变量rules，类型为不可变Map，键是CQL的DataType，值是Calcite的SqlTypeName
      ImmutableMap.<DataType, SqlTypeName>builder() // 使用Guava的ImmutableMap构建器，创建一个不可变的Map实例
          .put(DataTypes.UUID, SqlTypeName.CHAR) // 映射CQL的UUID类型（通用唯一标识符）到SQL的CHAR类型（固定长度字符串）
          .put(DataTypes.TIMEUUID, SqlTypeName.CHAR) // 映射CQL的TIMEUUID类型（基于时间的UUID，用于排序）到SQL的CHAR类型

          .put(DataTypes.ASCII, SqlTypeName.VARCHAR) // 映射CQL的ASCII类型（ASCII字符字符串）到SQL的VARCHAR类型（可变长度字符串）
          .put(DataTypes.TEXT, SqlTypeName.VARCHAR) // 映射CQL的TEXT类型（UTF-8编码的文本字符串）到SQL的VARCHAR类型

          .put(DataTypes.INT, SqlTypeName.INTEGER) // 映射CQL的INT类型（32位有符号整数）到SQL的INTEGER类型
          .put(DataTypes.VARINT, SqlTypeName.INTEGER) // 映射CQL的VARINT类型（任意精度整数）到SQL的INTEGER类型（注意：可能会丢失精度）
          .put(DataTypes.BIGINT, SqlTypeName.BIGINT) // 映射CQL的BIGINT类型（64位有符号长整数）到SQL的BIGINT类型
          .put(DataTypes.TINYINT, SqlTypeName.TINYINT) // 映射CQL的TINYINT类型（8位有符号字节）到SQL的TINYINT类型
          .put(DataTypes.SMALLINT, SqlTypeName.SMALLINT) // 映射CQL的SMALLINT类型（16位有符号短整数）到SQL的SMALLINT类型

          .put(DataTypes.DOUBLE, SqlTypeName.DOUBLE) // 映射CQL的DOUBLE类型（64位浮点数）到SQL的DOUBLE类型
          .put(DataTypes.FLOAT, SqlTypeName.REAL) // 映射CQL的FLOAT类型（32位浮点数）到SQL的REAL类型（单精度浮点数）
          .put(DataTypes.DECIMAL, SqlTypeName.DOUBLE) // 映射CQL的DECIMAL类型（高精度小数）到SQL的DOUBLE类型（注意：可能会丢失精度）

          .put(DataTypes.BLOB, SqlTypeName.VARBINARY) // 映射CQL的BLOB类型（二进制大对象）到SQL的VARBINARY类型（可变长度二进制数据）

          .put(DataTypes.BOOLEAN, SqlTypeName.BOOLEAN) // 映射CQL的BOOLEAN类型（布尔值true/false）到SQL的BOOLEAN类型

          .put(DataTypes.COUNTER, SqlTypeName.BIGINT) // 映射CQL的COUNTER类型（计数器列，只能递增）到SQL的BIGINT类型

          // number of nanoseconds since midnight // 注释说明：CQL的TIME类型表示自午夜以来的纳秒数
          .put(DataTypes.TIME, SqlTypeName.BIGINT) // 映射CQL的TIME类型（时间值，精确到纳秒）到SQL的BIGINT类型（以纳秒数存储）
          .put(DataTypes.DATE, SqlTypeName.DATE) // 映射CQL的DATE类型（日期，没有时间部分）到SQL的DATE类型
          .put(DataTypes.TIMESTAMP, SqlTypeName.TIMESTAMP) // 映射CQL的TIMESTAMP类型（日期和时间戳）到SQL的TIMESTAMP类型
          .build(); // 调用build()方法完成不可变Map的构建，此时rules变量被初始化为一个包含所有类型映射的不可变Map

  //~ Methods ---------------------------------------------------------------- // 方法区域的分隔标记

  /**
   * Returns the
   * {@link org.apache.calcite.util.Glossary#SINGLETON_PATTERN singleton}
   * instance.
   * 返回该类的单例实例
   * 这是一个静态工厂方法，用于获取CqlToSqlTypeConversionRules类的唯一实例
   * 采用单例模式确保整个应用程序中只有一个类型转换规则实例，避免重复创建
   *
   * @return 返回CqlToSqlTypeConversionRules类的单例实例（INSTANCE常量）
   */
  public static CqlToSqlTypeConversionRules instance() { // 声明一个公共静态方法，返回类型为CqlToSqlTypeConversionRules，无参数
    return INSTANCE; // 返回类初始化时创建的单例实例INSTANCE
  }

  /**
   * Returns a corresponding {@link SqlTypeName} for a given CQL type name.
   * 根据给定的CQL类型名称返回对应的SQL类型名称
   * 这是核心方法，用于查询CQL类型到SQL类型的映射关系
   *
   * @param name the CQL type name to lookup // 参数：要查找的CQL类型名称（DataType对象）
   * @return a corresponding SqlTypeName if found, ANY otherwise // 返回值：如果找到对应的SQL类型则返回该类型，否则返回SqlTypeName.ANY（表示任意类型）
   */
  public SqlTypeName lookup(DataType name) { // 声明一个公共实例方法，接收一个DataType参数，返回SqlTypeName
    return rules.getOrDefault(name, SqlTypeName.ANY); // 从rules映射表中查找给定的CQL类型，如果找到则返回对应的SQL类型，如果没找到则返回默认值SqlTypeName.ANY
  } // 方法结束
} // 类定义结束
