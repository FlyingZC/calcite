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
package org.apache.calcite.sql.test;

import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.advise.SqlAdvisor;
import org.apache.calcite.sql.advise.SqlAdvisorValidator;
import org.apache.calcite.sql.advise.SqlSimpleParser;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.StringAndPos;
import org.apache.calcite.sql.validate.SqlMoniker;
import org.apache.calcite.sql.validate.SqlMonikerType;
import org.apache.calcite.test.SqlValidatorFixture;
import org.apache.calcite.test.SqlValidatorTestCase;

import com.google.common.collect.ImmutableMap;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import static java.util.Objects.requireNonNull;

/**
 * Concrete child class of {@link SqlValidatorTestCase}, containing unit tests
 * for SqlAdvisor.
 * SqlAdvisorTest是SqlValidatorTestCase的具体子类,包含SqlAdvisor的单元测试
 * SqlAdvisor是Calcite提供的SQL建议器,用于为不完整或错误的SQL语句提供自动补全和建议功能
 * 该测试类主要测试以下功能:
 * 1. SQL语句的自动补全功能,包括表名、列名、关键字等
 * 2. SQL语句的简化功能,将复杂的SQL语句简化为易于分析的形式
 * 3. 不同SQL语法场景下的建议功能,如FROM子句、WHERE子句、JOIN等
 * 4. 标识符的补全,包括部分输入的标识符
 * 5. 子查询中的建议功能
 * 6. 不同SQL方言的支持(如Java、MySQL、SQL Server等)
 * 7. 注释和字符串的处理
 * 8. 集合操作(UNION、INTERSECT等)的建议功能
 */
@SuppressWarnings({"unchecked", "ArraysAsListWithZeroOrOneArgument"})
class SqlAdvisorTest extends SqlValidatorTestCase { // SqlAdvisorTest继承自SqlValidatorTestCase,用于测试SqlAdvisor的各种功能
  public static final SqlTestFactory ADVISOR_NEW_TEST_FACTORY =
      SqlTestFactory.INSTANCE.withValidator(SqlAdvisorValidator::new); // 创建SqlTestFactory实例,使用SqlAdvisorValidator作为验证器,用于创建SqlAdvisor测试环境

  static final Fixture LOCAL_FIXTURE =
      new Fixture(SqlValidatorTester.DEFAULT, ADVISOR_NEW_TEST_FACTORY,
          StringAndPos.of("?"), false, false); // 本地测试夹具,使用默认验证器、ADVISOR_NEW_TEST_FACTORY工厂、字符串"?"作为占位符,非表达式模式,非完整模式

  private static final List<String> STAR_KEYWORD =
      Collections.singletonList(
          "KEYWORD(*)"); // 星号关键字列表,用于SQL中的通配符选择所有列

  protected static final List<String> FROM_KEYWORDS =
      Arrays.asList(
          "KEYWORD(()", // 左括号关键字
          "KEYWORD(LATERAL)", // LATERAL关键字,用于横向连接
          "KEYWORD(TABLE)", // TABLE关键字
          "KEYWORD(UNNEST)"); // UNNEST关键字,用于展开数组

  protected static final List<String> SALES_TABLES =
      Arrays.asList(
          "SCHEMA(CATALOG.SALES)", // SALES模式
          "SCHEMA(CATALOG.SALES.NEST)", // SALES模式下的NEST嵌套模式
          "TABLE(CATALOG.SALES.EMP)", // 员工表
          "TABLE(CATALOG.SALES.EMPDEFAULTS)", // 员工默认值表
          "TABLE(CATALOG.SALES.EMPNULLABLES)", // 包含可空列的员工表
          "TABLE(CATALOG.SALES.EMP_B)", // 员工表B
          "TABLE(CATALOG.SALES.EMP_20)", // 部门20的员工表
          "TABLE(CATALOG.SALES.EMPNULLABLES_20)", // 部门20的可空员工表
          "TABLE(CATALOG.SALES.EMPTY_PRODUCTS)", // 空产品表
          "TABLE(CATALOG.SALES.EMP_ADDRESS)", // 员工地址表
          "TABLE(CATALOG.SALES.DEPT)", // 部门表
          "TABLE(CATALOG.SALES.DEPTNULLABLES)", // 包含可空列的部门表
          "TABLE(CATALOG.SALES.DEPT_SINGLE)", // 单行部门表
          "TABLE(CATALOG.SALES.DEPT_NESTED)", // 嵌套部门表
          "TABLE(CATALOG.SALES.DEPT_NESTED_EXPANDED)", // 展开的嵌套部门表
          "TABLE(CATALOG.SALES.BONUS)", // 奖金表
          "TABLE(CATALOG.SALES.ORDERS)", // 订单表
          "TABLE(CATALOG.SALES.SALGRADE)", // 薪资等级表
          "TABLE(CATALOG.SALES.SHIPMENTS)", // 装运表
          "TABLE(CATALOG.SALES.PRODUCTS)", // 产品表
          "TABLE(CATALOG.SALES.PRODUCTS_TEMPORAL)", // 临时产品表
          "TABLE(CATALOG.SALES.SUPPLIERS)", // 供应商表
          "TABLE(CATALOG.SALES.EMP_R)", // 只读员工表
          "TABLE(CATALOG.SALES.DEPT_R)"); // 只读部门表

  private static final List<String> SCHEMAS =
      Arrays.asList(
          "CATALOG(CATALOG)", // CATALOG目录
          "SCHEMA(CATALOG.SALES)", // SALES模式
          "SCHEMA(CATALOG.STRUCT)", // STRUCT结构模式
          "SCHEMA(CATALOG.CUSTOMER)", // CUSTOMER客户模式
          "SCHEMA(CATALOG.SALES.NEST)"); // SALES模式下的NEST嵌套模式

  private static final List<String> AB_TABLES =
      Arrays.asList(
          "TABLE(A)", // 表A
          "TABLE(B)"); // 表B

  private static final List<String> EMP_TABLE =
      Collections.singletonList(
          "TABLE(EMP)"); // EMP表

  protected static final List<String> FETCH_OFFSET =
      Arrays.asList(
          "KEYWORD(FETCH)", // FETCH关键字,用于获取行
          "KEYWORD(LIMIT)", // LIMIT关键字,用于限制返回行数
          "KEYWORD(OFFSET)"); // OFFSET关键字,用于跳过行

  protected static final List<String> EXPR_KEYWORDS =
      Arrays.asList(
          "KEYWORD(()", // 左括号
          "KEYWORD(+)", // 加号运算符
          "KEYWORD(-)", // 减号运算符
          "KEYWORD(?)", // 参数占位符
          "KEYWORD(ABS)", // ABS绝对值函数
          "KEYWORD(ARRAY)", // ARRAY数组构造函数
          "KEYWORD(AVG)", // AVG平均值聚合函数
          "KEYWORD(CARDINALITY)", // CARDINALITY计算集合基数函数
          "KEYWORD(CASE)", // CASE条件表达式
          "KEYWORD(CAST)", // CAST类型转换函数
          "KEYWORD(CEIL)", // CEIL向上取整函数
          "KEYWORD(CEILING)", // CEILING向上取整函数(同CEIL)
          "KEYWORD(CHAR)", // CHAR字符函数
          "KEYWORD(CHARACTER_LENGTH)", // CHARACTER_LENGTH字符串长度函数
          "KEYWORD(CHAR_LENGTH)", // CHAR_LENGTH字符串长度函数
          "KEYWORD(CLASSIFIER)", // CLASSIFIER分类函数
          "KEYWORD(COALESCE)", // COALESCE返回第一个非空值函数
          "KEYWORD(COLLECT)", // COLLECT集合聚合函数
          "KEYWORD(CONVERT)", // CONVERT转换函数
          "KEYWORD(COUNT)", // COUNT计数聚合函数
          "KEYWORD(COVAR_POP)", // COVAR_POP总体协方差函数
          "KEYWORD(COVAR_SAMP)", // COVAR_SAMP样本协方差函数
          "KEYWORD(CUME_DIST)", // CUME_DIST累积分布函数
          "KEYWORD(CURRENT)", // CURRENT当前值函数
          "KEYWORD(CURRENT_CATALOG)", // CURRENT_CATALOG当前目录函数
          "KEYWORD(CURRENT_DATE)", // CURRENT_DATE当前日期函数
          "KEYWORD(CURRENT_DEFAULT_TRANSFORM_GROUP)", // CURRENT_DEFAULT_TRANSFORM_GROUP当前默认转换组
          "KEYWORD(CURRENT_PATH)", // CURRENT_PATH当前路径函数
          "KEYWORD(CURRENT_ROLE)", // CURRENT_ROLE当前角色函数
          "KEYWORD(CURRENT_SCHEMA)", // CURRENT_SCHEMA当前模式函数
          "KEYWORD(CURRENT_TIME)", // CURRENT_TIME当前时间函数
          "KEYWORD(CURRENT_TIMESTAMP)", // CURRENT_TIMESTAMP当前时间戳函数
          "KEYWORD(CURRENT_USER)", // CURRENT_USER当前用户函数
          "KEYWORD(CURSOR)", // CURSOR游标关键字
          "KEYWORD(DATE)", // DATE日期类型
          "KEYWORD(DATETIME)", // DATETIME日期时间类型
          "KEYWORD(DECIMAL)", // DECIMAL小数类型
          "KEYWORD(DENSE_RANK)", // DENSE_RANK密集排名函数
          "KEYWORD(ELEMENT)", // ELEMENT元素函数
          "KEYWORD(EVERY)", // EVERY全量聚合函数
          "KEYWORD(EXISTS)", // EXISTS存在量词
          "KEYWORD(EXP)", // EXP指数函数
          "KEYWORD(EXTRACT)", // EXTRACT提取函数
          "KEYWORD(FALSE)", // FALSE布尔值
          "KEYWORD(FIRST_VALUE)", // FIRST_VALUE第一个值函数
          "KEYWORD(FLOOR)", // FLOOR向下取整函数
          "KEYWORD(FUSION)", // FUSION融合聚合函数
          "KEYWORD(GROUPING)", // GROUPING分组函数
          "KEYWORD(HOUR)", // HOUR小时函数
          "KEYWORD(INTERSECTION)", // INTERSECTION交集函数
          "KEYWORD(INTERVAL)", // INTERVAL间隔类型
          "KEYWORD(JSON_ARRAY)", // JSON_ARRAY JSON数组函数
          "KEYWORD(JSON_ARRAYAGG)", // JSON_ARRAYAGG JSON数组聚合函数
          "KEYWORD(JSON_EXISTS)", // JSON_EXISTS JSON存在函数
          "KEYWORD(JSON_OBJECT)", // JSON_OBJECT JSON对象函数
          "KEYWORD(JSON_OBJECTAGG)", // JSON_OBJECTAGG JSON对象聚合函数
          "KEYWORD(JSON_QUERY)", // JSON_QUERY JSON查询函数
          "KEYWORD(JSON_VALUE)", // JSON_VALUE JSON值函数
          "KEYWORD(LAG)", // LAG滞后函数
          "KEYWORD(LAST_VALUE)", // LAST_VALUE最后一个值函数
          "KEYWORD(LEAD)", // LEAD超前函数
          "KEYWORD(LEFT)", // LEFT左子串函数
          "KEYWORD(LN)", // LN自然对数函数
          "KEYWORD(LOCALTIME)", // LOCALTIME本地时间函数
          "KEYWORD(LOCALTIMESTAMP)", // LOCALTIMESTAMP本地时间戳函数
          "KEYWORD(LOWER)", // LOWER转小写函数
          "KEYWORD(MATCH_NUMBER)", // MATCH_NUMBER匹配编号函数
          "KEYWORD(MAX)", // MAX最大值聚合函数
          "KEYWORD(MIN)", // MIN最小值聚合函数
          "KEYWORD(MINUTE)", // MINUTE分钟函数
          "KEYWORD(MOD)", // MOD取模函数
          "KEYWORD(MONTH)", // MONTH月份函数
          "KEYWORD(MULTISET)", // MULTISET多重集类型
          "KEYWORD(NEW)", // NEW新建关键字
          "KEYWORD(NEXT)", // NEXT下一个关键字
          "KEYWORD(NOT)", // NOT非运算符
          "KEYWORD(NTH_VALUE)", // NTH_VALUE第N个值函数
          "KEYWORD(NTILE)", // NTILE分桶函数
          "KEYWORD(NULL)", // NULL空值
          "KEYWORD(NULLIF)", // NULLIF空值判断函数
          "KEYWORD(OCTET_LENGTH)", // OCTET_LENGTH字节长度函数
          "KEYWORD(OVERLAY)", // OVERLAY覆盖函数
          "KEYWORD(PERCENTILE_CONT)", // PERCENTILE_CONT连续百分位函数
          "KEYWORD(PERCENTILE_DISC)", // PERCENTILE_DISC离散百分位函数
          "KEYWORD(PERCENT_RANK)", // PERCENT_RANK百分位排名函数
          "KEYWORD(PERIOD)", // PERIOD周期关键字
          "KEYWORD(POSITION)", // POSITION位置函数
          "KEYWORD(POWER)", // POWER幂函数
          "KEYWORD(PREV)", // PREV前一个关键字
          "KEYWORD(RANK)", // RANK排名函数
          "KEYWORD(REGR_COUNT)", // REGR_COUNT回归计数函数
          "KEYWORD(REGR_SXX)", // REGR_SXX回归平方和函数
          "KEYWORD(REGR_SYY)", // REGR_SYY回归平方和函数
          "KEYWORD(RIGHT)", // RIGHT右子串函数
          "KEYWORD(ROW)", // ROW行关键字
          "KEYWORD(ROW_NUMBER)", // ROW_NUMBER行号函数
          "KEYWORD(RUNNING)", // RUNNING运行关键字
          "KEYWORD(SAFE_CAST)", // SAFE_CAST安全类型转换函数
          "KEYWORD(SECOND)", // SECOND秒函数
          "KEYWORD(SESSION_USER)", // SESSION_USER会话用户函数
          "KEYWORD(SOME)", // SOME存在量词(同ANY)
          "KEYWORD(SPECIFIC)", // SPECIFIC特定关键字
          "KEYWORD(SQRT)", // SQRT平方根函数
          "KEYWORD(SUBSTRING)", // SUBSTRING子串函数
          "KEYWORD(STDDEV_POP)", // STDDEV_POP总体标准差函数
          "KEYWORD(STDDEV_SAMP)", // STDDEV_SAMP样本标准差函数
          "KEYWORD(SUM)", // SUM求和聚合函数
          "KEYWORD(SYSTEM_USER)", // SYSTEM_USER系统用户函数
          "KEYWORD(TIME)", // TIME时间类型
          "KEYWORD(TIMESTAMP)", // TIMESTAMP时间戳类型
          "KEYWORD(TRANSLATE)", // TRANSLATE转换函数
          "KEYWORD(TRIM)", // TRIM修剪函数
          "KEYWORD(TRUE)", // TRUE布尔值
          "KEYWORD(TRUNCATE)", // TRUNCATE截断函数
          "KEYWORD(TRY_CAST)", // TRY_CAST尝试类型转换函数
          "KEYWORD(UNIQUE)", // UNIQUE唯一约束
          "KEYWORD(UNKNOWN)", // UNKNOWN未知值
          "KEYWORD(UPPER)", // UPPER转大写函数
          "KEYWORD(USER)", // USER用户函数
          "KEYWORD(UUID)", // UUID唯一标识符函数
          "KEYWORD(VAR_POP)", // VAR_POP总体方差函数
          "KEYWORD(VAR_SAMP)", // VAR_SAMP样本方差函数
          "KEYWORD(YEAR)"); // YEAR年份函数

