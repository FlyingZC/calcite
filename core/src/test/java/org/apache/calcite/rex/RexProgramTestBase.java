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
package org.apache.calcite.rex; // 定义包名，该类属于 org.apache.calcite.rex 包，用于处理关系表达式(Relational Expression)

import org.apache.calcite.plan.RelOptPredicateList; // 导入 RelOptPredicateList 类，用于表示谓词列表
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入 RelDataTypeImpl 类，用于关系数据类型的实现
import org.apache.calcite.sql.SqlKind; // 导入 SqlKind 类，表示 SQL 操作符的类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 类，表示 SQL 类型的名称
import org.apache.calcite.test.Matchers; // 导入 Matchers 类，提供测试匹配器

import com.google.common.collect.ImmutableMap; // 导入 ImmutableMap 类，用于创建不可变映射

import org.hamcrest.Matcher; // 导入 Matcher 接口，用于测试断言匹配

import static org.hamcrest.CoreMatchers.is; // 导入 is 静态方法，用于测试相等性
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 静态方法，用于测试断言
import static org.hamcrest.Matchers.hasToString; // 导入 hasToString 静态方法，用于测试字符串匹配

import static java.util.Objects.requireNonNull; // 导入 requireNonNull 静态方法，用于检查对象非空

/** Base class for tests of {@link RexProgram}. */ // 类注释：这是 RexProgram 测试的基类
// RexProgram 是 Calcite 中表示关系表达式程序的核心类，包含输入字段、表达式、输出字段、条件等
// 该测试基类提供了丰富的辅助方法来测试 RexProgram 的各种功能，包括表达式简化、CNF 转换、因子提取等
class RexProgramTestBase extends RexProgramBuilderBase { // 定义 RexProgramTestBase 类，继承自 RexProgramBuilderBase

  protected Node node(RexNode node) { // 定义 node 方法，创建一个 Node 对象用于流式测试
    return new Node(rexBuilder, node); // 返回新的 Node 对象，包含 rexBuilder 和传入的 RexNode
  }

  protected void checkCnf(RexNode node, String expected) { // 定义 checkCnf 方法，检查表达式转换为合取范式(CNF)后的结果是否符合预期
    assertThat("RexUtil.toCnf(rexBuilder, " + node + ")", // 断言消息，说明正在测试 RexUtil.toCnf 方法
        RexUtil.toCnf(rexBuilder, node), hasToString(expected)); // 调用 RexUtil.toCnf 方法将表达式转换为 CNF，并验证结果字符串是否符合预期
  }

  protected void checkThresholdCnf(RexNode node, int threshold, String expected) { // 定义 checkThresholdCnf 方法，检查带阈值的 CNF 转换
    assertThat("RexUtil.toCnf(rexBuilder, threshold=" + threshold + " , " + node + ")", // 断言消息，包含阈值参数
        RexUtil.toCnf(rexBuilder, threshold, node), // 调用 RexUtil.toCnf 方法，传入阈值参数，避免表达式过度展开
        hasToString(expected)); // 验证结果字符串是否符合预期
  }

  protected void checkPullFactorsUnchanged(RexNode node) { // 定义 checkPullFactorsUnchanged 方法，检查因子提取后表达式是否保持不变
    checkPullFactors(node, node.toString()); // 调用 checkPullFactors 方法，预期结果与原始表达式相同
  }

  protected void checkPullFactors(RexNode node, String expected) { // 定义 checkPullFactors 方法，检查因子提取后的结果是否符合预期
    assertThat("RexUtil.pullFactors(rexBuilder, " + node + ")", // 断言消息，说明正在测试 RexUtil.pullFactors 方法
        RexUtil.pullFactors(rexBuilder, node), // 调用 RexUtil.pullFactors 方法提取表达式的公共因子
        hasToString(expected)); // 验证结果字符串是否符合预期
  }

