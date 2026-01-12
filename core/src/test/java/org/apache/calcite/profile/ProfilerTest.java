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
package org.apache.calcite.profile; // 包声明：Apache Calcite性能分析器测试包
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，用于实现LINQ风格的枚举器
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，支持LINQ查询操作
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合
import org.apache.calcite.rel.metadata.NullSentinel; // 导入空值标记类，用于处理SQL NULL值
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言工具类，用于测试验证
import org.apache.calcite.test.Matchers; // 导入匹配器工具类，提供测试断言方法
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集类，用于表示列集合
import org.apache.calcite.util.JsonBuilder; // 导入JSON构建器，用于生成JSON格式输出
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法

import com.google.common.collect.HashMultimap; // 导入Google Guava哈希多值映射，支持一对多关系
import com.google.common.collect.ImmutableList; // 导入Google Guava不可变列表，保证线程安全
import com.google.common.collect.Multimap; // 导入Google Guava多值映射接口
import com.google.common.collect.Ordering; // 导入Google Guava排序器，用于自定义排序逻辑

import org.hamcrest.Matcher; // 导入Hamcrest匹配器接口，用于灵活断言
import org.junit.jupiter.api.Disabled; // 导入JUnit5禁用注解，标记跳过的测试方法
import org.junit.jupiter.api.Tag; // 导入JUnit5标签注解，用于测试分类
import org.junit.jupiter.api.Test; // 导入JUnit5测试注解，标记测试方法

import java.sql.PreparedStatement; // 导入JDBC预编译语句接口，用于执行SQL查询
import java.sql.ResultSet; // 导入JDBC结果集接口，表示查询返回的数据
import java.sql.ResultSetMetaData; // 导入JDBC结果集元数据接口，获取列信息
import java.sql.SQLException; // 导入JDBC异常类，处理SQL相关错误
import java.util.ArrayList; // 导入动态数组列表类，用于存储可变长数据集合
import java.util.Collection; // 导入集合接口，表示一组对象
import java.util.Comparator; // 导入比较器接口，用于自定义排序规则
import java.util.List; // 导入列表接口，表示有序集合
import java.util.Map; // 导入映射接口，表示键值对集合
import java.util.SortedSet; // 导入有序集合接口，保证元素排序
import java.util.TreeSet; // 导入树集合类，基于红黑树实现的有序集合
import java.util.function.Predicate; // 导入谓词函数接口，用于条件判断
import java.util.function.Supplier; // 导入供应者函数接口，用于延迟提供对象
import java.util.stream.Collectors; // 导入流收集器，用于流操作的终端操作

import static com.google.common.collect.ImmutableList.toImmutableList; // 静态导入：将流收集为不可变列表

import static org.hamcrest.CoreMatchers.is; // 静态导入：相等断言匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入：Hamcrest断言方法
import static org.hamcrest.Matchers.hasToString; // 静态导入：字符串匹配器

import static java.util.Objects.requireNonNull; // 静态导入：非空检查方法，验证对象不为null

/**
 * Unit tests for {@link Profiler}. // Profiler性能分析器的单元测试类
 * // 此类用于测试Apache Calcite的性能分析器功能，包括数据分布统计、行数统计、唯一性约束检测等
 * // 性能分析器是Calcite查询优化器的重要组成部分，通过分析数据统计信息帮助优化器选择最佳执行计划
 */ // 类作用：提供Profiler的全面测试用例，验证数据统计和分布分析的正确性
@Tag("slow") // 使用JUnit5标签注解标记为慢速测试，用于测试分类和选择性执行
class ProfilerTest { // 测试类声明：Profiler性能分析器的单元测试类
  @Test // JUnit5测试注解：标记这是一个测试方法
  void testProfileZeroRows() throws Exception { // 测试方法：测试分析零行结果集的性能统计信息
    final String sql = "select * from \"scott\".dept where false"; // 定义SQL查询：查询scott模式下的dept表，条件为false（永远不满足，返回0行）
    sql(sql).unordered( // 调用sql方法执行查询并使用unordered断言验证结果（不关注顺序）
        "{type:distribution,columns:[DEPTNO,DNAME,LOC],cardinality:0}", // 验证三个列的组合分布，基数为0（没有数据）
        "{type:distribution,columns:[DEPTNO,DNAME],cardinality:0}", // 验证DEPTNO和DNAME两列的组合分布，基数为0
        "{type:distribution,columns:[DEPTNO,LOC],cardinality:0}", // 验证DEPTNO和LOC两列的组合分布，基数为0
        "{type:distribution,columns:[DEPTNO],values:[],cardinality:0}", // 验证DEPTNO列的分布，值为空列表，基数为0
        "{type:distribution,columns:[DNAME,LOC],cardinality:0}", // 验证DNAME和LOC两列的组合分布，基数为0
        "{type:distribution,columns:[DNAME],values:[],cardinality:0}", // 验证DNAME列的分布，值为空列表，基数为0
        "{type:distribution,columns:[LOC],values:[],cardinality:0}", // 验证LOC列的分布，值为空列表，基数为0
        "{type:distribution,columns:[],cardinality:0}", // 验证空列集合的分布（表示整个表），基数为0
        "{type:rowCount,rowCount:0}", // 验证总行数为0
        "{type:unique,columns:[]}"); // 验证唯一性约束：空列集合（表示没有唯一约束）
  } // 测试方法结束：验证分析器能正确处理空结果集的情况

  @Test // JUnit5测试注解：标记这是一个测试方法
  void testProfileOneRow() throws Exception { // 测试方法：测试分析单行结果集的性能统计信息
    final String sql = "select * from \"scott\".dept where deptno = 10"; // 定义SQL查询：查询scott模式下的dept表中部门编号为10的记录（返回1行）
    sql(sql).unordered( // 调用sql方法执行查询并使用unordered断言验证结果（不关注顺序）
        "{type:distribution,columns:[DEPTNO,DNAME,LOC],cardinality:1}", // 验证三个列的组合分布，基数为1（只有一种组合）
        "{type:distribution,columns:[DEPTNO,DNAME],cardinality:1}", // 验证DEPTNO和DNAME两列的组合分布，基数为1
        "{type:distribution,columns:[DEPTNO,LOC],cardinality:1}", // 验证DEPTNO和LOC两列的组合分布，基数为1
        "{type:distribution,columns:[DEPTNO],values:[10],cardinality:1}", // 验证DEPTNO列的分布，值为[10]，基数为1
        "{type:distribution,columns:[DNAME,LOC],cardinality:1}", // 验证DNAME和LOC两列的组合分布，基数为1
        "{type:distribution,columns:[DNAME],values:[ACCOUNTING],cardinality:1}", // 验证DNAME列的分布，值为[ACCOUNTING]，基数为1
        "{type:distribution,columns:[LOC],values:[NEWYORK],cardinality:1}", // 验证LOC列的分布，值为[NEWYORK]，基数为1
        "{type:distribution,columns:[],cardinality:1}", // 验证空列集合的分布（表示整个表），基数为1
        "{type:rowCount,rowCount:1}", // 验证总行数为1
        "{type:unique,columns:[]}"); // 验证唯一性约束：空列集合（表示没有唯一约束，因为只有一行）
  } // 测试方法结束：验证分析器能正确处理单行数据的情况

  @Test // JUnit5测试注解：标记这是一个测试方法
  void testProfileTwoRows() throws Exception { // 测试方法：测试分析两行结果集的性能统计信息
    final String sql = "select * from \"scott\".dept where deptno in (10, 20)"; // 定义SQL查询：查询scott模式下的dept表中部门编号为10或20的记录（返回2行）
    sql(sql).unordered( // 调用sql方法执行查询并使用unordered断言验证结果（不关注顺序）
        "{type:distribution,columns:[DEPTNO,DNAME,LOC],cardinality:2}", // 验证三个列的组合分布，基数为2（有两种不同的组合）
        "{type:distribution,columns:[DEPTNO,DNAME],cardinality:2}", // 验证DEPTNO和DNAME两列的组合分布，基数为2
        "{type:distribution,columns:[DEPTNO,LOC],cardinality:2}", // 验证DEPTNO和LOC两列的组合分布，基数为2
        "{type:distribution,columns:[DEPTNO],values:[10,20],cardinality:2}", // 验证DEPTNO列的分布，值为[10,20]，基数为2
        "{type:distribution,columns:[DNAME,LOC],cardinality:2}", // 验证DNAME和LOC两列的组合分布，基数为2
        "{type:distribution,columns:[DNAME],values:[ACCOUNTING,RESEARCH],cardinality:2}", // 验证DNAME列的分布，值为[ACCOUNTING,RESEARCH]，基数为2
        "{type:distribution,columns:[LOC],values:[DALLAS,NEWYORK],cardinality:2}", // 验证LOC列的分布，值为[DALLAS,NEWYORK]，基数为2
        "{type:distribution,columns:[],cardinality:1}", // 验证空列集合的分布（表示整个表），基数为1（因为有两行数据）
        "{type:rowCount,rowCount:2}", // 验证总行数为2
        "{type:unique,columns:[DEPTNO]}", // 验证唯一性约束：DEPTNO列是唯一的（每个部门编号只出现一次）
        "{type:unique,columns:[DNAME]}", // 验证唯一性约束：DNAME列是唯一的（每个部门名称只出现一次）
        "{type:unique,columns:[LOC]}"); // 验证唯一性约束：LOC列是唯一的（每个位置只出现一次）
  } // 测试方法结束：验证分析器能正确处理两行数据的情况，并识别出唯一性约束