  protected static final List<String> QUANTIFIERS =
      Arrays.asList(
          "KEYWORD(ALL)", // ALL量词,表示所有
          "KEYWORD(ANY)", // ANY量词,表示任意
          "KEYWORD(SOME)"); // SOME量词,表示某些(同ANY)

  protected static final List<String> SELECT_KEYWORDS =
      Arrays.asList(
          "KEYWORD(ALL)", // ALL关键字,表示所有行
          "KEYWORD(DISTINCT)", // DISTINCT关键字,表示去重
          "KEYWORD(STREAM)", // STREAM关键字,表示流式查询
          "KEYWORD(*)", // 星号,表示所有列
          "KEYWORD(/*+)"); // 提示注释

  private static final List<String> ORDER_KEYWORDS =
      Arrays.asList(
          "KEYWORD(,)", // 逗号分隔符
          "KEYWORD(ASC)", // ASC升序关键字
          "KEYWORD(DESC)", // DESC降序关键字
          "KEYWORD(NULLS)"); // NULLS空值排序关键字

  private static final List<String> EMP_COLUMNS =
      Arrays.asList(
          "COLUMN(EMPNO)", // 员工编号列
          "COLUMN(ENAME)", // 员工姓名列
          "COLUMN(JOB)", // 职位列
          "COLUMN(MGR)", // 经理编号列
          "COLUMN(HIREDATE)", // 雇佣日期列
          "COLUMN(SAL)", // 薪资列
          "COLUMN(COMM)", // 佣金列
          "COLUMN(DEPTNO)", // 部门编号列
          "COLUMN(SLACKER)"); // 懒散员工列

  private static final List<String> EMP_COLUMNS_E =
      Arrays.asList(
          "COLUMN(EMPNO)", // 员工编号列
          "COLUMN(ENAME)"); // 员工姓名列

  private static final List<String> DEPT_COLUMNS =
      Arrays.asList(
          "COLUMN(DEPTNO)", // 部门编号列
          "COLUMN(NAME)"); // 部门名称列

  protected static final List<String> PREDICATE_KEYWORDS =
      Arrays.asList(
          "KEYWORD(()", // 左括号
          "KEYWORD(*)", // 星号
          "KEYWORD(+)", // 加号
          "KEYWORD(-)", // 减号
          "KEYWORD(.)", // 点号
          "KEYWORD(/)", // 除号
          "KEYWORD(%)", // 取模
          "KEYWORD(<)", // 小于号
          "KEYWORD(<=)", // 小于等于号
          "KEYWORD(<>)", // 不等于号
          "KEYWORD(!=)", // 不等于号
          "KEYWORD(=)", // 等于号
          "KEYWORD(>)", // 大于号
          "KEYWORD(>=)", // 大于等于号
          "KEYWORD(AND)", // AND逻辑与
          "KEYWORD(BETWEEN)", // BETWEEN范围查询
          "KEYWORD(CONTAINS)", // CONTAINS包含
          "KEYWORD(EQUALS)", // EQUALS等于
          "KEYWORD(FORMAT)", // FORMAT格式化
          "KEYWORD(ILIKE)", // ILIKE不区分大小写的模糊匹配
          "KEYWORD(RLIKE)", // RLIKE正则匹配
          "KEYWORD(IMMEDIATELY)", // IMMEDIATELY立即
          "KEYWORD(IN)", // IN包含
          "KEYWORD(IS)", // IS判断
          "KEYWORD(LIKE)", // LIKE模糊匹配
          "KEYWORD(MEMBER)", // MEMBER成员
          "KEYWORD(MULTISET)", // MULTISET多重集
          "KEYWORD(NOT)", // NOT逻辑非
          "KEYWORD(OR)", // OR逻辑或
          "KEYWORD(OVERLAPS)", // OVERLAPS重叠
          "KEYWORD(PRECEDES)", // PRECEDES先于
          "KEYWORD(SIMILAR)", // SIMILAR相似
          "KEYWORD(SUBMULTISET)", // SUBMULTISET子多重集
          "KEYWORD(SUCCEEDS)", // SUCCEEDS后于
          "KEYWORD([)", // 左方括号
          "KEYWORD(||)"); // 字符串连接

  private static final List<String> WHERE_KEYWORDS =
      Arrays.asList(
          "KEYWORD(EXCEPT)", // EXCEPT差集
          "KEYWORD(MINUS)", // MINUS减集
          "KEYWORD(FETCH)", // FETCH获取
          "KEYWORD(OFFSET)", // OFFSET偏移
          "KEYWORD(LIMIT)", // LIMIT限制
          "KEYWORD(GROUP)", // GROUP分组
          "KEYWORD(HAVING)", // HAVING分组过滤
          "KEYWORD(QUALIFY)", // QUALIFY限定
          "KEYWORD(INTERSECT)", // INTERSECT交集
          "KEYWORD(ORDER)", // ORDER排序
          "KEYWORD(UNION)", // UNION并集
          "KEYWORD(WINDOW)"); // WINDOW窗口

  private static final List<String> A_TABLE =
      Collections.singletonList(
          "TABLE(A)"); // 表A

  protected static final List<String> JOIN_KEYWORDS =
      Arrays.asList(
          "KEYWORD(FETCH)", // FETCH获取
          "KEYWORD(FOR)", // FOR循环
          "KEYWORD(OFFSET)", // OFFSET偏移
          "KEYWORD(LIMIT)", // LIMIT限制
          "KEYWORD(UNION)", // UNION并集
          "KEYWORD(FULL)", // FULL全连接
          "KEYWORD(ORDER)", // ORDER排序
          "KEYWORD(()", // 左括号
          "KEYWORD(EXTEND)", // EXTEND扩展
          "KEYWORD(/*+)", // 提示注释
          "KEYWORD(AS)", // AS别名
          "KEYWORD(ASOF)", // ASOF截至
          "KEYWORD(USING)", // USING连接列
          "KEYWORD(OUTER)", // OUTER外连接
          "KEYWORD(RIGHT)", // RIGHT右连接
          "KEYWORD(QUALIFY)", // QUALIFY限定
          "KEYWORD(GROUP)", // GROUP分组
          "KEYWORD(CROSS)", // CROSS交叉连接
          "KEYWORD(,)", // 逗号
          "KEYWORD(NATURAL)", // NATURAL自然连接
          "KEYWORD(INNER)", // INNER内连接
          "KEYWORD(HAVING)", // HAVING分组过滤
          "KEYWORD(LEFT)", // LEFT左连接
          "KEYWORD(EXCEPT)", // EXCEPT差集
          "KEYWORD(MATCH_CONDITION)", // MATCH_CONDITION匹配条件
          "KEYWORD(MATCH_RECOGNIZE)", // MATCH_RECOGNIZE模式识别
          "KEYWORD(MINUS)", // MINUS减集
          "KEYWORD(JOIN)", // JOIN连接
          "KEYWORD(WINDOW)", // WINDOW窗口
          "KEYWORD(.)", // 点号
          "KEYWORD(TABLESAMPLE)", // TABLESAMPLE表采样
          "KEYWORD(ON)", // ON连接条件
          "KEYWORD(INTERSECT)", // INTERSECT交集
          "KEYWORD(WHERE)"); // WHERE条件

  private static final List<String> SETOPS =
      Arrays.asList(
          "KEYWORD(EXCEPT)", // EXCEPT差集
          "KEYWORD(MINUS)", // MINUS减集
          "KEYWORD(INTERSECT)", // INTERSECT交集
          "KEYWORD(ORDER)", // ORDER排序
          "KEYWORD(UNION)"); // UNION并集

