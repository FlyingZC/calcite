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
package org.apache.calcite.adapter.druid;

import org.apache.calcite.avatica.util.DateTimeUtils; // 引入Apache Calcite日期时间工具类，用于处理日期时间相关的操作
import org.apache.calcite.avatica.util.TimeUnitRange; // 引入时间单位范围枚举，表示年、月、日、小时等时间单位
import org.apache.calcite.rex.RexCall; // 引入RexCall类，表示关系表达式中的函数调用节点
import org.apache.calcite.rex.RexLiteral; // 引入RexLiteral类，表示关系表达式中的常量值节点
import org.apache.calcite.rex.RexNode; // 引入RexNode类，表示关系表达式的基类
import org.apache.calcite.sql.SqlKind; // 引入SqlKind枚举，定义SQL操作符的类型（如EXTRACT、FLOOR、CAST等）
import org.apache.calcite.sql.type.SqlTypeName; // 引入SqlTypeName枚举，定义SQL数据类型名称

import com.fasterxml.jackson.core.JsonGenerator; // 引入Jackson的JsonGenerator类，用于生成JSON格式的输出
import com.google.common.collect.ImmutableSet; // 引入Google Guava的不可变集合类，用于创建不可修改的集合
import com.google.common.collect.Sets; // 引入Google Guava的集合工具类，提供集合操作的静态方法

import org.checkerframework.checker.nullness.qual.Nullable; // 引入注解，标记可能为null的返回值

import java.io.IOException; // 引入IO异常类，处理输入输出操作时的异常
import java.util.Locale; // 引入Locale类，表示特定的地理、政治或文化区域，用于本地化
import java.util.TimeZone; // 引入TimeZone类，表示时区偏移量

import static org.apache.calcite.adapter.druid.DruidQuery.writeFieldIf; // 引入静态方法，用于在JSON生成器中有条件地写入字段
import static org.apache.calcite.util.DateTimeStringUtils.ISO_DATETIME_FRACTIONAL_SECOND_FORMAT; // 引入常量，表示ISO格式的日期时间字符串，包含毫秒部分

/**
 * Druid时间格式提取函数的实现类
 *
 * <p>该类实现了Calcite与Druid之间的时间提取函数转换功能，用于将Calcite的时间提取表达式转换为Druid可识别的格式
 * 这些函数根据给定的格式字符串、时区和区域设置，返回格式化后的维度值
 *
 * <p>对于__time维度值，此函数会根据聚合粒度对时间值进行分桶（bucketing）处理并格式化输出
 * 主要用于处理时间相关的SQL操作，如EXTRACT（提取时间部分）、FLOOR（时间向下取整）和CAST（类型转换）
 */
public class TimeExtractionFunction implements ExtractionFunction { // 定义TimeExtractionFunction类，实现ExtractionFunction接口

  // 定义静态常量：有效的时间提取单位集合，用于EXTRACT函数
  private static final ImmutableSet<TimeUnitRange> VALID_TIME_EXTRACT = // 创建不可变的枚举集合，存储支持的时间提取单位
      Sets.immutableEnumSet(TimeUnitRange.YEAR, // 添加年份单位
          TimeUnitRange.MONTH, // 添加月份单位
          TimeUnitRange.DAY, // 添加日期单位
          TimeUnitRange.WEEK, // 添加周单位
          TimeUnitRange.HOUR, // 添加小时单位
          TimeUnitRange.MINUTE, // 添加分钟单位
          TimeUnitRange.SECOND); // 添加秒单位

  // 定义静态常量：有效的时间向下取整单位集合，用于FLOOR函数
  private static final ImmutableSet<TimeUnitRange> VALID_TIME_FLOOR = // 创建不可变的枚举集合，存储支持的时间取整单位
      Sets.immutableEnumSet(TimeUnitRange.YEAR, // 添加年份单位
          TimeUnitRange.QUARTER, // 添加季度单位（与EXTRACT相比，FLOOR支持季度）
          TimeUnitRange.MONTH, // 添加月份单位
          TimeUnitRange.DAY, // 添加日期单位
          TimeUnitRange.WEEK, // 添加周单位
          TimeUnitRange.HOUR, // 添加小时单位
          TimeUnitRange.MINUTE, // 添加分钟单位
          TimeUnitRange.SECOND); // 添加秒单位

