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
package org.apache.calcite.test; // 包声明，属于 Apache Calcite 测试包

import org.apache.calcite.plan.RelOptListener; // 导入关系表达式优化器监听器接口
import org.apache.calcite.plan.RelOptMaterialization; // 导入物化视图相关类
import org.apache.calcite.plan.hep.HepMatchOrder; // 导入 HepPlanner 匹配顺序枚举
import org.apache.calcite.plan.hep.HepPlanner; // 导入 HepPlanner（启发式规划器）类
import org.apache.calcite.plan.hep.HepProgram; // 导入 HepPlanner 程序类
import org.apache.calcite.plan.hep.HepProgramBuilder; // 导入 HepPlanner 程序构建器
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口
import org.apache.calcite.rel.externalize.RelDotWriter; // 导入关系表达式 DOT 格式输出器
import org.apache.calcite.rel.logical.LogicalIntersect; // 导入逻辑交集节点
import org.apache.calcite.rel.logical.LogicalUnion; // 导入逻辑联合（UNION）节点
import org.apache.calcite.rel.logical.LogicalValues; // 导入逻辑 VALUES 节点
import org.apache.calcite.rel.rules.CoerceInputsRule; // 导入输入强制类型转换规则
import org.apache.calcite.rel.rules.CoreRules; // 导入 Calcite 核心规则集合
import org.apache.calcite.sql.SqlExplainLevel; // 导入 SQL 解释级别枚举
import org.apache.calcite.tools.RelBuilder; // 导入关系表达式构建器

import com.google.common.collect.ImmutableList; // 导入 Google Guava 不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解
import org.junit.jupiter.api.AfterAll; // 导入 JUnit 5 的 @AfterAll 注解
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 @Test 注解

import java.io.PrintWriter; // 导入打印写入器
import java.io.StringWriter; // 导入字符串写入器

import static org.apache.calcite.test.Matchers.isLinux; // 导入 Linux 环境匹配器

import static org.hamcrest.CoreMatchers.instanceOf; // 导入实例类型匹配器
import static org.hamcrest.CoreMatchers.is; // 导入相等匹配器
import static org.hamcrest.CoreMatchers.notNullValue; // 导入非空匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言工具
import static org.hamcrest.Matchers.empty; // 导入空集合匹配器
import static org.hamcrest.Matchers.hasSize; // 导入集合大小匹配器

import static java.util.Objects.requireNonNull; // 导入对象非空检查方法

/**
 * HepPlannerTest is a unit test for {@link HepPlanner}. See
 * {@link RelOptRulesTest} for an explanation of how to add tests; the tests in
 * this class are targeted at exercising the planner, and use specific rules for
 * convenience only, whereas the tests in that class are targeted at exercising
 * specific rules, and use the planner for convenience only. Hence the split.
 * 
 * HepPlannerTest 类是 HepPlanner（启发式规划器）的单元测试类。该测试类专门用于测试
 * HepPlanner 本身的各种功能和特性，测试中使用特定的规则只是为了方便测试规划器。
 * 相反，RelOptRulesTest 类是专门用于测试特定规则的，使用规划器只是为了方便测试规则本身。
 * 因此这两个类被分离开来，各自专注于不同的测试目标。
 * 
 * HepPlanner（启发式规划器）是一种基于启发式规则的关系表达式优化器，它使用一系列
 * 预定义的转换规则来重写和优化关系表达式树，与基于代价的优化器（如 VolcanoPlanner）
 * 不同，HepPlanner 不使用代价模型，而是通过反复应用规则直到达到固定点来生成优化后的计划。
 */
class HepPlannerTest { // HepPlannerTest 测试类定义
  //~ Static fields/initializers ---------------------------------------------
  // 静态字段和初始化块分隔符

  private static final String UNION_TREE = // 定义一个包含嵌套 UNION 的 SQL 查询树字符串常量
      "(select name from dept union select ename from emp)" // 第一个 UNION：从 dept 表选择 name 和 emp 表选择 ename
      + " union (select ename from bonus)"; // 第二个 UNION：将第一个结果与 bonus 表的 ename 进行 UNION

