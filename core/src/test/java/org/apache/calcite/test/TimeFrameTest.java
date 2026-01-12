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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.test; // 声明包名为org.apache.calcite.test，表示该类位于测试包中

import org.apache.calcite.avatica.util.TimeUnit; // 导入TimeUnit类，用于表示时间单位（如年、月、日等）
import org.apache.calcite.rel.type.TimeFrame; // 导入TimeFrame类，用于表示时间框架，是时间单位的抽象表示
import org.apache.calcite.rel.type.TimeFrameSet; // 导入TimeFrameSet类，用于管理一组相关的时间框架
import org.apache.calcite.rel.type.TimeFrames; // 导入TimeFrames类，提供核心时间框架集合的访问
import org.apache.calcite.util.Pair; // 导入Pair类，用于存储键值对

import org.apache.commons.math3.fraction.BigFraction; // 导入BigFraction类，用于精确的分数计算

import com.google.common.collect.ImmutableMap; // 导入ImmutableMap类，用于创建不可变的Map

import org.hamcrest.Matcher; // 导入Matcher类，用于断言匹配
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import static org.apache.calcite.avatica.util.DateTimeUtils.dateStringToUnixDate; // 导入日期字符串转Unix日期的方法
import static org.apache.calcite.avatica.util.DateTimeUtils.timestampStringToUnixDate; // 导入时间戳字符串转Unix时间戳的方法
import static org.apache.calcite.avatica.util.DateTimeUtils.unixDateToString; // 导入Unix日期转日期字符串的方法
import static org.apache.calcite.avatica.util.DateTimeUtils.unixTimestampToString; // 导入Unix时间戳转时间戳字符串的方法
import static org.apache.calcite.avatica.util.TimeUnit.CENTURY; // 导入CENTURY时间单位常量（世纪）
import static org.apache.calcite.avatica.util.TimeUnit.DAY; // 导入DAY时间单位常量（天）
import static org.apache.calcite.avatica.util.TimeUnit.DECADE; // 导入DECADE时间单位常量（十年）
import static org.apache.calcite.avatica.util.TimeUnit.HOUR; // 导入HOUR时间单位常量（小时）
import static org.apache.calcite.avatica.util.TimeUnit.ISODOW; // 导入ISODOW时间单位常量（ISO周日）
import static org.apache.calcite.avatica.util.TimeUnit.ISOYEAR; // 导入ISOYEAR时间单位常量（ISO年）
import static org.apache.calcite.avatica.util.TimeUnit.MICROSECOND; // 导入MICROSECOND时间单位常量（微秒）
import static org.apache.calcite.avatica.util.TimeUnit.MILLENNIUM; // 导入MILLENNIUM时间单位常量（千年）
import static org.apache.calcite.avatica.util.TimeUnit.MILLISECOND; // 导入MILLISECOND时间单位常量（毫秒）
import static org.apache.calcite.avatica.util.TimeUnit.MINUTE; // 导入MINUTE时间单位常量（分钟）
import static org.apache.calcite.avatica.util.TimeUnit.MONTH; // 导入MONTH时间单位常量（月）
import static org.apache.calcite.avatica.util.TimeUnit.NANOSECOND; // 导入NANOSECOND时间单位常量（纳秒）
import static org.apache.calcite.avatica.util.TimeUnit.QUARTER; // 导入QUARTER时间单位常量（季度）
import static org.apache.calcite.avatica.util.TimeUnit.SECOND; // 导入SECOND时间单位常量（秒）
import static org.apache.calcite.avatica.util.TimeUnit.WEEK; // 导入WEEK时间单位常量（周）
import static org.apache.calcite.avatica.util.TimeUnit.YEAR; // 导入YEAR时间单位常量（年）

import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，用于断言值相等
import static org.hamcrest.CoreMatchers.notNullValue; // 导入notNullValue匹配器，用于断言值不为null
import static org.hamcrest.CoreMatchers.nullValue; // 导入nullValue匹配器，用于断言值为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat方法，用于执行断言
import static org.junit.jupiter.api.Assertions.fail; // 导入fail方法，用于标记测试失败

import static java.util.Objects.requireNonNull; // 导入requireNonNull方法，用于检查参数不为null

/** Unit test for {@link org.apache.calcite.rel.type.TimeFrame}. */ // TimeFrame类的单元测试
public class TimeFrameTest { // 定义TimeFrameTest测试类
  /** Unit test for {@link org.apache.calcite.rel.type.TimeFrames#CORE}. */ // 测试核心时间框架集合的单元测试
  @Test void testAvaticaTimeFrame() { // 测试方法，验证Avatica时间框架的基本功能
    final TimeFrameSet timeFrameSet = TimeFrames.CORE; // 获取核心时间框架集合，包含标准的时间单位
    final TimeFrame year = timeFrameSet.get(TimeUnit.YEAR); // 从时间框架集合中获取YEAR时间单位对应的时间框架
    assertThat(year, notNullValue()); // 断言year对象不为null
    assertThat(year.name(), is("YEAR")); // 断言year的名称为"YEAR"
    assertThat(timeFrameSet.getUnit(year), is(YEAR)); // 断言从时间框架获取的单位为YEAR

    final TimeFrame month = timeFrameSet.get(MONTH); // 从时间框架集合中获取MONTH时间单位对应的时间框架
    assertThat(month, notNullValue()); // 断言month对象不为null
    assertThat(month.name(), is("MONTH")); // 断言month的名称为"MONTH"
    assertThat(timeFrameSet.getUnit(month), is(MONTH)); // 断言从时间框架获取的单位为MONTH

    final Number monthPerYear = month.per(year); // 计算一个月占一年的比例（应为1/12）
    assertThat(monthPerYear, notNullValue()); // 断言monthPerYear不为null
    assertThat(monthPerYear, is(new BigFraction(12))); // 断言一年有12个月，用BigFraction表示为12
    final Number yearPerMonth = year.per(month); // 计算一年占一个月的比例（应为12）
    assertThat(yearPerMonth, notNullValue()); // 断言yearPerMonth不为null
    assertThat(yearPerMonth, is(BigFraction.ONE.divide(12))); // 断言一年占12个月，用BigFraction表示为1/12
    final Number monthPerMonth = month.per(month); // 计算一个月占一个月的比例（应为1）
    assertThat(monthPerMonth, notNullValue()); // 断言monthPerMonth不为null
    assertThat(monthPerMonth, is(BigFraction.ONE)); // 断言一个月占一个月为1，用BigFraction表示为1

    final TimeFrame second = timeFrameSet.get(TimeUnit.SECOND); // 从时间框架集合中获取SECOND时间单位对应的时间框架
    assertThat(second, notNullValue()); // 断言second对象不为null
    assertThat(second.name(), is("SECOND")); // 断言second的名称为"SECOND"

    final TimeFrame minute = timeFrameSet.get(TimeUnit.MINUTE); // 从时间框架集合中获取MINUTE时间单位对应的时间框架
    assertThat(minute, notNullValue()); // 断言minute对象不为null
    assertThat(minute.name(), is("MINUTE")); // 断言minute的名称为"MINUTE"

    final TimeFrame nano = timeFrameSet.get(TimeUnit.NANOSECOND); // 从时间框架集合中获取NANOSECOND时间单位对应的时间框架
    assertThat(nano, notNullValue()); // 断言nano对象不为null
    assertThat(nano.name(), is("NANOSECOND")); // 断言nano的名称为"NANOSECOND"

    final Number secondPerMonth = second.per(month); // 计算一秒占一个月的比例（因为单位和时间跨度不同，应为null）
    assertThat(secondPerMonth, nullValue()); // 断言secondPerMonth为null，因为秒和月属于不同的时间家族，无法直接计算比例
    final Number nanoPerMinute = nano.per(minute); // 计算一纳秒占一分钟的比例
    assertThat(nanoPerMinute, notNullValue()); // 断言nanoPerMinute不为null
    assertThat(nanoPerMinute, // 断言纳秒占分钟的比例
        is(BigFraction.ONE.multiply(1_000).multiply(1_000).multiply(1_000) // 一纳秒等于1/1000000000秒，一分钟等于60秒，所以比例为1/60000000000
            .multiply(60))); // 60秒乘以1000毫秒/秒乘以1000微秒/毫秒乘以1000纳秒/微秒

    // ISOWEEK is the only core time frame without a corresponding time unit. // ISOWEEK是唯一没有对应TimeUnit的核心时间框架
    // There is no TimeUnit.ISOWEEK. // 不存在TimeUnit.ISOWEEK
    final TimeFrame isoWeek = timeFrameSet.get("ISOWEEK"); // 从时间框架集合中通过名称获取ISOWEEK时间框架
    assertThat(isoWeek, notNullValue()); // 断言isoWeek对象不为null
    assertThat(isoWeek.name(), is("ISOWEEK")); // 断言isoWeek的名称为"ISOWEEK"
    assertThat(timeFrameSet.getUnit(isoWeek), nullValue()); // 断言从时间框架获取的单位为null，因为没有对应的TimeUnit

    // FRAC_SECOND is an alias. // FRAC_SECOND是一个别名
    final TimeFrame fracSecond = timeFrameSet.get("FRAC_SECOND"); // 从时间框架集合中获取FRAC_SECOND时间框架
    assertThat(fracSecond, notNullValue()); // 断言fracSecond对象不为null
    assertThat(fracSecond.name(), is("MICROSECOND")); // 断言fracSecond的名称为"MICROSECOND"，说明它是指向MICROSECOND的别名
    assertThat(timeFrameSet.getUnit(fracSecond), is(MICROSECOND)); // 断言从时间框架获取的单位为MICROSECOND

    // SQL_TSI_QUARTER is an alias. // SQL_TSI_QUARTER是一个别名
    final TimeFrame sqlTsiQuarter = timeFrameSet.get("SQL_TSI_QUARTER"); // 从时间框架集合中获取SQL_TSI_QUARTER时间框架
    assertThat(sqlTsiQuarter, notNullValue()); // 断言sqlTsiQuarter对象不为null
    assertThat(sqlTsiQuarter.name(), is("QUARTER")); // 断言sqlTsiQuarter的名称为"QUARTER"，说明它是指向QUARTER的别名
    assertThat(timeFrameSet.getUnit(sqlTsiQuarter), is(QUARTER)); // 断言从时间框架获取的单位为QUARTER
  }

