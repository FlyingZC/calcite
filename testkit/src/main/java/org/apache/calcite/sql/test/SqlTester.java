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
// Apache许可证头部声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.sql.test; // 定义包名，该类位于org.apache.calcite.sql.test包下

import org.apache.calcite.rel.RelNode; // 导入关系代数节点类，代表查询计划中的单个操作符
import org.apache.calcite.rel.RelRoot; // 导入关系表达式根节点类，封装了完整的关系表达式树
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，描述关系表或表达式的数据类型
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，代表SQL语法树中的抽象节点
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符类，代表SQL中的函数、运算符等
import org.apache.calcite.sql.parser.SqlParseException; // 导入SQL解析异常类，处理SQL解析过程中的错误
import org.apache.calcite.sql.parser.StringAndPos; // 导入字符串和位置类，封装SQL字符串及其错误位置信息
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口，负责验证SQL语句的语义正确性
import org.apache.calcite.test.DiffRepository; // 导入差异仓库类，用于存储和对比测试结果
import org.apache.calcite.util.Pair; // 导入键值对工具类，用于存储两个相关联的对象

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可能为null的参数或返回值

import java.sql.ResultSet; // 导入JDBC结果集接口，代表SQL查询的结果数据
import java.util.function.Consumer; // 导入消费者函数式接口，接受单个参数并执行操作
import java.util.function.Supplier; // 导入供应者函数式接口，不接受参数但返回结果

/**
 * Callback for testing SQL queries and expressions.
 * 用于测试SQL查询和表达式的回调接口
 *
 * <p>The idea is that when you define an operator (or another piece of SQL
 * functionality), you can define the logical behavior of that operator once, as
 * part of that operator. Later you can define one or more physical
 * implementations of that operator, and test them all using the same set of
 * tests.
 * 核心设计思想：当定义一个操作符（或其他SQL功能）时，可以在该操作符中一次性定义其逻辑行为。
 * 之后可以定义该操作符的一个或多个物理实现，并使用同一组测试用例来测试所有实现。
 * 这种设计实现了逻辑行为与物理实现的分离，确保不同实现具有相同的语义。
 *
 * <p>Specific implementations of <code>SqlTester</code> might evaluate the
 * queries in different ways, for example, using a C++ versus Java calculator.
 * An implementation might even ignore certain calls altogether.
 * SqlTester的具体实现可能以不同方式评估查询，例如使用C++计算器与Java计算器。
 * 某些实现甚至可能完全忽略某些调用，提供灵活的测试策略。
 */
public interface SqlTester extends AutoCloseable { // 定义SqlTester接口，继承AutoCloseable接口以支持资源自动清理
  //~ Enums ------------------------------------------------------------------
  // 枚举类型区域分隔符，表示以下是枚举定义

  /**
   * Name of a virtual machine that can potentially implement an operator.
   * 虚拟机名称枚举，表示可以潜在实现操作符的虚拟机
   */
  enum VmName { // 定义虚拟机名称枚举
    JAVA, // Java虚拟机，表示使用Java实现的操作符
    EXPAND // 扩展虚拟机，表示使用扩展实现的操作符
  } // 枚举定义结束

  //~ Methods ----------------------------------------------------------------
  // 方法区域分隔符，表示以下是方法定义

  /** Given a scalar expression, generates a sequence of SQL queries that
   * evaluate it, and calls a given action with each.
   * 给定一个标量表达式，生成一系列评估该表达式的SQL查询，并对每个查询调用指定的操作
   *
   * @param factory    Factory - SQL测试工厂，用于创建测试所需的上下文和配置
   * @param expression Scalar expression - 标量表达式字符串，如 "1 + 2"
   * @param consumer   Action to be called for each query - 对每个生成的查询执行的操作
   */
  void forEachQuery(SqlTestFactory factory, String expression, // 方法参数：测试工厂
      Consumer<String> consumer); // 方法参数：查询消费者接口

  /** Parses a query.
   * 解析SQL查询语句，将其转换为SqlNode语法树
   */
  SqlNode parseQuery(SqlTestFactory factory, String sql) // 返回解析后的SQL节点
      throws SqlParseException; // 可能抛出SQL解析异常

