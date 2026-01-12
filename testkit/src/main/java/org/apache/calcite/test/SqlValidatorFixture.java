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
package org.apache.calcite.test; // 包声明：该类属于 org.apache.calcite.test 包，用于测试相关功能

// 导入 Calcite 核心类型相关类
import org.apache.calcite.avatica.util.Casing; // 字母大小写转换枚举，用于标识符的大小写处理策略
import org.apache.calcite.avatica.util.Quoting; // 引用标识符的引号类型枚举（如反引号、双引号等）
import org.apache.calcite.config.CalciteConnectionProperty; // Calcite 连接属性枚举，定义连接时可配置的各种属性
import org.apache.calcite.config.Lex; // 词法配置类，封装了大小写敏感、引号类型等词法相关配置
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口，表示 Calcite 中的数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 关系数据类型字段接口，表示数据类型中的单个字段
// 导入 SQL 相关类
import org.apache.calcite.sql.SqlCall; // SQL 调用节点类，表示函数调用或运算符调用的抽象语法树节点
import org.apache.calcite.sql.SqlCollation; // SQL 排序规则类，表示字符的排序和比较规则
import org.apache.calcite.sql.SqlIntervalLiteral; // SQL 时间间隔字面量类，表示如 '1' DAY 这样的间隔值
import org.apache.calcite.sql.SqlNode; // SQL 节点基类，是所有 SQL 抽象语法树节点的父类
import org.apache.calcite.sql.SqlOperator; // SQL 运算符接口，表示 SQL 中的运算符（如 +、-、*、/ 等）
import org.apache.calcite.sql.SqlOperatorTable; // SQL 运算符表接口，用于查找和获取 SQL 运算符
import org.apache.calcite.sql.SqlSelect; // SQL SELECT 语句类，表示 SELECT 查询语句的抽象语法树节点
import org.apache.calcite.sql.dialect.AnsiSqlDialect; // ANSI SQL 方言类，用于生成符合 ANSI 标准的 SQL
// 导入 SQL 解析器相关类
import org.apache.calcite.sql.parser.SqlParser; // SQL 解析器类，负责将 SQL 字符串解析为抽象语法树
import org.apache.calcite.sql.parser.SqlParserUtil; // SQL 解析器工具类，提供解析器相关的辅助方法
import org.apache.calcite.sql.parser.StringAndPos; // 字符串和位置类，封装了字符串及光标位置信息，用于错误定位
// 导入 SQL 测试相关类
import org.apache.calcite.sql.test.AbstractSqlTester; // 抽象 SQL 测试器类，提供 SQL 测试的基础功能
import org.apache.calcite.sql.test.SqlTestFactory; // SQL 测试工厂类，用于创建测试所需的各类配置对象
import org.apache.calcite.sql.test.SqlTester; // SQL 测试器接口，定义了 SQL 测试的基本行为
import org.apache.calcite.sql.test.SqlTests; // SQL 测试工具类，提供测试相关的静态辅助方法
// 导入 SQL 验证器相关类
import org.apache.calcite.sql.validate.SqlConformance; // SQL 兼容性接口，定义 SQL 语法和语义的兼容性级别
import org.apache.calcite.sql.validate.SqlConformanceEnum; // SQL 兼容性枚举，提供预定义的兼容性级别（如 STRICT、LENIENT 等）
import org.apache.calcite.sql.validate.SqlMonotonicity; // SQL 单调性枚举，表示值的单调递增/递减特性
import org.apache.calcite.sql.validate.SqlValidator; // SQL 验证器接口，负责验证 SQL 语句的语法和语义正确性
import org.apache.calcite.sql.validate.SqlValidatorNamespace; // SQL 验证器命名空间接口，表示 SQL 中的命名空间（如表、子查询）
import org.apache.calcite.sql.validate.SqlValidatorUtil; // SQL 验证器工具类，提供验证相关的静态辅助方法
// 导入测试目录相关类
import org.apache.calcite.test.catalog.MockCatalogReaderExtended; // 扩展的模拟目录读取器类，用于测试时提供扩展的元数据
// 导入工具类
import org.apache.calcite.util.TestUtil; // 测试工具类，提供测试相关的辅助方法
import org.apache.calcite.util.Util; // 通用工具类，提供各种静态辅助方法

// 导入 Hamcrest 断言库相关类
import org.hamcrest.Matcher; // 匹配器接口，用于灵活的断言匹配

// 导入 Java 标准库相关类
import java.nio.charset.Charset; // 字符集类，表示字符编码（如 UTF-8、GBK 等）
import java.util.List; // 列表接口，表示有序的元素集合
import java.util.function.Consumer; // 消费者函数式接口，表示接受单个参数且无返回值的操作
import java.util.function.UnaryOperator; // 一元运算符函数式接口，表示接受单个参数并返回同类型结果的操作

// 导入 Google Guava 预条件检查类
import static com.google.common.base.Preconditions.checkArgument; // 导入静态方法，用于检查参数是否满足条件

// 导入 Calcite SQL 工具类
import static org.apache.calcite.sql.SqlUtil.stripAs; // 导入静态方法，用于移除 SQL 节点中的 AS 别名

// 导入 Hamcrest 核心匹配器
import static org.hamcrest.CoreMatchers.is; // 导入静态匹配器，用于检查值是否相等
import static org.hamcrest.CoreMatchers.notNullValue; // 导入静态匹配器，用于检查值是否非空
import static org.hamcrest.MatcherAssert.assertThat; // 导入静态断言方法，用于执行匹配器断言
import static org.hamcrest.Matchers.hasToString; // 导入静态匹配器，用于检查对象的 toString() 结果