  @Test // JUnit5测试注解：标记这是一个测试方法
  void testProfileScott() throws Exception { // 测试方法：测试分析Scott示例数据库中emp和dept表连接查询的性能统计信息
    final String sql = "select * from \"scott\".emp\n" // 定义SQL查询：从scott模式的emp表
        + "join \"scott\".dept on emp.deptno = dept.deptno"; // 与dept表连接，条件是员工部门编号等于部门部门编号（返回14行）
    sql(sql) // 调用sql方法执行查询
        .where(statistic -> // 使用where方法过滤统计信息，只保留满足条件的统计
            !(statistic instanceof Profiler.Distribution) // 条件1：如果不是分布统计（如函数依赖、唯一性约束等），则保留
                || ((Profiler.Distribution) statistic).cardinality < 14 // 条件2：如果是分布统计，基数必须小于14（避免过多统计信息）
                && ((Profiler.Distribution) statistic).minimal) // 条件3：且该分布是最小的（minimal标记）
        .unordered( // 使用unordered断言验证结果（不关注顺序）
            "{type:distribution,columns:[COMM,DEPTNO0],cardinality:5}", // 验证佣金和部门编号0的组合分布，基数为5
            "{type:distribution,columns:[COMM,DEPTNO],cardinality:5}", // 验证佣金和部门编号的组合分布，基数为5
            "{type:distribution,columns:[COMM,DNAME],cardinality:5}", // 验证佣金和部门名称的组合分布，基数为5
            "{type:distribution,columns:[COMM,LOC],cardinality:5}", // 验证佣金和位置的组合分布，基数为5
            "{type:distribution,columns:[COMM],values:[0.00,300.00,500.00,1400.00],cardinality:5,nullCount:10}", // 验证佣金列的分布，有4个非空值，10个空值，基数为5
            "{type:distribution,columns:[DEPTNO,DEPTNO0],cardinality:3}", // 验证部门编号和部门编号0的组合分布，基数为3
            "{type:distribution,columns:[DEPTNO,DNAME],cardinality:3}", // 验证部门编号和部门名称的组合分布，基数为3
            "{type:distribution,columns:[DEPTNO,LOC],cardinality:3}", // 验证部门编号和位置的组合分布，基数为3
            "{type:distribution,columns:[DEPTNO0,DNAME],cardinality:3}", // 验证部门编号0和部门名称的组合分布，基数为3
            "{type:distribution,columns:[DEPTNO0,LOC],cardinality:3}", // 验证部门编号0和位置的组合分布，基数为3
            "{type:distribution,columns:[DEPTNO0],values:[10,20,30],cardinality:3}", // 验证部门编号0列的分布，值为[10,20,30]，基数为3
            "{type:distribution,columns:[DEPTNO],values:[10,20,30],cardinality:3}", // 验证部门编号列的分布，值为[10,20,30]，基数为3
            "{type:distribution,columns:[DNAME,LOC],cardinality:3}", // 验证部门名称和位置的组合分布，基数为3
            "{type:distribution,columns:[DNAME],values:[ACCOUNTING,RESEARCH,SALES],cardinality:3}", // 验证部门名称列的分布，值为[ACCOUNTING,RESEARCH,SALES]，基数为3
            "{type:distribution,columns:[HIREDATE,COMM],cardinality:5}", // 验证雇佣日期和佣金的组合分布，基数为5
            "{type:distribution,columns:[HIREDATE],values:[1980-12-17,1981-01-05,1981-02-04,1981-02-20,1981-02-22,1981-06-09,1981-09-08,1981-09-28,1981-11-17,1981-12-03,1982-01-23,1987-04-19,1987-05-23],cardinality:13}", // 验证雇佣日期列的分布，有13个不同的日期，基数为13
            "{type:distribution,columns:[JOB,COMM],cardinality:5}", // 验证职位和佣金的组合分布，基数为5
            "{type:distribution,columns:[JOB,DEPTNO0],cardinality:9}", // 验证职位和部门编号0的组合分布，基数为9
            "{type:distribution,columns:[JOB,DEPTNO],cardinality:9}", // 验证职位和部门编号的组合分布，基数为9
            "{type:distribution,columns:[JOB,DNAME],cardinality:9}", // 验证职位和部门名称的组合分布，基数为9
            "{type:distribution,columns:[JOB,LOC],cardinality:9}", // 验证职位和位置的组合分布，基数为9
            "{type:distribution,columns:[JOB,MGR,DEPTNO0],cardinality:10}", // 验证职位、经理和部门编号0的三列组合分布，基数为10
            "{type:distribution,columns:[JOB,MGR,DEPTNO],cardinality:10}", // 验证职位、经理和部门编号的三列组合分布，基数为10
            "{type:distribution,columns:[JOB,MGR,DNAME],cardinality:10}", // 验证职位、经理和部门名称的三列组合分布，基数为10
            "{type:distribution,columns:[JOB,MGR,LOC],cardinality:10}", // 验证职位、经理和位置的三列组合分布，基数为10
            "{type:distribution,columns:[JOB,MGR],cardinality:8}", // 验证职位和经理的组合分布，基数为8
            "{type:distribution,columns:[JOB,SAL],cardinality:12}", // 验证职位和薪水的组合分布，基数为12
            "{type:distribution,columns:[JOB],values:[ANALYST,CLERK,MANAGER,PRESIDENT,SALESMAN],cardinality:5}", // 验证职位列的分布，值为[ANALYST,CLERK,MANAGER,PRESIDENT,SALESMAN]，基数为5
            "{type:distribution,columns:[LOC],values:[CHICAGO,DALLAS,NEWYORK],cardinality:3}", // 验证位置列的分布，值为[CHICAGO,DALLAS,NEWYORK]，基数为3
            "{type:distribution,columns:[MGR,COMM],cardinality:5}", // 验证经理和佣金的组合分布，基数为5
            "{type:distribution,columns:[MGR,DEPTNO0],cardinality:9}", // 验证经理和部门编号0的组合分布，基数为9
            "{type:distribution,columns:[MGR,DEPTNO],cardinality:9}", // 验证经理和部门编号的组合分布，基数为9
            "{type:distribution,columns:[MGR,DNAME],cardinality:9}", // 验证经理和部门名称的组合分布，基数为9
            "{type:distribution,columns:[MGR,LOC],cardinality:9}", // 验证经理和位置的组合分布，基数为9
            "{type:distribution,columns:[MGR,SAL],cardinality:12}", // 验证经理和薪水的组合分布，基数为12
            "{type:distribution,columns:[MGR],values:[7566,7698,7782,7788,7839,7902],cardinality:7,nullCount:1}", // 验证经理列的分布，有6个不同的经理编号，1个空值（总裁没有经理），基数为7
            "{type:distribution,columns:[SAL,COMM],cardinality:5}", // 验证薪水和佣金的组合分布，基数为5
            "{type:distribution,columns:[SAL,DEPTNO0],cardinality:12}", // 验证薪水和部门编号0的组合分布，基数为12
            "{type:distribution,columns:[SAL,DEPTNO],cardinality:12}", // 验证薪水和部门编号的组合分布，基数为12
            "{type:distribution,columns:[SAL,DNAME],cardinality:12}", // 验证薪水和部门名称的组合分布，基数为12
            "{type:distribution,columns:[SAL,LOC],cardinality:12}", // 验证薪水和位置的组合分布，基数为12
            "{type:distribution,columns:[SAL],values:[800.00,950.00,1100.00,1250.00,1300.00,1500.00,1600.00,2450.00,2850.00,2975.00,3000.00,5000.00],cardinality:12}", // 验证薪水列的分布，有12个不同的薪水值，基数为12
            "{type:distribution,columns:[],cardinality:1}", // 验证空列集合的分布（表示整个表），基数为1
            "{type:fd,columns:[DEPTNO0],dependentColumn:DEPTNO}", // 验证函数依赖：部门编号0决定部门编号（DEPTNO0 -> DEPTNO）
            "{type:fd,columns:[DEPTNO0],dependentColumn:DNAME}", // 验证函数依赖：部门编号0决定部门名称（DEPTNO0 -> DNAME）
            "{type:fd,columns:[DEPTNO0],dependentColumn:LOC}", // 验证函数依赖：部门编号0决定位置（DEPTNO0 -> LOC）
            "{type:fd,columns:[DEPTNO],dependentColumn:DEPTNO0}", // 验证函数依赖：部门编号决定部门编号0（DEPTNO -> DEPTNO0）
            "{type:fd,columns:[DEPTNO],dependentColumn:DNAME}", // 验证函数依赖：部门编号决定部门名称（DEPTNO -> DNAME）
            "{type:fd,columns:[DEPTNO],dependentColumn:LOC}", // 验证函数依赖：部门编号决定位置（DEPTNO -> LOC）
            "{type:fd,columns:[DNAME],dependentColumn:DEPTNO0}", // 验证函数依赖：部门名称决定部门编号0（DNAME -> DEPTNO0）
            "{type:fd,columns:[DNAME],dependentColumn:DEPTNO}", // 验证函数依赖：部门名称决定部门编号（DNAME -> DEPTNO）
            "{type:fd,columns:[DNAME],dependentColumn:LOC}", // 验证函数依赖：部门名称决定位置（DNAME -> LOC）
            "{type:fd,columns:[JOB],dependentColumn:COMM}", // 验证函数依赖：职位决定佣金（JOB -> COMM，只有销售员有佣金）
            "{type:fd,columns:[LOC],dependentColumn:DEPTNO0}", // 验证函数依赖：位置决定部门编号0（LOC -> DEPTNO0）
            "{type:fd,columns:[LOC],dependentColumn:DEPTNO}", // 验证函数依赖：位置决定部门编号（LOC -> DEPTNO）
            "{type:fd,columns:[LOC],dependentColumn:DNAME}", // 验证函数依赖：位置决定部门名称（LOC -> DNAME）
            "{type:fd,columns:[SAL],dependentColumn:DEPTNO0}", // 验证函数依赖：薪水决定部门编号0（SAL -> DEPTNO0）
            "{type:fd,columns:[SAL],dependentColumn:DEPTNO}", // 验证函数依赖：薪水决定部门编号（SAL -> DEPTNO）
            "{type:fd,columns:[SAL],dependentColumn:DNAME}", // 验证函数依赖：薪水决定部门名称（SAL -> DNAME）
            "{type:fd,columns:[SAL],dependentColumn:JOB}", // 验证函数依赖：薪水决定职位（SAL -> JOB）
            "{type:fd,columns:[SAL],dependentColumn:LOC}", // 验证函数依赖：薪水决定位置（SAL -> LOC）
            "{type:fd,columns:[SAL],dependentColumn:MGR}", // 验证函数依赖：薪水决定经理（SAL -> MGR）
            "{type:rowCount,rowCount:14}", // 验证总行数为14
            "{type:unique,columns:[EMPNO]}", // 验证唯一性约束：员工编号列是唯一的
            "{type:unique,columns:[ENAME]}", // 验证唯一性约束：员工姓名列是唯一的
            "{type:unique,columns:[HIREDATE,DEPTNO0]}", // 验证唯一性约束：雇佣日期和部门编号0的组合是唯一的
            "{type:unique,columns:[HIREDATE,DEPTNO]}", // 验证唯一性约束：雇佣日期和部门编号的组合是唯一的
            "{type:unique,columns:[HIREDATE,DNAME]}", // 验证唯一性约束：雇佣日期和部门名称的组合是唯一的
            "{type:unique,columns:[HIREDATE,LOC]}", // 验证唯一性约束：雇佣日期和位置的组合是唯一的
            "{type:unique,columns:[HIREDATE,SAL]}", // 验证唯一性约束：雇佣日期和薪水的组合是唯一的
            "{type:unique,columns:[JOB,HIREDATE]}"); // 验证唯一性约束：职位和雇佣日期的组合是唯一的
  } // 测试方法结束：验证分析器能正确处理连接查询并识别出各种统计信息和约束

