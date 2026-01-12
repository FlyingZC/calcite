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
package org.apache.calcite.test; // 声明包名，该类属于org.apache.calcite.test包，用于SQL操作符测试

import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，用于描述Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段类，表示关系类型中的字段
import org.apache.calcite.sql.SqlNode; // 导入SQL节点类，是所有SQL语法树的基类
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符类，表示SQL中的操作符（如+、-、*、/等）
import org.apache.calcite.sql.parser.StringAndPos; // 导入字符串和位置类，用于存储SQL字符串及其位置信息
import org.apache.calcite.sql.test.ResultCheckers; // 导入结果检查器类，提供各种结果验证工具方法
import org.apache.calcite.sql.test.SqlOperatorFixture; // 导入SQL操作符测试夹具接口，定义测试SQL操作符的契约
import org.apache.calcite.sql.test.SqlTestFactory; // 导入SQL测试工厂类，用于创建测试所需的组件
import org.apache.calcite.sql.test.SqlTester; // 导入SQL测试器接口，提供SQL测试的核心功能
import org.apache.calcite.sql.test.SqlTests; // 导入SQL测试工具类，提供测试辅助方法和常量
import org.apache.calcite.sql.test.SqlValidatorTester; // 导入SQL验证器测试器类，用于测试SQL验证功能
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口，负责验证SQL语句的语法和语义
import org.apache.calcite.util.JdbcType; // 导入JDBC类型类，表示JDBC中的数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数或返回值
import org.hamcrest.Matcher; // 导入匹配器接口，用于断言测试结果

import java.util.List; // 导入List接口，用于处理列表集合
import java.util.function.UnaryOperator; // 导入一元操作符函数式接口，用于对单个对象进行转换操作

import static org.apache.calcite.sql.test.ResultCheckers.isNullValue; // 静态导入isNullValue方法，用于检查结果是否为null值
import static org.apache.calcite.sql.test.ResultCheckers.isSingle; // 静态导入isSingle方法，用于检查结果是否为单个值

import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器，用于检查值是否相等
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat方法，用于执行断言
import static org.hamcrest.Matchers.hasSize; // 静态导入hasSize匹配器，用于检查集合大小
import static org.junit.jupiter.api.Assertions.assertNotNull; // 静态导入assertNotNull方法，用于断言对象不为null

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查参数不为null

/**
 * SqlOperatorFixture接口的实现类，提供SQL操作符测试的具体功能
 * 
 * 该类是Calcite测试框架中的核心测试夹具实现，用于测试SQL操作符的行为
 * 主要功能包括：
 * 1. 解析和验证SQL语句
 * 2. 检查SQL表达式的类型
 * 3. 验证SQL查询的执行结果
 * 4. 测试聚合函数和窗口函数
 * 5. 验证错误情况下的行为
 * 
 * 该类采用不可变对象模式，所有修改操作都会返回新的实例
 */
class SqlOperatorFixtureImpl implements SqlOperatorFixture { // 定义SqlOperatorFixtureImpl类，实现SqlOperatorFixture接口
  public static final SqlOperatorFixtureImpl DEFAULT = // 定义默认的测试夹具实例，使用默认配置
      new SqlOperatorFixtureImpl(SqlTestFactory.INSTANCE, // 使用默认的SQL测试工厂
          SqlValidatorTester.DEFAULT, false); // 使用默认的SQL验证器测试器，禁用broken测试

  private final SqlTestFactory factory; // SQL测试工厂，用于创建测试所需的组件（如验证器、解析器等）
  private final SqlTester tester; // SQL测试器，提供SQL测试的核心功能（解析、验证、执行等）
  private final boolean brokenTestsEnabled; // 是否启用已知失败的测试，用于标记和跳过已知的bug

  SqlOperatorFixtureImpl(SqlTestFactory factory, SqlTester tester, // 构造方法，创建SqlOperatorFixtureImpl实例
      boolean brokenTestsEnabled) { // 参数：factory-测试工厂，tester-测试器，brokenTestsEnabled-是否启用broken测试
    this.factory = requireNonNull(factory, "factory"); // 初始化factory字段，确保factory不为null
    this.tester = requireNonNull(tester, "tester"); // 初始化tester字段，确保tester不为null
    this.brokenTestsEnabled = brokenTestsEnabled; // 初始化brokenTestsEnabled字段
  }

  @Override public void close() { // 重写close方法，用于释放资源
  } // 当前实现为空，无需释放资源

