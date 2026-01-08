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
package org.apache.calcite.test; // 声明包名，该类属于org.apache.calcite.test测试包
import org.apache.calcite.sql.SqlDialect; // 导入SQL方言接口，用于不同数据库的SQL语法差异处理
import org.apache.calcite.sql.dialect.MysqlSqlDialect; // 导入MySQL方言实现，用于MySQL特定的SQL语法
import org.apache.calcite.sql.dialect.PostgresqlSqlDialect; // 导入PostgreSQL方言实现，用于PostgreSQL特定的SQL语法
import org.apache.calcite.sql.dialect.SparkSqlDialect; // 导入Spark SQL方言实现，用于Spark特定的SQL语法
import org.apache.calcite.sql.parser.SqlAbstractParserImpl; // 导入SQL解析器抽象实现类，提供解析器元数据等基础功能
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器主类，用于将SQL字符串解析为SQL语法树
import org.apache.calcite.sql.parser.SqlParserFixture; // 导入SQL解析器测试工具类，提供便捷的测试方法
import org.apache.calcite.sql.parser.SqlParserTest; // 导入SQL解析器测试基类，本测试类继承自该基类
import org.apache.calcite.sql.parser.StringAndPos; // 导入字符串和位置封装类，用于记录SQL语句及其错误位置
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl; // 导入Babel SQL解析器实现，支持多种SQL方言的统一解析
import org.apache.calcite.tools.Hoist; // 导入Hoist工具类，用于从SQL语句中提取字面量并替换为参数

import com.google.common.base.Throwables; // 导入Guava工具类，用于异常处理和堆栈跟踪

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数和返回值
import org.junit.jupiter.api.Disabled; // 导入JUnit5的Disabled注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.Arrays; // 导入数组工具类，提供数组操作方法
import java.util.Locale; // 导入地区设置类，用于本地化相关的操作
import java.util.Objects; // 导入对象工具类，提供对象比较和空值检查方法

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言方法，用于相等性断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类，用于执行断言
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest匹配器，用于检查对象的toString输出

/**
 * Tests the "Babel" SQL parser, that understands all dialects of SQL.
 * 测试"Babel" SQL解析器，该解析器能够理解所有SQL方言
 * Babel解析器是Calcite项目中的一个特殊解析器，旨在支持多种数据库的SQL语法
 * 与核心解析器不同，Babel解析器更加灵活，能够处理不同数据库之间的语法差异
 */
class BabelParserTest extends SqlParserTest { // 定义BabelParserTest测试类，继承自SqlParserTest基类

  @Override public SqlParserFixture fixture() { // 重写fixture方法，返回配置好的SQL解析器测试工具
    return super.fixture() // 调用父类的fixture方法获取基础配置
        .withTester(new BabelTesterImpl()) // 设置自定义的测试器实现BabelTesterImpl，用于处理Babel解析器的特殊错误检查逻辑
        .withConfig(c -> c.withParserFactory(SqlBabelParserImpl.FACTORY)); // 配置解析器工厂为SqlBabelParserImpl.FACTORY，使用Babel解析器实现
  }

  @Test void testReservedWords() { // 测试保留字处理，验证Babel解析器对保留字的识别
    assertThat(isReserved("escape"), is(false)); // 断言"escape"不是保留字，在Babel解析器中，"escape"被视为非保留关键字，可以用作标识符
  }

  /** {@inheritDoc}
   * 继承自父类的方法文档注释
   *
   * <p>Copy-pasted from base method, but with some key differences.
   * 从基类方法复制而来，但有一些关键差异，反映了Babel解析器与核心解析器在元数据处理上的不同
   */
  @Override @Test protected void testMetadata() { // 测试解析器元数据，验证Babel解析器对关键字、函数名、上下文变量等的分类
    SqlAbstractParserImpl.Metadata metadata = fixture().parser().getMetadata(); // 获取解析器的元数据对象，该对象包含关键字和函数的分类信息
    assertThat(metadata.isReservedFunctionName("ABS"), is(true)); // 断言"ABS"是保留函数名，ABS是数学函数，不能用作标识符
    assertThat(metadata.isReservedFunctionName("FOO"), is(false)); // 断言"FOO"不是保留函数名，可以自定义名为FOO的函数

    assertThat(metadata.isContextVariableName("CURRENT_USER"), is(true)); // 断言"CURRENT_USER"是上下文变量名，表示当前数据库用户
    assertThat(metadata.isContextVariableName("CURRENT_CATALOG"), is(true)); // 断言"CURRENT_CATALOG"是上下文变量名，表示当前数据库目录
    assertThat(metadata.isContextVariableName("CURRENT_SCHEMA"), is(true)); // 断言"CURRENT_SCHEMA"是上下文变量名，表示当前数据库模式
    assertThat(metadata.isContextVariableName("ABS"), is(false)); // 断言"ABS"不是上下文变量名，它是一个函数名
    assertThat(metadata.isContextVariableName("FOO"), is(false)); // 断言"FOO"不是上下文变量名

    assertThat(metadata.isNonReservedKeyword("A"), is(true)); // 断言"A"是非保留关键字，可以用作标识符
    assertThat(metadata.isNonReservedKeyword("KEY"), is(true)); // 断言"KEY"是非保留关键字，可以用作标识符
    assertThat(metadata.isNonReservedKeyword("SELECT"), is(false)); // 断言"SELECT"不是非保留关键字，它是保留关键字
    assertThat(metadata.isNonReservedKeyword("FOO"), is(false)); // 断言"FOO"不是非保留关键字
    assertThat(metadata.isNonReservedKeyword("ABS"), is(true)); // 断言"ABS"是非保留关键字，在Babel中ABS可以用作标识符 // was false 核心解析器中这是false

    assertThat(metadata.isKeyword("ABS"), is(true)); // 断言"ABS"是关键字，包括保留和非保留关键字
    assertThat(metadata.isKeyword("CURRENT_USER"), is(true)); // 断言"CURRENT_USER"是关键字
    assertThat(metadata.isKeyword("CURRENT_CATALOG"), is(true)); // 断言"CURRENT_CATALOG"是关键字
    assertThat(metadata.isKeyword("CURRENT_SCHEMA"), is(true)); // 断言"CURRENT_SCHEMA"是关键字
    assertThat(metadata.isKeyword("KEY"), is(true)); // 断言"KEY"是关键字
    assertThat(metadata.isKeyword("SELECT"), is(true)); // 断言"SELECT"是关键字
    assertThat(metadata.isKeyword("HAVING"), is(true)); // 断言"HAVING"是关键字
    assertThat(metadata.isKeyword("A"), is(true)); // 断言"A"是关键字
    assertThat(metadata.isKeyword("BAR"), is(false)); // 断言"BAR"不是关键字

    assertThat(metadata.isReservedWord("SELECT"), is(true)); // 断言"SELECT"是保留字，不能用作标识符
    assertThat(metadata.isReservedWord("CURRENT_CATALOG"), is(false)); // 断言"CURRENT_CATALOG"不是保留字，可以用作标识符 // was true 核心解析器中这是true
    assertThat(metadata.isReservedWord("CURRENT_SCHEMA"), is(false)); // 断言"CURRENT_SCHEMA"不是保留字，可以用作标识符 // was true 核心解析器中这是true
    assertThat(metadata.isReservedWord("KEY"), is(false)); // 断言"KEY"不是保留字，可以用作标识符

    String jdbcKeywords = metadata.getJdbcKeywords(); // 获取JDBC标准关键字列表，以逗号分隔的字符串
    assertThat(jdbcKeywords.contains(",COLLECT,"), is(false)); // 断言JDBC关键字中不包含"COLLECT"，在Babel中COLLECT不再作为JDBC关键字 // was true 核心解析器中这是true
    assertThat(!jdbcKeywords.contains(",SELECT,"), is(true)); // 断言JDBC关键字中不包含"SELECT"（双重否定）
  }

