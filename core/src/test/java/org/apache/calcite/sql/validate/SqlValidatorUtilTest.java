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
// 声明当前类所在的包，即org.apache.calcite.sql.validate包，表示这个测试类属于SQL验证相关的模块
package org.apache.calcite.sql.validate;
// 导入Calcite上下文异常类，用于处理SQL验证过程中可能出现的异常
import org.apache.calcite.runtime.CalciteContextException;
// 导入SQL标识符类，用于表示SQL中的标识符（如表名、列名等）
import org.apache.calcite.sql.SqlIdentifier;
// 导入SQL节点类，是所有SQL语法节点的基类
import org.apache.calcite.sql.SqlNode;
// 导入SQL解析位置类，用于标记SQL语法元素在原始SQL语句中的位置
import org.apache.calcite.sql.parser.SqlParserPos;
// 导入Calcite测试工具类，提供测试SQL验证器的辅助方法
import org.apache.calcite.test.Fixtures;
// 导入SQL验证器测试工具类，提供SQL验证器的测试fixture
import org.apache.calcite.test.SqlValidatorFixture;

// 导入Google Guava库的不可变列表类，用于创建不可变的列表集合
import com.google.common.collect.ImmutableList;
// 导入Google Guava库的Lists工具类，提供创建和操作列表的便捷方法
import com.google.common.collect.Lists;

// 导入JUnit 5的Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入Java标准库的ArrayList类，用于动态数组列表
import java.util.ArrayList;
// 导入Java标准库的Arrays工具类，提供操作数组的静态方法
import java.util.Arrays;
// 导入Java标准库的List接口，表示有序集合
import java.util.List;
// 导入Java标准库的Locale类，用于本地化相关的操作（如大小写转换）
import java.util.Locale;

// 导入Hamcrest断言库的anyOf匹配器，用于断言多个可能值
import static org.hamcrest.CoreMatchers.anyOf;
// 导入Hamcrest断言库的is匹配器，用于断言相等性
import static org.hamcrest.CoreMatchers.is;
// 导入Hamcrest断言库的not匹配器，用于断言否定条件
import static org.hamcrest.CoreMatchers.not;
// 导入Hamcrest断言库的sameInstance匹配器，用于断言对象引用相同
import static org.hamcrest.CoreMatchers.sameInstance;
// 导入Hamcrest断言库的Assert类，用于执行断言
import static org.hamcrest.MatcherAssert.assertThat;
// 导入Hamcrest断言库的hasSize匹配器，用于断言集合大小
import static org.hamcrest.Matchers.hasSize;
// 导入JUnit 5的Assertions类的fail方法，用于标记测试失败
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link SqlValidatorUtil}.
 * SqlValidatorUtil的测试类，用于测试SQL验证工具类的各种功能
 * SqlValidatorUtil是Calcite中用于SQL验证的工具类，提供了名称去重、标识符检查等实用方法
 * 本测试类主要测试以下功能：
 * 1. 名称列表去重（uniquify方法）- 测试大小写敏感和不敏感的情况
 * 2. 复合标识符重复检查 - 测试多级标识符的重复检测
 * 3. 名称匹配器测试 - 测试大小写敏感和不敏感的名称匹配
 */
// 定义SqlValidatorUtilTest测试类，用于测试SqlValidatorUtil工具类的功能
class SqlValidatorUtilTest {

  // 私有静态辅助方法，用于检查字段名称列表是否正确去重
  // 参数说明：
  //   nameList: 原始的名称列表
  //   resultList: 经过uniquify处理后的结果列表
  //   caseSensitive: 是否区分大小写的标志
  // 方法作用：验证结果列表中的名称是否正确去重，确保每个名称都是唯一的
  private static void checkChangedFieldList(
      List<String> nameList, List<String> resultList, boolean caseSensitive) {
    // 检查新名称是否按照它们在原始nameList中出现的顺序附加了"0"
    // 这里假设我们只有一个"冲突"情况，即只有重复的名称才会被修改
    // 初始化索引计数器i为0，用于遍历名称列表
    int i = 0;
    // 遍历原始名称列表中的每个名称
    for (String name : nameList) {
      // 从结果列表中获取对应位置的新名称
      String newName = resultList.get(i);
      // 断言：新名称要么与原名称相同（没有冲突），要么是原名称加"0"（有冲突）
      // anyOf表示 newName 满足 is(name) 或 is(name + "0") 中的任意一个条件
      assertThat(newName, anyOf(is(name), is(name + "0")));
      // 索引计数器递增，处理下一个名称
      i++;
    }

    // 确保每个名称都是唯一的（没有重复）
    // 创建一个新的ArrayList，大小与resultList相同，用于存储结果列表的副本
    List<String> copyResultList  = new ArrayList<>(resultList.size());
    // 遍历结果列表中的每个结果名称
    for (String result : resultList) {
      // 将结果名称转换为小写（使用ROOT locale确保一致性）并添加到副本列表中
      // 转换为小写是为了进行大小写不敏感的比较
      copyResultList.add(result.toLowerCase(Locale.ROOT));
    }

    // 再次遍历结果列表，验证每个名称的唯一性
    for (String result : resultList) {
      // 将当前结果名称转换为小写
      final String lowerResult = result.toLowerCase(Locale.ROOT);
      // 断言：副本列表中必须包含当前的小写名称（因为之前刚添加过）
      assertThat(copyResultList.contains(lowerResult), is(true));
      // 从副本列表中移除当前的小写名称，这样如果有重复就会被检测到
      copyResultList.remove(lowerResult);
      // 如果不区分大小写，则副本列表中不应该再包含当前的小写名称
      // 这确保了在大小写不敏感模式下，名称是真正唯一的
      if (!caseSensitive) {
        assertThat(copyResultList.contains(lowerResult), is(false));
      }
    }
    // 断言：副本列表的最终大小应该为0，说明所有名称都是唯一的，没有重复
    assertThat(copyResultList, hasSize(0));
  }

