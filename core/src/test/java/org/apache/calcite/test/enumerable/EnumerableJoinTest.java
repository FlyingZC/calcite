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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.test.enumerable; // 定义包名，表示这个测试类属于org.apache.calcite.test.enumerable包

import org.apache.calcite.adapter.enumerable.EnumerableMergeJoin; // 导入EnumerableMergeJoin类，用于可枚举的归并连接实现
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入EnumerableRules类，包含可枚举关系表达式转换规则
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema类，用于通过反射创建Schema
import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性配置类
import org.apache.calcite.config.Lex; // 导入Lex类，用于配置SQL词法分析策略
import org.apache.calcite.interpreter.Bindables; // 导入Bindables类，用于可绑定表扫描规则
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，定义关系优化规划器接口
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType枚举，定义连接类型（INNER、LEFT、RIGHT、FULL等）
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于在执行过程中插入自定义逻辑
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable类，包含标准SQL操作符
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert工具类，用于测试Calcite功能
import org.apache.calcite.test.schemata.hr.HierarchySchema; // 导入HierarchySchema类，用于层次结构测试Schema
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HrSchema类，用于人力资源测试Schema
import org.apache.calcite.test.schemata.hr.HrSchemaBig; // 导入HrSchemaBig类，用于大型人力资源测试Schema

import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.util.Arrays; // 导入Arrays工具类，用于数组操作
import java.util.function.Consumer; // 导入Consumer函数式接口，用于消费操作

/**
 * Unit tests for the different Enumerable Join implementations.
 */
// 类注释：EnumerableJoinTest类用于测试不同的Enumerable Join实现
// 该类包含各种连接类型的测试用例，包括INNER JOIN、LEFT JOIN、ANTI JOIN、SEMI JOIN、ASOF JOIN等
// 还测试了归并连接（Merge Join）的特殊场景，如复合键、NULL值处理、递归查询等
// 通过这些测试验证Calcite连接操作的正确性和性能
class EnumerableJoinTest { // 测试类定义，用于测试可枚举连接的各种实现

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2968">[CALCITE-2968]
   * New AntiJoin relational expression</a>. */
  // 方法注释：测试等值反连接（Anti Join）功能
  // Anti Join是一种特殊的连接类型，返回左表中不满足连接条件的行
  // 相当于SQL中的NOT EXISTS或NOT IN子查询
  // 该测试验证了Calcite对Anti Join的支持，对应JIRA问题CALCITE-2968
  @Test void equiAntiJoin() { // 测试方法，使用等值条件的反连接
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema作为测试Schema
        .withRel( // 设置关系表达式构建器
            // Retrieve departments without employees. Equivalent SQL:
            //   SELECT d.deptno, d.name FROM depts d
            //   WHERE NOT EXISTS (SELECT 1 FROM emps e WHERE e.deptno = d.deptno)
            // 注释说明：该查询返回没有员工的部门，使用NOT EXISTS实现反连接
            builder -> builder // 开始构建关系表达式
                .scan("s", "depts").as("d") // 扫描depts表，别名为d，作为左表
                .scan("s", "emps").as("e") // 扫描emps表，别名为e，作为右表
                .antiJoin( // 创建反连接操作，返回左表中不匹配的行
                    builder.equals( // 构建等值条件
                        builder.field(2, "d", "deptno"), // 引用左表d的deptno字段，2表示输入数量为2
                        builder.field(2, "e", "deptno"))) // 引用右表e的deptno字段，连接条件为d.deptno = e.deptno
                .project( // 投影操作，选择需要的列
                    builder.field("deptno"), // 选择deptno列
                    builder.field("name")) // 选择name列
                .build()) // 构建完成，生成关系表达式
        .returnsUnordered( // 验证返回结果，不要求顺序
            "deptno=30; name=Marketing", // 期望结果1：市场部门没有员工
            "deptno=40; name=HR"); // 期望结果2：人力资源部门没有员工
  } // 方法结束

