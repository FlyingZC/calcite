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
package org.apache.calcite.util; // 声明包名，该类属于org.apache.calcite.util包，提供测试工具类的测试用例

import org.apache.calcite.util.mapping.IntPair; // 导入IntPair类，用于表示整数对（源索引和目标索引）

import com.google.common.collect.ImmutableMap; // 导入Guava的ImmutableMap类，用于创建不可变的Map

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.Arrays; // 导入Arrays类，用于数组操作
import java.util.List; // 导入List接口，用于列表集合
import java.util.SortedSet; // 导入SortedSet接口，用于有序集合
import java.util.stream.Collectors; // 导入Collectors类，用于流式收集操作

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于断言相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的assertThat方法，用于断言
import static org.hamcrest.Matchers.empty; // 导入empty匹配器，用于断言集合为空
import static org.hamcrest.Matchers.emptyString; // 导入emptyString匹配器，用于断言字符串为空
import static org.hamcrest.Matchers.hasSize; // 导入hasSize匹配器，用于断言集合大小
import static org.hamcrest.Matchers.hasToString; // 导入hasToString匹配器，用于断言toString结果
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5的assertTrue方法，用于断言为真

/**
 * Tests for TestUtil. // TestUtil类的测试用例，用于验证TestUtil工具类中各种方法的正确性
 * 该测试类覆盖了TestUtil类中的主要功能，包括：
 * 1. Java版本检测和解析
 * 2. 版本号字符串的解析和比较
 * 3. Guava库版本检测
 * 4. 浮点数修正处理
 * 5. 列表乱序检测
 * 6. 字符串重复生成
 */
class TestUtilTest { // 定义TestUtilTest测试类，用于测试TestUtil工具类

  @Test void javaMajorVersionExceeds6() { // 测试方法：验证当前Java主版本号是否大于6
    // shouldn't throw any exceptions (for current JDK) // 不应该抛出任何异常（对于当前JDK）
    int majorVersion = TestUtil.getJavaMajorVersion(); // 调用TestUtil.getJavaMajorVersion()获取当前Java主版本号
    assertTrue(majorVersion > 6, // 断言主版本号大于6
        "current JavaMajorVersion == " + majorVersion + " is expected to exceed 6"); // 如果断言失败，显示此错误消息
  }

