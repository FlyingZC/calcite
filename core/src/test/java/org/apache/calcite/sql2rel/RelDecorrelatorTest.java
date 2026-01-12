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
package org.apache.calcite.sql2rel;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitDef;
import org.apache.calcite.plan.hep.HepProgram;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.rules.CoreRules;
import org.apache.calcite.rex.RexCorrelVariable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.test.CalciteAssert;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.calcite.tools.Program;
import org.apache.calcite.tools.Programs;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.tools.RuleSet;
import org.apache.calcite.tools.RuleSets;
import org.apache.calcite.util.Holder;
import org.apache.calcite.util.TestUtil;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.apache.calcite.test.Matchers.hasTree;

import static org.hamcrest.MatcherAssert.assertThat;

import static java.util.Objects.requireNonNull;

/**
 * Tests for {@link RelDecorrelator}.
 * RelDecorrelatorTest 类用于测试 RelDecorrelator（关系表达式去相关器）的功能
 * RelDecorrelator 是 Calcite 框架中用于消除相关子查询（correlated subquery）的核心组件
 * 它将包含相关变量的查询转换为等价的不相关查询，从而提高查询性能
 * 本测试类包含以下主要测试场景：
 * 1. testGroupKeyNotInFrontWhenDecorrelate: 测试去相关时分组键不在前面位置的情况
 * 2. testCorrVarOnAggregateKey: 测试相关变量用作聚合分组键的场景（修复 CALCITE-6468）
 * 3. testDecorrelatorCustomizeRules: 测试自定义去相关规则的功能（修复 CALCITE-6674）
 */
