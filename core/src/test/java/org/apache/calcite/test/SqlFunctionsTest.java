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
package org.apache.calcite.test; // Apache Calcite测试包,包含所有测试类
import org.apache.calcite.avatica.util.ByteString; // Avatica的字节字符串工具类,用于二进制数据处理
import org.apache.calcite.avatica.util.DateTimeUtils; // 日期时间工具类,提供日期转换和计算功能
import org.apache.calcite.runtime.CalciteException; // Calcite运行时异常类
import org.apache.calcite.runtime.SqlFunctions; // SQL函数实现类,包含各种内置SQL函数的实现
import org.apache.calcite.runtime.Utilities; // 通用工具类,提供辅助方法

import com.google.common.collect.ImmutableList; // Google Guava的不可变列表实现

import org.hamcrest.Matcher; // Hamcrest匹配器接口,用于断言
import org.junit.jupiter.api.Test; // JUnit 5测试注解,标记测试方法

import java.math.BigDecimal; // Java大数类,用于精确的十进制计算
import java.sql.Time; // SQL时间类型,表示时分秒
import java.sql.Timestamp; // SQL时间戳类型,表示日期时间
import java.util.ArrayList; // Java动态数组实现
import java.util.Arrays; // Java数组工具类
import java.util.Calendar; // Java日历类,用于日期时间操作
import java.util.Collections; // Java集合工具类
import java.util.List; // Java列表接口
import java.util.Locale; // Java本地化类,用于地区相关的格式化
import java.util.TimeZone; // Java时区类

import static org.apache.calcite.avatica.util.DateTimeUtils.MILLIS_PER_DAY; // 一天的毫秒数常量
import static org.apache.calcite.avatica.util.DateTimeUtils.dateStringToUnixDate; // 日期字符串转Unix日期的静态方法
import static org.apache.calcite.avatica.util.DateTimeUtils.timeStringToUnixDate; // 时间字符串转Unix时间的静态方法
import static org.apache.calcite.avatica.util.DateTimeUtils.timestampStringToUnixDate; // 时间戳字符串转Unix时间戳的静态方法
import static org.apache.calcite.runtime.SqlFunctions.arraysOverlap; // 数组重叠检测函数
import static org.apache.calcite.runtime.SqlFunctions.charLength; // 字符串长度函数
import static org.apache.calcite.runtime.SqlFunctions.concat; // 字符串连接函数(两参数版本)
import static org.apache.calcite.runtime.SqlFunctions.concatMulti; // 多参数字符串连接函数
import static org.apache.calcite.runtime.SqlFunctions.concatMultiObjectWithSeparator; // 多对象带分隔符连接函数
import static org.apache.calcite.runtime.SqlFunctions.concatMultiTypeWithSeparator; // 多类型带分隔符连接函数
import static org.apache.calcite.runtime.SqlFunctions.concatMultiWithNull; // 多参数连接函数(支持null处理)
import static org.apache.calcite.runtime.SqlFunctions.concatMultiWithSeparator; // 多参数带分隔符连接函数
import static org.apache.calcite.runtime.SqlFunctions.concatWithNull; // 字符串连接函数(支持null处理)
import static org.apache.calcite.runtime.SqlFunctions.convertOracle; // Oracle字符集转换函数
import static org.apache.calcite.runtime.SqlFunctions.fromBase64; // Base64解码函数
import static org.apache.calcite.runtime.SqlFunctions.greater; // 取较大值函数
import static org.apache.calcite.runtime.SqlFunctions.initcap; // 首字母大写函数
import static org.apache.calcite.runtime.SqlFunctions.internalToDate; // Unix日期转SQL日期函数
import static org.apache.calcite.runtime.SqlFunctions.internalToTime; // Unix时间转SQL时间函数
import static org.apache.calcite.runtime.SqlFunctions.internalToTimestamp; // Unix时间戳转SQL时间戳函数
import static org.apache.calcite.runtime.SqlFunctions.lesser; // 取较小值函数
import static org.apache.calcite.runtime.SqlFunctions.lower; // 转小写函数
import static org.apache.calcite.runtime.SqlFunctions.ltrim; // 左侧去空格函数
import static org.apache.calcite.runtime.SqlFunctions.md5; // MD5哈希函数
import static org.apache.calcite.runtime.SqlFunctions.overlay; // 字符串覆盖函数
import static org.apache.calcite.runtime.SqlFunctions.position; // 位置查找函数
import static org.apache.calcite.runtime.SqlFunctions.replace; // 字符串替换函数
import static org.apache.calcite.runtime.SqlFunctions.rtrim; // 右侧去空格函数
import static org.apache.calcite.runtime.SqlFunctions.sha1; // SHA-1哈希函数
import static org.apache.calcite.runtime.SqlFunctions.sha256; // SHA-256哈希函数
import static org.apache.calcite.runtime.SqlFunctions.sha512; // SHA-512哈希函数
import static org.apache.calcite.runtime.SqlFunctions.substring; // 子字符串提取函数
import static org.apache.calcite.runtime.SqlFunctions.toBase64; // Base64编码函数
import static org.apache.calcite.runtime.SqlFunctions.toInt; // 转整数函数
import static org.apache.calcite.runtime.SqlFunctions.toIntOptional; // 转整数函数(可选空值)
import static org.apache.calcite.runtime.SqlFunctions.toLong; // 转长整数函数
import static org.apache.calcite.runtime.SqlFunctions.toLongOptional; // 转长整数函数(可选空值)
import static org.apache.calcite.runtime.SqlFunctions.toTimestampWithLocalTimeZone; // 转本地时区时间戳函数
import static org.apache.calcite.runtime.SqlFunctions.trim; // 去除首尾空格函数
import static org.apache.calcite.runtime.SqlFunctions.upper; // 转大写函数
import static org.apache.calcite.test.Matchers.isListOf; // 测试匹配器,验证是否为列表

import static org.hamcrest.CoreMatchers.containsString; // Hamcrest匹配器,验证包含子字符串
import static org.hamcrest.CoreMatchers.equalTo; // Hamcrest匹配器,验证相等
import static org.hamcrest.CoreMatchers.is; // Hamcrest匹配器,验证相等
import static org.hamcrest.CoreMatchers.nullValue; // Hamcrest匹配器,验证为null
import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest断言方法
import static org.hamcrest.Matchers.closeTo; // Hamcrest匹配器,验证近似相等
import static org.hamcrest.Matchers.hasToString; // Hamcrest匹配器,验证toString输出
import static org.junit.jupiter.api.Assertions.assertSame; // JUnit断言方法,验证对象相同
import static org.junit.jupiter.api.Assertions.fail; // JUnit断言方法,标记测试失败

import static java.nio.charset.StandardCharsets.UTF_8; // UTF-8字符集常量

/**
 * SqlFunctionsTest类 - SqlFunctions类的单元测试类
 * 
 * 该类用于测试{@link SqlFunctions}中实现SQL函数的方法
 * SqlFunctions类包含了Calcite框架中各种内置SQL函数的实现
 * 
 * 测试范围包括:
 * - 字符串函数: concat, substring, replace, trim, upper, lower等
 * - 数学函数: floor, ceil, round, truncate等
 * - 日期时间函数: 日期转换,时间戳处理等
 * - 正则表达式函数: regexpContains, regexpExtract, regexpReplace等
 * - 哈希函数: md5, sha1, sha256, sha512等
 * - 数组函数: arraysOverlap, split等
 * - 比较函数: eqAny, neAny, ltAny, leAny, gtAny, geAny等
 * - 算术函数: plusAny, minusAny, multiplyAny, divideAny等
 * - 集合函数: multisetUnion, multisetIntersect, multisetExcept等
 * 
 * <p>开发者请注意: 请使用{@link org.hamcrest.MatcherAssert#assertThat assertThat}
 * 而不是{@code assertEquals},因为Hamcrest匹配器提供更丰富的断言能力
 */
class SqlFunctionsTest {
  // 静态泛型辅助方法: 创建包含指定元素的列表
  // 参数es: 可变参数,包含要添加到列表中的元素
  // 返回值: 包含所有指定元素的List对象
  static <E> List<E> list(E... es) {
    return Arrays.asList(es); // 使用Arrays工具类将可变参数转换为不可变列表
  }

  // 静态泛型辅助方法: 创建空列表
  // 返回值: 空的ImmutableList对象
  static <E> List<E> list() {
    return ImmutableList.of(); // 使用Guava的ImmutableList创建空列表
  }

  // 测试方法: 测试arraysOverlap函数(检测两个数组是否有重叠元素)
  // arraysOverlap函数用于判断两个数组是否包含相同的非null元素
  // 返回值: true表示有重叠, false表示无重叠, null表示无法确定(因为包含null)
  @Test void testArraysOverlap() {
    final List<Object> listWithOnlyNull = new ArrayList<>(); // 创建只包含null的列表
    listWithOnlyNull.add(null); // 添加null元素

    // 测试场景: list2为空列表
    assertThat(arraysOverlap(list(), list()), is(false)); // 两个空列表,无重叠
    assertThat(arraysOverlap(listWithOnlyNull, list()), is(false)); // 只含null的列表与空列表,无重叠
    assertThat(arraysOverlap(list(1, null), list()), is(false)); // 含1和null的列表与空列表,无重叠
    assertThat(arraysOverlap(list(1, 2), list()), is(false)); // 含1和2的列表与空列表,无重叠

    // 测试场景: list2只包含null元素
    assertThat(arraysOverlap(list(), listWithOnlyNull), is(false)); // 空列表与只含null的列表,无重叠
    assertThat(arraysOverlap(listWithOnlyNull, listWithOnlyNull), is(nullValue())); // 两个都只含null的列表,返回null(无法确定)
    assertThat(arraysOverlap(list(1, null), listWithOnlyNull), is(nullValue())); // 含1和null的列表与只含null的列表,返回null
    assertThat(arraysOverlap(list(1, 2), listWithOnlyNull), is(nullValue())); // 含1和2的列表与只含null的列表,返回null

    // 测试场景: list2包含null和非null元素的混合
    assertThat(arraysOverlap(list(), list(1, null)), is(false)); // 空列表与含1和null的列表,无重叠
    assertThat(arraysOverlap(listWithOnlyNull, list(1, null)), is(nullValue())); // 只含null的列表与含1和null的列表,返回null
    assertThat(arraysOverlap(list(1, null), list(1, null)), is(true)); // 两个都含1和null的列表,有重叠(元素1)
    assertThat(arraysOverlap(list(1, 2), list(1, null)), is(true)); // 含1和2的列表与含1和null的列表,有重叠(元素1)

    // 测试场景: list2只包含非null元素
    assertThat(arraysOverlap(list(), list(1, 2)), is(false)); // 空列表与含1和2的列表,无重叠
    assertThat(arraysOverlap(listWithOnlyNull, list(1, 2)), is(nullValue())); // 只含null的列表与含1和2的列表,返回null
    assertThat(arraysOverlap(list(1, null), list(1, 2)), is(true)); // 含1和null的列表与含1和2的列表,有重叠(元素1)
    assertThat(arraysOverlap(list(1, 2), list(1, 2)), is(true)); // 两个都含1和2的列表,有重叠(元素1和2)
  }

  // 测试方法: 测试charLength函数(计算字符串长度)
  // charLength函数返回字符串中字符的数量
  @Test void testCharLength() {
    assertThat(charLength("xyz"), is(3)); // 测试字符串"xyz"的长度为3
  }

  // 测试方法: 测试toString函数(将数值转换为字符串)
  // 该函数将float、double和BigDecimal类型转换为字符串表示
  // 注意: 转换格式遵循科学计数法规则,保留适当的小数位数
  @Test void testToString() {
    // 测试float类型转换
    assertThat(SqlFunctions.toString(0f), is("0E0")); // 0.0f转换为"0E0"(科学计数法)
    assertThat(SqlFunctions.toString(1f), is("1.0")); // 1.0f转换为"1.0"
    assertThat(SqlFunctions.toString(1.5f), is("1.5")); // 1.5f转换为"1.5"
    assertThat(SqlFunctions.toString(-1.5f), is("-1.5")); // -1.5f转换为"-1.5"
    assertThat(SqlFunctions.toString(1.5e8f), is("1.5E8")); // 150000000.0f转换为"1.5E8"
    assertThat(SqlFunctions.toString(-0.0625f), is("-0.0625")); // -0.0625f转换为"-0.0625"
    assertThat(SqlFunctions.toString(0.0625f), is("0.0625")); // 0.0625f转换为"0.0625"
    assertThat(SqlFunctions.toString(-5e-12f), is("-5.0E-12")); // -0.000000000005f转换为"-5.0E-12"

    // 测试double类型转换
    assertThat(SqlFunctions.toString(0d), is("0E0")); // 0.0d转换为"0E0"
    assertThat(SqlFunctions.toString(1d), is("1.0")); // 1.0d转换为"1.0"
    assertThat(SqlFunctions.toString(1.5d), is("1.5")); // 1.5d转换为"1.5"
    assertThat(SqlFunctions.toString(-1.5d), is("-1.5")); // -1.5d转换为"-1.5"
    assertThat(SqlFunctions.toString(1.5e8d), is("1.5E8")); // 150000000.0d转换为"1.5E8"
    assertThat(SqlFunctions.toString(-0.0625d), is("-0.0625")); // -0.0625d转换为"-0.0625"
    assertThat(SqlFunctions.toString(0.0625d), is("0.0625")); // 0.0625d转换为"0.0625"
    assertThat(SqlFunctions.toString(-5e-12d), is("-5.0E-12")); // -0.000000000005d转换为"-5.0E-12"

    // 测试BigDecimal类型转换
    assertThat(SqlFunctions.toString(new BigDecimal("0")), is("0")); // BigDecimal(0)转换为"0"
    assertThat(SqlFunctions.toString(new BigDecimal("1")), is("1")); // BigDecimal(1)转换为"1"
    assertThat(SqlFunctions.toString(new BigDecimal("1.5")), is("1.5")); // BigDecimal(1.5)转换为"1.5"
    assertThat(SqlFunctions.toString(new BigDecimal("-1.5")), is("-1.5")); // BigDecimal(-1.5)转换为"-1.5"
    assertThat(SqlFunctions.toString(new BigDecimal("1.5e8")), is("1.5E+8")); // BigDecimal(150000000)转换为"1.5E+8"
    assertThat(SqlFunctions.toString(new BigDecimal("-0.0625")), is("-.0625")); // BigDecimal(-0.0625)转换为"-.0625"(省略前导0)
    assertThat(SqlFunctions.toString(new BigDecimal("0.0625")), is(".0625")); // BigDecimal(0.0625)转换为".0625"(省略前导0)
    assertThat(SqlFunctions.toString(new BigDecimal("-5e-12")), is("-5E-12")); // BigDecimal(-0.000000000005)转换为"-5E-12"
  }

  // 测试方法: 测试concat函数(连接两个字符串)
  // concat函数将两个字符串连接成一个字符串
  // 注意: 代码生成器会确保不会传入null值
  // 如果传入null,会被当作字符串"null"处理(这不是SQL期望的行为)
  @Test void testConcat() {
    assertThat(concat("a b", "cd"), is("a bcd")); // 连接"a b"和"cd",得到"a bcd"
    // 代码生成器会确保不会传入null值。如果传入null,
    // 它会被当作字符串"null"处理,如下面的测试所示
    // 这不是SQL期望的行为
    assertThat(concat("a", null), is("anull")); // 连接"a"和null,得到"anull"(null被当作字符串"null")
    assertThat(concat((String) null, null), is("nullnull")); // 连接两个null,得到"nullnull"
    assertThat(concat(null, "b"), is("nullb")); // 连接null和"b",得到"nullb"
  }