  private static final String COMPLEX_UNION_TREE = "select * from (\n" // 定义一个复杂的 UNION ALL 查询树字符串常量，包含多个 UNION ALL 分支
      + "  select ENAME, 50011895 as cat_id, '1' as cat_name, 1 as require_free_postage, 0 as require_15return, 0 as require_48hour,1 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50011895 union all\n" // 第1个分支：选择特定条件的员工记录，cat_id 为 50011895
      + "  select ENAME, 50013023 as cat_id, '2' as cat_name, 0 as require_free_postage, 0 as require_15return, 0 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50013023 union all\n" // 第2个分支：cat_id 为 50013023
      + "  select ENAME, 50013032 as cat_id, '3' as cat_name, 0 as require_free_postage, 0 as require_15return, 0 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50013032 union all\n" // 第3个分支：cat_id 为 50013032
      + "  select ENAME, 50013024 as cat_id, '4' as cat_name, 0 as require_free_postage, 0 as require_15return, 0 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50013024 union all\n" // 第4个分支：cat_id 为 50013024
      + "  select ENAME, 50004204 as cat_id, '5' as cat_name, 0 as require_free_postage, 0 as require_15return, 0 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50004204 union all\n" // 第5个分支：cat_id 为 50004204
      + "  select ENAME, 50013043 as cat_id, '6' as cat_name, 0 as require_free_postage, 0 as require_15return, 0 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50013043 union all\n" // 第6个分支：cat_id 为 50013043
      + "  select ENAME, 290903 as cat_id, '7' as cat_name, 1 as require_free_postage, 0 as require_15return, 0 as require_48hour,1 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 290903 union all\n" // 第7个分支：cat_id 为 290903
      + "  select ENAME, 50008261 as cat_id, '8' as cat_name, 1 as require_free_postage, 0 as require_15return, 0 as require_48hour,1 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50008261 union all\n" // 第8个分支：cat_id 为 50008261
      + "  select ENAME, 124478013 as cat_id, '9' as cat_name, 0 as require_free_postage, 0 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 124478013 union all\n" // 第9个分支：cat_id 为 124478013
      + "  select ENAME, 124472005 as cat_id, '10' as cat_name, 0 as require_free_postage, 0 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 124472005 union all\n" // 第10个分支：cat_id 为 124472005
      + "  select ENAME, 50013475 as cat_id, '11' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50013475 union all\n" // 第11个分支：cat_id 为 50013475
      + "  select ENAME, 50018263 as cat_id, '12' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50018263 union all\n" // 第12个分支：cat_id 为 50018263
      + "  select ENAME, 50013498 as cat_id, '13' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50013498 union all\n" // 第13个分支：cat_id 为 50013498
      + "  select ENAME, 350511 as cat_id, '14' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 350511 union all\n" // 第14个分支：cat_id 为 350511
      + "  select ENAME, 50019790 as cat_id, '15' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50019790 union all\n" // 第15个分支：cat_id 为 50019790
      + "  select ENAME, 50015382 as cat_id, '16' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50015382 union all\n" // 第16个分支：cat_id 为 50015382
      + "  select ENAME, 350503 as cat_id, '17' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 350503 union all\n" // 第17个分支：cat_id 为 350503
      + "  select ENAME, 350401 as cat_id, '18' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 350401 union all\n" // 第18个分支：cat_id 为 350401
      + "  select ENAME, 50015560 as cat_id, '19' as cat_name, 0 as require_free_postage, 0 as require_15return, 0 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50015560 union all\n" // 第19个分支：cat_id 为 50015560
      + "  select ENAME, 122658003 as cat_id, '20' as cat_name, 0 as require_free_postage, 1 as require_15return, 1 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 122658003 union all\n" // 第20个分支：cat_id 为 122658003
      + "  select ENAME, 50022371 as cat_id, '100' as cat_name, 0 as require_free_postage, 0 as require_15return, 0 as require_48hour,0 as require_insurance from emp where EMPNO = 20171216 and MGR = 0 and ENAME = 'Y' and SAL = 50022371\n" // 第21个分支：cat_id 为 50022371
      + ") a"; // 将整个 UNION ALL 查询别名为 'a'

  @Nullable // 使用可空注解，表示该字段可能为 null
  private static DiffRepository diffRepos = null; // 差异仓库静态字段，用于存储测试结果差异，初始为 null