  // 成员变量：时间格式字符串，用于定义日期时间的输出格式
  private final String format; // 存储日期时间的格式化模式，如"yyyy-MM-dd HH:mm:ss"或"d"、"M"等
  // 成员变量：时间粒度对象，用于指定时间分桶的粒度级别
  private final Granularity granularity; // 存储Granularity对象，表示时间聚合的粒度（如按天、按月、按小时等）
  // 成员变量：时区标识字符串，用于指定时间格式化所使用的时区
  private final String timeZone; // 存储时区ID，如"UTC"、"America/Los_Angeles"等
  // 成员变量：本地化标识字符串，用于指定时间格式化所使用的区域设置
  private final String local; // 存储Locale的语言标签，如"en-US"、"zh-CN"等，影响输出语言的格式

  // 构造方法：创建TimeExtractionFunction实例，初始化所有成员变量
  public TimeExtractionFunction(String format, Granularity granularity, String timeZone, // 定义构造方法，接收格式字符串、粒度对象、时区和本地化参数
      String local) { // 接收本地化标识字符串参数
    this.format = format; // 将传入的格式字符串赋值给成员变量format，用于后续的时间格式化
    this.granularity = granularity; // 将传入的粒度对象赋值给成员变量granularity，用于时间分桶
    this.timeZone = timeZone; // 将传入的时区字符串赋值给成员变量timeZone，用于时区转换
    this.local = local; // 将传入的本地化字符串赋值给成员变量local，用于区域化格式
  }

  // 重写接口方法：将TimeExtractionFunction对象序列化为JSON格式并写入JsonGenerator
  @Override public void write(JsonGenerator generator) throws IOException { // 实现ExtractionFunction接口的write方法，接收JsonGenerator对象，可能抛出IO异常
    generator.writeStartObject(); // 开始写入JSON对象，输出"{"符号
    generator.writeStringField("type", "timeFormat"); // 写入type字段，固定值为"timeFormat"，标识这是一个时间格式提取函数
    writeFieldIf(generator, "format", format); // 如果format不为null，则写入format字段及其值
    writeFieldIf(generator, "granularity", granularity); // 如果granularity不为null，则写入granularity字段及其值
    writeFieldIf(generator, "timeZone", timeZone); // 如果timeZone不为null，则写入timeZone字段及其值
    writeFieldIf(generator, "locale", local); // 如果local不为null，则写入locale字段及其值
    generator.writeEndObject(); // 结束JSON对象写入，输出"}"符号
  }

  // Getter方法：获取时间格式字符串
  public String getFormat() { // 定义公共方法，返回格式字符串
    return format; // 返回成员变量format的值
  }
  // Getter方法：获取时间粒度对象
  public Granularity getGranularity() { // 定义公共方法，返回粒度对象
    return granularity; // 返回成员变量granularity的值
  }


  /**
   * 创建默认的时间格式提取函数
   * 此方法用于创建一个使用ISO标准格式的时间提取函数，不指定粒度和本地化
   *
   * @param timeZone 时区标识字符串，如"UTC"或"America/Los_Angeles"
   * @return 返回新创建的TimeExtractionFunction实例，使用ISO日期时间格式（包含毫秒）
   */
  public static TimeExtractionFunction createDefault(String timeZone) { // 定义静态工厂方法，接收时区参数
    return new TimeExtractionFunction(ISO_DATETIME_FRACTIONAL_SECOND_FORMAT, // 使用ISO标准日期时间格式（包含毫秒部分）
        null, // 不指定粒度，传入null
        timeZone, // 使用传入的时区
        null); // 不指定本地化，传入null
  }

  /**
   * 根据给定的粒度创建时间格式提取函数
   * 此方法用于将Calcite的EXTRACT函数转换为Druid的时间提取函数
   * 根据不同的时间粒度类型，使用对应的日期格式字符串
   *
   * @param granularity 要应用到列的时间粒度对象，指定提取的时间单位
   * @param timeZone 时区标识字符串，用于时间格式化
   * @return 返回对应粒度输入单位的时间提取函数，支持的粒度见{@link TimeExtractionFunction#VALID_TIME_EXTRACT}
   * @throws IllegalArgumentException 当粒度类型不被支持时抛出异常
   */
  public static TimeExtractionFunction createExtractFromGranularity( // 定义静态工厂方法，接收粒度和时区参数
      Granularity granularity, String timeZone) { // 接收Granularity对象和时区字符串
    final String local = Locale.US.toLanguageTag(); // 创建本地化标识，使用美国英语（en-US），确保格式一致性
    switch (granularity.getType()) { // 根据粒度类型进行分支判断
    case DAY: // 如果粒度是按天
      return new TimeExtractionFunction("d", null, timeZone, local); // 创建提取日期（1-31）的函数，格式为"d"
    case MONTH: // 如果粒度是按月
      return new TimeExtractionFunction("M", null, timeZone, local); // 创建提取月份（1-12）的函数，格式为"M"
    case YEAR: // 如果粒度是按年
      return new TimeExtractionFunction("yyyy", null, timeZone, local); // 创建提取年份的函数，格式为"yyyy"
    case WEEK: // 如果粒度是按周
      return new TimeExtractionFunction("w", null, timeZone, local); // 创建提取周数（1-53）的函数，格式为"w"
    case HOUR: // 如果粒度是按小时
      return new TimeExtractionFunction("H", null, timeZone, local); // 创建提取小时（0-23）的函数，格式为"H"
    case MINUTE: // 如果粒度是按分钟
      return new TimeExtractionFunction("m", null, timeZone, local); // 创建提取分钟（0-59）的函数，格式为"m"
    case SECOND: // 如果粒度是按秒
      return new TimeExtractionFunction("s", null, timeZone, local); // 创建提取秒（0-59）的函数，格式为"s"
    default: // 如果粒度类型不在上述范围内
      throw new IllegalArgumentException("Granularity [" + granularity + "] is not supported"); // 抛出非法参数异常，提示不支持的粒度
    }
  }