  /** Parses an expression.
   * 解析SQL表达式，将其转换为SqlNode语法树
   */
  SqlNode parseExpression(SqlTestFactory factory, String expr) // 返回解析后的表达式节点
      throws SqlParseException; // 可能抛出SQL解析异常

  /** Parses and validates a query, then calls an action on the result.
   * 解析并验证查询，然后对验证结果执行指定操作
   */
  void validateAndThen(SqlTestFactory factory, StringAndPos sap, // 参数：测试工厂和带位置的SQL字符串
      ValidatedNodeConsumer consumer); // 参数：验证节点消费者，处理验证后的SQL节点

  /** Parses and validates a query, then calls a function on the result.
   * 解析并验证查询，然后对验证结果应用指定函数并返回结果
   */
  <R> R validateAndApply(SqlTestFactory factory, StringAndPos sap, // 参数：测试工厂和带位置的SQL字符串
      ValidatedNodeFunction<R> function); // 参数：验证节点函数，处理验证后的SQL节点并返回结果R

  /**
   * Checks that a query is valid, or, if invalid, throws the right
   * message at the right location.
   * 检查查询是否有效，或者如果无效，则在正确的位置抛出正确的错误消息
   *
   * <p>If <code>expectedMsgPattern</code> is null, the query must
   * succeed.
   * 如果expectedMsgPattern为null，查询必须成功执行
   *
   * <p>If <code>expectedMsgPattern</code> is not null, the query must
   * fail, and give an error location of (expectedLine, expectedColumn)
   * through (expectedEndLine, expectedEndColumn).
   * 如果expectedMsgPattern不为null，查询必须失败，并在(expectedLine, expectedColumn)
   * 到(expectedEndLine, expectedEndColumn)的位置给出错误信息
   *
   * @param factory            Factory - SQL测试工厂
   * @param sap                SQL statement - SQL语句及其位置信息
   * @param expectedMsgPattern If this parameter is null the query must be - 如果此参数为null，查询必须成功
   */
  void assertExceptionIsThrown(SqlTestFactory factory, StringAndPos sap, // 参数：测试工厂和SQL语句
      @Nullable String expectedMsgPattern); // 参数：期望的错误消息模式，可能为null

  /**
   * Returns the data type of the sole column of a SQL query.
   * 返回SQL查询中唯一列的数据类型
   *
   * <p>For example, <code>getResultType("VALUES (1")</code> returns
   * <code>INTEGER</code>.
   * 例如，getResultType("VALUES (1)")返回INTEGER类型
   *
   * <p>Fails if query returns more than one column.
   * 如果查询返回多列则失败
   *
   * @see #getResultType(SqlTestFactory, String)
   */
  RelDataType getColumnType(SqlTestFactory factory, String sql); // 返回单列的关系数据类型

  /**
   * Returns the data type of the row returned by a SQL query.
   * 返回SQL查询返回的行的数据类型
   *
   * <p>For example, <code>getResultType("VALUES (1, 'foo')")</code>
   * returns <code>RecordType(INTEGER EXPR$0, CHAR(3) EXPR#1)</code>.
   * 例如，getResultType("VALUES (1, 'foo')")返回RecordType(INTEGER EXPR$0, CHAR(3) EXPR#1)
   */
  RelDataType getResultType(SqlTestFactory factory, String sql); // 返回整行的关系数据类型

  /**
   * Checks that a query returns one column of an expected type. For example,
   * <code>checkType("VALUES (1 + 2)", "INTEGER NOT NULL")</code>.
   * 检查查询返回的列是否为期望的类型。例如，checkType("VALUES (1 + 2)", "INTEGER NOT NULL")
   *
   * @param factory    Factory - SQL测试工厂
   * @param sql        Query expression - 查询表达式
   * @param type       Type string - 类型字符串，如"INTEGER NOT NULL"
   */
  void checkColumnType(SqlTestFactory factory, // 参数：测试工厂
      String sql, // 参数：SQL查询表达式
      String type); // 参数：期望的类型字符串

