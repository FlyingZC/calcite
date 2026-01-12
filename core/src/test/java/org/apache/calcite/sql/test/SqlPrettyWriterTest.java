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
// Apache许可证声明，说明该文件遵循Apache 2.0许可证
package org.apache.calcite.sql.test; // 定义包名，该类位于org.apache.calcite.sql.test包下

import org.apache.calcite.sql.SqlNode; // 导入SqlNode类，表示SQL抽象语法树中的节点
import org.apache.calcite.sql.SqlSelect; // 导入SqlSelect类，表示SELECT语句
import org.apache.calcite.sql.SqlWith; // 导入SqlWith类，表示WITH子句（公用表表达式CTE）
import org.apache.calcite.sql.SqlWriter; // 导入SqlWriter接口，定义SQL写入器的行为
import org.apache.calcite.sql.SqlWriterConfig; // 导入SqlWriterConfig类，用于配置SQL写入器的各种格式化选项
import org.apache.calcite.sql.parser.SqlParseException; // 导入SqlParseException类，表示SQL解析异常
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，用于将SQL字符串解析为SqlNode
import org.apache.calcite.sql.pretty.SqlPrettyWriter; // 导入SqlPrettyWriter类，用于将SqlNode格式化为漂亮的SQL字符串
import org.apache.calcite.test.DiffRepository; // 导入DiffRepository类，用于存储和比较测试的期望输出与实际输出

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可能为null的值
import org.junit.jupiter.api.AfterAll; // 导入AfterAll注解，表示在所有测试方法执行后执行一次
import org.junit.jupiter.api.Disabled; // 导入Disabled注解，表示禁用某个测试方法
import org.junit.jupiter.api.Test; // 导入Test注解，表示这是一个测试方法

import static org.apache.calcite.test.Matchers.isLinux; // 导入isLinux匹配器，用于验证字符串是否匹配Linux格式的期望输出

import static org.hamcrest.CoreMatchers.instanceOf; // 导入instanceOf匹配器，用于验证对象是否是特定类的实例
import static org.hamcrest.CoreMatchers.notNullValue; // 导入notNullValue匹配器，用于验证对象不为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat方法，用于断言验证
import static org.hamcrest.Matchers.hasToString; // 导入hasToString匹配器，用于验证对象的toString()方法返回特定字符串

/**
 * Unit test for {@link SqlPrettyWriter}.
 * SqlPrettyWriter的单元测试类
 *
 * <p>You must provide the system property "source.dir".
 * 必须提供系统属性"source.dir"来运行这些测试
 */
class SqlPrettyWriterTest { // SqlPrettyWriterTest类定义，用于测试SqlPrettyWriter的各种格式化功能
  /** Fixture that can be re-used by other tests. */
  // 可以被其他测试重用的测试夹具，是一个静态常量
  public static final SqlPrettyWriterFixture FIXTURE =
      new SqlPrettyWriterFixture(null, "?", false, null, "${formatted}", // 创建SqlPrettyWriterFixture实例，参数：SQL为null，期望描述为"?"，表达式标志为false，差异仓库为null，格式化模板为"${formatted}"
          w -> w); // 写入器配置函数，保持默认配置

  /** Fixture that is local to this test. */
  // 本地测试夹具，专门用于当前测试类
  private static final SqlPrettyWriterFixture LOCAL_FIXTURE =
      FIXTURE.withDiffRepos(DiffRepository.lookup(SqlPrettyWriterTest.class)); // 基于FIXTURE创建，并设置差异仓库为当前类的差异仓库

  @Nullable // 可以为null
  private static DiffRepository diffRepos = null; // 差异仓库实例，用于存储和比较测试输出

  @AfterAll // 在所有测试方法执行后执行此方法
  public static void checkActualAndReferenceFiles() { // 检查实际输出文件和参考文件是否一致
    if (diffRepos != null) { // 如果差异仓库不为null
      diffRepos.checkActualAndReferenceFiles(); // 检查实际输出和参考文件，如果不一致则报告差异
    }
  }

