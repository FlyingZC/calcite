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
// 包声明：该类属于 org.apache.calcite.sql.test 包，用于测试 SQL 美化写入器
package org.apache.calcite.sql.test;

// 导入 Calcite SQL 相关的核心类
import org.apache.calcite.sql.SqlCall; // SQL 调用表达式，表示函数调用或操作符调用
import org.apache.calcite.sql.SqlNode; // SQL 节点基类，所有 SQL 表达式的抽象基类
import org.apache.calcite.sql.SqlWriterConfig; // SQL 写入器配置类，用于配置格式化输出的各种选项
import org.apache.calcite.sql.dialect.AnsiSqlDialect; // ANSI SQL 方言，标准的 SQL 方言实现
import org.apache.calcite.sql.parser.SqlParseException; // SQL 解析异常类，当 SQL 解析失败时抛出
import org.apache.calcite.sql.parser.SqlParser; // SQL 解析器，负责将 SQL 字符串解析为抽象语法树（AST）
import org.apache.calcite.sql.pretty.SqlPrettyWriter; // SQL 美化写入器，负责将 SQL 节点格式化为可读的 SQL 字符串
import org.apache.calcite.test.DiffRepository; // 差异仓库，用于测试中比较预期输出和实际输出
import org.apache.calcite.util.Litmus; // 断言工具类，提供不同级别的断言检查

// 导入空值检查注解
import org.checkerframework.checker.nullness.qual.Nullable; // 用于标记可能为 null 的字段或参数

// 导入 Java I/O 相关类
import java.io.PrintWriter; // 打印写入器，用于将格式化后的文本输出到字符串
import java.io.StringWriter; // 字符串写入器，用于在内存中构建字符串

// 导入 Java 工具类
import java.util.Objects; // 对象工具类，提供 null 检查和对象比较方法
import java.util.function.UnaryOperator; // 一元操作符函数式接口，用于转换对象

// 导入 JUnit 断言
import static org.junit.jupiter.api.Assertions.assertTrue; // JUnit 5 的 assertTrue 断言方法

// 导入 Java 对象工具方法
import static java.util.Objects.requireNonNull; // requireNonNull 方法，用于检查参数是否为 null

/**
 * SQL 美化写入器的测试工具类
 * 
 * <p>该类提供了一个流畅的 API（Fluent API），允许通过链式方法调用来编写测试用例。
 * 这种设计模式使得测试代码更加简洁、易读，可以像自然语言一样表达测试意图。
 *
 * <p>该类是不可变的（immutable）。如果两个测试用例需要相似的配置设置，
 * 可以安全地使用同一个 fixture 对象作为两个测试的起点。
 * 不可变性确保了测试之间的相互隔离，避免了状态污染问题。
 *
 * <p>主要功能：
 * 1. 解析 SQL 字符串为抽象语法树（AST）
 * 2. 使用 SqlPrettyWriter 将 AST 格式化为 SQL 字符串
 * 3. 验证格式化后的 SQL 是否与预期一致
 * 4. 验证格式化后的 SQL 重新解析后是否与原始 AST 结构等价
 * 5. 支持表达式和完整查询两种测试模式
 * 6. 支持自定义 SqlWriterConfig 转换器来测试不同的格式化配置
 *
 * <p>使用示例：
 * <pre>
 * new SqlPrettyWriterFixture(diffRepos, "SELECT * FROM t", false, null, "SELECT *\nFROM t", config -> config)
 *     .check();
 * </pre>
 *
 * @see org.apache.calcite.sql.pretty.SqlPrettyWriter // 相关类：SQL 美化写入器
 */
// 测试工具类，用于测试 SQL 美化写入器的功能
class SqlPrettyWriterFixture {
  // 差异仓库，用于存储和比较测试的预期输出和实际输出，可能为 null（某些测试不使用差异仓库）
  private final @Nullable DiffRepository diffRepos; // 差异仓库对象，用于测试结果比较
  // 待测试的 SQL 字符串，可以是完整的查询语句或表达式
  public final String sql; // SQL 字符串，将被解析和格式化
  // 标识是否为表达式模式，true 表示测试表达式，false 表示测试完整查询
  public final boolean expression; // 表达式标志，true=表达式模式，false=查询模式
  // 预期的描述信息，用于验证美化写入器的配置描述，可能为 null
  public final @Nullable String desc; // 预期的描述字符串，用于验证配置描述
  // 预期的格式化输出字符串，用于验证格式化结果是否正确
  public final String formatted; // 预期的格式化 SQL 字符串
  // SqlWriterConfig 转换器，用于修改默认的写入器配置，支持链式配置
  public final UnaryOperator<SqlWriterConfig> transform; // 配置转换器，用于自定义格式化行为

