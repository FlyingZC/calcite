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
// 声明包名，表示这个类属于org.apache.calcite.test包
package org.apache.calcite.test;

// 导入DruidDateTimeUtils类，用于处理Druid日期时间工具方法
import org.apache.calcite.adapter.druid.DruidDateTimeUtils;
// 导入TimeUnitRange枚举，表示时间单位范围（如YEAR、MONTH、DAY等）
import org.apache.calcite.avatica.util.TimeUnitRange;
// 导入DateRangeRules类，包含日期范围规则的算法
import org.apache.calcite.rel.rules.DateRangeRules;
// 导入RexNode类，表示关系表达式节点
import org.apache.calcite.rex.RexNode;
// 导入SqlStdOperatorTable类，包含标准SQL操作符表
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
// 导入Fixture类，作为测试辅助工具类
import org.apache.calcite.test.RexImplicationCheckerFixtures.Fixture;
// 导入TimestampString类，用于表示时间戳字符串
import org.apache.calcite.util.TimestampString;
// 导入Util工具类，提供通用工具方法
import org.apache.calcite.util.Util;

// 导入ImmutableList类，用于创建不可变列表
import com.google.common.collect.ImmutableList;

// 导入Matcher类，用于Hamcrest断言匹配
import org.hamcrest.Matcher;
// 导入Interval类，表示时间间隔
import org.joda.time.Interval;
// 导入Test注解，表示这是一个测试方法
import org.junit.jupiter.api.Test;

// 导入Calendar类，用于日历操作
import java.util.Calendar;
// 导入List接口，用于列表集合
import java.util.List;

// 导入Hamcrest断言工具的is匹配器
import static org.hamcrest.CoreMatchers.is;
// 导入Hamcrest断言工具的notNullValue匹配器
import static org.hamcrest.CoreMatchers.notNullValue;
// 导入Hamcrest断言工具的assertThat断言方法
import static org.hamcrest.MatcherAssert.assertThat;
// 导入Hamcrest断言工具的hasToString匹配器
import static org.hamcrest.Matchers.hasToString;

/**
 * DruidDateRangeRulesTest类 - DateRangeRules算法的单元测试类
 * 
 * 类作用说明：
 * 这个测试类专门用于测试DateRangeRules类中的日期范围转换算法。
 * DateRangeRules是Calcite框架中的一个重要规则，用于将SQL中的日期条件（如EXTRACT函数）
 * 转换为Druid可以理解的时间间隔（Intervals）。
 * 
 * 在Druid数据库中，时间范围查询通常需要使用Interval对象来表示。
 * 当用户在SQL中使用EXTRACT(YEAR FROM date_column) = 2014这样的条件时，
 * DateRangeRules会将其转换为[2014-01-01T00:00:00.000Z/2015-01-01T00:00:00.000Z]这样的时间间隔，
 * 这样Druid就可以高效地过滤数据。
 * 
 * 这个测试类的主要功能包括：
 * 1. 测试从日期列中提取年份和月份的转换
 * 2. 测试日期范围计算
 * 3. 测试从日期列中提取年份和日期的转换（会生成多个不连续的时间间隔）
 * 4. 测试从日期列中提取年月日的转换（处理闰年等复杂情况）
 * 5. 测试从时间戳列中提取年月日的转换
 * 6. 测试带CAST操作的日期过滤
 * 
 * 核心概念：
 * - EXTRACT函数：SQL标准函数，用于从日期时间值中提取特定部分（年、月、日等）
 * - Interval：Joda-Time库中的时间间隔类，表示一个时间范围
 * - RexNode：Calcite中的关系表达式节点，表示SQL表达式
 * - Fixture：测试辅助类，提供常用的测试表达式和工具方法
 */
/** Unit tests for {@link DateRangeRules} algorithms. */
// DruidDateRangeRulesTest类 - DateRangeRules算法的单元测试类
class DruidDateRangeRulesTest {

