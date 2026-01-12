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
package org.apache.calcite.sql.parser;

import org.apache.calcite.avatica.util.TimeUnit;
import org.apache.calcite.sql.SqlIntervalQualifier;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests {@link SqlParserUtil}. Currently, this test focuses on tests for the methods that work
 * with {@link SqlIntervalQualifier}. It may be expanded to test other functionality in the future.
 * 测试 SqlParserUtil 工具类，当前主要测试与 SqlIntervalQualifier（SQL时间间隔限定符）相关的方法
 * SqlParserUtil 是 Calcite SQL 解析器的核心工具类，提供了多种解析和转换 SQL 语句的实用方法
 * 本测试类重点验证时间间隔值到毫秒数或月数的转换功能，确保 SQL 中的 INTERVAL 表达式能正确转换为内部数值表示
 */
public class SqlParserUtilTest { // SqlParserUtil 工具类的测试类，用于验证 SQL 解析工具类的核心功能
  private static final SqlParserPos POSITION = SqlParserPos.ZERO; // 静态常量：SQL 解析位置对象，用于标记 SQL 语句中元素的起始位置，ZERO 表示位置为 0（即不关心具体位置）

  @Test void testSecondIntervalToMillis() { // 测试方法：验证将秒级时间间隔转换为毫秒数的功能，测试包含小数秒的情况
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为秒
        new SqlIntervalQualifier(TimeUnit.SECOND, null, POSITION); // TimeUnit.SECOND 表示起始时间单位为秒，null 表示无结束时间单位（即只有秒），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2.1", qualifier), equalTo(2_100L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2.1" 秒转换为毫秒数，期望结果为 2100 毫秒（2.1 秒 = 2100 毫秒）
  }

  @Test void testMinuteIntervalToMillis() { // 测试方法：验证将分钟级时间间隔转换为毫秒数的功能
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为分钟
        new SqlIntervalQualifier(TimeUnit.MINUTE, null, POSITION); // TimeUnit.MINUTE 表示起始时间单位为分钟，null 表示无结束时间单位（即只有分钟），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2", qualifier), equalTo(120_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2" 分钟转换为毫秒数，期望结果为 120000 毫秒（2 分钟 = 2 * 60 * 1000 = 120000 毫秒）
  }

  @Test void testMinuteToSecondIntervalToMillis() { // 测试方法：验证将分钟到秒的时间间隔转换为毫秒数的功能，测试复合时间单位（分钟:秒）
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定起始时间单位为分钟，结束时间单位为秒
        new SqlIntervalQualifier(TimeUnit.MINUTE, TimeUnit.SECOND, POSITION); // TimeUnit.MINUTE 表示起始时间单位为分钟，TimeUnit.SECOND 表示结束时间单位为秒，POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2:30", qualifier), equalTo(150_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2:30"（2分30秒）转换为毫秒数，期望结果为 150000 毫秒（2分30秒 = 2*60*1000 + 30*1000 = 150000 毫秒）
  }

  @Test void testHourIntervalToMillis() { // 测试方法：验证将小时级时间间隔转换为毫秒数的功能
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为小时
        new SqlIntervalQualifier(TimeUnit.HOUR, null, POSITION); // TimeUnit.HOUR 表示起始时间单位为小时，null 表示无结束时间单位（即只有小时），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2", qualifier), equalTo(7_200_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2" 小时转换为毫秒数，期望结果为 7200000 毫秒（2 小时 = 2 * 60 * 60 * 1000 = 7200000 毫秒）
  }

  @Test void testHourToMinuteIntervalToMillis() { // 测试方法：验证将小时到分钟的时间间隔转换为毫秒数的功能，测试复合时间单位（小时:分钟）
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定起始时间单位为小时，结束时间单位为分钟
        new SqlIntervalQualifier(TimeUnit.HOUR, TimeUnit.MINUTE, POSITION); // TimeUnit.HOUR 表示起始时间单位为小时，TimeUnit.MINUTE 表示结束时间单位为分钟，POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2:03", qualifier), equalTo(7_380_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2:03"（2小时3分钟）转换为毫秒数，期望结果为 7380000 毫秒（2小时3分钟 = 2*60*60*1000 + 3*60*1000 = 7380000 毫秒）
  }

  @Test void testHourToSecondIntervalToMillis() { // 测试方法：验证将小时到秒的时间间隔转换为毫秒数的功能，测试复合时间单位（小时:分钟:秒）
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定起始时间单位为小时，结束时间单位为秒
        new SqlIntervalQualifier(TimeUnit.HOUR, TimeUnit.SECOND, POSITION); // TimeUnit.HOUR 表示起始时间单位为小时，TimeUnit.SECOND 表示结束时间单位为秒，POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2:03:30", qualifier), equalTo(7_410_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2:03:30"（2小时3分30秒）转换为毫秒数，期望结果为 7410000 毫秒（2小时3分30秒 = 2*60*60*1000 + 3*60*1000 + 30*1000 = 7410000 毫秒）
  }

  @Test void testDayIntervalToMillis() { // 测试方法：验证将天级时间间隔转换为毫秒数的功能
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为天
        new SqlIntervalQualifier(TimeUnit.DAY, null, POSITION); // TimeUnit.DAY 表示起始时间单位为天，null 表示无结束时间单位（即只有天），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2", qualifier), equalTo(172_800_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2" 天转换为毫秒数，期望结果为 172800000 毫秒（2 天 = 2 * 24 * 60 * 60 * 1000 = 172800000 毫秒）
  }

