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
package org.apache.calcite.sql.test; // 包声明:org.apache.calcite.sql.test包,包含SQL测试相关的类
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil类,用于关系表达式优化工具
import org.apache.calcite.rel.RelNode; // 导入RelNode类,表示关系代数节点
import org.apache.calcite.rel.RelRoot; // 导入RelRoot类,表示关系树的根节点
import org.apache.calcite.rel.core.RelFactories; // 导入RelFactories类,提供关系节点工厂
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类,表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类,表示关系数据类型字段
import org.apache.calcite.rex.RexNode; // 导入RexNode类,表示行表达式节点
import org.apache.calcite.runtime.PairList; // 导入PairList类,表示键值对列表
import org.apache.calcite.runtime.Utilities; // 导入Utilities类,提供通用工具方法
import org.apache.calcite.sql.SqlCall; // 导入SqlCall类,表示SQL函数调用
import org.apache.calcite.sql.SqlKind; // 导入SqlKind类,表示SQL操作符类型
import org.apache.calcite.sql.SqlLiteral; // 导入SqlLiteral类,表示SQL字面量
import org.apache.calcite.sql.SqlNode; // 导入SqlNode类,表示SQL抽象语法树节点
import org.apache.calcite.sql.SqlOperator; // 导入SqlOperator类,表示SQL操作符
import org.apache.calcite.sql.SqlUnresolvedFunction; // 导入SqlUnresolvedFunction类,表示未解析的SQL函数
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类,标准SQL操作符表
import org.apache.calcite.sql.parser.SqlParseException; // 导入SqlParseException类,SQL解析异常
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类,SQL解析器
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SqlParserPos类,SQL解析位置
import org.apache.calcite.sql.parser.SqlParserUtil; // 导入SqlParserUtil类,SQL解析工具
import org.apache.calcite.sql.parser.StringAndPos; // 导入StringAndPos类,表示字符串及其位置
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName类,SQL类型名称枚举
import org.apache.calcite.sql.util.SqlShuttle; // 导入SqlShuttle类,SQL节点访问器基类
import org.apache.calcite.sql.validate.SqlValidator; // 导入SqlValidator类,SQL验证器
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SqlValidatorUtil类,SQL验证工具
import org.apache.calcite.sql2rel.RelFieldTrimmer; // 导入RelFieldTrimmer类,关系字段修剪器
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入SqlToRelConverter类,SQL到关系代数转换器
import org.apache.calcite.test.DiffRepository; // 导入DiffRepository类,差异存储库
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder类,关系表达式构建器
import org.apache.calcite.util.Pair; // 导入Pair类,表示键值对
import org.apache.calcite.util.TestUtil; // 导入TestUtil类,测试工具
import org.apache.calcite.util.Util; // 导入Util类,通用工具类

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类,不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解,表示可空类型
import org.hamcrest.Matcher; // 导入Matcher类,Hamcrest匹配器

import java.util.ArrayList; // 导入ArrayList类,动态数组
import java.util.Collection; // 导入Collection接口,集合接口
import java.util.LinkedHashSet; // 导入LinkedHashSet类,链式哈希集合
import java.util.List; // 导入List接口,列表接口
import java.util.function.Consumer; // 导入Consumer接口,消费者函数式接口

import static org.apache.calcite.test.Matchers.relIsValid; // 静态导入relIsValid匹配器

import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat断言方法
import static org.hamcrest.Matchers.hasSize; // 静态导入hasSize匹配器
import static org.junit.jupiter.api.Assertions.assertNotNull; // 静态导入assertNotNull断言方法

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法

/**
 * Abstract implementation of {@link SqlTester}
 * that talks to a mock catalog.
 *
 * <p>This is to implement the default behavior: testing is only against the
 * {@link SqlValidator}.
 */
// SqlTester接口的抽象实现,与模拟目录进行交互
// 实现默认行为:仅针对SqlValidator进行测试
public abstract class AbstractSqlTester implements SqlTester, AutoCloseable { // 抽象类,实现SqlTester和AutoCloseable接口
  private  static final String NL = System.getProperty("line.separator"); // 静态常量:系统换行符,用于格式化输出

  public AbstractSqlTester() { // 无参构造方法,初始化测试器
  } // 构造方法结束

