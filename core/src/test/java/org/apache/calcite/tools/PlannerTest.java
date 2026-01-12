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
package org.apache.calcite.tools; // Apache Calcite工具包，包含Planner等核心工具类

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 可枚举调用约定，定义可枚举关系表达式的约定
import org.apache.calcite.adapter.enumerable.EnumerableProject; // 可枚举投影节点，用于将关系表达式转换为可执行的Java代码
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 可枚举规则集合，包含将逻辑节点转换为可枚举节点的规则
import org.apache.calcite.adapter.enumerable.EnumerableTableScan; // 可枚举表扫描节点，用于扫描表并生成可执行的Java代码
import org.apache.calcite.adapter.java.ReflectiveSchema; // 反射模式，通过反射将Java对象暴露为数据库表
import org.apache.calcite.adapter.jdbc.JdbcConvention; // JDBC调用约定，定义JDBC关系表达式的约定
import org.apache.calcite.adapter.jdbc.JdbcImplementor; // JDBC实现器，用于将关系表达式转换为SQL语句
import org.apache.calcite.adapter.jdbc.JdbcRel; // JDBC关系表达式接口，标记可以通过JDBC执行的关系表达式
import org.apache.calcite.adapter.jdbc.JdbcRules; // JDBC规则集合，包含将逻辑节点转换为JDBC节点的规则
import org.apache.calcite.config.Lex; // 词法分析配置，定义SQL解析的词法规则
import org.apache.calcite.plan.ConventionTraitDef; // 调用约定特征定义，定义关系表达式的调用约定特征
import org.apache.calcite.plan.RelOptCluster; // 关系优化集群，包含共享的优化器组件
import org.apache.calcite.plan.RelOptPlanner; // 关系优化规划器，负责优化关系表达式
import org.apache.calcite.plan.RelOptPredicateList; // 关系优化谓词列表，表示关系表达式的谓词集合
import org.apache.calcite.plan.RelOptRule; // 关系优化规则，定义如何转换关系表达式
import org.apache.calcite.plan.RelOptRuleCall; // 关系优化规则调用，表示规则的调用上下文
import org.apache.calcite.plan.RelOptTable; // 关系优化表，表示优化过程中的表
import org.apache.calcite.plan.RelOptUtil; // 关系优化工具类，提供关系表达式操作的工具方法
import org.apache.calcite.plan.RelRule; // 关系规则基类，提供规则实现的通用框架
import org.apache.calcite.plan.RelTraitDef; // 关系特征定义，定义关系表达式的特征
import org.apache.calcite.plan.RelTraitSet; // 关系特征集合，表示关系表达式的一组特征
import org.apache.calcite.rel.RelCollationTraitDef; // 排序特征定义，定义关系表达式的排序特征
import org.apache.calcite.rel.RelNode; // 关系表达式节点，表示关系代数操作
import org.apache.calcite.rel.RelRoot; // 关系根节点，表示完整的关系表达式树
import org.apache.calcite.rel.convert.ConverterRule; // 转换规则基类，用于将一种约定转换为另一种约定
import org.apache.calcite.rel.core.JoinRelType; // 连接关系类型，定义INNER/LEFT/RIGHT/FULL等连接类型
import org.apache.calcite.rel.core.RelFactories; // 关系工厂，提供创建关系表达式的工厂方法
import org.apache.calcite.rel.core.TableScan; // 表扫描节点，表示扫描表的操作
import org.apache.calcite.rel.logical.LogicalFilter; // 逻辑过滤节点，表示过滤操作
import org.apache.calcite.rel.logical.LogicalProject; // 逻辑投影节点，表示投影操作
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 关系元数据查询，用于查询关系表达式的元数据
import org.apache.calcite.rel.rules.CoreRules; // 核心规则集合，包含Calcite的核心优化规则
import org.apache.calcite.rel.rules.ProjectMergeRule; // 投影合并规则，用于合并相邻的投影节点
import org.apache.calcite.rel.rules.PruneEmptyRules; // 空关系剪枝规则，用于移除产生空结果的关系节点
import org.apache.calcite.rel.rules.UnionMergeRule; // 合并规则，用于合并相邻的UNION节点
import org.apache.calcite.rel.type.DelegatingTypeSystem; // 委托类型系统，允许自定义类型系统行为
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型，表示关系表达式的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 关系数据类型系统，定义类型系统的行为
import org.apache.calcite.schema.SchemaPlus; // 模式扩展接口，允许向模式中添加表和函数
import org.apache.calcite.schema.impl.ScalarFunctionImpl; // 标量函数实现，用于实现自定义标量函数
import org.apache.calcite.sql.SqlAggFunction; // SQL聚合函数接口，定义聚合函数的行为
import org.apache.calcite.sql.SqlCall; // SQL调用节点，表示函数调用
import org.apache.calcite.sql.SqlDialect; // SQL方言，定义不同数据库的SQL语法差异
import org.apache.calcite.sql.SqlExplainFormat; // SQL解释格式，定义解释计划的输出格式
import org.apache.calcite.sql.SqlExplainLevel; // SQL解释级别，定义解释计划的详细程度
import org.apache.calcite.sql.SqlFunctionCategory; // SQL函数类别，定义函数的分类
import org.apache.calcite.sql.SqlKind; // SQL节点类型，标识SQL节点的种类
import org.apache.calcite.sql.SqlNode; // SQL节点，表示SQL语法树中的一个节点
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // SQL标准操作符表，包含标准SQL操作符
import org.apache.calcite.sql.parser.SqlParseException; // SQL解析异常，表示SQL解析失败
import org.apache.calcite.sql.parser.SqlParser; // SQL解析器，负责将SQL文本解析为语法树
import org.apache.calcite.sql.test.SqlTests; // SQL测试工具类，提供SQL测试的辅助方法
import org.apache.calcite.sql.type.OperandTypes; // 操作数类型检查器，用于验证操作数类型
import org.apache.calcite.sql.type.ReturnTypes; // 返回类型推断器，用于推断函数的返回类型
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称，枚举SQL标准类型
import org.apache.calcite.sql.util.SqlOperatorTables; // SQL操作符表工具，用于组合多个操作符表
import org.apache.calcite.sql.validate.SqlValidator; // SQL验证器，负责验证SQL语句的语义正确性
import org.apache.calcite.sql.validate.SqlValidatorScope; // SQL验证器作用域，表示验证过程中的作用域
import org.apache.calcite.test.CalciteAssert; // Calcite断言工具，提供测试断言方法
import org.apache.calcite.test.RelBuilderTest; // 关系构建器测试，提供RelBuilder的测试辅助
import org.apache.calcite.test.schemata.tpch.TpchSchema; // TPC-H测试模式，提供TPC-H基准测试的数据模式
import org.apache.calcite.util.Optionality; // 可选性，表示某个特性是否可选
import org.apache.calcite.util.Smalls; // 小型测试工具类，提供测试用的辅助类和方法
import org.apache.calcite.util.Util; // 通用工具类，提供各种实用方法

import com.google.common.base.Throwables; // Google Guava异常工具类，用于处理异常堆栈
import com.google.common.collect.ImmutableList; // Google Guava不可变列表，提供线程安全的不可变集合

import org.hamcrest.Matcher; // Hamcrest匹配器接口，用于编写灵活的断言
import org.immutables.value.Value; // Immutables值对象注解，用于生成不可变值对象
import org.junit.jupiter.api.Assertions; // JUnit5断言工具类，提供各种断言方法
import org.junit.jupiter.api.Disabled; // JUnit5禁用测试注解，标记测试为禁用状态
import org.junit.jupiter.api.Tag; // JUnit5标签注解，用于对测试进行分类
import org.junit.jupiter.api.Test; // JUnit5测试注解，标记测试方法

import java.util.ArrayList; // Java动态数组列表，提供可变大小的数组实现
import java.util.List; // Java列表接口，定义有序集合的行为

import static org.apache.calcite.test.Matchers.sortsAs; // Calcite测试匹配器，用于验证排序结果

import static org.hamcrest.CoreMatchers.containsString; // Hamcrest字符串包含匹配器
import static org.hamcrest.CoreMatchers.equalTo; // Hamcrest相等匹配器
import static org.hamcrest.CoreMatchers.is; // Hamcrest是匹配器
import static org.hamcrest.CoreMatchers.notNullValue; // Hamcrest非空匹配器
import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest断言方法，使用匹配器进行断言
import static org.junit.jupiter.api.Assertions.assertFalse; // JUnit5假断言方法
import static org.junit.jupiter.api.Assertions.fail; // JUnit5失败方法，标记测试为失败

/**
 * 单元测试类，用于测试 {@link Planner} 接口的功能
 *
 * Planner是Calcite的核心组件，负责将SQL语句转换为可执行的关系表达式，
 * 并进行优化生成最终的执行计划。
 *
 * 本测试类覆盖了Planner的以下核心功能：
 * 1. SQL解析（parse）：将SQL文本解析为抽象语法树（SqlNode）
 * 2. SQL验证（validate）：验证SQL语句的语义正确性
 * 3. 关系转换（rel）：将验证后的SQL转换为关系表达式（RelNode）
 * 4. 优化转换（transform）：应用优化规则，将逻辑计划转换为物理执行计划
 *
 * 测试场景包括：
 * - 基本查询的解析、验证和转换
 * - JOIN操作的优化（包括多表连接、外连接、笛卡尔积等）
 * - 投影和过滤的优化
 * - 排序操作的优化
 * - 集合操作（UNION）的优化
 * - 用户自定义函数和聚合函数的支持
 * - 元数据查询和谓词上推
 * - 不同SQL方言的支持
 * - 自定义类型系统的支持
 * - 视图的支持
 * - 相关子查询的去关联化
 */
class PlannerTest { // Planner单元测试类，测试Planner接口的各项功能
  private void checkParseAndConvert(String query, // 输入的SQL查询字符串
      String queryFromParseTree, // 期望的解析树字符串表示
      String expectedRelExpr) throws Exception { // 期望的关系表达式字符串表示
    // 获取Planner实例，traitDefs为null表示使用默认的特征定义
    Planner planner = getPlanner(null); // 创建规划器实例
    // 解析SQL查询，将SQL文本转换为SqlNode（抽象语法树）
    SqlNode parse = planner.parse(query); // 执行SQL解析，生成语法树
    // 验证解析结果是否与期望的解析树字符串一致（使用Linux换行符格式化）
    assertThat(Util.toLinux(parse.toString()), equalTo(queryFromParseTree)); // 断言解析结果正确

    // 验证SqlNode，进行语义检查（如表名、列名是否存在，类型是否匹配等）
    SqlNode validate = planner.validate(parse); // 执行语义验证
    // 将验证后的SqlNode转换为关系表达式，并获取投影节点
    RelNode rel = planner.rel(validate).project(); // 转换为关系表达式树
    // 验证生成的关系表达式是否与期望的字符串表示一致
    assertThat(toString(rel), equalTo(expectedRelExpr)); // 断言转换结果正确
  }

  /** 测试基本的SQL解析和转换功能
   * 验证Planner能够正确执行以下操作：
   * 1. 解析带有LIKE条件的SELECT语句
   * 2. 验证解析后的语法树格式
   * 3. 将SQL转换为逻辑关系表达式
   * 4. 验证生成的逻辑计划结构
   */
  @Test void testParseAndConvert() throws Exception { // 测试解析和转换功能
    // 调用辅助方法，测试带有LIKE条件的查询
    checkParseAndConvert( // 执行解析和转换检查
        "select * from \"emps\" where \"name\" like '%e%'", // 输入SQL：查询name包含'e'的员工

        "SELECT *\n" // 期望的解析树格式（使用反引号引用标识符）
            + "FROM `emps`\n"
            + "WHERE `name` LIKE '%e%'",

        "LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n" // 期望的逻辑计划
        + "  LogicalFilter(condition=[LIKE($2, '%e%')])\n" // 过滤条件：name字段包含'e'
        + "    LogicalTableScan(table=[[hr, emps]])\n"); // 扫描hr模式下的emps表
  }