  /**
   * 使用给定的粒度创建时间向下取整的时间提取函数
   * 此方法用于将Calcite的FLOOR函数转换为Druid的时间提取函数
   * 与EXTRACT不同，FLOOR会保留完整的ISO时间格式，同时应用粒度进行时间分桶
   *
   * @param granularity 要应用到列的时间粒度对象，指定时间取整的单位
   * @param timeZone 时区标识字符串，用于时间格式化
   * @return 返回时间提取函数实例，使用ISO格式并应用粒度；如果粒度不支持则返回null
   */
  public static TimeExtractionFunction createFloorFromGranularity( // 定义静态工厂方法，接收粒度和时区参数
      Granularity granularity, String timeZone) { // 接收Granularity对象和时区字符串
    return new TimeExtractionFunction(ISO_DATETIME_FRACTIONAL_SECOND_FORMAT, granularity, timeZone, // 创建新实例，使用ISO完整格式，传入粒度对象和时区
        Locale.ROOT.toLanguageTag()); // 使用ROOT本地化（语言无关），确保格式在不同环境下一致
  }

  /**
   * 判断RexCall是否包含有效的EXTRACT单位，可以被序列化到Druid
   * 此方法用于验证Calcite的EXTRACT表达式是否可以转换为Druid的时间提取函数
   *
   * @param rexNode 表示EXTRACT表达式的关系表达式节点（RexNode）
   * @return 如果提取单位有效（在VALID_TIME_EXTRACT集合中）则返回true，否则返回false
   */

  public static boolean isValidTimeExtract(RexNode rexNode) { // 定义静态方法，接收RexNode参数，返回布尔值
    final RexCall call = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型，表示函数调用节点
    if (call.getKind() != SqlKind.EXTRACT || call.getOperands().size() != 2) { // 检查是否为EXTRACT操作且操作数数量为2
      return false; // 如果不是EXTRACT或操作数数量不正确，返回false
    }
    final RexLiteral flag = (RexLiteral) call.operands.get(0); // 获取第一个操作数（时间单位标志），转换为RexLiteral常量
    final TimeUnitRange timeUnit = (TimeUnitRange) flag.getValue(); // 从RexLiteral中提取TimeUnitRange枚举值
    return timeUnit != null && VALID_TIME_EXTRACT.contains(timeUnit); // 检查时间单位不为null且在有效提取单位集合中
  }

  /**
   * 判断RexCall是否包含有效的FLOOR单位，可以被序列化到Druid
   * 此方法用于验证Calcite的FLOOR表达式是否可以转换为Druid的时间提取函数
   * FLOOR函数用于将时间向下取整到指定的粒度级别
   *
   * @param rexNode 表示FLOOR表达式的关系表达式节点（RexNode）
   * @return 如果FLOOR单位有效（在VALID_TIME_FLOOR集合中）则返回true，否则返回false
   */
  public static boolean isValidTimeFloor(RexNode rexNode) { // 定义静态方法，接收RexNode参数，返回布尔值
    if (rexNode.getKind() != SqlKind.FLOOR) { // 首先检查节点类型是否为FLOOR操作
      return false; // 如果不是FLOOR操作，直接返回false
    }
    final RexCall call = (RexCall) rexNode; // 将RexNode强制转换为RexCall类型，表示函数调用节点
    if (call.operands.size() != 2) { // 检查操作数数量是否为2（FLOOR需要两个参数：时间值和时间单位）
      return false; // 如果操作数数量不正确，返回false
    }
    final RexLiteral flag = (RexLiteral) call.operands.get(1); // 获取第二个操作数（时间单位标志），转换为RexLiteral常量
    final TimeUnitRange timeUnit = (TimeUnitRange) flag.getValue(); // 从RexLiteral中提取TimeUnitRange枚举值
    return timeUnit != null && VALID_TIME_FLOOR.contains(timeUnit); // 检查时间单位不为null且在有效取整单位集合中
  }