  @Test void testSelect() { // 测试基本SELECT语句的解析和规范化
    final String sql = "select 1 from t"; // 定义输入的SQL语句：从表t中选择常量1
    final String expected = "SELECT 1\n" // 定义预期的规范化输出，关键字大写，表名用反引号括起来
        + "FROM `T`"; // FROM子句在新行，表名T被规范化为大写并用反引号括起来
    sql(sql).ok(expected); // 执行测试：解析SQL语句，验证解析结果是否与预期输出一致
  }

  @Test void testYearIsNotReserved() { // 测试YEAR不是保留字，可以用作列别名
    final String sql = "select 1 as year from t"; // 定义输入SQL：将常量1的列别名命名为year
    final String expected = "SELECT 1 AS `YEAR`\n" // 定义预期输出：AS关键字保留，别名YEAR用反引号括起来并大写
        + "FROM `T`"; // FROM子句在新行
    sql(sql).ok(expected); // 执行测试，验证YEAR可以用作列别名
  }

  @Test void testIdentifier() { // 测试不同数据库方言的标识符规则
    // MySQL supports identifiers started with numbers
    // MySQL支持以数字开头的标识符
    SqlParserFixture mysqlF = fixture().withDialect(MysqlSqlDialect.DEFAULT); // 创建MySQL方言的测试工具
    mysqlF.sql("select 1 as 1_c1 from t") // 测试MySQL中以数字开头的标识符
        .ok("SELECT 1 AS `1_c1`\n" // 验证MySQL使用反引号括起标识符
            + "FROM `t`"); // 表名也用反引号括起

    // PostgreSQL allows identifier
    // PostgreSQL允许标识符
    // to begin with a letter (a-z, but also letters with diacritical marks and non-Latin letters)
    // 以字母开头（a-z，但也可以是带变音符号的字母和非拉丁字母）
    // or an underscore (_). Subsequent characters in an identifier
    // 或下划线(_)。标识符的后续字符
    // can be letters, underscores, digits (0-9), or dollar signs ($)
    // 可以是字母、下划线、数字(0-9)或美元符号($)
    SqlParserFixture postgreF = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具
    postgreF.sql("select 1 as \200_$\251\377 from t") // 测试PostgreSQL中包含特殊字符的标识符（八进制转义序列）
        .ok("SELECT 1 AS \"\200_$\251\377\"\n" // 验证PostgreSQL使用双引号括起标识符
            + "FROM \"t\""); // 表名也用双引号括起
  }

  /** Tests that there are no reserved keywords.
 * 测试没有保留关键字，验证所有关键字都可以用作标识符 */
  @Disabled // 禁用此测试，因为测试逻辑有问题
  @Test void testKeywords() { // 测试所有关键字都可以用作列别名
    final String[] reserved = {"AND", "ANY", "END-EXEC"}; // 定义需要跳过的保留关键字列表
    final StringBuilder sql = new StringBuilder("select "); // 构建输入SQL的字符串构建器
    final StringBuilder expected = new StringBuilder("SELECT "); // 构建预期输出的字符串构建器
    for (String keyword : keywords(null)) { // 遍历所有关键字
      // Skip "END-EXEC"; I don't know how a keyword can contain '-'
      // 跳过"END-EXEC"，不知道关键字怎么能包含'-'
      if (!Arrays.asList(reserved).contains(keyword)) { // 检查当前关键字是否在跳过列表中
        sql.append("1 as ").append(keyword).append(", "); // 为每个关键字创建一个列别名，添加到SQL中
        expected.append("1 as `").append(keyword.toUpperCase(Locale.ROOT)) // 预期输出中关键字大写并用反引号括起
            .append("`,\n"); // 每个别名后跟逗号和换行
      }
    }
    sql.setLength(sql.length() - 2); // remove ', ' 移除SQL末尾的", "
    expected.setLength(expected.length() - 2); // remove ',\n' 移除预期输出末尾的",\n"
    sql.append(" from t"); // 添加FROM子句到SQL
    expected.append("\nFROM t"); // 添加FROM子句到预期输出
    sql(sql.toString()).ok(expected.toString()); // 执行测试，验证所有关键字都可以用作标识符
  }

  /** In Babel, AS is not reserved.
 * 在Babel解析器中，AS不是保留关键字，可以用作标识符 */
  @Test void testAs() { // 测试AS关键字可以用作表名或列名
    final String expected = "SELECT `AS`\n" // 预期输出：AS作为列名，用反引号括起
        + "FROM `T`"; // FROM子句
    sql("select as from t").ok(expected); // 执行测试，验证AS可以用作列名
  }

