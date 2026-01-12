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
 */ // Apache许可证声明，允许在遵守许可证条款的情况下使用和修改此代码
package org.apache.calcite.sql.test; // 定义包名，指示此文件属于org.apache.calcite.sql.test包

import org.apache.calcite.avatica.util.Casing; // 导入Casing类，用于处理标识符的大小写策略
import org.apache.calcite.avatica.util.Quoting; // 导入Quoting类，用于处理SQL标识符的引用方式
import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性类，用于配置Calcite连接
import org.apache.calcite.config.Lex; // 导入Lex类，用于配置SQL词法分析策略
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类，表示SQL操作符
import org.apache.calcite.sql.SqlOperatorTable; // 导入SqlOperatorTable接口，用于存储和查找SQL操作符
import org.apache.calcite.sql.fun.SqlLibrary; // 导入SqlLibrary枚举，定义不同的SQL函数库
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory; // 导入SqlLibraryOperatorTableFactory类，用于创建SQL库操作符表
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，用于解析SQL语句
import org.apache.calcite.sql.parser.StringAndPos; // 导入StringAndPos类，用于跟踪字符串及其位置信息
import org.apache.calcite.sql.test.SqlTester.ResultChecker; // 导入ResultChecker接口，用于检查SQL查询结果
import org.apache.calcite.sql.test.SqlTester.TypeChecker; // 导入TypeChecker接口，用于检查SQL表达式类型
import org.apache.calcite.sql.validate.SqlConformance; // 导入SqlConformance接口，定义SQL标准符合性级别
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SqlConformanceEnum枚举，定义具体的SQL标准符合性级别
import org.apache.calcite.sql.validate.SqlValidator; // 导入SqlValidator类，用于验证SQL语句
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert类，提供测试辅助工具
import org.apache.calcite.test.ConnectionFactories; // 导入ConnectionFactories类，用于创建连接工厂
import org.apache.calcite.test.ConnectionFactory; // 导入ConnectionFactory接口，用于创建数据库连接
import org.apache.calcite.test.Matchers; // 导入Matchers类，提供测试结果匹配器
import org.apache.calcite.util.Bug; // 导入Bug类，用于标记已知的bug和修复状态

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.util.ArrayList; // 导入ArrayList类，提供动态数组实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.function.Consumer; // 导入Consumer函数式接口，表示接受单个参数的操作
import java.util.function.UnaryOperator; // 导入UnaryOperator函数式接口，表示对单个参数的一元操作

import static org.apache.calcite.rel.type.RelDataTypeImpl.NON_NULLABLE_SUFFIX; // 导入非空后缀常量
import static org.apache.calcite.sql.test.ResultCheckers.isSingle; // 导入isSingle方法，用于创建单个结果检查器

/**
 * A fixture for testing the SQL operators. // 用于测试SQL操作符的测试装置
 *
 * <p>It provides a fluent API so that you can write tests by chaining method
 * calls. // 提供流式API，允许通过链式方法调用编写测试
 *
 * <p>It is immutable. If you have two test用例 that require a similar set up
 * (for example, the same SQL expression and parser configuration), it is safe
 * to use the same fixture object as a starting point for both tests. // 它是不可变的。如果两个测试用例需要相似的设置（例如相同的SQL表达式和解析器配置），可以安全地使用同一个fixture对象作为两个测试的起点
 *
 * <p>The idea is that when you define an operator (or another piece of SQL
 * functionality), you can define the logical behavior of that operator once, as
 * part of that operator. Later you can define one or more physical
 * implementations of that operator, and test them all using the same set of
 * tests. // 核心思想是：当定义一个操作符（或其他SQL功能）时，可以一次性定义该操作符的逻辑行为作为操作符的一部分。之后可以定义一个或多个该操作符的物理实现，并使用相同的测试集来测试它们
 *
 * <p>Depending on the implementation of {@link SqlTester} used
 * (see {@link #withTester(UnaryOperator)}), the fixture may or may not
 * evaluate expressions and check their results. // 根据使用的SqlTester实现（见withTester方法），测试装置可能会也可能不会计算表达式并检查结果
 */
public interface SqlOperatorFixture extends AutoCloseable { // 定义SqlOperatorFixture接口，继承AutoCloseable接口以支持资源自动关闭
  //~ Enums ------------------------------------------------------------------ // 枚举类型定义区域

  // TODO: Change message // TODO注释，提示需要更改此消息
  String INVALID_CHAR_MESSAGE = "(?s).*"; // 定义无效字符错误消息的正则表达式模式，(?s)表示单行模式，.*匹配任意字符

  String OUT_OF_RANGE_MESSAGE = ".* out of range.*"; // 定义超出范围错误消息的正则表达式模式

  String INTEGER_OVERFLOW = "integer overflow.*"; // 定义整数溢出错误消息的正则表达式模式

  String LONG_OVERFLOW = "long overflow.*"; // 定义长整数溢出错误消息的正则表达式模式

  String DECIMAL_OVERFLOW = ".*cannot be represented as a DECIMAL.*"; // 定义十进制溢出错误消息的正则表达式模式

