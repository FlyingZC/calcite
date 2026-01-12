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
package org.apache.calcite.sql.parser; // 声明包名，该类属于org.apache.calcite.sql.parser包，是SQL解析器相关的测试工具类

import org.apache.calcite.avatica.util.Casing; // 导入Casing类，用于处理标识符的大小写转换规则
import org.apache.calcite.avatica.util.Quoting; // 导入Quoting类，用于定义标识符的引用方式（如双引号、反引号等）
import org.apache.calcite.sql.SqlDialect; // 导入SqlDialect类，用于表示SQL方言，不同数据库的SQL语法差异
import org.apache.calcite.sql.SqlNode; // 导入SqlNode类，表示SQL语法树的抽象节点，是所有SQL节点的基类
import org.apache.calcite.sql.SqlNodeList; // 导入SqlNodeList类，表示SQL节点的列表，用于存储多个SqlNode对象
import org.apache.calcite.sql.test.SqlTestFactory; // 导入SqlTestFactory类，用于创建SQL测试相关的对象（如解析器、验证器等）
import org.apache.calcite.sql.validate.SqlConformance; // 导入SqlConformance接口，定义SQL符合性规范，控制SQL语法的严格程度
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SqlConformanceEnum枚举，提供预定义的SQL符合性规范实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或参数
import org.hamcrest.Matcher; // 导入Matcher接口，用于创建匹配器，对解析结果进行断言验证

import java.util.List; // 导入List接口，用于存储集合数据
import java.util.function.Consumer; // 导入Consumer函数式接口，用于消费对象（接受一个参数不返回结果）
import java.util.function.UnaryOperator; // 导入UnaryOperator函数式接口，用于对单个参数进行操作并返回结果

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于参数非空校验

/**
 * Helper class for building fluent parser tests such as
 * {@code sql("values 1").ok();}.
 * // 这是一个用于构建流式解析器测试的辅助类，支持链式调用，例如sql("values 1").ok()
 * // 该类封装了SQL解析测试的各种配置和断言方法，使得测试代码更加简洁易读
 * // 主要功能包括：设置SQL语句、配置解析器、验证解析结果、检查错误等
 */
public class SqlParserFixture { // 定义SqlParserFixture类，这是一个SQL解析器测试的fixture（测试夹具）类
  public static final SqlTestFactory FACTORY = // 声明一个公共静态常量FACTORY，类型为SqlTestFactory，用于创建SQL测试工厂
      SqlTestFactory.INSTANCE.withParserConfig(c -> // 获取默认的SqlTestFactory实例，并通过withParserConfig方法配置解析器
          c.withQuoting(Quoting.DOUBLE_QUOTE) // 设置标识符引用方式为双引号，即标识符用双引号包裹
              .withUnquotedCasing(Casing.TO_UPPER) // 设置未加引号的标识符转换为大写
              .withQuotedCasing(Casing.UNCHANGED) // 设置加引号的标识符保持原样不转换大小写
              .withConformance(SqlConformanceEnum.DEFAULT)); // 设置SQL符合性规范为默认值，控制SQL语法兼容性

  public static final SqlParserFixture DEFAULT = // 声明一个公共静态常量DEFAULT，类型为SqlParserFixture，表示默认的解析器测试fixture
      new SqlParserFixture(FACTORY, StringAndPos.of("?"), false, // 创建SqlParserFixture实例，使用FACTORY工厂，SQL为"?"，不是表达式
          SqlParserTest.TesterImpl.DEFAULT, null, true, parser -> { // 使用默认的测试器实现，方言为null，转换为Linux换行符，解析器检查器为空操作
      });