  /**
   * {@inheritDoc}
   *
   * <p>This default implementation does nothing.
   */
  // 覆盖AutoCloseable接口的close方法,释放资源
  // 此默认实现不执行任何操作
  @Override public void close() { // 实现close方法
    // no resources to release // 没有资源需要释放
  } // close方法结束

  @Override public void assertExceptionIsThrown(SqlTestFactory factory, // 实现SqlTester接口方法,断言抛出异常
      StringAndPos sap, @Nullable String expectedMsgPattern) { // 参数:测试工厂,字符串和位置,期望的错误消息模式
    final SqlNode sqlNode; // 声明SQL节点变量
    try { // 尝试解析SQL
      sqlNode = parseQuery(factory, sap.sql); // 解析SQL查询语句
    } catch (Throwable e) { // 捕获解析异常
      SqlTests.checkEx(e, expectedMsgPattern, sap, SqlTests.Stage.PARSE); // 检查解析阶段的异常
      return; // 返回,异常已处理
    } // 解析try块结束

    final SqlValidator validator = factory.createValidator(); // 创建SQL验证器
    Throwable thrown = null; // 初始化异常变量为null
    try { // 尝试验证SQL
      validator.validate(sqlNode); // 验证SQL节点
    } catch (Throwable ex) { // 捕获验证异常
      thrown = ex; // 保存异常
    } // 验证try块结束

    SqlTests.checkEx(thrown, expectedMsgPattern, sap, SqlTests.Stage.VALIDATE); // 检查验证阶段的异常
  } // assertExceptionIsThrown方法结束

  protected void checkParseEx(Throwable e, @Nullable String expectedMsgPattern, // 保护方法,检查解析异常
      StringAndPos sap) { // 参数:异常对象,期望的错误消息模式,字符串和位置
    try { // 尝试重新抛出异常
      throw e; // 抛出异常
    } catch (SqlParseException spe) { // 捕获SQL解析异常
      String errMessage = spe.getMessage(); // 获取错误消息
      if (expectedMsgPattern == null) { // 如果没有期望的错误消息模式
        throw new RuntimeException("Error while parsing query:" + sap, spe); // 抛出运行时异常
      } else if (errMessage == null // 如果错误消息为null
          || !Util.toLinux(errMessage).matches(expectedMsgPattern)) { // 或者错误消息不匹配期望的模式
        throw new RuntimeException("Error did not match expected [" // 抛出不匹配的运行时异常
            + expectedMsgPattern + "] while parsing query ["
            + sap + "]", spe);
      } // if结束
    } catch (Throwable t) { // 捕获其他异常
      throw new RuntimeException("Error while parsing query: " + sap, t); // 抛出运行时异常
    } // catch结束
  } // checkParseEx方法结束

  @Override public RelDataType getColumnType(SqlTestFactory factory, // 实现SqlTester接口方法,获取列类型
      String sql) { // 参数:测试工厂,SQL查询语句
    return validateAndApply(factory, StringAndPos.of(sql), // 调用validateAndApply方法验证SQL并应用函数
        (sql1, validator, n) -> { // Lambda表达式:接收SQL、验证器和节点
          final RelDataType rowType = // 声明行类型变量
              validator.getValidatedNodeType(n); // 获取验证后的节点类型
          final List<RelDataTypeField> fields = rowType.getFieldList(); // 获取字段列表
          assertThat("expected query to return 1 field", fields, hasSize(1)); // 断言查询返回1个字段
          return fields.get(0).getType(); // 返回第一个字段的类型
        }); // Lambda表达式结束
  } // getColumnType方法结束

  @Override public RelDataType getResultType(SqlTestFactory factory, // 实现SqlTester接口方法,获取结果类型
      String sql) { // 参数:测试工厂,SQL查询语句
    return validateAndApply(factory, StringAndPos.of(sql), // 调用validateAndApply方法验证SQL并应用函数
        (sql1, validator, n) -> // Lambda表达式:接收SQL、验证器和节点
            validator.getValidatedNodeType(n)); // 返回验证后的节点类型
  } // getResultType方法结束

  Pair<SqlValidator, SqlNode> parseAndValidate(SqlTestFactory factory, // 解析并验证SQL,返回验证器和验证后的节点
      String sql) { // 参数:测试工厂,SQL查询语句
    SqlNode sqlNode; // 声明SQL节点变量
    try { // 尝试解析SQL
      sqlNode = parseQuery(factory, sql); // 解析SQL查询语句
    } catch (Throwable e) { // 捕获解析异常
      throw new RuntimeException("Error while parsing query: " + sql, e); // 抛出运行时异常
    } // try块结束
    SqlValidator validator = factory.createValidator(); // 创建SQL验证器
    return Pair.of(validator, validator.validate(sqlNode)); // 返回验证器和验证后的节点组成的键值对
  } // parseAndValidate方法结束