  // 测试方法：测试大小写敏感的名称去重功能
  // 使用@Test注解标记为JUnit测试方法
  @Test void testUniquifyCaseSensitive() {
    // 创建一个包含大小写不同但字母相同的名称列表
    // "col1"和"COL1"在大小写敏感模式下是不同的名称
    // "col_ABC"和"col_abC"在大小写敏感模式下也是不同的名称
    List<String> nameList = Lists.newArrayList("col1", "COL1", "col_ABC", "col_abC");
    // 调用SqlValidatorUtil.uniquify方法对名称列表进行去重处理
    // 参数说明：
    //   nameList: 要去重的名称列表
    //   SqlValidatorUtil.EXPR_SUGGESTER: 名称建议器，用于生成新的唯一名称
    //   true: 表示大小写敏感
    // 返回值：去重后的名称列表
    List<String> resultList =
        SqlValidatorUtil.uniquify(nameList, SqlValidatorUtil.EXPR_SUGGESTER, true);
    // 断言：在大小写敏感模式下，由于所有名称都是唯一的，应该返回原始列表的同一个实例
    // sameInstance表示nameList和resultList应该是同一个对象引用
    assertThat(nameList, sameInstance(resultList));
  }

  // 测试方法：测试大小写不敏感的名称去重功能
  @Test void testUniquifyNotCaseSensitive() {
    // 创建一个包含大小写不同但字母相同的名称列表
    // 在大小写不敏感模式下，"col1"和"COL1"被认为是重复的
    // "col_ABC"和"col_abC"也被认为是重复的
    List<String> nameList = Lists.newArrayList("col1", "COL1", "col_ABC", "col_abC");
    // 调用SqlValidatorUtil.uniquify方法对名称列表进行去重处理
    // 参数说明：
    //   nameList: 要去重的名称列表
    //   SqlValidatorUtil.EXPR_SUGGESTER: 名称建议器，用于生成新的唯一名称
    //   false: 表示大小写不敏感
    // 返回值：去重后的名称列表
    List<String> resultList =
        SqlValidatorUtil.uniquify(nameList, SqlValidatorUtil.EXPR_SUGGESTER, false);
    // 断言：在大小写不敏感模式下，由于存在重复名称，resultList不应该与nameList是同一个对象
    assertThat(resultList, not(nameList));
    // 调用辅助方法检查结果列表是否正确去重
    // 参数说明：
    //   nameList: 原始名称列表
    //   resultList: 去重后的结果列表
    //   false: 表示大小写不敏感
    checkChangedFieldList(nameList, resultList, false);
  }

  // 测试方法：测试大小写敏感模式下名称顺序保持不变的功能
  @Test void testUniquifyOrderingCaseSensitive() {
    // 创建一个包含不同名称的列表，名称按特定顺序排列
    // 包含以数字开头的名称、小写名称、大写名称等
    List<String> nameList = Lists.newArrayList("k68s", "def", "col1", "COL1", "abc", "123");
    // 调用SqlValidatorUtil.uniquify方法进行去重处理
    // 参数说明：
    //   nameList: 要去重的名称列表
    //   SqlValidatorUtil.EXPR_SUGGESTER: 名称建议器
    //   true: 表示大小写敏感
    List<String> resultList =
        SqlValidatorUtil.uniquify(nameList, SqlValidatorUtil.EXPR_SUGGESTER, true);
    // 断言：在大小写敏感模式下，所有名称都是唯一的，应该返回原始列表的同一个实例
    assertThat(nameList, sameInstance(resultList));
  }