  public final SqlTestFactory factory; // 声明公共常量factory，类型为SqlTestFactory，用于创建SQL解析器等测试对象，是整个测试的核心工厂
  public final StringAndPos sap; // 声明公共常量sap，类型为StringAndPos，封装了SQL字符串及其位置信息（包括插入符位置，用于定位错误）
  public final boolean expression; // 声明公共常量expression，类型为boolean，标识当前测试的是表达式还是完整SQL语句，true表示表达式
  public final SqlParserTest.Tester tester; // 声明公共常量tester，类型为SqlParserTest.Tester，测试器接口，负责执行实际的测试验证逻辑
  public final boolean convertToLinux; // 声明公共常量convertToLinux，类型为boolean，标识是否将Windows换行符(CRLF)转换为Linux换行符(LF)，默认为true
  public final @Nullable SqlDialect dialect; // 声明公共常量dialect，类型为SqlDialect（可为null），表示SQL方言，用于处理不同数据库的SQL语法差异
  public final Consumer<SqlParser> parserChecker; // 声明公共常量parserChecker，类型为Consumer<SqlParser>，解析器检查器，用于对解析后的SqlParser对象进行自定义检查

  SqlParserFixture(SqlTestFactory factory, StringAndPos sap, boolean expression, // 私有构造方法，创建SqlParserFixture实例
      SqlParserTest.Tester tester, @Nullable SqlDialect dialect, // 参数：factory-测试工厂，sap-SQL字符串及位置，expression-是否为表达式
      boolean convertToLinux, Consumer<SqlParser> parserChecker) { // 参数：tester-测试器，dialect-SQL方言，convertToLinux-是否转换换行符，parserChecker-解析器检查器
    this.factory = requireNonNull(factory, "factory"); // 使用requireNonNull校验factory参数非空，如果为null抛出NullPointerException
    this.sap = requireNonNull(sap, "sap"); // 使用requireNonNull校验sap参数非空，如果为null抛出NullPointerException
    this.expression = expression; // 赋值expression字段，标识当前测试的是表达式还是完整SQL语句
    this.tester = requireNonNull(tester, "tester"); // 使用requireNonNull校验tester参数非空，如果为null抛出NullPointerException
    this.dialect = dialect; // 赋值dialect字段，表示SQL方言，可能为null
    this.convertToLinux = convertToLinux; // 赋值convertToLinux字段，标识是否需要转换Windows换行符为Linux换行符
    this.parserChecker = requireNonNull(parserChecker, "parserChecker"); // 使用requireNonNull校验parserChecker参数非空
  }

  public SqlParserFixture same() { // 声明公共方法same，用于验证解析后的SQL与原始SQL完全相同
    return compare(sap.sql); // 调用compare方法，传入原始SQL字符串作为期望值，验证解析结果与原始SQL一致
  }

  public SqlParserFixture ok(String expected) { // 声明公共方法ok，用于验证解析结果与给定的期望字符串匹配
    if (expected.equals(sap.sql)) { // 判断期望字符串是否与原始SQL相同
      throw new AssertionError("you should call same()"); // 如果相同，抛出断言错误，提示应该调用same()方法而不是ok()方法
    }
    return compare(expected); // 调用compare方法，传入期望字符串，进行解析结果的比较验证
  }

  public SqlParserFixture compare(String expected) { // 声明公共方法compare，执行实际的解析和比较逻辑
    final UnaryOperator<String> converter = SqlParserTest.linux(convertToLinux); // 创建字符串转换器，根据convertToLinux决定是否转换换行符
    if (expression) { // 判断当前测试的是否为表达式
      tester.checkExp(factory, sap, converter, expected, parserChecker); // 如果是表达式，调用tester的checkExp方法验证表达式解析结果
    } else { // 如果不是表达式（即完整SQL语句）
      tester.check(factory, sap, dialect, converter, expected, parserChecker); // 调用tester的check方法验证SQL语句解析结果，传入方言参数
    }
    return this; // 返回当前对象，支持链式调用
  }