  //~ Methods ----------------------------------------------------------------
  // 方法分隔符

  @AfterAll // 使用 JUnit 5 的 @AfterAll 注解，表示在所有测试方法执行后执行此方法
  public static void checkActualAndReferenceFiles() { // 检查实际输出文件和参考文件的方法
    requireNonNull(diffRepos, "diffRepos").checkActualAndReferenceFiles(); // 确保 diffRepos 不为 null，然后检查实际文件和参考文件是否一致
  }

  public RelOptFixture fixture() { // 创建并返回关系优化测试夹具的方法
    RelOptFixture fixture = RelOptFixture.DEFAULT // 获取默认的关系优化测试夹具
        .withDiffRepos(DiffRepository.lookup(HepPlannerTest.class)); // 为夹具设置差异仓库，通过查找 HepPlannerTest 类对应的差异仓库
    diffRepos = fixture.diffRepos(); // 将夹具的差异仓库赋值给静态字段 diffRepos
    return fixture; // 返回配置好的测试夹具
  }

  /** Sets the SQL statement for a test. */ // 设置测试用 SQL 语句的方法的文档注释
  public final RelOptFixture sql(String sql) { // 设置 SQL 语句并返回测试夹具的方法
    return fixture().sql(sql); // 调用 fixture() 方法获取测试夹具，然后设置 SQL 语句并返回
  }

  @Test void testRuleClass() { // 测试规则类的测试方法
    // Verify that an entire class of rules can be applied. // 验证整个规则类可以被应用

    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addRuleClass(CoerceInputsRule.class); // 将 CoerceInputsRule 类添加到程序中，表示该类的所有规则实例都可以被应用

    HepPlanner planner = // 创建 HepPlanner 启发式规划器实例
        new HepPlanner( // 使用构造函数创建 HepPlanner
            programBuilder.build()); // 传入构建好的 HepProgram

    planner.addRule( // 向规划器添加第一条规则
        CoerceInputsRule.Config.DEFAULT // 获取 CoerceInputsRule 的默认配置
            .withCoerceNames(false) // 设置不强制转换列名
            .withConsumerRelClass(LogicalUnion.class) // 设置消费者关系节点类为 LogicalUnion
            .toRule()); // 将配置转换为规则实例并添加到规划器
    planner.addRule( // 向规划器添加第二条规则
        CoerceInputsRule.Config.DEFAULT // 获取 CoerceInputsRule 的默认配置
            .withCoerceNames(false) // 设置不强制转换列名
            .withConsumerRelClass(LogicalIntersect.class) // 设置消费者关系节点类为 LogicalIntersect
            .withDescription("CoerceInputsRule:Intersection") // 设置规则描述为 "CoerceInputsRule:Intersection"
            .toRule()); // 将配置转换为规则实例并添加到规划器

    final String sql = "(select name from dept union select ename from emp)\n" // 定义测试 SQL 语句：dept 表的 name 和 emp 表的 ename 进行 UNION
        + "intersect (select fname from customer.contact)"; // 然后与 customer.contact 表的 fname 进行 INTERSECT
    sql(sql).withPlanner(planner).checkUnchanged(); // 执行 SQL 查询，使用指定的规划器，并检查计划是否未改变
  }

  @Test void testRuleDescription() { // 测试通过规则描述应用规则的测试方法
    // Verify that a rule can be applied via its description. // 验证规则可以通过其描述被应用

    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addRuleByDescription("FilterToCalcRule"); // 通过规则描述 "FilterToCalcRule" 添加规则到程序中

    HepPlanner planner = // 创建 HepPlanner 启发式规划器实例
        new HepPlanner( // 使用构造函数创建 HepPlanner
            programBuilder.build()); // 传入构建好的 HepProgram

    planner.addRule(CoreRules.FILTER_TO_CALC); // 向规划器添加 FilterToCalc 规则（将 Filter 节点转换为 Calc 节点）

    final String sql = "select name from sales.dept where deptno=12"; // 定义测试 SQL 语句：从 sales.dept 表中选择 deptno=12 的 name
    sql(sql).withPlanner(planner).check(); // 执行 SQL 查询，使用指定的规划器，并检查结果
  }

