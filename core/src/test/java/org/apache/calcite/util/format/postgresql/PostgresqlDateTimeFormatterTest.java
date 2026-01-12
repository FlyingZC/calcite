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
// Apache许可证头部声明，说明该文件遵循Apache 2.0许可证
package org.apache.calcite.util.format.postgresql; // 声明包名，该类位于org.apache.calcite.util.format.postgresql包下

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法
import org.junit.jupiter.params.ParameterizedTest; // 导入JUnit 5的参数化测试注解
import org.junit.jupiter.params.provider.ValueSource; // 导入参数化测试的值源注解

import java.time.LocalDateTime; // 导入Java 8的LocalDateTime类，表示不带时区的日期时间
import java.time.ZoneId; // 导入Java 8的ZoneId类，表示时区ID
import java.time.ZonedDateTime; // 导入Java 8的ZonedDateTime类，表示带时区的日期时间
import java.time.temporal.ChronoField; // 导入时间字段枚举，用于访问日期时间的各个字段
import java.util.Locale; // 导入Java的Locale类，用于本地化设置

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器的is方法，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具类
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit 5的fail方法，用于测试失败

/**
 * Unit test for {@link PostgresqlDateTimeFormatter}.
 * // PostgresqlDateTimeFormatter的单元测试类
 * // 该测试类用于验证PostgreSQL风格的日期时间格式化器的各种功能
 * // 包括日期时间的格式化（to_char）和解析（to_timestamp）功能
 * // 测试覆盖了各种日期时间格式模式，如年、月、日、时、分、秒、毫秒等
 */
public class PostgresqlDateTimeFormatterTest { // 定义PostgresqlDateTimeFormatterTest测试类
  private static final ZoneId TIME_ZONE; // 声明静态常量时区ID，用于所有测试中的日期时间操作
  static { // 静态初始化块，用于初始化TIME_ZONE常量
    ZoneId timeZone; // 声明临时变量timeZone
    try { // 尝试获取美国芝加哥时区
      timeZone = ZoneId.of("America/Chicago"); // 使用时区ID字符串创建ZoneId对象
    } catch (Exception e) { // 如果芝加哥时区不可用，则捕获异常
      timeZone = ZoneId.systemDefault(); // 使用系统默认时区作为备选
    }
    TIME_ZONE = timeZone; // 将初始化的时区赋值给静态常量TIME_ZONE
  }

  private static final ZonedDateTime DAY_1_CE = createDateTime(1, 1, 1, 0, 0, 0, 0); // 公元1年1月1日0点0分0秒0纳秒，作为公元第一天的基准日期时间
  private static final ZonedDateTime APR_17_2024 = createDateTime(2024, 4, 17, 0, 0, 0, 0); // 2024年4月17日0点0分0秒0纳秒，用于测试的特定日期
  private static final ZonedDateTime JAN_1_2001 = createDateTime(2001, 1, 1, 0, 0, 0, 0); // 2001年1月1日0点0分0秒0纳秒，用于测试的特定日期
  private static final ZonedDateTime JAN_1_2024 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 2024年1月1日0点0分0秒0纳秒，用于测试的特定日期

  private String toCharUs(String pattern, ZonedDateTime dateTime) { // 辅助方法：使用美国英语地区设置将日期时间格式化为字符串
    final CompiledDateTimeFormat dateTimeFormat = // 声明编译后的日期时间格式对象
        PostgresqlDateTimeFormatter.compilePattern(pattern); // 调用PostgresqlDateTimeFormatter编译格式模式字符串
    return dateTimeFormat.formatDateTime(dateTime, Locale.US); // 使用编译后的格式对象和美式英语地区设置格式化日期时间
  }

  private String toCharFrench(String pattern, ZonedDateTime dateTime) { // 辅助方法：使用法语地区设置将日期时间格式化为字符串
    final CompiledDateTimeFormat dateTimeFormat = // 声明编译后的日期时间格式对象
        PostgresqlDateTimeFormatter.compilePattern(pattern); // 调用PostgresqlDateTimeFormatter编译格式模式字符串
    return dateTimeFormat.formatDateTime(dateTime, Locale.FRENCH); // 使用编译后的格式对象和法语地区设置格式化日期时间
  }

  private ZonedDateTime toTimestamp(String input, String format) throws Exception { // 辅助方法：将字符串解析为日期时间对象
    final CompiledDateTimeFormat dateTimeFormat = // 声明编译后的日期时间格式对象
        PostgresqlDateTimeFormatter.compilePattern(format); // 调用PostgresqlDateTimeFormatter编译格式模式字符串
    return dateTimeFormat.parseDateTime(input, TIME_ZONE, Locale.US); // 使用编译后的格式对象、预设时区和美式英语地区设置解析输入字符串
  }

  @ParameterizedTest // 参数化测试注解，允许使用不同的参数运行同一个测试方法
  @ValueSource(strings = {"HH12", "HH"}) // 参数源：测试HH12和HH两种格式模式
  void testHH12(String pattern) { // 测试12小时制小时格式（HH12或HH）
    final ZonedDateTime midnight = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建午夜0点的时间对象
    final ZonedDateTime morning = createDateTime(2024, 1, 1, 6, 0, 0, 0); // 创建早上6点的时间对象
    final ZonedDateTime noon = createDateTime(2024, 1, 1, 12, 0, 0, 0); // 创建中午12点的时间对象
    final ZonedDateTime evening = createDateTime(2024, 1, 1, 18, 0, 0, 0); // 创建傍晚18点的时间对象

    assertThat(toCharUs(pattern, midnight), is("12")); // 断言：午夜应该格式化为12（12小时制）
    assertThat(toCharUs(pattern, morning), is("06")); // 断言：早上6点应该格式化为06
    assertThat(toCharUs(pattern, noon), is("12")); // 断言：中午12点应该格式化为12
    assertThat(toCharUs(pattern, evening), is("06")); // 断言：傍晚18点应该格式化为06（18-12=6）
    assertThat(toCharUs("FM" + pattern, midnight), is("12")); // 断言：使用FM模式（填充模式）午夜应该格式化为12（无前导零）
    assertThat(toCharUs("FM" + pattern, morning), is("6")); // 断言：使用FM模式早上6点应该格式化为6（无前导零）
    assertThat(toCharUs("FM" + pattern, noon), is("12")); // 断言：使用FM模式中午12点应该格式化为12
    assertThat(toCharUs("FM" + pattern, evening), is("6")); // 断言：使用FM模式傍晚18点应该格式化为6

    final ZonedDateTime hourOne = createDateTime(2024, 1, 1, 1, 0, 0, 0); // 创建凌晨1点的时间对象
    final ZonedDateTime hourTwo = createDateTime(2024, 1, 1, 2, 0, 0, 0); // 创建凌晨2点的时间对象
    final ZonedDateTime hourThree = createDateTime(2024, 1, 1, 3, 0, 0, 0); // 创建凌晨3点的时间对象
    assertThat(toCharUs(pattern + "TH", midnight), is("12TH")); // 断言：使用TH后缀（序数词大写）午夜应该格式化为12TH
    assertThat(toCharUs(pattern + "TH", hourOne), is("01ST")); // 断言：使用TH后缀凌晨1点应该格式化为01ST
    assertThat(toCharUs(pattern + "TH", hourTwo), is("02ND")); // 断言：使用TH后缀凌晨2点应该格式化为02ND
    assertThat(toCharUs(pattern + "TH", hourThree), is("03RD")); // 断言：使用TH后缀凌晨3点应该格式化为03RD
    assertThat(toCharUs(pattern + "th", midnight), is("12th")); // 断言：使用th后缀（序数词小写）午夜应该格式化为12th
    assertThat(toCharUs(pattern + "th", hourOne), is("01st")); // 断言：使用th后缀凌晨1点应该格式化为01st
    assertThat(toCharUs(pattern + "th", hourTwo), is("02nd")); // 断言：使用th后缀凌晨2点应该格式化为02nd
    assertThat(toCharUs(pattern + "th", hourThree), is("03rd")); // 断言：使用th后缀凌晨3点应该格式化为03rd

    assertThat(toCharUs("FM" + pattern + "th", hourTwo), is("2nd")); // 断言：使用FM和th后缀凌晨2点应该格式化为2nd（无前导零）
  }

  @Test // 测试方法注解
  void testHH24() { // 测试24小时制小时格式（HH24）
    final ZonedDateTime midnight = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建午夜0点的时间对象
    final ZonedDateTime morning = createDateTime(2024, 1, 1, 6, 0, 0, 0); // 创建早上6点的时间对象
    final ZonedDateTime noon = createDateTime(2024, 1, 1, 12, 0, 0, 0); // 创建中午12点的时间对象
    final ZonedDateTime evening = createDateTime(2024, 1, 1, 18, 0, 0, 0); // 创建傍晚18点的时间对象

    assertThat(toCharUs("HH24", midnight), is("00")); // 断言：午夜应该格式化为00（24小时制）
    assertThat(toCharUs("HH24", morning), is("06")); // 断言：早上6点应该格式化为06
    assertThat(toCharUs("HH24", noon), is("12")); // 断言：中午12点应该格式化为12
    assertThat(toCharUs("HH24", evening), is("18")); // 断言：傍晚18点应该格式化为18
    assertThat(toCharUs("FMHH24", midnight), is("0")); // 断言：使用FM模式午夜应该格式化为0（无前导零）
    assertThat(toCharUs("FMHH24", morning), is("6")); // 断言：使用FM模式早上6点应该格式化为6
    assertThat(toCharUs("FMHH24", noon), is("12")); // 断言：使用FM模式中午12点应该格式化为12
    assertThat(toCharUs("FMHH24", evening), is("18")); // 断言：使用FM模式傍晚18点应该格式化为18

    final ZonedDateTime hourOne = createDateTime(2024, 1, 1, 1, 0, 0, 0); // 创建凌晨1点的时间对象
    final ZonedDateTime hourTwo = createDateTime(2024, 1, 1, 2, 0, 0, 0); // 创建凌晨2点的时间对象
    final ZonedDateTime hourThree = createDateTime(2024, 1, 1, 3, 0, 0, 0); // 创建凌晨3点的时间对象
    assertThat(toCharUs("HH24TH", midnight), is("00TH")); // 断言：使用TH后缀午夜应该格式化为00TH
    assertThat(toCharUs("HH24TH", hourOne), is("01ST")); // 断言：使用TH后缀凌晨1点应该格式化为01ST
    assertThat(toCharUs("HH24TH", hourTwo), is("02ND")); // 断言：使用TH后缀凌晨2点应该格式化为02ND
    assertThat(toCharUs("HH24TH", hourThree), is("03RD")); // 断言：使用TH后缀凌晨3点应该格式化为03RD
    assertThat(toCharUs("HH24th", midnight), is("00th")); // 断言：使用th后缀午夜应该格式化为00th
    assertThat(toCharUs("HH24th", hourOne), is("01st")); // 断言：使用th后缀凌晨1点应该格式化为01st
    assertThat(toCharUs("HH24th", hourTwo), is("02nd")); // 断言：使用th后缀凌晨2点应该格式化为02nd
    assertThat(toCharUs("HH24th", hourThree), is("03rd")); // 断言：使用th后缀凌晨3点应该格式化为03rd

    assertThat(toCharUs("FMHH24th", hourTwo), is("2nd")); // 断言：使用FM和th后缀凌晨2点应该格式化为2nd
  }