  /**
   * Tests that a SQL query returns a single column with the given type. For
   * example,
   * 测试SQL查询返回的列是否为给定类型。例如：
   *
   * <blockquote>
   * <pre>check("VALUES (1 + 2)", "3", SqlTypeName.Integer);</pre>
   * </blockquote>
   *
   * <p>If <code>result</code> is null, the expression must yield the SQL NULL
   * value. If <code>result</code> is a {@link java.util.regex.Pattern}, the
   * result must match that pattern.
   * 如果result为null，表达式必须产生SQL NULL值。如果result是Pattern对象，结果必须匹配该模式
   *
   * @param factory       Factory - SQL测试工厂
   * @param query         SQL query - SQL查询
   * @param typeChecker   Checks whether the result is the expected type - 检查结果是否为期望类型
   * @param resultChecker Checks whether the result has the expected value - 检查结果是否为期望值
   */
  default void check(SqlTestFactory factory, // 默认方法实现，使用ANY_PARAMETER_CHECKER
      String query, // 参数：SQL查询字符串
      TypeChecker typeChecker, // 参数：类型检查器
      ResultChecker resultChecker) { // 参数：结果检查器
    check(factory, query, typeChecker, SqlTests.ANY_PARAMETER_CHECKER, // 调用完整的check方法，参数检查器设为ANY
        resultChecker); // 传递结果检查器
  } // 默认方法结束

  /**
   * Tests that a SQL query returns a result of expected type and value.
   * Checking of type and value are abstracted using {@link TypeChecker}
   * and {@link ResultChecker} functors.
   * 测试SQL查询返回的结果是否具有期望的类型和值。类型和值的检查通过TypeChecker和ResultChecker函数式接口抽象
   *
   * @param factory       Factory - SQL测试工厂
   * @param query         SQL query - SQL查询
   * @param typeChecker   Checks whether the result is the expected type - 检查结果是否为期望类型
   * @param parameterChecker Checks whether the parameters are of expected
   *                      types - 检查参数是否为期望类型
   * @param resultChecker Checks whether the result has the expected value - 检查结果是否为期望值
   */
  void check(SqlTestFactory factory, // 参数：测试工厂
      String query, // 参数：SQL查询字符串
      TypeChecker typeChecker, // 参数：类型检查器
      ParameterChecker parameterChecker, // 参数：参数检查器
      ResultChecker resultChecker); // 参数：结果检查器

  /**
   * Declares that this test is for a given operator. So we can check that all
   * operators are tested.
   * 声明此测试是针对给定操作符的。这样我们可以检查所有操作符是否都被测试
   *
   * @param operator             Operator - 被测试的SQL操作符
   * @param unimplementedVmNames Names of virtual machines for which this - 此操作符未实现的虚拟机名称
   */
  void setFor( // 方法开始
      SqlOperator operator, // 参数：SQL操作符
      VmName... unimplementedVmNames); // 参数：可变参数，未实现的虚拟机名称列表

  /**
   * Checks that an aggregate expression returns the expected result.
   * 检查聚合表达式是否返回期望的结果
   *
   * <p>For example, <code>checkAgg("AVG(DISTINCT x)", new String[] {"2", "3",
   * null, "3" }, new Double(2.5), 0);</code>
   * 例如，checkAgg("AVG(DISTINCT x)", new String[] {"2", "3", null, "3" }, new Double(2.5), 0)
   *
   * @param factory     Factory - SQL测试工厂
   * @param expr        Aggregate expression, e.g. {@code SUM(DISTINCT x)} - 聚合表达式，如SUM(DISTINCT x)
   * @param inputValues Array of input values, e.g. {@code ["1", null, "2"]} - 输入值数组，如["1", null, "2"]
   * @param resultChecker Checks whether the result has the expected value - 检查结果是否为期望值
   */
  void checkAgg(SqlTestFactory factory, // 参数：测试工厂
      String expr, // 参数：聚合表达式字符串
      String[] inputValues, // 参数：输入值数组
      ResultChecker resultChecker); // 参数：结果检查器