  @Test void majorVersionFromString() { // 测试方法：验证从版本字符串中提取主版本号的功能
    testJavaVersion(4, "1.4.2_03"); // 测试Java 1.4版本字符串，期望主版本号为4
    testJavaVersion(5, "1.5.0_16"); // 测试Java 1.5版本字符串，期望主版本号为5
    testJavaVersion(6, "1.6.0_22"); // 测试Java 1.6版本字符串，期望主版本号为6
    testJavaVersion(7, "1.7.0_65-b20"); // 测试Java 1.7版本字符串（带构建号），期望主版本号为7
    testJavaVersion(8, "1.8.0_72-internal"); // 测试Java 1.8内部版本字符串，期望主版本号为8
    testJavaVersion(8, "1.8.0_151"); // 测试Java 1.8更新版本字符串，期望主版本号为8
    testJavaVersion(8, "1.8.0_141"); // 测试Java 1.8另一个更新版本字符串，期望主版本号为8
    testJavaVersion(9, "1.9.0_20-b62"); // 测试Java 1.9版本字符串（带构建号），期望主版本号为9
    testJavaVersion(9, "1.9.0-ea-b19"); // 测试Java 1.9早期访问版本字符串，期望主版本号为9
    testJavaVersion(9, "9"); // 测试Java 9简化版本字符串，期望主版本号为9
    testJavaVersion(9, "9.0"); // 测试Java 9带次版本号的字符串，期望主版本号为9
    testJavaVersion(9, "9.0.1"); // 测试Java 9带修订号的字符串，期望主版本号为9
    testJavaVersion(9, "9-ea"); // 测试Java 9早期访问版本字符串，期望主版本号为9
    testJavaVersion(9, "9.0.1"); // 测试Java 9.0.1版本字符串，期望主版本号为9
    testJavaVersion(9, "9.1-ea"); // 测试Java 9.1早期访问版本字符串，期望主版本号为9
    testJavaVersion(9, "9.1.1-ea"); // 测试Java 9.1.1早期访问版本字符串，期望主版本号为9
    testJavaVersion(9, "9.1.1-ea+123"); // 测试Java 9.1.1早期访问版本字符串（带补丁号），期望主版本号为9
    testJavaVersion(10, "10"); // 测试Java 10简化版本字符串，期望主版本号为10
    testJavaVersion(10, "10+456"); // 测试Java 10带补丁号的版本字符串，期望主版本号为10
    testJavaVersion(10, "10-ea"); // 测试Java 10早期访问版本字符串，期望主版本号为10
    testJavaVersion(10, "10-ea42"); // 测试Java 10早期访问版本字符串（带编号），期望主版本号为10
    testJavaVersion(10, "10-ea+555"); // 测试Java 10早期访问版本字符串（带补丁号），期望主版本号为10
    testJavaVersion(10, "10-ea42+555"); // 测试Java 10早期访问版本字符串（带编号和补丁号），期望主版本号为10
    testJavaVersion(10, "10.0"); // 测试Java 10.0版本字符串，期望主版本号为10
    testJavaVersion(10, "10.0.0"); // 测试Java 10.0.0版本字符串，期望主版本号为10
    testJavaVersion(10, "10.0.0.0.0"); // 测试Java 10带多个零的版本字符串，期望主版本号为10
    testJavaVersion(10, "10.1.2.3.4.5.6.7.8"); // 测试Java 10带多级版本号的字符串，期望主版本号为10
    testJavaVersion(10, "10.0.1"); // 测试Java 10.0.1版本字符串，期望主版本号为10
    testJavaVersion(10, "10.1.1-foo"); // 测试Java 10.1.1带后缀的版本字符串，期望主版本号为10
    testJavaVersion(11, "11"); // 测试Java 11简化版本字符串，期望主版本号为11
    testJavaVersion(11, "11+111"); // 测试Java 11带补丁号的版本字符串，期望主版本号为11
    testJavaVersion(11, "11-ea"); // 测试Java 11早期访问版本字符串，期望主版本号为11
    testJavaVersion(11, "11.0"); // 测试Java 11.0版本字符串，期望主版本号为11
    testJavaVersion(12, "12.0"); // 测试Java 12.0版本字符串，期望主版本号为12
    testJavaVersion(20, "20.0"); // 测试Java 20.0版本字符串，期望主版本号为20
    testJavaVersion(42, "42"); // 测试Java 42简化版本字符串，期望主版本号为42（用于测试大版本号）
    testJavaVersion(100, "100"); // 测试Java 100简化版本字符串，期望主版本号为100（用于测试三位数版本号）
    testJavaVersion(100, "100.0"); // 测试Java 100.0版本字符串，期望主版本号为100
    testJavaVersion(1000, "1000"); // 测试Java 1000简化版本字符串，期望主版本号为1000（用于测试四位数版本号）
    testJavaVersion(2000, "2000"); // 测试Java 2000简化版本字符串，期望主版本号为2000
    testJavaVersion(205, "205.0"); // 测试Java 205.0版本字符串，期望主版本号为205
    testJavaVersion(2017, "2017"); // 测试Java 2017简化版本字符串，期望主版本号为2017
    testJavaVersion(2017, "2017.0"); // 测试Java 2017.0版本字符串，期望主版本号为2017
    testJavaVersion(2017, "2017.12"); // 测试Java 2017.12版本字符串，期望主版本号为2017
    testJavaVersion(2017, "2017.12-pre"); // 测试Java 2017.12-pre预发布版本字符串，期望主版本号为2017
    testJavaVersion(2017, "2017.12.31"); // 测试Java 2017.12.31版本字符串，期望主版本号为2017
  }

  private void testJavaVersion(int expectedMajorVersion, String versionString) { // 私有辅助方法：测试从版本字符串提取主版本号的功能
    assertThat(versionString, TestUtil.majorVersionFromString(versionString), // 断言从versionString提取的主版本号等于expectedMajorVersion
        is(expectedMajorVersion)); // 使用is匹配器验证期望值
  }