  // 构造方法：创建 SqlPrettyWriterFixture 实例
  // @param diffRepos 差异仓库，用于测试结果比较，可能为 null
  // @param sql 待测试的 SQL 字符串
  // @param expression 是否为表达式模式
  // @param desc 预期的描述信息，可能为 null
  // @param formatted 预期的格式化输出
  // @param transform SqlWriterConfig 转换器
  SqlPrettyWriterFixture(@Nullable DiffRepository diffRepos, String sql,
      boolean expression, @Nullable String desc, String formatted,
      UnaryOperator<SqlWriterConfig> transform) {
    this.diffRepos = diffRepos; // 保存差异仓库引用
    this.sql = requireNonNull(sql, "sql"); // 保存 SQL 字符串，确保不为 null
    this.expression = expression; // 保存表达式标志
    this.desc = desc; // 保存描述信息
    this.formatted = requireNonNull(formatted, "formatted"); // 保存格式化输出，确保不为 null
    this.transform = requireNonNull(transform, "transform"); // 保存配置转换器，确保不为 null
  }

  // 方法：创建一个新的 fixture，应用额外的写入器配置转换
  // 该方法支持链式调用，允许逐步添加配置转换
  // @param transform 要应用的额外配置转换器
  // @return 新的 SqlPrettyWriterFixture 实例，包含组合后的转换器
  SqlPrettyWriterFixture withWriter(
      UnaryOperator<SqlWriterConfig> transform) {
    requireNonNull(transform, "transform"); // 检查转换器参数不为 null
    // 将当前转换器和新转换器组合，形成新的转换器链
    // andThen 方法确保先应用当前转换器，再应用新转换器
    final UnaryOperator<SqlWriterConfig> transform1 =
        this.transform.andThen(transform)::apply; // 组合两个转换器
    // 返回新的 fixture 实例，保持不可变性
    return new SqlPrettyWriterFixture(diffRepos, sql, expression, desc,
        formatted, transform1); // 创建并返回新实例
  }

  // 方法：创建一个新的 fixture，使用不同的 SQL 字符串
  // 如果新 SQL 与当前 SQL 相同，则返回当前实例（优化性能）
  // @param sql 新的 SQL 字符串
  // @return 新的 SqlPrettyWriterFixture 实例或当前实例
  SqlPrettyWriterFixture withSql(String sql) {
    if (sql.equals(this.sql)) { // 如果 SQL 相同
      return this; // 返回当前实例，避免不必要的对象创建
    }
    // 创建并返回新实例，使用新的 SQL 字符串
    return new SqlPrettyWriterFixture(diffRepos, sql, expression, desc,
        formatted, transform); // 创建新实例
  }

  // 方法：创建一个新的 fixture，设置表达式模式标志
  // 如果新标志与当前标志相同，则返回当前实例（优化性能）
  // @param expression 新的表达式模式标志
  // @return 新的 SqlPrettyWriterFixture 实例或当前实例
  SqlPrettyWriterFixture withExpr(boolean expression) {
    if (this.expression == expression) { // 如果标志相同
      return this; // 返回当前实例
    }
    // 创建并返回新实例，使用新的表达式标志
    return new SqlPrettyWriterFixture(diffRepos, sql, expression, desc,
        formatted, transform); // 创建新实例
  }

  // 方法：创建一个新的 fixture，使用不同的差异仓库
  // 如果新仓库与当前仓库相同，则返回当前实例（优化性能）
  // @param diffRepos 新的差异仓库
  // @return 新的 SqlPrettyWriterFixture 实例或当前实例
  SqlPrettyWriterFixture withDiffRepos(DiffRepository diffRepos) {
    if (Objects.equals(this.diffRepos, diffRepos)) { // 如果仓库相同
      return this; // 返回当前实例
    }
    // 创建并返回新实例，使用新的差异仓库
    return new SqlPrettyWriterFixture(diffRepos, sql, expression, desc,
        formatted, transform); // 创建新实例
  }

