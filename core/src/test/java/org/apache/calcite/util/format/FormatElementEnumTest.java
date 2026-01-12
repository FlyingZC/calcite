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
package org.apache.calcite.util.format; // 声明包名，该类位于org.apache.calcite.util.format包下，属于Calcite日期时间格式化工具包

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.time.Instant; // 导入Instant类，用于表示时间戳
import java.util.Date; // 导入Date类，用于表示日期时间

import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，用于断言测试结果
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest匹配器，用于验证对象的toString()方法返回值

/**
 * Unit test for {@link FormatElementEnum}. // FormatElementEnum枚举类的单元测试类
 * 该测试类用于验证FormatElementEnum枚举中各个日期时间格式化元素的正确性
 * FormatElementEnum是Calcite中用于定义日期时间格式化元素的枚举类型
 * 每个格式化元素对应不同的日期时间部分（如年、月、日、时、分、秒等）及其不同的显示格式
 * 测试覆盖了各种格式化场景，包括大小写变体、ISO周、ISO周年等特殊情况
 */
class FormatElementEnumTest { // 定义测试类，类名为FormatElementEnumTest，用于测试FormatElementEnum枚举的功能

  @Test void testCC() { // 测试方法：测试CC格式化元素（世纪），@Test注解标记为JUnit测试方法
    assertFormatElement(FormatElementEnum.CC, "2014-09-30T10:00:00Z", "21"); // 断言CC格式化元素将2014年格式化为"21"（21世纪）
  }

  @Test void testDAY() { // 测试方法：测试DAY格式化元素（星期几大写全称）
    assertFormatElement(FormatElementEnum.DAY, "2014-09-30T10:00:00Z", "TUESDAY"); // 断言DAY格式化元素将2014-09-30（星期二）格式化为"TUESDAY"（全大写）
  }
  @Test void testDay() { // 测试方法：测试Day格式化元素（星期几首字母大写全称）
    assertFormatElement(FormatElementEnum.Day, "2014-09-30T10:00:00Z", "Tuesday"); // 断言Day格式化元素将2014-09-30格式化为"Tuesday"（首字母大写）
  }
  @Test void testday() { // 测试方法：测试day格式化元素（星期几全小写全称）
    assertFormatElement(FormatElementEnum.day, "2014-09-30T10:00:00Z", "tuesday"); // 断言day格式化元素将2014-09-30格式化为"tuesday"（全小写）
  }

  @Test void testD() { // 测试方法：测试D格式化元素（月份中的第几周，1-5）
    assertFormatElement(FormatElementEnum.D, "2014-09-30T10:00:00Z", "3"); // 断言D格式化元素将2014-09-30格式化为"3"（9月的第3周）
  }

  @Test void testDD() { // 测试方法：测试DD格式化元素（月份中的日期，01-31）
    assertFormatElement(FormatElementEnum.DD, "2014-09-30T10:00:00Z", "30"); // 断言DD格式化元素将2014-09-30格式化为"30"（30日）
  }

  @Test void testDDD() { // 测试方法：测试DDD格式化元素（一年中的第几天，001-366）
    assertFormatElement(FormatElementEnum.DDD, "2014-09-30T10:00:00Z", "273"); // 断言DDD格式化元素将2014-09-30格式化为"273"（2014年的第273天）
  }

  @Test void testDY() { // 测试方法：测试DY格式化元素（星期几大写缩写）
    assertFormatElement(FormatElementEnum.DY, "2014-09-30T10:00:00Z", "TUE"); // 断言DY格式化元素将2014-09-30格式化为"TUE"（星期二的大写缩写）
  }
  @Test void testDy() { // 测试方法：测试Dy格式化元素（星期几首字母大写缩写）
    assertFormatElement(FormatElementEnum.Dy, "2014-09-30T10:00:00Z", "Tue"); // 断言Dy格式化元素将2014-09-30格式化为"Tue"（星期二的首字母大写缩写）
  }
  @Test void testdy() { // 测试方法：测试dy格式化元素（星期几全小写缩写）
    assertFormatElement(FormatElementEnum.dy, "2014-09-30T10:00:00Z", "tue"); // 断言dy格式化元素将2014-09-30格式化为"tue"（星期二的全小写缩写）
  }

