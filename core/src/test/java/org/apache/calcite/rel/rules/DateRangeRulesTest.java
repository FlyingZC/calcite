/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，用于指定代码的许可证类型
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，参见NOTICE文件了解版权所有信息
 * this work for additional information regarding copyright ownership. // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权给您使用此文件
 * (the "License"); you may not use this file except in compliance with // "许可证"），除非遵守许可证，否则不得使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0的网址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 按原样分发，不提供任何明示或暗示的保证
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不含任何类型的保证或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 请参阅许可证以了解特定语言的权限和
 * limitations under the License. // 许可证下的限制
 */
package org.apache.calcite.rel.rules; // 声明包名，该类位于org.apache.calcite.rel.rules包下，属于规则层

import org.apache.calcite.avatica.util.TimeUnitRange; // 导入TimeUnitRange类，用于表示时间单位范围（如年、月、日等）
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式节点，是Calcite中表达式的抽象表示
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，提供标准SQL操作符（如EXTRACT、FLOOR、CEIL等）
import org.apache.calcite.test.RexImplicationCheckerFixtures.Fixture; // 导入Fixture类，提供测试用的辅助工具和表达式构建方法
import org.apache.calcite.util.DateString; // 导入DateString类，用于表示日期字符串
import org.apache.calcite.util.TimestampString; // 导入TimestampString类，用于表示时间戳字符串
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种辅助方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList，用于创建不可变列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava的ImmutableSet，用于创建不可变集合

import org.hamcrest.Matcher; // 导入Hamcrest的Matcher接口，用于断言匹配
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法

import java.util.Calendar; // 导入Java的Calendar类，用于处理日期和时间
import java.util.Set; // 导入Java的Set接口，表示集合

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于值相等断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的assertThat方法，用于执行断言
import static org.hamcrest.Matchers.hasToString; // 导入hasToString匹配器，用于检查对象的字符串表示
import static org.hamcrest.core.IsInstanceOf.any; // 导入any匹配器，用于匹配任意类型

/** Unit tests for {@link DateRangeRules} algorithms. */ // DateRangeRules算法的单元测试类
class DateRangeRulesTest { // 定义测试类DateRangeRulesTest，用于测试DateRangeRules规则的各种场景

  @Test void testExtractYearFromDateColumn() { // 测试方法：测试从DATE列提取年份的EXTRACT表达式转换
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具

    final RexNode e = f.eq(f.literal(2014), f.exYearD); // 创建表达式：2014 = EXTRACT(YEAR FROM d)，即提取年份等于2014
    assertThat(DateRangeRules.extractTimeUnits(e), // 调用DateRangeRules.extractTimeUnits方法提取时间单位
        is(set(TimeUnitRange.YEAR))); // 断言提取到的时间单位是YEAR（年）
    assertThat(DateRangeRules.extractTimeUnits(f.dec), is(set())); // 断言十进制数表达式不包含时间单位
    assertThat(DateRangeRules.extractTimeUnits(f.literal(1)), is(set())); // 断言字面量1不包含时间单位

    // extract YEAR from a DATE column // 从DATE列提取年份
    checkDateRange(f, e, is("AND(>=($8, 2014-01-01), <($8, 2015-01-01))")); // 检查日期范围转换：2014 = EXTRACT(YEAR FROM d) 应转换为 d >= 2014-01-01 AND d < 2015-01-01
    checkDateRange(f, f.eq(f.exYearD, f.literal(2014)), // 检查：EXTRACT(YEAR FROM d) = 2014
        is("AND(>=($8, 2014-01-01), <($8, 2015-01-01))")); // 应转换为：d >= 2014-01-01 AND d < 2015-01-01
    checkDateRange(f, f.ge(f.exYearD, f.literal(2014)), // 检查：EXTRACT(YEAR FROM d) >= 2014
        is(">=($8, 2014-01-01)")); // 应转换为：d >= 2014-01-01
    checkDateRange(f, f.gt(f.exYearD, f.literal(2014)), // 检查：EXTRACT(YEAR FROM d) > 2014
        is(">=($8, 2015-01-01)")); // 应转换为：d >= 2015-01-01（大于2014年等于大于等于2015年）
    checkDateRange(f, f.lt(f.exYearD, f.literal(2014)), // 检查：EXTRACT(YEAR FROM d) < 2014
        is("<($8, 2014-01-01)")); // 应转换为：d < 2014-01-01
    checkDateRange(f, f.le(f.exYearD, f.literal(2014)), // 检查：EXTRACT(YEAR FROM d) <= 2014
        is("<($8, 2015-01-01)")); // 应转换为：d < 2015-01-01（小于等于2014年等于小于2015年）
    checkDateRange(f, f.ne(f.exYearD, f.literal(2014)), // 检查：EXTRACT(YEAR FROM d) <> 2014
        is("<>(EXTRACT(FLAG(YEAR), $8), 2014)")); // 不等号无法转换为日期范围，保持原样
  } // 结束代码块