  private static final String EMPNO_EMP =
      "COLUMN(EMPNO)\n" // 员工编号列
          + "TABLE(EMP)\n"; // EMP表

  @Override public Fixture fixture() {
    return LOCAL_FIXTURE; // 返回本地测试夹具
  }

  protected List<String> getFromKeywords() {
    return FROM_KEYWORDS; // 返回FROM子句关键字列表
  }

  protected List<String> getSelectKeywords() {
    return SELECT_KEYWORDS; // 返回SELECT子句关键字列表
  }

  /**
   * Returns a list of the tables in the SALES schema. Derived classes with
   * extended SALES schemas may override.
   *
   * @return list of tables in the SALES schema
   */
  protected List<String> getSalesTables() {
    return SALES_TABLES; // 返回SALES模式中的表列表,子类可以重写此方法以扩展SALES模式
  }

  protected List<String> getJoinKeywords() {
    return JOIN_KEYWORDS; // 返回JOIN连接关键字列表
  }

  @Test void testFrom() { // 测试FROM子句的自动补全功能
    final Fixture f = fixture(); // 获取测试夹具

    String sql = "select a.empno, b.deptno from ^dummy a, sales.dummy b";
    f.withSql(sql)
        .assertHint(SCHEMAS, getSalesTables(), getFromKeywords()); // 测试FROM子句中的提示,应该建议SCHEMA、表和FROM关键字

    sql = "select a.empno, b.deptno from ^"; // 测试FROM后的空位置
    f.withSql(sql).assertComplete(SCHEMAS, getSalesTables(), getFromKeywords()); // 应该完整建议SCHEMA、表和FROM关键字
    sql = "select a.empno, b.deptno from ^, sales.dummy b"; // 测试FROM后带逗号
    f.withSql(sql).assertComplete(SCHEMAS, getSalesTables(), getFromKeywords()); // 应该完整建议SCHEMA、表和FROM关键字
    sql = "select a.empno, b.deptno from ^a"; // 测试FROM后带表别名
    f.withSql(sql).assertComplete(SCHEMAS, getSalesTables(), getFromKeywords()); // 应该完整建议SCHEMA、表和FROM关键字

    sql = "select a.empno, b.deptno from dummy a, ^sales.dummy b"; // 测试第二个表位置
    f.withSql(sql)
        .assertHint(SCHEMAS, getSalesTables(), getFromKeywords()); // 应该建议SCHEMA、表和FROM关键字
  }

  @Test void testFromComplete() { // 测试FROM子句的完整补全功能
    String sql = "select a.empno, b.deptno from dummy a, sales.^"; // 测试schema下的表补全
    fixture().withSql(sql).assertComplete(getSalesTables()); // 应该完整建议SALES模式下的所有表
  }

  @Test void testGroup() { // 测试GROUP BY子句的自动补全功能
    // This test is hard because the statement is not valid if you replace
    // '^' with a dummy identifier.
    // 这个测试比较困难,因为如果用虚拟标识符替换'^',语句将无效
    String sql = "select a.empno, b.deptno from emp group ^"; // 测试GROUP后的补全
    fixture().withSql(sql).assertComplete(Arrays.asList("KEYWORD(BY)")); // 应该建议BY关键字
  }

  @Test void testJoin() { // 测试JOIN连接的自动补全功能
    final Fixture f = fixture();
    String sql;

    // from
    // 测试FROM子句中第一个表的补全
    sql =
        "select a.empno, b.deptno from ^dummy a join sales.dummy b "
            + "on a.deptno=b.deptno where empno=1";
    f.withSql(sql).assertHint(getFromKeywords(), SCHEMAS, getSalesTables()); // 应该建议FROM关键字、SCHEMA和表

    // from
    // 测试FROM子句中空位置的补全
    sql = "select a.empno, b.deptno from ^ a join sales.dummy b";
    f.withSql(sql).assertComplete(getFromKeywords(), SCHEMAS, getSalesTables()); // 应该完整建议FROM关键字、SCHEMA和表

    // REVIEW: because caret is before 'sales', should it ignore schema
    // name and present all schemas and all tables in the default schema?
    // 审查:因为光标在'sales'之前,是否应该忽略schema名称并显示所有schema和默认schema中的所有表?
    // join
    // 测试JOIN关键字后的补全
    sql =
        "select a.empno, b.deptno from dummy a join ^sales.dummy b "
            + "on a.deptno=b.deptno where empno=1";
    f.withSql(sql).assertHint(getFromKeywords(), SCHEMAS, getSalesTables()); // 应该建议FROM关键字、SCHEMA和表

    sql = "select a.empno, b.deptno from dummy a join sales.^"; // 测试schema后的表补全
    f.withSql(sql).assertComplete(getSalesTables()); // join 应该完整建议SALES模式下的所有表
    sql = "select a.empno, b.deptno from dummy a join sales.^ on"; // 测试schema后的表补全后跟ON
    f.withSql(sql).assertComplete(getSalesTables()); // join 应该完整建议SALES模式下的所有表

    // unfortunately cannot complete this case: syntax is too broken
    // 遗憾的是无法完成这种情况:语法太破碎
    sql = "select a.empno, b.deptno from dummy a join sales.^ on a.deptno="; // 测试ON条件中的补全
    f.withSql(sql).assertComplete(QUANTIFIERS, EXPR_KEYWORDS); // join 应该建议量词和表达式关键字
  }

  @Test void testJoinKeywords() { // 测试JOIN关键字的自动补全功能
    // variety of keywords possible
    // 可能有多种关键字
    List<String> list = getJoinKeywords(); // 获取JOIN关键字列表
    String sql = "select * from dummy join sales.emp ^"; // 测试JOIN后的补全
    fixture().withSql(sql)
        .assertSimplify("SELECT * FROM dummy JOIN sales.emp _suggest_") // 应该简化为带建议占位符的SQL
        .assertComplete(list); // 应该完整建议所有JOIN关键字
  }

  @Test void testSimplifyStarAlias() { // 测试星号别名的简化功能
    String sql = "select ax^ from (select * from dummy a)"; // 测试子查询中的星号别名补全
    fixture().withSql(sql)
        .assertSimplify("SELECT ax _suggest_ FROM ( SELECT * FROM dummy a )"); // 应该简化为带建议占位符的SQL
  }

  @Test void testSimplifySubQueryStar() { // 测试子查询中星号的简化功能
    final Fixture f = fixture();
    String sql;

    sql = "select ax^ from (select (select * from dummy) axc from dummy a)"; // 测试嵌套子查询中的星号别名补全
    f.withSql(sql)
        .assertSimplify("SELECT ax _suggest_ FROM ("
            + " SELECT ( SELECT * FROM dummy ) axc FROM dummy a )") // 应该简化为带建议占位符的SQL
        .assertComplete("COLUMN(AXC)\n", "ax"); // 应该完整建议AXC列

    sql = "select ax^ from (select a.x+0 axa, b.x axb," // 测试多列子查询中的星号别名补全
        + " (select * from dummy) axbc from dummy a, dummy b)";
    f.withSql(sql)
        .assertSimplify("SELECT ax _suggest_ FROM ( SELECT a.x+0 axa , b.x axb ,"
            + " ( SELECT * FROM dummy ) axbc FROM dummy a , dummy b )") // 应该简化为带建议占位符的SQL
        .assertComplete("COLUMN(AXA)\nCOLUMN(AXB)\nCOLUMN(AXBC)\n", "ax"); // 应该完整建议AXA、AXB、AXBC列

    sql = "select ^ from (select * from dummy)"; // 测试子查询中的空位置补全
    f.withSql(sql)
        .assertSimplify("SELECT _suggest_ FROM ( SELECT * FROM dummy )"); // 应该简化为带建议占位符的SQL

    sql = "select ^ from (select x.* from dummy x)"; // 测试子查询中带表别名的星号补全
    f.withSql(sql)
        .assertSimplify("SELECT _suggest_ FROM ( SELECT x.* FROM dummy x )"); // 应该简化为带建议占位符的SQL

    sql = "select ^ from (select a.x + b.y from dummy a, dummy b)"; // 测试子查询中表达式的补全
    f.withSql(sql)
        .assertSimplify("SELECT _suggest_ FROM ( "
            + "SELECT a.x + b.y FROM dummy a , dummy b )"); // 应该简化为带建议占位符的SQL
  }

  @Test void testSimplifySubQueryMultipleFrom() { // 测试多个FROM子句的子查询简化功能
    final Fixture f = fixture();
    String sql;

    // "dummy b" should be removed
    // "dummy b"应该被移除
    sql = "select axc\n"
        + "from (select (select ^ from dummy) axc from dummy a), dummy b"; // 测试子查询中嵌套子查询的补全
    f.withSql(sql)
        .assertSimplify("SELECT * FROM ("
            + " SELECT ( SELECT _suggest_ FROM dummy ) axc FROM dummy a )"); // 应该简化为带建议占位符的SQL,移除无关的FROM项

    // "dummy b" should be removed
    // "dummy b"应该被移除
    sql = "select axc\n"
        + "from dummy b, (select (select ^ from dummy) axc from dummy a)"; // 测试FROM项顺序颠倒的情况
    f.withSql(sql)
        .assertSimplify("SELECT * FROM ("
            + " SELECT ( SELECT _suggest_ FROM dummy ) axc FROM dummy a )"); // 应该简化为带建议占位符的SQL,移除无关的FROM项
  }

  @Test void testSimplifyMinus() { // 测试MINUS集合操作的简化功能
    final Fixture f = fixture();
    String sql;

    sql = "select ^ from dummy a minus select * from dummy b"; // 测试MINUS前一个SELECT的补全
    f.withSql(sql).assertSimplify("SELECT _suggest_ FROM dummy a"); // 应该简化为带建议占位符的SQL,只保留第一个查询

    sql = "select * from dummy a minus select ^ from dummy b"; // 测试MINUS后一个SELECT的补全
    f.withSql(sql).assertSimplify("SELECT _suggest_ FROM dummy b"); // 应该简化为带建议占位符的SQL,只保留第二个查询
  }

  @Test void testOnCondition() { // 测试ON连接条件的自动补全功能
    final Fixture f = fixture();
    String sql;

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b "
            + "on ^a.deptno=b.dummy where empno=1"; // 测试ON条件左侧的补全
    f.withSql(sql).assertHint(AB_TABLES, EXPR_KEYWORDS); // on left 应该建议表A、表B和表达式关键字

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b "
            + "on a.^"; // 测试ON条件左侧表别名的补全
    f.withSql(sql).assertComplete(EMP_COLUMNS); // on left 应该完整建议EMP表的所有列

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b "
            + "on a.deptno=^b.dummy where empno=1"; // 测试ON条件右侧的补全
    f.withSql(sql).assertHint(EXPR_KEYWORDS, QUANTIFIERS, AB_TABLES); // on right 应该建议表达式关键字、量词和表A、表B

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b "
            + "on a.deptno=b.^ where empno=1"; // 测试ON条件右侧表别名的补全
    f.withSql(sql).assertComplete(DEPT_COLUMNS); // on right 应该完整建议DEPT表的所有列

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b "
            + "on a.deptno=b.^"; // 测试ON条件右侧表别名的补全(不带WHERE)
    f.withSql(sql).assertComplete(DEPT_COLUMNS); // on right 应该完整建议DEPT表的所有列
  }

