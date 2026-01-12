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
package org.apache.calcite.test; // 包声明，位于 org.apache.calcite.test 包下

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入可枚举规则类，用于定义可枚举操作相关的规则
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入反射模式类，用于通过反射方式创建数据源模式
import org.apache.calcite.config.CalciteConnectionProperty; // 导入 Calcite 连接属性配置类
import org.apache.calcite.config.Lex; // 导入词法分析配置类，定义 SQL 解析的词法规则
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器接口，用于查询计划优化
import org.apache.calcite.runtime.Hook; // 导入钩子类，用于在特定执行点插入自定义逻辑
import org.apache.calcite.test.CalciteAssert.AssertThat; // 导入断言构建器类，用于构建测试断言
import org.apache.calcite.test.CalciteAssert.DatabaseInstance; // 导入数据库实例枚举，标识不同的数据库类型
import org.apache.calcite.test.schemata.foodmart.FoodmartSchema; // 导入 FoodMart 测试数据模式类
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入 HR（人力资源）测试数据模式类
import org.apache.calcite.util.Smalls; // 导入测试工具类，包含各种测试辅助方法和小型测试对象
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试相关的实用方法

import org.hsqldb.jdbcDriver; // 导入 HSQLDB JDBC 驱动类
import org.junit.jupiter.api.Test; // 导入 JUnit 5 测试注解，标记测试方法

import java.sql.Connection; // 导入 JDBC 连接接口，用于建立数据库连接
import java.sql.DriverManager; // 导入 JDBC 驱动管理器，用于获取数据库连接
import java.sql.ResultSet; // 导入 JDBC 结果集接口，表示查询结果
import java.sql.SQLException; // 导入 JDBC SQL 异常类，表示数据库操作异常
import java.sql.Statement; // 导入 JDBC 语句接口，用于执行 SQL 语句
import java.util.Properties; // 导入属性类，用于存储配置信息
import java.util.concurrent.locks.Lock; // 导入锁接口，用于线程同步
import java.util.concurrent.locks.ReentrantLock; // 导入可重入锁实现类
import java.util.function.Consumer; // 导入函数式接口，用于消费操作

import static org.hamcrest.CoreMatchers.equalTo; // 导入 Hamcrest 匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 匹配器，用于断言布尔值
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 断言工具类
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入 JUnit 断言方法，用于断言为假

/**
 * Tests for the {@code org.apache.calcite.adapter.jdbc} package.
 * org.apache.calcite.adapter.jdbc 包的测试类
 * 
 * 该测试类专门用于测试 Calcite 的 JDBC 适配器功能，包括：
 * 1. SQL 查询下推到 JDBC 数据库的能力
 * 2. 各种 SQL 操作（JOIN、聚合、窗口函数、DML 等）的 SQL 生成和执行
 * 3. 不同数据库方言（HSQLDB、PostgreSQL、MySQL 等）的兼容性测试
 * 4. 查询计划优化和规则应用验证
 * 5. 元数据获取和类型转换测试
 * 
 * 测试覆盖了 JDBC 适配器的核心功能，确保 Calcite 能够正确地将
 * 关系代数操作转换为底层数据库的 SQL 语句，并正确处理结果。
 */
class JdbcAdapterTest { // JdbcAdapterTest 类：JDBC 适配器测试类

  /** Ensures that tests that are modifying data (doing DML) do not run at the
   * same time.
   * 确保修改数据（执行 DML 操作）的测试不会同时运行。
   * 
   * 这是一个静态的可重入锁，用于在多线程环境中同步执行数据修改操作，
   * 防止多个测试同时修改同一张表导致数据不一致或竞争条件。
   * 使用 ReentrantLock 可以支持同一个线程多次获取锁，避免死锁。
   */
  private static final ReentrantLock LOCK = new ReentrantLock(); // 静态可重入锁，用于同步 DML 操作