  /**
   * Ensures {@link org.apache.calcite.rel.AbstractRelNode} digest does not include
   * full digest tree.
   * 确保 AbstractRelNode 的摘要不包含完整的摘要树
   */ // 测试关系节点摘要长度的测试方法的文档注释
  @Test void testRelDigestLength() { // 测试关系节点摘要长度的测试方法
    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    HepPlanner planner = // 创建 HepPlanner 启发式规划器实例
        new HepPlanner( // 使用构造函数创建 HepPlanner
            programBuilder.build()); // 传入构建好的 HepProgram
    RelNode root = sql(buildUnion(10)).toRel(); // 构建包含 10 个 UNION ALL 的 SQL 查询，并将其转换为关系表达式树
    planner.setRoot(root); // 将关系表达式树设置为规划器的根节点
    RelNode best = planner.findBestExp(); // 查找最优的关系表达式

    // Good digest should look like // 好的摘要应该像这样
    //   rel#66:LogicalProject(input=rel#64:LogicalUnion) // 只包含直接输入，不包含完整的子树
    // Bad digest includes full tree, like // 坏的摘要包含完整的树，像这样
    //   rel#66:LogicalProject(input=rel#64:LogicalUnion(...)) // 包含了 LogicalUnion 的完整子树
    // So the assertion is to ensure digest includes LogicalUnion exactly once. // 因此断言确保摘要只包含一次 LogicalUnion
    assertIncludesExactlyOnce("best.getDescription()", // 断言 best 的描述字符串中
        best.toString(), "LogicalUnion"); // LogicalUnion 只出现一次
    assertIncludesExactlyOnce("best.getDigest()", // 断言 best 的摘要字符串中
        best.getDigest(), "LogicalUnion"); // LogicalUnion 只出现一次
  }

  private static String buildUnion(int n) { // 构建包含 n 个 UNION ALL 的 SQL 查询字符串的私有静态方法
    StringBuilder sb = new StringBuilder(); // 创建字符串构建器
    sb.append("select * from ("); // 开始构建查询，外层选择所有列
    sb.append("select name from sales.dept"); // 添加第一个查询：从 sales.dept 表选择 name
    for (int i = 0; i < n; i++) { // 循环 n 次
      sb.append(" union all select name from sales.dept"); // 每次添加一个 UNION ALL 分支
    }
    sb.append(")"); // 结束外层查询
    return sb.toString(); // 返回构建好的 SQL 查询字符串
  }

  @Test void testPlanToDot() { // 测试将计划转换为 DOT 格式的测试方法
    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    HepPlanner planner = // 创建 HepPlanner 启发式规划器实例
        new HepPlanner( // 使用构造函数创建 HepPlanner
            programBuilder.build()); // 传入构建好的 HepProgram
    RelNode root = sql("select name from sales.dept").toRel(); // 将 SQL 查询转换为关系表达式树
    planner.setRoot(root); // 将关系表达式树设置为规划器的根节点

    StringWriter sw = new StringWriter(); // 创建字符串写入器，用于捕获输出
    PrintWriter pw = new PrintWriter(sw); // 创建打印写入器，包装字符串写入器

    RelDotWriter planWriter = new RelDotWriter(pw, SqlExplainLevel.EXPPLAN_ATTRIBUTES, false); // 创建 DOT 格式写入器，使用 EXPPLAN_ATTRIBUTES 解释级别
    final RelNode root1 = planner.getRoot(); // 获取规划器的根节点
    assertThat(root1, notNullValue()); // 断言根节点不为 null
    root1.explain(planWriter); // 使用 DOT 写入器解释根节点（将计划转换为 DOT 格式）
    String planStr = sw.toString(); // 获取生成的 DOT 格式字符串

    assertThat( // 断言生成的 DOT 字符串符合预期
        planStr, isLinux("digraph {\n" // 在 Linux 环境下，DOT 格式应该包含以下内容
            + "\"LogicalTableScan\\ntable = [CATALOG, SA\\nLES, DEPT]\\n\" -> " // 逻辑表扫描节点
            + "\"LogicalProject\\nNAME = $1\\n\" [label=\"0\"]\n" // 逻辑投影节点
            + "}\n")); // 结束 digraph
  }

