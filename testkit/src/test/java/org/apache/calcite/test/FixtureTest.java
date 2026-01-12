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
package org.apache.calcite.test; // 定义包名，该类位于 org.apache.calcite.test 包下

import org.apache.calcite.avatica.util.Quoting; // 导入 Quoting 类，用于定义 SQL 标识符的引用方式（如反引号、双引号等）
import org.apache.calcite.rel.rules.CoreRules; // 导入 CoreRules 类，包含 Calcite 核心优化规则
import org.apache.calcite.sql.parser.SqlParserFixture; // 导入 SqlParserFixture 类，用于 SQL 解析器的测试装置
import org.apache.calcite.sql.test.SqlOperatorFixture; // 导入 SqlOperatorFixture 类，用于 SQL 操作符的测试装置

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.opentest4j.AssertionFailedError; // 导入 AssertionFailedError 类，用于断言失败时的异常

import java.util.concurrent.atomic.AtomicInteger; // 导入 AtomicInteger 类，用于原子整数操作，保证线程安全
import java.util.function.Predicate; // 导入 Predicate 函数式接口，用于定义谓词（返回布尔值的函数）
import java.util.function.UnaryOperator; // 导入 UnaryOperator 函数式接口，用于定义一元操作符

import static org.hamcrest.CoreMatchers.is; // 导入 is 匹配器，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 方法，用于 Hamcrest 断言
import static org.junit.jupiter.api.Assertions.fail; // 导入 fail 方法，用于显式使测试失败

/** Tests test fixtures. // 测试测试装置（Test Fixtures）的测试类
 *
 * <p>The key feature of fixtures is that they work outside of Calcite core
 * tests, and of course this test cannot verify that. So, additional tests will
 * be needed elsewhere. The code might look similar in these additional tests,
 * but the most likely breakages will be due to classes not being on the path.
 * // 测试装置的关键特性是它们可以在 Calcite 核心测试之外工作，当然这个测试无法验证这一点。
 * // 因此，需要在其他地方进行额外的测试。这些额外测试中的代码可能看起来相似，
 * // 但最可能的失败原因是类不在类路径上。
 *
 * @see Fixtures */ // 参见 Fixtures 类
public class FixtureTest { // 定义 FixtureTest 测试类，用于测试各种测试装置的功能

  public static final String DIFF_REPOS_MESSAGE = "diffRepos is null; if you require a " // 定义静态常量，存储差异仓库为空时的错误消息
      + "DiffRepository, set it in " // 错误消息续：如果需要 DiffRepository，请在
      + "your test's fixture() method"; // 错误消息续：你测试的 fixture() 方法中设置它

  /** Tests that you can write parser tests via {@link Fixtures#forParser()}. */ // 测试可以通过 Fixtures.forParser() 编写解析器测试
  @Test void testParserFixture() { // 定义测试方法，测试 SQL 解析器装置的功能
    // 'as' as identifier is invalid with Core parser // 'as' 作为标识符在核心解析器中是无效的
    final SqlParserFixture f = Fixtures.forParser(); // 创建一个 SQL 解析器装置，用于测试 SQL 解析功能
    f.sql("select ^as^ from t") // 设置要测试的 SQL 语句，使用 ^ 符号标记错误位置
        .fails("(?s)Encountered \"as\".*"); // 断言该 SQL 应该失败，错误消息包含 "Encountered \"as\""

    // Postgres cast is invalid with core parser // Postgres 风格的类型转换在核心解析器中是无效的
    f.sql("select 1 ^:^: integer as x") // 设置要测试的 SQL 语句，使用 :: 进行类型转换
        .fails("(?s).*Encountered \":\" at .*"); // 断言该 SQL 应该失败，错误消息包含 "Encountered \":\""

    // Backtick fails // 反引号标识符在默认配置下会失败
    f.sql("select ^`^foo` from `bar``") // 设置要测试的 SQL 语句，使用反引号标识标识符
        .fails("(?s)Lexical error at line 1, column 8.  " // 断言该 SQL 应该失败，错误消息包含词法错误
            + "Encountered: \"`\" \\(96\\), .*"); // 错误消息续：遇到了反引号字符

    // After changing config, backtick succeeds // 更改配置后，反引号标识符可以成功解析
    f.sql("select `foo` from `bar`") // 设置要测试的 SQL 语句，使用反引号标识标识符
        .withConfig(c -> c.withQuoting(Quoting.BACK_TICK)) // 配置解析器使用反引号作为标识符引用方式
        .ok("SELECT `foo`\n" // 断言 SQL 解析成功，解析后的标准化 SQL 为
            + "FROM `bar`"); // SELECT `foo` FROM `bar`
  }