  // 方法：获取差异仓库，并检查其不为 null
  // 该方法用于确保在需要使用差异仓库时，仓库确实存在
  // 注意：diffRepos 字段本身可以为 null，因为某些测试不使用差异仓库
  // @return 非空的 DiffRepository 实例
  // @throws NullPointerException 如果 diffRepos 为 null
  public DiffRepository diffRepos() {
    return DiffRepository.castNonNull(diffRepos); // 强制转换为非 null，如果为 null 则抛出异常
  }

  // 方法：创建一个新的 fixture，设置预期的描述信息
  // 如果新描述与当前描述相同，则返回当前实例（优化性能）
  // @param desc 新的预期描述信息，可能为 null
  // @return 新的 SqlPrettyWriterFixture 实例或当前实例
  SqlPrettyWriterFixture expectingDesc(@Nullable String desc) {
    if (Objects.equals(this.desc, desc)) { // 如果描述相同
      return this; // 返回当前实例
    }
    // 创建并返回新实例，使用新的预期描述
    return new SqlPrettyWriterFixture(diffRepos, sql, expression, desc,
        formatted, transform); // 创建新实例
  }

  // 方法：创建一个新的 fixture，设置预期的格式化输出
  // 如果新格式化输出与当前相同，则返回当前实例（优化性能）
  // @param formatted 新的预期格式化输出字符串
  // @return 新的 SqlPrettyWriterFixture 实例或当前实例
  SqlPrettyWriterFixture expectingFormatted(String formatted) {
    if (Objects.equals(this.formatted, formatted)) { // 如果格式化输出相同
      return this; // 返回当前实例
    }
    // 创建并返回新实例，使用新的预期格式化输出
    return new SqlPrettyWriterFixture(diffRepos, sql, expression, desc,
        formatted, transform); // 创建新实例
  }

  // 方法：解析 SQL 查询字符串为抽象语法树（AST）
  // 该方法提供默认的 SQL 解析实现，子类可以覆盖此方法以使用不同的解析器
  // @param sql 要解析的 SQL 字符串
  // @return 解析后的 SqlNode 抽象语法树节点
  // @throws AssertionError 如果 SQL 解析失败
  protected SqlNode parseQuery(String sql) {
    SqlNode node; // 声明 SQL 节点变量
    try {
      // 使用默认配置创建 SQL 解析器并解析查询
      node = SqlParser.create(sql).parseQuery(); // 解析 SQL 字符串为 AST
    } catch (SqlParseException e) { // 捕获解析异常
      // 构造详细的错误消息，包含原始 SQL 和异常信息
      String message = "Received error while parsing SQL '" + sql + "'"
          + "; error is:\n"
          + e.toString(); // 格式化错误消息
      throw new AssertionError(message); // 抛出断言错误，包含详细错误信息
    }
    return node; // 返回解析后的 SQL 节点
  }

  // 方法：执行测试检查，验证 SQL 格式化是否正确
  // 该方法解析 SQL，格式化它，然后验证结果是否符合预期
  // 它还会验证格式化后的 SQL 重新解析后是否与原始 AST 结构等价
  // @return 当前 SqlPrettyWriterFixture 实例，支持链式调用
  SqlPrettyWriterFixture check() {
    // 调用 checkTransformedNode 方法，使用恒等转换器（不转换节点）
    return checkTransformedNode(n -> n); // 传递恒等函数，不转换节点
  }

  // 方法：执行测试检查，但对转换后的节点进行验证
  // 与 check() 方法类似，但允许在验证前对 AST 节点进行转换
  // 这对于测试特定子树或修改后的节点很有用
  // 例如：可以测试某个节点的第二个子节点
  // @param nodeTransformer 节点转换器，用于在验证前转换 AST 节点
  // @return 当前 SqlPrettyWriterFixture 实例，支持链式调用
  SqlPrettyWriterFixture checkTransformedNode(
      UnaryOperator<SqlNode> nodeTransformer) {
    // 调用内部实现方法 check_，传递节点转换器
    return check_(nodeTransformer); // 委托给私有方法实现
  }