  /**
   * Checks that a windowed aggregate expression returns the expected result.
   * 检查窗口聚合表达式是否返回期望的结果
   *
   * <p>For example, <code>checkWinAgg("FIRST_VALUE(x)", new String[] {"2",
   * "3", null, "3" }, "INTEGER NOT NULL", 2, 0d);</code>
   * 例如，checkWinAgg("FIRST_VALUE(x)", new String[] {"2", "3", null, "3" }, "INTEGER NOT NULL", 2, 0d)
   *
   * @param factory     Factory - SQL测试工厂
   * @param expr        Aggregate expression, e.g. {@code SUM(DISTINCT x)} - 聚合表达式，如SUM(DISTINCT x)
   * @param inputValues Array of input values, e.g. {@code ["1", null, "2"]} - 输入值数组，如["1", null, "2"]
   * @param type        Expected result type - 期望的结果类型
   * @param resultChecker Checks whether the result has the expected value - 检查结果是否为期望值
   */
  void checkWinAgg(SqlTestFactory factory, // 参数：测试工厂
      String expr, // 参数：聚合表达式字符串
      String[] inputValues, // 参数：输入值数组
      String windowSpec, // 参数：窗口规范字符串
      String type, // 参数：期望的结果类型字符串
      ResultChecker resultChecker); // 参数：结果检查器

  /**
   * Tests that an aggregate expression fails at run time.
   * 测试聚合表达式在运行时是否失败
   *
   * @param factory       Factory - SQL测试工厂
   * @param expr          An aggregate expression - 聚合表达式
   * @param inputValues   Array of input values - 输入值数组
   * @param expectedError Pattern for expected error - 期望的错误模式
   * @param runtime       If true, must fail at runtime; if false, must fail at
   *                      validate time - 如果为true，必须在运行时失败；如果为false，必须在验证时失败
   */
  void checkAggFails(SqlTestFactory factory, // 参数：测试工厂
      String expr, // 参数：聚合表达式字符串
      String[] inputValues, // 参数：输入值数组
      String expectedError, // 参数：期望的错误消息模式
      boolean runtime); // 参数：是否为运行时错误

  /**
   * Tests that a scalar SQL expression fails at run time.
   * 测试标量SQL表达式在运行时是否失败
   *
   * @param factory       Factory - SQL测试工厂
   * @param expression    SQL scalar expression - SQL标量表达式
   * @param expectedError Pattern for expected error. If !runtime, must
   *                      include an error location. - 期望的错误模式。如果不是运行时，必须包含错误位置
   * @param runtime       If true, must fail at runtime; if false, must fail at
   *                      validate time - 如果为true，必须在运行时失败；如果为false，必须在验证时失败
   */
  void checkFails(SqlTestFactory factory, // 参数：测试工厂
      StringAndPos expression, // 参数：带位置的SQL表达式
      String expectedError, // 参数：期望的错误消息模式
      boolean runtime); // 参数：是否为运行时错误

  /** As {@link #checkFails(SqlTestFactory, StringAndPos, String, boolean)},
   * but with a string that contains carets.
   * 与checkFails(SqlTestFactory, StringAndPos, String, boolean)相同，但使用包含脱字符(^)的字符串
   */
  default void checkFails(SqlTestFactory factory, // 默认方法实现
      String expression, // 参数：包含脱字符的SQL表达式字符串
      String expectedError, // 参数：期望的错误消息模式
      boolean runtime) { // 参数：是否为运行时错误
    checkFails(factory, StringAndPos.of(expression), expectedError, runtime); // 调用完整的checkFails方法，将字符串转换为StringAndPos
  } // 默认方法结束

  /**
   * Tests that a SQL query fails at prepare time.
   * 测试SQL查询在准备阶段是否失败
   *
   * @param factory       Factory - SQL测试工厂
   * @param sap           SQL query and error position - SQL查询和错误位置
   * @param expectedError Pattern for expected error. Must
   *                      include an error location. - 期望的错误模式。必须包含错误位置
   */
  void checkQueryFails(SqlTestFactory factory, StringAndPos sap, // 参数：测试工厂和带位置的SQL查询
      String expectedError); // 参数：期望的错误消息模式

