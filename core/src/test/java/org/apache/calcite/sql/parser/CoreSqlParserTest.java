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
// Apache Calcite SQL 解析器核心测试类，用于测试 Calcite 的 SQL 解析功能
// 本类继承自 SqlParserTest，提供了针对核心 SQL 解析器的特定测试用例
// 主要关注保留关键字的一致性验证，确保解析器不会意外添加新的保留关键字
package org.apache.calcite.sql.parser;

// 导入用于测试差异比较的工具类，用于比较两个列表之间的差异
import org.apache.calcite.test.DiffTestCase;

// 导入 Google Guava 库的不可变列表类，用于创建不可修改的列表
import com.google.common.collect.ImmutableList;

// 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入 Java 集合框架的 SortedSet 接口，用于存储有序的字符串集合
import java.util.SortedSet;

// 导入 Java 集合框架的 TreeSet 类，用于实现 SortedSet 接口，提供基于红黑树的有序集合
import java.util.TreeSet;

// 导入 Hamcrest 断言库的 is 匹配器，用于断言两个值是否相等
import static org.hamcrest.CoreMatchers.is;

// 导入 Hamcrest 断言库的 assertThat 方法，用于执行断言操作
import static org.hamcrest.MatcherAssert.assertThat;

// 导入 JUnit 5 的 assumeTrue 方法，用于在测试前设置假设条件，如果条件不满足则跳过测试
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests SQL Parser.
 */
// CoreSqlParserTest 类：Calcite 核心 SQL 解析器的测试类
// 继承自 SqlParserTest，提供了针对核心解析器的特定测试
// 主要功能：
// 1. 验证解析器的保留关键字集合与预期一致
// 2. 确保不会意外添加新的保留关键字
// 3. 检查 SQL:92 标准保留关键字的一致性
// 4. 提供解析器元数据的验证机制
public class CoreSqlParserTest extends SqlParserTest {

  /**
   * Tests that reserved keywords are not added to the parser unintentionally.
   * (Most keywords are non-reserved. The set of reserved words generally
   * only changes with a new version of the SQL standard.)
   *
   * <p>If the new keyword added is intended to be a reserved keyword, update
   * the {@link SqlParserTest#RESERVED_KEYWORDS} list. If not, add the keyword to the
   * non-reserved keyword list in the parser.
   */
  // 测试方法：验证解析器不会意外添加新的保留关键字
  // 大多数关键字是非保留的，保留关键字集合通常只在 SQL 标准新版本发布时才会改变
  // 如果新添加的关键字应该作为保留关键字，需要更新 RESERVED_KEYWORDS 列表
  // 如果不应该作为保留关键字，需要将其添加到解析器的非保留关键字列表中
  @Test void testNoUnintendedNewReservedKeywords() {
    // 假设当前类不是子类，如果是子类则跳过此测试
    // 这是因为子类可能会有不同的保留关键字集合
    assumeTrue(isNotSubclass(), "don't run this test for sub-classes");
    // 获取解析器的元数据对象，该对象包含解析器的所有信息，包括关键字集合
    final SqlAbstractParserImpl.Metadata metadata =
        fixture().parser().getMetadata();

    // 创建一个有序集合用于存储解析器中的保留关键字
    // 使用 TreeSet 确保关键字按字母顺序排列，便于比较
    final SortedSet<String> reservedKeywords = new TreeSet<>();
    // 获取 SQL:92 标准的保留关键字集合，用于验证一致性
    final SortedSet<String> keywords92 = keywords("92");
    // 遍历解析器中的所有 token（词法单元）
    for (String s : metadata.getTokens()) {
      // 检查当前 token 是否是关键字且是保留关键字
      // 如果是，则将其添加到保留关键字集合中
      if (metadata.isKeyword(s) && metadata.isReservedWord(s)) {
        reservedKeywords.add(s);
      }
      // 检查解析器的 SQL:92 保留关键字列表与 keywords("92") 是否一致
      // 确保解析器正确实现了 SQL:92 标准的保留关键字
      // Check that the parser's list of SQL:92
      // reserved words is consistent with keywords("92").
      assertThat(s, metadata.isSql92ReservedWord(s),
          is(keywords92.contains(s)));
    }

    // 构建失败原因信息，如果发现新的保留关键字
    // 使用 DiffTestCase 工具类比较预期保留关键字集合和实际保留关键字集合的差异
    final String reason = "The parser has at least one new reserved keyword. "
        + "Are you sure it should be reserved? Difference:\n"
        + DiffTestCase.diffLines(ImmutableList.copyOf(getReservedKeywords()),
        ImmutableList.copyOf(reservedKeywords));
    // 断言实际保留关键字集合与预期保留关键字集合完全一致
    // 如果不一致，则抛出断言错误，并显示差异信息
    assertThat(reason, reservedKeywords, is(getReservedKeywords()));
  }

  // 私有辅助方法：判断当前类是否为 CoreSqlParserTest 本身，而不是其子类
  // 用于控制测试方法的执行，避免在子类中运行此测试
  // 返回值：如果是 CoreSqlParserTest 本身返回 true，如果是子类返回 false
  private boolean isNotSubclass() {
    // 比较当前类的 Class 对象与 CoreSqlParserTest 的 Class 对象是否相同
    // 如果相同，说明当前类就是 CoreSqlParserTest 本身
    return this.getClass().equals(CoreSqlParserTest.class);
  }
}