  @Override public SqlNode parseQuery(SqlTestFactory factory, String sql) // 实现SqlTester接口方法,解析SQL查询
      throws SqlParseException { // 可能抛出SQL解析异常
    SqlParser parser = factory.createParser(sql); // 创建SQL解析器
    return parser.parseQuery(); // 解析并返回SQL查询节点
  } // parseQuery方法结束

  @Override public SqlNode parseExpression(SqlTestFactory factory, // 实现SqlTester接口方法,解析SQL表达式
      String expr) throws SqlParseException { // 参数:测试工厂,SQL表达式,可能抛出SQL解析异常
    SqlParser parser = factory.createParser(expr); // 创建SQL解析器
    return parser.parseExpression(); // 解析并返回SQL表达式节点
  } // parseExpression方法结束

  @Override public void checkColumnType(SqlTestFactory factory, String sql, // 实现SqlTester接口方法,检查列类型
      String expected) { // 参数:测试工厂,SQL查询语句,期望的类型字符串
    validateAndThen(factory, StringAndPos.of(sql), // 调用validateAndThen方法验证SQL并执行操作
        checkColumnTypeAction(is(expected))); // 创建列类型检查动作
  } // checkColumnType方法结束

  private static ValidatedNodeConsumer checkColumnTypeAction( // 私有静态方法,创建列类型检查动作
      Matcher<String> matcher) { // 参数:字符串匹配器
    return (sql1, validator, validatedNode) -> { // 返回Lambda表达式:接收SQL、验证器和验证后的节点
      final RelDataType rowType = // 声明行类型变量
          validator.getValidatedNodeType(validatedNode); // 获取验证后的节点类型
      final List<RelDataTypeField> fields = rowType.getFieldList(); // 获取字段列表
      assertThat("expected query to return 1 field", fields, hasSize(1)); // 断言查询返回1个字段
      final RelDataType actualType = fields.get(0).getType(); // 获取第一个字段的类型
      String actual = SqlTests.getTypeString(actualType); // 获取类型字符串
      assertThat(actual, matcher); // 断言实际类型匹配期望
    }; // Lambda表达式结束
  } // checkColumnTypeAction方法结束

  // SqlTester methods // SqlTester接口方法的实现

  @Override public void setFor( // 实现SqlTester接口方法,设置操作符
      SqlOperator operator, // 参数:SQL操作符
      VmName... unimplementedVmNames) { // 可变参数:未实现的虚拟机名称
    // do nothing // 不执行任何操作
  } // setFor方法结束

  @Override public void checkAgg(SqlTestFactory factory, // 实现SqlTester接口方法,检查聚合函数
      String expr, // 参数:测试工厂,表达式
      String[] inputValues, // 输入值数组
      ResultChecker resultChecker) { // 结果检查器
    String query = // 声明查询字符串变量
        SqlTests.generateAggQuery(expr, inputValues); // 生成聚合查询
    check(factory, query, SqlTests.ANY_TYPE_CHECKER, resultChecker); // 检查查询
  } // checkAgg方法结束

  @Override public void checkWinAgg(SqlTestFactory factory, // 实现SqlTester接口方法,检查窗口聚合函数
      String expr, // 参数:测试工厂,表达式
      String[] inputValues, // 输入值数组
      String windowSpec, // 窗口规范
      String type, // 类型
      ResultChecker resultChecker) { // 结果检查器
    String query = // 声明查询字符串变量
        SqlTests.generateWinAggQuery( // 生成窗口聚合查询
            expr, windowSpec, inputValues); // 参数:表达式,窗口规范,输入值
    check(factory, query, SqlTests.ANY_TYPE_CHECKER, resultChecker); // 检查查询
  } // checkWinAgg方法结束