  // 测试方法：测试大小写敏感模式下有重复名称时的去重功能
  @Test void testUniquifyOrderingRepeatedCaseSensitive() {
    // 创建一个包含重复名称的列表
    // "def"出现了两次，在大小写敏感模式下这是重复的
    List<String> nameList = Lists.newArrayList("k68s", "def", "col1", "COL1", "def", "123");
    // 调用SqlValidatorUtil.uniquify方法进行去重处理
    // 参数说明：
    //   nameList: 要去重的名称列表
    //   SqlValidatorUtil.EXPR_SUGGESTER: 名称建议器
    //   true: 表示大小写敏感
    List<String> resultList =
        SqlValidatorUtil.uniquify(nameList, SqlValidatorUtil.EXPR_SUGGESTER, true);
    // 断言：由于存在重复名称，resultList不应该与nameList是同一个对象
    assertThat(nameList, not(resultList));
    // 调用辅助方法检查结果列表是否正确去重
    // 参数说明：
    //   nameList: 原始名称列表
    //   resultList: 去重后的结果列表
    //   true: 表示大小写敏感
    checkChangedFieldList(nameList, resultList, true);
  }

  // 测试方法：测试大小写不敏感模式下名称顺序保持不变的功能
  @Test void testUniquifyOrderingNotCaseSensitive() {
    // 创建一个包含不同名称的列表，包含大小写不同的名称
    // 在大小写不敏感模式下，"col1"和"COL1"被认为是重复的
    List<String> nameList = Lists.newArrayList("k68s", "def", "col1", "COL1", "abc", "123");
    // 调用SqlValidatorUtil.uniquify方法进行去重处理
    // 参数说明：
    //   nameList: 要去重的名称列表
    //   SqlValidatorUtil.EXPR_SUGGESTER: 名称建议器
    //   false: 表示大小写不敏感
    List<String> resultList =
        SqlValidatorUtil.uniquify(nameList, SqlValidatorUtil.EXPR_SUGGESTER, false);
    // 断言：由于存在大小写重复的名称，resultList不应该与nameList是同一个对象
    assertThat(resultList, not(nameList));
    // 调用辅助方法检查结果列表是否正确去重
    // 参数说明：
    //   nameList: 原始名称列表
    //   resultList: 去重后的结果列表
    //   false: 表示大小写不敏感
    checkChangedFieldList(nameList, resultList, false);
  }

  // 测试方法：测试大小写不敏感模式下有重复名称时的去重功能
  @Test void testUniquifyOrderingRepeatedNotCaseSensitive() {
    // 创建一个包含重复名称的列表
    // "def"出现了两次，"col1"和"COL1"在大小写不敏感模式下也是重复的
    List<String> nameList = Lists.newArrayList("k68s", "def", "col1", "COL1", "def", "123");
    // 调用SqlValidatorUtil.uniquify方法进行去重处理
    // 参数说明：
    //   nameList: 要去重的名称列表
    //   SqlValidatorUtil.EXPR_SUGGESTER: 名称建议器
    //   false: 表示大小写不敏感
    List<String> resultList =
        SqlValidatorUtil.uniquify(nameList, SqlValidatorUtil.EXPR_SUGGESTER, false);
    // 断言：由于存在重复名称，resultList不应该与nameList是同一个对象
    assertThat(resultList, not(nameList));
    // 调用辅助方法检查结果列表是否正确去重
    // 参数说明：
    //   nameList: 原始名称列表
    //   resultList: 去重后的结果列表
    //   false: 表示大小写不敏感
    checkChangedFieldList(nameList, resultList, false);
  }