  @Test void testFromWhere() { // 测试FROM和WHERE子句结合的自动补全功能
    final Fixture f = fixture();
    String sql;

    sql = "select a.empno, b.deptno from sales.emp a, sales.dept b " // 测试WHERE条件中的补全
        + "where b.deptno=^a.dummy";
    f.withSql(sql)
        .assertHint(AB_TABLES, EXPR_KEYWORDS, QUANTIFIERS); // where list 应该建议表A、表B、表达式关键字和量词

    sql = "select a.empno, b.deptno from sales.emp a, sales.dept b\n" // 测试WHERE条件中表别名的补全
        + "where b.deptno=a.^";
    f.withSql(sql)
        .assertComplete(ImmutableMap.of("COLUMN(COMM)", "COMM"),
            EMP_COLUMNS); // where list 应该完整建议EMP表的所有列,并验证替换

    sql =
        "select a.empno, b.deptno from sales.emp a, sales.dept b " // 测试WHERE条件中部分列名的补全
            + "where b.deptno=a.e^";
    f.withSql(sql)
        .assertComplete(ImmutableMap.of("COLUMN(ENAME)", "ename"),
            EMP_COLUMNS_E); // where list 应该完整建议以E开头的EMP表列,并验证替换

    // hints contain no columns, only table aliases, because there are >1
    // aliases
    // 提示不包含列,只包含表别名,因为有多个别名
    sql =
        "select a.empno, b.deptno from sales.emp a, sales.dept b "
            + "where ^dummy=1"; // 测试WHERE条件开头的补全
    f.withSql(sql)
        .assertComplete(
            ImmutableMap.of("KEYWORD(CURRENT_TIMESTAMP)", "CURRENT_TIMESTAMP"),
            AB_TABLES, EXPR_KEYWORDS); // where list 应该完整建议表A、表B和表达式关键字,并验证替换

    sql =
        "select a.empno, b.deptno from sales.emp a, sales.dept b "
            + "where ^"; // 测试WHERE条件空位置的补全
    f.withSql(sql)
        .assertComplete(AB_TABLES, EXPR_KEYWORDS); // where list 应该完整建议表A、表B和表达式关键字

    // If there's only one table alias, we allow both the alias and the
    // unqualified columns
    // 如果只有一个表别名,我们允许别名和非限定列
    sql = "select a.empno, a.deptno from sales.emp a " // 测试单表WHERE条件的补全
        + "where ^";
    f.withSql(sql)
        .assertComplete(A_TABLE, EMP_COLUMNS, EXPR_KEYWORDS); // 应该完整建议表A、EMP表的所有列和表达式关键字
  }

  @Test void testWhereList() { // 测试WHERE列表的自动补全功能
    final Fixture f = fixture();
    String sql;

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b " // 测试JOIN后WHERE条件中的补全
            + "on a.deptno=b.deptno where ^dummy=1";
    f.withSql(sql).assertHint(EXPR_KEYWORDS, AB_TABLES); // where list 应该建议表达式关键字、表A和表B

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b " // 测试JOIN后WHERE条件空位置的补全
            + "on a.deptno=b.deptno where ^";
    f.withSql(sql).assertComplete(EXPR_KEYWORDS, AB_TABLES); // where list 应该完整建议表达式关键字、表A和表B

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b " // 测试JOIN后WHERE条件中带表别名的补全
            + "on a.deptno=b.deptno where ^a.dummy=1";
    f.withSql(sql).assertHint(EXPR_KEYWORDS, AB_TABLES); // where list 应该建议表达式关键字、表A和表B

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b " // 测试JOIN后WHERE条件中表别名后的补全
            + "on a.deptno=b.deptno where a.^";
    f.withSql(sql).assertComplete(EMP_COLUMNS); // 应该完整建议EMP表的所有列

    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b " // 测试WHERE条件中列名后的补全
            + "on a.deptno=b.deptno where a.empno ^ ";
    f.withSql(sql).assertComplete(PREDICATE_KEYWORDS, WHERE_KEYWORDS); // 应该完整建议谓词关键字和WHERE关键字
  }

  @Test void testSelectList() { // 测试SELECT列表的自动补全功能
    final Fixture f = fixture();
    String sql;

    sql =
        "select ^dummy, b.dummy from sales.emp a join sales.dept b " // 测试SELECT列表中的补全
            + "on a.deptno=b.deptno where empno=1";
    f.withSql(sql).assertHint(getSelectKeywords(), EXPR_KEYWORDS, AB_TABLES); // 应该建议SELECT关键字、表达式关键字、表A和表B

    sql = "select ^ from (values (1))"; // 测试VALUES子查询中的补全
    f.withSql(sql)
        .assertComplete(getSelectKeywords(), EXPR_KEYWORDS,
            Arrays.asList("TABLE(EXPR$0)", "COLUMN(EXPR$0)")); // 应该完整建议SELECT关键字、表达式关键字、表EXPR$0和列EXPR$0

    sql = "select ^ from (values (1)) as t(c)"; // 测试带别名的VALUES子查询中的补全
    f.withSql(sql)
        .assertComplete(getSelectKeywords(), EXPR_KEYWORDS,
            Arrays.asList("TABLE(T)", "COLUMN(C)")); // 应该完整建议SELECT关键字、表达式关键字、表T和列C

    sql = "select ^, b.dummy from sales.emp a join sales.dept b "; // 测试SELECT列表中带逗号的补全
    f.withSql(sql)
        .assertComplete(getSelectKeywords(), EXPR_KEYWORDS, AB_TABLES); // 应该完整建议SELECT关键字、表达式关键字、表A和表B

    sql =
        "select dummy, ^b.dummy from sales.emp a join sales.dept b " // 测试SELECT列表中第二列的补全
            + "on a.deptno=b.deptno where empno=1";
    f.withSql(sql).assertHint(EXPR_KEYWORDS, STAR_KEYWORD, AB_TABLES); // 应该建议表达式关键字、星号关键字、表A和表B

    sql = "select dummy, b.^ from sales.emp a join sales.dept b on true"; // 测试SELECT列表中表别名后的补全
    f.withSql(sql).assertComplete(STAR_KEYWORD, DEPT_COLUMNS); // 应该完整建议星号关键字和DEPT表的所有列

    // REVIEW: Since 'b' is not a valid alias, should it suggest anything?
    // We don't get through validation, so the only suggestion, '*', comes
    // from the parser.
    // 审查:由于'b'不是有效的别名,它应该建议任何内容吗?
    // 我们没有通过验证,所以唯一的建议'*'来自解析器。
    sql = "select dummy, b.^ from sales.emp a"; // 测试无效表别名的补全
    f.withSql(sql).assertComplete(STAR_KEYWORD); // 应该完整建议星号关键字

    sql = "select ^emp.dummy from sales.emp"; // 测试SELECT列表中表名后的补全
    f.withSql(sql)
        .assertHint(getSelectKeywords(),
            EXPR_KEYWORDS,
            EMP_COLUMNS,
            Arrays.asList("TABLE(EMP)")); // 应该建议SELECT关键字、表达式关键字、EMP表的所有列和表EMP

    // Suggest columns for a table name or table alias in the SELECT clause.
    // 为SELECT子句中的表名或表别名建议列。
    final Consumer<String> c = sql_ ->
        f.withSql(sql_).assertComplete(EMP_COLUMNS, STAR_KEYWORD); // 验证每个SQL都应该完整建议EMP表的所有列和星号关键字
    c.accept("select emp.^ from sales.emp"); // 测试表名作为别名
    c.accept("select emp.^ from sales.emp as emp"); // 测试AS别名
    c.accept("select emp.^ from sales.emp emp"); // 测试空格别名
    c.accept("select e.^ from sales.emp as e"); // 测试短别名
    c.accept("select e.^ from sales.emp e"); // 测试短别名不带AS
    c.accept("select e.^ from sales.emp e, sales.dept d"); // 测试多表场景
    c.accept("select e.^ from sales.emp e cross join sales.dept d"); // 测试CROSS JOIN场景
    c.accept("select e.^ from sales.emp e where deptno = 20"); // 测试WHERE条件场景
    c.accept("select e.^ from sales.emp e order by deptno"); // 测试ORDER BY场景
  }

  @Test void testOrderByList() { // 测试ORDER BY列表的自动补全功能
    final Fixture f = fixture();
    String sql;

    sql = "select emp.empno from sales.emp where empno=1 order by ^dummy"; // 测试ORDER BY中的补全
    f.withSql(sql).assertHint(EXPR_KEYWORDS, EMP_COLUMNS, EMP_TABLE); // 应该建议表达式关键字、EMP表的所有列和表EMP

    sql = "select emp.empno from sales.emp where empno=1 order by ^"; // 测试ORDER BY空位置的补全
    f.withSql(sql).assertComplete(EXPR_KEYWORDS, EMP_COLUMNS, EMP_TABLE); // 应该完整建议表达式关键字、EMP表的所有列和表EMP

    sql =
        "select emp.empno\n" // 测试带列别名的ORDER BY补全
            + "from sales.emp as e(\n"
            + "  mpno,name,ob,gr,iredate,al,omm,eptno,lacker)\n"
            + "where e.mpno=1 order by ^";
    f.withSql(sql)
        .assertComplete(EXPR_KEYWORDS,
            Arrays.asList("COLUMN(MPNO)", // 应该建议所有别名列
                "COLUMN(NAME)",
                "COLUMN(OB)",
                "COLUMN(GR)",
                "COLUMN(IREDATE)",
                "COLUMN(AL)",
                "COLUMN(OMM)",
                "COLUMN(EPTNO)",
                "COLUMN(LACKER)"),
            Arrays.asList("TABLE(E)")); // 和表E

    sql =
        "select emp.empno from sales.emp where empno=1 order by empno ^, deptno"; // 测试ORDER BY列名后的补全
    f.withSql(sql)
        .assertComplete(PREDICATE_KEYWORDS, ORDER_KEYWORDS, FETCH_OFFSET); // 应该完整建议谓词关键字、ORDER BY关键字和FETCH/OFFSET关键字
  }

