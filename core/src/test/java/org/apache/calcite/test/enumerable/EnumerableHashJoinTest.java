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
package org.apache.calcite.test.enumerable; // 包声明：org.apache.calcite.test.enumerable，这是Calcite测试框架中专门用于测试Enumerable相关功能的包

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入Enumerable规则类，包含Enumerable相关的优化规则，如EnumerableMergeJoinRule等
import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性配置类，用于配置Calcite连接的各种属性
import org.apache.calcite.config.Lex; // 导入词法分析配置类，用于配置SQL解析的词法规则
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器接口，是Calcite查询优化器的核心接口
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于在查询执行过程中插入自定义逻辑
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准运算符表，包含SQL标准定义的各种运算符
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言测试工具类，用于构建测试场景和验证结果
import org.apache.calcite.test.ReflectiveSchemaWithoutRowCount; // 导入反射模式Schema类，不包含行数统计信息，用于测试
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HR（人力资源）Schema类，提供员工和部门表等测试数据

import org.junit.jupiter.api.Test; // 导入JUnit5测试注解，用于标记测试方法

import java.util.function.Consumer; // 导入函数式接口Consumer，用于接受单个输入参数并执行操作

/**
 * Unit test for
 * {@link org.apache.calcite.adapter.enumerable.EnumerableHashJoin}.
 * // 类注释：这是EnumerableHashJoin的单元测试类
 * // EnumerableHashJoin是Calcite中基于哈希表实现的连接操作符，用于高效执行等值连接
 * // 该测试类验证了各种连接类型（内连接、外连接、半连接等）在不同场景下的正确性
 * // 测试覆盖了简单键、复合键、NULL值处理、谓词下推等多种情况
 */
class EnumerableHashJoinTest { // 测试类定义：测试EnumerableHashJoin各种连接场景

