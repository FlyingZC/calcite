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
// Apache许可证声明，定义了代码的使用权限和限制条件
package org.apache.calcite.sql.validate;  // 定义包名，该测试类位于sql.validate包下
import org.apache.calcite.adapter.enumerable.EnumerableConvention;  // 导入可枚举约定接口，用于定义关系代数操作的执行约定
import org.apache.calcite.adapter.enumerable.EnumerableProject;  // 导入可枚举投影节点，表示投影操作
import org.apache.calcite.config.Lex;  // 导入词法策略枚举，定义SQL词法分析的大小写敏感策略
import org.apache.calcite.plan.RelTraitDef;  // 导入关系特征定义接口，用于定义关系代数的特征
import org.apache.calcite.plan.RelTraitSet;  // 导入关系特征集合类，用于存储关系节点的特征集合
import org.apache.calcite.rel.RelNode;  // 导入关系节点接口，表示关系代数树中的一个节点
import org.apache.calcite.schema.SchemaPlus;  // 导入模式加接口，表示数据库模式及其子模式
import org.apache.calcite.sql.SqlNode;  // 导入SQL节点接口，表示SQL语法树中的一个节点
import org.apache.calcite.sql.parser.SqlParser;  // 导入SQL解析器类，用于将SQL文本解析为SQL语法树
import org.apache.calcite.sql.parser.SqlParser.Config;  // 导入SQL解析器配置接口，用于配置解析器行为
import org.apache.calcite.test.CalciteAssert;  // 导入Calcite断言工具类，提供测试辅助方法
import org.apache.calcite.tools.FrameworkConfig;  // 导入框架配置接口，用于配置Calcite框架
import org.apache.calcite.tools.Frameworks;  // 导入框架工具类，用于创建框架实例
import org.apache.calcite.tools.Planner;  // 导入规划器接口，用于SQL查询的解析、验证和优化
import org.apache.calcite.tools.Program;  // 导入程序接口，定义关系代数转换程序
import org.apache.calcite.tools.Programs;  // 导入程序工具类，提供预定义的关系代数转换程序
import org.apache.calcite.tools.ValidationException;  // 导入验证异常类，表示SQL验证过程中的错误

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入可空注解，用于标记可能为null的参数
import org.junit.jupiter.api.Test;  // 导入JUnit5测试注解，标记测试方法

import java.util.List;  // 导入List接口，用于存储有序集合

import static org.hamcrest.CoreMatchers.anyOf;  // 导入Hamcrest匹配器，用于匹配多个条件中的任意一个
import static org.hamcrest.CoreMatchers.instanceOf;  // 导入Hamcrest匹配器，用于检查对象是否为指定类型的实例
import static org.hamcrest.CoreMatchers.is;  // 导入Hamcrest匹配器，用于检查值是否相等
import static org.hamcrest.MatcherAssert.assertThat;  // 导入Hamcrest断言方法，用于断言测试条件
import static org.hamcrest.Matchers.hasSize;  // 导入Hamcrest匹配器，用于检查集合大小
import static org.junit.jupiter.api.Assertions.assertThrows;  // 导入JUnit5断言方法，用于验证是否抛出指定异常

/**
 * Testing {@link SqlValidator} and {@link Lex}.
 * 测试SqlValidator（SQL验证器）和Lex（词法策略）的测试类
 * 该类主要测试不同数据库的词法策略（大小写敏感）对SQL解析和验证的影响
 * SqlValidator负责验证SQL语句的语义正确性，Lex定义了SQL词法分析的大小写敏感策略
 */
class LexCaseSensitiveTest {  // 定义测试类，用于测试不同词法策略下的SQL大小写敏感行为