  @Test void asofJoinTest() { // 测试方法，测试ASOF连接功能
    // ASOF Join是一种特殊的连接，用于匹配最近的时间点或序列点
    // 常用于时间序列分析，找到满足条件的最近记录
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema
        .withRel( // 设置关系表达式构建器
            // select d.deptno, e.empid from emps e left asof join depts d
            // match_condition d.name <= e.name
            // on d.deptno = e.deptno
            // SQL注释：说明这是一个LEFT ASOF JOIN，在部门编号相等的前提下，找到名称字典序小于等于员工名称的最近部门
            builder -> builder // 开始构建关系表达式
                .scan("s", "depts").as("d") // 扫描depts表，别名为d
                .scan("s", "emps").as("e") // 扫描emps表，别名为e
                .asofJoin(JoinRelType.LEFT_ASOF, // 创建LEFT ASOF连接，即使没有匹配也保留左表行
                    builder.equals( // 连接条件：部门编号相等
                        builder.field(2, "d", "deptno"), // 左表部门编号
                        builder.field(2, "e", "deptno")), // 右表部门编号
                    builder.lessThan( // 匹配条件：部门名称小于等于员工名称
                        builder.field(2, "d", "name"), // 左表名称字段
                        builder.field(2, "e", "name"))) // 右表名称字段
                .project( // 投影操作
                    builder.field("deptno"), // 选择部门编号
                    builder.field("e", "name"), // 选择员工名称
                    builder.field("empid")) // 选择员工ID
                .build()) // 构建完成
        .returnsUnordered( // 验证返回结果
            "deptno=10; name=Theodore; empid=110", // 结果1：员工Theodore匹配到部门10
            "deptno=30; name=null; empid=null", // 结果2：没有匹配的部门，返回null
            "deptno=40; name=null; empid=null"); // 结果3：没有匹配的部门，返回null

    tester(false, new HrSchema()) // 创建第二个测试器
        .withRel( // 设置关系表达式构建器
            // select d.deptno, e.empid from emps e asof join depts d
            // match_condition e.name <= d.name
            // on d.deptno = e.deptno
            // SQL注释：说明这是一个ASOF JOIN（非LEFT），在部门编号相等的前提下，找到名称字典序小于等于部门名称的最近员工
            builder -> builder // 开始构建关系表达式
                .scan("s", "depts").as("d") // 扫描depts表，别名为d
                .scan("s", "emps").as("e") // 扫描emps表，别名为e
                .asofJoin(JoinRelType.ASOF, // 创建ASOF连接，只返回有匹配的行
                    builder.equals( // 连接条件：部门编号相等
                        builder.field(2, "d", "deptno"), // 左表部门编号
                        builder.field(2, "e", "deptno")), // 右表部门编号
                    builder.lessThan( // 匹配条件：员工名称小于等于部门名称
                        builder.field(2, "e", "name"), // 右表名称字段
                        builder.field(2, "d", "name"))) // 左表名称字段
                .project( // 投影操作
                    builder.field("deptno"), // 选择部门编号
                    builder.field("e", "name"), // 选择员工名称
                    builder.field("empid")) // 选择员工ID
                .build()) // 构建完成
        .returnsUnordered( // 验证返回结果
            "deptno=10; name=Bill; empid=100"); // 结果：员工Bill匹配到部门10
  } // 方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2968">[CALCITE-2968]
   * New AntiJoin relational expression</a>. */
  // 方法注释：测试非等值反连接功能
  // 该测试展示了如何使用Anti Join查找每个部门中薪资最高的员工
  // 通过NOT EXISTS子查询实现，确保没有其他同部门员工薪资更高
  // 对应JIRA问题CALCITE-2968
  @Test void nonEquiAntiJoin() { // 测试方法，使用非等值条件的反连接
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema
        .withRel( // 设置关系表达式构建器
            // Retrieve employees with the top salary in their department. Equivalent SQL:
            //   SELECT e.name, e.salary FROM emps e
            //   WHERE NOT EXISTS (
            //     SELECT 1 FROM emps e2
            //     WHERE e.deptno = e2.deptno AND e2.salary > e.salary)
            // 注释说明：该查询返回每个部门薪资最高的员工，使用NOT EXISTS确保没有更高薪资的员工
            builder -> builder // 开始构建关系表达式
                .scan("s", "emps").as("e") // 扫描emps表，别名为e，作为左表
                .scan("s", "emps").as("e2") // 再次扫描emps表，别名为e2，作为右表（自连接）
                .antiJoin( // 创建反连接操作
                    builder.and( // 构建AND组合条件
                        builder.equals( // 条件1：部门编号相等
                            builder.field(2, "e", "deptno"), // 左表部门编号
                            builder.field(2, "e2", "deptno")), // 右表部门编号
                        builder.call( // 条件2：调用SQL操作符
                            SqlStdOperatorTable.GREATER_THAN, // 使用大于操作符
                            builder.field(2, "e2", "salary"), // 右表薪资字段
                            builder.field(2, "e", "salary")))) // 左表薪资字段，条件为e2.salary > e.salary
                .project( // 投影操作
                    builder.field("name"), // 选择员工姓名
                    builder.field("salary")) // 选择员工薪资
                .build()) // 构建完成
        .returnsUnordered( // 验证返回结果
            "name=Theodore; salary=11500.0", // 结果1：Theodore是部门10薪资最高的员工
            "name=Eric; salary=8000.0"); // 结果2：Eric是部门40薪资最高的员工
  } // 方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2968">[CALCITE-2968]
   * New AntiJoin relational expression</a>. */
  // 方法注释：测试包含NULL值的等值反连接
  // 该测试验证Anti Join对NULL值的处理，确保其行为与NOT EXISTS一致，而不是NOT IN
  // NOT EXISTS会忽略NULL值，而NOT IN遇到NULL会返回未知结果，导致整个查询无结果
  // 对应JIRA问题CALCITE-2968
  @Test void equiAntiJoinWithNullValues() { // 测试方法，测试包含NULL值的等值反连接
    final Integer salesDeptNo = 10; // 定义销售部门编号常量，值为10
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema
        .withRel( // 设置关系表达式构建器
            // Retrieve employees from any department other than Sales (deptno 10) whose
            // commission is different from any Sales employee commission. Since there
            // is a Sales employee with null commission, the goal is to validate that antiJoin
            // behaves as a NOT EXISTS (and returns results), and not as a NOT IN (which would
            // not return any result due to its null handling). Equivalent SQL:
            //   SELECT empOther.empid, empOther.name FROM emps empOther
            //   WHERE empOther.deptno <> 10 AND NOT EXISTS
            //     (SELECT 1 FROM emps empSales
            //      WHERE empSales.deptno = 10 AND empSales.commission = empOther.commission)
            // 注释说明：该查询返回非销售部门中提成与所有销售员工都不同的员工
            // 关键点：销售部门有NULL提成的员工，Anti Join应该返回结果（NOT EXISTS行为）
            // 如果使用NOT IN，由于NULL的存在，整个查询不会返回任何结果
            builder -> builder // 开始构建关系表达式
                .scan("s", "emps").as("empOther") // 扫描emps表，别名为empOther，作为左表
                .filter( // 添加过滤条件
                    builder.notEquals( // 构建不等于条件
                        builder.field("empOther", "deptno"), // 引用empOther的deptno字段
                        builder.literal(salesDeptNo))) // 字面值10，过滤掉销售部门的员工
                .scan("s", "emps").as("empSales") // 再次扫描emps表，别名为empSales，作为右表
                .filter( // 添加过滤条件
                    builder.equals( // 构建等于条件
                        builder.field("empSales", "deptno"), // 引用empSales的deptno字段
                        builder.literal(salesDeptNo))) // 字面值10，只选择销售部门的员工
                .antiJoin( // 创建反连接操作
                    builder.equals( // 构建等于条件
                        builder.field(2, "empOther", "commission"), // 左表提成字段
                        builder.field(2, "empSales", "commission"))) // 右表提成字段，条件为提成相等
                .project( // 投影操作
                    builder.field("empid"), // 选择员工ID
                    builder.field("name")) // 选择员工姓名
                .build()) // 构建完成
        .returnsUnordered("empid=200; name=Eric"); // 验证返回结果，只有Eric满足条件
  } // 方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3170">[CALCITE-3170]
   * ANTI join on conditions push down generates wrong plan</a>. */
  // 方法注释：测试Anti Join条件不能下推的场景
  // 该测试验证了当Anti Join的条件依赖于左表字段时，不能将条件下推到左表
  // 如果错误地下推条件，会导致错误的查询计划
  // 对应JIRA问题CALCITE-3170
  @Test void testCanNotPushAntiJoinConditionsToLeft() { // 测试方法，验证Anti Join条件下推限制
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema
        .withRel( // 设置关系表达式构建器
            // build a rel equivalent to sql:
            // select * from emps
            // where emps.deptno
            // not in (select depts.deptno from depts where emps.name = 'ddd')
            // SQL注释：说明该查询返回员工表中部门编号不在部门表中的员工
            // 关键点：子查询中的条件emps.name = 'ddd'依赖于外层查询，不能独立执行

            // Use `equals` instead of `is not distinct from` only for testing.
            // 注释说明：为了测试目的使用equals而不是is not distinct from
            builder -> builder // 开始构建关系表达式
                .scan("s", "emps") // 扫描emps表，作为左表
                .scan("s", "depts") // 扫描depts表，作为右表
                .antiJoin( // 创建反连接操作
                    builder.equals( // 连接条件：部门编号相等
                        builder.field(2, 0, "deptno"), // 左表（索引0）的deptno字段
                        builder.field(2, 1, "deptno")), // 右表（索引1）的deptno字段
                    builder.equals(builder.field(2, 0, "name"), // 额外条件：左表name字段等于'ddd'
                        builder.literal("ddd"))) // 字面值'ddd'
                .project(builder.field(0)) // 投影操作，选择左表的所有字段
                .build()) // 构建完成
        .returnsUnordered( // 验证返回结果
            "empid=100", // 结果1：员工ID 100
            "empid=110", // 结果2：员工ID 110
            "empid=150", // 结果3：员工ID 150
            "empid=200"); // 结果4：员工ID 200
  } // 方法结束