  /**
   * Asserts that a given node has expected string representation with account
   * of node type.
   *
   * @param message extra message that clarifies where the node came from
   * @param expected expected string representation of the node
   * @param node node to check
   */
  protected void assertNode(String message, String expected, RexNode node) { // 定义 assertNode 方法，断言节点的字符串表示符合预期
    String actual; // 声明实际字符串变量
    if (node.isA(SqlKind.CAST) || node.isA(SqlKind.NEW_SPECIFICATION)) { // 如果节点是类型转换或新规范操作
      // toString contains type (see RexCall.toString)
      actual = node.toString(); // 直接使用节点的 toString 方法，因为它已经包含类型信息
    } else { // 对于其他类型的节点
      actual = node + ":" + node.getType() + (node.getType().isNullable() ? "" // 构建字符串，包含节点、类型和可空性标记
          : RelDataTypeImpl.NON_NULLABLE_SUFFIX); // 如果不可空，添加非空后缀
    }
    assertThat(message, actual, is(expected)); // 断言实际字符串与预期字符串相等
  }

  /** Simplifies an expression and checks that the result is as expected. */ // 方法注释：简化表达式并检查结果是否符合预期
  protected SimplifiedNode checkSimplify(RexNode node, String expected) { // 定义 checkSimplify 方法，简化表达式并验证结果
    final String nodeString = node.toString(); // 获取原始节点的字符串表示
    if (expected.equals(nodeString)) { // 如果预期结果与原始节点相同
      throw new AssertionError("expected == node.toString(); " // 抛出断言错误，提示应该使用 checkSimplifyUnchanged 方法
          + "use checkSimplifyUnchanged"); // 错误消息建议使用 checkSimplifyUnchanged 方法
    }
    return checkSimplify3_(node, expected, expected, expected); // 调用 checkSimplify3_ 方法，三个预期值相同
  }

  /** Simplifies an expression and checks that the result is unchanged. */ // 方法注释：简化表达式并检查结果是否保持不变
  protected void checkSimplifyUnchanged(RexNode node) { // 定义 checkSimplifyUnchanged 方法，验证简化后表达式不变
    final String expected = node.toString(); // 预期结果就是原始节点的字符串表示
    checkSimplify3_(node, expected, expected, expected); // 调用 checkSimplify3_ 方法，三个预期值都相同
  }

  /** Simplifies an expression and checks the result if unknowns remain
   * unknown, or if unknown becomes false. If the result is the same, use
   * {@link #checkSimplify(RexNode, String)}.
   *
   * @param node Expression to simplify
   * @param expected Expected simplification
   * @param expectedFalse Expected simplification, if unknown is to be treated
   *     as false
   */
  protected void checkSimplify2(RexNode node, String expected, // 定义 checkSimplify2 方法，简化表达式并检查两种情况
      String expectedFalse) { // 参数：节点、预期结果、将未知值视为 false 时的预期结果
    checkSimplify3_(node, expected, expectedFalse, expected); // 调用 checkSimplify3_ 方法，第三个参数与第一个相同
    if (expected.equals(expectedFalse)) { // 如果预期结果与 false 情况相同
      throw new AssertionError("expected == expectedFalse; use checkSimplify"); // 抛出错误，提示应该使用 checkSimplify 方法
    }
  }

  protected void checkSimplify3(RexNode node, String expected, // 定义 checkSimplify3 方法，简化表达式并检查三种情况
      String expectedFalse, String expectedTrue) { // 参数：节点、预期结果、将未知值视为 false 时的预期、将未知值视为 true 时的预期
    checkSimplify3_(node, expected, expectedFalse, expectedTrue); // 调用 checkSimplify3_ 方法执行实际检查
    if (expected.equals(expectedFalse) && expected.equals(expectedTrue)) { // 如果三个预期值都相同
      throw new AssertionError("expected == expectedFalse == expectedTrue; " // 抛出错误，提示应该使用 checkSimplify 方法
          + "use checkSimplify"); // 错误消息建议使用 checkSimplify 方法
    }
    if (expected.equals(expectedTrue)) { // 如果预期结果与 true 情况相同
      throw new AssertionError("expected == expectedTrue; use checkSimplify2"); // 抛出错误，提示应该使用 checkSimplify2 方法
    }
  }

