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
package org.apache.calcite.adapter.druid; // Druid适配器包，包含Druid相关的适配器类

import org.apache.calcite.avatica.util.DateTimeUtils; // 日期时间工具类，提供UTC时区常量等
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型，表示Calcite中的类型系统
import org.apache.calcite.rex.RexCall; // Rex调用表达式，表示函数调用
import org.apache.calcite.rex.RexNode; // Rex节点基类，表示关系表达式
import org.apache.calcite.sql.SqlOperator; // SQL操作符接口
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 标准SQL操作符表，包含CAST等操作符
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举，定义所有SQL类型

import com.google.common.collect.ImmutableList; // Google的不可变列表，用于构建函数参数列表

import org.checkerframework.checker.nullness.qual.Nullable; // 注解，标记可空类型
import org.joda.time.Period; // Joda时间库的Period类，表示时间段

import java.util.TimeZone; // Java时区类，用于处理时区相关操作

/**
 * Druid cast converter operator; used to translates Calcite casts to Druid
 * expression casts.
 */
// Druid SQL类型转换转换器：用于将Calcite的类型转换操作转换为Druid表达式中的类型转换操作
// 这个类实现了DruidSqlOperatorConverter接口，专门处理CAST操作符的转换
// 它负责处理各种类型之间的转换，包括字符串到日期时间、日期时间到字符串、以及不同日期时间类型之间的转换
// 同时也处理普通类型之间的转换，如数值类型、字符串类型等
public class DruidSqlCastConverter implements DruidSqlOperatorConverter {

  // 返回Calcite的CAST操作符，用于识别这个转换器处理的是CAST操作
  @Override public SqlOperator calciteOperator() {
    return SqlStdOperatorTable.CAST; // 返回标准的SQL CAST操作符
  }

