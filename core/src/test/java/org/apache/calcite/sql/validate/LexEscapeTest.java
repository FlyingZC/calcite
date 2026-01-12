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
// 许可证说明：此文件遵循 Apache License 2.0 开源协议
// Apache 软件基金会（ASF）授予用户使用、修改和分发此代码的权利
// 用户必须保留版权声明和许可证声明
// 代码按"原样"提供，不提供任何明示或暗示的保证
// 包声明：此测试类位于 sql.validate 包下，用于测试 SQL 验证器功能
package org.apache.calcite.sql.validate;
// 导入 Lex 枚举类，用于指定 SQL 词法分析器方言（ORACLE、MYSQL、SQL_SERVER、JAVA 等）
import org.apache.calcite.config.Lex;
// 导入 RelTraitDef 类，用于定义关系节点的物理特征（如排序、分布等）
import org.apache.calcite.plan.RelTraitDef;
// 导入 RelNode 接口，表示关系代数树的节点
import org.apache.calcite.rel.RelNode;
// 导入 LogicalProject 类，表示逻辑投影操作（SQL 中的 SELECT 子句）
import org.apache.calcite.rel.logical.LogicalProject;
// 导入 RelDataType 接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入 RelDataTypeFactory 接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入 RelDataTypeField 类，表示关系数据类型的字段
import org.apache.calcite.rel.type.RelDataTypeField;
// 导入 SchemaPlus 接口，表示数据库模式的扩展接口，可以添加表和函数
import org.apache.calcite.schema.SchemaPlus;
// 导入 AbstractTable 抽象类，用于创建自定义表
import org.apache.calcite.schema.impl.AbstractTable;
// 导入 SqlNode 接口，表示 SQL 抽象语法树的节点
import org.apache.calcite.sql.SqlNode;
// 导入 SqlStdOperatorTable 类，包含 SQL 标准运算符表
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
// 导入 SqlParseException 异常类，表示 SQL 解析时的异常
import org.apache.calcite.sql.parser.SqlParseException;
// 导入 SqlParser 类，用于解析 SQL 语句
import org.apache.calcite.sql.parser.SqlParser;
// 导入 SqlParser.Config 接口，表示 SQL 解析器的配置
import org.apache.calcite.sql.parser.SqlParser.Config;
// 导入 SqlTypeName 枚举类，定义 SQL 标准数据类型名称
import org.apache.calcite.sql.type.SqlTypeName;
// 导入 FrameworkConfig 接口，表示 Calcite 框架的配置
import org.apache.calcite.tools.FrameworkConfig;
// 导入 Frameworks 类，用于创建 Calcite 框架的构建器
import org.apache.calcite.tools.Frameworks;
// 导入 Planner 接口，表示 SQL 规划器，负责解析、验证、优化和转换 SQL
import org.apache.calcite.tools.Planner;
// 导入 Program 接口，表示 SQL 转换程序，包含优化规则
import org.apache.calcite.tools.Program;
// 导入 Programs 类，提供常用的转换程序（如 RULE_SET）
import org.apache.calcite.tools.Programs;
// 导入 RelConversionException 异常类，表示关系转换时的异常
import org.apache.calcite.tools.RelConversionException;
// 导入 ValidationException 异常类，表示 SQL 验证时的异常
import org.apache.calcite.tools.ValidationException;

// 导入 Google Guava 的 ImmutableList 类，用于创建不可变列表
import com.google.common.collect.ImmutableList;

// 导入 Checker Framework 的 Nullable 注解，表示参数或返回值可以为 null
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入 Java 标准库的 List 接口
import java.util.List;

// 导入 Hamcrest 断言库的 instanceOf 匹配器，用于断言对象类型
import static org.hamcrest.CoreMatchers.instanceOf;
// 导入 Hamcrest 断言库的 is 匹配器，用于断言值相等
import static org.hamcrest.CoreMatchers.is;
// 导入 Hamcrest 断言库的 assertThat 方法，用于执行断言
import static org.hamcrest.MatcherAssert.assertThat;
// 导入 Hamcrest 断言库的 hasSize 匹配器，用于断言集合大小
import static org.hamcrest.Matchers.hasSize;

/**
 * Testing {@link SqlValidator} and {@link Lex} quoting.
 */
// 这是一个用于测试 SqlValidator 和 Lex（词法分析器）引用转义机制的测试类
// 主要测试不同数据库方言（Oracle、MySQL、SQL Server、Java）如何处理标识符的转义引用
// 例如测试保留字（如 localtime、current_timestamp）在不同方言下如何被正确识别和解析
// 测试内容包括：双引号、反引号、方括号等不同转义符号的使用场景
class LexEscapeTest {