  /** 测试使用默认标识符最大长度时解析超长标识符的行为
   * 验证当标识符长度超过默认限制时，Planner会抛出SqlParseException异常
   */
  @Test void testParseIdentifierMaxLengthWithDefault() { // 测试默认标识符长度限制
    // 断言解析带有超长别名的查询会抛出SqlParseException异常
    Assertions.assertThrows(SqlParseException.class, () -> { // 验证抛出解析异常
      Planner planner = getPlanner(null, SqlParser.config()); // 创建使用默认配置的规划器
      // 尝试解析带有超长别名的查询（别名长度超过默认限制）
      planner.parse("select name as " // 解析带有超长别名的查询
          + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa from \"emps\""); // 超长别名导致解析失败
    });
  }

  /** 测试增加标识符最大长度后解析超长标识符的行为
   * 验证当将标识符最大长度配置为512时，Planner能够成功解析超长标识符
   */
  @Test void testParseIdentifierMaxLengthWithIncreased() throws Exception { // 测试增加标识符长度限制
    // 创建规划器，配置标识符最大长度为512
    Planner planner = // 创建规划器实例
        getPlanner(null, SqlParser.config().withIdentifierMaxLength(512)); // 设置标识符最大长度为512
    // 解析带有超长别名的查询，此时应该成功（因为512 > 别名长度）
    planner.parse("select name as " // 解析带有超长别名的查询
        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa from \"emps\""); // 超长别名解析成功
  }

  /** 单元测试：解析、验证和转换带有ORDER BY和OFFSET的查询
   * 验证Planner能够正确处理以下SQL特性：
   * 1. ORDER BY子句：按指定字段排序
   * 2. OFFSET子句：跳过指定数量的行
   * 3. 生成正确的逻辑计划，包含LogicalSort节点
   */
  @Test void testParseAndConvertWithOrderByAndOffset() throws Exception { // 测试带有排序和偏移的查询
    // 调用辅助方法，测试带有ORDER BY和OFFSET的查询
    checkParseAndConvert( // 执行解析和转换检查
        "select * from \"emps\" " // 输入SQL：查询所有员工并按deptno排序，跳过前10行
            + "order by \"emps\".\"deptno\" offset 10", // ORDER BY和OFFSET子句

        "SELECT *\n" // 期望的解析树格式
            + "FROM `emps`\n"
            + "ORDER BY `emps`.`deptno`\n" // ORDER BY子句
            + "OFFSET 10 ROWS", // OFFSET子句

        "LogicalSort(sort0=[$1], dir0=[ASC], offset=[10])\n" // 期望的逻辑计划：排序节点，按第1列（deptno）升序排列，偏移10行
        + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n" // 投影所有字段
        + "    LogicalTableScan(table=[[hr, emps]])\n"); // 扫描emps表
  }

  /**
   * 辅助方法：将关系表达式转换为字符串表示
   * 用于在测试中验证生成的执行计划
   *
   * @param rel 要转换的关系表达式节点
   * @return 关系表达式的字符串表示，使用TEXT格式和EXPPLAN_ATTRIBUTES详细级别
   */
  private String toString(RelNode rel) { // 将关系表达式转换为字符串
    // 使用RelOptUtil工具类将关系表达式转储为文本格式
    return Util.toLinux( // 转换为Linux换行符格式
        RelOptUtil.dumpPlan("", rel, SqlExplainFormat.TEXT, // 使用文本格式转储计划
            SqlExplainLevel.EXPPLAN_ATTRIBUTES)); // 使用EXPPLAN_ATTRIBUTES详细级别（包含所有属性）
  }

  /** 测试SQL解析失败的情况
   * 验证当SQL语法错误时，Planner能够正确抛出SqlParseException异常
   * 测试场景：SELECT语句中有两个连续的*号（语法错误）
   */
  @Test void testParseFails() { // 测试解析失败场景
    Planner planner = getPlanner(null); // 创建规划器实例
    try { // 尝试解析错误的SQL
      SqlNode parse = // 解析SQL语句
          planner.parse("select * * from \"emps\""); // 语法错误：两个连续的*号
      fail("expected error, got " + parse); // 如果没有抛出异常，测试失败
    } catch (SqlParseException e) { // 捕获解析异常
      // 验证异常消息包含预期的错误信息（指出在第1行第10列遇到了*号）
      assertThat(e.getMessage(), // 断言异常消息
          containsString("Encountered \"*\" at line 1, column 10.")); // 验证错误位置
    }
  }

  /** 测试SQL验证失败的情况
   * 验证当SQL语义错误时，Planner能够正确抛出ValidationException异常
   * 测试场景：SELECT语句中引用了不存在的列名"Xname"
   */
  @Test void testValidateFails() throws SqlParseException { // 测试验证失败场景
    Planner planner = getPlanner(null); // 创建规划器实例
    // 解析SQL语句（语法正确，但语义有错误）
    SqlNode parse = // 解析SQL语句
        planner.parse("select * from \"emps\" where \"Xname\" like '%e%'"); // 引用不存在的列Xname
    // 验证解析结果（解析阶段不会检查列是否存在）
    assertThat(Util.toLinux(parse.toString()), // 断言解析结果
        equalTo("SELECT *\n" // 解析成功，生成语法树
            + "FROM `emps`\n"
            + "WHERE `Xname` LIKE '%e%'"));

    try { // 尝试验证解析后的SQL
      SqlNode validate = planner.validate(parse); // 验证SQL语义
      fail("expected error, got " + validate); // 如果没有抛出异常，测试失败
    } catch (ValidationException e) { // 捕获验证异常
      // 验证异常消息包含预期的错误信息（指出列'Xname'不存在）
      assertThat(Throwables.getStackTraceAsString(e), // 获取完整的异常堆栈
          containsString("Column 'Xname' not found in any table")); // 验证错误信息
      // ok - 异常符合预期
    }
  }

