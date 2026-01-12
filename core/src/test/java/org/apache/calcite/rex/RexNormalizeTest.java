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
package org.apache.calcite.rex; // 定义包名，该类属于org.apache.calcite.rex包，用于处理Rex（Row Expression）相关的表达式测试

import org.hamcrest.core.IsNot; // 导入Hamcrest测试框架的IsNot匹配器，用于断言不相等的情况
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.equalTo; // 导入equalTo匹配器，用于断言相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat静态方法，用于编写断言语句

/** Test cases for {@link RexNormalize}. */
// RexNormalizeTest类：用于测试RexNormalize（Rex表达式规范化）功能的测试类
// RexNormalize是Calcite中用于规范化Rex表达式树的工具类，规范化可以确保相同的表达式具有相同的表示形式
// 规范化对于表达式缓存、等价性检查和优化非常重要
// 本测试类继承自RexProgramTestBase，提供了测试Rex表达式规范化所需的基础设施和辅助方法
// 测试内容包括：表达式摘要的规范化、可逆操作符的规范化、对称操作符的规范化等
class RexNormalizeTest extends RexProgramTestBase { // 测试类定义，继承RexProgramTestBase基类以获得测试辅助方法

  @Test void digestIsNormalized() { // 测试方法：验证表达式摘要（digest）是否被正确规范化
    // 摘要是表达式的字符串表示，规范化后相同的表达式应该产生相同的摘要
    // 测试AND和OR操作符的规范化：操作数应该按照某种规则排序
    assertNodeEquals( // 断言两个Rex节点相等（包括值和hashCode）
        and(or(vBool(1), vBool(0)), vBool(0)), // 第一个表达式：(vBool(1) OR vBool(0)) AND vBool(0)
        and(vBool(0), or(vBool(0), vBool(1)))); // 第二个表达式：vBool(0) AND (vBool(0) OR vBool(1))
    // 预期：这两个表达式在规范化后应该相等，因为AND和OR是可交换的，操作数会被排序

    assertNodeEquals( // 断言两个Rex节点相等
        and(or(vBool(1), vBool(0)), vBool(0)), // 第一个表达式：(vBool(1) OR vBool(0)) AND vBool(0)
        and(or(vBool(0), vBool(1)), vBool(0))); // 第二个表达式：(vBool(0) OR vBool(1)) AND vBool(0)
    // 预期：这两个表达式在规范化后应该相等，因为OR操作数会被排序

    assertNodeEquals( // 断言两个Rex节点相等
        eq(vVarchar(0), literal("0123456789012345")), // 第一个表达式：vVarchar(0) = "0123456789012345"
        eq(literal("0123456789012345"), vVarchar(0))); // 第二个表达式："0123456789012345" = vVarchar(0)
    // 预期：这两个表达式在规范化后应该相等，因为等号操作符的操作数会被排序

    assertNodeEquals( // 断言两个Rex节点相等
        eq(vVarchar(0), literal("01")), // 第一个表达式：vVarchar(0) = "01"
        eq(literal("01"), vVarchar(0))); // 第二个表达式："01" = vVarchar(0)
    // 预期：这两个表达式在规范化后应该相等，验证等号操作符的规范化
  }

  @Test void reversibleNormalizedToLess() { // 测试方法：验证可逆比较操作符是否被规范化为小于形式
    // 可逆操作符是指可以通过交换操作数并改变操作符来保持语义不变的操作符
    // 例如：a > b 等价于 b < a，规范化时统一转换为一种形式（如小于）
    // Same type operands. // 相同类型的操作数测试
    assertNodeEquals( // 断言两个Rex节点相等
        lt(vBool(0), vBool(0)), // 第一个表达式：vBool(0) < vBool(0)
        gt(vBool(0), vBool(0))); // 第二个表达式：vBool(0) > vBool(0)
    // 预期：这两个表达式在规范化后应该相等，因为gt(大于)应该被规范化为lt(小于)

    assertNodeEquals( // 断言两个Rex节点相等
        le(vBool(0), vBool(0)), // 第一个表达式：vBool(0) <= vBool(0)
        ge(vBool(0), vBool(0))); // 第二个表达式：vBool(0) >= vBool(0)
    // 预期：这两个表达式在规范化后应该相等，因为ge(大于等于)应该被规范化为le(小于等于)

    // Different type operands. // 不同类型的操作数测试
    assertNodeEquals( // 断言两个Rex节点相等
        lt(vSmallInt(0), vInt(1)), // 第一个表达式：vSmallInt(0) < vInt(1)
        gt(vInt(1), vSmallInt(0))); // 第二个表达式：vInt(1) > vSmallInt(0)
    // 预期：这两个表达式在规范化后应该相等，验证不同类型操作数的可逆规范化

    assertNodeEquals( // 断言两个Rex节点相等
        le(vSmallInt(0), vInt(1)), // 第一个表达式：vSmallInt(0) <= vInt(1)
        ge(vInt(1), vSmallInt(0))); // 第二个表达式：vInt(1) >= vSmallInt(0)
    // 预期：这两个表达式在规范化后应该相等，验证不同类型操作数的可逆规范化
  }