  private void assertIncludesExactlyOnce(String message, String digest, // 断言字符串中子字符串只出现一次的私有方法
      String substring) { // 参数：断言消息、要检查的字符串、子字符串
    int pos = 0; // 初始化位置为 0
    int cnt = 0; // 初始化计数器为 0
    while (pos >= 0) { // 当位置有效时循环
      pos = digest.indexOf(substring, pos + 1); // 从下一个位置开始查找子字符串
      if (pos > 0) { // 如果找到子字符串
        cnt++; // 计数器加 1
      }
    }
    assertThat(message + " should include <<" + substring + ">> exactly once" // 断言子字符串只出现一次
        + ", actual value is " + digest, // 并在消息中包含实际值
        cnt, is(1)); // 断言计数器等于 1
  }

  @Test void testMatchLimitOneTopDown() { // 测试自顶向下匹配限制为 1 的测试方法
    // Verify that only the top union gets rewritten. // 验证只有顶层的 UNION 被重写

    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addMatchOrder(HepMatchOrder.TOP_DOWN); // 设置匹配顺序为自顶向下
    programBuilder.addMatchLimit(1); // 设置匹配限制为 1（只应用规则一次）
    programBuilder.addRuleInstance(CoreRules.UNION_TO_DISTINCT); // 添加 UNION_TO_DISTINCT 规则（将 UNION ALL 转换为 UNION DISTINCT）

    sql(UNION_TREE).withProgram(programBuilder.build()).check(); // 执行 UNION_TREE 查询，使用构建的程序，并检查结果
  }

  @Test void testMatchLimitOneBottomUp() { // 测试自底向上匹配限制为 1 的测试方法
    // Verify that only the bottom union gets rewritten. // 验证只有底层的 UNION 被重写

    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addMatchLimit(1); // 设置匹配限制为 1（只应用规则一次）
    programBuilder.addMatchOrder(HepMatchOrder.BOTTOM_UP); // 设置匹配顺序为自底向上
    programBuilder.addRuleInstance(CoreRules.UNION_TO_DISTINCT); // 添加 UNION_TO_DISTINCT 规则

    sql(UNION_TREE).withProgram(programBuilder.build()).check(); // 执行 UNION_TREE 查询，使用构建的程序，并检查结果
  }

  @Test void testMatchUntilFixpoint() { // 测试匹配直到达到固定点的测试方法
    // Verify that both unions get rewritten. // 验证两个 UNION 都被重写

    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addMatchLimit(HepProgram.MATCH_UNTIL_FIXPOINT); // 设置匹配限制为 MATCH_UNTIL_FIXPOINT（持续匹配直到达到固定点）
    programBuilder.addRuleInstance(CoreRules.UNION_TO_DISTINCT); // 添加 UNION_TO_DISTINCT 规则

    sql(UNION_TREE).withProgram(programBuilder.build()).check(); // 执行 UNION_TREE 查询，使用构建的程序，并检查结果
  }

  @Test void testReplaceCommonSubexpression() { // 测试替换公共子表达式的测试方法
    // Note that here it may look like the rule is firing // 注意这里看起来规则好像触发了两次
    // twice, but actually it's only firing once on the // 但实际上它只在公共子表达式上触发了一次
    // common sub-expression.  The purpose of this test // 这个测试的目的是确保规划器能够处理
    // is to make sure the planner can deal with // 由同一个父节点（在这个例子中是 JOIN）使用两次的
    // rewriting something used as a common sub-expression // 公共子表达式的重写
    // twice by the same parent (the join in this case). // （在这个例子中是连接操作）

    final String sql = "select d1.deptno from (select * from dept) d1,\n" // 定义测试 SQL：从 dept 表选择所有列别名为 d1
        + "(select * from dept) d2"; // 再从 dept 表选择所有列别名为 d2，然后连接这两个结果
    sql(sql).withRule(CoreRules.PROJECT_REMOVE).check(); // 执行 SQL 查询，使用 PROJECT_REMOVE 规则，并检查结果
  }

