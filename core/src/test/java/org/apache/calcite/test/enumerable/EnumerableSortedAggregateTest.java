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
package org.apache.calcite.test.enumerable; // 定义包名，表示该测试类位于org.apache.calcite.test.enumerable包下

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入EnumerableRules类，用于访问可枚举规则集合
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema类，用于通过反射创建Schema
import org.apache.calcite.config.CalciteConnectionProperty; // 导入CalciteConnectionProperty类，用于配置Calcite连接属性
import org.apache.calcite.config.Lex; // 导入Lex类，用于配置词法分析策略
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示关系表达式优化规划器
import org.apache.calcite.runtime.Hook; // 导入Hook类，用于在运行时插入自定义逻辑
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert类，用于Calcite测试断言工具
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HrSchema类，表示人力资源测试Schema

import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.util.function.Consumer; // 导入Consumer函数式接口，用于消费RelOptPlanner对象

/** Test for
 * {@link org.apache.calcite.adapter.enumerable.EnumerableSortedAggregate}. */ // 测试类文档注释，说明该类用于测试EnumerableSortedAggregate功能
public class EnumerableSortedAggregateTest { // 定义测试类名称，测试可枚举排序聚合操作
  @Test void sortedAgg() { // 定义测试方法，测试基本的排序聚合功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema作为测试Schema
        .query("select deptno, " // 构建SQL查询语句，选择部门编号
            + "max(salary) as max_salary, count(name) as num_employee " // 计算每个部门的最高薪水和员工数量
            + "from emps group by deptno") // 从员工表按部门编号分组
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 使用Hook机制定制规划器行为
          planner.removeRule(EnumerableRules.ENUMERABLE_AGGREGATE_RULE); // 移除普通的可枚举聚合规则
          planner.addRule(EnumerableRules.ENUMERABLE_SORTED_AGGREGATE_RULE); // 添加可枚举排序聚合规则
        })
        .explainContains( // 验证执行计划包含预期的操作符
            "EnumerableSortedAggregate(group=[{1}], max_salary=[MAX($3)], num_employee=[COUNT($2)])\n" // 验证包含排序聚合操作符，按第1列分组，计算第3列的最大值和第2列的计数
            + "  EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 验证包含排序操作符，按第1列升序排序
            + "    EnumerableTableScan(table=[[s, emps]])") // 验证包含表扫描操作符，扫描s.emps表
        .returnsOrdered( // 验证查询结果按顺序返回
            "deptno=10; max_salary=11500.0; num_employee=3", // 验证部门10的结果：最高薪水11500.0，3名员工
            "deptno=20; max_salary=8000.0; num_employee=1"); // 验证部门20的结果：最高薪水8000.0，1名员工
  }

  @Test void sortedAggTwoGroupKeys() { // 定义测试方法，测试使用两个分组键的排序聚合功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema作为测试Schema
        .query( // 构建SQL查询语句
            "select deptno, commission, " // 选择部门编号和佣金
                + "max(salary) as max_salary, count(name) as num_employee " // 计算每个部门和佣金组合的最高薪水和员工数量
                + "from emps group by deptno, commission") // 从员工表按部门编号和佣金分组
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 使用Hook机制定制规划器行为
          planner.removeRule(EnumerableRules.ENUMERABLE_AGGREGATE_RULE); // 移除普通的可枚举聚合规则
          planner.addRule(EnumerableRules.ENUMERABLE_SORTED_AGGREGATE_RULE); // 添加可枚举排序聚合规则
        })
        .explainContains( // 验证执行计划包含预期的操作符
            "EnumerableSortedAggregate(group=[{1, 4}], max_salary=[MAX($3)], num_employee=[COUNT($2)])\n" // 验证包含排序聚合操作符，按第1列和第4列分组，计算第3列的最大值和第2列的计数
            + "  EnumerableSort(sort0=[$1], sort1=[$4], dir0=[ASC], dir1=[ASC])\n" // 验证包含排序操作符，按第1列和第4列都升序排序
            + "    EnumerableTableScan(table=[[s, emps]])") // 验证包含表扫描操作符，扫描s.emps表
        .returnsOrdered( // 验证查询结果按顺序返回
            "deptno=10; commission=250; max_salary=11500.0; num_employee=1", // 验证部门10佣金250的结果
            "deptno=10; commission=1000; max_salary=10000.0; num_employee=1", // 验证部门10佣金1000的结果
            "deptno=10; commission=null; max_salary=7000.0; num_employee=1", // 验证部门10佣金为null的结果
            "deptno=20; commission=500; max_salary=8000.0; num_employee=1"); // 验证部门20佣金500的结果
  }

  // Outer sort is expected to be pushed through aggregation. // 注释说明：外部排序预期会被推过聚合操作（即排序会被合并到聚合内部的排序中）
  @Test void sortedAggGroupbyXOrderbyX() { // 定义测试方法，测试GROUP BY和ORDER BY使用相同列时的排序聚合优化
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema作为测试Schema
        .query( // 构建SQL查询语句
            "select deptno, " // 选择部门编号
                + "max(salary) as max_salary, count(name) as num_employee " // 计算每个部门的最高薪水和员工数量
                + "from emps group by deptno order by deptno") // 从员工表按部门编号分组，并按部门编号排序
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 使用Hook机制定制规划器行为
          planner.removeRule(EnumerableRules.ENUMERABLE_AGGREGATE_RULE); // 移除普通的可枚举聚合规则
          planner.addRule(EnumerableRules.ENUMERABLE_SORTED_AGGREGATE_RULE); // 添加可枚举排序聚合规则
        })
        .explainContains( // 验证执行计划包含预期的操作符
            "EnumerableSortedAggregate(group=[{1}], max_salary=[MAX($3)], num_employee=[COUNT($2)])\n" // 验证包含排序聚合操作符，按第1列分组
            + "  EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 验证包含排序操作符，按第1列升序排序（注意：这里只有一个排序，外部排序被合并）
            + "    EnumerableTableScan(table=[[s, emps]])") // 验证包含表扫描操作符，扫描s.emps表
        .returnsOrdered( // 验证查询结果按顺序返回
            "deptno=10; max_salary=11500.0; num_employee=3", // 验证部门10的结果
            "deptno=20; max_salary=8000.0; num_employee=1"); // 验证部门20的结果
  }

  // Outer sort is not expected to be pushed through aggregation. // 注释说明：外部排序预期不会被推过聚合操作（因为ORDER BY和GROUP BY使用不同的列）
  @Test void sortedAggGroupbyXOrderbyY() { // 定义测试方法，测试GROUP BY和ORDER BY使用不同列时的排序聚合行为
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema作为测试Schema
        .query( // 构建SQL查询语句
            "select deptno, " // 选择部门编号
                + "max(salary) as max_salary, count(name) as num_employee " // 计算每个部门的最高薪水和员工数量
                + "from emps group by deptno order by num_employee desc") // 从员工表按部门编号分组，但按员工数量降序排序
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 使用Hook机制定制规划器行为
          planner.removeRule(EnumerableRules.ENUMERABLE_AGGREGATE_RULE); // 移除普通的可枚举聚合规则
          planner.addRule(EnumerableRules.ENUMERABLE_SORTED_AGGREGATE_RULE); // 添加可枚举排序聚合规则
        })
        .explainContains( // 验证执行计划包含预期的操作符
            "EnumerableSort(sort0=[$2], dir0=[DESC])\n" // 验证包含外部排序操作符，按第2列（num_employee）降序排序
            + "  EnumerableSortedAggregate(group=[{1}], max_salary=[MAX($3)], num_employee=[COUNT($2)])\n" // 验证包含排序聚合操作符，按第1列分组
            + "    EnumerableSort(sort0=[$1], dir0=[ASC])\n" // 验证包含内部排序操作符，按第1列升序排序（用于聚合）
            + "      EnumerableTableScan(table=[[s, emps]])") // 验证包含表扫描操作符，扫描s.emps表
        .returnsOrdered( // 验证查询结果按顺序返回
            "deptno=10; max_salary=11500.0; num_employee=3", // 验证部门10的结果（员工数量最多）
            "deptno=20; max_salary=8000.0; num_employee=1"); // 验证部门20的结果（员工数量最少）
  }

  @Test void sortedAggNullValueInSortedGroupByKeys() { // 定义测试方法，测试排序分组键中包含NULL值时的排序聚合功能
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HrSchema作为测试Schema
        .query( // 构建SQL查询语句
            "select commission, " // 选择佣金
                + "count(deptno) as num_dept " // 计算每个佣金值的部门数量
                + "from emps group by commission") // 从员工表按佣金分组
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 使用Hook机制定制规划器行为
          planner.removeRule(EnumerableRules.ENUMERABLE_AGGREGATE_RULE); // 移除普通的可枚举聚合规则
          planner.addRule(EnumerableRules.ENUMERABLE_SORTED_AGGREGATE_RULE); // 添加可枚举排序聚合规则
        })
        .explainContains( // 验证执行计划包含预期的操作符
            "EnumerableSortedAggregate(group=[{4}], num_dept=[COUNT()])\n" // 验证包含排序聚合操作符，按第4列（commission）分组，计算部门数量
                + "  EnumerableSort(sort0=[$4], dir0=[ASC])\n" // 验证包含排序操作符，按第4列升序排序
                + "    EnumerableTableScan(table=[[s, emps]])") // 验证包含表扫描操作符，扫描s.emps表
        .returnsOrdered( // 验证查询结果按顺序返回
            "commission=250; num_dept=1", // 验证佣金250的结果
            "commission=500; num_dept=1", // 验证佣金500的结果
            "commission=1000; num_dept=1", // 验证佣金1000的结果
            "commission=null; num_dept=1"); // 验证佣金为null的结果（NULL值在升序排序中排在最后）
  }

  private CalciteAssert.AssertThat tester(boolean forceDecorrelate, // 定义私有辅助方法，用于创建测试断言对象，参数forceDecorrelate表示是否强制去相关化
                                          Object schema) { // 参数schema表示要使用的Schema对象
    return CalciteAssert.that() // 创建CalciteAssert断言对象
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 配置词法分析策略为JAVA风格（标识符用双引号，字符串用单引号）
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, forceDecorrelate) // 配置是否强制去相关化
        .withSchema("s", new ReflectiveSchema(schema)); // 配置Schema，使用反射Schema包装传入的schema对象，命名为"s"
  }
} // 类定义结束