// 导入 JUnit 断言类
import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入静态断言方法，用于检查对象是否非空

// 导入 Java 工具类
import static java.util.Objects.requireNonNull; // 导入静态方法，用于检查对象是否非空

/**
 * 用于测试 SQL 验证器的测试夹具类。
 *
 * <p>该类提供流畅的 API，允许通过链式方法调用来编写测试用例。
 *
 * <p>该类是不可变的。如果有两个测试用例需要相似的设置（例如，相同的 SQL 表达式和解析器配置），
 * 可以安全地使用同一个夹具对象作为两个测试的起点。
 *
 * <p>该类是 Calcite 测试框架的核心组件之一，主要用于验证 SQL 语句的正确性，
 * 包括语法检查、语义检查、类型推导、字段来源追踪等功能。通过该类可以方便地：
 * 1. 配置 SQL 解析器（大小写敏感、引号类型等）
 * 2. 配置 SQL 验证器（类型强制转换、运算符查找等）
 * 3. 执行 SQL 验证并检查结果（类型、错误消息、重写等）
 * 4. 验证 SQL 语义（单调性、聚合性、字段来源等）
 *
 * <p>典型用法示例：
 * <pre>
 *   // 测试 SQL 验证成功
 *   sql("SELECT empno FROM emp").ok();
 *
 *   // 测试 SQL 验证失败并检查错误消息
 *   sql("SELECT invalid_column FROM emp").fails("Column 'INVALID_COLUMN' not found");
 *
 *   // 测试返回类型
 *   sql("SELECT empno, name FROM emp").type("{EMPNO INTEGER NOT NULL, NAME VARCHAR(10) NOT NULL}");
 * </pre>
 *
 * <p>该类的设计遵循不可变对象模式，所有配置方法都返回新的实例，
 * 这使得测试代码既安全又易于理解和维护。
 */
public class SqlValidatorFixture { // 定义 SQL 验证器测试夹具类，提供 SQL 验证测试的流畅 API
  // 成员变量：SQL 测试器，负责执行实际的 SQL 测试操作
  public final SqlTester tester; // SQL 测试器实例，提供验证、断言等核心测试功能
  // 成员变量：SQL 测试工厂，负责创建测试所需的各类配置对象
  public final SqlTestFactory factory; // SQL 测试工厂实例，用于创建解析器、验证器等配置
  // 成员变量：字符串和位置对象，封装了 SQL 字符串及光标位置（用于错误定位）
  public final StringAndPos sap; // 字符串和位置对象，存储待测试的 SQL 及光标位置
  // 成员变量：标识是否为表达式而非完整查询
  public final boolean expression; // 布尔值，true 表示 SQL 是表达式，false 表示是完整查询
  // 成员变量：标识失败位置是否是整个查询或表达式
  public final boolean whole; // 布尔值，true 表示失败位置是整个查询/表达式，false 表示是特定位置

  /**
   * 创建 SqlValidatorFixture 实例的构造方法。
   *
   * <p>该构造方法是 protected 的，通常通过工厂方法或配置方法创建实例。
   * 由于该类是不可变的，所有字段都是 final 的，一旦创建就不能修改。
   *
   * @param tester     SQL 测试器实例，负责执行实际的验证和断言操作
   * @param factory    SQL 测试工厂实例，用于创建解析器、验证器等配置对象
   * @param sap        字符串和位置对象，封装了待测试的 SQL 字符串及光标位置
   * @param expression 布尔值，true 表示 SQL 是表达式，false 表示是完整查询
   * @param whole      布尔值，true 表示失败位置是整个查询/表达式，false 表示是特定位置
   */
  protected SqlValidatorFixture(SqlTester tester, SqlTestFactory factory,
      StringAndPos sap, boolean expression, boolean whole) { // 构造方法，初始化所有 final 成员变量
    this.tester = tester; // 保存 SQL 测试器实例
    this.factory = factory; // 保存 SQL 测试工厂实例
    this.expression = expression; // 保存是否为表达式的标志
    this.sap = sap; // 保存字符串和位置对象
    this.whole = whole; // 保存是否为整体失败的标志
  }

  /**
   * 使用转换函数创建新的 SqlValidatorFixture 实例，该实例使用转换后的 SQL 测试器。
   *
   * <p>该方法遵循不可变对象模式，返回一个新的实例而不是修改当前实例。
   * 这允许在测试中链式调用多个配置方法。
   *
   * <p>使用场景：当需要自定义测试器的行为时，可以使用此方法。
   * 例如，替换测试器的验证逻辑或断言行为。
   *
   * @param transform 一元运算符，接受当前的 SqlTester 并返回转换后的 SqlTester
   * @return 新的 SqlValidatorFixture 实例，使用转换后的测试器
   */
  public SqlValidatorFixture withTester(UnaryOperator<SqlTester> transform) { // 配置方法：使用转换后的测试器创建新实例
    final SqlTester tester = transform.apply(this.tester); // 应用转换函数，得到新的测试器
    return new SqlValidatorFixture(tester, factory, sap, expression, whole); // 返回新实例，其他参数保持不变
  }