  /**
   * testExtractYearAndMonthFromDateColumn方法
   * 测试从日期列中提取年份和月份的转换
   * 
   * 方法作用：
   * 测试当SQL条件为EXTRACT(YEAR FROM date_column) = 2014 AND EXTRACT(MONTH FROM date_column) = 6时，
   * DateRangeRules能否正确将其转换为时间间隔[2014-06-01T00:00:00.000Z/2014-07-01T00:00:00.000Z]
   * 
   * 测试逻辑：
   * 1. 创建Fixture2测试辅助对象
   * 2. 构建表达式：EXTRACT(YEAR) = 2014 AND EXTRACT(MONTH) = 6
   * 3. 调用checkDateRange方法验证转换结果
   * 4. 期望结果为2014年6月的时间间隔
   */
  @Test void testExtractYearAndMonthFromDateColumn() {
    // 创建Fixture2测试辅助对象，用于构建测试表达式
    final Fixture2 f = new Fixture2();
    // AND(>=($8, 2014-01-01), <($8, 2015-01-01), >=($8, 2014-06-01), <($8, 2014-07-01))
    // 调用checkDateRange方法检查日期范围转换，期望结果为2014年6月的时间间隔
    checkDateRange(f,
        // 构建AND表达式：EXTRACT(YEAR) = 2014 AND EXTRACT(MONTH) = 6
        f.and(f.eq(f.exYear, f.literal(2014)), f.eq(f.exMonth, f.literal(6))),
        // 期望的时间间隔字符串：从2014年6月1日0点到2014年7月1日0点
        is("[2014-06-01T00:00:00.000Z/2014-07-01T00:00:00.000Z]"));
  }

  /**
   * testRangeCalc方法
   * 测试日期范围计算
   * 
   * 方法作用：
   * 测试当SQL条件为timestamp_column >= 2011-01-01 AND timestamp_column <= 2012-02-02时，
   * DateRangeRules能否正确将其转换为时间间隔
   * 
   * 测试逻辑：
   * 1. 创建Fixture2测试辅助对象
   * 2. 构建表达式：timestamp_column >= 2011-01-01 AND timestamp_column <= 2012-02-02
   * 3. 调用checkDateRange方法验证转换结果
   * 4. 期望结果为从2011-01-01到2012-02-02的时间间隔（注意结束时间加了1毫秒）
   */
  @Test void testRangeCalc() {
    // 创建Fixture2测试辅助对象
    final Fixture2 f = new Fixture2();
    // 调用checkDateRange方法检查日期范围转换
    checkDateRange(f,
        // 构建AND表达式：2011-01-01 <= timestamp_column AND timestamp_column <= 2012-02-02
        f.and(
            f.le(f.timestampLiteral(2011, Calendar.JANUARY, 1), f.ts),
            f.le(f.ts, f.timestampLiteral(2012, Calendar.FEBRUARY, 2))),
        // 期望的时间间隔字符串：从2011年1月1日0点到2012年2月2日0点（结束时间加1毫秒以包含边界）
        is("[2011-01-01T00:00:00.000Z/2012-02-02T00:00:00.001Z]"));
  }