  @Test void testConflict() { // 测试方法，验证时间框架构建器在遇到冲突时的错误处理
    TimeFrameSet.Builder b = TimeFrameSet.builder(); // 创建一个TimeFrameSet构建器，用于构建自定义的时间框架集合
    b.addCore("SECOND"); // 添加SECOND作为核心时间框架，核心框架是最基本的时间单位，不依赖其他框架
    b.addMultiple("MINUTE", 60, "SECOND"); // 添加MINUTE时间框架，定义一分钟等于60秒
    b.addMultiple("HOUR", 60, "MINUTE"); // 添加HOUR时间框架，定义一小时等于60分钟
    b.addMultiple("DAY", 24, "SECOND"); // 添加DAY时间框架，定义一天等于24秒（这里故意设置错误，测试错误处理）
    b.addDivision("MILLISECOND", 1_000, "SECOND"); // 添加MILLISECOND时间框架，定义一秒等于1000毫秒

    // It's important that TimeFrame.Builder throws when you attempt to add // 重要的是，TimeFrame.Builder在尝试添加
    // a frame with the same name. It prevents DAGs and cycles. // 相同名称的框架时会抛出异常，这防止了有向无环图（DAG）和循环
    try { // 开始try块，捕获预期的异常
      b.addDivision("MILLISECOND", 10_000, "MINUTE"); // 尝试再次添加MILLISECOND框架，但这次定义一分钟等于10000毫秒（这是冲突的定义）
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("duplicate frame: MILLISECOND")); // 断言异常消息为"duplicate frame: MILLISECOND"
    }

