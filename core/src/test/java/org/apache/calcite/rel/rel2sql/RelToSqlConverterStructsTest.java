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
package org.apache.calcite.rel.rel2sql; // 指定当前类所在的包路径，org.apache.calcite.rel.rel2sql包包含了关系代数到SQL转换相关的类

import org.apache.calcite.sql.dialect.CalciteSqlDialect; // 导入Calcite SQL方言类，用于定义Calcite特定的SQL语法和格式
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器类，用于将SQL字符串解析为抽象语法树(AST)
import org.apache.calcite.test.CalciteAssert; // 导入Calcite测试工具类，提供测试断言和schema规范定义

import com.google.common.collect.ImmutableList; // 导入Google Guava库的不可变列表类，用于创建不可修改的列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava库的不可变集合类，用于创建不可修改的集合

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.function.UnaryOperator; // 导入Java函数式接口，表示对单个操作数进行操作并返回相同类型结果的一元运算符

/**
 * Tests for {@link RelToSqlConverter} on a schema that has nested structures of multiple
 * levels.
 */
// 这是一个测试类，用于测试RelToSqlConverter在具有多级嵌套结构的schema上的转换功能
// RelToSqlConverter是Calcite中将关系代数(RelNode)转换为SQL的核心转换器
// 该测试类专门针对包含复杂嵌套结构的schema进行测试，验证转换器能否正确处理多层嵌套的数据结构
class RelToSqlConverterStructsTest { // 定义测试类RelToSqlConverterStructsTest，继承自Object类（默认）

  // 定义一个辅助方法sql，用于创建RelToSqlConverterTest.Sql对象，该方法封装了测试SQL的创建逻辑
  // 参数sql: 输入的SQL查询字符串
  // 返回值: 返回一个配置好的RelToSqlConverterTest.Sql对象，用于后续的测试验证
  private RelToSqlConverterTest.Sql sql(String sql) { // 私有方法，只能在当前类内部调用
    // 创建并返回一个新的RelToSqlConverterTest.Sql对象，该对象封装了测试所需的所有配置信息
    // 参数1: CalciteAssert.SchemaSpec.MY_DB - 指定使用MY_DB这个schema规范，该schema包含具有嵌套结构的表
    // 参数2: sql - 输入的SQL查询字符串
    // 参数3: CalciteSqlDialect.DEFAULT - 使用默认的Calcite SQL方言进行转换
    // 参数4: SqlParser.Config.DEFAULT - 使用默认的SQL解析器配置
    // 参数5: ImmutableSet.of() - 空的不可变集合，用于指定额外的配置选项
    // 参数6: UnaryOperator.identity() - 一元操作符的恒等函数，表示不做任何转换
    // 参数7: null - 表示没有额外的配置参数
    // 参数8: ImmutableList.of() - 空的不可变列表，用于指定额外的配置列表
    return new RelToSqlConverterTest.Sql(CalciteAssert.SchemaSpec.MY_DB, sql,
        CalciteSqlDialect.DEFAULT, SqlParser.Config.DEFAULT, ImmutableSet.of(),
        UnaryOperator.identity(), null, ImmutableList.of());
  } // 方法结束，返回配置好的Sql对象

  // 测试方法：测试在嵌套schema上执行SELECT *查询时的SQL转换功能
  // 该测试验证转换器能否正确展开所有列，包括多层嵌套的结构
  @Test void testNestedSchemaSelectStar() { // 使用@Test注解标记为测试方法
    // 定义输入查询字符串，使用SELECT *选择myTable表的所有列
    String query = "SELECT * FROM \"myTable\""; // query变量存储输入的SQL查询
    // 定义期望的输出SQL字符串，验证转换器是否正确展开了嵌套结构
    // 期望输出包含：
    // 1. 根级别列："a"和"e"
    // 2. 嵌套列"n1"，它包含两层嵌套：n1.n11.b和n1.n12.c，使用ROW()函数构造
    // 3. 嵌套列"n2"，它包含一层嵌套：n2.d，使用ROW()函数构造
    // 4. 数组列"xs"
    // 5. 根级别列"e"
    String expected = "SELECT \"a\", " // 期望输出的SELECT子句，选择列"a"
        + "ROW(ROW(\"n1\".\"n11\".\"b\"), ROW(\"n1\".\"n12\".\"c\")) AS \"n1\", " // 使用ROW()构造嵌套结构n1，包含n11.b和n12.c两个子结构
        + "ROW(\"n2\".\"d\") AS \"n2\", \"xs\", " // 使用ROW()构造嵌套结构n2，包含d字段，以及数组列xs
        + "\"e\"\n" // 选择列"e"，并添加换行符
        + "FROM \"myDb\".\"myTable\""; // FROM子句，指定从myDb.myTable表查询
    // 调用sql()方法创建测试对象，然后调用ok()方法验证转换结果是否与期望的expected字符串匹配
    sql(query).ok(expected); // 如果转换结果与expected匹配，测试通过；否则测试失败
  } // 测试方法结束