  /** Tests that if two relational expressions are equivalent, the planner
   * notices, and only applies the rule once. */ // 测试如果两个关系表达式等价，规划器会注意到并且只应用规则一次
  @Test void testCommonSubExpression() { // 测试公共子表达式的测试方法
    // In the following, // 在下面的查询中
    //   (select 1 from dept where abs(-1)=20) // 子查询 (select 1 from dept where abs(-1)=20)
    // occurs twice, but it's a common sub-expression, so the rule should only // 出现了两次，但它是公共子表达式，所以规则应该只应用一次
    // apply once. // 应用一次
    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addRuleInstance(CoreRules.FILTER_TO_CALC); // 添加 FILTER_TO_CALC 规则

    final HepTestListener listener = new HepTestListener(0); // 创建测试监听器，初始应用次数为 0
    HepPlanner planner = new HepPlanner(programBuilder.build()); // 创建 HepPlanner 规划器
    planner.addListener(listener); // 将监听器添加到规划器

    final String sql = "(select 1 from dept where abs(-1)=20)\n" // 定义测试 SQL：第一个 UNION ALL 分支
        + "union all\n" // 使用 UNION ALL 连接
        + "(select 1 from dept where abs(-1)=20)"; // 第二个 UNION ALL 分支（与第一个完全相同）
    planner.setRoot(sql(sql).toRel()); // 将 SQL 查询转换为关系表达式并设置为规划器的根节点
    RelNode bestRel = planner.findBestExp(); // 查找最优的关系表达式

    assertThat(bestRel.getInput(0).equals(bestRel.getInput(1)), is(true)); // 断言两个输入是相等的（即它们是同一个公共子表达式）
    assertThat(listener.getApplyTimes() == 1, is(true)); // 断言规则只应用了一次
  }

  @Test void testSubprogram() { // 测试子程序的测试方法
    // Verify that subprogram gets re-executed until fixpoint. // 验证子程序会被重复执行直到达到固定点
    // In this case, the first time through we limit it to generate // 在这种情况下，第一次通过时我们限制它只生成
    // only one calc; the second time through it will generate // 一个 calc；第二次通过时它将生成
    // a second calc, and then merge them. // 第二个 calc，然后合并它们

    HepProgramBuilder subprogramBuilder = HepProgram.builder(); // 创建子程序构建器
    subprogramBuilder.addMatchOrder(HepMatchOrder.TOP_DOWN); // 设置匹配顺序为自顶向下
    subprogramBuilder.addMatchLimit(1); // 设置匹配限制为 1
    subprogramBuilder.addRuleInstance(CoreRules.PROJECT_TO_CALC); // 添加 PROJECT_TO_CALC 规则
    subprogramBuilder.addRuleInstance(CoreRules.FILTER_TO_CALC); // 添加 FILTER_TO_CALC 规则
    subprogramBuilder.addRuleInstance(CoreRules.CALC_MERGE); // 添加 CALC_MERGE 规则（合并相邻的 Calc 节点）

    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建主程序构建器
    programBuilder.addSubprogram(subprogramBuilder.build()); // 将子程序添加到主程序中

    final String sql = "select upper(ename) from\n" // 定义测试 SQL：外层选择 upper(ename)
        + "(select lower(ename) as ename from emp where empno = 100)"; // 内层查询：从 emp 表选择 empno=100 的 lower(ename)
    sql(sql).withProgram(programBuilder.build()).check(); // 执行 SQL 查询，使用构建的程序，并检查结果
  }

  @Test void testGroup() { // 测试规则组的测试方法
    // Verify simultaneous application of a group of rules. // 验证一组规则同时应用
    // Intentionally add them in the wrong order to make sure // 故意以错误的顺序添加它们，以确保
    // that order doesn't matter within the group. // 在组内顺序不重要

    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addGroupBegin(); // 开始定义规则组
    programBuilder.addRuleInstance(CoreRules.CALC_MERGE); // 添加 CALC_MERGE 规则
    programBuilder.addRuleInstance(CoreRules.PROJECT_TO_CALC); // 添加 PROJECT_TO_CALC 规则
    programBuilder.addRuleInstance(CoreRules.FILTER_TO_CALC); // 添加 FILTER_TO_CALC 规则
    programBuilder.addGroupEnd(); // 结束规则组定义

    final String sql = "select upper(name) from dept where deptno=20"; // 定义测试 SQL：从 dept 表选择 deptno=20 的 upper(name)
    sql(sql).withProgram(programBuilder.build()).check(); // 执行 SQL 查询，使用构建的程序，并检查结果
  }