  /** As {@link #testProfileScott()}, but prints only the most surprising // 方法注释：与testProfileScott()类似，但只打印最令人惊讶的分布
   * distributions. */ // 方法注释：测试分析器识别最令人惊讶的数据分布（surprise值最高）
    @Test // JUnit5测试注解：标记这是一个测试方法
    void testProfileScott2() throws Exception { // 测试方法：测试分析Scott数据并输出最令人惊讶的分布统计
      scott().factory(Fluid.SIMPLE_FACTORY).unordered( // 调用scott()方法获取Fluid对象，设置简单分析器工厂，验证结果顺序无关
          "{type:distribution,columns:[COMM],values:[0.00,300.00,500.00,1400.00],cardinality:5,nullCount:10,expectedCardinality:14,surprise:0.474}", // 验证佣金列分布：实际基数5，期望基数14，惊讶度0.474（表示分布比预期更集中）
          "{type:distribution,columns:[DEPTNO,DEPTNO0],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门编号组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO,DNAME],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门编号和名称组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO,LOC],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门编号和位置组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO0,DNAME],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门编号0和名称组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO0,LOC],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门编号0和位置组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO0],values:[10,20,30],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号0列：实际基数3，期望基数14，惊讶度0.647（非常集中）
          "{type:distribution,columns:[DEPTNO],values:[10,20,30],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号列：实际基数3，期望基数14，惊讶度0.647（非常集中）
          "{type:distribution,columns:[DNAME,LOC],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门名称和位置组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DNAME],values:[ACCOUNTING,RESEARCH,SALES],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门名称列：实际基数3，期望基数14，惊讶度0.647（非常集中）
          "{type:distribution,columns:[HIREDATE,COMM],cardinality:5,expectedCardinality:12.683,surprise:0.434}", // 验证雇佣日期和佣金组合分布：实际基数5，期望基数12.68，惊讶度0.434
          "{type:distribution,columns:[HIREDATE],values:[1980-12-17,1981-01-05,1981-02-04,1981-02-20,1981-02-22,1981-06-09,1981-09-08,1981-09-28,1981-11-17,1981-12-03,1982-01-23,1987-04-19,1987-05-23],cardinality:13,expectedCardinality:14,surprise:0.0370}", // 验证雇佣日期列：实际基数13，期望基数14，惊讶度0.037（接近预期）
          "{type:distribution,columns:[JOB],values:[ANALYST,CLERK,MANAGER,PRESIDENT,SALESMAN],cardinality:5,expectedCardinality:14,surprise:0.474}", // 验证职位列：实际基数5，期望基数14，惊讶度0.474
          "{type:distribution,columns:[LOC],values:[CHICAGO,DALLAS,NEWYORK],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证位置列：实际基数3，期望基数14，惊讶度0.647（非常集中）
          "{type:distribution,columns:[MGR,COMM],cardinality:5,expectedCardinality:11.675,surprise:0.400}", // 验证经理和佣金组合分布：实际基数5，期望基数11.68，惊讶度0.400
          "{type:distribution,columns:[MGR],values:[7566,7698,7782,7788,7839,7902],cardinality:7,nullCount:1,expectedCardinality:14,surprise:0.333}", // 验证经理列：实际基数7，期望基数14，惊讶度0.333
          "{type:distribution,columns:[SAL,COMM],cardinality:5,expectedCardinality:12.580,surprise:0.431}", // 验证薪水和佣金组合分布：实际基数5，期望基数12.58，惊讶度0.431
          "{type:distribution,columns:[SAL],values:[800.00,950.00,1100.00,1250.00,1300.00,1500.00,1600.00,2450.00,2850.00,2975.00,3000.00,5000.00],cardinality:12,expectedCardinality:14,surprise:0.0769}", // 验证薪水列：实际基数12，期望基数14，惊讶度0.077
          "{type:distribution,columns:[],cardinality:1,expectedCardinality:1,surprise:0}"); // 验证空列集合分布：实际基数1，期望基数1，惊讶度0（完全符合预期）
    } // 测试方法结束：验证分析器能正确计算并输出最令人惊讶的分布统计（surprise值最高的分布）
  /** As {@link #testProfileScott2()}, but uses the breadth-first profiler. // 方法注释：与testProfileScott2()类似，但使用广度优先分析器
   * Results should be the same, but are slightly different (extra EMPNO // 方法注释：结果应该相同，但略有不同（额外的EMPNO
   * and ENAME distributions). */ // 方法注释：和ENAME分布）
    @Test // JUnit5测试注解：标记这是一个测试方法
    void testProfileScott3() throws Exception { // 测试方法：测试使用广度优先分析器分析Scott数据
      scott().factory(Fluid.BETTER_FACTORY).unordered( // 调用scott()方法获取Fluid对象，设置更好的分析器工厂（广度优先），验证结果顺序无关
          "{type:distribution,columns:[COMM],values:[0.00,300.00,500.00,1400.00],cardinality:5,nullCount:10,expectedCardinality:14,surprise:0.474}", // 验证佣金列分布：实际基数5，期望基数14，惊讶度0.474
          "{type:distribution,columns:[DEPTNO,DEPTNO0,DNAME,LOC],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证四个部门相关列的组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO,DEPTNO0],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证两个部门编号列的组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO,DNAME],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门编号和名称的组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO,LOC],cardinality:3,expectedCardinality:7.2698,surprise:0.416}", // 验证部门编号和位置的组合分布：实际基数3，期望基数7.27，惊讶度0.416
          "{type:distribution,columns:[DEPTNO0,DNAME,LOC],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号0、名称和位置的三列组合分布：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[DEPTNO0],values:[10,20,30],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号0列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[DEPTNO],values:[10,20,30],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[DNAME],values:[ACCOUNTING,RESEARCH,SALES],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门名称列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[EMPNO],values:[7369,7499,7521,7566,7654,7698,7782,7788,7839,7844,7876,7900,7902,7934],cardinality:14,expectedCardinality:14,surprise:0}", // 验证员工编号列：实际基数14，期望基数14，惊讶度0（完全符合预期，每个员工编号唯一）
          "{type:distribution,columns:[ENAME],values:[ADAMS,ALLEN,BLAKE,CLARK,FORD,JAMES,JONES,KING,MARTIN,MILLER,SCOTT,SMITH,TURNER,WARD],cardinality:14,expectedCardinality:14,surprise:0}", // 验证员工姓名列：实际基数14，期望基数14，惊讶度0（完全符合预期，每个员工姓名唯一）
          "{type:distribution,columns:[HIREDATE],values:[1980-12-17,1981-01-05,1981-02-04,1981-02-20,1981-02-22,1981-06-09,1981-09-08,1981-09-28,1981-11-17,1981-12-03,1982-01-23,1987-04-19,1987-05-23],cardinality:13,expectedCardinality:14,surprise:0.0370}", // 验证雇佣日期列：实际基数13，期望基数14，惊讶度0.037
          "{type:distribution,columns:[JOB],values:[ANALYST,CLERK,MANAGER,PRESIDENT,SALESMAN],cardinality:5,expectedCardinality:14,surprise:0.474}", // 验证职位列：实际基数5，期望基数14，惊讶度0.474
          "{type:distribution,columns:[LOC],values:[CHICAGO,DALLAS,NEWYORK],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证位置列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[MGR],values:[7566,7698,7782,7788,7839,7902],cardinality:7,nullCount:1,expectedCardinality:14,surprise:0.333}", // 验证经理列：实际基数7，期望基数14，惊讶度0.333
          "{type:distribution,columns:[SAL],values:[800.00,950.00,1100.00,1250.00,1300.00,1500.00,1600.00,2450.00,2850.00,2975.00,3000.00,5000.00],cardinality:12,expectedCardinality:14,surprise:0.0769}", // 验证薪水列：实际基数12，期望基数14，惊讶度0.077
          "{type:distribution,columns:[],cardinality:1,expectedCardinality:1,surprise:0}"); // 验证空列集合分布：实际基数1，期望基数1，惊讶度0
    } // 测试方法结束：验证广度优先分析器能正确分析数据并包含额外的EMPNO和ENAME分布
  