  // 将Calcite的RexNode转换为Druid表达式
  // 参数说明：
  //   rexNode: Calcite的RexNode节点，表示一个CAST表达式
  //   topRel: 关系类型的顶层类型，用于类型推断
  //   druidQuery: Druid查询对象，包含连接配置等信息
  // 返回值：转换后的Druid表达式字符串，如果无法转换则返回null
  @Override public @Nullable String toDruidExpression(RexNode rexNode,
      RelDataType topRel, DruidQuery druidQuery) {

    final RexNode operand = ((RexCall) rexNode).getOperands().get(0); // 从CAST表达式中获取操作数（被转换的值）
    final String operandExpression =
        DruidExpressions.toDruidExpression(operand, topRel, druidQuery); // 将操作数转换为Druid表达式，递归处理

    if (operandExpression == null) {
      return null; // 如果操作数无法转换为Druid表达式，则返回null，表示转换失败
    }

    final SqlTypeName fromType = operand.getType().getSqlTypeName(); // 获取源类型（操作数的类型）
    String fromTypeString = dateTimeFormatString(fromType); // 获取源类型的日期时间格式字符串，用于解析
    final SqlTypeName toType = rexNode.getType().getSqlTypeName(); // 获取目标类型（CAST后的类型）
    final String timeZoneConf = druidQuery.getConnectionConfig().timeZone(); // 从Druid配置中获取时区配置字符串
    final TimeZone timeZone = TimeZone.getTimeZone(timeZoneConf); // 将时区字符串转换为TimeZone对象
    final boolean nullEqualToEmpty = druidQuery.getConnectionConfig().nullEqualToEmpty(); // 获取null值是否等于空字符串的配置

    if (fromTypeString == null) {
      fromTypeString = nullEqualToEmpty ? "" :  null; // 如果源类型的格式字符串为null，根据配置设置默认值
    }

    if (SqlTypeName.CHAR_TYPES.contains(fromType)
        && SqlTypeName.DATETIME_TYPES.contains(toType)) {
      // case chars to dates
      // 处理字符串到日期时间的转换
      return castCharToDateTime(toType == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE
              ? timeZone : DateTimeUtils.UTC_ZONE, // 如果目标类型是TIMESTAMP_WITH_LOCAL_TIME_ZONE，使用配置的时区，否则使用UTC时区
          operandExpression, toType, fromTypeString); // 调用字符串到日期时间的转换方法
    } else if (SqlTypeName.DATETIME_TYPES.contains(fromType)
        && SqlTypeName.CHAR_TYPES.contains(toType)) {
      // case dates to chars
      // 处理日期时间到字符串的转换
      return castDateTimeToChar(fromType == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE
          ? timeZone : DateTimeUtils.UTC_ZONE, operandExpression, fromType); // 如果源类型是TIMESTAMP_WITH_LOCAL_TIME_ZONE，使用配置的时区，否则使用UTC时区
    } else if (SqlTypeName.DATETIME_TYPES.contains(fromType)
        && toType == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) {
      // 处理日期时间类型到TIMESTAMP_WITH_LOCAL_TIME_ZONE的转换
      if (timeZone.equals(DateTimeUtils.UTC_ZONE)) {
        // bail out, internal representation is the same,
        // we do not need to do anything
        return operandExpression; // 如果时区是UTC，内部表示相同，直接返回操作数，无需转换
      }
      // to timestamp with local time zone
      // 先将日期时间转换为字符串（使用UTC时区），再将字符串转换为TIMESTAMP_WITH_LOCAL_TIME_ZONE（使用配置时区）
      return castCharToDateTime(
          timeZone, // 使用配置的时区
          castDateTimeToChar(DateTimeUtils.UTC_ZONE, operandExpression, fromType), // 先转换为字符串，使用UTC时区
          toType, // 目标类型
          fromTypeString); // 格式字符串
    } else if (fromType == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE
        && SqlTypeName.DATETIME_TYPES.contains(toType)) {
      // 处理TIMESTAMP_WITH_LOCAL_TIME_ZONE到其他日期时间类型的转换
      if (toType != SqlTypeName.DATE && timeZone.equals(DateTimeUtils.UTC_ZONE)) {
        // bail out, internal representation is the same,
        // we do not need to do anything
        return operandExpression; // 如果目标类型不是DATE且时区是UTC，直接返回操作数，无需转换
      }
      // timestamp with local time zone to other types
      // 先将TIMESTAMP_WITH_LOCAL_TIME_ZONE转换为字符串（使用配置时区），再将字符串转换为日期时间（使用UTC时区）
      return castCharToDateTime(
          DateTimeUtils.UTC_ZONE, // 使用UTC时区
          castDateTimeToChar(timeZone, operandExpression, fromType), // 先转换为字符串，使用配置时区
          toType, // 目标类型
          fromTypeString); // 格式字符串
    } else {
      // Handle other casts.
      // 处理其他类型的转换（非日期时间类型，如数值、字符串等）
      final DruidType fromExprType = DruidExpressions.EXPRESSION_TYPES.get(fromType); // 获取源类型的Druid类型映射
      final DruidType toExprType = DruidExpressions.EXPRESSION_TYPES.get(toType); // 获取目标类型的Druid类型映射

      if (fromExprType == null || toExprType == null) {
        // Unknown types bail out.
        return null; // 如果源类型或目标类型未知，返回null，表示无法转换
      }
      final String typeCastExpression; // 类型转换表达式
      if (fromExprType != toExprType) {
        typeCastExpression =
            DruidQuery.format("CAST(%s, '%s')", operandExpression,
                toExprType.toString()); // 生成Druid的CAST表达式，格式为CAST(operand, 'type')
      } else {
        // case it is the same type it is ok to skip CAST
        typeCastExpression = operandExpression; // 类型相同，跳过CAST，直接返回操作数
      }

      if (toType == SqlTypeName.DATE) {
        // Floor to day when casting to DATE.
        // 如果目标类型是DATE，需要向下取整到天
        return DruidExpressions.applyTimestampFloor(
            typeCastExpression, // 类型转换表达式
            Period.days(1).toString(), // 向下取整到天，使用Joda的Period类
            "", // 无额外参数
            TimeZone.getTimeZone(druidQuery.getConnectionConfig().timeZone())); // 使用配置的时区
      } else {
        return typeCastExpression; // 返回类型转换表达式
      }

    }
  }