  /**
   * testExtractYearAndDayFromDateColumn方法
   * 测试从日期列中提取年份和日期的转换
   * 
   * 方法作用：
   * 测试当SQL条件为EXTRACT(YEAR FROM date_column) = 2010 AND EXTRACT(DAY FROM date_column) = 31时，
   * DateRangeRules能否正确将其转换为多个不连续的时间间隔
   * 
   * 测试逻辑：
   * 1. 创建Fixture2测试辅助对象
   * 2. 构建表达式：EXTRACT(YEAR) = 2010 AND EXTRACT(DAY) = 31
   * 3. 调用checkDateRange方法验证转换结果
   * 4. 期望结果为2010年所有31号日期的时间间隔（1月、3月、5月、7月、8月、10月、12月）
   * 
   * 注意事项：
   * 这个测试展示了DateRangeRules处理复杂条件的能力。当条件是"年份=2010且日期=31"时，
   * 系统会生成多个不连续的时间间隔，因为不是每个月都有31号。
   */
  @Test void testExtractYearAndDayFromDateColumn() {
    // 创建Fixture2测试辅助对象
    final Fixture2 f = new Fixture2();
    // AND(AND(>=($8, 2010-01-01), <($8, 2011-01-01)),
    //     OR(AND(>=($8, 2010-01-31), <($8, 2010-02-01)),
    //        AND(>=($8, 2010-03-31), <($8, 2010-04-01)),
    //        AND(>=($8, 2010-05-31), <($8, 2010-06-01)),
    //        AND(>=($8, 2010-07-31), <($8, 2010-08-01)),
    //        AND(>=($8, 2010-08-31), <($8, 2010-09-01)),
    //        AND(>=($8, 2010-10-31), <($8, 2010-11-01)),
    //        AND(>=($8, 2010-12-31), <($8, 2011-01-01))))
    // 调用checkDateRange方法检查日期范围转换
    checkDateRange(f,
        // 构建AND表达式：EXTRACT(YEAR) = 2010 AND EXTRACT(DAY) = 31
        f.and(f.eq(f.exYear, f.literal(2010)), f.eq(f.exDay, f.literal(31))),
        // 期望的时间间隔字符串：2010年所有31号日期（1月31日、3月31日、5月31日、7月31日、8月31日、10月31日、12月31日）
        is("[2010-01-31T00:00:00.000Z/2010-02-01T00:00:00.000Z, "
            + "2010-03-31T00:00:00.000Z/2010-04-01T00:00:00.000Z, "
            + "2010-05-31T00:00:00.000Z/2010-06-01T00:00:00.000Z, "
            + "2010-07-31T00:00:00.000Z/2010-08-01T00:00:00.000Z, "
            + "2010-08-31T00:00:00.000Z/2010-09-01T00:00:00.000Z, "
            + "2010-10-31T00:00:00.000Z/2010-11-01T00:00:00.000Z, "
            + "2010-12-31T00:00:00.000Z/2011-01-01T00:00:00.000Z]"));
  }

  /**
   * testExtractYearMonthDayFromDateColumn方法
   * 测试从日期列中提取年、月、日的转换（处理闰年）
   * 
   * 方法作用：
   * 测试当SQL条件为EXTRACT(YEAR FROM date_column) > 2010 AND EXTRACT(YEAR FROM date_column) < 2020
   * AND EXTRACT(MONTH FROM date_column) = 2 AND EXTRACT(DAY FROM date_column) = 29时，
   * DateRangeRules能否正确识别闰年并转换为正确的时间间隔
   * 
   * 测试逻辑：
   * 1. 创建Fixture2测试辅助对象
   * 2. 构建表达式：年份在2010-2020之间且月份=2且日期=29
   * 3. 调用checkDateRange方法验证转换结果
   * 4. 期望结果为2012年和2016年的2月29日（这两个年份是闰年）
   * 
   * 注意事项：
   * 这个测试展示了DateRangeRules处理闰年的能力。2月29日只在闰年存在，
   * 在2010-2020年间，只有2012和2016是闰年，所以只生成这两个时间间隔。
   */
  @Test void testExtractYearMonthDayFromDateColumn() {
    // 创建Fixture2测试辅助对象
    final Fixture2 f = new Fixture2();
    // AND(>=($8, 2011-01-01),"
    //     AND(>=($8, 2011-01-01), <($8, 2020-01-01)),
    //     OR(AND(>=($8, 2011-02-01), <($8, 2011-03-01)),
    //        AND(>=($8, 2012-02-01), <($8, 2012-03-01)),
    //        AND(>=($8, 2013-02-01), <($8, 2013-03-01)),
    //        AND(>=($8, 2014-02-01), <($8, 2014-03-01)),
    //        AND(>=($8, 2015-02-01), <($8, 2015-03-01)),
    //        AND(>=($8, 2016-02-01), <($8, 2016-03-01)),
    //        AND(>=($8, 2017-02-01), <($8, 2017-03-01)),
    //        AND(>=($8, 2018-02-01), <($8, 2018-03-01)),
    //        AND(>=($8, 2019-02-01), <($8, 2019-03-01))),
    //     OR(AND(>=($8, 2012-02-29), <($8, 2012-03-01)),
    //        AND(>=($8, 2016-02-29), <($8, 2016-03-01))))
    // 调用checkDateRange方法检查日期范围转换
    checkDateRange(f,
        // 构建AND表达式：年份>2010 AND 年份<2020 AND 月份=2 AND 日期=29
        f.and(f.gt(f.exYear, f.literal(2010)), f.lt(f.exYear, f.literal(2020)),
            f.eq(f.exMonth, f.literal(2)), f.eq(f.exDay, f.literal(29))),
        // 期望的时间间隔字符串：只有2012年和2016年的2月29日（这两个年份是闰年）
        is("[2012-02-29T00:00:00.000Z/2012-03-01T00:00:00.000Z, "
            + "2016-02-29T00:00:00.000Z/2016-03-01T00:00:00.000Z]"));
  }