  @Test void testSubQuery() { // 测试子查询的自动补全功能
    final Fixture f = fixture();
    String sql;
    final List<String> xyColumns =
        Arrays.asList(
            "COLUMN(X)", // X列
            "COLUMN(Y)"); // Y列
    final List<String> tTable =
        Arrays.asList(
            "TABLE(T)"); // 表T

    sql = "select ^t.dummy from (\n" // 测试子查询FROM中的补全
        + "  select 1 as x, 2 as y from sales.emp) as t\n"
        + "where t.dummy=1";
    f.withSql(sql)
        .assertHint(EXPR_KEYWORDS, getSelectKeywords(), xyColumns, tTable); // 应该建议表达式关键字、SELECT关键字、X/Y列和表T

    sql = "select t.^ from (select 1 as x, 2 as y from sales.emp) as t"; // 测试子查询表别名后的补全
    f.withSql(sql).assertComplete(xyColumns, STAR_KEYWORD); // 应该完整建议X/Y列和星号关键字

    sql = "select t.x from (select 1 as x, 2 as y from sales.emp) as t " // 测试子查询WHERE中的补全
        + "where ^t.dummy=1";
    f.withSql(sql).assertHint(EXPR_KEYWORDS, tTable, xyColumns); // 应该建议表达式关键字、表T和X/Y列

    sql = "select t.x\n" // 测试子查询WHERE中表别名后的补全
        + "from (select 1 as x, 2 as y from sales.emp) as t\n"
        + "where t.^";
    f.withSql(sql).assertComplete(xyColumns); // 应该完整建议X/Y列

    sql = "select t.x from (select 1 as x, 2 as y from sales.emp) as t where ^"; // 测试子查询WHERE空位置的补全
    f.withSql(sql).assertComplete(EXPR_KEYWORDS, tTable, xyColumns); // 应该完整建议表达式关键字、表T和X/Y列

    // with extra from item, aliases are ambiguous, so columns are not
    // offered
    // 有额外的FROM项时,别名有歧义,所以不提供列
    sql = "select a.x\n"
        + "from (select 1 as x, 2 as y from sales.emp) as a,\n"
        + "  dept as b\n"
        + "where ^"; // 测试多FROM项的补全
    f.withSql(sql).assertComplete(EXPR_KEYWORDS, AB_TABLES); // 应该完整建议表达式关键字、表A和表B(不提供列)

    // note that we get hints even though there's a syntax error in
    // select clause ('t.')
    // 注意,即使SELECT子句中有语法错误('t.'),我们也能得到提示
    sql = "select t.\n"
        + "from (select 1 as x, 2 as y from (select x from sales.emp)) as t\n"
        + "where ^"; // 测试嵌套子查询的补全
    String simplified = "SELECT * "
        + "FROM ( SELECT 1 as x , 2 as y FROM ( SELECT x FROM sales.emp ) ) as t "
        + "WHERE _suggest_";
    f.withSql(sql)
        .assertSimplify(simplified) // 应该简化为带建议占位符的SQL
        .assertComplete(EXPR_KEYWORDS, tTable, xyColumns); // 应该完整建议表达式关键字、表T和X/Y列

    sql = "select t.x from (select 1 as x, 2 as y from sales.^) as t"; // 测试子查询中schema的补全
    f.withSql(sql).assertComplete(getSalesTables()); // 应该完整建议SALES模式下的所有表

    // CALCITE-3474:SqlSimpleParser toke.s equals NullPointerException
    // CALCITE-3474:SqlSimpleParser token.s等于NullPointerException
    sql = "select ^ from (select * from sales.emp) as t"; // 测试子查询SELECT的补全
    f.withSql(sql)
        .assertComplete(getSelectKeywords(), tTable, EMP_COLUMNS,
            EXPR_KEYWORDS); // 应该完整建议SELECT关键字、表T、EMP表的所有列和表达式关键字
  }

  @Test void testSubQueryInWhere() { // 测试WHERE子句中子查询的自动补全功能

      // Aliases from enclosing sub-queries are inherited: hence A from

      // enclosing, B from same scope.

      // 来自封闭子查询的别名被继承:因此A来自封闭范围,B来自相同范围。

      // The raw columns from dept are suggested (because they can

      // be used unqualified in the inner scope) but the raw

      // columns from emp are not (because they would need to be qualified

      // with A).

      // 建议dept的原始列(因为它们可以在内部范围中无限制使用),但不建议emp的原始列(因为它们需要用A限定)。

      String sql = "select * from sales.emp a where deptno in (" // 测试WHERE子句中子查询的补全

          + "select * from sales.dept b where ^)";

      String simplifiedSql = "SELECT * FROM sales.emp a WHERE deptno in ("

          + " SELECT * FROM sales.dept b WHERE _suggest_ )";

      fixture().withSql(sql)

          .assertSimplify(simplifiedSql) // 应该简化为带建议占位符的SQL

          .assertComplete(

              AB_TABLES, // 应该完整建议表A和表B

              DEPT_COLUMNS, // DEPT表的所有列

              EXPR_KEYWORDS); // 和表达式关键字

    }

  

    @Test void testSimpleParserTokenizer() { // 测试简单解析器的分词功能

      String sql =

          "select" // 构建一个复杂的SQL语句,包含各种元素

              + " 12"

              + " "

              + "*"

              + " 1.23e45"

              + " "

              + "("

              + "\"an id\""

              + ","

              + " "

              + "\"an id with \"\"quotes' inside\""

              + ","

              + " "

              + "/* a comment, with 'quotes', over\nmultiple lines\nand select keyword */"

              + "\n "

              + "("

              + " "

              + "a"

              + " "

              + "different"

              + " "

              + "// comment\n\r"

              + "//and a comment /* containing comment */ and then some more\r"

              + ")"

              + " "

              + "from"

              + " "

              + "t"

              + ")"

              + ")"

              + "/* a comment after close paren */"

              + " "

              + "("

              + "'quoted'"

              + " "

              + "'string with ''single and \"double\"\" quote'"

              + ")";

      String expected =

          "SELECT\n" // 期望的标记化结果

              + "ID(12)\n"

              + "ID(*)\n"

              + "ID(1.23e45)\n"

              + "LPAREN\n"

              + "DQID(\"an id\")\n"

              + "COMMA\n"

              + "DQID(\"an id with \"\"quotes' inside\")\n"

              + "COMMA\n"

              + "COMMENT\n"

  

              + "LPAREN\n"

              + "ID(a)\n"

              + "ID(different)\n"

              + "COMMENT\n"

              + "COMMENT\n"

              + "RPAREN\n"

              + "FROM\n"

              + "ID(t)\n"

              + "RPAREN\n"

              + "RPAREN\n"

              + "COMMENT\n"

              + "LPAREN\n"

              + "SQID('quoted')\n"

              + "SQID('string with ''single and \"double\"\" quote')\n"

              + "RPAREN\n";

      final Fixture f = fixture();

      f.withSql(sql).assertTokenizesTo(expected); // 验证标记化结果

  

      // Tokenizer should be lenient if input ends mid-token

      // 如果输入在标记中间结束,分词器应该宽容

      f.withSql("select /* unfinished comment") // 测试未完成的块注释

          .assertTokenizesTo("SELECT\nCOMMENT\n");

      f.withSql("select // unfinished comment") // 测试未完成的行注释

          .assertTokenizesTo("SELECT\nCOMMENT\n");

      f.withSql("'starts with string'") // 测试字符串

          .assertTokenizesTo("SQID('starts with string')\n");

      f.withSql("'unfinished string") // 测试未完成的字符串

          .assertTokenizesTo("SQID('unfinished string)\n");

      f.withSql("\"unfinished double-quoted id") // 测试未完成的双引号标识符

          .assertTokenizesTo("DQID(\"unfinished double-quoted id)\n");

      f.withSql("123") // 测试数字

          .assertTokenizesTo("ID(123)\n");

    }

  @Test void testSimpleParser() { // 测试简单解析器的简化功能
    final Fixture f = fixture();
    String sql;
    String expected;

    // from
    sql = "select * from ^where"; // 测试FROM后的补全
    expected = "SELECT * FROM _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // from
    sql = "select a.empno, b.deptno from ^"; // 测试FROM空位置的补全
    expected = "SELECT * FROM _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // select list
    sql = "select ^ from (values (1))"; // 测试SELECT列表的补全
    expected = "SELECT _suggest_ FROM ( values ( 1 ) )";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql = "select emp.^ from sales.emp"; // 测试表别名后的补全
    expected = "SELECT emp. _suggest_ FROM sales.emp";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql = "select ^from sales.emp"; // 测试SELECT后无空格的补全
    expected = "SELECT _suggest_ FROM sales.emp";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // remove other expressions in select clause
    // 移除SELECT子句中的其他表达式
    sql = "select a.empno ,^  from sales.emp a , sales.dept b"; // 测试SELECT列表中的补全
    expected = "SELECT _suggest_ FROM sales.emp a , sales.dept b";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql = "select ^, a.empno from sales.emp a , sales.dept b"; // 测试SELECT列表开头的补全
    expected = "SELECT _suggest_ FROM sales.emp a , sales.dept b";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql = "select dummy, b.^ from sales.emp a , sales.dept b"; // 测试SELECT列表中表别名后的补全
    expected = "SELECT b. _suggest_ FROM sales.emp a , sales.dept b";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // join
    sql = "select a.empno, b.deptno from dummy a join ^on where empno=1"; // 测试JOIN后的补全
    expected = "SELECT * FROM dummy a JOIN _suggest_ ON TRUE";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // join
    sql =
        "select a.empno, b.deptno from dummy a join sales.^ where empno=1"; // 测试JOIN中schema的补全
    expected = "SELECT * FROM dummy a JOIN sales. _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // on
    sql =
        "select a.empno, b.deptno from sales.emp a join sales.dept b " // 测试ON条件中的补全
            + "on a.deptno=^";
    expected =
        "SELECT * FROM sales.emp a JOIN sales.dept b "
            + "ON a.deptno= _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // where
    sql =
        "select a.empno, b.deptno from sales.emp a, sales.dept b " // 测试WHERE中的补全
            + "where ^";
    expected = "SELECT * FROM sales.emp a , sales.dept b WHERE _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // order by
    sql = "select emp.empno from sales.emp where empno=1 order by ^"; // 测试ORDER BY中的补全
    expected = "SELECT emp.empno FROM sales.emp ORDER BY _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // sub-query in from
    // FROM中的子查询
    sql =
        "select t.^ from (select 1 as x, 2 as y from sales.emp) as t " // 测试子查询表别名后的补全
            + "where t.dummy=1";
    expected =
        "SELECT t. _suggest_ "
            + "FROM ( SELECT 1 as x , 2 as y FROM sales.emp ) as t";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql =
        "select t. from (select 1 as x, 2 as y from " // 测试嵌套子查询的补全
            + "(select x from sales.emp)) as t where ^";
    expected =
        "SELECT * FROM ( SELECT 1 as x , 2 as y FROM "
            + "( SELECT x FROM sales.emp ) ) as t WHERE _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql =
        "select ^from (select 1 as x, 2 as y from sales.emp), " // 测试多子查询的补全
            + "(select 2 as y from (select m from n where)) as t "
            + "where t.dummy=1";
    expected =
        "SELECT _suggest_ FROM ( SELECT 1 as x , 2 as y FROM sales.emp ) "
            + ", ( SELECT 2 as y FROM ( SELECT m FROM n ) ) as t";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // Note: completes the missing close paren; wipes out select clause of
    // both outer and inner queries since not relevant.
    // 注意:补全缺失的右括号;由于不相关,擦除外部和内部查询的SELECT子句。
    sql = "select t.x from ( select 1 as x, 2 as y from sales.^"; // 测试子查询中schema的补全(缺少右括号)
    expected = "SELECT * FROM ( SELECT * FROM sales. _suggest_ )";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql = "select t.^ from (select 1 as x, 2 as y from sales)"; // 测试子查询表别名后的补全
    expected =
        "SELECT t. _suggest_ FROM ( SELECT 1 as x , 2 as y FROM sales )";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // sub-query in where; note that:
    // WHERE中的子查询;注意:
    // 1. removes the SELECT clause of sub-query in WHERE clause;
    // 1. 移除WHERE子句中子查询的SELECT子句;
    // 2. keeps SELECT clause of sub-query in FROM clause;
    // 2. 保留FROM子句中子查询的SELECT子句;
    // 3. removes GROUP BY clause of sub-query in FROM clause;
    // 3. 移除FROM子句中子查询的GROUP BY子句;
    // 4. removes SELECT clause of outer query.
    // 4. 移除外部查询的SELECT子句。
    sql =
        "select x + y + 32 from " // 测试WHERE中子查询的补全
            + "(select 1 as x, 2 as y from sales group by invalid stuff) as t "
            + "where x in (select deptno from emp where foo + t.^ < 10)";
    expected =
        "SELECT * FROM ( SELECT 1 as x , 2 as y FROM sales ) as t "
            + "WHERE x in ( SELECT * FROM emp WHERE foo + t. _suggest_ < 10 )";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // if hint is in FROM, can remove other members of FROM clause
    // 如果提示在FROM中,可以移除FROM子句的其他成员
    sql = "select a.empno, b.deptno from dummy a, sales.^"; // 测试FROM中schema的补全
    expected = "SELECT * FROM sales. _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // function
    // 函数
    sql = "select count(1) from sales.emp a where ^"; // 测试WHERE中的补全(带聚合函数)
    expected = "SELECT * FROM sales.emp a WHERE _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    sql =
        "select count(1) from sales.emp a " // 测试函数参数中的补全
            + "where substring(a.^ FROM 3 for 6) = '1234'";
    expected =
        "SELECT * FROM sales.emp a "
            + "WHERE substring ( a. _suggest_ FROM 3 for 6 ) = '1234'";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // missing ')' following sub-query
    // 子查询后缺少')'
    sql =
        "select * from sales.emp a where deptno in (" // 测试WHERE中子查询的补全(缺少右括号)
            + "select * from sales.dept b where ^";
    expected =
        "SELECT * FROM sales.emp a WHERE deptno in ("
            + " SELECT * FROM sales.dept b WHERE _suggest_ )";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // keyword embedded in single and double quoted string should be
    // ignored
    // 嵌入在单引号和双引号字符串中的关键字应该被忽略
    sql =
        "select 'a cat from a king' as foobar, 1 / 2 \"where\" from t " // 测试GROUP BY中的补全(忽略字符串中的关键字)
            + "group by t.^ order by 123";
    expected = "SELECT * FROM t GROUP BY t. _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // skip comments
    // 跳过注释
    sql = "select /* here is from */ 'cat' as foobar, 1 as x\n" // 测试GROUP BY中的补全(跳过块注释)
        + "from t group by t.^ order by 123";
    expected = "SELECT * FROM t GROUP BY t. _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // skip comments
    sql = "select // here is from clause\n" // 测试GROUP BY中的补全(跳过行注释)
        + " 'cat' as foobar, 1 as x from t group by t.^ order by 123";
    expected = "SELECT * FROM t GROUP BY t. _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // skip comments
    sql = "select -- here is from clause\n" // 测试GROUP BY中的补全(跳过行注释)
        + " 'cat' as foobar, 1 as x from t group by t.^ order by 123";
    expected = "SELECT * FROM t GROUP BY t. _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // skip comments
    sql = "-- test test\n" // 测试GROUP BY中的补全(跳过行注释)
        + "select -- here is from\n"
        + "'cat' as foobar, 1 as x from t group by t.^ order by 123";
    expected = "SELECT * FROM t GROUP BY t. _suggest_";
    f.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL
  }