  String WRONG_FORMAT_MESSAGE = "Number has wrong format.*"; // 定义数字格式错误消息的正则表达式模式

  // TODO: Change message // TODO注释，提示需要更改此消息
  String DIVISION_BY_ZERO_MESSAGE = "(?s).*"; // 定义除零错误消息的正则表达式模式

  // TODO: Change message // TODO注释，提示需要更改此消息
  String STRING_TRUNC_MESSAGE = "(?s).*"; // 定义字符串截断错误消息的正则表达式模式

  // TODO: Change message // TODO注释，提示需要更改此消息
  String BAD_DATETIME_MESSAGE = "(?s).*"; // 定义日期时间错误消息的正则表达式模式

  String LITERAL_OUT_OF_RANGE_MESSAGE =
      "(?s).*Numeric literal.*out of range.*"; // 定义数字字面量超出范围错误消息的正则表达式模式

  String INVALID_ARGUMENTS_NUMBER =
      "Invalid number of arguments to function .* Was expecting .* arguments"; // 定义函数参数数量无效错误消息的正则表达式模式

  String INVALID_ARGUMENTS_TYPE_VALIDATION_ERROR =
      "Cannot apply '.*' to arguments of type .*"; // 定义函数参数类型验证错误消息的正则表达式模式

  //~ Enums ------------------------------------------------------------------ // 枚举类型定义区域

  /**
   * Name of a virtual machine that can potentially implement an operator. // 可以潜在实现操作符的虚拟机名称
   */
  enum VmName { // 定义虚拟机名称枚举，表示不同的虚拟机实现
    JAVA, EXPAND // JAVA表示Java虚拟机实现，EXPAND表示展开式虚拟机实现
  }

  //~ Methods ---------------------------------------------------------------- // 方法定义区域

  /** Returns the test factory. // 返回测试工厂 */
  SqlTestFactory getFactory(); // 获取当前测试装置使用的测试工厂对象

  /** Creates a copy of this fixture with a new test factory. // 创建此测试装置的副本，使用新的测试工厂 */
  SqlOperatorFixture withFactory(UnaryOperator<SqlTestFactory> transform); // 使用转换函数修改测试工厂并返回新的测试装置

  /** Returns the tester. // 返回测试器 */
  SqlTester getTester(); // 获取当前测试装置使用的测试器对象

  /** Creates a copy of this fixture with a new tester. // 创建此测试装置的副本，使用新的测试器 */
  SqlOperatorFixture withTester(UnaryOperator<SqlTester> transform); // 使用转换函数修改测试器并返回新的测试装置

  /** Creates a copy of this fixture with a new parser configuration. // 创建此测试装置的副本，使用新的解析器配置 */
  default SqlOperatorFixture withParserConfig(
      UnaryOperator<SqlParser.Config> transform) { // 接受一个转换函数来修改解析器配置
    return withFactory(f -> f.withParserConfig(transform)); // 通过修改测试工厂的解析器配置来创建新装置
  }

  /** Returns a fixture that tests a given SQL quoting style. // 返回一个测试指定SQL引用风格的测试装置 */
  default SqlOperatorFixture withQuoting(Quoting quoting) { // 接受引用风格参数
    return withParserConfig(c -> c.withQuoting(quoting)); // 修改解析器配置的引用风格并返回新装置
  }

  /** Returns a fixture that applies a given casing policy to quoted
   * identifiers. // 返回一个对带引号标识符应用指定大小写策略的测试装置 */
  default SqlOperatorFixture withQuotedCasing(Casing casing) { // 接受大小写策略参数
    return withParserConfig(c -> c.withQuotedCasing(casing)); // 修改解析器配置的引号标识符大小写策略并返回新装置
  }

  /** Returns a fixture that applies a given casing policy to unquoted
   * identifiers. // 返回一个对不带引号标识符应用指定大小写策略的测试装置 */
  default SqlOperatorFixture withUnquotedCasing(Casing casing) { // 接受大小写策略参数
    return withParserConfig(c -> c.withUnquotedCasing(casing)); // 修改解析器配置的无引号标识符大小写策略并返回新装置
  }

  /** Returns a fixture that matches identifiers by case-sensitive or
   * case-insensitive. // 返回一个按大小写敏感或不敏感方式匹配标识符的测试装置 */
  default SqlOperatorFixture withCaseSensitive(boolean sensitive) { // 接受是否大小写敏感的布尔参数
    return withParserConfig(c -> c.withCaseSensitive(sensitive)); // 修改解析器配置的大小写敏感设置并返回新装置
  }

  /** Returns a fixture that follows a given lexical policy. // 返回一个遵循指定词法策略的测试装置 */
  default SqlOperatorFixture withLex(Lex lex) { // 接受词法策略参数
    return withParserConfig(c -> c.withLex(lex)); // 修改解析器配置的词法策略并返回新装置
  }