  /**
   * testExtractYearMonthDayFromTimestampColumn方法
   * 测试从时间戳列中提取年、月、日的转换
   * 
   * 方法作用：
   * 测试当SQL条件应用于时间戳列（而非日期列）时，DateRangeRules能否正确处理
   * 时间戳列可以包含时间部分（时、分、秒），但这个测试只关注日期部分
   * 
   * 测试逻辑：
   * 1. 创建Fixture2测试辅助对象
   * 2. 构建表达式：年份>2010 AND 年份<2020 AND 月份=2 AND 日期=29
   * 3. 调用checkDateRange方法验证转换结果
   * 4. 期望结果与testExtractYearMonthDayFromDateColumn相同
   * 
   * 注意事项：
   * 这个测试与testExtractYearMonthDayFromDateColumn的逻辑相同，但针对的是时间戳列。
   * 时间戳列和日期列在Calcite中可能使用不同的数据类型，但DateRangeRules应该能够统一处理。
   */
  @Test void testExtractYearMonthDayFromTimestampColumn() {
    // 创建Fixture2测试辅助对象
    final Fixture2 f = new Fixture2();
    // AND(>=($9, 2011-01-01),
    //     AND(>=($9, 2011-01-01), <($9, 2020-01-01)),
    //     OR(AND(>=($9, 2011-02-01), <($9, 2011-03-01)),
    //        AND(>=($9, 2012-02-01), <($9, 2012-03-01)),
    //        AND(>=($9, 2013-02-01), <($9, 2013-03-01)),
    //        AND(>=($9, 2014-02-01), <($9, 2014-03-01)),
    //        AND(>=($9, 2015-02-01), <($9, 2015-03-01)),
    //        AND(>=($9, 2016-02-01), <($9, 2016-03-01)),
    //        AND(>=($9, 2017-02-01), <($9, 2017-03-01)),
    //        AND(>=($9, 2018-02-01), <($9, 2018-03-01)),
    //        AND(>=($9, 2019-02-01), <($9, 2019-03-01))),
    //     OR(AND(>=($9, 2012-02-29), <($9, 2012-03-01)),"
    //        AND(>=($9, 2016-02-29), <($9, 2016-03-01))))
    // 调用checkDateRange方法检查日期范围转换
    checkDateRange(f,
        // 构建AND表达式：年份>2010 AND 年份<2020 AND 月份=2 AND 日期=29
        f.and(f.gt(f.exYear, f.literal(2010)),
            f.lt(f.exYear, f.literal(2020)),
            f.eq(f.exMonth, f.literal(2)), f.eq(f.exDay, f.literal(29))),
        // 期望的时间间隔字符串：只有2012年和2016年的2月29日
        is("[2012-02-29T00:00:00.000Z/2012-03-01T00:00:00.000Z, "
            + "2016-02-29T00:00:00.000Z/2016-03-01T00:00:00.000Z]"));
  }