  @Override public SqlTestFactory getFactory() { // 重写getFactory方法，获取SQL测试工厂
    return factory; // 返回当前的SQL测试工厂实例
  } // 该工厂用于创建测试所需的各种组件

  @Override public SqlTester getTester() { // 重写getTester方法，获取SQL测试器
    return tester; // 返回当前的SQL测试器实例
  } // 该测试器提供SQL解析、验证和执行的核心功能

  @Override public SqlOperatorFixture withFactory( // 重写withFactory方法，使用转换函数创建新的测试夹具
      UnaryOperator<SqlTestFactory> transform) { // 参数：transform-对工厂进行转换的一元操作符
    final SqlTestFactory factory = transform.apply(this.factory); // 应用转换函数到当前工厂，得到新的工厂
    if (factory == this.factory) { // 如果转换后的工厂与原工厂相同
      return this; // 返回当前实例，避免创建新对象
    } // 优化：当工厂未改变时直接返回this
    return new SqlOperatorFixtureImpl(factory, tester, brokenTestsEnabled); // 创建并返回新的测试夹具实例，使用新工厂
  } // 该方法支持不可变对象模式，返回新实例而不是修改当前实例

  @Override public SqlOperatorFixture withTester( // 重写withTester方法，使用转换函数创建新的测试夹具
      UnaryOperator<SqlTester> transform) { // 参数：transform-对测试器进行转换的一元操作符
    final SqlTester tester = transform.apply(this.tester); // 应用转换函数到当前测试器，得到新的测试器
    if (tester == this.tester) { // 如果转换后的测试器与原测试器相同
      return this; // 返回当前实例，避免创建新对象
    } // 优化：当测试器未改变时直接返回this
    return new SqlOperatorFixtureImpl(factory, tester, brokenTestsEnabled); // 创建并返回新的测试夹具实例，使用新测试器
  } // 该方法支持不可变对象模式，返回新实例而不是修改当前实例

  @Override public boolean brokenTestsEnabled() { // 重写brokenTestsEnabled方法，检查是否启用了broken测试
    return brokenTestsEnabled; // 返回brokenTestsEnabled标志的值
  } // 用于在测试中判断是否应该运行已知失败的测试用例

  @Override public SqlOperatorFixture withBrokenTestsEnabled( // 重写withBrokenTestsEnabled方法，设置broken测试标志
      boolean brokenTestsEnabled) { // 参数：brokenTestsEnabled-是否启用broken测试的新值
    if (brokenTestsEnabled == this.brokenTestsEnabled) { // 如果新值与当前值相同
      return this; // 返回当前实例，避免创建新对象
    } // 优化：当标志未改变时直接返回this
    return new SqlOperatorFixtureImpl(factory, tester, brokenTestsEnabled); // 创建并返回新的测试夹具实例，使用新标志
  } // 该方法支持不可变对象模式，返回新实例而不是修改当前实例

  @Override public SqlOperatorFixture setFor(SqlOperator operator, // 重写setFor方法，为特定操作符设置测试环境
      VmName... unimplementedVmNames) { // 参数：operator-要测试的SQL操作符，unimplementedVmNames-未实现的虚拟机名称
    return this; // 直接返回当前实例，当前实现不进行任何操作
  } // 该方法预留用于设置特定操作符的测试环境

  SqlNode parseAndValidate(SqlValidator validator, String sql) { // 解析并验证SQL语句，返回验证后的SQL节点
    SqlNode sqlNode; // 声明SQL节点变量，用于存储解析结果
    try { // 尝试解析SQL语句
      sqlNode = tester.parseQuery(factory, sql); // 使用测试器解析SQL查询，得到SQL节点
    } catch (Throwable e) { // 捕获解析过程中的任何异常
      throw new RuntimeException("Error while parsing query: " + sql, e); // 抛出运行时异常，包装原始异常信息
    } // 确保解析错误能够被正确传播
    return validator.validate(sqlNode); // 使用验证器验证SQL节点，返回验证后的节点
  } // 该方法是SQL处理流程的核心：解析 -> 验证

  @Override public void checkColumnType(String sql, String expected) { // 重写checkColumnType方法，检查SQL查询的列类型
    tester.validateAndThen(factory, StringAndPos.of(sql), // 使用测试器验证SQL，然后执行后续操作
        checkColumnTypeAction(is(expected))); // 创建列类型检查动作，验证结果类型是否与期望类型匹配
  } // 该方法用于测试SQL查询返回的列类型是否符合预期