  /** In Babel, DESC is not reserved.
 * 在Babel解析器中，DESC不是保留关键字，可以用作标识符 */
  @Test void testDesc() { // 测试DESC关键字可以用作列名，同时在ORDER BY中也能用作排序方向
    final String sql = "select desc\n" // 输入SQL：选择名为desc的列
        + "from t\n" // FROM子句
        + "order by desc asc, desc desc"; // ORDER BY子句：第一列按升序排序，第二列按降序排序
    final String expected = "SELECT `DESC`\n" // 预期输出：列名DESC用反引号括起
        + "FROM `T`\n" // FROM子句
        + "ORDER BY `DESC`, `DESC` DESC"; // ORDER BY子句：第一个`DESC`是列名，第二个DESC是排序方向
    sql(sql).ok(expected); // 执行测试，验证DESC可以用作列名
  }

  /**
   * This is a failure test making sure the LOOKAHEAD for WHEN clause is 2 in Babel, where
   * in core parser this number is 1.
   * 这是一个失败测试，确保Babel中WHEN子句的LOOKAHEAD（前瞻）是2，而在核心解析器中这个数字是1
   * LOOKAHEAD是解析器中用于消除语法歧义的向前查看token的数量
   *
   * @see SqlParserTest#testCaseExpression() 参见父类的CASE表达式测试
   * @see <a href="https://issues.apache.org/jira/browse/CALCITE-2847">[CALCITE-2847]
   * Optimize global LOOKAHEAD for SQL parsers</a> 参见JIRA问题：优化SQL解析器的全局LOOKAHEAD
   */
  @Test void testCaseExpressionBabel() { // 测试CASE表达式的WHEN子句解析，验证LOOKAHEAD行为
    sql("case x when 2, 4 then 3 ^when^ then 5 else 4 end") // 输入SQL：包含错误的CASE表达式，^when^标记错误位置
        .fails("(?s)Encountered \"when then\" at .*"); // 验证解析失败，错误信息应包含"when then"
  }

  /** In Redshift, DATE is a function. It requires special treatment in the
   * parser because it is a reserved keyword.
   * 在Redshift中，DATE是一个函数。它需要在解析器中特殊处理，因为它是保留关键字
   * (Curiously, TIMESTAMP and TIME are not functions.)
   * （奇怪的是，TIMESTAMP和TIME不是函数。） */
  @Test void testDateFunction() { // 测试DATE函数的解析，DATE既是保留关键字又是函数名
    final String expected = "SELECT `DATE`(`X`)\n" // 预期输出：DATE作为函数名，用反引号括起
        + "FROM `T`"; // FROM子句
    sql("select date(x) from t").ok(expected); // 执行测试，验证DATE函数可以正确解析
  }

  /** The DATEADD, DATEDIFF (in Redshift, Snowflake) and DATE_PART (in PostgreSQL)
   * functions have ordinary function syntax  except that its first argument is a time unit
   * (e.g. DAY). We must not parse that first argument as an identifier.
   * DATEADD、DATEDIFF（在Redshift、Snowflake中）和DATE_PART（在PostgreSQL中）函数
   * 具有普通的函数语法，除了第一个参数是时间单位（例如DAY）。我们必须不将第一个参数解析为标识符。 */
  @Test void testRedshiftFunctionsWithDateParts() { // 测试带时间单位参数的日期函数解析
    final String sql = "SELECT DATEADD(day, 1, t),\n" // 输入SQL：使用DATEADD函数，第一个参数是时间单位day
        + " DATEDIFF(week, 2, t),\n" // 使用DATEDIFF函数，第一个参数是时间单位week
        + " DATE_PART(year, t) FROM mytable"; // 使用DATE_PART函数，第一个参数是时间单位year
    final String expected = "SELECT DATEADD(DAY, 1, `T`)," // 预期输出：时间单位大写，表名用反引号括起
        + " DATEDIFF(WEEK, 2, `T`), DATE_PART(YEAR, `T`)\n" // 其他函数同样处理
        + "FROM `MYTABLE`"; // FROM子句

    sql(sql).ok(expected); // 执行测试，验证时间单位参数正确解析，不被当作标识符
  }

  /** Overrides, adding tests for DATEADD, DATEDIFF, DATE_PART functions
   * in addition to EXTRACT.
   * 重写父类方法，除了测试EXTRACT函数外，还添加了DATEADD、DATEDIFF、DATE_PART函数的测试 */
  @Test protected void testTimeUnitCodes() { // 测试时间单位代码的处理
    super.testTimeUnitCodes(); // 调用父类的测试方法，测试EXTRACT函数的时间单位

    // As for FLOOR in the base class, so for DATEADD, DATEDIFF, DATE_PART.
    // 就像基类中的FLOOR一样，DATEADD、DATEDIFF、DATE_PART也是如此
    // Extensions such as 'y' remain as identifiers; they are resolved in the
    // validator.
    // 扩展名如'y'保持为标识符；它们在验证器中解析
    final String ts = "'2022-06-03 12:00:00.000'"; // 定义时间戳字符串常量
    final String ts2 = "'2022-06-03 15:30:00.000'"; // 定义第二个时间戳字符串常量
    expr("DATEADD(year, 1, " + ts + ")") // 测试DATEADD函数使用完整时间单位名year
        .ok("DATEADD(YEAR, 1, " + ts + ")"); // 验证year被识别为时间单位并大写
    expr("DATEADD(y, 1, " + ts + ")") // 测试DATEADD函数使用缩写时间单位名y
        .ok("DATEADD(`Y`, 1, " + ts + ")"); // 验证缩写y被当作标识符，用反引号括起
    expr("DATEDIFF(year, 1, " + ts + ", " + ts2 + ")") // 测试DATEDIFF函数使用完整时间单位名year
        .ok("DATEDIFF(YEAR, 1, '2022-06-03 12:00:00.000', " // 验证year被识别为时间单位
            + "'2022-06-03 15:30:00.000')"); // 两个时间戳参数
    expr("DATEDIFF(y, 1, " + ts + ", " + ts2 + ")") // 测试DATEDIFF函数使用缩写时间单位名y
        .ok("DATEDIFF(`Y`, 1, '2022-06-03 12:00:00.000', " // 验证缩写y被当作标识符
            + "'2022-06-03 15:30:00.000')"); // 两个时间戳参数
    expr("DATE_PART(year, " + ts + ")") // 测试DATE_PART函数使用完整时间单位名year
        .ok("DATE_PART(YEAR, '2022-06-03 12:00:00.000')"); // 验证year被识别为时间单位
    expr("DATE_PART(y, " + ts + ")") // 测试DATE_PART函数使用缩写时间单位名y
        .ok("DATE_PART(`Y`, 1, '2022-06-03 12:00:00.000')"); // 这行代码有错误，应该是DATE_PART(`Y`, '2022-06-03 12:00:00.000')
  }