    /** As {@link #testProfileScott3()}, but uses the breadth-first profiler // 方法注释：与testProfileScott3()类似，但使用广度优先分析器
     * and deems everything uninteresting. Only first-level combinations (those // 方法注释：并认为所有内容都不有趣。只计算第一级组合（那些
     * consisting of a single column) are computed. */ // 方法注释：由单个列组成的）
    @Test // JUnit5测试注解：标记这是一个测试方法
    void testProfileScott4() throws Exception { // 测试方法：测试使用不感兴趣的分析器，只计算单列分布
      scott().factory(Fluid.INCURIOUS_PROFILER_FACTORY).unordered( // 调用scott()方法获取Fluid对象，设置不感兴趣的分析器工厂（只计算单列），验证结果顺序无关
          "{type:distribution,columns:[COMM],values:[0.00,300.00,500.00,1400.00],cardinality:5,nullCount:10,expectedCardinality:14,surprise:0.474}", // 验证佣金列分布：实际基数5，期望基数14，惊讶度0.474
          "{type:distribution,columns:[DEPTNO0,DNAME,LOC],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号0、名称和位置的三列组合分布：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[DEPTNO0],values:[10,20,30],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号0列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[DEPTNO],values:[10,20,30],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门编号列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[DNAME],values:[ACCOUNTING,RESEARCH,SALES],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证部门名称列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[EMPNO],values:[7369,7499,7521,7566,7654,7698,7782,7788,7839,7844,7876,7900,7902,7934],cardinality:14,expectedCardinality:14,surprise:0}", // 验证员工编号列：实际基数14，期望基数14，惊讶度0
          "{type:distribution,columns:[ENAME],values:[ADAMS,ALLEN,BLAKE,CLARK,FORD,JAMES,JONES,KING,MARTIN,MILLER,SCOTT,SMITH,TURNER,WARD],cardinality:14,expectedCardinality:14,surprise:0}", // 验证员工姓名列：实际基数14，期望基数14，惊讶度0
          "{type:distribution,columns:[HIREDATE],values:[1980-12-17,1981-01-05,1981-02-04,1981-02-20,1981-02-22,1981-06-09,1981-09-08,1981-09-28,1981-11-17,1981-12-03,1982-01-23,1987-04-19,1987-05-23],cardinality:13,expectedCardinality:14,surprise:0.0370}", // 验证雇佣日期列：实际基数13，期望基数14，惊讶度0.037
          "{type:distribution,columns:[JOB],values:[ANALYST,CLERK,MANAGER,PRESIDENT,SALESMAN],cardinality:5,expectedCardinality:14,surprise:0.474}", // 验证职位列：实际基数5，期望基数14，惊讶度0.474
          "{type:distribution,columns:[LOC],values:[CHICAGO,DALLAS,NEWYORK],cardinality:3,expectedCardinality:14,surprise:0.647}", // 验证位置列：实际基数3，期望基数14，惊讶度0.647
          "{type:distribution,columns:[MGR],values:[7566,7698,7782,7788,7839,7902],cardinality:7,nullCount:1,expectedCardinality:14,surprise:0.333}", // 验证经理列：实际基数7，期望基数14，惊讶度0.333
          "{type:distribution,columns:[SAL],values:[800.00,950.00,1100.00,1250.00,1300.00,1500.00,1600.00,2450.00,2850.00,2975.00,3000.00,5000.00],cardinality:12,expectedCardinality:14,surprise:0.0769}", // 验证薪水列：实际基数12，期望基数14，惊讶度0.077
          "{type:distribution,columns:[],cardinality:1,expectedCardinality:1,surprise:0}"); // 验证空列集合分布：实际基数1，期望基数1，惊讶度0
    } // 测试方法结束：验证不感兴趣的分析器只计算单列分布，不计算多列组合
  /** As {@link #testProfileScott3()}, but uses the breadth-first profiler. */ // 方法注释：与testProfileScott3()类似，但使用广度优先分析器
  @Disabled // JUnit5禁用注解：标记此测试方法被禁用（不执行）
  @Test // JUnit5测试注解：标记这是一个测试方法
  void testProfileScott5() throws Exception { // 测试方法：测试使用完整的分析器工厂分析Scott数据（已禁用）
    scott().factory(Fluid.PROFILER_FACTORY).unordered( // 调用scott()方法获取Fluid对象，设置完整的分析器工厂，验证结果顺序无关
        "{type:distribution,columns:[COMM],values:[0.00,300.00,500.00,1400.00],cardinality:5,nullCount:10,expectedCardinality:14.0,surprise:0.473}", // 验证佣金列分布：实际基数5，期望基数14.0，惊讶度0.473
        "{type:distribution,columns:[DEPTNO,DEPTNO0,DNAME,LOC],cardinality:3,expectedCardinality:7.269,surprise:0.415}", // 验证四个部门相关列的组合分布：实际基数3，期望基数7.269，惊讶度0.415
        "{type:distribution,columns:[DEPTNO,DEPTNO0],cardinality:3,expectedCardinality:7.269,surprise:0.415}", // 验证两个部门编号列的组合分布：实际基数3，期望基数7.269，惊讶度0.415
        "{type:distribution,columns:[DEPTNO,DNAME],cardinality:3,expectedCardinality:7.269,surprise:0.415}", // 验证部门编号和名称的组合分布：实际基数3，期望基数7.269，惊讶度0.415
        "{type:distribution,columns:[DEPTNO,LOC],cardinality:3,expectedCardinality:7.269,surprise:0.415}", // 验证部门编号和位置的组合分布：实际基数3，期望基数7.269，惊讶度0.415
        "{type:distribution,columns:[DEPTNO0,DNAME,LOC],cardinality:3,expectedCardinality:14.0,surprise:0.647}", // 验证部门编号0、名称和位置的三列组合分布：实际基数3，期望基数14.0，惊讶度0.647
        "{type:distribution,columns:[DEPTNO0],values:[10,20,30],cardinality:3,expectedCardinality:14.0,surprise:0.647}", // 验证部门编号0列：实际基数3，期望基数14.0，惊讶度0.647
        "{type:distribution,columns:[DEPTNO],values:[10,20,30],cardinality:3,expectedCardinality:14.0,surprise:0.647}", // 验证部门编号列：实际基数3，期望基数14.0，惊讶度0.647
        "{type:distribution,columns:[DNAME],values:[ACCOUNTING,RESEARCH,SALES],cardinality:3,expectedCardinality:14.0,surprise:0.647}", // 验证部门名称列：实际基数3，期望基数14.0，惊讶度0.647
        "{type:distribution,columns:[EMPNO],values:[7369,7499,7521,7566,7654,7698,7782,7788,7839,7844,7876,7900,7902,7934],cardinality:14,expectedCardinality:14.0,surprise:0}", // 验证员工编号列：实际基数14，期望基数14.0，惊讶度0
        "{type:distribution,columns:[ENAME],values:[ADAMS,ALLEN,BLAKE,CLARK,FORD,JAMES,JONES,KING,MARTIN,MILLER,SCOTT,SMITH,TURNER,WARD],cardinality:14,expectedCardinality:14.0,surprise:0}", // 验证员工姓名列：实际基数14，期望基数14.0，惊讶度0
        "{type:distribution,columns:[HIREDATE],values:[1980-12-17,1981-01-05,1981-02-04,1981-02-20,1981-02-22,1981-06-09,1981-09-08,1981-09-28,1981-11-17,1981-12-03,1982-01-23,1987-04-19,1987-05-23],cardinality:13,expectedCardinality:14.0,surprise:0.037}", // 验证雇佣日期列：实际基数13，期望基数14.0，惊讶度0.037
        "{type:distribution,columns:[JOB],values:[ANALYST,CLERK,MANAGER,PRESIDENT,SALESMAN],cardinality:5,expectedCardinality:14.0,surprise:0.473}", // 验证职位列：实际基数5，期望基数14.0，惊讶度0.473
        "{type:distribution,columns:[LOC],values:[CHICAGO,DALLAS,NEWYORK],cardinality:3,expectedCardinality:14.0,surprise:0.647}", // 验证位置列：实际基数3，期望基数14.0，惊讶度0.647
        "{type:distribution,columns:[MGR],values:[7566,7698,7782,7788,7839,7902],cardinality:7,nullCount:1,expectedCardinality:14.0,surprise:0.333}", // 验证经理列：实际基数7，期望基数14.0，惊讶度0.333
        "{type:distribution,columns:[SAL],values:[800.00,950.00,1100.00,1250.00,1300.00,1500.00,1600.00,2450.00,2850.00,2975.00,3000.00,5000.00],cardinality:12,expectedCardinality:14.0,surprise:0.076}", // 验证薪水列：实际基数12，期望基数14.0，惊讶度0.076
        "{type:distribution,columns:[],cardinality:1,expectedCardinality:1.0,surprise:0}"); // 验证空列集合分布：实际基数1，期望基数1.0，惊讶度0
  } // 测试方法结束：验证完整分析器工厂的分析结果（已禁用）

