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
package org.apache.calcite.test.enumerable; // 包声明：包含可枚举批处理嵌套循环连接的测试类

import org.apache.calcite.adapter.enumerable.EnumerableBatchNestedLoopJoinRule; // 导入：可枚举批处理嵌套循环连接规则类，用于将逻辑连接转换为物理的批处理嵌套循环连接
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入：可枚举规则集合，包含各种可枚举相关的优化规则
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入：反射模式类，用于通过反射将Java对象作为数据库模式使用
import org.apache.calcite.config.CalciteConnectionProperty; // 导入：Calcite连接属性枚举，定义各种连接配置属性
import org.apache.calcite.config.Lex; // 导入：词法分析配置枚举，定义SQL语句的词法分析方式
import org.apache.calcite.plan.RelOptPlanner; // 导入：关系表达式优化器接口，负责查询优化和规则匹配
import org.apache.calcite.rel.core.JoinRelType; // 导入：连接关系类型枚举，定义内连接、左外连接等连接类型
import org.apache.calcite.runtime.Hook; // 导入：钩子接口，允许在特定执行点插入自定义逻辑
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入：SQL标准操作符表，包含所有标准SQL函数和操作符
import org.apache.calcite.test.CalciteAssert; // 导入：Calcite断言工具类，用于编写测试用例和验证查询结果
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入：人力资源模式类，提供简单的HR测试数据
import org.apache.calcite.test.schemata.hr.HrSchemaBig; // 导入：大型人力资源模式类，提供更丰富的HR测试数据

import org.junit.jupiter.api.Test; // 导入：JUnit5测试注解，标记测试方法

import java.util.function.Consumer; // 导入：函数式接口，表示接受单个参数且无返回值的操作

/**
 * Unit test for // 单元测试类，用于测试
 * {@link org.apache.calcite.adapter.enumerable.EnumerableBatchNestedLoopJoin}. // 可枚举批处理嵌套循环连接实现
 * 这个类测试了批处理嵌套循环连接在各种场景下的正确性，包括内连接、左外连接、半连接、反连接等
 * 批处理嵌套循环连接是一种特殊的连接实现，它将右侧输入缓存到内存中，然后批量处理左侧输入的每一行
 * 这种方式在某些场景下比传统的嵌套循环连接更高效，特别是当右侧数据量较小而左侧数据量较大时
 */
class EnumerableBatchNestedLoopJoinTest { // 测试类定义：可枚举批处理嵌套循环连接测试类，继承自JUnit测试框架

  @Test void simpleInnerBatchJoinTestBuilder() { // 测试方法：使用RelBuilder API测试简单的内连接批处理场景
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR模式作为测试数据
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .withRel( // 使用关系构建器API构建测试查询
            builder -> builder // 获取关系构建器实例
                .scan("s", "depts").as("d") // 扫描模式s中的depts表，并设置别名为d
                .scan("s", "emps").as("e") // 扫描模式s中的emps表，并设置别名为e
                .join(JoinRelType.INNER, // 执行内连接操作，INNER表示内连接，只返回匹配的行
                    builder.equals( // 构建等值连接条件
                        builder.field(2, "d", "deptno"), // 获取d表的deptno字段，2表示输入数量
                        builder.field(2, "e", "deptno"))) // 获取e表的deptno字段，与d表的deptno进行等值比较
                .project( // 执行投影操作，选择需要的输出字段
                    builder.field("deptno")) // 选择deptno字段作为输出
                .build()) // 构建完整的关系表达式树
        .returnsUnordered( // 验证查询结果，不关心结果的顺序
            "deptno=10", // 期望的第一条结果记录
            "deptno=10", // 期望的第二条结果记录
            "deptno=10"); // 期望的第三条结果记录
  }

  @Test void simpleInnerBatchJoinTestSQL() { // 测试方法：使用SQL语句测试简单的内连接批处理场景
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR模式作为测试数据
        .query("select e.name from emps e join depts d on d.deptno = e.deptno") // 执行SQL查询：选择员工姓名，通过部门编号连接员工表和部门表
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered("name=Bill", // 验证查询结果，期望包含Bill的记录，不关心顺序
            "name=Sebastian", // 期望包含Sebastian的记录
            "name=Theodore"); // 期望包含Theodore的记录
  }