  /** Unit test for {@link Version}. */ // Version类的单元测试
  @SuppressWarnings("EqualsWithItself") // 抑制警告：与自身比较（用于测试compareTo方法）
  @Test void testVersion() { // 测试方法：验证Version类的功能，包括版本号解析和比较
    Version vEmpty = Version.of(""); // 创建空版本号对象
    assertThat(vEmpty.integers, empty()); // 断言空版本号的整数列表为空
    assertThat(vEmpty.string, emptyString()); // 断言空版本号的字符串为空字符串

    final Version v1 = Version.of("1"); // 创建版本号为"1"的Version对象
    assertThat(v1.integers, hasSize(1)); // 断言整数列表大小为1
    assertThat(v1.integers, hasToString("[1]")); // 断言整数列表转换为字符串为"[1]"

    final Version v1_8_3 = Version.of("1.8.3-jre"); // 创建版本号为"1.8.3-jre"的Version对象（"-jre"后缀会被忽略）
    assertThat(v1_8_3.integers, hasSize(3)); // 断言整数列表大小为3（1, 8, 3）
    assertThat(v1_8_3.integers, hasToString("[1, 8, 3]")); // 断言整数列表转换为字符串为"[1, 8, 3]"
    assertThat(v1_8_3.string, is("1.8.3-jre")); // 断言原始字符串保持不变（包含"-jre"后缀）

    final Version v1_19 = Version.of("1.19"); // 创建版本号为"1.19"的Version对象
    assertThat(v1_19.integers, hasSize(2)); // 断言整数列表大小为2（1, 19）
    assertThat(v1_19.integers, hasToString("[1, 19]")); // 断言整数列表转换为字符串为"[1, 19]"

    final Version v1_23 = Version.of("1.23"); // 创建版本号为"1.23"的Version对象
    assertThat(v1_23.integers, hasSize(2)); // 断言整数列表大小为2（1, 23）
    assertThat(v1_23.integers, hasToString("[1, 23]")); // 断言整数列表转换为字符串为"[1, 23]"

    final Version v1_23_0 = Version.of("1.23.0"); // 创建版本号为"1.23.0"的Version对象
    assertThat(v1_23_0.integers, hasSize(3)); // 断言整数列表大小为3（1, 23, 0）
    assertThat(v1_23_0.integers, hasToString("[1, 23, 0]")); // 断言整数列表转换为字符串为"[1, 23, 0]"

    final Version v1_23_1 = Version.of("1.23.1"); // 创建版本号为"1.23.1"的Version对象
    assertThat(v1_23_1.integers, hasSize(3)); // 断言整数列表大小为3（1, 23, 1）
    assertThat(v1_23_1.integers, hasToString("[1, 23, 1]")); // 断言整数列表转换为字符串为"[1, 23, 1]"

    // 1 < 1.8.3 < 1.19 < 1.23 < 1.23.0 < 1.23.1 // 版本号比较顺序说明
    assertThat(vEmpty.compareTo(v1), is(-1)); // 断言空版本号小于版本1
    assertThat(v1.compareTo(v1_23), is(-1)); // 断言版本1小于版本1.23
    assertThat(v1_8_3.compareTo(v1_19), is(-1)); // 断言版本1.8.3小于版本1.19
    assertThat(v1_19.compareTo(v1_23), is(-1)); // 断言版本1.19小于版本1.23
    assertThat(v1_23.compareTo(v1_23_0), is(-1)); // 断言版本1.23小于版本1.23.0（因为1.23.0有更多版本号部分）
    assertThat(v1_23_0.compareTo(v1_23_1), is(-1)); // 断言版本1.23.0小于版本1.23.1
    assertThat(v1_23_1.compareTo(v1), is(1)); // 断言版本1.23.1大于版本1

    assertThat(v1.compareTo(v1), is(0)); // 断言版本1与自身比较返回0（相等）
  }

  @Test void testGuavaMajorVersion() { // 测试方法：验证获取Guava库主版本号的功能
    int majorVersion = TestUtil.getGuavaMajorVersion(); // 调用TestUtil.getGuavaMajorVersion()获取当前Guava库的主版本号
    assertTrue(majorVersion >= 2, // 断言主版本号大于等于2
        "current GuavaMajorVersion is " + majorVersion + "; should exceed 2"); // 如果断言失败，显示此错误消息
  }