  @Test void testSimpleParserQuotedIdSqlServer() { // 测试SQL Server方言的引号标识符解析
    checkSimpleParserQuotedIdImpl(fixture().withLex(Lex.SQL_SERVER)); // 使用SQL Server词法规则测试
  }

  @Test void testSimpleParserQuotedIdMySql() { // 测试MySQL方言的引号标识符解析
    checkSimpleParserQuotedIdImpl(fixture().withLex(Lex.MYSQL)); // 使用MySQL词法规则测试
  }

  @Test void testSimpleParserQuotedIdJava() { // 测试Java方言的引号标识符解析
    checkSimpleParserQuotedIdImpl(fixture().withLex(Lex.JAVA)); // 使用Java词法规则测试
  }

  @Test void testSimpleParserQuotedIdDefault() { // 测试默认方言的引号标识符解析
    checkSimpleParserQuotedIdImpl(fixture()); // 使用默认词法规则测试
  }

  private String replaceQuotes(SqlParser.Config parserConfig, String sql) {
    char openQuote = parserConfig.quoting().string.charAt(0); // 获取开引号字符
    char closeQuote = openQuote == '[' ? ']' : openQuote; // 获取闭引号字符,如果是'['则对应']'
    return sql.replace('[', openQuote).replace(']', closeQuote); // 替换SQL中的引号字符
  }

  private void checkSimpleParserQuotedIdImpl(Fixture fixture) { // 测试引号标识符解析的实现
    SqlParser.Config parserConfig = fixture.parserConfig(); // 获取解析器配置
    String sql;
    String expected;

    // unclosed double-quote
    // 未闭合的双引号
    sql = replaceQuotes(parserConfig, "select * from t where [^"); // 测试未闭合引号的补全
    expected = replaceQuotes(parserConfig, "SELECT * FROM t WHERE _suggest_");
    fixture.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // closed double-quote
    // 闭合的双引号
    sql = replaceQuotes(parserConfig, "select * from t where [^] and x = y"); // 测试闭合引号的补全
    expected =
        replaceQuotes(parserConfig, "SELECT * FROM t WHERE _suggest_ and x = y");
    fixture.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // closed double-quote containing extra stuff
    // 包含额外内容的闭合双引号
    sql = replaceQuotes(parserConfig, "select * from t where [^foo] and x = y"); // 测试闭合引号内含内容的补全
    expected =
        replaceQuotes(parserConfig, "SELECT * FROM t WHERE _suggest_ and x = y");
    fixture.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL

    // escaped double-quote containing extra stuff
    // 包含额外内容的转义双引号
    sql =
        replaceQuotes(parserConfig, "select * from t where [^f]]oo] and x = y"); // 测试转义引号的补全
    expected =
        replaceQuotes(parserConfig, "SELECT * FROM t WHERE _suggest_ and x = y");
    fixture.withSql(sql).assertSimplify(expected); // 应该简化为带建议占位符的SQL
  }

  @Test void testPartialIdentifier() { // 测试部分标识符的自动补全功能
    final Fixture f = fixture();
    String sql = "select * from emp where e^ and emp.deptno = 10"; // 测试WHERE中部分标识符的补全
    String expected =
        "COLUMN(EMPNO)\n"
            + "COLUMN(ENAME)\n"
            + "KEYWORD(ELEMENT)\n"
            + "KEYWORD(EVERY)\n"
            + "KEYWORD(EXISTS)\n"
            + "KEYWORD(EXP)\n"
            + "KEYWORD(EXTRACT)\n"
            + "TABLE(EMP)\n";
    f.withSql(sql)
        .assertComplete(expected, "e", // 应该完整建议EMPNO、ENAME列和相关关键字,并验证替换
            ImmutableMap.of("KEYWORD(EXISTS)", "exists",
                "TABLE(EMP)", "emp"));

    sql = "select * from emp where \"e^ and emp.deptno = 10"; // 测试双引号中部分标识符的补全
    expected =
        "COLUMN(EMPNO)\n"
            + "COLUMN(ENAME)\n"
            + "KEYWORD(ELEMENT)\n"
            + "KEYWORD(EVERY)\n"
            + "KEYWORD(EXISTS)\n"
            + "KEYWORD(EXP)\n"
            + "KEYWORD(EXTRACT)\n"
            + "TABLE(EMP)\n";
    f.withSql(sql)
        .assertComplete(expected, "\"e", // 应该完整建议EMPNO、ENAME列和相关关键字,并验证替换
            ImmutableMap.of("KEYWORD(EXISTS)", "exists",
                "TABLE(EMP)", "\"EMP\""));

    sql = "select * from emp where E^ and emp.deptno = 10"; // 测试大写部分标识符的补全
    expected =
        "COLUMN(EMPNO)\n"
            + "COLUMN(ENAME)\n"
            + "KEYWORD(ELEMENT)\n"
            + "KEYWORD(EVERY)\n"
            + "KEYWORD(EXISTS)\n"
            + "KEYWORD(EXP)\n"
            + "KEYWORD(EXTRACT)\n"
            + "TABLE(EMP)\n";
    f.withSql(sql)
        .assertComplete(expected, "E", // 应该完整建议EMPNO、ENAME列和相关关键字,并验证替换
            ImmutableMap.of("KEYWORD(EXISTS)", "EXISTS",
                "TABLE(EMP)", "EMP"));

    // cursor in middle of word and at end
    // 光标在单词中间和末尾
    sql = "select * from emp where e^"; // 测试部分标识符在末尾的补全
    f.withSql(sql)
        .assertComplete(expected, null); // 应该完整建议EMPNO、ENAME列和相关关键字

    // longer completion
    // 更长的补全
    sql = "select * from emp where em^"; // 测试更长的部分标识符的补全
    f.withSql(sql)
        .assertComplete(EMPNO_EMP, null, // 应该完整建议EMPNO列和EMP表,并验证替换
            ImmutableMap.of("COLUMN(EMPNO)", "empno"));

    // word after punctuation
    // 标点符号后的单词
    sql = "select deptno,em^ from emp where 1+2<3+4"; // 测试逗号后部分标识符的补全
    f.withSql(sql)
        .assertComplete(EMPNO_EMP, null, // 应该完整建议EMPNO列和EMP表,并验证替换
            ImmutableMap.of("COLUMN(EMPNO)", "empno"));

    // inside double-quotes, no terminating double-quote.
    // Only identifiers should be suggested (no keywords),
    // and suggestion should include double-quotes
    // 在双引号内,没有终止双引号。
    // 只应该建议标识符(没有关键字),
    // 并且建议应该包含双引号
    sql = "select deptno,\"EM^ from emp where 1+2<3+4"; // 测试双引号内部分标识符的补全
    f.withSql(sql)
        .assertComplete(EMPNO_EMP, "\"EM", // 应该完整建议EMPNO列和EMP表,并验证替换
            ImmutableMap.of("COLUMN(EMPNO)", "\"EMPNO\""));

    // inside double-quotes, match is case-insensitive as well
    // 在双引号内,匹配也是不区分大小写的
    sql = "select deptno,\"em^ from emp where 1+2<3+4"; // 测试双引号内小写部分标识符的补全
    f.withSql(sql)
        .assertComplete(EMPNO_EMP, "\"em", // 应该完整建议EMPNO列和EMP表,并验证替换
            ImmutableMap.of("COLUMN(EMPNO)", "\"EMPNO\""));

    // when input strings has mixed casing, match should be case-sensitive
    // 当输入字符串有混合大小写时,匹配应该区分大小写
    sql = "select deptno,eM^ from emp where 1+2<3+4"; // 测试混合大小写部分标识符的补全
    f.withSql(sql).assertComplete("", "eM"); // 应该返回空结果

    // when input strings has mixed casing, match should be case-sensitive
    // 当输入字符串有混合大小写时,匹配应该区分大小写
    sql = "select deptno,\"eM^ from emp where 1+2<3+4"; // 测试双引号内混合大小写部分标识符的补全
    f.withSql(sql).assertComplete("", "\"eM"); // 应该返回空结果

    // eat up following double-quote
    // 消耗后面的双引号
    sql = "select deptno,\"EM^ps\" from emp where 1+2<3+4"; // 测试双引号内部分标识符后跟内容的补全
    f.withSql(sql)
        .assertComplete(EMPNO_EMP, "\"EM", // 应该完整建议EMPNO列和EMP表,并验证替换
            ImmutableMap.of("COLUMN(EMPNO)", "\"EMPNO\""));

    // closing double-quote is at very end of string
    // 闭合双引号在字符串的最末尾
    sql = "select * from emp where 5 = \"EM^xxx\""; // 测试双引号内部分标识符在末尾的补全
    f.withSql(sql)
        .assertComplete(EMPNO_EMP, "\"EM", // 应该完整建议EMPNO列和EMP表,并验证替换
            ImmutableMap.of("COLUMN(EMPNO)", "\"EMPNO\""));

    // just before dot
    // 就在点之前
    sql = "select emp.^name from emp"; // 测试点之前的补全
    f.withSql(sql).assertComplete(EMP_COLUMNS, STAR_KEYWORD); // 应该完整建议EMP表的所有列和星号关键字
  }

