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
package org.apache.calcite.adapter.enumerable; // 声明该类属于 org.apache.calcite.adapter.enumerable 包，这是 Calcite 中可枚举适配器相关的包

import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入 Java 类型工厂实现类，用于创建和管理 Java 类型系统
import org.apache.calcite.linq4j.tree.ClassDeclaration; // 导入类声明接口，用于表示生成的 Java 类的声明
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于将表达式树转换为字符串
import org.apache.calcite.plan.ConventionTraitDef; // 导入约定特质定义，用于定义关系代数操作的约定（如可枚举约定）
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，是关系表达式树的根节点，包含共享对象
import org.apache.calcite.plan.RelTraitSet; // 导入关系特质集合，用于描述关系节点的物理属性
import org.apache.calcite.plan.volcano.VolcanoPlanner; // 导入火山优化器，Calcite 的基于成本的优化器实现
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，是关系代数表达式树的基类
import org.apache.calcite.rel.RelRoot; // 导入关系根节点，表示完整的关系表达式树的根
import org.apache.calcite.rel.rules.CoreRules; // 导入核心规则集合，包含常用的优化规则
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rex.RexBuilder; // 导入行表达式构建器，用于构建行表达式（RexNode）
import org.apache.calcite.sql.SqlNode; // 导入 SQL 节点接口，表示抽象语法树中的 SQL 节点
import org.apache.calcite.sql.parser.SqlParseException; // 导入 SQL 解析异常类，用于处理 SQL 解析错误
import org.apache.calcite.sql.parser.SqlParser; // 导入 SQL 解析器，用于将 SQL 文本解析为抽象语法树
import org.apache.calcite.sql.test.SqlTestFactory; // 导入 SQL 测试工厂，用于创建测试所需的 SQL 相关对象
import org.apache.calcite.sql.validate.SqlValidator; // 导入 SQL 验证器，用于验证 SQL 语句的语义正确性
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入 SQL 到关系代数转换器，用于将 SQL 转换为关系表达式树

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.util.HashMap; // 导入 HashMap 类，用于存储键值对映射

import static org.junit.jupiter.api.Assertions.assertFalse; // 导入 JUnit 5 的 assertFalse 静态方法，用于断言条件为假

/**
 * Unit Tests for code generator. // 代码生成器的单元测试类，用于测试 Calcite 的代码生成功能
 * 
 * 该类专门用于测试 Calcite 框架中的代码生成器组件，代码生成器负责将关系代数表达式树
 * 转换为可执行的 Java 代码。这是 Calcite 可枚举适配器的核心功能之一，允许查询在 Java
 * 环境中高效执行。
 * 
 * 主要测试场景包括：
 * - CASE WHEN 表达式的代码生成优化
 * - 避免生成重复的代码片段
 * - 确保生成的代码简洁高效
 * 
 * 测试方法使用 VolcanoPlanner 优化器进行查询优化，然后通过 EnumerableRelImplementor
 * 将优化后的关系表达式树转换为 Java 代码，最后验证生成的代码是否符合预期。
 */
public class CodeGeneratorTest { // 定义 CodeGeneratorTest 测试类，用于测试代码生成器的功能