  @Test void testValidateUserDefinedAggregate() throws Exception { // 测试用户自定义聚合函数的验证
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 构建配置
        .defaultSchema( // 设置默认Schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR)) // 添加HR测试Schema
        .operatorTable( // 设置操作符表
            SqlOperatorTables.chain(SqlStdOperatorTable.instance(), // 链接标准操作符表
                SqlOperatorTables.of(new MyCountAggFunction()))) // 添加自定义聚合函数MY_COUNT
        .build(); // 构建配置
    final Planner planner = Frameworks.getPlanner(config); // 创建规划器
    SqlNode parse = // 解析SQL
        planner.parse("select \"deptno\", my_count(\"empid\") from \"emps\"\n" // 使用自定义聚合函数MY_COUNT
            + "group by \"deptno\""); // 按deptno分组
    assertThat(Util.toLinux(parse.toString()), // 断言解析结果
        equalTo("SELECT `deptno`, `MY_COUNT`(`empid`)\n" // MY_COUNT被识别为聚合函数
            + "FROM `emps`\n"
            + "GROUP BY `deptno`"));

    // MY_COUNT is recognized as an aggregate function, and therefore it is OK
    // that its argument empid is not in the GROUP BY clause.
    SqlNode validate = planner.validate(parse); // 验证SQL语义
    assertThat(validate, notNullValue()); // 断言验证成功

    // The presence of an aggregate function in the SELECT clause causes it
    // to become an aggregate query. Non-aggregate expressions become illegal.
    planner.close(); // 关闭规划器
    planner.reset(); // 重置规划器状态
    parse = planner.parse("select \"deptno\", count(1) from \"emps\""); // 解析带有count聚合的查询
    try { // 尝试验证
      validate = planner.validate(parse); // 验证SQL（应该失败，因为deptno不在GROUP BY中）
      fail("expected exception, got " + validate); // 如果没有抛出异常，测试失败
    } catch (ValidationException e) { // 捕获验证异常
      assertThat(e.getCause().getCause().getMessage(), // 获取嵌套异常消息
          containsString("Expression 'deptno' is not being grouped")); // 验证错误信息
    }
  }

  /** 测试用例：验证Planner能够找到添加到Schema中的用户自定义函数（UDF）
   * 解决JIRA issue CALCITE-3547
   * 测试场景：将自定义标量函数my_plus添加到Schema中，并在SQL中使用它
   */
  @Test void testValidateUserDefinedFunctionInSchema() throws Exception { // 测试Schema中的用户自定义函数
    SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema
    rootSchema.add("my_plus", // 添加自定义函数到Schema
        ScalarFunctionImpl.create(Smalls.MY_PLUS_EVAL_METHOD)); // 创建my_plus函数实现
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 构建配置
        .defaultSchema( // 设置默认Schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR)) // 添加HR测试Schema
        .build(); // 构建配置
    final Planner planner = Frameworks.getPlanner(config); // 创建规划器
    final String sql = "select \"my_plus\"(\"deptno\", 100) as \"p\"\n" // SQL：使用my_plus函数
        + "from \"hr\".\"emps\""; // 从emps表查询
    SqlNode parse = planner.parse(sql); // 解析SQL
    SqlNode validate = planner.validate(parse); // 验证SQL
    assertThat(Util.toLinux(validate.toString()), // 断言验证结果
        equalTo("SELECT `my_plus`(`emps`.`deptno`, 100) AS `p`\n" // my_plus函数被正确识别
            + "FROM `hr`.`emps` AS `emps`")); // 表别名正确
  }

  /**
   * 辅助方法：获取使用默认解析器配置的Planner实例
   *
   * @param traitDefs 关系特征定义列表，可为null表示使用默认值
   * @param programs 优化程序数组
   * @return 配置好的Planner实例
   */
  private Planner getPlanner(List<RelTraitDef> traitDefs, Program... programs) { // 获取Planner实例（使用默认解析器配置）
    return getPlanner(traitDefs, SqlParser.Config.DEFAULT, programs); // 调用重载方法
  }

  /**
   * 辅助方法：获取使用指定配置的Planner实例
   * 这是测试类中创建Planner的主要方法，配置了：
   * 1. 解析器配置（parserConfig）
   * 2. 默认Schema（HR测试Schema）
   * 3. 特征定义（traitDefs）
   * 4. 优化程序（programs）
   *
   * @param traitDefs 关系特征定义列表，可为null表示使用默认值
   * @param parserConfig SQL解析器配置
   * @param programs 优化程序数组
   * @return 配置好的Planner实例
   */
  private Planner getPlanner(List<RelTraitDef> traitDefs, // 关系特征定义列表
                             SqlParser.Config parserConfig, // SQL解析器配置
                             Program... programs) { // 优化程序数组
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 构建框架配置
        .parserConfig(parserConfig) // 设置解析器配置
        .defaultSchema( // 设置默认Schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR)) // 添加HR测试Schema
        .traitDefs(traitDefs) // 设置特征定义
        .programs(programs) // 设置优化程序
        .build(); // 构建配置
    return Frameworks.getPlanner(config); // 返回Planner实例
  }

  /** 测试：验证Planner在转换未验证的SqlNode时会抛出错误
   * Planner遵循状态机模式，必须按顺序执行：parse -> validate -> rel
   * 如果跳过validate直接调用rel，应该抛出IllegalArgumentException
   */
  @Test void testConvertWithoutValidateFails() throws Exception { // 测试未验证就转换的情况
    Planner planner = getPlanner(null); // 创建规划器
    SqlNode parse = planner.parse("select * from \"emps\""); // 解析SQL
    try { // 尝试直接转换（跳过验证）
      RelRoot rel = planner.rel(parse); // 直接调用rel，应该失败
      fail("expected error, got " + rel); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获非法参数异常
      assertThat(e.getMessage(), // 断言异常消息
          containsString( // 包含状态转换错误信息
              "cannot move from STATE_3_PARSED to STATE_4_VALIDATED")); // 不能从已解析状态直接跳到已验证状态
    }
  }

  /**
   * 辅助方法：测试关系表达式的上推谓词元数据
   * 上推谓词（pulled-up predicates）是指可以从子节点向上推到父节点的过滤条件
   * 这对于查询优化非常重要，可以尽早过滤数据
   *
   * @param sql 要测试的SQL语句
   * @param expectedPredicates 期望的上推谓词列表（字符串表示）
   */
  private void checkMetadataPredicates(String sql, // 输入SQL语句
      String expectedPredicates) throws Exception { // 期望的谓词列表
    Planner planner = getPlanner(null); // 创建规划器
    SqlNode parse = planner.parse(sql); // 解析SQL
    SqlNode validate = planner.validate(parse); // 验证SQL
    RelNode rel = planner.rel(validate).project(); // 转换为关系表达式
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery(); // 获取元数据查询器
    final RelOptPredicateList predicates = mq.getPulledUpPredicates(rel); // 获取上推谓词列表
    assertThat(predicates.pulledUpPredicates, sortsAs(expectedPredicates)); // 断言谓词列表正确
  }

  /** 测试：验证可以从UNION节点上推的谓词
   * UNION ALL的两个分支都有不同的过滤条件，上推后的谓词是两个条件的OR
   * 第一个分支：deptno < 10
   * 第二个分支：empid > 2
   * 上推结果：OR(<($1, 10), >($0, 2))
   */
  @Test void testMetadataUnionPredicates() throws Exception { // 测试UNION的谓词上推
    checkMetadataPredicates( // 检查谓词上推
        "select * from \"emps\" where \"deptno\" < 10\n" // 第一个分支：deptno < 10
            + "union all\n" // UNION ALL
            + "select * from \"emps\" where \"empid\" > 2", // 第二个分支：empid > 2
        "[OR(<($1, 10), >($0, 2))]"); // 期望的上推谓词：两个条件的OR
  }

  /** 测试用例：验证UNION中一个分支无过滤条件时的谓词上推
   * 解决JIRA issue CALCITE-443
   * 当UNION ALL的一个分支没有过滤条件时，无法上推任何谓词
   * 第一个分支：deptno < 10
   * 第二个分支：无过滤条件
   * 上推结果：[]（空列表）
   */
  @Test void testMetadataUnionPredicates2() throws Exception { // 测试UNION的谓词上推（一个分支无过滤）
    checkMetadataPredicates( // 检查谓词上推
        "select * from \"emps\" where \"deptno\" < 10\n" // 第一个分支：deptno < 10
            + "union all\n" // UNION ALL
            + "select * from \"emps\"", // 第二个分支：无过滤条件
        "[]"); // 期望的上推谓词：空列表（无法上推）
  }

  /** 测试：验证UNION的两个分支有共同过滤条件时的谓词上推
   * 第一个分支：deptno < 10
   * 第二个分支：deptno < 10 AND empid > 1
   * 上推结果：deptno < 10（两个分支的共同条件）
   */
  @Test void testMetadataUnionPredicates3() throws Exception { // 测试UNION的谓词上推（共同条件）
    checkMetadataPredicates( // 检查谓词上推
        "select * from \"emps\" where \"deptno\" < 10\n" // 第一个分支：deptno < 10
            + "union all\n" // UNION ALL
            + "select * from \"emps\" where \"deptno\" < 10 and \"empid\" > 1", // 第二个分支：deptno < 10 AND empid > 1
        "[<($1, 10)]"); // 期望的上推谓词：deptno < 10（共同条件）
  }

  /** 测试：验证UNION的分支有OR条件时的谓词上推
   * 第一个分支：deptno < 10
   * 第二个分支：deptno < 10 OR empid > 1
   * 上推结果：OR(<($1, 10), >($0, 1))（两个分支的OR条件的组合）
   */
  @Test void testMetadataUnionPredicates4() throws Exception { // 测试UNION的谓词上推（OR条件）
    checkMetadataPredicates( // 检查谓词上推
        "select * from \"emps\" where \"deptno\" < 10\n" // 第一个分支：deptno < 10
            + "union all\n" // UNION ALL
            + "select * from \"emps\" where \"deptno\" < 10 or \"empid\" > 1", // 第二个分支：deptno < 10 OR empid > 1
        "[OR(<($1, 10), >($0, 1))]"); // 期望的上推谓词：OR(<($1, 10), >($0, 1))
  }

  /** 测试：验证UNION的分支有false条件时的谓词上推
   * 第一个分支：deptno < 10
   * 第二个分支：deptno < 10 AND false（永远为false）
   * 上推结果：deptno < 10（忽略false条件）
   */
  @Test void testMetadataUnionPredicates5() throws Exception { // 测试UNION的谓词上推（false条件）
    final String sql = "select * from \"emps\" where \"deptno\" < 10\n" // 第一个分支：deptno < 10
        + "union all\n" // UNION ALL
        + "select * from \"emps\" where \"deptno\" < 10 and false"; // 第二个分支：deptno < 10 AND false
    checkMetadataPredicates(sql, "[<($1, 10)]"); // 期望的上推谓词：deptno < 10（忽略false）
  }

  /** 测试：验证从GROUP BY ()形式的聚合节点上推谓词
   * GROUP BY ()表示没有分组键的聚合（如COUNT(*)）
   * 这种聚合可以将空关系转换为单行关系（count=0）
   * 因此不能上推false谓词，因为false谓词会被忽略
   */
  @Test void testMetadataAggregatePredicates() throws Exception { // 测试GROUP BY ()的谓词上推
    checkMetadataPredicates("select count(*) from \"emps\" where false", // COUNT(*)聚合，where false
        "[]"); // 期望的上推谓词：空列表（false不能上推）
  }

  /** 测试：验证从有分组键的聚合节点上推谓词
   * 当聚合有非空的分组键时，false谓词意味着关系为空
   * 因为没有行能满足false条件
   * 此时可以上推false谓词
   */
  @Test void testMetadataAggregatePredicates2() throws Exception { // 测试有分组键的聚合的谓词上推
    final String sql = "select \"deptno\", count(\"deptno\")\n" // 聚合查询，按deptno分组
        + "from \"emps\" where false\n" // where false（没有行满足）
        + "group by \"deptno\""; // 按deptno分组
    checkMetadataPredicates(sql, "[false]"); // 期望的上推谓词：false（可以上推）
  }

  /** 测试：验证从有分组键和真实过滤条件的聚合节点上推谓词
   * 当聚合有非空的分组键和真实的过滤条件时
   * 可以上推该过滤条件
   */
  @Test void testMetadataAggregatePredicates3() throws Exception { // 测试有真实条件的聚合的谓词上推
    final String sql = "select \"deptno\", count(\"deptno\")\n" // 聚合查询，按deptno分组
        + "from \"emps\" where \"deptno\" > 10\n" // where deptno > 10
        + "group by \"deptno\""; // 按deptno分组
    checkMetadataPredicates(sql, "[>($0, 10)]"); // 期望的上推谓词：deptno > 10
  }

  /** 单元测试：完整的SQL处理流程 - 解析、验证、转换和计划
   * 这个测试展示了Planner的完整工作流程：
   * 1. parse: 解析SQL为语法树
   * 2. validate: 验证SQL语义
   * 3. rel: 转换为逻辑关系表达式
   * 4. transform: 应用优化规则，转换为物理执行计划
   *
   * 使用的优化规则：
   * - FILTER_MERGE: 合并相邻的过滤节点
   * - ENUMERABLE_TABLE_SCAN_RULE: 将逻辑表扫描转换为可枚举表扫描
   * - ENUMERABLE_FILTER_RULE: 将逻辑过滤转换为可枚举过滤
   * - ENUMERABLE_PROJECT_RULE: 将逻辑投影转换为可枚举投影
   */
  @Test void testPlan() throws Exception { // 测试完整的SQL处理流程
    // 创建优化程序，包含一组优化规则
    Program program = // 定义优化程序
        Programs.ofRules( // 从规则列表创建优化程序
            CoreRules.FILTER_MERGE, // 过滤合并规则：合并相邻的过滤节点
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE, // 可枚举表扫描规则
            EnumerableRules.ENUMERABLE_FILTER_RULE, // 可枚举过滤规则
            EnumerableRules.ENUMERABLE_PROJECT_RULE); // 可枚举投影规则
    Planner planner = getPlanner(null, program); // 创建规划器，指定优化程序
    SqlNode parse = planner.parse("select * from \"emps\""); // 步骤1: 解析SQL
    SqlNode validate = planner.validate(parse); // 步骤2: 验证SQL语义
    RelNode convert = planner.rel(validate).project(); // 步骤3: 转换为关系表达式
    RelTraitSet traitSet = convert.getTraitSet() // 获取特征集合
        .replace(EnumerableConvention.INSTANCE); // 替换为可枚举约定
    RelNode transform = planner.transform(0, traitSet, convert); // 步骤4: 应用优化规则转换
    assertThat(toString(transform), // 断言转换后的物理计划
        equalTo( // 期望的物理执行计划
            "EnumerableProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n" // 可枚举投影节点
            + "  EnumerableTableScan(table=[[hr, emps]])\n")); // 可枚举表扫描节点
  }

  /** Unit test that parses, validates, converts and plans. */
  @Test void trimEmptyUnion2() throws Exception {
    checkUnionPruning("values(1) union all select * from (values(2)) where false",
        "EnumerableValues(tuples=[[{ 1 }]])\n");

    checkUnionPruning("select * from (values(2)) where false union all values(1)",
        "EnumerableValues(tuples=[[{ 1 }]])\n");
  }

  @Test void trimEmptyUnion31() throws Exception {
    emptyUnions31();
  }

  @Test void trimEmptyUnion31withUnionMerge() throws Exception {
    emptyUnions31(CoreRules.UNION_MERGE);
  }

  private void emptyUnions31(UnionMergeRule... extraRules)
      throws SqlParseException, ValidationException, RelConversionException {
    String plan = "EnumerableValues(tuples=[[{ 1 }]])\n";
    checkUnionPruning("values(1)"
            + " union all select * from (values(2)) where false"
            + " union all select * from (values(3)) where false",
        plan, extraRules);

    checkUnionPruning("select * from (values(2)) where false"
            + " union all values(1)"
            + " union all select * from (values(3)) where false",
        plan, extraRules);

    checkUnionPruning("select * from (values(2)) where false"
            + " union all select * from (values(3)) where false"
            + " union all values(1)",
        plan, extraRules);
  }

  @Disabled("[CALCITE-2773] java.lang.AssertionError: rel"
      + " [rel#69:EnumerableUnion.ENUMERABLE.[](input#0=RelSubset#78,input#1=RelSubset#71,all=true)]"
      + " has lower cost {4.0 rows, 4.0 cpu, 0.0 io} than best cost {5.0 rows, 5.0 cpu, 0.0 io}"
      + " of subset [rel#67:Subset#6.ENUMERABLE.[]]")
  @Test void trimEmptyUnion32() throws Exception {
    emptyUnions32();
  }

  @Test void trimEmptyUnion32withUnionMerge() throws Exception {
    emptyUnions32(CoreRules.UNION_MERGE);
  }

  private void emptyUnions32(UnionMergeRule... extraRules)
      throws SqlParseException, ValidationException, RelConversionException {
    String plan = "EnumerableUnion(all=[true])\n"
        + "  EnumerableValues(tuples=[[{ 1 }]])\n"
        + "  EnumerableValues(tuples=[[{ 2 }]])\n";

    checkUnionPruning("values(1)"
            + " union all select * from (values(3)) where false"
            + " union all values(2)",
        plan, extraRules);

    checkUnionPruning("select * from (values(2)) where false"
            + " union all values(1)"
            + " union all values(2)",
        plan, extraRules);
  }

  private void checkUnionPruning(String sql, String plan, RelOptRule... extraRules)
      throws SqlParseException, ValidationException, RelConversionException {
    ImmutableList.Builder<RelOptRule> rules = ImmutableList.builder();
    rules.add(PruneEmptyRules.UNION_INSTANCE,
        CoreRules.PROJECT_FILTER_VALUES_MERGE,
        EnumerableRules.ENUMERABLE_PROJECT_RULE,
        EnumerableRules.ENUMERABLE_FILTER_RULE,
        EnumerableRules.ENUMERABLE_VALUES_RULE,
        EnumerableRules.ENUMERABLE_UNION_RULE);
    rules.add(extraRules);
    Program program = Programs.ofRules(rules.build());
    Planner planner = getPlanner(null, program);
    SqlNode parse = planner.parse(sql);
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).project();
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat("Empty values should be removed from " + sql,
        toString(transform), equalTo(plan));
  }

  @Disabled("[CALCITE-2773] java.lang.AssertionError: rel"
      + " [rel#17:EnumerableUnion.ENUMERABLE.[](input#0=RelSubset#26,input#1=RelSubset#19,all=true)]"
      + " has lower cost {4.0 rows, 4.0 cpu, 0.0 io}"
      + " than best cost {5.0 rows, 5.0 cpu, 0.0 io} of subset [rel#15:Subset#5.ENUMERABLE.[]]")
  @Test void trimEmptyUnion32viaRelBuidler() {
    RelBuilder relBuilder = RelBuilder.create(RelBuilderTest.config().build());

    // This somehow blows up (see trimEmptyUnion32, the second case)
    // (values(1) union all select * from (values(3)) where false)
    // union all values(2)

    // Non-trivial filter is important for the test to fail
    RelNode relNode = relBuilder
        .values(new String[]{"x"}, "1")
        .values(new String[]{"x"}, "3")
        .filter(relBuilder.equals(relBuilder.field("x"), relBuilder.literal("30")))
        .union(true)
        .values(new String[]{"x"}, "2")
        .union(true)
        .build();

    RelOptPlanner planner = relNode.getCluster().getPlanner();
    RuleSet ruleSet =
        RuleSets.ofList(PruneEmptyRules.UNION_INSTANCE,
            CoreRules.FILTER_VALUES_MERGE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE,
            EnumerableRules.ENUMERABLE_FILTER_RULE,
            EnumerableRules.ENUMERABLE_VALUES_RULE,
            EnumerableRules.ENUMERABLE_UNION_RULE);
    Program program = Programs.of(ruleSet);

    RelTraitSet toTraits = relNode.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);

    RelNode output =
        program.run(planner, relNode, toTraits,
            ImmutableList.of(), ImmutableList.of());

    // Expected outcomes are:
    // 1) relation is optimized to simple VALUES
    // 2) the number of rule invocations is reasonable
    // 3) planner does not throw OutOfMemoryError
    assertThat("empty union should be pruned out of " + toString(relNode),
        Util.toLinux(toString(output)),
        equalTo("EnumerableUnion(all=[true])\n"
            + "  EnumerableValues(tuples=[[{ 1 }]])\n"
            + "  EnumerableValues(tuples=[[{ 2 }]])\n"));
  }

  /** Unit test that parses, validates, converts and
   * plans for query using ORDER BY. */
  @Test void testSortPlan() throws Exception {
    RuleSet ruleSet =
        RuleSets.ofList(CoreRules.SORT_REMOVE,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE,
            EnumerableRules.ENUMERABLE_SORT_RULE);
    Planner planner = getPlanner(null, Programs.of(ruleSet));
    final String sql = "select * from \"emps\" order by \"emps\".\"deptno\"";
    SqlNode parse = planner.parse(sql);
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).project();
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform),
        equalTo("EnumerableSort(sort0=[$1], dir0=[ASC])\n"
            + "  EnumerableProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n"
            + "    EnumerableTableScan(table=[[hr, emps]])\n"));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich EnumerableHashJoin operator with order preserving information</a>.
   *
   * <p>Since the left input to the join is sorted, and this join preserves
   * order, there shouldn't be any sort operator above the join.
   */
  @Test void testRedundantSortOnJoinPlan() throws Exception {
    RuleSet ruleSet =
        RuleSets.ofList(CoreRules.SORT_REMOVE,
            CoreRules.SORT_JOIN_TRANSPOSE,
            CoreRules.SORT_PROJECT_TRANSPOSE,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_LIMIT_RULE,
            EnumerableRules.ENUMERABLE_JOIN_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE,
            EnumerableRules.ENUMERABLE_SORT_RULE);
    Planner planner = getPlanner(null, Programs.of(ruleSet));
    final String sql = "select e.\"deptno\" from \"emps\" e "
        + "left outer join \"depts\" d "
        + " on e.\"deptno\" = d.\"deptno\" "
        + "order by e.\"deptno\" "
        + "limit 10";
    SqlNode parse = planner.parse(sql);
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).rel;
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE).simplify();
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform),
        equalTo("EnumerableProject(deptno=[$1])\n"
        + "  EnumerableLimit(fetch=[10])\n"
        + "    EnumerableHashJoin(condition=[=($1, $5)], joinType=[left])\n"
        + "      EnumerableLimit(fetch=[10])\n"
        + "        EnumerableSort(sort0=[$1], dir0=[ASC])\n"
        + "          EnumerableTableScan(table=[[hr, emps]])\n"
        + "      EnumerableProject(deptno=[$0], name=[$1], employees=[$2], x=[$3.x], y=[$3.y])\n"
        + "        EnumerableTableScan(table=[[hr, depts]])\n"));
  }

  /** Unit test that parses, validates, converts and
   * plans for query using two duplicate order by.
   * The duplicate order by should be removed by SqlToRelConverter. */
  @Test void testDuplicateSortPlan() throws Exception {
    runDuplicateSortCheck(
        "select empid from ( "
        + "select * "
        + "from emps "
        + "order by emps.deptno) "
        + "order by deptno",
        "EnumerableSort(sort0=[$1], dir0=[ASC])\n"
        + "  EnumerableProject(empid=[$0], deptno=[$1])\n"
        + "    EnumerableTableScan(table=[[hr, emps]])\n");
  }

  /** Unit test that parses, validates, converts and
   * plans for query using two duplicate order by.
   * The duplicate order by should be removed by SqlToRelConverter. */
  @Test void testDuplicateSortPlanWithExpr() throws Exception {
    runDuplicateSortCheck("select empid+deptno from ( "
        + "select empid, deptno "
        + "from emps "
        + "order by emps.deptno) "
        + "order by deptno",
        "EnumerableSort(sort0=[$1], dir0=[ASC])\n"
        + "  EnumerableProject(EXPR$0=[+($0, $1)], deptno=[$1])\n"
        + "    EnumerableTableScan(table=[[hr, emps]])\n");
  }

  @Test void testTwoSortRemoveInnerSort() throws Exception {
    runDuplicateSortCheck("select empid+deptno from ( "
        + "select empid, deptno "
        + "from emps "
        + "order by empid) "
        + "order by deptno",
        "EnumerableSort(sort0=[$1], dir0=[ASC])\n"
        + "  EnumerableProject(EXPR$0=[+($0, $1)], deptno=[$1])\n"
        + "    EnumerableTableScan(table=[[hr, emps]])\n");
  }

  /** Tests that outer order by is not removed since window function
   * might reorder the rows in-between. */
  @Test void testDuplicateSortPlanWithOver() throws Exception {
    runDuplicateSortCheck("select emp_cnt, empid+deptno from ( "
        + "select empid, deptno, count(*) over (partition by deptno) emp_cnt from ( "
        + "  select empid, deptno "
        + "    from emps "
        + "   order by emps.deptno) "
        + ")"
        + "order by deptno",
        "EnumerableSort(sort0=[$2], dir0=[ASC])\n"
        + "  EnumerableProject(emp_cnt=[$5], EXPR$1=[+($0, $1)], deptno=[$1])\n"
        + "    EnumerableWindow(window#0=[window(partition {1} aggs [COUNT()])])\n"
        + "      EnumerableTableScan(table=[[hr, emps]])\n");
  }

  @Test void testDuplicateSortPlanWithRemovedOver() throws Exception {
    runDuplicateSortCheck("select empid+deptno from ( "
        + "select empid, deptno, count(*) over (partition by deptno) emp_cnt from ( "
        + "  select empid, deptno "
        + "    from emps "
        + "   order by emps.deptno) "
        + ")"
        + "order by deptno",
        "EnumerableSort(sort0=[$1], dir0=[ASC])\n"
        + "  EnumerableProject(EXPR$0=[+($0, $1)], deptno=[$1])\n"
        + "    EnumerableTableScan(table=[[hr, emps]])\n");
  }

  // If proper "SqlParseException, ValidationException, RelConversionException"
  // is used, then checkstyle fails with
  // "Redundant throws: 'ValidationException' listed more then one time"
  // "Redundant throws: 'RelConversionException' listed more then one time"
  private void runDuplicateSortCheck(String sql, String plan) throws Exception {
    RuleSet ruleSet =
        RuleSets.ofList(CoreRules.SORT_REMOVE,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE,
            EnumerableRules.ENUMERABLE_WINDOW_RULE,
            EnumerableRules.ENUMERABLE_SORT_RULE,
            CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW);
    Planner planner =
        getPlanner(null, SqlParser.config().withLex(Lex.JAVA),
            Programs.of(ruleSet));
    SqlNode parse = planner.parse(sql);
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).rel;
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    if (traitSet.getTrait(RelCollationTraitDef.INSTANCE) == null) {
      // SortRemoveRule can only work if collation trait is enabled.
      return;
    }
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform), equalTo(plan));
  }

  /** Unit test that parses, validates, converts and
   * plans for query using two duplicate order by.*/
  @Test void testDuplicateSortPlanWORemoveSortRule() throws Exception {
    RuleSet ruleSet =
        RuleSets.ofList(EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE,
            EnumerableRules.ENUMERABLE_SORT_RULE);
    Planner planner = getPlanner(null, Programs.of(ruleSet));
    final String sql = "select \"empid\" from ( "
        + "select * "
        + "from \"emps\" "
        + "order by \"emps\".\"deptno\") "
        + "order by \"deptno\"";
    SqlNode parse = planner.parse(sql);
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).rel;
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform),
        equalTo("EnumerableSort(sort0=[$1], dir0=[ASC])\n"
            + "  EnumerableProject(empid=[$0], deptno=[$1])\n"
            + "    EnumerableTableScan(table=[[hr, emps]])\n"));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3029">[CALCITE-3029]
   * Java-oriented field type is wrongly forced to be NOT NULL after being converted to
   * SQL-oriented</a>. */
  @Test void testInsertSourceRelTypeWithNullValues() throws Exception {
    Planner planner = getPlanner(null, Programs.standard());
    final String sql = "insert into \"emps\" values(1, 1, null, 1, 1)";
    SqlNode parse = planner.parse(sql);
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).rel;
    RelDataType insertSourceType = convert.getInput(0).getRowType();
    String typeString = SqlTests.getTypeString(insertSourceType);
    assertThat(typeString,
        is("RecordType(INTEGER NOT NULL empid, INTEGER NOT NULL deptno, "
            + "JavaType(class java.lang.String) name, REAL NOT NULL salary, "
            + "INTEGER NOT NULL commission) NOT NULL"));
  }

  /** Unit test that parses, validates, converts and plans. Planner is
   * provided with a list of RelTraitDefs to register. */
  @Test void testPlanWithExplicitTraitDefs() throws Exception {
    RuleSet ruleSet =
        RuleSets.ofList(CoreRules.FILTER_MERGE,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_FILTER_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE);
    final List<RelTraitDef> traitDefs = new ArrayList<>();
    traitDefs.add(ConventionTraitDef.INSTANCE);
    traitDefs.add(RelCollationTraitDef.INSTANCE);

    Planner planner = getPlanner(traitDefs, Programs.of(ruleSet));

    SqlNode parse = planner.parse("select * from \"emps\"");
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).project();
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform),
        equalTo(
            "EnumerableProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n"
            + "  EnumerableTableScan(table=[[hr, emps]])\n"));
  }

  /** Unit test that calls {@link Planner#transform} twice. */
  @Test void testPlanTransformTwice() throws Exception {
    RuleSet ruleSet =
        RuleSets.ofList(CoreRules.FILTER_MERGE,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_FILTER_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE);
    Planner planner = getPlanner(null, Programs.of(ruleSet));
    SqlNode parse = planner.parse("select * from \"emps\"");
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).project();
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    RelNode transform2 = planner.transform(0, traitSet, transform);
    assertThat(toString(transform2),
        equalTo(
            "EnumerableProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n"
            + "  EnumerableTableScan(table=[[hr, emps]])\n"));
  }

  /** Unit test that calls {@link Planner#transform} twice with
   * rule name conflicts. */
  @Test void testPlanTransformWithRuleNameConflicts() throws Exception {
    // Create two dummy rules with identical rules.
    RelOptRule rule1 = MyProjectFilterRule.config("MYRULE").toRule();
    RelOptRule rule2 = MyFilterProjectRule.config("MYRULE").toRule();

    RuleSet ruleSet1 =
        RuleSets.ofList(rule1,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_FILTER_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE);

    RuleSet ruleSet2 = RuleSets.ofList(rule2);

    Planner planner =
        getPlanner(null, Programs.of(ruleSet1), Programs.of(ruleSet2));
    SqlNode parse = planner.parse("select * from \"emps\"");
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).rel;
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    RelNode transform2 = planner.transform(1, traitSet, transform);
    assertThat(toString(transform2),
        equalTo(
            "EnumerableProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n"
                + "  EnumerableTableScan(table=[[hr, emps]])\n"));
  }

  /** Tests that Hive dialect does not generate "AS". */
  @Test void testHiveDialect() throws SqlParseException {
    Planner planner = getPlanner(null);
    final String sql = "select * from (select * from \"emps\") as t\n"
        + "where \"name\" like '%e%'";
    SqlNode parse = planner.parse(sql);
    final SqlDialect hiveDialect =
        SqlDialect.DatabaseProduct.HIVE.getDialect();
    assertThat(Util.toLinux(parse.toSqlString(hiveDialect).getSql()),
        equalTo("SELECT *\n"
            + "FROM (SELECT *\n"
            + "FROM `emps`) `T`\n"
            + "WHERE `name` LIKE '%e%'"));
  }

  /** Unit test that calls {@link Planner#transform} twice,
   * with different rule sets, with different conventions.
   *
   * <p>{@link org.apache.calcite.adapter.jdbc.JdbcConvention} is different
   * from the typical convention in that it is not a singleton. Switching to
   * a different instance causes problems unless planner state is wiped clean
   * between calls to {@link Planner#transform}. */
  @Test void testPlanTransformWithDiffRuleSetAndConvention()
      throws Exception {
    Program program0 =
        Programs.ofRules(
            CoreRules.FILTER_MERGE,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
            EnumerableRules.ENUMERABLE_FILTER_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE);

    JdbcConvention out = new JdbcConvention(null, null, "myjdbc");
    Program program1 =
        Programs.ofRules(MockJdbcProjectRule.create(out),
            MockJdbcTableRule.create(out));

    Planner planner = getPlanner(null, program0, program1);
    SqlNode parse = planner.parse("select T1.\"name\" from \"emps\" as T1 ");

    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).project();

    RelTraitSet traitSet0 = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);

    RelTraitSet traitSet1 = convert.getTraitSet()
        .replace(out);

    RelNode transform = planner.transform(0, traitSet0, convert);
    RelNode transform2 = planner.transform(1, traitSet1, transform);
    assertThat(toString(transform2),
        equalTo("JdbcProject(name=[$2])\n"
            + "  MockJdbcTableScan(table=[[hr, emps]])\n"));
  }

  @Test void testPlan5WayJoin() throws Exception {
    checkJoinNWay(5); // LoptOptimizeJoinRule disabled; takes about .4s
  }

  @Test void testPlan9WayJoin() throws Exception {
    checkJoinNWay(9); // LoptOptimizeJoinRule enabled; takes about 0.04s
  }

  @Test void testPlan35WayJoin() throws Exception {
    checkJoinNWay(35); // takes about 2s
  }

  @Tag("slow")
  @Test void testPlan60WayJoin() throws Exception {
    checkJoinNWay(60); // takes about 15s
  }

  /** Test that plans a query with a large number of joins. */
  private void checkJoinNWay(int n) throws Exception {
    // Here the times before and after enabling LoptOptimizeJoinRule.
    //
    // Note the jump between N=6 and N=7; LoptOptimizeJoinRule is disabled if
    // there are fewer than 6 joins (7 relations).
    //
    //       N    Before     After
    //         time (ms) time (ms)
    // ======= ========= =========
    //       5                 382
    //       6                 790
    //       7                  26
    //       9    6,000         39
    //      10    9,000         47
    //      11   19,000         67
    //      12   40,000         63
    //      13 OOM              96
    //      35 OOM           1,716
    //      60 OOM          12,230
    final StringBuilder buf = new StringBuilder();
    buf.append("select * from \"depts\" as d0");
    for (int i = 1; i < n; i++) {
      buf.append("\njoin \"depts\" as d").append(i);
      buf.append("\non d").append(i).append(".\"deptno\" = d").append(i - 1).append(".\"deptno\"");
    }
    Planner planner =
        getPlanner(null, Programs.heuristicJoinOrder(Programs.RULE_SET, false, 6));
    SqlNode parse = planner.parse(buf.toString());

    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).project();
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform),
        containsString(
            "EnumerableHashJoin(condition=[=($0, $5)], joinType=[inner])"));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-435">[CALCITE-435]
   * LoptOptimizeJoinRule incorrectly re-orders outer joins</a>.
   *
   * <p>Checks the
   * {@link org.apache.calcite.rel.rules.LoptOptimizeJoinRule} on a query with a
   * left outer join.
   *
   * <p>Specifically, tests that a relation (dependents) in an inner join
   * cannot be pushed into an outer join (emps left join depts).
   */
  @Test void testHeuristicLeftJoin() throws Exception {
    final String sql = "select * from \"emps\" as e\n"
        + "left join \"depts\" as d on e.\"deptno\" = d.\"deptno\"\n"
        + "join \"dependents\" as p on e.\"empid\" = p.\"empid\"";
    final String expected = ""
        + "EnumerableProject(empid=[$2], deptno=[$3], name=[$4], salary=[$5], commission=[$6], deptno0=[$7], name0=[$8], employees=[$9], location=[ROW($10, $11)], empid0=[$0], name1=[$1])\n"
        + "  EnumerableHashJoin(condition=[=($0, $2)], joinType=[inner])\n"
        + "    EnumerableTableScan(table=[[hr, dependents]])\n"
        + "    EnumerableHashJoin(condition=[=($1, $5)], joinType=[left])\n"
        + "      EnumerableTableScan(table=[[hr, emps]])\n"
        + "      EnumerableProject(deptno=[$0], name=[$1], employees=[$2], x=[$3.x], y=[$3.y])\n"
        + "        EnumerableTableScan(table=[[hr, depts]])";
    checkHeuristic(sql, expected);
  }

  /** It would probably be OK to transform
   * {@code (emps right join depts) join dependents}
   * to
   * {@code (emps  join dependents) right join depts}
   * but we do not currently allow it.
   */
  @Test void testHeuristicPushInnerJoin() throws Exception {
    final String sql = "select * from \"emps\" as e\n"
        + "right join \"depts\" as d on e.\"deptno\" = d.\"deptno\"\n"
        + "join \"dependents\" as p on e.\"empid\" = p.\"empid\"";
    final String expected = ""
        + "EnumerableProject(empid=[$2], deptno=[$3], name=[$4], salary=[$5], commission=[$6], deptno0=[$7], name0=[$8], employees=[$9], location=[ROW($10, $11)], empid0=[$0], name1=[$1])\n"
        + "  EnumerableHashJoin(condition=[=($0, $2)], joinType=[inner])\n"
        + "    EnumerableTableScan(table=[[hr, dependents]])\n"
        + "    EnumerableProject(empid=[$5], deptno=[$6], name=[$7], salary=[$8], commission=[$9], deptno0=[$0], name0=[$1], employees=[$2], x=[$3], y=[$4])\n"
        + "      EnumerableHashJoin(condition=[=($0, $6)], joinType=[left])\n"
        + "        EnumerableProject(deptno=[$0], name=[$1], employees=[$2], x=[$3.x], y=[$3.y])\n"
        + "          EnumerableTableScan(table=[[hr, depts]])\n"
        + "        EnumerableTableScan(table=[[hr, emps]])";
    checkHeuristic(sql, expected);
  }

  /** Tests that a relation (dependents) that is on the null-generating side of
   * an outer join cannot be pushed into an inner join (emps join depts). */
  @Test void testHeuristicRightJoin() throws Exception {
    final String sql = "select * from \"emps\" as e\n"
        + "join \"depts\" as d on e.\"deptno\" = d.\"deptno\"\n"
        + "right join \"dependents\" as p on e.\"empid\" = p.\"empid\"";
    final String expected = ""
        + "EnumerableProject(empid=[$2], deptno=[$3], name=[$4], salary=[$5], commission=[$6], deptno0=[$7], name0=[$8], employees=[$9], location=[ROW($10, $11)], empid0=[$0], name1=[$1])\n"
        + "  EnumerableHashJoin(condition=[=($0, $2)], joinType=[left])\n"
        + "    EnumerableTableScan(table=[[hr, dependents]])\n"
        + "    EnumerableHashJoin(condition=[=($1, $5)], joinType=[inner])\n"
        + "      EnumerableTableScan(table=[[hr, emps]])\n"
        + "      EnumerableProject(deptno=[$0], name=[$1], employees=[$2], x=[$3.x], y=[$3.y])\n"
        + "        EnumerableTableScan(table=[[hr, depts]])";
    checkHeuristic(sql, expected);
  }

  private void checkHeuristic(String sql, String expected) throws Exception {
    Planner planner =
        getPlanner(null, Programs.heuristicJoinOrder(Programs.RULE_SET, false, 0));
    SqlNode parse = planner.parse(sql);
    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).rel;
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform), containsString(expected));
  }

  /** Plans a 3-table join query on the FoodMart schema. The ideal plan is not
   * bushy, but nevertheless exercises the bushy-join heuristic optimizer. */
  @Test void testAlmostBushy() throws Exception {
    final String sql = "select *\n"
        + "from \"sales_fact_1997\" as s\n"
        + "join \"customer\" as c\n"
        + "  on s.\"customer_id\" = c.\"customer_id\"\n"
        + "join \"product\" as p\n"
        + "  on s.\"product_id\" = p.\"product_id\"\n"
        + "where c.\"city\" = 'San Francisco'\n"
        + "and p.\"brand_name\" = 'Washington'";
    final String expected = ""
        + "EnumerableProject(product_id=[$0], time_id=[$1], customer_id=[$2], promotion_id=[$3], store_id=[$4], store_sales=[$5], store_cost=[$6], unit_sales=[$7], customer_id0=[$8], account_num=[$9], lname=[$10], fname=[$11], mi=[$12], address1=[$13], address2=[$14], address3=[$15], address4=[$16], city=[$17], state_province=[$18], postal_code=[$19], country=[$20], customer_region_id=[$21], phone1=[$22], phone2=[$23], birthdate=[$24], marital_status=[$25], yearly_income=[$26], gender=[$27], total_children=[$28], num_children_at_home=[$29], education=[$30], date_accnt_opened=[$31], member_card=[$32], occupation=[$33], houseowner=[$34], num_cars_owned=[$35], fullname=[$36], product_class_id=[$37], product_id0=[$38], brand_name=[$39], product_name=[$40], SKU=[$41], SRP=[$42], gross_weight=[$43], net_weight=[$44], recyclable_package=[$45], low_fat=[$46], units_per_case=[$47], cases_per_pallet=[$48], shelf_width=[$49], shelf_height=[$50], shelf_depth=[$51])\n"
        + "  EnumerableProject(product_id0=[$44], time_id=[$45], customer_id0=[$46], promotion_id=[$47], store_id=[$48], store_sales=[$49], store_cost=[$50], unit_sales=[$51], customer_id=[$15], account_num=[$16], lname=[$17], fname=[$18], mi=[$19], address1=[$20], address2=[$21], address3=[$22], address4=[$23], city=[$24], state_province=[$25], postal_code=[$26], country=[$27], customer_region_id=[$28], phone1=[$29], phone2=[$30], birthdate=[$31], marital_status=[$32], yearly_income=[$33], gender=[$34], total_children=[$35], num_children_at_home=[$36], education=[$37], date_accnt_opened=[$38], member_card=[$39], occupation=[$40], houseowner=[$41], num_cars_owned=[$42], fullname=[$43], product_class_id=[$0], product_id=[$1], brand_name=[$2], product_name=[$3], SKU=[$4], SRP=[$5], gross_weight=[$6], net_weight=[$7], recyclable_package=[$8], low_fat=[$9], units_per_case=[$10], cases_per_pallet=[$11], shelf_width=[$12], shelf_height=[$13], shelf_depth=[$14])\n"
        + "    EnumerableHashJoin(condition=[=($1, $44)], joinType=[inner])\n"
        + "      EnumerableFilter(condition=[=($2, 'Washington')])\n"
        + "        EnumerableTableScan(table=[[foodmart2, product]])\n"
        + "      EnumerableHashJoin(condition=[=($0, $31)], joinType=[inner])\n"
        + "        EnumerableFilter(condition=[=($9, 'San Francisco')])\n"
        + "          EnumerableTableScan(table=[[foodmart2, customer]])\n"
        + "        EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n";
    checkBushy(sql, expected);
  }

  /** Plans a 4-table join query on the FoodMart schema.
   *
   * <p>The ideal plan is bushy:
   *   customer x (product_class x  product x sales)
   * which would be written
   *   (customer x ((product_class x product) x sales))
   * if you don't assume 'x' is left-associative. */
  @Test void testBushy() throws Exception {
    final String sql = "select *\n"
        + "from \"sales_fact_1997\" as s\n"
        + "join \"customer\" as c\n"
        + "  on s.\"customer_id\" = c.\"customer_id\"\n"
        + "join \"product\" as p\n"
        + "  on s.\"product_id\" = p.\"product_id\"\n"
        + "join \"product_class\" as pc\n"
        + "  on p.\"product_class_id\" = pc.\"product_class_id\"\n"
        + "where c.\"city\" = 'San Francisco'\n"
        + "and p.\"brand_name\" = 'Washington'";
    final String expected = ""
        + "EnumerableProject(product_id=[$0], time_id=[$1], customer_id=[$2], promotion_id=[$3], store_id=[$4], store_sales=[$5], store_cost=[$6], unit_sales=[$7], customer_id0=[$8], account_num=[$9], lname=[$10], fname=[$11], mi=[$12], address1=[$13], address2=[$14], address3=[$15], address4=[$16], city=[$17], state_province=[$18], postal_code=[$19], country=[$20], customer_region_id=[$21], phone1=[$22], phone2=[$23], birthdate=[$24], marital_status=[$25], yearly_income=[$26], gender=[$27], total_children=[$28], num_children_at_home=[$29], education=[$30], date_accnt_opened=[$31], member_card=[$32], occupation=[$33], houseowner=[$34], num_cars_owned=[$35], fullname=[$36], product_class_id=[$37], product_id0=[$38], brand_name=[$39], product_name=[$40], SKU=[$41], SRP=[$42], gross_weight=[$43], net_weight=[$44], recyclable_package=[$45], low_fat=[$46], units_per_case=[$47], cases_per_pallet=[$48], shelf_width=[$49], shelf_height=[$50], shelf_depth=[$51], product_class_id0=[$52], product_subcategory=[$53], product_category=[$54], product_department=[$55], product_family=[$56])\n"
        + "  EnumerableProject(product_id0=[$49], time_id=[$50], customer_id0=[$51], promotion_id=[$52], store_id=[$53], store_sales=[$54], store_cost=[$55], unit_sales=[$56], customer_id=[$0], account_num=[$1], lname=[$2], fname=[$3], mi=[$4], address1=[$5], address2=[$6], address3=[$7], address4=[$8], city=[$9], state_province=[$10], postal_code=[$11], country=[$12], customer_region_id=[$13], phone1=[$14], phone2=[$15], birthdate=[$16], marital_status=[$17], yearly_income=[$18], gender=[$19], total_children=[$20], num_children_at_home=[$21], education=[$22], date_accnt_opened=[$23], member_card=[$24], occupation=[$25], houseowner=[$26], num_cars_owned=[$27], fullname=[$28], product_class_id0=[$34], product_id=[$35], brand_name=[$36], product_name=[$37], SKU=[$38], SRP=[$39], gross_weight=[$40], net_weight=[$41], recyclable_package=[$42], low_fat=[$43], units_per_case=[$44], cases_per_pallet=[$45], shelf_width=[$46], shelf_height=[$47], shelf_depth=[$48], product_class_id=[$29], product_subcategory=[$30], product_category=[$31], product_department=[$32], product_family=[$33])\n"
        + "    EnumerableHashJoin(condition=[=($0, $51)], joinType=[inner])\n"
        + "      EnumerableFilter(condition=[=($9, 'San Francisco')])\n"
        + "        EnumerableTableScan(table=[[foodmart2, customer]])\n"
        + "      EnumerableHashJoin(condition=[=($6, $20)], joinType=[inner])\n"
        + "        EnumerableHashJoin(condition=[=($0, $5)], joinType=[inner])\n"
        + "          EnumerableTableScan(table=[[foodmart2, product_class]])\n"
        + "          EnumerableFilter(condition=[=($2, 'Washington')])\n"
        + "            EnumerableTableScan(table=[[foodmart2, product]])\n"
        + "        EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n";
    checkBushy(sql, expected);
  }

  /** Plans a 5-table join query on the FoodMart schema. The ideal plan is
   * bushy: store x (customer x (product_class x product x sales)). */
  @Test void testBushy5() throws Exception {
    final String sql = "select *\n"
        + "from \"sales_fact_1997\" as s\n"
        + "join \"customer\" as c\n"
        + "  on s.\"customer_id\" = c.\"customer_id\"\n"
        + "join \"product\" as p\n"
        + "  on s.\"product_id\" = p.\"product_id\"\n"
        + "join \"product_class\" as pc\n"
        + "  on p.\"product_class_id\" = pc.\"product_class_id\"\n"
        + "join \"store\" as st\n"
        + "  on s.\"store_id\" = st.\"store_id\"\n"
        + "where c.\"city\" = 'San Francisco'\n";
    final String expected = ""
        + "EnumerableProject(product_id=[$0], time_id=[$1], customer_id=[$2], promotion_id=[$3], store_id=[$4], store_sales=[$5], store_cost=[$6], unit_sales=[$7], customer_id0=[$8], account_num=[$9], lname=[$10], fname=[$11], mi=[$12], address1=[$13], address2=[$14], address3=[$15], address4=[$16], city=[$17], state_province=[$18], postal_code=[$19], country=[$20], customer_region_id=[$21], phone1=[$22], phone2=[$23], birthdate=[$24], marital_status=[$25], yearly_income=[$26], gender=[$27], total_children=[$28], num_children_at_home=[$29], education=[$30], date_accnt_opened=[$31], member_card=[$32], occupation=[$33], houseowner=[$34], num_cars_owned=[$35], fullname=[$36], product_class_id=[$37], product_id0=[$38], brand_name=[$39], product_name=[$40], SKU=[$41], SRP=[$42], gross_weight=[$43], net_weight=[$44], recyclable_package=[$45], low_fat=[$46], units_per_case=[$47], cases_per_pallet=[$48], shelf_width=[$49], shelf_height=[$50], shelf_depth=[$51], product_class_id0=[$52], product_subcategory=[$53], product_category=[$54], product_department=[$55], product_family=[$56], store_id0=[$57], store_type=[$58], region_id=[$59], store_name=[$60], store_number=[$61], store_street_address=[$62], store_city=[$63], store_state=[$64], store_postal_code=[$65], store_country=[$66], store_manager=[$67], store_phone=[$68], store_fax=[$69], first_opened_date=[$70], last_remodel_date=[$71], store_sqft=[$72], grocery_sqft=[$73], frozen_sqft=[$74], meat_sqft=[$75], coffee_bar=[$76], video_store=[$77], salad_bar=[$78], prepared_food=[$79], florist=[$80])\n"
        + "  EnumerableProject(product_id0=[$73], time_id=[$74], customer_id0=[$75], promotion_id=[$76], store_id0=[$77], store_sales=[$78], store_cost=[$79], unit_sales=[$80], customer_id=[$24], account_num=[$25], lname=[$26], fname=[$27], mi=[$28], address1=[$29], address2=[$30], address3=[$31], address4=[$32], city=[$33], state_province=[$34], postal_code=[$35], country=[$36], customer_region_id=[$37], phone1=[$38], phone2=[$39], birthdate=[$40], marital_status=[$41], yearly_income=[$42], gender=[$43], total_children=[$44], num_children_at_home=[$45], education=[$46], date_accnt_opened=[$47], member_card=[$48], occupation=[$49], houseowner=[$50], num_cars_owned=[$51], fullname=[$52], product_class_id0=[$58], product_id=[$59], brand_name=[$60], product_name=[$61], SKU=[$62], SRP=[$63], gross_weight=[$64], net_weight=[$65], recyclable_package=[$66], low_fat=[$67], units_per_case=[$68], cases_per_pallet=[$69], shelf_width=[$70], shelf_height=[$71], shelf_depth=[$72], product_class_id=[$53], product_subcategory=[$54], product_category=[$55], product_department=[$56], product_family=[$57], store_id=[$0], store_type=[$1], region_id=[$2], store_name=[$3], store_number=[$4], store_street_address=[$5], store_city=[$6], store_state=[$7], store_postal_code=[$8], store_country=[$9], store_manager=[$10], store_phone=[$11], store_fax=[$12], first_opened_date=[$13], last_remodel_date=[$14], store_sqft=[$15], grocery_sqft=[$16], frozen_sqft=[$17], meat_sqft=[$18], coffee_bar=[$19], video_store=[$20], salad_bar=[$21], prepared_food=[$22], florist=[$23])\n"
        + "    EnumerableHashJoin(condition=[=($0, $77)], joinType=[inner])\n"
        + "      EnumerableTableScan(table=[[foodmart2, store]])\n"
        + "      EnumerableHashJoin(condition=[=($0, $51)], joinType=[inner])\n"
        + "        EnumerableFilter(condition=[=($9, 'San Francisco')])\n"
        + "          EnumerableTableScan(table=[[foodmart2, customer]])\n"
        + "        EnumerableHashJoin(condition=[=($6, $20)], joinType=[inner])\n"
        + "          EnumerableHashJoin(condition=[=($0, $5)], joinType=[inner])\n"
        + "            EnumerableTableScan(table=[[foodmart2, product_class]])\n"
        + "            EnumerableTableScan(table=[[foodmart2, product]])\n"
        + "          EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n";
    checkBushy(sql, expected);
  }

  /** Tests the bushy join algorithm where one table does not join to
   * anything. */
  @Test void testBushyCrossJoin() throws Exception {
    final String sql = "select * from \"sales_fact_1997\" as s\n"
        + "join \"customer\" as c\n"
        + "  on s.\"customer_id\" = c.\"customer_id\"\n"
        + "cross join \"department\"";
    final String expected = ""
        + "EnumerableProject(product_id=[$0], time_id=[$1], customer_id=[$2], promotion_id=[$3], store_id=[$4], store_sales=[$5], store_cost=[$6], unit_sales=[$7], customer_id0=[$8], account_num=[$9], lname=[$10], fname=[$11], mi=[$12], address1=[$13], address2=[$14], address3=[$15], address4=[$16], city=[$17], state_province=[$18], postal_code=[$19], country=[$20], customer_region_id=[$21], phone1=[$22], phone2=[$23], birthdate=[$24], marital_status=[$25], yearly_income=[$26], gender=[$27], total_children=[$28], num_children_at_home=[$29], education=[$30], date_accnt_opened=[$31], member_card=[$32], occupation=[$33], houseowner=[$34], num_cars_owned=[$35], fullname=[$36], department_id=[$37], department_description=[$38])\n"
        + "  EnumerableProject(product_id=[$31], time_id=[$32], customer_id0=[$33], promotion_id=[$34], store_id=[$35], store_sales=[$36], store_cost=[$37], unit_sales=[$38], customer_id=[$2], account_num=[$3], lname=[$4], fname=[$5], mi=[$6], address1=[$7], address2=[$8], address3=[$9], address4=[$10], city=[$11], state_province=[$12], postal_code=[$13], country=[$14], customer_region_id=[$15], phone1=[$16], phone2=[$17], birthdate=[$18], marital_status=[$19], yearly_income=[$20], gender=[$21], total_children=[$22], num_children_at_home=[$23], education=[$24], date_accnt_opened=[$25], member_card=[$26], occupation=[$27], houseowner=[$28], num_cars_owned=[$29], fullname=[$30], department_id=[$0], department_description=[$1])\n"
        + "    EnumerableNestedLoopJoin(condition=[true], joinType=[inner])\n"
        + "      EnumerableTableScan(table=[[foodmart2, department]])\n"
        + "      EnumerableHashJoin(condition=[=($0, $31)], joinType=[inner])\n"
        + "        EnumerableTableScan(table=[[foodmart2, customer]])\n"
        + "        EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])";
    checkBushy(sql, expected);
  }

  /** Tests the bushy join algorithm against a query where not all tables have a
   * join condition to the others. */
  @Test void testBushyCrossJoin2() throws Exception {
    final String sql = "select * from \"sales_fact_1997\" as s\n"
        + "join \"customer\" as c\n"
        + "  on s.\"customer_id\" = c.\"customer_id\"\n"
        + "cross join \"department\" as d\n"
        + "join \"employee\" as e\n"
        + "  on d.\"department_id\" = e.\"department_id\"";
    final String expected = ""
        + "EnumerableProject(product_id=[$0], time_id=[$1], customer_id=[$2], promotion_id=[$3], store_id=[$4], store_sales=[$5], store_cost=[$6], unit_sales=[$7], customer_id0=[$8], account_num=[$9], lname=[$10], fname=[$11], mi=[$12], address1=[$13], address2=[$14], address3=[$15], address4=[$16], city=[$17], state_province=[$18], postal_code=[$19], country=[$20], customer_region_id=[$21], phone1=[$22], phone2=[$23], birthdate=[$24], marital_status=[$25], yearly_income=[$26], gender=[$27], total_children=[$28], num_children_at_home=[$29], education=[$30], date_accnt_opened=[$31], member_card=[$32], occupation=[$33], houseowner=[$34], num_cars_owned=[$35], fullname=[$36], department_id=[$37], department_description=[$38], employee_id=[$39], full_name=[$40], first_name=[$41], last_name=[$42], position_id=[$43], position_title=[$44], store_id0=[$45], department_id0=[$46], birth_date=[$47], hire_date=[$48], end_date=[$49], salary=[$50], supervisor_id=[$51], education_level=[$52], marital_status0=[$53], gender0=[$54], management_role=[$55])\n"
        + "  EnumerableProject(product_id=[$48], time_id=[$49], customer_id0=[$50], promotion_id=[$51], store_id0=[$52], store_sales=[$53], store_cost=[$54], unit_sales=[$55], customer_id=[$19], account_num=[$20], lname=[$21], fname=[$22], mi=[$23], address1=[$24], address2=[$25], address3=[$26], address4=[$27], city=[$28], state_province=[$29], postal_code=[$30], country=[$31], customer_region_id=[$32], phone1=[$33], phone2=[$34], birthdate=[$35], marital_status0=[$36], yearly_income=[$37], gender0=[$38], total_children=[$39], num_children_at_home=[$40], education=[$41], date_accnt_opened=[$42], member_card=[$43], occupation=[$44], houseowner=[$45], num_cars_owned=[$46], fullname=[$47], department_id=[$0], department_description=[$1], employee_id=[$2], full_name=[$3], first_name=[$4], last_name=[$5], position_id=[$6], position_title=[$7], store_id=[$8], department_id0=[$9], birth_date=[$10], hire_date=[$11], end_date=[$12], salary=[$13], supervisor_id=[$14], education_level=[$15], marital_status=[$16], gender=[$17], management_role=[$18])\n"
        + "    EnumerableNestedLoopJoin(condition=[true], joinType=[inner])\n"
        + "      EnumerableHashJoin(condition=[=($0, $9)], joinType=[inner])\n"
        + "        EnumerableTableScan(table=[[foodmart2, department]])\n"
        + "        EnumerableTableScan(table=[[foodmart2, employee]])\n"
        + "      EnumerableHashJoin(condition=[=($0, $31)], joinType=[inner])\n"
        + "        EnumerableTableScan(table=[[foodmart2, customer]])\n"
        + "        EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n";
    checkBushy(sql, expected);
  }

  /** Checks that a query returns a particular plan, using a planner with
   * MultiJoinOptimizeBushyRule enabled. */
  private void checkBushy(String sql, String expected) throws Exception {
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    final FrameworkConfig config = Frameworks.newConfigBuilder()
        .parserConfig(SqlParser.Config.DEFAULT)
        .defaultSchema(
            CalciteAssert.addSchema(rootSchema,
                CalciteAssert.SchemaSpec.CLONE_FOODMART))
        .traitDefs((List<RelTraitDef>) null)
        .programs(Programs.heuristicJoinOrder(Programs.RULE_SET, true, 2))
        .build();
    Planner planner = Frameworks.getPlanner(config);
    SqlNode parse = planner.parse(sql);

    SqlNode validate = planner.validate(parse);
    RelNode convert = planner.rel(validate).project();
    RelTraitSet traitSet = convert.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    RelNode transform = planner.transform(0, traitSet, convert);
    assertThat(toString(transform), containsString(expected));
  }

  /** Rule that matches a Project on a Filter. */
  public static class MyProjectFilterRule
      extends RelRule<MyProjectFilterRule.Config> {
    static Config config(String description) {
      return ImmutableMyProjectFilterRuleConfig.builder()
          .build()
          .withOperandSupplier(b0 ->
              b0.operand(LogicalProject.class).oneInput(b1 ->
                  b1.operand(LogicalFilter.class).anyInputs()))
          .withDescription(description)
          .as(Config.class);
    }

    protected MyProjectFilterRule(Config config) {
      super(config);
    }


    @Override public boolean matches(RelOptRuleCall call) {
      return false;
    }

    @Override public void onMatch(RelOptRuleCall call) {
    }

    /** Rule configuration. */
    @Value.Immutable
    @Value.Style(init = "with*", typeImmutable = "ImmutableMyProjectFilterRuleConfig")
    public interface Config extends RelRule.Config {
      @Override default MyProjectFilterRule toRule() {
        return new MyProjectFilterRule(this);
      }
    }
  }

  /** Rule that matches a Filter on a Project. */
  public static class MyFilterProjectRule
      extends RelRule<MyFilterProjectRule.Config> {
    static Config config(String description) {
      return ImmutableMyFilterProjectRuleConfig.builder()
          .withOperandSupplier(b0 ->
              b0.operand(LogicalFilter.class).oneInput(b1 ->
                  b1.operand(LogicalProject.class).anyInputs()))
          .withDescription(description)
          .build();
    }

    protected MyFilterProjectRule(Config config) {
      super(config);
    }

    @Override public boolean matches(RelOptRuleCall call) {
      return false;
    }

    @Override public void onMatch(RelOptRuleCall call) {
    }

    /** Rule configuration. */
    @Value.Immutable
    @Value.Style(init = "with*", typeImmutable = "ImmutableMyFilterProjectRuleConfig")
    public interface Config extends RelRule.Config {
      @Override default MyFilterProjectRule toRule() {
        return new MyFilterProjectRule(this);
      }
    }
  }

  /**
   * Rule to convert a
   * {@link org.apache.calcite.adapter.enumerable.EnumerableProject} to an
   * {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcProject}.
   */
  private static class MockJdbcProjectRule extends ConverterRule {
    static MockJdbcProjectRule create(JdbcConvention out) {
      return Config.INSTANCE
          .withConversion(EnumerableProject.class,
              EnumerableConvention.INSTANCE, out, "MockJdbcProjectRule")
          .withRuleFactory(MockJdbcProjectRule::new)
          .toRule(MockJdbcProjectRule.class);
    }

    MockJdbcProjectRule(Config config) {
      super(config);
    }

    @Override public RelNode convert(RelNode rel) {
      final EnumerableProject project = (EnumerableProject) rel;

      return new JdbcRules.JdbcProject(
          rel.getCluster(),
          rel.getTraitSet().replace(getOutConvention()),
          convert(project.getInput(),
              project.getInput().getTraitSet().replace(getOutConvention())),
          project.getProjects(),
          project.getRowType());
    }
  }

  /**
   * Rule to convert a
   * {@link org.apache.calcite.adapter.enumerable.EnumerableTableScan} to an
   * {@link MockJdbcTableScan}.
   */
  private static class MockJdbcTableRule extends ConverterRule {
    static MockJdbcTableRule create(JdbcConvention out) {
      return Config.INSTANCE
          .withConversion(EnumerableTableScan.class,
              EnumerableConvention.INSTANCE, out, "MockJdbcTableRule")
          .withRuleFactory(MockJdbcTableRule::new)
          .toRule(MockJdbcTableRule.class);
    }

    private MockJdbcTableRule(Config config) {
      super(config);
    }

    @Override public RelNode convert(RelNode rel) {
      final EnumerableTableScan scan =
          (EnumerableTableScan) rel;
      return new MockJdbcTableScan(scan.getCluster(),
          scan.getTable(),
          (JdbcConvention) getOutConvention());
    }
  }

  /**
   * Relational expression representing a "mock" scan of a table in a
   * JDBC data source.
   */
  private static class MockJdbcTableScan extends TableScan
      implements JdbcRel {

    MockJdbcTableScan(RelOptCluster cluster, RelOptTable table,
        JdbcConvention jdbcConvention) {
      super(cluster, cluster.traitSetOf(jdbcConvention), ImmutableList.of(), table);
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
      return new MockJdbcTableScan(getCluster(), table,
          (JdbcConvention) getConvention());
    }

    @Override public void register(RelOptPlanner planner) {
      final JdbcConvention out = (JdbcConvention) getConvention();
      for (RelOptRule rule : JdbcRules.rules(out)) {
        planner.addRule(rule);
      }
    }

    public JdbcImplementor.Result implement(JdbcImplementor implementor) {
      return null;
    }
  }

  /**
   * Test to determine whether de-correlation correctly removes Correlator.
   */
  @Test void testOldJoinStyleDeCorrelation() throws Exception {
    assertFalse(
        checkTpchQuery("select\n p.`pPartkey`\n"
            + "from\n"
            + "  `tpch`.`part` p,\n"
            + "  `tpch`.`partsupp` ps1\n"
            + "where\n"
            + "  p.`pPartkey` = ps1.`psPartkey`\n"
            + "  and ps1.`psSupplyCost` = (\n"
            + "    select\n"
            + "      min(ps.`psSupplyCost`)\n"
            + "    from\n"
            + "      `tpch`.`partsupp` ps\n"
            + "    where\n"
            + "      p.`pPartkey` = ps.`psPartkey`\n"
            + "  )\n")
            .contains("Correlat"));
  }

  public String checkTpchQuery(String tpchTestQuery) throws Exception {
    final SchemaPlus schema =
        Frameworks.createRootSchema(true).add("tpch",
            new ReflectiveSchema(new TpchSchema()));

    final FrameworkConfig config = Frameworks.newConfigBuilder()
        .parserConfig(SqlParser.config().withLex(Lex.MYSQL))
        .defaultSchema(schema)
        .programs(Programs.ofRules(Programs.RULE_SET))
        .build();
    String plan;
    try (Planner p = Frameworks.getPlanner(config)) {
      SqlNode n = p.parse(tpchTestQuery);
      n = p.validate(n);
      RelNode r = p.rel(n).project();
      plan = RelOptUtil.toString(r);
    }
    return plan;
  }

  /** User-defined aggregate function. */
  public static class MyCountAggFunction extends SqlAggFunction {
    MyCountAggFunction() {
      super("MY_COUNT", null, SqlKind.OTHER_FUNCTION, ReturnTypes.BIGINT, null,
          OperandTypes.ANY, SqlFunctionCategory.NUMERIC, false, false,
          Optionality.FORBIDDEN);
    }

    @SuppressWarnings("deprecation")
    public List<RelDataType> getParameterTypes(RelDataTypeFactory typeFactory) {
      return ImmutableList.of(typeFactory.createSqlType(SqlTypeName.ANY));
    }

    @SuppressWarnings("deprecation")
    public RelDataType getReturnType(RelDataTypeFactory typeFactory) {
      return typeFactory.createSqlType(SqlTypeName.BIGINT);
    }

    public RelDataType deriveType(SqlValidator validator,
        SqlValidatorScope scope, SqlCall call) {
      // Check for COUNT(*) function.  If it is we don't
      // want to try and derive the "*"
      if (call.isCountStar()) {
        return validator.getTypeFactory().createSqlType(SqlTypeName.BIGINT);
      }
      return super.deriveType(validator, scope, call);
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-569">[CALCITE-569]
   * ArrayIndexOutOfBoundsException when deducing collation</a>. */
  @Test void testOrderByNonSelectColumn() throws Exception {
    final SchemaPlus schema = Frameworks.createRootSchema(true)
        .add("tpch", new ReflectiveSchema(new TpchSchema()));

    String query = "select t.psPartkey from\n"
        + "(select ps.psPartkey from `tpch`.`partsupp` ps\n"
        + "order by ps.psPartkey, ps.psSupplyCost) t\n"
        + "order by t.psPartkey";

    List<RelTraitDef> traitDefs = new ArrayList<>();
    traitDefs.add(ConventionTraitDef.INSTANCE);
    traitDefs.add(RelCollationTraitDef.INSTANCE);
    final SqlParser.Config parserConfig = SqlParser.config().withLex(Lex.MYSQL);
    FrameworkConfig config = Frameworks.newConfigBuilder()
        .parserConfig(parserConfig)
        .defaultSchema(schema)
        .traitDefs(traitDefs)
        .programs(Programs.ofRules(Programs.RULE_SET))
        .build();
    String plan;
    try (Planner p = Frameworks.getPlanner(config)) {
      SqlNode n = p.parse(query);
      n = p.validate(n);
      RelNode r = p.rel(n).project();
      plan = RelOptUtil.toString(r);
      plan = Util.toLinux(plan);
    }
    assertThat(plan,
        equalTo("LogicalSort(sort0=[$0], dir0=[ASC])\n"
        + "  LogicalProject(psPartkey=[$0])\n"
        + "    LogicalTableScan(table=[[tpch, partsupp]])\n"));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-648">[CALCITE-648]
   * Update ProjectMergeRule description for new naming convention</a>. */
  @Test void testMergeProjectForceMode() {
    RuleSet ruleSet =
        RuleSets.ofList(
            CoreRules.PROJECT_MERGE.config
                .withRelBuilderFactory(
                    RelBuilder.proto(RelFactories.DEFAULT_PROJECT_FACTORY))
                .as(ProjectMergeRule.Config.class)
                .toRule());
    Planner planner = getPlanner(null, Programs.of(ruleSet));
    planner.close();
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3376">[CALCITE-3376]
   * VolcanoPlanner CannotPlanException: best rel is null even though there is
   * an option with non-infinite cost</a>. */
  @Test void testCorrelatedJoinWithIdenticalInputs() {
    final RelBuilder builder = RelBuilder.create(RelBuilderTest.config().build());
    final RuleSet ruleSet =
        RuleSets.ofList(CoreRules.JOIN_TO_CORRELATE,
            EnumerableRules.ENUMERABLE_CORRELATE_RULE,
            EnumerableRules.ENUMERABLE_PROJECT_RULE,
            EnumerableRules.ENUMERABLE_FILTER_RULE,
            EnumerableRules.ENUMERABLE_SORT_RULE,
            EnumerableRules.ENUMERABLE_UNION_RULE,
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);

    builder
        .scan("EMP")
        .scan("EMP")
        .union(true)

        .scan("EMP")
        .scan("EMP")
        .union(true)

        .join(
            JoinRelType.INNER,
            builder.equals(
                builder.field(2, 0, "DEPTNO"),
                builder.field(2, 1, "EMPNO")));

    final RelNode relNode = builder.build();
    final RelOptPlanner planner = relNode.getCluster().getPlanner();
    final Program program = Programs.of(ruleSet);
    final RelTraitSet toTraits = relNode.getTraitSet()
        .replace(EnumerableConvention.INSTANCE);
    final RelNode output =
        program.run(planner, relNode, toTraits, ImmutableList.of(),
            ImmutableList.of());
    final String plan = toString(output);
    assertThat(plan,
        equalTo("EnumerableCorrelate(correlation=[$cor0], joinType=[inner], "
            + "requiredColumns=[{7}])\n"
            + "  EnumerableUnion(all=[true])\n"
            + "    EnumerableTableScan(table=[[scott, EMP]])\n"
            + "    EnumerableTableScan(table=[[scott, EMP]])\n"
            + "  EnumerableFilter(condition=[=($cor0.DEPTNO, $0)])\n"
            + "    EnumerableUnion(all=[true])\n"
            + "      EnumerableTableScan(table=[[scott, EMP]])\n"
            + "      EnumerableTableScan(table=[[scott, EMP]])\n"));
  }

  @Test void testView() throws Exception {
    final String sql = "select * FROM dept";
    final String expected = "LogicalProject(DEPTNO=[$0], DNAME=[$1])\n"
        + "  LogicalValues("
        + "tuples=[[{ 10, 'Sales      ' },"
        + " { 20, 'Marketing  ' },"
        + " { 30, 'Engineering' },"
        + " { 40, 'Empty      ' }]])\n";
    checkView(sql, is(expected));
  }

  @Test void testViewOnView() throws Exception {
    final String sql = "select * FROM dept30";
    final String expected = "LogicalProject(DEPTNO=[$0], DNAME=[$1])\n"
        + "  LogicalFilter(condition=[=($0, 30)])\n"
        + "    LogicalProject(DEPTNO=[$0], DNAME=[$1])\n"
        + "      LogicalValues("
        + "tuples=[[{ 10, 'Sales      ' },"
        + " { 20, 'Marketing  ' },"
        + " { 30, 'Engineering' },"
        + " { 40, 'Empty      ' }]])\n";
    checkView(sql, is(expected));
  }

  private void checkView(String sql, Matcher<String> matcher)
      throws SqlParseException, ValidationException, RelConversionException {
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    final FrameworkConfig config = Frameworks.newConfigBuilder()
        .defaultSchema(
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.POST))
        .build();
    final Planner planner = Frameworks.getPlanner(config);
    SqlNode parse = planner.parse(sql);
    final SqlNode validate = planner.validate(parse);
    final RelRoot root = planner.rel(validate);
    assertThat(toString(root.rel), matcher);
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-4642">[CALCITE-4642]
   * Checks that custom type systems can be registered in a planner by
   * comparing options for converting unions of chars.</a>.
   */
  @Test void testCustomTypeSystem() throws Exception {
    final String sql = "select Case when DEPTNO <> 30 then 'hi' else 'world' end from dept";
    final String expectedVarying = "LogicalProject("
        + "EXPR$0=["
        + "CASE(<>($0, 30),"
        + " 'hi':VARCHAR(5), "
        + "'world':VARCHAR(5))])\n"
        + "  LogicalValues("
        + "tuples=[[{ 10, 'Sales' },"
        + " { 20, 'Marketing' },"
        + " { 30, 'Engineering' },"
        + " { 40, 'Empty' }]])\n";
    final String expectedDefault = ""
        + "LogicalProject(EXPR$0=[CASE(<>($0, 30), 'hi   ', 'world')])\n"
        + "  LogicalValues(tuples=[[{ 10, 'Sales      ' }, { 20, 'Marketing  ' }, { 30, 'Engineering' }, { 40, 'Empty      ' }]])\n";
    assertValidPlan(sql, new VaryingTypeSystem(DelegatingTypeSystem.DEFAULT), is(expectedVarying));
    assertValidPlan(sql, DelegatingTypeSystem.DEFAULT, is(expectedDefault));
  }

  /** Asserts that a Planner generates the correct plan using the
   * provided type system. */
  private void assertValidPlan(String sql, RelDataTypeSystem typeSystem,
      Matcher<String> planMatcher)  throws SqlParseException,
      ValidationException, RelConversionException {
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    final FrameworkConfig config = Frameworks.newConfigBuilder()
        .defaultSchema(
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.POST))
        .typeSystem(typeSystem).build();
    final Planner planner = Frameworks.getPlanner(config);
    SqlNode parse = planner.parse(sql);
    final SqlNode validate = planner.validate(parse);
    final RelRoot root = planner.rel(validate);
    assertThat(toString(root.rel), planMatcher);
  }

  /**
   * Custom type system that converts union of chars to varchars.
   */
  private static class VaryingTypeSystem extends DelegatingTypeSystem {

    VaryingTypeSystem(RelDataTypeSystem typeSystem) {
      super(typeSystem);
    }

    @Override public boolean shouldConvertRaggedUnionTypesToVarying() {
      return true;
    }
  }
}