  @Test void testFF1() { // 测试方法：测试FF1格式化元素（毫秒部分第1位）
    assertFormatElement(FormatElementEnum.FF1, "2014-09-30T10:00:00.123456Z", "1"); // 断言FF1格式化元素将毫秒部分123456格式化为"1"（第1位）
  }

  @Test void testFF2() { // 测试方法：测试FF2格式化元素（毫秒部分前2位）
    assertFormatElement(FormatElementEnum.FF2, "2014-09-30T10:00:00.123456Z", "12"); // 断言FF2格式化元素将毫秒部分123456格式化为"12"（前2位）
  }

  @Test void testFF3() { // 测试方法：测试FF3格式化元素（毫秒部分前3位）
    assertFormatElement(FormatElementEnum.FF3, "2014-09-30T10:00:00.123456Z", "123"); // 断言FF3格式化元素将毫秒部分123456格式化为"123"（前3位）
  }

  @Test void testFF4() { // 测试方法：测试FF4格式化元素（毫秒部分前4位，不足补0）
    assertFormatElement(FormatElementEnum.FF4, "2014-09-30T10:00:00.123456Z", "1230"); // 断言FF4格式化元素将毫秒部分123456格式化为"1230"（前4位，第4位补0）
  }

  @Test void testFF5() { // 测试方法：测试FF5格式化元素（毫秒部分前5位，不足补0）
    assertFormatElement(FormatElementEnum.FF5, "2014-09-30T10:00:00.123456Z", "12300"); // 断言FF5格式化元素将毫秒部分123456格式化为"12300"（前5位，后2位补0）
  }

  @Test void testFF6() { // 测试方法：测试FF6格式化元素（毫秒部分前6位，不足补0）
    assertFormatElement(FormatElementEnum.FF6, "2014-09-30T10:00:00.123456Z", "123000"); // 断言FF6格式化元素将毫秒部分123456格式化为"123000"（前6位，后3位补0）
  }
  @Test void testFF7() { // 测试方法：测试FF7格式化元素（毫秒部分前7位，不足补0）
    assertFormatElement(FormatElementEnum.FF7, "2014-09-30T10:00:00.123456Z", "1230000"); // 断言FF7格式化元素将毫秒部分123456格式化为"1230000"（前7位，后4位补0）
  }
  @Test void testFF8() { // 测试方法：测试FF8格式化元素（毫秒部分前8位，不足补0）
    assertFormatElement(FormatElementEnum.FF8, "2014-09-30T10:00:00.123456Z", "12300000"); // 断言FF8格式化元素将毫秒部分123456格式化为"12300000"（前8位，后5位补0）
  }
  @Test void testFF9() { // 测试方法：测试FF9格式化元素（毫秒部分前9位，不足补0）
    assertFormatElement(FormatElementEnum.FF9, "2014-09-30T10:00:00.123456Z", "123000000"); // 断言FF9格式化元素将毫秒部分123456格式化为"123000000"（前9位，后6位补0）
  }

  @Test void testID() { // 测试方法：测试ID格式化元素（ISO周中的星期几，1-7，1=周一，7=周日）
    assertFormatElement(FormatElementEnum.ID, "2014-09-30T10:00:00Z", "2"); // 断言ID格式化元素将2014-09-30格式化为"2"（ISO周中的第2天，即周二）
  }