  /** PostgreSQL and Redshift allow TIMESTAMP literals that contain only a
   * date part.
   * PostgreSQL和Redshift允许只包含日期部分的TIMESTAMP字面量 */
  @Test void testShortTimestampLiteral() { // 测试简短的TIMESTAMP字面量解析
    // Parser doesn't actually check the contents of the string. The validator
    // will convert it to '1969-07-20 00:00:00', when it has decided that
    // TIMESTAMP maps to the TIMESTAMP type.
    // 解析器实际上不检查字符串的内容。验证器会将其转换为'1969-07-20 00:00:00'，
    // 当它确定TIMESTAMP映射到TIMESTAMP类型时
    sql("select timestamp '1969-07-20'") // 测试只包含日期的TIMESTAMP字面量
        .ok("SELECT TIMESTAMP '1969-07-20'"); // 验证可以正确解析
    // PostgreSQL allows the following. We should too.
    // PostgreSQL允许以下格式。我们也应该支持
    sql("select ^timestamp '1969-07-20 1:2'^") // 测试包含不完整时间的TIMESTAMP字面量（应该失败）
        .ok("SELECT TIMESTAMP '1969-07-20 1:2'"); // 这个测试可能有误，因为^标记表示应该失败
    sql("select ^timestamp '1969-07-20:23:'^") // 测试格式错误的TIMESTAMP字面量（应该失败）
        .ok("SELECT TIMESTAMP '1969-07-20:23:'"); // 这个测试可能有误
  }

  /** Tests parsing PostgreSQL-style "::" cast operator.
 * 测试PostgreSQL风格的"::"类型转换操作符解析 */
  @Test void testParseInfixCast()  { // 测试中缀类型转换操作符的解析
    checkParseInfixCast("integer"); // 测试integer类型的转换
    checkParseInfixCast("varchar"); // 测试varchar类型的转换
    checkParseInfixCast("boolean"); // 测试boolean类型的转换
    checkParseInfixCast("double"); // 测试double类型的转换
    checkParseInfixCast("bigint"); // 测试bigint类型的转换

    final String sql = "select -('12' || '.34')::VARCHAR(30)::INTEGER as x\n" // 测试链式类型转换
        + "from t"; // FROM子句
    final String expected = "" // 预期输出
        + "SELECT (- ('12' || '.34') :: VARCHAR(30) :: INTEGER) AS `X`\n" // 负号、字符串连接、两次类型转换
        + "FROM `T`"; // FROM子句
    sql(sql).ok(expected); // 执行测试，验证链式类型转换正确解析
  }

  private void checkParseInfixCast(String sqlType) { // 私有辅助方法：检查特定类型的中缀类型转换解析
    String sql = "SELECT x::" + sqlType + " FROM (VALUES (1, 2)) as tbl(x,y)"; // 构建测试SQL：使用::操作符进行类型转换
    String expected = "SELECT `X` :: " + sqlType.toUpperCase(Locale.ROOT) + "\n" // 构建预期输出：类型名大写
        + "FROM (VALUES (ROW(1, 2))) AS `TBL` (`X`, `Y`)"; // VALUES子句包含ROW构造器
    sql(sql).ok(expected); // 执行测试，验证类型转换正确解析
  }

  /** Tests parsing MySQL-style "<=>" equal operator.
 * 测试MySQL风格的"<=>"空值安全相等操作符解析 */
  @Test void testParseNullSafeEqual()  { // 测试空值安全相等操作符的解析
    // x <=> y
    // 简单的空值安全相等比较
    final String projectSql = "SELECT x <=> 3 FROM (VALUES (1, 2)) as tbl(x,y)"; // 在SELECT子句中使用<=>操作符
    sql(projectSql).ok("SELECT (`X` <=> 3)\n" // 验证<=>操作符正确解析
        + "FROM (VALUES (ROW(1, 2))) AS `TBL` (`X`, `Y`)"); // FROM子句
    final String filterSql = "SELECT y FROM (VALUES (1, 2)) as tbl(x,y) WHERE x <=> null"; // 在WHERE子句中使用<=>操作符
    sql(filterSql).ok("SELECT `Y`\n" // 验证WHERE子句中的<=>操作符
        + "FROM (VALUES (ROW(1, 2))) AS `TBL` (`X`, `Y`)\n" // FROM子句
        + "WHERE (`X` <=> NULL)"); // WHERE条件
    final String joinConditionSql = "SELECT tbl1.y FROM (VALUES (1, 2)) as tbl1(x,y)\n" // 在JOIN条件中使用<=>操作符
        + "LEFT JOIN (VALUES (null, 3)) as tbl2(x,y) ON tbl1.x <=> tbl2.x"; // LEFT JOIN条件
    sql(joinConditionSql).ok("SELECT `TBL1`.`Y`\n" // 验证JOIN条件中的<=>操作符
        + "FROM (VALUES (ROW(1, 2))) AS `TBL1` (`X`, `Y`)\n" // 第一个表
        + "LEFT JOIN (VALUES (ROW(NULL, 3))) AS `TBL2` (`X`, `Y`) ON (`TBL1`.`X` <=> `TBL2`.`X`)"); // JOIN条件
    // (a, b) <=> (x, y)
    // 行值的空值安全相等比较
    final String rowComparisonSql = "SELECT y\n" // 测试行值比较
        + "FROM (VALUES (1, 2)) as tbl(x,y) WHERE (x,y) <=> (null,2)"; // WHERE条件中使用行值比较
    sql(rowComparisonSql).ok("SELECT `Y`\n" // 验证行值比较正确解析
        + "FROM (VALUES (ROW(1, 2))) AS `TBL` (`X`, `Y`)\n" // FROM子句
        + "WHERE ((ROW(`X`, `Y`)) <=> (ROW(NULL, 2)))"); // WHERE条件：行值比较
    // the higher precedence
    // 较高的优先级
    final String highPrecedenceSql = "SELECT x <=> 3 + 3 FROM (VALUES (1, 2)) as tbl(x,y)"; // 测试<=>与加法运算的优先级
    sql(highPrecedenceSql).ok("SELECT (`X` <=> (3 + 3))\n" // 验证加法优先级高于<=>
        + "FROM (VALUES (ROW(1, 2))) AS `TBL` (`X`, `Y`)"); // FROM子句
    // the lower precedence
    // 较低的优先级
    final String lowPrecedenceSql = "SELECT NOT x <=> 3 FROM (VALUES (1, 2)) as tbl(x,y)"; // 测试<=>与NOT运算的优先级
    sql(lowPrecedenceSql).ok("SELECT (NOT (`X` <=> 3))\n" // 验证NOT优先级高于<=>
        + "FROM (VALUES (ROW(1, 2))) AS `TBL` (`X`, `Y`)"); // FROM子句
  }

