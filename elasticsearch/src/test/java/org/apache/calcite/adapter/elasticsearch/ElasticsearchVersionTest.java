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
// 声明包名，表示这个类属于org.apache.calcite.adapter.elasticsearch包，这是Calcite框架中Elasticsearch适配器的测试包
package org.apache.calcite.adapter.elasticsearch;

// 导入JUnit 5的Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入Java的Locale类，用于本地化相关的操作，这里用于格式化错误消息
import java.util.Locale;

// 静态导入ElasticsearchVersion类的fromString方法，用于将字符串转换为ElasticsearchVersion枚举值
import static org.apache.calcite.adapter.elasticsearch.ElasticsearchVersion.fromString;

// 静态导入Hamcrest的is匹配器，用于断言两个值相等
import static org.hamcrest.CoreMatchers.is;
// 静态导入Hamcrest的assertThat方法，用于编写可读性更强的断言
import static org.hamcrest.MatcherAssert.assertThat;
// 静态导入JUnit 5的fail方法，用于标记测试失败
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Basic tests for parsing Elasticsearch version in different formats.
 * // 类说明：这是ElasticsearchVersion类的测试类，用于测试不同格式的Elasticsearch版本字符串解析功能
 * // 该测试类验证了ElasticsearchVersion.fromString()方法能够正确识别和解析各种版本字符串格式
 * // 测试包括：有效的版本号格式（如2.3.4、5.6.1等）和无效的版本号格式（如空字符串、格式错误的版本号等）
 * // ElasticsearchVersion是一个枚举类型，定义了Elasticsearch的主要版本：ES2、ES5、ES6、ES7和UNKNOWN
 * // 这个测试类确保版本解析的健壮性和正确性，是Calcite适配器与Elasticsearch集成的重要基础
 */
class ElasticsearchVersionTest { // 测试类定义，使用默认访问修饰符（包级私有），因为测试类通常不需要被其他包访问

  // 测试方法：测试版本字符串解析功能，@Test注解标记这是一个JUnit测试方法
  // 该方法验证fromString()方法能够正确解析各种有效和无效的版本字符串
  @Test void versions() { // 测试方法开始，无返回值，方法名为versions表示测试多个版本
    // 断言：将字符串"2.3.4"解析为ElasticsearchVersion，期望结果是ES2枚举值
    // 这里测试的是Elasticsearch 2.x版本的解析，主版本号是2
    assertThat(ElasticsearchVersion.ES2, is(fromString("2.3.4"))); // 使用Hamcrest的assertThat进行断言，验证fromString("2.3.4")返回ES2
    // 断言：将字符串"2.0.0"解析为ElasticsearchVersion，期望结果是ES2枚举值
    // 测试2.x版本的最低版本号2.0.0，确保边界情况正确处理
    assertThat(ElasticsearchVersion.ES2, is(fromString("2.0.0"))); // 验证fromString("2.0.0")返回ES2，测试版本号的最小边界
    // 断言：将字符串"5.6.1"解析为ElasticsearchVersion，期望结果是ES5枚举值
    // 测试Elasticsearch 5.x版本的解析，主版本号是5
    assertThat(ElasticsearchVersion.ES5, is(fromString("5.6.1"))); // 验证fromString("5.6.1")返回ES5
    // 断言：将字符串"6.0.1"解析为ElasticsearchVersion，期望结果是ES6枚举值
    // 测试Elasticsearch 6.x版本的解析，主版本号是6
    assertThat(ElasticsearchVersion.ES6, is(fromString("6.0.1"))); // 验证fromString("6.0.1")返回ES6
    // 断言：将字符串"7.0.1"解析为ElasticsearchVersion，期望结果是ES7枚举值
    // 测试Elasticsearch 7.x版本的解析，主版本号是7
    assertThat(ElasticsearchVersion.ES7, is(fromString("7.0.1"))); // 验证fromString("7.0.1")返回ES7
    // 断言：将字符串"111.0.1"解析为ElasticsearchVersion，期望结果是UNKNOWN枚举值
    // 测试不支持的版本号（主版本号111），应该返回UNKNOWN，表示未知版本
    assertThat(ElasticsearchVersion.UNKNOWN, is(fromString("111.0.1"))); // 验证不支持的版本号返回UNKNOWN
    // 断言：将字符串"2020.12.12"解析为ElasticsearchVersion，期望结果是UNKNOWN枚举值
    // 测试未来或非标准的版本号格式，应该返回UNKNOWN
    assertThat(ElasticsearchVersion.UNKNOWN, is(fromString("2020.12.12"))); // 验证非标准版本号返回UNKNOWN

    // 以下测试用例验证无效的版本字符串格式，期望抛出IllegalArgumentException异常
    // 测试空字符串，应该抛出异常
    assertFails(""); // 调用辅助方法assertFails，验证空字符串会抛出异常
    // 测试只有一个点号，应该抛出异常
    assertFails("."); // 验证只有点号的格式会抛出异常
    // 测试以点号开头的版本号".1.2"，应该抛出异常
    assertFails(".1.2"); // 验证以点号开头的格式会抛出异常
    // 测试只有两个数字段的版本号"1.2"，应该抛出异常
    assertFails("1.2"); // 验证缺少第三个数字段的格式会抛出异常
    // 测试单个数字"0"，应该抛出异常
    assertFails("0"); // 验证单个数字会抛出异常
    // 测试单个字母"b"，应该抛出异常
    assertFails("b"); // 验证单个字母会抛出异常
    // 测试两个字母"a.b"，应该抛出异常
    assertFails("a.b"); // 验证字母格式会抛出异常
    // 测试两个字母"aa"，应该抛出异常
    assertFails("aa"); // 验证双字母会抛出异常
    // 测试三个字母"a.b.c"，应该抛出异常
    assertFails("a.b.c"); // 验证字母点号格式会抛出异常
    // 测试只有两个数字段的版本号"2.2"，应该抛出异常
    assertFails("2.2"); // 验证只有两个字段的数字格式会抛出异常
    // 测试字母和数字混合"a.2"，应该抛出异常
    assertFails("a.2"); // 验证字母数字混合格式会抛出异常
    // 测试包含字母后缀"2.2.0a"，应该抛出异常
    assertFails("2.2.0a"); // 验证包含字母后缀的格式会抛出异常
    // 测试字母前缀"2a.2.0"，应该抛出异常
    assertFails("2a.2.0"); // 验证字母前缀的格式会抛出异常
  } // 测试方法结束

  // 私有静态辅助方法：用于验证给定的版本字符串应该抛出IllegalArgumentException异常
  // 参数version：要测试的版本字符串
  // 该方法封装了异常测试的逻辑，使测试代码更加简洁和可读
  private static void assertFails(String version) { // 方法开始，参数version表示要测试的版本字符串
    try { // 开始try块，用于捕获可能抛出的异常
      fromString(version); // 调用fromString方法尝试解析版本字符串，如果格式不正确应该抛出异常
      // 如果执行到这里，说明没有抛出异常，测试失败
      // 使用fail()方法标记测试失败，并输出格式化的错误消息
      // Locale.ROOT确保错误消息使用固定的语言环境，避免国际化影响
      fail(String.format(Locale.ROOT, "Should fail for version %s", version)); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException ignore) { // 捕获IllegalArgumentException异常，这是预期的行为
      // expected：注释说明这个异常是预期的，忽略它
      // 使用ignore作为变量名，明确表示这个异常是被忽略的
      // expected 注释说明这个异常是预期的，忽略它
    } // catch块结束
  } // 方法结束
} // 类结束
