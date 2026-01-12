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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.adapter.innodb; // 定义包名，该类位于org.apache.calcite.adapter.innodb包下

import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入RelDataTypeImpl类，关系数据类型的实现
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入RelDataTypeSystem接口，定义类型系统
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，关系数据类型的原型
import org.apache.calcite.schema.Table; // 导入Table接口，表示Calcite中的表
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema抽象类，作为Schema的基类
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SqlTypeFactoryImpl类，SQL类型工厂的实现
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义SQL类型名称

import org.apache.commons.lang3.StringUtils; // 导入StringUtils工具类，用于字符串操作

import com.alibaba.innodb.java.reader.TableReaderFactory; // 导入TableReaderFactory类，用于读取InnoDB表
import com.alibaba.innodb.java.reader.column.ColumnType; // 导入ColumnType类，表示列类型
import com.alibaba.innodb.java.reader.schema.Column; // 导入Column类，表示列定义
import com.alibaba.innodb.java.reader.schema.TableDef; // 导入TableDef类，表示表定义
import com.alibaba.innodb.java.reader.schema.provider.TableDefProvider; // 导入TableDefProvider接口，提供表定义
import com.alibaba.innodb.java.reader.schema.provider.impl.SqlFileTableDefProvider; // 导入SqlFileTableDefProvider类，从SQL文件提供表定义
import com.google.common.collect.ImmutableMap; // 导入ImmutableMap类，用于构建不可变的Map

import java.util.List; // 导入List接口，用于列表集合
import java.util.Map; // 导入Map接口，用于键值对集合

import static com.google.common.base.Preconditions.checkArgument; // 导入checkArgument静态方法，用于参数校验

import static java.util.stream.Collectors.toList; // 导入toList静态方法，用于流转换为List

/**
 * Schema for an InnoDB data source. // InnoDB数据源的Schema，用于表示和访问InnoDB数据库中的表结构
 * 该类继承自AbstractSchema，是Calcite适配器模式中的关键组件，负责将InnoDB数据库的表结构映射到Calcite的逻辑模型中
 * 它通过读取SQL文件和.ibd数据文件来获取表的元数据，并为Calcite提供表的定义和数据访问能力
 */
public class InnodbSchema extends AbstractSchema { // 定义InnodbSchema类，继承自AbstractSchema抽象类
  final List<String> sqlFilePathList; // SQL文件路径列表，包含用于定义表结构的SQL文件路径，这些文件包含CREATE TABLE语句
  final String ibdDataFileBasePath; // InnoDB数据文件的基础路径，指向.ibd数据文件所在的目录，这些文件包含实际的表数据
  final TableReaderFactory tableReaderFactory; // 表读取器工厂，用于创建表读取器实例，负责读取InnoDB表的定义和数据

  static final ColumnTypeToSqlTypeConversionRules COLUMN_TYPE_TO_SQL_TYPE = // 静态常量：列类型到SQL类型的转换规则
      ColumnTypeToSqlTypeConversionRules.instance(); // 获取ColumnTypeToSqlTypeConversionRules的单例实例，用于将InnoDB列类型映射到Calcite的SQL类型

  public InnodbSchema(List<String> sqlFilePathList, // 构造方法：创建InnodbSchema实例，初始化InnoDB数据源的Schema
      String ibdDataFileBasePath) { // 参数：ibdDataFileBasePath - InnoDB数据文件的基础路径
    checkArgument(sqlFilePathList != null && !sqlFilePathList.isEmpty(), // 校验SQL文件路径列表不为空
        "SQL file path list cannot be empty"); // 如果为空则抛出异常
    checkArgument(StringUtils.isNotEmpty(ibdDataFileBasePath), // 校验InnoDB数据文件路径不为空
        "InnoDB data file with ibd suffix cannot be empty"); // 如果为空则抛出异常
    this.sqlFilePathList = sqlFilePathList; // 保存SQL文件路径列表到成员变量
    this.ibdDataFileBasePath = ibdDataFileBasePath; // 保存InnoDB数据文件路径到成员变量

    List<TableDefProvider> tableDefProviderList = sqlFilePathList.stream() // 将SQL文件路径列表转换为流
        .map(SqlFileTableDefProvider::new).collect(toList()); // 将每个SQL文件路径转换为SqlFileTableDefProvider实例，并收集到列表
    this.tableReaderFactory = TableReaderFactory.builder() // 创建TableReaderFactory构建器
        .withProviders(tableDefProviderList) // 设置表定义提供者列表
        .withDataFileBasePath(ibdDataFileBasePath) // 设置数据文件基础路径
        .build(); // 构建TableReaderFactory实例
  } // 构造方法结束