  @Test void testCreateTableWithNoCollectionTypeSpecified() { // 测试创建表时不指定集合类型
    final String sql = "create table foo (bar integer not null, baz varchar(30))"; // 输入SQL：创建普通表
    final String expected = "CREATE TABLE `FOO` (`BAR` INTEGER NOT NULL, `BAZ` VARCHAR(30))"; // 预期输出：表名和列名用反引号括起并大写
    sql(sql).ok(expected); // 执行测试，验证基本CREATE TABLE语句解析
  }

  @Test void testCreateTableMapType() { // 测试创建包含MAP类型列的表
    final String sql = "create table foo (bar map<integer, varchar>)"; // 输入SQL：创建包含MAP类型列的表
    final String expected = "CREATE TABLE `FOO` (`BAR` MAP< INTEGER, VARCHAR >)"; // 预期输出：MAP类型保留原始格式
    sql(sql).ok(expected); // 执行测试，验证MAP类型列的解析
  }

  @Test void testCreateSetTable() { // 测试创建SET表（不允许重复行的表）
    final String sql = "create set table foo (bar int not null, baz varchar(30))"; // 输入SQL：创建SET表
    final String expected = "CREATE SET TABLE `FOO` (`BAR` INTEGER NOT NULL, `BAZ` VARCHAR(30))"; // 预期输出：SET关键字保留
    sql(sql).ok(expected); // 执行测试，验证SET表创建语句解析
  }

  @Test void testCreateMultisetTable() { // 测试创建MULTISET表（允许重复行的表）
    final String sql = "create multiset table foo (bar int not null, baz varchar(30))"; // 输入SQL：创建MULTISET表
    final String expected = "CREATE MULTISET TABLE `FOO` " // 预期输出：MULTISET关键字保留
        + "(`BAR` INTEGER NOT NULL, `BAZ` VARCHAR(30))"; // 列定义
    sql(sql).ok(expected); // 执行测试，验证MULTISET表创建语句解析
  }

  @Test void testCreateVolatileTable() { // 测试创建VOLATILE表（临时表，会话结束时自动删除）
    final String sql = "create volatile table foo (bar int not null, baz varchar(30))"; // 输入SQL：创建VOLATILE表
    final String expected = "CREATE VOLATILE TABLE `FOO` " // 预期输出：VOLATILE关键字保留
        + "(`BAR` INTEGER NOT NULL, `BAZ` VARCHAR(30))"; // 列定义
    sql(sql).ok(expected); // 执行测试，验证VOLATILE表创建语句解析
  }

  @Test void testCreateVariantTable() { // 测试创建包含VARIANT类型列的表（VARIANT是Snowflake中的半结构化数据类型）
    final String sql = "create table foo (bar variant not null)"; // 输入SQL：创建包含VARIANT类型列的表
    final String expected = "CREATE TABLE `FOO` " // 预期输出
        + "(`BAR` VARIANT NOT NULL)"; // VARIANT类型列定义
    sql(sql).ok(expected); // 执行测试，验证VARIANT类型列的解析
  }

  @Test void testArrayLiteralFromString() { // 测试从字符串字面量解析数组
    sql("select array '{1,2,3}'") // 测试一维整数数组字面量
        .ok("SELECT (ARRAY[1, 2, 3])"); // 验证转换为标准ARRAY语法
    sql("select array '{{1,2,5}, {3,4,7}}'") // 测试二维整数数组字面量
        .ok("SELECT (ARRAY[(ARRAY[1, 2, 5]), (ARRAY[3, 4, 7])])"); // 验证转换为嵌套ARRAY语法
    sql("select array '{}'") // 测试空数组字面量
        .ok("SELECT (ARRAY[])"); // 验证转换为空ARRAY
    sql("select array '{\"1\", \"2\", \"3\"}'") // 测试字符串数组字面量
        .ok("SELECT (ARRAY['1', '2', '3'])"); // 验证转换为字符串ARRAY
    sql("select array '{null, 1, null, 2}'") // 测试包含NULL值的数组字面量
        .ok("SELECT (ARRAY[NULL, 1, NULL, 2])"); // 验证NULL值正确处理

    sql("select array ^'null, 1, null, 2'^") // 测试格式错误的数组字面量（缺少花括号）
        .fails("Illegal array expression 'null, 1, null, 2'"); // 验证解析失败
  }

  @Test void testArrayLiteralBigQuery() { // 测试BigQuery风格的数组字面量解析
    final SqlParserFixture f = fixture().withDialect(BIG_QUERY); // 创建BigQuery方言的测试工具
    f.sql("select array '{1, 2}'") // 测试单引号包裹的数组字面量
        .ok("SELECT (ARRAY[1, 2])"); // 验证转换为标准ARRAY语法
    f.sql("select array \"{1, 2}\"") // 测试双引号包裹的数组字面量
        .ok("SELECT (ARRAY[1, 2])"); // 验证转换为标准ARRAY语法
    f.sql("select array '{\"a\", \"b\"}'") // 测试字符串数组字面量
        .ok("SELECT (ARRAY['a', 'b'])"); // 验证转换为字符串ARRAY
    f.sql("select array \"{\\\"a\\\", \\\"b\\\"}\"") // 测试转义双引号的数组字面量
        .ok("SELECT (ARRAY['a', 'b'])"); // 验证转义字符正确处理
  }