  @Test void simpleLeftBatchJoinTestSQL() { // 测试方法：使用SQL语句测试简单的左外连接批处理场景
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR模式作为测试数据
        .query( // 执行SQL查询
            "select e.name, d.deptno from emps e left join depts d on d.deptno = e.deptno") // 选择员工姓名和部门编号，使用左外连接，即使没有匹配的部门也返回员工记录
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered("name=Bill; deptno=10", // 验证查询结果，期望Bill属于部门10，不关心顺序
            "name=Eric; deptno=null", // 期望Eric没有匹配的部门，deptno为null，这是左外连接的特性
            "name=Sebastian; deptno=10", // 期望Sebastian属于部门10
            "name=Theodore; deptno=10"); // 期望Theodore属于部门10
  }

  @Test void innerBatchJoinTestSQL() { // 测试方法：使用大型HR模式测试内连接批处理场景，验证批量数据处理能力
    tester(false, new HrSchemaBig()) // 创建测试器，不强制去相关化，使用大型HR模式作为测试数据，包含更多记录
        .query( // 执行SQL查询
            "select count(e.name) from emps e join depts d on d.deptno = e.deptno") // 统计有匹配部门的员工数量，通过部门编号连接员工表和部门表
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered("EXPR$0=46"); // 验证查询结果，期望有46名员工有匹配的部门，EXPR$0是count函数的默认别名
  }

  @Test void innerBatchJoinTestSQL2() { // 测试方法：使用不同的连接条件测试内连接批处理场景，验证不同字段连接的正确性
    tester(false, new HrSchemaBig()) // 创建测试器，不强制去相关化，使用大型HR模式作为测试数据
        .query( // 执行SQL查询
            "select count(e.name) from emps e join depts d on d.deptno = e.empid") // 统计部门编号与员工ID匹配的记录数，这种连接条件在实际业务中较少见，主要用于测试
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered("EXPR$0=4"); // 验证查询结果，期望有4条记录满足部门编号等于员工ID的条件
  }

  @Test void leftBatchJoinTestSQL() { // 测试方法：使用大型HR模式测试左外连接批处理场景，并添加过滤条件
    tester(false, new HrSchemaBig()) // 创建测试器，不强制去相关化，使用大型HR模式作为测试数据
        .query( // 执行SQL查询
            "select count(d.deptno) from depts d left join emps e on d.deptno = e.deptno" // 统计部门编号，使用左外连接，即使没有匹配的员工也返回部门记录
            + " where d.deptno <30 and d.deptno>10") // 添加过滤条件，只统计部门编号在10到30之间的部门
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered("EXPR$0=8"); // 验证查询结果，期望有8条记录满足条件，注意count会忽略null值
  }

  @Test void testJoinSubQuery() { // 测试方法：测试包含子查询的NOT IN操作，验证批处理连接对复杂查询的支持
    String sql = "SELECT count(name) FROM emps e WHERE e.deptno NOT IN " // 定义SQL查询字符串，统计部门编号不在子查询结果中的员工数量
        + "(SELECT d.deptno FROM depts d WHERE d.name = 'Sales')"; // 子查询：选择名称为'Sales'的部门编号
    tester(false, new HrSchemaBig()) // 创建测试器，不强制去相关化，使用大型HR模式作为测试数据
        .query(sql) // 执行SQL查询
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，避免使用合并连接
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，避免使用常规连接
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered("EXPR$0=23"); // 验证查询结果，期望有23名员工的部门编号不在Sales部门中
  }