  @Test void testDayToHourIntervalToMillis() { // 测试方法：验证将天到小时的时间间隔转换为毫秒数的功能，测试复合时间单位（天 小时），注意这里使用空格分隔
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定起始时间单位为天，结束时间单位为小时
        new SqlIntervalQualifier(TimeUnit.DAY, TimeUnit.HOUR, POSITION); // TimeUnit.DAY 表示起始时间单位为天，TimeUnit.HOUR 表示结束时间单位为小时，POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2 1", qualifier), equalTo(176_400_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2 1"（2天1小时）转换为毫秒数，期望结果为 176400000 毫秒（2天1小时 = 2*24*60*60*1000 + 1*60*60*1000 = 176400000 毫秒）
  }

  @Test void testDayToMinuteIntervalToMillis() { // 测试方法：验证将天到分钟的时间间隔转换为毫秒数的功能，测试复合时间单位（天 小时:分钟），注意这里使用空格和冒号分隔
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定起始时间单位为天，结束时间单位为分钟
        new SqlIntervalQualifier(TimeUnit.DAY, TimeUnit.MINUTE, POSITION); // TimeUnit.DAY 表示起始时间单位为天，TimeUnit.MINUTE 表示结束时间单位为分钟，POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2 1:03", qualifier), equalTo(176_580_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2 1:03"（2天1小时3分钟）转换为毫秒数，期望结果为 176580000 毫秒（2天1小时3分钟 = 2*24*60*60*1000 + 1*60*60*1000 + 3*60*1000 = 176580000 毫秒）
  }

  @Test void testDayToSecondIntervalToMillis() { // 测试方法：验证将天到秒的时间间隔转换为毫秒数的功能，测试复合时间单位（天 小时:分钟:秒），注意这里使用空格和冒号分隔
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定起始时间单位为天，结束时间单位为秒
        new SqlIntervalQualifier(TimeUnit.DAY, TimeUnit.SECOND, POSITION); // TimeUnit.DAY 表示起始时间单位为天，TimeUnit.SECOND 表示结束时间单位为秒，POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2 1:02:30", qualifier), equalTo(176_550_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2 1:02:30"（2天1小时2分30秒）转换为毫秒数，期望结果为 176550000 毫秒（2天1小时2分30秒 = 2*24*60*60*1000 + 1*60*60*1000 + 2*60*1000 + 30*1000 = 176550000 毫秒）
  }

  @Test void testWeekIntervalToMillis() { // 测试方法：验证将周级时间间隔转换为毫秒数的功能
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为周
        new SqlIntervalQualifier(TimeUnit.WEEK, null, POSITION); // TimeUnit.WEEK 表示起始时间单位为周，null 表示无结束时间单位（即只有周），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2", qualifier), equalTo(1_209_600_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2" 周转换为毫秒数，期望结果为 1209600000 毫秒（2 周 = 2 * 7 * 24 * 60 * 60 * 1000 = 1209600000 毫秒）
  }

  @Test void testMonthIntervalToMonths() { // 测试方法：验证将月级时间间隔转换为月数的功能（注意：方法名虽然叫 testMonthIntervalToMonths，但实际测试的是周，这可能是代码错误或命名错误）
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为周（注意：这里使用的是 WEEK 而不是 MONTH，与方法名不符）
        new SqlIntervalQualifier(TimeUnit.WEEK, null, POSITION); // TimeUnit.WEEK 表示起始时间单位为周，null 表示无结束时间单位（即只有周），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMillis("2", qualifier), equalTo(1_209_600_000L)); // 调用 SqlParserUtil.intervalToMillis 方法将字符串 "2" 周转换为毫秒数，期望结果为 1209600000 毫秒（2 周 = 2 * 7 * 24 * 60 * 60 * 1000 = 1209600000 毫秒）
  }

  @Test void testQuarterIntervalToMonths() { // 测试方法：验证将季度时间间隔转换为月数的功能
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为季度
        new SqlIntervalQualifier(TimeUnit.QUARTER, null, POSITION); // TimeUnit.QUARTER 表示起始时间单位为季度，null 表示无结束时间单位（即只有季度），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMonths("2", qualifier), equalTo(6L)); // 调用 SqlParserUtil.intervalToMonths 方法将字符串 "2" 季度转换为月数，期望结果为 6 个月（2 季度 = 2 * 3 = 6 个月）
  }

  @Test void testYearIntervalToMonths() { // 测试方法：验证将年级时间间隔转换为月数的功能
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定时间单位为年
        new SqlIntervalQualifier(TimeUnit.YEAR, null, POSITION); // TimeUnit.YEAR 表示起始时间单位为年，null 表示无结束时间单位（即只有年），POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMonths("2", qualifier), equalTo(24L)); // 调用 SqlParserUtil.intervalToMonths 方法将字符串 "2" 年转换为月数，期望结果为 24 个月（2 年 = 2 * 12 = 24 个月）
  }

  @Test void testYearToMonthIntervalToMonths() { // 测试方法：验证将年到月的时间间隔转换为月数的功能，测试复合时间单位（年-月），注意这里使用连字符分隔
    final SqlIntervalQualifier qualifier = // 创建 SQL 时间间隔限定符对象，指定起始时间单位为年，结束时间单位为月
        new SqlIntervalQualifier(TimeUnit.YEAR, TimeUnit.MONTH, POSITION); // TimeUnit.YEAR 表示起始时间单位为年，TimeUnit.MONTH 表示结束时间单位为月，POSITION 指定在 SQL 中的位置
    assertThat(SqlParserUtil.intervalToMonths("2-3", qualifier), equalTo(27L)); // 调用 SqlParserUtil.intervalToMonths 方法将字符串 "2-3"（2年3个月）转换为月数，期望结果为 27 个月（2年3个月 = 2 * 12 + 3 = 27 个月）
  }
} // 类定义结束