  // 获取规划器的静态辅助方法
  // 该方法创建并配置一个Calcite规划器实例，用于SQL查询的解析、验证和优化
  // 参数traitDefs: 关系特征定义列表，可为null，用于定义关系代数的特征
  // 参数parserConfig: SQL解析器配置，用于指定词法策略等解析行为
  // 参数programs: 关系代数转换程序数组，用于定义查询优化规则
  // 返回值: 配置好的规划器实例
  private static Planner getPlanner(@Nullable List<RelTraitDef> traitDefs,  // 定义获取规划器的静态方法，参数为关系特征定义列表
      SqlParser.Config parserConfig, Program... programs) {  // SQL解析器配置和可变数量的关系代数转换程序
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);  // 创建根模式对象，true表示添加内置函数和类型
    final FrameworkConfig config = Frameworks.newConfigBuilder()  // 使用构建器模式创建框架配置
        .parserConfig(parserConfig)  // 设置SQL解析器配置，包括词法策略
        .defaultSchema(CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR))  // 设置默认模式为HR（人力资源）模式
        .traitDefs(traitDefs)  // 设置关系特征定义
        .programs(programs)  // 设置关系代数转换程序
        .build();  // 构建框架配置对象
    return Frameworks.getPlanner(config);  // 根据配置创建并返回规划器实例
  }

  // 使用指定词法策略运行项目查询的静态辅助方法
  // 该方法解析、验证、转换并优化SQL查询，验证不同词法策略下的行为
  // 参数lex: 词法策略，定义SQL标识符的大小写敏感行为
  // 参数sql: 要执行的SQL查询语句
  // 抛出: Exception，如果在查询处理过程中发生错误
  private static void runProjectQueryWithLex(Lex lex, String sql)  // 定义使用指定词法策略运行项目查询的静态方法
      throws Exception {  // 声明可能抛出异常
    Config javaLex = SqlParser.config().withLex(lex);  // 创建SQL解析器配置并设置指定的词法策略
    Planner planner = getPlanner(null, javaLex, Programs.ofRules(Programs.RULE_SET));  // 获取规划器，使用默认规则集
    SqlNode parse = planner.parse(sql);  // 解析SQL语句，生成SQL语法树（SqlNode）
    SqlNode validate = planner.validate(parse);  // 验证SQL语法树，检查语义正确性
    RelNode convert = planner.rel(validate).rel;  // 将验证后的SQL语法树转换为关系代数树（RelNode）
    RelTraitSet traitSet =  // 创建关系特征集合
        convert.getTraitSet().replace(EnumerableConvention.INSTANCE);  // 替换特征集合为可枚举约定
    RelNode transform = planner.transform(0, traitSet, convert);  // 使用指定特征集转换关系节点，进行查询优化
    assertThat(transform, instanceOf(EnumerableProject.class));  // 断言转换后的节点是可枚举投影节点
    List<String> fieldNames = transform.getRowType().getFieldNames();  // 获取结果行的字段名称列表
    assertThat(fieldNames, hasSize(2));  // 断言字段列表大小为2
    if (lex.caseSensitive) {  // 如果词法策略是大小写敏感的
      assertThat(fieldNames.get(0), is("EMPID"));  // 断言第一个字段名为"EMPID"
      assertThat(fieldNames.get(1), is("empid"));  // 断言第二个字段名为"empid"
    } else {  // 如果词法策略是大小写不敏感的
      assertThat(fieldNames.get(0) + "-" + fieldNames.get(1),  // 断言两个字段名的组合
          anyOf(is("EMPID-empid0"), is("EMPID0-empid")));  // 匹配两种可能的去重命名结果之一
    }
  }

  // 测试Oracle词法策略的测试方法
  // Oracle数据库对标识符大小写敏感，需要使用双引号来保持原始大小写
  // 该测试验证在Oracle词法策略下，正确使用双引号引用标识符的查询能够成功执行
  @Test void testCalciteCaseOracle() throws Exception {  // 定义测试方法，测试Oracle词法策略
    String sql = "select \"empid\" as EMPID, \"empid\" from\n"  // 定义SQL查询，使用双引号引用标识符，保持大小写
        + " (select \"empid\" from \"emps\" order by \"emps\".\"deptno\")";  // 子查询同样使用双引号引用表名和列名
    runProjectQueryWithLex(Lex.ORACLE, sql);  // 使用Oracle词法策略运行查询
  }

  // 测试Oracle词法策略下异常情况的测试方法
  // Oracle数据库对标识符大小写敏感，如果未使用双引号引用标识符，会导致验证失败
  // 该测试验证在Oracle词法策略下，未正确使用双引号引用标识符会抛出验证异常
  @Test void testCalciteCaseOracleException() {  // 定义测试方法，测试Oracle词法策略下的异常情况
    assertThrows(ValidationException.class, () -> {  // 断言会抛出验证异常
      // Oracle is case sensitive, so EMPID should not be found.  // 注释说明：Oracle大小写敏感，所以找不到EMPID
      String sql = "select EMPID, \"empid\" from\n"  // 定义SQL查询，EMPID未使用双引号，会被转换为小写
          + " (select \"empid\" from \"emps\" order by \"emps\".\"deptno\")";  // 子查询使用双引号引用标识符
      runProjectQueryWithLex(Lex.ORACLE, sql);  // 使用Oracle词法策略运行查询，预期会抛出验证异常
    });
  }

  // 测试MySQL词法策略的测试方法
  // MySQL数据库对标识符大小写不敏感，可以使用反引号引用标识符
  // 该测试验证在MySQL词法策略下，使用反引号引用标识符的查询能够成功执行
  @Test void testCalciteCaseMySql() throws Exception {  // 定义测试方法，测试MySQL词法策略
    String sql = "select empid as EMPID, empid from (\n"  // 定义SQL查询，标识符不使用引号，大小写不敏感
        + "  select empid from emps order by `EMPS`.DEPTNO)";  // 子查询使用反引号引用表名，DEPTNO不使用引号
    runProjectQueryWithLex(Lex.MYSQL, sql);  // 使用MySQL词法策略运行查询
  }

  // 测试MySQL词法策略下无异常情况的测试方法
  // MySQL数据库对标识符大小写不敏感，即使使用不同大小写的标识符也能正常工作
  // 该测试验证在MySQL词法策略下，使用不同大小写的标识符不会抛出异常
  @Test void testCalciteCaseMySqlNoException() throws Exception {  // 定义测试方法，测试MySQL词法策略下无异常
    String sql = "select EMPID, empid from\n"  // 定义SQL查询，使用不同大小写的标识符
        + " (select empid from emps order by emps.deptno)";  // 子查询使用小写标识符
    runProjectQueryWithLex(Lex.MYSQL, sql);  // 使用MySQL词法策略运行查询
  }

  // 测试MySQL ANSI词法策略的测试方法
  // MySQL ANSI模式遵循ANSI SQL标准，对标识符大小写不敏感，使用双引号引用标识符
  // 该测试验证在MySQL ANSI词法策略下，标识符大小写不敏感的查询能够成功执行
  @Test void testCalciteCaseMySqlAnsi() throws Exception {  // 定义测试方法，测试MySQL ANSI词法策略
    String sql = "select empid as EMPID, empid from (\n"  // 定义SQL查询，标识符不使用引号
        + "  select empid from emps order by EMPS.DEPTNO)";  // 子查询使用大写表名，大小写不敏感
    runProjectQueryWithLex(Lex.MYSQL_ANSI, sql);  // 使用MySQL ANSI词法策略运行查询
  }

  // 测试MySQL ANSI词法策略下无异常情况的测试方法
  // MySQL ANSI模式对标识符大小写不敏感，即使使用不同大小写的标识符也能正常工作
  // 该测试验证在MySQL ANSI词法策略下，使用不同大小写的标识符不会抛出异常
  @Test void testCalciteCaseMySqlAnsiNoException() throws Exception {  // 定义测试方法，测试MySQL ANSI词法策略下无异常
    String sql = "select EMPID, empid from\n"  // 定义SQL查询，使用不同大小写的标识符
        + " (select empid from emps order by emps.deptno)";  // 子查询使用小写标识符
    runProjectQueryWithLex(Lex.MYSQL_ANSI, sql);  // 使用MySQL ANSI词法策略运行查询
  }

  // 测试SQL Server词法策略的测试方法
  // SQL Server数据库对标识符大小写不敏感，可以使用方括号引用标识符
  // 该测试验证在SQL Server词法策略下，标识符大小写不敏感的查询能够成功执行
  @Test void testCalciteCaseSqlServer() throws Exception {  // 定义测试方法，测试SQL Server词法策略
    String sql = "select empid as EMPID, empid from (\n"  // 定义SQL查询，标识符不使用引号
        + "  select empid from emps order by EMPS.DEPTNO)";  // 子查询使用大写表名，大小写不敏感
    runProjectQueryWithLex(Lex.SQL_SERVER, sql);  // 使用SQL Server词法策略运行查询
  }

  // 测试SQL Server词法策略下无异常情况的测试方法
  // SQL Server数据库对标识符大小写不敏感，即使使用不同大小写的标识符也能正常工作
  // 该测试验证在SQL Server词法策略下，使用不同大小写的标识符不会抛出异常
  @Test void testCalciteCaseSqlServerNoException() throws Exception {  // 定义测试方法，测试SQL Server词法策略下无异常
    String sql = "select EMPID, empid from\n"  // 定义SQL查询，使用不同大小写的标识符
        + " (select empid from emps order by emps.deptno)";  // 子查询使用小写标识符
    runProjectQueryWithLex(Lex.SQL_SERVER, sql);  // 使用SQL Server词法策略运行查询
  }

  // 测试Java词法策略的测试方法
  // Java词法策略对标识符大小写敏感，类似于Java语言的标识符规则
  // 该测试验证在Java词法策略下，正确使用标识符的查询能够成功执行
  @Test void testCalciteCaseJava() throws Exception {  // 定义测试方法，测试Java词法策略
    String sql = "select empid as EMPID, empid from (\n"  // 定义SQL查询，标识符使用小写
        + "  select empid from emps order by emps.deptno)";  // 子查询使用小写标识符
    runProjectQueryWithLex(Lex.JAVA, sql);  // 使用Java词法策略运行查询
  }

  // 测试Java词法策略下异常情况的测试方法
  // Java词法策略对标识符大小写敏感，如果使用不同大小写的标识符会导致验证失败
  // 该测试验证在Java词法策略下，使用不同大小写的标识符会抛出验证异常
  @Test void testCalciteCaseJavaException() {  // 定义测试方法，测试Java词法策略下的异常情况
    assertThrows(ValidationException.class, () -> {  // 断言会抛出验证异常
      // JAVA is case sensitive, so EMPID should not be found.  // 注释说明：Java大小写敏感，所以找不到EMPID
      String sql = "select EMPID, empid from\n"  // 定义SQL查询，使用大写EMPID，但子查询使用小写empid
          + " (select empid from emps order by emps.deptno)";  // 子查询使用小写标识符
      runProjectQueryWithLex(Lex.JAVA, sql);  // 使用Java词法策略运行查询，预期会抛出验证异常
    });
  }

  // 测试Oracle词法策略下JOIN查询的测试方法
  // Oracle数据库对标识符大小写敏感，JOIN查询中需要使用双引号引用标识符
  // 该测试验证在Oracle词法策略下，使用双引号引用标识符的JOIN查询能够成功执行
  @Test void testCalciteCaseJoinOracle() throws Exception {  // 定义测试方法，测试Oracle词法策略下的JOIN查询
    String sql = "select t.\"empid\" as EMPID, s.\"empid\" from\n"  // 定义JOIN查询，使用双引号引用列名
        + "(select * from \"emps\" where \"emps\".\"deptno\" > 100) t join\n"  // 左子查询，使用双引号引用表名和列名
        + "(select * from \"emps\" where \"emps\".\"deptno\" < 200) s\n"  // 右子查询，使用双引号引用表名和列名
        + "on t.\"empid\" = s.\"empid\"";  // JOIN条件，使用双引号引用列名
    runProjectQueryWithLex(Lex.ORACLE, sql);  // 使用Oracle词法策略运行查询
  }

  // 测试MySQL词法策略下JOIN查询的测试方法
  // MySQL数据库对标识符大小写不敏感，JOIN查询中可以使用不同大小写的标识符
  // 该测试验证在MySQL词法策略下，标识符大小写不敏感的JOIN查询能够成功执行
  @Test void testCalciteCaseJoinMySql() throws Exception {  // 定义测试方法，测试MySQL词法策略下的JOIN查询
    String sql = "select t.empid as EMPID, s.empid from\n"  // 定义JOIN查询，标识符不使用引号
        + "(select * from emps where emps.deptno > 100) t join\n"  // 左子查询，使用小写标识符
        + "(select * from emps where emps.deptno < 200) s on t.empid = s.empid";  // 右子查询和JOIN条件，使用小写标识符
    runProjectQueryWithLex(Lex.MYSQL, sql);  // 使用MySQL词法策略运行查询
  }

  // 测试MySQL ANSI词法策略下JOIN查询的测试方法
  // MySQL ANSI模式对标识符大小写不敏感，JOIN查询中可以使用不同大小写的标识符
  // 该测试验证在MySQL ANSI词法策略下，标识符大小写不敏感的JOIN查询能够成功执行
  @Test void testCalciteCaseJoinMySqlAnsi() throws Exception {  // 定义测试方法，测试MySQL ANSI词法策略下的JOIN查询
    String sql = "select t.empid as EMPID, s.empid from\n"  // 定义JOIN查询，标识符不使用引号
        + "(select * from emps where emps.deptno > 100) t join\n"  // 左子查询，使用小写标识符
        + "(select * from emps where emps.deptno < 200) s on t.empid = s.empid";  // 右子查询和JOIN条件，使用小写标识符
    runProjectQueryWithLex(Lex.MYSQL_ANSI, sql);  // 使用MySQL ANSI词法策略运行查询
  }

  // 测试SQL Server词法策略下JOIN查询的测试方法
  // SQL Server数据库对标识符大小写不敏感，JOIN查询中可以使用不同大小写的标识符
  // 该测试验证在SQL Server词法策略下，标识符大小写不敏感的JOIN查询能够成功执行
  @Test void testCalciteCaseJoinSqlServer() throws Exception {  // 定义测试方法，测试SQL Server词法策略下的JOIN查询
    String sql = "select t.empid as EMPID, s.empid from\n"  // 定义JOIN查询，标识符不使用引号
        + "(select * from emps where emps.deptno > 100) t join\n"  // 左子查询，使用小写标识符
        + "(select * from emps where emps.deptno < 200) s on t.empid = s.empid";  // 右子查询和JOIN条件，使用小写标识符
    runProjectQueryWithLex(Lex.SQL_SERVER, sql);  // 使用SQL Server词法策略运行查询
  }

  // 测试Java词法策略下JOIN查询的测试方法
  // Java词法策略对标识符大小写敏感，JOIN查询中需要保持标识符大小写一致
  // 该测试验证在Java词法策略下，保持标识符大小写一致的JOIN查询能够成功执行
  @Test void testCalciteCaseJoinJava() throws Exception {  // 定义测试方法，测试Java词法策略下的JOIN查询
    String sql = "select t.empid as EMPID, s.empid from\n"  // 定义JOIN查询，标识符使用小写
        + "(select * from emps where emps.deptno > 100) t join\n"  // 左子查询，使用小写标识符
        + "(select * from emps where emps.deptno < 200) s on t.empid = s.empid";  // 右子查询和JOIN条件，使用小写标识符
    runProjectQueryWithLex(Lex.JAVA, sql);  // 使用Java词法策略运行查询
  }

  // 测试BigQuery词法策略的测试方法
  // BigQuery数据库对标识符大小写不敏感，可以使用不同大小写的标识符
  // 该测试验证在BigQuery词法策略下，标识符大小写不敏感的查询能够成功执行
  @Test void testCalciteCaseBigQuery() throws Exception {  // 定义测试方法，测试BigQuery词法策略
    String sql = "select empid as EMPID, empid from (\n"  // 定义SQL查询，标识符使用小写
        + "  select empid from emps order by EMPS.DEPTNO)";  // 子查询使用大写表名，大小写不敏感
    runProjectQueryWithLex(Lex.BIG_QUERY, sql);  // 使用BigQuery词法策略运行查询
  }

  // 测试BigQuery词法策略下无异常情况的测试方法
  // BigQuery数据库对标识符大小写不敏感，即使使用不同大小写的标识符也能正常工作
  // 该测试验证在BigQuery词法策略下，使用不同大小写的标识符不会抛出异常
  @Test void testCalciteCaseBigQueryNoException() throws Exception {  // 定义测试方法，测试BigQuery词法策略下无异常
    String sql = "select EMPID, empid from\n"  // 定义SQL查询，使用不同大小写的标识符
        + " (select empid from emps order by emps.deptno)";  // 子查询使用小写标识符
    runProjectQueryWithLex(Lex.BIG_QUERY, sql);  // 使用BigQuery词法策略运行查询
  }

  /** Test case for  // 测试用例，针对
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5291">[CALCITE-5291]  // JIRA问题CALCITE-5291的链接
   * Make BigQuery lexical policy case insensitive</a>.  // 使BigQuery词法策略大小写不敏感
   *
   * <p>In reality, BigQuery treats table names as case-sensitive, column names  // 实际上，BigQuery将表名视为大小写敏感，列名
   * and aliases as case-insensitive. Calcite cannot currently support a hybrid  // 和别名视为大小写不敏感。Calcite目前不支持混合
   * policy, so it treats table names as case-insensitive. */  // 策略，因此将表名视为大小写不敏感。
  @Test void testCalciteCaseJoinBigQuery() throws Exception {  // 定义测试方法，测试BigQuery词法策略下的JOIN查询
    String sql = "select t.empid as EMPID, s.empid from\n"  // 定义JOIN查询，标识符使用小写
        + "(select * from emps where emps.deptno > 100) t join\n"  // 左子查询，使用小写标识符
        + "(select * from emps where emps.deptno < 200) s on t.empid = s.empid";  // 右子查询和JOIN条件，使用小写标识符
    runProjectQueryWithLex(Lex.BIG_QUERY, sql);  // 使用BigQuery词法策略运行查询
  }
}