  /** 测试用例: 测试substring函数(提取子字符串)
   * 该测试针对JIRA问题
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6433">[CALCITE-6433]
   * SUBSTRING can return incorrect empty result for some parameters</a>
   * 该问题修复了substring函数在某些参数下返回错误空结果的问题
   * 
   * substring函数支持两种形式:
   * 1. substring(str, start): 从start位置开始提取到字符串末尾
   * 2. substring(str, start, length): 从start位置开始提取指定长度的子字符串
   * 
   * 注意: 位置索引从1开始(不是从0开始)
   * 负数位置会被当作1处理
   * 超出字符串长度的位置会返回空字符串
   */
  @Test void testSubString() {
    // 测试单参数形式: substring(str, start)
    assertThat(substring("string", -1), is("string")); // 负数位置-1当作1,返回整个字符串
    assertThat(substring("string", -1L), is("string")); // 负数位置-1L当作1,返回整个字符串
    assertThat(substring("string", 2), is("tring")); // 从位置2开始,返回"tring"
    assertThat(substring("string", 2L), is("tring")); // 从位置2L开始,返回"tring"
    assertThat(substring("string", Integer.MIN_VALUE), is("string")); // 最小整数当作1,返回整个字符串
    assertThat(substring("string", Long.MIN_VALUE), is("string")); // 最小长整数当作1,返回整个字符串
    assertThat(substring("string", Integer.MIN_VALUE + 10), is("string")); // 最小整数+10当作1,返回整个字符串
    assertThat(substring("string", Integer.MAX_VALUE), is("")); // 超出字符串长度,返回空字符串
    assertThat(substring("string", Long.MAX_VALUE), is("")); // 超出字符串长度,返回空字符串
    assertThat(substring("string", Integer.MAX_VALUE - 10), is("")); // 超出字符串长度,返回空字符串
    assertThat(substring("string", Integer.MIN_VALUE - 10L), is("string")); // 负数当作1,返回整个字符串
    assertThat(substring("string", Integer.MAX_VALUE + 10L), is("")); // 超出字符串长度,返回空字符串

    // 测试多参数形式: substring(str, start, length)
    assertThat(substring("string", -1, 1), is("")); // 负数位置-1当作1,长度1,但起始位置+长度超出字符串,返回空字符串
    assertThat(substring("string", -1, 1L), is("")); // 负数位置-1当作1,长度1L,返回空字符串
    assertThat(substring("string", -1L, 1), is("")); // 负数位置-1L当作1,长度1,返回空字符串
    assertThat(substring("string", -1L, 1L), is("")); // 负数位置-1L当作1,长度1L,返回空字符串

    assertThat(substring("string", 1, 2), is("st")); // 从位置1开始,长度2,返回"st"
    assertThat(substring("string", 1, 2L), is("st")); // 从位置1开始,长度2L,返回"st"
    assertThat(substring("string", 1L, 2), is("st")); // 从位置1L开始,长度2,返回"st"
    assertThat(substring("string", 1L, 2L), is("st")); // 从位置1L开始,长度2L,返回"st"

    assertThat(substring("string", -1, 2), is("")); // 负数位置-1当作1,长度2,但起始位置+长度超出字符串,返回空字符串
    assertThat(substring("string", -1L, 2), is("")); // 负数位置-1L当作1,长度2,返回空字符串
    assertThat(substring("string", -1, 2L), is("")); // 负数位置-1当作1,长度2L,返回空字符串
    assertThat(substring("string", -1L, 2L), is("")); // 负数位置-1L当作1,长度2L,返回空字符串

    assertThat(substring("string", -1, 3), is("s")); // 负数位置-1当作1,长度3,返回"s"
    assertThat(substring("string", -1L, 3), is("s")); // 负数位置-1L当作1,长度3,返回"s"
    assertThat(substring("string", -1, 3L), is("s")); // 负数位置-1当作1,长度3L,返回"s"
    assertThat(substring("string", -1L, 3L), is("s")); // 负数位置-1L当作1,长度3L,返回"s"

    assertThat(substring("string", -10, 12), is("s")); // 负数位置-10当作1,长度12,但字符串只有6个字符,返回"s"
    assertThat(substring("string", -10L, 12), is("s")); // 负数位置-10L当作1,长度12,返回"s"
    assertThat(substring("string", -10, 12L), is("s")); // 负数位置-10当作1,长度12L,返回"s"
    assertThat(substring("string", -10L, 12L), is("s")); // 负数位置-10L当作1,长度12L,返回"s"

    assertThat(substring("string", -1, Integer.MAX_VALUE), is("string")); // 负数位置-1当作1,超大长度,返回整个字符串
    assertThat(substring("string", -1L, Integer.MAX_VALUE), is("string")); // 负数位置-1L当作1,超大长度,返回整个字符串
    assertThat(substring("string", -1, Long.MAX_VALUE), is("string")); // 负数位置-1当作1,超大长度,返回整个字符串

    assertThat(substring("string", Integer.MIN_VALUE, Integer.MAX_VALUE), is("")); // 负数位置当作1,超大长度,但计算后超出范围,返回空字符串
    assertThat(substring("string", Integer.MIN_VALUE, Integer.MAX_VALUE + 10L), is("string")); // 负数位置当作1,超大长度,返回整个字符串
    assertThat(substring("string", Long.MIN_VALUE, Integer.MAX_VALUE), is("")); // 负数位置当作1,超大长度,返回空字符串
    assertThat(substring("string", Integer.MIN_VALUE, Long.MAX_VALUE), is("string")); // 负数位置当作1,超大长度,返回整个字符串
    assertThat(substring("string", Integer.MIN_VALUE - 10L, Long.MAX_VALUE), is("string")); // 负数位置当作1,超大长度,返回整个字符串
  }

  // 测试方法: 测试concatWithNull函数(连接两个字符串,支持null值处理)
  // concatWithNull函数与concat的区别在于: 
  // - 如果传入一个null值,会被当作空字符串处理
  // - 如果两个值都为null,则返回null
  // 这是更符合SQL语义的字符串连接行为
  @Test void testConcatWithNull() {
    assertThat(concatWithNull("a b", "cd"), is("a bcd")); // 连接"a b"和"cd",得到"a bcd"
    // 可以传入null值。如果传入一个null值,会被当作空字符串处理
    // 如果两个值都为null,则返回null
    // 如下面的测试所示
    assertThat(concatWithNull("a", null), is("a")); // 连接"a"和null,null被当作空字符串,得到"a"
    assertThat(concatWithNull(null, null), is(nullValue())); // 连接两个null,返回null
    assertThat(concatWithNull(null, "b"), is("b")); // 连接null和"b",null被当作空字符串,得到"b"
  }

  // 测试方法: 测试concatMulti函数(连接多个字符串)
  // concatMulti函数接受可变参数,将多个字符串连接成一个字符串
  // 注意: 代码生成器会确保不会传入null值
  // 如果传入null,会被当作字符串"null"处理(这不是SQL期望的行为)
  @Test void testConcatMulti() {
    assertThat(concatMulti("a b", "cd", "e"), is("a bcde")); // 连接"a b"、"cd"和"e",得到"a bcde"
    // 代码生成器会确保不会传入null值。如果传入null,
    // 它会被当作字符串"null"处理,如下面的测试所示
    // 这不是SQL期望的行为
    assertThat(concatMulti((String) null), is("null")); // 连接单个null,得到"null"
    assertThat(concatMulti((String) null, null), is("nullnull")); // 连接两个null,得到"nullnull"
    assertThat(concatMulti("a", null, "b"), is("anullb")); // 连接"a"、null和"b",得到"anullb"
  }

  // 测试方法: 测试concatMultiWithNull函数(连接多个字符串,支持null值处理)
  // concatMultiWithNull函数接受可变参数,将多个字符串连接成一个字符串
  // null值会被当作空字符串处理
  // 这是更符合SQL语义的多字符串连接行为
  @Test void testConcatMultiWithNull() {
    assertThat(concatMultiWithNull("a b", "cd", "e"), is("a bcde")); // 连接"a b"、"cd"和"e",得到"a bcde"
    // 可以传入null值,null会被当作空字符串处理
    assertThat(concatMultiWithNull((String) null), is("")); // 连接单个null,得到空字符串
    assertThat(concatMultiWithNull((String) null, ""), is("")); // 连接null和空字符串,得到空字符串
    assertThat(concatMultiWithNull((String) null, null, null), is("")); // 连接三个null,得到空字符串
    assertThat(concatMultiWithNull("a", null, "b"), is("ab")); // 连接"a"、null和"b",null被当作空字符串,得到"ab"
  }

  // 测试方法: 测试concatMultiWithSeparator函数(使用分隔符连接多个字符串)
  // concatMultiWithSeparator函数接受一个分隔符和多个字符串,用分隔符连接它们
  // null值会被当作空字符串处理
  // 空字符串也会被连接,导致分隔符出现
  @Test void testConcatMultiWithSeparator() {
    assertThat(concatMultiWithSeparator(",", "a"), is("a")); // 用","连接单个"a",得到"a"(不需要分隔符)
    assertThat(concatMultiWithSeparator(",", "a b", "cd"), is("a b,cd")); // 用","连接"a b"和"cd",得到"a b,cd"
    assertThat(concatMultiWithSeparator(",", "a b", null, "cd", null, "e"), is("a b,cd,e")); // 连接时跳过null,得到"a b,cd,e"
    assertThat(concatMultiWithSeparator(",", null, null), is("")); // 连接两个null,得到空字符串
    assertThat(concatMultiWithSeparator(",", "", ""), is(",")); // 连接两个空字符串,得到","
    assertThat(concatMultiWithSeparator("", "a", "b", null, "c"), is("abc")); // 用空字符串分隔,得到"abc"
    assertThat(concatMultiWithSeparator("", null, null), is("")); // 用空字符串分隔连接两个null,得到空字符串
    // 分隔符可以是null,会被当作空字符串处理
    assertThat(concatMultiWithSeparator(null, "a", "b", null, "c"), is("abc")); // null分隔符当作空字符串,得到"abc"
    assertThat(concatMultiWithSeparator(null, null, null), is("")); // null分隔符连接两个null,得到空字符串
  }

  /** 测试用例: 测试concatMultiTypeWithSeparator函数(使用分隔符连接多种类型)
   * 该测试针对JIRA问题
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6446">[CALCITE-6446]
   * Add CONCAT_WS function (enabled in Spark library)</a>
   * 
   * concatMultiTypeWithSeparator函数支持:
   * - 字符串类型: 直接连接
   * - 数组类型: 展开数组元素后连接
   * - 混合类型: 同时支持字符串和数组
   * 
   * null值会被跳过,空字符串会被保留
   * 第一个参数是分隔符,后续参数是要连接的值
   */
  @Test void testConcatMultiTypeWithSeparator() {
    // 测试字符串类型
    assertThat(concatMultiTypeWithSeparator("a"), is("")); // 只有分隔符,没有要连接的值,返回空字符串
    assertThat(concatMultiTypeWithSeparator(",", "a"), is("a")); // 用","连接单个"a",得到"a"
    assertThat(concatMultiTypeWithSeparator(",", "a b", "cd"), is("a b,cd")); // 用","连接"a b"和"cd",得到"a b,cd"
    assertThat(concatMultiTypeWithSeparator(",", "a b", null, "cd", null, "e"), is("a b,cd,e")); // 连接时跳过null,得到"a b,cd,e"
    assertThat(concatMultiTypeWithSeparator(",", "", ""), is(",")); // 连接两个空字符串,得到","
    assertThat(concatMultiTypeWithSeparator("", null, null), is("")); // 用空字符串分隔连接两个null,得到空字符串
    
    // 测试数组类型
    assertThat(concatMultiTypeWithSeparator(",", Arrays.asList()), is("")); // 连接空数组,得到空字符串
    assertThat(concatMultiTypeWithSeparator(",", Arrays.asList("a")), is("a")); // 连接["a"],得到"a"
    assertThat(concatMultiTypeWithSeparator(",", Arrays.asList("a", "b")), is("a,b")); // 连接["a","b"],得到"a,b"
    assertThat(concatMultiTypeWithSeparator(",", Arrays.asList("a", null, "b")), is("a,b")); // 连接["a",null,"b"],跳过null,得到"a,b"
    assertThat(concatMultiTypeWithSeparator(",", Arrays.asList(null, "b")), is("b")); // 连接[null,"b"],跳过null,得到"b"
    assertThat(concatMultiTypeWithSeparator(",", Arrays.asList(null, null)), is("")); // 连接[null,null],跳过所有null,得到空字符串
    assertThat(
        concatMultiTypeWithSeparator(",",
            Arrays.asList("11", "11"), Arrays.asList("12", "12")), is("11,11,12,12")); // 连接两个数组["11","11"]和["12","12"],得到"11,11,12,12"
    
    // 测试混合类型(字符串和数组)
    assertThat(concatMultiTypeWithSeparator(",", "11", "11", Arrays.asList("12", "12")),
        is("11,11,12,12")); // 连接字符串"11"、"11"和数组["12","12"],得到"11,11,12,12"
    assertThat(concatMultiTypeWithSeparator(",", null, "11", Arrays.asList("12", "12")),
        is("11,12,12")); // 连接null、"11"和数组["12","12"],跳过null,得到"11,12,12"
    assertThat(concatMultiTypeWithSeparator(",", "11", null, Arrays.asList("12", "12")),
        is("11,12,12")); // 连接"11"、null和数组["12","12"],跳过null,得到"11,12,12"
    assertThat(
        concatMultiTypeWithSeparator(",", "11", "11", Arrays.asList("12", "12"),
            Arrays.asList("13", null, "13")),
        is("11,11,12,12,13,13")); // 连接多个值,跳过数组中的null,得到"11,11,12,12,13,13"
  }

  /** 测试用例: 测试concatMultiObjectWithSeparator函数(使用分隔符连接多个对象)
   * 该测试针对JIRA问题
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6450">[CALCITE-6450]
   * Postgres CONCAT_WS function </a>
   * 
   * concatMultiObjectWithSeparator函数支持将不同类型的对象(字符串、数字、数组等)用分隔符连接
   * null值会被跳过,对象会调用toString()方法转换为字符串
   * 第一个参数是分隔符,后续参数是要连接的对象
   */
  @Test void testConcatMultiObjectWithSeparator() {
    assertThat(concatMultiObjectWithSeparator("a"), is("")); // 只有分隔符,没有要连接的值,返回空字符串
    assertThat(concatMultiObjectWithSeparator(",", "a b", "cd"), is("a b,cd")); // 用","连接"a b"和"cd",得到"a b,cd"
    assertThat(concatMultiObjectWithSeparator(",", "a", 1, Arrays.asList("b", "c")), // 连接字符串"a"、整数1和列表["b","c"]
        is("a,1,[b, c]")); // 列表会转换为字符串表示"[b, c]",得到"a,1,[b, c]"
    assertThat(concatMultiObjectWithSeparator(",", "a", 1, Arrays.asList("b", "c"), null), // 连接多个对象,包括null
        is("a,1,[b, c]")); // null被跳过,得到"a,1,[b, c]"
    assertThat(concatMultiObjectWithSeparator("abc", null, null), is("")); // 连接两个null,返回空字符串
  }

  // 测试方法: 测试convertOracle函数(Oracle字符集转换)
  // 该函数用于在不同字符集之间转换字符串
  // 主要兼容Oracle数据库的字符集转换功能
  @Test void testConvertOracle() {
    assertThat(convertOracle("a", "UTF8", "LATIN1"), is("a")); // 将"a"从UTF8转换为LATIN1,得到"a"(字符在两种编码中都相同)
    assertThat(convertOracle("a", "UTF8"), is("a")); // 将"a"从UTF8转换(不指定目标编码),得到"a"
  }

  // 测试方法: 测试PosixRegexFunction类(POSIX正则表达式函数)
  // 该类提供POSIX风格的正则表达式匹配功能
  // 包括大小写敏感和不敏感的匹配模式
  @Test void testPosixRegex() {
    final SqlFunctions.PosixRegexFunction f = // 创建POSIX正则表达式函数实例
        new SqlFunctions.PosixRegexFunction();
    // 测试大小写敏感的匹配
    assertThat(f.posixRegexSensitive("abc", "abc"), is(true)); // "abc"匹配"abc",返回true
    assertThat(f.posixRegexSensitive("abc", "^a"), is(true)); // "abc"以"a"开头,返回true
    assertThat(f.posixRegexSensitive("abc", "(b|d)"), is(true)); // "abc"包含"b"或"d",返回true
    assertThat(f.posixRegexSensitive("abc", "^(b|c)"), is(false)); // "abc"不以"b"或"c"开头,返回false

    // 测试大小写不敏感的匹配
    assertThat(f.posixRegexInsensitive("abc", "ABC"), is(true)); // "abc"匹配"ABC"(不区分大小写),返回true
    assertThat(f.posixRegexInsensitive("abc", "^A"), is(true)); // "abc"以"A"开头(不区分大小写),返回true
    assertThat(f.posixRegexInsensitive("abc", "(B|D)"), is(true)); // "abc"包含"B"或"D"(不区分大小写),返回true
    assertThat(f.posixRegexInsensitive("abc", "^(B|C)"), is(false)); // "abc"不以"B"或"C"开头(不区分大小写),返回false

    // 测试POSIX字符类
    assertThat(f.posixRegexInsensitive("abc", "^[[:xdigit:]]$"), is(false)); // "abc"不是单个十六进制字符,返回false
    assertThat(f.posixRegexInsensitive("abc", "^[[:xdigit:]]+$"), is(true)); // "abc"全是十六进制字符,返回true
    assertThat(f.posixRegexInsensitive("abcq", "^[[:xdigit:]]+$"), is(false)); // "abcq"包含非十六进制字符'q',返回false

    // 测试包含匹配
    assertThat(f.posixRegexInsensitive("abc", "[[:xdigit:]]"), is(true)); // "abc"包含十六进制字符,返回true
    assertThat(f.posixRegexInsensitive("abc", "[[:xdigit:]]+"), is(true)); // "abc"包含多个十六进制字符,返回true
    assertThat(f.posixRegexInsensitive("abcq", "[[:xdigit:]]"), is(true)); // "abcq"包含十六进制字符,返回true
  }

