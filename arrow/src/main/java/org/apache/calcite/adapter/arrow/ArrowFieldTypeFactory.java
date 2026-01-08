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
package org.apache.calcite.adapter.arrow; // 定义包名，表示这个类属于Apache Calcite的Arrow适配器模块

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建Calcite的Java类型
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示Calcite中的SQL类型系统
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，表示标准的SQL类型（如INTEGER、VARCHAR等）

import org.apache.arrow.vector.types.FloatingPointPrecision; // 导入Arrow浮点数精度枚举，表示单精度或双精度浮点数
import org.apache.arrow.vector.types.pojo.ArrowType; // 导入Arrow类型基类，表示Arrow格式中的数据类型

/**
 * Arrow field type. // Arrow字段类型工厂类，用于将Arrow数据类型转换为Calcite的SQL数据类型
 * 
 * 这个类是一个工具类，专门负责Apache Arrow数据类型和Apache Calcite SQL数据类型之间的映射转换。
 * Arrow是Apache的一个列式内存格式，Calcite是SQL查询引擎框架，这个类是连接两者的桥梁。
 * 
 * 主要功能：
 * 1. 将Arrow的各种数据类型（如Int、Bool、Utf8、FloatingPoint等）映射到Calcite的SQL类型
 * 2. 支持不同位宽的整数类型（8位、16位、32位、64位）
 * 3. 支持不同精度的浮点数类型（单精度、双精度）
 * 4. 支持Decimal类型的精度和标度映射
 * 5. 为所有转换后的类型添加可空性（nullable）属性
 * 
 * 设计模式：工具类模式（Utility Class），所有方法都是静态的，不允许实例化
 */
public class ArrowFieldTypeFactory { // 定义公共类ArrowFieldTypeFactory，这是一个类型转换工厂类

  private ArrowFieldTypeFactory() { // 私有构造方法，防止外部实例化这个工具类
    throw new UnsupportedOperationException("Utility class"); // 抛出异常，明确表示这是一个工具类，不应该被实例化
  } // 构造方法结束，确保这个类只能通过静态方法使用

  public static RelDataType toType(ArrowType arrowType, JavaTypeFactory typeFactory) { // 公共静态方法，将Arrow类型转换为Calcite的RelDataType（关系数据类型）
    RelDataType sqlType = of(arrowType, typeFactory); // 调用私有静态方法of()进行实际的类型转换，得到基础的SQL类型
    return typeFactory.createTypeWithNullability(sqlType, true); // 使用typeFactory为转换后的类型添加可空性（nullable=true），表示该字段可以包含NULL值
  } // 方法结束，返回带有可空性的RelDataType

  /**
   * Converts an Arrow type to a Calcite RelDataType. // 将Arrow类型转换为Calcite的RelDataType的私有静态方法
   * // 这是类型转换的核心实现方法，通过switch-case语句处理各种Arrow类型
   * // 该方法是类型映射的核心逻辑，负责将Arrow的类型系统映射到Calcite的SQL类型系统
   *
   * @param arrowType the Arrow type to convert // 参数：要转换的Arrow类型对象
   * @param typeFactory the factory to create the Calcite type // 参数：Calcite的类型工厂，用于创建RelDataType实例
   * @return the corresponding Calcite RelDataType // 返回值：对应的Calcite关系数据类型
   */
  private static RelDataType of(ArrowType arrowType, JavaTypeFactory typeFactory) { // 私有静态方法，执行实际的类型转换逻辑
    switch (arrowType.getTypeID()) { // 根据Arrow类型的ID进行分支判断，getTypeID()返回Arrow类型的枚举值
    case Int: // 处理整数类型（ArrowType.Int）
      int bitWidth = ((ArrowType.Int) arrowType).getBitWidth(); // 获取整数的位宽（8位、16位、32位或64位），需要将arrowType强制转换为ArrowType.Int
      switch (bitWidth) { // 根据位宽进行二级分支判断，确定具体的整数类型
      case 64: // 64位整数
        return typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建BIGINT类型，对应SQL中的BIGINT（长整数）
      case 32: // 32位整数
        return typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型，对应SQL中的INTEGER（标准整数）
      case 16: // 16位整数
        return typeFactory.createSqlType(SqlTypeName.SMALLINT); // 创建SMALLINT类型，对应SQL中的SMALLINT（短整数）
      case 8: // 8位整数
        return typeFactory.createSqlType(SqlTypeName.TINYINT); // 创建TINYINT类型，对应SQL中的TINYINT（极小整数）
      default: // 不支持的位宽
        throw new IllegalArgumentException("Unsupported Int bit width: " + bitWidth); // 抛出非法参数异常，表示不支持该位宽
      } // 二级switch结束
    case Bool: // 处理布尔类型（ArrowType.Bool）
      return typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 创建BOOLEAN类型，对应SQL中的BOOLEAN（布尔值）
    case Utf8: // 处理UTF-8字符串类型（ArrowType.Utf8）
      return typeFactory.createSqlType(SqlTypeName.VARCHAR); // 创建VARCHAR类型，对应SQL中的VARCHAR（可变长度字符串）
    case FloatingPoint: // 处理浮点数类型（ArrowType.FloatingPoint）
      FloatingPointPrecision precision = ((ArrowType.FloatingPoint) arrowType).getPrecision(); // 获取浮点数的精度（单精度或双精度），需要将arrowType强制转换为ArrowType.FloatingPoint
      switch (precision) { // 根据精度进行二级分支判断，确定具体的浮点数类型
      case SINGLE: // 单精度浮点数（32位）
        return typeFactory.createSqlType(SqlTypeName.REAL); // 创建REAL类型，对应SQL中的REAL（单精度浮点数）
      case DOUBLE: // 双精度浮点数（64位）
        return typeFactory.createSqlType(SqlTypeName.DOUBLE); // 创建DOUBLE类型，对应SQL中的DOUBLE（双精度浮点数）
      default: // 不支持的浮点数精度
        throw new IllegalArgumentException("Unsupported Floating point precision: " + precision); // 抛出非法参数异常，表示不支持该精度
      } // 二级switch结束
    case Date: // 处理日期类型（ArrowType.Date）
      return typeFactory.createSqlType(SqlTypeName.DATE); // 创建DATE类型，对应SQL中的DATE（日期）
    case Decimal: // 处理十进制数类型（ArrowType.Decimal）
      return typeFactory.createSqlType(SqlTypeName.DECIMAL, // 创建DECIMAL类型，对应SQL中的DECIMAL（精确小数）
          ((ArrowType.Decimal) arrowType).getPrecision(), // 获取Decimal的精度（总位数），需要将arrowType强制转换为ArrowType.Decimal
          ((ArrowType.Decimal) arrowType).getScale()); // 获取Decimal的标度（小数位数），需要将arrowType强制转换为ArrowType.Decimal
    case Time: // 处理时间类型（ArrowType.Time）
      return typeFactory.createSqlType(SqlTypeName.TIME); // 创建TIME类型，对应SQL中的TIME（时间）
    default: // 不支持的Arrow类型
      throw new IllegalArgumentException("Unsupported type: " + arrowType); // 抛出非法参数异常，表示不支持该Arrow类型
    } // 一级switch结束
  } // 方法结束，返回转换后的RelDataType
} // 类定义结束