  /** Tests that you can run validator tests via
   * {@link Fixtures#forValidator()}. */ // 测试可以通过 Fixtures.forValidator() 运行验证器测试
  @Test void testValidatorFixture() { // 定义测试方法，测试 SQL 验证器装置的功能
    final SqlValidatorFixture f = Fixtures.forValidator(); // 创建一个 SQL 验证器装置，用于测试 SQL 验证功能
    f.withSql("select ^1 + date '2002-03-04'^") // 设置要测试的 SQL 语句，使用 ^ 标记错误位置：整数与日期相加
        .fails("(?s).*Cannot apply '\\+' to arguments of" // 断言该 SQL 应该失败，错误消息包含不能对参数应用 '+' 运算符
            + " type '<INTEGER> \\+ <DATE>'.*"); // 错误消息续：类型为 <INTEGER> + <DATE>

    f.withSql("select 1 + 2 as three") // 设置要测试的 SQL 语句：1 + 2 别名为 three
        .type("RecordType(INTEGER NOT NULL THREE) NOT NULL"); // 断言结果类型为包含 INTEGER NOT NULL THREE 字段的记录类型
  }

  /** Tests that you can run operator tests via
   * {@link Fixtures#forValidator()}. */ // 测试可以通过 Fixtures.forValidator() 运行操作符测试
  @Test void testOperatorFixture() { // 定义测试方法，测试 SQL 操作符装置的功能
    // The first fixture only validates, does not execute. // 第一个装置只进行验证，不执行查询
    final SqlOperatorFixture validateFixture = Fixtures.forOperators(false); // 创建一个只验证不执行的操作符装置
    final SqlOperatorFixture executeFixture = Fixtures.forOperators(true); // 创建一个既验证又执行的操作符装置

    // Passes with and without execution // 无论是否执行，该测试都应该通过
    validateFixture.checkBoolean("1 < 5", true); // 验证装置检查 "1 < 5" 的结果是否为 true（只验证不执行）
    executeFixture.checkBoolean("1 < 5", true); // 执行装置检查 "1 < 5" 的结果是否为 true（验证并执行）

    // The fixture that executes fails, because the result value is incorrect. // 执行装置会失败，因为结果值不正确
    validateFixture.checkBoolean("1 < 5", false); // 验证装置检查 "1 < 5" 的结果是否为 false（只验证不执行，不会抛出异常）
    assertFails(() -> executeFixture.checkBoolean("1 < 5", false), // 断言执行装置会抛出断言错误，因为实际结果是 true
        "Query: values (1 < 5)", "<false>", "<true>"); // 错误消息包含查询语句、期望值 false 和实际值 true

    // The fixture that executes fails, because the result value is incorrect. // 验证装置会因为类型不匹配而失败
    validateFixture.checkScalarExact("1 + 2", "INTEGER NOT NULL", "foo"); // 验证装置检查 "1 + 2" 的类型和值，类型正确但值不匹配
    assertFails(() -> validateFixture.checkScalarExact("1 + 2", "DATE", "foo"), // 断言会抛出断言错误，因为类型不匹配
        "Query: values (1 + 2)", "\"DATE\"", "\"INTEGER NOT NULL\""); // 错误消息包含查询语句、期望类型 DATE 和实际类型 INTEGER NOT NULL

    // Both fixtures pass. // 两个装置都应该通过
    validateFixture.checkScalarExact("1 + 2", "INTEGER NOT NULL", "3"); // 验证装置检查 "1 + 2" 的类型为 INTEGER NOT NULL，值为 3
    executeFixture.checkScalarExact("1 + 2", "INTEGER NOT NULL", "3"); // 执行装置检查 "1 + 2" 的类型为 INTEGER NOT NULL，值为 3

    // Both fixtures fail, because the type is incorrect. // 两个装置都会失败，因为类型不正确
    assertFails(() -> validateFixture.checkScalarExact("1 + 2", "DATE", "foo"), // 断言验证装置会抛出断言错误
        "Query: values (1 + 2)", "\"DATE\"", "\"INTEGER NOT NULL\""); // 错误消息包含查询语句、期望类型 DATE 和实际类型 INTEGER NOT NULL
    assertFails(() -> executeFixture.checkScalarExact("1 + 2", "DATE", "foo"), // 断言执行装置会抛出断言错误
        "Query: values (1 + 2)", "\"DATE\"", "\"INTEGER NOT NULL\""); // 错误消息包含查询语句、期望类型 DATE 和实际类型 INTEGER NOT NULL
  }