  @Test void reversibleDifferentArgTypesShouldNotBeShuffled() { // 测试方法：验证可逆但不同类型的操作符参数不应被重排
    // 某些可交换操作符（如加法、乘法）如果操作数类型不同，则不应该重排操作数
    // 因为类型转换可能会影响结果或语义
    assertNodeNotEqual( // 断言两个Rex节点不相等
        plus(vSmallInt(1), vInt(0)), // 第一个表达式：vSmallInt(1) + vInt(0)
        plus(vInt(0), vSmallInt(1))); // 第二个表达式：vInt(0) + vSmallInt(1)
    // 预期：这两个表达式在规范化后应该不相等，因为操作数类型不同，不应重排

    assertNodeNotEqual( // 断言两个Rex节点不相等
        mul(vSmallInt(0), vInt(1)), // 第一个表达式：vSmallInt(0) * vInt(1)
        mul(vInt(1), vSmallInt(0))); // 第二个表达式：vInt(1) * vSmallInt(0)
    // 预期：这两个表达式在规范化后应该不相等，验证乘法操作符的类型敏感性
  }

  @Test void reversibleDifferentNullabilityArgsAreNormalized() { // 测试方法：验证可逆操作符的不同可空性参数是否被规范化
    // 可空性是指字段是否可以为NULL，规范化时应该根据可空性对操作数进行排序
    // 不可为NULL的参数应该排在可为NULL的参数之前
    assertNodeEquals( // 断言两个Rex节点相等
        plus(vIntNotNull(0), vInt(1)), // 第一个表达式：vIntNotNull(0) + vInt(1)，第一个参数不可为空
        plus(vInt(1), vIntNotNull(0))); // 第二个表达式：vInt(1) + vIntNotNull(0)，第二个参数不可为空
    // 预期：这两个表达式在规范化后应该相等，不可为NULL的参数应该被排在前面

    assertNodeEquals( // 断言两个Rex节点相等
        mul(vIntNotNull(1), vInt(0)), // 第一个表达式：vIntNotNull(1) * vInt(0)，第一个参数不可为空
        mul(vInt(0), vIntNotNull(1))); // 第二个表达式：vInt(0) * vIntNotNull(1)，第二个参数不可为空
    // 预期：这两个表达式在规范化后应该相等，验证乘法操作符的可空性规范化
  }

  @Test void symmetricalDifferentArgOps() { // 测试方法：验证对称操作符的不同参数顺序是否被规范化
    // 对称操作符是指操作数顺序不影响结果的操作符（如等号、不等号、最大值、最小值）
    // 规范化时应该对操作数进行排序，确保相同语义的表达式具有相同的表示
    assertNodeEquals( // 断言两个Rex节点相等
        eq(vBool(0), vBool(1)), // 第一个表达式：vBool(0) = vBool(1)
        eq(vBool(1), vBool(0))); // 第二个表达式：vBool(1) = vBool(0)
    // 预期：这两个表达式在规范化后应该相等，等号操作符是完全对称的

    assertNodeEquals( // 断言两个Rex节点相等
        ne(vBool(0), vBool(1)), // 第一个表达式：vBool(0) <> vBool(1)（不等于）
        ne(vBool(1), vBool(0))); // 第二个表达式：vBool(1) <> vBool(0)（不等于）
    // 预期：这两个表达式在规范化后应该相等，不等号操作符是完全对称的

    assertNodeEquals( // 断言两个Rex节点相等
        greatest(vInt(0), vInt(1)), // 第一个表达式：GREATEST(vInt(0), vInt(1))，取最大值
        greatest(vInt(1), vInt(0))); // 第二个表达式：GREATEST(vInt(1), vInt(0))，取最大值
    // 预期：这两个表达式在规范化后应该相等，GREATEST函数是完全对称的

    assertNodeEquals( // 断言两个Rex节点相等
        least(vInt(0), vInt(1)), // 第一个表达式：LEAST(vInt(0), vInt(1))，取最小值
        least(vInt(1), vInt(0))); // 第二个表达式：LEAST(vInt(1), vInt(0))，取最小值
    // 预期：这两个表达式在规范化后应该相等，LEAST函数是完全对称的
  }