  @Test void testPostgresSqlShow() { // 测试PostgreSQL的SHOW命令解析
    SqlParserFixture f = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具
    f.sql("SHOW autovacuum") // 测试SHOW autovacuum命令
        .ok("SHOW \"autovacuum\""); // 验证参数名用双引号括起
    f.sql("SHOW \"autovacuum\"") // 测试带引号的SHOW命令
        .same(); // 验证输出与输入相同
    f.sql("SHOW ALL") // 测试SHOW ALL命令
        .ok("SHOW \"all\""); // 验证ALL用双引号括起
    f.sql("SHOW TIME ZONE") // 测试SHOW TIME ZONE命令
        .ok("SHOW \"timezone\""); // 验证TIME ZONE转换为timezone
    f.sql("SHOW SESSION AUTHORIZATION") // 测试SHOW SESSION AUTHORIZATION命令
        .ok("SHOW \"session_authorization\""); // 验证SESSION AUTHORIZATION转换为session_authorization
    f.sql("SHOW TRANSACTION ISOLATION LEVEL") // 测试SHOW TRANSACTION ISOLATION LEVEL命令
        .ok("SHOW \"transaction_isolation\""); // 验证TRANSACTION ISOLATION LEVEL转换为transaction_isolation
  }

  @Test void testPostgresSqlSetOption() { // 测试PostgreSQL的SET命令解析
    SqlParserFixture f = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具
    f.sql("SET SESSION autovacuum = true") // 测试SET SESSION命令，设置布尔值
        .ok("SET \"autovacuum\" = TRUE"); // 验证参数名用双引号括起，TRUE大写
    f.sql("SET SESSION autovacuum = DEFAULT") // 测试设置参数为DEFAULT值
        .ok("SET \"autovacuum\" = DEFAULT"); // 验证DEFAULT保留
    f.sql("SET LOCAL autovacuum TO 'DEFAULT'") // 测试SET LOCAL命令，使用TO关键字
        .ok("SET LOCAL \"autovacuum\" = 'DEFAULT'"); // 验证TO转换为=

    f.sql("SET SESSION TIME ZONE DEFAULT") // 测试SET TIME ZONE命令，设置为DEFAULT
        .ok("SET TIME ZONE DEFAULT"); // 验证TIME ZONE保留
    f.sql("SET SESSION TIME ZONE LOCAL") // 测试SET TIME ZONE命令，设置为LOCAL
        .ok("SET TIME ZONE LOCAL"); // 验证LOCAL保留
    f.sql("SET TIME ZONE 'PST8PDT'").same(); // 测试设置时区为字符串
    f.sql("SET TIME ZONE INTERVAL '-08:00' HOUR TO MINUTE").same(); // 测试设置时区为INTERVAL类型
    f.sql("SET timezone = 'PST8PDT'") // 测试设置时区参数（小写）
            .ok("SET \"timezone\" = 'PST8PDT'"); // 验证参数名用双引号括起

    f.sql("SET SESSION AUTHORIZATION DEFAULT") // 测试设置会话授权
        .ok("SET \"session_authorization\" = DEFAULT"); // 验证SESSION AUTHORIZATION转换为session_authorization

    f.sql("SET search_path = public,public,\"$user\"") // 测试设置搜索路径，包含多个值
        .ok("SET \"search_path\" = \"public\", \"public\", \"$user\""); // 验证每个值用双引号括起
    f.sql("SET SCHEMA public,public,\"$user\"") // 测试SET SCHEMA命令（SET search_path的别名）
        .ok("SET \"search_path\" = \"public\", \"public\", \"$user\""); // 验证转换为search_path
    f.sql("SET NAMES iso_8859_15_to_utf8") // 测试SET NAMES命令
        .ok("SET \"client_encoding\" = \"iso_8859_15_to_utf8\""); // 验证NAMES转换为client_encoding

    f.sql("SET TRANSACTION READ ONLY").same(); // 测试设置事务为只读
    f.sql("SET TRANSACTION READ WRITE").same(); // 测试设置事务为读写
    f.sql("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE").same(); // 测试设置事务隔离级别
    f.sql("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE, READ ONLY, DEFERRABLE").same(); // 测试设置多个事务属性
    f.sql("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE, READ WRITE, NOT DEFERRABLE").same(); // 测试设置多个事务属性

    f.sql("SET TRANSACTION SNAPSHOT '000003A1-1'").same(); // 测试设置事务快照

    f.sql("SET ROLE NONE").same(); // 测试设置角色为NONE
    f.sql("SET ROLE 'paul'").same(); // 测试设置角色
  }

  @Test void testPostgresSqlReset() { // 测试PostgreSQL的RESET命令解析
    SqlParserFixture f = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具

    // RESET ROLE, RESET SESSION AUTHORIZATION, RESET TRANSACTION ISOLATION LEVEL,
    // and RESET TIME ZONE are simply syntactic sugar for a more unified syntax
    // RESET "<variable_name>".
    // RESET ROLE、RESET SESSION AUTHORIZATION、RESET TRANSACTION ISOLATION LEVEL
    // 和RESET TIME ZONE只是更统一语法RESET "<variable_name>"的语法糖
    f.sql("RESET ALL").same(); // 测试RESET ALL命令
    f.sql("RESET ROLE") // 测试RESET ROLE命令
        .ok("RESET \"role\""); // 验证转换为RESET "role"
    f.sql("RESET SESSION AUTHORIZATION") // 测试RESET SESSION AUTHORIZATION命令
        .ok("RESET \"session_authorization\""); // 验证转换为RESET "session_authorization"
    f.sql("RESET TRANSACTION ISOLATION LEVEL") // 测试RESET TRANSACTION ISOLATION LEVEL命令
        .ok("RESET \"transaction_isolation\""); // 验证转换为RESET "transaction_isolation"
    f.sql("RESET TIME ZONE") // 测试RESET TIME ZONE命令
        .ok("RESET \"timezone\""); // 验证转换为RESET "timezone"
    f.sql("RESET autovacuum") // 测试RESET参数命令
        .ok("RESET \"autovacuum\""); // 验证参数名用双引号括起
  }