  /** Returns a fixture that tests conformance to a particular SQL language
   * version. // 返回一个测试特定SQL语言版本符合性的测试装置 */
  default SqlOperatorFixture withConformance(SqlConformance conformance) { // 接受SQL符合性参数
    return withParserConfig(c -> c.withConformance(conformance)) // 修改解析器配置的符合性设置
        .withValidatorConfig(c -> c.withConformance(conformance)) // 修改验证器配置的符合性设置
        .withConnectionFactory(cf -> cf.with("conformance", conformance)); // 修改连接工厂的符合性设置
  }

  /** Returns the conformance. // 返回当前的SQL符合性设置 */
  default SqlConformance conformance() { // 无参方法
    return getFactory().parserConfig().conformance(); // 从测试工厂的解析器配置中获取符合性设置
  }

  /** Returns a fixture with a given validator configuration. // 返回一个使用指定验证器配置的测试装置 */
  default SqlOperatorFixture withValidatorConfig(
      UnaryOperator<SqlValidator.Config> transform) { // 接受验证器配置转换函数
    return withFactory(f -> f.withValidatorConfig(transform)); // 通过修改测试工厂的验证器配置来创建新装置
  }

  /** Returns a fixture that tests with implicit type coercion on/off. // 返回一个启用或禁用隐式类型转换的测试装置 */
  default SqlOperatorFixture enableTypeCoercion(boolean enabled) { // 接受是否启用类型转换的布尔参数
    return withValidatorConfig(c -> c.withTypeCoercionEnabled(enabled)); // 修改验证器配置的类型转换设置并返回新装置
  }

  /** Returns a fixture that does not fail validation if it encounters an
   * unknown function. // 返回一个遇到未知函数时不使验证失败的测试装置 */
  default SqlOperatorFixture withLenientOperatorLookup(boolean lenient) { // 接受是否使用宽松操作符查找的布尔参数
    return withValidatorConfig(c -> c.withLenientOperatorLookup(lenient)); // 修改验证器配置的操作符查找策略并返回新装置
  }

  /** Returns a fixture that gets connections from a given factory. // 返回一个从指定工厂获取连接的测试装置 */
  default SqlOperatorFixture withConnectionFactory(
      UnaryOperator<ConnectionFactory> transform) { // 接受连接工厂转换函数
    return withFactory(f -> f.withConnectionFactory(transform)); // 通过修改测试工厂的连接工厂来创建新装置
  }

  /** Returns a fixture that uses a given operator table. // 返回一个使用指定操作符表的测试装置 */
  default SqlOperatorFixture withOperatorTable(
      SqlOperatorTable operatorTable) { // 接受操作符表参数
    return withFactory(f -> f.withOperatorTable(o -> operatorTable)); // 通过修改测试工厂的操作符表来创建新装置
  }

  /** Returns whether to run tests that are considered 'broken'.
   * Returns false by default, but it is useful to temporarily enable the
   * 'broken' tests to see whether they are still broken. // 返回是否运行被认为是"损坏"的测试。默认返回false，但临时启用"损坏"测试以查看它们是否仍然损坏很有用 */
  boolean brokenTestsEnabled(); // 获取是否启用损坏测试的标志

  /** Sets {@link #brokenTestsEnabled()}. // 设置是否启用损坏测试 */
  SqlOperatorFixture withBrokenTestsEnabled(boolean enableBrokenTests); // 接受是否启用损坏测试的布尔参数并返回新装置

  void checkScalar(String expression, // 检查标量SQL表达式的方法，接受表达式字符串
      TypeChecker typeChecker, // 类型检查器，用于验证结果类型
      ResultChecker resultChecker); // 结果检查器，用于验证结果值

  /**
   * Tests that a scalar SQL expression returns the expected result and the
   * expected type. For example, // 测试标量SQL表达式是否返回预期结果和预期类型。例如：
   *
   * <blockquote>
   * <pre>checkScalar("1.1 + 2.9", "4.0", "DECIMAL(2, 1) NOT NULL");</pre>
   * </blockquote>
   *
   * @param expression Scalar expression // 标量表达式
   * @param result     Expected result // 预期结果
   * @param resultType Expected result type // 预期结果类型
   */
  default void checkScalar(
      String expression, // SQL表达式字符串
      Object result, // 预期的结果对象
      String resultType) { // 预期的结果类型字符串
    checkType(expression, resultType); // 首先检查表达式类型是否匹配
    checkScalar(expression, SqlTests.ANY_TYPE_CHECKER, // 然后检查标量表达式的结果值
        ResultCheckers.createChecker(result)); // 使用结果检查器验证结果值
  }

  /**
   * Tests that a scalar SQL expression returns the expected exact numeric
   * result as an integer. For example, // 测试标量SQL表达式是否返回预期的精确数值结果作为整数。例如：
   *
   * <blockquote>
   * <pre>checkScalarExact("1 + 2", 3);</pre>
   * </blockquote>
   *
   * @param expression Scalar expression // 标量表达式
   * @param result     Expected result // 预期结果
   */
  default void checkScalarExact(String expression, int result) { // 接受表达式和整数结果
    checkScalar(expression, SqlTests.INTEGER_TYPE_CHECKER, isSingle(result)); // 使用整数类型检查器和单个结果检查器验证
  }

