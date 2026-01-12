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
package org.apache.calcite.adapter.druid; // 包声明：Druid适配器包，包含Druid数据源的适配器实现

import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类，用于时区处理
import org.apache.calcite.avatica.util.TimeUnitRange; // 导入时间单位范围枚举，表示时间精度（如DAY、HOUR等）
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示Calcite中的数据类型
import org.apache.calcite.rex.RexCall; // 导入Rex调用表达式，表示函数调用
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量表达式，表示常量值
import org.apache.calcite.rex.RexNode; // 导入Rex节点基类，表示关系表达式
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符接口
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含CEIL等内置函数
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，如TIMESTAMP_WITH_LOCAL_TIME_ZONE

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值

import java.util.TimeZone; // 导入时区类，用于处理时区相关的日期时间计算

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于空值检查

/**
 * DruidSqlOperatorConverter implementation that handles Ceil operations
 * conversions.
 * DruidSqlOperatorConverter实现类：负责处理Ceil（向上取整）操作的转换
 * 
 * 类作用说明：
 * 1. 实现DruidSqlOperatorConverter接口，将Calcite的CEIL函数转换为Druid表达式
 * 2. 支持两种CEIL操作：
 *    - CEIL(expr)：对数值表达式向上取整
 *    - CEIL(expr TO timeUnit)：对时间戳按指定时间单位向上取整（如按天、按小时等）
 * 3. 处理时区相关的逻辑，特别是TIMESTAMP_WITH_LOCAL_TIME_ZONE类型
 * 4. 是Calcite适配器模式的关键组件，实现SQL到Druid查询的转换
 */
public class CeilOperatorConversion implements DruidSqlOperatorConverter { // 类声明：实现DruidSqlOperatorConverter接口
  @Override public SqlOperator calciteOperator() { // 重写方法：返回此转换器处理的Calcite SQL操作符
    return SqlStdOperatorTable.CEIL; // 返回标准SQL操作符表中的CEIL函数，表示此转换器专门处理CEIL操作
  }

  @Override public @Nullable String toDruidExpression(RexNode rexNode, RelDataType rowType, // 重写方法：将Calcite的Rex表达式转换为Druid表达式字符串，@Nullable表示返回值可能为null
      DruidQuery query) { // 参数：rexNode-待转换的Rex表达式节点，rowType-行类型信息，query-Druid查询上下文
    final RexCall call = (RexCall) rexNode; // 将RexNode强转为RexCall，因为CEIL是一个函数调用
    final RexNode arg = call.getOperands().get(0); // 获取CEIL函数的第一个操作数（待向上取整的表达式）
    final String druidExpression = // 声明变量存储转换后的Druid表达式字符串
        DruidExpressions.toDruidExpression(arg, rowType, query); // 递归调用工具类将参数表达式转换为Druid表达式
    if (druidExpression == null) { // 如果转换失败（返回null），表示不支持该表达式
      return null; // 返回null表示无法转换为Druid表达式，查询优化器会放弃使用Druid
    } else if (call.getOperands().size() == 1) { // 如果CEIL函数只有一个操作数，处理数值向上取整情况：CEIL(expr)
      // case CEIL(expr) // 注释说明：处理一元CEIL函数，对数值表达式向上取整
      return  DruidQuery.format("ceil(%s)", druidExpression); // 格式化Druid表达式，调用Druid的ceil函数
    } else if (call.getOperands().size() == 2) { // 如果CEIL函数有两个操作数，处理时间戳向上取整情况：CEIL(expr TO timeUnit)
      // CEIL(expr TO timeUnit) // 注释说明：处理二元CEIL函数，对时间戳按指定时间单位向上取整
      final RexLiteral flag = (RexLiteral) call.getOperands().get(1); // 获取第二个操作数（时间单位标志），强转为字面量
      final TimeUnitRange timeUnit = // 声明变量存储时间单位范围
          requireNonNull((TimeUnitRange) flag.getValue()); // 从字面量中提取时间单位值，requireNonNull确保非空
      final Granularity.Type type = DruidDateTimeUtils.toDruidGranularity(timeUnit); // 将Calcite的时间单位转换为Druid的粒度类型
      if (type == null) { // 如果转换失败（不支持的时间单位）
        // Unknown Granularity bail out // 注释说明：遇到未知的粒度类型，放弃转换
        return null; // 返回null表示无法转换
      }
      String isoPeriodFormat = DruidDateTimeUtils.toISOPeriodFormat(type); // 将Druid粒度类型转换为ISO周期格式字符串
      if (isoPeriodFormat == null) { // 如果无法生成ISO周期格式
        return null; // 返回null表示无法转换
      }
      final TimeZone tz; // 声明时区变量，用于时间戳计算
      if (arg.getType().getSqlTypeName() == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE) { // 如果参数类型是带本地时区的时间戳
        tz = TimeZone.getTimeZone(query.getConnectionConfig().timeZone()); // 使用连接配置中指定的时区
      } else { // 参数类型是普通时间戳或其他类型
        tz = DateTimeUtils.UTC_ZONE; // 使用UTC时区
      }
      return DruidExpressions.applyTimestampCeil( // 调用工具类方法应用时间戳向上取整
          druidExpression, isoPeriodFormat, "", tz); // 参数：Druid表达式、ISO周期格式、空字符串（可选参数）、时区
    } else { // 如果操作数个数不是1或2（非法情况）
      return null; // 返回null表示不支持的操作数个数
    }
  }
}
