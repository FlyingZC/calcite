/*  Apache软件基金会许可证声明，包含版权信息和许可条款 */
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
package org.apache.calcite.adapter.file;  // 定义包名，该测试类位于file适配器包下

import org.junit.jupiter.api.Test;  // 导入JUnit 5的Test注解，用于标记测试方法

import java.math.BigDecimal;  // 导入BigDecimal类，用于精确的十进制数值处理

import static org.hamcrest.CoreMatchers.is;  // 导入Hamcrest的is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat;  // 导入Hamcrest的断言工具类
import static org.junit.jupiter.api.Assertions.assertThrows;  // 导入JUnit 5的assertThrows方法，用于验证异常抛出

/**
 * Test for the {@link CsvEnumerator}.  // CsvEnumerator测试类注释：用于测试CsvEnumerator类的功能，主要测试CSV文件枚举器中Decimal类型数值的解析功能
 */
@SuppressWarnings("SameParameterValue")  // 抑制警告：忽略参数未使用的警告（因为测试方法中某些参数可能被重复使用）
class CsvEnumeratorTest {  // CsvEnumeratorTest测试类定义，用于测试CsvEnumerator中的Decimal解析逻辑

  @Test void testParseDecimalScaleRounding() {  // 测试方法：测试Decimal类型数值的精度和舍入处理
    checkParse("123.45", 5, 2, "123.45");  // 测试用例1：验证正常小数"123.45"在精度5、小数位2时的解析结果为"123.45"（无需舍入）
    checkParse("123.455", 5, 2, "123.46");  // 测试用例2：验证小数"123.455"在精度5、小数位2时的解析结果为"123.46"（第三位小数5向上舍入）
    checkParse("-123.455", 5, 2, "-123.46");  // 测试用例3：验证负数"-123.455"在精度5、小数位2时的解析结果为"-123.46"（负数舍入规则：绝对值增大）
    checkParse("123.454", 5, 2, "123.45");  // 测试用例4：验证小数"123.454"在精度5、小数位2时的解析结果为"123.45"（第三位小数4向下舍入）
    checkParse("-123.454", 5, 2, "-123.45");  // 测试用例5：验证负数"-123.454"在精度5、小数位2时的解析结果为"-123.45"（负数舍入规则：绝对值减小）
  }

  private static void checkParse(String s, int precision, int scale,  // 私有静态辅助方法：用于验证CsvEnumerator.parseDecimal方法的解析结果，参数s为待解析的字符串，precision为精度（总位数），scale为小数位数，expected为期望的字符串结果
      String expected) {  // 参数expected：期望解析后的字符串表示
    assertThat(CsvEnumerator.parseDecimal(precision, scale, s),  // 断言：调用CsvEnumerator.parseDecimal方法解析字符串s，使用指定的精度和小数位数
        is(new BigDecimal(expected)));  // 验证解析结果是否等于期望的BigDecimal值（使用is匹配器比较）
  }

  @Test void testParseDecimalPrecisionExceeded() {  // 测试方法：测试Decimal类型数值精度超出限制时的异常处理
    checkThrows(4, 0, "1e+5");  // 测试用例1：验证科学计数法"1e+5"（即100000）在精度4、小数位0时抛出异常（超出精度限制）
    checkThrows(4, 0, "-1e+5");  // 测试用例2：验证负数科学计数法"-1e+5"（即-100000）在精度4、小数位0时抛出异常（超出精度限制）
    checkThrows(4, 0, "12345");  // 测试用例3：验证整数"12345"在精度4、小数位0时抛出异常（5位超出精度4）
    checkThrows(4, 0, "-12345");  // 测试用例4：验证负整数"-12345"在精度4、小数位0时抛出异常（5位超出精度4）
    checkThrows(4, 2, "123.45");  // 测试用例5：验证小数"123.45"在精度4、小数位2时抛出异常（整数部分3位+小数部分2位=5位超出精度4）
    checkThrows(4, 2, "-123.45");  // 测试用例6：验证负小数"-123.45"在精度4、小数位2时抛出异常（整数部分3位+小数部分2位=5位超出精度4）
  }

  private static void checkThrows(int precision, int scale, String s) {  // 私有静态辅助方法：用于验证CsvEnumerator.parseDecimal方法在精度超出限制时是否抛出IllegalArgumentException异常，参数precision为精度，scale为小数位数，s为待解析的字符串
    assertThrows(IllegalArgumentException.class,  // 断言：验证调用CsvEnumerator.parseDecimal方法时抛出IllegalArgumentException异常
        () -> CsvEnumerator.parseDecimal(precision, scale, s));  // 使用Lambda表达式调用parseDecimal方法，传入精度、小数位数和待解析字符串
  }
}  // CsvEnumeratorTest类结束