  /** Profiles a star-join query on the Foodmart schema using the breadth-first // 方法注释：使用广度优先分析器分析Foodmart模式上的星型连接查询
   * profiler. */ // 方法注释：测试分析器在大规模数据集上的性能
  @Disabled // JUnit5禁用注解：标记此测试方法被禁用（不执行）
  @Test // JUnit5测试注解：标记这是一个测试方法
  void testProfileFoodmart() throws Exception { // 测试方法：测试分析Foodmart销售数据的星型连接查询（已禁用）
    foodmart().factory(Fluid.PROFILER_FACTORY).unordered( // 调用foodmart()方法获取Fluid对象，设置完整的分析器工厂，验证结果顺序无关
        "{type:distribution,columns:[brand_name],cardinality:111,expectedCardinality:86837.0,surprise:0.997}", // 验证品牌名称列：实际基数111，期望基数86837，惊讶度0.997（非常集中）
        "{type:distribution,columns:[cases_per_pallet],values:[5,6,7,8,9,10,11,12,13,14],cardinality:10,expectedCardinality:86837.0,surprise:0.999}", // 验证每托盘箱数列：实际基数10，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[day_of_month],cardinality:30,expectedCardinality:86837.0,surprise:0.999}", // 验证月份天数列：实际基数30，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[fiscal_period],values:[],cardinality:1,nullCount:86837,expectedCardinality:86837.0,surprise:0.999}", // 验证财政周期列：实际基数1，空值数86837，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[low_fat],values:[false,true],cardinality:2,expectedCardinality:86837.0,surprise:0.999}", // 验证低脂列：实际基数2，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[month_of_year],values:[1,2,3,4,5,6,7,8,9,10,11,12],cardinality:12,expectedCardinality:86837.0,surprise:0.999}", // 验证月份列：实际基数12，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[product_category],cardinality:45,expectedCardinality:86837.0,surprise:0.998}", // 验证产品类别列：实际基数45，期望基数86837，惊讶度0.998
        "{type:distribution,columns:[product_class_id0,product_subcategory,product_category,product_department,product_family],cardinality:102,expectedCardinality:86837.0,surprise:0.997}", // 验证产品相关五列组合：实际基数102，期望基数86837，惊讶度0.997
        "{type:distribution,columns:[product_class_id0],cardinality:102,expectedCardinality:86837.0,surprise:0.997}", // 验证产品类别ID0列：实际基数102，期望基数86837，惊讶度0.997
        "{type:distribution,columns:[product_class_id],cardinality:102,expectedCardinality:86837.0,surprise:0.997}", // 验证产品类别ID列：实际基数102，期望基数86837，惊讶度0.997
        "{type:distribution,columns:[product_department],cardinality:22,expectedCardinality:86837.0,surprise:0.999}", // 验证产品部门列：实际基数22，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[product_family],values:[Drink,Food,Non-Consumable],cardinality:3,expectedCardinality:86837.0,surprise:0.999}", // 验证产品系列列：实际基数3，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[product_subcategory],cardinality:102,expectedCardinality:86837.0,surprise:0.997}", // 验证产品子类别列：实际基数102，期望基数86837，惊讶度0.997
        "{type:distribution,columns:[quarter],values:[Q1,Q2,Q3,Q4],cardinality:4,expectedCardinality:86837.0,surprise:0.999}", // 验证季度列：实际基数4，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[recyclable_package],values:[false,true],cardinality:2,expectedCardinality:86837.0,surprise:0.999}", // 验证可回收包装列：实际基数2，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[store_cost,fiscal_period],cardinality:10601,nullCount:86724,expectedCardinality:10.0,surprise:0.998}", // 验证商店成本和财政周期组合：实际基数10601，空值数86724，期望基数10，惊讶度0.998
        "{type:distribution,columns:[store_cost,low_fat],cardinality:17673,expectedCardinality:20.0,surprise:0.997}", // 验证商店成本和低脂组合：实际基数17673，期望基数20，惊讶度0.997
        "{type:distribution,columns:[store_cost,product_family],cardinality:19453,expectedCardinality:30.0,surprise:0.996}", // 验证商店成本和产品系列组合：实际基数19453，期望基数30，惊讶度0.996
        "{type:distribution,columns:[store_cost,quarter],cardinality:29590,expectedCardinality:40.0,surprise:0.997}", // 验证商店成本和季度组合：实际基数29590，期望基数40，惊讶度0.997
        "{type:distribution,columns:[store_cost,recyclable_package],cardinality:17847,expectedCardinality:20.0,surprise:0.997}", // 验证商店成本和可回收包装组合：实际基数17847，期望基数20，惊讶度0.997
        "{type:distribution,columns:[store_cost,the_year],cardinality:10944,expectedCardinality:10.0,surprise:0.998}", // 验证商店成本和年份组合：实际基数10944，期望基数10，惊讶度0.998
        "{type:distribution,columns:[store_cost],cardinality:10,expectedCardinality:86837.0,surprise:0.999}", // 验证商店成本列：实际基数10，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[store_id],values:[2,3,6,7,11,13,14,15,16,17,22,23,24],cardinality:13,expectedCardinality:86837.0,surprise:0.999}", // 验证商店ID列：实际基数13，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[store_sales],cardinality:21,expectedCardinality:86837.0,surprise:0.999}", // 验证商店销售额列：实际基数21，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[the_day],values:[Friday,Monday,Saturday,Sunday,Thursday,Tuesday,Wednesday],cardinality:7,expectedCardinality:86837.0,surprise:0.999}", // 验证星期列：实际基数7，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[the_month],values:[April,August,December,February,January,July,June,March,May,November,October,September],cardinality:12,expectedCardinality:86837.0,surprise:0.999}", // 验证月份名称列：实际基数12，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[the_year],values:[1997],cardinality:1,expectedCardinality:86837.0,surprise:0.999}", // 验证年份列：实际基数1，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[unit_sales],values:[1.0000,2.0000,3.0000,4.0000,5.0000,6.0000],cardinality:6,expectedCardinality:86837.0,surprise:0.999}", // 验证单位销售列：实际基数6，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[units_per_case],cardinality:36,expectedCardinality:86837.0,surprise:0.999}", // 验证每箱单位数列：实际基数36，期望基数86837，惊讶度0.999
        "{type:distribution,columns:[week_of_year],cardinality:52,expectedCardinality:86837.0,surprise:0.998}", // 验证一年中的周数列：实际基数52，期望基数86837，惊讶度0.998
        "{type:distribution,columns:[],cardinality:1,expectedCardinality:1.0,surprise:0}"); // 验证空列集合分布：实际基数1，期望基数1.0，惊讶度0
  } // 测试方法结束：验证分析器在大规模星型连接查询上的性能（已禁用）