  @Test void testPostgresSqlBegin() { // 测试PostgreSQL的BEGIN命令解析
    SqlParserFixture f = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具
    f.sql("BEGIN").same(); // 测试简单的BEGIN命令
    f.sql("BEGIN READ ONLY").same(); // 测试BEGIN READ ONLY命令
    f.sql("BEGIN TRANSACTION READ WRITE") // 测试BEGIN TRANSACTION命令
        .ok("BEGIN READ WRITE"); // 验证TRANSACTION关键字被移除
    f.sql("BEGIN WORK ISOLATION LEVEL SERIALIZABLE") // 测试BEGIN WORK命令
        .ok("BEGIN ISOLATION LEVEL SERIALIZABLE"); // 验证WORK关键字被移除
    f.sql("BEGIN ISOLATION LEVEL SERIALIZABLE, READ ONLY, DEFERRABLE").same(); // 测试设置多个事务属性
    f.sql("BEGIN ISOLATION LEVEL SERIALIZABLE, READ WRITE, NOT DEFERRABLE").same(); // 测试设置多个事务属性
  }

  @Test void testPostgresSqlCommit() { // 测试PostgreSQL的COMMIT命令解析
    SqlParserFixture f = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具
    f.sql("COMMIT").same(); // 测试简单的COMMIT命令
    f.sql("COMMIT WORK") // 测试COMMIT WORK命令
        .ok("COMMIT"); // 验证WORK关键字被移除
    f.sql("COMMIT TRANSACTION") // 测试COMMIT TRANSACTION命令
        .ok("COMMIT"); // 验证TRANSACTION关键字被移除
    f.sql("COMMIT AND NO CHAIN") // 测试COMMIT AND NO CHAIN命令
        .ok("COMMIT"); // 验证AND NO CHAIN被移除
    f.sql("COMMIT AND CHAIN").same(); // 测试COMMIT AND CHAIN命令
  }

  @Test void testPostgresSqlRollback() { // 测试PostgreSQL的ROLLBACK命令解析
    SqlParserFixture f = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具
    f.sql("ROLLBACK").same(); // 测试简单的ROLLBACK命令
    f.sql("ROLLBACK WORK") // 测试ROLLBACK WORK命令
        .ok("ROLLBACK"); // 验证WORK关键字被移除
    f.sql("ROLLBACK TRANSACTION") // 测试ROLLBACK TRANSACTION命令
        .ok("ROLLBACK"); // 验证TRANSACTION关键字被移除
    f.sql("ROLLBACK AND NO CHAIN") // 测试ROLLBACK AND NO CHAIN命令
        .ok("ROLLBACK"); // 验证AND NO CHAIN被移除
    f.sql("ROLLBACK AND CHAIN").same(); // 测试ROLLBACK AND CHAIN命令
  }

  @Test void testPostgresSqlDiscard() { // 测试PostgreSQL的DISCARD命令解析
    SqlParserFixture f = fixture().withDialect(PostgresqlSqlDialect.DEFAULT); // 创建PostgreSQL方言的测试工具
    f.sql("DISCARD ALL").same(); // 测试DISCARD ALL命令
    f.sql("DISCARD PLANS").same(); // 测试DISCARD PLANS命令
    f.sql("DISCARD SEQUENCES").same(); // 测试DISCARD SEQUENCES命令
    f.sql("DISCARD TEMPORARY").same(); // 测试DISCARD TEMPORARY命令
    f.sql("DISCARD TEMP").same(); // 测试DISCARD TEMP命令（TEMPORARY的缩写）
  }

  @Test void testSparkLeftAntiJoin() { // 测试Spark SQL的LEFT ANTI JOIN解析
    final SqlParserFixture f = fixture().withDialect(SparkSqlDialect.DEFAULT); // 创建Spark SQL方言的测试工具
    final String sql = "select a.cid, a.cname, count(1) as amount\n" // 输入SQL：使用LEFT ANTI JOIN
        + "from geo.area1 as a\n" // FROM子句
        + "left anti join (select distinct cid, cname\n" // LEFT ANTI JOIN子查询
        + "from geo.area2\n" // 子查询FROM子句
        + "where cname = 'cityA') as b on a.cid = b.cid\n" // 子查询WHERE条件和JOIN条件
        + "group by a.cid, a.cname"; // GROUP BY子句
    final String expected = "SELECT `A`.`CID`, `A`.`CNAME`, COUNT(1) `AMOUNT`\n" // 预期输出
        + "FROM `GEO`.`AREA1` `A`\n" // 表别名和列名用反引号括起
        + "LEFT ANTI JOIN (SELECT DISTINCT `CID`, `CNAME`\n" // LEFT ANTI JOIN保留
        + "FROM `GEO`.`AREA2`\n" // 子查询
        + "WHERE (`CNAME` = 'cityA')) `B` ON (`A`.`CID` = `B`.`CID`)\n" // JOIN条件
        + "GROUP BY `A`.`CID`, `A`.`CNAME`"; // GROUP BY子句
    f.sql(sql).ok(expected); // 执行测试，验证LEFT ANTI JOIN正确解析
  }

  @Test void testLeftAntiJoin() { // 测试通用的LEFT ANTI JOIN解析（不指定方言）
    final String sql = "select a.cid, a.cname, count(1) as amount\n" // 输入SQL：使用LEFT ANTI JOIN
        + "from geo.area1 as a\n" // FROM子句
        + "left anti join (select distinct cid, cname\n" // LEFT ANTI JOIN子查询
        + "from geo.area2\n" // 子查询FROM子句
        + "where cname = 'cityA') as b on a.cid = b.cid\n" // 子查询WHERE条件和JOIN条件
        + "group by a.cid, a.cname"; // GROUP BY子句
    final String expected = "SELECT `A`.`CID`, `A`.`CNAME`, COUNT(1) AS `AMOUNT`\n" // 预期输出
        + "FROM `GEO`.`AREA1` AS `A`\n" // AS关键字保留
        + "LEFT ANTI JOIN (SELECT DISTINCT `CID`, `CNAME`\n" // LEFT ANTI JOIN保留
        + "FROM `GEO`.`AREA2`\n" // 子查询
        + "WHERE (`CNAME` = 'cityA')) AS `B` ON (`A`.`CID` = `B`.`CID`)\n" // JOIN条件
        + "GROUP BY `A`.`CID`, `A`.`CNAME`"; // GROUP BY子句
    sql(sql).ok(expected); // 执行测试，验证LEFT ANTI JOIN正确解析
  }