  @Test // 测试方法注解
  void testMI() { // 测试分钟格式（MI）
    final ZonedDateTime minute0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0分钟的时间对象
    final ZonedDateTime minute2 = createDateTime(2024, 1, 1, 0, 2, 0, 0); // 创建2分钟的时间对象
    final ZonedDateTime minute15 = createDateTime(2024, 1, 1, 0, 15, 0, 0); // 创建15分钟的时间对象

    assertThat(toCharUs("MI", minute0), is("00")); // 断言：0分钟应该格式化为00
    assertThat(toCharUs("MI", minute2), is("02")); // 断言：2分钟应该格式化为02
    assertThat(toCharUs("MI", minute15), is("15")); // 断言：15分钟应该格式化为15

    assertThat(toCharUs("FMMI", minute0), is("0")); // 断言：使用FM模式0分钟应该格式化为0
    assertThat(toCharUs("FMMI", minute2), is("2")); // 断言：使用FM模式2分钟应该格式化为2
    assertThat(toCharUs("FMMI", minute15), is("15")); // 断言：使用FM模式15分钟应该格式化为15

    assertThat(toCharUs("MITH", minute0), is("00TH")); // 断言：使用TH后缀0分钟应该格式化为00TH
    assertThat(toCharUs("MITH", minute2), is("02ND")); // 断言：使用TH后缀2分钟应该格式化为02ND
    assertThat(toCharUs("MITH", minute15), is("15TH")); // 断言：使用TH后缀15分钟应该格式化为15TH
    assertThat(toCharUs("MIth", minute0), is("00th")); // 断言：使用th后缀0分钟应该格式化为00th
    assertThat(toCharUs("MIth", minute2), is("02nd")); // 断言：使用th后缀2分钟应该格式化为02nd
    assertThat(toCharUs("MIth", minute15), is("15th")); // 断言：使用th后缀15分钟应该格式化为15th

    assertThat(toCharUs("FMMIth", minute2), is("2nd")); // 断言：使用FM和th后缀2分钟应该格式化为2nd
    assertThat(toCharUs("FMMInd", minute2), is("2nd")); // 断言：使用FM和nd后缀2分钟应该格式化为2nd
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"SSSSS", "SSSS"}) // 参数源：测试SSSSS和SSSS两种格式模式（一天中的秒数）
  void testSSSSS(String pattern) { // 测试一天中的秒数格式（SSSSS或SSSS）
    final ZonedDateTime second0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建一天开始的时间对象（0秒）
    final ZonedDateTime second1001 = createDateTime(2024, 1, 1, 0, 16, 41, 0); // 创建第1001秒的时间对象（16分41秒）
    final ZonedDateTime endOfDay = createDateTime(2024, 1, 1, 23, 59, 59, 0); // 创建一天结束的时间对象（86399秒）

    assertThat(toCharUs(pattern, second0), is("0")); // 断言：一天开始应该格式化为0秒
    assertThat(toCharUs(pattern, second1001), is("1001")); // 断言：16分41秒应该格式化为1001秒
    assertThat(toCharUs(pattern, endOfDay), is("86399")); // 断言：一天结束应该格式化为86399秒

    assertThat(toCharUs("FM" + pattern, second0), is("0")); // 断言：使用FM模式一天开始应该格式化为0
    assertThat(toCharUs("FM" + pattern, second1001), is("1001")); // 断言：使用FM模式16分41秒应该格式化为1001
    assertThat(toCharUs("FM" + pattern, endOfDay), is("86399")); // 断言：使用FM模式一天结束应该格式化为86399

    assertThat(toCharUs(pattern + "TH", second0), is("0TH")); // 断言：使用TH后缀0秒应该格式化为0TH
    assertThat(toCharUs(pattern + "TH", second1001), is("1001ST")); // 断言：使用TH后缀1001秒应该格式化为1001ST
    assertThat(toCharUs(pattern + "TH", endOfDay), is("86399TH")); // 断言：使用TH后缀86399秒应该格式化为86399TH
    assertThat(toCharUs(pattern + "th", second0), is("0th")); // 断言：使用th后缀0秒应该格式化为0th
    assertThat(toCharUs(pattern + "th", second1001), is("1001st")); // 断言：使用th后缀1001秒应该格式化为1001st
    assertThat(toCharUs(pattern + "th", endOfDay), is("86399th")); // 断言：使用th后缀86399秒应该格式化为86399th

    assertThat(toCharUs("FM" + pattern + "th", second1001), is("1001st")); // 断言：使用FM和th后缀1001秒应该格式化为1001st
    assertThat(toCharUs("FM" + pattern + "nd", second1001), is("1001nd")); // 断言：使用FM和nd后缀1001秒应该格式化为1001nd
  }

  @Test // 测试方法注解
  void testSS() { // 测试秒数格式（SS）
    final ZonedDateTime second0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0秒的时间对象
    final ZonedDateTime second2 = createDateTime(2024, 1, 1, 0, 0, 2, 0); // 创建2秒的时间对象
    final ZonedDateTime second15 = createDateTime(2024, 1, 1, 0, 0, 15, 0); // 创建15秒的时间对象

    assertThat(toCharUs("SS", second0), is("00")); // 断言：0秒应该格式化为00
    assertThat(toCharUs("SS", second2), is("02")); // 断言：2秒应该格式化为02
    assertThat(toCharUs("SS", second15), is("15")); // 断言：15秒应该格式化为15

    assertThat(toCharUs("FMSS", second0), is("0")); // 断言：使用FM模式0秒应该格式化为0
    assertThat(toCharUs("FMSS", second2), is("2")); // 断言：使用FM模式2秒应该格式化为2
    assertThat(toCharUs("FMSS", second15), is("15")); // 断言：使用FM模式15秒应该格式化为15

    assertThat(toCharUs("SSTH", second0), is("00TH")); // 断言：使用TH后缀0秒应该格式化为00TH
    assertThat(toCharUs("SSTH", second2), is("02ND")); // 断言：使用TH后缀2秒应该格式化为02ND
    assertThat(toCharUs("SSTH", second15), is("15TH")); // 断言：使用TH后缀15秒应该格式化为15TH
    assertThat(toCharUs("SSth", second0), is("00th")); // 断言：使用th后缀0秒应该格式化为00th
    assertThat(toCharUs("SSth", second2), is("02nd")); // 断言：使用th后缀2秒应该格式化为02nd
    assertThat(toCharUs("SSth", second15), is("15th")); // 断言：使用th后缀15秒应该格式化为15th

    assertThat(toCharUs("FMSSth", second2), is("2nd")); // 断言：使用FM和th后缀2秒应该格式化为2nd
    assertThat(toCharUs("FMSSnd", second2), is("2nd")); // 断言：使用FM和nd后缀2秒应该格式化为2nd
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"MS", "FF3"}) // 参数源：测试MS和FF3两种格式模式（毫秒）
  void testMS(String pattern) { // 测试毫秒格式（MS或FF3）
    final ZonedDateTime ms0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0毫秒的时间对象
    final ZonedDateTime ms2 = createDateTime(2024, 1, 1, 0, 0, 2, 2000000); // 创建2毫秒的时间对象（2秒+2毫秒）
    final ZonedDateTime ms15 = createDateTime(2024, 1, 1, 0, 0, 15, 15000000); // 创建15毫秒的时间对象（15秒+15毫秒）

    assertThat(toCharUs(pattern, ms0), is("000")); // 断言：0毫秒应该格式化为000
    assertThat(toCharUs(pattern, ms2), is("002")); // 断言：2毫秒应该格式化为002
    assertThat(toCharUs(pattern, ms15), is("015")); // 断言：15毫秒应该格式化为015

    assertThat(toCharUs("FM" + pattern, ms0), is("0")); // 断言：使用FM模式0毫秒应该格式化为0
    assertThat(toCharUs("FM" + pattern, ms2), is("2")); // 断言：使用FM模式2毫秒应该格式化为2
    assertThat(toCharUs("FM" + pattern, ms15), is("15")); // 断言：使用FM模式15毫秒应该格式化为15

    assertThat(toCharUs(pattern + "TH", ms0), is("000TH")); // 断言：使用TH后缀0毫秒应该格式化为000TH
    assertThat(toCharUs(pattern + "TH", ms2), is("002ND")); // 断言：使用TH后缀2毫秒应该格式化为002ND
    assertThat(toCharUs(pattern + "TH", ms15), is("015TH")); // 断言：使用TH后缀15毫秒应该格式化为015TH
    assertThat(toCharUs(pattern + "th", ms0), is("000th")); // 断言：使用th后缀0毫秒应该格式化为000th
    assertThat(toCharUs(pattern + "th", ms2), is("002nd")); // 断言：使用th后缀2毫秒应该格式化为002nd
    assertThat(toCharUs(pattern + "th", ms15), is("015th")); // 断言：使用th后缀15毫秒应该格式化为015th

    assertThat(toCharUs("FM" + pattern + "th", ms2), is("2nd")); // 断言：使用FM和th后缀2毫秒应该格式化为2nd
    assertThat(toCharUs("FM" + pattern + "nd", ms2), is("2nd")); // 断言：使用FM和nd后缀2毫秒应该格式化为2nd
  }

  @Test // 测试方法注解
  void testUS() { // 测试微秒格式（US）
    final ZonedDateTime us0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0微秒的时间对象
    final ZonedDateTime us2 = createDateTime(2024, 1, 1, 0, 0, 0, 2000); // 创建2微秒的时间对象
    final ZonedDateTime us15 = createDateTime(2024, 1, 1, 0, 0, 0, 15000); // 创建15微秒的时间对象
    final ZonedDateTime usWithMs = createDateTime(2024, 1, 1, 0, 0, 0, 2015000); // 创建2015微秒的时间对象（2毫秒+15微秒）

    assertThat(toCharUs("US", us0), is("000000")); // 断言：0微秒应该格式化为000000
    assertThat(toCharUs("US", us2), is("000002")); // 断言：2微秒应该格式化为000002
    assertThat(toCharUs("US", us15), is("000015")); // 断言：15微秒应该格式化为000015
    assertThat(toCharUs("US", usWithMs), is("002015")); // 断言：2015微秒应该格式化为002015

    assertThat(toCharUs("FMUS", us0), is("0")); // 断言：使用FM模式0微秒应该格式化为0
    assertThat(toCharUs("FMUS", us2), is("2")); // 断言：使用FM模式2微秒应该格式化为2
    assertThat(toCharUs("FMUS", us15), is("15")); // 断言：使用FM模式15微秒应该格式化为15
    assertThat(toCharUs("FMUS", usWithMs), is("2015")); // 断言：使用FM模式2015微秒应该格式化为2015

    assertThat(toCharUs("USTH", us0), is("000000TH")); // 断言：使用TH后缀0微秒应该格式化为000000TH
    assertThat(toCharUs("USTH", us2), is("000002ND")); // 断言：使用TH后缀2微秒应该格式化为000002ND
    assertThat(toCharUs("USTH", us15), is("000015TH")); // 断言：使用TH后缀15微秒应该格式化为000015TH
    assertThat(toCharUs("USTH", usWithMs), is("002015TH")); // 断言：使用TH后缀2015微秒应该格式化为002015TH
    assertThat(toCharUs("USth", us0), is("000000th")); // 断言：使用th后缀0微秒应该格式化为000000th
    assertThat(toCharUs("USth", us2), is("000002nd")); // 断言：使用th后缀2微秒应该格式化为000002nd
    assertThat(toCharUs("USth", us15), is("000015th")); // 断言：使用th后缀15微秒应该格式化为000015th
    assertThat(toCharUs("USth", usWithMs), is("002015th")); // 断言：使用th后缀2015微秒应该格式化为002015th

    assertThat(toCharUs("FMUSth", us2), is("2nd")); // 断言：使用FM和th后缀2微秒应该格式化为2nd
    assertThat(toCharUs("FMUSnd", us2), is("2nd")); // 断言：使用FM和nd后缀2微秒应该格式化为2nd
  }

  @Test // 测试方法注解
  void testFF1() { // 测试1位小数秒格式（FF1，十分之一秒）
    final ZonedDateTime ms0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0十分之一秒的时间对象
    final ZonedDateTime ms200 = createDateTime(2024, 1, 1, 0, 0, 0, 200_000_000); // 创建2十分之一秒的时间对象（200毫秒）
    final ZonedDateTime ms150 = createDateTime(2024, 1, 1, 0, 0, 0, 150_000_000); // 创建1.5十分之一秒的时间对象（150毫秒）

    assertThat(toCharUs("FF1", ms0), is("0")); // 断言：0十分之一秒应该格式化为0
    assertThat(toCharUs("FF1", ms200), is("2")); // 断言：2十分之一秒应该格式化为2
    assertThat(toCharUs("FF1", ms150), is("1")); // 断言：1.5十分之一秒应该格式化为1（向下取整）

    assertThat(toCharUs("FMFF1", ms0), is("0")); // 断言：使用FM模式0十分之一秒应该格式化为0
    assertThat(toCharUs("FMFF1", ms200), is("2")); // 断言：使用FM模式2十分之一秒应该格式化为2
    assertThat(toCharUs("FMFF1", ms150), is("1")); // 断言：使用FM模式1.5十分之一秒应该格式化为1

    assertThat(toCharUs("FF1TH", ms0), is("0TH")); // 断言：使用TH后缀0十分之一秒应该格式化为0TH
    assertThat(toCharUs("FF1TH", ms200), is("2ND")); // 断言：使用TH后缀2十分之一秒应该格式化为2ND
    assertThat(toCharUs("FF1TH", ms150), is("1ST")); // 断言：使用TH后缀1十分之一秒应该格式化为1ST
    assertThat(toCharUs("FF1th", ms0), is("0th")); // 断言：使用th后缀0十分之一秒应该格式化为0th
    assertThat(toCharUs("FF1th", ms200), is("2nd")); // 断言：使用th后缀2十分之一秒应该格式化为2nd
    assertThat(toCharUs("FF1th", ms150), is("1st")); // 断言：使用th后缀1十分之一秒应该格式化为1st

    assertThat(toCharUs("FMFF1th", ms200), is("2nd")); // 断言：使用FM和th后缀2十分之一秒应该格式化为2nd
    assertThat(toCharUs("FMFF1nd", ms200), is("2nd")); // 断言：使用FM和nd后缀2十分之一秒应该格式化为2nd
  }

  @Test // 测试方法注解
  void testFF2() { // 测试2位小数秒格式（FF2，百分之一秒）
    final ZonedDateTime ms0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0百分之一秒的时间对象
    final ZonedDateTime ms20 = createDateTime(2024, 1, 1, 0, 0, 0, 20_000_000); // 创建2百分之一秒的时间对象（20毫秒）
    final ZonedDateTime ms150 = createDateTime(2024, 1, 1, 0, 0, 0, 150_000_000); // 创建15百分之一秒的时间对象（150毫秒）

    assertThat(toCharUs("FF2", ms0), is("00")); // 断言：0百分之一秒应该格式化为00
    assertThat(toCharUs("FF2", ms20), is("02")); // 断言：2百分之一秒应该格式化为02
    assertThat(toCharUs("FF2", ms150), is("15")); // 断言：15百分之一秒应该格式化为15

    assertThat(toCharUs("FMFF2", ms0), is("0")); // 断言：使用FM模式0百分之一秒应该格式化为0
    assertThat(toCharUs("FMFF2", ms20), is("2")); // 断言：使用FM模式2百分之一秒应该格式化为2
    assertThat(toCharUs("FMFF2", ms150), is("15")); // 断言：使用FM模式15百分之一秒应该格式化为15

    assertThat(toCharUs("FF2TH", ms0), is("00TH")); // 断言：使用TH后缀0百分之一秒应该格式化为00TH
    assertThat(toCharUs("FF2TH", ms20), is("02ND")); // 断言：使用TH后缀2百分之一秒应该格式化为02ND
    assertThat(toCharUs("FF2TH", ms150), is("15TH")); // 断言：使用TH后缀15百分之一秒应该格式化为15TH
    assertThat(toCharUs("FF2th", ms0), is("00th")); // 断言：使用th后缀0百分之一秒应该格式化为00th
    assertThat(toCharUs("FF2th", ms20), is("02nd")); // 断言：使用th后缀2百分之一秒应该格式化为02nd
    assertThat(toCharUs("FF2th", ms150), is("15th")); // 断言：使用th后缀15百分之一秒应该格式化为15th

    assertThat(toCharUs("FMFF2th", ms20), is("2nd")); // 断言：使用FM和th后缀2百分之一秒应该格式化为2nd
    assertThat(toCharUs("FMFF2nd", ms20), is("2nd")); // 断言：使用FM和nd后缀2百分之一秒应该格式化为2nd
  }

  @Test // 测试方法注解
  void testFF4() { // 测试4位小数秒格式（FF4，万分之一秒）
    final ZonedDateTime us0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0万分之一秒的时间对象
    final ZonedDateTime us200 = createDateTime(2024, 1, 1, 0, 0, 0, 200_000); // 创建2万分之一秒的时间对象（200微秒）
    final ZonedDateTime ms150 = createDateTime(2024, 1, 1, 0, 0, 0, 150_000_000); // 创建1500万分之一秒的时间对象（150毫秒）

    assertThat(toCharUs("FF4", us0), is("0000")); // 断言：0万分之一秒应该格式化为0000
    assertThat(toCharUs("FF4", us200), is("0002")); // 断言：2万分之一秒应该格式化为0002
    assertThat(toCharUs("FF4", ms150), is("1500")); // 断言：1500万分之一秒应该格式化为1500

    assertThat(toCharUs("FMFF4", us0), is("0")); // 断言：使用FM模式0万分之一秒应该格式化为0
    assertThat(toCharUs("FMFF4", us200), is("2")); // 断言：使用FM模式2万分之一秒应该格式化为2
    assertThat(toCharUs("FMFF4", ms150), is("1500")); // 断言：使用FM模式1500万分之一秒应该格式化为1500

    assertThat(toCharUs("FF4TH", us0), is("0000TH")); // 断言：使用TH后缀0万分之一秒应该格式化为0000TH
    assertThat(toCharUs("FF4TH", us200), is("0002ND")); // 断言：使用TH后缀2万分之一秒应该格式化为0002ND
    assertThat(toCharUs("FF4TH", ms150), is("1500TH")); // 断言：使用TH后缀1500万分之一秒应该格式化为1500TH
    assertThat(toCharUs("FF4th", us0), is("0000th")); // 断言：使用th后缀0万分之一秒应该格式化为0000th
    assertThat(toCharUs("FF4th", us200), is("0002nd")); // 断言：使用th后缀2万分之一秒应该格式化为0002nd
    assertThat(toCharUs("FF4th", ms150), is("1500th")); // 断言：使用th后缀1500万分之一秒应该格式化为1500th

    assertThat(toCharUs("FMFF4th", us200), is("2nd")); // 断言：使用FM和th后缀2万分之一秒应该格式化为2nd
    assertThat(toCharUs("FMFF4nd", us200), is("2nd")); // 断言：使用FM和nd后缀2万分之一秒应该格式化为2nd
  }

  @Test // 测试方法注解
  void testFF5() { // 测试5位小数秒格式（FF5，十万分之一秒）
    final ZonedDateTime us0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0十万分之一秒的时间对象
    final ZonedDateTime us20 = createDateTime(2024, 1, 1, 0, 0, 0, 20_000); // 创建2十万分之一秒的时间对象（20微秒）
    final ZonedDateTime ms150 = createDateTime(2024, 1, 1, 0, 0, 0, 150_000_000); // 创建15000十万分之一秒的时间对象（150毫秒）

    assertThat(toCharUs("FF5", us0), is("00000")); // 断言：0十万分之一秒应该格式化为00000
    assertThat(toCharUs("FF5", us20), is("00002")); // 断言：2十万分之一秒应该格式化为00002
    assertThat(toCharUs("FF5", ms150), is("15000")); // 断言：15000十万分之一秒应该格式化为15000

    assertThat(toCharUs("FMFF5", us0), is("0")); // 断言：使用FM模式0十万分之一秒应该格式化为0
    assertThat(toCharUs("FMFF5", us20), is("2")); // 断言：使用FM模式2十万分之一秒应该格式化为2
    assertThat(toCharUs("FMFF5", ms150), is("15000")); // 断言：使用FM模式15000十万分之一秒应该格式化为15000

    assertThat(toCharUs("FF5TH", us0), is("00000TH")); // 断言：使用TH后缀0十万分之一秒应该格式化为00000TH
    assertThat(toCharUs("FF5TH", us20), is("00002ND")); // 断言：使用TH后缀2十万分之一秒应该格式化为00002ND
    assertThat(toCharUs("FF5TH", ms150), is("15000TH")); // 断言：使用TH后缀15000十万分之一秒应该格式化为15000TH
    assertThat(toCharUs("FF5th", us0), is("00000th")); // 断言：使用th后缀0十万分之一秒应该格式化为00000th
    assertThat(toCharUs("FF5th", us20), is("00002nd")); // 断言：使用th后缀2十万分之一秒应该格式化为00002nd
    assertThat(toCharUs("FF5th", ms150), is("15000th")); // 断言：使用th后缀15000十万分之一秒应该格式化为15000th

    assertThat(toCharUs("FMFF5th", us20), is("2nd")); // 断言：使用FM和th后缀2十万分之一秒应该格式化为2nd
    assertThat(toCharUs("FMFF5nd", us20), is("2nd")); // 断言：使用FM和nd后缀2十万分之一秒应该格式化为2nd
  }

  @Test // 测试方法注解
  void testFF6() { // 测试6位小数秒格式（FF6，百万分之一秒/微秒）
    final ZonedDateTime us0 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建0微秒的时间对象
    final ZonedDateTime us2 = createDateTime(2024, 1, 1, 0, 0, 0, 2_000); // 创建2微秒的时间对象
    final ZonedDateTime ms150 = createDateTime(2024, 1, 1, 0, 0, 0, 150_000_000); // 创建150000微秒的时间对象（150毫秒）

    assertThat(toCharUs("FF6", us0), is("000000")); // 断言：0微秒应该格式化为000000
    assertThat(toCharUs("FF6", us2), is("000002")); // 断言：2微秒应该格式化为000002
    assertThat(toCharUs("FF6", ms150), is("150000")); // 断言：150000微秒应该格式化为150000

    assertThat(toCharUs("FMFF6", us0), is("0")); // 断言：使用FM模式0微秒应该格式化为0
    assertThat(toCharUs("FMFF6", us2), is("2")); // 断言：使用FM模式2微秒应该格式化为2
    assertThat(toCharUs("FMFF6", ms150), is("150000")); // 断言：使用FM模式150000微秒应该格式化为150000

    assertThat(toCharUs("FF6TH", us0), is("000000TH")); // 断言：使用TH后缀0微秒应该格式化为000000TH
    assertThat(toCharUs("FF6TH", us2), is("000002ND")); // 断言：使用TH后缀2微秒应该格式化为000002ND
    assertThat(toCharUs("FF6TH", ms150), is("150000TH")); // 断言：使用TH后缀150000微秒应该格式化为150000TH
    assertThat(toCharUs("FF6th", us0), is("000000th")); // 断言：使用th后缀0微秒应该格式化为000000th
    assertThat(toCharUs("FF6th", us2), is("000002nd")); // 断言：使用th后缀2微秒应该格式化为000002nd
    assertThat(toCharUs("FF6th", ms150), is("150000th")); // 断言：使用th后缀150000微秒应该格式化为150000th

    assertThat(toCharUs("FMFF6th", us2), is("2nd")); // 断言：使用FM和th后缀2微秒应该格式化为2nd
    assertThat(toCharUs("FMFF6nd", us2), is("2nd")); // 断言：使用FM和nd后缀2微秒应该格式化为2nd
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"AM", "PM"}) // 参数源：测试AM和PM两种格式模式
  void testAMUpperCase(String pattern) { // 测试上午/下午标记大写格式（AM或PM）
    final ZonedDateTime midnight = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建午夜的时间对象
    final ZonedDateTime morning = createDateTime(2024, 1, 1, 6, 0, 0, 0); // 创建早上的时间对象
    final ZonedDateTime noon = createDateTime(2024, 1, 1, 12, 0, 0, 0); // 创建中午的时间对象
    final ZonedDateTime evening = createDateTime(2024, 1, 1, 18, 0, 0, 0); // 创建傍晚的时间对象

    assertThat(toCharUs(pattern, midnight), is("AM")); // 断言：午夜应该显示AM
    assertThat(toCharUs(pattern, morning), is("AM")); // 断言：早上应该显示AM
    assertThat(toCharUs(pattern, noon), is("PM")); // 断言：中午应该显示PM
    assertThat(toCharUs(pattern, evening), is("PM")); // 断言：傍晚应该显示PM
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"am", "pm"}) // 参数源：测试am和pm两种格式模式
  void testAMLowerCase(String pattern) { // 测试上午/下午标记小写格式（am或pm）
    final ZonedDateTime midnight = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建午夜的时间对象
    final ZonedDateTime morning = createDateTime(2024, 1, 1, 6, 0, 0, 0); // 创建早上的时间对象
    final ZonedDateTime noon = createDateTime(2024, 1, 1, 12, 0, 0, 0); // 创建中午的时间对象
    final ZonedDateTime evening = createDateTime(2024, 1, 1, 18, 0, 0, 0); // 创建傍晚的时间对象

    assertThat(toCharUs(pattern, midnight), is("am")); // 断言：午夜应该显示am
    assertThat(toCharUs(pattern, morning), is("am")); // 断言：早上应该显示am
    assertThat(toCharUs(pattern, noon), is("pm")); // 断言：中午应该显示pm
    assertThat(toCharUs(pattern, evening), is("pm")); // 断言：傍晚应该显示pm
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"A.M.", "P.M."}) // 参数源：测试A.M.和P.M.两种格式模式
  void testAMWithDotsUpperCase(String pattern) { // 测试上午/下午标记带点大写格式（A.M.或P.M.）
    final ZonedDateTime midnight = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建午夜的时间对象
    final ZonedDateTime morning = createDateTime(2024, 1, 1, 6, 0, 0, 0); // 创建早上的时间对象
    final ZonedDateTime noon = createDateTime(2024, 1, 1, 12, 0, 0, 0); // 创建中午的时间对象
    final ZonedDateTime evening = createDateTime(2024, 1, 1, 18, 0, 0, 0); // 创建傍晚的时间对象

    assertThat(toCharUs(pattern, midnight), is("A.M.")); // 断言：午夜应该显示A.M.
    assertThat(toCharUs(pattern, morning), is("A.M.")); // 断言：早上应该显示A.M.
    assertThat(toCharUs(pattern, noon), is("P.M.")); // 断言：中午应该显示P.M.
    assertThat(toCharUs(pattern, evening), is("P.M.")); // 断言：傍晚应该显示P.M.
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"a.m.", "p.m."}) // 参数源：测试a.m.和p.m.两种格式模式
  void testAMWithDotsLowerCase(String pattern) { // 测试上午/下午标记带点小写格式（a.m.或p.m.）
    final ZonedDateTime midnight = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建午夜的时间对象
    final ZonedDateTime morning = createDateTime(2024, 1, 1, 6, 0, 0, 0); // 创建早上的时间对象
    final ZonedDateTime noon = createDateTime(2024, 1, 1, 12, 0, 0, 0); // 创建中午的时间对象
    final ZonedDateTime evening = createDateTime(2024, 1, 1, 18, 0, 0, 0); // 创建傍晚的时间对象

    assertThat(toCharUs(pattern, midnight), is("a.m.")); // 断言：午夜应该显示a.m.
    assertThat(toCharUs(pattern, morning), is("a.m.")); // 断言：早上应该显示a.m.
    assertThat(toCharUs(pattern, noon), is("p.m.")); // 断言：中午应该显示p.m.
    assertThat(toCharUs(pattern, evening), is("p.m.")); // 断言：傍晚应该显示p.m.
  }

  @Test // 测试方法注解
  void testYearWithCommas() { // 测试带逗号的年份格式（Y,YYY）
    final ZonedDateTime year1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年的时间对象
    final ZonedDateTime year2 = createDateTime(100, 1, 1, 0, 0, 0, 0); // 创建100年的时间对象
    final ZonedDateTime year3 = createDateTime(1, 1, 1, 0, 0, 0, 0); // 创建1年的时间对象
    final ZonedDateTime year4 = createDateTime(32136, 1, 1, 0, 0, 0, 0); // 创建32136年的时间对象

    assertThat(toCharUs("Y,YYY", year1), is("2,024")); // 断言：2024年应该格式化为2,024
    assertThat(toCharUs("Y,YYY", year2), is("0,100")); // 断言：100年应该格式化为0,100
    assertThat(toCharUs("Y,YYY", year3), is("0,001")); // 断言：1年应该格式化为0,001
    assertThat(toCharUs("Y,YYY", year4), is("32,136")); // 断言：32136年应该格式化为32,136
    assertThat(toCharUs("FMY,YYY", year1), is("2,024")); // 断言：使用FM模式2024年应该格式化为2,024
    assertThat(toCharUs("FMY,YYY", year2), is("0,100")); // 断言：使用FM模式100年应该格式化为0,100
    assertThat(toCharUs("FMY,YYY", year3), is("0,001")); // 断言：使用FM模式1年应该格式化为0,001
    assertThat(toCharUs("FMY,YYY", year4), is("32,136")); // 断言：使用FM模式32136年应该格式化为32,136

    assertThat(toCharUs("Y,YYYTH", year1), is("2,024TH")); // 断言：使用TH后缀2024年应该格式化为2,024TH
    assertThat(toCharUs("Y,YYYTH", year2), is("0,100TH")); // 断言：使用TH后缀100年应该格式化为0,100TH
    assertThat(toCharUs("Y,YYYTH", year3), is("0,001ST")); // 断言：使用TH后缀1年应该格式化为0,001ST
    assertThat(toCharUs("Y,YYYTH", year4), is("32,136TH")); // 断言：使用TH后缀32136年应该格式化为32,136TH
    assertThat(toCharUs("Y,YYYth", year1), is("2,024th")); // 断言：使用th后缀2024年应该格式化为2,024th
    assertThat(toCharUs("Y,YYYth", year2), is("0,100th")); // 断言：使用th后缀100年应该格式化为0,100th
    assertThat(toCharUs("Y,YYYth", year3), is("0,001st")); // 断言：使用th后缀1年应该格式化为0,001st
    assertThat(toCharUs("Y,YYYth", year4), is("32,136th")); // 断言：使用th后缀32136年应该格式化为32,136th

    assertThat(toCharUs("FMY,YYYth", year1), is("2,024th")); // 断言：使用FM和th后缀2024年应该格式化为2,024th
  }

  @Test // 测试方法注解
  void testYYYY() { // 测试4位年份格式（YYYY）
    final ZonedDateTime year1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年的时间对象
    final ZonedDateTime year2 = createDateTime(100, 1, 1, 0, 0, 0, 0); // 创建100年的时间对象
    final ZonedDateTime year3 = createDateTime(1, 1, 1, 0, 0, 0, 0); // 创建1年的时间对象
    final ZonedDateTime year4 = createDateTime(32136, 1, 1, 0, 0, 0, 0); // 创建32136年的时间对象

    assertThat(toCharUs("YYYY", year1), is("2024")); // 断言：2024年应该格式化为2024
    assertThat(toCharUs("YYYY", year2), is("0100")); // 断言：100年应该格式化为0100（4位补零）
    assertThat(toCharUs("YYYY", year3), is("0001")); // 断言：1年应该格式化为0001（4位补零）
    assertThat(toCharUs("YYYY", year4), is("32136")); // 断言：32136年应该格式化为32136（超过4位不截断）
    assertThat(toCharUs("FMYYYY", year1), is("2024")); // 断言：使用FM模式2024年应该格式化为2024
    assertThat(toCharUs("FMYYYY", year2), is("100")); // 断言：使用FM模式100年应该格式化为100（不补零）
    assertThat(toCharUs("FMYYYY", year3), is("1")); // 断言：使用FM模式1年应该格式化为1（不补零）
    assertThat(toCharUs("FMYYYY", year4), is("32136")); // 断言：使用FM模式32136年应该格式化为32136

    assertThat(toCharUs("YYYYTH", year1), is("2024TH")); // 断言：使用TH后缀2024年应该格式化为2024TH
    assertThat(toCharUs("YYYYTH", year2), is("0100TH")); // 断言：使用TH后缀100年应该格式化为0100TH
    assertThat(toCharUs("YYYYTH", year3), is("0001ST")); // 断言：使用TH后缀1年应该格式化为0001ST
    assertThat(toCharUs("YYYYTH", year4), is("32136TH")); // 断言：使用TH后缀32136年应该格式化为32136TH
    assertThat(toCharUs("YYYYth", year1), is("2024th")); // 断言：使用th后缀2024年应该格式化为2024th
    assertThat(toCharUs("YYYYth", year2), is("0100th")); // 断言：使用th后缀100年应该格式化为0100th
    assertThat(toCharUs("YYYYth", year3), is("0001st")); // 断言：使用th后缀1年应该格式化为0001st
    assertThat(toCharUs("YYYYth", year4), is("32136th")); // 断言：使用th后缀32136年应该格式化为32136th

    assertThat(toCharUs("FMYYYYth", year1), is("2024th")); // 断言：使用FM和th后缀2024年应该格式化为2024th
  }

  @Test // 测试方法注解
  void testYYY() { // 测试3位年份格式（YYY，年份的后3位）
    final ZonedDateTime year1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年的时间对象
    final ZonedDateTime year2 = createDateTime(100, 1, 1, 0, 0, 0, 0); // 创建100年的时间对象
    final ZonedDateTime year3 = createDateTime(1, 1, 1, 0, 0, 0, 0); // 创建1年的时间对象
    final ZonedDateTime year4 = createDateTime(32136, 1, 1, 0, 0, 0, 0); // 创建32136年的时间对象

    assertThat(toCharUs("YYY", year1), is("024")); // 断言：2024年应该格式化为024（后3位）
    assertThat(toCharUs("YYY", year2), is("100")); // 断言：100年应该格式化为100
    assertThat(toCharUs("YYY", year3), is("001")); // 断言：1年应该格式化为001（3位补零）
    assertThat(toCharUs("YYY", year4), is("136")); // 断言：32136年应该格式化为136（后3位）
    assertThat(toCharUs("FMYYY", year1), is("24")); // 断言：使用FM模式2024年应该格式化为24（不补零）
    assertThat(toCharUs("FMYYY", year2), is("100")); // 断言：使用FM模式100年应该格式化为100
    assertThat(toCharUs("FMYYY", year3), is("1")); // 断言：使用FM模式1年应该格式化为1（不补零）
    assertThat(toCharUs("FMYYY", year4), is("136")); // 断言：使用FM模式32136年应该格式化为136

    assertThat(toCharUs("YYYTH", year1), is("024TH")); // 断言：使用TH后缀2024年应该格式化为024TH
    assertThat(toCharUs("YYYTH", year2), is("100TH")); // 断言：使用TH后缀100年应该格式化为100TH
    assertThat(toCharUs("YYYTH", year3), is("001ST")); // 断言：使用TH后缀1年应该格式化为001ST
    assertThat(toCharUs("YYYTH", year4), is("136TH")); // 断言：使用TH后缀32136年应该格式化为136TH
    assertThat(toCharUs("YYYth", year1), is("024th")); // 断言：使用th后缀2024年应该格式化为024th
    assertThat(toCharUs("YYYth", year2), is("100th")); // 断言：使用th后缀100年应该格式化为100th
    assertThat(toCharUs("YYYth", year3), is("001st")); // 断言：使用th后缀1年应该格式化为001st
    assertThat(toCharUs("YYYth", year4), is("136th")); // 断言：使用th后缀32136年应该格式化为136th

    assertThat(toCharUs("FMYYYth", year1), is("24th")); // 断言：使用FM和th后缀2024年应该格式化为24th
  }

  @Test // 测试方法注解
  void testYY() { // 测试2位年份格式（YY，年份的后2位）
    final ZonedDateTime year1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年的时间对象
    final ZonedDateTime year2 = createDateTime(100, 1, 1, 0, 0, 0, 0); // 创建100年的时间对象
    final ZonedDateTime year3 = createDateTime(1, 1, 1, 0, 0, 0, 0); // 创建1年的时间对象
    final ZonedDateTime year4 = createDateTime(32136, 1, 1, 0, 0, 0, 0); // 创建32136年的时间对象

    assertThat(toCharUs("YY", year1), is("24")); // 断言：2024年应该格式化为24（后2位）
    assertThat(toCharUs("YY", year2), is("00")); // 断言：100年应该格式化为00（后2位）
    assertThat(toCharUs("YY", year3), is("01")); // 断言：1年应该格式化为01（2位补零）
    assertThat(toCharUs("YY", year4), is("36")); // 断言：32136年应该格式化为36（后2位）
    assertThat(toCharUs("FMYY", year1), is("24")); // 断言：使用FM模式2024年应该格式化为24
    assertThat(toCharUs("FMYY", year2), is("0")); // 断言：使用FM模式100年应该格式化为0（不补零）
    assertThat(toCharUs("FMYY", year3), is("1")); // 断言：使用FM模式1年应该格式化为1（不补零）
    assertThat(toCharUs("FMYY", year4), is("36")); // 断言：使用FM模式32136年应该格式化为36

    assertThat(toCharUs("YYTH", year1), is("24TH")); // 断言：使用TH后缀2024年应该格式化为24TH
    assertThat(toCharUs("YYTH", year2), is("00TH")); // 断言：使用TH后缀100年应该格式化为00TH
    assertThat(toCharUs("YYTH", year3), is("01ST")); // 断言：使用TH后缀1年应该格式化为01ST
    assertThat(toCharUs("YYTH", year4), is("36TH")); // 断言：使用TH后缀32136年应该格式化为36TH
    assertThat(toCharUs("YYth", year1), is("24th")); // 断言：使用th后缀2024年应该格式化为24th
    assertThat(toCharUs("YYth", year2), is("00th")); // 断言：使用th后缀100年应该格式化为00th
    assertThat(toCharUs("YYth", year3), is("01st")); // 断言：使用th后缀1年应该格式化为01st
    assertThat(toCharUs("YYth", year4), is("36th")); // 断言：使用th后缀32136年应该格式化为36th

    assertThat(toCharUs("FMYYth", year1), is("24th")); // 断言：使用FM和th后缀2024年应该格式化为24th
  }

  @Test // 测试方法注解
  void testY() { // 测试1位年份格式（Y，年份的最后1位）
    final ZonedDateTime year1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年的时间对象
    final ZonedDateTime year2 = createDateTime(100, 1, 1, 0, 0, 0, 0); // 创建100年的时间对象
    final ZonedDateTime year3 = createDateTime(1, 1, 1, 0, 0, 0, 0); // 创建1年的时间对象
    final ZonedDateTime year4 = createDateTime(32136, 1, 1, 0, 0, 0, 0); // 创建32136年的时间对象

    assertThat(toCharUs("Y", year1), is("4")); // 断言：2024年应该格式化为4（最后1位）
    assertThat(toCharUs("Y", year2), is("0")); // 断言：100年应该格式化为0（最后1位）
    assertThat(toCharUs("Y", year3), is("1")); // 断言：1年应该格式化为1
    assertThat(toCharUs("Y", year4), is("6")); // 断言：32136年应该格式化为6（最后1位）
    assertThat(toCharUs("FMY", year1), is("4")); // 断言：使用FM模式2024年应该格式化为4
    assertThat(toCharUs("FMY", year2), is("0")); // 断言：使用FM模式100年应该格式化为0
    assertThat(toCharUs("FMY", year3), is("1")); // 断言：使用FM模式1年应该格式化为1
    assertThat(toCharUs("FMY", year4), is("6")); // 断言：使用FM模式32136年应该格式化为6

    assertThat(toCharUs("YTH", year1), is("4TH")); // 断言：使用TH后缀2024年应该格式化为4TH
    assertThat(toCharUs("YTH", year2), is("0TH")); // 断言：使用TH后缀100年应该格式化为0TH
    assertThat(toCharUs("YTH", year3), is("1ST")); // 断言：使用TH后缀1年应该格式化为1ST
    assertThat(toCharUs("YTH", year4), is("6TH")); // 断言：使用TH后缀32136年应该格式化为6TH
    assertThat(toCharUs("Yth", year1), is("4th")); // 断言：使用th后缀2024年应该格式化为4th
    assertThat(toCharUs("Yth", year2), is("0th")); // 断言：使用th后缀100年应该格式化为0th
    assertThat(toCharUs("Yth", year3), is("1st")); // 断言：使用th后缀1年应该格式化为1st
    assertThat(toCharUs("Yth", year4), is("6th")); // 断言：使用th后缀32136年应该格式化为6th

    assertThat(toCharUs("FMYth", year1), is("4th")); // 断言：使用FM和th后缀2024年应该格式化为4th
  }

  @Test // 测试方法注解
  void testIYYY() { // 测试ISO 4位年份格式（IYYY，基于ISO周标准的年份）
    final ZonedDateTime date1 = createDateTime(2019, 12, 29, 0, 0, 0, 0); // 创建2019年12月29日的时间对象
    final ZonedDateTime date2 = date1.plusDays(1); // 创建date1的后一天（2019年12月30日）
    final ZonedDateTime date3 = date2.plusDays(1); // 创建date2的后一天（2019年12月31日）
    final ZonedDateTime date4 = date3.plusDays(1); // 创建date3的后一天（2020年1月1日）
    final ZonedDateTime date5 = date4.plusDays(1); // 创建date4的后一天（2020年1月2日）

    assertThat(toCharUs("IYYY", date1), is("2019")); // 断言：2019年12月29日应该格式化为2019（属于2019年ISO周）
    assertThat(toCharUs("IYYY", date2), is("2020")); // 断言：2019年12月30日应该格式化为2020（属于2020年ISO周）
    assertThat(toCharUs("IYYY", date3), is("2020")); // 断言：2019年12月31日应该格式化为2020（属于2020年ISO周）
    assertThat(toCharUs("IYYY", date4), is("2020")); // 断言：2020年1月1日应该格式化为2020
    assertThat(toCharUs("IYYY", date5), is("2020")); // 断言：2020年1月2日应该格式化为2020
    assertThat(toCharUs("FMIYYY", date1), is("2019")); // 断言：使用FM模式2019年12月29日应该格式化为2019
    assertThat(toCharUs("FMIYYY", date2), is("2020")); // 断言：使用FM模式2019年12月30日应该格式化为2020
    assertThat(toCharUs("FMIYYY", date3), is("2020")); // 断言：使用FM模式2019年12月31日应该格式化为2020
    assertThat(toCharUs("FMIYYY", date4), is("2020")); // 断言：使用FM模式2020年1月1日应该格式化为2020
    assertThat(toCharUs("FMIYYY", date5), is("2020")); // 断言：使用FM模式2020年1月2日应该格式化为2020

    assertThat(toCharUs("IYYYTH", date1), is("2019TH")); // 断言：使用TH后缀2019年12月29日应该格式化为2019TH
    assertThat(toCharUs("IYYYTH", date2), is("2020TH")); // 断言：使用TH后缀2019年12月30日应该格式化为2020TH
    assertThat(toCharUs("IYYYTH", date3), is("2020TH")); // 断言：使用TH后缀2019年12月31日应该格式化为2020TH
    assertThat(toCharUs("IYYYTH", date4), is("2020TH")); // 断言：使用TH后缀2020年1月1日应该格式化为2020TH
    assertThat(toCharUs("IYYYTH", date5), is("2020TH")); // 断言：使用TH后缀2020年1月2日应该格式化为2020TH
    assertThat(toCharUs("IYYYth", date1), is("2019th")); // 断言：使用th后缀2019年12月29日应该格式化为2019th
    assertThat(toCharUs("IYYYth", date2), is("2020th")); // 断言：使用th后缀2019年12月30日应该格式化为2020th
    assertThat(toCharUs("IYYYth", date3), is("2020th")); // 断言：使用th后缀2019年12月31日应该格式化为2020th
    assertThat(toCharUs("IYYYth", date4), is("2020th")); // 断言：使用th后缀2020年1月1日应该格式化为2020th
    assertThat(toCharUs("IYYYth", date5), is("2020th")); // 断言：使用th后缀2020年1月2日应该格式化为2020th

    assertThat(toCharUs("FMIYYYth", date5), is("2020th")); // 断言：使用FM和th后缀2020年1月2日应该格式化为2020th
  }

  @Test // 测试方法注解
  void testIYY() { // 测试ISO 3位年份格式（IYY，基于ISO周标准的年份后3位）
    final ZonedDateTime date1 = createDateTime(2019, 12, 29, 0, 0, 0, 0); // 创建2019年12月29日的时间对象
    final ZonedDateTime date2 = date1.plusDays(1); // 创建date1的后一天（2019年12月30日）
    final ZonedDateTime date3 = date2.plusDays(1); // 创建date2的后一天（2019年12月31日）
    final ZonedDateTime date4 = date3.plusDays(1); // 创建date3的后一天（2020年1月1日）
    final ZonedDateTime date5 = date4.plusDays(1); // 创建date4的后一天（2020年1月2日）

    assertThat(toCharUs("IYY", date1), is("019")); // 断言：2019年12月29日应该格式化为019（2019的后3位）
    assertThat(toCharUs("IYY", date2), is("020")); // 断言：2019年12月30日应该格式化为020（2020的后3位）
    assertThat(toCharUs("IYY", date3), is("020")); // 断言：2019年12月31日应该格式化为020（2020的后3位）
    assertThat(toCharUs("IYY", date4), is("020")); // 断言：2020年1月1日应该格式化为020
    assertThat(toCharUs("IYY", date5), is("020")); // 断言：2020年1月2日应该格式化为020
    assertThat(toCharUs("FMIYY", date1), is("19")); // 断言：使用FM模式2019年12月29日应该格式化为19
    assertThat(toCharUs("FMIYY", date2), is("20")); // 断言：使用FM模式2019年12月30日应该格式化为20
    assertThat(toCharUs("FMIYY", date3), is("20")); // 断言：使用FM模式2019年12月31日应该格式化为20
    assertThat(toCharUs("FMIYY", date4), is("20")); // 断言：使用FM模式2020年1月1日应该格式化为20
    assertThat(toCharUs("FMIYY", date5), is("20")); // 断言：使用FM模式2020年1月2日应该格式化为20

    assertThat(toCharUs("IYYTH", date1), is("019TH")); // 断言：使用TH后缀2019年12月29日应该格式化为019TH
    assertThat(toCharUs("IYYTH", date2), is("020TH")); // 断言：使用TH后缀2019年12月30日应该格式化为020TH
    assertThat(toCharUs("IYYTH", date3), is("020TH")); // 断言：使用TH后缀2019年12月31日应该格式化为020TH
    assertThat(toCharUs("IYYTH", date4), is("020TH")); // 断言：使用TH后缀2020年1月1日应该格式化为020TH
    assertThat(toCharUs("IYYTH", date5), is("020TH")); // 断言：使用TH后缀2020年1月2日应该格式化为020TH
    assertThat(toCharUs("IYYth", date1), is("019th")); // 断言：使用th后缀2019年12月29日应该格式化为019th
    assertThat(toCharUs("IYYth", date2), is("020th")); // 断言：使用th后缀2019年12月30日应该格式化为020th
    assertThat(toCharUs("IYYth", date3), is("020th")); // 断言：使用th后缀2019年12月31日应该格式化为020th
    assertThat(toCharUs("IYYth", date4), is("020th")); // 断言：使用th后缀2020年1月1日应该格式化为020th
    assertThat(toCharUs("IYYth", date5), is("020th")); // 断言：使用th后缀2020年1月2日应该格式化为020th

    assertThat(toCharUs("FMIYYth", date5), is("20th")); // 断言：使用FM和th后缀2020年1月2日应该格式化为20th
  }

  @Test // 测试方法注解
  void testIY() { // 测试ISO 2位年份格式（IY，基于ISO周标准的年份后2位）
    final ZonedDateTime date1 = createDateTime(2019, 12, 29, 0, 0, 0, 0); // 创建2019年12月29日的时间对象
    final ZonedDateTime date2 = date1.plusDays(1); // 创建date1的后一天（2019年12月30日）
    final ZonedDateTime date3 = date2.plusDays(1); // 创建date2的后一天（2019年12月31日）
    final ZonedDateTime date4 = date3.plusDays(1); // 创建date3的后一天（2020年1月1日）
    final ZonedDateTime date5 = date4.plusDays(1); // 创建date4的后一天（2020年1月2日）

    assertThat(toCharUs("IY", date1), is("19")); // 断言：2019年12月29日应该格式化为19（2019的后2位）
    assertThat(toCharUs("IY", date2), is("20")); // 断言：2019年12月30日应该格式化为20（2020的后2位）
    assertThat(toCharUs("IY", date3), is("20")); // 断言：2019年12月31日应该格式化为20（2020的后2位）
    assertThat(toCharUs("IY", date4), is("20")); // 断言：2020年1月1日应该格式化为20
    assertThat(toCharUs("IY", date5), is("20")); // 断言：2020年1月2日应该格式化为20
    assertThat(toCharUs("FMIY", date1), is("19")); // 断言：使用FM模式2019年12月29日应该格式化为19
    assertThat(toCharUs("FMIY", date2), is("20")); // 断言：使用FM模式2019年12月30日应该格式化为20
    assertThat(toCharUs("FMIY", date3), is("20")); // 断言：使用FM模式2019年12月31日应该格式化为20
    assertThat(toCharUs("FMIY", date4), is("20")); // 断言：使用FM模式2020年1月1日应该格式化为20
    assertThat(toCharUs("FMIY", date5), is("20")); // 断言：使用FM模式2020年1月2日应该格式化为20

    assertThat(toCharUs("IYTH", date1), is("19TH")); // 断言：使用TH后缀2019年12月29日应该格式化为19TH
    assertThat(toCharUs("IYTH", date2), is("20TH")); // 断言：使用TH后缀2019年12月30日应该格式化为20TH
    assertThat(toCharUs("IYTH", date3), is("20TH")); // 断言：使用TH后缀2019年12月31日应该格式化为20TH
    assertThat(toCharUs("IYTH", date4), is("20TH")); // 断言：使用TH后缀2020年1月1日应该格式化为20TH
    assertThat(toCharUs("IYTH", date5), is("20TH")); // 断言：使用TH后缀2020年1月2日应该格式化为20TH
    assertThat(toCharUs("IYth", date1), is("19th")); // 断言：使用th后缀2019年12月29日应该格式化为19th
    assertThat(toCharUs("IYth", date2), is("20th")); // 断言：使用th后缀2019年12月30日应该格式化为20th
    assertThat(toCharUs("IYth", date3), is("20th")); // 断言：使用th后缀2019年12月31日应该格式化为20th
    assertThat(toCharUs("IYth", date4), is("20th")); // 断言：使用th后缀2020年1月1日应该格式化为20th
    assertThat(toCharUs("IYth", date5), is("20th")); // 断言：使用th后缀2020年1月2日应该格式化为20th

    assertThat(toCharUs("FMIYth", date5), is("20th")); // 断言：使用FM和th后缀2020年1月2日应该格式化为20th
  }

  @Test // 测试方法注解
  void testI() { // 测试ISO 1位年份格式（I，基于ISO周标准的年份最后1位）
    final ZonedDateTime date1 = createDateTime(2019, 12, 29, 0, 0, 0, 0); // 创建2019年12月29日的时间对象
    final ZonedDateTime date2 = date1.plusDays(1); // 创建date1的后一天（2019年12月30日）
    final ZonedDateTime date3 = date2.plusDays(1); // 创建date2的后一天（2019年12月31日）
    final ZonedDateTime date4 = date3.plusDays(1); // 创建date3的后一天（2020年1月1日）
    final ZonedDateTime date5 = date4.plusDays(1); // 创建date4的后一天（2020年1月2日）

    assertThat(toCharUs("I", date1), is("9")); // 断言：2019年12月29日应该格式化为9（2019的最后1位）
    assertThat(toCharUs("I", date2), is("0")); // 断言：2019年12月30日应该格式化为0（2020的最后1位）
    assertThat(toCharUs("I", date3), is("0")); // 断言：2019年12月31日应该格式化为0（2020的最后1位）
    assertThat(toCharUs("I", date4), is("0")); // 断言：2020年1月1日应该格式化为0
    assertThat(toCharUs("I", date5), is("0")); // 断言：2020年1月2日应该格式化为0
    assertThat(toCharUs("FMI", date1), is("9")); // 断言：使用FM模式2019年12月29日应该格式化为9
    assertThat(toCharUs("FMI", date2), is("0")); // 断言：使用FM模式2019年12月30日应该格式化为0
    assertThat(toCharUs("FMI", date3), is("0")); // 断言：使用FM模式2019年12月31日应该格式化为0
    assertThat(toCharUs("FMI", date4), is("0")); // 断言：使用FM模式2020年1月1日应该格式化为0
    assertThat(toCharUs("FMI", date5), is("0")); // 断言：使用FM模式2020年1月2日应该格式化为0

    assertThat(toCharUs("ITH", date1), is("9TH")); // 断言：使用TH后缀2019年12月29日应该格式化为9TH
    assertThat(toCharUs("ITH", date2), is("0TH")); // 断言：使用TH后缀2019年12月30日应该格式化为0TH
    assertThat(toCharUs("ITH", date3), is("0TH")); // 断言：使用TH后缀2019年12月31日应该格式化为0TH
    assertThat(toCharUs("ITH", date4), is("0TH")); // 断言：使用TH后缀2020年1月1日应该格式化为0TH
    assertThat(toCharUs("ITH", date5), is("0TH")); // 断言：使用TH后缀2020年1月2日应该格式化为0TH
    assertThat(toCharUs("Ith", date1), is("9th")); // 断言：使用th后缀2019年12月29日应该格式化为9th
    assertThat(toCharUs("Ith", date2), is("0th")); // 断言：使用th后缀2019年12月30日应该格式化为0th
    assertThat(toCharUs("Ith", date3), is("0th")); // 断言：使用th后缀2019年12月31日应该格式化为0th
    assertThat(toCharUs("Ith", date4), is("0th")); // 断言：使用th后缀2020年1月1日应该格式化为0th
    assertThat(toCharUs("Ith", date5), is("0th")); // 断言：使用th后缀2020年1月2日应该格式化为0th

    assertThat(toCharUs("FMIth", date5), is("0th")); // 断言：使用FM和th后缀2020年1月2日应该格式化为0th
  }

  @Test // 测试方法注解
  void testIW() { // 测试ISO周数格式（IW，基于ISO周标准的周数）
    final ZonedDateTime date1 = createDateTime(2019, 12, 29, 0, 0, 0, 0); // 创建2019年12月29日的时间对象
    final ZonedDateTime date2 = date1.plusDays(1); // 创建date1的后一天（2019年12月30日）
    final ZonedDateTime date3 = date2.plusDays(186); // 创建date2的186天后（2020年7月3日）

    assertThat(toCharUs("IW", date1), is("52")); // 断言：2019年12月29日应该格式化为52（2019年第52周）
    assertThat(toCharUs("IW", date2), is("01")); // 断言：2019年12月30日应该格式化为01（2020年第1周）
    assertThat(toCharUs("IW", date3), is("27")); // 断言：2020年7月3日应该格式化为27（2020年第27周）
    assertThat(toCharUs("FMIW", date1), is("52")); // 断言：使用FM模式2019年12月29日应该格式化为52
    assertThat(toCharUs("FMIW", date2), is("1")); // 断言：使用FM模式2019年12月30日应该格式化为1
    assertThat(toCharUs("FMIW", date3), is("27")); // 断言：使用FM模式2020年7月3日应该格式化为27

    assertThat(toCharUs("IWTH", date1), is("52ND")); // 断言：使用TH后缀2019年12月29日应该格式化为52ND
    assertThat(toCharUs("IWTH", date2), is("01ST")); // 断言：使用TH后缀2019年12月30日应该格式化为01ST
    assertThat(toCharUs("IWTH", date3), is("27TH")); // 断言：使用TH后缀2020年7月3日应该格式化为27TH
    assertThat(toCharUs("IWth", date1), is("52nd")); // 断言：使用th后缀2019年12月29日应该格式化为52nd
    assertThat(toCharUs("IWth", date2), is("01st")); // 断言：使用th后缀2019年12月30日应该格式化为01st
    assertThat(toCharUs("IWth", date3), is("27th")); // 断言：使用th后缀2020年7月3日应该格式化为27th

    assertThat(toCharUs("FMIWth", date3), is("27th")); // 断言：使用FM和th后缀2020年7月3日应该格式化为27th
  }

  @Test // 测试方法注解
  void testIDDD() { // 测试ISO年中的天数格式（IDDD，基于ISO周标准的年中的天数）
    final ZonedDateTime date1 = createDateTime(2019, 12, 29, 0, 0, 0, 0); // 创建2019年12月29日的时间对象
    final ZonedDateTime date2 = date1.plusDays(1); // 创建date1的后一天（2019年12月30日）
    final ZonedDateTime date3 = date2.plusDays(186); // 创建date2的186天后（2020年7月3日）

    assertThat(toCharUs("IDDD", date1), is("364")); // 断言：2019年12月29日应该格式化为364（2019年第364天）
    assertThat(toCharUs("IDDD", date2), is("001")); // 断言：2019年12月30日应该格式化为001（2020年第1天）
    assertThat(toCharUs("IDDD", date3), is("187")); // 断言：2020年7月3日应该格式化为187（2020年第187天）
    assertThat(toCharUs("FMIDDD", date1), is("364")); // 断言：使用FM模式2019年12月29日应该格式化为364
    assertThat(toCharUs("FMIDDD", date2), is("1")); // 断言：使用FM模式2019年12月30日应该格式化为1
    assertThat(toCharUs("FMIDDD", date3), is("187")); // 断言：使用FM模式2020年7月3日应该格式化为187

    assertThat(toCharUs("IDDDTH", date1), is("364TH")); // 断言：使用TH后缀2019年12月29日应该格式化为364TH
    assertThat(toCharUs("IDDDTH", date2), is("001ST")); // 断言：使用TH后缀2019年12月30日应该格式化为001ST
    assertThat(toCharUs("IDDDTH", date3), is("187TH")); // 断言：使用TH后缀2020年7月3日应该格式化为187TH
    assertThat(toCharUs("IDDDth", date1), is("364th")); // 断言：使用th后缀2019年12月29日应该格式化为364th
    assertThat(toCharUs("IDDDth", date2), is("001st")); // 断言：使用th后缀2019年12月30日应该格式化为001st
    assertThat(toCharUs("IDDDth", date3), is("187th")); // 断言：使用th后缀2020年7月3日应该格式化为187th

    assertThat(toCharUs("FMIDDDth", date3), is("187th")); // 断言：使用FM和th后缀2020年7月3日应该格式化为187th
  }

  @Test // 测试方法注解
  void testID() { // 测试ISO周中的星期几格式（ID，基于ISO周标准的星期几，1-7，1=周一）
    final ZonedDateTime date1 = createDateTime(2019, 12, 29, 0, 0, 0, 0); // 创建2019年12月29日的时间对象（周日）
    final ZonedDateTime date2 = date1.plusDays(1); // 创建date1的后一天（2019年12月30日，周一）
    final ZonedDateTime date3 = date2.plusDays(186); // 创建date2的186天后（2020年7月3日，周五）

    assertThat(toCharUs("ID", date1), is("7")); // 断言：2019年12月29日应该格式化为7（周日）
    assertThat(toCharUs("ID", date2), is("1")); // 断言：2019年12月30日应该格式化为1（周一）
    assertThat(toCharUs("ID", date3), is("5")); // 断言：2020年7月3日应该格式化为5（周五）
    assertThat(toCharUs("FMID", date1), is("7")); // 断言：使用FM模式2019年12月29日应该格式化为7
    assertThat(toCharUs("FMID", date2), is("1")); // 断言：使用FM模式2019年12月30日应该格式化为1
    assertThat(toCharUs("FMID", date3), is("5")); // 断言：使用FM模式2020年7月3日应该格式化为5

    assertThat(toCharUs("IDTH", date1), is("7TH")); // 断言：使用TH后缀2019年12月29日应该格式化为7TH
    assertThat(toCharUs("IDTH", date2), is("1ST")); // 断言：使用TH后缀2019年12月30日应该格式化为1ST
    assertThat(toCharUs("IDTH", date3), is("5TH")); // 断言：使用TH后缀2020年7月3日应该格式化为5TH
    assertThat(toCharUs("IDth", date1), is("7th")); // 断言：使用th后缀2019年12月29日应该格式化为7th
    assertThat(toCharUs("IDth", date2), is("1st")); // 断言：使用th后缀2019年12月30日应该格式化为1st
    assertThat(toCharUs("IDth", date3), is("5th")); // 断言：使用th后缀2020年7月3日应该格式化为5th

    assertThat(toCharUs("FMIDth", date3), is("5th")); // 断言：使用FM和th后缀2020年7月3日应该格式化为5th
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"AD", "BC"}) // 参数源：测试AD和BC两种格式模式
  void testEraUpperCaseNoDots(String pattern) { // 测试纪元大写格式不带点（AD或BC）
    final ZonedDateTime date1 = createDateTime(2019, 1, 1, 23, 0, 0, 0); // 创建公元2019年的时间对象
    final ZonedDateTime date2 = date1.minusYears(2018); // 创建date1减去2018年的时间对象（公元1年）
    final ZonedDateTime date3 = date2.minusYears(1); // 创建date2减去1年的时间对象（公元前1年）
    final ZonedDateTime date4 = date3.minusYears(200); // 创建date3减去200年的时间对象（公元前201年）

    assertThat(toCharUs(pattern, date1), is("AD")); // 断言：公元2019年应该显示AD
    assertThat(toCharUs(pattern, date2), is("AD")); // 断言：公元1年应该显示AD
    assertThat(toCharUs(pattern, date3), is("BC")); // 断言：公元前1年应该显示BC
    assertThat(toCharUs(pattern, date4), is("BC")); // 断言：公元前201年应该显示BC
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"ad", "bc"}) // 参数源：测试ad和bc两种格式模式
  void testEraLowerCaseNoDots(String pattern) { // 测试纪元小写格式不带点（ad或bc）
    final ZonedDateTime date1 = createDateTime(2019, 1, 1, 23, 0, 0, 0); // 创建公元2019年的时间对象
    final ZonedDateTime date2 = date1.minusYears(2018); // 创建date1减去2018年的时间对象（公元1年）
    final ZonedDateTime date3 = date2.minusYears(1); // 创建date2减去1年的时间对象（公元前1年）
    final ZonedDateTime date4 = date3.minusYears(200); // 创建date3减去200年的时间对象（公元前201年）

    assertThat(toCharUs(pattern, date1), is("ad")); // 断言：公元2019年应该显示ad
    assertThat(toCharUs(pattern, date2), is("ad")); // 断言：公元1年应该显示ad
    assertThat(toCharUs(pattern, date3), is("bc")); // 断言：公元前1年应该显示bc
    assertThat(toCharUs(pattern, date4), is("bc")); // 断言：公元前201年应该显示bc
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"A.D.", "B.C."}) // 参数源：测试A.D.和B.C.两种格式模式
  void testEraUpperCaseWithDots(String pattern) { // 测试纪元大写格式带点（A.D.或B.C.）
    final ZonedDateTime date1 = createDateTime(2019, 1, 1, 23, 0, 0, 0); // 创建公元2019年的时间对象
    final ZonedDateTime date2 = date1.minusYears(2018); // 创建date1减去2018年的时间对象（公元1年）
    final ZonedDateTime date3 = date2.minusYears(1); // 创建date2减去1年的时间对象（公元前1年）
    final ZonedDateTime date4 = date3.minusYears(200); // 创建date3减去200年的时间对象（公元前201年）

    assertThat(toCharUs(pattern, date1), is("A.D.")); // 断言：公元2019年应该显示A.D.
    assertThat(toCharUs(pattern, date2), is("A.D.")); // 断言：公元1年应该显示A.D.
    assertThat(toCharUs(pattern, date3), is("B.C.")); // 断言：公元前1年应该显示B.C.
    assertThat(toCharUs(pattern, date4), is("B.C.")); // 断言：公元前201年应该显示B.C.
  }

  @ParameterizedTest // 参数化测试注解
  @ValueSource(strings = {"a.d.", "b.c."}) // 参数源：测试a.d.和b.c.两种格式模式
  void testEraLowerCaseWithDots(String pattern) { // 测试纪元小写格式带点（a.d.或b.c.）
    final ZonedDateTime date1 = createDateTime(2019, 1, 1, 23, 0, 0, 0); // 创建公元2019年的时间对象
    final ZonedDateTime date2 = date1.minusYears(2018); // 创建date1减去2018年的时间对象（公元1年）
    final ZonedDateTime date3 = date2.minusYears(1); // 创建date2减去1年的时间对象（公元前1年）
    final ZonedDateTime date4 = date3.minusYears(200); // 创建date3减去200年的时间对象（公元前201年）

    assertThat(toCharUs(pattern, date1), is("a.d.")); // 断言：公元2019年应该显示a.d.
    assertThat(toCharUs(pattern, date2), is("a.d.")); // 断言：公元1年应该显示a.d.
    assertThat(toCharUs(pattern, date3), is("b.c.")); // 断言：公元前1年应该显示b.c.
    assertThat(toCharUs(pattern, date4), is("b.c.")); // 断言：公元前201年应该显示b.c.
  }

  @Test // 测试方法注解
  void testMonthFullUpperCase() { // 测试月份全称大写格式（MONTH）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("MONTH", date1), is("JANUARY  ")); // 断言：1月应该格式化为JANUARY（右填充到9字符）
    assertThat(toCharUs("MONTH", date2), is("MARCH    ")); // 断言：3月应该格式化为MARCH（右填充到9字符）
    assertThat(toCharUs("MONTH", date3), is("NOVEMBER ")); // 断言：11月应该格式化为NOVEMBER（右填充到9字符）
  }

  @Test // 测试方法注解
  void testMonthFullUpperCaseNoTranslate() { // 测试月份全称大写格式不翻译（MONTH，法语地区）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharFrench("MONTH", date1), is("JANUARY  ")); // 断言：法语地区1月应该格式化为JANUARY（不翻译）
    assertThat(toCharFrench("MONTH", date2), is("MARCH    ")); // 断言：法语地区3月应该格式化为MARCH（不翻译）
    assertThat(toCharFrench("MONTH", date3), is("NOVEMBER ")); // 断言：法语地区11月应该格式化为NOVEMBER（不翻译）
  }

  @Test // 测试方法注解
  void testMonthFullUpperCaseTranslate() { // 测试月份全称大写格式翻译（TMMONTH，法语地区）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharFrench("TMMONTH", date1), is("JANVIER")); // 断言：法语地区1月应该翻译为JANVIER
    assertThat(toCharFrench("TMMONTH", date2), is("MARS")); // 断言：法语地区3月应该翻译为MARS
    assertThat(toCharFrench("TMMONTH", date3), is("NOVEMBRE")); // 断言：法语地区11月应该翻译为NOVEMBRE
  }

  @Test // 测试方法注解
  void testMonthFullUpperCaseNoPadding() { // 测试月份全称大写格式无填充（FMMONTH）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("FMMONTH", date1), is("JANUARY")); // 断言：使用FM模式1月应该格式化为JANUARY（无填充）
    assertThat(toCharUs("FMMONTH", date2), is("MARCH")); // 断言：使用FM模式3月应该格式化为MARCH（无填充）
    assertThat(toCharUs("FMMONTH", date3), is("NOVEMBER")); // 断言：使用FM模式11月应该格式化为NOVEMBER（无填充）
  }

  @Test // 测试方法注解
  void testMonthFullCapitalized() { // 测试月份全称首字母大写格式（Month）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("Month", date1), is("January  ")); // 断言：1月应该格式化为January（右填充到9字符）
    assertThat(toCharUs("Month", date2), is("March    ")); // 断言：3月应该格式化为March（右填充到9字符）
    assertThat(toCharUs("Month", date3), is("November ")); // 断言：11月应该格式化为November（右填充到9字符）
  }

  @Test // 测试方法注解
  void testMonthFullLowerCase() { // 测试月份全称小写格式（month）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("month", date1), is("january  ")); // 断言：1月应该格式化为january（右填充到9字符）
    assertThat(toCharUs("month", date2), is("march    ")); // 断言：3月应该格式化为march（右填充到9字符）
    assertThat(toCharUs("month", date3), is("november ")); // 断言：11月应该格式化为november（右填充到9字符）
  }

  @Test // 测试方法注解
  void testMonthShortUpperCase() { // 测试月份简称大写格式（MON）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("MON", date1), is("JAN")); // 断言：1月应该格式化为JAN
    assertThat(toCharUs("MON", date2), is("MAR")); // 断言：3月应该格式化为MAR
    assertThat(toCharUs("MON", date3), is("NOV")); // 断言：11月应该格式化为NOV
  }

  @Test // 测试方法注解
  void testMonthShortCapitalized() { // 测试月份简称首字母大写格式（Mon）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("Mon", date1), is("Jan")); // 断言：1月应该格式化为Jan
    assertThat(toCharUs("Mon", date2), is("Mar")); // 断言：3月应该格式化为Mar
    assertThat(toCharUs("Mon", date3), is("Nov")); // 断言：11月应该格式化为Nov
  }

  @Test // 测试方法注解
  void testMonthShortLowerCase() { // 测试月份简称小写格式（mon）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("mon", date1), is("jan")); // 断言：1月应该格式化为jan
    assertThat(toCharUs("mon", date2), is("mar")); // 断言：3月应该格式化为mar
    assertThat(toCharUs("mon", date3), is("nov")); // 断言：11月应该格式化为nov
  }

  @Test // 测试方法注解
  void testMM() { // 测试月份数字格式（MM）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象

    assertThat(toCharUs("MM", date1), is("01")); // 断言：1月应该格式化为01
    assertThat(toCharUs("MM", date2), is("03")); // 断言：3月应该格式化为03
    assertThat(toCharUs("MM", date3), is("11")); // 断言：11月应该格式化为11
    assertThat(toCharUs("FMMM", date1), is("1")); // 断言：使用FM模式1月应该格式化为1
    assertThat(toCharUs("FMMM", date2), is("3")); // 断言：使用FM模式3月应该格式化为3
    assertThat(toCharUs("FMMM", date3), is("11")); // 断言：使用FM模式11月应该格式化为11

    assertThat(toCharUs("MMTH", date1), is("01ST")); // 断言：使用TH后缀1月应该格式化为01ST
    assertThat(toCharUs("MMTH", date2), is("03RD")); // 断言：使用TH后缀3月应该格式化为03RD
    assertThat(toCharUs("MMTH", date3), is("11TH")); // 断言：使用TH后缀11月应该格式化为11TH
    assertThat(toCharUs("MMth", date1), is("01st")); // 断言：使用th后缀1月应该格式化为01st
    assertThat(toCharUs("MMth", date2), is("03rd")); // 断言：使用th后缀3月应该格式化为03rd
    assertThat(toCharUs("MMth", date3), is("11th")); // 断言：使用th后缀11月应该格式化为11th

    assertThat(toCharUs("FMMMth", date2), is("3rd")); // 断言：使用FM和th后缀3月应该格式化为3rd
  }

  @Test // 测试方法注解
  void testDayFullUpperCase() { // 测试星期全称大写格式（DAY）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharUs("DAY", date1), is("MONDAY   ")); // 断言：周一应该格式化为MONDAY（右填充到9字符）
    assertThat(toCharUs("DAY", date2), is("FRIDAY   ")); // 断言：周五应该格式化为FRIDAY（右填充到9字符）
    assertThat(toCharUs("DAY", date3), is("TUESDAY  ")); // 断言：周二应该格式化为TUESDAY（右填充到9字符）
  }

  @Test // 测试方法注解
  void testDayFullUpperNoTranslate() { // 测试星期全称大写格式不翻译（DAY，法语地区）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharFrench("DAY", date1), is("MONDAY   ")); // 断言：法语地区周一应该格式化为MONDAY（不翻译）
    assertThat(toCharFrench("DAY", date2), is("FRIDAY   ")); // 断言：法语地区周五应该格式化为FRIDAY（不翻译）
    assertThat(toCharFrench("DAY", date3), is("TUESDAY  ")); // 断言：法语地区周二应该格式化为TUESDAY（不翻译）
  }

  @Test // 测试方法注解
  void testDayFullUpperTranslate() { // 测试星期全称大写格式翻译（TMDAY，法语地区）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharFrench("TMDAY", date1), is("LUNDI")); // 断言：法语地区周一应该翻译为LUNDI
    assertThat(toCharFrench("TMDAY", date2), is("VENDREDI")); // 断言：法语地区周五应该翻译为VENDREDI
    assertThat(toCharFrench("TMDAY", date3), is("MARDI")); // 断言：法语地区周二应该翻译为MARDI
  }

  @Test // 测试方法注解
  void testDayFullCapitalized() { // 测试星期全称首字母大写格式（Day）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharUs("Day", date1), is("Monday   ")); // 断言：周一应该格式化为Monday（右填充到9字符）
    assertThat(toCharUs("Day", date2), is("Friday   ")); // 断言：周五应该格式化为Friday（右填充到9字符）
    assertThat(toCharUs("Day", date3), is("Tuesday  ")); // 断言：周二应该格式化为Tuesday（右填充到9字符）
  }

  @Test // 测试方法注解
  void testDayFullLowerCase() { // 测试星期全称小写格式（day）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharUs("day", date1), is("monday   ")); // 断言：周一应该格式化为monday（右填充到9字符）
    assertThat(toCharUs("day", date2), is("friday   ")); // 断言：周五应该格式化为friday（右填充到9字符）
    assertThat(toCharUs("day", date3), is("tuesday  ")); // 断言：周二应该格式化为tuesday（右填充到9字符）
  }

  @Test // 测试方法注解
  void testDayShortUpperCase() { // 测试星期简称大写格式（DY）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharUs("DY", date1), is("MON")); // 断言：周一应该格式化为MON
    assertThat(toCharUs("DY", date2), is("FRI")); // 断言：周五应该格式化为FRI
    assertThat(toCharUs("DY", date3), is("TUE")); // 断言：周二应该格式化为TUE
  }

  @Test // 测试方法注解
  void testDayShortCapitalized() { // 测试星期简称首字母大写格式（Dy）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharUs("Dy", date1), is("Mon")); // 断言：周一应该格式化为Mon
    assertThat(toCharUs("Dy", date2), is("Fri")); // 断言：周五应该格式化为Fri
    assertThat(toCharUs("Dy", date3), is("Tue")); // 断言：周二应该格式化为Tue
  }

  @Test // 测试方法注解
  void testDayShortLowerCase() { // 测试星期简称小写格式（dy）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（周五）
    final ZonedDateTime date3 = createDateTime(2024, 10, 1, 23, 0, 0, 0); // 创建2024年10月1日的时间对象（周二）

    assertThat(toCharUs("dy", date1), is("mon")); // 断言：周一应该格式化为mon
    assertThat(toCharUs("dy", date2), is("fri")); // 断言：周五应该格式化为fri
    assertThat(toCharUs("dy", date3), is("tue")); // 断言：周二应该格式化为tue
  }

  @Test // 测试方法注解
  void testDDD() { // 测试年中的天数格式（DDD）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（第1天）
    final ZonedDateTime date2 = createDateTime(2024, 3, 1, 23, 0, 0, 0); // 创建2024年3月1日的时间对象（第61天）
    final ZonedDateTime date3 = createDateTime(2024, 11, 1, 23, 0, 0, 0); // 创建2024年11月1日的时间对象（第306天）

    assertThat(toCharUs("DDD", date1), is("001")); // 断言：1月1日应该格式化为001（第1天）
    assertThat(toCharUs("DDD", date2), is("061")); // 断言：3月1日应该格式化为061（第61天）
    assertThat(toCharUs("DDD", date3), is("306")); // 断言：11月1日应该格式化为306（第306天）
    assertThat(toCharUs("FMDDD", date1), is("1")); // 断言：使用FM模式1月1日应该格式化为1
    assertThat(toCharUs("FMDDD", date2), is("61")); // 断言：使用FM模式3月1日应该格式化为61
    assertThat(toCharUs("FMDDD", date3), is("306")); // 断言：使用FM模式11月1日应该格式化为306

    assertThat(toCharUs("DDDTH", date1), is("001ST")); // 断言：使用TH后缀1月1日应该格式化为001ST
    assertThat(toCharUs("DDDTH", date2), is("061ST")); // 断言：使用TH后缀3月1日应该格式化为061ST
    assertThat(toCharUs("DDDTH", date3), is("306TH")); // 断言：使用TH后缀11月1日应该格式化为306TH
    assertThat(toCharUs("DDDth", date1), is("001st")); // 断言：使用th后缀1月1日应该格式化为001st
    assertThat(toCharUs("DDDth", date2), is("061st")); // 断言：使用th后缀3月1日应该格式化为061st
    assertThat(toCharUs("DDDth", date3), is("306th")); // 断言：使用th后缀11月1日应该格式化为306th

    assertThat(toCharUs("FMDDDth", date1), is("1st")); // 断言：使用FM和th后缀1月1日应该格式化为1st
  }

  @Test // 测试方法注解
  void testDD() { // 测试月份中的天数格式（DD）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = createDateTime(2024, 1, 12, 23, 0, 0, 0); // 创建2024年1月12日的时间对象
    final ZonedDateTime date3 = createDateTime(2024, 1, 29, 23, 0, 0, 0); // 创建2024年1月29日的时间对象

    assertThat(toCharUs("DD", date1), is("01")); // 断言：1日应该格式化为01
    assertThat(toCharUs("DD", date2), is("12")); // 断言：12日应该格式化为12
    assertThat(toCharUs("DD", date3), is("29")); // 断言：29日应该格式化为29
    assertThat(toCharUs("FMDD", date1), is("1")); // 断言：使用FM模式1日应该格式化为1
    assertThat(toCharUs("FMDD", date2), is("12")); // 断言：使用FM模式12日应该格式化为12
    assertThat(toCharUs("FMDD", date3), is("29")); // 断言：使用FM模式29日应该格式化为29

    assertThat(toCharUs("DDTH", date1), is("01ST")); // 断言：使用TH后缀1日应该格式化为01ST
    assertThat(toCharUs("DDTH", date2), is("12TH")); // 断言：使用TH后缀12日应该格式化为12TH
    assertThat(toCharUs("DDTH", date3), is("29TH")); // 断言：使用TH后缀29日应该格式化为29TH
    assertThat(toCharUs("DDth", date1), is("01st")); // 断言：使用th后缀1日应该格式化为01st
    assertThat(toCharUs("DDth", date2), is("12th")); // 断言：使用th后缀12日应该格式化为12th
    assertThat(toCharUs("DDth", date3), is("29th")); // 断言：使用th后缀29日应该格式化为29th

    assertThat(toCharUs("FMDDth", date1), is("1st")); // 断言：使用FM和th后缀1日应该格式化为1st
  }

  @Test // 测试方法注解
  void testD() { // 测试周中的星期几格式（D，1-7，1=周日）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（周一）
    final ZonedDateTime date2 = createDateTime(2024, 1, 2, 23, 0, 0, 0); // 创建2024年1月2日的时间对象（周二）
    final ZonedDateTime date3 = createDateTime(2024, 1, 27, 23, 0, 0, 0); // 创建2024年1月27日的时间对象（周六）

    assertThat(toCharUs("D", date1), is("2")); // 断言：周一应该格式化为2
    assertThat(toCharUs("D", date2), is("3")); // 断言：周二应该格式化为3
    assertThat(toCharUs("D", date3), is("7")); // 断言：周六应该格式化为7
    assertThat(toCharUs("FMD", date1), is("2")); // 断言：使用FM模式周一应该格式化为2
    assertThat(toCharUs("FMD", date2), is("3")); // 断言：使用FM模式周二应该格式化为3
    assertThat(toCharUs("FMD", date3), is("7")); // 断言：使用FM模式周六应该格式化为7

    assertThat(toCharUs("DTH", date1), is("2ND")); // 断言：使用TH后缀周一应该格式化为2ND
    assertThat(toCharUs("DTH", date2), is("3RD")); // 断言：使用TH后缀周二应该格式化为3RD
    assertThat(toCharUs("DTH", date3), is("7TH")); // 断言：使用TH后缀周六应该格式化为7TH
    assertThat(toCharUs("Dth", date1), is("2nd")); // 断言：使用th后缀周一应该格式化为2nd
    assertThat(toCharUs("Dth", date2), is("3rd")); // 断言：使用th后缀周二应该格式化为3rd
    assertThat(toCharUs("Dth", date3), is("7th")); // 断言：使用th后缀周六应该格式化为7th

    assertThat(toCharUs("FMDth", date1), is("2nd")); // 断言：使用FM和th后缀周一应该格式化为2nd
  }

  @Test // 测试方法注解
  void testWW() { // 测试年中的周数格式（WW）
    final ZonedDateTime date1 = createDateTime(2016, 1, 1, 23, 0, 0, 0); // 创建2016年1月1日的时间对象（第1周）
    final ZonedDateTime date2 = createDateTime(2016, 3, 1, 23, 0, 0, 0); // 创建2016年3月1日的时间对象（第9周）
    final ZonedDateTime date3 = createDateTime(2016, 10, 1, 23, 0, 0, 0); // 创建2016年10月1日的时间对象（第40周）

    assertThat(toCharUs("WW", date1), is("1")); // 断言：1月1日应该格式化为1（第1周）
    assertThat(toCharUs("WW", date2), is("9")); // 断言：3月1日应该格式化为9（第9周）
    assertThat(toCharUs("WW", date3), is("40")); // 断言：10月1日应该格式化为40（第40周）
    assertThat(toCharUs("FMWW", date1), is("1")); // 断言：使用FM模式1月1日应该格式化为1
    assertThat(toCharUs("FMWW", date2), is("9")); // 断言：使用FM模式3月1日应该格式化为9
    assertThat(toCharUs("FMWW", date3), is("40")); // 断言：使用FM模式10月1日应该格式化为40

    assertThat(toCharUs("WWTH", date1), is("1ST")); // 断言：使用TH后缀1月1日应该格式化为1ST
    assertThat(toCharUs("WWTH", date2), is("9TH")); // 断言：使用TH后缀3月1日应该格式化为9TH
    assertThat(toCharUs("WWTH", date3), is("40TH")); // 断言：使用TH后缀10月1日应该格式化为40TH
    assertThat(toCharUs("WWth", date1), is("1st")); // 断言：使用th后缀1月1日应该格式化为1st
    assertThat(toCharUs("WWth", date2), is("9th")); // 断言：使用th后缀3月1日应该格式化为9th
    assertThat(toCharUs("WWth", date3), is("40th")); // 断言：使用th后缀10月1日应该格式化为40th

    assertThat(toCharUs("FMWWth", date1), is("1st")); // 断言：使用FM和th后缀1月1日应该格式化为1st
  }

  @Test // 测试方法注解
  void testW() { // 测试月中的周数格式（W）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年1月1日的时间对象（第1周）
    final ZonedDateTime date2 = createDateTime(2024, 1, 15, 23, 0, 0, 0); // 创建2024年1月15日的时间对象（第3周）
    final ZonedDateTime date3 = createDateTime(2024, 10, 31, 23, 0, 0, 0); // 创建2024年10月31日的时间对象（第5周）

    assertThat(toCharUs("W", date1), is("1")); // 断言：1月1日应该格式化为1（第1周）
    assertThat(toCharUs("W", date2), is("3")); // 断言：1月15日应该格式化为3（第3周）
    assertThat(toCharUs("W", date3), is("5")); // 断言：10月31日应该格式化为5（第5周）
    assertThat(toCharUs("FMW", date1), is("1")); // 断言：使用FM模式1月1日应该格式化为1
    assertThat(toCharUs("FMW", date2), is("3")); // 断言：使用FM模式1月15日应该格式化为3
    assertThat(toCharUs("FMW", date3), is("5")); // 断言：使用FM模式10月31日应该格式化为5

    assertThat(toCharUs("WTH", date1), is("1ST")); // 断言：使用TH后缀1月1日应该格式化为1ST
    assertThat(toCharUs("WTH", date2), is("3RD")); // 断言：使用TH后缀1月15日应该格式化为3RD
    assertThat(toCharUs("WTH", date3), is("5TH")); // 断言：使用TH后缀10月31日应该格式化为5TH
    assertThat(toCharUs("Wth", date1), is("1st")); // 断言：使用th后缀1月1日应该格式化为1st
    assertThat(toCharUs("Wth", date2), is("3rd")); // 断言：使用th后缀1月15日应该格式化为3rd
    assertThat(toCharUs("Wth", date3), is("5th")); // 断言：使用th后缀10月31日应该格式化为5th

    assertThat(toCharUs("FMWth", date1), is("1st")); // 断言：使用FM和th后缀1月1日应该格式化为1st
  }

  @Test // 测试方法注解
  void testCC() { // 测试世纪格式（CC）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 23, 0, 0, 0); // 创建2024年的时间对象（21世纪）
    final ZonedDateTime date2 = date1.minusYears(2023); // 创建date1减去2023年的时间对象（公元1年，1世纪）
    final ZonedDateTime date3 = date2.minusYears(1); // 创建date2减去1年的时间对象（公元前1年，-1世纪）
    final ZonedDateTime date4 = date3.minusYears(200); // 创建date3减去200年的时间对象（公元前201年，-3世纪）

    assertThat(toCharUs("CC", date1), is("21")); // 断言：2024年应该格式化为21（21世纪）
    assertThat(toCharUs("CC", date2), is("01")); // 断言：公元1年应该格式化为01（1世纪）
    assertThat(toCharUs("CC", date3), is("-01")); // 断言：公元前1年应该格式化为-01（-1世纪）
    assertThat(toCharUs("CC", date4), is("-03")); // 断言：公元前201年应该格式化为-03（-3世纪）
    assertThat(toCharUs("FMCC", date1), is("21")); // 断言：使用FM模式2024年应该格式化为21
    assertThat(toCharUs("FMCC", date2), is("1")); // 断言：使用FM模式公元1年应该格式化为1
    assertThat(toCharUs("FMCC", date3), is("-1")); // 断言：使用FM模式公元前1年应该格式化为-1
    assertThat(toCharUs("FMCC", date4), is("-3")); // 断言：使用FM模式公元前201年应该格式化为-3

    assertThat(toCharUs("CCTH", date1), is("21ST")); // 断言：使用TH后缀2024年应该格式化为21ST
    assertThat(toCharUs("CCTH", date2), is("01ST")); // 断言：使用TH后缀公元1年应该格式化为01ST
    assertThat(toCharUs("CCTH", date3), is("-01ST")); // 断言：使用TH后缀公元前1年应该格式化为-01ST
    assertThat(toCharUs("CCTH", date4), is("-03RD")); // 断言：使用TH后缀公元前201年应该格式化为-03RD
    assertThat(toCharUs("CCth", date1), is("21st")); // 断言：使用th后缀2024年应该格式化为21st
    assertThat(toCharUs("CCth", date2), is("01st")); // 断言：使用th后缀公元1年应该格式化为01st
    assertThat(toCharUs("CCth", date3), is("-01st")); // 断言：使用th后缀公元前1年应该格式化为-01st
    assertThat(toCharUs("CCth", date4), is("-03rd")); // 断言：使用th后缀公元前201年应该格式化为-03rd

    assertThat(toCharUs("FMCCth", date3), is("-1st")); // 断言：使用FM和th后缀公元前1年应该格式化为-1st
  }

  @Test // 测试方法注解
  void testJ() { // 测试儒略日格式（J）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年1月1日的时间对象
    final ZonedDateTime date2 = date1.minusYears(2024); // 创建date1减去2024年的时间对象（公元1年1月1日）
    final ZonedDateTime date3 = date2.minusYears(1000); // 创建date2减去1000年的时间对象（公元前999年1月1日）

    assertThat(toCharUs("J", date1), is("2460311")); // 断言：2024年1月1日应该格式化为2460311（儒略日）
    assertThat(toCharUs("J", date2), is("1721060")); // 断言：公元1年1月1日应该格式化为1721060（儒略日）
    assertThat(toCharUs("J", date3), is("1356183")); // 断言：公元前999年1月1日应该格式化为1356183（儒略日）
    assertThat(toCharUs("FMJ", date1), is("2460311")); // 断言：使用FM模式2024年1月1日应该格式化为2460311
    assertThat(toCharUs("FMJ", date2), is("1721060")); // 断言：使用FM模式公元1年1月1日应该格式化为1721060
    assertThat(toCharUs("FMJ", date3), is("1356183")); // 断言：使用FM模式公元前999年1月1日应该格式化为1356183

    assertThat(toCharUs("JTH", date1), is("2460311TH")); // 断言：使用TH后缀2024年1月1日应该格式化为2460311TH
    assertThat(toCharUs("JTH", date2), is("1721060TH")); // 断言：使用TH后缀公元1年1月1日应该格式化为1721060TH
    assertThat(toCharUs("JTH", date3), is("1356183RD")); // 断言：使用TH后缀公元前999年1月1日应该格式化为1356183RD
    assertThat(toCharUs("Jth", date1), is("2460311th")); // 断言：使用th后缀2024年1月1日应该格式化为2460311th
    assertThat(toCharUs("Jth", date2), is("1721060th")); // 断言：使用th后缀公元1年1月1日应该格式化为1721060th
    assertThat(toCharUs("Jth", date3), is("1356183rd")); // 断言：使用th后缀公元前999年1月1日应该格式化为1356183rd
  }

  @Test // 测试方法注解
  void testQ() { // 测试季度格式（Q）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年1月1日的时间对象（第1季度）
    final ZonedDateTime date2 = createDateTime(2024, 4, 9, 0, 0, 0, 0); // 创建2024年4月9日的时间对象（第2季度）
    final ZonedDateTime date3 = createDateTime(2024, 8, 23, 0, 0, 0, 0); // 创建2024年8月23日的时间对象（第3季度）
    final ZonedDateTime date4 = createDateTime(2024, 12, 31, 0, 0, 0, 0); // 创建2024年12月31日的时间对象（第4季度）

    assertThat(toCharUs("Q", date1), is("1")); // 断言：1月1日应该格式化为1（第1季度）
    assertThat(toCharUs("Q", date2), is("2")); // 断言：4月9日应该格式化为2（第2季度）
    assertThat(toCharUs("Q", date3), is("3")); // 断言：8月23日应该格式化为3（第3季度）
    assertThat(toCharUs("Q", date4), is("4")); // 断言：12月31日应该格式化为4（第4季度）
    assertThat(toCharUs("FMQ", date1), is("1")); // 断言：使用FM模式1月1日应该格式化为1
    assertThat(toCharUs("FMQ", date2), is("2")); // 断言：使用FM模式4月9日应该格式化为2
    assertThat(toCharUs("FMQ", date3), is("3")); // 断言：使用FM模式8月23日应该格式化为3
    assertThat(toCharUs("FMQ", date4), is("4")); // 断言：使用FM模式12月31日应该格式化为4

    assertThat(toCharUs("QTH", date1), is("1ST")); // 断言：使用TH后缀1月1日应该格式化为1ST
    assertThat(toCharUs("QTH", date2), is("2ND")); // 断言：使用TH后缀4月9日应该格式化为2ND
    assertThat(toCharUs("QTH", date3), is("3RD")); // 断言：使用TH后缀8月23日应该格式化为3RD
    assertThat(toCharUs("QTH", date4), is("4TH")); // 断言：使用TH后缀12月31日应该格式化为4TH
    assertThat(toCharUs("Qth", date1), is("1st")); // 断言：使用th后缀1月1日应该格式化为1st
    assertThat(toCharUs("Qth", date2), is("2nd")); // 断言：使用th后缀4月9日应该格式化为2nd
    assertThat(toCharUs("Qth", date3), is("3rd")); // 断言：使用th后缀8月23日应该格式化为3rd
    assertThat(toCharUs("Qth", date4), is("4th")); // 断言：使用th后缀12月31日应该格式化为4th
  }

  @Test // 测试方法注解
  void testRMUpperCase() { // 测试罗马数字月份格式大写（RM）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年1月1日的时间对象（1月）
    final ZonedDateTime date2 = createDateTime(2024, 4, 9, 0, 0, 0, 0); // 创建2024年4月9日的时间对象（4月）
    final ZonedDateTime date3 = createDateTime(2024, 8, 23, 0, 0, 0, 0); // 创建2024年8月23日的时间对象（8月）
    final ZonedDateTime date4 = createDateTime(2024, 12, 31, 0, 0, 0, 0); // 创建2024年12月31日的时间对象（12月）

    assertThat(toCharUs("RM", date1), is("I")); // 断言：1月应该格式化为I（罗马数字）
    assertThat(toCharUs("RM", date2), is("IV")); // 断言：4月应该格式化为IV（罗马数字）
    assertThat(toCharUs("RM", date3), is("VIII")); // 断言：8月应该格式化为VIII（罗马数字）
    assertThat(toCharUs("RM", date4), is("XII")); // 断言：12月应该格式化为XII（罗马数字）
  }

  @Test // 测试方法注解
  void testRMLowerCase() { // 测试罗马数字月份格式小写（rm）
    final ZonedDateTime date1 = createDateTime(2024, 1, 1, 0, 0, 0, 0); // 创建2024年1月1日的时间对象（1月）
    final ZonedDateTime date2 = createDateTime(2024, 4, 9, 0, 0, 0, 0); // 创建2024年4月9日的时间对象（4月）
    final ZonedDateTime date3 = createDateTime(2024, 8, 23, 0, 0, 0, 0); // 创建2024年8月23日的时间对象（8月）
    final ZonedDateTime date4 = createDateTime(2024, 12, 31, 0, 0, 0, 0); // 创建2024年12月31日的时间对象（12月）

    assertThat(toCharUs("rm", date1), is("i")); // 断言：1月应该格式化为i（罗马数字小写）
    assertThat(toCharUs("rm", date2), is("iv")); // 断言：4月应该格式化为iv（罗马数字小写）
    assertThat(toCharUs("rm", date3), is("viii")); // 断言：8月应该格式化为viii（罗马数字小写）
    assertThat(toCharUs("rm", date4), is("xii")); // 断言：12月应该格式化为xii（罗马数字小写）
  }

  @Test // 测试方法注解
  void testToCharReuseFormat() throws Exception { // 测试重用编译后的格式进行格式化
    final CompiledDateTimeFormat compiledFormat = // 声明编译后的日期时间格式对象
        PostgresqlDateTimeFormatter.compilePattern("YYYY-MM-DD HH24:MI:SS.MS"); // 编译完整的日期时间格式模式
    final ZonedDateTime expected1 = createDateTime(2019, 3, 7, 15, 46, 23, 521000000); // 创建第一个期望的日期时间对象
    final ZonedDateTime expected2 = createDateTime(1983, 11, 29, 4, 21, 16, 45000000); // 创建第二个期望的日期时间对象
    final ZonedDateTime expected3 = createDateTime(2024, 9, 24, 14, 53, 37, 891000000); // 创建第三个期望的日期时间对象
    assertThat( // 断言：验证第一个日期时间格式化结果
        compiledFormat.parseDateTime("2019-03-07 15:46:23.521", TIME_ZONE, // 使用编译后的格式解析第一个字符串
            Locale.US), // 使用美式英语地区设置
        is(expected1)); // 期望结果为expected1
    assertThat( // 断言：验证第二个日期时间格式化结果
        compiledFormat.parseDateTime("1983-11-29 04:21:16.045", TIME_ZONE, // 使用编译后的格式解析第二个字符串
            Locale.US), // 使用美式英语地区设置
        is(expected2)); // 期望结果为expected2
    assertThat( // 断言：验证第三个日期时间格式化结果
        compiledFormat.parseDateTime("2024-09-24 14:53:37.891", TIME_ZONE, // 使用编译后的格式解析第三个字符串
            Locale.US), // 使用美式英语地区设置
        is(expected3)); // 期望结果为expected3
    assertThat( // 断言：验证使用不同分隔符的解析
        compiledFormat.parseDateTime("2024x09x24x14x53x37x891", TIME_ZONE, // 使用x作为分隔符解析字符串
            Locale.US), // 使用美式英语地区设置
        is(expected3)); // 期望结果为expected3（分隔符不影响解析）
  }

  @Test // 测试方法注解
  void testToTimestampHH() throws Exception { // 测试解析小时格式（HH）
    assertThat(toTimestamp("01", "HH"), is(DAY_1_CE.plusHours(1))); // 断言：解析"01"应该得到1小时
    assertThat(toTimestamp("1", "HH"), is(DAY_1_CE.plusHours(1))); // 断言：解析"1"应该得到1小时
    assertThat(toTimestamp("11", "HH"), is(DAY_1_CE.plusHours(11))); // 断言：解析"11"应该得到11小时

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("72", "HH"); // 解析"72"（超出0-23范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "HH"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampHH12() throws Exception { // 测试解析12小时制小时格式（HH12）
    assertThat(toTimestamp("01", "HH12"), is(DAY_1_CE.plusHours(1))); // 断言：解析"01"应该得到1小时
    assertThat(toTimestamp("1", "HH12"), is(DAY_1_CE.plusHours(1))); // 断言：解析"1"应该得到1小时
    assertThat(toTimestamp("11", "HH12"), is(DAY_1_CE.plusHours(11))); // 断言：解析"11"应该得到11小时

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("72", "HH12"); // 解析"72"（超出1-12范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "HH12"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampHH24() throws Exception { // 测试解析24小时制小时格式（HH24）
    assertThat(toTimestamp("01", "HH24"), is(DAY_1_CE.plusHours(1))); // 断言：解析"01"应该得到1小时
    assertThat(toTimestamp("1", "HH24"), is(DAY_1_CE.plusHours(1))); // 断言：解析"1"应该得到1小时
    assertThat(toTimestamp("18", "HH24"), is(DAY_1_CE.plusHours(18))); // 断言：解析"18"应该得到18小时

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("72", "HH24"); // 解析"72"（超出0-23范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "HH24"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampMI() throws Exception { // 测试解析分钟格式（MI）
    assertThat(toTimestamp("01", "MI"), is(DAY_1_CE.plusMinutes(1))); // 断言：解析"01"应该得到1分钟
    assertThat(toTimestamp("1", "MI"), is(DAY_1_CE.plusMinutes(1))); // 断言：解析"1"应该得到1分钟
    assertThat(toTimestamp("57", "MI"), is(DAY_1_CE.plusMinutes(57))); // 断言：解析"57"应该得到57分钟

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("72", "MI"); // 解析"72"（超出0-59范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "MI"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampSS() throws Exception { // 测试解析秒格式（SS）
    assertThat(toTimestamp("01", "SS"), is(DAY_1_CE.plusSeconds(1))); // 断言：解析"01"应该得到1秒
    assertThat(toTimestamp("1", "SS"), is(DAY_1_CE.plusSeconds(1))); // 断言：解析"1"应该得到1秒
    assertThat(toTimestamp("57", "SS"), is(DAY_1_CE.plusSeconds(57))); // 断言：解析"57"应该得到57秒

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("72", "SS"); // 解析"72"（超出0-59范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "SS"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampMS() throws Exception { // 测试解析毫秒格式（MS）
    assertThat(toTimestamp("001", "MS"), is(DAY_1_CE.plusNanos(1_000_000))); // 断言：解析"001"应该得到1毫秒（1,000,000纳秒）
    assertThat(toTimestamp("1", "MS"), is(DAY_1_CE.plusNanos(1_000_000))); // 断言：解析"1"应该得到1毫秒（1,000,000纳秒）
    assertThat(toTimestamp("999", "MS"), is(DAY_1_CE.plusNanos(999_000_000))); // 断言：解析"999"应该得到999毫秒（999,000,000纳秒）

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("9999", "MS"); // 解析"9999"（超出0-999范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "MS"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampUS() throws Exception { // 测试解析微秒格式（US）
    assertThat(toTimestamp("001", "US"), is(DAY_1_CE.plusNanos(1_000))); // 断言：解析"001"应该得到1微秒（1,000纳秒）
    assertThat(toTimestamp("1", "US"), is(DAY_1_CE.plusNanos(1_000))); // 断言：解析"1"应该得到1微秒（1,000纳秒）
    assertThat(toTimestamp("999", "US"), is(DAY_1_CE.plusNanos(999_000))); // 断言：解析"999"应该得到999微秒（999,000纳秒）

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("9999999", "US"); // 解析"9999999"（超出0-999999范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "US"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampFF1() throws Exception { // 测试解析1位小数秒格式（FF1）
    assertThat(toTimestamp("1", "FF1"), is(DAY_1_CE.plusNanos(100_000_000))); // 断言：解析"1"应该得到0.1秒（100,000,000纳秒）
    assertThat(toTimestamp("9", "FF1"), is(DAY_1_CE.plusNanos(900_000_000))); // 断言：解析"9"应该得到0.9秒（900,000,000纳秒）

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("72", "FF1"); // 解析"72"（超出0-9范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "FF1"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampFF2() throws Exception { // 测试解析2位小数秒格式（FF2）
    assertThat(toTimestamp("01", "FF2"), is(DAY_1_CE.plusNanos(10_000_000))); // 断言：解析"01"应该得到0.01秒（10,000,000纳秒）
    assertThat(toTimestamp("1", "FF2"), is(DAY_1_CE.plusNanos(10_000_000))); // 断言：解析"1"应该得到0.01秒（10,000,000纳秒）
    assertThat(toTimestamp("97", "FF2"), is(DAY_1_CE.plusNanos(970_000_000))); // 断言：解析"97"应该得到0.97秒（970,000,000纳秒）

    try { // 尝试解析超出范围的值
      ZonedDateTime x = toTimestamp("999", "FF2"); // 解析"999"（超出0-99范围）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Parsed value outside of valid range")); // 断言：异常消息应该为"Parsed value outside of valid range"
    }

    try { // 尝试解析无效的值
      ZonedDateTime x = toTimestamp("abc", "FF2"); // 解析"abc"（非数字）
      fail("expected error, got " + x); // 如果没有抛出异常则测试失败
    } catch (Exception e) { // 捕获预期异常
      assertThat(e.getMessage(), is("Unable to parse value")); // 断言：异常消息应该为"Unable to parse value"
    }
  }

  @Test // 测试方法注解
  void testToTimestampFF3() throws Exception { // 测试解析3位小数秒格式（FF3）
    assertThat(toTimestamp("001", "FF3"), is(DAY_1_CE.plusNanos(1_000_000))); // 断言：解析"001"应该得到0.001秒（1,000,000纳秒）
    assertThat(toTimestamp("1", "FF3"), is(DAY_1_CE.plusNanos(1_000_000))); // 断言：解析"1"应该得到0.001秒（1,000,000纳秒）
    assertThat(toTimestamp("976", "FF3"), is(DAY_1_CE.plusNanos(976_000_000))); // 断言：解析"976"应该得到0.976秒（976,000,000纳秒）
  }

  @Test // 测试方法注解
  void testToTimestampFF4() throws Exception { // 测试解析4位小数秒格式（FF4）
    assertThat(toTimestamp("0001", "FF4"), is(DAY_1_CE.plusNanos(100_000))); // 断言：解析"0001"应该得到0.0001秒（100,000纳秒）
    assertThat(toTimestamp("1", "FF4"), is(DAY_1_CE.plusNanos(100_000))); // 断言：解析"1"应该得到0.0001秒（100,000纳秒）
    assertThat(toTimestamp("9762", "FF4"), is(DAY_1_CE.plusNanos(976_200_000))); // 断言：解析"9762"应该得到0.9762秒（976,200,000纳秒）
  }

  @Test // 测试方法注解
  void testToTimestampFF5() throws Exception { // 测试解析5位小数秒格式（FF5）
    assertThat(toTimestamp("00001", "FF5"), is(DAY_1_CE.plusNanos(10_000))); // 断言：解析"00001"应该得到0.00001秒（10,000纳秒）
    assertThat(toTimestamp("1", "FF5"), is(DAY_1_CE.plusNanos(10_000))); // 断言：解析"1"应该得到0.00001秒（10,000纳秒）
    assertThat(toTimestamp("97621", "FF5"), is(DAY_1_CE.plusNanos(976_210_000))); // 断言：解析"97621"应该得到0.97621秒（976,210,000纳秒）
  }

  @Test // 测试方法注解
  void testToTimestampFF6() throws Exception { // 测试解析6位小数秒格式（FF6）
    assertThat(toTimestamp("000001", "FF6"), is(DAY_1_CE.plusNanos(1_000))); // 断言：解析"000001"应该得到0.000001秒（1,000纳秒）
    assertThat(toTimestamp("1", "FF6"), is(DAY_1_CE.plusNanos(1_000))); // 断言：解析"1"应该得到0.000001秒（1,000纳秒）
    assertThat(toTimestamp("976214", "FF6"), is(DAY_1_CE.plusNanos(976_214_000))); // 断言：解析"976214"应该得到0.976214秒（976,214,000纳秒）
  }

  @Test // 测试方法注解
  void testToTimestampAMPM() throws Exception { // 测试解析上午/下午标记格式（AM/PM）
    assertThat(toTimestamp("03AM", "HH12AM"), is(DAY_1_CE.plusHours(3))); // 断言：解析"03AM"应该得到3点（上午）
    assertThat(toTimestamp("03AM", "HH12PM"), is(DAY_1_CE.plusHours(3))); // 断言：解析"03AM"应该得到3点（上午，格式为PM不影响解析）
    assertThat(toTimestamp("03PM", "HH12AM"), is(DAY_1_CE.plusHours(15))); // 断言：解析"03PM"应该得到15点（下午）
    assertThat(toTimestamp("03PM", "HH12PM"), is(DAY_1_CE.plusHours(15))); // 断言：解析"03PM"应该得到15点（下午，格式为PM不影响解析）
    assertThat(toTimestamp("03A.M.", "HH12A.M."), is(DAY_1_CE.plusHours(3))); // 断言：解析"03A.M."应该得到3点（上午）
    assertThat(toTimestamp("03A.M.", "HH12P.M."), is(DAY_1_CE.plusHours(3))); // 断言：解析"03A.M."应该得到3点（上午，格式为P.M.不影响解析）
    assertThat(toTimestamp("03P.M.", "HH12A.M."), is(DAY_1_CE.plusHours(15))); // 断言：解析"03P.M."应该得到15点（下午）
    assertThat(toTimestamp("03P.M.", "HH12P.M."), is(DAY_1_CE.plusHours(15))); // 断言：解析"03P.M."应该得到15点（下午，格式为P.M.不影响解析）
    assertThat(toTimestamp("03am", "HH12am"), is(DAY_1_CE.plusHours(3))); // 断言：解析"03am"应该得到3点（上午小写）
    assertThat(toTimestamp("03am", "HH12pm"), is(DAY_1_CE.plusHours(3))); // 断言：解析"03am"应该得到3点（上午小写，格式为pm不影响解析）
    assertThat(toTimestamp("03pm", "HH12am"), is(DAY_1_CE.plusHours(15))); // 断言：解析"03pm"应该得到15点（下午小写）
    assertThat(toTimestamp("03pm", "HH12pm"), is(DAY_1_CE.plusHours(15))); // 断言：解析"03pm"应该得到15点（下午小写，格式为pm不影响解析）
    assertThat(toTimestamp("03a.m.", "HH12a.m."), is(DAY_1_CE.plusHours(3))); // 断言：解析"03a.m."应该得到3点（上午小写带点）
    assertThat(toTimestamp("03a.m.", "HH12p.m."), is(DAY_1_CE.plusHours(3))); // 断言：解析"03a.m."应该得到3点（上午小写带点，格式为p.m.不影响解析）
    assertThat(toTimestamp("03p.m.", "HH12a.m."), is(DAY_1_CE.plusHours(15))); // 断言：解析"03p.m."应该得到15点（下午小写带点）
    assertThat(toTimestamp("03p.m.", "HH12p.m."), is(DAY_1_CE.plusHours(15))); // 断言：解析"03p.m."应该得到15点（下午小写带点，格式为p.m.不影响解析）
  }

  @Test // 测试方法注解
  void testToTimestampYYYYWithCommas() throws Exception { // 测试解析带逗号的年份格式（Y,YYY）
    assertThat(toTimestamp("0,001", "Y,YYY"), is(DAY_1_CE)); // 断言：解析"0,001"应该得到公元1年
    assertThat(toTimestamp("2,024", "Y,YYY"), is(JAN_1_2024)); // 断言：解析"2,024"应该得到2024年
  }

  @Test // 测试方法注解
  void testToTimestampYYYY() throws Exception { // 测试解析4位年份格式（YYYY）
    assertThat(toTimestamp("0001", "YYYY"), is(DAY_1_CE)); // 断言：解析"0001"应该得到公元1年
    assertThat(toTimestamp("1", "YYYY"), is(DAY_1_CE)); // 断言：解析"1"应该得到公元1年
    assertThat(toTimestamp("2024", "YYYY"), is(JAN_1_2024)); // 断言：解析"2024"应该得到2024年
  }

  @Test // 测试方法注解
  void testToTimestampYYY() throws Exception { // 测试解析3位年份格式（YYY，年份后3位）
    assertThat(toTimestamp("001", "YYY"), is(JAN_1_2001)); // 断言：解析"001"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("1", "YYY"), is(JAN_1_2001)); // 断言：解析"1"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("987", "YYY"), // 断言：解析"987"应该得到1987年（基于当前世纪）
        is(createDateTime(1987, 1, 1, 0, 0, 0, 0))); // 期望结果为1987年1月1日
  }

  @Test // 测试方法注解
  void testToTimestampYY() throws Exception { // 测试解析2位年份格式（YY，年份后2位）
    assertThat(toTimestamp("01", "YY"), is(JAN_1_2001)); // 断言：解析"01"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("1", "YY"), is(JAN_1_2001)); // 断言：解析"1"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("24", "YY"), is(JAN_1_2024)); // 断言：解析"24"应该得到2024年（基于当前世纪）
  }

  @Test // 测试方法注解
  void testToTimestampY() throws Exception { // 测试解析1位年份格式（Y，年份最后1位）
    assertThat(toTimestamp("1", "Y"), is(JAN_1_2001)); // 断言：解析"1"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("4", "Y"), is(JAN_1_2001.plusYears(3))); // 断言：解析"4"应该得到2004年（基于当前世纪）
  }

  @Test // 测试方法注解
  void testToTimestampIYYY() throws Exception { // 测试解析ISO 4位年份格式（IYYY）
    assertThat(toTimestamp("0001", "IYYY"), is(DAY_1_CE)); // 断言：解析"0001"应该得到公元1年
    assertThat(toTimestamp("1", "IYYY"), is(DAY_1_CE)); // 断言：解析"1"应该得到公元1年
    assertThat(toTimestamp("2024", "IYYY"), is(JAN_1_2024)); // 断言：解析"2024"应该得到2024年
  }

  @Test // 测试方法注解
  void testToTimestampIYY() throws Exception { // 测试解析ISO 3位年份格式（IYY，年份后3位）
    assertThat(toTimestamp("001", "IYY"), is(JAN_1_2001)); // 断言：解析"001"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("1", "IYY"), is(JAN_1_2001)); // 断言：解析"1"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("987", "IYY"), // 断言：解析"987"应该得到1987年（基于当前世纪）
        is(createDateTime(1987, 1, 1, 0, 0, 0, 0))); // 期望结果为1987年1月1日
  }

  @Test // 测试方法注解
  void testToTimestampIY() throws Exception { // 测试解析ISO 2位年份格式（IY，年份后2位）
    assertThat(toTimestamp("01", "IY"), is(JAN_1_2001)); // 断言：解析"01"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("1", "IY"), is(JAN_1_2001)); // 断言：解析"1"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("24", "IY"), is(JAN_1_2024)); // 断言：解析"24"应该得到2024年（基于当前世纪）
  }

  @Test // 测试方法注解
  void testToTimestampI() throws Exception { // 测试解析ISO 1位年份格式（I，年份最后1位）
    assertThat(toTimestamp("1", "I"), is(JAN_1_2001)); // 断言：解析"1"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("1", "I"), is(JAN_1_2001)); // 断言：解析"1"应该得到2001年（基于当前世纪）
    assertThat(toTimestamp("4", "I"), is(JAN_1_2001.plusYears(3))); // 断言：解析"4"应该得到2004年（基于当前世纪）
  }

  @Test // 测试方法注解
  void testToTimestampBCAD() throws Exception { // 测试解析纪元标记格式（BC/AD）
    assertThat(toTimestamp("1920BC", "YYYYBC").get(ChronoField.ERA), is(0)); // 断言：解析"1920BC"应该得到公元前纪元（ERA=0）
    assertThat(toTimestamp("1920BC", "YYYYAD").get(ChronoField.ERA), is(0)); // 断言：解析"1920BC"应该得到公元前纪元（ERA=0，格式为AD不影响）
    assertThat(toTimestamp("1920AD", "YYYYBC").get(ChronoField.ERA), is(1)); // 断言：解析"1920AD"应该得到公元纪元（ERA=1，格式为BC不影响）
    assertThat(toTimestamp("1920AD", "YYYYAD").get(ChronoField.ERA), is(1)); // 断言：解析"1920AD"应该得到公元纪元（ERA=1）
    assertThat(toTimestamp("1920B.C.", "YYYYB.C.").get(ChronoField.ERA), is(0)); // 断言：解析"1920B.C."应该得到公元前纪元（ERA=0）
    assertThat(toTimestamp("1920B.C.", "YYYYA.D.").get(ChronoField.ERA), is(0)); // 断言：解析"1920B.C."应该得到公元前纪元（ERA=0，格式为A.D.不影响）
    assertThat(toTimestamp("1920A.D.", "YYYYB.C.").get(ChronoField.ERA), is(1)); // 断言：解析"1920A.D."应该得到公元纪元（ERA=1，格式为B.C.不影响）
    assertThat(toTimestamp("1920A.D.", "YYYYA.D.").get(ChronoField.ERA), is(1)); // 断言：解析"1920A.D."应该得到公元纪元（ERA=1）
    assertThat(toTimestamp("1920bc", "YYYYbc").get(ChronoField.ERA), is(0)); // 断言：解析"1920bc"应该得到公元前纪元（ERA=0，小写）
    assertThat(toTimestamp("1920bc", "YYYYad").get(ChronoField.ERA), is(0)); // 断言：解析"1920bc"应该得到公元前纪元（ERA=0，小写，格式为ad不影响）
    assertThat(toTimestamp("1920ad", "YYYYbc").get(ChronoField.ERA), is(1)); // 断言：解析"1920ad"应该得到公元纪元（ERA=1，小写，格式为bc不影响）
    assertThat(toTimestamp("1920ad", "YYYYad").get(ChronoField.ERA), is(1)); // 断言：解析"1920ad"应该得到公元纪元（ERA=1，小写）
    assertThat(toTimestamp("1920b.c.", "YYYYb.c.").get(ChronoField.ERA), is(0)); // 断言：解析"1920b.c."应该得到公元前纪元（ERA=0，小写带点）
    assertThat(toTimestamp("1920b.c.", "YYYYa.d.").get(ChronoField.ERA), is(0)); // 断言：解析"1920b.c."应该得到公元前纪元（ERA=0，小写带点，格式为a.d.不影响）
    assertThat(toTimestamp("1920a.d.", "YYYYb.c.").get(ChronoField.ERA), is(1)); // 断言：解析"1920a.d."应该得到公元纪元（ERA=1，小写带点，格式为b.c.不影响）
    assertThat(toTimestamp("1920a.d.", "YYYYa.d.").get(ChronoField.ERA), is(1)); // 断言：解析"1920a.d."应该得到公元纪元（ERA=1，小写带点）
  }

  @Test // 测试方法注解
  void testToTimestampMonthUpperCase() throws Exception { // 测试解析月份全称大写格式（MONTH）
    assertThat(toTimestamp("JANUARY", "MONTH"), is(DAY_1_CE)); // 断言：解析"JANUARY"应该得到1月
    assertThat(toTimestamp("MARCH", "MONTH"), is(DAY_1_CE.plusMonths(2))); // 断言：解析"MARCH"应该得到3月
    assertThat(toTimestamp("NOVEMBER", "MONTH"), is(DAY_1_CE.plusMonths(10))); // 断言：解析"NOVEMBER"应该得到11月
  }

  @Test // 测试方法注解
  void testToTimestampMonthCapitalized() throws Exception { // 测试解析月份全称首字母大写格式（Month）
    assertThat(toTimestamp("January", "Month"), is(DAY_1_CE)); // 断言：解析"January"应该得到1月
    assertThat(toTimestamp("March", "Month"), is(DAY_1_CE.plusMonths(2))); // 断言：解析"March"应该得到3月
    assertThat(toTimestamp("November", "Month"), is(DAY_1_CE.plusMonths(10))); // 断言：解析"November"应该得到11月
  }

  @Test // 测试方法注解
  void testToTimestampMonthLowerCase() throws Exception { // 测试解析月份全称小写格式（month）
    assertThat(toTimestamp("january", "month"), is(DAY_1_CE)); // 断言：解析"january"应该得到1月
    assertThat(toTimestamp("march", "month"), is(DAY_1_CE.plusMonths(2))); // 断言：解析"march"应该得到3月
    assertThat(toTimestamp("november", "month"), is(DAY_1_CE.plusMonths(10))); // 断言：解析"november"应该得到11月
  }

  @Test // 测试方法注解
  void testToTimestampMonUpperCase() throws Exception { // 测试解析月份简称大写格式（MON）
    assertThat(toTimestamp("JAN", "MON"), is(DAY_1_CE)); // 断言：解析"JAN"应该得到1月
    assertThat(toTimestamp("MAR", "MON"), is(DAY_1_CE.plusMonths(2))); // 断言：解析"MAR"应该得到3月
    assertThat(toTimestamp("NOV", "MON"), is(DAY_1_CE.plusMonths(10))); // 断言：解析"NOV"应该得到11月
  }

  @Test // 测试方法注解
  void testToTimestampMonCapitalized() throws Exception { // 测试解析月份简称首字母大写格式（Mon）
    assertThat(toTimestamp("Jan", "Mon"), is(DAY_1_CE)); // 断言：解析"Jan"应该得到1月
    assertThat(toTimestamp("Mar", "Mon"), is(DAY_1_CE.plusMonths(2))); // 断言：解析"Mar"应该得到3月
    assertThat(toTimestamp("Nov", "Mon"), is(DAY_1_CE.plusMonths(10))); // 断言：解析"Nov"应该得到11月
  }

  @Test // 测试方法注解
  void testToTimestampMonLowerCase() throws Exception { // 测试解析月份简称小写格式（mon）
    assertThat(toTimestamp("jan", "mon"), is(DAY_1_CE)); // 断言：解析"jan"应该得到1月
    assertThat(toTimestamp("mar", "mon"), is(DAY_1_CE.plusMonths(2))); // 断言：解析"mar"应该得到3月
    assertThat(toTimestamp("nov", "mon"), is(DAY_1_CE.plusMonths(10))); // 断言：解析"nov"应该得到11月
  }

  @Test // 测试方法注解
  void testToTimestampMM() throws Exception { // 测试解析月份数字格式（MM）
    assertThat(toTimestamp("01", "MM"), is(DAY_1_CE)); // 断言：解析"01"应该得到1月
    assertThat(toTimestamp("1", "MM"), is(DAY_1_CE)); // 断言：解析"1"应该得到1月
    assertThat(toTimestamp("11", "MM"), is(DAY_1_CE.plusMonths(10))); // 断言：解析"11"应该得到11月
  }

  @Test // 测试方法注解
  void testToTimestampDayUpperCase() throws Exception { // 测试解析星期全称大写格式（DAY）
    assertThat(toTimestamp("1982 23 MONDAY", "IYYY IW DAY"), // 断言：解析"1982 23 MONDAY"应该得到1982年第23周的周一
        is(createDateTime(1982, 6, 7, 0, 0, 0, 0))); // 期望结果为1982年6月7日
    assertThat(toTimestamp("1982 23 THURSDAY", "IYYY IW DAY"), // 断言：解析"1982 23 THURSDAY"应该得到1982年第23周的周四
        is(createDateTime(1982, 6, 10, 0, 0, 0, 0))); // 期望结果为1982年6月10日
    assertThat(toTimestamp("1982 23 FRIDAY", "IYYY IW DAY"), // 断言：解析"1982 23 FRIDAY"应该得到1982年第23周的周五
        is(createDateTime(1982, 6, 11, 0, 0, 0, 0))); // 期望结果为1982年6月11日
  }

  @Test // 测试方法注解
  void testToTimestampDayCapitalized() throws Exception { // 测试解析星期全称首字母大写格式（Day）
    assertThat(toTimestamp("1982 23 Monday", "IYYY IW Day"), // 断言：解析"1982 23 Monday"应该得到1982年第23周的周一
        is(createDateTime(1982, 6, 7, 0, 0, 0, 0))); // 期望结果为1982年6月7日
    assertThat(toTimestamp("1982 23 Thursday", "IYYY IW Day"), // 断言：解析"1982 23 Thursday"应该得到1982年第23周的周四
        is(createDateTime(1982, 6, 10, 0, 0, 0, 0))); // 期望结果为1982年6月10日
    assertThat(toTimestamp("1982 23 Friday", "IYYY IW Day"), // 断言：解析"1982 23 Friday"应该得到1982年第23周的周五
        is(createDateTime(1982, 6, 11, 0, 0, 0, 0))); // 期望结果为1982年6月11日
  }

  @Test // 测试方法注解
  void testToTimestampDayLowerCase() throws Exception { // 测试解析星期全称小写格式（day）
    assertThat(toTimestamp("1982 23 monday", "IYYY IW day"), // 断言：解析"1982 23 monday"应该得到1982年第23周的周一
        is(createDateTime(1982, 6, 7, 0, 0, 0, 0))); // 期望结果为1982年6月7日
    assertThat(toTimestamp("1982 23 thursday", "IYYY IW day"), // 断言：解析"1982 23 thursday"应该得到1982年第23周的周四
        is(createDateTime(1982, 6, 10, 0, 0, 0, 0))); // 期望结果为1982年6月10日
    assertThat(toTimestamp("1982 23 friday", "IYYY IW day"), // 断言：解析"1982 23 friday"应该得到1982年第23周的周五
        is(createDateTime(1982, 6, 11, 0, 0, 0, 0))); // 期望结果为1982年6月11日
  }

  @Test // 测试方法注解
  void testToTimestampDyUpperCase() throws Exception { // 测试解析星期简称大写格式（DY）
    assertThat(toTimestamp("1982 23 MON", "IYYY IW DY"), // 断言：解析"1982 23 MON"应该得到1982年第23周的周一
        is(createDateTime(1982, 6, 7, 0, 0, 0, 0))); // 期望结果为1982年6月7日
    assertThat(toTimestamp("1982 23 THU", "IYYY IW DY"), // 断言：解析"1982 23 THU"应该得到1982年第23周的周四
        is(createDateTime(1982, 6, 10, 0, 0, 0, 0))); // 期望结果为1982年6月10日
    assertThat(toTimestamp("1982 23 FRI", "IYYY IW DY"), // 断言：解析"1982 23 FRI"应该得到1982年第23周的周五
        is(createDateTime(1982, 6, 11, 0, 0, 0, 0))); // 期望结果为1982年6月11日
  }

  @Test // 测试方法注解
  void testToTimestampDyCapitalized() throws Exception { // 测试解析星期简称首字母大写格式（Dy）
    assertThat(toTimestamp("1982 23 Mon", "IYYY IW Dy"), // 断言：解析"1982 23 Mon"应该得到1982年第23周的周一
        is(createDateTime(1982, 6, 7, 0, 0, 0, 0))); // 期望结果为1982年6月7日
    assertThat(toTimestamp("1982 23 Thu", "IYYY IW Dy"), // 断言：解析"1982 23 Thu"应该得到1982年第23周的周四
        is(createDateTime(1982, 6, 10, 0, 0, 0, 0))); // 期望结果为1982年6月10日
    assertThat(toTimestamp("1982 23 Fri", "IYYY IW Dy"), // 断言：解析"1982 23 Fri"应该得到1982年第23周的周五
        is(createDateTime(1982, 6, 11, 0, 0, 0, 0))); // 期望结果为1982年6月11日
  }

  @Test // 测试方法注解
  void testToTimestampDyLowerCase() throws Exception { // 测试解析星期简称小写格式（dy）
    assertThat(toTimestamp("1982 23 mon", "IYYY IW dy"), // 断言：解析"1982 23 mon"应该得到1982年第23周的周一
        is(createDateTime(1982, 6, 7, 0, 0, 0, 0))); // 期望结果为1982年6月7日
    assertThat(toTimestamp("1982 23 thu", "IYYY IW dy"), // 断言：解析"1982 23 thu"应该得到1982年第23周的周四
        is(createDateTime(1982, 6, 10, 0, 0, 0, 0))); // 期望结果为1982年6月10日
    assertThat(toTimestamp("1982 23 fri", "IYYY IW dy"), // 断言：解析"1982 23 fri"应该得到1982年第23周的周五
        is(createDateTime(1982, 6, 11, 0, 0, 0, 0))); // 期望结果为1982年6月11日
  }

  @Test // 测试方法注解
  void testToTimestampDDD() throws Exception { // 测试解析年中的天数格式（DDD）
    assertThat(toTimestamp("2024 001", "YYYY DDD"), is(JAN_1_2024)); // 断言：解析"2024 001"应该得到2024年第1天
    assertThat(toTimestamp("2024 1", "YYYY DDD"), is(JAN_1_2024)); // 断言：解析"2024 1"应该得到2024年第1天
    assertThat(toTimestamp("2024 137", "YYYY DDD"), // 断言：解析"2024 137"应该得到2024年第137天
        is(createDateTime(2024, 5, 16, 0, 0, 0, 0))); // 期望结果为2024年5月16日
  }

  @Test // 测试方法注解
  void testToTimestampDD() throws Exception { // 测试解析月份中的天数格式（DD）
    assertThat(toTimestamp("01", "DD"), is(DAY_1_CE)); // 断言：解析"01"应该得到1日
    assertThat(toTimestamp("1", "DD"), is(DAY_1_CE)); // 断言：解析"1"应该得到1日
    assertThat(toTimestamp("23", "DD"), is(DAY_1_CE.plusDays(22))); // 断言：解析"23"应该得到23日
  }

  @Test // 测试方法注解
  void testToTimestampIDDD() throws Exception { // 测试解析ISO年中的天数格式（IDDD）
    assertThat(toTimestamp("2020 001", "IYYY IDDD"), // 断言：解析"2020 001"应该得到2020年ISO年第1天
        is(createDateTime(2019, 12, 30, 0, 0, 0, 0))); // 期望结果为2019年12月30日（2020年ISO年第1天）
    assertThat(toTimestamp("2020 1", "IYYY IDDD"), // 断言：解析"2020 1"应该得到2020年ISO年第1天
        is(createDateTime(2019, 12, 30, 0, 0, 0, 0))); // 期望结果为2019年12月30日（2020年ISO年第1天）
    assertThat(toTimestamp("2020 137", "IYYY IDDD"), // 断言：解析"2020 137"应该得到2020年ISO年第137天
        is(createDateTime(2020, 5, 14, 0, 0, 0, 0))); // 期望结果为2020年5月14日
  }

  @Test // 测试方法注解
  void testToTimestampID() throws Exception { // 测试解析ISO周中的星期几格式（ID）
    assertThat(toTimestamp("1982 23 1", "IYYY IW ID"), // 断言：解析"1982 23 1"应该得到1982年ISO第23周的周一
        is(createDateTime(1982, 6, 7, 0, 0, 0, 0))); // 期望结果为1982年6月7日
    assertThat(toTimestamp("1982 23 4", "IYYY IW ID"), // 断言：解析"1982 23 4"应该得到1982年ISO第23周的周四
        is(createDateTime(1982, 6, 10, 0, 0, 0, 0))); // 期望结果为1982年6月10日
    assertThat(toTimestamp("1982 23 5", "IYYY IW ID"), // 断言：解析"1982 23 5"应该得到1982年ISO第23周的周五
        is(createDateTime(1982, 6, 11, 0, 0, 0, 0))); // 期望结果为1982年6月11日
  }

  @Test // 测试方法注解
  void testToTimestampW() throws Exception { // 测试解析月中的周数格式（W）
    assertThat(toTimestamp("2024 1 1", "YYYY MM W"), is(JAN_1_2024)); // 断言：解析"2024 1 1"应该得到2024年1月第1周
    assertThat(toTimestamp("2024 4 2", "YYYY MM W"), // 断言：解析"2024 4 2"应该得到2024年4月第2周
        is(createDateTime(2024, 4, 8, 0, 0, 0, 0))); // 期望结果为2024年4月8日
    assertThat(toTimestamp("2024 11 4", "YYYY MM W"), // 断言：解析"2024 11 4"应该得到2024年11月第4周
        is(createDateTime(2024, 11, 22, 0, 0, 0, 0))); // 期望结果为2024年11月22日
  }

  @Test // 测试方法注解
  void testToTimestampWW() throws Exception { // 测试解析年中的周数格式（WW）
    assertThat(toTimestamp("2024 01", "YYYY WW"), is(JAN_1_2024)); // 断言：解析"2024 01"应该得到2024年第1周
    assertThat(toTimestamp("2024 1", "YYYY WW"), is(JAN_1_2024)); // 断言：解析"2024 1"应该得到2024年第1周
    assertThat(toTimestamp("2024 51", "YYYY WW"), // 断言：解析"2024 51"应该得到2024年第51周
        is(createDateTime(2024, 12, 16, 0, 0, 0, 0))); // 期望结果为2024年12月16日
  }

  @Test // 测试方法注解
  void testToTimestampIW() throws Exception { // 测试解析ISO周数格式（IW）
    assertThat(toTimestamp("2020 01", "IYYY IW"), // 断言：解析"2020 01"应该得到2020年ISO第1周
        is(createDateTime(2019, 12, 30, 0, 0, 0, 0))); // 期望结果为2019年12月30日（2020年ISO第1周）
    assertThat(toTimestamp("2020 1", "IYYY IW"), // 断言：解析"2020 1"应该得到2020年ISO第1周
        is(createDateTime(2019, 12, 30, 0, 0, 0, 0))); // 期望结果为2019年12月30日（2020年ISO第1周）
    assertThat(toTimestamp("2020 51", "IYYY IW"), // 断言：解析"2020 51"应该得到2020年ISO第51周
        is(createDateTime(2020, 12, 14, 0, 0, 0, 0))); // 期望结果为2020年12月14日
  }

  @Test // 测试方法注解
  void testToTimestampCC() throws Exception { // 测试解析世纪格式（CC）
    assertThat(toTimestamp("21", "CC"), is(JAN_1_2001)); // 断言：解析"21"应该得到21世纪（2001年）
    assertThat(toTimestamp("16", "CC"), // 断言：解析"16"应该得到16世纪（1501年）
        is(createDateTime(1501, 1, 1, 0, 0, 0, 0))); // 期望结果为1501年1月1日
    assertThat(toTimestamp("1", "CC"), is(DAY_1_CE)); // 断言：解析"1"应该得到1世纪（公元1年）
  }

  @Test // 测试方法注解
  void testToTimestampJ() throws Exception { // 测试解析儒略日格式（J）
    assertThat(toTimestamp("2460311", "J"), is(JAN_1_2024)); // 断言：解析"2460311"应该得到2024年1月1日
    assertThat(toTimestamp("2445897", "J"), // 断言：解析"2445897"应该得到1984年7月15日
        is(createDateTime(1984, 7, 15, 0, 0, 0, 0))); // 期望结果为1984年7月15日
    assertThat(toTimestamp("1806606", "J"), // 断言：解析"1806606"应该得到234年3月21日
        is(createDateTime(234, 3, 21, 0, 0, 0, 0))); // 期望结果为234年3月21日
  }

  @Test // 测试方法注解
  void testToTimestampRMUpperCase() throws Exception { // 测试解析罗马数字月份格式大写（RM）
    assertThat(toTimestamp("I", "RM"), is(DAY_1_CE)); // 断言：解析"I"应该得到1月
    assertThat(toTimestamp("IV", "RM"), is(DAY_1_CE.plusMonths(3))); // 断言：解析"IV"应该得到4月
    assertThat(toTimestamp("IX", "RM"), is(DAY_1_CE.plusMonths(8))); // 断言：解析"IX"应该得到9月
  }

  @Test // 测试方法注解
  void testToTimestampRMLowerCase() throws Exception { // 测试解析罗马数字月份格式小写（rm）
    assertThat(toTimestamp("i", "rm"), is(DAY_1_CE)); // 断言：解析"i"应该得到1月
    assertThat(toTimestamp("iv", "rm"), is(DAY_1_CE.plusMonths(3))); // 断言：解析"iv"应该得到4月
    assertThat(toTimestamp("ix", "rm"), is(DAY_1_CE.plusMonths(8))); // 断言：解析"ix"应该得到9月
  }

  @Test // 测试方法注解
  void testToTimestampDateValidFormats() throws Exception { // 测试解析各种有效的日期格式
    assertThat(toTimestamp("2024-04-17", "YYYY-MM-DD"), is(APR_17_2024)); // 断言：解析"2024-04-17"应该得到2024年4月17日
    assertThat(toTimestamp("2,024-04-17", "Y,YYY-MM-DD"), is(APR_17_2024)); // 断言：解析"2,024-04-17"应该得到2024年4月17日
    assertThat(toTimestamp("24-04-17", "YYY-MM-DD"), is(APR_17_2024)); // 断言：解析"24-04-17"应该得到2024年4月17日
    assertThat(toTimestamp("24-04-17", "YY-MM-DD"), is(APR_17_2024)); // 断言：解析"24-04-17"应该得到2024年4月17日
    assertThat(toTimestamp("2124-04-17", "CCYY-MM-DD"), is(APR_17_2024)); // 断言：解析"2124-04-17"应该得到2024年4月17日
    assertThat(toTimestamp("20240417", "YYYYMMDD"), is(APR_17_2024)); // 断言：解析"20240417"应该得到2024年4月17日
    assertThat(toTimestamp("2,0240417", "Y,YYYMMDD"), is(APR_17_2024)); // 断言：解析"2,0240417"应该得到2024年4月17日
    assertThat(toTimestamp("2024-16-3", "IYYY-IW-ID"), is(APR_17_2024)); // 断言：解析"2024-16-3"应该得到2024年ISO第16周周三
    assertThat(toTimestamp("2024-16 Wednesday", "IYYY-IW Day"), is(APR_17_2024)); // 断言：解析"2024-16 Wednesday"应该得到2024年ISO第16周周三
    assertThat(toTimestamp("2024-108", "IYYY-IDDD"), is(APR_17_2024)); // 断言：解析"2024-108"应该得到2024年ISO年第108天
    assertThat(toTimestamp("April 17, 2024", "Month DD, YYYY"), is(APR_17_2024)); // 断言：解析"April 17, 2024"应该得到2024年4月17日
    assertThat(toTimestamp("IV 17, 2024", "RM DD, YYYY"), is(APR_17_2024)); // 断言：解析"IV 17, 2024"应该得到2024年4月17日
    assertThat(toTimestamp("APR 17, 2024", "MON DD, YYYY"), is(APR_17_2024)); // 断言：解析"APR 17, 2024"应该得到2024年4月17日
    assertThat(toTimestamp("2024-16", "YYYY-WW"), // 断言：解析"2024-16"应该得到2024年第16周
        is(createDateTime(2024, 4, 15, 0, 0, 0, 0))); // 期望结果为2024年4月15日
    assertThat(toTimestamp("2024-108", "YYYY-DDD"), is(APR_17_2024)); // 断言：解析"2024-108"应该得到2024年第108天
    assertThat(toTimestamp("0000-01-01", "YYYY-MM-DD"), is(DAY_1_CE)); // 断言：解析"0000-01-01"应该得到公元1年1月1日
  }

  @Test // 测试方法注解
  void testToTimestampWithTimezone() throws Exception { // 测试解析带时区的日期时间格式
    final ZoneId utcZone = ZoneId.of("UTC"); // 创建UTC时区对象
    final CompiledDateTimeFormat dateTimeFormat = // 声明编译后的日期时间格式对象
        PostgresqlDateTimeFormatter.compilePattern("YYYY-MM-DD HH24:MI:SSTZH:TZM"); // 编译带时区的日期时间格式模式
    assertThat( // 断言：验证带时区的日期时间解析
        dateTimeFormat.parseDateTime("2024-04-17 00:00:00-07:00", utcZone, // 解析带时区的日期时间字符串
            Locale.US), // 使用美式英语地区设置
        is(APR_17_2024.plusHours(7).withZoneSameLocal(utcZone))); // 期望结果为UTC时区的2024年4月17日7点（考虑时区偏移）
  }

  @Test // 测试方法注解
  void testToTimestampReuseFormat() { // 测试重用编译后的格式进行格式化
    final CompiledDateTimeFormat compiledFormat = // 声明编译后的日期时间格式对象
        PostgresqlDateTimeFormatter.compilePattern("YYYY-MM-DD HH24:MI:SS.MS"); // 编译完整的日期时间格式模式
    final String expected1 = "2019-03-07 15:46:23.521"; // 第一个期望的格式化结果
    final String expected2 = "1983-11-29 04:21:16.045"; // 第二个期望的格式化结果
    final String expected3 = "2024-09-24 14:53:37.891"; // 第三个期望的格式化结果
    final ZonedDateTime timestamp1 = // 第一个日期时间对象
        createDateTime(2019, 3, 7, 15, 46, 23, 521000000); // 创建2019年3月7日15点46分23秒521毫秒
    final ZonedDateTime timestamp2 = // 第二个日期时间对象
        createDateTime(1983, 11, 29, 4, 21, 16, 45000000); // 创建1983年11月29日4点21分16秒45毫秒
    final ZonedDateTime timestamp3 = // 第三个日期时间对象
        createDateTime(2024, 9, 24, 14, 53, 37, 891000000); // 创建2024年9月24日14点53分37秒891毫秒
    assertThat(compiledFormat.formatDateTime(timestamp1, Locale.US), // 断言：验证第一个日期时间格式化结果
        is(expected1)); // 期望结果为expected1
    assertThat(compiledFormat.formatDateTime(timestamp2, Locale.US), // 断言：验证第二个日期时间格式化结果
        is(expected2)); // 期望结果为expected2
    assertThat(compiledFormat.formatDateTime(timestamp3, Locale.US), // 断言：验证第三个日期时间格式化结果
        is(expected3)); // 期望结果为expected3
  }

  protected static ZonedDateTime createDateTime(int year, int month, int dayOfMonth, int hour, // 辅助方法：创建带时区的日期时间对象
      int minute, int seconds, int nanoseconds) { // 方法参数：年、月、日、时、分、秒、纳秒
    return ZonedDateTime.of( // 创建并返回ZonedDateTime对象
        LocalDateTime.of(year, month, dayOfMonth, hour, minute, seconds, nanoseconds), // 创建LocalDateTime对象
        TIME_ZONE); // 使用预设的时区创建ZonedDateTime
  }
}
// 类结束括号