  /** Tests {@link TestUtil#correctRoundedFloat(String)}. */ // 测试TestUtil.correctRoundedFloat方法
  @Test void testCorrectRoundedFloat() { // 测试方法：验证修正浮点数四舍五入结果的功能
    // unchanged; no '.' // 不改变；没有小数点
    assertThat(TestUtil.correctRoundedFloat("1230000006"), is("1230000006")); // 断言整数字符串不变（无小数点）
    assertThat(TestUtil.correctRoundedFloat("12.300000006"), is("12.3")); // 断言去除多余的零
    assertThat(TestUtil.correctRoundedFloat("53.742500000000014"), // 断言去除多余的零并修正精度
        is("53.7425")); // 期望结果为"53.7425"
    // unchanged; too few zeros // 不改变；零太少
    assertThat(TestUtil.correctRoundedFloat("12.30006"), is("12.30006")); // 断言零太少时保持不变
    assertThat(TestUtil.correctRoundedFloat("12.300000"), is("12.3")); // 断言去除末尾多个零
    assertThat(TestUtil.correctRoundedFloat("-12.30000006"), is("-12.3")); // 断言负数的修正
    assertThat(TestUtil.correctRoundedFloat("-12.349999991"), is("-12.35")); // 断言负数999序列的修正（进位）
    assertThat(TestUtil.correctRoundedFloat("-12.349999999"), is("-12.35")); // 断言负数999序列的修正（进位）
    assertThat(TestUtil.correctRoundedFloat("-12.3499999911"), is("-12.35")); // 断言负数999序列的修正（进位）
    // unchanged; too many non-nines at the end // 不改变；末尾非9数字太多
    assertThat(TestUtil.correctRoundedFloat("-12.34999999118"), // 断言末尾非9数字太多时保持不变
        is("-12.34999999118")); // 期望结果保持不变
    // unchanged; too few nines // 不改变；9太少
    assertThat(TestUtil.correctRoundedFloat("-12.349991"), is("-12.349991")); // 断言9太少时保持不变
    assertThat(TestUtil.correctRoundedFloat("95637.41489999992"), // 断言大数的999序列修正
        is("95637.4149")); // 期望结果为"95637.4149"
    assertThat(TestUtil.correctRoundedFloat("14181.569999999989"), // 断言大数的999序列修正
        is("14181.57")); // 期望结果为"14181.57"
    // can't handle nines that start right after the point very well. oh well. // 无法很好处理小数点后立即开始的9序列
    assertThat(TestUtil.correctRoundedFloat("12.999999"), is("12.")); // 断言小数点后全是9的情况处理（结果为"12."）
  }

  /** Tests {@link TestUtil#outOfOrderItems(List)}. */ // 测试TestUtil.outOfOrderItems方法
  @Test void testOutOfOrderItems() { // 测试方法：验证检测列表中乱序元素的功能
    final List<String> list = // 创建字符串列表，包含乱序的元素
        new ArrayList<>(Arrays.asList("a", "g", "b", "c", "e", "d")); // 初始列表：a, g, b, c, e, d
    final SortedSet<String> distance = TestUtil.outOfOrderItems(list); // 检测列表中的乱序元素
    assertThat(distance, hasToString("[b, d]")); // 断言乱序元素为b和d（它们应该出现在更早的位置）

    list.add("f"); // 在列表末尾添加"f"
    final SortedSet<String> distance2 = TestUtil.outOfOrderItems(list); // 再次检测乱序元素
    assertThat(distance2, hasToString("[b, d]")); // 断言乱序元素仍为b和d（f在正确位置）

    list.add(1, "b"); // 在索引1处插入"b"
    final SortedSet<String> distance3 = TestUtil.outOfOrderItems(list); // 再次检测乱序元素
    assertThat(distance3, hasToString("[b, d]")); // 断言乱序元素仍为b和d（重复的b不影响）

    list.add(1, "c"); // 在索引1处插入"c"
    final SortedSet<String> distance4 = TestUtil.outOfOrderItems(list); // 再次检测乱序元素
    assertThat(distance4, hasToString("[b, d]")); // 断言乱序元素仍为b和d（重复的c不影响）

    list.add(0, "z"); // 在列表开头添加"z"
    final SortedSet<String> distance5 = TestUtil.outOfOrderItems(list); // 再次检测乱序元素
    assertThat(distance5, hasToString("[a, b, d]")); // 断言乱序元素为a, b, d（z在开头导致a也变成乱序）
  }

  private long totalDistance(ImmutableMap<String, IntPair> map) { // 私有辅助方法：计算映射中所有IntPair的target值总和
    return map.entrySet().stream() // 将映射的条目转换为流
        .collect(Collectors.summarizingInt(e -> e.getValue().target)).getSum(); // 收集每个IntPair的target值并求和
  }

  /** Tests {@link TestUtil#repeat(String, int)}. */ // 测试TestUtil.repeat方法
  @Test void testRepeat() { // 测试方法：验证重复字符串的功能
    final CharSequence a3 = TestUtil.repeat("a", 3); // 将字符"a"重复3次
    assertThat(a3, hasToString("aaa")); // 断言结果为"aaa"
    final CharSequence ab0 = TestUtil.repeat("ab", 0); // 将字符串"ab"重复0次
    assertThat(ab0, hasToString("")); // 断言结果为空字符串
    final CharSequence ab3 = TestUtil.repeat("ab", 3); // 将字符串"ab"重复3次
    assertThat(ab3.length(), is(6)); // 断言结果长度为6（ab * 3 = 6）
    assertThat(ab3.subSequence(2, 4), hasToString("ab")); // 断言子序列[2,4)为"ab"
    assertThat(ab3.subSequence(3, 5), hasToString("ba")); // 断言子序列[3,5)为"ba"
    assertThat(ab3.subSequence(3, 6), hasToString("bab")); // 断言子序列[3,6)为"bab"
  }
} // 类定义结束