  @Override public void check(SqlTestFactory factory, // 实现SqlTester接口方法,检查SQL查询
      String query, TypeChecker typeChecker, // 参数:测试工厂,查询语句,类型检查器
      ParameterChecker parameterChecker, ResultChecker resultChecker) { // 参数检查器,结果检查器
    // This implementation does NOT check the result! // 此实现不检查结果!
    // All it does is check the return type. // 只检查返回类型
    requireNonNull(typeChecker, "typeChecker"); // 断言类型检查器不为null
    requireNonNull(parameterChecker, "parameterChecker"); // 断言参数检查器不为null
    requireNonNull(resultChecker, "resultChecker"); // 断言结果检查器不为null

    // Parse and validate. There should be no errors. // 解析和验证,应该没有错误
    // There must be 1 column. Get its type. // 必须有1列,获取其类型
    RelDataType actualType = getColumnType(factory, query); // 获取列类型

    // Check result type. // 检查结果类型
    typeChecker.checkType(() -> "Query: " + query, actualType); // 检查类型

    Pair<SqlValidator, SqlNode> p = parseAndValidate(factory, query); // 解析并验证SQL
    SqlValidator validator = p.left; // 获取验证器
    SqlNode n = p.right; // 获取节点
    final RelDataType parameterRowType = validator.getParameterRowType(n); // 获取参数行类型
    parameterChecker.checkParameters(parameterRowType); // 检查参数
  } // check方法结束

  @Override public void validateAndThen(SqlTestFactory factory, // 实现SqlTester接口方法,验证后执行操作
      StringAndPos sap, ValidatedNodeConsumer consumer) { // 参数:测试工厂,字符串和位置,验证节点消费者
    Pair<SqlValidator, SqlNode> p = parseAndValidate(factory, sap.sql); // 解析并验证SQL
    SqlValidator validator = p.left; // 获取验证器
    SqlNode rewrittenNode = p.right; // 获取重写后的节点
    consumer.accept(sap, validator, rewrittenNode); // 调用消费者处理
  } // validateAndThen方法结束

  @Override public <R> R validateAndApply(SqlTestFactory factory, // 实现SqlTester接口方法,验证后应用函数并返回结果
      StringAndPos sap, ValidatedNodeFunction<R> function) { // 参数:测试工厂,字符串和位置,验证节点函数
    Pair<SqlValidator, SqlNode> p = parseAndValidate(factory, sap.sql); // 解析并验证SQL
    SqlValidator validator = p.left; // 获取验证器
    SqlNode rewrittenNode = p.right; // 获取重写后的节点
    return function.apply(sap, validator, rewrittenNode); // 应用函数并返回结果
  } // validateAndApply方法结束

  @Override public void checkFails(SqlTestFactory factory, StringAndPos sap, // 实现SqlTester接口方法,检查失败情况
      String expectedError, boolean runtime) { // 参数:测试工厂,字符串和位置,期望错误,是否运行时
    if (runtime) { // 如果是运行时错误
      // We need to test that the expression fails at runtime. // 需要测试表达式在运行时失败
      // Ironically, that means that it must succeed at prepare time. // 讽刺的是,这意味着它必须在准备时成功
      final String sql = buildQuery(sap.addCarets()); // 构建查询语句
      Pair<SqlValidator, SqlNode> p = parseAndValidate(factory, sql); // 解析并验证SQL
      SqlNode n = p.right; // 获取节点
      assertNotNull(n); // 断言节点不为null
    } else { // 如果不是运行时错误
      StringAndPos sap1 = StringAndPos.of(buildQuery(sap.addCarets())); // 构建查询并创建字符串和位置对象
      checkQueryFails(factory, sap1, expectedError); // 检查查询失败
    } // if结束
  } // checkFails方法结束

  @Override public void checkQueryFails(SqlTestFactory factory, // 实现SqlTester接口方法,检查查询失败
      StringAndPos sap, String expectedError) { // 参数:测试工厂,字符串和位置,期望错误
    assertExceptionIsThrown(factory, sap, expectedError); // 断言抛出异常
  } // checkQueryFails方法结束

  @Override public void checkAggFails(SqlTestFactory factory, // 实现SqlTester接口方法,检查聚合失败
      String expr, // 参数:测试工厂,表达式
      String[] inputValues, // 输入值数组
      String expectedError, // 期望错误
      boolean runtime) { // 是否运行时
    final String sql = // 声明查询字符串变量
        SqlTests.generateAggQuery(expr, inputValues); // 生成聚合查询
    if (runtime) { // 如果是运行时错误
      Pair<SqlValidator, SqlNode> p = parseAndValidate(factory, sql); // 解析并验证SQL
      SqlNode n = p.right; // 获取节点
      assertNotNull(n); // 断言节点不为null
    } else { // 如果不是运行时错误
      checkQueryFails(factory, StringAndPos.of(sql), expectedError); // 检查查询失败
    } // if结束
  } // checkAggFails方法结束