  @Test void testIW() { // 测试方法：测试IW格式化元素（ISO周数，01-53）
    assertFormatElement(FormatElementEnum.IW, "2014-09-30T10:00:00Z", "40"); // 断言IW格式化元素将2014-09-30格式化为"40"（ISO周数40）
    // Test case for [CALCITE-6226] https://issues.apache.org/jira/browse/CALCITE-6226 // 测试用例针对CALCITE-6226问题
    // Edge case where ISO WEEK != WEEK // 边界情况：ISO周数与普通周数不一致
    assertFormatElement(FormatElementEnum.IW, "2023-01-01T10:00:00Z", "52"); // 断言2023-01-01属于ISO周52（属于2022年的最后一周）
    assertFormatElement(FormatElementEnum.IW, "2023-01-02T10:00:00Z", "01"); // 断言2023-01-02属于ISO周01（2023年的第一周）
    // Edge case where ISO WEEK != WEEK for Julian dates - motivated by [CALCITE-6252] // 儒略历日期的边界情况，由CALCITE-6252问题引发
    assertFormatElement(FormatElementEnum.IW, "0001-01-01T10:00:00Z", "01"); // 断言0001-01-01属于ISO周01
    assertFormatElement(FormatElementEnum.IW, "0005-01-01T10:00:00Z", "53"); // 断言0005-01-01属于ISO周53（0004年的最后一周）
    assertFormatElement(FormatElementEnum.IW, "0005-01-03T10:00:00Z", "01"); // 断言0005-01-03属于ISO周01（0005年的第一周）
  }

  @Test void testIYY() { // 测试方法：测试IYY格式化元素（ISO周年的后2位）
    assertFormatElement(FormatElementEnum.IYY, "2014-09-30T10:00:00Z", "14"); // 断言IYY格式化元素将2014-09-30格式化为"14"（ISO周年2014的后2位）
    // Test case for [CALCITE-6226] https://issues.apache.org/jira/browse/CALCITE-6226 // 测试用例针对CALCITE-6226问题
    // Edge case where ISO WEEK YEAR != YEAR // 边界情况：ISO周年与普通年份不一致
    assertFormatElement(FormatElementEnum.IYY, "2023-01-01T10:00:00Z", "22"); // 断言2023-01-01属于ISO周年2022，后2位为"22"
    assertFormatElement(FormatElementEnum.IYY, "2023-01-02T10:00:00Z", "23"); // 断言2023-01-02属于ISO周年2023，后2位为"23"
    // Edge case where ISO WEEK YEAR != YEAR for Julian dates - motivated by [CALCITE-6252] // 儒略历日期的边界情况，由CALCITE-6252问题引发
    assertFormatElement(FormatElementEnum.IYY, "0001-01-01T10:00:00Z", "01"); // 断言0001-01-01属于ISO周年1，后2位为"01"
    assertFormatElement(FormatElementEnum.IYY, "0005-01-01T10:00:00Z", "04"); // 断言0005-01-01属于ISO周年4，后2位为"04"
    assertFormatElement(FormatElementEnum.IYY, "0005-01-03T10:00:00Z", "05"); // 断言0005-01-03属于ISO周年5，后2位为"05"
  }

  @Test void testIYYYY() { // 测试方法：测试IYYYY格式化元素（ISO周年的4位完整年份）
    assertFormatElement(FormatElementEnum.IYYYY, "2014-09-30T10:00:00Z", "2014"); // 断言IYYYY格式化元素将2014-09-30格式化为"2014"（ISO周年2014）
    // Test case for [CALCITE-6226] https://issues.apache.org/jira/browse/CALCITE-6226 // 测试用例针对CALCITE-6226问题
    // Edge case where ISO WEEK YEAR != YEAR // 边界情况：ISO周年与普通年份不一致
    assertFormatElement(FormatElementEnum.IYYYY, "2023-01-01T10:00:00Z", "2022"); // 断言2023-01-01属于ISO周年2022
    assertFormatElement(FormatElementEnum.IYYYY, "2023-01-02T10:00:00Z", "2023"); // 断言2023-01-02属于ISO周年2023
    // Edge case where ISO WEEK YEAR != YEAR for Julian dates - motivated by [CALCITE-6252] // 儒略历日期的边界情况，由CALCITE-6252问题引发
    assertFormatElement(FormatElementEnum.IYYYY, "0001-01-01T10:00:00Z", "1"); // 断言0001-01-01属于ISO周年1
    assertFormatElement(FormatElementEnum.IYYYY, "0005-01-01T10:00:00Z", "4"); // 断言0005-01-01属于ISO周年4
    assertFormatElement(FormatElementEnum.IYYYY, "0005-01-03T10:00:00Z", "5"); // 断言0005-01-03属于ISO周年5
  }