  /**
   * 使用转换函数创建新的 SqlValidatorFixture 实例，该实例使用转换后的 SQL 测试工厂。
   *
   * <p>该方法遵循不可变对象模式，返回一个新的实例而不是修改当前实例。
   * 这是配置测试环境的主要方法之一，因为工厂控制了解析器、验证器等核心组件的创建。
   *
   * <p>使用场景：当需要自定义工厂的行为时，可以使用此方法。
   * 例如，替换目录读取器、修改连接配置等。
   *
   * @param transform 一元运算符，接受当前的 SqlTestFactory 并返回转换后的 SqlTestFactory
   * @return 新的 SqlValidatorFixture 实例，使用转换后的工厂
   */
  public SqlValidatorFixture withFactory(
      UnaryOperator<SqlTestFactory> transform) { // 配置方法：使用转换后的工厂创建新实例
    final SqlTestFactory factory = transform.apply(this.factory); // 应用转换函数，得到新的工厂
    return new SqlValidatorFixture(tester, factory, sap, expression, whole); // 返回新实例，其他参数保持不变
  }

  /**
   * 使用转换函数创建新的 SqlValidatorFixture 实例，该实例使用转换后的 SQL 解析器配置。
   *
   * <p>该方法是一个便捷方法，内部调用 withFactory，并指定转换解析器配置。
   * 解析器配置包括：大小写敏感、引号类型、词法规则等。
   *
   * <p>使用场景：当需要自定义解析器的词法行为时，可以使用此方法。
   * 例如，设置标识符的大小写敏感、修改引号类型等。
   *
   * @param transform 一元运算符，接受当前的 SqlParser.Config 并返回转换后的配置
   * @return 新的 SqlValidatorFixture 实例，使用转换后的解析器配置
   */
  public SqlValidatorFixture withParserConfig(
      UnaryOperator<SqlParser.Config> transform) { // 配置方法：使用转换后的解析器配置创建新实例
    return withFactory(f -> f.withParserConfig(transform)); // 委托给 withFactory，转换工厂的解析器配置
  }