  /**
   * Tests that a scalar SQL expression returns the expected exact numeric
   * result. For example, // 测试标量SQL表达式是否返回预期的精确数值结果。例如：
   *
   * <blockquote>
   * <pre>checkScalarExact("1 + 2", "3");</pre>
   * </blockquote>
   *
   * @param expression   Scalar expression // 标量表达式
   * @param expectedType Type we expect the result to have, including
   *                     nullability, precision and scale, for example
   *                     <code>DECIMAL(2, 1) NOT NULL</code>. // 预期结果类型，包括可空性、精度和小数位数，例如DECIMAL(2, 1) NOT NULL
   * @param result       Expected result // 预期结果
   */
  default void checkScalarExact(
      String expression, // SQL表达式字符串
      String expectedType, // 预期类型字符串
      String result) { // 预期结果字符串
    checkScalarExact(expression, expectedType, isSingle(result)); // 调用重载方法，将结果转换为单个结果检查器
  }

  void checkScalarExact( // 检查精确标量表达式的方法
      String expression, // SQL表达式字符串
      String expectedType, // 预期类型字符串
      ResultChecker resultChecker); // 结果检查器，用于验证结果值

  /**
   * Tests that a scalar SQL expression returns expected approximate numeric
   * result. For example, // 测试标量SQL表达式是否返回预期的近似数值结果。例如：
   *
   * <blockquote>
   * <pre>checkScalarApprox("1.0 + 2.1", "3.1");</pre>
   * </blockquote>
   *
   * @param expression     Scalar expression // 标量表达式
   * @param expectedType   Type we expect the result to have, including
   *                       nullability, precision and scale, for example
   *                       <code>DECIMAL(2, 1) NOT NULL</code>. // 预期结果类型，包括可空性、精度和小数位数
   * @param result         Expected result, or a matcher // 预期结果或匹配器
   *
   * @see Matchers#within(Number, double) // 参见Matchers.within方法用于近似匹配
   */
  void checkScalarApprox( // 检查近似标量表达式的方法
      String expression, // SQL表达式字符串
      String expectedType, // 预期类型字符串
      Object result); // 预期结果对象或匹配器

  /**
   * Tests that a scalar SQL expression returns the expected boolean result.
   * For example, // 测试标量SQL表达式是否返回预期的布尔结果。例如：
   *
   * <blockquote>
   * <pre>checkScalarExact("TRUE AND FALSE", Boolean.TRUE);</pre>
   * </blockquote>
   *
   * <p>The expected result can be null: // 预期结果可以为null：
   *
   * <blockquote>
   * <pre>checkScalarExact("NOT UNKNOWN", null);</pre>
   * </blockquote>
   *
   * @param expression Scalar expression // 标量表达式
   * @param result     Expected result (null signifies NULL). // 预期结果（null表示SQL的NULL值）
   */
  void checkBoolean( // 检查布尔表达式的方法
      String expression, // SQL表达式字符串
      @Nullable Boolean result); // 预期的布尔结果，可能为null

  /**
   * Tests that a scalar SQL expression returns the expected string result.
   * For example, // 测试标量SQL表达式是否返回预期的字符串结果。例如：
   *
   * <blockquote>
   * <pre>checkScalarExact("'ab' || 'c'", "abc");</pre>
   * </blockquote>
   *
   * @param expression Scalar expression // 标量表达式
   * @param result     Expected result // 预期结果
   * @param resultType Expected result type // 预期结果类型
   */
  void checkString( // 检查字符串表达式的方法
      String expression, // SQL表达式字符串
      String result, // 预期的字符串结果
      String resultType); // 预期的结果类型字符串

  /**
   * Tests that a SQL expression returns the SQL NULL value. For example, // 测试SQL表达式是否返回SQL NULL值。例如：
   *
   * <blockquote>
   * <pre>checkNull("CHAR_LENGTH(CAST(NULL AS VARCHAR(3))");</pre>
   * </blockquote>
   *
   * @param expression Scalar expression // 标量表达式
   */
  void checkNull(String expression); // 检查表达式是否返回NULL的方法，接受表达式字符串

  /**
   * Tests that a SQL expression has a given type. For example, // 测试SQL表达式是否具有特定类型。例如：
   *
   * <blockquote>
   * <code>checkType("SUBSTR('hello' FROM 1 FOR 3)",
   * "VARCHAR(3) NOT NULL");</code>
   * </blockquote>
   *
   * <p>This method checks length/precision, scale, and whether the type allows
   * NULL values, so is more precise than the type-checking done by methods
   * such as {@link #checkScalarExact}. // 此方法检查长度/精度、小数位数以及类型是否允许NULL值，因此比checkScalarExact等方法进行的类型检查更精确
   *
   * @param expression Scalar expression // 标量表达式
   * @param type       Type string // 类型字符串
   */
  void checkType( // 检查表达式类型的方法
      String expression, // SQL表达式字符串
      String type); // 预期的类型字符串