  /**
   * Converts a SQL string to a {@link RelNode} tree.
   * 将SQL字符串转换为关系表达式树(RelNode)
   *
   * @param factory Factory - SQL测试工厂
   * @param sql SQL statement - SQL语句
   * @param decorrelate Whether to decorrelate - 是否去相关化
   * @param trim Whether to trim - 是否修剪
   * @return Relational expression, never null - 关系表达式，从不为null
   */
  default RelRoot convertSqlToRel(SqlTestFactory factory, // 默认方法实现
      String sql, boolean decorrelate, boolean trim) { // 参数：SQL语句、是否去相关化、是否修剪
    Pair<SqlValidator, RelRoot> pair = // 声明键值对变量
        convertSqlToRel2(factory, sql, decorrelate, trim); // 调用convertSqlToRel2获取验证器和关系根节点
    return pair.right; // 返回关系根节点
  } // 默认方法结束

  /** Converts a SQL string to a (SqlValidator, RelNode) pair.
   * 将SQL字符串转换为(SqlValidator, RelNode)键值对
   */
  Pair<SqlValidator, RelRoot> convertSqlToRel2(SqlTestFactory factory, // 返回验证器和关系根节点的键值对
      String sql, boolean decorrelate, boolean trim); // 参数：SQL语句、是否去相关化、是否修剪

  /**
   * Checks that a SQL statement converts to a given plan, optionally
   * trimming columns that are not needed.
   * 检查SQL语句是否转换为给定的计划，可选择修剪不需要的列
   *
   * @param factory Factory - SQL测试工厂
   * @param diffRepos Diff repository - 差异仓库
   * @param sql  SQL query or expression - SQL查询或表达式
   * @param plan Expected plan - 期望的计划
   * @param trim Whether to trim columns that are not needed - 是否修剪不需要的列
   * @param expression True if {@code sql} is an expression, false if it is a query - 如果sql是表达式则为true，如果是查询则为false
   */
  void assertConvertsTo(SqlTestFactory factory, DiffRepository diffRepos, // 参数：测试工厂和差异仓库
      String sql, // 参数：SQL查询或表达式
      String plan, // 参数：期望的计划字符串
      boolean trim, // 参数：是否修剪
      boolean expression, // 参数：是否为表达式
      boolean decorrelate); // 参数：是否去相关化

  /** Trims a RelNode.
   * 修剪关系节点，移除不需要的列
   */
  RelNode trimRelNode(SqlTestFactory factory, RelNode relNode); // 参数：测试工厂和关系节点，返回修剪后的关系节点

  //~ Inner Interfaces -------------------------------------------------------
  // 内部接口区域分隔符，表示以下是内部接口定义

  /** Type checker.
   * 类型检查器接口，用于检查结果是否为期望的数据类型
   */
  interface TypeChecker { // 定义类型检查器接口
    void checkType(Supplier<String> sql, RelDataType type); // 检查类型的方法，接收SQL字符串供应者和关系数据类型
  } // 接口定义结束

  /** Parameter checker.
   * 参数检查器接口，用于检查参数是否为期望的类型
   */
  interface ParameterChecker { // 定义参数检查器接口
    void checkParameters(RelDataType parameterRowType); // 检查参数类型的方法，接收参数行类型
  } // 接口定义结束

  /** Result checker.
   * 结果检查器接口，用于检查结果是否为期望的值
   */
  interface ResultChecker { // 定义结果检查器接口
    void checkResult(String sql, ResultSet result) throws Exception; // 检查结果的方法，接收SQL字符串和结果集，可能抛出异常
  } // 接口定义结束

  /** Action that is called after validation.
   * 验证后调用的操作接口
   *
   * @see #validateAndThen
   */
  interface ValidatedNodeConsumer { // 定义验证节点消费者接口
    void accept(StringAndPos sap, SqlValidator validator, // 接受验证结果的方法
        SqlNode validatedNode); // 参数：带位置的SQL字符串、验证器、验证后的SQL节点
  } // 接口定义结束

  /** A function to apply to the result of validation.
   * 应用于验证结果的函数接口
   *
   * @param <R> Result type of the function - 函数的结果类型
   *
   * @see AbstractSqlTester#validateAndApply
   */
  interface ValidatedNodeFunction<R> { // 定义验证节点函数接口，泛型R为返回类型
    R apply(StringAndPos sap, SqlValidator validator, SqlNode validatedNode); // 应用函数的方法，返回结果R，参数：带位置的SQL字符串、验证器、验证后的SQL节点
  } // 接口定义结束
} // SqlTester接口定义结束