  public static String buildQuery(String expression) { // 静态方法,构建VALUES查询
    return "values (" + expression + ")"; // 返回VALUES查询语句
  } // buildQuery方法结束

  public static String buildQueryAgg(String expression) { // 静态方法,构建聚合查询
    return "select " + expression + " from (values (1)) as t(x) group by x"; // 返回聚合查询语句
  } // buildQueryAgg方法结束

  /**
   * Builds a query that extracts all literals as columns in an underlying
   * select.
   *
   * <p>For example,
   *
   * <blockquote>{@code 1 < 5}</blockquote>
   *
   * <p>becomes
   *
   * <blockquote>{@code SELECT p0 < p1
   * FROM (VALUES (1, 5)) AS t(p0, p1)}</blockquote>
   *
   * <p>Null literals don't have enough type information to be extracted.
   * We push down {@code CAST(NULL AS type)} but raw nulls such as
   * {@code CASE 1 WHEN 2 THEN 'a' ELSE NULL END} are left as is.
   *
   * @param factory Test factory
   * @param expression Scalar expression
   * @return Query that evaluates a scalar expression
   */
// 构建一个查询,将所有字面量提取为底层select中的列
// 例如:1 < 5 变成 SELECT p0 < p1 FROM (VALUES (1, 5)) AS t(p0, p1)
// Null字面量没有足够的类型信息可以被提取,我们下推CAST(NULL AS type)但原始null如CASE 1 WHEN 2 THEN 'a' ELSE NULL END保持不变
// 参数:测试工厂,标量表达式,返回:评估标量表达式的查询
  protected String buildQuery2(SqlTestFactory factory, String expression) { // 保护方法,构建查询2
    if (expression.matches("(?i).*(percentile_(cont|disc)|convert|sort_array|cast)\\(.*")) { // 如果表达式匹配特定函数
      // PERCENTILE_CONT requires its argument to be a literal, // PERCENTILE_CONT要求其参数是字面量
      // so converting its argument to a column will cause false errors. // 因此将其参数转换为列会导致虚假错误
      // Similarly, MSSQL-style CONVERT. // 同样,MSSQL风格的CONVERT
      return buildQuery(expression); // 返回简单查询
    } // if结束
    // "values (1 < 5)" // 注释:原始查询
    // becomes // 变成
    // "select p0 < p1 from (values (1, 5)) as t(p0, p1)" // 注释:转换后的查询
    SqlNode x; // 声明SQL节点变量
    final String sql = "values (" + expression + ")"; // 构建VALUES查询
    try { // 尝试解析SQL
      x = parseQuery(factory, sql); // 解析查询
    } catch (SqlParseException e) { // 捕获SQL解析异常
      throw TestUtil.rethrow(e); // 重新抛出异常
    } // try块结束
    final Collection<SqlNode> literalSet = new LinkedHashSet<>(); // 创建字面量集合
    x.accept( // 接受访问器遍历SQL节点
        new SqlShuttle() { // 匿名内部类:SQL节点访问器
          private final List<SqlOperator> ops = // 私有常量:操作符列表
              ImmutableList.of( // 不可变列表
                  SqlStdOperatorTable.LITERAL_CHAIN, // 字面量链操作符
                  SqlStdOperatorTable.LOCALTIME, // 本地时间操作符
                  SqlStdOperatorTable.LOCALTIMESTAMP, // 本地时间戳操作符
                  SqlStdOperatorTable.CURRENT_TIME, // 当前时间操作符
                  SqlStdOperatorTable.CURRENT_TIMESTAMP); // 当前时间戳操作符

          @Override public SqlNode visit(SqlLiteral literal) { // 覆盖visit方法,访问字面量节点
            if (!isNull(literal) // 如果不是null
                && literal.getTypeName() != SqlTypeName.SYMBOL) { // 且类型不是SYMBOL
              literalSet.add(literal); // 添加到字面量集合
            } // if结束
            return literal; // 返回字面量
          } // visit方法结束

          @Override public @Nullable SqlNode visit(SqlCall call) { // 覆盖visit方法,访问函数调用节点
            SqlOperator operator = call.getOperator(); // 获取操作符
            if (operator.getKind() == SqlKind.LAMBDA) { // 如果是Lambda表达式
              return call; // 直接返回调用节点
            } // if结束
            if (operator instanceof SqlUnresolvedFunction) { // 如果是未解析函数
              final SqlUnresolvedFunction unresolvedFunction = // 转换为未解析函数
                  (SqlUnresolvedFunction) operator;
              final SqlOperator lookup = // 查找SQL函数
                  SqlValidatorUtil.lookupSqlFunctionByID( // 通过ID查找SQL函数
                      SqlStdOperatorTable.instance(), // 标准操作符表实例
                      requireNonNull(unresolvedFunction.getSqlIdentifier()), // 函数标识符
                      unresolvedFunction.getFunctionType()); // 函数类型
              if (lookup != null) { // 如果找到函数
                operator = lookup; // 更新操作符
                call = // 创建新的调用节点
                    operator.createCall(call.getFunctionQuantifier(), // 函数量词
                        call.getParserPosition(), call.getOperandList()); // 解析位置和操作数列表
              } // if结束
            } // if结束
            if (operator == SqlStdOperatorTable.CAST // 如果是CAST操作符
                && isNull(call.operand(0))) { // 且第一个操作数是null
              literalSet.add(call); // 添加到字面量集合
              return call; // 返回调用节点
            } else if (operator == SqlStdOperatorTable.TRIM) { // 如果是TRIM操作符
              // see https://issues.apache.org/jira/projects/CALCITE/issues/CALCITE-6780 // 参考JIRA问题
              // don't extract trimmed literal for TRIM function // 不提取TRIM函数的修剪字面量
              call.operand(2).accept(this); // 只访问第三个操作数
              return call; // 返回调用节点
            } else if (ops.contains(operator)) { // 如果操作符在特殊操作符列表中
              // "Argument to function 'LOCALTIME' must be a // 函数'LOCALTIME'的参数必须是
              // literal" // 字面量
              return call; // 直接返回调用节点
            } else { // 其他情况
              return super.visit(call); // 调用父类方法
            } // if结束
          } // visit方法结束

          private boolean isNull(SqlNode sqlNode) { // 私有方法,判断节点是否为null
            return sqlNode instanceof SqlLiteral // 如果是字面量
                && ((SqlLiteral) sqlNode).getTypeName() // 且类型名
                == SqlTypeName.NULL; // 是NULL
          } // isNull方法结束
        }); // 匿名内部类结束
    final List<SqlNode> nodes = new ArrayList<>(literalSet); // 创建节点列表
    nodes.sort((o1, o2) -> { // 对节点进行排序
      final SqlParserPos pos0 = o1.getParserPosition(); // 获取第一个节点的位置
      final SqlParserPos pos1 = o2.getParserPosition(); // 获取第二个节点的位置
      int c = -Utilities.compare(pos0.getLineNum(), pos1.getLineNum()); // 比较行号(降序)
      if (c != 0) { // 如果行号不同
        return c; // 返回比较结果
      } // if结束
      return -Utilities.compare(pos0.getColumnNum(), pos1.getColumnNum()); // 比较列号(降序)
    }); // sort方法结束
    String sql2 = sql; // 复制SQL字符串
    final PairList<String, String> values = PairList.of(); // 创建键值对列表
    int p = 0; // 初始化参数计数器
    for (SqlNode literal : nodes) { // 遍历所有字面量节点
      final SqlParserPos pos = literal.getParserPosition(); // 获取节点位置
      final int start = // 计算起始索引
          SqlParserUtil.lineColToIndex( // 将行列转换为索引
              sql, pos.getLineNum(), pos.getColumnNum()); // 参数:SQL,行号,列号
      final int end = // 计算结束索引
          SqlParserUtil.lineColToIndex( // 将行列转换为索引
              sql,
              pos.getEndLineNum(), // 结束行号
              pos.getEndColumnNum()) + 1; // 结束列号+1
      String param = "p" + p++; // 生成参数名
      values.add(sql2.substring(start, end), param); // 添加到键值对列表(原始值,参数名)
      sql2 = sql2.substring(0, start) // 替换字面量为参数
          + param
          + sql2.substring(end);
    } // for循环结束
    if (values.isEmpty()) { // 如果没有字面量
      values.add("1", "p0"); // 添加默认值
    } // if结束
    return "select " // 返回SELECT查询
        + sql2.substring("values (".length(), sql2.length() - 1) // 提取表达式部分
        + " from (values (" // FROM子句
        + Util.commaList(values.leftList()) // VALUES子句
        + ")) as t(" // 表别名
        + Util.commaList(values.rightList()) // 列名列表
        + ")"; // 结束括号
  } // buildQuery2方法结束