  @Override public void checkType(String expression, String type) { // 重写checkType方法，检查表达式的类型
    forEachQueryValidateAndThen(StringAndPos.of(expression), // 对表达式进行查询处理和验证
        checkColumnTypeAction(is(type))); // 创建列类型检查动作，验证表达式类型是否与期望类型匹配
  } // 该方法用于测试SQL表达式的返回类型是否符合预期

  private static SqlTester.ValidatedNodeConsumer checkColumnTypeAction( // 私有静态方法，创建列类型检查动作
      Matcher<String> matcher) { // 参数：matcher-字符串匹配器，用于验证类型字符串
    return (sql, validator, validatedNode) -> { // 返回一个ValidatedNodeConsumer lambda表达式，处理验证后的节点
      final RelDataType rowType = // 获取验证后节点的行类型（关系数据类型）
          validator.getValidatedNodeType(validatedNode); // 通过验证器获取节点的验证后类型
      final List<RelDataTypeField> fields = rowType.getFieldList(); // 获取行类型中的所有字段列表
      assertThat("expected query to return 1 field", fields, hasSize(1)); // 断言查询应该返回1个字段
      final RelDataType actualType = fields.get(0).getType(); // 获取第一个字段的实际类型
      String actual = SqlTests.getTypeString(actualType); // 将实际类型转换为字符串表示
      assertThat("Query: " + sql.sql, actual, matcher); // 断言实际类型字符串与匹配器匹配
    }; // lambda表达式结束
  } // 该方法创建一个消费者，用于验证SQL查询返回的列类型

  @Override public void checkQuery(String sql) { // 重写checkQuery方法，检查SQL查询是否抛出异常
    tester.assertExceptionIsThrown(factory, StringAndPos.of(sql), null); // 断言执行SQL查询时会抛出异常
  } // 该方法用于测试应该失败的SQL查询

  void forEachQueryValidateAndThen(StringAndPos expression, // 私有方法，对表达式进行查询处理、验证和后续操作
      SqlTester.ValidatedNodeConsumer consumer) { // 参数：expression-表达式，consumer-验证后节点的消费者
    tester.forEachQuery(factory, expression.addCarets(), query -> // 对表达式添加插入符后进行迭代查询处理
        tester.validateAndThen(factory, StringAndPos.of(query), consumer)); // 对每个查询进行验证，然后调用消费者处理
  } // 该方法用于处理包含插入符的表达式，支持测试多个变体

  @Override public void checkFails(StringAndPos sap, String expectedError, // 重写checkFails方法，检查表达式是否在预期时失败
      boolean runtime) { // 参数：sap-字符串和位置对象，expectedError-期望的错误消息，runtime-是否为运行时错误
    final String sql = "values (" + sap.addCarets() + ")"; // 构建VALUES表达式，将表达式包装在VALUES子句中
    if (runtime) { // 如果是运行时错误
      // We need to test that the expression fails at runtime.
      // Ironically, that means that it must succeed at prepare time.
      SqlValidator validator = factory.createValidator(); // 创建SQL验证器
      SqlNode n = parseAndValidate(validator, sql); // 解析并验证SQL语句
      assertNotNull(n); // 断言解析后的节点不为null，确保预处理成功
      tester.checkFails(factory, sap, expectedError, runtime); // 使用测试器检查表达式在运行时是否失败
    } else { // 如果不是运行时错误（即编译时/验证时错误）
      checkQueryFails(StringAndPos.of(sql), // 检查SQL查询在验证时是否失败
          expectedError); // 传入期望的错误消息
    } // 根据错误类型选择不同的验证策略
  } // 该方法用于测试表达式在预期阶段（验证时或运行时）是否失败

  @Override public void checkQueryFails(StringAndPos sap, // 重写checkQueryFails方法，检查SQL查询是否失败
      String expectedError) { // 参数：sap-字符串和位置对象，expectedError-期望的错误消息
    tester.assertExceptionIsThrown(factory, sap, expectedError); // 断言执行SQL查询时会抛出包含期望消息的异常
  } // 该方法用于测试应该在验证阶段失败的SQL查询