  public SqlParserFixture fails(String expectedMsgPattern) { // 声明公共方法fails，用于验证解析应该失败并抛出特定错误消息
    if (expression) { // 判断当前测试的是否为表达式
      tester.checkExpFails(factory, sap, expectedMsgPattern); // 如果是表达式，调用tester的checkExpFails方法验证表达式解析失败
    } else { // 如果不是表达式（即完整SQL语句）
      tester.checkFails(factory, sap, false, expectedMsgPattern); // 调用tester的checkFails方法验证SQL语句解析失败，第二个参数false表示不期望成功
    }
    return this; // 返回当前对象，支持链式调用
  }

  public SqlParserFixture hasWarning(Consumer<List<? extends Throwable>> messageMatcher) { // 声明公共方法hasWarning，用于验证解析过程中产生的警告
    final Consumer<SqlParser> parserConsumer = parser -> // 创建一个SqlParser的消费者
        messageMatcher.accept(parser.getWarnings()); // 在消费者中，获取解析器的警告列表，并传递给messageMatcher进行匹配验证
    return new SqlParserFixture(factory, sap, expression, tester, dialect, // 创建新的SqlParserFixture实例，使用新的parserConsumer替换原有的parserChecker
        convertToLinux, parserConsumer);
  }

  public SqlParserFixture node(Matcher<SqlNode> matcher) { // 声明公共方法node，用于验证解析后的SqlNode是否匹配给定的匹配器
    tester.checkNode(factory, sap, matcher); // 调用tester的checkNode方法，使用Hamcrest匹配器验证解析后的SqlNode
    return this; // 返回当前对象，支持链式调用
  }

  /**
   * Changes the SQL.
   * // 更改SQL语句，返回一个新的fixture实例
   */
  public SqlParserFixture sql(String sql) { // 声明公共方法sql，用于设置新的SQL语句
    if (sql.equals(this.sap.addCarets())) { // 判断新SQL是否与当前SQL（添加插入符后）相同
      return this; // 如果相同，直接返回当前对象，不创建新实例
    }
    StringAndPos sap = StringAndPos.of(sql); // 创建新的StringAndPos对象，封装新的SQL字符串
    return new SqlParserFixture(factory, sap, expression, tester, dialect, // 创建新的SqlParserFixture实例，使用新的sap，其他参数保持不变
        convertToLinux, parserChecker);
  }

  /**
   * Flags that this is an expression, not a whole query.
   * // 标识这是一个表达式，而不是完整的查询语句
   */
  public SqlParserFixture expression() { // 声明公共方法expression，将当前测试标记为表达式测试
    return expression(true); // 调用expression(boolean)方法，传入true表示是表达式
  }

  /**
   * Sets whether this is an expression (as opposed to a whole query).
   * // 设置这是否为表达式（相对于完整的查询语句）
   */
  public SqlParserFixture expression(boolean expression) { // 声明公共方法expression，设置expression标志
    if (this.expression == expression) { // 判断当前expression值是否与传入的值相同
      return this; // 如果相同，直接返回当前对象，不创建新实例
    }
    return new SqlParserFixture(factory, sap, expression, tester, dialect, // 创建新的SqlParserFixture实例，使用新的expression值
        convertToLinux, parserChecker);
  }

  /**
   * Creates an instance of helper class {@link SqlParserListFixture} to test parsing a
   * list of statements.
   * // 创建辅助类SqlParserListFixture的实例，用于测试解析语句列表
   */
  protected SqlParserListFixture list() { // 声明保护方法list，创建SqlParserListFixture实例用于测试多条SQL语句的解析
    return new SqlParserListFixture(factory, tester, dialect, convertToLinux, sap); // 创建SqlParserListFixture实例，传入所有相关参数
  }

  public SqlParserFixture withDialect(SqlDialect dialect) { // 声明公共方法withDialect，设置SQL方言
    if (dialect == this.dialect) { // 判断新方言是否与当前方言相同
      return this; // 如果相同，直接返回当前对象，不创建新实例
    }
    SqlTestFactory factory = // 创建新的SqlTestFactory实例
        this.factory.withParserConfig(dialect::configureParser); // 使用方言的configureParser方法配置解析器
    return new SqlParserFixture(factory, sap, expression, tester, dialect, // 创建新的SqlParserFixture实例，使用新的factory和dialect
        convertToLinux, parserChecker);
  }