  /** Returns the default fixture for tests. Sub-classes may override. */
  // 返回测试的默认夹具，子类可以重写此方法
  protected SqlPrettyWriterFixture fixture() { // 定义fixture方法
    diffRepos = LOCAL_FIXTURE.diffRepos(); // 设置差异仓库为本地夹具的差异仓库
    return LOCAL_FIXTURE; // 返回本地夹具
  }

  /** Returns a fixture with a given SQL query. */
  // 返回包含指定SQL查询的夹具
  public final SqlPrettyWriterFixture sql(String sql) { // 定义sql方法，接收SQL字符串参数
    return fixture().withSql(sql); // 调用fixture()方法获取夹具，并设置SQL语句
  }

  /** Returns a fixture with a given SQL expression. */
  // 返回包含指定SQL表达式的夹具
  public final SqlPrettyWriterFixture expr(String sql) { // 定义expr方法，接收SQL表达式字符串参数
    return fixture().withSql(sql).withExpr(true); // 调用fixture()方法获取夹具，设置SQL语句，并标记为表达式
  }

  /** Creates a fluent test for a SQL statement that has most common lexical
   * features. */
  // 创建一个包含最常见词法特征的SQL语句的流式测试
  private SqlPrettyWriterFixture simple() { // 定义simple方法
    return sql("select x as a, b as b, c as c, d," // 创建SQL语句：select子句，包含多个列，其中x重命名为a，b重命名为b等
        + " 'mixed-Case string'," // 选择一个混合大小写的字符串字面量
        + " unquotedCamelCaseId," // 选择一个未加引号的驼峰命名标识符
        + " \"quoted id\" " // 选择一个加引号的标识符（包含空格）
        + "from" // from子句开始
        + " (select *" // 子查询开始，选择所有列
        + " from t" // 从表t中选择
        + " where x = y and a > 5" // where条件：x等于y且a大于5
        + " group by z, zz" // group by子句，按z和zz分组
        + " window w as (partition by c)," // 窗口定义w，按c分区
        + "  w1 as (partition by c,d order by a, b" // 窗口定义w1，按c和d分区，按a和b排序
        + "   range between interval '2:2' hour to minute preceding" // 范围从2小时2分钟前开始
        + "    and interval '1' day following)) " // 到1天后结束，子查询结束
        + "order by gg"); // 按gg排序
  }

  /** Creates a fluent test for a SQL statement that contains "tableAlias.*". */
  // 创建一个包含"tableAlias.*"语法的SQL语句的流式测试
  private SqlPrettyWriterFixture tableDotStar() { // 定义tableDotStar方法
    return sql("select x as a, b, s.*, t.* " // 创建SQL语句：选择x重命名为a，选择列b，选择s表的所有列，选择t表的所有列
        + "from" // from子句开始
        + " (select *" // 子查询开始，选择所有列
        + " from t" // 从表t中选择
        + " where x = y and a > 5) " // where条件：x等于y且a大于5，子查询结束
        + "order by g desc, h asc, i"); // 按g降序、h升序、i排序
  }

  // ~ Tests ----------------------------------------------------------------
  // 测试方法区域标记

