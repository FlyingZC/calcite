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
package org.apache.calcite.util; // 声明包名，该类属于 org.apache.calcite.util 包

import org.apache.calcite.util.format.FormatElement; // 导入 FormatElement 类，表示格式化元素
import org.apache.calcite.util.format.FormatModel; // 导入 FormatModel 类，表示格式化模型
import org.apache.calcite.util.format.FormatModels; // 导入 FormatModels 类，提供预定义的格式化模型

import org.hamcrest.Matcher; // 导入 Matcher 接口，用于断言匹配
import org.junit.jupiter.api.Test; // 导入 Test 注解，标记测试方法

import java.util.ArrayList; // 导入 ArrayList 类，用于动态数组
import java.util.List; // 导入 List 接口，表示列表

import static org.apache.calcite.test.Matchers.isListOf; // 导入 isListOf 静态方法，用于列表匹配

import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 静态方法，用于断言

/**
 * Unit test for {@link FormatModel}. // FormatModel 的单元测试类
 * 该类用于测试 FormatModel 的格式化解析功能，主要测试日期时间格式字符串的解析能力
 * FormatModel 是 Calcite 中用于解析和格式化日期时间字符串的核心组件
 * 测试覆盖了单个元素、多个元素、任意文本和别名文本的解析场景
 */
public class FormatModelTest { // 定义 FormatModelTest 测试类

  private void assertThatFormatElementParse(String formatString, // 定义私有辅助方法，用于断言格式化元素解析结果，参数 formatString 表示要解析的格式字符串
      Matcher<? super List<String>> matcher) { // 参数 matcher 表示期望的匹配器，用于验证解析结果
    List<FormatElement> elements = FormatModels.BIG_QUERY.parse(formatString); // 使用 BIG_QUERY 格式模型解析格式字符串，得到格式化元素列表
    List<String> stringResults = new ArrayList<>(); // 创建字符串列表，用于存储解析后的字符串结果
    for (FormatElement element : elements) { // 遍历每个格式化元素
      element.flatten(i -> stringResults.add(i.toString())); // 将格式化元素扁平化为字符串并添加到结果列表中，flatten 方法会递归处理嵌套元素
    }
    assertThat(stringResults, matcher); // 使用 Hamcrest 断言验证解析结果是否符合预期
  }

  @Test void testSingleElement() { // 测试方法，测试单个格式化元素的解析
    assertThatFormatElementParse("%j", isListOf("DDD")); // 测试 %j 格式符（一年中的第几天），期望解析为 "DDD"
  }

  @Test void testMultipleElements() { // 测试方法，测试多个格式化元素的解析
    assertThatFormatElementParse("%b-%d-%Y", // 测试多个格式化符的组合：月份缩写、日期、四位年份
        isListOf("Mon", "-", "DD", "-", "pctY")); // 期望解析结果为月份缩写 "Mon"、连字符 "-"、日期 "DD"、连字符 "-"、年份 "pctY"
  }

  @Test void testArbitraryText() { // 测试方法，测试包含任意文本的格式字符串解析
    assertThatFormatElementParse("%jtext%b", // 测试格式字符串中包含普通文本 "text"
        isListOf("DDD", "text", "Mon")); // 期望解析结果为 "DDD"、普通文本 "text"、月份缩写 "Mon"
  }

  @Test void testAliasText() { // 测试方法，测试别名格式符的解析
    assertThatFormatElementParse("%R", // 测试 %R 格式符（24小时制时间，等价于 %H:%M）
        isListOf("HH24", ":", "MI")); // 期望解析结果为小时 "HH24"、冒号 ":"、分钟 "MI"
  }
} // 类定义结束