  @Override public void forEachQuery(SqlTestFactory factory, // 实现SqlTester接口方法,对每个查询执行操作
      String expression, Consumer<String> consumer) { // 参数:测试工厂,表达式,消费者
    // Why not return a list? If there is a syntax error in the expression, the // 为什么不返回列表?如果表达式中有语法错误
    // consumer will discover it before we try to parse it to do substitutions // 消费者会在我们尝试解析它进行替换之前发现它
    // on the parse tree. // 在解析树上
    consumer.accept("values (" + expression + ")"); // 接受简单VALUES查询
    consumer.accept(buildQuery2(factory, expression)); // 接受转换后的查询
  } // forEachQuery方法结束

  @Override public void assertConvertsTo(SqlTestFactory factory, // 实现SqlTester接口方法,断言转换为指定计划
      DiffRepository diffRepos, // 参数:测试工厂,差异存储库
      String sql, // SQL语句
      String plan, // 期望的计划
      boolean trim, // 是否修剪字段
      boolean expression, // 是否是表达式
      boolean decorrelate) { // 是否去相关
    if (expression) { // 如果是表达式
      assertExprConvertsTo(factory, diffRepos, sql, plan); // 断言表达式转换
    } else { // 如果是SQL查询
      assertSqlConvertsTo(factory, diffRepos, sql, plan, trim, decorrelate); // 断言SQL转换
    } // if结束
  } // assertConvertsTo方法结束

