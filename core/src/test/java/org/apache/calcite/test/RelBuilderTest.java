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
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.config.Lex;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelDistributions;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.Correlate;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.Exchange;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.core.TableFunctionScan;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.core.Window;
import org.apache.calcite.rel.hint.RelHint;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.rex.RexFieldCollation;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexWindowBounds;
import org.apache.calcite.runtime.CalciteException;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.TableFunction;
import org.apache.calcite.schema.impl.TableFunctionImpl;
import org.apache.calcite.schema.impl.ViewTable;
import org.apache.calcite.schema.impl.ViewTableMacro;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlMatchRecognize;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlLibrary;
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory;
import org.apache.calcite.sql.fun.SqlLibraryOperators;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.OperandTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlOperandMetadata;
import org.apache.calcite.sql.type.SqlTypeFamily;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedTableFunction;
import org.apache.calcite.test.schemata.hr.HrSchema;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Program;
import org.apache.calcite.tools.Programs;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RelRunner;
import org.apache.calcite.tools.RelRunners;
import org.apache.calcite.tools.RuleSet;
import org.apache.calcite.tools.RuleSets;
import org.apache.calcite.util.Bug;
import org.apache.calcite.util.Holder;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Smalls;
import org.apache.calcite.util.TimestampString;
import org.apache.calcite.util.Util;
import org.apache.calcite.util.mapping.Mappings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static org.apache.calcite.test.Matchers.hasExpandedTree;
import static org.apache.calcite.test.Matchers.hasFieldNames;
import static org.apache.calcite.test.Matchers.hasHints;
import static org.apache.calcite.test.Matchers.hasTree;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link RelBuilder}.
 *
 * <p>Tasks:
 * <ol>
 *   <li>Add RelBuilder.scan(List&lt;String&gt;)</li>
 *   <li>Add RelBuilder.scan(Table)</li>
 *   <li>Test that {@link RelBuilder#filter} does not create a filter if the
 *   predicates optimize to true</li>
 *   <li>Test that {@link RelBuilder#filter} DOES create a filter if the
 *   predicates optimize to false. (Creating an empty Values seems too
 *   devious.)</li>
 *   <li>Test that {@link RelBuilder#scan} throws good error if table not
 *   found</li>
 *   <li>Test that {@link RelBuilder#scan} obeys case-sensitivity</li>
 *   <li>Test that {@link RelBuilder#join(JoinRelType, String...)} obeys
 *   case-sensitivity</li>
 *   <li>Test RelBuilder with alternative factories</li>
 *   <li>Test that {@link RelBuilder#field(String)} obeys case-sensitivity</li>
 *   <li>Test case-insensitive unique field names</li>
 *   <li>Test that an alias created using
 *      {@link RelBuilder#alias(RexNode, String)} is removed if not a top-level
 *      project</li>
 *   <li>{@link RelBuilder#aggregate} with grouping sets</li>
 *   <li>Add call to create {@link TableFunctionScan}</li>
 *   <li>Add call to create {@link Window}</li>
 *   <li>Add call to create {@link TableModify}</li>
 *   <li>Add call to create {@link Exchange}</li>
 *   <li>Add call to create {@link Correlate}</li>
 *   <li>Add call to create {@link AggregateCall} with filter</li>
 * </ol>
 */
public class RelBuilderTest {  // RelBuilder单元测试类：这是Apache Calcite框架中RelBuilder组件的完整测试套件
  /** Creates a config based on the "scott" schema. */  // 执行代码
  public static Frameworks.ConfigBuilder config() {  // 静态工厂方法：创建基于"scott"模式的配置构建器，用于测试环境初始化
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);  // Frameworks：使用Calcite框架工具类进行配置
    return Frameworks.newConfigBuilder()  // Frameworks：使用Calcite框架工具类进行配置
        .parserConfig(SqlParser.Config.DEFAULT)  // 执行代码
        .defaultSchema(  // 执行代码
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL))  // 执行代码
        .traitDefs((List<RelTraitDef>) null)  // 执行代码
        .programs(Programs.heuristicJoinOrder(Programs.RULE_SET, true, 2));  // 执行代码
  }

  /** Creates a config builder that will contain a view, "MYVIEW", and also  // 执行代码
   * the SCOTT JDBC schema, whose tables implement
   * {@link org.apache.calcite.schema.TranslatableTable}. */
  static Frameworks.ConfigBuilder expandingConfig(Connection connection)  // 静态工厂方法：创建包含视图"MYVIEW"和SCOTT JDBC模式的扩展配置构建器
      throws SQLException {  // 执行代码
    final CalciteConnection calciteConnection =  // 执行代码
        connection.unwrap(CalciteConnection.class);  // 执行代码
    final SchemaPlus root = calciteConnection.getRootSchema();  // 执行代码
    CalciteAssert.SchemaSpec spec = CalciteAssert.SchemaSpec.SCOTT;  // 执行代码
    CalciteAssert.addSchema(root, spec);  // 执行代码
    final String viewSql =  // 执行代码
        String.format(Locale.ROOT, "select * from \"%s\".\"%s\" where 1=1",  // 执行代码
            spec.schemaName, "EMP");  // 执行代码

    // create view
    ViewTableMacro macro =  // 执行代码
        ViewTable.viewMacro(root, viewSql, Collections.singletonList("test"),  // 执行代码
            Arrays.asList("test", "view"), false);  // 执行代码

    // register view (in root schema)
    root.add("MYVIEW", macro);  // 执行代码

    return Frameworks.newConfigBuilder().defaultSchema(root);  // Frameworks：使用Calcite框架工具类进行配置
  }

  /** Creates a RelBuilder with default config. */  // 执行代码
  static RelBuilder createBuilder() {  // 静态工厂方法：使用默认配置创建RelBuilder实例
    return createBuilder(c -> c);  // 执行代码
  }

  /** Creates a RelBuilder with transformed config. */  // 执行代码
  static RelBuilder createBuilder(UnaryOperator<RelBuilder.Config> transform) {  // 静态工厂方法：使用转换函数修改配置后创建RelBuilder实例，支持自定义配置
    final Frameworks.ConfigBuilder configBuilder = config();  // Frameworks：使用Calcite框架工具类进行配置
    configBuilder.context(  // 执行代码
        Contexts.of(transform.apply(RelBuilder.Config.DEFAULT)));  // 执行代码
    return RelBuilder.create(configBuilder.build());  // 构建操作：完成关系代数树的构建并返回根节点
  }

  @Test void testScan() {  // 测试方法：验证RelBuilder的Scan功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        RelBuilder.create(config().build())  // 构建操作：完成关系代数树的构建并返回根节点
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root,  // 断言验证：验证构建的RelNode是否符合预期
        hasTree("LogicalTableScan(table=[[scott, EMP]])\n"));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6620">[CALCITE-6620]
   * VALUES created by RelBuilder do not have a homogeneous type</a>. */
  @Test void differentTypeValues() {  // 测试方法
    CalciteAssert.that()  // 执行代码
        .with(CalciteConnectionProperty.LEX, Lex.JAVA)  // 执行代码
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, false)  // 执行代码
        .withSchema("s", new ReflectiveSchema(new HrSchema()))  // 执行代码
        .query("SELECT * FROM (VALUES (1, 2, 3), (CAST(5E0 AS REAL), 5E0, NULL))")  // 执行代码
        .explainContains("PLAN=EnumerableValues(tuples=[[{ 1.0E0, 2.0E0, 3 }, "  // 执行代码
            + "{ 5.0E0, 5.0E0, null }]])")  // 执行代码
        .returnsOrdered("EXPR$0=1.0; EXPR$1=2.0; EXPR$2=3", "EXPR$0=5.0; EXPR$1=5.0; EXPR$2=null");  // 执行代码
  }

  @Test void testScanQualifiedTable() {  // 测试方法：验证RelBuilder的ScanQualifiedTable功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM "scott"."emp"
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        RelBuilder.create(config().build())  // 构建操作：完成关系代数树的构建并返回根节点
            .scan("scott", "EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root,  // 断言验证：验证构建的RelNode是否符合预期
        hasTree("LogicalTableScan(table=[[scott, EMP]])\n"));  // 执行代码
  }

  @Test void testScanInvalidTable() {  // 测试方法：验证RelBuilder的ScanInvalidTable功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM zzz
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          RelBuilder.create(config().build())  // 构建操作：完成关系代数树的构建并返回根节点
              .scan("ZZZ") // this relation does not exist  // 扫描表操作：将表数据源添加到关系代数树中
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (Exception e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("Table 'ZZZ' not found"));  // 执行代码
    }
  }

  @Test void testScanInvalidSchema() {  // 测试方法：验证RelBuilder的ScanInvalidSchema功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM "zzz"."emp"
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          RelBuilder.create(config().build())  // 构建操作：完成关系代数树的构建并返回根节点
              .scan("ZZZ", "EMP") // the table exists, but the schema does not  // 扫描表操作：将表数据源添加到关系代数树中
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (Exception e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("Table 'ZZZ.EMP' not found"));  // 执行代码
    }
  }

  @Test void testScanInvalidQualifiedTable() {  // 测试方法：验证RelBuilder的ScanInvalidQualifiedTable功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM "scott"."zzz"
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          RelBuilder.create(config().build())  // 构建操作：完成关系代数树的构建并返回根节点
              .scan("scott", "ZZZ") // the schema is valid, but the table does not exist  // 扫描表操作：将表数据源添加到关系代数树中
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (Exception e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("Table 'scott.ZZZ' not found"));  // 执行代码
    }
  }

  @Test void testScanValidTableWrongCase() {  // 测试方法：验证RelBuilder的ScanValidTableWrongCase功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM "emp"
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          RelBuilder.create(config().build())  // 构建操作：完成关系代数树的构建并返回根节点
              .scan("emp") // the table is named 'EMP', not 'emp'  // 扫描表操作：将表数据源添加到关系代数树中
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("Expected error (table names are case-sensitive), but got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (Exception e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("Table 'emp' not found"));  // 执行代码
    }
  }

  @Test void testScanFilterTrue() {  // 测试方法：验证RelBuilder的ScanFilterTrue功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE TRUE
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(builder.literal(true))  // 过滤操作：添加过滤条件到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root,  // 断言验证：验证构建的RelNode是否符合预期
        hasTree("LogicalTableScan(table=[[scott, EMP]])\n"));  // 执行代码
  }

  @Test void testScanFilterTriviallyFalse() {  // 测试方法：验证RelBuilder的ScanFilterTriviallyFalse功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE 1 = 2
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(builder.equals(builder.literal(1), builder.literal(2)))  // 过滤操作：添加过滤条件到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root,  // 断言验证：验证构建的RelNode是否符合预期
        hasTree("LogicalValues(tuples=[[]])\n"));  // 执行代码
  }

  @Test void testScanFilterEquals() {  // 测试方法：验证RelBuilder的ScanFilterEquals功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 20
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("DEPTNO"), builder.literal(20)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalFilter(condition=[=($7, 20)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testScanFilterGreaterThan() {  // 测试方法：验证RelBuilder的ScanFilterGreaterThan功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno > 20
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field("DEPTNO"), builder.literal(20)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalFilter(condition=[>($7, 20)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSnapshotTemporalTable() {  // 测试方法：验证RelBuilder的SnapshotTemporalTable功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM products_temporal FOR SYSTEM_TIME AS OF TIMESTAMP '2011-07-20 12:34:56'
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("products_temporal")  // 扫描表操作：将表数据源添加到关系代数树中
            .snapshot(  // 执行代码
                builder.getRexBuilder().makeTimestampLiteral(  // 执行代码
                    new TimestampString("2011-07-20 12:34:56"), 0))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalSnapshot(period=[2011-07-20 12:34:56])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
            + "  LogicalTableScan(table=[[scott, products_temporal]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testTableFunctionScan() {  // 测试方法：验证RelBuilder的TableFunctionScan功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM TABLE(
    //       DEDUP(CURSOR(select * from emp),
    //             CURSOR(select * from DEPT), 'NAME'))
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final SqlOperator dedupFunction =  // SQL操作符：使用标准SQL操作符构建表达式
        new MockSqlOperatorTable.DedupFunction();  // SQL操作符：使用标准SQL操作符构建表达式
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .functionScan(dedupFunction, 2, builder.cursor(2, 0),  // 执行代码
            builder.cursor(2, 1))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalTableFunctionScan("  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "invocation=[DEDUP(CURSOR($0), CURSOR($1))], "  // 执行代码
        + "rowType=[RecordType(VARCHAR(1024) NAME)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    // Make sure that the builder's stack is empty.
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RelNode node = builder.build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + node);  // 失败标记：如果执行到此处说明测试失败
    } catch (NoSuchElementException e) {  // 捕获异常：处理预期的异常情况
      assertNull(e.getMessage());  // 执行代码
    }
  }

  @Test void testTableFunctionScanZeroInputs() {  // 测试方法：验证RelBuilder的TableFunctionScanZeroInputs功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM TABLE(RAMP(3))
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final SqlOperator rampFunction = new MockSqlOperatorTable.RampFunction();  // SQL操作符：使用标准SQL操作符构建表达式
    RelNode root = builder.functionScan(rampFunction, 0, builder.literal(3))  // 声明根节点：最终构建的关系代数树的根节点
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalTableFunctionScan(invocation=[RAMP(3)], "  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "rowType=[RecordType(INTEGER I)])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    // Make sure that the builder's stack is empty.
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RelNode node = builder.build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + node);  // 失败标记：如果执行到此处说明测试失败
    } catch (NoSuchElementException e) {  // 捕获异常：处理预期的异常情况
      assertNull(e.getMessage());  // 执行代码
    }
  }

  /** Tests scanning a table function whose row type is determined by parsing a  // 执行代码
   * JSON argument. The arguments must therefore be available at prepare
   * time. */
  @Test void testTableFunctionScanDynamicType() {  // 测试方法：验证RelBuilder的TableFunctionScanDynamicType功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM TABLE("dynamicRowType"('{nullable:true,fields:[...]}', 3))
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final Method m = Smalls.DYNAMIC_ROW_TYPE_TABLE_METHOD;  // 执行代码
    final TableFunction tableFunction =  // 执行代码
        TableFunctionImpl.create(m.getDeclaringClass(), m.getName());  // 执行代码
    final SqlOperator operator =  // SQL操作符：使用标准SQL操作符构建表达式
        new SqlUserDefinedTableFunction(  // 执行代码
            new SqlIdentifier("dynamicRowType", SqlParserPos.ZERO),  // 执行代码
            SqlKind.OTHER_FUNCTION, ReturnTypes.CURSOR, InferTypes.ANY_NULLABLE,  // 执行代码
            Arg.metadata(  // 执行代码
                Arg.of("count", f -> f.createSqlType(SqlTypeName.INTEGER),  // SQL类型名：指定SQL数据类型
                    SqlTypeFamily.INTEGER, false),  // 执行代码
                Arg.of("typeJson", f -> f.createSqlType(SqlTypeName.VARCHAR),  // SQL类型名：指定SQL数据类型
                    SqlTypeFamily.STRING, false)),  // 执行代码
            tableFunction);  // 执行代码

    final String jsonRowType = "{\"nullable\":false,\"fields\":["  // 执行代码
        + "  {\"name\":\"i\",\"type\":\"INTEGER\",\"nullable\":false},"  // 执行代码
        + "  {\"name\":\"d\",\"type\":\"DATE\",\"nullable\":true}"  // 执行代码
        + "]}";
    final int rowCount = 3;  // 执行代码
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.functionScan(operator, 0, builder.literal(jsonRowType),  // 执行代码
                builder.literal(rowCount))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalTableFunctionScan("  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "invocation=[dynamicRowType('{\"nullable\":false,\"fields\":["  // 执行代码
        + "  {\"name\":\"i\",\"type\":\"INTEGER\",\"nullable\":false},"  // 执行代码
        + "  {\"name\":\"d\",\"type\":\"DATE\",\"nullable\":true}]}', 3)], "  // 执行代码
        + "rowType=[RecordType(INTEGER i, DATE d)])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    // Make sure that the builder's stack is empty.
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RelNode node = builder.build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + node);  // 失败标记：如果执行到此处说明测试失败
    } catch (NoSuchElementException e) {  // 捕获异常：处理预期的异常情况
      assertNull(e.getMessage());  // 执行代码
    }
  }

  @Test void testJoinTemporalTable() {  // 测试方法：验证RelBuilder的JoinTemporalTable功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM orders
    //   JOIN products_temporal FOR SYSTEM_TIME AS OF TIMESTAMP '2011-07-20 12:34:56'
    //   ON orders.product = products_temporal.id
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("orders")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("products_temporal")  // 扫描表操作：将表数据源添加到关系代数树中
            .snapshot(  // 执行代码
                builder.getRexBuilder().makeTimestampLiteral(  // 执行代码
                    new TimestampString("2011-07-20 12:34:56"), 0))  // 执行代码
            .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
                builder.equals(builder.field(2, 0, "PRODUCT"),  // 执行代码
                    builder.field(2, 1, "ID")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalJoin(condition=[=($2, $4)], joinType=[inner])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, orders]])\n"  // 执行代码
        + "  LogicalSnapshot(period=[2011-07-20 12:34:56])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, products_temporal]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testJoinTemporalTableForTimestampWithLocalTimezone() {  // 测试方法：验证RelBuilder的JoinTemporalTableForTimestampWithLocalTimezone功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM orders
    //   JOIN products_temporal FOR SYSTEM_TIME
    //   AS OF TIMESTAMP WITH LOCAL TIME ZONE '2011-07-20 12:34:56'
    //   ON orders.product = products_temporal.id
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("orders")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("products_temporal")  // 扫描表操作：将表数据源添加到关系代数树中
            .snapshot(  // 执行代码
                builder.getRexBuilder().makeTimestampWithLocalTimeZoneLiteral(  // 执行代码
                    new TimestampString("2011-07-20 12:34:56"), 0))  // 执行代码
            .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
                builder.equals(builder.field(2, 0, "PRODUCT"),  // 执行代码
                    builder.field(2, 1, "ID")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalJoin(condition=[=($2, $4)], joinType=[inner])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, orders]])\n"  // 执行代码
        + "  LogicalSnapshot(period=[2011-07-20 12:34:56:TIMESTAMP_WITH_LOCAL_TIME_ZONE(0)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, products_temporal]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests that {@link RelBuilder#project} simplifies expressions if and only if  // 执行代码
   * {@link RelBuilder.Config#simplify}. */
  @Test void testSimplify() {  // 测试方法：验证RelBuilder的Simplify功能
    checkSimplify(c -> c.withSimplify(true),  // 执行代码
        hasTree("LogicalProject($f0=[true])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n"));  // 执行代码
    checkSimplify(c -> c,  // 执行代码
        hasTree("LogicalProject($f0=[true])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n"));  // 执行代码
    checkSimplify(c -> c.withSimplify(false),  // 执行代码
        hasTree("LogicalProject($f0=[IS NOT NULL($0)])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n"));  // 执行代码
  }

  private void checkSimplify(UnaryOperator<RelBuilder.Config> transform,  // 辅助方法
      Matcher<RelNode> matcher) {  // 执行代码
    final RelBuilder builder = createBuilder(transform);  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.isNotNull(builder.field("EMPNO")))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, matcher);  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testScanFilterOr() {  // 测试方法：验证RelBuilder的ScanFilterOr功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE (deptno = 20 OR comm IS NULL) AND mgr IS NOT NULL
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.or(  // 执行代码
                    builder.equals(builder.field("DEPTNO"),  // 执行代码
                        builder.literal(20)),  // 执行代码
                    builder.isNull(builder.field(6))),  // 执行代码
                builder.isNotNull(builder.field(3)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[AND(OR(=($7, 20), IS NULL($6)), IS NOT NULL($3))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testScanFilterOr2() {  // 测试方法：验证RelBuilder的ScanFilterOr2功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 20 OR deptno = 20
    // simplifies to
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 20
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.or(  // 执行代码
                    builder.greaterThan(builder.field("DEPTNO"),  // 执行代码
                        builder.literal(20)),  // 执行代码
                    builder.greaterThan(builder.field("DEPTNO"),  // 执行代码
                        builder.literal(20))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalFilter(condition=[>($7, 20)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testScanFilterAndFalse() {  // 测试方法：验证RelBuilder的ScanFilterAndFalse功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 20 AND FALSE
    // simplifies to
    //   VALUES
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field("DEPTNO"),  // 执行代码
                    builder.literal(20)),  // 执行代码
                builder.literal(false))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalValues(tuples=[[]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testScanFilterAndTrue() {  // 测试方法：验证RelBuilder的ScanFilterAndTrue功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 20 AND TRUE
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field("DEPTNO"),  // 执行代码
                    builder.literal(20)),  // 执行代码
                builder.literal(true))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalFilter(condition=[>($7, 20)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2730">[CALCITE-2730]
   * RelBuilder incorrectly simplifies a filter with duplicate conjunction to
   * empty</a>. */
  @Test void testScanFilterDuplicateAnd() {  // 测试方法：验证RelBuilder的ScanFilterDuplicateAnd功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno > 20 AND deptno > 20 AND deptno > 20
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    builder.scan("EMP");  // 扫描表操作：将表数据源添加到关系代数树中
    final RexNode condition =  // RexNode：声明行表达式节点，代表关系表达式
        builder.greaterThan(builder.field("DEPTNO"), builder.literal(20));  // 执行代码
    final RexNode condition2 =  // RexNode：声明行表达式节点，代表关系表达式
        builder.lessThan(builder.field("DEPTNO"), builder.literal(30));  // 执行代码
    final RelNode root = builder.filter(condition, condition, condition)  // 声明根节点：最终构建的关系代数树的根节点
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalFilter(condition=[>($7, 20)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno > 20 AND deptno < 30 AND deptno > 20
    final RelNode root2 = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .filter(condition, condition2, condition, condition)  // 过滤操作：添加过滤条件到关系代数树
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[SEARCH($7, Sarg[(20..30)])])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root2, hasTree(expected2));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4325">[CALCITE-4325]
   * RexSimplify incorrectly simplifies complex expressions with Sarg and
   * NULL</a>. */
  @Test void testFilterAndOrWithNull() {  // 测试方法：验证RelBuilder的FilterAndOrWithNull功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE (deptno <> 20 OR deptno IS NULL) AND deptno = 10
    // Should be simplified to:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 10
    // With [CALCITE-4325], is incorrectly simplified to:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 10 OR deptno IS NULL
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.and(  // 执行代码
                    b.or(  // 执行代码
                        b.notEquals(b.field("DEPTNO"), b.literal(20)),  // 执行代码
                        b.isNull(b.field("DEPTNO"))),  // 执行代码
                    b.equals(b.field("DEPTNO"), b.literal(10))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalFilter(condition=[=($7, 10)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testFilterAndOrWithNull2() {  // 测试方法：验证RelBuilder的FilterAndOrWithNull2功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE (deptno = 20 OR deptno IS NULL) AND deptno = 10
    // Should be simplified to:
    //   No rows (WHERE FALSE)
    // With [CALCITE-4325], is incorrectly simplified to:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno IS NULL
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.and(  // 执行代码
                    b.or(b.equals(b.field("DEPTNO"), b.literal(20)),  // 执行代码
                        b.isNull(b.field("DEPTNO"))),  // 执行代码
                    b.equals(b.field("DEPTNO"), b.literal(10))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalValues(tuples=[[]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testBadFieldName() {  // 测试方法：验证RelBuilder的BadFieldName功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RexInputRef ref = builder.scan("EMP").field("deptno");  // 扫描表操作：将表数据源添加到关系代数树中
      fail("expected error, got " + ref);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("field [deptno] not found; input fields are: [EMPNO, ENAME, JOB, "  // 执行代码
              + "MGR, HIREDATE, SAL, COMM, DEPTNO]"));  // 执行代码
    }
  }

  @Test void testBadFieldOrdinal() {  // 测试方法：验证RelBuilder的BadFieldOrdinal功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RexInputRef ref = builder.scan("DEPT").field(20);  // 扫描表操作：将表数据源添加到关系代数树中
      fail("expected error, got " + ref);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("field ordinal [20] out of range; "  // 执行代码
                  + "input fields are: [DEPTNO, DNAME, LOC]"));  // 执行代码
    }
  }

  @Test void testBadType() {  // 测试方法：验证RelBuilder的BadType功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      builder.scan("EMP");  // 扫描表操作：将表数据源添加到关系代数树中
      RexNode call =  // RexNode：声明行表达式节点，代表关系表达式
          builder.call(SqlStdOperatorTable.PLUS, builder.field(1),  // SQL操作符：使用标准SQL操作符构建表达式
              builder.field(3));  // 执行代码
      fail("expected error, got " + call);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("Cannot infer return type for +; "  // 执行代码
              + "operand types: [VARCHAR(10), SMALLINT]"));  // 执行代码
    }
  }

  @Test void testProject() {  // 测试方法：验证RelBuilder的Project功能
    // Equivalent SQL:
    //   SELECT deptno, CAST(comm AS SMALLINT) AS comm, 20 AS $f2,
    //     comm AS comm3, comm AS c
    //   FROM emp
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.cast(builder.field(6), SqlTypeName.SMALLINT),  // SQL类型名：指定SQL数据类型
                builder.literal(20),  // 执行代码
                builder.field(6),  // 执行代码
                builder.alias(builder.field(6), "C"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Note: CAST(COMM) gets the COMM alias because it occurs first
    // Note: AS(COMM, C) becomes just $6
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$7], COMM=[CAST($6):SMALLINT NOT NULL], $f2=[20], COMM0=[$6], C=[$6])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests each method that creates a scalar expression. */  // 执行代码
  @Test void testProject2() {  // 测试方法：验证RelBuilder的Project2功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.cast(builder.field(6), SqlTypeName.SMALLINT),  // SQL类型名：指定SQL数据类型
                builder.or(  // 执行代码
                    builder.equals(builder.field("DEPTNO"),  // 执行代码
                        builder.literal(20)),  // 执行代码
                    builder.and(  // 执行代码
                        builder.cast(builder.literal(null),  // 执行代码
                            SqlTypeName.BOOLEAN),  // SQL类型名：指定SQL数据类型
                        builder.equals(builder.field("DEPTNO"),  // 执行代码
                            builder.literal(10)),  // 执行代码
                        builder.and(builder.isNull(builder.field(6)),  // 执行代码
                            builder.not(builder.isNotNull(builder.field(5))))),  // 执行代码
                    builder.equals(builder.field("DEPTNO"),  // 执行代码
                        builder.literal(20)),  // 执行代码
                    builder.equals(builder.field("DEPTNO"),  // 执行代码
                        builder.literal(30))),  // 执行代码
                builder.alias(builder.isNull(builder.field(2)), "n2"),  // 执行代码
                builder.alias(builder.isNotNull(builder.field(3)), "nn2"),  // 执行代码
                builder.literal(20),  // 执行代码
                builder.field(6),  // 执行代码
                builder.alias(builder.field(6), "C"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$7], COMM=[CAST($6):SMALLINT NOT NULL], "  // 执行代码
        + "$f2=[OR(SEARCH($7, Sarg[20, 30]), AND(null, =($7, 10), "  // 执行代码
        + "IS NULL($6), IS NULL($5)))], n2=[IS NULL($2)], "  // 执行代码
        + "nn2=[IS NOT NULL($3)], $f5=[20], COMM0=[$6], C=[$6])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testProjectIdentity() {  // 测试方法：验证RelBuilder的ProjectIdentity功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.fields(Mappings.bijection(Arrays.asList(0, 1, 2))))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalTableScan(table=[[scott, DEPT]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1297">[CALCITE-1297]
   * RelBuilder does not translate identity projects even if they rename
   * fields</a>. */
  @Test void testProjectIdentityWithFieldsRename() {  // 测试方法：验证RelBuilder的ProjectIdentityWithFieldsRename功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.alias(builder.field(0), "a"),  // 投影操作：选择和计算输出字段
                builder.alias(builder.field(1), "b"),  // 执行代码
                builder.alias(builder.field(2), "c"))  // 执行代码
            .as("t1")  // 执行代码
            .project(builder.field("a"),  // 投影操作：选择和计算输出字段
                builder.field("t1", "c"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalProject(a=[$0], c=[$2])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Variation on {@link #testProjectIdentityWithFieldsRename}: don't use a  // 执行代码
   * table alias, and make sure the field names propagate through a filter. */
  @Test void testProjectIdentityWithFieldsRenameFilter() {  // 测试方法：验证RelBuilder的ProjectIdentityWithFieldsRenameFilter功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.alias(builder.field(0), "a"),  // 投影操作：选择和计算输出字段
                builder.alias(builder.field(1), "b"),  // 执行代码
                builder.alias(builder.field(2), "c"))  // 执行代码
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("a"),  // 执行代码
                    builder.literal(20)))  // 执行代码
            .aggregate(builder.groupKey(0, 1, 2),  // 聚合操作：添加聚合节点到关系代数树
                builder.aggregateCall(SqlStdOperatorTable.SUM,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field(0)))  // 执行代码
            .project(builder.field("c"),  // 投影操作：选择和计算输出字段
                builder.field("a"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(c=[$2], a=[$0])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0, 1, 2}], agg#0=[SUM($0)])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($0, 20)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testProjectLeadingEdge() {  // 测试方法：验证RelBuilder的ProjectLeadingEdge功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.fields(Mappings.bijection(Arrays.asList(0, 1, 2))))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testProjectWithAliasFromScan() {  // 测试方法：验证RelBuilder的ProjectWithAliasFromScan功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field(1, "EMP", "ENAME"))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalProject(ENAME=[$1])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3228">[CALCITE-3228]
   * IllegalArgumentException in getMapping() for project containing same reference</a>. */
  @Test void testProjectMapping() {  // 测试方法：验证RelBuilder的ProjectMapping功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field(0), builder.field(0))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, instanceOf(Project.class));  // 断言验证：验证构建的RelNode是否符合预期
    Project project = (Project) root;  // 执行代码
    Mappings.TargetMapping mapping = project.getMapping();  // 执行代码
    assertThat(mapping, nullValue());  // 执行代码
  }

  private void project1(int value, SqlTypeName sqlTypeName, String message, String expected) {  // 辅助方法
    final RelBuilder builder = createBuilder(c -> c.withSimplifyValues(false));  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RexBuilder rex = builder.getRexBuilder();  // 执行代码
    RelNode actual =  // 执行代码
        builder.values(new String[]{"x"}, 42)  // 执行代码
            .empty()  // 执行代码
            .project(  // 投影操作：选择和计算输出字段
                rex.makeLiteral(value,  // 执行代码
                    rex.getTypeFactory().createSqlType(sqlTypeName)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(message, actual, hasTree(expected));  // 执行代码
  }

  @Test void testProject1asInt() {  // 测试方法：验证RelBuilder的Project1asInt功能
    project1(1, SqlTypeName.INTEGER,  // SQL类型名：指定SQL数据类型
        "project(1 as INT) might omit type of 1 in the output plan as"  // 执行代码
            + " it is convention to omit INTEGER for integer literals",  // 执行代码
        "LogicalProject($f0=[1])\n"  // 执行代码
            + "  LogicalValues(tuples=[[]])\n");  // 执行代码
  }

  @Test void testProject1asBigInt() {  // 测试方法：验证RelBuilder的Project1asBigInt功能
    project1(1, SqlTypeName.BIGINT, "project(1 as BIGINT) should contain"  // SQL类型名：指定SQL数据类型
            + " type of 1 in the output plan since the convention is to omit type of INTEGER",  // 执行代码
        "LogicalProject($f0=[1:BIGINT])\n"  // 执行代码
            + "  LogicalValues(tuples=[[]])\n");  // 执行代码
  }

  @Test void testProjectBloat() {  // 测试方法：验证RelBuilder的ProjectBloat功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(  // 投影操作：选择和计算输出字段
                b.alias(  // 执行代码
                    caseCall(b, b.field("DEPTNO"),  // 执行代码
                        b.literal(0), b.literal("zero"),  // 执行代码
                        b.literal(1), b.literal("one"),  // 执行代码
                        b.literal(2), b.literal("two"),  // 执行代码
                        b.literal("other")),  // 执行代码
                    "v"))  // 执行代码
            .project(  // 投影操作：选择和计算输出字段
                b.call(SqlStdOperatorTable.PLUS, b.field("v"), b.field("v")))  // SQL操作符：使用标准SQL操作符构建表达式
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Complexity of bottom is 14; top is 3; merged is 29; difference is -12.
    // So, we merge if bloat is 20 or 100 (the default),
    // but not if it is -1, 0 or 10.
    final String expected = "LogicalProject($f0=[+"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "(CASE(=($7, 0), 'zero', =($7, 1), 'one', =($7, 2), 'two', 'other'),"  // 执行代码
        + " CASE(=($7, 0), 'zero', =($7, 1), 'one', =($7, 2), 'two', 'other'))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final String expectedNeg = "LogicalProject($f0=[+($0, $0)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalProject(v=[CASE(=($7, 0), 'zero', =($7, 1), "  // 执行代码
        + "'one', =($7, 2), 'two', 'other')])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withBloat(0))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedNeg));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withBloat(-1))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedNeg));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withBloat(10))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedNeg));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withBloat(20))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
  }

  @Test void testProjectBloat2() {  // 测试方法：验证RelBuilder的ProjectBloat2功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(  // 投影操作：选择和计算输出字段
                b.field("DEPTNO"),  // 执行代码
                b.field("SAL"),  // 执行代码
                b.alias(  // 执行代码
                    b.call(SqlStdOperatorTable.PLUS, b.field("DEPTNO"),  // SQL操作符：使用标准SQL操作符构建表达式
                        b.field("EMPNO")), "PLUS"))  // 执行代码
            .project(  // 投影操作：选择和计算输出字段
                b.call(SqlStdOperatorTable.MULTIPLY, b.field("SAL"),  // SQL操作符：使用标准SQL操作符构建表达式
                    b.field("PLUS")),  // 执行代码
                b.field("SAL"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Complexity of bottom is 5; top is 4; merged is 6; difference is 3.
    // So, we merge except when bloat is -1.
    final String expected = "LogicalProject($f0=[*($5, +($7, $0))], SAL=[$5])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final String expectedNeg = "LogicalProject($f0=[*($1, $2)], SAL=[$1])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalProject(DEPTNO=[$7], SAL=[$5], PLUS=[+($7, $0)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withBloat(0))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withBloat(-1))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedNeg));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withBloat(10))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withBloat(20))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
  }

  private RexNode caseCall(RelBuilder b, RexNode ref, RexNode... nodes) {  // 辅助方法
    final List<RexNode> list = new ArrayList<>();  // RexNode：声明行表达式节点，代表关系表达式
    for (int i = 0; i + 1 < nodes.length; i += 2) {  // 执行代码
      list.add(b.equals(ref, nodes[i]));  // 执行代码
      list.add(nodes[i + 1]);  // 执行代码
    }
    list.add(nodes.length % 2 == 1 ? nodes[nodes.length - 1]  // 执行代码
        : b.literal(null));  // 执行代码
    return b.call(SqlStdOperatorTable.CASE, list);  // SQL操作符：使用标准SQL操作符构建表达式
  }

  /** Creates a {@link Project} that contains a windowed aggregate function.  // 执行代码
   * Repeats the using {@link RelBuilder.AggCall#over} and
   * {@link RexBuilder#makeOver}. */
  @Test void testProjectOver() {  // 测试方法：验证RelBuilder的ProjectOver功能
    final Function<RelBuilder, RelNode> f = b -> {  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
      final RelDataType intType =  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
          b.getTypeFactory().createSqlType(SqlTypeName.INTEGER);  // SQL类型名：指定SQL数据类型
      return b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
          .project(b.field("DEPTNO"),  // 投影操作：选择和计算输出字段
              b.alias(  // 执行代码
                  b.getRexBuilder().makeOver(intType,  // RexBuilder：用于构建RexNode表达式树的构建器
                      SqlStdOperatorTable.ROW_NUMBER, ImmutableList.of(),  // SQL操作符：使用标准SQL操作符构建表达式
                      ImmutableList.of(),  // 执行代码
                      ImmutableList.of(  // 执行代码
                          new RexFieldCollation(b.field("EMPNO"),  // 执行代码
                              ImmutableSet.of())),  // 执行代码
                      RexWindowBounds.UNBOUNDED_PRECEDING,  // 执行代码
                      RexWindowBounds.UNBOUNDED_FOLLOWING,  // 执行代码
                      true, true, false, false, false),  // 执行代码
                  "x"))  // 执行代码
          .build();  // 构建操作：完成关系代数树的构建并返回根节点
    };
    final Function<RelBuilder, RelNode> f2 = b -> b.scan("EMP")  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        .project(b.field("DEPTNO"),  // 投影操作：选择和计算输出字段
            b.aggregateCall(SqlStdOperatorTable.ROW_NUMBER)  // SQL操作符：使用标准SQL操作符构建表达式
                .over()  // 执行代码
                .partitionBy()  // 执行代码
                .orderBy(b.field("EMPNO"))  // 执行代码
                .rowsUnbounded()  // 执行代码
                .allowPartial(true)  // 执行代码
                .nullWhenCountZero(false)  // 执行代码
                .as("x"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$7], x=[ROW_NUMBER() OVER (ORDER BY $0)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f2.apply(createBuilder()), hasTree(expected));  // 执行代码
  }

  /** Tests that RelBuilder does not merge a Project that contains a windowed  // 执行代码
   * aggregate function into a lower Project. */
  @Test void testProjectOverOver() {  // 测试方法：验证RelBuilder的ProjectOverOver功能
    final Function<RelBuilder, RelNode> f = b -> b.scan("EMP")  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        .project(b.field("DEPTNO"),  // 投影操作：选择和计算输出字段
            b.aggregateCall(SqlStdOperatorTable.ROW_NUMBER)  // SQL操作符：使用标准SQL操作符构建表达式
                .over()  // 执行代码
                .partitionBy()  // 执行代码
                .orderBy(b.field("EMPNO"))  // 执行代码
                .rowsUnbounded()  // 执行代码
                .as("x"))  // 执行代码
        .project(b.field("DEPTNO"),  // 投影操作：选择和计算输出字段
            b.aggregateCall(SqlStdOperatorTable.ROW_NUMBER)  // SQL操作符：使用标准SQL操作符构建表达式
                .over()  // 执行代码
                .partitionBy()  // 执行代码
                .orderBy(b.field("DEPTNO"))  // 执行代码
                .rowsUnbounded()  // 执行代码
                .as("y"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$0], y=[ROW_NUMBER() OVER (ORDER BY $0)])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$7], x=[ROW_NUMBER() OVER (ORDER BY $0)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testRename() {  // 测试方法：验证RelBuilder的Rename功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象

    // No rename necessary (null name is ignored)
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .rename(Arrays.asList("DEPTNO", null))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalTableScan(table=[[scott, DEPT]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    // No rename necessary (prefix matches)
    root =  // 执行代码
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .rename(ImmutableList.of("DEPTNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    // Add project to rename fields
    root =  // 执行代码
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .rename(Arrays.asList("NAME", null, "DEPTNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(NAME=[$0], DNAME=[$1], DEPTNO=[$2])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected2));  // 断言验证：验证构建的RelNode是否符合预期

    // If our requested list has non-unique names, we might get the same field
    // names we started with. Don't add a useless project.
    root =  // 执行代码
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .rename(Arrays.asList("DEPTNO", null, "DEPTNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected3 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$0], DNAME=[$1], DEPTNO0=[$2])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected3));  // 断言验证：验证构建的RelNode是否符合预期
    root =  // 执行代码
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .rename(Arrays.asList("DEPTNO", null, "DEPTNO"))  // 执行代码
            .rename(Arrays.asList("DEPTNO", null, "DEPTNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // No extra Project
    assertThat(root, hasTree(expected3));  // 断言验证：验证构建的RelNode是否符合预期

    // Name list too long
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      root =  // 执行代码
          builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
              .rename(ImmutableList.of("NAME", "DEPTNO", "Y", "Z"))  // 执行代码
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("More names than fields"));  // 执行代码
    }
  }

  @Test void testRenameValues() {  // 测试方法：验证RelBuilder的RenameValues功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.values(new String[]{"a", "b"}, true, 1, false, -50)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ true, 1 }, { false, -50 }]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    // When you rename Values, you get a Values with a new row type, no Project
    root =  // 执行代码
        builder.push(root)  // 执行代码
            .rename(ImmutableList.of("x", "y z"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    assertThat(root, hasFieldNames("[x, y z]"));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests conditional rename using {@link RelBuilder#let}. */  // 执行代码
  @Test void testLetRename() {  // 测试方法：验证RelBuilder的LetRename功能
    final AtomicInteger i = new AtomicInteger();  // 执行代码
    final Function<RelBuilder, String> f = builder ->  // 执行代码
        builder.values(new String[]{"a", "b"}, 1, true)  // 执行代码
            .rename(Arrays.asList("p", "q"))  // 执行代码
            .let(r -> i.getAndIncrement() == 0  // 执行代码
                ? r.rename(Arrays.asList("x", "y")) : r)  // 执行代码
            .let(r -> i.getAndIncrement() == 1  // 执行代码
                ? r.project(r.field(1), r.field(0)) : r)  // 投影操作：选择和计算输出字段
            .let(r -> i.getAndIncrement() == 0  // 执行代码
                ? r.rename(Arrays.asList("c", "d")) : r)  // 执行代码
            .let(r -> r.build().getRowType().toString());  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "RecordType(BOOLEAN y, INTEGER x)";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(f.apply(createBuilder()), is(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(i.get(), is(3));  // 执行代码
  }

  @Test void testPermute() {  // 测试方法：验证RelBuilder的Permute功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .permute(Mappings.bijection(Arrays.asList(1, 2, 0)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalProject(JOB=[$2], EMPNO=[$0], ENAME=[$1])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testConvert() {  // 测试方法：验证RelBuilder的Convert功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelDataType rowType =  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
        builder.getTypeFactory().builder()  // 执行代码
            .add("a", SqlTypeName.BIGINT)  // SQL类型名：指定SQL数据类型
            .add("b", SqlTypeName.VARCHAR, 10)  // SQL类型名：指定SQL数据类型
            .add("c", SqlTypeName.VARCHAR, 10).nullable(true)  // SQL类型名：指定SQL数据类型
            .add("d", SqlTypeName.INTEGER).nullable(true)  // SQL类型名：指定SQL数据类型
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .projectPlus(builder.alias(builder.literal(2), "two"))  // 执行代码
            .convert(rowType, false)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[CAST($0):BIGINT NOT NULL], "  // 执行代码
        + "DNAME=[CAST($1):VARCHAR(10) NOT NULL], "  // 执行代码
        + "LOC=[CAST($2):VARCHAR(10)], "  // 执行代码
        + "two=[CAST(2):INTEGER])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testConvertRename() {  // 测试方法：验证RelBuilder的ConvertRename功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelDataType rowType =  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
        builder.getTypeFactory().builder()  // 执行代码
            .add("a", SqlTypeName.BIGINT)  // SQL类型名：指定SQL数据类型
            .add("b", SqlTypeName.VARCHAR, 10)  // SQL类型名：指定SQL数据类型
            .add("c", SqlTypeName.VARCHAR, 10)  // SQL类型名：指定SQL数据类型
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .convert(rowType, true)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(a=[CAST($0):BIGINT NOT NULL], b=[CAST($1):VARCHAR(10) NOT NULL], c=[CAST($2):VARCHAR(10) NOT NULL])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4429">[CALCITE-4429]
   * RelOptUtil#createCastRel should throw an exception when the desired row type
   * and the row type to be converted don't have the same number of fields</a>. */
  @Test void testConvertNegative() {  // 测试方法：验证RelBuilder的ConvertNegative功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelDataType rowType =  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
        builder.getTypeFactory().builder()  // 执行代码
            .add("a", SqlTypeName.BIGINT)  // SQL类型名：指定SQL数据类型
            .add("b", SqlTypeName.VARCHAR, 10)  // SQL类型名：指定SQL数据类型
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    IllegalArgumentException ex =  // 执行代码
        assertThrows(IllegalArgumentException.class, () ->  // 异常断言：验证是否抛出预期的异常
            builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
                .convert(rowType, false)  // 执行代码
                .build(),  // 构建操作：完成关系代数树的构建并返回根节点
            "Convert should fail since the field counts are not equal.");  // 执行代码
    assertThat(ex.getMessage(), containsString("Field counts are not equal"));  // 执行代码
  }

  @Test void testAggregate() {  // 测试方法：验证RelBuilder的Aggregate功能
    // Equivalent SQL:
    //   SELECT COUNT(DISTINCT deptno) AS c
    //   FROM emp
    //   GROUP BY ()
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(builder.groupKey(),  // 聚合操作：添加聚合节点到关系代数树
                builder.count(true, "C", builder.field("DEPTNO")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{}], C=[COUNT(DISTINCT $7)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAggregate2() {  // 测试方法：验证RelBuilder的Aggregate2功能
    // Equivalent SQL:
    //   SELECT COUNT(*) AS c, SUM(mgr + 1) AS s
    //   FROM emp
    //   GROUP BY ename, hiredate + mgr
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(builder.field(1),  // 执行代码
                    builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.field(4),  // 执行代码
                        builder.field(3)),  // 执行代码
                    builder.field(1)),  // 执行代码
                builder.countStar("C"),  // 执行代码
                builder.sum(  // 执行代码
                    builder.call(SqlStdOperatorTable.PLUS, builder.field(3),  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.literal(1))).as("S"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{0, 1}], C=[COUNT()], S=[SUM($2)])\n"  // 执行代码
        + "  LogicalProject(ENAME=[$1], $f8=[+($4, $3)], $f9=[+($3, 1)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果

    // now without pruning
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{1, 8}], C=[COUNT()], S=[SUM($9)])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], "  // 执行代码
        + "HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+($4, $3)], "  // 执行代码
        + "$f9=[+($3, 1)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withPruneInputOfAggregate(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected2));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2192">[CALCITE-2192]
   * RelBuilder wrongly skips creation of Aggregate that prunes columns if input
   * is unique</a>. */
  @Test void testAggregate3() {  // 测试方法：验证RelBuilder的Aggregate3功能
    // Equivalent SQL:
    //   SELECT DISTINCT deptno FROM (
    //     SELECT deptno, COUNT(*)
    //     FROM emp
    //     GROUP BY deptno)
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(builder.groupKey(builder.field(1)),  // 聚合操作：添加聚合节点到关系代数树
                builder.count().as("C"))  // 执行代码
            .aggregate(builder.groupKey(builder.field(0)))  // 聚合操作：添加聚合节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(ENAME=[$0])\n"  // 执行代码
        + "  LogicalAggregate(group=[{1}], C=[COUNT()])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** As {@link #testAggregate3()} but with Filter. */  // 执行代码
  @Test void testAggregate4() {  // 测试方法：验证RelBuilder的Aggregate4功能
    // Equivalent SQL:
    //   SELECT DISTINCT deptno FROM (
    //     SELECT deptno, COUNT(*)
    //     FROM emp
    //     GROUP BY deptno
    //     HAVING COUNT(*) > 3)
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(builder.groupKey(builder.field(1)),  // 聚合操作：添加聚合节点到关系代数树
                builder.count().as("C"))  // 执行代码
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field(1), builder.literal(3)))  // 执行代码
            .aggregate(builder.groupKey(builder.field(0)))  // 聚合操作：添加聚合节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(ENAME=[$0])\n"  // 执行代码
        + "  LogicalFilter(condition=[>($1, 3)])\n"  // 执行代码
        + "    LogicalAggregate(group=[{1}], C=[COUNT()])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2946">[CALCITE-2946]
   * RelBuilder wrongly skips creation of Aggregate that prunes columns if input
   * produces one row at most</a>. */
  @Test void testAggregate5() {  // 测试方法：验证RelBuilder的Aggregate5功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(builder.groupKey(), builder.count().as("C"))  // 聚合操作：添加聚合节点到关系代数树
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field("C"), builder.literal(5)))  // 执行代码
            .project(builder.literal(4), builder.literal(2), builder.field(0))  // 投影操作：选择和计算输出字段
            .aggregate(builder.groupKey(builder.field(0), builder.field(1)))  // 聚合操作：添加聚合节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject($f0=[4], $f1=[2])\n"  // 执行代码
        + "  LogicalFilter(condition=[>($0, 5)])\n"  // 执行代码
        + "    LogicalAggregate(group=[{}], C=[COUNT()])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Unlike {@link #testAggregate5()}, where the input to the Aggregate has at  // 执行代码
   * most one row (zero or one rows), the input is known to be exactly one row,
   * and therefore can be converted to {@code Values}. */
  @Test void testAggregate5b() {  // 测试方法：验证RelBuilder的Aggregate5b功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(builder.groupKey(), builder.count().as("C"))  // 聚合操作：添加聚合节点到关系代数树
            .project(builder.literal(4), builder.literal(2), builder.field(0))  // 投影操作：选择和计算输出字段
            .aggregate(builder.groupKey(builder.field(0), builder.field(1)))  // 聚合操作：添加聚合节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalValues(tuples=[[{ 4, 2 }]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3839">[CALCITE-3839]
   * After calling RelBuilder.aggregate, cannot lookup field by name</a>. */
  @Test void testAggregateAndThenProjectNamedField() {  // 测试方法：验证RelBuilder的AggregateAndThenProjectNamedField功能
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("EMPNO"), builder.field("ENAME"),  // 投影操作：选择和计算输出字段
                builder.field("SAL"))  // 执行代码
            .aggregate(builder.groupKey(builder.field("ENAME")),  // 聚合操作：添加聚合节点到关系代数树
                builder.sum(builder.field("SAL")))  // 执行代码
            // Before [CALCITE-3839] was fixed, the following line gave
            // 'field [ENAME] not found'
            .project(builder.field("ENAME"))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(ENAME=[$0])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0}], agg#0=[SUM($1)])\n"  // 执行代码
        + "    LogicalProject(ENAME=[$1], SAL=[$5])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  /** Tests that {@link RelBuilder#aggregate} eliminates duplicate aggregate  // 执行代码
   * calls and creates a {@code Project} to compensate. */
  @Test void testAggregateEliminatesDuplicateCalls() {  // 测试方法：验证RelBuilder的AggregateEliminatesDuplicateCalls功能
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(S1=[$0], C=[$1], S2=[$2], S1b=[$0])\n"  // 执行代码
        + "  LogicalAggregate(group=[{}], S1=[SUM($1)], C=[COUNT()], S2=[SUM($2)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(  // 执行代码
        buildRelWithDuplicateAggregates(c -> c.withDedupAggregateCalls(true)),  // 执行代码
        hasTree(expected));  // 执行代码

    // Now, disable the rewrite
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{}], S1=[SUM($1)], C=[COUNT()], S2=[SUM($2)], S1b=[SUM($1)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(  // 执行代码
        buildRelWithDuplicateAggregates(c -> c.withDedupAggregateCalls(false)),  // 执行代码
        hasTree(expected2));  // 执行代码
  }

  /** As {@link #testAggregateEliminatesDuplicateCalls()} but with a  // 执行代码
   * single-column GROUP BY clause. */
  @Test void testAggregateEliminatesDuplicateCalls2() {  // 测试方法：验证RelBuilder的AggregateEliminatesDuplicateCalls2功能
    RelNode root = buildRelWithDuplicateAggregates(c -> c, 0);  // 声明根节点：最终构建的关系代数树的根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], S1=[$1], C=[$2], S2=[$3], S1b=[$1])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0}], S1=[SUM($1)], C=[COUNT()], S2=[SUM($2)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** As {@link #testAggregateEliminatesDuplicateCalls()} but with a  // 执行代码
   * multi-column GROUP BY clause. */
  @Test void testAggregateEliminatesDuplicateCalls3() {  // 测试方法：验证RelBuilder的AggregateEliminatesDuplicateCalls3功能
    RelNode root = buildRelWithDuplicateAggregates(c -> c, 2, 0, 4, 3);  // 声明根节点：最终构建的关系代数树的根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], JOB=[$1], MGR=[$2], HIREDATE=[$3], S1=[$4], C=[$5], S2=[$6], S1b=[$4])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0, 2, 3, 4}], S1=[SUM($1)], C=[COUNT()], S2=[SUM($2)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE/issues/CALCITE-6340">
   * [CALCITE-6340] RelBuilder drops traits when aggregating over duplicate projected fields</a>.
   */
  @Test void testPruneProjectInputOfAggregatePreservesConventionAndCollationsWhenEmpty() {  // 测试方法：验证RelBuilder的PruneProjectInputOfAggregatePreservesConventionAndCollationsWhenEmpty功能
    final RelBuilder builder = createBuilder(config -> config.withPruneInputOfAggregate(true));  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象

    RelNode node = builder  // 执行代码
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .sort(builder.nullsLast(builder.desc(builder.field(1))),  // 排序操作：添加排序节点到关系代数树
            builder.field(0))  // 执行代码
        .project(builder.alias(builder.field(0), "a"),  // 投影操作：选择和计算输出字段
            builder.alias(builder.field(1), "b"),  // 执行代码
            builder.alias(builder.field(0), "c"),  // 执行代码
            builder.alias(builder.field(1), "d"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet desiredTraits = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE);  // 执行代码

    final RuleSet prepareRules =  // 执行代码
        RuleSets.ofList(EnumerableRules.ENUMERABLE_PROJECT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_SORT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);  // 执行代码
    final Program program = Programs.of(prepareRules);  // 执行代码
    node =  // 执行代码
        program.run(node.getCluster().getPlanner(), node, desiredTraits, ImmutableList.of(),  // 执行代码
            ImmutableList.of());  // 执行代码

    // collations are lost as the sort is on column [1, 0], but we group on 0, convention stays
    node = builder.push(node)  // 执行代码
        .aggregate(  // 聚合操作：添加聚合节点到关系代数树
            builder.groupKey(0), builder.aggregateCall(  // 执行代码
            SqlStdOperatorTable.SUM, builder.field(0)))  // SQL操作符：使用标准SQL操作符构建表达式
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet expectedTraitSet = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE);  // 执行代码
    assertTrue(expectedTraitSet.contains(EnumerableConvention.INSTANCE));  // 执行代码

    if (Bug.CALCITE_6391_FIXED) {  // 执行代码
      assertThat(node.getInput(0).getTraitSet(), is(expectedTraitSet));  // 执行代码
    } else {  // 执行代码
      assertThat(node.getInput(0).getTraitSet().get(0), is(expectedTraitSet.get(0)));  // 执行代码
    }
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE/issues/CALCITE-6340">
   * [CALCITE-6340] RelBuilder drops traits when aggregating over duplicate projected fields</a>.
   */
  @Test void testPruneProjectInputOfAggregatePreservesConventionAndSingletonCollation() {  // 测试方法：验证RelBuilder的PruneProjectInputOfAggregatePreservesConventionAndSingletonCollation功能
    final RelBuilder builder = createBuilder(config -> config.withPruneInputOfAggregate(true));  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象

    RelNode node = builder  // 执行代码
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .sort(builder.nullsLast(builder.desc(builder.field(1))))  // 排序操作：添加排序节点到关系代数树
        .project(builder.alias(builder.field(0), "a"),  // 投影操作：选择和计算输出字段
            builder.alias(builder.field(1), "b"),  // 执行代码
            builder.alias(builder.field(0), "c"),  // 执行代码
            builder.alias(builder.field(1), "d"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet desiredTraits = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE);  // 执行代码

    final RuleSet prepareRules =  // 执行代码
        RuleSets.ofList(EnumerableRules.ENUMERABLE_PROJECT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_SORT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);  // 执行代码
    final Program program = Programs.of(prepareRules);  // 执行代码

    // turn the logical plan into a physical plan so that a convention can be set
    node =  // 执行代码
        program.run(node.getCluster().getPlanner(), node, desiredTraits, ImmutableList.of(),  // 执行代码
            ImmutableList.of());  // 执行代码


    node = builder.push(node)  // 执行代码
        .aggregate(  // 聚合操作：添加聚合节点到关系代数树
            builder.groupKey(1), builder.aggregateCall(  // 执行代码
                SqlStdOperatorTable.SUM, builder.field(1)))  // SQL操作符：使用标准SQL操作符构建表达式
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet expectedTraitSet = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE)  // 执行代码
        .replace(  // 执行代码
            RelCollations.of(  // 执行代码
                new RelFieldCollation(0,  // 执行代码
                    RelFieldCollation.Direction.DESCENDING, RelFieldCollation.NullDirection.LAST)));  // 执行代码

    if (Bug.CALCITE_6391_FIXED) {  // 执行代码
      assertThat(node.getInput(0).getTraitSet(), is(expectedTraitSet));  // 执行代码
    } else {  // 执行代码
      assertThat(node.getInput(0).getTraitSet().get(0), is(expectedTraitSet.get(0)));  // 执行代码
    }
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE/issues/CALCITE-6340">
   * [CALCITE-6340] RelBuilder drops traits when aggregating over duplicate projected fields</a>.
   */
  @Test void testPruneProjectInputOfAggregatePreservesConventionAndCompositeCollation() {  // 测试方法：验证RelBuilder的PruneProjectInputOfAggregatePreservesConventionAndCompositeCollation功能
    final RelBuilder builder = createBuilder(config -> config.withPruneInputOfAggregate(true));  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象

    RelNode node = builder  // 执行代码
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .sort(builder.nullsLast(builder.desc(builder.field(1))),  // 排序操作：添加排序节点到关系代数树
            builder.field(0))  // 执行代码
        .project(builder.alias(builder.field(0), "a"),  // 投影操作：选择和计算输出字段
            builder.alias(builder.field(1), "b"),  // 执行代码
            builder.alias(builder.field(0), "c"),  // 执行代码
            builder.alias(builder.field(1), "d"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet desiredTraits = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE);  // 执行代码

    final RuleSet prepareRules =  // 执行代码
        RuleSets.ofList(EnumerableRules.ENUMERABLE_PROJECT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_SORT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);  // 执行代码
    final Program program = Programs.of(prepareRules);  // 执行代码

    // turn the logical plan into a physical plan so that a convention can be set
    node =  // 执行代码
        program.run(node.getCluster().getPlanner(), node, desiredTraits, ImmutableList.of(),  // 执行代码
            ImmutableList.of());  // 执行代码


    node = builder.push(node)  // 执行代码
        .aggregate(  // 聚合操作：添加聚合节点到关系代数树
            builder.groupKey(1), builder.aggregateCall(  // 执行代码
                SqlStdOperatorTable.SUM, builder.field(1)))  // SQL操作符：使用标准SQL操作符构建表达式
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet expectedTraitSet = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE)  // 执行代码
        .replace(  // 执行代码
            RelCollations.of(  // 执行代码
                new RelFieldCollation(0,  // 执行代码
                    RelFieldCollation.Direction.DESCENDING, RelFieldCollation.NullDirection.LAST)));  // 执行代码

    if (Bug.CALCITE_6391_FIXED) {  // 执行代码
      assertThat(node.getInput(0).getTraitSet(), is(expectedTraitSet));  // 执行代码
    } else {  // 执行代码
      assertThat(node.getInput(0).getTraitSet().get(0), is(expectedTraitSet.get(0)));  // 执行代码
    }
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE/issues/CALCITE-6340">
   * [CALCITE-6340] RelBuilder drops traits when aggregating over duplicate projected fields</a>.
   */
  @Test void testPruneProjectInputOfAggregatePreservesConventionAndDistribution() {  // 测试方法：验证RelBuilder的PruneProjectInputOfAggregatePreservesConventionAndDistribution功能
    final RelBuilder builder = createBuilder(config -> config.withPruneInputOfAggregate(true));  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象

    RelNode node = builder  // 执行代码
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .project(builder.alias(builder.field(0), "a"),  // 投影操作：选择和计算输出字段
            builder.alias(builder.field(0), "b"),  // 执行代码
            builder.alias(builder.field(1), "c"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet desiredTraits = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE);  // 执行代码
    final RuleSet prepareRules =  // 执行代码
        RuleSets.ofList(EnumerableRules.ENUMERABLE_PROJECT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);  // 执行代码
    final Program program = Programs.of(prepareRules);  // 执行代码

    // turn the logical plan into a physical plan so that a distribution can be set
    node =  // 执行代码
        program.run(node.getCluster().getPlanner(), node, desiredTraits, ImmutableList.of(),  // 执行代码
            ImmutableList.of());  // 执行代码

    // setting the distribution drops the collations
    node = node.copy(desiredTraits.plus(RelDistributions.BROADCAST_DISTRIBUTED), node.getInputs());  // 执行代码

    node = builder.push(node)  // 执行代码
        .aggregate(  // 聚合操作：添加聚合节点到关系代数树
            builder.groupKey(0), builder.aggregateCall(  // 执行代码
            SqlStdOperatorTable.SUM, builder.field(0)))  // SQL操作符：使用标准SQL操作符构建表达式
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet expectedTraitSet = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE)  // 执行代码
        .plus(RelDistributions.BROADCAST_DISTRIBUTED);  // 执行代码

    assertThat(node.getInput(0).getTraitSet(), is(expectedTraitSet));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE/issues/CALCITE-6340">
   * [CALCITE-6340] RelBuilder drops traits when aggregating over duplicate projected fields</a>.
   */
  @Test void testPruneProjectInputOfAggregatePreservesConvention() {  // 测试方法：验证RelBuilder的PruneProjectInputOfAggregatePreservesConvention功能
    final RelBuilder builder = createBuilder(config -> config.withPruneInputOfAggregate(true));  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode node = builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .adoptConvention(EnumerableConvention.INSTANCE)  // 执行代码
        .project(builder.alias(builder.field(0), "a"),  // 投影操作：选择和计算输出字段
            builder.alias(builder.field(0), "b"))  // 执行代码
        .aggregate(  // 聚合操作：添加聚合节点到关系代数树
            builder.groupKey(0), builder.aggregateCall(  // 执行代码
            SqlStdOperatorTable.SUM, builder.field(0))).build();  // 构建操作：完成关系代数树的构建并返回根节点

    final RelTraitSet expectedTraitSet = builder.getCluster().traitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE)  // 执行代码
        .replace(RelCollations.of(new RelFieldCollation(0)));  // 执行代码

    if (Bug.CALCITE_6391_FIXED) {  // 执行代码
      assertThat(node.getInput(0).getTraitSet(), is(expectedTraitSet));  // 执行代码
    } else {  // 执行代码
      assertThat(node.getInput(0).getTraitSet().get(0), is(expectedTraitSet.get(0)));  // 执行代码
    }
  }

  private RelNode buildRelWithDuplicateAggregates(  // 私有辅助方法：构建包含重复聚合的测试用例
      UnaryOperator<RelBuilder.Config> transform,  // 执行代码
      int... groupFieldOrdinals) {  // 执行代码
    final RelBuilder builder = createBuilder(transform);  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    return builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .aggregate(builder.groupKey(groupFieldOrdinals),  // 聚合操作：添加聚合节点到关系代数树
            builder.sum(builder.field(1)).as("S1"),  // 执行代码
            builder.count().as("C"),  // 执行代码
            builder.sum(builder.field(2)).as("S2"),  // 执行代码
            builder.sum(builder.field(1)).as("S1b"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
  }

  /** Tests eliminating duplicate aggregate calls, when some of them are only  // 执行代码
   * seen to be duplicates when a spurious "DISTINCT" has been eliminated.
   *
   * <p>Note that "M2" and "MD2" are based on the same field, because
   * "MIN(DISTINCT $2)" is identical to "MIN($2)". The same is not true for
   * "SUM". */
  @Test void testAggregateEliminatesDuplicateDistinctCalls() {  // 测试方法：验证RelBuilder的AggregateEliminatesDuplicateDistinctCalls功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .aggregate(builder.groupKey(2),  // 聚合操作：添加聚合节点到关系代数树
            builder.sum(builder.field(1)).as("S1"),  // 执行代码
            builder.sum(builder.field(1)).distinct().as("SD1"),  // 执行代码
            builder.count().as("C"),  // 执行代码
            builder.min(builder.field(2)).distinct().as("MD2"),  // 执行代码
            builder.min(builder.field(2)).as("M2"),  // 执行代码
            builder.min(builder.field(2)).distinct().as("MD2b"),  // 执行代码
            builder.sum(builder.field(1)).distinct().as("S1b"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(JOB=[$0], S1=[$1], SD1=[$2], C=[$3], MD2=[$4], "  // 执行代码
        + "M2=[$4], MD2b=[$4], S1b=[$2])\n"  // 执行代码
        + "  LogicalAggregate(group=[{2}], S1=[SUM($1)], "  // 执行代码
        + "SD1=[SUM(DISTINCT $1)], C=[COUNT()], MD2=[MIN($2)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /**
   * Test reproducing issue CALCITE-6261.
   */
  @Test void testAggregateDuplicateAggCallsWithForceProjectAndFieldPruning() {  // 测试方法：验证RelBuilder的AggregateDuplicateAggCallsWithForceProjectAndFieldPruning功能
    final Function<RelBuilder, RelNode> f1 = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        // single table scan with force project of all columns
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(  // 投影操作：选择和计算输出字段
                ImmutableList.of(  // 执行代码
                    builder.field("EMPNO"),  // 执行代码
                    builder.field("ENAME"),  // 执行代码
                    builder.field("JOB"),  // 执行代码
                    builder.field("MGR"),  // 执行代码
                    builder.field("HIREDATE"),  // 执行代码
                    builder.field("SAL"),  // 执行代码
                    builder.field("COMM"),  // 执行代码
                    builder.field("DEPTNO")),  // 执行代码
                ImmutableList.of(),  // 执行代码
                true)  // 执行代码
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(builder.field("MGR")),  // 执行代码
                // duplicate avg() agg calls
                builder.avg(false, "SALARY_AVG", builder.field("SAL")),  // 执行代码
                builder.sum(false, "SALARY_SUM", builder.field("SAL")),  // 执行代码
                builder.avg(false, "SALARY_MEAN", builder.field("SAL")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(MGR=[$0], SALARY_AVG=[$1], SALARY_SUM=[$2], SALARY_MEAN=[$1])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0}], SALARY_AVG=[AVG($1)], SALARY_SUM=[SUM($1)])\n"  // 执行代码
        + "    LogicalProject(MGR=[$3], SAL=[$5])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f1.apply(createBuilder()), hasTree(expected));  // 执行代码
  }

  /**
   * Test recreating the reproducer from issue CALCITE-5888 but with the existing scott tables.
   */
  @Test void testAggregateDuplicateAggCallsAndFieldPruningWithJoinAndLiteralGroupKey() {  // 测试方法：验证RelBuilder的AggregateDuplicateAggCallsAndFieldPruningWithJoinAndLiteralGroupKey功能
    final Function<RelBuilder, RelNode> f1 = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        // first inner join two tables
        builder.scan("EMP").scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER, "DEPTNO")  // 连接操作：添加连接节点到关系代数树
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                // null group key
                builder.groupKey(builder.cast(builder.literal(null), SqlTypeName.INTEGER)),  // SQL类型名：指定SQL数据类型
                // duplicated min/max agg calls
                builder.min(builder.field("SAL")),  // 执行代码
                builder.max(builder.field("SAL")),  // 执行代码
                builder.min(builder.field("SAL")),  // 执行代码
                builder.max(builder.field("SAL")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject($f11=[$0], $f1=[$1], $f2=[$2], $f10=[$1], $f20=[$2])\n"  // 执行代码
        + "  LogicalAggregate(group=[{1}], agg#0=[MIN($0)], agg#1=[MAX($0)])\n"  // 执行代码
        + "    LogicalProject(SAL=[$5], $f11=[null:INTEGER])\n"  // 执行代码
        + "      LogicalJoin(condition=[=($7, $8)], joinType=[inner])\n"  // 执行代码
        + "        LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "        LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(f1.apply(createBuilder()), hasTree(expected));  // 执行代码
  }

  @Test void testAggregateFilter() {  // 测试方法：验证RelBuilder的AggregateFilter功能
    // Equivalent SQL:
    //   SELECT deptno, COUNT(*) FILTER (WHERE empno > 100) AS c
    //   FROM emp
    //   GROUP BY ROLLUP(deptno)
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(ImmutableBitSet.of(7),  // 执行代码
                    ImmutableList.of(ImmutableBitSet.of(7), ImmutableBitSet.of())),  // 执行代码
                builder.count()  // 执行代码
                    .filter(  // 过滤操作：添加过滤条件到关系代数树
                        builder.greaterThan(builder.field("EMPNO"),  // 执行代码
                            builder.literal(100)))  // 执行代码
                    .as("C"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{0}], groups=[[{0}, {}]], C=[COUNT() FILTER $1])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$7], $f8=[>($0, 100)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果

    // now without pruning
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{7}], groups=[[{7}, {}]], C=[COUNT() FILTER $8])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], "  // 执行代码
        + "HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[>($0, 100)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withPruneInputOfAggregate(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected2));  // 执行代码
  }

  @Test void testAggregateFilterFails() {  // 测试方法：验证RelBuilder的AggregateFilterFails功能
    // Equivalent SQL:
    //   SELECT deptno, SUM(sal) FILTER (WHERE comm) AS c
    //   FROM emp
    //   GROUP BY deptno
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
              .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                  builder.groupKey(builder.field("DEPTNO")),  // 执行代码
                  builder.sum(builder.field("SAL"))  // 执行代码
                      .filter(builder.field("COMM"))  // 过滤操作：添加过滤条件到关系代数树
                      .as("C"))  // 执行代码
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (CalciteException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("FILTER expression must be of type BOOLEAN"));  // 执行代码
    }
  }

  @Test void testAggregateFilterNullable() {  // 测试方法：验证RelBuilder的AggregateFilterNullable功能
    // Equivalent SQL:
    //   SELECT deptno, SUM(sal) FILTER (WHERE comm < 100) AS c
    //   FROM emp
    //   GROUP BY deptno
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(builder.field("DEPTNO")),  // 执行代码
                builder.sum(builder.field("SAL"))  // 执行代码
                    .filter(  // 过滤操作：添加过滤条件到关系代数树
                        builder.lessThan(builder.field("COMM"),  // 执行代码
                            builder.literal(100)))  // 执行代码
                    .as("C"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{1}], C=[SUM($0) FILTER $2])\n"  // 执行代码
        + "  LogicalProject(SAL=[$5], DEPTNO=[$7], $f8=[IS TRUE(<($6, 100))])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果

    // now without pruning
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{7}], C=[SUM($5) FILTER $8])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[IS TRUE(<($6, 100))])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withPruneInputOfAggregate(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected2));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1980">[CALCITE-1980]
   * RelBuilder gives NPE if groupKey contains alias</a>.
   *
   * <p>Now, the alias does not cause a new expression to be added to the input,
   * but causes the referenced fields to be renamed. */
  @Test void testAggregateProjectWithAliases() {  // 测试方法：验证RelBuilder的AggregateProjectWithAliases功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(  // 执行代码
                    builder.alias(builder.field("DEPTNO"), "departmentNo")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{0}])\n"  // 执行代码
        + "  LogicalProject(departmentNo=[$7])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAggregateProjectWithExpression() {  // 测试方法：验证RelBuilder的AggregateProjectWithExpression功能
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(  // 执行代码
                    builder.alias(  // 执行代码
                        builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                            builder.field("DEPTNO"), builder.literal(3)),  // 执行代码
                        "d3")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{0}])\n"  // 执行代码
        + "  LogicalProject(d3=[+($7, 3)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果

    // now without pruning
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{1}])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$7], d3=[+($7, 3)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withPruneInputOfAggregate(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected2));  // 执行代码
  }

  /** Tests that {@link RelBuilder#aggregate} on top of a {@link Project} prunes  // 执行代码
   * away expressions that are not used.
   *
   * @see RelBuilder.Config#pruneInputOfAggregate */
  @Test void testAggregateProjectPrune() {  // 测试方法：验证RelBuilder的AggregateProjectPrune功能
    // SELECT deptno, SUM(sal) FILTER (WHERE b)
    // FROM (
    //   SELECT deptno, empno + 10, sal, job = 'CLERK' AS b
    //   FROM emp)
    // GROUP BY deptno
    //   -->
    // SELECT deptno, SUM(sal) FILTER (WHERE b)
    // FROM (
    //   SELECT deptno, sal, job = 'CLERK' AS b
    //   FROM emp)
    // GROUP BY deptno
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("EMPNO"), builder.literal(10)),  // 执行代码
                builder.field("SAL"),  // 执行代码
                builder.field("JOB"))  // 执行代码
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(builder.field("DEPTNO")),  // 执行代码
                    builder.sum(builder.field("SAL"))  // 执行代码
                .filter(  // 过滤操作：添加过滤条件到关系代数树
                    builder.equals(builder.field("JOB"),  // 执行代码
                        builder.literal("CLERK"))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{0}], agg#0=[SUM($1) FILTER $2])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$7], SAL=[$5], $f4=[IS TRUE(=($2, 'CLERK'))])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码

    // now with pruning disabled
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{0}], agg#0=[SUM($2) FILTER $4])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$7], $f1=[+($0, 10)], SAL=[$5], JOB=[$2], "  // 执行代码
        + "$f4=[IS TRUE(=($2, 'CLERK'))])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withPruneInputOfAggregate(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected2));  // 执行代码
  }

  /** Tests that (a) if the input is a project and no fields are used  // 执行代码
   * we remove the project (rather than projecting zero fields, which
   * would be wrong), and (b) if the same aggregate function is used
   * twice, we add a project on top. */
  @Test void testAggregateProjectPruneEmpty() {  // 测试方法：验证RelBuilder的AggregateProjectPruneEmpty功能
    // SELECT COUNT(*) AS C, COUNT(*) AS C2 FROM (
    //  SELECT deptno, empno + 10, sal, job = 'CLERK' AS b
    //  FROM emp)
    //   -->
    // SELECT C, C AS C2 FROM (
    //   SELECT COUNT(*) AS c
    //   FROM emp)
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("EMPNO"), builder.literal(10)),  // 执行代码
                builder.field("SAL"),  // 执行代码
                builder.field("JOB"))  // 执行代码
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(),  // 执行代码
                    builder.countStar("C"),  // 执行代码
                    builder.countStar("C2"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(C=[$0], C2=[$0])\n"  // 执行代码
        + "  LogicalAggregate(group=[{}], C=[COUNT()])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果

    // now with pruning disabled
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(C=[$0], C2=[$0])\n"  // 执行代码
        + "  LogicalAggregate(group=[{}], C=[COUNT()])\n"  // 执行代码
        + "    LogicalProject(DEPTNO=[$7], $f1=[+($0, 10)], SAL=[$5], JOB=[$2])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withPruneInputOfAggregate(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected2));  // 执行代码
  }

  @Test void testAggregateGroupingKeyOutOfRangeFails() {  // 测试方法：验证RelBuilder的AggregateGroupingKeyOutOfRangeFails功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
              .aggregate(builder.groupKey(ImmutableBitSet.of(17)))  // 聚合操作：添加聚合节点到关系代数树
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("out of bounds: {17}"));  // 执行代码
    }
  }

  @Test void testAggregateGroupingSetNotSubsetFails() {  // 测试方法：验证RelBuilder的AggregateGroupingSetNotSubsetFails功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
              .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                  builder.groupKey(ImmutableBitSet.of(7),  // 执行代码
                      ImmutableList.of(ImmutableBitSet.of(4), ImmutableBitSet.of())))  // 执行代码
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("group set element [$4] must be a subset of group key"));  // 执行代码
    }
  }

  /** Tests that, if you try to create an Aggregate with duplicate grouping  // 执行代码
   * sets, RelBuilder creates a Union. Each branch of the Union has an
   * Aggregate that has distinct grouping sets. */
  @Test void testAggregateGroupingSetDuplicate() {  // 测试方法：验证RelBuilder的AggregateGroupingSetDuplicate功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(  // 聚合操作：添加聚合节点到关系代数树
                builder.groupKey(ImmutableBitSet.of(7, 6),  // 执行代码
                    ImmutableList.of(ImmutableBitSet.of(7),  // 执行代码
                            ImmutableBitSet.of(6),  // 执行代码
                            ImmutableBitSet.of(7))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalUnion(all=[true])\n"  // 执行代码
        + "  LogicalAggregate(group=[{6, 7}], groups=[[{6}, {7}]])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalAggregate(group=[{6, 7}], groups=[[{7}]])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4665">[CALCITE-4665]
   * Allow Aggregate.groupSet to contain columns not in any of the groupSets.</a>. */
  @Test void testGroupingSetWithGroupKeysContainingUnusedColumn() {  // 测试方法：验证RelBuilder的GroupingSetWithGroupKeysContainingUnusedColumn功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .aggregate(  // 聚合操作：添加聚合节点到关系代数树
            builder.groupKey(  // 执行代码
                ImmutableBitSet.of(0, 1, 2),  // 执行代码
                ImmutableList.of(ImmutableBitSet.of(0, 1), ImmutableBitSet.of(0))),  // 执行代码
            builder.count(false, "C"),  // 执行代码
            builder.sum(false, "S", builder.field("SAL")))  // 执行代码
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.call(  // 执行代码
                SqlStdOperatorTable.GREATER_THAN,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.field("C"),  // 执行代码
                builder.literal(10)))  // 执行代码
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.call(  // 执行代码
                SqlStdOperatorTable.EQUALS,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.field("JOB"),  // 执行代码
                builder.literal("DEVELOP")))  // 执行代码
        .project(builder.field("JOB")).build();  // 投影操作：选择和计算输出字段
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(JOB=[$2])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($2, 'DEVELOP')])\n"  // 执行代码
        + "    LogicalFilter(condition=[>($3, 10)])\n"  // 执行代码
        + "      LogicalAggregate(group=[{0, 1, 2}], groups=[[{0, 1}, {0}]], C=[COUNT()], S=[SUM"  // 执行代码
        + "($5)])\n"  // 执行代码
        + "        LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAggregateGrouping() {  // 测试方法：验证RelBuilder的AggregateGrouping功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(builder.groupKey(6, 7),  // 聚合操作：添加聚合节点到关系代数树
                builder.aggregateCall(SqlStdOperatorTable.GROUPING,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("DEPTNO")).as("g"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{6, 7}], g=[GROUPING($7)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAggregateGroupingWithDistinctFails() {  // 测试方法：验证RelBuilder的AggregateGroupingWithDistinctFails功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
              .aggregate(builder.groupKey(6, 7),  // 聚合操作：添加聚合节点到关系代数树
                  builder.aggregateCall(SqlStdOperatorTable.GROUPING,  // SQL操作符：使用标准SQL操作符构建表达式
                      builder.field("DEPTNO"))  // 执行代码
                      .distinct(true)  // 执行代码
                      .as("g"))  // 执行代码
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("DISTINCT not allowed"));  // 执行代码
    }
  }

  @Test void testAggregateGroupingWithFilterFails() {  // 测试方法：验证RelBuilder的AggregateGroupingWithFilterFails功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
              .aggregate(builder.groupKey(6, 7),  // 聚合操作：添加聚合节点到关系代数树
                  builder.aggregateCall(SqlStdOperatorTable.GROUPING,  // SQL操作符：使用标准SQL操作符构建表达式
                      builder.field("DEPTNO"))  // 执行代码
                      .filter(builder.literal(true))  // 过滤操作：添加过滤条件到关系代数树
                      .as("g"))  // 执行代码
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), is("FILTER not allowed"));  // 执行代码
    }
  }

  @Test void testAggregateOneRow() {  // 测试方法：验证RelBuilder的AggregateOneRow功能
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.values(new String[] {"a", "b"}, 1, 2)  // 执行代码
            .aggregate(builder.groupKey(1))  // 聚合操作：添加聚合节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String plan = "LogicalProject(b=[$1])\n"  // 执行代码
        + "  LogicalValues(tuples=[[{ 1, 2 }]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(plan));  // 断言验证：执行构建函数并验证结果

    final String plan2 = "LogicalAggregate(group=[{1}])\n"  // 执行代码
        + "  LogicalValues(tuples=[[{ 1, 2 }]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withAggregateUnique(true))),  // 断言验证：执行构建函数并验证结果
        hasTree(plan2));  // 执行代码
  }

  /** Tests that we do not convert an Aggregate to a Project if there are  // 执行代码
   * multiple group sets. */
  @Test void testAggregateGroupingSetsOneRow() {  // 测试方法：验证RelBuilder的AggregateGroupingSetsOneRow功能
    final Function<RelBuilder, RelNode> f = builder -> {  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
      final List<Integer> list01 = Arrays.asList(0, 1);  // 执行代码
      final List<Integer> list0 = Collections.singletonList(0);  // 执行代码
      final List<Integer> list1 = Collections.singletonList(1);  // 执行代码
      return builder.values(new String[] {"a", "b"}, 1, 2)  // 执行代码
          .aggregate(  // 聚合操作：添加聚合节点到关系代数树
              builder.groupKey(builder.fields(list01),  // 执行代码
                  ImmutableList.of(builder.fields(list0),  // 执行代码
                      builder.fields(list1),  // 执行代码
                      builder.fields(list01))))  // 执行代码
          .build();  // 构建操作：完成关系代数树的构建并返回根节点
    };
    final String plan = ""  // 执行代码
        + "LogicalAggregate(group=[{0, 1}], groups=[[{0, 1}, {0}, {1}]])\n"  // 执行代码
        + "  LogicalValues(tuples=[[{ 1, 2 }]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(plan));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withAggregateUnique(true))),  // 断言验证：执行构建函数并验证结果
        hasTree(plan));  // 执行代码
  }

  /** Tests creating (and expanding) a call to {@code GROUP_ID()} in a  // 执行代码
   * {@code GROUPING SETS} query. Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4199">[CALCITE-4199]
   * RelBuilder throws NullPointerException while implementing
   * GROUP_ID()</a>. */
  @Test void testAggregateGroupingSetsGroupId() {  // 测试方法：验证RelBuilder的AggregateGroupingSetsGroupId功能
    final String plan = ""  // 执行代码
        + "LogicalProject(JOB=[$0], DEPTNO=[$1], g=[0:BIGINT])\n"  // 执行代码
        + "  LogicalAggregate(group=[{2, 7}], groups=[[{2, 7}, {2}, {7}]])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(groupIdRel(createBuilder(), false), hasTree(plan));  // 执行代码
    assertThat(  // 执行代码
        groupIdRel(createBuilder(c -> c.withAggregateUnique(true)), false),  // 执行代码
        hasTree(plan));  // 执行代码

    // If any group occurs more than once, we need a UNION ALL.
    final String plan2 = ""  // 执行代码
        + "LogicalUnion(all=[true])\n"  // 执行代码
        + "  LogicalProject(JOB=[$0], DEPTNO=[$1], g=[0:BIGINT])\n"  // 执行代码
        + "    LogicalAggregate(group=[{2, 7}], groups=[[{2, 7}, {2}, {7}]])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalProject(JOB=[$0], DEPTNO=[$1], g=[1:BIGINT])\n"  // 执行代码
        + "    LogicalAggregate(group=[{2, 7}])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(groupIdRel(createBuilder(), true), hasTree(plan2));  // 执行代码
  }

  private static RelNode groupIdRel(RelBuilder builder, boolean extra) {  // 辅助方法
    final List<String> djList = Arrays.asList("DEPTNO", "JOB");  // 执行代码
    final List<String> dList = Collections.singletonList("DEPTNO");  // 执行代码
    final List<String> jList = Collections.singletonList("JOB");  // 执行代码
    return builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .aggregate(  // 聚合操作：添加聚合节点到关系代数树
            builder.groupKey(builder.fields(djList),  // 执行代码
                ImmutableList.<List<? extends RexNode>>builder()  // 执行代码
                    .add(builder.fields(dList))  // 执行代码
                    .add(builder.fields(jList))  // 执行代码
                    .add(builder.fields(djList))  // 执行代码
                    .addAll(extra ? ImmutableList.of(builder.fields(djList))  // 执行代码
                        : ImmutableList.of())  // 执行代码
                    .build()),  // 构建操作：完成关系代数树的构建并返回根节点
            builder.aggregateCall(SqlStdOperatorTable.GROUP_ID).as("g"))  // SQL操作符：使用标准SQL操作符构建表达式
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
  }

  @Test void testWithinDistinct() {  // 测试方法：验证RelBuilder的WithinDistinct功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregate(builder.groupKey(),  // 聚合操作：添加聚合节点到关系代数树
                builder.avg(builder.field("SAL"))  // 执行代码
                    .as("g"),  // 执行代码
                builder.avg(builder.field("SAL"))  // 执行代码
                    .unique(builder.field("DEPTNO"))  // 执行代码
                    .as("g2"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{}], g=[AVG($5)],"  // 执行代码
        + " g2=[AVG($5) WITHIN DISTINCT ($7)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testDistinct() {  // 测试方法：验证RelBuilder的Distinct功能
    // Equivalent SQL:
    //   SELECT DISTINCT deptno
    //   FROM emp
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .distinct()  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalAggregate(group=[{0}])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalProject(DEPTNO=[$7])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testDistinctAlready() {  // 测试方法：验证RelBuilder的DistinctAlready功能
    // DEPT is already distinct
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .distinct()  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalTableScan(table=[[scott, DEPT]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testDistinctEmpty() {  // 测试方法：验证RelBuilder的DistinctEmpty功能
    // Is a relation with zero columns distinct?
    // What about if we know there are zero rows?
    // It is a matter of definition: there are no duplicate rows,
    // but applying "select ... group by ()" to it would change the result.
    // In theory, we could omit the distinct if we know there is precisely one
    // row, but we don't currently.
    final Function<RelBuilder, RelNode> f = builder ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(SqlStdOperatorTable.IS_NULL,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("COMM")))  // 执行代码
            .project()  // 投影操作：选择和计算输出字段
            .distinct()  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalValues(tuples=[[{ true }]])\n";  // 执行代码
    final RelNode r = f.apply(createBuilder());  // 执行代码
    assertThat(r, hasTree(expected));  // 执行代码

    // Now without adding extra fields
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalValues(tuples=[[{  }]])\n";  // 执行代码
    final RelNode r2 =  // 执行代码
        f.apply(createBuilder(c -> c.withPreventEmptyFieldList(false)));  // 执行代码
    assertThat(r2, hasTree(expected2));  // 执行代码

    // Now without pruning
    // (The empty LogicalProject is dubious, but it's what we've always done)
    final String expected3 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{}])\n"  // 执行代码
        + "  LogicalProject\n"  // 执行代码
        + "    LogicalFilter(condition=[IS NULL($6)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final RelNode r3 =  // 执行代码
        f.apply(  // 执行代码
            createBuilder(c ->  // 执行代码
                c.withPruneInputOfAggregate(false)  // 执行代码
                    .withPreventEmptyFieldList(false)));  // 执行代码
    assertThat(r3, hasTree(expected3));  // 执行代码
  }

  @Test void testUnion() {  // 测试方法：验证RelBuilder的Union功能
    // Equivalent SQL:
    //   SELECT deptno FROM emp
    //   UNION ALL
    //   SELECT deptno FROM dept
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("DEPTNO"),  // 执行代码
                    builder.literal(20)))  // 执行代码
            .project(builder.field("EMPNO"))  // 投影操作：选择和计算输出字段
            .union(true)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalUnion(all=[true])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$0])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($7, 20)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1522">[CALCITE-1522]
   * Fix error message for SetOp with incompatible args</a>. */
  @Test void testBadUnionArgsErrorMessage() {  // 测试方法：验证RelBuilder的BadUnionArgsErrorMessage功能
    // Equivalent SQL:
    //   SELECT EMPNO, SAL FROM emp
    //   UNION ALL
    //   SELECT DEPTNO FROM dept
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
          builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
              .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
              .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
              .project(builder.field("EMPNO"), builder.field("SAL"))  // 投影操作：选择和计算输出字段
              .union(true)  // 执行代码
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      fail("Expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      final String expected = "Cannot compute compatible row type for "  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
          + "arguments to set op: RecordType(TINYINT DEPTNO), "  // 执行代码
          + "RecordType(SMALLINT EMPNO, DECIMAL(7, 2) SAL)";  // 执行代码
      assertThat(e.getMessage(), is(expected));  // 执行代码
    }
  }

  @Test void testUnion3() {  // 测试方法：验证RelBuilder的Union3功能
    // Equivalent SQL:
    //   SELECT deptno FROM dept
    //   UNION ALL
    //   SELECT empno FROM emp
    //   UNION ALL
    //   SELECT deptno FROM emp
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("EMPNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .union(true, 3)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalUnion(all=[true])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$0])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$7])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testUnion1() {  // 测试方法：验证RelBuilder的Union1功能
    // Equivalent SQL:
    //   SELECT deptno FROM dept
    //   UNION ALL
    //   SELECT empno FROM emp
    //   UNION ALL
    //   SELECT deptno FROM emp
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("EMPNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .union(true, 1)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalProject(DEPTNO=[$7])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testRepeatUnion1() {  // 测试方法：验证RelBuilder的RepeatUnion1功能
    // Generates the sequence 1,2,3,...10 using a repeat union. Equivalent SQL:
    //   WITH RECURSIVE delta(n) AS (
    //     VALUES (1)
    //     UNION ALL
    //     SELECT n+1 FROM delta WHERE n < 10
    //   )
    //   SELECT * FROM delta
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.values(new String[] { "i" }, 1)  // 执行代码
            .transientScan("DELTA_TABLE")  // 执行代码
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(  // 执行代码
                    SqlStdOperatorTable.LESS_THAN,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field(0),  // 执行代码
                    builder.literal(10)))  // 执行代码
            .project(  // 投影操作：选择和计算输出字段
                builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field(0),  // 执行代码
                    builder.literal(1)))  // 执行代码
            .repeatUnion("DELTA_TABLE", true)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalRepeatUnion(all=[true])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableSpool(readType=[LAZY], writeType=[LAZY], table=[[DELTA_TABLE]])\n"  // 执行代码
        + "    LogicalValues(tuples=[[{ 1 }]])\n"  // 执行代码
        + "  LogicalTableSpool(readType=[LAZY], writeType=[LAZY], table=[[DELTA_TABLE]])\n"  // 执行代码
        + "    LogicalProject($f0=[+($0, 1)])\n"  // 执行代码
        + "      LogicalFilter(condition=[<($0, 10)])\n"  // 执行代码
        + "        LogicalTableScan(table=[[DELTA_TABLE]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testRepeatUnion2() {  // 测试方法：验证RelBuilder的RepeatUnion2功能
    // Generates the factorial function from 0 to 7. Equivalent SQL:
    //   WITH RECURSIVE delta (n, fact) AS (
    //     VALUES (0, 1)
    //     UNION ALL
    //     SELECT n+1, (n+1)*fact FROM delta WHERE n < 7
    //   )
    //   SELECT * FROM delta
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.values(new String[] { "n", "fact" }, 0, 1)  // 执行代码
            .transientScan("AUX")  // 执行代码
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(  // 执行代码
                    SqlStdOperatorTable.LESS_THAN,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("n"),  // 执行代码
                    builder.literal(7)))  // 执行代码
            .project(  // 投影操作：选择和计算输出字段
                Arrays.asList(  // 执行代码
                    builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.field("n"),  // 执行代码
                        builder.literal(1)),  // 执行代码
                    builder.call(SqlStdOperatorTable.MULTIPLY,  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                            builder.field("n"),  // 执行代码
                            builder.literal(1)),  // 执行代码
                        builder.field("fact"))),  // 执行代码
                Arrays.asList("n", "fact"))  // 执行代码
            .repeatUnion("AUX", true)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalRepeatUnion(all=[true])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableSpool(readType=[LAZY], writeType=[LAZY], table=[[AUX]])\n"  // 执行代码
        + "    LogicalValues(tuples=[[{ 0, 1 }]])\n"  // 执行代码
        + "  LogicalTableSpool(readType=[LAZY], writeType=[LAZY], table=[[AUX]])\n"  // 执行代码
        + "    LogicalProject(n=[+($0, 1)], fact=[*(+($0, 1), $1)])\n"  // 执行代码
        + "      LogicalFilter(condition=[<($0, 7)])\n"  // 执行代码
        + "        LogicalTableScan(table=[[AUX]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testIntersect() {  // 测试方法：验证RelBuilder的Intersect功能
    // Equivalent SQL:
    //   SELECT empno FROM emp
    //   WHERE deptno = 20
    //   INTERSECT
    //   SELECT deptno FROM dept
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("DEPTNO"),  // 执行代码
                    builder.literal(20)))  // 执行代码
            .project(builder.field("EMPNO"))  // 投影操作：选择和计算输出字段
            .intersect(false)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalIntersect(all=[false])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$0])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($7, 20)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testIntersect3() {  // 测试方法：验证RelBuilder的Intersect3功能
    // Equivalent SQL:
    //   SELECT deptno FROM dept
    //   INTERSECT ALL
    //   SELECT empno FROM emp
    //   INTERSECT ALL
    //   SELECT deptno FROM emp
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("EMPNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .intersect(true, 3)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalIntersect(all=[true])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$0])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$7])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testExcept() {  // 测试方法：验证RelBuilder的Except功能
    // Equivalent SQL:
    //   SELECT empno FROM emp
    //   WHERE deptno = 20
    //   MINUS
    //   SELECT deptno FROM dept
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field("DEPTNO"))  // 投影操作：选择和计算输出字段
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("DEPTNO"),  // 执行代码
                    builder.literal(20)))  // 执行代码
            .project(builder.field("EMPNO"))  // 投影操作：选择和计算输出字段
            .minus(false)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalMinus(all=[false])\n"  // 执行代码
        + "  LogicalProject(DEPTNO=[$0])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "  LogicalProject(EMPNO=[$0])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($7, 20)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests building a simple join. Also checks {@link RelBuilder#size()}  // 执行代码
   * at every step. */
  @Test void testJoin() {  // 测试方法：验证RelBuilder的Join功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM (SELECT * FROM emp WHERE comm IS NULL)
    //   JOIN dept ON emp.deptno = dept.deptno
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.let(b -> assertSize(b, is(0)))  // 执行代码
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .let(b -> assertSize(b, is(1)))  // 执行代码
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(SqlStdOperatorTable.IS_NULL,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("COMM")))  // 执行代码
            .let(b -> assertSize(b, is(1)))  // 执行代码
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .let(b -> assertSize(b, is(2)))  // 执行代码
            .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
                builder.equals(builder.field(2, 0, "DEPTNO"),  // 执行代码
                    builder.field(2, 1, "DEPTNO")))  // 执行代码
            .let(b -> assertSize(b, is(1)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(builder, isRelBuilderWithSize(is(0)));  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($7, $8)], joinType=[inner])\n"  // 执行代码
        + "  LogicalFilter(condition=[IS NULL($6)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Returns a Matcher that checks {@link RelBuilder#size()}. */  // 执行代码
  private static Matcher<RelBuilder> isRelBuilderWithSize(  // 辅助方法
      final Matcher<Integer> matcher) {  // 执行代码
    return new FeatureMatcher<RelBuilder, Integer>(matcher,  // 执行代码
        "RelBuilder", "size") {  // 执行代码
      @Override protected Integer featureValueOf(RelBuilder actual) {  // 辅助方法
        return actual.size();  // 执行代码
      }
    };
  }

  private static RelBuilder assertSize(RelBuilder b,  // 静态辅助方法
      Matcher<Integer> sizeMatcher) {  // 执行代码
    assertThat(b, isRelBuilderWithSize(is(sizeMatcher)));  // 执行代码
    return b;  // 执行代码
  }

  /** Same as {@link #testJoin} using USING. */  // 执行代码
  @Test void testJoinUsing() {  // 测试方法：验证RelBuilder的JoinUsing功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root2 =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(SqlStdOperatorTable.IS_NULL,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("COMM")))  // 执行代码
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER, "DEPTNO")  // 连接操作：添加连接节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($7, $8)], joinType=[inner])\n"  // 执行代码
        + "  LogicalFilter(condition=[IS NULL($6)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root2, hasTree(expected));  // 执行代码
  }

  @Test void testJoin2() {  // 测试方法：验证RelBuilder的Join2功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   LEFT JOIN dept ON emp.deptno = dept.deptno
    //     AND emp.empno = 123
    //     AND dept.deptno IS NOT NULL
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.LEFT,  // 连接操作：添加连接节点到关系代数树
                builder.equals(builder.field(2, 0, "DEPTNO"),  // 执行代码
                    builder.field(2, 1, "DEPTNO")),  // 执行代码
                builder.equals(builder.field(2, 0, "EMPNO"),  // 执行代码
                    builder.literal(123)),  // 执行代码
                builder.isNotNull(builder.field(2, 1, "DEPTNO")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Note that "dept.deptno IS NOT NULL" has been simplified away.
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[AND(=($7, $8), =($0, 123))], joinType=[left])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests that simplification is run in  // 执行代码
   * {@link org.apache.calcite.rex.RexUnknownAs#FALSE} mode for join
   * conditions. */
  @Test void testJoinConditionSimplification() {  // 测试方法：验证RelBuilder的JoinConditionSimplification功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
                b.or(b.literal(null),  // 执行代码
                    b.and(b.equals(b.field(2, 0, "DEPTNO"), b.literal(1)),  // 执行代码
                        b.equals(b.field(2, 0, "DEPTNO"), b.literal(2)),  // 执行代码
                        b.equals(b.field(2, 1, "DEPTNO"),  // 执行代码
                            b.field(2, 0, "DEPTNO")))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[false], joinType=[inner])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    final String expectedWithoutSimplify = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[OR(null:NULL, "  // 执行代码
        + "AND(=($7, 1), =($7, 2), =($8, $7)))], joinType=[inner])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withSimplify(true))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withSimplify(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedWithoutSimplify));  // 执行代码
  }

  @Test void testJoinPushCondition() {  // 测试方法：验证RelBuilder的JoinPushCondition功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
                b.equals(  // 执行代码
                    b.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                        b.field(2, 0, "DEPTNO"),  // 执行代码
                        b.field(2, 0, "EMPNO")),  // 执行代码
                    b.field(2, 1, "DEPTNO")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // SELECT * FROM EMP AS e JOIN DEPT AS d ON e.DEPTNO + e.EMPNO = d.DEPTNO
    //  becomes
    // SELECT * FROM (SELECT *, EMPNO + DEPTNO AS x FROM EMP) AS e
    // JOIN DEPT AS d ON e.x = d.DEPTNO
    final String expectedWithoutPush = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=(+($7, $0), $8)], joinType=[inner])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], "  // 执行代码
        + "HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], DEPTNO0=[$9], "  // 执行代码
        + "DNAME=[$10], LOC=[$11])\n"  // 执行代码
        + "  LogicalJoin(condition=[=($8, $9)], joinType=[inner])\n"  // 执行代码
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], "  // 执行代码
        + "HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+($7, $0)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expectedWithoutPush));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withPushJoinCondition(true))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withPushJoinCondition(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedWithoutPush));  // 执行代码
  }

  @Test void testJoinCartesian() {  // 测试方法：验证RelBuilder的JoinCartesian功能
    // Equivalent SQL:
    //   SELECT * emp CROSS JOIN dept
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER)  // 连接操作：添加连接节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalJoin(condition=[true], joinType=[inner])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testCorrelationFails() {  // 测试方法：验证RelBuilder的CorrelationFails功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
          .variable(v::set)  // 执行代码
          .filter(builder.equals(builder.field(0), v.get()))  // 过滤操作：添加过滤条件到关系代数树
          .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
          .join(JoinRelType.INNER, builder.literal(true),  // 连接操作：添加连接节点到关系代数树
              ImmutableSet.of(v.get().id));  // 执行代码
      fail("expected error");  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          containsString("variable $cor0 must not be used by left input to correlation"));  // 执行代码
    }
  }

  @Test void testCorrelationWithCondition() {  // 测试方法：验证RelBuilder的CorrelationWithCondition功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .variable(v::set)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.equals(builder.field(0),  // 执行代码
                builder.field(v.get(), "DEPTNO")))  // 执行代码
        .join(JoinRelType.LEFT,  // 连接操作：添加连接节点到关系代数树
            builder.equals(builder.field(2, 0, "SAL"),  // 执行代码
                builder.literal(1000)),  // 执行代码
            ImmutableSet.of(v.get().id))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Note that the join filter gets pushed to the right-hand input of
    // LogicalCorrelate
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{5, 7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.SAL, 1000)])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($0, $cor0.DEPTNO)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testTrivialCorrelation() {  // 测试方法：验证RelBuilder的TrivialCorrelation功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .variable(v::set)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .join(JoinRelType.LEFT,  // 连接操作：添加连接节点到关系代数树
            builder.equals(builder.field(2, 0, "SAL"),  // 执行代码
                builder.literal(1000)),  // 执行代码
            ImmutableSet.of(v.get().id))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Note that the join is emitted since the query is not actually a correlated.
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($5, 1000)], joinType=[left], variablesSet=[[$cor0]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAntiJoin() {  // 测试方法：验证RelBuilder的AntiJoin功能
    // Equivalent SQL:
    //   SELECT * FROM dept d
    //   WHERE NOT EXISTS (SELECT 1 FROM emp e WHERE e.deptno = d.deptno)
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root = builder  // 声明根节点：最终构建的关系代数树的根节点
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .antiJoin(  // 执行代码
            builder.equals(  // 执行代码
                builder.field(2, 0, "DEPTNO"),  // 执行代码
                builder.field(2, 1, "DEPTNO")))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($0, $10)], joinType=[anti])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testInQuery() {  // 测试方法：验证RelBuilder的InQuery功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno IN (
    //     SELECT deptno
    //     FROM dept
    //     WHERE dname = 'Accounting')
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.in(b.field("DEPTNO"),  // 执行代码
                    b2 ->  // 执行代码
                        b2.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
                            .filter(  // 过滤操作：添加过滤条件到关系代数树
                                b2.equals(b2.field("DNAME"),  // 执行代码
                                    b2.literal("Accounting")))  // 执行代码
                            .project(b2.field("DEPTNO"))  // 投影操作：选择和计算输出字段
                            .build()))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalFilter(condition=[IN($7, {\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$0])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($1, 'Accounting')])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "})])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testExists() {  // 测试方法：验证RelBuilder的Exists功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE EXISTS (
    //     SELECT null
    //     FROM dept
    //     WHERE dname = 'Accounting')
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.exists(b2 ->  // 执行代码
                    b2.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
                        .filter(  // 过滤操作：添加过滤条件到关系代数树
                            b2.equals(b2.field("DNAME"),  // 执行代码
                                b2.literal("Accounting")))  // 执行代码
                        .build()))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalFilter(condition=[EXISTS({\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[=($1, 'Accounting')])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "})])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testExistsCorrelated() {  // 测试方法：验证RelBuilder的ExistsCorrelated功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE EXISTS (
    //     SELECT null
    //     FROM dept
    //     WHERE deptno = emp.deptno)
    final Function<RelBuilder, RelNode> f = b -> {  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
      final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
      return b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
          .variable(v::set)  // 执行代码
          .filter(ImmutableList.of(v.get().id),  // 过滤操作：添加过滤条件到关系代数树
              b.exists(b2 ->  // 执行代码
                  b2.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
                      .filter(  // 过滤操作：添加过滤条件到关系代数树
                          b2.equals(b2.field("DEPTNO"),  // 执行代码
                              b2.field(v.get(), "DEPTNO")))  // 执行代码
                      .build()))  // 构建操作：完成关系代数树的构建并返回根节点
          .build();  // 构建操作：完成关系代数树的构建并返回根节点
    };

    final String expected = "LogicalFilter(condition=[EXISTS({\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[=($0, $cor0.DEPTNO)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n"  // 执行代码
        + "})], variablesSet=[[$cor0]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testSomeAll() {  // 测试方法：验证RelBuilder的SomeAll功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE sal > SOME (SELECT comm FROM emp)
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.some(b.field("SAL"),  // 执行代码
                    SqlStdOperatorTable.GREATER_THAN,  // SQL操作符：使用标准SQL操作符构建表达式
                    b2 ->  // 执行代码
                        b2.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                            .project(b2.field("COMM"))  // 投影操作：选择和计算输出字段
                        .build()))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE NOT (sal <= ALL (SELECT comm FROM emp))
    final Function<RelBuilder, RelNode> f2 = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.not(  // 执行代码
                    b.all(b.field("SAL"),  // 执行代码
                        SqlStdOperatorTable.LESS_THAN_OR_EQUAL,  // SQL操作符：使用标准SQL操作符构建表达式
                        b2 ->  // 执行代码
                            b2.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                                .project(b2.field("COMM"))  // 投影操作：选择和计算输出字段
                                .build())))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalFilter(condition=[> SOME($5, {\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(COMM=[$6])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "})])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f2.apply(createBuilder()), hasTree(expected));  // 执行代码
  }

  @Test void testUnique() {  // 测试方法：验证RelBuilder的Unique功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM dept
    //   WHERE UNIQUE (SELECT deptno FROM emp WHERE job = 'MANAGER')
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.unique(b2 ->  // 执行代码
                    b2.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                        .filter(  // 过滤操作：添加过滤条件到关系代数树
                            b2.equals(b2.field("JOB"),  // 执行代码
                                b2.literal("MANAGER")))  // 执行代码
                        .build()))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalFilter(condition=[UNIQUE({\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[=($2, 'MANAGER')])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "})])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testScalarQuery() {  // 测试方法：验证RelBuilder的ScalarQuery功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE sal > (
    //     SELECT AVG(sal)
    //     FROM emp)
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.greaterThan(b.field("SAL"),  // 执行代码
                    b.scalarQuery(b2 ->  // 执行代码
                        b2.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                            .aggregate(b2.groupKey(),  // 聚合操作：添加聚合节点到关系代数树
                                b2.avg(b2.field("SAL")))  // 执行代码
                            .build())))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalFilter(condition=[>($5, $SCALAR_QUERY({\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{}], agg#0=[AVG($5)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "}))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testArrayQuery() {  // 测试方法：验证RelBuilder的ArrayQuery功能
    // Equivalent SQL:
    //   SELECT deptno, ARRAY (SELECT * FROM Emp)
    //   FROM Dept AS d
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(  // 投影操作：选择和计算输出字段
                b.field("DEPTNO"),  // 执行代码
                b.arrayQuery(b2 ->  // 执行代码
                        b2.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                            .build()))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalProject(DEPTNO=[$0], $f1=[ARRAY({\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "})])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testMultisetQuery() {  // 测试方法：验证RelBuilder的MultisetQuery功能
    // Equivalent SQL:
    //   SELECT deptno, MULTISET (SELECT * FROM Emp)
    //   FROM Dept AS d
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(  // 投影操作：选择和计算输出字段
                b.field("DEPTNO"),  // 执行代码
                b.multisetQuery(b2 ->  // 执行代码
                        b2.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                            .build()))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalProject(DEPTNO=[$0], $f1=[MULTISET({\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "})])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testMapQuery() {  // 测试方法：验证RelBuilder的MapQuery功能
    // Equivalent SQL:
    //   SELECT deptno, MAP (SELECT empno, job FROM Emp)
    //   FROM Dept AS d
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(  // 投影操作：选择和计算输出字段
                b.field("DEPTNO"),  // 执行代码
                b.mapQuery(b2 ->  // 执行代码
                        b2.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                            .project(b2.field("EMPNO"), b2.field("JOB"))  // 投影操作：选择和计算输出字段
                            .build()))  // 构建操作：完成关系代数树的构建并返回根节点
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = "LogicalProject(DEPTNO=[$0], $f1=[MAP({\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], JOB=[$2])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "})])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testAlias() {  // 测试方法：验证RelBuilder的Alias功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp AS e, dept
    //   WHERE e.deptno = dept.deptno
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.LEFT)  // 连接操作：添加连接节点到关系代数树
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("e", "DEPTNO"),  // 执行代码
                    builder.field("DEPT", "DEPTNO")))  // 执行代码
            .project(builder.field("e", "ENAME"),  // 投影操作：选择和计算输出字段
                builder.field("DEPT", "DNAME"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalProject(ENAME=[$1], DNAME=[$9])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalFilter(condition=[=($7, $8)])\n"  // 执行代码
        + "    LogicalJoin(condition=[true], joinType=[left])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    final RelDataTypeField field = root.getRowType().getFieldList().get(1);  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
    assertThat(field.getName(), is("DNAME"));  // 执行代码
    assertThat(field.getType().isNullable(), is(true));  // 执行代码
  }

  @Test void testAlias2() {  // 测试方法：验证RelBuilder的Alias2功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp AS e, emp as m, dept
    //   WHERE e.deptno = dept.deptno
    //   AND m.empno = e.mgr
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("m")  // 执行代码
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER)  // 连接操作：添加连接节点到关系代数树
            .join(JoinRelType.INNER)  // 连接操作：添加连接节点到关系代数树
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("e", "DEPTNO"),  // 执行代码
                    builder.field("DEPT", "DEPTNO")),  // 执行代码
                builder.equals(builder.field("m", "EMPNO"),  // 执行代码
                    builder.field("e", "MGR")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[AND(=($7, $16), =($8, $3))])\n"  // 执行代码
        + "  LogicalJoin(condition=[true], joinType=[inner])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalJoin(condition=[true], joinType=[inner])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAliasSort() {  // 测试方法：验证RelBuilder的AliasSort功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .sort(0)  // 排序操作：添加排序节点到关系代数树
            .project(builder.field("e", "EMPNO"))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0])\n"  // 执行代码
        + "  LogicalSort(sort0=[$0], dir0=[ASC])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAliasLimit() {  // 测试方法：验证RelBuilder的AliasLimit功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .sort(1)  // 排序操作：添加排序节点到关系代数树
            .sortLimit(10, 20) // aliases were lost here if preceded by sort()  // 执行代码
            .project(builder.field("e", "EMPNO"))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0])\n"  // 执行代码
        + "  LogicalSort(sort0=[$1], dir0=[ASC], offset=[10], fetch=[20])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1551">[CALCITE-1551]
   * RelBuilder's project() doesn't preserve alias</a>. */
  @Test void testAliasProject() {  // 测试方法：验证RelBuilder的AliasProject功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("EMP_alias")  // 执行代码
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.literal(20))  // 执行代码
            .project(builder.field("EMP_alias", "DEPTNO"))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$7])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests that table aliases are propagated even when there is a project on  // 执行代码
   * top of a project. (Aliases tend to get lost when projects are merged). */
  @Test void testAliasProjectProject() {  // 测试方法：验证RelBuilder的AliasProjectProject功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("EMP_alias")  // 执行代码
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.literal(20))  // 执行代码
            .project(builder.field(1),  // 投影操作：选择和计算输出字段
                builder.literal(10),  // 执行代码
                builder.field(0))  // 执行代码
            .project(builder.alias(builder.field(1), "sum"),  // 投影操作：选择和计算输出字段
                builder.field("EMP_alias", "DEPTNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(sum=[10], DEPTNO=[$7])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests that table aliases are propagated and are available to a filter,  // 执行代码
   * even when there is a project on top of a project. (Aliases tend to get lost
   * when projects are merged). */
  @Test void testAliasFilter() {  // 测试方法：验证RelBuilder的AliasFilter功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("EMP_alias")  // 执行代码
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.literal(20))  // 执行代码
            .project(builder.field(1), // literal 20  // 投影操作：选择和计算输出字段
                builder.literal(10),  // 执行代码
                builder.field(0)) // DEPTNO  // 执行代码
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field(1),  // 执行代码
                    builder.field("EMP_alias", "DEPTNO")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[>($1, $2)])\n"  // 执行代码
        + "  LogicalProject($f1=[20], $f2=[10], DEPTNO=[$7])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests that the {@link RelBuilder#alias(RexNode, String)} function is  // 执行代码
   * idempotent. */
  @Test void testScanAlias() {  // 测试方法：验证RelBuilder的ScanAlias功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    builder.scan("EMP");  // 扫描表操作：将表数据源添加到关系代数树中

    // Simplify "emp.deptno as d as d" to "emp.deptno as d".
    final RexNode e0 =  // RexNode：声明行表达式节点，代表关系表达式
        builder.alias(builder.alias(builder.field("DEPTNO"), "D"), "D");  // 执行代码
    assertThat(e0, hasToString("AS($7, 'D')"));  // 执行代码

    // It would be nice if RelBuilder could simplify
    // "emp.deptno as deptno" to "emp.deptno", but there is not
    // enough information in RexInputRef.
    final RexNode e1 = builder.alias(builder.field("DEPTNO"), "DEPTNO");  // RexNode：声明行表达式节点，代表关系表达式
    assertThat(e1, hasToString("AS($7, 'DEPTNO')"));  // 执行代码

    // The intervening alias 'DEPTNO' is removed
    final RexNode e2 =  // RexNode：声明行表达式节点，代表关系表达式
        builder.alias(builder.alias(builder.field("DEPTNO"), "DEPTNO"), "D1");  // 执行代码
    assertThat(e2, hasToString("AS($7, 'D1')"));  // 执行代码

    // Simplify "emp.deptno as d2 as d3" to "emp.deptno as d3"
    // because "d3" alias overrides "d2".
    final RexNode e3 =  // RexNode：声明行表达式节点，代表关系表达式
        builder.alias(builder.alias(builder.field("DEPTNO"), "D2"), "D3");  // 执行代码
    assertThat(e3, hasToString("AS($7, 'D3')"));  // 执行代码

    final RelNode root = builder.project(e0, e1, e2, e3).build();  // 声明根节点：最终构建的关系代数树的根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(D=[$7], DEPTNO=[$7], D1=[$7], D3=[$7])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /**
   * Tests that project field name aliases are suggested incrementally.
   */
  @Test void testAliasSuggester() {  // 测试方法：验证RelBuilder的AliasSuggester功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .project(builder.field(0),  // 投影操作：选择和计算输出字段
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0),  // 执行代码
            builder.field(0))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], EMPNO0=[$0], EMPNO1=[$0], "  // 执行代码
        + "EMPNO2=[$0], EMPNO3=[$0], EMPNO4=[$0], EMPNO5=[$0], "  // 执行代码
        + "EMPNO6=[$0], EMPNO7=[$0], EMPNO8=[$0], EMPNO9=[$0], EMPNO10=[$0])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testAliasAggregate() {  // 测试方法：验证RelBuilder的AliasAggregate功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("EMP_alias")  // 执行代码
            .project(builder.field("DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.literal(20))  // 执行代码
            .aggregate(builder.groupKey(builder.field("EMP_alias", "DEPTNO")),  // 聚合操作：添加聚合节点到关系代数树
                builder.sum(builder.field(1)))  // 执行代码
            .project(builder.alias(builder.field(1), "sum"),  // 投影操作：选择和计算输出字段
                builder.field("EMP_alias", "DEPTNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(sum=[$1], DEPTNO=[$0])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0}], agg#0=[SUM($1)])\n"  // 执行代码
        + "    LogicalProject(DEPTNO=[$7], $f1=[20])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5802">[CALCITE-5802]
   * In RelBuilder, add method aggregateRex, to allow aggregating complex
   * expressions such as "1 + SUM(x + 2)"</a>. */
  @Test void testAggregateRex() {  // 测试方法：验证RelBuilder的AggregateRex功能
    // SELECT deptno,
    //   deptno + 2 AS d2,
    //   3 + SUM(4 + sal) AS s
    // FROM emp
    // GROUP BY deptno
    BiFunction<RelBuilder, Boolean, RelNode> f = (b, projectKey) ->  // 执行代码
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregateRex(b.groupKey(b.field("DEPTNO")), projectKey,  // 执行代码
                ImmutableList.of(  // 执行代码
                    b.alias(  // 执行代码
                        b.call(SqlStdOperatorTable.PLUS, b.field("DEPTNO"),  // SQL操作符：使用标准SQL操作符构建表达式
                            b.literal(2)),  // 执行代码
                        "d2"),  // 执行代码
                    b.alias(  // 执行代码
                        b.call(SqlStdOperatorTable.PLUS, b.literal(3),  // SQL操作符：使用标准SQL操作符构建表达式
                            b.call(SqlStdOperatorTable.SUM,  // SQL操作符：使用标准SQL操作符构建表达式
                                b.call(SqlStdOperatorTable.PLUS, b.literal(4),  // SQL操作符：使用标准SQL操作符构建表达式
                                    b.field("SAL")))),  // 执行代码
                        "s")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(d2=[+($0, 2)], s=[+(3, $1)])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0}], agg#0=[SUM($1)])\n"  // 执行代码
        + "    LogicalProject(DEPTNO=[$7], $f8=[+(4, $5)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final String expectedRowType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(INTEGER d2, DECIMAL(19, 2) s) NOT NULL";  // 执行代码
    final RelNode r = f.apply(createBuilder(), false);  // 执行代码
    assertThat(r, hasTree(expected));  // 执行代码
    assertThat(r.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码

    // As above, with projectKey = true
    final String expected2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$0], d2=[+($0, 2)], s=[+(3, $1)])\n"  // 执行代码
        + "  LogicalAggregate(group=[{0}], agg#0=[SUM($1)])\n"  // 执行代码
        + "    LogicalProject(DEPTNO=[$7], $f8=[+(4, $5)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final String expectedRowType2 =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(TINYINT DEPTNO, INTEGER d2, DECIMAL(19, 2) s) NOT NULL";  // 执行代码
    final RelNode r2 = f.apply(createBuilder(), true);  // 执行代码
    assertThat(r2, hasTree(expected2));  // 执行代码
    assertThat(r2.getRowType().getFullTypeString(), is(expectedRowType2));  // 执行代码
  }

  /** Tests {@link RelBuilder#aggregateRex} with an expression;  // 执行代码
   * it needs to be evaluated post aggregation. */
  @Test void testAggregateRex2() {  // 测试方法：验证RelBuilder的AggregateRex2功能
    // SELECT CURRENT_DATE AS d
    // FROM emp
    // GROUP BY ()
    BiFunction<RelBuilder, Boolean, RelNode> f = (b, projectKey) ->  // 执行代码
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregateRex(b.groupKey(), projectKey,  // 执行代码
                ImmutableList.of(  // 执行代码
                    b.alias(b.call(SqlStdOperatorTable.CURRENT_DATE), "d")))  // SQL操作符：使用标准SQL操作符构建表达式
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(d=[CURRENT_DATE])\n"  // 执行代码
        + "  LogicalValues(tuples=[[{ true }]])\n";  // 执行代码
    final String expectedRowType = "RecordType(DATE NOT NULL d) NOT NULL";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    final RelNode r = f.apply(createBuilder(), false);  // 执行代码
    assertThat(r, hasTree(expected));  // 执行代码
    assertThat(r.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码

    // As above, with projectKey = true
    final RelNode r2 = f.apply(createBuilder(), true);  // 执行代码
    assertThat(r2, hasTree(expected));  // 执行代码
    assertThat(r2.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码

    // As above, disabling extra fields
    final String expected3 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(d=[CURRENT_DATE])\n"  // 执行代码
        + "  LogicalValues(tuples=[[{  }]])\n";  // 执行代码
    final RelNode r3 =  // 执行代码
        f.apply(createBuilder(c -> c.withPreventEmptyFieldList(false)),  // 执行代码
            false);  // 执行代码
    assertThat(r3, hasTree(expected3));  // 执行代码
    assertThat(r3.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码
  }

  /** Tests {@link RelBuilder#aggregateRex} with a literal expression;  // 执行代码
   * it needs to be evaluated post aggregation. */
  @Test void testAggregateRex3() {  // 测试方法：验证RelBuilder的AggregateRex3功能
    // SELECT 2 AS two, false AS f
    // FROM emp
    // GROUP BY ()
    BiFunction<RelBuilder, Boolean, RelNode> f = (b, projectKey) ->  // 执行代码
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregateRex(b.groupKey(), projectKey,  // 执行代码
                ImmutableList.of(b.alias(b.literal(2), "two"),  // 执行代码
                    b.alias(b.literal(false), "f")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ 2, false }]])\n";  // 执行代码
    final String expectedRowType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(INTEGER NOT NULL two, BOOLEAN NOT NULL f) NOT NULL";  // 执行代码
    final RelNode r = f.apply(createBuilder(), false);  // 执行代码
    assertThat(r, hasTree(expected));  // 执行代码
    assertThat(r.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码

    // As above, with projectKey = true
    final RelNode r2 = f.apply(createBuilder(), true);  // 执行代码
    assertThat(r2, hasTree(expected));  // 执行代码
    assertThat(r2.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码

    // As above, disabling extra fields
    final RelNode r3 =  // 执行代码
        f.apply(createBuilder(c -> c.withPreventEmptyFieldList(false)),  // 执行代码
            false);  // 执行代码
    assertThat(r3, hasTree(expected));  // 执行代码
    assertThat(r3.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码
  }

  /** Tests {@link RelBuilder#aggregateRex} with an aggregate call that needs to  // 执行代码
   * become nullable because of "GROUP BY ()". */
  @Test void testAggregateRex4() {  // 测试方法：验证RelBuilder的AggregateRex4功能
    // SELECT SUM(sal) AS s, COUNT(sal) AS c
    // FROM emp
    // GROUP BY ()
    Function<RelBuilder, RelNode> f = b ->  // 执行代码
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .aggregateRex(b.groupKey(),  // 执行代码
                b.alias(b.call(SqlStdOperatorTable.SUM, b.field("EMPNO")), "s"),  // SQL操作符：使用标准SQL操作符构建表达式
                b.alias(b.call(SqlStdOperatorTable.COUNT, b.field("SAL")), "c"))  // SQL操作符：使用标准SQL操作符构建表达式
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalAggregate(group=[{}], s=[SUM($0)], c=[COUNT($5)])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    // s is nullable because "GROUP BY ()" may have a group that contains 0 rows
    final String expectedRowType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(SMALLINT s, BIGINT NOT NULL c) NOT NULL";  // 执行代码
    final RelNode r = f.apply(createBuilder());  // 执行代码
    assertThat(r, hasTree(expected));  // 执行代码
    assertThat(r.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码
  }

  /** Tests that a projection retains field names after a join. */  // 执行代码
  @Test void testProjectJoin() {  // 测试方法：验证RelBuilder的ProjectJoin功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER)  // 连接操作：添加连接节点到关系代数树
            .project(builder.field("DEPT", "DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.field(0),  // 执行代码
                builder.field("e", "MGR"))  // 执行代码
            // essentially a no-op, was previously throwing exception due to
            // project() using join-renamed fields
            .project(builder.field("DEPT", "DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.field(1),  // 执行代码
                builder.field("e", "MGR"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$8], EMPNO=[$0], MGR=[$3])\n"  // 执行代码
        + "  LogicalJoin(condition=[true], joinType=[inner])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests that a projection after a projection. */  // 执行代码
  @Test void testProjectProject() {  // 测试方法：验证RelBuilder的ProjectProject功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .projectPlus(  // 执行代码
                builder.alias(  // 执行代码
                    builder.call(SqlStdOperatorTable.PLUS, builder.field(0),  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.field(3)), "x"))  // 执行代码
            .project(builder.field("e", "DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.field(0),  // 执行代码
                builder.field("e", "MGR"),  // 执行代码
                Util.last(builder.fields()))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$7], EMPNO=[$0], MGR=[$3], x=[+($0, $3)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3462">[CALCITE-3462]
   * Add projectExcept method in RelBuilder for projecting out expressions</a>. */
  @Test void testProjectExceptWithOrdinal() {  // 测试方法：验证RelBuilder的ProjectExceptWithOrdinal功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .projectExcept(  // 执行代码
                builder.field(2),  // 执行代码
                builder.field(3))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3462">[CALCITE-3462]
   * Add projectExcept method in RelBuilder for projecting out expressions</a>. */
  @Test void testProjectExceptWithName() {  // 测试方法：验证RelBuilder的ProjectExceptWithName功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .projectExcept(  // 执行代码
                builder.field("MGR"),  // 执行代码
                builder.field("JOB"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3462">[CALCITE-3462]
   * Add projectExcept method in RelBuilder for projecting out expressions</a>. */
  @Test void testProjectExceptWithExplicitAliasAndName() {  // 测试方法：验证RelBuilder的ProjectExceptWithExplicitAliasAndName功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .projectExcept(  // 执行代码
                builder.field("e", "MGR"),  // 执行代码
                builder.field("e", "JOB"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3462">[CALCITE-3462]
   * Add projectExcept method in RelBuilder for projecting out expressions</a>. */
  @Test void testProjectExceptWithImplicitAliasAndName() {  // 测试方法：验证RelBuilder的ProjectExceptWithImplicitAliasAndName功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .projectExcept(  // 执行代码
                builder.field("EMP", "MGR"),  // 执行代码
                builder.field("EMP", "JOB"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3462">[CALCITE-3462]
   * Add projectExcept method in RelBuilder for projecting out expressions</a>. */
  @Test void testProjectExceptWithDuplicateField() {  // 测试方法：验证RelBuilder的ProjectExceptWithDuplicateField功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    IllegalArgumentException ex =  // 执行代码
        assertThrows(IllegalArgumentException.class, () ->  // 异常断言：验证是否抛出预期的异常
            builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                .projectExcept(builder.field("EMP", "MGR"),  // 执行代码
                    builder.field("EMP", "MGR")),  // 执行代码
            "Project should fail since we are trying to remove the same field "  // 执行代码
                + "two times.");  // 执行代码
    assertThat(ex.getMessage(), containsString("Input list contains duplicates."));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3462">[CALCITE-3462]
   * Add projectExcept method in RelBuilder for projecting out expressions</a>. */
  @Test void testProjectExceptWithMissingField() {  // 测试方法：验证RelBuilder的ProjectExceptWithMissingField功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    builder.scan("EMP");  // 扫描表操作：将表数据源添加到关系代数树中
    RexNode deptnoField = builder.field("DEPTNO");  // RexNode：声明行表达式节点，代表关系表达式
    IllegalArgumentException ex =  // 执行代码
        assertThrows(IllegalArgumentException.class, () ->  // 异常断言：验证是否抛出预期的异常
            builder.project(builder.field("EMPNO"), builder.field("ENAME"))  // 投影操作：选择和计算输出字段
                .projectExcept(deptnoField),  // 执行代码
            "Project should fail since we are trying to remove a field that "  // 执行代码
                + "does not exist.");  // 执行代码
    assertThat(ex.getMessage(), allOf(containsString("Expression"), containsString("not found")));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5083">[CALCITE-5083]
   * In RelBuilder.project_, do not unwrap SARGs</a>. */
  @Test void testProjectWithSarg() {  // 测试方法：验证RelBuilder的ProjectWithSarg功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(  // 投影操作：选择和计算输出字段
                builder.between(  // 执行代码
                    builder.field("DEPTNO"),  // 执行代码
                    builder.literal(20),  // 执行代码
                    builder.literal(30)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalProject($f0=[SEARCH($7, Sarg[[20..30]])])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4409">[CALCITE-4409]
   * Improve exception when RelBuilder tries to create a field on a non-struct expression</a>. */
  @Test void testFieldOnNonStructExpression() {  // 测试方法：验证RelBuilder的FieldOnNonStructExpression功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    IllegalStateException ex =  // 执行代码
        assertThrows(IllegalStateException.class, () ->  // 异常断言：验证是否抛出预期的异常
            builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
                .project(builder.field(builder.field("EMPNO"), "abc"))  // 投影操作：选择和计算输出字段
                .build(),  // 构建操作：完成关系代数树的构建并返回根节点
            "Field should fail since we are trying access a field on "  // 执行代码
                + "expression with non-struct type");  // 执行代码
    assertThat(ex.getMessage(),  // 执行代码
        is("Trying to access field abc in a type with no fields: SMALLINT"));  // 执行代码
  }

  @Test void testMultiLevelAlias() {  // 测试方法：验证RelBuilder的MultiLevelAlias功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e")  // 执行代码
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("m")  // 执行代码
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER)  // 连接操作：添加连接节点到关系代数树
            .join(JoinRelType.INNER)  // 连接操作：添加连接节点到关系代数树
            .project(builder.field("DEPT", "DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.field(16),  // 执行代码
                builder.field("m", "EMPNO"),  // 执行代码
                builder.field("e", "MGR"))  // 执行代码
            .as("all")  // 执行代码
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field("DEPT", "DEPTNO"),  // 执行代码
                    builder.literal(100)))  // 执行代码
            .project(builder.field("DEPT", "DEPTNO"),  // 投影操作：选择和计算输出字段
                builder.field("all", "EMPNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$0], EMPNO=[$2])\n"  // 执行代码
        + "  LogicalFilter(condition=[>($0, 100)])\n"  // 执行代码
        + "    LogicalProject(DEPTNO=[$16], DEPTNO0=[$16], EMPNO=[$8], MGR=[$3])\n"  // 执行代码
        + "      LogicalJoin(condition=[true], joinType=[inner])\n"  // 执行代码
        + "        LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "        LogicalJoin(condition=[true], joinType=[inner])\n"  // 执行代码
        + "          LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "          LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testUnionAlias() {  // 测试方法：验证RelBuilder的UnionAlias功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e1")  // 执行代码
            .project(builder.field("EMPNO"),  // 投影操作：选择和计算输出字段
                builder.call(SqlStdOperatorTable.CONCAT,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("ENAME"),  // 执行代码
                    builder.literal("-1")))  // 执行代码
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .as("e2")  // 执行代码
            .project(builder.field("EMPNO"),  // 投影操作：选择和计算输出字段
                builder.call(SqlStdOperatorTable.CONCAT,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("ENAME"),  // 执行代码
                    builder.literal("-2")))  // 执行代码
            .union(false) // aliases lost here  // 执行代码
            .project(builder.fields(Lists.newArrayList(1, 0)))  // 投影操作：选择和计算输出字段
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject($f1=[$1], EMPNO=[$0])\n"  // 执行代码
        + "  LogicalUnion(all=[false])\n"  // 执行代码
        + "    LogicalProject(EMPNO=[$0], $f1=[||($1, '-1')])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalProject(EMPNO=[$0], $f1=[||($1, '-2')])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1523">[CALCITE-1523]
   * Add RelBuilder field() method to reference aliased relations not on top of
   * stack</a>, accessing tables aliased that are not accessible in the top
   * RelNode. */
  @Test void testAliasPastTop() {  // 测试方法：验证RelBuilder的AliasPastTop功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   LEFT JOIN dept ON emp.deptno = dept.deptno
    //     AND emp.empno = 123
    //     AND dept.deptno IS NOT NULL
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.LEFT,  // 连接操作：添加连接节点到关系代数树
                builder.equals(builder.field(2, "EMP", "DEPTNO"),  // 执行代码
                    builder.field(2, "DEPT", "DEPTNO")),  // 执行代码
                builder.equals(builder.field(2, "EMP", "EMPNO"),  // 执行代码
                    builder.literal(123)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[AND(=($7, $8), =($0, 123))], joinType=[left])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** As {@link #testAliasPastTop()}. */  // 执行代码
  @Test void testAliasPastTop2() {  // 测试方法：验证RelBuilder的AliasPastTop2功能
    // Equivalent SQL:
    //   SELECT t1.EMPNO, t2.EMPNO, t3.DEPTNO
    //   FROM emp t1
    //   INNER JOIN emp t2 ON t1.EMPNO = t2.EMPNO
    //   INNER JOIN dept t3 ON t1.DEPTNO = t3.DEPTNO
    //     AND t2.JOB != t3.LOC
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP").as("t1")  // 扫描表操作：将表数据源添加到关系代数树中
            .scan("EMP").as("t2")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
                builder.equals(builder.field(2, "t1", "EMPNO"),  // 执行代码
                    builder.field(2, "t2", "EMPNO")))  // 执行代码
            .scan("DEPT").as("t3")  // 扫描表操作：将表数据源添加到关系代数树中
            .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
                builder.equals(builder.field(2, "t1", "DEPTNO"),  // 执行代码
                    builder.field(2, "t3", "DEPTNO")),  // 执行代码
                builder.not(  // 执行代码
                    builder.equals(builder.field(2, "t2", "JOB"),  // 执行代码
                        builder.field(2, "t3", "LOC"))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Cols:
    // 0-7   EMP as t1
    // 8-15  EMP as t2
    // 16-18 DEPT as t3
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[AND(=($7, $16), <>($10, $18))], joinType=[inner])\n"  // 执行代码
        + "  LogicalJoin(condition=[=($0, $8)], joinType=[inner])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testEmpty() {  // 测试方法：验证RelBuilder的Empty功能
    // Equivalent SQL:
    //   SELECT deptno, true FROM dept LIMIT 0
    // optimized to
    //   VALUES
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .project(builder.field(0), builder.literal(false))  // 投影操作：选择和计算输出字段
            .empty()  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    final String expectedType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(TINYINT NOT NULL DEPTNO, BOOLEAN NOT NULL $f1) NOT NULL";  // 执行代码
    assertThat(root.getRowType().getFullTypeString(), is(expectedType));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3172">[CALCITE-3172]
   * RelBuilder#empty does not keep aliases</a>. */
  @Test void testEmptyWithAlias() {  // 测试方法：验证RelBuilder的EmptyWithAlias功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalProject(DEPTNO=[$0], DNAME=[$1])\n  LogicalValues(tuples=[[]])\n";  // 执行代码
    final String expectedType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(TINYINT NOT NULL DEPTNO, VARCHAR(14) DNAME) NOT NULL";  // 执行代码

    // Scan + Empty + Project (without alias)
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .empty()  // 执行代码
            .project(  // 投影操作：选择和计算输出字段
                builder.field("DEPTNO"),  // 执行代码
                builder.field("DNAME"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    assertThat(root.getRowType().getFullTypeString(), is(expectedType));  // 执行代码

    // Scan + Empty + Project (with alias)
    root =  // 执行代码
        builder.scan("DEPT").as("d")  // 扫描表操作：将表数据源添加到关系代数树中
            .empty()  // 执行代码
            .project(  // 投影操作：选择和计算输出字段
                builder.field(1, "d", "DEPTNO"),  // 执行代码
                builder.field(1, "d", "DNAME"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    assertThat(root.getRowType().getFullTypeString(), is(expectedType));  // 执行代码

    // Scan + Filter false (implicitly converted into Empty) + Project (with alias)
    root =  // 执行代码
        builder.scan("DEPT").as("d")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(builder.literal(false))  // 过滤操作：添加过滤条件到关系代数树
            .project(  // 投影操作：选择和计算输出字段
                builder.field(1, "d", "DEPTNO"),  // 执行代码
                builder.field(1, "d", "DNAME"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    assertThat(root.getRowType().getFullTypeString(), is(expectedType));  // 执行代码
  }

  @Test void testValues() {  // 测试方法：验证RelBuilder的Values功能
    // Equivalent SQL:
    //   VALUES (true, 1), (false, -50) AS t(a, b)
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.values(new String[]{"a", "b"}, true, 1, false, -50)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ true, 1 }, { false, -50 }]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    final String expectedType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(BOOLEAN NOT NULL a, INTEGER NOT NULL b) NOT NULL";  // 执行代码
    assertThat(root.getRowType().getFullTypeString(), is(expectedType));  // 执行代码
  }

  /** Tests creating Values with some field names and some values null. */  // 执行代码
  @Test void testValuesNullable() {  // 测试方法：验证RelBuilder的ValuesNullable功能
    // Equivalent SQL:
    //   VALUES (null, 1, 'abc'), (false, null, 'longer string')
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.values(new String[]{"a", null, "c"},  // 执行代码
            null, 1, "abc",  // 执行代码
            false, null, "longer string").build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ null, 1, 'abc' }, { false, null, 'longer string' }]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    final String expectedType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(BOOLEAN a, INTEGER EXPR$1, CHAR(13) NOT NULL c) NOT NULL";  // 执行代码
    assertThat(root.getRowType().getFullTypeString(), is(expectedType));  // 执行代码
  }

  @Test void testValuesBadNullFieldNames() {  // 测试方法：验证RelBuilder的ValuesBadNullFieldNames功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    assertThrows(NullPointerException.class,  // 异常断言：验证是否抛出预期的异常
        () -> builder.values((String[]) null, "a", "b"),  // 执行代码
        "fieldNames");  // 执行代码

    final String[] f1 = {"x"};  // 执行代码
    assertThat(builder.values(f1, "a", "b", "c", "d"), notNullValue());  // 执行代码

    final String[] f2 = {"x", "y"};  // 执行代码
    assertThat(builder.values(f2, "a", "b", "c", "d"), notNullValue());  // 执行代码

    final String[] f3 = {"x", "y", "z"};  // 执行代码
    assertThrows(IllegalArgumentException.class,  // 异常断言：验证是否抛出预期的异常
        () -> builder.values(f3, "a", "b", "c", "d"),  // 执行代码
        "Value count must be a positive multiple of field count");  // 执行代码
  }

  @Test void testValuesBadNoFields() {  // 测试方法：验证RelBuilder的ValuesBadNoFields功能
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      RelBuilder root = builder.values(new String[0], 1, 2, 3);  // 执行代码
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("Value count must be a positive multiple of field count"));  // 执行代码
    }
  }

  @Test void testValuesBadNoValues() {  // 测试方法：验证RelBuilder的ValuesBadNoValues功能
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      RelBuilder root = builder.values(new String[]{"a", "b"});  // 执行代码
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("Value count must be a positive multiple of field count"));  // 执行代码
    }
  }

  @Test void testValuesBadOddMultiple() {  // 测试方法：验证RelBuilder的ValuesBadOddMultiple功能
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      RelBuilder root = builder.values(new String[] {"a", "b"}, 1, 2, 3, 4, 5);  // 执行代码
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("Value count must be a positive multiple of field count"));  // 执行代码
    }
  }

  @Test void testValuesBadAllNull() {  // 测试方法：验证RelBuilder的ValuesBadAllNull功能
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      RelBuilder root =  // 执行代码
          builder.values(new String[] {"a", "b"}, null, null, 1, null);  // 执行代码
      fail("expected error, got " + root);  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(),  // 执行代码
          is("All values of field 'b' (field index 1) are null; cannot deduce type"));  // 执行代码
    }
  }

  @Test void testValuesAllNull() {  // 测试方法：验证RelBuilder的ValuesAllNull功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelDataType rowType =  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
        builder.getTypeFactory().builder()  // 执行代码
            .add("a", SqlTypeName.BIGINT)  // SQL类型名：指定SQL数据类型
            .add("a", SqlTypeName.VARCHAR, 10)  // SQL类型名：指定SQL数据类型
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.values(rowType, null, null, 1, null).build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ null, null }, { 1, null }]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    final String expectedType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(BIGINT NOT NULL a, VARCHAR(10) NOT NULL a) NOT NULL";  // 执行代码
    assertThat(root.getRowType().getFullTypeString(), is(expectedType));  // 执行代码
  }

  @Test void testValuesRename() {  // 测试方法：验证RelBuilder的ValuesRename功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.values(new String[] {"a", "b"}, 1, true, 2, false)  // 执行代码
            .rename(Arrays.asList("x", "y"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ 1, true }, { 2, false }]])\n";  // 执行代码
    final String expectedRowType =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "RecordType(INTEGER NOT NULL x, BOOLEAN NOT NULL y) NOT NULL";  // 执行代码
    final RelNode r = f.apply(createBuilder());  // 执行代码
    assertThat(r, hasTree(expected));  // 执行代码
    assertThat(r.getRowType().getFullTypeString(), is(expectedRowType));  // 执行代码
  }

  /** Tests that {@code Union(Project(Values), ... Project(Values))} is  // 执行代码
   * simplified to {@code Values}. It occurs commonly: people write
   * {@code SELECT 1 UNION SELECT 2}. */
  @Test void testUnionProjectValues() {  // 测试方法：验证RelBuilder的UnionProjectValues功能
    // Equivalent SQL:
    //   SELECT 'a', 1
    //   UNION ALL
    //   SELECT 'b', 2
    final BiFunction<RelBuilder, Boolean, RelNode> f = (b, all) ->  // 执行代码
        b.values(new String[] {"zero"}, 0)  // 执行代码
            .project(b.literal("a"), b.literal(1))  // 投影操作：选择和计算输出字段
            .values(new String[] {"zero"}, 0)  // 执行代码
            .project(b.literal("b"), b.literal(2))  // 投影操作：选择和计算输出字段
            .union(all, 2)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ 'a', 1 }, { 'b', 2 }]])\n";  // 执行代码

    // Same effect with and without ALL because tuples are distinct
    assertThat(f.apply(createBuilder(), true), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(), false), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testUnionProjectValues2() {  // 测试方法：验证RelBuilder的UnionProjectValues2功能
    // Equivalent SQL:
    //   SELECT 'a', 1 FROM (VALUES (0), (0))
    //   UNION ALL
    //   SELECT 'b', 2
    final BiFunction<RelBuilder, Boolean, RelNode> f = (b, all) ->  // 执行代码
        b.values(new String[] {"zero"}, 0)  // 执行代码
            .project(b.literal("a"), b.literal(1))  // 投影操作：选择和计算输出字段
            .values(new String[] {"zero"}, 0, 0)  // 执行代码
            .project(b.literal("b"), b.literal(2))  // 投影操作：选择和计算输出字段
            .union(all, 2)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    // Different effect with and without ALL because tuples are not distinct.
    final String expectedAll =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ 'a', 1 }, { 'b', 2 }, { 'b', 2 }]])\n";  // 执行代码
    final String expectedDistinct =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalValues(tuples=[[{ 'a', 1 }, { 'b', 2 }]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(), true), hasTree(expectedAll));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(), false), hasTree(expectedDistinct));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testSort() {  // 测试方法：验证RelBuilder的Sort功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY 3. 1 DESC
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.field(2), builder.desc(builder.field(0)))  // 排序操作：添加排序节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSort(sort0=[$2], sort1=[$0], dir0=[ASC], dir1=[DESC])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    assertThat(((Sort) root).getSortExps(), hasToString("[$2, $0]"));  // 执行代码

    // same result using ordinals
    final RelNode root2 =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(2, -1)  // 排序操作：添加排序节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root2, hasTree(expected));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1015">[CALCITE-1015]
   * OFFSET 0 causes AssertionError</a>. */
  @Test void testTrivialSort() {  // 测试方法：验证RelBuilder的TrivialSort功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   OFFSET 0
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sortLimit(0, -1, ImmutableList.of())  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalTableScan(table=[[scott, EMP]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSortDuplicate() {  // 测试方法：验证RelBuilder的SortDuplicate功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY empno DESC, deptno, empno ASC, hiredate
    //
    // The sort key "empno ASC" is unnecessary and is ignored.
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.desc(builder.field("EMPNO")),  // 排序操作：添加排序节点到关系代数树
                builder.field("DEPTNO"),  // 执行代码
                builder.field("EMPNO"),  // 执行代码
                builder.field("HIREDATE"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalSort(sort0=[$0], sort1=[$7], sort2=[$4], "  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "dir0=[DESC], dir1=[ASC], dir2=[ASC])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSortByExpression() {  // 测试方法：验证RelBuilder的SortByExpression功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY ename ASC NULLS LAST, hiredate + mgr DESC NULLS FIRST
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.nullsLast(builder.desc(builder.field(1))),  // 排序操作：添加排序节点到关系代数树
                builder.nullsFirst(  // 执行代码
                    builder.call(SqlStdOperatorTable.PLUS, builder.field(4),  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.field(3))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7])\n"  // 执行代码
            + "  LogicalSort(sort0=[$1], sort1=[$8], dir0=[DESC-nulls-last], dir1=[ASC-nulls-first])\n"  // 执行代码
            + "    LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+($4, $3)])\n"  // 执行代码
            + "      LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6817">[CALCITE-6817]  // 执行代码
   * Add string representation of default nulls direction for RelNode</a>. */
  @Test void testDescWithDefaultNullDirection() {  // 测试方法：验证RelBuilder的DescWithDefaultNullDirection功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY empno DESC
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.desc(builder.field(0)))  // 排序操作：添加排序节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSort(sort0=[$0], dir0=[DESC])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final String expectedExpanded =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSort(sort0=[$0], dir0=[DESC-nulls-first])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    assertThat(root, hasExpandedTree(expectedExpanded));  // 断言验证：验证构建的RelNode是否符合预期

    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY empno DESC NULLS FIRST
    final RelNode root2 =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.nullsFirst(builder.desc(builder.field(0))))  // 排序操作：添加排序节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root2, hasTree(expected));  // 执行代码
    assertThat(root2, hasExpandedTree(expectedExpanded));  // 执行代码
  }

  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6817">[CALCITE-6817]  // 执行代码
   * Add string representation of default nulls direction for RelNode</a>. */
  @Test void testAscWithDefaultNullDirection() {  // 测试方法：验证RelBuilder的AscWithDefaultNullDirection功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY empno ASC
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.field(0))  // 排序操作：添加排序节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSort(sort0=[$0], dir0=[ASC])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final String expectedExpanded =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSort(sort0=[$0], dir0=[ASC-nulls-last])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
    assertThat(root, hasExpandedTree(expectedExpanded));  // 断言验证：验证构建的RelNode是否符合预期

    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY empno ASC NULLS LAST
    final RelNode root2 =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.nullsLast(builder.field(0)))  // 排序操作：添加排序节点到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root2, hasTree(expected));  // 执行代码
    assertThat(root2, hasExpandedTree(expectedExpanded));  // 执行代码
  }

  @Test void testLimit() {  // 测试方法：验证RelBuilder的Limit功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   OFFSET 2 FETCH 10
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .limit(2, 10)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSort(offset=[2], fetch=[10])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSortLimit() {  // 测试方法：验证RelBuilder的SortLimit功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY deptno DESC FETCH 10
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sortLimit(-1, 10, builder.desc(builder.field("DEPTNO")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSort(sort0=[$7], dir0=[DESC], fetch=[10])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSortLimit0() {  // 测试方法：验证RelBuilder的SortLimit0功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY deptno DESC FETCH 0
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sortLimit(-1, 0, b.desc(b.field("DEPTNO")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalValues(tuples=[[]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    final String expectedNoSimplify = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalSort(sort0=[$7], dir0=[DESC], fetch=[0])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withSimplifyLimit(true))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
    assertThat(f.apply(createBuilder(c -> c.withSimplifyLimit(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedNoSimplify));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6128">[CALCITE-6128]
   * RelBuilder.sortLimit should compose offset and fetch</a>. */
  @Test void testSortOffsetLimit() {  // 测试方法：验证RelBuilder的SortOffsetLimit功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY deptno OFFSET 2 LIMIT 3

    // Case 1. Set sort+offset, then set fetch.
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sortLimit(2, -1, b.field("DEPTNO")) // ORDER BY deptno OFFSET 2  // 执行代码
            .limit(-1, 3) // LIMIT 3  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalSort(sort0=[$7], dir0=[ASC], offset=[2], fetch=[3])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withSimplifyLimit(true))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码

    // Case 2. Set sort, then offset, then fetch. Same effect as case 1.
    final Function<RelBuilder, RelNode> f2 = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(b.field("DEPTNO")) // ORDER BY deptno  // 排序操作：添加排序节点到关系代数树
            .limit(2, -1) // OFFSET 2  // 执行代码
            .limit(-1, 3) // LIMIT 3  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(f2.apply(createBuilder()), hasTree(expected));  // 执行代码
    assertThat(f2.apply(createBuilder(c -> c.withSimplifyLimit(true))),  // 执行代码
        hasTree(expected));  // 执行代码

    // Case 3. Set sort, then fetch, then offset. Same effect as case 1 & 2.
    final Function<RelBuilder, RelNode> f3 = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(b.field("DEPTNO")) // ORDER BY deptno  // 排序操作：添加排序节点到关系代数树
            .limit(-1, 3) // LIMIT 3  // 执行代码
            .limit(2, -1) // OFFSET 2  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(f3.apply(createBuilder()), hasTree(expected));  // 执行代码
    assertThat(f3.apply(createBuilder(c -> c.withSimplifyLimit(true))),  // 执行代码
        hasTree(expected));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1610">[CALCITE-1610]
   * RelBuilder sort-combining optimization treats aliases incorrectly</a>. */
  @Test void testSortOverProjectSort() {  // 测试方法：验证RelBuilder的SortOverProjectSort功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .sort(0)  // 排序操作：添加排序节点到关系代数树
        .project(builder.field(1))  // 投影操作：选择和计算输出字段
        // was throwing exception here when attempting to apply to
        // inner sort node
        .limit(0, 1)  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .sort(0)  // 排序操作：添加排序节点到关系代数树
        .project(Lists.newArrayList(builder.field(1)),  // 投影操作：选择和计算输出字段
            Lists.newArrayList("F1"))  // 执行代码
        .limit(0, 1)  // 执行代码
        // make sure we can still access the field by alias
        .project(builder.field("F1"))  // 投影操作：选择和计算输出字段
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    String expected = "LogicalProject(F1=[$1])\n"  // 执行代码
        + "  LogicalSort(sort0=[$0], dir0=[ASC], fetch=[1])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests that a sort on a field followed by a limit gives the same  // 执行代码
   * effect as calling sortLimit.
   *
   * <p>In general a relational operator cannot rely on the order of its input,
   * but it is reasonable to merge sort and limit if they were created by
   * consecutive builder operations. And clients such as Piglet rely on it. */
  @Test void testSortThenLimit() {  // 测试方法：验证RelBuilder的SortThenLimit功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(builder.desc(builder.field("DEPTNO")))  // 排序操作：添加排序节点到关系代数树
            .limit(-1, 10)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalSort(sort0=[$7], dir0=[DESC], fetch=[10])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    final RelNode root2 =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sortLimit(-1, 10, builder.desc(builder.field("DEPTNO")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root2, hasTree(expected));  // 执行代码
  }

  /** Tests that a sort on an expression followed by a limit gives the same  // 执行代码
   * effect as calling sortLimit. */
  @Test void testSortExpThenLimit() {  // 测试方法：验证RelBuilder的SortExpThenLimit功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(  // 排序操作：添加排序节点到关系代数树
                builder.desc(  // 执行代码
                    builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.field("DEPTNO"), builder.literal(1))))  // 执行代码
            .limit(3, 10)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$0], DNAME=[$1], LOC=[$2])\n"  // 执行代码
        + "  LogicalSort(sort0=[$3], dir0=[DESC], offset=[3], fetch=[10])\n"  // 执行代码
        + "    LogicalProject(DEPTNO=[$0], DNAME=[$1], LOC=[$2], $f3=[+($0, 1)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期

    final RelNode root2 =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
            .sortLimit(3, 10,  // 执行代码
                builder.desc(  // 执行代码
                    builder.call(SqlStdOperatorTable.PLUS,  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.field("DEPTNO"), builder.literal(1))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root2, hasTree(expected));  // 执行代码
  }

  /** Tests {@link org.apache.calcite.tools.RelRunner} for a VALUES query. */  // 执行代码
  @Test void testRunValues() throws Exception {  // 测试方法：验证RelBuilder的RunValues功能
    // Equivalent SQL:
    //   VALUES (true, 1), (false, -50) AS t(a, b)
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.values(new String[]{"a", "b"}, true, 1, false, -50)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    try (PreparedStatement preparedStatement = RelRunners.run(root)) {  // 执行代码
      String s = CalciteAssert.toString(preparedStatement.executeQuery());  // 执行代码
      final String result = "a=true; b=1\n"  // 执行代码
          + "a=false; b=-50\n";  // 执行代码
      assertThat(s, is(result));  // 执行代码
    }
  }

  /** Tests {@link org.apache.calcite.tools.RelRunner} for a table scan + filter  // 执行代码
   * query. */
  @Test void testRun() throws Exception {  // 测试方法：验证RelBuilder的Run功能
    // Equivalent SQL:
    //   SELECT * FROM EMP WHERE DEPTNO = 20
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.equals(builder.field("DEPTNO"), builder.literal(20)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点

    // Note that because the table has been resolved in the RelNode tree
    // we do not need to supply a "schema" as context to the runner.
    try (PreparedStatement preparedStatement = RelRunners.run(root)) {  // 执行代码
      String s = CalciteAssert.toString(preparedStatement.executeQuery());  // 执行代码
      final String result = ""  // 执行代码
          + "EMPNO=7369; ENAME=SMITH; JOB=CLERK; MGR=7902; HIREDATE=1980-12-17; SAL=800.00; COMM=null; DEPTNO=20\n"  // 执行代码
          + "EMPNO=7566; ENAME=JONES; JOB=MANAGER; MGR=7839; HIREDATE=1981-02-04; SAL=2975.00; COMM=null; DEPTNO=20\n"  // 执行代码
          + "EMPNO=7788; ENAME=SCOTT; JOB=ANALYST; MGR=7566; HIREDATE=1987-04-19; SAL=3000.00; COMM=null; DEPTNO=20\n"  // 执行代码
          + "EMPNO=7876; ENAME=ADAMS; JOB=CLERK; MGR=7788; HIREDATE=1987-05-23; SAL=1100.00; COMM=null; DEPTNO=20\n"  // 执行代码
          + "EMPNO=7902; ENAME=FORD; JOB=ANALYST; MGR=7566; HIREDATE=1981-12-03; SAL=3000.00; COMM=null; DEPTNO=20\n";  // 执行代码
      assertThat(s, is(result));  // 执行代码
    }
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1595">[CALCITE-1595]
   * RelBuilder.call throws NullPointerException if argument types are
   * invalid</a>. */
  @Test void testTypeInferenceValidation() {  // 测试方法：验证RelBuilder的TypeInferenceValidation功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    // test for a) call(operator, Iterable<RexNode>)
    final RexNode arg0 = builder.literal(0);  // RexNode：声明行表达式节点，代表关系表达式
    final RexNode arg1 = builder.literal("xyz");  // RexNode：声明行表达式节点，代表关系表达式
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      builder.call(SqlStdOperatorTable.PLUS, Lists.newArrayList(arg0, arg1));  // SQL操作符：使用标准SQL操作符构建表达式
      fail("Invalid combination of parameter types");  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), containsString("Cannot infer return type"));  // 执行代码
    }

    // test for b) call(operator, RexNode...)
    try {  // 开始异常捕获：尝试执行可能抛出异常的代码
      builder.call(SqlStdOperatorTable.PLUS, arg0, arg1);  // SQL操作符：使用标准SQL操作符构建表达式
      fail("Invalid combination of parameter types");  // 失败标记：如果执行到此处说明测试失败
    } catch (IllegalArgumentException e) {  // 捕获异常：处理预期的异常情况
      assertThat(e.getMessage(), containsString("Cannot infer return type"));  // 执行代码
    }
  }

  @Test void testPivot() {  // 测试方法：验证RelBuilder的Pivot功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM (SELECT mgr, deptno, job, sal FROM emp)
    //   PIVOT (SUM(sal) AS ss, COUNT(*) AS c
    //       FOR (job, deptno)
    //       IN (('CLERK', 10) AS c10, ('MANAGER', 20) AS m20))
    //
    // translates to
    //   SELECT mgr,
    //     SUM(sal) FILTER (WHERE job = 'CLERK' AND deptno = 10) AS c10_ss,
    //     COUNT(*) FILTER (WHERE job = 'CLERK' AND deptno = 10) AS c10_c,
    //     SUM(sal) FILTER (WHERE job = 'MANAGER' AND deptno = 20) AS m20_ss,
    //     COUNT(*) FILTER (WHERE job = 'MANAGER' AND deptno = 20) AS m20_c
    //   FROM emp
    //   GROUP BY mgr
    //
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .pivot(b.groupKey("MGR"),  // 执行代码
                Arrays.asList(  // 执行代码
                    b.sum(b.field("SAL")).as("SS"),  // 执行代码
                    b.count().as("C")),  // 执行代码
                b.fields(Arrays.asList("JOB", "DEPTNO")),  // 执行代码
                ImmutableMap.<String, List<RexNode>>builder()  // 执行代码
                    .put("C10",  // 执行代码
                        Arrays.asList(b.literal("CLERK"), b.literal(10)))  // 执行代码
                    .put("M20",  // 执行代码
                        Arrays.asList(b.literal("MANAGER"), b.literal(20)))  // 执行代码
                    .build()  // 构建操作：完成关系代数树的构建并返回根节点
                    .entrySet())  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalAggregate(group=[{0}], C10_SS=[SUM($1) FILTER $2], "  // 执行代码
        + "C10_C=[COUNT() FILTER $2], M20_SS=[SUM($1) FILTER $3], "  // 执行代码
        + "M20_C=[COUNT() FILTER $3])\n"  // 执行代码
        + "  LogicalProject(MGR=[$3], SAL=[$5], "  // 执行代码
        + "$f8=[IS TRUE(AND(=($2, 'CLERK'), =($7, 10)))], "  // 执行代码
        + "$f9=[IS TRUE(AND(=($2, 'MANAGER'), =($7, 20)))])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testUnpivot() {  // 测试方法：验证RelBuilder的Unpivot功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM (SELECT deptno, job, sal, comm FROM emp)
    //   UNPIVOT INCLUDE NULLS (remuneration
    //     FOR remuneration_type IN (comm AS 'commission',
    //                               sal AS 'salary'))
    //
    // translates to
    //   SELECT e.deptno, e.job,
    //     CASE t.remuneration_type
    //     WHEN 'commission' THEN comm
    //     ELSE sal
    //     END AS remuneration
    //   FROM emp
    //   CROSS JOIN VALUES ('commission', 'salary') AS t (remuneration_type)
    //
    final BiFunction<RelBuilder, Boolean, RelNode> f = (b, includeNulls) ->  // 执行代码
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .unpivot(includeNulls, ImmutableList.of("REMUNERATION"),  // 执行代码
                ImmutableList.of("REMUNERATION_TYPE"),  // 执行代码
                Pair.zip(  // 执行代码
                    Arrays.asList(ImmutableList.of(b.literal("commission")),  // 执行代码
                        ImmutableList.of(b.literal("salary"))),  // 执行代码
                    Arrays.asList(ImmutableList.of(b.field("COMM")),  // 执行代码
                        ImmutableList.of(b.field("SAL")))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expectedIncludeNulls = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], "  // 执行代码
        + "HIREDATE=[$4], DEPTNO=[$7], REMUNERATION_TYPE=[$8], "  // 执行代码
        + "REMUNERATION=[CASE(=($8, 'commission'), $6, =($8, 'salary'), $5, "  // 执行代码
        + "null:NULL)])\n"  // 执行代码
        + "  LogicalJoin(condition=[true], joinType=[inner])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalValues(tuples=[[{ 'commission' }, { 'salary' }]])\n";  // 执行代码
    final String expectedExcludeNulls = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], "  // 执行代码
        + "HIREDATE=[$4], DEPTNO=[$5], REMUNERATION_TYPE=[$6], "  // 执行代码
        + "REMUNERATION=[CAST($7):DECIMAL(7, 2) NOT NULL])\n"  // 执行代码
        + "  LogicalFilter(condition=[IS NOT NULL($7)])\n"  // 执行代码
        + "    " + expectedIncludeNulls.replace("\n  ", "\n      ");  // 执行代码
    assertThat(f.apply(createBuilder(), true), hasTree(expectedIncludeNulls));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(), false), hasTree(expectedExcludeNulls));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testSample() {  // 测试方法：验证RelBuilder的Sample功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   TABLESAMPLE SYSTEM(40)
    final Function<RelBuilder, RelNode> f =  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b -> b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sample(false, new BigDecimal("0.4"), null)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "Sample(mode=[system], rate=[0.4], repeatableSeed=[-])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testSampleBernoulliRepeatable() {  // 测试方法：验证RelBuilder的SampleBernoulliRepeatable功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   TABLESAMPLE BERNOULLI(25, 31415926)
    final Function<RelBuilder, RelNode> f =  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b -> b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sample(true, new BigDecimal("0.25"), 31_415_926)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "Sample(mode=[bernoulli], rate=[0.25], repeatableSeed=[31415926])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  /** Tests that TABLESAMPLE(0) returns zero rows. */  // 执行代码
  @Test void testSampleZero() {  // 测试方法：验证RelBuilder的SampleZero功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   TABLESAMPLE SYSTEM(0)
    final BiFunction<RelBuilder, Boolean, RelNode> f =  // 执行代码
        (b, mode) -> b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sample(mode, BigDecimal.ZERO, null)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalValues(tuples=[[]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(f.apply(createBuilder(), true), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(), false), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  /** Tests that TABLESAMPLE(100) (rate=1.0) does no sampling. */  // 执行代码
  @Test void testSampleAll() {  // 测试方法：验证RelBuilder的SampleAll功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   TABLESAMPLE SYSTEM(100)
    // becomes
    //   SELECT *
    //   FROM emp
    final BiFunction<RelBuilder, Boolean, RelNode> f =  // 执行代码
        (b, mode) -> b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sample(mode, BigDecimal.ONE, null)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder(), true), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(), false), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  @Test void testMatchRecognize() {  // 测试方法：验证RelBuilder的MatchRecognize功能
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   MATCH_RECOGNIZE (
    //     PARTITION BY deptno
    //     ORDER BY empno asc
    //     MEASURES
    //       STRT.mgr as start_nw,
    //       LAST(DOWN.mgr) as bottom_nw,
    //     PATTERN (STRT DOWN+ UP+) WITHIN INTERVAL '5' SECOND
    //     DEFINE
    //       DOWN as DOWN.mgr < PREV(DOWN.mgr),
    //       UP as UP.mgr > PREV(UP.mgr)
    //   )
    final RelBuilder builder = RelBuilder.create(config().build()).scan("EMP");  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelDataTypeFactory typeFactory = builder.getTypeFactory();  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
    final RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER);  // RelDataType：声明关系数据类型，描述表或表达式的类型信息

    RexNode pattern =  // RexNode：声明行表达式节点，代表关系表达式
        builder.patternConcat(builder.literal("STRT"),  // 执行代码
            builder.patternQuantify(builder.literal("DOWN"), builder.literal(1),  // 执行代码
                builder.literal(-1), builder.literal(false)),  // 执行代码
            builder.patternQuantify(builder.literal("UP"), builder.literal(1),  // 执行代码
                builder.literal(-1), builder.literal(false)));  // 执行代码

    ImmutableMap.Builder<String, RexNode> pdBuilder = new ImmutableMap.Builder<>();  // RexNode：声明行表达式节点，代表关系表达式
    RexNode downDefinition =  // RexNode：声明行表达式节点，代表关系表达式
        builder.lessThan(  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("DOWN", intType, 3),  // 执行代码
                builder.literal(0)),  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("DOWN", intType, 3),  // 执行代码
                builder.literal(1)));  // 执行代码
    pdBuilder.put("DOWN", downDefinition);  // 执行代码
    RexNode upDefinition =  // RexNode：声明行表达式节点，代表关系表达式
        builder.greaterThan(  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("UP", intType, 3),  // 执行代码
                builder.literal(0)),  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("UP", intType, 3),  // 执行代码
                builder.literal(1)));  // 执行代码
    pdBuilder.put("UP", upDefinition);  // 执行代码

    ImmutableList.Builder<RexNode> measuresBuilder = new ImmutableList.Builder<>();  // RexNode：声明行表达式节点，代表关系表达式
    measuresBuilder.add(  // 执行代码
        builder.alias(builder.patternField("STRT", intType, 3),  // 执行代码
            "start_nw"));  // 执行代码
    measuresBuilder.add(  // 执行代码
        builder.alias(  // 执行代码
            builder.call(SqlStdOperatorTable.LAST,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("DOWN", intType, 3),  // 执行代码
                builder.literal(0)),  // 执行代码
            "bottom_nw"));  // 执行代码

    RexNode after =  // RexNode：声明行表达式节点，代表关系表达式
        builder.getRexBuilder()  // 执行代码
            .makeFlag(SqlMatchRecognize.AfterOption.SKIP_TO_NEXT_ROW);  // 执行代码

    ImmutableList.Builder<RexNode> partitionKeysBuilder = new ImmutableList.Builder<>();  // RexNode：声明行表达式节点，代表关系表达式
    partitionKeysBuilder.add(builder.field("DEPTNO"));  // 执行代码

    ImmutableList.Builder<RexNode> orderKeysBuilder = new ImmutableList.Builder<>();  // RexNode：声明行表达式节点，代表关系表达式
    orderKeysBuilder.add(builder.field("EMPNO"));  // 执行代码

    RexNode interval = builder.literal("INTERVAL '5' SECOND");  // RexNode：声明行表达式节点，代表关系表达式

    final ImmutableMap<String, TreeSet<String>> subsets = ImmutableMap.of();  // 执行代码
    final RelNode root = builder  // 声明根节点：最终构建的关系代数树的根节点
        .match(pattern, false, false, pdBuilder.build(),  // 构建操作：完成关系代数树的构建并返回根节点
            measuresBuilder.build(), after, subsets, false,  // 构建操作：完成关系代数树的构建并返回根节点
            partitionKeysBuilder.build(), orderKeysBuilder.build(), interval)  // 构建操作：完成关系代数树的构建并返回根节点
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalMatch(partition=[[7]], order=[[0]], "  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "outputFields=[[$7, 'start_nw', 'bottom_nw']], allRows=[false], "  // 执行代码
        + "after=[FLAG(SKIP TO NEXT ROW)], pattern=[(('STRT', "  // 执行代码
        + "PATTERN_QUANTIFIER('DOWN', 1, -1, false)), "  // 执行代码
        + "PATTERN_QUANTIFIER('UP', 1, -1, false))], "  // 执行代码
        + "isStrictStarts=[false], isStrictEnds=[false], "  // 执行代码
        + "interval=['INTERVAL ''5'' SECOND'], subsets=[[]], "  // 执行代码
        + "patternDefinitions=[[<(PREV(DOWN.$3, 0), PREV(DOWN.$3, 1)), "  // 执行代码
        + ">(PREV(UP.$3, 0), PREV(UP.$3, 1))]], "  // 执行代码
        + "inputFields=[[EMPNO, ENAME, JOB, MGR, HIREDATE, SAL, COMM, DEPTNO]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testFilterCastAny() {  // 测试方法：验证RelBuilder的FilterCastAny功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelDataType anyType =  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
        builder.getTypeFactory().createSqlType(SqlTypeName.ANY);  // SQL类型名：指定SQL数据类型
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.cast(  // 执行代码
                    builder.getRexBuilder().makeInputRef(anyType, 0),  // 执行代码
                    SqlTypeName.BOOLEAN))  // SQL类型名：指定SQL数据类型
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[CAST($0):BOOLEAN NOT NULL])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testFilterCastNull() {  // 测试方法：验证RelBuilder的FilterCastNull功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelDataTypeFactory typeFactory = builder.getTypeFactory();  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.getRexBuilder().makeCast(  // 执行代码
                    typeFactory.createTypeWithNullability(  // 执行代码
                        typeFactory.createSqlType(SqlTypeName.BOOLEAN), true),  // SQL类型名：指定SQL数据类型
                    builder.equals(builder.field("DEPTNO"),  // 执行代码
                        builder.literal(10))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[=($7, 10)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Tests {@link RelBuilder#in} with duplicate values. */  // 执行代码
  @Test void testFilterIn() {  // 测试方法：验证RelBuilder的FilterIn功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.in(b.field("DEPTNO"), b.literal(10), b.literal(20),  // 执行代码
                    b.literal(10)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[SEARCH($7, Sarg[10, 20])])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withSimplify(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expected));  // 执行代码
  }

  @Test void testFilterOrIn() {  // 测试方法：验证RelBuilder的FilterOrIn功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.or(  // 执行代码
                    b.greaterThan(b.field("DEPTNO"), b.literal(15)),  // 执行代码
                    b.in(b.field("JOB"), b.literal("CLERK")),  // 执行代码
                    b.in(b.field("DEPTNO"), b.literal(10), b.literal(20),  // 执行代码
                        b.literal(11), b.literal(10))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[OR(SEARCH($7, Sarg[10, 11, (15..+∞)]), =($2, 'CLERK'))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    final String expectedWithoutSimplify = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[OR(>($7, 15), =($2, 'CLERK'), SEARCH($7, Sarg[10, 11, 20]))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withSimplify(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedWithoutSimplify));  // 执行代码
  }

  /** Tests filter builder with correlation variables. */  // 执行代码
  @Test void testFilterWithCorrelationVariables() {  // 测试方法：验证RelBuilder的FilterWithCorrelationVariables功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .variable(v::set)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .filter(Collections.singletonList(v.get().id),  // 过滤操作：添加过滤条件到关系代数树
            builder.or(  // 执行代码
                builder.and(  // 执行代码
                    builder.lessThan(builder.field(v.get(), "DEPTNO"),  // 执行代码
                        builder.literal(30)),  // 执行代码
                    builder.greaterThan(builder.field(v.get(), "DEPTNO"),  // 执行代码
                        builder.literal(20))),  // 执行代码
                builder.isNull(builder.field(2))))  // 执行代码
        .join(JoinRelType.LEFT,  // 连接操作：添加连接节点到关系代数树
            builder.equals(builder.field(2, 0, "SAL"),  // 执行代码
                builder.literal(1000)),  // 执行代码
            ImmutableSet.of(v.get().id))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], "  // 执行代码
        + "requiredColumns=[{5, 7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.SAL, 1000)])\n"  // 执行代码
        + "    LogicalFilter(condition=[OR("  // 执行代码
        + "SEARCH($cor0.DEPTNO, Sarg[(20..30)]), "  // 执行代码
        + "IS NULL($2))], variablesSet=[[$cor0]])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码

    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testFilterEmpty() {  // 测试方法：验证RelBuilder的FilterEmpty功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            // We intend to call
            //   filter(Iterable<CorrelationId>, RexNode...)
            // with zero varargs, not
            //   filter(Iterable<RexNode>)
            // Let's hope they're distinct after type erasure.
            .filter(ImmutableSet.<CorrelationId>of())  // 过滤操作：添加过滤条件到关系代数树
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, hasTree("LogicalTableScan(table=[[scott, EMP]])\n"));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Checks if simplification is run in  // 执行代码
   * {@link org.apache.calcite.rex.RexUnknownAs#FALSE} mode for filter
   * conditions. */
  @Test void testFilterSimplification() {  // 测试方法：验证RelBuilder的FilterSimplification功能
    Function<RelBuilder, RelNode> f = b ->  // 执行代码
        b.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                b.or(b.literal(null),  // 执行代码
                     b.and(b.equals(b.field(2), b.literal(1)),  // 执行代码
                         b.equals(b.field(2), b.literal(2)))))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = "LogicalValues(tuples=[[]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    final String expectedWithoutSimplify = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[OR(null:NULL, AND(=($2, 1), =($2, 2)))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
    assertThat(f.apply(createBuilder(c -> c.withSimplify(false))),  // 断言验证：执行构建函数并验证结果
        hasTree(expectedWithoutSimplify));  // 执行代码
  }

  @Test void testRelBuilderToString() {  // 测试方法：验证RelBuilder的RelBuilderToString功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    builder.scan("EMP");  // 扫描表操作：将表数据源添加到关系代数树中

    // One entry on the stack, a single-node tree
    final String expected1 = "LogicalTableScan(table=[[scott, EMP]])\n";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    assertThat(Util.toLinux(builder.toString()), is(expected1));  // 执行代码

    // One entry on the stack, a two-node tree
    builder.filter(builder.equals(builder.field(2), builder.literal(3)));  // 过滤操作：添加过滤条件到关系代数树
    final String expected2 = "LogicalFilter(condition=[=($2, 3)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(Util.toLinux(builder.toString()), is(expected2));  // 执行代码

    // Two entries on the stack
    builder.scan("DEPT");  // 扫描表操作：将表数据源添加到关系代数树中
    final String expected3 = "LogicalTableScan(table=[[scott, DEPT]])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[=($2, 3)])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(Util.toLinux(builder.toString()), is(expected3));  // 执行代码
  }

  /**
   * Ensures that relational algebra ({@link RelBuilder}) works with SQL views.
   *
   * <p>This test currently fails (thus ignored).
   */
  @Test void testExpandViewInRelBuilder() throws SQLException {  // 测试方法：验证RelBuilder的ExpandViewInRelBuilder功能
    try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) {  // 执行代码
      final Frameworks.ConfigBuilder configBuilder =  // Frameworks：使用Calcite框架工具类进行配置
          expandingConfig(connection);  // 执行代码
      final RelOptTable.ViewExpander viewExpander =  // 执行代码
          (RelOptTable.ViewExpander) Frameworks.getPlanner(configBuilder.build());  // 构建操作：完成关系代数树的构建并返回根节点
      configBuilder.context(Contexts.of(viewExpander));  // 执行代码
      final RelBuilder builder = RelBuilder.create(configBuilder.build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      RelNode node = builder.scan("MYVIEW").build();  // 扫描表操作：将表数据源添加到关系代数树中

      int count = 0;  // 执行代码
      try (PreparedStatement statement =  // 执行代码
               connection.unwrap(RelRunner.class).prepareStatement(node);  // 执行代码
           ResultSet resultSet = statement.executeQuery()) {  // 执行代码
        while (resultSet.next()) {  // 执行代码
          count++;  // 执行代码
        }
      }

      assertTrue(count > 1);  // 执行代码
    }
  }

  @Test void testExpandViewShouldKeepAlias() throws SQLException {  // 测试方法：验证RelBuilder的ExpandViewShouldKeepAlias功能
    try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) {  // 执行代码
      final Frameworks.ConfigBuilder configBuilder =  // Frameworks：使用Calcite框架工具类进行配置
          expandingConfig(connection);  // 执行代码
      final RelOptTable.ViewExpander viewExpander =  // 执行代码
          (RelOptTable.ViewExpander) Frameworks.getPlanner(configBuilder.build());  // 构建操作：完成关系代数树的构建并返回根节点
      configBuilder.context(Contexts.of(viewExpander));  // 执行代码
      final RelBuilder builder = RelBuilder.create(configBuilder.build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      RelNode node =  // 执行代码
          builder.scan("MYVIEW")  // 扫描表操作：将表数据源添加到关系代数树中
              .project(  // 投影操作：选择和计算输出字段
                  builder.field(1, "MYVIEW", "EMPNO"),  // 执行代码
                  builder.field(1, "MYVIEW", "ENAME"))  // 执行代码
              .build();  // 构建操作：完成关系代数树的构建并返回根节点
      String expected =  // 执行代码
          "LogicalProject(EMPNO=[$0], ENAME=[$1])\n"  // 执行代码
              + "  LogicalFilter(condition=[=(1, 1)])\n"  // 执行代码
                  + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
      assertThat(node, hasTree(expected));  // 执行代码
    }
  }

  @Test void testExpandTable() throws SQLException {  // 测试方法：验证RelBuilder的ExpandTable功能
    try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) {  // 执行代码
      // RelBuilder expands as default. Plan contains JdbcTableScan,
      // because RelBuilder.scan has called RelOptTable.toRel.
      final Frameworks.ConfigBuilder configBuilder =  // Frameworks：使用Calcite框架工具类进行配置
          expandingConfig(connection);  // 执行代码
      final RelBuilder builder = RelBuilder.create(configBuilder.build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
      final String expected = "LogicalFilter(condition=[>($2, 10)])\n"  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
          + "  JdbcTableScan(table=[[JDBC_SCOTT, EMP]])\n";  // 执行代码
      checkExpandTable(builder, hasTree(expected));  // 执行代码
    }
  }

  private void checkExpandTable(RelBuilder builder, Matcher<RelNode> matcher) {  // 辅助方法
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("JDBC_SCOTT", "EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.greaterThan(builder.field(2), builder.literal(10)))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root, matcher);  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testExchange() {  // 测试方法：验证RelBuilder的Exchange功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .exchange(RelDistributions.hash(Lists.newArrayList(0)))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalExchange(distribution=[hash[0]])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSortExchange() {  // 测试方法：验证RelBuilder的SortExchange功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .sortExchange(RelDistributions.hash(Lists.newArrayList(0)),  // 执行代码
                RelCollations.of(0))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected =  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        "LogicalSortExchange(distribution=[hash[0]], collation=[[0]])\n"  // 执行代码
            + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testCorrelate() {  // 测试方法：验证RelBuilder的Correlate功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .variable(v::set)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.equals(builder.field(0),  // 执行代码
                builder.field(v.get(), "DEPTNO")))  // 执行代码
        .correlate(JoinRelType.LEFT, v.get().id, builder.field(2, 0, "DEPTNO"))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($0, $cor0.DEPTNO)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSimpleSemiCorrelateViaJoin() {  // 测试方法：验证RelBuilder的SimpleSemiCorrelateViaJoin功能
    RelNode root = buildSimpleCorrelateWithJoin(JoinRelType.SEMI);  // 声明根节点：最终构建的关系代数树的根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($7, $8)], joinType=[semi], variablesSet=[[$cor0]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Join with correlate id but the id never used should be simplified to a join.",  // 执行代码
        root, hasTree(expected));  // 执行代码
  }

  @Test void testSimpleSemiCorrelateViaJoinWithoutConvertCorrelateToJoin() {  // 测试方法：验证RelBuilder的SimpleSemiCorrelateViaJoinWithoutConvertCorrelateToJoin功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        buildSimpleCorrelateWithJoin(JoinRelType.SEMI, b);  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[semi], requiredColumns=[{7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.DEPTNO, $0)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Join with correlate id but the id never used should be simplified to a join.",  // 执行代码
        f.apply(createBuilder(c -> c.withConvertCorrelateToJoin(false))), hasTree(expected));  // 执行代码
  }

  @ParameterizedTest  // 执行代码
  @ValueSource(booleans = {true, false})  // 执行代码
  void testSemiCorrelatedViaJoin(boolean convertCorrelateToJoin) {  // 执行代码
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        buildCorrelateWithJoin(JoinRelType.SEMI, b);  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[semi], requiredColumns=[{0, 7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.DEPTNO, $0)])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($cor0.EMPNO, 'NaN')])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Correlated semi joins should emmit a correlate with a filter on the right side.",  // 执行代码
        f.apply(createBuilder(c -> c.withConvertCorrelateToJoin(convertCorrelateToJoin))),  // 执行代码
            hasTree(expected));  // 执行代码
  }

  @Test void testSimpleAntiCorrelateViaJoin() {  // 测试方法：验证RelBuilder的SimpleAntiCorrelateViaJoin功能
    RelNode root = buildSimpleCorrelateWithJoin(JoinRelType.ANTI);  // 声明根节点：最终构建的关系代数树的根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($7, $8)], joinType=[anti], variablesSet=[[$cor0]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Join with correlate id but the id never used should be simplified to a join.",  // 执行代码
        root, hasTree(expected));  // 执行代码
  }

  @Test void testSimpleAntiCorrelateViaJoinWithoutConvertCorrelateToJoin() {  // 测试方法：验证RelBuilder的SimpleAntiCorrelateViaJoinWithoutConvertCorrelateToJoin功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        buildSimpleCorrelateWithJoin(JoinRelType.ANTI, b);  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[anti], requiredColumns=[{7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.DEPTNO, $0)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Join with correlate id but the id never used should be simplified to a join.",  // 执行代码
        f.apply(createBuilder(c -> c.withConvertCorrelateToJoin(false))), hasTree(expected));  // 执行代码
  }

  @ParameterizedTest  // 执行代码
  @ValueSource(booleans = {true, false})  // 执行代码
  void testAntiCorrelateViaJoin(boolean convertCorrelateToJoin) {  // 执行代码
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        buildCorrelateWithJoin(JoinRelType.ANTI, b);  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[anti], requiredColumns=[{0, 7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.DEPTNO, $0)])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($cor0.EMPNO, 'NaN')])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Correlated anti joins should emmit a correlate with a filter on the right side.",  // 执行代码
        f.apply(createBuilder(c -> c.withConvertCorrelateToJoin(convertCorrelateToJoin))),  // 执行代码
            hasTree(expected));  // 执行代码
  }

  @Test void testSimpleLeftCorrelateViaJoin() {  // 测试方法：验证RelBuilder的SimpleLeftCorrelateViaJoin功能
    RelNode root = buildSimpleCorrelateWithJoin(JoinRelType.LEFT);  // 声明根节点：最终构建的关系代数树的根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($7, $8)], joinType=[left], variablesSet=[[$cor0]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Join with correlate id but the id never used should be simplified to a join.",  // 执行代码
        root, hasTree(expected));  // 执行代码
  }

  @Test void testSimpleLeftCorrelateViaJoinWithoutConvertCorrelateToJoin() {  // 测试方法：验证RelBuilder的SimpleLeftCorrelateViaJoinWithoutConvertCorrelateToJoin功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        buildSimpleCorrelateWithJoin(JoinRelType.LEFT, b);  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.DEPTNO, $0)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Join with correlate id but the id never used should be simplified to a join.",  // 执行代码
        f.apply(createBuilder(c -> c.withConvertCorrelateToJoin(false))), hasTree(expected));  // 执行代码
  }

  @ParameterizedTest  // 执行代码
  @ValueSource(booleans = {true, false})  // 执行代码
  void testLeftCorrelateViaJoin(boolean convertCorrelateToJoin) {  // 执行代码
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        buildCorrelateWithJoin(  // 执行代码
            JoinRelType.LEFT,  // 执行代码
            createBuilder(c -> c.withConvertCorrelateToJoin(convertCorrelateToJoin)));  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{0, 7}])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($cor0.DEPTNO, $0)])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($cor0.EMPNO, 'NaN')])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Correlated left joins should emmit a correlate with a filter on the right side.",  // 执行代码
        root, hasTree(expected));  // 执行代码
  }

  @Test void testSimpleInnerCorrelateViaJoin() {  // 测试方法：验证RelBuilder的SimpleInnerCorrelateViaJoin功能
    RelNode root = buildSimpleCorrelateWithJoin(JoinRelType.INNER);  // 声明根节点：最终构建的关系代数树的根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalJoin(condition=[=($7, $8)], joinType=[inner], variablesSet=[[$cor0]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat("Join with correlate id but never used should be simplified to a join.",  // 执行代码
        root, hasTree(expected));  // 执行代码
  }

  @Test void testSimpleInnerCorrelateViaJoinWithoutConvertCorrelateToJoin() {  // 测试方法：验证RelBuilder的SimpleInnerCorrelateViaJoinWithoutConvertCorrelateToJoin功能
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        buildSimpleCorrelateWithJoin(JoinRelType.INNER, b);  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[=($7, $8)])\n"  // 执行代码
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[inner], requiredColumns=[{}])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat("Join with correlate id but never used should be simplified to a join.",  // 执行代码
        f.apply(createBuilder(c -> c.withConvertCorrelateToJoin(false))), hasTree(expected));  // 执行代码
  }

  @ParameterizedTest  // 执行代码
  @ValueSource(booleans = {true, false})  // 执行代码
  void testInnerCorrelateViaJoin(boolean convertCorrelateToJoin) {  // 执行代码
    final Function<RelBuilder, RelNode> f = b ->  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        buildCorrelateWithJoin(JoinRelType.INNER, b);  // 执行代码
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[=($7, $8)])\n"  // 执行代码
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[inner], requiredColumns=[{0}])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "    LogicalFilter(condition=[=($cor0.EMPNO, 'NaN')])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(  // 执行代码
        "Correlated inner joins should emmit a correlate with a filter on top.",  // 执行代码
        f.apply(createBuilder(c -> c.withConvertCorrelateToJoin(convertCorrelateToJoin))),  // 执行代码
            hasTree(expected));  // 执行代码
  }

  @Test void testSimpleRightCorrelateViaJoinThrowsException() {  // 测试方法：验证RelBuilder的SimpleRightCorrelateViaJoinThrowsException功能
    assertThrows(IllegalArgumentException.class,  // 异常断言：验证是否抛出预期的异常
        () -> buildSimpleCorrelateWithJoin(JoinRelType.RIGHT),  // 执行代码
        "Right outer joins with correlated ids are invalid even if id is not used.");  // 执行代码
  }

  @Test void testSimpleFullCorrelateViaJoinThrowsException() {  // 测试方法：验证RelBuilder的SimpleFullCorrelateViaJoinThrowsException功能
    assertThrows(IllegalArgumentException.class,  // 异常断言：验证是否抛出预期的异常
        () -> buildSimpleCorrelateWithJoin(JoinRelType.FULL),  // 执行代码
        "Full outer joins with correlated ids are invalid even if id is not used.");  // 执行代码
  }

  @Test void testRightCorrelateViaJoinThrowsException() {  // 测试方法：验证RelBuilder的RightCorrelateViaJoinThrowsException功能
    assertThrows(IllegalArgumentException.class,  // 异常断言：验证是否抛出预期的异常
        () -> buildCorrelateWithJoin(JoinRelType.RIGHT),  // 执行代码
        "Right outer joins with correlated ids are invalid.");  // 执行代码
  }

  @Test void testFullCorrelateViaJoinThrowsException() {  // 测试方法：验证RelBuilder的FullCorrelateViaJoinThrowsException功能
    assertThrows(IllegalArgumentException.class,  // 异常断言：验证是否抛出预期的异常
        () -> buildCorrelateWithJoin(JoinRelType.FULL),  // 执行代码
        "Full outer joins with correlated ids are invalid.");  // 执行代码
  }

  private static RelNode buildSimpleCorrelateWithJoin(JoinRelType type) {  // 辅助方法
    return buildSimpleCorrelateWithJoin(type, RelBuilder.create(config().build()));  // 构建操作：完成关系代数树的构建并返回根节点
  }

  private static RelNode buildSimpleCorrelateWithJoin(JoinRelType type, RelBuilder builder) {  // 辅助方法
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    return builder  // 执行代码
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .variable(v::set)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .join(type,  // 连接操作：添加连接节点到关系代数树
            builder.equals(  // 执行代码
                builder.field(2, 0, "DEPTNO"),  // 执行代码
                builder.field(2, 1, "DEPTNO")), ImmutableSet.of(v.get().id))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
  }

  private static RelNode buildCorrelateWithJoin(JoinRelType type) {  // 辅助方法
    return buildCorrelateWithJoin(type, RelBuilder.create(config().build()));  // 构建操作：完成关系代数树的构建并返回根节点
  }

  private static RelNode buildCorrelateWithJoin(JoinRelType type, RelBuilder builder) {  // 辅助方法
    final RexBuilder rexBuilder = builder.getRexBuilder();  // 执行代码
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    return builder  // 执行代码
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .variable(v::set)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.equals(  // 执行代码
                rexBuilder.makeFieldAccess(v.get(), 0),  // 执行代码
                builder.literal("NaN")))  // 执行代码
        .join(type,  // 连接操作：添加连接节点到关系代数树
            builder.equals(  // 执行代码
                builder.field(2, 0, "DEPTNO"),  // 执行代码
                builder.field(2, 1, "DEPTNO")), ImmutableSet.of(v.get().id))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
  }

  @Test void testCorrelateWithComplexFields() {  // 测试方法：验证RelBuilder的CorrelateWithComplexFields功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();  // 执行代码
    RelNode root = builder.scan("EMP")  // 声明根节点：最终构建的关系代数树的根节点
        .variable(v::set)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.equals(builder.field(0),  // 执行代码
                builder.field(v.get(), "DEPTNO")))  // 执行代码
        .correlate(JoinRelType.LEFT, v.get().id,  // 执行代码
            builder.field(2, 0, "DEPTNO"),  // 执行代码
            builder.getRexBuilder().makeCall(SqlStdOperatorTable.AS,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.field(2, 0, "EMPNO"),  // 执行代码
                builder.literal("RENAMED_EMPNO")))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点

    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{0, 7}])\n"  // 执行代码
        + "  LogicalProject(RENAMED_EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n"  // 执行代码
        + "  LogicalFilter(condition=[=($0, $cor0.DEPTNO)])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testDynamicParameterInLimitOffset() {  // 测试方法：验证RelBuilder的DynamicParameterInLimitOffset功能
    final RelBuilder relBuilder = RelBuilder.create(config().build());  // 构建操作：完成关系代数树的构建并返回根节点
    final RelDataType intType = relBuilder.getTypeFactory().createSqlType(SqlTypeName.INTEGER);  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
    final RexBuilder rexBuilder = relBuilder.getRexBuilder();  // RexBuilder：用于构建RexNode表达式树的构建器

    RelNode planBefore = relBuilder  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .sortLimit(rexBuilder.makeDynamicParam(intType, 1),  // 执行代码
            rexBuilder.makeDynamicParam(intType, 0),  // 执行代码
            ImmutableList.of())  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    String expectedLogicalPlan = "LogicalSort(offset=[?1], fetch=[?0])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(planBefore, hasTree(expectedLogicalPlan));  // 执行代码

    RuleSet prepareRules =  // 执行代码
        RuleSets.ofList(EnumerableRules.ENUMERABLE_FILTER_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_SORT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_LIMIT_RULE,  // 执行代码
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE);  // 执行代码
    RelTraitSet desiredTraits = planBefore.getTraitSet()  // 执行代码
        .replace(EnumerableConvention.INSTANCE);  // 执行代码
    Program program = Programs.of(prepareRules);  // 执行代码
    RelNode planAfter =  // 执行代码
        program.run(planBefore.getCluster().getPlanner(), planBefore,  // 执行代码
            desiredTraits, ImmutableList.of(), ImmutableList.of());  // 执行代码
    String expectedEnumerablePlan = "EnumerableLimit(offset=[?1], fetch=[?0])\n"  // 执行代码
        + "  EnumerableTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(planAfter, hasTree(expectedEnumerablePlan));  // 执行代码

    RelMetadataQuery mq = planAfter.getCluster().getMetadataQuery();  // 执行代码
    assertThat(mq.getMinRowCount(planAfter), is(0D));  // 执行代码
    assertThat(mq.getMaxRowCount(planAfter), is(Double.POSITIVE_INFINITY));  // 执行代码
  }

  @Test void testAdoptConventionEnumerable() {  // 测试方法：验证RelBuilder的AdoptConventionEnumerable功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root = builder  // 声明根节点：最终构建的关系代数树的根节点
        .adoptConvention(EnumerableConvention.INSTANCE)  // 执行代码
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.equals(builder.field("DEPTNO"), builder.literal(20)))  // 执行代码
        .sort(builder.field(2), builder.desc(builder.field(0)))  // 排序操作：添加排序节点到关系代数树
        .project(builder.field(0))  // 投影操作：选择和计算输出字段
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    String expected = ""  // 执行代码
        + "EnumerableProject(DEPTNO=[$0])\n"  // 执行代码
        + "  EnumerableSort(sort0=[$2], sort1=[$0], dir0=[ASC], dir1=[DESC])\n"  // 执行代码
        + "    EnumerableFilter(condition=[=($0, 20)])\n"  // 执行代码
        + "      EnumerableTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testSwitchConventions() {  // 测试方法：验证RelBuilder的SwitchConventions功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root = builder  // 声明根节点：最终构建的关系代数树的根节点
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .adoptConvention(EnumerableConvention.INSTANCE)  // 执行代码
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.equals(builder.field("DEPTNO"), builder.literal(20)))  // 执行代码
        .sort(builder.field(2), builder.desc(builder.field(0)))  // 排序操作：添加排序节点到关系代数树
        .adoptConvention(Convention.NONE)  // 执行代码
        .project(builder.field(0))  // 投影操作：选择和计算输出字段
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    String expected = ""  // 执行代码
        + "LogicalProject(DEPTNO=[$0])\n"  // 执行代码
        + "  EnumerableSort(sort0=[$2], sort1=[$0], dir0=[ASC], dir1=[DESC])\n"  // 执行代码
        + "    EnumerableFilter(condition=[=($0, 20)])\n"  // 执行代码
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testHints() {  // 测试方法：验证RelBuilder的Hints功能
    final RelHint indexHint = RelHint.builder("INDEX")  // 执行代码
        .hintOption("_idx1")  // 执行代码
        .hintOption("_idx2")  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final RelHint propsHint = RelHint.builder("PROPERTIES")  // 执行代码
        .inheritPath(0)  // 执行代码
        .hintOption("parallelism", "3")  // 执行代码
        .hintOption("mem", "20Mb")  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final RelHint noHashJoinHint = RelHint.builder("NO_HASH_JOIN")  // 执行代码
        .inheritPath(0)  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final RelHint hashJoinHint = RelHint.builder("USE_HASH_JOIN")  // 执行代码
        .hintOption("orders")  // 执行代码
        .hintOption("products_temporal")  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp /*+ INDEX(_idx1, _idx2) */
    final RelNode root = builder  // 声明根节点：最终构建的关系代数树的根节点
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .hints(indexHint)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root,  // 断言验证：验证构建的RelNode是否符合预期
        hasHints("[[INDEX inheritPath:[] options:[_idx1, _idx2]]]"));  // 执行代码
    // Equivalent SQL:
    //   SELECT /*+  PROPERTIES(parallelism='3', mem='20Mb') */
    //   *
    //   FROM emp /*+ INDEX(_idx1, _idx2) */
    final RelNode root1 = builder  // 声明根节点：最终构建的关系代数树的根节点
            .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .hints(indexHint, propsHint)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root1,  // 执行代码
        hasHints("[[INDEX inheritPath:[] options:[_idx1, _idx2]], "  // 执行代码
            + "[PROPERTIES inheritPath:[0] options:{parallelism=3, mem=20Mb}]]"));  // 执行代码
    // Equivalent SQL:
    //   SELECT /*+ NO_HASH_JOIN */
    //   *
    //   FROM emp
    //     join dept
    //     on emp.deptno = dept.deptno
    final RelNode root2 = builder  // 声明根节点：最终构建的关系代数树的根节点
        .scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .scan("DEPT")  // 扫描表操作：将表数据源添加到关系代数树中
        .join(JoinRelType.INNER,  // 连接操作：添加连接节点到关系代数树
            builder.equals(  // 执行代码
                builder.field(2, 0, "DEPTNO"),  // 执行代码
                builder.field(2, 1, "DEPTNO")))  // 执行代码
        .hints(noHashJoinHint)  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root2, hasHints("[[NO_HASH_JOIN inheritPath:[0]]]"));  // 执行代码

    // Equivalent SQL:
    //   SELECT *
    //   FROM orders
    //   JOIN products_temporal FOR SYSTEM_TIME AS OF orders.rowtime
    //   ON orders.product = products_temporal.id
    RelNode left = builder.scan("orders").build();  // 扫描表操作：将表数据源添加到关系代数树中
    RelNode right = builder.scan("products_temporal").build();  // 扫描表操作：将表数据源添加到关系代数树中
    RexNode period =  // RexNode：声明行表达式节点，代表关系表达式
        builder.getRexBuilder().makeFieldAccess(  // 执行代码
            builder.getRexBuilder().makeCorrel(left.getRowType(),  // 执行代码
                new CorrelationId(0)),  // 执行代码
            0);  // 执行代码
    RelNode root3 =  // 声明根节点：最终构建的关系代数树的根节点
        builder  // 执行代码
            .push(left)  // 执行代码
            .push(right)  // 执行代码
            .snapshot(period)  // 执行代码
            .correlate(  // 执行代码
                JoinRelType.INNER,  // 执行代码
                new CorrelationId(0),  // 执行代码
                builder.field(2, 0, "ROWTIME"),  // 执行代码
                builder.field(2, 0, "ID"),  // 执行代码
                builder.field(2, 0, "PRODUCT"))  // 执行代码
            .hints(hashJoinHint)  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    assertThat(root3,  // 执行代码
        hasHints("[[USE_HASH_JOIN inheritPath:[] options:[orders, products_temporal]]]"));  // 执行代码
  }

  @Test void testHintsOnEmptyStack() {  // 测试方法：验证RelBuilder的HintsOnEmptyStack功能
    final RelHint indexHint = RelHint.builder("INDEX")  // 执行代码
        .hintOption("_idx1")  // 执行代码
        .hintOption("_idx2")  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Attach hints on empty stack.
    final IllegalArgumentException error =  // 执行代码
        assertThrows(IllegalArgumentException.class,  // 异常断言：验证是否抛出预期的异常
            () -> RelBuilder.create(config().build()).hints(indexHint),  // 构建操作：完成关系代数树的构建并返回根节点
        "hints() should fail on empty stack");  // 执行代码
    assertThat(error.getMessage(),  // 执行代码
        containsString("There is no relational expression to attach the hints"));  // 执行代码
  }

  @Test void testHintsOnNonHintable() {  // 测试方法：验证RelBuilder的HintsOnNonHintable功能
    final RelHint indexHint = RelHint.builder("INDEX")  // 执行代码
        .hintOption("_idx1")  // 执行代码
        .hintOption("_idx2")  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Attach hints on non hintable.

    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   MATCH_RECOGNIZE (
    //     PARTITION BY deptno
    //     ORDER BY empno asc
    //     MEASURES
    //       STRT.mgr as start_nw,
    //       LAST(DOWN.mgr) as bottom_nw,
    //     PATTERN (STRT DOWN+ UP+) WITHIN INTERVAL '5' SECOND
    //     DEFINE
    //       DOWN as DOWN.mgr < PREV(DOWN.mgr),
    //       UP as UP.mgr > PREV(UP.mgr)
    //   )
    final RelBuilder builder = RelBuilder.create(config().build()).scan("EMP");  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelDataTypeFactory typeFactory = builder.getTypeFactory();  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
    final RelDataType intType = typeFactory.createSqlType(SqlTypeName.INTEGER);  // RelDataType：声明关系数据类型，描述表或表达式的类型信息

    RexNode pattern =  // RexNode：声明行表达式节点，代表关系表达式
        builder.patternConcat(builder.literal("STRT"),  // 执行代码
            builder.patternQuantify(builder.literal("DOWN"),  // 执行代码
                builder.literal(1), builder.literal(-1),  // 执行代码
                builder.literal(false)),  // 执行代码
            builder.patternQuantify(builder.literal("UP"),  // 执行代码
                builder.literal(1), builder.literal(-1),  // 执行代码
                builder.literal(false)));  // 执行代码

    ImmutableMap.Builder<String, RexNode> pdBuilder =  // RexNode：声明行表达式节点，代表关系表达式
        new ImmutableMap.Builder<>();  // 执行代码
    RexNode downDefinition =  // RexNode：声明行表达式节点，代表关系表达式
        builder.lessThan(  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("DOWN", intType, 3),  // 执行代码
                builder.literal(0)),  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("DOWN", intType, 3),  // 执行代码
                builder.literal(1)));  // 执行代码
    pdBuilder.put("DOWN", downDefinition);  // 执行代码
    RexNode upDefinition =  // RexNode：声明行表达式节点，代表关系表达式
        builder.greaterThan(  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("UP", intType, 3),  // 执行代码
                builder.literal(0)),  // 执行代码
            builder.call(SqlStdOperatorTable.PREV,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("UP", intType, 3),  // 执行代码
                builder.literal(1)));  // 执行代码
    pdBuilder.put("UP", upDefinition);  // 执行代码

    ImmutableList.Builder<RexNode> measuresBuilder =  // RexNode：声明行表达式节点，代表关系表达式
        new ImmutableList.Builder<>();  // 执行代码
    measuresBuilder.add(  // 执行代码
        builder.alias(builder.patternField("STRT", intType, 3), "start_nw"));  // 执行代码
    measuresBuilder.add(  // 执行代码
        builder.alias(  // 执行代码
            builder.call(SqlStdOperatorTable.LAST,  // SQL操作符：使用标准SQL操作符构建表达式
                builder.patternField("DOWN", intType, 3),  // 执行代码
                builder.literal(0)),  // 执行代码
            "bottom_nw"));  // 执行代码

    RexNode after =  // RexNode：声明行表达式节点，代表关系表达式
        builder.getRexBuilder().makeFlag(  // 执行代码
            SqlMatchRecognize.AfterOption.SKIP_TO_NEXT_ROW);  // 执行代码

    ImmutableList.Builder<RexNode> partitionKeysBuilder =  // RexNode：声明行表达式节点，代表关系表达式
        new ImmutableList.Builder<>();  // 执行代码
    partitionKeysBuilder.add(builder.field("DEPTNO"));  // 执行代码

    ImmutableList.Builder<RexNode> orderKeysBuilder =  // RexNode：声明行表达式节点，代表关系表达式
        new ImmutableList.Builder<>();  // 执行代码
    orderKeysBuilder.add(builder.field("EMPNO"));  // 执行代码

    RexNode interval = builder.literal("INTERVAL '5' SECOND");  // RexNode：声明行表达式节点，代表关系表达式

    final IllegalArgumentException error1 =  // 执行代码
        assertThrows(IllegalArgumentException.class, () ->  // 异常断言：验证是否抛出预期的异常
            builder.match(pattern, false, false, pdBuilder.build(),  // 构建操作：完成关系代数树的构建并返回根节点
                    measuresBuilder.build(), after, ImmutableMap.of(), false,  // 构建操作：完成关系代数树的构建并返回根节点
                    partitionKeysBuilder.build(), orderKeysBuilder.build(),  // 构建操作：完成关系代数树的构建并返回根节点
                    interval)  // 执行代码
                .hints(indexHint),  // 执行代码
            "hints() should fail on non Hintable relational expression");  // 执行代码
    assertThat(error1.getMessage(),  // 执行代码
        containsString("The top relational expression is not a Hintable"));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3747">[CALCITE-3747]
   * Constructing BETWEEN with RelBuilder throws class cast exception</a>.
   *
   * <p>BETWEEN is no longer allowed in RexCall. 'a BETWEEN b AND c' is expanded
   * 'a >= b AND a <= c', whether created via
   * {@link RelBuilder#call(SqlOperator, RexNode...)} or
   * {@link RelBuilder#between(RexNode, RexNode, RexNode)}.*/
  @Test void testCallBetweenOperator() {  // 测试方法：验证RelBuilder的CallBetweenOperator功能
    final RelBuilder builder = RelBuilder.create(config().build()).scan("EMP");  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象

    final String expected = "SEARCH($0, Sarg[[1..5]])";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
    final RexNode call =  // RexNode：声明行表达式节点，代表关系表达式
        builder.call(SqlStdOperatorTable.BETWEEN,  // SQL操作符：使用标准SQL操作符构建表达式
            builder.field("EMPNO"),  // 执行代码
            builder.literal(1),  // 执行代码
            builder.literal(5));  // 执行代码
    assertThat(call, hasToString(expected));  // 执行代码

    final RexNode call2 =  // RexNode：声明行表达式节点，代表关系表达式
        builder.between(builder.field("EMPNO"),  // 执行代码
            builder.literal(1),  // 执行代码
            builder.literal(5));  // 执行代码
    assertThat(call2, hasToString(expected));  // 执行代码

    final RelNode root = builder.filter(call2).build();  // 声明根节点：最终构建的关系代数树的根节点
    final String expectedRel = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[SEARCH($0, Sarg[[1..5]])])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expectedRel));  // 断言验证：验证构建的RelNode是否符合预期

    // Consecutive filters are not merged. (For now, anyway.)
    builder.push(root)  // 执行代码
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.not(  // 执行代码
                builder.equals(builder.field("EMPNO"), builder.literal(3))),  // 执行代码
            builder.equals(builder.field("DEPTNO"), builder.literal(10)));  // 执行代码
    final RelNode root2 = builder.build();  // 声明根节点：最终构建的关系代数树的根节点
    final String expectedRel2 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[AND(<>($0, 3), =($7, 10))])\n"  // 执行代码
        + "  LogicalFilter(condition=[SEARCH($0, Sarg[[1..5]])])\n"  // 执行代码
        + "    LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root2, hasTree(expectedRel2));  // 执行代码

    // The conditions in one filter are simplified.
    builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
        .filter(  // 过滤操作：添加过滤条件到关系代数树
            builder.between(builder.field("EMPNO"),  // 执行代码
                builder.literal(1),  // 执行代码
                builder.literal(5)),  // 执行代码
            builder.not(  // 执行代码
                builder.equals(builder.field("EMPNO"), builder.literal(3))),  // 执行代码
            builder.equals(builder.field("DEPTNO"), builder.literal(10)));  // 执行代码
    final RelNode root3 = builder.build();  // 声明根节点：最终构建的关系代数树的根节点
    final String expectedRel3 = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[AND(SEARCH($0, Sarg[[1..3), (3..5]]), =($7, 10))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root3, hasTree(expectedRel3));  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3926">[CALCITE-3926]
   * CannotPlanException when an empty LogicalValues requires a certain collation</a>. */
  @Test void testEmptyValuesWithCollation() throws Exception {  // 测试方法：验证RelBuilder的EmptyValuesWithCollation功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder  // 执行代码
            .scan("DEPT").empty()  // 扫描表操作：将表数据源添加到关系代数树中
            .sort(  // 排序操作：添加排序节点到关系代数树
                builder.field("DNAME"),  // 执行代码
                builder.field("DEPTNO"))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    try (PreparedStatement preparedStatement = RelRunners.run(root)) {  // 执行代码
      final String result = CalciteAssert.toString(preparedStatement.executeQuery());  // 执行代码
      final String expectedResult = "";  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
      assertThat(result, is(expectedResult));  // 执行代码
    }
  }

  /** Tests {@link RelBuilder#isDistinctFrom} and  // 执行代码
   * {@link RelBuilder#isNotDistinctFrom}. */
  @Test void testIsDistinctFrom() {  // 测试方法：验证RelBuilder的IsDistinctFrom功能
    final Function<RelBuilder, RelNode> f = b -> b.scan("EMP")  // 定义构建函数：使用Lambda表达式定义从RelBuilder到RelNode的转换逻辑
        .project(b.field("DEPTNO"),  // 投影操作：选择和计算输出字段
            b.isNotDistinctFrom(b.field("SAL"), b.field("DEPTNO")),  // 执行代码
            b.isNotDistinctFrom(b.field("EMPNO"), b.field("DEPTNO")),  // 执行代码
            b.isDistinctFrom(b.field("EMPNO"), b.field("DEPTNO")))  // 执行代码
        .build();  // 构建操作：完成关系代数树的构建并返回根节点
    // Note: skip IS NULL check when both fields are NOT NULL;
    // enclose in IS TRUE or IS NOT TRUE so that the result is BOOLEAN NOT NULL.
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalProject(DEPTNO=[$7], "  // 执行代码
        + "$f1=[OR(AND(IS NULL($5), IS NULL($7)), IS TRUE(=($5, $7)))], "  // 执行代码
        + "$f2=[IS TRUE(=($0, $7))], "  // 执行代码
        + "$f3=[IS NOT TRUE(=($0, $7))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(f.apply(createBuilder()), hasTree(expected));  // 断言验证：执行构建函数并验证结果
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4415">[CALCITE-4415]
   * SqlStdOperatorTable.NOT_LIKE has a wrong implementor</a>. */
  @Test void testNotLike() {  // 测试方法：验证RelBuilder的NotLike功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(SqlStdOperatorTable.NOT_LIKE,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("ENAME"),  // 执行代码
                    builder.literal("a%b%c")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[NOT(LIKE($1, 'a%b%c'))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  @Test void testNotIlike() {  // 测试方法：验证RelBuilder的NotIlike功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(SqlLibraryOperators.NOT_ILIKE,  // 执行代码
                    builder.field("ENAME"),  // 执行代码
                    builder.literal("a%b%c")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[NOT(ILIKE($1, 'a%b%c'))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4415">[CALCITE-4415]
   * SqlStdOperatorTable.NOT_LIKE has a wrong implementor</a>. */
  @Test void testNotSimilarTo() {  // 测试方法：验证RelBuilder的NotSimilarTo功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    RelNode root =  // 声明根节点：最终构建的关系代数树的根节点
        builder.scan("EMP")  // 扫描表操作：将表数据源添加到关系代数树中
            .filter(  // 过滤操作：添加过滤条件到关系代数树
                builder.call(  // 执行代码
                    SqlStdOperatorTable.NOT_SIMILAR_TO,  // SQL操作符：使用标准SQL操作符构建表达式
                    builder.field("ENAME"),  // 执行代码
                    builder.literal("a%b%c")))  // 执行代码
            .build();  // 构建操作：完成关系代数树的构建并返回根节点
    final String expected = ""  // 定义期望结果：预期的RelNode树字符串表示，用于断言验证
        + "LogicalFilter(condition=[NOT(SIMILAR TO($1, 'a%b%c'))])\n"  // 执行代码
        + "  LogicalTableScan(table=[[scott, EMP]])\n";  // 执行代码
    assertThat(root, hasTree(expected));  // 断言验证：验证构建的RelNode是否符合预期
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4415">[CALCITE-4415]
   * SqlStdOperatorTable.NOT_LIKE has a wrong implementor</a>. */
  @Test void testExecuteNotLike() {  // 测试方法：验证RelBuilder的ExecuteNotLike功能
    CalciteAssert.that()  // 执行代码
        .withSchema("s", new ReflectiveSchema(new HrSchema()))  // 执行代码
        .withRel(  // 执行代码
            builder -> builder  // 执行代码
                .scan("s", "emps")  // 扫描表操作：将表数据源添加到关系代数树中
                .filter(  // 过滤操作：添加过滤条件到关系代数树
                    builder.call(  // 执行代码
                        SqlStdOperatorTable.NOT_LIKE,  // SQL操作符：使用标准SQL操作符构建表达式
                        builder.field("name"),  // 执行代码
                        builder.literal("%r%c")))  // 执行代码
                .project(  // 投影操作：选择和计算输出字段
                    builder.field("empid"),  // 执行代码
                    builder.field("name"))  // 执行代码
                .build())  // 构建操作：完成关系代数树的构建并返回根节点
        .returnsUnordered(  // 执行代码
            "empid=100; name=Bill",  // 执行代码
            "empid=110; name=Theodore",  // 执行代码
            "empid=150; name=Sebastian");  // 执行代码
  }

  /** Test case for  // 执行代码
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6688">[CALCITE-6688]
   * Allow operators of SqlKind.SYMMETRICAL to be reversed</a>. */
  @Test void testSymmetricalOperatorsCanBeReversed() {  // 测试方法：验证RelBuilder的SymmetricalOperatorsCanBeReversed功能
    final RelBuilder builder = RelBuilder.create(config().build());  // 创建RelBuilder实例：这是构建关系代数树的核心构建器对象
    final RelDataType type = builder.getTypeFactory().createUnknownType();  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
    final RexInputRef r1 = new RexInputRef(1, type);  // 执行代码
    final RexInputRef r2 = new RexInputRef(2, type);  // 执行代码
    final List<SqlOperator> symmetricOperators =  // SQL操作符：使用标准SQL操作符构建表达式
        SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(  // 执行代码
            SqlLibrary.values()).getOperatorList().stream().filter(  // 过滤操作：添加过滤条件到关系代数树
                op -> op.isSymmetrical()).collect(Collectors.toList());  // 执行代码

    for (SqlOperator op : symmetricOperators) {  // SQL操作符：使用标准SQL操作符构建表达式
      RexNode c1 = builder.call(op, r1, r2);  // RexNode：声明行表达式节点，代表关系表达式
      RexNode c2 = builder.call(op, r2, r1);  // RexNode：声明行表达式节点，代表关系表达式

      assertDoesNotThrow(() -> op.reverse());  // 执行代码
      assertTrue(c1.equals(c2));  // 执行代码
    }
  }

  /** Operand to a user-defined function. */  // 执行代码
  private interface Arg {  // 辅助方法
    String name();  // 执行代码
    RelDataType type(RelDataTypeFactory typeFactory);  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
    SqlTypeFamily family();  // 执行代码
    boolean optional();  // 执行代码

    static SqlOperandMetadata metadata(Arg... args) {  // 执行代码
      return OperandTypes.operandMetadata(  // 执行代码
          Arrays.stream(args).map(Arg::family).collect(Collectors.toList()),  // 执行代码
          typeFactory ->  // 执行代码
              Arrays.stream(args).map(arg -> arg.type(typeFactory))  // 执行代码
                  .collect(Collectors.toList()),  // 执行代码
          i -> args[i].name(), i -> args[i].optional());  // 执行代码
    }

    static Arg of(String name,  // 执行代码
        Function<RelDataTypeFactory, RelDataType> protoType,  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
        SqlTypeFamily family, boolean optional) {  // 执行代码
      return new Arg() {  // 执行代码
        @Override public String name() {  // 执行代码
          return name;  // 执行代码
        }

        @Override public RelDataType type(RelDataTypeFactory typeFactory) {  // RelDataType：声明关系数据类型，描述表或表达式的类型信息
          return protoType.apply(typeFactory);  // 执行代码
        }

        @Override public SqlTypeFamily family() {  // 执行代码
          return family;  // 执行代码
        }

        @Override public boolean optional() {  // 执行代码
          return optional;  // 执行代码
        }
      };
    }
  }
}