  /**
   * testFilterWithCast方法
   * 测试带CAST操作的日期过滤
   * 
   * 方法作用：
   * 测试当SQL条件包含CAST操作时，DateRangeRules能否正确转换。
   * 这个测试对应JIRA issue CALCITE-1738，用于验证将字面量的CAST推送到Druid的功能。
   * 
   * 测试逻辑：
   * 1. 创建Fixture2测试辅助对象
   * 2. 创建起始时间2010-01-01和结束时间2011-01-01
   * 3. 构建表达式：d >= CAST(2010-01-01 AS TIMESTAMP) AND d < CAST(2011-01-01 AS TIMESTAMP)
   * 4. 调用checkDateRangeNoSimplify方法验证转换结果（不简化表达式以保留CAST）
   * 5. 期望结果为[2010-01-01T00:00:00.000Z/2011-01-01T00:00:00.000Z]
   * 
   * 注意事项：
   * 这个测试使用checkDateRangeNoSimplify而不是checkDateRange，因为简化过程会移除CAST操作。
   * 在Hive中，当使用HiveRexExecutorImpl时，表达式会保留CAST操作，所以需要测试这种情况。
   */
  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1738">[CALCITE-1738]
   * Push CAST of literals to Druid</a>. */
  @Test void testFilterWithCast() {
    // 创建Fixture2测试辅助对象
    final Fixture2 f = new Fixture2();
    // 创建Calendar对象用于构建时间戳
    final Calendar c = Util.calendar();
    // 清除Calendar的所有字段
    c.clear();
    // 设置日期为2010年1月1日
    c.set(2010, Calendar.JANUARY, 1);
    // 从Calendar字段创建TimestampString对象（起始时间）
    final TimestampString from = TimestampString.fromCalendarFields(c);
    // 清除Calendar的所有字段
    c.clear();
    // 设置日期为2011年1月1日
    c.set(2011, Calendar.JANUARY, 1);
    // 从Calendar字段创建TimestampString对象（结束时间）
    final TimestampString to = TimestampString.fromCalendarFields(c);

    // d >= 2010-01-01 AND d < 2011-01-01
    // 调用checkDateRangeNoSimplify方法检查日期范围转换（不简化表达式以保留CAST）
    checkDateRangeNoSimplify(f,
        // 构建AND表达式：d >= CAST(2010-01-01 AS TIMESTAMP) AND d < CAST(2011-01-01 AS TIMESTAMP)
        f.and(
            f.ge(f.d, f.cast(f.timestampDataType, f.timestampLiteral(from))),
            f.lt(f.d, f.cast(f.timestampDataType, f.timestampLiteral(to)))),
        // 期望的时间间隔字符串：从2010年1月1日0点到2011年1月1日0点
        is("[2010-01-01T00:00:00.000Z/2011-01-01T00:00:00.000Z]"));
  }

  /**
   * checkDateRangeNoSimplify方法
   * 检查日期范围转换（不简化表达式）
   * 
   * 方法作用：
   * 这是一个辅助方法，用于测试DateRangeRules的日期范围转换功能，但不简化表达式。
   * 不简化表达式可以保留CAST操作，以便测试Hive等系统中表达式保留CAST的情况。
   * 
   * 参数说明：
   * @param f Fixture测试辅助对象，包含rexBuilder等工具
   * @param e 需要转换的RexNode表达式
   * @param intervalMatcher 期望的时间间隔匹配器
   * 
   * 处理流程：
   * 1. 调用DateRangeRules.replaceTimeUnits方法，将时间单位替换为常量
   * 2. 调用DruidDateTimeUtils.createInterval方法，将表达式转换为时间间隔列表
   * 3. 验证时间间隔不为null
   * 4. 验证时间间隔的字符串表示与期望值匹配
   * 
   * 注意事项：
   * 这个方法与checkDateRange的区别在于不调用simplify.simplify方法，
   * 因此表达式中的CAST操作会被保留。
   */
  // For testFilterWithCast we need to no simplify the expression, which would
  // remove the CAST, in order to match the way expressions are presented when
  // HiveRexExecutorImpl is used in Hive
  private void checkDateRangeNoSimplify(Fixture f, RexNode e,
      Matcher<String> intervalMatcher) {
    // 调用DateRangeRules.replaceTimeUnits方法，将时间单位替换为常量（使用UTC时区）
    e = DateRangeRules.replaceTimeUnits(f.rexBuilder, e, "UTC");
    // 调用DruidDateTimeUtils.createInterval方法，将表达式转换为时间间隔列表
    final List<Interval> intervals =
        DruidDateTimeUtils.createInterval(e);
    // 验证时间间隔不为null
    assertThat(intervals, notNullValue());
    // 验证时间间隔的字符串表示与期望值匹配
    assertThat(intervals, hasToString(intervalMatcher));
  }

