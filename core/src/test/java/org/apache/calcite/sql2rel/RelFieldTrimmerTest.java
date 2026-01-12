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
package org.apache.calcite.sql2rel; // RelFieldTrimmerTest类所在的包路径，属于SQL到关系代数转换模块

import org.apache.calcite.plan.RelTraitDef; // 导入关系特征定义类，用于定义关系的物理属性
import org.apache.calcite.plan.hep.HepPlanner; // 导入HepPlanner启发式规划器，用于基于规则的优化
import org.apache.calcite.plan.hep.HepProgram; // 导入HepProgram，定义HepPlanner的优化程序
import org.apache.calcite.plan.hep.HepProgramBuilder; // 导入HepProgramBuilder，用于构建HepProgram
import org.apache.calcite.rel.RelCollations; // 导入RelCollations，用于处理关系的排序属性
import org.apache.calcite.rel.RelDistributions; // 导入RelDistributions，用于处理关系的分布属性
import org.apache.calcite.rel.RelNode; // 导入RelNode，关系代数节点的基类
import org.apache.calcite.rel.core.Aggregate; // 导入Aggregate聚合操作节点
import org.apache.calcite.rel.core.Calc; // 导入Calc计算节点，结合了投影和过滤
import org.apache.calcite.rel.core.Join; // 导入Join连接操作节点
import org.apache.calcite.rel.core.JoinRelType; // 导入JoinRelType，定义连接类型（内连接、外连接等）
import org.apache.calcite.rel.core.Project; // 导入Project投影操作节点
import org.apache.calcite.rel.hint.HintPredicates; // 导入HintPredicates，用于定义hint的谓词
import org.apache.calcite.rel.hint.HintStrategyTable; // 导入HintStrategyTable，定义hint的策略表
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint，关系hint，用于给优化器提供提示
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules，核心优化规则集合
import org.apache.calcite.rex.RexCorrelVariable; // 导入RexCorrelVariable，相关变量，用于相关子查询
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus，扩展的Schema接口
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入标准SQL操作符表
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser，SQL解析器
import org.apache.calcite.test.CalciteAssert; // 导入CalciteAssert，测试工具类
import org.apache.calcite.tools.Frameworks; // 导入Frameworks，框架配置工具
import org.apache.calcite.tools.Programs; // 导入Programs，预定义的优化程序
import org.apache.calcite.tools.RelBuilder; // 导入RelBuilder，关系代数构建器
import org.apache.calcite.util.Holder; // 导入Holder，用于持有可变值

import com.google.common.collect.Lists; // 导入Guava的Lists工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解

import java.util.Collections; // 导入Java集合工具类
import java.util.List; // 导入List接口

import static org.apache.calcite.test.Matchers.hasTree; // 导入hasTree匹配器，用于验证关系树结构

import static org.hamcrest.CoreMatchers.instanceOf; // 导入instanceOf匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言工具
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入assertTrue断言方法