  static void assertFails(Runnable runnable, String msg, String expected, String actual) { // 静态辅助方法，断言某个可执行对象会抛出断言错误
    try { // 尝试执行可执行对象
      runnable.run(); // 运行可执行对象
      fail("expected error"); // 如果没有抛出异常，使测试失败
    } catch (AssertionError e) { // 捕获断言错误
      String expectedMessage = msg + "\n" // 构造期望的错误消息
          + "Expected: is " + expected + "\n" // 期望值部分
          + "     but: was " + actual; // 实际值部分
      assertThat(e.getMessage(), is(expectedMessage)); // 断言实际错误消息与期望错误消息匹配
    }
  }

  /** Tests that you can run SQL-to-Rel tests via
   * {@link Fixtures#forSqlToRel()}. */ // 测试可以通过 Fixtures.forSqlToRel() 运行 SQL 到 Rel 转换测试
  @Test void testSqlToRelFixture() { // 定义测试方法，测试 SQL 到 Rel 转换装置的功能
    final SqlToRelFixture f = // 创建一个 SQL 到 Rel 转换装置
        Fixtures.forSqlToRel() // 获取 SQL 到 Rel 转换装置
            .withDiffRepos(DiffRepository.lookup(FixtureTest.class)); // 设置差异仓库，用于比较期望输出和实际输出
    final String sql = "select 1 from emp"; // 定义要测试的 SQL 语句
    f.withSql(sql).ok(); // 设置 SQL 并断言转换成功，结果与差异仓库中的期望输出匹配
  }

  /** Tests that we get a good error message if a test needs a diff repository.
   * // 测试当测试需要差异仓库但未设置时，是否能获得良好的错误消息
   *
   * @see DiffRepository#castNonNull(DiffRepository) */ // 参见 DiffRepository.castNonNull 方法
  @Test void testSqlToRelFixtureNeedsDiffRepos() { // 定义测试方法，测试差异仓库缺失时的错误处理
    try { // 尝试执行测试
      final SqlToRelFixture f = Fixtures.forSqlToRel(); // 创建 SQL 到 Rel 转换装置，未设置差异仓库
      final String sql = "select 1 from emp"; // 定义要测试的 SQL 语句
      f.withSql(sql).ok(); // 尝试设置 SQL 并断言转换成功（会因为缺少差异仓库而失败）
      throw new AssertionError("expected error"); // 如果没有抛出异常，使测试失败
    } catch (IllegalArgumentException e) { // 捕获非法参数异常
      assertThat(e.getMessage(), is(DIFF_REPOS_MESSAGE)); // 断言异常消息为预定义的差异仓库缺失消息
    }
  }

  /** Tests the {@link SqlToRelFixture#ensuring(Predicate, UnaryOperator)}
   * test infrastructure. */ // 测试 SqlToRelFixture.ensuring 测试基础设施
  @Test void testSqlToRelFixtureEnsure() { // 定义测试方法，测试 ensuring 方法的功能
    final SqlToRelFixture f = Fixtures.forSqlToRel(); // 创建一个 SQL 到 Rel 转换装置

    // Case 1. Predicate is true at first, remedy not needed // 情况 1：谓词一开始就是 true，不需要补救措施
    f.ensuring(f2 -> true, f2 -> { // 调用 ensuring 方法，谓词总是返回 true
      throw new AssertionError("remedy not needed"); // 如果补救措施被调用，抛出断言错误（不应该执行到这里）
    });

    // Case 2. Predicate is false at first, true after we invoke the remedy. // 情况 2：谓词一开始是 false，调用补救措施后变为 true
    final AtomicInteger b = new AtomicInteger(0); // 创建原子整数，初始值为 0
    assertThat(b.intValue(), is(0)); // 断言原子整数的值为 0
    f.ensuring(f2 -> b.intValue() > 0, f2 -> { // 调用 ensuring 方法，谓词检查原子整数是否大于 0
      b.incrementAndGet(); // 补救措施：原子整数加 1
      return f2; // 返回装置（未修改）
    });
    assertThat(b.intValue(), is(1)); // 断言原子整数的值为 1（补救措施被调用）

    // Case 3. Predicate is false at first, remains false after the "remedy" is
    // invoked. // 情况 3：谓词一开始是 false，调用补救措施后仍然保持 false
    try { // 尝试执行测试
      f.ensuring(f2 -> b.intValue() < 0, f2 -> { // 调用 ensuring 方法，谓词检查原子整数是否小于 0
        b.incrementAndGet(); // 补救措施：原子整数加 1
        return f2; // 返回装置（未修改）
      });
      throw new AssertionFailedError("expected AssertionError"); // 如果没有抛出异常，使测试失败
    } catch (AssertionError e) { // 捕获断言错误
      String expectedMessage = "remedy failed\n" // 构造期望的错误消息
          + "Expected: is <true>\n" // 期望值部分
          + "     but: was <false>"; // 实际值部分
      assertThat(e.getMessage(), is(expectedMessage)); // 断言实际错误消息与期望错误消息匹配
    }
    assertThat("Remedy should be called, even though it is unsuccessful", // 断言补救措施应该被调用，即使不成功
        b.intValue(), is(2)); // 断言原子整数的值为 2（补救措施被调用）
  }