  @Test void testExtractYearFromTimestampColumn() { // 测试方法：测试从TIMESTAMP列提取年份的EXTRACT表达式转换
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例
    checkDateRange(f, f.eq(f.exYearTs, f.literal(2014)), // 检查：EXTRACT(YEAR FROM ts) = 2014
        is("AND(>=($9, 2014-01-01 00:00:00), <($9, 2015-01-01 00:00:00))")); // 应转换为：ts >= 2014-01-01 00:00:00 AND ts < 2015-01-01 00:00:00
    checkDateRange(f, f.ge(f.exYearTs, f.literal(2014)), // 检查：EXTRACT(YEAR FROM ts) >= 2014
        is(">=($9, 2014-01-01 00:00:00)")); // 应转换为：ts >= 2014-01-01 00:00:00
    checkDateRange(f, f.gt(f.exYearTs, f.literal(2014)), // 检查：EXTRACT(YEAR FROM ts) > 2014
        is(">=($9, 2015-01-01 00:00:00)")); // 应转换为：ts >= 2015-01-01 00:00:00
    checkDateRange(f, f.lt(f.exYearTs, f.literal(2014)), // 检查：EXTRACT(YEAR FROM ts) < 2014
        is("<($9, 2014-01-01 00:00:00)")); // 应转换为：ts < 2014-01-01 00:00:00
    checkDateRange(f, f.le(f.exYearTs, f.literal(2014)), // 检查：EXTRACT(YEAR FROM ts) <= 2014
        is("<($9, 2015-01-01 00:00:00)")); // 应转换为：ts < 2015-01-01 00:00:00
    checkDateRange(f, f.ne(f.exYearTs, f.literal(2014)), // 检查：EXTRACT(YEAR FROM ts) <> 2014
        is("<>(EXTRACT(FLAG(YEAR), $9), 2014)")); // 不等号无法转换，保持原样
  } // 结束代码块