    try { // 开始try块，捕获预期的异常
      b.addCore("SECOND"); // 尝试再次添加SECOND核心框架
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("duplicate frame: SECOND")); // 断言异常消息为"duplicate frame: SECOND"
    }

    try { // 开始try块，捕获预期的异常
      b.addQuotient("SECOND", "MINUTE", "HOUR"); // 尝试使用商的方式添加SECOND框架
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("duplicate frame: SECOND")); // 断言异常消息为"duplicate frame: SECOND"
    }

    try { // 开始try块，捕获预期的异常
      b.addQuotient("MINUTE_OF_WEEK", "MINUTE", "WEEK"); // 尝试添加MINUTE_OF_WEEK框架，但WEEK框架尚未定义
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("unknown frame: WEEK")); // 断言异常消息为"unknown frame: WEEK"
    }

    try { // 开始try块，捕获预期的异常
      b.addQuotient("DAY_OF_WEEK", "DAY", "YEAR"); // 尝试添加DAY_OF_WEEK框架，但YEAR框架尚未定义
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("unknown frame: YEAR")); // 断言异常消息为"unknown frame: YEAR"
    }

    try { // 开始try块，捕获预期的异常
      b.addAlias("SECOND", "DAY"); // 尝试添加SECOND作为DAY的别名
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("duplicate frame: SECOND")); // 断言异常消息为"duplicate frame: SECOND"
    }

    try { // 开始try块，捕获预期的异常
      b.addAlias("FOO", "BAZ"); // 尝试添加FOO作为BAZ的别名，但BAZ框架不存在
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("unknown frame: BAZ")); // 断言异常消息为"unknown frame: BAZ"
    }

    // Can't define NANOSECOND in terms of a frame that has not been defined // 不能基于未定义的框架来定义NANOSECOND
    // yet. // 还未定义
    try { // 开始try块，捕获预期的异常
      b.addDivision("NANOSECOND", 1_000, "MICROSECOND"); // 尝试基于MICROSECOND定义NANOSECOND，但MICROSECOND尚未定义
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("unknown frame: MICROSECOND")); // 断言异常消息为"unknown frame: MICROSECOND"
    }

    // We can define NANOSECOND and MICROSECOND as long as we define each frame // 我们可以定义NANOSECOND和MICROSECOND，只要我们按照正确的顺序
    // in terms of previous frames. // 基于之前定义的框架来定义每个框架
    b.addDivision("NANOSECOND", 1_000_000, "MILLISECOND"); // 添加NANOSECOND，定义一毫秒等于1000000纳秒
    b.addMultiple("MICROSECOND", 1_000, "NANOSECOND"); // 添加MICROSECOND，定义一微秒等于1000纳秒

    // Can't define a frame in terms of itself. // 不能基于自身来定义一个框架
    // (I guess you should use a core frame.) // （我想你应该使用核心框架）
    try { // 开始try块，捕获预期的异常
      b.addDivision("PICOSECOND", 1, "PICOSECOND"); // 尝试基于自身定义PICOSECOND框架
      fail("expected error"); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("unknown frame: PICOSECOND")); // 断言异常消息为"unknown frame: PICOSECOND"
    }

    final TimeFrameSet timeFrameSet = b.build(); // 构建时间框架集合

    final TimeFrame second = timeFrameSet.get("SECOND"); // 从时间框架集合中获取SECOND框架
    final TimeFrame hour = timeFrameSet.get("HOUR"); // 从时间框架集合中获取HOUR框架
    assertThat(hour.per(second), is(BigFraction.ONE.divide(3_600))); // 断言一小时占3600秒，用BigFraction表示为1/3600
    final TimeFrame millisecond = timeFrameSet.get("MILLISECOND"); // 从时间框架集合中获取MILLISECOND框架
    assertThat(hour.per(millisecond), is(BigFraction.ONE.divide(3_600_000))); // 断言一小时占3600000毫秒（3600秒*1000）
    final TimeFrame nanosecond = timeFrameSet.get("NANOSECOND"); // 从时间框架集合中获取NANOSECOND框架
    assertThat(nanosecond.per(second), // 断言一纳秒占一秒的比例
        is(BigFraction.ONE.multiply(1_000_000_000))); // 用BigFraction表示为1/1000000000
  }

  @Test void testEvalFloor() { // 测试方法，验证时间框架的向下取整（floor）功能
    final Fixture f = new Fixture(); // 创建Fixture测试辅助对象
    f.checkDateFloor("1970-08-01", WEEK, is("1970-07-26")); // 验证1970-08-01（周六）向下取整到周，结果为1970-07-26（周日）
    f.checkDateFloor("1970-08-02", WEEK, is("1970-08-02")); // 验证1970-08-02（周日）向下取整到周，结果为1970-08-02（周日）
    f.checkDateFloor("1970-08-03", WEEK, is("1970-08-02")); // 验证1970-08-03（周一）向下取整到周，结果为1970-08-02（周日）
    f.checkDateFloor("1970-08-04", WEEK, is("1970-08-02")); // 验证1970-08-04（周二）向下取整到周，结果为1970-08-02（周日）

    f.checkDateFloor("1970-08-01", f.isoWeek, is("1970-07-27")); // 验证1970-08-01（周六）向下取整到ISO周，结果为1970-07-27（周一）
    f.checkDateFloor("1970-08-02", f.isoWeek, is("1970-07-27")); // 验证1970-08-02（周日）向下取整到ISO周，结果为1970-07-27（周一）
    f.checkDateFloor("1970-08-03", f.isoWeek, is("1970-08-03")); // 验证1970-08-03（周一）向下取整到ISO周，结果为1970-08-03（周一）
    f.checkDateFloor("1970-08-04", f.isoWeek, is("1970-08-03")); // 验证1970-08-04（周二）向下取整到ISO周，结果为1970-08-03（周一）

    f.checkDateFloor("1970-08-04", "WEEK_MONDAY", is("1970-08-03")); // 验证1970-08-04（周二）向下取整到周一为起始的周，结果为1970-08-03（周一）
    f.checkDateFloor("1970-08-04", "WEEK_TUESDAY", is("1970-08-04")); // 验证1970-08-04（周二）向下取整到周二为起始的周，结果为1970-08-04（周二）

    f.checkTimestampFloor("1970-01-01 01:23:45", HOUR, // 验证时间戳向下取整到小时
        0, is("1970-01-01 01:00:00")); // 精度为0，结果为1970-01-01 01:00:00
    f.checkTimestampFloor("1970-01-01 01:23:45", MINUTE, // 验证时间戳向下取整到分钟
        0, is("1970-01-01 01:23:00")); // 精度为0，结果为1970-01-01 01:23:00
    f.checkTimestampFloor("1970-01-01 01:23:45.67", SECOND, // 验证时间戳向下取整到秒
        0, is("1970-01-01 01:23:45")); // 精度为0，结果为1970-01-01 01:23:45
    f.checkTimestampFloor("1970-01-01 01:23:45.6789012345", MILLISECOND, // 验证时间戳向下取整到毫秒
        4, is("1970-01-01 01:23:45.6790")); // 精度为4，结果为1970-01-01 01:23:45.6790（四舍五入）
    // Time frames can represent unlimited precision, but out representation of // 时间框架可以表示无限精度，但我们的时间戳表示
    // timestamp can't represent more than millisecond precision. // 不能表示超过毫秒的精度
    f.checkTimestampFloor("1970-01-01 01:23:45.6789012345", MICROSECOND, // 验证时间戳向下取整到微秒
        7, is("1970-01-01 01:23:45.6790000")); // 精度为7，结果为1970-01-01 01:23:45.6790000（四舍五入到毫秒）

    f.checkTimestampFloor("1971-12-25 01:23:45", DAY, // 验证时间戳向下取整到天
        0, is("1971-12-25 00:00:00")); // 精度为0，结果为1971-12-25 00:00:00
    f.checkTimestampFloor("1971-12-25 01:23:45", WEEK, // 验证时间戳向下取整到周
        0, is("1971-12-19 00:00:00")); // 精度为0，结果为1971-12-19 00:00:00（周日）
  }

  @Test void testCanRollUp() { // 测试方法，验证时间框架之间的向上聚合（roll up）能力
    final Fixture f = new Fixture(); // 创建Fixture测试辅助对象

    // The rollup from DAY to MONTH is special. It provides the bridge between // 从DAY到MONTH的向上聚合是特殊的，它提供了
    // the frames in the SECOND family and those in the MONTH family. // SECOND家族和MONTH家族时间框架之间的桥梁
    f.checkCanRollUp(DAY, MONTH, true); // 验证DAY可以向上聚合到MONTH
    f.checkCanRollUp(MONTH, DAY, false); // 验证MONTH不能向上聚合到DAY（只能向下分解）

    // Note 0: when we pass TimeUnit.ISODOW to tests, we mean f.ISOWEEK. // 注0：当我们传递TimeUnit.ISODOW给测试时，我们指的是f.ISOWEEK

    f.checkCanRollUp(NANOSECOND, NANOSECOND, true); // 验证NANOSECOND可以向上聚合到NANOSECOND（自身）
    f.checkCanRollUp(NANOSECOND, MICROSECOND, true); // 验证NANOSECOND可以向上聚合到MICROSECOND
    f.checkCanRollUp(NANOSECOND, MILLISECOND, true); // 验证NANOSECOND可以向上聚合到MILLISECOND
    f.checkCanRollUp(NANOSECOND, SECOND, true); // 验证NANOSECOND可以向上聚合到SECOND
    f.checkCanRollUp(NANOSECOND, MINUTE, true); // 验证NANOSECOND可以向上聚合到MINUTE
    f.checkCanRollUp(NANOSECOND, HOUR, true); // 验证NANOSECOND可以向上聚合到HOUR
    f.checkCanRollUp(NANOSECOND, DAY, true); // 验证NANOSECOND可以向上聚合到DAY
    f.checkCanRollUp(NANOSECOND, WEEK, true); // 验证NANOSECOND可以向上聚合到WEEK
    f.checkCanRollUp(NANOSECOND, f.isoWeek, true); // 验证NANOSECOND可以向上聚合到ISO周（见注0）
    f.checkCanRollUp(NANOSECOND, MONTH, true); // 验证NANOSECOND可以向上聚合到MONTH
    f.checkCanRollUp(NANOSECOND, QUARTER, true); // 验证NANOSECOND可以向上聚合到QUARTER
    f.checkCanRollUp(NANOSECOND, YEAR, true); // 验证NANOSECOND可以向上聚合到YEAR
    f.checkCanRollUp(NANOSECOND, ISOYEAR, true); // 验证NANOSECOND可以向上聚合到ISOYEAR
    f.checkCanRollUp(NANOSECOND, CENTURY, true); // 验证NANOSECOND可以向上聚合到CENTURY
    f.checkCanRollUp(NANOSECOND, DECADE, true); // 验证NANOSECOND可以向上聚合到DECADE
    f.checkCanRollUp(NANOSECOND, MILLENNIUM, true); // 验证NANOSECOND可以向上聚合到MILLENNIUM

    f.checkCanRollUp(MICROSECOND, NANOSECOND, false); // 验证MICROSECOND不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(MICROSECOND, MICROSECOND, true); // 验证MICROSECOND可以向上聚合到MICROSECOND（自身）
    f.checkCanRollUp(MICROSECOND, MILLISECOND, true); // 验证MICROSECOND可以向上聚合到MILLISECOND
    f.checkCanRollUp(MICROSECOND, SECOND, true); // 验证MICROSECOND可以向上聚合到SECOND
    f.checkCanRollUp(MICROSECOND, MINUTE, true); // 验证MICROSECOND可以向上聚合到MINUTE
    f.checkCanRollUp(MICROSECOND, HOUR, true); // 验证MICROSECOND可以向上聚合到HOUR
    f.checkCanRollUp(MICROSECOND, DAY, true); // 验证MICROSECOND可以向上聚合到DAY
    f.checkCanRollUp(MICROSECOND, WEEK, true); // 验证MICROSECOND可以向上聚合到WEEK
    f.checkCanRollUp(MICROSECOND, f.isoWeek, true); // 验证MICROSECOND可以向上聚合到ISO周
    f.checkCanRollUp(MICROSECOND, MONTH, true); // 验证MICROSECOND可以向上聚合到MONTH
    f.checkCanRollUp(MICROSECOND, QUARTER, true); // 验证MICROSECOND可以向上聚合到QUARTER
    f.checkCanRollUp(MICROSECOND, YEAR, true); // 验证MICROSECOND可以向上聚合到YEAR
    f.checkCanRollUp(MICROSECOND, ISOYEAR, true); // 验证MICROSECOND可以向上聚合到ISOYEAR
    f.checkCanRollUp(MICROSECOND, CENTURY, true); // 验证MICROSECOND可以向上聚合到CENTURY
    f.checkCanRollUp(MICROSECOND, DECADE, true); // 验证MICROSECOND可以向上聚合到DECADE
    f.checkCanRollUp(MICROSECOND, MILLENNIUM, true); // 验证MICROSECOND可以向上聚合到MILLENNIUM

    f.checkCanRollUp(MILLISECOND, NANOSECOND, false); // 验证MILLISECOND不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(MILLISECOND, MICROSECOND, false); // 验证MILLISECOND不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(MILLISECOND, MILLISECOND, true); // 验证MILLISECOND可以向上聚合到MILLISECOND（自身）
    f.checkCanRollUp(MILLISECOND, SECOND, true); // 验证MILLISECOND可以向上聚合到SECOND
    f.checkCanRollUp(MILLISECOND, MINUTE, true); // 验证MILLISECOND可以向上聚合到MINUTE
    f.checkCanRollUp(MILLISECOND, HOUR, true); // 验证MILLISECOND可以向上聚合到HOUR
    f.checkCanRollUp(MILLISECOND, DAY, true); // 验证MILLISECOND可以向上聚合到DAY
    f.checkCanRollUp(MILLISECOND, WEEK, true); // 验证MILLISECOND可以向上聚合到WEEK
    f.checkCanRollUp(MILLISECOND, f.isoWeek, true); // 验证MILLISECOND可以向上聚合到ISO周
    f.checkCanRollUp(MILLISECOND, MONTH, true); // 验证MILLISECOND可以向上聚合到MONTH
    f.checkCanRollUp(MILLISECOND, QUARTER, true); // 验证MILLISECOND可以向上聚合到QUARTER
    f.checkCanRollUp(MILLISECOND, YEAR, true); // 验证MILLISECOND可以向上聚合到YEAR
    f.checkCanRollUp(MILLISECOND, ISOYEAR, true); // 验证MILLISECOND可以向上聚合到ISOYEAR
    f.checkCanRollUp(MILLISECOND, CENTURY, true); // 验证MILLISECOND可以向上聚合到CENTURY
    f.checkCanRollUp(MILLISECOND, DECADE, true); // 验证MILLISECOND可以向上聚合到DECADE
    f.checkCanRollUp(MILLISECOND, MILLENNIUM, true); // 验证MILLISECOND可以向上聚合到MILLENNIUM

    f.checkCanRollUp(SECOND, NANOSECOND, false); // 验证SECOND不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(SECOND, MICROSECOND, false); // 验证SECOND不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(SECOND, MILLISECOND, false); // 验证SECOND不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(SECOND, SECOND, true); // 验证SECOND可以向上聚合到SECOND（自身）
    f.checkCanRollUp(SECOND, MINUTE, true); // 验证SECOND可以向上聚合到MINUTE
    f.checkCanRollUp(SECOND, HOUR, true); // 验证SECOND可以向上聚合到HOUR
    f.checkCanRollUp(SECOND, DAY, true); // 验证SECOND可以向上聚合到DAY
    f.checkCanRollUp(SECOND, WEEK, true); // 验证SECOND可以向上聚合到WEEK
    f.checkCanRollUp(SECOND, f.isoWeek, true); // 验证SECOND可以向上聚合到ISO周
    f.checkCanRollUp(SECOND, MONTH, true); // 验证SECOND可以向上聚合到MONTH
    f.checkCanRollUp(SECOND, QUARTER, true); // 验证SECOND可以向上聚合到QUARTER
    f.checkCanRollUp(SECOND, YEAR, true); // 验证SECOND可以向上聚合到YEAR
    f.checkCanRollUp(SECOND, ISOYEAR, true); // 验证SECOND可以向上聚合到ISOYEAR
    f.checkCanRollUp(SECOND, CENTURY, true); // 验证SECOND可以向上聚合到CENTURY
    f.checkCanRollUp(SECOND, DECADE, true); // 验证SECOND可以向上聚合到DECADE
    f.checkCanRollUp(SECOND, MILLENNIUM, true); // 验证SECOND可以向上聚合到MILLENNIUM

    f.checkCanRollUp(MINUTE, NANOSECOND, false); // 验证MINUTE不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(MINUTE, MICROSECOND, false); // 验证MINUTE不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(MINUTE, MILLISECOND, false); // 验证MINUTE不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(MINUTE, SECOND, false); // 验证MINUTE不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(MINUTE, MINUTE, true); // 验证MINUTE可以向上聚合到MINUTE（自身）
    f.checkCanRollUp(MINUTE, HOUR, true); // 验证MINUTE可以向上聚合到HOUR
    f.checkCanRollUp(MINUTE, DAY, true); // 验证MINUTE可以向上聚合到DAY
    f.checkCanRollUp(MINUTE, WEEK, true); // 验证MINUTE可以向上聚合到WEEK
    f.checkCanRollUp(MINUTE, f.isoWeek, true); // 验证MINUTE可以向上聚合到ISO周
    f.checkCanRollUp(MINUTE, MONTH, true); // 验证MINUTE可以向上聚合到MONTH
    f.checkCanRollUp(MINUTE, QUARTER, true); // 验证MINUTE可以向上聚合到QUARTER
    f.checkCanRollUp(MINUTE, YEAR, true); // 验证MINUTE可以向上聚合到YEAR
    f.checkCanRollUp(MINUTE, ISOYEAR, true); // 验证MINUTE可以向上聚合到ISOYEAR
    f.checkCanRollUp(MINUTE, CENTURY, true); // 验证MINUTE可以向上聚合到CENTURY
    f.checkCanRollUp(MINUTE, DECADE, true); // 验证MINUTE可以向上聚合到DECADE
    f.checkCanRollUp(MINUTE, MILLENNIUM, true); // 验证MINUTE可以向上聚合到MILLENNIUM

    f.checkCanRollUp(HOUR, NANOSECOND, false); // 验证HOUR不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(HOUR, MICROSECOND, false); // 验证HOUR不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(HOUR, MILLISECOND, false); // 验证HOUR不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(HOUR, SECOND, false); // 验证HOUR不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(HOUR, MINUTE, false); // 验证HOUR不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(HOUR, HOUR, true); // 验证HOUR可以向上聚合到HOUR（自身）
    f.checkCanRollUp(HOUR, DAY, true); // 验证HOUR可以向上聚合到DAY
    f.checkCanRollUp(HOUR, WEEK, true); // 验证HOUR可以向上聚合到WEEK
    f.checkCanRollUp(HOUR, f.isoWeek, true); // 验证HOUR可以向上聚合到ISO周
    f.checkCanRollUp(HOUR, MONTH, true); // 验证HOUR可以向上聚合到MONTH
    f.checkCanRollUp(HOUR, QUARTER, true); // 验证HOUR可以向上聚合到QUARTER
    f.checkCanRollUp(HOUR, YEAR, true); // 验证HOUR可以向上聚合到YEAR
    f.checkCanRollUp(HOUR, ISOYEAR, true); // 验证HOUR可以向上聚合到ISOYEAR
    f.checkCanRollUp(HOUR, DECADE, true); // 验证HOUR可以向上聚合到DECADE
    f.checkCanRollUp(HOUR, CENTURY, true); // 验证HOUR可以向上聚合到CENTURY
    f.checkCanRollUp(HOUR, MILLENNIUM, true); // 验证HOUR可以向上聚合到MILLENNIUM

    f.checkCanRollUp(DAY, NANOSECOND, false); // 验证DAY不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(DAY, MICROSECOND, false); // 验证DAY不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(DAY, MILLISECOND, false); // 验证DAY不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(DAY, SECOND, false); // 验证DAY不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(DAY, MINUTE, false); // 验证DAY不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(DAY, HOUR, false); // 验证DAY不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(DAY, DAY, true); // 验证DAY可以向上聚合到DAY（自身）
    f.checkCanRollUp(DAY, WEEK, true); // 验证DAY可以向上聚合到WEEK
    f.checkCanRollUp(DAY, f.isoWeek, true); // 验证DAY可以向上聚合到ISO周
    f.checkCanRollUp(DAY, MONTH, true); // 验证DAY可以向上聚合到MONTH
    f.checkCanRollUp(DAY, QUARTER, true); // 验证DAY可以向上聚合到QUARTER
    f.checkCanRollUp(DAY, YEAR, true); // 验证DAY可以向上聚合到YEAR
    f.checkCanRollUp(DAY, ISOYEAR, true); // 验证DAY可以向上聚合到ISOYEAR
    f.checkCanRollUp(DAY, DECADE, true); // 验证DAY可以向上聚合到DECADE
    f.checkCanRollUp(DAY, CENTURY, true); // 验证DAY可以向上聚合到CENTURY
    f.checkCanRollUp(DAY, MILLENNIUM, true); // 验证DAY可以向上聚合到MILLENNIUM

    // Note 1. WEEK cannot roll up to MONTH, YEAR or higher. // 注1：WEEK不能向上聚合到MONTH、YEAR或更高粒度
    // Some weeks cross month, year, decade, century and millennium boundaries. // 有些周跨越月、年、十年、世纪和千年的边界

    // Note 2. WEEK, MONTH, QUARTER, YEAR, DECADE, CENTURY, MILLENNIUM cannot // 注2：WEEK、MONTH、QUARTER、YEAR、DECADE、CENTURY、MILLENNIUM
    // roll up to ISOYEAR. Only f.ISOWEEK can roll up to ISOYEAR. // 不能向上聚合到ISOYEAR。只有f.ISOWEEK可以向上聚合到ISOYEAR

    f.checkCanRollUp(WEEK, NANOSECOND, false); // 验证WEEK不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(WEEK, MICROSECOND, false); // 验证WEEK不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(WEEK, MILLISECOND, false); // 验证WEEK不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(WEEK, SECOND, false); // 验证WEEK不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(WEEK, MINUTE, false); // 验证WEEK不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(WEEK, HOUR, false); // 验证WEEK不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(WEEK, DAY, false); // 验证WEEK不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(WEEK, WEEK, true); // 验证WEEK可以向上聚合到WEEK（自身）
    f.checkCanRollUp(WEEK, f.isoWeek, false); // 验证WEEK不能向上聚合到ISO周（不同的周定义）
    f.checkCanRollUp(WEEK, MONTH, false); // 验证WEEK不能向上聚合到MONTH（见注1）
    f.checkCanRollUp(WEEK, QUARTER, false); // 验证WEEK不能向上聚合到QUARTER（见注1）
    f.checkCanRollUp(WEEK, YEAR, false); // 验证WEEK不能向上聚合到YEAR（见注1）
    f.checkCanRollUp(WEEK, ISOYEAR, false); // 验证WEEK不能向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(WEEK, DECADE, false); // 验证WEEK不能向上聚合到DECADE（见注1）
    f.checkCanRollUp(WEEK, CENTURY, false); // 验证WEEK不能向上聚合到CENTURY（见注1）
    f.checkCanRollUp(WEEK, MILLENNIUM, false); // 验证WEEK不能向上聚合到MILLENNIUM（见注1）

    f.checkCanRollUp(f.isoWeek, NANOSECOND, false); // 验证ISO周不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(f.isoWeek, MICROSECOND, false); // 验证ISO周不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(f.isoWeek, MILLISECOND, false); // 验证ISO周不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(f.isoWeek, SECOND, false); // 验证ISO周不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(f.isoWeek, MINUTE, false); // 验证ISO周不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(f.isoWeek, HOUR, false); // 验证ISO周不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(f.isoWeek, DAY, false); // 验证ISO周不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(f.isoWeek, WEEK, false); // 验证ISO周不能向上聚合到WEEK（不同的周定义）
    f.checkCanRollUp(f.isoWeek, f.isoWeek, true); // 验证ISO周可以向上聚合到ISO周（自身）
    f.checkCanRollUp(f.isoWeek, MONTH, false); // 验证ISO周不能向上聚合到MONTH（见注1）
    f.checkCanRollUp(f.isoWeek, QUARTER, false); // 验证ISO周不能向上聚合到QUARTER（见注1）
    f.checkCanRollUp(f.isoWeek, YEAR, false); // 验证ISO周不能向上聚合到YEAR（见注1）
    f.checkCanRollUp(f.isoWeek, ISOYEAR, true); // 验证ISO周可以向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(f.isoWeek, DECADE, false); // 验证ISO周不能向上聚合到DECADE（见注1）
    f.checkCanRollUp(f.isoWeek, CENTURY, false); // 验证ISO周不能向上聚合到CENTURY（见注1）
    f.checkCanRollUp(f.isoWeek, MILLENNIUM, false); // 验证ISO周不能向上聚合到MILLENNIUM（见注1）

    f.checkCanRollUp(MONTH, NANOSECOND, false); // 验证MONTH不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(MONTH, MICROSECOND, false); // 验证MONTH不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(MONTH, MILLISECOND, false); // 验证MONTH不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(MONTH, SECOND, false); // 验证MONTH不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(MONTH, MINUTE, false); // 验证MONTH不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(MONTH, HOUR, false); // 验证MONTH不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(MONTH, DAY, false); // 验证MONTH不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(MONTH, WEEK, false); // 验证MONTH不能向上聚合到WEEK（粒度更细）
    f.checkCanRollUp(MONTH, f.isoWeek, false); // 验证MONTH不能向上聚合到ISO周（粒度更细）
    f.checkCanRollUp(MONTH, MONTH, true); // 验证MONTH可以向上聚合到MONTH（自身）
    f.checkCanRollUp(MONTH, QUARTER, true); // 验证MONTH可以向上聚合到QUARTER
    f.checkCanRollUp(MONTH, YEAR, true); // 验证MONTH可以向上聚合到YEAR
    f.checkCanRollUp(MONTH, ISOYEAR, false); // 验证MONTH不能向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(MONTH, DECADE, true); // 验证MONTH可以向上聚合到DECADE
    f.checkCanRollUp(MONTH, CENTURY, true); // 验证MONTH可以向上聚合到CENTURY
    f.checkCanRollUp(MONTH, MILLENNIUM, true); // 验证MONTH可以向上聚合到MILLENNIUM

    f.checkCanRollUp(QUARTER, NANOSECOND, false); // 验证QUARTER不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(QUARTER, MICROSECOND, false); // 验证QUARTER不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(QUARTER, MILLISECOND, false); // 验证QUARTER不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(QUARTER, SECOND, false); // 验证QUARTER不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(QUARTER, MINUTE, false); // 验证QUARTER不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(QUARTER, HOUR, false); // 验证QUARTER不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(QUARTER, DAY, false); // 验证QUARTER不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(QUARTER, WEEK, false); // 验证QUARTER不能向上聚合到WEEK（粒度更细）
    f.checkCanRollUp(QUARTER, f.isoWeek, false); // 验证QUARTER不能向上聚合到ISO周（粒度更细）
    f.checkCanRollUp(QUARTER, MONTH, false); // 验证QUARTER不能向上聚合到MONTH（粒度更细）
    f.checkCanRollUp(QUARTER, QUARTER, true); // 验证QUARTER可以向上聚合到QUARTER（自身）
    f.checkCanRollUp(QUARTER, YEAR, true); // 验证QUARTER可以向上聚合到YEAR
    f.checkCanRollUp(QUARTER, ISOYEAR, false); // 验证QUARTER不能向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(QUARTER, DECADE, true); // 验证QUARTER可以向上聚合到DECADE
    f.checkCanRollUp(QUARTER, CENTURY, true); // 验证QUARTER可以向上聚合到CENTURY
    f.checkCanRollUp(QUARTER, MILLENNIUM, true); // 验证QUARTER可以向上聚合到MILLENNIUM

    f.checkCanRollUp(YEAR, NANOSECOND, false); // 验证YEAR不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(YEAR, MICROSECOND, false); // 验证YEAR不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(YEAR, MILLISECOND, false); // 验证YEAR不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(YEAR, SECOND, false); // 验证YEAR不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(YEAR, MINUTE, false); // 验证YEAR不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(YEAR, HOUR, false); // 验证YEAR不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(YEAR, DAY, false); // 验证YEAR不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(YEAR, WEEK, false); // 验证YEAR不能向上聚合到WEEK（粒度更细）
    f.checkCanRollUp(YEAR, f.isoWeek, false); // 验证YEAR不能向上聚合到ISO周（粒度更细）
    f.checkCanRollUp(YEAR, MONTH, false); // 验证YEAR不能向上聚合到MONTH（粒度更细）
    f.checkCanRollUp(YEAR, QUARTER, false); // 验证YEAR不能向上聚合到QUARTER（粒度更细）
    f.checkCanRollUp(YEAR, YEAR, true); // 验证YEAR可以向上聚合到YEAR（自身）
    f.checkCanRollUp(YEAR, ISOYEAR, false); // 验证YEAR不能向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(YEAR, DECADE, true); // 验证YEAR可以向上聚合到DECADE
    f.checkCanRollUp(YEAR, CENTURY, true); // 验证YEAR可以向上聚合到CENTURY
    f.checkCanRollUp(YEAR, MILLENNIUM, true); // 验证YEAR可以向上聚合到MILLENNIUM

    // Note 3. DECADE cannot roll up to CENTURY or MILLENNIUM // 注3：DECADE不能向上聚合到CENTURY或MILLENNIUM
    // because decade starts on year 0, the others start on year 1. // 因为十年从第0年开始，而其他从第1年开始
    // For example, 2000 is start of a decade, but 2001 is start of a century // 例如，2000年是十年的开始，但2001年是世纪的开始
    // and millennium. // 和千年的开始
    f.checkCanRollUp(DECADE, NANOSECOND, false); // 验证DECADE不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(DECADE, MICROSECOND, false); // 验证DECADE不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(DECADE, MILLISECOND, false); // 验证DECADE不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(DECADE, SECOND, false); // 验证DECADE不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(DECADE, MINUTE, false); // 验证DECADE不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(DECADE, HOUR, false); // 验证DECADE不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(DECADE, DAY, false); // 验证DECADE不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(DECADE, WEEK, false); // 验证DECADE不能向上聚合到WEEK（粒度更细）
    f.checkCanRollUp(DECADE, f.isoWeek, false); // 验证DECADE不能向上聚合到ISO周（粒度更细）
    f.checkCanRollUp(DECADE, MONTH, false); // 验证DECADE不能向上聚合到MONTH（粒度更细）
    f.checkCanRollUp(DECADE, QUARTER, false); // 验证DECADE不能向上聚合到QUARTER（粒度更细）
    f.checkCanRollUp(DECADE, YEAR, false); // 验证DECADE不能向上聚合到YEAR（粒度更细）
    f.checkCanRollUp(DECADE, ISOYEAR, false); // 验证DECADE不能向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(DECADE, DECADE, true); // 验证DECADE可以向上聚合到DECADE（自身）
    f.checkCanRollUp(DECADE, CENTURY, false); // 验证DECADE不能向上聚合到CENTURY（见注3）
    f.checkCanRollUp(DECADE, MILLENNIUM, false); // 验证DECADE不能向上聚合到MILLENNIUM（见注3）

    f.checkCanRollUp(CENTURY, NANOSECOND, false); // 验证CENTURY不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(CENTURY, MICROSECOND, false); // 验证CENTURY不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(CENTURY, MILLISECOND, false); // 验证CENTURY不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(CENTURY, SECOND, false); // 验证CENTURY不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(CENTURY, MINUTE, false); // 验证CENTURY不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(CENTURY, HOUR, false); // 验证CENTURY不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(CENTURY, DAY, false); // 验证CENTURY不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(CENTURY, WEEK, false); // 验证CENTURY不能向上聚合到WEEK（粒度更细）
    f.checkCanRollUp(CENTURY, f.isoWeek, false); // 验证CENTURY不能向上聚合到ISO周（粒度更细）
    f.checkCanRollUp(CENTURY, MONTH, false); // 验证CENTURY不能向上聚合到MONTH（粒度更细）
    f.checkCanRollUp(CENTURY, QUARTER, false); // 验证CENTURY不能向上聚合到QUARTER（粒度更细）
    f.checkCanRollUp(CENTURY, YEAR, false); // 验证CENTURY不能向上聚合到YEAR（粒度更细）
    f.checkCanRollUp(CENTURY, ISOYEAR, false); // 验证CENTURY不能向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(CENTURY, DECADE, false); // 验证CENTURY不能向上聚合到DECADE（粒度更细）
    f.checkCanRollUp(CENTURY, CENTURY, true); // 验证CENTURY可以向上聚合到CENTURY（自身）
    f.checkCanRollUp(CENTURY, MILLENNIUM, true); // 验证CENTURY可以向上聚合到MILLENNIUM

    f.checkCanRollUp(MILLENNIUM, NANOSECOND, false); // 验证MILLENNIUM不能向上聚合到NANOSECOND（粒度更细）
    f.checkCanRollUp(MILLENNIUM, MICROSECOND, false); // 验证MILLENNIUM不能向上聚合到MICROSECOND（粒度更细）
    f.checkCanRollUp(MILLENNIUM, MILLISECOND, false); // 验证MILLENNIUM不能向上聚合到MILLISECOND（粒度更细）
    f.checkCanRollUp(MILLENNIUM, SECOND, false); // 验证MILLENNIUM不能向上聚合到SECOND（粒度更细）
    f.checkCanRollUp(MILLENNIUM, MINUTE, false); // 验证MILLENNIUM不能向上聚合到MINUTE（粒度更细）
    f.checkCanRollUp(MILLENNIUM, HOUR, false); // 验证MILLENNIUM不能向上聚合到HOUR（粒度更细）
    f.checkCanRollUp(MILLENNIUM, DAY, false); // 验证MILLENNIUM不能向上聚合到DAY（粒度更细）
    f.checkCanRollUp(MILLENNIUM, WEEK, false); // 验证MILLENNIUM不能向上聚合到WEEK（粒度更细）
    f.checkCanRollUp(MILLENNIUM, f.isoWeek, false); // 验证MILLENNIUM不能向上聚合到ISO周（粒度更细）
    f.checkCanRollUp(MILLENNIUM, MONTH, false); // 验证MILLENNIUM不能向上聚合到MONTH（粒度更细）
    f.checkCanRollUp(MILLENNIUM, QUARTER, false); // 验证MILLENNIUM不能向上聚合到QUARTER（粒度更细）
    f.checkCanRollUp(MILLENNIUM, YEAR, false); // 验证MILLENNIUM不能向上聚合到YEAR（粒度更细）
    f.checkCanRollUp(MILLENNIUM, ISOYEAR, false); // 验证MILLENNIUM不能向上聚合到ISOYEAR（见注2）
    f.checkCanRollUp(MILLENNIUM, DECADE, false); // 验证MILLENNIUM不能向上聚合到DECADE（粒度更细）
    f.checkCanRollUp(MILLENNIUM, CENTURY, false); // 验证MILLENNIUM不能向上聚合到CENTURY（粒度更细）
    f.checkCanRollUp(MILLENNIUM, MILLENNIUM, true); // 验证MILLENNIUM可以向上聚合到MILLENNIUM（自身）
  }

  /** Test fixture. Contains everything you need to write fluent tests. */ // 测试辅助类，包含编写流畅测试所需的一切
  static class Fixture { // 定义静态内部类Fixture，用于辅助测试
    final TimeUnit isoWeek = TimeUnit.ISODOW; // 定义isoWeek成员变量，使用ISODOW作为ISO周的代理（因为没有TimeUnit.ISOWEEK）

    final TimeFrameSet timeFrameSet = TimeFrames.CORE; // 定义timeFrameSet成员变量，引用核心时间框架集合

    private static final ImmutableMap<String, Pair<String, String>> MAP; // 定义MAP静态常量，用于存储时间框架名称到时间戳范围对的映射

    static { // 静态初始化块，用于初始化MAP
      String[] values = { // 定义字符串数组，包含时间框架名称、下限时间戳和上限时间戳
          "NANOSECOND", "2022-06-25 12:34:56.123234456", // 纳秒时间框架和对应的时间戳
          "2022-06-25 12:34:56.123234456", // 纳秒的下限和上限相同
          "MICROSECOND", "2022-06-25 12:34:56.123234", // 微秒时间框架和对应的时间戳
          "2022-06-25 12:34:56.123234", // 微秒的下限和上限相同
          "MILLISECOND", "2022-06-25 12:34:56.123", "2022-06-25 12:34:56.124", // 毫秒时间框架和对应的时间戳范围
          "SECOND", "2022-06-25 12:34:56", "2022-06-25 12:34:57", // 秒时间框架和对应的时间戳范围
          "MINUTE", "2022-06-25 12:34:00", "2022-06-25 12:35:00", // 分钟时间框架和对应的时间戳范围
          "HOUR", "2022-06-25 12:00:00", "2022-06-25 13:00:00", // 小时时间框架和对应的时间戳范围
          "DAY", "2022-06-25 00:00:00", "2022-06-26 00:00:00", // 天时间框架和对应的时间戳范围
          "WEEK", "2022-06-19 00:00:00", "2022-06-26 00:00:00", // 周时间框架和对应的时间戳范围（周日为起点）
          "ISOWEEK", "2022-06-20 00:00:00", "2022-06-27 00:00:00", // ISO周时间框架和对应的时间戳范围（周一为起点）
          "MONTH", "2022-06-01 00:00:00", "2022-07-01 00:00:00", // 月时间框架和对应的时间戳范围
          "QUARTER", "2022-04-01 00:00:00", "2022-07-01 00:00:00", // 季度时间框架和对应的时间戳范围
          "YEAR", "2022-01-01 00:00:00", "2023-01-01 00:00:00", // 年时间框架和对应的时间戳范围
          "ISOYEAR", "2022-01-03 00:00:00", "2023-01-02 00:00:00", // ISO年时间框架和对应的时间戳范围
          "DECADE", "2020-01-01 00:00:00", "2030-01-01 00:00:00", // 十年时间框架和对应的时间戳范围
          "CENTURY", "2001-01-01 00:00:00", "2101-01-01 00:00:00", // 世纪时间框架和对应的时间戳范围
          "MILLENNIUM", "2001-01-01 00:00:00", "3001-01-01 00:00:00", // 千年时间框架和对应的时间戳范围
      };
      ImmutableMap.Builder<String, Pair<String, String>> b = // 创建ImmutableMap构建器
          ImmutableMap.builder(); // 用于构建不可变Map
      for (int i = 0; i < values.length;) { // 遍历values数组
        b.put(values[i++], Pair.of(values[i++], values[i++])); // 将时间框架名称映射到时间戳范围对（下限，上限）
      }
      MAP = b.build(); // 构建不可变Map
    } // 静态初始化块结束

    private TimeFrame frame(TimeUnit unit) { // 私有方法，根据TimeUnit获取对应的TimeFrame
      if (unit == ISODOW) { // 如果传入的TimeUnit是ISODOW
        // Just for testing. We want to test f.ISOWEEK but there is no TimeUnit // 仅用于测试。我们想测试f.ISOWEEK但没有TimeUnit
        // for it, so we use ISODOW as a stand-in. // 对应它，所以我们使用ISODOW作为替代
        return timeFrameSet.get("ISOWEEK"); // 返回ISOWEEK时间框架
      } // if语句结束
      return timeFrameSet.get(unit); // 否则返回对应TimeUnit的时间框架
    } // frame方法结束

    void checkDateFloor(String in, TimeUnit unit, Matcher<String> matcher) { // 方法，检查日期向下取整功能
      int inDate = dateStringToUnixDate(in); // 将输入日期字符串转换为Unix日期（天数）
      int outDate = timeFrameSet.floorDate(inDate, frame(unit)); // 将日期向下取整到指定时间框架
      assertThat("floor(" + in + " to " + unit + ")", // 断言向下取整结果
          unixDateToString(outDate), matcher); // 将输出日期转换为字符串并与期望值匹配
    } // checkDateFloor方法结束

    void checkDateFloor(String in, String timeFrameName, Matcher<String> matcher) { // 重载方法，通过时间框架名称检查日期向下取整
      int inDate = dateStringToUnixDate(in); // 将输入日期字符串转换为Unix日期（天数）
      int outDate = timeFrameSet.floorDate(inDate, timeFrameSet.get(timeFrameName)); // 将日期向下取整到指定名称的时间框架
      assertThat("floor(" + in + " to " + timeFrameName + ")", // 断言向下取整结果
          unixDateToString(outDate), matcher); // 将输出日期转换为字符串并与期望值匹配
    } // checkDateFloor方法结束

    void checkTimestampFloor(String in, TimeUnit unit, int precision, // 方法，检查时间戳向下取整功能
        Matcher<String> matcher) { // 参数：输入时间戳、时间单位、精度、期望值匹配器
      long inTs = timestampStringToUnixDate(in); // 将输入时间戳字符串转换为Unix时间戳（毫秒）
      long outTs = timeFrameSet.floorTimestamp(inTs, frame(unit)); // 将时间戳向下取整到指定时间框架
      assertThat("floor(" + in + " to " + unit + ")", // 断言向下取整结果
          unixTimestampToString(outTs, precision), matcher); // 将输出时间戳转换为字符串（指定精度）并与期望值匹配
    } // checkTimestampFloor方法结束

    void checkTimestampCeil(String in, TimeUnit unit, int precision, // 方法，检查时间戳向上取整功能
        Matcher<String> matcher) { // 参数：输入时间戳、时间单位、精度、期望值匹配器
      long inTs = timestampStringToUnixDate(in); // 将输入时间戳字符串转换为Unix时间戳（毫秒）
      long outTs = timeFrameSet.ceilTimestamp(inTs, frame(unit)); // 将时间戳向上取整到指定时间框架
      assertThat("ceil(" + in + " to " + unit + ")", // 断言向上取整结果
          unixTimestampToString(outTs, precision), matcher); // 将输出时间戳转换为字符串（指定精度）并与期望值匹配
    } // checkTimestampCeil方法结束

    void checkCanRollUp(TimeUnit fromUnit, TimeUnit toUnit, boolean can) { // 方法，检查时间框架之间是否可以向上聚合
      TimeFrame fromFrame = frame(fromUnit); // 获取源时间框架
      TimeFrame toFrame = frame(toUnit); // 获取目标时间框架
      if (can) { // 如果期望可以向上聚合
        assertThat("can roll up " + fromUnit + " to " + toUnit, // 断言可以向上聚合
            fromFrame.canRollUpTo(toFrame), // 调用canRollUpTo方法检查是否可以向上聚合
            is(true)); // 期望结果为true

        final int precision; // 定义精度变量
        switch (toUnit) { // 根据目标时间单位确定精度
        case NANOSECOND: // 如果是纳秒
          precision = 9; // 精度为9（小数点后9位）
          break; // 跳出switch
        case MICROSECOND: // 如果是微秒
          precision = 6; // 精度为6（小数点后6位）
          break; // 跳出switch
        case MILLISECOND: // 如果是毫秒
          precision = 3; // 精度为3（小数点后3位）
          break; // 跳出switch
        default: // 其他情况
          precision = 0; // 精度为0（无小数位）
        } // switch语句结束
        if (precision <= 3) { // 如果精度小于等于3（可以测试）
          // Cannot test conversion to NANOSECOND or MICROSECOND because the // 不能测试转换为NANOSECOND或MICROSECOND，因为
          // representation is milliseconds. // 表示是毫秒
          final Pair<String, String> fromPair = // 获取源时间框架的时间戳范围对
              requireNonNull(MAP.get(fromFrame.name())); // 从MAP中获取，确保不为null
          final String timestampString = fromPair.left; // 获取时间戳字符串（下限）
          final Pair<String, String> toPair = // 获取目标时间框架的时间戳范围对
              requireNonNull(MAP.get(toFrame.name())); // 从MAP中获取，确保不为null
          final String floorTimestampString = toPair.left; // 获取向下取整的时间戳字符串（下限）
          final String ceilTimestampString = toPair.right; // 获取向上取整的时间戳字符串（上限）
          checkTimestampFloor(timestampString, toUnit, precision, // 检查时间戳向下取整
              is(floorTimestampString)); // 期望结果为floorTimestampString
          checkTimestampCeil(timestampString, toUnit, precision, // 检查时间戳向上取整
              is(ceilTimestampString)); // 期望结果为ceilTimestampString

          final String dateString = timestampString.substring(0, 10); // 从时间戳中提取日期字符串（前10个字符）
          final String floorDateString = floorTimestampString.substring(0, 10); // 从向下取整时间戳中提取日期字符串
          checkDateFloor(dateString, toUnit, is(floorDateString)); // 检查日期向下取整
        } // if语句结束

        // The 'canRollUpTo' method should be a partial order. // 'canRollUpTo'方法应该是一个偏序关系
        // A partial order is reflexive (for all x, x = x) // 偏序是自反的（对于所有x，x = x）
        // and antisymmetric (for all x, y, if x <= y and x != y, then !(y <= x)) // 并且是反对称的（对于所有x, y，如果x <= y且x != y，则!(y <= x)）
        assertThat(toFrame.canRollUpTo(fromFrame), is(fromUnit == toUnit)); // 断言反向向上聚合只有在两者相同时才为true
      } else { // 如果期望不可以向上聚合
        assertThat("can roll up " + fromUnit + " to " + toUnit, // 断言不可以向上聚合
            fromFrame.canRollUpTo(toFrame), // 调用canRollUpTo方法检查是否可以向上聚合
            is(false)); // 期望结果为false
      } // if-else语句结束
    } // checkCanRollUp方法结束
  } // Fixture类结束
} // TimeFrameTest类结束