  // 测试方法: 测试regexpContains函数(正则表达式包含检测)
  // 该函数检测字符串是否包含匹配正则表达式的子串
  // 支持正则表达式缓存以提高性能
  // 测试方法: 测试regexpContains函数(正则表达式包含检测)
  // 该函数检测字符串是否包含匹配正则表达式的子串
  // 支持正则表达式缓存以提高性能
  // 测试无效正则表达式的错误处理
  @Test void testRegexpContains() {
    final SqlFunctions.RegexFunction f = new SqlFunctions.RegexFunction(); // 创建正则表达式函数实例

    // 使用相同的正则表达式;应该命中缓存
    assertThat(f.regexpContains("abcdef", "abz*"), is(true)); // "abcdef"包含"ab"后跟零个或多个"z",返回true
    assertThat(f.regexpContains("zabzz", "abz*"), is(true)); // "zabzz"包含"ab"后跟零个或多个"z",返回true
    assertThat(f.regexpContains("zazbbzz", "abz*"), is(false)); // "zazbbzz"不包含"ab"后跟零个或多个"z",返回false
    assertThat(f.regexpContains("abcadcabcaecghi", ""), is(true)); // 任何字符串都包含空字符串,返回true

    try {
      final boolean b = f.regexpContains("abc def ghi", "(abc");
      fail("expected error, got " + b);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid regular expression for REGEXP_CONTAINS: 'Unclosed "
              + "group near index 4 (abc'"));
    }

    try {
      final boolean b = f.regexpContains("abc def ghi", "[z-a]");
      fail("expected error, got " + b);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid regular expression for REGEXP_CONTAINS: 'Illegal "
              + "character range near index 3 [z-a]    ^'"));
    }

    try {
      final boolean b = f.regexpContains("abc def ghi", "{2,1}");
      fail("expected error, got " + b);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid regular expression for REGEXP_CONTAINS: 'Illegal "
              + "repetition range near index 4 {2,1}     ^'"));
    }
  }

  // 测试方法: 测试regexpExtract函数(正则表达式提取)
  // 该函数从字符串中提取匹配正则表达式的子串
  // 支持捕获组、位置参数和出现次数参数
  // 测试基本提取、捕获组提取、位置提取和出现次数提取
  // 测试无效参数的错误处理
  @Test void testRegexpExtract() {
    final SqlFunctions.RegexFunction f = new SqlFunctions.RegexFunction();

    // basic extracts
    assertThat(f.regexpExtract("abcadcabcaecghi", "ac"), nullValue());
    assertThat(f.regexpExtract("abcadcabcaecghi", ""), is(""));
    assertThat(f.regexpExtract("a9cadca5c4aecghi", "a[0-9]c"), is("a9c"));
    assertThat(f.regexpExtract("abcadcabcaecghi", "a.*c"), is("abcadcabcaec"));

    // capturing group extracts
    assertThat(f.regexpExtract("abcadcabcaecghi", "abc(a.c)"), is("adc"));
    assertThat(f.regexpExtract("abcadcabcaecghi", "abc(a.c)", 4), is("aec"));
    assertThat(f.regexpExtract("abcadcabcaecghi", "abc(a.c)", 1, 2), is("aec"));

    // position-based extracts
    assertThat(f.regexpExtract("abcadcabcaecghi", "a.c", 25), nullValue());
    assertThat(f.regexpExtract("a9cadca5c4aecghi", "a[0-9]c", 1), is("a9c"));
    assertThat(f.regexpExtract("a9cadca5c4aecghi", "a[0-9]c", 6), is("a5c"));
    assertThat(f.regexpExtract("abcadcabcaecghi", "a.*c", 7), is("abcaec"));

    // occurrence-based extracts
    assertThat(f.regexpExtract("abcadcabcaecghi", "a.c", 1, 3), is("abc"));
    assertThat(f.regexpExtract("abcadcabcaecghi", "a.c", 2, 3), is("aec"));
    assertThat(f.regexpExtract("abcadcabcaecghi", "a.c", 1, 5), nullValue());
    assertThat(f.regexpExtract("abcadcabcaecghi", "a.+c", 1, 2), nullValue());

    // exceptional scenarios
    try {
      final String s = f.regexpExtract("abc def ghi", "(abc");
      fail("expected error, got " + s);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid regular expression for REGEXP_EXTRACT: 'Unclosed group near index 4 "
              + "(abc'"));
    }

    try {
      final String s = f.regexpExtract("abcadcabcaecghi", "(abc)ax(a.c)");
      fail("expected error, got " + s);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Multiple capturing groups (count=2) not allowed in regex input for "
              + "REGEXP_EXTRACT"));
    }

    try {
      final String s = f.regexpExtract("abcadcabcaecghi", "a.c", 0);
      fail("expected error, got " + s);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid integer input '0' for argument 'position' in REGEXP_EXTRACT"));
    }

    try {
      final String s = f.regexpExtract("abcadcabcaecghi", "a.c", 3, -1);
      fail("expected error, got " + s);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid integer input '-1' for argument 'occurrence' in REGEXP_EXTRACT"));
    }

    try {
      final String s = f.regexpExtract("abcadcabcaecghi", "a.c", -4, 4);
      fail("expected error, got " + s);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid integer input '-4' for argument 'position' in REGEXP_EXTRACT"));
    }
  }

  // 测试方法: 测试regexpExtractAll函数(提取所有匹配的正则表达式)
  // 该函数提取字符串中所有匹配正则表达式的子串
  // 返回一个字符串列表
  // 测试多个匹配项和空匹配项的情况
  // 测试无效正则表达式的错误处理
  @Test void testRegexpExtractAll() {
    final SqlFunctions.RegexFunction f = new SqlFunctions.RegexFunction();

    assertThat(f.regexpExtractAll("abcadcabcaecghi", "ac"), is(list()));
    assertThat(f.regexpExtractAll("abcadc", ""), is(list("", "", "", "", "", "", "")));
    assertThat(f.regexpExtractAll("abcadcabcaecghi", "abc(a.c)"), is(list("adc", "aec")));
    assertThat(f.regexpExtractAll("abcadcabcaecghi", "a.c"), is(list("abc", "adc", "abc", "aec")));
    assertThat(f.regexpExtractAll("banana", "ana"), is(list("ana")));
    assertThat(f.regexpExtractAll("abacadaeafa", "a.a"), is(list("aba", "ada", "afa")));
    assertThat(f.regexpExtractAll("abcdefghijklmnop", ".+"), is(list("abcdefghijklmnop")));

    try {
      final List<String> s = f.regexpExtractAll("abc def ghi", "(abc");
      fail("expected error, got array: " + s);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid regular expression for REGEXP_EXTRACT_ALL: 'Unclosed group near index 4 "
              + "(abc'"));
    }

    try {
      final List<String> s = f.regexpExtractAll("abcadcabcaecghi", "(abc).(ax).(a.c)");
      fail("expected error, got array:" + s);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Multiple capturing groups (count=3) not allowed in regex input for "
              + "REGEXP_EXTRACT_ALL"));
    }
  }

  // 测试方法: 测试regexpInstr函数(正则表达式位置查找)
  // 该函数查找正则表达式在字符串中的位置
  // 支持位置参数、出现次数参数和返回类型参数
  // 测试基本搜索、捕获组搜索、位置搜索和出现次数搜索
  // 测试无效参数的错误处理
  @Test void testRegexpInstr() {
    final SqlFunctions.RegexFunction f = new SqlFunctions.RegexFunction();

    // basic searches
    assertThat(f.regexpInstr("abcdefghij", "adc"), is(0));
    assertThat(f.regexpInstr("abcdefghij", ""), is(0));
    assertThat(f.regexpInstr("a9ca5c4aechi", "a[0-9]c"), is(1));
    assertThat(f.regexpInstr("abcadcabcaecghi", ".dc"), is(4));

    // capturing group searches
    assertThat(f.regexpInstr("abcadcabcaecghi", "abc(a.c)"), is(4));
    assertThat(f.regexpInstr("abcadcabcaecghi", "abc(a.c)", 4), is(10));
    assertThat(f.regexpInstr("abcadcabcaecghi", "abc(a.c)", 1, 2), is(10));
    assertThat(f.regexpInstr("abcadcabcaecghi", "abc(a.c)", 1, 2, 1), is(13));

    // position-based searches
    assertThat(f.regexpInstr("abcadcabcaecghi", ".ec", 25), is(0));
    assertThat(f.regexpInstr("a9cadca5c4aecghi", "a[0-9]c", 4), is(7));
    assertThat(f.regexpInstr("abcadcabcaecghi", "a.*c", 7), is(7));

    // occurrence-based searches
    assertThat(f.regexpInstr("a9cadca5c4aecghi", "a[0-9]c", 1, 3), is(0));
    assertThat(f.regexpInstr("a9cadca5c4aecghi", "a[0-9]c", 2, 1), is(7));
    assertThat(f.regexpInstr("a9cadca5c4aecghi", "a[0-9]c", 1, 1), is(1));

    // occurrence_position-based searches
    assertThat(f.regexpInstr("a9cadca5c4aecghi", "a[0-9]c", 1, 1, 0), is(1));
    assertThat(f.regexpInstr("abcadcabcaecghi", "abc(a.c)", 7, 1, 1), is(13));
    assertThat(f.regexpInstr("abcadcabcaec", "abc(a.c)", 4, 1, 1), is(13));

    // exceptional scenarios
    try {
      final int idx = f.regexpInstr("abc def ghi", "{4,1}");
      fail("expected error, got " + idx);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid regular expression for REGEXP_INSTR: 'Illegal repetition range near index 4"
              + " {4,1}     ^'"));
    }

    try {
      final int idx = f.regexpInstr("abcadcabcaecghi", "(.)a(.c)");
      fail("expected error, got " + idx);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Multiple capturing groups (count=2) not allowed in regex input for "
              + "REGEXP_INSTR"));
    }

    try {
      final int idx = f.regexpInstr("abcadcabcaecghi", "a.c", 0);
      fail("expected error, got " + idx);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid integer input '0' for argument 'position' in REGEXP_INSTR"));
    }

    try {
      final int idx = f.regexpInstr("abcadcabcaecghi", "a.c", 3, -1);
      fail("expected error, got " + idx);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid integer input '-1' for argument 'occurrence' in REGEXP_INSTR"));
    }

    try {
      final int idx = f.regexpInstr("abcadcabcaecghi", "a.c", 2, 4, -4);
      fail("expected error, got " + idx);
    } catch (RuntimeException e) {
      assertThat(e.getMessage(),
          is("Invalid integer input '-4' for argument 'occurrence_position' in REGEXP_INSTR"));
    }
  }

  // 测试方法: 测试replace函数(字符串替换)
  // 该函数在字符串中查找并替换子串
  // 支持大小写敏感和不敏感的替换
  // 测试空字符串、全匹配和无匹配的情况
  @Test void testReplace() {
    assertThat(replace("", "ciao", "ci", true), is(""));
    assertThat(replace("ciao", "ciao", "", true), is(""));
    assertThat(replace("ciao", "", "ciao", true), is("ciao"));
    assertThat(replace("ci ao", " ", "ciao", true), is("ciciaoao"));
    assertThat(replace("ciAao", "a", "ciao", true), is("ciAciaoo"));
    assertThat(replace("ciAao", "A", "ciao", true), is("ciciaoao"));
    assertThat(replace("ciAao", "a", "ciao", false), is("ciciaociaoo"));
    assertThat(replace("ciAao", "A", "ciao", false), is("ciciaociaoo"));
    assertThat(replace("hello world", "o", "", true), is("hell wrld"));
  }

  // 测试方法: 测试regexpReplace函数(正则表达式替换)
  // 该函数使用正则表达式在字符串中进行替换
  // 支持位置参数、出现次数参数和匹配类型参数
  // 测试基本替换、捕获组替换和PostgreSQL风格替换
  // 测试无效参数的错误处理
  @Test void testRegexpReplace() {
    final SqlFunctions.RegexFunction f = new SqlFunctions.RegexFunction();
    assertThat(f.regexpReplace("abc", "b"), is("ac"));
    assertThat(f.regexpReplace("a b c", "b", "X"), is("a X c"));
    assertThat(f.regexpReplace("abc def ghi", "[g-z]+", "X"), is("abc def X"));
    assertThat(f.regexpReplace("abc def ghi", "[a-z]+", "X"), is("X X X"));
    assertThat(f.regexpReplace("a b c", "a|b", "X"), is("X X c"));
    assertThat(f.regexpReplace("a b c", "y", "X"), is("a b c"));

    assertThat(f.regexpReplace("100-200", "(\\d+)", "num"), is("num-num"));
    assertThat(f.regexpReplace("100-200", "(\\d+)", "###"), is("###-###"));
    assertThat(f.regexpReplace("100-200", "(-)", "###"), is("100###200"));

    assertThat(f.regexpReplace("abc def ghi", "[a-z]+", "X", 1), is("X X X"));
    assertThat(f.regexpReplace("abc def ghi", "[a-z]+", "X", 2), is("aX X X"));
    assertThat(f.regexpReplace("abc def ghi", "[a-z]+", "X", 1, 3),
        is("abc def X"));
    assertThat(f.regexpReplace("abc def GHI", "[a-z]+", "X", 1, 3, "c"),
        is("abc def GHI"));
    assertThat(f.regexpReplace("abc def GHI", "[a-z]+", "X", 1, 3, "i"),
        is("abc def X"));
    assertThat(f.regexpReplacePg("abc def GHI", "[a-z]+", "X"), is("X def GHI"));
    assertThat(f.regexpReplacePg("abc def GHI", "[a-z]+", "X", "g"),
        is("X X GHI"));
    assertThat(f.regexpReplacePg("ABC def GHI", "[a-z]+", "X", "i"),
        is("X def GHI"));
    assertThat(f.regexpReplacePg("", "[a-z]+", "X", "i"), is(""));
    assertThat(f.regexpReplace("", "[a-z]+", "X", 1, 1, "i"), is(""));


    try {
      f.regexpReplace("abc def ghi", "[a-z]+", "X", 0);
      fail("'regexp_replace' on an invalid pos is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid input for REGEXP_REPLACE: '0'"));
    }

    try {
      f.regexpReplace("abc def ghi", "[a-z]+", "X", 1, 3, "WWW");
      fail("'regexp_replace' on an invalid matchType is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid input for REGEXP_REPLACE: 'WWW'"));
    }

    assertThat(f.regexpReplacePg("abc", "a(.*)c", "x\\1x", "i"),
        is("xbx"));
    assertThat(f.regexpReplace("abc", "a(.*)c", "x$1x"),
        is("xbx"));
    assertThat(f.regexpReplace("abc", "a(.*)c", "x\\1x"),
        is("x1x"));
  }

  @Test void testReplaceNonDollarIndexedString() {
    assertThat(SqlFunctions.RegexFunction.replaceNonDollarIndexedString("\\\\4_\\\\2"),
        is("$4_$2"));
    assertThat(SqlFunctions.RegexFunction.replaceNonDollarIndexedString("abc123"),
        is("abc123"));
    assertThat(SqlFunctions.RegexFunction.replaceNonDollarIndexedString("$007"),
        is("\\$007"));
    assertThat(SqlFunctions.RegexFunction.replaceNonDollarIndexedString("\\\\\\\\ \\\\\\\\"),
        is("\\\\ \\\\"));
    assertThat(SqlFunctions.RegexFunction.replaceNonDollarIndexedString("\\\\\\\\$ $\\\\\\\\"),
        is("\\\\\\$ \\$\\\\"));
    try {
      SqlFunctions.RegexFunction.replaceNonDollarIndexedString("\\\\-x");
      fail("'regexp_replace' with invalid replacement pattern is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid replacement pattern for REGEXP_REPLACE: '\\\\-x'"));
    }
    try {
      SqlFunctions.RegexFunction.replaceNonDollarIndexedString("\\\\ \\\\");
      fail("'regexp_replace' with invalid replacement pattern is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid replacement pattern for REGEXP_REPLACE: '\\\\ \\\\'"));
    }
    try {
      SqlFunctions.RegexFunction.replaceNonDollarIndexedString("\\\\a");
      fail("'regexp_replace' with invalid replacement pattern is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid replacement pattern for REGEXP_REPLACE: '\\\\a'"));
    }
  }

  @Test void testRegexpReplaceNonDollarIndexed() {
    final SqlFunctions.RegexFunction f = new SqlFunctions.RegexFunction();
    assertThat(f.regexpReplaceNonDollarIndexed("abascusB", "b", "X"), is("aXascusB"));
    assertThat(f.regexpReplaceNonDollarIndexed("abc01def02ghi", "[a-z]+", "X"), is("X01X02X"));
    assertThat(f.regexpReplaceNonDollarIndexed("a0b1c2d3", "0|2", "X"), is("aXb1cXd3"));

    // Test double-backslash indexing for capturing groups
    assertThat(f.regexpReplaceNonDollarIndexed("abc_defcon", "([a-z])_([a-z])", "\\\\2_\\\\1"),
        is("abd_cefcon"));
    assertThat(f.regexpReplaceNonDollarIndexed("1\\2\\3\\4\\5", "2.(.).4", "\\\\1"),
        is("1\\3\\5"));
    assertThat(f.regexpReplaceNonDollarIndexed("abc16", "b(.*)(\\d)", "\\\\\\\\"),
        is("a\\"));
    assertThat(f.regexpReplaceNonDollarIndexed("qwerty123", "([0-9]+)", "$147"),
        is("qwerty$147"));

    try {
      f.regexpReplaceNonDollarIndexed("abcdefghijabc", "abc(.)", "\\\\-11x");
      fail("'regexp_replace' with invalid replacement pattern is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid replacement pattern for REGEXP_REPLACE: '\\\\-11x'"));
    }
    try {
      f.regexpReplaceNonDollarIndexed("abcdefghijabc", "abc(.)", "\\\11x");
      fail("'regexp_replace' with invalid replacement pattern is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid replacement pattern for REGEXP_REPLACE: '\\\tx'"));
    }
  }

  // 测试方法: 测试lower函数(转小写)
  // 该函数将字符串转换为小写
  // 测试混合大小写字符串的转换
  @Test void testLower() {
    assertThat(lower("A bCd Iijk"), is("a bcd iijk"));
  }

  // 测试方法: 测试fromBase64函数(Base64解码)
  // 该函数将Base64编码的字符串解码为原始字节
  // 测试各种字符串(空字符串、特殊字符、Unicode字符)的解码
  // 测试无效Base64字符串的处理
  @Test void testFromBase64() {
    final List<String> expectedList =
        Arrays.asList("", "\0", "0", "a", " ", "\n", "\r\n", "\u03C0",
            "hello\tword");
    for (String expected : expectedList) {
      assertThat(fromBase64(toBase64(expected)),
          is(new ByteString(expected.getBytes(UTF_8))));
    }
    assertThat("546869732069732061207465737420537472696e672e",
        is(fromBase64("VGhpcyB  pcyBh\rIHRlc3Qg\tU3Ry\naW5nLg==").toString()));
    assertThat(fromBase64("-1"), nullValue());
  }

  // 测试方法: 测试toBase64函数(Base64编码)
  // 该函数将字符串编码为Base64格式
  // 支持长字符串自动换行(每76字符)
  // 测试长字符串和空字符串的编码
  @Test void testToBase64() {
    final String s = ""
        + "This is a test String. check resulte out of 76This is a test String."
        + "This is a test String.This is a test String.This is a test String."
        + "This is a test String. This is a test String. check resulte out of 76"
        + "This is a test String.This is a test String.This is a test String."
        + "This is a test String.This is a test String. This is a test String. "
        + "check resulte out of 76This is a test String.This is a test String."
        + "This is a test String.This is a test String.This is a test String.";
    final String actual = ""
        + "VGhpcyBpcyBhIHRlc3QgU3RyaW5nLiBjaGVjayByZXN1bHRlIG91dCBvZiA3NlRoaXMgaXMgYSB0\n"
        + "ZXN0IFN0cmluZy5UaGlzIGlzIGEgdGVzdCBTdHJpbmcuVGhpcyBpcyBhIHRlc3QgU3RyaW5nLlRo\n"
        + "aXMgaXMgYSB0ZXN0IFN0cmluZy5UaGlzIGlzIGEgdGVzdCBTdHJpbmcuIFRoaXMgaXMgYSB0ZXN0\n"
        + "IFN0cmluZy4gY2hlY2sgcmVzdWx0ZSBvdXQgb2YgNzZUaGlzIGlzIGEgdGVzdCBTdHJpbmcuVGhp\n"
        + "cyBpcyBhIHRlc3QgU3RyaW5nLlRoaXMgaXMgYSB0ZXN0IFN0cmluZy5UaGlzIGlzIGEgdGVzdCBT\n"
        + "dHJpbmcuVGhpcyBpcyBhIHRlc3QgU3RyaW5nLiBUaGlzIGlzIGEgdGVzdCBTdHJpbmcuIGNoZWNr\n"
        + "IHJlc3VsdGUgb3V0IG9mIDc2VGhpcyBpcyBhIHRlc3QgU3RyaW5nLlRoaXMgaXMgYSB0ZXN0IFN0\n"
        + "cmluZy5UaGlzIGlzIGEgdGVzdCBTdHJpbmcuVGhpcyBpcyBhIHRlc3QgU3RyaW5nLlRoaXMgaXMg\n"
        + "YSB0ZXN0IFN0cmluZy4=";
    assertThat(toBase64(s), is(actual));
    assertThat(toBase64(""), is(""));
  }

  // 测试方法: 测试upper函数(转大写)
  // 该函数将字符串转换为大写
  // 测试混合大小写字符串的转换
  @Test void testUpper() {
    assertThat(upper("A bCd iIjk"), is("A BCD IIJK"));
  }

  // 测试方法: 测试initcap函数(首字母大写)
  // 该函数将每个单词的首字母转换为大写,其余字母转换为小写
  // 测试各种字符串(全小写、全大写、混合大小写、含数字)的转换
  @Test void testInitcap() {
    assertThat(initcap("aA"), is("Aa"));
    assertThat(initcap("zz"), is("Zz"));
    assertThat(initcap("AZ"), is("Az"));
    assertThat(initcap("tRy a littlE  "), is("Try A Little  "));
    assertThat(initcap("won't it?no"), is("Won'T It?No"));
    assertThat(initcap("1A"), is("1a"));
    assertThat(initcap(" b0123B"), is(" B0123b"));
  }

  // 测试方法: 测试lesser函数(取较小值)
  // 该函数比较两个值,返回较小的一个
  // null值会被特殊处理: 如果任一值为null,返回另一个值; 如果都为null,返回null
  // 测试字符串比较和null值处理
  @Test void testLesser() {
    assertThat(lesser("a", "bc"), is("a"));
    assertThat(lesser("bc", "ac"), is("ac"));
    assertThat(lesser("a", null), is("a"));
    assertThat(lesser(null, "a"), is("a"));
    assertThat(lesser((String) null, null), nullValue());
  }

  // 测试方法: 测试greater函数(取较大值)
  // 该函数比较两个值,返回较大的一个
  // null值会被特殊处理: 如果任一值为null,返回另一个值; 如果都为null,返回null
  // 测试字符串比较和null值处理
  @Test void testGreater() {
    assertThat(greater("a", "bc"), is("bc"));
    assertThat(greater("bc", "ac"), is("bc"));
    assertThat(greater("a", null), is("a"));
    assertThat(greater(null, "a"), is("a"));
    assertThat(greater((String) null, null), nullValue());
  }

  /** Test for {@link SqlFunctions#rtrim}. */
  // 测试方法: 测试rtrim函数(去除右侧空格)
  // 该函数去除字符串右侧的空格字符
  // 测试空字符串、全空格字符串和部分空格字符串的处理
  @Test void testRtrim() {
    assertThat(rtrim(""), is(""));
    assertThat(rtrim("    "), is(""));
    assertThat(rtrim("   x  "), is("   x"));
    assertThat(rtrim("   x "), is("   x"));
    assertThat(rtrim("   x y "), is("   x y"));
    assertThat(rtrim("   x"), is("   x"));
    assertThat(rtrim("x"), is("x"));
  }

  /** Test for {@link SqlFunctions#ltrim}. */
  // 测试方法: 测试ltrim函数(去除左侧空格)
  // 该函数去除字符串左侧的空格字符
  // 测试空字符串、全空格字符串和部分空格字符串的处理
  @Test void testLtrim() {
    assertThat(ltrim(""), is(""));
    assertThat(ltrim("    "), is(""));
    assertThat(ltrim("   x  "), is("x  "));
    assertThat(ltrim("   x "), is("x "));
    assertThat(ltrim("x y "), is("x y "));
    assertThat(ltrim("   x"), is("x"));
    assertThat(ltrim("x"), is("x"));
  }

  /** Test for {@link SqlFunctions#trim}. */
  // 测试方法: 测试trim函数(去除首尾空格)
  // 该函数去除字符串首尾的空格字符
  // 测试空字符串、全空格字符串和部分空格字符串的处理
  @Test void testTrim() {
    assertThat(trimSpacesBoth(""), is(""));
    assertThat(trimSpacesBoth("    "), is(""));
    assertThat(trimSpacesBoth("   x  "), is("x"));
    assertThat(trimSpacesBoth("   x "), is("x"));
    assertThat(trimSpacesBoth("   x y "), is("x y"));
    assertThat(trimSpacesBoth("   x"), is("x"));
    assertThat(trimSpacesBoth("x"), is("x"));
  }

  /** Test for {@link SqlFunctions#overlay}. */
  // 测试方法: 测试overlay函数(字符串覆盖)
  // 该函数在字符串的指定位置覆盖另一段字符串
  // 可以指定覆盖的长度
  // 测试基本覆盖和零长度覆盖(插入操作)
  @Test void testOverlay() {
    assertThat(overlay("HelloWorld", "Java", 6), is("HelloJavad"));
    assertThat(overlay("Hello World", "World", 1), is("World World"));
    assertThat(overlay("HelloWorld", "Java", 6, 5), is("HelloJava"));
    assertThat(overlay("HelloWorld", "Java", 6, 0), is("HelloJavaWorld"));
  }