  @Test void testGC() { // 测试垃圾回收的测试方法
    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addMatchOrder(HepMatchOrder.TOP_DOWN); // 设置匹配顺序为自顶向下
    programBuilder.addRuleInstance(CoreRules.CALC_MERGE); // 添加 CALC_MERGE 规则
    programBuilder.addRuleInstance(CoreRules.PROJECT_TO_CALC); // 添加 PROJECT_TO_CALC 规则
    programBuilder.addRuleInstance(CoreRules.FILTER_TO_CALC); // 添加 FILTER_TO_CALC 规则

    HepPlanner planner = new HepPlanner(programBuilder.build()); // 创建 HepPlanner 规划器
    planner.setRoot( // 设置规划器的根节点
        sql("select upper(name) from dept where deptno=20").toRel()); // 将 SQL 查询转换为关系表达式
    planner.findBestExp(); // 查找最优的关系表达式
    // Reuse of HepPlanner (should trigger GC). // 重用 HepPlanner（应该触发垃圾回收）
    planner.setRoot( // 再次设置规划器的根节点
        sql("select upper(name) from dept where deptno=20").toRel()); // 使用相同的 SQL 查询
    planner.findBestExp(); // 再次查找最优的关系表达式
  }

  @Test void testRelNodeCacheWithDigest() { // 测试使用摘要的关系节点缓存的测试方法
    HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    HepPlanner planner = // 创建 HepPlanner 启发式规划器实例
        new HepPlanner( // 使用构造函数创建 HepPlanner
            programBuilder.build()); // 传入构建好的 HepProgram
    String query = "(select n_nationkey from SALES.CUSTOMER) union all\n" // 定义测试查询：从 SALES.CUSTOMER 表选择 n_nationkey
        + "(select n_name from CUSTOMER_MODIFIABLEVIEW)"; // 与 CUSTOMER_MODIFIABLEVIEW 表的 n_name 进行 UNION ALL
    sql(query) // 执行查询
        .withDynamicTable() // 使用动态表
        .withDecorrelate(true) // 启用去相关
        .withProgram(programBuilder.build()) // 使用构建的程序
        .withPlanner(planner) // 使用指定的规划器
        .checkUnchanged(); // 检查计划是否未改变
  }

  @Test void testRuleApplyCount() { // 测试规则应用次数的测试方法
    final long applyTimes1 = checkRuleApplyCount(HepMatchOrder.ARBITRARY); // 使用任意顺序检查规则应用次数
    assertThat(applyTimes1, is(316L)); // 断言应用次数为 316 次

    final long applyTimes2 = checkRuleApplyCount(HepMatchOrder.DEPTH_FIRST); // 使用深度优先顺序检查规则应用次数
    assertThat(applyTimes2, is(87L)); // 断言应用次数为 87 次
  }

  @Test void testMaterialization() { // 测试物化视图的测试方法
    HepPlanner planner = new HepPlanner(HepProgram.builder().build()); // 创建 HepPlanner 规划器
    RelNode tableRel = sql("select * from dept").toRel(); // 将 SQL 查询转换为关系表达式（表关系）
    RelNode queryRel = tableRel; // 查询关系设置为表关系（即物化视图的查询就是表本身）
    RelOptMaterialization mat1 = // 创建物化视图对象
        new RelOptMaterialization(tableRel, queryRel, null, // 参数：表关系、查询关系、可修改视图
            ImmutableList.of("default", "mv")); // 物化视图的限定名称列表
    planner.addMaterialization(mat1); // 将物化视图添加到规划器
    assertThat(planner.getMaterializations(), hasSize(1)); // 断言规划器有 1 个物化视图
    assertThat(mat1, is(planner.getMaterializations().get(0))); // 断言添加的物化视图是规划器中的第一个
    planner.clear(); // 清除规划器
    assertThat(planner.getMaterializations(), empty()); // 断言规划器的物化视图列表为空
  }