  @Test void testAdviceKeywordsJava() { // 测试Java方言的关键字建议功能
    String sql = "select deptno, exi^ from emp where 1+2<3+4"; // 测试部分关键字EXISTS的补全
    fixture().withSql(sql).withLex(Lex.JAVA) // 使用Java词法规则
        .assertComplete("KEYWORD(EXISTS)\n", "exi", // 应该完整建议EXISTS关键字,并验证替换
            ImmutableMap.of("KEYWORD(EXISTS)", "exists"));
  }

  @Test void testAdviceMixedCase() { // 测试混合大小写的建议功能
    String sql = "select is^ from (select 1 isOne from emp)"; // 测试混合大小写标识符的补全
    fixture().withSql(sql).withLex(Lex.JAVA) // 使用Java词法规则
        .assertComplete("COLUMN(isOne)\n", "is", // 应该完整建议isOne列,并验证替换
            ImmutableMap.of("COLUMN(isOne)", "isOne"));
  }

  @Test void testAdviceExpression() { // 测试表达式的建议功能
    String sql = "select s.`count`+s.co^ from (select 1 `count` from emp) s"; // 测试表达式中部分标识符的补全
    fixture().withSql(sql).withLex(Lex.JAVA) // 使用Java词法规则
        .assertComplete("COLUMN(count)\n", "co", // 应该完整建议count列,并验证替换
            ImmutableMap.of("COLUMN(count)", "`count`"));
  }

  @Test void testAdviceEmptyFrom() { // 测试空FROM的建议功能
    String sql = "select * from^"; // 测试FROM关键字后的补全
    fixture().withSql(sql).withLex(Lex.JAVA) // 使用Java词法规则
        .assertComplete("KEYWORD(FROM)\n", "from", // 应该完整建议FROM关键字,并验证替换
            ImmutableMap.of("KEYWORD(FROM)", "from"));
  }

  @Disabled("Inserts are not supported by SimpleParser yet")
  @Test void testInsert() { // 测试INSERT语句的自动补全功能(已禁用,因为SimpleParser还不支持INSERT)
    final Fixture f = fixture();
    String sql;

    sql = "insert into emp(empno, mgr) select ^ from dept a"; // 测试INSERT中SELECT的补全
    f.withSql(sql)
        .assertComplete(getSelectKeywords(), // 应该完整建议SELECT关键字
            EXPR_KEYWORDS, // 表达式关键字
            A_TABLE, // 表A
            DEPT_COLUMNS, // DEPT表的所有列
            SETOPS, // 集合操作
            FETCH_OFFSET); // FETCH/OFFSET关键字

    sql = "insert into emp(empno, mgr) values (123, 3 + ^)"; // 测试INSERT中VALUES表达式的补全
    f.withSql(sql).assertComplete(EXPR_KEYWORDS); // 应该完整建议表达式关键字

    // Wish we could do better here. Parser gives error 'Non-query
    // expression encountered in illegal context' and cannot suggest
    // possible tokens.
    // 希望我们能在这里做得更好。解析器给出错误'在非法上下文中遇到非查询表达式',无法建议可能的标记。
    sql = "insert into emp(empno, mgr) ^"; // 测试INSERT开头的补全
    f.withSql(sql).assertComplete("", null); // 应该返回空结果
  }

  @Test void testNestSchema() { // 测试嵌套schema的自动补全功能
    final Fixture f = fixture();
    String sql;

    sql = "select * from sales.n^"; // 测试schema中部分标识符的补全
    f.withSql(sql)
        .assertComplete("SCHEMA(CATALOG.SALES.NEST)\n", "n", // 应该完整建议NEST schema,并验证替换
            ImmutableMap.of("SCHEMA(CATALOG.SALES.NEST)", "nest"));

    sql = "select * from sales.\"n^asfasdf"; // 测试双引号中部分标识符的补全
    f.withSql(sql)
        .assertComplete("SCHEMA(CATALOG.SALES.NEST)\n", "\"n", // 应该完整建议NEST schema,并验证替换
            ImmutableMap.of("SCHEMA(CATALOG.SALES.NEST)", "\"NEST\""));

    sql = "select * from sales.n^est"; // 测试schema中更长部分标识符的补全
    f.withSql(sql)
        .assertComplete("SCHEMA(CATALOG.SALES.NEST)\n", "n", // 应该完整建议NEST schema,并验证替换
            ImmutableMap.of("SCHEMA(CATALOG.SALES.NEST)", "nest"));

    sql = "select * from sales.nu^"; // 测试schema中不匹配部分标识符的补全
    f.withSql(sql).assertComplete("", "nu"); // 应该返回空结果
  }

  @Disabled("The set of completion results is empty")
  @Test void testNestTable1() { // 测试嵌套表1的自动补全功能(已禁用)
    final Fixture f = fixture();
    String sql;

    // select scott.emp.deptno from scott.emp; # valid
    // select scott.emp.deptno from scott.emp; # 有效
    sql = "select catalog.sales.emp.em^ from catalog.sales.emp"; // 测试嵌套表列的补全
    f.withSql(sql)
        .assertComplete("COLUMN(EMPNO)\n", "em", // 应该完整建议EMPNO列,并验证替换
            ImmutableMap.of("COLUMN(EMPNO)", "empno"));

    sql = "select catalog.sales.em^ from catalog.sales.emp"; // 测试嵌套表的补全
    f.withSql(sql)
        .assertComplete("TABLE(EMP)\n", "em", // 应该完整建议EMP表,并验证替换
            ImmutableMap.of("TABLE(EMP)", "emp"));
  }

  @Test void testNestTable2() { // 测试嵌套表2的自动补全功能
    // select scott.emp.deptno from scott.emp as e; # not valid
    // select scott.emp.deptno from scott.emp as e; # 无效
    String sql = "select catalog.sales.emp.em^ from catalog.sales.emp as e"; // 测试带别名的嵌套表列的补全
    fixture().withSql(sql)
        .assertComplete("", "em"); // 应该返回空结果(因为别名不匹配)
  }


  @Disabled("The set of completion results is empty")
  @Test void testNestTable3() { // 测试嵌套表3的自动补全功能(已禁用)
    String sql;

    // select scott.emp.deptno from emp; # valid
    // select scott.emp.deptno from emp; # 有效
    sql = "select catalog.sales.emp.em^ from emp"; // 测试FROM表与嵌套表不匹配的补全
    fixture().withSql(sql)
        .assertComplete("COLUMN(EMPNO)\n", "em", // 应该完整建议EMPNO列,并验证替换
            ImmutableMap.of("COLUMN(EMP)", "empno"));

    sql = "select catalog.sales.em^ from emp"; // 测试FROM表与嵌套表不匹配的补全
    fixture().withSql(sql)
        .assertComplete("TABLE(EMP)\n", "em", // 应该完整建议EMP表,并验证替换
            ImmutableMap.of("TABLE(EMP)", "emp"));
  }

  @Test void testNestTable4() { // 测试嵌套表4的自动补全功能
    // select scott.emp.deptno from emp as emp; # not valid
    // select scott.emp.deptno from emp as emp; # 无效
    String sql = "select catalog.sales.emp.em^ from catalog.sales.emp as emp"; // 测试别名与表名相同的嵌套表列的补全
    fixture().withSql(sql)
        .assertComplete("", "em"); // 应该返回空结果(因为别名不匹配)
  }

  @Test void testNestTableSchemaMustMatch() { // 测试嵌套表schema必须匹配的功能
    String sql;

    // select foo.emp.deptno from emp; # not valid
    // select foo.emp.deptno from emp; # 无效
    sql = "select sales.nest.em^ from catalog.sales.emp_r"; // 测试schema不匹配的嵌套表列的补全
    fixture().withSql(sql)
        .assertComplete("", "em"); // 应该返回空结果(因为schema不匹配)
  }

  @Test void testNestSchemaSqlServer() { // 测试SQL Server方言的嵌套schema补全功能
    final Fixture f = fixture().withLex(Lex.SQL_SERVER); // 使用SQL Server词法规则
    f.withSql("select * from SALES.N^") // 测试schema中部分标识符的补全
        .assertComplete("SCHEMA(CATALOG.SALES.NEST)\n", "N", // 应该完整建议NEST schema,并验证替换
            ImmutableMap.of("SCHEMA(CATALOG.SALES.NEST)", "NEST"));

    f.withSql("select * from SALES.[n^asfasdf") // 测试方括号中部分标识符的补全
        .assertComplete("SCHEMA(CATALOG.SALES.NEST)\n", "[n", // 应该完整建议NEST schema,并验证替换
            ImmutableMap.of("SCHEMA(CATALOG.SALES.NEST)", "[NEST]"));

    f.withSql("select * from SALES.[N^est") // 测试方括号中更长部分标识符的补全
        .assertComplete("SCHEMA(CATALOG.SALES.NEST)\n", "[N", // 应该完整建议NEST schema,并验证替换
            ImmutableMap.of("SCHEMA(CATALOG.SALES.NEST)", "[NEST]"));

    f.withSql("select * from SALES.NU^") // 测试schema中不匹配部分标识符的补全
        .assertComplete("", "NU"); // 应该返回空结果
  }

  @Test void testUnion() { // 测试UNION集合操作的自动补全功能
    // we simplify set ops such as UNION by removing other queries -
    // thereby avoiding validation errors due to mismatched select lists
    // 我们通过移除其他查询来简化UNION等集合操作 -
    // 从而避免由于SELECT列表不匹配导致的验证错误
    String sql =
        "select 1 from emp union select 2 from dept a where ^ and deptno < 5"; // 测试UNION中WHERE的补全
    String simplified =
        "SELECT * FROM dept a WHERE _suggest_ and deptno < 5";
    final Fixture f = fixture();
    f.withSql(sql)
        .assertSimplify(simplified) // 应该简化为带建议占位符的SQL
        .assertComplete(EXPR_KEYWORDS, A_TABLE, DEPT_COLUMNS); // 应该完整建议表达式关键字、表A和DEPT表的所有列

    // UNION ALL
    sql = "select 1 from emp\n" // 测试UNION ALL中WHERE的补全
        + "union all\n"
        + "select 2 from dept a where ^ and deptno < 5";
    f.withSql(sql).assertSimplify(simplified); // 应该简化为带建议占位符的SQL

    // hint is in first query
    // 提示在第一个查询中
    sql = "select 1 from emp group by ^ except select 2 from dept a"; // 测试EXCEPT中GROUP BY的补全
    simplified = "SELECT * FROM emp GROUP BY _suggest_";
    f.withSql(sql).assertSimplify(simplified); // 应该简化为带建议占位符的SQL
  }

  @Test void testMssql() { // 测试SQL Server方言的UNION补全功能
    String sql = "select 1 from [emp]\n" // 测试SQL Server方言的UNION中WHERE的补全
        + "union\n"
        + "select 2 from [DEPT] a where ^ and deptno < 5";
    String simplified =
        "SELECT * FROM [DEPT] a WHERE _suggest_ and deptno < 5";
    fixture()
        .withLex(Lex.SQL_SERVER) // 使用SQL Server词法规则
        .withSql(sql)
        .assertSimplify(simplified) // 应该简化为带建议占位符的SQL
        .assertComplete(EXPR_KEYWORDS, Collections.singletonList("TABLE(a)"), // 应该完整建议表达式关键字、表a和DEPT表的所有列
            DEPT_COLUMNS);
  }