  /**
   * checkDateRange方法
   * 检查日期范围转换（简化表达式）
   * 
   * 方法作用：
   * 这是一个辅助方法，用于测试DateRangeRules的日期范围转换功能，并简化表达式。
   * 简化表达式可以优化表达式结构，移除冗余操作，使转换更高效。
   * 
   * 参数说明：
   * @param f Fixture测试辅助对象，包含rexBuilder和simplify等工具
   * @param e 需要转换的RexNode表达式
   * @param intervalMatcher 期望的时间间隔匹配器
   * 
   * 处理流程：
   * 1. 调用DateRangeRules.replaceTimeUnits方法，将时间单位替换为常量
   * 2. 调用simplify.simplify方法，简化表达式结构
   * 3. 调用DruidDateTimeUtils.createInterval方法，将表达式转换为时间间隔列表
   * 4. 验证时间间隔不为null，如果为null则抛出AssertionError
   * 5. 验证时间间隔的字符串表示与期望值匹配
   * 
   * 注意事项：
   * 这个方法与checkDateRangeNoSimplify的区别在于调用了simplify.simplify方法，
   * 这会移除表达式中的冗余操作，如CAST操作。
   */
  private void checkDateRange(Fixture f, RexNode e, Matcher<String> intervalMatcher) {
    // 调用DateRangeRules.replaceTimeUnits方法，将时间单位替换为常量（使用UTC时区）
    e = DateRangeRules.replaceTimeUnits(f.rexBuilder, e, "UTC");
    // 调用simplify.simplify方法，简化表达式结构（移除冗余操作）
    final RexNode e2 = f.simplify.simplify(e);
    // 调用DruidDateTimeUtils.createInterval方法，将简化后的表达式转换为时间间隔列表
    List<Interval> intervals =
        DruidDateTimeUtils.createInterval(e2);
    // 如果时间间隔为null，抛出断言错误
    if (intervals == null) {
      throw new AssertionError("null interval");
    }
    // 验证时间间隔的字符串表示与期望值匹配
    assertThat(intervals, hasToString(intervalMatcher));
  }

  /**
   * Fixture2内部类
   * 测试辅助类，提供跨测试的公共表达式
   * 
   * 类作用说明：
   * Fixture2继承自Fixture类，专门为DruidDateRangeRulesTest提供测试所需的公共表达式。
   * 它封装了常用的EXTRACT表达式（年、月、日），避免在每个测试方法中重复创建这些表达式。
   * 
   * 成员变量说明：
   * - exYear: 表示EXTRACT(YEAR FROM ts)的表达式，用于提取年份
   * - exMonth: 表示EXTRACT(MONTH FROM ts)的表达式，用于提取月份
   * - exDay: 表示EXTRACT(DAY FROM ts)的表达式，用于提取日期
   * 
   * 继承关系：
   * Fixture2 extends Fixture，继承了Fixture中的rexBuilder、ts、d、simplify等成员变量和方法
   * 
   * 使用场景：
   * 在各个测试方法中，通过创建Fixture2对象，可以方便地使用exYear、exMonth、exDay等表达式，
   * 构建复杂的日期条件表达式。
   */
  /** Common expressions across tests. */
  private static class Fixture2 extends Fixture {
    /**
     * exYear成员变量
     * 表示EXTRACT(YEAR FROM ts)的表达式
     * 
     * 变量作用：
     * 这个RexNode表达式用于从时间戳列ts中提取年份部分。
     * 例如：EXTRACT(YEAR FROM '__time')，其中__time是Druid默认的时间戳列名。
     * 
     * 创建方式：
     * 使用rexBuilder.makeCall方法创建EXTRACT函数调用，
     * 参数包括YEAR标志和时间戳列引用ts。
     */
    private final RexNode exYear;
    /**
     * exMonth成员变量
     * 表示EXTRACT(MONTH FROM ts)的表达式
     * 
     * 变量作用：
     * 这个RexNode表达式用于从时间戳列ts中提取月份部分。
     * 例如：EXTRACT(MONTH FROM '__time')。
     * 
     * 创建方式：
     * 使用rexBuilder.makeCall方法创建EXTRACT函数调用，
     * 参数包括MONTH标志、整数返回类型和时间戳列引用ts。
     */
    private final RexNode exMonth;
    /**
     * exDay成员变量
     * 表示EXTRACT(DAY FROM ts)的表达式
     * 
     * 变量作用：
     * 这个RexNode表达式用于从时间戳列ts中提取日期部分。
     * 例如：EXTRACT(DAY FROM '__time')。
     * 
     * 创建方式：
     * 使用rexBuilder.makeCall方法创建EXTRACT函数调用，
     * 参数包括DAY标志、整数返回类型和时间戳列引用ts。
     */
    private final RexNode exDay;