public class RelDecorrelatorTest {
  // 创建并返回一个 Frameworks.ConfigBuilder 对象，用于构建 Calcite 框架配置
  // 该配置用于测试环境的初始化，包括解析器配置、默认 schema 和 trait 定义
  public static Frameworks.ConfigBuilder config() {
    // 创建根 schema，参数 true 表示启用类型系统
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    // 构建配置对象
    return Frameworks.newConfigBuilder()
        // 设置 SQL 解析器配置为默认配置
        .parserConfig(SqlParser.Config.DEFAULT)
        // 设置默认 schema 为 SCOTT_WITH_TEMPORAL（包含 Scott 测试数据集和临时表支持）
        .defaultSchema(
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL))
        // 设置 trait 定义为 null，表示使用默认的 trait 定义列表
        .traitDefs((List<RelTraitDef>) null);
  }

  // 测试方法：验证去相关时分组键不在前面位置的场景
  // 该测试构建了一个包含相关子查询的查询计划，然后验证去相关后的结果是否正确
  // 主要验证点：去相关后聚合的分组键位置变化是否正确
  @Test void testGroupKeyNotInFrontWhenDecorrelate() {
    // 创建 RelBuilder 对象，用于构建关系表达式树
    final RelBuilder builder = RelBuilder.create(config().build());
    // 创建一个 Holder 对象，用于保存相关变量（RexCorrelVariable）
    // Holder 是一个可变容器，允许在 lambda 表达式中修改其值
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();
    // 构建去相关前的原始关系表达式树
    RelNode before = builder.scan("EMP") // 扫描 EMP 表，作为外层查询
        .variable(v::set) // 创建相关变量并保存到 Holder v 中
        .scan("DEPT") // 扫描 DEPT 表，作为内层查询
        .filter( // 添加过滤条件
            builder.equals(builder.field(0), // 比较 DEPT 表的第一个字段
                builder.call( // 调用加法运算
                    SqlStdOperatorTable.PLUS, // 加法操作符
                    builder.literal(10), // 常量 10
                    builder.field(v.get(), "DEPTNO")))) // 引用相关变量的 DEPTNO 字段
        .correlate(JoinRelType.LEFT, v.get().id, builder.field(2, 0, "DEPTNO")) // 创建相关连接，类型为左连接
        .aggregate(builder.groupKey("ENAME"), builder.max(builder.field("EMPNO"))) // 按 ENAME 分组，计算 EMPNO 的最大值
        .build(); // 构建关系表达式树

    // 定义去相关前的预期计划字符串（用于验证）
    final String planBefore = ""
        + "LogicalAggregate(group=[{1}], agg#0=[MAX($0)])\n" // 逻辑聚合：按第1列分组，第0列取最大值
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n" // 逻辑相关连接：左连接，需要第7列
        + "    LogicalTableScan(table=[[scott, EMP]])\n" // 扫描 EMP 表
        + "    LogicalFilter(condition=[=($0, +(10, $cor0.DEPTNO))])\n" // 过滤条件：第0列等于10加相关变量的DEPTNO
        + "      LogicalTableScan(table=[[scott, DEPT]])\n"; // 扫描 DEPT 表
    // 断言：验证 before 关系表达式的计划树是否符合预期
    assertThat(before, hasTree(planBefore));

    // 调用 RelDecorrelator.decorrelateQuery 方法对查询进行去相关处理
    // 该方法会消除相关变量，将相关子查询转换为等价的连接操作
    RelNode after = RelDecorrelator.decorrelateQuery(before, builder);

    // 定义去相关后的预期计划字符串（用于验证）
    final String planAfter = ""
        + "LogicalAggregate(group=[{0}], agg#0=[MAX($1)])\n" // 逻辑聚合：按第0列分组，第1列取最大值（注意分组键位置变化）
        + "  LogicalProject(ENAME=[$1], EMPNO=[$0])\n" // 逻辑投影：输出 ENAME 和 EMPNO 字段
        + "    LogicalJoin(condition=[=($8, $9)], joinType=[left])\n" // 逻辑连接：左连接，条件是第8列等于第9列
        + "      LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], " // 逻辑投影：输出 EMP 表的所有字段
        + "SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[+(10, $7)])\n" // 以及计算字段 $f8（DEPTNO + 10）
        + "        LogicalTableScan(table=[[scott, EMP]])\n" // 扫描 EMP 表
        + "      LogicalTableScan(table=[[scott, DEPT]])\n"; // 扫描 DEPT 表
    // 断言：验证去相关后的关系表达式计划树是否符合预期
    assertThat(after, hasTree(planAfter));
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6468">[CALCITE-6468] RelDecorrelator
   * throws AssertionError if correlated variable is used as Aggregate group key</a>.
   * 测试用例：验证当相关变量用作聚合分组键时，RelDecorrelator 不会抛出 AssertionError
   * 该测试修复了 CALCITE-6468 问题，确保去相关器能正确处理相关变量作为聚合分组键的场景
   */
  @Test void testCorrVarOnAggregateKey() {
    // 构建框架配置对象
    final FrameworkConfig frameworkConfig = config().build();
    // 创建 RelBuilder 对象，用于构建关系表达式
    final RelBuilder builder = RelBuilder.create(frameworkConfig);
    // 获取 RelOptCluster 对象，包含元数据提供者和类型系统
    final RelOptCluster cluster = builder.getCluster();
    // 获取 Planner 对象，用于 SQL 解析、验证和转换
    final Planner planner = Frameworks.getPlanner(frameworkConfig);
    // 定义测试 SQL 语句
    // 该 SQL 包含一个 CTE（公共表表达式）agg_sal 和一个相关子查询
    // 子查询中 s1.deptno = s2.deptno 是相关条件
    final String sql = "WITH agg_sal AS"
        + " (SELECT deptno, sum(sal) AS total FROM emp GROUP BY deptno)\n" // CTE：按部门分组计算薪资总和
        + " SELECT 1 FROM agg_sal s1" // 查询 agg_sal 表别名为 s1
        + " WHERE s1.total > (SELECT avg(total) FROM agg_sal s2 WHERE s1.deptno = s2.deptno)"; // 相关子查询：筛选薪资总和大于同部门平均值的记录
    // 声明原始关系表达式变量
    final RelNode originalRel;
    try {
      // 解析 SQL 语句为 SqlNode（抽象语法树）
      final SqlNode parse = planner.parse(sql);
      // 验证 SqlNode，进行语义检查（如表名、字段名、类型等）
      final SqlNode validate = planner.validate(parse);
      // 将验证后的 SqlNode 转换为关系表达式（RelNode）
      originalRel = planner.rel(validate).rel;
    } catch (Exception e) {
      // 如果发生异常，重新抛出（包装为运行时异常）
      throw TestUtil.rethrow(e);
    }

    // 构建 HepProgram（启发式优化程序）
    // HepProgram 是一种基于规则的优化器，按顺序应用规则
    final HepProgram hepProgram = HepProgram.builder()
        .addRuleCollection( // 添加规则集合
            ImmutableList.of(
                // 子查询转换规则：将 Filter 中的子查询转换为相关连接
                CoreRules.FILTER_SUB_QUERY_TO_CORRELATE,
                // 子查询转换规则：将 Project 中的子查询转换为相关连接
                CoreRules.PROJECT_SUB_QUERY_TO_CORRELATE,
                // 子查询转换规则：将 Join 中的子查询转换为相关连接
                CoreRules.JOIN_SUB_QUERY_TO_CORRELATE,
                // 过滤聚合下推规则：将 Filter 下推到 Aggregate 下方
                CoreRules.FILTER_AGGREGATE_TRANSPOSE))
        .build(); // 构建 HepProgram
    // 创建 Program 对象，包装 HepProgram
    // 参数：hepProgram（优化程序）、true（启用元数据）、metadataProvider（元数据提供者）
    final Program program =
        Programs.of(hepProgram, true,
            requireNonNull(cluster.getMetadataProvider()));
    // 运行优化程序，将原始关系表达式转换为包含相关连接的形式
    // 参数：planner（规划器）、originalRel（原始关系表达式）、traitSet（特征集合）、空列表（物质化视图）、空列表（统计信息）
    final RelNode before =
        program.run(cluster.getPlanner(), originalRel, cluster.traitSet(),
            Collections.emptyList(), Collections.emptyList());
    // 定义去相关前的预期计划字符串（用于验证）
    final String planBefore = ""
        + "LogicalProject(EXPR$0=[1])\n" // 逻辑投影：输出常量 1
        + "  LogicalProject(DEPTNO=[$0], TOTAL=[$1])\n" // 逻辑投影：输出 DEPTNO 和 TOTAL 字段
        + "    LogicalFilter(condition=[>($1, $2)])\n" // 逻辑过滤：条件是 TOTAL 大于子查询结果
        + "      LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{0}])\n" // 逻辑相关连接：左连接，需要第0列（DEPTNO）
        + "        LogicalAggregate(group=[{0}], TOTAL=[SUM($1)])\n" // 逻辑聚合：按 DEPTNO 分组，计算 SAL 的总和
        + "          LogicalProject(DEPTNO=[$7], SAL=[$5])\n" // 逻辑投影：提取 DEPTNO（第7列）和 SAL（第5列）
        + "            LogicalTableScan(table=[[scott, EMP]])\n" // 扫描 EMP 表
        + "        LogicalAggregate(group=[{}], EXPR$0=[AVG($0)])\n" // 逻辑聚合：无分组，计算平均值
        + "          LogicalProject(TOTAL=[$1])\n" // 逻辑投影：提取 TOTAL 字段
        + "            LogicalAggregate(group=[{0}], TOTAL=[SUM($1)])\n" // 逻辑聚合：按 DEPTNO 分组，计算 SAL 的总和
        + "              LogicalFilter(condition=[=($cor0.DEPTNO, $0)])\n" // 逻辑过滤：使用相关变量 $cor0.DEPTNO 进行过滤
        + "                LogicalProject(DEPTNO=[$7], SAL=[$5])\n" // 逻辑投影：提取 DEPTNO 和 SAL
        + "                  LogicalTableScan(table=[[scott, EMP]])\n"; // 扫描 EMP 表
    // 断言：验证 before 关系表达式的计划树是否符合预期
    assertThat(before, hasTree(planBefore));

    // 检查去相关操作不会失败（这是 CALCITE-6468 问题的核心）
    // 调用 RelDecorrelator.decorrelateQuery 方法对查询进行去相关处理
    final RelNode after = RelDecorrelator.decorrelateQuery(before, builder);

    // 验证去相关后的计划
    final String planAfter = ""
        + "LogicalProject(EXPR$0=[1])\n" // 逻辑投影：输出常量 1
        + "  LogicalJoin(condition=[AND(=($0, $2), >($1, $3))], joinType=[inner])\n" // 逻辑连接：内连接，条件是 DEPTNO 相等且 TOTAL 大于平均值
        + "    LogicalAggregate(group=[{0}], TOTAL=[SUM($1)])\n" // 逻辑聚合：按 DEPTNO 分组，计算 SAL 的总和
        + "      LogicalProject(DEPTNO=[$7], SAL=[$5])\n" // 逻辑投影：提取 DEPTNO 和 SAL
        + "        LogicalTableScan(table=[[scott, EMP]])\n" // 扫描 EMP 表
        + "    LogicalAggregate(group=[{0}], EXPR$0=[AVG($1)])\n" // 逻辑聚合：按 DEPTNO 分组，计算 TOTAL 的平均值
        + "      LogicalProject(DEPTNO=[$0], TOTAL=[$1])\n" // 逻辑投影：输出 DEPTNO 和 TOTAL
        + "        LogicalAggregate(group=[{0}], TOTAL=[SUM($1)])\n" // 逻辑聚合：按 DEPTNO 分组，计算 SAL 的总和
        + "          LogicalProject(DEPTNO=[$0], SAL=[$1])\n" // 逻辑投影：输出 DEPTNO 和 SAL
        + "            LogicalFilter(condition=[IS NOT NULL($0)])\n" // 逻辑过滤：确保 DEPTNO 不为空
        + "              LogicalProject(DEPTNO=[$7], SAL=[$5])\n" // 逻辑投影：提取 DEPTNO 和 SAL
        + "                LogicalTableScan(table=[[scott, EMP]])\n"; // 扫描 EMP 表
    // 断言：验证去相关后的关系表达式计划树是否符合预期
    assertThat(after, hasTree(planAfter));
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6674">[CALCITE-6674] Make
   * RelDecorrelator rules configurable</a>.
   * 测试用例：验证 RelDecorrelator 的规则可配置性
   * 该测试修复了 CALCITE-6674 问题，允许用户自定义去相关时应用的规则集合
   * 测试场景包括：使用默认规则、使用显式指定的默认规则、使用仅相关规则、不使用任何预规则
   */
  @Test void testDecorrelatorCustomizeRules() {
    // 构建框架配置对象
    final FrameworkConfig frameworkConfig = config().build();
    // 创建 RelBuilder 对象，用于构建关系表达式
    final RelBuilder builder = RelBuilder.create(frameworkConfig);
    // 获取 RelOptCluster 对象，包含元数据提供者和类型系统
    final RelOptCluster cluster = builder.getCluster();
    // 获取 Planner 对象，用于 SQL 解析、验证和转换
    final Planner planner = Frameworks.getPlanner(frameworkConfig);
    // 定义测试 SQL 语句
    // 该 SQL 使用 ROW 构造函数，其中包含一个标量子查询
    // 子查询根据 emp.deptno 从 dept 表中查询对应的 deptno
    final String sql = "select ROW("
        + "(select deptno\n"
        + "from dept\n"
        + "where dept.deptno = emp.deptno), emp.ename)\n" // 相关子查询：根据 emp.deptno 查询 dept.deptno
        + "from emp"; // 从 emp 表查询
    // 声明解析后的关系表达式变量
    final RelNode parsedRel;
    try {
      // 解析 SQL 语句为 SqlNode（抽象语法树）
      final SqlNode parse = planner.parse(sql);
      // 验证 SqlNode，进行语义检查
      final SqlNode validate = planner.validate(parse);
      // 将验证后的 SqlNode 转换为关系表达式（RelNode）
      parsedRel = planner.rel(validate).rel;
    } catch (Exception e) {
      // 如果发生异常，重新抛出
      throw TestUtil.rethrow(e);
    }

    // 将子查询转换为相关连接（Correlate）
    final HepProgram hepProgram = HepProgram.builder()
        .addRuleCollection(ImmutableList.of(CoreRules.PROJECT_SUB_QUERY_TO_CORRELATE)) // 添加 Project 子查询到相关连接的转换规则
        .build(); // 构建 HepProgram
    // 创建 Program 对象，包装 HepProgram
    final Program program =
        Programs.of(hepProgram, true,
            requireNonNull(cluster.getMetadataProvider()));
    // 运行优化程序，将解析后的关系表达式转换为包含相关连接的形式
    final RelNode original =
        program.run(cluster.getPlanner(), parsedRel, cluster.traitSet(),
            Collections.emptyList(), Collections.emptyList());
    // 定义原始计划的预期字符串（用于验证）
    final String planOriginal = ""
        + "LogicalProject(EXPR$0=[ROW($8, $1)])\n" // 逻辑投影：输出 ROW 构造的结果
        + "  LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{7}])\n" // 逻辑相关连接：左连接，需要第7列（DEPTNO）
        + "    LogicalTableScan(table=[[scott, EMP]])\n" // 扫描 EMP 表
        + "    LogicalAggregate(group=[{}], agg#0=[SINGLE_VALUE($0)])\n" // 逻辑聚合：无分组，取单一值
        + "      LogicalProject(DEPTNO=[$0])\n" // 逻辑投影：提取 DEPTNO 字段
        + "        LogicalFilter(condition=[=($0, $cor0.DEPTNO)])\n" // 逻辑过滤：使用相关变量进行过滤
        + "          LogicalTableScan(table=[[scott, DEPT]])\n"; // 扫描 DEPT 表
    // 断言：验证 original 关系表达式的计划树是否符合预期
    assertThat(original, hasTree(planOriginal));

    // 使用默认规则进行去相关
    final RelNode decorrelatedDefault = RelDecorrelator.decorrelateQuery(original, builder);
    // 定义默认去相关后的预期计划字符串（用于验证）
    final String planDecorrelatedDefault = ""
        + "LogicalProject(EXPR$0=[ROW($8, $1)])\n" // 逻辑投影：输出 ROW 构造的结果
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], DEPTNO8=[$8])\n" // 逻辑投影：输出所有字段
        + "    LogicalJoin(condition=[=($8, $7)], joinType=[left])\n" // 逻辑连接：左连接，条件是 DEPTNO 相等
        + "      LogicalTableScan(table=[[scott, EMP]])\n" // 扫描 EMP 表
        + "      LogicalTableScan(table=[[scott, DEPT]])\n"; // 扫描 DEPT 表
    // 断言：验证默认去相关后的计划树是否符合预期
    assertThat(decorrelatedDefault, hasTree(planDecorrelatedDefault));

    // 使用显式指定的默认规则进行去相关：结果应该相同
    // 创建规则集合，包含三个默认的去相关规则
    final RuleSet defaultRules =
        RuleSets.ofList(RelDecorrelator.RemoveSingleAggregateRule.DEFAULT.toRule(), // 移除单一聚合规则
            RelDecorrelator.RemoveCorrelationForScalarProjectRule.DEFAULT.toRule(), // 移除标量投影中的相关规则
            RelDecorrelator.RemoveCorrelationForScalarAggregateRule.DEFAULT.toRule()); // 移除标量聚合中的相关规则
    // 使用显式指定的规则进行去相关
    final RelNode decorrelatedDefault2 =
        RelDecorrelator.decorrelateQuery(original, builder, defaultRules);
    // 断言：验证使用显式规则去相关后的计划树与默认规则的结果相同
    assertThat(decorrelatedDefault2, hasTree(planDecorrelatedDefault));

    // 仅使用与当前查询相关的规则进行去相关：结果应该相同
    // 创建规则集合，仅包含移除标量投影中相关的规则
    final RuleSet relevantRule =
        RuleSets.ofList(RelDecorrelator.RemoveCorrelationForScalarProjectRule.DEFAULT.toRule());
    // 使用相关规则进行去相关
    final RelNode decorrelatedRelevantRule =
        RelDecorrelator.decorrelateQuery(original, builder, relevantRule);
    // 断言：验证使用相关规则去相关后的计划树与默认规则的结果相同
    assertThat(decorrelatedRelevantRule, hasTree(planDecorrelatedDefault));

    // 不使用任何预规则（仅使用"主"去相关程序）：去相关但保留聚合
    // 创建空规则集合，表示不应用任何预规则
    final RuleSet noRules = RuleSets.ofList(Collections.emptyList());
    // 使用空规则集合进行去相关
    final RelNode decorrelatedNoRules =
        RelDecorrelator.decorrelateQuery(original, builder, noRules);
    // 定义不使用预规则去相关后的预期计划字符串（用于验证）
    final String planDecorrelatedNoRules = ""
        + "LogicalProject(EXPR$0=[ROW($9, $1)])\n" // 逻辑投影：输出 ROW 构造的结果
        + "  LogicalJoin(condition=[=($7, $8)], joinType=[left])\n" // 逻辑连接：左连接，条件是 DEPTNO 相等
        + "    LogicalTableScan(table=[[scott, EMP]])\n" // 扫描 EMP 表
        + "    LogicalAggregate(group=[{0}], agg#0=[SINGLE_VALUE($1)])\n" // 逻辑聚合：按 DEPTNO 分组，取单一值（注意：聚合被保留）
        + "      LogicalProject(DEPTNO1=[$0], DEPTNO=[$0])\n" // 逻辑投影：输出 DEPTNO1 和 DEPTNO（相同值）
        + "        LogicalTableScan(table=[[scott, DEPT]])\n"; // 扫描 DEPT 表
    // 断言：验证不使用预规则去相关后的计划树是否符合预期
    assertThat(decorrelatedNoRules, hasTree(planDecorrelatedNoRules));
  }
}