  // 将字符串转换为日期时间类型的私有方法
  // 参数说明：
  //   timeZone: 时区对象，用于解析时间戳
  //   operand: 操作数表达式（字符串）
  //   toType: 目标类型（DATE、TIMESTAMP或TIMESTAMP_WITH_LOCAL_TIME_ZONE）
  //   format: 日期时间格式字符串，用于解析字符串
  // 返回值：转换后的Druid表达式
  private static String castCharToDateTime(
      TimeZone timeZone,
      String operand,
      final SqlTypeName toType, String format) {
    // Cast strings to date times by parsing them from SQL format.
    // 使用Druid的timestamp_parse函数将字符串解析为时间戳
    final String timestampExpression =
        DruidExpressions.functionCall("timestamp_parse", // 调用Druid的timestamp_parse函数
            ImmutableList.of(operand, // 操作数（字符串）
                DruidExpressions.stringLiteral(format), // 格式字符串，如"yyyy-MM-dd"
                DruidExpressions.stringLiteral(timeZone.getID()))); // 时区ID，如"UTC"

    if (toType == SqlTypeName.DATE) {
      // case to date we need to floor to day first
      // 如果目标类型是DATE，需要向下取整到天
      return DruidExpressions.applyTimestampFloor(
          timestampExpression, // 时间戳表达式
          Period.days(1).toString(), // 向下取整到天，使用Joda的Period类
          "", // 无额外参数
          timeZone); // 使用指定的时区
    } else if (toType == SqlTypeName.TIMESTAMP
        || toType == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) {
      return timestampExpression; // 如果是TIMESTAMP或TIMESTAMP_WITH_LOCAL_TIME_ZONE，直接返回时间戳表达式
    } else {
      throw new IllegalStateException(
          DruidQuery.format("Unsupported DateTime type[%s]", toType)); // 不支持的日期时间类型，抛出异常
    }
  }

  // 将日期时间类型转换为字符串的私有方法
  // 参数说明：
  //   timeZone: 时区对象，用于格式化时间戳
  //   operand: 操作数表达式（日期时间）
  //   fromType: 源类型（DATE、TIMESTAMP或TIMESTAMP_WITH_LOCAL_TIME_ZONE）
  // 返回值：转换后的Druid表达式（字符串）
  private static String castDateTimeToChar(
      final TimeZone timeZone,
      final String operand,
      final SqlTypeName fromType) {
    return DruidExpressions.functionCall(
        "timestamp_format", // 调用Druid的timestamp_format函数
        ImmutableList.of(
            operand, // 操作数（日期时间）
            DruidExpressions.stringLiteral(dateTimeFormatString(fromType)), // 格式字符串，根据源类型获取
            DruidExpressions.stringLiteral(timeZone.getID()))); // 时区ID
  }

  // 根据SQL类型名称获取对应的日期时间格式字符串的公共静态方法
  // 参数说明：
  //   sqlTypeName: SQL类型名称
  // 返回值：对应的日期时间格式字符串，如果不是日期时间类型则返回null
  public static @Nullable String dateTimeFormatString(SqlTypeName sqlTypeName) {
    if (sqlTypeName == SqlTypeName.DATE) {
      return "yyyy-MM-dd"; // DATE类型的格式：年-月-日，如"2023-01-01"
    } else if (sqlTypeName == SqlTypeName.TIMESTAMP) {
      return "yyyy-MM-dd HH:mm:ss"; // TIMESTAMP类型的格式：年-月-日 时:分:秒，如"2023-01-01 12:30:45"
    } else if (sqlTypeName == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) {
      return "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"; // TIMESTAMP_WITH_LOCAL_TIME_ZONE类型的格式：ISO 8601格式，如"2023-01-01T12:30:45.123Z"
    } else {
      return null; // 不是日期时间类型，返回null
    }
  }
}