  /** Very similar to {@link #checkType}, but generates inside a SELECT
   * with a non-empty GROUP BY. Aggregate functions may be nullable if executed
   * in a SELECT with an empty GROUP BY. // 与checkType非常相似，但在带有非空GROUP BY的SELECT中生成。如果在带有空GROUP BY的SELECT中执行，聚合函数可能可为空
   *
   * <p>Viz: {@code SELECT sum(1) FROM emp} has type "INTEGER",
   * {@code SELECT sum(1) FROM emp GROUP BY deptno} has type "INTEGER NOT NULL", // 例如：SELECT sum(1) FROM emp的类型为"INTEGER"，SELECT sum(1) FROM emp GROUP BY deptno的类型为"INTEGER NOT NULL"
   */
  default SqlOperatorFixture checkAggType(String expr, String type) { // 检查聚合表达式类型的方法
    checkColumnType(AbstractSqlTester.buildQueryAgg(expr), type); // 构建聚合查询并检查列类型
    return this; // 返回当前测试装置以支持链式调用
  }

  /**
   * Checks that a query returns one column of an expected type. For example,
   * <code>checkType("VALUES (1 + 2)", "INTEGER NOT NULL")</code>. // 检查查询是否返回预期类型的一列。例如：checkType("VALUES (1 + 2)", "INTEGER NOT NULL")
   *
   * @param sql  Query expression // 查询表达式
   * @param type Type string // 类型字符串
   */
  void checkColumnType( // 检查查询列类型的方法
      String sql, // SQL查询字符串
      String type); // 预期的类型字符串

  /**
   * Tests that a SQL query returns a single column with the given type. For
   * example, // 测试SQL查询是否返回给定类型的单列。例如：
   *
   * <blockquote>
   * <pre>check("VALUES (1 + 2)", "3", SqlTypeName.Integer);</pre>
   * </blockquote>
   *
   * <p>If <code>result</code> is null, the expression must yield the SQL NULL
   * value. If <code>result</code> is a {@link java.util.regex.Pattern}, the
   * result must match that pattern. // 如果result为null，表达式必须产生SQL NULL值。如果result是正则表达式模式，结果必须匹配该模式
   *
   * @param query       SQL query // SQL查询
   * @param typeChecker Checks whether the result is the expected type; must
   *                    not be null // 检查结果是否为预期类型的类型检查器，不能为null
   * @param result      Expected result, or matcher // 预期结果或匹配器
   */
  default void check(String query, // 检查查询的方法，接受查询字符串
      TypeChecker typeChecker, // 类型检查器
      Object result) { // 预期结果对象
    check(query, typeChecker, SqlTests.ANY_PARAMETER_CHECKER, // 调用完整版本的check方法
        ResultCheckers.createChecker(result)); // 使用任意参数检查器和结果检查器
  }

  default void check(String query, String expectedType, Object result) { // 检查查询的重载方法，接受类型字符串
    check(query, new SqlTests.StringTypeChecker(expectedType), result); // 将类型字符串转换为类型检查器并调用完整版本
  }

  /**
   * Tests that a SQL query returns a result of expected type and value.
   * Checking of type and value are abstracted using {@link TypeChecker}
   * and {@link ResultChecker} functors. // 测试SQL查询是否返回预期类型和值的结果。使用TypeChecker和ResultChecker函数对象来抽象类型和值的检查
   *
   * @param query         SQL query // SQL查询
   * @param typeChecker   Checks whether the result is the expected type // 检查结果是否为预期类型
   * @param parameterChecker Checks whether the parameters are of expected
   *                      types // 检查参数是否为预期类型
   * @param resultChecker Checks whether the result has the expected value // 检查结果是否具有预期值
   */
  default void check(String query, // 完整版本的check方法
      SqlTester.TypeChecker typeChecker, // 类型检查器
      SqlTester.ParameterChecker parameterChecker, // 参数检查器
      ResultChecker resultChecker) { // 结果检查器
    getTester() // 获取测试器
        .check(getFactory(), query, typeChecker, parameterChecker, // 调用测试器的check方法
            resultChecker); // 传入所有检查器进行验证
  }

  /**
   * Declares that this test is for a given operator. So we can check that all
   * operators are tested. // 声明此测试是针对给定操作符的。这样我们可以检查所有操作符是否都经过测试
   *
   * @param operator             Operator // 操作符
   * @param unimplementedVmNames Names of virtual machines for which this // 未实现此操作符的虚拟机名称
   */
  SqlOperatorFixture setFor( // 设置测试针对的操作符
      SqlOperator operator, // 操作符对象
      VmName... unimplementedVmNames); // 未实现的虚拟机名称数组

  /**
   * Checks that an aggregate expression returns the expected result. // 检查聚合表达式是否返回预期结果
   *
   * <p>For example, <code>checkAgg("AVG(DISTINCT x)", new String[] {"2", "3",
   * null, "3" }, new Double(2.5), 0);</code> // 例如：checkAgg("AVG(DISTINCT x)", new String[] {"2", "3", null, "3" }, new Double(2.5), 0)
   *
   * @param expr        Aggregate expression, e.g. <code>SUM(DISTINCT x)</code> // 聚合表达式，例如SUM(DISTINCT x)
   * @param inputValues Array of input values, e.g. <code>["1", null,
   *                    "2"]</code>. // 输入值数组，例如["1", null, "2"]
   * @param checker     Result checker // 结果检查器
   */
  void checkAgg( // 检查聚合表达式的方法
      String expr, // 聚合表达式字符串
      String[] inputValues, // 输入值数组
      ResultChecker checker); // 结果检查器