  protected SimplifiedNode checkSimplify3_(RexNode node, String expected, // 定义 checkSimplify3_ 私有方法，实际执行简化检查
      String expectedFalse, String expectedTrue) { // 参数：节点、三种不同情况下的预期结果
    final RexNode simplified = // 声明简化后的节点变量
        checkSimplifyAs(node, RexUnknownAs.UNKNOWN, is(expected)); // 首先检查将未知值视为 UNKNOWN 时的简化结果
    if (node.getType().getSqlTypeName() == SqlTypeName.BOOLEAN) { // 如果节点类型是布尔型
      checkSimplifyAs(node, RexUnknownAs.FALSE, is(expectedFalse)); // 检查将未知值视为 FALSE 时的简化结果
      checkSimplifyAs(node, RexUnknownAs.TRUE, is(expectedTrue)); // 检查将未知值视为 TRUE 时的简化结果
    } else { // 如果节点类型不是布尔型
      assertThat("node type is not BOOLEAN, so <<expectedFalse>> should match <<expected>>", // 断言消息
          expectedFalse, is(expected)); // 验证 false 情况应该与预期结果相同
      assertThat("node type is not BOOLEAN, so <<expectedTrue>> should match <<expected>>", // 断言消息
          expectedTrue, is(expected)); // 验证 true 情况应该与预期结果相同
    }
    return new SimplifiedNode(rexBuilder, node, simplified); // 返回 SimplifiedNode 对象，包含原始节点和简化后的节点
  }

  private RexNode checkSimplifyAs(RexNode node, RexUnknownAs unknownAs, // 定义 checkSimplifyAs 私有方法，按指定方式处理未知值进行简化
      Matcher<String> matcher) { // 参数：节点、未知值处理方式、字符串匹配器
    final RexNode simplified = // 声明简化后的节点变量
        simplify.simplifyUnknownAs(node, unknownAs); // 调用 RexSimplify 的 simplifyUnknownAs 方法进行简化
    assertThat(("simplify(unknown as " + unknownAs + "): ") + node, // 断言消息，包含未知值处理方式和节点
        simplified, hasToString(matcher)); // 验证简化后的节点字符串是否符合匹配器
    return simplified; // 返回简化后的节点
  }

  protected void checkSimplifyFilter(RexNode node, String expected) { // 定义 checkSimplifyFilter 方法，检查作为过滤条件的表达式简化
    checkSimplifyAs(node, RexUnknownAs.FALSE, is(expected)); // 调用 checkSimplifyAs，将未知值视为 FALSE（过滤条件中未知值通常视为 false）
  }

  protected void checkSimplifyFilter(RexNode node, // 定义 checkSimplifyFilter 重载方法，带谓词列表的过滤条件简化检查
      RelOptPredicateList predicates, String expected) { // 参数：节点、谓词列表、预期结果
    checkSimplifyWithPredicates(node, predicates, RexUnknownAs.FALSE, expected); // 调用 checkSimplifyWithPredicates 方法
  }

  protected void checkSimplifyWithPredicates(RexNode node, // 定义 checkSimplifyWithPredicates 方法，使用谓词列表进行简化
      RelOptPredicateList predicates, RexUnknownAs unknownAs, String expected) { // 参数：节点、谓词列表、未知值处理方式、预期结果
    final RexNode simplified = // 声明简化后的节点变量
        simplify.withPredicates(predicates) // 将谓词列表添加到简化器中
            .simplifyUnknownAs(node, unknownAs); // 使用指定的未知值处理方式进行简化
    assertThat(simplified, hasToString(expected)); // 验证简化后的节点字符串是否符合预期
  }

