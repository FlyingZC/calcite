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
package org.apache.calcite.test; // 声明包名,该测试类位于 org.apache.calcite.test 包下

import org.apache.calcite.adapter.spark.SparkRel; // 导入 SparkRel 类,用于确保测试依赖 Spark 模块
import org.apache.calcite.util.Util; // 导入 Util 工具类,用于调用 discard 方法

import org.junit.jupiter.api.Disabled; // 导入 Disabled 注解,用于标记禁用的测试方法
import org.junit.jupiter.api.Test; // 导入 Test 注解,用于标记测试方法

/**
 * Tests for using Calcite with Spark as an internal engine, as implemented by
 * the {@link org.apache.calcite.adapter.spark} package.
 * // 该类用于测试 Calcite 与 Spark 作为内部引擎的集成,由 org.apache.calcite.adapter.spark 包实现
 *
 * <p>Under JDK 23 and higher, this test requires
 * "{@code -Djava.security.manager=allow}" command-line arguments due to
 * Hadoop's use of deprecated methods in {@link javax.security.auth.Subject}.
 * These arguments are set automatically if you run via Gradle.
 * // 在 JDK 23 及更高版本下,由于 Hadoop 使用了 javax.security.auth.Subject 中的已弃用方法,该测试需要 "-Djava.security.manager=allow" 命令行参数。如果通过 Gradle 运行,这些参数会自动设置。
 */
class SparkAdapterTest { // 定义 SparkAdapterTest 测试类,用于测试 Spark 适配器的功能
  private static final String VALUES0 = "(values (1, 'a'), (2, 'b'))"; // 定义 VALUES0 常量,包含两行值 (1,'a') 和 (2,'b'),用于基础 VALUES 查询测试

  private static final String VALUES1 = // 定义 VALUES1 常量,包含两行值并指定别名 t 和列名 x,y
      "(values (1, 'a'), (2, 'b')) as t(x, y)"; // VALUES1 用于带别名和列名的 VALUES 查询测试

  private static final String VALUES2 = // 定义 VALUES2 常量,包含五行值并指定别名 t 和列名 x,y
      "(values (1, 'a'), (2, 'b'), (1, 'b'), (2, 'c'), (2, 'c')) as t(x, y)"; // VALUES2 用于测试重复值、分组、聚合等场景

  private static final String VALUES3 = // 定义 VALUES3 常量,包含两行值并指定别名 v 和列名 w,z
      "(values (1, 'a'), (2, 'b')) as v(w, z)"; // VALUES3 用于测试连接操作,列名不同

  private static final String VALUES4 = // 定义 VALUES4 常量,包含五行值并指定别名 t 和列名 x,y
      "(values (1, 'a'), (2, 'b'), (3, 'b'), (4, 'c'), (2, 'c')) as t(x, y)"; // VALUES4 用于测试更复杂的过滤条件

  private CalciteAssert.AssertQuery sql(String sql) { // 定义 sql 方法,用于创建带有 SPARK 配置的断言查询对象
    return CalciteAssert.that() // 调用 CalciteAssert.that() 创建断言构建器
        .with(CalciteAssert.Config.SPARK) // 配置使用 Spark 配置
        .query(sql); // 设置要执行的 SQL 查询字符串
  }