  /** Test case for // 测试用例，用于验证特定问题的修复
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6740">[CALCITE-6740] // 引用 JIRA 问题编号 CALCITE-6740
   * Case statements may generate too many same code</a>. */ // 问题描述：CASE 语句可能会生成过多相同的代码
  @Test public void testCaseWhen() throws SqlParseException { // 测试方法，测试 CASE WHEN 表达式的代码生成，可能抛出 SQL 解析异常
    String sql = "with \"t\" as (select case " // 定义 SQL 语句，使用 WITH 子句创建临时表 t，包含 CASE WHEN 表达式
        + "when \"store_id\" = 1 then '1' " // 当 store_id 等于 1 时返回 '1'
        + "when \"store_id\" = 2 then '2' " // 当 store_id 等于 2 时返回 '2'
        + "when \"store_id\" = 3 then '3' " // 当 store_id 等于 3 时返回 '3'
        + "when \"store_id\" = 4 then '4' " // 当 store_id 等于 4 时返回 '4'
        + "else '0' end as \"myid\" from (values (1),(2),(3),(6)) as \"tt\"(\"store_id\"))\n" // 否则返回 '0'，数据源是 VALUES 子句创建的临时表
        + "select case " // 从临时表 t 中查询，再次使用 CASE WHEN 表达式
        + "when \"myid\" = '1' then '11' " // 当 myid 等于 '1' 时返回 '11'
        + "when \"myid\" = '2' then '22' " // 当 myid 等于 '2' 时返回 '22'
        + "when \"myid\" = '3' then '33' " // 当 myid 等于 '3' 时返回 '33'
        + "when "\"myid\" = '4' then '44' " // 当 myid 等于 '4' 时返回 '44'
        + "else '0' end as \"res\" from \"t\""; // 否则返回 '0'，结果列别名为 res

    VolcanoPlanner planner = new VolcanoPlanner(); // 创建火山优化器实例，这是 Calcite 的基于成本的优化器
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 向优化器添加约定特质定义，用于管理关系操作的约定
    planner.addRule(CoreRules.PROJECT_TO_CALC); // 添加 PROJECT_TO_CALC 规则，将投影节点转换为计算节点
    planner.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举计算规则，将计算节点转换为可枚举计算节点
    planner.addRule(EnumerableRules.ENUMERABLE_VALUES_RULE); // 添加可枚举值规则，将值节点转换为可枚举值节点

    SqlTestFactory factory = SqlTestFactory.INSTANCE.withPlannerFactory(context -> planner); // 创建 SQL 测试工厂，并设置自定义的优化器工厂
    SqlParser parser = factory.createParser(sql); // 使用工厂创建 SQL 解析器，传入 SQL 语句
    SqlNode sqlNode = parser.parseQuery(); // 解析 SQL 语句，生成抽象语法树（AST）的根节点
    SqlToRelConverter converter = factory.createSqlToRelConverter(); // 创建 SQL 到关系代数转换器
    SqlValidator validator = converter.validator; // 获取转换器中的 SQL 验证器
    SqlNode validated = validator.validate(sqlNode); // 验证 SQL 节点的语义正确性，返回验证后的 SQL 节点
    RelRoot relRoot = converter.convertQuery(validated, false, true); // 将验证后的 SQL 转换为关系表达式树，返回关系根节点

    RelDataTypeFactory typeFactory = // 创建 Java 类型工厂实例，用于管理关系数据类型
        new JavaTypeFactoryImpl(org.apache.calcite.rel.type.RelDataTypeSystem.DEFAULT); // 使用默认的关系数据类型系统
    RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(typeFactory)); // 创建关系优化集群，传入优化器和行表达式构建器
    RelTraitSet desiredTraits = // 创建期望的关系特质集合
        cluster.traitSet().replace(EnumerableConvention.INSTANCE); // 将特质集合中的约定替换为可枚举约定

    RelNode rel = planner.changeTraits(relRoot.rel, desiredTraits); // 改变关系节点的特质，使其符合可枚举约定
    planner.setRoot(rel); // 将修改后的关系节点设置为优化器的根节点

    EnumerableRel plan = (EnumerableRel) planner.findBestExp(); // 执行优化，找到最优的可枚举关系表达式
    EnumerableRelImplementor relImplementor = // 创建可枚举关系实现器，用于将关系表达式转换为 Java 代码
        new EnumerableRelImplementor(plan.getCluster().getRexBuilder(), new HashMap<>()); // 传入行表达式构建器和空的参数映射
    ClassDeclaration classExpr = relImplementor.implementRoot(plan, EnumerableRel.Prefer.ARRAY); // 实现关系表达式根节点，生成类声明，偏好数组形式
    String javaCode = // 将类声明转换为 Java 代码字符串
        Expressions.toString(classExpr.memberDeclarations, "\n", false); // 使用换行符分隔，不包含缩进
    assertFalse(javaCode.contains("case_when_value1")); // 断言生成的代码不包含 "case_when_value1"，验证没有生成重复的临时变量
    assertFalse(javaCode.contains("case_when_value2")); // 断言生成的代码不包含 "case_when_value2"，验证没有生成重复的临时变量
    assertFalse(javaCode.contains("case_when_value3")); // 断言生成的代码不包含 "case_when_value3"，验证没有生成重复的临时变量
    assertFalse(javaCode.contains("case_when_value4")); // 断言生成的代码不包含 "case_when_value4"，验证没有生成重复的临时变量
  }
} // 类定义结束