  /**
   * 获取当前实例使用的 SQL 解析器配置。
   *
   * <p>该方法用于访问当前的解析器配置，可以用于检查配置或基于当前配置创建新的配置。
   *
   * @return 当前的 SQL 解析器配置对象
   */
  public SqlParser.Config parserConfig() { // 获取方法：返回当前使用的解析器配置
    return factory.parserConfig(); // 从工厂获取解析器配置
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的 SQL 查询字符串。
   *
   * <p>该方法将 SQL 标记为完整查询（expression = false），而不是表达式。
   * 这是测试 SQL 查询的主要方法。
   *
   * <p>使用场景：当需要测试完整的 SQL 查询语句（如 SELECT、INSERT 等）时使用。
   *
   * @param sql SQL 查询字符串
   * @return 新的 SqlValidatorFixture 实例，使用指定的 SQL 查询
   */
  public SqlValidatorFixture withSql(String sql) { // 配置方法：使用指定的 SQL 查询创建新实例
    StringAndPos sap = StringAndPos.of(sql); // 创建字符串和位置对象，光标位置默认为 -1
    return new SqlValidatorFixture(tester, factory, sap, false, false); // 返回新实例，标记为查询而非表达式
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的 SQL 表达式字符串。
   *
   * <p>该方法将 SQL 标记为表达式（expression = true），而不是完整查询。
   * 表达式会被包装在 SELECT 语句中进行验证。
   *
   * <p>使用场景：当需要测试 SQL 表达式（如 1+2、'abc' || 'def' 等）时使用。
   *
   * @param sql SQL 表达式字符串
   * @return 新的 SqlValidatorFixture 实例，使用指定的 SQL 表达式
   */
  public SqlValidatorFixture withExpr(String sql) { // 配置方法：使用指定的 SQL 表达式创建新实例
    StringAndPos sap = StringAndPos.of(sql); // 创建字符串和位置对象，光标位置默认为 -1
    return new SqlValidatorFixture(tester, factory, sap, true, false); // 返回新实例，标记为表达式而非查询
  }

  /**
   * 根据当前配置生成用于测试的 SQL 字符串和位置对象。
   *
   * <p>如果当前 SQL 是表达式，该方法会将其包装在 SELECT 语句中，并可选择添加插入符（^）标记。
   * 如果当前 SQL 是查询，则直接返回原始的字符串和位置对象。
   *
   * <p>插入符用于标记错误位置，当 withCaret 为 true 时会添加插入符。
   *
   * @param withCaret 是否在 SQL 中添加插入符标记
   * @return 字符串和位置对象，用于测试
   */
  public StringAndPos toSql(boolean withCaret) { // 转换方法：根据配置生成测试用的 SQL 字符串和位置
    return expression // 如果是表达式
        ? StringAndPos.of(AbstractSqlTester.buildQuery(sap.addCarets())) // 将表达式包装为 SELECT 查询
        : sap; // 如果是查询，直接返回原始的字符串和位置对象
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用扩展的目录读取器。
   *
   * <p>扩展的目录读取器提供了更多的元数据信息，用于测试更复杂的场景。
   *
   * <p>使用场景：当测试需要访问扩展的目录信息时使用。
   *
   * @return 新的 SqlValidatorFixture 实例，使用扩展的目录读取器
   */
  public SqlValidatorFixture withExtendedCatalog() { // 配置方法：使用扩展的目录读取器创建新实例
    return withCatalogReader(MockCatalogReaderExtended::create); // 委托给 withCatalogReader，使用扩展目录读取器工厂
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的目录读取器工厂。
   *
   * <p>目录读取器负责提供数据库元数据（表、列、类型等）。
   * 通过自定义目录读取器，可以模拟不同的数据库环境。
   *
   * <p>使用场景：当需要自定义目录读取器以模拟特定的数据库环境时使用。
   *
   * @param catalogReaderFactory 目录读取器工厂函数
   * @return 新的 SqlValidatorFixture 实例，使用指定的目录读取器
   */
  public SqlValidatorFixture withCatalogReader(
      SqlTestFactory.CatalogReaderFactory catalogReaderFactory) { // 配置方法：使用指定的目录读取器工厂创建新实例
    return withFactory(f -> f.withCatalogReader(catalogReaderFactory)); // 委托给 withFactory，设置目录读取器工厂
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的引号类型。
   *
   * <p>引号类型用于标识标识符的引用方式，如反引号（`）、双引号（"）等。
   *
   * <p>使用场景：当测试需要使用特定的引号类型时使用。
   *
   * @param quoting 引号类型枚举值
   * @return 新的 SqlValidatorFixture 实例，使用指定的引号类型
   */
  public SqlValidatorFixture withQuoting(Quoting quoting) { // 配置方法：使用指定的引号类型创建新实例
    return withParserConfig(config -> config.withQuoting(quoting)); // 委托给 withParserConfig，设置引号类型
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的词法配置。
   *
   * <p>词法配置（Lex）是一个综合配置，包括引号类型、大小写敏感、引用和非引用标识符的大小写转换等。
   * 该方法会一次性设置所有相关的词法配置。
   *
   * <p>使用场景：当需要使用预定义的词法配置（如 Lex.MYSQL、Lex.ORACLE 等）时使用。
   *
   * @param lex 词法配置对象
   * @return 新的 SqlValidatorFixture 实例，使用指定的词法配置
   */
  public SqlValidatorFixture withLex(Lex lex) { // 配置方法：使用指定的词法配置创建新实例
    return withParserConfig(c -> c.withQuoting(lex.quoting) // 设置引号类型
        .withCaseSensitive(lex.caseSensitive) // 设置大小写敏感
        .withQuotedCasing(lex.quotedCasing) // 设置引用标识符的大小写转换
        .withUnquotedCasing(lex.unquotedCasing)); // 设置非引用标识符的大小写转换
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的 SQL 兼容性级别。
   *
   * <p>SQL 兼容性级别定义了 SQL 语法和语义的兼容性，如是否允许非标准语法、类型转换规则等。
   * 该方法会同时更新验证器配置、解析器配置和连接配置。
   *
   * <p>使用场景：当需要测试不同 SQL 兼容性级别下的行为时使用。
   *
   * @param conformance SQL 兼容性级别
   * @return 新的 SqlValidatorFixture 实例，使用指定的兼容性级别
   */
  public SqlValidatorFixture withConformance(SqlConformance conformance) { // 配置方法：使用指定的兼容性级别创建新实例
    return withValidatorConfig(c -> c.withConformance(conformance)) // 设置验证器的兼容性级别
        .withParserConfig(c -> c.withConformance(conformance)) // 设置解析器的兼容性级别
        .withFactory(f -> conformance instanceof SqlConformanceEnum // 如果是枚举类型的兼容性级别
            ? f.withConnectionFactory(cf -> // 设置连接工厂的兼容性属性
            cf.with(CalciteConnectionProperty.CONFORMANCE, conformance)) // 设置连接属性
            : f); // 否则不修改连接工厂
  }

  /**
   * 获取当前实例使用的 SQL 兼容性级别。
   *
   * <p>该方法用于访问当前的兼容性级别，可以用于检查配置或基于当前配置创建新的配置。
   *
   * @return 当前的 SQL 兼容性级别
   */
  public SqlConformance conformance() { // 获取方法：返回当前使用的兼容性级别
    return factory.parserConfig().conformance(); // 从解析器配置获取兼容性级别
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的类型强制转换设置。
   *
   * <p>类型强制转换是指在验证过程中自动将一种类型转换为另一种类型的功能。
   * 例如，将整数转换为字符串以进行字符串拼接。
   *
   * <p>使用场景：当需要测试类型强制转换功能时使用。
   *
   * @param typeCoercion 是否启用类型强制转换
   * @return 新的 SqlValidatorFixture 实例，使用指定的类型强制转换设置
   */
  public SqlValidatorFixture withTypeCoercion(boolean typeCoercion) { // 配置方法：使用指定的类型强制转换设置创建新实例
    return withValidatorConfig(c -> c.withTypeCoercionEnabled(typeCoercion)); // 委托给 withValidatorConfig，设置类型强制转换
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的宽松运算符查找设置。
   *
   * <p>宽松运算符查找是指在遇到未知函数时不立即失败，而是尝试继续验证。
   * 这对于测试某些场景下的错误处理行为很有用。
   *
   * @param lenient 是否启用宽松运算符查找
   * @return 新的 SqlValidatorFixture 实例，使用指定的宽松运算符查找设置
   */
  public SqlValidatorFixture withLenientOperatorLookup(boolean lenient) { // 配置方法：使用指定的宽松运算符查找设置创建新实例
    return withValidatorConfig(c -> c.withLenientOperatorLookup(lenient)); // 委托给 withValidatorConfig，设置宽松运算符查找
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的整体失败标志。
   *
   * <p>整体失败标志表示错误位置是整个查询或表达式，而不是特定位置。
   * 该方法会在 SQL 的开头和结尾添加插入符（^）来标记整体范围。
   *
   * <p>使用场景：当需要测试整个查询或表达式的失败时使用。
   *
   * @param whole 是否设置为整体失败
   * @return 新的 SqlValidatorFixture 实例，使用指定的整体失败标志
   */
  public SqlValidatorFixture withWhole(boolean whole) { // 配置方法：使用指定的整体失败标志创建新实例
    checkArgument(sap.cursor < 0); // 检查光标位置是否小于 0（即没有设置光标）
    final StringAndPos sap = StringAndPos.of("^" + this.sap.sql + "^"); // 在 SQL 的开头和结尾添加插入符
    return new SqlValidatorFixture(tester, factory, sap, expression, whole); // 返回新实例，使用新的字符串和位置对象
  }

  /**
   * 验证 SQL 应该成功，不抛出异常。
   *
   * <p>该方法会执行 SQL 验证，并期望不抛出任何异常。
   * 如果验证失败（抛出异常），测试将失败。
   *
   * <p>使用场景：当测试 SQL 语句应该成功验证时使用。
   *
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture ok() { // 验证方法：检查 SQL 验证应该成功
    tester.assertExceptionIsThrown(factory, toSql(false), null); // 断言不抛出异常（expected 为 null）
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证 SQL 应该失败，并抛出包含指定错误消息的异常。
   *
   * <p>该方法会执行 SQL 验证，并期望抛出异常，且异常消息包含指定的文本。
   * 如果验证成功或错误消息不匹配，测试将失败。
   *
   * <p>使用场景：当测试 SQL 语句应该失败并检查错误消息时使用。
   *
   * @param expected 期望的错误消息
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture fails(String expected) { // 验证方法：检查 SQL 验证应该失败并包含指定错误消息
    requireNonNull(expected, "expected"); // 检查期望的错误消息是否非空
    tester.assertExceptionIsThrown(factory, toSql(true), expected); // 断言抛出包含指定消息的异常
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 根据条件验证 SQL 应该失败或成功。
   *
   * <p>如果条件 b 为 true，则验证 SQL 应该失败并抛出包含指定错误消息的异常。
   * 如果条件 b 为 false，则验证 SQL 应该成功，不抛出异常。
   *
   * <p>使用场景：当需要根据条件动态测试 SQL 的成功或失败时使用。
   *
   * @param b 条件标志
   * @param expected 期望的错误消息（当 b 为 true 时使用）
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture failsIf(boolean b, String expected) { // 验证方法：根据条件检查 SQL 验证应该失败或成功
    if (b) { // 如果条件为 true
      fails(expected); // 验证应该失败
    } else { // 如果条件为 false
      ok(); // 验证应该成功
    }
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证查询返回的结果行类型是否与期望类型匹配。
   *
   * <p>该方法会验证 SQL 查询，并检查返回的结果行类型是否与指定的类型字符串匹配。
   * 类型字符串的格式为：{字段名 类型 可空性, 字段名 类型 可空性, ...}
   *
   * <p>使用示例：
   * <pre>
   * sql("select empno, name from emp")
   *   .type("{EMPNO INTEGER NOT NULL, NAME VARCHAR(10) NOT NULL}");
   * </pre>
   *
   * @param expectedType 期望的结果行类型字符串
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture type(String expectedType) { // 验证方法：检查查询返回的结果行类型
    tester.validateAndThen(factory, sap, (sql1, validator, n) -> { // 验证 SQL 并在验证后执行回调
      RelDataType actualType = validator.getValidatedNodeType(n); // 获取验证后的节点类型
      String actual = SqlTests.getTypeString(actualType); // 将类型转换为字符串表示
      assertThat(actual, is(expectedType)); // 断言实际类型与期望类型匹配
    });
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 将查询返回的结果行类型传递给消费者进行处理。
   *
   * <p>该方法会验证 SQL 查询，并将返回的结果行类型传递给指定的消费者。
   * 消费者可以执行任意的类型检查或断言。
   *
   * <p>使用场景：当需要自定义类型检查逻辑时使用。
   *
   * @param check 消费者函数，接受 RelDataType 并执行检查
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture type(Consumer<RelDataType> check) { // 验证方法：将结果行类型传递给消费者
    tester.validateAndThen(factory, sap, (sql, validator, n) -> { // 验证 SQL 并在验证后执行回调
      RelDataType actualType = validator.getValidatedNodeType(n); // 获取验证后的节点类型
      check.accept(actualType); // 将类型传递给消费者
    });
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证查询返回单列，并且该列的类型与期望类型匹配。
   *
   * <p>该方法会验证 SQL 查询，并检查查询是否返回单列，且该列的类型是否与指定的类型字符串匹配。
   * 类型字符串应包含类型和可空性，如 "INTEGER NOT NULL"。
   *
   * <p>使用示例：
   * <pre>
   * sql("SELECT empno FROM Emp").columnType("INTEGER NOT NULL");
   * </pre>
   *
   * @param expectedType 期望的列类型字符串（包括可空性）
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture columnType(String expectedType) { // 验证方法：检查查询返回的单列类型
    tester.checkColumnType(factory, toSql(false).sql, expectedType); // 检查列类型是否匹配
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证指定列是否为度量（measure）。
   *
   * <p>度量是 OLAP（联机分析处理）中的一个概念，表示可以进行聚合计算的值。
   * 该方法会检查指定列是否被标记为度量。
   *
   * @param column 列索引（从 0 开始）
   * @param matcher 匹配器，用于验证是否为度量
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture assertMeasure(int column,
      Matcher<Boolean> matcher) { // 验证方法：检查指定列是否为度量
    tester.validateAndThen(factory, sap, (sql1, validator, n) -> { // 验证 SQL 并在验证后执行回调
      final SqlNode selectItem = ((SqlSelect) n).getSelectList().get(column); // 获取指定列的选择项
      boolean isMeasure = SqlValidatorUtil.isMeasure(selectItem); // 检查是否为度量
      assertThat(isMeasure, matcher); // 断言是否为度量与匹配器匹配
    });
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证查询的第一列是否具有指定的单调性。
   *
   * <p>单调性表示值的排序特性，如单调递增、单调递减等。
   * 该方法会检查查询的第一列是否具有指定的单调性。
   *
   * <p>使用场景：当需要验证列的排序特性时使用，例如验证 ORDER BY 子句的有效性。
   *
   * @param matcher 匹配器，用于验证单调性
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture assertMonotonicity(
      Matcher<SqlMonotonicity> matcher) { // 验证方法：检查第一列的单调性
    tester.validateAndThen(factory, toSql(false), // 验证 SQL 并在验证后执行回调
        (sap, validator, n) -> {
          final RelDataType rowType = validator.getValidatedNodeType(n); // 获取验证后的节点类型（行类型）
          final SqlValidatorNamespace selectNamespace = // 获取选择语句的命名空间
              validator.getNamespace(n);
          final String field0 = rowType.getFieldList().get(0).getName(); // 获取第一列的字段名
          final SqlMonotonicity monotonicity = // 获取第一列的单调性
              selectNamespace.getMonotonicity(field0);
          assertThat(monotonicity, matcher); // 断言单调性与匹配器匹配
        });
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证查询的参数绑定类型是否与期望匹配。
   *
   * <p>参数绑定类型是指动态参数（如 ?）的类型信息。
   * 该方法会检查查询中使用的动态参数的类型是否与期望匹配。
   *
   * <p>使用场景：当需要验证动态参数的类型推导时使用。
   *
   * @param matcher 匹配器，用于验证参数绑定类型的字符串表示
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture assertBindType(Matcher<String> matcher) { // 验证方法：检查参数绑定类型
    tester.validateAndThen(factory, sap, (sap, validator, validatedNode) -> { // 验证 SQL 并在验证后执行回调
      final RelDataType parameterRowType = // 获取参数行类型
          validator.getParameterRowType(validatedNode);
      assertThat(parameterRowType, hasToString(matcher)); // 断言参数行类型的字符串表示与匹配器匹配
    });
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证查询返回的单列的字符集是否与期望匹配。
   *
   * <p>该方法会验证 SQL 查询，并检查返回的单列的字符集是否与期望匹配。
   * 字符集定义了字符的编码方式，如 UTF-8、GBK 等。
   *
   * <p>使用场景：当需要验证字符类型的字符集时使用。
   *
   * @param charsetMatcher 匹配器，用于验证字符集
   */
  public void assertCharset(Matcher<Charset> charsetMatcher) { // 验证方法：检查单列的字符集
    tester.forEachQuery(factory, sap.addCarets(), query -> // 对每个查询执行验证
        tester.validateAndThen(factory, StringAndPos.of(query), // 验证 SQL 并在验证后执行回调
            (sap, validator, n) -> {
              final RelDataType rowType = validator.getValidatedNodeType(n); // 获取验证后的节点类型（行类型）
              final List<RelDataTypeField> fields = rowType.getFieldList(); // 获取字段列表
              assertThat("expected query to return 1 field", fields.size(), // 断言查询返回单列
                  is(1));
              RelDataType actualType = fields.get(0).getType(); // 获取第一列的类型
              Charset actualCharset = actualType.getCharset(); // 获取字符集
              assertThat(actualCharset, charsetMatcher); // 断言字符集与匹配器匹配
            }));
  }

  /**
   * 验证查询返回的单列的排序规则和强制性是否与期望匹配。
   *
   * <p>排序规则（Collation）定义了字符的比较和排序规则，如不区分大小写的排序。
   * 强制性（Coercibility）表示排序规则的优先级，用于确定不同排序规则如何合并。
   *
   * <p>使用场景：当需要验证字符类型的排序规则时使用。
   *
   * @param collationMatcher 匹配器，用于验证排序规则名称
   * @param coercibilityMatcher 匹配器，用于验证强制性
   */
  public void assertCollation(Matcher<String> collationMatcher,
      Matcher<SqlCollation.Coercibility> coercibilityMatcher) { // 验证方法：检查单列的排序规则和强制性
    tester.forEachQuery(factory, sap.addCarets(), query -> // 对每个查询执行验证
        tester.validateAndThen(factory, StringAndPos.of(query), // 验证 SQL 并在验证后执行回调
            (sap, validator, n) -> {
              RelDataType rowType = validator.getValidatedNodeType(n); // 获取验证后的节点类型（行类型）
              final List<RelDataTypeField> fields = rowType.getFieldList(); // 获取字段列表
              assertThat("expected query to return 1 field", fields.size(), // 断言查询返回单列
                  is(1));
              RelDataType actualType = fields.get(0).getType(); // 获取第一列的类型
              SqlCollation collation = actualType.getCollation(); // 获取排序规则
              assertThat(collation, notNullValue()); // 断言排序规则非空
              assertThat(collation.getCollationName(), collationMatcher); // 断言排序规则名称与匹配器匹配
              assertThat(collation.getCoercibility(), coercibilityMatcher); // 断言强制性与匹配器匹配
            }));
  }

  /**
   * 验证时间间隔值转换为毫秒数的结果是否与期望匹配。
   *
   * <p>该方法会验证 SQL 中的时间间隔字面量，并将其转换为毫秒数或月数。
   * 如果是年-月间隔（如 INTERVAL '1' YEAR），则转换为月数。
   * 如果是日-时间隔（如 INTERVAL '1' DAY），则转换为毫秒数。
   *
   * <p>使用示例：
   * <pre>
   * sql("VALUES (INTERVAL '1' Minute)").intervalConv("60000");
   * </pre>
   *
   * @param matcher 匹配器，用于验证转换后的毫秒数或月数
   */
  public void assertInterval(Matcher<Long> matcher) { // 验证方法：检查时间间隔值的转换结果
    tester.validateAndThen(factory, toSql(false), // 验证 SQL 并在验证后执行回调
        (sap, validator, validatedNode) -> {
          final SqlCall n = (SqlCall) validatedNode; // 将验证后的节点转换为调用节点
          SqlNode node = null; // 初始化节点变量
          for (int i = 0; i < n.operandCount(); i++) { // 遍历调用节点的操作数
            node = stripAs(n.operand(i)); // 移除 AS 别名
            if (node instanceof SqlCall) { // 如果是调用节点
              node = ((SqlCall) node).operand(0); // 获取第一个操作数
              break; // 找到后退出循环
            }
          }

          assertNotNull(node); // 断言节点非空
          SqlIntervalLiteral intervalLiteral = (SqlIntervalLiteral) node; // 将节点转换为时间间隔字面量
          SqlIntervalLiteral.IntervalValue interval = // 获取时间间隔值
              intervalLiteral.getValueAs(
                  SqlIntervalLiteral.IntervalValue.class);
          long l = // 根据间隔类型转换为毫秒数或月数
              interval.getIntervalQualifier().isYearMonth() // 如果是年-月间隔
                  ? SqlParserUtil.intervalToMonths(interval) // 转换为月数
                  : SqlParserUtil.intervalToMillis(interval); // 否则转换为毫秒数
          assertThat(l, matcher); // 断言转换结果与匹配器匹配
        });
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的大小写敏感设置。
   *
   * <p>大小写敏感设置决定了标识符的比较是否区分大小写。
   * 例如，'empno' 和 'EMPNO' 是否被视为相同的标识符。
   *
   * <p>使用场景：当需要测试不同大小写敏感设置下的行为时使用。
   *
   * @param caseSensitive 是否大小写敏感
   * @return 新的 SqlValidatorFixture 实例，使用指定的大小写敏感设置
   */
  public SqlValidatorFixture withCaseSensitive(boolean caseSensitive) { // 配置方法：使用指定的大小写敏感设置创建新实例
    return withParserConfig(c -> c.withCaseSensitive(caseSensitive)); // 委托给 withParserConfig，设置大小写敏感
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的运算符表。
   *
   * <p>运算符表定义了可用的 SQL 运算符和函数。
   * 通过自定义运算符表，可以添加或替换运算符和函数。
   *
   * <p>使用场景：当需要测试自定义运算符或函数时使用。
   *
   * @param operatorTable 运算符表
   * @return 新的 SqlValidatorFixture 实例，使用指定的运算符表
   */
  public SqlValidatorFixture withOperatorTable(SqlOperatorTable operatorTable) { // 配置方法：使用指定的运算符表创建新实例
    return withFactory(c -> c.withOperatorTable(o -> operatorTable)); // 委托给 withFactory，设置运算符表
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的引用标识符大小写转换设置。
   *
   * <p>引用标识符是指使用引号括起来的标识符，如 "empno"。
   * 该设置决定了引用标识符在内部存储时的大小写转换方式。
   *
   * <p>使用场景：当需要测试引用标识符的大小写转换时使用。
   *
   * @param casing 大小写转换枚举值（如 TO_UPPER、TO_LOWER、UNCHANGED）
   * @return 新的 SqlValidatorFixture 实例，使用指定的引用标识符大小写转换设置
   */
  public SqlValidatorFixture withQuotedCasing(Casing casing) { // 配置方法：使用指定的引用标识符大小写转换设置创建新实例
    return withParserConfig(c -> c.withQuotedCasing(casing)); // 委托给 withParserConfig，设置引用标识符的大小写转换
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的非引用标识符大小写转换设置。
   *
   * <p>非引用标识符是指不使用引号的标识符，如 empno。
   * 该设置决定了非引用标识符在内部存储时的大小写转换方式。
   *
   * <p>使用场景：当需要测试非引用标识符的大小写转换时使用。
   *
   * @param casing 大小写转换枚举值（如 TO_UPPER、TO_LOWER、UNCHANGED）
   * @return 新的 SqlValidatorFixture 实例，使用指定的非引用标识符大小写转换设置
   */
  public SqlValidatorFixture withUnquotedCasing(Casing casing) { // 配置方法：使用指定的非引用标识符大小写转换设置创建新实例
    return withParserConfig(c -> c.withUnquotedCasing(casing)); // 委托给 withParserConfig，设置非引用标识符的大小写转换
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用转换后的验证器配置。
   *
   * <p>验证器配置包括：兼容性级别、类型强制转换、运算符查找、标识符展开、调用重写等。
   * 该方法是配置验证器的主要方法。
   *
   * <p>使用场景：当需要自定义验证器的行为时使用。
   *
   * @param transform 一元运算符，接受当前的 SqlValidator.Config 并返回转换后的配置
   * @return 新的 SqlValidatorFixture 实例，使用转换后的验证器配置
   */
  public SqlValidatorFixture withValidatorConfig(
      UnaryOperator<SqlValidator.Config> transform) { // 配置方法：使用转换后的验证器配置创建新实例
    return withFactory(f -> f.withValidatorConfig(transform)); // 委托给 withFactory，设置验证器配置
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的标识符展开设置。
   *
   * <p>标识符展开是指在验证过程中将引用的标识符展开为完全限定名（如 empno 展开为 schema.empno）。
   *
   * <p>使用场景：当需要测试标识符展开功能时使用。
   *
   * @param expansion 是否启用标识符展开
   * @return 新的 SqlValidatorFixture 实例，使用指定的标识符展开设置
   */
  public SqlValidatorFixture withValidatorIdentifierExpansion(
      boolean expansion) { // 配置方法：使用指定的标识符展开设置创建新实例
    return withValidatorConfig(c -> c.withIdentifierExpansion(expansion)); // 委托给 withValidatorConfig，设置标识符展开
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的调用重写设置。
   *
   * <p>调用重写是指在验证过程中重写 SQL 调用（如函数调用、运算符调用）以优化或规范化。
   *
   * <p>使用场景：当需要测试调用重写功能时使用。
   *
   * @param rewrite 是否启用调用重写
   * @return 新的 SqlValidatorFixture 实例，使用指定的调用重写设置
   */
  public SqlValidatorFixture withValidatorCallRewrite(boolean rewrite) { // 配置方法：使用指定的调用重写设置创建新实例
    return withValidatorConfig(c -> c.withCallRewrite(rewrite)); // 委托给 withValidatorConfig，设置调用重写
  }

  /**
   * 创建新的 SqlValidatorFixture 实例，该实例使用指定的列引用展开设置。
   *
   * <p>列引用展开是指在验证过程中将列引用展开为完全限定名（如 empno 展开为 emp.empno）。
   *
   * <p>使用场景：当需要测试列引用展开功能时使用。
   *
   * @param expansion 是否启用列引用展开
   * @return 新的 SqlValidatorFixture 实例，使用指定的列引用展开设置
   */
  public SqlValidatorFixture withValidatorColumnReferenceExpansion(
      boolean expansion) { // 配置方法：使用指定的列引用展开设置创建新实例
    return withValidatorConfig(c -> // 委托给 withValidatorConfig，设置列引用展开
        c.withColumnReferenceExpansion(expansion));
  }

  /**
   * 验证 SQL 重写后的结果是否与期望匹配。
   *
   * <p>该方法会验证 SQL 查询，并将重写后的 SQL 转换为 ANSI SQL 方言的字符串表示。
   * 然后检查重写后的 SQL 是否与期望的 SQL 匹配。
   *
   * <p>使用场景：当需要验证 SQL 重写的正确性时使用。
   *
   * @param expected 期望的重写后的 SQL 字符串
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture rewritesTo(String expected) { // 验证方法：检查 SQL 重写后的结果
    tester.validateAndThen(factory, toSql(false), // 验证 SQL 并在验证后执行回调
        (sap, validator, validatedNode) -> {
          String actualRewrite = // 将重写后的节点转换为 ANSI SQL 字符串
              validatedNode.toSqlString(AnsiSqlDialect.DEFAULT, false)
                  .getSql();
          TestUtil.assertEqualsVerbose(expected, Util.toLinux(actualRewrite)); // 断言重写后的 SQL 与期望匹配
        });
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证查询是否为聚合查询。
   *
   * <p>聚合查询是指包含聚合函数（如 SUM、COUNT、AVG 等）或 GROUP BY 子句的查询。
   * 该方法会检查查询是否为聚合查询。
   *
   * <p>使用场景：当需要验证查询的聚合特性时使用。
   *
   * @param matcher 匹配器，用于验证是否为聚合查询
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture isAggregate(Matcher<Boolean> matcher) { // 验证方法：检查查询是否为聚合查询
    tester.validateAndThen(factory, toSql(false), // 验证 SQL 并在验证后执行回调
        (sap, validator, validatedNode) ->
            assertThat(validator.isAggregate((SqlSelect) validatedNode), // 断言是否为聚合查询与匹配器匹配
                matcher));
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 验证查询结果字段的来源列表是否与期望匹配。
   *
   * <p>字段来源列表表示每个结果字段来自哪个表的哪个字段。
   * 例如，{(CATALOG.SALES.EMP.EMPNO, null)} 表示第一个字段来自 CATALOG.SALES.EMP 表的 EMPNO 字段。
   *
   * <p>使用场景：当需要验证字段的来源追踪功能时使用。
   *
   * @param matcher 匹配器，用于验证字段来源列表的字符串表示
   * @return 当前实例（支持链式调用）
   */
  public SqlValidatorFixture assertFieldOrigin(Matcher<String> matcher) { // 验证方法：检查结果字段的来源列表
    tester.validateAndThen(factory, toSql(false), (sap, validator, n) -> { // 验证 SQL 并在验证后执行回调
      final List<List<String>> list = validator.getFieldOrigins(n); // 获取字段来源列表
      final StringBuilder buf = new StringBuilder("{"); // 创建字符串构建器
      int i = 0; // 初始化索引
      for (List<String> strings : list) { // 遍历每个字段的来源
        if (i++ > 0) { // 如果不是第一个字段
          buf.append(", "); // 添加分隔符
        }
        if (strings == null) { // 如果来源为空
          buf.append("null"); // 添加 null
        } else { // 如果来源不为空
          int j = 0; // 初始化索引
          for (String s : strings) { // 遍历来源的每个部分
            if (j++ > 0) { // 如果不是第一部分
              buf.append('.'); // 添加分隔符
            }
            buf.append(s); // 添加部分字符串
          }
        }
      }
      buf.append("}"); // 添加结束括号
      assertThat(buf, hasToString(matcher)); // 断言字段来源列表的字符串表示与匹配器匹配
    });
    return this; // 返回当前实例，支持链式调用
  }

  /**
   * 为指定的运算符设置测试上下文。
   *
   * <p>该方法当前为空实现，保留用于未来的扩展。
   *
   * @param operator 运算符
   */
  public void setFor(SqlOperator operator) { // 设置方法：为运算符设置测试上下文（当前为空实现）
  }
} // 类结束