  @Test void testExtractYearAndMonthFromDateColumn() { // 测试方法：测试从DATE列同时提取年份和月份的EXTRACT表达式转换
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearD, f.literal(2014)), f.eq(f.exMonthD, f.literal(6))), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
        "UTC",
        is("AND(AND(>=($8, 2014-01-01), <($8, 2015-01-01))," // 创建匹配器，用于断言
            + " AND(>=($8, 2014-06-01), <($8, 2014-07-01)))"),
        is("SEARCH($8, Sarg[[2014-06-01..2014-07-01)])")); // 创建匹配器，用于断言
  } // 结束代码块

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1601">[CALCITE-1601]
   * DateRangeRules loses OR filters</a>. */
  @Test void testExtractYearAndMonthFromDateColumn2() { // 测试方法：测试CALCITE-1601问题，DateRangeRules丢失OR过滤器
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    final String s1 = "AND(" // 定义第一个期望的字符串表达式（转换后的结果）
        + "AND(>=($8, 2000-01-01), <($8, 2001-01-01)),"
        + " OR("
        + "AND(>=($8, 2000-02-01), <($8, 2000-03-01)), "
        + "AND(>=($8, 2000-03-01), <($8, 2000-04-01)), "
        + "AND(>=($8, 2000-05-01), <($8, 2000-06-01))))";
    final String s2 = "SEARCH($8, Sarg[[2000-02-01..2000-04-01)," // 定义第二个期望的字符串表达式（简化后的结果）
        + " [2000-05-01..2000-06-01)])";
    final RexNode e = // 创建表达式节点
        f.and(f.eq(f.exYearD, f.literal(2000)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
            f.or(f.eq(f.exMonthD, f.literal(2)), // 创建OR条件：EXTRACT(MONTH FROM d) IN (多个值)
                f.eq(f.exMonthD, f.literal(3)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                f.eq(f.exMonthD, f.literal(5)))); // 创建条件：EXTRACT(MONTH FROM d) = 常数
    checkDateRange(f, e, "UTC", is(s1), is(s2)); // 检查日期范围转换
  } // 结束代码块

  @Test void testExtractYearAndDayFromDateColumn() { // 测试方法：测试从DATE列同时提取年份和日期的EXTRACT表达式转换
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearD, f.literal(2010)), f.eq(f.exDayD, f.literal(31))), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
        is("AND(AND(>=($8, 2010-01-01), <($8, 2011-01-01))," // 创建匹配器，用于断言
            + " OR(AND(>=($8, 2010-01-31), <($8, 2010-02-01)),"
            + " AND(>=($8, 2010-03-31), <($8, 2010-04-01)),"
            + " AND(>=($8, 2010-05-31), <($8, 2010-06-01)),"
            + " AND(>=($8, 2010-07-31), <($8, 2010-08-01)),"
            + " AND(>=($8, 2010-08-31), <($8, 2010-09-01)),"
            + " AND(>=($8, 2010-10-31), <($8, 2010-11-01)),"
            + " AND(>=($8, 2010-12-31), <($8, 2011-01-01))))"));

  } // 结束代码块

  @Test void testExtractYearMonthDayFromDateColumn() { // 测试方法：测试从DATE列同时提取年月日的EXTRACT表达式转换，查找2010-2020年间的闰日（2月29日）
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    // The following condition finds the 2 leap days between 2010 and 2020,
    // namely 29th February 2012 and 2016.
    //
    // Currently there are redundant conditions, e.g.
    // "AND(>=($8, 2011-01-01), <($8, 2020-01-01))". We should remove them by
    // folding intervals.
    checkDateRange(f, // 检查日期范围转换
        f.and(f.gt(f.exYearD, f.literal(2010)),
            f.lt(f.exYearD, f.literal(2020)), // 创建条件：EXTRACT(YEAR FROM d) < 常数
            f.eq(f.exMonthD, f.literal(2)), f.eq(f.exDayD, f.literal(29))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("AND(>=($8, 2011-01-01)," // 创建匹配器，用于断言
            + " AND(>=($8, 2011-01-01), <($8, 2020-01-01)),"
            + " OR(AND(>=($8, 2011-02-01), <($8, 2011-03-01)),"
            + " AND(>=($8, 2012-02-01), <($8, 2012-03-01)),"
            + " AND(>=($8, 2013-02-01), <($8, 2013-03-01)),"
            + " AND(>=($8, 2014-02-01), <($8, 2014-03-01)),"
            + " AND(>=($8, 2015-02-01), <($8, 2015-03-01)),"
            + " AND(>=($8, 2016-02-01), <($8, 2016-03-01)),"
            + " AND(>=($8, 2017-02-01), <($8, 2017-03-01)),"
            + " AND(>=($8, 2018-02-01), <($8, 2018-03-01)),"
            + " AND(>=($8, 2019-02-01), <($8, 2019-03-01))),"
            + " OR(AND(>=($8, 2012-02-29), <($8, 2012-03-01)),"
            + " AND(>=($8, 2016-02-29), <($8, 2016-03-01))))"));
  } // 结束代码块

  @Test void testExtractYearMonthDayFromTimestampColumn() { // 测试方法：测试从TIMESTAMP列同时提取年月日的EXTRACT表达式转换
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(f.gt(f.exYearD, f.literal(2010)),
            f.lt(f.exYearD, f.literal(2020)), // 创建条件：EXTRACT(YEAR FROM d) < 常数
            f.eq(f.exMonthD, f.literal(2)), f.eq(f.exDayD, f.literal(29))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("AND(>=($8, 2011-01-01)," // 创建匹配器，用于断言
            + " AND(>=($8, 2011-01-01), <($8, 2020-01-01)),"
            + " OR(AND(>=($8, 2011-02-01), <($8, 2011-03-01)),"
            + " AND(>=($8, 2012-02-01), <($8, 2012-03-01)),"
            + " AND(>=($8, 2013-02-01), <($8, 2013-03-01)),"
            + " AND(>=($8, 2014-02-01), <($8, 2014-03-01)),"
            + " AND(>=($8, 2015-02-01), <($8, 2015-03-01)),"
            + " AND(>=($8, 2016-02-01), <($8, 2016-03-01)),"
            + " AND(>=($8, 2017-02-01), <($8, 2017-03-01)),"
            + " AND(>=($8, 2018-02-01), <($8, 2018-03-01)),"
            + " AND(>=($8, 2019-02-01), <($8, 2019-03-01))),"
            + " OR(AND(>=($8, 2012-02-29), <($8, 2012-03-01)),"
            + " AND(>=($8, 2016-02-29), <($8, 2016-03-01))))"));
  } // 结束代码块

  /** Test case #1 for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1658">[CALCITE-1658]
   * DateRangeRules issues</a>. */
  @Test void testExtractWithOrCondition1() { // 测试方法：测试CALCITE-1658问题#1，带OR条件的EXTRACT表达式转换
    // (EXTRACT(YEAR FROM __time) = 2000
    //    AND EXTRACT(MONTH FROM __time) IN (2, 3, 5))
    // OR (EXTRACT(YEAR FROM __time) = 2001
    //    AND EXTRACT(MONTH FROM __time) = 1)
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.or(
            f.and(f.eq(f.exYearD, f.literal(2000)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                f.or(f.eq(f.exMonthD, f.literal(2)), // 创建OR条件：EXTRACT(MONTH FROM d) IN (多个值)
                    f.eq(f.exMonthD, f.literal(3)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                    f.eq(f.exMonthD, f.literal(5)))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
            f.and(f.eq(f.exYearD, f.literal(2001)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                f.eq(f.exMonthD, f.literal(1)))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("OR(AND(AND(>=($8, 2000-01-01), <($8, 2001-01-01))," // 创建匹配器，用于断言
            + " OR(AND(>=($8, 2000-02-01), <($8, 2000-03-01)),"
            + " AND(>=($8, 2000-03-01), <($8, 2000-04-01)),"
            + " AND(>=($8, 2000-05-01), <($8, 2000-06-01)))),"
            + " AND(AND(>=($8, 2001-01-01), <($8, 2002-01-01)),"
            + " AND(>=($8, 2001-01-01), <($8, 2001-02-01))))"));
  } // 结束代码块

  /** Test case #2 for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1658">[CALCITE-1658]
   * DateRangeRules issues</a>. */
  @Test void testExtractWithOrCondition2() { // 测试方法：测试CALCITE-1658问题#2，带OR条件的EXTRACT表达式转换
    // EXTRACT(YEAR FROM __time) IN (2000, 2001)
    //   AND ((EXTRACT(YEAR FROM __time) = 2000
    //         AND EXTRACT(MONTH FROM __time) IN (2, 3, 5))
    //     OR (EXTRACT(YEAR FROM __time) = 2001
    //       AND EXTRACT(MONTH FROM __time) = 1))
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(
            f.or(f.eq(f.exYearD, f.literal(2000)), // 创建OR条件：EXTRACT(YEAR FROM d) IN (2000, 2001)
                f.eq(f.exYearD, f.literal(2001))), // 创建条件：EXTRACT(YEAR FROM d) = 常数
            f.or(
                f.and(f.eq(f.exYearD, f.literal(2000)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                    f.or(f.eq(f.exMonthD, f.literal(2)), // 创建OR条件：EXTRACT(MONTH FROM d) IN (多个值)
                        f.eq(f.exMonthD, f.literal(3)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                        f.eq(f.exMonthD, f.literal(5)))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                f.and(f.eq(f.exYearD, f.literal(2001)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                    f.eq(f.exMonthD, f.literal(1))))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("AND(OR(AND(>=($8, 2000-01-01), <($8, 2001-01-01))," // 创建匹配器，用于断言
            + " AND(>=($8, 2001-01-01), <($8, 2002-01-01))),"
            + " OR(AND(AND(>=($8, 2000-01-01), <($8, 2001-01-01)),"
            + " OR(AND(>=($8, 2000-02-01), <($8, 2000-03-01)),"
            + " AND(>=($8, 2000-03-01), <($8, 2000-04-01)),"
            + " AND(>=($8, 2000-05-01), <($8, 2000-06-01)))),"
            + " AND(AND(>=($8, 2001-01-01), <($8, 2002-01-01)),"
            + " AND(>=($8, 2001-01-01), <($8, 2001-02-01)))))"));
  } // 结束代码块

  /** Test case #3 for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1658">[CALCITE-1658]
   * DateRangeRules issues</a>. */
  @Test void testExtractPartialRewriteForNotEqualsYear() { // 测试方法：测试CALCITE-1658问题#3，年份不等式的部分重写
    // EXTRACT(YEAR FROM __time) <> 2000
    // AND ((EXTRACT(YEAR FROM __time) = 2000
    //     AND EXTRACT(MONTH FROM __time) IN (2, 3, 5))
    //   OR (EXTRACT(YEAR FROM __time) = 2001
    //     AND EXTRACT(MONTH FROM __time) = 1))
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(
            f.ne(f.exYearD, f.literal(2000)), // 创建条件：EXTRACT(YEAR FROM d) <> 常数
            f.or(
                f.and(f.eq(f.exYearD, f.literal(2000)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                    f.or(f.eq(f.exMonthD, f.literal(2)), // 创建OR条件：EXTRACT(MONTH FROM d) IN (多个值)
                        f.eq(f.exMonthD, f.literal(3)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                        f.eq(f.exMonthD, f.literal(5)))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                f.and(f.eq(f.exYearD, f.literal(2001)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                    f.eq(f.exMonthD, f.literal(1))))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("AND(<>(EXTRACT(FLAG(YEAR), $8), 2000)," // 创建匹配器，用于断言
            + " OR(AND(AND(>=($8, 2000-01-01), <($8, 2001-01-01)),"
            + " OR(AND(>=($8, 2000-02-01), <($8, 2000-03-01)),"
            + " AND(>=($8, 2000-03-01), <($8, 2000-04-01)),"
            + " AND(>=($8, 2000-05-01), <($8, 2000-06-01)))),"
            + " AND(AND(>=($8, 2001-01-01), <($8, 2002-01-01)),"
            + " AND(>=($8, 2001-01-01), <($8, 2001-02-01)))))"));
  } // 结束代码块

  /** Test case #4 for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1658">[CALCITE-1658]
   * DateRangeRules issues</a>. */
  @Test void testExtractPartialRewriteForInMonth() { // 测试方法：测试CALCITE-1658问题#4，月份IN条件的部分重写
    // EXTRACT(MONTH FROM __time) in (1, 2, 3, 4, 5)
    // AND ((EXTRACT(YEAR FROM __time) = 2000
    //     AND EXTRACT(MONTH FROM __time) IN (2, 3, 5))
    //   OR (EXTRACT(YEAR FROM __time) = 2001
    //     AND EXTRACT(MONTH FROM __time) = 1))
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(
            f.or(f.eq(f.exMonthD, f.literal(1)), // 创建OR条件：EXTRACT(MONTH FROM d) IN (多个值)
                f.eq(f.exMonthD, f.literal(2)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                f.eq(f.exMonthD, f.literal(3)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                f.eq(f.exMonthD, f.literal(4)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                f.eq(f.exMonthD, f.literal(5))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
            f.or(
                f.and(f.eq(f.exYearD, f.literal(2000)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                    f.or(f.eq(f.exMonthD, f.literal(2)), // 创建OR条件：EXTRACT(MONTH FROM d) IN (多个值)
                        f.eq(f.exMonthD, f.literal(3)), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                        f.eq(f.exMonthD, f.literal(5)))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
                f.and(f.eq(f.exYearD, f.literal(2001)), // 创建AND条件：EXTRACT(YEAR FROM d) = 常数
                    f.eq(f.exMonthD, f.literal(1))))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("AND(OR(=(EXTRACT(FLAG(MONTH), $8), 1)," // 创建匹配器，用于断言
            + " =(EXTRACT(FLAG(MONTH), $8), 2),"
            + " =(EXTRACT(FLAG(MONTH), $8), 3),"
            + " =(EXTRACT(FLAG(MONTH), $8), 4),"
            + " =(EXTRACT(FLAG(MONTH), $8), 5)),"
            + " OR(AND(AND(>=($8, 2000-01-01), <($8, 2001-01-01)),"
            + " OR(AND(>=($8, 2000-02-01), <($8, 2000-03-01)),"
            + " AND(>=($8, 2000-03-01), <($8, 2000-04-01)),"
            + " AND(>=($8, 2000-05-01), <($8, 2000-06-01)))),"
            + " AND(AND(>=($8, 2001-01-01), <($8, 2002-01-01)),"
            + " AND(>=($8, 2001-01-01), <($8, 2001-02-01)))))"));
  } // 结束代码块

  @Test void testExtractRewriteForInvalidMonthComparison() { // 测试方法：测试无效月份比较的EXTRACT表达式重写（如月份=0、13、14等）
    // "EXTRACT(MONTH FROM ts) = 14" will never be TRUE
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(14))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " false)"));

    // "EXTRACT(MONTH FROM ts) = 0" will never be TRUE
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(0))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " false)"));

    // "EXTRACT(MONTH FROM ts) = 13" will never be TRUE
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(13))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " false)"));

    // "EXTRACT(MONTH FROM ts) = 12" might be TRUE
    // Careful with boundaries, because Calendar.DECEMBER = 11
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(12))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " AND(>=($9, 2010-12-01 00:00:00), <($9, 2011-01-01 00:00:00)))"));

    // "EXTRACT(MONTH FROM ts) = 1" can happen
    // Careful with boundaries, because Calendar.JANUARY = 0
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(1))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " AND(>=($9, 2010-01-01 00:00:00), <($9, 2010-02-01 00:00:00)))"));
  } // 结束代码块

  @Test void testExtractRewriteForInvalidDayComparison() { // 测试方法：测试无效日期比较的EXTRACT表达式重写（如2月31日）
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(11)), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
            f.eq(f.exDayTs, f.literal(32))), // 创建条件：EXTRACT(DAY FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " AND(>=($9, 2010-11-01 00:00:00), <($9, 2010-12-01 00:00:00)), false)"));
    // Feb 31 is an invalid date
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(2)), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
            f.eq(f.exDayTs, f.literal(31))), // 创建条件：EXTRACT(DAY FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " AND(>=($9, 2010-02-01 00:00:00), <($9, 2010-03-01 00:00:00)), false)"));
  } // 结束代码块

  @Test void testUnboundYearExtractRewrite() { // 测试方法：测试无边界年份的EXTRACT表达式重写
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    // No lower bound on YEAR
    checkDateRange(f, // 检查日期范围转换
        f.and(f.le(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) <= 常数
            f.eq(f.exMonthTs, f.literal(11)), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
            f.eq(f.exDayTs, f.literal(2))), // 创建条件：EXTRACT(DAY FROM ts) = 常数
        is("AND(<($9, 2011-01-01 00:00:00), =(EXTRACT(FLAG(MONTH), $9), 11)," // 创建匹配器，用于断言
            + " =(EXTRACT(FLAG(DAY), $9), 2))"));

    // No upper bound on YEAR
    checkDateRange(f, // 检查日期范围转换
        f.and(f.ge(f.exYearTs, f.literal(2010)),
            f.eq(f.exMonthTs, f.literal(11)), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
            f.eq(f.exDayTs, f.literal(2))), // 创建条件：EXTRACT(DAY FROM ts) = 常数
        // Since the year does not have a upper bound, MONTH and DAY cannot be replaced
        is("AND(>=($9, 2010-01-01 00:00:00), =(EXTRACT(FLAG(MONTH), $9), 11)," // 创建匹配器，用于断言
            + " =(EXTRACT(FLAG(DAY), $9), 2))"));

    // No lower/upper bound on YEAR for individual rexNodes.
    checkDateRange(f, // 检查日期范围转换
        f.and(f.le(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) <= 常数
            f.ge(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) >= 常数
            f.eq(f.exMonthTs, f.literal(5))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(<($9, 2011-01-01 00:00:00), AND(>=($9, 2010-01-01 00:00:00)," // 创建匹配器，用于断言
            + " <($9, 2011-01-01 00:00:00)), AND(>=($9, 2010-05-01 00:00:00),"
            + " <($9, 2010-06-01 00:00:00)))"));
  } // 结束代码块

  // Test reWrite with multiple operands
  @Test void testExtractRewriteMultipleOperands() { // 测试方法：测试多个操作数的EXTRACT表达式重写
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(10)), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
            f.eq(f.exMonthD, f.literal(5))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " AND(>=($9, 2010-10-01 00:00:00), <($9, 2010-11-01 00:00:00)),"
            + " =(EXTRACT(FLAG(MONTH), $8), 5))"));

    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.exYearTs, f.literal(2010)), // 创建AND条件：EXTRACT(YEAR FROM ts) = 常数
            f.eq(f.exMonthTs, f.literal(10)), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
            f.eq(f.exYearD, f.literal(2011)), // 创建条件：EXTRACT(YEAR FROM d) = 常数
            f.eq(f.exMonthD, f.literal(5))), // 创建条件：EXTRACT(MONTH FROM d) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " AND(>=($9, 2010-10-01 00:00:00), <($9, 2010-11-01 00:00:00)),"
            + " AND(>=($8, 2011-01-01), <($8, 2012-01-01)), AND(>=($8, 2011-05-01),"
            + " <($8, 2011-06-01)))"));
  } // 结束代码块

  @Test void testFloorEqRewrite() { // 测试方法：测试FLOOR函数等于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    // Always False
    checkDateRange(f, f.eq(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("false")); // 创建匹配器，用于断言
    checkDateRange(f, f.eq(f.timestampLiteral(TimestampString.fromCalendarFields(c)), f.floorYear), // 检查日期范围转换
        is("false")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.floorMonth, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>=($9, 2010-02-01 00:00:00), <($9, 2010-03-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.DECEMBER, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.floorMonth, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>=($9, 2010-12-01 00:00:00), <($9, 2011-01-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.floorDay, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>=($9, 2010-02-04 00:00:00), <($9, 2010-02-05 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.DECEMBER, 31, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.floorDay, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>=($9, 2010-12-31 00:00:00), <($9, 2011-01-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 4, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.floorHour, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>=($9, 2010-02-04 04:00:00), <($9, 2010-02-04 05:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.DECEMBER, 31, 23, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.floorHour, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>=($9, 2010-12-31 23:00:00), <($9, 2011-01-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 2, 32, 0); // 设置日历的年月日时分秒
    checkDateRange(f, // 检查日期范围转换
        f.eq(f.floorMinute, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建条件：FLOOR(ts TO MINUTE) = 时间戳字面量
        is("AND(>=($9, 2010-02-04 02:32:00), <($9, 2010-02-04 02:33:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 2, 59, 0); // 设置日历的年月日时分秒
    checkDateRange(f, // 检查日期范围转换
        f.eq(f.floorMinute, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建条件：FLOOR(ts TO MINUTE) = 时间戳字面量
        is("AND(>=($9, 2010-02-04 02:59:00), <($9, 2010-02-04 03:00:00))")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testFloorLtRewrite() { // 测试方法：测试FLOOR函数小于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.lt(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<($9, 2011-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.lt(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testFloorLeRewrite() { // 测试方法：测试FLOOR函数小于等于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.le(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<($9, 2011-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.le(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<($9, 2011-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testFloorGtRewrite() { // 测试方法：测试FLOOR函数大于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.gt(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">=($9, 2011-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.gt(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">=($9, 2011-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testFloorGeRewrite() { // 测试方法：测试FLOOR函数大于等于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.ge(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">=($9, 2011-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.ge(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">=($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testFloorExtractBothRewrite() { // 测试方法：测试FLOOR和EXTRACT混合表达式的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    Fixture2 f = new Fixture2();
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, // 检查日期范围转换
        f.and(f.eq(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建AND条件：FLOOR表达式 = 时间戳字面量
            f.eq(f.exMonthTs, f.literal(5))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(AND(>=($9, 2010-01-01 00:00:00), <($9, 2011-01-01 00:00:00))," // 创建匹配器，用于断言
            + " AND(>=($9, 2010-05-01 00:00:00), <($9, 2010-06-01 00:00:00)))"));

    // No lower range for floor
    checkDateRange(f, // 检查日期范围转换
        f.and(f.le(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建AND条件：FLOOR表达式 <= 时间戳字面量
            f.eq(f.exMonthTs, f.literal(5))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(<($9, 2011-01-01 00:00:00), =(EXTRACT(FLAG(MONTH), $9), 5))")); // 创建匹配器，用于断言

    // No lower range for floor
    checkDateRange(f, // 检查日期范围转换
        f.and(f.gt(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建AND条件：FLOOR表达式 > 时间戳字面量
            f.eq(f.exMonthTs, f.literal(5))), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
        is("AND(>=($9, 2011-01-01 00:00:00), =(EXTRACT(FLAG(MONTH), $9), 5))")); // 创建匹配器，用于断言

    // No upper range for individual floor rexNodes, but combined results in bounded interval
    checkDateRange(f, // 检查日期范围转换
        f.and(f.le(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建AND条件：FLOOR表达式 <= 时间戳字面量
            f.eq(f.exMonthTs, f.literal(5)), // 创建条件：EXTRACT(MONTH FROM ts) = 常数
            f.ge(f.floorYear, f.timestampLiteral(TimestampString.fromCalendarFields(c)))), // 创建条件：FLOOR(ts TO YEAR) >= 时间戳字面量
        is("AND(<($9, 2011-01-01 00:00:00), AND(>=($9, 2010-05-01 00:00:00)," // 创建匹配器，用于断言
            + " <($9, 2010-06-01 00:00:00)), >=($9, 2010-01-01 00:00:00))"));

  } // 结束代码块

  @Test void testCeilEqRewrite() { // 测试方法：测试CEIL函数等于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    // Always False
    checkDateRange(f, f.eq(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("false")); // 创建匹配器，用于断言
    checkDateRange(f, f.eq(f.timestampLiteral(TimestampString.fromCalendarFields(c)), f.ceilYear), // 检查日期范围转换
        is("false")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>($9, 2009-01-01 00:00:00), <=($9, 2010-01-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.ceilMonth, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>($9, 2010-01-01 00:00:00), <=($9, 2010-02-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.DECEMBER, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.ceilMonth, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>($9, 2010-11-01 00:00:00), <=($9, 2010-12-01 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.ceilDay, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>($9, 2010-02-03 00:00:00), <=($9, 2010-02-04 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.DECEMBER, 31, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.ceilDay, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>($9, 2010-12-30 00:00:00), <=($9, 2010-12-31 00:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 4, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.ceilHour, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>($9, 2010-02-04 03:00:00), <=($9, 2010-02-04 04:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.DECEMBER, 31, 23, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.eq(f.ceilHour, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("AND(>($9, 2010-12-31 22:00:00), <=($9, 2010-12-31 23:00:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 2, 32, 0); // 设置日历的年月日时分秒
    checkDateRange(f, // 检查日期范围转换
        f.eq(f.ceilMinute, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建条件：CEIL(ts TO MINUTE) = 时间戳字面量
        is("AND(>($9, 2010-02-04 02:31:00), <=($9, 2010-02-04 02:32:00))")); // 创建匹配器，用于断言

    c.set(2010, Calendar.FEBRUARY, 4, 2, 59, 0); // 设置日历的年月日时分秒
    checkDateRange(f, // 检查日期范围转换
        f.eq(f.ceilMinute, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建条件：CEIL(ts TO MINUTE) = 时间戳字面量
        is("AND(>($9, 2010-02-04 02:58:00), <=($9, 2010-02-04 02:59:00))")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testCeilLtRewrite() { // 测试方法：测试CEIL函数小于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.lt(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<=($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.lt(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<=($9, 2009-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testCeilLeRewrite() { // 测试方法：测试CEIL函数小于等于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.le(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<=($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.le(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is("<=($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testCeilGtRewrite() { // 测试方法：测试CEIL函数大于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.gt(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.gt(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testCeilGeRewrite() { // 测试方法：测试CEIL函数大于等于条件的重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 10, 11, 12, 05); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, f.ge(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">($9, 2010-01-01 00:00:00)")); // 创建匹配器，用于断言

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.JANUARY, 1, 0, 0, 0); // 设置日历的年月日时分秒
    checkDateRange(f, f.ge(f.ceilYear, f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 检查日期范围转换
        is(">($9, 2009-01-01 00:00:00)")); // 创建匹配器，用于断言
  } // 结束代码块

  @Test void testFloorRewriteWithTimezone() { // 测试方法：测试带时区的FLOOR函数重写
    final Calendar c = Util.calendar(); // 创建日历对象用于日期操作
    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 1, 11, 30, 0); // 设置日历的年月日时分秒
    final Fixture2 f = new Fixture2(); // 创建测试夹具Fixture2实例，提供测试所需的表达式构建工具
    checkDateRange(f, // 检查日期范围转换
        f.eq(f.floorHour, // 创建条件：FLOOR表达式 = 时间戳字面量
            f.timestampLocalTzLiteral(TimestampString.fromCalendarFields(c))), // 创建带本地时区的时间戳字面量
        "IST",
        is("AND(>=($9, 2010-02-01 17:00:00), <($9, 2010-02-01 18:00:00))"), // 创建匹配器，用于断言
        any(String.class)); // 创建任意类型匹配器

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 1, 11, 00, 0); // 设置日历的年月日时分秒
    checkDateRange(f, // 检查日期范围转换
        f.eq(f.floorHour, // 创建条件：FLOOR表达式 = 时间戳字面量
            f.timestampLiteral(TimestampString.fromCalendarFields(c))), // 创建时间戳字面量
        "IST",
        is("AND(>=($9, 2010-02-01 11:00:00), <($9, 2010-02-01 12:00:00))"), // 创建匹配器，用于断言
        any(String.class)); // 创建任意类型匹配器

    c.clear(); // 清除日历的所有字段
    c.set(2010, Calendar.FEBRUARY, 1, 00, 00, 0); // 设置日历的年月日时分秒
    checkDateRange(f, // 检查日期范围转换
        f.eq(f.floorHour, f.dateLiteral(DateString.fromCalendarFields(c))), // 创建条件：FLOOR表达式 = 时间戳字面量
        "IST",
        is("AND(>=($9, 2010-02-01 00:00:00), <($9, 2010-02-01 01:00:00))"), // 创建匹配器，用于断言
        any(String.class)); // 创建任意类型匹配器
  } // 结束代码块

  private static Set<TimeUnitRange> set(TimeUnitRange... es) { // 辅助方法：从可变参数创建不可变集合
    return ImmutableSet.copyOf(es); // 返回包含指定元素的不可变集合
  } // 结束代码块

  private void checkDateRange(Fixture f, RexNode e, Matcher<String> matcher) { // 辅助方法：检查日期范围转换结果（使用UTC时区）
    checkDateRange(f, e, "UTC", matcher, any(String.class)); // 检查日期范围转换
  } // 结束代码块

  private void checkDateRange(Fixture f, RexNode e, String timeZone, // 辅助方法：检查日期范围转换结果（支持指定时区）
      Matcher<String> matcher, Matcher<String> simplifyMatcher) {
    e = DateRangeRules.replaceTimeUnits(f.rexBuilder, e, timeZone); // 调用DateRangeRules.replaceTimeUnits方法替换时间单位为日期范围
    assertThat(e, hasToString(matcher)); // 断言转换后的表达式字符串表示与期望匹配
    final RexNode e2 = f.simplify.simplify(e); // 对表达式进行简化处理
    assertThat(e2, hasToString(simplifyMatcher)); // 断言简化后的表达式字符串表示与期望匹配
  } // 结束代码块

  /** Common expressions across tests. */
  private static class Fixture2 extends Fixture { // 定义测试夹具内部类，提供常用的表达式构建方法
    private final RexNode exYearTs; // EXTRACT YEAR from TIMESTAMP field
    private final RexNode exMonthTs; // EXTRACT MONTH from TIMESTAMP field
    private final RexNode exDayTs; // EXTRACT DAY from TIMESTAMP field
    private final RexNode exYearD; // EXTRACT YEAR from DATE field
    private final RexNode exMonthD; // EXTRACT MONTH from DATE field
    private final RexNode exDayD; // EXTRACT DAY from DATE field

    private final RexNode floorYear; // FLOOR to YEAR表达式节点
    private final RexNode floorMonth; // FLOOR to MONTH表达式节点
    private final RexNode floorDay; // FLOOR to DAY表达式节点
    private final RexNode floorHour; // FLOOR to HOUR表达式节点
    private final RexNode floorMinute; // FLOOR to MINUTE表达式节点

    private final RexNode ceilYear; // CEIL to YEAR表达式节点
    private final RexNode ceilMonth; // CEIL to MONTH表达式节点
    private final RexNode ceilDay; // CEIL to DAY表达式节点
    private final RexNode ceilHour; // CEIL to HOUR表达式节点
    private final RexNode ceilMinute; // CEIL to MINUTE表达式节点

    Fixture2() { // 构造函数，初始化所有表达式节点
      exYearTs = // 创建EXTRACT(YEAR FROM ts)表达式
          rexBuilder.makeCall(SqlStdOperatorTable.EXTRACT, // 使用表达式构建器创建EXTRACT函数调用
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.YEAR), ts)); // 创建不可变列表，包含EXTRACT函数的参数
      exMonthTs = // 创建EXTRACT(MONTH FROM ts)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.EXTRACT, // 使用表达式构建器创建EXTRACT函数调用（返回整数类型）
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.MONTH), ts)); // 创建不可变列表，包含EXTRACT函数的参数
      exDayTs = // 创建EXTRACT(DAY FROM ts)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.EXTRACT, // 使用表达式构建器创建EXTRACT函数调用（返回整数类型）
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.DAY), ts)); // 创建不可变列表，包含EXTRACT函数的参数
      exYearD = // 创建EXTRACT(YEAR FROM d)表达式
          rexBuilder.makeCall(SqlStdOperatorTable.EXTRACT, // 使用表达式构建器创建EXTRACT函数调用
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.YEAR), d)); // 创建不可变列表，包含EXTRACT函数的参数
      exMonthD = // 创建EXTRACT(MONTH FROM d)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.EXTRACT, // 使用表达式构建器创建EXTRACT函数调用（返回整数类型）
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.MONTH), d)); // 创建不可变列表，包含EXTRACT函数的参数
      exDayD = // 创建EXTRACT(DAY FROM d)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.EXTRACT, // 使用表达式构建器创建EXTRACT函数调用（返回整数类型）
              ImmutableList.of(rexBuilder.makeFlag(TimeUnitRange.DAY), d)); // 创建不可变列表，包含EXTRACT函数的参数

      floorYear = // 创建FLOOR(ts TO YEAR)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.FLOOR, // 使用表达式构建器创建FLOOR函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.YEAR))); // 创建不可变列表，包含EXTRACT函数的参数
      floorMonth = // 创建FLOOR(ts TO MONTH)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.FLOOR, // 使用表达式构建器创建FLOOR函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.MONTH))); // 创建不可变列表，包含EXTRACT函数的参数
      floorDay = // 创建FLOOR(ts TO DAY)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.FLOOR, // 使用表达式构建器创建FLOOR函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.DAY))); // 创建不可变列表，包含EXTRACT函数的参数
      floorHour = // 创建FLOOR(ts TO HOUR)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.FLOOR, // 使用表达式构建器创建FLOOR函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.HOUR))); // 创建不可变列表，包含EXTRACT函数的参数
      floorMinute = // 创建FLOOR(ts TO MINUTE)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.FLOOR, // 使用表达式构建器创建FLOOR函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.MINUTE))); // 创建不可变列表，包含EXTRACT函数的参数

      ceilYear = // 创建CEIL(ts TO YEAR)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.CEIL, // 使用表达式构建器创建CEIL函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.YEAR))); // 创建不可变列表，包含EXTRACT函数的参数
      ceilMonth = // 创建CEIL(ts TO MONTH)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.CEIL, // 使用表达式构建器创建CEIL函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.MONTH))); // 创建不可变列表，包含EXTRACT函数的参数
      ceilDay = // 创建CEIL(ts TO DAY)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.CEIL, // 使用表达式构建器创建CEIL函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.DAY))); // 创建不可变列表，包含EXTRACT函数的参数
      ceilHour = // 创建CEIL(ts TO HOUR)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.CEIL, // 使用表达式构建器创建CEIL函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.HOUR))); // 创建不可变列表，包含EXTRACT函数的参数
      ceilMinute = // 创建CEIL(ts TO MINUTE)表达式
          rexBuilder.makeCall(intRelDataType, SqlStdOperatorTable.CEIL, // 使用表达式构建器创建CEIL函数调用（返回整数类型）
              ImmutableList.of(ts, rexBuilder.makeFlag(TimeUnitRange.MINUTE))); // 创建不可变列表，包含EXTRACT函数的参数
    } // 结束代码块
  } // 结束代码块
} // 结束代码块