  /**
   * Checks that an aggregate expression returns the expected result. // 检查聚合表达式是否返回预期结果
   *
   * <p>For example, <code>checkAgg("AVG(DISTINCT x)", new String[] {"2", "3",
   * null, "3" }, "INTEGER", isSingle([2, 3]));</code> // 例如：checkAgg("AVG(DISTINCT x)", new String[] {"2", "3", null, "3" }, "INTEGER", isSingle([2, 3]))
   *
   * @param expr        Aggregate expression, e.g. <code>SUM(DISTINCT x)</code> // 聚合表达式，例如SUM(DISTINCT x)
   * @param inputValues Array of input values, e.g. <code>["1", null,
   *                    "2"]</code>. // 输入值数组，例如["1", null, "2"]
   * @param type        Expected result type // 预期结果类型
   * @param checker     Result checker // 结果检查器
   */
  void checkAgg( // 检查聚合表达式的重载方法，包含类型检查
      String expr, // 聚合表达式字符串
      String[] inputValues, // 输入值数组
      String type, // 预期类型字符串
      ResultChecker checker); // 结果检查器

  /**
   * Checks that an aggregate expression with multiple args returns the expected
   * result. // 检查带多个参数的聚合表达式是否返回预期结果
   *
   * @param expr        Aggregate expression, e.g. <code>AGG_FUNC(x, x2, x3)</code> // 聚合表达式，例如AGG_FUNC(x, x2, x3)
   * @param inputValues Nested array of input values, e.g. <code>[
   *                    ["1", null, "2"]
   *                    ["3", "4", null]
   *                    ]</code> // 嵌套的输入值数组，例如[["1", null, "2"], ["3", "4", null]]
   * @param resultChecker Checks whether the result has the expected value // 检查结果是否具有预期值的结果检查器
   */
  void checkAggWithMultipleArgs( // 检查多参数聚合表达式的方法
      String expr, // 聚合表达式字符串
      String[][] inputValues, // 二维输入值数组
      ResultChecker resultChecker); // 结果检查器

  /**
   * Checks that a windowed aggregate expression returns the expected result. // 检查窗口聚合表达式是否返回预期结果
   *
   * <p>For example, <code>checkWinAgg("FIRST_VALUE(x)", new String[] {"2",
   * "3", null, "3" }, "INTEGER NOT NULL", 2, 0d);</code> // 例如：checkWinAgg("FIRST_VALUE(x)", new String[] {"2", "3", null, "3" }, "INTEGER NOT NULL", 2, 0d)
   *
   * @param expr          Aggregate expression, e.g. {@code SUM(DISTINCT x)} // 聚合表达式，例如SUM(DISTINCT x)
   * @param inputValues   Array of input values, e.g. {@code ["1", null, "2"]} // 输入值数组，例如["1", null, "2"]
   * @param type          Expected result type // 预期结果类型
   * @param resultChecker Checks whether the result has the expected value // 检查结果是否具有预期值的结果检查器
   */
  void checkWinAgg( // 检查窗口聚合表达式的方法
      String expr, // 聚合表达式字符串
      String[] inputValues, // 输入值数组
      String windowSpec, // 窗口规范字符串
      String type, // 预期类型字符串
      ResultChecker resultChecker); // 结果检查器

  /**
   * Tests that an aggregate expression fails at run time. // 测试聚合表达式在运行时是否失败
   *
   * @param expr An aggregate expression // 聚合表达式
   * @param inputValues Array of input values // 输入值数组
   * @param expectedError Pattern for expected error // 预期错误的模式
   * @param runtime       If true, must fail at runtime; if false, must fail at
   *                      validate time // 如果为true，必须在运行时失败；如果为false，必须在验证时失败
   */
  void checkAggFails( // 检查聚合表达式失败的方法
      String expr, // 聚合表达式字符串
      String[] inputValues, // 输入值数组
      String expectedError, // 预期错误字符串
      boolean runtime); // 是否为运行时失败的标志

  /**
   * Tests that a scalar SQL expression fails at run time. // 测试标量SQL表达式在运行时是否失败
   *
   * @param expression    SQL scalar expression // SQL标量表达式
   * @param expectedError Pattern for expected error. If !runtime, must
   *                      include an error location. // 预期错误的模式。如果不是运行时，必须包含错误位置
   * @param runtime       If true, must fail at runtime; if false, must fail at
   *                      validate time // 如果为true，必须在运行时失败；如果为false，必须在验证时失败
   */
  void checkFails( // 检查表达式失败的方法
      StringAndPos expression, // 带位置信息的表达式对象
      String expectedError, // 预期错误字符串
      boolean runtime); // 是否为运行时失败的标志