  /**
   * Creates a copy of this fixture with a new test factory.
   * // 使用新的测试工厂创建此fixture的副本
   */
  public SqlParserFixture withFactory(UnaryOperator<SqlTestFactory> transform) { // 声明公共方法withFactory，通过转换函数创建新的测试工厂
    final SqlTestFactory factory = transform.apply(this.factory); // 应用转换函数到当前factory，生成新的factory
    if (factory == this.factory) { // 判断新factory是否与当前factory相同
      return this; // 如果相同，直接返回当前对象，不创建新实例
    }
    return new SqlParserFixture(factory, sap, expression, tester, dialect, // 创建新的SqlParserFixture实例，使用新的factory
        convertToLinux, parserChecker);
  }

  public SqlParserFixture withConfig(UnaryOperator<SqlParser.Config> transform) { // 声明公共方法withConfig，通过转换函数配置解析器
    return withFactory(f -> f.withParserConfig(transform)); // 调用withFactory方法，传入转换函数，该函数对解析器配置进行转换
  }

  public SqlParserFixture withConformance(SqlConformance conformance) { // 声明公共方法withConformance，设置SQL符合性规范
    return withConfig(c -> c.withConformance(conformance)); // 调用withConfig方法，传入转换函数，该函数设置解析器配置的符合性规范
  }

  public SqlParserFixture withTester(SqlParserTest.Tester tester) { // 声明公共方法withTester，设置测试器
    if (tester == this.tester) { // 判断新测试器是否与当前测试器相同
      return this; // 如果相同，直接返回当前对象，不创建新实例
    }
    return new SqlParserFixture(factory, sap, expression, tester, dialect, // 创建新的SqlParserFixture实例，使用新的tester
        convertToLinux, parserChecker);
  }

  /**
   * Sets whether to convert actual strings to Linux (converting Windows
   * CR-LF line endings to Linux LF) before comparing them to expected.
   * Default is true.
   * // 设置是否将实际字符串转换为Linux格式（将Windows的CR-LF行结束符转换为Linux的LF），然后再与期望值比较
   * // 默认值为true
   */
  public SqlParserFixture withConvertToLinux(boolean convertToLinux) { // 声明公共方法withConvertToLinux，设置是否转换换行符
    if (convertToLinux == this.convertToLinux) { // 判断新值是否与当前值相同
      return this; // 如果相同，直接返回当前对象，不创建新实例
    }
    return new SqlParserFixture(factory, sap, expression, tester, dialect, // 创建新的SqlParserFixture实例，使用新的convertToLinux值
        convertToLinux, parserChecker);
  }

  public SqlParser parser() { // 声明公共方法parser，创建并返回SQL解析器
    return factory.createParser(sap.addCarets()); // 使用工厂创建解析器，传入添加插入符后的SQL字符串
  }

  public SqlNode node() { // 声明公共方法node，解析SQL并返回SqlNode对象
    return ((SqlParserTest.TesterImpl) tester) // 将tester强制转换为TesterImpl类型
        .parseStmtAndHandleEx(factory, sap.addCarets(), parser -> { // 调用parseStmtAndHandleEx方法解析SQL语句并处理异常
        }); // 传入空操作的parser消费者
  }

  public SqlNodeList nodeList() { // 声明公共方法nodeList，解析SQL并返回SqlNodeList对象（用于多条语句）
    return ((SqlParserTest.TesterImpl) tester) // 将tester强制转换为TesterImpl类型
        .parseStmtsAndHandleEx(factory, sap.addCarets()); // 调用parseStmtsAndHandleEx方法解析多条SQL语句并处理异常
  }
} // 类定义结束