  @Test void innerJoin() { // 测试方法：测试内连接（INNER JOIN）功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema作为测试数据源
        .query( // 构建SQL查询：员工表与部门表进行内连接，基于部门编号匹配
            "select e.empid, e.name, d.name as dept from emps e join depts "
                + "d on e.deptno=d.deptno") // SQL查询：选择员工ID、姓名和部门名称
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置Hook：在优化器阶段移除MergeJoin规则，强制使用HashJoin
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除MergeJoin规则，确保使用HashJoin
        .explainContains("EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], " // 验证执行计划包含：Calc计算节点，输出empid、name、dept字段
            + "name=[$t2], dept=[$t4])\n" // Calc节点：对输入字段进行投影和重命名
            + "  EnumerableHashJoin(condition=[=($1, $3)], joinType=[inner])\n" // HashJoin节点：使用等值条件($1=$3)进行内连接
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..2=[{exprs}])\n" // 左子节点Calc：投影员工表的empid、deptno、name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 右子节点Calc：投影部门表的deptno、name字段
            + "      EnumerableTableScan(table=[[s, depts]])\n") // 右子节点：扫描部门表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; name=Bill; dept=Sales", // 预期结果1：员工Bill属于Sales部门
            "empid=110; name=Theodore; dept=Sales", // 预期结果2：员工Theodore属于Sales部门
            "empid=150; name=Sebastian; dept=Sales"); // 预期结果3：员工Sebastian属于Sales部门
  }

  @Test void leftOuterJoin() { // 测试方法：测试左外连接（LEFT OUTER JOIN）功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表左外连接部门表，保留所有员工记录
            "select e.empid, e.name, d.name as dept from emps e  left outer "
                + "join depts d on e.deptno=d.deptno") // SQL查询：左外连接，即使没有匹配的部门也保留员工记录
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置Hook：移除MergeJoin规则，强制使用HashJoin
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除MergeJoin规则
        .explainContains("EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], " // 验证执行计划包含Calc节点
            + "name=[$t2], dept=[$t4])\n" // Calc节点：投影empid、name、dept字段
            + "  EnumerableHashJoin(condition=[=($1, $3)], joinType=[left])\n" // HashJoin节点：joinType=[left]表示左外连接
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..2=[{exprs}])\n" // 左子节点：投影员工表的empid、deptno、name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 右子节点：投影部门表的deptno、name字段
            + "      EnumerableTableScan(table=[[s, depts]])\n") // 右子节点：扫描部门表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; name=Bill; dept=Sales", // 预期结果1：员工Bill属于Sales部门
            "empid=110; name=Theodore; dept=Sales", // 预期结果2：员工Theodore属于Sales部门
            "empid=150; name=Sebastian; dept=Sales", // 预期结果3：员工Sebastian属于Sales部门
            "empid=200; name=Eric; dept=null"); // 预期结果4：员工Eric没有匹配的部门，dept为null（左外连接保留左表所有记录）
  }

  @Test void rightOuterJoin() { // 测试方法：测试右外连接（RIGHT OUTER JOIN）功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表右外连接部门表，保留所有部门记录
            "select e.empid, e.name, d.name as dept from emps e  right outer "
                + "join depts d on e.deptno=d.deptno") // SQL查询：右外连接，即使没有匹配的员工也保留部门记录
        .explainContains("EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], " // 验证执行计划包含Calc节点
            + "name=[$t2], dept=[$t4])\n" // Calc节点：投影empid、name、dept字段
            + "  EnumerableHashJoin(condition=[=($1, $3)], joinType=[right])\n" // HashJoin节点：joinType=[right]表示右外连接
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..2=[{exprs}])\n" // 左子节点：投影员工表的empid、deptno、name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 右子节点：投影部门表的deptno、name字段
            + "      EnumerableTableScan(table=[[s, depts]])") // 右子节点：扫描部门表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; name=Bill; dept=Sales", // 预期结果1：员工Bill属于Sales部门
            "empid=110; name=Theodore; dept=Sales", // 预期结果2：员工Theodore属于Sales部门
            "empid=150; name=Sebastian; dept=Sales", // 预期结果3：员工Sebastian属于Sales部门
            "empid=null; name=null; dept=Marketing", // 预期结果4：Marketing部门没有匹配的员工，员工字段为null（右外连接保留右表所有记录）
            "empid=null; name=null; dept=HR"); // 预期结果5：HR部门没有匹配的员工，员工字段为null
  }

  @Test void leftOuterJoinWithPredicate() { // 测试方法：测试带谓词的左外连接功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表左外连接部门表，连接条件包含多个谓词
            "select e.empid, e.name, d.name as dept from emps e left outer "
                + "join depts d on e.deptno=d.deptno and e.empid<150 and e"
                + ".empid>d.deptno") // SQL查询：连接条件包括部门编号相等、员工ID小于150、员工ID大于部门编号
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置Hook：移除MergeJoin规则，强制使用HashJoin
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除MergeJoin规则
        .explainContains("EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], " // 验证执行计划包含Calc节点
            + "name=[$t2], dept=[$t4])\n" // Calc节点：投影empid、name、dept字段
            + "  EnumerableHashJoin(condition=[AND(=($1, $3), <($0, 150), >" // HashJoin节点：连接条件为AND组合的三个条件
            + "($0, $3))], joinType=[left])\n" // 条件：$1=$3（部门编号相等）AND $0<150（员工ID小于150）AND $0>$3（员工ID大于部门编号）
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..2=[{exprs}])\n" // 左子节点：投影员工表的empid、deptno、name字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 右子节点：投影部门表的deptno、name字段
            + "      EnumerableTableScan(table=[[s, depts]])\n") // 右子节点：扫描部门表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; name=Bill; dept=Sales", // 预期结果1：员工Bill（ID=100）满足所有条件：deptno=10，100<150且100>10，匹配Sales部门
            "empid=110; name=Theodore; dept=Sales", // 预期结果2：员工Theodore（ID=110）满足所有条件：deptno=10，110<150且110>10，匹配Sales部门
            "empid=150; name=Sebastian; dept=null", // 预期结果3：员工Sebastian（ID=150）不满足条件150<150，dept=null（左外连接保留）
            "empid=200; name=Eric; dept=null"); // 预期结果4：员工Eric（ID=200）不满足条件200<150，dept=null（左外连接保留）
  }

  @Test void rightOuterJoinWithPredicate() { // 测试方法：测试带谓词的右外连接功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表右外连接部门表，连接条件包含谓词
            "select e.empid, e.name, d.name as dept from emps e right outer "
                + "join depts d on e.deptno=d.deptno and e.empid<150") // SQL查询：连接条件包括部门编号相等和员工ID小于150
        .explainContains("EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], " // 验证执行计划包含Calc节点
            + "name=[$t2], dept=[$t4])\n" // Calc节点：投影empid、name、dept字段
            + "  EnumerableHashJoin(condition=[=($1, $3)], joinType=[right])\n" // HashJoin节点：joinType=[right]表示右外连接，条件只有$1=$3（部门编号相等）
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[150], " // 左子节点Calc：expr#5=150（常量），expr#6=<$t0,$t5>（员工ID<150）
            + "expr#6=[<($t0, $t5)], proj#0..2=[{exprs}], $condition=[$t6])\n" // proj#0..2投影empid、deptno、name，$condition=expr#6（过滤条件）
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 右子节点：投影部门表的deptno、name字段
            + "      EnumerableTableScan(table=[[s, depts]])\n") // 右子节点：扫描部门表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; name=Bill; dept=Sales", // 预期结果1：员工Bill（ID=100）满足条件100<150，匹配Sales部门
            "empid=110; name=Theodore; dept=Sales", // 预期结果2：员工Theodore（ID=110）满足条件110<150，匹配Sales部门
            "empid=null; name=null; dept=Marketing", // 预期结果3：Marketing部门没有匹配的员工（因为员工ID<150的员工中没有在Marketing部门的），员工字段为null（右外连接保留）
            "empid=null; name=null; dept=HR"); // 预期结果4：HR部门没有匹配的员工，员工字段为null（右外连接保留）
  }

  @Test void semiJoin() { // 测试方法：测试半连接（SEMI JOIN）功能，使用IN子句
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：查询有员工的部门（使用IN子句实现半连接）
            "SELECT d.deptno, d.name FROM depts d WHERE d.deptno in (SELECT e.deptno FROM emps e)") // SQL查询：找出至少有一个员工的部门
        .explainContains("EnumerableHashJoin(condition=[=($0, $3)], " // 验证执行计划包含HashJoin节点
            + "joinType=[semi])\n" // HashJoin节点：joinType=[semi]表示半连接，只返回左表中与右表匹配的记录
            + "  EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 左子节点：投影部门表的deptno、name字段
            + "    EnumerableTableScan(table=[[s, depts]])\n" // 左子节点：扫描部门表
            + "  EnumerableTableScan(table=[[s, emps]])") // 右子节点：扫描员工表，投影deptno字段
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "deptno=10; name=Sales"); // 预期结果：只有Sales部门（deptno=10）有员工，Marketing和HR部门没有员工
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4561">[CALCITE-4561]
   * Wrong results for plan with EnumerableHashJoin (semi) on nullable colunms</a>. */
  // 测试用例说明：这是针对CALCITE-4561问题的测试，该问题是关于在可空列上使用EnumerableHashJoin（半连接）时产生错误结果
  // 测试目的是验证半连接在处理包含NULL值的可空列时能正确返回结果
  @Test void semiJoinWithNulls() { // 测试方法：测试在可空列上进行半连接的功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：查询佣金相同的员工（使用IN子句，commission字段可能包含NULL值）
            "SELECT e1.name FROM emps e1 WHERE e1.commission in (SELECT e2.commission FROM emps e2)") // SQL查询：找出佣金与其他员工相同的员工
        .explainContains("EnumerableCalc(expr#0..1=[{inputs}], name=[$t0])\n" // 验证执行计划包含Calc节点，投影name字段
            + "  EnumerableHashJoin(condition=[=($1, $6)], joinType=[semi])\n" // HashJoin节点：joinType=[semi]表示半连接，条件是佣金相等
            + "    EnumerableCalc(expr#0..4=[{inputs}], name=[$t2], commission=[$t4])\n" // 左子节点Calc：投影name和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableTableScan(table=[[s, emps]])\n\n") // 右子节点：扫描员工表，投影commission字段
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "name=Bill", // 预期结果1：Bill的佣金与其他员工相同
            "name=Eric", // 预期结果2：Eric的佣金与其他员工相同
            "name=Theodore"); // 预期结果3：Theodore的佣金与其他员工相同
  }

  @Test void semiJoinWithPredicate() { // 测试方法：测试带谓词的半连接功能，使用RelBuilder构建关系表达式
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .withRel( // 使用RelBuilder构建关系表达式
            // Retrieve employees with the top salary in their department. Equivalent SQL:
            //   SELECT e.name, e.salary FROM emps e
            //   WHERE  EXISTS (
            //     SELECT 1 FROM emps e2
            //     WHERE e.deptno = e2.deptno AND e2.salary > e.salary)
            // 注释说明：查询每个部门中薪水最高的员工（使用EXISTS子句实现半连接）
            builder -> builder // 开始构建关系表达式
                .scan("s", "emps").as("e") // 扫描员工表，别名为"e"
                .scan("s", "emps").as("e2") // 再次扫描员工表，别名为"e2"
                .semiJoin( // 执行半连接操作
                    builder.and( // 连接条件为AND组合
                        builder.equals( // 条件1：部门编号相等
                            builder.field(2, "e", "deptno"), // 左表的deptno字段（来自"e"表）
                            builder.field(2, "e2", "deptno")), // 右表的deptno字段（来自"e2"表）
                        builder.call( // 条件2：调用SQL运算符
                            SqlStdOperatorTable.GREATER_THAN, // 使用大于运算符
                            builder.field(2, "e2", "salary"), // 右表的salary字段
                            builder.field(2, "e", "salary")))) // 左表的salary字段（e2.salary > e.salary）
                .project( // 投影操作，选择输出字段
                    builder.field("name"), // 投影name字段
                    builder.field("salary")) // 投影salary字段
                .build()) // 构建完成
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "name=Bill; salary=10000.0", // 预期结果1：Bill是Sales部门薪水最高的员工（10000.0）
            "name=Sebastian; salary=7000.0"); // 预期结果2：Sebastian是Sales部门薪水第二高的员工（7000.0），但因为没有其他更高薪水的员工，所以也被返回
  }

  @Test void innerJoinWithPredicate() { // 测试方法：测试带谓词的内连接功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表内连接部门表，连接条件包含多个谓词
            "select e.empid, e.name, d.name as dept from emps e join depts d"
                + " on e.deptno=d.deptno and e.empid<150 and e.empid>d.deptno") // SQL查询：连接条件包括部门编号相等、员工ID小于150、员工ID大于部门编号
        .explainContains("EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0], name=[$t2], " // 验证执行计划包含Calc节点
            + "dept=[$t4])\n" // Calc节点：投影empid、name、dept字段
            + "  EnumerableHashJoin(condition=[AND(=($1, $3), >($0, $3))], joinType=[inner])\n" // HashJoin节点：joinType=[inner]表示内连接，条件为AND组合：$1=$3且$0>$3
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[150], expr#6=[<($t0, $t5)], " // 左子节点Calc：expr#5=150，expr#6=<$t0,$t5>（员工ID<150）
            + "proj#0..2=[{exprs}], $condition=[$t6])\n" // proj#0..2投影empid、deptno、name，$condition=expr#6（过滤条件）
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}])\n" // 右子节点：投影部门表的deptno、name字段
            + "      EnumerableTableScan(table=[[s, depts]])\n") // 右子节点：扫描部门表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; name=Bill; dept=Sales", // 预期结果1：员工Bill（ID=100）满足所有条件：deptno=10，100<150且100>10，匹配Sales部门
            "empid=110; name=Theodore; dept=Sales"); // 预期结果2：员工Theodore（ID=110）满足所有条件：deptno=10，110<150且110>10，匹配Sales部门
  }

  @Test void innerJoinWithCompositeKeyAndNullValues() { // 测试方法：测试复合键且包含NULL值的内连接功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表自连接，基于部门编号和佣金两个字段进行连接（复合键）
            "select e1.empid from emps e1 join emps e2 "
                + "on e1.deptno=e2.deptno and e1.commission=e2.commission") // SQL查询：连接条件是部门编号相等且佣金相等（复合键连接）
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置Hook：移除MergeJoin规则，强制使用HashJoin
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除MergeJoin规则
        .explainContains("EnumerableCalc(expr#0..4=[{inputs}], empid=[$t0])\n" // 验证执行计划包含Calc节点，投影empid字段
            + "  EnumerableHashJoin(condition=[AND(=($1, $3), =($2, $4))], joinType=[inner])\n" // HashJoin节点：joinType=[inner]表示内连接，条件为AND组合：$1=$3（deptno相等）且$2=$4（commission相等）
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 左子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..4=[{inputs}], deptno=[$t1], commission=[$t4])\n" // 右子节点Calc：投影deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 右子节点：扫描员工表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100", // 预期结果1：员工100的deptno和commission与某个员工匹配
            "empid=110", // 预期结果2：员工110的deptno和commission与某个员工匹配
            "empid=200"); // 预期结果3：员工200的deptno和commission与某个员工匹配
  }

  @Test void leftOuterJoinWithCompositeKeyAndNullValues() { // 测试方法：测试复合键且包含NULL值的左外连接功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表左外连接自身，基于部门编号和佣金两个字段进行连接（复合键）
            "select e1.empid, e2.empid from emps e1 left outer join emps e2 "
                + "on e1.deptno=e2.deptno and e1.commission=e2.commission") // SQL查询：左外连接，基于deptno和commission的复合键
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置Hook：移除MergeJoin规则，强制使用HashJoin
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除MergeJoin规则
        .explainContains("EnumerableCalc(expr#0..5=[{inputs}], empid=[$t0], empid0=[$t3])\n" // 验证执行计划包含Calc节点，投影两个empid字段
            + "  EnumerableHashJoin(condition=[AND(=($1, $4), =($2, $5))], joinType=[left])\n" // HashJoin节点：joinType=[left]表示左外连接，条件为AND组合：$1=$4（deptno相等）且$2=$5（commission相等）
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 左子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 右子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 右子节点：扫描员工表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; empid=100", // 预期结果1：员工100的deptno和commission与某个员工匹配（可能是自己）
            "empid=110; empid=110", // 预期结果2：员工110的deptno和commission与某个员工匹配（可能是自己）
            "empid=150; empid=null", // 预期结果3：员工150没有匹配的员工（commission可能为null或不匹配），右表empid为null（左外连接保留左表所有记录）
            "empid=200; empid=200"); // 预期结果4：员工200的deptno和commission与某个员工匹配（可能是自己）
  }

  @Test void rightOuterJoinWithCompositeKeyAndNullValues() { // 测试方法：测试复合键且包含NULL值的右外连接功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表右外连接自身，基于部门编号和佣金两个字段进行连接（复合键）
            "select e1.empid, e2.empid from emps e1 right outer join emps e2 "
                + "on e1.deptno=e2.deptno and e1.commission=e2.commission") // SQL查询：右外连接，基于deptno和commission的复合键
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置Hook：移除MergeJoin规则，强制使用HashJoin
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除MergeJoin规则
        .explainContains("EnumerableCalc(expr#0..5=[{inputs}], empid=[$t0], empid0=[$t3])\n" // 验证执行计划包含Calc节点，投影两个empid字段
            + "  EnumerableHashJoin(condition=[AND(=($1, $4), =($2, $5))], joinType=[right])\n" // HashJoin节点：joinType=[right]表示右外连接，条件为AND组合：$1=$4（deptno相等）且$2=$5（commission相等）
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 左子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 右子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 右子节点：扫描员工表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; empid=100", // 预期结果1：员工100的deptno和commission与某个员工匹配（可能是自己）
            "empid=110; empid=110", // 预期结果2：员工110的deptno和commission与某个员工匹配（可能是自己）
            "empid=200; empid=200", // 预期结果3：员工200的deptno和commission与某个员工匹配（可能是自己）
            "empid=null; empid=150"); // 预期结果4：员工150没有匹配的员工（commission可能为null或不匹配），左表empid为null（右外连接保留右表所有记录）
  }

  @Test void fullOuterJoinWithCompositeKeyAndNullValues() { // 测试方法：测试复合键且包含NULL值的全外连接功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR Schema
        .query( // 构建SQL查询：员工表全外连接自身，基于部门编号和佣金两个字段进行连接（复合键）
            "select e1.empid, e2.empid from emps e1 full outer join emps e2 "
                + "on e1.deptno=e2.deptno and e1.commission=e2.commission") // SQL查询：全外连接，基于deptno和commission的复合键
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置Hook：移除MergeJoin规则，强制使用HashJoin
            planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE)) // 移除MergeJoin规则
        .explainContains("EnumerableCalc(expr#0..5=[{inputs}], empid=[$t0], empid0=[$t3])\n" // 验证执行计划包含Calc节点，投影两个empid字段
            + "  EnumerableHashJoin(condition=[AND(=($1, $4), =($2, $5))], joinType=[full])\n" // HashJoin节点：joinType=[full]表示全外连接，条件为AND组合：$1=$4（deptno相等）且$2=$5（commission相等）
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 左子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 右子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 右子节点：扫描员工表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100; empid=100", // 预期结果1：员工100的deptno和commission与某个员工匹配（可能是自己）
            "empid=110; empid=110", // 预期结果2：员工110的deptno和commission与某个员工匹配（可能是自己）
            "empid=150; empid=null", // 预期结果3：员工150没有匹配的员工，右表empid为null（全外连接保留左表所有记录）
            "empid=200; empid=200", // 预期结果4：员工200的deptno和commission与某个员工匹配（可能是自己）
            "empid=null; empid=150"); // 预期结果5：员工150没有匹配的员工，左表empid为null（全外连接保留右表所有记录）
  }

  @Test void semiJoinWithCompositeKeyAndNullValues() { // 测试方法：测试复合键且包含NULL值的半连接功能
    tester(true, new HrSchema()) // 创建测试器，强制去相关化（forceDecorrelate=true），使用HR Schema
        .query( // 构建SQL查询：查询与某个员工部门编号和佣金都相同的员工（使用EXISTS子句实现半连接）
            "select e1.empid from emps e1 where exists (select 1 from emps e2 "
                + "where e1.deptno=e2.deptno and e1.commission=e2.commission)") // SQL查询：基于deptno和commission的复合键进行半连接
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置Hook：移除MergeJoin规则，强制使用HashJoin
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除MergeJoin规则
        })
        .explainContains("EnumerableCalc(expr#0..2=[{inputs}], empid=[$t0])\n" // 验证执行计划包含Calc节点，投影empid字段
            + "  EnumerableHashJoin(condition=[AND(=($1, $4), =($2, $7))], joinType=[semi])\n" // HashJoin节点：joinType=[semi]表示半连接，条件为AND组合：$1=$4（deptno相等）且$2=$7（commission相等）
            + "    EnumerableCalc(expr#0..4=[{inputs}], proj#0..1=[{exprs}], commission=[$t4])\n" // 左子节点Calc：投影empid、deptno字段和commission字段
            + "      EnumerableTableScan(table=[[s, emps]])\n" // 左子节点：扫描员工表
            + "    EnumerableCalc(expr#0..4=[{inputs}], expr#5=[IS NOT NULL($t4)], proj#0..4=[{exprs}], $condition=[$t5])\n" // 右子节点Calc：expr#5=IS NOT NULL($t4)（检查commission不为null），$condition=expr#5（过滤条件）
            + "      EnumerableTableScan(table=[[s, emps]])\n") // 右子节点：扫描员工表
        .returnsUnordered( // 验证查询结果（不关心顺序）
            "empid=100", // 预期结果1：员工100的deptno和commission与某个员工匹配
            "empid=110", // 预期结果2：员工110的deptno和commission与某个员工匹配
            "empid=200"); // 预期结果3：员工200的deptno和commission与某个员工匹配
  }

  private CalciteAssert.AssertThat tester(boolean forceDecorrelate, // 私有辅助方法：创建CalciteAssert测试器
      Object schema) { // 参数：forceDecorrelate-是否强制去相关化，schema-测试使用的Schema对象
    return CalciteAssert.that() // 创建CalciteAssert测试器对象
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置词法分析器为JAVA模式（使用双引号标识符，方括号索引）
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, forceDecorrelate) // 设置是否强制去相关化（将子查询转换为连接）
        .withSchema("s", new ReflectiveSchemaWithoutRowCount(schema)); // 注册Schema：名称为"s"，使用ReflectiveSchemaWithoutRowCount包装schema对象（不提供行数统计）
  } // 返回配置好的CalciteAssert.AssertThat对象，可用于构建测试查询
} // 类结束