  /** Tests // 方法注释：测试
   * {@link org.apache.calcite.profile.ProfilerImpl.SurpriseQueue}. */ // 方法注释：ProfilerImpl.SurpriseQueue惊讶度队列的实现
    @Test // JUnit5测试注解：标记这是一个测试方法
    void testSurpriseQueue() { // 测试方法：测试惊讶度队列（SurpriseQueue）的行为
      ProfilerImpl.SurpriseQueue q = new ProfilerImpl.SurpriseQueue(4, 3); // 创建惊讶度队列：容量为4，阈值为3
      assertThat(q.offer(2), is(true)); // 向队列添加值2，期望返回true（添加成功）
      assertThat(q, hasToString("min: 2.0, contents: [2.0]")); // 验证队列状态：最小值为2.0，内容为[2.0]
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      assertThat(q.offer(4), is(true)); // 向队列添加值4，期望返回true（添加成功）
      assertThat(q, hasToString("min: 2.0, contents: [2.0, 4.0]")); // 验证队列状态：最小值为2.0，内容为[2.0, 4.0]
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      // Since we're in the warm-up period, a value lower than the minimum is // 注释：由于我们处于预热期，低于最小值的值
      // accepted. // 注释：被接受
      assertThat(q.offer(1), is(true)); // 向队列添加值1（低于当前最小值2），期望返回true（预热期接受）
      assertThat(q, hasToString("min: 1.0, contents: [2.0, 4.0, 1.0]")); // 验证队列状态：最小值为1.0，内容为[2.0, 4.0, 1.0]
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      assertThat(q.offer(5), is(true)); // 向队列添加值5，期望返回true（添加成功）
      assertThat(q, hasToString("min: 1.0, contents: [4.0, 1.0, 5.0]")); // 验证队列状态：最小值为1.0，内容为[4.0, 1.0, 5.0]
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      assertThat(q.offer(3), is(true)); // 向队列添加值3，期望返回true（添加成功）
      assertThat(q, hasToString("min: 1.0, contents: [1.0, 5.0, 3.0]")); // 验证队列状态：最小值为1.0，内容为[1.0, 5.0, 3.0]
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      // Duplicate entry // 注释：重复条目
      assertThat(q.offer(5), is(true)); // 向队列添加值5（重复值），期望返回true（添加成功）
      assertThat(q, hasToString("min: 3.0, contents: [5.0, 3.0, 5.0]")); // 验证队列状态：最小值为3.0，内容为[5.0, 3.0, 5.0]
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      // Now that the list is full, a value below the minimum is refused. // 注释：现在列表已满，低于最小值的值被拒绝
      // "offer" returns false, and the value is not added to the queue. // 注释："offer"返回false，值不会被添加到队列中
      // Thus the median never decreases. // 注释：因此中位数永远不会降低
      assertThat(q.offer(2), is(false)); // 向队列添加值2（低于当前最小值3），期望返回false（已满，拒绝）
      assertThat(q, hasToString("min: 3.0, contents: [5.0, 3.0, 5.0]")); // 验证队列状态：最小值为3.0，内容为[5.0, 3.0, 5.0]（未改变）
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      // Same applies for a value equal to the minimum. // 注释：同样适用于等于最小值的值
      assertThat(q.offer(3), is(false)); // 向队列添加值3（等于当前最小值3），期望返回false（已满，拒绝）
      assertThat(q, hasToString("min: 3.0, contents: [5.0, 3.0, 5.0]")); // 验证队列状态：最小值为3.0，内容为[5.0, 3.0, 5.0]（未改变）
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
  
      // Add a value that is above the minimum. // 注释：添加一个高于最小值的值
      assertThat(q.offer(4.5), is(true)); // 向队列添加值4.5（高于当前最小值3），期望返回true（添加成功）
      assertThat(q, hasToString("min: 3.0, contents: [3.0, 5.0, 4.5]")); // 验证队列状态：最小值为3.0，内容为[3.0, 5.0, 4.5]
      assertThat(q.isValid(), is(true)); // 验证队列状态有效
    } // 测试方法结束：验证惊讶度队列在预热期和满载后的行为
  private Fluid scott() { // 私有辅助方法：创建用于测试Scott数据的Fluid对象
    final String sql = "select * from \"scott\".emp\n" // 定义SQL查询：从scott模式的emp表
        + "join \"scott\".dept on emp.deptno = dept.deptno"; // 与dept表连接，条件是员工部门编号等于部门部门编号
    return sql(sql) // 调用sql方法创建Fluid对象
        .where(Fluid.STATISTIC_PREDICATE) // 设置过滤条件：只保留满足STATISTIC_PREDICATE的统计信息
        .sort(Fluid.ORDERING.reverse()) // 设置排序：使用ORDERING的反向排序（惊讶度从高到低）
        .limit(30) // 设置限制：最多返回30条统计信息
        .project(Fluid.EXTENDED_COLUMNS); // 设置投影：使用扩展列（包含expectedCardinality和surprise字段）
  } // 方法结束：返回配置好的Fluid对象用于Scott数据测试

  private Fluid foodmart() { // 私有辅助方法：创建用于测试Foodmart数据的Fluid对象
    final String sql = "select \"s\".*, \"p\".*, \"t\".*, \"pc\".*\n" // 定义SQL查询：选择所有表的所有列
        + "from \"foodmart\".\"sales_fact_1997\" as \"s\"\n" // 从foodmart模式的销售事实表1997（别名s）
        + "join \"foodmart\".\"product\" as \"p\" using (\"product_id\")\n" // 与产品表连接（别名p），使用product_id
        + "join \"foodmart\".\"time_by_day\" as \"t\" using (\"time_id\")\n" // 与时间表连接（别名t），使用time_id
        + "join \"foodmart\".\"product_class\" as \"pc\"\n" // 与产品类别表连接（别名pc）
        + "  on \"p\".\"product_class_id\" = \"pc\".\"product_class_id\"\n"; // 连接条件：产品类别ID相等
    return sql(sql) // 调用sql方法创建Fluid对象
        .config(CalciteAssert.Config.JDBC_FOODMART) // 设置配置：使用Foodmart的JDBC配置
        .where(Fluid.STATISTIC_PREDICATE) // 设置过滤条件：只保留满足STATISTIC_PREDICATE的统计信息
        .sort(Fluid.ORDERING.reverse()) // 设置排序：使用ORDERING的反向排序（惊讶度从高到低）
        .limit(30) // 设置限制：最多返回30条统计信息
        .project(Fluid.EXTENDED_COLUMNS); // 设置投影：使用扩展列（包含expectedCardinality和surprise字段）
  } // 方法结束：返回配置好的Fluid对象用于Foodmart数据测试