  RelProtoDataType getRelDataType(String tableName) { // 获取指定表的关系数据类型原型，用于在Calcite中表示表的结构
    // Temporary type factory, just for the duration of this method. Allowable
    // because we're creating a proto-type, not a type; before being used, the
    // proto-type will be copied into a real type factory.
    // 创建临时的类型工厂，仅在此方法期间使用。这是允许的，因为我们创建的是原型类型，而不是实际类型；
    // 在使用之前，原型类型将被复制到实际的类型工厂中
    final RelDataTypeFactory typeFactory = // 创建SQL类型工厂实例，用于构建关系数据类型
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认的RelDataTypeSystem创建类型工厂
    final RelDataTypeFactory.Builder fieldInfo = typeFactory.builder(); // 创建类型构建器，用于构建字段信息
    if (!tableReaderFactory.existTableDef(tableName)) { // 检查表定义是否存在
      throw new RuntimeException("Table definition " + tableName // 如果不存在则抛出运行时异常
          + " not found"); // 异常信息包含表名
    }
    TableDef tableDef = tableReaderFactory.getTableDef(tableName); // 获取表定义对象，包含表的结构信息
    for (Column column : tableDef.getColumnList()) { // 遍历表中的每一列
      final SqlTypeName sqlTypeName = // 获取列对应的SQL类型名称
          COLUMN_TYPE_TO_SQL_TYPE.lookup(column.getType()); // 通过转换规则查找InnoDB列类型对应的SQL类型
      final int precision; // 声明精度变量，用于存储列的精度
      final int scale; // 声明标度变量，用于存储列的标度（小数位数）
      switch (column.getType()) { // 根据列类型进行特殊处理
      case ColumnType.TIMESTAMP: // 时间戳类型
      case ColumnType.TIME: // 时间类型
      case ColumnType.DATETIME: // 日期时间类型
        precision = column.getPrecision(); // 获取精度
        scale = 0; // 这些类型的标度固定为0
        break; // 跳出switch
      default: // 默认情况
        precision = column.getPrecision(); // 获取精度
        scale = column.getScale(); // 获取标度
        break; // 跳出switch
      }
      if (sqlTypeName.allowsPrecScale(true, true) // 检查SQL类型是否同时支持精度和标度
          && column.getPrecision() >= 0 // 精度必须大于等于0
          && column.getScale() >= 0) { // 标度必须大于等于0
        fieldInfo.add(column.getName(), sqlTypeName, precision, scale); // 添加带有精度和标度的字段
      } else if (sqlTypeName.allowsPrecNoScale() && precision >= 0) { // 检查SQL类型是否只支持精度不支持标度
        fieldInfo.add(column.getName(), sqlTypeName, precision); // 添加只带有精度的字段
      } else { // 其他情况
        assert sqlTypeName.allowsNoPrecNoScale(); // 断言SQL类型不支持精度和标度
        fieldInfo.add(column.getName(), sqlTypeName); // 添加不带精度和标度的字段
      }
      fieldInfo.nullable(column.isNullable()); // 设置字段是否可为空
    }
    return RelDataTypeImpl.proto(fieldInfo.build()); // 返回关系数据类型的原型，用于后续的类型转换
  } // 方法结束

  /**
   * Return table definition. // 返回表定义，获取指定表的详细结构信息
   * 该方法用于获取表的完整定义，包括列名、列类型、索引等信息
   * @param tableName 表名，要获取定义的表名称
   * @return TableDef 表定义对象，包含表的完整结构信息
   */
  public TableDef getTableDef(String tableName) { // 获取表定义的公共方法
    if (!tableReaderFactory.existTableDef(tableName)) { // 检查表定义是否存在
      throw new RuntimeException("cannot find table definition for " + tableName); // 如果不存在则抛出异常
    }
    return tableReaderFactory.getTableDef(tableName); // 返回表定义对象
  } // 方法结束

  @Override protected Map<String, Table> getTableMap() { // 重写父类方法，获取Schema中所有表的映射关系
    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建不可变Map的构建器
    Map<String, TableDef> map = tableReaderFactory.getTableNameToDefMap(); // 获取所有表名到表定义的映射
    for (Map.Entry<String, TableDef> entry : map.entrySet()) { // 遍历每个表定义
      String tableName = entry.getKey(); // 获取表名
      builder.put(tableName, new InnodbTable(this, tableName)); // 创建InnodbTable实例并添加到Map中
    }
    return builder.build(); // 构建并返回不可变的表映射Map
  } // 方法结束
}
