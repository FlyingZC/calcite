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
package org.apache.calcite.test;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.avatica.util.Quoting;
import org.apache.calcite.avatica.util.TimeUnit;
import org.apache.calcite.config.Lex;
import org.apache.calcite.rel.type.DelegatingTypeSystem;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeSystem;
import org.apache.calcite.rel.type.TimeFrameSet;
import org.apache.calcite.runtime.CalciteContextException;
import org.apache.calcite.sql.SqlBasicFunction;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlCollation;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlFunctionCategory;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.fun.SqlLibrary;
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.type.ArraySqlType;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.sql.util.SqlOperatorTables;
import org.apache.calcite.sql.util.SqlShuttle;
import org.apache.calcite.sql.validate.SqlAbstractConformance;
import org.apache.calcite.sql.validate.SqlConformance;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.sql.validate.SqlDelegatingConformance;
import org.apache.calcite.sql.validate.SqlMonotonicity;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorCatalogReader;
import org.apache.calcite.sql.validate.SqlValidatorImpl;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.test.catalog.CountingFactory;
import org.apache.calcite.test.catalog.MustFilterMockCatalogReader;
import org.apache.calcite.testlib.annotations.LocaleEnUs;
import org.apache.calcite.util.Bug;
import org.apache.calcite.util.ImmutableBitSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.apache.calcite.test.Matchers.isCharset;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static java.util.Arrays.asList;

/**
 * Concrete child class of {@link SqlValidatorTestCase}, containing lots of unit
 * tests.
 *
 * <p>If you want to run these same tests in a different environment, create a
 * derived class whose {@link #fixture()} returns a different implementation of
 * {@link SqlValidatorFixture}.
 */
@LocaleEnUs

/**
 * SqlValidatorTest - Calcite SQL验证器综合测试类
 *
 * 本类是SqlValidatorTestCase的具体实现子类，包含了Calcite SQL验证器的全面测试用例集合。
 * 这些测试用例覆盖了SQL验证器的各个功能模块，确保SQL语法、语义和类型系统的正确性。
 *
 * 核心职责：
 * 1. 验证SQL语法解析的正确性
 * 2. 检查SQL表达式的类型兼容性
 * 3. 验证SQL语义的合法性（表名、列名、函数名等）
 * 4. 测试SQL高级特性的验证（聚合、窗口函数、子查询等）
 * 5. 验证不同SQL方言的兼容性处理
 * 6. 测试流式SQL（Streaming SQL）的特殊验证逻辑
 *
 * 测试分类：
 * - 字面量测试：测试各种SQL字面量的类型推导
 * - 操作符测试：测试算术、逻辑、比较等操作符
 * - 函数测试：测试SQL标准函数和自定义函数
 * - 类型测试：测试类型推导和类型转换
 * - 聚合测试：测试GROUP BY、HAVING等聚合功能
 * - 子查询测试：测试EXISTS、IN、标量子查询等
 * - 窗口函数测试：测试OVER窗口规范
 * - 流式查询测试：测试流SQL的特殊约束
 *
 * 测试方法命名约定：
 * - test[Feature]：正常情况的功能测试
 * - test[Feature]Fails：异常情况的错误检测测试
 * - test[Feature]Type：类型推导相关的测试
 *
 * 扩展使用：
 * 如果需要在不同的环境中运行这些测试（如不同的目录表、不同的SQL方言），
 * 可以创建一个派生类，重写fixture()方法返回自定义的SqlValidatorFixture实现。
 *
 * @see SqlValidatorTestCase
 * @see SqlValidatorFixture
 * @see SqlValidator
 * @see SqlValidatorImpl
 */public class SqlValidatorTest extends SqlValidatorTestCase {
  //~ Static fields/initializers ---------------------------------------------

  // CHECKSTYLE: IGNORE 1
  /** @deprecated Deprecated so that usages of this constant will show up in
   * yellow in Intellij and maybe someone will fix them. */
  protected static final boolean TODO = false;  // TODO标记：标记未完成的功能，已弃用以便在IDE中显示黄色警告
  private static final String ANY = "(?s).*";  // 正则表达式常量：匹配任意字符串（包括换行符），用于错误消息匹配

  protected static final Logger LOGGER =  // 日志记录器：用于记录测试执行过程中的调试信息
      LoggerFactory.getLogger(SqlValidatorTest.class);

  private static final String ERR_IN_VALUES_INCOMPATIBLE =
      "Values in expression list must have compatible types";

  private static final String ERR_IN_OPERANDS_INCOMPATIBLE =
      "Values passed to IN operator must have compatible types";

  private static final String ERR_AGG_IN_GROUP_BY =
      "Aggregate expression is illegal in GROUP BY clause";

  private static final String ERR_AGG_IN_ORDER_BY =
      "Aggregate expression is illegal in ORDER BY clause of non-aggregating SELECT";

  private static final String ERR_NESTED_AGG =
      "Aggregate expressions cannot be nested";

  private static final String EMP_RECORD_TYPE =  // EMP表记录类型：定义EMP表中所有列的类型信息
      "RecordType(INTEGER NOT NULL EMPNO,"
          + " VARCHAR(20) NOT NULL ENAME,"
          + " VARCHAR(10) NOT NULL JOB,"
          + " INTEGER MGR,"
          + " TIMESTAMP(0) NOT NULL HIREDATE,"
          + " INTEGER NOT NULL SAL,"
          + " INTEGER NOT NULL COMM,"
          + " INTEGER NOT NULL DEPTNO,"
          + " BOOLEAN NOT NULL SLACKER) NOT NULL";

  private static final String STR_AGG_REQUIRES_MONO = "Streaming aggregation "  // 错误消息常量：流式聚合需要在GROUP BY中至少有一个单调表达式
      + "requires at least one monotonic expression in GROUP BY clause";

  private static final String STR_ORDER_REQUIRES_MONO =
      "Streaming ORDER BY must start with monotonic expression";

  private static final String STR_SET_OP_INCONSISTENT =
      "Set operator cannot combine streaming and non-streaming inputs";

  private static final String ROW_RANGE_NOT_ALLOWED_WITH_RANK =
      "ROW/RANGE not allowed with RANK, DENSE_RANK, ROW_NUMBER, PERCENTILE_CONT/DISC or LAG/LEAD functions";

  private static final String RANK_REQUIRES_ORDER_BY = "RANK or DENSE_RANK "  // 错误消息常量：RANK或DENSE_RANK函数需要在窗口规范中包含ORDER BY子句
      + "functions require ORDER BY clause in window specification";

  private static String cannotConvertToStream(String name) {  // 辅助方法：生成表无法转换为流的错误消息
    return "Cannot convert table '" + name + "' to stream";
  }

  private static String cannotConvertToRelation(String table) {  // 辅助方法：生成流无法转换为表的错误消息
    return "Cannot convert stream '" + table + "' to relation";
  }

  private static String cannotStreamResultsForNonStreamingInputs(String inputs) {  // 辅助方法：生成无流输入时无法流式输出的错误消息
    return "Cannot stream results of a query with no streaming inputs: '"
        + inputs
        + "'. At least one input should be convertible to a stream";
  }

  static SqlOperatorTable operatorTableFor(SqlLibrary library) {
    return SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(
        SqlLibrary.STANDARD, library);
  }

  @Test void testMultipleSameAsPass() {  // 测试方法：testMultipleSameAsPass
    sql("select 1 as again,2 as \"again\", 3 as AGAiN from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testMultipleDifferentAs() {  // 测试方法：testMultipleDifferentAs
    sql("select 1 as c1,2 as c2 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6190">
   * Incorrect precision derivation for negative numeric types</a>. */
  @Test void testTypeOfDecimal() {  // 测试方法：testTypeOfDecimal
    sql("select DECIMAL '100.01' as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(5, 2) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select DECIMAL '-100.01' as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(5, 2) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select DECIMAL ' 100.01 ' as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(5, 2) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select DECIMAL ' -100.01 ' as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(5, 2) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testTypeOfAs() {  // 测试方法：testTypeOfAs
    sql("select 1 as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select 'hej' as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select x'deadbeef' as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BINARY(4) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(null as boolean) as c1 from (values (true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
  }

  @Test void testTypesLiterals() {  // 测试方法：testTypesLiterals
    expr("'abc'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("n'abc'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("_UTF16'abc'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("'ab '\n"  // 创建表达式测试：解析并验证SQL表达式
        + "' cd'")
        .columnType("CHAR(6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("'ab'\n"  // 创建表达式测试：解析并验证SQL表达式
        + "'cd'\n"
        + "'ef'\n"
        + "'gh'\n"
        + "'ij'\n"
        + "'kl'")
        .columnType("CHAR(12) NOT NULL");  // 类型验证：检查结果列的类型
    expr("n'ab '\n"  // 创建表达式测试：解析并验证SQL表达式
        + "' cd'")
        .columnType("CHAR(6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("_UTF16'ab '\n"  // 创建表达式测试：解析并验证SQL表达式
        + "' cd'")
        .columnType("CHAR(6) NOT NULL");  // 类型验证：检查结果列的类型

    expr("^x'abc'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Binary literal string must contain an even number of hexits");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("x'abcd'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BINARY(2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("x'abcd'\n"  // 创建表达式测试：解析并验证SQL表达式
        + "'ff001122aabb'")
        .columnType("BINARY(8) NOT NULL");  // 类型验证：检查结果列的类型
    expr("x'aaaa'\n"  // 创建表达式测试：解析并验证SQL表达式
        + "'bbbb'\n"
        + "'0000'\n"
        + "'1111'")
        .columnType("BINARY(8) NOT NULL");  // 类型验证：检查结果列的类型

    expr("1234567890")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("123456.7890")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("123456.7890e3")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("true")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("false")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("unknown")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("DECIMAL '123456.7890'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 4) NOT NULL");  // 类型验证：检查结果列的类型
  }

  /** Tests that date-time literals with invalid strings are considered invalid.
   * Originally the parser did that checking, but now the parser creates a
   * {@link org.apache.calcite.sql.SqlUnknownLiteral} and the checking is
   * deferred to the validator. */
  @Test void testLiteral() {  // 测试方法：testLiteral
    expr("^DATE '12/21/99'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Illegal DATE literal.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^TIME '1230:33'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Illegal TIME literal.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^TIME '12:00:00 PM'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Illegal TIME literal.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^TIMESTAMP '12-21-99, 12:30:00'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Illegal TIMESTAMP literal.*");  // 断言验证：期望SQL验证失败并检查错误消息

    expr("^TIMESTAMP WITH LOCAL TIME ZONE '12-21-99, 12:30:00'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Illegal TIMESTAMP WITH LOCAL TIME ZONE literal.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^TIMESTAMP WITH TIME ZONE '12-21-99, 12:30:00'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Illegal TIMESTAMP literal.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** PostgreSQL and Redshift allow TIMESTAMP literals that contain only a
   * date part. */
  @Test void testShortTimestampLiteral() {  // 测试方法：testShortTimestampLiteral
    sql("select timestamp '1969-07-20'")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // PostgreSQL allows the following. We should too.
    sql("select ^timestamp '1969-07-20 1:2'^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Illegal TIMESTAMP literal '1969-07-20 1:2': not in format "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'yyyy-MM-dd HH:mm:ss'"); // PostgreSQL gives 1969-07-20 01:02:00
    sql("select ^timestamp '1969-07-20:23:'^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Illegal TIMESTAMP literal '1969-07-20:23:': not in format "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'yyyy-MM-dd HH:mm:ss'"); // PostgreSQL gives 1969-07-20 23:00:00
  }

  @Test void testBooleans() {  // 测试方法：testBooleans
    sql("select TRUE OR unknowN from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select false AND unknown from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select not UNKNOWn from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select not true from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select not false from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testAndOrIllegalTypesFails() {  // 测试方法：testAndOrIllegalTypesFails
    // TODO need col+line number
    wholeExpr("'abc' AND FaLsE")
        .fails("(?s).*'<CHAR.3.> AND <BOOLEAN>'.*");  // 断言验证：期望SQL验证失败并检查错误消息

    wholeExpr("TRUE OR 1")
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息

    wholeExpr("unknown OR 1.0")
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息

    wholeExpr("true OR 1.0e4")
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息

    if (TODO) {
      wholeExpr("TRUE OR (TIME '12:00' AT LOCAL)")
          .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息
    }
  }

  @Test void testNotIllegalTypeFails() {  // 测试方法：testNotIllegalTypeFails
    sql("select ^NOT 3.141^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply 'NOT' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'NOT<DECIMAL.4, 3.>'.*");

    sql("select ^NOT 'abc'^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^NOT 1^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testIs() {  // 测试方法：testIs
    sql("select TRUE IS FALSE FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select false IS NULL FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select UNKNOWN IS NULL FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select FALSE IS UNKNOWN FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select TRUE IS NOT FALSE FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select TRUE IS NOT NULL FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select false IS NOT NULL FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select UNKNOWN IS NOT NULL FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select FALSE IS NOT UNKNOWN FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select 1 IS NULL FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2 IS NULL FROM (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    expr("^'abc' IS NOT UNKNOWN^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testIsFails() {  // 测试方法：testIsFails
    sql("select ^1 IS TRUE^ FROM (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*'<INTEGER> IS TRUE'.*");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^1.1 IS NOT FALSE^ FROM (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^1.1e1 IS NOT FALSE^ FROM (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply 'IS NOT FALSE' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<DOUBLE> IS NOT FALSE'.*");

    sql("select ^'abc' IS NOT TRUE^ FROM (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testScalars() {  // 测试方法：testScalars
    sql("select 1  + 1 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1  + 2.3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2+3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2+3.4 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select 1  - 1 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1  - 2.3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2-3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2-3.4 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select 1  * 2 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2* 3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1  * 2.3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2* 3.4 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select 1  / 2 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1  / 2.3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2/ 3 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1.2/3.4 from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testScalarsFails() {  // 测试方法：testScalarsFails
    sql("select ^1+TRUE^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply '\\+' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTEGER> \\+ <BOOLEAN>'\\. Supported form\\(s\\):.*");
  }

  @Test void testNumbers() {  // 测试方法：testNumbers
    sql("select 1+-2.*-3.e-1/-4>+5 AND true from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testPrefix() {  // 测试方法：testPrefix
    expr("+interval '1' second")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL SECOND NOT NULL");  // 类型验证：检查结果列的类型
    expr("-interval '1' month")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL MONTH NOT NULL");  // 类型验证：检查结果列的类型
    sql("SELECT ^-'abc'^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply '-' to arguments of type '-<CHAR.3.>'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT -'abc' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT ^+'abc'^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply '\\+' to arguments of type '\\+<CHAR.3.>'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT +'abc' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testNiladicForBigQuery() {  // 测试方法：testNiladicForBigQuery
    sql("select current_time, current_time(), current_date, "  // 创建SQL测试：解析并验证SQL语句
        + "current_date(), current_timestamp, current_timestamp()")
        .withConformance(SqlConformanceEnum.BIG_QUERY).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testEqualNotEqual() {  // 测试方法：testEqualNotEqual
    expr("''=''").ok();  // 断言验证：期望SQL验证通过
    expr("'abc'=n''").ok();  // 断言验证：期望SQL验证通过
    expr("''=_latin1''").ok();  // 断言验证：期望SQL验证通过
    expr("n''=''").ok();  // 断言验证：期望SQL验证通过
    expr("n'abc'=n''").ok();  // 断言验证：期望SQL验证通过
    expr("n''=_latin1''").ok();  // 断言验证：期望SQL验证通过
    expr("_latin1''=''").ok();  // 断言验证：期望SQL验证通过
    expr("_latin1''=n''").ok();  // 断言验证：期望SQL验证通过
    expr("_latin1''=_latin1''").ok();  // 断言验证：期望SQL验证通过

    expr("''<>''").ok();  // 断言验证：期望SQL验证通过
    expr("'abc'<>n''").ok();  // 断言验证：期望SQL验证通过
    expr("''<>_latin1''").ok();  // 断言验证：期望SQL验证通过
    expr("n''<>''").ok();  // 断言验证：期望SQL验证通过
    expr("n'abc'<>n''").ok();  // 断言验证：期望SQL验证通过
    expr("n''<>_latin1''").ok();  // 断言验证：期望SQL验证通过
    expr("_latin1''<>''").ok();  // 断言验证：期望SQL验证通过
    expr("_latin1'abc'<>n''").ok();  // 断言验证：期望SQL验证通过
    expr("_latin1''<>_latin1''").ok();  // 断言验证：期望SQL验证通过

    expr("true=false").ok();  // 断言验证：期望SQL验证通过
    expr("unknown<>true").ok();  // 断言验证：期望SQL验证通过

    expr("1=1").ok();  // 断言验证：期望SQL验证通过
    expr("1=.1").ok();  // 断言验证：期望SQL验证通过
    expr("1=1e-1").ok();  // 断言验证：期望SQL验证通过
    expr("0.1=1").ok();  // 断言验证：期望SQL验证通过
    expr("0.1=0.1").ok();  // 断言验证：期望SQL验证通过
    expr("0.1=1e1").ok();  // 断言验证：期望SQL验证通过
    expr("1.1e1=1").ok();  // 断言验证：期望SQL验证通过
    expr("1.1e1=1.1").ok();  // 断言验证：期望SQL验证通过
    expr("1.1e-1=1e1").ok();  // 断言验证：期望SQL验证通过

    expr("''<>''").ok();  // 断言验证：期望SQL验证通过
    expr("1<>1").ok();  // 断言验证：期望SQL验证通过
    expr("1<>.1").ok();  // 断言验证：期望SQL验证通过
    expr("1<>1e-1").ok();  // 断言验证：期望SQL验证通过
    expr("0.1<>1").ok();  // 断言验证：期望SQL验证通过
    expr("0.1<>0.1").ok();  // 断言验证：期望SQL验证通过
    expr("0.1<>1e1").ok();  // 断言验证：期望SQL验证通过
    expr("1.1e1<>1").ok();  // 断言验证：期望SQL验证通过
    expr("1.1e1<>1.1").ok();  // 断言验证：期望SQL验证通过
    expr("1.1e-1<>1e1").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testEqualNotEqualFails() {  // 测试方法：testEqualNotEqualFails
    // compare CHAR, INTEGER ok; implicitly convert CHAR
    expr("''<>1").ok();  // 断言验证：期望SQL验证通过
    expr("'1'>=1").ok();  // 断言验证：期望SQL验证通过
    // compare INTEGER, NCHAR ok
    expr("1<>n'abc'").ok();  // 断言验证：期望SQL验证通过
    // compare CHAR, DECIMAL ok
    expr("''=.1").ok();  // 断言验证：期望SQL验证通过
    expr("true<>1e-1").ok();  // 断言验证：期望SQL验证通过
    expr("^true<>1e-1^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply '<>' to arguments of type '<BOOLEAN> <> <DOUBLE>'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    // compare BOOLEAN, CHAR ok
    expr("false=''").ok();  // 断言验证：期望SQL验证通过
    expr("^x'a4'=0.01^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.1.> = <DECIMAL.3, 2.>'.*");
    expr("^x'a4'=1^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '=' to arguments of type '<BINARY.1.> = <INTEGER>'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^x'13'<>0.01^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '<>' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.1.> <> <DECIMAL.3, 2.>'.*");
    expr("^x'abcd'<>1^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '<>' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.2.> <> <INTEGER>'.*");
    // Test cases for [CALCITE-6736] Validator accepts comparisons between arrays, multisets, maps
    // without regard to element types
    expr("^array[x'a4'] = array[1]^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.1. ARRAY> = <INTEGER ARRAY>'.*");
    expr("^MAP[x'a4', 1] = MAP[1, 1]^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<.BINARY.1., INTEGER. MAP> = <.INTEGER, INTEGER. MAP>'.*");
    expr("^array[x'a4'] = 1^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '=' to arguments of type '<BINARY.1. ARRAY> = <INTEGER>'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^multiset[x'a4'] = multiset[1]^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply '=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.1. MULTISET> = <INTEGER MULTISET>'.*");
  }

  @Test void testBinaryString() {  // 测试方法：testBinaryString
    sql("select x'face'=X'' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select x'ff'=X'' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testBinaryStringFails() {  // 测试方法：testBinaryStringFails
    expr("select x'ffee'='abc' from (values(true))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    sql("select ^x'ffee'='abc'^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply '=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.2.> = <CHAR.3.>'.*");
    sql("select ^x'ff'=88^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply '=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.1.> = <INTEGER>'.*");
    sql("select ^x''<>1.1e-1^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply '<>' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.0.> <> <DOUBLE>'.*");
    sql("select ^x''<>1.1^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply '<>' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<BINARY.0.> <> <DECIMAL.2, 1.>'.*");
  }

  @Test void testStringLiteral() {  // 测试方法：testStringLiteral
    sql("select n''=_iso-8859-1'abc' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select N'f'<>'''' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testStringLiteralBroken() {  // 测试方法：testStringLiteralBroken
    sql("select 'foo'\n"  // 创建SQL测试：解析并验证SQL语句
        + "'bar' from (values(true))").ok();  // 断言验证：期望SQL验证通过
    sql("select 'foo'\r'bar' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 'foo'\n\r'bar' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 'foo'\r\n'bar' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 'foo'\n'bar' from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 'foo' /* comment */ ^'bar'^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("String literal continued on same line");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 'foo' -- comment\r from (values(true))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 'foo' ^'bar'^ from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .fails("String literal continued on same line");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testArithmeticOperators() {  // 测试方法：testArithmeticOperators
    expr("power(2,3)").ok();  // 断言验证：期望SQL验证通过
    expr("aBs(-2.3e-2)").ok();  // 断言验证：期望SQL验证通过
    expr("MOD(5             ,\t\f\r\n2)").ok();  // 断言验证：期望SQL验证通过
    expr("ln(5.43  )").ok();  // 断言验证：期望SQL验证通过
    expr("log10(- -.2  )").ok();  // 断言验证：期望SQL验证通过

    expr("mod(5.1, 3)").ok();  // 断言验证：期望SQL验证通过
    expr("mod(2,5.1)").ok();  // 断言验证：期望SQL验证通过
    expr("5.1 % 3")  // 创建表达式测试：解析并验证SQL表达式
        .withConformance(SqlConformanceEnum.LENIENT)
        .columnType("DECIMAL(2, 1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("2 % 5.1")  // 创建表达式测试：解析并验证SQL表达式
        .withConformance(SqlConformanceEnum.LENIENT)
        .columnType("DECIMAL(2, 1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("exp(3.67)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testArithmeticOperatorsFails() {  // 测试方法：testArithmeticOperatorsFails
    expr("^power(2,'abc')^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'POWER' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'POWER.<INTEGER>, <CHAR.3.>.*");
    expr("power(2,'abc')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("^power(true,1)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply 'POWER' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'POWER.<BOOLEAN>, <INTEGER>.*");
    expr("^mod(x'1100',1)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply 'MOD' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'MOD.<BINARY.2.>, <INTEGER>.*");
    expr("^mod(1, x'1100')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply 'MOD' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'MOD.<INTEGER>, <BINARY.2.>.*");
    expr("^abs(x'')^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'ABS' to arguments of type 'ABS.<BINARY.0.>.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^ln(x'face12')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply 'LN' to arguments of type 'LN.<BINARY.3.>.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^log10(x'fa')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply 'LOG10' to arguments of type 'LOG10.<BINARY.1.>.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^exp('abc')^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'EXP' to arguments of type 'EXP.<CHAR.3.>.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("exp('abc')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testCaseExpression() {  // 测试方法：testCaseExpression
    expr("case 1 when 1 then 'one' end").ok();  // 断言验证：期望SQL验证通过
    expr("case 1 when 1 then 'one' else null end").ok();  // 断言验证：期望SQL验证通过
    expr("case 1 when 1 then 'one' else 'more' end").ok();  // 断言验证：期望SQL验证通过
    expr("case 1 when 1 then 'one' when 2 then null else 'more' end").ok();  // 断言验证：期望SQL验证通过
    expr("case when TRUE then 'true' else 'false' end").ok();  // 断言验证：期望SQL验证通过
    sql("values case when TRUE then 'true' else 'false' end").ok();  // 创建SQL测试：解析并验证SQL语句
    expr("CASE 1 WHEN 1 THEN cast(null as integer) WHEN 2 THEN null END").ok();  // 断言验证：期望SQL验证通过
    expr("CASE 1 WHEN 1 THEN cast(null as integer) WHEN 2 THEN cast(null as "  // 创建表达式测试：解析并验证SQL表达式
        + "integer) END").ok();  // 断言验证：期望SQL验证通过
    expr("CASE 1 WHEN 1 THEN null WHEN 2 THEN cast(null as integer) END").ok();  // 断言验证：期望SQL验证通过
    expr("CASE 1 WHEN 1 THEN cast(null as integer) WHEN 2 THEN cast(cast(null"  // 创建表达式测试：解析并验证SQL表达式
        + " as tinyint) as integer) END").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testCaseExpressionTypes() {  // 测试方法：testCaseExpressionTypes
    expr("case 1 when 1 then 'one' else 'not one' end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(7) NOT NULL");  // 类型验证：检查结果列的类型
    expr("case when 2<1 then 'impossible' end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(10)");  // 类型验证：检查结果列的类型
    expr("case 'one' when 'two' then 2.00 when 'one' then 1.3 else 3.2 end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(3, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("case 'one' when 'two' then 2 when 'one' then 1.00 else 3 end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(12, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("case 1 when 1 then 'one' when 2 then null else 'more' end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(4)");  // 类型验证：检查结果列的类型
    expr("case when TRUE then 'true' else 'false' end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(5) NOT NULL");  // 类型验证：检查结果列的类型
    expr("CASE 1 WHEN 1 THEN cast(null as integer) END")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("CASE 1\n"  // 创建表达式测试：解析并验证SQL表达式
        + "WHEN 1 THEN NULL\n"
        + "WHEN 2 THEN cast(cast(null as tinyint) as integer) END")
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("CASE 1\n"  // 创建表达式测试：解析并验证SQL表达式
        + "WHEN 1 THEN cast(null as integer)\n"
        + "WHEN 2 THEN cast(null as integer) END")
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("CASE 1\n"  // 创建表达式测试：解析并验证SQL表达式
        + "WHEN 1 THEN cast(null as integer)\n"
        + "WHEN 2 THEN cast(cast(null as tinyint) as integer)\n"
        + "END")
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("CASE 1\n"  // 创建表达式测试：解析并验证SQL表达式
        + "WHEN 1 THEN INTERVAL '12 3:4:5.6' DAY TO SECOND(6)\n"
        + "WHEN 2 THEN INTERVAL '12 3:4:5.6' DAY TO SECOND(9)\n"
        + "END")
        .columnType("INTERVAL DAY TO SECOND(9)");  // 类型验证：检查结果列的类型

    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "CASE WHEN job is not null THEN mgr\n"
        + "ELSE 5 end as mgr\n"
        + "from EMP")
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
  }

  @Test void testCaseExpressionFails() {  // 测试方法：testCaseExpressionFails
    // varchar not comparable with bit string
    expr("case 'string' when x'01' then 'zero one' else 'something' end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(9) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("case 'string' when x'01' then 'zero one' else 'something' end")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply '=' to arguments of type '<CHAR.6.> = <BINARY.1.>'.*");  // 断言验证：期望SQL验证失败并检查错误消息

    // all thens and else return null
    wholeExpr("case 1 when 1 then null else null end")
        .withTypeCoercion(false)
        .fails("(?s).*ELSE clause or at least one THEN clause must be non-NULL.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("case 1 when 1 then null else null end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("NULL");  // 类型验证：检查结果列的类型

    // all thens and else return null
    wholeExpr("case 1 when 1 then null end")
        .withTypeCoercion(false)
        .fails("(?s).*ELSE clause or at least one THEN clause must be non-NULL.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("case 1 when 1 then null end")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("NULL");  // 类型验证：检查结果列的类型

    wholeExpr("case when true and true then 1 "
        + "when false then 2 "
        + "when false then true " + "else "
        + "case when true then 3 end end")
        .fails("Illegal mixing of types in CASE or COALESCE statement");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testNullIf() {  // 测试方法：testNullIf
    expr("nullif(1,2)").ok();  // 断言验证：期望SQL验证通过
    expr("nullif(1,2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("nullif('a','b')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(1)");  // 类型验证：检查结果列的类型
    expr("nullif(345.21, 2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(5, 2)");  // 类型验证：检查结果列的类型
    expr("nullif(345.21, 2e0)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(5, 2)");  // 类型验证：检查结果列的类型
    expr("nullif(DATE '2020-01-01', '2020-01-01')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DATE");  // 类型验证：检查结果列的类型
    expr("nullif('2020-01-01', DATE '2020-01-01')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(10)");  // 类型验证：检查结果列的类型
    expr("nullif(DATE '2020-01-01', '2020-01-01')")  // 创建表达式测试：解析并验证SQL表达式
        .withValidatorConfig(c -> c.withCallRewrite(false))
        .columnType("DATE");  // 类型验证：检查结果列的类型
    expr("nullif('2020-01-01', DATE '2020-01-01')")  // 创建表达式测试：解析并验证SQL表达式
        .withValidatorConfig(c -> c.withCallRewrite(false))
        .columnType("CHAR(10)");  // 类型验证：检查结果列的类型
    wholeExpr("nullif(1,2,3)")
        .fails("Invalid number of arguments to function 'NULLIF'. Was "  // 断言验证：期望SQL验证失败并检查错误消息
            + "expecting 2 arguments");
  }

  @Test void testCoalesce() {  // 测试方法：testCoalesce
    expr("coalesce('a','b')").ok();  // 断言验证：期望SQL验证通过
    expr("coalesce('a','b','c')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("COALESCE(DATE '2020-01-01', '2020-01-02')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("COALESCE(DATE '2020-01-01', '2020-01-02')")  // 创建表达式测试：解析并验证SQL表达式
        .withValidatorConfig(c -> c.withCallRewrite(false))
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    sql("select COALESCE(mgr, 12) as m from EMP")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testCoalesceFails() {  // 测试方法：testCoalesceFails
    wholeExpr("coalesce('a',1)")
        .withTypeCoercion(false)
        .fails("Illegal mixing of types in CASE or COALESCE statement");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("coalesce('a',1)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("coalesce('a','b',1)")
        .withTypeCoercion(false)
        .fails("Illegal mixing of types in CASE or COALESCE statement");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("coalesce('a','b',1)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testStringCompare() {  // 测试方法：testStringCompare
    expr("'a' = 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' <> 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' > 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' < 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' >= 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' <= 'b'").ok();  // 断言验证：期望SQL验证通过

    expr("cast('' as varchar(1))>cast('' as char(1))").ok();  // 断言验证：期望SQL验证通过
    expr("cast('' as varchar(1))<cast('' as char(1))").ok();  // 断言验证：期望SQL验证通过
    expr("cast('' as varchar(1))>=cast('' as char(1))").ok();  // 断言验证：期望SQL验证通过
    expr("cast('' as varchar(1))<=cast('' as char(1))").ok();  // 断言验证：期望SQL验证通过
    expr("cast('' as varchar(1))=cast('' as char(1))").ok();  // 断言验证：期望SQL验证通过
    expr("cast('' as varchar(1))<>cast('' as char(1))").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testStringCompareType() {  // 测试方法：testStringCompareType
    expr("'a' = 'b'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'a' <> 'b'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'a' > 'b'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'a' < 'b'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'a' >= 'b'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'a' <= 'b'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("CAST(NULL AS VARCHAR(33)) > 'foo'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
  }

  @Test void testConcat() {  // 测试方法：testConcat
    expr("'a'||'b'").ok();  // 断言验证：期望SQL验证通过
    expr("x'12'||x'34'").ok();  // 断言验证：期望SQL验证通过
    expr("'a'||'b'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('a' as char(1))||cast('b' as char(2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as char(1))||cast('b' as char(2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3)");  // 类型验证：检查结果列的类型
    expr("'a'||'b'||'c'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("'a'||'b'||'cde'||'f'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("'a'||'b'||cast('cde' as VARCHAR(3))|| 'f'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("_UTF16'a'||_UTF16'b'||_UTF16'c'").ok();  // 断言验证：期望SQL验证通过
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6779">[CALCITE-6779]
   * Casts from UUID to DATE should be invalid</a>. */
  @Test void testUuidCasts() {  // 测试方法：testUuidCasts
    final String error = "Cast function cannot convert value of type UUID to type.*";
    expr("^CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS TIME)^").fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS DATE)^").fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS TIMESTAMP)^").fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS INT)^").fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS DOUBLE)^").fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String error2 = "Cast function cannot convert value of type.* to type UUID.*";
    expr("^CAST(TIME '10:00:00' AS UUID)^").fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(DATE '2024-01-01' AS UUID)^").fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(TIMESTAMP '2024-01-01 00:00:00' AS UUID)^").fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(2 AS UUID)^").fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CAST(2.0e0 AS UUID)^").fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息

    expr("CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS UUID)").ok();  // 断言验证：期望SQL验证通过
    expr("CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS VARCHAR)").ok();  // 断言验证：期望SQL验证通过
    expr("CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS CHAR(2))").ok();  // 断言验证：期望SQL验证通过
    expr("CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS BINARY(2))").ok();  // 断言验证：期望SQL验证通过
    expr("CAST(UUID '123e4567-e89b-12d3-a456-426655440000' AS VARBINARY)").ok();  // 断言验证：期望SQL验证通过

    expr("CAST('123e4567-e89b-12d3-a456-426655440000' AS UUID)").ok();  // 断言验证：期望SQL验证通过
    expr("CAST(CAST('123e4567-e89b-12d3-a456-426655440000' AS VARCHAR) AS UUID)").ok();  // 断言验证：期望SQL验证通过
    expr("CAST(x'123e4567e89b12d3a456426655440000' AS UUID)").ok();  // 断言验证：期望SQL验证通过
    expr("CAST(CAST(x'123e4567e89b12d3a456426655440000' AS VARBINARY) AS UUID)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testConcatWithCharset() {  // 测试方法：testConcatWithCharset
    sql("_UTF16'a'||_UTF16'b'||_UTF16'c'")  // 创建SQL测试：解析并验证SQL语句
        .assertCharset(isCharset("UTF-16LE"));
  }

  @Test void testConcatFails() {  // 测试方法：testConcatFails
    wholeExpr("'a'||x'ff'")
        .fails("(?s).*Cannot apply '\\|\\|' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<CHAR.1.> \\|\\| <BINARY.1.>'.*Supported form.s.: "
            + "'<STRING> \\|\\| <STRING>.*'");
  }

  /** Tests the CONCAT function, which unlike the concat operator ('||') is not
   * standard but enabled in the ORACLE, MySQL, BigQuery, POSTGRESQL and MSSQL libraries. */
  @Test void testConcatFunction() {  // 测试方法：testConcatFunction
    // CONCAT is not in the library operator table
    final SqlValidatorFixture s = fixture()
        .withOperatorTable(operatorTableFor(SqlLibrary.POSTGRESQL));
    s.withExpr("concat('a', 'b')").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("concat(x'12', x'34')").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("concat(_UTF16'a', _UTF16'b', _UTF16'c')").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("concat('aabbcc', 'ab', '+-')")
        .columnType("VARCHAR(10) NOT NULL");  // 类型验证：检查结果列的类型
    s.withExpr("concat('aabbcc', CAST(NULL AS VARCHAR(20)), '+-')")
        .columnType("VARCHAR(28) NOT NULL");  // 类型验证：检查结果列的类型
    s.withExpr("concat('aabbcc', 2)")
        .withWhole(true)
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply 'CONCAT' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'CONCAT\\(<CHAR\\(6\\)>, <INTEGER>\\)'\\. .*");
    s.withExpr("concat('aabbcc', 2)").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("concat('abc', 'ab', 123)")
        .withWhole(true)
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply 'CONCAT' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'CONCAT\\(<CHAR\\(3\\)>, <CHAR\\(2\\)>, <INTEGER>\\)'\\. .*");
    s.withExpr("concat('abc', 'ab', 123)").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("concat(true, false)")
        .withWhole(true)
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply 'CONCAT' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'CONCAT\\(<BOOLEAN>, <BOOLEAN>\\)'\\. .*");
    s.withExpr("concat(true, false)").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("concat(DATE '2020-04-17', TIMESTAMP '2020-04-17 14:17:51')")
        .withWhole(true)
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply 'CONCAT' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'CONCAT\\(<DATE>, <TIMESTAMP\\(0\\)>\\)'\\. .*");
    s.withExpr("concat(DATE '2020-04-17', TIMESTAMP '2020-04-17 14:17:51')").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testBetween() {  // 测试方法：testBetween
    expr("1 between 2 and 3").ok();  // 断言验证：期望SQL验证通过
    expr("'a' between 'b' and 'c'").ok();  // 断言验证：期望SQL验证通过
    // can implicitly convert CHAR to INTEGER
    expr("'' between 2 and 3").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("date '2012-02-03' between 2 and 3")
        .fails("(?s).*Cannot apply 'BETWEEN ASYMMETRIC' to arguments of type.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testCharsetMismatch() {  // 测试方法：testCharsetMismatch
    wholeExpr("''=_UTF16''")
        .fails("Cannot apply .* to strings with different charsets 'ISO-8859-1' and "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'UTF-16LE'");
    wholeExpr("''<>_UTF16''")
        .fails("(?s).*Cannot apply .* to strings with different charsets.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("''>_UTF16''")
        .fails("(?s).*Cannot apply .* to strings with different charsets.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("''<_UTF16''")
        .fails("(?s).*Cannot apply .* to strings with different charsets.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("''<=_UTF16''")
        .fails("(?s).*Cannot apply .* to strings with different charsets.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("''>=_UTF16''")
        .fails("(?s).*Cannot apply .* to strings with different charsets.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("''||_UTF16''")
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("'a'||'b'||_UTF16'c'")
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  // FIXME jvs 2-Feb-2005:

  @Disabled("all collation-related tests are disabled due to dtbug 280")
  void testSimpleCollate() {
    expr("'s' collate latin1$en$1").ok();  // 断言验证：期望SQL验证通过
    expr("'s' collate latin1$en$1")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(1)");  // 类型验证：检查结果列的类型
    sql("'s'")  // 创建SQL测试：解析并验证SQL语句
        .assertCollation(is("ISO-8859-1$en_US$primary"),
            is(SqlCollation.Coercibility.COERCIBLE));
    sql("'s' collate latin1$sv$3")  // 创建SQL测试：解析并验证SQL语句
        .assertCollation(is("ISO-8859-1$sv$3"),
            is(SqlCollation.Coercibility.EXPLICIT));
  }

  @Disabled("all collation-related tests are disabled due to dtbug 280")
  void testCharsetAndCollateMismatch() {
    // todo
    expr("_UTF16's' collate latin1$en$1")  // 创建表达式测试：解析并验证SQL表达式
        .fails("?");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Disabled("all collation-related tests are disabled due to dtbug 280")
  void testDyadicCollateCompare() {
    expr("'s' collate latin1$en$1 < 't'").ok();  // 断言验证：期望SQL验证通过
    expr("'t' > 's' collate latin1$en$1").ok();  // 断言验证：期望SQL验证通过
    expr("'s' collate latin1$en$1 <> 't' collate latin1$en$1").ok();  // 断言验证：期望SQL验证通过
  }

  @Disabled("all collation-related tests are disabled due to dtbug 280")
  void testDyadicCompareCollateFails() {
    // two different explicit collations. difference in strength
    expr("'s' collate latin1$en$1 <= 't' collate latin1$en$2")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Two explicit different collations.*are illegal.*");  // 断言验证：期望SQL验证失败并检查错误消息

    // two different explicit collations. difference in language
    expr("'s' collate latin1$sv$1 >= 't' collate latin1$en$1")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Two explicit different collations.*are illegal.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Disabled("all collation-related tests are disabled due to dtbug 280")
  void testDyadicCollateOperator() {
    sql("'a' || 'b'")  // 创建SQL测试：解析并验证SQL语句
        .assertCollation(is("ISO-8859-1$en_US$primary"),
            is(SqlCollation.Coercibility.COERCIBLE));
    sql("'a' collate latin1$sv$3 || 'b'")  // 创建SQL测试：解析并验证SQL语句
        .assertCollation(is("ISO-8859-1$sv$3"),
            is(SqlCollation.Coercibility.EXPLICIT));
    sql("'a' collate latin1$sv$3 || 'b' collate latin1$sv$3")  // 创建SQL测试：解析并验证SQL语句
        .assertCollation(is("ISO-8859-1$sv$3"),
            is(SqlCollation.Coercibility.EXPLICIT));
  }

  @Test void testCharLength() {  // 测试方法：testCharLength
    expr("char_length('string')").ok();  // 断言验证：期望SQL验证通过
    expr("char_length(_UTF16'string')").ok();  // 断言验证：期望SQL验证通过
    expr("character_length('string')").ok();  // 断言验证：期望SQL验证通过
    expr("char_length('string')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("character_length('string')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testUpperLower() {  // 测试方法：testUpperLower
    expr("upper(_UTF16'sadf')").ok();  // 断言验证：期望SQL验证通过
    expr("lower(n'sadf')").ok();  // 断言验证：期望SQL验证通过
    expr("lower('sadf')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(4) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("upper(123)")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'UPPER' to arguments of type 'UPPER.<INTEGER>.'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("upper(123)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testPosition() {  // 测试方法：testPosition
    expr("position('mouse' in 'house')").ok();  // 断言验证：期望SQL验证通过
    expr("position(x'11' in x'100110')").ok();  // 断言验证：期望SQL验证通过
    expr("position(x'11' in x'100110' FROM 10)").ok();  // 断言验证：期望SQL验证通过
    expr("position(x'abcd' in x'')").ok();  // 断言验证：期望SQL验证通过
    expr("position('mouse' in 'house')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("position(x'1234' in '110')")
        .fails("Parameters must be of the same type");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("position(x'1234' in '110' from 3)")
        .fails("Parameters must be of the same type");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testTrim() {  // 测试方法：testTrim
    expr("trim('mustache' FROM 'beard')").ok();  // 断言验证：期望SQL验证通过
    expr("trim(both 'mustache' FROM 'beard')").ok();  // 断言验证：期望SQL验证通过
    expr("trim(leading 'mustache' FROM 'beard')").ok();  // 断言验证：期望SQL验证通过
    expr("trim(trailing 'mustache' FROM 'beard')").ok();  // 断言验证：期望SQL验证通过
    expr("trim('mustache' FROM 'beard')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(5) NOT NULL");  // 类型验证：检查结果列的类型
    expr("trim('beard  ')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(7) NOT NULL");  // 类型验证：检查结果列的类型
    expr("trim('mustache' FROM cast(null as varchar(4)))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(4)");  // 类型验证：检查结果列的类型

    if (TODO) {
      final SqlCollation.Coercibility expectedCoercibility = null;
      sql("trim('mustache' FROM 'beard')")  // 创建SQL测试：解析并验证SQL语句
          .assertCollation(is("CHAR(5)"), is(expectedCoercibility));
    }
  }

  @Test void testTrimFails() {  // 测试方法：testTrimFails
    wholeExpr("trim(123 FROM 'beard')")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'TRIM' to arguments of type.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("trim(123 FROM 'beard')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(5) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("trim('a' FROM 123)")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'TRIM' to arguments of type.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("trim('a' FROM 123)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("trim('a' FROM _UTF16'b')")
        .fails("(?s).*not comparable to each other.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testConvertAndTranslate() {  // 测试方法：testConvertAndTranslate
    sql("select convert('abc' using utf16) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select convert(cast(deptno as varchar) using utf8) from emp");  // 创建SQL测试：解析并验证SQL语句
    sql("select convert(null using utf16) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select ^convert(deptno using latin1)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Invalid type 'INTEGER NOT NULL' in 'TRANSLATE' function\\. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Only 'CHARACTER' type is supported");
    sql("select convert(ename using utf9) from emp").fails("UTF9");  // 创建SQL测试：解析并验证SQL语句

    sql("select translate('abc' using utf8) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select translate(cast(deptno as varchar) using utf8) from emp");  // 创建SQL测试：解析并验证SQL语句
    sql("select translate(null using utf16) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select ^translate(deptno using latin1)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Invalid type 'INTEGER NOT NULL' in 'TRANSLATE' function\\. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Only 'CHARACTER' type is supported");
    sql("select translate(ename using utf9) from emp").fails("UTF9");  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testTranslate3() {  // 测试方法：testTranslate3
    // TRANSLATE3 is not in the standard operator table
    wholeExpr("translate('aabbcc', 'ab', '+-')")
        .fails("No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "TRANSLATE3\\(<CHARACTER>, <CHARACTER>, <CHARACTER>\\)");

    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.ORACLE);

    expr("translate('aabbcc', 'ab', '+-')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR(6) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("translate('abc', 'ab')")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TRANSLATE3'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 3 arguments");
    wholeExpr("translate('abc', 'ab', 123)")
        .withOperatorTable(opTable)
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply 'TRANSLATE3' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'TRANSLATE3\\(<CHAR\\(3\\)>, <CHAR\\(2\\)>, <INTEGER>\\)'\\. .*");
    expr("translate('abc', 'ab', 123)")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("translate('abc', 'ab', '+-', 'four')")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TRANSLATE3'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 3 arguments");
  }

  /** Test case for <a href="https://issues.apache.org/jira/projects/CALCITE/issues/CALCITE-6813">
   * [CALCITE-6813] UNNEST infers incorrect nullability for the result when applied to
   * an array that contains nullable ROW values</a>. */
  @Test void testUnnestRow() {  // 测试方法：testUnnestRow
    sql("with orders(data) as\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (values (ARRAY[ROW(1, 'Alice'), ROW(2, NULL), ROW(NULL, 'Bob'), NULL]))\n"
        + "select e.EXPR$0\n"
        + "from orders, UNNEST(orders.data) as e")
        .type(actualType -> {
          // Unfortunately the string representation does not contain nullability information
          assertThat(actualType, hasToString("RecordType(INTEGER EXPR$0)"));
          assertTrue(actualType.isStruct());
          assertThat(actualType.getFieldCount(), is(1));
          // The field type should be nullable
          assertTrue(actualType.getFieldList().get(0).getType().isNullable());
        });
    sql("with orders(data) as\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (values (ARRAY[ROW(1, 'Alice'), ROW(2, NULL), ROW(NULL, 'Bob'), NULL]))\n"
        + "select e.*\n"
        + "from orders, UNNEST(orders.data) as e")
        .type(actualType -> {
          assertThat(actualType, hasToString("RecordType(INTEGER EXPR$0, CHAR(5) EXPR$1)"));
          assertTrue(actualType.isStruct());
          assertTrue(actualType.getFieldList().get(0).getType().isNullable());
          assertTrue(actualType.getFieldList().get(1).getType().isNullable());
        });
    sql("with orders(data) as\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (values (ARRAY[ROW(1, 'Alice'), ROW(2, NULL)]))\n"
        + "select e.*\n"
        + "from orders, UNNEST(orders.data) as e")
        .type(actualType -> {
          // The array unnested is not nullable
          assertThat(actualType, hasToString("RecordType(INTEGER EXPR$0, CHAR(5) EXPR$1)"));
          assertTrue(actualType.isStruct());
          assertThat(actualType.getFieldList().get(0).getType().isNullable(), is(false));
          assertTrue(actualType.getFieldList().get(1).getType().isNullable());
        });
    sql("with orders(data) as\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (values (ARRAY[ARRAY[ROW(1, 'Alice'), ROW(2, NULL)], NULL]))\n"
        + "select e.*\n"
        + "from orders, UNNEST(orders.data[1]) as e")
        .type(actualType -> {
          // The inner array that is unnested is nullable in this example
          assertThat(actualType, hasToString("RecordType(INTEGER EXPR$0, CHAR(5) EXPR$1)"));
          assertTrue(actualType.isStruct());
          assertThat(actualType.getFieldList().get(0).getType().isNullable(), is(false));
          assertTrue(actualType.getFieldList().get(1).getType().isNullable());
        });
  }

  @Test void testOverlay() {  // 测试方法：testOverlay
    expr("overlay('ABCdef' placing 'abc' from 1)").ok();  // 断言验证：期望SQL验证通过
    expr("overlay('ABCdef' placing 'abc' from 1 for 3)").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("overlay('ABCdef' placing 'abc' from '1' for 3)")
        .withTypeCoercion(false)
        .fails("(?s).*OVERLAY\\(<STRING> PLACING <STRING> FROM <INTEGER>\\).*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("overlay('ABCdef' placing 'abc' from '1' for 3)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(9) NOT NULL");  // 类型验证：检查结果列的类型
    expr("overlay('ABCdef' placing 'abc' from 1 for 3)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(9) NOT NULL");  // 类型验证：检查结果列的类型
    expr("overlay('ABCdef' placing 'abc' from 6 for 3)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(9) NOT NULL");  // 类型验证：检查结果列的类型
    expr("overlay('ABCdef' placing cast(null as char(5)) from 1)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(11)");  // 类型验证：检查结果列的类型

    if (TODO) {
      sql("overlay('ABCdef' placing 'abc' collate latin1$sv from 1 for 3)")  // 创建SQL测试：解析并验证SQL语句
          .assertCollation(is("ISO-8859-1$sv"),
              is(SqlCollation.Coercibility.EXPLICIT));
    }
  }

  @Test void testSubstring() {  // 测试方法：testSubstring
    expr("substring('a' FROM 1)").ok();  // 断言验证：期望SQL验证通过
    expr("substring('a' FROM 1 FOR 3)").ok();  // 断言验证：期望SQL验证通过
    expr("substring('a' FROM 'reg' FOR '\\')").ok();  // 断言验证：期望SQL验证通过
    // binary string
    expr("substring(x'ff' FROM 1  FOR 2)").ok();  // 断言验证：期望SQL验证通过

    expr("substring('10' FROM 1  FOR 2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("substring('1000' FROM 2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("substring('1000' FROM '1'  FOR 'w')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("substring(cast(' 100 ' as CHAR(99)) FROM '1'  FOR 'w')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(99) NOT NULL");  // 类型验证：检查结果列的类型
    expr("substring(x'10456b' FROM 1  FOR 2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARBINARY(3) NOT NULL");  // 类型验证：检查结果列的类型

    sql("substring('10' FROM 1  FOR 2)")  // 创建SQL测试：解析并验证SQL语句
        .assertCharset(isCharset("ISO-8859-1")); // aka "latin1"
    sql("substring(_UTF16'10' FROM 1  FOR 2)")  // 创建SQL测试：解析并验证SQL语句
        .assertCharset(isCharset("UTF-16LE"));
    expr("substring('a', 1)").ok();  // 断言验证：期望SQL验证通过
    expr("substring('a', 1, 3)").ok();  // 断言验证：期望SQL验证通过
    // Implicit type coercion.
    expr("substring(12345, '1')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("substring('a', '1')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("substring('a', 1, '3')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(1) NOT NULL");  // 类型验证：检查结果列的类型

    // Correctly processed null string and params.
    expr("SUBSTRING(NULL FROM 1 FOR 2)").ok();  // 断言验证：期望SQL验证通过
    expr("SUBSTRING('text' FROM 1 FOR NULL)").ok();  // 断言验证：期望SQL验证通过
    expr("SUBSTRING('text' FROM NULL FOR 2)").ok();  // 断言验证：期望SQL验证通过
    expr("SUBSTRING('text' FROM NULL)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testSubstringFails() {  // 测试方法：testSubstringFails
    String error = "(?s).*Cannot apply 'SUBSTRING' to arguments of type.*";
    wholeExpr("substring('a' from 1 for 'b')")
        .withTypeCoercion(false)
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("substring(_UTF16'10' FROM '0' FOR '\\')")
        .withTypeCoercion(false)
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("substring('10' FROM _UTF16'0' FOR '\\')")
        .withTypeCoercion(false)
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("substring('10' FROM '0' FOR _UTF16'\\')")
        .withTypeCoercion(false)
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testLikeAndSimilar() {  // 测试方法：testLikeAndSimilar
    expr("'a' like 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' like 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' similar to 'b'").ok();  // 断言验证：期望SQL验证通过
    expr("'a' similar to 'b' escape 'c'").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testIlike() {  // 测试方法：testIlike
    final SqlValidatorFixture s = fixture()
        .withOperatorTable(operatorTableFor(SqlLibrary.POSTGRESQL));
    s.withExpr("'a' ilike 'b'").columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    s.withExpr("'a' ilike cast(null as varchar(99))").columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    s.withExpr("cast(null as varchar(99)) not ilike 'b'").columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    s.withExpr("'a' not ilike 'b' || 'c'").columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型

    // ILIKE is only available in the PostgreSQL function library
    expr("^'a' ilike 'b'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("No match found for function signature ILIKE");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRlike() {  // 测试方法：testRlike
    // RLIKE is supported for SPARK
    final SqlValidatorFixture s = fixture()
        .withOperatorTable(operatorTableFor(SqlLibrary.SPARK));
    s.withExpr("'first_name' rlike '%Ted%'").columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    s.withExpr("'first_name' rlike '^M+'").columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型

    // RLIKE is only supported for Spark and Hive
    String noMatch = "(?s).*No match found for function signature RLIKE";
    expr("^'b' rlike '.+@.+\\\\..+'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails(noMatch)  // 断言验证：期望SQL验证失败并检查错误消息
        .withOperatorTable(operatorTableFor(SqlLibrary.POSTGRESQL))
        .fails(noMatch)  // 断言验证：期望SQL验证失败并检查错误消息
        .withOperatorTable(operatorTableFor(SqlLibrary.SPARK))
        .columnType("BOOLEAN NOT NULL")  // 类型验证：检查结果列的类型
        .withOperatorTable(operatorTableFor(SqlLibrary.HIVE))
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Disabled
  void testLikeAndSimilarFails() {
    expr("'a' like _UTF16'b'  escape 'c'")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Operands _ISO-8859-1.a. COLLATE ISO-8859-1.en_US.primary,"  // 断言验证：期望SQL验证失败并检查错误消息
            + " _SHIFT_JIS.b..*");
    expr("'a' similar to _UTF16'b'  escape 'c'")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Operands _ISO-8859-1.a. COLLATE ISO-8859-1.en_US.primary,"  // 断言验证：期望SQL验证失败并检查错误消息
            + " _SHIFT_JIS.b..*");

    expr("'a' similar to 'b' collate UTF16$jp  escape 'c'")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Operands _ISO-8859-1.a. COLLATE ISO-8859-1.en_US.primary,"  // 断言验证：期望SQL验证失败并检查错误消息
            + " _ISO-8859-1.b. COLLATE SHIFT_JIS.jp.primary.*");
  }

  @Test void testNull() {  // 测试方法：testNull
    expr("nullif(null, 1)").ok();  // 断言验证：期望SQL验证通过
    expr("values 1.0 + ^NULL^").ok();  // 断言验证：期望SQL验证通过
    expr("1.0 + ^NULL^").ok();  // 断言验证：期望SQL验证通过
    expr("case when 1 > 0 then null else 0 end").ok();  // 断言验证：期望SQL验证通过
    expr("1 > 0 and null").ok();  // 断言验证：期望SQL验证通过
    expr("position(null in 'abc' from 1)").ok();  // 断言验证：期望SQL验证通过
    expr("substring(null from 1)").ok();  // 断言验证：期望SQL验证通过
    expr("trim(null from 'ab')").ok();  // 断言验证：期望SQL验证通过
    expr("trim(null from null)").ok();  // 断言验证：期望SQL验证通过
    expr("null || 'a'").ok();  // 断言验证：期望SQL验证通过
    expr("not(null)").ok();  // 断言验证：期望SQL验证通过
    expr("+null").ok();  // 断言验证：期望SQL验证通过
    expr("-null").ok();  // 断言验证：期望SQL验证通过
    expr("upper(null)").ok();  // 断言验证：期望SQL验证通过
    expr("lower(null)").ok();  // 断言验证：期望SQL验证通过
    expr("initcap(null)").ok();  // 断言验证：期望SQL验证通过
    expr("mod(null, 2) + 1").ok();  // 断言验证：期望SQL验证通过
    expr("abs(null)").ok();  // 断言验证：期望SQL验证通过
    expr("round(null,1)").ok();  // 断言验证：期望SQL验证通过
    expr("sign(null) + 1").ok();  // 断言验证：期望SQL验证通过
    expr("truncate(null,1) + 1").ok();  // 断言验证：期望SQL验证通过

    sql("select null as a from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select avg(null) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select bit_and(null) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select bit_or(null) from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    expr("substring(null from 1) + 1").ok();  // 断言验证：期望SQL验证通过
    expr("substring(^NULL^ from 1)")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Illegal use of .NULL.*");  // 断言验证：期望SQL验证失败并检查错误消息

    expr("values 1.0 + ^NULL^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Illegal use of .NULL.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("values 1.0 + NULL")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(2, 1)");  // 类型验证：检查结果列的类型
    expr("1.0 + ^NULL^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Illegal use of .NULL.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("1.0 + NULL")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(2, 1)");  // 类型验证：检查结果列的类型

    // FIXME: SQL:2003 does not allow raw NULL in IN clause
    expr("1 in (1, null, 2)").ok();  // 断言验证：期望SQL验证通过
    expr("1 in (null, 1, null, 2)").ok();  // 断言验证：期望SQL验证通过
    expr("1 in (cast(null as integer), null)").ok();  // 断言验证：期望SQL验证通过
    expr("1 in (null, null)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testNullCast() {  // 测试方法：testNullCast
    expr("cast(null as tinyint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TINYINT");  // 类型验证：检查结果列的类型
    expr("cast(null as smallint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("SMALLINT");  // 类型验证：检查结果列的类型
    expr("cast(null as integer)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("cast(null as bigint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT");  // 类型验证：检查结果列的类型
    expr("cast(null as float)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("FLOAT");  // 类型验证：检查结果列的类型
    expr("cast(null as real)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL");  // 类型验证：检查结果列的类型
    expr("cast(null as double)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型
    expr("cast(null as boolean)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("cast(null as varchar(1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(1)");  // 类型验证：检查结果列的类型
    expr("cast(null as char(1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(1)");  // 类型验证：检查结果列的类型
    expr("cast(null as binary(1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BINARY(1)");  // 类型验证：检查结果列的类型
    expr("cast(null as date)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DATE");  // 类型验证：检查结果列的类型
    expr("cast(null as time)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0)");  // 类型验证：检查结果列的类型
    expr("cast(null as timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
    expr("cast(null as decimal)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 0)");  // 类型验证：检查结果列的类型
    expr("cast(null as varbinary(1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARBINARY(1)");  // 类型验证：检查结果列的类型

    expr("cast(null as integer), cast(null as char(1))").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testCastTypeToType() {  // 测试方法：testCastTypeToType
    expr("cast(123 as char)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(123 as varchar)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(x'1234' as binary)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BINARY(1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(x'1234' as varbinary)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARBINARY NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(123 as varchar(3))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(123 as char(3))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('123' as integer)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('123' as double)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('1.0' as real)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0 as tinyint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TINYINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as tinyint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TINYINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0 as smallint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("SMALLINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as integer)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0 as integer)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0 as bigint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as bigint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0 as float)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("FLOAT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as float)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("FLOAT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0 as real)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as real)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0 as double)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as double)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(123 as decimal(6,4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(6, 4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(123 as decimal(6))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(6, 0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(123 as decimal)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.234 as decimal(2,5))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(2, 5) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('4.5' as decimal(3,1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(3, 1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as boolean)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("cast('abc' as varchar(1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as char(1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(x'ff' as binary(1))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BINARY(1) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(multiset[1] as double multiset)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(multiset['abc'] as integer multiset)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as boolean)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1.0e1 as boolean)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("^cast(true as numeric)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Cast function cannot convert value of type BOOLEAN "  // 断言验证：期望SQL验证失败并检查错误消息
        + "to type DECIMAL\\(19, 0\\)");
    // It's a runtime error that 'TRUE' cannot fit into CHAR(3), but at
    // validate time this expression is OK.
    expr("cast(true as char(3))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    // test cast to time type.
    expr("cast('abc' as time)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as time without time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as time with local time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME_WITH_LOCAL_TIME_ZONE(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as time with time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME_TZ(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as time(3))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as time(3) without time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as time(3) with local time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME_WITH_LOCAL_TIME_ZONE(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as time(3) with time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME_TZ(3) NOT NULL");  // 类型验证：检查结果列的类型
    // test cast to timestamp type.
    expr("cast('abc' as timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as timestamp without time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as timestamp with local time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP_WITH_LOCAL_TIME_ZONE(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as timestamp with time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP_TZ(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as timestamp(3))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as timestamp(3) without time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as timestamp(3) with local time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP_WITH_LOCAL_TIME_ZONE(3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as timestamp(3) with time zone)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP_TZ(3) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testCastVariant() {  // 测试方法：testCastVariant
    expr("cast(NULL as variant)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT");  // 类型验证：检查结果列的类型
    expr("cast(1 as variant)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast('abc' as variant)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(TIMESTAMP '2024-09-01 00:00:00' as variant)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(ARRAY[1,2,3] AS VARIANT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(MAP[1, 2, 3, 4] AS VARIANT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT NOT NULL");  // 类型验证：检查结果列的类型

    expr("cast(cast(NULL as variant) as int)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("cast(cast(1 as variant) as int)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("cast(cast(1 as variant) as varchar)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
    expr("cast(cast('abc' as variant) as varchar)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
    expr("cast(cast(TIMESTAMP '2024-09-01 00:00:00' as variant) as timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
    expr("cast(ARRAY[1,2,3] AS VARIANT ARRAY)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(MAP['a','b','c','d'] AS MAP<VARCHAR, VARIANT>)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("(VARCHAR NOT NULL, VARIANT) MAP NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testAccessVariant() {  // 测试方法：testAccessVariant
    expr("cast(1 as variant).field")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT");  // 类型验证：检查结果列的类型
    expr("cast(1 as variant)['field']")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT");  // 类型验证：检查结果列的类型
    expr("cast(1 as variant)[0]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARIANT");  // 类型验证：检查结果列的类型
  }

  @Test void testCastRegisteredType() {  // 测试方法：testCastRegisteredType
    expr("cast(123 as ^customBigInt^)")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Unknown identifier 'CUSTOMBIGINT'");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("cast(123 as sales.customBigInt)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(123 as catalog.sales.customBigInt)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testCastFails() {  // 测试方法：testCastFails
    expr("cast('foo' as ^bar^)")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Unknown identifier 'BAR'");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("cast(multiset[1] as integer)")
        .fails("(?s).*Cast function cannot convert value of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "INTEGER MULTISET to type INTEGER");
    wholeExpr("cast(x'ff' as decimal(5,2))")
        .fails("(?s).*Cast function cannot convert value of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "BINARY\\(1\\) to type DECIMAL\\(5, 2\\)");
    wholeExpr("cast(DATE '1243-12-01' as TIME)")
        .fails("(?s).*Cast function cannot convert value of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "DATE to type TIME.*");
    wholeExpr("cast(TIME '12:34:01' as DATE)")
        .fails("(?s).*Cast function cannot convert value of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "TIME\\(0\\) to type DATE.*");
  }

  @Test void testCastBinaryLiteral() {  // 测试方法：testCastBinaryLiteral
    expr("cast(^x'0dd'^ as binary(5))")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Binary literal string must contain an even number of hexits");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /**
   * Tests whether the GEOMETRY data type is allowed.
   *
   * @see SqlConformance#allowGeometry()
   */
  @Test void testGeometry() {  // 测试方法：testGeometry
    final String err =
        "Geo-spatial extensions and the GEOMETRY data type are not enabled";
    sql("select cast(null as ^geometry^) as g from emp")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.STRICT_2003).fails(err)  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(SqlConformanceEnum.LENIENT).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testDateTime() {  // 测试方法：testDateTime
    // LOCAL_TIME
    expr("LOCALTIME(3)").ok();  // 断言验证：期望SQL验证通过
    expr("LOCALTIME").ok(); // fix sqlcontext later.  // 断言验证：期望SQL验证通过
    wholeExpr("LOCALTIME(1+2)")
        .fails("Argument to function 'LOCALTIME' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIME(NULL)")
        .withTypeCoercion(false)
        .fails("Argument to function 'LOCALTIME' must not be NULL");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIME(NULL)")
        .fails("Argument to function 'LOCALTIME' must not be NULL");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIME(CAST(NULL AS INTEGER))")
        .fails("Argument to function 'LOCALTIME' must not be NULL");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIME()")
        .fails("No match found for function signature LOCALTIME..");  // 断言验证：期望SQL验证失败并检查错误消息
    //  with TZ?
    expr("LOCALTIME")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("LOCALTIME(-1)")
        .fails("Argument to function 'LOCALTIME' must be a positive integer literal");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^LOCALTIME(100000000000000)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Numeric literal '100000000000000' out of range.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIME(4)")
        .fails("Argument to function 'LOCALTIME' must be a valid precision "  // 断言验证：期望SQL验证失败并检查错误消息
            + "between '0' and '3'");
    wholeExpr("LOCALTIME('foo')")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIME('foo')")
        .fails("Argument to function 'LOCALTIME' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息

    // LOCALTIMESTAMP
    expr("LOCALTIMESTAMP(3)").ok();  // 断言验证：期望SQL验证通过
    //    fix sqlcontext later.
    expr("LOCALTIMESTAMP").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("LOCALTIMESTAMP(1+2)")
        .fails("Argument to function 'LOCALTIMESTAMP' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIMESTAMP()")
        .fails("No match found for function signature LOCALTIMESTAMP..");  // 断言验证：期望SQL验证失败并检查错误消息
    // with TZ?
    expr("LOCALTIMESTAMP")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("LOCALTIMESTAMP(-1)")
        .fails("Argument to function 'LOCALTIMESTAMP' must be a positive "  // 断言验证：期望SQL验证失败并检查错误消息
            + "integer literal");
    expr("^LOCALTIMESTAMP(100000000000000)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Numeric literal '100000000000000' out of range.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIMESTAMP(4)")
        .fails("Argument to function 'LOCALTIMESTAMP' must be a valid "  // 断言验证：期望SQL验证失败并检查错误消息
            + "precision between '0' and '3'");
    wholeExpr("LOCALTIMESTAMP('foo')")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("LOCALTIMESTAMP('foo')")
        .fails("Argument to function 'LOCALTIMESTAMP' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息

    // CURRENT_DATE
    wholeExpr("CURRENT_DATE(3)")
        .fails("No match found for function signature CURRENT_DATE..NUMERIC..");  // 断言验证：期望SQL验证失败并检查错误消息
    //    fix sqlcontext later.
    expr("CURRENT_DATE").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("CURRENT_DATE(1+2)")
        .fails("No match found for function signature CURRENT_DATE..NUMERIC..");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("CURRENT_DATE()")
        .fails("No match found for function signature CURRENT_DATE..");  // 断言验证：期望SQL验证失败并检查错误消息
    //  with TZ?
    expr("CURRENT_DATE")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DATE NOT NULL");  // 类型验证：检查结果列的类型
    // I guess -s1 is an expression?
    wholeExpr("CURRENT_DATE(-1)")
        .fails("No match found for function signature CURRENT_DATE..NUMERIC..");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("CURRENT_DATE('foo')")
        .fails(ANY);  // 断言验证：期望SQL验证失败并检查错误消息

    // current_time
    expr("current_time(3)").ok();  // 断言验证：期望SQL验证通过
    //    fix sqlcontext later.
    expr("current_time").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("current_time(1+2)")
        .fails("Argument to function 'CURRENT_TIME' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("current_time()")
        .fails("No match found for function signature CURRENT_TIME..");  // 断言验证：期望SQL验证失败并检查错误消息
    // with TZ?
    expr("current_time")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("current_time(-1)")
        .fails("Argument to function 'CURRENT_TIME' must be a positive integer literal");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^CURRENT_TIME(100000000000000)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Numeric literal '100000000000000' out of range.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("CURRENT_TIME(4)")
        .fails("Argument to function 'CURRENT_TIME' must be a valid precision "  // 断言验证：期望SQL验证失败并检查错误消息
            + "between '0' and '3'");
    wholeExpr("current_time('foo')")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("current_time('foo')")
        .fails("Argument to function 'CURRENT_TIME' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息

    // current_timestamp
    expr("CURRENT_TIMESTAMP(3)").ok();  // 断言验证：期望SQL验证通过
    //    fix sqlcontext later.
    expr("CURRENT_TIMESTAMP").ok();  // 断言验证：期望SQL验证通过
    sql("SELECT CURRENT_TIMESTAMP AS X FROM (VALUES (1))").ok();  // 创建SQL测试：解析并验证SQL语句
    wholeExpr("CURRENT_TIMESTAMP(1+2)")
        .fails("Argument to function 'CURRENT_TIMESTAMP' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("CURRENT_TIMESTAMP()")
        .fails("No match found for function signature CURRENT_TIMESTAMP..");  // 断言验证：期望SQL验证失败并检查错误消息
    // should type be 'TIMESTAMP with TZ'?
    expr("CURRENT_TIMESTAMP")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0) NOT NULL");  // 类型验证：检查结果列的类型
    // should type be 'TIMESTAMP with TZ'?
    expr("CURRENT_TIMESTAMP(2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(2) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("CURRENT_TIMESTAMP(-1)")
        .fails("Argument to function 'CURRENT_TIMESTAMP' must be a positive "  // 断言验证：期望SQL验证失败并检查错误消息
            + "integer literal");
    expr("^CURRENT_TIMESTAMP(100000000000000)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Numeric literal '100000000000000' out of range.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("CURRENT_TIMESTAMP(4)")
        .fails("Argument to function 'CURRENT_TIMESTAMP' must be a valid "  // 断言验证：期望SQL验证失败并检查错误消息
            + "precision between '0' and '3'");
    wholeExpr("CURRENT_TIMESTAMP('foo')")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("CURRENT_TIMESTAMP('foo')")
        .fails("Argument to function 'CURRENT_TIMESTAMP' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息

    // Date literals
    expr("DATE '2004-12-01'").ok();  // 断言验证：期望SQL验证通过
    expr("TIME '12:01:01'").ok();  // 断言验证：期望SQL验证通过
    expr("TIME '11:59:59.99'").ok();  // 断言验证：期望SQL验证通过
    expr("TIME '12:01:01.001'").ok();  // 断言验证：期望SQL验证通过
    expr("TIMESTAMP '2004-12-01 12:01:01'").ok();  // 断言验证：期望SQL验证通过
    expr("TIMESTAMP '2004-12-01 12:01:01.001'").ok();  // 断言验证：期望SQL验证通过

    // REVIEW: Can't think of any date/time/ts literals that will parse,
    // but not validate.
  }

  /**
   * Tests casting to/from date/time types.
   */
  @Test void testDateTimeCast() {  // 测试方法：testDateTimeCast
    wholeExpr("CAST(1 as DATE)")
        .fails("Cast function cannot convert value of type INTEGER to type DATE");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("CAST(DATE '2001-12-21' AS VARCHAR(10))").ok();  // 断言验证：期望SQL验证通过
    expr("CAST( '2001-12-21' AS DATE)").ok();  // 断言验证：期望SQL验证通过
    expr("CAST( TIMESTAMP '2001-12-21 10:12:21' AS VARCHAR(20))").ok();  // 断言验证：期望SQL验证通过
    expr("CAST( TIME '10:12:21' AS VARCHAR(20))").ok();  // 断言验证：期望SQL验证通过
    expr("CAST( '10:12:21' AS TIME)").ok();  // 断言验证：期望SQL验证通过
    expr("CAST( '2004-12-21 10:12:21' AS TIMESTAMP)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testConvertTimezoneFunction() {  // 测试方法：testConvertTimezoneFunction
    wholeExpr("CONVERT_TIMEZONE('UTC', 'America/Los_Angeles',"
        + " CAST('2000-01-01' AS TIMESTAMP))")
        .fails("No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "CONVERT_TIMEZONE\\(<CHARACTER>, <CHARACTER>, <TIMESTAMP>\\)");

    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.REDSHIFT);
    expr("CONVERT_TIMEZONE('UTC', 'America/Los_Angeles',\n"  // 创建表达式测试：解析并验证SQL表达式
        + "  CAST('2000-01-01' AS TIMESTAMP))")
        .withOperatorTable(opTable)
        .columnType("DATE NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("CONVERT_TIMEZONE('UTC', 'America/Los_Angeles')")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'CONVERT_TIMEZONE'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 3 arguments");
    wholeExpr("CONVERT_TIMEZONE('UTC', 'America/Los_Angeles', '2000-01-01')")
        .withOperatorTable(opTable)
        .fails("Cannot apply 'CONVERT_TIMEZONE' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'CONVERT_TIMEZONE\\(<CHAR\\(3\\)>, <CHAR\\(19\\)>, "
            + "<CHAR\\(10\\)>\\)'\\. Supported form\\(s\\): "
            + "'CONVERT_TIMEZONE\\(<CHARACTER>, <CHARACTER>, <DATETIME>\\)'");
    wholeExpr("CONVERT_TIMEZONE('UTC', 'America/Los_Angeles', "
        + "'UTC', CAST('2000-01-01' AS TIMESTAMP))")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'CONVERT_TIMEZONE'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 3 arguments");
  }

  @Test void testToCharFunction() {  // 测试方法：testToCharFunction
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.POSTGRESQL);
    expr("TO_CHAR(CURRENT_TIMESTAMP, 'YYYY-MM-DD HH24:MI:SS.MS TZ')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .ok();  // 断言验证：期望SQL验证通过
    expr("^TO_CHAR(1680080352, 'YYYY-MM-DD HH24:MI:SS.MS TZ')^")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .fails("Cannot apply 'TO_CHAR' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'TO_CHAR\\(<INTEGER>, <CHAR\\(27\\)>\\)'\\. Supported form\\(s\\): "
            + "'TO_CHAR\\(<TIMESTAMP>, <STRING>\\)'");
  }

  @Test void testToDateFunction() {  // 测试方法：testToDateFunction
    wholeExpr("TO_DATE('2000-01-01', 'YYYY-MM-DD')")
        .fails("No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "TO_DATE\\(<CHARACTER>, <CHARACTER>\\)");

    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.POSTGRESQL);
    expr("TO_DATE('2000-01-01', 'YYYY-MM-DD')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("DATE NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("TO_DATE('2000-01-01')")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TO_DATE'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 2 arguments");
    expr("TO_DATE(2000, 'YYYY')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("DATE NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("TO_DATE(2000, 'YYYY')")
        .withOperatorTable(opTable)
        .withTypeCoercion(false)
        .fails("Cannot apply 'TO_DATE' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'TO_DATE\\(<INTEGER>, <CHAR\\(4\\)>\\)'\\. "
            + "Supported form\\(s\\): 'TO_DATE\\(<STRING>, <STRING>\\)'");
    wholeExpr("TO_DATE('2000-01-01', 'YYYY-MM-DD', 'YYYY-MM-DD')")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TO_DATE'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 2 arguments");
  }

  @Test void testToTimestampFunction() {  // 测试方法：testToTimestampFunction
    wholeExpr("TO_TIMESTAMP('2000-01-01 01:00:00', 'YYYY-MM-DD HH:MM:SS')")
        .fails("No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "TO_TIMESTAMP\\(<CHARACTER>, <CHARACTER>\\)");

    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.POSTGRESQL);
    expr("TO_TIMESTAMP('2000-01-01 01:00:00', 'YYYY-MM-DD HH:MM:SS')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("TIMESTAMP_TZ(0) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("TO_TIMESTAMP('2000-01-01 01:00:00')")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TO_TIMESTAMP'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 2 arguments");
    expr("TO_TIMESTAMP(2000, 'YYYY')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("TIMESTAMP_TZ(0) NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("TO_TIMESTAMP(2000, 'YYYY')")
        .withOperatorTable(opTable)
        .withTypeCoercion(false)
        .fails("Cannot apply 'TO_TIMESTAMP' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'TO_TIMESTAMP\\(<INTEGER>, <CHAR\\(4\\)>\\)'\\. "
            + "Supported form\\(s\\): 'TO_TIMESTAMP\\(<STRING>, <STRING>\\)'");
    wholeExpr("TO_TIMESTAMP('2000-01-01 01:00:00', 'YYYY-MM-DD HH:MM:SS',"
        + " 'YYYY-MM-DD')")
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TO_TIMESTAMP'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 2 arguments");
  }

  @Test void testCurrentDatetime() {  // 测试方法：testCurrentDatetime
    final String currentDateTimeExpr = "select ^current_datetime^";
    SqlValidatorFixture shouldFail = sql(currentDateTimeExpr)
        .withConformance(SqlConformanceEnum.BIG_QUERY);
    final String expectedError = "query [select CURRENT_DATETIME]; exception "
        + "[Column 'CURRENT_DATETIME' not found in any table]; class "
        + "[class org.apache.calcite.sql.validate.SqlValidatorException]; pos [line 1 col 8 thru line 1 col 8]";
    shouldFail.fails("Column 'CURRENT_DATETIME' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.BIG_QUERY);
    sql("select current_datetime")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable).ok();  // 断言验证：期望SQL验证通过
    sql("select current_datetime()")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable).ok();  // 断言验证：期望SQL验证通过
    sql("select CURRENT_DATETIME('America/Los_Angeles')")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable).ok();  // 断言验证：期望SQL验证通过
    sql("select CURRENT_DATETIME(CAST(NULL AS VARCHAR(20)))")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testSysDateFunction() {  // 测试方法：testSysDateFunction
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.ORACLE);
    // test oracle sysdate function validate
    expr("SYSDATE")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("DATE NOT NULL");  // 类型验证：检查结果列的类型
    expr("^SYSDATE^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Column 'SYSDATE' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^SYSDATE()^")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .fails("No match found for function signature SYSDATE..");  // 断言验证：期望SQL验证失败并检查错误消息
    // test oracle sysdate function validate within to_char function
    expr("TO_CHAR(SYSDATE, 'MM-DD-YYYY HH24:MI:SS')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("TO_CHAR(^SYSDATE^, 'MM-DD-YYYY HH24:MI:SS')")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Column 'SYSDATE' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^TO_CHAR(SYSDATE)^")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TO_CHAR'. Was expecting 2 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSysTimestampFunction() {  // 测试方法：testSysTimestampFunction
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.ORACLE);
    // test oracle systimestamp function validate
    expr("SYSTIMESTAMP")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("TIMESTAMP_TZ(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("^SYSTIMESTAMP^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Column 'SYSTIMESTAMP' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^SYSTIMESTAMP()^")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .fails("No match found for function signature SYSTIMESTAMP..");  // 断言验证：期望SQL验证失败并检查错误消息
    // test oracle systimestamp function validate within to_char function
    expr("TO_CHAR(SYSTIMESTAMP, 'SSSSS.FF')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("TO_CHAR(^SYSTIMESTAMP^, 'SSSSS.FF')")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Column 'SYSTIMESTAMP' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^TO_CHAR(SYSTIMESTAMP)^")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .fails("Invalid number of arguments to function 'TO_CHAR'. Was expecting 2 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInvalidFunction() {  // 测试方法：testInvalidFunction
    wholeExpr("foo()")
        .fails("No match found for function signature FOO..");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("mod(123)")
        .fails("Invalid number of arguments to function 'MOD'. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 2 arguments");
    assumeTrue(false,
        "test case for [CALCITE-3326], disabled til it is fixed");
    sql("select foo()")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("No match found for function signature FOO..");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInvalidTableFunction() {  // 测试方法：testInvalidTableFunction
    // A table function at most have one input table with row semantics
    sql("select * from table(^invalid(table orders, table emp)^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("A table function at most has one input table with row semantics."  // 断言验证：期望SQL验证失败并检查错误消息
            + " Table function 'INVALID' has multiple input tables with row semantics");
    // Only tables with set semantics may be partitioned
    sql("select * from table(^score(table orders partition by productid)^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Only tables with set semantics may be partitioned."  // 断言验证：期望SQL验证失败并检查错误消息
            + " Invalid PARTITION BY clause in the 0-th operand of table function 'SCORE'");
    // Only tables with set semantics may be ordered
    sql("select * from table(^score(table orders order by orderId)^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Only tables with set semantics may be ordered."  // 断言验证：期望SQL验证失败并检查错误消息
            + " Invalid ORDER BY clause in the 0-th operand of table function 'SCORE'");
  }

  @Test void testTableFunctionWithTableParam() {  // 测试方法：testTableFunctionWithTableParam
    // test input table with row semantic
    sql("select * from table(score(table orders))").ok();  // 创建SQL测试：解析并验证SQL语句
    // test no partition by clause and order by clause for input table with set semantic
    sql("select * from table(topn(table orders, 3))").ok();  // 创建SQL测试：解析并验证SQL语句
    // test one partition key for input table with set semantic
    sql("select * from table(topn(table orders partition by productid, 3))")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // test multiple partition keys for input table with set semantic
    sql("select * from table(topn(table orders partition by (orderId, productid), 3))")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // test one order key for input table with set semantic
    sql("select * from table(topn(table orders order by orderId, 3))")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // test multiple order keys for input table with set semantic
    sql("select * from table(topn(table orders order by (orderId, productid), 3))")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // test complex order-by clause for input table with set semantic
    sql("select * from table(topn(table orders order by (orderId desc, productid asc), 3))")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // test partition by clause and order by clause for input table with set semantic
    sql("select * from table(topn(table orders partition by productid order by orderId, 3))")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // test partition by clause and order by clause for subquery
    sql("select * from table(topn(select * from Orders partition by productid\n "  // 创建SQL测试：解析并验证SQL语句
        + "order by orderId, 3))")
        .ok();  // 断言验证：期望SQL验证通过
    // test multiple input tables
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "similarlity(\n"
        + "  table emp partition by deptno order by empno,\n"
        + "  table emp_b partition by deptno order by empno))")
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUnknownFunctionHandling() {  // 测试方法：testUnknownFunctionHandling
    final SqlValidatorFixture s = fixture().withLenientOperatorLookup(true);
    s.withExpr("concat('a', 2)").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("foo('2001-12-21')").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("\"foo\"('b')").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("foo()").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("'a' || foo(bar('2001-12-21'))").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("cast(foo(5, 2) as DECIMAL)").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("select ascii('xyz')").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("select get_bit(CAST('FFFF' as BINARY), 1)").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("select now()").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("^TIMESTAMP_CMP_TIMESTAMPTZ^").fails("(?s).*");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withExpr("atan(0)").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("select row_number() over () from emp").ok();  // 断言验证：期望SQL验证通过
    s.withExpr("select coalesce(1, 2, 3)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select count() from emp").ok(); // too few args  // 断言验证：期望SQL验证通过
    s.withSql("select sum(1, 2) from emp").ok(); // too many args  // 断言验证：期望SQL验证通过
  }

  @Test void testJdbcFunctionCall() {  // 测试方法：testJdbcFunctionCall
    expr("{fn log10(1)}").ok();  // 断言验证：期望SQL验证通过
    expr("{fn locate('','')}").ok();  // 断言验证：期望SQL验证通过
    expr("{fn insert('',1,2,'')}").ok();  // 断言验证：期望SQL验证通过

    // 'lower' is a valid SQL function but not valid JDBC fn; the JDBC
    // equivalent is 'lcase'
    wholeExpr("{fn lower('Foo' || 'Bar')}")
        .fails("Function '\\{fn LOWER\\}' is not defined");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("{fn lcase('Foo' || 'Bar')}").ok();  // 断言验证：期望SQL验证通过

    expr("{fn power(2, 3)}").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("{fn insert('','',1,2)}")
        .withTypeCoercion(false)
        .fails("(?s).*.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("{fn insert('','',1,2)}").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("{fn insert('','',1)}")
        .fails("(?s).*4.*");  // 断言验证：期望SQL验证失败并检查错误消息

    expr("{fn locate('','',1)}").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("{fn log10('1')}")
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply.*fn LOG10..<CHAR.1.>.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("{fn log10('1')}").ok();  // 断言验证：期望SQL验证通过
    final String expected = "Cannot apply '\\{fn LOG10\\}' to arguments of"
        + " type '\\{fn LOG10\\}\\(<INTEGER>, <INTEGER>\\)'\\. "
        + "Supported form\\(s\\): '\\{fn LOG10\\}\\(<NUMERIC>\\)'";
    wholeExpr("{fn log10(1,1)}")
        .fails(expected);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("{fn fn(1)}")
        .fails("(?s).*Function '.fn FN.' is not defined.*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("{fn hahaha(1)}")
        .fails("(?s).*Function '.fn HAHAHA.' is not defined.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testQuotedFunction() {  // 测试方法：testQuotedFunction
    if (false) {
      // REVIEW jvs 2-Feb-2005:  I am disabling this test because I
      // removed the corresponding support from the parser.  Where in the
      // standard does it state that you're supposed to be able to quote
      // keywords for builtin functions?
      expr("\"CAST\"(1 as double)").ok();  // 断言验证：期望SQL验证通过
      expr("\"POSITION\"('b' in 'alphabet')").ok();  // 断言验证：期望SQL验证通过

      // convert and translate not yet implemented
      //        checkExp("\"CONVERT\"('b' using conversion)");
      //        checkExp("\"TRANSLATE\"('b' using translation)");
      expr("\"OVERLAY\"('a' PLAcing 'b' from 1)").ok();  // 断言验证：期望SQL验证通过
      expr("\"SUBSTRING\"('a' from 1)").ok();  // 断言验证：期望SQL验证通过
      expr("\"TRIM\"('b')").ok();  // 断言验证：期望SQL验证通过
    } else {
      expr("\"TRIM\"('b' ^FROM^ 'a')")  // 创建表达式测试：解析并验证SQL表达式
          .fails("(?s).*Encountered \"FROM\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息

      // Without the "FROM" noise word, TRIM is parsed as a regular
      // function without quoting and built-in function with quoting.
      expr("\"TRIM\"('b', 'FROM', 'a')")  // 创建表达式测试：解析并验证SQL表达式
          .columnType("VARCHAR(1) NOT NULL");  // 类型验证：检查结果列的类型
      expr("TRIM('b')")  // 创建表达式测试：解析并验证SQL表达式
          .columnType("VARCHAR(1) NOT NULL");  // 类型验证：检查结果列的类型
    }
  }

  /**
   * Not able to parse member function yet.
   */
  @Test void testInvalidMemberFunction() {  // 测试方法：testInvalidMemberFunction
    expr("myCol.^func()^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*No match found for function signature FUNC().*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("customer.mySubschema.^memberFunc()^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*No match found for function signature MEMBERFUNC().*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRowtype() {  // 测试方法：testRowtype
    sql("values (1),(2),(1)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("values (1),(2),(1)")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL EXPR$0) NOT NULL");
    sql("values (1,'1'),(2,'2')").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("values (1,'1'),(2,'2')")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL EXPR$0, CHAR(1) NOT NULL EXPR$1) NOT NULL");
    sql("values true")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(BOOLEAN NOT NULL EXPR$0) NOT NULL");
    sql("^values ('1'),(2)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Values passed to VALUES operator must have compatible types");  // 断言验证：期望SQL验证失败并检查错误消息
    if (TODO) {
      sql("values (1),(2.0),(3)")  // 创建SQL测试：解析并验证SQL语句
          .columnType("ROWTYPE(DOUBLE)");  // 类型验证：检查结果列的类型
    }
  }

  @Test void testRow() {  // 测试方法：testRow
    // double-nested rows can confuse validator namespace resolution
    sql("select t.r.\"EXPR$1\".\"EXPR$2\"\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select ((1,2),(3,4,5)) r from dept) t")
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select row(emp.empno, emp.ename) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("RecordType(INTEGER NOT NULL EXPR$0, VARCHAR(20) NOT NULL EXPR$1) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select row(emp.empno + 1, emp.ename) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("RecordType(INTEGER NOT NULL EXPR$0, VARCHAR(20) NOT NULL EXPR$1) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select row((select deptno from dept where dept.deptno = emp.deptno), emp.ename)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp")
        .columnType("RecordType(INTEGER EXPR$0, VARCHAR(20) NOT NULL EXPR$1) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select ROW^(x'12') <> ROW(0.01)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot apply '<>' to arguments of type.*");  // 断言验证：期望SQL验证失败并检查错误消息
    // Test cases for [CALCITE-6911] https://issues.apache.org/jira/browse/CALCITE-6911
    // SqlItemOperator.inferReturnType throws AssertionError for out of bounds accesses
    sql("select ^x[2]^ from (select ROW(1) as x)")  // 创建SQL测试：解析并验证SQL语句
        .fails("ROW type does not have a field with index 2; legal range is 1 to 1");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^x[0]^ from (select ROW(1) as x)")  // 创建SQL测试：解析并验证SQL语句
        .fails("ROW type does not have a field with index 0; legal range is 1 to 1");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^T.x['g']^ from (SELECT CAST(ROW(1) AS ROW(f INT)) AS x) AS T")  // 创建SQL测试：解析并验证SQL语句
        .fails("ROW type does not have a field named 'g': RecordType\\(INTEGER F\\)");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select T.x['f'] from (SELECT CAST(ROW(1) AS ROW(f INT)) AS x) AS T")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testRowWithValidDot() {  // 测试方法：testRowWithValidDot
    sql("select ((1,2),(3,4,5)).\"EXPR$1\".\"EXPR$2\"\n from dept")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select row(1,2).\"EXPR$1\" from dept")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select t.a.\"EXPR$1\" from (select row(1,2) as a from (values (1))) as t")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testRowWithInvalidDotOperation() {  // 测试方法：testRowWithInvalidDotOperation
    final String sql = "select t.^s.\"EXPR$1\"^ from (\n"
        + "  select 1 AS s from (values (1))) as t";
    expr(sql)
        .fails("(?s).*Column 'S\\.EXPR\\$1' not found in table 'T'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("select ^array[1, 2, 3]^.\"EXPR$1\" from dept")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Incompatible types.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("select ^'mystr'^.\"EXPR$1\" from dept")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Incompatible types.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testDotAfterParenthesizedIdentifier() {  // 测试方法：testDotAfterParenthesizedIdentifier
    sql("select (home_address).city from emp_address")  // 创建SQL测试：解析并验证SQL语句
        .columnType("VARCHAR(20) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testMultiset() {  // 测试方法：testMultiset
    expr("multiset[1]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("multiset[1, CAST(null AS DOUBLE)]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("multiset[1.3,2.3]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(2, 1) NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("multiset[1,2.3, cast(4 as bigint)]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 0) NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("multiset['1','22', '333','22']")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(3) NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("^multiset[1, '2']^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Parameters must be of the same type");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("multiset[ROW(1,2)]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("RecordType(INTEGER NOT NULL EXPR$0,"  // 类型验证：检查结果列的类型
            + " INTEGER NOT NULL EXPR$1) NOT NULL MULTISET NOT NULL");
    expr("multiset[ROW(1,2),ROW(2,5)]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("RecordType(INTEGER NOT NULL EXPR$0,"  // 类型验证：检查结果列的类型
            + " INTEGER NOT NULL EXPR$1) NOT NULL MULTISET NOT NULL");
    expr("multiset[ROW(1,2),ROW(3.4,5.4)]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("RecordType(DECIMAL(11, 1) NOT NULL EXPR$0,"  // 类型验证：检查结果列的类型
            + " DECIMAL(11, 1) NOT NULL EXPR$1) NOT NULL MULTISET NOT NULL");
    expr("multiset(select*from emp)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("RecordType(INTEGER NOT NULL EMPNO,"  // 类型验证：检查结果列的类型
            + " VARCHAR(20) NOT NULL ENAME,"
            + " VARCHAR(10) NOT NULL JOB,"
            + " INTEGER MGR,"
            + " TIMESTAMP(0) NOT NULL HIREDATE,"
            + " INTEGER NOT NULL SAL,"
            + " INTEGER NOT NULL COMM,"
            + " INTEGER NOT NULL DEPTNO,"
            + " BOOLEAN NOT NULL SLACKER) NOT NULL MULTISET NOT NULL");
  }

  @Test void testMultisetSetOperators() {  // 测试方法：testMultisetSetOperators
    expr("multiset[1] multiset union multiset[1,2.3]").ok();  // 断言验证：期望SQL验证通过
    expr("multiset[324.2] multiset union multiset[23.2,2.32]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(5, 2) NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("multiset[1] multiset union multiset[1,2.3]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(11, 1) NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    expr("multiset[1] multiset union all multiset[1,2.3]").ok();  // 断言验证：期望SQL验证通过
    expr("multiset[1] multiset except multiset[1,2.3]").ok();  // 断言验证：期望SQL验证通过
    expr("multiset[1] multiset except all multiset[1,2.3]").ok();  // 断言验证：期望SQL验证通过
    expr("multiset[1] multiset intersect multiset[1,2.3]").ok();  // 断言验证：期望SQL验证通过
    expr("multiset[1] multiset intersect all multiset[1,2.3]").ok();  // 断言验证：期望SQL验证通过

    expr("^multiset[1, '2']^ multiset union multiset[1]")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Parameters must be of the same type");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("multiset[ROW(1,2)] multiset intersect multiset[row(3,4)]").ok();  // 断言验证：期望SQL验证通过
    if (TODO) {
      wholeExpr("multiset[ROW(1,'2')] multiset union multiset[ROW(1,2)]")
          .fails("Parameters must be of the same type");  // 断言验证：期望SQL验证失败并检查错误消息
    }
  }

  @Test void testSubMultisetOf() {  // 测试方法：testSubMultisetOf
    expr("multiset[1] submultiset of multiset[1,2.3]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("multiset[1] submultiset of multiset[1]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型

    expr("^multiset[1, '2']^ submultiset of multiset[1]")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Parameters must be of the same type");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("multiset[ROW(1,2)] submultiset of multiset[row(3,4)]").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testElement() {  // 测试方法：testElement
    expr("element(multiset[1])")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("1.0+element(multiset[1])")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(12, 1)");  // 类型验证：检查结果列的类型
    expr("element(multiset['1'])")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("CHAR(1)");  // 类型验证：检查结果列的类型
    expr("element(multiset[1e-2])")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型
    expr("element(multiset[multiset[cast(null as tinyint)]])")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TINYINT MULTISET");  // 类型验证：检查结果列的类型
    // Test case for https://issues.apache.org/jira/projects/CALCITE/issues/CALCITE-6227
    // ELEMENT(NULL) causes an assertion failure.
    expr("element(null)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testMemberOf() {  // 测试方法：testMemberOf
    expr("1 member of multiset[1]")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("1 member of multiset['1']")
        .fails("Cannot compare values of types 'INTEGER', 'CHAR\\(1\\)'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testIsASet() {  // 测试方法：testIsASet
    expr("multiset[1] is a set").ok();  // 断言验证：期望SQL验证通过
    expr("multiset['1'] is a set").ok();  // 断言验证：期望SQL验证通过
    wholeExpr("'a' is a set")
        .fails(".*Cannot apply 'IS A SET' to.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testCardinality() {  // 测试方法：testCardinality
    expr("cardinality(multiset[1])")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("cardinality(multiset['1'])")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("cardinality('a')")
        .fails("Cannot apply 'CARDINALITY' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'CARDINALITY\\(<CHAR\\(1\\)>\\)'\\. Supported form\\(s\\): "
            + "'CARDINALITY\\(<MULTISET>\\)'\n"
            + "'CARDINALITY\\(<ARRAY>\\)'\n"
            + "'CARDINALITY\\(<MAP>\\)'");
  }

  @Test void testPivot() {  // 测试方法：testPivot
    final String sql = "SELECT * FROM emp\n"
        + "PIVOT (sum(sal) AS ss FOR job in ('CLERK' AS c, 'MANAGER' AS m))";
    sql(sql).type("RecordType(INTEGER NOT NULL EMPNO,"
        + " VARCHAR(20) NOT NULL ENAME, INTEGER MGR,"
        + " TIMESTAMP(0) NOT NULL HIREDATE, INTEGER NOT NULL COMM,"
        + " INTEGER NOT NULL DEPTNO, BOOLEAN NOT NULL SLACKER,"
        + " INTEGER C_SS, INTEGER M_SS) NOT NULL");
  }

  @Test void testPivot2() {  // 测试方法：testPivot2
    final String sql = "SELECT *\n"
        + "FROM   (SELECT deptno, job, sal\n"
        + "        FROM   emp)\n"
        + "PIVOT  (SUM(sal) AS sum_sal, COUNT(*) AS \"COUNT\"\n"
        + "        FOR (job) IN ('CLERK', 'MANAGER' mgr, 'ANALYST' AS \"a\"))\n"
        + "ORDER BY deptno";
    final String type = "RecordType(INTEGER NOT NULL DEPTNO, "
        + "INTEGER 'CLERK'_SUM_SAL, BIGINT NOT NULL 'CLERK'_COUNT, "
        + "INTEGER MGR_SUM_SAL, BIGINT NOT NULL MGR_COUNT, INTEGER a_SUM_SAL, "
        + "BIGINT NOT NULL a_COUNT) NOT NULL";
    sql(sql).type(type);
  }

  @Test void testPivotAliases() {  // 测试方法：testPivotAliases
    final String sql = "SELECT *\n"
        + "FROM (\n"
        + "    SELECT deptno, job, sal FROM   emp)\n"
        + "PIVOT (SUM(sal) AS ss\n"
        + "    FOR (job, deptno)\n"
        + "    IN (('A B'/*C*/||' D', 10),\n"
        + "        ('MANAGER', null) mgr,\n"
        + "        ('ANALYST', 30) AS \"a\"))";
    // Oracle uses parse tree without spaces around '||',
    //   'A B'||' D'_10_SUM_SAL
    // but close enough.
    final String type = "RecordType(INTEGER 'A B' || ' D'_10_SS, "
        + "INTEGER MGR_SS, INTEGER a_SS) NOT NULL";
    sql(sql).type(type);
  }

  @Test void testPivotAggAliases() {  // 测试方法：testPivotAggAliases
    final String sql = "SELECT *\n"
        + "FROM (SELECT deptno, job, sal FROM emp)\n"
        + "PIVOT (SUM(sal) AS ss, MIN(job)\n"
        + "    FOR deptno IN (10 AS ten, 20))";
    final String type = "RecordType(INTEGER TEN_SS, VARCHAR(10) TEN, "
        + "INTEGER 20_SS, VARCHAR(10) 20) NOT NULL";
    sql(sql).type(type);
  }

  @Test void testPivotNoValues() {  // 测试方法：testPivotNoValues
    final String sql = "SELECT *\n"
        + "FROM (SELECT deptno, sal, job FROM emp)\n"
        + "PIVOT (sum(sal) AS sum_sal FOR job in ())";
    sql(sql).type("RecordType(INTEGER NOT NULL DEPTNO) NOT NULL");
  }

  /** Output only includes columns not referenced in an aggregate or axis. */
  @Test void testPivotRemoveColumns() {  // 测试方法：testPivotRemoveColumns
    final String sql = "SELECT * FROM emp\n"
        + "PIVOT (sum(sal) AS sum_sal, count(comm) AS count_comm,\n"
        + "      min(hiredate) AS min_hiredate, max(hiredate) AS max_hiredate\n"
        + "  FOR (job, deptno, slacker, mgr, ename)\n"
        + "  IN (('CLERK', 10, false, null, ename) AS c10))";
    sql(sql).type("RecordType(INTEGER NOT NULL EMPNO,"
        + " INTEGER C10_SUM_SAL, BIGINT NOT NULL C10_COUNT_COMM,"
        + " TIMESTAMP(0) C10_MIN_HIREDATE,"
        + " TIMESTAMP(0) C10_MAX_HIREDATE) NOT NULL");
  }

  @Test void testPivotInvalidCol() {  // 测试方法：testPivotInvalidCol
    final String sql = "SELECT * FROM emp\n"
        + "PIVOT (sum(^invalid^) AS sal FOR job in ('CLERK' AS c, 'MANAGER'))";
    sql(sql).fails("Column 'INVALID' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testPivotInvalidCol2() {  // 测试方法：testPivotInvalidCol2
    final String sql = "SELECT * FROM emp\n"
        + "PIVOT (sum(sal) AS sal FOR (job, ^invalid^) in (('CLERK', 'x') AS c))";
    sql(sql).fails("Column 'INVALID' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testPivotMeasureMustBeAgg() {  // 测试方法：testPivotMeasureMustBeAgg
    final String sql = "SELECT * FROM emp\n"
        + "PIVOT (sal ^+^ 1 AS sal1 FOR job in ('CLERK' AS c, 'MANAGER'))";
    sql(sql).fails("(?s).*Encountered \"\\+\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql2 = "SELECT * FROM emp\n"
        + "PIVOT (^log10(sal)^ AS logSal FOR job in ('CLERK' AS c, 'MANAGER'))";
    sql(sql2).fails("Measure expression in PIVOT must use aggregate function");  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql3 = "SELECT * FROM emp\n"
        + "PIVOT (^123^ AS logSal FOR job in ('CLERK' AS c, 'MANAGER'))";
    sql(sql3).fails("(?s).*Encountered \"123\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Tests an expression as argument to an aggregate function in a PIVOT.
   * Both of the columns referenced ({@code sum} and {@code deptno}) are removed
   * from the implicit GROUP BY. */
  @Test void testPivotAggExpression() {  // 测试方法：testPivotAggExpression
    final String sql = "SELECT * FROM (SELECT sal, deptno, job, mgr FROM Emp)\n"
        + "PIVOT (sum(sal + deptno + 1)\n"
        + "   FOR job in ('CLERK' AS c, 'ANALYST' AS a))";
    sql(sql).type("RecordType(INTEGER MGR, INTEGER C, INTEGER A) NOT NULL");
  }

  @Test void testPivotValueMismatch() {  // 测试方法：testPivotValueMismatch
    final String sql = "SELECT * FROM Emp\n"
        + "PIVOT (SUM(sal) FOR job IN (^('A', 'B')^, ('C', 'D')))";
    sql(sql).fails("Value count in PIVOT \\(2\\) must match number of "  // 断言验证：期望SQL验证失败并检查错误消息
        + "FOR columns \\(1\\)");

    final String sql2 = "SELECT * FROM Emp\n"
        + "PIVOT (SUM(sal) FOR job IN (^('A', 'B')^ AS x, ('C', 'D')))";
    sql(sql2).fails("Value count in PIVOT \\(2\\) must match number of "  // 断言验证：期望SQL验证失败并检查错误消息
        + "FOR columns \\(1\\)");

    final String sql3 = "SELECT * FROM Emp\n"
        + "PIVOT (SUM(sal) FOR (job) IN (^('A', 'B')^))";
    sql(sql3).fails("Value count in PIVOT \\(2\\) must match number of "  // 断言验证：期望SQL验证失败并检查错误消息
        + "FOR columns \\(1\\)");

    final String sql4 = "SELECT * FROM Emp\n"
        + "PIVOT (SUM(sal) FOR (job, deptno) IN (^'CLERK'^, 10))";
    sql(sql4).fails("Value count in PIVOT \\(1\\) must match number of "  // 断言验证：期望SQL验证失败并检查错误消息
        + "FOR columns \\(2\\)");
  }

  @Test void testUnpivot() {  // 测试方法：testUnpivot
    final String sql = "SELECT * FROM emp\n"
        + "UNPIVOT (remuneration\n"
        + "  FOR remuneration_type IN (comm AS 'commission',\n"
        + "                            sal as 'salary'))";
    sql(sql).type("RecordType(INTEGER NOT NULL EMPNO,"
        + " VARCHAR(20) NOT NULL ENAME, VARCHAR(10) NOT NULL JOB, INTEGER MGR,"
        + " TIMESTAMP(0) NOT NULL HIREDATE, INTEGER NOT NULL DEPTNO,"
        + " BOOLEAN NOT NULL SLACKER, CHAR(10) NOT NULL REMUNERATION_TYPE,"
        + " INTEGER NOT NULL REMUNERATION) NOT NULL");
  }

  @Test void testUnpivotInvalidColumn() {  // 测试方法：testUnpivotInvalidColumn
    final String sql = "SELECT * FROM emp\n"
        + "UNPIVOT (remuneration\n"
        + "  FOR remuneration_type IN (comm AS 'commission',\n"
        + "                            ^unknownCol^ as 'salary'))";
    sql(sql).fails("Column 'UNKNOWNCOL' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnpivotCannotDeriveMeasureType() {  // 测试方法：testUnpivotCannotDeriveMeasureType
    final String sql = "SELECT * FROM emp\n"
        + "UNPIVOT (remuneration\n"
        + "  FOR remuneration_type IN (^comm^ AS 'commission',\n"
        + "                            ename as 'salary'))";
    sql(sql).fails("In UNPIVOT, cannot derive type for measure 'REMUNERATION'"  // 断言验证：期望SQL验证失败并检查错误消息
        + " because source columns have different data types");
  }

  @Test void testUnpivotValueMismatch() {  // 测试方法：testUnpivotValueMismatch
    final String sql = "SELECT * FROM emp\n"
        + "UNPIVOT (remuneration\n"
        + "  FOR remuneration_type IN (comm AS 'commission',\n"
        + "                            sal AS ^('salary', 1)^))";
    String expected = "Value count in UNPIVOT \\(2\\) must match "
        + "number of FOR columns \\(1\\)";
    sql(sql).fails(expected);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnpivotDuplicateName() {  // 测试方法：testUnpivotDuplicateName
    final String sql = "SELECT * FROM emp\n"
        + "UNPIVOT ((remuneration, ^remuneration^)\n"
        + "  FOR remuneration_type\n"
        + "  IN ((comm, comm) AS 'commission',\n"
        + "      (sal, sal) AS 'salary'))";
    sql(sql).fails("Duplicate column name 'REMUNERATION' in UNPIVOT");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnpivotDuplicateName2() {  // 测试方法：testUnpivotDuplicateName2
    final String sql = "SELECT * FROM emp\n"
        + "UNPIVOT (remuneration\n"
        + "  FOR ^remuneration^ IN (comm AS 'commission',\n"
        + "                         sal AS 'salary'))";
    sql(sql).fails("Duplicate column name 'REMUNERATION' in UNPIVOT");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnpivotDuplicateName3() {  // 测试方法：testUnpivotDuplicateName3
    final String sql = "SELECT * FROM emp\n"
        + "UNPIVOT (remuneration\n"
        + "  FOR ^deptno^ IN (comm AS 'commission',\n"
        + "                         sal AS 'salary'))";
    sql(sql).fails("Duplicate column name 'DEPTNO' in UNPIVOT");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnpivotMissingAs() {  // 测试方法：testUnpivotMissingAs
    final String sql = "SELECT *\n"
        + "FROM (\n"
        + "  SELECT *\n"
        + "  FROM (VALUES (0, 1, 2, 3, 4),\n"
        + "               (10, 11, 12, 13, 14))\n"
        + "      AS t (c0, c1, c2, c3, c4))\n"
        + "UNPIVOT ((m0, m1, m2)\n"
        + "    FOR (a0, a1)\n"
        + "     IN ((c1, c2, c3) AS ('col1','col2'),\n"
        + "         (c2, c3, c4)))";
    sql(sql).type("RecordType(INTEGER NOT NULL C0, VARCHAR(8) NOT NULL A0,"
        + " VARCHAR(8) NOT NULL A1, INTEGER M0, INTEGER M1,"
        + " INTEGER M2) NOT NULL");
  }

  @Test void testUnpivotMissingAs2() {  // 测试方法：testUnpivotMissingAs2
    final String sql = "SELECT *\n"
        + "FROM (\n"
        + "  SELECT *\n"
        + "  FROM (VALUES (0, 1, 2, 3, 4),\n"
        + "               (10, 11, 12, 13, 14))\n"
        + "      AS t (c0, c1, c2, c3, c4))\n"
        + "UNPIVOT ((m0, m1, m2)\n"
        + "    FOR (^a0^, a1)\n"
        + "     IN ((c1, c2, c3) AS (6, true),\n"
        + "         (c2, c3, c4)))";
    sql(sql).fails("In UNPIVOT, cannot derive type for axis 'A0'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testMatchRecognizeWithDistinctAggregation() {  // 测试方法：testMatchRecognizeWithDistinctAggregation
    final String sql = "SELECT *\n"
        + "FROM emp\n"
        + "MATCH_RECOGNIZE (\n"
        + "  ORDER BY ename\n"
        + "  MEASURES\n"
        + "    ^COUNT(DISTINCT A.deptno)^ AS deptno\n"
        + "  PATTERN (A B)\n"
        + "  DEFINE\n"
        + "    A AS A.empno = 123\n"
        + ") AS T";
    sql(sql).fails("DISTINCT/ALL not allowed with "  // 断言验证：期望SQL验证失败并检查错误消息
        + "COUNT\\(DISTINCT `A`\\.`DEPTNO`\\) function");
  }

  @Test void testIntervalTimeUnitEnumeration() {  // 测试方法：testIntervalTimeUnitEnumeration
    // Since there is validation code relaying on the fact that the
    // enumerated time unit ordinals in SqlIntervalQualifier starts with 0
    // and ends with 5, this test is here to make sure that if someone
    // changes how the time untis are setup, an early feedback will be
    // generated by this test.
    assertThat(TimeUnit.YEAR.ordinal(), is(0));
    assertThat(TimeUnit.MONTH.ordinal(), is(1));
    assertThat(TimeUnit.DAY.ordinal(), is(2));
    assertThat(TimeUnit.HOUR.ordinal(), is(3));
    assertThat(TimeUnit.MINUTE.ordinal(), is(4));
    assertThat(TimeUnit.SECOND.ordinal(), is(5));
    boolean b =
        (TimeUnit.YEAR.ordinal()
            < TimeUnit.MONTH.ordinal())
            && (TimeUnit.MONTH.ordinal()
            < TimeUnit.DAY.ordinal())
            && (TimeUnit.DAY.ordinal()
            < TimeUnit.HOUR.ordinal())
            && (TimeUnit.HOUR.ordinal()
            < TimeUnit.MINUTE.ordinal())
            && (TimeUnit.MINUTE.ordinal()
            < TimeUnit.SECOND.ordinal());
    assertTrue(b);
  }

  @Test void testIntervalMonthsConversion() {  // 测试方法：testIntervalMonthsConversion
    expr("INTERVAL '1' YEAR").assertInterval(is(12L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '5' MONTH").assertInterval(is(5L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '3-2' YEAR TO MONTH").assertInterval(is(38L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '-5-4' YEAR TO MONTH").assertInterval(is(-64L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '100-2' YEAR TO MONTH").assertInterval(is(1202L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1000-2' YEAR TO MONTH").assertInterval(is(12002L));  // 创建表达式测试：解析并验证SQL表达式
  }

  @Test void testIntervalMillisConversion() {  // 测试方法：testIntervalMillisConversion
    expr("INTERVAL '1' DAY").assertInterval(is(86_400_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1' HOUR").assertInterval(is(3_600_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1' MINUTE").assertInterval(is(60_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1' SECOND").assertInterval(is(1_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1:05' HOUR TO MINUTE").assertInterval(is(3_900_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1:05' MINUTE TO SECOND").assertInterval(is(65_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1 1' DAY TO HOUR").assertInterval(is(90_000_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1 1:05' DAY TO MINUTE").assertInterval(is(90_300_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1 1:05:03' DAY TO SECOND").assertInterval(is(90_303_000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1 1:05:03.12345' DAY TO SECOND")  // 创建表达式测试：解析并验证SQL表达式
        .assertInterval(is(90_303_123L));
    expr("INTERVAL '1.12345' SECOND").assertInterval(is(1_123L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1:05.12345' MINUTE TO SECOND").assertInterval(is(65_123L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1:05:03' HOUR TO SECOND").assertInterval(is(3903000L));  // 创建表达式测试：解析并验证SQL表达式
    expr("INTERVAL '1:05:03.12345' HOUR TO SECOND")  // 创建表达式测试：解析并验证SQL表达式
        .assertInterval(is(3_903_123L));
  }

  @Test void testDatetimePlusNullInterval() {  // 测试方法：testDatetimePlusNullInterval
    expr("TIME '8:8:8' + cast(NULL AS interval hour)").columnType("TIME(0)");  // 类型验证：检查结果列的类型
    expr("TIME '8:8:8' + cast(NULL AS interval YEAR)").columnType("TIME(0)");  // 类型验证：检查结果列的类型
    expr("TIMESTAMP '1990-12-12 12:12:12' + cast(NULL AS interval hour)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
    expr("TIMESTAMP '1990-12-12 12:12:12' + cast(NULL AS interval YEAR)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型

    expr("cast(NULL AS interval hour) + TIME '8:8:8'").columnType("TIME(0)");  // 类型验证：检查结果列的类型
    expr("cast(NULL AS interval YEAR) + TIME '8:8:8'").columnType("TIME(0)");  // 类型验证：检查结果列的类型
    expr("cast(NULL AS interval hour) + TIMESTAMP '1990-12-12 12:12:12'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
    expr("cast(NULL AS interval YEAR) + TIMESTAMP '1990-12-12 12:12:12'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
  }

  @Test void testIntervalLiterals() {  // 测试方法：testIntervalLiterals
    // First check that min, max, and defaults are what we expect
    // (values used in subtests depend on these being true to
    // accurately test bounds)
    final RelDataTypeSystem typeSystem =
        fixture().factory.getTypeFactory().getTypeSystem();
    final RelDataTypeSystem defTypeSystem = RelDataTypeSystem.DEFAULT;
    for (SqlTypeName typeName : SqlTypeName.INTERVAL_TYPES) {
      assertThat(typeSystem.getMinPrecision(typeName), is(1));
      assertThat(typeSystem.getMaxPrecision(typeName), is(10));
      assertThat(typeSystem.getDefaultPrecision(typeName), is(2));
      assertThat(typeSystem.getMinScale(typeName), is(0));
      assertThat(typeSystem.getMaxScale(typeName), is(9));
      assertThat(typeSystem.getDefaultScale(typeName), is(6));
    }

    final SqlValidatorFixture f = fixture();
    final IntervalTest.Fixture intervalFixture = new IntervalTest.Fixture() {
      @Override public IntervalTest.Fixture2 expr(String s) {
        return getFixture2(f.withExpr(s));
      }

      @Override public IntervalTest.Fixture2 wholeExpr(String s) {
        return getFixture2(f.withExpr(s).withWhole(true));
      }

      private IntervalTest.Fixture2 getFixture2(SqlValidatorFixture f2) {
        return new IntervalTest.Fixture2() {
          @Override public void fails(String message) {
            f2.fails(message);  // 断言验证：期望SQL验证失败并检查错误消息
          }

          @Override public void columnType(String expectedType) {
            f2.columnType(expectedType);  // 类型验证：检查结果列的类型
          }

          @Override public IntervalTest.Fixture2 assertParse(String expected) {
            return this;
          }
        };
      }
    };

    new IntervalTest(intervalFixture).testAll();
  }

  @Test void testIntervalExpression() {  // 测试方法：testIntervalExpression
    expr("interval 1 hour").columnType("INTERVAL HOUR NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval (2 + 3) month").columnType("INTERVAL MONTH NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval (cast(null as integer)) year").columnType("INTERVAL YEAR");  // 类型验证：检查结果列的类型
    expr("interval (cast(null as integer)) year(2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR(2)");  // 类型验证：检查结果列的类型
    expr("interval (date '1970-01-01') hour").withWhole(true)  // 创建表达式测试：解析并验证SQL表达式
        .fails("Cannot apply 'INTERVAL' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'INTERVAL <DATE> <INTERVAL HOUR>'\\. Supported form\\(s\\): "
            + "'INTERVAL <NUMERIC> <DATETIME_INTERVAL>'");
    expr("interval (nullif(true, true)) hour").withWhole(true)  // 创建表达式测试：解析并验证SQL表达式
        .fails("Cannot apply 'INTERVAL' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'INTERVAL <BOOLEAN> <INTERVAL HOUR>'\\. Supported form\\(s\\): "
            + "'INTERVAL <NUMERIC> <DATETIME_INTERVAL>'");
    expr("interval (interval '1' day) hour").withWhole(true)  // 创建表达式测试：解析并验证SQL表达式
        .fails("Cannot apply 'INTERVAL' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'INTERVAL <INTERVAL DAY> <INTERVAL HOUR>'\\. "
            + "Supported form\\(s\\): "
            + "'INTERVAL <NUMERIC> <DATETIME_INTERVAL>'");
    sql("select interval empno hour as h from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTERVAL HOUR NOT NULL");  // 类型验证：检查结果列的类型
    sql("select interval emp.mgr hour as h from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTERVAL HOUR");  // 类型验证：检查结果列的类型
    expr("interval '1' second(1, 0)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL SECOND(1, 0) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testIntervalOperators() {  // 测试方法：testIntervalOperators
    expr("interval '1' hour + TIME '8:8:8'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("TIME '8:8:8' - interval '1' hour")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("TIME '8:8:8' + interval '1' hour")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIME(0) NOT NULL");  // 类型验证：检查结果列的类型

    expr("interval '1' day + interval '1' DAY(4)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' day(5) + interval '1' DAY")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' day + interval '1' HOUR(10)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO HOUR NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' day + interval '1' MINUTE")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO MINUTE NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' day + interval '1' second")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO SECOND NOT NULL");  // 类型验证：检查结果列的类型

    expr("interval '1:2' hour to minute + interval '1' second")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL HOUR TO SECOND NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1:3' hour to minute + interval '1 1:2:3.4' day to second")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO SECOND NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1:2' hour to minute + interval '1 1' day to hour")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO MINUTE NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1:2' hour to minute + interval '1 1' day to hour")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO MINUTE NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1 2' day to hour + interval '1:1' minute to second")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO SECOND NOT NULL");  // 类型验证：检查结果列的类型

    expr("interval '1' year + interval '1' month")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR TO MONTH NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' day - interval '1' hour")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO HOUR NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' year - interval '1' month")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR TO MONTH NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' month - interval '1' year")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR TO MONTH NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("interval '1' year + interval '1' day")
        .fails("(?s).*Cannot apply '\\+' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTERVAL YEAR> \\+ <INTERVAL DAY>'.*");
    wholeExpr("interval '1' month + interval '1' second")
        .fails("(?s).*Cannot apply '\\+' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTERVAL MONTH> \\+ <INTERVAL SECOND>'.*");
    wholeExpr("interval '1' year - interval '1' day")
        .fails("(?s).*Cannot apply '-' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTERVAL YEAR> - <INTERVAL DAY>'.*");
    wholeExpr("interval '1' month - interval '1' second")
        .fails("(?s).*Cannot apply '-' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTERVAL MONTH> - <INTERVAL SECOND>'.*");

    // mixing between datetime and interval todo        checkExpType("date
    // '1234-12-12' + INTERVAL '1' month + interval '1' day","DATE"); todo
    //      checkExpFails("date '1234-12-12' + (INTERVAL '1' month +
    // interval '1' day)","?");

    // multiply operator
    expr("interval '1' year * 2")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("1.234*interval '1 1:2:3' day to second ")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY TO SECOND NOT NULL");  // 类型验证：检查结果列的类型

    // division operator
    expr("interval '1' month / 0.1")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL MONTH NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1-2' year TO month / 0.1e-9")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR TO MONTH NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("1.234/interval '1 1:2:3' day to second")
        .fails("(?s).*Cannot apply '/' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<DECIMAL.4, 3.> / <INTERVAL DAY TO SECOND>'.*");
  }

  @Test void testTimestampAddAndDiff() {  // 测试方法：testTimestampAddAndDiff
    List<String> tsi = ImmutableList.<String>builder()
        .add("FRAC_SECOND")
        .add("MICROSECOND")
        .add("MINUTE")
        .add("HOUR")
        .add("DAY")
        .add("WEEK")
        .add("MONTH")
        .add("QUARTER")
        .add("YEAR")
        .add("SQL_TSI_FRAC_SECOND")
        .add("SQL_TSI_MICROSECOND")
        .add("SQL_TSI_MINUTE")
        .add("SQL_TSI_HOUR")
        .add("SQL_TSI_DAY")
        .add("SQL_TSI_WEEK")
        .add("SQL_TSI_MONTH")
        .add("SQL_TSI_QUARTER")
        .add("SQL_TSI_YEAR")
        .build();

    List<String> functions = ImmutableList.<String>builder()
        .add("timestampadd(%s, 12, current_timestamp)")
        .add("timestampdiff(%s, current_timestamp, current_timestamp)")
        .build();

    for (String interval : tsi) {
      for (String function : functions) {
        expr(String.format(Locale.ROOT, function, interval)).ok();  // 断言验证：期望SQL验证通过
      }
    }

    expr("timestampadd(SQL_TSI_WEEK, 2, current_timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("timestampadd(SQL_TSI_WEEK, 2, cast(null as timestamp))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
    expr("timestampdiff(SQL_TSI_WEEK, current_timestamp, current_timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("timestampdiff(SQL_TSI_WEEK, cast(null as timestamp), current_timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型

    expr("timestampadd(^incorrect^, 1, current_timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .fails("'INCORRECT' is not a valid time frame");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("timestampdiff(^incorrect^, current_timestamp, current_timestamp)")  // 创建表达式测试：解析并验证SQL表达式
        .fails("'INCORRECT' is not a valid time frame");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testTimestampAddNullInterval() {  // 测试方法：testTimestampAddNullInterval
    expr("timestampadd(SQL_TSI_SECOND, cast(NULL AS INTEGER),"  // 创建表达式测试：解析并验证SQL表达式
        + " current_timestamp)")
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
    expr("timestampadd(SQL_TSI_DAY, cast(NULL AS INTEGER),"  // 创建表达式测试：解析并验证SQL表达式
        + " current_timestamp)")
        .columnType("TIMESTAMP(0)");  // 类型验证：检查结果列的类型
  }

  @Test void testNumericOperators() {  // 测试方法：testNumericOperators
    // unary operator
    expr("- cast(1 as TINYINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TINYINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("+ cast(1 as INT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("- cast(1 as FLOAT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("FLOAT NOT NULL");  // 类型验证：检查结果列的类型
    expr("+ cast(1 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("-1.643")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(4, 3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("+1.643")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(4, 3) NOT NULL");  // 类型验证：检查结果列的类型

    // addition operator
    expr("cast(1 as TINYINT) + cast(5 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as SMALLINT) + cast(5 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT");  // 类型验证：检查结果列的类型
    expr("cast(1 as REAL) + cast(5 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as REAL) + cast(5 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型
    expr("cast(null as REAL) + cast(5 as REAL)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL");  // 类型验证：检查结果列的类型

    expr("cast(1 as DECIMAL(5, 2)) + cast(1 as REAL)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) + cast(1 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DECIMAL(5, 2)) + cast(1 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型

    expr("1.543 + 2.34")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(5, 3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) + cast(1 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as NUMERIC(5, 2)) + cast(1 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(13, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) + cast(null as SMALLINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(8, 2)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) + cast(1 as TINYINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(6, 2) NOT NULL");  // 类型验证：检查结果列的类型

    expr("cast(1 as DECIMAL(5, 2)) + cast(1 as DECIMAL(5, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(6, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) + cast(1 as DECIMAL(6, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(7, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(4, 2)) + cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(7, 4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DECIMAL(4, 2)) + cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(7, 4)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(19, 2)) + cast(1 as DECIMAL(19, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 2) NOT NULL");  // 类型验证：检查结果列的类型

    // subtraction operator
    expr("cast(1 as TINYINT) - cast(5 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as INTEGER) - cast(5 as SMALLINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("cast(1 as INTEGER) - cast(5 as REAL)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as REAL) - cast(5 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型
    expr("cast(null as REAL) - cast(5 as REAL)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL");  // 类型验证：检查结果列的类型

    expr("cast(1 as DECIMAL(5, 2)) - cast(1 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DOUBLE) - cast(1 as DECIMAL)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型

    expr("1.543 - 24")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(14, 3) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5)) - cast(1 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) - cast(1 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(13, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) - cast(null as SMALLINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(8, 2)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) - cast(1 as TINYINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(6, 2) NOT NULL");  // 类型验证：检查结果列的类型

    expr("cast(1 as DECIMAL(5, 2)) - cast(1 as DECIMAL(7))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) - cast(1 as DECIMAL(6, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(7, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(4, 2)) - cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(7, 4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DECIMAL) - cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 4)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(19, 2)) - cast(1 as DECIMAL(19, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 2) NOT NULL");  // 类型验证：检查结果列的类型

    // multiply operator
    expr("cast(1 as TINYINT) * cast(5 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as SMALLINT) * cast(5 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT");  // 类型验证：检查结果列的类型
    expr("cast(1 as REAL) * cast(5 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as REAL) * cast(5 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型

    expr("cast(1 as DECIMAL(7, 3)) * 1.654")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(11, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DECIMAL(7, 3)) * cast (1.654 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型

    expr("cast(null as DECIMAL(5, 2)) * cast(1 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 2)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) * cast(1 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(15, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) * cast(1 as SMALLINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 2) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) * cast(1 as TINYINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(8, 2) NOT NULL");  // 类型验证：检查结果列的类型

    expr("cast(1 as DECIMAL(5, 2)) * cast(1 as DECIMAL(5, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) * cast(1 as DECIMAL(6, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(11, 4) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(4, 2)) * cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DECIMAL(4, 2)) * cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 6)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(4, 10)) * cast(null as DECIMAL(6, 10))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(10, 19)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(19, 2)) * cast(1 as DECIMAL(19, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 4) NOT NULL");  // 类型验证：检查结果列的类型

    // divide operator
    expr("cast(1 as TINYINT) / cast(5 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as SMALLINT) / cast(5 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT");  // 类型验证：检查结果列的类型
    expr("cast(1 as REAL) / cast(5 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("REAL NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as REAL) / cast(5 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(7, 3)) / 1.654")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(15, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DECIMAL(7, 3)) / cast (1.654 as DOUBLE)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型

    expr("cast(null as DECIMAL(5, 2)) / cast(1 as BIGINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 6)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) / cast(1 as INTEGER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(16, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) / cast(1 as SMALLINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(11, 8) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) / cast(1 as TINYINT)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(9, 6) NOT NULL");  // 类型验证：检查结果列的类型

    expr("cast(1 as DECIMAL(5, 2)) / cast(1 as DECIMAL(5, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(13, 8) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(5, 2)) / cast(1 as DECIMAL(6, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(14, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(4, 2)) / cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(15, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as DECIMAL(4, 2)) / cast(1 as DECIMAL(6, 4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(15, 6)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(4, 10)) / cast(null as DECIMAL(6, 19))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 6)");  // 类型验证：检查结果列的类型
    expr("cast(1 as DECIMAL(19, 2)) / cast(1 as DECIMAL(19, 2))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(19, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("4/3")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("-4.0/3")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(13, 12) NOT NULL");  // 类型验证：检查结果列的类型
    expr("4/3.0")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(17, 6) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(2.3 as float)/3")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("FLOAT NOT NULL");  // 类型验证：检查结果列的类型
    // null
    expr("cast(2.3 as float)/null")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("FLOAT");  // 类型验证：检查结果列的类型
  }

  @Test void testFloorCeil() {  // 测试方法：testFloorCeil
    expr("floor(cast(null as tinyint))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("TINYINT");  // 类型验证：检查结果列的类型
    expr("floor(1.2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(2, 0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("floor(1)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("floor(1.2e-2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("floor(interval '2' day)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY NOT NULL");  // 类型验证：检查结果列的类型

    expr("ceil(cast(null as bigint))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT");  // 类型验证：检查结果列的类型
    expr("ceil(1.2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DECIMAL(2, 0) NOT NULL");  // 类型验证：检查结果列的类型
    expr("ceil(1)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    expr("ceil(1.2e-2)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    expr("ceil(interval '2' second)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL SECOND NOT NULL");  // 类型验证：检查结果列的类型
  }

  /** Tests that EXTRACT, FLOOR, CEIL functions accept abbreviations for
   * time units (such as "Y" for "YEAR").
   *
   * <p>This used to be accomplished via the now deprecated
   * {@code timeUnitCodes} method in {@link SqlParser.Config}, and is now
   * accomplished via
   * {@link RelDataTypeSystem#deriveTimeFrameSet(TimeFrameSet)}. */
  @Test void testTimeUnitCodes() {  // 测试方法：testTimeUnitCodes
    final Map<String, TimeUnit> simpleCodes =
        ImmutableMap.<String, TimeUnit>builder()
            .put("Y", TimeUnit.YEAR)
            .put("M", TimeUnit.MONTH)
            .put("D", TimeUnit.DAY)
            .put("H", TimeUnit.HOUR)
            .put("N", TimeUnit.MINUTE)
            .put("S", TimeUnit.SECOND)
            .build();

    // Time unit abbreviations for Microsoft SQL Server
    final Map<String, TimeUnit> mssqlCodes =
        ImmutableMap.<String, TimeUnit>builder()
            .put("Y", TimeUnit.YEAR)
            .put("YY", TimeUnit.YEAR)
            .put("YYYY", TimeUnit.YEAR)
            .put("Q", TimeUnit.QUARTER)
            .put("QQ", TimeUnit.QUARTER)
            .put("M", TimeUnit.MONTH)
            .put("MM", TimeUnit.MONTH)
            .put("W", TimeUnit.WEEK)
            .put("WK", TimeUnit.WEEK)
            .put("WW", TimeUnit.WEEK)
            .put("DY", TimeUnit.DOY)
            .put("DW", TimeUnit.DOW)
            .put("D", TimeUnit.DAY)
            .put("DD", TimeUnit.DAY)
            .put("H", TimeUnit.HOUR)
            .put("HH", TimeUnit.HOUR)
            .put("N", TimeUnit.MINUTE)
            .put("MI", TimeUnit.MINUTE)
            .put("S", TimeUnit.SECOND)
            .put("SS", TimeUnit.SECOND)
            .put("MS", TimeUnit.MILLISECOND)
            .build();

    checkTimeUnitCodes(ImmutableMap.of());
    checkTimeUnitCodes(simpleCodes);
    checkTimeUnitCodes(mssqlCodes);
  }

  /** Checks parsing of built-in functions that accept time unit
   * abbreviations.
   *
   * <p>For example, {@code EXTRACT(Y FROM orderDate)} is using
   * "Y" as an abbreviation for "YEAR".
   *
   * <p>Override if your parser supports more such functions. */
  protected void checkTimeUnitCodes(Map<String, TimeUnit> timeUnitCodes) {
    SqlValidatorFixture f = fixture()
        .withFactory(tf ->
            tf.withTypeSystem(typeSystem ->
                new DelegatingTypeSystem(typeSystem) {
                  @Override public TimeFrameSet deriveTimeFrameSet(
                      TimeFrameSet frameSet) {
                    TimeFrameSet.Builder b = TimeFrameSet.builder();
                    b.addAll(frameSet);
                    timeUnitCodes.forEach((name, unit) ->
                        b.addAlias(name, unit.name()));
                    return b.build();
                  }
                }));
    final String ts = "TIMESTAMP '2020-08-27 18:16:43'";
    BiConsumer<String, TimeUnit> validConsumer = (abbrev, timeUnit) -> {
      f.withSql("select extract(" + abbrev + " from " + ts + ")").ok();
      f.withSql("select floor(" + ts + " to " + abbrev + ")").ok();
      f.withSql("select ceil(" + ts + " to " + abbrev + ")").ok();
    };
    BiConsumer<String, TimeUnit> invalidConsumer = (abbrev, timeUnit) -> {
      final String upAbbrev = abbrev.toUpperCase(Locale.ROOT);
      String message = "'" + upAbbrev + "' is not a valid time frame";
      f.withSql("select extract(^" + abbrev + "^ from " + ts + ")")
          .fails(message);
      f.withSql("SELECT FLOOR(" + ts + " to ^" + abbrev + "^)")
          .fails(message);
      f.withSql("SELECT CEIL(" + ts + " to ^" + abbrev + "^)")
          .fails(message);
    };

    // Check that each valid code passes each query that it should.
    timeUnitCodes.forEach(validConsumer);

    // If "M" is a valid code then "m" should be also.
    timeUnitCodes.forEach((abbrev, timeUnit) ->
        validConsumer.accept(abbrev.toLowerCase(Locale.ROOT), timeUnit));

    // Check that invalid codes generate the right error messages.
    final Map<String, TimeUnit> invalidCodes =
        ImmutableMap.of("A", TimeUnit.YEAR,
            "a", TimeUnit.YEAR);
    invalidCodes.forEach(invalidConsumer);
  }

  /** Tests parsing of built-in functions that accept time unit
   * "WEEK(WEEKDAY)". */
  @Test void testWeekdayCustomTimeFrames() {  // 测试方法：testWeekdayCustomTimeFrames
    SqlValidatorFixture f = fixture()
        .withOperatorTable(operatorTableFor(SqlLibrary.BIG_QUERY));

    // Check that each valid code passes each query that it should.
    final String ds = "DATE '2022-12-25'";
    Consumer<String> validConsumer = weekday -> {
      f.withSql("select date_trunc(" + ds + ", " + weekday + ")").ok();  // 断言验证：期望SQL验证通过
    };
    validConsumer.accept("WEEK");
    validConsumer.accept("WEEK(SUNDAY)");
    validConsumer.accept("WEEK(MONDAY)");
    validConsumer.accept("WEEK(TUESDAY)");
    validConsumer.accept("WEEK(WEDNESDAY)");
    validConsumer.accept("WEEK(THURSDAY)");
    validConsumer.accept("WEEK(FRIDAY)");
    validConsumer.accept("WEEK(SUNDAY)");

    // Check that each invalid code fails each query that it should.
    Consumer<String> invalidConsumer = weekday -> {
      String errorMessage = "Column '" + weekday + "' not found in any table";
      f.withSql("select date_trunc(" + ds + ", ^" + weekday + "^)")
          .fails(errorMessage);  // 断言验证：期望SQL验证失败并检查错误消息
    };
    invalidConsumer.accept("A");
  }

  public void checkWinFuncExpWithWinClause(
      String sql,
      String expectedMsgPattern) {
    winExp(sql).fails(expectedMsgPattern);
  }

  // test window partition clause. See SQL 2003 specification for detail
  @Disabled
  void testWinPartClause() {
    win("window w as (w2 order by deptno), w2 as (^rang^e 100 preceding)")
        .fails("Referenced window cannot have framing declarations");  // 断言验证：期望SQL验证失败并检查错误消息
    // Test specified collation, window clause syntax rule 4,5.
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-820">[CALCITE-820]
   * Validate that window functions have OVER clause</a>, and
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1340">[CALCITE-1340]
   * Window aggregates give invalid errors</a>. */
  @Test void testWindowFunctionsWithoutOver() {  // 测试方法：testWindowFunctionsWithoutOver
    winSql("select sum(empno)\n"
        + "from emp\n"
        + "group by deptno\n"
        + "order by ^row_number()^")
        .fails("OVER clause is necessary for window functions");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^rank()^\n"
        + "from emp")
        .fails("OVER clause is necessary for window functions");  // 断言验证：期望SQL验证失败并检查错误消息

    // With [CALCITE-1340], the validator would see RANK without OVER,
    // mistakenly think this is an aggregate query, and wrongly complain
    // about the PARTITION BY: "Expression 'DEPTNO' is not being grouped"
    winSql("select cume_dist() over w , ^rank()^\n"
        + "from emp\n"
        + "window w as (partition by deptno order by deptno)")
        .fails("OVER clause is necessary for window functions");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^nth_value(sal, 2)^\n"
        + "from emp")
        .fails("OVER clause is necessary for window functions");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testOverInPartitionBy() {  // 测试方法：testOverInPartitionBy
    winSql("select sum(deptno) over ^(partition by sum(deptno)\n"
        + "over(order by deptno))^ from emp")
        .fails("PARTITION BY expression should not contain OVER clause");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select sum(deptno) over w\n"
        + "from emp\n"
        + "window w as ^(partition by sum(deptno) over(order by deptno))^")
        .fails("PARTITION BY expression should not contain OVER clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6442">[CALCITE-6442]
   * Validator rejects FILTER in OVER windows</a>. */
  @Test void testOverFilter() {  // 测试方法：testOverFilter
    winSql("SELECT deptno,\n"
        + "       ^COUNT(DISTINCT deptno) FILTER (WHERE deptno > 10)^\n"
        + "OVER win AS agg\n"
        + "FROM emp\n"
         + "WINDOW win AS (PARTITION BY empno)")
        .fails("OVER must be applied to aggregate function");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testOverInOrderBy() {  // 测试方法：testOverInOrderBy
    winSql("select sum(deptno) over ^(order by sum(deptno)\n"
        + "over(order by deptno))^ from emp")
        .fails("ORDER BY expression should not contain OVER clause");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select sum(deptno) over w\n"
        + "from emp\n"
        + "window w as ^(order by sum(deptno) over(order by deptno))^")
        .fails("ORDER BY expression should not contain OVER clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testAggregateFunctionInOver() {  // 测试方法：testAggregateFunctionInOver
    final String sql = "select sum(deptno) over (order by count(empno))\n"
        + "from emp\n"
        + "group by deptno";
    winSql(sql).ok();  // 断言验证：期望SQL验证通过
    final String sql2 = "select sum(^empno^) over (order by count(empno))\n"
        + "from emp\n"
        + "group by deptno";
    winSql(sql2).fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testAggregateInsideOverClause() {  // 测试方法：testAggregateInsideOverClause
    final String sql = "select ^empno^,\n"
        + "  sum(empno) over (partition by min(sal)) empno_sum\n"
        + "from emp";
    sql(sql).fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql2 = "select ^empno^,\n"
        + "  sum(empno) over (partition by min(sal)) empno_sum\n"
        + "from emp\n"
        + "group by empno";
    sql(sql2).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testAggregateInsideOverClause2() {  // 测试方法：testAggregateInsideOverClause2
    final String sql = "select ^empno^,\n"
        + "  sum(empno) over ()\n"
        + "  + sum(empno) over (partition by min(sal)) empno_sum\n"
        + "from emp";
    sql(sql).fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testWindowFunctions() {  // 测试方法：testWindowFunctions
    // SQL 03 Section 6.10

    // Window functions may only appear in the <select list> of a
    // <query specification> or <select statement: single row>,
    // or the <order by clause> of a simple table query.
    // See 4.15.3 for detail
    winSql("select *\n"
        + " from emp\n"
        + " where ^sum(sal) over (partition by deptno\n"
        + "    order by empno\n"
        + "    rows 3 preceding)^ > 10")
        .fails("Windowed aggregate expression is illegal in WHERE clause");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select *\n"
        + " from emp\n"
        + " group by ename, ^sum(sal) over (partition by deptno\n"
        + "    order by empno\n"
        + "    rows 3 preceding)^ + 10\n"
        + "order by deptno")
        .fails("Windowed aggregate expression is illegal in GROUP BY clause");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select *\n"
        + " from emp\n"
        + " join dept on emp.deptno = dept.deptno\n"
        + " and ^sum(sal) over (partition by emp.deptno\n"
        + "    order by empno\n"
        + "    rows 3 preceding)^ = dept.deptno + 40\n"
        + "order by deptno")
        .fails("Windowed aggregate expression is illegal in ON clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // rule 3, a)
    winSql("select sal from emp\n"
        + "order by sum(sal) over (partition by deptno order by deptno)")
        .ok();  // 断言验证：期望SQL验证通过

    // scope reference

    // rule 4,
    // valid window functions
    winExp("sum(sal)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testWindowFunctions2() {  // 测试方法：testWindowFunctions2
    List<String> defined =
        asList("CUME_DIST", "DENSE_RANK", "PERCENT_RANK", "RANK",
            "ROW_NUMBER");
    if (Bug.TODO_FIXED) {
      sql("select rank() over (order by deptno) from emp")  // 创建SQL测试：解析并验证SQL语句
          .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    }
    winSql("select rank() over w from emp\n"
        + "window w as ^(partition by sal)^, w2 as (w order by deptno)")
        .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select rank() over w2 from emp\n"
        + "window w as (partition by sal), w2 as (w order by deptno)").ok();  // 断言验证：期望SQL验证通过

    // row_number function
    winExp("row_number() over (order by deptno)").ok();  // 断言验证：期望SQL验证通过
    winExp("row_number() over (partition by deptno)").ok();  // 断言验证：期望SQL验证通过
    winExp("row_number() over ()").ok();  // 断言验证：期望SQL验证通过
    winExp("row_number() over (order by deptno ^rows^ 2 preceding)")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
    winExp("row_number() over (order by deptno ^range^ 2 preceding)")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息

    // rank function type
    if (defined.contains("DENSE_RANK")) {
      winExp("^dense_rank()^")
          .fails("OVER clause is necessary for window functions");  // 断言验证：期望SQL验证失败并检查错误消息
    } else {
      checkWinFuncExpWithWinClause("^dense_rank()^",
          "Function 'DENSE_RANK\\(\\)' is not defined");
    }
    winExp("rank() over (order by empno)").ok();  // 断言验证：期望SQL验证通过
    winExp("percent_rank() over (order by empno)").ok();  // 断言验证：期望SQL验证通过
    winExp("cume_dist() over (order by empno)").ok();  // 断言验证：期望SQL验证通过
    winExp("nth_value(sal, 2) over (order by empno)").ok();  // 断言验证：期望SQL验证通过

    // rule 6a
    // ORDER BY required with RANK & DENSE_RANK
    winSql("select rank() over ^(partition by deptno)^ from emp")
        .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select dense_rank() over ^(partition by deptno)^ from emp ")
        .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select rank() over w from emp window w as ^(partition by deptno)^")
        .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select dense_rank() over w from emp\n"
        + "window w as ^(partition by deptno)^")
        .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息

    // rule 6b
    // Framing not allowed with RANK & DENSE_RANK functions
    // window framing defined in window clause
    winSql("select rank() over w from emp\n"
        + "window w as (order by empno ^rows^ 2 preceding )")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select dense_rank() over w from emp\n"
        + "window w as (order by empno ^rows^ 2 preceding)")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select lag(deptno,1) over w from emp\n"
        + "window w as (order by empno ^rows^ 2 preceding)")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select lead(deptno,1) over w from emp\n"
        + "window w as (order by empno ^rows^ 2 preceding)")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
    if (defined.contains("PERCENT_RANK")) {
      winSql("select percent_rank() over w from emp\n"
          + "window w as (order by empno)")
          .ok();  // 断言验证：期望SQL验证通过
      winSql("select percent_rank() over w from emp\n"
          + "window w as (order by empno ^rows^ 2 preceding)")
          .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
      winSql("select percent_rank() over w from emp\n"
          + "window w as ^(partition by empno)^")
          .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    } else {
      checkWinFuncExpWithWinClause("^percent_rank()^",
          "Function 'PERCENT_RANK\\(\\)' is not defined");
    }
    if (defined.contains("CUME_DIST")) {
      winSql("select cume_dist() over w from emp\n"
          + "window w as ^(rows 2 preceding)^")
          .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
      winSql("select cume_dist() over w from emp\n"
          + "window w as (order by empno ^rows^ 2 preceding)")
          .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
      winSql("select cume_dist() over w from emp window w as (order by empno)")
          .ok();  // 断言验证：期望SQL验证通过
      winSql("select cume_dist() over (order by empno) from emp ").ok();  // 断言验证：期望SQL验证通过
    } else {
      checkWinFuncExpWithWinClause("^cume_dist()^",
          "Function 'CUME_DIST\\(\\)' is not defined");
    }
    // window framing defined in in-line window
    winSql("select rank() over (order by empno ^range^ 2 preceding ) from emp ")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select dense_rank() over (order by empno ^rows^ 2 preceding ) from emp ")
        .fails(ROW_RANGE_NOT_ALLOWED_WITH_RANK);  // 断言验证：期望SQL验证失败并检查错误消息
    if (defined.contains("PERCENT_RANK")) {
      winSql("select percent_rank() over (order by empno) from emp").ok();  // 断言验证：期望SQL验证通过
    }

    // invalid column reference
    checkWinFuncExpWithWinClause("sum(^invalidColumn^)",
        "Column 'INVALIDCOLUMN' not found in any table");

    // invalid window functions
    checkWinFuncExpWithWinClause("^invalidFun(sal)^",
        "No match found for function signature INVALIDFUN\\(<NUMERIC>\\)");

    // 6.10 rule 10. no distinct allowed aggregate function
    // Fails in parser.
    // checkWinFuncExpWithWinClause(" sum(distinct sal) over w ", null);

    // 7.11 rule 10c
    winSql("select sum(sal) over (w partition by ^deptno^)\n"
        + " from emp window w as (order by empno rows 2 preceding )")
        .fails("PARTITION BY not allowed with existing window reference");  // 断言验证：期望SQL验证失败并检查错误消息

    // 7.11 rule 10d
    winSql("select sum(sal) over (w order by ^empno^)\n"
        + " from emp window w as (order by empno rows 2 preceding )")
        .fails("ORDER BY not allowed in both base and referenced windows");  // 断言验证：期望SQL验证失败并检查错误消息

    // 7.11 rule 10e
    winSql("select sum(sal) over (w)\n"
        + " from emp window w as (order by empno ^rows^ 2 preceding )")
        .fails("Referenced window cannot have framing declarations");  // 断言验证：期望SQL验证失败并检查错误消息

    // Empty window is OK for functions that don't require ordering.
    winSql("select sum(sal) over () from emp").ok();  // 断言验证：期望SQL验证通过
    winSql("select sum(sal) over w from emp window w as ()").ok();  // 断言验证：期望SQL验证通过
    winSql("select count(*) over () from emp").ok();  // 断言验证：期望SQL验证通过
    winSql("select count(*) over w from emp window w as ()").ok();  // 断言验证：期望SQL验证通过
    winSql("select rank() over ^()^ from emp")
        .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    winSql("select rank() over w from emp window w as ^()^")
        .fails(RANK_REQUIRES_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6382">[CALCITE-6382]
   * Type inference for SqlLeadLagAggFunction is incorrect</a>. */
  @Test void testWindowLagInference() {  // 测试方法：testWindowLagInference
    sql("select lead(sal, 4, 0.5) over (w)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp window w as (order by empno)")
        .type("RecordType(DECIMAL(11, 1) NOT NULL EXPR$0) NOT NULL");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-883">[CALCITE-883]
   * Give error if the aggregate function don't support null treatment</a>. */
  @Test void testWindowFunctionsIgnoreNulls() {  // 测试方法：testWindowFunctionsIgnoreNulls
    winSql("select lead(sal, 4) over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select lead(sal, 4) IGNORE NULLS over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select lag(sal, 4) over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select lag(sal, 4) IGNORE NULLS over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select first_value(sal) over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select first_value(sal) IGNORE NULLS over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select last_value(sal) over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select last_value(sal) IGNORE NULLS over (w)\n"
        + " from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select ^sum(sal)^ IGNORE NULLS over (w)\n"
        + " from emp window w as (order by empno)")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'SUM'");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^count(sal)^ IGNORE NULLS over (w)\n"
        + " from emp window w as (order by empno)")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'COUNT'");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^avg(sal)^ IGNORE NULLS\n"
        + " from emp")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'AVG'");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^abs(sal)^ IGNORE NULLS\n"
        + " from emp")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'ABS'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-883">[CALCITE-883]
   * Give error if the aggregate function don't support null treatment</a>. */
  @Test void testWindowFunctionsRespectNulls() {  // 测试方法：testWindowFunctionsRespectNulls
    winSql("select lead(sal, 4) over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select lead(sal, 4) RESPECT NULLS over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select lag(sal, 4) over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select lag(sal, 4) RESPECT NULLS over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select first_value(sal) over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select first_value(sal) RESPECT NULLS over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select last_value(sal) over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select last_value(sal) RESPECT NULLS over (w)\n"
        + "from emp window w as (order by empno)").ok();  // 断言验证：期望SQL验证通过

    winSql("select ^sum(sal)^ RESPECT NULLS over (w)\n"
        + "from emp window w as (order by empno)")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'SUM'");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^count(sal)^ RESPECT NULLS over (w)\n"
        + "from emp window w as (order by empno)")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'COUNT'");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^avg(sal)^ RESPECT NULLS\n"
        + "from emp")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'AVG'");  // 断言验证：期望SQL验证失败并检查错误消息

    winSql("select ^abs(sal)^ RESPECT NULLS\n"
        + "from emp")
        .fails("Cannot specify IGNORE NULLS or RESPECT NULLS following 'ABS'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1954">[CALCITE-1954]
   * Column from outer join should be null, whether or not it is aliased</a>. */
  @Test void testLeftOuterJoinWithAlias() {  // 测试方法：testLeftOuterJoinWithAlias
    final String query = "select *\n"
        + "from (select row_number() over (order by sal) from emp) as emp1(r1)\n"
        + "left outer join\n"
        + "(select  dense_rank() over(order by sal) from emp) as emp2(r2)\n"
        + "on (emp1.r1 = emp2.r2)";
    // In this case, R2 is nullable in the join since we have a left outer join.
    final String type = "RecordType(BIGINT NOT NULL R1, BIGINT R2) NOT NULL";
    sql(query).type(type);

    // Similar query, without "AS t(c)"
    final String query2 = "select *\n"
        + "from (select row_number() over (order by sal) as r1 from emp) as emp1\n"
        + "left outer join\n"
        + "(select dense_rank() over(order by sal) as r2 from emp) as emp2\n"
        + "on (emp1.r1 = emp2.r2)";
    sql(query2).type(type);

    // Similar query, without "AS t"
    final String query3 = "select *\n"
        + "from (select row_number() over (order by sal) as r1 from emp)\n"
        + "left outer join\n"
        + "(select dense_rank() over(order by sal) as r2 from emp)\n"
        + "on r1 = r2";
    sql(query3).type(type);
  }

  @Test void testAsOfJoin() {  // 测试方法：testAsOfJoin
    final String type0 = "RecordType(INTEGER NOT NULL EMPNO, INTEGER NOT NULL DEPTNO) NOT NULL";
    final String sql0 = "select emp.empno, dept.deptno from emp asof join dept\n"
        + "match_condition emp.deptno <= dept.deptno\n"
        + "on emp.ename = dept.name";
    sql(sql0).type(type0);
    // ASOF join of a join result
    final String sql1 = "select emp.empno, D.deptno from emp asof join\n"
        + "(select L.* FROM dept AS L join dept AS R on L.deptno = R.deptno) as D\n"
        + "match_condition emp.deptno <= D.deptno\n"
        + "on emp.ename = D.name";
    sql(sql1).type(type0);

    // LEFT ASOF JOIN
    final String sql2 = "select emp.empno, dept.deptno from emp left asof join dept\n"
        + "match_condition emp.deptno <= dept.deptno\n"
        + "on emp.ename = dept.name";
    final String type2 = "RecordType(INTEGER NOT NULL EMPNO, INTEGER DEPTNO) NOT NULL";
    sql(sql2).type(type2);
    // LEFT ASOF join of a join result
    final String sql3 = "select emp.empno, D.deptno from emp left asof join\n"
        + "(select L.* FROM dept AS L join dept AS R on L.deptno = R.deptno) as D\n"
        + "match_condition emp.deptno <= D.deptno\n"
        + "on emp.ename = D.name";
    sql(sql3).type(type2);

    // No table specified for on condition
    final String sql4 = "select emp.empno, dept.deptno from emp asof join dept\n"
        + "match_condition emp.deptno <= dept.deptno\n"
        + "on ename = name";
    sql(sql4).type(type0);

    // No table specified for match condition
    final String sql5 = "select emp.empno, dno as deptno from emp asof join "
        + "(select deptno as dno, name from dept)\n"
        + "match_condition deptno <= dno\n"
        + "on ename = name";
    sql(sql5).type(type0);

    // Longer sequence of comparisons
    final String sql6 = "select emp.empno, dept.deptno from emp asof join dept\n"
        + "match_condition emp.deptno <= dept.deptno\n"
        + "on emp.ename = dept.name AND emp.deptno = dept.deptno AND emp.job = dept.name";
    sql(sql6).type(type0);

    // 2 Test cases for https://issues.apache.org/jira/browse/CALCITE-6641
    // Compiling programs with ASOF joins can report obscure errors
    final String type7 = "RecordType(INTEGER NOT NULL EMPNO, BIGINT NOT NULL DEPTNO) NOT NULL";
    // ASOF involving casts
    final String sql7 = "select emp.empno, dno as deptno from emp asof join "
        + "(select CAST(deptno AS BIGINT) as dno, name from dept)\n"
        + "match_condition deptno <= dno\n"
        + "on ename = name";
    sql(sql7).type(type7);

    // ASOF involving casts
    final String sql8 = "select emp.empno, dno as deptno from emp asof join "
        + "(select CAST(deptno AS BIGINT) as dno, name from dept)\n"
        + "match_condition ename <= name\n"
        + "on deptno = dno";
    sql(sql8).type(type7);

    // Failure cases
    // match condition is not an inequality test
    sql("select emp.empno from emp asof join dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "match_condition ^emp.deptno IN (1, 2)^\n"
        + "on emp.ename = dept.name")
        .fails(  // 断言验证：期望SQL验证失败并检查错误消息
            "ASOF JOIN MATCH_CONDITION must be a comparison between columns from the two inputs");
    // match condition does not compare columns from both tables
    sql("select emp.empno from emp asof join dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "match_condition ^emp.deptno < 12^\n"
        + "on emp.ename = dept.name")
        .fails(  // 断言验证：期望SQL验证失败并检查错误消息
            "ASOF JOIN MATCH_CONDITION must be a comparison between columns from the two inputs");
    // comparison is not a conjunction of equality tests
    sql("select emp.empno from emp asof join dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "match_condition emp.deptno < dept.deptno\n"
        + "on ^emp.ename < 'foo'^")
        .fails("ASOF JOIN condition must be a conjunction of equality comparisons");  // 断言验证：期望SQL验证失败并检查错误消息
    // comparison contains an equality test that does not check both tables joined
    sql("select emp.empno from emp asof join dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "match_condition emp.deptno < dept.deptno\n"
        + "on ^emp.ename = 'foo'^")
        .fails("ASOF JOIN condition must be a conjunction of equality comparisons");  // 断言验证：期望SQL验证失败并检查错误消息
    // comparison contains is not a conjunction
    sql("select emp.empno from emp asof join dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "match_condition emp.deptno < dept.deptno\n"
        + "on ^emp.ename = dept.name OR emp.deptno = dept.deptno^")
        .fails("ASOF JOIN condition must be a conjunction of equality comparisons");  // 断言验证：期望SQL验证失败并检查错误消息
    // comparison is not a conjunction
    sql("select * from (VALUES(true, false)) AS T0(b0, b1)\n"  // 创建SQL测试：解析并验证SQL语句
        + "asof join (VALUES(false, false)) AS T1(b0, b1)\n"
        + "match_condition T0.b0 < T1.b0\n"
        + "on ^T0.b1 AND T1.b1^")
        .fails("ASOF JOIN condition must be a conjunction of equality comparisons");  // 断言验证：期望SQL验证失败并检查错误消息
    // Condition contains a cast that is not applied to a column
    sql("select * from (VALUES(true, false)) AS T0(b0, b1)\n"  // 创建SQL测试：解析并验证SQL语句
        + "asof join (VALUES(false, 1)) AS T1(b0, b1)\n"
        + "match_condition T0.b0 < T1.b0\n"
        + "on ^T0.b1 = CAST(T1.b1 + 1 AS BOOLEAN)^")
        .fails("ASOF JOIN condition must be a conjunction of equality comparisons");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInvalidWindowFunctionWithGroupBy() {  // 测试方法：testInvalidWindowFunctionWithGroupBy
    sql("select max(^empno^) over () from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select max(deptno) over (partition by ^empno^) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select rank() over (order by ^empno^) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInlineWinDef() {  // 测试方法：testInlineWinDef
    // the <window specification> used by windowed agg functions is
    // fully defined in SQL 03 Std. section 7.1 <window clause>
    sql("select sum(sal) over (partition by deptno order by empno)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp order by empno").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) OVER ("
        + "partition by deptno "
        + "order by empno "
        + "rows 2 preceding )").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) OVER ("
        + "order by 1 "
        + "rows 2 preceding )").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) OVER ("
        + "order by 'b' "
        + "rows 2 preceding )").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over ("
        + "partition by deptno "
        + "order by 1+1 rows 26 preceding)").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over (order by deptno rows unbounded preceding)").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over (order by deptno rows current row)").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over ^("
        + "order by deptno "
        + "rows between unbounded preceding and unbounded following)^").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over ^("
        + "order by deptno "
        + "rows between CURRENT ROW and unbounded following)^").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "rows between unbounded preceding and CURRENT ROW)").ok();  // 断言验证：期望SQL验证通过

    // logical current row/current row
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "rows between CURRENT ROW and CURRENT ROW)").ok();  // 断言验证：期望SQL验证通过

    // physical current row/current row
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "range between CURRENT ROW and CURRENT ROW)").ok();  // 断言验证：期望SQL验证通过

    winExp2("sum(sal) over ("
        + "order by deptno "
        + "rows between 2 preceding and CURRENT ROW)").ok();  // 断言验证：期望SQL验证通过
    winExp("sum(sal) OVER (w "
        + "rows 2 preceding )").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over (order by deptno range 2.0 preceding)").ok();  // 断言验证：期望SQL验证通过

    // compound order by with literal bounds
    winExp2("sum(sal) over "
            + "(order by sal,deptno "
            + "range between current row and unbounded following)").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over "
            + "(order by sal,deptno "
            + "range between current row and current row)").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over "
            + "(order by sal,deptno "
            + "range between unbounded preceding and current row)").ok();  // 断言验证：期望SQL验证通过
    winExp2("sum(sal) over "
            + "(order by sal,deptno "
            + "range between unbounded preceding and unbounded following)").ok();  // 断言验证：期望SQL验证通过

    // Range without order by with only literal bounds
    winExp2("sum(sal) over "
        + "(partition by sal,deptno "
        + "range between unbounded preceding and unbounded following)").ok();  // 断言验证：期望SQL验证通过

    // RANGE with non-difference type order by and literal bounds
    winExp2("sum(sal) over "
        + "(order by ename "
        + "range between unbounded preceding and current row)").ok();  // 断言验证：期望SQL验证通过

    // Range without order by with only preceding / following should fail
    winExp2("sum(sal) over "
        + "^(partition by sal "
        + "range between 3 preceding and unbounded following)^")
        .fails("Window specification must contain an ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over "
        + "^(partition by sal "
        + "range between current row and 3 following)^")
        .fails("Window specification must contain an ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // Failure mode tests
    winExp2("sum(sal) over (order by deptno "
        + "rows between ^UNBOUNDED FOLLOWING^ and unbounded preceding)")
        .fails("UNBOUNDED FOLLOWING cannot be specified for the lower frame boundary");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "rows between 2 preceding and ^UNBOUNDED PRECEDING^)")
        .fails("UNBOUNDED PRECEDING cannot be specified for the upper frame boundary");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "rows between CURRENT ROW and ^2 preceding^)")
        .fails("Upper frame boundary cannot be PRECEDING when lower boundary is CURRENT ROW");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "rows between 2 following and ^CURRENT ROW^)")
        .fails("Upper frame boundary cannot be CURRENT ROW when lower boundary is FOLLOWING");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "rows between 2 following and ^2 preceding^)")
        .fails("Upper frame boundary cannot be PRECEDING when lower boundary is FOLLOWING");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ("
        + "order by deptno "
        + "RANGE BETWEEN ^INTERVAL '1' SECOND^ PRECEDING AND INTERVAL '1' SECOND FOLLOWING)")
        .fails("Data Type mismatch between ORDER BY and RANGE clause");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ("
        + "order by empno "
        + "RANGE BETWEEN ^INTERVAL '1' SECOND^ PRECEDING AND INTERVAL '1' SECOND FOLLOWING)")
        .fails("Data Type mismatch between ORDER BY and RANGE clause");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over (order by deptno, empno ^range^ 2 preceding)")
        .fails("RANGE clause cannot be used with compound ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ^(partition by deptno range 5 preceding)^")
        .fails("Window specification must contain an ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) over ^w1^")
        .fails("Window 'W1' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    winExp2("sum(sal) OVER (^w1^ "
        + "partition by deptno "
        + "order by empno "
        + "rows 2 preceding )")
        .fails("Window 'W1' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testPartitionByExpr() {  // 测试方法：testPartitionByExpr
    winExp2("sum(sal) over (partition by empno + deptno order by empno range 5 preceding)")
        .ok();  // 断言验证：期望SQL验证通过

    winSql("select "
        + "sum(sal) over (partition by ^empno + ename^ order by empno range 5 preceding)"
        + " from emp")
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply '\\+' to arguments of type '<INTEGER> \\+ <VARCHAR\\(20\\)>'.*");  // 断言验证：期望SQL验证失败并检查错误消息

    winExp2("sum(sal) over (partition by empno + ename order by empno range 5 preceding)");
  }

  @Test void testWindowClause() {  // 测试方法：testWindowClause
    // -----------------------------------
    // --   positive testings           --
    // -----------------------------------
    // correct syntax:
    winExp("sum(sal) as sumsal").ok();  // 断言验证：期望SQL验证通过
    win("window w as (partition by sal order by deptno rows 2 preceding)")
        .ok();  // 断言验证：期望SQL验证通过

    // define window on an existing window
    win("window w as (order by sal), w1 as (w)").ok();  // 断言验证：期望SQL验证通过

    // -----------------------------------
    // --   negative testings           --
    // -----------------------------------
    // Test fails in parser
    // checkWinClauseExp("window foo.w as (range 100 preceding) "+
    //    "Window name must be a simple identifier\");

    // rule 11
    // a)
    // missing window order clause.
    win("window w as ^(range 100 preceding)^")
        .fails("Window specification must contain an ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // order by number
    win("window w as (order by sal range 100 preceding)").ok();  // 断言验证：期望SQL验证通过

    // order by date
    win("window w as (order by hiredate range ^100^ preceding)")
        .fails("Data Type mismatch between ORDER BY and RANGE clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // order by string, should fail
    win("window w as (order by ename range ^100^ preceding)")
        .fails("Data type of ORDER BY prohibits use of RANGE clause");  // 断言验证：期望SQL验证失败并检查错误消息
    // todo: interval test ???

    // b)
    // valid
    win("window w as (rows 2 preceding)").ok();  // 断言验证：期望SQL验证通过

    // invalid tests exact numeric for the unsigned value specification The
    // following two test fail as they should but in the parser: JR not
    // anymore now the validator kicks out
    win("window w as (rows ^-2.5^ preceding)")
        .fails("ROWS value must be a non-negative integral constant");  // 断言验证：期望SQL验证失败并检查错误消息
    win("window w as (rows ^-2^ preceding)")
        .fails("ROWS value must be a non-negative integral constant");  // 断言验证：期望SQL验证失败并检查错误消息

    // This test should fail as per 03 Std. but we pass it and plan
    // to apply the FLOOR function before window processing
    win("window w as (rows ^2.5^ preceding)")
        .fails("ROWS value must be a non-negative integral constant");  // 断言验证：期望SQL验证失败并检查错误消息

    // CALCITE-5931 - Allow integers like 1.00 in window frame
    win("window w as (rows 2.00 preceding)").ok();  // 断言验证：期望SQL验证通过

    // -----------------------------------
    // --   negative testings           --
    // -----------------------------------
    // reference undefined xyz column
    win("window w as (partition by ^xyz^)")
        .fails("Column 'XYZ' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // window definition is empty when applied to unsorted table
    win("window w as ^( /* boo! */  )^").ok();  // 断言验证：期望SQL验证通过

    // duplicate window name
    win("window w as (order by empno), ^w^ as (order by empno)")
        .fails("Duplicate window names not allowed");  // 断言验证：期望SQL验证失败并检查错误消息
    win("window win1 as (order by empno), ^win1^ as (order by empno)")
        .fails("Duplicate window names not allowed");  // 断言验证：期望SQL验证失败并检查错误消息

    // syntax rule 6
    sql("select min(sal) over (order by deptno) from emp group by deptno,sal")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select min(sal) over (order by ^deptno^) from emp group by sal")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select min(sal) over\n"  // 创建SQL测试：解析并验证SQL语句
        + "(partition by comm order by deptno) from emp group by deptno,sal,comm")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select min(sal) over\n"  // 创建SQL测试：解析并验证SQL语句
        + "(partition by ^comm^ order by deptno) from emp group by deptno,sal")
        .fails("Expression 'COMM' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // syntax rule 7
    win("window w as ^(order by rank() over (order by sal))^")
        .fails("ORDER BY expression should not contain OVER clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // ------------------------------------
    // ---- window frame between tests ----
    // ------------------------------------
    // bound 1 shall not specify UNBOUNDED FOLLOWING
    win("window w as (rows between ^unbounded following^ and 5 following)")
        .fails("UNBOUNDED FOLLOWING cannot be specified for the lower frame boundary");  // 断言验证：期望SQL验证失败并检查错误消息

    // bound 2 shall not specify UNBOUNDED PRECEDING
    win("window w as ("
        + "order by deptno "
        + "rows between 2 preceding and ^UNBOUNDED PRECEDING^)")
        .fails("UNBOUNDED PRECEDING cannot be specified for the upper frame boundary");  // 断言验证：期望SQL验证失败并检查错误消息
    win("window w as ("
        + "order by deptno "
        + "rows between 2 following and ^2 preceding^)")
        .fails("Upper frame boundary cannot be PRECEDING when lower boundary is FOLLOWING");  // 断言验证：期望SQL验证失败并检查错误消息
    win("window w as ("
        + "order by deptno "
        + "rows between CURRENT ROW and ^2 preceding^)")
        .fails("Upper frame boundary cannot be PRECEDING when lower boundary is CURRENT ROW");  // 断言验证：期望SQL验证失败并检查错误消息
    win("window w as ("
        + "order by deptno "
        + "rows between 2 following and ^CURRENT ROW^)")
        .fails("Upper frame boundary cannot be CURRENT ROW when lower boundary is FOLLOWING");  // 断言验证：期望SQL验证失败并检查错误消息

    // Sql '03 rule 10 c) assertExceptionIsThrown("select deptno as d, sal
    // as s from emp window w as (partition by deptno order by sal), w2 as
    // (w partition by deptno)", null); checkWinClauseExp("window w as
    // (partition by sal order by deptno), w2 as (w partition by sal)",
    // null); d) valid because existing window does not have an ORDER BY
    // clause
    win("window w as (w2 range 2 preceding), w2 as (order by sal)").ok();  // 断言验证：期望SQL验证通过
    win("window w as ^(partition by sal)^, w2 as (w order by deptno)").ok();  // 断言验证：期望SQL验证通过
    win("window w as (w2 partition by ^sal^), w2 as (order by deptno)")
        .fails("PARTITION BY not allowed with existing window reference");  // 断言验证：期望SQL验证失败并检查错误消息
    win("window w as (partition by sal order by deptno), w2 as (w order by ^deptno^)")
        .fails("ORDER BY not allowed in both base and referenced windows");  // 断言验证：期望SQL验证失败并检查错误消息

    // e)
    win("window w as (w2 order by deptno), w2 as (^range^ 100 preceding)")
        .fails("Referenced window cannot have framing declarations");  // 断言验证：期望SQL验证失败并检查错误消息

    // rule 12, todo: test scope of window assertExceptionIsThrown("select
    // deptno as d from emp window d as (partition by deptno)", null);

    // rule 13
    win("window w as (order by sal)").ok();  // 断言验证：期望SQL验证通过
    win("window w as (order by ^non_exist_col^)")
        .fails("Column 'NON_EXIST_COL' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    win("window w as (partition by ^non_exist_col^ order by sal)")
        .fails("Column 'NON_EXIST_COL' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testWindowClause2() {  // 测试方法：testWindowClause2
    // 7.10 syntax rule 2 <new window name> NWN1 shall not be contained in
    // the scope of another <new window name> NWN2 such that NWN1 and NWN2
    // are equivalent.
    win("window\n"
        + "w  as (partition by deptno order by empno rows 2 preceding),\n"
        + "w2 as ^(partition by deptno order by empno rows 2 preceding)^\n")
        .fails("Duplicate window specification not allowed in the same window clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testWindowClauseWithSubQuery() {  // 测试方法：testWindowClauseWithSubQuery
    sql("select * from\n"  // 创建SQL测试：解析并验证SQL语句
        + "( select sum(empno) over w, sum(deptno) over w from emp\n"
        + "window w as (order by hiredate range interval '1' minute preceding))").ok();  // 断言验证：期望SQL验证通过

    sql("select * from\n"  // 创建SQL测试：解析并验证SQL语句
        + "( select sum(empno) over w, sum(deptno) over w, hiredate from emp)\n"
        + "window w as (order by hiredate range interval '1' minute preceding)").ok();  // 断言验证：期望SQL验证通过

    sql("select * from\n"  // 创建SQL测试：解析并验证SQL语句
        + "( select sum(empno) over w, sum(deptno) over w from emp)\n"
        + "window w as (order by ^hiredate^ range interval '1' minute preceding)")
        .fails("Column 'HIREDATE' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-754">[CALCITE-754]
   * Validator error when resolving OVER clause of JOIN query</a>. */
  @Test void testPartitionByColumnInJoinAlias() {  // 测试方法：testPartitionByColumnInJoinAlias
    sql("select sum(1) over(partition by t1.ename)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp t1, emp t2")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select sum(1) over(partition by emp.ename)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp, dept")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select sum(1) over(partition by ^deptno^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp, dept")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1535">[CALCITE-1535]
   * Give error if column referenced in ORDER BY is ambiguous</a>. */
  @Test void testOrderByColumn() {  // 测试方法：testOrderByColumn
    sql("select emp.deptno from emp, dept order by emp.deptno")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    // Not ambiguous. There are two columns which could be referenced as
    // "deptno", but the one in the SELECT clause takes priority.
    sql("select emp.deptno from emp, dept order by deptno")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select emp.deptno as deptno from emp, dept order by deptno")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select emp.empno as deptno from emp, dept order by deptno")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select emp.deptno as n, dept.deptno as n from emp, dept order by ^n^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'N' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select emp.empno as deptno, dept.deptno from emp, dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by ^deptno^")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select emp.empno as deptno, dept.deptno from emp, dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by emp.deptno")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select emp.empno as deptno, dept.deptno from emp, dept order by 1, 2")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select empno as \"deptno\", deptno from emp order by deptno")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select empno as \"deptno\", deptno from emp order by \"deptno\"")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testWindowNegative() {  // 测试方法：testWindowNegative
    // Do not fail when window has negative size. Allow
    final String negSize = null;
    checkNegWindow("rows between 2 preceding and 4 preceding", negSize);
    checkNegWindow("rows between 2 preceding and 3 preceding", negSize);
    checkNegWindow("rows between 2 preceding and 2 preceding", null);
    checkNegWindow("rows between unbounded preceding and current row",
        null);
    // Unbounded following IS supported
    final String unboundedFollowing =
        null;
    checkNegWindow("rows between unbounded preceding and unbounded following",
        unboundedFollowing);
    checkNegWindow("rows between current row and unbounded following",
        unboundedFollowing);
    checkNegWindow("rows between current row and 2 following", null);
    checkNegWindow("range between 2 preceding and 2 following", null);
    checkNegWindow("range between 2 preceding and -2 preceding", null);
    checkNegWindow("range between 4 following and 3 following", negSize);
    checkNegWindow("range between 4 following and 5 following", null);
    checkNegWindow("rows between 1 following and 0 following", negSize);
    checkNegWindow("rows between 0 following and 0 following", null);
  }

  private void checkNegWindow(String s, String msg) {
    String sql =
        "select sum(deptno) over ^(order by empno "
            + s
            + ")^ from emp";
    sql(sql).failsIf(msg != null, msg);
  }

  @Test void testWindowPartial() {  // 测试方法：testWindowPartial
    sql("select sum(deptno) over (\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by deptno, empno rows 2 preceding disallow partial)\n"
        + "from emp").ok();  // 断言验证：期望SQL验证通过

    // cannot do partial over logical window
    sql("select sum(deptno) over (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  partition by deptno\n"
        + "  order by empno\n"
        + "  range between 2 preceding and 3 following\n"
        + "  ^disallow partial^)\n"
        + "from emp")
        .fails("Cannot use DISALLOW PARTIAL with window based on RANGE");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testOneWinFunc() {  // 测试方法：testOneWinFunc
    win("window w as (partition by sal order by deptno rows 2 preceding)")
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testNameResolutionInValuesClause() {  // 测试方法：testNameResolutionInValuesClause
    final String emps =
        "(select 1 as empno, 'x' as name, 10 as deptno, 'M' as gender, 'San Francisco' as city, 30 as empid, 25 as age from (values (1)))";
    final String depts =
        "(select 10 as deptno, 'Sales' as name from (values (1)))";

    sql("select * from " + emps + " join " + depts + "\n"  // 创建SQL测试：解析并验证SQL语句
        + " on ^emps^.deptno = deptno")
        .fails("Table 'EMPS' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // this is ok
    sql("select * from " + emps + " as e\n"  // 创建SQL测试：解析并验证SQL语句
        + " join " + depts + " as d\n"
        + " on e.deptno = d.deptno").ok();  // 断言验证：期望SQL验证通过

    // fail: ambiguous column in WHERE
    sql("select * from " + emps + " as emps,\n"  // 创建SQL测试：解析并验证SQL语句
        + " " + depts + "\n"
        + "where ^deptno^ > 5")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // fail: ambiguous column reference in ON clause
    sql("select * from " + emps + " as e\n"  // 创建SQL测试：解析并验证SQL语句
        + " join " + depts + " as d\n"
        + " on e.deptno = ^deptno^")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // ok: column 'age' is unambiguous
    sql("select * from " + emps + " as e\n"  // 创建SQL测试：解析并验证SQL语句
        + " join " + depts + " as d\n"
        + " on e.deptno = age").ok();  // 断言验证：期望SQL验证通过

    // ok: reference to derived column
    sql("select * from " + depts + "\n"  // 创建SQL测试：解析并验证SQL语句
        + " join (select mod(age, 30) as agemod from " + emps + ")\n"
        + "on deptno = agemod").ok();  // 断言验证：期望SQL验证通过

    // fail: deptno is ambiguous
    sql("select name from " + depts + "\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (select mod(age, 30) as agemod, deptno from " + emps + ")\n"
        + "on ^deptno^ = agemod")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // fail: lateral reference
    sql("select * from " + emps + " as e,\n"  // 创建SQL测试：解析并验证SQL语句
        + " (select 1, ^e^.deptno from (values(true))) as d")
        .fails("Table 'E' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // fail: deptno is ambiguous
    sql("select ^deptno^, name from " + depts + "as d\n"  // 创建SQL测试：解析并验证SQL语句
        + "join " + emps +  "as e\n"
        + "on d.deptno = e.deptno")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testNestedFrom() {  // 测试方法：testNestedFrom
    sql("values (true)")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    sql("select * from (values(true))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    sql("select * from (select * from (values(true)))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    sql("select * from (select * from (select * from (values(true))))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    sql("select * from ("  // 创建SQL测试：解析并验证SQL语句
        + "  select * from ("
        + "    select * from (values(true))"
        + "    union"
        + "    select * from (values (false)))"
        + "  except"
        + "  select * from (values(true)))")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testAmbiguousColumn() {  // 测试方法：testAmbiguousColumn
    sql("select * from emp join dept\n"  // 创建SQL测试：解析并验证SQL语句
        + " on emp.deptno = ^deptno^")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // this is ok
    sql("select * from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + " join dept as d\n"
        + " on e.deptno = d.deptno").ok();  // 断言验证：期望SQL验证通过

    // fail: ambiguous column in WHERE
    sql("select * from emp as emps, dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "where ^deptno^ > 5")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // fail: alias 'd' obscures original table name 'dept'
    sql("select * from emp as emps, dept as d\n"  // 创建SQL测试：解析并验证SQL语句
        + "where ^dept^.deptno > 5")
        .fails("Table 'DEPT' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // fail: ambiguous column reference in ON clause
    sql("select * from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + " join dept as d\n"
        + " on e.deptno = ^deptno^")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // ok: column 'comm' is unambiguous
    sql("select * from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + " join dept as d\n"
        + " on e.deptno = comm").ok();  // 断言验证：期望SQL验证通过

    // ok: reference to derived column
    sql("select * from dept\n"  // 创建SQL测试：解析并验证SQL语句
        + " join (select mod(comm, 30) as commmod from emp)\n"
        + "on deptno = commmod").ok();  // 断言验证：期望SQL验证通过

    // fail: deptno is ambiguous
    sql("select name from dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (select mod(comm, 30) as commmod, deptno from emp)\n"
        + "on ^deptno^ = commmod")
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // fail: lateral reference
    sql("select * from emp as e,\n"  // 创建SQL测试：解析并验证SQL语句
        + " (select 1, ^e^.deptno from (values(true))) as d")
        .fails("Table 'E' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testExpandStar() {  // 测试方法：testExpandStar
    // dtbug 282 -- "select r.* from sales.depts" gives NPE.
    // dtbug 318 -- error location should be ^r^ not ^r.*^.
    sql("select ^r^.* from dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'R'");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select e.* from emp as e").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select emp.* from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    // Error message could be better (EMPNO does exist, but it's a column).
    sql("select ^empno^ .  * from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Not a record type. The '\\*' operator requires a record");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^emp.empno^ .  * from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Not a record type. The '\\*' operator requires a record");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-546">[CALCITE-546]
   * Allow table, column and field called '*'</a>.
   */
  @Test void testStarIdentifier() {  // 测试方法：testStarIdentifier
    sql("SELECT * FROM (VALUES (0, 0)) AS T(A, \"*\")")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL A, INTEGER NOT NULL *) NOT NULL");
  }

  @Test void testStarAliasFails() {  // 测试方法：testStarAliasFails
    sql("select emp.^*^ AS x from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown field '\\*'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testNonLocalStar() {  // 测试方法：testNonLocalStar
    // MySQL allows this, and now so do we
    sql("select * from emp e where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select e.* from dept where dept.deptno = e.deptno)")
        .type(EMP_RECORD_TYPE);
  }

  /**
   * Parser does not allow "*" in FROM clause.
   * Parser allows "*" in column expressions, but throws if they are outside
   * of the SELECT clause.
   *
   * @see #testStarIdentifier()
   */
  @Test void testStarInFromFails() {  // 测试方法：testStarInFromFails
    sql("select emp.empno AS x from sales^.^*")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s)Encountered \"\\. \\*\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from emp^.^*")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s)Encountered \"\\. \\*\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select emp.empno AS x from emp^.^*")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s)Encountered \"\\. \\*\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select emp.empno from emp where emp.^*^ is not null")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown field '\\*'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStarDotIdFails() {  // 测试方法：testStarDotIdFails
    // Fails in parser
    sql("select emp.^*^.\"EXPR$1\" from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Unknown field '\\*'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select emp.^*^.foo from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Unknown field '\\*'");  // 断言验证：期望SQL验证失败并检查错误消息
    // Parser does not allow star dot identifier.
    sql("select *^.^foo from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Encountered \".\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStarWithoutFromFails() {  // 测试方法：testStarWithoutFromFails
    final String selectStarRequiresAFromClause =
        "SELECT \\* requires a FROM clause";
    sql("select ^*^")  // 创建SQL测试：解析并验证SQL语句
        .fails(selectStarRequiresAFromClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from (select 2 as two)")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL TWO) NOT NULL");
    sql("select ^e^.*")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'E'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^*^, 2 as two")  // 创建SQL测试：解析并验证SQL语句
        .fails(selectStarRequiresAFromClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 2 as two, ^*^")  // 创建SQL测试：解析并验证SQL语句
        .fails(selectStarRequiresAFromClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 3 as three union select ^*^ union select 4 as four")  // 创建SQL测试：解析并验证SQL语句
        .fails(selectStarRequiresAFromClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sum(1) as someone, ^*^")  // 创建SQL测试：解析并验证SQL语句
        .fails(selectStarRequiresAFromClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select c from (select ^*^) as t(c)")  // 创建SQL测试：解析并验证SQL语句
        .fails(selectStarRequiresAFromClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 2 as two\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp as e\n"
        + "where exists (select e.*)")
        .type("RecordType(INTEGER NOT NULL TWO) NOT NULL");
  }

  @Test void testAsColumnList() {  // 测试方法：testAsColumnList
    sql("select d.a, b from dept as d(a, b)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select d.^deptno^ from dept as d(a, b)")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Column 'DEPTNO' not found in table 'D'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 1 from dept as d(^a, b, c^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*List of column aliases must have same degree as table; "  // 断言验证：期望SQL验证失败并检查错误消息
            + "table has 2 columns \\('DEPTNO', 'NAME'\\), "
            + "whereas alias list has 3 columns.*");
    sql("select * from dept as d(a, b)")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL A, VARCHAR(10) NOT NULL B) NOT NULL");
    sql("select * from (values ('a', 1), ('bc', 2)) t (a, b)")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(CHAR(2) NOT NULL A, INTEGER NOT NULL B) NOT NULL");
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6677">[CALCITE-6677]
   * HAVING clauses fail validation when type coercion is applied to GROUP BY clause</a>. */
  @Test void testCoercionCast() {  // 测试方法：testCoercionCast
    SqlValidatorFixture f =
        // Needed for the IF function
        fixture().withOperatorTable(operatorTableFor(SqlLibrary.BIG_QUERY));
    final String sql =
        "select if(EMP.empno <= CAST(18 AS DOUBLE), 'youth', 'adult') as adult_or_child\n"
        + "from EMP \n"
        + "GROUP BY if(EMP.empno <= CAST(18 AS DOUBLE), 'youth', 'adult')\n"
        + "HAVING if(EMP.empno <= CAST(18 AS DOUBLE), 'youth', 'adult')  = 'adult'";
    f.withSql(sql).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testMeasureRef() {  // 测试方法：testMeasureRef
    // A measure can be used in the SELECT clause of a GROUP BY query even
    // though it is not a GROUP BY key.
    SqlValidatorFixture f =
        fixture().withExtendedCatalog()
            .withOperatorTable(operatorTableFor(SqlLibrary.CALCITE));

    SqlValidatorFixture fNakedInsideOnly =
        f.withValidatorConfig(c ->
            c.withNakedMeasuresInAggregateQuery(true)
                .withNakedMeasuresInNonAggregateQuery(false));
    SqlValidatorFixture fNakedOutsideOnly =
        f.withValidatorConfig(c ->
            c.withNakedMeasuresInNonAggregateQuery(true)
                .withNakedMeasuresInAggregateQuery(false));
    SqlValidatorFixture fNoNakedMeasures =
        f.withValidatorConfig(c ->
            c.withNakedMeasuresInNonAggregateQuery(false)
                .withNakedMeasuresInAggregateQuery(false));

    final String measureIllegal =
        "Measure expressions can only occur within AGGREGATE function";
    final String measureIllegal2 =
        "Measure expressions can only occur within a GROUP BY query";

    final String sql0 = "select deptno, ^count_plus_100^\n"
        + "from empm\n"
        + "group by deptno";
    f.withSql(sql0)
        .isAggregate(is(true))
        .ok();  // 断言验证：期望SQL验证通过

    // Same SQL is invalid if naked measures are not enabled
    fNoNakedMeasures.withSql(sql0).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedOutsideOnly.withSql(sql0).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedInsideOnly.withSql(sql0).isAggregate(is(true)).ok();  // 断言验证：期望SQL验证通过

    // Similarly, with alias
    final String sql1b = "select deptno, ^count_plus_100^ as x\n"
        + "from empm\n"
        + "group by deptno";
    f.withSql(sql1b).isAggregate(is(true)).ok();  // 断言验证：期望SQL验证通过
    fNoNakedMeasures.withSql(sql1b).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedOutsideOnly.withSql(sql1b).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedInsideOnly.withSql(sql1b).isAggregate(is(true)).ok();  // 断言验证：期望SQL验证通过

    // Similarly, in an expression
    final String sql1c = "select deptno, deptno + ^count_plus_100^ * 2 as x\n"
        + "from empm\n"
        + "group by deptno";
    f.withSql(sql1c).isAggregate(is(true)).ok();  // 断言验证：期望SQL验证通过
    fNoNakedMeasures.withSql(sql1c).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedOutsideOnly.withSql(sql1c).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedInsideOnly.withSql(sql1c).isAggregate(is(true)).ok();  // 断言验证：期望SQL验证通过

    // Similarly, for a query that is an aggregate query because of another
    // aggregate function.
    final String sql1 = "select count(*), ^count_plus_100^\n"
        + "from empm";
    f.withSql(sql1).isAggregate(is(true)).ok();  // 断言验证：期望SQL验证通过
    fNoNakedMeasures.withSql(sql1).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedInsideOnly.withSql(sql1).isAggregate(is(true)).ok();  // 断言验证：期望SQL验证通过
    fNakedOutsideOnly.withSql(sql1).fails(measureIllegal);  // 断言验证：期望SQL验证失败并检查错误消息

    // A measure in a non-aggregate query.
    // Using a measure should not make it an aggregate query.
    // The type of the measure should be the result type of the COUNT aggregate
    // function (BIGINT), not type of the un-aggregated argument type (VARCHAR).
    final String sql2 = "select deptno, ^count_plus_100^, ename\n"
        + "from empm";
    f.withSql(sql2)
        .type("RecordType(INTEGER NOT NULL DEPTNO, "
            + "INTEGER NOT NULL COUNT_PLUS_100, "
            + "VARCHAR(20) NOT NULL ENAME) NOT NULL")
        .isAggregate(is(false));
    fNoNakedMeasures.withSql(sql2).fails(measureIllegal2);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedInsideOnly.withSql(sql2).fails(measureIllegal2);  // 断言验证：期望SQL验证失败并检查错误消息
    fNakedOutsideOnly.withSql(sql2).isAggregate(is(false)).ok();  // 断言验证：期望SQL验证通过

    // as above, wrapping the measure in AGGREGATE
    final String sql3 = "select deptno, aggregate(count_plus_100) as x, ename\n"
        + "from empm\n"
        + "group by deptno, ename";
    f.withSql(sql3)
        .type("RecordType(INTEGER NOT NULL DEPTNO, "
            + "INTEGER NOT NULL X, "
            + "VARCHAR(20) NOT NULL ENAME) NOT NULL");

    // you can apply the AGGREGATE function only to measures
    f.withSql("select deptno, aggregate(count_plus_100), ^aggregate(ename)^\n"
            + "from empm\n"
            + "group by deptno, ename")
        .fails("Argument to function 'AGGREGATE' must be a measure");  // 断言验证：期望SQL验证失败并检查错误消息

    f.withSql("select deptno, ^aggregate(count_plus_100 + 1)^\n"
            + "from empm\n"
            + "group by deptno, ename")
        .fails("Argument to function 'AGGREGATE' must be a measure");  // 断言验证：期望SQL验证失败并检查错误消息

    // A query with AGGREGATE is an aggregate query, even without GROUP BY,
    // and even if it is inside an expression.
    f.withSql("select aggregate(count_plus_100) + 1\n"
            + "from empm")
        .isAggregate(is(true));

    // Including a measure in a query does not make it an aggregate query
    f.withSql("select count_plus_100\n"
            + "from empm")
        .isAggregate(is(false));
  }

  @Test void testAsMeasure() {  // 测试方法：testAsMeasure
    // various kinds of measure expressions
    sql("select deptno, empno + 1 as measure e1 from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select *, empno + 1 as measure e1 from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    // an aggregate function in a measure does not make it an aggregate query
    sql("select *, sum(empno) as measure e1 from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select e1 from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select deptno, empno + 1 as measure e1 from emp)").ok();  // 断言验证：期望SQL验证通过
    sql("select e1 from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select *, sum(empno) as measure e1 from emp)").ok();  // 断言验证：期望SQL验证通过

    // Aggregate and DISTINCT queries may not contain measures.
    // (Maybe relax this restriction later?)
    final String message = "MEASURE not valid in aggregate or DISTINCT query";
    sql("select deptno, ^1 as measure e1^ from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sum(sal) as s, ^1 as measure e1^ from emp having count(*) > 0")  // 创建SQL测试：解析并验证SQL语句
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 2 + 3, count(*), ^1 as measure e1^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^1 as measure e1^ from emp group by ()")  // 创建SQL测试：解析并验证SQL语句
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select distinct deptno, ^1 as measure e1^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息

    // invalid column
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  ^nonExistent^ + 1 as d1,\n"
        + "  deptno + 3 as d3\n"
        + "from emp").fails("Column 'NONEXISTENT' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  ^nonExistent^ + 1 as measure d1,\n"
        + "  deptno + 3 as d3\n"
        + "from emp").fails("Column 'NONEXISTENT' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // measures may reference both measures and non-measure aliases
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as d1,\n"
        + "  d1 + 2 as measure d3\n"
        + "from emp").ok();  // 断言验证：期望SQL验证通过
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as d1,\n"
        + "  d1 + 2 as measure d3,\n"
        + "  d3 + 3 as measure d6\n"
        + "from emp").ok();  // 断言验证：期望SQL验证通过
    // forward references are ok
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  d3 + 3 as measure d6,\n"
        + "  d1 + 2 as measure d3,\n"
        + "  deptno + 1 as d1\n"
        + "from emp").ok();  // 断言验证：期望SQL验证通过
    // non-measures may not reference measures
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as measure d1,\n"
        + "  ^d1^ + 2 as d3\n"
        + "from emp").fails("Column 'D1' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // sub-query
    sql("select * from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select deptno,\n"
        + "    empno + 1 as measure e1,\n"
        + "    e1 + deptno as measure e2\n"
        + "  from emp)").ok();  // 断言验证：期望SQL验证通过
    // as previous, but references a non-measure
    sql("select * from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select deptno,\n"
        + "    empno + 1 as e1,\n"
        + "    e1 + deptno as measure e2\n"
        + "  from emp)").ok();  // 断言验证：期望SQL验证通过

    // non-measures don't even see measures - fall back to columns
    final String intVarcharType =
        "RecordType(INTEGER NOT NULL ENAME,"
            + " VARCHAR(20) NOT NULL N) NOT NULL";
    final String intVarcharmType =
        "RecordType(INTEGER NOT NULL ENAME,"
            + " VARCHAR(20) NOT NULL N) NOT NULL";
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as measure ename,\n"
        + "  ename as n\n"
        + "from emp")
        .type(intVarcharType)
        .assertMeasure(0, is(true))
        .assertMeasure(1, is(false));
    final String intIntType =
        "RecordType(INTEGER NOT NULL ENAME,"
            + " INTEGER NOT NULL N) NOT NULL";
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as measure ename,\n"
        + "  ename as measure n\n"
        + "from emp")
        .type(intIntType)
        .assertMeasure(0, is(true))
        .assertMeasure(1, is(true));
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as measure ename,\n"
        + "  min(ename) as measure n\n"
        + "from emp")
        .type(intIntType)
        .assertMeasure(0, is(true))
        .assertMeasure(1, is(true));
    // measure can reference column by qualifying with table alias
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as measure ename,\n"
        + "  emp.ename as measure n\n"
        + "from emp").type(intVarcharmType);
    sql("select\n"  // 创建SQL测试：解析并验证SQL语句
        + "  deptno + 1 as measure ename,\n"
        + "  min(emp.ename) as measure n\n"
        + "from emp").type(intVarcharmType);

    // without the 't.' qualifier, this would be an illegal cyclic reference
    sql("select t.uno as measure uno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select deptno, 1 as uno from emp) as t")
        .type("RecordType(INTEGER NOT NULL UNO) NOT NULL")
        .assertMeasure(0, is(true));

    // cyclic
    sql("select ^six^ - 5 as measure uno, 2 + uno as measure three,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  three * 2 as measure six\n"
        + "from emp").
        fails("Measure 'SIX' is cyclic; its definition depends on the "
            + "following measures: 'UNO', 'THREE', 'SIX'");
    sql("select 2 as measure two, ^uno^ as measure uno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp").
        fails("Measure 'UNO' is cyclic; its definition depends on the "
            + "following measures: 'UNO'");

    // A measure can be used in the SELECT clause of a GROUP BY query even
    // though it is not a GROUP BY key.
    sql("select deptno, count_plus_100\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select empno, deptno, count(mgr) + 100 as measure count_plus_100\n"
        + "  from emp)\n"
        + "group by deptno")
        .isAggregate(is(true))
        .ok();  // 断言验证：期望SQL验证通过

    // Similarly for a query that is an aggregate query because of another
    // aggregate function.
    sql("select count(*), count_plus_100\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select empno, deptno, count(mgr) + 100 as measure count_plus_100\n"
        + "  from emp)")
        .isAggregate(is(true))
        .ok();  // 断言验证：期望SQL验证通过

    // A measure in a non-aggregate query.
    // Using a measure should not make it an aggregate query.
    // The type of the measure should be the result type of the COUNT aggregate
    // function (BIGINT), not type of the un-aggregated argument type (VARCHAR).
    sql("select deptno, count_plus_100, ename\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select ename, deptno, sal,\n"
        + "      count(ename) + 100 as measure count_plus_100\n"
        + "  from emp)")
        .type("RecordType(INTEGER NOT NULL DEPTNO, "
            + "BIGINT NOT NULL COUNT_PLUS_100, "
            + "VARCHAR(20) NOT NULL ENAME) NOT NULL")
        .isAggregate(is(false))
        .assertMeasure(0, is(false))
        .assertMeasure(1, is(false))
        .assertMeasure(2, is(false));

    // as above, wrapping the measure in AGGREGATE
    sql("select deptno, aggregate(count_plus_100), ename\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select ename, deptno, sal,\n"
        + "      count(ename) + 100 as measure count_plus_100\n"
        + "  from emp)\n"
        + "group by deptno, ename")
        .withOperatorTable(operatorTableFor(SqlLibrary.CALCITE))
        .type("RecordType(INTEGER NOT NULL DEPTNO, "
            + "BIGINT NOT NULL EXPR$1, "
            + "VARCHAR(20) NOT NULL ENAME) NOT NULL");

    // you can apply the AGGREGATE function only to measures
    sql("select deptno, aggregate(count_plus_100), ^aggregate(ename)^\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select ename, deptno, sal,\n"
        + "      count(ename) + 100 as measure count_plus_100\n"
        + "  from emp)\n"
        + "group by deptno, ename")
        .withOperatorTable(operatorTableFor(SqlLibrary.CALCITE))
        .fails("Argument to function 'AGGREGATE' must be a measure");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select deptno, ^aggregate(count_plus_100 + 1)^\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select ename, deptno, sal,\n"
        + "      count(ename) + 100 as measure count_plus_100\n"
        + "  from emp)\n"
        + "group by deptno, ename")
        .withOperatorTable(operatorTableFor(SqlLibrary.CALCITE))
        .fails("Argument to function 'AGGREGATE' must be a measure");  // 断言验证：期望SQL验证失败并检查错误消息

    // A query with AGGREGATE is an aggregate query, even without GROUP BY,
    // and even if it is inside an expression.
    sql("select aggregate(count_plus_100) + 1\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select ename, deptno, sal,\n"
        + "      count(ename) + 100 as measure count_plus_100\n"
        + "  from emp)")
        .withOperatorTable(operatorTableFor(SqlLibrary.CALCITE))
        .isAggregate(is(true));

    // Including a measure in a query does not make it an aggregate query
    sql("select count_plus_100\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (\n"
        + "  select ename, deptno, sal,\n"
        + "      count(ename) + 100 as measure count_plus_100\n"
        + "  from emp)")
        .withOperatorTable(operatorTableFor(SqlLibrary.CALCITE))
        .isAggregate(is(false));
  }

  @Test void testLeastRestrictiveUsesMeasureElement() {  // 测试方法：testLeastRestrictiveUsesMeasureElement
    SqlValidatorFixture f =
        fixture().withExtendedCatalog()
            .withOperatorTable(operatorTableFor(SqlLibrary.BIG_QUERY));
    f.withSql("select ifnull(count_times_100, 0) from empm")
        .type("RecordType(DECIMAL(19, 0) NOT NULL EXPR$0) NOT NULL");
  }

  @Test void testAmbiguousColumnInIn() {  // 测试方法：testAmbiguousColumnInIn
    // ok: cyclic reference
    sql("select * from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + "where e.deptno in (\n"
        + "  select 1 from (values(true)) where e.empno > 10)").ok();  // 断言验证：期望SQL验证通过

    // ok: cyclic reference
    sql("select * from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + "where e.deptno in (\n"
        + "  select e.deptno from (values(true)))").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInList() {  // 测试方法：testInList
    sql("select * from emp where empno in (10,20)").ok();  // 创建SQL测试：解析并验证SQL语句

    // "select * from emp where empno in ()" is invalid -- see parser test
    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "where empno in (10 + deptno, cast(null as integer))").ok();  // 断言验证：期望SQL验证通过
    sql("select * from emp where empno in (10, '20')").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where empno in ^(10, '20')^")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails(ERR_IN_VALUES_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息

    expr("1 in (2, 3, 4)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as integer) in (2, 3, 4)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("1 in (2, cast(null as integer) , 4)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("1 in (2.5, 3.14)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("true in (false, unknown)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("true in (false, false or unknown)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("true in (false, true)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("(1,2) in ((1,2), (3,4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'medium' in (cast(null as varchar(10)), 'bc')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型

    // nullability depends on nullability of both sides
    sql("select empno in (1, 2) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    sql("select nullif(empno,empno) in (1, 2) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    sql("select empno in (1, nullif(empno,empno), 2) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型

    expr("1 in (2, 'c')").ok();  // 断言验证：期望SQL验证通过
    expr("1 in ^(2, 'c')^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails(ERR_IN_VALUES_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("1 in ^((2), (3,4))^")  // 创建表达式测试：解析并验证SQL表达式
        .fails(ERR_IN_VALUES_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("false and ^1 in ('b', 'c')^").ok();  // 断言验证：期望SQL验证通过
    expr("false and ^1 in (date '2012-01-02', date '2012-01-04')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails(ERR_IN_OPERANDS_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("1 > 5 or ^(1, 2) in (3, 4)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails(ERR_IN_OPERANDS_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInSubQuery() {  // 测试方法：testInSubQuery
    sql("select * from emp where deptno in (select deptno from dept)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where (empno,deptno)"  // 创建SQL测试：解析并验证SQL语句
        + " in (select deptno,deptno from dept)").ok();  // 断言验证：期望SQL验证通过

    // NOTE: jhyde: The closing caret should be one character to the right
    // ("dept)^"), but it's difficult to achieve, because parentheses are
    // discarded during the parsing process.
    sql("select * from emp where ^deptno in "  // 创建SQL测试：解析并验证SQL语句
        + "(select deptno,deptno from dept^)")
        .fails("Values passed to IN operator must have compatible types");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5626">[CALCITE-5626]
   * Sub-query with fully-qualified table name throws 'table not found' during
   * validation</a>. */
  @Test void testInSubQueryWithFullyQualifiedName() {  // 测试方法：testInSubQueryWithFullyQualifiedName
    // Minimal test case requires fully-qualified column name in WHERE clause of
    // subquery; sub-query.iq contains further non-minimal test cases.
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "where deptno in (select deptno\n"
        + "  from sales.dept\n"
        + "  where sales.dept.deptno > 15)").ok();  // 断言验证：期望SQL验证通过

    // If we change 'sales.dept' to 'sales.dept2', query is genuinely invalid.
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "where deptno in (select deptno\n"
        + "  from sales.dept\n"
        + "  where ^sales.dept2^.deptno > 15)")
        .fails("Table 'SALES.DEPT2' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testAnyList() {  // 测试方法：testAnyList
    sql("select * from emp where empno = any (10,20)").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "where empno < any (10 + deptno, cast(null as integer))").ok();  // 断言验证：期望SQL验证通过
    sql("select * from emp where empno < any (10, '20')").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where empno < any ^(10, '20')^")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails(ERR_IN_VALUES_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息

    expr("1 < all (2, 3, 4)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as integer) < all (2, 3, 4)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("1 > some (2, cast(null as integer) , 4)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("1 > any (2.5, 3.14)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("true = any (false, unknown)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("true = any (false, false or unknown)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("true <> any (false, true)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("(1,2) = any ((1,2), (3,4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("(1,2) < any ((1,2), (3,4))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'abc' < any (cast(null as varchar(10)), 'bc')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型

    // nullability depends on nullability of both sides
    sql("select empno < any (1, 2) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    sql("select nullif(empno,empno) > all (1, 2) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    sql("select empno in (1, nullif(empno,empno), 2) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型

    expr("1 = any (2, 'c')").ok();  // 断言验证：期望SQL验证通过
    expr("1 = any ^(2, 'c')^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails(ERR_IN_VALUES_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("1 > all ^((2), (3,4))^")  // 创建表达式测试：解析并验证SQL表达式
        .fails(ERR_IN_VALUES_INCOMPATIBLE);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("false and 1 = any ('b', 'c')").ok();  // 断言验证：期望SQL验证通过
    expr("false and ^1 = any (date '2012-01-02', date '2012-01-04')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Values passed to = SOME operator must have compatible types");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("1 > 5 or ^(1, 2) < any (3, 4)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Values passed to < SOME operator must have compatible types");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testDoubleNoAlias() {  // 测试方法：testDoubleNoAlias
    sql("select * from emp join dept on true").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp, dept").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp cross join dept").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testDuplicateColumnAliasIsOK() {  // 测试方法：testDuplicateColumnAliasIsOK
    // duplicate column aliases are daft, but SQL:2003 allows them
    sql("select 1 as a, 2 as b, 3 as a from emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testDuplicateTableAliasFails() {  // 测试方法：testDuplicateTableAliasFails
    // implicit alias clashes with implicit alias
    sql("select 1 from emp, ^emp^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // implicit alias clashes with implicit alias, using join syntax
    sql("select 1 from emp join ^emp^ on emp.empno = emp.mgrno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // explicit alias clashes with implicit alias
    sql("select 1 from emp join ^dept as emp^ on emp.empno = emp.deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // implicit alias does not clash with overridden alias
    sql("select 1 from emp as e join emp on emp.empno = e.deptno").ok();  // 创建SQL测试：解析并验证SQL语句

    // explicit alias does not clash with overridden alias
    sql("select 1 from emp as e join dept as emp on e.empno = emp.deptno").ok();  // 创建SQL测试：解析并验证SQL语句

    // more than 2 in from clause
    sql("select 1 from emp, dept, emp as e, ^dept as emp^, emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // alias applied to sub-query
    sql("select 1 from emp, (^select 1 as x from (values (true))) as emp^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 1 from emp, (^values (true,false)) as emp (b, c)^, dept as emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // alias applied to table function. doesn't matter that table fn
    // doesn't exist - should find the alias problem first
    sql("select 1 from emp, ^table(foo()) as emp^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // explicit table
    sql("select 1 from emp, ^(table foo.bar.emp) as emp^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // alias does not clash with alias inherited from enclosing context
    sql("select 1 from emp, dept where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select 1 from emp where emp.empno = emp.deptno)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testSchemaTableStar() {  // 测试方法：testSchemaTableStar
    sql("select ^sales.e^.* from sales.emp as e")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'SALES\\.E'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sales.dept.* from sales.dept")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL DEPTNO,"
            + " VARCHAR(10) NOT NULL NAME) NOT NULL");
    sql("select sales.emp.* from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select sales.emp.* from emp as emp").ok();  // 创建SQL测试：解析并验证SQL语句
    // MySQL gives: "Unknown table 'emp'"
    // (consistent with MySQL)
    sql("select ^sales.emp^.* from emp as e")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'SALES.EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSchemaTableColumn() {  // 测试方法：testSchemaTableColumn
    sql("select emp.empno from sales.emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select sales.emp.empno from sales.emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select sales.emp.empno from sales.emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "where sales.emp.deptno > 0").ok();  // 断言验证：期望SQL验证通过
    sql("select 1 from sales.emp where sales.emp.^bad^ < 0")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'BAD' not found in table 'SALES.EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^sales.bad^.empno from sales.emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "where sales.emp.deptno > 0")
        .fails("Table 'SALES\\.BAD' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sales.emp.deptno from sales.emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1 from sales.emp where sales.emp.deptno = 10").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1 from sales.emp order by sales.emp.deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    // alias does not hide the fully-qualified name if same
    // (consistent with MySQL)
    sql("select sales.emp.deptno from sales.emp as emp").ok();  // 创建SQL测试：解析并验证SQL语句
    // alias hides the fully-qualified name
    // (consistent with MySQL)
    sql("select ^sales.emp^.deptno from sales.emp as e")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'SALES\\.EMP' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sales.emp.deptno from sales.emp, ^sales.emp^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息
    // Table exists but not used in FROM clause
    sql("select ^sales.emp^.deptno from sales.dept as d1, sales.dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'SALES.EMP' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    // Table does not exist
    sql("select ^sales.bad^.deptno from sales.dept as d1, sales.dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'SALES.BAD' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6584">[CALCITE-6584]
   * Validate prefixed column identifiers in SET clause of UPDATE
   * statement</a>. */
  @Test void testAliasInSetClauseOfUpdate() {  // 测试方法：testAliasInSetClauseOfUpdate
    // good examples
    // (Postgres does not consider these valid, but Calcite in this case
    // is more lenient than Postgres.)
    sql("UPDATE sales.emp AS e SET e.deptno = 10").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("UPDATE emp AS e SET e.deptno = 10").ok();  // 创建SQL测试：解析并验证SQL语句

    // bad examples
    sql("UPDATE sales.emp AS emp SET ^sales.emp^.deptno = 10")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'SALES.EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("UPDATE sales.emp AS e SET ^emp^.deptno = 10")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("UPDATE emp AS e SET ^emp^.deptno = 10")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("UPDATE emp AS e SET ^a.b.c.d^.deptno = 10")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'A.B.C.D'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("UPDATE emp AS e SET ^dept^.deptno = 10")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown identifier 'DEPT'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-881">[CALCITE-881]
   * Allow schema.table.column references in GROUP BY</a>. */
  @Test void testSchemaTableColumnInGroupBy() {  // 测试方法：testSchemaTableColumnInGroupBy
    sql("select 1 from sales.emp group by sales.emp.deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno from sales.emp group by sales.emp.deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno + 1 from sales.emp group by sales.emp.deptno").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testInvalidGroupBy() {  // 测试方法：testInvalidGroupBy
    sql("select ^empno^, deptno from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInvalidGroupBy2() {  // 测试方法：testInvalidGroupBy2
    sql("select count(*) from emp group by ^deptno + 'a'^")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply '\\+' to arguments of type.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(*) from emp group by deptno + 'a'").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testInvalidGroupBy3() {  // 测试方法：testInvalidGroupBy3
    sql("select deptno / 2 + 1, count(*) as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by rollup(deptno / 2, sal), rollup(empno, ^deptno + 'a'^)")
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply '\\+' to arguments of type.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno / 2 + 1, count(*) as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by rollup(deptno / 2, sal), rollup(empno, ^deptno + 'a'^)").ok();  // 断言验证：期望SQL验证通过
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3003">[CALCITE-3003]
   * AssertionError when GROUP BY nested field</a>.
   *
   * <p>Make sure table name of GROUP BY item with nested field could be
   * properly validated.
   */
  @Test void testInvalidGroupByWithInvalidTableName() {  // 测试方法：testInvalidGroupByWithInvalidTableName
    final String sql =
        "select\n"
            + "  coord.x,\n"
            + "  avg(coord.y)\n"
            + "from\n"
            + "  customer.contact_peek\n"
            + "group by\n"
            + "  ^unknown_table_alias.coord^.x";
    sql(sql)
        .fails("Table 'UNKNOWN_TABLE_ALIAS.COORD' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1781">[CALCITE-1781]
   * Allow expression in CUBE and ROLLUP</a>. */
  @Test void testCubeExpression() {  // 测试方法：testCubeExpression
    final String sql = "select deptno + 1\n"
        + "from emp\n"
        + "group by cube(deptno + 1)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过
    final String sql2 = "select deptno + 2 - 2\n"
        + "from emp\n"
        + "group by cube(deptno + 2, empno)";
    sql(sql2).ok();  // 断言验证：期望SQL验证通过
    final String sql3 = "select ^deptno^\n"
        + "from emp\n"
        + "group by cube(deptno + 1)";
    sql(sql3).fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql4 = "select ^deptno^ + 10\n"
        + "from emp\n"
        + "group by rollup(empno, deptno + 10 - 10)";
    sql(sql4).fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql5 = "select deptno + 10\n"
        + "from emp\n"
        + "group by rollup(deptno + 10 - 10, deptno)";
    sql(sql5).ok();  // 断言验证：期望SQL验证通过
  }

  /** Unit test for
   * {@link org.apache.calcite.sql.validate.SqlValidatorUtil#rollup}. */
  @Test void testRollupBitSets() {  // 测试方法：testRollupBitSets
    assertThat(rollup(ImmutableBitSet.of(1), ImmutableBitSet.of(3)),
        hasToString("[{1, 3}, {1}, {}]"));
    assertThat(rollup(ImmutableBitSet.of(1), ImmutableBitSet.of(3, 4)),
        hasToString("[{1, 3, 4}, {1}, {}]"));
    assertThat(rollup(ImmutableBitSet.of(1, 3), ImmutableBitSet.of(4)),
        hasToString("[{1, 3, 4}, {1, 3}, {}]"));
    assertThat(rollup(ImmutableBitSet.of(1, 4), ImmutableBitSet.of(3)),
        hasToString("[{1, 3, 4}, {1, 4}, {}]"));
    // non-disjoint bit sets
    assertThat(rollup(ImmutableBitSet.of(1, 4), ImmutableBitSet.of(3, 4)),
        hasToString("[{1, 3, 4}, {1, 4}, {}]"));
    // some bit sets are empty
    assertThat(
        rollup(ImmutableBitSet.of(1, 4), ImmutableBitSet.of(),
            ImmutableBitSet.of(3, 4), ImmutableBitSet.of()),
        hasToString("[{1, 3, 4}, {1, 4}, {}]"));
    assertThat(rollup(ImmutableBitSet.of(1)),
        hasToString("[{1}, {}]"));
    // one empty bit set
    assertThat(rollup(ImmutableBitSet.of()),
        hasToString("[{}]"));
    // no bit sets
    assertThat(rollup(),
        hasToString("[{}]"));
  }

  private ImmutableList<ImmutableBitSet> rollup(ImmutableBitSet... sets) {
    return SqlValidatorUtil.rollup(ImmutableList.copyOf(sets));
  }

  /** Unit test for
   * {@link org.apache.calcite.sql.validate.SqlValidatorUtil#cube}. */
  @Test void testCubeBitSets() {  // 测试方法：testCubeBitSets
    assertThat(cube(ImmutableBitSet.of(1), ImmutableBitSet.of(3)),
        hasToString("[{1, 3}, {1}, {3}, {}]"));
    assertThat(cube(ImmutableBitSet.of(1), ImmutableBitSet.of(3, 4)),
        hasToString("[{1, 3, 4}, {1}, {3, 4}, {}]"));
    assertThat(cube(ImmutableBitSet.of(1, 3), ImmutableBitSet.of(4)),
        hasToString("[{1, 3, 4}, {1, 3}, {4}, {}]"));
    assertThat(cube(ImmutableBitSet.of(1, 4), ImmutableBitSet.of(3)),
        hasToString("[{1, 3, 4}, {1, 4}, {3}, {}]"));
    // non-disjoint bit sets
    assertThat(
        cube(ImmutableBitSet.of(1, 4), ImmutableBitSet.of(3, 4)),
        hasToString("[{1, 3, 4}, {1, 4}, {3, 4}, {}]"));
    // some bit sets are empty, and there are duplicates
    assertThat(
        cube(ImmutableBitSet.of(1, 4),
            ImmutableBitSet.of(),
            ImmutableBitSet.of(1, 4),
            ImmutableBitSet.of(3, 4),
            ImmutableBitSet.of()),
        hasToString("[{1, 3, 4}, {1, 4}, {3, 4}, {}]"));
    assertThat(cube(ImmutableBitSet.of(1)),
        hasToString("[{1}, {}]"));
    assertThat(cube(ImmutableBitSet.of()),
        hasToString("[{}]"));
    assertThat(cube(),
        hasToString("[{}]"));
  }

  private ImmutableList<ImmutableBitSet> cube(ImmutableBitSet... sets) {
    return SqlValidatorUtil.cube(ImmutableList.copyOf(sets));
  }

  @Test void testGrouping() {  // 测试方法：testGrouping
    sql("select deptno, grouping(deptno) from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno, grouping(deptno, deptno) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno / 2, grouping(deptno / 2),\n"  // 创建SQL测试：解析并验证SQL语句
        + " ^grouping(deptno / 2, empno)^\n"
        + "from emp group by deptno / 2, empno")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno, grouping(^empno^) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Argument to GROUPING operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, grouping(deptno, ^empno^) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Argument to GROUPING operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, grouping(^empno^, deptno) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Argument to GROUPING operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, grouping(^deptno + 1^) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Argument to GROUPING operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, grouping(emp.^xxx^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'XXX' not found in table 'EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, ^grouping(deptno)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, sum(^grouping(deptno)^) over () from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp group by deptno having grouping(deptno) < 5")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno from emp group by deptno order by grouping(deptno)")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno as xx from emp group by deptno order by grouping(xx)")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno as empno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno order by grouping(empno)")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select 1 as deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno order by grouping(^deptno^)")
        .fails("Argument to GROUPING operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp group by deptno order by grouping(emp.deptno)")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select ^deptno^ from emp group by empno order by grouping(deptno)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp order by ^grouping(deptno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp where ^grouping(deptno)^ = 1")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp where ^grouping(deptno)^ = 1 group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp group by deptno, ^grouping(deptno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by grouping sets(deptno, ^grouping(deptno)^)")
        .fails("GROUPING operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by cube(empno, ^grouping(deptno)^)")
        .fails("GROUPING operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by rollup(empno, ^grouping(deptno)^)")
        .fails("GROUPING operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testGroupingId() {  // 测试方法：testGroupingId
    sql("select deptno, grouping_id(deptno) from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno, grouping_id(deptno, deptno) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno / 2, grouping_id(deptno / 2),\n"  // 创建SQL测试：解析并验证SQL语句
        + " ^grouping_id(deptno / 2, empno)^\n"
        + "from emp group by deptno / 2, empno")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno / 2, ^grouping_id()^\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno / 2, empno")
        .fails("Invalid number of arguments to function 'GROUPING_ID'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, grouping_id(^empno^) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Argument to GROUPING_ID operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, grouping_id(^deptno + 1^) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Argument to GROUPING_ID operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, grouping_id(emp.^xxx^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'XXX' not found in table 'EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, ^grouping_id(deptno)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING_ID operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, sum(^grouping_id(deptno)^) over () from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING_ID operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp group by deptno having grouping_id(deptno) < 5")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno from emp group by deptno order by grouping_id(deptno)")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno as xx from emp group by deptno order by grouping_id(xx)")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select deptno as empno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno order by grouping_id(empno)")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select 1 as deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno order by grouping_id(^deptno^)")
        .fails("Argument to GROUPING_ID operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp group by deptno\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by grouping_id(emp.deptno)")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select ^deptno^ from emp group by empno order by grouping_id(deptno)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp order by ^grouping_id(deptno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING_ID operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp where ^grouping_id(deptno)^ = 1")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING_ID operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp where ^grouping_id(deptno)^ = 1\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno")
        .fails("GROUPING_ID operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp group by deptno, ^grouping_id(deptno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING_ID operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by grouping sets(deptno, ^grouping_id(deptno)^)")
        .fails("GROUPING_ID operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by cube(empno, ^grouping_id(deptno)^)")
        .fails("GROUPING_ID operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by rollup(empno, ^grouping_id(deptno)^)")
        .fails("GROUPING_ID operator may only occur in SELECT, HAVING or ORDER BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testGroupId() {  // 测试方法：testGroupId
    final String groupIdOnlyInAggregate =
        "GROUP_ID operator may only occur in an aggregate query";
    final String groupIdWrongClause =
        "GROUP_ID operator may only occur in SELECT, HAVING or ORDER BY clause";

    sql("select deptno, group_id() from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno, ^group_id^ as x from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'GROUP_ID' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, ^group_id(deptno)^ from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Invalid number of arguments to function 'GROUP_ID'\\. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Was expecting 0 arguments");
    // Oracle throws "GROUPING function only supported with GROUP BY CUBE or
    // ROLLUP"
    sql("select ^group_id()^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdOnlyInAggregate);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp order by ^group_id(deptno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdOnlyInAggregate);  // 断言验证：期望SQL验证失败并检查错误消息
    // Oracle throws "GROUPING function only supported with GROUP BY CUBE or
    // ROLLUP"
    sql("select 1 from emp order by ^group_id()^")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdOnlyInAggregate);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select 1 from emp order by ^grouping(deptno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("GROUPING operator may only occur in an aggregate query");  // 断言验证：期望SQL验证失败并检查错误消息
    // Oracle throws "group function is not allowed here"
    sql("select deptno from emp where ^group_id()^ = 1")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdOnlyInAggregate);  // 断言验证：期望SQL验证失败并检查错误消息
    // Oracle throws "group function is not allowed here"
    sql("select deptno from emp group by ^group_id()^")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdWrongClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp where ^group_id()^ = 1 group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdWrongClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp group by deptno, ^group_id()^")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdWrongClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by grouping sets(deptno, ^group_id()^)")
        .fails(groupIdWrongClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by cube(empno, ^group_id()^)")
        .fails(groupIdWrongClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by rollup(empno, ^group_id()^)")
        .fails(groupIdWrongClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select grouping(^group_id()^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdOnlyInAggregate);  // 断言验证：期望SQL验证失败并检查错误消息
    // Oracle throws "not a GROUP BY expression"
    sql("select grouping(^group_id()^) from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails(groupIdWrongClause);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^grouping(sum(empno))^ from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Aggregate expressions cannot be nested");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testCubeGrouping() {  // 测试方法：testCubeGrouping
    sql("select deptno, grouping(deptno) from emp group by cube(deptno)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno, grouping(^deptno + 1^) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by cube(deptno, empno)")
        .fails("Argument to GROUPING operator must be a grouped expression");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSumInvalidArgs() {  // 测试方法：testSumInvalidArgs
    sql("select ^sum(ename)^, deptno from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply 'SUM' to arguments of type 'SUM\\(<VARCHAR\\(20\\)>\\)'\\. .*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sum(ename), deptno from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(DECIMAL(19, 9) NOT NULL EXPR$0, INTEGER NOT NULL DEPTNO) NOT NULL");
  }

  @Test void testSumTooManyArgs() {  // 测试方法：testSumTooManyArgs
    sql("select ^sum(empno, deptno)^, deptno from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Invalid number of arguments to function 'SUM'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSumTooFewArgs() {  // 测试方法：testSumTooFewArgs
    sql("select ^sum()^, deptno from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Invalid number of arguments to function 'SUM'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSingleNoAlias() {  // 测试方法：testSingleNoAlias
    sql("select * from emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testObscuredAliasFails() {  // 测试方法：testObscuredAliasFails
    // It is an error to refer to a table which has been given another
    // alias.
    sql("select * from emp as e where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select 1 from dept where dept.deptno = ^emp^.deptno)")
        .fails("Table 'EMP' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testFromReferenceFails() {  // 测试方法：testFromReferenceFails
    // You cannot refer to a table ('e2') in the parent scope of a query in
    // the from clause.
    sql("select * from emp as e1 where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select * from emp as e2,\n"
        + "    (select * from dept where dept.deptno = ^e2^.deptno))")
        .fails("Table 'E2' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testWhereReference() {  // 测试方法：testWhereReference
    // You can refer to a table ('e1') in the parent scope of a query in
    // the from clause.
    //
    // Note: Oracle10g does not allow this query.
    sql("select * from emp as e1 where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select * from emp as e2,\n"
        + "    (select * from dept where dept.deptno = e1.deptno))").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUnionNameResolution() {  // 测试方法：testUnionNameResolution
    sql("select * from emp as e1 where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select * from emp as e2,\n"
        + "  (select deptno from dept as d\n"
        + "   union\n"
        + "   select deptno from emp as e3 where deptno = ^e2^.deptno))")
        .fails("Table 'E2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select * from dept where ^empno^ < 10")
        .fails("Column 'EMPNO' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnionCountMismatchFails() {  // 测试方法：testUnionCountMismatchFails
    sql("select 1,2 from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select ^3^ from dept")
        .fails("Column count mismatch in UNION");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnionCountMismatcWithValuesFails() {  // 测试方法：testUnionCountMismatcWithValuesFails
    sql("select * from ( values (1))\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select ^*^ from ( values (1,2))")
        .fails("Column count mismatch in UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from ( values (1))\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select ^*^ from emp")
        .fails("Column count mismatch in UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select ^*^ from ( values (1))")
        .fails("Column count mismatch in UNION");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnionTypeMismatchFails() {  // 测试方法：testUnionTypeMismatchFails
    sql("select 1, ^2^ from emp union select deptno, name from dept")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Type mismatch in column 2 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select 1, 2 from emp union select deptno, ^name^ from dept").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select ^slacker^ from emp union select name from dept")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Type mismatch in column 1 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^slacker^ from emp union select name from dept").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select ^name^ from dept union select name from dept union select slacker from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Type mismatch in column 1 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^name^ from dept except select name from dept except select slacker from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Type mismatch in column 1 of EXCEPT");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^name^ from dept intersect select name from dept intersect select slacker from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Type mismatch in column 1 of INTERSECT");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^name^ from dept minus select name from dept minus select slacker from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .withConformance(SqlConformanceEnum.ORACLE_12) // in order to enable isMinusAllowed()
        .fails("Type mismatch in column 1 of EXCEPT");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select name from dept union select name from dept union select ename from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .ok();  // 断言验证：期望SQL验证通过

    sql("select name from dept except select name from dept except select ename from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .ok();  // 断言验证：期望SQL验证通过

    sql("select name from dept intersect select name from dept intersect select ename from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUnionTypeMismatchWithStarFails() {  // 测试方法：testUnionTypeMismatchWithStarFails
    sql("select ^*^ from dept union select 1, 2 from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Type mismatch in column 2 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from dept union select 1, 2 from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select ^dept.*^ from dept union select 1, 2 from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Type mismatch in column 2 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select dept.* from dept union select 1, 2 from emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testUnionTypeMismatchWithValuesFails() {  // 测试方法：testUnionTypeMismatchWithValuesFails
    sql("values (1, ^2^, 3), (3, 4, 5), (6, 7, 8) union\n"  // 创建SQL测试：解析并验证SQL语句
        + "select deptno, name, deptno from dept")
        .withTypeCoercion(false)
        .fails("Type mismatch in column 2 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("values (1, 2, 3), (3, 4, 5), (6, 7, 8) union\n"  // 创建SQL测试：解析并验证SQL语句
        + "select deptno, ^name^, deptno from dept").ok();  // 断言验证：期望SQL验证通过

    sql("select 1 from (values (^'x'^)) union\n"  // 创建SQL测试：解析并验证SQL语句
        + "select 'a' from (values ('y'))")
        .withTypeCoercion(false)
        .fails("Type mismatch in column 1 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select 1 from (values ('x')) union\n"  // 创建SQL测试：解析并验证SQL语句
        + "select 'a' from (values ('y'))").ok();  // 断言验证：期望SQL验证通过

    sql("select 1 from (values (^'x'^)) union\n"  // 创建SQL测试：解析并验证SQL语句
        + "(values ('a'))")
        .withTypeCoercion(false)
        .fails("Type mismatch in column 1 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select 1 from (values ('x')) union\n"  // 创建SQL测试：解析并验证SQL语句
        + "(values ('a'))").ok();  // 断言验证：期望SQL验证通过

    sql("select 1, ^2^, 3 union\n "  // 创建SQL测试：解析并验证SQL语句
        + "select deptno, name, deptno from dept")
        .withTypeCoercion(false)
        .fails("Type mismatch in column 2 of UNION");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select 1, 2, 3 union\n "  // 创建SQL测试：解析并验证SQL语句
        + "select deptno, name, deptno from dept").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testValuesTypeMismatchFails() {  // 测试方法：testValuesTypeMismatchFails
    sql("^values (1), ('a')^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Values passed to VALUES operator must have compatible types");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testNaturalCrossJoinFails() {  // 测试方法：testNaturalCrossJoinFails
    sql("select * from emp natural cross ^join^ dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot specify condition \\(NATURAL keyword, or ON or USING "  // 断言验证：期望SQL验证失败并检查错误消息
            + "clause\\) following CROSS JOIN");
  }

  @Test void testCrossJoinUsingFails() {  // 测试方法：testCrossJoinUsingFails
    sql("select * from emp cross join dept ^using^ (deptno)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot specify condition \\(NATURAL keyword, or ON or USING "  // 断言验证：期望SQL验证失败并检查错误消息
            + "clause\\) following CROSS JOIN");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5547">[CALCITE-5547]
   * Join using returns incorrect column names</a>. */
  @Test void testExtraColJoinUsing() {  // 测试方法：testExtraColJoinUsing
    final String expectedType = "RecordType(INTEGER NOT NULL TWO, "
        + "INTEGER NOT NULL DEPTNO, "
        + "INTEGER NOT NULL EMPNO, "
        + "VARCHAR(20) NOT NULL ENAME, "
        + "VARCHAR(10) NOT NULL JOB, "
        + "INTEGER MGR, "
        + "TIMESTAMP(0) NOT NULL HIREDATE, "
        + "INTEGER NOT NULL SAL, "
        + "INTEGER NOT NULL COMM, "
        + "BOOLEAN NOT NULL SLACKER, "
        + "VARCHAR(10) NOT NULL NAME) NOT NULL";

    sql("select 2 as two, * from emp inner join dept using(deptno)")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType);

    sql("select 2 as two, * from emp natural join dept")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType);

    sql("select *, 2 as two from emp natural join dept")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL DEPTNO, "
            + "INTEGER NOT NULL EMPNO, "
            + "VARCHAR(20) NOT NULL ENAME, "
            + "VARCHAR(10) NOT NULL JOB, "
            + "INTEGER MGR, "
            + "TIMESTAMP(0) NOT NULL HIREDATE, "
            + "INTEGER NOT NULL SAL, "
            + "INTEGER NOT NULL COMM, "
            + "BOOLEAN NOT NULL SLACKER, "
            + "VARCHAR(10) NOT NULL NAME, "
            + "INTEGER NOT NULL TWO) NOT NULL");
  }

  @Test void testJoinUsing() {  // 测试方法：testJoinUsing
    final String empDeptType = "RecordType(INTEGER NOT NULL DEPTNO,"
        + " INTEGER NOT NULL EMPNO,"
        + " VARCHAR(20) NOT NULL ENAME,"
        + " VARCHAR(10) NOT NULL JOB,"
        + " INTEGER MGR,"
        + " TIMESTAMP(0) NOT NULL HIREDATE,"
        + " INTEGER NOT NULL SAL,"
        + " INTEGER NOT NULL COMM,"
        + " BOOLEAN NOT NULL SLACKER,"
        + " VARCHAR(10) NOT NULL NAME) NOT NULL";
    sql("select * from emp join dept using (deptno)").ok()  // 创建SQL测试：解析并验证SQL语句
        .type(empDeptType);

    // NATURAL has same effect as USING
    sql("select * from emp natural join dept").ok()  // 创建SQL测试：解析并验证SQL语句
        .type(empDeptType);

    // fail: comm exists on one side not the other
    // todo: The error message could be improved.
    sql("select * from emp join dept using (deptno, ^comm^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'COMM' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from emp join dept using (^empno^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'EMPNO' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from dept join emp using (^empno^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'EMPNO' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // not on either side
    sql("select * from dept join emp using (^abc^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ABC' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // column exists, but wrong case
    sql("select * from dept join emp using (^\"deptno\"^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'deptno' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // ok to repeat (ok in Oracle10g too)
    final String sql = "select * from emp join dept using (deptno, deptno)";
    sql(sql).ok()  // 断言验证：期望SQL验证通过
        .type(empDeptType);

    // inherited column, not found in either side of the join, in the
    // USING clause
    sql("select * from dept where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "select 1 from emp join bonus using (^dname^))")
        .fails("Column 'DNAME' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // inherited column, found in only one side of the join, in the
    // USING clause
    sql("select * from dept where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "select 1 from emp join bonus using (^deptno^))")
        .fails("Column 'DEPTNO' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // cannot qualify common column in Oracle.
    sql("select ^emp.deptno^ from emp join dept using (deptno)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .fails("Cannot qualify common column 'EMP.DEPTNO'")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(SqlConformanceEnum.ORACLE_12)
        .fails("Cannot qualify common column 'EMP.DEPTNO'");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^emp.deptno^ from emp natural join dept")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .fails("Cannot qualify common column 'EMP.DEPTNO'")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(SqlConformanceEnum.ORACLE_12)
        .fails("Cannot qualify common column 'EMP.DEPTNO'");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select count(^emp.deptno^) from emp join dept using (deptno)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .fails("Cannot qualify common column 'EMP.DEPTNO'")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(SqlConformanceEnum.ORACLE_12)
        .fails("Cannot qualify common column 'EMP.DEPTNO'");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select emp.deptno from emp join dept using (deptno)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.DEFAULT)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.MYSQL_5)
        .ok();  // 断言验证：期望SQL验证通过

    sql("select emp.empno from emp natural join dept")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.DEFAULT)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.ORACLE_12)
        .ok();  // 断言验证：期望SQL验证通过

    sql("select emp.empno from emp join dept using (deptno)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.DEFAULT)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.ORACLE_12)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testCrossJoinOnFails() {  // 测试方法：testCrossJoinOnFails
    sql("select * from emp cross join dept\n"  // 创建SQL测试：解析并验证SQL语句
        + " ^on^ emp.deptno = dept.deptno")
        .fails("Cannot specify condition \\(NATURAL keyword, or ON or "  // 断言验证：期望SQL验证失败并检查错误消息
            + "USING clause\\) following CROSS JOIN");
  }

  @Test void testInnerJoinWithoutUsingOrOnFails() {  // 测试方法：testInnerJoinWithoutUsingOrOnFails
    sql("select * from emp inner ^join^ dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "where emp.deptno = dept.deptno")
        .fails("INNER, LEFT, RIGHT, FULL, or ASOF join requires a condition "  // 断言验证：期望SQL验证失败并检查错误消息
            + "\\(NATURAL keyword or ON or USING clause\\)");
  }

  @Test void testNaturalJoinWithOnFails() {  // 测试方法：testNaturalJoinWithOnFails
    sql("select * from emp natural join dept on ^emp.deptno = dept.deptno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot specify NATURAL keyword with ON or USING clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testNaturalJoinWithUsing() {  // 测试方法：testNaturalJoinWithUsing
    sql("select * from emp natural join dept ^using (deptno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot specify NATURAL keyword with ON or USING clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testNaturalJoinCaseSensitive() {  // 测试方法：testNaturalJoinCaseSensitive
    // With case-insensitive match, more columns are recognized as join columns
    // and therefore "*" expands to fewer columns.
    final String sql = "select *\n"
        + "from (select empno, deptno from emp)\n"
        + "natural join (select deptno as \"deptno\", name from dept)";
    final String type0 = "RecordType(INTEGER NOT NULL EMPNO,"
        + " INTEGER NOT NULL DEPTNO,"
        + " INTEGER NOT NULL deptno,"
        + " VARCHAR(10) NOT NULL NAME) NOT NULL";
    final String type1 = "RecordType(INTEGER NOT NULL DEPTNO,"
        + " INTEGER NOT NULL EMPNO,"
        + " VARCHAR(10) NOT NULL NAME) NOT NULL";
    sql(sql)
        .type(type0)
        .withCaseSensitive(false)
        .type(type1);
  }

  @Test void testNaturalJoinIncompatibleDatatype() {  // 测试方法：testNaturalJoinIncompatibleDatatype
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select ename as name, hiredate as deptno from emp)\n"
        + "^natural^ join\n"
        + "(select deptno, name as sal from dept)")
        .fails("Column 'DEPTNO' matched using NATURAL keyword or USING clause "  // 断言验证：期望SQL验证失败并检查错误消息
            + "has incompatible types: "
            + "cannot compare 'TIMESTAMP\\(0\\)' to 'INTEGER'");

    // INTEGER and VARCHAR are comparable: VARCHAR implicit converts to INTEGER
    sql("select * from emp natural ^join^\n"  // 创建SQL测试：解析并验证SQL语句
        + "(select deptno, name as sal from dept)").ok();  // 断言验证：期望SQL验证通过

    // make sal occur more than once on rhs, it is ignored and therefore
    // there is no error about incompatible types
    sql("select * from emp natural join\n"  // 创建SQL测试：解析并验证SQL语句
        + " (select deptno, name as sal, 'foo' as sal2 from dept)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testJoinUsingIncompatibleDatatype() {  // 测试方法：testJoinUsingIncompatibleDatatype
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select ename as name, hiredate as deptno from emp)\n"
        + "join (select deptno, name as sal from dept) using (^deptno^, sal)")
        .fails("Column 'DEPTNO' matched using NATURAL keyword or USING clause "  // 断言验证：期望SQL验证失败并检查错误消息
            + "has incompatible types: "
            + "cannot compare 'TIMESTAMP\\(0\\)' to 'INTEGER'");

    // INTEGER and VARCHAR are comparable: VARCHAR implicit converts to INTEGER
    final String sql = "select * from emp\n"
        + "join (select deptno, name as sal from dept) using (deptno, sal)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testJoinUsingInvalidColsFails() {  // 测试方法：testJoinUsingInvalidColsFails
    // todo: Improve error msg
    sql("select * from emp left join dept using (^gender^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'GENDER' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5171">[CALCITE-5171]
   * NATURAL join and USING should fail if join columns are not unique</a>. */
  @Test void testJoinDuplicateColumns() {  // 测试方法：testJoinDuplicateColumns
    // NATURAL join and USING should fail if join columns are not unique
    final String message = "Column name 'DEPTNO' in NATURAL join or "
        + "USING clause is not unique on one side of join";
    sql("select e.ename, d.name\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept as d\n"
        + "^natural^ join (select ename, sal as deptno, deptno from emp) as e")
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息

    // A similar query with USING fails with the same error
    sql("select e.ename, d.name\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept as d\n"
        + "join (select ename, sal as deptno, deptno from emp) as e\n"
        + "  using (^deptno^)")
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息

    // Reversed query gives reversed error message
    sql("select e.ename, d.name\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select ename, sal as deptno, deptno from emp) as e\n"
        + "join dept as d\n"
        + "  using (^deptno^)")
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息

    // Also with "*". (Proves that FROM is validated before SELECT.)
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "left join (select deptno, name as deptno from dept)\n"
        + "  using (^deptno^)")
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test @DisplayName("Natural join require input column uniqueness")
  void testNaturalJoinRequireInputColumnUniqueness() {
    final String message = "Column name 'DEPTNO' in NATURAL join or "
        + "USING clause is not unique on one side of join";
    // Invalid. NATURAL JOIN eliminates duplicate columns from its output but
    // requires input columns to be unique.
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (emp as e cross join dept as d)\n"
        + "^natural^ join\n"
        + "(emp as e2 cross join dept as d2)")
        .fails(message);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test @DisplayName("Should produce two DEPTNO columns")
  void testReturnsCorrectRowTypeOnCombinedJoin() {
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp as e\n"
        + "natural join dept as d\n"
        + "join (select deptno as x, deptno from dept) as d2"
        + "  on d2.deptno = e.deptno")
        .type("RecordType("
            + "INTEGER NOT NULL DEPTNO, "
            + "INTEGER NOT NULL EMPNO, "
            + "VARCHAR(20) NOT NULL ENAME, "
            + "VARCHAR(10) NOT NULL JOB, "
            + "INTEGER MGR, "
            + "TIMESTAMP(0) NOT NULL HIREDATE, "
            + "INTEGER NOT NULL SAL, "
            + "INTEGER NOT NULL COMM, "
            + "BOOLEAN NOT NULL SLACKER, "
            + "VARCHAR(10) NOT NULL NAME, "
            + "INTEGER NOT NULL X, "
            + "INTEGER NOT NULL DEPTNO1) NOT NULL");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5171">[CALCITE-5171]
   * NATURAL join and USING should fail if join columns are not unique</a>. */
  @Test void testCorrectJoinDuplicateColumns() {  // 测试方法：testCorrectJoinDuplicateColumns
    // The error only occurs if the duplicate column is referenced. The
    // following query has a duplicate hiredate column.
    sql("select e.ename, d.name\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept as d\n"
        + "join (select ename, sal as hiredate, deptno from emp) as e\n"
        + "  using (deptno)")
        .ok();  // 断言验证：期望SQL验证通过

    // Previous join chain does not affect validation.
    sql("select * from EMP natural join EMPNULLABLES natural join DEPT")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testNaturalEmptyKey() {  // 测试方法：testNaturalEmptyKey
    // If there are no columns in common, natural join is empty, and that's OK.
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select ename from emp) as e\n"
        + "natural join dept as d")
        .type("RecordType("
            + "VARCHAR(20) NOT NULL ENAME, "
            + "INTEGER NOT NULL DEPTNO, "
            + "VARCHAR(10) NOT NULL NAME) NOT NULL");

    // If there are duplicates on one side, that's OK, because the empty natural
    // join prevents us from checking.
    sql("select d.*\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select ename, sal as ename from emp) as e\n"
        + "natural join dept as d")
        .type("RecordType("
            + "INTEGER NOT NULL DEPTNO, "
            + "VARCHAR(10) NOT NULL NAME) NOT NULL");
    // Cannot expand star if it contains duplicate columns.
    // (Postgres thinks this query is OK.)
    sql("select ^e.*^\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (select ename, sal as ename from emp) as e\n"
        + "natural join dept as d")
        .fails("Column 'ENAME' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJoinRowType() {  // 测试方法：testJoinRowType
    sql("select * from emp left join dept on emp.deptno = dept.deptno")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL EMPNO,"
            + " VARCHAR(20) NOT NULL ENAME,"
            + " VARCHAR(10) NOT NULL JOB,"
            + " INTEGER MGR,"
            + " TIMESTAMP(0) NOT NULL HIREDATE,"
            + " INTEGER NOT NULL SAL,"
            + " INTEGER NOT NULL COMM,"
            + " INTEGER NOT NULL DEPTNO,"
            + " BOOLEAN NOT NULL SLACKER,"
            + " INTEGER DEPTNO0,"
            + " VARCHAR(10) NAME) NOT NULL");

    sql("select * from emp right join dept on emp.deptno = dept.deptno")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER EMPNO,"
            + " VARCHAR(20) ENAME,"
            + " VARCHAR(10) JOB,"
            + " INTEGER MGR,"
            + " TIMESTAMP(0) HIREDATE,"
            + " INTEGER SAL,"
            + " INTEGER COMM,"
            + " INTEGER DEPTNO,"
            + " BOOLEAN SLACKER,"
            + " INTEGER NOT NULL DEPTNO0,"
            + " VARCHAR(10) NOT NULL NAME) NOT NULL");

    sql("select * from emp full join dept on emp.deptno = dept.deptno")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER EMPNO,"
            + " VARCHAR(20) ENAME,"
            + " VARCHAR(10) JOB,"
            + " INTEGER MGR,"
            + " TIMESTAMP(0) HIREDATE,"
            + " INTEGER SAL,"
            + " INTEGER COMM,"
            + " INTEGER DEPTNO,"
            + " BOOLEAN SLACKER,"
            + " INTEGER DEPTNO0,"
            + " VARCHAR(10) NAME) NOT NULL");
  }

  @Test void testJoinUsingWithParentheses() {  // 测试方法：testJoinUsingWithParentheses
    sql("select * from (emp join bonus using (job))\n"  // 创建SQL测试：解析并验证SQL语句
        + "join dept using (deptno)").ok();  // 断言验证：期望SQL验证通过

    // Cannot alias a JOIN (until
    // [CALCITE-5168] Allow AS after parenthesized JOIN
    // is fixed).
    sql("select * from (emp ^join^ bonus using (job)) as x\n"  // 创建SQL测试：解析并验证SQL语句
        + "join dept using (deptno)")
        .fails("Join expression encountered in illegal context");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from (emp join bonus using (job))\n"  // 创建SQL测试：解析并验证SQL语句
        + "join dept using (^dname^)")
        .fails("Column 'DNAME' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // Needs real Error Message and error marks in query
    sql("select * from (emp join bonus using (job))\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (select 1 as job from ^(^true)) using (job)")
        .fails("(?s).*Encountered \"\\( true\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Disabled("bug: should fail if sub-query does not have alias")
  @Test void testJoinSubQuery() {  // 测试方法：testJoinSubQuery
    // Sub-queries require alias
    sql("select * from (select 1 as uno from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (values (1), (2)) on true")
        .fails("require alias");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJoinOnIn() {  // 测试方法：testJoinOnIn
    final String sql = "select * from emp join dept\n"
        + "on dept.deptno in (select deptno from emp)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testJoinOnInCorrelated() {  // 测试方法：testJoinOnInCorrelated
    final String sql = "select * from emp as e join dept\n"
        + "on dept.deptno in (select deptno from emp where deptno < e.deptno)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testJoinOnInCorrelatedFails() {  // 测试方法：testJoinOnInCorrelatedFails
    final String sql = "select * from emp as e join dept as d\n"
        + "on d.deptno in (select deptno from emp where deptno < d.^empno^)";
    sql(sql).fails("Column 'EMPNO' not found in table 'D'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJoinOnExistsCorrelated() {  // 测试方法：testJoinOnExistsCorrelated
    final String sql = "select * from emp as e join dept\n"
        + "on exists (select 1, 2 from emp where deptno < e.deptno)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testJoinOnScalarCorrelated() {  // 测试方法：testJoinOnScalarCorrelated
    final String sql = "select * from emp as e join dept d\n"
        + "on d.deptno = (select 1 from emp where deptno < e.deptno)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testJoinOnScalarFails() {  // 测试方法：testJoinOnScalarFails
    final String sql = "select * from emp as e join dept d\n"
        + "on d.deptno = ^(select 1, 2 from emp where deptno < e.deptno)^";
    final String expected = "(?s)Cannot apply '\\$SCALAR_QUERY' to arguments "
        + "of type '\\$SCALAR_QUERY\\(<RECORDTYPE\\(INTEGER EXPR\\$0, INTEGER "
        + "EXPR\\$1\\)>\\)'\\. Supported form\\(s\\).*";
    sql(sql).fails(expected);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJoinUsingThreeWay() {  // 测试方法：testJoinUsingThreeWay
    final String sql0 = "select *\n"
        + "from emp as e\n"
        + "join dept as d using (deptno)\n"
        + "join emp as e2 using (empno)";
    sql(sql0).ok();  // 断言验证：期望SQL验证通过

    final String sql1 = "select *\n"
        + "from emp as e\n"
        + "join dept as d using (deptno)\n"
        + "join dept as d2 using (^deptno^)";
    sql(sql1).ok();  // 断言验证：期望SQL验证通过

    final String sql2 = "select *\n"
        + "from emp as e\n"
        + "join dept as d using (deptno)";
    final String type2 = "RecordType(INTEGER NOT NULL DEPTNO,"
        + " INTEGER NOT NULL EMPNO,"
        + " VARCHAR(20) NOT NULL ENAME,"
        + " VARCHAR(10) NOT NULL JOB,"
        + " INTEGER MGR,"
        + " TIMESTAMP(0) NOT NULL HIREDATE,"
        + " INTEGER NOT NULL SAL,"
        + " INTEGER NOT NULL COMM,"
        + " BOOLEAN NOT NULL SLACKER,"
        + " VARCHAR(10) NOT NULL NAME) NOT NULL";
    sql(sql2).type(type2);

    final String sql3 = "select *\n"
        + "from emp as e\n"
        + "join dept as d using (deptno)\n"
        + "join (values (10, 1000)) as b (empno, bonus) using (empno)";
    final String type3 = "RecordType(INTEGER NOT NULL EMPNO,"
        + " INTEGER NOT NULL DEPTNO,"
        + " VARCHAR(20) NOT NULL ENAME,"
        + " VARCHAR(10) NOT NULL JOB,"
        + " INTEGER MGR,"
        + " TIMESTAMP(0) NOT NULL HIREDATE,"
        + " INTEGER NOT NULL SAL,"
        + " INTEGER NOT NULL COMM,"
        + " BOOLEAN NOT NULL SLACKER,"
        + " VARCHAR(10) NOT NULL NAME,"
        + " INTEGER NOT NULL BONUS) NOT NULL";
    sql(sql3).type(type3);
  }

  @Test void testWhere() {  // 测试方法：testWhere
    sql("select * from emp where ^sal^")  // 创建SQL测试：解析并验证SQL语句
        .fails("WHERE clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testOn() {  // 测试方法：testOn
    sql("select * from emp e1 left outer join emp e2 on ^e1.sal^")  // 创建SQL测试：解析并验证SQL语句
        .fails("ON clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testHaving() {  // 测试方法：testHaving
    sql("select empno from emp group by empno having ^sum(sal)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("HAVING clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^*^ from emp having sum(sal) > 10")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMP\\.EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // agg in select and having, no group by
    sql("select sum(sal + sal) from emp having sum(sal) > 10").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT deptno FROM emp GROUP BY deptno HAVING ^sal^ > 10")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'SAL' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testHavingBetween() {  // 测试方法：testHavingBetween
    // FRG-115: having clause with between not working
    sql("select deptno from emp group by deptno\n"  // 创建SQL测试：解析并验证SQL语句
        + "having deptno between 10 and 12").ok();  // 断言验证：期望SQL验证通过

    // this worked even before FRG-115 was fixed
    sql("select deptno from emp group by deptno having deptno + 5 > 10").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  /** Tests the {@code QUALIFY} clause. */
  @Test void testQualifyPositive() {  // 测试方法：testQualifyPositive
    final SqlValidatorFixture f =
        fixture().withConformance(SqlConformanceEnum.LENIENT);

    final String qualifyWithoutAlias = "SELECT\n"
        + "empno, ename\n"
        + "FROM emp\n"
        + "QUALIFY ROW_NUMBER() over (partition by ename order by deptno) = 1";
    f.withSql(qualifyWithoutAlias).ok();  // 断言验证：期望SQL验证通过

    final String qualifyWithAlias = "SELECT empno, ename, deptno,\n"
        + "   ROW_NUMBER() over (partition by ename order by deptno) as row_num\n"
        + "FROM emp\n"
        + "QUALIFY row_num = 1";
    f.withSql(qualifyWithAlias).ok();  // 断言验证：期望SQL验证通过

    final String qualifyWithWindowClause = "SELECT empno, ename,\n"
        + " SUM(deptno) OVER myWindow as sumDeptNo\n"
        + "FROM emp\n"
        + "WINDOW myWindow AS (PARTITION BY ename ORDER BY empno)\n"
        + "QUALIFY sumDeptNo = 1";
    f.withSql(qualifyWithWindowClause).ok();  // 断言验证：期望SQL验证通过

    final String qualifyWithEverything = "SELECT DISTINCT empno, ename, deptno,\n"
        + "    RANK() OVER (PARTITION BY ename ORDER BY deptno DESC) as rank_val\n"
        + "FROM emp\n"
        + "WHERE sal > 1000\n"
        + "QUALIFY rank_val = (SELECT COUNT(*) FROM emp)\n"
        + "ORDER BY deptno\n"
        + "LIMIT 5";
    f.withSql(qualifyWithEverything).ok();  // 断言验证：期望SQL验证通过

    final String qualifyReferencingCommonColumnInJoin = "SELECT * \n"
        + "FROM emp\n"
        + "NATURAL JOIN dept\n"
        + "QUALIFY ROW_NUMBER() over (partition by ename order by emp.deptno) = 1";
    f.withSql(qualifyReferencingCommonColumnInJoin).ok();  // 断言验证：期望SQL验证通过

    final String qualifyOnMultipleWindowFunctions = "SELECT"
        + "   AVG(deptno) OVER (PARTITION BY ename) avgDeptNo,"
        + "   RANK() OVER (PARTITION BY deptno ORDER BY ename) as myRank\n"
        + "FROM emp\n"
        + "QUALIFY avgDeptNo = 1 AND myRank = 1";
    f.withSql(qualifyOnMultipleWindowFunctions).ok();  // 断言验证：期望SQL验证通过
  }

  /** Negative tests for the {@code QUALIFY} clause. */
  @Test void testQualifyNegative() {  // 测试方法：testQualifyNegative
    final SqlValidatorFixture f =
        fixture().withConformance(SqlConformanceEnum.LENIENT);

    // The predicate in the QUALIFY clause expression must be a boolean, since
    // we use it as a filter.
    final String qualifyWithNonBooleanExpression = "SELECT\n"
        + "empno, ename\n"
        + "FROM emp\n"
        + "QUALIFY ^1 + 1^";
    f.withSql(qualifyWithNonBooleanExpression)
        .fails("QUALIFY clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息

    // We don't allow for using ordinal column references in QUALIFY.
    final String qualifyWithOrdinal = "SELECT\n"
        + "empno, ename, ROW_NUMBER() over (partition by ename order by deptno) = 1\n"
        + "FROM emp\n"
        + "QUALIFY ^3^";
    f.withSql(qualifyWithOrdinal)
        .fails("QUALIFY clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息

    // 'deptno' is a common column in both the emp and dept table.
    // We need to use emp.deptno or dept.deptno to make it unambiguous
    final String qualifyReferencingAmbiguousCommonColumnInJoin = "SELECT *\n"
        + "FROM emp\n"
        + "NATURAL JOIN dept\n"
        + "QUALIFY ROW_NUMBER() over (partition by ename order by ^deptno^) = 1";
    f.withSql(qualifyReferencingAmbiguousCommonColumnInJoin)
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    // Qualify must contain a window function. This matches the behavior where
    // HAVING needs to have an aggregate or reference a group column.
    final String qualifyOnNonWindowFunction = "SELECT * \n"
        + "FROM emp\n"
        + "QUALIFY ^SUM(deptno) = 1^";
    f.withSql(qualifyOnNonWindowFunction)
        .fails("QUALIFY expression 'SUM\\(`EMP`\\.`DEPTNO`\\) = 1' "  // 断言验证：期望SQL验证失败并检查错误消息
            + "must contain a window function");

    final String qualifyOnAliasedNonWindowFunction = ""
        + "SELECT ^SUM(deptno) as sumDeptNo\n"
        + "FROM emp\n"
        + "QUALIFY sumDeptNo = 1^";
    f.withSql(qualifyOnAliasedNonWindowFunction)
        .fails("QUALIFY expression 'SUM\\(`EMP`\\.`DEPTNO`\\) = 1' "  // 断言验证：期望SQL验证失败并检查错误消息
            + "must contain a window function");

    // This query fails, since it's a mix of regular aggregates and window
    // functions. This query needs to fail, since we assume that qualify filters
    // on the result of a window function in SqlToRelConverter and that the
    // input Rel is a LogicalProject and not a LogicalAggregate.
    final String mixedNonAggregateAndWindowAggregate = "SELECT\n"
        + "   SUM(deptno) as sumDeptNo, "
        + "   RANK() OVER (PARTITION BY ^ename^ ORDER BY deptno) myRank\n"
        + "FROM emp\n";
    f.withSql(mixedNonAggregateAndWindowAggregate)
        .fails("Expression 'ENAME' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Tests the {@code WITH} clause, also called common table expressions. */
  @Test void testWith() {  // 测试方法：testWith
    // simplest possible
    sql("with emp2 as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .type(EMP_RECORD_TYPE);

    // simplest with recursive fails
    sql("with RECURSIVE emp2 as (select * from ^emp2^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .fails("Object 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("with RECURSIVE emp2 as ("  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp "
        + " union select * from ^emp2^"
        + " union select * from emp2"
        + ")\n"
        + "select * from emp2")
        .fails("Object 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // mutually recursive queries are not supported.
    sql("WITH RECURSIVE\n"  // 创建SQL测试：解析并验证SQL语句
        + "x (id) AS (SELECT 1 UNION ALL SELECT id+1 FROM ^y^ WHERE id < 5),\n"
        + "y (id) AS (SELECT 1 UNION ALL SELECT id+1 FROM x WHERE id < 5)\n"
        + "SELECT * FROM x")
        .fails("Object 'Y' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("WITH RECURSIVE t_out(n) AS\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (WITH RECURSIVE t_in(n) AS\n"
        + "     (\n"
        + "      VALUES (1)\n"
        + "      UNION ALL SELECT n+1\n"
        + "      FROM ^t_out^\n"
        + "      WHERE n < 9 ) SELECT n\n"
        + "   FROM t_in\n"
        + "   UNION ALL SELECT n*10\n"
        + "   FROM t_out\n"
        + "   WHERE n < 100 )\n"
        + "SELECT n\n"
        + "FROM t_out")
        .fails("Object 'T_OUT' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("WITH RECURSIVE cte (n) AS\n"  // 创建SQL测试：解析并验证SQL语句
        + "(\n"
        + "  SELECT 1, 2\n"
        + "  UNION ALL\n"
        + "  SELECT ^n + 1^ FROM cte WHERE n < 5\n"
        + ")\n"
        + "SELECT * FROM cte")
        .fails("Column count mismatch in UNION ALL");  // 断言验证：期望SQL验证失败并检查错误消息

    // simplest with RECURSIVE working case.
    sql("with RECURSIVE emp2 as (select * from emp union select * from emp2)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .type(EMP_RECORD_TYPE);

    // union all with recursive working case.
    sql("with RECURSIVE emp2 as (select * from emp union all select * from emp2)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .type(EMP_RECORD_TYPE);

    // recursive usage of the with clause table name on the left child should throw an error.
    sql("with RECURSIVE emp2 as (select * from ^emp2^ union all select * from emp2)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .fails("Object 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("with RECURSIVE emp2 as (select * from emp intersect select * from ^emp2^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .fails("Object 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("with recursive emp2 as (select * from emp),\n"  // 创建SQL测试：解析并验证SQL语句
        + "emp3 as (select * from emp2 union all select * from emp3)\n"
        + "select * from emp2")
        .type(EMP_RECORD_TYPE);

    // degree of emp2 column list does not match its query
    sql("with emp2 ^(x, y)^ as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .fails("Number of columns must match number of query columns");  // 断言验证：期望SQL验证失败并检查错误消息

    // duplicate names in column list
    sql("with emp2 (x, y, ^y^, x) as (select sal, deptno, ename, empno from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .fails("Duplicate name 'Y' in column list");  // 断言验证：期望SQL验证失败并检查错误消息

    // column list required if aliases are not unique
    sql("with emp2 as (^select empno as e, sal, deptno as e from emp^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2")
        .fails("Column has duplicate column name 'E' and no column list specified");  // 断言验证：期望SQL验证失败并检查错误消息

    // forward reference
    sql("with emp3 as (select * from ^emp2^),\n"  // 创建SQL测试：解析并验证SQL语句
        + " emp2 as (select * from emp)\n"
        + "select * from emp3")
        .fails("Object 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // forward reference in with-item not used; should still fail
    sql("with emp3 as (select * from ^emp2^),\n"  // 创建SQL测试：解析并验证SQL语句
        + " emp2 as (select * from emp)\n"
        + "select * from emp2")
        .fails("Object 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // table not used is ok
    sql("with emp2 as (select * from emp),\n"  // 创建SQL测试：解析并验证SQL语句
        + " emp3 as (select * from emp2)\n"
        + "select * from emp2")
        .type(EMP_RECORD_TYPE);

    // self-reference is not ok, even in table not used
    sql("with emp2 as (select * from emp),\n"  // 创建SQL测试：解析并验证SQL语句
        + " emp3 as (select * from ^emp3^)\n"
        + "values (1)")
        .fails("Object 'EMP3' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // self-reference not ok
    sql("with emp2 as (select * from ^emp2^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2 where false")
        .fails("Object 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // refer to 2 previous tables, not just immediately preceding
    sql("with emp2 as (select * from emp),\n"  // 创建SQL测试：解析并验证SQL语句
        + " dept2 as (select * from dept),\n"
        + " empDept as (select emp2.empno, dept2.deptno from dept2 join emp2 using (deptno))\n"
        + "select 1 as uno from empDept")
        .type("RecordType(INTEGER NOT NULL UNO) NOT NULL");
  }

  /** Tests the {@code WITH} clause with UNION. */
  @Test void testWithUnion() {  // 测试方法：testWithUnion
    // nested WITH (parentheses required - and even with parentheses SQL
    // standard doesn't allow sub-query to have WITH)
    sql("with emp2 as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from emp2 union all select * from emp")
        .type(EMP_RECORD_TYPE);
  }

  /** Tests the {@code WITH} clause and column aliases. */
  @Test void testWithColumnAlias() {  // 测试方法：testWithColumnAlias
    sql("with w(x, y) as (select * from dept)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from w")
        .type("RecordType(INTEGER NOT NULL X, VARCHAR(10) NOT NULL Y) NOT NULL");
    sql("with w(x, y) as (select * from dept)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from w, w as w2")
        .type("RecordType(INTEGER NOT NULL X, VARCHAR(10) NOT NULL Y, "
            + "INTEGER NOT NULL X0, VARCHAR(10) NOT NULL Y0) NOT NULL");
    sql("with w(x, y) as (select * from dept)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select ^deptno^ from w")
        .fails("Column 'DEPTNO' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("with w(x, ^x^) as (select * from dept)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from w")
        .fails("Duplicate name 'X' in column list");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Tests the {@code WITH} clause in sub-queries. */
  @Test void testWithSubQuery() {  // 测试方法：testWithSubQuery
    // nested WITH (parentheses required - and even with parentheses SQL
    // standard doesn't allow sub-query to have WITH)
    sql("with emp2 as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "(\n"
        + "  with dept2 as (select * from dept)\n"
        + "  (\n"
        + "    with empDept as (select emp2.empno, dept2.deptno from dept2 join emp2 using (deptno))\n"
        + "    select 1 as uno from empDept))")
        .type("RecordType(INTEGER NOT NULL UNO) NOT NULL");

    // WITH inside WHERE can see enclosing tables
    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "where exists (\n"
        + "  with dept2 as (select * from dept where dept.deptno >= emp.deptno)\n"
        + "  select 1 from dept2 where deptno <= emp.deptno)")
        .type(EMP_RECORD_TYPE);

    // WITH inside FROM cannot see enclosing tables
    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (\n"
        + "  with dept2 as (select * from dept where dept.deptno >= ^emp^.deptno)\n"
        + "  select * from dept2) as d on true")
        .fails("Table 'EMP' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // as above, using USING
    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (\n"
        + "  with dept2 as (select * from dept where dept.deptno >= ^emp^.deptno)\n"
        + "  select * from dept2) as d using (deptno)")
        .fails("Table 'EMP' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // WITH inside FROM
    sql("select e.empno, d.* from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (\n"
        + "  with dept2 as (select * from dept where dept.deptno > 10)\n"
        + "  select deptno, 1 as uno from dept2) as d using (deptno)")
        .type("RecordType(INTEGER NOT NULL EMPNO,"
            + " INTEGER NOT NULL DEPTNO,"
            + " INTEGER NOT NULL UNO) NOT NULL");

    sql("select ^e^.empno, d.* from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (\n"
        + "  with dept2 as (select * from dept where dept.deptno > 10)\n"
        + "  select deptno, 1 as uno from dept2) as d using (deptno)")
        .fails("Table 'E' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testWithOrderAgg() {  // 测试方法：testWithOrderAgg
    sql("select count(*) from emp order by count(*)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("with q as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select count(*) from q group by deptno order by count(*)").ok();  // 断言验证：期望SQL验证通过
    sql("with q as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select count(*) from q order by count(*)").ok();  // 断言验证：期望SQL验证通过

    // ORDER BY on UNION would produce a similar parse tree,
    // SqlOrderBy(SqlUnion(SqlSelect ...)), but is not valid SQL.
    sql("select count(*) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "union all\n"
        + "select count(*) from emp\n"
        + "order by ^count(*)^")
        .fails("Aggregate expression is illegal in ORDER BY clause of non-aggregating SELECT");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4092">[CALCITE-4092]
   * NPE using WITH clause without a corresponding SELECT FROM</a>. */
  @Test void testWithNotSelected() {  // 测试方法：testWithNotSelected
    final String sql = "with emp2 as (select max(empno) as empno from emp)\n"
        + "select * from emp where empno < ^emp2^.empno";
    sql(sql).fails("Table 'EMP2' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /**
   * Tests a large scalar expression, which will expose any O(n^2) algorithms
   * lurking in the validation process.
   */
  @Test void testLarge() {  // 测试方法：testLarge
    checkLarge(700, sql -> sql(sql).ok());  // 断言验证：期望SQL验证通过
  }

  static void checkLarge(int x, Consumer<String> f) {
    if (System.getProperty("os.name").startsWith("Windows")) {
      // NOTE jvs 1-Nov-2006:  Default thread stack size
      // on Windows is too small, so avoid stack overflow
      x /= 3;
    }

    // E.g. large = "deptno * 1 + deptno * 2 + deptno * 3".
    String large = list(" + ", "deptno * ", x);
    f.accept("select " + large + "from emp");
    f.accept("select distinct " + large + "from emp");
    f.accept("select " + large + " from emp " + "group by deptno");
    f.accept("select * from emp where " + large + " > 5");
    f.accept("select * from emp order by " + large + " desc");
    f.accept("select " + large + " from emp order by 1");
    f.accept("select distinct " + large + " from emp order by " + large);

    // E.g. "in (0, 1, 2, ...)"
    f.accept("select * from emp where deptno in (" + list(", ", "", x) + ")");

    // E.g. "where x = 1 or x = 2 or x = 3 ..."
    f.accept("select * from emp where " + list(" or ", "deptno = ", x));

    // E.g. "select x1, x2 ... from (
    // select 'a' as x1, 'a' as x2, ... from emp union
    // select 'bb' as x1, 'bb' as x2, ... from dept)"
    f.accept("select " + list(", ", "x", x)
        + " from (select " + list(", ", "'a' as x", x) + " from emp "
        + "union all select " + list(", ", "'bb' as x", x) + " from dept)");
  }

  private static String list(String sep, String before, int count) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < count; i++) {
      if (i > 0) {
        buf.append(sep);
      }
      buf.append(before).append(i);
    }
    return buf.toString();
  }

  @Test void testAbstractConformance()
      throws InvocationTargetException, IllegalAccessException {
    final SqlAbstractConformance c0 = new SqlAbstractConformance() {
    };
    final SqlConformance c1 = SqlConformanceEnum.DEFAULT;
    for (Method method : SqlConformance.class.getMethods()) {
      final Object o0 = method.invoke(c0);
      final Object o1 = method.invoke(c1);
      assertThat(method.toString(), Objects.equals(o0, o1), is(true));
    }
  }

  @Test void testUserDefinedConformance() {  // 测试方法：testUserDefinedConformance
    final SqlConformance custom =
        new SqlDelegatingConformance(SqlConformanceEnum.DEFAULT) {
          public boolean isBangEqualAllowed() {
            return true;
          }
        };

    // Our conformance behaves differently from ORACLE_10 for FROM-less query.
    sql("^select 2+2^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(custom)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.DEFAULT)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .fails("SELECT must have a FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // Our conformance behaves like ORACLE_10 for "!=" operator.
    sql("select * from (values 1) where 1 ^!=^ 2")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(custom)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.DEFAULT)
        .fails("Bang equal '!=' is not allowed under the current SQL conformance level")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .ok();  // 断言验证：期望SQL验证通过

    sql("select * from (values 1) where 1 ^!=^ any (2, 3)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(custom)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.DEFAULT)
        .fails("Bang equal '!=' is not allowed under the current SQL conformance level")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testOrder() {  // 测试方法：testOrder
    final SqlConformance conformance = fixture().conformance();
    sql("select empno as x from emp order by empno").ok();  // 创建SQL测试：解析并验证SQL语句

    // invalid use of 'asc'
    sql("select empno, sal from emp order by ^asc^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ASC' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // In sql92, empno is obscured by the alias.
    // Otherwise valid.
    // Checked Oracle10G -- is it valid.
    sql("select empno as x from emp order by empno")  // 创建SQL测试：解析并验证SQL语句
        .failsIf(conformance.isSortByAliasObscures(),
            "unknown column empno");

    // valid in oracle and pre-99 sql,
    // invalid in sql:2003
    sql("select empno as x from emp order by ^x^")  // 创建SQL测试：解析并验证SQL语句
        .failsIf(!conformance.isSortByAlias(),
            "Column 'X' not found in any table");

    // invalid in oracle and pre-99;
    // valid from sql:99 onwards (but sorting by constant achieves
    // nothing!)
    sql("select empno as x from emp order by ^10^")  // 创建SQL测试：解析并验证SQL语句
        .failsIf(conformance.isSortByOrdinal(),
            "Ordinal out of range");

    // Has different meanings in different dialects (which makes it very
    // confusing!) but is always valid.
    sql("select empno + 1 as empno from emp order by empno").ok();  // 创建SQL测试：解析并验证SQL语句

    // Always fails
    sql("select empno as x from emp, dept order by ^deptno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'DEPTNO' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select empno + 1 from emp order by deptno asc, empno + 1 desc").ok();  // 创建SQL测试：解析并验证SQL语句

    // Alias 'deptno' is closer in scope than 'emp.deptno'
    // and 'dept.deptno', and is therefore not ambiguous.
    // Checked Oracle10G -- it is valid.
    // Ambiguous in SQL:2003
    sql("select empno as deptno from emp, dept order by deptno")  // 创建SQL测试：解析并验证SQL语句
        .failsIf(!conformance.isSortByAlias(),
            "col ambig");

    sql("select deptno from dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select empno from emp\n"
        + "order by deptno").ok();  // 断言验证：期望SQL验证通过

    sql("select deptno from dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select empno from emp\n"
        + "order by ^empno^")
        .fails("Column 'EMPNO' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // invalid in oracle and pre-99
    sql("select deptno from dept\n"  // 创建SQL测试：解析并验证SQL语句
        + "union\n"
        + "select empno from emp\n"
        + "order by ^10^")
        .failsIf(conformance.isSortByOrdinal(),
            "Ordinal out of range");

    // Sort by scalar sub-query
    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by (select name from dept where deptno = emp.deptno)").ok();  // 断言验证：期望SQL验证通过
    sql("select * from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by (select name from dept where deptno = emp.^foo^)")
        .fails("Column 'FOO' not found in table 'EMP'");  // 断言验证：期望SQL验证失败并检查错误消息

    // REVIEW jvs 10-Apr-2008:  I disabled this because I don't
    // understand what it means; see
    // testAggregateInOrderByFails for the discrimination I added
    // (SELECT should be aggregating for this to make sense).
/*
        // Sort by aggregate. Oracle allows this.
        check("select 1 from emp order by sum(sal)");
*/

    // ORDER BY and SELECT *
    sql("select * from emp order by empno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp order by ^nonExistent^, deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'NONEXISTENT' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // Overriding expression has different type.
    sql("select 'foo' as empno from emp order by ^empno + 5^")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply '\\+' to arguments of type '<CHAR\\(3\\)> \\+ <INTEGER>'\\..*");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select 'foo' as empno from emp order by empno + 5").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  /** Tests that you can reference a column alias in the ORDER BY clause if
   * {@link SqlConformance#isSortByAlias()}. */
  @Test void testOrderByAlias() {  // 测试方法：testOrderByAlias
    sql("select count(*) as total from emp order by ^total^")  // 创建SQL测试：解析并验证SQL语句
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .ok()  // 断言验证：期望SQL验证通过
        .withConformance(SqlConformanceEnum.STRICT_2003)
        .fails("Column 'TOTAL' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testOrderJoin() {  // 测试方法：testOrderJoin
    sql("select * from emp as e, dept as d order by e.empno").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-633">[CALCITE-633]
   * WITH ... ORDER BY cannot find table</a>. */
  @Test void testWithOrder() {  // 测试方法：testWithOrder
    sql("with e as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from e as e1 order by e1.empno").ok();  // 断言验证：期望SQL验证通过
    sql("with e as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from e as e1, e as e2 order by e1.empno").ok();  // 断言验证：期望SQL验证通过
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-662">[CALCITE-662]
   * Query validation fails when an ORDER BY clause is used with WITH
   * CLAUSE</a>. */
  @Test void testWithOrderInParentheses() {  // 测试方法：testWithOrderInParentheses
    sql("with e as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "(select e.empno from e order by e.empno)").ok();  // 断言验证：期望SQL验证通过
    sql("with e as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "(select e.empno from e order by 1)").ok();  // 断言验证：期望SQL验证通过
    sql("with e as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "(select ee.empno from e as ee order by ee.deptno)").ok();  // 断言验证：期望SQL验证通过
    // worked even before CALCITE-662 fixed
    sql("with e as (select * from emp)\n"  // 创建SQL测试：解析并验证SQL语句
        + "(select e.empno from e)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testOrderUnion() {  // 测试方法：testOrderUnion
    sql("select empno, sal from emp "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select deptno, deptno from dept "
        + "order by empno").ok();  // 断言验证：期望SQL验证通过

    sql("select empno, sal from emp "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select deptno, deptno from dept "
        + "order by ^asc^")
        .fails("Column 'ASC' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // name belongs to emp but is not projected so cannot sort on it
    sql("select empno, sal from emp "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select deptno, deptno from dept "
        + "order by ^ename^ desc")
        .fails("Column 'ENAME' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // empno is not an alias in the first select in the union
    sql("select deptno, deptno as no2 from dept "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select empno, sal from emp "
        + "order by deptno asc, ^empno^")
        .fails("Column 'EMPNO' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // ordinals ok
    sql("select empno, sal from emp "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select deptno, deptno from dept "
        + "order by 2").ok();  // 断言验证：期望SQL验证通过

    // ordinal out of range -- if 'order by <ordinal>' means something in
    // this dialect
    final SqlConformance conformance = fixture().conformance();
    if (conformance.isSortByOrdinal()) {
      sql("select empno, sal from emp "  // 创建SQL测试：解析并验证SQL语句
          + "union all "
          + "select deptno, deptno from dept "
          + "order by ^3^")
          .fails("Ordinal out of range");  // 断言验证：期望SQL验证失败并检查错误消息
    }

    // Expressions made up of aliases are OK.
    // (This is illegal in Oracle 10G.)
    sql("select empno, sal from emp "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select deptno, deptno from dept "
        + "order by empno * sal + 2").ok();  // 断言验证：期望SQL验证通过

    sql("select empno, sal from emp "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select deptno, deptno from dept "
        + "order by 'foobar'").ok();  // 断言验证：期望SQL验证通过
  }

  /**
   * Tests validation of the ORDER BY clause when GROUP BY is present.
   */
  @Test void testOrderGroup() {  // 测试方法：testOrderGroup
    // Group by
    sql("select 1 from emp group by deptno order by ^empno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // order by can contain aggregate expressions
    sql("select empno from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by empno, deptno "
        + "order by deptno * sum(sal + 2)").ok();  // 断言验证：期望SQL验证通过

    // Having

    sql("select sum(sal) from emp having count(*) > 3 order by ^empno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select sum(sal) from emp having count(*) > 3 order by sum(deptno)").ok();  // 创建SQL测试：解析并验证SQL语句

    // Select distinct

    sql("select distinct deptno from emp group by deptno order by ^empno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select distinct deptno from emp group by deptno order by deptno, ^empno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select distinct deptno from emp group by deptno order by deptno").ok();  // 创建SQL测试：解析并验证SQL语句

    // UNION of SELECT DISTINCT and GROUP BY behaves just like a UNION.
    sql("select distinct deptno from dept "  // 创建SQL测试：解析并验证SQL语句
        + "union all "
        + "select empno from emp group by deptno, empno "
        + "order by deptno").ok();  // 断言验证：期望SQL验证通过

    // order by can contain a mixture of aliases and aggregate expressions
    sql("select empno as x "  // 创建SQL测试：解析并验证SQL语句
        + "from emp "
        + "group by empno, deptno "
        + "order by x * sum(sal + 2)").ok();  // 断言验证：期望SQL验证通过

    final SqlConformance conformance = fixture().conformance();
    sql("select empno as x "  // 创建SQL测试：解析并验证SQL语句
        + "from emp "
        + "group by empno, deptno "
        + "order by empno * sum(sal + 2)")
        .failsIf(conformance.isSortByAliasObscures(), "xxxx");
  }

  /**
   * Tests validation of the aliases in GROUP BY.
   *
   * <p>Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1306">[CALCITE-1306]
   * Allow GROUP BY and HAVING to reference SELECT expressions by ordinal and
   * alias</a>.
   *
   * @see SqlConformance#isGroupByAlias()
   */
  @Test void testAliasInGroupBy() {  // 测试方法：testAliasInGroupBy
    final SqlConformanceEnum lenient = SqlConformanceEnum.LENIENT;
    final SqlConformanceEnum strict = SqlConformanceEnum.STRICT_2003;

    // Group by
    sql("select empno as e from emp group by ^e^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select empno as e from emp group by ^e^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select emp.empno as e from emp group by ^e^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select e.empno from emp as e group by e.empno")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).ok()  // 断言验证：期望SQL验证通过
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select e.empno as eno from emp as e group by ^eno^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'ENO' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select deptno as dno from emp group by cube(^dno^)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'DNO' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select deptno as dno, ename name, sum(sal) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by grouping sets (^(dno)^, (name, deptno))")
        .withConformance(strict).fails("Column 'DNO' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ename as deptno from emp as e join dept as d on "  // 创建SQL测试：解析并验证SQL语句
        + "e.deptno = d.deptno group by ^deptno^")
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select t.e, count(*) from (select empno as e from emp) t group by e")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).ok()  // 断言验证：期望SQL验证通过
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过

    // The following 2 tests have the same SQL but fail for different reasons.
    sql("select t.e, count(*) as c from "  // 创建SQL测试：解析并验证SQL语句
        + " (select empno as e from emp) t group by e,^c^")
        .withConformance(strict).fails("Column 'C' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select t.e, ^count(*)^ as c from "  // 创建SQL测试：解析并验证SQL语句
        + " (select empno as e from emp) t group by e,c")
        .withConformance(lenient).fails(ERR_AGG_IN_GROUP_BY);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select t.e, e + ^count(*)^ as c from "  // 创建SQL测试：解析并验证SQL语句
        + " (select empno as e from emp) t group by e,c")
        .withConformance(lenient).fails(ERR_AGG_IN_GROUP_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select t.e, e + ^count(*)^ as c from "  // 创建SQL测试：解析并验证SQL语句
        + " (select empno as e from emp) t group by e,2")
        .withConformance(lenient).fails(ERR_AGG_IN_GROUP_BY)  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(strict).ok();  // 断言验证：期望SQL验证通过

    sql("select deptno,(select empno + 1 from emp) eno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept group by deptno,^eno^")
        .withConformance(strict).fails("Column 'ENO' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select empno as e, deptno as e\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by ^e^")
        .withConformance(lenient).fails("Column 'E' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select empno, ^count(*)^ c from emp group by empno, c")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(lenient).fails(ERR_AGG_IN_GROUP_BY);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno + empno as d, deptno + empno + mgr from emp"  // 创建SQL测试：解析并验证SQL语句
        + " group by d,mgr")
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    // When alias is equal to one or more columns in the query then giving
    // priority to alias. But Postgres may throw ambiguous column error or give
    // priority to column name.
    sql("select count(*) from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select ename AS deptno FROM emp GROUP BY deptno) t")
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select count(*) from "  // 创建SQL测试：解析并验证SQL语句
        + "(select ename AS deptno FROM emp, dept GROUP BY deptno) t")
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select empno + deptno AS \"z\" FROM emp GROUP BY \"Z\"")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(lenient).withCaseSensitive(false).ok();  // 断言验证：期望SQL验证通过
    sql("select empno + deptno as c, ^c^ + mgr as d from emp group by c, d")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(lenient).fails("Column 'C' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    // Group by alias with strict conformance should fail.
    sql("select empno as e from emp group by ^e^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select floor(empno/2) as empno from emp group by floor(empno/2)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).ok()  // 断言验证：期望SQL验证通过
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
  }

  /**
   * Tests validation of alias in function within GROUP BY.
   *
   * @see SqlConformance#isGroupByAlias()
   */
  @Test void testAliasInFunctionWithinGroupBy() {  // 测试方法：testAliasInFunctionWithinGroupBy
    final SqlConformanceEnum lenient = SqlConformanceEnum.LENIENT;
    final SqlFunction date_add =
        SqlBasicFunction.create(SqlKind.DATE_ADD, ReturnTypes.DATE,
                OperandTypes.STRING_INTEGER)
            .withFunctionType(SqlFunctionCategory.TIMEDATE);

    sql("select date_add('2024-01-01', empno) as empno from emp group by ^year(empno)^")  // 创建SQL测试：解析并验证SQL语句
        .withOperatorTable(
            SqlOperatorTables.chain(
            SqlOperatorTables.of(date_add), SqlStdOperatorTable.instance()))
        .withConformance(lenient)
        .fails("Cannot apply 'EXTRACT' to arguments of "  // 断言验证：期望SQL验证失败并检查错误消息
            + "type 'EXTRACT\\(<INTERVAL YEAR> FROM <INTEGER>\\)'\\. "
            + "Supported form\\(s\\): "
            + "'EXTRACT\\(<DATETIME_INTERVAL> FROM <DATETIME_INTERVAL>\\)'\n"
            + "'EXTRACT\\(<DATETIME_INTERVAL> FROM <DATETIME>\\)'\n"
            + "'EXTRACT\\(<INTERVAL_DAY_TIME> FROM <INTERVAL_YEAR_MONTH>\\)'");
  }

  /**
   * Tests validation of ordinals in GROUP BY.
   *
   * @see SqlConformance#isGroupByOrdinal()
   */
  @Test void testOrdinalInGroupBy() {  // 测试方法：testOrdinalInGroupBy
    final SqlConformanceEnum lenient = SqlConformanceEnum.LENIENT;
    final SqlConformanceEnum strict = SqlConformanceEnum.STRICT_2003;

    sql("select ^empno^,deptno from emp group by 1, deptno")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^emp.empno^ as e from emp group by 1")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'EMP.EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select 2 + ^emp.empno^ + 3 as e from emp group by 1")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'EMP.EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^e.empno^ from emp as e group by 1")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'E.EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select e.empno from emp as e group by 1, empno")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).ok()  // 断言验证：期望SQL验证通过
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^e.empno^ as eno from emp as e group by 1")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'E.EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^deptno^ as dno from emp group by cube(1)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'DEPTNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select 1 as dno from emp group by cube(1)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).ok()  // 断言验证：期望SQL验证通过
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select deptno as dno, ename name, sum(sal) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by grouping sets ((1), (^name^, deptno))")
        .withConformance(strict).fails("Column 'NAME' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^e.deptno^ from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + "join dept as d on e.deptno = d.deptno group by 1")
        .withConformance(strict).fails("Expression 'E.DEPTNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^deptno^,(select empno from emp) eno from dept"  // 创建SQL测试：解析并验证SQL语句
        + " group by 1,2")
        .withConformance(strict).fails("Expression 'DEPTNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^empno^, count(*) from emp group by 1 order by 1")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^empno^ eno, count(*) from emp group by 1 order by 1")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select count(*) from (select 1 from emp"  // 创建SQL测试：解析并验证SQL语句
        + " group by substring(ename from 2 for 3))")
        .withConformance(strict).ok()  // 断言验证：期望SQL验证通过
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select deptno from emp group by deptno, ^100^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(lenient).fails("Ordinal out of range")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(strict).ok();  // 断言验证：期望SQL验证通过
    // Calcite considers integers in GROUP BY to be constants, so test passes.
    // Postgres considers them ordinals and throws out of range position error.
    sql("select deptno from emp group by ^100^, deptno")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(lenient).fails("Ordinal out of range")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(strict).ok();  // 断言验证：期望SQL验证通过
    sql("select floor(e.deptno / 2) AS deptno from emp as e\n"  // 创建SQL测试：解析并验证SQL语句
        + "join dept as d on e.deptno = d.deptno group by ^deptno^")
        .withConformance(strict).fails("Column 'DEPTNO' is ambiguous")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5507">[CALCITE-5507]
   * HAVING alias failed when aggregate function in condition</a>. */
  @Test void testAggregateFunAndAliasInHaving() {  // 测试方法：testAggregateFunAndAliasInHaving
    final SqlConformanceEnum lenient = SqlConformanceEnum.LENIENT;
    final SqlConformanceEnum strict = SqlConformanceEnum.STRICT_2003;

    sql("select count(empno) as e from emp having ^e^ > 10 and count(empno) > 10 ")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select count(empno) as e from emp having count(empno) > 10 and count(^e^) > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).fails("Column 'E' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6939">
   * [CALCITE-6939] Add support for Lateral Column Alias</a>. */
  @Test void testAliasInSelect() {  // 测试方法：testAliasInSelect
    final SqlConformance leftRoRight = new SqlAbstractConformance() {
      @Override public SelectAliasLookup isSelectAlias() {
        return SelectAliasLookup.LEFT_TO_RIGHT;
      }
      @Override public boolean isGroupByAlias() {
        return true;
      }
    };
    final SqlConformance any = new SqlAbstractConformance() {
      @Override public SelectAliasLookup isSelectAlias() {
        return SelectAliasLookup.ANY;
      }
    };

    // Standard conformance: error
    sql("select 1 AS x, ^x^ as Y")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'X' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    // same query, conformance that allows select to use its own aliases
    sql("select 1 AS x, x as Y")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(leftRoRight)
        .ok();  // 断言验证：期望SQL验证通过
    // column used before it is defined
    sql("select ^x^ as Y, 1 AS x")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(leftRoRight)
        .fails("Column 'X' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select x as Y, 1 AS x")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(any)
        .ok();  // 断言验证：期望SQL验证通过
    // multiple aliases in the same select
    sql("select 1 AS x, 2 as x, ^x^ AS y")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(leftRoRight)
        .fails("Column 'X' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
    // multiple aliases in the same select
    sql("select 1 AS x, 2 as x, ^x^ AS y")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(any)
        .fails("Column 'X' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
    // Circular dependency
    sql("select ^x^ as y, y + 1 AS x")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(any)
        .fails("The definition of column 'X' depends on itself through the following columns: "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'X', 'Y'");
    // Using a FROM clause with a table
    sql("select empno AS x, x as y FROM emp")  // 创建SQL测试：解析并验证SQL语句
        .withValidatorIdentifierExpansion(true)
        .withConformance(leftRoRight)
        .ok();  // 断言验证：期望SQL验证通过
    // aliases are visible in group by
    sql("select empno AS x, x as y FROM emp GROUP BY y")  // 创建SQL测试：解析并验证SQL语句
        .withValidatorIdentifierExpansion(true)
        .withConformance(leftRoRight)
        .ok();  // 断言验证：期望SQL验证通过
    // the following is not legal in any conformance
    // because 'empno' is visible in the FROM clause
    sql("select count(empno + deptno) as empno, ^empno^ as x from emp"  // 创建SQL测试：解析并验证SQL语句
            + " group by empno + deptno")
            .fails("Expression 'EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
            .withConformance(leftRoRight)
            .fails("Expression 'EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
            .withConformance(any)
            .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /**
   * Tests validation of the aliases in HAVING.
   *
   * @see SqlConformance#isHavingAlias()
   */
  @Test void testAliasInHaving() {  // 测试方法：testAliasInHaving
    final SqlConformanceEnum lenient = SqlConformanceEnum.LENIENT;
    final SqlConformanceEnum strict = SqlConformanceEnum.STRICT_2003;

    sql("select count(empno) as e from emp having ^e^ > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select emp.empno as e from emp group by ^e^ having e > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select emp.empno as e from emp group by empno having ^e^ > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    sql("select ^e.empno^ from emp as e group by 1 having e.empno > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'E.EMPNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    // When alias is equal to one or more columns in the query then giving
    // priority to alias, but PostgreSQL throws ambiguous column error or gives
    // priority to column name.
    sql("select count(empno) as deptno from emp having ^deptno^ > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Expression 'DEPTNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
    // Alias in aggregate is not allowed.
    sql("select empno as e from emp group by empno having max(^e^) > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).fails("Column 'E' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(empno) as e from emp having ^e^ > 10")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(strict).fails("Column 'E' not found in any table")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(lenient).ok();  // 断言验证：期望SQL验证通过
  }

  /**
   * Tests validation of the ORDER BY clause when DISTINCT is present.
   */
  @Test void testOrderDistinct() {  // 测试方法：testOrderDistinct
    // Distinct on expressions with attempts to order on a column in
    // the underlying table
    sql("select distinct cast(empno as bigint) "  // 创建SQL测试：解析并验证SQL语句
        + "from emp order by ^empno^")
        .fails("Expression 'EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select distinct cast(empno as bigint) "  // 创建SQL测试：解析并验证SQL语句
        + "from emp order by ^emp.empno^")
        .fails("Expression 'EMP\\.EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select distinct cast(empno as bigint) as empno "  // 创建SQL测试：解析并验证SQL语句
        + "from emp order by ^emp.empno^")
        .fails("Expression 'EMP\\.EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select distinct cast(empno as bigint) as empno "  // 创建SQL测试：解析并验证SQL语句
        + "from emp as e order by ^e.empno^")
        .fails("Expression 'E\\.EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // These tests are primarily intended to test cases where sorting by
    // an alias is allowed.  But for instances that don't support sorting
    // by alias, the tests also verify that a proper exception is thrown.
    final SqlConformance conformance = fixture().conformance();
    sql("select distinct cast(empno as bigint) as empno "  // 创建SQL测试：解析并验证SQL语句
        + "from emp order by ^empno^")
        .failsIf(!conformance.isSortByAlias(),
            "Expression 'EMPNO' is not in the select clause");
    sql("select distinct cast(empno as bigint) as eno "  // 创建SQL测试：解析并验证SQL语句
        + "from emp order by ^eno^")
        .failsIf(!conformance.isSortByAlias(),
            "Column 'ENO' not found in any table");
    sql("select distinct cast(empno as bigint) as empno "  // 创建SQL测试：解析并验证SQL语句
        + "from emp e order by ^empno^")
        .failsIf(!conformance.isSortByAlias(),
            "Expression 'EMPNO' is not in the select clause");

    // Distinct on expressions, sorting using ordinals.
    if (conformance.isSortByOrdinal()) {
      sql("select distinct cast(empno as bigint) from emp order by 1").ok();  // 创建SQL测试：解析并验证SQL语句
      sql("select distinct cast(empno as bigint) as empno "  // 创建SQL测试：解析并验证SQL语句
          + "from emp order by 1").ok();  // 断言验证：期望SQL验证通过
      sql("select distinct cast(empno as bigint) as empno "  // 创建SQL测试：解析并验证SQL语句
          + "from emp as e order by 1").ok();  // 断言验证：期望SQL验证通过
    }

    // Distinct on expressions with ordering on expressions as well
    sql("select distinct cast(empno as varchar(10)) from emp "  // 创建SQL测试：解析并验证SQL语句
        + "order by cast(empno as varchar(10))").ok();  // 断言验证：期望SQL验证通过
    sql("select distinct cast(empno as varchar(10)) as eno from emp "  // 创建SQL测试：解析并验证SQL语句
        + " order by upper(^eno^)")
        .failsIf(!conformance.isSortByAlias(),
            "Column 'ENO' not found in any table");

    // Test case for [CALCITE-5653], order by on aggregate will fail when there is an implicit cast
    // and IdentifierExpansion is off
    sql("select distinct sum(deptno + '1') from dept order by 1")  // 创建SQL测试：解析并验证SQL语句
        .withValidatorIdentifierExpansion(false)
        .ok();  // 断言验证：期望SQL验证通过
  }

  /**
   * Tests validation of the ORDER BY clause when DISTINCT and GROUP BY are
   * present.
   *
   * <p>Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-634">[CALCITE-634]
   * Allow ORDER BY aggregate function in SELECT DISTINCT, provided that it
   * occurs in SELECT clause</a>.
   */
  @Test void testOrderGroupDistinct() {  // 测试方法：testOrderGroupDistinct
    // Order by an aggregate function,
    // which exists in select-clause with distinct being present
    sql("select distinct count(empno) AS countEMPNO from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by empno\n"
        + "order by 1").ok();  // 断言验证：期望SQL验证通过

    sql("select distinct count(empno) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by empno\n"
        + "order by 1").ok();  // 断言验证：期望SQL验证通过

    sql("select distinct count(empno) AS countEMPNO from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by empno\n"
        + "order by count(empno)").ok();  // 断言验证：期望SQL验证通过

    sql("select distinct count(empno) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by empno\n"
        + "order by count(empno)").ok();  // 断言验证：期望SQL验证通过

    sql("select distinct count(empno) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by empno\n"
        + "order by count(empno) desc").ok();  // 断言验证：期望SQL验证通过

    sql("SELECT DISTINCT deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "ORDER BY deptno, ^sum(empno)^")
        .fails("Aggregate expression is illegal in ORDER BY clause of non-aggregating SELECT");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "GROUP BY deptno ORDER BY deptno, ^sum(empno)^")
        .fails("Expression 'SUM\\(`EMPNO`\\)' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT deptno, min(empno) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "GROUP BY deptno ORDER BY deptno, ^sum(empno)^")
        .fails("Expression 'SUM\\(`EMPNO`\\)' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT deptno, sum(empno) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "GROUP BY deptno ORDER BY deptno, sum(empno)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testGroup() {  // 测试方法：testGroup
    sql("select empno from emp where ^sum(sal)^ > 50")  // 创建SQL测试：解析并验证SQL语句
        .fails("Aggregate expression is illegal in WHERE clause");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^empno^ from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^*^ from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMP\\.EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // If we're grouping on ALL columns, 'select *' is ok.
    // Checked on Oracle10G.
    sql("select * from (select empno,deptno from emp) group by deptno,empno").ok();  // 创建SQL测试：解析并验证SQL语句

    // This query tries to reference an agg expression from within a
    // sub-query as a correlating expression, but the SQL syntax rules say
    // that the agg function SUM always applies to the current scope.
    // As it happens, the query is valid.
    sql("select deptno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by deptno\n"
        + "having exists (select sum(emp.sal) > 10 from (values(true)))").ok();  // 断言验证：期望SQL验证通过

    // if you reference a column from a sub-query, it must be a group col
    sql("select deptno "  // 创建SQL测试：解析并验证SQL语句
        + "from emp "
        + "group by deptno "
        + "having exists (select 1 from (values(true)) where emp.deptno = 10)").ok();  // 断言验证：期望SQL验证通过

    // Needs proper error message text and error markers in query
    if (TODO) {
      sql("select deptno "  // 创建SQL测试：解析并验证SQL语句
          + "from emp "
          + "group by deptno "
          + "having exists (select 1 from (values(true)) where emp.empno = 10)")
          .fails("xx");  // 断言验证：期望SQL验证失败并检查错误消息
    }

    // constant expressions
    sql("select cast(1 as integer) + 2 from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select localtime, deptno + 3 from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-886">[CALCITE-886]
   * System functions in GROUP BY clause</a>. */
  @Test void testGroupBySystemFunction() {  // 测试方法：testGroupBySystemFunction
    sql("select CURRENT_USER from emp group by CURRENT_USER").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select CURRENT_USER from emp group by rollup(CURRENT_USER)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select CURRENT_USER from emp group by rollup(CURRENT_USER, ^x^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'X' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select CURRENT_USER from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testGroupingSets() {  // 测试方法：testGroupingSets
    sql("select count(1), ^empno^ from emp group by grouping sets (deptno)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, ename, sum(sal) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by grouping sets ((deptno), (ename, deptno))\n"
        + "order by 2").ok();  // 断言验证：期望SQL验证通过

    // duplicate (and heavily nested) GROUPING SETS
    sql("select sum(sal) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno,\n"
        + "  grouping sets (deptno,\n"
        + "    grouping sets (deptno, ename),\n"
        + "      (ename)),\n"
        + "  ()").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testRollup() {  // 测试方法：testRollup
    // DEPTNO is not null in database, but rollup introduces nulls
    sql("select deptno, count(*) as c, sum(sal) as s\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by rollup(deptno)")
        .ok()  // 断言验证：期望SQL验证通过
        .type("RecordType(INTEGER DEPTNO, BIGINT NOT NULL C, INTEGER NOT NULL S) NOT NULL");

    // EMPNO stays NOT NULL because it is not rolled up
    sql("select deptno, empno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by empno, rollup(deptno)")
        .ok()  // 断言验证：期望SQL验证通过
        .type("RecordType(INTEGER DEPTNO, INTEGER NOT NULL EMPNO) NOT NULL");

    // DEPTNO stays NOT NULL because it is not rolled up
    sql("select deptno, empno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by rollup(empno), deptno")
        .ok()  // 断言验证：期望SQL验证通过
        .type("RecordType(INTEGER NOT NULL DEPTNO, INTEGER EMPNO) NOT NULL");

    // empno becomes NULL because it is rolled up, and so does empno + 1.
    sql("select empno, empno + 1 as e1\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by rollup(empno)")
        .ok()  // 断言验证：期望SQL验证通过
        .type("RecordType(INTEGER EMPNO, INTEGER E1) NOT NULL");
  }

  @Test void testGroupByCorrelatedColumn() {  // 测试方法：testGroupByCorrelatedColumn
    // This is not sql 2003 standard; see sql2003 part2,  7.9
    // But the extension seems harmless.
    final String sql = "select count(*)\n"
        + "from emp\n"
        + "where exists (select count(*) from dept group by emp.empno)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testGroupExpressionEquivalence() {  // 测试方法：testGroupExpressionEquivalence
    // operator equivalence
    sql("select empno + 1 from emp group by empno + 1").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select 1 + ^empno^ from emp group by empno + 1")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // datatype equivalence
    sql("select cast(empno as VARCHAR(10)) from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by cast(empno as VARCHAR(10))").ok();  // 断言验证：期望SQL验证通过
    sql("select cast(^empno^ as VARCHAR(11)) from emp group by cast(empno as VARCHAR(10))")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testGroupExpressionEquivalenceId() {  // 测试方法：testGroupExpressionEquivalenceId
    // identifier equivalence
    sql("select case empno when 10 then deptno else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then deptno else null end").ok();  // 断言验证：期望SQL验证通过

    // matches even when one column is qualified (checked on Oracle10.1)
    sql("select case empno when 10 then deptno else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then emp.deptno else null end").ok();  // 断言验证：期望SQL验证通过
    sql("select case empno when 10 then deptno else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case emp.empno when 10 then emp.deptno else null end").ok();  // 断言验证：期望SQL验证通过
    sql("select case emp.empno when 10 then deptno else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then emp.deptno else null end").ok();  // 断言验证：期望SQL验证通过

    // emp.deptno is different to dept.deptno (even though there is an '='
    // between them)
    sql("select case ^emp.empno^ when 10 then emp.deptno else null end "  // 创建SQL测试：解析并验证SQL语句
        + "from emp join dept on emp.deptno = dept.deptno "
        + "group by case emp.empno when 10 then dept.deptno else null end")
        .fails("Expression 'EMP\\.EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Disabled("todo: enable when correlating variables work")
  void testGroupExpressionEquivalenceCorrelated() {
    // dname comes from dept, so it is constant within the sub-query, and
    // is so is a valid expr in a group-by query
    sql("select * from dept where exists ("  // 创建SQL测试：解析并验证SQL语句
        + "select dname from emp group by empno)").ok();  // 断言验证：期望SQL验证通过
    sql("select * from dept where exists ("  // 创建SQL测试：解析并验证SQL语句
        + "select dname + empno + 1 from emp group by empno, dept.deptno)").ok();  // 断言验证：期望SQL验证通过
  }

  @Disabled("todo: enable when params are implemented")
  void testGroupExpressionEquivalenceParams() {
    sql("select cast(? as integer) from emp group by cast(? as integer)").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testGroupExpressionEquivalenceLiteral() {  // 测试方法：testGroupExpressionEquivalenceLiteral
    // The purpose of this test is to see whether the validator
    // regards a pair of constants as equivalent. If we just used the raw
    // constants the validator wouldn't care ('SELECT 1 FROM emp GROUP BY
    // 2' is legal), so we combine a column and a constant into the same
    // CASE expression.

    // literal equivalence
    sql("select case empno when 10 then date '1969-04-29' else null end\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by case empno when 10 then date '1969-04-29' else null end").ok();  // 断言验证：期望SQL验证通过

    // this query succeeds in oracle 10.1 because 1 and 1.0 have the same
    // type
    sql("select case ^empno^ when 10 then 1 else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then 1.0 else null end")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // 3.1415 and 3.14150 are different literals (I don't care either way)
    sql("select case ^empno^ when 10 then 3.1415 else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then 3.14150 else null end")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // 3 and 03 are the same literal (I don't care either way)
    sql("select case empno when 10 then 03 else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then 3 else null end").ok();  // 断言验证：期望SQL验证通过
    sql("select case ^empno^ when 10 then 1 else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then 2 else null end")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select case empno when 10 then timestamp '1969-04-29 12:34:56.0'\n"  // 创建SQL测试：解析并验证SQL语句
        + "       else null end from emp\n"
        + "group by case empno when 10 then timestamp '1969-04-29 12:34:56'\n"
        + "              else null end")
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testGroupExpressionEquivalenceStringLiteral() {  // 测试方法：testGroupExpressionEquivalenceStringLiteral
    sql("select case empno when 10 then 'foo bar' else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then 'foo bar' else null end").ok();  // 断言验证：期望SQL验证通过

    if (Bug.FRG78_FIXED) {
      final String sql = "select case empno when 10\n"
          + "      then _iso-8859-1'foo bar' collate latin1$en$1\n"
          + "      else null end\n"
          + "from emp\n"
          + "group by case empno when 10\n"
          + "      then _iso-8859-1'foo bar' collate latin1$en$1\n"
          + "      else null end";
      sql(sql).ok();  // 断言验证：期望SQL验证通过
    }

    sql("select case ^empno^ when 10 then _iso-8859-1'foo bar' else null end from emp "  // 创建SQL测试：解析并验证SQL语句
        + "group by case empno when 10 then _UTF16'foo bar' else null end")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    if (Bug.FRG78_FIXED) {
      sql("select case ^empno^ when 10 then 'foo bar' collate latin1$en$1 else null end from emp "  // 创建SQL测试：解析并验证SQL语句
          + "group by case empno when 10 then 'foo bar' collate latin1$fr$1 else null end")
          .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    }
  }

  @Test void testGroupByAliasedColumn() {  // 测试方法：testGroupByAliasedColumn
    // alias in GROUP BY query has been known to cause problems
    sql("select deptno as d, count(*) as c from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testNestedAggFails() {  // 测试方法：testNestedAggFails
    // simple case
    sql("select ^sum(max(empno))^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(ERR_NESTED_AGG);  // 断言验证：期望SQL验证失败并检查错误消息

    // should still fail with intermediate expression
    sql("select ^sum(2*max(empno))^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(ERR_NESTED_AGG);  // 断言验证：期望SQL验证失败并检查错误消息

    // make sure it fails with GROUP BY too
    sql("select ^sum(max(empno))^ from emp group by deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails(ERR_NESTED_AGG);  // 断言验证：期望SQL验证失败并检查错误消息

    // make sure it fails in HAVING too
    sql("select count(*) from emp group by deptno "  // 创建SQL测试：解析并验证SQL语句
        + "having ^sum(max(empno))^=3")
        .fails(ERR_NESTED_AGG);  // 断言验证：期望SQL验证失败并检查错误消息

    // double-nesting should fail too; bottom-up validation currently
    // causes us to flag the intermediate level
    sql("select sum(^max(min(empno))^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(ERR_NESTED_AGG);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testNestedAggOver() {  // 测试方法：testNestedAggOver
    sql("select sum(max(empno))\n"  // 创建SQL测试：解析并验证SQL语句
        + " OVER (order by ^deptno^ ROWS 2 PRECEDING)\n"
        + " from emp")
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sum(max(empno)) OVER w\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp\n"
        + " window w as (order by ^deptno^ ROWS 2 PRECEDING)")
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sum(max(empno)) OVER w2, sum(deptno) OVER w1\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno\n"
        + " window w1 as (partition by ^empno^ ROWS 2 PRECEDING),\n"
        + " w2 as (order by deptno ROWS 2 PRECEDING)")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(count(empno)) over w\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno\n"
        + "window w as (partition by deptno order by ^empno^)")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // in OVER clause with more than one level of nesting
    sql("select ^avg(sum(min(sal)))^ OVER (partition by deptno)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails(ERR_NESTED_AGG);  // 断言验证：期望SQL验证失败并检查错误消息

    // OVER clause columns not appearing in GROUP BY clause NOT OK
    sql("select avg(^sal^) OVER (), avg(count(empno)) OVER (partition by 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp")
        .fails("Expression 'SAL' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(^sal^) OVER (), avg(count(empno)) OVER (partition by deptno)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno")
        .fails("Expression 'SAL' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(deptno) OVER (partition by ^empno^),\n"  // 创建SQL测试：解析并验证SQL语句
        + " avg(count(empno)) OVER (partition by deptno)\n"
        + " from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(deptno) OVER (order by ^empno^),\n"  // 创建SQL测试：解析并验证SQL语句
        + " avg(count(empno)) OVER (partition by deptno)\n"
        + " from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by ^empno^)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by ^empno^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by ^empno^)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by ^empno^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by deptno order by ^empno^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(^sal^) OVER (),\n"  // 创建SQL测试：解析并验证SQL语句
        + " avg(count(empno)) OVER (partition by min(deptno))\n"
        + " from emp")
        .fails("Expression 'SAL' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(deptno) OVER (),\n"  // 创建SQL测试：解析并验证SQL语句
        + " avg(count(empno)) OVER (partition by deptno)"
        + " from emp group by deptno").ok();  // 断言验证：期望SQL验证通过

    // COUNT(*), PARTITION BY(CONST) with GROUP BY is OK
    sql("select avg(count(*)) OVER ()\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select count(*) OVER ()\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select count(deptno) OVER ()\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select count(deptno, deptno + 1) OVER ()\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select count(deptno, deptno + 1, ^empno^) OVER ()\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(emp.^*^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails("Unknown field '\\*'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(emp.^*^) OVER ()\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails("Unknown field '\\*'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(count(*)) OVER (partition by 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select avg(sum(sal)) OVER (partition by 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp").ok();  // 断言验证：期望SQL验证通过
    sql("select avg(sal), avg(count(empno)) OVER (partition by 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp").ok();  // 断言验证：期望SQL验证通过

    // expression is OK
    sql("select avg(sum(sal)) OVER (partition by 10 - deptno\n"  // 创建SQL测试：解析并验证SQL语句
        + "   order by deptno / 2 desc)\n"
        + "from emp group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select avg(sal),\n"  // 创建SQL测试：解析并验证SQL语句
        + " avg(count(empno)) OVER (partition by min(deptno))\n"
        + " from emp").ok();  // 断言验证：期望SQL验证通过
    sql("select avg(min(sal)) OVER (),\n"  // 创建SQL测试：解析并验证SQL语句
        + " avg(count(empno)) OVER (partition by min(deptno))\n"
        + " from emp").ok();  // 断言验证：期望SQL验证通过

    // expression involving non-GROUP column is not OK
    sql("select avg(2+^sal^) OVER (partition by deptno)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails("Expression 'SAL' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by deptno + ^empno^)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by empno + deptno)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by empno + deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select avg(sum(sal)) OVER (partition by empno + deptno + 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by empno + deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select avg(sum(sal)) OVER (partition by ^deptno^ + 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by empno + deptno")
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(empno + deptno) OVER (partition by empno + deptno + 1),\n"  // 创建SQL测试：解析并验证SQL语句
        + " count(empno + deptno) OVER (partition by empno + deptno + 1)\n"
        + " from emp group by empno + deptno").ok();  // 断言验证：期望SQL验证通过

    // expression is NOT OK (AS clause)
    sql("select sum(max(empno))\n"  // 创建SQL测试：解析并验证SQL语句
        + " OVER (order by ^deptno^ ROWS 2 PRECEDING) AS sumEmpNo\n"
        + " from emp")
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sum(max(empno)) OVER w AS sumEmpNo\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp\n"
        + " window w AS (order by ^deptno^ ROWS 2 PRECEDING)")
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(^sal^) OVER () AS avgSal,"  // 创建SQL测试：解析并验证SQL语句
        + " avg(count(empno)) OVER (partition by 1) AS avgEmpNo\n"
        + " from emp")
        .fails("Expression 'SAL' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(deptno, deptno + 1, ^empno^) OVER () AS cntDeptEmp\n"  // 创建SQL测试：解析并验证SQL语句
        + " from emp group by deptno")
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select avg(sum(sal)) OVER (partition by ^deptno^ + 1) AS avgSal\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp group by empno + deptno")
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // OVER in clause
    sql("select ^sum(max(empno) OVER (order by deptno ROWS 2 PRECEDING))^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails(ERR_NESTED_AGG);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /**
   * Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6473">[CALCITE-6473]
   * HAVING clauses may not contain window functions</a>.
   */
  @Test void testOverInHaving() {  // 测试方法：testOverInHaving
    final SqlConformanceEnum lenient = SqlConformanceEnum.LENIENT;
    final SqlConformanceEnum strict = SqlConformanceEnum.STRICT_2003;

    sql("select sum(1) over () as e from emp having ^e^ > 1")  // 创建SQL测试：解析并验证SQL语句
            .withConformance(strict)
            .fails("Column 'E' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select sum(1) over () as e from emp having ^e > 1^")  // 创建SQL测试：解析并验证SQL语句
            .withConformance(lenient)
            .fails("Window expressions are not permitted in the HAVING clause;"  // 断言验证：期望SQL验证失败并检查错误消息
                    + " use the QUALIFY clause instead");
    sql("select empno from emp group by empno having ^max(empno) OVER () > 1^")  // 创建SQL测试：解析并验证SQL语句
            .fails("Window expressions are not permitted in the HAVING clause;"  // 断言验证：期望SQL验证失败并检查错误消息
                    + " use the QUALIFY clause instead");
  }

  @Test void testAggregateInGroupByFails() {  // 测试方法：testAggregateInGroupByFails
    sql("select count(*) from emp group by ^sum(empno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails(ERR_AGG_IN_GROUP_BY);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testAggregateInNonGroupBy() {  // 测试方法：testAggregateInNonGroupBy
    sql("select count(1), ^empno^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(*) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
    sql("select count(deptno) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型

    // Even though deptno is not null, its sum may be, because emp may be empty.
    sql("select sum(deptno) from emp")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    sql("select sum(deptno) from emp group by ()")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    sql("select sum(deptno) from emp group by empno")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testAggregateInOrderByFails() {  // 测试方法：testAggregateInOrderByFails
    sql("select empno from emp order by ^sum(empno)^")  // 创建SQL测试：解析并验证SQL语句
        .fails(ERR_AGG_IN_ORDER_BY);  // 断言验证：期望SQL验证失败并检查错误消息

    // but this should be OK
    sql("select sum(empno) from emp group by deptno order by sum(empno)").ok();  // 创建SQL测试：解析并验证SQL语句

    // this should also be OK
    sql("select sum(empno) from emp order by sum(empno)").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testAggregateFilter() {  // 测试方法：testAggregateFilter
    sql("select sum(empno) filter (where deptno < 10) as s from emp")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER S) NOT NULL");
  }

  @Test void testAggregateFilterNotBoolean() {  // 测试方法：testAggregateFilterNotBoolean
    sql("select sum(empno) filter (where ^deptno + 10^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("FILTER clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testAggregateFilterInHaving() {  // 测试方法：testAggregateFilterInHaving
    sql("select sum(empno) as s from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno\n"
        + "having sum(empno) filter (where deptno < 20) > 10")
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testAggregateFilterContainsAggregate() {  // 测试方法：testAggregateFilterContainsAggregate
    sql("select sum(empno) filter (where ^count(*) < 10^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("FILTER must not contain aggregate expression");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testWithinGroup() {  // 测试方法：testWithinGroup
    sql("select deptno,\n"  // 创建SQL测试：解析并验证SQL语句
        + " collect(empno) within group(order by 1)\n"
        + "from emp\n"
        + "group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select collect(empno) within group(order by 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by ()").ok();  // 断言验证：期望SQL验证通过
    sql("select deptno,\n"  // 创建SQL测试：解析并验证SQL语句
        + " collect(empno) within group(order by deptno)\n"
        + "from emp\n"
        + "group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select deptno,\n"  // 创建SQL测试：解析并验证SQL语句
        + " collect(empno) within group(order by deptno, hiredate desc)\n"
        + "from emp\n"
        + "group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select deptno,\n"  // 创建SQL测试：解析并验证SQL语句
        + " collect(empno) within group(\n"
        + "  order by cast(deptno as varchar), hiredate desc)\n"
        + "from emp\n"
        + "group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select collect(empno) within group(order by 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by deptno").ok();  // 断言验证：期望SQL验证通过
    sql("select collect(empno) within group(order by 1)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp").ok();  // 断言验证：期望SQL验证通过
    sql("select ^power(deptno, 1) within group(order by 1)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*WITHIN GROUP not allowed with POWER function.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^collect(empno)^ within group(order by count(*))\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "group by deptno")
        .fails("WITHIN GROUP must not contain aggregate expression");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4644">[CALCITE-4644]
   * Add PERCENTILE_CONT and PERCENTILE_DISC aggregate functions</a>. */
  @Test void testPercentile() {  // 测试方法：testPercentile
    final String sql = "select\n"
        + " percentile_cont(0.25) within group (order by sal) as c,\n"
        + " percentile_disc(0.5) within group (order by sal desc) as d\n"
        + "from emp\n"
        + "group by deptno";
    sql(sql)
        .type("RecordType(INTEGER NOT NULL C, INTEGER NOT NULL D) NOT NULL");
  }

  /** Tests that {@code PERCENTILE_CONT} only allows numeric fields. */
  @Test void testPercentileContMustOrderByNumeric() {  // 测试方法：testPercentileContMustOrderByNumeric
    final String sql = "select\n"
        + " percentile_cont(0.25) within group (^order by ename^)\n"
        + "from emp";
    sql(sql)
        .fails("Invalid type 'VARCHAR' in ORDER BY clause of "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'PERCENTILE_CONT' function. Only NUMERIC types are supported");
  }

  /** Tests that {@code PERCENTILE_CONT} only allows one sort key. */
  @Test void testPercentileContMultipleOrderByFields() {  // 测试方法：testPercentileContMultipleOrderByFields
    final String sql = "select\n"
        + " percentile_cont(0.25) within group (^order by deptno, empno^)\n"
        + "from emp";
    sql(sql)
        .fails("'PERCENTILE_CONT' requires precisely one ORDER BY key");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testPercentileContFractionMustBeLiteral() {  // 测试方法：testPercentileContFractionMustBeLiteral
    final String sql = "select\n"
        + " ^percentile_cont(deptno)^ within group (order by empno)\n"
        + "from emp\n"
        + "group by deptno";
    sql(sql)
        .fails("Argument to function 'PERCENTILE_CONT' must be a literal");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testPercentileContFractionOutOfRange() {  // 测试方法：testPercentileContFractionOutOfRange
    final String sql = "select\n"
        + " ^percentile_cont(1.5)^ within group (order by deptno)\n"
        + "from emp";
    sql(sql)
        .fails("Argument to function 'PERCENTILE_CONT' must be a numeric "  // 断言验证：期望SQL验证失败并检查错误消息
            + "literal between 0 and 1");
  }

  /** Tests that {@code PERCENTILE_DISC} only allows numeric fields. */
  @Test void testPercentileDiscMustOrderByNumeric() {  // 测试方法：testPercentileDiscMustOrderByNumeric
    final String sql = "select\n"
        + " percentile_disc(0.25) within group (^order by ename^)\n"
        + "from emp";
    sql(sql)
        .fails("Invalid type 'VARCHAR' in ORDER BY clause of "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'PERCENTILE_DISC' function. Only NUMERIC types are supported");
  }

  /** Tests that {@code PERCENTILE_DISC} only allows one sort key. */
  @Test void testPercentileDiscMultipleOrderByFields() {  // 测试方法：testPercentileDiscMultipleOrderByFields
    final String sql = "select\n"
        + " percentile_disc(0.25) within group (^order by deptno, empno^)\n"
        + "from emp";
    sql(sql)
        .fails("'PERCENTILE_DISC' requires precisely one ORDER BY key");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3679">[CALCITE-3679]
   * Allow lambda expressions in SQL queries</a>. */
  @Test void testHigherOrderFunction() {  // 测试方法：testHigherOrderFunction
    final SqlValidatorFixture s = fixture()
        .withOperatorTable(MockSqlOperatorTable.standard().extend());
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> x + 1)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> y)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> char_length(x) + 1)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> cast(null as integer))").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select HIGHER_ORDER_FUNCTION2(1, () -> 0.1)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select emp.deptno, HIGHER_ORDER_FUNCTION(1, (x, deptno) -> deptno) from emp").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> char_length(x) + 1)")
        .type("RecordType(INTEGER NOT NULL EXPR$0) NOT NULL");
    s.withSql("select HIGHER_ORDER_FUNCTION2(1, () -> 0.1)")
        .type("RecordType(INTEGER NOT NULL EXPR$0) NOT NULL");

    // test for type check
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> ^x + 1^)")
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply '\\+' to arguments of type '<VARCHAR> \\+ <INTEGER>'\\..*");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> ^null^)")
        .withTypeCoercion(false)
        .fails("(?s)Illegal use of 'NULL'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> ^array[1] = x^)")
        .fails("Cannot apply '=' to arguments of type '<INTEGER ARRAY> = <VARCHAR>'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("select ^HIGHER_ORDER_FUNCTION(1, null)^")
        .fails("Cannot apply '(?s).*HIGHER_ORDER_FUNCTION' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'HIGHER_ORDER_FUNCTION\\(<INTEGER>, <NULL>\\)'.*");
    s.withSql("select ^HIGHER_ORDER_FUNCTION(1, (x, y, z) -> x + 1)^")
        .fails("Cannot apply '(?s).*HIGHER_ORDER_FUNCTION' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'HIGHER_ORDER_FUNCTION\\(<INTEGER>, <FUNCTION\\(ANY, ANY, ANY\\) -> ANY>\\)'.*");

    // test for illegal parameters
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> x + 1 + ^emp.deptno^) from emp")
        .fails("Param 'EMP\\.DEPTNO' not found in lambda expression "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'\\(`X`, `Y`\\) -> `X` \\+ 1 \\+ `EMP`\\.`DEPTNO`'");
    s.withSql("select HIGHER_ORDER_FUNCTION(1, (x, y) -> x + 1 + ^deptno^) from emp")
        .fails("Param 'DEPTNO' not found in lambda expression "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'\\(`X`, `Y`\\) -> `X` \\+ 1 \\+ `DEPTNO`'");
  }

  @Test void testPercentileFunctionsBigQuery() {  // 测试方法：testPercentileFunctionsBigQuery
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.BIG_QUERY);
    final String sql = "select\n"
        + " percentile_cont(sal, 0.25) over() as c,\n"
        + " percentile_disc(sal, 0.5) over() as d\n"
        + "from emp";
    sql(sql)
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable)
        .type("RecordType(DOUBLE NOT NULL C, INTEGER NOT NULL D) NOT NULL");
  }

  @Test void testPercentileContBigQueryFraction() {  // 测试方法：testPercentileContBigQueryFraction
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.BIG_QUERY);
    final String sql = "select\n"
        + "^percentile_cont(sal, 1.5)^ over() as c\n"
        + "from emp as x";
    sql(sql)
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable)
        .fails("Argument to function 'PERCENTILE_CONT' must be a numeric "  // 断言验证：期望SQL验证失败并检查错误消息
            + "literal between 0 and 1");
  }

  @Test void testPercentileContBigQueryAllowsNullTreatment() {  // 测试方法：testPercentileContBigQueryAllowsNullTreatment
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.BIG_QUERY);
    final String sql = "select\n"
        + "percentile_cont(sal, 1 RESPECT NULLS) over() as c\n"
        + "from emp";
    sql(sql)
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable)
        .type("RecordType(DOUBLE NOT NULL C) NOT NULL");
  }

  @Test void testPercentileDiscBigQueryFraction() {  // 测试方法：testPercentileDiscBigQueryFraction
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.BIG_QUERY);
    final String sql = "select\n"
        + "^percentile_disc(sal, 1.5)^ over() as c\n"
        + "from emp";
    sql(sql)
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable)
        .fails("Argument to function 'PERCENTILE_DISC' must be a numeric "  // 断言验证：期望SQL验证失败并检查错误消息
            + "literal between 0 and 1");
  }

  @Test void testPercentileDiscBigQueryAllowsNullTreatment() {  // 测试方法：testPercentileDiscBigQueryAllowsNullTreatment
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.BIG_QUERY);
    final String sql = "select\n"
        + "percentile_disc(sal, 1 RESPECT NULLS) over() as c\n"
        + "from emp";
    sql(sql)
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .withOperatorTable(opTable)
        .type("RecordType(INTEGER NOT NULL C) NOT NULL");
  }

  @Test void testCorrelatingVariables() {  // 测试方法：testCorrelatingVariables
    // reference to unqualified correlating column
    sql("select * from emp where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from dept where deptno = sal)").ok();  // 断言验证：期望SQL验证通过

    // reference to qualified correlating column
    sql("select * from emp where exists (\n"  // 创建SQL测试：解析并验证SQL语句
        + "select * from dept where deptno = emp.sal)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testIntervalCompare() {  // 测试方法：testIntervalCompare
    expr("interval '1' hour = interval '1' day")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' hour <> interval '1' hour")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' hour < interval '1' second")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' hour <= interval '1' minute")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' minute > interval '1' second")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' second >= interval '1' day")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' year >= interval '1' year")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' month = interval '1' year")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' month <> interval '1' month")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '1' year >= interval '1' month")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型

    wholeExpr("interval '1' second >= interval '1' year")
        .fails("(?s).*Cannot apply '>=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTERVAL SECOND> >= <INTERVAL YEAR>'.*");
    wholeExpr("interval '1' month = interval '1' day")
        .fails("(?s).*Cannot apply '=' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTERVAL MONTH> = <INTERVAL DAY>'.*");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-613">[CALCITE-613]
   * Implicitly convert strings in comparisons</a>. */
  @Test void testDateCompare() {  // 测试方法：testDateCompare
    // can convert character value to date, time, timestamp, interval
    // provided it is on one side of a comparison operator (=, <, >, BETWEEN)
    expr("date '2015-03-17' < '2015-03-18'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("date '2015-03-17' > '2015-03-18'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("date '2015-03-17' = '2015-03-18'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'2015-03-17' < date '2015-03-18'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("date '2015-03-17' between '2015-03-16' and '2015-03-19'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("date '2015-03-17' between '2015-03-16' and '2015-03'||'-19'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'2015-03-17' between date '2015-03-16' and date '2015-03-19'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("date '2015-03-17' between date '2015-03-16' and '2015-03-19'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("date '2015-03-17' between '2015-03-16' and date '2015-03-19'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("time '12:34:56' < '12:34:57'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("timestamp '2015-03-17 12:34:56' < '2015-03-17 12:34:57'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("interval '2' hour < '2:30'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型

    // can convert to exact and approximate numeric
    expr("123 > '72'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("12.3 > '7.2'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型

    // can convert to boolean
    expr("true = 'true'")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("^true and 'true'^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("Cannot apply 'AND' to arguments of type '<BOOLEAN> AND <CHAR\\(4\\)>'\\..*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testOverlaps() {  // 测试方法：testOverlaps
    expr("(date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + " overlaps (date '1-2-3', date '1-2-3')")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("period (date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + " overlaps period (date '1-2-3', date '1-2-3')")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("(date '1-2-3', date '1-2-3') overlaps (date '1-2-3', interval '1' "  // 创建表达式测试：解析并验证SQL表达式
        + "year)").ok();  // 断言验证：期望SQL验证通过
    expr("(time '1:2:3', interval '1' second) overlaps (time '23:59:59', time"  // 创建表达式测试：解析并验证SQL表达式
        + " '1:2:3')").ok();  // 断言验证：期望SQL验证通过
    expr("(timestamp '1-2-3 4:5:6', timestamp '1-2-3 4:5:6' ) overlaps "  // 创建表达式测试：解析并验证SQL表达式
        + "(timestamp '1-2-3 4:5:6', interval '1 2:3:4.5' day to second)").ok();  // 断言验证：期望SQL验证通过

    wholeExpr("(timestamp '1-2-3 4:5:6', timestamp '1-2-3 4:5:6' )\n"
        + " overlaps (time '4:5:6', interval '1 2:3:4.5' day to second)")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type .*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("(time '4:5:6', timestamp '1-2-3 4:5:6' )\n"
        + " overlaps (time '4:5:6', interval '1 2:3:4.5' day to second)")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type .*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("(time '4:5:6', time '4:5:6' )\n"
        + " overlaps (time '4:5:6', date '1-2-3')")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type .*");  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("1 overlaps 2")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'<INTEGER> OVERLAPS <INTEGER>'\\. Supported form.*");

    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or (date '1-2-3', date '1-2-3')\n"
        + "   overlaps (date '1-2-3', date '1-2-3')\n"
        + "or false")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    // row with 3 arguments as left argument to overlaps
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or ^(date '1-2-3', date '1-2-3', date '1-2-3')\n"
        + "   overlaps (date '1-2-3', date '1-2-3')^\n"
        + "or false")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type .*");  // 断言验证：期望SQL验证失败并检查错误消息
    // row with 3 arguments as right argument to overlaps
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or ^(date '1-2-3', date '1-2-3')\n"
        + "  overlaps (date '1-2-3', date '1-2-3', date '1-2-3')^\n"
        + "or false")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type .*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("^period (date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + "   overlaps (date '1-2-3', date '1-2-3', date '1-2-3')^")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type .*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or ^(1, 2) overlaps (2, 3)^\n"
        + "or false")
        .fails("(?s).*Cannot apply 'OVERLAPS' to arguments of type .*");  // 断言验证：期望SQL验证失败并检查错误消息

    // Other operators with similar syntax
    String[] ops = {
        "overlaps", "contains", "equals", "precedes", "succeeds",
        "immediately precedes", "immediately succeeds"
    };
    for (String op : ops) {
      expr("period (date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
          + " " + op + " period (date '1-2-3', date '1-2-3')")
          .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
      expr("(date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
          + " " + op + " (date '1-2-3', date '1-2-3')")
          .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    }
  }

  @Test void testContains() {  // 测试方法：testContains
    final String cannotApply =
        "(?s).*Cannot apply 'CONTAINS' to arguments of type .*";

    expr("(date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + " contains (date '1-2-3', date '1-2-3')")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("period (date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + " contains period (date '1-2-3', date '1-2-3')")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("(date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + "  contains (date '1-2-3', interval '1' year)").ok();  // 断言验证：期望SQL验证通过
    expr("(time '1:2:3', interval '1' second)\n"  // 创建表达式测试：解析并验证SQL表达式
        + " contains (time '23:59:59', time '1:2:3')").ok();  // 断言验证：期望SQL验证通过
    expr("(timestamp '1-2-3 4:5:6', timestamp '1-2-3 4:5:6')\n"  // 创建表达式测试：解析并验证SQL表达式
        + " contains (timestamp '1-2-3 4:5:6', interval '1 2:3:4.5' day to "
        + "second)").ok();  // 断言验证：期望SQL验证通过

    // period contains point
    expr("(date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + "  contains date '1-2-3'").ok();  // 断言验证：期望SQL验证通过
    // same, with "period" keyword
    expr("period (date '1-2-3', date '1-2-3')\n"  // 创建表达式测试：解析并验证SQL表达式
        + "  contains date '1-2-3'").ok();  // 断言验证：期望SQL验证通过
    // point contains period
    wholeExpr("date '1-2-3'\n"
        + "  contains (date '1-2-3', date '1-2-3')")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
    // same, with "period" keyword
    wholeExpr("date '1-2-3'\n"
        + "  contains period (date '1-2-3', date '1-2-3')")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
    // point contains point
    wholeExpr("date '1-2-3' contains date '1-2-3'")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息

    wholeExpr("(timestamp '1-2-3 4:5:6', timestamp '1-2-3 4:5:6' )\n"
        + " contains (time '4:5:6', interval '1 2:3:4.5' day to second)")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("(time '4:5:6', timestamp '1-2-3 4:5:6' )\n"
        + " contains (time '4:5:6', interval '1 2:3:4.5' day to second)")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("(time '4:5:6', time '4:5:6' )\n"
        + "  contains (time '4:5:6', date '1-2-3')")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
    wholeExpr("1 contains 2")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
    // row with 3 arguments
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or ^(date '1-2-3', date '1-2-3', date '1-2-3')\n"
        + "  contains (date '1-2-3', date '1-2-3')^\n"
        + "or false")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息

    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or (date '1-2-3', date '1-2-3')\n"
        + "  contains (date '1-2-3', date '1-2-3')\n"
        + "or false")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    // second argument is a point
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or (date '1-2-3', date '1-2-3')\n"
        + "  contains date '1-2-3'\n"
        + "or false")
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    // first argument may be null, so result may be null
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or (date '1-2-3',\n"
        + "     case 1 when 2 then date '1-2-3' else null end)\n"
        + "  contains date '1-2-3'\n"
        + "or false")
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    // second argument may be null, so result may be null
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or (date '1-2-3', date '1-2-3')\n"
        + "  contains case 1 when 1 then date '1-2-3' else null end\n"
        + "or false")
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or ^period (date '1-2-3', date '1-2-3')\n"
        + "  contains period (date '1-2-3', time '4:5:6')^\n"
        + "or false")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
    expr("true\n"  // 创建表达式测试：解析并验证SQL表达式
        + "or ^(1, 2) contains (2, 3)^\n"
        + "or false")
        .fails(cannotApply);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testExtract() {  // 测试方法：testExtract
    // TODO: Need to have extract return decimal type for seconds
    // so we can have seconds fractions
    expr("extract(year from interval '1-2' year to month)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("extract(minute from interval '1.1' second)").ok();  // 断言验证：期望SQL验证通过
    expr("extract(year from DATE '2008-2-2')").ok();  // 断言验证：期望SQL验证通过
    expr("extract(minute from interval '11' month)").ok();  // 断言验证：期望SQL验证通过

    wholeExpr("extract(year from interval '11' second)")
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testCastToInterval() {  // 测试方法：testCastToInterval
    expr("cast(interval '1' hour as varchar(20))")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(20) NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(interval '1' hour as bigint)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BIGINT NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(1000 as interval hour)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL HOUR NOT NULL");  // 类型验证：检查结果列的类型

    expr("cast(interval '1' month as interval year)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(interval '1-1' year to month as interval month)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL MONTH NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(interval '1:1' hour to minute as interval day)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL DAY NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(interval '1:1' hour to minute as interval minute to second)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL MINUTE TO SECOND NOT NULL");  // 类型验证：检查结果列的类型

    wholeExpr("cast(interval '1:1' hour to minute as interval month)")
        .fails("Cast function cannot convert value of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "INTERVAL HOUR TO MINUTE to type INTERVAL MONTH");
    wholeExpr("cast(interval '1-1' year to month as interval second)")
        .fails("Cast function cannot convert value of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "INTERVAL YEAR TO MONTH to type INTERVAL SECOND");
  }

  @Test void testMinusDateOperator() {  // 测试方法：testMinusDateOperator
    expr("(CURRENT_DATE - CURRENT_DATE) HOUR")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL HOUR NOT NULL");  // 类型验证：检查结果列的类型
    expr("(CURRENT_DATE - CURRENT_DATE) YEAR TO MONTH")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTERVAL YEAR TO MONTH NOT NULL");  // 类型验证：检查结果列的类型
    wholeExpr("(CURRENT_DATE - LOCALTIME) YEAR TO MONTH")
        .fails("(?s).*Parameters must be of the same type.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testBind() {  // 测试方法：testBind
    sql("select * from emp where deptno = ?").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where deptno = ? and sal < 100000").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select case when deptno = ? then 1 else 2 end from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    // It is not possible to infer type of ?, because SUBSTRING is overloaded
    sql("select deptno from emp group by substring(ename from ^?^ for ?)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Illegal use of dynamic parameter");  // 断言验证：期望SQL验证失败并检查错误消息
    // In principle we could infer that ? should be a VARCHAR
    sql("select count(*) from emp group by position(^?^ in ename)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Illegal use of dynamic parameter");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^deptno^ from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by case when deptno = ? then 1 else 2 end")
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno from emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "group by deptno, case when deptno = ? then 1 else 2 end").ok();  // 断言验证：期望SQL验证通过
    sql("select 1 from emp having sum(sal) < ?").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1310">[CALCITE-1310]
   * Infer type of arguments to BETWEEN operator</a>. */
  @Test void testBindBetween() {  // 测试方法：testBindBetween
    sql("select * from emp where ename between ? and ?").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where deptno between ? and ?").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where ? between deptno and ?").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where ? between ? and deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from emp where ^?^ between ? and ?")  // 创建SQL测试：解析并验证SQL语句
        .fails("Illegal use of dynamic parameter");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnnest() {  // 测试方法：testUnnest
    sql("select*from unnest(multiset[1])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(multiset[1, 2])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(multiset[321.3, 2.33])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(5, 2) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(multiset[321.3, 4.23e0])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(multiset[43.2e1, cast(null as decimal(4,2))])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型
    sql("select*from unnest(multiset[1, 2.3, 1])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(11, 1) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(multiset['1','22','333'])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(multiset['1','22','333','22'])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from ^unnest(1)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply 'UNNEST' to arguments of type 'UNNEST.<INTEGER>.'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select*from unnest(multiset(select*from dept))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select c from unnest(multiset(select deptno from dept)) as t(c)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select c from unnest(multiset(select * from dept)) as t(^c^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("List of column aliases must have same degree as table; "  // 断言验证：期望SQL验证失败并检查错误消息
            + "table has 2 columns \\('DEPTNO', 'NAME'\\), "
            + "whereas alias list has 1 columns");
    sql("select ^c1^ from unnest(multiset(select name from dept)) as t(c)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'C1' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3789">[CALCITE-3789]
   * Support validation of UNNEST multiple array columns like Presto</a>.
   */
  @Test void testAliasUnnestMultipleArrays() {  // 测试方法：testAliasUnnestMultipleArrays
    // for accessing a field in STRUCT type unnested from array
    sql("select e.ENAME\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept_nested_expanded as d CROSS JOIN\n"
        + " UNNEST(d.employees) as t(e)")
        .withConformance(SqlConformanceEnum.PRESTO).columnType("VARCHAR(10) NOT NULL");  // 类型验证：检查结果列的类型

    // for unnesting multiple arrays at the same time
    sql("select d.deptno, e, k.empno, l.\"unit\", l.\"X\" * l.\"Y\"\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept_nested_expanded as d CROSS JOIN\n"
        + " UNNEST(d.admins, d.employees, d.offices) as t(e, k, l)")
        .withConformance(SqlConformanceEnum.PRESTO).ok();  // 断言验证：期望SQL验证通过

    // Make sure validation fails properly given illegal select items
    sql("select d.deptno, ^e^.some_column, k.empno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept_nested_expanded as d CROSS JOIN\n"
        + " UNNEST(d.admins, d.employees) as t(e, k)")
        .withConformance(SqlConformanceEnum.PRESTO)
        .fails("Table 'E' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select d.deptno, e.detail, ^unknown^.detail\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept_nested_expanded as d CROSS JOIN\n"
        + " UNNEST(d.employees) as t(e)")
        .withConformance(SqlConformanceEnum.PRESTO).fails("Incompatible types");  // 断言验证：期望SQL验证失败并检查错误消息

    // Make sure validation fails without PRESTO conformance
    sql("select e.ENAME\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept_nested_expanded as d CROSS JOIN\n"
        + " UNNEST(d.employees) as t(^e^)")
        .fails("List of column aliases must have same degree as table; table has 3 columns "  // 断言验证：期望SQL验证失败并检查错误消息
            + "\\('EMPNO', 'ENAME', 'DETAIL'\\), whereas alias list has 1 columns");
  }

  @Test void testUnnestArray() {  // 测试方法：testUnnestArray
    sql("select*from unnest(array[1])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array[1, 2])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array[321.3, 2.33])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(5, 2) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array[321.3, 4.23e0])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DOUBLE NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array[43.2e1, cast(null as decimal(4,2))])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DOUBLE");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array[1, 2.3, 1])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("DECIMAL(11, 1) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array['1','22','333'])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array['1','22','333','22'])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("CHAR(3) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select*from unnest(array['1','22',null,'22'])")  // 创建SQL测试：解析并验证SQL语句
        .columnType("CHAR(2)");  // 类型验证：检查结果列的类型
    sql("select*from ^unnest(1)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply 'UNNEST' to arguments of type 'UNNEST.<INTEGER>.'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select*from unnest(array(select*from dept))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select*from unnest(array[1,null])").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select c from unnest(array(select deptno from dept)) as t(c)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select c from unnest(array(select * from dept)) as t(^c^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("List of column aliases must have same degree as table; "  // 断言验证：期望SQL验证失败并检查错误消息
            + "table has 2 columns \\('DEPTNO', 'NAME'\\), "
            + "whereas alias list has 1 columns");
    sql("select ^c1^ from unnest(array(select name from dept)) as t(c)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'C1' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testArrayConstructor() {  // 测试方法：testArrayConstructor
    sql("select array[1,2] as a from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select array[1,cast(null as integer), 2] as a\n"  // 创建SQL测试：解析并验证SQL语句
        + "from (values (1))")
        .columnType("INTEGER ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select array[1,null,2] as a from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select array['1',null,'234',''] as a from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("CHAR(3) ARRAY NOT NULL");  // 类型验证：检查结果列的类型
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4999">[CALCITE-4999]
   * ARRAY, MULTISET functions should return a collection of scalars
   * if a sub-query returns 1 column</a>.
   */
  @Test void testArrayQueryConstructor() {  // 测试方法：testArrayQueryConstructor
    sql("select array(select 1)")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select array(select ROW(1,2))")  // 创建SQL测试：解析并验证SQL语句
        .columnType(  // 类型验证：检查结果列的类型
            "RecordType(INTEGER NOT NULL EXPR$0, INTEGER NOT NULL EXPR$1) NOT NULL ARRAY NOT NULL");
  }

  @Test void testCastAsCollectionType() {  // 测试方法：testCastAsCollectionType
    sql("select cast(array[1,null,2] as int array) from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(array['1',null,'2'] as varchar(5) array) from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("VARCHAR(5) ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(multiset[1,null,2] as int multiset) from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(array[1,null,2] as int multiset) from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER MULTISET NOT NULL");  // 类型验证：检查结果列的类型

    // test array type.
    sql("select cast(\"intArrayType\" as int array) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("INTEGER NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"varchar5ArrayType\" as varchar(5) array) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("VARCHAR(5) NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"intArrayArrayType\" as int array array) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("INTEGER NOT NULL ARRAY NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"varchar5ArrayArrayType\" as varchar(5) array array) "  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("VARCHAR(5) NOT NULL ARRAY NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    // test multiset type.
    sql("select cast(\"intMultisetType\" as int multiset) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("INTEGER NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"varchar5MultisetType\" as varchar(5) multiset) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("VARCHAR(5) NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"intMultisetArrayType\" as int multiset array) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("INTEGER NOT NULL MULTISET NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"varchar5MultisetArrayType\" as varchar(5) multiset array) "  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("VARCHAR(5) NOT NULL MULTISET NOT NULL ARRAY NOT NULL");  // 类型验证：检查结果列的类型
    // test row type nested in collection type.
    sql("select cast(\"rowArrayMultisetType\" as row(f0 int array multiset, "  // 创建SQL测试：解析并验证SQL语句
        + "f1 varchar(5) array) array multiset) "
        + "from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("RecordType(INTEGER NOT NULL ARRAY NOT NULL MULTISET NOT NULL F0, "  // 类型验证：检查结果列的类型
            + "VARCHAR(5) NOT NULL ARRAY NOT NULL F1) NOT NULL "
            + "ARRAY NOT NULL MULTISET NOT NULL");
    // test UDT collection type.
    sql("select cast(a as ^MyUDT^ array multiset) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .fails("Unknown identifier 'MYUDT'");  // 断言验证：期望SQL验证失败并检查错误消息
    // Test case for [CALCITE-6920]
    // https://issues.apache.org/jira/projects/CALCITE/issues/CALCITE-6920
    // The type derived for a cast to INT ARRAY always has non-nullable elements
    expr("cast(CAST (ARRAY[1,null] AS VARIANT) AS INT ARRAY)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER ARRAY");  // 类型验证：检查结果列的类型
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5570">[CALCITE-5570]
   * Support nested map type for SqlDataTypeSpec</a>.
   */
  @Test void testCastMapType() {  // 测试方法：testCastMapType
    sql("select cast(\"int2IntMapType\" as map<int,int>) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("(INTEGER NOT NULL, INTEGER) MAP NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"int2varcharArrayMapType\" as map<int,varchar array>) "  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("(INTEGER NOT NULL, VARCHAR ARRAY) MAP NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(\"varcharMultiset2IntIntMapType\" as map<varchar(5) multiset, map<int, int>>)"  // 创建SQL测试：解析并验证SQL语句
        + " from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("(VARCHAR(5) MULTISET NOT NULL, "  // 类型验证：检查结果列的类型
            + "(INTEGER NOT NULL, INTEGER) MAP) MAP NOT NULL");
  }

  @Test void testCastAsRowType() {  // 测试方法：testCastAsRowType
    sql("select cast(a as row(f0 int, f1 varchar)) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .columnType("RecordType(INTEGER NOT NULL F0, VARCHAR NOT NULL F1) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select cast(b as row(f0 int not null, f1 varchar null))\n"  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("RecordType(INTEGER NOT NULL F0, VARCHAR F1) NOT NULL");  // 类型验证：检查结果列的类型
    // test nested row type.
    sql("select "  // 创建SQL测试：解析并验证SQL语句
        + "cast(c as row("
        + "f0 row(ff0 int not null, ff1 varchar null) null, "
        + "f1 timestamp not null))"
        + " from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("RecordType("  // 类型验证：检查结果列的类型
            + "RecordType(INTEGER FF0, VARCHAR FF1) F0, "
            + "TIMESTAMP(0) NOT NULL F1) NOT NULL");
    // test row type in collection data types.
    sql("select cast(d as row(f0 bigint not null, f1 decimal null) array)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("RecordType(BIGINT NOT NULL F0, DECIMAL(19, 0) F1) NOT NULL "  // 类型验证：检查结果列的类型
            + "ARRAY NOT NULL");
    sql("select cast(e as row(f0 varchar not null, f1 timestamp null) multiset)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withExtendedCatalog()
        .columnType("RecordType(VARCHAR NOT NULL F0, TIMESTAMP(0) F1) NOT NULL "  // 类型验证：检查结果列的类型
            + "MULTISET NOT NULL");
  }

  @Test void testSafeCastAsCollectionType() {  // 测试方法：testSafeCastAsCollectionType
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.BIG_QUERY);

    sql("select safe_cast(array[1,null,2] as int array) from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .withOperatorTable(opTable)
        .columnType("INTEGER ARRAY");  // 类型验证：检查结果列的类型
    sql("select safe_cast(multiset[1,null,2] as int multiset) from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .withOperatorTable(opTable)
        .columnType("INTEGER MULTISET");  // 类型验证：检查结果列的类型

    // test array type.
    sql("select safe_cast(\"varchar5ArrayArrayType\" as varchar(5) array array) "  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withOperatorTable(opTable)
        .withExtendedCatalog()
        .columnType("VARCHAR(5) ARRAY ARRAY");  // 类型验证：检查结果列的类型
    // test multiset type.
    sql("select safe_cast(\"varchar5MultisetArrayType\" as varchar(5) multiset array) "  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withOperatorTable(opTable)
        .withExtendedCatalog()
        .columnType("VARCHAR(5) MULTISET ARRAY");  // 类型验证：检查结果列的类型
  }

  @Test void testTryCastAsRowType() {  // 测试方法：testTryCastAsRowType
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.MSSQL);

    sql("select try_cast(a as row(f0 int, f1 varchar)) from COMPLEXTYPES.CTC_T1")  // 创建SQL测试：解析并验证SQL语句
        .withOperatorTable(opTable)
        .withExtendedCatalog()
        .columnType("RecordType(INTEGER F0, VARCHAR F1)");  // 类型验证：检查结果列的类型
    // test nested row type.
    sql("select "  // 创建SQL测试：解析并验证SQL语句
        + "try_cast(c as row("
        + "f0 row(ff0 int, ff1 varchar), "
        + "f1 timestamp))"
        + " from COMPLEXTYPES.CTC_T1")
        .withOperatorTable(opTable)
        .withExtendedCatalog()
        .columnType("RecordType("  // 类型验证：检查结果列的类型
            + "RecordType(INTEGER FF0, VARCHAR FF1) F0, "
            + "TIMESTAMP(0) F1)");
    // test row type in collection data types.
    sql("select try_cast(d as row(f0 bigint, f1 decimal) array)\n"  // 创建SQL测试：解析并验证SQL语句
        + "from COMPLEXTYPES.CTC_T1")
        .withOperatorTable(opTable)
        .withExtendedCatalog()
        .columnType("RecordType(BIGINT F0, DECIMAL(19, 0) F1) "  // 类型验证：检查结果列的类型
            + "ARRAY");
  }

  @Test void testMultisetConstructor() {  // 测试方法：testMultisetConstructor
    sql("select multiset[1,null,2] as a from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER MULTISET NOT NULL");  // 类型验证：检查结果列的类型
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4999">[CALCITE-4999]
   * ARRAY, MULTISET functions should return an collection of scalars
   * if a sub-query returns 1 column</a>.
   */
  @Test void testMultisetQueryConstructor() {  // 测试方法：testMultisetQueryConstructor
    sql("select multiset(select 1)")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL MULTISET NOT NULL");  // 类型验证：检查结果列的类型
    sql("select multiset(select ROW(1,2))")  // 创建SQL测试：解析并验证SQL语句
        .columnType(  // 类型验证：检查结果列的类型
            "RecordType(INTEGER NOT NULL EXPR$0, INTEGER NOT NULL EXPR$1) NOT NULL MULTISET NOT NULL");
  }

  @Test void testUnnestArrayColumn() {  // 测试方法：testUnnestArrayColumn
    final String sql1 = "select d.deptno, e.*\n"
        + "from dept_nested as d,\n"
        + " UNNEST(d.employees) as e";
    final String type = "RecordType(INTEGER NOT NULL DEPTNO,"
        + " INTEGER NOT NULL EMPNO,"
        + " VARCHAR(10) NOT NULL ENAME,"
        + " RecordType(RecordType(VARCHAR(10) NOT NULL TYPE, VARCHAR(20) NOT NULL DESC,"
        + " RecordType(VARCHAR(10) NOT NULL A, VARCHAR(10) NOT NULL B) NOT NULL OTHERS)"
        + " NOT NULL ARRAY NOT NULL SKILLS) NOT NULL DETAIL) NOT NULL";
    sql(sql1).type(type);

    // equivalent query without table alias
    final String sql1b = "select d.deptno, e.*\n"
        + "from dept_nested as d,\n"
        + " UNNEST(employees) as e";
    sql(sql1b).type(type);

    // equivalent query using CROSS JOIN
    final String sql2 = "select d.deptno, e.*\n"
        + "from dept_nested as d CROSS JOIN\n"
        + " UNNEST(d.employees) as e";
    sql(sql2).type(type);

    // equivalent query using CROSS JOIN, without table alias
    final String sql2b = "select d.deptno, e.*\n"
        + "from dept_nested as d CROSS JOIN\n"
        + " UNNEST(employees) as e";
    sql(sql2b).type(type);

    // LATERAL works left-to-right
    final String sql3 = "select d.deptno, e.*\n"
        + "from UNNEST(^d^.employees) as e, dept_nested as d";
    sql(sql3).fails("Table 'D' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUnnestWithOrdinality() {  // 测试方法：testUnnestWithOrdinality
    sql("select*from unnest(array[1, 2]) with ordinality")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL EXPR$0, INTEGER NOT NULL ORDINALITY) NOT NULL");
    sql("select*from unnest(array[43.2e1, cast(null as decimal(4,2))]) with ordinality")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(DOUBLE EXPR$0, INTEGER NOT NULL ORDINALITY) NOT NULL");
    sql("select * from unnest(array(select deptno from dept)) with ordinality as t")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL T, INTEGER NOT NULL ORDINALITY) NOT NULL");
    sql("select*from ^unnest(1) with ordinality^")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply 'UNNEST' to arguments of type 'UNNEST.<INTEGER>.'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from unnest(array(select*from dept)) with ordinality\n"
        + "where ordinality < 5").ok();  // 断言验证：期望SQL验证通过
    sql("select c from unnest(\n"  // 创建SQL测试：解析并验证SQL语句
        + "  array(select deptno from dept)) with ordinality as t(^c^)")
        .fails("List of column aliases must have same degree as table; table has 2 "  // 断言验证：期望SQL验证失败并检查错误消息
            + "columns \\('EXPR\\$0', 'ORDINALITY'\\), "
            + "whereas alias list has 1 columns");
    sql("select c from unnest(\n"  // 创建SQL测试：解析并验证SQL语句
        + "  array(select deptno from dept)) with ordinality as t(c, d)").ok();  // 断言验证：期望SQL验证通过
    sql("select c from unnest(\n"  // 创建SQL测试：解析并验证SQL语句
        + "  array(select deptno from dept)) with ordinality as t(^c, d, e^)")
        .fails("List of column aliases must have same degree as table; table has 2 "  // 断言验证：期望SQL验证失败并检查错误消息
            + "columns \\('EXPR\\$0', 'ORDINALITY'\\), "
            + "whereas alias list has 3 columns");
    sql("select c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from unnest(array(select * from dept)) with ordinality as t(^c, d, e, f^)")
        .fails("List of column aliases must have same degree as table; table has 3 "  // 断言验证：期望SQL验证失败并检查错误消息
            + "columns \\('DEPTNO', 'NAME', 'ORDINALITY'\\), "
            + "whereas alias list has 4 columns");
    sql("select ^name^ from unnest(array(select name from dept)) with ordinality as t(c, o)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'NAME' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^ordinality^ from unnest(array(select name from dept)) with ordinality as t(c, o)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ORDINALITY' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void unnestMapMustNameColumnsKeyAndValueWhenNotAliased() {
    sql("select * from unnest(map[1, 12, 2, 22])")
        .type("RecordType(INTEGER NOT NULL KEY, INTEGER NOT NULL VALUE) NOT NULL");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4305">[CALCITE-4305]
   * Implicit column alias for single-column UNNEST</a>. */
  @Test void testUnnestAlias() {  // 测试方法：testUnnestAlias
    final String expectedType = "RecordType(CHAR(6) NOT NULL FRUIT) NOT NULL";

    // When UNNEST produces a single column, and you use an alias for the
    // relation, that alias becomes the name of the column.
    sql("select fruit.* from UNNEST(array ['apple', 'banana']) as fruit")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType);
    sql("select fruit.* from UNNEST(array(select 'banana')) as fruit")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType);
    sql("SELECT array(SELECT y + 1 FROM UNNEST(s.x) y) FROM (SELECT ARRAY[1,2,3] as x) s")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过

    // The magic doesn't happen if the query is not an UNNEST.
    // In this case, the query is a SELECT.
    sql("SELECT fruit.*\n"  // 创建SQL测试：解析并验证SQL语句
        + "FROM (\n"
        + "  SELECT * FROM UNNEST(array ['apple', 'banana']) as x) as fruit")
        .type("RecordType(CHAR(6) NOT NULL X) NOT NULL");

    // The magic doesn't happen if the UNNEST yields more than one column.
    sql("select * from UNNEST(array [('apple', 1), ('banana', 2)]) as fruit")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(CHAR(6) NOT NULL EXPR$0, INTEGER NOT NULL EXPR$1) "
            + "NOT NULL");
    // VALUES gets the same treatment as ARRAY. (Unlike PostgreSQL.)
    sql("select * from (values ('apple'), ('banana')) as fruit")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(CHAR(6) NOT NULL FRUIT) NOT NULL");

    // UNNEST MULTISET gets the same treatment as UNNEST ARRAY.
    sql("select * from unnest(multiset [1, 2, 1]) as f")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL F) NOT NULL");

    // The magic doesn't happen if the UNNEST is used without AS operator.
    sql("select * from (SELECT ARRAY['banana'] as fruits) as t, UNNEST(t.fruits)")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(CHAR(6) NOT NULL ARRAY NOT NULL FRUITS, "
            + "CHAR(6) NOT NULL EXPR$0) NOT NULL").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testCorrelationJoin() {  // 测试方法：testCorrelationJoin
    sql("select *,"  // 创建SQL测试：解析并验证SQL语句
        + "         multiset(select * from emp where deptno=dept.deptno) "
        + "               as empset"
        + "      from dept").ok();  // 断言验证：期望SQL验证通过
    sql("select*from unnest(select multiset[8] from dept)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select*from unnest(select multiset[deptno] from dept)").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testStructuredTypes() {  // 测试方法：testStructuredTypes
    sql("values new address()")  // 创建SQL测试：解析并验证SQL语句
        .columnType("ObjectSqlType(ADDRESS) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select home_address from emp_address")  // 创建SQL测试：解析并验证SQL语句
        .columnType("ObjectSqlType(ADDRESS) NOT NULL");  // 类型验证：检查结果列的类型
    sql("select ea.home_address.zip from emp_address ea")  // 创建SQL测试：解析并验证SQL语句
        .columnType("INTEGER NOT NULL");  // 类型验证：检查结果列的类型
    sql("select ea.mailing_address.city from emp_address ea")  // 创建SQL测试：解析并验证SQL语句
        .columnType("VARCHAR(20) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testLateral() {  // 测试方法：testLateral
    sql("select * from emp, (select * from dept where ^emp^.deptno=dept.deptno)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'EMP' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from emp,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  LATERAL (select * from dept where emp.deptno=dept.deptno)").ok();  // 断言验证：期望SQL验证通过
    sql("select * from emp,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  LATERAL (select * from dept where emp.deptno=dept.deptno) as ldt").ok();  // 断言验证：期望SQL验证通过
    sql("select * from emp,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  LATERAL (select * from dept where emp.deptno=dept.deptno) ldt").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testCollect() {  // 测试方法：testCollect
    sql("select collect(deptno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select collect(multiset[3]) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select collect(multiset[3]), ^deptno^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testFusion() {  // 测试方法：testFusion
    sql("select ^fusion(deptno)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Cannot apply 'FUSION' to arguments of type 'FUSION.<INTEGER>.'.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select fusion(multiset[3]) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    // todo. FUSION is an aggregate function. test that validator can only
    // take set operators in its select list once aggregation support is
    // complete
  }

  @Test void testCountFunction() {  // 测试方法：testCountFunction
    sql("select count(*) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select count(ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select count(sal) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select count(1) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select ^count()^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Invalid number of arguments to function 'COUNT'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testCountCompositeFunction() {  // 测试方法：testCountCompositeFunction
    sql("select count(ename, deptno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select count(ename, deptno, ^gender^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'GENDER' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(ename, 1, deptno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select count(distinct ename, 1, deptno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select count(deptno, ^*^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Encountered \"\\*\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(*^,^ deptno) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Encountered \",\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testLastFunction() {  // 测试方法：testLastFunction
    sql("select LAST_VALUE(sal) over (order by empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select LAST_VALUE(ename) over (order by empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select FIRST_VALUE(sal) over (order by empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select FIRST_VALUE(ename) over (order by empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select NTH_VALUE(sal, 2) over (order by empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select NTH_VALUE(ename, 2) over (order by empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testMinMaxFunctions() {  // 测试方法：testMinMaxFunctions
    sql("SELECT MIN(true) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT MAX(false) from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("SELECT MIN(sal+deptno) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT MAX(ename) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT MIN(5.5) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT MAX(5) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testArgMinMaxFunctions() {  // 测试方法：testArgMinMaxFunctions
    sql("SELECT ARG_MIN(1, true) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT ARG_MAX(2, false) from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("SELECT ARG_MIN(sal, deptno) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT ARG_MAX(deptno, sal) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT ARG_MIN('a', 5.5) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT ARG_MAX('b', 5) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testModeFunction() {  // 测试方法：testModeFunction
    sql("select MODE(sal) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select MODE(sal) over (order by empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select MODE(ename) from emp where sal=3000");  // 创建SQL测试：解析并验证SQL语句
    sql("select MODE(sal) from emp group by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select MODE(sal) from emp group by deptno order by deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno,\n"  // 创建SQL测试：解析并验证SQL语句
        + "^mode(empno)^ within group(order by 1)\n"
        + "from emp\n"
        + "group by deptno")
        .fails("Aggregate expression 'MODE' must not contain a WITHIN GROUP clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSomeEveryAndIntersectionFunctions() {  // 测试方法：testSomeEveryAndIntersectionFunctions
    sql("select some(sal = 100), every(sal > 0), intersection(multiset[1,2]) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select some(sal = 100), ^empno^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select every(sal > 0), ^empno^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select intersection(multiset[1]), ^empno^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testAnyValueFunction() {  // 测试方法：testAnyValueFunction
    sql("SELECT any_value(ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testBoolAndBoolOrFunction() {  // 测试方法：testBoolAndBoolOrFunction
    final SqlValidatorFixture s = fixture()
        .withOperatorTable(operatorTableFor(SqlLibrary.POSTGRESQL));
    s.withSql("SELECT bool_and(true) from emp").ok();  // 断言验证：期望SQL验证通过
    s.withSql("SELECT bool_or(true) from emp").ok();  // 断言验证：期望SQL验证通过

    s.withSql("select bool_and(col)\n"
        + "from (values(true), (false), (true)) as tbl(col)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select bool_or(col)\n"
        + "from (values(true), (false), (true)) as tbl(col)").ok();  // 断言验证：期望SQL验证通过

    s.withSql("select bool_and(col)\n"
        + "from (values(true), (false), (null)) as tbl(col)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select bool_or(col)\n"
        + "from (values(true), (false), (null)) as tbl(col)").ok();  // 断言验证：期望SQL验证通过

    s.withSql("SELECT ^bool_and(ename)^ from emp")
        .fails("(?s).*Cannot apply 'BOOL_AND' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'BOOL_AND\\(<VARCHAR\\(20\\)>\\)'.*");
    s.withSql("SELECT ^bool_or(ename)^ from emp")
        .fails("(?s).*Cannot apply 'BOOL_OR' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'BOOL_OR\\(<VARCHAR\\(20\\)>\\)'.*");
  }

  @Test void testConvertFunction() {  // 测试方法：testConvertFunction
    sql("select convert(ename, utf16, utf8) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select convert(cast(deptno as varchar), utf16, utf8) from emp");  // 创建SQL测试：解析并验证SQL语句
    sql("select convert(null, gbk, utf8) from emp");  // 创建SQL测试：解析并验证SQL语句
    sql("select ^convert(deptno, utf8, latin1)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Invalid type 'INTEGER NOT NULL' in 'CONVERT' function\\. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Only 'CHARACTER' type is supported");
    sql("select convert(ename, utf8, utf9) from emp").fails("UTF9");  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testFunctionalDistinct() {  // 测试方法：testFunctionalDistinct
    sql("select count(distinct sal) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select COALESCE(^distinct^ sal) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("DISTINCT/ALL not allowed with COALESCE function");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testColumnNotFound() {  // 测试方法：testColumnNotFound
    sql("select ^b0^ from sales.emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'B0' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testColumnNotFound2() {  // 测试方法：testColumnNotFound2
    sql("select ^b0^ from sales.emp, sales.dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'B0' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testColumnNotFound3() {  // 测试方法：testColumnNotFound3
    sql("select e.^b0^ from sales.emp as e")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'B0' not found in table 'E'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectDistinct() {  // 测试方法：testSelectDistinct
    sql("SELECT DISTINCT deptno FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT DISTINCT deptno, sal FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT DISTINCT deptno FROM emp GROUP BY deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT DISTINCT ^deptno^ FROM emp GROUP BY sal")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT avg(sal) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT DISTINCT ^deptno^, avg(sal) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT deptno, sal from emp GROUP BY sal, deptno").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT deptno FROM emp GROUP BY deptno HAVING deptno > 55").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT DISTINCT deptno, 33 FROM emp\n"  // 创建SQL测试：解析并验证SQL语句
        + "GROUP BY deptno HAVING deptno > 55").ok();  // 断言验证：期望SQL验证通过
    sql("SELECT DISTINCT ^deptno^, 33 FROM emp HAVING deptno > 55")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    // same query under a different conformance finds a different error first
    sql("SELECT DISTINCT ^deptno^, 33 FROM emp HAVING deptno > 55")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.LENIENT)
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT 33 FROM emp HAVING ^deptno^ > 55")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'DEPTNO' is not being grouped")  // 断言验证：期望SQL验证失败并检查错误消息
        .withConformance(SqlConformanceEnum.LENIENT)
        .fails("Expression 'DEPTNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT * from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT DISTINCT ^*^ from emp GROUP BY deptno")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMP\\.EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息

    // similar validation for SELECT DISTINCT and GROUP BY
    sql("SELECT deptno FROM emp GROUP BY deptno ORDER BY deptno, ^empno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT deptno from emp ORDER BY deptno, ^empno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT DISTINCT deptno from emp ORDER BY deptno + 2").ok();  // 创建SQL测试：解析并验证SQL语句

    // The ORDER BY clause works on what is projected by DISTINCT - even if
    // GROUP BY is present.
    sql("SELECT DISTINCT deptno FROM emp GROUP BY deptno, empno ORDER BY deptno, ^empno^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not in the select clause");  // 断言验证：期望SQL验证失败并检查错误消息

    // redundant distinct; same query is in unitsql/optimizer/distinct.sql
    sql("select distinct * from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "  select distinct deptno from emp) order by 1").ok();  // 断言验证：期望SQL验证通过

    sql("SELECT DISTINCT 5, 10+5, 'string' from emp").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testSelectWithoutFrom() {  // 测试方法：testSelectWithoutFrom
    sql("^select 2+2^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.DEFAULT)
        .ok();  // 断言验证：期望SQL验证通过
    sql("^select 2+2^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.ORACLE_10)
        .fails("SELECT must have a FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("^select 2+2^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.STRICT_2003)
        .fails("SELECT must have a FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectAmbiguousField() {  // 测试方法：testSelectAmbiguousField
    sql("select ^t0^ from (select 1 as t0, 2 as T0 from dept)")  // 创建SQL测试：解析并验证SQL语句
        .withCaseSensitive(false)
        .withUnquotedCasing(Casing.UNCHANGED)
        .fails("Column 't0' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^t0^ from (select 1 as t0, 2 as t0,3 as t1,4 as t1, 5 as t2 from dept)")  // 创建SQL测试：解析并验证SQL语句
        .withCaseSensitive(false)
        .withUnquotedCasing(Casing.UNCHANGED)
        .fails("Column 't0' is ambiguous");  // 断言验证：期望SQL验证失败并检查错误消息
    // t0 is not referenced,so this case is allowed
    sql("select 1 as t0, 2 as t0 from dept")  // 创建SQL测试：解析并验证SQL语句
        .withCaseSensitive(false)
        .withUnquotedCasing(Casing.UNCHANGED)
        .ok();  // 断言验证：期望SQL验证通过

    sql("select t0 from (select 1 as t0, 2 as T0 from DEPT)")  // 创建SQL测试：解析并验证SQL语句
        .withCaseSensitive(true)
        .withUnquotedCasing(Casing.UNCHANGED)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testTableExtend() {  // 测试方法：testTableExtend
    sql("select * from dept extend (x int not null)")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL DEPTNO, VARCHAR(10) NOT NULL NAME, "
            + "INTEGER NOT NULL X) NOT NULL");
    sql("select deptno + x as z\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept extend (x int not null) as x\n"
        + "where x > 10")
        .type("RecordType(INTEGER NOT NULL Z) NOT NULL");
  }

  @Test void testExplicitTable() {  // 测试方法：testExplicitTable
    final String empRecordType =
        "RecordType(INTEGER NOT NULL EMPNO,"
            + " VARCHAR(20) NOT NULL ENAME,"
            + " VARCHAR(10) NOT NULL JOB,"
            + " INTEGER MGR,"
            + " TIMESTAMP(0) NOT NULL HIREDATE,"
            + " INTEGER NOT NULL SAL,"
            + " INTEGER NOT NULL COMM,"
            + " INTEGER NOT NULL DEPTNO,"
            + " BOOLEAN NOT NULL SLACKER) NOT NULL";
    sql("select * from (table emp)")  // 创建SQL测试：解析并验证SQL语句
        .type(empRecordType);
    sql("table emp")  // 创建SQL测试：解析并验证SQL语句
        .type(empRecordType);
    sql("table ^nonexistent^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'NONEXISTENT' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("table ^sales.nonexistent^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'NONEXISTENT' not found within 'SALES'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("table ^nonexistent.foo^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'NONEXISTENT' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testCollectionTable() {  // 测试方法：testCollectionTable
    sql("select * from table(ramp(3))")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL I) NOT NULL");

    sql("select * from table(^ramp('3')^)")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("Cannot apply 'RAMP' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'RAMP\\(<CHAR\\(1\\)>\\)'\\. "
            + "Supported form\\(s\\): 'RAMP\\(<NUMERIC>\\)'");

    sql("select * from table(ramp('3'))")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL I) NOT NULL");

    sql("select * from table(^abs^(-1))")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s)Encountered \"abs\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from table(^1^ + 2)")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s)Encountered \"1\" at .*");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from table(^nonExistentRamp('3')^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("No match found for function signature NONEXISTENTRAMP\\(<CHARACTER>\\)");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from table(^bad_ramp(3)^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Argument must be a table function: BAD_RAMP");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from table(^bad_table_function(3)^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table function should have CURSOR type, not"  // 断言验证：期望SQL验证失败并检查错误消息
            + " RecordType\\(INTEGER I\\)");
  }

  /** Tests that Calcite gives an error if a table function is used anywhere
   * that a scalar expression is expected. */
  @Test void testTableFunctionAsExpression() {  // 测试方法：testTableFunctionAsExpression
    sql("select ^ramp(3)^ from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot call table function here: 'RAMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from (values (1)) where ^ramp(3)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("WHERE clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from (values (1)) where ^ramp(3) and 1 = 1^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot apply 'AND' to arguments of type '<CURSOR> AND "  // 断言验证：期望SQL验证失败并检查错误消息
            + "<BOOLEAN>'\\. Supported form\\(s\\): '<BOOLEAN> AND <BOOLEAN>'");
    sql("select * from (values (1)) where ^ramp(3) is not null^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot apply 'IS NOT NULL' to arguments of type '<CURSOR> IS"  // 断言验证：期望SQL验证失败并检查错误消息
            + " NOT NULL'\\. Supported form\\(s\\): '<ANY> IS NOT NULL'");
    sql("select ^sum(ramp(3))^ from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot apply 'SUM' to arguments of type 'SUM\\(<CURSOR>\\)'\\. "  // 断言验证：期望SQL验证失败并检查错误消息
            + "Supported form\\(s\\): 'SUM\\(<NUMERIC>\\)'");
    sql("select 0 from (values (1)) group by ^ramp(3)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot call table function here: 'RAMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select count(*) from (values (1)) having ^ramp(3)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("HAVING clause must be a condition");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from (values (1)) order by ^ramp(3)^ asc, 1 desc")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot call table function here: 'RAMP'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5779">[CALCITE-5779]
   * Implicit column alias for single-column table function should work</a>. */
  @Test void testTableFunctionSingleColumnAlias() {  // 测试方法：testTableFunctionSingleColumnAlias
    final SqlValidatorFixture s = fixture()
        .withOperatorTable(MockSqlOperatorTable.standard().extend());
    s.withSql("select rmp from table(ramp(3)) as rmp").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select rmp.i from table(ramp(3)) as rmp").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select rmp.i, rmp from table(ramp(3)) as rmp").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select l from table(ramp(3)) as rmp(l)").ok();  // 断言验证：期望SQL验证通过
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1309">[CALCITE-1309]
   * Support LATERAL TABLE</a>. */
  @Test void testCollectionTableWithLateral() {  // 测试方法：testCollectionTableWithLateral
    final String expectedType = "RecordType(INTEGER NOT NULL DEPTNO, "
        + "VARCHAR(10) NOT NULL NAME, "
        + "INTEGER NOT NULL I) NOT NULL";
    sql("select * from dept, lateral table(ramp(dept.deptno))")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType);
    sql("select * from dept cross join lateral table(ramp(dept.deptno))")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType);
    sql("select * from dept join lateral table(ramp(dept.deptno)) on true")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType);

    final String expectedType2 = "RecordType(INTEGER NOT NULL DEPTNO, "
        + "VARCHAR(10) NOT NULL NAME, "
        + "INTEGER I) NOT NULL";
    sql("select * from dept left join lateral table(ramp(dept.deptno)) on true")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType2);

    sql("select * from dept, lateral table(^ramp(dept.name)^)")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s)Cannot apply 'RAMP' to arguments of type 'RAMP\\(<VARCHAR\\(10\\)>\\)'.*");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from dept, lateral table(ramp(dept.name))")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL DEPTNO, "
            + "VARCHAR(10) NOT NULL NAME, "
            + "INTEGER NOT NULL I) NOT NULL");

    sql("select * from lateral table(ramp(^dept^.deptno)), dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'DEPT' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    final String expectedType3 = "RecordType(INTEGER NOT NULL I, "
        + "INTEGER NOT NULL DEPTNO, "
        + "VARCHAR(10) NOT NULL NAME) NOT NULL";
    sql("select * from lateral table(ramp(1234)), dept")  // 创建SQL测试：解析并验证SQL语句
        .type(expectedType3);
  }

  @Test void testCollectionTableWithLateral2() {  // 测试方法：testCollectionTableWithLateral2
    // The expression inside the LATERAL can only see tables before it in the
    // FROM clause. And it can't see itself.
    sql("select * from emp, lateral table(ramp(emp.deptno)), dept")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过
    sql("select * from emp, lateral table(ramp(^z^.i)) as z, dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'Z' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from emp, lateral table(ramp(^dept^.deptno)), dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'DEPT' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6266">[CALCITE-6266]
   * SqlValidatorException with LATERAL TABLE and JOIN</a>. */
  @Test void testCollectionTableWithLateral3() {  // 测试方法：testCollectionTableWithLateral3
    // The cause of [CALCITE-6266] was that CROSS JOIN had higher precedence
    // than ",", and therefore "table(ramp(deptno)" was being associated with
    // "values".
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept,\n"
        + "  lateral table(ramp(deptno))\n"
        + "  cross join (values ('A'), ('B'))")
        .ok();  // 断言验证：期望SQL验证通过
    // As above, using NATURAL JOIN
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept,\n"
        + "  lateral table(ramp(deptno))\n"
        + "  natural join emp")
        .ok();  // 断言验证：期望SQL验证通过
    // As above, using comma
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp,\n"
        + "  lateral (select * from dept where dept.deptno = emp.deptno),\n"
        + "  emp as e2")
        .ok();  // 断言验证：期望SQL验证通过
    // Without 'as e2', relation name is not unique
    sql("select *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp,\n"
        + "  lateral (select * from dept where dept.deptno = emp.deptno),\n"
        + "  ^emp^")
        .fails("Duplicate relation name 'EMP' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testCollectionTableWithCursorParam() {  // 测试方法：testCollectionTableWithCursorParam
    sql("select * from table(dedup(cursor(select * from emp),'ename'))")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(VARCHAR(1024) NOT NULL NAME) NOT NULL");
    sql("select * from table(dedup(cursor(select * from ^bloop^),'ename'))")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'BLOOP' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testTemporalTable() {  // 测试方法：testTemporalTable
    sql("select stream * from orders, ^products^ for system_time as of"  // 创建SQL测试：解析并验证SQL语句
        + " TIMESTAMP '2011-01-02 00:00:00'")
        .fails("Table 'PRODUCTS' is not a temporal table, "  // 断言验证：期望SQL验证失败并检查错误消息
            + "can not be queried in system time period specification");

    sql("select stream * from orders, ^products^ for system_time as of"  // 创建SQL测试：解析并验证SQL语句
        + " TIMESTAMP WITH LOCAL TIME ZONE '2011-01-02 00:00:00'")
        .fails("Table 'PRODUCTS' is not a temporal table, "  // 断言验证：期望SQL验证失败并检查错误消息
            + "can not be queried in system time period specification");

    sql("select stream * from orders, products_temporal "  // 创建SQL测试：解析并验证SQL语句
        + "for system_time as of ^'2011-01-02 00:00:00'^")
        .fails("The system time period specification expects Timestamp type but is 'CHAR'");  // 断言验证：期望SQL验证失败并检查错误消息

    // verify inner join with a specific timestamp
    sql("select stream * from orders join products_temporal "  // 创建SQL测试：解析并验证SQL语句
        + "for system_time as of timestamp '2011-01-02 00:00:00' "
        + "on orders.productid = products_temporal.productid").ok();  // 断言验证：期望SQL验证通过

    // verify left join with a timestamp field
    sql("select stream * from orders left join products_temporal "  // 创建SQL测试：解析并验证SQL语句
        + "for system_time as of orders.rowtime "
        + "on orders.productid = products_temporal.productid").ok();  // 断言验证：期望SQL验证通过

    // verify left join with a timestamp expression
    sql("select stream * from orders left join products_temporal\n"  // 创建SQL测试：解析并验证SQL语句
        + "for system_time as of orders.rowtime - INTERVAL '3' DAY\n"
        + "on orders.productid = products_temporal.productid").ok();  // 断言验证：期望SQL验证通过

    // verify left join with a datetime value function
    sql("select stream * from orders left join products_temporal\n"  // 创建SQL测试：解析并验证SQL语句
        + "for system_time as of CURRENT_TIMESTAMP\n"
        + "on orders.productid = products_temporal.productid").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testScalarSubQuery() {  // 测试方法：testScalarSubQuery
    sql("SELECT  ename,(select name from dept where deptno=1) FROM emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT ename,^(select losal, hisal from salgrade where grade=1)^ FROM emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot apply '\\$SCALAR_QUERY' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'\\$SCALAR_QUERY\\(<RECORDTYPE\\(INTEGER LOSAL, "
            + "INTEGER HISAL\\)>\\)'\\. Supported form\\(s\\): "
            + "'\\$SCALAR_QUERY\\(<RECORDTYPE\\(SINGLE FIELD\\)>\\)'");

    // Note that X is a field (not a record) and is nullable even though
    // EMP.NAME is NOT NULL.
    sql("SELECT  ename,(select name from dept where deptno=1) FROM emp")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(VARCHAR(20) NOT NULL ENAME, VARCHAR(10) EXPR$1) NOT NULL");

    // scalar subqery inside AS operator
    sql("SELECT  ename,(select name from dept where deptno=1) as X FROM emp")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(VARCHAR(20) NOT NULL ENAME, VARCHAR(10) X) NOT NULL");

    // scalar subqery inside + operator
    sql("SELECT  ename, 1 + (select deptno from dept where deptno=1) as X FROM emp")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(VARCHAR(20) NOT NULL ENAME, INTEGER X) NOT NULL");

    // scalar sub-query inside WHERE
    sql("select * from emp where (select true from dept)").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Disabled("not supported")
  @Test void testSubQueryInOnClause() {  // 测试方法：testSubQueryInOnClause
    // Currently not supported. Should give validator error, but gives
    // internal error.
    sql("select * from emp as emps left outer join dept as depts\n"  // 创建SQL测试：解析并验证SQL语句
        + "on emps.deptno = depts.deptno and emps.deptno = (\n"
        + "select min(deptno) from dept as depts2)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void dynamicParameterType() {
    expr("CAST(? AS INTEGER)")
        .columnType("INTEGER");
  }

  @Test void testRecordType() {  // 测试方法：testRecordType
    // Have to qualify columns with table name.
    sql("SELECT ^coord^.x, coord.y FROM customer.contact")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'COORD' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("SELECT contact.coord.x, contact.coord.y FROM customer.contact")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL X, INTEGER NOT NULL Y) NOT NULL");

    // Qualifying with schema is OK.
    sql("SELECT customer.contact.coord.x, customer.contact.email,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  contact.coord.y\n"
        + "FROM customer.contact")
        .type("RecordType(INTEGER NOT NULL X, VARCHAR(20) NOT NULL EMAIL, "
            + "INTEGER NOT NULL Y) NOT NULL");
  }

  @Test void testArrayOfRecordType() {  // 测试方法：testArrayOfRecordType
    sql("SELECT name, dept_nested.employees[1].^ne^ as ne from dept_nested")  // 创建SQL测试：解析并验证SQL语句
        .fails("Unknown field 'NE'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("SELECT name, dept_nested.employees[1].ename as ename from dept_nested")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(VARCHAR(10) NOT NULL NAME, VARCHAR(10) ENAME) NOT NULL");
    sql("SELECT dept_nested.employees[1].detail.skills[1].desc as DESCRIPTION\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept_nested")
        .type("RecordType(VARCHAR(20) DESCRIPTION) NOT NULL");
    sql("SELECT dept_nested.employees[1].detail.skills[1].others.a as oa\n"  // 创建SQL测试：解析并验证SQL语句
        + "from dept_nested")
        .type("RecordType(VARCHAR(10) OA) NOT NULL");
  }

  @Test void testItemOperatorException() {  // 测试方法：testItemOperatorException
    sql("select ^name[0]^ from dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Cannot apply 'ITEM' to arguments of type 'ITEM\\(<VARCHAR\\(10\\)>, "  // 断言验证：期望SQL验证失败并检查错误消息
            +  "<INTEGER>\\)'\\. Supported form\\(s\\): <ARRAY>\\[<INTEGER>\\]\n"
            + "<MAP>\\[<ANY>\\]\n"
            + "<ROW>\\[<CHARACTER>\\|<INTEGER>\\]\n"
            + "<VARIANT>\\[<ANY>\\].*");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-497">[CALCITE-497]
   * Support optional qualifier for column name references</a>. */
  @Test void testRecordTypeElided() {  // 测试方法：testRecordTypeElided
    sql("SELECT contact.^x^, contact.coord.y FROM customer.contact")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'X' not found in table 'CONTACT'");  // 断言验证：期望SQL验证失败并检查错误消息

    // Fully qualified works.
    sql("SELECT contact.coord.x, contact.coord.y FROM customer.contact")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL X, INTEGER NOT NULL Y) NOT NULL");

    // Because the types of CONTACT_PEEK.COORD and CONTACT_PEEK.COORD_NE are
    // marked "peek", the validator can see through them.
    sql("SELECT c.x, c.coord.y, c.m, c.b FROM customer.contact_peek as c")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL X, INTEGER NOT NULL Y, INTEGER NOT NULL M,"
            + " INTEGER NOT NULL B) NOT NULL");
    sql("SELECT c.coord.x, c.coord.y, c.coord_ne.m FROM customer.contact_peek as c")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL X, INTEGER NOT NULL Y, INTEGER NOT NULL M) NOT NULL");
    sql("SELECT x, a, c.coord.y FROM customer.contact_peek as c")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL X, INTEGER NOT NULL A, INTEGER NOT NULL Y) NOT NULL");

    // Because CONTACT_PEEK.COORD_NE is marked "peek no expand",
    // "select *" does not flatten it.
    sql("SELECT coord_ne.* FROM customer.contact_peek as c")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL M, "
            + "RecordType:peek_no_expand(INTEGER NOT NULL A, INTEGER NOT NULL B) "
            + "NOT NULL SUB) NOT NULL");
    sql("SELECT * FROM customer.contact_peek as c")  // 创建SQL测试：解析并验证SQL语句
        .type("RecordType(INTEGER NOT NULL CONTACTNO, VARCHAR(10) NOT NULL FNAME, "
            + "VARCHAR(10) NOT NULL LNAME, VARCHAR(20) NOT NULL EMAIL, INTEGER NOT NULL X, "
            + "INTEGER NOT NULL Y, VARCHAR(20) NOT NULL unit, "
            + "RecordType:peek_no_expand(INTEGER NOT NULL M, "
            + "RecordType:peek_no_expand(INTEGER NOT NULL A, INTEGER NOT NULL B) "
            + "NOT NULL SUB) NOT NULL COORD_NE) NOT NULL");

    // Qualifying with schema is OK.
    final String sql = "SELECT customer.contact_peek.x,\n"
        + " customer.contact_peek.email, contact_peek.coord.y\n"
        + "FROM customer.contact_peek";
    sql(sql).type("RecordType(INTEGER NOT NULL X, VARCHAR(20) NOT NULL EMAIL,"
        + " INTEGER NOT NULL Y) NOT NULL");
  }

  @Test void testSample() {  // 测试方法：testSample
    // applied to table
    sql("SELECT * FROM emp TABLESAMPLE SUBSTITUTE('foo')").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT * FROM emp TABLESAMPLE BERNOULLI(50)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("SELECT * FROM emp TABLESAMPLE SYSTEM(50)").ok();  // 创建SQL测试：解析并验证SQL语句

    // applied to query
    sql("SELECT * FROM ("  // 创建SQL测试：解析并验证SQL语句
        + "SELECT deptno FROM emp "
        + "UNION ALL "
        + "SELECT deptno FROM dept) AS x TABLESAMPLE SUBSTITUTE('foo') "
        + "WHERE x.deptno < 100").ok();  // 断言验证：期望SQL验证通过

    sql("SELECT x.^empno^ FROM ("  // 创建SQL测试：解析并验证SQL语句
        + "SELECT deptno FROM emp TABLESAMPLE SUBSTITUTE('bar') "
        + "UNION ALL "
        + "SELECT deptno FROM dept) AS x TABLESAMPLE SUBSTITUTE('foo') "
        + "ORDER BY 1")
        .fails("Column 'EMPNO' not found in table 'X'");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "    select * from emp\n"
        + "    join dept on emp.deptno = dept.deptno\n"
        + ") tablesample substitute('SMALL')").ok();  // 断言验证：期望SQL验证通过

    sql("SELECT * FROM ("  // 创建SQL测试：解析并验证SQL语句
        + "SELECT deptno FROM emp "
        + "UNION ALL "
        + "SELECT deptno FROM dept) AS x TABLESAMPLE BERNOULLI(50) "
        + "WHERE x.deptno < 100").ok();  // 断言验证：期望SQL验证通过

    sql("SELECT x.^empno^ FROM ("  // 创建SQL测试：解析并验证SQL语句
        + "SELECT deptno FROM emp TABLESAMPLE BERNOULLI(50) "
        + "UNION ALL "
        + "SELECT deptno FROM dept) AS x TABLESAMPLE BERNOULLI(10) "
        + "ORDER BY 1")
        .fails("Column 'EMPNO' not found in table 'X'");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "    select * from emp\n"
        + "    join dept on emp.deptno = dept.deptno\n"
        + ") tablesample bernoulli(10)").ok();  // 断言验证：期望SQL验证通过

    sql("SELECT * FROM ("  // 创建SQL测试：解析并验证SQL语句
        + "SELECT deptno FROM emp "
        + "UNION ALL "
        + "SELECT deptno FROM dept) AS x TABLESAMPLE SYSTEM(50) "
        + "WHERE x.deptno < 100").ok();  // 断言验证：期望SQL验证通过

    sql("SELECT x.^empno^ FROM ("  // 创建SQL测试：解析并验证SQL语句
        + "SELECT deptno FROM emp TABLESAMPLE SYSTEM(50) "
        + "UNION ALL "
        + "SELECT deptno FROM dept) AS x TABLESAMPLE SYSTEM(10) "
        + "ORDER BY 1")
        .fails("Column 'EMPNO' not found in table 'X'");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from (\n"  // 创建SQL测试：解析并验证SQL语句
        + "    select * from emp\n"
        + "    join dept on emp.deptno = dept.deptno\n"
        + ") tablesample system(10)").ok();  // 断言验证：期望SQL验证通过

    sql("select * from ^emp TABLESAMPLE BERNOULLI(1000)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("TABLESAMPLE percentage must be between 0 and 100, inclusive");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from ^emp TABLESAMPLE SYSTEM(101)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("TABLESAMPLE percentage must be between 0 and 100, inclusive");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRewriteWithoutIdentifierExpansion() {  // 测试方法：testRewriteWithoutIdentifierExpansion
    sql("select * from dept")  // 创建SQL测试：解析并验证SQL语句
        .withValidatorIdentifierExpansion(false)
        .rewritesTo("SELECT *\n"
            + "FROM `DEPT`");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6007">[CALCITE-6007]
   * Sub-query that contains WITH and has no alias generates invalid SQL after
   * expansion</a>. */
  @Test void testSubQueryWithoutAlias() {  // 测试方法：testSubQueryWithoutAlias
    // Note the 'AS `EXPR$0`' in the rewritten form of each query.
    // Before [CALCITE-6007] was fixed, that alias was missing.
    sql("select a from (select 1 as a)")  // 创建SQL测试：解析并验证SQL语句
        .withValidatorIdentifierExpansion(true)
        .rewritesTo("SELECT `EXPR$0`.`A`\n"
            + "FROM (SELECT 1 AS `A`) AS `EXPR$0`");
    sql("select a from (with sub as (select 1 as a) select a from sub)")  // 创建SQL测试：解析并验证SQL语句
        .withValidatorIdentifierExpansion(true)
        .rewritesTo("SELECT `EXPR$0`.`A`\n"
            + "FROM (WITH `SUB` AS (SELECT 1 AS `A`) "
            + "SELECT `SUB`.`A`\n"
            + "FROM `SUB` AS `SUB`) AS `EXPR$0`");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1238">[CALCITE-1238]
   * Unparsing LIMIT without ORDER BY after validation</a>. */
  @Test void testRewriteWithLimitWithoutOrderBy() {  // 测试方法：testRewriteWithLimitWithoutOrderBy
    final String sql = "select name from dept limit 2";
    final String expected = "SELECT `NAME`\n"
        + "FROM `DEPT`\n"
        + "FETCH NEXT 2 ROWS ONLY";
    sql(sql)
        .withValidatorIdentifierExpansion(false)
        .rewritesTo(expected);
  }

  @Test void testRewriteWithLimitWithDynamicParameters() {  // 测试方法：testRewriteWithLimitWithDynamicParameters
    final String sql = "select name from dept offset ? rows fetch next ? rows only";
    final String expected = "SELECT `NAME`\n"
        + "FROM `DEPT`\n"
        + "OFFSET ? ROWS\n"
        + "FETCH NEXT ? ROWS ONLY";
    sql(sql)
        .withValidatorIdentifierExpansion(false)
        .rewritesTo(expected);
  }

  @Test void testRewriteWithOffsetWithoutOrderBy() {  // 测试方法：testRewriteWithOffsetWithoutOrderBy
    final String sql = "select name from dept offset 2";
    final String expected = "SELECT `NAME`\n"
        + "FROM `DEPT`\n"
        + "OFFSET 2 ROWS";
    sql(sql)
        .withValidatorIdentifierExpansion(false)
        .rewritesTo(expected);
  }

  @Test void testRewriteWithUnionFetchWithoutOrderBy() {  // 测试方法：testRewriteWithUnionFetchWithoutOrderBy
    final String sql =
        "select name from dept union all select name from dept limit 2";
    final String expected = "SELECT *\n"
        + "FROM (SELECT `NAME`\n"
        + "FROM `DEPT`\n"
        + "UNION ALL\n"
        + "SELECT `NAME`\n"
        + "FROM `DEPT`)\n"
        + "FETCH NEXT 2 ROWS ONLY";
    sql(sql)
        .withValidatorIdentifierExpansion(false)
        .rewritesTo(expected);
  }

  @Test void testRewriteWithIdentifierExpansion() {  // 测试方法：testRewriteWithIdentifierExpansion
    sql("select * from dept")  // 创建SQL测试：解析并验证SQL语句
        .withValidatorIdentifierExpansion(true)
        .rewritesTo("SELECT `DEPT`.`DEPTNO`, `DEPT`.`NAME`\n"
            + "FROM `CATALOG`.`SALES`.`DEPT` AS `DEPT`");
  }

  @Test void testRewriteWithColumnReferenceExpansion() {  // 测试方法：testRewriteWithColumnReferenceExpansion
    // The names in the ORDER BY clause are not qualified.
    // This is because ORDER BY references columns in the SELECT clause
    // in preference to columns in tables in the FROM clause.

    sql("select name from dept where name = 'Moonracer' group by name"  // 创建SQL测试：解析并验证SQL语句
        + " having sum(deptno) > 3 order by name")
        .withValidatorIdentifierExpansion(true)
        .withValidatorColumnReferenceExpansion(true)
        .rewritesTo("SELECT `DEPT`.`NAME`\n"
            + "FROM `CATALOG`.`SALES`.`DEPT` AS `DEPT`\n"
            + "WHERE `DEPT`.`NAME` = CAST('Moonracer' AS VARCHAR(10) CHARACTER SET `ISO-8859-1`)\n"
            + "GROUP BY `DEPT`.`NAME`\n"
            + "HAVING SUM(`DEPT`.`DEPTNO`) > 3\n"
            + "ORDER BY `NAME`");
  }

  @Test void testRewriteWithColumnReferenceExpansionAndFromAlias() {  // 测试方法：testRewriteWithColumnReferenceExpansionAndFromAlias
    // In the ORDER BY clause, 'ename' is not qualified but 'deptno' and 'sal'
    // are. This is because 'ename' appears as an alias in the SELECT clause.
    // 'sal' is qualified in the ORDER BY clause, so remains qualified.

    final String sql = "select ename, sal from (select * from emp) as e"
        + " where ename = 'Moonracer' group by ename, deptno, sal"
        + " having sum(deptno) > 3 order by ename, deptno, e.sal";
    final String expected = "SELECT `E`.`ENAME`, `E`.`SAL`\n"
        + "FROM (SELECT `EMP`.`EMPNO`, `EMP`.`ENAME`, `EMP`.`JOB`,"
        + " `EMP`.`MGR`, `EMP`.`HIREDATE`, `EMP`.`SAL`, `EMP`.`COMM`,"
        + " `EMP`.`DEPTNO`, `EMP`.`SLACKER`\n"
        + "FROM `CATALOG`.`SALES`.`EMP` AS `EMP`) AS `E`\n"
        + "WHERE `E`.`ENAME` = CAST('Moonracer' AS VARCHAR(20) CHARACTER SET `ISO-8859-1`)\n"
        + "GROUP BY `E`.`ENAME`, `E`.`DEPTNO`, `E`.`SAL`\n"
        + "HAVING SUM(`E`.`DEPTNO`) > 3\n"
        + "ORDER BY `ENAME`, `E`.`DEPTNO`, `E`.`SAL`";
    sql(sql)
        .withValidatorIdentifierExpansion(true)
        .withValidatorColumnReferenceExpansion(true)
        .rewritesTo(expected);
  }

  @Test void testRewriteExpansionOfColumnReferenceBeforeResolution() {  // 测试方法：testRewriteExpansionOfColumnReferenceBeforeResolution
    final String sql = "select unexpanded.deptno from dept \n"
        + " where unexpanded.name = 'Moonracer' \n"
        + " group by unexpanded.deptno\n"
        + " having sum(unexpanded.deptno) > 0\n"
        + " order by unexpanded.deptno";
    final String expectedSql = "SELECT `DEPT`.`DEPTNO`\n"
        + "FROM `CATALOG`.`SALES`.`DEPT` AS `DEPT`\n"
        + "WHERE `DEPT`.`NAME` = CAST('Moonracer' AS VARCHAR(10) CHARACTER SET `ISO-8859-1`)\n"
        + "GROUP BY `DEPT`.`DEPTNO`\n"
        + "HAVING SUM(`DEPT`.`DEPTNO`) > 0\n"
        + "ORDER BY `DEPT`.`DEPTNO`";
    SqlValidatorTestCase.FIXTURE
        .withFactory(t -> t.withValidator(UnexpandedToDeptValidator::new))
        .withSql(sql)
        .withValidatorIdentifierExpansion(true)
        .withValidatorColumnReferenceExpansion(true)
        .withConformance(SqlConformanceEnum.LENIENT)
        .rewritesTo(expectedSql);
  }

  @Test void testCoalesceWithoutRewrite() {  // 测试方法：testCoalesceWithoutRewrite
    final String sql = "select coalesce(deptno, empno) from emp";
    final String expected1 = "SELECT COALESCE(`EMP`.`DEPTNO`, `EMP`.`EMPNO`)\n"
        + "FROM `CATALOG`.`SALES`.`EMP` AS `EMP`";
    final String expected2 = "SELECT COALESCE(`DEPTNO`, `EMPNO`)\n"
        + "FROM `EMP`";
    sql(sql)
        .withValidatorCallRewrite(false)
        .withValidatorIdentifierExpansion(true)
        .rewritesTo(expected1)
        .withValidatorIdentifierExpansion(false)
        .rewritesTo(expected2);
  }

  @Test void testCoalesceWithRewrite() {  // 测试方法：testCoalesceWithRewrite
    final String sql = "select coalesce(deptno, empno) from emp";
    final String expected1 = "SELECT CASE WHEN `EMP`.`DEPTNO` IS NOT NULL"
        + " THEN `EMP`.`DEPTNO` ELSE `EMP`.`EMPNO` END\n"
        + "FROM `CATALOG`.`SALES`.`EMP` AS `EMP`";
    final String expected2 = "SELECT CASE WHEN `DEPTNO` IS NOT NULL"
        + " THEN `DEPTNO` ELSE `EMPNO` END\n"
        + "FROM `EMP`";
    sql(sql)
        .withValidatorCallRewrite(true)
        .withValidatorIdentifierExpansion(true)
        .rewritesTo(expected1)
        .withValidatorIdentifierExpansion(false)
        .rewritesTo(expected2);
  }

  @Test void testDatePartWithRewrite() {  // 测试方法：testDatePartWithRewrite
    final String sql = "select week(date '2022-04-27'), year(date '2022-04-27')";
    final String expected = "SELECT EXTRACT(WEEK FROM DATE '2022-04-27'),"
        + " EXTRACT(YEAR FROM DATE '2022-04-27')";
    sql(sql)
        .withValidatorCallRewrite(true)
        .rewritesTo(expected);

    final String noParamSql = "select ^week()^";
    sql(noParamSql)
        .withValidatorCallRewrite(true)
        .fails("Invalid number of arguments to function 'WEEK'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息

    final String multiParamsSql = "select ^week(date '2022-04-27', 1)^";
    sql(multiParamsSql)
        .withValidatorCallRewrite(true)
        .fails("Invalid number of arguments to function 'WEEK'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testDatePartWithoutRewrite() {  // 测试方法：testDatePartWithoutRewrite
    final String sql = "select week(date '2022-04-27'), year(date '2022-04-27')";
    final String expected = "SELECT WEEK(DATE '2022-04-27'), YEAR(DATE '2022-04-27')";
    sql(sql)
        .withValidatorCallRewrite(false)
        .rewritesTo(expected);

    final String noParamSql = "select ^week()^";
    sql(noParamSql)
        .withValidatorCallRewrite(false)
        .fails("Invalid number of arguments to function 'WEEK'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息

    final String multiParamsSql = "select ^week(date '2022-04-27', 1)^";
    sql(multiParamsSql)
        .withValidatorCallRewrite(false)
        .fails("Invalid number of arguments to function 'WEEK'. Was expecting 1 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Disabled
  @Test void testValuesWithAggFuncs() {  // 测试方法：testValuesWithAggFuncs
    sql("values(^count(1)^)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Call to xxx is invalid\\. Direct calls to aggregate "  // 断言验证：期望SQL验证失败并检查错误消息
            + "functions not allowed in ROW definitions\\.");
  }

  @Test void testFieldOrigin() {  // 测试方法：testFieldOrigin
    sql("select * from emp join dept on true")  // 创建SQL测试：解析并验证SQL语句
        .assertFieldOrigin(
            is("{CATALOG.SALES.EMP.EMPNO,"
            + " CATALOG.SALES.EMP.ENAME,"
            + " CATALOG.SALES.EMP.JOB,"
            + " CATALOG.SALES.EMP.MGR,"
            + " CATALOG.SALES.EMP.HIREDATE,"
            + " CATALOG.SALES.EMP.SAL,"
            + " CATALOG.SALES.EMP.COMM,"
            + " CATALOG.SALES.EMP.DEPTNO,"
            + " CATALOG.SALES.EMP.SLACKER,"
            + " CATALOG.SALES.DEPT.DEPTNO,"
            + " CATALOG.SALES.DEPT.NAME}"));

    sql("select distinct emp.empno, hiredate, 1 as uno,\n"  // 创建SQL测试：解析并验证SQL语句
        + " emp.empno * 2 as twiceEmpno\n"
        + "from emp join dept on true")
        .assertFieldOrigin(
            is("{CATALOG.SALES.EMP.EMPNO,"
                + " CATALOG.SALES.EMP.HIREDATE,"
                + " null,"
                + " null}"));

    sql("select e.empno from dept_nested, unnest(employees) as e")  // 创建SQL测试：解析并验证SQL语句
        .assertFieldOrigin(
            is("{CATALOG.SALES.DEPT_NESTED.EMPLOYEES.EMPNO}"));

    sql("select * from UNNEST(ARRAY['a', 'b'])")  // 创建SQL测试：解析并验证SQL语句
        .assertFieldOrigin(is("{null}"));

    sql("select * from UNNEST(ARRAY['a', 'b'], ARRAY['d', 'e'])")  // 创建SQL测试：解析并验证SQL语句
        .assertFieldOrigin(is("{null, null}"));

    sql("select dpt.skill.desc from dept_nested as dpt")  // 创建SQL测试：解析并验证SQL语句
        .assertFieldOrigin(is("{CATALOG.SALES.DEPT_NESTED.SKILL.DESC}"));
  }

  @Test void testBrackets() {  // 测试方法：testBrackets
    final SqlValidatorFixture s = fixture().withQuoting(Quoting.BRACKET);
    s.withSql("select [e].EMPNO from [EMP] as [e]")
        .type("RecordType(INTEGER NOT NULL EMPNO) NOT NULL");

    s.withSql("select ^e^.EMPNO from [EMP] as [e]")
        .fails("Table 'E' not found; did you mean 'e'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    s.withSql("select ^x^ from (\n"
        + "  select [e].EMPNO as [x] from [EMP] as [e])")
        .fails("Column 'X' not found in any table; did you mean 'x'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    s.withSql("select ^x^ from (\n"
        + "  select [e].EMPNO as [x ] from [EMP] as [e])")
        .fails("Column 'X' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    s.withSql("select EMP^.^\"x\" from EMP")
        .fails("(?s).*Encountered \"\\. \\\\\"\" at line .*");  // 断言验证：期望SQL验证失败并检查错误消息

    s.withSql("select [x[y]] z ] from (\n"
        + "  select [e].EMPNO as [x[y]] z ] from [EMP] as [e])").type(
        "RecordType(INTEGER NOT NULL x[y] z ) NOT NULL");
  }

  @Test void testLexJava() {  // 测试方法：testLexJava
    final SqlValidatorFixture s = fixture().withLex(Lex.JAVA);
    s.withSql("select e.EMPNO from EMP as e")
        .type("RecordType(INTEGER NOT NULL EMPNO) NOT NULL");

    s.withSql("select ^e^.EMPNO from EMP as E")
        .fails("Table 'e' not found; did you mean 'E'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    s.withSql("select ^E^.EMPNO from EMP as e")
        .fails("Table 'E' not found; did you mean 'e'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    s.withSql("select ^x^ from (\n"
        + "  select e.EMPNO as X from EMP as e)")
        .fails("Column 'x' not found in any table; did you mean 'X'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    s.withSql("select ^x^ from (\n"
        + "  select e.EMPNO as Xx from EMP as e)")
        .fails("Column 'x' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息

    // double-quotes are not valid in this lexical convention
    s.withSql("select EMP^.^\"x\" from EMP")
        .fails("(?s).*Encountered \"\\. \\\\\"\" at line .*");  // 断言验证：期望SQL验证失败并检查错误消息

    // in Java mode, creating identifiers with spaces is not encouraged, but you
    // can use back-ticks if you really have to
    s.withSql("select `x[y] z ` from (\n"
        + "  select e.EMPNO as `x[y] z ` from EMP as e)")
        .type("RecordType(INTEGER NOT NULL x[y] z ) NOT NULL");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-145">[CALCITE-145]
   * Unexpected upper-casing of keywords when using java lexer</a>. */
  @Test void testLexJavaKeyword() {  // 测试方法：testLexJavaKeyword
    final SqlValidatorFixture s = fixture().withLex(Lex.JAVA);
    s.withSql("select path, x from (select 1 as path, 2 as x from (values (true)))")
        .type("RecordType(INTEGER NOT NULL path, INTEGER NOT NULL x) NOT NULL");
    s.withSql("select path, x from (select 1 as `path`, 2 as x from (values (true)))")
        .type("RecordType(INTEGER NOT NULL path, INTEGER NOT NULL x) NOT NULL");
    s.withSql("select `path`, x from (select 1 as path, 2 as x from (values (true)))")
        .type("RecordType(INTEGER NOT NULL path, INTEGER NOT NULL x) NOT NULL");
    s.withSql("select ^PATH^ from (select 1 as path from (values (true)))")
        .fails("Column 'PATH' not found in any table; did you mean 'path'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("select t.^PATH^ from (select 1 as path from (values (true))) as t")
        .fails("Column 'PATH' not found in table 't'; did you mean 'path'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("select t.x, t.^PATH^ from (values (true, 1)) as t(path, x)")
        .fails("Column 'PATH' not found in table 't'; did you mean 'path'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    // Built-in functions can be written in any case, even those with no args,
    // and regardless of spaces between function name and open parenthesis.
    s.withSql("values (current_timestamp, floor(2.5), ceil (3.5))").ok();  // 断言验证：期望SQL验证通过
    s.withSql("values (CURRENT_TIMESTAMP, FLOOR(2.5), CEIL (3.5))").ok();  // 断言验证：期望SQL验证通过
    s.withSql("values (CURRENT_TIMESTAMP, CEIL (3.5))")
        .type("RecordType(TIMESTAMP(0) NOT NULL CURRENT_TIMESTAMP, "
            + "DECIMAL(2, 0) NOT NULL EXPR$1) NOT NULL");
  }

  @Test void testLexAndQuoting() {  // 测试方法：testLexAndQuoting
    // in Java mode, creating identifiers with spaces is not encouraged, but you
    // can use double-quote if you really have to
    fixture()
        .withLex(Lex.JAVA)
        .withQuoting(Quoting.DOUBLE_QUOTE)
        .withSql("select \"x[y] z \" from (\n"
            + "  select e.EMPNO as \"x[y] z \" from EMP as e)")
        .type("RecordType(INTEGER NOT NULL x[y] z ) NOT NULL");
  }

  /** Tests using case-insensitive matching of identifiers. */
  @Test void testCaseInsensitive() {  // 测试方法：testCaseInsensitive
    final SqlValidatorFixture s = fixture()
        .withCaseSensitive(false)
        .withQuoting(Quoting.BRACKET);
    final SqlValidatorFixture sensitive = fixture()
        .withQuoting(Quoting.BRACKET);

    s.withSql("select EMPNO from EMP").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select empno from emp").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select [empno] from [emp]").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select [E].[empno] from [emp] as e").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select t.[x] from (\n"
        + "  select [E].[empno] as x from [emp] as e) as [t]").ok();  // 断言验证：期望SQL验证通过

    // correlating variable
    s.withSql("select * from emp as [e] where exists (\n"
        + "select 1 from dept where dept.deptno = [E].deptno)").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select * from emp as [e] where exists (\n"
        + "select 1 from dept where dept.deptno = ^[E]^.deptno)")
        .fails("(?s).*Table 'E' not found; did you mean 'e'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select count(1), ^empno^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Expression 'EMPNO' is not being grouped");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Tests using case-insensitive matching of user-defined functions. */
  @Test void testCaseInsensitiveUdfs() {  // 测试方法：testCaseInsensitiveUdfs
    final SqlOperatorTable operatorTable =
        MockSqlOperatorTable.standard().extend();
    final SqlValidatorFixture insensitive = fixture()
        .withCaseSensitive(false)
        .withQuoting(Quoting.BRACKET)
        .withOperatorTable(operatorTable);
    final SqlValidatorFixture sensitive = fixture()
        .withQuoting(Quoting.BRACKET)
        .withOperatorTable(operatorTable);

    // test table function lookup case-insensitively.
    insensitive.withSql("select * from dept, lateral table(ramp(dept.deptno))").ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql("select * from dept, lateral table(RAMP(dept.deptno))").ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql("select * from dept, lateral table([RAMP](dept.deptno))").ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql("select * from dept, lateral table([Ramp](dept.deptno))").ok();  // 断言验证：期望SQL验证通过
    // test scalar function lookup case-insensitively.
    insensitive.withSql("select myfun(EMPNO) from EMP").ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql("select MYFUN(empno) from emp").ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql("select [MYFUN]([empno]) from [emp]").ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql("select [Myfun]([E].[empno]) from [emp] as e").ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql("select t.[x] from (\n"
        + "  select [Myfun]([E].[empno]) as x from [emp] as e) as [t]").ok();  // 断言验证：期望SQL验证通过

    // correlating variable
    insensitive.withSql("select * from emp as [e] where exists (\n"
        + "select 1 from dept where dept.deptno = myfun([E].deptno))").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select * from emp as [e] where exists (\n"
        + "select 1 from dept where dept.deptno = ^[myfun]([e].deptno)^)")
        .fails("No match found for function signature myfun\\(<NUMERIC>\\).*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Tests using case-sensitive matching of builtin functions. */
  @Test void testCaseSensitiveBuiltinFunction() {  // 测试方法：testCaseSensitiveBuiltinFunction
    final SqlValidatorFixture sensitive = fixture()
        .withCaseSensitive(true)
        .withUnquotedCasing(Casing.UNCHANGED)
        .withQuoting(Quoting.BRACKET)
        .withOperatorTable(SqlStdOperatorTable.instance());

    sensitive.withSql("select sum(EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select [sum](EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select [SUM](EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select SUM(EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select Sum(EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select count(EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select [count](EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select [COUNT](EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select COUNT(EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select Count(EMPNO) from EMP group by ENAME, EMPNO").ok();  // 断言验证：期望SQL验证通过
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-319">[CALCITE-319]
   * Table aliases should follow case-sensitivity policy</a>. */
  @Test void testCaseInsensitiveTableAlias() {  // 测试方法：testCaseInsensitiveTableAlias
    final SqlValidatorFixture s = fixture()
        .withCaseSensitive(false)
        .withQuoting(Quoting.BRACKET);
    final SqlValidatorFixture sensitive = fixture()
        .withQuoting(Quoting.BRACKET);

    // Table aliases should follow case-sensitivity preference.
    //
    // In MySQL, table aliases are case-insensitive:
    // mysql> select `D`.day from DAYS as `d`, DAYS as `D`;
    // ERROR 1066 (42000): Not unique table/alias: 'D'
    s.withSql("select count(*) from dept as [D], ^dept as [d]^")
        .fails("Duplicate relation name 'd' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息
    sensitive.withSql("select count(*) from dept as [D], dept as [d]").ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql("select count(*) from dept as [D], ^dept as [D]^")
        .fails("Duplicate relation name 'D' in FROM clause");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1305">[CALCITE-1305]
   * Case-insensitive table aliases and GROUP BY</a>. */
  @Test void testCaseInsensitiveTableAliasInGroupBy() {  // 测试方法：testCaseInsensitiveTableAliasInGroupBy
    final SqlValidatorFixture s = fixture()
        .withCaseSensitive(false)
        .withUnquotedCasing(Casing.UNCHANGED);
    s.withSql("select deptno, count(*) from EMP AS emp\n"
        + "group by eMp.deptno").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select deptno, count(*) from EMP AS EMP\n"
        + "group by eMp.deptno").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select deptno, count(*) from EMP\n"
        + "group by eMp.deptno").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select * from EMP where exists (\n"
        + "  select 1 from dept\n"
        + "  group by eMp.deptno)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select deptno, count(*) from EMP group by DEPTNO").ok();  // 断言验证：期望SQL验证通过
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1549">[CALCITE-1549]
   * Improve error message when table or column not found</a>. */
  @Test void testTableNotFoundDidYouMean() {  // 测试方法：testTableNotFoundDidYouMean
    // No table in default schema
    sql("select * from ^unknownTable^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'UNKNOWNTABLE' not found");  // 断言验证：期望SQL验证失败并检查错误消息

    // Similar table exists in default schema
    sql("select * from ^\"Emp\"^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'Emp' not found within 'SALES'; did you mean 'EMP'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    // Schema correct, but no table in specified schema
    sql("select * from ^sales.unknownTable^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'UNKNOWNTABLE' not found within 'SALES'");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar table exists in specified schema
    sql("select * from ^sales.\"Emp\"^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'Emp' not found within 'SALES'; did you mean 'EMP'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    // No schema found
    sql("select * from ^unknownSchema.unknownTable^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'UNKNOWNSCHEMA' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar schema found
    sql("select * from ^\"sales\".emp^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'sales' not found; did you mean 'SALES'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from ^\"saLes\".\"eMp\"^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'saLes' not found; did you mean 'SALES'\\?");  // 断言验证：期望SQL验证失败并检查错误消息

    // Spurious after table
    sql("select * from ^emp.foo^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'FOO' not found within 'SALES\\.EMP'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from ^sales.emp.foo^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Object 'FOO' not found within 'SALES\\.EMP'");  // 断言验证：期望SQL验证失败并检查错误消息

    // Alias not found
    sql("select ^aliAs^.\"name\"\n"  // 创建SQL测试：解析并验证SQL语句
        + "from sales.emp as \"Alias\"")
        .fails("Table 'ALIAS' not found; did you mean 'Alias'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // Alias not found, fully-qualified
    sql("select ^sales.\"emp\"^.\"name\" from sales.emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Table 'SALES\\.emp' not found; did you mean 'EMP'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testColumnNotFoundDidYouMean() {  // 测试方法：testColumnNotFoundDidYouMean
    // Column not found
    sql("select ^\"unknownColumn\"^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'unknownColumn' not found in any table");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar column in table, unqualified table name
    sql("select ^\"empNo\"^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'empNo' not found in any table; did you mean 'EMPNO'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar column in table, table name qualified with schema
    sql("select ^\"empNo\"^ from sales.emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'empNo' not found in any table; did you mean 'EMPNO'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar column in table, table name qualified with catalog and schema
    sql("select ^\"empNo\"^ from catalog.sales.emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'empNo' not found in any table; did you mean 'EMPNO'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // With table alias
    sql("select e.^\"empNo\"^ from catalog.sales.emp as e")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'empNo' not found in table 'E'; did you mean 'EMPNO'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // With fully-qualified table alias
    sql("select catalog.sales.emp.^\"empNo\"^\n"  // 创建SQL测试：解析并验证SQL语句
        + "from catalog.sales.emp")
        .fails("Column 'empNo' not found in table 'CATALOG\\.SALES\\.EMP'; "  // 断言验证：期望SQL验证失败并检查错误消息
            + "did you mean 'EMPNO'\\?");
    // Similar column in table; multiple tables
    sql("select ^\"name\"^ from emp, dept")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'name' not found in any table; did you mean 'NAME'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar column in table; table and a query
    sql("select ^\"name\"^ from emp,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (select * from dept) as d")
        .fails("Column 'name' not found in any table; did you mean 'NAME'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar column in table; table and an un-aliased query
    sql("select ^\"name\"^ from emp, (select * from dept)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'name' not found in any table; did you mean 'NAME'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    // Similar column in table, multiple tables
    sql("select ^\"deptno\"^ from emp,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (select deptno as \"deptNo\" from dept)")
        .fails("Column 'deptno' not found in any table; "  // 断言验证：期望SQL验证失败并检查错误消息
            + "did you mean 'DEPTNO', 'deptNo'\\?");
    sql("select ^\"deptno\"^ from emp,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  (select * from dept) as t(\"deptNo\", name)")
        .fails("Column 'deptno' not found in any table; "  // 断言验证：期望SQL验证失败并检查错误消息
            + "did you mean 'DEPTNO', 'deptNo'\\?");
  }

  /** Tests matching of built-in operator names. */
  @Test void testUnquotedBuiltInFunctionNames() {  // 测试方法：testUnquotedBuiltInFunctionNames
    final SqlValidatorFixture mysql = fixture()
        .withUnquotedCasing(Casing.UNCHANGED)
        .withQuoting(Quoting.BACK_TICK)
        .withCaseSensitive(false);
    final SqlValidatorFixture oracle = fixture()
        .withUnquotedCasing(Casing.TO_UPPER)
        .withCaseSensitive(true);

    // Built-in functions are always case-insensitive.
    oracle.withSql("select count(*), sum(deptno), floor(2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    oracle.withSql("select COUNT(*), FLOOR(2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    oracle.withSql("select cOuNt(*), FlOOr(2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    oracle.withSql("select cOuNt (*), FlOOr (2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    oracle.withSql("select current_time from dept").ok();  // 断言验证：期望SQL验证通过
    oracle.withSql("select Current_Time from dept").ok();  // 断言验证：期望SQL验证通过
    oracle.withSql("select CURRENT_TIME from dept").ok();  // 断言验证：期望SQL验证通过

    mysql.withSql("select sum(deptno), floor(2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select count(*), sum(deptno), floor(2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select COUNT(*), FLOOR(2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select cOuNt(*), FlOOr(2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select cOuNt (*), FlOOr (2.5) from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select current_time from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select Current_Time from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select CURRENT_TIME from dept").ok();  // 断言验证：期望SQL验证通过

    // MySQL assumes that a quoted function name is not a built-in.
    //
    // mysql> select `sum`(`day`) from days;
    // ERROR 1630 (42000): FUNCTION foodmart.sum does not exist. Check the
    //   'Function Name Parsing and Resolution' section in the Reference Manual
    // mysql> select `SUM`(`day`) from days;
    // ERROR 1630 (42000): FUNCTION foodmart.SUM does not exist. Check the
    //   'Function Name Parsing and Resolution' section in the Reference Manual
    // mysql> select SUM(`day`) from days;
    // +------------+
    // | SUM(`day`) |
    // +------------+
    // |         28 |
    // +------------+
    // 1 row in set (0.00 sec)
    //
    // We do not follow MySQL in this regard. `count` is preserved in
    // lower-case, and is matched case-insensitively because it is a built-in.
    // So, the query succeeds.
    oracle.withSql("select \"count\"(*) from dept").ok();  // 断言验证：期望SQL验证通过
    mysql.withSql("select `count`(*) from dept").ok();  // 断言验证：期望SQL验证通过
  }

  /** Sanity check: All built-ins are upper-case. We rely on this. */
  @Test void testStandardOperatorNamesAreUpperCase() {  // 测试方法：testStandardOperatorNamesAreUpperCase
    for (SqlOperator op : SqlStdOperatorTable.instance().getOperatorList()) {
      final String name = op.getName();
      switch (op.getSyntax()) {
      case SPECIAL:
      case INTERNAL:
        break;
      default:
        assertThat(name.toUpperCase(Locale.ROOT), equalTo(name));
        break;
      }
    }
  }

  private static int prec(SqlOperator op) {
    return Math.max(op.getLeftPrec(), op.getRightPrec());
  }

  /** Tests that operators, sorted by precedence, are in a sane order. Each
   * operator has a {@link SqlOperator#getLeftPrec() left} and
   * {@link SqlOperator#getRightPrec()} right} precedence, but we would like
   * the order to remain the same even if we tweak particular operators'
   * precedences. If you need to update the expected output, you might also
   * need to change
   * <a href="http://calcite.apache.org/docs/reference.html#operator-precedence">
   * the documentation</a>. */
  @Test void testOperatorsSortedByPrecedence() {  // 测试方法：testOperatorsSortedByPrecedence
    final StringBuilder b = new StringBuilder();
    final Comparator<SqlOperator> comparator = (o1, o2) -> {
      int c = Integer.compare(prec(o1), prec(o2));
      if (c != 0) {
        return -c;
      }
      c = o1.getName().compareTo(o2.getName());
      if (c != 0) {
        return c;
      }
      return o1.getSyntax().compareTo(o2.getSyntax());
    };
    final List<SqlOperator> operators =
        SqlStdOperatorTable.instance().getOperatorList();
    int p = -1;
    for (SqlOperator op : Ordering.from(comparator).sortedCopy(operators)) {
      final String type;
      switch (op.getSyntax()) {
      case FUNCTION:
      case FUNCTION_ID:
      case FUNCTION_STAR:
      case INTERNAL:
        continue;
      case PREFIX:
        type = "pre";
        break;
      case POSTFIX:
        type = "post";
        break;
      case BINARY:
        if (op.getLeftPrec() < op.getRightPrec()) {
          type = "left";
        } else {
          type = "right";
        }
        break;
      default:
        if (op instanceof SqlSpecialOperator) {
          type = "-";
        } else {
          continue;
        }
      }
      if (prec(op) != p) {
        b.append('\n');
        p = prec(op);
      }
      b.append(op.getName())
          .append(' ')
          .append(type)
          .append('\n');
    }
    final String expected = "\n"
        + "ARRAY -\n"
        + "ARRAY -\n"
        + "COLUMN_LIST -\n"
        + "CURSOR -\n"
        + "LATERAL -\n"
        + "MAP -\n"
        + "MAP -\n"
        + "MULTISET -\n"
        + "MULTISET -\n"
        + "ROW -\n"
        + "TABLE -\n"
        + "UNNEST -\n"
        + "\n"
        + "CURRENT_VALUE -\n"
        + "DEFAULT -\n"
        + "DOT -\n"
        + "ITEM -\n"
        + "NEXT_VALUE -\n"
        + "PATTERN_EXCLUDE -\n"
        + "PATTERN_PERMUTE -\n"
        + "WITHIN DISTINCT left\n"
        + "WITHIN GROUP left\n"
        + "\n"
        + "PATTERN_QUANTIFIER -\n"
        + "\n"
        + " left\n"
        + "$LiteralChain -\n"
        + "+ pre\n"
        + "- pre\n"
        + "- pre\n" // checked
        + "FINAL pre\n"
        + "RUNNING pre\n"
        + "\n"
        + "| left\n"
        + "\n"
        + "% left\n"
        + "* left\n"
        + "* left\n" // checked
        + "/ left\n" // checked
        + "/ left\n"
        + "/INT left\n"
        + "/INT left\n" // checked
        + "|| left\n"
        + "\n"
        + "+ left\n"
        + "+ left\n" // checked
        + "+ -\n"
        + "- left\n"
        + "- left\n" // checked
        + "- -\n"
        + "EXISTS pre\n"
        + "UNIQUE pre\n"
        + "\n"
        + "< ALL left\n"
        + "< SOME left\n"
        + "<= ALL left\n"
        + "<= SOME left\n"
        + "<> ALL left\n"
        + "<> SOME left\n"
        + "= ALL left\n"
        + "= SOME left\n"
        + "> ALL left\n"
        + "> SOME left\n"
        + ">= ALL left\n"
        + ">= SOME left\n"
        + "BETWEEN ASYMMETRIC -\n"
        + "BETWEEN SYMMETRIC -\n"
        + "IN left\n"
        + "LIKE -\n"
        + "NEGATED POSIX REGEX CASE INSENSITIVE left\n"
        + "NEGATED POSIX REGEX CASE SENSITIVE left\n"
        + "NOT BETWEEN ASYMMETRIC -\n"
        + "NOT BETWEEN SYMMETRIC -\n"
        + "NOT IN left\n"
        + "NOT LIKE -\n"
        + "NOT SIMILAR TO -\n"
        + "POSIX REGEX CASE INSENSITIVE left\n"
        + "POSIX REGEX CASE SENSITIVE left\n"
        + "SIMILAR TO -\n"
        + "\n"
        + "$IS_DIFFERENT_FROM left\n"
        + "< left\n"
        + "<= left\n"
        + "<> left\n"
        + "= left\n"
        + "> left\n"
        + ">= left\n"
        + "CONTAINS left\n"
        + "EQUALS left\n"
        + "IMMEDIATELY PRECEDES left\n"
        + "IMMEDIATELY SUCCEEDS left\n"
        + "IS DISTINCT FROM left\n"
        + "IS NOT DISTINCT FROM left\n"
        + "MEMBER OF left\n"
        + "NOT SUBMULTISET OF left\n"
        + "OVERLAPS left\n"
        + "PRECEDES left\n"
        + "SUBMULTISET OF left\n"
        + "SUCCEEDS left\n"
        + "\n"
        + "FORMAT JSON post\n"
        + "IS A SET post\n"
        + "IS EMPTY post\n"
        + "IS FALSE post\n"
        + "IS JSON ARRAY post\n"
        + "IS JSON OBJECT post\n"
        + "IS JSON SCALAR post\n"
        + "IS JSON VALUE post\n"
        + "IS NOT A SET post\n"
        + "IS NOT EMPTY post\n"
        + "IS NOT FALSE post\n"
        + "IS NOT JSON ARRAY post\n"
        + "IS NOT JSON OBJECT post\n"
        + "IS NOT JSON SCALAR post\n"
        + "IS NOT JSON VALUE post\n"
        + "IS NOT NULL post\n"
        + "IS NOT TRUE post\n"
        + "IS NOT UNKNOWN post\n"
        + "IS NULL post\n"
        + "IS TRUE post\n"
        + "IS UNKNOWN post\n"
        + "\n"
        + "NOT pre\n"
        + "\n"
        + "AND left\n"
        + "\n"
        + "OR left\n"
        + "\n"
        + "-> -\n"
        + "AS -\n"
        + "DESC post\n"
        + "FILTER left\n"
        + "IGNORE NULLS -\n"
        + "OVER left\n"
        + "RESPECT NULLS -\n"
        + "TABLESAMPLE -\n"
        + "\n"
        + "NULLS FIRST post\n"
        + "NULLS LAST post\n"
        + "\n"
        + "INTERSECT left\n"
        + "INTERSECT ALL left\n"
        + "MULTISET INTERSECT ALL left\n"
        + "MULTISET INTERSECT DISTINCT left\n"
        + "\n"
        + "EXCEPT left\n"
        + "EXCEPT ALL left\n"
        + "MULTISET EXCEPT ALL left\n"
        + "MULTISET EXCEPT DISTINCT left\n"
        + "MULTISET UNION ALL left\n"
        + "MULTISET UNION DISTINCT left\n"
        + "UNION left\n"
        + "UNION ALL left\n"
        + "\n"
        + "$throw -\n"
        + "Reinterpret -\n"
        + "TABLE pre\n"
        + "VALUES -\n"
        + "\n"
        + "CALL pre\n"
        + "ESCAPE -\n"
        + "NEW pre\n";
    assertThat(b, hasToString(expected));
  }

  /** Tests that it is an error to insert into the same column twice, even using
   * case-insensitive matching. */
  @Test void testCaseInsensitiveInsert() {  // 测试方法：testCaseInsensitiveInsert
    sql("insert into EMP ([EMPNO], deptno, ^[empno]^)\n"  // 创建SQL测试：解析并验证SQL语句
        + " values (1, 1, 1)")
        .withCaseSensitive(false)
        .withQuoting(Quoting.BRACKET)
        .fails("Target column 'EMPNO' is assigned more than once");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Tests referencing columns from a sub-query that has duplicate column
   * names. (The standard says it should be an error, but we don't right
   * now.) */
  @Test void testCaseInsensitiveSubQuery() {  // 测试方法：testCaseInsensitiveSubQuery
    final SqlValidatorFixture insensitive = fixture()
        .withCaseSensitive(false)
        .withQuoting(Quoting.BRACKET);
    final SqlValidatorFixture sensitive = fixture()
        .withCaseSensitive(true)
        .withUnquotedCasing(Casing.UNCHANGED)
        .withQuoting(Quoting.BRACKET);
    String sql = "select [e] from (\n"
        + "select EMPNO as [e], DEPTNO as d, 1 as [e2] from EMP)";
    sensitive.withSql(sql).ok();  // 断言验证：期望SQL验证通过
    insensitive.withSql(sql).ok();  // 断言验证：期望SQL验证通过

    String sql1 = "select e2 from (\n"
        + "select EMPNO as [e2], DEPTNO as d, 1 as [E] from EMP)";
    insensitive.withSql(sql1).ok();  // 断言验证：期望SQL验证通过
    sensitive.withSql(sql1).ok();  // 断言验证：期望SQL验证通过
  }

  /** Tests using case-insensitive matching of table names. */
  @Test void testCaseInsensitiveTables() {  // 测试方法：testCaseInsensitiveTables
    final SqlValidatorFixture mssql = fixture().withLex(Lex.SQL_SERVER);
    mssql.withSql("select eMp.* from (select * from emp) as EmP").ok();  // 断言验证：期望SQL验证通过
    mssql.withSql("select ^eMp^.* from (select * from emp as EmP)")
        .fails("Unknown identifier 'eMp'");  // 断言验证：期望SQL验证失败并检查错误消息
    mssql.withSql("select eMp.* from (select * from emP) as EmP").ok();  // 断言验证：期望SQL验证通过
    mssql.withSql("select eMp.empNo from (select * from emP) as EmP").ok();  // 断言验证：期望SQL验证通过
    mssql.withSql("select empNo from (select Empno from emP) as EmP").ok();  // 断言验证：期望SQL验证通过
    mssql.withSql("select empNo from (select Empno from emP)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsert() {  // 测试方法：testInsert
    sql("insert into empnullables (empno, ename)\n"  // 创建SQL测试：解析并验证SQL语句
        + "values (1, 'Ambrosia')").ok();  // 断言验证：期望SQL验证通过
    sql("insert into empnullables (empno, ename)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select 1, 'Ardy' from (values 'a')").ok();  // 断言验证：期望SQL验证通过

    final String sql = "insert into emp\n"
        + "values (1, 'nom', 'job', 0, timestamp '1970-01-01 00:00:00', 1, 1,\n"
        + "  1, false)";
    sql(sql).ok();  // 断言验证：期望SQL验证通过

    final String sql2 = "insert into emp (empno, ename, job, mgr, hiredate,\n"
        + "  sal, comm, deptno, slacker)\n"
        + "select 1, 'nom', 'job', 0, timestamp '1970-01-01 00:00:00',\n"
        + "  1, 1, 1, false\n"
        + "from (values 'a')";
    sql(sql2).ok();  // 断言验证：期望SQL验证通过

    sql("insert into empnullables (ename, empno, deptno)\n"  // 创建SQL测试：解析并验证SQL语句
        + "values ('Pat', 1, null)").ok();  // 断言验证：期望SQL验证通过

    final String sql3 = "insert into empnullables (\n"
        + "  empno, ename, job, hiredate)\n"
        + "values (1, 'Jim', 'Baker', timestamp '1970-01-01 00:00:00')";
    sql(sql3).ok();  // 断言验证：期望SQL验证通过

    sql("insert into empnullables (empno, ename)\n"  // 创建SQL测试：解析并验证SQL语句
        + "select 1, 'b' from (values 'a')").ok();  // 断言验证：期望SQL验证通过

    sql("insert into empnullables (empno, ename)\n"  // 创建SQL测试：解析并验证SQL语句
        + "values (1, 'Karl')").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertWithNonEqualSourceSinkFieldsNum() {  // 测试方法：testInsertWithNonEqualSourceSinkFieldsNum
    sql("insert into ^dept^ select sid, ename, deptno "  // 创建SQL测试：解析并验证SQL语句
        + "from "
        + "(select sum(empno) as sid, ename, deptno, sal "
        + "from emp group by ename, deptno, sal)")
        .fails("Number of INSERT target columns \\(2\\) "  // 断言验证：期望SQL验证失败并检查错误消息
            + "does not equal number of source items \\(3\\)");
  }

  @Test void testInsertSubset() {  // 测试方法：testInsertSubset
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);

    final String sql1 = "insert into empnullables\n"
        + "values (1, 'nom', 'job', 0, timestamp '1970-01-01 00:00:00')";
    s.withSql(sql1).ok();  // 断言验证：期望SQL验证通过

    final String sql2 = "insert into empnullables\n"
        + "values (1, 'nom', null, 0, null)";
    s.withSql(sql2).ok();  // 断言验证：期望SQL验证通过
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1510">[CALCITE-1510]
   * INSERT/UPSERT should allow fewer values than columns</a>,
   * check for default value only when target field is null. */
  @Test void testInsertShouldNotCheckForDefaultValue() {  // 测试方法：testInsertShouldNotCheckForDefaultValue
    final int c = CountingFactory.THREAD_CALL_COUNT.get().get();
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);

    final String sql1 = "insert into emp values(1, 'nom', 'job', 0, "
        + "timestamp '1970-01-01 00:00:00', 1, 1, 1, false)";
    s.withSql(sql1).ok();  // 断言验证：期望SQL验证通过
    assertThat("Should not check for default value if column is in INSERT",
        CountingFactory.THREAD_CALL_COUNT.get().get(), is(c));

    // Now add a list of target columns, keeping the query otherwise the same.
    final String sql2 = "insert into emp (empno, ename, job, mgr, hiredate,\n"
        + "  sal, comm, deptno, slacker)\n"
        + "values(1, 'nom', 'job', 0,\n"
        + "  timestamp '1970-01-01 00:00:00', 1, 1, 1, false)";
    s.withSql(sql2).ok();  // 断言验证：期望SQL验证通过
    assertThat("Should not check for default value if column is in INSERT",
        CountingFactory.THREAD_CALL_COUNT.get().get(), is(c));

    // Now remove SLACKER, which is NOT NULL, from the target list.
    final String sql3 = "insert into ^emp^ (empno, ename, job, mgr, hiredate,\n"
        + "  sal, comm, deptno)\n"
        + "values(1, 'nom', 'job', 0,\n"
        + "  timestamp '1970-01-01 00:00:00', 1, 1, 1)";
    s.withSql(sql3)
        .fails("Column 'SLACKER' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    assertThat("Should not check for default value, even if if column is "
            + "missing from INSERT and nullable",
        CountingFactory.THREAD_CALL_COUNT.get().get(),
        is(c));

    // Now remove DEPTNO, which has a default value, from the target list.
    // Will generate an extra call to newColumnDefaultValue at sql-to-rel time,
    // just not yet.
    final String sql4 = "insert into ^emp^ (empno, ename, job, mgr, hiredate,\n"
        + "  sal, comm, slacker)\n"
        + "values(1, 'nom', 'job', 0,\n"
        + "  timestamp '1970-01-01 00:00:00', 1, 1, false)";
    s.withSql(sql4).ok();  // 断言验证：期望SQL验证通过
    assertThat("Missing DEFAULT column generates a call to factory",
        CountingFactory.THREAD_CALL_COUNT.get().get(),
        is(c));
  }

  @Test void testInsertView() {  // 测试方法：testInsertView
    sql("insert into empnullables_20 (ename, empno, comm)\n"  // 创建SQL测试：解析并验证SQL语句
        + "values ('Karl', 1, 1)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertSubsetView() {  // 测试方法：testInsertSubsetView
    sql("insert into empnullables_20\n"  // 创建SQL测试：解析并验证SQL语句
        + "values (1, 'Karl')")
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertModifiableView() {  // 测试方法：testInsertModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("insert into EMP_MODIFIABLEVIEW (empno, ename, job)\n"
        + "values (1, 'Arthur', 'clown')").ok();  // 断言验证：期望SQL验证通过
    s.withSql("insert into EMP_MODIFIABLEVIEW2 (empno, ename, job, extra)\n"
        + "values (1, 'Arthur', 'clown', true)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertSubsetModifiableView() {  // 测试方法：testInsertSubsetModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog()
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003);
    s.withSql("insert into EMP_MODIFIABLEVIEW2\n"
        + "values ('Arthur', 1)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("insert into EMP_MODIFIABLEVIEW2\n"
        + "values ('Arthur', 1, 'Knight', 20, false, 99999, true, timestamp '1370-01-01 00:00:00',"
        + " 1, 100)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertBind() {  // 测试方法：testInsertBind
    // VALUES
    final String sql0 = "insert into empnullables (empno, ename, deptno)\n"
        + "values (?, ?, ?)";
    final String expectedType0 =
        "RecordType(INTEGER ?0, VARCHAR(20) ?1, INTEGER ?2)";
    sql(sql0).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType0));

    // multiple VALUES
    final String sql1 = "insert into empnullables (empno, ename, deptno)\n"
        + "values (?, 'Pat', 1), (2, ?, ?), (3, 'Tod', ?), (4, 'Arthur', null)";
    final String expectedType1 =
        "RecordType(INTEGER ?0, VARCHAR(20) ?1, INTEGER ?2, INTEGER ?3)";
    sql(sql1).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType1));

    // VALUES with expression
    sql("insert into empnullables (ename, empno) values (?, ? + 1)")  // 创建SQL测试：解析并验证SQL语句
        .ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(VARCHAR(20) ?0, INTEGER ?1)"));

    // SELECT
    sql("insert into empnullables (ename, empno) select ?, ? from (values (1))")  // 创建SQL测试：解析并验证SQL语句
        .ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(VARCHAR(20) ?0, INTEGER ?1)"));

    // WITH
    final String sql3 = "insert into empnullables (ename, empno)\n"
        + "with v as (values ('a'))\n"
        + "select ?, ? from (values (1))";
    sql(sql3).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(VARCHAR(20) ?0, INTEGER ?1)"));

    // UNION
    final String sql2 = "insert into empnullables (ename, empno)\n"
        + "select ?, ? from (values (1))\n"
        + "union all\n"
        + "select ?, ? from (values (time '1:2:3'))";
    final String expected2 = "RecordType(VARCHAR(20) ?0, INTEGER ?1,"
        + " VARCHAR(20) ?2, INTEGER ?3)";
    sql(sql2).ok().assertBindType(is(expected2));  // 断言验证：期望SQL验证通过
  }


  @Test void testInsertWithExtendedColumns() {  // 测试方法：testInsertWithExtendedColumns
    final String sql0 = "insert into empnullables\n"
        + " (empno, ename, \"f.dc\" ^varchar(10)^)\n"
        + "values (?, ?, ?)";
    final String expectedType0 =
        "RecordType(INTEGER ?0, VARCHAR(20) ?1, VARCHAR(10) ?2)";
    sql(sql0).withExtendedCatalog()
        .withConformance(SqlConformanceEnum.LENIENT)
        .ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType0))
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .fails("Extended columns not allowed under "  // 断言验证：期望SQL验证失败并检查错误消息
            + "the current SQL conformance level");

    final String sql1 = "insert into empnullables\n"
        + " (empno, ename, dynamic_column ^double^ not null)\n"
        + "values (?, ?, ?)";
    final String expectedType1 =
        "RecordType(INTEGER ?0, VARCHAR(20) ?1, DOUBLE ?2)";
    sql(sql1)
        .withExtendedCatalog()
        .withConformance(SqlConformanceEnum.LENIENT)
        .ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType1))
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .fails("Extended columns not allowed under "  // 断言验证：期望SQL验证失败并检查错误消息
            + "the current SQL conformance level");

    final String sql2 = "insert into struct.t_extend\n"
        + " (f0.c0, f1.c1, \"F2\".\"C2\" ^varchar(20)^ not null)\n"
        + "values (?, ?, ?)";
    final String expectedType2 =
        "RecordType(INTEGER ?0, INTEGER ?1, VARCHAR(20) ?2)";
    sql(sql2)
        .withExtendedCatalog()
        .withConformance(SqlConformanceEnum.LENIENT)
        .ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType2))
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .fails("Extended columns not allowed under "  // 断言验证：期望SQL验证失败并检查错误消息
            + "the current SQL conformance level");
  }

  @Test void testInsertBindSubset() {  // 测试方法：testInsertBindSubset
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);

    // VALUES
    final String sql0 = "insert into empnullables\n"
        + "values (?, ?, ?)";
    final String expectedType0 = "RecordType(INTEGER ?0, VARCHAR(20) ?1, VARCHAR(10) ?2)";
    s.withSql(sql0).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType0));

    // multiple VALUES
    final String sql1 = "insert into empnullables\n"
        + "values (?, 'Pat', 'Tailor'), (2, ?, ?),\n"
        + " (3, 'Tod', ?), (4, 'Arthur', null)";
    final String expectedType1 = "RecordType(INTEGER ?0, VARCHAR(20) ?1, VARCHAR(10) ?2, "
        + "VARCHAR(10) ?3)";
    s.withSql(sql1).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType1));

    // VALUES with expression
    s.withSql("insert into empnullables values (? + 1, ?)").ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(INTEGER ?0, VARCHAR(20) ?1)"));

    // SELECT
    s.withSql("insert into empnullables select ?, ? from (values (1))").ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(INTEGER ?0, VARCHAR(20) ?1)"));

    // WITH
    final String sql3 = "insert into empnullables\n"
        + "with v as (values ('a'))\n"
        + "select ?, ? from (values (1))";
    s.withSql(sql3).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(INTEGER ?0, VARCHAR(20) ?1)"));

    // UNION
    final String sql2 = "insert into empnullables\n"
        + "select ?, ? from (values (1))\n"
        + "union all\n"
        + "select ?, ? from (values (time '1:2:3'))";
    final String expected2 = "RecordType(INTEGER ?0, VARCHAR(20) ?1,"
        + " INTEGER ?2, VARCHAR(20) ?3)";
    s.withSql(sql2).ok().assertBindType(is(expected2));  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertBindView() {  // 测试方法：testInsertBindView
    final String sql = "insert into EMP_MODIFIABLEVIEW (mgr, empno, ename)"
        + " values (?, ?, ?)";
    final String expectedType =
        "RecordType(INTEGER ?0, INTEGER ?1, VARCHAR(20) ?2)";
    sql(sql).withExtendedCatalog().ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is(expectedType));
  }

  @Test void testInsertModifiableViewPassConstraint() {  // 测试方法：testInsertModifiableViewPassConstraint
    sql("insert into EMP_MODIFIABLEVIEW2 (deptno, empno, ename, extra)"  // 创建SQL测试：解析并验证SQL语句
        + " values (20, 100, 'Lex', true)")
        .withExtendedCatalog()
        .ok();  // 断言验证：期望SQL验证通过
    sql("insert into EMP_MODIFIABLEVIEW2 (empno, ename, extra)"  // 创建SQL测试：解析并验证SQL语句
        + " values (100, 'Lex', true)")
        .withExtendedCatalog()
        .ok();  // 断言验证：期望SQL验证通过

    sql("insert into EMP_MODIFIABLEVIEW2 values ('Edward', 20)")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertModifiableViewFailConstraint() {  // 测试方法：testInsertModifiableViewFailConstraint
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into EMP_MODIFIABLEVIEW2 (deptno, empno, ename)"
        + " values (^21^, 100, 'Lex')";
    final String error0 = "Modifiable view constraint is not satisfied"
        + " for column 'DEPTNO' of base table 'EMP_MODIFIABLEVIEW2'";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into EMP_MODIFIABLEVIEW2 (deptno, empno, ename)"
        + " values (^19+1^, 100, 'Lex')";
    final String error1 = "Modifiable view constraint is not satisfied"
        + " for column 'DEPTNO' of base table 'EMP_MODIFIABLEVIEW2'";
    s.withSql(sql1).fails(error1);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql2 = "insert into EMP_MODIFIABLEVIEW2\n"
        + "values ('Arthur', 1, 'Knight', ^27^, false, 99999, true,"
        + "timestamp '1370-01-01 00:00:00', 1, 100)";
    final String error2 = "Modifiable view constraint is not satisfied"
        + " for column 'DEPTNO' of base table 'EMP_MODIFIABLEVIEW2'";
    s.withSql(sql2).fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateModifiableViewPassConstraint() {  // 测试方法：testUpdateModifiableViewPassConstraint
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("update EMP_MODIFIABLEVIEW2"
        + " set deptno = 20, empno = 99"
        + " where ename = 'Lex'").ok();  // 断言验证：期望SQL验证通过
    s.withSql("update EMP_MODIFIABLEVIEW2"
        + " set empno = 99"
        + " where ename = 'Lex'").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUpdateModifiableViewFailConstraint() {  // 测试方法：testUpdateModifiableViewFailConstraint
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "update EMP_MODIFIABLEVIEW2"
        + " set deptno = ^21^, empno = 99"
        + " where ename = 'Lex'";
    final String error = "Modifiable view constraint is not satisfied"
        + " for column 'DEPTNO' of base table 'EMP_MODIFIABLEVIEW2'";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "update EMP_MODIFIABLEVIEW2"
        + " set deptno = ^19 + 1^, empno = 99"
        + " where ename = 'Lex'";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertTargetTableWithVirtualColumns() {  // 测试方法：testInsertTargetTableWithVirtualColumns
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("insert into VIRTUALCOLUMNS.VC_T1\n"
        + "select a, b, c from VIRTUALCOLUMNS.VC_T2").ok();  // 断言验证：期望SQL验证通过

    final String sql0 = "insert into ^VIRTUALCOLUMNS.VC_T1^\n"
        + "values(1, 2, 'abc', 3, 4)";
    final String error0 = "Cannot INSERT into generated column 'D'";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into ^VIRTUALCOLUMNS.VC_T1^\n"
        + "values(1, 2, 'abc', DEFAULT, DEFAULT)";
    s.withSql(sql1).ok();  // 断言验证：期望SQL验证通过

    final String sql2 = "insert into ^VIRTUALCOLUMNS.VC_T1^\n"
        + "values(1, 2, 'abc', DEFAULT)";
    final String error2 = "(?s).*Number of INSERT target columns \\(5\\) "
        + "does not equal number of source items \\(4\\).*";
    s.withSql(sql2).fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql3 = "insert into ^VIRTUALCOLUMNS.VC_T1^\n"
        + "values(1, 2, 'abc', DEFAULT, DEFAULT, DEFAULT)";
    final String error3 = "(?s).*Number of INSERT target columns \\(5\\) "
        + "does not equal number of source items \\(6\\).*";
    s.withSql(sql3).fails(error3);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql4 = "insert into VIRTUALCOLUMNS.VC_T1\n"
        + "^values(1, '2', 'abc')^";
    final String error4 = "(?s).*Cannot assign to target field 'B' of type BIGINT "
        + "from source field 'EXPR\\$1' of type CHAR\\(1\\).*";
    s.withSql(sql4).withTypeCoercion(false).fails(error4);  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql(sql4).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertFailNullability() {  // 测试方法：testInsertFailNullability
    sql("insert into ^emp^ (ename) values ('Kevin')")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into ^emp^ (empno) values (10)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into emp (empno, ename, deptno) ^values (5, null, 5)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertSubsetFailNullability() {  // 测试方法：testInsertSubsetFailNullability
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);

    s.withSql("insert into ^emp^ values (1)")
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("insert into emp ^values (null, 'Liam')^")
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("insert into emp ^values (45, null, 5)^")
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertViewFailNullability() {  // 测试方法：testInsertViewFailNullability
    sql("insert into ^emp_20^ (ename) values ('Jake')")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into ^emp_20^ (empno) values (9)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into emp_20 (empno, ename, mgr) ^values (5, null, 5)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertSubsetViewFailNullability() {  // 测试方法：testInsertSubsetViewFailNullability
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);

    s.withSql("insert into ^EMP_20^ values (1)")
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("insert into EMP_20 ^values (null, 'Liam')^")
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("insert into EMP_20 ^values (45, null)^")
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertBindFailNullability() {  // 测试方法：testInsertBindFailNullability
    sql("insert into ^emp^ (ename) values (?)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into ^emp^ (empno) values (?)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into emp (empno, ename, deptno) ^values (?, null, 5)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertBindSubsetFailNullability() {  // 测试方法：testInsertBindSubsetFailNullability
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);

    s.withSql("insert into ^emp^ values (?)")
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("insert into emp ^values (null, ?)^")
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("insert into emp ^values (?, null)^")
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertSubsetDisallowed() {  // 测试方法：testInsertSubsetDisallowed
    sql("insert into ^emp^ values (1)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(9\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(1\\)");
    sql("insert into ^emp^ values (null)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(9\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(1\\)");
    sql("insert into ^emp^ values (1, 'Kevin')")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(9\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(2\\)");
  }

  @Test void testInsertSubsetViewDisallowed() {  // 测试方法：testInsertSubsetViewDisallowed
    sql("insert into ^emp_20^ values (1)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(8\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(1\\)");
    sql("insert into ^emp_20^ values (null)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(8\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(1\\)");
    sql("insert into ^emp_20^ values (?, ?)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(8\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(2\\)");
  }

  @Test void testInsertBindSubsetDisallowed() {  // 测试方法：testInsertBindSubsetDisallowed
    sql("insert into ^emp^ values (?)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(9\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(1\\)");
    sql("insert into ^emp^ values (?, ?)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(9\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(2\\)");
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-985">[CALCITE-985]
   * Validate MERGE</a>. */
  @Test void testMergeInto() {  // 测试方法：testMergeInto
    sql("merge into empnullables e "  // 创建SQL测试：解析并验证SQL语句
        + "using (select * from emp where deptno is null) t "
        + "on e.empno = t.empno "
        + "when matched then update "
        + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
        + "when not matched then insert (empno, ename, deptno, sal) "
        + "values(t.empno, t.ename, 10, t.sal * .15)")
        .ok();  // 断言验证：期望SQL验证通过

    sql("merge into ^emp^ e "  // 创建SQL测试：解析并验证SQL语句
        + "using (select * from empnullables where deptno is null) t "
        + "on e.empno = t.empno "
        + "when matched then update "
        + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
        + "when not matched then insert (empno, ename, deptno, sal) "
        + "values(t.empno, t.ename, 10, t.sal * .15)")
        .fails("Column 'JOB' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testMergeFailCaseSensitivity() {  // 测试方法：testMergeFailCaseSensitivity
    final SqlValidatorFixture s = fixture()
        .withExtendedCatalog();
    final String sql0 = "merge into EMP_MODIFIABLEVIEW e "
        + "using (select * from emp where deptno is null) t "
        + "on e.empno = t.empno "
        + "when matched then update "
        + "set ename = t.ename, sal = t.sal * .1 "
        + "when not matched then insert (^\"empno\"^, ename, sal) "
        + "values(t.empno, t.ename, t.sal * .15)";
    s.withSql(sql0).fails("Unknown target column 'empno'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testMergeFailExcludedColumn() {  // 测试方法：testMergeFailExcludedColumn
    sql("merge into empnullables e "  // 创建SQL测试：解析并验证SQL语句
        + "using (select * from emp where deptno is null) t "
        + "on e.empno = t.empno "
        + "when matched then update "
        + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
        + "when not matched then insert (empno, ^name^, deptno, sal) "
        + "values(t.empno, t.ename, 10, t.sal * .15)")
        .fails("Unknown target column 'NAME'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testMergeBindWithCustomInitializerExpressionFactory() {  // 测试方法：testMergeBindWithCustomInitializerExpressionFactory
    sql("merge into empdefaults e "  // 创建SQL测试：解析并验证SQL语句
        + "using (select * from emp where deptno is null) t "
        + "on e.empno = t.empno "
        + "when matched then update "
        + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
        + "when not matched then insert (deptno) "
        + "values(?)").ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(INTEGER ?0)"));
    sql("merge into empdefaults e "  // 创建SQL测试：解析并验证SQL语句
        + "using (select * from emp where deptno is null) t "
        + "on e.empno = t.empno "
        + "when matched then update "
        + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
        + "when not matched then insert (empno, ename) "
        + "values(?, ?)").ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(INTEGER ?0, VARCHAR(20) ?1)"));
    sql("merge into empdefaults e "  // 创建SQL测试：解析并验证SQL语句
        + "using (select * from emp where deptno is null) t "
        + "on e.empno = t.empno "
        + "when matched then update "
        + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
        + "when not matched then insert (empno, ename) "
        + "values(^null^, ?)")
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testMergeBindSubsetWithCustomInitializerExpressionFactory() {  // 测试方法：testMergeBindSubsetWithCustomInitializerExpressionFactory
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);
    s.withSql("merge into empdefaults e "
            + "using (select * from emp where deptno is null) t "
            + "on e.empno = t.empno "
            + "when matched then update "
            + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
            + "when not matched then insert (empno, ename, deptno, sal) "
            + "values(t.empno, t.ename, ?, t.sal * .15)")
        .assertBindType(is("RecordType(INTEGER ?0)"));
    s.withSql("merge into empdefaults e "
            + "using (select * from emp where deptno is null) t "
            + "on e.empno = t.empno "
            + "when matched then update "
            + "set ename = t.ename, deptno = t.deptno, sal = t.sal * .1 "
            + "when not matched then insert (empno, ename, deptno, sal) "
            + "values(^null^, t.ename, ?, t.sal * .15)")
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectExtendedColumnDuplicate() {  // 测试方法：testSelectExtendedColumnDuplicate
    sql("select deptno, extra from emp (extra int, \"extra\" boolean)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno, extra from emp (extra int, \"extra\" int)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select deptno, extra from emp (extra int, ^extra^ int)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate name 'EXTRA' in column list");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select deptno, extra from emp (extra int, ^extra^ boolean)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate name 'EXTRA' in column list");  // 断言验证：期望SQL验证失败并检查错误消息
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "select deptno, extra\n"
        + "from EMP_MODIFIABLEVIEW (extra int, ^extra^ int)";
    s.withSql(sql0).fails("Duplicate name 'EXTRA' in column list");  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "select deptno, extra from EMP_MODIFIABLEVIEW"
        + " (extra int, ^extra^ boolean)";
    s.withSql(sql1).fails("Duplicate name 'EXTRA' in column list");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectViewFailExcludedColumn() {  // 测试方法：testSelectViewFailExcludedColumn
    final String sql = "select ^deptno^, empno from EMP_MODIFIABLEVIEW";
    final String error = "Column 'DEPTNO' not found in any table";
    sql(sql).withExtendedCatalog().fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectViewExtendedColumnCollision() {  // 测试方法：testSelectViewExtendedColumnCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR\n"
        + " from EMP_MODIFIABLEVIEW3 extend (SAL int)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select ENAME, EMPNO, JOB, SLACKER, SAL, \"Sal\", HIREDATE, MGR\n"
        + " from EMP_MODIFIABLEVIEW3 extend (\"Sal\" VARCHAR)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testSelectViewExtendedColumnExtendedCollision() {  // 测试方法：testSelectViewExtendedColumnExtendedCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, EXTRA\n"
        + " from EMP_MODIFIABLEVIEW2 extend (EXTRA boolean)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, EXTRA,"
        + " \"EXtra\"\n"
        + " from EMP_MODIFIABLEVIEW2 extend (\"EXtra\" VARCHAR)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testSelectViewExtendedColumnUnderlyingCollision() {  // 测试方法：testSelectViewExtendedColumnUnderlyingCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, COMM\n"
        + " from EMP_MODIFIABLEVIEW3 extend (COMM int)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
    s.withSql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, \"comM\"\n"
        + " from EMP_MODIFIABLEVIEW3 extend (\"comM\" BOOLEAN)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testSelectExtendedColumnCollision() {  // 测试方法：testSelectExtendedColumnCollision
    sql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, COMM\n"  // 创建SQL测试：解析并验证SQL语句
        + " from EMPDEFAULTS extend (COMM int)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
    sql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, COMM, \"ComM\"\n"  // 创建SQL测试：解析并验证SQL语句
        + " from EMPDEFAULTS extend (\"ComM\" int)\n"
        + " where SAL = 20").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testSelectExtendedColumnFailCollision() {  // 测试方法：testSelectExtendedColumnFailCollision
    sql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, COMM\n"  // 创建SQL测试：解析并验证SQL语句
        + " from EMPDEFAULTS extend (^COMM^ boolean)\n"
        + " where SAL = 20")
        .fails("Cannot assign to target field 'COMM' of type INTEGER "  // 断言验证：期望SQL验证失败并检查错误消息
            + "from source field 'COMM' of type BOOLEAN");
    sql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, COMM\n"  // 创建SQL测试：解析并验证SQL语句
        + " from EMPDEFAULTS extend (^EMPNO^ integer)\n"
        + " where SAL = 20")
        .fails("Cannot assign to target field 'EMPNO' of type INTEGER NOT NULL "  // 断言验证：期望SQL验证失败并检查错误消息
            + "from source field 'EMPNO' of type INTEGER");
    sql("select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE, MGR, COMM\n"  // 创建SQL测试：解析并验证SQL语句
        + " from EMPDEFAULTS extend (^\"EMPNO\"^ integer)\n"
        + " where SAL = 20")
        .fails("Cannot assign to target field 'EMPNO' of type INTEGER NOT NULL "  // 断言验证：期望SQL验证失败并检查错误消息
            + "from source field 'EMPNO' of type INTEGER");
  }

  @Test void testSelectViewExtendedColumnFailCollision() {  // 测试方法：testSelectViewExtendedColumnFailCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE,"
        + " MGR, EXTRA\n"
        + "from EMP_MODIFIABLEVIEW2 extend (^SLACKER^ integer)\n"
        + " where SAL = 20";
    final String error0 = "Cannot assign to target field 'SLACKER' of type"
        + " BOOLEAN from source field 'SLACKER' of type INTEGER";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE,"
        + " MGR, COMM\n"
        + "from EMP_MODIFIABLEVIEW2 extend (^EMPNO^ integer)\n"
        + " where SAL = 20";
    final String error1 = "Cannot assign to target field 'EMPNO' of type"
        + " INTEGER NOT NULL from source field 'EMPNO' of type INTEGER";
    s.withSql(sql1).fails(error1);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectViewExtendedColumnFailExtendedCollision() {  // 测试方法：testSelectViewExtendedColumnFailExtendedCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE,"
        + " MGR, EXTRA\n"
        + "from EMP_MODIFIABLEVIEW2 extend (^EXTRA^ integer)\n"
        + " where SAL = 20";
    final String error = "Cannot assign to target field 'EXTRA' of type"
        + " BOOLEAN from source field 'EXTRA' of type INTEGER";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE,"
        + " MGR, EXTRA\n"
        + "from EMP_MODIFIABLEVIEW2 extend (^\"EXTRA\"^ integer)\n"
        + " where SAL = 20";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectViewExtendedColumnFailUnderlyingCollision() {  // 测试方法：testSelectViewExtendedColumnFailUnderlyingCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE,"
        + " MGR, COMM\n"
        + "from EMP_MODIFIABLEVIEW3 extend (^COMM^ boolean)\n"
        + "where SAL = 20";
    final String error = "Cannot assign to target field 'COMM' of type INTEGER"
        + " from source field 'COMM' of type BOOLEAN";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "select ENAME, EMPNO, JOB, SLACKER, SAL, HIREDATE,"
        + " MGR, COMM\n"
        + "from EMP_MODIFIABLEVIEW3 extend (^\"COMM\"^ boolean)\n"
        + " where SAL = 20";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectFailCaseSensitivity() {  // 测试方法：testSelectFailCaseSensitivity
    sql("select ^\"empno\"^, ename, deptno from EMP")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'empno' not found in any table; did you mean 'EMPNO'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^\"extra\"^, ename, deptno from EMP (extra boolean)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'extra' not found in any table; did you mean 'EXTRA'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select ^extra^, ename, deptno from EMP (\"extra\" boolean)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'EXTRA' not found in any table; did you mean 'extra'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertFailCaseSensitivity() {  // 测试方法：testInsertFailCaseSensitivity
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into EMP_MODIFIABLEVIEW"
        + " (^\"empno\"^, ename, deptno)"
        + " values (45, 'Jake', 5)";
    s.withSql(sql0).fails("Unknown target column 'empno'");  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into EMP_MODIFIABLEVIEW (\"extra\" int)"
        + " (^extra^, ename, deptno)"
        + " values (45, 'Jake', 5)";
    s.withSql(sql1).fails("Unknown target column 'EXTRA'");  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql2 = "insert into EMP_MODIFIABLEVIEW (extra int)"
        + " (^\"extra\"^, ename, deptno)"
        + " values (45, 'Jake', 5)";
    s.withSql(sql2).fails("Unknown target column 'extra'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertFailExcludedColumn() {  // 测试方法：testInsertFailExcludedColumn
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql = ""
        + "insert into EMP_MODIFIABLEVIEW (empno, ename, ^deptno^)"
        + " values (45, 'Jake', 5)";
    s.withSql(sql).fails("Unknown target column 'DEPTNO'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertBindViewFailExcludedColumn() {  // 测试方法：testInsertBindViewFailExcludedColumn
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql = "insert into EMP_MODIFIABLEVIEW (empno, ename, ^deptno^)"
        + " values (?, ?, ?)";
    s.withSql(sql).fails("Unknown target column 'DEPTNO'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertWithCustomInitializerExpressionFactory() {  // 测试方法：testInsertWithCustomInitializerExpressionFactory
    sql("insert into empdefaults (deptno) values (1)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("insert into empdefaults (ename, empno) values ('Quan', 50)").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("insert into empdefaults (ename, deptno) ^values (null, 1)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into ^empdefaults^ values (null, 'Tod')")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(9\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(2\\)");
  }

  @Test void testInsertSubsetWithCustomInitializerExpressionFactory() {  // 测试方法：testInsertSubsetWithCustomInitializerExpressionFactory
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);

    s.withSql("insert into empdefaults values (101)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("insert into empdefaults values (101, 'Coral')").ok();  // 断言验证：期望SQL验证通过
    s.withSql("insert into empdefaults ^values (null, 'Tod')^")
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("insert into empdefaults ^values (78, null)^")
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertBindWithCustomInitializerExpressionFactory() {  // 测试方法：testInsertBindWithCustomInitializerExpressionFactory
    sql("insert into empdefaults (deptno) values (?)").ok()  // 创建SQL测试：解析并验证SQL语句
        .assertBindType(is("RecordType(INTEGER ?0)"));
    sql("insert into empdefaults (ename, empno) values (?, ?)").ok()  // 创建SQL测试：解析并验证SQL语句
        .assertBindType(is("RecordType(VARCHAR(20) ?0, INTEGER ?1)"));
    sql("insert into empdefaults (ename, deptno) ^values (null, ?)^")  // 创建SQL测试：解析并验证SQL语句
        .fails("Column 'ENAME' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into ^empdefaults^ values (null, ?)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Number of INSERT target columns \\(9\\) does not equal "  // 断言验证：期望SQL验证失败并检查错误消息
            + "number of source items \\(2\\)");
  }

  @Test void testInsertBindSubsetWithCustomInitializerExpressionFactory() {  // 测试方法：testInsertBindSubsetWithCustomInitializerExpressionFactory
    final SqlValidatorFixture s = fixture().withConformance(SqlConformanceEnum.PRAGMATIC_2003);
    s.withSql("insert into empdefaults values (101, ?)").ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(VARCHAR(20) ?0)"));
    s.withSql("insert into empdefaults ^values (null, ?)^")
        .fails("Column 'EMPNO' has no default value and does not allow NULLs");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertBindWithCustomColumnResolving() {  // 测试方法：testInsertBindWithCustomColumnResolving
    final SqlConformanceEnum pragmatic = SqlConformanceEnum.PRAGMATIC_2003;

    final String sql = "insert into struct.t\n"
        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    final String expected = "RecordType(VARCHAR(20) ?0, VARCHAR(20) ?1,"
        + " INTEGER ?2, BOOLEAN ?3, INTEGER ?4, INTEGER ?5, INTEGER ?6,"
        + " INTEGER ?7, INTEGER ?8)";
    sql(sql).ok().assertBindType(is(expected));  // 断言验证：期望SQL验证通过

    final String sql2 =
        "insert into struct.t_nullables (c0, c2, c1) values (?, ?, ?)";
    final String expected2 =
        "RecordType(INTEGER ?0, INTEGER ?1, VARCHAR(20) ?2)";
    sql(sql2).withConformance(pragmatic).ok().assertBindType(is(expected2));  // 断言验证：期望SQL验证通过

    final String sql3 =
        "insert into struct.t_nullables (f1.c0, f1.c2, f0.c1) values (?, ?, ?)";
    final String expected3 =
        "RecordType(INTEGER ?0, INTEGER ?1, INTEGER ?2)";
    sql(sql3).withConformance(pragmatic).ok().assertBindType(is(expected3));  // 断言验证：期望SQL验证通过

    sql("insert into struct.t_nullables (c0, ^c4^, c1) values (?, ?, ?)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(pragmatic)
        .fails("Unknown target column 'C4'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("insert into struct.t_nullables (^a0^, c2, c1) values (?, ?, ?)")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(pragmatic)
        .fails("Unknown target column 'A0'");  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql4 = "insert into struct.t_nullables (\n"
        + "  f1.c0, ^f0.a0^, f0.c1) values (?, ?, ?)";
    sql(sql4).withConformance(pragmatic)
        .fails("Unknown target column 'F0.A0'");  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql5 = "insert into struct.t_nullables (\n"
        + "  f1.c0, f1.c2, ^f1.c0^) values (?, ?, ?)";
    sql(sql5).withConformance(pragmatic)
        .fails("Target column '\"F1\".\"C0\"' is assigned more than once");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateBind() {  // 测试方法：testUpdateBind
    final String sql = "update emp\n"
        + "set ename = ?\n"
        + "where deptno = ?";
    sql(sql).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(VARCHAR(20) ?0, INTEGER ?1)"));
  }

  @Test void testDeleteBind() {  // 测试方法：testDeleteBind
    final String sql = "delete from emp\n"
        + "where deptno = ?\n"
        + "or ename = ?";
    sql(sql).ok()  // 断言验证：期望SQL验证通过
        .assertBindType(is("RecordType(INTEGER ?0, VARCHAR(20) ?1)"));
  }

  @Test void testStream() {  // 测试方法：testStream
    sql("select stream * from orders").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select stream * from ^emp^")  // 创建SQL测试：解析并验证SQL语句
        .fails(cannotConvertToStream("EMP"));  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from ^orders^")  // 创建SQL测试：解析并验证SQL语句
        .fails(cannotConvertToRelation("ORDERS"));  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStreamWhere() {  // 测试方法：testStreamWhere
    sql("select stream * from orders where productId < 10").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select stream * from ^emp^ where deptno = 10")  // 创建SQL测试：解析并验证SQL语句
        .fails(cannotConvertToStream("EMP"));  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream * from ^emp^ as e where deptno = 10")  // 创建SQL测试：解析并验证SQL语句
        .fails(cannotConvertToStream("E"));  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream * from (^select * from emp as e1^) as e\n"  // 创建SQL测试：解析并验证SQL语句
        + "where deptno = 10")
        .fails("Cannot convert table 'E' to stream");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from ^orders^ where productId > 10")  // 创建SQL测试：解析并验证SQL语句
        .fails(cannotConvertToRelation("ORDERS"));  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStreamGroupBy() {  // 测试方法：testStreamGroupBy
    sql("select stream rowtime, productId, count(*) as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "group by productId, rowtime").ok();  // 断言验证：期望SQL验证通过
    sql("select stream floor(rowtime to hour) as rowtime, productId,\n"  // 创建SQL测试：解析并验证SQL语句
        + " count(*) as c\n"
        + "from orders\n"
        + "group by floor(rowtime to hour), productId").ok();  // 断言验证：期望SQL验证通过
    sql("select stream productId, count(*) as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "^group by productId^")
        .fails(STR_AGG_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream ^count(*)^ as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders")
        .fails(STR_AGG_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream count(*) as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders ^group by ()^")
        .fails(STR_AGG_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStreamHaving() {  // 测试方法：testStreamHaving
    sql("select stream rowtime, productId, count(*) as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "group by productId, rowtime\n"
        + "having count(*) > 5").ok();  // 断言验证：期望SQL验证通过
    sql("select stream floor(rowtime to hour) as rowtime, productId,\n"  // 创建SQL测试：解析并验证SQL语句
        + " count(*) as c\n"
        + "from orders\n"
        + "group by floor(rowtime to hour), productId\n"
        + "having false").ok();  // 断言验证：期望SQL验证通过
    sql("select stream productId, count(*) as c\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "^group by productId^\n"
        + "having count(*) > 5")
        .fails(STR_AGG_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream 1\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "having ^count(*) > 3^")
        .fails(STR_AGG_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Tests that various expressions are monotonic. */
  @Test void testMonotonic() {  // 测试方法：testMonotonic
    sql("select stream floor(rowtime to hour) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream ceil(rowtime to minute) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream extract(minute from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.NOT_MONOTONIC));
    sql("select stream (rowtime - timestamp '1970-01-01 00:00:00') hour from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "cast((rowtime - timestamp '1970-01-01 00:00:00') hour as integer)\n"
        + "from orders")
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "cast((rowtime - timestamp '1970-01-01 00:00:00') hour as integer) / 15\n"
        + "from orders")
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "mod(cast((rowtime - timestamp '1970-01-01 00:00:00') hour as integer), 15)\n"
        + "from orders")
        .assertMonotonicity(is(SqlMonotonicity.NOT_MONOTONIC));

    // constant
    sql("select stream 1 - 2 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT));
    sql("select stream 1 + 2 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT));

    // extract(YEAR) is monotonic, extract(other time unit) is not
    sql("select stream extract(year from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream extract(month from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.NOT_MONOTONIC));

    // <monotonic> - constant
    sql("select stream extract(year from rowtime) - 3 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream extract(year from rowtime) * 5 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream extract(year from rowtime) * -5 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.DECREASING));

    // <monotonic> / constant
    sql("select stream extract(year from rowtime) / -5 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.DECREASING));
    sql("select stream extract(year from rowtime) / 5 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream extract(year from rowtime) / 0 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT)); // +inf is constant!
    sql("select stream extract(year from rowtime) / null from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT));
    sql("select stream null / extract(year from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT));
    sql("select stream extract(year from rowtime) / cast(null as integer) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT));
    sql("select stream cast(null as integer) / extract(year from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT));

    // constant / <monotonic> is not monotonic (we don't know whether sign of
    // expression ever changes)
    sql("select stream 5 / extract(year from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.NOT_MONOTONIC));

    // <monotonic> * constant
    sql("select stream extract(year from rowtime) * -5 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.DECREASING));
    sql("select stream extract(year from rowtime) * 5 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream extract(year from rowtime) * 0 from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT)); // 0 is constant!

    // constant * <monotonic>
    sql("select stream -5 * extract(year from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.DECREASING));
    sql("select stream 5 * extract(year from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
    sql("select stream 0 * extract(year from rowtime) from orders")  // 创建SQL测试：解析并验证SQL语句
        .assertMonotonicity(is(SqlMonotonicity.CONSTANT));

    // <monotonic> - <monotonic>
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "extract(year from rowtime) - extract(year from rowtime)\n"
        + "from orders")
        .assertMonotonicity(is(SqlMonotonicity.NOT_MONOTONIC));

    // <monotonic> + <monotonic>
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "extract(year from rowtime) + extract(year from rowtime)\n"
        + "from orders")
        .assertMonotonicity(is(SqlMonotonicity.INCREASING));
  }

  @Test void testStreamUnionAll() {  // 测试方法：testStreamUnionAll
    sql("select orderId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from ^orders^\n"
        + "union all\n"
        + "select orderId\n"
        + "from shipments")
        .fails(cannotConvertToRelation("ORDERS"));  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream orderId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "union all\n"
        + "^select orderId\n"
        + "from shipments^")
        .fails(STR_SET_OP_INCONSISTENT);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select empno\n"  // 创建SQL测试：解析并验证SQL语句
        + "from emp\n"
        + "union all\n"
        + "^select stream orderId\n"
        + "from orders^")
        .fails(STR_SET_OP_INCONSISTENT);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream orderId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "union all\n"
        + "select stream orderId\n"
        + "from shipments").ok();  // 断言验证：期望SQL验证通过
    sql("select stream rowtime, orderId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "union all\n"
        + "select stream rowtime, orderId\n"
        + "from shipments\n"
        + "order by rowtime").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testStreamValues() {  // 测试方法：testStreamValues
    sql("select stream * from (^values 1) as e^")  // 创建SQL测试：解析并验证SQL语句
        .fails(cannotConvertToStream("E"));  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream * from (^values 1) as e (c)^")  // 创建SQL测试：解析并验证SQL语句
        .fails(cannotConvertToStream("E"));  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream orderId from orders\n"  // 创建SQL测试：解析并验证SQL语句
        + "union all\n"
        + "^values 1^")
        .fails(STR_SET_OP_INCONSISTENT);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("values 1, 2\n"  // 创建SQL测试：解析并验证SQL语句
        + "union all\n"
        + "^select stream orderId from orders^\n")
        .fails(STR_SET_OP_INCONSISTENT);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStreamOrderBy() {  // 测试方法：testStreamOrderBy
    sql("select stream *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "order by rowtime").ok();  // 断言验证：期望SQL验证通过
    sql("select stream *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "order by floor(rowtime to hour)").ok();  // 断言验证：期望SQL验证通过
    sql("select stream floor(rowtime to minute), productId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "order by floor(rowtime to hour)").ok();  // 断言验证：期望SQL验证通过
    sql("select stream floor(rowtime to minute), productId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "order by floor(rowtime to minute), productId desc").ok();  // 断言验证：期望SQL验证通过
    sql("select stream *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "order by ^productId^, rowtime")
        .fails(STR_ORDER_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "order by ^rowtime desc^")
        .fails(STR_ORDER_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select stream *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "order by floor(rowtime to hour), rowtime desc").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testStreamJoin() {  // 测试方法：testStreamJoin
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + " orders.rowtime as rowtime, orders.orderId as orderId,\n"
        + " products.supplierId as supplierId\n"
        + "from orders\n"
        + "join products on orders.productId = products.productId").ok();  // 断言验证：期望SQL验证通过
    sql("^select stream *\n"  // 创建SQL测试：解析并验证SQL语句
        + "from products\n"
        + "join suppliers on products.supplierId = suppliers.supplierId^")
        .fails(cannotStreamResultsForNonStreamingInputs("PRODUCTS, SUPPLIERS"));  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testDummy() {  // 测试方法：testDummy
    // (To debug individual statements, paste them into this method.)
  }

  @Test void testCustomColumnResolving() {  // 测试方法：testCustomColumnResolving
    checkCustomColumnResolving("T");
  }

  @Test void testCustomColumnResolvingWithView() {  // 测试方法：testCustomColumnResolvingWithView
    checkCustomColumnResolving("T_10");
  }

  private void checkCustomColumnResolving(String table) {
    // Table STRUCT.T is defined as: (
    //   K0 VARCHAR(20) NOT NULL,
    //   C1 VARCHAR(20) NOT NULL,
    //   RecordType:PEEK_FIELDS_DEFAULT(
    //     C0 INTEGER NOT NULL,
    //     C1 INTEGER NOT NULL) F0,
    //   RecordType:PEEK_FIELDS(
    //      C0 INTEGER,
    //      C2 INTEGER NOT NULL,
    //      A0 INTEGER NOT NULL) F1,
    //   RecordType:PEEK_FIELDS(
    //      C3 INTEGER NOT NULL,
    //      A0 BOOLEAN NOT NULL) F2)
    //
    // The labels 'PEEK_FIELDS_DEFAULT' and 'PEEK_FIELDS' mean that F0, F1 and
    // F2 can all be transparent. F0 has default struct priority; F1 and F2 have
    // lower priority.

    sql("select * from struct." + table).ok();

    // Resolve K0 as top-level column K0.
    sql("select k0 from struct." + table)
        .type("RecordType(VARCHAR(20) NOT NULL K0) NOT NULL");

    // Resolve C2 as secondary-level column F1.C2.
    sql("select c2 from struct." + table)
        .type("RecordType(INTEGER NOT NULL C2) NOT NULL");

    // Resolve F1.C2 as fully qualified column F1.C2.
    sql("select f1.c2 from struct." + table)
        .type("RecordType(INTEGER NOT NULL C2) NOT NULL");

    // Resolve C1 as top-level column C1 as opposed to F0.C1.
    sql("select c1 from struct." + table)
        .type("RecordType(VARCHAR(20) NOT NULL C1) NOT NULL");

    // Resolve C0 as secondary-level column F0.C0 as opposed to F1.C0, since F0
    // has the default priority.
    sql("select c0 from struct." + table)
        .type("RecordType(INTEGER NOT NULL C0) NOT NULL");

    // Resolve F1.C0 as fully qualified column F1.C0 (as evidenced by "INTEGER"
    // rather than "INTEGER NOT NULL")
    sql("select f1.c0 from struct." + table)
        .type("RecordType(INTEGER C0) NOT NULL");

    // Fail ambiguous column reference A0, since F1.A0 and F2.A0 both exist with
    // the same resolving priority.
    sql("select ^a0^ from struct." + table)
        .fails("Column 'A0' is ambiguous");

    // Resolve F2.A0 as fully qualified column F2.A0.
    sql("select f2.a0 from struct." + table)
        .type("RecordType(BOOLEAN NOT NULL A0) NOT NULL");

    // Resolve T0.K0 as top-level column K0, since T0 is recognized as the table
    // alias.
    sql("select t0.k0 from struct." + table + " t0")
        .type("RecordType(VARCHAR(20) NOT NULL K0) NOT NULL");

    // Resolve T0.C2 as secondary-level column F1.C2, since T0 is recognized as
    // the table alias here.
    sql("select t0.c2 from struct." + table + " t0")
        .type("RecordType(INTEGER NOT NULL C2) NOT NULL");

    // Resolve F0.C2 as secondary-level column F1.C2, since F0 is recognized as
    // the table alias here.
    sql("select f0.c2 from struct." + table + " f0")
        .type("RecordType(INTEGER NOT NULL C2) NOT NULL");

    // Resolve F0.C1 as top-level column C1 as opposed to F0.C1, since F0 is
    // recognized as the table alias here.
    sql("select f0.c1 from struct." + table + " f0")
        .type("RecordType(VARCHAR(20) NOT NULL C1) NOT NULL");

    // Resolve C1 as inner INTEGER column not top-level VARCHAR column.
    sql("select f0.f0.c1 from struct." + table + " f0")
        .type("RecordType(INTEGER NOT NULL C1) NOT NULL");

    // Resolve <table>.C1 as top-level column C1 as opposed to F0.C1, since <table> is
    // recognized as the table name.
    sql("select " + table + ".c1 from struct." + table)
        .type("RecordType(VARCHAR(20) NOT NULL C1) NOT NULL");

    // Alias "f0" obscures table name "<table>"
    sql("select ^" + table + "^.c1 from struct." + table + " f0")
        .fails("Table '" + table + "' not found");

    // Resolve STRUCT.<table>.C1 as top-level column C1 as opposed to F0.C1, since
    // STRUCT.<table> is recognized as the schema and table name.
    sql("select struct." + table + ".c1 from struct." + table)
        .type("RecordType(VARCHAR(20) NOT NULL C1) NOT NULL");

    // Table alias "f0" obscures table name "STRUCT.<table>"
    sql("select ^struct." + table + "^.c1 from struct." + table + " f0")
        .fails("Table 'STRUCT." + table + "' not found");

    // Resolve F0.F0.C1 as secondary-level column F0.C1, since the first F0 is
    // recognized as the table alias here.
    sql("select f0.f0.c1 from struct." + table + " f0")
        .type("RecordType(INTEGER NOT NULL C1) NOT NULL");

    // Resolve <table>.F0.C1 as secondary-level column F0.C1, since <table> is
    // recognized as the table name.
    sql("select " + table + ".f0.c1 from struct." + table)
        .type("RecordType(INTEGER NOT NULL C1) NOT NULL");

    // Table alias obscures
    sql("select ^" + table + ".f0^.c1 from struct." + table + " f0")
        .fails("Table '" + table + ".F0' not found");

    // Resolve STRUCT.<table>.F0.C1 as secondary-level column F0.C1, since
    // STRUCT.<table> is recognized as the schema and table name.
    sql("select struct." + table + ".f0.c1 from struct." + table)
        .type("RecordType(INTEGER NOT NULL C1) NOT NULL");

    // Table alias "f0" obscures table name "STRUCT.<table>"
    sql("select ^struct." + table + ".f0^.c1 from struct." + table + " f0")
        .fails("Table 'STRUCT." + table + ".F0' not found");

    // Resolve struct type F1 with wildcard.
    sql("select f1.* from struct." + table)
        .type("RecordType(INTEGER NOT NULL A0, INTEGER C0,"
            + " INTEGER NOT NULL C2) NOT NULL");

    // Resolve struct type F1 with wildcard.
    sql("select " + table + ".f1.* from struct." + table)
        .type("RecordType(INTEGER NOT NULL A0, INTEGER C0,"
            + " INTEGER NOT NULL C2) NOT NULL");

    // Fail non-existent column B0.
    sql("select ^b0^ from struct." + table)
        .fails("Column 'B0' not found in any table");

    // A column family can only be referenced with a star expansion.
    sql("select ^f1^ from struct." + table)
        .fails("Column 'F1' not found in any table");

    // If we fail to find a column, give an error based on the shortest prefix
    // that fails.
    sql("select " + table + ".^f0.notFound^.a.b.c.d from struct." + table)
        .fails("Column 'F0\\.NOTFOUND' not found in table '" + table + "'");
    sql("select " + table + ".^f0.notFound^ from struct." + table)
        .fails("Column 'F0\\.NOTFOUND' not found in table '" + table + "'");
    sql("select " + table + ".^f0.c1.notFound^ from struct." + table)
        .fails("Column 'F0\\.C1\\.NOTFOUND' not found in table '" + table + "'");
  }

  @Test void testDescriptor() {  // 测试方法：testDescriptor
    sql("select * from table(tumble(table orders, descriptor(rowtime), interval '2' hour))").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select * from table(tumble(table orders, descriptor(^column_not_exist^), "  // 创建SQL测试：解析并验证SQL语句
        + "interval '2' hour))")
        .fails("Unknown identifier 'COLUMN_NOT_EXIST'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testTumbleTableFunction() {  // 测试方法：testTumbleTableFunction
    sql("select rowtime, productid, orderid, 'window_start', 'window_end' from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "tumble(table orders, descriptor(rowtime), interval '2' hour))").ok();  // 断言验证：期望SQL验证通过
    sql("select rowtime, productid, orderid, 'window_start', 'window_end' from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "tumble(table orders, descriptor(rowtime), interval '2' hour, interval '1' hour))").ok();  // 断言验证：期望SQL验证通过
    // test named params.
    sql("select rowtime, productid, orderid, 'window_start', 'window_end'\n"  // 创建SQL测试：解析并验证SQL语句
        + "from table(\n"
        + "tumble(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(rowtime),\n"
        + "size -> interval '2' hour))").ok();  // 断言验证：期望SQL验证通过
    sql("select rowtime, productid, orderid, 'window_start', 'window_end'\n"  // 创建SQL测试：解析并验证SQL语句
        + "from table(\n"
        + "tumble(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(rowtime),\n"
        + "size -> interval '2' hour,\n"
        + "\"OFFSET\" -> interval '1' hour))").ok();  // 断言验证：期望SQL验证通过
    // negative tests.
    sql("select rowtime, productid, orderid, 'window_start', 'window_end'\n"  // 创建SQL测试：解析并验证SQL语句
        + "from table(\n"
        + "^tumble(table orders, descriptor(productid), interval '2' hour)^)")
        .fails("Cannot apply 'TUMBLE' to arguments of type 'TUMBLE\\(<RECORDTYPE\\"  // 断言验证：期望SQL验证失败并检查错误消息
            + "(TIMESTAMP\\(0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, "
            + "<INTERVAL HOUR>\\)'\\. Supported form\\(s\\): TUMBLE\\(TABLE table_name, "
            + "DESCRIPTOR\\(timecol\\), datetime interval\\[, datetime interval\\]\\)");
    sql("select rowtime, productid, orderid, 'window_start', 'window_end'\n"  // 创建SQL测试：解析并验证SQL语句
        + "from table(\n"
        + "^tumble(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(productid),\n"
        + "size -> interval '2' hour)^)")
        .fails("Cannot apply 'TUMBLE' to arguments of type 'TUMBLE\\(<RECORDTYPE\\"  // 断言验证：期望SQL验证失败并检查错误消息
            + "(TIMESTAMP\\(0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, "
            + "<INTERVAL HOUR>\\)'\\. Supported form\\(s\\): TUMBLE\\(TABLE table_name, "
            + "DESCRIPTOR\\(timecol\\), datetime interval\\[, datetime interval\\]\\)");
    sql("select rowtime, productid, orderid, 'window_start', 'window_end'\n"  // 创建SQL测试：解析并验证SQL语句
        + "from table(\n"
        + "^tumble(\n"
        + "data -> table orders,\n"
        + "SIZE -> interval '2' hour)^)")
        .fails("Invalid number of arguments to function 'TUMBLE'. Was expecting 3 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^tumble(table orders, descriptor(rowtime))^)")
        .fails("Invalid number of arguments to function 'TUMBLE'. Was expecting 3 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^tumble(table orders, descriptor(rowtime), 'test')^)")
        .fails("Cannot apply 'TUMBLE' to arguments of type 'TUMBLE\\(<RECORDTYPE\\"  // 断言验证：期望SQL验证失败并检查错误消息
            + "(TIMESTAMP\\(0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>,"
            + " <CHAR\\(4\\)>\\)'\\. Supported form\\(s\\): TUMBLE\\(TABLE "
            + "table_name, DESCRIPTOR\\(timecol\\), datetime interval"
            + "\\[, datetime interval\\]\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^tumble(table orders, 'test', interval '2' hour)^)")
        .fails("Cannot apply 'TUMBLE' to arguments of type 'TUMBLE\\(<RECORDTYPE\\"  // 断言验证：期望SQL验证失败并检查错误消息
            + "(TIMESTAMP\\(0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <CHAR\\"
            + "(4\\)>, <INTERVAL HOUR>\\)'\\. Supported form\\(s\\): TUMBLE\\(TABLE "
            + "table_name, DESCRIPTOR\\(timecol\\), datetime interval"
            + "\\[, datetime interval\\]\\)");
    sql("select rowtime, productid, orderid, 'window_start', 'window_end' from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^tumble(table orders, descriptor(rowtime), interval '2' hour, 'test')^)")
        .fails("Cannot apply 'TUMBLE' to arguments of type 'TUMBLE\\(<RECORDTYPE\\"  // 断言验证：期望SQL验证失败并检查错误消息
            + "(TIMESTAMP\\(0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>,"
            + " <INTERVAL HOUR>, <CHAR\\(4\\)>\\)'\\. Supported form\\(s\\): TUMBLE\\(TABLE "
            + "table_name, DESCRIPTOR\\(timecol\\), datetime interval"
            + "\\[, datetime interval\\]\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "tumble(TABLE ^tabler_not_exist^, descriptor(rowtime), interval '2' hour))")
        .fails("Object 'TABLER_NOT_EXIST' not found");  // 断言验证：期望SQL验证失败并检查错误消息
    // Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6936">[CALCITE-6936]
    // Table function parameter matching should always be case-insensitive</a>.
    // We cannot use table "orders", since lookup fails with unquotedCasing.TO_LOWER,
    // so we make up a new table o.
    sql("with o as "  // 创建SQL测试：解析并验证SQL语句
        + "(select TIMESTAMP '2020-01-01 00:00:00' as rowtime, 2 as productid, 3 as orderid)"
        + "select rowtime, productid, orderid, 'window_start', 'window_end'\n"
        + "from table(\n"
        + "tumble(\n"
        + "data -> table o,\n"
        + "timecol -> descriptor(rowtime),\n"
        + "size -> interval '2' hour))")
        .withUnquotedCasing(Casing.TO_LOWER)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testHopTableFunction() {  // 测试方法：testHopTableFunction
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "hop(table orders, descriptor(rowtime), interval '2' hour, interval '1' hour))").ok();  // 断言验证：期望SQL验证通过
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "hop(table orders, descriptor(rowtime), interval '2' hour, interval '1' hour, "
        + "interval '20' minute))").ok();  // 断言验证：期望SQL验证通过
    // test named params.
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "hop(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(rowtime),\n"
        + "slide -> interval '2' hour,\n"
        + "size -> interval '1' hour))").ok();  // 断言验证：期望SQL验证通过
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "hop(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(rowtime),\n"
        + "slide -> interval '2' hour,\n"
        + "size -> interval '1' hour,\n"
        + "\"OFFSET\" -> interval '20' minute))").ok();  // 断言验证：期望SQL验证通过
    // negative tests.
    sql("select rowtime, productid, orderid, 'window_start', 'window_end'\n"  // 创建SQL测试：解析并验证SQL语句
        + "from table(\n"
        + "^hop(table orders, descriptor(productid), interval '2' hour, interval '1' hour)^)")
        .fails("Cannot apply 'HOP' to arguments of type 'HOP\\(<RECORDTYPE\\"  // 断言验证：期望SQL验证失败并检查错误消息
            + "(TIMESTAMP\\(0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, "
            + "<INTERVAL HOUR>, <INTERVAL HOUR>\\)'\\. Supported form\\(s\\): "
            + "HOP\\(TABLE table_name, DESCRIPTOR\\(timecol\\), "
            + "datetime interval, datetime interval\\[, datetime interval\\]\\)");
    sql("select rowtime, productid, orderid, 'window_start', 'window_end'\n"  // 创建SQL测试：解析并验证SQL语句
        + "from table(\n"
        + "^hop(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(productid),\n"
        + "size -> interval '2' hour,\n"
        + "slide -> interval '1' hour)^)")
        .fails("Cannot apply 'HOP' to arguments of type 'HOP\\(<RECORDTYPE\\"  // 断言验证：期望SQL验证失败并检查错误消息
            + "(TIMESTAMP\\(0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, "
            + "<INTERVAL HOUR>, <INTERVAL HOUR>\\)'\\. Supported form\\(s\\): "
            + "HOP\\(TABLE table_name, DESCRIPTOR\\(timecol\\), "
            + "datetime interval, datetime interval\\[, datetime interval\\]\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^hop(\n"
        + "data -> table orders,\n"
        + "slide -> interval '2' hour,\n"
        + "size -> interval '1' hour)^)")
        .fails("Invalid number of arguments to function 'HOP'. Was expecting 4 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^hop(table orders, descriptor(rowtime), interval '2' hour)^)")
        .fails("Invalid number of arguments to function 'HOP'. Was expecting 4 arguments");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^hop(table orders, descriptor(rowtime), interval '2' hour, 'test')^)")
        .fails("Cannot apply 'HOP' to arguments of type 'HOP\\(<RECORDTYPE\\(TIMESTAMP\\(0\\) "  // 断言验证：期望SQL验证失败并检查错误消息
            + "ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, <INTERVAL HOUR>, "
            + "<CHAR\\(4\\)>\\)'. Supported form\\(s\\): HOP\\(TABLE table_name, DESCRIPTOR\\("
            + "timecol\\), datetime interval, datetime interval\\[, datetime interval\\]\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^hop(table orders, descriptor(rowtime), 'test', interval '2' hour)^)")
        .fails("Cannot apply 'HOP' to arguments of type 'HOP\\(<RECORDTYPE\\(TIMESTAMP\\(0\\) "  // 断言验证：期望SQL验证失败并检查错误消息
            + "ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, <CHAR\\(4\\)>, "
            + "<INTERVAL HOUR>\\)'. Supported form\\(s\\): HOP\\(TABLE table_name, DESCRIPTOR\\("
            + "timecol\\), datetime interval, datetime interval\\[, datetime interval\\]\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^hop(table orders, 'test', interval '2' hour, interval '2' hour)^)")
        .fails("Cannot apply 'HOP' to arguments of type 'HOP\\(<RECORDTYPE\\(TIMESTAMP\\(0\\) "  // 断言验证：期望SQL验证失败并检查错误消息
            + "ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <CHAR\\(4\\)>, <INTERVAL HOUR>, "
            + "<INTERVAL HOUR>\\)'. Supported form\\(s\\): HOP\\(TABLE table_name, DESCRIPTOR\\("
            + "timecol\\), datetime interval, datetime interval\\[, datetime interval\\]\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^hop(table orders, descriptor(rowtime), interval '2' hour, interval '1' hour, 'test')^)")
        .fails("Cannot apply 'HOP' to arguments of type 'HOP\\(<RECORDTYPE\\(TIMESTAMP\\(0\\) "  // 断言验证：期望SQL验证失败并检查错误消息
            + "ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, <INTERVAL HOUR>, "
            + "<INTERVAL HOUR>, <CHAR\\(4\\)>\\)'. Supported form\\(s\\): HOP\\(TABLE table_name, "
            + "DESCRIPTOR\\(timecol\\), datetime interval, datetime interval\\[, datetime interval\\]\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "hop(TABLE ^tabler_not_exist^, descriptor(rowtime), interval '2' hour, interval '1' hour))")
        .fails("Object 'TABLER_NOT_EXIST' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSessionTableFunction() {  // 测试方法：testSessionTableFunction
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "session(table orders, descriptor(rowtime), descriptor(productid), interval '1' hour))")
        .ok();  // 断言验证：期望SQL验证通过
    // test without key descriptor
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "session(table orders, descriptor(rowtime), interval '2' hour))").ok();  // 断言验证：期望SQL验证通过
    // test named params.
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "session(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(rowtime),\n"
        + "key -> descriptor(productid),\n"
        + "size -> interval '1' hour))")
        .ok();  // 断言验证：期望SQL验证通过
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "session(\n"
        + "data -> table orders,\n"
        + "timecol -> descriptor(rowtime),\n"
        + "size -> interval '1' hour))")
        .ok();  // 断言验证：期望SQL验证通过
    // negative tests.
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^session(\n"
        + "data -> table orders,\n"
        + "key -> descriptor(productid),\n"
        + "size -> interval '1' hour)^)")
        .fails("Cannot apply 'SESSION' to arguments of type 'SESSION\\(<RECORDTYPE\\(TIMESTAMP\\("  // 断言验证：期望SQL验证失败并检查错误消息
            + "0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, "
            + "<INTERVAL HOUR>\\)'. Supported form\\(s\\): SESSION\\(TABLE table_name, DESCRIPTOR\\("
            + "timecol\\), DESCRIPTOR\\(key\\) optional, datetime interval\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^session(table orders, descriptor(rowtime), descriptor(productid), 'test')^)")
        .fails("Cannot apply 'SESSION' to arguments of type 'SESSION\\(<RECORDTYPE\\(TIMESTAMP\\("  // 断言验证：期望SQL验证失败并检查错误消息
            + "0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, <COLUMN_LIST>, "
            + "<CHAR\\(4\\)>\\)'. Supported form\\(s\\): SESSION\\(TABLE table_name, DESCRIPTOR\\("
            + "timecol\\), DESCRIPTOR\\(key\\) optional, datetime interval\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^session(table orders, descriptor(rowtime), 'test', interval '2' hour)^)")
        .fails("Cannot apply 'SESSION' to arguments of type 'SESSION\\(<RECORDTYPE\\(TIMESTAMP\\("  // 断言验证：期望SQL验证失败并检查错误消息
            + "0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <COLUMN_LIST>, <CHAR\\(4\\)>, "
            + "<INTERVAL HOUR>\\)'. Supported form\\(s\\): SESSION\\(TABLE table_name, DESCRIPTOR\\("
            + "timecol\\), DESCRIPTOR\\(key\\) optional, datetime interval\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "^session(table orders, 'test', descriptor(productid), interval '2' hour)^)")
        .fails("Cannot apply 'SESSION' to arguments of type 'SESSION\\(<RECORDTYPE\\(TIMESTAMP\\("  // 断言验证：期望SQL验证失败并检查错误消息
            + "0\\) ROWTIME, INTEGER PRODUCTID, INTEGER ORDERID\\)>, <CHAR\\(4\\)>, <COLUMN_LIST>, "
            + "<INTERVAL HOUR>\\)'. Supported form\\(s\\): SESSION\\(TABLE table_name, DESCRIPTOR\\("
            + "timecol\\), DESCRIPTOR\\(key\\) optional, datetime interval\\)");
    sql("select * from table(\n"  // 创建SQL测试：解析并验证SQL语句
        + "session(TABLE ^tabler_not_exist^, descriptor(rowtime), descriptor(productid), interval '1' hour))")
        .fails("Object 'TABLER_NOT_EXIST' not found");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStreamTumble() {  // 测试方法：testStreamTumble
    // TUMBLE
    sql("select stream tumble_end(rowtime, interval '2' hour) as rowtime\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "group by tumble(rowtime, interval '2' hour), productId").ok();  // 断言验证：期望SQL验证通过
    sql("select stream ^tumble(rowtime, interval '2' hour)^ as rowtime\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "group by tumble(rowtime, interval '2' hour), productId")
        .fails("Group function '\\$TUMBLE' can only appear in GROUP BY clause");  // 断言验证：期望SQL验证失败并检查错误消息
    // TUMBLE with align argument
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "  tumble_end(rowtime, interval '2' hour, time '00:12:00') as rowtime\n"
        + "from orders\n"
        + "group by tumble(rowtime, interval '2' hour, time '00:12:00')").ok();  // 断言验证：期望SQL验证通过
    // TUMBLE_END without corresponding TUMBLE
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "  ^tumble_end(rowtime, interval '2' hour, time '00:13:00')^ as rowtime\n"
        + "from orders\n"
        + "group by floor(rowtime to hour)")
        .fails("Call to auxiliary group function 'TUMBLE_END' must have "  // 断言验证：期望SQL验证失败并检查错误消息
            + "matching call to group function '\\$TUMBLE' in GROUP BY clause");
    // Arguments to TUMBLE_END are slightly different to arguments to TUMBLE
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "  ^tumble_start(rowtime, interval '2' hour, time '00:13:00')^ as rowtime\n"
        + "from orders\n"
        + "group by tumble(rowtime, interval '2' hour, time '00:12:00')")
        .fails("Call to auxiliary group function 'TUMBLE_START' must have "  // 断言验证：期望SQL验证失败并检查错误消息
            + "matching call to group function '\\$TUMBLE' in GROUP BY clause");
    // Even though align defaults to TIME '00:00:00', we need structural
    // equivalence, not semantic equivalence.
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "  ^tumble_end(rowtime, interval '2' hour, time '00:00:00')^ as rowtime\n"
        + "from orders\n"
        + "group by tumble(rowtime, interval '2' hour)")
        .fails("Call to auxiliary group function 'TUMBLE_END' must have "  // 断言验证：期望SQL验证失败并检查错误消息
            + "matching call to group function '\\$TUMBLE' in GROUP BY clause");
    // TUMBLE query produces no monotonic column - OK
    sql("select stream productId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "group by tumble(rowtime, interval '2' hour), productId").ok();  // 断言验证：期望SQL验证通过
    sql("select stream productId\n"  // 创建SQL测试：解析并验证SQL语句
        + "from orders\n"
        + "^group by productId,\n"
        + "  tumble(timestamp '1990-03-04 12:34:56', interval '2' hour)^")
        .fails(STR_AGG_REQUIRES_MONO);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testStreamHop() {  // 测试方法：testStreamHop
    // HOP
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "  hop_start(rowtime, interval '1' hour, interval '3' hour) as rowtime,\n"
        + "  count(*) as c\n"
        + "from orders\n"
        + "group by hop(rowtime, interval '1' hour, interval '3' hour)").ok();  // 断言验证：期望SQL验证通过
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "  ^hop_start(rowtime, interval '1' hour, interval '2' hour)^,\n"
        + "  count(*) as c\n"
        + "from orders\n"
        + "group by hop(rowtime, interval '1' hour, interval '3' hour)")
        .fails("Call to auxiliary group function 'HOP_START' must have "  // 断言验证：期望SQL验证失败并检查错误消息
            + "matching call to group function '\\$HOP' in GROUP BY clause");
    // HOP with align
    sql("select stream\n"  // 创建SQL测试：解析并验证SQL语句
        + "  hop_start(rowtime, interval '1' hour, interval '3' hour,\n"
        + "    time '12:34:56') as rowtime,\n"
        + "  count(*) as c\n"
        + "from orders\n"
        + "group by hop(rowtime, interval '1' hour, interval '3' hour,\n"
        + "    time '12:34:56')").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testStreamSession() {  // 测试方法：testStreamSession
    // SESSION
    sql("select stream session_start(rowtime, interval '1' hour) as rowtime,\n"  // 创建SQL测试：解析并验证SQL语句
        + "  session_end(rowtime, interval '1' hour),\n"
        + "  count(*) as c\n"
        + "from orders\n"
        + "group by session(rowtime, interval '1' hour)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumn() {  // 测试方法：testInsertExtendedColumn
    sql("insert into empdefaults(extra BOOLEAN, note VARCHAR)"  // 创建SQL测试：解析并验证SQL语句
        + " (deptno, empno, ename, extra, note) values (1, 10, '2', true, 'ok')").ok();  // 断言验证：期望SQL验证通过
    sql("insert into emp(\"rank\" INT, extra BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " values (1, 'nom', 'job', 0, timestamp '1970-01-01 00:00:00', 1, 1,"
        + "  1, false, 100, false)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertBindExtendedColumn() {  // 测试方法：testInsertBindExtendedColumn
    sql("insert into empdefaults(extra BOOLEAN, note VARCHAR)"  // 创建SQL测试：解析并验证SQL语句
        + " (deptno, empno, ename, extra, note) values (1, 10, '2', ?, 'ok')").ok();  // 断言验证：期望SQL验证通过
    sql("insert into emp(\"rank\" INT, extra BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " values (1, 'nom', 'job', 0, timestamp '1970-01-01 00:00:00', 1, 1,"
        + "  1, false, ?, ?)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableView() {  // 测试方法：testInsertExtendedColumnModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into EMP_MODIFIABLEVIEW2(extra2 BOOLEAN,"
        + " note VARCHAR) (deptno, empno, ename, extra2, note)\n"
        + "values (20, 10, '2', true, 'ok')";
    s.withSql(sql0).ok();  // 断言验证：期望SQL验证通过
    final String sql1 = "insert into EMP_MODIFIABLEVIEW2(\"rank\" INT,"
        + " extra2 BOOLEAN)\n"
        + "values ('nom', 1, 'job', 20, true, 0, false,"
        + " timestamp '1970-01-01 00:00:00', 1, 1,  1, false)";
    s.withSql(sql1).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertBindExtendedColumnModifiableView() {  // 测试方法：testInsertBindExtendedColumnModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("insert into EMP_MODIFIABLEVIEW2(extra2 BOOLEAN, note VARCHAR)"
        + " (deptno, empno, ename, extra2, note) values (20, 10, '2', true, ?)").ok();  // 断言验证：期望SQL验证通过
    s.withSql("insert into EMP_MODIFIABLEVIEW2(\"rank\" INT, extra2 BOOLEAN)"
        + " values ('nom', 1, 'job', 20, true, 0, false, timestamp '1970-01-01 00:00:00', 1, 1,"
        + "  ?, false)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableViewFailConstraint() {  // 测试方法：testInsertExtendedColumnModifiableViewFailConstraint
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into EMP_MODIFIABLEVIEW2(extra2 BOOLEAN,"
        + " note VARCHAR) (deptno, empno, ename, extra2, note)\n"
        + "values (^1^, 10, '2', true, 'ok')";
    final String error = "Modifiable view constraint is not satisfied"
        + " for column 'DEPTNO' of base table 'EMP_MODIFIABLEVIEW2'";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into EMP_MODIFIABLEVIEW2(extra2 BOOLEAN,"
        + " note VARCHAR) (deptno, empno, ename, extra2, note)\n"
        + "values (^?^, 10, '2', true, 'ok')";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql2 = "insert into EMP_MODIFIABLEVIEW2(\"rank\" INT,"
        + " extra2 BOOLEAN)\n"
        + "values ('nom', 1, 'job', ^0^, true, 0, false,"
        + " timestamp '1970-01-01 00:00:00', 1, 1,  1, false)";
    s.withSql(sql2).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertExtendedColumnModifiableViewFailColumnCount() {  // 测试方法：testInsertExtendedColumnModifiableViewFailColumnCount
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into ^EMP_MODIFIABLEVIEW2(\"rank\" INT, extra2 BOOLEAN)^"
        + " values ('nom', 1, 'job', 0, true, 0, false,"
        + " timestamp '1970-01-01 00:00:00', 1, 1,  1)";
    final String error0 = "Number of INSERT target columns \\(12\\) does not"
        + " equal number of source items \\(11\\)";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into ^EMP_MODIFIABLEVIEW2(\"rank\" INT, extra2 BOOLEAN)^"
        + " (deptno, empno, ename, extra2, \"rank\") values (?, 10, '2', true)";
    final String error1 = "Number of INSERT target columns \\(5\\) does not"
        + " equal number of source items \\(4\\)";
    s.withSql(sql1).fails(error1);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertExtendedColumnFailDuplicate() {  // 测试方法：testInsertExtendedColumnFailDuplicate
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into EMP_MODIFIABLEVIEW2(extcol INT,"
        + " ^extcol^ BOOLEAN)\n"
        + "values ('nom', 1, 'job', 0, true, 0, false,"
        + " timestamp '1970-01-01 00:00:00', 1, 1,  1)";
    final String error = "Duplicate name 'EXTCOL' in column list";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into EMP_MODIFIABLEVIEW2(extcol INT,"
        + " ^extcol^ BOOLEAN) (extcol) values (1)";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql2 = "insert into EMP_MODIFIABLEVIEW2(extcol INT,"
        + " ^extcol^ BOOLEAN) (extcol) values (false)";
    s.withSql(sql2).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql3 = "insert into EMP(extcol INT, ^extcol^ BOOLEAN)"
        + " (extcol) values (1)";
    s.withSql(sql3).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql4 = "insert into EMP(extcol INT, ^extcol^ BOOLEAN)"
        + " (extcol) values (false)";
    s.withSql(sql4).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateExtendedColumn() {  // 测试方法：testUpdateExtendedColumn
    sql("update empdefaults(extra BOOLEAN, note VARCHAR)"  // 创建SQL测试：解析并验证SQL语句
        + " set deptno = 1, extra = true, empno = 20, ename = 'Bob', note = 'legion'"
        + " where deptno = 10").ok();  // 断言验证：期望SQL验证通过
    sql("update empdefaults(extra BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " set extra = true, deptno = 1, ename = 'Bob'"
        + " where deptno = 10").ok();  // 断言验证：期望SQL验证通过
    sql("update empdefaults(\"empNo\" VARCHAR)"  // 创建SQL测试：解析并验证SQL语句
        + " set \"empNo\" = '5', deptno = 1, ename = 'Bob'"
        + " where deptno = 10").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertFailDataType() {  // 测试方法：testInsertFailDataType
    sql("insert into empnullables ^values ('5', 'bob')^")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .withTypeCoercion(false)
        .fails("Cannot assign to target field 'EMPNO' of type INTEGER"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR\\$0' of type CHAR\\(1\\)");
    sql("insert into empnullables values ('5', 'bob')")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .ok();  // 断言验证：期望SQL验证通过
    sql("insert into empnullables (^empno^, ename) values ('5', 'bob')")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .withTypeCoercion(false)
        .fails("Cannot assign to target field 'EMPNO' of type INTEGER"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR\\$0' of type CHAR\\(1\\)");
    sql("insert into empnullables (empno, ename) values ('5', 'bob')")  // 创建SQL测试：解析并验证SQL语句
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .ok();  // 断言验证：期望SQL验证通过
    sql("insert into empnullables(extra BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, ^extra^) values (5, 'bob', 'true')")
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .withTypeCoercion(false)
        .fails("Cannot assign to target field 'EXTRA' of type BOOLEAN"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR\\$2' of type CHAR\\(4\\)");
    sql("insert into empnullables(extra BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, ^extra^) values (5, 'bob', 'true')")
        .withConformance(SqlConformanceEnum.PRAGMATIC_2003)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Disabled("CALCITE-1727")
  @Test void testUpdateFailDataType() {  // 测试方法：testUpdateFailDataType
    sql("update emp"  // 创建SQL测试：解析并验证SQL语句
        + " set ^empNo^ = '5', deptno = 1, ename = 'Bob'"
        + " where deptno = 10")
        .fails("Cannot assign to target field 'EMPNO' of type INTEGER"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR$0' of type CHAR(1)");
    sql("update emp(extra boolean)"  // 创建SQL测试：解析并验证SQL语句
        + " set ^extra^ = '5', deptno = 1, ename = 'Bob'"
        + " where deptno = 10")
        .fails("Cannot assign to target field 'EXTRA' of type BOOLEAN"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR$0' of type CHAR(1)");
  }

  @Disabled("CALCITE-1727")
  @Test void testUpdateFailCaseSensitivity() {  // 测试方法：testUpdateFailCaseSensitivity
    sql("update empdefaults"  // 创建SQL测试：解析并验证SQL语句
        + " set empNo = '5', deptno = 1, ename = 'Bob'"
        + " where deptno = 10")
        .fails("Column 'empno' not found in any table; did you mean 'EMPNO'\\?");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateExtendedColumnFailCaseSensitivity() {  // 测试方法：testUpdateExtendedColumnFailCaseSensitivity
    sql("update empdefaults(\"extra\" BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " set ^extra^ = true, deptno = 1, ename = 'Bob'"
        + " where deptno = 10")
        .fails("Unknown target column 'EXTRA'");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("update empdefaults(extra BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " set ^\"extra\"^ = true, deptno = 1, ename = 'Bob'"
        + " where deptno = 10")
        .fails("Unknown target column 'extra'");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateBindExtendedColumn() {  // 测试方法：testUpdateBindExtendedColumn
    sql("update empdefaults(extra BOOLEAN, note VARCHAR)"  // 创建SQL测试：解析并验证SQL语句
        + " set deptno = 1, extra = true, empno = 20, ename = 'Bob', note = ?"
        + " where deptno = 10").ok();  // 断言验证：期望SQL验证通过
    sql("update empdefaults(extra BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " set extra = ?, deptno = 1, ename = 'Bob'"
        + " where deptno = 10").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUpdateExtendedColumnModifiableView() {  // 测试方法：testUpdateExtendedColumnModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("update EMP_MODIFIABLEVIEW2(extra2 BOOLEAN, note VARCHAR)"
        + " set deptno = 20, extra2 = true, empno = 20, ename = 'Bob', note = 'legion'"
        + " where ename = 'Jane'").ok();  // 断言验证：期望SQL验证通过
    s.withSql("update EMP_MODIFIABLEVIEW2(extra2 BOOLEAN)"
        + " set extra2 = true, ename = 'Bob'"
        + " where ename = 'Jane'").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUpdateBindExtendedColumnModifiableView() {  // 测试方法：testUpdateBindExtendedColumnModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("update EMP_MODIFIABLEVIEW2(extra2 BOOLEAN, note VARCHAR)"
        + " set deptno = 20, extra2 = true, empno = 20, ename = 'Bob', note = ?"
        + " where ename = 'Jane'").ok();  // 断言验证：期望SQL验证通过
    s.withSql("update EMP_MODIFIABLEVIEW2(extra2 BOOLEAN)"
        + " set extra2 = ?, ename = 'Bob'"
        + " where ename = 'Jane'").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUpdateExtendedColumnModifiableViewFailConstraint() {  // 测试方法：testUpdateExtendedColumnModifiableViewFailConstraint
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "update EMP_MODIFIABLEVIEW2(extra2 BOOLEAN,"
        + " note VARCHAR)\n"
        + "set deptno = ^1^, extra2 = true, empno = 20, ename = 'Bob',"
        + " note = 'legion'\n"
        + "where ename = 'Jane'";
    final String error = "Modifiable view constraint is not satisfied"
        + " for column 'DEPTNO' of base table 'EMP_MODIFIABLEVIEW2'";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "update EMP_MODIFIABLEVIEW2(extra2 BOOLEAN)"
        + " set extra2 = true, deptno = ^1^, ename = 'Bob'"
        + " where ename = 'Jane'";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateExtendedColumnCollision() {  // 测试方法：testUpdateExtendedColumnCollision
    sql("update empdefaults(empno INTEGER NOT NULL, deptno INTEGER)"  // 创建SQL测试：解析并验证SQL语句
        + " set deptno = 1, empno = 20, ename = 'Bob'"
        + " where deptno = 10").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUpdateExtendedColumnModifiableViewCollision() {  // 测试方法：testUpdateExtendedColumnModifiableViewCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("update EMP_MODIFIABLEVIEW3(empno INTEGER NOT NULL,"
        + " deptno INTEGER)\n"
        + "set deptno = 20, empno = 20, ename = 'Bob'\n"
        + "where empno = 10").ok();  // 断言验证：期望SQL验证通过
    s.withSql("update EMP_MODIFIABLEVIEW3(empno INTEGER NOT NULL,"
        + " \"deptno\" BOOLEAN)\n"
        + "set \"deptno\" = true, empno = 20, ename = 'Bob'\n"
        + "where empno = 10").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testUpdateExtendedColumnFailCollision() {  // 测试方法：testUpdateExtendedColumnFailCollision
    final String sql = "update empdefaults(^empno^ BOOLEAN, deptno INTEGER)\n"
        + "set deptno = 1, empno = false, ename = 'Bob'\n"
        + "where deptno = 10";
    final String expected = "Cannot assign to target field 'EMPNO' of type "
        + "INTEGER NOT NULL from source field 'EMPNO' of type BOOLEAN";
    sql(sql).fails(expected);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Disabled("CALCITE-1727")
  @Test void testUpdateExtendedColumnFailCollision2() {  // 测试方法：testUpdateExtendedColumnFailCollision2
    final String sql = "update empdefaults(^\"deptno\"^ BOOLEAN)\n"
        + "set \"deptno\" = 1, empno = 1, ename = 'Bob'\n"
        + "where deptno = 10";
    final String expected = "Cannot assign to target field 'deptno' of type "
        + "BOOLEAN NOT NULL from source field 'deptno' of type INTEGER";
    sql(sql).fails(expected);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateExtendedColumnModifiableViewFailCollision() {  // 测试方法：testUpdateExtendedColumnModifiableViewFailCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql = "update EMP_MODIFIABLEVIEW3(^empno^ BOOLEAN,"
        + " deptno INTEGER)\n"
        + "set deptno = 1, empno = false, ename = 'Bob'\n"
        + "where deptno = 10";
    final String error = "Cannot assign to target field 'EMPNO' of type"
        + " INTEGER NOT NULL from source field 'EMPNO' of type BOOLEAN";
    s.withSql(sql).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateExtendedColumnModifiableViewFailExtendedCollision() {  // 测试方法：testUpdateExtendedColumnModifiableViewFailExtendedCollision
    final String error = "Cannot assign to target field 'EXTRA' of type"
        + " BOOLEAN from source field 'EXTRA' of type INTEGER";
    final String sql = "update EMP_MODIFIABLEVIEW2(^extra^ INTEGER,"
        + " deptno INTEGER)\n"
        + "set deptno = 20, empno = 20, ename = 'Bob', extra = 5\n"
        + "where empno = 10";
    sql(sql).withExtendedCatalog().fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateExtendedColumnModifiableViewFailUnderlyingCollision() {  // 测试方法：testUpdateExtendedColumnModifiableViewFailUnderlyingCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql = "update EMP_MODIFIABLEVIEW3(^comm^ BOOLEAN,"
        + " deptno INTEGER)\n"
        + "set deptno = 1, empno = 20, ename = 'Bob', comm = true\n"
        + "where deptno = 10";
    final String error = "Cannot assign to target field 'COMM' of type"
        + " INTEGER from source field 'COMM' of type BOOLEAN";
    s.withSql(sql).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testUpdateExtendedColumnFailDuplicate() {  // 测试方法：testUpdateExtendedColumnFailDuplicate
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "update emp(comm BOOLEAN, ^comm^ INTEGER)\n"
        + "set deptno = 1, empno = 20, ename = 'Bob', comm = 1\n"
        + "where deptno = 10";
    final String error = "Duplicate name 'COMM' in column list";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "update EMP_MODIFIABLEVIEW3(comm BOOLEAN,"
        + " ^comm^ INTEGER)\n"
        + "set deptno = 1, empno = 20, ename = 'Bob', comm = true\n"
        + "where deptno = 10";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testInsertExtendedColumnCollision() {  // 测试方法：testInsertExtendedColumnCollision
    sql("insert into EMPDEFAULTS(^comm^ INTEGER) (empno, ename, job, comm)\n"  // 创建SQL测试：解析并验证SQL语句
        + "values (1, 'Arthur', 'clown', 5)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableViewCollision() {  // 测试方法：testInsertExtendedColumnModifiableViewCollision
    final String sql = "insert into EMP_MODIFIABLEVIEW3(^sal^ INTEGER)\n"
        + " (empno, ename, job, sal)\n"
        + "values (1, 'Arthur', 'clown', 5)";
    sql(sql).withExtendedCatalog().ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableViewExtendedCollision() {  // 测试方法：testInsertExtendedColumnModifiableViewExtendedCollision
    final String sql = "insert into EMP_MODIFIABLEVIEW2(^extra^ BOOLEAN)"
        + " (empno, ename, job, extra)\n"
        + "values (1, 'Arthur', 'clown', true)";
    sql(sql).withExtendedCatalog().ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableViewUnderlyingCollision() {  // 测试方法：testInsertExtendedColumnModifiableViewUnderlyingCollision
    final String sql = "insert into EMP_MODIFIABLEVIEW3(^comm^ INTEGER)\n"
        + " (empno, ename, job, comm)\n"
        + "values (1, 'Arthur', 'clown', 5)";
    sql(sql).withExtendedCatalog().ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnFailCollision() {  // 测试方法：testInsertExtendedColumnFailCollision
    sql("insert into EMPDEFAULTS(^comm^ BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, job, comm)\n"
        + "values (1, 'Arthur', 'clown', true)")
        .fails("Cannot assign to target field 'COMM' of type INTEGER"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'COMM' of type BOOLEAN");
    sql("insert into EMPDEFAULTS(\"comm\" BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, job, ^comm^)\n"
        + "values (1, 'Arthur', 'clown', true)")
        .withTypeCoercion(false)
        .fails("Cannot assign to target field 'COMM' of type INTEGER"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR\\$3' of type BOOLEAN");
    sql("insert into EMPDEFAULTS(\"comm\" BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, job, ^comm^)\n"
        + "values (1, 'Arthur', 'clown', true)")
        .fails("Cannot assign to target field 'COMM' of type INTEGER"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR\\$3' of type BOOLEAN");
    sql("insert into EMPDEFAULTS(\"comm\" BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, job, comm)\n"
        + "values (1, 'Arthur', 'clown', true)")
        .withConformance(SqlConformanceEnum.BIG_QUERY)
        .ok();  // 断言验证：期望SQL验证通过
    sql("insert into EMPDEFAULTS(\"comm\" BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, job, ^\"comm\"^)\n"
        + "values (1, 'Arthur', 'clown', 1)")
        .withTypeCoercion(false)
        .fails("Cannot assign to target field 'comm' of type BOOLEAN"  // 断言验证：期望SQL验证失败并检查错误消息
            + " from source field 'EXPR\\$3' of type INTEGER");
    sql("insert into EMPDEFAULTS(\"comm\" BOOLEAN)"  // 创建SQL测试：解析并验证SQL语句
        + " (empno, ename, job, \"comm\")\n"
        + "values (1, 'Arthur', 'clown', 1)")
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableViewFailCollision() {  // 测试方法：testInsertExtendedColumnModifiableViewFailCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into EMP_MODIFIABLEVIEW2(^slacker^ INTEGER)"
        + " (empno, ename, job, slacker) values (1, 'Arthur', 'clown', true)";
    final String error0 = "Cannot assign to target field 'SLACKER' of type"
        + " BOOLEAN from source field 'SLACKER' of type INTEGER";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into EMP_MODIFIABLEVIEW2(\"slacker\" INTEGER)"
        + " (empno, ename, job, ^slacker^) values (1, 'Arthur', 'clown', 1)";
    final String error1 = "Cannot assign to target field 'SLACKER' of type"
        + " BOOLEAN from source field 'EXPR\\$3' of type INTEGER";
    s.withSql(sql1).withTypeCoercion(false).fails(error1);  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql(sql1).ok();  // 断言验证：期望SQL验证通过

    final String sql2 = "insert into EMP_MODIFIABLEVIEW2(\"slacker\" INTEGER)"
        + " (empno, ename, job, ^\"slacker\"^)\n"
        + "values (1, 'Arthur', 'clown', true)";
    final String error2 = "Cannot assign to target field 'slacker' of type"
        + " INTEGER from source field 'EXPR\\$3' of type BOOLEAN";
    s.withSql(sql2).withTypeCoercion(false).fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息
    s.withConformance(SqlConformanceEnum.BIG_QUERY).withSql(sql2).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableViewFailExtendedCollision() {  // 测试方法：testInsertExtendedColumnModifiableViewFailExtendedCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "insert into EMP_MODIFIABLEVIEW2(^extra^ INTEGER)"
        + " (empno, ename, job, extra) values (1, 'Arthur', 'clown', true)";
    final String error0 = "Cannot assign to target field 'EXTRA' of type"
        + " BOOLEAN from source field 'EXTRA' of type INTEGER";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into EMP_MODIFIABLEVIEW2(\"extra\" INTEGER)"
        + " (empno, ename, job, ^extra^) values (1, 'Arthur', 'clown', 1)";
    final String error1 = "Cannot assign to target field 'EXTRA' of type"
        + " BOOLEAN from source field 'EXPR\\$3' of type INTEGER";
    s.withSql(sql1).withTypeCoercion(false).fails(error1);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql2 = "insert into EMP_MODIFIABLEVIEW2(\"extra\" INTEGER)"
        + " (empno, ename, job, extra) values (1, 'Arthur', 'clown', 1)";
    s.withSql(sql2).ok();  // 断言验证：期望SQL验证通过

    final String sql3 = "insert into EMP_MODIFIABLEVIEW2(\"extra\" INTEGER)"
        + " (empno, ename, job, ^\"extra\"^)\n"
        + "values (1, 'Arthur', 'clown', true)";
    final String error3 = "Cannot assign to target field 'extra' of type"
        + " INTEGER from source field 'EXPR\\$3' of type BOOLEAN";
    s.withSql(sql3).withTypeCoercion(false).fails(error3);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql4 = "insert into EMP_MODIFIABLEVIEW2(\"extra\" INTEGER)"
        + " (empno, ename, job, \"extra\")\n"
        + "values (1, 'Arthur', 'clown', true)";
    s.withConformance(SqlConformanceEnum.BIG_QUERY).withTypeCoercion(true)
        .withSql(sql4).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testInsertExtendedColumnModifiableViewFailUnderlyingCollision() {  // 测试方法：testInsertExtendedColumnModifiableViewFailUnderlyingCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String error0 = "Cannot assign to target field 'COMM' of type"
        + " INTEGER from source field 'COMM' of type BOOLEAN";
    final String sql0 = "insert into EMP_MODIFIABLEVIEW3(^comm^ BOOLEAN)"
        + " (empno, ename, job, comm) values (1, 'Arthur', 'clown', true)";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息

    final String sql1 = "insert into EMP_MODIFIABLEVIEW3(\"comm\" BOOLEAN)"
        + " (empno, ename, job, ^comm^) values (1, 'Arthur', 'clown', 5)";
    final String error1 = "Unknown target column 'COMM'";
    s.withSql(sql1).fails(error1);  // 断言验证：期望SQL验证失败并检查错误消息


    final String sql2 = "insert into EMP_MODIFIABLEVIEW3(\"comm\" BOOLEAN)"
        + " (empno, ename, job, ^\"comm\"^) values (1, 'Arthur', 'clown', 1)";
    final String error2 = "Cannot assign to target field 'comm' of type"
        + " BOOLEAN from source field 'EXPR\\$3' of type INTEGER";
    s.withSql(sql2).withTypeCoercion(false).fails(error2);  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql(sql2).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testDelete() {  // 测试方法：testDelete
    sql("delete from empdefaults where deptno = 10").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testDeleteExtendedColumn() {  // 测试方法：testDeleteExtendedColumn
    sql("delete from empdefaults(extra BOOLEAN) where deptno = 10").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("delete from empdefaults(extra BOOLEAN) where extra = false").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testDeleteBindExtendedColumn() {  // 测试方法：testDeleteBindExtendedColumn
    sql("delete from empdefaults(extra BOOLEAN) where deptno = ?").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("delete from empdefaults(extra BOOLEAN) where extra = ?").ok();  // 创建SQL测试：解析并验证SQL语句
  }

  @Test void testDeleteModifiableView() {  // 测试方法：testDeleteModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("delete from EMP_MODIFIABLEVIEW2 where deptno = 10").ok();  // 断言验证：期望SQL验证通过
    s.withSql("delete from EMP_MODIFIABLEVIEW2 where deptno = 20").ok();  // 断言验证：期望SQL验证通过
    s.withSql("delete from EMP_MODIFIABLEVIEW2 where empno = 30").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testDeleteExtendedColumnModifiableView() {  // 测试方法：testDeleteExtendedColumnModifiableView
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    s.withSql("delete from EMP_MODIFIABLEVIEW2(extra BOOLEAN) where sal > 10")
        .ok();  // 断言验证：期望SQL验证通过
    s.withSql("delete from EMP_MODIFIABLEVIEW2(note BOOLEAN) where note = 'fired'")
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testDeleteExtendedColumnCollision() {  // 测试方法：testDeleteExtendedColumnCollision
    final String sql =
        "delete from emp(empno INTEGER NOT NULL) where sal > 10";
    sql(sql).withExtendedCatalog().ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testDeleteExtendedColumnModifiableViewCollision() {  // 测试方法：testDeleteExtendedColumnModifiableViewCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "delete from EMP_MODIFIABLEVIEW2("
        + "empno INTEGER NOT NULL) where sal > 10";
    s.withSql(sql0).ok();  // 断言验证：期望SQL验证通过
    final String sql1 = "delete from EMP_MODIFIABLEVIEW2(\"empno\" INTEGER)\n"
        + "where sal > 10";
    s.withSql(sql1).ok();  // 断言验证：期望SQL验证通过
    final String sql2 = "delete from EMP_MODIFIABLEVIEW2(extra BOOLEAN)\n"
        + "where sal > 10";
    s.withSql(sql2).ok();  // 断言验证：期望SQL验证通过
    final String sql3 = "delete from EMP_MODIFIABLEVIEW2(\"extra\" VARCHAR)\n"
        + "where sal > 10";
    s.withSql(sql3).ok();  // 断言验证：期望SQL验证通过
    final String sql4 = "delete from EMP_MODIFIABLEVIEW3(comm INTEGER)\n"
        + "where sal > 10";
    s.withSql(sql4).ok();  // 断言验证：期望SQL验证通过
    final String sql5 = "delete from EMP_MODIFIABLEVIEW3(\"comm\" BIGINT)\n"
        + "where sal > 10";
    s.withSql(sql5).ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testDeleteExtendedColumnFailCollision() {  // 测试方法：testDeleteExtendedColumnFailCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "delete from EMP_MODIFIABLEVIEW2(^empno^ BOOLEAN)\n"
        + "where sal > 10";
    final String error0 = "Cannot assign to target field 'EMPNO' of type"
        + " INTEGER NOT NULL from source field 'EMPNO' of type BOOLEAN";
    s.withSql(sql0).fails(error0);  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql1 = "delete from EMP_MODIFIABLEVIEW2(^empno^ INTEGER)\n"
        + "where sal > 10";
    final String error = "Cannot assign to target field 'EMPNO' of type"
        + " INTEGER NOT NULL from source field 'EMPNO' of type INTEGER";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql2 = "delete from EMP_MODIFIABLEVIEW2(^\"EMPNO\"^ INTEGER)"
        + " where sal > 10";
    s.withSql(sql2).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testDeleteExtendedColumnModifiableViewFailCollision() {  // 测试方法：testDeleteExtendedColumnModifiableViewFailCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String sql0 = "delete from EMP_MODIFIABLEVIEW(^deptno^ BOOLEAN)\n"
        + "where sal > 10";
    final String error = "Cannot assign to target field 'DEPTNO' of type"
        + " INTEGER from source field 'DEPTNO' of type BOOLEAN";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql1 = "delete from EMP_MODIFIABLEVIEW(^\"DEPTNO\"^ BOOLEAN)"
        + " where sal > 10";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testDeleteExtendedColumnModifiableViewFailExtendedCollision() {  // 测试方法：testDeleteExtendedColumnModifiableViewFailExtendedCollision
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    final String error = "Cannot assign to target field 'SLACKER' of type"
        + " BOOLEAN from source field 'SLACKER' of type INTEGER";
    final String sql0 = "delete from EMP_MODIFIABLEVIEW(^slacker^ INTEGER)\n"
        + "where sal > 10";
    s.withSql(sql0).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
    final String sql1 = "delete from EMP_MODIFIABLEVIEW(^\"SLACKER\"^ INTEGER)"
        + " where sal > 10";
    s.withSql(sql1).fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testDeleteExtendedColumnFailDuplicate() {  // 测试方法：testDeleteExtendedColumnFailDuplicate
    final SqlValidatorFixture s = fixture().withExtendedCatalog();
    sql("delete from emp (extra VARCHAR, ^extra^ VARCHAR)")  // 创建SQL测试：解析并验证SQL语句
        .fails("Duplicate name 'EXTRA' in column list");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("delete from EMP_MODIFIABLEVIEW (extra VARCHAR, ^extra^ VARCHAR)"
        + " where extra = 'test'")
        .fails("Duplicate name 'EXTRA' in column list");  // 断言验证：期望SQL验证失败并检查错误消息
    s.withSql("delete from EMP_MODIFIABLEVIEW (extra VARCHAR, ^\"EXTRA\"^ VARCHAR)"
        + " where extra = 'test'")
        .fails("Duplicate name 'EXTRA' in column list");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1804">[CALCITE-1804]
   * Cannot assign NOT NULL array to nullable array</a>. */
  @Test void testArrayAssignment() {  // 测试方法：testArrayAssignment
    final SqlTypeFactoryImpl typeFactory =
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);
    final RelDataType bigint = typeFactory.createSqlType(SqlTypeName.BIGINT);
    final RelDataType bigintNullable =
        typeFactory.createTypeWithNullability(bigint, true);
    final RelDataType bigintNotNull =
        typeFactory.createTypeWithNullability(bigint, false);
    final RelDataType date = typeFactory.createSqlType(SqlTypeName.DATE);
    final RelDataType dateNotNull =
        typeFactory.createTypeWithNullability(date, false);
    assertThat(SqlTypeUtil.canAssignFrom(bigintNullable, bigintNotNull),
        is(true));
    assertThat(SqlTypeUtil.canAssignFrom(bigintNullable, dateNotNull),
        is(false));
    final RelDataType bigintNullableArray =
        typeFactory.createArrayType(bigintNullable, -1);
    final RelDataType bigintArrayNullable =
        typeFactory.createTypeWithNullability(bigintNullableArray, true);
    final RelDataType bigintNotNullArray =
        new ArraySqlType(bigintNotNull, false);
    assertThat(SqlTypeUtil.canAssignFrom(bigintArrayNullable, bigintNotNullArray),
        is(true));
    final RelDataType dateNotNullArray =
        new ArraySqlType(dateNotNull, false);
    assertThat(SqlTypeUtil.canAssignFrom(bigintArrayNullable, dateNotNullArray),
        is(false));
  }

  @Test void testSelectRolledUpColumn() {  // 测试方法：testSelectRolledUpColumn
    final String error = "Rolled up column 'SLACKINGMIN' is not allowed in SELECT";

    sql("select ^slackingmin^ from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select a from (select ^slackingmin^ from emp_r)")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^slackingmin^ as b from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select empno, ^slackingmin^ from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select slackingmin from (select empno as slackingmin from emp_r)").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select ^emp_r.slackingmin^ from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^sales.emp_r.slackingmin^ from sales.emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^sales.emp_r.slackingmin^ from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^catalog.sales.emp_r.slackingmin^ from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select (select ^slackingmin^ from emp_r), a from (select empno as a from emp_r)")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^(((slackingmin)))^ from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select ^slackingmin^ from nest.emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("with emp_r as (select 1 as slackingmin) select slackingmin from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过

    sql("with emp_r as (select ^slackingmin^ from emp_r) select slackingmin from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("with emp_r1 as (select 1 as slackingmin) select emp_r1.slackingmin from emp_r, emp_r1")  // 创建SQL测试：解析并验证SQL语句
        .ok();  // 断言验证：期望SQL验证通过

    sql("with emp_r1 as (select 1 as slackingmin) select ^emp_r.slackingmin^ from emp_r, emp_r1")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testSelectAggregateOnRolledUpColumn() {  // 测试方法：testSelectAggregateOnRolledUpColumn
    final String maxError = "Rolled up column 'SLACKINGMIN' is not allowed in MAX";
    final String plusError = "Rolled up column 'SLACKINGMIN' is not allowed in PLUS";

    sql("select max(^slackingmin^) from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(maxError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select count(slackingmin) from emp_r").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select count(empno, deptno, slackingmin) from emp_r").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select sum(slackingmin) from emp_r").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select empno, min(slackingmin) from emp_r group by empno").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select count(distinct slackingmin) from emp_r").ok();  // 创建SQL测试：解析并验证SQL语句

    sql("select sum(empno + ^slackingmin^) from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(plusError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select max(^slackingmin^) over t as a "  // 创建SQL测试：解析并验证SQL语句
        + "from emp_r window t as (partition by empno order by empno)")
        .fails(maxError);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRolledUpColumnInWhere() {  // 测试方法：testRolledUpColumnInWhere
    final String error = "Rolled up column 'SLACKINGMIN' is not allowed in GREATER_THAN";

    // Fire these slackers!!
    sql("select empno from emp_r where slacker and ^slackingmin^ > 60")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select sum(slackingmin) filter (where slacker and ^slackingmin^ > 60) from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRolledUpColumnInHaving() {  // 测试方法：testRolledUpColumnInHaving
    final String error = "Rolled up column 'SLACKINGMIN' is not allowed in SUM";

    sql("select deptno, sum(slackingmin) from emp_r group "  // 创建SQL测试：解析并验证SQL语句
        + "by deptno having sum(^slackingmin^) > 1000")
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRollUpInWindow() {  // 测试方法：testRollUpInWindow
    final String partitionError = "Rolled up column 'SLACKINGMIN' is not allowed in PARTITION BY";
    final String orderByError = "Rolled up column 'SLACKINGMIN' is not allowed in ORDER BY";

    sql("select empno, sum(slackingmin) over (partition by ^slackingmin^) from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(partitionError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select empno, sum(slackingmin) over (partition by empno, ^slackingmin^) from emp_r")  // 创建SQL测试：解析并验证SQL语句
        .fails(partitionError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select empno, sum(slackingmin) over (partition by empno order by ^slackingmin^) "  // 创建SQL测试：解析并验证SQL语句
        + "from emp_r")
        .fails(orderByError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select empno, sum(slackingmin) over slackingmin "  // 创建SQL测试：解析并验证SQL语句
        + "from emp_r window slackingmin as (partition by ^slackingmin^)")
        .fails(partitionError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select sum(slackingmin) over t "  // 创建SQL测试：解析并验证SQL语句
        + "from emp_r window t as (partition by empno order by ^slackingmin^, empno)")
        .fails(orderByError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select sum(slackingmin) over t as a "  // 创建SQL测试：解析并验证SQL语句
        + "from emp_r window t as (partition by empno order by ^slackingmin^, empno)")
        .fails(orderByError);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRollUpInGroupBy() {  // 测试方法：testRollUpInGroupBy
    final String error = "Rolled up column 'SLACKINGMIN' is not allowed in GROUP BY";

    sql("select empno, count(distinct empno) from emp_r group by empno, ^slackingmin^")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select empno from emp_r group by grouping sets (empno, ^slackingmin^)")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRollUpInOrderBy() {  // 测试方法：testRollUpInOrderBy
    final String error = "Rolled up column 'SLACKINGMIN' is not allowed in ORDER BY";

    sql("select empno from emp_r order by ^slackingmin^ asc")  // 创建SQL测试：解析并验证SQL语句
        .fails(error);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select slackingmin from (select empno as slackingmin from emp_r)\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by slackingmin")
        .ok();  // 断言验证：期望SQL验证通过

    sql("select empno, sum(slackingmin) from emp_r group by empno\n"  // 创建SQL测试：解析并验证SQL语句
        + "order by sum(slackingmin)").ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testRollUpInJoin() {  // 测试方法：testRollUpInJoin
    final String onError = "Rolled up column 'SLACKINGMIN' is not allowed in ON";
    final String usingError = "Rolled up column 'SLACKINGMIN' is not allowed in USING";
    final String selectError = "Rolled up column 'SLACKINGMIN' is not allowed in SELECT";

    sql("select * from (select deptno, ^slackingmin^ from emp_r)\n"  // 创建SQL测试：解析并验证SQL语句
        + " join dept using (deptno)")
        .fails(selectError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from dept as a\n"  // 创建SQL测试：解析并验证SQL语句
        + "join (select deptno, ^slackingmin^ from emp_r) using (deptno)")
        .fails(selectError);  // 断言验证：期望SQL验证失败并检查错误消息

    sql("select * from emp_r as a\n"  // 创建SQL测试：解析并验证SQL语句
        + "join dept_r as b using (deptno, ^slackingmin^)")
        .fails(usingError);  // 断言验证：期望SQL验证失败并检查错误消息

    // Even though the emp_r.slackingmin column will be under the SqlNode for '=',
    // The error should say it happened in 'ON' instead
    sql("select * from emp_r\n"  // 创建SQL测试：解析并验证SQL语句
        + "join dept_r on (^emp_r.slackingmin^ = dept_r.slackingmin)")
        .fails(onError);  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJsonValueExpressionOperator() {  // 测试方法：testJsonValueExpressionOperator
    expr("'{}' format json").ok();  // 断言验证：期望SQL验证通过
    expr("'{}' format json encoding utf8").ok();  // 断言验证：期望SQL验证通过
    expr("'{}' format json encoding utf16").ok();  // 断言验证：期望SQL验证通过
    expr("'{}' format json encoding utf32").ok();  // 断言验证：期望SQL验证通过
    expr("'{}' format json")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("ANY NOT NULL");  // 类型验证：检查结果列的类型
    expr("'null' format json")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("ANY NOT NULL");  // 类型验证：检查结果列的类型
    expr("cast(null as varchar) format json")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("ANY");  // 类型验证：检查结果列的类型
    expr("null format json")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("ANY");  // 类型验证：检查结果列的类型
    expr("^null^ format json")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Illegal use of .NULL.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJsonExists() {  // 测试方法：testJsonExists
    expr("json_exists('{}', 'lax $')").ok();  // 断言验证：期望SQL验证通过
    expr("json_exists('{}', 'lax $')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonValue() {  // 测试方法：testJsonValue
    expr("json_value('{\"foo\":\"bar\"}', 'lax $.foo')").ok();  // 断言验证：期望SQL验证通过
    expr("json_value('{\"foo\":\"bar\"}', 123)").ok();  // 断言验证：期望SQL验证通过
    expr("json_value('{\"foo\":\"bar\"}', 'lax $.foo')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_value('{\"foo\":100}', 'lax $.foo')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_value('{\"foo\":100}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning integer)")
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("json_value('{\"foo\":100}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning integer default 0 on empty default 0 on error)")
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("json_value('{\"foo\":100}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning integer default null on empty default null on error)")
        .columnType("INTEGER");  // 类型验证：检查结果列的类型

    expr("json_value('{\"foo\":true}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning boolean default 100 on empty default 100 on error)")
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型

    // test type inference of default value
    expr("json_value('{\"foo\":100}', 'lax $.foo' default 'empty' on empty)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_value('{\"foo\":100}', 'lax $.foo' returning boolean"  // 创建表达式测试：解析并验证SQL表达式
        + " default 100 on empty)")
        .columnType("BOOLEAN");  // 类型验证：检查结果列的类型

    expr("json_value('{\"foo\":[100, null, 200]}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning integer array)")
        .columnType("INTEGER ARRAY");  // 类型验证：检查结果列的类型

    expr("json_value('{\"foo\":[[100, null, 200]]}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning integer array array)")
        .columnType("INTEGER ARRAY ARRAY");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonQuery() {  // 测试方法：testJsonQuery
    expr("json_query('{\"foo\":\"bar\"}', 'lax $')").ok();  // 断言验证：期望SQL验证通过
    expr("json_query('{\"foo\":\"bar\"}', 'lax $')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_query('{\"foo\":\"bar\"}', 'strict $')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_query('{\"foo\":\"bar\"}', 'strict $' WITH WRAPPER)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_query('{\"foo\":\"bar\"}', 'strict $' EMPTY OBJECT ON EMPTY)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_query('{\"foo\":\"bar\"}', 'strict $' EMPTY ARRAY ON ERROR)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_query('{\"foo\":\"bar\"}', 'strict $' EMPTY OBJECT ON EMPTY "  // 创建表达式测试：解析并验证SQL表达式
        + "EMPTY ARRAY ON ERROR EMPTY ARRAY ON EMPTY NULL ON ERROR)")
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型

    expr("json_query('{\"foo\":[100, null, 200]}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning integer array)")
        .columnType("INTEGER ARRAY");  // 类型验证：检查结果列的类型

    expr("json_query('{\"foo\":[[100, null, 200]]}', 'lax $.foo'"  // 创建表达式测试：解析并验证SQL表达式
        + "returning integer array array)")
        .columnType("INTEGER ARRAY ARRAY");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonArray() {  // 测试方法：testJsonArray
    expr("json_array()").ok();  // 断言验证：期望SQL验证通过
    expr("json_array('foo', 'bar')").ok();  // 断言验证：期望SQL验证通过
    expr("json_array('foo', 'bar')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonArrayAgg() {  // 测试方法：testJsonArrayAgg
    sql("select json_arrayagg(ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    expr("json_arrayagg('foo')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonObject() {  // 测试方法：testJsonObject
    expr("json_object()").ok();  // 断言验证：期望SQL验证通过
    expr("json_object('foo': 'bar')").ok();  // 断言验证：期望SQL验证通过
    expr("json_object('foo': 'bar')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000) NOT NULL");  // 类型验证：检查结果列的类型
    expr("^json_object(100: 'bar')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Expected a character type*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJsonPretty() {  // 测试方法：testJsonPretty
    sql("select json_pretty(ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    expr("json_pretty('{\"foo\":\"bar\"}')").ok();  // 断言验证：期望SQL验证通过
    expr("json_pretty('{\"foo\":\"bar\"}')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    sql("select json_pretty(^NULL^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s).*Illegal use of .NULL.*");  // 断言验证：期望SQL验证失败并检查错误消息
    sql("select json_pretty(NULL) from emp").ok();  // 创建SQL测试：解析并验证SQL语句

    if (!Bug.CALCITE_2869_FIXED) {
      // the case should throw an error but currently validation
      // is done during sql-to-rel process.
      //
      // see StandardConvertletTable.JsonOperatorValueExprConvertlet
      return;
    }
    sql("select json_pretty(^1^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(.*)JSON_VALUE_EXPRESSION(.*)");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJsonStorageSize() {  // 测试方法：testJsonStorageSize
    sql("select json_storage_size(ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    expr("json_storage_size('{\"foo\":\"bar\"}')").ok();  // 断言验证：期望SQL验证通过
    expr("json_storage_size('{\"foo\":\"bar\"}')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonType() {  // 测试方法：testJsonType
    sql("select json_type(ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    expr("json_type('{\"foo\":\"bar\"}')").ok();  // 断言验证：期望SQL验证通过
    expr("json_type('{\"foo\":\"bar\"}')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(20)");  // 类型验证：检查结果列的类型

    if (!Bug.CALCITE_2869_FIXED) {
      return;
    }
    sql("select json_type(^1^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(.*)JSON_VALUE_EXPRESSION(.*)");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJsonDepth() {  // 测试方法：testJsonDepth
    sql("select json_depth(ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    expr("json_depth('{\"foo\":\"bar\"}')").ok();  // 断言验证：期望SQL验证通过
    expr("json_depth('{\"foo\":\"bar\"}')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型

    if (!Bug.CALCITE_2869_FIXED) {
      return;
    }
    sql("select json_depth(^1^) from emp")  // 创建SQL测试：解析并验证SQL语句
        .fails("(.*)JSON_VALUE_EXPRESSION(.*)");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJsonLength() {  // 测试方法：testJsonLength
    expr("json_length('{\"foo\":\"bar\"}')").ok();  // 断言验证：期望SQL验证通过
    expr("json_length('{\"foo\":\"bar\"}', 'lax $')").ok();  // 断言验证：期望SQL验证通过
    expr("json_length('{\"foo\":\"bar\"}')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("json_length('{\"foo\":\"bar\"}', 'lax $')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
    expr("json_length('{\"foo\":\"bar\"}', 'strict $')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("INTEGER");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonKeys() {  // 测试方法：testJsonKeys
    expr("json_keys('{\"foo\":\"bar\"}', 'lax $')").ok();  // 断言验证：期望SQL验证通过
    expr("json_keys('{\"foo\":\"bar\"}', 'lax $')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_keys('{\"foo\":\"bar\"}', 'strict $')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonRemove() {  // 测试方法：testJsonRemove
    expr("json_remove('{\"foo\":\"bar\"}', '$')").ok();  // 断言验证：期望SQL验证通过
    expr("json_remove('{\"foo\":\"bar\"}', '$')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_remove('{\"foo\":\"bar\"}', 1, '2', 3)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("json_remove('{\"foo\":\"bar\"}', 1, 2, 3)")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    sql("select ^json_remove('{\"foo\":\"bar\"}')^")  // 创建SQL测试：解析并验证SQL语句
        .fails("(?s).*Invalid number of arguments.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testJsonObjectAgg() {  // 测试方法：testJsonObjectAgg
    sql("select json_objectagg(ename: empno) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select json_objectagg(empno: ename) from emp").ok();  // 创建SQL测试：解析并验证SQL语句
    sql("select ^json_objectagg(empno: ename)^ from emp")  // 创建SQL测试：解析并验证SQL语句
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
    expr("json_objectagg('foo': 'bar')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000) NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testJsonPredicate() {  // 测试方法：testJsonPredicate
    expr("'{}' is json")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'{}' is json value")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'{}' is json object")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'[]' is json array")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'100' is json scalar")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'{}' is not json")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'{}' is not json value")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'{}' is not json object")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'[]' is not json array")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("'100' is not json scalar")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("100 is json value")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("BOOLEAN NOT NULL");  // 类型验证：检查结果列的类型
    expr("^100 is json value^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test public void testJsonInsert() {
    expr("json_insert('{ \"a\": 1, \"b\": [2]}', '$.a', 10, '$.c', '[true]')").ok();  // 断言验证：期望SQL验证通过
    expr("json_insert('{ \"a\": 1, \"b\": [2]}', '$.a', 10, '$.c', '[true]')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("select ^json_insert('{\"foo\":\"bar\"}')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Invalid number of arguments.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test public void testJsonReplace() {
    expr("json_replace('{ \"a\": 1, \"b\": [2]}', '$.a', 10, '$.c', '[true]')").ok();  // 断言验证：期望SQL验证通过
    expr("json_replace('{ \"a\": 1, \"b\": [2]}', '$.a', 10, '$.c', '[true]')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("select ^json_replace('{\"foo\":\"bar\"}')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Invalid number of arguments.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test public void testJsonSet() {
    expr("json_set('{ \"a\": 1, \"b\": [2]}', '$.a', 10, '$.c', '[true]')").ok();  // 断言验证：期望SQL验证通过
    expr("json_set('{ \"a\": 1, \"b\": [2]}', '$.a', 10, '$.c', '[true]')")  // 创建表达式测试：解析并验证SQL表达式
        .columnType("VARCHAR(2000)");  // 类型验证：检查结果列的类型
    expr("select ^json_set('{\"foo\":\"bar\"}')^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Invalid number of arguments.*");  // 断言验证：期望SQL验证失败并检查错误消息
  }

  @Test void testRegexpReplace() {  // 测试方法：testRegexpReplace
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.ORACLE);

    expr("REGEXP_REPLACE('a b c', 'a', 'X')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def ghi', '[a-z]+', 'X', 2)")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def ghi', '[a-z]+', 'X', 1, 3)")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def GHI', '[a-z]+', 'X', 1, 3, 'c')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    // Implicit type coercion.
    expr("REGEXP_REPLACE(null, '(-)', '###')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('100-200', null, '###')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('100-200', '(-)', null)")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def ghi', '[a-z]+', 'X', '2')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def ghi', '[a-z]+', 'X', '1', '3')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    // The last argument to REGEXP_REPLACE should be specific character, but with
    // implicit type coercion, the validation still passes.
    expr("REGEXP_REPLACE('abc def ghi', '[a-z]+', 'X', '1', '3', '1')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
  }

  @Test void testPgRegexpReplace() {  // 测试方法：testPgRegexpReplace
    final SqlOperatorTable opTable = operatorTableFor(SqlLibrary.POSTGRESQL);

    expr("REGEXP_REPLACE('a b c', 'a', 'X')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def ghi', '[a-z]+', 'X')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def ghi', '[a-z]+', 'X')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('abc def GHI', '[a-z]+', 'X', 'c')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR NOT NULL");  // 类型验证：检查结果列的类型
    // Implicit type coercion.
    expr("REGEXP_REPLACE(null, '(-)', '###')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('100-200', null, '###')")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
    expr("REGEXP_REPLACE('100-200', '(-)', null)")  // 创建表达式测试：解析并验证SQL表达式
        .withOperatorTable(opTable)
        .columnType("VARCHAR");  // 类型验证：检查结果列的类型
  }

  @Test void testInvalidFunctionCall() {  // 测试方法：testInvalidFunctionCall
    final SqlOperatorTable operatorTable =
        MockSqlOperatorTable.standard().extend();

    // With implicit type coercion.
    expr("^unknown_udf(1, 2)^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "UNKNOWN_UDF\\(<NUMERIC>, <NUMERIC>\\).*");
    expr("^power(cast(1 as timestamp), cast(2 as timestamp))^")  // 创建表达式测试：解析并验证SQL表达式
        .fails("(?s).*Cannot apply 'POWER' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'POWER\\(<TIMESTAMP\\(0\\)>, <TIMESTAMP\\(0\\)>\\)'.*");
    expr("^myFUN(cast('124' as timestamp))^")  // 创建表达式测试：解析并验证SQL表达式
        .withCaseSensitive(true)
        .withOperatorTable(operatorTable)
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'MYFUN' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'MYFUN\\(<TIMESTAMP\\(0\\)>\\)'.*");
    expr("^myFUN(1, 2)^")  // 创建表达式测试：解析并验证SQL表达式
        .withCaseSensitive(true)
        .withOperatorTable(operatorTable)
        .withTypeCoercion(false)
        .fails("(?s).*No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "MYFUN\\(<NUMERIC>, <NUMERIC>\\).*");

    // Without implicit type coercion.
    expr("^unknown_udf(1, 2)^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "UNKNOWN_UDF\\(<NUMERIC>, <NUMERIC>\\).*");
    expr("^power(cast(1 as timestamp), cast(2 as timestamp))^")  // 创建表达式测试：解析并验证SQL表达式
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'POWER' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'POWER\\(<TIMESTAMP\\(0\\)>, <TIMESTAMP\\(0\\)>\\)'.*");
    expr("^myFUN(cast('124' as timestamp))^")  // 创建表达式测试：解析并验证SQL表达式
        .withCaseSensitive(true)
        .withOperatorTable(operatorTable)
        .withTypeCoercion(false)
        .fails("(?s).*Cannot apply 'MYFUN' to arguments of type "  // 断言验证：期望SQL验证失败并检查错误消息
            + "'MYFUN\\(<TIMESTAMP\\(0\\)>\\)'.*");
    expr("^myFUN(1, 2)^")  // 创建表达式测试：解析并验证SQL表达式
        .withCaseSensitive(true)
        .withOperatorTable(operatorTable)
        .withTypeCoercion(false)
        .fails("(?s).*No match found for function signature "  // 断言验证：期望SQL验证失败并检查错误消息
            + "MYFUN\\(<NUMERIC>, <NUMERIC>\\).*");
  }

  @Test void testPositionalAggregateWithExpandedCurrentDateFunction() {  // 测试方法：testPositionalAggregateWithExpandedCurrentDateFunction
    SqlConformance defaultPlusOrdinalGroupBy =
        new SqlDelegatingConformance(SqlConformanceEnum.DEFAULT) {
          @Override public boolean isGroupByOrdinal() {
            return true;
          }
        };
    sql("SELECT HIREDATE >= CURRENT_DATE, COUNT(*) "  // 创建SQL测试：解析并验证SQL语句
        + "FROM EMP GROUP BY 1")
        .withConformance(defaultPlusOrdinalGroupBy)
        .ok();  // 断言验证：期望SQL验证通过
  }

  @Test void testValidatorReportsOriginalQueryUsingReader()
      throws Exception {
    final String sql = "select a from b";
    final SqlParser.Config config = SqlParser.config();
    final String messagePassingSqlString;
    try {
      final SqlParser sqlParserReader = SqlParser.create(sql, config);
      final SqlNode node = sqlParserReader.parseQuery();
      final SqlValidator validator = fixture().factory.createValidator();
      final SqlNode x = validator.validate(node);
      fail("expecting an error, got " + x);
      return;
    } catch (CalciteContextException error) {
      // we want the exception to report column and line
      assertThat(error.getMessage(),
          is("At line 1, column 15: Object 'B' not found"));
      messagePassingSqlString = error.getMessage();
      // the error does not contain the original query (even using a String)
      assertNull(error.getOriginalStatement());
    }

    // parse SQL text using a java.io.Reader and not a java.lang.String
    try {
      final SqlParser sqlParserReader =
          SqlParser.create(new StringReader(sql), config);
      final SqlNode node = sqlParserReader.parseQuery();
      final SqlValidator validator = fixture().factory.createValidator();
      final SqlNode x = validator.validate(node);
      fail("expecting an error, got " + x);
    } catch (CalciteContextException error) {
      // we want exactly the same error as with java.lang.String input
      assertThat(error.getMessage(), is(messagePassingSqlString));
      // the error does not contain the original query (using a Reader)
      assertThat(error.getOriginalStatement(), nullValue());
    }
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6913">
   * [CALCITE-6913] Some casts inserted by type coercion do not have
   * source position information</a>. */
  @Test void testImplicitCastPosition() throws SqlParseException {
    final String sql = "select -'2'";
    final SqlParser.Config config = SqlParser.config();
    final SqlParser sqlParserReader = SqlParser.create(sql, config);
    final SqlNode node = sqlParserReader.parseQuery();
    final SqlValidator validator = fixture().factory.createValidator();
    final SqlNode x = validator.validate(node);
    assertThat(x instanceof SqlSelect, is(true));
    // After coercion the statement is SELECT -(CAST '2' AS DECIMAL(...))
    SqlSelect select = (SqlSelect) x;
    SqlNodeList selectList = select.getSelectList();
    assertThat(selectList.isEmpty(), is(false));
    SqlNode neg = selectList.get(0);
    assertThat(neg instanceof SqlCall, is(true));
    SqlNode cast = ((SqlCall) neg).getOperandList().get(0);
    assertThat(cast.getParserPosition().getLineNum(), is(1));
  }

  @Test void testValidateParameterizedExpression() throws SqlParseException {
    final SqlParser.Config config = SqlParser.config();
    final SqlValidator validator = fixture().factory.createValidator();
    final RelDataTypeFactory typeFactory = validator.getTypeFactory();
    final RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER);
    final RelDataType intTypeNull = typeFactory.createTypeWithNullability(intType, true);
    final Map<String, RelDataType> nameToTypeMap = new HashMap<>();
    nameToTypeMap.put("A", intType);
    nameToTypeMap.put("B", intTypeNull);
    final String expr = "a + b";
    final SqlParser parser = SqlParser.create(expr, config);
    final SqlNode sqlNode = parser.parseExpression();
    final SqlNode validated = validator.validateParameterizedExpression(sqlNode, nameToTypeMap);
    final RelDataType resultType = validator.getValidatedNodeType(validated);
    assertThat(resultType, hasToString("INTEGER"));
  }

  /**
   * Tests validation of must-filter columns.
   *
   * <p>If a table that implements
   * {@link org.apache.calcite.sql.validate.SemanticTable} tags fields as
   * 'must-filter', and the SQL query does not contain a WHERE or HAVING clause
   * on each of the tagged columns, the validator should throw an error.
   */
  @Test void testMustFilterColumns() {  // 测试方法：testMustFilterColumns
    final SqlValidatorFixture fixture = fixture()
        .withParserConfig(c -> c.withQuoting(Quoting.BACK_TICK))
        .withOperatorTable(operatorTableFor(SqlLibrary.BIG_QUERY))
        .withCatalogReader(MustFilterMockCatalogReader::create);
    // Basic query
    fixture.withSql("select empno\n"
            + "from emp\n"
            + "where job = 'doctor'\n"
            + "and empno = 1")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "where concat(emp.empno, ' ') = 'abc'^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息

    // SUBQUERIES
    fixture.withSql("select * from (\n"
            + "  select * from emp where empno = 1)\n"
            + "where job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过
    // Deceitful alias #1. Filter on 'j' is a filter on the underlying 'job'.
    fixture.withSql("select * from (\n"
            + "  select job as j, ename as job\n"
            + "  from emp\n"
            + "  where empno = 1)\n"
            + "where j = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过
    // Deceitful alias #2. Filter on 'job' is a filter on the underlying
    // 'slacker', so the underlying 'job' is missing a filter.
    fixture.withSql("^select * from (\n"
            + "  select job as j, slacker as job\n"
            + "  from emp\n"
            + "  where empno = 1)\n"
            + "where job = 'doctor'^")
        .fails(missingFilters("J"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select * from (\n"
            + "  select * from emp where job = 'doctor')\n"
            + "where empno = 1")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select * from (\n"
            + "  select empno from emp where job = 'doctor')\n"
            + "where empno = 1")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select * from (\n"
            + "  select * from emp where empno = 1)^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select * from (select * from `SALES`.`EMP`) as a1^ ")
        .fails(missingFilters("EMPNO", "JOB"));  // 断言验证：期望SQL验证失败并检查错误消息

    // JOINs
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno^")
        .fails(missingFilters("EMPNO", "JOB", "NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno\n"
            + "where emp.empno = 1^")
        .fails(missingFilters("JOB", "NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno\n"
            + "where emp.empno = 1\n"
            + "and emp.job = 'doctor'\n"
            + "and dept.name = 'ACCOUNTING'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno\n"
            + "where empno = 1\n"
            + "and job = 'doctor'\n"
            + "and dept.name = 'ACCOUNTING'")
        .ok();  // 断言验证：期望SQL验证通过

    // Self-join
    fixture.withSql("^select *\n"
            + "from `SALES`.emp a1\n"
            + "join `SALES`.emp a2 on a1.empno = a2.empno^")
        .fails(missingFilters("EMPNO", "EMPNO0", "JOB", "JOB0"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select *\n"
            + "from emp a1\n"
            + "join emp a2 on a1.empno = a2.empno\n"
            + "where a2.empno = 1\n"
            + "and a1.empno = 1\n"
            + "and a2.job = 'doctor'^")
        // There are two JOB columns but only one is filtered
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select *\n"
            + "from emp a1\n"
            + "join emp a2 on a1.empno = a2.empno\n"
            + "where a1.empno = 1\n"
            + "and a1.job = 'doctor'\n"
            + "and a2.empno = 2\n"
            + "and a2.job = 'undertaker'\n")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select *\n"
            + " from (select * from `SALES`.`EMP`) as a1\n"
            + "join (select * from `SALES`.`EMP`) as a2\n"
            + "  on a1.`EMPNO` = a2.`EMPNO`^")
        .fails(missingFilters("EMPNO", "EMPNO0", "JOB", "JOB0"));  // 断言验证：期望SQL验证失败并检查错误消息


    // USING
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept using(deptno)\n"
            + "where emp.empno = 1^")
        .fails(missingFilters("JOB", "NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select *\n"
            + "from emp\n"
            + "join dept using(deptno)\n"
            + "where emp.empno = 1\n"
            + "and emp.job = 'doctor'\n"
            + "and dept.name = 'ACCOUNTING'")
        .ok();  // 断言验证：期望SQL验证通过

    // GROUP BY (HAVING)
    fixture.withSql("select *\n"
            + "from dept\n"
            + "group by deptno, name\n"
            + "having name = 'accounting_dept'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select *\n"
            + "from dept\n"
            + "group by deptno, name^")
        .fails(missingFilters("NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select name\n"
            + "from dept\n"
            + "group by name\n"
            + "having name = 'accounting'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select name\n"
            + "from dept\n"
            + "group by name^ ")
        .fails(missingFilters("NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select sum(sal)\n"
            + "from emp\n"
            + "where empno > 10\n"
            + "and job = 'doctor'\n"
            + "group by empno\n"
            + "having sum(sal) > 100")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select sum(sal)\n"
            + "from emp\n"
            + "where empno > 10\n"
            + "group by empno\n"
            + "having sum(sal) > 100^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息

    // CTE
    fixture.withSql("^WITH cte AS (\n"
            + "  select * from emp order by empno)^\n"
            + "SELECT * from cte")
        .fails(missingFilters("EMPNO", "JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^WITH cte AS (\n"
            + "  select * from emp where empno = 1)^\n"
            + "SELECT * from cte")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("WITH cte AS (\n"
            + "  select *\n"
            + "  from emp\n"
            + "  where empno = 1\n"
            + "  and job = 'doctor')\n"
            + "SELECT * from cte")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^WITH cte AS (\n"
            + "  select * from emp)^\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where empno = 1")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("WITH cte AS (\n"
            + "  select * from emp)\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where empno = 1\n"
            + "and job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("WITH cte AS (\n"
            + "  select * from emp where empno = 1)\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("WITH cte AS (\n"
            + "  select empno, job from emp)\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where empno = 1\n"
            + "and job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过

    // Filters are missing on EMPNO and JOB, but the error message only
    // complains about JOB because EMPNO is in the SELECT clause, and could
    // theoretically be filtered by an enclosing query.
    fixture.withSql("^select empno\n"
            + "from emp^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select empno,\n"
            + "  sum(sal) over (order by mgr)\n"
            + "from emp^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /**
   * Tests validation of must-filter columns with the inclusion of bypass fields.
   *
   * <p>If a table that implements
   * {@link org.apache.calcite.sql.validate.SemanticTable} tags fields as
   * 'must-filter', and the SQL query does not contain a WHERE or HAVING clause
   * on each of the tagged columns, the validator should throw an error.
   * If any bypass field for a table is in a WHERE or HAVING clause for that
   * SELECT statement, the must-filter requirements for that table are
   * disabled.
   */
  @Test void testMustFilterColumnsWithBypass() {  // 测试方法：testMustFilterColumnsWithBypass
    final SqlValidatorFixture fixture = fixture()
        .withParserConfig(c -> c.withQuoting(Quoting.BACK_TICK))
        .withOperatorTable(operatorTableFor(SqlLibrary.BIG_QUERY))
        .withCatalogReader(MustFilterMockCatalogReader::create);

    // Basic query
    fixture.withSql("select empno\n"
            + "from emp\n"
            + "where job = 'doctor'\n"
            + "and empno = 1")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "where concat(emp.empno, ' ') = 'abc'^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息

    // ENAME is a bypass field
    fixture.withSql("select *\n"
            + "from emp\n"
            + "where concat(emp.ename, ' ') = 'abc'^")
        .ok();  // 断言验证：期望SQL验证通过

    // SUBQUERIES
    fixture.withSql("select * from (\n"
            + "  select * from emp where empno = 1)\n"
            + "where job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select * from (\n"
            + "  select ename from emp where empno = 1)^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select * from (\n"
            + "  select job, ename from emp where empno = 1)"
            + "where ename = '1'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select * from (\n"
            + "  select empno, job from emp)\n"
            + "where job = 'doctor' and empno = 1")
        .ok();  // 断言验证：期望SQL验证通过

    // Deceitful alias #1. Filter on 'j' is a filter on the underlying 'job'.
    fixture.withSql("select * from (\n"
            + "  select job as j, ename as job\n"
            + "  from emp\n"
            + "  where empno = 1)\n"
            + "where j = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过

    // Deceitful alias #2. Filter on 'job' is a filter on the underlying
    // 'slacker', so the underlying 'job' is missing a filter.
    fixture.withSql("^select * from (\n"
            + "  select job as j, slacker as job\n"
            + "  from emp\n"
            + "  where empno = 1)\n"
            + "where job = 'doctor'^")
        .fails(missingFilters("J"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Deceitful alias #3. Filter on 'job' is a filter on the underlying
    // 'ename', which is a bypass field thus no exception.
    fixture.withSql("select * from (\n"
            + "  select job as j, ename as job\n"
            + "  from emp\n"
            + "  where empno = 1)\n"
            + "where job = 'doctor'^")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select * from (\n"
            + "  select * from emp where job = 'doctor')\n"
            + "where empno = 1")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select * from (\n"
            + "  select empno from emp where job = 'doctor')\n"
            + "where empno = 1")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select * from (\n"
            + "  select * from emp where empno = 1)^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is valid because ENAME is a bypass field
    fixture.withSql("select * from (\n"
            + "  select * from emp where ename = 1)^")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select * from (select * from `SALES`.`EMP`) as a1^ ")
        .fails(missingFilters("EMPNO", "JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select *\n"
            + "from (select * from `SALES`.`EMP`) as a1\n"
            + "where ename = '1'^ ")
        .ok();  // 断言验证：期望SQL验证通过

    // JOINs
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno^")
        .fails(missingFilters("EMPNO", "JOB", "NAME"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is invalid because ENAME is a bypass field for EMP table, but not
    // the DEPT table.
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno where ename = '1'^")
        .fails(missingFilters("NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno\n"
            + "where emp.empno = 1^")
        .fails(missingFilters("JOB", "NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno\n"
            + "where emp.empno = 1\n"
            + "and emp.job = 'doctor'\n"
            + "and dept.name = 'ACCOUNTING'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select *\n"
            + "from emp\n"
            + "join dept on emp.deptno = dept.deptno\n"
            + "where empno = 1\n"
            + "and job = 'doctor'\n"
            + "and dept.name = 'ACCOUNTING'")
        .ok();  // 断言验证：期望SQL验证通过

    // Self-join
    fixture.withSql("^select *\n"
            + "from `SALES`.emp a1\n"
            + "join `SALES`.emp a2 on a1.empno = a2.empno^")
        .fails(missingFilters("EMPNO", "EMPNO0", "JOB", "JOB0"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is invalid because filtering on a bypass field in a1 disables
    // must-filter for a1, but a2 must-filters are still required.
    fixture.withSql("^select *\n"
            + "from `SALES`.emp a1\n"
            + "join `SALES`.emp a2 on a1.empno = a2.empno\n"
            + "where a1.ename = '1'^")
            .fails(missingFilters("EMPNO0", "JOB0"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is invalid because here are two JOB columns but only one is
    // filtered.
    fixture.withSql("^select *\n"
            + "from emp a1\n"
            + "join emp a2 on a1.empno = a2.empno\n"
            + "where a2.empno = 1\n"
            + "and a1.empno = 1\n"
            + "and a2.job = 'doctor'^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select *\n"
            + "from emp a1\n"
            + "join emp a2 on a1.empno = a2.empno\n"
            + "where a2.empno = 1\n"
            + "and a1.empno = 1\n"
            + "and a2.job = 'doctor'^\n"
            + "and a1.ename = '1'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select *\n"
            + "from emp a1\n"
            + "join emp a2 on a1.empno = a2.empno\n"
            + "where a1.empno = 1\n"
            + "and a1.job = 'doctor'\n"
            + "and a2.empno = 2\n"
            + "and a2.job = 'undertaker'\n")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select *\n"
            + " from (select * from `SALES`.`EMP`) as a1\n"
            + "join (select * from `SALES`.`EMP`) as a2\n"
            + "  on a1.`EMPNO` = a2.`EMPNO`^")
        .fails(missingFilters("EMPNO", "EMPNO0", "JOB", "JOB0"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is invalid because filtering on a bypass field in a1 disables
    // must-filter for a1, but a2 must-filters are still required.
    fixture.withSql("^select *\n"
            + " from (select * from `SALES`.`EMP`) as a1\n"
            + "join (select * from `SALES`.`EMP`) as a2\n"
            + "  on a1.`EMPNO` = a2.`EMPNO`\n"
            + "where a1.ename = '1'^")
        .fails(missingFilters("EMPNO0", "JOB0"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select *\n"
            + " from (select * from `SALES`.`EMP` where `ENAME` = '1') as a1\n"
            + "join (select * from `SALES`.`EMP`) as a2\n"
            + "  on a1.`EMPNO` = a2.`EMPNO`^")
        .fails(missingFilters("EMPNO0", "JOB0"));  // 断言验证：期望SQL验证失败并检查错误消息

    // USING
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept using(deptno)\n"
            + "where emp.empno = 1^")
        .fails(missingFilters("JOB", "NAME"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is invalid because ENAME is bypass field for EMP, but not for DEPT.
    fixture.withSql("^select *\n"
            + "from emp\n"
            + "join dept using(deptno)\n"
            + "where emp.ename = '1'^")
        .fails(missingFilters("NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select *\n"
            + "from emp\n"
            + "join dept using(deptno)\n"
            + "where emp.empno = 1\n"
            + "and emp.job = 'doctor'\n"
            + "and dept.name = 'ACCOUNTING'")
        .ok();  // 断言验证：期望SQL验证通过

    // GROUP BY (HAVING)
    fixture.withSql("select *\n"
            + "from dept\n"
            + "group by deptno, name\n"
            + "having name = 'accounting_dept'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select *\n"
            + "from dept\n"
            + "group by deptno, name^")
        .fails(missingFilters("NAME"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is valid because DEPTNO is bypass field.
    fixture.withSql("select *\n"
            + "from dept\n"
            + "group by deptno, name\n"
            + "having deptno > '1'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select name\n"
            + "from dept\n"
            + "group by name\n"
            + "having name = 'accounting'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select name\n"
            + "from dept\n"
            + "group by name^ ")
        .fails(missingFilters("NAME"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select sum(sal)\n"
            + "from emp\n"
            + "where empno > 10\n"
            + "and job = 'doctor'\n"
            + "group by empno\n"
            + "having sum(sal) > 100")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^select sum(sal)\n"
            + "from emp\n"
            + "where empno > 10\n"
            + "group by empno\n"
            + "having sum(sal) > 100^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select sum(sal)\n"
            + "from emp\n"
            + "where empno > 10\n"
            + "group by empno\n"
            + "having sum(sal) > 100^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("select sum(sal), job\n"
            + "from emp\n"
            + "where empno > 10\n"
            + "group by job\n"
            + "having job = 'undertaker'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select sum(sal), ename\n"
            + "from emp\n"
            + "where empno > 10\n"
            + "group by empno, ename\n"
            + "having ename = '1'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("select sum(sal)\n"
            + "from emp\n"
            + "where ename = '1'\n"
            + "group by empno, ename\n"
            + "having sum(sal) > 100")
        .ok();  // 断言验证：期望SQL验证通过

    // CTE
    fixture.withSql("^WITH cte AS (\n"
            + "  select * from emp order by empno)^\n"
            + "SELECT * from cte")
        .fails(missingFilters("EMPNO", "JOB"));  // 断言验证：期望SQL验证失败并检查错误消息

    // Query is valid because ENAME is a bypass field.
    fixture.withSql("WITH cte AS (\n"
            + "  select * from emp where ename = '1' order by empno)^\n"
            + "SELECT * from cte")
        .ok();  // 断言验证：期望SQL验证通过

    // Query is valid because ENAME is a bypass field.
    fixture.withSql("WITH cte AS (\n"
        + "  select * from emp order by empno)^\n"
        + "SELECT * from cte where ename = '1'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^WITH cte AS (\n"
            + "  select * from emp where empno = 1)^\n"
            + "SELECT * from cte")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("WITH cte AS (\n"
            + "  select *\n"
            + "  from emp\n"
            + "  where empno = 1\n"
            + "  and job = 'doctor')\n"
            + "SELECT * from cte")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("^WITH cte AS (\n"
            + "  select * from emp)^\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where empno = 1")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("WITH cte AS (\n"
            + "  select * from emp where ename = '1')^\n"
            + "SELECT *\n"
            + "from cte\n")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("WITH cte AS (\n"
            + "  select * from emp)^\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where ename = '1'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("WITH cte AS (\n"
            + "  select * from emp)\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where empno = 1\n"
            + "and job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("WITH cte AS (\n"
        + "  select * from emp where empno = 1)\n"
        + "SELECT *\n"
        + "from cte\n"
        + "where job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过
    fixture.withSql("WITH cte AS (\n"
            + "  select empno, job from emp)\n"
            + "SELECT *\n"
            + "from cte\n"
            + "where empno = 1\n"
            + "and job = 'doctor'")
        .ok();  // 断言验证：期望SQL验证通过

    // Query is invalid because filters are missing on EMPNO and JOB.
    // The error message only complains about JOB because EMPNO is in the SELECT
    // clause, and could theoretically be filtered by an enclosing query.
    fixture.withSql("^select empno\n"
            + "from emp^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
    fixture.withSql("^select empno,\n"
            + "  sum(sal) over (order by mgr)\n"
            + "from emp^")
        .fails(missingFilters("JOB"));  // 断言验证：期望SQL验证失败并检查错误消息
  }

  /** Returns a message that the particular columns are not filtered. */
  private static String missingFilters(String... args) {
    return "SQL statement did not contain filters on the following fields: \\["
        + String.join(", ", new TreeSet<>(Arrays.asList(args)))
        + "\\]";
  }

  @Test void testAccessingNestedFieldsOfNullableRecord() {  // 测试方法：testAccessingNestedFieldsOfNullableRecord
    sql("select ROW_COLUMN_ARRAY[0].NOT_NULL_FIELD from NULLABLEROWS.NR_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .type("RecordType(BIGINT EXPR$0) NOT NULL");
    sql("select ROW_COLUMN_ARRAY[0]['NOT_NULL_FIELD'] from NULLABLEROWS.NR_T1")  // 创建SQL测试：解析并验证SQL语句
        .withExtendedCatalog()
        .type("RecordType(BIGINT EXPR$0) NOT NULL");

    final SqlOperatorTable operatorTable =
        MockSqlOperatorTable.standard().extend();
    sql("select * FROM TABLE(ROW_FUNC()) AS T(a, b)")  // 创建SQL测试：解析并验证SQL语句
        .withOperatorTable(operatorTable)
        .type("RecordType(BIGINT NOT NULL A, BIGINT B) NOT NULL");
  }

  /** Validator that rewrites columnar sql identifiers 'UNEXPANDED'.'Something'
   * to 'DEPT'.'Something', where 'Something' is any string. */
  private static class UnexpandedToDeptValidator extends SqlValidatorImpl {
    UnexpandedToDeptValidator(SqlOperatorTable opTab,
        SqlValidatorCatalogReader catalogReader,
        RelDataTypeFactory typeFactory, Config config) {
      super(opTab, catalogReader, typeFactory, config);
    }

    @Override public SqlNode validate(SqlNode topNode) {
      SqlNode rewrittenNode = rewriteNode(topNode);
      return super.validate(rewrittenNode);
    }

    private static SqlNode rewriteNode(SqlNode sqlNode) {
      return sqlNode.accept(new SqlShuttle() {
        @Override public SqlNode visit(SqlIdentifier id) {
          return rewriteIdentifier(id);
        }
      });
    }

    private static SqlIdentifier rewriteIdentifier(SqlIdentifier sqlIdentifier) {
      if (sqlIdentifier.names.size() != 2) {
        return sqlIdentifier;
      }

      if (sqlIdentifier.names.get(0).equals("UNEXPANDED")) {
        return new SqlIdentifier(asList("DEPT", sqlIdentifier.names.get(1)),
            null, sqlIdentifier.getParserPosition(),
            asList(sqlIdentifier.getComponentParserPosition(0),
                sqlIdentifier.getComponentParserPosition(1)));
      } else if (sqlIdentifier.names.get(0).equals("DEPT")) {
        //  Identifiers are expanded multiple times
        return sqlIdentifier;
      } else {
        return sqlIdentifier;
      }
    }
  }
}