  @Override public void checkAggFails( // 重写checkAggFails方法，检查聚合表达式是否失败
      String expr, // 参数：expr-聚合表达式
      String[] inputValues, // 参数：inputValues-输入值数组
      String expectedError, // 参数：expectedError-期望的错误消息
      boolean runtime) { // 参数：runtime-是否为运行时错误
    final String sql = // 生成聚合查询SQL
        SqlTests.generateAggQuery(expr, inputValues); // 使用SqlTests工具类生成聚合查询
    if (runtime) { // 如果是运行时错误
      SqlValidator validator = factory.createValidator(); // 创建SQL验证器
      SqlNode n = parseAndValidate(validator, sql); // 解析并验证SQL语句
      assertNotNull(n); // 断言解析后的节点不为null，确保预处理成功
      tester.checkAggFails(factory, expr, inputValues, expectedError, runtime); // 使用测试器检查聚合表达式在运行时是否失败
    } else { // 如果不是运行时错误（即编译时/验证时错误）
      checkQueryFails(StringAndPos.of(sql), expectedError); // 检查聚合查询在验证时是否失败
    } // 根据错误类型选择不同的验证策略
  } // 该方法用于测试聚合表达式在预期阶段是否失败

  @Override public void checkAgg(String expr, String[] inputValues, // 重写checkAgg方法，检查聚合表达式
      SqlTester.ResultChecker checker) { // 参数：expr-聚合表达式，inputValues-输入值数组，checker-结果检查器
    checkAgg(expr, inputValues, SqlTests.ANY_TYPE_CHECKER, checker); // 调用重载方法，使用任意类型检查器
  } // 该方法不限制返回类型，使用默认的类型检查器

  @Override public void checkAgg(String expr, String[] inputValues, // 重写checkAgg方法，检查聚合表达式的类型和结果
      String type, SqlTester.ResultChecker checker) { // 参数：expr-聚合表达式，inputValues-输入值数组，type-期望类型，checker-结果检查器
    final SqlTester.TypeChecker typeChecker = // 创建类型检查器
        new SqlTests.StringTypeChecker(type); // 使用字符串类型检查器，检查返回类型是否与期望类型匹配
    checkAgg(expr, inputValues, typeChecker, checker); // 调用私有重载方法执行聚合检查
  } // 该方法验证聚合表达式的返回类型和结果

  private void checkAgg(String expr, String[] inputValues, // 私有方法，执行聚合检查的核心逻辑
      SqlTester.TypeChecker typeChecker, SqlTester.ResultChecker resultChecker) { // 参数：expr-聚合表达式，inputValues-输入值数组，typeChecker-类型检查器，resultChecker-结果检查器
    String query = // 生成聚合查询SQL
        SqlTests.generateAggQuery(expr, inputValues); // 使用SqlTests工具类生成聚合查询
    tester.check(factory, query, typeChecker, resultChecker); // 使用测试器检查查询的类型和结果
  } // 该方法是所有checkAgg重载方法的最终实现

  @Override public void checkAggWithMultipleArgs( // 重写checkAggWithMultipleArgs方法，检查多参数聚合表达式
      String expr, // 参数：expr-聚合表达式
      String[][] inputValues, // 参数：inputValues-二维输入值数组，每个子数组代表一个参数的输入值
      SqlTester.ResultChecker resultChecker) { // 参数：resultChecker-结果检查器
    String query = // 生成多参数聚合查询SQL
        SqlTests.generateAggQueryWithMultipleArgs(expr, inputValues); // 使用SqlTests工具类生成多参数聚合查询
    tester.check(factory, query, SqlTests.ANY_TYPE_CHECKER, resultChecker); // 使用测试器检查查询，使用任意类型检查器
  } // 该方法用于测试接受多个参数的聚合函数

  @Override public void checkWinAgg( // 重写checkWinAgg方法，检查窗口聚合表达式
      String expr, // 参数：expr-窗口聚合表达式
      String[] inputValues, // 参数：inputValues-输入值数组
      String windowSpec, // 参数：windowSpec-窗口规范（如OVER子句）
      String type, // 参数：type-期望的返回类型
      SqlTester.ResultChecker resultChecker) { // 参数：resultChecker-结果检查器
    final SqlTester.TypeChecker typeChecker = // 创建类型检查器
        new SqlTests.StringTypeChecker(type); // 使用字符串类型检查器，检查返回类型是否与期望类型匹配
    String query = // 生成窗口聚合查询SQL
        SqlTests.generateWinAggQuery(expr, windowSpec, inputValues); // 使用SqlTests工具类生成窗口聚合查询
    tester.check(factory, query, typeChecker, resultChecker); // 使用测试器检查查询的类型和结果
  } // 该方法用于测试窗口函数（如OVER子句中的聚合函数）