  /** VALUES is pushed down.
   * 测试 VALUES 子句能否被下推到 JDBC 数据库执行。
   * 
   * 该测试验证 Calcite 能否将 VALUES 子句转换为数据库原生的 VALUES 语法，
   * 而不是在 Calcite 层面处理数据。这对于提高性能很重要，因为可以
   * 利用数据库的优化能力。
   */
  @Test void testValuesPlan() { // 测试方法：测试 VALUES 计划下推
    final String sql = "select * from \"days\", (values 1, 2) as t(c)"; // SQL 查询：从 days 表和 VALUES 子句生成的临时表 t 中选择所有列
    final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划字符串
        + "  JdbcJoin(condition=[true], joinType=[inner])\n" // JDBC 连接节点，内连接
        + "    JdbcTableScan(table=[[foodmart, days]])\n" // JDBC 表扫描：扫描 foodmart.days 表
        + "    JdbcValues(tuples=[[{ 1 }, { 2 }]])"; // JDBC VALUES 子句：包含两个元组 {1} 和 {2}
    final String jdbcSql = "SELECT *\n" // 预期生成的 JDBC SQL 语句
        + "FROM \"foodmart\".\"days\",\n" // 从 foodmart.days 表
        + "(VALUES (1),\n" // 和 VALUES 子句（包含 1 和 2）
        + "(2)) AS \"t\" (\"C\")"; // 生成的别名为 t，列别名为 C
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query(sql) // 执行 SQL 查询
        .explainContains(explain) // 验证执行计划包含预期的节点
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB // 仅在 HSQLDB 或 PostgreSQL 数据库上启用
            || CalciteAssert.DB == DatabaseInstance.POSTGRESQL)
        .planHasSql(jdbcSql) // 验证生成的 SQL 包含预期的 JDBC SQL
        .returnsCount(14); // 验证返回 14 行结果
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6462">[CALCITE-6462]
   * VolcanoPlanner internal valid may throw exception when log trace is enabled</a>.
   * 测试用例：验证 VolcanoPlanner 在启用日志跟踪时内部验证不会抛出异常。
   * 
   * 该测试针对 CALCITE-6462 问题，当启用日志跟踪时，VolcanoPlanner 的内部
   * 验证可能会抛出异常。测试通过执行一个包含子查询的 LEFT JOIN 来验证
   * 修复是否有效。
   */
  @Test void testVolcanoPlannerInternalValid() { // 测试方法：测试 VolcanoPlanner 内部验证
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select *\n" // SQL 查询：选择所有列
            + "from scott.emp e left join scott.dept d\n" // 从 emp 表左连接 dept 表
            + "on 'job' in (select job from scott.bonus b)") // 连接条件：'job' 在 bonus 表的 job 列中
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], DEPTNO0=[$8], DNAME=[$9], LOC=[$10])\n" // JDBC 投影节点，选择多个列
            + "    JdbcJoin(condition=[true], joinType=[left])\n" // JDBC 左连接节点
            + "      JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcJoin(condition=[true], joinType=[inner])\n" // JDBC 内连接节点
            + "        JdbcTableScan(table=[[SCOTT, DEPT]])\n" // 扫描 SCOTT.DEPT 表
            + "        JdbcAggregate(group=[{0}])\n" // JDBC 聚合节点，按第 0 列分组
            + "          JdbcProject(cs=[true])\n" // JDBC 投影节点，生成常量 true
            + "            JdbcFilter(condition=[=('job', $1)])\n" // JDBC 过滤节点，过滤条件为 job = $1
            + "              JdbcTableScan(table=[[SCOTT, BONUS]])\n\n") // 扫描 SCOTT.BONUS 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .returnsCount(14); // 验证返回 14 行结果
  }

  @Test void testUnionPlan() { // 测试方法：测试 UNION 计划
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query("select * from \"sales_fact_1997\"\n" // SQL 查询：从 sales_fact_1997 表选择所有列
            + "union all\n" // UNION ALL 操作（保留重复行）
            + "select * from \"sales_fact_1998\"") // 从 sales_fact_1998 表选择所有列
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcUnion(all=[true])\n" // JDBC UNION 节点，all=true 表示保留重复行
            + "    JdbcTableScan(table=[[foodmart, sales_fact_1997]])\n" // 扫描 sales_fact_1997 表
            + "    JdbcTableScan(table=[[foodmart, sales_fact_1998]])") // 扫描 sales_fact_1998 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT *\n" // 验证生成的 SQL
            + "FROM \"foodmart\".\"sales_fact_1997\"\n" // 从 sales_fact_1997 表
            + "UNION ALL\n" // 使用 UNION ALL
            + "SELECT *\n" // 选择所有列
            + "FROM \"foodmart\".\"sales_fact_1998\""); // 从 sales_fact_1998 表
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3115">[CALCITE-3115]
   * Cannot add JdbcRules which have different JdbcConvention
   * to same VolcanoPlanner's RuleSet</a>.
   * 测试用例：验证不同 JdbcConvention 的规则可以添加到同一个 VolcanoPlanner 中。
   * 
   * 该测试针对 CALCITE-3115 问题，当使用多个具有不同 JdbcConvention 的
   * JDBC 数据源时，规则集无法正确添加。测试通过连接两个不同数据库
   * （foodmart 和 SCOTT）的表来验证修复。
   */
  @Test void testUnionPlan2() { // 测试方法：测试跨数据库 UNION 计划
    CalciteAssert.model(JdbcTest.FOODMART_SCOTT_MODEL) // 使用包含 foodmart 和 SCOTT 的测试模型
        .query("select \"store_name\" from \"foodmart\".\"store\" where \"store_id\" < 10\n" // SQL 查询：从 foodmart.store 表选择 store_name，条件是 store_id < 10
            + "union all\n" // UNION ALL 操作
            + "select ename from SCOTT.emp where empno > 10") // 从 SCOTT.emp 表选择 ename，条件是 empno > 10
        .explainContains("PLAN=EnumerableUnion(all=[true])\n" // 预期的执行计划：使用 EnumerableUnion（可枚举联合）
            + "  JdbcToEnumerableConverter\n" // JDBC 到可枚举转换器
            + "    JdbcProject(store_name=[$3])\n" // JDBC 投影：选择 store_name 列
            + "      JdbcFilter(condition=[<($0, 10)])\n" // JDBC 过滤：store_id < 10
            + "        JdbcTableScan(table=[[foodmart, store]])\n" // 扫描 foodmart.store 表
            + "  JdbcToEnumerableConverter\n" // 另一个 JDBC 到可枚举转换器
            + "    JdbcProject(ENAME=[CAST($1):VARCHAR(30)])\n" // JDBC 投影：将 ename 转换为 VARCHAR(30)
            + "      JdbcFilter(condition=[>(CAST($0):INTEGER NOT NULL, 10)])\n" // JDBC 过滤：empno 转换为整数后 > 10
            + "        JdbcTableScan(table=[[SCOTT, EMP]])") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"store_name\"\n" // 验证第一个 SQL
                + "FROM \"foodmart\".\"store\"\n" // 从 foodmart.store 表
                + "WHERE \"store_id\" < 10") // 过滤条件
        .planHasSql("SELECT CAST(\"ENAME\" AS VARCHAR(30)) AS \"ENAME\"\n" // 验证第二个 SQL
            + "FROM \"SCOTT\".\"EMP\"\n" // 从 SCOTT.EMP 表
            + "WHERE CAST(\"EMPNO\" AS INTEGER) > 10"); // 过滤条件
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6268">[CALCITE-6268]
   * Support implementing custom JdbcSchema</a>.
   * 测试用例：验证支持自定义 JdbcSchema 实现。
   * 
   * 该测试针对 CALCITE-6268 功能，允许用户实现自定义的 JdbcSchema
   * 来扩展或修改 JDBC 适配器的行为。
   */
  @Test void testCustomJdbc() { // 测试方法：测试自定义 JDBC Schema
    CalciteAssert.model(JdbcTest.FOODMART_SCOTT_CUSTOM_MODEL) // 使用包含自定义 JDBC Schema 的测试模型
        .query("select * from SCOTT.emp\n") // SQL 查询：从 SCOTT.emp 表选择所有列
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT *\nFROM \"SCOTT\".\"EMP\"") // 验证生成的 SQL
        .returnsCount(14); // 验证返回 14 行结果
  }

  @Test void testFilterUnionPlan() { // 测试方法：测试过滤 UNION 计划
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query("select * from (\n" // SQL 查询：从子查询选择所有列
            + "  select * from \"sales_fact_1997\"\n" // 子查询第一部分：sales_fact_1997 表
            + "  union all\n" // UNION ALL
            + "  select * from \"sales_fact_1998\")\n" // 子查询第二部分：sales_fact_1998 表
            + "where \"product_id\" = 1") // 外层过滤条件：product_id = 1
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT 1 AS \"product_id\", \"time_id\", \"customer_id\", " // 验证生成的 SQL
            + "\"promotion_id\", \"store_id\", \"store_sales\", " // 选择列
            + "\"store_cost\", \"unit_sales\"\n" // 选择列
            + "FROM (SELECT \"time_id\", \"customer_id\", \"promotion_id\", \"store_id\", " // 内层子查询
            + "\"store_sales\", \"store_cost\", \"unit_sales\"\n" // 选择列
            + "FROM \"foodmart\".\"sales_fact_1997\"\n" // 从 sales_fact_1997 表
            + "WHERE \"product_id\" = 1\n" // 过滤条件
            + "UNION ALL\n" // UNION ALL
            + "SELECT \"time_id\", \"customer_id\", \"promotion_id\", \"store_id\", " // 第二部分
            + "\"store_sales\", \"store_cost\", \"unit_sales\"\n" // 选择列
            + "FROM \"foodmart\".\"sales_fact_1998\"\n" // 从 sales_fact_1998 表
            + "WHERE \"product_id\" = 1) AS \"t3\""); // 过滤条件并添加别名
  }

  @Test void testInPlan() { // 测试方法：测试 IN 子句计划
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query("select \"store_id\", \"store_name\" from \"store\"\n" // SQL 查询：从 store 表选择 store_id 和 store_name
            + "where \"store_name\" in ('Store 1', 'Store 10', 'Store 11', 'Store 15', 'Store 16', 'Store 24', 'Store 3', 'Store 7')") // IN 子句条件
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"store_id\", \"store_name\"\n" // 验证生成的 SQL
            + "FROM \"foodmart\".\"store\"\n" // 从 store 表
            + "WHERE \"store_name\" IN ('Store 1', 'Store 10', 'Store 11'," // IN 子句
            + " 'Store 15', 'Store 16', 'Store 24', 'Store 3', 'Store 7')") // IN 子句
        .returns("store_id=1; store_name=Store 1\n" // 验证返回结果
            + "store_id=3; store_name=Store 3\n" // 验证返回结果
            + "store_id=7; store_name=Store 7\n" // 验证返回结果
            + "store_id=10; store_name=Store 10\n" // 验证返回结果
            + "store_id=11; store_name=Store 11\n" // 验证返回结果
            + "store_id=15; store_name=Store 15\n" // 验证返回结果
            + "store_id=16; store_name=Store 16\n" // 验证返回结果
            + "store_id=24; store_name=Store 24\n"); // 验证返回结果
  }

  @Test void testEquiJoinPlan() { // 测试方法：测试等值连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select empno, ename, e.deptno, dname\n" // SQL 查询：选择 empno, ename, e.deptno, dname
            + "from scott.emp e inner join scott.dept d\n" // emp 表和 dept 表内连接
            + "on e.deptno = d.deptno") // 连接条件：e.deptno = d.deptno
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$2], DNAME=[$4])\n" // JDBC 投影：选择指定列
            + "    JdbcJoin(condition=[=($2, $3)], joinType=[inner])\n" // JDBC 内连接：条件为 $2 = $3
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // 左侧投影：选择 empno, ename, deptno
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // 右侧投影：选择 deptno, dname
            + "        JdbcTableScan(table=[[SCOTT, DEPT]])") // 扫描 SCOTT.DEPT 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\", \"t\".\"ENAME\", " // 验证生成的 SQL
            + "\"t\".\"DEPTNO\", \"t0\".\"DNAME\"\n" // 选择列
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"DEPTNO\"\n" // 子查询：选择 emp 表的列
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"DEPTNO\", \"DNAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"DEPT\") AS \"t0\" " // 别名为 t0
            + "ON \"t\".\"DEPTNO\" = \"t0\".\"DEPTNO\""); // 连接条件
  }

  @Test void testPushDownSort() { // 测试方法：测试排序下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .with(CalciteConnectionProperty.TOPDOWN_OPT.camelName(), false) // 禁用自顶向下优化
        .query("select ename\n" // SQL 查询：选择 ename
            + "from scott.emp\n" // 从 emp 表
            + "order by empno") // 按 empno 排序
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcSort(sort0=[$1], dir0=[ASC])\n" // JDBC 排序：按第 1 列升序排序
            + "    JdbcProject(ENAME=[$1], EMPNO=[$0])\n" // JDBC 投影：选择 ename 和 empno
            + "      JdbcTableScan(table=[[SCOTT, EMP]])") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"ENAME\", \"EMPNO\"\n" // 验证生成的 SQL
            + "FROM \"SCOTT\".\"EMP\"\n" // 从 emp 表
            + "ORDER BY \"EMPNO\" NULLS LAST"); // 按 empno 排序，NULL 值排在最后
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6401">[CALCITE-6401]
   * JdbcAdapter cannot push down NOT IN sub-queries</a>.
   * 测试用例：验证 JDBC 适配器能够下推 NOT IN 子查询。
   * 
   * 该测试针对 CALCITE-6401 问题，NOT IN 子查询无法正确下推到 JDBC 数据库。
   * 测试通过执行包含 NOT IN 子查询的查询来验证修复。
   */
  @Test void testPushDownNotIn() { // 测试方法：测试 NOT IN 下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select * from dept where deptno not in (select deptno from emp)") // SQL 查询：从 dept 表选择 deptno 不在 emp 表的 deptno 中的行
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(DEPTNO=[$0], DNAME=[$1], LOC=[$2])\n" // JDBC 投影：选择 deptno, dname, loc
            + "    JdbcFilter(condition=[OR(=($3, 0), AND(IS NULL($6), >=($4, $3)))])\n" // JDBC 过滤：NOT IN 转换后的条件
            + "      JdbcJoin(condition=[=($0, $5)], joinType=[left])\n" // JDBC 左连接
            + "        JdbcJoin(condition=[true], joinType=[inner])\n" // JDBC 内连接
            + "          JdbcTableScan(table=[[SCOTT, DEPT]])\n" // 扫描 SCOTT.DEPT 表
            + "          JdbcAggregate(group=[{}], c=[COUNT()], ck=[COUNT($7)])\n" // JDBC 聚合：计算总行数和非空行数
            + "            JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "        JdbcAggregate(group=[{7}], i=[LITERAL_AGG(true)])\n" // JDBC 聚合：按 deptno 分组
            + "          JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"DEPT\".\"DEPTNO\", \"DEPT\".\"DNAME\", \"DEPT\".\"LOC\"\n" // 验证生成的 SQL
            + "FROM \"SCOTT\".\"DEPT\"\n" // 从 dept 表
            + "CROSS JOIN (SELECT COUNT(*) AS \"c\", COUNT(\"DEPTNO\") AS \"ck\"\n" // 交叉连接聚合子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 计算总行数和非空 deptno 数
            + "LEFT JOIN (SELECT \"DEPTNO\", TRUE AS \"i\"\n" // 左连接分组子查询
            + "FROM \"SCOTT\".\"EMP\"\n" // 从 emp 表
            + "GROUP BY \"DEPTNO\") AS \"t0\" ON \"DEPT\".\"DEPTNO\" = \"t0\".\"DEPTNO\"\n" // 连接条件
            + "WHERE \"t\".\"c\" = 0 OR \"t0\".\"i\" IS NULL AND \"t\".\"ck\" >= \"t\".\"c\""); // NOT IN 转换后的条件
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6401">[CALCITE-6401]
   * JDBC adapter cannot push down JOIN with condition
   * includes IS TRUE、IS NULL、Dynamic parameter、CAST、Literal comparison</a>.
   * 测试用例：验证 JDBC 适配器能够下推包含 IS TRUE 条件的 JOIN。
   * 
   * 该测试针对 CALCITE-6401 问题，JOIN 条件中的 IS TRUE、IS NULL、动态参数、
   * CAST 和字面量比较无法正确下推。测试通过执行包含 IS TRUE 条件的
   * JOIN 查询来验证修复。
   */
  @Test void testJoinConditionPushDownIsTrue() { // 测试方法：测试 JOIN 条件 IS TRUE 下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select emp1.empno\n" // SQL 查询：选择 emp1.empno
            + "from scott.emp as emp1 join scott.emp as emp2 on\n" // emp 表自连接
            + "(emp1.empno = emp2.empno) is true\n" // 连接条件：emp1.empno = emp2.empno 为 true
            + " and (emp1.ename = emp2.ename) is not true") // 连接条件：emp1.ename = emp2.ename 不为 true
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0])\n" // JDBC 投影：选择 empno
            + "    JdbcJoin(condition=[AND(=($0, $2), IS NOT TRUE(=($1, $3)))], joinType=[inner])\n" // JDBC 内连接：包含 IS NOT TRUE 条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 右侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"ENAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" ON \"t\".\"EMPNO\" = \"t0\".\"EMPNO\" AND \"t\".\"ENAME\" = \"t0\".\"ENAME\" IS NOT TRUE"); // 连接条件包含 IS NOT TRUE
  }

  @Test void testJoinConditionPushDownIsFalse() { // 测试方法：测试 JOIN 条件 IS FALSE 下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select emp1.empno\n" // SQL 查询：选择 emp1.empno
            + "from scott.emp as emp1 join scott.emp as emp2 on\n" // emp 表自连接
            + "(emp1.empno = emp2.empno) is false\n" // 连接条件：emp1.empno = emp2.empno 为 false
            + " and (emp1.ename = emp2.ename) is not false") // 连接条件：emp1.ename = emp2.ename 不为 false
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0])\n" // JDBC 投影：选择 empno
            + "    JdbcJoin(condition=[AND(<>($0, $2), IS NOT FALSE(=($1, $3)))], joinType=[inner])\n" // JDBC 内连接：包含 IS NOT FALSE 条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 右侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"ENAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" ON \"t\".\"EMPNO\" <> \"t0\".\"EMPNO\" AND \"t\".\"ENAME\" = \"t0\".\"ENAME\" IS NOT FALSE"); // 连接条件包含 IS NOT FALSE
  }

  @Test void testJoinConditionPushDownIsNull() { // 测试方法：测试 JOIN 条件 IS NULL 下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select emp1.empno\n" // SQL 查询：选择 emp1.empno
            + "from scott.emp as emp1 join scott.emp as emp2 on\n" // emp 表自连接
            + "(emp1.empno = emp2.empno or emp1.ename is null)\n" // 连接条件：empno 相等或 ename 为 null
            + " and (emp1.ename = emp2.ename or emp2.empno is null)") // 连接条件：ename 相等或 emp2.empno 为 null
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0])\n" // JDBC 投影：选择 empno
            + "    JdbcJoin(condition=[AND(OR(IS NULL($1), =($0, $2)), =($1, $3))], joinType=[inner])\n" // JDBC 内连接：包含 IS NULL 条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 右侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"ENAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" ON (\"t\".\"ENAME\" IS NULL OR \"t\".\"EMPNO\" = \"t0\".\"EMPNO\") AND \"t\".\"ENAME\" = \"t0\".\"ENAME\""); // 连接条件包含 IS NULL
  }

  @Test void testJoinConditionPushDownIsNotNull() { // 测试方法：测试 JOIN 条件 IS NOT NULL 下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select emp1.empno\n" // SQL 查询：选择 emp1.empno
            + "from scott.emp as emp1 join scott.emp as emp2 on\n" // emp 表自连接
            + "(emp1.empno = emp2.empno and emp1.ename is not null)\n" // 连接条件：empno 相等且 ename 不为 null
            + " or (emp1.ename = emp2.ename and emp2.empno is not null)") // 连接条件：ename 相等且 emp2.empno 不为 null
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0])\n" // JDBC 投影：选择 empno
            + "    JdbcJoin(condition=[OR(AND(=($0, $2), IS NOT NULL($1)), =($1, $3))], joinType=[inner])\n" // JDBC 内连接：包含 IS NOT NULL 条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 右侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"ENAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" ON \"t\".\"EMPNO\" = \"t0\".\"EMPNO\" AND \"t\".\"ENAME\" IS NOT NULL OR \"t\".\"ENAME\" = \"t0\".\"ENAME\""); // 连接条件包含 IS NOT NULL
  }

  @Test void testJoinConditionPushDownLiteral() { // 测试方法：测试 JOIN 条件字面量下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select emp1.empno\n" // SQL 查询：选择 emp1.empno
            + "from scott.emp as emp1 join scott.emp as emp2 on\n" // emp 表自连接
            + "(emp1.empno = emp2.empno and emp1.ename = 'empename') or\n" // 连接条件：empno 相等且 ename 等于 'empename'
            + "(emp1.ename = emp2.ename and emp2.empno = 5)") // 连接条件：ename 相等且 emp2.empno 等于 5
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0])\n" // JDBC 投影：选择 empno
            + "    JdbcJoin(condition=[OR(AND(=($0, $2), =($1, 'empename')), AND(=($1, $3), =(CAST($2):INTEGER NOT NULL, 5)))], joinType=[inner])\n" // JDBC 内连接：包含字面量比较
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 右侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"ENAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" ON \"t\".\"EMPNO\" = \"t0\".\"EMPNO\" AND \"t\".\"ENAME\" = 'empename' OR \"t\".\"ENAME\" = \"t0\".\"ENAME\" AND CAST(\"t0\".\"EMPNO\" AS INTEGER) = 5"); // 连接条件包含字面量和 CAST
  }

  @Test void testJoinConditionPushDownCast() { // 测试方法：测试 JOIN 条件 CAST 下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select emp1.empno\n" // SQL 查询：选择 emp1.empno
            + "from scott.emp as emp1 join scott.emp as emp2 on\n" // emp 表自连接
            + "(emp1.empno = emp2.empno and emp1.ename = 'empename') or\n" // 连接条件：empno 相等且 ename 等于 'empename'
            + "(emp1.ename = emp2.ename and emp2.empno = 5)") // 连接条件：ename 相等且 emp2.empno 等于 5
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0])\n" // JDBC 投影：选择 empno
            + "    JdbcJoin(condition=[OR(AND(=($0, $2), =($1, 'empename')), AND(=($1, $3), =(CAST($2):INTEGER NOT NULL, 5)))], joinType=[inner])\n" // JDBC 内连接：包含 CAST
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 右侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"ENAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" ON \"t\".\"EMPNO\" = \"t0\".\"EMPNO\" AND \"t\".\"ENAME\" = 'empename' OR \"t\".\"ENAME\" = \"t0\".\"ENAME\" AND CAST(\"t0\".\"EMPNO\" AS INTEGER) = 5"); // 连接条件包含 CAST
  }

  @Test void testJoinConditionPushDownDynamicParam() { // 测试方法：测试 JOIN 条件动态参数下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select emp1.empno\n" // SQL 查询：选择 emp1.empno
            + "from scott.emp as emp1 join scott.emp as emp2 on\n" // emp 表自连接
            + "(emp1.empno = emp2.empno and emp1.ename = 'empename') or\n" // 连接条件：empno 相等且 ename 等于 'empename'
            + "(emp1.ename = emp2.ename and emp2.empno = ?)") // 连接条件：ename 相等且 emp2.empno 等于动态参数
        .consumesPreparedStatement(p -> p.setInt(1, 5)) // 设置动态参数值为 5
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0])\n" // JDBC 投影：选择 empno
            + "    JdbcJoin(condition=[OR(AND(=($0, $2), =($1, 'empename')), AND(=($1, $3), =($2, ?0)))], joinType=[inner])\n" // JDBC 内连接：包含动态参数
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 右侧投影：选择 empno, ename
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n\n") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"ENAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" ON \"t\".\"EMPNO\" = \"t0\".\"EMPNO\" AND \"t\".\"ENAME\" = 'empename' OR \"t\".\"ENAME\" = \"t0\".\"ENAME\" AND \"t0\".\"EMPNO\" = ?"); // 连接条件包含动态参数
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3751">[CALCITE-3751]
   * JDBC adapter wrongly pushes ORDER BY into sub-query</a>.
   * 测试用例：验证 JDBC 适配器不会错误地将 ORDER BY 下推到子查询。
   * 
   * 该测试针对 CALCITE-3751 问题，ORDER BY 被错误地推入子查询。
   * 测试通过执行包含 GROUP BY 和 ORDER BY 的查询来验证修复。
   */
  @Test void testOrderByPlan() { // 测试方法：测试 ORDER BY 计划
    final String sql = "select deptno, job, sum(sal)\n" // SQL 查询：选择 deptno, job 和 sal 的总和
        + "from \"EMP\"\n" // 从 EMP 表
        + "group by deptno, job\n" // 按 deptno 和 job 分组
        + "order by 1, 2"; // 按第 1 列和第 2 列排序
    final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
        + "  JdbcSort(sort0=[$0], sort1=[$1], dir0=[ASC], dir1=[ASC])\n" // JDBC 排序：按第 0 列和第 1 列升序排序
        + "    JdbcProject(DEPTNO=[$1], JOB=[$0], EXPR$2=[$2])\n" // JDBC 投影：选择 deptno, job 和总和
        + "      JdbcAggregate(group=[{2, 7}], EXPR$2=[SUM($5)])\n" // JDBC 聚合：按第 2 列和第 7 列分组，计算第 5 列的总和
        + "        JdbcTableScan(table=[[SCOTT, EMP]])"; // 扫描 SCOTT.EMP 表
    final String sqlHsqldb = "SELECT \"DEPTNO\", \"JOB\", SUM(\"SAL\")\n" // 预期生成的 HSQLDB SQL
        + "FROM \"SCOTT\".\"EMP\"\n" // 从 EMP 表
        + "GROUP BY \"JOB\", \"DEPTNO\"\n" // 按 JOB 和 DEPTNO 分组（注意顺序）
        + "ORDER BY \"DEPTNO\" NULLS LAST, \"JOB\" NULLS LAST"; // 按 DEPTNO 和 JOB 排序，NULL 值排在最后
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .with(CalciteConnectionProperty.TOPDOWN_OPT.camelName(), false) // 禁用自顶向下优化
        .query(sql) // 执行 SQL 查询
        .explainContains(explain) // 验证执行计划包含预期的节点
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql(sqlHsqldb); // 验证生成的 SQL 包含预期的 SQL
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-631">[CALCITE-631]
   * Push theta joins down to JDBC adapter</a>.
   * 测试用例：验证 JDBC 适配器能够下推 Theta 连接（非等值连接）。
   * 
   * 该测试针对 CALCITE-631 功能，支持将非等值连接下推到 JDBC 数据库。
   * 测试通过执行包含 > 和 < 条件的连接查询来验证功能。
   */
  @Test void testNonEquiJoinPlan() { // 测试方法：测试非等值连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select empno, ename, grade\n" // SQL 查询：选择 empno, ename, grade
            + "from scott.emp e inner join scott.salgrade s\n" // emp 表和 salgrade 表内连接
            + "on e.sal > s.losal and e.sal < s.hisal") // 连接条件：e.sal 在 s.losal 和 s.hisal 之间
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], GRADE=[$3])\n" // JDBC 投影：选择 empno, ename, grade
            + "    JdbcJoin(condition=[AND(>($2, $4), <($2, $5))], joinType=[inner])\n" // JDBC 内连接：包含 > 和 < 条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], SAL=[$5])\n" // 左侧投影：选择 empno, ename, sal
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcTableScan(table=[[SCOTT, SALGRADE]])") // 扫描 SCOTT.SALGRADE 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\", \"t\".\"ENAME\", \"SALGRADE\".\"GRADE\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"SAL\"\n" // 子查询：选择 empno, ename, sal
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN \"SCOTT\".\"SALGRADE\" " // 内连接 salgrade 表
            + "ON \"t\".\"SAL\" > \"SALGRADE\".\"LOSAL\" " // 连接条件：sal > losal
            + "AND \"t\".\"SAL\" < \"SALGRADE\".\"HISAL\""); // 连接条件：sal < hisal
  }

  @Test void testNonEquiJoinReverseConditionPlan() { // 测试方法：测试反向条件的非等值连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select empno, ename, grade\n" // SQL 查询：选择 empno, ename, grade
            + "from scott.emp e inner join scott.salgrade s\n" // emp 表和 salgrade 表内连接
            + "on s.losal <= e.sal and s.hisal >= e.sal") // 连接条件：e.sal 在 s.losal 和 s.hisal 之间（反向写法）
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], GRADE=[$3])\n" // JDBC 投影：选择 empno, ename, grade
            + "    JdbcJoin(condition=[AND(<=($4, $2), >=($5, $2))], joinType=[inner])\n" // JDBC 内连接：包含 <= 和 >= 条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], SAL=[$5])\n" // 左侧投影：选择 empno, ename, sal
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcTableScan(table=[[SCOTT, SALGRADE]])") // 扫描 SCOTT.SALGRADE 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\", \"t\".\"ENAME\", \"SALGRADE\".\"GRADE\"\n" // 验证生成的 SQL
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"SAL\"\n" // 子查询：选择 empno, ename, sal
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN \"SCOTT\".\"SALGRADE\" ON \"t\".\"SAL\" >= \"SALGRADE\".\"LOSAL\" " // 连接条件：sal >= losal
            + "AND \"t\".\"SAL\" <= \"SALGRADE\".\"HISAL\""); // 连接条件：sal <= hisal
  }

  @Test void testMixedJoinPlan() { // 测试方法：测试混合连接计划（等值+非等值）
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select e.empno, e.ename, e.empno, e.ename\n" // SQL 查询：选择 empno, ename（重复）
            + "from scott.emp e inner join scott.emp m on\n" // emp 表自连接
            + "e.mgr = m.empno and e.sal > m.sal") // 连接条件：e.mgr = m.empno 且 e.sal > m.sal
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], EMPNO0=[$0], ENAME0=[$1])\n" // JDBC 投影：选择 empno, ename（带别名）
            + "    JdbcJoin(condition=[AND(=($2, $4), >($3, $5))], joinType=[inner])\n" // JDBC 内连接：包含等值和不等值条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], MGR=[$3], SAL=[$5])\n" // 左侧投影：选择 empno, ename, mgr, sal
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], SAL=[$5])\n" // 右侧投影：选择 empno, sal
            + "        JdbcTableScan(table=[[SCOTT, EMP]])") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\", \"t\".\"ENAME\", " // 验证生成的 SQL
            + "\"t\".\"EMPNO\" AS \"EMPNO0\", \"t\".\"ENAME\" AS \"ENAME0\"\n" // 选择列并添加别名
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"MGR\", \"SAL\"\n" // 子查询：选择 empno, ename, mgr, sal
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"SAL\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" " // 别名为 t0
            + "ON \"t\".\"MGR\" = \"t0\".\"EMPNO\" AND \"t\".\"SAL\" > \"t0\".\"SAL\""); // 连接条件包含等值和不等值
  }

  @Test void testMixedJoinWithOrPlan() { // 测试方法：测试带 OR 的混合连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select e.empno, e.ename, e.empno, e.ename\n" // SQL 查询：选择 empno, ename（重复）
            + "from scott.emp e inner join scott.emp m on\n" // emp 表自连接
            + "e.mgr = m.empno and (e.sal > m.sal or m.hiredate > e.hiredate)") // 连接条件：e.mgr = m.empno 且 (e.sal > m.sal 或 m.hiredate > e.hiredate)
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], EMPNO0=[$0], ENAME0=[$1])\n" // JDBC 投影：选择 empno, ename（带别名）
            + "    JdbcJoin(condition=[AND(=($2, $5), OR(>($4, $7), >($6, $3)))], joinType=[inner])\n" // JDBC 内连接：包含 AND 和 OR 条件
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], MGR=[$3], HIREDATE=[$4], SAL=[$5])\n" // 左侧投影：选择 empno, ename, mgr, hiredate, sal
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(EMPNO=[$0], HIREDATE=[$4], SAL=[$5])\n" // 右侧投影：选择 empno, hiredate, sal
            + "        JdbcTableScan(table=[[SCOTT, EMP]])") // 扫描 SCOTT.EMP 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\", \"t\".\"ENAME\", " // 验证生成的 SQL
            + "\"t\".\"EMPNO\" AS \"EMPNO0\", \"t\".\"ENAME\" AS \"ENAME0\"\n" // 选择列并添加别名
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"MGR\", \"HIREDATE\", \"SAL\"\n" // 子查询：选择 empno, ename, mgr, hiredate, sal
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"EMPNO\", \"HIREDATE\", \"SAL\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"EMP\") AS \"t0\" " // 别名为 t0
            + "ON \"t\".\"MGR\" = \"t0\".\"EMPNO\" " // 连接条件：mgr = empno
            + "AND (\"t\".\"SAL\" > \"t0\".\"SAL\" OR \"t\".\"HIREDATE\" < \"t0\".\"HIREDATE\")"); // 连接条件包含 OR
  }


  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6436">[CALCITE-6436]
   * JDBC adapter generates SQL missing parentheses when comparing 3 values with
   * the same precedence like (a=b)=c</a>.
   * 测试用例：验证 JDBC 适配器在比较三个具有相同优先级的值时能正确生成括号。
   * 
   * 该测试针对 CALCITE-6436 问题，当比较三个具有相同优先级的值（如 (a=b)=c）时，
   * 生成的 SQL 缺少必要的括号。测试通过执行包含此类比较的查询来验证修复。
   */
  @Test void testMissingParentheses() { // 测试方法：测试缺失括号的情况
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query("select * from \"sales_fact_1997\" " // SQL 查询：从 sales_fact_1997 表选择所有列
            + "where (\"product_id\" = 1) = ?") // 过滤条件：(product_id = 1) = 动态参数
        .consumesPreparedStatement(p -> p.setBoolean(1, true)) // 设置动态参数值为 true
        .returnsCount(26) // 验证返回 26 行结果
        .planHasSql("SELECT *\nFROM \"foodmart\".\"sales_fact_1997\"\n" // 验证生成的 SQL
            + "WHERE (\"product_id\" = 1) = ?"); // 验证包含正确的括号
  }

  @Test void testJoin3TablesPlan() { // 测试方法：测试三表连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select  empno, ename, dname, grade\n" // SQL 查询：选择 empno, ename, dname, grade
            + "from scott.emp e inner join scott.dept d\n" // emp 表和 dept 表内连接
            + "on e.deptno = d.deptno\n" // 连接条件：e.deptno = d.deptno
            + "inner join scott.salgrade s\n" // 再与 salgrade 表内连接
            + "on e.sal > s.losal and e.sal < s.hisal") // 连接条件：e.sal 在 s.losal 和 s.hisal 之间
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], DNAME=[$5], GRADE=[$6])\n" // JDBC 投影：选择 empno, ename, dname, grade
            + "    JdbcJoin(condition=[AND(>($2, $7), <($2, $8))], joinType=[inner])\n" // JDBC 内连接：emp 和 salgrade 的连接
            + "      JdbcJoin(condition=[=($3, $4)], joinType=[inner])\n" // JDBC 内连接：emp 和 dept 的连接
            + "        JdbcProject(EMPNO=[$0], ENAME=[$1], SAL=[$5], DEPTNO=[$7])\n" // JDBC 投影：选择 empno, ename, sal, deptno
            + "          JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "        JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // JDBC 投影：选择 deptno, dname
            + "          JdbcTableScan(table=[[SCOTT, DEPT]])\n" // 扫描 SCOTT.DEPT 表
            + "      JdbcTableScan(table=[[SCOTT, SALGRADE]])\n") // 扫描 SCOTT.SALGRADE 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\", \"t\".\"ENAME\", " // 验证生成的 SQL
            + "\"t0\".\"DNAME\", \"SALGRADE\".\"GRADE\"\n" // 选择列
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"SAL\", \"DEPTNO\"\n" // 子查询：选择 empno, ename, sal, deptno
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"DEPTNO\", \"DNAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"DEPT\") AS \"t0\" ON \"t\".\"DEPTNO\" = \"t0\".\"DEPTNO\"\n" // 连接条件：deptno = deptno
            + "INNER JOIN \"SCOTT\".\"SALGRADE\" " // 再内连接 salgrade 表
            + "ON \"t\".\"SAL\" > \"SALGRADE\".\"LOSAL\" " // 连接条件：sal > losal
            + "AND \"t\".\"SAL\" < \"SALGRADE\".\"HISAL\""); // 连接条件：sal < hisal
  }

  @Test void testCrossJoinWithJoinKeyPlan() { // 测试方法：测试带连接键的交叉连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select empno, ename, d.deptno, dname\n" // SQL 查询：选择 empno, ename, d.deptno, dname
            + "from scott.emp e,scott.dept d\n" // emp 表和 dept 表交叉连接（逗号语法）
            + "where e.deptno = d.deptno") // WHERE 子句：e.deptno = d.deptno（实际是内连接）
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$3], DNAME=[$4])\n" // JDBC 投影：选择 empno, ename, deptno, dname
            + "    JdbcJoin(condition=[=($2, $3)], joinType=[inner])\n" // JDBC 内连接：条件为 $2 = $3
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // 左侧投影：选择 empno, ename, deptno
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // 右侧投影：选择 deptno, dname
            + "        JdbcTableScan(table=[[SCOTT, DEPT]])") // 扫描 SCOTT.DEPT 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t\".\"EMPNO\", \"t\".\"ENAME\", " // 验证生成的 SQL
            + "\"t0\".\"DEPTNO\", \"t0\".\"DNAME\"\n" // 选择列
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"DEPTNO\"\n" // 子查询：选择 empno, ename, deptno
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 别名为 t
            + "INNER JOIN (SELECT \"DEPTNO\", \"DNAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"DEPT\") AS \"t0\" ON \"t\".\"DEPTNO\" = \"t0\".\"DEPTNO\""); // 连接条件
  }

  @Test void testCartesianJoinWithoutKeyPlan() { // 测试方法：测试无键的笛卡尔连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select empno, ename, d.deptno, dname\n" // SQL 查询：选择 empno, ename, d.deptno, dname
            + "from scott.emp e,scott.dept d") // emp 表和 dept 表交叉连接（无 WHERE 子句）
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcJoin(condition=[true], joinType=[inner])\n" // JDBC 内连接：条件为 true（笛卡尔积）
            + "    JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
            + "      JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "    JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // 右侧投影：选择 deptno, dname
            + "      JdbcTableScan(table=[[SCOTT, DEPT]])") // 扫描 SCOTT.DEPT 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB); // 仅在 HSQLDB 数据库上启用
  }

  @Test void testCrossJoinWithJoinKeyAndFilterPlan() { // 测试方法：测试带连接键和过滤的交叉连接计划
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select empno, ename, d.deptno, dname\n" // SQL 查询：选择 empno, ename, d.deptno, dname
            + "from scott.emp e,scott.dept d\n" // emp 表和 dept 表交叉连接
            + "where e.deptno = d.deptno\n" // WHERE 子句：e.deptno = d.deptno
            + "and e.deptno=20") // AND 条件：e.deptno = 20
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$3], DNAME=[$4])\n" // JDBC 投影：选择 empno, ename, deptno, dname
            + "    JdbcJoin(condition=[=($2, $3)], joinType=[inner])\n" // JDBC 内连接：条件为 $2 = $3
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // 左侧投影：选择 empno, ename, deptno
            + "        JdbcFilter(condition=[=(CAST($7):INTEGER, 20)])\n" // JDBC 过滤：deptno 转换为整数后 = 20
            + "          JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
            + "      JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // 右侧投影：选择 deptno, dname
            + "        JdbcTableScan(table=[[SCOTT, DEPT]])") // 扫描 SCOTT.DEPT 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT \"t0\".\"EMPNO\", \"t0\".\"ENAME\", " // 验证生成的 SQL
            + "\"t1\".\"DEPTNO\", \"t1\".\"DNAME\"\n" // 选择列
            + "FROM (SELECT \"EMPNO\", \"ENAME\", \"DEPTNO\"\n" // 子查询：选择 empno, ename, deptno
            + "FROM \"SCOTT\".\"EMP\"\n" // 从 emp 表
            + "WHERE CAST(\"DEPTNO\" AS INTEGER) = 20) AS \"t0\"\n" // 过滤条件：deptno = 20
            + "INNER JOIN (SELECT \"DEPTNO\", \"DNAME\"\n" // 内连接子查询
            + "FROM \"SCOTT\".\"DEPT\") AS \"t1\" ON \"t0\".\"DEPTNO\" = \"t1\".\"DEPTNO\""); // 连接条件
  }

  @Test void testJoinConditionAlwaysTruePushDown() { // 测试方法：测试连接条件始终为 true 的下推
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("select empno, ename, d.deptno, dname\n" // SQL 查询：选择 empno, ename, d.deptno, dname
                + "from scott.emp e,scott.dept d\n" // emp 表和 dept 表交叉连接
                + "where true") // WHERE 子句：true（实际是笛卡尔积）
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
                + "  JdbcJoin(condition=[true], joinType=[inner])\n" // JDBC 内连接：条件为 true（笛卡尔积）
                + "    JdbcProject(EMPNO=[$0], ENAME=[$1])\n" // 左侧投影：选择 empno, ename
                + "      JdbcTableScan(table=[[SCOTT, EMP]])\n" // 扫描 SCOTT.EMP 表
                + "    JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // 右侧投影：选择 deptno, dname
                + "      JdbcTableScan(table=[[SCOTT, DEPT]])") // 扫描 SCOTT.DEPT 表
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .planHasSql("SELECT *\n" // 验证生成的 SQL
                + "FROM (SELECT \"EMPNO\", \"ENAME\"\n" // 子查询：选择 empno, ename
                + "FROM \"SCOTT\".\"EMP\") AS \"t\",\n" // 别名为 t
                + "(SELECT \"DEPTNO\", \"DNAME\"\n" // 子查询：选择 deptno, dname
                + "FROM \"SCOTT\".\"DEPT\") AS \"t0\""); // 别名为 t0
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-893">[CALCITE-893]
   * Theta join in JdbcAdapter</a>.
   * 测试用例：验证 JDBC 适配器中的 Theta 连接（非等值连接）。
   * 
   * 该测试针对 CALCITE-893 问题，验证 JDBC 适配器能够正确处理
   * 包含 OR 条件的 Theta 连接。
   */
  @Test void testJoinPlan() { // 测试方法：测试连接计划
    final String sql = "SELECT T1.\"brand_name\"\n" // SQL 查询：选择 T1.brand_name
        + "FROM \"foodmart\".\"product\" AS T1\n" // 从 product 表（别名 T1）
        + " INNER JOIN \"foodmart\".\"product_class\" AS T2\n" // 内连接 product_class 表（别名 T2）
        + " ON T1.\"product_class_id\" = T2.\"product_class_id\"\n" // 连接条件：product_class_id 相等
        + "WHERE T2.\"product_department\" = 'Frozen Foods'\n" // 过滤条件：product_department = 'Frozen Foods'
        + " OR T2.\"product_department\" = 'Baking Goods'\n" // 或 product_department = 'Baking Goods'
        + " AND T1.\"brand_name\" <> 'King'"; // 且 brand_name 不等于 'King'
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query(sql).runs() // 执行查询
        .returnsCount(275); // 验证返回 275 行结果
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1372">[CALCITE-1372]
   * JDBC adapter generates SQL with wrong field names</a>.
   * 测试用例：验证 JDBC 适配器生成的 SQL 字段名正确。
   * 
   * 该测试针对 CALCITE-1372 问题，JDBC 适配器生成的 SQL 包含错误的字段名。
   * 测试通过执行 LEFT JOIN 查询来验证修复。
   */
  @Test void testJoinPlan2() { // 测试方法：测试连接计划2
    final String sql = "SELECT v1.deptno, v2.deptno\n" // SQL 查询：选择 v1.deptno, v2.deptno
        + "FROM Scott.dept v1 LEFT JOIN Scott.emp v2 ON v1.deptno = v2.deptno\n" // dept 表左连接 emp 表
        + "WHERE v2.job LIKE 'PRESIDENT'"; // 过滤条件：v2.job LIKE 'PRESIDENT'
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .with(Lex.MYSQL) // 使用 MySQL 词法规则
        .query(sql).runs() // 执行查询
        .returnsCount(1); // 验证返回 1 行结果
  }

  @Test void testJoinCartesian() { // 测试方法：测试笛卡尔连接
    final String sql = "SELECT *\n" // SQL 查询：选择所有列
        + "FROM Scott.dept, Scott.emp"; // dept 表和 emp 表交叉连接
    CalciteAssert.model(JdbcTest.SCOTT_MODEL).query(sql).returnsCount(56); // 执行查询并验证返回 56 行结果
  }

  @Test void testJoinCartesianCount() { // 测试方法：测试笛卡尔连接计数
    final String sql = "SELECT count(*) as c\n" // SQL 查询：计算行数
        + "FROM Scott.dept, Scott.emp"; // dept 表和 emp 表交叉连接
    CalciteAssert.model(JdbcTest.SCOTT_MODEL).query(sql).returns("C=56\n"); // 执行查询并验证结果为 C=56
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1382">[CALCITE-1382]
   * ClassCastException in JDBC adapter</a>.
   * 测试用例：验证 JDBC 适配器中的类转换异常已修复。
   * 
   * 该测试针对 CALCITE-1382 问题，JDBC 适配器中存在类转换异常。
   * 测试通过执行复杂的连接和聚合查询来验证修复。
   */
  @Test void testJoinPlan3() { // 测试方法：测试连接计划3
    final String sql = "SELECT count(*) AS c FROM (\n" // SQL 查询：计算子查询的行数
        + "  SELECT count(emp.empno) `Count Emp`,\n" // 子查询：计算 emp.empno 的数量
        + "      dept.dname `Department Name`\n" // 选择 dept.dname
        + "  FROM emp emp\n" // 从 emp 表
        + "  JOIN dept dept ON emp.deptno = dept.deptno\n" // 内连接 dept 表
        + "  JOIN salgrade salgrade ON emp.comm = salgrade.hisal\n" // 内连接 salgrade 表
        + "  WHERE dept.dname LIKE '%A%'\n" // 过滤条件：dname 包含 'A'
        + "  GROUP BY emp.deptno, dept.dname)"; // 按 emp.deptno, dept.dname 分组
    final String expected = "c=1\n"; // 预期结果
    final String expectedSql = "SELECT COUNT(*) AS \"c\"\n" // 预期生成的 SQL
        + "FROM (SELECT \"t0\".\"DEPTNO\", \"t2\".\"DNAME\"\n" // 外层子查询
        + "FROM (SELECT \"HISAL\"\n" // 内层子查询
        + "FROM \"SCOTT\".\"SALGRADE\") AS \"t\"\n" // 从 salgrade 表选择 hisal
        + "INNER JOIN ((SELECT \"COMM\", \"DEPTNO\"\n" // 内连接
        + "FROM \"SCOTT\".\"EMP\") AS \"t0\" " // 从 emp 表选择 comm, deptno
        + "INNER JOIN (SELECT \"DEPTNO\", \"DNAME\"\n" // 内连接
        + "FROM \"SCOTT\".\"DEPT\"\n" // 从 dept 表
        + "WHERE \"DNAME\" LIKE '%A%') AS \"t2\" " // 过滤条件：dname 包含 'A'
        + "ON \"t0\".\"DEPTNO\" = \"t2\".\"DEPTNO\") " // 连接条件：deptno = deptno
        + "ON \"t\".\"HISAL\" = \"t0\".\"COMM\"\n" // 连接条件：hisal = comm
        + "GROUP BY \"t0\".\"DEPTNO\", \"t2\".\"DNAME\") AS \"t3\""; // 按 deptno, dname 分组
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .with(Lex.MYSQL) // 使用 MySQL 词法规则
        .query(sql) // 执行查询
        .returns(expected) // 验证返回预期结果
        .planHasSql(expectedSql); // 验证生成的 SQL 包含预期的 SQL
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-657">[CALCITE-657]
   * NullPointerException when executing JdbcAggregate implement method</a>.
   * 测试用例：验证执行 JdbcAggregate 实现方法时的空指针异常已修复。
   * 
   * 该测试针对 CALCITE-657 问题，执行 JdbcAggregate 实现方法时抛出空指针异常。
   * 测试通过创建临时表并执行聚合查询来验证修复。
   */
  @Test void testJdbcAggregate() throws Exception { // 测试方法：测试 JDBC 聚合
    final String url = MultiJdbcSchemaJoinTest.TempDb.INSTANCE.getUrl(); // 获取临时数据库 URL
    Connection baseConnection = DriverManager.getConnection(url); // 获取基础数据库连接
    Statement baseStmt = baseConnection.createStatement(); // 创建语句对象
    baseStmt.execute("CREATE TABLE T2 (\n" // 创建表 T2
            + "ID INTEGER,\n" // ID 列：整数类型
            + "VALS INTEGER)"); // VALS 列：整数类型
    baseStmt.execute("INSERT INTO T2 VALUES (1, 1)"); // 插入数据：(1, 1)
    baseStmt.execute("INSERT INTO T2 VALUES (2, null)"); // 插入数据：(2, null)
    baseStmt.close(); // 关闭语句对象
    baseConnection.commit(); // 提交事务

    Properties info = new Properties(); // 创建属性对象
    info.put("model", // 设置模型配置
        "inline:"
            + "{\n"
            + "  version: '1.0',\n" // 版本：1.0
            + "  defaultSchema: 'BASEJDBC',\n" // 默认模式：BASEJDBC
            + "  schemas: [\n"
            + "     {\n"
            + "       type: 'jdbc',\n" // 类型：jdbc
            + "       name: 'BASEJDBC',\n" // 名称：BASEJDBC
            + "       jdbcDriver: '" + jdbcDriver.class.getName() + "',\n" // JDBC 驱动：hsqldb.jdbcDriver
            + "       jdbcUrl: '" + url + "',\n" // JDBC URL
            + "       jdbcCatalog: null,\n" // JDBC Catalog：null
            + "       jdbcSchema: null\n" // JDBC Schema：null
            + "     }\n"
            + "  ]\n"
            + "}");

    final Connection calciteConnection = // 创建 Calcite 连接
        DriverManager.getConnection("jdbc:calcite:", info); // 使用 jdbc:calcite: 协议
    ResultSet rs = calciteConnection // 执行查询
        .prepareStatement("select 10 * count(ID) from t2").executeQuery(); // SQL：计算 ID 数量的 10 倍

    assertThat(rs.next(), is(true)); // 验证有下一行
    assertThat(rs.getObject(1), equalTo(20L)); // 验证第一列的值为 20L
    assertThat(rs.next(), is(false)); // 验证没有更多行

    rs.close(); // 关闭结果集
    calciteConnection.close(); // 关闭 Calcite 连接
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2206">[CALCITE-2206]
   * JDBC adapter incorrectly pushes windowed aggregates down to HSQLDB</a>.
   * 测试用例：验证 JDBC 适配器不会错误地将窗口聚合下推到 HSQLDB。
   * 
   * 该测试针对 CALCITE-2206 问题，JDBC 适配器错误地将窗口聚合下推到不支持
   * 窗口函数的数据库（如 HSQLDB）。测试通过执行窗口函数查询来验证修复。
   */
  @Test void testOverNonSupportedDialect() { // 测试方法：测试不支持窗口函数的方言
    final String sql = "select \"store_id\", \"account_id\", \"exp_date\",\n" // SQL 查询：选择多个列
        + " \"time_id\", \"category_id\", \"currency_id\", \"amount\",\n" // 选择列
        + " last_value(\"time_id\") over () as \"last_version\"\n" // 窗口函数：计算 last_value
        + "from \"expense_fact\""; // 从 expense_fact 表
    final String explain = "PLAN=" // 预期的执行计划
        + "EnumerableWindow(window#0=[window(aggs [LAST_VALUE($3)])])\n" // 可枚举窗口节点：计算 LAST_VALUE
        + "  JdbcToEnumerableConverter\n" // JDBC 到可枚举转换器
        + "    JdbcTableScan(table=[[foodmart, expense_fact]])\n"; // JDBC 表扫描
    CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .query(sql) // 执行查询
        .explainContains(explain) // 验证执行计划包含预期的节点
        .runs() // 执行查询
        .planHasSql("SELECT *\n" // 验证生成的 SQL
            + "FROM \"foodmart\".\"expense_fact\""); // 从 expense_fact 表选择所有列（不下推窗口函数）
  }

  @Test void testTablesNoCatalogSchema() { // 测试方法：测试无 Catalog 和 Schema 的表
    // Switch from "FOODMART" user, whose default schema is 'foodmart',
    // to "sa", whose default schema is the root, and therefore cannot
    // see the table unless directed to look in a particular schema.
    // 从 "FOODMART" 用户（默认模式是 'foodmart'）切换到 "sa" 用户
    // （默认模式是根目录），因此除非指定查看特定模式，否则无法看到表。
    final String model = // 创建模型字符串
        FoodmartSchema.FOODMART_MODEL // 基于 FoodMart 模型
            .replace("jdbcUser: 'FOODMART'", "jdbcUser: 'sa'") // 替换用户为 'sa'
            .replace("jdbcPassword: 'FOODMART'", "jdbcPassword: ''") // 替换密码为空
            .replace("jdbcCatalog: 'foodmart'", "jdbcCatalog: null") // 替换 Catalog 为 null
            .replace("jdbcSchema: 'foodmart'", "jdbcSchema: null"); // 替换 Schema 为 null
    // Since Calcite uses PostgreSQL JDBC driver version >= 4.1,
    // catalog/schema can be retrieved from JDBC connection and
    // this test succeeds
    // 由于 Calcite 使用 PostgreSQL JDBC 驱动版本 >= 4.1，
    // 可以从 JDBC 连接获取 catalog/schema，因此测试成功
    CalciteAssert.model(model) // 使用模型
        // Calcite uses PostgreSQL JDBC driver version >= 4.1
        // Calcite 使用 PostgreSQL JDBC 驱动版本 >= 4.1
        .enable(CalciteAssert.DB == DatabaseInstance.POSTGRESQL) // 仅在 PostgreSQL 数据库上启用
        .query("select \"store_id\", \"account_id\", \"exp_date\"," // SQL 查询：选择多个列
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " last_value(\"time_id\") over ()" // 窗口函数：计算 last_value
            + " as \"last_version\" from \"expense_fact\"") // 从 expense_fact 表
        .runs(); // 执行查询
    // Since Calcite uses HSQLDB JDBC driver version < 4.1,
    // catalog/schema cannot be retrieved from JDBC connection and
    // this test fails
    // 由于 Calcite 使用 HSQLDB JDBC 驱动版本 < 4.1，
    // 无法从 JDBC 连接获取 catalog/schema，因此测试失败
    CalciteAssert.model(model) // 使用模型
        .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .query("select \"store_id\", \"account_id\", \"exp_date\"," // SQL 查询：选择多个列
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " last_value(\"time_id\") over ()" // 窗口函数：计算 last_value
            + " as \"last_version\" from \"expense_fact\"") // 从 expense_fact 表
        .throws_("'expense_fact' not found"); // 验证抛出异常：'expense_fact' not found
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1506">[CALCITE-1506]
   * Push OVER Clause to underlying SQL via JDBC adapter</a>.
   *
   * <p>Test runs only on Postgres; the default database, Hsqldb, does not
   * support OVER.
   * 测试用例：验证 JDBC 适配器能够将 OVER 子句下推到底层 SQL。
   * 
   * 测试仅在 PostgreSQL 上运行；默认数据库 HSQLDB 不支持 OVER。
   */
  @Test void testOverDefault() { // 测试方法：测试默认 OVER 子句
    CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.POSTGRESQL) // 仅在 PostgreSQL 数据库上启用
        .query("select \"store_id\", \"account_id\", \"exp_date\"," // SQL 查询：选择多个列
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " last_value(\"time_id\") over ()" // 窗口函数：计算 last_value
            + " as \"last_version\" from \"expense_fact\"") // 从 expense_fact 表
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(store_id=[$0], account_id=[$1], exp_date=[$2], " // JDBC 投影：选择列
            + "time_id=[$3], category_id=[$4], currency_id=[$5], amount=[$6],"
            + " last_version=[LAST_VALUE($3) OVER (RANGE BETWEEN UNBOUNDED" // 窗口函数：LAST_VALUE
            + " PRECEDING AND UNBOUNDED FOLLOWING)])\n" // 窗口范围：从无界前导到无界后继
            + "    JdbcTableScan(table=[[foodmart, expense_fact]])\n") // JDBC 表扫描
        .runs() // 执行查询
        .planHasSql("SELECT \"store_id\", \"account_id\", \"exp_date\"," // 验证生成的 SQL
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " LAST_VALUE(\"time_id\") OVER (RANGE BETWEEN UNBOUNDED" // 窗口函数：LAST_VALUE
            + " PRECEDING AND UNBOUNDED FOLLOWING) AS \"last_version\"\n" // 窗口范围
            + "FROM \"foodmart\".\"expense_fact\""); // 从 expense_fact 表
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2305">[CALCITE-2305]
   * JDBC adapter generates invalid casts on PostgreSQL, because PostgreSQL does
   * not have TINYINT and DOUBLE types</a>.
   * 测试用例：验证 JDBC 适配器在 PostgreSQL 上生成有效的类型转换。
   * 
   * 该测试针对 CALCITE-2305 问题，JDBC 适配器在 PostgreSQL 上生成无效的类型转换，
   * 因为 PostgreSQL 没有 TINYINT 和 DOUBLE 类型。测试通过执行 CAST 查询来验证修复。
   */
  @Test void testCast() { // 测试方法：测试类型转换
    CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.POSTGRESQL) // 仅在 PostgreSQL 数据库上启用
        .query("select cast(\"store_id\" as TINYINT)," // SQL 查询：将 store_id 转换为 TINYINT
            + "cast(\"store_id\" as DOUBLE)" // 将 store_id 转换为 DOUBLE
            + " from \"expense_fact\"") // 从 expense_fact 表
        .runs() // 执行查询
        .planHasSql("SELECT CAST(\"store_id\" AS SMALLINT)," // 验证生成的 SQL：TINYINT 转换为 SMALLINT
            + " CAST(\"store_id\" AS DOUBLE PRECISION)\n" // DOUBLE 转换为 DOUBLE PRECISION
            + "FROM \"foodmart\".\"expense_fact\""); // 从 expense_fact 表
  }

  @Test void testOverRowsBetweenBoundFollowingAndFollowing() { // 测试方法：测试 OVER ROWS BETWEEN ... FOLLOWING AND ... FOLLOWING
    CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.POSTGRESQL) // 仅在 PostgreSQL 数据库上启用
        .query("select \"store_id\", \"account_id\", \"exp_date\"," // SQL 查询：选择多个列
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " last_value(\"time_id\") over (partition by \"account_id\"" // 窗口函数：按 account_id 分区
            + " order by \"time_id\" rows between 1 following and 10 following)" // 窗口范围：从 1 行后到 10 行后
            + " as \"last_version\" from \"expense_fact\"") // 从 expense_fact 表
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(store_id=[$0], account_id=[$1], exp_date=[$2], " // JDBC 投影：选择列
            + "time_id=[$3], category_id=[$4], currency_id=[$5], amount=[$6],"
            + " last_version=[LAST_VALUE($3) OVER (PARTITION BY $1" // 窗口函数：LAST_VALUE
            + " ORDER BY $3 ROWS BETWEEN 1 FOLLOWING AND 10 FOLLOWING)])\n" // 窗口范围
            + "    JdbcTableScan(table=[[foodmart, expense_fact]])\n") // JDBC 表扫描
        .runs() // 执行查询
        .planHasSql("SELECT \"store_id\", \"account_id\", \"exp_date\"," // 验证生成的 SQL
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " LAST_VALUE(\"time_id\") OVER (PARTITION BY \"account_id\"" // 窗口函数：LAST_VALUE
            + " ORDER BY \"time_id\" ROWS BETWEEN 1 FOLLOWING" // 窗口范围
            + " AND 10 FOLLOWING) AS \"last_version\"\n" // 窗口范围
            + "FROM \"foodmart\".\"expense_fact\""); // 从 expense_fact 表
  }

  @Test void testOverRowsBetweenBoundPrecedingAndCurrent() { // 测试方法：测试 OVER ROWS BETWEEN ... PRECEDING AND CURRENT ROW
    CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.POSTGRESQL) // 仅在 PostgreSQL 数据库上启用
        .query("select \"store_id\", \"account_id\", \"exp_date\"," // SQL 查询：选择多个列
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " last_value(\"time_id\") over (partition by \"account_id\"" // 窗口函数：按 account_id 分区
            + " order by \"time_id\" rows between 3 preceding and current row)" // 窗口范围：从 3 行前到当前行
            + " as \"last_version\" from \"expense_fact\"") // 从 expense_fact 表
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(store_id=[$0], account_id=[$1], exp_date=[$2], " // JDBC 投影：选择列
            + "time_id=[$3], category_id=[$4], currency_id=[$5], amount=[$6],"
            + " last_version=[LAST_VALUE($3) OVER (PARTITION BY $1" // 窗口函数：LAST_VALUE
            + " ORDER BY $3 ROWS BETWEEN 3 PRECEDING AND CURRENT ROW)])\n" // 窗口范围
            + "    JdbcTableScan(table=[[foodmart, expense_fact]])\n") // JDBC 表扫描
        .runs() // 执行查询
        .planHasSql("SELECT \"store_id\", \"account_id\", \"exp_date\"," // 验证生成的 SQL
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " LAST_VALUE(\"time_id\") OVER (PARTITION BY \"account_id\"" // 窗口函数：LAST_VALUE
            + " ORDER BY \"time_id\" ROWS BETWEEN 3 PRECEDING" // 窗口范围
            + " AND CURRENT ROW) AS \"last_version\"\n" // 窗口范围
            + "FROM \"foodmart\".\"expense_fact\""); // 从 expense_fact 表
  }

  @Test void testOverDisallowPartial() { // 测试方法：测试 OVER DISALLOW PARTIAL
    CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.POSTGRESQL) // 仅在 PostgreSQL 数据库上启用
        .query("select \"store_id\", \"account_id\", \"exp_date\"," // SQL 查询：选择多个列
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " last_value(\"time_id\") over (partition by \"account_id\"" // 窗口函数：按 account_id 分区
            + " order by \"time_id\" rows 3 preceding disallow partial)" // 窗口范围：3 行前，不允许部分结果
            + " as \"last_version\" from \"expense_fact\"") // 从 expense_fact 表
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(store_id=[$0], account_id=[$1], exp_date=[$2]," // JDBC 投影：选择列
            + " time_id=[$3], category_id=[$4], currency_id=[$5],"
            + " amount=[$6], last_version=[CASE(>=(COUNT() OVER" // CASE 表达式：根据条件选择值
            + " (PARTITION BY $1 ORDER BY $3 ROWS BETWEEN 3 PRECEDING AND" // 窗口函数：COUNT
            + " CURRENT ROW), 2), LAST_VALUE($3) OVER (PARTITION BY $1" // 条件：COUNT >= 2 时返回 LAST_VALUE
            + " ORDER BY $3 ROWS BETWEEN 3 PRECEDING AND CURRENT ROW)," // 窗口函数：LAST_VALUE
            + " null)])\n    JdbcTableScan(table=[[foodmart," // 否则返回 null
            + " expense_fact]])\n") // JDBC 表扫描
        .runs() // 执行查询
        .planHasSql("SELECT \"store_id\", \"account_id\", \"exp_date\"," // 验证生成的 SQL
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " CASE WHEN (COUNT(*) OVER (PARTITION BY \"account_id\"" // CASE 表达式
            + " ORDER BY \"time_id\" ROWS BETWEEN 3 PRECEDING" // 窗口函数：COUNT
            + " AND CURRENT ROW)) >= 2 THEN LAST_VALUE(\"time_id\")" // 条件：COUNT >= 2 时返回 LAST_VALUE
            + " OVER (PARTITION BY \"account_id\" ORDER BY \"time_id\"" // 窗口函数：LAST_VALUE
            + " ROWS BETWEEN 3 PRECEDING AND CURRENT ROW)" // 窗口范围
            + " ELSE NULL END AS \"last_version\"\n" // 否则返回 null
            + "FROM \"foodmart\".\"expense_fact\""); // 从 expense_fact 表
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6346">[CALCITE-6346]
   * JdbcAdapter: Cast for dynamic filter arguments is lost</a>.
   * 测试用例：验证 JDBC 适配器中动态参数的 CAST 不会丢失。
   * 
   * 该测试针对 CALCITE-6346 问题，动态参数的 CAST 在下推时丢失。
   * 测试通过执行包含动态参数 CAST 的查询来验证修复。
   */
  @Test void testCastDynamic() { // 测试方法：测试动态参数的 CAST
    CalciteAssert.that() // 创建断言
        .with(CalciteAssert.Config.FOODMART_CLONE) // 使用 FoodMart 克隆配置
        .query("SELECT * FROM \"foodmart\".\"sales_fact_1997\"" // SQL 查询：从 sales_fact_1997 表选择所有列
            + " WHERE cast (? as varchar(10)) = cast(? as varchar(10))") // 过滤条件：两个动态参数都转换为 varchar(10) 后比较
        .planHasSql("SELECT *\n" // 验证生成的 SQL
            + "FROM \"foodmart\".\"sales_fact_1997\"\n" // 从 sales_fact_1997 表
            + "WHERE CAST(? AS VARCHAR(10)) = CAST(? AS VARCHAR(10))") // 过滤条件包含 CAST
        .consumesPreparedStatement(p -> { // 消费预处理语句
          p.setInt(1, 10); // 设置第一个参数为 10
          p.setLong(2, 10); // 设置第二个参数为 10
        })
        .runs(); // 执行查询
  }

  @Test void testLastValueOver() { // 测试方法：测试 LAST_VALUE OVER
    CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.POSTGRESQL) // 仅在 PostgreSQL 数据库上启用
        .query("select \"store_id\", \"account_id\", \"exp_date\"," // SQL 查询：选择多个列
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " last_value(\"time_id\") over (partition by \"account_id\"" // 窗口函数：按 account_id 分区
            + " order by \"time_id\") as \"last_version\"" // 按 time_id 排序，计算 LAST_VALUE
            + " from \"expense_fact\"") // 从 expense_fact 表
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(store_id=[$0], account_id=[$1], exp_date=[$2]," // JDBC 投影：选择列
            + " time_id=[$3], category_id=[$4], currency_id=[$5], amount=[$6],"
            + " last_version=[LAST_VALUE($3) OVER (PARTITION BY $1 ORDER BY $3" // 窗口函数：LAST_VALUE
            + " RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)])\n" // 窗口范围：从无界前导到当前行
            + "    JdbcTableScan(table=[[foodmart, expense_fact]])\n") // JDBC 表扫描
        .runs() // 执行查询
        .planHasSql("SELECT \"store_id\", \"account_id\", \"exp_date\"," // 验证生成的 SQL
            + " \"time_id\", \"category_id\", \"currency_id\", \"amount\","
            + " LAST_VALUE(\"time_id\") OVER (PARTITION BY \"account_id\"" // 窗口函数：LAST_VALUE
            + " ORDER BY \"time_id\" RANGE BETWEEN UNBOUNDED PRECEDING AND" // 窗口范围
            + " CURRENT ROW) AS \"last_version\"" // 窗口范围
            + "\nFROM \"foodmart\".\"expense_fact\""); // 从 expense_fact 表
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-259">[CALCITE-259]
   * Using sub-queries in CASE statement against JDBC tables generates invalid
   * Oracle SQL</a>.
   * 测试用例：验证在 JDBC 表的 CASE 语句中使用子查询不会生成无效的 Oracle SQL。
   * 
   * 该测试针对 CALCITE-259 问题，在 JDBC 表的 CASE 语句中使用子查询时
   * 生成无效的 Oracle SQL。测试通过执行包含子查询的 CASE 语句来验证修复。
   */
  @Test void testSubQueryWithSingleValue() { // 测试方法：测试包含单值的子查询
    final String expected; // 预期错误消息
    switch (CalciteAssert.DB) { // 根据数据库类型选择预期错误消息
    case MYSQL: // MySQL 数据库
      expected = "Sub" // 预期错误消息
          + "query returns more than 1 row"; // 子查询返回多行
      break;
    default: // 其他数据库
      expected = "more than one value in agg SINGLE_VALUE"; // 聚合 SINGLE_VALUE 中有多个值
    }
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query("SELECT \"full_name\" FROM \"employee\" WHERE " // SQL 查询：从 employee 表选择 full_name
                + "\"employee_id\" = (SELECT \"employee_id\" FROM \"salary\")") // 过滤条件：employee_id 等于子查询结果
        .explainContains("SINGLE_VALUE") // 验证执行计划包含 SINGLE_VALUE
        .throws_(expected); // 验证抛出预期的异常
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-865">[CALCITE-865]
   * Unknown table type causes NullPointerException in JdbcSchema</a>. The issue
   * occurred because of the "SYSTEM_INDEX" table type when run against
   * PostgreSQL.
   * 测试用例：验证未知表类型不会导致 JdbcSchema 中的空指针异常。
   * 
   * 该测试针对 CALCITE-865 问题，未知表类型（如 PostgreSQL 的 "SYSTEM_INDEX"）
   * 导致 JdbcSchema 抛出空指针异常。测试通过获取表元数据来验证修复。
   */
  @Test void testMetadataTables() throws Exception { // 测试方法：测试元数据表
    // The troublesome tables occur in PostgreSQL's system schema.
    // 有问题的表出现在 PostgreSQL 的系统模式中。
    final String model = // 创建模型字符串
        FoodmartSchema.FOODMART_MODEL.replace("jdbcSchema: 'foodmart'", // 替换 jdbcSchema
            "jdbcSchema: null"); // 设置为 null
    CalciteAssert.model( // 使用模型
        model)
        .doWithConnection(connection -> { // 使用连接执行操作
          try {
            final ResultSet resultSet = // 获取表元数据
                connection.getMetaData().getTables(null, null, "%", null); // 获取所有表
            assertFalse(CalciteAssert.toString(resultSet).isEmpty()); // 验证结果不为空
          } catch (SQLException e) { // 捕获 SQL 异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        });
  }

  @Test void testMetadataFunctions() { // 测试方法：测试元数据函数
    final String model = "" // 创建模型字符串
        + "{\n"
        + "  version: '1.0',\n" // 版本：1.0
        + "   schemas: [\n"
        + "     {\n"
        + "       name: 'adhoc',\n" // 模式名称：adhoc
        + "       functions: [\n" // 函数列表
        + "         {\n"
        + "           name: 'MY_STR',\n" // 函数名称：MY_STR
        + "           className: '" + Smalls.MyToStringFunction.class.getName() + "'\n" // 函数类：Smalls.MyToStringFunction
        + "         },\n"
        + "         {\n"
        + "           name: 'FIBONACCI_TABLE',\n" // 函数名称：FIBONACCI_TABLE
        + "           className: '" + Smalls.class.getName() + "',\n" // 函数类：Smalls
        + "           methodName: 'fibonacciTable'\n" // 方法名：fibonacciTable
        + "         }\n"
        + "       ],\n"
        + "       materializations: [\n" // 物化视图列表
        + "         {\n"
        + "           table: 'TEST_VIEW',\n" // 表名：TEST_VIEW
        + "           sql: 'SELECT 1'\n" // SQL：SELECT 1
        + "         }\n"
        + "       ]\n"
        + "     }\n"
        + "   ]\n"
        + "}";
    CalciteAssert.model(model) // 使用模型
        .withDefaultSchema("adhoc") // 设置默认模式为 adhoc
        .metaData(connection -> { // 获取元数据
          try {
            return connection.getMetaData().getFunctions(null, "adhoc", "%"); // 获取 adhoc 模式的所有函数
          } catch (SQLException e) { // 捕获 SQL 异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        })
        .returns("" // 验证返回结果
            + "FUNCTION_CAT=null; FUNCTION_SCHEM=adhoc; FUNCTION_NAME=FIBONACCI_TABLE; REMARKS=null; FUNCTION_TYPE=0; SPECIFIC_NAME=FIBONACCI_TABLE\n" // FIBONACCI_TABLE 函数信息
            + "FUNCTION_CAT=null; FUNCTION_SCHEM=adhoc; FUNCTION_NAME=MY_STR; REMARKS=null; FUNCTION_TYPE=0; SPECIFIC_NAME=MY_STR\n"); // MY_STR 函数信息
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-666">[CALCITE-666]
   * Anti-semi-joins against JDBC adapter give wrong results</a>.
   * 测试用例：验证 JDBC 适配器的反半连接（anti-semi-joins）返回正确结果。
   * 
   * 该测试针对 CALCITE-666 问题，JDBC 适配器的反半连接返回错误结果。
   * 测试通过执行包含子查询的查询来验证修复。
   */
  @Test void testScalarSubQuery() { // 测试方法：测试标量子查询
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("SELECT COUNT(empno) AS cEmpNo FROM \"SCOTT\".\"EMP\" " // SQL 查询：计算 empno 的数量
            + "WHERE DEPTNO <> (SELECT * FROM (VALUES 1))") // 过滤条件：deptno 不等于子查询结果（1）
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .returns("CEMPNO=14\n"); // 验证返回结果

    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("SELECT ename FROM \"SCOTT\".\"EMP\" " // SQL 查询：选择 ename
            + "WHERE DEPTNO = (SELECT deptno FROM \"SCOTT\".\"DEPT\" " // 过滤条件：deptno 等于子查询结果
            + "WHERE dname = 'ACCOUNTING')") // 子查询：dname = 'ACCOUNTING'
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .returns("ENAME=CLARK\nENAME=KING\nENAME=MILLER\n"); // 验证返回结果

    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("SELECT COUNT(ename) AS cEname FROM \"SCOTT\".\"EMP\" " // SQL 查询：计算 ename 的数量
            + "WHERE DEPTNO > (SELECT deptno FROM \"SCOTT\".\"DEPT\" " // 过滤条件：deptno 大于子查询结果
            + "WHERE dname = 'ACCOUNTING')") // 子查询：dname = 'ACCOUNTING'
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .returns("CENAME=11\n"); // 验证返回结果

    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("SELECT COUNT(ename) AS cEname FROM \"SCOTT\".\"EMP\" " // SQL 查询：计算 ename 的数量
            + "WHERE DEPTNO < (SELECT deptno FROM \"SCOTT\".\"DEPT\" " // 过滤条件：deptno 小于子查询结果
            + "WHERE dname = 'ACCOUNTING')") // 子查询：dname = 'ACCOUNTING'
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB) // 仅在 HSQLDB 数据库上启用
        .returns("CENAME=0\n"); // 验证返回结果
  }

  /**
   * Acquires an exclusive connection to a test database, and cleans it.
   * 获取测试数据库的独占连接，并清理它。
   *
   * <p>Cleans any previous TableModify states and creates
   * one expense_fact instance with store_id = 666.
   * 清理所有之前的 TableModify 状态，并创建一个 store_id = 666 的 expense_fact 实例。
   *
   * <p>Caller must close the returned wrapper, so that the next test can
   * acquire the lock and use the database.
   * 调用者必须关闭返回的包装器，以便下一个测试可以获取锁并使用数据库。
   *
   * @param c JDBC connection
   * @param c JDBC 连接
   */
  private LockWrapper exclusiveCleanDb(Connection c) throws SQLException { // 私有方法：独占清理数据库
    final LockWrapper wrapper = LockWrapper.lock(LOCK); // 获取锁包装器
    try (Statement statement = c.createStatement()) { // 创建语句对象
      final String dSql = "DELETE FROM \"foodmart\".\"expense_fact\"" // 删除 SQL
          + " WHERE \"store_id\"=666\n"; // 删除条件：store_id = 666
      final String iSql = "INSERT INTO \"foodmart\".\"expense_fact\"(\n" // 插入 SQL
          + " \"store_id\", \"account_id\", \"exp_date\", \"time_id\","
          + " \"category_id\", \"currency_id\", \"amount\")\n" // 插入列
          + " VALUES (666, 666, TIMESTAMP '1997-01-01 00:00:00'," // 插入值
          + " 666, '666', 666, 666)"; // 插入值
      statement.executeUpdate(dSql); // 执行删除
      int rowCount = statement.executeUpdate(iSql); // 执行插入并获取行数
      assertThat(rowCount, is(1)); // 验证插入 1 行
      return wrapper; // 返回锁包装器
    } catch (SQLException | RuntimeException | Error e) { // 捕获异常
      wrapper.close(); // 关闭锁包装器
      throw e; // 重新抛出异常
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1527">[CALCITE-1527]
   * Support DML in the JDBC adapter</a>.
   * 测试用例：验证 JDBC 适配器支持 DML 操作。
   * 
   * 该测试针对 CALCITE-1527 功能，JDBC 适配器支持 DML（INSERT、UPDATE、DELETE）操作。
   * 测试通过执行 INSERT 语句来验证功能。
   */
  @Test void testTableModifyInsert() throws Exception { // 测试方法：测试表修改 INSERT
    final String sql = "INSERT INTO \"foodmart\".\"expense_fact\"(\n" // SQL 插入语句
        + " \"store_id\", \"account_id\", \"exp_date\", \"time_id\","
        + " \"category_id\", \"currency_id\", \"amount\")\n" // 插入列
        + "VALUES (666, 666, TIMESTAMP '1997-01-01 00:00:00'," // 插入值
        + " 666, '666', 666, 666)"; // 插入值
    final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
        + "  JdbcTableModify(table=[[foodmart, expense_fact]], " // JDBC 表修改节点
        + "operation=[INSERT], flattened=[false])\n" // 操作类型：INSERT，flattened：false
        + "    JdbcValues(tuples=[[{ 666, 666, 1997-01-01 00:00:00, 666, " // JDBC VALUES 节点
        + "'666', 666, 666.0000 }]])\n\n"; // 元组值
    final String jdbcSql = "INSERT INTO \"foodmart\".\"expense_fact\" (\"store_id\", " // 预期生成的 JDBC SQL
        + "\"account_id\", \"exp_date\", \"time_id\", \"category_id\", \"currency_id\", "
        + "\"amount\")\n" // 列
        + "VALUES (666, 666, TIMESTAMP '1997-01-01 00:00:00', 666, '666', " // 值
        + "666, 666.0000)"; // 值
    final AssertThat that = // 创建断言
        CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
            .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB // 仅在 HSQLDB 或 PostgreSQL 数据库上启用
                || CalciteAssert.DB == DatabaseInstance.POSTGRESQL);
    that.doWithConnection(connection -> { // 使用连接执行操作
      try (LockWrapper ignore = exclusiveCleanDb(connection)) { // 独占清理数据库
        that.query(sql) // 执行查询
            .explainContains(explain) // 验证执行计划包含预期的节点
            .planUpdateHasSql(jdbcSql, 1); // 验证生成的 SQL 和影响的行数
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    });
  }

  @Test void testTableModifyInsertMultiValues() throws Exception { // 测试方法：测试表修改 INSERT 多值
    final String sql = "INSERT INTO \"foodmart\".\"expense_fact\"(\n" // SQL 插入语句
        + " \"store_id\", \"account_id\", \"exp_date\", \"time_id\","
        + " \"category_id\", \"currency_id\", \"amount\")\n" // 插入列
        + "VALUES (666, 666, TIMESTAMP '1997-01-01 00:00:00'," // 第一组值
        + "   666, '666', 666, 666),\n" // 第一组值
        + " (666, 777, TIMESTAMP '1997-01-01 00:00:00'," // 第二组值
        + "   666, '666', 666, 666)"; // 第二组值
    final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
        + "  JdbcTableModify(table=[[foodmart, expense_fact]], " // JDBC 表修改节点
        + "operation=[INSERT], flattened=[false])\n" // 操作类型：INSERT，flattened：false
        + "    JdbcValues(tuples=[[" // JDBC VALUES 节点
        + "{ 666, 666, 1997-01-01 00:00:00, 666, '666', 666, 666.0000 }, " // 第一个元组
        + "{ 666, 777, 1997-01-01 00:00:00, 666, '666', 666, 666.0000 }]])\n\n"; // 第二个元组
    final String jdbcSql = "INSERT INTO \"foodmart\".\"expense_fact\"" // 预期生成的 JDBC SQL
        + " (\"store_id\", \"account_id\", \"exp_date\", \"time_id\", " // 列
        + "\"category_id\", \"currency_id\", \"amount\")\n" // 列
        + "VALUES " // VALUES 关键字
        + "(666, 666, TIMESTAMP '1997-01-01 00:00:00', 666, '666', 666, 666.0000),\n" // 第一组值
        + "(666, 777, TIMESTAMP '1997-01-01 00:00:00', 666, '666', 666, 666.0000)"; // 第二组值
    final AssertThat that = // 创建断言

        CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
            .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB // 仅在 HSQLDB 或 PostgreSQL 数据库上启用
                || CalciteAssert.DB == DatabaseInstance.POSTGRESQL);
    that.doWithConnection(connection -> { // 使用连接执行操作
      try (LockWrapper ignore = exclusiveCleanDb(connection)) { // 独占清理数据库
        that.query(sql) // 执行查询
            .explainContains(explain) // 验证执行计划包含预期的节点
            .planUpdateHasSql(jdbcSql, 2); // 验证生成的 SQL 和影响的行数
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    });
  }

  @Test void testTableModifyInsertWithSubQuery() throws Exception { // 测试方法：测试表修改 INSERT 带子查询
    final AssertThat that = CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB); // 仅在 HSQLDB 数据库上启用

    that.doWithConnection(connection -> { // 使用连接执行操作
      try (LockWrapper ignore = exclusiveCleanDb(connection)) { // 独占清理数据库
        final String sql = "INSERT INTO \"foodmart\".\"expense_fact\"(\n" // SQL 插入语句
            + " \"store_id\", \"account_id\", \"exp_date\", \"time_id\","
            + " \"category_id\", \"currency_id\", \"amount\")\n" // 插入列
            + "SELECT  \"store_id\", \"account_id\", \"exp_date\"," // SELECT 子查询
            + " \"time_id\" + 1, \"category_id\", \"currency_id\"," // 选择列并计算
            + " \"amount\"\n" // 选择列
            + "FROM \"foodmart\".\"expense_fact\"\n" // 从 expense_fact 表
            + "WHERE \"store_id\" = 666"; // 过滤条件：store_id = 666
        final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcTableModify(table=[[foodmart, expense_fact]], operation=[INSERT], flattened=[false])\n" // JDBC 表修改节点
            + "    JdbcProject(store_id=[$0], account_id=[$1], exp_date=[$2], time_id=[+($3, 1)], category_id=[$4], currency_id=[$5], amount=[$6])\n" // JDBC 投影：选择列并计算
            + "      JdbcFilter(condition=[=($0, 666)])\n" // JDBC 过滤：store_id = 666
            + "        JdbcTableScan(table=[[foodmart, expense_fact]])\n"; // JDBC 表扫描
        final String jdbcSql = "INSERT INTO \"foodmart\".\"expense_fact\"" // 预期生成的 JDBC SQL
            + " (\"store_id\", \"account_id\", \"exp_date\", \"time_id\"," // 列
            + " \"category_id\", \"currency_id\", \"amount\")\n" // 列
            + "SELECT \"store_id\", \"account_id\", \"exp_date\"," // SELECT 子查询
            + " \"time_id\" + 1 AS \"time_id\", \"category_id\"," // 选择列并计算
            + " \"currency_id\", \"amount\"\n" // 选择列
            + "FROM \"foodmart\".\"expense_fact\"\n" // 从 expense_fact 表
            + "WHERE \"store_id\" = 666"; // 过滤条件：store_id = 666
        that.query(sql) // 执行查询
            .explainContains(explain) // 验证执行计划包含预期的节点
            .planUpdateHasSql(jdbcSql, 1); // 验证生成的 SQL 和影响的行数
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    });
  }

  @Test void testTableModifyUpdate() throws Exception { // 测试方法：测试表修改 UPDATE
    final AssertThat that = CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB); // 仅在 HSQLDB 数据库上启用

    that.doWithConnection(connection -> { // 使用连接执行操作
      try (LockWrapper ignore = exclusiveCleanDb(connection)) { // 独占清理数据库
        final String sql = "UPDATE \"foodmart\".\"expense_fact\"\n" // SQL 更新语句
            + " SET \"account_id\"=888\n" // 设置 account_id = 888
            + " WHERE \"store_id\"=666\n"; // 过滤条件：store_id = 666
        final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcTableModify(table=[[foodmart, expense_fact]], operation=[UPDATE], updateColumnList=[[account_id]], sourceExpressionList=[[888]], flattened=[false])\n" // JDBC 表修改节点
            + "    JdbcProject(store_id=[$0], account_id=[$1], exp_date=[$2], time_id=[$3], category_id=[$4], currency_id=[$5], amount=[$6], EXPR$0=[888])\n" // JDBC 投影：选择列并添加表达式
            + "      JdbcFilter(condition=[=($0, 666)])\n" // JDBC 过滤：store_id = 666
            + "        JdbcTableScan(table=[[foodmart, expense_fact]])"; // JDBC 表扫描
        final String jdbcSql = "UPDATE \"foodmart\".\"expense_fact\"" // 预期生成的 JDBC SQL
            + " SET \"account_id\" = 888\n" // 设置 account_id = 888
            + "WHERE \"store_id\" = 666"; // 过滤条件：store_id = 666
        that.query(sql) // 执行查询
            .explainContains(explain) // 验证执行计划包含预期的节点
            .planUpdateHasSql(jdbcSql, 1); // 验证生成的 SQL 和影响的行数
        return null; // 返回 null
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    });
  }

  @Test void testTableModifyDelete() throws Exception { // 测试方法：测试表修改 DELETE
    final AssertThat that = CalciteAssert // 创建断言
        .model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB); // 仅在 HSQLDB 数据库上启用

    that.doWithConnection(connection -> { // 使用连接执行操作
      try (LockWrapper ignore = exclusiveCleanDb(connection)) { // 独占清理数据库
        final String sql = "DELETE FROM \"foodmart\".\"expense_fact\"\n" // SQL 删除语句
            + "WHERE \"store_id\"=666\n"; // 过滤条件：store_id = 666
        final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcTableModify(table=[[foodmart, expense_fact]], operation=[DELETE], flattened=[false])\n" // JDBC 表修改节点
            + "    JdbcFilter(condition=[=($0, 666)])\n" // JDBC 过滤：store_id = 666
            + "      JdbcTableScan(table=[[foodmart, expense_fact]])"; // JDBC 表扫描
        final String jdbcSql = "DELETE FROM \"foodmart\".\"expense_fact\"\n" // 预期生成的 JDBC SQL
            + "WHERE \"store_id\" = 666"; // 过滤条件：store_id = 666
        that.query(sql) // 执行查询
            .explainContains(explain) // 验证执行计划包含预期的节点
            .planUpdateHasSql(jdbcSql, 1); // 验证生成的 SQL 和影响的行数
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    });
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1572">[CALCITE-1572]
   * JdbcSchema throws exception when detecting nullable columns</a>.
   * 测试用例：验证 JdbcSchema 在检测可空列时不会抛出异常。
   * 
   * 该测试针对 CALCITE-1572 问题，JdbcSchema 在检测可空列时抛出异常。
   * 测试通过执行查询并检查列类型来验证修复。
   */
  @Test void testColumnNullability() { // 测试方法：测试列可空性
    final String sql = "select \"employee_id\", \"position_id\"\n" // SQL 查询：选择 employee_id 和 position_id
        + "from \"foodmart\".\"employee\" limit 10"; // 从 employee 表，限制 10 行
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query(sql) // 执行查询
        .runs() // 执行查询
        .returnsCount(10) // 验证返回 10 行
        .typeIs("[employee_id INTEGER NOT NULL, position_id INTEGER]"); // 验证返回类型
  }

  @Test void pushBindParameters() { // 测试方法：测试绑定参数下推
    final String sql = "select empno, ename from emp where empno = ?"; // SQL 查询：选择 empno, ename，条件是 empno = 动态参数
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query(sql) // 执行查询
        .consumesPreparedStatement(p -> p.setInt(1, 7566)) // 设置动态参数值为 7566
        .returnsCount(1) // 验证返回 1 行
        .planHasSql("SELECT \"EMPNO\", \"ENAME\"\nFROM \"SCOTT\".\"EMP\"\nWHERE \"EMPNO\" = ?"); // 验证生成的 SQL 包含动态参数
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4619">[CALCITE-4619]
   * "Full join" generates an incorrect execution plan under mysql</a>.
   * 测试用例：验证 FULL JOIN 在 MySQL 下生成正确的执行计划。
   * 
   * 该测试针对 CALCITE-4619 问题，FULL JOIN 在 MySQL 下生成错误的执行计划。
   * 测试通过执行 FULL JOIN 查询来验证修复。
   */
  @Test void testFullJoinNonSupportedDialect() { // 测试方法：测试不支持 FULL JOIN 的方言
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.H2 // 仅在 H2 或 MySQL 数据库上启用
            || CalciteAssert.DB == CalciteAssert.DatabaseInstance.MYSQL)
        .query("select empno, ename, e.deptno, dname\n" // SQL 查询：选择 empno, ename, e.deptno, dname
            + "from scott.emp e full join scott.dept d\n" // emp 表和 dept 表全连接
            + "on e.deptno = d.deptno") // 连接条件：e.deptno = d.deptno
        .explainContains("PLAN=EnumerableCalc(expr#0..4=[{inputs}], proj#0..2=[{exprs}]," // 预期的执行计划：使用可枚举计算
            + " DNAME=[$t4])\n" // 投影：选择列
            + "  EnumerableHashJoin(condition=[=($2, $3)], joinType=[full])\n" // 可枚举哈希连接：全连接
            + "    JdbcToEnumerableConverter\n" // JDBC 到可枚举转换器
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // JDBC 投影：选择 empno, ename, deptno
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // JDBC 表扫描
            + "    JdbcToEnumerableConverter\n" // JDBC 到可枚举转换器
            + "      JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // JDBC 投影：选择 deptno, dname
            + "        JdbcTableScan(table=[[SCOTT, DEPT]])") // JDBC 表扫描
        .runs(); // 执行查询
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6995">[CALCITE-6995]
   * Support FULL JOIN in StarRocks/Doris Dialect</a>.
   * 测试用例：验证 FULL JOIN 在 StarRocks/Doris 方言中得到支持。
   * 
   * 该测试针对 CALCITE-6995 功能，支持 StarRocks/Doris 方言中的 FULL JOIN。
   * 测试通过执行 FULL JOIN 查询来验证功能。
   */
  @Test void testFullJoinSupportedDialect() { // 测试方法：测试支持 FULL JOIN 的方言
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .enable(CalciteAssert.DB != CalciteAssert.DatabaseInstance.H2 // 排除 H2 和 MySQL 数据库
            && CalciteAssert.DB != CalciteAssert.DatabaseInstance.MYSQL)
        .query("select empno, ename, e.deptno, dname\n" // SQL 查询：选择 empno, ename, e.deptno, dname
            + "from scott.emp e full join scott.dept d\n" // emp 表和 dept 表全连接
            + "on e.deptno = d.deptno") // 连接条件：e.deptno = d.deptno
        .explainContains("PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
            + "  JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$2], DNAME=[$4])\n" // JDBC 投影：选择 empno, ename, deptno, dname
            + "    JdbcJoin(condition=[=($2, $3)], joinType=[full])\n" // JDBC 连接：全连接
            + "      JdbcProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // 左侧投影：选择 empno, ename, deptno
            + "        JdbcTableScan(table=[[SCOTT, EMP]])\n" // JDBC 表扫描
            + "      JdbcProject(DEPTNO=[$0], DNAME=[$1])\n" // 右侧投影：选择 deptno, dname
            + "        JdbcTableScan(table=[[SCOTT, DEPT]])\n\n") // JDBC 表扫描
        .runs(); // 执行查询
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5243">[CALCITE-5243]
   * "SELECT NULL AS C causes NoSuchMethodException: java.sql.ResultSet.getVoid(int)</a>.
   * 测试用例：验证 SELECT NULL AS C 不会导致 NoSuchMethodException。
   * 
   * 该测试针对 CALCITE-5243 问题，SELECT NULL AS C 导致 NoSuchMethodException: java.sql.ResultSet.getVoid(int)。
   * 测试通过执行 SELECT NULL AS C 查询来验证修复。
   */
  @Test void testNullSelect() { // 测试方法：测试 NULL 选择
    final String sql = "select NULL AS C from \"days\""; // SQL 查询：选择 NULL 并别名为 C
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .query(sql) // 执行查询
        .runs() // 执行查询
        .returnsCount(7) // 验证返回 7 行
        .returns("C=null\nC=null\nC=null\nC=null\nC=null\nC=null\nC=null\n"); // 验证返回结果
  }

  @Test void testMerge() throws Exception { // 测试方法：测试 MERGE 语句
    final String sql = "merge into \"foodmart\".\"expense_fact\"\n" // SQL MERGE 语句
        + "using (values(666, 42)) as vals(store_id, amount)\n" // 使用 VALUES 子句作为源
        + "on \"expense_fact\".\"store_id\" = vals.store_id\n" // 连接条件：store_id 匹配
        + "when matched then update\n" // 匹配时更新
        + "set \"amount\" = vals.amount\n" // 设置 amount = vals.amount
        + "when not matched then insert\n" // 不匹配时插入
        + "values (vals.store_id, 666, TIMESTAMP '1997-01-01 00:00:00', 666, '666', 666," // 插入值
        + " vals.amount)"; // 插入值
    final String explain = "PLAN=JdbcToEnumerableConverter\n" // 预期的执行计划
        + "  JdbcTableModify(table=[[foodmart, expense_fact]], operation=[MERGE]," // JDBC 表修改节点：MERGE 操作
        + " updateColumnList=[[amount]], flattened=[false])\n" // 更新列列表：amount
        + "    JdbcProject(STORE_ID=[$0], $f1=[666], $f2=[1997-01-01 00:00:00], $f3=[666]," // JDBC 投影：选择列
        + " $f4=['666':VARCHAR(30)], $f5=[666], AMOUNT=[CAST($1):DECIMAL(10, 4) NOT NULL]," // 选择列并转换类型
        + " store_id=[$2]," // 选择列
        + " account_id=[$3], exp_date=[$4], time_id=[$5], category_id=[$6], currency_id=[$7]," // 选择列
        + " amount=[$8], AMOUNT0=[$1])\n" // 选择列
        + "      JdbcJoin(condition=[=($2, $0)], joinType=[left])\n" // JDBC 左连接
        + "        JdbcValues(tuples=[[{ 666, 42 }]])\n" // JDBC VALUES 节点
        + "        JdbcTableScan(table=[[foodmart, expense_fact]])\n"; // JDBC 表扫描
    final String jdbcSql = "MERGE INTO \"foodmart\".\"expense_fact\"\n" // 预期生成的 JDBC SQL
        + "USING (VALUES (666, 42)) AS \"t\" (\"STORE_ID\", \"AMOUNT\")\n" // USING 子句
        + "ON \"t\".\"STORE_ID\" = \"expense_fact\".\"store_id\"\n" // ON 条件
        + "WHEN MATCHED THEN UPDATE SET \"amount\" = \"t\".\"AMOUNT\"\n" // MATCHED 时更新
        + "WHEN NOT MATCHED THEN INSERT (\"store_id\", \"account_id\", \"exp_date\", \"time_id\", " // NOT MATCHED 时插入
        + "\"category_id\", \"currency_id\", \"amount\") VALUES \"t\".\"STORE_ID\",\n" // 插入值
        + "666,\nTIMESTAMP '1997-01-01 00:00:00',\n666,\n'666',\n666,\n" // 插入值
        + "CAST(\"t\".\"AMOUNT\" AS DECIMAL(10, 4))"; // 插入值并转换类型
    final AssertThat that = // 创建断言
        CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
            .enable(CalciteAssert.DB == DatabaseInstance.HSQLDB); // 仅在 HSQLDB 数据库上启用
    that.doWithConnection(connection -> { // 使用连接执行操作
      try (LockWrapper ignore = exclusiveCleanDb(connection)) { // 独占清理数据库
        that.query(sql) // 执行查询
            .explainContains(explain) // 验证执行计划包含预期的节点
            .planUpdateHasSql(jdbcSql, 1); // 验证生成的 SQL 和影响的行数
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    });
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6221">[CALCITE-6221]</a>.*/
  @Test void testUnknownColumn() { // 测试方法：测试未知列
    CalciteAssert.model(JdbcTest.SCOTT_MODEL) // 使用 SCOTT 测试模型
        .query("SELECT\n" // SQL 查询：选择列
          + "    \"content-format-owner\",\n" // 选择列
          + "    \"content-owner\"\n" // 选择列
          + "FROM\n" // 从子查询
          + "    (\n" // 子查询开始
          + "        SELECT\n" // 选择列
          + "            d1.dname AS \"content-format-owner\",\n" // dname 别名为 content-format-owner
          + "            d2.dname || ' ' AS \"content-owner\"\n" // dname 拼接空格别名为 content-owner
          + "        FROM\n" // 从表
          + "            scott.emp e1\n" // emp 表
          + "            left outer join scott.dept d1 on e1.deptno = d1.deptno\n" // 左连接 dept 表
          + "            left outer join scott.dept d2 on e1.deptno = d2.deptno\n" // 左连接 dept 表
          + "            left outer join scott.emp e2 on e1.deptno = e2.deptno\n" // 左连接 emp 表
          + "        GROUP BY\n" // 分组
          + "            d1.dname,\n" // 按 dname 分组
          + "            d2.dname\n" // 按 dname 分组
          + "    )\n" // 子查询结束
          + "WHERE\n" // 过滤条件
          + "    \"content-owner\" IN (?)") // content-owner 在动态参数列表中
        .planHasSql("SELECT " // 验证生成的 SQL
            + "\"t2\".\"DNAME\" AS \"content-format-owner\", " // 选择列
            + "\"t2\".\"DNAME0\" || ' ' AS \"content-owner\"\n" // 选择列并拼接
            + "FROM (SELECT \"t\".\"DEPTNO\" AS \"DEPTNO\", " // 子查询：选择列
            + "\"t0\".\"DEPTNO\" AS \"DEPTNO0\", " // 选择列
            + "\"t0\".\"DNAME\" AS \"DNAME\", " // 选择列
            + "\"t1\".\"DEPTNO\" AS \"DEPTNO1\", " // 选择列
            + "\"t1\".\"DNAME\" AS \"DNAME0\"\n" // 选择列
            + "FROM (SELECT \"DEPTNO\"\n" // 子查询：选择 deptno
            + "FROM \"SCOTT\".\"EMP\") AS \"t\"\n" // 从 emp 表
            + "LEFT JOIN (SELECT \"DEPTNO\", \"DNAME\"\n" // 左连接
            + "FROM \"SCOTT\".\"DEPT\") AS \"t0\" ON \"t\".\"DEPTNO\" = \"t0\".\"DEPTNO\"\n" // 连接条件
            + "LEFT JOIN (SELECT \"DEPTNO\", \"DNAME\"\n" // 左连接
            + "FROM \"SCOTT\".\"DEPT\") AS \"t1\" " // 左连接
            + "ON \"t\".\"DEPTNO\" = \"t1\".\"DEPTNO\"\n" // 连接条件
            + "WHERE \"t1\".\"DNAME\" || ' ' = ?) AS \"t2\"\n" // 过滤条件
            + "LEFT JOIN (SELECT \"DEPTNO\"\n" // 左连接
            + "FROM \"SCOTT\".\"EMP\") AS \"t3\" " // 左连接
            + "ON \"t2\".\"DEPTNO\" = \"t3\".\"DEPTNO\"\n" // 连接条件
            + "GROUP BY \"t2\".\"DNAME\", \"t2\".\"DNAME0\"") // 分组
        .runs(); // 执行查询
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4188">[CALCITE-4188]
   * Support EnumerableBatchNestedLoopJoin for JDBC</a>.
   * 测试用例：验证 JDBC 支持 EnumerableBatchNestedLoopJoin。
   * 
   * 该测试针对 CALCITE-4188 功能，支持 JDBC 的 EnumerableBatchNestedLoopJoin。
   * 测试通过执行批量嵌套循环连接查询来验证功能。
   */
  @Test void testBatchNestedLoopJoinPlan() { // 测试方法：测试批量嵌套循环连接计划
    final String sql = "SELECT *\n" // SQL 查询：选择所有列
        + "FROM \"s\".\"emps\" A\n" // 从 s.emps 表（别名 A）
        + "LEFT OUTER JOIN \"foodmart\".\"store\" B ON A.\"empid\" = B.\"store_id\""; // 左连接 foodmart.store 表（别名 B）
    final String explain = "JdbcFilter(condition=[OR(=($cor0.empid0, $0), =($cor1.empid0, $0)"; // 预期的执行计划（部分）
    final String jdbcSql = "SELECT *\n" // 预期生成的 JDBC SQL
        + "FROM \"foodmart\".\"store\"\n" // 从 foodmart.store 表
        + "WHERE ? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\") OR (? = \"store_id\" OR" // WHERE 条件：包含多个 OR
        + " (? = \"store_id\" OR ? = \"store_id\")) OR (? = \"store_id\" OR (? = \"store_id\" OR ? " // WHERE 条件：包含多个 OR
        + "= \"store_id\") OR (? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\"))) OR (? =" // WHERE 条件：包含多个 OR
        + " \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\") OR (? = \"store_id\" OR (? = " // WHERE 条件：包含多个 OR
        + "\"store_id\" OR ? = \"store_id\")) OR (? = \"store_id\" OR (? = \"store_id\" OR ? = " // WHERE 条件：包含多个 OR
        + "\"store_id\") OR (? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\"))) OR (? =" // WHERE 条件：包含多个 OR
        + " \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\") OR (? = \"store_id\" OR (? = " // WHERE 条件：包含多个 OR
        + "\"store_id\" OR ? = \"store_id\")) OR (? = \"store_id\" OR (? = \"store_id\" OR ? = " // WHERE 条件：包含多个 OR
        + "\"store_id\") OR (? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\"))) OR (? =" // WHERE 条件：包含多个 OR
        + " \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\") OR (? = \"store_id\" OR (? = " // WHERE 条件：包含多个 OR
        + "\"store_id\" OR ? = \"store_id\")) OR (? = \"store_id\" OR (? = \"store_id\" OR ? = " // WHERE 条件：包含多个 OR
        + "\"store_id\") OR (? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\"))) OR (? =" // WHERE 条件：包含多个 OR
        + " \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\") OR (? = \"store_id\" OR (? = " // WHERE 条件：包含多个 OR
        + "\"store_id\" OR ? = \"store_id\")) OR (? = \"store_id\" OR (? = \"store_id\" OR ? = " // WHERE 条件：包含多个 OR
        + "\"store_id\") OR (? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\"))) OR (? =" // WHERE 条件：包含多个 OR
        + " \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\") OR (? = \"store_id\" OR (? = " // WHERE 条件：包含多个 OR
        + "\"store_id\" OR ? = \"store_id\")) OR (? = \"store_id\" OR (? = \"store_id\" OR ? = " // WHERE 条件：包含多个 OR
        + "\"store_id\") OR (? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\"))) OR (? =" // WHERE 条件：包含多个 OR
        + " \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\") OR (? = \"store_id\" OR (? = " // WHERE 条件：包含多个 OR
        + "\"store_id\" OR ? = \"store_id\")) OR (? = \"store_id\" OR (? = \"store_id\" OR ? = " // WHERE 条件：包含多个 OR
        + "\"store_id\") OR (? = \"store_id\" OR (? = \"store_id\" OR ? = \"store_id\"))))))"; // WHERE 条件：包含多个 OR
    CalciteAssert.model(FoodmartSchema.FOODMART_MODEL) // 使用 FoodMart 测试模型
        .withSchema("s", new ReflectiveSchema(new HrSchema())) // 添加 s 模式
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 添加规划器钩子
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批量嵌套循环连接规则
        })
        .query(sql) // 执行查询
        .explainContains(explain) // 验证执行计划包含预期的节点
        .runs() // 执行查询
        .enable(CalciteAssert.DB == CalciteAssert.DatabaseInstance.HSQLDB // 仅在 HSQLDB 或 PostgreSQL 数据库上启用
            || CalciteAssert.DB == DatabaseInstance.POSTGRESQL)
        .planHasSql(jdbcSql) // 验证生成的 SQL
        .returnsCount(4); // 验证返回 4 行结果
  }

  /** Acquires a lock, and releases it when closed.
   * 获取锁，并在关闭时释放它。
   * 
   * 这是一个内部类，用于实现 AutoCloseable 接口，
   * 可以在 try-with-resources 语句中使用，自动释放锁。
   */
  static class LockWrapper implements AutoCloseable { // 静态内部类：锁包装器，实现 AutoCloseable 接口
    private final Lock lock; // 锁对象

    LockWrapper(Lock lock) { // 构造方法：初始化锁包装器
      this.lock = lock; // 设置锁对象
    }

    /** Acquires a lock and returns a closeable wrapper.
     * 获取锁并返回可关闭的包装器。
     * 
     * 这是一个静态工厂方法，用于获取锁并创建包装器。
     * 
     * @param lock 要获取的锁
     * @return 锁包装器
     */
    static LockWrapper lock(Lock lock) { // 静态方法：获取锁并返回包装器
      lock.lock(); // 获取锁
      return new LockWrapper(lock); // 返回锁包装器
    }

    public void close() { // close 方法：释放锁
      lock.unlock(); // 释放锁
    }
  }
}