  /**
   * The test verifies if {@link EnumerableMergeJoin} can implement a join with non-equi conditions.
   */
  // 方法注释：测试EnumerableMergeJoin是否可以实现非等值条件的连接
  // Merge Join通常要求等值连接条件，但这个测试验证了它也可以处理非等值条件
  // 关键是先对输入表进行排序，然后Merge Join可以在排序的基础上执行连接
  @Test void testSortMergeJoinWithNonEquiCondition() { // 测试方法，测试带非等值条件的排序归并连接
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置规划器钩子
          planner.addRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 添加归并连接规则
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除普通连接规则，强制使用归并连接
        })
        .withRel(builder -> builder // 设置关系表达式构建器
            // build a rel equivalent to sql:
            // select e.empid, e.name, d.name as dept, e.deptno, d.deptno
            // from emps e join depts d
            // on e.deptno=d.deptno and e.empid > d.deptno * 10
            // SQL注释：说明这是一个内连接，连接条件包括等值条件和非等值条件
            // 非等值条件为员工ID大于部门编号的10倍
            // Note: explicit sort is used so EnumerableMergeJoin could actually work
            // 注释说明：显式使用排序操作，使EnumerableMergeJoin能够正常工作
            .scan("s", "emps") // 扫描emps表
            .sort(builder.field("deptno")) // 按deptno字段排序，为归并连接做准备
            .scan("s", "depts") // 扫描depts表
            .sort(builder.field("deptno")) // 按deptno字段排序，为归并连接做准备
            .join(JoinRelType.INNER, // 创建内连接
                builder.and( // 构建AND组合条件
                    builder.equals( // 条件1：部门编号相等（等值条件）
                        builder.field(2, 0, "deptno"), // 左表deptno字段
                        builder.field(2, 1, "deptno")), // 右表deptno字段
                    builder.getRexBuilder().makeCall( // 条件2：构建函数调用
                        SqlStdOperatorTable.GREATER_THAN, // 使用大于操作符
                        builder.field(2, 0, "empid"), // 左表empid字段
                        builder.getRexBuilder().makeCall( // 嵌套函数调用
                            SqlStdOperatorTable.MULTIPLY, // 使用乘法操作符
                            builder.literal(10), // 字面值10
                            builder.field(2, 1, "deptno"))))) // 右表deptno字段，条件为empid > deptno * 10
            .project( // 投影操作
                builder.field(1, "emps", "empid"), // 选择emps表的empid字段
                builder.field(1, "emps", "name"), // 选择emps表的name字段
                builder.alias(builder.field(1, "depts", "name"), "dept_name"), // 选择depts表的name字段，别名为dept_name
                builder.alias(builder.field(1, "emps", "deptno"), "e_deptno"), // 选择emps表的deptno字段，别名为e_deptno
                builder.alias(builder.field(1, "depts", "deptno"), "d_deptno")) // 选择depts表的deptno字段，别名为d_deptno
            .build()) // 构建完成
        .explainHookMatches("" // 验证执行计划包含预期的操作符
            + "EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2], dept_name=[$t4], e_deptno=[$t1], d_deptno=[$t3])\n" // 计算节点
            + "  EnumerableMergeJoin(condition=[AND(=($1, $3), >($0, *(10, $3)))], joinType=[inner])\n" // 归并连接节点，包含等值和非等值条件
            + "    EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 左表排序节点
            + "      EnumerableCalc(expr#0..4=[{inputs}], proj#0..2=[{exprs}])\n" // 左表计算节点
            + "        EnumerableTableScan(table=[[s, emps]])\n" // 左表扫描节点
            + "    EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 右表排序节点
            + "      EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 右表计算节点
            + "        EnumerableTableScan(table=[[s, depts]])\n") // 右表扫描节点
        .returnsUnordered("empid=110; name=Theodore; dept_name=Sales; e_deptno=10; d_deptno=10", // 验证返回结果1
            "empid=150; name=Sebastian; dept_name=Sales; e_deptno=10; d_deptno=10"); // 验证返回结果2
  } // 方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3846">[CALCITE-3846]
   * EnumerableMergeJoin: wrong comparison of composite key with null values</a>. */
  // 方法注释：测试归并连接在复合键和NULL值情况下的行为
  // 该测试验证了当连接条件包含多个字段（复合键）且存在NULL值时，归并连接的正确性
  // 复合键比较需要特殊处理NULL值，确保符合SQL语义
  // 对应JIRA问题CALCITE-3846
  @Test void testMergeJoinInnerWithCompositeKeyAndNullValues() { // 测试方法，测试内连接的复合键和NULL值处理
    checkMergeJoinWithCompositeKeyAndNullValues( // 调用辅助方法进行测试
        false, // 使用标准HrSchema（非大型Schema）
        JoinRelType.INNER, // 连接类型为内连接
        "empid=110; empid0=110", // 期望结果1
        "empid=100; empid0=100", // 期望结果2
        "empid=200; empid0=200"); // 期望结果3
    checkMergeJoinWithCompositeKeyAndNullValues( // 再次调用辅助方法，使用大型Schema
        true, // 使用HrSchemaBig（大型Schema）
        JoinRelType.INNER, // 连接类型为内连接
        "empid=48; empid0=48", // 期望结果1
        "empid=4; empid0=4", // 期望结果2
        "empid=4; empid0=8"); // 期望结果3
  } // 方法结束

  @Test void testMergeJoinLeftWithCompositeKeyAndNullValues() { // 测试方法，测试左连接的复合键和NULL值处理
    checkMergeJoinWithCompositeKeyAndNullValues( // 调用辅助方法进行测试
        false, // 使用标准HrSchema
        JoinRelType.LEFT, // 连接类型为左连接
        "empid=110; empid0=110", // 期望结果1
        "empid=100; empid0=100", // 期望结果2
        "empid=150; empid0=null", // 期望结果3：左连接保留左表行，右表无匹配时为null
        "empid=200; empid0=200"); // 期望结果4
    checkMergeJoinWithCompositeKeyAndNullValues( // 再次调用辅助方法，使用大型Schema
        true, // 使用HrSchemaBig
        JoinRelType.LEFT, // 连接类型为左连接
        "empid=48; empid0=48", // 期望结果1
        "empid=47; empid0=null", // 期望结果2：左连接保留左表行，右表无匹配时为null
        "empid=4; empid0=4"); // 期望结果3
  } // 方法结束

  @Test void testMergeJoinSemiWithCompositeKeyAndNullValues() { // 测试方法，测试半连接的复合键和NULL值处理
    // Semi Join只返回左表中存在匹配的行，相当于SQL中的EXISTS
    // 左表的字段在结果中只出现一次，不包含右表的字段
    checkMergeJoinWithCompositeKeyAndNullValues( // 调用辅助方法进行测试
        false, // 使用标准HrSchema
        JoinRelType.SEMI, // 连接类型为半连接
        "empid=110", // 期望结果1：只包含左表字段
        "empid=100", // 期望结果2：只包含左表字段
        "empid=200"); // 期望结果3：只包含左表字段
    checkMergeJoinWithCompositeKeyAndNullValues( // 再次调用辅助方法，使用大型Schema
        true, // 使用HrSchemaBig
        JoinRelType.SEMI, // 连接类型为半连接
        "empid=48", // 期望结果1：只包含左表字段
        "empid=4", // 期望结果2：只包含左表字段
        "empid=8"); // 期望结果3：只包含左表字段
  } // 方法结束

  @Test void testMergeJoinAntiWithCompositeKeyAndNullValues() { // 测试方法，测试反连接的复合键和NULL值处理
    // Anti Join只返回左表中不存在匹配的行，相当于SQL中的NOT EXISTS
    // 左表的字段在结果中只出现一次，不包含右表的字段
    checkMergeJoinWithCompositeKeyAndNullValues( // 调用辅助方法进行测试
        false, // 使用标准HrSchema
        JoinRelType.ANTI, // 连接类型为反连接
        "empid=150"); // 期望结果：只包含左表中无匹配的行
    checkMergeJoinWithCompositeKeyAndNullValues( // 再次调用辅助方法，使用大型Schema
        true, // 使用HrSchemaBig
        JoinRelType.ANTI, // 连接类型为反连接
        "empid=47", // 期望结果1：只包含左表中无匹配的行
        "empid=3", // 期望结果2：只包含左表中无匹配的行
        "empid=7"); // 期望结果3：只包含左表中无匹配的行
  } // 方法结束

  private void checkMergeJoinWithCompositeKeyAndNullValues(boolean bigSchema, JoinRelType joinType,
      String... expected) { // 私有辅助方法，用于测试复合键和NULL值情况下的归并连接
    // 参数说明：
    // bigSchema: 是否使用大型Schema（HrSchemaBig）
    // joinType: 连接类型（INNER、LEFT、SEMI、ANTI等）
    // expected: 期望的查询结果数组
    CalciteAssert.AssertQuery checker = // 创建查询检查器
        tester(false, bigSchema ? new HrSchemaBig() : new HrSchema()) // 根据参数选择Schema
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置规划器钩子
          planner.addRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 添加归并连接规则
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除普通连接规则，强制使用归并连接
        })
        .withRel(builder -> builder // 设置关系表达式构建器
            .scan("s", "emps").as("e1") // 扫描emps表，别名为e1，作为左表
            .sort(builder.field("deptno"), builder.field("commission"), builder.field("empid")) // 按复合键排序：部门编号、提成、员工ID
            .scan("s", "emps").as("e2") // 再次扫描emps表，别名为e2，作为右表（自连接）
            .sort(builder.field("deptno"), builder.field("commission"), builder.field("empid")) // 按复合键排序：部门编号、提成、员工ID
            .join(joinType, // 创建连接，连接类型由参数指定
                builder.and( // 构建AND组合条件，形成复合键
                    builder.equals( // 条件1：部门编号相等
                        builder.field(2, 0, "deptno"), // 左表deptno字段
                        builder.field(2, 1, "deptno")), // 右表deptno字段
                    builder.equals( // 条件2：提成相等
                        builder.field(2, 0, "commission"), // 左表commission字段
                        builder.field(2, 1, "commission")))) // 右表commission字段，注意commission可能为NULL
            .project(joinType.projectsRight() // 根据连接类型决定投影字段
                ? Arrays.asList(builder.field("e1", "empid"), builder.field("e2", "empid")) // 如果需要右表字段（INNER、LEFT），则包含左右表的empid
                : Arrays.asList(builder.field("e1", "empid"))) // 如果不需要右表字段（SEMI、ANTI），则只包含左表的empid
            .build()) // 构建完成
        .explainHookContains("EnumerableMergeJoin"); // 验证执行计划包含MergeJoin节点
    if (bigSchema) { // 如果使用大型Schema
      checker.returnsStartingWith(expected); // 验证结果以期望值开头（大型Schema数据量大，只验证前几条）
    } else { // 如果使用标准Schema
      checker.returnsOrdered(expected); // 验证结果按顺序完全匹配（标准Schema数据量小）
    }
  } // 方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3820">[CALCITE-3820]
   * EnumerableDefaults#orderBy should be lazily computed + support enumerator
   * re-initialization</a>. */
  // 方法注释：测试递归查询（RepeatUnion）与归并连接的结合使用
  // 该测试验证了在递归查询中使用归并连接的正确性
  // 递归查询用于处理层次结构数据，如组织架构、树形结构等
  // 对应JIRA问题CALCITE-3820
  @Test void testRepeatUnionWithMergeJoin() { // 测试方法，测试递归查询与归并连接的结合
    tester(false, new HierarchySchema()) // 创建测试器，不强制去相关化，使用HierarchySchema（层次结构Schema）
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置规划器钩子
          planner.addRule(Bindables.BINDABLE_TABLE_SCAN_RULE); // 添加可绑定表扫描规则
          planner.addRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 添加归并连接规则
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除普通连接规则，强制使用归并连接
        })
        // Note: explicit sort is used so EnumerableMergeJoin can actually work
        // 注释说明：显式使用排序操作，使EnumerableMergeJoin能够正常工作
        .withRel(builder -> builder // 设置关系表达式构建器
            //   WITH RECURSIVE delta(empid, name) as (
            //     SELECT empid, name FROM emps WHERE empid = 2
            //     UNION ALL
            //     SELECT e.empid, e.name FROM delta d
            //                            JOIN hierarchies h ON d.empid = h.managerid
            //                            JOIN emps e        ON h.subordinateid = e.empid
            //   )
            //   SELECT empid, name FROM delta
            // SQL注释：说明这是一个递归CTE（公用表表达式），用于查找员工ID为2的所有下属
            // 递归基例：选择empid=2的员工
            // 递归步骤：通过hierarchies表连接delta表和emps表，找到下属员工
            .scan("s", "emps") // 扫描emps表
            .filter( // 添加过滤条件
                builder.equals( // 构建等于条件
                    builder.field("empid"), // 引用empid字段
                    builder.literal(2))) // 字面值2，过滤出empid=2的员工
            .project( // 投影操作
                builder.field("emps", "empid"), // 选择emps表的empid字段
                builder.field("emps", "name")) // 选择emps表的name字段

            .transientScan("#DELTA#") // 扫描递归表#DELTA#，用于递归步骤
            .sort(builder.field("empid")) // 按empid字段排序，为归并连接做准备
            .scan("s", "hierarchies") // 扫描hierarchies表
            .sort(builder.field("managerid")) // 按managerid字段排序，为归并连接做准备
            .join( // 创建第一个连接
                JoinRelType.INNER, // 内连接
                builder.equals( // 连接条件：delta表的empid等于hierarchies表的managerid
                    builder.field(2, "#DELTA#", "empid"), // delta表的empid字段
                    builder.field(2, "hierarchies", "managerid"))) // hierarchies表的managerid字段
            .sort(builder.field("subordinateid")) // 按subordinateid字段排序，为归并连接做准备

            .scan("s", "emps") // 扫描emps表
            .sort(builder.field("empid")) // 按empid字段排序，为归并连接做准备
            .join( // 创建第二个连接
                JoinRelType.INNER, // 内连接
                builder.equals( // 连接条件：hierarchies表的subordinateid等于emps表的empid
                    builder.field(2, "hierarchies", "subordinateid"), // hierarchies表的subordinateid字段
                    builder.field(2, "emps", "empid"))) // emps表的empid字段
            .project( // 投影操作
                builder.field("emps", "empid"), // 选择emps表的empid字段
                builder.field("emps", "name")) // 选择emps表的name字段
            .repeatUnion("#DELTA#", true) // 创建递归联合操作，表名为#DELTA#，all=true表示包含重复行
            .build() // 构建完成
        )
        .explainHookMatches("" // 验证执行计划包含预期的操作符
            + "EnumerableRepeatUnion(all=[true])\n" // 递归联合节点
            + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[#DELTA#]])\n" // 递归基例的表缓存节点，懒加载读写
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[2], expr#6=[=($t0, $t5)], empid=[$t0], name=[$t2], $condition=[$t6])\n" // 计算节点，过滤empid=2
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 表扫描节点
            + "  EnumerableTableSpool(readType=[LAZY], writeType=[LAZY], table=[[#DELTA#]])\n" // 递归步骤的表缓存节点，懒加载读写
            + "    EnumerableCalc(expr#0..4=[{inputs}], empid=[$t3], name=[$t4])\n" // 计算节点，投影empid和name
            + "      EnumerableMergeJoin(condition=[=($2, $3)], joinType=[inner])\n" // 第二个归并连接节点
            + "        EnumerableSort(sort0=[$2], dir0=[ASC])\n" // 第二个连接的左表排序节点
            + "          EnumerableMergeJoin(condition=[=($0, $1)], joinType=[inner])\n" // 第一个归并连接节点
            + "            EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第一个连接的左表排序节点
            + "              EnumerableCalc(expr#0..1=[{inputs}], empid=[$t0])\n" // 计算节点，投影empid
            + "                EnumerableInterpreter\n" // 解释器节点
            + "                  BindableTableScan(table=[[#DELTA#]])\n" // 可绑定表扫描节点，扫描递归表
            + "            EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第一个连接的右表排序节点
            + "              EnumerableTableScan(table=[[s, hierarchies]])\n" // 表扫描节点
            + "        EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 第二个连接的右表排序节点
            + "          EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2])\n" // 计算节点，投影empid和name
            + "            EnumerableTableScan(table=[[s, emps]])\n") // 表扫描节点
        .returnsUnordered("empid=2; name=Emp2", // 验证返回结果：员工2
            "empid=3; name=Emp3", // 验证返回结果：员工3（员工2的下属）
            "empid=5; name=Emp5"); // 验证返回结果：员工5（员工2的下属）
  } // 方法结束

  private CalciteAssert.AssertThat tester(boolean forceDecorrelate,
      Object schema) { // 私有辅助方法，用于创建测试器
    // 参数说明：
    // forceDecorrelate: 是否强制去相关化（将子查询转换为连接）
    // schema: 测试使用的Schema对象
    // 返回值：配置好的CalciteAssert.AssertThat对象，用于构建和验证查询
    return CalciteAssert.that() // 创建Calcite断言对象
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置SQL词法分析策略为JAVA风格
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, forceDecorrelate) // 设置是否强制去相关化
        .withSchema("s", new ReflectiveSchema(schema)); // 设置Schema，名称为"s"，使用反射创建Schema
  } // 方法结束
} // 类结束