  /** Checks that {@link RexNode#isAlwaysTrue()},
   * {@link RexNode#isAlwaysTrue()} and {@link RexSimplify} agree that
   * an expression reduces to true or false. */ // 方法注释：检查表达式的常量性判断和简化结果是否一致
  protected void checkIs(RexNode e, boolean expected) { // 定义 checkIs 方法，检查表达式是否总是为 true 或 false
    assertThat("isAlwaysTrue() of expression: " + e, // 断言消息
        e.isAlwaysTrue(), is(expected)); // 验证 isAlwaysTrue 方法返回值是否符合预期
    assertThat("isAlwaysFalse() of expression: " + e, // 断言消息
        e.isAlwaysFalse(), is(!expected)); // 验证 isAlwaysFalse 方法返回值是否与预期相反
    assertThat("Simplification is not using isAlwaysX information", // 断言消息
        simplify(e), hasToString(expected ? "true" : "false")); // 验证简化结果是否为 "true" 或 "false"
  }

  protected Comparable eval(RexNode e) { // 定义 eval 方法，计算表达式的值
    return RexInterpreter.evaluate(e, ImmutableMap.of()); // 使用 RexInterpreter 解释执行表达式，传入空映射
  }

  protected RexNode simplify(RexNode e) { // 定义 simplify 方法，简化表达式
    final RexSimplify simplify = // 声明 RexSimplify 对象
        new RexSimplify(rexBuilder, RelOptPredicateList.EMPTY, RexUtil.EXECUTOR) // 创建 RexSimplify 实例，使用空谓词列表和执行器
            .withParanoid(true); // 启用偏执模式，进行更严格的简化
    return simplify.simplifyUnknownAs(e, RexUnknownAs.UNKNOWN); // 调用 simplifyUnknownAs 方法，将未知值视为 UNKNOWN 进行简化
  }

  /** Fluent test. */ // 类注释：流式测试类
  static class Node { // 定义 Node 静态内部类，用于流式测试
    final RexBuilder rexBuilder; // 成员变量：RexBuilder 对象，用于构建关系表达式
    final RexNode node; // 成员变量：RexNode 对象，表示关系表达式节点

    Node(RexBuilder rexBuilder, RexNode node) { // Node 类的构造方法
      this.rexBuilder = requireNonNull(rexBuilder, "rexBuilder"); // 初始化 rexBuilder，检查非空
      this.node = requireNonNull(node, "node"); // 初始化 node，检查非空
    }
  }

  /** Fluent test that includes original and simplified expression. */ // 类注释：包含原始表达式和简化后表达式的流式测试类
  static class SimplifiedNode extends Node { // 定义 SimplifiedNode 静态内部类，继承自 Node
    private final RexNode simplified; // 成员变量：简化后的 RexNode 对象

    SimplifiedNode(RexBuilder rexBuilder, RexNode node, RexNode simplified) { // SimplifiedNode 类的构造方法
      super(rexBuilder, node); // 调用父类 Node 的构造方法
      this.simplified = simplified; // 初始化 simplified 成员变量
    }

    /** Asserts that the result of expanding calls to {@code SEARCH} operator
     * in the simplified expression yields an expected {@link RexNode}. */ // 方法注释：断言展开 SEARCH 操作符后的结果符合预期
    public Node expandedSearch(Matcher<RexNode> matcher) { // 定义 expandedSearch 方法，使用匹配器检查展开结果
      final RexNode node2 = RexUtil.expandSearch(rexBuilder, null, simplified); // 调用 RexUtil.expandSearch 方法展开 SEARCH 操作符
      assertThat(node2, matcher); // 断言展开后的节点符合匹配器
      return this; // 返回 this，支持链式调用
    }

    /** Asserts that the result of expanding calls to {@code SEARCH} operator
     * in the simplified expression yields a {@link RexNode}
     * with a given string representation. */ // 方法注释：断言展开 SEARCH 操作符后的结果字符串表示符合预期
    public Node expandedSearch(String expected) { // 定义 expandedSearch 重载方法，使用字符串检查展开结果
      return expandedSearch(Matchers.hasRex(expected)); // 调用另一个 expandedSearch 方法，使用 hasRex 匹配器
    }
  }
} // 类结束