  private static Fluid sql(String sql) { // 私有静态辅助方法：为给定SQL创建基本的Fluid对象
    return new Fluid(CalciteAssert.Config.SCOTT, sql, Fluid.SIMPLE_FACTORY, // 创建Fluid对象：使用Scott配置、指定SQL、简单分析器工厂
        s -> true, null, -1, Fluid.DEFAULT_COLUMNS); // 设置参数：接受所有统计（s->true）、无比较器、无限制（-1）、使用默认列
  } // 方法结束：返回基本配置的Fluid对象

  /** Fluid interface for writing profiler test cases. */ // 内部类注释：用于编写性能分析器测试用例的流式接口

    private static class Fluid { // 私有静态内部类：提供流式API用于配置和执行性能分析器测试

      static final Supplier<Profiler> SIMPLE_FACTORY = SimpleProfiler::new; // 静态常量：简单分析器工厂，使用SimpleProfiler类创建分析器实例

  

      static final Supplier<Profiler> BETTER_FACTORY = // 静态常量：更好的分析器工厂，使用ProfilerImpl类创建

          () -> new ProfilerImpl(600, 200, p -> true); // 参数：最大列数600，阈值200，接受所有谓词（p -> true）

  

      static final Ordering<Profiler.Statistic> ORDERING = // 静态常量：统计信息的排序器，用于自定义排序规则

          new Ordering<Profiler.Statistic>() { // 匿名内部类：继承Ordering并实现compare方法

            public int compare(Profiler.Statistic left, // 方法：比较两个统计信息

                Profiler.Statistic right) { // 参数：左边和右边的统计信息对象

              int c = left.getClass().getSimpleName() // 步骤1：先按类名的简单名称排序

                  .compareTo(right.getClass().getSimpleName()); // 比较类名字符串

              if (c == 0 // 如果类名相同（都是同类型的统计信息）

                  && left instanceof Profiler.Distribution // 且左边是分布统计

                  && right instanceof Profiler.Distribution) { // 且右边也是分布统计

                final Profiler.Distribution d0 = (Profiler.Distribution) left; // 将左边转换为分布对象

                final Profiler.Distribution d1 = (Profiler.Distribution) right; // 将右边转换为分布对象

                c = Double.compare(d0.surprise(), d1.surprise()); // 步骤2：按惊讶度从低到高排序

                if (c == 0) { // 如果惊讶度也相同

                  c = d0.columns.toString().compareTo(d1.columns.toString()); // 步骤3：按列集合的字符串表示排序

                }

              }

              return c; // 返回比较结果：负数表示left<right，0表示相等，正数表示left>right

            }

          }; // 匿名内部类结束：定义了统计信息的排序规则

  

      static final Predicate<Profiler.Statistic> STATISTIC_PREDICATE = // 静态常量：统计信息过滤谓词，用于筛选感兴趣的统计信息

          statistic -> statistic instanceof Profiler.Distribution // 条件1：必须是分布统计

              && (((Profiler.Distribution) statistic).columns.size() < 2 // 条件2：要么是单列分布（列数<2）

              || ((Profiler.Distribution) statistic).surprise() > 0.4D) // 要么是惊讶度大于0.4的多列分布

              && ((Profiler.Distribution) statistic).minimal; // 条件3：且该分布是最小的（minimal标记）

  

      static final List<String> DEFAULT_COLUMNS = // 静态常量：默认的输出列列表，定义JSON输出中包含的字段

          ImmutableList.of("type", "distribution", "columns", "cardinality", // 包含：类型、分布、列、基数

              "values", "nullCount", "dependentColumn", "rowCount"); // 以及：值、空值数、依赖列、行数

  

      static final List<String> EXTENDED_COLUMNS = // 静态常量：扩展的输出列列表，在默认列基础上增加额外字段

          ImmutableList.<String>builder().addAll(DEFAULT_COLUMNS) // 使用构建器，先添加所有默认列

              .add("expectedCardinality", "surprise") // 再添加：期望基数和惊讶度字段

              .build(); // 构建不可变列表

  

      private static final Supplier<Profiler> PROFILER_FACTORY = () -> // 静态常量：完整的分析器工厂，使用ProfilerImpl类创建

          new ProfilerImpl(7500, 100, p -> { // 参数：最大列数7500，阈值100，自定义谓词

  

            final Profiler.Distribution distribution = // 获取左侧空间的分布统计

                p.left.distribution(); // 调用left.distribution()方法获取分布对象

            if (distribution == null) { // 如果分布为null（表示该空间尚未被评估）

              // We don't have a distribution yet, because this space // 注释：我们还没有分布统计，因为这个空间

              // has not yet been evaluated. Let's do it anyway. // 注释：尚未被评估。让我们无论如何都评估它

              return true; // 返回true，表示接受这个空间进行评估

            }

            return distribution.surprise() >= 0.3D; // 返回惊讶度是否>=0.3，作为是否接受的判断条件

          }); // Lambda表达式结束：定义了是否评估某个空间的判断逻辑

  

      private static final Supplier<Profiler> INCURIOUS_PROFILER_FACTORY = // 静态常量：不感兴趣的分析器工厂，只计算单列分布

          () -> new ProfilerImpl(10, 200, p -> false); // 参数：最大列数10，阈值200，拒绝所有多列组合（p -> false）

  

      private final String sql; // 实例变量：要执行的SQL查询语句
    private final List<String> columns; // 实例变量：输出列列表，定义JSON输出中包含的字段
    private final Comparator<Profiler.Statistic> comparator; // 实例变量：统计信息比较器，用于自定义排序规则
    private final int limit; // 实例变量：结果数量限制，-1表示无限制
    private final Predicate<Profiler.Statistic> predicate; // 实例变量：统计信息过滤谓词，用于筛选统计信息
    private final Supplier<Profiler> factory; // 实例变量：分析器工厂，用于创建Profiler实例
    private final CalciteAssert.Config config; // 实例变量：Calcite断言配置，包含数据库连接信息

    Fluid(CalciteAssert.Config config, String sql, Supplier<Profiler> factory, // 构造方法：创建Fluid对象，初始化所有配置参数
        Predicate<Profiler.Statistic> predicate, // 参数：统计信息过滤谓词
        Comparator<Profiler.Statistic> comparator, int limit, // 参数：比较器、结果数量限制
        List<String> columns) { // 参数：输出列列表
      this.sql = requireNonNull(sql, "sql"); // 赋值：SQL语句，使用requireNonNull确保非空
      this.factory = requireNonNull(factory, "factory"); // 赋值：分析器工厂，使用requireNonNull确保非空
      this.columns = ImmutableList.copyOf(columns); // 赋值：输出列列表，创建不可变副本
      this.predicate = requireNonNull(predicate, "predicate"); // 赋值：过滤谓词，使用requireNonNull确保非空
      this.comparator = comparator; // 赋值：比较器，null表示按JSON表示排序
      this.limit = limit; // 赋值：结果数量限制
      this.config = config; // 赋值：Calcite配置
    } // 构造方法结束：初始化所有实例变量

    Fluid config(CalciteAssert.Config config) { // 方法：设置Calcite配置，返回新的Fluid对象
      return new Fluid(config, sql, factory, predicate, comparator, limit, // 创建新Fluid对象，只修改config参数
          columns); // 其他参数保持不变
    } // 方法结束：返回配置了新config的Fluid对象

    Fluid factory(Supplier<Profiler> factory) { // 方法：设置分析器工厂，返回新的Fluid对象
      return new Fluid(config, sql, factory, predicate, comparator, limit, // 创建新Fluid对象，只修改factory参数
          columns); // 其他参数保持不变
    } // 方法结束：返回配置了新factory的Fluid对象

    Fluid project(List<String> columns) { // 方法：设置输出列列表，返回新的Fluid对象
      return new Fluid(config, sql, factory, predicate, comparator, limit, // 创建新Fluid对象，只修改columns参数
          columns); // 其他参数保持不变
    } // 方法结束：返回配置了新columns的Fluid对象

    Fluid sort(Ordering<Profiler.Statistic> comparator) { // 方法：设置排序器，返回新的Fluid对象
      return new Fluid(config, sql, factory, predicate, comparator, limit, // 创建新Fluid对象，只修改comparator参数
          columns); // 其他参数保持不变
    } // 方法结束：返回配置了新comparator的Fluid对象

