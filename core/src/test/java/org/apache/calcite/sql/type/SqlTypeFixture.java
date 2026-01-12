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
package org.apache.calcite.sql.type;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeSystem;

import com.google.common.collect.ImmutableList;

/**
 * 可重用的 {@link RelDataType} 测试工具类，用于在测试中提供各种SQL类型的数据结构
 * 该类封装了Calcite中常用的SQL类型，包括基本类型、数组类型、多集类型、结构类型和映射类型
 * 通过预定义这些类型，测试代码可以方便地访问和使用标准化的SQL类型实例
 * 该类主要用于单元测试和集成测试，确保类型系统的一致性和可重复性
 */
class SqlTypeFixture {
  // 类型工厂实例，使用默认的关系数据类型系统，用于创建各种SQL类型
  // SqlTypeFactoryImpl是Calcite中创建SQL数据类型的核心工厂类
  // RelDataTypeSystem.DEFAULT提供了标准的类型系统实现，包括类型兼容性规则、精度限制等
  final SqlTypeFactoryImpl typeFactory =
      new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);
  // 布尔类型（非空），对应SQL中的BOOLEAN类型，值为true或false
  final RelDataType sqlBoolean = type(SqlTypeName.BOOLEAN, false);
  // 大整数类型（非空），对应SQL中的BIGINT类型，通常表示64位有符号整数
  final RelDataType sqlBigInt = type(SqlTypeName.BIGINT, false);
  // 大整数类型（可空），允许存储NULL值的大整数类型
  final RelDataType sqlBigIntNullable = type(SqlTypeName.BIGINT, true);
  // 整数类型（非空），对应SQL中的INTEGER类型，通常表示32位有符号整数
  final RelDataType sqlInt = type(SqlTypeName.INTEGER, false);
  // 日期类型（非空），对应SQL中的DATE类型，表示年月日，不包含时间部分
  final RelDataType sqlDate = type(SqlTypeName.DATE, false);
  // 变长字符串类型（非空），对应SQL中的VARCHAR类型，长度可变
  final RelDataType sqlVarchar = type(SqlTypeName.VARCHAR, false);
  // 定长字符串类型（非空），对应SQL中的CHAR类型，长度固定，不足时用空格填充
  final RelDataType sqlChar = type(SqlTypeName.CHAR, false);
  // 变长字符串类型（可空），允许存储NULL值的变长字符串类型
  final RelDataType sqlVarcharNullable = type(SqlTypeName.VARCHAR, true);
  // NULL类型（非空），表示SQL中的NULL值，用于类型推导和nullability处理
  final RelDataType sqlNull = type(SqlTypeName.NULL, false);
  // 未知类型（非空），用于表示类型推导过程中尚未确定的类型
  final RelDataType sqlUnknown = type(SqlTypeName.UNKNOWN, false);
  // 任意类型（非空），表示可以接受任何类型的万能类型，用于类型系统的顶层类型
  final RelDataType sqlAny = type(SqlTypeName.ANY, false);
  // 浮点数类型（非空），对应SQL中的FLOAT类型，通常表示32位浮点数
  final RelDataType sqlFloat = type(SqlTypeName.FLOAT, false);
  // 时间戳类型（精度为0），对应SQL中的TIMESTAMP类型，精度0表示不包含小数秒
  final RelDataType sqlTimestampPrec0 = type(SqlTypeName.TIMESTAMP, 0);
  // 时间戳类型（精度为3），对应SQL中的TIMESTAMP类型，精度3表示包含3位小数秒（毫秒）
  final RelDataType sqlTimestampPrec3 = type(SqlTypeName.TIMESTAMP, 3);
  // 几何类型（非空），用于表示空间数据，如点、线、面等地理信息
  final RelDataType sqlGeometry = type(SqlTypeName.GEOMETRY, false);
  // 浮点数数组类型（非空），元素类型为FLOAT，数组长度不限（-1表示无界）
  // 数组类型用于表示同类型元素的有序集合，支持索引访问
  final RelDataType arrayFloat =
      notNullable(typeFactory.createArrayType(sqlFloat, -1));
  // 可空大整数数组类型（非空），元素类型为可空的BIGINT，数组长度不限
  // 注意：数组本身不可空，但数组元素可以是NULL
  final RelDataType arrayBigInt =
      notNullable(typeFactory.createArrayType(sqlBigIntNullable, -1));
  // 浮点数多集类型（非空），元素类型为FLOAT，多集长度不限
  // 多集（MULTISET）是一种允许重复元素的无序集合，类似SQL中的MULTISET类型
  final RelDataType multisetFloat =
      notNullable(typeFactory.createMultisetType(sqlFloat, -1));
  // 可空大整数多集类型（非空），元素类型为可空的BIGINT，多集长度不限
  final RelDataType multisetBigInt =
      notNullable(typeFactory.createMultisetType(sqlBigIntNullable, -1));
  // 可空大整数多集类型（可空），多集本身允许为NULL，元素类型为可空的BIGINT
  final RelDataType multisetBigIntNullable =
      nullable(typeFactory.createMultisetType(sqlBigIntNullable, -1));
  // 可空大整数数组类型（可空），数组本身允许为NULL，元素类型为可空的BIGINT
  final RelDataType arrayBigIntNullable =
      nullable(typeFactory.createArrayType(sqlBigIntNullable, -1));
  // 二维数组类型（非空），数组的数组，外层和内层都是不可空的BIGINT数组
  // 用于测试嵌套数组类型和复杂类型系统
  final RelDataType arrayOfArrayBigInt =
      notNullable(typeFactory.createArrayType(arrayBigInt, -1));
  // 二维数组类型（非空），数组的数组，外层和内层都是不可空的FLOAT数组
  final RelDataType arrayOfArrayFloat =
      notNullable(typeFactory.createArrayType(arrayFloat, -1));
  // 结构体类型（非空），包含两个INTEGER字段，字段名分别为"i"和"j"
  // 结构体（STRUCT）类型用于表示具有命名字段的记录，类似SQL中的行类型或结构化类型
  final RelDataType structOfInt =
      notNullable(
          typeFactory.createStructType(
              ImmutableList.of(sqlInt, sqlInt), ImmutableList.of("i", "j")));
  // 结构体类型（可空），包含两个INTEGER字段，字段名分别为"i"和"j"，结构体本身允许为NULL
  final RelDataType structOfIntNullable =
      nullable(
          typeFactory.createStructType(
              ImmutableList.of(sqlInt, sqlInt), ImmutableList.of("i", "j")));
  // 映射类型（非空），键和值都是INTEGER类型
  // 映射（MAP）类型用于表示键值对集合，类似SQL中的MAP类型，键必须唯一
  final RelDataType mapOfInt =
      notNullable(typeFactory.createMapType(sqlInt, sqlInt));
  // 映射类型（可空），键和值都是INTEGER类型，映射本身允许为NULL
  final RelDataType mapOfIntNullable =
      nullable(typeFactory.createMapType(sqlInt, sqlInt));
  // 定长字符串类型（非空），长度为1，用于测试固定长度字符串类型
  final RelDataType sqlChar1 = type(SqlTypeName.CHAR, 1);
  // 定长字符串类型（非空），长度为10，用于测试固定长度字符串类型
  final RelDataType sqlChar10 = type(SqlTypeName.CHAR, 10);
  // 定长字符串数组类型（非空），元素类型为CHAR(10)，数组长度不限
  final RelDataType arraySqlChar10 =
      notNullable(typeFactory.createArrayType(sqlChar10, -1));
  // 定长字符串数组类型（非空），元素类型为CHAR(1)，数组长度不限
  final RelDataType arraySqlChar1 =
      notNullable(typeFactory.createArrayType(sqlChar1, -1));
  // 定长字符串多集类型（可空），元素类型为CHAR(10)，多集本身允许为NULL
  final RelDataType multisetSqlChar10Nullable =
      nullable(typeFactory.createMultisetType(sqlChar10, -1));
  // 定长字符串多集类型（非空），元素类型为CHAR(1)，多集长度不限
  final RelDataType multisetSqlChar1 =
      notNullable(typeFactory.createMultisetType(sqlChar1, -1));
  // 定长字符串映射类型（可空），键和值都是CHAR(10)类型，映射本身允许为NULL
  final RelDataType mapSqlChar10Nullable =
      nullable(typeFactory.createMapType(sqlChar10, sqlChar10));
  // 定长字符串映射类型（非空），键和值都是CHAR(1)类型
  final RelDataType mapSqlChar1 =
      notNullable(typeFactory.createMapType(sqlChar1, sqlChar1));

  // 私有辅助方法：创建指定类型名称和精度的非空SQL类型
  // 参数typeName: SQL类型名称枚举，如TIMESTAMP、DECIMAL等需要指定精度的类型
  // 参数precision: 类型精度，如时间戳的小数秒位数、字符类型的长度等
  // 返回值: 创建的不可空RelDataType对象
  private RelDataType type(SqlTypeName typeName, int precision) {
    return notNullable(typeFactory.createSqlType(typeName, precision));
  }

  // 私有辅助方法：创建指定类型名称和可空性的SQL类型
  // 参数typeName: SQL类型名称枚举，如BOOLEAN、INTEGER等基本类型
  // 参数nullable: 是否允许NULL值，true表示可空，false表示不可空
  // 返回值: 创建的RelDataType对象，具有指定的可空性
  private RelDataType type(SqlTypeName typeName, boolean nullable) {
    RelDataType type = typeFactory.createSqlType(typeName);
    return typeFactory.createTypeWithNullability(type, nullable);
  }

  // 私有辅助方法：将给定的类型设置为不可空
  // 参数type: 原始类型对象
  // 返回值: 具有相同类型结构但设置为不可空的RelDataType对象
  // 该方法用于确保类型不允许NULL值，常用于创建严格的类型约束
  private RelDataType notNullable(RelDataType type) {
    return typeFactory.createTypeWithNullability(type, false);
  }

  // 私有辅助方法：将给定的类型设置为可空
  // 参数type: 原始类型对象
  // 返回值: 具有相同类型结构但设置为可空的RelDataType对象
  // 该方法用于允许类型接受NULL值，常用于创建宽松的类型约束
  private RelDataType nullable(RelDataType type) {
    return typeFactory.createTypeWithNullability(type, true);
  }
}