  /** Tests that you can run RelRule tests via
   * {@link Fixtures#forValidator()}. */ // 测试可以通过 Fixtures.forRules() 运行 RelRule 测试
  @Test void testRuleFixture() { // 定义测试方法，测试 RelRule 装置的功能
    final String sql = "select * from dept\n" // 定义要测试的 SQL 语句：从 dept 表选择所有列
        + "union\n" // 使用 UNION 操作符
        + "select * from dept"; // 再次从 dept 表选择所有列（产生重复行）
    final RelOptFixture f = // 创建一个关系表达式优化装置
        Fixtures.forRules() // 获取规则测试装置
            .withDiffRepos(DiffRepository.lookup(FixtureTest.class)); // 设置差异仓库，用于比较期望输出和实际输出
    f.sql(sql) // 设置 SQL 语句
        .withRule(CoreRules.UNION_TO_DISTINCT) // 添加 UNION_TO_DISTINCT 规则，将 UNION 转换为 UNION DISTINCT
        .check(); // 执行检查，比较实际输出与差异仓库中的期望输出
  }

  /** As {@link #testSqlToRelFixtureNeedsDiffRepos} but for
   * {@link Fixtures#forRules()}. */ // 与 testSqlToRelFixtureNeedsDiffRepos 类似，但是针对 Fixtures.forRules()
  @Test void testRuleFixtureNeedsDiffRepos() { // 定义测试方法，测试规则装置差异仓库缺失时的错误处理
    try { // 尝试执行测试
      final String sql = "select * from dept\n" // 定义要测试的 SQL 语句：从 dept 表选择所有列
          + "union\n" // 使用 UNION 操作符
          + "select * from dept"; // 再次从 dept 表选择所有列（产生重复行）
      final RelOptFixture f = Fixtures.forRules(); // 创建规则测试装置，未设置差异仓库
      f.sql(sql) // 设置 SQL 语句
          .withRule(CoreRules.UNION_TO_DISTINCT) // 添加 UNION_TO_DISTINCT 规则
          .check(); // 尝试执行检查（会因为缺少差异仓库而失败）
      throw new AssertionError("expected error"); // 如果没有抛出异常，使测试失败
    } catch (IllegalArgumentException e) { // 捕获非法参数异常
      assertThat(e.getMessage(), is(DIFF_REPOS_MESSAGE)); // 断言异常消息为预定义的差异仓库缺失消息
    }
  }

  /** Tests metadata. */ // 测试元数据功能
  @Test void testMetadata() { // 定义测试方法，测试元数据装置的功能
    final RelMetadataFixture f = Fixtures.forMetadata(); // 创建一个元数据装置，用于测试关系表达式元数据
    f.withSql("select name as dname from dept") // 设置 SQL 语句：从 dept 表选择 name 列，别名为 dname
          .assertColumnOriginSingle("DEPT", "NAME", false); // 断言列来源：dname 列来自 DEPT 表的 NAME 列，非派生
    f.withSql("select upper(name) as dname from dept") // 设置 SQL 语句：从 dept 表选择 name 列的大写形式，别名为 dname
        .assertColumnOriginSingle("DEPT", "NAME", true); // 断言列来源：dname 列来自 DEPT 表的 NAME 列，是派生的（通过 upper 函数）
    f.withSql("select name||ename from dept,emp") // 设置 SQL 语句：连接 dept 和 emp 表，选择 name 和 ename 的拼接
        .assertColumnOriginDouble("DEPT", "NAME", "EMP", "ENAME", true); // 断言列来源：结果列来自 DEPT.NAME 和 EMP.ENAME，是派生的（通过 || 操作符）
    f.withSql("select 'Minstrelsy' as dname from dept") // 设置 SQL 语句：从 dept 表选择常量字符串 'Minstrelsy'，别名为 dname
        .assertColumnOriginIsEmpty(); // 断言列来源为空（因为 dname 列是常量，不来自任何表的列）
  }
}
