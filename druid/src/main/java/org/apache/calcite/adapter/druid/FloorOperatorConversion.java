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
package org.apache.calcite.adapter.druid; // 声明包名，表示该类位于org.apache.calcite.adapter.druid包下，属于Calcite的Druid适配器模块

import org.apache.calcite.avatica.util.DateTimeUtils; // 导入Avatica的日期时间工具类，用于处理时区相关的日期时间操作
import org.apache.calcite.rel.type.RelDataType; // 导入Calcite的关系数据类型接口，用于表示关系表达式中的数据类型
import org.apache.calcite.rex.RexCall; // 导入RexCall类，表示关系表达式中的函数调用节点
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示关系表达式树的基类
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符的抽象基类
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含FLOOR等标准SQL函数
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，定义所有SQL标准数据类型名称

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值

import java.util.TimeZone; // 导入Java的TimeZone类，用于处理时区信息

/**
 * DruidSqlOperatorConverter implementation that handles Floor operations
 * conversions.
 * DruidSqlOperatorConverter接口的实现类，用于处理FLOOR操作的转换
 * 该类负责将Calcite的FLOOR函数调用转换为Druid查询引擎能够理解的表达式
 * 支持两种FLOOR操作：1) FLOOR(expr) - 向下取整 2) FLOOR(expr TO timeUnit) - 按时间单位向下取整
 */
public class FloorOperatorConversion implements DruidSqlOperatorConverter { // 定义FloorOperatorConversion类，实现DruidSqlOperatorConverter接口
  @Override public SqlOperator calciteOperator() { // 重写接口方法，返回该转换器处理的Calcite SQL操作符
    return SqlStdOperatorTable.FLOOR; // 返回标准SQL操作符表中的FLOOR操作符，表示该转换器专门处理FLOOR函数
  }

  @Override public @Nullable String toDruidExpression(RexNode rexNode, RelDataType rowType, // 重写接口方法，将Calcite的FLOOR表达式转换为Druid表达式，@Nullable表示返回值可能为null
      DruidQuery druidQuery) { // druidQuery参数提供Druid查询上下文信息，包含连接配置等
    final RexCall call = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型，因为FLOOR是一个函数调用操作
    final RexNode arg = call.getOperands().get(0); // 获取FLOOR函数的第一个操作数（参数），即需要向下取值的表达式
    final String druidExpression = // 声明变量用于存储转换后的Druid表达式
        DruidExpressions.toDruidExpression(arg, rowType, druidQuery); // 递归调用工具方法将第一个参数转换为Druid表达式
    if (druidExpression == null) { // 检查表达式转换是否失败
      return null; // 如果转换失败，返回null表示无法将该FLOOR操作转换为Druid表达式
    } else if (call.getOperands().size() == 1) { // 检查FLOOR函数只有一个参数的情况，即FLOOR(expr)形式
      // case FLOOR(expr) // 注释说明：处理单参数的FLOOR表达式，用于数值向下取整
      return  DruidQuery.format("floor(%s)", druidExpression); // 使用Druid的floor函数格式化表达式，生成Druid的floor函数调用
    } else if (call.getOperands().size() == 2) { // 检查FLOOR函数有两个参数的情况，即FLOOR(expr TO timeUnit)形式
      // FLOOR(expr TO timeUnit) // 注释说明：处理双参数的FLOOR表达式，用于按时间单位向下取整
      final TimeZone tz; // 声明时区变量，用于确定时间计算使用的时区
      if (arg.getType().getSqlTypeName() == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) { // 检查参数类型是否为带本地时区的时间戳
        tz = TimeZone.getTimeZone(druidQuery.getConnectionConfig().timeZone()); // 如果是带本地时区的时间戳，使用连接配置中指定的时区
      } else { // 如果不是带本地时区的时间戳类型
        tz = DateTimeUtils.UTC_ZONE; // 使用UTC时区作为默认时区
      }
      final Granularity granularity = DruidDateTimeUtils // 声明粒度变量，用于存储提取的时间粒度
          .extractGranularity(call, tz.getID()); // 从FLOOR函数调用中提取时间粒度信息，传入时区ID用于正确解析
      if (granularity == null) { // 检查是否成功提取到时间粒度
        return null; // 如果无法提取粒度信息，返回null表示转换失败
      }
      String isoPeriodFormat = DruidDateTimeUtils.toISOPeriodFormat(granularity.getType()); // 将粒度类型转换为ISO 8601周期格式字符串
      if (isoPeriodFormat == null) { // 检查ISO周期格式转换是否成功
        return null; // 如果转换失败，返回null表示无法生成Druid表达式
      }
      return DruidExpressions.applyTimestampFloor( // 调用工具方法应用时间戳向下取整操作
          druidExpression, // 传入Druid表达式字符串
          isoPeriodFormat, // 传入ISO周期格式字符串（如"PT1H"表示1小时）
          "", // 传入空字符串作为时区偏移量（由tz参数处理）
          tz); // 传入时区对象用于时间计算
    } else { // 如果FLOOR函数的参数个数不是1或2
      return null; // 返回null表示不支持的操作，无法转换为Druid表达式
    }
  }
} // 类定义结束