  @Test void testFilterComment() { // 测试注释过滤功能
    // SqlSimpleParser.Tokenizer#nextToken() lines 401 - 423
    // is used to recognize the sql of TokenType.ID or some keywords
    // 用于识别TokenType.ID或某些关键字的SQL
    // if a certain segment of characters is continuously composed of Token,
    // the function of this code may be wrong
    // 如果某个字符段连续由Token组成,此代码的功能可能是错误的
    // E.g :
    // 例如:
    // (1)select * from a where price> 10.0--comment
    // 【10.0--comment】should be recognize as TokenType.ID("10.0") and TokenType.COMMENT
    // 【10.0--comment】应该被识别为TokenType.ID("10.0")和TokenType.COMMENT
    // but it recognize as TokenType.ID("10.0--comment")
    // 但它被识别为TokenType.ID("10.0--comment")
    // (2)select * from a where column_b='/* this is not comment */'
    // 【/* this is not comment */】should be recognize as
    // 【/* this is not comment */】应该被识别为
    // TokenType.SQID("/* this is not comment */"), but it was not
    // TokenType.SQID("/* this is not comment */"),但实际不是

    final String baseOriginSql = "select * from a "; // 基础SQL
    final String baseResultSql = "SELECT * FROM a "; // 基础结果SQL
    String originSql;

    // when SqlSimpleParser.Tokenizer#nextToken() method parse sql,
    // ignore the  "--" after 10.0, this is a comment,
    // but Tokenizer#nextToken() does not recognize it
    // 当SqlSimpleParser.Tokenizer#nextToken()方法解析SQL时,
    // 忽略10.0后的"--",这是一个注释,
    // 但Tokenizer#nextToken()没有识别它
    originSql = baseOriginSql + "where price > 10.0-- this is comment " // 测试行注释后的数字
        + System.lineSeparator() + " -- comment ";
    assertSimplifySql(originSql, baseResultSql + "WHERE price > 10.0"); // 应该正确识别注释

    originSql = baseOriginSql + "where column_b='/* this is not comment */'"; // 测试字符串中的块注释
    assertSimplifySql(originSql,
        baseResultSql + "WHERE column_b= '/* this is not comment */'"); // 应该保留字符串中的注释

    originSql = baseOriginSql + "where column_b='2021 --this is not comment'"; // 测试字符串中的行注释
    assertSimplifySql(originSql,
        baseResultSql + "WHERE column_b= '2021 --this is not comment'"); // 应该保留字符串中的注释

    originSql = baseOriginSql + "where column_b='2021--this is not comment'"; // 测试字符串中无空格的行注释
    assertSimplifySql(originSql,
        baseResultSql + "WHERE column_b= '2021--this is not comment'"); // 应该保留字符串中的注释
  }

  /**
   * Tests that the simplified originSql is consistent with expectedSql.
   * 测试简化的originSql与expectedSql一致。
   *
   * @param originSql   a string sql to simplify. // 要简化的SQL字符串
   * @param expectedSql Expected result after simplification. // 简化后的预期结果
   */
  private void assertSimplifySql(String originSql, String expectedSql) {
    SqlSimpleParser simpleParser =
        new SqlSimpleParser("_suggest_", SqlParser.Config.DEFAULT); // 创建简单解析器,使用默认配置和建议占位符

    String actualSql = simpleParser.simplifySql(originSql); // 简化SQL
    assertThat("simpleParser.simplifySql(" + originSql + ")", // 验证简化结果是否与预期一致
        actualSql, equalTo(expectedSql));
  }

  /** Fixture for the advisor test. */
  // Advisor测试的测试夹具,继承自SqlValidatorFixture,用于提供测试环境和辅助方法
  static class Fixture extends SqlValidatorFixture {
    protected Fixture(SqlTester tester, SqlTestFactory factory,
        StringAndPos sap, boolean expression, boolean whole) {
      super(tester, factory, sap, expression, whole); // 调用父类构造函数,初始化测试器、工厂、SQL字符串和位置、表达式模式和完整模式
    }

    @SuppressWarnings("deprecation")
    @Override public Fixture withTester(UnaryOperator<SqlTester> transform) {
      final SqlTester tester = transform.apply(this.tester); // 应用转换函数到测试器
      return new Fixture(tester, factory, sap, expression, whole); // 返回新的Fixture实例
    }

    @Override public Fixture withFactory(
        UnaryOperator<SqlTestFactory> transform) {
      final SqlTestFactory factory = transform.apply(this.factory); // 应用转换函数到工厂
      return new Fixture(tester, factory, sap, expression, whole); // 返回新的Fixture实例
    }

    @Override public Fixture withLex(Lex lex) {
      return (Fixture) super.withLex(lex); // 设置词法规则,返回Fixture类型
    }

    @Override public Fixture withSql(String sql) {
      return new Fixture(tester, factory, StringAndPos.of(sql), false, false); // 设置SQL字符串,创建新的Fixture实例
    }

    private void assertTokenizesTo(String expected) {
      SqlSimpleParser.Tokenizer tokenizer =
          new SqlSimpleParser.Tokenizer(sap.sql, "xxxxx", // 创建分词器,使用SQL字符串、建议占位符和引号配置
              factory.parserConfig().quoting());
      StringBuilder buf = new StringBuilder();
      while (true) {
        SqlSimpleParser.Token token = tokenizer.nextToken(); // 获取下一个标记
        if (token == null) {
          break;
        }
        buf.append(token).append("\n"); // 将标记添加到缓冲区
      }
      assertThat(buf, hasToString(expected)); // 验证标记化结果是否与预期一致
    }

    protected void assertHint(List<String>... expectedLists) {
      List<String> expectedList = plus(expectedLists); // 合并所有预期列表
      final String expected = toString(new TreeSet<>(expectedList)); // 转换为排序后的字符串
      assertHint(expected); // 断言提示结果
    }

    /**
     * Checks that a given SQL statement yields the expected set of completion
     * hints.
     * 检查给定的SQL语句是否产生预期的补全提示集合。
     *
     * @param expectedResults Expected list of hints // 预期的提示列表
     */
    protected void assertHint(String expectedResults) {
      SqlAdvisor advisor = factory.createAdvisor(); // 创建SQL建议器

      List<SqlMoniker> results =
          advisor.getCompletionHints( // 获取补全提示
              sap.sql,
              requireNonNull(sap.pos, "sap.pos"));
      assertThat(convertCompletionHints(results), is(expectedResults)); // 验证提示结果是否与预期一致
    }

    /**
     * Tests that a given SQL statement simplifies to the salesTables result.
     * 测试给定的SQL语句是否简化为预期结果。
     *
     * @param expected Expected result after simplification. // 简化后的预期结果
     */
    protected Fixture assertSimplify(String expected) {
      SqlAdvisor advisor = factory.createAdvisor(); // 创建SQL建议器

      String actual = advisor.simplifySql(sap.sql, sap.cursor); // 简化SQL
      assertThat(actual, is(expected)); // 验证简化结果是否与预期一致
      return this; // 返回当前实例以支持链式调用
    }

    protected void assertComplete(List<String>... expectedResults) {
      assertComplete(null, expectedResults); // 断言完整补全结果,不指定替换映射
    }

    protected void assertComplete(Map<String, String> replacements,
        List<String>... expectedResults) {
      List<String> expectedList = plus(expectedResults); // 合并所有预期列表
      String expected = toString(new TreeSet<>(expectedList)); // 转换为排序后的字符串
      assertComplete(expected, null, replacements); // 断言完整补全结果
    }

    protected void assertComplete(String expectedResults,
        @Nullable String expectedWord) {
      assertComplete(expectedResults, expectedWord, null); // 断言完整补全结果,不指定替换映射
    }

    /**
     * Tests that a given SQL which may be invalid or incomplete simplifies
     * itself and yields the salesTables set of completion hints. This is an
     * integration test of {@link #assertHint} and {@link #assertSimplify}.
     * 测试给定的可能无效或不完整的SQL是否简化自身并产生预期的补全提示集合。
     * 这是{@link #assertHint}和{@link #assertSimplify}的集成测试。
     *
     * @param expectedResults Expected list of hints // 预期的提示列表
     * @param expectedWord    Word that we expect to be replaced, or null if we
     *                        don't care // 我们期望被替换的单词,如果不关心则为null
     */
    protected void assertComplete(String expectedResults,
        @Nullable String expectedWord,
        @Nullable Map<String, String> replacements) {
      SqlAdvisor advisor = factory.createAdvisor(); // 创建SQL建议器

      final String[] replaced = {null};
      List<SqlMoniker> results =
          advisor.getCompletionHints(sap.sql, sap.cursor, replaced); // 获取补全提示和被替换的单词
      assertThat("Completion hints for " + sap, convertCompletionHints(results), // 验证提示结果是否与预期一致
          is(expectedResults));
      if (expectedWord != null) {
        assertThat("replaced[0] for " + sap, replaced[0], is(expectedWord)); // 验证被替换的单词是否与预期一致
      } else {
        assertNotNull(replaced[0]); // 确保被替换的单词不为null
      }
      assertReplacements(replacements, advisor, replaced[0], results); // 验证替换映射
    }

    private void assertReplacements(@Nullable Map<String, String> replacements,
        SqlAdvisor advisor, String word, List<SqlMoniker> results) {
      if (replacements == null) {
        return; // 如果没有替换映射,直接返回
      }
      Set<String> missingReplacemenets = new HashSet<>(replacements.keySet()); // 创建缺失替换的集合
      for (SqlMoniker result : results) {
        String id = result.id(); // 获取结果ID
        String expectedReplacement = replacements.get(id); // 获取预期替换
        if (expectedReplacement == null) {
          continue; // 如果没有预期替换,跳过
        }
        missingReplacemenets.remove(id); // 从缺失集合中移除
        String actualReplacement = advisor.getReplacement(result, word); // 获取实际替换
        assertThat(sap + ", replacement of " + word + " with " + id, // 验证替换是否与预期一致
            actualReplacement, is(expectedReplacement));
      }
      if (missingReplacemenets.isEmpty()) {
        return; // 如果所有替换都已验证,返回
      }
      fail("Sql " + sap + " did not produce replacement hints " // 如果有缺失的替换,失败
          + missingReplacemenets);
    }

    private String convertCompletionHints(List<SqlMoniker> hints) {
      List<String> list = new ArrayList<>();
      for (SqlMoniker hint : hints) {
        if (hint.getType() != SqlMonikerType.FUNCTION) { // 过滤掉函数类型的提示
          list.add(hint.id()); // 添加提示ID到列表
        }
      }
      Collections.sort(list); // 排序列表
      return toString(list); // 转换为字符串
    }

    /**
     * Converts a list to a string, one item per line.
     * 将列表转换为字符串,每行一个项目。
     *
     * @param list List // 列表
     * @return String with one item of the list per line // 每行一个列表项的字符串
     */
    private static <T> String toString(Collection<T> list) {
      StringBuilder buf = new StringBuilder();
      for (T t : list) {
        buf.append(t).append("\n"); // 将每个项目添加到缓冲区,每行一个
      }
      return buf.toString(); // 返回字符串
    }

    /**
     * Concatenates several lists of the same type into a single list.
     * 将多个相同类型的列表连接成一个列表。
     *
     * @param lists Lists to concatenate // 要连接的列表
     * @return Sum list // 合并后的列表
     */
    protected static <T> List<T> plus(List<T>... lists) {
      final List<T> result = new ArrayList<>();
      for (List<T> list : lists) {
        result.addAll(list); // 将每个列表的所有元素添加到结果列表
      }
      return result; // 返回合并后的列表
    }
  }
}