  @Test // 标记为测试方法
  void testDefault() { // 测试默认格式化配置
    simple().check(); // 使用simple()创建的SQL语句，使用默认配置进行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testIndent8() { // 测试缩进为8个空格的格式化
    simple() // 使用simple()创建的SQL语句
        .expectingDesc("${desc}") // 设置期望的描述模板
        .withWriter(w -> w.withIndentation(8)) // 配置写入器，设置缩进为8个空格
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testClausesNotOnNewLine() { // 测试子句不换行的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withClauseStartsLine(false)) // 配置写入器，设置子句不另起一行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testTableDotStarClausesNotOnNewLine() { // 测试包含tableAlias.*的语句且子句不换行的格式化
    tableDotStar() // 使用tableDotStar()创建的SQL语句
        .withWriter(w -> w.withClauseStartsLine(false)) // 配置写入器，设置子句不另起一行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testSelectListItemsOnSeparateLines() { // 测试SELECT列表项每行一个的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withSelectListItemsOnSeparateLines(true)) // 配置写入器，设置SELECT列表项每行显示一个
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testSelectListNoExtraIndentFlag() { // 测试SELECT列表项每行一个但不额外缩进的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withSelectListItemsOnSeparateLines(true) // 配置写入器，设置SELECT列表项每行显示一个
            .withSelectListExtraIndentFlag(false) // 设置不额外缩进SELECT列表项
            .withClauseEndsLine(true)) // 设置子句结束另起一行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testFold() { // 测试FOLD折叠模式的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withLineFolding(SqlWriterConfig.LineFolding.FOLD) // 配置写入器，设置行折叠模式为FOLD（智能折叠）
            .withFoldLength(45)) // 设置折叠长度为45个字符
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testChop() { // 测试CHOP截断模式的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withLineFolding(SqlWriterConfig.LineFolding.CHOP) // 配置写入器，设置行折叠模式为CHOP（强制截断）
            .withFoldLength(45)) // 设置折叠长度为45个字符
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testChopLeadingComma() { // 测试CHOP模式且使用前导逗号的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withLineFolding(SqlWriterConfig.LineFolding.CHOP) // 配置写入器，设置行折叠模式为CHOP
            .withFoldLength(45) // 设置折叠长度为45个字符
            .withLeadingComma(true)) // 设置使用前导逗号（逗号在行首）
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testLeadingComma() { // 测试使用前导逗号的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withLeadingComma(true) // 配置写入器，设置使用前导逗号
            .withSelectListItemsOnSeparateLines(true) // 设置SELECT列表项每行显示一个
            .withSelectListExtraIndentFlag(true)) // 设置额外缩进SELECT列表项
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testClauseEndsLine() { // 测试子句结束另起一行的格式化（WIDE模式）
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withClauseEndsLine(true) // 配置写入器，设置子句结束另起一行
            .withLineFolding(SqlWriterConfig.LineFolding.WIDE) // 设置行折叠模式为WIDE（宽格式）
            .withFoldLength(45)) // 设置折叠长度为45个字符
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testClauseEndsLineTall() { // 测试子句结束另起一行的格式化（TALL模式）
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withClauseEndsLine(true) // 配置写入器，设置子句结束另起一行
            .withLineFolding(SqlWriterConfig.LineFolding.TALL) // 设置行折叠模式为TALL（高格式，更多换行）
            .withFoldLength(45)) // 设置折叠长度为45个字符
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testClauseEndsLineFold() { // 测试子句结束另起一行的格式化（FOLD模式）
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withClauseEndsLine(true) // 配置写入器，设置子句结束另起一行
            .withLineFolding(SqlWriterConfig.LineFolding.FOLD) // 设置行折叠模式为FOLD（智能折叠）
            .withFoldLength(45)) // 设置折叠长度为45个字符
        .check(); // 执行格式化并检查输出
  }