  // 使用@SuppressWarnings注解抑制"resource"警告
  // 这是因为fixture可能包含需要关闭的资源，但在测试中不需要显式关闭
  @SuppressWarnings("resource")
  // 测试方法：测试检查复合标识符重复的功能
  // 复合标识符是指由多个部分组成的标识符，如"表名.列名"
  @Test void testCheckingDuplicatesWithCompoundIdentifiers() {
    // 创建一个新的SQL节点列表，初始容量为2
    final List<SqlNode> newList = new ArrayList<>(2);
    // 添加第一个复合标识符SqlIdentifier
    // 参数说明：
    //   Arrays.asList("f0", "c0"): 创建一个包含两个部分的列表，表示"f0.c0"这样的复合标识符
    //   SqlParserPos.ZERO: 表示标识符在SQL语句中的位置为零位置
    newList.add(new SqlIdentifier(Arrays.asList("f0", "c0"), SqlParserPos.ZERO));
    // 添加第二个复合标识符SqlIdentifier，与第一个完全相同
    // 这样就创建了重复的复合标识符
    newList.add(new SqlIdentifier(Arrays.asList("f0", "c0"), SqlParserPos.ZERO));
    // 使用Fixtures工具类创建SQL验证器的测试fixture
    final SqlValidatorFixture fixture = Fixtures.forValidator();
    // 从fixture的工厂创建一个SQL验证器实例，并强制转换为SqlValidatorImpl类型
    final SqlValidatorImpl validator =
        (SqlValidatorImpl) fixture.factory.createValidator();
    // 尝试检查标识符列表中的重复项
    try {
      // 调用SqlValidatorUtil.checkIdentifierListForDuplicates方法检查重复
      // 参数说明：
      //   newList: 要检查的标识符列表
      //   validator.getValidationErrorFunction(): 验证错误函数，用于在发现重复时创建错误信息
      // 由于列表中有重复的标识符，这个方法应该抛出CalciteContextException异常
      SqlValidatorUtil.checkIdentifierListForDuplicates(newList,
          validator.getValidationErrorFunction());
      // 如果没有抛出异常，则测试失败
      fail("expected exception");
    } catch (CalciteContextException e) {
      // 捕获预期的异常，测试通过
      // ok
    }
    // 修改列表中的第二个标识符，使其不再重复
    // 将第二个标识符从"f0.c0"改为"f0.c1"
    // should not throw: 这次不应该抛出异常，因为标识符不再重复
    newList.set(1, new SqlIdentifier(Arrays.asList("f0", "c1"), SqlParserPos.ZERO));
    // 再次检查标识符列表，这次传入null作为错误函数
    // 由于没有重复，这个方法不应该抛出异常
    SqlValidatorUtil.checkIdentifierListForDuplicates(newList, null);
  }

  // 测试方法：测试名称匹配器的功能
  // 名称匹配器用于在名称列表中查找和匹配名称，支持大小写敏感和不敏感的模式
  @Test void testNameMatcher() {
    // 创建一个不可变的名称列表，包含甲壳虫乐队成员的名字
    // 注意：列表中包含"ringo"和"rinGo"，用于测试大小写匹配
    final ImmutableList<String> beatles =
        ImmutableList.of("john", "paul", "ringo", "rinGo");
    // 创建一个大小写不敏感的名称匹配器
    // SqlNameMatchers.withCaseSensitive(false): 创建不区分大小写的匹配器
    final SqlNameMatcher insensitiveMatcher =
        SqlNameMatchers.withCaseSensitive(false);
    // 断言：在不区分大小写的模式下，"ringo"出现的次数应该是2
    // 因为"ringo"和"rinGo"在不区分大小写时是相同的
    assertThat(insensitiveMatcher.frequency(beatles, "ringo"), is(2));
    // 断言：在不区分大小写的模式下，"rinGo"出现的次数也应该是2
    assertThat(insensitiveMatcher.frequency(beatles, "rinGo"), is(2));
    // 断言：在不区分大小写的模式下，"rinGo"第一次出现的索引应该是2
    // 返回的是第一个匹配项的索引
    assertThat(insensitiveMatcher.indexOf(beatles, "rinGo"), is(2));
    // 断言：在不区分大小写的模式下，"stuart"不存在，应该返回-1
    assertThat(insensitiveMatcher.indexOf(beatles, "stuart"), is(-1));
    // 创建一个大小写敏感的名称匹配器
    // SqlNameMatchers.withCaseSensitive(true): 创建区分大小写的匹配器
    final SqlNameMatcher sensitiveMatcher =
        SqlNameMatchers.withCaseSensitive(true);
    // 断言：在区分大小写的模式下，"ringo"出现的次数应该是1
    // 因为只有小写的"ringo"匹配，"rinGo"不匹配
    assertThat(sensitiveMatcher.frequency(beatles, "ringo"), is(1));
    // 断言：在区分大小写的模式下，"rinGo"出现的次数应该是1
    assertThat(sensitiveMatcher.frequency(beatles, "rinGo"), is(1));
    // 断言：在区分大小写的模式下，"Ringo"出现的次数应该是0
    // 因为列表中没有大写的"Ringo"
    assertThat(sensitiveMatcher.frequency(beatles, "Ringo"), is(0));
    // 断言：在区分大小写的模式下，"ringo"的索引应该是2
    assertThat(sensitiveMatcher.indexOf(beatles, "ringo"), is(2));
    // 断言：在区分大小写的模式下，"rinGo"的索引应该是3
    assertThat(sensitiveMatcher.indexOf(beatles, "rinGo"), is(3));
    // 断言：在区分大小写的模式下，"Ringo"不存在，应该返回-1
    assertThat(sensitiveMatcher.indexOf(beatles, "Ringo"), is(-1));

  }
}