  private void assertExprConvertsTo(SqlTestFactory factory, // 私有方法,断言表达式转换为指定计划
      DiffRepository diffRepos, String expr, String plan) { // 参数:测试工厂,差异存储库,表达式,期望计划
    String expr2 = diffRepos.expand("sql", expr); // 展开表达式
    RexNode rex = convertExprToRex(factory, expr2); // 将表达式转换为Rex节点
    assertNotNull(rex); // 断言Rex节点不为null
    // NOTE jvs 28-Mar-2006:  insert leading newline so // 注:jvs 2006年3月28日:插入前导换行符以便
    // that plans come out nicely stacked instead of first // 计划可以很好地堆叠显示,而不是第一行
    // line immediately after CDATA start // 紧跟在CDATA开始之后
    String actual = NL + rex + NL; // 构建实际计划字符串
    diffRepos.assertEquals("plan", plan, actual); // 断言计划匹配
  } // assertExprConvertsTo方法结束

  private void assertSqlConvertsTo(SqlTestFactory factory, // 私有方法,断言SQL转换为指定计划
      DiffRepository diffRepos, String sql, String plan, // 参数:测试工厂,差异存储库,SQL,期望计划
      boolean trim, // 是否修剪字段
      boolean decorrelate) { // 是否去相关
    String sql2 = diffRepos.expand("sql", sql); // 展开SQL
    final Pair<SqlValidator, RelRoot> pair = // 声明键值对变量
        convertSqlToRel2(factory, sql2, decorrelate, trim); // 将SQL转换为关系代数
    final RelRoot root = pair.right; // 获取关系根节点
    final SqlValidator validator = pair.left; // 获取验证器
    RelNode rel = root.project(); // 获取投影节点

    assertNotNull(rel); // 断言关系节点不为null
    assertThat(rel, relIsValid()); // 断言关系节点有效

    if (trim) { // 如果需要修剪字段
      final RelBuilder relBuilder = // 创建关系构建器
          RelFactories.LOGICAL_BUILDER.create(rel.getCluster(), null); // 使用逻辑构建器工厂
      final RelFieldTrimmer trimmer = // 创建字段修剪器
          createFieldTrimmer(validator, relBuilder); // 调用createFieldTrimmer方法
      rel = trimmer.trim(rel); // 修剪关系节点
      assertNotNull(rel); // 断言关系节点不为null
      assertThat(rel, relIsValid()); // 断言关系节点有效
    } // if结束

    // NOTE jvs 28-Mar-2006:  insert leading newline so // 注:jvs 2006年3月28日:插入前导换行符以便
    // that plans come out nicely stacked instead of first // 计划可以很好地堆叠显示,而不是第一行
    // line immediately after CDATA start // 紧跟在CDATA开始之后
    String actual = NL + RelOptUtil.toString(rel); // 构建实际计划字符串
    diffRepos.assertEquals("plan", plan, actual); // 断言计划匹配
  } // assertSqlConvertsTo方法结束