  private long checkRuleApplyCount(HepMatchOrder matchOrder) { // 检查规则应用次数的私有方法
    final HepProgramBuilder programBuilder = HepProgram.builder(); // 创建 HepProgram 构建器
    programBuilder.addMatchOrder(matchOrder); // 设置匹配顺序
    programBuilder.addRuleInstance(CoreRules.FILTER_REDUCE_EXPRESSIONS); // 添加 FILTER_REDUCE_EXPRESSIONS 规则（简化 Filter 中的表达式）
    programBuilder.addRuleInstance(CoreRules.PROJECT_REDUCE_EXPRESSIONS); // 添加 PROJECT_REDUCE_EXPRESSIONS 规则（简化 Project 中的表达式）

    final HepTestListener listener = new HepTestListener(0); // 创建测试监听器，初始应用次数为 0
    HepPlanner planner = new HepPlanner(programBuilder.build()); // 创建 HepPlanner 规划器
    planner.addListener(listener); // 将监听器添加到规划器
    planner.setRoot(sql(COMPLEX_UNION_TREE).toRel()); // 将复杂的 UNION ALL 查询转换为关系表达式并设置为根节点
    planner.findBestExp(); // 查找最优的关系表达式
    return listener.getApplyTimes(); // 返回规则应用次数
  }

  /** Listener for HepPlannerTest; counts how many times rules fire. */ // HepPlannerTest 的监听器；统计规则触发的次数
  private static class HepTestListener implements RelOptListener { // HepTestListener 内部静态类，实现 RelOptListener 接口
    private long applyTimes; // 规则应用次数计数器

    HepTestListener(long applyTimes) { // 构造函数，初始化应用次数
      this.applyTimes = applyTimes; // 将传入的应用次数赋值给成员变量
    }

    long getApplyTimes() { // 获取规则应用次数的方法
      return applyTimes; // 返回应用次数
    }

    @Override public void relEquivalenceFound(RelEquivalenceEvent event) { // 当找到等价的关系表达式时调用
      // 空实现，不需要处理等价事件
    }

    @Override public void ruleAttempted(RuleAttemptedEvent event) { // 当尝试应用规则时调用
      if (event.isBefore()) { // 如果是在规则应用之前
        ++applyTimes; // 应用次数加 1
      }
    }

    @Override public void ruleProductionSucceeded(RuleProductionEvent event) { // 当规则应用成功时调用
      // 空实现，不需要处理规则应用成功事件
    }

    @Override public void relDiscarded(RelDiscardedEvent event) { // 当关系表达式被丢弃时调用
      // 空实现，不需要处理丢弃事件
    }

    @Override public void relChosen(RelChosenEvent event) { // 当选择关系表达式时调用
      // 空实现，不需要处理选择事件
    }
  }

  /** Test case for // 测试用例
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5401">[CALCITE-5401] // JIRA 问题链接
   * Rule fired by HepPlanner can return Volcano's RelSubset</a>. */ // HepPlanner 触发的规则可以返回 Volcano 的 RelSubset
  @Test void testAggregateRemove() { // 测试聚合移除的测试方法
    final RelBuilder builder = RelBuilderTest.createBuilder(c -> c.withAggregateUnique(true)); // 创建关系构建器，启用聚合唯一性
    final RelNode root = // 创建根关系节点
        builder // 使用构建器
            .values(new String[]{"i"}, 1, 2, 3) // 创建包含值 1, 2, 3 的 VALUES 节点，列名为 "i"
            .distinct() // 应用 DISTINCT 操作（去重）
            .build(); // 构建关系节点
    final HepProgram program = new HepProgramBuilder() // 创建 HepProgram
        .addRuleInstance(CoreRules.AGGREGATE_REMOVE) // 添加 AGGREGATE_REMOVE 规则
        .build(); // 构建程序
    final HepPlanner planner = new HepPlanner(program); // 创建 HepPlanner 规划器
    planner.setRoot(root); // 设置根节点
    final RelNode result = planner.findBestExp(); // 查找最优的关系表达式
    assertThat(result, is(instanceOf(LogicalValues.class))); // 断言结果是 LogicalValues 类型
  }
}