  @Test void testInnerJoinOnString() { // 测试方法：测试基于字符串字段的内连接，验证批处理连接对字符串类型字段的支持
    String sql = "SELECT d.name, e.salary FROM depts d join emps e on d.name = e.name"; // 定义SQL查询字符串，选择部门名称和员工薪资，通过名称字段连接部门表和员工表
    tester(false, new HrSchemaBig()) // 创建测试器，不强制去相关化，使用大型HR模式作为测试数据
        .query(sql) // 执行SQL查询
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，避免使用合并连接
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，避免使用常规连接
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered(""); // 验证查询结果，期望返回空结果集，因为部门名称和员工名称通常不会匹配
  }
  @Test void testSemiJoin() { // 测试方法：测试半连接操作，验证批处理连接对半连接的支持，半连接只返回左侧表中在右侧表中存在匹配的行
    tester(false, new HrSchemaBig()) // 创建测试器，不强制去相关化，使用大型HR模式作为测试数据
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，避免使用合并连接
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，避免使用常规连接
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .withRel( // 使用关系构建器API构建测试查询
            builder -> builder // 获取关系构建器实例
                .scan("s", "emps").as("e") // 扫描模式s中的emps表，并设置别名为e
                .scan("s", "depts").as("d") // 扫描模式s中的depts表，并设置别名为d
                .semiJoin( // 执行半连接操作，只返回左侧表中在右侧表中存在匹配的行
                    builder.equals( // 构建等值连接条件
                        builder.field(2, "e", "empid"), // 获取e表的empid字段，2表示输入数量
                        builder.field(2, "d", "deptno"))) // 获取d表的deptno字段，与e表的empid进行等值比较
                .project( // 执行投影操作，选择需要的输出字段
                    builder.field("name")) // 选择name字段作为输出
                .build()) // 构建完整的关系表达式树
        .returnsUnordered( // 验证查询结果，不关心结果的顺序
            "name=Emmanuel", // 期望包含Emmanuel的记录
            "name=Gabriel", // 期望包含Gabriel的记录
            "name=Michelle", // 期望包含Michelle的记录
            "name=Ursula"); // 期望包含Ursula的记录
  }

  @Test void testAntiJoin() { // 测试方法：测试反连接操作，验证批处理连接对反连接的支持，反连接只返回左侧表中在右侧表中不存在匹配的行
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR模式作为测试数据
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，避免使用合并连接
          planner.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，避免使用常规连接
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .withRel( // 使用关系构建器API构建测试查询
            builder -> builder // 获取关系构建器实例
                .scan("s", "emps").as("e") // 扫描模式s中的emps表，并设置别名为e
                .scan("s", "emps").as("e2") // 再次扫描模式s中的emps表，并设置别名为e2，实现自连接
                .antiJoin( // 执行反连接操作，只返回左侧表中在右侧表中不存在匹配的行
                    builder.and( // 构建AND逻辑表达式，连接多个条件
                        builder.equals( // 第一个条件：部门编号相等
                            builder.field(2, "e", "deptno"), // 获取e表的deptno字段，2表示输入数量
                            builder.field(2, "e2", "deptno")), // 获取e2表的deptno字段，与e表的deptno进行等值比较
                        builder.call( // 第二个条件：薪资大于
                            SqlStdOperatorTable.GREATER_THAN, // 使用大于操作符
                            builder.field(2, "e2", "salary"), // 获取e2表的salary字段作为比较的左操作数
                            builder.field(2, "e", "salary")))) // 获取e表的salary字段作为比较的右操作数，表示e2表的薪资大于e表的薪资
                .project( // 执行投影操作，选择需要的输出字段
                    builder.field("name"), // 选择name字段作为输出
                    builder.field("salary")) // 选择salary字段作为输出
                .build()) // 构建完整的关系表达式树
        .returnsUnordered( // 验证查询结果，不关心结果的顺序
            "name=Theodore; salary=11500.0", // 期望包含Theodore的记录，薪资为11500.0，表示该部门中没有薪资比他更高的员工
            "name=Eric; salary=8000.0"); // 期望包含Eric的记录，薪资为8000.0，表示该部门中没有薪资比他更高的员工
  }