// 辅助方法: 同时去除字符串首尾的空格
  // 调用trim函数,指定去除左右两侧的空格
  // 参数s: 要处理的字符串
  // 返回值: 去除首尾空格后的字符串
  static String trimSpacesBoth(String s) {
    return trim(true, true, " ", s); // 调用trim函数,第一个true表示去除左侧空格,第二个true表示去除右侧空格
  }

  // 测试方法: 测试floor函数(向下取整)
  // 该函数返回小于或等于给定值的最大整数
  // 支持多种数值类型: int、long、short、byte和BigDecimal
  // 测试正数、负数和零的处理
  @Test void testFloor() {
    checkFloor(0, 10, 0); // 0向下取整到10的倍数,得到0
    checkFloor(27, 10, 20); // 27向下取整到10的倍数,得到20
    checkFloor(30, 10, 30); // 30向下取整到10的倍数,得到30
    checkFloor(-30, 10, -30); // -30向下取整到10的倍数,得到-30
    checkFloor(-27, 10, -30); // -27向下取整到10的倍数,得到-30
  }

  // 私有辅助方法: 检查floor函数的多种类型实现
  // 测试int、long、short、byte和BigDecimal类型的floor函数
  // 验证所有类型返回相同的结果
  // 参数x: 要向下取整的值
  // 参数y: 精度(取整的单位)
  // 参数result: 期望的结果
  private void checkFloor(int x, int y, int result) {
    assertThat(SqlFunctions.floor(x, y), is(result)); // 测试int类型
    assertThat(SqlFunctions.floor((long) x, (long) y), is((long) result)); // 测试long类型
    assertThat(SqlFunctions.floor((short) x, (short) y), is((short) result)); // 测试short类型
    assertThat(SqlFunctions.floor((byte) x, (byte) y), is((byte) result)); // 测试byte类型
    assertThat(
        SqlFunctions.floor(BigDecimal.valueOf(x), BigDecimal.valueOf(y)), // 测试BigDecimal类型
        is(BigDecimal.valueOf(result)));
  }

  // 测试方法: 测试ceil函数(向上取整)
  // 该函数返回大于或等于给定值的最小整数
  // 支持多种数值类型: int、long、short、byte和BigDecimal
  // 测试正数、负数和零的处理
  @Test void testCeil() {
    checkCeil(0, 10, 0); // 0向上取整到10的倍数,得到0
    checkCeil(27, 10, 30); // 27向上取整到10的倍数,得到30
    checkCeil(30, 10, 30); // 30向上取整到10的倍数,得到30
    checkCeil(-30, 10, -30); // -30向上取整到10的倍数,得到-30
    checkCeil(-27, 10, -20); // -27向上取整到10的倍数,得到-20
    checkCeil(-27, 1, -27); // -27向上取整到1的倍数,得到-27
  }

  // 私有辅助方法: 检查ceil函数的多种类型实现
  // 测试int、long、short、byte和BigDecimal类型的ceil函数
  // 验证所有类型返回相同的结果
  // 参数x: 要向上取整的值
  // 参数y: 精度(取整的单位)
  // 参数result: 期望的结果
  private void checkCeil(int x, int y, int result) {
    assertThat(SqlFunctions.ceil(x, y), is(result)); // 测试int类型
    assertThat(SqlFunctions.ceil((long) x, (long) y), is((long) result)); // 测试long类型
    assertThat(SqlFunctions.ceil((short) x, (short) y), is((short) result)); // 测试short类型
    assertThat(SqlFunctions.ceil((byte) x, (byte) y), is((byte) result)); // 测试byte类型
    assertThat(
        SqlFunctions.ceil(BigDecimal.valueOf(x), BigDecimal.valueOf(y)), // 测试BigDecimal类型
        is(BigDecimal.valueOf(result)));
  }

  /** Unit test for
   * {@link Utilities#compare(java.util.List, java.util.List)}. */
  // 测试方法: 测试compare函数(列表比较)
  // 该函数比较两个列表的字典序
  // 返回-1、0或1表示小于、等于或大于
  // 测试不同长度列表、相同列表和空列表的比较
  @Test void testCompare() {
    final List<String> ac = Arrays.asList("a", "c");
    final List<String> abc = Arrays.asList("a", "b", "c");
    final List<String> a = Collections.singletonList("a");
    final List<String> empty = Collections.emptyList();
    assertThat(Utilities.compare(ac, ac), is(0));
    assertThat(Utilities.compare(ac, new ArrayList<>(ac)), is(0));
    assertThat(Utilities.compare(a, ac), is(-1));
    assertThat(Utilities.compare(empty, ac), is(-1));
    assertThat(Utilities.compare(ac, a), is(1));
    assertThat(Utilities.compare(ac, abc), is(1));
    assertThat(Utilities.compare(ac, empty), is(1));
    assertThat(Utilities.compare(empty, empty), is(0));
  }

  // 测试方法: 测试truncate函数(长整数截断)
  // 该函数将长整数截断到指定的精度
  // 负精度表示截断到十的幂次方
  // 测试正数、负数和边界值处理
  @Test void testTruncateLong() {
    assertThat(SqlFunctions.truncate(12345L, 1000L), is(12000L));
    assertThat(SqlFunctions.truncate(12000L, 1000L), is(12000L));
    assertThat(SqlFunctions.truncate(12001L, 1000L), is(12000L));
    assertThat(SqlFunctions.truncate(11999L, 1000L), is(11000L));

    assertThat(SqlFunctions.truncate(-12345L, 1000L), is(-13000L));
    assertThat(SqlFunctions.truncate(-12000L, 1000L), is(-12000L));
    assertThat(SqlFunctions.truncate(-12001L, 1000L), is(-13000L));
    assertThat(SqlFunctions.truncate(-11999L, 1000L), is(-12000L));
  }

  // 测试方法: 测试truncate和round函数(整数截断和四舍五入)
  // 该函数将整数截断或四舍五入到指定的精度
  // 测试正数、负数和边界值处理
  @Test void testTruncateInt() {
    assertThat(SqlFunctions.truncate(12345, 1000), is(12000));
    assertThat(SqlFunctions.truncate(12000, 1000), is(12000));
    assertThat(SqlFunctions.truncate(12001, 1000), is(12000));
    assertThat(SqlFunctions.truncate(11999, 1000), is(11000));

    assertThat(SqlFunctions.truncate(-12345, 1000), is(-13000));
    assertThat(SqlFunctions.truncate(-12000, 1000), is(-12000));
    assertThat(SqlFunctions.truncate(-12001, 1000), is(-13000));
    assertThat(SqlFunctions.truncate(-11999, 1000), is(-12000));

    assertThat(SqlFunctions.round(12345, 1000), is(12000));
    assertThat(SqlFunctions.round(12845, 1000), is(13000));
    assertThat(SqlFunctions.round(-12345, 1000), is(-12000));
    assertThat(SqlFunctions.round(-12845, 1000), is(-13000));
  }

  // 测试方法: 测试struncate函数(双精度浮点数截断)
  // 该函数将双精度浮点数截断到指定的小数位数
  // 支持正负精度: 正精度截断小数位,负精度截断整数位
  // 测试正数、负数和零的处理
  @Test void testSTruncateDouble() {
    assertThat(SqlFunctions.struncate(12.345d, 3), closeTo(12.345d, 0.001));
    assertThat(SqlFunctions.struncate(12.345d, 2), closeTo(12.340d, 0.001));
    assertThat(SqlFunctions.struncate(12.345d, 1), closeTo(12.300d, 0.001));
    assertThat(SqlFunctions.struncate(12.999d, 0), closeTo(12.000d, 0.001));

    assertThat(SqlFunctions.struncate(-12.345d, 3), closeTo(-12.345d, 0.001));
    assertThat(SqlFunctions.struncate(-12.345d, 2), closeTo(-12.340d, 0.001));
    assertThat(SqlFunctions.struncate(-12.345d, 1), closeTo(-12.300d, 0.001));
    assertThat(SqlFunctions.struncate(-12.999d, 0), closeTo(-12.000d, 0.001));

    assertThat(SqlFunctions.struncate(12345d, -3), closeTo(12000d, 0.001));
    assertThat(SqlFunctions.struncate(12000d, -3), closeTo(12000d, 0.001));
    assertThat(SqlFunctions.struncate(12001d, -3), closeTo(12000d, 0.001));
    assertThat(SqlFunctions.struncate(12000d, -4), closeTo(10000d, 0.001));
    assertThat(SqlFunctions.struncate(12000d, -5), closeTo(0d, 0.001));
    assertThat(SqlFunctions.struncate(11999d, -3), closeTo(11000d, 0.001));

    assertThat(SqlFunctions.struncate(-12345d, -3), closeTo(-12000d, 0.001));
    assertThat(SqlFunctions.struncate(-12000d, -3), closeTo(-12000d, 0.001));
    assertThat(SqlFunctions.struncate(-11999d, -3), closeTo(-11000d, 0.001));
    assertThat(SqlFunctions.struncate(-12000d, -4), closeTo(-10000d, 0.001));
    assertThat(SqlFunctions.struncate(-12000d, -5), closeTo(0d, 0.001));
  }

  // 测试方法: 测试struncate函数(长整数截断)
  // 该函数将长整数截断到指定的精度
  // 负精度表示截断到十的幂次方
  // 测试正数、负数和边界值处理
  @Test void testSTruncateLong() {
    assertThat(SqlFunctions.struncate(12345L, -3), is(12000L));
    assertThat(SqlFunctions.struncate(12000L, -3), is(12000L));
    assertThat(SqlFunctions.struncate(12001L, -3), is(12000L));
    assertThat(SqlFunctions.struncate(12000L, -4), is(10000L));
    assertThat(SqlFunctions.struncate(12000L, -5), is(0L));
    assertThat(SqlFunctions.struncate(11999L, -3), is(11000L));

    assertThat(SqlFunctions.struncate(-12345L, -3), is(-12000L));
    assertThat(SqlFunctions.struncate(-12000L, -3), is(-12000L));
    assertThat(SqlFunctions.struncate(-11999L, -3), is(-11000L));
    assertThat(SqlFunctions.struncate(-12000L, -4), is(-10000L));
    assertThat(SqlFunctions.struncate(-12000L, -5), is(0L));
  }

  // 测试方法: 测试struncate函数(整数截断)
  // 该函数将整数截断到指定的精度
  // 负精度表示截断到十的幂次方
  // 测试正数、负数和边界值处理
  @Test void testSTruncateInt() {
    assertThat(SqlFunctions.struncate(12345, -3), is(12000));
    assertThat(SqlFunctions.struncate(12000, -3), is(12000));
    assertThat(SqlFunctions.struncate(12001, -3), is(12000));
    assertThat(SqlFunctions.struncate(12000, -4), is(10000));
    assertThat(SqlFunctions.struncate(12000, -5), is(0));
    assertThat(SqlFunctions.struncate(11999, -3), is(11000));

    assertThat(SqlFunctions.struncate(-12345, -3), is(-12000));
    assertThat(SqlFunctions.struncate(-12000, -3), is(-12000));
    assertThat(SqlFunctions.struncate(-11999, -3), is(-11000));
    assertThat(SqlFunctions.struncate(-12000, -4), is(-10000));
    assertThat(SqlFunctions.struncate(-12000, -5), is(0));
  }

  // 测试方法: 测试sround函数(双精度浮点数四舍五入)
  // 该函数将双精度浮点数四舍五入到指定的小数位数
  // 支持正负精度: 正精度四舍五入小数位,负精度四舍五入整数位
  // 测试正数、负数和零的处理
  @Test void testSRoundDouble() {
    assertThat(SqlFunctions.sround(12.345d, 3), closeTo(12.345d, 0.001));
    assertThat(SqlFunctions.sround(12.345d, 2), closeTo(12.350d, 0.001));
    assertThat(SqlFunctions.sround(12.345d, 1), closeTo(12.300d, 0.001));
    assertThat(SqlFunctions.sround(12.999d, 2), closeTo(13.000d, 0.001));
    assertThat(SqlFunctions.sround(12.999d, 1), closeTo(13.000d, 0.001));
    assertThat(SqlFunctions.sround(12.999d, 0), closeTo(13.000d, 0.001));

    assertThat(SqlFunctions.sround(-12.345d, 3), closeTo(-12.345d, 0.001));
    assertThat(SqlFunctions.sround(-12.345d, 2), closeTo(-12.350d, 0.001));
    assertThat(SqlFunctions.sround(-12.345d, 1), closeTo(-12.300d, 0.001));
    assertThat(SqlFunctions.sround(-12.999d, 2), closeTo(-13.000d, 0.001));
    assertThat(SqlFunctions.sround(-12.999d, 1), closeTo(-13.000d, 0.001));
    assertThat(SqlFunctions.sround(-12.999d, 0), closeTo(-13.000d, 0.001));

    assertThat(SqlFunctions.sround(12345d, -1), closeTo(12350d, 0.001));
    assertThat(SqlFunctions.sround(12345d, -2), closeTo(12300d, 0.001));
    assertThat(SqlFunctions.sround(12345d, -3), closeTo(12000d, 0.001));
    assertThat(SqlFunctions.sround(12000d, -3), closeTo(12000d, 0.001));
    assertThat(SqlFunctions.sround(12001d, -3), closeTo(12000d, 0.001));
    assertThat(SqlFunctions.sround(12000d, -4), closeTo(10000d, 0.001));
    assertThat(SqlFunctions.sround(12000d, -5), closeTo(0d, 0.001));
    assertThat(SqlFunctions.sround(11999d, -3), closeTo(12000d, 0.001));

    assertThat(SqlFunctions.sround(-12345d, -1), closeTo(-12350d, 0.001));
    assertThat(SqlFunctions.sround(-12345d, -2), closeTo(-12300d, 0.001));
    assertThat(SqlFunctions.sround(-12345d, -3), closeTo(-12000d, 0.001));
    assertThat(SqlFunctions.sround(-12000d, -3), closeTo(-12000d, 0.001));
    assertThat(SqlFunctions.sround(-11999d, -3), closeTo(-12000d, 0.001));
    assertThat(SqlFunctions.sround(-12000d, -4), closeTo(-10000d, 0.001));
    assertThat(SqlFunctions.sround(-12000d, -5), closeTo(0d, 0.001));
  }

  // 测试方法: 测试sround函数(长整数四舍五入)
  // 该函数将长整数四舍五入到指定的精度
  // 负精度表示四舍五入到十的幂次方
  // 测试正数、负数和边界值处理
  @Test void testSRoundLong() {
    assertThat(SqlFunctions.sround(12345L, -1), is(12350L));
    assertThat(SqlFunctions.sround(12345L, -2), is(12300L));
    assertThat(SqlFunctions.sround(12345L, -3), is(12000L));
    assertThat(SqlFunctions.sround(12000L, -3), is(12000L));
    assertThat(SqlFunctions.sround(12001L, -3), is(12000L));
    assertThat(SqlFunctions.sround(12000L, -4), is(10000L));
    assertThat(SqlFunctions.sround(12000L, -5), is(0L));
    assertThat(SqlFunctions.sround(11999L, -3), is(12000L));

    assertThat(SqlFunctions.sround(-12345L, -1), is(-12350L));
    assertThat(SqlFunctions.sround(-12345L, -2), is(-12300L));
    assertThat(SqlFunctions.sround(-12345L, -3), is(-12000L));
    assertThat(SqlFunctions.sround(-12000L, -3), is(-12000L));
    assertThat(SqlFunctions.sround(-11999L, -3), is(-12000L));
    assertThat(SqlFunctions.sround(-12000L, -4), is(-10000L));
    assertThat(SqlFunctions.sround(-12000L, -5), is(0L));
  }

  // 测试方法: 测试sround函数(整数四舍五入)
  // 该函数将整数四舍五入到指定的精度
  // 负精度表示四舍五入到十的幂次方
  // 测试正数、负数和边界值处理
  @Test void testSRoundInt() {
    assertThat(SqlFunctions.sround(12345, -1), is(12350));
    assertThat(SqlFunctions.sround(12345, -2), is(12300));
    assertThat(SqlFunctions.sround(12345, -3), is(12000));
    assertThat(SqlFunctions.sround(12000, -3), is(12000));
    assertThat(SqlFunctions.sround(12001, -3), is(12000));
    assertThat(SqlFunctions.sround(12000, -4), is(10000));
    assertThat(SqlFunctions.sround(12000, -5), is(0));
    assertThat(SqlFunctions.sround(11999, -3), is(12000));

    assertThat(SqlFunctions.sround(-12345, -1), is(-12350));
    assertThat(SqlFunctions.sround(-12345, -2), is(-12300));
    assertThat(SqlFunctions.sround(-12345, -3), is(-12000));
    assertThat(SqlFunctions.sround(-12000, -3), is(-12000));
    assertThat(SqlFunctions.sround(-11999, -3), is(-12000));
    assertThat(SqlFunctions.sround(-12000, -4), is(-10000));
    assertThat(SqlFunctions.sround(-12000, -5), is(0));
  }

  // 测试方法: 测试split函数(字符串分割)
  // 该函数使用分隔符将字符串分割成多个子串
  // 支持String和ByteString类型
  // 测试无分隔符、分隔符在中间、分隔符在开头/结尾、空分隔符等情况
  @Test void testSplit() {
    assertThat("no occurrence of delimiter",
        SqlFunctions.split("abc", ","), is(list("abc")));
    assertThat("delimiter in middle",
        SqlFunctions.split("abc", "b"), is(list("a", "c")));
    assertThat("delimiter at end",
        SqlFunctions.split("abc", "c"), is(list("ab", "")));
    assertThat("delimiter at start",
        SqlFunctions.split("abc", "a"), is(list("", "bc")));
    assertThat("empty delimiter",
        SqlFunctions.split("abc", ""), is(list("abc")));
    assertThat("empty delimiter and string",
        SqlFunctions.split("", ""), is(list()));
    assertThat("empty string",
        SqlFunctions.split("", ","), is(list()));
    assertThat("long delimiter (occurs at start)",
        SqlFunctions.split("abracadabra", "ab"), is(list("", "racad", "ra")));
    assertThat("long delimiter (occurs at end)",
        SqlFunctions.split("sabracadabrab", "ab"),
        is(list("s", "racad", "r", "")));

    // Same as above but for ByteString
    final ByteString a = ByteString.of("aa", 16);
    final ByteString ab = ByteString.of("aabb", 16);
    final ByteString abc = ByteString.of("aabbcc", 16);
    final ByteString abracadabra = ByteString.of("aabb44aaccaaddaabb44aa", 16);
    final ByteString b = ByteString.of("bb", 16);
    final ByteString bc = ByteString.of("bbcc", 16);
    final ByteString c = ByteString.of("cc", 16);
    final ByteString f = ByteString.of("ff", 16);
    final ByteString r = ByteString.of("44", 16);
    final ByteString ra = ByteString.of("44aa", 16);
    final ByteString racad = ByteString.of("44aaccaadd", 16);
    final ByteString empty = ByteString.of("", 16);
    final ByteString s = ByteString.of("55", 16);
    final ByteString sabracadabrab =
        ByteString.of("55", 16).concat(abracadabra).concat(b);
    assertThat("no occurrence of delimiter",
        SqlFunctions.split(abc, f), is(list(abc)));
    assertThat("delimiter in middle",
        SqlFunctions.split(abc, b), is(list(a, c)));
    assertThat("delimiter at end",
        SqlFunctions.split(abc, c), is(list(ab, empty)));
    assertThat("delimiter at start",
        SqlFunctions.split(abc, a), is(list(empty, bc)));
    assertThat("empty delimiter",
        SqlFunctions.split(abc, empty), is(list(abc)));
    assertThat("empty delimiter and string",
        SqlFunctions.split(empty, empty), is(list()));
    assertThat("empty string",
        SqlFunctions.split(empty, f), is(list()));
    assertThat("long delimiter (occurs at start)",
        SqlFunctions.split(abracadabra, ab), is(list(empty, racad, ra)));
    assertThat("long delimiter (occurs at end)",
        SqlFunctions.split(sabracadabrab, ab),
        is(list(s, racad, r, empty)));
  }

  // 测试方法: 测试splitPart函数(获取分割后的指定部分)
  // 该函数使用分隔符分割字符串并返回指定位置的子串
  // 支持正负索引: 正索引从左到右,负索引从右到左
  // 测试正常索引、边界索引、空值和无效索引的处理
  @Test void testSplitPart() {
    assertThat(SqlFunctions.splitPart("abc~@~def~@~ghi", "~@~", 2), is("def"));
    assertThat(SqlFunctions.splitPart("abc,def,ghi,jkl", ",", -2), is("ghi"));

    assertThat(SqlFunctions.splitPart("abc,,ghi", ",", 2), is(""));
    assertThat(SqlFunctions.splitPart("", ",", 1), is(""));
    assertThat(SqlFunctions.splitPart("abc", "", 1), is(""));

    assertThat(SqlFunctions.splitPart(null, ",", 1), is(""));
    assertThat(SqlFunctions.splitPart("abc,def", null, 1), is(""));
    assertThat(SqlFunctions.splitPart("abc,def", ",", 0), is(""));

    assertThat(SqlFunctions.splitPart("abc,def", ",", 3), is(""));
    assertThat(SqlFunctions.splitPart("abc,def", ",", -3), is(""));
  }

  // 测试方法: 测试ByteString类(字节字符串)
  // 该类用于处理二进制数据
  // 支持十六进制、二进制和Base64表示
  // 测试创建、转换、比较、连接、查找和子字符串操作
  @Test void testByteString() {
    final byte[] bytes = {(byte) 0xAB, (byte) 0xFF};
    final ByteString byteString = new ByteString(bytes);
    assertThat(byteString.length(), is(2));
    assertThat(byteString, hasToString("abff"));
    assertThat(byteString.toString(16), is("abff"));
    assertThat(byteString.toString(2), is("1010101111111111"));

    final ByteString emptyByteString = new ByteString(new byte[0]);
    assertThat(emptyByteString.length(), is(0));
    assertThat(emptyByteString, hasToString(""));
    assertThat(emptyByteString.toString(16), is(""));
    assertThat(emptyByteString.toString(2), is(""));

    assertThat(ByteString.EMPTY, is(emptyByteString));

    assertThat(byteString.substring(1, 2), hasToString("ff"));
    assertThat(byteString.substring(0, 2), hasToString("abff"));
    assertThat(byteString.substring(2, 2), hasToString(""));

    // Add empty string, get original string back
    assertSame(byteString.concat(emptyByteString), byteString);
    final ByteString byteString1 = new ByteString(new byte[]{(byte) 12});
    assertThat(byteString.concat(byteString1), hasToString("abff0c"));

    final byte[] bytes3 = {(byte) 0xFF};
    final ByteString byteString3 = new ByteString(bytes3);

    assertThat(byteString.indexOf(emptyByteString), is(0));
    assertThat(byteString.indexOf(byteString1), is(-1));
    assertThat(byteString.indexOf(byteString3), is(1));
    assertThat(byteString3.indexOf(byteString), is(-1));

    thereAndBack(bytes);
    thereAndBack(emptyByteString.getBytes());
    thereAndBack(new byte[]{10, 0, 29, -80});

    assertThat(ByteString.of("ab12", 16).toString(16), equalTo("ab12"));
    assertThat(ByteString.of("AB0001DdeAD3", 16).toString(16),
        equalTo("ab0001ddead3"));
    assertThat(ByteString.of("", 16), equalTo(emptyByteString));
    try {
      ByteString x = ByteString.of("ABg0", 16);
      fail("expected error, got " + x);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), equalTo("invalid hex character: g"));
    }
    try {
      ByteString x = ByteString.of("ABC", 16);
      fail("expected error, got " + x);
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage(), equalTo("hex string has odd length"));
    }

    final byte[] bytes4 = {10, 0, 1, -80};
    final ByteString byteString4 = new ByteString(bytes4);
    final byte[] bytes5 = {10, 0, 1, 127};
    final ByteString byteString5 = new ByteString(bytes5);
    final ByteString byteString6 = new ByteString(bytes4);

    assertThat(byteString4.compareTo(byteString5) > 0, is(true));
    assertThat(byteString4.compareTo(byteString6) == 0, is(true));
    assertThat(byteString5.compareTo(byteString4) < 0, is(true));
  }

  // 私有辅助方法: 测试字节串的往返转换
  // 将字节数组转换为ByteString,再转换回来,验证一致性
  // 同时测试Base64编码和解码的往返转换
  // 参数bytes: 原始字节数组
  private void thereAndBack(byte[] bytes) {
    final ByteString byteString = new ByteString(bytes); // 创建ByteString对象
    final byte[] bytes2 = byteString.getBytes(); // 从ByteString获取字节数组
    assertThat(bytes, equalTo(bytes2)); // 验证往返转换后字节数组一致

    final String base64String = byteString.toBase64String(); // 转换为Base64字符串
    final ByteString byteString1 = ByteString.ofBase64(base64String); // 从Base64字符串创建ByteString
    assertThat(byteString, equalTo(byteString1)); // 验证Base64往返转换后ByteString一致
  }

  // 测试方法: 测试eqAny函数(任意类型相等比较)
  // 该函数比较两个值是否相等
  // 支持多种数值类型的自动转换(int、long、double、BigDecimal)
  // 测试相同类型、不同类型和无效类型的比较
  @Test void testEqWithAny() {
    // Non-numeric same type equality check
    assertThat(SqlFunctions.eqAny("hello", "hello"), is(true));

    // Numeric types equality check
    assertThat(SqlFunctions.eqAny(1, 1L), is(true));
    assertThat(SqlFunctions.eqAny(1, 1.0D), is(true));
    assertThat(SqlFunctions.eqAny(1L, 1.0D), is(true));
    assertThat(SqlFunctions.eqAny(new BigDecimal(1L), 1), is(true));
    assertThat(SqlFunctions.eqAny(new BigDecimal(1L), 1L), is(true));
    assertThat(SqlFunctions.eqAny(new BigDecimal(1L), 1.0D), is(true));
    assertThat(SqlFunctions.eqAny(new BigDecimal(1L), new BigDecimal(1.0D)),
        is(true));

    // Non-numeric different type equality check
    assertThat(SqlFunctions.eqAny("2", 2), is(false));
  }

  // 测试方法: 测试neAny函数(任意类型不等比较)
  // 该函数比较两个值是否不等
  // 支持多种数值类型的自动转换(int、long、double、BigDecimal)
  // 测试相同类型、不同类型和无效类型的比较
  @Test void testNeWithAny() {
    // Non-numeric same type inequality check
    assertThat(SqlFunctions.neAny("hello", "world"), is(true));

    // Numeric types inequality check
    assertThat(SqlFunctions.neAny(1, 2L), is(true));
    assertThat(SqlFunctions.neAny(1, 2.0D), is(true));
    assertThat(SqlFunctions.neAny(1L, 2.0D), is(true));
    assertThat(SqlFunctions.neAny(new BigDecimal(2L), 1), is(true));
    assertThat(SqlFunctions.neAny(new BigDecimal(2L), 1L), is(true));
    assertThat(SqlFunctions.neAny(new BigDecimal(2L), 1.0D), is(true));
    assertThat(SqlFunctions.neAny(new BigDecimal(2L), new BigDecimal(1.0D)),
        is(true));

    // Non-numeric different type inequality check
    assertThat(SqlFunctions.neAny("2", 2), is(true));
  }

  // 测试方法: 测试ltAny函数(任意类型小于比较)
  // 该函数比较两个值,第一个是否小于第二个
  // 支持数值类型和可比较类型
  // 测试相同类型、不同类型和无效类型的比较
  @Test void testLtWithAny() {
    // Non-numeric same type "less then" check
    assertThat(SqlFunctions.ltAny("apple", "banana"), is(true));

    // Numeric types "less than" check
    assertThat(SqlFunctions.ltAny(1, 2L), is(true));
    assertThat(SqlFunctions.ltAny(1, 2.0D), is(true));
    assertThat(SqlFunctions.ltAny(1L, 2.0D), is(true));
    assertThat(SqlFunctions.ltAny(new BigDecimal(1L), 2), is(true));
    assertThat(SqlFunctions.ltAny(new BigDecimal(1L), 2L), is(true));
    assertThat(SqlFunctions.ltAny(new BigDecimal(1L), 2.0D), is(true));
    assertThat(SqlFunctions.ltAny(new BigDecimal(1L), new BigDecimal(2.0D)),
        is(true));

    // Non-numeric different type but both implements Comparable
    // "less than" check
    try {
      assertThat(SqlFunctions.ltAny("1", 2L), is(false));
      fail("'lt' on non-numeric different type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for comparison: class java.lang.String < "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试leAny函数(任意类型小于等于比较)
  // 该函数比较两个值,第一个是否小于等于第二个
  // 支持数值类型和可比较类型
  // 测试相同类型、不同类型和无效类型的比较
  @Test void testLeWithAny() {
    // Non-numeric same type "less or equal" check
    assertThat(SqlFunctions.leAny("apple", "banana"), is(true));
    assertThat(SqlFunctions.leAny("apple", "apple"), is(true));

    // Numeric types "less or equal" check
    assertThat(SqlFunctions.leAny(1, 2L), is(true));
    assertThat(SqlFunctions.leAny(1, 1L), is(true));
    assertThat(SqlFunctions.leAny(1, 2.0D), is(true));
    assertThat(SqlFunctions.leAny(1, 1.0D), is(true));
    assertThat(SqlFunctions.leAny(1L, 2.0D), is(true));
    assertThat(SqlFunctions.leAny(1L, 1.0D), is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), 2), is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), 1), is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), 2L), is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), 1L), is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), 2.0D), is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), 1.0D), is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), new BigDecimal(2.0D)),
        is(true));
    assertThat(SqlFunctions.leAny(new BigDecimal(1L), new BigDecimal(1.0D)),
        is(true));

    // Non-numeric different type but both implements Comparable
    // "less or equal" check
    try {
      assertThat(SqlFunctions.leAny("2", 2L), is(false));
      fail("'le' on non-numeric different type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for comparison: class java.lang.String <= "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试gtAny函数(任意类型大于比较)
  // 该函数比较两个值,第一个是否大于第二个
  // 支持数值类型和可比较类型
  // 测试相同类型、不同类型和无效类型的比较
  @Test void testGtWithAny() {
    // Non-numeric same type "greater then" check
    assertThat(SqlFunctions.gtAny("banana", "apple"), is(true));

    // Numeric types "greater than" check
    assertThat(SqlFunctions.gtAny(2, 1L), is(true));
    assertThat(SqlFunctions.gtAny(2, 1.0D), is(true));
    assertThat(SqlFunctions.gtAny(2L, 1.0D), is(true));
    assertThat(SqlFunctions.gtAny(new BigDecimal(2L), 1), is(true));
    assertThat(SqlFunctions.gtAny(new BigDecimal(2L), 1L), is(true));
    assertThat(SqlFunctions.gtAny(new BigDecimal(2L), 1.0D), is(true));
    assertThat(SqlFunctions.gtAny(new BigDecimal(2L), new BigDecimal(1.0D)),
        is(true));

    // Non-numeric different type but both implements Comparable
    // "greater than" check
    try {
      assertThat(SqlFunctions.gtAny("2", 1L), is(false));
      fail("'gt' on non-numeric different type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for comparison: class java.lang.String > "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试geAny函数(任意类型大于等于比较)
  // 该函数比较两个值,第一个是否大于等于第二个
  // 支持数值类型和可比较类型
  // 测试相同类型、不同类型和无效类型的比较
  @Test void testGeWithAny() {
    // Non-numeric same type "greater or equal" check
    assertThat(SqlFunctions.geAny("banana", "apple"), is(true));
    assertThat(SqlFunctions.geAny("apple", "apple"), is(true));

    // Numeric types "greater or equal" check
    assertThat(SqlFunctions.geAny(2, 1L), is(true));
    assertThat(SqlFunctions.geAny(1, 1L), is(true));
    assertThat(SqlFunctions.geAny(2, 1.0D), is(true));
    assertThat(SqlFunctions.geAny(1, 1.0D), is(true));
    assertThat(SqlFunctions.geAny(2L, 1.0D), is(true));
    assertThat(SqlFunctions.geAny(1L, 1.0D), is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(2L), 1), is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(1L), 1), is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(2L), 1L), is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(1L), 1L), is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(2L), 1.0D), is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(1L), 1.0D), is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(2L), new BigDecimal(1.0D)),
        is(true));
    assertThat(SqlFunctions.geAny(new BigDecimal(1L), new BigDecimal(1.0D)),
        is(true));

    // Non-numeric different type but both implements Comparable
    // "greater or equal" check
    try {
      assertThat(SqlFunctions.geAny("2", 2L), is(false));
      fail("'ge' on non-numeric different type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for comparison: class java.lang.String >= "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试plusAny函数(任意类型加法)
  // 该函数对两个值进行加法运算
  // 支持多种数值类型的自动转换(int、long、double、BigDecimal)
  // 测试null值、数值类型和无效类型的处理
  @Test void testPlusAny() {
    // null parameters
    assertThat(SqlFunctions.plusAny(null, null), nullValue());
    assertThat(SqlFunctions.plusAny(null, 1), nullValue());
    assertThat(SqlFunctions.plusAny(1, null), nullValue());

    // Numeric types
    assertThat(SqlFunctions.plusAny(2, 1L), is((Object) new BigDecimal(3)));
    assertThat(SqlFunctions.plusAny(2, 1.0D), is((Object) new BigDecimal(3)));
    assertThat(SqlFunctions.plusAny(2L, 1.0D), is((Object) new BigDecimal(3)));
    assertThat(SqlFunctions.plusAny(new BigDecimal(2L), 1),
        is((Object) new BigDecimal(3)));
    assertThat(SqlFunctions.plusAny(new BigDecimal(2L), 1L),
        is((Object) new BigDecimal(3)));
    assertThat(SqlFunctions.plusAny(new BigDecimal(2L), 1.0D),
        is((Object) new BigDecimal(3)));
    assertThat(SqlFunctions.plusAny(new BigDecimal(2L), new BigDecimal(1.0D)),
        is((Object) new BigDecimal(3)));

    // Non-numeric type
    try {
      SqlFunctions.plusAny("2", 2L);
      fail("'plus' on non-numeric type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for arithmetic: class java.lang.String + "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试minusAny函数(任意类型减法)
  // 该函数对两个值进行减法运算
  // 支持多种数值类型的自动转换(int、long、double、BigDecimal)
  // 测试null值、数值类型和无效类型的处理
  @Test void testMinusAny() {
    // null parameters
    assertThat(SqlFunctions.minusAny(null, null), nullValue());
    assertThat(SqlFunctions.minusAny(null, 1), nullValue());
    assertThat(SqlFunctions.minusAny(1, null), nullValue());

    // Numeric types
    assertThat(SqlFunctions.minusAny(2, 1L), is((Object) new BigDecimal(1)));
    assertThat(SqlFunctions.minusAny(2, 1.0D), is((Object) new BigDecimal(1)));
    assertThat(SqlFunctions.minusAny(2L, 1.0D), is((Object) new BigDecimal(1)));
    assertThat(SqlFunctions.minusAny(new BigDecimal(2L), 1),
        is((Object) new BigDecimal(1)));
    assertThat(SqlFunctions.minusAny(new BigDecimal(2L), 1L),
        is((Object) new BigDecimal(1)));
    assertThat(SqlFunctions.minusAny(new BigDecimal(2L), 1.0D),
        is((Object) new BigDecimal(1)));
    assertThat(SqlFunctions.minusAny(new BigDecimal(2L), new BigDecimal(1.0D)),
        is((Object) new BigDecimal(1)));

    // Non-numeric type
    try {
      SqlFunctions.minusAny("2", 2L);
      fail("'minus' on non-numeric type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for arithmetic: class java.lang.String - "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试multiplyAny函数(任意类型乘法)
  // 该函数对两个值进行乘法运算
  // 支持多种数值类型的自动转换(int、long、double、BigDecimal)
  // 测试null值、数值类型和无效类型的处理
  @Test void testMultiplyAny() {
    // null parameters
    assertThat(SqlFunctions.multiplyAny(null, null), nullValue());
    assertThat(SqlFunctions.multiplyAny(null, 1), nullValue());
    assertThat(SqlFunctions.multiplyAny(1, null), nullValue());

    // Numeric types
    assertThat(SqlFunctions.multiplyAny(2, 1L), is(new BigDecimal(2)));
    assertThat(SqlFunctions.multiplyAny(2, 1.0D),
        is(new BigDecimal(2)));
    assertThat(SqlFunctions.multiplyAny(2L, 1.0D),
        is(new BigDecimal(2)));
    assertThat(SqlFunctions.multiplyAny(new BigDecimal(2L), 1),
        is(new BigDecimal(2)));
    assertThat(SqlFunctions.multiplyAny(new BigDecimal(2L), 1L),
        is(new BigDecimal(2)));
    assertThat(SqlFunctions.multiplyAny(new BigDecimal(2L), 1.0D),
        is(new BigDecimal(2)));
    assertThat(SqlFunctions.multiplyAny(new BigDecimal(2L), new BigDecimal(1.0D)),
        is(new BigDecimal(2)));

    // Non-numeric type
    try {
      SqlFunctions.multiplyAny("2", 2L);
      fail("'multiply' on non-numeric type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for arithmetic: class java.lang.String * "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试divideAny函数(任意类型除法)
  // 该函数对两个值进行除法运算
  // 支持多种数值类型的自动转换(int、long、double、BigDecimal)
  // 测试null值、数值类型和无效类型的处理
  @Test void testDivideAny() {
    // null parameters
    assertThat(SqlFunctions.divideAny(null, null), nullValue());
    assertThat(SqlFunctions.divideAny(null, 1), nullValue());
    assertThat(SqlFunctions.divideAny(1, null), nullValue());

    // Numeric types
    assertThat(SqlFunctions.divideAny(5, 2L),
        is(new BigDecimal("2.5")));
    assertThat(SqlFunctions.divideAny(5, 2.0D),
        is(new BigDecimal("2.5")));
    assertThat(SqlFunctions.divideAny(5L, 2.0D),
        is(new BigDecimal("2.5")));
    assertThat(SqlFunctions.divideAny(new BigDecimal(5L), 2),
        is(new BigDecimal(2.5)));
    assertThat(SqlFunctions.divideAny(new BigDecimal(5L), 2L),
        is(new BigDecimal(2.5)));
    assertThat(SqlFunctions.divideAny(new BigDecimal(5L), 2.0D),
        is(new BigDecimal(2.5)));
    assertThat(SqlFunctions.divideAny(new BigDecimal(5L), new BigDecimal(2.0D)),
        is(new BigDecimal(2.5)));

    // Non-numeric type
    try {
      SqlFunctions.divideAny("5", 2L);
      fail("'divide' on non-numeric type is not possible");
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid types for arithmetic: class java.lang.String / "
              + "class java.lang.Long"));
    }
  }

  // 测试方法: 测试multiset函数(多重集合操作)
  // 该函数提供多重集合的并集、交集、差集操作
  // 支持保留重复元素(All)和去重(Distinct)两种模式
  // 测试multisetUnion(并集)、multisetIntersect(交集)、multisetExcept(差集)
  @Test void testMultiset() {
    final List<String> abacee = Arrays.asList("a", "b", "a", "c", "e", "e");
    final List<String> adaa = Arrays.asList("a", "d", "a", "a");
    final List<String> addc = Arrays.asList("a", "d", "c", "d", "c");
    final List<String> z = Collections.emptyList();
    assertThat(SqlFunctions.multisetExceptAll(abacee, addc),
        isListOf("b", "a", "e", "e"));
    assertThat(SqlFunctions.multisetExceptAll(abacee, z), is(abacee));
    assertThat(SqlFunctions.multisetExceptAll(z, z), is(z));
    assertThat(SqlFunctions.multisetExceptAll(z, addc), is(z));

    assertThat(SqlFunctions.multisetExceptDistinct(abacee, addc),
        isListOf("b", "e"));
    assertThat(SqlFunctions.multisetExceptDistinct(abacee, z),
        isListOf("a", "b", "c", "e"));
    assertThat(SqlFunctions.multisetExceptDistinct(z, z), is(z));
    assertThat(SqlFunctions.multisetExceptDistinct(z, addc), is(z));

    Matcher<Object> result;
    result = isListOf("a", "c");
    assertThat(SqlFunctions.multisetIntersectAll(abacee, addc),
        result);
    assertThat(SqlFunctions.multisetIntersectAll(abacee, adaa),
        isListOf("a", "a"));
    assertThat(SqlFunctions.multisetIntersectAll(adaa, abacee),
        isListOf("a", "a"));
    assertThat(SqlFunctions.multisetIntersectAll(abacee, z), is(z));
    assertThat(SqlFunctions.multisetIntersectAll(z, z), is(z));
    assertThat(SqlFunctions.multisetIntersectAll(z, addc), is(z));

    assertThat(SqlFunctions.multisetIntersectDistinct(abacee, addc),
        isListOf("a", "c"));
    assertThat(SqlFunctions.multisetIntersectDistinct(abacee, adaa),
        isListOf("a"));
    assertThat(SqlFunctions.multisetIntersectDistinct(adaa, abacee),
        isListOf("a"));
    assertThat(SqlFunctions.multisetIntersectDistinct(abacee, z), is(z));
    assertThat(SqlFunctions.multisetIntersectDistinct(z, z), is(z));
    assertThat(SqlFunctions.multisetIntersectDistinct(z, addc), is(z));

    assertThat(SqlFunctions.multisetUnionAll(abacee, addc),
        isListOf("a", "b", "a", "c", "e", "e", "a", "d", "c", "d", "c"));
    assertThat(SqlFunctions.multisetUnionAll(abacee, z), is(abacee));
    assertThat(SqlFunctions.multisetUnionAll(z, z), is(z));
    assertThat(SqlFunctions.multisetUnionAll(z, addc), is(addc));

    assertThat(SqlFunctions.multisetUnionDistinct(abacee, addc),
        isListOf("a", "b", "c", "d", "e"));
    assertThat(SqlFunctions.multisetUnionDistinct(abacee, z),
        isListOf("a", "b", "c", "e"));
    assertThat(SqlFunctions.multisetUnionDistinct(z, z), is(z));
    assertThat(SqlFunctions.multisetUnionDistinct(z, addc),
        isListOf("a", "c", "d"));
  }

  // 测试方法: 测试md5函数(MD5哈希)
  // 该函数计算字符串或字节串的MD5哈希值
  // 测试空字符串、普通字符串和ByteString的哈希计算
  // 测试null值的处理
  @Test void testMd5() {
    assertThat("d41d8cd98f00b204e9800998ecf8427e", is(md5("")));
    assertThat("d41d8cd98f00b204e9800998ecf8427e", is(md5(ByteString.of("", 16))));
    assertThat("902fbdd2b1df0c4f70b4a5d23525e932", is(md5("ABC")));
    assertThat("902fbdd2b1df0c4f70b4a5d23525e932",
        is(md5(new ByteString("ABC".getBytes(UTF_8)))));
    try {
      String o = md5((String) null);
      fail("Expected NPE, got " + o);
    } catch (NullPointerException e) {
      // ok
    }
  }

  // 测试方法: 测试sha1函数(SHA-1哈希)
  // 该函数计算字符串或字节串的SHA-1哈希值
  // 测试空字符串、普通字符串和ByteString的哈希计算
  // 测试null值的处理
  @Test void testSha1() {
    assertThat("da39a3ee5e6b4b0d3255bfef95601890afd80709", is(sha1("")));
    assertThat("da39a3ee5e6b4b0d3255bfef95601890afd80709", is(sha1(ByteString.of("", 16))));
    assertThat("3c01bdbb26f358bab27f267924aa2c9a03fcfdb8", is(sha1("ABC")));
    assertThat("3c01bdbb26f358bab27f267924aa2c9a03fcfdb8",
        is(sha1(new ByteString("ABC".getBytes(UTF_8)))));
    try {
      String o = sha1((String) null);
      fail("Expected NPE, got " + o);
    } catch (NullPointerException e) {
      // ok
    }
  }

  // 测试方法: 测试sha256函数(SHA-256哈希)
  // 该函数计算字符串或字节串的SHA-256哈希值
  // 测试空字符串、普通字符串和ByteString的哈希计算
  // 测试null值的处理
  @Test void testSha256() {
    assertThat("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        is(sha256("")));
    assertThat("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        is(sha256(ByteString.of("", 16))));
    assertThat("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
        is(sha256("Hello World")));
    assertThat("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
        is(sha256(new ByteString("Hello World".getBytes(UTF_8)))));
    try {
      String o = sha256((String) null);
      fail("Expected NPE, got " + o);
    } catch (NullPointerException e) {
      // ok
    }
  }

  // 测试方法: 测试sha512函数(SHA-512哈希)
  // 该函数计算字符串或字节串的SHA-512哈希值
  // 测试空字符串、普通字符串和ByteString的哈希计算
  // 测试null值的处理
  @Test void testSha512() {
    assertThat("cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5"
            + "d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
        is(sha512("")));
    assertThat("cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5"
            + "d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
        is(sha512(ByteString.of("", 16))));
    assertThat("2c74fd17edafd80e8447b0d46741ee243b7eb74dd2149a0ab1b9246fb30382f27e853d858"
            + "5719e0e67cbda0daa8f51671064615d645ae27acb15bfb1447f459b",
        is(sha512("Hello World")));
    assertThat("2c74fd17edafd80e8447b0d46741ee243b7eb74dd2149a0ab1b9246fb30382f27e853d858"
            + "5719e0e67cbda0daa8f51671064615d645ae27acb15bfb1447f459b",
        is(sha512(new ByteString("Hello World".getBytes(UTF_8)))));
    try {
      String o = sha512((String) null);
      fail("Expected NPE, got " + o);
    } catch (NullPointerException e) {
      // ok
    }
  }

  // 测试方法: 测试position函数(位置查找)
  // 该函数查找子串在字符串中的位置
  // 支持起始位置和出现次数参数
  // 支持String和ByteString类型
  // 测试基本查找、带起始位置的查找、带出现次数的查找和无效参数的处理
  @Test void testPosition() {
    assertThat(position("c", "abcdec"), is(3));
    assertThat(position("c", "abcdec", 2), is(3));
    assertThat(position("c", "abcdec", -2), is(3));
    assertThat(position("c", "abcdec", 4), is(6));
    assertThat(position("c", "abcdec", 1, 2), is(6));
    assertThat(position("cde", "abcdecde", -2, 1), is(6));
    assertThat(position("c", "abcdec", -1, 2), is(3));
    assertThat(position("f", "abcdec", 1, 1), is(0));
    assertThat(position("c", "abcdec", 1, 3), is(0));
    try {
      int i = position("c", "abcdec", 0, 1);
      fail("expected error, got: " + i);
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid input for POSITION function: from operand value must not be zero"));
    }
    try {
      int i = position("c", "abcdec", 1, 0);
      fail("expected error, got: " + i);
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid input for POSITION function: occurrence operand value must be positive"));
    }
    final ByteString abcdec = ByteString.of("aabbccddeecc", 16);
    final ByteString c = ByteString.of("cc", 16);
    final ByteString dec = ByteString.of("ddeecc", 16);
    final ByteString f = ByteString.of("ff", 16);
    assertThat(position(c, abcdec), is(3));
    assertThat(position(c, abcdec, 2), is(3));
    assertThat(position(c, abcdec, -2), is(3));
    assertThat(position(c, abcdec, 4), is(6));
    assertThat(position(dec, abcdec, -2), is(4));
    assertThat(position(c, abcdec, 1, 2), is(6));
    assertThat(position(c, abcdec, -1, 2), is(3));
    assertThat(position(f, abcdec, 1, 1), is(0));
    assertThat(position(c, abcdec, 1, 3), is(0));
    try {
      int i = position(c, abcdec, 0, 1);
      fail("expected error, got: " + i);
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid input for POSITION function: from operand value must not be zero"));
    }
    try {
      int i = position(c, abcdec, 1, 0);
      fail("expected error, got: " + i);
    } catch (CalciteException e) {
      assertThat(e.getMessage(),
          is("Invalid input for POSITION function: occurrence operand value must be positive"));
    }
  }


  /**
   * Tests that a date in the local time zone converts to a Unix timestamp in
   * UTC.
   */
  // 测试方法: 测试toInt函数(SQL日期转Unix日期)
  // 该函数将java.sql.Date转换为Unix日期(天数)
  // 测试纪元日期、历史日期和四舍五入处理
  @Test void testToIntWithSqlDate() {
    assertThat(toInt(new java.sql.Date(0L)), is(0));  // rounded to closest day
    assertThat(sqlDate("1970-01-01"), is(0));
    assertThat(sqlDate("1500-04-30"), is(dateStringToUnixDate("1500-04-30")));
  }

  /**
   * Test calendar conversion from the standard Gregorian calendar used by
   * {@code java.sql} and the proleptic Gregorian calendar used by Unix
   * timestamps.
   */
  // 测试方法: 测试toInt函数(格里高利历转换)
  // 该函数测试格里高利历转换(1582年10月4日跳到10月15日)
  // 验证从标准格里高利历到外推格里高利历的转换
  @Test void testToIntWithSqlDateInGregorianShift() {
    assertThat(sqlDate("1582-10-04"), is(dateStringToUnixDate("1582-10-04")));
    assertThat(sqlDate("1582-10-05"), is(dateStringToUnixDate("1582-10-15")));
    assertThat(sqlDate("1582-10-15"), is(dateStringToUnixDate("1582-10-15")));
  }

  /**
   * Test date range 0001-01-01 to 9999-12-31 required by ANSI SQL.
   *
   * <p>Java may not be able to represent 0001-01-01 depending on the default
   * time zone. If the date would fall outside of Anno Domini (AD) when
   * converted to the default time zone, that date should not be tested.
   *
   * <p>Not every time zone has a January 1st 12:00am, so this test skips those
   * dates.
   */
  // 测试方法: 测试toInt函数(ANSI日期范围)
  // 该函数测试ANSI SQL要求的日期范围(0001-01-01到9999-12-31)
  // 测试从2年到9999年的所有日期转换
  // 跳过在某些时区无法表示的日期
  @Test void testToIntWithSqlDateInAnsiDateRange() {
    for (int i = 2; i <= 9999; ++i) {
      final String str = String.format(Locale.ROOT, "%04d-01-01", i);
      final java.sql.Date date = java.sql.Date.valueOf(str);
      final Timestamp timestamp = new Timestamp(date.getTime());
      if (timestamp.toString().endsWith("00:00:00.0")) {
        // Test equality if the time is valid in Java
        assertThat("Converts '" + str + "' from SQL to Unix date",
            toInt(date),
            is(dateStringToUnixDate(str)));
      } else {
        // Test result matches legacy behavior if the time cannot be
        // represented in Java. This probably results in a different date but
        // is pretty rare.
        final long expected =
            (date.getTime() + DateTimeUtils.DEFAULT_ZONE.getOffset(date.getTime()))
                / DateTimeUtils.MILLIS_PER_DAY;
        assertThat("Converts '" + str
                + "' from SQL to Unix date using legacy behavior",
            toInt(date),
            is((int) expected));
      }
    }
  }

  /**
   * Test using a custom {@link TimeZone} to calculate the Unix timestamp.
   * Dates created by a {@link java.sql.Date} method should be converted
   * relative to the local time and not UTC.
   */
  @Test public void testToIntWithTimeZone() {
    // Dates created by a Calendar should be converted to a Unix date in that
    // time zone
    final Calendar utcCal =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
    utcCal.set(1970, Calendar.JANUARY, 1, 0, 0, 0);
    utcCal.set(Calendar.MILLISECOND, 0);
    assertThat(
        toInt(new java.sql.Date(utcCal.getTimeInMillis()),
            utcCal.getTimeZone()),
        is(0));

    // Dates should be relative to the local time and not UTC
    final java.sql.Date epoch = java.sql.Date.valueOf("1970-01-01");

    final TimeZone minusDayZone = TimeZone.getDefault();
    minusDayZone.setRawOffset((int) (minusDayZone.getRawOffset() - MILLIS_PER_DAY));
    assertThat(toInt(epoch, minusDayZone), is(-1));

    final TimeZone plusDayZone = TimeZone.getDefault();
    plusDayZone.setRawOffset((int) (plusDayZone.getRawOffset() + MILLIS_PER_DAY));
    assertThat(toInt(epoch, plusDayZone), is(1));
  }

  /**
   * Tests that a nullable date in the local time zone converts to a Unix
   * timestamp in UTC.
   */
  // 测试方法: 测试toIntOptional函数(本地时区可选)
  // 该函数将可空的本地时区日期转换为Unix日期
  // 测试正常日期和null值的处理
  @Test void testToIntOptionalWithLocalTimeZone() {
    assertThat(toIntOptional(java.sql.Date.valueOf("1970-01-01")), is(0));
    assertThat(toIntOptional((java.sql.Date) null), is(nullValue()));
  }

  /**
   * Tests that a nullable date in the given time zone converts to a Unix
   * timestamp in UTC.
   */
  // 测试方法: 测试toIntOptional函数(自定义时区可选)
  // 该函数将可空的指定时区日期转换为Unix日期
  // 测试UTC时区和null值的处理
  @Test void testToIntOptionalWithCustomTimeZone() {
    final TimeZone utc = TimeZone.getTimeZone("UTC");
    assertThat(toIntOptional(new java.sql.Date(0L), utc), is(0));
    assertThat(toIntOptional(null, utc), is(nullValue()));
  }

  /**
   * Tests that a time in the local time zone converts to a Unix time in UTC.
   */
  // 测试方法: 测试toInt函数(SQL时间转Unix时间)
  // 该函数将java.sql.Time转换为Unix时间(毫秒数)
  // 测试午夜时间和午夜前一秒的时间
  @Test void testToIntWithSqlTime() {
    assertThat(sqlTime("00:00:00"), is(timeStringToUnixDate("00:00:00")));
    assertThat(sqlTime("23:59:59"), is(timeStringToUnixDate("23:59:59")));
  }

  /**
   * Tests that a nullable time in the local time zone converts to a Unix time
   * in UTC.
   */
  // 测试方法: 测试toIntOptional函数(SQL时间可选)
  // 该函数将可空的SQL时间转换为Unix时间
  // 测试午夜时间和null值的处理
  @Test void testToIntOptionalWithSqlTime() {
    assertThat(toIntOptional(Time.valueOf("00:00:00")), is(0));
    assertThat(toIntOptional((Time) null), is(nullValue()));
  }

  /**
   * Tests that a timestamp in the local time zone converts to a Unix timestamp
   * in UTC.
   */
  // 测试方法: 测试toLong函数(SQL时间戳转Unix时间戳)
  // 该函数将java.sql.Timestamp转换为Unix时间戳(毫秒数)
  // 测试纪元时间戳、现代时间戳和历史时间戳
  @Test void testToLongWithSqlTimestamp() {
    assertThat(sqlTimestamp("1970-01-01 00:00:00"), is(0L));
    assertThat(sqlTimestamp("2014-09-30 15:28:27.356"),
        is(timestampStringToUnixDate("2014-09-30 15:28:27.356")));
    assertThat(sqlTimestamp("1500-04-30 12:00:00.123"),
        is(timestampStringToUnixDate("1500-04-30 12:00:00.123")));
  }

  /**
   * Test using a custom {@link TimeZone} to calculate the Unix timestamp.
   * Timestamps created by a {@link Calendar} should be converted to a Unix
   * timestamp in the given time zone. Timestamps created by a {@link Timestamp}
   * method should be converted relative to the local time and not UTC.
   */
  // 测试方法: 测试toLong函数(带自定义时区)
  // 该函数使用自定义时区计算Unix时间戳
  // 测试UTC时区、负偏移时区和正偏移时区
  @Test void testToLongWithSqlTimestampAndCustomTimeZone() {
    final Timestamp epoch = java.sql.Timestamp.valueOf("1970-01-01 00:00:00");

    final Calendar utcCal =
        Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
    utcCal.set(1970, Calendar.JANUARY, 1, 0, 0, 0);
    utcCal.set(Calendar.MILLISECOND, 0);
    assertThat(toLong(new Timestamp(utcCal.getTimeInMillis()), utcCal.getTimeZone()),
        is(0L));

    final TimeZone est = TimeZone.getTimeZone("GMT-5:00");
    assertThat(toLong(epoch, est),
        is(epoch.getTime() + est.getOffset(epoch.getTime())));

    final TimeZone ist = TimeZone.getTimeZone("GMT+5:00");
    assertThat(toLong(epoch, ist),
        is(epoch.getTime() + ist.getOffset(epoch.getTime())));
  }

  /**
   * Test calendar conversion from the standard Gregorian calendar used by
   * {@code java.sql} and the proleptic Gregorian calendar used by Unix
   * timestamps.
   */
  // 测试方法: 测试toLong函数(格里高利历转换)
  // 该函数测试格里高利历转换(1582年10月4日跳到10月15日)
  // 验证从标准格里高利历到外推格里高利历的转换
  @Test void testToLongWithSqlTimestampInGregorianShift() {
    assertThat(sqlTimestamp("1582-10-04 00:00:00"),
        is(timestampStringToUnixDate("1582-10-04 00:00:00")));
    assertThat(sqlTimestamp("1582-10-05 00:00:00"),
        is(timestampStringToUnixDate("1582-10-15 00:00:00")));
    assertThat(sqlTimestamp("1582-10-15 00:00:00"),
        is(timestampStringToUnixDate("1582-10-15 00:00:00")));
  }

  /**
   * Test date range 0001-01-01 to 9999-12-31 required by ANSI SQL.
   *
   * <p>Java may not be able to represent 0001-01-01 depending on the default
   * time zone. If the date would fall outside of Anno Domini (AD) when
   * converted to the default time zone, that date should not be tested.
   *
   * <p>Not every time zone has a January 1st 12:00am, so this test skips those
   * dates.
   */
  // 测试方法: 测试toLong函数(ANSI日期范围)
  // 该函数测试ANSI SQL要求的日期范围(0001-01-01到9999-12-31)
  // 测试从2年到9999年的所有时间戳转换
  // 跳过在某些时区无法表示的时间戳
  @Test void testToLongWithSqlTimestampInAnsiDateRange() {
    for (int i = 2; i <= 9999; ++i) {
      final String str = String.format(Locale.ROOT, "%04d-01-01 00:00:00", i);
      final Timestamp timestamp = Timestamp.valueOf(str);
      if (timestamp.toString().endsWith("00:00:00.0")) {
        // Test equality if the time is valid in Java
        assertThat("Converts '" + str + "' from SQL to Unix timestamp",
            toLong(timestamp),
            is(timestampStringToUnixDate(str)));
      } else {
        // Test result matches legacy behavior if the time cannot be represented in Java
        // This probably results in a different date but is pretty rare
        final long expected = timestamp.getTime()
            + DateTimeUtils.DEFAULT_ZONE.getOffset(timestamp.getTime());
        assertThat("Converts '" + str
                + "' from SQL to Unix timestamp using legacy behavior",
            toLong(timestamp),
            is(expected));
      }
    }
  }

  /**
   * Tests that a nullable timestamp in the local time zone converts to a Unix
   * timestamp in UTC.
   */
  // 测试方法: 测试toLongOptional函数(本地时区可选)
  // 该函数将可空的本地时区时间戳转换为Unix时间戳
  // 测试纪元时间戳和null值的处理
  @Test void testToLongOptionalWithLocalTimeZone() {
    assertThat(toLongOptional(Timestamp.valueOf("1970-01-01 00:00:00")), is(0L));
    assertThat(toLongOptional(null), is(nullValue()));
  }

  /**
   * Test date after 0001-01-01 required by ANSI SQL - is passed.
   * Test date before 0001-01-01 and malformed date time literal - is failed.
   */
  // 测试方法: 测试toTimestampWithLocalTimeZone函数(转本地时区时间戳)
  // 该函数将字符串转换为本地时区的Unix时间戳
  // 测试正常时间戳、带毫秒的时间戳和带时区ID的时间戳
  // 测试格式错误和日期范围错误的处理
  @Test void testToTimestampWithLocalTimeZone() {
    Long ret = toTimestampWithLocalTimeZone("1970-01-01 00:00:01", TimeZone.getTimeZone("UTC"));
    assertThat(ret, is(1000L));

    ret = toTimestampWithLocalTimeZone("1970-01-01 00:00:01.010", TimeZone.getTimeZone("UTC"));
    assertThat(ret, is(1010L));

    ret = toTimestampWithLocalTimeZone("1970-01-01 00:00:01 "
        + TimeZone.getTimeZone("UTC").getID());
    assertThat(ret, is(1000L));

    // exceptional scenarios
    try {
      ret = toTimestampWithLocalTimeZone("malformed", TimeZone.getDefault());
      fail("expected error, got " + ret);
    } catch (CalciteException e) {
      assertThat(e.getMessage(), containsString("Illegal TIMESTAMP WITH LOCAL TIME ZONE literal"));
    }

    try {
      ret = toTimestampWithLocalTimeZone("0000-01-01 00:00:01", TimeZone.getDefault());
      fail("expected error, got " + ret);
    } catch (CalciteException e) {
      assertThat(e.getMessage(), containsString("Illegal TIMESTAMP WITH LOCAL TIME ZONE literal"));
    }

    try {
      ret = toTimestampWithLocalTimeZone("malformed " + TimeZone.getDefault().getID());
      fail("expected error, got " + ret);
    } catch (CalciteException e) {
      assertThat(e.getMessage(), containsString("Illegal TIMESTAMP WITH LOCAL TIME ZONE literal"));
    }
  }

  /**
   * Tests that a nullable timestamp in the given time zone converts to a Unix
   * timestamp in UTC.
   */
  // 测试方法: 测试toLongOptional函数(自定义时区可选)
  // 该函数将可空的指定时区时间戳转换为Unix时间戳
  // 测试UTC时区和null值的处理
  @Test void testToLongOptionalWithCustomTimeZone() {
    final TimeZone utc = TimeZone.getTimeZone("UTC");
    assertThat(toLongOptional(new Timestamp(0L), utc), is(0L));
    assertThat(toLongOptional(null, utc), is(nullValue()));
  }

  /**
   * Tests that a Unix timestamp converts to a date in the local time zone.
   */
  // 测试方法: 测试internalToDate函数(Unix日期转SQL日期)
  // 该函数将Unix日期转换为java.sql.Date
  // 相对于本地时区
  // 测试纪元日期和历史日期
  @Test void testInternalToDate() {
    assertThat(internalToDate(0), is(java.sql.Date.valueOf("1970-01-01")));
    assertThat(internalToDate(dateStringToUnixDate("1500-04-30")),
        is(java.sql.Date.valueOf("1500-04-30")));
  }

  /**
   * Test calendar conversion from the standard Gregorian calendar used by
   * {@code java.sql} and the proleptic Gregorian calendar used by Unix
   * timestamps.
   */
  // 测试方法: 测试internalToDate函数(格里高利历转换)
  // 该函数测试格里高利历转换(1582年10月4日跳到10月15日)
  // 验证从外推格里高利历到标准格里高利历的转换
  @Test void testInternalToDateWithGregorianShift() {
    // Gregorian shift
    assertThat(internalToDate(dateStringToUnixDate("1582-10-04")),
        is(java.sql.Date.valueOf("1582-10-04")));
    assertThat(internalToDate(dateStringToUnixDate("1582-10-05")),
        is(java.sql.Date.valueOf("1582-10-15")));
    assertThat(internalToDate(dateStringToUnixDate("1582-10-15")),
        is(java.sql.Date.valueOf("1582-10-15")));
  }

  /**
   * Test date range 0001-01-01 to 9999-12-31 required by ANSI SQL.
   *
   * <p>Java may not be able to represent all dates depending on the default time zone, but both
   * the expected and actual assertion values handles that in the same way.
   */
  // 测试方法: 测试internalToDate函数(ANSI日期范围)
  // 该函数测试ANSI SQL要求的日期范围(0001-01-01到9999-12-31)
  // 测试从2年到9999年的所有日期转换
  @Test void testInternalToDateWithAnsiDateRange() {
    for (int i = 2; i <= 9999; ++i) {
      final String str = String.format(Locale.ROOT, "%04d-01-01", i);
      assertThat(internalToDate(dateStringToUnixDate(str)),
          is(java.sql.Date.valueOf(str)));
    }
  }

  /**
   * Tests that a Unix time converts to a SQL time in the local time zone.
   */
  // 测试方法: 测试internalToTime函数(Unix时间转SQL时间)
  // 该函数将Unix时间转换为java.sql.Time
  // 相对于本地时区
  // 测试午夜时间和午夜前一秒的时间
  @Test void testInternalToTime() {
    assertThat(internalToTime(0), is(Time.valueOf("00:00:00")));
    assertThat(internalToTime(86399000), is(Time.valueOf("23:59:59")));
  }

  /**
   * Tests that timestamp can be converted to a string given a custom pattern.
   */
  // 测试方法: 测试toChar函数(时间戳转字符串)
  // 该函数将Unix时间戳转换为指定格式的字符串
  // 测试多种日期时间格式(YYYY-MM-DD HH24:MI:SS.MS, Day, DD HH12:MI:SS)
  // 测试纪元时间戳、现代时间戳和历史时间戳
  @Test void testToChar() {
    String pattern1 = "YYYY-MM-DD HH24:MI:SS.MS";
    String pattern2 = "Day, DD HH12:MI:SS";

    final SqlFunctions.DateFormatFunction f =
        new SqlFunctions.DateFormatFunction();
    assertThat(f.toChar(0, pattern1),
        is("1970-01-01 00:00:00.000"));

    assertThat(f.toChar(0, pattern2),
        is("Thursday, 01 12:00:00"));

    final long ts0 = timestampStringToUnixDate("2014-09-30 15:28:27.356");
    assertThat(f.toChar(ts0, pattern1),
        is("2014-09-30 15:28:27.356"));

    assertThat(f.toChar(ts0, pattern2),
        is("Tuesday, 30 03:28:27"));

    final long ts1 = timestampStringToUnixDate("1500-04-30 12:00:00.123");
    assertThat(f.toChar(ts1, pattern1),
        is("1500-04-30 12:00:00.123"));
  }

  // 测试方法: 测试toDate函数(字符串转日期)
  // 该函数将字符串按照指定格式解析为Unix日期
  // 测试标准日期格式(YYYY-MM-DD)的解析
  @Test void testToDate() {
    String pattern1 = "YYYY-MM-DD";

    final SqlFunctions.DateFormatFunction f =
        new SqlFunctions.DateFormatFunction();

    assertThat(f.toDate("2001-10-06", pattern1), is(11601));
  }

  // 测试方法: 测试toTimestamp函数(字符串转时间戳)
  // 该函数将字符串按照指定格式解析为Unix时间戳
  // 测试自定义日期时间格式(HH24:MI:SS YYYY-MM-DD)的解析
  @Test void testToTimestamp() {
    String pattern1 = "HH24:MI:SS YYYY-MM-DD";

    final SqlFunctions.DateFormatFunction f =
        new SqlFunctions.DateFormatFunction();

    assertThat(f.toTimestamp("18:43:36 2001-10-06", pattern1), is(1002393816000L));
  }

  /**
   * Tests that a Unix timestamp converts to a SQL timestamp in the local time
   * zone.
   */
  // 测试方法: 测试internalToTimestamp函数(Unix时间戳转SQL时间戳)
  // 该函数将Unix时间戳转换为java.sql.Timestamp
  // 相对于本地时区
  // 测试纪元时间戳、现代时间戳和历史时间戳
  @Test void testInternalToTimestamp() {
    assertThat(internalToTimestamp(0),
        is(Timestamp.valueOf("1970-01-01 00:00:00.0")));
    assertThat(internalToTimestamp(timestampStringToUnixDate("2014-09-30 15:28:27.356")),
        is(Timestamp.valueOf("2014-09-30 15:28:27.356")));
    assertThat(internalToTimestamp(timestampStringToUnixDate("1500-04-30 12:00:00.123")),
        is(Timestamp.valueOf("1500-04-30 12:00:00.123")));
  }

  /**
   * Test calendar conversion from the standard Gregorian calendar used by
   * {@code java.sql} and the proleptic Gregorian calendar used by Unix timestamps.
   */
  // 测试方法: 测试internalToTimestamp函数(格里高利历转换)
  // 该函数测试格里高利历转换(1582年10月4日跳到10月15日)
  // 验证从外推格里高利历到标准格里高利历的转换
  @Test void testInternalToTimestampWithGregorianShift() {
    assertThat(
        internalToTimestamp(timestampStringToUnixDate("1582-10-04 00:00:00")),
        is(Timestamp.valueOf("1582-10-04 00:00:00.0")));
    assertThat(
        internalToTimestamp(timestampStringToUnixDate("1582-10-05 00:00:00")),
        is(Timestamp.valueOf("1582-10-15 00:00:00.0")));
    assertThat(
        internalToTimestamp(timestampStringToUnixDate("1582-10-15 00:00:00")),
        is(Timestamp.valueOf("1582-10-15 00:00:00.0")));
  }

  /**
   * Test date range 0001-01-01 to 9999-12-31 required by ANSI SQL.
   *
   * <p>Java may not be able to represent all dates depending on the default
   * time zone, but both the expected and actual assertion values handles that
   * in the same way.
   */
  // 测试方法: 测试internalToTimestamp函数(ANSI日期范围)
  // 该函数测试ANSI SQL要求的日期范围(0001-01-01到9999-12-31)
  // 测试从2年到9999年的所有时间戳转换
  @Test void testInternalToTimestampWithAnsiDateRange() {
    for (int i = 2; i <= 9999; ++i) {
      final String str = String.format(Locale.ROOT, "%04d-01-01 00:00:00", i);
      assertThat(internalToTimestamp(timestampStringToUnixDate(str)),
          is(Timestamp.valueOf(str)));
    }
  }

  // 私有辅助方法: 将SQL日期字符串转换为Unix日期
  // 使用java.sql.Date.valueOf解析日期字符串
  // 然后调用toInt函数转换为Unix日期
  // 参数str: 日期字符串(格式: YYYY-MM-DD)
  // 返回值: Unix日期(天数)
  private int sqlDate(String str) {
    return toInt(java.sql.Date.valueOf(str)); // 解析日期字符串并转换为Unix日期
  }

  // 私有辅助方法: 将SQL时间字符串转换为Unix时间
  // 使用java.sql.Time.valueOf解析时间字符串
  // 然后调用toInt函数转换为Unix时间
  // 参数str: 时间字符串(格式: HH:MM:SS)
  // 返回值: Unix时间(毫秒数)
  private int sqlTime(String str) {
    return toInt(java.sql.Time.valueOf(str)); // 解析时间字符串并转换为Unix时间
  }

  // 私有辅助方法: 将SQL时间戳字符串转换为Unix时间戳
  // 使用java.sql.Timestamp.valueOf解析时间戳字符串
  // 然后调用toLong函数转换为Unix时间戳
  // 参数str: 时间戳字符串(格式: YYYY-MM-DD HH:MM:SS[.SSS])
  // 返回值: Unix时间戳(毫秒数)
  private long sqlTimestamp(String str) {
    return toLong(java.sql.Timestamp.valueOf(str)); // 解析时间戳字符串并转换为Unix时间戳
  }
}