  /** As {@link #checkFails(StringAndPos, String, boolean)}, but with a string
   * that contains carets. // 与checkFails(StringAndPos, String, boolean)相同，但使用包含脱字符的字符串 */
  default void checkFails( // 检查表达式失败的重载方法，接受字符串
      String expression, // 表达式字符串
      String expectedError, // 预期错误字符串
      boolean runtime) { // 是否为运行时失败的标志
    checkFails(StringAndPos.of(expression), expectedError, runtime); // 将字符串转换为StringAndPos对象并调用完整版本
  }

  /**
   * Tests that a SQL query fails at prepare time. // 测试SQL查询在准备时是否失败
   *
   * @param sap           SQL query and error position // SQL查询和错误位置
   * @param expectedError Pattern for expected error. Must
   *                      include an error location. // 预期错误的模式。必须包含错误位置
   */
  void checkQueryFails(StringAndPos sap, String expectedError); // 检查查询失败的方法，接受带位置信息的查询字符串和预期错误

  /**
   * Tests that a SQL query succeeds at prepare time. // 测试SQL查询在准备时是否成功
   *
   * @param sql           SQL query // SQL查询
   */
  void checkQuery(String sql); // 检查查询成功的方法，接受SQL查询字符串

  default SqlOperatorFixture withLibrary(SqlLibrary library) { // 使用指定SQL库的测试装置
    return withOperatorTable( // 设置操作符表
        SqlLibraryOperatorTableFactory.INSTANCE // 获取操作符表工厂实例
            .getOperatorTable(SqlLibrary.STANDARD, library)) // 获取标准库和指定库的操作符表
        .withConnectionFactory(cf -> // 设置连接工厂
            cf.with(ConnectionFactories.add(CalciteAssert.SchemaSpec.HR)) // 添加HR架构规范
                .with(CalciteConnectionProperty.FUN, library.fun)); // 设置函数库属性
  }

  default SqlOperatorFixture withLibraries(SqlLibrary... libraries) { // 使用多个SQL库的测试装置
    List<String> names = new ArrayList<>(); // 创建库名称列表
    for (SqlLibrary lib : libraries) { // 遍历所有库
      names.add(lib.fun); // 添加每个库的函数名称
    }
    return withOperatorTable( // 设置操作符表
        SqlLibraryOperatorTableFactory.INSTANCE // 获取操作符表工厂实例
            .getOperatorTable(libraries)) // 获取所有库的操作符表
        .withConnectionFactory(cf -> // 设置连接工厂
            cf.with(ConnectionFactories.add(CalciteAssert.SchemaSpec.HR)) // 添加HR架构规范
                .with(CalciteConnectionProperty.FUN, String.join(",", names))); // 设置多个函数库属性，用逗号分隔
  }

  /** Applies this fixture to some code for each of the given libraries. // 对每个给定的库将此测试装置应用于某些代码 */
  default void forEachLibrary(Iterable<? extends SqlLibrary> libraries, // 遍历库的方法
      Consumer<SqlOperatorFixture> consumer) { // 接受库的迭代和消费者函数
    SqlLibrary.expand(libraries).forEach(library -> { // 展开库并遍历每个库
      try {
        consumer.accept(this.withLibrary(library)); // 对每个库调用消费者函数
      } catch (Exception e) { // 捕获异常
        throw new RuntimeException("for library " + library, e); // 抛出运行时异常，包含库信息
      }
    });
  }

  /** Applies this fixture to some code for each of the given conformances. // 对每个给定的符合性级别将此测试装置应用于某些代码 */
  default void forEachConformance(Iterable<? extends SqlConformanceEnum> conformances, // 遍历符合性级别的方法
      Consumer<SqlOperatorFixture> consumer) { // 接受符合性级别的迭代和消费者函数
    conformances.forEach(conformance -> { // 遍历每个符合性级别
      try {
        consumer.accept(this.withConformance(conformance)); // 对每个符合性级别调用消费者函数
      } catch (Exception e) { // 捕获异常
        throw new RuntimeException("for conformance " + conformance, e); // 抛出运行时异常，包含符合性信息
      }
    });
  }

  default SqlOperatorFixture forOracle(SqlConformance conformance) { // 为Oracle特定配置的测试装置
    return withConformance(conformance) // 设置符合性级别
        .withOperatorTable( // 设置操作符表
            SqlLibraryOperatorTableFactory.INSTANCE // 获取操作符表工厂实例
                .getOperatorTable(SqlLibrary.STANDARD, SqlLibrary.ORACLE)) // 获取标准和Oracle库的操作符表
        .withConnectionFactory(cf -> // 设置连接工厂
            cf.with(ConnectionFactories.add(CalciteAssert.SchemaSpec.HR)) // 添加HR架构规范
                .with("fun", "oracle")); // 设置Oracle函数库
  }

  /**
   * Types for cast. // 类型转换的类型
   */
  enum CastType { // 定义类型转换类型的枚举
    CAST("cast"), // 普通类型转换
    SAFE_CAST("safe_cast"), // 安全类型转换
    TRY_CAST("try_cast"); // 尝试类型转换