    Fluid limit(int limit) { // 方法：设置结果数量限制，返回新的Fluid对象
      return new Fluid(config, sql, factory, predicate, comparator, limit, // 创建新Fluid对象，只修改limit参数
          columns); // 其他参数保持不变
    } // 方法结束：返回配置了新limit的Fluid对象

    Fluid where(Predicate<Profiler.Statistic> predicate) { // 方法：设置过滤谓词，返回新的Fluid对象
      return new Fluid(config, sql, factory, predicate, comparator, limit, // 创建新Fluid对象，只修改predicate参数
          columns); // 其他参数保持不变
    } // 方法结束：返回配置了新predicate的Fluid对象

    Fluid unordered(String... lines) throws Exception { // 方法：使用无序匹配验证结果，返回当前Fluid对象
      return check(Matchers.equalsUnordered(lines)); // 调用check方法，使用无序相等匹配器验证结果
    } // 方法结束：返回this以支持链式调用

    public Fluid check(final Matcher<Iterable<String>> matcher) // 方法：使用指定的匹配器验证结果，返回当前Fluid对象
        throws Exception { // 可能抛出异常
      CalciteAssert.that(config) // 使用CalciteAssert创建测试断言
          .doWithConnection(c -> { // 获取数据库连接并执行操作
            try (PreparedStatement s = c.prepareStatement(sql)) { // 创建预编译语句，执行SQL查询
              final ResultSetMetaData m = s.getMetaData(); // 获取结果集元数据，包含列信息
              final List<Profiler.Column> columns = new ArrayList<>(); // 创建列列表，存储所有列的定义
              final int columnCount = m.getColumnCount(); // 获取列总数
              for (int i = 0; i < columnCount; i++) { // 遍历所有列
                columns.add(new Profiler.Column(i, m.getColumnLabel(i + 1))); // 创建Profiler.Column对象并添加到列表
              }

              // Create an initial group for each table in the query. // 注释：为查询中的每个表创建初始组
              // Columns in the same table will tend to have the same // 注释：同一表中的列通常具有相同的
              // cardinality as the table, and as the table's primary key. // 注释：基数，与表及其主键相同
              final Multimap<String, Integer> groups = HashMultimap.create(); // 创建多值映射，按表名分组列索引
              for (int i = 0; i < m.getColumnCount(); i++) { // 遍历所有列
                groups.put(m.getTableName(i + 1), i); // 将列索引添加到对应表名的组中
              }
              final SortedSet<ImmutableBitSet> initialGroups = // 创建有序集合，存储初始列组
                  new TreeSet<>(); // 使用TreeSet保证排序
              for (Collection<Integer> integers : groups.asMap().values()) { // 遍历每个表的列索引集合
                initialGroups.add(ImmutableBitSet.of(integers)); // 创建不可变位集并添加到初始组集合中
              }
              final Profiler p = factory.get(); // 使用工厂创建Profiler实例
              final Enumerable<List<Comparable>> rows = getRows(s); // 从结果集获取可枚举的行数据
              final Profiler.Profile profile = // 调用分析器分析数据，生成性能配置文件
                  p.profile(rows, columns, initialGroups); // 参数：行数据、列定义、初始列组
              final List<Profiler.Statistic> statistics = // 获取所有统计信息
                  profile.statistics().stream().filter(predicate) // 过滤统计信息，只保留满足谓词的
                      .collect(toImmutableList()); // 收集为不可变列表

              // If no comparator specified, use the function that converts to // 注释：如果没有指定比较器，使用转换为
              // JSON strings // 注释：JSON字符串的函数
              final StatisticToJson toJson = new StatisticToJson(); // 创建统计信息到JSON的转换器
              Ordering<Profiler.Statistic> comp = comparator != null // 如果提供了比较器
                  ? Ordering.from(comparator) // 使用提供的比较器
                  : Ordering.natural().onResultOf(toJson::apply); // 否则使用JSON字符串的自然排序
              ImmutableList<Profiler.Statistic> statistics2 = // 对统计信息进行排序
                  comp.immutableSortedCopy(statistics); // 创建不可变的排序列表
              if (limit >= 0 && limit < statistics2.size()) { // 如果设置了限制且限制小于统计信息数量
                statistics2 = statistics2.subList(0, limit); // 截取前limit个统计信息
              }

              final List<String> strings = // 将统计信息转换为JSON字符串列表
                  statistics2.stream().map(toJson::apply) // 使用转换器将每个统计信息转换为JSON字符串
                      .collect(Collectors.toList()); // 收集为列表
              assertThat(strings, matcher); // 使用匹配器验证结果
            } catch (SQLException e) { // 捕获SQL异常
              throw TestUtil.rethrow(e); // 重新抛出异常
            }
          }); // Lambda表达式结束：数据库连接操作
      return this; // 返回当前Fluid对象以支持链式调用
    } // 方法结束：执行查询、分析数据并验证结果

    private Enumerable<List<Comparable>> getRows(final PreparedStatement s) { // 私有方法：从预编译语句获取可枚举的行数据
      return new AbstractEnumerable<List<Comparable>>() { // 创建抽象可枚举类的匿名子类
        public Enumerator<List<Comparable>> enumerator() { // 方法：返回枚举器，用于遍历行数据
          try {
            final ResultSet r = s.executeQuery(); // 执行查询，获取结果集
            return getListEnumerator(r, r.getMetaData().getColumnCount()); // 创建并返回列表枚举器
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        } // 方法结束：返回枚举器对象
      }; // 匿名类结束：定义了如何创建枚举器
    } // 方法结束：返回可枚举的行数据

    private Enumerator<List<Comparable>> getListEnumerator( // 私有方法：创建列表枚举器，用于遍历结果集
        final ResultSet r, final int columnCount) { // 参数：结果集对象、列数量
      return new Enumerator<List<Comparable>>() { // 创建枚举器的匿名子类
        final Comparable[] values = new Comparable[columnCount]; // 实例变量：存储当前行的值数组

        public List<Comparable> current() { // 方法：返回当前行的数据列表
          for (int i = 0; i < columnCount; i++) { // 遍历所有列
            try {
              final Comparable value = (Comparable) r.getObject(i + 1); // 获取当前列的值（列索引从1开始）
              values[i] = NullSentinel.mask(value); // 使用NullSentinel处理NULL值
            } catch (SQLException e) { // 捕获SQL异常
              throw TestUtil.rethrow(e); // 重新抛出异常
            }
          }
          return ImmutableList.copyOf(values); // 返回不可变的值列表
        } // 方法结束：返回当前行的数据列表

        public boolean moveNext() { // 方法：移动到下一行
          try {
            return r.next(); // 调用ResultSet.next()移动指针，返回是否有下一行
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        } // 方法结束：返回是否有下一行

        public void reset() { // 方法：重置枚举器（空实现）
        } // 方法结束：不做任何操作

        public void close() { // 方法：关闭枚举器和结果集
          try {
            r.close(); // 关闭结果集，释放资源
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        } // 方法结束：关闭资源
      }; // 匿名类结束：定义了如何遍历结果集
    } // 方法结束：返回枚举器对象

    /** Returns a function that converts a statistic to a JSON string. */ // 内部类注释：返回将统计信息转换为JSON字符串的函数
    private class StatisticToJson { // 私有内部类：将统计信息转换为JSON字符串
      final JsonBuilder jb = new JsonBuilder(); // 实例变量：JSON构建器，用于生成JSON格式输出

      public String apply(Profiler.Statistic statistic) { // 方法：将统计信息转换为JSON字符串
        Object map = statistic.toMap(jb); // 步骤1：将统计信息转换为Map对象
        if (map instanceof Map) { // 如果转换结果是Map
          @SuppressWarnings("unchecked") // 抑制未检查类型转换警告
          final Map<String, Object> map1 = (Map) map; // 将map转换为Map<String, Object>
          map1.keySet().retainAll(Fluid.this.columns); // 步骤2：只保留在columns列表中定义的键
        }
        final String json = jb.toJsonString(map); // 步骤3：将Map转换为JSON字符串
        return json.replace("\n", "") // 步骤4：移除换行符
            .replace(" ", "") // 移除空格
            .replace("\"", ""); // 移除双引号，生成紧凑格式
      } // 方法结束：返回紧凑的JSON字符串（无空格和换行）
    } // 内部类结束：定义了统计信息到JSON的转换逻辑
  } // Fluid内部类结束：提供流式API用于性能分析器测试
} // ProfilerTest类结束：Profiler的单元测试类
