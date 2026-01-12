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
package org.apache.calcite.adapter.druid; // 声明包名，该类属于org.apache.calcite.adapter.druid包，用于Druid适配器相关的功能

import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类，用于处理日期时间相关的操作，特别是时区处理
import org.apache.calcite.avatica.util.TimeUnitRange; // 导入时间单位范围枚举，定义了各种时间单位如SECOND、MINUTE、HOUR等
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，用于表示关系代数中的数据类型信息
import org.apache.calcite.rex.RexCall; // 导入Rex调用节点类，表示函数调用表达式
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量类，表示常量值表达式
import org.apache.calcite.rex.RexNode; // 导入Rex节点基类，表示关系表达式树中的节点
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符接口，表示SQL中的操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表，包含所有标准SQL操作符的定义
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义了所有SQL数据类型

import com.google.common.collect.ImmutableMap; // 导入Google Guava库的不可变Map类，用于创建不可修改的映射

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空性注解，用于标记可能为null的返回值

import java.util.Map; // 导入Java标准库的Map接口，用于键值对映射
import java.util.TimeZone; // 导入Java标准库的时区类，用于处理时区相关的操作

/**
 * 时间提取操作符转换器，用于处理类似 {@code EXTRACT(timeUnit FROM arg)} 的表达式转换。
 *
 * <p>该类实现了DruidSqlOperatorConverter接口，负责将Calcite的EXTRACT操作符转换为Druid表达式。
 * EXTRACT操作符用于从日期时间值中提取特定的时间部分，如年、月、日、小时、分钟、秒等。
 *
 * <p>Unit（时间单位）可以是： SECOND（秒）、MINUTE（分钟）、HOUR（小时）、DAY（月中的日，1-31）、
 * DOW（星期几，1-7或0-6，取决于实现）、DOY（一年中的第几天，1-366）、
 * WEEK（一年中的周数）、MONTH（月份，1-12）、QUARTER（季度，1-4）或 YEAR（年）。
 *
 * <p>该转换器在Calcite查询引擎与Druid数据存储之间架起桥梁，使得Calcite生成的EXTRACT表达式
 * 能够被正确地转换为Druid可以理解和执行的原生表达式。
 */
public class ExtractOperatorConversion implements DruidSqlOperatorConverter { // 声明ExtractOperatorConversion类，实现DruidSqlOperatorConverter接口，用于将Calcite的EXTRACT操作符转换为Druid表达式
  private static final Map<TimeUnitRange, String> EXTRACT_UNIT_MAP = // 声明一个静态不可变映射，用于将Calcite的时间单位范围枚举映射到Druid支持的时间单位字符串
      ImmutableMap.<TimeUnitRange, String>builder() // 使用Guava的ImmutableMap构建器创建不可变映射
          .put(TimeUnitRange.SECOND, "SECOND") // 将Calcite的SECOND时间单位映射到Druid的"SECOND"字符串
          .put(TimeUnitRange.MINUTE, "MINUTE") // 将Calcite的MINUTE时间单位映射到Druid的"MINUTE"字符串
          .put(TimeUnitRange.HOUR, "HOUR") // 将Calcite的HOUR时间单位映射到Druid的"HOUR"字符串
          .put(TimeUnitRange.DAY, "DAY") // 将Calcite的DAY时间单位（月中的日）映射到Druid的"DAY"字符串
          .put(TimeUnitRange.DOW, "DOW") // 将Calcite的DOW时间单位（星期几）映射到Druid的"DOW"字符串
          .put(TimeUnitRange.DOY, "DOY") // 将Calcite的DOY时间单位（一年中的第几天）映射到Druid的"DOY"字符串
          .put(TimeUnitRange.WEEK, "WEEK") // 将Calcite的WEEK时间单位（一年中的周数）映射到Druid的"WEEK"字符串
          .put(TimeUnitRange.MONTH, "MONTH") // 将Calcite的MONTH时间单位（月份）映射到Druid的"MONTH"字符串
          .put(TimeUnitRange.QUARTER, "QUARTER") // 将Calcite的QUARTER时间单位（季度）映射到Druid的"QUARTER"字符串
          .put(TimeUnitRange.YEAR, "YEAR") // 将Calcite的YEAR时间单位（年）映射到Druid的"YEAR"字符串
          .build(); // 构建不可变映射

  @Override public SqlOperator calciteOperator() { // 重写DruidSqlOperatorConverter接口的calciteOperator方法，返回该转换器对应的Calcite SQL操作符
    return SqlStdOperatorTable.EXTRACT; // 返回标准SQL操作符表中的EXTRACT操作符，表示该转换器专门处理EXTRACT操作
  }

  @Override public @Nullable String toDruidExpression( // 重写DruidSqlOperatorConverter接口的toDruidExpression方法，将Calcite的EXTRACT表达式转换为Druid表达式字符串，@Nullable表示返回值可能为null
      RexNode rexNode, RelDataType rowType, DruidQuery query) { // 参数：rexNode表示要转换的Rex表达式节点，rowType表示输入行的数据类型信息，query表示Druid查询上下文对象

    final RexCall call = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型，因为EXTRACT是一个函数调用表达式
    final RexLiteral flag = (RexLiteral) call.getOperands().get(0); // 获取EXTRACT函数的第一个操作数，该操作数是一个字面量，表示要提取的时间单位（如SECOND、MINUTE等）
    final TimeUnitRange calciteUnit = (TimeUnitRange) flag.getValue(); // 从字面量中提取时间单位值，转换为TimeUnitRange枚举类型
    final RexNode arg = call.getOperands().get(1); // 获取EXTRACT函数的第二个操作数，该操作数是要提取时间的日期时间表达式

    final String input = DruidExpressions.toDruidExpression(arg, rowType, query); // 递归调用DruidExpressions工具类，将日期时间参数转换为Druid表达式字符串
    if (input == null) { // 如果转换失败（返回null），说明该表达式无法转换为Druid表达式
      return null; // 返回null表示转换失败，Druid无法处理该表达式
    }

    final String druidUnit = EXTRACT_UNIT_MAP.get(calciteUnit); // 从映射表中查找Calcite时间单位对应的Druid时间单位字符串
    if (druidUnit == null) { // 如果找不到对应的Druid时间单位（理论上不应该发生，因为映射表包含了所有标准时间单位）
      return null; // 返回null表示转换失败，Druid不支持该时间单位
    }

    final TimeZone tz = // 声明时区变量，用于确定时间提取时使用的时区
        arg.getType().getSqlTypeName() == SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE // 检查参数的数据类型是否为TIMESTAMP_WITH_LOCAL_TIME_ZONE（带本地时区的时间戳）
            ? TimeZone.getTimeZone(query.getConnectionConfig().timeZone()) // 如果是带本地时区的时间戳，使用查询连接配置中指定的时区
            : DateTimeUtils.UTC_ZONE; // 否则使用UTC时区（协调世界时），这是处理不带时区信息的日期时间的标准做法
    return DruidExpressions.applyTimeExtract(input, druidUnit, tz); // 调用DruidExpressions工具类的applyTimeExtract方法，生成Druid的时间提取表达式，传入输入表达式、时间单位和时区
  }
}