  // 私有方法：执行测试检查的核心实现
  // 该方法执行以下步骤：
  // 1. 应用配置转换器创建 SqlWriterConfig
  // 2. 创建 SqlPrettyWriter 实例
  // 3. 解析 SQL 字符串为 AST
  // 4. 如果是表达式模式，从 VALUES 子句中提取表达式
  // 5. 如果有预期描述，验证配置描述
  // 6. 格式化 AST 为 SQL 字符串
  // 7. 验证格式化结果是否符合预期
  // 8. 重新解析格式化后的 SQL，验证结构等价性
  // @param nodeTransformer 节点转换器，用于在验证前转换 AST 节点
  // @return 当前 SqlPrettyWriterFixture 实例
  private SqlPrettyWriterFixture check_(UnaryOperator<SqlNode> nodeTransformer) {
    // 应用配置转换器到默认配置，并设置 ANSI SQL 方言
    // transform 是一个函数，接收默认配置并返回修改后的配置
    final SqlWriterConfig config =
        transform.apply(SqlPrettyWriter.config()
            .withDialect(AnsiSqlDialect.DEFAULT)); // 创建配置对象
    // 使用配置创建 SQL 美化写入器
    final SqlPrettyWriter prettyWriter = new SqlPrettyWriter(config); // 创建写入器实例
    // 声明原始解析的节点
    final SqlNode node; // 原始 SQL 节点
    // 声明转换后的节点（用于后续验证）
    final SqlNode node1; // 转换后的节点
    if (expression) { // 如果是表达式模式
      // 将表达式包装在 VALUES 子句中进行解析
      // 例如："a + b" -> "VALUES (a + b)"
      final SqlCall valuesCall = (SqlCall) parseQuery("VALUES (" + sql + ")"); // 解析 VALUES 语句
      // 获取 VALUES 的第一个操作数（ROW 构造器）
      final SqlCall rowCall = valuesCall.operand(0); // 获取 ROW 节点
      // 获取 ROW 的第一个操作数（实际的表达式）
      node = rowCall.operand(0); // 提取表达式节点
      // 表达式模式下不转换节点
      node1 = node; // 直接使用原始节点
    } else { // 如果是完整查询模式
      // 直接解析 SQL 查询
      node = parseQuery(sql); // 解析完整查询
      // 应用节点转换器
      node1 = nodeTransformer.apply(node); // 转换节点
    }

    // 验证配置描述（如果设置了预期描述）
    if (desc != null) { // 如果有预期描述
      // 创建字符串写入器，用于捕获输出
      final StringWriter sw = new StringWriter(); // 创建字符串写入器
      // 创建打印写入器，包装字符串写入器
      final PrintWriter pw = new PrintWriter(sw); // 创建打印写入器
      // 将美化写入器的配置描述输出到打印写入器
      prettyWriter.describe(pw, true); // 描述配置，true 表示详细输出
      // 刷新打印写入器，确保所有内容都写入字符串
      pw.flush(); // 刷新缓冲区
      // 获取描述字符串
      final String desc = sw.toString(); // 获取生成的描述
      // 验证描述是否符合预期
      diffRepos().assertEquals("desc", this.desc, desc); // 比较描述
    }

    // 格式化 SQL 节点为字符串
    final String formatted = prettyWriter.format(node); // 格式化节点
    // 验证格式化结果是否符合预期
    diffRepos().assertEquals("formatted", this.formatted, formatted); // 比较格式化输出

    // 验证格式化后的 SQL 重新解析后是否与原始 AST 结构等价
    // 这是确保格式化过程没有改变语义的重要步骤
    // 将反引号替换为双引号（某些方言使用反引号）
    final String actual2 = formatted.replace("`", "\""); // 标准化引号
    // 声明重新解析后的节点
    final SqlNode node2; // 重新解析的节点
    if (expression) { // 如果是表达式模式
      // 将格式化后的表达式包装在 VALUES 子句中重新解析
      final SqlCall valuesCall =
          (SqlCall) parseQuery("VALUES (" + actual2 + ")"); // 重新解析 VALUES 语句
      // 获取 ROW 节点
      final SqlCall rowCall = valuesCall.operand(0); // 获取 ROW 节点
      // 提取表达式节点
      node2 = rowCall.operand(0); // 提取表达式节点
    } else { // 如果是完整查询模式
      // 重新解析格式化后的 SQL
      SqlNode node2a = parseQuery(actual2); // 解析格式化后的 SQL
      // 应用相同的节点转换器
      node2 = nodeTransformer.apply(node2a); // 转换节点
    }
    // 深度比较两个节点是否等价
    // Litmus.THROW 表示如果不等价则抛出异常
    assertTrue(node1.equalsDeep(node2, Litmus.THROW)); // 验证结构等价性

    // 返回当前实例，支持链式调用
    return this; // 返回 this
  }
}