    /**
     * Fixture2构造方法
     * 
     * 方法作用：
     * 初始化Fixture2对象，创建EXTRACT(YEAR)、EXTRACT(MONTH)、EXTRACT(DAY)表达式。
     * 这些表达式将在测试方法中被重复使用，用于构建日期条件。
     * 
     * 处理流程：
     * 1. 创建EXTRACT(YEAR FROM ts)表达式，赋值给exYear
     * 2. 创建EXTRACT(MONTH FROM ts)表达式，赋值给exMonth
     * 3. 创建EXTRACT(DAY FROM ts)表达式，赋值给exDay
     */
    Fixture2() {
      // 创建EXTRACT(YEAR FROM ts)表达式：从时间戳列提取年份
      exYear =
          rexBuilder.makeCall(SqlStdOperatorTable.EXTRACT,
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.YEAR), ts));
      // 创建EXTRACT(MONTH FROM ts)表达式：从时间戳列提取月份，返回整数类型
      exMonth =
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.EXTRACT,
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.MONTH), ts));
      // 创建EXTRACT(DAY FROM ts)表达式：从时间戳列提取日期，返回整数类型
      exDay =
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.EXTRACT,
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.DAY), ts));
    }

    /**
     * timestampLiteral方法
     * 创建时间戳字面量
     * 
     * 方法作用：
     * 根据年、月、日参数创建一个时间戳字面量表达式。
     * 这个方法简化了创建时间戳字面量的过程，避免在每个测试中重复编写Calendar操作代码。
     * 
     * 参数说明：
     * @param year 年份，如2014
     * @param month 月份，使用Calendar常量如Calendar.JANUARY
     * @param day 日期，如1
     * 
     * 返回值：
     * @return RexNode 表示时间戳字面量的表达式节点
     * 
     * 处理流程：
     * 1. 创建Calendar对象
     * 2. 清除Calendar的所有字段
     * 3. 设置年、月、日
     * 4. 从Calendar字段创建TimestampString对象
     * 5. 调用父类Fixture的timestampLiteral方法创建RexNode表达式
     * 
     * 使用示例：
     * timestampLiteral(2014, Calendar.JANUARY, 1) 创建表示2014年1月1日的时间戳字面量
     */
    public RexNode timestampLiteral(int year, int month, int day) {
      // 创建Calendar对象
      final Calendar c = Util.calendar();
      // 清除Calendar的所有字段
      c.clear();
      // 设置年、月、日
      c.set(year, month, day);
      // 从Calendar字段创建TimestampString对象
      final TimestampString ts = TimestampString.fromCalendarFields(c);
      // 调用父类Fixture的timestampLiteral方法创建RexNode表达式
      return timestampLiteral(ts);
    }
  }
}