  @Override public void checkScalar(String expression, // 重写checkScalar方法，检查标量表达式
      SqlTester.TypeChecker typeChecker, // 参数：typeChecker-类型检查器
      SqlTester.ResultChecker resultChecker) { // 参数：resultChecker-结果检查器
    tester.forEachQuery(factory, expression, sql -> // 对表达式进行迭代查询处理
        tester.check(factory, sql, typeChecker, resultChecker)); // 检查每个查询的类型和结果
  } // 该方法用于测试返回单个值的标量表达式

  @Override public void checkScalarExact(String expression, // 重写checkScalarExact方法，精确检查标量表达式
      String expectedType, SqlTester.ResultChecker resultChecker) { // 参数：expression-标量表达式，expectedType-期望类型，resultChecker-结果检查器
    final SqlTester.TypeChecker typeChecker = // 创建类型检查器
        new SqlTests.StringTypeChecker(expectedType); // 使用字符串类型检查器，精确匹配期望类型
    tester.forEachQuery(factory, expression, sql -> // 对表达式进行迭代查询处理
        tester.check(factory, sql, typeChecker, resultChecker)); // 检查每个查询的类型和结果
  } // 该方法用于精确测试标量表达式的类型和结果，类型必须完全匹配

  @Override public void checkScalarApprox( // 重写checkScalarApprox方法，近似检查标量表达式
      String expression, // 参数：expression-标量表达式
      String expectedType, // 参数：expectedType-期望类型
      Object result) { // 参数：result-期望结果值
    SqlTester.TypeChecker typeChecker = // 创建类型检查器
        new SqlTests.StringTypeChecker(expectedType); // 使用字符串类型检查器，检查返回类型是否与期望类型匹配
    final SqlTester.ResultChecker checker = ResultCheckers.createChecker(result); // 创建结果检查器，验证结果值
    tester.forEachQuery(factory, expression, sql -> // 对表达式进行迭代查询处理
        tester.check(factory, sql, typeChecker, checker)); // 检查每个查询的类型和结果
  } // 该方法用于近似测试标量表达式，适用于浮点数等需要近似比较的场景

  @Override public void checkBoolean( // 重写checkBoolean方法，检查布尔表达式
      String expression, // 参数：expression-布尔表达式
      @Nullable Boolean result) { // 参数：result-期望的布尔结果值（可能为null）
    if (null == result) { // 如果期望结果为null
      checkNull(expression); // 检查表达式是否返回null
    } else { // 如果期望结果不为null
      SqlTester.ResultChecker resultChecker = // 创建结果检查器
          ResultCheckers.createChecker(is(result), JdbcType.BOOLEAN); // 使用is匹配器验证布尔值，指定JDBC布尔类型
      tester.forEachQuery(factory, expression, sql -> // 对表达式进行迭代查询处理
          tester.check(factory, sql, SqlTests.BOOLEAN_TYPE_CHECKER, // 检查布尔类型
              SqlTests.ANY_PARAMETER_CHECKER, resultChecker)); // 使用任意参数检查器和结果检查器
    } // 根据期望结果是否为null选择不同的验证策略
  } // 该方法用于测试返回布尔值的表达式

  @Override public void checkString( // 重写checkString方法，检查字符串表达式
      String expression, // 参数：expression-字符串表达式
      String result, // 参数：result-期望的字符串结果值
      String expectedType) { // 参数：expectedType-期望的返回类型
    SqlTester.TypeChecker typeChecker = // 创建类型检查器
        new SqlTests.StringTypeChecker(expectedType); // 使用字符串类型检查器，检查返回类型是否与期望类型匹配
    SqlTester.ResultChecker resultChecker = isSingle(result); // 创建结果检查器，验证单个字符串结果
    tester.forEachQuery(factory, expression, sql -> // 对表达式进行迭代查询处理
        tester.check(factory, sql, typeChecker, resultChecker)); // 检查每个查询的类型和结果
  } // 该方法用于测试返回字符串的表达式

  @Override public void checkNull(String expression) { // 重写checkNull方法，检查表达式是否返回null
    tester.forEachQuery(factory, expression, sql -> // 对表达式进行迭代查询处理
        tester.check(factory, sql, SqlTests.ANY_NULLABLE_TYPE_CHECKER, isNullValue())); // 检查结果是否为null值
  } // 该方法用于测试返回null的表达式
} // 类定义结束