  @Test void innerBatchJoinAndTestSQL() { // 测试方法：测试包含多个连接条件的内连接，验证批处理连接对复合条件的支持
    tester(false, new HrSchemaBig()) // 创建测试器，不强制去相关化，使用大型HR模式作为测试数据
        .query( // 执行SQL查询
            "select count(e.name) from emps e join depts d on d.deptno = e.empid and d.deptno = e.deptno") // 统计员工数量，使用AND连接多个条件：部门编号等于员工ID且部门编号等于员工部门编号
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          planner.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加批处理嵌套循环连接规则，强制使用批处理连接
        })
        .returnsUnordered("EXPR$0=1"); // 验证查询结果，期望只有1条记录同时满足两个条件，即员工ID等于部门编号且员工部门编号也等于部门编号
  }

  /** Test case for // 测试用例，用于验证
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4261">[CALCITE-4261] // JIRA问题CALCITE-4261
   * Join with three tables causes IllegalArgumentException // 三表连接导致IllegalArgumentException异常
   * in EnumerableBatchNestedLoopJoinRule</a>. */ // 在可枚举批处理嵌套循环连接规则中
  @Test void doubleInnerBatchJoinTestSQL() { // 测试方法：测试三表连接场景，验证批处理连接对多表连接的支持，同时修复JIRA-4261问题
    tester(false, new HrSchema()) // 创建测试器，不强制去相关化，使用HR模式作为测试数据
        .query("select e.name, d.name as dept, l.name as location " // 执行SQL查询，选择员工姓名、部门名称和位置名称
            + "from emps e join depts d on d.deptno <> e.salary " // 第一个连接：员工表和部门表，使用不等于条件连接部门编号和员工薪资
            + "join locations l on e.empid <> l.empid and d.deptno = l.empid") // 第二个连接：连接位置表，使用不等于条件连接员工ID和位置员工ID，并使用等于条件连接部门编号和位置员工ID
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> { // 设置优化器钩子，在优化器初始化时执行自定义配置
          planner.removeRule(EnumerableRules.ENUMERABLE_CORRELATE_RULE); // 移除可枚举相关规则，避免使用相关子查询优化
          // Use a small batch size, otherwise we will run into Janino's // 使用较小的批处理大小，否则会遇到Janino的
          // "InternalCompilerException: Code of method grows beyond 64 KB". // "InternalCompilerException：方法代码超过64KB"异常
          planner.addRule( // 添加批处理嵌套循环连接规则
              EnumerableBatchNestedLoopJoinRule.Config.DEFAULT.withBatchSize(10).toRule()); // 使用默认配置，但将批处理大小设置为10，以避免生成过大的代码
        })
        .explainContains("EnumerableBatchNestedLoopJoin") // 验证执行计划中包含可枚举批处理嵌套循环连接节点
        .returnsUnordered("name=Bill; dept=Sales; location=San Francisco", // 验证查询结果，期望包含Bill的记录，部门为Sales，位置为San Francisco，不关心顺序
            "name=Eric; dept=Sales; location=San Francisco", // 期望包含Eric的记录，部门为Sales，位置为San Francisco
            "name=Sebastian; dept=Sales; location=San Francisco", // 期望包含Sebastian的记录，部门为Sales，位置为San Francisco
            "name=Theodore; dept=Sales; location=San Francisco"); // 期望包含Theodore的记录，部门为Sales，位置为San Francisco
  }

  private CalciteAssert.AssertThat tester(boolean forceDecorrelate, // 私有辅助方法：创建Calcite断言测试器，用于配置测试环境
      Object schema) { // 参数：forceDecorrelate是否强制去相关化，schema测试用的模式对象
    return CalciteAssert.that() // 创建Calcite断言测试器实例
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 设置词法分析器为JAVA风格，使用双引号标识符，反斜杠转义
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, forceDecorrelate) // 设置是否强制去相关化，将相关子查询转换为非相关子查询
        .withSchema("s", new ReflectiveSchema(schema)); // 注册模式，使用反射模式将Java对象包装为数据库模式，别名为"s"
  }
} // 类结束：可枚举批处理嵌套循环连接测试类