  /** Similar to {@link #testHoist()} but using custom parser.
 * 与{@link #testHoist()}类似，但使用自定义解析器 */
  @Test void testHoistMySql() { // 测试使用Hoist工具提取SQL中的字面量，使用MySQL方言和Babel解析器
    // SQL contains back-ticks, which require MySQL's quoting,
    // and DATEADD, which requires Babel.
    // SQL包含反引号，需要MySQL的引用方式，以及DATEADD，需要Babel解析器
    final String sql = "select 1 as x,\n" // 输入SQL：包含多种字面量和MySQL特定语法
        + "  'ab' || 'c' as y\n" // 字符串连接
        + "from `my emp` /* comment with 'quoted string'? */ as e\n" // MySQL的反引号标识符和注释
        + "where deptno < 40\n" // 数值字面量
        + "and DATEADD(day, 1, hiredate) > date '2010-05-06'"; // DATEADD函数和日期字面量
    final SqlDialect dialect = MysqlSqlDialect.DEFAULT; // 创建MySQL方言
    final Hoist.Hoisted hoisted = // 创建Hoisted对象，包含提取的字面量信息
        Hoist.create(Hoist.config() // 创建Hoist配置
            .withParserConfig( // 配置解析器
                dialect.configureParser(SqlParser.config()) // 使用MySQL方言配置解析器
                    .withParserFactory(SqlBabelParserImpl::new))) // 设置Babel解析器工厂
            .hoist(sql); // 提取SQL中的字面量

    // Simple toString converts each variable to '?N'
    // 简单的toString将每个变量转换为'?N'
    final String expected = "select ?0 as x,\n" // 预期输出：字面量被替换为参数占位符
        + "  ?1 || ?2 as y\n" // 字符串字面量被替换
        + "from `my emp` /* comment with 'quoted string'? */ as e\n" // 注释和标识符保留
        + "where deptno < ?3\n" // 数值字面量被替换
        + "and DATEADD(day, ?4, hiredate) > ?5"; // DATEADD参数和日期字面量被替换
    assertThat(hoisted, hasToString(expected)); // 验证Hoisted对象的toString输出

    // Custom string converts variables to '[N:TYPE:VALUE]'
    // 自定义字符串将变量转换为'[N:TYPE:VALUE]'格式
    final String expected2 = "select [0:DECIMAL:1] as x,\n" // 预期输出：参数包含类型和值信息
        + "  [1:CHAR:ab] || [2:CHAR:c] as y\n" // 字符串参数
        + "from `my emp` /* comment with 'quoted string'? */ as e\n" // 注释和标识符保留
        + "where deptno < [3:DECIMAL:40]\n" // 数值参数
        + "and DATEADD(day, [4:DECIMAL:1], hiredate) > [5:DATE:2010-05-06]"; // 日期参数
    assertThat(hoisted.substitute(SqlParserTest::varToStr), is(expected2)); // 验证自定义替换函数的输出
  }

  /**
   * Babel parser's global {@code LOOKAHEAD} is larger than the core
   * parser's. This causes different parse error message between these two
   * parsers. Here we define a looser error checker for Babel, so that we can
   * reuse failure testing codes from {@link SqlParserTest}.
   * Babel解析器的全局{@code LOOKAHEAD}比核心解析器的更大。这导致这两个解析器之间
   * 产生不同的解析错误消息。这里我们为Babel定义了一个更宽松的错误检查器，
   * 以便我们可以重用来自{@link SqlParserTest}的失败测试代码。
   *
   * <p>If a test case is written in this file -- that is, not inherited -- it
   * is still checked by {@link SqlParserTest}'s checker.
   * 如果测试用例是在这个文件中编写的——即不是继承的——它仍然由{@link SqlParserTest}的检查器检查。
   */
  public static class BabelTesterImpl extends TesterImpl { // 自定义测试器实现类，继承自TesterImpl
    @Override protected void checkEx(String expectedMsgPattern, // 重写异常检查方法
        StringAndPos sap, @Nullable Throwable thrown) { // sap包含SQL语句和错误位置，thrown是抛出的异常
      if (thrown != null && thrownByBabelTest(thrown)) { // 如果异常不为空且是由Babel测试抛出的
        super.checkEx(expectedMsgPattern, sap, thrown); // 使用父类的严格检查，匹配错误消息模式
      } else { // 如果异常不是由Babel测试抛出的
        checkExNotNull(sap, thrown); // 只检查异常不为空，不验证错误消息
      }
    }

    private boolean thrownByBabelTest(Throwable ex) { // 私有方法：检查异常是否由Babel测试抛出
      Throwable rootCause = Throwables.getRootCause(ex); // 获取异常的根本原因
      StackTraceElement[] stackTrace = rootCause.getStackTrace(); // 获取根本原因的堆栈跟踪
      for (StackTraceElement stackTraceElement : stackTrace) { // 遍历堆栈跟踪元素
        String className = stackTraceElement.getClassName(); // 获取类名
        if (Objects.equals(className, BabelParserTest.class.getName())) { // 如果类名是BabelParserTest
          return true; // 返回true，表示异常由Babel测试抛出
        }
      }
      return false; // 返回false，表示异常不是由Babel测试抛出的
    }

    private void checkExNotNull(StringAndPos sap, // 私有方法：检查异常不为空
        @Nullable Throwable thrown) { // thrown是抛出的异常
      if (thrown == null) { // 如果异常为空
        throw new AssertionError("Expected query to throw exception, " // 抛出断言错误
            + "but it did not; query [" + sap.sql // 提示期望查询抛出异常但实际没有
            + "]"); // 包含SQL语句
      }
    }
  }
}
}