/** Test for {@link RelFieldTrimmer}. */ // RelFieldTrimmer的测试类，用于测试字段修剪功能
class RelFieldTrimmerTest { // RelFieldTrimmerTest类定义，包含各种测试用例来验证RelFieldTrimmer的正确性
  public static Frameworks.ConfigBuilder config() { // 创建并返回一个Frameworks配置构建器，用于配置Calcite框架
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根Schema，true表示启用元数据
    return Frameworks.newConfigBuilder() // 创建新的配置构建器
        .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
        .defaultSchema( // 设置默认Schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL)) // 添加SCOTT_WITH_TEMPORAL Schema到根Schema
        .traitDefs((List<RelTraitDef>) null) // 设置特征定义为null，使用默认特征
        .programs(Programs.heuristicJoinOrder(Programs.RULE_SET, true, 2)); // 设置优化程序为启发式连接顺序优化，使用规则集，启用强制枚举，最大迭代次数为2
  }

  @Test void testSortExchangeFieldTrimmer() { // 测试方法：测试SortExchange节点的字段修剪功能
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例，使用配置构建器
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影EMPNO、ENAME、DEPTNO三个字段
            .sortExchange(RelDistributions.hash(Lists.newArrayList(1)), RelCollations.of(0)) // 添加SortExchange节点，使用哈希分布（基于字段1）和排序（基于字段0）
            .project(builder.field("EMPNO"), builder.field("ENAME")) // 再次投影，只保留EMPNO和ENAME字段
            .build(); // 构建关系树

    RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例，传入null和builder
    RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪，返回修剪后的关系节点

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalSortExchange(distribution=[hash[1]], collation=[[0]])\n" // 期望的SortExchange节点，哈希分布基于字段1，排序基于字段0
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // 期望的Project节点，只保留EMPNO和ENAME
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // 期望的TableScan节点，扫描EMP表
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望的树结构匹配
  }

  @Test void testSortExchangeFieldTrimmerWhenProjectCannotBeMerged() { // 测试方法：测试当Project无法合并时SortExchange的字段修剪
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .sortExchange(RelDistributions.hash(Lists.newArrayList(1)), RelCollations.of(0)) // 添加SortExchange，哈希分布基于字段1，排序基于字段0
            .project(builder.field("EMPNO")) // 再次投影，只保留EMPNO字段
            .build(); // 构建关系树

    RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalProject(EMPNO=[$0])\n" // 期望最终的Project节点，只输出EMPNO
        + "  LogicalSortExchange(distribution=[hash[1]], collation=[[0]])\n" // SortExchange节点保持不变
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // 中间的Project节点保留EMPNO和ENAME（因为ENAMe被SortExchange使用）
        + "      LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testSortExchangeFieldTrimmerWithEmptyCollation() { // 测试方法：测试SortExchange使用空排序时的字段修剪
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .sortExchange(RelDistributions.hash(Lists.newArrayList(1)), RelCollations.EMPTY) // 添加SortExchange，哈希分布基于字段1，使用空排序
            .project(builder.field("EMPNO"), builder.field("ENAME")) // 投影EMPNO和ENAME
            .build(); // 构建关系树

    RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalSortExchange(distribution=[hash[1]], collation=[[]])\n" // SortExchange节点，哈希分布基于字段1，排序为空
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // Project节点，保留EMPNO和ENAME
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testSortExchangeFieldTrimmerWithSingletonDistribution() { // 测试方法：测试SortExchange使用单例分布时的字段修剪
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .sortExchange(RelDistributions.SINGLETON, RelCollations.of(0)) // 添加SortExchange，使用单例分布，排序基于字段0
            .project(builder.field("EMPNO"), builder.field("ENAME")) // 投影EMPNO和ENAME
            .build(); // 构建关系树

    RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalSortExchange(distribution=[single], collation=[[0]])\n" // SortExchange节点，单例分布，排序基于字段0
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // Project节点，保留EMPNO和ENAME
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testExchangeFieldTrimmer() { // 测试方法：测试Exchange节点的字段修剪功能
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .exchange(RelDistributions.hash(Lists.newArrayList(1))) // 添加Exchange节点，使用哈希分布（基于字段1）
            .project(builder.field("EMPNO"), builder.field("ENAME")) // 再次投影，只保留EMPNO和ENAME
            .build(); // 构建关系树

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalExchange(distribution=[hash[1]])\n" // Exchange节点，哈希分布基于字段1
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // Project节点，保留EMPNO和ENAME
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testExchangeFieldTrimmerWhenProjectCannotBeMerged() { // 测试方法：测试当Project无法合并时Exchange的字段修剪
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .exchange(RelDistributions.hash(Lists.newArrayList(1))) // 添加Exchange节点，哈希分布基于字段1
            .project(builder.field("EMPNO")) // 再次投影，只保留EMPNO
            .build(); // 构建关系树

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalProject(EMPNO=[$0])\n" // 最终的Project节点，只输出EMPNO
        + "  LogicalExchange(distribution=[hash[1]])\n" // Exchange节点，哈希分布基于字段1
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // 中间的Project节点保留EMPNO和ENAME（因为ENAME被Exchange使用）
        + "      LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testExchangeFieldTrimmerWithSingletonDistribution() { // 测试方法：测试Exchange使用单例分布时的字段修剪
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .exchange(RelDistributions.SINGLETON) // 添加Exchange节点，使用单例分布
            .project(builder.field("EMPNO"), builder.field("ENAME")) // 投影EMPNO和ENAME
            .build(); // 构建关系树

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalExchange(distribution=[single])\n" // Exchange节点，单例分布
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // Project节点，保留EMPNO和ENAME
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4055">[CALCITE-4055] // 引用JIRA问题链接
   * RelFieldTrimmer loses hints</a>. */ // 问题：RelFieldTrimmer丢失了hint
  @Test void testJoinWithHints() { // 测试方法：测试Join节点在字段修剪时保留hint
    final RelHint noHashJoinHint = RelHint.builder("no_hash_join").build(); // 创建一个名为"no_hash_join"的hint，用于禁用哈希连接
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    builder.getCluster().setHintStrategies( // 设置集群的hint策略
        HintStrategyTable.builder() // 创建hint策略表构建器
            .hintStrategy("no_hash_join", HintPredicates.JOIN) // 为"no_hash_join" hint设置策略，只应用于Join节点
            .build()); // 构建hint策略表
    final RelNode original = // 定义原始关系节点
        builder.scan("EMP") // 扫描EMP表
            .scan("DEPT") // 扫描DEPT表
            .join(JoinRelType.INNER, // 连接两个表，使用内连接
                builder.equals( // 连接条件：DEPTNO相等
                    builder.field(2, 0, "DEPTNO"), // 第一个输入（EMP）的DEPTNO字段
                    builder.field(2, 1, "DEPTNO"))) // 第二个输入（DEPT）的DEPTNO字段
            .hints(noHashJoinHint) // 给Join节点添加hint
            .project( // 投影操作
                builder.field("ENAME"), // 输出ENAME字段
                builder.field("DNAME")) // 输出DNAME字段
            .build(); // 构建关系树

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(original); // 对原始节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalProject(ENAME=[$1], DNAME=[$4])\n" // 期望的Project节点，输出ENAME和DNAME
        + "  LogicalJoin(condition=[=($2, $3)], joinType=[inner])\n" // Join节点，内连接
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // 左侧Project节点，保留EMPNO、ENAME、DEPTNO
        + "      LogicalTableScan(table=[[scott, EMP]])\n" // 左侧TableScan节点
        + "    LogicalProject(DEPTNO=[$0], DNAME=[$1])\n" // 右侧Project节点，保留DEPTNO、DNAME
        + "      LogicalTableScan(table=[[scott, DEPT]])\n"; // 右侧TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配

    assertThat(original.getInput(0), instanceOf(Join.class)); // 验证原始节点的第一个输入是Join节点
    final Join originalJoin = (Join) original.getInput(0); // 获取原始Join节点
    assertTrue(originalJoin.getHints().contains(noHashJoinHint)); // 验证原始Join节点包含hint

    assertThat(trimmed.getInput(0), instanceOf(Join.class)); // 验证修剪后节点的第一个输入是Join节点
    final Join join = (Join) trimmed.getInput(0); // 获取修剪后的Join节点
    assertTrue(join.getHints().contains(noHashJoinHint)); // 验证修剪后的Join节点仍然包含hint
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4055">[CALCITE-4055] // 引用JIRA问题链接
   * RelFieldTrimmer loses hints</a>. */ // 问题：RelFieldTrimmer丢失了hint
  @Test void testAggregateWithHints() { // 测试方法：测试Aggregate节点在字段修剪时保留hint
    final RelHint aggHint = RelHint.builder("resource").build(); // 创建一个名为"resource"的hint，用于指定资源分配
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    builder.getCluster().setHintStrategies( // 设置集群的hint策略
        HintStrategyTable.builder().hintStrategy("resource", HintPredicates.AGGREGATE).build()); // 为"resource" hint设置策略，只应用于Aggregate节点
    final RelNode original = // 定义原始关系节点
        builder.scan("EMP") // 扫描EMP表
            .aggregate( // 聚合操作
                builder.groupKey(builder.field("DEPTNO")), // 按DEPTNO分组
                builder.count(false, "C", builder.field("SAL"))) // 计算SAL的计数，别名为C
            .hints(aggHint) // 给Aggregate节点添加hint
            .build(); // 构建关系树

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(original); // 对原始节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalAggregate(group=[{1}], C=[COUNT($0)])\n" // Aggregate节点，按字段1分组，计算字段0的计数
        + "  LogicalProject(SAL=[$5], DEPTNO=[$7])\n" // Project节点，只保留SAL和DEPTNO
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配

    assertThat(original, instanceOf(Aggregate.class)); // 验证原始节点是Aggregate节点
    final Aggregate originalAggregate = (Aggregate) original; // 获取原始Aggregate节点
    assertTrue(originalAggregate.getHints().contains(aggHint)); // 验证原始Aggregate节点包含hint

    assertThat(trimmed, instanceOf(Aggregate.class)); // 验证修剪后节点是Aggregate节点
    final Aggregate aggregate = (Aggregate) trimmed; // 获取修剪后的Aggregate节点
    assertTrue(aggregate.getHints().contains(aggHint)); // 验证修剪后的Aggregate节点仍然包含hint
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6734">[CALCITE-6734] // 引用JIRA问题链接
   * RelFieldTrimmer should trim Aggregate's input fields which are arguments of // RelFieldTrimmer应该修剪Aggregate的输入字段，这些字段是
   * unused aggregate functions</a>. */ // 未使用的聚合函数的参数
  @Test void testTrimUnusedAggregateInput() { // 测试方法：测试修剪Aggregate中未使用的聚合函数参数
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode original = // 定义原始关系节点
        builder.scan("EMP") // 扫描EMP表
            .filter( // 过滤操作
                builder.greaterThan(builder.field("DEPTNO"), // 过滤条件：DEPTNO大于100
                    builder.literal(100)))
            .aggregate( // 聚合操作
                builder.groupKey(builder.field("DEPTNO")), // 按DEPTNO分组
                builder.sum(false, "SAL", builder.field("SAL")), // 计算SAL的总和，别名为SAL
                builder.count(false, "ENAME", builder.field("ENAME"))) // 计算ENAME的计数，别名为ENAME
            .project(builder.field("DEPTNO"), builder.field("SAL")) // 投影DEPTNO和SAL字段（不包含ENAME）
            .build(); // 构建关系树

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(original); // 对原始节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalAggregate(group=[{2}], SAL=[SUM($1)])\n" // Aggregate节点，按字段2分组，计算字段1的总和（ENAME的聚合被移除）
        + "  LogicalFilter(condition=[>($2, 100)])\n" // Filter节点，过滤条件是字段2大于100
        + "    LogicalProject(EMPNO=[$0], SAL=[$5], DEPTNO=[$7])\n" // Project节点，只保留EMPNO、SAL、DEPTNO（ENAME被移除）
        + "      LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4055">[CALCITE-4055] // 引用JIRA问题链接
   * RelFieldTrimmer loses hints</a>. */ // 问题：RelFieldTrimmer丢失了hint
  @Test void testProjectWithHints() { // 测试方法：测试Project节点在字段修剪时保留hint
    final RelHint projectHint = RelHint.builder("resource").build(); // 创建一个名为"resource"的hint，用于指定资源分配
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    builder.getCluster().setHintStrategies( // 设置集群的hint策略
        HintStrategyTable.builder().hintStrategy("resource", HintPredicates.PROJECT).build()); // 为"resource" hint设置策略，只应用于Project节点
    final RelNode original = // 定义原始关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), // 投影EMPNO字段
                builder.field("ENAME"), // 投影ENAME字段
                builder.field("DEPTNO")) // 投影DEPTNO字段
            .hints(projectHint) // 给Project节点添加hint
            .sort(builder.field("EMPNO")) // 按EMPNO排序
            .project(builder.field("EMPNO")) // 再次投影，只保留EMPNO
            .build(); // 构建关系树

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(original); // 对原始节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalSort(sort0=[$0], dir0=[ASC])\n" // Sort节点，按字段0升序排序
        + "  LogicalProject(EMPNO=[$0])\n" // Project节点，只输出EMPNO
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配

    assertThat(original.getInput(0).getInput(0), instanceOf(Project.class)); // 验证原始节点的第二个输入是Project节点
    final Project originalProject = (Project) original.getInput(0).getInput(0); // 获取原始Project节点
    assertTrue(originalProject.getHints().contains(projectHint)); // 验证原始Project节点包含hint

    assertThat(trimmed.getInput(0), instanceOf(Project.class)); // 验证修剪后节点的第一个输入是Project节点
    final Project project = (Project) trimmed.getInput(0); // 获取修剪后的Project节点
    assertTrue(project.getHints().contains(projectHint)); // 验证修剪后的Project节点仍然包含hint
  }

  @Test void testCalcFieldTrimmer0() { // 测试方法：测试Calc节点的字段修剪功能（简单投影场景）
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .exchange(RelDistributions.SINGLETON) // 添加Exchange节点，使用单例分布
            .project(builder.field("EMPNO"), builder.field("ENAME")) // 再次投影，只保留EMPNO和ENAME
            .build(); // 构建关系树

    final HepProgram hepProgram = new HepProgramBuilder(). // 创建HepProgramBuilder
        addRuleInstance(CoreRules.PROJECT_TO_CALC).build(); // 添加PROJECT_TO_CALC规则，将Project转换为Calc，并构建程序

    final HepPlanner hepPlanner = new HepPlanner(hepProgram); // 创建HepPlanner实例，传入优化程序
    hepPlanner.setRoot(root); // 设置根节点
    final RelNode relNode = hepPlanner.findBestExp(); // 执行优化，找到最佳表达式
    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(relNode); // 对优化后的节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalCalc(expr#0..1=[{inputs}], proj#0..1=[{exprs}])\n" // 顶层的Calc节点，表达式和投影
        + "  LogicalExchange(distribution=[single])\n" // Exchange节点，单例分布
        + "    LogicalCalc(expr#0..1=[{inputs}], proj#0..1=[{exprs}])\n" // 下层的Calc节点
        + "      LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // Project节点，保留EMPNO和ENAME
        + "        LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testCalcFieldTrimmer1() { // 测试方法：测试Calc节点的字段修剪功能（包含过滤的场景）
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), // 投影三个字段
                builder.field("DEPTNO"))
            .exchange(RelDistributions.SINGLETON) // 添加Exchange节点，使用单例分布
            .filter( // 过滤操作
                builder.greaterThan(builder.field("EMPNO"), // 过滤条件：EMPNO大于100
                    builder.literal(100)))
            .build(); // 构建关系树

    final HepProgram hepProgram = new HepProgramBuilder() // 创建HepProgramBuilder
        .addRuleInstance(CoreRules.PROJECT_TO_CALC) // 添加PROJECT_TO_CALC规则
        .addRuleInstance(CoreRules.FILTER_TO_CALC) // 添加FILTER_TO_CALC规则
        .build(); // 构建优化程序

    final HepPlanner hepPlanner = new HepPlanner(hepProgram); // 创建HepPlanner实例
    hepPlanner.setRoot(root); // 设置根节点
    final RelNode relNode = hepPlanner.findBestExp(); // 执行优化，找到最佳表达式
    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(relNode); // 对优化后的节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalCalc(expr#0..2=[{inputs}], expr#3=[100], expr#4=[>($t0, $t3)], proj#0." // Calc节点，包含表达式和条件
        + ".2=[{exprs}], $condition=[$t4])\n" // 条件表达式
        + "  LogicalExchange(distribution=[single])\n" // Exchange节点，单例分布
        + "    LogicalCalc(expr#0..2=[{inputs}], proj#0..2=[{exprs}])\n" // 下层的Calc节点
        + "      LogicalProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // Project节点
        + "        LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testCalcFieldTrimmer2() { // 测试方法：测试Calc节点的字段修剪功能（包含过滤和投影合并的场景）
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), builder.field("ENAME"), builder.field("DEPTNO")) // 投影三个字段
            .exchange(RelDistributions.SINGLETON) // 添加Exchange节点，使用单例分布
            .filter( // 过滤操作
                builder.greaterThan(builder.field("EMPNO"), // 过滤条件：EMPNO大于100
                    builder.literal(100)))
            .project(builder.field("EMPNO"), builder.field("ENAME")) // 再次投影，只保留EMPNO和ENAME
            .build(); // 构建关系树

    final HepProgram hepProgram = new HepProgramBuilder() // 创建HepProgramBuilder
        .addRuleInstance(CoreRules.PROJECT_TO_CALC) // 添加PROJECT_TO_CALC规则
        .addRuleInstance(CoreRules.FILTER_TO_CALC) // 添加FILTER_TO_CALC规则
        .addRuleInstance(CoreRules.CALC_MERGE).build(); // 添加CALC_MERGE规则，合并Calc节点

    final HepPlanner hepPlanner = new HepPlanner(hepProgram); // 创建HepPlanner实例
    hepPlanner.setRoot(root); // 设置根节点
    final RelNode relNode = hepPlanner.findBestExp(); // 执行优化，找到最佳表达式
    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(relNode); // 对优化后的节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalCalc(expr#0..1=[{inputs}], expr#2=[100], expr#3=[>($t0, $t2)], proj#0." // 合并后的Calc节点
        + ".1=[{exprs}], $condition=[$t3])\n" // 条件表达式
        + "  LogicalExchange(distribution=[single])\n" // Exchange节点，单例分布
        + "    LogicalCalc(expr#0..1=[{inputs}], proj#0..1=[{exprs}])\n" // 下层的Calc节点
        + "      LogicalProject(EMPNO=[$0], ENAME=[$1])\n" // Project节点
        + "        LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  @Test void testCalcWithHints() { // 测试方法：测试Calc节点在字段修剪时保留hint
    final RelHint calcHint = RelHint.builder("resource").build(); // 创建一个名为"resource"的hint，用于指定资源分配
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    builder.getCluster().setHintStrategies( // 设置集群的hint策略
        HintStrategyTable.builder().hintStrategy("resource", HintPredicates.CALC).build()); // 为"resource" hint设置策略，只应用于Calc节点
    final RelNode original = // 定义原始关系节点
        builder.scan("EMP") // 扫描EMP表
            .project(builder.field("EMPNO"), // 投影EMPNO字段
                builder.field("ENAME"), // 投影ENAME字段
                builder.field("DEPTNO")) // 投影DEPTNO字段
            .hints(calcHint) // 给Project节点添加hint（后续会转换为Calc）
            .sort(builder.field("EMPNO")) // 按EMPNO排序
            .project(builder.field("EMPNO")) // 再次投影，只保留EMPNO
            .build(); // 构建关系树

    final HepProgram hepProgram = new HepProgramBuilder() // 创建HepProgramBuilder
        .addRuleInstance(CoreRules.PROJECT_TO_CALC) // 添加PROJECT_TO_CALC规则
        .build(); // 构建优化程序
    final HepPlanner hepPlanner = new HepPlanner(hepProgram); // 创建HepPlanner实例
    hepPlanner.setRoot(original); // 设置根节点
    final RelNode relNode = hepPlanner.findBestExp(); // 执行优化，找到最佳表达式

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(relNode); // 对优化后的节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalCalc(expr#0=[{inputs}], EMPNO=[$t0])\n" // 顶层的Calc节点
        + "  LogicalSort(sort0=[$0], dir0=[ASC])\n" // Sort节点，按字段0升序排序
        + "    LogicalCalc(expr#0=[{inputs}], EMPNO=[$t0])\n" // 下层的Calc节点（由Project转换而来）
        + "      LogicalProject(EMPNO=[$0])\n" // Project节点
        + "        LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配

    assertThat(original.getInput(0).getInput(0), instanceOf(Project.class)); // 验证原始节点的第二个输入是Project节点
    final Project originalProject = (Project) original.getInput(0).getInput(0); // 获取原始Project节点
    assertTrue(originalProject.getHints().contains(calcHint)); // 验证原始Project节点包含hint

    assertThat(relNode.getInput(0).getInput(0), instanceOf(Calc.class)); // 验证优化后节点的第二个输入是Calc节点
    final Calc originalCalc = (Calc) relNode.getInput(0).getInput(0); // 获取优化后的Calc节点
    assertTrue(originalCalc.getHints().contains(calcHint)); // 验证优化后的Calc节点包含hint

    assertThat(trimmed.getInput(0).getInput(0), instanceOf(Calc.class)); // 验证修剪后节点的第二个输入是Calc节点
    final Calc calc = (Calc) trimmed.getInput(0).getInput(0); // 获取修剪后的Calc节点
    assertTrue(calc.getHints().contains(calcHint)); // 验证修剪后的Calc节点仍然包含hint
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4783">[CALCITE-4783] // 引用JIRA问题链接
   * RelFieldTrimmer incorrectly drops filter condition</a>. */ // 问题：RelFieldTrimmer错误地丢弃了过滤条件
  @Test void testCalcFieldTrimmer3() { // 测试方法：测试Calc节点在字段修剪时正确保留过滤条件
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP") // 扫描EMP表
            .project( // 投影操作
                builder.field("ENAME"), // 投影ENAME字段
                builder.field("DEPTNO")) // 投影DEPTNO字段
            .exchange(RelDistributions.SINGLETON) // 添加Exchange节点，使用单例分布
            .filter(builder.equals(builder.field("ENAME"), builder.literal("bob"))) // 过滤条件：ENAME等于"bob"
            .aggregate(builder.groupKey(), builder.countStar(null)) // 聚合操作：计数所有行
            .build(); // 构建关系树

    final HepProgram hepProgram = new HepProgramBuilder() // 创建HepProgramBuilder
        .addRuleInstance(CoreRules.FILTER_TO_CALC).build(); // 添加FILTER_TO_CALC规则

    final HepPlanner hepPlanner = new HepPlanner(hepProgram); // 创建HepPlanner实例
    hepPlanner.setRoot(root); // 设置根节点
    final RelNode relNode = hepPlanner.findBestExp(); // 执行优化，找到最佳表达式
    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(relNode); // 对优化后的节点进行字段修剪

    final String expected = "" // 定义期望的关系树字符串
        + "LogicalAggregate(group=[{}], agg#0=[COUNT()])\n" // Aggregate节点，无分组，计数
        + "  LogicalCalc(expr#0=[{inputs}], expr#1=['bob'], expr#2=[=($t0, $t1)], $condition=[$t2])\n" // Calc节点，包含过滤条件
        + "    LogicalExchange(distribution=[single])\n" // Exchange节点，单例分布
        + "      LogicalProject(ENAME=[$1])\n" // Project节点，只保留ENAME
        + "        LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4995">[CALCITE-4995] // 引用JIRA问题链接
   * AssertionError caused by RelFieldTrimmer on SEMI/ANTI join</a>. */ // 问题：RelFieldTrimmer在SEMI/ANTI连接上导致断言错误
  @Test void testSemiJoinAntiJoinFieldTrimmer() { // 测试方法：测试半连接和反连接的字段修剪
    for (final JoinRelType joinType : new JoinRelType[]{JoinRelType.ANTI, JoinRelType.SEMI}) { // 遍历ANTI和SEMI两种连接类型
      final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
      final RelNode root = builder // 定义根关系节点
          .values(new String[]{"id"}, 1, 2).as("a") // 创建Values节点a，包含id字段和值1、2
          .values(new String[]{"id"}, 2, 3).as("b") // 创建Values节点b，包含id字段和值2、3
          .join(joinType, // 连接a和b，使用当前循环的连接类型
              builder.equals( // 连接条件：id相等
                  builder.field(2, "a", "id"), // a表的id字段
                  builder.field(2, "b", "id"))) // b表的id字段
          .values(new String[]{"id"}, 0, 2).as("c") // 创建Values节点c，包含id字段和值0、2
          .join(joinType, // 连接a和c，使用当前循环的连接类型
              builder.equals( // 连接条件：id相等
                  builder.field(2, "a", "id"), // a表的id字段
                  builder.field(2, "c", "id"))) // c表的id字段
          .build(); // 构建关系树

      final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
      final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪
      final String expected = "" // 定义期望的关系树字符串
          + "LogicalJoin(condition=[=($0, $1)], joinType=[" + joinType.lowerName + "])\n" // 顶层的Join节点
          + "  LogicalJoin(condition=[=($0, $1)], joinType=[" + joinType.lowerName + "])\n" // 下层的Join节点
          + "    LogicalValues(tuples=[[{ 1 }, { 2 }]])\n" // Values节点a
          + "    LogicalValues(tuples=[[{ 2 }, { 3 }]])\n" // Values节点b
          + "  LogicalValues(tuples=[[{ 0 }, { 2 }]])\n"; // Values节点c
      assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
    }
  }

  @Test void testUnionFieldTrimmer() { // 测试方法：测试Union节点的字段修剪功能
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final RelNode root = // 定义根关系节点
        builder.scan("EMP").as("t1") // 扫描EMP表，别名为t1
            .project(builder.field("EMPNO")) // 投影EMPNO字段
            .scan("EMP").as("t2") // 扫描EMP表，别名为t2
            .scan("EMP").as("t3") // 扫描EMP表，别名为t3
            .join(JoinRelType.INNER, // 连接t2和t3，使用内连接
                builder.equals( // 连接条件：EMPNO相等
                    builder.field(2, "t2", "EMPNO"), // t2表的EMPNO字段
                    builder.field(2, "t3", "EMPNO"))) // t3表的EMPNO字段
            .project(builder.field("t2", "EMPNO")) // 投影t2表的EMPNO字段
            .union(false) // 创建Union节点，all=false表示去重
            .build(); // 构建关系树
    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪
    final String expected = "" // 定义期望的关系树字符串
        + "LogicalUnion(all=[false])\n" // Union节点，去重
        + "  LogicalProject(EMPNO=[$0])\n" // 第一个输入的Project节点
        + "    LogicalTableScan(table=[[scott, EMP]])\n" // 第一个输入的TableScan节点
        + "  LogicalProject(EMPNO=[$0])\n" // 第二个输入的Project节点
        + "    LogicalJoin(condition=[=($0, $1)], joinType=[inner])\n" // Join节点
        + "      LogicalProject(EMPNO=[$0])\n" // Join左侧的Project节点
        + "        LogicalTableScan(table=[[scott, EMP]])\n" // Join左侧的TableScan节点
        + "      LogicalProject(EMPNO=[$0])\n" // Join右侧的Project节点
        + "        LogicalTableScan(table=[[scott, EMP]])\n"; // Join右侧的TableScan节点
    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  /**
   * Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6715">[CALCITE-6715] // 引用JIRA问题链接
   * Enhance RelFieldTrimmer to trim LogicalCorrelate nodes</a>. // 增强：RelFieldTrimmer修剪LogicalCorrelate节点
   */
  @Test void testLogicalCorrelateFieldTrimmer() { // 测试方法：测试LogicalCorrelate节点的字段修剪
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty(); // 创建一个Holder来持有相关变量
    RelNode root = builder.scan("EMP") // 扫描EMP表
        .projectPlus(builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(0))) // 添加一个计算字段：EMPNO+EMPNO
        .variable(v::set) // 设置相关变量
        .values(new String[] {"dummy"}, true) // 创建Values节点，包含一个dummy字段和值true
        .project( // 投影操作
            builder.call(SqlStdOperatorTable.ARRAY_VALUE_CONSTRUCTOR, // 创建数组构造器
            builder.field(v.get(), "DEPTNO"), builder.field(v.get(), "DEPTNO"))) // 构造包含两个DEPTNO的数组
        .uncollect(Collections.emptyList(), false) // 解构数组
        .correlate(JoinRelType.LEFT, v.get().id, builder.field(2, 0, "DEPTNO")) // 创建相关连接，左连接，基于DEPTNO
        .aggregate(builder.groupKey("ENAME"), builder.max(builder.field("EMPNO"))) // 聚合操作：按ENAME分组，计算EMPNO的最大值
        .build(); // 构建关系树

    String origTree = "" // 定义原始关系树字符串
        + "LogicalAggregate(group=[{1}], agg#0=[MAX($0)])\n" // Aggregate节点
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n" // Correlate节点，需要第7个字段（DEPTNO）
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+($0, $0)])\n" // Project节点
        + "      LogicalTableScan(table=[[scott, EMP]])\n" // TableScan节点
        + "    Uncollect\n" // Uncollect节点
        + "      LogicalProject($f0=[ARRAY($cor0.DEPTNO, $cor0.DEPTNO)])\n" // Project节点，构造数组
        + "        LogicalValues(tuples=[[{ true }]])\n"; // Values节点
    assertThat(root, hasTree(origTree)); // 断言原始关系树与期望匹配

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪
    final String expected = "" // 定义期望的关系树字符串
        + "LogicalAggregate(group=[{1}], agg#0=[MAX($0)])\n" // Aggregate节点
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{2}])\n" // Correlate节点，需要第2个字段（修剪后的DEPTNO）
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // Project节点，只保留EMPNO、ENAME、DEPTNO
        + "      LogicalTableScan(table=[[scott, EMP]])\n" // TableScan节点
        + "    Uncollect\n" // Uncollect节点
        + "      LogicalProject($f0=[ARRAY($cor0.DEPTNO, $cor0.DEPTNO)])\n" // Project节点，构造数组
        + "        LogicalValues(tuples=[[{ true }]])\n"; // Values节点

    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  /**
   * Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6715">[CALCITE-6715] // 引用JIRA问题链接
   * Enhance RelFieldTrimmer to trim LogicalCorrelate nodes</a>. // 增强：RelFieldTrimmer修剪LogicalCorrelate节点
   */
  @Test void testLogicalCorrelateFieldTrimmer2() { // 测试方法：测试LogicalCorrelate节点的字段修剪（复杂场景）
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty(); // 创建一个Holder来持有相关变量
    RelNode root = builder.scan("EMP") // 扫描EMP表
        .projectPlus(builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(0))) // 添加一个计算字段：EMPNO+EMPNO
        .variable(v::set) // 设置相关变量
        .scan("DEPT") // 扫描DEPT表
        .projectPlus( // 添加计算字段
            builder.call(SqlStdOperatorTable.PLUS, // 加法运算
            builder.field(v.get(), "DEPTNO"), builder.field(v.get(), "DEPTNO"))) // DEPTNO+DEPTNO（使用相关变量）
        .filter( // 过滤操作
            builder.equals(builder.field(0), // 过滤条件：DEPTNO等于10+相关DEPTNO
                builder.call( // 调用函数
                    SqlStdOperatorTable.PLUS, // 加法运算
                    builder.literal(10), // 字面量10
                    builder.field(v.get(), "DEPTNO")))) // 相关DEPTNO
        .correlate(JoinRelType.LEFT, v.get().id, builder.field(2, 0, "DEPTNO")) // 创建相关连接，左连接，基于DEPTNO
        .aggregate(builder.groupKey("ENAME"), builder.max(builder.field("EMPNO"))) // 聚合操作：按ENAME分组，计算EMPNO的最大值
        .build(); // 构建关系树

    String origTree = "" // 定义原始关系树字符串
        + "LogicalAggregate(group=[{1}], agg#0=[MAX($0)])\n" // Aggregate节点
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n" // Correlate节点，需要第7个字段（DEPTNO）
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+($0, $0)])\n" // Project节点
        + "      LogicalTableScan(table=[[scott, EMP]])\n" // TableScan节点
        + "    LogicalFilter(condition=[=($0, +(10, $cor0.DEPTNO))])\n" // Filter节点，过滤条件
        + "      LogicalProject(DEPTNO=[$0], DNAME=[$1], LOC=[$2], $f3=[+($cor0.DEPTNO, $cor0.DEPTNO)])\n" // Project节点
        + "        LogicalTableScan(table=[[scott, DEPT]])\n"; // TableScan节点
    assertThat(root, hasTree(origTree)); // 断言原始关系树与期望匹配

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪
    final String expected = "" // 定义期望的关系树字符串
        + "LogicalAggregate(group=[{1}], agg#0=[MAX($0)])\n" // Aggregate节点
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{2}])\n" // Correlate节点，需要第2个字段（修剪后的DEPTNO）
        + "    LogicalProject(EMPNO=[$0], ENAME=[$1], DEPTNO=[$7])\n" // Project节点，只保留EMPNO、ENAME、DEPTNO
        + "      LogicalTableScan(table=[[scott, EMP]])\n" // TableScan节点
        + "    LogicalFilter(condition=[=($0, +(10, $cor0.DEPTNO))])\n" // Filter节点，过滤条件保留
        + "      LogicalProject(DEPTNO=[$0])\n" // Project节点，只保留DEPTNO
        + "        LogicalTableScan(table=[[scott, DEPT]])\n"; // TableScan节点

    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

  /**
   * Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3772">[CALCITE-3772] // 引用JIRA问题链接
   * RelFieldTrimmer incorrectly trims fields when the query includes correlated-subquery</a>. */ // 问题：RelFieldTrimmer在查询包含相关子查询时错误地修剪字段
  @Test void testTrimCorrelatedSubquery() { // 测试方法：测试相关子查询的字段修剪
    final RelBuilder builder = RelBuilder.create(config().build()); // 创建RelBuilder实例
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty(); // 创建一个Holder来持有相关变量
    RelNode root = builder.scan("EMP") // 扫描EMP表
        .variable(v::set) // 设置相关变量
        .filter( // 过滤操作
            builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(5), // 过滤条件：SAL（第5个字段）大于10
            builder.literal(10)))
        .project( // 投影操作
            builder.field(0), // 投影EMPNO
            builder.scalarQuery( // 创建标量子查询
                b2 -> builder.scan("EMP").filter( // 子查询：扫描EMP表并过滤
                    builder.call(SqlStdOperatorTable.LESS_THAN, // 过滤条件：MGR小于外层的MGR
                        builder.field(3), builder.field(v.get(), "MGR")))
                    .project(builder.field(0)) // 投影EMPNO
                    .aggregate(builder.groupKey(), builder.countStar("c")) // 聚合：计数
                    .build())) // 构建子查询
        .build(); // 构建关系树

    String origTree = "" // 定义原始关系树字符串
        + "LogicalProject(EMPNO=[$0], $f1=[$SCALAR_QUERY({\n" // Project节点，包含标量子查询
        + "LogicalAggregate(group=[{}], c=[COUNT()])\n" // 子查询中的Aggregate节点
        + "  LogicalFilter(condition=[<($3, $cor0.MGR)])\n" // 子查询中的Filter节点，使用相关变量
        + "    LogicalTableScan(table=[[scott, EMP]])\n" // 子查询中的TableScan节点
        + "})])\n"
        + "  LogicalFilter(condition=[>($5, 10)])\n" // Filter节点，过滤SAL大于10
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点
    assertThat(root, hasTree(origTree)); // 断言原始关系树与期望匹配

    final RelFieldTrimmer fieldTrimmer = new RelFieldTrimmer(null, builder); // 创建RelFieldTrimmer实例
    final RelNode trimmed = fieldTrimmer.trim(root); // 对根节点进行字段修剪
    final String expected = "" // 定义期望的关系树字符串
        + "LogicalProject(EMPNO=[$0], $f1=[$SCALAR_QUERY({\n" // Project节点，包含标量子查询
        + "LogicalAggregate(group=[{}], c=[COUNT()])\n" // 子查询中的Aggregate节点
        + "  LogicalFilter(condition=[<($3, $cor0.MGR)])\n" // 子查询中的Filter节点，使用相关变量
        + "    LogicalTableScan(table=[[scott, EMP]])\n" // 子查询中的TableScan节点
        + "})])\n"
        + "  LogicalFilter(condition=[>($2, 10)])\n" // Filter节点，过滤条件更新为第2个字段
        + "    LogicalProject(EMPNO=[$0], MGR=[$3], SAL=[$5])\n" // Project节点，只保留EMPNO、MGR、SAL
        + "      LogicalTableScan(table=[[scott, EMP]])\n"; // TableScan节点

    assertThat(trimmed, hasTree(expected)); // 断言修剪后的关系树与期望匹配
  }

}
