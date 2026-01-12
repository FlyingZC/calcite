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
// Apache许可证声明，这是一个开源项目，遵循Apache 2.0许可证
package org.apache.calcite.runtime; // 声明包名，这个类属于org.apache.calcite.runtime包

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入反射模式类，用于通过反射将Java对象映射为数据库表
import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性配置类，用于配置Calcite连接的各种属性
import org.apache.calcite.config.Lex; // 导入词法分析配置类，用于配置SQL词法分析规则
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器接口，负责执行查询优化规则
import org.apache.calcite.rel.core.JoinRelType; // 导入连接关系类型枚举，定义INNER、LEFT、RIGHT等连接类型
import org.apache.calcite.rel.rules.CoreRules; // 导入核心优化规则集合，包含Calcite的所有核心优化规则
import org.apache.calcite.test.CalciteAssert; // 导入Calcite测试断言工具类，用于编写测试用例
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HR模式测试数据，包含部门、员工、受抚养人等表结构

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.function.Consumer; // 导入Java函数式接口，用于定义消费者函数

/**
 * Unit tests for rules in {@code org.apache.calcite.rel} and subpackages.
 */
// 类注释：RelOptRulesRuntimeTest是一个单元测试类，用于测试org.apache.calcite.rel及其子包中的优化规则
// 这个类主要关注规则在运行时的行为，特别是规则转换的正确性和有效性
// 测试重点包括连接转换规则、半连接转换规则等核心优化逻辑
public class RelOptRulesRuntimeTest { // 定义测试类，继承自Object，使用JUnit5测试框架

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5952">[CALCITE-5952]
   * SemiJoinJoinTransposeRule should check if JoinType supports pushing predicates
   * into its inputs</a>. */
  // 方法注释：测试SemiJoinJoinTransposeRule规则在左外连接场景下的行为
  // 这个测试用例针对JIRA issue CALCITE-5952，验证半连接连接转换规则是否正确检查连接类型是否支持将谓词下推到其输入
  // 测试场景：depts LEFT JOIN emps，然后与dependents进行SEMI JOIN
  // 预期行为：由于LEFT JOIN不支持将谓词下推到左输入，规则应该正确处理这种情况
  @Test void semiJoinLeftJoinTransposeTest() { // 标记为测试方法，测试左外连接与半连接的转换
    tester(true, new HrSchema()) // 调用tester辅助方法，强制去相关化并使用HR模式作为测试数据
        .withRel( // 使用RelBuilder构建关系表达式树
            builder -> builder.scan("s", "depts") // 扫描s模式下的depts表（部门表），作为第一个输入
                .scan("s", "emps") // 扫描s模式下的emps表（员工表），作为第二个输入
                .join(JoinRelType.LEFT, // 创建左外连接，保留左侧depts表的所有记录
                    builder.equals( // 构建等值连接条件
                        builder.field(2, 0, "deptno"), // 引用第一个输入（depts）的deptno字段，2表示总共2个输入，0表示第一个输入
                        builder.field(2, 1, "deptno")) // 引用第二个输入（emps）的deptno字段，1表示第二个输入
                ) // 完成左外连接构建，结果包含所有部门信息，即使没有匹配的员工
                .scan("s", "dependents") // 扫描s模式下的dependents表（受抚养人表），作为半连接的右输入
                .semiJoin( // 创建半连接，只返回左输入中与右输入匹配的记录
                    builder.equals( // 构建半连接的等值条件
                    builder.field(2, 0, "empid"), // 引用左输入（左外连接结果）的empid字段，2表示总共2个输入，0表示第一个输入
                    builder.field(2, 1, "empid"))) // 引用右输入（dependents）的empid字段，1表示第二个输入
                .build() // 构建完整的关系表达式树
        ) // 完成关系表达式树的构建
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置优化器钩子，在优化器上执行自定义操作
            planner.addRule(CoreRules.SEMI_JOIN_JOIN_TRANSPOSE) // 向优化器添加SemiJoinJoinTransposeRule规则，该规则负责转换半连接和连接的顺序
        ) // 完成钩子设置
        .returnsUnordered(); // 执行测试并验证结果，不关心结果的顺序，只验证结果集是否正确
  } // 方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5952">[CALCITE-5952]
   * SemiJoinJoinTransposeRule should check if JoinType supports pushing predicates
   * into its inputs</a>. */
  // 方法注释：测试SemiJoinJoinTransposeRule规则在右外连接场景下的行为
  // 这个测试用例同样针对JIRA issue CALCITE-5952，验证半连接连接转换规则是否正确检查连接类型是否支持将谓词下推到其输入
  // 测试场景：emps RIGHT JOIN depts（等价于depts LEFT JOIN emps），然后与dependents进行SEMI JOIN
  // 预期行为：由于RIGHT JOIN不支持将谓词下推到右输入，规则应该正确处理这种情况
  @Test void semiJoinRightJoinTransposeTest() { // 标记为测试方法，测试右外连接与半连接的转换
    tester(true, new HrSchema()) // 调用tester辅助方法，强制去相关化并使用HR模式作为测试数据
        .withRel( // 使用RelBuilder构建关系表达式树
            builder -> builder.scan("s", "emps") // 扫描s模式下的emps表（员工表），作为第一个输入
                .scan("s", "depts") // 扫描s模式下的depts表（部门表），作为第二个输入
                .join(JoinRelType.RIGHT, // 创建右外连接，保留右侧depts表的所有记录
                    builder.equals( // 构建等值连接条件
                        builder.field(2, 0, "deptno"), // 引用第一个输入（emps）的deptno字段，2表示总共2个输入，0表示第一个输入
                        builder.field(2, 1, "deptno")) // 引用第二个输入（depts）的deptno字段，1表示第二个输入
                ) // 完成右外连接构建，结果包含所有部门信息，即使没有匹配的员工
                .scan("s", "dependents") // 扫描s模式下的dependents表（受抚养人表），作为半连接的右输入
                .semiJoin( // 创建半连接，只返回左输入中与右输入匹配的记录
                    builder.equals( // 构建半连接的等值条件
                    builder.field(2, 0, "empid"), // 引用左输入（右外连接结果）的empid字段，2表示总共2个输入，0表示第一个输入
                    builder.field(2, 1, "empid"))) // 引用右输入（dependents）的empid字段，1表示第二个输入
                .build() // 构建完整的关系表达式树
        ) // 完成关系表达式树的构建
        .withHook(Hook.PLANNER, (Consumer<RelOptPlanner>) planner -> // 设置优化器钩子，在优化器上执行自定义操作
            planner.addRule(CoreRules.SEMI_JOIN_JOIN_TRANSPOSE) // 向优化器添加SemiJoinJoinTransposeRule规则，该规则负责转换半连接和连接的顺序
        ) // 完成钩子设置
        .returnsUnordered(); // 执行测试并验证结果，不关心结果的顺序，只验证结果集是否正确
  } // 方法结束

  // 方法注释：tester是一个私有辅助方法，用于创建和配置CalciteAssert测试环境
  // 这个方法封装了测试环境的公共配置，避免在每个测试方法中重复相同的配置代码
  // 参数说明：
  //   - forceDecorrelate: 是否强制去相关化，true表示将子查询转换为连接
  //   - schema: 测试使用的模式对象，通常是包含表结构的Java对象
  // 返回值：返回配置好的CalciteAssert.AssertThat对象，可以继续链式调用配置测试场景
  private CalciteAssert.AssertThat tester(boolean forceDecorrelate, Object schema) { // 定义私有辅助方法，接收去相关化标志和模式对象
    return CalciteAssert.that() // 创建CalciteAssert测试断言对象，用于构建测试场景
        .with(CalciteConnectionProperty.LEX, Lex.JAVA) // 配置词法分析器使用JAVA模式，支持Java风格的SQL语法（如使用点号访问字段）
        .with(CalciteConnectionProperty.FORCE_DECORRELATE, forceDecorrelate) // 配置是否强制去相关化，true表示将子查询转换为连接操作
        .withSchema("s", new ReflectiveSchema(schema)); // 配置测试模式，将Java对象映射为名为"s"的数据库模式
  } // 方法结束
} // 类定义结束