  @Test void testMM() { // 测试方法：测试MM格式化元素（月份，01-12）
    assertFormatElement(FormatElementEnum.MM, "2014-09-30T10:00:00Z", "09"); // 断言MM格式化元素将2014-09-30格式化为"09"（9月）
  }

  @Test void testMON() { // 测试方法：测试MON格式化元素（月份大写缩写）
    assertFormatElement(FormatElementEnum.MON, "2014-09-30T10:00:00Z", "SEP"); // 断言MON格式化元素将2014-09-30格式化为"SEP"（9月的大写缩写）
  }
  @Test void testMon() { // 测试方法：测试Mon格式化元素（月份首字母大写缩写）
    assertFormatElement(FormatElementEnum.Mon, "2014-09-30T10:00:00Z", "Sep"); // 断言Mon格式化元素将2014-09-30格式化为"Sep"（9月的首字母大写缩写）
  }
  @Test void testmon() { // 测试方法：测试mon格式化元素（月份全小写缩写）
    assertFormatElement(FormatElementEnum.mon, "2014-09-30T10:00:00Z", "sep"); // 断言mon格式化元素将2014-09-30格式化为"sep"（9月的全小写缩写）
  }

  @Test void testQ() { // 测试方法：测试Q格式化元素（季度，1-4）
    assertFormatElement(FormatElementEnum.Q, "2014-09-30T10:00:00Z", "3"); // 断言Q格式化元素将2014-09-30格式化为"3"（第3季度）
  }

  @Test void testMS() { // 测试方法：测试MS格式化元素（毫秒，000-999）
    assertFormatElement(FormatElementEnum.MS, "2014-09-30T10:00:00Z", "000"); // 断言MS格式化元素将2014-09-30T10:00:00Z格式化为"000"（毫秒部分）
  }

  @Test void testSS() { // 测试方法：测试SS格式化元素（秒，00-59）
    assertFormatElement(FormatElementEnum.SS, "2014-09-30T10:00:00Z", "00"); // 断言SS格式化元素将2014-09-30T10:00:00Z格式化为"00"（0秒）
  }

  @Test void testWM() { // 测试方法：测试W格式化元素（月份中的第几周，1-5）
    assertFormatElement(FormatElementEnum.W, "2014-09-30T10:00:00Z", "5"); // 断言W格式化元素将2014-09-30格式化为"5"（9月的第5周）
  }

  @Test void testWW() { // 测试方法：测试WW格式化元素（一年中的第几周，01-53）
    assertFormatElement(FormatElementEnum.WW, "2014-09-30T10:00:00Z", "40"); // 断言WW格式化元素将2014-09-30格式化为"40"（2014年的第40周）
  }

  @Test void testYY() { // 测试方法：测试YY格式化元素（年份的后2位）
    assertFormatElement(FormatElementEnum.YY, "2014-09-30T10:00:00Z", "14"); // 断言YY格式化元素将2014-09-30格式化为"14"（2014年的后2位）
  }

  @Test void testYYYY() { // 测试方法：测试YYYY格式化元素（4位完整年份）
    assertFormatElement(FormatElementEnum.YYYY, "2014-09-30T10:00:00Z", "2014"); // 断言YYYY格式化元素将2014-09-30格式化为"2014"（2014年）
  }

  private void assertFormatElement(FormatElementEnum formatElement, String date, String expected) { // 私有辅助方法：断言格式化元素的正确性，formatElement为要测试的格式化元素，date为ISO格式的日期时间字符串，expected为期望的格式化结果
    StringBuilder ts = new StringBuilder(); // 创建StringBuilder对象用于存储格式化后的结果
    formatElement.format(ts, Date.from(Instant.parse(date))); // 调用格式化元素的format方法，将解析后的Date对象格式化到StringBuilder中，Instant.parse(date)将ISO字符串解析为Instant，Date.from()转换为Date对象
    assertThat(ts, hasToString(expected)); // 使用Hamcrest断言验证StringBuilder的toString()结果等于期望值
  }
}