  // 测试方法：测试在嵌套schema上只选择根级别列时的SQL转换功能
  // 该测试验证转换器能否正确处理只选择根级别列的情况，不涉及嵌套列
  @Test void testNestedSchemaRootColumns() { // 使用@Test注解标记为测试方法
    // 定义输入查询字符串，只选择根级别的"a"和"e"两列
    String query = "SELECT \"a\", \"e\" FROM \"myTable\""; // query变量存储只选择根级别列的SQL查询
    // 定义期望的输出SQL字符串，验证转换器是否正确生成了简单的SELECT语句
    // 期望输出只包含根级别的"a"和"e"两列，不包含任何嵌套结构
    String expected = "SELECT \"a\", " // 期望输出的SELECT子句，选择列"a"
        + "\"e\"\n" // 选择列"e"，并添加换行符
        + "FROM \"myDb\".\"myTable\""; // FROM子句，指定从myDb.myTable表查询
    // 调用sql()方法创建测试对象，然后调用ok()方法验证转换结果是否与期望的expected字符串匹配
    sql(query).ok(expected); // 如果转换结果与expected匹配，测试通过；否则测试失败
  } // 测试方法结束

  // 测试方法：测试在嵌套schema上选择嵌套列时的SQL转换功能
  // 该测试验证转换器能否正确处理选择多层嵌套列的情况，包括路径引用的简化
  @Test void testNestedSchemaNestedColumns() { // 使用@Test注解标记为测试方法
    // 定义输入查询字符串，选择根级别列和嵌套列
    // 使用完整路径引用嵌套列：myTable.n1.n11.b和myTable.n2.d
    String query = "SELECT \"a\", \"e\", " // 选择根级别列"a"和"e"
        + "\"myTable\".\"n1\".\"n11\".\"b\", " // 使用完整路径选择嵌套列n1.n11.b
        + "\"myTable\".\"n2\".\"d\" " // 使用完整路径选择嵌套列n2.d
        + "FROM \"myTable\""; // FROM子句，指定从myTable表查询
    // 定义期望的输出SQL字符串，验证转换器是否正确简化了嵌套列的路径引用
    // 期望输出中，嵌套列的路径被简化为n1.n11.b和n2.d，去掉了表名前缀
    String expected = "SELECT \"a\", " // 期望输出的SELECT子句，选择列"a"
        + "\"e\", " // 选择列"e"
        + "\"n1\".\"n11\".\"b\", " // 选择嵌套列n1.n11.b，路径已简化
        + "\"n2\".\"d\"\n" // 选择嵌套列n2.d，路径已简化，并添加换行符
        + "FROM \"myDb\".\"myTable\""; // FROM子句，指定从myDb.myTable表查询
    // 调用sql()方法创建测试对象，然后调用ok()方法验证转换结果是否与期望的expected字符串匹配
    sql(query).ok(expected); // 如果转换结果与expected匹配，测试通过；否则测试失败
  } // 测试方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6218">[CALCITE-6218]
   * RelToSqlConverter fails to convert correlated lateral joins</a>. */
  // 测试方法：测试关联横向联接(uncollect与lateral join)的SQL转换功能
  // 该测试是对CALCITE-6218问题的修复验证，该问题是RelToSqlConverter无法正确转换关联的lateral join
  // 关联lateral join是指子查询引用了外部查询的列，需要使用相关变量(correlation variable)来处理
  @Test void testUncollectLateralJoin() { // 使用@Test注解标记为测试方法
    // 定义输入查询字符串，使用unnest函数展开数组列，并与主表进行关联查询
    // unnest是PostgreSQL等数据库中的函数，用于展开数组为多行
    final String query = "select \"a\",\n" // 选择列"a"，并添加换行符
        + "\"x\"\n" // 选择展开后的数组元素"x"，并添加换行符
        + "from \"myDb\".\"myTable\",\n" // FROM子句，从myDb.myTable表查询，并添加换行符
        + "unnest(\"xs\") as \"x\""; // 使用unnest函数展开xs数组列，别名为"x"，这是一个隐式的lateral join
    // 定义期望的输出SQL字符串，验证转换器是否正确处理了关联lateral join
    // 期望输出包含：
    // 1. 使用相关变量$cor0来引用外部查询的列
    // 2. 使用LATERAL关键字明确标识lateral join
    // 3. 使用UNNEST函数展开数组，并构造子查询来引用外部列
    final String expected = "SELECT \"$cor0\".\"a\", \"t10\".\"xs\" AS \"x\"\n" // SELECT子句，使用相关变量$cor0引用a列，t10表引用xs列别名为x
        + "FROM (SELECT \"a\", \"n1\".\"n11\".\"b\", \"n1\".\"n12\".\"c\", \"n2\".\"d\", \"xs\", \"e\"\n" // FROM子句，定义相关变量$cor0，它是一个子查询，包含myTable的所有列
        + "FROM \"myDb\".\"myTable\") AS \"$cor0\",\nLATERAL UNNEST((SELECT \"$cor0\".\"xs\"\n" // 使用LATERAL UNNEST进行关联展开，子查询中引用$cor0.xs
        + "FROM (VALUES (0)) AS \"t\" (\"ZERO\"))) AS \"t10\" (\"xs\")"; // 构造一个虚拟表t(VALUES (0))来支持子查询，结果别名为t10
    // 调用sql()方法创建测试对象，显式指定schema为MY_DB，然后调用ok()方法验证转换结果是否与期望的expected字符串匹配
    sql(query).schema(CalciteAssert.SchemaSpec.MY_DB).ok(expected); // 如果转换结果与expected匹配，测试通过；否则测试失败
  } // 测试方法结束
} // 类定义结束
