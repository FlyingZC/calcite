/* // 代码行
 * Licensed to the Apache Software Foundation (ASF) under one or more // JavaDoc注释内容
 * contributor license agreements.  See the NOTICE file distributed with // JavaDoc注释内容
 * this work for additional information regarding copyright ownership. // JavaDoc注释内容
 * The ASF licenses this file to you under the Apache License, Version 2.0 // JavaDoc注释内容
 * (the "License"); you may not use this file except in compliance with // JavaDoc注释内容
 * the License.  You may obtain a copy of the License at // JavaDoc注释内容
 * // JavaDoc注释内容
 * http://www.apache.org/licenses/LICENSE-2.0 // JavaDoc注释内容
 * // JavaDoc注释内容
 * Unless required by applicable law or agreed to in writing, software // JavaDoc注释内容
 * distributed under the License is distributed on an "AS IS" BASIS, // JavaDoc注释内容
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // JavaDoc注释内容
 * See the License for the specific language governing permissions and // JavaDoc注释内容
 * limitations under the License. // JavaDoc注释内容
 */ // JavaDoc注释内容
package org.apache.calcite.test; // 包声明:定义当前类所属的包
 // 空行
import org.apache.calcite.DataContexts; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.clone.CloneSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.generate.RangeTable; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.jdbc.JdbcConvention; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.jdbc.JdbcSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.adapter.jdbc.JdbcTable; // 导入语句:导入所需的类或接口
import org.apache.calcite.avatica.AvaticaConnection; // 导入语句:导入所需的类或接口
import org.apache.calcite.avatica.AvaticaStatement; // 导入语句:导入所需的类或接口
import org.apache.calcite.avatica.Handler; // 导入语句:导入所需的类或接口
import org.apache.calcite.avatica.HandlerImpl; // 导入语句:导入所需的类或接口
import org.apache.calcite.avatica.util.Casing; // 导入语句:导入所需的类或接口
import org.apache.calcite.avatica.util.Quoting; // 导入语句:导入所需的类或接口
import org.apache.calcite.config.CalciteConnectionConfig; // 导入语句:导入所需的类或接口
import org.apache.calcite.config.CalciteConnectionProperty; // 导入语句:导入所需的类或接口
import org.apache.calcite.config.CalciteSystemProperty; // 导入语句:导入所需的类或接口
import org.apache.calcite.config.Lex; // 导入语句:导入所需的类或接口
import org.apache.calcite.config.NullCollation; // 导入语句:导入所需的类或接口
import org.apache.calcite.jdbc.CalciteConnection; // 导入语句:导入所需的类或接口
import org.apache.calcite.jdbc.CalcitePrepare; // 导入语句:导入所需的类或接口
import org.apache.calcite.jdbc.CalciteSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.jdbc.Driver; // 导入语句:导入所需的类或接口
import org.apache.calcite.linq4j.Enumerator; // 导入语句:导入所需的类或接口
import org.apache.calcite.linq4j.Linq4j; // 导入语句:导入所需的类或接口
import org.apache.calcite.linq4j.Ord; // 导入语句:导入所需的类或接口
import org.apache.calcite.linq4j.QueryProvider; // 导入语句:导入所需的类或接口
import org.apache.calcite.linq4j.Queryable; // 导入语句:导入所需的类或接口
import org.apache.calcite.plan.RelOptPlanner; // 导入语句:导入所需的类或接口
import org.apache.calcite.plan.RelOptUtil; // 导入语句:导入所需的类或接口
import org.apache.calcite.prepare.CalcitePrepareImpl; // 导入语句:导入所需的类或接口
import org.apache.calcite.prepare.Prepare; // 导入语句:导入所需的类或接口
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider; // 导入语句:导入所需的类或接口
import org.apache.calcite.rel.rules.CoreRules; // 导入语句:导入所需的类或接口
import org.apache.calcite.rel.type.RelDataType; // 导入语句:导入所需的类或接口
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入语句:导入所需的类或接口
import org.apache.calcite.rel.type.RelProtoDataType; // 导入语句:导入所需的类或接口
import org.apache.calcite.runtime.FlatLists; // 导入语句:导入所需的类或接口
import org.apache.calcite.runtime.Hook; // 导入语句:导入所需的类或接口
import org.apache.calcite.runtime.SqlFunctions; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.Schema; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.SchemaFactory; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.SchemaPlus; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.SchemaVersion; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.Table; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.TableFactory; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.TableMacro; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.Wrapper; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.impl.AbstractSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.impl.AbstractTable; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.impl.DelegatingSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.impl.TableMacroImpl; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.impl.ViewTable; // 导入语句:导入所需的类或接口
import org.apache.calcite.schema.lookup.LikePattern; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.SqlCall; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.SqlDialect; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.SqlKind; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.SqlNode; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.SqlOperator; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.SqlSelect; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.SqlSpecialOperator; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.parser.SqlParser; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.parser.SqlParserPos; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.parser.impl.SqlParserImpl; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.type.SqlTypeName; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入语句:导入所需的类或接口
import org.apache.calcite.sql2rel.SqlToRelConverter.Config; // 导入语句:导入所需的类或接口
import org.apache.calcite.test.schemata.catchall.CatchallSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.test.schemata.foodmart.FoodmartSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.test.schemata.hr.Department; // 导入语句:导入所需的类或接口
import org.apache.calcite.test.schemata.hr.Employee; // 导入语句:导入所需的类或接口
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入语句:导入所需的类或接口
import org.apache.calcite.tools.Program; // 导入语句:导入所需的类或接口
import org.apache.calcite.tools.Programs; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.Bug; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.Holder; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.JsonBuilder; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.Pair; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.Smalls; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.TestUtil; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.TryThreadLocal; // 导入语句:导入所需的类或接口
import org.apache.calcite.util.Util; // 导入语句:导入所需的类或接口
 // 空行
import com.google.common.collect.ImmutableList; // 导入语句:导入所需的类或接口
import com.google.common.collect.LinkedListMultimap; // 导入语句:导入所需的类或接口
import com.google.common.collect.Multimap; // 导入语句:导入所需的类或接口
 // 空行
import org.checkerframework.checker.nullness.qual.Nullable; // 导入语句:导入所需的类或接口
import org.hamcrest.Matcher; // 导入语句:导入所需的类或接口
import org.hamcrest.comparator.ComparatorMatcherBuilder; // 导入语句:导入所需的类或接口
import org.hamcrest.number.OrderingComparison; // 导入语句:导入所需的类或接口
import org.hsqldb.jdbcDriver; // 导入语句:导入所需的类或接口
import org.junit.jupiter.api.Disabled; // 导入语句:导入所需的类或接口
import org.junit.jupiter.api.Test; // 导入语句:导入所需的类或接口
import org.junit.jupiter.params.ParameterizedTest; // 导入语句:导入所需的类或接口
import org.junit.jupiter.params.provider.Arguments; // 导入语句:导入所需的类或接口
import org.junit.jupiter.params.provider.MethodSource; // 导入语句:导入所需的类或接口
 // 空行
import java.io.File; // 导入语句:导入所需的类或接口
import java.io.IOException; // 导入语句:导入所需的类或接口
import java.io.PrintWriter; // 导入语句:导入所需的类或接口
import java.lang.reflect.Method; // 导入语句:导入所需的类或接口
import java.math.BigDecimal; // 导入语句:导入所需的类或接口
import java.sql.Array; // 导入语句:导入所需的类或接口
import java.sql.Connection; // 导入语句:导入所需的类或接口
import java.sql.DatabaseMetaData; // 导入语句:导入所需的类或接口
import java.sql.Date; // 导入语句:导入所需的类或接口
import java.sql.DriverManager; // 导入语句:导入所需的类或接口
import java.sql.DriverPropertyInfo; // 导入语句:导入所需的类或接口
import java.sql.ParameterMetaData; // 导入语句:导入所需的类或接口
import java.sql.PreparedStatement; // 导入语句:导入所需的类或接口
import java.sql.ResultSet; // 导入语句:导入所需的类或接口
import java.sql.ResultSetMetaData; // 导入语句:导入所需的类或接口
import java.sql.SQLException; // 导入语句:导入所需的类或接口
import java.sql.Statement; // 导入语句:导入所需的类或接口
import java.sql.Timestamp; // 导入语句:导入所需的类或接口
import java.sql.Types; // 导入语句:导入所需的类或接口
import java.util.ArrayList; // 导入语句:导入所需的类或接口
import java.util.Arrays; // 导入语句:导入所需的类或接口
import java.util.Calendar; // 导入语句:导入所需的类或接口
import java.util.Collections; // 导入语句:导入所需的类或接口
import java.util.HashMap; // 导入语句:导入所需的类或接口
import java.util.HashSet; // 导入语句:导入所需的类或接口
import java.util.List; // 导入语句:导入所需的类或接口
import java.util.Locale; // 导入语句:导入所需的类或接口
import java.util.Map; // 导入语句:导入所需的类或接口
import java.util.Properties; // 导入语句:导入所需的类或接口
import java.util.Set; // 导入语句:导入所需的类或接口
import java.util.TimeZone; // 导入语句:导入所需的类或接口
import java.util.concurrent.atomic.AtomicInteger; // 导入语句:导入所需的类或接口
import java.util.function.Consumer; // 导入语句:导入所需的类或接口
import java.util.regex.Pattern; // 导入语句:导入所需的类或接口
import java.util.stream.Collectors; // 导入语句:导入所需的类或接口
import java.util.stream.Stream; // 导入语句:导入所需的类或接口
import javax.sql.DataSource; // 导入语句:导入所需的类或接口
 // 空行
import static org.apache.calcite.adapter.enumerable.EnumerableRules.ENUMERABLE_MINUS_RULE; // 导入语句:导入所需的类或接口
import static org.apache.calcite.test.CalciteAssert.checkResult; // 导入语句:导入所需的类或接口
import static org.apache.calcite.test.Matchers.isLinux; // 导入语句:导入所需的类或接口
import static org.apache.calcite.util.Static.RESOURCE; // 导入语句:导入所需的类或接口
 // 空行
import static org.hamcrest.CoreMatchers.containsString; // 导入语句:导入所需的类或接口
import static org.hamcrest.CoreMatchers.hasItem; // 导入语句:导入所需的类或接口
import static org.hamcrest.CoreMatchers.instanceOf; // 导入语句:导入所需的类或接口
import static org.hamcrest.CoreMatchers.is; // 导入语句:导入所需的类或接口
import static org.hamcrest.CoreMatchers.not; // 导入语句:导入所需的类或接口
import static org.hamcrest.CoreMatchers.notNullValue; // 导入语句:导入所需的类或接口
import static org.hamcrest.CoreMatchers.nullValue; // 导入语句:导入所需的类或接口
import static org.hamcrest.CoreMatchers.startsWith; // 导入语句:导入所需的类或接口
import static org.hamcrest.MatcherAssert.assertThat; // 导入语句:导入所需的类或接口
import static org.hamcrest.Matchers.arrayWithSize; // 导入语句:导入所需的类或接口
import static org.hamcrest.Matchers.hasSize; // 导入语句:导入所需的类或接口
import static org.junit.jupiter.api.Assertions.assertArrayEquals; // 导入语句:导入所需的类或接口
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入语句:导入所需的类或接口
import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入语句:导入所需的类或接口
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入语句:导入所需的类或接口
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入语句:导入所需的类或接口
import static org.junit.jupiter.api.Assertions.fail; // 导入语句:导入所需的类或接口
import static org.junit.jupiter.params.provider.Arguments.arguments; // 导入语句:导入所需的类或接口
 // 空行
import static java.util.Objects.requireNonNull; // 导入语句:导入所需的类或接口
 // 空行
/** // JavaDoc注释开始
 * Tests for using Calcite via JDBC. // JavaDoc注释内容
 */ // JavaDoc注释内容
public class JdbcTest { // JdbcTest类:测试通过JDBC使用Calcite的测试类
 // 空行
  public static final ConnectionSpec SCOTT = // SCOTT连接规范:定义SCOTT数据库的连接规范
      requireNonNull( // 代码行
          Util.first(CalciteAssert.DB.scott, // 代码行
              CalciteAssert.DatabaseInstance.HSQLDB.scott)); // 代码行
 // 空行
  public static final String SCOTT_SCHEMA = "     {\n" // SCOTT模式定义:定义SCOTT模式的JSON配置
      + "       type: 'jdbc',\n" // 代码行
      + "       name: 'SCOTT',\n" // 代码行
      + "       jdbcDriver: " + q(SCOTT.driver) + ",\n" // 代码行
      + "       jdbcUser: " + q(SCOTT.username) + ",\n" // 代码行
      + "       jdbcPassword: " + q(SCOTT.password) + ",\n" // 代码行
      + "       jdbcUrl: " + q(SCOTT.url) + ",\n" // 代码行
      + "       jdbcCatalog: " + q(SCOTT.catalog) + ",\n" // 代码行
      + "       jdbcSchema: " + q(SCOTT.schema) + "\n" // 代码行
      + "     }\n"; // 代码行
 // 空行
  public static final String SCOTT_MODEL = "{\n" // SCOTT模型定义:定义SCOTT模型的完整JSON配置
      + "  version: '1.0',\n" // 代码行
      + "  defaultSchema: 'SCOTT',\n" // 代码行
      + "   schemas: [\n" // 代码行
      + SCOTT_SCHEMA // 代码行
      + "   ]\n" // 代码行
      + "}"; // 代码行
 // 空行
  public static final String HR_SCHEMA = "     {\n" // HR模式定义:定义HR模式的JSON配置
      + "       type: 'custom',\n" // 代码行
      + "       name: 'hr',\n" // 代码行
      + "       factory: '" // 代码行
      + ReflectiveSchema.Factory.class.getName() // 代码行
      + "',\n" // 代码行
      + "       operand: {\n" // 代码行
      + "         class: '" + HrSchema.class.getName() + "'\n" // 代码行
      + "       }\n" // 代码行
      + "     }\n"; // 代码行
 // 空行
  public static final String HR_MODEL = "{\n" // HR模型定义:定义HR模型的完整JSON配置
      + "  version: '1.0',\n" // 代码行
      + "  defaultSchema: 'hr',\n" // 代码行
      + "   schemas: [\n" // 代码行
      + HR_SCHEMA // 代码行
      + "   ]\n" // 代码行
      + "}"; // 代码行
 // 空行
  public static final String FOODMART_SCOTT_MODEL = "{\n" // FoodMart+Scott模型定义:定义包含FoodMart和Scott的模型
      + "  version: '1.0',\n" // 代码行
      + "   schemas: [\n" // 代码行
      + FoodmartSchema.FOODMART_SCHEMA // 代码行
      + ",\n" // 代码行
      + SCOTT_SCHEMA // 代码行
      + "   ]\n" // 代码行
      + "}"; // 代码行
 // 空行
  public static final String START_OF_GROUP_DATA = "(values" // 分组测试数据:用于测试分组操作的测试数据
      + "(1,0,1),\n" // 代码行
      + "(2,0,1),\n" // 代码行
      + "(3,1,2),\n" // 代码行
      + "(4,0,3),\n" // 代码行
      + "(5,0,3),\n" // 代码行
      + "(6,0,3),\n" // 代码行
      + "(7,1,4),\n" // 代码行
      + "(8,1,4))\n" // 代码行
      + " as t(rn,val,expected)"; // 代码行
 // 空行
  public static final String FOODMART_SCOTT_CUSTOM_MODEL = "{\n" // 自定义FoodMart+Scott模型:使用自定义schema的模型
      + "  version: '1.0',\n" // 代码行
      + "   schemas: [\n" // 代码行
      + "     {\n" // 代码行
      + "       type: 'custom',\n" // 代码行
      + "       factory: '" // 代码行
      + JdbcCustomSchemaFactory.class.getName() // 代码行
      + "',\n" // 代码行
      + "       name: 'SCOTT'\n" // 代码行
       + "     }\n" // 代码行
      + "   ]\n" // 代码行
      + "}"; // 代码行
 // 空行
 // 空行
  /** // JavaDoc注释开始
   * Tests class for custom JDBC schema. // JavaDoc注释内容
   */ // JavaDoc注释内容
  public static class JdbcCustomSchema extends DelegatingSchema implements Wrapper { // JdbcCustomSchema内部类:自定义JDBC模式的Schema
 // 空行
    public JdbcCustomSchema(SchemaPlus parentSchema, String name) { // 代码行
      super(JdbcSchema.create(parentSchema, name, getDataSource(), SCOTT.catalog, SCOTT.schema)); // 代码行
    } // 代码行
 // 空行
    private static DataSource getDataSource() { // 代码行
      return JdbcSchema.dataSource(SCOTT.url, SCOTT.driver, SCOTT.username, SCOTT.password); // 代码行
    } // 代码行
 // 空行
    @Override public Schema snapshot(SchemaVersion version) { // 注解
      return this; // 代码行
    } // 代码行
 // 空行
    @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) { // 注解
      if (schema instanceof Wrapper) { // 代码行
        return ((Wrapper) schema).unwrap(clazz); // 代码行
      } // 代码行
      return null; // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests class for custom JDBC schema factory. // JavaDoc注释内容
   */ // JavaDoc注释内容
  public static class JdbcCustomSchemaFactory implements SchemaFactory { // JdbcCustomSchemaFactory内部类:自定义JDBC模式的Schema工厂
 // 空行
    @Override public Schema create(SchemaPlus parentSchema, String name, // 注解
        Map<String, Object> operand) { // 代码行
      return new JdbcCustomSchema(parentSchema, name); // 代码行
    } // 代码行
  } // 代码行
  private static String q(@Nullable String s) { // q方法:将字符串转换为带引号的字符串
    return s == null ? "null" : "'" + s + "'"; // 代码行
  } // 代码行
 // 空行
  public static List<Pair<String, String>> getFoodmartQueries() { // 获取Foodmart查询列表:返回Foodmart查询的列表
    return FOODMART_QUERIES; // 代码行
  } // 代码行
 // 空行
  static Stream<String> explainFormats() { // 返回解释格式流:返回支持的EXPLAIN格式流
    return Stream.of("text", "dot"); // 代码行
  } // 代码行
 // 空行
  static Stream<Arguments> disableTrimmingConfigsTestArguments() { // 返回禁用字段修剪配置的测试参数:用于测试字段修剪功能的参数
    /** enableTrimmingByConfig, enableTrimmingByProgram, expectedLogicalPlan. */ // JavaDoc注释开始
    return Stream.of( // 代码行
        arguments(true, true, // 代码行
            "" // 代码行
                + "LogicalProject(name=[$1])\n" // 代码行
                + "  LogicalJoin(condition=[=($0, $2)], joinType=[inner])\n" // 代码行
                + "    LogicalProject(deptno=[$0], name=[$1])\n" // 代码行
                + "      LogicalTableScan(table=[[hr, depts]])\n" // 代码行
                + "    LogicalProject(deptno=[$1])\n" // 代码行
                + "      LogicalTableScan(table=[[hr, emps]])\n" // 代码行
                + ""), // 代码行
        arguments(true, false, // 代码行
            "" // 代码行
                + "LogicalProject(name=[$1])\n" // 代码行
                + "  LogicalJoin(condition=[=($0, $2)], joinType=[inner])\n" // 代码行
                + "    LogicalProject(deptno=[$0], name=[$1])\n" // 代码行
                + "      LogicalTableScan(table=[[hr, depts]])\n" // 代码行
                + "    LogicalProject(deptno=[$1])\n" // 代码行
                + "      LogicalTableScan(table=[[hr, emps]])\n" // 代码行
                + ""), // 代码行
        arguments(false, true, // 代码行
            "" // 代码行
                + "LogicalProject(name=[$1])\n" // 代码行
                + "  LogicalJoin(condition=[=($0, $2)], joinType=[inner])\n" // 代码行
                + "    LogicalProject(deptno=[$0], name=[$1])\n" // 代码行
                + "      LogicalTableScan(table=[[hr, depts]])\n" // 代码行
                + "    LogicalProject(deptno=[$1])\n" // 代码行
                + "      LogicalTableScan(table=[[hr, emps]])\n" // 代码行
                + ""), // 代码行
        arguments(false, false, // 代码行
            "" // 代码行
                + "LogicalProject(name=[$1])\n" // 代码行
                + "  LogicalJoin(condition=[=($0, $5)], joinType=[inner])\n" // 代码行
                + "    LogicalTableScan(table=[[hr, depts]])\n" // 代码行
                + "    LogicalTableScan(table=[[hr, emps]])\n" // 代码行
                + "")); // 代码行
  } // 代码行
 // 空行
  /** Runs a task (such as a test) with and without expansion. */ // JavaDoc注释开始
  static void forEachExpand(Runnable r) { // 遍历执行任务:在展开和非展开两种模式下执行任务
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_EXPAND.push(false)) { // 代码行
      r.run(); // 代码行
    } // 代码行
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_EXPAND.push(true)) { // 代码行
      r.run(); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests a modifiable view. */ // JavaDoc注释开始
  @Test void testModelWithModifiableView() throws Exception { // 测试方法:测试ModelWithModifiableView功能
    final List<Employee> employees = new ArrayList<>(); // 代码行
    employees.add(new Employee(135, 10, "Simon", 56.7f, null)); // 代码行
    try (TryThreadLocal.Memo ignore = // 代码行
             EmpDeptTableFactory.THREAD_COLLECTION.push(employees)) { // 代码行
      final CalciteAssert.AssertThat with = // 代码行
          modelWithView("select \"name\", \"empid\" as e, \"salary\" " // 代码行
                  + "from \"MUTABLE_EMPLOYEES\" where \"deptno\" = 10", // 代码行
              null); // 代码行
      with.query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .returns("name=Simon\n"); // 代码行
      with.doWithConnection(connection -> { // 代码行
        try { // 代码行
          final Statement statement = connection.createStatement(); // 代码行
          ResultSet resultSet = // 代码行
              statement.executeQuery("explain plan for\n" // 代码行
                  + "insert into \"adhoc\".V\n" // 代码行
                  + "values ('Fred', 56, 123.4)"); // 代码行
          assertThat(resultSet.next(), is(true)); // 代码行
          final String expected = "" // 代码行
              + "EnumerableTableModify(table=[[adhoc, MUTABLE_EMPLOYEES]], " // 代码行
              + "operation=[INSERT], flattened=[false])\n" // 代码行
              + "  EnumerableCalc(expr#0..2=[{inputs}], " // 代码行
              + "expr#3=[CAST($t1):JavaType(int) NOT NULL], expr#4=[10], " // 代码行
              + "expr#5=[CAST($t0):JavaType(class java.lang.String)], " // 代码行
              + "expr#6=[CAST($t2):JavaType(float) NOT NULL], " // 代码行
              + "expr#7=[null:JavaType(class java.lang.Integer)], " // 代码行
              + "empid=[$t3], deptno=[$t4], name=[$t5], salary=[$t6], " // 代码行
              + "commission=[$t7])\n" // 代码行
              + "    EnumerableValues(tuples=[[{ 'Fred', 56, 123.4000015258789E0 }]])\n"; // 代码行
          assertThat(resultSet.getString(1), isLinux(expected)); // 代码行
 // 空行
          // With named columns // 单行注释
          resultSet = // 代码行
              statement.executeQuery("explain plan for\n" // 代码行
                  + "insert into \"adhoc\".V (\"name\", e, \"salary\")\n" // 代码行
                  + "values ('Fred', 56, 123.4)"); // 代码行
          assertThat(resultSet.next(), is(true)); // 代码行
 // 空行
          // With named columns, in different order // 单行注释
          resultSet = // 代码行
              statement.executeQuery("explain plan for\n" // 代码行
                  + "insert into \"adhoc\".V (e, \"salary\", \"name\")\n" // 代码行
                  + "values (56, 123.4, 'Fred')"); // 代码行
          assertThat(resultSet.next(), is(true)); // 代码行
 // 空行
          // Mis-named column // 单行注释
          try { // 代码行
            final PreparedStatement s = // 代码行
                connection.prepareStatement("explain plan for\n" // 代码行
                    + "insert into \"adhoc\".V (empno, \"salary\", \"name\")\n" // 代码行
                    + "values (56, 123.4, 'Fred')"); // 代码行
            fail("expected error, got " + s); // 代码行
          } catch (SQLException e) { // 代码行
            assertThat(e.getMessage(), // 代码行
                startsWith("Error while preparing statement")); // 代码行
          } // 代码行
 // 空行
          // Fail to provide mandatory column // 单行注释
          try { // 代码行
            final PreparedStatement s = // 代码行
                connection.prepareStatement("explain plan for\n" // 代码行
                    + "insert into \"adhoc\".V (e, name)\n" // 代码行
                    + "values (56, 'Fred')"); // 代码行
            fail("expected error, got " + s); // 代码行
          } catch (SQLException e) { // 代码行
            assertThat(e.getMessage(), // 代码行
                startsWith("Error while preparing statement")); // 代码行
          } // 代码行
 // 空行
          statement.close(); // 代码行
        } catch (SQLException e) { // 代码行
          throw TestUtil.rethrow(e); // 代码行
        } // 代码行
      }); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests a few cases where modifiable views are invalid. */ // JavaDoc注释开始
  @Test void testModelWithInvalidModifiableView() { // 测试方法:测试ModelWithInvalidModifiableView功能
    final List<Employee> employees = new ArrayList<>(); // 代码行
    employees.add(new Employee(135, 10, "Simon", 56.7f, null)); // 代码行
    try (TryThreadLocal.Memo ignore = // 代码行
             EmpDeptTableFactory.THREAD_COLLECTION.push(employees)) { // 代码行
      Util.discard(RESOURCE.noValueSuppliedForViewColumn("column", "table")); // 代码行
      modelWithView("select \"name\", \"empid\" as e, \"salary\" " // 代码行
              + "from \"MUTABLE_EMPLOYEES\" where \"commission\" = 10", // 代码行
          true) // 代码行
          .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .throws_( // 代码行
              "View is not modifiable. No value is supplied for NOT NULL " // 代码行
                  + "column 'deptno' of base table 'MUTABLE_EMPLOYEES'"); // 代码行
 // 空行
      // no error if we do not claim that the view is modifiable // 单行注释
      modelWithView( // 代码行
          "select \"name\", \"empid\" as e, \"salary\" " // 代码行
              + "from \"MUTABLE_EMPLOYEES\" where \"commission\" = 10", null) // 代码行
          .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .runs(); // 代码行
 // 空行
      modelWithView("select \"name\", \"empid\" as e, \"salary\" " // 代码行
              + "from \"MUTABLE_EMPLOYEES\" where \"deptno\" IN (10, 20)", // 代码行
          true) // 代码行
          .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .throws_( // 代码行
              "Modifiable view must be predicated only on equality expressions"); // 代码行
 // 空行
      // Deduce "deptno = 10" from the constraint, and add a further // 单行注释
      // condition "deptno < 20 OR commission > 1000". // 单行注释
      modelWithView("select \"name\", \"empid\" as e, \"salary\" " // 代码行
              + "from \"MUTABLE_EMPLOYEES\"\n" // 代码行
              + "where \"deptno\" = 10 AND (\"deptno\" < 20 OR \"commission\" > 1000)", // 代码行
          true) // 代码行
          .query("insert into \"adhoc\".v values ('n',1,2)") // 代码行
          .throws_( // 代码行
              "Modifiable view must be predicated only on equality expressions"); // 代码行
      modelWithView("select \"name\", \"empid\" as e, \"salary\" " // 代码行
              + "from \"MUTABLE_EMPLOYEES\"\n" // 代码行
              + "where \"deptno\" = 10 AND (\"deptno\" > 20 AND \"commission\" > 1000)", // 代码行
          true) // 代码行
          .query("insert into \"adhoc\".v values ('n',1,2)") // 代码行
          .throws_( // 代码行
              "Modifiable view must be predicated only on equality expressions"); // 代码行
 // 空行
      modelWithView( // 代码行
          "select \"name\", \"empid\" as e, \"salary\" " // 代码行
              + "from \"MUTABLE_EMPLOYEES\"\n" // 代码行
              + "where \"commission\" = 100 AND \"deptno\" = 20", // 代码行
          true) // 代码行
          .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .runs(); // 代码行
 // 空行
      modelWithView( // 代码行
          "select \"name\", \"empid\" as e, \"salary\", \"empid\" + 3 as e3, 1 as uno\n" // 代码行
              + "from \"MUTABLE_EMPLOYEES\"\n" // 代码行
              + "where \"commission\" = 100 AND \"deptno\" = 20", // 代码行
          true) // 代码行
          .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .runs(); // 代码行
 // 空行
      Util.discard(RESOURCE.moreThanOneMappedColumn("column", "table")); // 代码行
      modelWithView( // 代码行
          "select \"name\", \"empid\" as e, \"salary\", \"name\" as n2 " // 代码行
              + "from \"MUTABLE_EMPLOYEES\" where \"deptno\" IN (10, 20)", // 代码行
          true) // 代码行
          .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .throws_( // 代码行
              "View is not modifiable. More than one expression maps to " // 代码行
              + "column 'name' of base table 'MUTABLE_EMPLOYEES'"); // 代码行
 // 空行
      // no error if we do not claim that the view is modifiable // 单行注释
      modelWithView( // 代码行
          "select \"name\", \"empid\" as e, \"salary\", \"name\" as n2 " // 代码行
              + "from \"MUTABLE_EMPLOYEES\" where \"deptno\" IN (10, 20)", // 代码行
          null) // 代码行
          .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
          .runs(); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Adds table macro for connection, with catalog named "s" // JavaDoc注释内容
   * and the method reflection name as the name of the macro. // JavaDoc注释内容
   */ // JavaDoc注释内容
  private void addTableMacro(Connection connection, Method method) throws SQLException { // addTableMacro方法:为连接添加表宏
    CalciteConnection calciteConnection = // 代码行
        connection.unwrap(CalciteConnection.class); // 代码行
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 代码行
    SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 代码行
    final TableMacro tableMacro = // 代码行
        requireNonNull(TableMacroImpl.create(method)); // 代码行
    schema.add(method.getName(), tableMacro); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests a relation that is accessed via method syntax. // JavaDoc注释内容
   * // JavaDoc注释内容
   * <p>The function ({@link Smalls#view(String)} has a return type // JavaDoc注释内容
   * {@link Table} and the actual returned value implements // JavaDoc注释内容
   * {@link org.apache.calcite.schema.TranslatableTable}. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testTableMacro() throws SQLException { // 测试方法:测试TableMacro功能
    Connection connection = // 代码行
        DriverManager.getConnection("jdbc:calcite:"); // 代码行
    addTableMacro(connection, Smalls.VIEW_METHOD); // 代码行
    ResultSet resultSet = connection.createStatement().executeQuery("select *\n" // 代码行
        + "from table(\"s\".\"view\"('(10), (20)')) as t(n)\n" // 代码行
        + "where n < 15"); // 代码行
    // The call to "View('(10), (2)')" expands to 'values (1), (3), (10), (20)'. // 单行注释
    assertThat(CalciteAssert.toString(resultSet), // 代码行
        is("N=1\n" // 代码行
            + "N=3\n" // 代码行
            + "N=10\n")); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Table macro that takes a MAP as a parameter. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-588">[CALCITE-588] // JavaDoc注释内容
   * Allow TableMacro to consume Maps and Collections</a>. */ // JavaDoc注释内容
  @Test void testTableMacroMap() throws SQLException { // 测试方法:测试TableMacroMap功能
    Connection connection = // 代码行
        DriverManager.getConnection("jdbc:calcite:"); // 代码行
    addTableMacro(connection, Smalls.STR_METHOD); // 代码行
    ResultSet resultSet = connection.createStatement().executeQuery("select *\n" // 代码行
        + "from table(\"s\".\"str\"(MAP['a', 1, 'baz', 2],\n" // 代码行
        + "                         ARRAY[3, 4, CAST(null AS INTEGER)])) as t(n)"); // 代码行
    // The call to "View('(10), (2)')" expands to 'values (1), (3), (10), (20)'. // 单行注释
    assertThat(CalciteAssert.toString(resultSet), // 代码行
        is("N={'a'=1, 'baz'=2}\n" // 代码行
            + "N=[3, 4, null]    \n")); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4590">[CALCITE-4590] // JavaDoc注释内容
   * Incorrect query result with fixed-length string</a>. */ // JavaDoc注释内容
  @Test void testTrimLiteral() { // 测试方法:测试TrimLiteral功能
    CalciteAssert.that() // 代码行
        .query("with t(x, y) as (values (1, 'a'), (2, 'abc'))" // 代码行
            + "select * from t where y = 'a'") // 代码行
        .returns("X=1; Y=a  \n"); // 代码行
    CalciteAssert.that() // 代码行
        .query("with t(x, y) as (values (1, 'a'), (2, 'abc'))" // 代码行
            + "select * from t where y = 'a' or y = 'abc'") // 代码行
        .returns("X=1; Y=a  \n" // 代码行
            + "X=2; Y=abc\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3423">[CALCITE-3423] // JavaDoc注释内容
   * Support using CAST operation and BOOLEAN type value in table macro</a>. */ // JavaDoc注释内容
  @Test void testTableMacroWithCastOrBoolean() throws SQLException { // 测试方法:测试TableMacroWithCastOrBoolean功能
    Connection connection = // 代码行
        DriverManager.getConnection("jdbc:calcite:"); // 代码行
    addTableMacro(connection, Smalls.STR_METHOD); // 代码行
    // check for cast // 单行注释
    ResultSet resultSet = // 代码行
        connection.createStatement().executeQuery("select *\n" // 代码行
            + "from table(\"s\".\"str\"(MAP['a', 1, 'baz', 2], cast(1 as bigint))) as t(n)"); // 代码行
    assertThat(CalciteAssert.toString(resultSet), // 代码行
        is("N={'a'=1, 'baz'=2}\n" // 代码行
            + "N=1               \n")); // 代码行
    // check for Boolean type // 单行注释
    resultSet = // 代码行
        connection.createStatement().executeQuery("select *\n" // 代码行
            + "from table(\"s\".\"str\"(MAP['a', 1, 'baz', 2], true)) as t(n)"); // 代码行
    assertThat(CalciteAssert.toString(resultSet), // 代码行
        is("N={'a'=1, 'baz'=2}\n" // 代码行
            + "N=true            \n")); // 代码行
    // check for nested cast // 单行注释
    resultSet = // 代码行
        connection.createStatement().executeQuery("select *\n" // 代码行
            + "from table(\"s\".\"str\"(MAP['a', 1, 'baz', 2]," // 代码行
            + "cast(cast(1 as int) as varchar(1)))) as t(n)"); // 代码行
    assertThat(CalciteAssert.toString(resultSet), // 代码行
        is("N={'a'=1, 'baz'=2}\n" // 代码行
            + "N=1               \n")); // 代码行
 // 空行
    resultSet = // 代码行
        connection.createStatement().executeQuery("select *\n" // 代码行
            + "from table(\"s\".\"str\"(MAP['a', 1, 'baz', 2]," // 代码行
            + "cast(cast(cast('2019-10-18 10:35:23' as TIMESTAMP) as BIGINT) as VARCHAR))) as t(n)"); // 代码行
    assertThat(CalciteAssert.toString(resultSet), // 代码行
        is("N={'a'=1, 'baz'=2}     \n" // 代码行
            + "N='2019-10-18 10:35:23'\n")); // 代码行
 // 空行
    // check for implicit type coercion // 单行注释
    addTableMacro(connection, Smalls.VIEW_METHOD); // 代码行
    resultSet = // 代码行
        connection.createStatement().executeQuery("select *\n" // 代码行
            + "from table(\"s\".\"view\"(5)) as t(n)"); // 代码行
    assertThat(CalciteAssert.toString(resultSet), // 代码行
        is("N=1\n" // 代码行
            + "N=3\n" // 代码行
            + "N=5\n")); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Tests a table macro with named and optional parameters. */ // JavaDoc注释开始
  @Test void testTableMacroWithNamedParameters() { // 测试方法:测试TableMacroWithNamedParameters功能
    // View(String r optional, String s, int t optional) // 单行注释
    final CalciteAssert.AssertThat with = // 代码行
        assertWithMacro(Smalls.TableMacroFunctionWithNamedParameters.class, // 代码行
            Smalls.AnotherTableMacroFunctionWithNamedParameters.class); // 代码行
    with.query("select * from table(\"adhoc\".\"View\"('(5)'))") // 代码行
        .throws_("No match found for function signature View(<CHARACTER>)"); // 代码行
    final String expected1 = "c=1\n" // 代码行
        + "c=3\n" // 代码行
        + "c=5\n" // 代码行
        + "c=6\n"; // 代码行
    with.query("select * from table(\"adhoc\".\"View\"('5', '6'))") // 代码行
        .returns(expected1); // 代码行
    final String expected2 = "c=1\n" // 代码行
        + "c=3\n" // 代码行
        + "c=5\n" // 代码行
        + "c=6\n"; // 代码行
    with.query("select * from table(\"adhoc\".\"View\"(r->'5', s->'6'))") // 代码行
        .returns(expected2); // 代码行
    with.query("select * from table(\"adhoc\".\"View\"(t->'5', t->'6'))") // 代码行
        .throws_("Duplicate argument name 'T'"); // 代码行
    final String expected3 = "c=1\n" // 代码行
        + "c=3\n" // 代码行
        + "c=6\n" // 代码行
        + "c=5\n"; // 代码行
    // implicit type coercion // 单行注释
    with.query("select * from table(\"adhoc\".\"View\"(t->'5', s->'6'))") // 代码行
        .returns(expected3); // 代码行
    with.query("select * from table(\"adhoc\".\"View\"(t->5, s->'6'))") // 代码行
        .returns(expected3); // 代码行
    with.query("select * from table(\"adhoc\".\"View\"(s->'6', t->5))") // 代码行
        .returns(expected3); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains a table // JavaDoc注释开始
   * macro. */ // JavaDoc注释内容
  @Test void testTableMacroInModel() { // 测试方法:测试TableMacroInModel功能
    checkTableMacroInModel(Smalls.TableMacroFunction.class); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains a table // JavaDoc注释开始
   * macro defined as a static method. */ // JavaDoc注释内容
  @Test void testStaticTableMacroInModel() { // 测试方法:测试StaticTableMacroInModel功能
    checkTableMacroInModel(Smalls.StaticTableMacroFunction.class); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains a table // JavaDoc注释开始
   * function. */ // JavaDoc注释内容
  @Test void testTableFunctionInModel() { // 测试方法:测试TableFunctionInModel功能
    checkTableFunctionInModel(Smalls.MyTableFunction.class); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains a table // JavaDoc注释开始
   * function defined as a static method. */ // JavaDoc注释内容
  @Test void testStaticTableFunctionInModel() { // 测试方法:测试StaticTableFunctionInModel功能
    checkTableFunctionInModel(Smalls.TestStaticTableFunction.class); // 代码行
  } // 代码行
 // 空行
  private CalciteAssert.AssertThat assertWithMacro(Class<?>... clazz) { // assertWithMacro方法:创建带宏的测试断言
    String delimiter = "" // 代码行
        + "'\n" // 代码行
        + "         },\n" // 代码行
        + "         {\n" // 代码行
        + "           name: 'View',\n" // 代码行
        + "           className: '"; // 代码行
    String functions = Arrays.stream(clazz) // 代码行
        .map(Class::getName) // 代码行
        .collect(Collectors.joining(delimiter)); // 代码行
 // 空行
    return CalciteAssert.model("{\n" // 代码行
        + "  version: '1.0',\n" // 代码行
        + "   schemas: [\n" // 代码行
        + "     {\n" // 代码行
        + "       name: 'adhoc',\n" // 代码行
        + "       functions: [\n" // 代码行
        + "         {\n" // 代码行
        + "           name: 'View',\n" // 代码行
        + "           className: '" // 代码行
        + functions // 代码行
        + "'\n" // 代码行
        + "         }\n" // 代码行
        + "       ]\n" // 代码行
        + "     }\n" // 代码行
        + "   ]\n" // 代码行
        + "}"); // 代码行
  } // 代码行
 // 空行
  private void checkTableMacroInModel(Class<?> clazz) { // checkTableMacroInModel方法:检查模型中的表宏
    assertWithMacro(clazz) // 代码行
        .query("select * from table(\"adhoc\".\"View\"('(30)'))") // 代码行
        .returns("" // 代码行
            + "c=1\n" // 代码行
            + "c=3\n" // 代码行
            + "c=30\n"); // 代码行
  } // 代码行
 // 空行
  private void checkTableFunctionInModel(Class<?> clazz) { // checkTableFunctionInModel方法:检查模型中的表函数
    checkTableMacroInModel(clazz); // 代码行
 // 空行
    assertWithMacro(clazz) // 代码行
        .query("select \"a\".\"c\" a, \"b\".\"c\" b\n" // 代码行
            + "  from table(\"adhoc\".\"View\"('(30)')) \"a\",\n" // 代码行
            + " lateral(select *\n" // 代码行
            + "   from table(\"adhoc\".\"View\"('('||\n" // 代码行
            + "          cast(\"a\".\"c\" as varchar(10))||')'))) \"b\"") // 代码行
        .returnsUnordered( // 代码行
            "A=1; B=1", // 代码行
            "A=1; B=3", // 代码行
            "A=1; B=1", // 代码行
            "A=3; B=1", // 代码行
            "A=3; B=3", // 代码行
            "A=3; B=3", // 代码行
            "A=30; B=1", // 代码行
            "A=30; B=3", // 代码行
            "A=30; B=30"); // 代码行
  } // 代码行
 // 空行
  /** Tests {@link org.apache.calcite.avatica.Handler#onConnectionClose} // JavaDoc注释开始
   * and  {@link org.apache.calcite.avatica.Handler#onStatementClose}. */ // JavaDoc注释内容
  @Test void testOnConnectionClose() throws Exception { // 测试方法:测试OnConnectionClose功能
    final int[] closeCount = {0}; // 代码行
    final int[] statementCloseCount = {0}; // 代码行
    final HandlerImpl h = new HandlerImpl() { // 代码行
      @Override public void onConnectionClose(AvaticaConnection connection) { // 注解
        ++closeCount[0]; // 代码行
        throw new RuntimeException(); // 代码行
      } // 代码行
 // 空行
      @Override public void onStatementClose(AvaticaStatement statement) { // 注解
        ++statementCloseCount[0]; // 代码行
        throw new RuntimeException(); // 代码行
      } // 代码行
    }; // 代码行
    try (TryThreadLocal.Memo ignore = // 代码行
             HandlerDriver.HANDLERS.push(h)) { // 代码行
      final HandlerDriver driver = new HandlerDriver(); // 代码行
      CalciteConnection connection = (CalciteConnection) // 代码行
          driver.connect("jdbc:calcite:", new Properties()); // 代码行
      SchemaPlus rootSchema = connection.getRootSchema(); // 代码行
      rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 代码行
      connection.setSchema("hr"); // 代码行
      final Statement statement = connection.createStatement(); // 代码行
      final ResultSet resultSet = // 代码行
          statement.executeQuery("select * from \"emps\""); // 代码行
      assertThat(closeCount[0], is(0)); // 代码行
      assertThat(statementCloseCount[0], is(0)); // 代码行
      resultSet.close(); // 代码行
      try { // 代码行
        resultSet.next(); // 代码行
        fail("resultSet.next() should throw SQLException when closed"); // 代码行
      } catch (SQLException e) { // 代码行
        assertThat(e.getMessage(), containsString("ResultSet closed")); // 代码行
      } // 代码行
      assertThat(closeCount[0], is(0)); // 代码行
      assertThat(statementCloseCount[0], is(0)); // 代码行
 // 空行
      // Close statement. It throws SQLException, but statement is still closed. // 单行注释
      try { // 代码行
        statement.close(); // 代码行
        fail("expecting error"); // 代码行
      } catch (SQLException e) { // 代码行
        // ok // 单行注释
      } // 代码行
      assertThat(closeCount[0], is(0)); // 代码行
      assertThat(statementCloseCount[0], is(1)); // 代码行
 // 空行
      // Close connection. It throws SQLException, but connection is still closed. // 单行注释
      try { // 代码行
        connection.close(); // 代码行
        fail("expecting error"); // 代码行
      } catch (SQLException e) { // 代码行
        // ok // 单行注释
      } // 代码行
      assertThat(closeCount[0], is(1)); // 代码行
      assertThat(statementCloseCount[0], is(1)); // 代码行
 // 空行
      // Close a closed connection. Handler is not called again. // 单行注释
      connection.close(); // 代码行
      assertThat(closeCount[0], is(1)); // 代码行
      assertThat(statementCloseCount[0], is(1)); // 代码行
 // 空行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests {@link java.sql.Statement}.{@code closeOnCompletion()}. */ // JavaDoc注释开始
  @Test void testStatementCloseOnCompletion() throws Exception { // 测试方法:测试StatementCloseOnCompletion功能
    String javaVersion = System.getProperty("java.version"); // 代码行
    if (javaVersion.compareTo("1.7") < 0) { // 代码行
      // Statement.closeOnCompletion was introduced in JDK 1.7. // 单行注释
      return; // 代码行
    } // 代码行
    final Driver driver = new Driver(); // 代码行
    CalciteConnection connection = (CalciteConnection) // 代码行
        driver.connect("jdbc:calcite:", new Properties()); // 代码行
    SchemaPlus rootSchema = connection.getRootSchema(); // 代码行
    rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 代码行
    connection.setSchema("hr"); // 代码行
    final Statement statement = connection.createStatement(); // 代码行
    assertFalse((Boolean) CalciteAssert.call(statement, "isCloseOnCompletion")); // 代码行
    CalciteAssert.call(statement, "closeOnCompletion"); // 代码行
    assertTrue((Boolean) CalciteAssert.call(statement, "isCloseOnCompletion")); // 代码行
    final ResultSet resultSet = // 代码行
        statement.executeQuery("select * from \"emps\""); // 代码行
 // 空行
    assertFalse(resultSet.isClosed()); // 代码行
    assertFalse(statement.isClosed()); // 代码行
    assertFalse(connection.isClosed()); // 代码行
 // 空行
    // when result set is closed, statement is closed automatically // 单行注释
    resultSet.close(); // 代码行
    assertTrue(resultSet.isClosed()); // 代码行
    assertTrue(statement.isClosed()); // 代码行
    assertFalse(connection.isClosed()); // 代码行
 // 空行
    connection.close(); // 代码行
    assertTrue(resultSet.isClosed()); // 代码行
    assertTrue(statement.isClosed()); // 代码行
    assertTrue(connection.isClosed()); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2071">[CALCITE-2071] // JavaDoc注释内容
   * Query with IN and OR in WHERE clause returns wrong result</a>. // JavaDoc注释内容
   * More cases in sub-query.iq. */ // JavaDoc注释内容
  @Test void testWhereInOr() { // 测试方法:测试WhereInOr功能
    final String sql = "select \"empid\"\n" // 代码行
        + "from \"hr\".\"emps\" t\n" // 代码行
        + "where (\"empid\" in (select \"empid\" from \"hr\".\"emps\")\n" // 代码行
        + "    or \"empid\" in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,\n" // 代码行
        + "        12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25))\n" // 代码行
        + "and \"empid\" in (100, 200, 150)"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .returnsUnordered("empid=100", // 代码行
            "empid=200", // 代码行
            "empid=150"); // 代码行
  } // 代码行
 // 空行
  /** Tests that a driver can be extended with its own parser and can execute // JavaDoc注释开始
   * its own flavor of DDL. */ // JavaDoc注释内容
  @Test void testMockDdl() { // 测试方法:测试MockDdl功能
    final AtomicInteger counter = new AtomicInteger(); // 代码行
 // 空行
    // Raw MockDdlDriver does not implement commit. // 单行注释
    checkMockDdl(counter, false, new MockDdlDriver()); // 代码行
 // 空行
    // MockDdlDriver implements commit if we have supplied a // 单行注释
    // prepare-factory to do so. // 单行注释
    checkMockDdl(counter, true, // 代码行
        new MockDdlDriver() // 代码行
            .withPrepareFactory(() -> new CountingPrepare(counter))); // 代码行
 // 空行
    // Raw MockDdlDriver2 does not implement commit. // 单行注释
    final MockDdlDriver2 driver2 = new MockDdlDriver2(counter); // 代码行
    checkMockDdl(counter, false, driver2); // 代码行
 // 空行
    // MockDdlDriver2 implements commit if we have supplied a // 单行注释
    // prepare-factory to do so. // 单行注释
    checkMockDdl(counter, true, // 代码行
        driver2.withPrepareFactory(() -> new CountingPrepare(counter))); // 代码行
 // 空行
    // MockDdlDriver2 implements commit if we override its createPrepare // 单行注释
    // method. // 单行注释
    checkMockDdl(counter, true, // 代码行
        new MockDdlDriver2(counter) { // 代码行
          @Override public CalcitePrepare createPrepare() { // 注解
            return new CountingPrepare(counter); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  static void checkMockDdl(AtomicInteger counter, boolean hasCommit, // 代码行
      Driver driver) { // 代码行
    try (Connection connection = // 代码行
             driver.connect("jdbc:calcite:", new Properties()); // 代码行
         Statement statement = connection.createStatement()) { // 代码行
      final int original = counter.get(); // 代码行
      if (hasCommit) { // 代码行
        int rowCount = statement.executeUpdate("COMMIT"); // 代码行
        assertThat(rowCount, is(0)); // 代码行
        assertThat(counter.get() - original, is(1)); // 代码行
      } else { // 代码行
        assertThrows(SQLException.class, // 代码行
            () -> statement.executeUpdate("COMMIT")); // 代码行
      } // 代码行
    } catch (SQLException e) { // 代码行
      throw new AssertionError(e); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void testCustomValidator() { // 测试方法:测试CustomValidator功能
    final Driver driver = new MockDdlDriver().withPrepareFactory(MockPrepareImpl::new); // 代码行
    assertThat(driver.createPrepare().getClass(), is(MockPrepareImpl.class)); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * The example in the README. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testReadme() throws SQLException { // 测试方法:测试Readme功能
    Properties info = new Properties(); // 代码行
    info.setProperty("lex", "JAVA"); // 代码行
    Connection connection = DriverManager.getConnection("jdbc:calcite:", info); // 代码行
    CalciteConnection calciteConnection = // 代码行
        connection.unwrap(CalciteConnection.class); // 代码行
    final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 代码行
    rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 代码行
    Statement statement = calciteConnection.createStatement(); // 代码行
    ResultSet resultSet = // 代码行
        statement.executeQuery("select d.deptno, min(e.empid)\n" // 代码行
            + "from hr.emps as e\n" // 代码行
            + "join hr.depts as d\n" // 代码行
            + "  on e.deptno = d.deptno\n" // 代码行
            + "group by d.deptno\n" // 代码行
            + "having count(*) > 1"); // 代码行
    final String s = CalciteAssert.toString(resultSet); // 代码行
    assertThat(s, notNullValue()); // 代码行
    resultSet.close(); // 代码行
    statement.close(); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Test for {@link Driver#getPropertyInfo(String, Properties)}. */ // JavaDoc注释开始
  @Test void testConnectionProperties() throws SQLException { // 测试方法:测试ConnectionProperties功能
    java.sql.Driver driver = DriverManager.getDriver("jdbc:calcite:"); // 代码行
    final DriverPropertyInfo[] propertyInfo = // 代码行
        driver.getPropertyInfo("jdbc:calcite:", new Properties()); // 代码行
    final Set<String> names = new HashSet<>(); // 代码行
    for (DriverPropertyInfo info : propertyInfo) { // 代码行
      names.add(info.name); // 代码行
    } // 代码行
    assertTrue(names.contains("SCHEMA")); // 代码行
    assertTrue(names.contains("TIME_ZONE")); // 代码行
    assertTrue(names.contains("MATERIALIZATIONS_ENABLED")); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Make sure that the properties look sane. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testVersion() throws SQLException { // 测试方法:测试Version功能
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 代码行
    CalciteConnection calciteConnection = // 代码行
        connection.unwrap(CalciteConnection.class); // 代码行
    final DatabaseMetaData metaData = calciteConnection.getMetaData(); // 代码行
    assertThat(metaData.getDriverName(), is("Calcite JDBC Driver")); // 代码行
 // 空行
    final String driverVersion = metaData.getDriverVersion(); // 代码行
    final int driverMajor = metaData.getDriverMajorVersion(); // 代码行
    final int driverMinor = metaData.getDriverMinorVersion(); // 代码行
    assertThat(driverMajor, is(1)); // 代码行
    assertThat(driverMinor, is(40)); // 代码行
 // 空行
    assertThat(metaData.getDatabaseProductName(), is("Calcite")); // 代码行
    final String databaseVersion = // 代码行
        metaData.getDatabaseProductVersion(); // 代码行
    final int databaseMajor = metaData.getDatabaseMajorVersion(); // 代码行
    assertThat(databaseMajor, is(driverMajor)); // 代码行
    final int databaseMinor = metaData.getDatabaseMinorVersion(); // 代码行
    assertThat(databaseMinor, is(driverMinor)); // 代码行
 // 空行
    // Check how version is composed of major and minor version. Note that // 单行注释
    // version is stored in pom.xml; major and minor version are // 单行注释
    // stored in org-apache-calcite-jdbc.properties, but derived from // 单行注释
    // version.major and version.minor in pom.xml. // 单行注释
    // // 单行注释
    // We are more permissive for snapshots. // 单行注释
    // For instance, we allow 1.4.0-SNAPSHOT to match {major=1, minor=3}. // 单行注释
    // Previously, this test would break the first build after a release. // 单行注释
    assertTrue(driverVersion.startsWith(driverMajor + ".")); // 代码行
    assertTrue(driverVersion.split("\\.").length >= 2); // 代码行
    assertTrue(driverVersion.equals(mm(driverMajor, driverMinor)) // 代码行
        || driverVersion.startsWith(mm(driverMajor, driverMinor) + ".") // 代码行
        || driverVersion.startsWith(mm(driverMajor, driverMinor) + "-") // 代码行
        || driverVersion.endsWith("-SNAPSHOT") // 代码行
            && driverVersion.startsWith(mm(driverMajor, driverMinor + 1))); // 代码行
 // 空行
    assertTrue(databaseVersion.startsWith("1.")); // 代码行
    assertTrue(databaseVersion.split("\\.").length >= 2); // 代码行
    assertTrue(databaseVersion.equals(mm(databaseMajor, databaseMinor)) // 代码行
        || databaseVersion.startsWith(mm(databaseMajor, databaseMinor) + ".") // 代码行
        || databaseVersion.startsWith(mm(databaseMajor, databaseMinor) + "-") // 代码行
        || databaseVersion.endsWith("-SNAPSHOT") // 代码行
            && databaseVersion.startsWith(mm(driverMajor, driverMinor + 1))); // 代码行
 // 空行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  private String mm(int majorVersion, int minorVersion) { // 代码行
    return majorVersion + "." + minorVersion; // 代码行
  } // 代码行
 // 空行
  /** Tests driver's implementation of {@link DatabaseMetaData#getColumns}. */ // JavaDoc注释开始
  @Test void testMetaDataColumns() throws SQLException { // 测试方法:测试MetaDataColumns功能
    Connection connection = CalciteAssert // 代码行
        .that(CalciteAssert.Config.REGULAR).connect(); // 代码行
    DatabaseMetaData metaData = connection.getMetaData(); // 代码行
    ResultSet resultSet = metaData.getColumns(null, null, null, null); // 代码行
    assertTrue(resultSet.next()); // there's something // 代码行
    String name = resultSet.getString(4); // 代码行
    int type = resultSet.getInt(5); // 代码行
    String typeName = resultSet.getString(6); // 代码行
    int columnSize = resultSet.getInt(7); // 代码行
    int decimalDigits = resultSet.getInt(9); // 代码行
    int numPrecRadix = resultSet.getInt(10); // 代码行
    int charOctetLength = resultSet.getInt(16); // 代码行
    String isNullable = resultSet.getString(18); // 代码行
    resultSet.close(); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Tests driver's implementation of {@link DatabaseMetaData#getPrimaryKeys}. // JavaDoc注释开始
   * It is empty but it should still have column definitions. */ // JavaDoc注释内容
  @Test void testMetaDataPrimaryKeys() throws SQLException { // 测试方法:测试MetaDataPrimaryKeys功能
    Connection connection = CalciteAssert // 代码行
        .that(CalciteAssert.Config.REGULAR).connect(); // 代码行
    DatabaseMetaData metaData = connection.getMetaData(); // 代码行
    ResultSet resultSet = metaData.getPrimaryKeys(null, null, null); // 代码行
    assertFalse(resultSet.next()); // catalog never contains primary keys // 代码行
    ResultSetMetaData resultSetMetaData = resultSet.getMetaData(); // 代码行
    assertThat(resultSetMetaData.getColumnCount(), is(6)); // 代码行
    assertThat(resultSetMetaData.getColumnName(1), is("TABLE_CAT")); // 代码行
    assertThat(resultSetMetaData.getColumnType(1), is(Types.VARCHAR)); // 代码行
    assertThat(resultSetMetaData.getColumnName(6), is("PK_NAME")); // 代码行
    resultSet.close(); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Unit test for // JavaDoc注释开始
   * {@link LikePattern#likeToRegex(org.apache.calcite.avatica.Meta.Pat)}. */ // JavaDoc注释内容
  @Test void testLikeToRegex() { // 测试方法:测试LikeToRegex功能
    checkLikeToRegex(true, "%", "abc"); // 代码行
    checkLikeToRegex(true, "abc", "abc"); // 代码行
    checkLikeToRegex(false, "abc", "abcd"); // trailing char fails match // 代码行
    checkLikeToRegex(false, "abc", "0abc"); // leading char fails match // 代码行
    checkLikeToRegex(false, "abc", "aBc"); // case-sensitive match // 代码行
    checkLikeToRegex(true, "a[b]c", "a[b]c"); // nothing special about brackets // 代码行
    checkLikeToRegex(true, "a$c", "a$c"); // nothing special about dollar // 代码行
    checkLikeToRegex(false, "a$", "a"); // nothing special about dollar // 代码行
    checkLikeToRegex(true, "a%c", "ac"); // 代码行
    checkLikeToRegex(true, "a%c", "abbbc"); // 代码行
    checkLikeToRegex(false, "a%c", "acccd"); // 代码行
 // 空行
    // escape using back-slash // 单行注释
    checkLikeToRegex(true, "a\\%c", "a%c"); // 代码行
    checkLikeToRegex(false, "a\\%c", "abc"); // 代码行
    checkLikeToRegex(false, "a\\%c", "a\\%c"); // 代码行
 // 空行
    // multiple wild-cards // 单行注释
    checkLikeToRegex(true, "a%c%d", "abcdaaad"); // 代码行
    checkLikeToRegex(false, "a%c%d", "abcdc"); // 代码行
  } // 代码行
 // 空行
  private void checkLikeToRegex(boolean b, String pattern, String abc) { // 代码行
    final Pattern regex = LikePattern.likeToRegex(pattern); // 代码行
    assertTrue(b == regex.matcher(abc).matches()); // 代码行
  } // 代码行
 // 空行
  /** Tests driver's implementation of {@link DatabaseMetaData#getColumns}, // JavaDoc注释开始
   * and also // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1222">[CALCITE-1222] // JavaDoc注释内容
   * DatabaseMetaData.getColumnLabel returns null when query has ORDER // JavaDoc注释内容
   * BY</a>. */ // JavaDoc注释内容
  @Test void testResultSetMetaData() throws SQLException { // 测试方法:测试ResultSetMetaData功能
    try (Connection connection = // 代码行
             CalciteAssert.that(CalciteAssert.Config.REGULAR).connect()) { // 代码行
      final String sql0 = "select \"empid\", \"deptno\" as x, 1 as y\n" // 代码行
          + "from \"hr\".\"emps\""; // 代码行
      checkResultSetMetaData(connection, sql0); // 代码行
      final String sql1 = "select \"empid\", \"deptno\" as x, 1 as y\n" // 代码行
          + "from \"hr\".\"emps\"\n" // 代码行
          + "order by 1"; // 代码行
      checkResultSetMetaData(connection, sql1); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  private void checkResultSetMetaData(Connection connection, String sql) // 代码行
      throws SQLException { // 代码行
    try (Statement statement = connection.createStatement(); // 代码行
         ResultSet resultSet = statement.executeQuery(sql)) { // 代码行
      ResultSetMetaData metaData = resultSet.getMetaData(); // 代码行
      assertThat(metaData.getColumnCount(), is(3)); // 代码行
      assertThat(metaData.getColumnLabel(1), is("empid")); // 代码行
      assertThat(metaData.getColumnName(1), is("empid")); // 代码行
      assertThat(metaData.getTableName(1), is("emps")); // 代码行
      assertThat(metaData.getColumnLabel(2), is("X")); // 代码行
      assertThat(metaData.getColumnName(2), is("deptno")); // 代码行
      assertThat(metaData.getTableName(2), is("emps")); // 代码行
      assertThat(metaData.getColumnLabel(3), is("Y")); // 代码行
      assertThat(metaData.getColumnName(3), is("Y")); // 代码行
      assertThat(metaData.getTableName(3), nullValue()); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests some queries that have expedited processing because connection pools // JavaDoc注释开始
   * like to use them to check whether the connection is alive. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testSimple() { // 测试方法:测试Simple功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("SELECT 1") // 代码行
        .returns("EXPR$0=1\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests accessing columns by name. */ // JavaDoc注释开始
  @Test void testGetByName() throws Exception { // 测试方法:测试GetByName功能
    // JDBC 3.0 specification: "Column names supplied to getter methods are case // 单行注释
    // insensitive. If a select list contains the same column more than once, // 单行注释
    // the first instance of the column will be returned." // 单行注释
    CalciteAssert.that() // 代码行
        .doWithConnection(c -> { // 代码行
          try { // 代码行
            Statement s = c.createStatement(); // 代码行
            ResultSet rs = // 代码行
                s.executeQuery("" // 代码行
                    + "SELECT 1 as \"a\", 2 as \"b\", 3 as \"a\", 4 as \"B\"\n" // 代码行
                    + "FROM (VALUES (0))"); // 代码行
            assertTrue(rs.next()); // 代码行
            assertThat(rs.getInt("a"), is(1)); // 代码行
            assertThat(rs.getInt("A"), is(1)); // 代码行
            assertThat(rs.getInt("b"), is(2)); // 代码行
            assertThat(rs.getInt("B"), is(2)); // 代码行
            assertThat(rs.getInt(1), is(1)); // 代码行
            assertThat(rs.getInt(2), is(2)); // 代码行
            assertThat(rs.getInt(3), is(3)); // 代码行
            assertThat(rs.getInt(4), is(4)); // 代码行
            try { // 代码行
              int x = rs.getInt("z"); // 代码行
              fail("expected error, got " + x); // 代码行
            } catch (SQLException e) { // 代码行
              // ok // 单行注释
            } // 代码行
            assertThat(rs.findColumn("a"), is(1)); // 代码行
            assertThat(rs.findColumn("A"), is(1)); // 代码行
            assertThat(rs.findColumn("b"), is(2)); // 代码行
            assertThat(rs.findColumn("B"), is(2)); // 代码行
            try { // 代码行
              int x = rs.findColumn("z"); // 代码行
              fail("expected error, got " + x); // 代码行
            } catch (SQLException e) { // 代码行
              assertThat(e.getMessage(), is("column 'z' not found")); // 代码行
            } // 代码行
            try { // 代码行
              int x = rs.getInt(0); // 代码行
              fail("expected error, got " + x); // 代码行
            } catch (SQLException e) { // 代码行
              assertThat(e.getMessage(), // 代码行
                  is("invalid column ordinal: 0")); // 代码行
            } // 代码行
            try { // 代码行
              int x = rs.getInt(5); // 代码行
              fail("expected error, got " + x); // 代码行
            } catch (SQLException e) { // 代码行
              assertThat(e.getMessage(), // 代码行
                  is("invalid column ordinal: 5")); // 代码行
            } // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  @Test void testCloneSchema() throws SQLException { // 测试方法:测试CloneSchema功能
    final Connection connection = // 代码行
        CalciteAssert.that(CalciteAssert.Config.JDBC_FOODMART).connect(); // 代码行
    final CalciteConnection calciteConnection = // 代码行
        connection.unwrap(CalciteConnection.class); // 代码行
    final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 代码行
    final SchemaPlus foodmart = // 代码行
        requireNonNull(rootSchema.subSchemas().get("foodmart")); // 代码行
    rootSchema.add("foodmart2", new CloneSchema(foodmart)); // 代码行
    Statement statement = connection.createStatement(); // 代码行
    ResultSet resultSet = // 代码行
        statement.executeQuery( // 代码行
            "select count(*) from \"foodmart2\".\"time_by_day\""); // 代码行
    assertTrue(resultSet.next()); // 代码行
    assertThat(resultSet.getInt(1), is(730)); // 代码行
    resultSet.close(); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  @Test void testJdbcTableScan() throws SQLException { // 测试方法:测试JdbcTableScan功能
    final Connection connection = // 代码行
        CalciteAssert.that(CalciteAssert.Config.JDBC_FOODMART).connect(); // 代码行
    final CalciteConnection calciteConnection = // 代码行
        connection.unwrap(CalciteConnection.class); // 代码行
    final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 代码行
    final SchemaPlus foodmart = rootSchema.subSchemas().get("foodmart"); // 代码行
    assertThat(foodmart, notNullValue()); // 代码行
    final JdbcTable timeByDay = // 代码行
        requireNonNull((JdbcTable) foodmart.tables().get("time_by_day")); // 代码行
    final int rows = timeByDay.scan(DataContexts.of(calciteConnection, rootSchema)).count(); // 代码行
    assertThat(rows, OrderingComparison.greaterThan(0)); // 代码行
  } // 代码行
 // 空行
  @Test void testCloneGroupBy() { // 测试方法:测试CloneGroupBy功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"the_year\", count(*) as c, min(\"the_month\") as m\n" // 代码行
            + "from \"foodmart2\".\"time_by_day\"\n" // 代码行
            + "group by \"the_year\"\n" // 代码行
            + "order by 1, 2") // 代码行
        .returns("" // 代码行
            + "the_year=1997; C=365; M=April\n" // 代码行
            + "the_year=1998; C=365; M=April\n"); // 代码行
  } // 代码行
 // 空行
  @Disabled("The test returns expected results. Not sure why it is disabled") // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testCloneGroupBy2() { // 测试方法:测试CloneGroupBy2功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query( // 代码行
            "select \"time_by_day\".\"the_year\" as \"c0\", \"time_by_day\".\"quarter\" as \"c1\", \"product_class\".\"product_family\" as \"c2\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" from \"time_by_day\" as \"time_by_day\", \"sales_fact_1997\" as \"sales_fact_1997\", \"product_class\" as \"product_class\", \"product\" as \"product\" where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" group by \"time_by_day\".\"the_year\", \"time_by_day\".\"quarter\", \"product_class\".\"product_family\"") // 代码行
        .returnsUnordered( // 代码行
            "c0=1997; c1=Q2; c2=Drink; m0=5895.0000", // 代码行
            "c0=1997; c1=Q1; c2=Food; m0=47809.0000", // 代码行
            "c0=1997; c1=Q3; c2=Drink; m0=6065.0000", // 代码行
            "c0=1997; c1=Q4; c2=Drink; m0=6661.0000", // 代码行
            "c0=1997; c1=Q4; c2=Food; m0=51866.0000", // 代码行
            "c0=1997; c1=Q1; c2=Drink; m0=5976.0000", // 代码行
            "c0=1997; c1=Q3; c2=Non-Consumable; m0=12343.0000", // 代码行
            "c0=1997; c1=Q4; c2=Non-Consumable; m0=13497.0000", // 代码行
            "c0=1997; c1=Q2; c2=Non-Consumable; m0=11890.0000", // 代码行
            "c0=1997; c1=Q2; c2=Food; m0=44825.0000", // 代码行
            "c0=1997; c1=Q3; c2=Food; m0=47440.0000", // 代码行
            "c0=1997; c1=Q1; c2=Non-Consumable; m0=12506.0000"); // 代码行
  } // 代码行
 // 空行
  /** Tests plan for a query with 4 tables, 3 joins. */ // JavaDoc注释开始
  @Disabled("The actual and expected plan differ") // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testCloneGroupBy2Plan() { // 测试方法:测试CloneGroupBy2Plan功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query( // 代码行
            "explain plan for select \"time_by_day\".\"the_year\" as \"c0\", \"time_by_day\".\"quarter\" as \"c1\", \"product_class\".\"product_family\" as \"c2\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" from \"time_by_day\" as \"time_by_day\", \"sales_fact_1997\" as \"sales_fact_1997\", \"product_class\" as \"product_class\", \"product\" as \"product\" where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" group by \"time_by_day\".\"the_year\", \"time_by_day\".\"quarter\", \"product_class\".\"product_family\"") // 代码行
        .returns("PLAN=EnumerableAggregate(group=[{0, 1, 2}], m0=[SUM($3)])\n" // 代码行
            + "  EnumerableCalc(expr#0..37=[{inputs}], c0=[$t9], c1=[$t13], c2=[$t4], unit_sales=[$t22])\n" // 代码行
            + "    EnumerableHashJoin(condition=[=($23, $0)], joinType=[inner])\n" // 代码行
            + "      EnumerableTableScan(table=[[foodmart2, product_class]])\n" // 代码行
            + "      EnumerableHashJoin(condition=[=($10, $19)], joinType=[inner])\n" // 代码行
            + "        EnumerableHashJoin(condition=[=($11, $0)], joinType=[inner])\n" // 代码行
            + "          EnumerableCalc(expr#0..9=[{inputs}], expr#10=[CAST($t4):INTEGER], expr#11=[1997], expr#12=[=($t10, $t11)], proj#0..9=[{exprs}], $condition=[$t12])\n" // 代码行
            + "            EnumerableTableScan(table=[[foodmart2, time_by_day]])\n" // 代码行
            + "          EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n" // 代码行
            + "        EnumerableTableScan(table=[[foodmart2, product]])\n" // 代码行
            + "\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testOrderByCase() { // 测试方法:测试OrderByCase功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query( // 代码行
            "select \"time_by_day\".\"the_year\" as \"c0\" from \"time_by_day\" as \"time_by_day\" group by \"time_by_day\".\"the_year\" order by CASE WHEN \"time_by_day\".\"the_year\" IS NULL THEN 1 ELSE 0 END, \"time_by_day\".\"the_year\" ASC") // 代码行
        .returns("c0=1997\n" // 代码行
            + "c0=1998\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2894">[CALCITE-2894] // JavaDoc注释内容
   * NullPointerException thrown by RelMdPercentageOriginalRows when explaining // JavaDoc注释内容
   * plan with all attributes</a>. */ // JavaDoc注释内容
  @Test void testExplainAllAttributesSemiJoinUnionCorrelate() { // 测试方法:测试ExplainAllAttributesSemiJoinUnionCorrelate功能
    final String sql = "select deptno, name from depts where deptno in (\n" // 代码行
        + "  select e.deptno from emps e where exists (\n" // 代码行
        + "     select 1 from depts d where d.deptno = e.deptno)\n" // 代码行
        + "   union\n" // 代码行
        + "   select e.deptno from emps e where e.salary > 10000)"; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 代码行
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, false) // 代码行
        .withSchema("s", new ReflectiveSchema(new HrSchema())) // 代码行
        .query(sql) // 代码行
        .explainMatches("including all attributes ", // 代码行
            CalciteAssert.checkResultContains("EnumerableCorrelate")); // 代码行
  } // 代码行
 // 空行
  /** Just short of bushy. */ // JavaDoc注释开始
  @Test void testAlmostBushy() { // 测试方法:测试AlmostBushy功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select *\n" // 代码行
            + "from \"sales_fact_1997\" as s\n" // 代码行
            + "join \"customer\" as c\n" // 代码行
            + "  on s.\"customer_id\" = c.\"customer_id\"\n" // 代码行
            + "join \"product\" as p\n" // 代码行
            + "  on s.\"product_id\" = p.\"product_id\"\n" // 代码行
            + "where c.\"city\" = 'San Francisco'\n" // 代码行
            + "and p.\"brand_name\" = 'Washington'") // 代码行
        .explainMatches("including all attributes ", // 代码行
            CalciteAssert.checkMaskedResultContains("" // 代码行
                + "EnumerableMergeJoin(condition=[=($0, $38)], joinType=[inner]): rowcount = 7.050660528307499E8, cumulative cost = {7.656040129282498E8 rows, 5.408916992330521E10 cpu, 0.0 io}\n" // 代码行
                + "  EnumerableSort(sort0=[$0], dir0=[ASC]): rowcount = 2.0087351932499997E7, cumulative cost = {4.044858016499999E7 rows, 5.408911688230521E10 cpu, 0.0 io}\n" // 代码行
                + "    EnumerableMergeJoin(condition=[=($2, $8)], joinType=[inner]): rowcount = 2.0087351932499997E7, cumulative cost = {2.0361228232499994E7 rows, 4.4173907295063056E7 cpu, 0.0 io}\n" // 代码行
                + "      EnumerableSort(sort0=[$2], dir0=[ASC]): rowcount = 86837.0, cumulative cost = {173674.0 rows, 4.3536484295063056E7 cpu, 0.0 io}\n" // 代码行
                + "        EnumerableTableScan(table=[[foodmart2, sales_fact_1997]]): rowcount = 86837.0, cumulative cost = {86837.0 rows, 86838.0 cpu, 0.0 io}\n" // 代码行
                + "      EnumerableCalc(expr#0..28=[{inputs}], expr#29=['San Francisco':VARCHAR(30)], expr#30=[=($t9, $t29)], proj#0..28=[{exprs}], $condition=[$t30]): rowcount = 1542.1499999999999, cumulative cost = {11823.15 rows, 637423.0 cpu, 0.0 io}\n" // 代码行
                + "        EnumerableTableScan(table=[[foodmart2, customer]]): rowcount = 10281.0, cumulative cost = {10281.0 rows, 10282.0 cpu, 0.0 io}\n" // 代码行
                + "  EnumerableCalc(expr#0..14=[{inputs}], expr#15=['Washington':VARCHAR(60)], expr#16=[=($t2, $t15)], proj#0..14=[{exprs}], $condition=[$t16]): rowcount = 234.0, cumulative cost = {1794.0 rows, 53041.0 cpu, 0.0 io}\n" // 代码行
                + "    EnumerableTableScan(table=[[foodmart2, product]]): rowcount = 1560.0, cumulative cost = {1560.0 rows, 1561.0 cpu, 0.0 io}\n")); // 代码行
  } // 代码行
 // 空行
  /** Tests a query whose best plan is a bushy join. // JavaDoc注释开始
   * First join sales_fact_1997 to customer; // JavaDoc注释内容
   * in parallel join product to product_class; // JavaDoc注释内容
   * then join the results. */ // JavaDoc注释内容
  @Test void testBushy() { // 测试方法:测试Bushy功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select *\n" // 代码行
            + "from \"sales_fact_1997\" as s\n" // 代码行
            + "  join \"customer\" as c using (\"customer_id\")\n" // 代码行
            + "  join \"product\" as p using (\"product_id\")\n" // 代码行
            + "  join \"product_class\" as pc using (\"product_class_id\")\n" // 代码行
            + "where c.\"city\" = 'San Francisco'\n" // 代码行
            + "and pc.\"product_department\" = 'Snacks'\n") // 代码行
          .explainMatches("including all attributes ", // 代码行
              CalciteAssert.checkMaskedResultContains("" // 代码行
                  + "EnumerableCalc(expr#0..56=[{inputs}], product_class_id=[$t5], product_id=[$t20], customer_id=[$t22], time_id=[$t21], promotion_id=[$t23], store_id=[$t24], store_sales=[$t25], store_cost=[$t26], unit_sales=[$t27], account_num=[$t29], lname=[$t30], fname=[$t31], mi=[$t32], address1=[$t33], address2=[$t34], address3=[$t35], address4=[$t36], city=[$t37], state_province=[$t38], postal_code=[$t39], country=[$t40], customer_region_id=[$t41], phone1=[$t42], phone2=[$t43], birthdate=[$t44], marital_status=[$t45], yearly_income=[$t46], gender=[$t47], total_children=[$t48], num_children_at_home=[$t49], education=[$t50], date_accnt_opened=[$t51], member_card=[$t52], occupation=[$t53], houseowner=[$t54], num_cars_owned=[$t55], fullname=[$t56], brand_name=[$t7], product_name=[$t8], SKU=[$t9], SRP=[$t10], gross_weight=[$t11], net_weight=[$t12], recyclable_package=[$t13], low_fat=[$t14], units_per_case=[$t15], cases_per_pallet=[$t16], shelf_width=[$t17], shelf_height=[$t18], shelf_depth=[$t19], product_subcategory=[$t1], product_category=[$t2], product_department=[$t3], product_family=[$t4]): rowcount = 1.1633589871707373E10, cumulative cost = {2.3307667366104446E10 rows, 1.2913726527688135E12 cpu, 0.0 io}\n" // 代码行
                  + "  EnumerableHashJoin(condition=[=($6, $20)], joinType=[inner]): rowcount = 1.1633589871707373E10, cumulative cost = {1.1674077494397076E10 rows, 4.4177009295063056E7 cpu, 0.0 io}\n" // 代码行
                  + "    EnumerableHashJoin(condition=[=($0, $5)], joinType=[inner]): rowcount = 3861.0, cumulative cost = {7154.755446284958 rows, 3102.0 cpu, 0.0 io}\n" // 代码行
                  + "      EnumerableCalc(expr#0..4=[{inputs}], expr#5=['Snacks':VARCHAR(30)], expr#6=[=($t3, $t5)], proj#0..4=[{exprs}], $condition=[$t6]): rowcount = 16.5, cumulative cost = {126.5 rows, 1541.0 cpu, 0.0 io}\n" // 代码行
                  + "        EnumerableTableScan(table=[[foodmart2, product_class]]): rowcount = 110.0, cumulative cost = {110.0 rows, 111.0 cpu, 0.0 io}\n" // 代码行
                  + "      EnumerableTableScan(table=[[foodmart2, product]]): rowcount = 1560.0, cumulative cost = {1560.0 rows, 1561.0 cpu, 0.0 io}\n" // 代码行
                  + "    EnumerableMergeJoin(condition=[=($2, $8)], joinType=[inner]): rowcount = 2.0087351932499997E7, cumulative cost = {2.0361228232499994E7 rows, 4.4173907295063056E7 cpu, 0.0 io}\n" // 代码行
                  + "      EnumerableSort(sort0=[$2], dir0=[ASC]): rowcount = 86837.0, cumulative cost = {173674.0 rows, 4.3536484295063056E7 cpu, 0.0 io}\n" // 代码行
                  + "        EnumerableTableScan(table=[[foodmart2, sales_fact_1997]]): rowcount = 86837.0, cumulative cost = {86837.0 rows, 86838.0 cpu, 0.0 io}\n" // 代码行
                  + "      EnumerableCalc(expr#0..28=[{inputs}], expr#29=['San Francisco':VARCHAR(30)], expr#30=[=($t9, $t29)], proj#0..28=[{exprs}], $condition=[$t30]): rowcount = 1542.1499999999999, cumulative cost = {11823.15 rows, 637423.0 cpu, 0.0 io}\n" // 代码行
                  + "        EnumerableTableScan(table=[[foodmart2, customer]]): rowcount = 10281.0, cumulative cost = {10281.0 rows, 10282.0 cpu, 0.0 io}\n")); // 代码行
  } // 代码行
 // 空行
  private static final String[] QUERIES = { // 代码行
      "select count(*) from (select 1 as \"c0\" from \"salary\" as \"salary\") as \"init\"", // 代码行
      "EXPR$0=21252\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"salary\" as \"salary2\") as \"init\"", // 代码行
      "EXPR$0=21252\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"department\" as \"department\") as \"init\"", // 代码行
      "EXPR$0=12\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"employee\" as \"employee\") as \"init\"", // 代码行
      "EXPR$0=1155\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"employee_closure\" as \"employee_closure\") as \"init\"", // 代码行
      "EXPR$0=7179\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"position\" as \"position\") as \"init\"", // 代码行
      "EXPR$0=18\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"promotion\" as \"promotion\") as \"init\"", // 代码行
      "EXPR$0=1864\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"store\" as \"store\") as \"init\"", // 代码行
      "EXPR$0=25\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"product\" as \"product\") as \"init\"", // 代码行
      "EXPR$0=1560\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"product_class\" as \"product_class\") as \"init\"", // 代码行
      "EXPR$0=110\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"time_by_day\" as \"time_by_day\") as \"init\"", // 代码行
      "EXPR$0=730\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"customer\" as \"customer\") as \"init\"", // 代码行
      "EXPR$0=10281\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"sales_fact_1997\" as \"sales_fact_1997\") as \"init\"", // 代码行
      "EXPR$0=86837\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"inventory_fact_1997\" as \"inventory_fact_1997\") as \"init\"", // 代码行
      "EXPR$0=4070\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"warehouse\" as \"warehouse\") as \"init\"", // 代码行
      "EXPR$0=24\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"agg_c_special_sales_fact_1997\" as \"agg_c_special_sales_fact_1997\") as \"init\"", // 代码行
      "EXPR$0=86805\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"agg_pl_01_sales_fact_1997\" as \"agg_pl_01_sales_fact_1997\") as \"init\"", // 代码行
      "EXPR$0=86829\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"agg_l_05_sales_fact_1997\" as \"agg_l_05_sales_fact_1997\") as \"init\"", // 代码行
      "EXPR$0=86154\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"agg_g_ms_pcat_sales_fact_1997\" as \"agg_g_ms_pcat_sales_fact_1997\") as \"init\"", // 代码行
      "EXPR$0=2637\n", // 代码行
      "select count(*) from (select 1 as \"c0\" from \"agg_c_14_sales_fact_1997\" as \"agg_c_14_sales_fact_1997\") as \"init\"", // 代码行
      "EXPR$0=86805\n", // 代码行
      "select \"time_by_day\".\"the_year\" as \"c0\" from \"time_by_day\" as \"time_by_day\" group by \"time_by_day\".\"the_year\" order by \"time_by_day\".\"the_year\" ASC", // 代码行
      "c0=1997\n" // 代码行
          + "c0=1998\n", // 代码行
      "select \"store\".\"store_country\" as \"c0\" from \"store\" as \"store\" where UPPER(\"store\".\"store_country\") = UPPER('USA') group by \"store\".\"store_country\" order by \"store\".\"store_country\" ASC", // 代码行
      "c0=USA\n", // 代码行
      "select \"store\".\"store_state\" as \"c0\" from \"store\" as \"store\" where (\"store\".\"store_country\" = 'USA') and UPPER(\"store\".\"store_state\") = UPPER('CA') group by \"store\".\"store_state\" order by \"store\".\"store_state\" ASC", // 代码行
      "c0=CA\n", // 代码行
      "select \"store\".\"store_city\" as \"c0\", \"store\".\"store_state\" as \"c1\" from \"store\" as \"store\" where (\"store\".\"store_state\" = 'CA' and \"store\".\"store_country\" = 'USA') and UPPER(\"store\".\"store_city\") = UPPER('Los Angeles') group by \"store\".\"store_city\", \"store\".\"store_state\" order by \"store\".\"store_city\" ASC", // 代码行
      "c0=Los Angeles; c1=CA\n", // 代码行
      "select \"customer\".\"country\" as \"c0\" from \"customer\" as \"customer\" where UPPER(\"customer\".\"country\") = UPPER('USA') group by \"customer\".\"country\" order by \"customer\".\"country\" ASC", // 代码行
      "c0=USA\n", // 代码行
      "select \"customer\".\"state_province\" as \"c0\", \"customer\".\"country\" as \"c1\" from \"customer\" as \"customer\" where (\"customer\".\"country\" = 'USA') and UPPER(\"customer\".\"state_province\") = UPPER('CA') group by \"customer\".\"state_province\", \"customer\".\"country\" order by \"customer\".\"state_province\" ASC", // 代码行
      "c0=CA; c1=USA\n", // 代码行
      "select \"customer\".\"city\" as \"c0\", \"customer\".\"country\" as \"c1\", \"customer\".\"state_province\" as \"c2\" from \"customer\" as \"customer\" where (\"customer\".\"country\" = 'USA' and \"customer\".\"state_province\" = 'CA' and \"customer\".\"country\" = 'USA' and \"customer\".\"state_province\" = 'CA' and \"customer\".\"country\" = 'USA') and UPPER(\"customer\".\"city\") = UPPER('Los Angeles') group by \"customer\".\"city\", \"customer\".\"country\", \"customer\".\"state_province\" order by \"customer\".\"city\" ASC", // 代码行
      "c0=Los Angeles; c1=USA; c2=CA\n", // 代码行
      "select \"store\".\"store_country\" as \"c0\" from \"store\" as \"store\" where UPPER(\"store\".\"store_country\") = UPPER('Gender') group by \"store\".\"store_country\" order by \"store\".\"store_country\" ASC", // 代码行
      "", // 代码行
      "select \"store\".\"store_type\" as \"c0\" from \"store\" as \"store\" where UPPER(\"store\".\"store_type\") = UPPER('Gender') group by \"store\".\"store_type\" order by \"store\".\"store_type\" ASC", // 代码行
      "", // 代码行
      "select \"product_class\".\"product_family\" as \"c0\" from \"product\" as \"product\", \"product_class\" as \"product_class\" where \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" and UPPER(\"product_class\".\"product_family\") = UPPER('Gender') group by \"product_class\".\"product_family\" order by \"product_class\".\"product_family\" ASC", // 代码行
      "", // 代码行
      "select \"promotion\".\"media_type\" as \"c0\" from \"promotion\" as \"promotion\" where UPPER(\"promotion\".\"media_type\") = UPPER('Gender') group by \"promotion\".\"media_type\" order by \"promotion\".\"media_type\" ASC", // 代码行
      "", // 代码行
      "select \"promotion\".\"promotion_name\" as \"c0\" from \"promotion\" as \"promotion\" where UPPER(\"promotion\".\"promotion_name\") = UPPER('Gender') group by \"promotion\".\"promotion_name\" order by \"promotion\".\"promotion_name\" ASC", // 代码行
      "", // 代码行
      "select \"promotion\".\"media_type\" as \"c0\" from \"promotion\" as \"promotion\" where UPPER(\"promotion\".\"media_type\") = UPPER('No Media') group by \"promotion\".\"media_type\" order by \"promotion\".\"media_type\" ASC", // 代码行
      "c0=No Media\n", // 代码行
      "select \"promotion\".\"media_type\" as \"c0\" from \"promotion\" as \"promotion\" group by \"promotion\".\"media_type\" order by \"promotion\".\"media_type\" ASC", // 代码行
      "c0=Bulk Mail\n" // 代码行
        + "c0=Cash Register Handout\n" // 代码行
        + "c0=Daily Paper\n" // 代码行
        + "c0=Daily Paper, Radio\n" // 代码行
        + "c0=Daily Paper, Radio, TV\n" // 代码行
        + "c0=In-Store Coupon\n" // 代码行
        + "c0=No Media\n" // 代码行
        + "c0=Product Attachment\n" // 代码行
        + "c0=Radio\n" // 代码行
        + "c0=Street Handout\n" // 代码行
        + "c0=Sunday Paper\n" // 代码行
        + "c0=Sunday Paper, Radio\n" // 代码行
        + "c0=Sunday Paper, Radio, TV\n" // 代码行
        + "c0=TV\n", // 代码行
      "select count(distinct \"the_year\") from \"time_by_day\"", // 代码行
      "EXPR$0=2\n", // 代码行
      "select \"time_by_day\".\"the_year\" as \"c0\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" from \"time_by_day\" as \"time_by_day\", \"sales_fact_1997\" as \"sales_fact_1997\" where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 group by \"time_by_day\".\"the_year\"", // 代码行
      "c0=1997; m0=266773.0000\n", // 代码行
      "select \"time_by_day\".\"the_year\" as \"c0\", \"promotion\".\"media_type\" as \"c1\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" from \"time_by_day\" as \"time_by_day\", \"sales_fact_1997\" as \"sales_fact_1997\", \"promotion\" as \"promotion\" where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 and \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\" group by \"time_by_day\".\"the_year\", \"promotion\".\"media_type\"", // 代码行
      "c0=1997; c1=Bulk Mail; m0=4320.0000\n" // 代码行
        + "c0=1997; c1=Radio; m0=2454.0000\n" // 代码行
        + "c0=1997; c1=Street Handout; m0=5753.0000\n" // 代码行
        + "c0=1997; c1=TV; m0=3607.0000\n" // 代码行
        + "c0=1997; c1=No Media; m0=195448.0000\n" // 代码行
        + "c0=1997; c1=In-Store Coupon; m0=3798.0000\n" // 代码行
        + "c0=1997; c1=Sunday Paper, Radio, TV; m0=2726.0000\n" // 代码行
        + "c0=1997; c1=Product Attachment; m0=7544.0000\n" // 代码行
        + "c0=1997; c1=Daily Paper; m0=7738.0000\n" // 代码行
        + "c0=1997; c1=Cash Register Handout; m0=6697.0000\n" // 代码行
        + "c0=1997; c1=Daily Paper, Radio; m0=6891.0000\n" // 代码行
        + "c0=1997; c1=Daily Paper, Radio, TV; m0=9513.0000\n" // 代码行
        + "c0=1997; c1=Sunday Paper, Radio; m0=5945.0000\n" // 代码行
        + "c0=1997; c1=Sunday Paper; m0=4339.0000\n", // 代码行
      "select \"store\".\"store_country\" as \"c0\", sum(\"inventory_fact_1997\".\"supply_time\") as \"m0\" from \"store\" as \"store\", \"inventory_fact_1997\" as \"inventory_fact_1997\" where \"inventory_fact_1997\".\"store_id\" = \"store\".\"store_id\" group by \"store\".\"store_country\"", // 代码行
      "c0=USA; m0=10425\n", // 代码行
      "select \"sn\".\"desc\" as \"c0\" from (SELECT * FROM (VALUES (1, 'SameName')) AS \"t\" (\"id\", \"desc\")) as \"sn\" group by \"sn\".\"desc\" order by \"sn\".\"desc\" ASC NULLS LAST", // 代码行
      "c0=SameName\n", // 代码行
      "select \"the_year\", count(*) as c, min(\"the_month\") as m\n" // 代码行
        + "from \"foodmart2\".\"time_by_day\"\n" // 代码行
        + "group by \"the_year\"\n" // 代码行
        + "order by 1, 2", // 代码行
      "the_year=1997; C=365; M=April\n" // 代码行
        + "the_year=1998; C=365; M=April\n", // 代码行
      "select\n" // 代码行
        + " \"store\".\"store_state\" as \"c0\",\n" // 代码行
        + " \"time_by_day\".\"the_year\" as \"c1\",\n" // 代码行
        + " sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\",\n" // 代码行
        + " sum(\"sales_fact_1997\".\"store_sales\") as \"m1\"\n" // 代码行
        + "from \"store\" as \"store\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
        + " \"time_by_day\" as \"time_by_day\"\n" // 代码行
        + "where \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"\n" // 代码行
        + "and \"store\".\"store_state\" in ('DF', 'WA')\n" // 代码行
        + "and \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
        + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "group by \"store\".\"store_state\", \"time_by_day\".\"the_year\"", // 代码行
      "c0=WA; c1=1997; m0=124366.0000; m1=263793.2200\n", // 代码行
      "select count(distinct \"product_id\") from \"product\"", // 代码行
      "EXPR$0=1560\n", // 代码行
      "select \"store\".\"store_name\" as \"c0\",\n" // 代码行
        + " \"time_by_day\".\"the_year\" as \"c1\",\n" // 代码行
        + " sum(\"sales_fact_1997\".\"store_sales\") as \"m0\"\n" // 代码行
        + "from \"store\" as \"store\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
        + " \"time_by_day\" as \"time_by_day\"\n" // 代码行
        + "where \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"\n" // 代码行
        + "and \"store\".\"store_name\" in ('Store 1', 'Store 10', 'Store 11', 'Store 15', 'Store 16', 'Store 24', 'Store 3', 'Store 7')\n" // 代码行
        + "and \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
        + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "group by \"store\".\"store_name\",\n" // 代码行
        + " \"time_by_day\".\"the_year\"\n", // 代码行
      "c0=Store 7; c1=1997; m0=54545.2800\n" // 代码行
        + "c0=Store 24; c1=1997; m0=54431.1400\n" // 代码行
        + "c0=Store 16; c1=1997; m0=49634.4600\n" // 代码行
        + "c0=Store 3; c1=1997; m0=52896.3000\n" // 代码行
        + "c0=Store 15; c1=1997; m0=52644.0700\n" // 代码行
        + "c0=Store 11; c1=1997; m0=55058.7900\n", // 代码行
      "select \"customer\".\"yearly_income\" as \"c0\"," // 代码行
        + " \"customer\".\"education\" as \"c1\"\n" // 代码行
        + "from \"customer\" as \"customer\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\"\n" // 代码行
        + "where \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n" // 代码行
        + " and ((not (\"customer\".\"yearly_income\" in ('$10K - $30K', '$50K - $70K'))\n" // 代码行
        + " or (\"customer\".\"yearly_income\" is null)))\n" // 代码行
        + "group by \"customer\".\"yearly_income\",\n" // 代码行
        + " \"customer\".\"education\"\n" // 代码行
        + "order by \"customer\".\"yearly_income\" ASC NULLS LAST,\n" // 代码行
        + " \"customer\".\"education\" ASC NULLS LAST", // 代码行
      "c0=$110K - $130K; c1=Bachelors Degree\n" // 代码行
        + "c0=$110K - $130K; c1=Graduate Degree\n" // 代码行
        + "c0=$110K - $130K; c1=High School Degree\n" // 代码行
        + "c0=$110K - $130K; c1=Partial College\n" // 代码行
        + "c0=$110K - $130K; c1=Partial High School\n" // 代码行
        + "c0=$130K - $150K; c1=Bachelors Degree\n" // 代码行
        + "c0=$130K - $150K; c1=Graduate Degree\n" // 代码行
        + "c0=$130K - $150K; c1=High School Degree\n" // 代码行
        + "c0=$130K - $150K; c1=Partial College\n" // 代码行
        + "c0=$130K - $150K; c1=Partial High School\n" // 代码行
        + "c0=$150K +; c1=Bachelors Degree\n" // 代码行
        + "c0=$150K +; c1=Graduate Degree\n" // 代码行
        + "c0=$150K +; c1=High School Degree\n" // 代码行
        + "c0=$150K +; c1=Partial College\n" // 代码行
        + "c0=$150K +; c1=Partial High School\n" // 代码行
        + "c0=$30K - $50K; c1=Bachelors Degree\n" // 代码行
        + "c0=$30K - $50K; c1=Graduate Degree\n" // 代码行
        + "c0=$30K - $50K; c1=High School Degree\n" // 代码行
        + "c0=$30K - $50K; c1=Partial College\n" // 代码行
        + "c0=$30K - $50K; c1=Partial High School\n" // 代码行
        + "c0=$70K - $90K; c1=Bachelors Degree\n" // 代码行
        + "c0=$70K - $90K; c1=Graduate Degree\n" // 代码行
        + "c0=$70K - $90K; c1=High School Degree\n" // 代码行
        + "c0=$70K - $90K; c1=Partial College\n" // 代码行
        + "c0=$70K - $90K; c1=Partial High School\n" // 代码行
        + "c0=$90K - $110K; c1=Bachelors Degree\n" // 代码行
        + "c0=$90K - $110K; c1=Graduate Degree\n" // 代码行
        + "c0=$90K - $110K; c1=High School Degree\n" // 代码行
        + "c0=$90K - $110K; c1=Partial College\n" // 代码行
        + "c0=$90K - $110K; c1=Partial High School\n", // 代码行
      "ignore:select \"time_by_day\".\"the_year\" as \"c0\", \"product_class\".\"product_family\" as \"c1\", \"customer\".\"state_province\" as \"c2\", \"customer\".\"city\" as \"c3\", sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\" from \"time_by_day\" as \"time_by_day\", \"sales_fact_1997\" as \"sales_fact_1997\", \"product_class\" as \"product_class\", \"product\" as \"product\", \"customer\" as \"customer\" where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\" and \"time_by_day\".\"the_year\" = 1997 and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\" and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\" and \"product_class\".\"product_family\" = 'Drink' and \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\" and \"customer\".\"state_province\" = 'WA' and \"customer\".\"city\" in ('Anacortes', 'Ballard', 'Bellingham', 'Bremerton', 'Burien', 'Edmonds', 'Everett', 'Issaquah', 'Kirkland', 'Lynnwood', 'Marysville', 'Olympia', 'Port Orchard', 'Puyallup', 'Redmond', 'Renton', 'Seattle', 'Sedro Woolley', 'Spokane', 'Tacoma', 'Walla Walla', 'Yakima') group by \"time_by_day\".\"the_year\", \"product_class\".\"product_family\", \"customer\".\"state_province\", \"customer\".\"city\"", // 代码行
      "c0=1997; c1=Drink; c2=WA; c3=Sedro Woolley; m0=58.0000\n", // 代码行
      "select \"store\".\"store_country\" as \"c0\",\n" // 代码行
        + " \"time_by_day\".\"the_year\" as \"c1\",\n" // 代码行
        + " sum(\"sales_fact_1997\".\"store_cost\") as \"m0\",\n" // 代码行
        + " count(\"sales_fact_1997\".\"product_id\") as \"m1\",\n" // 代码行
        + " count(distinct \"sales_fact_1997\".\"customer_id\") as \"m2\",\n" // 代码行
        + " sum((case when \"sales_fact_1997\".\"promotion_id\" = 0 then 0\n" // 代码行
        + "     else \"sales_fact_1997\".\"store_sales\" end)) as \"m3\"\n" // 代码行
        + "from \"store\" as \"store\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
        + " \"time_by_day\" as \"time_by_day\"\n" // 代码行
        + "where \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"\n" // 代码行
        + "and \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
        + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "group by \"store\".\"store_country\", \"time_by_day\".\"the_year\"", // 代码行
      "c0=USA; c1=1997; m0=225627.2336; m1=86837; m2=5581; m3=151211.2100\n", // 代码行
      // query 6077 // 单行注释
      // disabled (runs out of memory) // 单行注释
      "ignore:select \"time_by_day\".\"the_year\" as \"c0\",\n" // 代码行
        + " count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\"\n" // 代码行
        + "from \"time_by_day\" as \"time_by_day\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
        + " \"product_class\" as \"product_class\",\n" // 代码行
        + " \"product\" as \"product\"\n" // 代码行
        + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
        + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
        + "and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n" // 代码行
        + "and (((\"product\".\"brand_name\" = 'Cormorant'\n" // 代码行
        + "   and \"product_class\".\"product_subcategory\" = 'Pot Scrubbers'\n" // 代码行
        + "   and \"product_class\".\"product_category\" = 'Kitchen Products'\n" // 代码行
        + "   and \"product_class\".\"product_department\" = 'Household'\n" // 代码行
        + "   and \"product_class\".\"product_family\" = 'Non-Consumable')\n" // 代码行
        + " or (\"product\".\"brand_name\" = 'Denny'\n" // 代码行
        + "   and \"product_class\".\"product_subcategory\" = 'Pot Scrubbers'\n" // 代码行
        + "   and \"product_class\".\"product_category\" = 'Kitchen Products'\n" // 代码行
        + "   and \"product_class\".\"product_department\" = 'Household'\n" // 代码行
        + "   and \"product_class\".\"product_family\" = 'Non-Consumable')\n" // 代码行
        + " or (\"product\".\"brand_name\" = 'High Quality'\n" // 代码行
        + "   and \"product_class\".\"product_subcategory\" = 'Pot Scrubbers'\n" // 代码行
        + "   and \"product_class\".\"product_category\" = 'Kitchen Products'\n" // 代码行
        + "   and \"product_class\".\"product_department\" = 'Household'\n" // 代码行
        + "   and \"product_class\".\"product_family\" = 'Non-Consumable')\n" // 代码行
        + " or (\"product\".\"brand_name\" = 'Red Wing'\n" // 代码行
        + "   and \"product_class\".\"product_subcategory\" = 'Pot Scrubbers'\n" // 代码行
        + "   and \"product_class\".\"product_category\" = 'Kitchen Products'\n" // 代码行
        + "   and \"product_class\".\"product_department\" = 'Household'\n" // 代码行
        + "   and \"product_class\".\"product_family\" = 'Non-Consumable'))\n" // 代码行
        + " or (\"product_class\".\"product_subcategory\" = 'Pots and Pans'\n" // 代码行
        + "   and \"product_class\".\"product_category\" = 'Kitchen Products'\n" // 代码行
        + "   and \"product_class\".\"product_department\" = 'Household'\n" // 代码行
        + "   and \"product_class\".\"product_family\" = 'Non-Consumable'))\n" // 代码行
        + "group by \"time_by_day\".\"the_year\"\n", // 代码行
      "xxtodo", // 代码行
      // query 6077, simplified // 单行注释
      // disabled (slow) // 单行注释
      "ignore:select count(\"sales_fact_1997\".\"customer_id\") as \"m0\"\n" // 代码行
        + "from \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
        + " \"product_class\" as \"product_class\",\n" // 代码行
        + " \"product\" as \"product\"\n" // 代码行
        + "where \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
        + "and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n" // 代码行
        + "and ((\"product\".\"brand_name\" = 'Cormorant'\n" // 代码行
        + "   and \"product_class\".\"product_subcategory\" = 'Pot Scrubbers')\n" // 代码行
        + " or (\"product_class\".\"product_subcategory\" = 'Pots and Pans'))\n", // 代码行
      "xxxx", // 代码行
      // query 6077, simplified further // 单行注释
      "select count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\"\n" // 代码行
        + "from \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
        + " \"product_class\" as \"product_class\",\n" // 代码行
        + " \"product\" as \"product\"\n" // 代码行
        + "where \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
        + "and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n" // 代码行
        + "and \"product\".\"brand_name\" = 'Cormorant'\n", // 代码行
      "m0=1298", // 代码行
      // query 193 // 单行注释
      "select \"store\".\"store_country\" as \"c0\",\n" // 代码行
        + " \"time_by_day\".\"the_year\" as \"c1\",\n" // 代码行
        + " \"time_by_day\".\"quarter\" as \"c2\",\n" // 代码行
        + " \"product_class\".\"product_family\" as \"c3\",\n" // 代码行
        + " count(\"sales_fact_1997\".\"product_id\") as \"m0\",\n" // 代码行
        + " count(distinct \"sales_fact_1997\".\"customer_id\") as \"m1\"\n" // 代码行
        + "from \"store\" as \"store\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
        + " \"time_by_day\" as \"time_by_day\",\n" // 代码行
        + " \"product_class\" as \"product_class\",\n" // 代码行
        + " \"product\" as \"product\"\n" // 代码行
        + "where \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"\n" // 代码行
        + "and \"store\".\"store_country\" = 'USA'\n" // 代码行
        + "and \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
        + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "and \"time_by_day\".\"quarter\" = 'Q3'\n" // 代码行
        + "and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
        + "and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n" // 代码行
        + "and \"product_class\".\"product_family\" = 'Food'\n" // 代码行
        + "group by \"store\".\"store_country\",\n" // 代码行
        + " \"time_by_day\".\"the_year\",\n" // 代码行
        + " \"time_by_day\".\"quarter\",\n" // 代码行
        + " \"product_class\".\"product_family\"", // 代码行
      "c0=USA; c1=1997; c2=Q3; c3=Food; m0=15449; m1=2939", // 代码行
  }; // 代码行
 // 空行
  public static final List<Pair<String, String>> FOODMART_QUERIES = // 代码行
      querify(QUERIES); // 代码行
 // 空行
  /** Janino bug // JavaDoc注释开始
   * <a href="https://jira.codehaus.org/browse/JANINO-169">[JANINO-169]</a> // JavaDoc注释内容
   * running queries against the JDBC adapter. The bug is not present with // JavaDoc注释内容
   * janino-3.0.9 so the workaround in EnumerableRelImplementor was removed. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testJanino169() { // 测试方法:测试Janino169功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query( // 代码行
            "select \"time_id\" from \"foodmart\".\"time_by_day\" as \"t\"\n") // 代码行
        .returnsCount(730); // 代码行
  } // 代码行
 // 空行
  /** Tests 3-way AND. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>With // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-127">[CALCITE-127] // JavaDoc注释内容
   * EnumerableCalcRel can't support 3+ AND conditions</a>, the last condition // JavaDoc注释内容
   * is ignored and rows with deptno=10 are wrongly returned. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testAnd3() { // 测试方法:测试And3功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\" from \"hr\".\"emps\"\n" // 代码行
            + "where \"emps\".\"empid\" < 240\n" // 代码行
            + "and \"salary\" > 7500.0" // 代码行
            + "and \"emps\".\"deptno\" > 10\n") // 代码行
        .returnsUnordered("deptno=20"); // 代码行
  } // 代码行
 // 空行
  /** Tests a date literal against a JDBC data source. */ // JavaDoc注释开始
  @Test void testJdbcDate() { // 测试方法:测试JdbcDate功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select count(*) as c from (\n" // 代码行
            + "  select 1 from \"foodmart\".\"employee\" as e1\n" // 代码行
            + "  where \"position_title\" = 'VP Country Manager'\n" // 代码行
            + "  and \"birth_date\" < DATE '1950-01-01'\n" // 代码行
            + "  and \"gender\" = 'F')") // 代码行
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.ORACLE) // 代码行
        .returns2("C=1\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a timestamp literal against JDBC data source. */ // JavaDoc注释开始
  @Test void testJdbcTimestamp() { // 测试方法:测试JdbcTimestamp功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select count(*) as c from (\n" // 代码行
            + "  select 1 from \"foodmart\".\"employee\" as e1\n" // 代码行
            + "  where \"hire_date\" < TIMESTAMP '1996-06-05 00:00:00'\n" // 代码行
            + "  and \"gender\" = 'F')") // 代码行
        .returns("C=287\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-281">[CALCITE-281] // JavaDoc注释内容
   * SQL type of EXTRACT is BIGINT but it is implemented as int</a>. */ // JavaDoc注释内容
  @Test void testExtract() { // 测试方法:测试Extract功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("values extract(year from date '2008-2-23')") // 代码行
        .returns(resultSet -> { // 代码行
          // The following behavior is not quite correct. See // 单行注释
          //   [CALCITE-508] Reading from ResultSet before calling next() // 单行注释
          //   should throw SQLException not NoSuchElementException // 单行注释
          // for details. // 单行注释
          try { // 代码行
            final BigDecimal bigDecimal = resultSet.getBigDecimal(1); // 代码行
            fail("expected error, got " + bigDecimal); // 代码行
          } catch (SQLException e) { // 代码行
            assertThat(e.getMessage(), // 代码行
                is("java.util.NoSuchElementException: Expecting cursor " // 代码行
                    + "position to be Position.OK, actual " // 代码行
                    + "is Position.BEFORE_START")); // 代码行
          } // 代码行
          try { // 代码行
            assertTrue(resultSet.next()); // 代码行
            final BigDecimal bigDecimal = resultSet.getBigDecimal(1); // 代码行
            assertThat(bigDecimal, is(BigDecimal.valueOf(2008))); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  @Test void testExtractMonthFromTimestamp() { // 测试方法:测试ExtractMonthFromTimestamp功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select extract(month from \"birth_date\") as c\n" // 代码行
            + "from \"foodmart\".\"employee\" where \"employee_id\"=1") // 代码行
        .returns("C=8\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testExtractYearFromTimestamp() { // 测试方法:测试ExtractYearFromTimestamp功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select extract(year from \"birth_date\") as c\n" // 代码行
            + "from \"foodmart\".\"employee\" where \"employee_id\"=1") // 代码行
        .returns("C=1961\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testExtractFromInterval() { // 测试方法:测试ExtractFromInterval功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select extract(month from interval '2-3' year to month) as c\n" // 代码行
            + "from \"foodmart\".\"employee\" where \"employee_id\"=1") // 代码行
        // disable for MySQL, H2; cannot handle EXTRACT yet // 单行注释
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.MYSQL // 代码行
            && CalciteAssert.DB != CalciteAssert.DatabaseInstance.H2) // 代码行
        .returns("C=3\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1188">[CALCITE-1188] // JavaDoc注释内容
   * NullPointerException when EXTRACT is applied to NULL date field</a>. // JavaDoc注释内容
   * The problem occurs when EXTRACT appears in both SELECT and WHERE ... IN // JavaDoc注释内容
   * clauses, the latter with at least two values. */ // JavaDoc注释内容
  @Test void testExtractOnNullDateField() { // 测试方法:测试ExtractOnNullDateField功能
    final String sql = "select\n" // 代码行
        + "  extract(year from \"end_date\"), \"hire_date\", \"birth_date\"\n" // 代码行
        + "from \"foodmart\".\"employee\"\n" // 代码行
        + "where extract(year from \"end_date\") in (1994, 1995, 1996)\n" // 代码行
        + "group by\n" // 代码行
        + "  extract(year from \"end_date\"), \"hire_date\", \"birth_date\"\n"; // 代码行
    final String sql2 = sql + "\n" // 代码行
        + "limit 10000"; // 代码行
    final String sql3 = "select *\n" // 代码行
        + "from \"foodmart\".\"employee\"\n" // 代码行
        + "where extract(year from \"end_date\") in (1994, 1995, 1996)"; // 代码行
    final CalciteAssert.AssertThat with = CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE); // 代码行
    with.query(sql).returns(""); // 代码行
    with.query(sql2).returns(""); // 代码行
    with.query(sql3).returns(""); // 代码行
  } // 代码行
 // 空行
  @Test void testFloorDate() { // 测试方法:测试FloorDate功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select floor(timestamp '2011-9-14 19:27:23' to month) as c\n" // 代码行
            + "from \"foodmart\".\"employee\" limit 1") // 代码行
        // disable for MySQL; birth_date suffers timezone shift // 单行注释
        // disable for H2; Calcite generates incorrect FLOOR syntax // 单行注释
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.MYSQL // 代码行
            && CalciteAssert.DB != CalciteAssert.DatabaseInstance.H2) // 代码行
        .returns("C=2011-09-01 00:00:00\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3435">[CALCITE-3435] // JavaDoc注释内容
   * Enable decimal modulus operation to allow numeric with non-zero scale</a>. */ // JavaDoc注释内容
  @Test void testModOperation() { // 测试方法:测试ModOperation功能
    CalciteAssert.that() // 代码行
        .query("select mod(33.5, 7) as c0, floor(mod(33.5, 7)) as c1, " // 代码行
            + "mod(11, 3.2) as c2, floor(mod(11, 3.2)) as c3," // 代码行
            + "mod(12, 3) as c4, floor(mod(12, 3)) as c5") // 代码行
        .typeIs("[C0 DECIMAL NOT NULL, C1 DECIMAL NOT NULL, C2 DECIMAL NOT NULL, " // 代码行
            + "C3 DECIMAL NOT NULL, C4 INTEGER NOT NULL, C5 INTEGER NOT NULL]") // 代码行
        .returns("C0=5.5; C1=5; C2=1.4; C3=1; C4=0; C5=0\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-387">[CALCITE-387] // JavaDoc注释内容
   * CompileException when cast TRUE to nullable boolean</a>. */ // JavaDoc注释内容
  @Test void testTrue() { // 测试方法:测试True功能
    final CalciteAssert.AssertThat that = CalciteAssert.that(); // 代码行
    that.query("select case when deptno = 10 then null else true end as x\n" // 代码行
        + "from (values (10), (20)) as t(deptno)") // 代码行
        .returnsUnordered("X=null", "X=true"); // 代码行
    that.query("select case when deptno = 10 then null else 100 end as x\n" // 代码行
        + "from (values (10), (20)) as t(deptno)") // 代码行
        .returnsUnordered("X=null", "X=100"); // 代码行
    that.query("select case when deptno = 10 then null else 'xy' end as x\n" // 代码行
        + "from (values (10), (20)) as t(deptno)") // 代码行
        .returnsUnordered("X=null", "X=xy"); // 代码行
  } // 代码行
 // 空行
  /** Unit test for self-join. Left and right children of the join are the same // JavaDoc注释开始
   * relational expression. */ // JavaDoc注释内容
  @Test void testSelfJoin() { // 测试方法:测试SelfJoin功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select count(*) as c from (\n" // 代码行
            + "  select 1 from \"foodmart\".\"employee\" as e1\n" // 代码行
            + "  join \"foodmart\".\"employee\" as e2 using (\"position_title\"))") // 代码行
        .returns("C=247149\n"); // 代码行
  } // 代码行
 // 空行
  /** Self-join on different columns, select a different column, and sort and // JavaDoc注释开始
   * limit on yet another column. */ // JavaDoc注释内容
  @Test void testSelfJoinDifferentColumns() { // 测试方法:测试SelfJoinDifferentColumns功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select e1.\"full_name\"\n" // 代码行
            + "  from \"foodmart\".\"employee\" as e1\n" // 代码行
            + "  join \"foodmart\".\"employee\" as e2 on e1.\"first_name\" = e2.\"last_name\"\n" // 代码行
            + "order by e1.\"last_name\" limit 3") // 代码行
        // disable for H2; gives "Unexpected code path" internal error // 单行注释
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.H2) // 代码行
        .returns("full_name=James Aguilar\n" // 代码行
            + "full_name=Carol Amyotte\n" // 代码行
            + "full_name=Terry Anderson\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2029">[CALCITE-2029] // JavaDoc注释内容
   * Query with "is distinct from" condition in where or join clause fails // JavaDoc注释内容
   * with AssertionError: Cast for just nullability not allowed</a>. */ // JavaDoc注释内容
  @Test void testIsNotDistinctInFilter() { // 测试方法:测试IsNotDistinctInFilter功能
    CalciteAssert.that() // 代码行
      .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
      .query("select *\n" // 代码行
          + "  from \"foodmart\".\"employee\" as e1\n" // 代码行
          + "  where e1.\"last_name\" is distinct from e1.\"last_name\"") // 代码行
      .runs(); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2029">[CALCITE-2029] // JavaDoc注释内容
   * Query with "is distinct from" condition in where or join clause fails // JavaDoc注释内容
   * with AssertionError: Cast for just nullability not allowed</a>. */ // JavaDoc注释内容
  @Test void testMixedEqualAndIsNotDistinctJoin() { // 测试方法:测试MixedEqualAndIsNotDistinctJoin功能
    CalciteAssert.that() // 代码行
      .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
      .query("select *\n" // 代码行
          + "  from \"foodmart\".\"employee\" as e1\n" // 代码行
          + "  join \"foodmart\".\"employee\" as e2 on\n" // 代码行
          + "  e1.\"first_name\" = e1.\"first_name\"\n" // 代码行
          + "  and e1.\"last_name\" is distinct from e2.\"last_name\"") // 代码行
      .runs(); // 代码行
  } // 代码行
 // 空行
  /** A join that has both equi and non-equi conditions. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-371">[CALCITE-371] // JavaDoc注释内容
   * Cannot implement JOIN whose ON clause contains mixed equi and theta</a>. */ // JavaDoc注释内容
  @Test void testEquiThetaJoin() { // 测试方法:测试EquiThetaJoin功能
    CalciteAssert.hr() // 代码行
        .query("select e.\"empid\", d.\"name\", e.\"name\"\n" // 代码行
            + "from \"hr\".\"emps\" as e\n" // 代码行
            + "join \"hr\".\"depts\" as d\n" // 代码行
            + "on e.\"deptno\" = d.\"deptno\"\n" // 代码行
            + "and e.\"name\" <> d.\"name\"\n") // 代码行
        .returns("empid=100; name=Sales; name=Bill\n" // 代码行
            + "empid=150; name=Sales; name=Sebastian\n" // 代码行
            + "empid=110; name=Sales; name=Theodore\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-451">[CALCITE-451] // JavaDoc注释内容
   * Implement theta join, inner and outer, in enumerable convention</a>. */ // JavaDoc注释内容
  @Test void testThetaJoin() { // 测试方法:测试ThetaJoin功能
    CalciteAssert.hr() // 代码行
        .query( // 代码行
            "select e.\"empid\", d.\"name\", e.\"name\"\n" // 代码行
            + "from \"hr\".\"emps\" as e\n" // 代码行
            + "left join \"hr\".\"depts\" as d\n" // 代码行
            + "on e.\"deptno\" < d.\"deptno\"\n") // 代码行
        .returnsUnordered("empid=100; name=Marketing; name=Bill", // 代码行
            "empid=100; name=HR; name=Bill", // 代码行
            "empid=200; name=Marketing; name=Eric", // 代码行
            "empid=200; name=HR; name=Eric", // 代码行
            "empid=150; name=Marketing; name=Sebastian", // 代码行
            "empid=150; name=HR; name=Sebastian", // 代码行
            "empid=110; name=Marketing; name=Theodore", // 代码行
            "empid=110; name=HR; name=Theodore"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-35">[CALCITE-35] // JavaDoc注释内容
   * Support parenthesized sub-clause in JOIN</a>. */ // JavaDoc注释内容
  @Test void testJoinJoin() { // 测试方法:测试JoinJoin功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select\n" // 代码行
            + "   \"product_class\".\"product_family\" as \"c0\",\n" // 代码行
            + "   \"product_class\".\"product_department\" as \"c1\",\n" // 代码行
            + "   \"customer\".\"country\" as \"c2\",\n" // 代码行
            + "   \"customer\".\"state_province\" as \"c3\",\n" // 代码行
            + "   \"customer\".\"city\" as \"c4\"\n" // 代码行
            + "from\n" // 代码行
            + "   \"sales_fact_1997\" as \"sales_fact_1997\"\n" // 代码行
            + "join (\"product\" as \"product\"\n" // 代码行
            + "     join \"product_class\" as \"product_class\"\n" // 代码行
            + "     on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\")\n" // 代码行
            + "on  \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
            + "join \"customer\" as \"customer\"\n" // 代码行
            + "on  \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n" // 代码行
            + "join \"promotion\" as \"promotion\"\n" // 代码行
            + "on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n" // 代码行
            + "where (\"promotion\".\"media_type\" = 'Radio'\n" // 代码行
            + " or \"promotion\".\"media_type\" = 'TV'\n" // 代码行
            + " or \"promotion\".\"media_type\" = 'Sunday Paper'\n" // 代码行
            + " or \"promotion\".\"media_type\" = 'Street Handout')\n" // 代码行
            + " and (\"product_class\".\"product_family\" = 'Drink')\n" // 代码行
            + " and (\"customer\".\"country\" = 'USA'\n" // 代码行
            + "   and \"customer\".\"state_province\" = 'WA'\n" // 代码行
            + "   and \"customer\".\"city\" = 'Bellingham')\n" // 代码行
            + "group by \"product_class\".\"product_family\",\n" // 代码行
            + "   \"product_class\".\"product_department\",\n" // 代码行
            + "   \"customer\".\"country\",\n" // 代码行
            + "   \"customer\".\"state_province\",\n" // 代码行
            + "   \"customer\".\"city\"\n" // 代码行
            + "order by \"product_class\".\"product_family\" asc nulls first,\n" // 代码行
            + "   \"product_class\".\"product_department\" asc nulls first,\n" // 代码行
            + "   \"customer\".\"country\" asc nulls first,\n" // 代码行
            + "   \"customer\".\"state_province\" asc nulls first,\n" // 代码行
            + "   \"customer\".\"city\" asc nulls first") // 代码行
        .returnsUnordered( // 代码行
            "c0=Drink; c1=Alcoholic Beverages; c2=USA; c3=WA; c4=Bellingham", // 代码行
            "c0=Drink; c1=Dairy; c2=USA; c3=WA; c4=Bellingham"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6930">[CALCITE-6930] // JavaDoc注释内容
   * Implementing JoinConditionOrExpansionRule</a>. */ // JavaDoc注释内容
  @Test void testJoinConditionOrExpansionRule() { // 测试方法:测试JoinConditionOrExpansionRule功能
    final String sql = "" // 代码行
        + "SELECT \"t1\".\"deptno\", \"t1\".\"empid\", \"t2\".\"deptno\", \"t2\".\"empid\"\n" // 代码行
        + "FROM \"hr\".\"emps\" AS \"t1\"\n" // 代码行
        + "INNER JOIN (\n" // 代码行
        + "    SELECT (\"deptno\" + 10) AS \"deptno\", (\"empid\" + 100) AS \"empid\" FROM \"hr\".\"emps\"\n" // 代码行
        + ") AS \"t2\"\n" // 代码行
        + "ON (\"t1\".\"deptno\" = \"t2\".\"deptno\") OR (\"t1\".\"empid\" = \"t2\".\"empid\")"; // 代码行
 // 空行
    String[] returns = new String[] { // 代码行
        "deptno=20; empid=200; deptno=20; empid=200", // 代码行
        "deptno=20; empid=200; deptno=20; empid=210", // 代码行
        "deptno=20; empid=200; deptno=20; empid=250"}; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .returnsUnordered(returns); // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 代码行
          planner.addRule(CoreRules.JOIN_EXPAND_OR_TO_UNION_RULE); // 代码行
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 代码行
        }) // 代码行
        .returnsUnordered(returns); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6930">[CALCITE-6930] // JavaDoc注释内容
   * Implementing JoinConditionOrExpansionRule</a>. */ // JavaDoc注释内容
  @Test void testJoinConditionOrExpansionRuleMultiOr() { // 测试方法:测试JoinConditionOrExpansionRuleMultiOr功能
    final String sql = "" // 代码行
        + "SELECT \"t1\".\"deptno\", \"t1\".\"empid\", \"t2\".\"deptno\", \"t2\".\"empid\"\n" // 代码行
        + "FROM \"hr\".\"emps\" AS \"t1\"\n" // 代码行
        + "INNER JOIN (\n" // 代码行
        + "    SELECT (\"deptno\" + 10) AS \"deptno\", (\"empid\" + 100) AS \"empid\" FROM \"hr\".\"emps\"\n" // 代码行
        + ") AS \"t2\"\n" // 代码行
        + "ON (\"t1\".\"deptno\" = \"t2\".\"deptno\")" // 代码行
        + "OR (\"t1\".\"salary\" > 8000)" // 代码行
        + "OR (\"t1\".\"empid\" = \"t2\".\"empid\")"; // 代码行
 // 空行
    String[] returns = new String[] { // 代码行
        "deptno=10; empid=100; deptno=20; empid=200", // 代码行
        "deptno=10; empid=100; deptno=20; empid=210", // 代码行
        "deptno=10; empid=100; deptno=20; empid=250", // 代码行
        "deptno=10; empid=100; deptno=30; empid=300", // 代码行
        "deptno=10; empid=110; deptno=20; empid=200", // 代码行
        "deptno=10; empid=110; deptno=20; empid=210", // 代码行
        "deptno=10; empid=110; deptno=20; empid=250", // 代码行
        "deptno=10; empid=110; deptno=30; empid=300", // 代码行
        "deptno=20; empid=200; deptno=20; empid=200", // 代码行
        "deptno=20; empid=200; deptno=20; empid=210", // 代码行
        "deptno=20; empid=200; deptno=20; empid=250"}; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .returnsUnordered(returns); // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 代码行
          planner.addRule(CoreRules.JOIN_EXPAND_OR_TO_UNION_RULE); // 代码行
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 代码行
        }) // 代码行
        .returnsUnordered(returns); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6930">[CALCITE-6930] // JavaDoc注释内容
   * Implementing JoinConditionOrExpansionRule</a>. */ // JavaDoc注释内容
  @Test void testJoinConditionOrExpansionRuleLeft() { // 测试方法:测试JoinConditionOrExpansionRuleLeft功能
    final String sql = "" // 代码行
        + "SELECT \"t1\".\"deptno\", \"t1\".\"empid\", \"t2\".\"deptno\", \"t2\".\"empid\"\n" // 代码行
        + "FROM \"hr\".\"emps\" AS \"t1\"\n" // 代码行
        + "LEFT JOIN (\n" // 代码行
        + "    SELECT (\"deptno\" + 10) AS \"deptno\", (\"empid\" + 100) AS \"empid\" FROM \"hr\".\"emps\"\n" // 代码行
        + ") AS \"t2\"\n" // 代码行
        + "ON (\"t1\".\"deptno\" = \"t2\".\"deptno\") OR (\"t1\".\"empid\" = \"t2\".\"empid\")"; // 代码行
 // 空行
    String[] returns = new String[] { // 代码行
        "deptno=10; empid=100; deptno=null; empid=null", // 代码行
        "deptno=10; empid=110; deptno=null; empid=null", // 代码行
        "deptno=10; empid=150; deptno=null; empid=null", // 代码行
        "deptno=20; empid=200; deptno=20; empid=200", // 代码行
        "deptno=20; empid=200; deptno=20; empid=210", // 代码行
        "deptno=20; empid=200; deptno=20; empid=250"}; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .returnsUnordered(returns); // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 代码行
          planner.addRule(CoreRules.JOIN_EXPAND_OR_TO_UNION_RULE); // 代码行
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 代码行
        }) // 代码行
        .returnsUnordered(returns); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6930">[CALCITE-6930] // JavaDoc注释内容
   * Implementing JoinConditionOrExpansionRule</a>. */ // JavaDoc注释内容
  @Test void testJoinConditionOrExpansionRuleLeftUnion() { // 测试方法:测试JoinConditionOrExpansionRuleLeftUnion功能
    final String sql = "" // 代码行
        + "SELECT \"t1\".\"deptno\", \"t1\".\"empid\", \"t2\".\"deptno\", \"t2\".\"empid\"\n" // 代码行
        + "FROM \"hr\".\"emps\" AS \"t1\"\n" // 代码行
        + "LEFT JOIN (\n" // 代码行
        + "    SELECT (\"deptno\" + 10) AS \"deptno\", (\"empid\" + 100) AS \"empid\" FROM \"hr\".\"emps\"\n" // 代码行
        + "    UNION ALL\n" // 代码行
        + "    SELECT (\"deptno\" + 10) AS \"deptno\", (\"empid\" + 100) AS \"empid\" FROM \"hr\".\"emps\"\n" // 代码行
        + ") AS \"t2\"\n" // 代码行
        + "ON (\"t1\".\"deptno\" = \"t2\".\"deptno\") OR (\"t1\".\"empid\" = \"t2\".\"empid\")"; // 代码行
 // 空行
    String[] returns = new String[] { // 代码行
        "deptno=10; empid=100; deptno=null; empid=null", // 代码行
        "deptno=10; empid=110; deptno=null; empid=null", // 代码行
        "deptno=10; empid=150; deptno=null; empid=null", // 代码行
        "deptno=20; empid=200; deptno=20; empid=200", // 代码行
        "deptno=20; empid=200; deptno=20; empid=200", // 代码行
        "deptno=20; empid=200; deptno=20; empid=210", // 代码行
        "deptno=20; empid=200; deptno=20; empid=210", // 代码行
        "deptno=20; empid=200; deptno=20; empid=250", // 代码行
        "deptno=20; empid=200; deptno=20; empid=250"}; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .returnsUnordered(returns); // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 代码行
          planner.addRule(CoreRules.JOIN_EXPAND_OR_TO_UNION_RULE); // 代码行
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 代码行
        }) // 代码行
        .returnsUnordered(returns); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6930">[CALCITE-6930] // JavaDoc注释内容
   * Implementing JoinConditionOrExpansionRule</a>. */ // JavaDoc注释内容
  @Test void testJoinConditionOrExpansionRuleLeftWithNull() { // 测试方法:测试JoinConditionOrExpansionRuleLeftWithNull功能
    final String sql = "" // 代码行
        + "SELECT \"t1\".\"deptno\", \"t1\".\"commission\", \"t2\".\"deptno\", \"t2\".\"commission\"\n" // 代码行
        + "FROM \"hr\".\"emps\" AS \"t1\"\n" // 代码行
        + "LEFT JOIN (\n" // 代码行
        + "    SELECT (\"deptno\" + 10) AS \"deptno\", \"commission\" FROM \"hr\".\"emps\"\n" // 代码行
        + ") AS \"t2\"\n" // 代码行
        + "ON (\"t1\".\"deptno\" = \"t2\".\"deptno\") OR (\"t1\".\"commission\" = \"t2\".\"commission\")"; // 代码行
 // 空行
    String[] returns = new String[] { // 代码行
        "deptno=10; commission=1000; deptno=20; commission=1000", // 代码行
        "deptno=10; commission=250; deptno=20; commission=250", // 代码行
        "deptno=10; commission=null; deptno=null; commission=null", // 代码行
        "deptno=20; commission=500; deptno=20; commission=1000", // 代码行
        "deptno=20; commission=500; deptno=20; commission=250", // 代码行
        "deptno=20; commission=500; deptno=20; commission=null", // 代码行
        "deptno=20; commission=500; deptno=30; commission=500"}; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .returnsUnordered(returns); // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 代码行
          planner.addRule(CoreRules.JOIN_EXPAND_OR_TO_UNION_RULE); // 代码行
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 代码行
        }) // 代码行
        .returnsUnordered(returns); // 代码行
  } // 代码行
 // 空行
  /** Four-way join. Used to take 80 seconds. */ // JavaDoc注释开始
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testJoinFiveWay() { // 测试方法:测试JoinFiveWay功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"store\".\"store_country\" as \"c0\",\n" // 代码行
            + " \"time_by_day\".\"the_year\" as \"c1\",\n" // 代码行
            + " \"product_class\".\"product_family\" as \"c2\",\n" // 代码行
            + " count(\"sales_fact_1997\".\"product_id\") as \"m0\"\n" // 代码行
            + "from \"store\" as \"store\",\n" // 代码行
            + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
            + " \"time_by_day\" as \"time_by_day\",\n" // 代码行
            + " \"product_class\" as \"product_class\",\n" // 代码行
            + " \"product\" as \"product\"\n" // 代码行
            + "where \"sales_fact_1997\".\"store_id\" = \"store\".\"store_id\"\n" // 代码行
            + "and \"store\".\"store_country\" = 'USA'\n" // 代码行
            + "and \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
            + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
            + "and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
            + "and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n" // 代码行
            + "group by \"store\".\"store_country\",\n" // 代码行
            + " \"time_by_day\".\"the_year\",\n" // 代码行
            + " \"product_class\".\"product_family\"") // 代码行
        .explainContains("" // 代码行
            + "EnumerableAggregateRel(group=[{0, 1, 2}], m0=[COUNT($3)])\n" // 代码行
            + "  EnumerableCalcRel(expr#0..61=[{inputs}], c0=[$t19], c1=[$t4], c2=[$t46], product_id=[$t34])\n" // 代码行
            + "    EnumerableJoinRel(condition=[=($35, $0)], joinType=[inner])\n" // 代码行
            + "      EnumerableCalcRel(expr#0..9=[{inputs}], expr#10=[CAST($t4):INTEGER], expr#11=[1997], expr#12=[=($t10, $t11)], proj#0..9=[{exprs}], $condition=[$t12])\n" // 代码行
            + "        EnumerableTableScan(table=[[foodmart2, time_by_day]])\n" // 代码行
            + "      EnumerableCalcRel(expr#0..51=[{inputs}], proj#0..23=[{exprs}], product_id=[$t44], time_id=[$t45], customer_id=[$t46], promotion_id=[$t47], store_id0=[$t48], store_sales=[$t49], store_cost=[$t50], unit_sales=[$t51], product_class_id=[$t24], product_subcategory=[$t25], product_category=[$t26], product_department=[$t27], product_family=[$t28], product_class_id0=[$t29], product_id0=[$t30], brand_name=[$t31], product_name=[$t32], SKU=[$t33], SRP=[$t34], gross_weight=[$t35], net_weight=[$t36], recyclable_package=[$t37], low_fat=[$t38], units_per_case=[$t39], cases_per_pallet=[$t40], shelf_width=[$t41], shelf_height=[$t42], shelf_depth=[$t43])\n" // 代码行
            + "        EnumerableJoinRel(condition=[=($48, $0)], joinType=[inner])\n" // 代码行
            + "          EnumerableCalcRel(expr#0..23=[{inputs}], expr#24=['USA'], expr#25=[=($t9, $t24)], proj#0..23=[{exprs}], $condition=[$t25])\n" // 代码行
            + "            EnumerableTableScan(table=[[foodmart2, store]])\n" // 代码行
            + "          EnumerableCalcRel(expr#0..27=[{inputs}], proj#0..4=[{exprs}], product_class_id0=[$t13], product_id=[$t14], brand_name=[$t15], product_name=[$t16], SKU=[$t17], SRP=[$t18], gross_weight=[$t19], net_weight=[$t20], recyclable_package=[$t21], low_fat=[$t22], units_per_case=[$t23], cases_per_pallet=[$t24], shelf_width=[$t25], shelf_height=[$t26], shelf_depth=[$t27], product_id0=[$t5], time_id=[$t6], customer_id=[$t7], promotion_id=[$t8], store_id=[$t9], store_sales=[$t10], store_cost=[$t11], unit_sales=[$t12])\n" // 代码行
            + "            EnumerableJoinRel(condition=[=($13, $0)], joinType=[inner])\n" // 代码行
            + "              EnumerableTableScan(table=[[foodmart2, product_class]])\n" // 代码行
            + "              EnumerableJoinRel(condition=[=($0, $9)], joinType=[inner])\n" // 代码行
            + "                EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n" // 代码行
            + "                EnumerableTableScan(table=[[foodmart2, product]])\n" // 代码行
            + "\n" // 代码行
            + "]>") // 代码行
        .returns("+-------+---------------------+-----+------+------------+\n" // 代码行
            + "| c0    | c1                  | c2  | c3   | c4         |\n" // 代码行
            + "+-------+---------------------+-----+------+------------+\n" // 代码行
            + "| Drink | Alcoholic Beverages | USA | WA   | Bellingham |\n" // 代码行
            + "| Drink | Dairy               | USA | WA   | Bellingham |\n" // 代码行
            + "+-------+---------------------+-----+------+------------+"); // 代码行
  } // 代码行
 // 空行
  /** Tests a simple (primary key to primary key) N-way join, with arbitrary // JavaDoc注释开始
   * N. */ // JavaDoc注释内容
  @Test void testJoinManyWay() { // 测试方法:测试JoinManyWay功能
    // Timings without LoptOptimizeJoinRule // 单行注释
    //    N  Time // 单行注释
    //   == ===== // 单行注释
    //    6     2 // 单行注释
    //   10    10 // 单行注释
    //   11    19 // 单行注释
    //   12    36 // 单行注释
    //   13   116 - OOM did not complete // 单行注释
    checkJoinNWay(1); // 代码行
    checkJoinNWay(3); // 代码行
    checkJoinNWay(13); // 代码行
  } // 代码行
 // 空行
  private static void checkJoinNWay(int n) { // 代码行
    assert n > 0; // 代码行
    final StringBuilder buf = new StringBuilder(); // 代码行
    buf.append("select count(*)"); // 代码行
    for (int i = 0; i < n; i++) { // 代码行
      buf.append(i == 0 ? "\nfrom " : ",\n") // 代码行
          .append("\"hr\".\"depts\" as d").append(i); // 代码行
    } // 代码行
    for (int i = 1; i < n; i++) { // 代码行
      buf.append(i == 1 ? "\nwhere" : "\nand").append(" d") // 代码行
          .append(i).append(".\"deptno\" = d") // 代码行
          .append(i - 1).append(".\"deptno\""); // 代码行
    } // 代码行
    CalciteAssert.hr() // 代码行
        .query(buf.toString()) // 代码行
        .returns("EXPR$0=3\n"); // 代码行
  } // 代码行
 // 空行
  /** Returns a list of (query, expected) pairs. The expected result is // JavaDoc注释开始
   * sometimes null. */ // JavaDoc注释内容
  private static List<Pair<String, String>> querify(String[] queries1) { // querify方法:将查询数组转换为(查询,期望结果)对列表
    final List<Pair<String, String>> list = new ArrayList<>(); // 代码行
    for (int i = 0; i < queries1.length; i++) { // 代码行
      String query = queries1[i]; // 代码行
      String expected = null; // 代码行
      if (i + 1 < queries1.length // 代码行
          && queries1[i + 1] != null // 代码行
          && !queries1[i + 1].startsWith("select")) { // 代码行
        expected = queries1[++i]; // 代码行
      } // 代码行
      list.add(Pair.of(query, expected)); // 代码行
    } // 代码行
    return list; // 代码行
  } // 代码行
 // 空行
  /** A selection of queries generated by Mondrian. */ // JavaDoc注释开始
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testCloneQueries() { // 测试方法:测试CloneQueries功能
    CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that() // 代码行
            .with(CalciteAssert.Config.FOODMART_CLONE); // 代码行
    for (Ord<Pair<String, String>> query : Ord.zip(FOODMART_QUERIES)) { // 代码行
      try { // 代码行
        // uncomment to run specific queries: // 单行注释
//      if (query.i != FOODMART_QUERIES.size() - 1) continue; // 单行注释
        final String sql = query.e.left; // 代码行
        if (sql.startsWith("ignore:")) { // 代码行
          continue; // 代码行
        } // 代码行
        final String expected = query.e.right; // 代码行
        final CalciteAssert.AssertQuery query1 = with.query(sql); // 代码行
        if (expected != null) { // 代码行
          if (sql.contains("order by")) { // 代码行
            query1.returns(expected); // 代码行
          } else { // 代码行
            query1.returnsUnordered(expected.split("\n")); // 代码行
          } // 代码行
        } else { // 代码行
          query1.runs(); // 代码行
        } // 代码行
      } catch (Throwable e) { // 代码行
        throw new RuntimeException("while running query #" + query.i, e); // 代码行
      } // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests accessing a measure via JDBC. */ // JavaDoc注释开始
  @Test void testMeasure() throws SQLException { // 测试方法:测试Measure功能
    Properties info = new Properties(); // 代码行
    info.put("fun", "calcite"); // 代码行
    final String sql = "SELECT x AS d, y + 1 AS MEASURE m\n" // 代码行
        + "FROM (VALUES ('a', 2), ('a', 3), ('b', 4)) AS t (x, y)\n"; // 代码行
    try (Connection calciteConnection = // 代码行
        DriverManager.getConnection("jdbc:calcite:", info); // 代码行
         Statement calciteStatement = calciteConnection.createStatement(); // 代码行
         ResultSet rs = calciteStatement.executeQuery(sql)) { // 代码行
      final ResultSetMetaData metaData = rs.getMetaData(); // 代码行
      assertThat(metaData.getColumnCount(), is(2)); // 代码行
 // 空行
      assertThat(metaData.getColumnName(1), is("D")); // 代码行
      assertThat(metaData.getColumnType(1), is(Types.CHAR)); // 代码行
      assertThat(metaData.getColumnTypeName(1), is("CHAR")); // 代码行
 // 空行
      // The type name is "INTEGER", not "MEASURE<INTEGER>", because measures // 单行注释
      // are automatically converted to values at the top level of a query. // 单行注释
      assertThat(metaData.getColumnName(2), is("M")); // 代码行
      assertThat(metaData.getColumnType(2), is(Types.INTEGER)); // 代码行
      assertThat(metaData.getColumnTypeName(2), is("INTEGER")); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests accessing a column in a JDBC source whose type is ARRAY. */ // JavaDoc注释开始
  @Test void testArray() throws Exception { // 测试方法:测试Array功能
    final String url = MultiJdbcSchemaJoinTest.TempDb.INSTANCE.getUrl(); // 代码行
    Connection baseConnection = DriverManager.getConnection(url); // 代码行
    Statement baseStmt = baseConnection.createStatement(); // 代码行
    baseStmt.execute("CREATE TABLE ARR_TABLE (\n" // 代码行
        + "ID INTEGER,\n" // 代码行
        + "VALS INTEGER ARRAY)"); // 代码行
    baseStmt.execute("INSERT INTO ARR_TABLE VALUES (1, ARRAY[1,2,3])"); // 代码行
    baseStmt.execute("CREATE TABLE ARR_TABLE2 (\n" // 代码行
        + "ID INTEGER,\n" // 代码行
        + "VALS INTEGER ARRAY,\n" // 代码行
        + "VALVALS VARCHAR(10) ARRAY)"); // 代码行
    baseStmt.execute( // 代码行
        "INSERT INTO ARR_TABLE2 VALUES (1, ARRAY[1,2,3], ARRAY['x','y'])"); // 代码行
    baseStmt.close(); // 代码行
    baseConnection.commit(); // 代码行
 // 空行
    Properties info = new Properties(); // 代码行
    info.put("model", // 代码行
        "inline:" // 代码行
            + "{\n" // 代码行
            + "  version: '1.0',\n" // 代码行
            + "  defaultSchema: 'BASEJDBC',\n" // 代码行
            + "  schemas: [\n" // 代码行
            + "     {\n" // 代码行
            + "       type: 'jdbc',\n" // 代码行
            + "       name: 'BASEJDBC',\n" // 代码行
            + "       jdbcDriver: '" + jdbcDriver.class.getName() + "',\n" // 代码行
            + "       jdbcUrl: '" + url + "',\n" // 代码行
            + "       jdbcCatalog: null,\n" // 代码行
            + "       jdbcSchema: null\n" // 代码行
            + "     }\n" // 代码行
            + "  ]\n" // 代码行
            + "}"); // 代码行
 // 空行
    Connection calciteConnection = // 代码行
        DriverManager.getConnection("jdbc:calcite:", info); // 代码行
    Statement calciteStatement = calciteConnection.createStatement(); // 代码行
    final String sql = "SELECT ID, VALS FROM ARR_TABLE"; // 代码行
    ResultSet rs = calciteStatement.executeQuery(sql); // 代码行
    assertTrue(rs.next()); // 代码行
    assertThat(rs.getInt(1), is(1)); // 代码行
    Array array = rs.getArray(2); // 代码行
    assertNotNull(array); // 代码行
    assertArrayEquals(new int[]{1, 2, 3}, (int[]) array.getArray()); // 代码行
    assertFalse(rs.next()); // 代码行
    rs.close(); // 代码行
 // 空行
    rs = // 代码行
        calciteStatement.executeQuery("SELECT ID, CARDINALITY(VALS), VALS[2]\n" // 代码行
            + "FROM ARR_TABLE"); // 代码行
    assertTrue(rs.next()); // 代码行
    assertThat(rs.getInt(1), is(1)); // 代码行
    assertThat(rs.getInt(2), is(3)); // 代码行
    assertThat(rs.getInt(3), is(2)); // 代码行
    assertFalse(rs.next()); // 代码行
    rs.close(); // 代码行
 // 空行
    rs = calciteStatement.executeQuery("SELECT * FROM ARR_TABLE2"); // 代码行
    final ResultSetMetaData metaData = rs.getMetaData(); // 代码行
    assertThat(metaData.getColumnTypeName(1), is("INTEGER")); // 代码行
    assertThat(metaData.getColumnTypeName(2), is("INTEGER ARRAY")); // 代码行
    assertThat(metaData.getColumnTypeName(3), is("VARCHAR(10) ARRAY")); // 代码行
    assertTrue(rs.next()); // 代码行
    assertThat(rs.getInt(1), is(1)); // 代码行
    assertThat(rs.getArray(2), notNullValue()); // 代码行
    assertThat(rs.getArray(3), notNullValue()); // 代码行
    assertFalse(rs.next()); // 代码行
 // 空行
    calciteConnection.close(); // 代码行
  } // 代码行
 // 空行
  /** Tests the {@code CARDINALITY} function applied to an array column. */ // JavaDoc注释开始
  @Test void testArray2() { // 测试方法:测试Array2功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\", cardinality(\"employees\") as c\n" // 代码行
            + "from \"hr\".\"depts\"") // 代码行
        .returnsUnordered("deptno=10; C=2", // 代码行
            "deptno=30; C=0", // 代码行
            "deptno=40; C=1"); // 代码行
  } // 代码行
 // 空行
  /** Tests JDBC support for nested arrays. */ // JavaDoc注释开始
  @Test void testNestedArray() throws Exception { // 测试方法:测试NestedArray功能
    CalciteAssert.hr() // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            final Statement statement = connection.createStatement(); // 代码行
            ResultSet resultSet = // 代码行
                statement.executeQuery("select \"empid\",\n" // 代码行
                    + "  array[\n" // 代码行
                    + "    array['x', 'y', 'z'],\n" // 代码行
                    + "    array[\"name\"]] as a\n" // 代码行
                    + "from \"hr\".\"emps\""); // 代码行
            assertThat(resultSet.next(), is(true)); // 代码行
            assertThat(resultSet.getInt(1), is(100)); // 代码行
            assertThat(resultSet.getString(2), // 代码行
                is("[[x, y, z], [Bill]]")); // 代码行
            final Array array = resultSet.getArray(2); // 代码行
            assertThat(array.getBaseType(), // 代码行
                is(Types.ARRAY)); // 代码行
            final Object[] arrayValues = // 代码行
                (Object[]) array.getArray(); // 代码行
            assertThat(arrayValues, arrayWithSize(2)); // 代码行
            final Array subArray = (Array) arrayValues[0]; // 代码行
            assertThat(subArray.getBaseType(), // 代码行
                is(Types.VARCHAR)); // 代码行
            final Object[] subArrayValues = // 代码行
                (Object[]) subArray.getArray(); // 代码行
            assertThat(subArrayValues, arrayWithSize(3)); // 代码行
            assertThat(subArrayValues[2], is("z")); // 代码行
 // 空行
            final ResultSet subResultSet = subArray.getResultSet(); // 代码行
            assertThat(subResultSet.next(), is(true)); // 代码行
            assertThat(subResultSet.getString(1), is("x")); // 代码行
            try { // 代码行
              final String string = subResultSet.getString(2); // 代码行
              fail("expected error, got " + string); // 代码行
            } catch (SQLException e) { // 代码行
              assertThat(e.getMessage(), // 代码行
                  is("invalid column ordinal: 2")); // 代码行
            } // 代码行
            assertThat(subResultSet.next(), is(true)); // 代码行
            assertThat(subResultSet.next(), is(true)); // 代码行
            assertThat(subResultSet.isAfterLast(), is(false)); // 代码行
            assertThat(subResultSet.getString(1), is("z")); // 代码行
            assertThat(subResultSet.next(), is(false)); // 代码行
            assertThat(subResultSet.isAfterLast(), is(true)); // 代码行
            statement.close(); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  @Test void testArrayConstructor() { // 测试方法:测试ArrayConstructor功能
    CalciteAssert.that() // 代码行
        .query("select array[1,2] as a from (values (1))") // 代码行
        .returnsUnordered("A=[1, 2]"); // 代码行
  } // 代码行
 // 空行
  @Test void testMultisetConstructor() { // 测试方法:测试MultisetConstructor功能
    CalciteAssert.that() // 代码行
        .query("select multiset[1,2] as a from (values (1))") // 代码行
        .returnsUnordered("A=[1, 2]"); // 代码行
  } // 代码行
 // 空行
  @Test void testMultisetQuery() { // 测试方法:测试MultisetQuery功能
    forEachExpand(this::checkMultisetQuery); // 代码行
  } // 代码行
 // 空行
  void checkMultisetQuery() { // 代码行
    CalciteAssert.hr() // 代码行
        .query("select multiset(\n" // 代码行
            + "  select \"deptno\", \"empid\" from \"hr\".\"emps\") as a\n" // 代码行
            + "from (values (1))") // 代码行
        .returnsUnordered("A=[{10, 100}, {20, 200}, {10, 150}, {10, 110}]"); // 代码行
  } // 代码行
 // 空行
  @Test void testMultisetQueryWithSingleColumn() { // 测试方法:测试MultisetQueryWithSingleColumn功能
    forEachExpand(this::checkMultisetQueryWithSingleColumn); // 代码行
  } // 代码行
 // 空行
  void checkMultisetQueryWithSingleColumn() { // 代码行
    CalciteAssert.hr() // 代码行
        .query("select multiset(\n" // 代码行
            + "  select \"deptno\" from \"hr\".\"emps\") as a\n" // 代码行
            + "from (values (1))") // 代码行
        .returnsUnordered("A=[10, 20, 10, 10]"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestArray() { // 测试方法:测试UnnestArray功能
    CalciteAssert.that() // 代码行
        .query("select*from unnest(array[1,2])") // 代码行
        .returnsUnordered("EXPR$0=1", // 代码行
            "EXPR$0=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestArrayWithOrdinality() { // 测试方法:测试UnnestArrayWithOrdinality功能
    CalciteAssert.that() // 代码行
        .query("select*from unnest(array[10,20]) with ordinality as t(i, o)") // 代码行
        .returnsUnordered("I=10; O=1", // 代码行
            "I=20; O=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestRecordType() { // 测试方法:测试UnnestRecordType功能
    // unnest(RecordType(Array)) // 单行注释
    CalciteAssert.that() // 代码行
        .query("select * from unnest\n" // 代码行
            + "(select t.x from (values array[10, 20], array[30, 40]) as t(x))\n" // 代码行
            + " with ordinality as t(a, o)") // 代码行
        .returnsUnordered("A=10; O=1", "A=20; O=2", // 代码行
            "A=30; O=1", "A=40; O=2"); // 代码行
 // 空行
    // unnest(RecordType(Multiset)) // 单行注释
    CalciteAssert.that() // 代码行
        .query("select * from unnest\n" // 代码行
            + "(select t.x from (values multiset[10, 20], array[30, 40]) as t(x))\n" // 代码行
            + " with ordinality as t(a, o)") // 代码行
        .returnsUnordered("A=10; O=1", "A=20; O=2", // 代码行
            "A=30; O=1", "A=40; O=2"); // 代码行
 // 空行
    // unnest(RecordType(Map)) // 单行注释
    CalciteAssert.that() // 代码行
        .query("select * from unnest\n" // 代码行
            + "(select t.x from (values map['a', 20], map['b', 30], map['c', 40]) as t(x))\n" // 代码行
            + " with ordinality as t(a, b, o)") // 代码行
        .returnsUnordered("A=a; B=20; O=1", // 代码行
            "A=b; B=30; O=1", // 代码行
            "A=c; B=40; O=1"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestMultiset() { // 测试方法:测试UnnestMultiset功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select*from unnest(multiset[1,2]) as t(c)") // 代码行
        .returnsUnordered("C=1", "C=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestMultiset2() { // 测试方法:测试UnnestMultiset2功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select*from unnest(\n" // 代码行
            + " select \"employees\" from \"hr\".\"depts\"\n" // 代码行
            + " where \"deptno\" = 10)") // 代码行
        .returnsUnordered( // 代码行
            "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000", // 代码行
            "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2391">[CALCITE-2391] // JavaDoc注释内容
   * Aggregate query with UNNEST or LATERAL fails with // JavaDoc注释内容
   * ClassCastException</a>. */ // JavaDoc注释内容
  @Test void testAggUnnestColumn() { // 测试方法:测试AggUnnestColumn功能
    final String sql = "select count(d.\"name\") as c\n" // 代码行
        + "from \"hr\".\"depts\" as d,\n" // 代码行
        + " UNNEST(d.\"employees\") as e"; // 代码行
    CalciteAssert.hr().query(sql).returnsUnordered("C=3"); // 代码行
  } // 代码行
 // 空行
  @Test void testArrayElement() { // 测试方法:测试ArrayElement功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select element(\"employees\") from \"hr\".\"depts\"\n" // 代码行
            + "where cardinality(\"employees\") < 2") // 代码行
        .returnsUnordered("EXPR$0={200, 20, Eric, 8000.0, 500}", // 代码行
            "EXPR$0=null"); // 代码行
  } // 代码行
 // 空行
  @Test void testLateral() { // 测试方法:测试Lateral功能
    CalciteAssert.hr() // 代码行
        .query("select * from \"hr\".\"emps\",\n" // 代码行
            + " LATERAL (select * from \"hr\".\"depts\" where \"emps\".\"deptno\" = \"depts\".\"deptno\")") // 代码行
        .returnsUnordered( // 代码行
            "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000; deptno0=10; name0=Sales; employees=[{100, 10, Bill, 10000.0, 1000}, {150, 10, Sebastian, 7000.0, null}]; location={-122, 38}", // 代码行
            "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250; deptno0=10; name0=Sales; employees=[{100, 10, Bill, 10000.0, 1000}, {150, 10, Sebastian, 7000.0, null}]; location={-122, 38}", // 代码行
            "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null; deptno0=10; name0=Sales; employees=[{100, 10, Bill, 10000.0, 1000}, {150, 10, Sebastian, 7000.0, null}]; location={-122, 38}"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-531">[CALCITE-531] // JavaDoc注释内容
   * Window function does not work in LATERAL</a>. */ // JavaDoc注释内容
  @Test void testLateralWithOver() { // 测试方法:测试LateralWithOver功能
    final String sql = "select \"emps\".\"name\", d.\"deptno\", d.m\n" // 代码行
        + "from \"hr\".\"emps\",\n" // 代码行
        + "  LATERAL (\n" // 代码行
        + "    select \"depts\".\"deptno\",\n" // 代码行
        + "      max(\"deptno\" + \"emps\".\"empid\") over (\n" // 代码行
        + "        partition by \"emps\".\"deptno\") as m\n" // 代码行
        + "     from \"hr\".\"depts\"\n" // 代码行
        + "     where \"emps\".\"deptno\" = \"depts\".\"deptno\") as d"; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query(sql) // 代码行
        .returnsUnordered("name=Bill; deptno=10; M=190", // 代码行
            "name=Bill; deptno=30; M=190", // 代码行
            "name=Bill; deptno=40; M=190", // 代码行
            "name=Eric; deptno=10; M=240", // 代码行
            "name=Eric; deptno=30; M=240", // 代码行
            "name=Eric; deptno=40; M=240", // 代码行
            "name=Sebastian; deptno=10; M=190", // 代码行
            "name=Sebastian; deptno=30; M=190", // 代码行
            "name=Sebastian; deptno=40; M=190", // 代码行
            "name=Theodore; deptno=10; M=190", // 代码行
            "name=Theodore; deptno=30; M=190", // 代码行
            "name=Theodore; deptno=40; M=190"); // 代码行
  } // 代码行
 // 空行
  /** Per SQL std, UNNEST is implicitly LATERAL. */ // JavaDoc注释开始
  @Test void testUnnestArrayColumn() { // 测试方法:测试UnnestArrayColumn功能
    CalciteAssert.hr() // 代码行
        .query("select d.\"name\", e.*\n" // 代码行
            + "from \"hr\".\"depts\" as d,\n" // 代码行
            + " UNNEST(d.\"employees\") as e") // 代码行
        .returnsUnordered( // 代码行
            "name=HR; empid=200; deptno=20; name0=Eric; salary=8000.0; commission=500", // 代码行
            "name=Sales; empid=100; deptno=10; name0=Bill; salary=10000.0; commission=1000", // 代码行
            "name=Sales; empid=150; deptno=10; name0=Sebastian; salary=7000.0; commission=null"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestArrayScalarArray() { // 测试方法:测试UnnestArrayScalarArray功能
    CalciteAssert.hr() // 代码行
        .query("select d.\"name\", e.*\n" // 代码行
            + "from \"hr\".\"depts\" as d,\n" // 代码行
            + " UNNEST(d.\"employees\", array[1, 2]) as e") // 代码行
        .returnsUnordered( // 代码行
            "name=HR; empid=200; deptno=20; name0=Eric; salary=8000.0; commission=500; EXPR$1=1", // 代码行
            "name=HR; empid=200; deptno=20; name0=Eric; salary=8000.0; commission=500; EXPR$1=2", // 代码行
            "name=Sales; empid=100; deptno=10; name0=Bill; salary=10000.0; commission=1000; EXPR$1=1", // 代码行
            "name=Sales; empid=100; deptno=10; name0=Bill; salary=10000.0; commission=1000; EXPR$1=2", // 代码行
            "name=Sales; empid=150; deptno=10; name0=Sebastian; salary=7000.0; commission=null; EXPR$1=1", // 代码行
            "name=Sales; empid=150; deptno=10; name0=Sebastian; salary=7000.0; commission=null; EXPR$1=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestArrayScalarArrayAliased() { // 测试方法:测试UnnestArrayScalarArrayAliased功能
    CalciteAssert.hr() // 代码行
        .query("select d.\"name\", e.*\n" // 代码行
            + "from \"hr\".\"depts\" as d,\n" // 代码行
            + " UNNEST(d.\"employees\", array[1, 2]) as e (ei, d, n, s, c, i)\n" // 代码行
            + "where ei + i > 151") // 代码行
        .returnsUnordered( // 代码行
            "name=HR; EI=200; D=20; N=Eric; S=8000.0; C=500; I=1", // 代码行
            "name=HR; EI=200; D=20; N=Eric; S=8000.0; C=500; I=2", // 代码行
            "name=Sales; EI=150; D=10; N=Sebastian; S=7000.0; C=null; I=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestArrayScalarArrayWithOrdinal() { // 测试方法:测试UnnestArrayScalarArrayWithOrdinal功能
    CalciteAssert.hr() // 代码行
        .query("select d.\"name\", e.*\n" // 代码行
            + "from \"hr\".\"depts\" as d,\n" // 代码行
            + " UNNEST(d.\"employees\", array[1, 2]) with ordinality as e (ei, d, n, s, c, i, o)\n" // 代码行
            + "where ei + i > 151") // 代码行
        .returnsUnordered( // 代码行
            "name=HR; EI=200; D=20; N=Eric; S=8000.0; C=500; I=1; O=1", // 代码行
            "name=HR; EI=200; D=20; N=Eric; S=8000.0; C=500; I=2; O=2", // 代码行
            "name=Sales; EI=150; D=10; N=Sebastian; S=7000.0; C=null; I=2; O=4"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3498">[CALCITE-3498] // JavaDoc注释内容
   * Unnest operation's ordinality should be deterministic</a>. */ // JavaDoc注释内容
  @Test void testUnnestArrayWithDeterministicOrdinality() { // 测试方法:测试UnnestArrayWithDeterministicOrdinality功能
    CalciteAssert.that() // 代码行
        .query("select v, o\n" // 代码行
            + "from unnest(array[100, 200]) with ordinality as t1(v, o)\n" // 代码行
            + "where v > 1") // 代码行
        .returns("V=100; O=1\n" // 代码行
            + "V=200; O=2\n"); // 代码行
 // 空行
    CalciteAssert.that() // 代码行
        .query("with\n" // 代码行
            + "  x as (select * from unnest(array[100, 200]) with ordinality as t1(v, o)), " // 代码行
            + "  y as (select * from unnest(array[1000, 2000]) with ordinality as t2(v, o))\n" // 代码行
            + "select x.o as o1, x.v as v1, y.o as o2, y.v as v2 " // 代码行
            + "from x join y on x.o=y.o") // 代码行
        .returnsUnordered( // 代码行
            "O1=1; V1=100; O2=1; V2=1000", // 代码行
            "O1=2; V1=200; O2=2; V2=2000"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1250">[CALCITE-1250] // JavaDoc注释内容
   * UNNEST applied to MAP data type</a>. */ // JavaDoc注释内容
  @Test void testUnnestItemsInMap() throws SQLException { // 测试方法:测试UnnestItemsInMap功能
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 代码行
    final String sql = "select * from unnest(MAP['a', 1, 'b', 2]) as um(k, v)"; // 代码行
    ResultSet resultSet = connection.createStatement().executeQuery(sql); // 代码行
    final String expected = "K=a; V=1\n" // 代码行
        + "K=b; V=2\n"; // 代码行
    assertThat(CalciteAssert.toString(resultSet), is(expected)); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestItemsInMapWithOrdinality() throws SQLException { // 测试方法:测试UnnestItemsInMapWithOrdinality功能
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 代码行
    final String sql = "select *\n" // 代码行
        + "from unnest(MAP['a', 1, 'b', 2]) with ordinality as um(k, v, i)"; // 代码行
    ResultSet resultSet = connection.createStatement().executeQuery(sql); // 代码行
    final String expected = "K=a; V=1; I=1\n" // 代码行
        + "K=b; V=2; I=2\n"; // 代码行
    assertThat(CalciteAssert.toString(resultSet), is(expected)); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  @Test void testUnnestItemsInMapWithNoAliasAndAdditionalArgument() // 测试方法:测试UnnestItemsInMapWithNoAliasAndAdditionalArgument功能
      throws SQLException { // 代码行
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 代码行
    final String sql = // 代码行
        "select * from unnest(MAP['a', 1, 'b', 2], array[5, 6, 7])"; // 代码行
    ResultSet resultSet = connection.createStatement().executeQuery(sql); // 代码行
 // 空行
    List<String> map = FlatLists.of("KEY=a; VALUE=1", "KEY=b; VALUE=2"); // 代码行
    List<String> array = FlatLists.of(" EXPR$1=5", " EXPR$1=6", " EXPR$1=7"); // 代码行
 // 空行
    final StringBuilder b = new StringBuilder(); // 代码行
    for (List<String> row : Linq4j.product(FlatLists.of(map, array))) { // 代码行
      b.append(row.get(0)).append(";").append(row.get(1)).append("\n"); // 代码行
    } // 代码行
    final String expected = b.toString(); // 代码行
 // 空行
    assertThat(CalciteAssert.toString(resultSet), is(expected)); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  private CalciteAssert.AssertQuery withFoodMartQuery(int id) // withFoodMartQuery方法:获取FoodMart查询
      throws IOException { // 代码行
    final FoodMartQuerySet set = FoodMartQuerySet.instance(); // 代码行
    return CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(set.queries.get(id).sql); // 代码行
  } // 代码行
 // 空行
  /** Makes sure that a projection introduced by a call to // JavaDoc注释开始
   * {@link org.apache.calcite.rel.rules.JoinCommuteRule} does not // JavaDoc注释内容
   * manifest as an // JavaDoc注释内容
   * {@link org.apache.calcite.adapter.enumerable.EnumerableCalc} in the // JavaDoc注释内容
   * plan. // JavaDoc注释内容
   * // JavaDoc注释内容
   * <p>Test case for (not yet fixed) // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-92">[CALCITE-92] // JavaDoc注释内容
   * Project should be optimized away, not converted to EnumerableCalcRel</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testNoCalcBetweenJoins() throws IOException { // 测试方法:测试NoCalcBetweenJoins功能
    final FoodMartQuerySet set = FoodMartQuerySet.instance(); // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(set.queries.get(16).sql) // 代码行
        .explainContains("" // 代码行
            + "EnumerableSortRel(sort0=[$0], sort1=[$1], sort2=[$2], sort3=[$4], sort4=[$10], sort5=[$11], sort6=[$12], sort7=[$13], sort8=[$22], sort9=[$23], sort10=[$24], sort11=[$25], sort12=[$26], sort13=[$27], dir0=[Ascending-nulls-last], dir1=[Ascending-nulls-last], dir2=[Ascending-nulls-last], dir3=[Ascending-nulls-last], dir4=[Ascending-nulls-last], dir5=[Ascending-nulls-last], dir6=[Ascending-nulls-last], dir7=[Ascending-nulls-last], dir8=[Ascending-nulls-last], dir9=[Ascending-nulls-last], dir10=[Ascending-nulls-last], dir11=[Ascending-nulls-last], dir12=[Ascending-nulls-last], dir13=[Ascending-nulls-last])\n" // 代码行
            + "  EnumerableCalcRel(expr#0..26=[{inputs}], proj#0..4=[{exprs}], c5=[$t4], c6=[$t5], c7=[$t6], c8=[$t7], c9=[$t8], c10=[$t9], c11=[$t10], c12=[$t11], c13=[$t12], c14=[$t13], c15=[$t14], c16=[$t15], c17=[$t16], c18=[$t17], c19=[$t18], c20=[$t19], c21=[$t20], c22=[$t21], c23=[$t22], c24=[$t23], c25=[$t24], c26=[$t25], c27=[$t26])\n" // 代码行
            + "    EnumerableAggregateRel(group=[{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26}])\n" // 代码行
            + "      EnumerableCalcRel(expr#0..80=[{inputs}], c0=[$t12], c1=[$t10], c2=[$t9], c3=[$t0], fullname=[$t28], c6=[$t19], c7=[$t17], c8=[$t22], c9=[$t18], c10=[$t46], c11=[$t44], c12=[$t43], c13=[$t40], c14=[$t38], c15=[$t47], c16=[$t52], c17=[$t53], c18=[$t54], c19=[$t55], c20=[$t56], c21=[$t42], c22=[$t80], c23=[$t79], c24=[$t78], c25=[$t77], c26=[$t63], c27=[$t64])\n" // 代码行
            + "        EnumerableJoinRel(condition=[=($61, $76)], joinType=[inner])\n" // 代码行
            + "          EnumerableJoinRel(condition=[=($29, $62)], joinType=[inner])\n" // 代码行
            + "            EnumerableJoinRel(condition=[=($33, $37)], joinType=[inner])\n" // 代码行
            + "              EnumerableCalcRel(expr#0..36=[{inputs}], customer_id=[$t8], account_num=[$t9], lname=[$t10], fname=[$t11], mi=[$t12], address1=[$t13], address2=[$t14], address3=[$t15], address4=[$t16], city=[$t17], state_province=[$t18], postal_code=[$t19], country=[$t20], customer_region_id=[$t21], phone1=[$t22], phone2=[$t23], birthdate=[$t24], marital_status=[$t25], yearly_income=[$t26], gender=[$t27], total_children=[$t28], num_children_at_home=[$t29], education=[$t30], date_accnt_opened=[$t31], member_card=[$t32], occupation=[$t33], houseowner=[$t34], num_cars_owned=[$t35], fullname=[$t36], product_id=[$t0], time_id=[$t1], customer_id0=[$t2], promotion_id=[$t3], store_id=[$t4], store_sales=[$t5], store_cost=[$t6], unit_sales=[$t7])\n" // 代码行
            + "                EnumerableJoinRel(condition=[=($2, $8)], joinType=[inner])\n" // 代码行
            + "                  EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n" // 代码行
            + "                  EnumerableTableScan(table=[[foodmart2, customer]])\n" // 代码行
            + "              EnumerableTableScan(table=[[foodmart2, store]])\n" // 代码行
            + "            EnumerableTableScan(table=[[foodmart2, product]])\n" // 代码行
            + "          EnumerableTableScan(table=[[foodmart2, product_class]])\n"); // 代码行
  } // 代码行
 // 空行
  /** Checks that a 3-way join is re-ordered so that join conditions can be // JavaDoc注释开始
   * applied. The plan must not contain cartesian joins. // JavaDoc注释内容
   * {@link org.apache.calcite.rel.rules.JoinPushThroughJoinRule} makes this // JavaDoc注释内容
   * possible. */ // JavaDoc注释内容
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testExplainJoin() { // 测试方法:测试ExplainJoin功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(FOODMART_QUERIES.get(48).left) // 代码行
        .explainContains("" // 代码行
            + "EnumerableAggregateRel(group=[{}], m0=[COUNT($0)])\n" // 代码行
            + "  EnumerableAggregateRel(group=[{0}])\n" // 代码行
            + "    EnumerableCalcRel(expr#0..27=[{inputs}], customer_id=[$t7])\n" // 代码行
            + "      EnumerableJoinRel(condition=[=($13, $0)], joinType=[inner])\n" // 代码行
            + "        EnumerableTableScan(table=[[foodmart2, product_class]])\n" // 代码行
            + "        EnumerableJoinRel(condition=[=($0, $9)], joinType=[inner])\n" // 代码行
            + "          EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n" // 代码行
            + "          EnumerableCalcRel(expr#0..14=[{inputs}], expr#15=['Cormorant'], expr#16=[=($t2, $t15)], proj#0..14=[{exprs}], $condition=[$t16])\n" // 代码行
            + "            EnumerableTableScan(table=[[foodmart2, product]]"); // 代码行
  } // 代码行
 // 空行
  /** Checks that a 3-way join is re-ordered so that join conditions can be // JavaDoc注释开始
   * applied. The plan is left-deep (agg_c_14_sales_fact_1997 the most // JavaDoc注释内容
   * rows, then time_by_day, then store). This makes for efficient // JavaDoc注释内容
   * hash-joins. */ // JavaDoc注释内容
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testExplainJoin2() throws IOException { // 测试方法:测试ExplainJoin2功能
    withFoodMartQuery(2482) // 代码行
        .explainContains("" // 代码行
            + "EnumerableSortRel(sort0=[$0], sort1=[$1], dir0=[Ascending-nulls-last], dir1=[Ascending-nulls-last])\n" // 代码行
            + "  EnumerableAggregateRel(group=[{0, 1}])\n" // 代码行
            + "    EnumerableCalcRel(expr#0..5=[{inputs}], c0=[$t4], c1=[$t1])\n" // 代码行
            + "      EnumerableJoinRel(condition=[=($3, $5)], joinType=[inner])\n" // 代码行
            + "        EnumerableCalcRel(expr#0..3=[{inputs}], store_id=[$t2], store_country=[$t3], store_id0=[$t0], month_of_year=[$t1])\n" // 代码行
            + "          EnumerableJoinRel(condition=[=($0, $2)], joinType=[inner])\n" // 代码行
            + "            EnumerableCalcRel(expr#0..10=[{inputs}], store_id=[$t2], month_of_year=[$t4])\n" // 代码行
            + "              EnumerableTableScan(table=[[foodmart2, agg_c_14_sales_fact_1997]])\n" // 代码行
            + "            EnumerableCalcRel(expr#0..23=[{inputs}], store_id=[$t0], store_country=[$t9])\n" // 代码行
            + "              EnumerableTableScan(table=[[foodmart2, store]])\n" // 代码行
            + "        EnumerableCalcRel(expr#0..9=[{inputs}], the_year=[$t4], month_of_year=[$t7])\n" // 代码行
            + "          EnumerableTableScan(table=[[foodmart2, time_by_day]])\n") // 代码行
        .runs(); // 代码行
  } // 代码行
 // 空行
  /** One of the most expensive foodmart queries. */ // JavaDoc注释开始
  @Disabled // OOME on Travis; works on most other machines // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testExplainJoin3() throws IOException { // 测试方法:测试ExplainJoin3功能
    withFoodMartQuery(8) // 代码行
        .explainContains("" // 代码行
            + "EnumerableSortRel(sort0=[$0], sort1=[$1], sort2=[$2], sort3=[$4], dir0=[Ascending-nulls-last], dir1=[Ascending-nulls-last], dir2=[Ascending-nulls-last], dir3=[Ascending-nulls-last])\n" // 代码行
            + "  EnumerableCalcRel(expr#0..8=[{inputs}], expr#9=['%Jeanne%'], expr#10=[LIKE($t4, $t9)], proj#0..4=[{exprs}], c5=[$t4], c6=[$t5], c7=[$t6], c8=[$t7], c9=[$t8], $condition=[$t10])\n" // 代码行
            + "    EnumerableAggregateRel(group=[{0, 1, 2, 3, 4, 5, 6, 7, 8}])\n" // 代码行
            + "      EnumerableCalcRel(expr#0..46=[{inputs}], c0=[$t12], c1=[$t10], c2=[$t9], c3=[$t0], fullname=[$t28], c6=[$t19], c7=[$t17], c8=[$t22], c9=[$t18])\n" // 代码行
            + "        EnumerableJoinRel(condition=[=($30, $37)], joinType=[inner])\n" // 代码行
            + "          EnumerableCalcRel(expr#0..36=[{inputs}], customer_id=[$t8], account_num=[$t9], lname=[$t10], fname=[$t11], mi=[$t12], address1=[$t13], address2=[$t14], address3=[$t15], address4=[$t16], city=[$t17], state_province=[$t18], postal_code=[$t19], country=[$t20], customer_region_id=[$t21], phone1=[$t22], phone2=[$t23], birthdate=[$t24], marital_status=[$t25], yearly_income=[$t26], gender=[$t27], total_children=[$t28], num_children_at_home=[$t29], education=[$t30], date_accnt_opened=[$t31], member_card=[$t32], occupation=[$t33], houseowner=[$t34], num_cars_owned=[$t35], fullname=[$t36], product_id=[$t0], time_id=[$t1], customer_id0=[$t2], promotion_id=[$t3], store_id=[$t4], store_sales=[$t5], store_cost=[$t6], unit_sales=[$t7])\n" // 代码行
            + "            EnumerableJoinRel(condition=[=($2, $8)], joinType=[inner])\n" // 代码行
            + "              EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n" // 代码行
            + "              EnumerableTableScan(table=[[foodmart2, customer]])\n" // 代码行
            + "          EnumerableCalcRel(expr#0..9=[{inputs}], expr#10=[CAST($t4):INTEGER], expr#11=[1997], expr#12=[=($t10, $t11)], proj#0..9=[{exprs}], $condition=[$t12])\n" // 代码行
            + "            EnumerableTableScan(table=[[foodmart2, time_by_day]])") // 代码行
        .runs(); // 代码行
  } // 代码行
 // 空行
  /** Tests that a relatively complex query on the foodmart schema creates // JavaDoc注释开始
   * an in-memory aggregate table and then uses it. */ // JavaDoc注释内容
  @Disabled // DO NOT CHECK IN // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testFoodmartLattice() throws IOException { // 测试方法:测试FoodmartLattice功能
    // 8: select ... from customer, sales, time ... group by ... // 单行注释
    final FoodMartQuerySet set = FoodMartQuerySet.instance(); // 代码行
    final FoodMartQuerySet.FoodmartQuery query = set.queries.get(8); // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART_WITH_LATTICE) // 代码行
        .withDefaultSchema("foodmart") // 代码行
        .pooled() // 代码行
        .query(query.sql) // 代码行
        .enableMaterializations(true) // 代码行
        .explainContains("" // 代码行
            + "EnumerableCalc(expr#0..8=[{inputs}], c0=[$t3], c1=[$t2], c2=[$t1], c3=[$t0], c4=[$t8], c5=[$t8], c6=[$t6], c7=[$t4], c8=[$t7], c9=[$t5])\n" // 代码行
            + "  EnumerableSort(sort0=[$3], sort1=[$2], sort2=[$1], sort3=[$8], dir0=[ASC-nulls-last], dir1=[ASC-nulls-last], dir2=[ASC-nulls-last], dir3=[ASC-nulls-last])\n" // 代码行
            + "    EnumerableAggregate(group=[{0, 1, 2, 3, 4, 5, 6, 7, 8}])\n" // 代码行
            + "      EnumerableCalc(expr#0..9=[{inputs}], expr#10=[CAST($t0):INTEGER], expr#11=[1997], expr#12=[=($t10, $t11)], expr#13=['%Jeanne%'], expr#14=[LIKE($t9, $t13)], expr#15=[AND($t12, $t14)], $f0=[$t1], $f1=[$t2], $f2=[$t3], $f3=[$t4], $f4=[$t5], $f5=[$t6], $f6=[$t7], $f7=[$t8], $f8=[$t9], $f9=[$t0], $condition=[$t15])\n" // 代码行
            + "        EnumerableTableScan(table=[[foodmart, m{12, 18, 27, 28, 30, 35, 36, 37, 40, 46}]])") // 代码行
        .runs(); // 代码行
  } // 代码行
 // 空行
  /** Test case for (not yet fixed) // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-99">[CALCITE-99] // JavaDoc注释内容
   * Recognize semi-join that has high selectivity and push it down</a>. */ // JavaDoc注释内容
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testExplainJoin4() throws IOException { // 测试方法:测试ExplainJoin4功能
    withFoodMartQuery(5217) // 代码行
        .explainContains("" // 代码行
            + "EnumerableAggregateRel(group=[{0, 1, 2, 3}], m0=[COUNT($4)])\n" // 代码行
            + "  EnumerableCalcRel(expr#0..69=[{inputs}], c0=[$t4], c1=[$t27], c2=[$t61], c3=[$t66], $f11=[$t11])\n" // 代码行
            + "    EnumerableJoinRel(condition=[=($68, $69)], joinType=[inner])\n" // 代码行
            + "      EnumerableCalcRel(expr#0..67=[{inputs}], proj#0..67=[{exprs}], $f68=[$t66])\n" // 代码行
            + "        EnumerableJoinRel(condition=[=($11, $65)], joinType=[inner])\n" // 代码行
            + "          EnumerableJoinRel(condition=[=($46, $59)], joinType=[inner])\n" // 代码行
            + "            EnumerableCalcRel(expr#0..58=[{inputs}], $f0=[$t49], $f1=[$t50], $f2=[$t51], $f3=[$t52], $f4=[$t53], $f5=[$t54], $f6=[$t55], $f7=[$t56], $f8=[$t57], $f9=[$t58], $f10=[$t41], $f11=[$t42], $f12=[$t43], $f13=[$t44], $f14=[$t45], $f15=[$t46], $f16=[$t47], $f17=[$t48], $f18=[$t0], $f19=[$t1], $f20=[$t2], $f21=[$t3], $f22=[$t4], $f23=[$t5], $f24=[$t6], $f25=[$t7], $f26=[$t8], $f27=[$t9], $f28=[$t10], $f29=[$t11], $f30=[$t12], $f31=[$t13], $f32=[$t14], $f33=[$t15], $f34=[$t16], $f35=[$t17], $f36=[$t18], $f37=[$t19], $f38=[$t20], $f39=[$t21], $f40=[$t22], $f41=[$t23], $f42=[$t24], $f43=[$t25], $f44=[$t26], $f45=[$t27], $f46=[$t28], $f47=[$t29], $f48=[$t30], $f49=[$t31], $f50=[$t32], $f51=[$t33], $f52=[$t34], $f53=[$t35], $f54=[$t36], $f55=[$t37], $f56=[$t38], $f57=[$t39], $f58=[$t40])\n" // 代码行
            + "              EnumerableJoinRel(condition=[=($41, $50)], joinType=[inner])\n" // 代码行
            + "                EnumerableCalcRel(expr#0..48=[{inputs}], $f0=[$t25], $f1=[$t26], $f2=[$t27], $f3=[$t28], $f4=[$t29], $f5=[$t30], $f6=[$t31], $f7=[$t32], $f8=[$t33], $f9=[$t34], $f10=[$t35], $f11=[$t36], $f12=[$t37], $f13=[$t38], $f14=[$t39], $f15=[$t40], $f16=[$t41], $f17=[$t42], $f18=[$t43], $f19=[$t44], $f20=[$t45], $f21=[$t46], $f22=[$t47], $f23=[$t48], $f24=[$t8], $f25=[$t9], $f26=[$t10], $f27=[$t11], $f28=[$t12], $f29=[$t13], $f30=[$t14], $f31=[$t15], $f32=[$t16], $f33=[$t17], $f34=[$t18], $f35=[$t19], $f36=[$t20], $f37=[$t21], $f38=[$t22], $f39=[$t23], $f40=[$t24], $f41=[$t0], $f42=[$t1], $f43=[$t2], $f44=[$t3], $f45=[$t4], $f46=[$t5], $f47=[$t6], $f48=[$t7])\n" // 代码行
            + "                  EnumerableJoinRel(condition=[=($14, $25)], joinType=[inner])\n" // 代码行
            + "                    EnumerableJoinRel(condition=[=($1, $8)], joinType=[inner])\n" // 代码行
            + "                      EnumerableTableScan(table=[[foodmart2, salary]])\n" // 代码行
            + "                      EnumerableTableScan(table=[[foodmart2, employee]])\n" // 代码行
            + "                    EnumerableTableScan(table=[[foodmart2, store]])\n" // 代码行
            + "                EnumerableTableScan(table=[[foodmart2, time_by_day]])\n" // 代码行
            + "            EnumerableTableScan(table=[[foodmart2, position]])\n" // 代码行
            + "          EnumerableTableScan(table=[[foodmart2, employee_closure]])\n" // 代码行
            + "      EnumerableAggregateRel(group=[{0}])\n" // 代码行
            + "        EnumerableValuesRel(tuples=[[{ 1 }, { 2 }, { 20 }, { 21 }, { 22 }, { 23 }, { 24 }, { 25 }, { 26 }, { 27 }, { 28 }, { 29 }, { 30 }, { 31 }, { 53 }, { 54 }, { 55 }, { 56 }, { 57 }, { 58 }, { 59 }, { 60 }, { 61 }, { 62 }, { 63 }, { 64 }, { 65 }, { 66 }, { 67 }, { 68 }, { 69 }, { 70 }, { 71 }, { 72 }, { 73 }, { 74 }, { 75 }, { 76 }, { 77 }, { 78 }, { 79 }, { 80 }, { 81 }, { 82 }, { 83 }, { 84 }, { 85 }, { 86 }, { 87 }, { 88 }, { 89 }, { 90 }, { 91 }, { 92 }, { 93 }, { 94 }, { 95 }, { 96 }, { 97 }, { 98 }, { 99 }, { 100 }, { 101 }, { 102 }, { 103 }, { 104 }, { 105 }, { 106 }, { 107 }, { 108 }, { 109 }, { 110 }, { 111 }, { 112 }, { 113 }, { 114 }, { 115 }, { 116 }, { 117 }, { 118 }, { 119 }, { 120 }, { 121 }, { 122 }, { 123 }, { 124 }, { 125 }, { 126 }, { 127 }, { 128 }, { 129 }, { 130 }, { 131 }, { 132 }, { 133 }, { 134 }, { 135 }, { 136 }, { 137 }, { 138 }, { 139 }, { 140 }, { 141 }, { 142 }, { 143 }, { 144 }, { 145 }, { 146 }, { 147 }, { 148 }, { 149 }, { 150 }, { 151 }, { 152 }, { 153 }, { 154 }, { 155 }, { 156 }, { 157 }, { 158 }, { 159 }, { 160 }, { 161 }, { 162 }, { 163 }, { 164 }, { 165 }, { 166 }, { 167 }, { 168 }, { 169 }, { 170 }, { 171 }, { 172 }, { 173 }, { 174 }, { 175 }, { 176 }, { 177 }, { 178 }, { 179 }, { 180 }, { 181 }, { 182 }, { 183 }, { 184 }, { 185 }, { 186 }, { 187 }, { 188 }, { 189 }, { 190 }, { 191 }, { 192 }, { 193 }, { 194 }, { 195 }, { 196 }, { 197 }, { 198 }, { 199 }, { 200 }, { 201 }, { 202 }, { 203 }, { 204 }, { 205 }, { 206 }, { 207 }, { 208 }, { 209 }, { 210 }, { 211 }, { 212 }, { 213 }, { 214 }, { 215 }, { 216 }, { 217 }, { 218 }, { 219 }, { 220 }, { 221 }, { 222 }, { 223 }, { 224 }, { 225 }, { 226 }, { 227 }, { 228 }, { 229 }, { 230 }, { 231 }, { 232 }, { 233 }, { 234 }, { 235 }, { 236 }, { 237 }, { 238 }, { 239 }, { 240 }, { 241 }, { 242 }, { 243 }, { 244 }, { 245 }, { 246 }, { 247 }, { 248 }, { 249 }, { 250 }, { 251 }, { 252 }, { 253 }, { 254 }, { 255 }, { 256 }, { 257 }, { 258 }, { 259 }, { 260 }, { 261 }, { 262 }, { 263 }, { 264 }, { 265 }, { 266 }, { 267 }, { 268 }, { 269 }, { 270 }, { 271 }, { 272 }, { 273 }, { 274 }, { 275 }, { 276 }, { 277 }, { 278 }, { 279 }, { 280 }, { 281 }, { 282 }, { 283 }, { 284 }, { 285 }, { 286 }, { 287 }, { 288 }, { 289 }, { 290 }, { 291 }, { 292 }, { 293 }, { 294 }, { 295 }, { 296 }, { 297 }, { 298 }, { 299 }, { 300 }, { 301 }, { 302 }, { 303 }, { 304 }, { 305 }, { 306 }, { 307 }, { 308 }, { 309 }, { 310 }, { 311 }, { 312 }, { 313 }, { 314 }, { 315 }, { 316 }, { 317 }, { 318 }, { 319 }, { 320 }, { 321 }, { 322 }, { 323 }, { 324 }, { 325 }, { 326 }, { 327 }, { 328 }, { 329 }, { 330 }, { 331 }, { 332 }, { 333 }, { 334 }, { 335 }, { 336 }, { 337 }, { 338 }, { 339 }, { 340 }, { 341 }, { 342 }, { 343 }, { 344 }, { 345 }, { 346 }, { 347 }, { 348 }, { 349 }, { 350 }, { 351 }, { 352 }, { 353 }, { 354 }, { 355 }, { 356 }, { 357 }, { 358 }, { 359 }, { 360 }, { 361 }, { 362 }, { 363 }, { 364 }, { 365 }, { 366 }, { 367 }, { 368 }, { 369 }, { 370 }, { 371 }, { 372 }, { 373 }, { 374 }, { 375 }, { 376 }, { 377 }, { 378 }, { 379 }, { 380 }, { 381 }, { 382 }, { 383 }, { 384 }, { 385 }, { 386 }, { 387 }, { 388 }, { 389 }, { 390 }, { 391 }, { 392 }, { 393 }, { 394 }, { 395 }, { 396 }, { 397 }, { 398 }, { 399 }, { 400 }, { 401 }, { 402 }, { 403 }, { 404 }, { 405 }, { 406 }, { 407 }, { 408 }, { 409 }, { 410 }, { 411 }, { 412 }, { 413 }, { 414 }, { 415 }, { 416 }, { 417 }, { 418 }, { 419 }, { 420 }, { 421 }, { 422 }, { 423 }, { 424 }, { 425 }, { 430 }, { 431 }, { 432 }, { 433 }, { 434 }, { 435 }, { 436 }, { 437 }, { 442 }, { 443 }, { 444 }, { 445 }, { 446 }, { 447 }, { 448 }, { 449 }, { 450 }, { 451 }, { 457 }, { 458 }, { 459 }, { 460 }, { 461 }, { 462 }, { 463 }, { 469 }, { 470 }, { 471 }, { 472 }, { 473 }]])\n") // 代码行
        .runs(); // 代码行
  } // 代码行
 // 空行
  /** Condition involving OR makes this more complex than // JavaDoc注释开始
   * {@link #testExplainJoin()}. */ // JavaDoc注释内容
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testExplainJoinOrderingWithOr() { // 测试方法:测试ExplainJoinOrderingWithOr功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(FOODMART_QUERIES.get(47).left) // 代码行
        .explainContains("xxx"); // 代码行
  } // 代码行
 // 空行
  /** There was a bug representing a nullable timestamp using a {@link Long} // JavaDoc注释开始
   * internally. */ // JavaDoc注释内容
  @Test void testNullableTimestamp() { // 测试方法:测试NullableTimestamp功能
    checkNullableTimestamp(CalciteAssert.Config.FOODMART_CLONE); // 代码行
  } // 代码行
 // 空行
  /** Similar to {@link #testNullableTimestamp} but directly off JDBC. */ // JavaDoc注释开始
  @Test void testNullableTimestamp2() { // 测试方法:测试NullableTimestamp2功能
    checkNullableTimestamp(CalciteAssert.Config.JDBC_FOODMART); // 代码行
  } // 代码行
 // 空行
  private void checkNullableTimestamp(CalciteAssert.Config config) { // checkNullableTimestamp方法:检查可空时间戳
    CalciteAssert.that() // 代码行
        .with(config) // 代码行
        .query( // 代码行
            "select \"hire_date\", \"end_date\", \"birth_date\" from \"foodmart\".\"employee\" where \"employee_id\" = 1") // 代码行
        // disable for MySQL; birth_date suffers timezone shift // 单行注释
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.MYSQL) // 代码行
        .returns2( // 代码行
            "hire_date=1994-12-01; end_date=null; birth_date=1961-08-26\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testReuseExpressionWhenNullChecking() { // 测试方法:测试ReuseExpressionWhenNullChecking功能
    final String sql = "select upper((case when \"empid\">\"deptno\"*10" // 代码行
        + " then 'y' else null end)) T\n" // 代码行
        + "from \"hr\".\"emps\""; // 代码行
    final String plan = "" // 代码行
        + "      String case_when_value;\n" // 代码行
        + "              final org.apache.calcite.test.schemata.hr.Employee current = (org.apache" // 代码行
        + ".calcite.test.schemata.hr.Employee) inputEnumerator.current();\n" // 代码行
        + "              if (current.empid > current.deptno * 10) {\n" // 代码行
        + "                case_when_value = \"y\";\n" // 代码行
        + "              } else {\n" // 代码行
        + "                case_when_value = null;\n" // 代码行
        + "              }\n" // 代码行
        + "              return case_when_value == null ? null : org.apache.calcite" // 代码行
        + ".runtime.SqlFunctions.upper(case_when_value);"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .planContains(plan) // 代码行
        .returns("T=null\n" // 代码行
            + "T=null\n" // 代码行
            + "T=Y\n" // 代码行
            + "T=Y\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testReuseExpressionWhenNullChecking2() { // 测试方法:测试ReuseExpressionWhenNullChecking2功能
    final String sql = "select upper((case when \"empid\">\"deptno\"*10" // 代码行
        + " then \"name\" end)) T\n" // 代码行
        + "from \"hr\".\"emps\""; // 代码行
    final String plan = "" // 代码行
        + "      String case_when_value;\n" // 代码行
        + "              final org.apache.calcite.test.schemata.hr.Employee current = (org.apache" // 代码行
        + ".calcite.test.schemata.hr.Employee) inputEnumerator.current();\n" // 代码行
        + "              if (current.empid > current.deptno * 10) {\n" // 代码行
        + "                case_when_value = current.name;\n" // 代码行
        + "              } else {\n" // 代码行
        + "                case_when_value = null;\n" // 代码行
        + "              }\n" // 代码行
        + "              return case_when_value == null ? null : org.apache.calcite" // 代码行
        + ".runtime.SqlFunctions.upper(case_when_value);"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .planContains(plan) // 代码行
        .returns("T=null\n" // 代码行
            + "T=null\n" // 代码行
            + "T=SEBASTIAN\n" // 代码行
            + "T=THEODORE\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testReuseExpressionWhenNullChecking3() { // 测试方法:测试ReuseExpressionWhenNullChecking3功能
    final String sql = "select substring(\"name\",\n" // 代码行
        + " \"deptno\"+case when CURRENT_PATH <> '' then 1 end)\n" // 代码行
        + "from \"hr\".\"emps\""; // 代码行
    final String plan = "" // 代码行
        + "              final org.apache.calcite.test.schemata.hr.Employee current" // 代码行
        + " = (org.apache.calcite.test.schemata.hr.Employee) inputEnumerator.current();\n" // 代码行
        + "              final String input_value = current.name;\n" // 代码行
        + "              Integer case_when_value;\n" // 代码行
        + "              if ($L4J$C$org_apache_calcite_runtime_SqlFunctions_ne_) {\n" // 代码行
        + "                case_when_value = $L4J$C$Integer_valueOf_1_;\n" // 代码行
        + "              } else {\n" // 代码行
        + "                case_when_value = null;\n" // 代码行
        + "              }\n" // 代码行
        + "              final Integer binary_call_value0 = " // 代码行
        + "case_when_value == null ? null : " // 代码行
        + "Integer.valueOf(current.deptno + case_when_value.intValue());\n" // 代码行
        + "              return input_value == null || binary_call_value0 == null" // 代码行
        + " ? null" // 代码行
        + " : org.apache.calcite.runtime.SqlFunctions.substring(input_value, " // 代码行
        + "binary_call_value0.intValue());\n"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .planContains(plan); // 代码行
  } // 代码行
 // 空行
  @Test void testReuseExpressionWhenNullChecking4() { // 测试方法:测试ReuseExpressionWhenNullChecking4功能
    final String sql = "select substring(trim(\n" // 代码行
        + "substring(\"name\",\n" // 代码行
        + "  \"deptno\"*0+case when CURRENT_PATH = '' then 1 end)\n" // 代码行
        + "), case when \"empid\">\"deptno\" then 4\n" /* diff from 5 */ // 代码行
        + "   else\n" // 代码行
        + "     case when \"deptno\"*8>8 then 5 end\n" // 代码行
        + "   end-2) T\n" // 代码行
        + "from\n" // 代码行
        + "\"hr\".\"emps\""; // 代码行
    final String plan = "" // 代码行
        + "              final org.apache.calcite.test.schemata.hr.Employee current =" // 代码行
        + " (org.apache.calcite.test.schemata.hr.Employee) inputEnumerator.current();\n" // 代码行
        + "              final String input_value = current.name;\n" // 代码行
        + "              final int input_value0 = current.deptno;\n" // 代码行
        + "              Integer case_when_value;\n" // 代码行
        + "              if ($L4J$C$org_apache_calcite_runtime_SqlFunctions_eq_) {\n" // 代码行
        + "                case_when_value = $L4J$C$Integer_valueOf_1_;\n" // 代码行
        + "              } else {\n" // 代码行
        + "                case_when_value = null;\n" // 代码行
        + "              }\n" // 代码行
        + "              final Integer binary_call_value1 = " // 代码行
        + "case_when_value == null" // 代码行
        + " ? null" // 代码行
        + " : Integer.valueOf(input_value0 * 0 + case_when_value.intValue());\n" // 代码行
        + "              final String method_call_value = " // 代码行
        + "input_value == null || binary_call_value1 == null" // 代码行
        + " ? null" // 代码行
        + " : org.apache.calcite.runtime.SqlFunctions.substring(input_value, " // 代码行
        + "binary_call_value1.intValue());\n" // 代码行
        + "              final String trim_value = " // 代码行
        + "method_call_value == null" // 代码行
        + " ? null" // 代码行
        + " : org.apache.calcite.runtime.SqlFunctions.trim(true, true, \" \", " // 代码行
        + "method_call_value, true);\n" // 代码行
        + "              Integer case_when_value0;\n" // 代码行
        + "              if (current.empid > input_value0) {\n" // 代码行
        + "                case_when_value0 = $L4J$C$Integer_valueOf_4_;\n" // 代码行
        + "              } else {\n" // 代码行
        + "                Integer case_when_value1;\n" // 代码行
        + "                if (current.deptno * 8 > 8) {\n" // 代码行
        + "                  case_when_value1 = $L4J$C$Integer_valueOf_5_;\n" // 代码行
        + "                } else {\n" // 代码行
        + "                  case_when_value1 = null;\n" // 代码行
        + "                }\n" // 代码行
        + "                case_when_value0 = case_when_value1;\n" // 代码行
        + "              }\n" // 代码行
        + "              final Integer binary_call_value3 = " // 代码行
        + "case_when_value0 == null" // 代码行
        + " ? null" // 代码行
        + " : Integer.valueOf(case_when_value0.intValue() - 2);\n" // 代码行
        + "              return trim_value == null || binary_call_value3 == null" // 代码行
        + " ? null" // 代码行
        + " : org.apache.calcite.runtime.SqlFunctions.substring(trim_value, " // 代码行
        + "binary_call_value3.intValue());\n"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .planContains(plan) // 代码行
        .returns("T=ill\n" // 代码行
            + "T=ric\n" // 代码行
            + "T=ebastian\n" // 代码行
            + "T=heodore\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJoinConditionExpandIsNotDistinctFrom() { // 测试方法:测试JoinConditionExpandIsNotDistinctFrom功能
    final String sql = "" // 代码行
        + "select \"t1\".\"commission\" from \"hr\".\"emps\" as \"t1\"\n" // 代码行
        + "join\n" // 代码行
        + "\"hr\".\"emps\" as \"t2\"\n" // 代码行
        + "on \"t1\".\"commission\" is not distinct from \"t2\".\"commission\""; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 代码行
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 代码行
          planner.addRule(CoreRules.JOIN_CONDITION_EXPAND_IS_NOT_DISTINCT_FROM); // 代码行
        }) // 代码行
        .explainContains("HashJoin") // 代码行
        .returnsUnordered("commission=1000", // 代码行
            "commission=250", // 代码行
            "commission=500", // 代码行
            "commission=null"); // 代码行
  } // 代码行
  @Test void testReuseExpressionWhenNullChecking5() { // 测试方法:测试ReuseExpressionWhenNullChecking5功能
    final String sql = "select substring(trim(\n" // 代码行
        + "substring(\"name\",\n" // 代码行
        + "  \"deptno\"*0+case when CURRENT_PATH = '' then 1 end)\n" // 代码行
        + "), case when \"empid\">\"deptno\" then 5\n" /* diff from 4 */ // 代码行
        + "   else\n" // 代码行
        + "     case when \"deptno\"*8>8 then 5 end\n" // 代码行
        + "   end-2) T\n" // 代码行
        + "from\n" // 代码行
        + "\"hr\".\"emps\""; // 代码行
    final String plan = "" // 代码行
        + "              final org.apache.calcite.test.schemata.hr.Employee current =" // 代码行
        + " (org.apache.calcite.test.schemata.hr.Employee) inputEnumerator.current();\n" // 代码行
        + "              final String input_value = current.name;\n" // 代码行
        + "              final int input_value0 = current.deptno;\n" // 代码行
        + "              Integer case_when_value;\n" // 代码行
        + "              if ($L4J$C$org_apache_calcite_runtime_SqlFunctions_eq_) {\n" // 代码行
        + "                case_when_value = $L4J$C$Integer_valueOf_1_;\n" // 代码行
        + "              } else {\n" // 代码行
        + "                case_when_value = null;\n" // 代码行
        + "              }\n" // 代码行
        + "              final Integer binary_call_value1 = " // 代码行
        + "case_when_value == null" // 代码行
        + " ? null" // 代码行
        + " : Integer.valueOf(input_value0 * 0 + case_when_value.intValue());\n" // 代码行
        + "              final String method_call_value = " // 代码行
        + "input_value == null || binary_call_value1 == null" // 代码行
        + " ? null" // 代码行
        + " : org.apache.calcite.runtime.SqlFunctions.substring(input_value, " // 代码行
        + "binary_call_value1.intValue());\n" // 代码行
        + "              final String trim_value = " // 代码行
        + "method_call_value == null" // 代码行
        + " ? null" // 代码行
        + " : org.apache.calcite.runtime.SqlFunctions.trim(true, true, \" \", " // 代码行
        + "method_call_value, true);\n" // 代码行
        + "              Integer case_when_value0;\n" // 代码行
        + "              if (current.empid > input_value0) {\n" // 代码行
        + "                case_when_value0 = $L4J$C$Integer_valueOf_5_;\n" // 代码行
        + "              } else {\n" // 代码行
        + "                Integer case_when_value1;\n" // 代码行
        + "                if (current.deptno * 8 > 8) {\n" // 代码行
        + "                  case_when_value1 = $L4J$C$Integer_valueOf_5_;\n" // 代码行
        + "                } else {\n" // 代码行
        + "                  case_when_value1 = null;\n" // 代码行
        + "                }\n" // 代码行
        + "                case_when_value0 = case_when_value1;\n" // 代码行
        + "              }\n" // 代码行
        + "              final Integer binary_call_value3 = " // 代码行
        + "case_when_value0 == null" // 代码行
        + " ? null" // 代码行
        + " : Integer.valueOf(case_when_value0.intValue() - 2);\n" // 代码行
        + "              return trim_value == null || binary_call_value3 == null" // 代码行
        + " ? null" // 代码行
        + " : org.apache.calcite.runtime.SqlFunctions.substring(trim_value, " // 代码行
        + "binary_call_value3.intValue());"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .planContains(plan) // 代码行
        .returns("T=ll\n" // 代码行
            + "T=ic\n" // 代码行
            + "T=bastian\n" // 代码行
            + "T=eodore\n"); // 代码行
  } // 代码行
 // 空行
 // 空行
 // 空行
  @Test void testValues() { // 测试方法:测试Values功能
    CalciteAssert.that() // 代码行
        .query("values (1), (2)") // 代码行
        .returns("EXPR$0=1\n" // 代码行
            + "EXPR$0=2\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testValuesAlias() { // 测试方法:测试ValuesAlias功能
    CalciteAssert.that() // 代码行
        .query( // 代码行
            "select \"desc\" from (VALUES ROW(1, 'SameName')) AS \"t\" (\"id\", \"desc\")") // 代码行
        .returns("desc=SameName\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testValuesMinus() { // 测试方法:测试ValuesMinus功能
    CalciteAssert.that() // 代码行
        .query("values (-2-1)") // 代码行
        .returns("EXPR$0=-3\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1120">[CALCITE-1120] // JavaDoc注释内容
   * Support SELECT without FROM</a>. */ // JavaDoc注释内容
  @Test void testSelectWithoutFrom() { // 测试方法:测试SelectWithoutFrom功能
    CalciteAssert.that() // 代码行
        .query("select 2+2") // 代码行
        .returns("EXPR$0=4\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a table constructor that has multiple rows and multiple columns. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>Note that the character literals become CHAR(3) and that the first is // JavaDoc注释内容
   * correctly rendered with trailing spaces: 'a  '. If we were inserting // JavaDoc注释内容
   * into a VARCHAR column the behavior would be different; the literals // JavaDoc注释内容
   * would be converted into VARCHAR(3) values and the implied cast from // JavaDoc注释内容
   * CHAR(1) to CHAR(3) that appends trailing spaces does not occur. See // JavaDoc注释内容
   * "contextually typed value specification" in the SQL spec. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testValuesComposite() { // 测试方法:测试ValuesComposite功能
    CalciteAssert.that() // 代码行
        .query("values (1, 'a'), (2, 'abc')") // 代码行
        .returns("EXPR$0=1; EXPR$1=a  \n" // 代码行
            + "EXPR$0=2; EXPR$1=abc\n"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests that even though trivial "rename columns" projection is removed, // JavaDoc注释内容
   * the query still returns proper column names. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testValuesCompositeRenamed() { // 测试方法:测试ValuesCompositeRenamed功能
    CalciteAssert.that() // 代码行
        .query("select EXPR$0 q, EXPR$1 w from (values (1, 'a'), (2, 'abc'))") // 代码行
        .explainContains( // 代码行
            "PLAN=EnumerableValues(tuples=[[{ 1, 'a  ' }, { 2, 'abc' }]])\n") // 代码行
        .returns("Q=1; W=a  \n" // 代码行
            + "Q=2; W=abc\n"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests that even though trivial "rename columns" projection is removed, // JavaDoc注释内容
   * the query still returns proper column names. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testValuesCompositeRenamedSameNames() { // 测试方法:测试ValuesCompositeRenamedSameNames功能
    CalciteAssert.that() // 代码行
        .query("select EXPR$0 q, EXPR$1 q from (values (1, 'a'), (2, 'abc'))") // 代码行
        .explainContains( // 代码行
            "PLAN=EnumerableValues(tuples=[[{ 1, 'a  ' }, { 2, 'abc' }]])\n") // 代码行
        .returnsUnordered( // 代码行
            "Q=1; Q=a  ", // 代码行
            "Q=2; Q=abc"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests that even though trivial "rename columns" projection is removed, // JavaDoc注释内容
   * the query still returns proper column names. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @ParameterizedTest // 参数化测试注解:标记为参数化测试方法
  @MethodSource("explainFormats") // 方法源注解:指定测试数据的来源方法
  void testUnionWithSameColumnNames(String format) { // 代码行
    final String expected; // 代码行
    final String extra; // 代码行
    switch (format) { // 代码行
    case "dot": // 代码行
      expected = "PLAN=digraph {\n" // 代码行
          + "\"EnumerableCalc\\nexpr#0..3 = {inputs}\\ndeptno = $t0\\ndeptno0 = $t0\\n\" -> " // 代码行
          + "\"EnumerableUnion\\nall = false\\n\" [label=\"0\"]\n" // 代码行
          +  "\"EnumerableCalc\\nexpr#0..4 = {inputs}\\ndeptno = $t1\\nempid = $t0\\n\" -> " // 代码行
          + "\"EnumerableUnion\\nall = false\\n\" [label=\"1\"]\n" // 代码行
          + "\"EnumerableTableScan\\ntable = [hr, depts]\\n\" -> \"EnumerableCalc\\nexpr#0..3 = " // 代码行
          + "{inputs}\\ndeptno = $t0\\ndeptno0 = $t0\\n\" [label=\"0\"]\n" // 代码行
          + "\"EnumerableTableScan\\ntable = [hr, emps]\\n\" -> \"EnumerableCalc\\nexpr#0..4 = " // 代码行
          + "{inputs}\\ndeptno = $t1\\nempid = $t0\\n\" [label=\"0\"]\n" // 代码行
          + "}\n" // 代码行
          + "\n"; // 代码行
      extra = " as dot "; // 代码行
      break; // 代码行
    case "text": // 代码行
      expected = "" // 代码行
          + "PLAN=EnumerableUnion(all=[false])\n" // 代码行
          + "  EnumerableCalc(expr#0..3=[{inputs}], deptno=[$t0], deptno0=[$t0])\n" // 代码行
          + "    EnumerableTableScan(table=[[hr, depts]])\n" // 代码行
          + "  EnumerableCalc(expr#0..4=[{inputs}], deptno=[$t1], empid=[$t0])\n" // 代码行
          + "    EnumerableTableScan(table=[[hr, emps]])\n"; // 代码行
      extra = ""; // 代码行
      break; // 代码行
    default: // 代码行
      throw new AssertionError(); // 代码行
    } // 代码行
    CalciteAssert.hr() // 代码行
        .query( // 代码行
            "select \"deptno\", \"deptno\" from \"hr\".\"depts\" union select \"deptno\", \"empid\" from \"hr\".\"emps\"") // 代码行
        .explainMatches(extra, CalciteAssert.checkResultContains(expected)) // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; deptno=110", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=20; deptno=200", // 代码行
            "deptno=10; deptno=100", // 代码行
            "deptno=10; deptno=150", // 代码行
            "deptno=30; deptno=30", // 代码行
            "deptno=40; deptno=40"); // 代码行
  } // 代码行
 // 空行
  /** Tests inner join to an inline table ({@code VALUES} clause). */ // JavaDoc注释开始
  @ParameterizedTest // 参数化测试注解:标记为参数化测试方法
  @MethodSource("explainFormats") // 方法源注解:指定测试数据的来源方法
  void testInnerJoinValues(String format) { // 代码行
    String expected = null; // 代码行
    final String extra; // 代码行
    switch (format) { // 代码行
    case "text": // 代码行
      expected = "EnumerableCalc(expr#0=[{inputs}], expr#1=['SameName'], proj#0..1=[{exprs}])\n" // 代码行
          + "  EnumerableAggregate(group=[{0}])\n" // 代码行
          + "    EnumerableCalc(expr#0..1=[{inputs}], expr#2=[CAST($t1):INTEGER NOT NULL], expr#3=[10], expr#4=[=($t2, $t3)], proj#0..1=[{exprs}], $condition=[$t4])\n" // 代码行
          + "      EnumerableTableScan(table=[[SALES, EMPS]])\n\n"; // 代码行
      extra = ""; // 代码行
      break; // 代码行
    case "dot": // 代码行
      expected = "PLAN=digraph {\n" // 代码行
          + "\"EnumerableAggregate\\n" // 代码行
          + "group = {0}\\n" // 代码行
          + "\" -> \"EnumerableCalc\\n" // 代码行
          + "expr#0 = {inputs}\\n" // 代码行
          + "expr#1 = 'SameName'\\n" // 代码行
          + "proj#0..1 = {exprs}\\n" // 代码行
          + "\" [label=\"0\"]\n" // 代码行
          + "\"EnumerableCalc\\n" // 代码行
          + "expr#0..1 = {inputs}\\n" // 代码行
          + "expr#2 = CAST($t1):I\\n" // 代码行
          + "NTEGER NOT NULL\\n" // 代码行
          + "expr#3 = 10\\n" // 代码行
          + "expr#4 = =($t2, $t3)\\n" // 代码行
          + "...\" -> \"EnumerableAggregate\\n" // 代码行
          + "group = {0}\\n" // 代码行
          + "\" [label=\"0\"]\n" // 代码行
          + "\"EnumerableTableScan\\n" // 代码行
          + "table = [SALES, EMPS\\n]\\n" // 代码行
          + "\" -> \"EnumerableCalc\\n" // 代码行
          + "expr#0..1 = {inputs}\\n" // 代码行
          + "expr#2 = CAST($t1):I\\n" // 代码行
          + "NTEGER NOT NULL\\n" // 代码行
          + "expr#3 = 10\\n" // 代码行
          + "expr#4 = =($t2, $t3)\\n" // 代码行
          + "...\" [label=\"0\"]\n" // 代码行
          + "}\n\n"; // 代码行
      extra = " as dot "; // 代码行
      break; // 代码行
    default: // 代码行
      throw new AssertionError("unknown " + format); // 代码行
    } // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.LINGUAL) // 代码行
        .query("select empno, desc from sales.emps,\n" // 代码行
            + "  (SELECT * FROM (VALUES (10, 'SameName')) AS t (id, desc)) as sn\n" // 代码行
            + "where emps.deptno = sn.id and sn.desc = 'SameName' group by empno, desc") // 代码行
        .explainMatches(extra, CalciteAssert.checkResultContains(expected)) // 代码行
        .returns("EMPNO=1; DESC=SameName\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a merge-join. */ // JavaDoc注释开始
  @Test void testMergeJoin() { // 测试方法:测试MergeJoin功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select \"emps\".\"empid\",\n" // 代码行
            + " \"depts\".\"deptno\", \"depts\".\"name\"\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + " join \"hr\".\"depts\" using (\"deptno\")") // 代码行
        .explainContains("" // 代码行
            + "EnumerableCalc(expr#0..3=[{inputs}], empid=[$t0], deptno=[$t2], name=[$t3])\n" // 代码行
            + "  EnumerableMergeJoin(condition=[=($1, $2)], joinType=[inner])\n" // 代码行
            + "    EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 代码行
            + "      EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}])\n" // 代码行
            + "        EnumerableTableScan(table=[[hr, emps]])\n" // 代码行
            + "    EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 代码行
            + "      EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 代码行
            + "        EnumerableTableScan(table=[[hr, depts]])") // 代码行
        .returns("empid=100; deptno=10; name=Sales\n" // 代码行
            + "empid=150; deptno=10; name=Sales\n" // 代码行
            + "empid=110; deptno=10; name=Sales\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a cartesian product aka cross join. */ // JavaDoc注释开始
  @Test void testCartesianJoin() { // 测试方法:测试CartesianJoin功能
    CalciteAssert.hr() // 代码行
        .query( // 代码行
            "select * from \"hr\".\"emps\", \"hr\".\"depts\" where \"emps\".\"empid\" < 140 and \"depts\".\"deptno\" > 20") // 代码行
        .returnsUnordered( // 代码行
            "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000; deptno0=30; name0=Marketing; employees=[]; location={0, 52}", // 代码行
            "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000; deptno0=40; name0=HR; employees=[{200, 20, Eric, 8000.0, 500}]; location=null", // 代码行
            "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250; deptno0=30; name0=Marketing; employees=[]; location={0, 52}", // 代码行
            "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250; deptno0=40; name0=HR; employees=[{200, 20, Eric, 8000.0, 500}]; location=null"); // 代码行
  } // 代码行
 // 空行
  @Test void testDistinctCountSimple() { // 测试方法:测试DistinctCountSimple功能
    final String s = // 代码行
        "select count(distinct \"sales_fact_1997\".\"unit_sales\") as \"m0\"\n" // 代码行
            + "from \"sales_fact_1997\" as \"sales_fact_1997\""; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(s) // 代码行
        .explainContains("EnumerableAggregate(group=[{}], m0=[COUNT($0)])\n" // 代码行
            + "  EnumerableAggregate(group=[{7}])\n" // 代码行
            + "    EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])") // 代码行
        .returns("m0=6\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testDistinctCount2() { // 测试方法:测试DistinctCount2功能
    final String s = "select cast(\"unit_sales\" as integer) as \"u\",\n" // 代码行
        + " count(distinct \"sales_fact_1997\".\"customer_id\") as \"m0\"\n" // 代码行
        + "from \"sales_fact_1997\" as \"sales_fact_1997\"\n" // 代码行
        + "group by \"unit_sales\""; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(s) // 代码行
        .explainContains("" // 代码行
            + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[CAST($t0):INTEGER NOT NULL], u=[$t2], m0=[$t1])\n" // 代码行
            + "  EnumerableAggregate(group=[{1}], m0=[COUNT($0)])\n" // 代码行
            + "    EnumerableAggregate(group=[{2, 7}])\n" // 代码行
            + "      EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])") // 代码行
        .returnsUnordered( // 代码行
            "u=1; m0=523", // 代码行
            "u=5; m0=1059", // 代码行
            "u=4; m0=4459", // 代码行
            "u=6; m0=19", // 代码行
            "u=3; m0=4895", // 代码行
            "u=2; m0=4735"); // 代码行
  } // 代码行
 // 空行
  @Test void testDistinctCount() { // 测试方法:测试DistinctCount功能
    final String s = "select \"time_by_day\".\"the_year\" as \"c0\",\n" // 代码行
        + " count(distinct \"sales_fact_1997\".\"unit_sales\") as \"m0\"\n" // 代码行
        + "from \"time_by_day\" as \"time_by_day\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\"\n" // 代码行
        + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
        + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "group by \"time_by_day\".\"the_year\""; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(s) // 代码行
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.ORACLE) // 代码行
        .explainContains("" // 代码行
            + "EnumerableAggregate(group=[{0}], m0=[COUNT($1)])\n" // 代码行
            + "  EnumerableCalc(expr#0=[{inputs}], expr#1=[1997:SMALLINT], expr#2=[CAST($t1):SMALLINT], c0=[$t2], unit_sales=[$t0])\n" // 代码行
            + "    EnumerableAggregate(group=[{1}])\n" // 代码行
            + "      EnumerableHashJoin(condition=[=($0, $2)], joinType=[semi])\n" // 代码行
            + "        EnumerableCalc(expr#0..7=[{inputs}], time_id=[$t1], unit_sales=[$t7])\n" // 代码行
            + "          EnumerableTableScan(table=[[foodmart2, sales_fact_1997]])\n" // 代码行
            + "        EnumerableCalc(expr#0..9=[{inputs}], expr#10=[CAST($t4):INTEGER], expr#11=[1997], expr#12=[=($t10, $t11)], time_id=[$t0], the_year=[$t4], $condition=[$t12])\n" // 代码行
            + "          EnumerableTableScan(table=[[foodmart2, time_by_day]])") // 代码行
        .returns("c0=1997; m0=6\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testDistinctCountComposite() { // 测试方法:测试DistinctCountComposite功能
    final String s = "select \"time_by_day\".\"the_year\" as \"c0\",\n" // 代码行
        + " count(distinct \"sales_fact_1997\".\"product_id\",\n" // 代码行
        + "       \"sales_fact_1997\".\"customer_id\") as \"m0\"\n" // 代码行
        + "from \"time_by_day\" as \"time_by_day\",\n" // 代码行
        + " \"sales_fact_1997\" as \"sales_fact_1997\"\n" // 代码行
        + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
        + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "group by \"time_by_day\".\"the_year\""; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(s) // 代码行
        .returns("c0=1997; m0=85452\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testAggregateFilter() { // 测试方法:测试AggregateFilter功能
    final String s = "select \"the_month\",\n" // 代码行
        + " count(*) as \"c\",\n" // 代码行
        + " count(*) filter (where \"day_of_month\" > 20) as \"c2\"\n" // 代码行
        + "from \"time_by_day\" as \"time_by_day\"\n" // 代码行
        + "where \"time_by_day\".\"the_year\" = 1997\n" // 代码行
        + "group by \"time_by_day\".\"the_month\"\n" // 代码行
        + "order by \"time_by_day\".\"the_month\""; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query(s) // 代码行
        .returns("the_month=April; c=30; c2=10\n" // 代码行
            + "the_month=August; c=31; c2=11\n" // 代码行
            + "the_month=December; c=31; c2=11\n" // 代码行
            + "the_month=February; c=28; c2=8\n" // 代码行
            + "the_month=January; c=31; c2=11\n" // 代码行
            + "the_month=July; c=31; c2=11\n" // 代码行
            + "the_month=June; c=30; c2=10\n" // 代码行
            + "the_month=March; c=31; c2=11\n" // 代码行
            + "the_month=May; c=31; c2=11\n" // 代码行
            + "the_month=November; c=30; c2=10\n" // 代码行
            + "the_month=October; c=31; c2=11\n" // 代码行
            + "the_month=September; c=30; c2=10\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a simple IN query implemented as a semi-join. */ // JavaDoc注释开始
  @Test void testSimpleIn() { // 测试方法:测试SimpleIn功能
    CalciteAssert.hr() // 代码行
        .query("select * from \"hr\".\"depts\" where \"deptno\" in (\n" // 代码行
            + "  select \"deptno\" from \"hr\".\"emps\"\n" // 代码行
            + "  where \"empid\" < 150)") // 代码行
        .convertContains("" // 代码行
            + "LogicalProject(deptno=[$0], name=[$1], employees=[$2], location=[$3])\n" // 代码行
            + "  LogicalFilter(condition=[IN($0, {\n" // 代码行
            + "LogicalProject(deptno=[$1])\n" // 代码行
            + "  LogicalFilter(condition=[<($0, 150)])\n" // 代码行
            + "    LogicalTableScan(table=[[hr, emps]])\n" // 代码行
            + "})])\n" // 代码行
            + "    LogicalTableScan(table=[[hr, depts]])") // 代码行
        .explainContains("" // 代码行
            + "EnumerableHashJoin(condition=[=($0, $5)], joinType=[semi])\n" // 代码行
            + "  EnumerableTableScan(table=[[hr, depts]])\n" // 代码行
            + "  EnumerableCalc(expr#0..4=[{inputs}], expr#5=[150], expr#6=[<($t0, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 代码行
            + "    EnumerableTableScan(table=[[hr, emps]])") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; name=Sales; employees=[{100, 10, Bill, 10000.0, 1000}, {150, 10, Sebastian, 7000.0, null}]; location={-122, 38}"); // 代码行
  } // 代码行
 // 空行
  /** Test cases for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5984">[CALCITE-5984]</a> // JavaDoc注释内容
   * Disabling trimming of unused fields via config and program. */ // JavaDoc注释内容
  @ParameterizedTest // 参数化测试注解:标记为参数化测试方法
  @MethodSource("disableTrimmingConfigsTestArguments") // 方法源注解:指定测试数据的来源方法
  void testJoinWithTrimmingConfigs(boolean enableTrimmingByConfig, // 代码行
      boolean enableTrimmingByProgram, // 代码行
      String expectedLogicalPlan) { // 代码行
    CalciteAssert.hr().query("select \"d\".\"name\" from \"hr\".\"depts\" as \"d\" \n" // 代码行
                + "  join \"hr\".\"emps\" as \"e\" on \"d\".\"deptno\" = \"e\".\"deptno\" \n") // 代码行
        .withHook(Hook.SQL2REL_CONVERTER_CONFIG_BUILDER, // 代码行
            (Consumer<Holder<Config>>) configHolder -> // 代码行
            configHolder.set(configHolder.get().withTrimUnusedFields(enableTrimmingByConfig))) // 代码行
        .withHook(Hook.PROGRAM, // 代码行
            (Consumer<Holder<Program>>) // 代码行
            programHolder -> programHolder // 代码行
                    .set( // 代码行
                        Programs.standard( // 代码行
                        DefaultRelMetadataProvider.INSTANCE, enableTrimmingByProgram))) // 代码行
        .convertContains(expectedLogicalPlan); // 代码行
  } // 代码行
 // 空行
  /** A difficult query: an IN list so large that the planner promotes it // JavaDoc注释开始
   * to a semi-join against a VALUES relation. */ // JavaDoc注释内容
  @Disabled // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testIn() { // 测试方法:测试In功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"time_by_day\".\"the_year\" as \"c0\",\n" // 代码行
            + " \"product_class\".\"product_family\" as \"c1\",\n" // 代码行
            + " \"customer\".\"country\" as \"c2\",\n" // 代码行
            + " \"customer\".\"state_province\" as \"c3\",\n" // 代码行
            + " \"customer\".\"city\" as \"c4\",\n" // 代码行
            + " sum(\"sales_fact_1997\".\"unit_sales\") as \"m0\"\n" // 代码行
            + "from \"time_by_day\" as \"time_by_day\",\n" // 代码行
            + " \"sales_fact_1997\" as \"sales_fact_1997\",\n" // 代码行
            + " \"product_class\" as \"product_class\",\n" // 代码行
            + " \"product\" as \"product\", \"customer\" as \"customer\"\n" // 代码行
            + "where \"sales_fact_1997\".\"time_id\" = \"time_by_day\".\"time_id\"\n" // 代码行
            + "and \"time_by_day\".\"the_year\" = 1997\n" // 代码行
            + "and \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
            + "and \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"\n" // 代码行
            + "and \"product_class\".\"product_family\" = 'Drink'\n" // 代码行
            + "and \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n" // 代码行
            + "and \"customer\".\"country\" = 'USA'\n" // 代码行
            + "and \"customer\".\"state_province\" = 'WA'\n" // 代码行
            + "and \"customer\".\"city\" in ('Anacortes', 'Ballard', 'Bellingham', 'Bremerton', 'Burien', 'Edmonds', 'Everett', 'Issaquah', 'Kirkland', 'Lynnwood', 'Marysville', 'Olympia', 'Port Orchard', 'Puyallup', 'Redmond', 'Renton', 'Seattle', 'Sedro Woolley', 'Spokane', 'Tacoma', 'Walla Walla', 'Yakima')\n" // 代码行
            + "group by \"time_by_day\".\"the_year\",\n" // 代码行
            + " \"product_class\".\"product_family\",\n" // 代码行
            + " \"customer\".\"country\",\n" // 代码行
            + " \"customer\".\"state_province\",\n" // 代码行
            + " \"customer\".\"city\"") // 代码行
        .returns( // 代码行
            "c0=1997; c1=Drink; c2=USA; c3=WA; c4=Sedro Woolley; m0=58.0000\n"); // 代码行
  } // 代码行
 // 空行
  /** Query that uses parenthesized JOIN. */ // JavaDoc注释开始
  @Test void testSql92JoinParenthesized() { // 测试方法:测试Sql92JoinParenthesized功能
    if (!Bug.TODO_FIXED) { // 代码行
      return; // 代码行
    } // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select\n" // 代码行
            + "   \"product_class\".\"product_family\" as \"c0\",\n" // 代码行
            + "   \"product_class\".\"product_department\" as \"c1\",\n" // 代码行
            + "   \"customer\".\"country\" as \"c2\",\n" // 代码行
            + "   \"customer\".\"state_province\" as \"c3\",\n" // 代码行
            + "   \"customer\".\"city\" as \"c4\"\n" // 代码行
            + "from\n" // 代码行
            + "   \"sales_fact_1997\" as \"sales_fact_1997\"\n" // 代码行
            + "join (\"product\" as \"product\"\n" // 代码行
            + "     join \"product_class\" as \"product_class\"\n" // 代码行
            + "     on \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\")\n" // 代码行
            + "on  \"sales_fact_1997\".\"product_id\" = \"product\".\"product_id\"\n" // 代码行
            + "join \"customer\" as \"customer\"\n" // 代码行
            + "on  \"sales_fact_1997\".\"customer_id\" = \"customer\".\"customer_id\"\n" // 代码行
            + "join \"promotion\" as \"promotion\"\n" // 代码行
            + "on \"sales_fact_1997\".\"promotion_id\" = \"promotion\".\"promotion_id\"\n" // 代码行
            + "where (\"promotion\".\"media_type\" = 'Radio'\n" // 代码行
            + " or \"promotion\".\"media_type\" = 'TV'\n" // 代码行
            + " or \"promotion\".\"media_type\" = 'Sunday Paper'\n" // 代码行
            + " or \"promotion\".\"media_type\" = 'Street Handout')\n" // 代码行
            + " and (\"product_class\".\"product_family\" = 'Drink')\n" // 代码行
            + " and (\"customer\".\"country\" = 'USA' and \"customer\".\"state_province\"" // 代码行
            + " = 'WA' and \"customer\".\"city\" = 'Bellingham')\n" // 代码行
            + "group by \"product_class\".\"product_family\",\n" // 代码行
            + "   \"product_class\".\"product_department\",\n" // 代码行
            + "   \"customer\".\"country\",\n" // 代码行
            + "   \"customer\".\"state_province\",\n" // 代码行
            + "   \"customer\".\"city\"\n" // 代码行
            + "order by \"product_class\".\"product_family\" ASC,\n" // 代码行
            + "   \"product_class\".\"product_department\" ASC,\n" // 代码行
            + "   \"customer\".\"country\" ASC,\n" // 代码行
            + "   \"customer\".\"state_province\" ASC,\n" // 代码行
            + "   \"customer\".\"city\" ASC") // 代码行
        .returns("+-------+---------------------+-----+------+------------+\n" // 代码行
            + "| c0    | c1                  | c2  | c3   | c4         |\n" // 代码行
            + "+-------+---------------------+-----+------+------------+\n" // 代码行
            + "| Drink | Alcoholic Beverages | USA | WA   | Bellingham |\n" // 代码行
            + "| Drink | Dairy               | USA | WA   | Bellingham |\n" // 代码行
            + "+-------+---------------------+-----+------+------------+\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY with no options. Nulls come last. // JavaDoc注释开始
   * // JavaDoc注释内容
   * @see org.apache.calcite.avatica.AvaticaDatabaseMetaData#nullsAreSortedAtEnd() // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testOrderBy() { // 测试方法:测试OrderBy功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"store_id\", \"grocery_sqft\" from \"store\"\n" // 代码行
            + "where \"store_id\" < 3 order by 2") // 代码行
        .returns("store_id=1; grocery_sqft=17475\n" // 代码行
            + "store_id=2; grocery_sqft=22271\n" // 代码行
            + "store_id=0; grocery_sqft=null\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ... DESC. Nulls come first (they come last for ASC). */ // JavaDoc注释开始
  @Test void testOrderByDesc() { // 测试方法:测试OrderByDesc功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"store_id\", \"grocery_sqft\" from \"store\"\n" // 代码行
            + "where \"store_id\" < 3 order by 2 desc") // 代码行
        .returns("store_id=0; grocery_sqft=null\n" // 代码行
            + "store_id=2; grocery_sqft=22271\n" // 代码行
            + "store_id=1; grocery_sqft=17475\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests sorting by an expression not in the select clause. */ // JavaDoc注释开始
  @Test void testOrderByExpr() { // 测试方法:测试OrderByExpr功能
    CalciteAssert.hr() // 代码行
        .query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
            + "order by - \"empid\"") // 代码行
        .returns("name=Eric; empid=200\n" // 代码行
            + "name=Sebastian; empid=150\n" // 代码行
            + "name=Theodore; empid=110\n" // 代码行
            + "name=Bill; empid=100\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests sorting by an expression not in the '*' select clause. Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-176">[CALCITE-176] // JavaDoc注释内容
   * ORDER BY expression doesn't work with SELECT *</a>. */ // JavaDoc注释内容
  @Test void testOrderStarByExpr() { // 测试方法:测试OrderStarByExpr功能
    CalciteAssert.hr() // 代码行
        .query("select * from \"hr\".\"emps\"\n" // 代码行
            + "order by - \"empid\"") // 代码行
        .explainContains("EnumerableSort(sort0=[$5], dir0=[ASC])\n" // 代码行
            + "  EnumerableCalc(expr#0..4=[{inputs}], expr#5=[-($t0)], proj#0..5=[{exprs}])\n" // 代码行
            + "    EnumerableTableScan(table=[[hr, emps]])") // 代码行
        .returns("" // 代码行
            + "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500\n" // 代码行
            + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n" // 代码行
            + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testOrderUnionStarByExpr() { // 测试方法:测试OrderUnionStarByExpr功能
    CalciteAssert.hr() // 代码行
        .query("select * from \"hr\".\"emps\" where \"empid\" < 150\n" // 代码行
            + "union all\n" // 代码行
            + "select * from \"hr\".\"emps\" where \"empid\" > 150\n" // 代码行
            + "order by - \"empid\"") // 代码行
        .returns("" // 代码行
            + "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500\n" // 代码行
            + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests sorting by a CAST expression not in the select clause. */ // JavaDoc注释开始
  @Test void testOrderByCast() { // 测试方法:测试OrderByCast功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"customer_id\", \"postal_code\" from \"customer\"\n" // 代码行
            + "where \"customer_id\" < 5\n" // 代码行
            + "order by cast(substring(\"postal_code\" from 3) as integer) desc") // 代码行
        // ordered by last 3 digits (980, 674, 172, 057) // 单行注释
        .returns("customer_id=3; postal_code=73980\n" // 代码行
            + "customer_id=4; postal_code=74674\n" // 代码行
            + "customer_id=2; postal_code=17172\n" // 代码行
            + "customer_id=1; postal_code=15057\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY with all combinations of ASC, DESC, NULLS FIRST, // JavaDoc注释开始
   * NULLS LAST. */ // JavaDoc注释内容
  @Test void testOrderByNulls() { // 测试方法:测试OrderByNulls功能
    checkOrderByNulls(CalciteAssert.Config.FOODMART_CLONE); // 代码行
    checkOrderByNulls(CalciteAssert.Config.JDBC_FOODMART); // 代码行
  } // 代码行
 // 空行
  private void checkOrderByNulls(CalciteAssert.Config clone) { // checkOrderByNulls方法:检查ORDER BY NULLS功能
    checkOrderByDescNullsFirst(clone); // 代码行
    checkOrderByNullsFirst(clone); // 代码行
    checkOrderByDescNullsLast(clone); // 代码行
    checkOrderByNullsLast(clone); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ... DESC NULLS FIRST. */ // JavaDoc注释开始
  private void checkOrderByDescNullsFirst(CalciteAssert.Config config) { // checkOrderByDescNullsFirst方法:检查ORDER BY DESC NULLS FIRST
    CalciteAssert.that() // 代码行
        .with(config) // 代码行
        .query("select \"store_id\", \"grocery_sqft\"\n" // 代码行
            + "from \"foodmart\".\"store\"\n" // 代码行
            + "where \"store_id\" < 3 order by 2 desc nulls first") // 代码行
        .returns("store_id=0; grocery_sqft=null\n" // 代码行
            + "store_id=2; grocery_sqft=22271\n" // 代码行
            + "store_id=1; grocery_sqft=17475\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ... NULLS FIRST. */ // JavaDoc注释开始
  private void checkOrderByNullsFirst(CalciteAssert.Config config) { // checkOrderByNullsFirst方法:检查ORDER BY NULLS FIRST
    CalciteAssert.that() // 代码行
        .with(config) // 代码行
        .query("select \"store_id\", \"grocery_sqft\"\n" // 代码行
            + "from \"foodmart\".\"store\"\n" // 代码行
            + "where \"store_id\" < 3 order by 2 nulls first") // 代码行
        .returns("store_id=0; grocery_sqft=null\n" // 代码行
            + "store_id=1; grocery_sqft=17475\n" // 代码行
            + "store_id=2; grocery_sqft=22271\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ... DESC NULLS LAST. */ // JavaDoc注释开始
  private void checkOrderByDescNullsLast(CalciteAssert.Config config) { // checkOrderByDescNullsLast方法:检查ORDER BY DESC NULLS LAST
    CalciteAssert.that() // 代码行
        .with(config) // 代码行
        .query("select \"store_id\", \"grocery_sqft\"\n" // 代码行
            + "from \"foodmart\".\"store\"\n" // 代码行
            + "where \"store_id\" < 3 order by 2 desc nulls last") // 代码行
        .returns("store_id=2; grocery_sqft=22271\n" // 代码行
            + "store_id=1; grocery_sqft=17475\n" // 代码行
            + "store_id=0; grocery_sqft=null\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ... NULLS LAST. */ // JavaDoc注释开始
  private void checkOrderByNullsLast(CalciteAssert.Config config) { // checkOrderByNullsLast方法:检查ORDER BY NULLS LAST
    CalciteAssert.that() // 代码行
        .with(config) // 代码行
        .query("select \"store_id\", \"grocery_sqft\"\n" // 代码行
            + "from \"foodmart\".\"store\"\n" // 代码行
            + "where \"store_id\" < 3 order by 2 nulls last") // 代码行
        .returns("store_id=1; grocery_sqft=17475\n" // 代码行
            + "store_id=2; grocery_sqft=22271\n" // 代码行
            + "store_id=0; grocery_sqft=null\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ...  with various values of // JavaDoc注释开始
   * {@link CalciteConnectionConfig#defaultNullCollation()}. */ // JavaDoc注释内容
  @Test void testOrderByVarious() { // 测试方法:测试OrderByVarious功能
    final boolean[] booleans = {false, true}; // 代码行
    for (NullCollation nullCollation : NullCollation.values()) { // 代码行
      for (boolean asc : booleans) { // 代码行
        checkOrderBy(asc, nullCollation); // 代码行
      } // 代码行
    } // 代码行
  } // 代码行
 // 空行
  public void checkOrderBy(final boolean desc, // 代码行
      final NullCollation nullCollation) { // 代码行
    final Consumer<ResultSet> checker = resultSet -> { // 代码行
      final String msg = (desc ? "DESC" : "ASC") + ":" + nullCollation; // 代码行
      final List<Number> numbers = new ArrayList<>(); // 代码行
      try { // 代码行
        while (resultSet.next()) { // 代码行
          numbers.add((Number) resultSet.getObject(2)); // 代码行
        } // 代码行
      } catch (SQLException e) { // 代码行
        throw TestUtil.rethrow(e); // 代码行
      } // 代码行
      assertThat(msg, numbers, hasSize(3)); // 代码行
      assertThat(msg, numbers.get(nullCollation.last(desc) ? 2 : 0), // 代码行
          nullValue()); // 代码行
    }; // 代码行
    final CalciteAssert.AssertThat with = CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .with(CalciteConnectionProperty.DEFAULT_NULL_COLLATION, nullCollation); // 代码行
    final String sql = "select \"store_id\", \"grocery_sqft\" from \"store\"\n" // 代码行
        + "where \"store_id\" < 3 order by 2 " // 代码行
        + (desc ? " DESC" : ""); // 代码行
    final String sql1 = "select \"store_id\", \"grocery_sqft\" from \"store\"\n" // 代码行
        + "where \"store_id\" < 3 order by \"florist\", 2 " // 代码行
        + (desc ? " DESC" : ""); // 代码行
    final String sql2 = "select \"store_id\", \"grocery_sqft\" from \"store\"\n" // 代码行
        + "where \"store_id\" < 3 order by 2 " // 代码行
        + (desc ? " DESC" : "") // 代码行
        + ", 1"; // 代码行
    with.query(sql).returns(checker); // 代码行
    with.query(sql1).returns(checker); // 代码行
    with.query(sql2).returns(checker); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ... FETCH. */ // JavaDoc注释开始
  @Test void testOrderByFetch() { // 测试方法:测试OrderByFetch功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"store_id\", \"grocery_sqft\" from \"store\"\n" // 代码行
            + "where \"store_id\" < 10\n" // 代码行
            + "order by 1 fetch first 5 rows only") // 代码行
        .explainContains("PLAN=" // 代码行
            + "EnumerableCalc(expr#0..23=[{inputs}], store_id=[$t0], grocery_sqft=[$t16])\n" // 代码行
            + "  EnumerableLimit(fetch=[5])\n" // 代码行
            + "    EnumerableCalc(expr#0..23=[{inputs}], expr#24=[10], expr#25=[<($t0, $t24)], proj#0..23=[{exprs}], $condition=[$t25])\n" // 代码行
            + "      EnumerableTableScan(table=[[foodmart2, store]])\n") // 代码行
        .returns("store_id=0; grocery_sqft=null\n" // 代码行
            + "store_id=1; grocery_sqft=17475\n" // 代码行
            + "store_id=2; grocery_sqft=22271\n" // 代码行
            + "store_id=3; grocery_sqft=24390\n" // 代码行
            + "store_id=4; grocery_sqft=16844\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests ORDER BY ... OFFSET ... FETCH. */ // JavaDoc注释开始
  @Test void testOrderByOffsetFetch() { // 测试方法:测试OrderByOffsetFetch功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"store_id\", \"grocery_sqft\" from \"store\"\n" // 代码行
            + "where \"store_id\" < 10\n" // 代码行
            + "order by 1 offset 2 rows fetch next 5 rows only") // 代码行
        .returns("store_id=2; grocery_sqft=22271\n" // 代码行
            + "store_id=3; grocery_sqft=24390\n" // 代码行
            + "store_id=4; grocery_sqft=16844\n" // 代码行
            + "store_id=5; grocery_sqft=15012\n" // 代码行
            + "store_id=6; grocery_sqft=15337\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests FETCH with no ORDER BY. */ // JavaDoc注释开始
  @Test void testFetch() { // 测试方法:测试Fetch功能
    CalciteAssert.hr() // 代码行
        .query("select \"empid\" from \"hr\".\"emps\"\n" // 代码行
            + "fetch first 2 rows only") // 代码行
        .returns("empid=100\n" // 代码行
            + "empid=200\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testFetchStar() { // 测试方法:测试FetchStar功能
    CalciteAssert.hr() // 代码行
        .query("select * from \"hr\".\"emps\"\n" // 代码行
            + "fetch first 2 rows only") // 代码行
        .returns("" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n" // 代码行
            + "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500\n"); // 代码行
  } // 代码行
 // 空行
  /** "SELECT ... LIMIT 0" is executed differently. A planner rule converts the // JavaDoc注释开始
   * whole query to an empty rel. */ // JavaDoc注释内容
  @Test void testLimitZero() { // 测试方法:测试LimitZero功能
    CalciteAssert.hr() // 代码行
        .query("select * from \"hr\".\"emps\"\n" // 代码行
            + "limit 0") // 代码行
        .returns("") // 代码行
        .planContains( // 代码行
            "return org.apache.calcite.linq4j.Linq4j.asEnumerable(new Object[] {})"); // 代码行
  } // 代码行
 // 空行
  /** Alternative formulation for {@link #testFetchStar()}. */ // JavaDoc注释开始
  @Test void testLimitStar() { // 测试方法:测试LimitStar功能
    CalciteAssert.hr() // 代码行
        .query("select * from \"hr\".\"emps\"\n" // 代码行
            + "limit 2") // 代码行
        .returns("" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n" // 代码行
            + "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500\n"); // 代码行
  } // 代码行
 // 空行
  /** Limit implemented using {@link Queryable#take}. Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-96">[CALCITE-96] // JavaDoc注释内容
   * LIMIT against a table in a clone schema causes // JavaDoc注释内容
   * UnsupportedOperationException</a>. */ // JavaDoc注释内容
  @Test void testLimitOnQueryableTable() { // 测试方法:测试LimitOnQueryableTable功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select * from \"days\"\n" // 代码行
            + "limit 2") // 代码行
        .returns("day=1; week_day=Sunday\n" // 代码行
            + "day=2; week_day=Monday\n"); // 代码行
  } // 代码行
 // 空行
  /** Limit implemented using {@link Queryable#take}. Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-70">[CALCITE-70] // JavaDoc注释内容
   * Joins seem to be very expensive in memory</a>. */ // JavaDoc注释内容
  @Test void testSelfJoinCount() { // 测试方法:测试SelfJoinCount功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query( // 代码行
            "select count(*) as c from \"foodmart\".\"store\" as p1 join \"foodmart\".\"store\" as p2 using (\"store_id\")") // 代码行
        .returns("C=25\n") // 代码行
        .explainContains("JdbcToEnumerableConverter\n" // 代码行
            + "  JdbcAggregate(group=[{}], C=[COUNT()])\n" // 代码行
            + "    JdbcJoin(condition=[=($0, $1)], joinType=[inner])\n" // 代码行
            + "      JdbcProject(store_id=[$0])\n" // 代码行
            + "        JdbcTableScan(table=[[foodmart, store]])\n" // 代码行
            + "      JdbcProject(store_id=[$0])\n" // 代码行
            + "        JdbcTableScan(table=[[foodmart, store]])\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests composite GROUP BY where one of the columns has NULL values. */ // JavaDoc注释开始
  @Test void testGroupByNull() { // 测试方法:测试GroupByNull功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\", \"commission\", sum(\"salary\") s\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "group by \"deptno\", \"commission\"") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; commission=null; S=7000.0", // 代码行
            "deptno=20; commission=500; S=8000.0", // 代码行
            "deptno=10; commission=1000; S=10000.0", // 代码行
            "deptno=10; commission=250; S=11500.0"); // 代码行
  } // 代码行
 // 空行
  @Test void testGroupingSets() { // 测试方法:测试GroupingSets功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\", count(*) as c, sum(\"salary\") as s\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "group by grouping sets((\"deptno\"), ())") // 代码行
        .returnsUnordered( // 代码行
            "deptno=null; C=4; S=36500.0", // 代码行
            "deptno=10; C=3; S=28500.0", // 代码行
            "deptno=20; C=1; S=8000.0"); // 代码行
  } // 代码行
 // 空行
  @Test void testRollup() { // 测试方法:测试Rollup功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\", count(*) as c, sum(\"salary\") as s\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "group by rollup(\"deptno\")") // 代码行
        .returnsUnordered( // 代码行
            "deptno=null; C=4; S=36500.0", // 代码行
            "deptno=10; C=3; S=28500.0", // 代码行
            "deptno=20; C=1; S=8000.0"); // 代码行
  } // 代码行
 // 空行
  @Test void testCaseWhenOnNullableField() { // 测试方法:测试CaseWhenOnNullableField功能
    CalciteAssert.hr() // 代码行
        .query("select case when \"commission\" is not null " // 代码行
            + "then \"commission\" else 100 end\n" // 代码行
            + "from \"hr\".\"emps\"\n") // 代码行
        .explainContains("PLAN=EnumerableCalc(expr#0..4=[{inputs}]," // 代码行
            + " expr#5=[IS NOT NULL($t4)], expr#6=[CAST($t4):INTEGER NOT NULL]," // 代码行
            + " expr#7=[100], expr#8=[CASE($t5, $t6, $t7)], EXPR$0=[$t8])\n" // 代码行
            + "  EnumerableTableScan(table=[[hr, emps]])") // 代码行
        .returns("EXPR$0=1000\n" // 代码行
            + "EXPR$0=500\n" // 代码行
            + "EXPR$0=100\n" // 代码行
            + "EXPR$0=250\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testSelectValuesIncludeNull() { // 测试方法:测试SelectValuesIncludeNull功能
    CalciteAssert.that() // 代码行
        .query("select * from (values (null))") // 代码行
        .returns("EXPR$0=null\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4757">[CALCITE-4757] // JavaDoc注释内容
   * In Avatica, support columns of type "NULL" in query results</a>. */ // JavaDoc注释内容
  @Test void testSelectValuesIncludeNull2() { // 测试方法:测试SelectValuesIncludeNull2功能
    CalciteAssert.that() // 代码行
        .query("select * from (values (null, true))") // 代码行
        .returns("EXPR$0=null; EXPR$1=true\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testSelectDistinct() { // 测试方法:测试SelectDistinct功能
    CalciteAssert.hr() // 代码行
        .query("select distinct \"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\"\n") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10", // 代码行
            "deptno=20"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-397">[CALCITE-397] // JavaDoc注释内容
   * "SELECT DISTINCT *" on reflective schema gives ClassCastException at // JavaDoc注释内容
   * runtime</a>. */ // JavaDoc注释内容
  @Test void testSelectDistinctStar() { // 测试方法:测试SelectDistinctStar功能
    CalciteAssert.hr() // 代码行
        .query("select distinct *\n" // 代码行
            + "from \"hr\".\"emps\"\n") // 代码行
        .returnsCount(4) // 代码行
        .planContains(".distinct("); // 代码行
  } // 代码行
 // 空行
  /** Select distinct on composite key, one column of which is boolean to // JavaDoc注释开始
   * boot. */ // JavaDoc注释内容
  @Test void testSelectDistinctComposite() { // 测试方法:测试SelectDistinctComposite功能
    CalciteAssert.hr() // 代码行
        .query("select distinct \"empid\" > 140 as c, \"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\"\n") // 代码行
        .returnsUnordered( // 代码行
            "C=false; deptno=10", // 代码行
            "C=true; deptno=10", // 代码行
            "C=true; deptno=20") // 代码行
        .planContains(".distinct("); // 代码行
  } // 代码行
 // 空行
  /** Same result (and plan) as {@link #testSelectDistinct}. */ // JavaDoc注释开始
  @Test void testGroupByNoAggregates() { // 测试方法:测试GroupByNoAggregates功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "group by \"deptno\"") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10", // 代码行
            "deptno=20"); // 代码行
  } // 代码行
 // 空行
  /** Same result (and plan) as {@link #testSelectDistinct}. */ // JavaDoc注释开始
  @Test void testGroupByNoAggregatesAllColumns() { // 测试方法:测试GroupByNoAggregatesAllColumns功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "group by \"deptno\", \"empid\", \"name\", \"salary\", \"commission\"") // 代码行
        .returnsCount(4) // 代码行
        .planContains(".distinct("); // 代码行
  } // 代码行
 // 空行
  /** Same result (and plan) as {@link #testSelectDistinct}. */ // JavaDoc注释开始
  @Test void testGroupByMax1IsNull() { // 测试方法:测试GroupByMax1IsNull功能
    CalciteAssert.hr() // 代码行
        .query("select * from (\n" // 代码行
            + "select max(1) max_id\n" // 代码行
            + "from \"hr\".\"emps\" where 1=2\n" // 代码行
            + ") where max_id is null") // 代码行
        .returnsUnordered( // 代码行
            "MAX_ID=null"); // 代码行
  } // 代码行
 // 空行
  /** Same result (and plan) as {@link #testSelectDistinct}. */ // JavaDoc注释开始
  @Test void testGroupBy1Max1() { // 测试方法:测试GroupBy1Max1功能
    CalciteAssert.hr() // 代码行
        .query("select * from (\n" // 代码行
            + "select max(u) max_id\n" // 代码行
            + "from (select \"empid\"+\"deptno\" u, 1 cnst\n" // 代码行
            + "from \"hr\".\"emps\" a) where 1=2\n" // 代码行
            + "group by cnst\n" // 代码行
            + ") where max_id is null") // 代码行
        .returnsCount(0); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-403">[CALCITE-403] // JavaDoc注释内容
   * Enumerable gives NullPointerException with NOT on nullable // JavaDoc注释内容
   * expression</a>. */ // JavaDoc注释内容
  @Test void testHavingNot() throws IOException { // 测试方法:测试HavingNot功能
    withFoodMartQuery(6597).runs(); // 代码行
  } // 代码行
 // 空行
  /** Minimal case of {@link #testHavingNot()}. */ // JavaDoc注释开始
  @Test void testHavingNot2() { // 测试方法:测试HavingNot2功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select 1\n" // 代码行
            + "from \"store\"\n" // 代码行
            + "group by \"store\".\"store_street_address\"\n" // 代码行
            + "having NOT (sum(\"store\".\"grocery_sqft\") < 20000)") // 代码行
        .returnsCount(10); // 代码行
  } // 代码行
 // 空行
  /** ORDER BY on a sort-key does not require a sort. */ // JavaDoc注释开始
  @Test void testOrderOnSortedTable() { // 测试方法:测试OrderOnSortedTable功能
    // The ArrayTable "store" is sorted by "store_id". // 单行注释
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"day\"\n" // 代码行
            + "from \"days\"\n" // 代码行
            + "order by \"day\"") // 代码行
        .returns("day=1\n" // 代码行
            + "day=2\n" // 代码行
            + "day=3\n" // 代码行
            + "day=4\n" // 代码行
            + "day=5\n" // 代码行
            + "day=6\n" // 代码行
            + "day=7\n"); // 代码行
  } // 代码行
 // 空行
  /** ORDER BY on a sort-key does not require a sort. */ // JavaDoc注释开始
  @Test void testOrderSorted() { // 测试方法:测试OrderSorted功能
    // The ArrayTable "store" is sorted by "store_id". // 单行注释
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"store_id\"\n" // 代码行
            + "from \"store\"\n" // 代码行
            + "order by \"store_id\" limit 3") // 代码行
        .returns("store_id=0\n" // 代码行
            + "store_id=1\n" // 代码行
            + "store_id=2\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testWhereNot() { // 测试方法:测试WhereNot功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select 1\n" // 代码行
            + "from \"store\"\n" // 代码行
            + "where NOT (\"store\".\"grocery_sqft\" < 22000)\n" // 代码行
            + "group by \"store\".\"store_street_address\"\n") // 代码行
        .returnsCount(8); // 代码行
  } // 代码行
 // 空行
  /** Query that reads no columns from either underlying table. */ // JavaDoc注释开始
  @Test void testCountStar() { // 测试方法:测试CountStar功能
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 代码行
      CalciteAssert.hr() // 代码行
          .query("select count(*) c from \"hr\".\"emps\", \"hr\".\"depts\"") // 代码行
          .convertContains("LogicalAggregate(group=[{}], C=[COUNT()])\n" // 代码行
              + "  LogicalJoin(condition=[true], joinType=[inner])\n" // 代码行
              + "    LogicalProject(DUMMY=[0])\n" // 代码行
              + "      LogicalTableScan(table=[[hr, emps]])\n" // 代码行
              + "    LogicalProject(DUMMY=[0])\n" // 代码行
              + "      LogicalTableScan(table=[[hr, depts]])"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Same result (and plan) as {@link #testSelectDistinct}. */ // JavaDoc注释开始
  @Test void testCountUnionAll() { // 测试方法:测试CountUnionAll功能
    CalciteAssert.hr() // 代码行
        .query("select count(*) c from (\n" // 代码行
            + "select * from \"hr\".\"emps\" where 1=2\n" // 代码行
            + "union all\n" // 代码行
            + "select * from \"hr\".\"emps\" where 3=4\n" // 代码行
            + ")") // 代码行
        .returnsUnordered( // 代码行
            "C=0"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnionAll() { // 测试方法:测试UnionAll功能
    CalciteAssert.hr() // 代码行
        .query("select \"empid\", \"name\" from \"hr\".\"emps\" where \"deptno\"=10\n" // 代码行
            + "union all\n" // 代码行
            + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"empid\">=150") // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableUnion(all=[true])") // 代码行
        .returnsUnordered("empid=100; name=Bill", // 代码行
            "empid=110; name=Theodore", // 代码行
            "empid=150; name=Sebastian", // 代码行
            "empid=150; name=Sebastian", // 代码行
            "empid=200; name=Eric"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnion() { // 测试方法:测试Union功能
    final String sql = "" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"deptno\"=10\n" // 代码行
        + "union\n" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableUnion(all=[false])") // 代码行
        .returnsUnordered("empid=100; name=Bill", // 代码行
            "empid=110; name=Theodore", // 代码行
            "empid=150; name=Sebastian", // 代码行
            "empid=200; name=Eric"); // 代码行
  } // 代码行
 // 空行
  @Test void testIntersect() { // 测试方法:测试Intersect功能
    final String sql = "" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"deptno\"=10\n" // 代码行
        + "intersect\n" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 代码行
            planner.removeRule(CoreRules.INTERSECT_TO_DISTINCT)) // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableIntersect(all=[false])") // 代码行
        .returnsUnordered("empid=150; name=Sebastian"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case of // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6836">[CALCITE-6836] // JavaDoc注释内容
   * Add Rule to convert INTERSECT to EXISTS</a>. */ // JavaDoc注释内容
  @Test void testIntersectToExist() { // 测试方法:测试IntersectToExist功能
    final String sql = "" // 代码行
            + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"deptno\"=10\n" // 代码行
            + "intersect\n" // 代码行
            + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) // 代码行
            p -> { // 代码行
              p.removeRule(CoreRules.INTERSECT_TO_DISTINCT); // 代码行
              p.removeRule(EnumerableRules.ENUMERABLE_INTERSECT_RULE); // 代码行
              p.removeRule(EnumerableRules.ENUMERABLE_FILTER_RULE); // 代码行
              p.addRule(CoreRules.INTERSECT_TO_EXISTS); // 代码行
              p.addRule(CoreRules.FILTER_SUB_QUERY_TO_CORRELATE); // 代码行
              p.addRule(CoreRules.FILTER_TO_CALC); // 代码行
            }) // 代码行
        .explainContains("") // 代码行
        .returnsUnordered("empid=150; name=Sebastian"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case of // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6893">[CALCITE-6893] // JavaDoc注释内容
   * Remove agg from Union children in IntersectToDistinctRule</a>. */ // JavaDoc注释内容
  @Test void testIntersectToDistinct() { // 测试方法:测试IntersectToDistinct功能
    final String sql = "" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"deptno\"=10\n" // 代码行
        + "intersect\n" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
    final String[] returns = new String[] { // 代码行
        "empid=150; name=Sebastian"}; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .explainContains("EnumerableIntersect") // 代码行
        .returnsUnordered(returns); // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) // 代码行
            p -> { // 代码行
              p.removeRule(EnumerableRules.ENUMERABLE_INTERSECT_RULE); // 代码行
            }) // 代码行
        .explainContains("EnumerableUnion(all=[true])") // 代码行
        .returnsUnordered(returns); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6904">[CALCITE-6904] // JavaDoc注释内容
   * IS_NOT_DISTINCT_FROM is converted error in EnumerableJoinRule</a>. */ // JavaDoc注释内容
  @Test void testIsNotDistinctFrom() { // 测试方法:测试IsNotDistinctFrom功能
    final String sql = "" // 代码行
        + "select \"t1\".\"commission\" from \"hr\".\"emps\" as \"t1\"\n" // 代码行
        + "join\n" // 代码行
        + "\"hr\".\"emps\" as \"t2\"\n" // 代码行
        + "on \"t1\".\"commission\" is not distinct from \"t2\".\"commission\""; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .explainContains("NestedLoopJoin(condition=[IS NOT DISTINCT FROM($0, $1)]") // 代码行
        .returnsUnordered("commission=1000", // 代码行
            "commission=250", // 代码行
            "commission=500", // 代码行
            "commission=null"); // 代码行
  } // 代码行
 // 空行
  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6880">[CALCITE-6880] // JavaDoc注释开始
   * Implement IntersectToSemiJoinRule</a>. */ // JavaDoc注释内容
  @Test void testIntersectToSemiJoin() { // 测试方法:测试IntersectToSemiJoin功能
    final String sql = "" // 代码行
        + "select \"commission\" from \"hr\".\"emps\"\n" // 代码行
        + "intersect\n" // 代码行
        + "select \"commission\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) // 代码行
            planner -> { // 代码行
              planner.removeRule(CoreRules.INTERSECT_TO_DISTINCT); // 代码行
              planner.removeRule(EnumerableRules.ENUMERABLE_INTERSECT_RULE); // 代码行
              planner.addRule(CoreRules.INTERSECT_TO_SEMI_JOIN); // 代码行
            }) // 代码行
        .explainContains("") // 代码行
        .returnsUnordered("commission=500", // 代码行
            "commission=null"); // 代码行
  } // 代码行
 // 空行
  /** Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-6948">[CALCITE-6948] // JavaDoc注释开始
   * Implement IntersectToSemiJoinRule</a>. */ // JavaDoc注释内容
  @Test void testMinusToAntiJoinRule() { // 测试方法:测试MinusToAntiJoinRule功能
    final String sql = "" // 代码行
        + "select \"commission\" from \"hr\".\"emps\"\n" // 代码行
        + "except\n" // 代码行
        + "select \"commission\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
 // 空行
    final String[] returns = new String[] { // 代码行
        "commission=1000", // 代码行
        "commission=250"}; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .returnsUnordered(returns); // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) // 代码行
            p -> { // 代码行
              p.removeRule(CoreRules.MINUS_TO_DISTINCT); // 代码行
              p.removeRule(ENUMERABLE_MINUS_RULE); // 代码行
              p.addRule(CoreRules.MINUS_TO_ANTI_JOIN); // 代码行
            }) // 代码行
        .explainContains("joinType=[anti]") // 代码行
        .returnsUnordered(returns); // 代码行
  } // 代码行
 // 空行
  @Test void testExcept() { // 测试方法:测试Except功能
    final String sql = "" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"deptno\"=10\n" // 代码行
        + "except\n" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableMinus(all=[false])") // 代码行
        .returnsUnordered("empid=100; name=Bill", // 代码行
            "empid=110; name=Theodore"); // 代码行
  } // 代码行
 // 空行
  @Test void testMinusToDistinct() { // 测试方法:测试MinusToDistinct功能
    final String sql = "" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"deptno\"=10\n" // 代码行
        + "except\n" // 代码行
        + "select \"empid\", \"name\" from \"hr\".\"emps\" where \"empid\">=150"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 代码行
            planner.removeRule(ENUMERABLE_MINUS_RULE)) // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableCalc(expr#0..3=[{inputs}], expr#4=[0], expr#5=[>($t2, $t4)], " // 代码行
            + "expr#6=[=($t3, $t4)], expr#7=[AND($t5, $t6)], proj#0..1=[{exprs}], " // 代码行
            + "$condition=[$t7])\n" // 代码行
            + "  EnumerableAggregate(group=[{0, 1}], agg#0=[COUNT() FILTER $2], agg#1=[COUNT() " // 代码行
            + "FILTER $3])\n" // 代码行
            + "    EnumerableCalc(expr#0..2=[{inputs}], expr#3=[0], expr#4=[=($t2, $t3)], " // 代码行
            + "expr#5=[1], expr#6=[=($t2, $t5)], proj#0..1=[{exprs}], $f2=[$t4], $f3=[$t6])\n" // 代码行
            + "      EnumerableUnion(all=[true])\n" // 代码行
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=[0], expr#6=[CAST($t1):INTEGER" // 代码行
            + " NOT NULL], expr#7=[10], expr#8=[=($t6, $t7)], empid=[$t0], name=[$t2], $f2=[$t5]," // 代码行
            + " $condition=[$t8])\n" // 代码行
            + "          EnumerableTableScan(table=[[hr, emps]])\n" // 代码行
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=[1], expr#6=[150], expr#7=[>=" // 代码行
            + "($t0, $t6)], empid=[$t0], name=[$t2], $f2=[$t5], $condition=[$t7])\n" // 代码行
            + "          EnumerableTableScan(table=[[hr, emps]])\n") // 代码行
        .returnsUnordered("empid=100; name=Bill", // 代码行
            "empid=110; name=Theodore"); // 代码行
  } // 代码行
 // 空行
  @Test void testMinusToDistinctWithSubquery() { // 测试方法:测试MinusToDistinctWithSubquery功能
    final String sql = "with doubleEmp as (select \"empid\", \"name\"\n" // 代码行
        + "                   from \"hr\".\"emps\"\n" // 代码行
        + "                   union all\n" // 代码行
        + "                   select \"empid\", \"name\"\n" // 代码行
        + "                   from \"hr\".\"emps\")\n" // 代码行
        + "select \"empid\", \"name\"\n" // 代码行
        + "from doubleEmp\n" // 代码行
        + "except\n" // 代码行
        + "select \"empid\", \"name\"\n" // 代码行
        + "from \"hr\".\"emps\""; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 代码行
            planner.removeRule(ENUMERABLE_MINUS_RULE)) // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableCalc(expr#0..3=[{inputs}], expr#4=[0], expr#5=[>($t2, $t4)], " // 代码行
            + "expr#6=[=($t3, $t4)], expr#7=[AND($t5, $t6)], proj#0..1=[{exprs}], " // 代码行
            + "$condition=[$t7])\n" // 代码行
            + "  EnumerableAggregate(group=[{0, 1}], agg#0=[COUNT() FILTER $2], agg#1=[COUNT() " // 代码行
            + "FILTER $3])\n" // 代码行
            + "    EnumerableCalc(expr#0..2=[{inputs}], expr#3=[0], expr#4=[=($t2, $t3)], " // 代码行
            + "expr#5=[1], expr#6=[=($t2, $t5)], proj#0..1=[{exprs}], $f2=[$t4], $f3=[$t6])\n" // 代码行
            + "      EnumerableUnion(all=[true])\n" // 代码行
            + "        EnumerableCalc(expr#0..1=[{inputs}], expr#2=[0], proj#0..2=[{exprs}])\n" // 代码行
            + "          EnumerableUnion(all=[true])\n" // 代码行
            + "            EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2])\n" // 代码行
            + "              EnumerableTableScan(table=[[hr, emps]])\n" // 代码行
            + "            EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2])\n" // 代码行
            + "              EnumerableTableScan(table=[[hr, emps]])\n" // 代码行
            + "        EnumerableCalc(expr#0..4=[{inputs}], expr#5=[1], empid=[$t0], name=[$t2], " // 代码行
            + "$f2=[$t5])\n" // 代码行
            + "          EnumerableTableScan(table=[[hr, emps]])") // 代码行
        .returnsUnordered(""); // 代码行
  } // 代码行
 // 空行
  /** Tests that SUM and AVG over empty set return null. COUNT returns 0. */ // JavaDoc注释开始
  @Test void testAggregateEmpty() { // 测试方法:测试AggregateEmpty功能
    CalciteAssert.hr() // 代码行
        .query("select\n" // 代码行
            + " count(*) as cs,\n" // 代码行
            + " count(\"deptno\") as c,\n" // 代码行
            + " sum(\"deptno\") as s,\n" // 代码行
            + " avg(\"deptno\") as a\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "where \"deptno\" < 0") // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableCalc(expr#0..1=[{inputs}], expr#2=[0], expr#3=[=($t0, $t2)], expr#4=[null:JavaType(class java.lang.Integer)], expr#5=[CASE($t3, $t4, $t1)], expr#6=[/($t5, $t0)], expr#7=[CAST($t6):JavaType(class java.lang.Integer)], CS=[$t0], C=[$t0], S=[$t5], A=[$t7])\n" // 代码行
            + "  EnumerableAggregate(group=[{}], CS=[COUNT()], S=[$SUM0($1)])\n" // 代码行
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[0], expr#6=[<($t1, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 代码行
            + "      EnumerableTableScan(table=[[hr, emps]])\n") // 代码行
        .returns("CS=0; C=0; S=null; A=null\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests that count(deptno) is reduced to count(). */ // JavaDoc注释开始
  @Test void testReduceCountNotNullable() { // 测试方法:测试ReduceCountNotNullable功能
    CalciteAssert.hr() // 代码行
        .query("select\n" // 代码行
            + " count(\"deptno\") as cs,\n" // 代码行
            + " count(*) as cs2\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "where \"deptno\" < 0") // 代码行
        .explainContains("" // 代码行
            + "PLAN=EnumerableCalc(expr#0=[{inputs}], CS=[$t0], CS2=[$t0])\n" // 代码行
            + "  EnumerableAggregate(group=[{}], CS=[COUNT()])\n" // 代码行
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[0], expr#6=[<($t1, $t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 代码行
            + "      EnumerableTableScan(table=[[hr, emps]])\n") // 代码行
        .returns("CS=0; CS2=0\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests that {@code count(deptno, commission, commission + 1)} is reduced to // JavaDoc注释开始
   * {@code count(commission, commission + 1)}, because deptno is NOT NULL. */ // JavaDoc注释内容
  @Test void testReduceCompositeCountNotNullable() { // 测试方法:测试ReduceCompositeCountNotNullable功能
    CalciteAssert.hr() // 代码行
        .query("select\n" // 代码行
            + " count(\"deptno\", \"commission\", \"commission\" + 1) as cs\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .explainContains("" // 代码行
            + "EnumerableAggregate(group=[{}], CS=[COUNT($0, $1)])\n" // 代码行
            + "  EnumerableCalc(expr#0..4=[{inputs}], expr#5=[1], expr#6=[+($t4, $t5)], commission=[$t4], $f2=[$t6])\n" // 代码行
            + "    EnumerableTableScan(table=[[hr, emps]])") // 代码行
        .returns("CS=3\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests sorting by a column that is already sorted. */ // JavaDoc注释开始
  @Test void testOrderByOnSortedTable() { // 测试方法:测试OrderByOnSortedTable功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select * from \"time_by_day\"\n" // 代码行
            + "order by \"time_id\"") // 代码行
        .explainContains( // 代码行
            "PLAN=EnumerableTableScan(table=[[foodmart2, time_by_day]])\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests sorting by a column that is already sorted. */ // JavaDoc注释开始
  @ParameterizedTest // 参数化测试注解:标记为参数化测试方法
  @MethodSource("explainFormats") // 方法源注解:指定测试数据的来源方法
  void testOrderByOnSortedTable2(String format) { // 代码行
    String expected = null; // 代码行
    final String extra; // 代码行
    switch (format) { // 代码行
    case "text": // 代码行
      expected = "" // 代码行
          + "PLAN=EnumerableCalc(expr#0..9=[{inputs}], expr#10=[370], expr#11=[<($t0, $t10)], proj#0..1=[{exprs}], $condition=[$t11])\n" // 代码行
          + "  EnumerableTableScan(table=[[foodmart2, time_by_day]])\n\n"; // 代码行
      extra = ""; // 代码行
      break; // 代码行
    case "dot": // 代码行
      expected = "PLAN=digraph {\n" // 代码行
          + "\"EnumerableTableScan\\ntable = [foodmart2, \\ntime_by_day]\\n\" -> " // 代码行
          + "\"EnumerableCalc\\nexpr#0..9 = {inputs}\\nexpr#10 = 370\\nexpr#11 = <($t0, $t1\\n0)" // 代码行
          + "\\nproj#0..1 = {exprs}\\n$condition = $t11\" [label=\"0\"]\n" // 代码行
          + "}\n" // 代码行
          + "\n"; // 代码行
      extra = " as dot "; // 代码行
      break; // 代码行
    default: // 代码行
      throw new AssertionError("unknown " + format); // 代码行
    } // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .query("select \"time_id\", \"the_date\" from \"time_by_day\"\n" // 代码行
            + "where \"time_id\" < 370\n" // 代码行
            + "order by \"time_id\"") // 代码行
        .returns("time_id=367; the_date=1997-01-01 00:00:00\n" // 代码行
            + "time_id=368; the_date=1997-01-02 00:00:00\n" // 代码行
            + "time_id=369; the_date=1997-01-03 00:00:00\n") // 代码行
        .explainMatches(extra, CalciteAssert.checkResultContains(expected)); // 代码行
  } // 代码行
 // 空行
  @Test void testWithInsideWhereExists() { // 测试方法:测试WithInsideWhereExists功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\" from \"hr\".\"emps\"\n" // 代码行
            + "where exists (\n" // 代码行
            + "  with dept2 as (select * from \"hr\".\"depts\" where \"depts\".\"deptno\" >= \"emps\".\"deptno\")\n" // 代码行
            + "  select 1 from dept2 where \"deptno\" <= \"emps\".\"deptno\")") // 代码行
        .returnsUnordered("deptno=10", // 代码行
            "deptno=10", // 代码行
            "deptno=10"); // 代码行
  } // 代码行
 // 空行
  @Test void testWithOrderBy() { // 测试方法:测试WithOrderBy功能
    CalciteAssert.hr() // 代码行
        .query("with emp2 as (select * from \"hr\".\"emps\")\n" // 代码行
            + "select * from emp2\n" // 代码行
            + "order by \"deptno\" desc, \"empid\" desc") // 代码行
        .returns("" // 代码行
            + "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500\n" // 代码行
            + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n" // 代码行
            + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests windowed aggregation. */ // JavaDoc注释开始
  @Test void testWinAgg() { // 测试方法:测试WinAgg功能
    CalciteAssert.hr() // 代码行
        .query("select" // 代码行
            + " \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + "sum(\"salary\" + \"empid\") over w as s,\n" // 代码行
            + " 5 as five,\n" // 代码行
            + " min(\"salary\") over w as m,\n" // 代码行
            + " count(*) over w as c\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "window w as (partition by \"deptno\" order by \"empid\" rows 1 preceding)") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, S REAL, FIVE INTEGER NOT NULL, M REAL, C BIGINT NOT NULL]") // 代码行
        .explainContains("" // 代码行
            + "EnumerableCalc(expr#0..7=[{inputs}], expr#8=[0:BIGINT], expr#9=[>($t4, $t8)], expr#10=[null:JavaType(class java.lang.Float)], expr#11=[CASE($t9, $t5, $t10)], expr#12=[5], deptno=[$t1], empid=[$t0], S=[$t11], FIVE=[$t12], M=[$t6], C=[$t7])\n" // 代码行
            + "  EnumerableWindow(window#0=[window(partition {1} order by [0] rows between $4 PRECEDING and CURRENT ROW aggs [COUNT($3), $SUM0($3), MIN($2), COUNT()])], constants=[[1]])\n" // 代码行
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[+($t3, $t0)], proj#0..1=[{exprs}], salary=[$t3], $3=[$t5])\n" // 代码行
            + "      EnumerableTableScan(table=[[hr, emps]])\n") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; empid=100; S=10100.0; FIVE=5; M=10000.0; C=1", // 代码行
            "deptno=10; empid=110; S=21710.0; FIVE=5; M=10000.0; C=2", // 代码行
            "deptno=10; empid=150; S=18760.0; FIVE=5; M=7000.0; C=2", // 代码行
            "deptno=20; empid=200; S=8200.0; FIVE=5; M=8000.0; C=1") // 代码行
        .planContains(CalciteSystemProperty.DEBUG.value() // 代码行
            ? "_list.add(new Object[] {\n" // 代码行
            + "        row[0],\n" // box-unbox is optimized // 代码行
            + "        row[1],\n" // 代码行
            + "        row[2],\n" // 代码行
            + "        row[3],\n" // 代码行
            + "        COUNTa0w0,\n" // 代码行
            + "        $SUM0a1w0,\n" // 代码行
            + "        MINa2w0,\n" // 代码行
            + "        COUNTa3w0});" // 代码行
            : "_list.add(new Object[] {\n" // 代码行
            + "        row[0],\n" // box-unbox is optimized // 代码行
            + "        row[1],\n" // 代码行
            + "        row[2],\n" // 代码行
            + "        row[3],\n" // 代码行
            + "        a0w0,\n" // 代码行
            + "        a1w0,\n" // 代码行
            + "        a2w0,\n" // 代码行
            + "        a3w0});") // 代码行
        .planContains("      Float case_when_value;\n" // 代码行
            + "              if (org.apache.calcite.runtime.SqlFunctions.toLong(current[4]) > 0L) {\n" // 代码行
            + "                case_when_value = Float.valueOf(org.apache.calcite.runtime.SqlFunctions.toFloat(current[5]));\n" // 代码行
            + "              } else {\n" // 代码行
            + "                case_when_value = null;\n" // 代码行
            + "              }") // 代码行
        .planContains("return new Object[] {\n" // 代码行
            + "                  current[1],\n" // 代码行
            + "                  current[0],\n" // 代码行
            + "                  case_when_value,\n" // 代码行
            + "                  5,\n" // 代码行
            + "                  current[6],\n" // 代码行
            + "                  current[7]};\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests windowed aggregation with multiple windows. // JavaDoc注释开始
   * One window straddles the current row. // JavaDoc注释内容
   * Some windows have no PARTITION BY clause. */ // JavaDoc注释内容
  @Test void testWinAgg2() { // 测试方法:测试WinAgg2功能
    CalciteAssert.hr() // 代码行
        .query("select" // 代码行
            + " \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + "sum(\"salary\" + \"empid\") over w as s,\n" // 代码行
            + " 5 as five,\n" // 代码行
            + " min(\"salary\") over w as m,\n" // 代码行
            + " count(*) over w as c,\n" // 代码行
            + " count(*) over w2 as c2,\n" // 代码行
            + " count(*) over w11 as c11,\n" // 代码行
            + " count(*) over w11dept as c11dept\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "window w as (order by \"empid\" rows 1 preceding),\n" // 代码行
            + " w2 as (order by \"empid\" rows 2 preceding),\n" // 代码行
            + " w11 as (order by \"empid\" rows between 1 preceding and 1 following),\n" // 代码行
            + " w11dept as (partition by \"deptno\" order by \"empid\" rows between 1 preceding and 1 following)") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, S REAL, FIVE INTEGER NOT NULL, M REAL, C BIGINT NOT NULL, C2 BIGINT NOT NULL, C11 BIGINT NOT NULL, C11DEPT BIGINT NOT NULL]") // 代码行
        // Check that optimizes for window whose PARTITION KEY is empty // 单行注释
        .planContains("tempList.size()") // 代码行
        .returnsUnordered( // 代码行
            "deptno=20; empid=200; S=15350.0; FIVE=5; M=7000.0; C=2; C2=3; C11=2; C11DEPT=1", // 代码行
            "deptno=10; empid=100; S=10100.0; FIVE=5; M=10000.0; C=1; C2=1; C11=2; C11DEPT=2", // 代码行
            "deptno=10; empid=110; S=21710.0; FIVE=5; M=10000.0; C=2; C2=2; C11=3; C11DEPT=3", // 代码行
            "deptno=10; empid=150; S=18760.0; FIVE=5; M=7000.0; C=2; C2=3; C11=3; C11DEPT=2"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests that window aggregates work when computed over non-nullable // JavaDoc注释内容
   * {@link org.apache.calcite.adapter.enumerable.JavaRowFormat#SCALAR} inputs. // JavaDoc注释内容
   * Window aggregates use temporary buffers, thus need to check if // JavaDoc注释内容
   * primitives are properly boxed and un-boxed. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testWinAggScalarNonNullPhysType() { // 测试方法:测试WinAggScalarNonNullPhysType功能
    String planLine = // 代码行
        "a0s0w0 = org.apache.calcite.runtime.SqlFunctions.lesser(a0s0w0, org.apache.calcite.runtime.SqlFunctions.toFloat(_rows[j]));"; // 代码行
    if (CalciteSystemProperty.DEBUG.value()) { // 代码行
      planLine = planLine.replace("a0s0w0", "MINa0s0w0"); // 代码行
    } // 代码行
    CalciteAssert.hr() // 代码行
        .query("select min(\"salary\"+1) over w as m\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "window w as (order by \"salary\"+1 rows 1 preceding)\n") // 代码行
        .typeIs( // 代码行
            "[M REAL]") // 代码行
        .planContains(planLine) // 代码行
        .returnsUnordered( // 代码行
            "M=7001.0", // 代码行
            "M=7001.0", // 代码行
            "M=8001.0", // 代码行
            "M=10001.0"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests that {@link org.apache.calcite.rel.logical.LogicalCalc} is // JavaDoc注释内容
   * implemented properly when input is // JavaDoc注释内容
   * {@link org.apache.calcite.rel.logical.LogicalWindow} and literal. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testWinAggScalarNonNullPhysTypePlusOne() { // 测试方法:测试WinAggScalarNonNullPhysTypePlusOne功能
    String planLine = // 代码行
        "a0s0w0 = org.apache.calcite.runtime.SqlFunctions.lesser(a0s0w0, org.apache.calcite.runtime.SqlFunctions.toFloat(_rows[j]));"; // 代码行
    if (CalciteSystemProperty.DEBUG.value()) { // 代码行
      planLine = planLine.replace("a0s0w0", "MINa0s0w0"); // 代码行
    } // 代码行
    CalciteAssert.hr() // 代码行
        .query("select 1+min(\"salary\"+1) over w as m\n" // 代码行
            + "from \"hr\".\"emps\"\n" // 代码行
            + "window w as (order by \"salary\"+1 rows 1 preceding)\n") // 代码行
        .typeIs( // 代码行
            "[M REAL]") // 代码行
        .planContains(planLine) // 代码行
        .returnsUnordered( // 代码行
            "M=7002.0", // 代码行
            "M=7002.0", // 代码行
            "M=8002.0", // 代码行
            "M=10002.0"); // 代码行
  } // 代码行
 // 空行
  /** Tests for RANK and ORDER BY ... DESCENDING, NULLS FIRST, NULLS LAST. */ // JavaDoc注释开始
  @Test void testWinAggRank() { // 测试方法:测试WinAggRank功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + " \"commission\",\n" // 代码行
            + " rank() over (partition by \"deptno\" order by \"commission\" desc nulls first) as rcnf,\n" // 代码行
            + " rank() over (partition by \"deptno\" order by \"commission\" desc nulls last) as rcnl,\n" // 代码行
            + " rank() over (partition by \"deptno\" order by \"empid\") as r,\n" // 代码行
            + " rank() over (partition by \"deptno\" order by \"empid\" desc) as rd\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, commission INTEGER, RCNF BIGINT NOT NULL, RCNL BIGINT NOT NULL, R BIGINT NOT NULL, RD BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; empid=100; commission=1000; RCNF=2; RCNL=1; R=1; RD=3", // 代码行
            "deptno=10; empid=110; commission=250; RCNF=3; RCNL=2; R=2; RD=2", // 代码行
            "deptno=10; empid=150; commission=null; RCNF=1; RCNL=3; R=3; RD=1", // 代码行
            "deptno=20; empid=200; commission=500; RCNF=1; RCNL=1; R=1; RD=1"); // 代码行
  } // 代码行
 // 空行
  /** Tests for RANK with same values. */ // JavaDoc注释开始
  @Test void testWinAggRankValues() { // 测试方法:测试WinAggRankValues功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " rank() over (order by \"deptno\") as r\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, R BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; R=1", // 代码行
            "deptno=10; R=1", // 代码行
            "deptno=10; R=1", // 代码行
            "deptno=20; R=4"); // 4 for rank and 2 for dense_rank // 代码行
  } // 代码行
 // 空行
  /** Tests for RANK with same values. */ // JavaDoc注释开始
  @Test void testWinAggRankValuesDesc() { // 测试方法:测试WinAggRankValuesDesc功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " rank() over (order by \"deptno\" desc) as r\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, R BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; R=2", // 代码行
            "deptno=10; R=2", // 代码行
            "deptno=10; R=2", // 代码行
            "deptno=20; R=1"); // 代码行
  } // 代码行
 // 空行
  /** Tests for DENSE_RANK with same values. */ // JavaDoc注释开始
  @Test void testWinAggDenseRankValues() { // 测试方法:测试WinAggDenseRankValues功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " dense_rank() over (order by \"deptno\") as r\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, R BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; R=1", // 代码行
            "deptno=10; R=1", // 代码行
            "deptno=10; R=1", // 代码行
            "deptno=20; R=2"); // 代码行
  } // 代码行
 // 空行
  /** Tests for DENSE_RANK with same values. */ // JavaDoc注释开始
  @Test void testWinAggDenseRankValuesDesc() { // 测试方法:测试WinAggDenseRankValuesDesc功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " dense_rank() over (order by \"deptno\" desc) as r\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, R BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; R=2", // 代码行
            "deptno=10; R=2", // 代码行
            "deptno=10; R=2", // 代码行
            "deptno=20; R=1"); // 代码行
  } // 代码行
 // 空行
  /** Tests for DATE +- INTERVAL window frame. */ // JavaDoc注释开始
  @Test void testWinIntervalFrame() { // 测试方法:测试WinIntervalFrame功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + " \"hire_date\",\n" // 代码行
            + " count(*) over (partition by \"deptno\" order by \"hire_date\"" // 代码行
            + " range between interval '1' year preceding and interval '1' year following) as r\n" // 代码行
            + "from (select \"empid\", \"deptno\",\n" // 代码行
            + "  DATE '2014-06-12' + \"empid\"*interval '0' day \"hire_date\"\n" // 代码行
            + "  from \"hr\".\"emps\")") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, hire_date DATE NOT NULL, R BIGINT NOT NULL]") // 代码行
        .returnsUnordered("deptno=10; empid=100; hire_date=2014-06-12; R=3", // 代码行
            "deptno=10; empid=110; hire_date=2014-06-12; R=3", // 代码行
            "deptno=10; empid=150; hire_date=2014-06-12; R=3", // 代码行
            "deptno=20; empid=200; hire_date=2014-06-12; R=1"); // 代码行
  } // 代码行
 // 空行
  @Test void testNestedWin() { // 测试方法:测试NestedWin功能
    CalciteAssert.hr() // 代码行
        .query("select\n" // 代码行
            + " lag(a2, 1, 0) over (partition by \"deptno\" order by a1) as lagx\n" // 代码行
            + "from\n" // 代码行
            + " (\n" // 代码行
            + "  select\n" // 代码行
            + "   \"deptno\",\n" // 代码行
            + "   \"salary\" / \"commission\" as a1,\n" // 代码行
            + "   sum(\"commission\") over ( partition by \"deptno\" order by \"salary\" / " // 代码行
            + "\"commission\") / sum(\"commission\") over (partition by \"deptno\") as a2\n" // 代码行
            + "  from\n" // 代码行
            + "   \"hr\".\"emps\"\n" // 代码行
            + " )\n") // 代码行
        .typeIs( // 代码行
            "[LAGX INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "LAGX=0", // 代码行
            "LAGX=0", // 代码行
            "LAGX=0", // 代码行
            "LAGX=1"); // 代码行
  } // 代码行
 // 空行
  private void startOfGroupStep1(String startOfGroup) { // startOfGroupStep1方法:测试分组第一步
    CalciteAssert.that() // 代码行
        .query("select t.*\n" // 代码行
            + "  from (\n" // 代码行
            + "       select  t.*,\n" // 代码行
            + "               case when " + startOfGroup // 代码行
            + " then 0 else 1 end start_of_group\n" // 代码行
            + "         from " // 代码行
            + START_OF_GROUP_DATA // 代码行
            + ") t\n") // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, VAL INTEGER NOT NULL, EXPECTED INTEGER NOT NULL, START_OF_GROUP INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; VAL=0; EXPECTED=1; START_OF_GROUP=1", // 代码行
            "RN=2; VAL=0; EXPECTED=1; START_OF_GROUP=0", // 代码行
            "RN=3; VAL=1; EXPECTED=2; START_OF_GROUP=1", // 代码行
            "RN=4; VAL=0; EXPECTED=3; START_OF_GROUP=1", // 代码行
            "RN=5; VAL=0; EXPECTED=3; START_OF_GROUP=0", // 代码行
            "RN=6; VAL=0; EXPECTED=3; START_OF_GROUP=0", // 代码行
            "RN=7; VAL=1; EXPECTED=4; START_OF_GROUP=1", // 代码行
            "RN=8; VAL=1; EXPECTED=4; START_OF_GROUP=0"); // 代码行
  } // 代码行
 // 空行
  private void startOfGroupStep2(String startOfGroup) { // startOfGroupStep2方法:测试分组第二步
    CalciteAssert.that() // 代码行
        .query("select t.*\n" // 代码行
            // current row is assumed, group_id should be NOT NULL // 单行注释
            + "       ,sum(start_of_group) over (order by rn rows unbounded preceding) group_id\n" // 代码行
            + "  from (\n" // 代码行
            + "       select  t.*,\n" // 代码行
            + "               case when " + startOfGroup // 代码行
            + " then 0 else 1 end start_of_group\n" // 代码行
            + "         from " // 代码行
            + START_OF_GROUP_DATA // 代码行
            + ") t\n") // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, VAL INTEGER NOT NULL, EXPECTED INTEGER NOT NULL, START_OF_GROUP INTEGER NOT NULL, GROUP_ID INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; VAL=0; EXPECTED=1; START_OF_GROUP=1; GROUP_ID=1", // 代码行
            "RN=2; VAL=0; EXPECTED=1; START_OF_GROUP=0; GROUP_ID=1", // 代码行
            "RN=3; VAL=1; EXPECTED=2; START_OF_GROUP=1; GROUP_ID=2", // 代码行
            "RN=4; VAL=0; EXPECTED=3; START_OF_GROUP=1; GROUP_ID=3", // 代码行
            "RN=5; VAL=0; EXPECTED=3; START_OF_GROUP=0; GROUP_ID=3", // 代码行
            "RN=6; VAL=0; EXPECTED=3; START_OF_GROUP=0; GROUP_ID=3", // 代码行
            "RN=7; VAL=1; EXPECTED=4; START_OF_GROUP=1; GROUP_ID=4", // 代码行
            "RN=8; VAL=1; EXPECTED=4; START_OF_GROUP=0; GROUP_ID=4"); // 代码行
  } // 代码行
 // 空行
  private void startOfGroupStep3(String startOfGroup) { // startOfGroupStep3方法:测试分组第三步
    CalciteAssert.that() // 代码行
        .query("select group_id, min(rn) min_rn, max(rn) max_rn,\n" // 代码行
            + "  count(rn) cnt_rn, avg(val) avg_val" // 代码行
            + " from (\n" // 代码行
            + "select t.*\n" // 代码行
            // current row is assumed, group_id should be NOT NULL // 单行注释
            + "       ,sum(start_of_group) over (order by rn rows unbounded preceding) group_id\n" // 代码行
            + "  from (\n" // 代码行
            + "       select  t.*,\n" // 代码行
            + "               case when " + startOfGroup // 代码行
            + " then 0 else 1 end start_of_group\n" // 代码行
            + "         from " // 代码行
            + START_OF_GROUP_DATA // 代码行
            + ") t\n" // 代码行
            + ") group by group_id\n") // 代码行
        .typeIs( // 代码行
            "[GROUP_ID INTEGER NOT NULL, MIN_RN INTEGER NOT NULL, MAX_RN INTEGER NOT NULL, CNT_RN BIGINT NOT NULL, AVG_VAL INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "GROUP_ID=1; MIN_RN=1; MAX_RN=2; CNT_RN=2; AVG_VAL=0", // 代码行
            "GROUP_ID=2; MIN_RN=3; MAX_RN=3; CNT_RN=1; AVG_VAL=1", // 代码行
            "GROUP_ID=3; MIN_RN=4; MAX_RN=6; CNT_RN=3; AVG_VAL=0", // 代码行
            "GROUP_ID=4; MIN_RN=7; MAX_RN=8; CNT_RN=2; AVG_VAL=1"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step1, implemented as last_value. // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLastValueStep1() { // 测试方法:测试StartOfGroupLastValueStep1功能
    startOfGroupStep1( // 代码行
        "val = last_value(val) over (order by rn rows between 1 preceding and 1 preceding)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step2, that gets the final group numbers // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLastValueStep2() { // 测试方法:测试StartOfGroupLastValueStep2功能
    startOfGroupStep2( // 代码行
        "val = last_value(val) over (order by rn rows between 1 preceding and 1 preceding)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step3, that aggregates the computed groups // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLastValueStep3() { // 测试方法:测试StartOfGroupLastValueStep3功能
    startOfGroupStep3( // 代码行
        "val = last_value(val) over (order by rn rows between 1 preceding and 1 preceding)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step1, implemented as last_value. // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLagStep1() { // 测试方法:测试StartOfGroupLagStep1功能
    startOfGroupStep1("val = lag(val) over (order by rn)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step2, that gets the final group numbers // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLagValueStep2() { // 测试方法:测试StartOfGroupLagValueStep2功能
    startOfGroupStep2("val = lag(val) over (order by rn)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step3, that aggregates the computed groups // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLagStep3() { // 测试方法:测试StartOfGroupLagStep3功能
    startOfGroupStep3("val = lag(val) over (order by rn)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step1, implemented as last_value. // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLeadStep1() { // 测试方法:测试StartOfGroupLeadStep1功能
    startOfGroupStep1("val = lead(val, -1) over (order by rn)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step2, that gets the final group numbers // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLeadValueStep2() { // 测试方法:测试StartOfGroupLeadValueStep2功能
    startOfGroupStep2("val = lead(val, -1) over (order by rn)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests start_of_group approach for grouping of adjacent intervals. // JavaDoc注释内容
   * This is a step3, that aggregates the computed groups // JavaDoc注释内容
   * http://timurakhmadeev.wordpress.com/2013/07/21/start_of_group/ // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testStartOfGroupLeadStep3() { // 测试方法:测试StartOfGroupLeadStep3功能
    startOfGroupStep3("val = lead(val, -1) over (order by rn)"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests default value of LAG function. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testLagDefaultValue() { // 测试方法:测试LagDefaultValue功能
    CalciteAssert.that() // 代码行
        .query("select t.*, lag(rn+expected,1,42) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, VAL INTEGER NOT NULL, EXPECTED INTEGER NOT NULL, L INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; VAL=0; EXPECTED=1; L=42", // 代码行
            "RN=2; VAL=0; EXPECTED=1; L=2", // 代码行
            "RN=3; VAL=1; EXPECTED=2; L=3", // 代码行
            "RN=4; VAL=0; EXPECTED=3; L=5", // 代码行
            "RN=5; VAL=0; EXPECTED=3; L=7", // 代码行
            "RN=6; VAL=0; EXPECTED=3; L=8", // 代码行
            "RN=7; VAL=1; EXPECTED=4; L=9", // 代码行
            "RN=8; VAL=1; EXPECTED=4; L=11"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests default value of LEAD function. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testLeadDefaultValue() { // 测试方法:测试LeadDefaultValue功能
    CalciteAssert.that() // 代码行
        .query("select t.*, lead(rn+expected,1,42) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, VAL INTEGER NOT NULL, EXPECTED INTEGER NOT NULL, L INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; VAL=0; EXPECTED=1; L=3", // 代码行
            "RN=2; VAL=0; EXPECTED=1; L=5", // 代码行
            "RN=3; VAL=1; EXPECTED=2; L=7", // 代码行
            "RN=4; VAL=0; EXPECTED=3; L=8", // 代码行
            "RN=5; VAL=0; EXPECTED=3; L=9", // 代码行
            "RN=6; VAL=0; EXPECTED=3; L=11", // 代码行
            "RN=7; VAL=1; EXPECTED=4; L=12", // 代码行
            "RN=8; VAL=1; EXPECTED=4; L=42"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests expression in offset value of LAG function. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testLagExpressionOffset() { // 测试方法:测试LagExpressionOffset功能
    CalciteAssert.that() // 代码行
        .query("select t.*, lag(rn, expected, 42) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, VAL INTEGER NOT NULL, EXPECTED INTEGER NOT NULL, L INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; VAL=0; EXPECTED=1; L=42", // 代码行
            "RN=2; VAL=0; EXPECTED=1; L=1", // 代码行
            "RN=3; VAL=1; EXPECTED=2; L=1", // 代码行
            "RN=4; VAL=0; EXPECTED=3; L=1", // 代码行
            "RN=5; VAL=0; EXPECTED=3; L=2", // 代码行
            "RN=6; VAL=0; EXPECTED=3; L=3", // 代码行
            "RN=7; VAL=1; EXPECTED=4; L=3", // 代码行
            "RN=8; VAL=1; EXPECTED=4; L=4"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests DATE as offset argument of LAG function. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testLagInvalidOffsetArgument() { // 测试方法:测试LagInvalidOffsetArgument功能
    CalciteAssert.that() // 代码行
        .query("select t.*,\n" // 代码行
            + "  lag(rn, DATE '2014-06-20', 42) over (order by rn) l\n" // 代码行
            + "from " + START_OF_GROUP_DATA) // 代码行
        .throws_( // 代码行
            "Cannot apply 'LAG' to arguments of type 'LAG(<INTEGER>, <DATE>, <INTEGER>)'"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests LAG function with IGNORE NULLS. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testLagIgnoreNulls() { // 测试方法:测试LagIgnoreNulls功能
    final String sql = "select\n" // 代码行
        + "  lag(rn, expected, 42) ignore nulls over (w) l,\n" // 代码行
        + "  lead(rn, expected) over (w),\n" // 代码行
        + "  lead(rn, expected) over (order by expected)\n" // 代码行
        + "from (values" // 代码行
        + "  (1,0,1),\n" // 代码行
        + "  (2,0,1),\n" // 代码行
        + "  (2,0,1),\n" // 代码行
        + "  (3,1,2),\n" // 代码行
        + "  (4,0,3),\n" // 代码行
        + "  (cast(null as int),0,3),\n" // 代码行
        + "  (5,0,3),\n" // 代码行
        + "  (6,0,3),\n" // 代码行
        + "  (7,1,4),\n" // 代码行
        + "  (8,1,4)) as t(rn,val,expected)\n" // 代码行
        + "window w as (order by rn)"; // 代码行
    CalciteAssert.that().query(sql) // 代码行
        .throws_("IGNORE NULLS not supported"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests NTILE(2). // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testNtile1() { // 测试方法:测试Ntile1功能
    CalciteAssert.that() // 代码行
        .query("select rn, ntile(1) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, L BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; L=1", // 代码行
            "RN=2; L=1", // 代码行
            "RN=3; L=1", // 代码行
            "RN=4; L=1", // 代码行
            "RN=5; L=1", // 代码行
            "RN=6; L=1", // 代码行
            "RN=7; L=1", // 代码行
            "RN=8; L=1"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Tests NTILE(2). // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testNtile2() { // 测试方法:测试Ntile2功能
    CalciteAssert.that() // 代码行
        .query("select rn, ntile(2) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, L BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; L=1", // 代码行
            "RN=2; L=1", // 代码行
            "RN=3; L=1", // 代码行
            "RN=4; L=1", // 代码行
            "RN=5; L=2", // 代码行
            "RN=6; L=2", // 代码行
            "RN=7; L=2", // 代码行
            "RN=8; L=2"); // 代码行
  } // 代码行
 // 空行
  @Disabled("Have no idea how to validate that expression is constant") // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testNtileConstantArgs() { // 测试方法:测试NtileConstantArgs功能
    CalciteAssert.that() // 代码行
        .query("select rn, ntile(1+1) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .typeIs( // 代码行
            "[RN INTEGER NOT NULL, VAL INTEGER NOT NULL, EXPECTED INTEGER NOT NULL, L INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "RN=1; L=1", // 代码行
            "RN=2; L=1", // 代码行
            "RN=3; L=1", // 代码行
            "RN=4; L=1", // 代码行
            "RN=5; L=2", // 代码行
            "RN=6; L=2", // 代码行
            "RN=7; L=2", // 代码行
            "RN=8; L=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testNtileNegativeArg() { // 测试方法:测试NtileNegativeArg功能
    CalciteAssert.that() // 代码行
        .query("select rn, ntile(-1) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .throws_( // 代码行
            "Argument to function 'NTILE' must be a positive integer literal"); // 代码行
  } // 代码行
 // 空行
  @Test void testNtileDecimalArg() { // 测试方法:测试NtileDecimalArg功能
    CalciteAssert.that() // 代码行
        .query("select rn, ntile(3.141592653) over (order by rn) l\n" // 代码行
            + " from " + START_OF_GROUP_DATA) // 代码行
        .throws_( // 代码行
            "Cannot apply 'NTILE' to arguments of type 'NTILE(<DECIMAL(10, 9)>)'"); // 代码行
  } // 代码行
 // 空行
  /** Tests for FIRST_VALUE. */ // JavaDoc注释开始
  @Test void testWinAggFirstValue() { // 测试方法:测试WinAggFirstValue功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + " \"commission\",\n" // 代码行
            + " first_value(\"commission\") over (partition by \"deptno\" order by \"empid\") as r\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, commission INTEGER, R INTEGER]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; empid=100; commission=1000; R=1000", // 代码行
            "deptno=10; empid=110; commission=250; R=1000", // 代码行
            "deptno=10; empid=150; commission=null; R=1000", // 代码行
            "deptno=20; empid=200; commission=500; R=500"); // 代码行
  } // 代码行
 // 空行
  /** Tests for FIRST_VALUE desc. */ // JavaDoc注释开始
  @Test void testWinAggFirstValueDesc() { // 测试方法:测试WinAggFirstValueDesc功能
    CalciteAssert.hr() // 代码行
        .query("select  \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + " \"commission\",\n" // 代码行
            + " first_value(\"commission\") over (partition by \"deptno\" order by \"empid\" desc) as r\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, commission INTEGER, R INTEGER]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; empid=100; commission=1000; R=null", // 代码行
            "deptno=10; empid=110; commission=250; R=null", // 代码行
            "deptno=10; empid=150; commission=null; R=null", // 代码行
            "deptno=20; empid=200; commission=500; R=500"); // 代码行
  } // 代码行
 // 空行
  /** Tests for FIRST_VALUE empty window. */ // JavaDoc注释开始
  @Test void testWinAggFirstValueEmptyWindow() { // 测试方法:测试WinAggFirstValueEmptyWindow功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + " \"commission\",\n" // 代码行
            + " first_value(\"commission\") over (partition by \"deptno\" order by \"empid\" desc range between 1000 preceding and 999 preceding) as r\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, commission INTEGER, R INTEGER]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; empid=100; commission=1000; R=null", // 代码行
            "deptno=10; empid=110; commission=250; R=null", // 代码行
            "deptno=10; empid=150; commission=null; R=null", // 代码行
            "deptno=20; empid=200; commission=500; R=null"); // 代码行
  } // 代码行
 // 空行
  /** Tests for ROW_NUMBER. */ // JavaDoc注释开始
  @Test void testWinRowNumber() { // 测试方法:测试WinRowNumber功能
    CalciteAssert.hr() // 代码行
        .query("select \"deptno\",\n" // 代码行
            + " \"empid\",\n" // 代码行
            + " \"commission\",\n" // 代码行
            + " row_number() over (partition by \"deptno\") as r,\n" // 代码行
            + " row_number() over (partition by \"deptno\" order by \"commission\" desc nulls first) as rcnf,\n" // 代码行
            + " row_number() over (partition by \"deptno\" order by \"commission\" desc nulls last) as rcnl,\n" // 代码行
            + " row_number() over (partition by \"deptno\" order by \"empid\") as r,\n" // 代码行
            + " row_number() over (partition by \"deptno\" order by \"empid\" desc) as rd\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[deptno INTEGER NOT NULL, empid INTEGER NOT NULL, commission INTEGER, R BIGINT NOT NULL, RCNF BIGINT NOT NULL, RCNL BIGINT NOT NULL, R BIGINT NOT NULL, RD BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; empid=100; commission=1000; R=1; RCNF=2; RCNL=1; R=1; RD=3", // 代码行
            "deptno=10; empid=110; commission=250; R=3; RCNF=3; RCNL=2; R=2; RD=2", // 代码行
            "deptno=10; empid=150; commission=null; R=2; RCNF=1; RCNL=3; R=3; RD=1", // 代码行
            "deptno=20; empid=200; commission=500; R=1; RCNF=1; RCNL=1; R=1; RD=1"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6837">[CALCITE-6837] // JavaDoc注释内容
   * Invalid code generated for ROW_NUMBER function in Enumerable convention</a>. */ // JavaDoc注释内容
  @Test void testWinRowNumber1() { // 测试方法:测试WinRowNumber1功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_SCOTT) // 代码行
        .query("select row_number() over () from dept") // 代码行
        .returnsUnordered("EXPR$0=1", // 代码行
            "EXPR$0=2", // 代码行
            "EXPR$0=3", // 代码行
            "EXPR$0=4"); // 代码行
  } // 代码行
 // 空行
  /** Tests UNBOUNDED PRECEDING clause. */ // JavaDoc注释开始
  @Test void testOverUnboundedPreceding() { // 测试方法:测试OverUnboundedPreceding功能
    CalciteAssert.hr() // 代码行
        .query("select \"empid\",\n" // 代码行
            + "  \"commission\",\n" // 代码行
            + "  count(\"empid\") over (partition by 42\n" // 代码行
            + "    order by \"commission\" nulls first\n" // 代码行
            + "    rows between UNBOUNDED PRECEDING and current row) as m\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[empid INTEGER NOT NULL, commission INTEGER, M BIGINT NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "empid=100; commission=1000; M=4", // 代码行
            "empid=200; commission=500; M=3", // 代码行
            "empid=150; commission=null; M=1", // 代码行
            "empid=110; commission=250; M=2"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3563">[CALCITE-3563] // JavaDoc注释内容
   * When resolving method call in calcite runtime, add type check and match // JavaDoc注释内容
   * mechanism for input arguments</a>. */ // JavaDoc注释内容
  @Test void testMethodParameterTypeMatch() { // 测试方法:测试MethodParameterTypeMatch功能
    CalciteAssert.that() // 代码行
        .query("SELECT mod(12.5, cast(3 as bigint))") // 代码行
        .planContains("final java.math.BigDecimal literal_value = " // 代码行
            + "$L4J$C$new_java_math_BigDecimal_12_5_") // 代码行
        .planContains("org.apache.calcite.runtime.SqlFunctions.mod(literal_value, " // 代码行
            + "$L4J$C$new_java_math_BigDecimal_3L_)") // 代码行
        .returns("EXPR$0=0.5\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests UNBOUNDED PRECEDING clause. */ // JavaDoc注释开始
  @Test void testSumOverUnboundedPreceding() { // 测试方法:测试SumOverUnboundedPreceding功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select \"empid\",\n" // 代码行
            + "  \"commission\",\n" // 代码行
            + "  sum(\"empid\") over (partition by 42\n" // 代码行
            + "    order by \"commission\" nulls first\n" // 代码行
            + "    rows between UNBOUNDED PRECEDING and current row) as m\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[empid INTEGER NOT NULL, commission INTEGER, M INTEGER NOT NULL]") // 代码行
        .returnsUnordered( // 代码行
            "empid=100; commission=1000; M=560", // 代码行
            "empid=110; commission=250; M=260", // 代码行
            "empid=150; commission=null; M=150", // 代码行
            "empid=200; commission=500; M=460"); // 代码行
  } // 代码行
 // 空行
  /** Tests that sum over possibly empty window is nullable. */ // JavaDoc注释开始
  @Test void testSumOverPossiblyEmptyWindow() { // 测试方法:测试SumOverPossiblyEmptyWindow功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select \"empid\",\n" // 代码行
            + "  \"commission\",\n" // 代码行
            + "  sum(\"empid\") over (partition by 42\n" // 代码行
            + "    order by \"commission\" nulls first\n" // 代码行
            + "    rows between UNBOUNDED PRECEDING and 1 preceding) as m\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .typeIs( // 代码行
            "[empid INTEGER NOT NULL, commission INTEGER, M INTEGER]") // 代码行
        .returnsUnordered( // 代码行
            "empid=100; commission=1000; M=460", // 代码行
            "empid=110; commission=250; M=150", // 代码行
            "empid=150; commission=null; M=null", // 代码行
            "empid=200; commission=500; M=260"); // 代码行
  } // 代码行
 // 空行
  /** Tests windowed aggregation with no ORDER BY clause. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-285">[CALCITE-285] // JavaDoc注释内容
   * Window functions throw exception without ORDER BY</a>. // JavaDoc注释内容
   * // JavaDoc注释内容
   * <p>Note: // JavaDoc注释内容
   * // JavaDoc注释内容
   * <ul> // JavaDoc注释内容
   * <li>With no ORDER BY, the window is over all rows in the partition. // JavaDoc注释内容
   * <li>With an ORDER BY, the implicit frame is 'RANGE BETWEEN // JavaDoc注释内容
   *     UNBOUNDED PRECEDING AND CURRENT ROW'. // JavaDoc注释内容
   * <li>With no ORDER BY or PARTITION BY, the window contains all rows in the // JavaDoc注释内容
   *     table. // JavaDoc注释内容
   * </ul> // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testOverNoOrder() { // 测试方法:测试OverNoOrder功能
    // If no range is specified, default is "RANGE BETWEEN UNBOUNDED PRECEDING // 单行注释
    // AND CURRENT ROW". // 单行注释
    // The aggregate function is within the current partition; // 单行注释
    // if there is no partition, that means the whole table. // 单行注释
    // Rows are deemed "equal to" the current row per the ORDER BY clause. // 单行注释
    // If there is no ORDER BY clause, CURRENT ROW has the same effect as // 单行注释
    // UNBOUNDED FOLLOWING; that is, no filtering effect at all. // 单行注释
    final String sql = "select *,\n" // 代码行
        + " count(*) over (partition by deptno) as m1,\n" // 代码行
        + " count(*) over (partition by deptno order by ename) as m2,\n" // 代码行
        + " count(*) over () as m3\n" // 代码行
        + "from emp"; // 代码行
    withEmpDept(sql).returnsUnordered( // 代码行
        "ENAME=Adam ; DEPTNO=50; GENDER=M; M1=2; M2=1; M3=9", // 代码行
        "ENAME=Alice; DEPTNO=30; GENDER=F; M1=2; M2=1; M3=9", // 代码行
        "ENAME=Bob  ; DEPTNO=10; GENDER=M; M1=2; M2=1; M3=9", // 代码行
        "ENAME=Eric ; DEPTNO=20; GENDER=M; M1=1; M2=1; M3=9", // 代码行
        "ENAME=Eve  ; DEPTNO=50; GENDER=F; M1=2; M2=2; M3=9", // 代码行
        "ENAME=Grace; DEPTNO=60; GENDER=F; M1=1; M2=1; M3=9", // 代码行
        "ENAME=Jane ; DEPTNO=10; GENDER=F; M1=2; M2=2; M3=9", // 代码行
        "ENAME=Susan; DEPTNO=30; GENDER=F; M1=2; M2=2; M3=9", // 代码行
        "ENAME=Wilma; DEPTNO=null; GENDER=F; M1=1; M2=1; M3=9"); // 代码行
  } // 代码行
 // 空行
  /** Tests that field-trimming creates a project near the table scan. */ // JavaDoc注释开始
  @Test void testTrimFields() { // 测试方法:测试TrimFields功能
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 代码行
      CalciteAssert.hr() // 代码行
          .query("select \"name\", count(\"commission\") + 1\n" // 代码行
              + "from \"hr\".\"emps\"\n" // 代码行
              + "group by \"deptno\", \"name\"") // 代码行
          .convertContains("LogicalProject(name=[$1], EXPR$1=[+($2, 1)])\n" // 代码行
              + "  LogicalAggregate(group=[{0, 1}], agg#0=[COUNT($2)])\n" // 代码行
              + "    LogicalProject(deptno=[$1], name=[$2], commission=[$4])\n" // 代码行
              + "      LogicalTableScan(table=[[hr, emps]])\n"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests that field-trimming creates a project near the table scan, in a // JavaDoc注释开始
   * query with windowed-aggregation. */ // JavaDoc注释内容
  @Test void testTrimFieldsOver() { // 测试方法:测试TrimFieldsOver功能
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 代码行
      // The correct plan has a project on a filter on a project on a scan. // 单行注释
      CalciteAssert.hr() // 代码行
          .query("select \"name\",\n" // 代码行
              + "  count(\"commission\") over (partition by \"deptno\") + 1\n" // 代码行
              + "from \"hr\".\"emps\"\n" // 代码行
              + "where \"empid\" > 10") // 代码行
          .convertContains("" // 代码行
              + "LogicalProject(name=[$2], EXPR$1=[+(COUNT($3) OVER (PARTITION BY $1), 1)])\n" // 代码行
              + "  LogicalFilter(condition=[>($0, 10)])\n" // 代码行
              + "    LogicalProject(empid=[$0], deptno=[$1], name=[$2], commission=[$4])\n" // 代码行
              + "      LogicalTableScan(table=[[hr, emps]])\n"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests window aggregate whose argument is a constant. */ // JavaDoc注释开始
  @Test void testWinAggConstant() { // 测试方法:测试WinAggConstant功能
    CalciteAssert.hr() // 代码行
        .query("select max(1) over (partition by \"deptno\"\n" // 代码行
            + "  order by \"empid\") as m\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .returnsUnordered( // 代码行
            "M=1", // 代码行
            "M=1", // 代码行
            "M=1", // 代码行
            "M=1"); // 代码行
  } // 代码行
 // 空行
  /** Tests multiple window aggregates over constants. // JavaDoc注释开始
   * This tests that EnumerableWindowRel is able to reference the right slot // JavaDoc注释内容
   * when accessing constant for aggregation argument. */ // JavaDoc注释内容
  @Test void testWinAggConstantMultipleConstants() { // 测试方法:测试WinAggConstantMultipleConstants功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select \"deptno\", sum(1) over (partition by \"deptno\"\n" // 代码行
            + "  order by \"empid\" rows between unbounded preceding and current row) as a,\n" // 代码行
            + " sum(-1) over (partition by \"deptno\"\n" // 代码行
            + "  order by \"empid\" rows between unbounded preceding and current row) as b\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; A=1; B=-1", // 代码行
            "deptno=10; A=2; B=-2", // 代码行
            "deptno=10; A=3; B=-3", // 代码行
            "deptno=20; A=1; B=-1"); // 代码行
  } // 代码行
 // 空行
  /** Tests window aggregate PARTITION BY constant. */ // JavaDoc注释开始
  @Test void testWinAggPartitionByConstant() { // 测试方法:测试WinAggPartitionByConstant功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("" // 代码行
            // *0 is used to make results predictable. // 单行注释
            // If using just max(empid) calcite cannot compute the result // 单行注释
            // properly since it does not support range windows yet :( // 单行注释
            + "select max(\"empid\"*0) over (partition by 42\n" // 代码行
            + "  order by \"empid\") as m\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .returnsUnordered( // 代码行
            "M=0", // 代码行
            "M=0", // 代码行
            "M=0", // 代码行
            "M=0"); // 代码行
  } // 代码行
 // 空行
  /** Tests window aggregate ORDER BY constant. Unlike in SELECT ... ORDER BY, // JavaDoc注释开始
   * the constant does not mean a column. It means a constant, therefore the // JavaDoc注释内容
   * order of the rows is not changed. */ // JavaDoc注释内容
  @Test void testWinAggOrderByConstant() { // 测试方法:测试WinAggOrderByConstant功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("" // 代码行
            // *0 is used to make results predictable. // 单行注释
            // If using just max(empid) calcite cannot compute the result // 单行注释
            // properly since it does not support range windows yet :( // 单行注释
            + "select max(\"empid\"*0) over (partition by \"deptno\"\n" // 代码行
            + "  order by 42) as m\n" // 代码行
            + "from \"hr\".\"emps\"") // 代码行
        .returnsUnordered( // 代码行
            "M=0", // 代码行
            "M=0", // 代码行
            "M=0", // 代码行
            "M=0"); // 代码行
  } // 代码行
 // 空行
  /** Tests WHERE comparing a nullable integer with an integer literal. */ // JavaDoc注释开始
  @Test void testWhereNullable() { // 测试方法:测试WhereNullable功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select * from \"hr\".\"emps\"\n" // 代码行
            + "where \"commission\" > 800") // 代码行
        .returns( // 代码行
            "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for rewriting queries that contain {@code GROUP_ID()} function. // JavaDoc注释开始
   * For instance, the query // JavaDoc注释内容
   * {@code // JavaDoc注释内容
   *    select deptno, group_id() as gid // JavaDoc注释内容
   *    from scott.emp // JavaDoc注释内容
   *    group by grouping sets(deptno, deptno, deptno, (), ()) // JavaDoc注释内容
   * } // JavaDoc注释内容
   * will be converted into: // JavaDoc注释内容
   * {@code // JavaDoc注释内容
   *    select deptno, 0 as gid // JavaDoc注释内容
   *    from scott.emp group by grouping sets(deptno, ()) // JavaDoc注释内容
   *    union all // JavaDoc注释内容
   *    select deptno, 1 as gid // JavaDoc注释内容
   *    from scott.emp group by grouping sets(deptno, ()) // JavaDoc注释内容
   *    union all // JavaDoc注释内容
   *    select deptno, 2 as gid // JavaDoc注释内容
   *    from scott.emp group by grouping sets(deptno) // JavaDoc注释内容
   * } // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testGroupId() { // 测试方法:测试GroupId功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.SCOTT) // 代码行
        .query("select deptno, group_id() + 1 as g, count(*) as c\n" // 代码行
            + "from \"scott\".emp\n" // 代码行
            + "group by grouping sets (deptno, deptno, deptno, (), ())\n" // 代码行
            + "having group_id() > 0") // 代码行
        .explainContains("EnumerableCalc(expr#0..2=[{inputs}], expr#3=[1], expr#4=[+($t1, $t3)], " // 代码行
            + "expr#5=[0:BIGINT], expr#6=[>($t1, $t5)], DEPTNO=[$t0], G=[$t4], C=[$t2], $condition=[$t6])\n" // 代码行
            + "  EnumerableUnion(all=[true])\n" // 代码行
            + "    EnumerableCalc(expr#0..1=[{inputs}], expr#2=[0:BIGINT], DEPTNO=[$t0], $f1=[$t2], C=[$t1])\n" // 代码行
            + "      EnumerableAggregate(group=[{7}], groups=[[{7}, {}]], C=[COUNT()])\n" // 代码行
            + "        EnumerableTableScan(table=[[scott, EMP]])\n" // 代码行
            + "    EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1:BIGINT], DEPTNO=[$t0], $f1=[$t2], C=[$t1])\n" // 代码行
            + "      EnumerableAggregate(group=[{7}], groups=[[{7}, {}]], C=[COUNT()])\n" // 代码行
            + "        EnumerableTableScan(table=[[scott, EMP]])\n" // 代码行
            + "    EnumerableCalc(expr#0..1=[{inputs}], expr#2=[2:BIGINT], DEPTNO=[$t0], $f1=[$t2], C=[$t1])\n" // 代码行
            + "      EnumerableAggregate(group=[{7}], C=[COUNT()])\n" // 代码行
            + "        EnumerableTableScan(table=[[scott, EMP]])") // 代码行
        .returnsUnordered("DEPTNO=10; G=2; C=3", // 代码行
            "DEPTNO=10; G=3; C=3", // 代码行
            "DEPTNO=20; G=2; C=5", // 代码行
            "DEPTNO=20; G=3; C=5", // 代码行
            "DEPTNO=30; G=2; C=6", // 代码行
            "DEPTNO=30; G=3; C=6", // 代码行
            "DEPTNO=null; G=2; C=14"); // 代码行
  } // 代码行
 // 空行
  /** Tests // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-980">[CALCITE-980] // JavaDoc注释内容
   * Not (C='a' or C='b') causes NPE</a>. */ // JavaDoc注释内容
  @Test void testWhereOrAndNullable() { // 测试方法:测试WhereOrAndNullable功能
    /* Generates the following code: // 代码行
       public boolean moveNext() { // 代码行
         while (inputEnumerator.moveNext()) { // 代码行
           final Object[] current = (Object[]) inputEnumerator.current(); // 代码行
           final String inp0_ = current[0] == null ? (String) null : current[0].toString(); // 代码行
           final String inp1_ = current[1] == null ? (String) null : current[1].toString(); // 代码行
           if (inp0_ != null && org.apache.calcite.runtime.SqlFunctions.eq(inp0_, "a") // 代码行
               && (inp1_ != null && org.apache.calcite.runtime.SqlFunctions.eq(inp1_, "b")) // 代码行
               || inp0_ != null && org.apache.calcite.runtime.SqlFunctions.eq(inp0_, "b") // 代码行
               && (inp1_ != null && org.apache.calcite.runtime.SqlFunctions.eq(inp1_, "c"))) { // 代码行
             return true; // 代码行
           } // 代码行
         } // 代码行
         return false; // 代码行
       } // 代码行
     */ // JavaDoc注释内容
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("with tst(c) as (values('a'),('b'),('c'),(cast(null as varchar)))" // 代码行
            + " select u.c u, v.c v from tst u, tst v where ((u.c = 'a' and v.c = 'b') or (u.c = 'b' and v.c = 'c'))") // 代码行
        .returnsUnordered( // 代码行
            "U=a; V=b", // 代码行
            "U=b; V=c"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-980">[CALCITE-980] // JavaDoc注释内容
   * different flavors of boolean logic</a>. // JavaDoc注释内容
   * // JavaDoc注释内容
   * @see QuidemTest sql/conditions.iq */ // JavaDoc注释内容
  @Disabled("Fails with org.codehaus.commons.compiler.CompileException: Line 16, Column 112:" // 禁用测试注解:标记当前测试方法为禁用状态
      + " Cannot compare types \"int\" and \"java.lang.String\"\n") // 代码行
  @Test void testComparingIntAndString() { // 测试方法:测试ComparingIntAndString功能
    // if (((...test.ReflectiveSchemaTest.IntAndString) inputEnumerator.current()).id == "T") // 单行注释
 // 空行
    CalciteAssert.that() // 代码行
        .withSchema("s", // 代码行
            new ReflectiveSchema( // 代码行
                new CatchallSchema())) // 代码行
        .query("select a.\"value\", b.\"value\"\n" // 代码行
            + "  from \"bools\" a\n" // 代码行
            + "     , \"bools\" b\n" // 代码行
            + " where b.\"value\" = 'T'\n" // 代码行
            + " order by 1, 2") // 代码行
        .returnsUnordered( // 代码行
            "should fail with 'not a number' sql error while converting text to number"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1015">[CALCITE-1015] // JavaDoc注释内容
   * OFFSET 0 causes AssertionError</a>. */ // JavaDoc注释内容
  @Test void testTrivialSort() { // 测试方法:测试TrivialSort功能
    final String sql = "select a.\"value\", b.\"value\"\n" // 代码行
        + "  from \"bools\" a\n" // 代码行
        + "     , \"bools\" b\n" // 代码行
        + " offset 0"; // 代码行
    CalciteAssert.that() // 代码行
        .withSchema("s", // 代码行
            new ReflectiveSchema( // 代码行
                new CatchallSchema())) // 代码行
        .query(sql) // 代码行
        .returnsUnordered("value=T; value=T", // 代码行
            "value=T; value=F", // 代码行
            "value=T; value=null", // 代码行
            "value=F; value=T", // 代码行
            "value=F; value=F", // 代码行
            "value=F; value=null", // 代码行
            "value=null; value=T", // 代码行
            "value=null; value=F", // 代码行
            "value=null; value=null"); // 代码行
  } // 代码行
 // 空行
  /** Tests the LIKE operator. */ // JavaDoc注释开始
  @Test void testLike() { // 测试方法:测试Like功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select * from \"hr\".\"emps\"\n" // 代码行
            + "where \"name\" like '%i__'") // 代码行
        .returns("" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n" // 代码行
            + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests array index. */ // JavaDoc注释开始
  @Test void testArrayIndexing() { // 测试方法:测试ArrayIndexing功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query( // 代码行
            "select \"deptno\", \"employees\"[1] as e from \"hr\".\"depts\"\n").returnsUnordered( // 代码行
        "deptno=10; E={100, 10, Bill, 10000.0, 1000}", // 代码行
        "deptno=30; E=null", // 代码行
        "deptno=40; E={200, 20, Eric, 8000.0, 500}"); // 代码行
  } // 代码行
 // 空行
  @Test void testVarcharEquals() { // 测试方法:测试VarcharEquals功能
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query("select \"lname\" from \"customer\" where \"lname\" = 'Nowmer'") // 代码行
        .returns("lname=Nowmer\n"); // 代码行
 // 空行
    // lname is declared as VARCHAR(30), comparing it with a string longer // 单行注释
    // than 30 characters would introduce a cast to the least restrictive // 单行注释
    // type, thus lname would be cast to a varchar(40) in this case. // 单行注释
    // These sorts of casts are removed though when constructing the jdbc // 单行注释
    // sql, since e.g. HSQLDB does not support them. // 单行注释
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query("select count(*) as c from \"customer\" " // 代码行
            + "where \"lname\" = 'this string is longer than 30 characters'") // 代码行
        .returns("C=0\n"); // 代码行
 // 空行
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query("select count(*) as c from \"customer\" " // 代码行
            + "where cast(\"customer_id\" as char(20)) = 'this string is longer than 30 characters'") // 代码行
        .returns("C=0\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1153">[CALCITE-1153] // JavaDoc注释内容
   * Invalid CAST when push JOIN down to Oracle</a>. */ // JavaDoc注释内容
  @Test void testJoinMismatchedVarchar() { // 测试方法:测试JoinMismatchedVarchar功能
    final String sql = "select count(*) as c\n" // 代码行
        + "from \"customer\" as c\n" // 代码行
        + "join \"product\" as p on c.\"lname\" = p.\"brand_name\""; // 代码行
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query(sql) // 代码行
        .returns("C=607\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testIntersectMismatchedVarchar() { // 测试方法:测试IntersectMismatchedVarchar功能
    final String sql = "select count(*) as c from (\n" // 代码行
        + "  select \"lname\" from \"customer\" as c\n" // 代码行
        + "  intersect\n" // 代码行
        + "  select \"brand_name\" from \"product\" as p)"; // 代码行
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query(sql) // 代码行
        .returns("C=12\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests the NOT IN operator. Problems arose in code-generation because // JavaDoc注释开始
   * the column allows nulls. */ // JavaDoc注释内容
  @Test void testNotIn() { // 测试方法:测试NotIn功能
    predicate("\"name\" not in ('a', 'b') or \"name\" is null") // 代码行
        .returns("" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n" // 代码行
            + "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500\n" // 代码行
            + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n" // 代码行
            + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n"); // 代码行
 // 空行
    // And some similar combinations... // 单行注释
    predicate("\"name\" in ('a', 'b') or \"name\" is null"); // 代码行
    predicate("\"name\" in ('a', 'b', null) or \"name\" is null"); // 代码行
    predicate("\"name\" in ('a', 'b') or \"name\" is not null"); // 代码行
    predicate("\"name\" in ('a', 'b', null) or \"name\" is not null"); // 代码行
    predicate("\"name\" not in ('a', 'b', null) or \"name\" is not null"); // 代码行
    predicate("\"name\" not in ('a', 'b', null) and \"name\" is not null"); // 代码行
  } // 代码行
 // 空行
  @Test void testNotInEmptyQuery() { // 测试方法:测试NotInEmptyQuery功能
    // RHS is empty, therefore returns all rows from emp, including the one // 单行注释
    // with deptno = NULL. // 单行注释
    final String sql = "select deptno from emp where deptno not in (\n" // 代码行
        + "select deptno from dept where deptno = -1)"; // 代码行
    withEmpDept(sql) // 代码行
//        .explainContains("EnumerableCalc(expr#0..2=[{inputs}], " // 单行注释
//            + "expr#3=[IS NOT NULL($t2)], expr#4=[true], " // 单行注释
//            + "expr#5=[IS NULL($t0)], expr#6=[null], expr#7=[false], " // 单行注释
//            + "expr#8=[CASE($t3, $t4, $t5, $t6, $t7)], expr#9=[NOT($t8)], " // 单行注释
//            + "EXPR$1=[$t0], $condition=[$t9])") // 单行注释
        .returnsUnordered("DEPTNO=null", // 代码行
            "DEPTNO=10", // 代码行
            "DEPTNO=10", // 代码行
            "DEPTNO=20", // 代码行
            "DEPTNO=30", // 代码行
            "DEPTNO=30", // 代码行
            "DEPTNO=50", // 代码行
            "DEPTNO=50", // 代码行
            "DEPTNO=60"); // 代码行
  } // 代码行
 // 空行
  @Test void testNotInQuery() { // 测试方法:测试NotInQuery功能
    // None of the rows from RHS is NULL. // 单行注释
    final String sql = "select deptno from emp where deptno not in (\n" // 代码行
        + "select deptno from dept)"; // 代码行
    withEmpDept(sql) // 代码行
        .returnsUnordered("DEPTNO=50", // 代码行
            "DEPTNO=50", // 代码行
            "DEPTNO=60"); // 代码行
  } // 代码行
 // 空行
  @Test void testNotInQueryWithNull() { // 测试方法:测试NotInQueryWithNull功能
    // There is a NULL on the RHS, and '10 not in (20, null)' yields unknown // 单行注释
    // (similarly for every other value of deptno), so no rows are returned. // 单行注释
    final String sql = "select deptno from emp where deptno not in (\n" // 代码行
        + "select deptno from emp)"; // 代码行
    withEmpDept(sql) // 代码行
        .returnsCount(0); // 代码行
  } // 代码行
 // 空行
  @Test void testTrim() { // 测试方法:测试Trim功能
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query("select trim(\"lname\") as \"lname\" " // 代码行
            + "from \"customer\" where \"lname\" = 'Nowmer'") // 代码行
        .returns("lname=Nowmer\n"); // 代码行
 // 空行
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query("select trim(leading 'N' from \"lname\") as \"lname\" " // 代码行
            + "from \"customer\" where \"lname\" = 'Nowmer'") // 代码行
        .returns("lname=owmer\n"); // 代码行
  } // 代码行
 // 空行
  private CalciteAssert.AssertQuery predicate(String foo) { // predicate方法:创建谓词测试断言
    return CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query("select * from \"hr\".\"emps\"\n" // 代码行
            + "where " + foo) // 代码行
        .runs(); // 代码行
  } // 代码行
 // 空行
  @Test void testExistsCorrelated() { // 测试方法:测试ExistsCorrelated功能
    final String sql = "select*from \"hr\".\"emps\" where exists (\n" // 代码行
        + " select 1 from \"hr\".\"depts\"\n" // 代码行
        + " where \"emps\".\"deptno\"=\"depts\".\"deptno\")"; // 代码行
    final String plan = "" // 代码行
        + "LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], commission=[$4])\n" // 代码行
        + "  LogicalFilter(condition=[EXISTS({\n" // 代码行
        + "LogicalFilter(condition=[=($cor0.deptno, $0)])\n" // 代码行
        + "  LogicalTableScan(table=[[hr, depts]])\n" // 代码行
        + "})], variablesSet=[[$cor0]])\n" // 代码行
        + "    LogicalTableScan(table=[[hr, emps]])\n"; // 代码行
    CalciteAssert.hr().query(sql).convertContains(plan) // 代码行
        .returnsUnordered( // 代码行
            "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000", // 代码行
            "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null", // 代码行
            "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250"); // 代码行
  } // 代码行
 // 空行
  @Test void testNotExistsCorrelated() { // 测试方法:测试NotExistsCorrelated功能
    final String plan = "PLAN=" // 代码行
        + "EnumerableCalc(expr#0..5=[{inputs}], expr#6=[IS NULL($t5)], proj#0..4=[{exprs}], $condition=[$t6])\n" // 代码行
        + "  EnumerableCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{1}])\n" // 代码行
        + "    EnumerableTableScan(table=[[hr, emps]])\n" // 代码行
        + "    EnumerableAggregate(group=[{0}])\n" // 代码行
        + "      EnumerableCalc(expr#0..3=[{inputs}], expr#4=[true], expr#5=[$cor0], expr#6=[$t5.deptno], expr#7=[=($t6, $t0)], i=[$t4], $condition=[$t7])\n" // 代码行
        + "        EnumerableTableScan(table=[[hr, depts]])\n"; // 代码行
    final String sql = "select * from \"hr\".\"emps\" where not exists (\n" // 代码行
        + " select 1 from \"hr\".\"depts\"\n" // 代码行
        + " where \"emps\".\"deptno\"=\"depts\".\"deptno\")"; // 代码行
    CalciteAssert.hr() // 代码行
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, false) // 代码行
        .query(sql) // 代码行
        .explainContains(plan) // 代码行
        .returnsUnordered( // 代码行
            "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500"); // 代码行
  } // 代码行
 // 空行
  /** Manual expansion of EXISTS in {@link #testNotExistsCorrelated()}. */ // JavaDoc注释开始
  @Test void testNotExistsCorrelated2() { // 测试方法:测试NotExistsCorrelated2功能
    final String sql = "select * from \"hr\".\"emps\" as e left join lateral (\n" // 代码行
        + " select distinct true as i\n" // 代码行
        + " from \"hr\".\"depts\"\n" // 代码行
        + " where e.\"deptno\"=\"depts\".\"deptno\") on true"; // 代码行
    final String explain = "" // 代码行
        + "EnumerableCalc(expr#0..6=[{inputs}], proj#0..4=[{exprs}], I=[$t6])\n" // 代码行
        + "  EnumerableMergeJoin(condition=[=($1, $5)], joinType=[left])\n" // 代码行
        + "    EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 代码行
        + "      EnumerableTableScan(table=[[hr, emps]])\n" // 代码行
        + "    EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 代码行
        + "      EnumerableCalc(expr#0=[{inputs}], expr#1=[true], proj#0..1=[{exprs}])\n" // 代码行
        + "        EnumerableAggregate(group=[{0}])\n" // 代码行
        + "          EnumerableTableScan(table=[[hr, depts]])"; // 代码行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .explainContains(explain) // 代码行
        .returnsUnordered( // 代码行
            "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000; I=true", // 代码行
            "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250; I=true", // 代码行
            "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null; I=true", // 代码行
            "empid=200; deptno=20; name=Eric; salary=8000.0; commission=500; I=null"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-313">[CALCITE-313] // JavaDoc注释内容
   * Query decorrelation fails</a>. */ // JavaDoc注释内容
  @Test void testJoinInCorrelatedSubQuery() { // 测试方法:测试JoinInCorrelatedSubQuery功能
    CalciteAssert.hr() // 代码行
        .query("select *\n" // 代码行
            + "from \"hr\".\"depts\" as d\n" // 代码行
            + "where \"deptno\" in (\n" // 代码行
            + "  select d2.\"deptno\"\n" // 代码行
            + "  from \"hr\".\"depts\" as d2\n" // 代码行
            + "  join \"hr\".\"emps\" as e2 using (\"deptno\")\n" // 代码行
            + "where d.\"deptno\" = d2.\"deptno\")") // 代码行
        .convertMatches(relNode -> { // 代码行
          String s = RelOptUtil.toString(relNode); // 代码行
          assertThat(s, not(containsString("Correlate"))); // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests a correlated scalar sub-query in the SELECT clause. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>Note that there should be an extra row "empid=200; deptno=20; // JavaDoc注释内容
   * DNAME=null" but left join doesn't work. */ // JavaDoc注释内容
  @Test void testScalarSubQuery() { // 测试方法:测试ScalarSubQuery功能
    CalciteAssert.hr() // 代码行
        .query("select \"empid\", \"deptno\",\n" // 代码行
            + " (select \"name\" from \"hr\".\"depts\"\n" // 代码行
            + "  where \"deptno\" = e.\"deptno\") as dname\n" // 代码行
            + "from \"hr\".\"emps\" as e") // 代码行
        .returnsUnordered("empid=100; deptno=10; DNAME=Sales", // 代码行
            "empid=110; deptno=10; DNAME=Sales", // 代码行
            "empid=150; deptno=10; DNAME=Sales", // 代码行
            "empid=200; deptno=20; DNAME=null"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-559">[CALCITE-559] // JavaDoc注释内容
   * Correlated scalar sub-query in WHERE gives error</a>. */ // JavaDoc注释内容
  @Test void testJoinCorrelatedScalarSubQuery() { // 测试方法:测试JoinCorrelatedScalarSubQuery功能
    final String sql = "select e.employee_id, d.department_id " // 代码行
        + " from employee e, department d " // 代码行
        + " where e.department_id = d.department_id " // 代码行
        + " and e.salary > (select avg(e2.salary) " // 代码行
        + "                 from employee e2 " // 代码行
        + "                 where e2.store_id = e.store_id)"; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .with(Lex.JAVA) // 代码行
        .query(sql) // 代码行
        .returnsCount(599); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-685">[CALCITE-685] // JavaDoc注释内容
   * Correlated scalar sub-query in SELECT clause throws</a>. */ // JavaDoc注释内容
  @Disabled("[CALCITE-685]") // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testCorrelatedScalarSubQuery() { // 测试方法:测试CorrelatedScalarSubQuery功能
    final String sql = "select e.department_id, sum(e.employee_id),\n" // 代码行
        + "       ( select sum(e2.employee_id)\n" // 代码行
        + "         from  employee e2\n" // 代码行
        + "         where e.department_id = e2.department_id\n" // 代码行
        + "       )\n" // 代码行
        + "from employee e\n" // 代码行
        + "group by e.department_id\n"; // 代码行
    final String explain = "EnumerableNestedLoopJoin(condition=[true], joinType=[left])\n" // 代码行
        + "  EnumerableAggregate(group=[{7}], EXPR$1=[$SUM0($0)])\n" // 代码行
        + "    EnumerableTableScan(table=[[foodmart2, employee]])\n" // 代码行
        + "  EnumerableAggregate(group=[{}], EXPR$0=[SUM($0)])\n" // 代码行
        + "    EnumerableCalc(expr#0..16=[{inputs}], expr#17=[$cor0], expr#18=[$t17.department_id], expr#19=[=($t18, $t7)], employee_id=[$t0], department_id=[$t7], $condition=[$t19])\n" // 代码行
        + "      EnumerableTableScan(table=[[foodmart2, employee]])\n"; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.FOODMART_CLONE) // 代码行
        .with(Lex.JAVA) // 代码行
        .query(sql) // 代码行
        .explainContains(explain) // 代码行
        .returnsCount(0); // 代码行
  } // 代码行
 // 空行
  @Test void testLeftJoin() { // 测试方法:测试LeftJoin功能
    CalciteAssert.hr() // 代码行
        .query("select e.\"deptno\", d.\"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\" as e\n" // 代码行
            + "  left join \"hr\".\"depts\" as d using (\"deptno\")") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=20; deptno=null"); // 代码行
  } // 代码行
 // 空行
  @Test void testFullJoin() { // 测试方法:测试FullJoin功能
    CalciteAssert.hr() // 代码行
        .query("select e.\"deptno\", d.\"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\" as e\n" // 代码行
            + "  full join \"hr\".\"depts\" as d using (\"deptno\")") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=20; deptno=null", // 代码行
            "deptno=null; deptno=30", // 代码行
            "deptno=null; deptno=40"); // 代码行
  } // 代码行
 // 空行
  @Test void testRightJoin() { // 测试方法:测试RightJoin功能
    CalciteAssert.hr() // 代码行
        .query("select e.\"deptno\", d.\"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\" as e\n" // 代码行
            + "  right join \"hr\".\"depts\" as d using (\"deptno\")") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=null; deptno=30", // 代码行
            "deptno=null; deptno=40"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2464">[CALCITE-2464] // JavaDoc注释内容
   * Allow to set nullability for columns of structured types</a>. */ // JavaDoc注释内容
  @Test void testLeftJoinWhereStructIsNotNull() { // 测试方法:测试LeftJoinWhereStructIsNotNull功能
    CalciteAssert.hr() // 代码行
        .query("select e.\"deptno\", d.\"deptno\"\n" // 代码行
            + "from \"hr\".\"emps\" as e\n" // 代码行
            + "  left join \"hr\".\"depts\" as d using (\"deptno\")" // 代码行
            + "where d.\"location\" is not null") // 代码行
        .returnsUnordered( // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10", // 代码行
            "deptno=10; deptno=10"); // 代码行
  } // 代码行
 // 空行
  /** Various queries against EMP and DEPT, in particular involving composite // JavaDoc注释开始
   * join conditions in various flavors of outer join. Results are verified // JavaDoc注释内容
   * against MySQL (except full join, which MySQL does not support). */ // JavaDoc注释内容
  @Test void testVariousOuter() { // 测试方法:测试VariousOuter功能
    final String sql = // 代码行
        "select * from emp join dept on emp.deptno = dept.deptno"; // 代码行
    withEmpDept(sql).returnsUnordered( // 代码行
        "ENAME=Alice; DEPTNO=30; GENDER=F; DEPTNO0=30; DNAME=Engineering", // 代码行
        "ENAME=Bob  ; DEPTNO=10; GENDER=M; DEPTNO0=10; DNAME=Sales      ", // 代码行
        "ENAME=Eric ; DEPTNO=20; GENDER=M; DEPTNO0=20; DNAME=Marketing  ", // 代码行
        "ENAME=Jane ; DEPTNO=10; GENDER=F; DEPTNO0=10; DNAME=Sales      ", // 代码行
        "ENAME=Susan; DEPTNO=30; GENDER=F; DEPTNO0=30; DNAME=Engineering"); // 代码行
  } // 代码行
 // 空行
  private CalciteAssert.AssertQuery withEmpDept(String sql) { // withEmpDept方法:创建带EMP和DEPT表的测试断言
    // Append a 'WITH' clause that supplies EMP and DEPT tables like this: // 单行注释
    // // 单行注释
    // drop table emp; // 单行注释
    // drop table dept; // 单行注释
    // create table emp(ename varchar(10), deptno int, gender varchar(1)); // 单行注释
    // insert into emp values ('Jane', 10, 'F'); // 单行注释
    // insert into emp values ('Bob', 10, 'M'); // 单行注释
    // insert into emp values ('Eric', 20, 'M'); // 单行注释
    // insert into emp values ('Susan', 30, 'F'); // 单行注释
    // insert into emp values ('Alice', 30, 'F'); // 单行注释
    // insert into emp values ('Adam', 50, 'M'); // 单行注释
    // insert into emp values ('Eve', 50, 'F'); // 单行注释
    // insert into emp values ('Grace', 60, 'F'); // 单行注释
    // insert into emp values ('Wilma', null, 'F'); // 单行注释
    // create table dept (deptno int, dname varchar(12)); // 单行注释
    // insert into dept values (10, 'Sales'); // 单行注释
    // insert into dept values (20, 'Marketing'); // 单行注释
    // insert into dept values (30, 'Engineering'); // 单行注释
    // insert into dept values (40, 'Empty'); // 单行注释
    return CalciteAssert.that() // 代码行
        .query("with\n" // 代码行
            + "  emp(ename, deptno, gender) as (values\n" // 代码行
            + "    ('Jane', 10, 'F'),\n" // 代码行
            + "    ('Bob', 10, 'M'),\n" // 代码行
            + "    ('Eric', 20, 'M'),\n" // 代码行
            + "    ('Susan', 30, 'F'),\n" // 代码行
            + "    ('Alice', 30, 'F'),\n" // 代码行
            + "    ('Adam', 50, 'M'),\n" // 代码行
            + "    ('Eve', 50, 'F'),\n" // 代码行
            + "    ('Grace', 60, 'F'),\n" // 代码行
            + "    ('Wilma', cast(null as integer), 'F')),\n" // 代码行
            + "  dept(deptno, dname) as (values\n" // 代码行
            + "    (10, 'Sales'),\n" // 代码行
            + "    (20, 'Marketing'),\n" // 代码行
            + "    (30, 'Engineering'),\n" // 代码行
            + "    (40, 'Empty'))\n" // 代码行
            + sql); // 代码行
  } // 代码行
 // 空行
  @Test void testScalarSubQueryUncorrelated() { // 测试方法:测试ScalarSubQueryUncorrelated功能
    CalciteAssert.hr() // 代码行
        .query("select \"empid\", \"deptno\",\n" // 代码行
            + " (select \"name\" from \"hr\".\"depts\"\n" // 代码行
            + "  where \"deptno\" = 30) as dname\n" // 代码行
            + "from \"hr\".\"emps\" as e") // 代码行
        .returnsUnordered("empid=100; deptno=10; DNAME=Marketing", // 代码行
            "empid=110; deptno=10; DNAME=Marketing", // 代码行
            "empid=150; deptno=10; DNAME=Marketing", // 代码行
            "empid=200; deptno=20; DNAME=Marketing"); // 代码行
  } // 代码行
 // 空行
  @Test void testScalarSubQueryInCase() { // 测试方法:测试ScalarSubQueryInCase功能
    CalciteAssert.hr() // 代码行
        .query("select e.\"name\",\n" // 代码行
            + " (CASE e.\"deptno\"\n" // 代码行
            + "  WHEN (Select \"deptno\" from \"hr\".\"depts\" d\n" // 代码行
            + "        where d.\"deptno\" = e.\"deptno\")\n" // 代码行
            + "  THEN (Select d.\"name\" from \"hr\".\"depts\" d\n" // 代码行
            + "        where d.\"deptno\" = e.\"deptno\")\n" // 代码行
            + "  ELSE 'DepartmentNotFound'  END) AS DEPTNAME\n" // 代码行
            + "from \"hr\".\"emps\" e") // 代码行
        .returnsUnordered("name=Bill; DEPTNAME=Sales", // 代码行
            "name=Eric; DEPTNAME=DepartmentNotFound", // 代码行
            "name=Sebastian; DEPTNAME=Sales", // 代码行
            "name=Theodore; DEPTNAME=Sales"); // 代码行
  } // 代码行
 // 空行
  @Test void testScalarSubQueryInCase2() { // 测试方法:测试ScalarSubQueryInCase2功能
    CalciteAssert.hr() // 代码行
        .query("select e.\"name\",\n" // 代码行
            + " (CASE WHEN e.\"deptno\" = (\n" // 代码行
            + "    Select \"deptno\" from \"hr\".\"depts\" d\n" // 代码行
            + "    where d.\"name\" = 'Sales')\n" // 代码行
            + "  THEN 'Sales'\n" // 代码行
            + "  ELSE 'Not Matched'  END) AS DEPTNAME\n" // 代码行
            + "from \"hr\".\"emps\" e") // 代码行
        .returnsUnordered("name=Bill; DEPTNAME=Sales      ", // 代码行
            "name=Eric; DEPTNAME=Not Matched", // 代码行
            "name=Sebastian; DEPTNAME=Sales      ", // 代码行
            "name=Theodore; DEPTNAME=Sales      "); // 代码行
  } // 代码行
 // 空行
  /** Tests the TABLES table in the information schema. */ // JavaDoc注释开始
  @Test void testMetaTables() { // 测试方法:测试MetaTables功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR_PLUS_METADATA) // 代码行
        .query("select * from \"metadata\".TABLES") // 代码行
        .returns( // 代码行
            CalciteAssert.checkResultContains( // 代码行
                "tableSchem=metadata; tableName=COLUMNS; tableType=SYSTEM TABLE; ")); // 代码行
 // 空行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR_PLUS_METADATA) // 代码行
        .query("select count(distinct \"tableSchem\") as c\n" // 代码行
            + "from \"metadata\".TABLES") // 代码行
        .returns("C=3\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests that {@link java.sql.Statement#setMaxRows(int)} is honored. */ // JavaDoc注释开始
  @Test void testSetMaxRows() throws Exception { // 测试方法:测试SetMaxRows功能
    CalciteAssert.hr() // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            final Statement statement = connection.createStatement(); // 代码行
            try { // 代码行
              statement.setMaxRows(-1); // 代码行
              fail("expected error"); // 代码行
            } catch (SQLException e) { // 代码行
              assertThat("illegal maxRows value: -1", is(e.getMessage())); // 代码行
            } // 代码行
            statement.setMaxRows(2); // 代码行
            assertThat(statement.getMaxRows(), is(2)); // 代码行
            final ResultSet resultSet = // 代码行
                statement.executeQuery("select * from \"hr\".\"emps\""); // 代码行
            assertTrue(resultSet.next()); // 代码行
            assertTrue(resultSet.next()); // 代码行
            assertFalse(resultSet.next()); // 代码行
            resultSet.close(); // 代码行
            statement.close(); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests a {@link PreparedStatement} with parameters. */ // JavaDoc注释开始
  @Test void testPreparedStatement() throws Exception { // 测试方法:测试PreparedStatement功能
    CalciteAssert.hr() // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            final PreparedStatement preparedStatement = // 代码行
                connection.prepareStatement("select \"deptno\", \"name\" " // 代码行
                    + "from \"hr\".\"emps\"\n" // 代码行
                    + "where \"deptno\" < ? and \"name\" like ?"); // 代码行
 // 空行
            // execute with vars unbound - gives error // 单行注释
            ResultSet resultSet; // 代码行
            try { // 代码行
              resultSet = preparedStatement.executeQuery(); // 代码行
              fail("expected error, got " + resultSet); // 代码行
            } catch (SQLException e) { // 代码行
              assertThat(e.getMessage(), // 代码行
                  containsString( // 代码行
                      "exception while executing query: unbound parameter")); // 代码行
            } // 代码行
 // 空行
            // execute with both vars null - no results // 单行注释
            preparedStatement.setNull(1, Types.INTEGER); // 代码行
            preparedStatement.setNull(2, Types.VARCHAR); // 代码行
            resultSet = preparedStatement.executeQuery(); // 代码行
            assertFalse(resultSet.next()); // 代码行
 // 空行
            // execute with ?0=15, ?1='%' - 3 rows // 单行注释
            preparedStatement.setInt(1, 15); // 代码行
            preparedStatement.setString(2, "%"); // 代码行
            resultSet = preparedStatement.executeQuery(); // 代码行
            assertThat(CalciteAssert.toString(resultSet), // 代码行
                is("deptno=10; name=Bill\n" // 代码行
                    + "deptno=10; name=Sebastian\n" // 代码行
                    + "deptno=10; name=Theodore\n")); // 代码行
 // 空行
            // execute with ?0=15 (from last bind), ?1='%r%' - 1 row // 单行注释
            preparedStatement.setString(2, "%r%"); // 代码行
            resultSet = preparedStatement.executeQuery(); // 代码行
            assertThat(CalciteAssert.toString(resultSet), // 代码行
                is("deptno=10; name=Theodore\n")); // 代码行
 // 空行
            // Now BETWEEN, with 3 arguments, 2 of which are parameters // 单行注释
            final String sql2 = "select \"deptno\", \"name\" " // 代码行
                + "from \"hr\".\"emps\"\n" // 代码行
                + "where \"deptno\" between symmetric ? and ?\n" // 代码行
                + "order by 2"; // 代码行
            final PreparedStatement preparedStatement2 = // 代码行
                connection.prepareStatement(sql2); // 代码行
            preparedStatement2.setInt(1, 15); // 代码行
            preparedStatement2.setInt(2, 5); // 代码行
            resultSet = preparedStatement2.executeQuery(); // 代码行
            assertThat(CalciteAssert.toString(resultSet), // 代码行
                is("deptno=10; name=Bill\n" // 代码行
                    + "deptno=10; name=Sebastian\n" // 代码行
                    + "deptno=10; name=Theodore\n")); // 代码行
 // 空行
            resultSet.close(); // 代码行
            preparedStatement2.close(); // 代码行
            preparedStatement.close(); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2061">[CALCITE-2061] // JavaDoc注释内容
   * Dynamic parameters in offset/fetch</a>. */ // JavaDoc注释内容
  @Test void testPreparedOffsetFetch() throws Exception { // 测试方法:测试PreparedOffsetFetch功能
    checkPreparedOffsetFetch(0, 0, Matchers.returnsUnordered()); // 代码行
    checkPreparedOffsetFetch(100, 4, Matchers.returnsUnordered()); // 代码行
    checkPreparedOffsetFetch(3, 4, // 代码行
        Matchers.returnsUnordered("name=Eric")); // 代码行
  } // 代码行
 // 空行
  private void checkPreparedOffsetFetch(final int offset, final int fetch, // checkPreparedOffsetFetch方法:检查带参数的OFFSET FETCH
      final Matcher<? super ResultSet> matcher) throws Exception { // 代码行
    CalciteAssert.hr() // 代码行
        .doWithConnection(connection -> { // 代码行
          final String sql = "select \"name\"\n" // 代码行
              + "from \"hr\".\"emps\"\n" // 代码行
              + "order by \"empid\" offset ? fetch next ? rows only"; // 代码行
          try (PreparedStatement p = // 代码行
                   connection.prepareStatement(sql)) { // 代码行
            final ParameterMetaData pmd = p.getParameterMetaData(); // 代码行
            assertThat(pmd.getParameterCount(), is(2)); // 代码行
            assertThat(pmd.getParameterType(1), is(Types.INTEGER)); // 代码行
            assertThat(pmd.getParameterType(2), is(Types.INTEGER)); // 代码行
            p.setInt(1, offset); // 代码行
            p.setInt(2, fetch); // 代码行
            try (ResultSet r = p.executeQuery()) { // 代码行
              assertThat(r, matcher); // 代码行
            } // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case for <a href="https://issues.apache.org/jira/browse/CALCITE-5048">[CALCITE-5048] // JavaDoc注释内容
   * Query with parameterized LIMIT and correlated sub-query throws AssertionError "not a // JavaDoc注释内容
   * literal"</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Disabled("[CALCITE-5229] JdbcTest#testDynamicParameterInLimitOffset" // 禁用测试注解:标记当前测试方法为禁用状态
      + " throws IllegalArgumentException") // 代码行
  @Test void testDynamicParameterInLimitOffset() { // 测试方法:测试DynamicParameterInLimitOffset功能
    CalciteAssert.hr() // 代码行
        .query("SELECT * FROM \"hr\".\"emps\" AS a " // 代码行
            + "WHERE \"deptno\" = " // 代码行
            + "(SELECT MAX(\"deptno\") " // 代码行
            + "FROM \"hr\".\"emps\" AS b " // 代码行
            + "WHERE a.\"empid\" = b.\"empid\"" // 代码行
            + ") ORDER BY \"salary\" LIMIT ? OFFSET ?") // 代码行
        .explainContains("EnumerableLimit(offset=[?1], fetch=[?0])") // 代码行
        .consumesPreparedStatement(p -> { // 代码行
          p.setInt(1, 2); // 代码行
          p.setInt(2, 1); // 代码行
        }) // 代码行
        .returns("empid=200; deptno=20; name=Eric; salary=8000.0; commission=500\n" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model (a single schema based on // JavaDoc注释开始
   * a JDBC database). */ // JavaDoc注释内容
  @Test void testModel() { // 测试方法:测试Model功能
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .query("select count(*) as c from \"foodmart\".\"time_by_day\"") // 代码行
        .returns("C=730\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a JSON model with a comment. Not standard JSON, but harmless to // JavaDoc注释开始
   * allow Jackson's comments extension. // JavaDoc注释内容
   * // JavaDoc注释内容
   * <p>Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-160">[CALCITE-160] // JavaDoc注释内容
   * Allow comments in schema definitions</a>. */ // JavaDoc注释内容
  @Test void testModelWithComment() { // 测试方法:测试ModelWithComment功能
    final String model = // 代码行
        FoodmartSchema.FOODMART_MODEL.replace("schemas:", "/* comment */ schemas:"); // 代码行
    assertThat(model, not(is(FoodmartSchema.FOODMART_MODEL))); // 代码行
    CalciteAssert.model(model) // 代码行
        .query("select count(*) as c from \"foodmart\".\"time_by_day\"") // 代码行
        .returns("C=730\n"); // 代码行
  } // 代码行
 // 空行
  /** Defines a materialized view and tests that the query is rewritten to use // JavaDoc注释开始
   * it, and that the query produces the same result with and without it. There // JavaDoc注释内容
   * are more comprehensive tests in {@link MaterializationTest}. */ // JavaDoc注释内容
  @Disabled("until JdbcSchema can define materialized views") // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void testModelWithMaterializedView() { // 测试方法:测试ModelWithMaterializedView功能
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 代码行
        .enable(false) // 代码行
        .query( // 代码行
            "select count(*) as c from \"foodmart\".\"sales_fact_1997\" join \"foodmart\".\"time_by_day\" using (\"time_id\")") // 代码行
        .returns("C=86837\n"); // 代码行
    CalciteAssert.that().withMaterializations( // 代码行
        FoodmartSchema.FOODMART_MODEL, // 代码行
        "agg_c_10_sales_fact_1997", // 代码行
            "select t.`month_of_year`, t.`quarter`, t.`the_year`, sum(s.`store_sales`) as `store_sales`, sum(s.`store_cost`), sum(s.`unit_sales`), count(distinct s.`customer_id`), count(*) as `fact_count` from `time_by_day` as t join `sales_fact_1997` as s using (`time_id`) group by t.`month_of_year`, t.`quarter`, t.`the_year`") // 代码行
        .query( // 代码行
            "select t.\"month_of_year\", t.\"quarter\", t.\"the_year\", sum(s.\"store_sales\") as \"store_sales\", sum(s.\"store_cost\"), sum(s.\"unit_sales\"), count(distinct s.\"customer_id\"), count(*) as \"fact_count\" from \"time_by_day\" as t join \"sales_fact_1997\" as s using (\"time_id\") group by t.\"month_of_year\", t.\"quarter\", t.\"the_year\"") // 代码行
        .explainContains( // 代码行
            "JdbcTableScan(table=[[foodmart, agg_c_10_sales_fact_1997]])") // 代码行
        .enableMaterializations(false) // 代码行
        .explainContains("JdbcTableScan(table=[[foodmart, sales_fact_1997]])") // 代码行
        .sameResultWithMaterializationsDisabled(); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains custom // JavaDoc注释开始
   * tables. */ // JavaDoc注释内容
  @Test void testModelCustomTable() { // 测试方法:测试ModelCustomTable功能
    CalciteAssert.model("{\n" // 代码行
        + "  version: '1.0',\n" // 代码行
        + "   schemas: [\n" // 代码行
        + "     {\n" // 代码行
        + "       name: 'adhoc',\n" // 代码行
        + "       tables: [\n" // 代码行
        + "         {\n" // 代码行
        + "           name: 'EMPLOYEES',\n" // 代码行
        + "           type: 'custom',\n" // 代码行
        + "           factory: '" // 代码行
        + EmpDeptTableFactory.class.getName() + "',\n" // 代码行
        + "           operand: {'foo': 1, 'bar': [345, 357] }\n" // 代码行
        + "         }\n" // 代码行
        + "       ]\n" // 代码行
        + "     }\n" // 代码行
        + "   ]\n" // 代码行
        + "}") // 代码行
        .query("select * from \"adhoc\".EMPLOYEES where \"deptno\" = 10") // 代码行
        .returns("" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n" // 代码行
            + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n" // 代码行
            + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains custom // JavaDoc注释开始
   * tables. */ // JavaDoc注释内容
  @Test void testModelCustomTable2() { // 测试方法:测试ModelCustomTable2功能
    testRangeTable("object"); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains custom // JavaDoc注释开始
   * tables. */ // JavaDoc注释内容
  @Test void testModelCustomTableArrayRowSingleColumn() { // 测试方法:测试ModelCustomTableArrayRowSingleColumn功能
    testRangeTable("array"); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains custom // JavaDoc注释开始
   * tables. */ // JavaDoc注释内容
  @Test void testModelCustomTableIntegerRowSingleColumn() { // 测试方法:测试ModelCustomTableIntegerRowSingleColumn功能
    testRangeTable("integer"); // 代码行
  } // 代码行
 // 空行
  private void testRangeTable(String elementType) { // 代码行
    CalciteAssert.model("{\n" // 代码行
        + "  version: '1.0',\n" // 代码行
        + "   schemas: [\n" // 代码行
        + "     {\n" // 代码行
        + "       name: 'MATH',\n" // 代码行
        + "       tables: [\n" // 代码行
        + "         {\n" // 代码行
        + "           name: 'INTEGERS',\n" // 代码行
        + "           type: 'custom',\n" // 代码行
        + "           factory: '" // 代码行
        + RangeTable.Factory.class.getName() + "',\n" // 代码行
        + "           operand: {'column': 'N', 'start': 3, 'end': 7, " // 代码行
        + " 'elementType': '" + elementType + "'}\n" // 代码行
        + "         }\n" // 代码行
        + "       ]\n" // 代码行
        + "     }\n" // 代码行
        + "   ]\n" // 代码行
        + "}") // 代码行
        .query("select * from math.integers") // 代码行
        .returns("N=3\n" // 代码行
            + "N=4\n" // 代码行
            + "N=5\n" // 代码行
            + "N=6\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains a custom // JavaDoc注释开始
   * schema. */ // JavaDoc注释内容
  @Test void testModelCustomSchema() throws Exception { // 测试方法:测试ModelCustomSchema功能
    final CalciteAssert.AssertThat that = // 代码行
        CalciteAssert.model("{\n" // 代码行
            + "  version: '1.0',\n" // 代码行
            + "  defaultSchema: 'adhoc',\n" // 代码行
            + "  schemas: [\n" // 代码行
            + "    {\n" // 代码行
            + "      name: 'empty'\n" // 代码行
            + "    },\n" // 代码行
            + "    {\n" // 代码行
            + "      name: 'adhoc',\n" // 代码行
            + "      type: 'custom',\n" // 代码行
            + "      factory: '" // 代码行
            + MySchemaFactory.class.getName() // 代码行
            + "',\n" // 代码行
            + "      operand: {'tableName': 'ELVIS'}\n" // 代码行
            + "    }\n" // 代码行
            + "  ]\n" // 代码行
            + "}"); // 代码行
    // check that the specified 'defaultSchema' was used // 单行注释
    that.doWithConnection(connection -> { // 代码行
      try { // 代码行
        assertThat(connection.getSchema(), is("adhoc")); // 代码行
      } catch (SQLException e) { // 代码行
        throw TestUtil.rethrow(e); // 代码行
      } // 代码行
    }); // 代码行
    that.query("select * from \"adhoc\".ELVIS where \"deptno\" = 10") // 代码行
        .returns("" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n" // 代码行
            + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n" // 代码行
            + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n"); // 代码行
    that.query("select * from \"adhoc\".EMPLOYEES") // 代码行
        .throws_("Object 'EMPLOYEES' not found within 'adhoc'"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1360">[CALCITE-1360] // JavaDoc注释内容
   * Custom schema in file in current directory</a>. */ // JavaDoc注释内容
  @Test void testCustomSchemaInFileInPwd() throws SQLException { // 测试方法:测试CustomSchemaInFileInPwd功能
    checkCustomSchemaInFileInPwd("custom-schema-model.json"); // 代码行
    switch (File.pathSeparatorChar) { // 代码行
    case '/': // 代码行
      // Skip this test on Windows; the mapping from file names to URLs is too // 单行注释
      // weird. // 单行注释
      checkCustomSchemaInFileInPwd("." + File.pathSeparatorChar // 代码行
          + "custom-schema-model2.json"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  private void checkCustomSchemaInFileInPwd(String fileName) // checkCustomSchemaInFileInPwd方法:检查当前目录中的自定义Schema
      throws SQLException { // 代码行
    final File file = new File(fileName); // 代码行
    try (PrintWriter pw = Util.printWriter(file)) { // 代码行
      file.deleteOnExit(); // 代码行
      pw.println("{\n" // 代码行
          + "  version: '1.0',\n" // 代码行
          + "  defaultSchema: 'adhoc',\n" // 代码行
          + "  schemas: [\n" // 代码行
          + "    {\n" // 代码行
          + "      name: 'empty'\n" // 代码行
          + "    },\n" // 代码行
          + "    {\n" // 代码行
          + "      name: 'adhoc',\n" // 代码行
          + "      type: 'custom',\n" // 代码行
          + "      factory: '" // 代码行
          + MySchemaFactory.class.getName() // 代码行
          + "',\n" // 代码行
          + "      operand: {'tableName': 'ELVIS'}\n" // 代码行
          + "    }\n" // 代码行
          + "  ]\n" // 代码行
          + "}"); // 代码行
      pw.flush(); // 代码行
      final String url = "jdbc:calcite:model=" + file; // 代码行
      try (Connection c = DriverManager.getConnection(url); // 代码行
           Statement s = c.createStatement(); // 代码行
           ResultSet r = s.executeQuery("values 1")) { // 代码行
        assertThat(r.next(), is(true)); // 代码行
      } // 代码行
      //noinspection ResultOfMethodCallIgnored // 单行注释
      file.delete(); // 代码行
    } catch (IOException e) { // 代码行
      // current directory is not writable; an environment issue, not // 单行注释
      // necessarily a bug // 单行注释
    } // 代码行
  } // 代码行
 // 空行
  /** Connects to a custom schema without writing a model. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1259">[CALCITE-1259] // JavaDoc注释内容
   * Allow connecting to a single schema without writing a model</a>. */ // JavaDoc注释内容
  @Test void testCustomSchemaDirectConnection() throws Exception { // 测试方法:测试CustomSchemaDirectConnection功能
    final String url = "jdbc:calcite:" // 代码行
        + "schemaFactory=" + MySchemaFactory.class.getName() // 代码行
        + "; schema.tableName=ELVIS"; // 代码行
    checkCustomSchema(url, "adhoc"); // implicit schema is called 'adhoc' // 代码行
    checkCustomSchema(url + "; schema=xyz", "xyz"); // explicit schema // 代码行
  } // 代码行
 // 空行
  private void checkCustomSchema(String url, String schemaName) throws SQLException { // checkCustomSchema方法:检查自定义Schema
    try (Connection connection = DriverManager.getConnection(url)) { // 代码行
      assertThat(connection.getSchema(), is(schemaName)); // 代码行
      final String sql = "select * from \"" + schemaName + "\".ELVIS where \"deptno\" = 10"; // 代码行
      final String sql2 = "select * from ELVIS where \"deptno\" = 10"; // 代码行
      String expected = "" // 代码行
          + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n" // 代码行
          + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n" // 代码行
          + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n"; // 代码行
      try (Statement statement = connection.createStatement()) { // 代码行
        try (ResultSet resultSet = statement.executeQuery(sql)) { // 代码行
          assertThat(CalciteAssert.toString(resultSet), is(expected)); // 代码行
        } // 代码行
        try (ResultSet resultSet = statement.executeQuery(sql2)) { // 代码行
          assertThat(CalciteAssert.toString(resultSet), is(expected)); // 代码行
        } // 代码行
      } // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Connects to a JDBC schema without writing a model. */ // JavaDoc注释开始
  @Test void testJdbcSchemaDirectConnection() throws Exception { // 测试方法:测试JdbcSchemaDirectConnection功能
    checkJdbcSchemaDirectConnection( // 代码行
        "schemaFactory=org.apache.calcite.adapter.jdbc.JdbcSchema$Factory"); // 代码行
    checkJdbcSchemaDirectConnection("schemaType=JDBC"); // 代码行
  } // 代码行
 // 空行
  private void checkJdbcSchemaDirectConnection(String s) throws SQLException { // 代码行
    final StringBuilder b = new StringBuilder("jdbc:calcite:"); // 代码行
    b.append(s); // 代码行
    pv(b, "schema.jdbcUser", SCOTT.username); // 代码行
    pv(b, "schema.jdbcPassword", SCOTT.password); // 代码行
    pv(b, "schema.jdbcUrl", SCOTT.url); // 代码行
    pv(b, "schema.jdbcCatalog", SCOTT.catalog); // 代码行
    pv(b, "schema.jdbcDriver", SCOTT.driver); // 代码行
    pv(b, "schema.jdbcSchema", SCOTT.schema); // 代码行
    final String url =  b.toString(); // 代码行
    Connection connection = DriverManager.getConnection(url); // 代码行
    assertThat(connection.getSchema(), is("adhoc")); // 代码行
    String expected = "C=14\n"; // 代码行
    final String sql = "select count(*) as c from emp"; // 代码行
    try (Statement statement = connection.createStatement(); // 代码行
         ResultSet resultSet = statement.executeQuery(sql)) { // 代码行
      assertThat(CalciteAssert.toString(resultSet), is(expected)); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  private void pv(StringBuilder b, String p, @Nullable String v) { // pv方法:构建属性值字符串
    if (v != null) { // 代码行
      b.append("; ").append(p).append("=").append(v); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Connects to a map schema without writing a model. */ // JavaDoc注释开始
  @Test void testMapSchemaDirectConnection() throws Exception { // 测试方法:测试MapSchemaDirectConnection功能
    checkMapSchemaDirectConnection("schemaType=MAP"); // 代码行
    checkMapSchemaDirectConnection( // 代码行
        "schemaFactory=org.apache.calcite.schema.impl.AbstractSchema$Factory"); // 代码行
  } // 代码行
 // 空行
  private void checkMapSchemaDirectConnection(String s) throws SQLException { // 代码行
    final String url = "jdbc:calcite:" + s; // 代码行
    Connection connection = DriverManager.getConnection(url); // 代码行
    assertThat(connection.getSchema(), is("adhoc")); // 代码行
    String expected = "EXPR$0=1\n"; // 代码行
    final String sql = "values 1"; // 代码行
    try (Statement statement = connection.createStatement(); // 代码行
         ResultSet resultSet = statement.executeQuery(sql)) { // 代码行
      assertThat(CalciteAssert.toString(resultSet), is(expected)); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests that an immutable schema in a model cannot contain a view. */ // JavaDoc注释开始
  @Test void testModelImmutableSchemaCannotContainView() { // 测试方法:测试ModelImmutableSchemaCannotContainView功能
    CalciteAssert.model("{\n" // 代码行
        + "  version: '1.0',\n" // 代码行
        + "  defaultSchema: 'adhoc',\n" // 代码行
        + "  schemas: [\n" // 代码行
        + "    {\n" // 代码行
        + "      name: 'empty'\n" // 代码行
        + "    },\n" // 代码行
        + "    {\n" // 代码行
        + "      name: 'adhoc',\n" // 代码行
        + "      type: 'custom',\n" // 代码行
        + "      tables: [\n" // 代码行
        + "        {\n" // 代码行
        + "          name: 'v',\n" // 代码行
        + "          type: 'view',\n" // 代码行
        + "          sql: 'values (1)'\n" // 代码行
        + "        }\n" // 代码行
        + "      ],\n" // 代码行
        + "      factory: '" // 代码行
        + MySchemaFactory.class.getName() // 代码行
        + "',\n" // 代码行
        + "      operand: {\n" // 代码行
        + "           'tableName': 'ELVIS',\n" // 代码行
        + "           'mutable': false\n" // 代码行
        + "      }\n" // 代码行
        + "    }\n" // 代码行
        + "  ]\n" // 代码行
        + "}") // 代码行
        .connectThrows( // 代码行
            "Cannot define view; parent schema 'adhoc' is not mutable"); // 代码行
  } // 代码行
 // 空行
  private CalciteAssert.AssertThat modelWithView(String view, // 代码行
      @Nullable Boolean modifiable) { // 注解
    final Class<EmpDeptTableFactory> clazz = EmpDeptTableFactory.class; // 代码行
    return CalciteAssert.model("{\n" // 代码行
        + "  version: '1.0',\n" // 代码行
        + "   schemas: [\n" // 代码行
        + "     {\n" // 代码行
        + "       name: 'adhoc',\n" // 代码行
        + "       tables: [\n" // 代码行
        + "         {\n" // 代码行
        + "           name: 'EMPLOYEES',\n" // 代码行
        + "           type: 'custom',\n" // 代码行
        + "           factory: '" + clazz.getName() + "',\n" // 代码行
        + "           operand: {'foo': true, 'bar': 345}\n" // 代码行
        + "         },\n" // 代码行
        + "         {\n" // 代码行
        + "           name: 'MUTABLE_EMPLOYEES',\n" // 代码行
        + "           type: 'custom',\n" // 代码行
        + "           factory: '" + clazz.getName() + "',\n" // 代码行
        + "           operand: {'foo': false}\n" // 代码行
        + "         },\n" // 代码行
        + "         {\n" // 代码行
        + "           name: 'V',\n" // 代码行
        + "           type: 'view',\n" // 代码行
        + (modifiable == null ? "" : " modifiable: " + modifiable + ",\n") // 代码行
        + "           sql: " + new JsonBuilder().toJsonString(view) + "\n" // 代码行
        + "         }\n" // 代码行
        + "       ]\n" // 代码行
        + "     }\n" // 代码行
        + "   ]\n" // 代码行
        + "}"); // 代码行
  } // 代码行
 // 空行
  /** Tests a JDBC connection that provides a model that contains a view. */ // JavaDoc注释开始
  @Test void testModelView() throws Exception { // 测试方法:测试ModelView功能
    final CalciteAssert.AssertThat with = // 代码行
        modelWithView("select * from \"EMPLOYEES\" where \"deptno\" = 10", // 代码行
            null); // 代码行
 // 空行
    with.query("select * from \"adhoc\".V order by \"name\" desc") // 代码行
        .returns("" // 代码行
            + "empid=110; deptno=10; name=Theodore; salary=11500.0; commission=250\n" // 代码行
            + "empid=150; deptno=10; name=Sebastian; salary=7000.0; commission=null\n" // 代码行
            + "empid=100; deptno=10; name=Bill; salary=10000.0; commission=1000\n"); // 代码行
 // 空行
    // Make sure that views appear in metadata. // 单行注释
    with.doWithConnection(connection -> { // 代码行
      try { // 代码行
        final DatabaseMetaData metaData = connection.getMetaData(); // 代码行
 // 空行
        // all table types // 单行注释
        try (ResultSet r = // 代码行
                 metaData.getTables(null, "adhoc", null, null)) { // 代码行
          assertThat(CalciteAssert.toString(r), // 代码行
              is("TABLE_CAT=null; TABLE_SCHEM=adhoc;" // 代码行
                  + " TABLE_NAME=EMPLOYEES; TABLE_TYPE=TABLE;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc;" // 代码行
                  + " TABLE_NAME=MUTABLE_EMPLOYEES; TABLE_TYPE=TABLE;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc;" // 代码行
                  + " TABLE_NAME=V; TABLE_TYPE=VIEW;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n")); // 代码行
        } // 代码行
 // 空行
        // including system tables; note that table type is "SYSTEM TABLE" // 单行注释
        // not "SYSTEM_TABLE" // 单行注释
        try (ResultSet r = metaData.getTables(null, null, null, null)) { // 代码行
          assertThat(CalciteAssert.toString(r), // 代码行
              is("TABLE_CAT=null; TABLE_SCHEM=adhoc;" // 代码行
                  + " TABLE_NAME=EMPLOYEES; TABLE_TYPE=TABLE;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc;" // 代码行
                  + " TABLE_NAME=MUTABLE_EMPLOYEES; TABLE_TYPE=TABLE;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc;" // 代码行
                  + " TABLE_NAME=V; TABLE_TYPE=VIEW;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=metadata;" // 代码行
                  + " TABLE_NAME=COLUMNS; TABLE_TYPE=SYSTEM TABLE;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=metadata;" // 代码行
                  + " TABLE_NAME=TABLES; TABLE_TYPE=SYSTEM TABLE;" // 代码行
                  + " REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null;" // 代码行
                  + " TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null;" // 代码行
                  + " REF_GENERATION=null\n")); // 代码行
        } // 代码行
 // 空行
        // views only // 单行注释
        try (ResultSet r = // 代码行
                 metaData.getTables(null, "adhoc", null, // 代码行
                     new String[]{Schema.TableType.VIEW.jdbcName})) { // 代码行
          assertThat(CalciteAssert.toString(r), // 代码行
              is("TABLE_CAT=null; TABLE_SCHEM=adhoc; TABLE_NAME=V;" // 代码行
                  + " TABLE_TYPE=VIEW; REMARKS=null; TYPE_CAT=null;" // 代码行
                  + " TYPE_SCHEM=null; TYPE_NAME=null;" // 代码行
                  + " SELF_REFERENCING_COL_NAME=null; REF_GENERATION=null\n")); // 代码行
        } // 代码行
 // 空行
        // columns // 单行注释
        try (ResultSet r = // 代码行
                 metaData.getColumns(null, "adhoc", "V", null)) { // 代码行
          assertThat(CalciteAssert.toString(r), // 代码行
              is("TABLE_CAT=null; TABLE_SCHEM=adhoc; TABLE_NAME=V;" // 代码行
                  + " COLUMN_NAME=empid; DATA_TYPE=4;" // 代码行
                  + " TYPE_NAME=JavaType(int) NOT NULL; COLUMN_SIZE=-1;" // 代码行
                  + " BUFFER_LENGTH=null; DECIMAL_DIGITS=null;" // 代码行
                  + " NUM_PREC_RADIX=10; NULLABLE=0; REMARKS=null;" // 代码行
                  + " COLUMN_DEF=null; SQL_DATA_TYPE=null;" // 代码行
                  + " SQL_DATETIME_SUB=null; CHAR_OCTET_LENGTH=-1;" // 代码行
                  + " ORDINAL_POSITION=1; IS_NULLABLE=NO; SCOPE_CATALOG=null;" // 代码行
                  + " SCOPE_SCHEMA=null; SCOPE_TABLE=null;" // 代码行
                  + " SOURCE_DATA_TYPE=null; IS_AUTOINCREMENT=;" // 代码行
                  + " IS_GENERATEDCOLUMN=\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc; TABLE_NAME=V;" // 代码行
                  + " COLUMN_NAME=deptno; DATA_TYPE=4;" // 代码行
                  + " TYPE_NAME=JavaType(int) NOT NULL; COLUMN_SIZE=-1;" // 代码行
                  + " BUFFER_LENGTH=null; DECIMAL_DIGITS=null;" // 代码行
                  + " NUM_PREC_RADIX=10; NULLABLE=0; REMARKS=null;" // 代码行
                  + " COLUMN_DEF=null; SQL_DATA_TYPE=null;" // 代码行
                  + " SQL_DATETIME_SUB=null; CHAR_OCTET_LENGTH=-1;" // 代码行
                  + " ORDINAL_POSITION=2; IS_NULLABLE=NO; SCOPE_CATALOG=null;" // 代码行
                  + " SCOPE_SCHEMA=null; SCOPE_TABLE=null;" // 代码行
                  + " SOURCE_DATA_TYPE=null; IS_AUTOINCREMENT=;" // 代码行
                  + " IS_GENERATEDCOLUMN=\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc; TABLE_NAME=V;" // 代码行
                  + " COLUMN_NAME=name; DATA_TYPE=12;" // 代码行
                  + " TYPE_NAME=JavaType(class java.lang.String);" // 代码行
                  + " COLUMN_SIZE=-1; BUFFER_LENGTH=null; DECIMAL_DIGITS=null;" // 代码行
                  + " NUM_PREC_RADIX=10; NULLABLE=1; REMARKS=null;" // 代码行
                  + " COLUMN_DEF=null; SQL_DATA_TYPE=null;" // 代码行
                  + " SQL_DATETIME_SUB=null; CHAR_OCTET_LENGTH=-1;" // 代码行
                  + " ORDINAL_POSITION=3; IS_NULLABLE=YES; SCOPE_CATALOG=null;" // 代码行
                  + " SCOPE_SCHEMA=null; SCOPE_TABLE=null;" // 代码行
                  + " SOURCE_DATA_TYPE=null; IS_AUTOINCREMENT=;" // 代码行
                  + " IS_GENERATEDCOLUMN=\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc; TABLE_NAME=V;" // 代码行
                  + " COLUMN_NAME=salary; DATA_TYPE=7;" // 代码行
                  + " TYPE_NAME=JavaType(float) NOT NULL; COLUMN_SIZE=-1;" // 代码行
                  + " BUFFER_LENGTH=null; DECIMAL_DIGITS=null;" // 代码行
                  + " NUM_PREC_RADIX=10; NULLABLE=0; REMARKS=null;" // 代码行
                  + " COLUMN_DEF=null; SQL_DATA_TYPE=null;" // 代码行
                  + " SQL_DATETIME_SUB=null; CHAR_OCTET_LENGTH=-1;" // 代码行
                  + " ORDINAL_POSITION=4; IS_NULLABLE=NO; SCOPE_CATALOG=null;" // 代码行
                  + " SCOPE_SCHEMA=null; SCOPE_TABLE=null;" // 代码行
                  + " SOURCE_DATA_TYPE=null; IS_AUTOINCREMENT=;" // 代码行
                  + " IS_GENERATEDCOLUMN=\n" // 代码行
                  + "TABLE_CAT=null; TABLE_SCHEM=adhoc; TABLE_NAME=V;" // 代码行
                  + " COLUMN_NAME=commission; DATA_TYPE=4;" // 代码行
                  + " TYPE_NAME=JavaType(class java.lang.Integer);" // 代码行
                  + " COLUMN_SIZE=-1; BUFFER_LENGTH=null; DECIMAL_DIGITS=null;" // 代码行
                  + " NUM_PREC_RADIX=10; NULLABLE=1; REMARKS=null;" // 代码行
                  + " COLUMN_DEF=null; SQL_DATA_TYPE=null;" // 代码行
                  + " SQL_DATETIME_SUB=null; CHAR_OCTET_LENGTH=-1;" // 代码行
                  + " ORDINAL_POSITION=5; IS_NULLABLE=YES; SCOPE_CATALOG=null;" // 代码行
                  + " SCOPE_SCHEMA=null; SCOPE_TABLE=null;" // 代码行
                  + " SOURCE_DATA_TYPE=null; IS_AUTOINCREMENT=;" // 代码行
                  + " IS_GENERATEDCOLUMN=\n")); // 代码行
        } // 代码行
 // 空行
        // catalog // 单行注释
        try (ResultSet r = metaData.getCatalogs()) { // 代码行
          assertThat(CalciteAssert.toString(r), is("TABLE_CAT=null\n")); // 代码行
        } // 代码行
 // 空行
        // schemas // 单行注释
        try (ResultSet r = metaData.getSchemas()) { // 代码行
          assertThat(CalciteAssert.toString(r), // 代码行
              is("TABLE_SCHEM=adhoc; TABLE_CATALOG=null\n" // 代码行
                  + "TABLE_SCHEM=metadata; TABLE_CATALOG=null\n")); // 代码行
        } // 代码行
 // 空行
        // schemas (qualified) // 单行注释
        try (ResultSet r = metaData.getSchemas(null, "adhoc")) { // 代码行
          assertThat(CalciteAssert.toString(r), // 代码行
              is("TABLE_SCHEM=adhoc; TABLE_CATALOG=null\n")); // 代码行
        } // 代码行
 // 空行
        // table types // 单行注释
        try (ResultSet r = metaData.getTableTypes()) { // 代码行
          assertThat(CalciteAssert.toString(r), // 代码行
              is("TABLE_TYPE=TABLE\n" // 代码行
                  + "TABLE_TYPE=VIEW\n")); // 代码行
        } // 代码行
      } catch (SQLException e) { // 代码行
        throw TestUtil.rethrow(e); // 代码行
      } // 代码行
    }); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4323">[CALCITE-4323] // JavaDoc注释内容
   * View with ORDER BY throws AssertionError during view expansion</a>. */ // JavaDoc注释内容
  @Test void testSortedView() { // 测试方法:测试SortedView功能
    final String viewSql = "select * from \"EMPLOYEES\" order by \"deptno\""; // 代码行
    final CalciteAssert.AssertThat with = modelWithView(viewSql, null); // 代码行
    // Keep sort, because view is top node // 单行注释
    with.query("select * from \"adhoc\".V") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalProject(empid=[$0], deptno=[$1], name=[$2], " // 代码行
                + "salary=[$3], commission=[$4])\n" // 代码行
                + "  LogicalSort(sort0=[$1], dir0=[ASC])\n" // 代码行
                + "    LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "      LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    // Remove sort, because view not is top node // 单行注释
    with.query("select * from \"adhoc\".V union all select * from  \"adhoc\".\"EMPLOYEES\"") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalUnion(all=[true])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select * from " // 代码行
            + "(select \"empid\", \"deptno\" from  \"adhoc\".V) where \"deptno\" > 10") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalProject(empid=[$0], deptno=[$1])\n" // 代码行
                + "  LogicalFilter(condition=[>($1, 10)])\n" // 代码行
                + "    LogicalProject(empid=[$0], deptno=[$1])\n" // 代码行
                + "      LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select * from \"adhoc\".\"EMPLOYEES\" where exists (select * from \"adhoc\".V)") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalProject(empid=[$0], deptno=[$1], name=[$2], " // 代码行
                + "salary=[$3], commission=[$4])\n" // 代码行
                + "  LogicalFilter(condition=[EXISTS({\n" // 代码行
                + "LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n" // 代码行
                + "})])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    // View is used in a query at top level，but it's not the top plan // 单行注释
    // Still remove sort // 单行注释
    with.query("select * from \"adhoc\".V order by \"empid\"") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalSort(sort0=[$0], dir0=[ASC])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select * from \"adhoc\".V, \"adhoc\".\"EMPLOYEES\"") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalProject(empid=[$0], deptno=[$1], name=[$2], " // 代码行
                + "salary=[$3], commission=[$4], empid0=[$5], deptno0=[$6], name0=[$7], salary0=[$8]," // 代码行
                + " commission0=[$9])\n" // 代码行
                + "  LogicalJoin(condition=[true], joinType=[inner])\n" // 代码行
                + "    LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "      LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select \"empid\", count(*) from \"adhoc\".V group by \"empid\"") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalAggregate(group=[{0}], EXPR$1=[COUNT()])\n" // 代码行
                + "  LogicalProject(empid=[$0])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select distinct * from \"adhoc\".V") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalAggregate(group=[{0, 1, 2, 3, 4}])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
  } // 代码行
 // 空行
  @Test void testCustomRemoveSortInView() { // 测试方法:测试CustomRemoveSortInView功能
    final String viewSql = "select * from \"EMPLOYEES\" order by \"deptno\""; // 代码行
    final CalciteAssert.AssertThat with = modelWithView(viewSql, null); // 代码行
    // Some cases where we may or may not want to keep the Sort // 单行注释
    with.query("select * from \"adhoc\".V where \"deptno\" > 10") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalProject(empid=[$0], deptno=[$1], name=[$2], " // 代码行
                + "salary=[$3], commission=[$4])\n" // 代码行
                + "  LogicalFilter(condition=[>($1, 10)])\n" // 代码行
                + "    LogicalSort(sort0=[$1], dir0=[ASC])\n" // 代码行
                + "      LogicalProject(empid=[$0], deptno=[$1], name=[$2], " // 代码行
                + "salary=[$3], commission=[$4])\n" // 代码行
                + "        LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select * from \"adhoc\".V where \"deptno\" > 10") // 代码行
        .withHook(Hook.SQL2REL_CONVERTER_CONFIG_BUILDER, // 代码行
            (Consumer<Holder<Config>>) configHolder -> // 代码行
                configHolder.set(configHolder.get().withRemoveSortInSubQuery(false))) // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalProject(empid=[$0], deptno=[$1], name=[$2], " // 代码行
                + "salary=[$3], commission=[$4])\n" // 代码行
                + "  LogicalFilter(condition=[>($1, 10)])\n" // 代码行
                + "    LogicalSort(sort0=[$1], dir0=[ASC])\n" // 代码行
                + "      LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "        LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
 // 空行
    with.query("select * from \"adhoc\".V limit 10") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalSort(fetch=[10])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select * from \"adhoc\".V limit 10") // 代码行
        .withHook(Hook.SQL2REL_CONVERTER_CONFIG_BUILDER, // 代码行
            (Consumer<Holder<Config>>) configHolder -> // 代码行
                configHolder.set(configHolder.get().withRemoveSortInSubQuery(false))) // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalSort(fetch=[10])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalSort(sort0=[$1], dir0=[ASC])\n" // 代码行
                + "      LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "        LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
 // 空行
    with.query("select * from \"adhoc\".V offset 10") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalSort(offset=[10])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select * from \"adhoc\".V offset 10") // 代码行
        .withHook(Hook.SQL2REL_CONVERTER_CONFIG_BUILDER, // 代码行
            (Consumer<Holder<Config>>) configHolder -> // 代码行
                configHolder.set(configHolder.get().withRemoveSortInSubQuery(false))) // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalSort(offset=[10])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalSort(sort0=[$1], dir0=[ASC])\n" // 代码行
                + "      LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "        LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
 // 空行
 // 空行
    with.query("select * from \"adhoc\".V limit 5 offset 5") // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalSort(offset=[5], fetch=[5])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
    with.query("select * from \"adhoc\".V limit 5 offset 5") // 代码行
        .withHook(Hook.SQL2REL_CONVERTER_CONFIG_BUILDER, // 代码行
            (Consumer<Holder<Config>>) configHolder -> // 代码行
                configHolder.set(configHolder.get().withRemoveSortInSubQuery(false))) // 代码行
        .explainMatches(" without implementation ", // 代码行
            checkResult("PLAN=" // 代码行
                + "LogicalSort(offset=[5], fetch=[5])\n" // 代码行
                + "  LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "    LogicalSort(sort0=[$1], dir0=[ASC])\n" // 代码行
                + "      LogicalProject(empid=[$0], deptno=[$1], name=[$2], salary=[$3], " // 代码行
                + "commission=[$4])\n" // 代码行
                + "        LogicalTableScan(table=[[adhoc, EMPLOYEES]])\n\n")); // 代码行
  } // 代码行
 // 空行
  /** Tests a view with ORDER BY and LIMIT clauses. */ // JavaDoc注释开始
  @Test void testOrderByView() { // 测试方法:测试OrderByView功能
    final CalciteAssert.AssertThat with = // 代码行
        modelWithView("select * from \"EMPLOYEES\" where \"deptno\" = 10 " // 代码行
            + "order by \"empid\" limit 2", null); // 代码行
    with // 代码行
        .query("select \"name\" from \"adhoc\".V order by \"name\"") // 代码行
        .returns("name=Bill\n" // 代码行
            + "name=Theodore\n"); // 代码行
 // 空行
    // Now a sub-query with ORDER BY and LIMIT clauses. (Same net effect, but // 单行注释
    // ORDER BY and LIMIT in sub-query were not standard SQL until SQL:2008.) // 单行注释
    with // 代码行
        .query("select \"name\" from (\n" // 代码行
            + "select * from \"adhoc\".\"EMPLOYEES\" where \"deptno\" = 10\n" // 代码行
            + "order by \"empid\" limit 2)\n" // 代码行
            + "order by \"name\"") // 代码行
        .returns("name=Bill\n" // 代码行
            + "name=Theodore\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1900">[CALCITE-1900] // JavaDoc注释内容
   * Improve error message for cyclic views</a>. // JavaDoc注释内容
   * Previously got a {@link StackOverflowError}. */ // JavaDoc注释内容
  @Test void testSelfReferentialView() { // 测试方法:测试SelfReferentialView功能
    final CalciteAssert.AssertThat with = // 代码行
        modelWithView("select * from \"V\"", null); // 代码行
    with.query("select \"name\" from \"adhoc\".V") // 代码行
        .throws_("Cannot resolve 'adhoc.V'; it references view 'adhoc.V', " // 代码行
            + "whose definition is cyclic"); // 代码行
  } // 代码行
 // 空行
  @Test void testSelfReferentialView2() { // 测试方法:测试SelfReferentialView2功能
    final String model = "{\n" // 代码行
        + "  version: '1.0',\n" // 代码行
        + "  defaultSchema: 'adhoc',\n" // 代码行
        + "  schemas: [ {\n" // 代码行
        + "    name: 'adhoc',\n" // 代码行
        + "    tables: [ {\n" // 代码行
        + "      name: 'A',\n" // 代码行
        + "      type: 'view',\n" // 代码行
        + "      sql: " // 代码行
        + new JsonBuilder().toJsonString("select * from B") + "\n" // 代码行
        + "    }, {\n" // 代码行
        + "      name: 'B',\n" // 代码行
        + "      type: 'view',\n" // 代码行
        + "      sql: " // 代码行
        + new JsonBuilder().toJsonString("select * from C") + "\n" // 代码行
        + "    }, {\n" // 代码行
        + "      name: 'C',\n" // 代码行
        + "      type: 'view',\n" // 代码行
        + "      sql: " // 代码行
        + new JsonBuilder().toJsonString("select * from D, B") + "\n" // 代码行
        + "    }, {\n" // 代码行
        + "      name: 'D',\n" // 代码行
        + "      type: 'view',\n" // 代码行
        + "      sql: " // 代码行
        + new JsonBuilder().toJsonString( // 代码行
            "select * from (values (1, 'a')) as t(x, y)") + "\n" // 代码行
        + "    } ]\n" // 代码行
        + "  } ]\n" // 代码行
        + "}"; // 代码行
    final CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.model(model); // 代码行
    // // 单行注释
    //       +-----+ // 单行注释
    //       V     | // 单行注释
    // A --> B --> C --> D // 单行注释
    // // 单行注释
    // A is not in a cycle, but depends on cyclic views // 单行注释
    // B is cyclic // 单行注释
    // C is cyclic // 单行注释
    // D is not cyclic // 单行注释
    with.query("select x from \"adhoc\".a") // 代码行
        .throws_("Cannot resolve 'adhoc.A'; it references view 'adhoc.B', " // 代码行
            + "whose definition is cyclic"); // 代码行
    with.query("select x from \"adhoc\".b") // 代码行
        .throws_("Cannot resolve 'adhoc.B'; it references view 'adhoc.B', " // 代码行
            + "whose definition is cyclic"); // 代码行
    // as previous, but implicit schema // 单行注释
    with.query("select x from b") // 代码行
        .throws_("Cannot resolve 'B'; it references view 'adhoc.B', " // 代码行
            + "whose definition is cyclic"); // 代码行
    with.query("select x from \"adhoc\".c") // 代码行
        .throws_("Cannot resolve 'adhoc.C'; it references view 'adhoc.C', " // 代码行
            + "whose definition is cyclic"); // 代码行
    with.query("select x from \"adhoc\".d") // 代码行
        .returns("X=1\n"); // 代码行
    with.query("select x from \"adhoc\".d except select x from \"adhoc\".a") // 代码行
        .throws_("Cannot resolve 'adhoc.A'; it references view 'adhoc.B', " // 代码行
            + "whose definition is cyclic"); // 代码行
  } // 代码行
 // 空行
  /** Tests saving query results into temporary tables, per // JavaDoc注释开始
   * {@link org.apache.calcite.avatica.Handler.ResultSink}. */ // JavaDoc注释内容
  @Test void testAutomaticTemporaryTable() throws Exception { // 测试方法:测试AutomaticTemporaryTable功能
    final List<Object> objects = new ArrayList<>(); // 代码行
    CalciteAssert.that() // 代码行
        .with(() -> { // 代码行
          CalciteConnection connection = (CalciteConnection) // 代码行
              new AutoTempDriver(objects) // 代码行
                  .connect("jdbc:calcite:", new Properties()); // 代码行
          final SchemaPlus rootSchema = connection.getRootSchema(); // 代码行
          rootSchema.add("hr", // 代码行
              new ReflectiveSchema(new HrSchema())); // 代码行
          connection.setSchema("hr"); // 代码行
          return connection; // 代码行
        }) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            final String sql = "select * from \"hr\".\"emps\" " // 代码行
                + "where \"deptno\" = 10"; // 代码行
            connection.createStatement() // 代码行
                .executeQuery(sql); // 代码行
            assertThat(objects, hasSize(1)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  @Test void testExplain() { // 测试方法:测试Explain功能
    final CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that().with(CalciteAssert.Config.FOODMART_CLONE); // 代码行
    with.query("explain plan for values (1, 'ab')") // 代码行
        .returns("PLAN=EnumerableValues(tuples=[[{ 1, 'ab' }]])\n\n"); // 代码行
    final String expectedXml = "PLAN=<RelNode type=\"EnumerableValues\">\n" // 代码行
        + "\t<Property name=\"tuples\">\n" // 代码行
        + "\t\t[{ 1, &#39;ab&#39; }]\n" // 代码行
        + "\t</Property>\n" // 代码行
        + "\t<Inputs/>\n" // 代码行
        + "</RelNode>\n" // 代码行
        + "\n"; // 代码行
    with.query("explain plan as xml for values (1, 'ab')") // 代码行
        .returns(expectedXml); // 代码行
    final String expectedJson = "PLAN={\n" // 代码行
        + "  \"rels\": [\n" // 代码行
        + "    {\n" // 代码行
        + "      \"id\": \"0\",\n" // 代码行
        + "      \"relOp\": \"org.apache.calcite.adapter.enumerable.EnumerableValues\",\n" // 代码行
        + "      \"type\": [\n" // 代码行
        + "        {\n" // 代码行
        + "          \"type\": \"INTEGER\",\n" // 代码行
        + "          \"nullable\": false,\n" // 代码行
        + "          \"name\": \"EXPR$0\"\n" // 代码行
        + "        },\n" // 代码行
        + "        {\n" // 代码行
        + "          \"type\": \"CHAR\",\n" // 代码行
        + "          \"nullable\": false,\n" // 代码行
        + "          \"precision\": 2,\n" // 代码行
        + "          \"name\": \"EXPR$1\"\n" // 代码行
        + "        },\n" // 代码行
        + "        {\n" // 代码行
        + "          \"type\": \"TIMESTAMP\",\n" // 代码行
        + "          \"nullable\": false,\n" // 代码行
        + "          \"precision\": 0,\n" // 代码行
        + "          \"name\": \"EXPR$2\"\n" // 代码行
        + "        },\n" // 代码行
        + "        {\n" // 代码行
        + "          \"type\": \"DECIMAL\",\n" // 代码行
        + "          \"nullable\": false,\n" // 代码行
        + "          \"precision\": 3,\n" // 代码行
        + "          \"scale\": 2,\n" // 代码行
        + "          \"name\": \"EXPR$3\"\n" // 代码行
        + "        }\n" // 代码行
        + "      ],\n" // 代码行
        + "      \"tuples\": [\n" // 代码行
        + "        [\n" // 代码行
        + "          {\n" // 代码行
        + "            \"literal\": 1,\n" // 代码行
        + "            \"type\": {\n" // 代码行
        + "              \"type\": \"INTEGER\",\n" // 代码行
        + "              \"nullable\": false\n" // 代码行
        + "            }\n" // 代码行
        + "          },\n" // 代码行
        + "          {\n" // 代码行
        + "            \"literal\": \"ab\",\n" // 代码行
        + "            \"type\": {\n" // 代码行
        + "              \"type\": \"CHAR\",\n" // 代码行
        + "              \"nullable\": false,\n" // 代码行
        + "              \"precision\": 2\n" // 代码行
        + "            }\n" // 代码行
        + "          },\n" // 代码行
        + "          {\n" // 代码行
        + "            \"literal\": 1364860800000,\n" // 代码行
        + "            \"type\": {\n" // 代码行
        + "              \"type\": \"TIMESTAMP\",\n" // 代码行
        + "              \"nullable\": false,\n" // 代码行
        + "              \"precision\": 0\n" // 代码行
        + "            }\n" // 代码行
        + "          },\n" // 代码行
        + "          {\n" // 代码行
        + "            \"literal\": 0.01,\n" // 代码行
        + "            \"type\": {\n" // 代码行
        + "              \"type\": \"DECIMAL\",\n" // 代码行
        + "              \"nullable\": false,\n" // 代码行
        + "              \"precision\": 3,\n" // 代码行
        + "              \"scale\": 2\n" // 代码行
        + "            }\n" // 代码行
        + "          }\n" // 代码行
        + "        ]\n" // 代码行
        + "      ],\n" // 代码行
        + "      \"inputs\": []\n" // 代码行
        + "    }\n" // 代码行
        + "  ]\n" // 代码行
        + "}\n"; // 代码行
    with.query("explain plan as json for values (1, 'ab', TIMESTAMP '2013-04-02 00:00:00', 0.01)") // 代码行
        .returns(expectedJson); // 代码行
    with.query("explain plan with implementation for values (1, 'ab')") // 代码行
        .returns("PLAN=EnumerableValues(tuples=[[{ 1, 'ab' }]])\n\n"); // 代码行
    with.query("explain plan without implementation for values (1, 'ab')") // 代码行
        .returns("PLAN=LogicalValues(tuples=[[{ 1, 'ab' }]])\n\n"); // 代码行
    with.query("explain plan with type for values (1, 'ab')") // 代码行
        .returns("PLAN=EXPR$0 INTEGER NOT NULL,\n" // 代码行
            + "EXPR$1 CHAR(2) NOT NULL\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for bug where if two tables have different element classes // JavaDoc注释开始
   * but those classes have identical fields, Calcite would generate code to use // JavaDoc注释内容
   * the wrong element class; a {@link ClassCastException} would ensue. */ // JavaDoc注释内容
  @Test void testDifferentTypesSameFields() throws Exception { // 测试方法:测试DifferentTypesSameFields功能
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 代码行
    CalciteConnection calciteConnection = // 代码行
        connection.unwrap(CalciteConnection.class); // 代码行
    final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 代码行
    rootSchema.add("TEST", new ReflectiveSchema(new MySchema())); // 代码行
    Statement statement = calciteConnection.createStatement(); // 代码行
    ResultSet resultSet = // 代码行
        statement.executeQuery("SELECT \"myvalue\" from TEST.\"mytable2\""); // 代码行
    assertThat(CalciteAssert.toString(resultSet), is("myvalue=2\n")); // 代码行
    resultSet.close(); // 代码行
    statement.close(); // 代码行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Tests that CURRENT_TIMESTAMP gives different values each time a statement // JavaDoc注释开始
   * is executed. */ // JavaDoc注释内容
  @Test void testCurrentTimestamp() throws Exception { // 测试方法:测试CurrentTimestamp功能
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.TIME_ZONE, "GMT+1:00") // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            final PreparedStatement statement = // 代码行
                connection.prepareStatement("VALUES CURRENT_TIMESTAMP"); // 代码行
            ResultSet resultSet; // 代码行
 // 空行
            resultSet = statement.executeQuery(); // 代码行
            assertTrue(resultSet.next()); // 代码行
            String s0 = resultSet.getString(1); // 代码行
            assertFalse(resultSet.next()); // 代码行
 // 空行
            try { // 代码行
              Thread.sleep(1000); // 代码行
            } catch (InterruptedException e) { // 代码行
              throw TestUtil.rethrow(e); // 代码行
            } // 代码行
 // 空行
            resultSet = statement.executeQuery(); // 代码行
            assertTrue(resultSet.next()); // 代码行
            String s1 = resultSet.getString(1); // 代码行
            assertFalse(resultSet.next()); // 代码行
 // 空行
            assertThat(s0, ComparatorMatcherBuilder.<String>usingNaturalOrdering().lessThan(s1)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Test for timestamps and time zones, based on pgsql TimezoneTest. */ // JavaDoc注释开始
  @Test void testGetTimestamp() throws Exception { // 测试方法:测试GetTimestamp功能
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.TIME_ZONE, "GMT+1:00") // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            checkGetTimestamp(connection); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  private void checkGetTimestamp(Connection con) throws SQLException { // checkGetTimestamp方法:检查获取时间戳功能
    Statement statement = con.createStatement(); // 代码行
 // 空行
    // Not supported yet. We set timezone using connect-string parameters. // 单行注释
    //   statement.executeUpdate("alter session set timezone = 'gmt-3'"); // 单行注释
 // 空行
    ResultSet rs = statement.executeQuery("SELECT * FROM (VALUES(\n" // 代码行
        + " TIMESTAMP '1970-01-01 00:00:00',\n" // 代码行
        + " /* TIMESTAMP '2005-01-01 15:00:00 +0300', */\n" // 代码行
        + " TIMESTAMP '2005-01-01 15:00:00',\n" // 代码行
        + " TIME '15:00:00',\n" // 代码行
        + " /* TIME '15:00:00 +0300', */\n" // 代码行
        + " DATE '2005-01-01'\n" // 代码行
        + ")) AS t(ts0, /* tstz, */ ts, t, /* tz, */ d)"); // 代码行
    assertTrue(rs.next()); // 代码行
 // 空行
    TimeZone tzUtc   = TimeZone.getTimeZone("UTC");    // +0000 always // 代码行
    TimeZone tzGmt03 = TimeZone.getTimeZone("GMT+03"); // +0300 always // 代码行
    TimeZone tzGmt05 = TimeZone.getTimeZone("GMT-05"); // -0500 always // 代码行
    TimeZone tzGmt13 = TimeZone.getTimeZone("GMT+13"); // +1000 always // 代码行
 // 空行
    Calendar cUtc   = Calendar.getInstance(tzUtc, Locale.ROOT); // 代码行
    Calendar cGmt03 = Calendar.getInstance(tzGmt03, Locale.ROOT); // 代码行
    Calendar cGmt05 = Calendar.getInstance(tzGmt05, Locale.ROOT); // 代码行
    Calendar cGmt13 = Calendar.getInstance(tzGmt13, Locale.ROOT); // 代码行
 // 空行
    Timestamp ts; // 代码行
    String s; // 代码行
    int c = 1; // 代码行
 // 空行
    // timestamp: 1970-01-01 00:00:00 // 单行注释
    ts = rs.getTimestamp(c);                     // Convert timestamp to +0100 // 代码行
    assertThat(ts.getTime(), is(-3600000L)); // 代码行
    ts = rs.getTimestamp(c, cUtc);               // Convert timestamp to UTC // 代码行
    assertThat(ts.getTime(), is(0L)); // 代码行
    ts = rs.getTimestamp(c, cGmt03);             // Convert timestamp to +0300 // 代码行
    assertThat(ts.getTime(), is(-10800000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt05);             // Convert timestamp to -0500 // 代码行
    assertThat(ts.getTime(), is(18000000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt13);             // Convert timestamp to +1300 // 代码行
    assertThat(ts.getTime(), is(-46800000L)); // 代码行
    s = rs.getString(c); // 代码行
    assertThat(s, is("1970-01-01 00:00:00")); // 代码行
    ++c; // 代码行
 // 空行
    if (false) { // 代码行
      // timestamptz: 2005-01-01 15:00:00+03 // 单行注释
      ts = rs.getTimestamp(c);                      // Represents an instant in // 代码行
                                                    // time, TZ is irrelevant. // 单行注释
      assertThat(ts.getTime(), is(1104580800000L)); // 代码行
      ts = rs.getTimestamp(c, cUtc);                // TZ irrelevant, as above // 代码行
      assertThat(ts.getTime(), is(1104580800000L)); // 代码行
      ts = rs.getTimestamp(c, cGmt03);              // TZ irrelevant, as above // 代码行
      assertThat(ts.getTime(), is(1104580800000L)); // 代码行
      ts = rs.getTimestamp(c, cGmt05);              // TZ irrelevant, as above // 代码行
      assertThat(ts.getTime(), is(1104580800000L)); // 代码行
      ts = rs.getTimestamp(c, cGmt13);              // TZ irrelevant, as above // 代码行
      assertThat(ts.getTime(), is(1104580800000L)); // 代码行
      ++c; // 代码行
    } // 代码行
 // 空行
    // timestamp: 2005-01-01 15:00:00 // 单行注释
    ts = rs.getTimestamp(c);                     // Convert timestamp to +0100 // 代码行
    assertThat(ts.getTime(), is(1104588000000L)); // 代码行
    ts = rs.getTimestamp(c, cUtc);               // Convert timestamp to UTC // 代码行
    assertThat(ts.getTime(), is(1104591600000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt03);             // Convert timestamp to +0300 // 代码行
    assertThat(ts.getTime(), is(1104580800000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt05);             // Convert timestamp to -0500 // 代码行
    assertThat(ts.getTime(), is(1104609600000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt13);             // Convert timestamp to +1300 // 代码行
    assertThat(ts.getTime(), is(1104544800000L)); // 代码行
    s = rs.getString(c); // 代码行
    assertThat(s, is("2005-01-01 15:00:00")); // 代码行
    ++c; // 代码行
 // 空行
    // time: 15:00:00 // 单行注释
    ts = rs.getTimestamp(c); // 代码行
    assertThat(ts.getTime(), is(50400000L)); // 代码行
    ts = rs.getTimestamp(c, cUtc); // 代码行
    assertThat(ts.getTime(), is(54000000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt03); // 代码行
    assertThat(ts.getTime(), is(43200000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt05); // 代码行
    assertThat(ts.getTime(), is(72000000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt13); // 代码行
    assertThat(ts.getTime(), is(7200000L)); // 代码行
    s = rs.getString(c); // 代码行
    assertThat(s, is("15:00:00")); // 代码行
    ++c; // 代码行
 // 空行
    if (false) { // 代码行
      // timetz: 15:00:00+03 // 单行注释
      ts = rs.getTimestamp(c); // 代码行
      assertThat(ts.getTime(), is(43200000L)); // 代码行
      // 1970-01-01 13:00:00 +0100 // 单行注释
      ts = rs.getTimestamp(c, cUtc); // 代码行
      assertThat(ts.getTime(), is(43200000L)); // 代码行
      // 1970-01-01 12:00:00 +0000 // 单行注释
      ts = rs.getTimestamp(c, cGmt03); // 代码行
      assertThat(ts.getTime(), is(43200000L)); // 代码行
      // 1970-01-01 15:00:00 +0300 // 单行注释
      ts = rs.getTimestamp(c, cGmt05); // 代码行
      assertThat(ts.getTime(), is(43200000L)); // 代码行
      // 1970-01-01 07:00:00 -0500 // 单行注释
      ts = rs.getTimestamp(c, cGmt13); // 代码行
      assertThat(ts.getTime(), is(43200000L)); // 代码行
      // 1970-01-02 01:00:00 +1300 // 单行注释
      ++c; // 代码行
    } // 代码行
 // 空行
    // date: 2005-01-01 // 单行注释
    ts = rs.getTimestamp(c); // 代码行
    assertThat(ts.getTime(), is(1104534000000L)); // 代码行
    ts = rs.getTimestamp(c, cUtc); // 代码行
    assertThat(ts.getTime(), is(1104537600000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt03); // 代码行
    assertThat(ts.getTime(), is(1104526800000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt05); // 代码行
    assertThat(ts.getTime(), is(1104555600000L)); // 代码行
    ts = rs.getTimestamp(c, cGmt13); // 代码行
    assertThat(ts.getTime(), is(1104490800000L)); // 代码行
    s = rs.getString(c); // 代码行
    assertThat(s, is("2005-01-01")); // 代码行
    ++c; // 代码行
 // 空行
    assertTrue(!rs.next()); // 代码行
  } // 代码行
 // 空行
  /** Test for MONTHNAME, DAYNAME and DAYOFWEEK functions in two locales. */ // JavaDoc注释开始
  @Test void testMonthName() { // 测试方法:测试MonthName功能
    final String sql = "SELECT * FROM (VALUES(\n" // 代码行
        + " monthname(TIMESTAMP '1969-01-01 00:00:00'),\n" // 代码行
        + " monthname(DATE '1969-01-01'),\n" // 代码行
        + " monthname(DATE '2019-02-10'),\n" // 代码行
        + " monthname(TIMESTAMP '2019-02-10 02:10:12'),\n" // 代码行
        + " dayname(TIMESTAMP '1969-01-01 00:00:00'),\n" // 代码行
        + " dayname(DATE '1969-01-01'),\n" // 代码行
        + " dayname(DATE '2019-02-10'),\n" // 代码行
        + " dayname(TIMESTAMP '2019-02-10 02:10:12'),\n" // 代码行
        + " dayofweek(DATE '2019-02-09'),\n" // sat=7 // 代码行
        + " dayofweek(DATE '2019-02-10'),\n" // sun=1 // 代码行
        + " extract(DOW FROM DATE '2019-02-09'),\n" // sat=7 // 代码行
        + " extract(DOW FROM DATE '2019-02-10'),\n" // sun=1 // 代码行
        + " extract(ISODOW FROM DATE '2019-02-09'),\n" // sat=6 // 代码行
        + " extract(ISODOW FROM DATE '2019-02-10')\n" // sun=7 // 代码行
        + ")) AS t(t0, t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11, t12, t13)"; // 代码行
    Stream.of(TestLocale.values()).forEach(t -> { // 代码行
      try { // 代码行
        CalciteAssert.that() // 代码行
            .with(CalciteConnectionProperty.LOCALE, t.localeName) // 代码行
            .with(CalciteConnectionProperty.FUN, "mysql") // 代码行
            .doWithConnection(connection -> { // 代码行
              try (Statement statement = connection.createStatement()) { // 代码行
                try (ResultSet rs = statement.executeQuery(sql)) { // 代码行
                  assertThat(rs.next(), is(true)); // 代码行
                  assertThat(rs.getString(1), is(t.january)); // 代码行
                  assertThat(rs.getString(2), is(t.january)); // 代码行
                  assertThat(rs.getString(3), is(t.february)); // 代码行
                  assertThat(rs.getString(4), is(t.february)); // 代码行
                  assertThat(rs.getString(5), is(t.wednesday)); // 代码行
                  assertThat(rs.getString(6), is(t.wednesday)); // 代码行
                  assertThat(rs.getString(7), is(t.sunday)); // 代码行
                  assertThat(rs.getString(8), is(t.sunday)); // 代码行
                  assertThat(rs.getInt(9), is(7)); // 代码行
                  assertThat(rs.getInt(10), is(1)); // 代码行
                  assertThat(rs.getInt(11), is(7)); // 代码行
                  assertThat(rs.getInt(12), is(1)); // 代码行
                  assertThat(rs.getInt(13), is(6)); // 代码行
                  assertThat(rs.getInt(14), is(7)); // 代码行
                  assertThat(rs.next(), is(false)); // 代码行
                } // 代码行
              } catch (SQLException e) { // 代码行
                throw TestUtil.rethrow(e); // 代码行
              } // 代码行
            }); // 代码行
      } catch (Exception e) { // 代码行
        System.out.println(t.localeName + ":" + Locale.getDefault().toString()); // 代码行
        throw TestUtil.rethrow(e); // 代码行
      } // 代码行
    }); // 代码行
  } // 代码行
 // 空行
  /** Tests accessing a column in a JDBC source whose type is DATE. */ // JavaDoc注释开始
  @Test void testGetDate() throws Exception { // 测试方法:测试GetDate功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            Statement stmt = connection.createStatement(); // 代码行
            ResultSet rs = // 代码行
                stmt.executeQuery("select min(\"date\") mindate\n" // 代码行
                    + "from \"foodmart\".\"currency\""); // 代码行
            assertTrue(rs.next()); // 代码行
            assertThat(rs.getDate(1), is(Date.valueOf("1997-01-01"))); // 代码行
            assertFalse(rs.next()); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests accessing a date as a string in a JDBC source whose type is DATE. */ // JavaDoc注释开始
  @Test void testGetDateAsString() { // 测试方法:测试GetDateAsString功能
    CalciteAssert.that() // 代码行
      .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
      .query("select min(\"date\") mindate from \"foodmart\".\"currency\"") // 代码行
      .returns2("MINDATE=1997-01-01\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testGetTimestampObject() throws Exception { // 测试方法:测试GetTimestampObject功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            Statement stmt = connection.createStatement(); // 代码行
            ResultSet rs = // 代码行
                stmt.executeQuery("select \"hire_date\"\n" // 代码行
                    + "from \"foodmart\".\"employee\"\n" // 代码行
                    + "where \"employee_id\" = 1"); // 代码行
            assertTrue(rs.next()); // 代码行
            assertThat(rs.getTimestamp(1), // 代码行
                is(Timestamp.valueOf("1994-12-01 00:00:00"))); // 代码行
            assertFalse(rs.next()); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  @Test void testRowComparison() { // 测试方法:测试RowComparison功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_SCOTT) // 代码行
        // The extra casts are necessary because HSQLDB does not support a ROW type, // 单行注释
        // and in the absence of these explicit casts the code generated contains // 单行注释
        // a cast of a ROW value.  The correct way to fix this would be to improve // 单行注释
        // the code generation for HSQLDB to expand suc casts into constructs // 单行注释
        // supported by HSQLDB. // 单行注释
        .query("SELECT empno FROM JDBC_SCOTT.emp WHERE (ename, job) < " // 代码行
            + "(CAST('Blake' AS VARCHAR(10)), CAST('Manager' AS VARCHAR(9)))") // 代码行
        .returnsUnordered("EMPNO=7876", "EMPNO=7499", "EMPNO=7698"); // 代码行
  } // 代码行
 // 空行
  @Test void testTimestampEqualsComparison() { // 测试方法:测试TimestampEqualsComparison功能
    CalciteAssert.that() // 代码行
        .query("select time0 = time1, time0 <> time1" // 代码行
            + " from (" // 代码行
            + "  select timestamp'2000-12-30 21:07:32'as time0," // 代码行
            + "         timestamp'2000-12-30 21:07:32'as time1 " // 代码行
            + "  union all" // 代码行
            + "  select cast(null as timestamp) as time0," // 代码行
            + "         cast(null as timestamp) as time1" // 代码行
            + ") calcs") // 代码行
        .returns("EXPR$0=true; EXPR$1=false\n" // 代码行
            + "EXPR$0=null; EXPR$1=null\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnicode() { // 测试方法:测试Unicode功能
    CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that().with(CalciteAssert.Config.FOODMART_CLONE); // 代码行
 // 空行
    // Note that \u82f1 in a Java string is a Java unicode escape; // 单行注释
    // But \\82f1 in a SQL string is a SQL unicode escape. // 单行注释
    // various ways to create a unicode string literal // 单行注释
    with.query("values _UTF16'\u82f1\u56fd'") // 代码行
        .returns("EXPR$0=\u82f1\u56fd\n"); // 代码行
    with.query("values U&'\\82F1\\56FD'") // 代码行
        .returns("EXPR$0=\u82f1\u56fd\n"); // 代码行
    with.query("values u&'\\82f1\\56fd'") // 代码行
        .returns("EXPR$0=\u82f1\u56fd\n"); // 代码行
    with.query("values '\u82f1\u56fd'") // 代码行
        .throws_( // 代码行
            "Failed to encode '\u82f1\u56fd' in character set 'ISO-8859-1'"); // 代码行
 // 空行
    // comparing a unicode string literal with a regular string literal // 单行注释
    with.query( // 代码行
        "select * from \"employee\" where \"full_name\" = '\u82f1\u56fd'") // 代码行
        .throws_( // 代码行
            "Failed to encode '\u82f1\u56fd' in character set 'ISO-8859-1'"); // 代码行
    with.query( // 代码行
        "select * from \"employee\" where \"full_name\" = _UTF16'\u82f1\u56fd'") // 代码行
        .throws_( // 代码行
            "Cannot apply operation '=' to strings with different charsets 'ISO-8859-1' and 'UTF-16LE'"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6146">[CALCITE-6146] // JavaDoc注释内容
   * Target charset should be used when comparing two strings through // JavaDoc注释内容
   * CONVERT/TRANSLATE function during validation</a>. */ // JavaDoc注释内容
  @Test void testStringComparisonWithConvertFunc() { // 测试方法:测试StringComparisonWithConvertFunc功能
    CalciteAssert.AssertThat with = CalciteAssert.hr(); // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where convert(\"name\" using GBK)=_GBK'Eric'") // 代码行
        .returns("name=Eric; empid=200\n"); // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where _BIG5'Eric'=translate(\"name\" using BIG5)") // 代码行
        .returns("name=Eric; empid=200\n"); // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where translate(null using UTF8) is null") // 代码行
        .returns("name=Bill; empid=100\n" // 代码行
                + "name=Eric; empid=200\n" // 代码行
                + "name=Sebastian; empid=150\n" // 代码行
                + "name=Theodore; empid=110\n"); // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where _BIG5'Eric'=translate(\"name\" using LATIN1)") // 代码行
        .throws_("Cannot apply operation '=' to strings with " // 代码行
                + "different charsets 'Big5' and 'ISO-8859-1'"); // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where convert(convert(\"name\" using GBK) using BIG5)=_BIG5'Eric'") // 代码行
        .returns("name=Eric; empid=200\n"); // 代码行
 // 空行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where convert(\"name\", UTF8, GBK)=_GBK'Sebastian'") // 代码行
        .returns("name=Sebastian; empid=150\n"); // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where _BIG5'Sebastian'=convert(\"name\", UTF8, BIG5)") // 代码行
        .returns("name=Sebastian; empid=150\n"); // 代码行
 // 空行
    // check cast // 单行注释
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where cast(convert(\"name\" using LATIN1) as char(5))='Eric'") // 代码行
        .returns("name=Eric; empid=200\n"); // 代码行
    // the result of convert(\"name\" using GBK) has GBK charset // 单行注释
    // while CHAR(5) has ISO-8859-1 charset, which is not allowed to cast // 单行注释
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
          + "where cast(convert(\"name\" using GBK) as char(5))='Eric'") // 代码行
        .throws_( // 代码行
            "cannot convert value of type " // 代码行
            + "JavaType(class java.lang.String CHARACTER SET \"GBK\") to type CHAR(5) NOT NULL"); // 代码行
  } // 代码行
 // 空行
  /** Tests metadata for the MySQL lexical scheme. */ // JavaDoc注释开始
  @Test void testLexMySQL() throws Exception { // 测试方法:测试LexMySQL功能
    CalciteAssert.that() // 代码行
        .with(Lex.MYSQL) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            DatabaseMetaData metaData = connection.getMetaData(); // 代码行
            assertThat(metaData.getIdentifierQuoteString(), is("`")); // 代码行
            assertThat(metaData.supportsMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.supportsMixedCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseQuotedIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests metadata for the MySQL ANSI lexical scheme. */ // JavaDoc注释开始
  @Test void testLexMySQLANSI() throws Exception { // 测试方法:测试LexMySQLANSI功能
    CalciteAssert.that() // 代码行
        .with(Lex.MYSQL_ANSI) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            DatabaseMetaData metaData = connection.getMetaData(); // 代码行
            assertThat(metaData.getIdentifierQuoteString(), is("\"")); // 代码行
            assertThat(metaData.supportsMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.supportsMixedCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseQuotedIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests metadata for different the "SQL_SERVER" lexical scheme. */ // JavaDoc注释开始
  @Test void testLexSqlServer() throws Exception { // 测试方法:测试LexSqlServer功能
    CalciteAssert.that() // 代码行
        .with(Lex.SQL_SERVER) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            DatabaseMetaData metaData = connection.getMetaData(); // 代码行
            assertThat(metaData.getIdentifierQuoteString(), is("[")); // 代码行
            assertThat(metaData.supportsMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.supportsMixedCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseQuotedIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests metadata for the ORACLE (and default) lexical scheme. */ // JavaDoc注释开始
  @Test void testLexOracle() throws Exception { // 测试方法:测试LexOracle功能
    CalciteAssert.that() // 代码行
        .with(Lex.ORACLE) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            DatabaseMetaData metaData = connection.getMetaData(); // 代码行
            assertThat(metaData.getIdentifierQuoteString(), // 代码行
                is("\"")); // 代码行
            assertThat(metaData.supportsMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesLowerCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.supportsMixedCaseQuotedIdentifiers(), // 代码行
                is(true)); // 代码行
            // Oracle JDBC 12.1.0.1.0 returns true here, however it is // 单行注释
            // not clear if the bug is in JDBC specification or Oracle // 单行注释
            // driver // 单行注释
            assertThat(metaData.storesMixedCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesUpperCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests metadata for the JAVA lexical scheme. */ // JavaDoc注释开始
  @Test void testLexJava() throws Exception { // 测试方法:测试LexJava功能
    CalciteAssert.that() // 代码行
        .with(Lex.JAVA) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            DatabaseMetaData metaData = connection.getMetaData(); // 代码行
            assertThat(metaData.getIdentifierQuoteString(), // 代码行
                is("`")); // 代码行
            assertThat(metaData.supportsMixedCaseIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.supportsMixedCaseQuotedIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesMixedCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesUpperCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests metadata for the ORACLE lexical scheme overridden like JAVA. */ // JavaDoc注释开始
  @Test void testLexOracleAsJava() throws Exception { // 测试方法:测试LexOracleAsJava功能
    CalciteAssert.that() // 代码行
        .with(Lex.ORACLE) // 代码行
        .with(CalciteConnectionProperty.QUOTING, Quoting.BACK_TICK) // 代码行
        .with(CalciteConnectionProperty.UNQUOTED_CASING, Casing.UNCHANGED) // 代码行
        .with(CalciteConnectionProperty.QUOTED_CASING, Casing.UNCHANGED) // 代码行
        .with(CalciteConnectionProperty.CASE_SENSITIVE, true) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            DatabaseMetaData metaData = connection.getMetaData(); // 代码行
            assertThat(metaData.getIdentifierQuoteString(), // 代码行
                is("`")); // 代码行
            assertThat(metaData.supportsMixedCaseIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.supportsMixedCaseQuotedIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesMixedCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesUpperCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests metadata for the BigQuery lexical scheme. */ // JavaDoc注释开始
  @Test void testLexBigQuery() throws Exception { // 测试方法:测试LexBigQuery功能
    CalciteAssert.that() // 代码行
        .with(Lex.BIG_QUERY) // 代码行
        .doWithConnection(connection -> { // 代码行
          try { // 代码行
            DatabaseMetaData metaData = connection.getMetaData(); // 代码行
            assertThat(metaData.getIdentifierQuoteString(), is("`")); // 代码行
            assertThat(metaData.supportsMixedCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.supportsMixedCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesMixedCaseQuotedIdentifiers(), // 代码行
                is(true)); // 代码行
            assertThat(metaData.storesUpperCaseIdentifiers(), // 代码行
                is(false)); // 代码行
            assertThat(metaData.storesLowerCaseQuotedIdentifiers(), // 代码行
                is(false)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Tests case-insensitive resolution of schema and table names. */ // JavaDoc注释开始
  @Test void testLexCaseInsensitive() { // 测试方法:测试LexCaseInsensitive功能
    final CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that().with(Lex.MYSQL); // 代码行
    with.query("select COUNT(*) as c from metaData.tAbles") // 代码行
        .returns("c=2\n"); // 代码行
    with.query("select COUNT(*) as c from `metaData`.`tAbles`") // 代码行
        .returns("c=2\n"); // 代码行
 // 空行
    // case-sensitive gives error // 单行注释
    final CalciteAssert.AssertThat with2 = // 代码行
        CalciteAssert.that().with(Lex.JAVA); // 代码行
    with2.query("select COUNT(*) as c from `metaData`.`tAbles`") // 代码行
        .throws_("Object 'metaData' not found; did you mean 'metadata'?"); // 代码行
    with2.query("select COUNT(*) as c from `metaData`.`TABLES`") // 代码行
        .throws_("Object 'metaData' not found; did you mean 'metadata'?"); // 代码行
    with2.query("select COUNT(*) as c from `metaData`.`tables`") // 代码行
        .throws_("Object 'metaData' not found; did you mean 'metadata'?"); // 代码行
    with2.query("select COUNT(*) as c from `metaData`.`nonExistent`") // 代码行
        .throws_("Object 'metaData' not found; did you mean 'metadata'?"); // 代码行
    with2.query("select COUNT(*) as c from `metadata`.`tAbles`") // 代码行
        .throws_("Object 'tAbles' not found within 'metadata'; did you mean 'TABLES'?"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1563">[CALCITE-1563] // JavaDoc注释内容
   * In case-insensitive connection, non-existent tables use alphabetically // JavaDoc注释内容
   * preceding table</a>. */ // JavaDoc注释内容
  @Test void testLexCaseInsensitiveFindsNonexistentTable() { // 测试方法:测试LexCaseInsensitiveFindsNonexistentTable功能
    final CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that().with(Lex.MYSQL); // 代码行
    // With [CALCITE-1563], the following query succeeded; it queried // 单行注释
    // metadata.tables. // 单行注释
    with.query("select COUNT(*) as c from `metaData`.`zoo`") // 代码行
        .throws_("Object 'zoo' not found within 'metadata'"); // 代码行
    with.query("select COUNT(*) as c from `metaData`.`tAbLes`") // 代码行
        .returns("c=2\n"); // 代码行
  } // 代码行
 // 空行
  /** Tests case-insensitive resolution of sub-query columns. // JavaDoc注释开始
   * // JavaDoc注释内容
   * <p>Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-550">[CALCITE-550] // JavaDoc注释内容
   * Case-insensitive matching of sub-query columns fails</a>. */ // JavaDoc注释内容
  @Test void testLexCaseInsensitiveSubQueryField() { // 测试方法:测试LexCaseInsensitiveSubQueryField功能
    CalciteAssert.that() // 代码行
        .with(Lex.MYSQL) // 代码行
        .query("select DID\n" // 代码行
            + "from (select deptid as did\n" // 代码行
            + "         FROM\n" // 代码行
            + "            ( values (1), (2) ) as T1(deptid)\n" // 代码行
            + "         ) ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testLexCaseInsensitiveTableAlias() { // 测试方法:测试LexCaseInsensitiveTableAlias功能
    CalciteAssert.that() // 代码行
        .with(Lex.MYSQL) // 代码行
        .query("select e.empno\n" // 代码行
            + "from (values (1, 2)) as E (empno, deptno),\n" // 代码行
            + "  (values (3, 4)) as d (deptno, name)") // 代码行
        .returnsUnordered("empno=1"); // 代码行
  } // 代码行
 // 空行
  @Test void testFunOracle() { // 测试方法:测试FunOracle功能
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "oracle") // 代码行
        .query("select nvl(\"commission\", -99) as c from \"hr\".\"emps\"") // 代码行
        .returnsUnordered("C=-99", // 代码行
            "C=1000", // 代码行
            "C=250", // 代码行
            "C=500"); // 代码行
 // 空行
    // NVL is not present in the default operator table // 单行注释
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .query("select nvl(\"commission\", -99) as c from \"hr\".\"emps\"") // 代码行
        .throws_("No match found for function signature NVL(<NUMERIC>, <NUMERIC>)"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6730">[CALCITE-6730] // JavaDoc注释内容
   * Add CONVERT function(enabled in Oracle library)</a>. */ // JavaDoc注释内容
  @Test void testConvertOracle() { // 测试方法:测试ConvertOracle功能
    CalciteAssert.AssertThat withOracle10 = // 代码行
        CalciteAssert.hr() // 代码行
            .with(SqlConformanceEnum.ORACLE_10); // 代码行
    testConvertOracleInternal(withOracle10); // 代码行
 // 空行
    CalciteAssert.AssertThat withOracle12 // 代码行
        = withOracle10.with(SqlConformanceEnum.ORACLE_12); // 代码行
    testConvertOracleInternal(withOracle12); // 代码行
  } // 代码行
 // 空行
  private void testConvertOracleInternal(CalciteAssert.AssertThat with) { // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
            + "where convert(\"name\", GBK)=_GBK'Eric'") // 代码行
        .returns("name=Eric; empid=200\n"); // 代码行
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
            + "where _BIG5'Eric'=convert(\"name\", LATIN1)") // 代码行
        .throws_("Cannot apply operation '=' to strings with " // 代码行
            + "different charsets 'Big5' and 'ISO-8859-1'"); // 代码行
    // use LATIN1 as dest charset, not BIG5 // 单行注释
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
            + "where _BIG5'Eric'=convert(\"name\", LATIN1, BIG5)") // 代码行
        .throws_("Cannot apply operation '=' to strings with " // 代码行
            + "different charsets 'Big5' and 'ISO-8859-1'"); // 代码行
 // 空行
    // check cast // 单行注释
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
            + "where cast(convert(\"name\", LATIN1, UTF8) as varchar)='Eric'") // 代码行
        .returns("name=Eric; empid=200\n"); // 代码行
    // the result of convert(\"name\", GBK) has GBK charset // 单行注释
    // while CHAR(5) has ISO-8859-1 charset, which is not allowed to cast // 单行注释
    with.query("select \"name\", \"empid\" from \"hr\".\"emps\"\n" // 代码行
            + "where cast(convert(\"name\", GBK) as varchar)='Eric'") // 代码行
        .throws_( // 代码行
            "cannot convert value of type " // 代码行
                + "JavaType(class java.lang.String CHARACTER SET \"GBK\") to type VARCHAR NOT NULL"); // 代码行
  } // 代码行
 // 空行
  @Test void testIf() { // 测试方法:测试If功能
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "bigquery") // 代码行
        .query("select if(1 = 1,1,2) as r") // 代码行
        .returnsUnordered("R=1"); // 代码行
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "hive") // 代码行
        .query("select if(1 = 1,1,2) as r") // 代码行
        .returnsUnordered("R=1"); // 代码行
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "spark") // 代码行
        .query("select if(1 = 1,1,2) as r") // 代码行
        .returnsUnordered("R=1"); // 代码行
  } // 代码行
 // 空行
  @Test void testIfWithExpression() { // 测试方法:测试IfWithExpression功能
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "bigquery") // 代码行
        .query("select if(TRIM('a ') = 'a','a','b') as r") // 代码行
        .returnsUnordered("R=a"); // 代码行
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "hive") // 代码行
        .query("select if(TRIM('a ') = 'a','a','b') as r") // 代码行
        .returnsUnordered("R=a"); // 代码行
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "spark") // 代码行
        .query("select if(TRIM('a ') = 'a','a','b') as r") // 代码行
        .returnsUnordered("R=a"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2072">[CALCITE-2072] // JavaDoc注释内容
   * Enable spatial operator table by adding 'fun=spatial'to JDBC URL</a>. */ // JavaDoc注释内容
  @Test void testFunSpatial() { // 测试方法:测试FunSpatial功能
    final String sql = "select distinct\n" // 代码行
        + "  ST_PointFromText('POINT(-71.0642 .28)') as c\n" // 代码行
        + "from \"hr\".\"emps\""; // 代码行
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .with(CalciteConnectionProperty.FUN, "spatial") // 代码行
        .query(sql) // 代码行
        .returnsUnordered("C=POINT (-71.0642 0.28)"); // 代码行
 // 空行
    // NVL is present in the Oracle operator table, but not spatial or core // 单行注释
    CalciteAssert.that(CalciteAssert.Config.REGULAR) // 代码行
        .query("select nvl(\"commission\", -99) as c from \"hr\".\"emps\"") // 代码行
        .throws_("No match found for function signature NVL(<NUMERIC>, <NUMERIC>)"); // 代码行
  } // 代码行
 // 空行
  /** Unit test for LATERAL CROSS JOIN to table function. */ // JavaDoc注释开始
  @Test void testLateralJoin() { // 测试方法:测试LateralJoin功能
    final String sql = "SELECT *\n" // 代码行
        + "FROM AUX.SIMPLETABLE ST\n" // 代码行
        + "CROSS JOIN LATERAL TABLE(AUX.TBLFUN(ST.INTCOL))"; // 代码行
    CalciteAssert.that(CalciteAssert.Config.AUX) // 代码行
        .query(sql) // 代码行
        .returnsUnordered( // 代码行
            "STRCOL=ABC; INTCOL=1; n=0; s=", // 代码行
            "STRCOL=DEF; INTCOL=2; n=0; s=", // 代码行
            "STRCOL=DEF; INTCOL=2; n=1; s=a", // 代码行
            "STRCOL=GHI; INTCOL=3; n=0; s=", // 代码行
            "STRCOL=GHI; INTCOL=3; n=1; s=a", // 代码行
            "STRCOL=GHI; INTCOL=3; n=2; s=ab"); // 代码行
  } // 代码行
 // 空行
  /** Unit test for view expansion with lateral join. */ // JavaDoc注释开始
  @Test void testExpandViewWithLateralJoin() { // 测试方法:测试ExpandViewWithLateralJoin功能
    final String sql = "SELECT * FROM AUX.VIEWLATERAL"; // 代码行
    CalciteAssert.that(CalciteAssert.Config.AUX) // 代码行
        .query(sql) // 代码行
        .returnsUnordered( // 代码行
            "STRCOL=ABC; INTCOL=1; n=0; s=", // 代码行
            "STRCOL=DEF; INTCOL=2; n=0; s=", // 代码行
            "STRCOL=DEF; INTCOL=2; n=1; s=a", // 代码行
            "STRCOL=GHI; INTCOL=3; n=0; s=", // 代码行
            "STRCOL=GHI; INTCOL=3; n=1; s=a", // 代码行
            "STRCOL=GHI; INTCOL=3; n=2; s=ab"); // 代码行
  } // 代码行
 // 空行
  /** Tests that {@link Hook#PARSE_TREE} works. */ // JavaDoc注释开始
  @Test void testHook() { // 测试方法:测试Hook功能
    final int[] callCount = {0}; // 代码行
    try (Hook.Closeable ignored = // 代码行
             Hook.PARSE_TREE.<Object[]>addThread(args -> { // 代码行
               assertThat(args, arrayWithSize(2)); // 代码行
               assertThat(args[0], instanceOf(String.class)); // 代码行
               assertThat(args[0], // 代码行
                   is("select \"deptno\", \"commission\", sum(\"salary\") s\n" // 代码行
                       + "from \"hr\".\"emps\"\n" // 代码行
                       + "group by \"deptno\", \"commission\"")); // 代码行
               assertThat(args[1], instanceOf(SqlSelect.class)); // 代码行
               ++callCount[0]; // 代码行
             })) { // 代码行
      // Simple query does not run the hook. // 单行注释
      testSimple(); // 代码行
      assertThat(callCount[0], is(0)); // 代码行
 // 空行
      // Non-trivial query runs hook once. // 单行注释
      testGroupByNull(); // 代码行
      assertThat(callCount[0], is(1)); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Tests {@link SqlDialect}. */ // JavaDoc注释开始
  @Test void testDialect() { // 测试方法:测试Dialect功能
    final String[] sqls = {null}; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select count(*) as c from \"foodmart\".\"employee\" as e1\n" // 代码行
            + "  where \"first_name\" = 'abcde'\n" // 代码行
            + "  and \"gender\" = 'F'") // 代码行
        .withHook(Hook.QUERY_PLAN, (Consumer<String>) sql -> sqls[0] = sql) // 代码行
        .returns("C=0\n"); // 代码行
    switch (CalciteAssert.DB) { // 代码行
    case HSQLDB: // 代码行
      assertThat(sqls[0], // 代码行
          isLinux("SELECT COUNT(*) AS \"C\"\n" // 代码行
              + "FROM \"foodmart\".\"employee\"\n" // 代码行
              + "WHERE \"first_name\" = 'abcde' AND \"gender\" = 'F'")); // 代码行
      break; // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void testExplicitImplicitSchemaSameName() { // 测试方法:测试ExplicitImplicitSchemaSameName功能
    final SchemaPlus rootSchema = CalciteSchema.createRootSchema(false).plus(); // 代码行
 // 空行
    // create schema "/a" // 单行注释
    final Map<String, Schema> aSubSchemaMap = new HashMap<>(); // 代码行
    final SchemaPlus aSchema = // 代码行
        rootSchema.add("a", new AbstractSchema() { // 代码行
          @Override protected Map<String, Schema> getSubSchemaMap() { // 注解
            return aSubSchemaMap; // 代码行
          } // 代码行
        }); // 代码行
 // 空行
    // add explicit schema "/a/b". // 单行注释
    aSchema.add("b", new AbstractSchema()); // 代码行
 // 空行
    // add implicit schema "/a/b" // 单行注释
    aSubSchemaMap.put("b", new AbstractSchema()); // 代码行
 // 空行
    aSchema.setCacheEnabled(true); // 代码行
 // 空行
    // explicit should win implicit. // 单行注释
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(1)); // 代码行
  } // 代码行
 // 空行
  @Test void testSimpleCalciteSchema() { // 测试方法:测试SimpleCalciteSchema功能
    final SchemaPlus rootSchema = CalciteSchema.createRootSchema(false, false).plus(); // 代码行
 // 空行
    // create schema "/a" // 单行注释
    final Map<String, Schema> aSubSchemaMap = new HashMap<>(); // 代码行
    final SchemaPlus aSchema = // 代码行
        rootSchema.add("a", new AbstractSchema() { // 代码行
          @Override protected Map<String, Schema> getSubSchemaMap() { // 注解
            return aSubSchemaMap; // 代码行
          } // 代码行
        }); // 代码行
 // 空行
    // add explicit schema "/a/b". // 单行注释
    aSchema.add("b", new AbstractSchema()); // 代码行
 // 空行
    // add implicit schema "/a/c" // 单行注释
    aSubSchemaMap.put("c", new AbstractSchema()); // 代码行
 // 空行
    assertThat(aSchema.subSchemas().get("c"), notNullValue()); // 代码行
    assertThat(aSchema.subSchemas().get("b"), notNullValue()); // 代码行
 // 空行
    // add implicit schema "/a/b" // 单行注释
    aSubSchemaMap.put("b", new AbstractSchema()); // 代码行
    // explicit should win implicit. // 单行注释
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(2)); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6903">[CALCITE-6903] // JavaDoc注释内容
   * CalciteSchema#getSubSchemaMap must consider implicit sub-schemas</a>. */ // JavaDoc注释内容
  @Test void testCalciteSchemaGetSubSchemaMapCache() { // 测试方法:测试CalciteSchemaGetSubSchemaMapCache功能
    checkCalciteSchemaGetSubSchemaMap(true); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6903">[CALCITE-6903] // JavaDoc注释内容
   * CalciteSchema#getSubSchemaMap must consider implicit sub-schemas</a>. */ // JavaDoc注释内容
  @Test void testCalciteSchemaGetSubSchemaMapNoCache() { // 测试方法:测试CalciteSchemaGetSubSchemaMapNoCache功能
    checkCalciteSchemaGetSubSchemaMap(false); // 代码行
  } // 代码行
 // 空行
  void checkCalciteSchemaGetSubSchemaMap(boolean cache) { // checkCalciteSchemaGetSubSchemaMap方法:检查CalciteSchema的getSubSchemaMap
    final CalciteSchema calciteSchema = CalciteSchema.createRootSchema(false, cache); // 代码行
 // 空行
    // create schema "/a" // 单行注释
    final Map<String, Schema> aSubSchemaMap = new HashMap<>(); // 代码行
    final CalciteSchema aSchema = // 代码行
        calciteSchema.add("a", new AbstractSchema() { // 代码行
          @Override protected Map<String, Schema> getSubSchemaMap() { // 注解
            return aSubSchemaMap; // 代码行
          } // 代码行
        }); // 代码行
 // 空行
    // add explicit schema "/a/b". // 单行注释
    aSchema.add("b", new AbstractSchema()); // 代码行
 // 空行
    // add implicit schema "/a/c" // 单行注释
    aSubSchemaMap.put("c", new AbstractSchema()); // 代码行
 // 空行
    assertThat(aSchema.subSchemas().get("c"), notNullValue()); // 代码行
    assertThat(aSchema.subSchemas().get("b"), notNullValue()); // 代码行
 // 空行
    final Map<String, CalciteSchema> subSchemaMap = aSchema.getSubSchemaMap(); // 代码行
    assertThat(subSchemaMap.values(), hasSize(2)); // 代码行
    assertThat(subSchemaMap.get("c"), notNullValue()); // 代码行
    assertThat(subSchemaMap.get("b"), notNullValue()); // 代码行
  } // 代码行
 // 空行
  @Test void testCaseSensitiveConfigurableSimpleCalciteSchema() { // 测试方法:测试CaseSensitiveConfigurableSimpleCalciteSchema功能
    final SchemaPlus rootSchema = CalciteSchema.createRootSchema(false, false).plus(); // 代码行
    // create schema "/a" // 单行注释
    final Map<String, Schema> dummySubSchemaMap = new HashMap<>(); // 代码行
    final Map<String, Table> dummyTableMap = new HashMap<>(); // 代码行
    final Map<String, RelProtoDataType> dummyTypeMap = new HashMap<>(); // 代码行
    final SchemaPlus dummySchema = // 代码行
        rootSchema.add("dummy", new AbstractSchema() { // 代码行
          @Override protected Map<String, Schema> getSubSchemaMap() { // 注解
            return dummySubSchemaMap; // 代码行
          } // 代码行
 // 空行
          @Override protected Map<String, Table> getTableMap() { // 注解
            return dummyTableMap; // 代码行
          } // 代码行
 // 空行
          @Override protected Map<String, RelProtoDataType> getTypeMap() { // 注解
            return dummyTypeMap; // 代码行
          } // 代码行
        }); // 代码行
    // add implicit schema "/dummy/abc" // 单行注释
    dummySubSchemaMap.put("abc", new AbstractSchema()); // 代码行
    // add implicit table "/dummy/xyz" // 单行注释
    dummyTableMap.put("xyz", new AbstractTable() { // 代码行
      @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 注解
        throw new UnsupportedOperationException("getRowType"); // 代码行
      } // 代码行
    }); // 代码行
    // add implicit table "/dummy/myType" // 单行注释
    dummyTypeMap.put("myType", factory -> factory.builder().build()); // 代码行
 // 空行
    final CalciteSchema dummyCalciteSchema = CalciteSchema.from(dummySchema); // 代码行
    assertThat(dummyCalciteSchema.getSubSchema("abc", true), notNullValue()); // 代码行
    assertThat(dummyCalciteSchema.getSubSchema("aBC", false), notNullValue()); // 代码行
    assertThat(dummyCalciteSchema.getSubSchema("aBC", true), nullValue()); // 代码行
    assertThat(dummyCalciteSchema.getTable("xyz", true), notNullValue()); // 代码行
    assertThat(dummyCalciteSchema.getTable("XyZ", false), notNullValue()); // 代码行
    assertThat(dummyCalciteSchema.getTable("XyZ", true), nullValue()); // 代码行
    assertThat(dummyCalciteSchema.getType("myType", true), notNullValue()); // 代码行
    assertThat(dummyCalciteSchema.getType("MytYpE", false), notNullValue()); // 代码行
    assertThat(dummyCalciteSchema.getType("MytYpE", true), nullValue()); // 代码行
  } // 代码行
 // 空行
  @Test void testSimpleCalciteSchemaWithView() { // 测试方法:测试SimpleCalciteSchemaWithView功能
    final SchemaPlus rootSchema = CalciteSchema.createRootSchema(false, false).plus(); // 代码行
 // 空行
    final Multimap<String, org.apache.calcite.schema.Function> functionMap = // 代码行
        LinkedListMultimap.create(); // 代码行
    // create schema "/a" // 单行注释
    final SchemaPlus aSchema = // 代码行
        rootSchema.add("a", new AbstractSchema() { // 代码行
          @Override protected Multimap<String, org.apache.calcite.schema.Function> // 注解
          getFunctionMultimap() { // 代码行
            return functionMap; // 代码行
          } // 代码行
        }); // 代码行
    // add view definition // 单行注释
    final String viewName = "V"; // 代码行
    final SchemaPlus a = rootSchema.subSchemas().get("a"); // 代码行
    assertThat(a, notNullValue()); // 代码行
    final org.apache.calcite.schema.Function view = // 代码行
        ViewTable.viewMacro(a, // 代码行
            "values('1', '2')", ImmutableList.of(), null, false); // 代码行
    functionMap.put(viewName, view); // 代码行
 // 空行
    final CalciteSchema calciteSchema = CalciteSchema.from(aSchema); // 代码行
    assertThat( // 代码行
        calciteSchema.getTableBasedOnNullaryFunction(viewName, true), notNullValue()); // 代码行
    assertThat( // 代码行
        calciteSchema.getTableBasedOnNullaryFunction(viewName, false), notNullValue()); // 代码行
    assertThat( // 代码行
        calciteSchema.getTableBasedOnNullaryFunction("V1", true), nullValue()); // 代码行
    assertThat( // 代码行
        calciteSchema.getTableBasedOnNullaryFunction("V1", false), nullValue()); // 代码行
 // 空行
    assertThat(calciteSchema.getFunctions(viewName, true), hasItem(view)); // 代码行
    assertThat(calciteSchema.getFunctions(viewName, false), hasItem(view)); // 代码行
    assertThat(calciteSchema.getFunctions("V1", true), not(hasItem(view))); // 代码行
    assertThat(calciteSchema.getFunctions("V1", false), not(hasItem(view))); // 代码行
  } // 代码行
 // 空行
  @Test void testSchemaCaching() throws Exception { // 测试方法:测试SchemaCaching功能
    final Connection connection = // 代码行
        CalciteAssert.that(CalciteAssert.Config.JDBC_FOODMART).connect(); // 代码行
    final CalciteConnection calciteConnection = // 代码行
        connection.unwrap(CalciteConnection.class); // 代码行
    final SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 代码行
 // 空行
    // create schema "/a" // 单行注释
    final Map<String, Schema> aSubSchemaMap = new HashMap<>(); // 代码行
    final SchemaPlus aSchema = rootSchema.add("a", new AbstractSchema() { // 代码行
      @Override protected Map<String, Schema> getSubSchemaMap() { // 注解
        return aSubSchemaMap; // 代码行
      } // 代码行
    }); // 代码行
    aSchema.setCacheEnabled(true); // 代码行
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(0)); // 代码行
 // 空行
    // first call, to populate the cache // 单行注释
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(0)); // 代码行
 // 空行
    // create schema "/a/b1". Appears only when we disable caching. // 单行注释
    aSubSchemaMap.put("b1", new AbstractSchema()); // 代码行
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(0)); // 代码行
    assertThat(aSchema.subSchemas().get("b1"), nullValue()); // 代码行
    aSchema.setCacheEnabled(false); // 代码行
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(1)); // 代码行
    assertThat(aSchema.subSchemas().get("b1"), notNullValue()); // 代码行
 // 空行
    // create schema "/a/b2". Appears immediately, because caching is disabled. // 单行注释
    aSubSchemaMap.put("b2", new AbstractSchema()); // 代码行
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(2)); // 代码行
 // 空行
    // an explicit sub-schema appears immediately, even if caching is enabled // 单行注释
    aSchema.setCacheEnabled(true); // 代码行
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(2)); // 代码行
    aSchema.add("b3", new AbstractSchema()); // explicit // 代码行
    aSubSchemaMap.put("b4", new AbstractSchema()); // implicit // 代码行
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(3)); // 代码行
    aSchema.setCacheEnabled(false); // 代码行
    assertThat(aSchema.subSchemas().getNames(LikePattern.any()), hasSize(4)); // 代码行
    for (String name : aSchema.subSchemas().getNames(LikePattern.any())) { // 代码行
      assertThat(aSchema.subSchemas().get(name), notNullValue()); // 代码行
    } // 代码行
 // 空行
    // create schema "/a2" // 单行注释
    final Map<String, Schema> a2SubSchemaMap = new HashMap<>(); // 代码行
    final SchemaPlus a2Schema = rootSchema.add("a", new AbstractSchema() { // 代码行
      @Override protected Map<String, Schema> getSubSchemaMap() { // 注解
        return a2SubSchemaMap; // 代码行
      } // 代码行
    }); // 代码行
    a2Schema.setCacheEnabled(true); // 代码行
    assertThat(a2Schema.subSchemas().getNames(LikePattern.any()), hasSize(0)); // 代码行
 // 空行
    // create schema "/a2/b3". Change not visible since caching is enabled. // 单行注释
    a2SubSchemaMap.put("b3", new AbstractSchema()); // 代码行
    assertThat(a2Schema.subSchemas().getNames(LikePattern.any()), hasSize(0)); // 代码行
    Thread.sleep(1); // 代码行
    assertThat(a2Schema.subSchemas().getNames(LikePattern.any()), hasSize(0)); // 代码行
 // 空行
    // Change visible after we turn off caching. // 单行注释
    a2Schema.setCacheEnabled(false); // 代码行
    assertThat(a2Schema.subSchemas().getNames(LikePattern.any()), hasSize(1)); // 代码行
    a2SubSchemaMap.put("b4", new AbstractSchema()); // 代码行
    assertThat(a2Schema.subSchemas().getNames(LikePattern.any()), hasSize(2)); // 代码行
    for (String name : aSchema.subSchemas().getNames(LikePattern.any())) { // 代码行
      assertThat(aSchema.subSchemas().get(name), notNullValue()); // 代码行
    } // 代码行
 // 空行
    // add tables and retrieve with various case sensitivities // 单行注释
    final Smalls.SimpleTable table = // 代码行
        new Smalls.SimpleTable(); // 代码行
    a2Schema.add("table1", table); // 代码行
    a2Schema.add("TABLE1", table); // 代码行
    a2Schema.add("tabLe1", table); // 代码行
    a2Schema.add("tabLe2", table); // 代码行
    assertThat(a2Schema.tables().getNames(LikePattern.any()), hasSize(4)); // 代码行
    final CalciteSchema a2CalciteSchema = CalciteSchema.from(a2Schema); // 代码行
    assertThat(a2CalciteSchema.getTable("table1", true), notNullValue()); // 代码行
    assertThat(a2CalciteSchema.getTable("table1", false), notNullValue()); // 代码行
    assertThat(a2CalciteSchema.getTable("taBle1", true), nullValue()); // 代码行
    assertThat(a2CalciteSchema.getTable("taBle1", false), notNullValue()); // 代码行
    final TableMacro function = // 代码行
        ViewTable.viewMacro(a2Schema, "values 1", ImmutableList.of(), null, // 代码行
            null); // 代码行
    Util.discard(function); // 代码行
 // 空行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  @Test void testCaseSensitiveSubQueryOracle() { // 测试方法:测试CaseSensitiveSubQueryOracle功能
    final CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that() // 代码行
            .with(Lex.ORACLE); // 代码行
 // 空行
    with.query("select DID from (select DEPTID as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
 // 空行
    with.query("select x.DID from (select DEPTID as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnquotedCaseSensitiveSubQueryMySql() { // 测试方法:测试UnquotedCaseSensitiveSubQueryMySql功能
    final CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that() // 代码行
            .with(Lex.MYSQL); // 代码行
 // 空行
    with.query("select DID from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
 // 空行
    with.query("select x.DID from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
 // 空行
    with.query("select X.DID from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
 // 空行
    with.query("select X.DID2 from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X (DID2)") // 代码行
        .returnsUnordered("DID2=1", "DID2=2"); // 代码行
 // 空行
    with.query("select X.DID2 from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X (DID2)") // 代码行
        .returnsUnordered("DID2=1", "DID2=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testQuotedCaseSensitiveSubQueryMySql() { // 测试方法:测试QuotedCaseSensitiveSubQueryMySql功能
    final CalciteAssert.AssertThat with = // 代码行
        CalciteAssert.that() // 代码行
            .with(Lex.MYSQL); // 代码行
 // 空行
    with.query("select `DID` from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
 // 空行
    with.query("select `x`.`DID` from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
 // 空行
    with.query("select `X`.`DID` from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
 // 空行
    with.query("select `X`.`DID2` from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X (DID2)") // 代码行
        .returnsUnordered("DID2=1", "DID2=2"); // 代码行
 // 空行
    with.query("select `X`.`DID2` from (select deptid as did FROM\n" // 代码行
        + "     ( values (1), (2) ) as T1(deptid) ) X (DID2)") // 代码行
        .returnsUnordered("DID2=1", "DID2=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testUnquotedCaseSensitiveSubQuerySqlServer() { // 测试方法:测试UnquotedCaseSensitiveSubQuerySqlServer功能
    CalciteAssert.that() // 代码行
        .with(Lex.SQL_SERVER) // 代码行
        .query("select DID from (select deptid as did FROM\n" // 代码行
            + "     ( values (1), (2) ) as T1(deptid) ) ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
  } // 代码行
 // 空行
  @Test void testQuotedCaseSensitiveSubQuerySqlServer() { // 测试方法:测试QuotedCaseSensitiveSubQuerySqlServer功能
    CalciteAssert.that() // 代码行
        .with(Lex.SQL_SERVER) // 代码行
        .query("select [DID] from (select deptid as did FROM\n" // 代码行
            + "     ( values (1), (2) ) as T1([deptid]) ) ") // 代码行
        .returnsUnordered("DID=1", "DID=2"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-596">[CALCITE-596] // JavaDoc注释内容
   * JDBC adapter incorrectly reads null values as 0</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testPrimitiveColumnsWithNullValues() throws Exception { // 测试方法:测试PrimitiveColumnsWithNullValues功能
    String hsqldbMemUrl = "jdbc:hsqldb:mem:."; // 代码行
    Connection baseConnection = DriverManager.getConnection(hsqldbMemUrl); // 代码行
    Statement baseStmt = baseConnection.createStatement(); // 代码行
    baseStmt.execute("CREATE TABLE T1 (\n" // 代码行
        + "ID INTEGER,\n" // 代码行
        + "VALS DOUBLE)"); // 代码行
    baseStmt.execute("INSERT INTO T1 VALUES (1, 1.0)"); // 代码行
    baseStmt.execute("INSERT INTO T1 VALUES (2, null)"); // 代码行
    baseStmt.execute("INSERT INTO T1 VALUES (null, 2.0)"); // 代码行
 // 空行
    baseStmt.close(); // 代码行
    baseConnection.commit(); // 代码行
 // 空行
    Properties info = new Properties(); // 代码行
    info.put("model", // 代码行
        "inline:" // 代码行
            + "{\n" // 代码行
            + "  version: '1.0',\n" // 代码行
            + "  defaultSchema: 'BASEJDBC',\n" // 代码行
            + "  schemas: [\n" // 代码行
            + "     {\n" // 代码行
            + "       type: 'jdbc',\n" // 代码行
            + "       name: 'BASEJDBC',\n" // 代码行
            + "       jdbcDriver: '" + jdbcDriver.class.getName() + "',\n" // 代码行
            + "       jdbcUrl: '" + hsqldbMemUrl + "',\n" // 代码行
            + "       jdbcCatalog: null,\n" // 代码行
            + "       jdbcSchema: null\n" // 代码行
            + "     }\n" // 代码行
            + "  ]\n" // 代码行
            + "}"); // 代码行
 // 空行
    Connection calciteConnection = // 代码行
        DriverManager.getConnection("jdbc:calcite:", info); // 代码行
 // 空行
    ResultSet rs = calciteConnection.prepareStatement("select * from t1") // 代码行
        .executeQuery(); // 代码行
 // 空行
    assertThat(rs.next(), is(true)); // 代码行
    assertThat((Integer) rs.getObject("ID"), is(1)); // 代码行
    assertThat((Double) rs.getObject("VALS"), is(1.0)); // 代码行
 // 空行
    assertThat(rs.next(), is(true)); // 代码行
    assertThat((Integer) rs.getObject("ID"), is(2)); // 代码行
    assertThat(rs.getObject("VALS"), nullValue()); // 代码行
 // 空行
    assertThat(rs.next(), is(true)); // 代码行
    assertThat(rs.getObject("ID"), nullValue()); // 代码行
    assertThat((Double) rs.getObject("VALS"), is(2.0)); // 代码行
 // 空行
    rs.close(); // 代码行
    calciteConnection.close(); // 代码行
 // 空行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2054">[CALCITE-2054] // JavaDoc注释内容
   * Error while validating UPDATE with dynamic parameter in SET clause</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testUpdateBind() throws Exception { // 测试方法:测试UpdateBind功能
    String hsqldbMemUrl = "jdbc:hsqldb:mem:."; // 代码行
    try (Connection baseConnection = DriverManager.getConnection(hsqldbMemUrl); // 代码行
         Statement baseStmt = baseConnection.createStatement()) { // 代码行
      baseStmt.execute("CREATE TABLE T2 (\n" // 代码行
          + "ID INTEGER,\n" // 代码行
          + "VALS DOUBLE)"); // 代码行
      baseStmt.execute("INSERT INTO T2 VALUES (1, 1.0)"); // 代码行
      baseStmt.execute("INSERT INTO T2 VALUES (2, null)"); // 代码行
      baseStmt.execute("INSERT INTO T2 VALUES (null, 2.0)"); // 代码行
 // 空行
      baseStmt.close(); // 代码行
      baseConnection.commit(); // 代码行
 // 空行
      Properties info = new Properties(); // 代码行
      final String model = "inline:" // 代码行
          + "{\n" // 代码行
          + "  version: '1.0',\n" // 代码行
          + "  defaultSchema: 'BASEJDBC',\n" // 代码行
          + "  schemas: [\n" // 代码行
          + "     {\n" // 代码行
          + "       type: 'jdbc',\n" // 代码行
          + "       name: 'BASEJDBC',\n" // 代码行
          + "       jdbcDriver: '" + jdbcDriver.class.getName() + "',\n" // 代码行
          + "       jdbcUrl: '" + hsqldbMemUrl + "',\n" // 代码行
          + "       jdbcCatalog: null,\n" // 代码行
          + "       jdbcSchema: null\n" // 代码行
          + "     }\n" // 代码行
          + "  ]\n" // 代码行
          + "}"; // 代码行
      info.put("model", model); // 代码行
 // 空行
      Connection calciteConnection = // 代码行
          DriverManager.getConnection("jdbc:calcite:", info); // 代码行
 // 空行
      ResultSet rs = calciteConnection.prepareStatement("select * from t2") // 代码行
          .executeQuery(); // 代码行
 // 空行
      assertThat(rs.next(), is(true)); // 代码行
      assertThat((Integer) rs.getObject("ID"), is(1)); // 代码行
      assertThat((Double) rs.getObject("VALS"), is(1.0)); // 代码行
 // 空行
      assertThat(rs.next(), is(true)); // 代码行
      assertThat((Integer) rs.getObject("ID"), is(2)); // 代码行
      assertThat(rs.getObject("VALS"), nullValue()); // 代码行
 // 空行
      assertThat(rs.next(), is(true)); // 代码行
      assertThat(rs.getObject("ID"), nullValue()); // 代码行
      assertThat((Double) rs.getObject("VALS"), is(2.0)); // 代码行
 // 空行
      rs.close(); // 代码行
 // 空行
      final String sql = "update t2 set vals=? where id=?"; // 代码行
      try (PreparedStatement ps = // 代码行
               calciteConnection.prepareStatement(sql)) { // 代码行
        ParameterMetaData pmd = ps.getParameterMetaData(); // 代码行
        assertThat(pmd.getParameterCount(), is(2)); // 代码行
        assertThat(pmd.getParameterType(1), is(Types.DOUBLE)); // 代码行
        assertThat(pmd.getParameterType(2), is(Types.INTEGER)); // 代码行
      } // 代码行
      calciteConnection.close(); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-730">[CALCITE-730] // JavaDoc注释内容
   * ClassCastException in table from CloneSchema</a>. */ // JavaDoc注释内容
  @Test void testNullableNumericColumnInCloneSchema() { // 测试方法:测试NullableNumericColumnInCloneSchema功能
    CalciteAssert.model("{\n" // 代码行
        + "  version: '1.0',\n" // 代码行
        + "  defaultSchema: 'SCOTT_CLONE',\n" // 代码行
        + "  schemas: [ {\n" // 代码行
        + "    name: 'SCOTT_CLONE',\n" // 代码行
        + "    type: 'custom',\n" // 代码行
        + "    factory: 'org.apache.calcite.adapter.clone.CloneSchema$Factory',\n" // 代码行
        + "    operand: {\n" // 代码行
        + "      jdbcDriver: '" + JdbcTest.SCOTT.driver + "',\n" // 代码行
        + "      jdbcUser: '" + JdbcTest.SCOTT.username + "',\n" // 代码行
        + "      jdbcPassword: '" + JdbcTest.SCOTT.password + "',\n" // 代码行
        + "      jdbcUrl: '" + JdbcTest.SCOTT.url + "',\n" // 代码行
        + "      jdbcSchema: 'SCOTT'\n" // 代码行
        + "   } } ]\n" // 代码行
        + "}") // 代码行
        .query("select * from emp") // 代码行
        .returns(input -> { // 代码行
          int rowCount = 0; // 代码行
          int nullCount = 0; // 代码行
          int valueCount = 0; // 代码行
          try { // 代码行
            final int columnCount = input.getMetaData().getColumnCount(); // 代码行
            while (input.next()) { // 代码行
              for (int i = 0; i < columnCount; i++) { // 代码行
                final Object o = input.getObject(i + 1); // 代码行
                if (o == null) { // 代码行
                  ++nullCount; // 代码行
                } // 代码行
                ++valueCount; // 代码行
              } // 代码行
              ++rowCount; // 代码行
            } // 代码行
            assertThat(rowCount, is(14)); // 代码行
            assertThat(columnCount, is(8)); // 代码行
            assertThat(valueCount, is(rowCount * columnCount)); // 代码行
            assertThat(nullCount, is(11)); // 代码行
          } catch (SQLException e) { // 代码行
            throw TestUtil.rethrow(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1097">[CALCITE-1097] // JavaDoc注释内容
   * Exception when executing query with too many aggregation columns</a>. */ // JavaDoc注释内容
  @Test void testAggMultipleMeasures() throws SQLException { // 测试方法:测试AggMultipleMeasures功能
    final Driver driver = new Driver(); // 代码行
    CalciteConnection connection = (CalciteConnection) // 代码行
        driver.connect("jdbc:calcite:", new Properties()); // 代码行
    SchemaPlus rootSchema = connection.getRootSchema(); // 代码行
    rootSchema.add("sale", new ReflectiveSchema(new Smalls.WideSaleSchema())); // 代码行
    connection.setSchema("sale"); // 代码行
    final Statement statement = connection.createStatement(); // 代码行
 // 空行
    // 200 columns: sum(sale0) + ... sum(sale199) // 单行注释
    ResultSet resultSet = // 代码行
        statement.executeQuery("select s.\"prodId\"" + sums(200, true) + "\n" // 代码行
            + "from \"sale\".\"prod\" as s group by s.\"prodId\"\n"); // 代码行
    assertThat(resultSet.next(), is(true)); // 代码行
    assertThat(resultSet.getInt(1), is(100)); // 代码行
    assertThat(resultSet.getInt(2), is(10)); // 代码行
    assertThat(resultSet.getInt(200), is(10)); // 代码行
    assertThat(resultSet.next(), is(false)); // 代码行
 // 空行
    // 800 columns: // 单行注释
    //   sum(sale0 + 0) + ... + sum(sale0 + 100) + ... sum(sale99 + 799) // 单行注释
    final int n = 800; // 代码行
    resultSet = // 代码行
        statement.executeQuery("select s.\"prodId\"" + sums(n, false) + "\n" // 代码行
            + "from \"sale\".\"prod\" as s group by s.\"prodId\"\n"); // 代码行
    assertThat(resultSet.next(), is(true)); // 代码行
    assertThat(resultSet.getInt(1), is(100)); // 代码行
    assertThat(resultSet.getInt(2), is(10)); // 代码行
    assertThat(resultSet.getInt(n), is(n + 8)); // 代码行
    assertThat(resultSet.next(), is(false)); // 代码行
 // 空行
    connection.close(); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3039">[CALCITE-3039] // JavaDoc注释内容
   * In Interpreter, min() incorrectly returns maximum double value</a>. */ // JavaDoc注释内容
  @Test void testMinAggWithDouble() { // 测试方法:测试MinAggWithDouble功能
    try (Hook.Closeable ignored = Hook.ENABLE_BINDABLE.addThread(Hook.propertyJ(true))) { // 代码行
      CalciteAssert.hr() // 代码行
          .query( // 代码行
              "select min(div) as _min from (" // 代码行
                  + "select \"empid\", \"deptno\", CAST(\"empid\" AS DOUBLE)/\"deptno\" as div from \"hr\".\"emps\")") // 代码行
          .explainContains("BindableAggregate(group=[{}], _MIN=[MIN($0)])\n" // 代码行
              + "  BindableProject(DIV=[/(CAST($0):DOUBLE NOT NULL, $1)])\n" // 代码行
              + "    BindableTableScan(table=[[hr, emps]])") // 代码行
          .returns("_MIN=10.0\n"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void testBindableIntersect() { // 测试方法:测试BindableIntersect功能
    try (Hook.Closeable ignored = Hook.ENABLE_BINDABLE.addThread(Hook.propertyJ(true))) { // 代码行
      final String sql0 = "select \"empid\", \"deptno\" from \"hr\".\"emps\""; // 代码行
      final String sql = sql0 + " intersect all " + sql0; // 代码行
      CalciteAssert.hr() // 代码行
          .query(sql) // 代码行
          .explainContains("" // 代码行
              + "PLAN=BindableIntersect(all=[true])\n" // 代码行
              + "  BindableProject(empid=[$0], deptno=[$1])\n" // 代码行
              + "    BindableTableScan(table=[[hr, emps]])\n" // 代码行
              + "  BindableProject(empid=[$0], deptno=[$1])\n" // 代码行
              + "    BindableTableScan(table=[[hr, emps]])") // 代码行
          .returns("" // 代码行
              + "empid=150; deptno=10\n" // 代码行
              + "empid=100; deptno=10\n" // 代码行
              + "empid=200; deptno=20\n" // 代码行
              + "empid=110; deptno=10\n"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void testBindableMinus() { // 测试方法:测试BindableMinus功能
    try (Hook.Closeable ignored = Hook.ENABLE_BINDABLE.addThread(Hook.propertyJ(true))) { // 代码行
      final String sql0 = "select \"empid\", \"deptno\" from \"hr\".\"emps\""; // 代码行
      final String sql = sql0 + " except all " + sql0; // 代码行
      CalciteAssert.hr() // 代码行
          .query(sql) // 代码行
          .explainContains("" // 代码行
              + "PLAN=BindableMinus(all=[true])\n" // 代码行
              + "  BindableProject(empid=[$0], deptno=[$1])\n" // 代码行
              + "    BindableTableScan(table=[[hr, emps]])\n" // 代码行
              + "  BindableProject(empid=[$0], deptno=[$1])\n" // 代码行
              + "    BindableTableScan(table=[[hr, emps]])") // 代码行
          .returns(""); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2224">[CALCITE-2224] // JavaDoc注释内容
   * WITHIN GROUP clause for aggregate functions</a>. */ // JavaDoc注释内容
  @Test void testWithinGroupClause1() { // 测试方法:测试WithinGroupClause1功能
    final String sql = "select X,\n" // 代码行
        + " collect(Y) within group (order by Y desc) as \"SET\"\n" // 代码行
        + "from (values (1, 'a'), (1, 'b'),\n" // 代码行
        + "             (3, 'c'), (3, 'd')) AS t(X, Y)\n" // 代码行
        + "group by X\n" // 代码行
        + "limit 10"; // 代码行
    CalciteAssert.that().query(sql) // 代码行
        .returnsUnordered("X=1; SET=[b, a]", // 代码行
            "X=3; SET=[d, c]"); // 代码行
  } // 代码行
 // 空行
  @Test void testWithinGroupClause2() { // 测试方法:测试WithinGroupClause2功能
    final String sql = "select X,\n" // 代码行
        + " collect(Y) within group (order by Y desc) as SET_1,\n" // 代码行
        + " collect(Y) within group (order by Y asc) as SET_2\n" // 代码行
        + "from (values (1, 'a'), (1, 'b'), (3, 'c'), (3, 'd')) AS t(X, Y)\n" // 代码行
        + "group by X\n" // 代码行
        + "limit 10"; // 代码行
    CalciteAssert // 代码行
        .that() // 代码行
        .query(sql) // 代码行
        .returnsUnordered("X=1; SET_1=[b, a]; SET_2=[a, b]", // 代码行
            "X=3; SET_1=[d, c]; SET_2=[c, d]"); // 代码行
  } // 代码行
 // 空行
  @Test void testWithinGroupClause3() { // 测试方法:测试WithinGroupClause3功能
    final String sql = "select" // 代码行
        + " collect(Y) within group (order by Y desc) as SET_1,\n" // 代码行
        + " collect(Y) within group (order by Y asc) as SET_2\n" // 代码行
        + "from (values (1, 'a'), (1, 'b'), (3, 'c'), (3, 'd')) AS t(X, Y)\n" // 代码行
        + "limit 10"; // 代码行
    CalciteAssert.that().query(sql) // 代码行
        .returns("SET_1=[d, c, b, a]; SET_2=[a, b, c, d]\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testWithinGroupClause4() { // 测试方法:测试WithinGroupClause4功能
    final String sql = "select" // 代码行
        + " collect(Y) within group (order by Y desc) as SET_1,\n" // 代码行
        + " collect(Y) within group (order by Y asc) as SET_2\n" // 代码行
        + "from (values (1, 'a'), (1, 'b'), (3, 'c'), (3, 'd')) AS t(X, Y)\n" // 代码行
        + "group by X\n" // 代码行
        + "limit 10"; // 代码行
    CalciteAssert.that().query(sql) // 代码行
        .returnsUnordered("SET_1=[b, a]; SET_2=[a, b]", // 代码行
            "SET_1=[d, c]; SET_2=[c, d]"); // 代码行
  } // 代码行
 // 空行
  @Test void testWithinGroupClause5() { // 测试方法:测试WithinGroupClause5功能
    CalciteAssert // 代码行
        .that() // 代码行
        .query("select collect(array[X, Y])\n" // 代码行
            + " within group (order by Y desc) as \"SET\"\n" // 代码行
            + "from (values ('b', 'a'), ('a', 'b'), ('a', 'c'),\n" // 代码行
            + "             ('a', 'd')) AS t(X, Y)\n" // 代码行
            + "limit 10") // 代码行
        .returns("SET=[[a, d], [a, c], [a, b], [b, a]]\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testWithinGroupClause6() { // 测试方法:测试WithinGroupClause6功能
    final String sql = "select collect(\"commission\")" // 代码行
        + " within group (order by \"commission\")\n" // 代码行
        + "from \"hr\".\"emps\""; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query(sql) // 代码行
        .explainContains("EnumerableAggregate(group=[{}], " // 代码行
            + "EXPR$0=[COLLECT($4) WITHIN GROUP ([4])])") // 代码行
        .returns("EXPR$0=[250, 500, 1000]\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2593">[CALCITE-2593] // JavaDoc注释内容
   * Error when transforming multiple collations to single collation</a>. */ // JavaDoc注释内容
  @Test void testWithinGroupClause7() { // 测试方法:测试WithinGroupClause7功能
    CalciteAssert // 代码行
        .that() // 代码行
        .query("select sum(X + 1) filter (where Y) as S\n" // 代码行
            + "from (values (1, TRUE), (2, TRUE)) AS t(X, Y)") // 代码行
        .explainContains("EnumerableAggregate(group=[{}], S=[SUM($0) FILTER $1])\n" // 代码行
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t0, $t2)], $f0=[$t3], Y=[$t1])\n" // 代码行
            + "    EnumerableValues(tuples=[[{ 1, true }, { 2, true }]])\n") // 代码行
        .returns("S=5\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2010">[CALCITE-2010] // JavaDoc注释内容
   * Fails to plan query that is UNION ALL applied to VALUES</a>. */ // JavaDoc注释内容
  @Test void testUnionAllValues() { // 测试方法:测试UnionAllValues功能
    CalciteAssert.hr() // 代码行
        .query("select x, y from (values (1, 2)) as t(x, y)\n" // 代码行
            + "union all\n" // 代码行
            + "select a + b, a - b from (values (3, 4), (5, 6)) as u(a, b)") // 代码行
        .explainContains("EnumerableUnion(all=[true])\n" // 代码行
            + "  EnumerableValues(tuples=[[{ 1, 2 }]])\n" // 代码行
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[+($t0, $t1)], expr#3=[-($t0, $t1)], EXPR$0=[$t2], EXPR$1=[$t3])\n" // 代码行
            + "    EnumerableValues(tuples=[[{ 3, 4 }, { 5, 6 }]])\n") // 代码行
        .returnsUnordered("X=11; Y=-1", // 代码行
            "X=1; Y=2", // 代码行
            "X=7; Y=-1"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3565">[CALCITE-3565] // JavaDoc注释内容
   * Explicitly cast assignable operand types to decimal for udf</a>. */ // JavaDoc注释内容
  @Test void testAssignableTypeCast() { // 测试方法:测试AssignableTypeCast功能
    final String sql = "SELECT ST_MakePoint(1, 2.1)"; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.GEO) // 代码行
        .query(sql) // 代码行
        .planContains("static final java.math.BigDecimal $L4J$C$new_java_math_BigDecimal_1_ = " // 代码行
            + "new java.math.BigDecimal(\n" // 代码行
            + "              1)") // 代码行
        .planContains("org.apache.calcite.runtime.SpatialTypeFunctions.ST_MakePoint(" // 代码行
            + "$L4J$C$new_java_math_BigDecimal_1_, literal_value0)") // 代码行
        .returns("EXPR$0=POINT (1 2.1)\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testMatchSimple() { // 测试方法:测试MatchSimple功能
    final String sql = "select *\n" // 代码行
        + "from \"hr\".\"emps\" match_recognize (\n" // 代码行
        + "  order by \"empid\" desc\n" // 代码行
        + "  measures up.\"commission\" as c,\n" // 代码行
        + "    up.\"empid\" as empid,\n" // 代码行
        + "    2 as two\n" // 代码行
        + "  pattern (up s)\n" // 代码行
        + "  define up as up.\"empid\" = 100)"; // 代码行
    final String convert = "" // 代码行
        + "LogicalMatch(partition=[[]], order=[[0 DESC]], " // 代码行
        + "outputFields=[[C, EMPID, TWO]], allRows=[false], " // 代码行
        + "after=[FLAG(SKIP TO NEXT ROW)], pattern=[('UP', 'S')], " // 代码行
        + "isStrictStarts=[false], isStrictEnds=[false], subsets=[[]], " // 代码行
        + "patternDefinitions=[[=(CAST(PREV(UP.$0, 0)):INTEGER NOT NULL, 100)]], " // 代码行
        + "inputFields=[[empid, deptno, name, salary, commission]])\n" // 代码行
        + "  LogicalTableScan(table=[[hr, emps]])\n"; // 代码行
    final String plan = "PLAN=" // 代码行
        + "EnumerableMatch(partition=[[]], order=[[0 DESC]], " // 代码行
        + "outputFields=[[C, EMPID, TWO]], allRows=[false], " // 代码行
        + "after=[FLAG(SKIP TO NEXT ROW)], pattern=[('UP', 'S')], " // 代码行
        + "isStrictStarts=[false], isStrictEnds=[false], subsets=[[]], " // 代码行
        + "patternDefinitions=[[=(CAST(PREV(UP.$0, 0)):INTEGER NOT NULL, 100)]], " // 代码行
        + "inputFields=[[empid, deptno, name, salary, commission]])\n" // 代码行
        + "  EnumerableTableScan(table=[[hr, emps]])"; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query(sql) // 代码行
        .convertContains(convert) // 代码行
        .explainContains(plan) // 代码行
        .returns("C=1000; EMPID=100; TWO=2\nC=500; EMPID=200; TWO=2\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testMatch() { // 测试方法:测试Match功能
    final String sql = "select *\n" // 代码行
        + "from \"hr\".\"emps\" match_recognize (\n" // 代码行
        + "  order by \"empid\" desc\n" // 代码行
        + "  measures \"commission\" as c,\n" // 代码行
        + "    \"empid\" as empid\n" // 代码行
        + "  pattern (s up)\n" // 代码行
        + "  define up as up.\"commission\" < prev(up.\"commission\"))"; // 代码行
    final String convert = "" // 代码行
        + "LogicalMatch(partition=[[]], order=[[0 DESC]], " // 代码行
        + "outputFields=[[C, EMPID]], allRows=[false], " // 代码行
        + "after=[FLAG(SKIP TO NEXT ROW)], pattern=[('S', 'UP')], " // 代码行
        + "isStrictStarts=[false], isStrictEnds=[false], subsets=[[]], " // 代码行
        + "patternDefinitions=[[<(PREV(UP.$4, 0), PREV(UP.$4, 1))]], " // 代码行
        + "inputFields=[[empid, deptno, name, salary, commission]])\n" // 代码行
        + "  LogicalTableScan(table=[[hr, emps]])\n"; // 代码行
    final String plan = "PLAN=" // 代码行
        + "EnumerableMatch(partition=[[]], order=[[0 DESC]], " // 代码行
        + "outputFields=[[C, EMPID]], allRows=[false], " // 代码行
        + "after=[FLAG(SKIP TO NEXT ROW)], pattern=[('S', 'UP')], " // 代码行
        + "isStrictStarts=[false], isStrictEnds=[false], subsets=[[]], " // 代码行
        + "patternDefinitions=[[<(PREV(UP.$4, 0), PREV(UP.$4, 1))]], " // 代码行
        + "inputFields=[[empid, deptno, name, salary, commission]])\n" // 代码行
        + "  EnumerableTableScan(table=[[hr, emps]])"; // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.REGULAR) // 代码行
        .query(sql) // 代码行
        .convertContains(convert) // 代码行
        .explainContains(plan) // 代码行
        .returns("C=1000; EMPID=100\nC=500; EMPID=200\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonType() { // 测试方法:测试JsonType功能
    CalciteAssert.that() // 代码行
        .query("SELECT JSON_TYPE(v) AS c1\n" // 代码行
            + ",JSON_TYPE(JSON_VALUE(v, 'lax $.b' ERROR ON ERROR)) AS c2\n" // 代码行
            + ",JSON_TYPE(JSON_VALUE(v, 'strict $.a[0]' ERROR ON ERROR)) AS c3\n" // 代码行
            + ",JSON_TYPE(JSON_VALUE(v, 'strict $.a[1]' ERROR ON ERROR)) AS c4\n" // 代码行
            + "FROM (VALUES ('{\"a\": [10, true],\"b\": \"[10, true]\"}')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1=OBJECT; C2=ARRAY; C3=INTEGER; C4=BOOLEAN\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonQuery() { // 测试方法:测试JsonQuery功能
    CalciteAssert.that() // 代码行
        .query("SELECT JSON_QUERY(v, '$.a') AS c1\n" // 代码行
            + ",JSON_QUERY(v, '$.a' RETURNING INTEGER ARRAY) AS c2\n" // 代码行
            + ",JSON_QUERY(v, '$.b' RETURNING INTEGER ARRAY EMPTY ARRAY ON ERROR) AS c3\n" // 代码行
            + ",JSON_QUERY(v, '$.b' RETURNING VARCHAR ARRAY WITH ARRAY WRAPPER) AS c4\n" // 代码行
            + "FROM (VALUES ('{\"a\": [1, 2],\"b\": \"[1, 2]\"}')) AS t(v)\n" // 代码行
            + "LIMIT 10") // 代码行
        .returns("C1=[1,2]; C2=[1, 2]; C3=[]; C4=[[1, 2]]\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonValueError() { // 测试方法:测试JsonValueError功能
    java.sql.SQLException t = // 代码行
        assertThrows( // 代码行
            java.sql.SQLException.class, // 代码行
            () -> CalciteAssert.that() // 代码行
                .query("SELECT JSON_VALUE(v, 'lax $.a' RETURNING INTEGER) AS c1\n" // 代码行
                    + "FROM (VALUES ('{\"a\": \"abc\"}')) AS t(v)\n" // 代码行
                    + "LIMIT 10") // 代码行
                .returns("")); // 代码行
 // 空行
    assertThat( // 代码行
        t.getMessage(), containsString("java.lang.String cannot be cast to")); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonQueryError() { // 测试方法:测试JsonQueryError功能
    java.sql.SQLException t = // 代码行
        assertThrows( // 代码行
            java.sql.SQLException.class, // 代码行
            () -> CalciteAssert.that() // 代码行
                .query("SELECT JSON_QUERY(v, '$.a' RETURNING VARCHAR ARRAY" // 代码行
                    + " EMPTY OBJECT ON ERROR) AS c1\n" // 代码行
                    + "FROM (VALUES ('{\"a\": \"hi\"}')) AS t(v)\n" // 代码行
                    + "LIMIT 10") // 代码行
                .returns("")); // 代码行
 // 空行
    assertThat( // 代码行
        t.getMessage(), containsString("EMPTY_OBJECT is illegal for given return type")); // 代码行
 // 空行
    t = // 代码行
        assertThrows( // 代码行
            java.sql.SQLException.class, // 代码行
            () -> CalciteAssert.that() // 代码行
                .query("SELECT JSON_QUERY(v, 'lax $.a' RETURNING VARCHAR ARRAY" // 代码行
                    + " EMPTY OBJECT ON EMPTY) AS c1\n" // 代码行
                    + "FROM (VALUES ('{\"a\": null}')) AS t(v)\n" // 代码行
                    + "LIMIT 10") // 代码行
                .returns("")); // 代码行
 // 空行
    assertThat( // 代码行
        t.getMessage(), containsString("EMPTY_OBJECT is illegal for given return type")); // 代码行
 // 空行
    t = // 代码行
        assertThrows( // 代码行
            java.sql.SQLException.class, // 代码行
            () -> CalciteAssert.that() // 代码行
                .query("SELECT JSON_QUERY(v, 'lax $.a' RETURNING INTEGER) AS c1\n" // 代码行
                    + "FROM (VALUES ('{\"a\": [\"a\", \"b\"]}')) AS t(v)\n" // 代码行
                    + "LIMIT 10") // 代码行
                .returns("")); // 代码行
 // 空行
    assertThat( // 代码行
        t.getMessage(), containsString("java.util.ArrayList cannot be cast to")); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonDepth() { // 测试方法:测试JsonDepth功能
    CalciteAssert.that() // 代码行
        .query("SELECT JSON_DEPTH(v) AS c1\n" // 代码行
            + ",JSON_DEPTH(JSON_VALUE(v, 'lax $.b' ERROR ON ERROR)) AS c2\n" // 代码行
            + ",JSON_DEPTH(JSON_VALUE(v, 'strict $.a[0]' ERROR ON ERROR)) AS c3\n" // 代码行
            + ",JSON_DEPTH(JSON_VALUE(v, 'strict $.a[1]' ERROR ON ERROR)) AS c4\n" // 代码行
            + "FROM (VALUES ('{\"a\": [10, true],\"b\": \"[10, true]\"}')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1=3; C2=2; C3=1; C4=1\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonLength() { // 测试方法:测试JsonLength功能
    CalciteAssert.that() // 代码行
        .query("SELECT JSON_LENGTH(v) AS c1\n" // 代码行
            + ",JSON_LENGTH(v, 'lax $.a') AS c2\n" // 代码行
            + ",JSON_LENGTH(v, 'strict $.a[0]') AS c3\n" // 代码行
            + ",JSON_LENGTH(v, 'strict $.a[1]') AS c4\n" // 代码行
            + "FROM (VALUES ('{\"a\": [10, true]}')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1=1; C2=2; C3=1; C4=1\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonPretty() { // 测试方法:测试JsonPretty功能
    CalciteAssert.that() // 代码行
        .query("SELECT JSON_PRETTY(v) AS c1\n" // 代码行
            + "FROM (VALUES ('{\"a\": [10, true],\"b\": [10, true]}')) as t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1={\n" // 代码行
            + "  \"a\" : [ 10, true ],\n" // 代码行
            + "  \"b\" : [ 10, true ]\n" // 代码行
            + "}\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonKeys() { // 测试方法:测试JsonKeys功能
    CalciteAssert.that() // 代码行
        .query("SELECT JSON_KEYS(v) AS c1\n" // 代码行
            + ",JSON_KEYS(v, 'lax $.a') AS c2\n" // 代码行
            + ",JSON_KEYS(v, 'lax $.b') AS c3\n" // 代码行
            + ",JSON_KEYS(v, 'strict $.a[0]') AS c4\n" // 代码行
            + ",JSON_KEYS(v, 'strict $.a[1]') AS c5\n" // 代码行
            + "FROM (VALUES ('{\"a\": [10, true],\"b\": {\"c\": 30}}')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1=[\"a\",\"b\"]; C2=null; C3=[\"c\"]; C4=null; C5=null\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonRemove() { // 测试方法:测试JsonRemove功能
    CalciteAssert.that() // 代码行
        .query("SELECT JSON_REMOVE(v, '$[1]') AS c1\n" // 代码行
            + "FROM (VALUES ('[\"a\", [\"b\", \"c\"], \"d\"]')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1=[\"a\",\"d\"]\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testJsonStorageSize() { // 测试方法:测试JsonStorageSize功能
    CalciteAssert.that() // 代码行
        .query("SELECT\n" // 代码行
            + "JSON_STORAGE_SIZE('[100, \"sakila\", [1, 3, 5], 425.05]') AS A,\n" // 代码行
            + "JSON_STORAGE_SIZE('{\"a\": 10, \"b\": \"a\", \"c\": \"[1, 3, 5, 7]\"}') AS B,\n" // 代码行
            + "JSON_STORAGE_SIZE('{\"a\": 10, \"b\": \"xyz\", \"c\": \"[1, 3, 5, 7]\"}') AS C,\n" // 代码行
            + "JSON_STORAGE_SIZE('[100, \"json\", [[10, 20, 30], 3, 5], 425.05]') AS D\n" // 代码行
            + "limit 10") // 代码行
        .returns("A=29; B=35; C=37; D=36\n"); // 代码行
  } // 代码行
 // 空行
  @Test public void testJsonInsert() { // 注解
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.FUN, "mysql") // 代码行
        .query("SELECT JSON_INSERT(v, '$.a', 10, '$.c', '[1]') AS c1\n" // 代码行
            + ",JSON_INSERT(v, '$', 10, '$.c', '[1]') AS c2\n" // 代码行
            + "FROM (VALUES ('{\"a\": 1,\"b\":[2]}')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1={\"a\":1,\"b\":[2],\"c\":\"[1]\"}; C2={\"a\":1,\"b\":[2],\"c\":\"[1]\"}\n"); // 代码行
  } // 代码行
 // 空行
  @Test public void testJsonReplace() { // 注解
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.FUN, "mysql") // 代码行
        .query("SELECT JSON_REPLACE(v, '$.a', 10, '$.c', '[1]') AS c1\n" // 代码行
            + ",JSON_REPLACE(v, '$', 10, '$.c', '[1]') AS c2\n" // 代码行
            + "FROM (VALUES ('{\"a\": 1,\"b\":[2]}')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1={\"a\":10,\"b\":[2]}; C2=10\n"); // 代码行
  } // 代码行
 // 空行
  @Test public void testJsonSet() { // 注解
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.FUN, "mysql") // 代码行
        .query("SELECT JSON_SET(v, '$.a', 10, '$.c', '[1]') AS c1\n" // 代码行
            + ",JSON_SET(v, '$', 10, '$.c', '[1]') AS c2\n" // 代码行
            + "FROM (VALUES ('{\"a\": 1,\"b\":[2]}')) AS t(v)\n" // 代码行
            + "limit 10") // 代码行
        .returns("C1={\"a\":10,\"b\":[2],\"c\":\"[1]\"}; C2=10\n"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2609">[CALCITE-2609] // JavaDoc注释内容
   * Dynamic parameters ("?") pushed to underlying JDBC schema, causing // JavaDoc注释内容
   * error</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testQueryWithParameter() throws Exception { // 测试方法:测试QueryWithParameter功能
    String hsqldbMemUrl = "jdbc:hsqldb:mem:."; // 代码行
    try (Connection baseConnection = DriverManager.getConnection(hsqldbMemUrl); // 代码行
         Statement baseStmt = baseConnection.createStatement()) { // 代码行
      baseStmt.execute("CREATE TABLE T3 (\n" // 代码行
          + "ID INTEGER,\n" // 代码行
          + "VALS DOUBLE)"); // 代码行
      baseStmt.execute("INSERT INTO T3 VALUES (1, 1.0)"); // 代码行
      baseStmt.execute("INSERT INTO T3 VALUES (2, null)"); // 代码行
      baseStmt.execute("INSERT INTO T3 VALUES (null, 2.0)"); // 代码行
      baseStmt.close(); // 代码行
      baseConnection.commit(); // 代码行
 // 空行
      Properties info = new Properties(); // 代码行
      final String model = "inline:" // 代码行
          + "{\n" // 代码行
          + "  version: '1.0',\n" // 代码行
          + "  defaultSchema: 'BASEJDBC',\n" // 代码行
          + "  schemas: [\n" // 代码行
          + "     {\n" // 代码行
          + "       type: 'jdbc',\n" // 代码行
          + "       name: 'BASEJDBC',\n" // 代码行
          + "       jdbcDriver: '" + jdbcDriver.class.getName() + "',\n" // 代码行
          + "       jdbcUrl: '" + hsqldbMemUrl + "',\n" // 代码行
          + "       jdbcCatalog: null,\n" // 代码行
          + "       jdbcSchema: null\n" // 代码行
          + "     }\n" // 代码行
          + "  ]\n" // 代码行
          + "}"; // 代码行
      info.put("model", model); // 代码行
 // 空行
      Connection calciteConnection = // 代码行
          DriverManager.getConnection("jdbc:calcite:", info); // 代码行
 // 空行
      final String sql = "select * from t3 where vals = ?"; // 代码行
      try (PreparedStatement ps = // 代码行
               calciteConnection.prepareStatement(sql)) { // 代码行
        ParameterMetaData pmd = ps.getParameterMetaData(); // 代码行
        assertThat(pmd.getParameterCount(), is(1)); // 代码行
        assertThat(pmd.getParameterType(1), is(Types.DOUBLE)); // 代码行
        ps.setDouble(1, 1.0); // 代码行
        ps.executeQuery(); // 代码行
      } // 代码行
      calciteConnection.close(); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3347">[CALCITE-3347] // JavaDoc注释内容
   * IndexOutOfBoundsException in FixNullabilityShuttle when using FilterIntoJoinRule</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testSemiJoin() { // 测试方法:测试SemiJoin功能
    CalciteAssert.that() // 代码行
        .with(CalciteAssert.Config.JDBC_FOODMART) // 代码行
        .query("select *\n" // 代码行
            + " from \"foodmart\".\"employee\"" // 代码行
            + " where \"employee_id\" = 1 and \"last_name\" in" // 代码行
            + " (select \"last_name\" from \"foodmart\".\"employee\" where \"employee_id\" = 2)") // 代码行
        .runs(); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3894">[CALCITE-3894] // JavaDoc注释内容
   * SET operation between DATE and TIMESTAMP returns a wrong result</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testUnionDateTime() { // 测试方法:测试UnionDateTime功能
    CalciteAssert.AssertThat assertThat = CalciteAssert.that(); // 代码行
    String query = "select * from (\n" // 代码行
        + "select \"id\" from (VALUES(DATE '2018-02-03')) \"foo\"(\"id\")\n" // 代码行
        + "union\n" // 代码行
        + "select \"id\" from (VALUES(TIMESTAMP '2008-03-31 12:23:34')) \"foo\"(\"id\"))"; // 代码行
    assertThat.query(query).returns("id=2008-03-31 12:23:34\nid=2018-02-03 00:00:00\n"); // 代码行
  } // 代码行
 // 空行
  @Test void testNestedCastBigInt() { // 测试方法:测试NestedCastBigInt功能
    CalciteAssert.AssertThat assertThat = CalciteAssert.that(); // 代码行
    String query = "SELECT CAST(CAST(4200000000 AS BIGINT) AS ANY) FROM (VALUES(1))"; // 代码行
    assertThat.query(query).returns("EXPR$0=4200000000\n"); // 代码行
  } // 代码行
 // 空行
  /** // JavaDoc注释开始
   * Test case for // JavaDoc注释内容
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4811">[CALCITE-4811] // JavaDoc注释内容
   * Check for internal content in case of ROW in // JavaDoc注释内容
   * RelDataTypeFactoryImpl#leastRestrictiveStructuredType should be after isStruct check</a>. // JavaDoc注释内容
   */ // JavaDoc注释内容
  @Test void testCoalesceNullAndRow() { // 测试方法:测试CoalesceNullAndRow功能
    CalciteAssert.that() // 代码行
        .query("SELECT COALESCE(NULL, ROW(1)) AS F") // 代码行
        .typeIs("[F STRUCT]") // 代码行
        .returns("F={1}\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4600">[CALCITE-4600] // JavaDoc注释内容
   * ClassCastException retrieving from an ARRAY that has DATE, TIME or // JavaDoc注释内容
   * TIMESTAMP elements</a>. */ // JavaDoc注释内容
  @Test void testArrayOfDates() { // 测试方法:测试ArrayOfDates功能
    CalciteAssert.that() // 代码行
        .query("select array[cast('1900-1-1' as date)]") // 代码行
        .returns("EXPR$0=[1900-01-01]\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4602">[CALCITE-4602] // JavaDoc注释内容
   * ClassCastException retrieving from ARRAY that has mixed INTEGER and DECIMAL // JavaDoc注释内容
   * elements</a>. */ // JavaDoc注释内容
  @Test void testIntAndBigDecimalInArray() { // 测试方法:测试IntAndBigDecimalInArray功能
    CalciteAssert.that() // 代码行
        .query("select array[1, 1.1]") // 代码行
        .returns("EXPR$0=[1.0, 1.1]\n"); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6032">[CALCITE-6032] // JavaDoc注释内容
   * NullPointerException in Reldecorrelator for a Multi level correlated subquery</a>. */ // JavaDoc注释内容
  @Test void testMultiLevelDecorrelation() throws Exception { // 测试方法:测试MultiLevelDecorrelation功能
    String hsqldbMemUrl = "jdbc:hsqldb:mem:."; // 代码行
    Connection baseConnection = DriverManager.getConnection(hsqldbMemUrl); // 代码行
    Statement baseStmt = baseConnection.createStatement(); // 代码行
    baseStmt.execute("create table invoice (inv_id integer, col1\n" // 代码行
        + "integer, inv_amt integer)"); // 代码行
    baseStmt.execute("create table item(item_id integer, item_amt\n" // 代码行
        + "integer, item_col1 integer, item_col2 integer, item_col3\n" // 代码行
        + "integer,item_col4 integer )"); // 代码行
    baseStmt.execute("INSERT INTO invoice VALUES (1, 1, 1)"); // 代码行
    baseStmt.execute("INSERT INTO invoice VALUES (2, 2, 2)"); // 代码行
    baseStmt.execute("INSERT INTO invoice VALUES (3, 3, 3)"); // 代码行
    baseStmt.execute("INSERT INTO item values (1, 1, 1, 1, 1, 1)"); // 代码行
    baseStmt.execute("INSERT INTO item values (2, 2, 2, 2, 2, 2)"); // 代码行
    baseStmt.close(); // 代码行
    baseConnection.commit(); // 代码行
 // 空行
    Properties info = new Properties(); // 代码行
    info.put("model", // 代码行
        "inline:" // 代码行
            + "{\n" // 代码行
            + "  version: '1.0',\n" // 代码行
            + "  defaultSchema: 'BASEJDBC',\n" // 代码行
            + "  schemas: [\n" // 代码行
            + "     {\n" // 代码行
            + "       type: 'jdbc',\n" // 代码行
            + "       name: 'BASEJDBC',\n" // 代码行
            + "       jdbcDriver: '" + jdbcDriver.class.getName() + "',\n" // 代码行
            + "       jdbcUrl: '" + hsqldbMemUrl + "',\n" // 代码行
            + "       jdbcCatalog: null,\n" // 代码行
            + "       jdbcSchema: null\n" // 代码行
            + "     }\n" // 代码行
            + "  ]\n" // 代码行
            + "}"); // 代码行
 // 空行
    Connection calciteConnection = // 代码行
        DriverManager.getConnection("jdbc:calcite:", info); // 代码行
 // 空行
    String statement = "SELECT Sum(invoice.inv_amt * (\n" // 代码行
        + "              SELECT max(mainrate.item_id + mainrate.item_amt)\n" // 代码行
        + "              FROM   item AS mainrate\n" // 代码行
        + "              WHERE  mainrate.item_col1 is not null\n" // 代码行
        + "              AND    mainrate.item_col2 is not null\n" // 代码行
        + "              AND    mainrate.item_col3 = invoice.col1\n" // 代码行
        + "              AND    mainrate.item_col4 = (\n" // 代码行
        + "                        SELECT max(cr.item_col4)\n" // 代码行
        + "                        FROM   item AS cr\n" // 代码行
        + "                        WHERE  cr.item_col3 = mainrate.item_col3\n" // 代码行
        + "                                AND    cr.item_col1 =\n" // 代码行
        + "mainrate.item_col1\n" // 代码行
        + "                                AND    cr.item_col2 =\n" // 代码行
        + "mainrate.item_col2 \n" // 代码行
        + "                                AND    cr.item_col4 <=\n" // 代码行
        + "invoice.inv_id))) AS invamount,\n" // 代码行
        + "count(*)       AS invcount\n" // 代码行
        + "FROM    invoice\n" // 代码行
        + "WHERE  invoice.inv_amt < 10 AND  invoice.inv_amt > 0"; // 代码行
    ResultSet rs = calciteConnection.prepareStatement(statement).executeQuery(); // 代码行
    assertThat(rs.next(), is(true)); // 代码行
    assertThat(rs.getInt(1), is(10)); // 代码行
    assertThat(rs.getInt(2), is(3)); // 代码行
    assertThat(rs.next(), is(false)); // 代码行
    rs.close(); // 代码行
    calciteConnection.close(); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5414">[CALCITE-5414]</a> // JavaDoc注释内容
   * Convert between standard Gregorian and proleptic Gregorian calendars for // JavaDoc注释内容
   * literal dates in local time zone. */ // JavaDoc注释内容
  @Test void testLiteralDateToSqlTimestamp() { // 测试方法:测试LiteralDateToSqlTimestamp功能
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.TIME_ZONE, TimeZone.getDefault().getID()) // 代码行
        .query("select cast('1500-04-30' as date)") // 代码行
        .returns(resultSet -> { // 代码行
          try { // 代码行
            assertTrue(resultSet.next()); // 代码行
            assertThat(resultSet.getString(1), is("1500-04-30")); // 代码行
            assertThat(resultSet.getDate(1), is(Date.valueOf("1500-04-30"))); // 代码行
            assertFalse(resultSet.next()); // 代码行
          } catch (SQLException e) { // 代码行
            throw new RuntimeException(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5414">[CALCITE-5414]</a> // JavaDoc注释内容
   * Convert between standard Gregorian and proleptic Gregorian calendars for // JavaDoc注释内容
   * literal timestamps in local time zone. */ // JavaDoc注释内容
  @Test void testLiteralTimestampToSqlTimestamp() { // 测试方法:测试LiteralTimestampToSqlTimestamp功能
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.TIME_ZONE, TimeZone.getDefault().getID()) // 代码行
        .query("select cast('1500-04-30 12:00:00' as timestamp)") // 代码行
        .returns(resultSet -> { // 代码行
          try { // 代码行
            assertTrue(resultSet.next()); // 代码行
            assertThat(resultSet.getString(1), is("1500-04-30 12:00:00")); // 代码行
            assertThat(resultSet.getTimestamp(1), // 代码行
                is(Timestamp.valueOf("1500-04-30 12:00:00"))); // 代码行
            assertFalse(resultSet.next()); // 代码行
          } catch (SQLException e) { // 代码行
            throw new RuntimeException(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5414">[CALCITE-5414]</a> // JavaDoc注释内容
   * Convert between standard Gregorian and proleptic Gregorian calendars for // JavaDoc注释内容
   * dynamic dates in local time zone. */ // JavaDoc注释内容
  @Test void testDynamicDateToSqlTimestamp() { // 测试方法:测试DynamicDateToSqlTimestamp功能
    final Date date = Date.valueOf("1500-04-30"); // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.TIME_ZONE, TimeZone.getDefault().getID()) // 代码行
        .query("select cast(? as date)") // 代码行
        .consumesPreparedStatement(statement -> statement.setDate(1, date)) // 代码行
        .returns(resultSet -> { // 代码行
          try { // 代码行
            assertTrue(resultSet.next()); // 代码行
            assertThat(resultSet.getString(1), is("1500-04-30")); // 代码行
            assertThat(resultSet.getDate(1), is(date)); // 代码行
            assertFalse(resultSet.next()); // 代码行
          } catch (SQLException e) { // 代码行
            throw new RuntimeException(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  /** Test case for // JavaDoc注释开始
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5414">[CALCITE-5414]</a> // JavaDoc注释内容
   * Convert between standard Gregorian and proleptic Gregorian calendars for // JavaDoc注释内容
   * dynamic timestamps in local time zone. */ // JavaDoc注释内容
  @Test void testDynamicTimestampToSqlTimestamp() { // 测试方法:测试DynamicTimestampToSqlTimestamp功能
    final Timestamp timestamp = Timestamp.valueOf("1500-04-30 12:00:00"); // 代码行
    CalciteAssert.that() // 代码行
        .with(CalciteConnectionProperty.TIME_ZONE, TimeZone.getDefault().getID()) // 代码行
        .query("select cast(? as timestamp)") // 代码行
        .consumesPreparedStatement(statement -> statement.setTimestamp(1, timestamp)) // 代码行
        .returns(resultSet -> { // 代码行
          try { // 代码行
            assertTrue(resultSet.next()); // 代码行
            assertThat(resultSet.getString(1), is("1500-04-30 12:00:00")); // 代码行
            assertThat(resultSet.getTimestamp(1), is(timestamp)); // 代码行
            assertFalse(resultSet.next()); // 代码行
          } catch (SQLException e) { // 代码行
            throw new RuntimeException(e); // 代码行
          } // 代码行
        }); // 代码行
  } // 代码行
 // 空行
  @Test void bindByteParameter() { // 注解
    for (SqlTypeName tpe : SqlTypeName.INT_TYPES) { // 代码行
      final String sql = // 代码行
          "with cte as (select cast(100 as " + tpe.getName() + ") as empid)" // 代码行
              + "select * from cte where empid = ?"; // 代码行
      CalciteAssert.hr() // 代码行
          .query(sql) // 代码行
          .consumesPreparedStatement(p -> { // 代码行
            p.setByte(1, (byte) 100); // 代码行
          }) // 代码行
          .returnsUnordered("EMPID=100"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void bindShortParameter() { // 注解
    for (SqlTypeName tpe : SqlTypeName.INT_TYPES) { // 代码行
      final String sql = // 代码行
          "with cte as (select cast(100 as " + tpe.getName() + ") as empid)" // 代码行
              + "select * from cte where empid = ?"; // 代码行
 // 空行
      CalciteAssert.hr() // 代码行
          .query(sql) // 代码行
          .consumesPreparedStatement(p -> { // 代码行
            p.setShort(1, (short) 100); // 代码行
          }) // 代码行
          .returnsUnordered("EMPID=100"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void bindDecimalParameter() { // 注解
    final String sql = // 代码行
        "with cte as (select 2500.55 as val)" // 代码行
            + "select * from cte where val = ?"; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .consumesPreparedStatement(p -> { // 代码行
          p.setBigDecimal(1, new BigDecimal("2500.55")); // 代码行
        }) // 代码行
        .returnsUnordered("VAL=2500.55"); // 代码行
  } // 代码行
 // 空行
  @Test void bindNullParameter() { // 注解
    final String sql = // 代码行
        "with cte as (select 2500.55 as val)" // 代码行
            + "select * from cte where val = ?"; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .consumesPreparedStatement(p -> { // 代码行
          p.setBigDecimal(1, null); // 代码行
        }) // 代码行
        .returnsUnordered(""); // 代码行
  } // 代码行
 // 空行
  @Disabled("CALCITE-6366") // 禁用测试注解:标记当前测试方法为禁用状态
  @Test void bindOverflowingTinyIntParameter() { // 注解
    final String sql = // 代码行
        "with cte as (select cast(300 as smallint) as empid)" // 代码行
            + "select * from cte where empid = cast(? as tinyint)"; // 代码行
 // 空行
    java.sql.SQLException t = // 代码行
        assertThrows( // 代码行
          java.sql.SQLException.class, // 代码行
          () -> CalciteAssert.hr() // 代码行
            .query(sql) // 代码行
            .consumesPreparedStatement(p -> { // 代码行
              p.setShort(1, (short) 300); // 代码行
            }) // 代码行
            .returns("")); // 代码行
 // 空行
    assertThat( // 代码行
        "message matches", // 代码行
        t.getMessage().contains("value is outside the range of java.lang.Byte")); // 代码行
  } // 代码行
 // 空行
  @Test void bindIntParameter() { // 注解
    for (SqlTypeName tpe : SqlTypeName.INT_TYPES) { // 代码行
      final String sql = // 代码行
          "with cte as (select cast(100 as " + tpe.getName() + ") as empid)" // 代码行
              + "select * from cte where empid = ?"; // 代码行
 // 空行
      CalciteAssert.hr() // 代码行
          .query(sql) // 代码行
          .consumesPreparedStatement(p -> { // 代码行
            p.setInt(1, 100); // 代码行
          }) // 代码行
          .returnsUnordered("EMPID=100"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void bindLongParameter() { // 注解
    for (SqlTypeName tpe : SqlTypeName.INT_TYPES) { // 代码行
      final String sql = // 代码行
          "with cte as (select cast(100 as " + tpe.getName() + ") as empid)" // 代码行
              + "select * from cte where empid = ?"; // 代码行
 // 空行
      CalciteAssert.hr() // 代码行
          .query(sql) // 代码行
          .consumesPreparedStatement(p -> { // 代码行
            p.setLong(1, 100); // 代码行
          }) // 代码行
          .returnsUnordered("EMPID=100"); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  @Test void bindNumericParameter() { // 注解
    final String sql = // 代码行
        "with cte as (select cast(100 as numeric(5)) as empid)" // 代码行
            + "select * from cte where empid = ?"; // 代码行
 // 空行
    CalciteAssert.hr() // 代码行
        .query(sql) // 代码行
        .consumesPreparedStatement(p -> { // 代码行
          p.setLong(1, 100); // 代码行
        }) // 代码行
        .returnsUnordered("EMPID=100"); // 代码行
  } // 代码行
 // 空行
  private static String sums(int n, boolean c) { // sums方法:生成聚合函数调用字符串
    final StringBuilder b = new StringBuilder(); // 代码行
    for (int i = 0; i < n; i++) { // 代码行
      if (c) { // 代码行
        b.append(", sum(s.\"sale").append(i).append("\")"); // 代码行
      } else { // 代码行
        b.append(", sum(s.\"sale").append(i % 100).append("\"").append(" + ") // 代码行
            .append(i).append(")"); // 代码行
      } // 代码行
    } // 代码行
    return b.toString(); // 代码行
  } // 代码行
 // 空行
  // Disable checkstyle, so it doesn't complain about fields like "customer_id". // 单行注释
  //CHECKSTYLE: OFF // 单行注释
 // 空行
  public static class FoodmartJdbcSchema extends JdbcSchema { // FoodmartJdbcSchema内部类:FoodMart的JDBC Schema
    public FoodmartJdbcSchema(DataSource dataSource, SqlDialect dialect, // 代码行
        JdbcConvention convention, String catalog, String schema) { // 代码行
      super(dataSource, dialect, convention, catalog, schema); // 代码行
    } // 代码行
 // 空行
    public final Table customer = // 代码行
        requireNonNull(tables().get("customer")); // 代码行
  } // 代码行
 // 空行
  public static class Customer { // Customer内部类:Customer实体类
    public final int customer_id; // 代码行
 // 空行
    public Customer(int customer_id) { // 代码行
      this.customer_id = customer_id; // 代码行
    } // 代码行
 // 空行
    @Override public boolean equals(Object obj) { // 注解
      return obj == this // 代码行
          || obj instanceof Customer // 代码行
          && customer_id == ((Customer) obj).customer_id; // 代码行
    } // 代码行
  } // 代码行
 // 空行
  //CHECKSTYLE: ON // 单行注释
 // 空行
  /** Factory for EMP and DEPT tables. */ // JavaDoc注释开始
  public static class EmpDeptTableFactory implements TableFactory<Table> { // EmpDeptTableFactory内部类:EMP和DEPT表的工厂类
    public static final TryThreadLocal<List<Employee>> THREAD_COLLECTION = // 代码行
        TryThreadLocal.of(Collections.emptyList()); // 代码行
 // 空行
    public Table create( // 代码行
        SchemaPlus schema, // 代码行
        String name, // 代码行
        Map<String, Object> operand, // 代码行
        @Nullable RelDataType rowType) { // 注解
      final Class clazz; // 代码行
      final Object[] array; // 代码行
      switch (name) { // 代码行
      case "EMPLOYEES": // 代码行
        clazz = Employee.class; // 代码行
        array = new HrSchema().emps; // 代码行
        break; // 代码行
      case "MUTABLE_EMPLOYEES": // 代码行
        List<Employee> employees = THREAD_COLLECTION.get(); // 代码行
        return JdbcFrontLinqBackTest.mutable(name, employees, false); // 代码行
      case "DEPARTMENTS": // 代码行
        clazz = Department.class; // 代码行
        array = new HrSchema().depts; // 代码行
        break; // 代码行
      default: // 代码行
        throw new AssertionError(name); // 代码行
      } // 代码行
      return new AbstractQueryableTable(clazz) { // 代码行
        public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 代码行
          return ((JavaTypeFactory) typeFactory).createType(clazz); // 代码行
        } // 代码行
 // 空行
        public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 代码行
            SchemaPlus schema, String tableName) { // 代码行
          return new AbstractTableQueryable<T>(queryProvider, schema, this, // 代码行
              tableName) { // 代码行
            public Enumerator<T> enumerator() { // 代码行
              @SuppressWarnings("unchecked") final List<T> list = // 注解
                  (List) Arrays.asList(array); // 代码行
              return Linq4j.enumerator(list); // 代码行
            } // 代码行
          }; // 代码行
        } // 代码行
      }; // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Schema factory that creates {@link MySchema} objects. */ // JavaDoc注释开始
  public static class MySchemaFactory implements SchemaFactory { // MySchemaFactory内部类:自定义Schema的工厂类
    public Schema create( // 代码行
        SchemaPlus parentSchema, // 代码行
        String name, // 代码行
        final Map<String, Object> operand) { // 代码行
      final boolean mutable = // 代码行
          SqlFunctions.isNotFalse((Boolean) operand.get("mutable")); // 代码行
      return new ReflectiveSchema(new HrSchema()) { // 代码行
        @Override protected Map<String, Table> getTableMap() { // 注解
          // Mine the EMPS table and add it under another name e.g. ELVIS // 单行注释
          final Map<String, Table> tableMap = super.getTableMap(); // 代码行
          final Table table = tableMap.get("emps"); // 代码行
          final String tableName = (String) operand.get("tableName"); // 代码行
          return FlatLists.append(tableMap, tableName, table); // 代码行
        } // 代码行
 // 空行
        @Override public boolean isMutable() { // 注解
          return mutable; // 代码行
        } // 代码行
      }; // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Mock driver that has a handler that stores the results of each query in // JavaDoc注释开始
   * a temporary table. */ // JavaDoc注释内容
  public static class AutoTempDriver // 代码行
      extends org.apache.calcite.jdbc.Driver { // 代码行
    private final List<Object> results; // 代码行
 // 空行
    AutoTempDriver(List<Object> results) { // 代码行
      this.results = results; // 代码行
    } // 代码行
 // 空行
    @Override protected Handler createHandler() { // 注解
      return new HandlerImpl() { // 代码行
        @Override public void onStatementExecute( // 注解
            AvaticaStatement statement, // 代码行
            ResultSink resultSink) { // 代码行
          super.onStatementExecute(statement, resultSink); // 代码行
          results.add(resultSink); // 代码行
        } // 代码行
      }; // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Mock driver that a given {@link Handler}. */ // JavaDoc注释开始
  public static class HandlerDriver extends org.apache.calcite.jdbc.Driver { // HandlerDriver内部类:处理器驱动
    private static final TryThreadLocal<@Nullable Handler> HANDLERS = // 代码行
        TryThreadLocal.of(null); // 代码行
 // 空行
    public HandlerDriver() { // 代码行
    } // 代码行
 // 空行
    @Override protected Handler createHandler() { // 注解
      return requireNonNull(HANDLERS.get()); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Mock driver that can execute a trivial DDL statement. */ // JavaDoc注释开始
  public static class MockDdlDriver extends org.apache.calcite.jdbc.Driver { // MockDdlDriver内部类:模拟DDL驱动
  } // 代码行
 // 空行
  /** Mock driver that can execute a trivial DDL statement. */ // JavaDoc注释开始
  public static class MockDdlDriver2 extends MockDdlDriver { // MockDdlDriver2内部类:模拟DDL驱动2
    final AtomicInteger counter; // 代码行
 // 空行
    public MockDdlDriver2(AtomicInteger counter) { // 代码行
      this.counter = counter; // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Implementation of {@link CalcitePrepare} that counts how many DDL // JavaDoc注释开始
   * statements have been executed. */ // JavaDoc注释内容
  private static class CountingPrepare extends CalcitePrepareImpl { // 代码行
    private final AtomicInteger counter; // 代码行
 // 空行
    CountingPrepare(AtomicInteger counter) { // 代码行
      this.counter = counter; // 代码行
    } // 代码行
 // 空行
    @Override protected SqlParser.Config parserConfig() { // 注解
      return super.parserConfig().withParserFactory(stream -> // 代码行
          new SqlParserImpl(stream) { // 代码行
            @Override public SqlNode parseSqlStmtEof() { // 注解
              return new SqlCall(SqlParserPos.ZERO) { // 代码行
                @Override public SqlOperator getOperator() { // 注解
                  return new SqlSpecialOperator("COMMIT", // 代码行
                      SqlKind.COMMIT); // 代码行
                } // 代码行
 // 空行
                @Override public List<SqlNode> getOperandList() { // 注解
                  return ImmutableList.of(); // 代码行
                } // 代码行
              }; // 代码行
            } // 代码行
          }); // 代码行
    } // 代码行
 // 空行
    @Override public void executeDdl(Context context, SqlNode node) { // 注解
      counter.incrementAndGet(); // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Dummy subclass of CalcitePrepareImpl. */ // JavaDoc注释开始
  public static class MockPrepareImpl extends CalcitePrepareImpl { // 代码行
    public MockPrepareImpl() { // 代码行
    } // 代码行
  } // 代码行
 // 空行
  /** Dummy table. */ // JavaDoc注释开始
  public static class MyTable { // MyTable内部类:测试用的表类
    public String mykey = "foo"; // 代码行
    public Integer myvalue = 1; // 代码行
  } // 代码行
 // 空行
  /** Another dummy table. */ // JavaDoc注释开始
  public static class MyTable2 { // MyTable内部类:测试用的表类
    public String mykey = "foo"; // 代码行
    public Integer myvalue = 2; // 代码行
  } // 代码行
 // 空行
  /** Schema containing dummy tables. */ // JavaDoc注释开始
  public static class MySchema { // MySchema内部类:测试用的Schema
    public MyTable[] mytable = { new MyTable() }; // 代码行
    public MyTable2[] mytable2 = { new MyTable2() }; // 代码行
  } // 代码行
 // 空行
  /** Locales for which to test DAYNAME and MONTHNAME functions, // JavaDoc注释开始
   * and expected results of those functions. */ // JavaDoc注释内容
  enum TestLocale { // 代码行
    ROOT(Locale.ROOT.toString(), shorten("Wednesday"), shorten("Sunday"), // 代码行
        shorten("January"), shorten("February"), 0), // 代码行
    EN("en", "Wednesday", "Sunday", "January", "February", 0), // 代码行
    FR("fr", "mercredi", "dimanche", "janvier", "f\u00e9vrier", 6), // 代码行
    FR_FR("fr_FR", "mercredi", "dimanche", "janvier", "f\u00e9vrier", 6), // 代码行
    FR_CA("fr_CA", "mercredi", "dimanche", "janvier", "f\u00e9vrier", 6), // 代码行
    ZH_CN("zh_CN", "\u661f\u671f\u4e09", "\u661f\u671f\u65e5", "\u4e00\u6708", // 代码行
        "\u4e8c\u6708", 6), // 代码行
    ZH("zh", "\u661f\u671f\u4e09", "\u661f\u671f\u65e5", "\u4e00\u6708", // 代码行
        "\u4e8c\u6708", 6); // 代码行
 // 空行
    private static String shorten(String name) { // shorten方法:缩短名称字符串
      // In root locale, for Java versions 9 and higher, day and month names // 单行注释
      // are shortened to 3 letters. This means root locale behaves differently // 单行注释
      // to English. // 单行注释
      return TestUtil.getJavaMajorVersion() > 8 ? name.substring(0, 3) : name; // 代码行
    } // 代码行
 // 空行
    public final String localeName; // 代码行
    public final String wednesday; // 代码行
    public final String sunday; // 代码行
    public final String january; // 代码行
    public final String february; // 代码行
    public final int sundayDayOfWeek; // 代码行
 // 空行
    TestLocale(String localeName, String wednesday, String sunday, // 代码行
        String january, String february, int sundayDayOfWeek) { // 代码行
      this.localeName = localeName; // 代码行
      this.wednesday = wednesday; // 代码行
      this.sunday = sunday; // 代码行
      this.january = january; // 代码行
      this.february = february; // 代码行
      this.sundayDayOfWeek = sundayDayOfWeek; // 代码行
    } // 代码行
  } // 代码行
} // 代码行