  private RexNode convertExprToRex(SqlTestFactory factory, String expr) { // 私有方法,将表达式转换为Rex节点
    requireNonNull(expr, "expr"); // 断言表达式不为null
    final SqlNode sqlQuery; // 声明SQL查询节点变量
    try { // 尝试解析表达式
      sqlQuery = parseExpression(factory, expr); // 解析表达式
    } catch (RuntimeException | Error e) { // 捕获运行时异常或错误
      throw e; // 重新抛出
    } catch (Exception e) { // 捕获其他异常
      throw TestUtil.rethrow(e); // 重新抛出
    } // try块结束

    final SqlToRelConverter converter = factory.createSqlToRelConverter(); // 创建SQL到关系代数转换器
    final SqlValidator validator = requireNonNull(converter.validator); // 获取验证器
    final SqlNode validatedQuery = validator.validate(sqlQuery); // 验证SQL查询
    return converter.convertExpression(validatedQuery); // 转换表达式为Rex节点
  } // convertExprToRex方法结束

  @Override public Pair<SqlValidator, RelRoot> convertSqlToRel2( // 实现SqlTester接口方法,将SQL转换为关系代数
      SqlTestFactory factory, String sql, boolean decorrelate, // 参数:测试工厂,SQL,是否去相关
      boolean trim) { // 是否修剪字段
    requireNonNull(sql, "sql"); // 断言SQL不为null
    final SqlNode sqlQuery; // 声明SQL查询节点变量
    try { // 尝试解析SQL
      sqlQuery = parseQuery(factory, sql); // 解析SQL查询
    } catch (RuntimeException | Error e) { // 捕获运行时异常或错误
      throw e; // 重新抛出
    } catch (Exception e) { // 捕获其他异常
      throw TestUtil.rethrow(e); // 重新抛出
    } // try块结束
    final SqlToRelConverter converter = factory.createSqlToRelConverter(); // 创建SQL到关系代数转换器
    final SqlValidator validator = requireNonNull(converter.validator); // 获取验证器

    final SqlNode validatedQuery = validator.validate(sqlQuery); // 验证SQL查询
    RelRoot root = // 声明关系根节点
        converter.convertQuery(validatedQuery, false, true); // 转换查询为关系代数
    requireNonNull(root, "root"); // 断言根节点不为null
    if (decorrelate || trim) { // 如果需要去相关或修剪
      root = root.withRel(converter.flattenTypes(root.rel, true)); // 展平类型
    } // if结束
    if (decorrelate) { // 如果需要去相关
      root = root.withRel(converter.decorrelate(sqlQuery, root.rel)); // 去相关
    } // if结束
    if (trim) { // 如果需要修剪字段
      root = root.withRel(converter.trimUnusedFields(true, root.rel)); // 修剪未使用的字段
    } // if结束
    return Pair.of(validator, root); // 返回验证器和根节点的键值对
  } // convertSqlToRel2方法结束

  @Override public RelNode trimRelNode(SqlTestFactory factory, // 实现SqlTester接口方法,修剪关系节点
      RelNode relNode) { // 参数:测试工厂,关系节点
    final SqlToRelConverter converter = factory.createSqlToRelConverter(); // 创建SQL到关系代数转换器
    RelNode r2 = converter.flattenTypes(relNode, true); // 展平类型
    return converter.trimUnusedFields(true, r2); // 修剪未使用的字段
  } // trimRelNode方法结束

  /**
   * Creates a RelFieldTrimmer.
   *
   * @param validator Validator
   * @param relBuilder Builder
   * @return Field trimmer
   */
  // 创建RelFieldTrimmer字段修剪器
  // 参数:验证器,关系构建器,返回:字段修剪器
  public RelFieldTrimmer createFieldTrimmer(SqlValidator validator, // 公共方法,创建字段修剪器
      RelBuilder relBuilder) { // 参数:验证器,关系构建器
    return new RelFieldTrimmer(validator, relBuilder); // 返回新的字段修剪器实例
  } // createFieldTrimmer方法结束
} // 类定义结束