  // 私有静态方法：获取并配置一个 Planner（规划器）对象
  // 参数 traitDefs：关系特征定义列表，用于定义物理属性如排序、分布等（可为空）
  // 参数 parserConfig：SQL解析器的配置对象，包含词法分析器（Lex）等配置
  // 参数 programs：程序数组，用于定义SQL转换的规则集（可变参数）
  // 返回值：配置好的 Planner 对象，可用于后续的 SQL 解析、验证和转换
  private static Planner getPlanner(@Nullable List<RelTraitDef> traitDefs,
      Config parserConfig, Program... programs) {
    // 创建一个根 Schema（模式），true 表示添加内置函数
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    // 向根 Schema 中添加一个名为 "TMP" 的虚拟表
    rootSchema.add("TMP", new AbstractTable() {
      // 重写 getRowType 方法，定义 TMP 表的行类型结构
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        // 创建结构类型，包含两个字段：
        // 第一个字段：VARCHAR 类型，字段名为 "localtime"（这是一个保留字）
        // 第二个字段：INTEGER 类型，字段名为 "current_timestamp"（这也是一个保留字）
        return typeFactory.createStructType(
            ImmutableList.of(typeFactory.createSqlType(SqlTypeName.VARCHAR),
                typeFactory.createSqlType(SqlTypeName.INTEGER)),
            ImmutableList.of("localtime", "current_timestamp"));
      }
    });
    // 构建框架配置对象
    final FrameworkConfig config = Frameworks.newConfigBuilder()
        .parserConfig(parserConfig) // 设置解析器配置（包含 Lex 方言配置）
        .defaultSchema(rootSchema) // 设置默认 Schema 为刚创建的 rootSchema
        .traitDefs(traitDefs) // 设置关系特征定义
        .programs(programs) // 设置程序规则集
        .operatorTable(SqlStdOperatorTable.instance()) // 设置标准运算符表
        .build();
    // 返回配置好的 Planner 对象
    return Frameworks.getPlanner(config);
  }

  // 私有静态方法：使用指定的词法分析器（Lex）配置运行投影查询并验证结果
  // 参数 lex：词法分析器枚举值，指定数据库方言（ORACLE、MYSQL、SQL_SERVER、JAVA 等）
  // 参数 sql：要执行的 SQL 查询语句
  // 可能抛出的异常：SqlParseException（SQL解析异常）、ValidationException（验证异常）、RelConversionException（关系转换异常）
  private static void runProjectQueryWithLex(Lex lex, String sql)
      throws SqlParseException, ValidationException, RelConversionException {
    // 创建 SQL 解析器配置，并设置指定的词法分析器（Lex）方言
    Config javaLex = SqlParser.config().withLex(lex);
    // 获取配置好的 Planner，传入解析器配置和规则集（使用标准规则集）
    Planner planner = getPlanner(null, javaLex, Programs.ofRules(Programs.RULE_SET));
    // 第一步：解析 SQL 语句，生成抽象语法树（SqlNode）
    SqlNode parse = planner.parse(sql);
    // 第二步：验证 SQL 语句的语义正确性，包括表名、字段名、类型检查等
    SqlNode validate = planner.validate(parse);
    // 第三步：将验证后的 SQL 转换为关系代数树（RelNode）
    RelNode convert = planner.rel(validate).rel;
    // 断言：转换后的关系节点必须是 LogicalProject（逻辑投影）类型
    assertThat(convert, instanceOf(LogicalProject.class));
    // 获取投影结果的字段列表
    List<RelDataTypeField> fields = convert.getRowType().getFieldList();
    // 从 SQL 文本中获取字段类型，并验证我们在验证后正确解析了它
    // 断言：应该有 4 个字段（2 个带引号的标识符 + 2 个不带引号的标识符）
    assertThat(fields, hasSize(4));
    // 断言：第 1 个字段类型应该是 VARCHAR（这是带引号的 "localtime" 字段，被识别为表中的列）
    assertThat(fields.get(0).getType().getSqlTypeName(), is(SqlTypeName.VARCHAR));
    // 断言：第 2 个字段类型应该是 TIME（这是不带引号的 localtime，被识别为 SQL 函数）
    assertThat(fields.get(1).getType().getSqlTypeName(), is(SqlTypeName.TIME));
    // 断言：第 3 个字段类型应该是 INTEGER（这是带引号的 "current_timestamp" 字段，被识别为表中的列）
    assertThat(fields.get(2).getType().getSqlTypeName(), is(SqlTypeName.INTEGER));
    // 断言：第 4 个字段类型应该是 TIMESTAMP（这是不带引号的 current_timestamp，被识别为 SQL 函数）
    assertThat(fields.get(3).getType().getSqlTypeName(), is(SqlTypeName.TIMESTAMP));
  }

  // 测试方法：测试 Oracle 方言下的标识符转义机制
  // Oracle 使用双引号（"）来引用标识符（表名、列名等）
  // 可能抛出的异常：SqlParseException（SQL解析异常）、ValidationException（验证异常）、RelConversionException（关系转换异常）
  @Test void testCalciteEscapeOracle()
      throws SqlParseException, ValidationException, RelConversionException {
    // SQL 语句说明：
    // 1. "localtime" - 用双引号引用，表示这是表中的列名（不是函数）
    // 2. localtime - 不带引号，表示这是 SQL 标准函数，返回当前时间
    // 3. "current_timestamp" - 用双引号引用，表示这是表中的列名（不是函数）
    // 4. current_timestamp - 不带引号，表示这是 SQL 标准函数，返回当前时间戳
    String sql = "select \"localtime\", localtime, "
        + "\"current_timestamp\", current_timestamp from TMP";
    // 使用 Oracle 方言运行查询并验证结果
    runProjectQueryWithLex(Lex.ORACLE, sql);
  }

  // 测试方法：测试 MySQL 方言下的标识符转义机制
  // MySQL 使用反引号（`）来引用标识符（表名、列名等）
  // 可能抛出的异常：SqlParseException（SQL解析异常）、ValidationException（验证异常）、RelConversionException（关系转换异常）
  @Test void testCalciteEscapeMySql()
      throws SqlParseException, ValidationException, RelConversionException {
    // SQL 语句说明：
    // 1. `localtime` - 用反引号引用，表示这是表中的列名（不是函数）
    // 2. localtime - 不带引号，表示这是 SQL 标准函数，返回当前时间
    // 3. `current_timestamp` - 用反引号引用，表示这是表中的列名（不是函数）
    // 4. current_timestamp - 不带引号，表示这是 SQL 标准函数，返回当前时间戳
    String sql = "select `localtime`, localtime, `current_timestamp`, current_timestamp from TMP";
    // 使用 MySQL 方言运行查询并验证结果
    runProjectQueryWithLex(Lex.MYSQL, sql);
  }

  // 测试方法：测试 MySQL ANSI 方言下的标识符转义机制
  // MySQL ANSI 模式使用双引号（"）来引用标识符，符合 ANSI SQL 标准
  // 可能抛出的异常：SqlParseException（SQL解析异常）、ValidationException（验证异常）、RelConversionException（关系转换异常）
  @Test void testCalciteEscapeMySqlAnsi()
      throws SqlParseException, ValidationException, RelConversionException {
    // SQL 语句说明：
    // 1. "localtime" - 用双引号引用，表示这是表中的列名（不是函数）
    // 2. localtime - 不带引号，表示这是 SQL 标准函数，返回当前时间
    // 3. "current_timestamp" - 用双引号引用，表示这是表中的列名（不是函数）
    // 4. current_timestamp - 不带引号，表示这是 SQL 标准函数，返回当前时间戳
    // 注意：MySQL ANSI 模式下使用双引号而不是反引号
    String sql = "select \"localtime\", localtime, "
        + "\"current_timestamp\", current_timestamp from TMP";
    // 使用 MySQL ANSI 方言运行查询并验证结果
    runProjectQueryWithLex(Lex.MYSQL_ANSI, sql);
  }

  // 测试方法：测试 SQL Server 方言下的标识符转义机制
  // SQL Server 使用方括号（[ ]）来引用标识符（表名、列名等）
  // 可能抛出的异常：SqlParseException（SQL解析异常）、ValidationException（验证异常）、RelConversionException（关系转换异常）
  @Test void testCalciteEscapeSqlServer()
      throws SqlParseException, ValidationException, RelConversionException {
    // SQL 语句说明：
    // 1. [localtime] - 用方括号引用，表示这是表中的列名（不是函数）
    // 2. localtime - 不带引号，表示这是 SQL 标准函数，返回当前时间
    // 3. [current_timestamp] - 用方括号引用，表示这是表中的列名（不是函数）
    // 4. current_timestamp - 不带引号，表示这是 SQL 标准函数，返回当前时间戳
    String sql = "select [localtime], localtime, [current_timestamp], current_timestamp from TMP";
    // 使用 SQL Server 方言运行查询并验证结果
    runProjectQueryWithLex(Lex.SQL_SERVER, sql);
  }

  // 测试方法：测试 Java 方言下的标识符转义机制
  // Java 方言使用反引号（`）来引用标识符，与 MySQL 类似
  // 这种模式通常用于在 Java 代码中嵌入 SQL 时使用
  // 可能抛出的异常：SqlParseException（SQL解析异常）、ValidationException（验证异常）、RelConversionException（关系转换异常）
  @Test void testCalciteEscapeJava()
      throws SqlParseException, ValidationException, RelConversionException {
    // SQL 语句说明：
    // 1. `localtime` - 用反引号引用，表示这是表中的列名（不是函数）
    // 2. localtime - 不带引号，表示这是 SQL 标准函数，返回当前时间
    // 3. `current_timestamp` - 用反引号引用，表示这是表中的列名（不是函数）
    // 4. current_timestamp - 不带引号，表示这是 SQL 标准函数，返回当前时间戳
    String sql = "select `localtime`, localtime, `current_timestamp`, current_timestamp from TMP";
    // 使用 Java 方言运行查询并验证结果
    runProjectQueryWithLex(Lex.JAVA, sql);
  }
}