  /**
   * Tests a VALUES query evaluated using Spark.
   * There are no data sources.
   * // 测试使用 Spark 评估的 VALUES 查询,不涉及任何数据源
   */
  @Test void testValues() { // 定义 testValues 测试方法,使用 @Test 注解标记
    // Insert a spurious reference to a class in Calcite's Spark adapter.
    // Otherwise this test doesn't depend on the Spark module at all, and
    // Javadoc gets confused.
    // // 插入对 Calcite Spark 适配器中类的引用,否则此测试不依赖 Spark 模块,Javadoc 会混淆
    Util.discard(SparkRel.class); // 调用 Util.discard 方法丢弃 SparkRel.class,确保测试依赖 Spark 模块

    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES0; // 从 VALUES0 常量定义的值中查询

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])"; // 期望生成 EnumerableValues 节点,包含两个元组

    final String[] expectedResult = { // 定义预期的查询结果数组
        "EXPR$0=1; EXPR$1=a", // 第一行结果,EXPR$0 是第一列的默认别名,值为 1,EXPR$1 是第二列,值为 'a'
        "EXPR$0=2; EXPR$1=b" // 第二行结果,EXPR$0 值为 2,EXPR$1 值为 'b'
    };

    sql(sql).returnsOrdered(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  /** Tests values followed by filter, evaluated by Spark. */
  // // 测试 VALUES 查询后跟过滤条件,由 Spark 评估
  @Test void testValuesFilter() { // 定义 testValuesFilter 测试方法
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x < 2"; // 添加过滤条件,只选择 x 列值小于 2 的行

    final String[] expectedResult = {"X=1; Y=a"}; // 定义预期的查询结果,只有一行 X=1, Y=a

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[2], expr#3=[<($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 期望生成 EnumerableCalc 节点,包含过滤条件 x < 2
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n"; // 子节点是 EnumerableValues,包含原始数据

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testSelectDistinct() { // 定义 testSelectDistinct 测试方法,测试 DISTINCT 去重功能
    final String sql = "select distinct *\n" // 定义 SQL 查询字符串,使用 DISTINCT 去除重复行
        + "from " + VALUES2; // 从 VALUES2 定义的值中查询,VALUES2 包含重复行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableAggregate(group=[{0, 1}])\n" // 期望生成 EnumerableAggregate 节点,按第 0 和 1 列分组实现去重
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据(有重复)

    final String[] expectedResult = { // 定义预期的查询结果数组,去重后的四行
        "X=1; Y=a", // 第一行去重结果
        "X=1; Y=b", // 第二行去重结果
        "X=2; Y=b", // 第三行去重结果
        "X=2; Y=c" // 第四行去重结果,原始有两行 (2,'c') 被去重为一行
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests about grouping and aggregate functions
// // 以下测试涉及分组和聚合函数

  @Test void testGroupBy() { // 定义 testGroupBy 测试方法,测试 GROUP BY 分组和多种聚合函数
    final String sql = "select sum(x) as SUM_X, min(y) as MIN_Y, max(y) as MAX_Y, " // 定义 SQL 查询字符串,计算 x 的总和、y 的最小值、y 的最大值
        + "count(*) as CNT_Y, count(distinct y) as CNT_DIST_Y\n" // 计算行数和 y 的不同值数量
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "group by x"; // 按 x 列分组

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..5=[{inputs}], expr#6=[CAST($t1):INTEGER NOT NULL], expr#7=[CAST($t2):CHAR(1) NOT NULL], expr#8=[CAST($t3):CHAR(1) NOT NULL], expr#9=[CAST($t4):BIGINT NOT NULL], SUM_X=[$t6], MIN_Y=[$t7], MAX_Y=[$t8], CNT_Y=[$t9], CNT_DIST_Y=[$t5])\n" // 期望生成 EnumerableCalc 节点,进行类型转换和投影
        + "  EnumerableAggregate(group=[{0}], SUM_X=[MIN($2) FILTER $7], MIN_Y=[MIN($3) FILTER $7], MAX_Y=[MIN($4) FILTER $7], CNT_Y=[MIN($5) FILTER $7], CNT_DIST_Y=[COUNT($1) FILTER $6])\n" // 期望生成 EnumerableAggregate 节点,按第 0 列分组,使用 FILTER 提取 GROUPING 结果
        + "    EnumerableCalc(expr#0..6=[{inputs}], expr#7=[0], expr#8=[=($t6, $t7)], expr#9=[1], expr#10=[=($t6, $t9)], proj#0..5=[{exprs}], $g_0=[$t8], $g_1=[$t10])\n" // 期望生成 EnumerableCalc 节点,计算 GROUPING 条件
        + "      EnumerableAggregate(group=[{0, 1}], groups=[[{0, 1}, {0}]], SUM_X=[$SUM0($0)], MIN_Y=[MIN($1)], MAX_Y=[MAX($1)], CNT_Y=[COUNT()], $g=[GROUPING($0, $1)])\n" // 期望生成 EnumerableAggregate 节点,按 (0,1) 和 (0) 分组,计算聚合函数和 GROUPING
        + "        EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n"; // 子节点是 EnumerableValues,包含原始数据

    final String[] expectedResult = { // 定义预期的查询结果数组,按 x 分组后的聚合结果
        "SUM_X=2; MIN_Y=a; MAX_Y=b; CNT_Y=2; CNT_DIST_Y=2", // x=1 组的结果:sum(x)=2, min(y)='a', max(y)='b', count=2, count(distinct y)=2
        "SUM_X=6; MIN_Y=b; MAX_Y=c; CNT_Y=3; CNT_DIST_Y=2" // x=2 组的结果:sum(x)=6, min(y)='b', max(y)='c', count=3, count(distinct y)=2
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testAggFuncNoGroupBy() { // 定义 testAggFuncNoGroupBy 测试方法,测试不带 GROUP BY 的聚合函数
    final String sql = "select sum(x) as SUM_X, min(y) as MIN_Y, max(y) as MAX_Y, " // 定义 SQL 查询字符串,计算 x 的总和、y 的最小值、y 的最大值
        + "count(*) as CNT_Y, count(distinct y) as CNT_DIST_Y\n" // 计算行数和 y 的不同值数量
        + "from " + VALUES2; // 从 VALUES2 定义的值中查询,不使用 GROUP BY

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..4=[{inputs}], expr#5=[CAST($t3):BIGINT NOT NULL], proj#0..2=[{exprs}], CNT_Y=[$t5], CNT_DIST_Y=[$t4])\n" // 期望生成 EnumerableCalc 节点,进行类型转换和投影
        + "  EnumerableAggregate(group=[{}], SUM_X=[MIN($1) FILTER $6], MIN_Y=[MIN($2) FILTER $6], MAX_Y=[MIN($3) FILTER $6], CNT_Y=[MIN($4) FILTER $6], CNT_DIST_Y=[COUNT($0) FILTER $5])\n" // 期望生成 EnumerableAggregate 节点,空分组(全表聚合),使用 FILTER 提取 GROUPING 结果
        + "    EnumerableCalc(expr#0..5=[{inputs}], expr#6=[0], expr#7=[=($t5, $t6)], expr#8=[1], expr#9=[=($t5, $t8)], proj#0..4=[{exprs}], $g_0=[$t7], $g_1=[$t9])\n" // 期望生成 EnumerableCalc 节点,计算 GROUPING 条件
        + "      EnumerableAggregate(group=[{1}], groups=[[{1}, {}]], SUM_X=[$SUM0($0)], MIN_Y=[MIN($1)], MAX_Y=[MAX($1)], CNT_Y=[COUNT()], $g=[GROUPING($1)])\n" // 期望生成 EnumerableAggregate 节点,按第 1 列和空分组,计算聚合函数和 GROUPING
        + "        EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "SUM_X=8; MIN_Y=a; MAX_Y=c; CNT_Y=5; CNT_DIST_Y=3"; // 定义预期的查询结果,全表聚合:sum(x)=8, min(y)='a', max(y)='c', count=5, count(distinct y)=3

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testGroupByOrderByAsc() { // 定义 testGroupByOrderByAsc 测试方法,测试 GROUP BY 后按升序排序
    final String sql = "select x, count(*) as CNT_Y\n" // 定义 SQL 查询字符串,选择 x 列和计数
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "group by x\n" // 按 x 列分组
        + "order by x asc"; // 按 x 列升序排序

    final String plan = ""; // 定义预期的执行计划字符串,此处为空字符串

    final String expectedResult = "X=1; CNT_Y=2\n" // 定义预期的查询结果,第一行 x=1, count=2
        + "X=2; CNT_Y=3\n"; // 第二行 x=2, count=3

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testGroupByMinMaxCountCountDistinctOrderByAsc() { // 定义 testGroupByMinMaxCountCountDistinctOrderByAsc 测试方法,测试 GROUP BY 多种聚合后按升序排序
    final String sql = "select x, min(y) as MIN_Y, max(y) as MAX_Y, count(*) as CNT_Y, " // 定义 SQL 查询字符串,选择 x 列和多种聚合函数
        + "count(distinct y) as CNT_DIST_Y\n" // 计算 y 的不同值数量
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "group by x\n" // 按 x 列分组
        + "order by x asc"; // 按 x 列升序排序

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 期望生成 EnumerableSort 节点,按第 0 列升序排序
        + "  EnumerableCalc(expr#0..4=[{inputs}], expr#5=[CAST($t1):CHAR(1) NOT NULL], expr#6=[CAST($t2):CHAR(1) NOT NULL], expr#7=[CAST($t3):BIGINT NOT NULL], X=[$t0], MIN_Y=[$t5], MAX_Y=[$t6], CNT_Y=[$t7], CNT_DIST_Y=[$t4])\n" // 期望生成 EnumerableCalc 节点,进行类型转换和投影
        + "    EnumerableAggregate(group=[{0}], MIN_Y=[MIN($2) FILTER $6], MAX_Y=[MIN($3) FILTER $6], CNT_Y=[MIN($4) FILTER $6], CNT_DIST_Y=[COUNT($1) FILTER $5])\n" // 期望生成 EnumerableAggregate 节点,按第 0 列分组,使用 FILTER 提取 GROUPING 结果
        + "      EnumerableCalc(expr#0..5=[{inputs}], expr#6=[0], expr#7=[=($t5, $t6)], expr#8=[1], expr#9=[=($t5, $t8)], proj#0..4=[{exprs}], $g_0=[$t7], $g_1=[$t9])\n" // 期望生成 EnumerableCalc 节点,计算 GROUPING 条件
        + "        EnumerableAggregate(group=[{0, 1}], groups=[[{0, 1}, {0}]], MIN_Y=[MIN($1)], MAX_Y=[MAX($1)], CNT_Y=[COUNT()], $g=[GROUPING($0, $1)])\n" // 期望生成 EnumerableAggregate 节点,按 (0,1) 和 (0) 分组,计算聚合函数和 GROUPING
        + "          EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "X=1; MIN_Y=a; MAX_Y=b; CNT_Y=2; CNT_DIST_Y=2\n" // 定义预期的查询结果,第一行 x=1 的聚合结果
        + "X=2; MIN_Y=b; MAX_Y=c; CNT_Y=3; CNT_DIST_Y=2\n"; // 第二行 x=2 的聚合结果

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testGroupByMiMaxCountCountDistinctOrderByDesc() { // 定义 testGroupByMiMaxCountCountDistinctOrderByDesc 测试方法,测试 GROUP BY 多种聚合后按降序排序
    final String sql = "select x, min(y) as MIN_Y, max(y) as MAX_Y, count(*) as CNT_Y, " // 定义 SQL 查询字符串,选择 x 列和多种聚合函数
        + "count(distinct y) as CNT_DIST_Y\n" // 计算 y 的不同值数量
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "group by x\n" // 按 x 列分组
        + "order by x desc"; // 按 x 列降序排序

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableSort(sort0=[$0], dir0=[DESC])\n" // 期望生成 EnumerableSort 节点,按第 0 列降序排序
        + "  EnumerableCalc(expr#0..4=[{inputs}], expr#5=[CAST($t1):CHAR(1) NOT NULL], expr#6=[CAST($t2):CHAR(1) NOT NULL], expr#7=[CAST($t3):BIGINT NOT NULL], X=[$t0], MIN_Y=[$t5], MAX_Y=[$t6], CNT_Y=[$t7], CNT_DIST_Y=[$t4])\n" // 期望生成 EnumerableCalc 节点,进行类型转换和投影
        + "    EnumerableAggregate(group=[{0}], MIN_Y=[MIN($2) FILTER $6], MAX_Y=[MIN($3) FILTER $6], CNT_Y=[MIN($4) FILTER $6], CNT_DIST_Y=[COUNT($1) FILTER $5])\n" // 期望生成 EnumerableAggregate 节点,按第 0 列分组,使用 FILTER 提取 GROUPING 结果
        + "      EnumerableCalc(expr#0..5=[{inputs}], expr#6=[0], expr#7=[=($t5, $t6)], expr#8=[1], expr#9=[=($t5, $t8)], proj#0..4=[{exprs}], $g_0=[$t7], $g_1=[$t9])\n" // 期望生成 EnumerableCalc 节点,计算 GROUPING 条件
        + "        EnumerableAggregate(group=[{0, 1}], groups=[[{0, 1}, {0}]], MIN_Y=[MIN($1)], MAX_Y=[MAX($1)], CNT_Y=[COUNT()], $g=[GROUPING($0, $1)])\n" // 期望生成 EnumerableAggregate 节点,按 (0,1) 和 (0) 分组,计算聚合函数和 GROUPING
        + "          EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "X=2; MIN_Y=b; MAX_Y=c; CNT_Y=3; CNT_DIST_Y=2\n" // 定义预期的查询结果,第一行 x=2 的聚合结果(降序)
        + "X=1; MIN_Y=a; MAX_Y=b; CNT_Y=2; CNT_DIST_Y=2\n"; // 第二行 x=1 的聚合结果

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testGroupByHaving() { // 定义 testGroupByHaving 测试方法,测试 GROUP BY 后使用 HAVING 过滤分组
    final String sql = "select x\n" // 定义 SQL 查询字符串,选择 x 列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "group by x\n" // 按 x 列分组
        + "having count(*) > 2"; // 使用 HAVING 子句过滤,只保留行数大于 2 的分组

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[2:BIGINT], expr#3=[>($t1, $t2)], X=[$t0], $condition=[$t3])\n" // 期望生成 EnumerableCalc 节点,过滤 count(*) > 2 的分组
        + "  EnumerableAggregate(group=[{0}], agg#0=[COUNT()])\n" // 期望生成 EnumerableAggregate 节点,按第 0 列分组并计算 COUNT
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "X=2"; // 定义预期的查询结果,只有 x=2 的分组满足 count(*) > 2(count=3)

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests about set operators (UNION, UNION ALL, INTERSECT)
// // 以下测试涉及集合操作符(UNION, UNION ALL, INTERSECT)

  @Test void testUnionAll() { // 定义 testUnionAll 测试方法,测试 UNION ALL 操作(保留所有行,包括重复)
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + " union all\n" // 使用 UNION ALL 合并结果,保留所有行(包括重复)
        + "select *\n" // 选择所有列
        + "from " + VALUES2; // 从 VALUES2 定义的值中查询

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 期望优化后合并为一个 EnumerableValues 节点,包含所有行(包括重复)

    final String[] expectedResult = { // 定义预期的查询结果数组,包含所有行(包括重复)
        "X=1; Y=a", // 来自 VALUES1 的第一行
        "X=1; Y=a", // 来自 VALUES2 的第一行(重复)
        "X=1; Y=b", // 来自 VALUES2 的第三行
        "X=2; Y=b", // 来自 VALUES1 的第二行
        "X=2; Y=b", // 来自 VALUES2 的第二行(重复)
        "X=2; Y=c", // 来自 VALUES2 的第四行
        "X=2; Y=c" // 来自 VALUES2 的第五行(重复)
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testUnion() { // 定义 testUnion 测试方法,测试 UNION 操作(去除重复行)
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + " union\n" // 使用 UNION 合并结果,去除重复行
        + "select *\n" // 选择所有列
        + "from " + VALUES2; // 从 VALUES2 定义的值中查询

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }]])\n\n"; // 期望优化后合并为一个 EnumerableValues 节点,去除重复行

    final String[] expectedResult = { // 定义预期的查询结果数组,去重后的唯一行
        "X=1; Y=a", // 去重后的第一行
        "X=1; Y=b", // 去重后的第二行
        "X=2; Y=b", // 去重后的第三行
        "X=2; Y=c" // 去重后的第四行
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testIntersect() { // 定义 testIntersect 测试方法,测试 INTERSECT 操作(求交集)
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + " intersect\n" // 使用 INTERSECT 求两个结果集的交集
        + "select *\n" // 选择所有列
        + "from " + VALUES2; // 从 VALUES2 定义的值中查询

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableIntersect(all=[false])\n" // 期望生成 EnumerableIntersect 节点,all=false 表示去除重复
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n" // 左输入是 VALUES1 的数据
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n"; // 右输入是 VALUES2 的数据

    final String[] expectedResult = { // 定义预期的查询结果数组,两个结果集的交集
        "X=1; Y=a", // VALUES1 和 VALUES2 都有的行
        "X=2; Y=b" // VALUES1 和 VALUES2 都有的行
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests about sorting
  // // 以下测试涉及排序操作

  @Test void testSortXAscProjectY() { // 定义 testSortXAscProjectY 测试方法,测试按 x 升序排序但只输出 y 列
    final String sql = "select y\n" // 定义 SQL 查询字符串,只选择 y 列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "order by x asc"; // 按 x 列升序排序(虽然输出只有 y,但排序依据是 x)

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 期望生成 EnumerableSort 节点,按第 1 列(x)升序排序
        + "  EnumerableCalc(expr#0..1=[{inputs}], Y=[$t1], X=[$t0])\n" // 期望生成 EnumerableCalc 节点,输出 Y 列,保留 X 列用于排序
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "Y=a\n" // 定义预期的查询结果,按 x 升序排序后的 y 值
        + "Y=b\n" // 第二行, x=1, y='b'
        + "Y=b\n" // 第三行, x=2, y='b'
        + "Y=c\n" // 第四行, x=2, y='c'
        + "Y=c\n"; // 第五行, x=2, y='c'

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testSortXDescYDescProjectY() { // 定义 testSortXDescYDescProjectY 测试方法,测试按 x 降序、y 降序排序但只输出 y 列
    final String sql = "select y\n" // 定义 SQL 查询字符串,只选择 y 列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "order by x desc, y desc"; // 先按 x 列降序排序,x 相同则按 y 列降序排序

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableSort(sort0=[$1], sort1=[$0], dir0=[DESC], dir1=[DESC])\n" // 期望生成 EnumerableSort 节点,按第 1 列(x)降序排序,第 0 列(y)降序排序
        + "  EnumerableCalc(expr#0..1=[{inputs}], Y=[$t1], X=[$t0])\n" // 期望生成 EnumerableCalc 节点,输出 Y 列,保留 X 列用于排序
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "Y=c\n" // 定义预期的查询结果,按 x 降序、y 降序排序后的 y 值
        + "Y=c\n" // 第二行, x=2, y='c'
        + "Y=b\n" // 第三行, x=2, y='b'
        + "Y=b\n" // 第四行, x=1, y='b'
        + "Y=a\n"; // 第五行, x=1, y='a'

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testSortXDescYAscProjectY() { // 定义 testSortXDescYAscProjectY 测试方法,测试按 x 降序、y 升序排序但只输出 y 列
    final String sql = "select y\n" // 定义 SQL 查询字符串,只选择 y 列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "order by x desc, y"; // 先按 x 列降序排序,x 相同则按 y 列升序排序(默认是 ASC)

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableSort(sort0=[$1], sort1=[$0], dir0=[DESC], dir1=[ASC])\n" // 期望生成 EnumerableSort 节点,按第 1 列(x)降序排序,第 0 列(y)升序排序
        + "  EnumerableCalc(expr#0..1=[{inputs}], Y=[$t1], X=[$t0])\n" // 期望生成 EnumerableCalc 节点,输出 Y 列,保留 X 列用于排序
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "Y=b\n" // 定义预期的查询结果,按 x 降序、y 升序排序后的 y 值
        + "Y=c\n" // 第二行, x=2, y='c'
        + "Y=c\n" // 第三行, x=2, y='c'(重复)
        + "Y=a\n" // 第四行, x=1, y='a'
        + "Y=b\n"; // 第五行, x=1, y='b'

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testSortXAscYDescProjectY() { // 定义 testSortXAscYDescProjectY 测试方法,测试按 x 升序、y 降序排序但只输出 y 列
    final String sql = "select y\n" // 定义 SQL 查询字符串,只选择 y 列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "order by x, y desc"; // 先按 x 列升序排序(默认),x 相同则按 y 列降序排序

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableSort(sort0=[$1], sort1=[$0], dir0=[ASC], dir1=[DESC])\n" // 期望生成 EnumerableSort 节点,按第 1 列(x)升序排序,第 0 列(y)降序排序
        + "  EnumerableCalc(expr#0..1=[{inputs}], Y=[$t1], X=[$t0])\n" // 期望生成 EnumerableCalc 节点,输出 Y 列,保留 X 列用于排序
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "Y=b\n" // 定义预期的查询结果,按 x 升序、y 降序排序后的 y 值
        + "Y=a\n" // 第二行, x=1, y='a'
        + "Y=c\n" // 第三行, x=2, y='c'
        + "Y=c\n" // 第四行, x=2, y='c'(重复)
        + "Y=b\n"; // 第五行, x=2, y='b'

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests involving joins
// // 以下测试涉及连接操作

  @Test void testJoinProject() { // 定义 testJoinProject 测试方法,测试内连接和投影
    final String sql = "select t.y, v.z\n" // 定义 SQL 查询字符串,选择 t.y 和 v.z 列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值(别名 t)中查询
        + "  join " + VALUES3 + " on t.x = v.w"; // 与 VALUES3 定义的值(别名 v)进行内连接,连接条件是 t.x = v.w

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..3=[{inputs}], Y=[$t3], Z=[$t1])\n" // 期望生成 EnumerableCalc 节点,投影输出 Y 和 Z 列
        + "  EnumerableHashJoin(condition=[=($0, $2)], joinType=[inner])\n" // 期望生成 EnumerableHashJoin 节点,使用哈希连接,连接条件是第 0 列等于第 2 列
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n" // 左输入是 VALUES3 的数据(别名 v)
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 右输入是 VALUES2 的数据(别名 t)

    final String[] expectedResult = { // 定义预期的查询结果数组,内连接后的结果
        "Y=a; Z=a", // t.x=1, t.y='a' 连接 v.w=1, v.z='a'
        "Y=b; Z=a", // t.x=1, t.y='b' 连接 v.w=1, v.z='a'
        "Y=b; Z=b", // t.x=2, t.y='b' 连接 v.w=2, v.z='b'
        "Y=c; Z=b", // t.x=2, t.y='c' 连接 v.w=2, v.z='b'
        "Y=c; Z=b" // t.x=2, t.y='c' 连接 v.w=2, v.z='b'(重复)
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testJoinProjectAliasProject() { // 定义 testJoinProjectAliasProject 测试方法,测试带别名的连接和投影
    final String sql = "select r.z\n" // 定义 SQL 查询字符串,选择 r.z 列
        + "from (\n" // 使用子查询作为数据源
        + "  select *\n" // 选择所有列
        + "  from " + VALUES2 + "\n" // 从 VALUES2 定义的值(别名 t)中查询
        + "    join " + VALUES3 + " on t.x = v.w) as r"; // 与 VALUES3 定义的值(别名 v)进行内连接,连接条件是 t.x = v.w,结果别名为 r

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..3=[{inputs}], Z=[$t1])\n" // 期望生成 EnumerableCalc 节点,投影输出 Z 列
        + "  EnumerableHashJoin(condition=[=($0, $2)], joinType=[inner])\n" // 期望生成 EnumerableHashJoin 节点,使用哈希连接,连接条件是第 0 列等于第 2 列
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n" // 左输入是 VALUES3 的数据(别名 v)
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 右输入是 VALUES2 的数据(别名 t)

    final String[] expectedResult = { // 定义预期的查询结果数组,只输出 Z 列
        "Z=a", // t.x=1, t.y='a' 连接 v.w=1, v.z='a',输出 Z='a'
        "Z=a", // t.x=1, t.y='b' 连接 v.w=1, v.z='a',输出 Z='a'
        "Z=b", // t.x=2, t.y='b' 连接 v.w=2, v.z='b',输出 Z='b'
        "Z=b", // t.x=2, t.y='c' 连接 v.w=2, v.z='b',输出 Z='b'
        "Z=b" // t.x=2, t.y='c' 连接 v.w=2, v.z='b',输出 Z='b'(重复)
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests involving LIMIT/OFFSET
// // 以下测试涉及 LIMIT 和 OFFSET 操作

  @Test void testLimit() { // 定义 testLimit 测试方法,测试 LIMIT 限制返回行数
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where x = 1\n" // 添加过滤条件,只选择 x=1 的行
        + "limit 1"; // 限制只返回 1 行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableLimit(fetch=[1])\n" // 期望生成 EnumerableLimit 节点,fetch=1 表示只获取 1 行
        + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[=($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 期望生成 EnumerableCalc 节点,过滤 x=1 的行
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }"; // 子节点是 EnumerableValues,包含原始数据(注意:这里字符串不完整,原文件就是这样)

    final String expectedResult = "X=1; Y=a"; // 定义预期的查询结果,只返回第一行 x=1, y='a'

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testOrderByLimit() { // 定义 testOrderByLimit 测试方法,测试 ORDER BY 后使用 LIMIT
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "order by y\n" // 按 y 列排序(默认升序)
        + "limit 1"; // 限制只返回 1 行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableLimit(fetch=[1])\n" // 期望生成 EnumerableLimit 节点,fetch=1 表示只获取 1 行
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "X=1; Y=a\n"; // 定义预期的查询结果,按 y 排序后的第一行

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testOrderByOffset() { // 定义 testOrderByOffset 测试方法,测试 ORDER BY 后使用 OFFSET
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "order by y\n" // 按 y 列排序(默认升序)
        + "offset 2"; // 跳过前 2 行,从第 3 行开始返回

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableLimit(offset=[2])\n" // 期望生成 EnumerableLimit 节点,offset=2 表示跳过前 2 行
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "X=1; Y=b\n" // 定义预期的查询结果,跳过前 2 行后的剩余行
        + "X=2; Y=c\n" // 第四行
        + "X=2; Y=c\n"; // 第五行

    sql(sql).returns(expectedResult) // 执行 SQL 查询并验证结果是否按预期顺序返回
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests involving "complex" filters in WHERE clause
// // 以下测试涉及 WHERE 子句中的"复杂"过滤条件

  @Test void testFilterBetween() { // 定义 testFilterBetween 测试方法,测试 BETWEEN 过滤条件
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES4 + "\n" // 从 VALUES4 定义的值中查询
        + "where x between 3 and 4"; // 添加过滤条件,只选择 x 在 3 到 4 之间的行(包含边界)

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[Sarg[[3..4]]], expr#3=[SEARCH($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 期望生成 EnumerableCalc 节点,使用 Sarg(搜索参数)进行范围搜索
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 3, 'b' }, { 4, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String[] expectedResult = { // 定义预期的查询结果数组,x 在 3 到 4 之间的行
        "X=3; Y=b", // x=3 满足 between 3 and 4
        "X=4; Y=c" // x=4 满足 between 3 and 4
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testFilterIsIn() { // 定义 testFilterIsIn 测试方法,测试 IN 过滤条件
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES4 + "\n" // 从 VALUES4 定义的值中查询
        + "where x in (3, 4)"; // 添加过滤条件,只选择 x 在集合 (3, 4) 中的行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[Sarg[3, 4]], expr#3=[SEARCH($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 期望生成 EnumerableCalc 节点,使用 Sarg(搜索参数)进行 IN 搜索
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 3, 'b' }, { 4, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String[] expectedResult = { // 定义预期的查询结果数组,x 在 (3, 4) 集合中的行
        "X=3; Y=b", // x=3 满足 in (3, 4)
        "X=4; Y=c" // x=4 满足 in (3, 4)
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testFilterTrue() { // 定义 testFilterTrue 测试方法,测试 WHERE true 过滤条件
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where true"; // 添加过滤条件 true,表示不过滤,返回所有行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 期望直接生成 EnumerableValues 节点,没有过滤

    final String[] expectedResult = { // 定义预期的查询结果数组,返回所有行
        "X=1; Y=a", // 第一行
        "X=1; Y=b", // 第二行
        "X=2; Y=b", // 第三行
        "X=2; Y=c", // 第四行
        "X=2; Y=c" // 第五行
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testFilterFalse() { // 定义 testFilterFalse 测试方法,测试 WHERE false 过滤条件
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where false"; // 添加过滤条件 false,表示过滤掉所有行,不返回任何行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableValues(tuples=[[]])\n\n"; // 期望生成 EnumerableValues 节点,但只包含空元组

    final String expectedResult = ""; // 定义预期的查询结果,空字符串表示没有行

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testFilterOr() { // 定义 testFilterOr 测试方法,测试 OR 过滤条件
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where x = 1 or x = 2"; // 添加过滤条件,只选择 x=1 或 x=2 的行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[Sarg[1, 2]], expr#3=[SEARCH($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 期望生成 EnumerableCalc 节点,使用 Sarg(搜索参数)进行 OR 搜索(优化为 IN)
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String[] expectedResult = { // 定义预期的查询结果数组,x=1 或 x=2 的行(即所有行)
        "X=1; Y=a", // 第一行, x=1
        "X=1; Y=b", // 第三行, x=1
        "X=2; Y=b", // 第二行, x=2
        "X=2; Y=c", // 第四行, x=2
        "X=2; Y=c" // 第五行, x=2
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testFilterIsNotNull() { // 定义 testFilterIsNotNull 测试方法,测试 IS NOT NULL 过滤条件
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where x is not null"; // 添加过滤条件,只选择 x 不为 NULL 的行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 期望直接生成 EnumerableValues 节点(优化:所有值的 x 都不为 NULL)

    final String[] expectedResult = { // 定义预期的查询结果数组,所有行(因为所有 x 都不为 NULL)
        "X=1; Y=a", // 第一行
        "X=1; Y=b", // 第三行
        "X=2; Y=b", // 第二行
        "X=2; Y=c", // 第四行
        "X=2; Y=c" // 第五行
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testFilterIsNull() { // 定义 testFilterIsNull 测试方法,测试 IS NULL 过滤条件
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where x is null"; // 添加过滤条件,只选择 x 为 NULL 的行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableValues(tuples=[[]])\n\n"; // 期望生成 EnumerableValues 节点,但只包含空元组(因为没有 x 为 NULL 的行)

    final String expectedResult = ""; // 定义预期的查询结果,空字符串表示没有行

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests on more complex queries as UNION operands
// // 以下测试涉及更复杂的查询作为 UNION 操作数

  @Test void testUnionWithFilters() { // 定义 testUnionWithFilters 测试方法,测试带过滤条件的 UNION ALL
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x > 1\n" // 添加过滤条件,只选择 x>1 的行
        + " union all\n" // 使用 UNION ALL 合并结果,保留所有行(包括重复)
        + "select *\n" // 选择所有列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where x > 1"; // 添加过滤条件,只选择 x>1 的行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableUnion(all=[true])\n" // 期望生成 EnumerableUnion 节点,all=true 表示保留重复
        + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[>($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 左分支的过滤节点
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n" // 左分支的数据源
        + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[>($t0, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 右分支的过滤节点
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n"; // 右分支的数据源

    final String[] expectedResult = { // 定义预期的查询结果数组,两个分支 x>1 的行(保留重复)
        "X=2; Y=b", // 来自 VALUES1 的第二行
        "X=2; Y=b", // 来自 VALUES2 的第二行(重复)
        "X=2; Y=c", // 来自 VALUES2 的第四行
        "X=2; Y=c" // 来自 VALUES2 的第五行(重复)
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testUnionWithFiltersProject() { // 定义 testUnionWithFiltersProject 测试方法,测试带过滤条件和投影的 UNION
    final String sql = "select x\n" // 定义 SQL 查询字符串,只选择 x 列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x > 1\n" // 添加过滤条件,只选择 x>1 的行
        + " union\n" // 使用 UNION 合并结果,去除重复
        + "select x\n" // 只选择 x 列
        + "from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + "where x > 1"; // 添加过滤条件,只选择 x>1 的行

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableUnion(all=[false])\n" // 期望生成 EnumerableUnion 节点,all=false 表示去除重复
        + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[>($t0, $t2)], X=[$t0], $condition=[$t3])\n" // 左分支的过滤和投影节点
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n" // 左分支的数据源
        + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[>($t0, $t2)], X=[$t0], $condition=[$t3])\n" // 右分支的过滤和投影节点
        + "    EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }, { 1, 'b' }, { 2, 'c' }, { 2, 'c' }]])\n\n"; // 右分支的数据源

    final String expectedResult = "X=2"; // 定义预期的查询结果,去重后只有 x=2

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests involving arithmetic operators
// // 以下测试涉及算术运算符

  @Test void testArithmeticPlus() { // 定义 testArithmeticPlus 测试方法,测试加法运算符
    final String sql = "select x\n" // 定义 SQL 查询字符串,选择 x 列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x + 1 > 1"; // 添加过滤条件,使用加法运算 x+1 > 1

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t0, $t2)], expr#4=[>($t3, $t2)], X=[$t0], $condition=[$t4])\n" // 期望生成 EnumerableCalc 节点,计算 x+1 并判断是否大于 1
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String[] expectedResult = { // 定义预期的查询结果数组,x+1 > 1 的行(即 x > 0,所有行都满足)
        "X=1", // 第一行, 1+1=2 > 1
        "X=2" // 第二行, 2+1=3 > 1
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testArithmeticMinus() { // 定义 testArithmeticMinus 测试方法,测试减法运算符
    final String sql = "select x\n" // 定义 SQL 查询字符串,选择 x 列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x - 1 > 0"; // 添加过滤条件,使用减法运算 x-1 > 0

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[-($t0, $t2)], expr#4=[0], expr#5=[>($t3, $t4)], X=[$t0], $condition=[$t5])\n" // 期望生成 EnumerableCalc 节点,计算 x-1 并判断是否大于 0
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "X=2"; // 定义预期的查询结果,x-1 > 0 的行(即 x > 1,只有 x=2 满足)

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testArithmeticMul() { // 定义 testArithmeticMul 测试方法,测试乘法运算符
    final String sql = "select x\n" // 定义 SQL 查询字符串,选择 x 列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x * x > 1"; // 添加过滤条件,使用乘法运算 x*x > 1

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[*($t0, $t0)], expr#3=[1], expr#4=[>($t2, $t3)], X=[$t0], $condition=[$t4])\n" // 期望生成 EnumerableCalc 节点,计算 x*x 并判断是否大于 1
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String expectedResult = "X=2"; // 定义预期的查询结果,x*x > 1 的行(即 x > 1 或 x < -1,只有 x=2 满足)

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Test void testArithmeticDiv() { // 定义 testArithmeticDiv 测试方法,测试除法运算符
    final String sql = "select x\n" // 定义 SQL 查询字符串,选择 x 列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x / x = 1"; // 添加过滤条件,使用除法运算 x/x = 1

    final String plan = "PLAN=" // 定义预期的执行计划字符串
        + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[/($t0, $t0)], expr#3=[1], expr#4=[=($t2, $t3)], X=[$t0], $condition=[$t4])\n" // 期望生成 EnumerableCalc 节点,计算 x/x 并判断是否等于 1
        + "  EnumerableValues(tuples=[[{ 1, 'a' }, { 2, 'b' }]])\n\n"; // 子节点是 EnumerableValues,包含原始数据

    final String[] expectedResult = { // 定义预期的查询结果数组,x/x = 1 的行(所有非零 x 都满足)
        "X=1", // 第一行, 1/1=1
        "X=2" // 第二行, 2/2=1
    };

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  // Tests involving sub-queries (both correlated and non correlated)
// // 以下测试涉及子查询(包括相关子查询和非相关子查询)

  @Disabled("[CALCITE-2184] ClassCastException: RexSubQuery cannot be cast to RexLocalRef") // 使用 @Disabled 注解禁用此测试,因为存在类转换异常问题
  @Test void testFilterExists() { // 定义 testFilterExists 测试方法,测试 EXISTS 子查询
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES4 + "\n" // 从 VALUES4 定义的值中查询
        + "where exists (\n" // 使用 EXISTS 子查询作为过滤条件
        + "  select *\n" // 子查询选择所有列
        + "  from " + VALUES3 + "\n" // 从 VALUES3 定义的值中查询
        + "  where w < x\n" // 子查询的过滤条件,w < x(相关子查询,x 来自外层查询)
        + ")"; // 结束 EXISTS 子查询

    final String plan = "PLAN=todo\n\n"; // 定义预期的执行计划字符串,此处为 "todo" 表示待实现

    final String expectedResult = "X=2; Y=b\n" // 定义预期的查询结果,存在 w < x 的行
        + "X=2; Y=c\n" // 第二行
        + "X=3; Y=b\n" // 第三行
        + "X=4; Y=c"; // 第四行

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Disabled("[CALCITE-2184] ClassCastException: RexSubQuery cannot be cast to RexLocalRef") // 使用 @Disabled 注解禁用此测试,因为存在类转换异常问题
  @Test void testFilterNotExists() { // 定义 testFilterNotExists 测试方法,测试 NOT EXISTS 子查询
    final String sql = "select *\n" // 定义 SQL 查询字符串,选择所有列
        + "from " + VALUES4 + "\n" // 从 VALUES4 定义的值中查询
        + "where not exists (\n" // 使用 NOT EXISTS 子查询作为过滤条件
        + "  select *\n" // 子查询选择所有列
        + "  from " + VALUES3 + "\n" // 从 VALUES3 定义的值中查询
        + "  where w > x\n" // 子查询的过滤条件,w > x(相关子查询,x 来自外层查询)
        + ")"; // 结束 NOT EXISTS 子查询

    final String plan = "PLAN=todo\n\n"; // 定义预期的执行计划字符串,此处为 "todo" 表示待实现

    final String expectedResult = "X=1; Y=a"; // 定义预期的查询结果,不存在 w > x 的行(只有 x=1 满足)

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Disabled("[CALCITE-2184] ClassCastException: RexSubQuery cannot be cast to RexLocalRef") // 使用 @Disabled 注解禁用此测试,因为存在类转换异常问题
  @Test void testSubQueryAny() { // 定义 testSubQueryAny 测试方法,测试 ANY 子查询
    final String sql = "select x\n" // 定义 SQL 查询字符串,选择 x 列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x <= any (\n" // 使用 ANY 子查询作为过滤条件,x <= any (子查询结果)
        + "  select x\n" // 子查询选择 x 列
        + "  from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + ")"; // 结束 ANY 子查询

    final String plan = "PLAN=todo\n\n"; // 定义预期的执行计划字符串,此处为 "todo" 表示待实现

    final String expectedResult = "X=1\n" // 定义预期的查询结果,x <= any (子查询结果) 的行(所有行都满足)
        + "X=2"; // 第二行

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }

  @Disabled("[CALCITE-2184] ClassCastException: RexSubQuery cannot be cast to RexLocalRef") // 使用 @Disabled 注解禁用此测试,因为存在类转换异常问题
  @Test void testSubQueryAll() { // 定义 testSubQueryAll 测试方法,测试 ALL 子查询
    final String sql = "select x\n" // 定义 SQL 查询字符串,选择 x 列
        + "from " + VALUES1 + "\n" // 从 VALUES1 定义的值中查询
        + "where x <= all (\n" // 使用 ALL 子查询作为过滤条件,x <= all (子查询结果)
        + "  select x\n" // 子查询选择 x 列
        + "  from " + VALUES2 + "\n" // 从 VALUES2 定义的值中查询
        + ")"; // 结束 ALL 子查询

    final String plan = "PLAN=todo\n\n"; // 定义预期的执行计划字符串,此处为 "todo" 表示待实现

    final String expectedResult = "X=2"; // 定义预期的查询结果,x <= all (子查询结果) 的行(只有 x=2 满足,因为子查询的最小值是 1,最大值是 2,x=2 <= 所有值)

    sql(sql).returnsUnordered(expectedResult) // 执行 SQL 查询并验证结果是否返回(不关注顺序)
        .explainContains(plan); // 验证执行计划是否包含预期的计划字符串
  }
} // 结束 SparkAdapterTest 类定义