  @Test void reversibleDifferentArgOps() { // 测试方法：验证可逆但不对称的操作符的不同参数顺序是否不被规范化
    // 可逆但不对称的操作符（如小于、大于、小于等于、大于等于）不能简单地重排操作数
    // 因为重排操作数会改变表达式的语义
    assertNodeNotEqual( // 断言两个Rex节点不相等
        lt(vBool(0), vBool(1)), // 第一个表达式：vBool(0) < vBool(1)
        lt(vBool(1), vBool(0))); // 第二个表达式：vBool(1) < vBool(0)
    // 预期：这两个表达式在规范化后应该不相等，小于操作符不是对称的

    assertNodeNotEqual( // 断言两个Rex节点不相等
        le(vBool(0), vBool(1)), // 第一个表达式：vBool(0) <= vBool(1)
        le(vBool(1), vBool(0))); // 第二个表达式：vBool(1) <= vBool(0)
    // 预期：这两个表达式在规范化后应该不相等，小于等于操作符不是对称的

    assertNodeNotEqual( // 断言两个Rex节点不相等
        gt(vBool(0), vBool(1)), // 第一个表达式：vBool(0) > vBool(1)
        gt(vBool(1), vBool(0))); // 第二个表达式：vBool(1) > vBool(0)
    // 预期：这两个表达式在规范化后应该不相等，大于操作符不是对称的

    assertNodeNotEqual( // 断言两个Rex节点不相等
        ge(vBool(0), vBool(1)), // 第一个表达式：vBool(0) >= vBool(1)
        ge(vBool(1), vBool(0))); // 第二个表达式：vBool(1) >= vBool(0)
    // 预期：这两个表达式在规范化后应该不相等，大于等于操作符不是对称的
  }

  /** Asserts two rex nodes are equal. */
  // assertNodeEquals方法：断言两个Rex节点相等
  // 参数说明：
  //   - node1: 第一个Rex节点（表达式）
  //   - node2: 第二个Rex节点（表达式）
  // 功能：验证两个Rex节点不仅值相等，而且hashCode也相等
  // 用途：用于测试规范化后的表达式是否具有相同的表示形式
  private static void assertNodeEquals(RexNode node1, RexNode node2) { // 定义静态方法，接收两个Rex节点参数
    final String reason = getReason(node1, node2, true); // 获取断言原因字符串，true表示期望相等
    assertThat(reason, node1, equalTo(node2)); // 使用Hamcrest断言验证两个节点值相等
    assertThat(reason, node1.hashCode(), equalTo(node2.hashCode())); // 使用Hamcrest断言验证两个节点的hashCode相等
  }

  /** Asserts two rex nodes are not equal. */
  // assertNodeNotEqual方法：断言两个Rex节点不相等
  // 参数说明：
  //   - node1: 第一个Rex节点（表达式）
  //   - node2: 第二个Rex节点（表达式）
  // 功能：验证两个Rex节点不仅值不相等，而且hashCode也不相等
  // 用途：用于测试不应该被规范化的表达式是否保持不同的表示形式
  private static void assertNodeNotEqual(RexNode node1, RexNode node2) { // 定义静态方法，接收两个Rex节点参数
    final String reason = getReason(node1, node2, false); // 获取断言原因字符串，false表示期望不相等
    assertThat(reason, node1, IsNot.not(equalTo(node2))); // 使用Hamcrest断言验证两个节点值不相等
    assertThat(reason, node1.hashCode(), IsNot.not(equalTo(node2.hashCode()))); // 使用Hamcrest断言验证两个节点的hashCode不相等
  }

  /** Returns the assertion reason. */
  // getReason方法：生成断言原因字符串
  // 参数说明：
  //   - node1: 第一个Rex节点（表达式）
  //   - node2: 第二个Rex节点（表达式）
  //   - equal: 布尔值，true表示期望相等，false表示期望不相等
  // 返回值：格式化的断言原因字符串，包含两个节点的表示和期望结果
  // 用途：为断言失败时提供清晰的错误信息，帮助调试
  private static String getReason(RexNode node1, RexNode node2, boolean equal) { // 定义静态方法，接收两个Rex节点和一个布尔值参数
    StringBuilder reason = new StringBuilder("Rex nodes ["); // 创建StringBuilder，开始构建原因字符串
    reason.append(node1); // 追加第一个节点的字符串表示
    reason.append("] and ["); // 追加分隔符
    reason.append(node2); // 追加第二个节点的字符串表示
    reason.append("] expect to be "); // 追加期望描述前缀
    if (!equal) { // 如果期望不相等
      reason.append("not "); // 追加"not "前缀
    }
    reason.append("equal"); // 追加"equal"或"not equal"
    return reason.toString(); // 返回构建完成的原因字符串
  }
} // 类定义结束