  /** Tests formatting a query with Looker's preferences. */
  // 测试使用Looker偏好的查询格式化
  @Test // 标记为测试方法
  void testLooker() { // 测试Looker风格的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withFoldLength(60) // 配置写入器，设置折叠长度为60个字符
            .withLineFolding(SqlWriterConfig.LineFolding.STEP) // 设置行折叠模式为STEP（阶梯式）
            .withSelectFolding(SqlWriterConfig.LineFolding.TALL) // 设置SELECT子句的折叠模式为TALL
            .withFromFolding(SqlWriterConfig.LineFolding.TALL) // 设置FROM子句的折叠模式为TALL
            .withWhereFolding(SqlWriterConfig.LineFolding.TALL) // 设置WHERE子句的折叠模式为TALL
            .withHavingFolding(SqlWriterConfig.LineFolding.TALL) // 设置HAVING子句的折叠模式为TALL
            .withClauseEndsLine(true)) // 设置子句结束另起一行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testKeywordsLowerCase() { // 测试关键字小写的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withKeywordsLowerCase(true)) // 配置写入器，设置关键字使用小写
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testParenthesizeAllExprs() { // 测试所有表达式都加括号的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withAlwaysUseParentheses(true)) // 配置写入器，设置始终使用括号包裹表达式
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testOnlyQuoteIdentifiersWhichNeedIt() { // 测试只为需要引号的标识符加引号的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withQuoteAllIdentifiers(false)) // 配置写入器，设置不为所有标识符加引号，只为需要的加
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testBlackSubQueryStyle() { // 测试BLACK子查询风格的格式化
    // Note that ( is at the indent, SELECT is on the same line, and ) is
    // below it.
    // 注意：左括号在缩进位置，SELECT在同一行，右括号在下方
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withSubQueryStyle(SqlWriter.SubQueryStyle.BLACK)) // 配置写入器，设置子查询风格为BLACK
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testBlackSubQueryStyleIndent0() { // 测试BLACK子查询风格且缩进为0的格式化
    simple() // 使用simple()创建的SQL语句
        .withWriter(w -> w.withSubQueryStyle(SqlWriter.SubQueryStyle.BLACK) // 配置写入器，设置子查询风格为BLACK
            .withIndentation(0)) // 设置缩进为0
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testValuesNewline() { // 测试VALUES子句每行一个值的格式化
    sql("select * from (values (1, 2), (3, 4)) as t") // 创建SQL语句：从VALUES子句中选择数据
        .withWriter(w -> w.withValuesListNewline(true)) // 配置写入器，设置VALUES列表每行一个
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testValuesLeadingCommas() { // 测试VALUES子句使用前导逗号的格式化
    sql("select * from (values (1, 2), (3, 4)) as t") // 创建SQL语句：从VALUES子句中选择数据
        .withWriter(w -> w.withValuesListNewline(true) // 配置写入器，设置VALUES列表每行一个
            .withLeadingComma(true)) // 设置使用前导逗号
        .check(); // 执行格式化并检查输出
  }

  @Disabled("default SQL parser cannot parse DDL") // 禁用此测试，因为默认SQL解析器无法解析DDL语句
  @Test // 标记为测试方法
  void testExplain() { // 测试EXPLAIN语句的格式化
    sql("explain select * from t") // 创建SQL语句：EXPLAIN查询
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testCase() { // 测试CASE表达式的格式化（多行格式）
    // Note that CASE is rewritten to the searched form. Wish it weren't
    // so, but that's beyond the control of the pretty-printer.
    // 注意：CASE被重写为搜索形式。虽然不希望这样，但这超出了美化打印器的控制范围
    // todo: indent should be 4 not 8
    // 待办：缩进应该是4而不是8
    final String sql = "case 1\n" // 定义原始SQL：简单CASE表达式
        + " when 2 + 3 then 4\n" // when条件：当1等于2+3时返回4
        + " when case a when b then c else d end then 6\n" // when条件：嵌套CASE表达式
        + " else 7\n" // else子句：返回7
        + "end"; // CASE结束
    final String formatted = "CASE\n" // 定义期望的格式化输出：搜索CASE形式
        + "WHEN 1 = 2 + 3\n" // WHEN条件：1等于2+3
        + "THEN 4\n" // THEN结果：返回4
        + "WHEN 1 = CASE\n" // WHEN条件：1等于嵌套CASE
        + "        WHEN `A` = `B`\n" // 嵌套CASE的WHEN条件：A等于B
        + "        THEN `C`\n" // 嵌套CASE的THEN结果：返回C
        + "        ELSE `D`\n" // 嵌套CASE的ELSE结果：返回D
        + "        END\n" // 嵌套CASE结束
        + "THEN 6\n" // THEN结果：返回6
        + "ELSE 7\n" // ELSE结果：返回7
        + "END"; // CASE表达式结束
    expr(sql) // 创建表达式夹具
        .withWriter(w -> w.withCaseClausesOnNewLines(true)) // 配置写入器，设置CASE子句每行一个
        .expectingFormatted(formatted) // 设置期望的格式化输出
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testCase2() { // 测试CASE表达式的格式化（单行格式）
    final String sql = "case 1" // 定义原始SQL：简单CASE表达式（单行）
        + " when 2 + 3 then 4" // when条件
        + " when case a when b then c else d end then 6" // when条件：嵌套CASE
        + " else 7 end"; // else子句和结束
    final String formatted = "CASE WHEN 1 = 2 + 3 THEN 4" // 定义期望的格式化输出：搜索CASE形式（单行）
        + " WHEN 1 = CASE WHEN `A` = `B` THEN `C` ELSE `D` END THEN 6" // 嵌套CASE
        + " ELSE 7 END"; // ELSE和结束
    expr(sql) // 创建表达式夹具
        .expectingFormatted(formatted) // 设置期望的格式化输出
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testBetween() { // 测试BETWEEN表达式的格式化
    // todo: remove leading
    // 待办：移除前导空格
    expr("x not between symmetric y and z") // 创建表达式：NOT BETWEEN SYMMETRIC
        .expectingFormatted("`X` NOT BETWEEN SYMMETRIC `Y` AND `Z`") // 期望的格式化输出
        .check(); // 执行格式化并检查输出

    // space
    // 空格
  }

  @Test // 标记为测试方法
  void testCast() { // 测试CAST表达式的格式化
    expr("cast(x + y as decimal(5, 10))") // 创建表达式：CAST类型转换
        .expectingFormatted("CAST(`X` + `Y` AS DECIMAL(5, 10))") // 期望的格式化输出
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testLiteralChain() { // 测试字面量链的格式化
    final String sql = "'x' /* comment */ 'y'\n" // 定义原始SQL：包含注释的字符串字面量链
        + "  'z' "; // 第二行字符串
    final String formatted = "'x'\n" // 定义期望的格式化输出：移除注释，每行一个字符串
        + "'y'\n"
        + "'z'";
    expr(sql).expectingFormatted(formatted).check(); // 创建表达式夹具，设置期望输出，执行检查
  }

  @Test // 标记为测试方法
  void testOverlaps() { // 测试OVERLAPS表达式的格式化
    final String sql = "(x,xx) overlaps (y,yy) or x is not null"; // 定义原始SQL：OVERLAPS时间区间重叠检测
    final String formatted = "PERIOD (`X`, `XX`) OVERLAPS PERIOD (`Y`, `YY`)" // 定义期望的格式化输出：PERIOD语法
        + " OR `X` IS NOT NULL"; // OR条件
    expr(sql).expectingFormatted(formatted).check(); // 创建表达式夹具，设置期望输出，执行检查
  }

  @Test // 标记为测试方法
  void testUnion() { // 测试UNION语句的格式化
    final String sql = "select * from t " // 定义原始SQL：嵌套UNION
        + "union select * from (" // 第一个UNION
        + "  select * from u " // 内层UNION第一个查询
        + "  union select * from v) " // 内层UNION第二个查询
        + "union select * from w " // 第二个UNION
        + "order by a, b"; // ORDER BY子句
    sql(sql) // 创建SQL夹具
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testMultiset() { // 测试MULTISET集合类型的格式化
    sql("values (multiset (select * from t))") // 创建SQL语句：VALUES子句包含MULTISET
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testJoinComma() { // 测试逗号连接的JOIN格式化
    final String sql = "select *\n" // 定义原始SQL：使用逗号的传统JOIN语法
        + "from x, y as y1, z, (select * from a, a2 as a3),\n" // 多个表和子查询
        + " (select * from b) as b2\n" // 另一个子查询
        + "where p = q\n" // WHERE条件
        + "and exists (select 1 from v, w)"; // EXISTS子查询
    sql(sql).check(); // 创建SQL夹具，执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testInnerJoin() { // 测试INNER JOIN的格式化
    sql("select * from x inner join y on x.k=y.k") // 创建SQL语句：INNER JOIN
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testJoinTall() { // 测试JOIN的TALL模式格式化
    sql("select * from x inner join y on x.k=y.k left join z using (a)") // 创建SQL语句：INNER JOIN和LEFT JOIN
        .withWriter(c -> c.withLineFolding(SqlWriterConfig.LineFolding.TALL)) // 配置写入器，设置TALL折叠模式
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testJoinTallClauseEndsLine() { // 测试JOIN的TALL模式且子句换行的格式化
    sql("select * from x inner join y on x.k=y.k left join z using (a)") // 创建SQL语句：INNER JOIN和LEFT JOIN
        .withWriter(c -> c.withLineFolding(SqlWriterConfig.LineFolding.TALL) // 配置写入器，设置TALL折叠模式
            .withClauseEndsLine(true)) // 设置子句结束另起一行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testJoinLateralSubQueryTall() { // 测试包含LATERAL子查询的JOIN的TALL模式格式化
    final String sql = "select *\n" // 定义原始SQL：包含LATERAL子查询的复杂JOIN
        + "from (select a from customers where b < c group by d) as c,\n" // 第一个子查询
        + " products,\n" // 第二个表
        + " lateral (select e from orders where exists (\n" // LATERAL子查询
        + "    select 1 from promotions)) as t5\n" // EXISTS子查询
        + "group by f"; // GROUP BY子句
    sql(sql) // 创建SQL夹具
        .withWriter(c -> c.withLineFolding(SqlWriterConfig.LineFolding.TALL)) // 配置写入器，设置TALL折叠模式
        .check(); // 执行格式化并检查输出
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4401">[CALCITE-4401]
   * SqlJoin toString throws RuntimeException</a>. */
  // 测试用例：验证CALCITE-4401问题，SqlJoin的toString方法抛出RuntimeException的修复
  @Test // 标记为测试方法
  void testJoinClauseToString() { // 测试JOIN子句的toString方法
    final String sql = "SELECT t.region_name, t0.o_totalprice\n" // 定义原始SQL：包含JOIN的SELECT语句
        + "FROM (SELECT c_custkey, region_name\n" // 第一个子查询
        + "FROM tpch.out_tpch_vw__customer) AS t\n" // 子查询别名t
        + "INNER JOIN (SELECT o_custkey, o_totalprice\n" // INNER JOIN
        + "FROM tpch.out_tpch_vw__orders) AS t0 ON t.c_custkey = t0.o_custkey"; // 第二个子查询别名t0，连接条件

    final String expectedJoinString = "SELECT *\n" // 定义期望的JOIN字符串格式化输出
        + "FROM (SELECT `C_CUSTKEY`, `REGION_NAME`\n" // 第一个子查询
        + "FROM `TPCH`.`OUT_TPCH_VW__CUSTOMER`) AS `T`\n" // 子查询别名
        + "INNER JOIN (SELECT `O_CUSTKEY`, `O_TOTALPRICE`\n" // INNER JOIN
        + "FROM `TPCH`.`OUT_TPCH_VW__ORDERS`) AS `T0`" // 第二个子查询别名
        + " ON `T`.`C_CUSTKEY` = `T0`.`O_CUSTKEY`"; // 连接条件

    sql(sql) // 创建SQL夹具
        .checkTransformedNode(root -> { // 检查转换后的节点
          assertThat(root, instanceOf(SqlSelect.class)); // 验证根节点是SqlSelect实例
          SqlNode from = ((SqlSelect) root).getFrom(); // 获取FROM子句节点
          assertThat(from, notNullValue()); // 验证FROM节点不为null
          assertThat(from, hasToString(isLinux(expectedJoinString))); // 验证FROM节点的toString输出匹配期望的JOIN字符串
          return from; // 返回FROM节点
        });
  }

  @Test // 标记为测试方法
  void testWhereListItemsOnSeparateLinesOr() { // 测试WHERE列表项换行（OR条件）
    final String sql = "select x" // 定义原始SQL：WHERE子句包含OR条件
        + " from y"
        + " where h is not null and i < j" // AND条件
        + " or ((a or b) is true) and d not in (f,g)" // OR条件和IN操作符
        + " or x <> z"; // 另一个OR条件
    sql(sql) // 创建SQL夹具
        .withWriter(w -> w.withSelectListItemsOnSeparateLines(true) // 配置写入器，设置SELECT列表项换行
            .withSelectListExtraIndentFlag(false) // 设置不额外缩进
            .withWhereListItemsOnSeparateLines(true)) // 设置WHERE列表项换行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testWhereListItemsOnSeparateLinesAnd() { // 测试WHERE列表项换行（AND条件）
    final String sql = "select x" // 定义原始SQL：WHERE子句包含复杂的AND条件
        + " from y"
        + " where h is not null and (i < j" // AND条件和括号
        + " or ((a or b) is true)) and (d not in (f,g)" // 嵌套OR和IN
        + " or v <> ((w * x) + y) * z)"; // 复杂的算术表达式
    sql(sql) // 创建SQL夹具
        .withWriter(w -> w.withSelectListItemsOnSeparateLines(true) // 配置写入器，设置SELECT列表项换行
            .withSelectListExtraIndentFlag(false) // 设置不额外缩进
            .withWhereListItemsOnSeparateLines(true)) // 设置WHERE列表项换行
        .check(); // 执行格式化并检查输出
  }

  /** As {@link #testWhereListItemsOnSeparateLinesAnd()}, but
   * with {@link SqlWriterConfig#clauseEndsLine ClauseEndsLine=true}. */
  // 与testWhereListItemsOnSeparateLinesAnd相同，但设置了ClauseEndsLine=true
  @Test // 标记为测试方法
  void testWhereListItemsOnSeparateLinesAndNewline() { // 测试WHERE列表项换行且子句换行（AND条件）
    final String sql = "select x" // 定义原始SQL：WHERE子句包含复杂的AND条件
        + " from y"
        + " where h is not null and (i < j" // AND条件和括号
        + " or ((a or b) is true)) and (d not in (f,g)" // 嵌套OR和IN
        + " or v <> ((w * x) + y) * z)"; // 复杂的算术表达式
    sql(sql) // 创建SQL夹具
        .withWriter(w -> w.withSelectListItemsOnSeparateLines(true) // 配置写入器，设置SELECT列表项换行
            .withSelectListExtraIndentFlag(false) // 设置不额外缩进
            .withWhereListItemsOnSeparateLines(true) // 设置WHERE列表项换行
            .withClauseEndsLine(true)) // 设置子句结束另起一行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testUpdate() { // 测试UPDATE语句的格式化
    final String sql = "update emp\n" // 定义原始SQL：UPDATE语句
        + "set mgr = mgr + 1, deptno = 5\n" // SET子句：更新多个列
        + "where deptno = 10 and name = 'Fred'"; // WHERE条件
    sql(sql) // 创建SQL夹具
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testUpdateNoLine() { // 测试UPDATE语句不换行的格式化
    final String sql = "update emp\n" // 定义原始SQL：UPDATE语句
        + "set mgr = mgr + 1, deptno = 5\n" // SET子句
        + "where deptno = 10 and name = 'Fred'"; // WHERE条件
    sql(sql) // 创建SQL夹具
        .withWriter(w -> w.withUpdateSetListNewline(false)) // 配置写入器，设置SET列表不换行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testUpdateNoLine2() { // 测试UPDATE语句不换行且子句不换行的格式化
    final String sql = "update emp\n" // 定义原始SQL：UPDATE语句
        + "set mgr = mgr + 1, deptno = 5\n" // SET子句
        + "where deptno = 10 and name = 'Fred'"; // WHERE条件
    sql(sql) // 创建SQL夹具
        .withWriter(w -> w.withUpdateSetListNewline(false) // 配置写入器，设置SET列表不换行
            .withClauseStartsLine(false)) // 设置子句不另起一行
        .check(); // 执行格式化并检查输出
  }

  @Test // 标记为测试方法
  void testInsert() { // 测试INSERT语句的格式化
    sql("insert into t1 select * from t2") // 创建SQL语句：INSERT INTO ... SELECT
        .check(); // 执行格式化并检查输出
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6102">[CALCITE-6102]
   * SqlWriter in SqlInsert's unparse start a list but does not end it</a>. */
  // 测试用例：验证CALCITE-6102问题，SqlInsert的unparse方法中SqlWriter开始列表但不结束的问题
  @Test // 标记为测试方法
  void testSqlWithBodyIsSqlInsert() throws SqlParseException { // 测试SqlWith的body是SqlInsert的情况
    final String withSql = "with tmp as (select * from t1) select 1"; // 定义WITH语句：定义公用表表达式tmp
    final String insertSql = "insert into t2 select * from tmp"; // 定义INSERT语句：插入到t2
    final String expectedSql = "WITH `TMP` AS (SELECT *\n" // 定义期望的格式化输出
        + "FROM `T1`) INSERT INTO `T2`\n" // WITH子句和INSERT语句
        + "SELECT *\n" // SELECT子句
        + "FROM `TMP`"; // FROM子句
    final SqlNode sqlInsert = SqlParser.create(insertSql).parseStmt(); // 解析INSERT语句为SqlNode
    final SqlNode sqlNode = SqlParser.create(withSql).parseQuery(); // 解析WITH语句为SqlNode
    assertThat(sqlNode, instanceOf(SqlWith.class)); // 验证WITH语句是SqlWith实例
    final SqlWith sqlWith = (SqlWith) sqlNode; // 强制转换为SqlWith
    sqlWith.setOperand(1, sqlInsert); // 将WITH语句的body设置为INSERT语句
    assertThat(sqlWith, hasToString(isLinux(expectedSql))); // 验证WITH语句的toString输出匹配期望的SQL
  }

  public static void main(String[] args) throws SqlParseException { // main方法，用于手动测试格式化功能
    final String sql = "select x as a, b as b, c as c, d," // 定义SQL语句：复杂的SELECT查询
        + " 'mixed-Case string'," // 混合大小写字符串
        + " unquotedCamelCaseId," // 未加引号的驼峰标识符
        + " \"quoted id\" " // 加引号的标识符
        + "from" // FROM子句
        + " (select *" // 子查询
        + " from t"
        + " where x = y and a > 5" // WHERE条件
        + " group by z, zz" // GROUP BY
        + " window w as (partition by c)," // WINDOW定义
        + "  w1 as (partition by c,d order by a, b" // 另一个WINDOW定义
        + "   range between interval '2:2' hour to minute preceding" // RANGE子句
        + "    and interval '1' day following)) "
        + "order by gg desc nulls last, hh asc"; // ORDER BY子句
    final SqlNode node = SqlParser.create(sql).parseQuery(); // 解析SQL语句为SqlNode

    final SqlWriterConfig config = SqlPrettyWriter.config() // 创建SqlWriterConfig配置
        .withLineFolding(SqlWriterConfig.LineFolding.STEP) // 设置行折叠模式为STEP
        .withSelectFolding(SqlWriterConfig.LineFolding.TALL) // 设置SELECT子句折叠为TALL
        .withFromFolding(SqlWriterConfig.LineFolding.TALL) // 设置FROM子句折叠为TALL
        .withWhereFolding(SqlWriterConfig.LineFolding.TALL) // 设置WHERE子句折叠为TALL
        .withHavingFolding(SqlWriterConfig.LineFolding.TALL) // 设置HAVING子句折叠为TALL
        .withIndentation(4) // 设置缩进为4个空格
        .withClauseEndsLine(true); // 设置子句结束另起一行
    System.out.println(new SqlPrettyWriter(config).format(node)); // 使用配置创建SqlPrettyWriter，格式化SqlNode并输出结果
  }
}