  /** 将CAST表达式转换为Druid时间提取函数，如果无法转换则返回null
   * 此方法用于处理Calcite中的类型转换操作，将日期时间类型转换为字符串或其他时间类型
   * 根据源类型和目标类型的不同组合，生成相应的时间提取函数
   *
   * @param rexNode 表示CAST表达式的关系表达式节点（RexNode），必须是SqlKind.CAST类型
   * @param timeZone 时区对象，用于处理TIMESTAMP_WITH_LOCAL_TIME_ZONE类型的转换
   * @return 返回对应的时间提取函数实例；如果转换不支持则返回null
   */
  public static @Nullable TimeExtractionFunction translateCastToTimeExtract(RexNode rexNode, // 定义静态方法，接收RexNode和TimeZone参数，返回可空的时间提取函数
      TimeZone timeZone) { // 接收时区对象参数
    assert rexNode.getKind() == SqlKind.CAST; // 断言传入的节点必须是CAST操作类型
    final RexCall rexCall = (RexCall) rexNode; // 将RexNode转换为RexCall类型，表示CAST函数调用
    final String castFormat = DruidSqlCastConverter // 根据目标类型获取对应的日期时间格式字符串
        .dateTimeFormatString(rexCall.getType().getSqlTypeName()); // 调用DruidSqlCastConverter的静态方法获取格式
    final String timeZoneId = timeZone == null ? null : timeZone.getID(); // 获取时区ID字符串，如果时区为null则返回null
    if (castFormat == null) { // 检查格式字符串是否为null（表示不支持该类型的转换）
      // unknown format // 未知格式注释
      return null; // 如果格式未知，返回null表示无法转换
    }
    SqlTypeName fromType = rexCall.getOperands().get(0).getType().getSqlTypeName(); // 获取源数据类型（CAST操作前的类型）
    SqlTypeName toType = rexCall.getType().getSqlTypeName(); // 获取目标数据类型（CAST操作后的类型）
    String granularityTZId; // 定义变量，存储粒度时区ID
    switch (fromType) { // 根据源类型进行分支处理
    case DATE: // 如果源类型是DATE（日期）
    case TIMESTAMP: // 如果源类型是TIMESTAMP（时间戳）
      granularityTZId = DateTimeUtils.UTC_ZONE.getID(); // 使用UTC时区作为粒度时区
      break; // 跳出switch语句
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE: // 如果源类型是TIMESTAMP_WITH_LOCAL_TIME_ZONE（带本地时区的时间戳）
      granularityTZId = timeZoneId; // 使用传入的时区ID作为粒度时区
      break; // 跳出switch语句
    default: // 如果源类型不在上述范围内
      return null; // 返回null表示不支持该源类型的转换
    }
    switch (toType) { // 根据目标类型进行分支处理
    case DATE: // 如果目标类型是DATE（日期）
      return new TimeExtractionFunction(castFormat, // 创建时间提取函数，使用获取的格式字符串
          Granularities.createGranularity(TimeUnitRange.DAY, granularityTZId), // 创建按天粒度的Granularity对象
          DateTimeUtils.UTC_ZONE.getID(), Locale.ENGLISH.toString()); // 使用UTC时区和英语本地化
    case TIMESTAMP: // 如果目标类型是TIMESTAMP（时间戳）
      // date -> timestamp: UTC // 注释：日期转时间戳使用UTC时区
      // timestamp -> timestamp: UTC // 注释：时间戳转时间戳使用UTC时区
      // timestamp with local time zone -> granularityTZId // 注释：带本地时区的时间戳转换使用粒度时区
      return new TimeExtractionFunction( // 创建时间提取函数
          castFormat, null, granularityTZId, Locale.ENGLISH.toString()); // 使用格式字符串、无粒度、粒度时区和英语本地化
    case TIMESTAMP_WITH_LOCAL_TIME_ZONE: // 如果目标类型是TIMESTAMP_WITH_LOCAL_TIME_ZONE（带本地时区的时间戳）
      return new TimeExtractionFunction( // 创建时间提取函数
          castFormat, null, DateTimeUtils.UTC_ZONE.getID(), Locale.ENGLISH.toString()); // 使用格式字符串、无粒度、UTC时区和英语本地化
    default: // 如果目标类型不在上述范围内
      return null; // 返回null表示不支持该目标类型的转换
    }
  }

} // 类定义结束