    CastType(String name) { // 构造函数，接受类型名称
      this.name = name; // 设置类型名称
    }

    final String name; // 类型名称字段
  }

  default String getCastString( // 获取类型转换字符串的方法
      String value, // 要转换的值
      String targetType, // 目标类型
      boolean errorLoc, // 是否标记错误位置
      CastType castType) { // 转换类型
    if (errorLoc) { // 如果需要标记错误位置
      value = "^" + value + "^"; // 在值前后添加脱字符标记错误位置
    }
    String function = castType.name; // 获取转换函数名称
    return function + "(" + value + " as " + targetType + ")"; // 构建类型转换表达式字符串
  }

  default void checkCastToApproxOkay(String value, String targetType, // 检查转换为近似数值类型成功的方法
      Object expected, CastType castType) { // 接受值、目标类型、预期值和转换类型
    checkScalarApprox(getCastString(value, targetType, false, castType), // 使用近似标量检查器验证转换表达式
        getTargetType(targetType, castType), expected); // 获取目标类型并验证预期值
  }

  default void checkCastToStringOkay(String value, String targetType, // 检查转换为字符串类型成功的方法
      String expected, CastType castType) { // 接受值、目标类型、预期字符串和转换类型
    final String castString = getCastString(value, targetType, false, castType); // 获取转换表达式字符串
    checkString(castString, expected, getTargetType(targetType, castType)); // 使用字符串检查器验证转换结果
  }

  default void checkCastToScalarOkay(String value, String targetType, // 检查转换为标量类型成功的方法
      String expected, CastType castType) { // 接受值、目标类型、预期字符串和转换类型
    final String castString = getCastString(value, targetType, false, castType); // 获取转换表达式字符串
    checkScalarExact(castString, getTargetType(targetType, castType), expected); // 使用精确标量检查器验证转换结果
  }

  default String getTargetType(String targetType, CastType castType) { // 获取目标类型字符串的方法
    return castType == CastType.CAST ? targetType + NON_NULLABLE_SUFFIX : targetType; // 如果是普通转换，添加非空后缀；否则返回原类型
  }

  default void checkCastToScalarOkay(String value, String targetType, // 检查转换为标量类型成功的重载方法，预期值为原值
      CastType castType) { // 接受值、目标类型和转换类型
    checkCastToScalarOkay(value, targetType, value, castType); // 调用完整版本，预期值设为原值
  }

  default void checkCastFails(String value, String targetType, // 检查类型转换失败的方法
      String expectedError, boolean runtime, CastType castType) { // 接受值、目标类型、预期错误、是否运行时失败和转换类型
    // Safe casts should never fail // 安全转换不应该失败
    boolean shouldFail = castType == CastType.CAST; // 只有普通转换才应该失败
    final String castString = getCastString(value, targetType, shouldFail && !runtime, castType); // 获取转换表达式字符串
    if (shouldFail) { // 如果应该失败
      checkFails(castString, expectedError, runtime); // 检查表达式是否按预期失败
    } else { // 如果不应该失败
      checkNull(castString); // 检查表达式是否返回NULL
    }
  }

  default void checkCastToString(String value, @Nullable String type, // 检查转换为字符串的方法
      @Nullable String expected, CastType castType) { // 接受值、类型、预期字符串和转换类型
    String spaces = "     "; // 定义5个空格用于CHAR类型填充
    if (expected == null) { // 如果没有提供预期值
      expected = value.trim(); // 使用值的修剪版本作为预期值
    }
    int len = expected.length(); // 获取预期字符串的长度
    if (type != null) { // 如果提供了类型
      value = getCastString(value, type, false, castType); // 获取类型转换表达式字符串
    }

    // currently no exception thrown for truncation // 当前截断时不抛出异常
    if (Bug.DT239_FIXED) { // 如果bug DT239已修复
      checkCastFails(value, // 检查转换为更短VARCHAR是否失败
          "VARCHAR(" + (len - 1) + ")", STRING_TRUNC_MESSAGE, // 使用截断消息
          true, castType); // 运行时失败
    }

    checkCastToStringOkay(value, "VARCHAR(" + len + ")", expected, castType); // 检查转换为精确长度VARCHAR
    checkCastToStringOkay(value, "VARCHAR(" + (len + 5) + ")", expected, castType); // 检查转换为更长VARCHAR

    // currently no exception thrown for truncation // 当前截断时不抛出异常
    if (Bug.DT239_FIXED) { // 如果bug DT239已修复
      checkCastFails(value, // 检查转换为更短CHAR是否失败
          "CHAR(" + (len - 1) + ")", STRING_TRUNC_MESSAGE, // 使用截断消息
          true, castType); // 运行时失败
    }

    checkCastToStringOkay(value, "CHAR(" + len + ")", expected, castType); // 检查转换为精确长度CHAR
    checkCastToStringOkay(value, "CHAR(" + (len + 5) + ")", // 检查转换为更长CHAR
        expected + spaces, castType); // CHAR类型会用空格填充
  }
}
