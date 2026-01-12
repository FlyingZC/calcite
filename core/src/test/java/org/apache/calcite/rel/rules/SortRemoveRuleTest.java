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
package org.apache.calcite.rel.rules;

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention，用于定义可枚举的约定特征
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入EnumerableRules，包含可枚举操作符的规则集合
import org.apache.calcite.plan.ConventionTraitDef; // 导入ConventionTraitDef，用于定义约定特征
import org.apache.calcite.plan.RelOptUtil; // 导入RelOptUtil，提供关系表达式优化的工具方法
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet，表示关系表达式的特征集合
import org.apache.calcite.rel.RelCollationTraitDef; // 导入RelCollationTraitDef，用于定义排序特征
import org.apache.calcite.rel.RelNode; // 导入RelNode，表示关系代数表达式的基本接口
import org.apache.calcite.rel.RelRoot; // 导入RelRoot，表示关系代数表达式的根节点
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus，表示数据库模式
import org.apache.calcite.schemas.HrClusteredSchema; // 导入HrClusteredSchema，提供按主键聚类的测试表
import org.apache.calcite.sql.SqlExplainFormat; // 导入SqlExplainFormat，定义SQL解释的输出格式
import org.apache.calcite.sql.SqlExplainLevel; // 导入SqlExplainLevel，定义SQL解释的详细程度
import org.apache.calcite.sql.SqlNode; // 导入SqlNode，表示SQL抽象语法树的节点
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser，用于解析SQL语句
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入SqlToRelConverter，用于将SQL转换为关系代数表达式
import org.apache.calcite.tools.FrameworkConfig; // 导入FrameworkConfig，表示Calcite框架的配置
import org.apache.calcite.tools.Frameworks; // 导入Frameworks，提供创建Calcite框架的工具方法
import org.apache.calcite.tools.Planner; // 导入Planner，表示查询计划器
import org.apache.calcite.tools.Programs; // 导入Programs，提供预定义的优化程序
import org.apache.calcite.tools.RuleSet; // 导入RuleSet，表示优化规则的集合
import org.apache.calcite.tools.RuleSets; // 导入RuleSets，提供创建规则集合的工具方法
import org.apache.calcite.util.Util; // 导入Util，提供通用的工具方法

import org.hamcrest.Matcher; // 导入Matcher，用于匹配测试结果
import org.junit.jupiter.api.Test; // 导入Test，用于标记测试方法

import java.util.Arrays; // 导入Arrays，提供数组操作的工具方法
import java.util.function.UnaryOperator; // 导入UnaryOperator，表示单目操作符函数接口

import static org.hamcrest.CoreMatchers.allOf; // 导入allOf，用于组合多个匹配器
import static org.hamcrest.CoreMatchers.containsString; // 导入containsString，用于检查字符串是否包含指定内容
import static org.hamcrest.CoreMatchers.not; // 导入not，用于对匹配器取反
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat，用于断言测试结果

/**
 * Tests the application of the {@link SortRemoveRule}.
 * 测试SortRemoveRule规则的应用，该规则用于移除冗余的Sort操作符
 */
public final class SortRemoveRuleTest { // 测试类，final表示不能被继承

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich enumerable join operators with order preserving information</a>.
   * 测试用例：验证在可枚举的HashJoin操作符上方移除冗余的Sort操作符
   *
   * <p>Since join inputs are sorted, and this join preserves the order of the
   * left input, there shouldn't be any sort operator above the join.
   * 由于join的输入已经排序，并且该join保持了左输入的顺序，因此在join上方不应该有任何sort操作符
   */
  @Test void removeSortOverEnumerableHashJoin() { // 测试方法，验证移除EnumerableHashJoin上方的Sort
    RuleSet prepareRules = // 定义准备阶段的规则集，用于将逻辑关系转换为物理可执行的关系
        RuleSets.ofList(CoreRules.SORT_PROJECT_TRANSPOSE, // SortProjectTranspose规则，将Sort下推到Project下方
            EnumerableRules.ENUMERABLE_JOIN_RULE, // 将Join转换为EnumerableHashJoin的规则
            EnumerableRules.ENUMERABLE_PROJECT_RULE, // 将Project转换为EnumerableProject的规则
            EnumerableRules.ENUMERABLE_SORT_RULE, // 将Sort转换为EnumerableSort的规则
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE); // 将TableScan转换为EnumerableTableScan的规则
    for (String joinType : Arrays.asList("left", "right", "full", "inner")) { // 遍历所有join类型：左连接、右连接、全连接、内连接
      String sql = // 构造SQL查询语句，从emps和depts表进行join，并按empid排序
          "select e.\"deptno\" from \"hr\".\"emps\" e " // 选择emps表的deptno字段
              + joinType + " join \"hr\".\"depts\" d " // 使用当前join类型连接depts表
              + " on e.\"deptno\" = d.\"deptno\" " // 连接条件是deptno相等
              + "order by e.\"empid\" "; // 按empid字段排序
      new Fixture(sql, prepareRules) // 创建测试夹具，传入SQL和规则集
          .assertThatPlan( // 断言执行计划符合预期
              allOf(containsString("EnumerableHashJoin"), // 计划中必须包含EnumerableHashJoin操作符
                  not(containsString("EnumerableSort")))); // 计划中不能包含EnumerableSort操作符（因为join保持了左输入的顺序）
    }
  }


  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich enumerable join operators with order preserving information</a>.
   * 测试用例：验证在可枚举的NestedLoopJoin操作符上方移除冗余的Sort操作符
   *
   * <p>Since join inputs are sorted, and this join preserves the order of the
   * left input, there shouldn't be any sort operator above the join.
   * 由于join的输入已经排序，并且该join保持了左输入的顺序，因此在join上方不应该有任何sort操作符
   */
  @Test void removeSortOverEnumerableNestedLoopJoin() { // 测试方法，验证移除EnumerableNestedLoopJoin上方的Sort
    RuleSet prepareRules = // 定义准备阶段的规则集
        RuleSets.ofList(CoreRules.SORT_PROJECT_TRANSPOSE, // SortProjectTranspose规则
            EnumerableRules.ENUMERABLE_JOIN_RULE, // 将Join转换为可枚举的join规则
            EnumerableRules.ENUMERABLE_PROJECT_RULE, // 将Project转换为EnumerableProject的规则
            EnumerableRules.ENUMERABLE_SORT_RULE, // 将Sort转换为EnumerableSort的规则
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE); // 将TableScan转换为EnumerableTableScan的规则
    // Inner join is not considered since the ENUMERABLE_JOIN_RULE does not generate a nestedLoop
    // join in the case of inner joins.
    // 内连接不考虑，因为ENUMERABLE_JOIN_RULE在内连接情况下不会生成嵌套循环连接
    for (String joinType : Arrays.asList("left", "right", "full")) { // 遍历左连接、右连接、全连接（不包括内连接）
      String sql = // 构造SQL查询语句，使用大于条件触发嵌套循环连接
          "select e.\"deptno\" from \"hr\".\"emps\" e " // 选择emps表的deptno字段
              + joinType + " join \"hr\".\"depts\" d " // 使用当前join类型连接depts表
              + " on e.\"deptno\" > d.\"deptno\" " // 使用大于条件，这会触发嵌套循环连接而不是哈希连接
              + "order by e.\"empid\" "; // 按empid字段排序
      new Fixture(sql, prepareRules) // 创建测试夹具
          .assertThatPlan( // 断言执行计划
              allOf(containsString("EnumerableNestedLoopJoin"), // 计划中必须包含EnumerableNestedLoopJoin
                  not(containsString("EnumerableSort")))); // 计划中不能包含EnumerableSort
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich enumerable join operators with order preserving information</a>.
   * 测试用例：验证在可枚举的Correlate操作符上方移除冗余的Sort操作符
   *
   * <p>Since join inputs are sorted, and this join preserves the order of the
   * left input, there shouldn't be any sort operator above the join.
   * 由于join的输入已经排序，并且该join保持了左输入的顺序，因此在join上方不应该有任何sort操作符
   *
   * <p>Until CALCITE-2018 is fixed we can add back EnumerableRules.ENUMERABLE_SORT_RULE
   * 在CALCITE-2018修复之前，可以重新添加EnumerableRules.ENUMERABLE_SORT_RULE
   */
  @Test void removeSortOverEnumerableCorrelate() throws Exception { // 测试方法，验证移除EnumerableCorrelate上方的Sort
    RuleSet prepareRules = // 定义准备阶段的规则集
        RuleSets.ofList(CoreRules.SORT_PROJECT_TRANSPOSE, // SortProjectTranspose规则
            CoreRules.JOIN_TO_CORRELATE, // 将Join转换为Correlate的规则
            EnumerableRules.ENUMERABLE_PROJECT_RULE, // 将Project转换为EnumerableProject的规则
            EnumerableRules.ENUMERABLE_CORRELATE_RULE, // 将Correlate转换为EnumerableCorrelate的规则
            EnumerableRules.ENUMERABLE_FILTER_RULE, // 将Filter转换为EnumerableFilter的规则
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE); // 将TableScan转换为EnumerableTableScan的规则
    for (String joinType : Arrays.asList("left", "inner")) { // 遍历左连接和内连接
      String sql = // 构造SQL查询语句
          "select e.\"deptno\" from \"hr\".\"emps\" e " // 选择emps表的deptno字段
              + joinType + " join \"hr\".\"depts\" d " // 使用当前join类型连接depts表
              + " on e.\"deptno\" = d.\"deptno\" " // 连接条件是deptno相等
              + "order by e.\"empid\" "; // 按empid字段排序
      RelNode actualPlan = new Fixture(sql, prepareRules).plan(); // 生成实际的执行计划
      assertThat( // 断言执行计划
          toString(actualPlan), // 将执行计划转换为字符串
          allOf( // 组合多个匹配条件
              containsString("EnumerableCorrelate"), // 计划中必须包含EnumerableCorrelate
              not(containsString("EnumerableSort")))); // 计划中不能包含EnumerableSort
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2554">[CALCITE-2554]
   * Enrich enumerable join operators with order preserving information</a>.
   * 测试用例：验证在可枚举的SemiJoin操作符上方移除冗余的Sort操作符
   *
   * <p>Since join inputs are sorted, and this join preserves the order of the
   * left input, there shouldn't be any sort operator above the join.
   * 由于join的输入已经排序，并且该join保持了左输入的顺序，因此在join上方不应该有任何sort操作符
   */
  @Test void removeSortOverEnumerableSemiJoin() throws Exception { // 测试方法，验证移除SemiJoin上方的Sort
    RuleSet prepareRules = // 定义准备阶段的规则集
        RuleSets.ofList(CoreRules.SORT_PROJECT_TRANSPOSE, // SortProjectTranspose规则
            CoreRules.PROJECT_TO_SEMI_JOIN, // 将Project转换为SemiJoin的规则
            CoreRules.JOIN_TO_SEMI_JOIN, // 将Join转换为SemiJoin的规则
            EnumerableRules.ENUMERABLE_PROJECT_RULE, // 将Project转换为EnumerableProject的规则
            EnumerableRules.ENUMERABLE_SORT_RULE, // 将Sort转换为EnumerableSort的规则
            EnumerableRules.ENUMERABLE_JOIN_RULE, // 将Join转换为可枚举的join规则
            EnumerableRules.ENUMERABLE_FILTER_RULE, // 将Filter转换为EnumerableFilter的规则
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE); // 将TableScan转换为EnumerableTableScan的规则
    String sql = // 构造SQL查询语句，使用IN子查询
        "select e.\"deptno\" from \"hr\".\"emps\" e\n" // 选择emps表的deptno字段
            + " where e.\"deptno\" in (select d.\"deptno\" from \"hr\".\"depts\" d)\n" // 使用IN子查询，会被转换为SemiJoin
            + " order by e.\"empid\""; // 按empid字段排序
    RelNode actualPlan = new Fixture(sql, prepareRules) // 创建测试夹具
        .withSqlToRel(c -> c.withExpand(true)) // 配置SqlToRelConverter，启用子查询展开
        .plan(); // 生成执行计划
    assertThat( // 断言执行计划
        toString(actualPlan), // 将执行计划转换为字符串
        allOf( // 组合多个匹配条件
            containsString("EnumerableHashJoin"), // 计划中必须包含EnumerableHashJoin（SemiJoin会被转换为HashJoin）
            not(containsString("EnumerableSort")))); // 计划中不能包含EnumerableSort
  }

  private static String toString(RelNode rel) { // 静态方法，将关系节点转换为字符串表示
    return Util.toLinux( // 将字符串转换为Linux格式（统一换行符）
        RelOptUtil.dumpPlan("", rel, SqlExplainFormat.TEXT, // 将关系节点转储为文本格式的执行计划
            SqlExplainLevel.DIGEST_ATTRIBUTES)); // 使用摘要级别的详细程度
  }

  /** Test fixture. */
  private static class Fixture { // 测试夹具类，用于封装测试所需的配置和工具方法
    final RuleSet prepareRules; // 成员变量：准备阶段的规则集，用于将逻辑关系转换为物理可执行的关系
    final String sql; // 成员变量：待测试的SQL查询语句
    final UnaryOperator<SqlToRelConverter.Config> sqlToRelConfigTransform; // 成员变量：SqlToRelConverter配置的转换函数，用于自定义SQL到关系代数的转换配置

    Fixture(String sql, RuleSet prepareRules, // 构造方法：创建测试夹具，传入SQL语句、规则集和配置转换函数
        UnaryOperator<SqlToRelConverter.Config> sqlToRelConfigTransform) { // 参数：sqlToRelConfigTransform用于修改SqlToRelConverter的默认配置
      this.prepareRules = prepareRules; // 初始化准备规则集
      this.sql = sql; // 初始化SQL语句
      this.sqlToRelConfigTransform = sqlToRelConfigTransform; // 初始化配置转换函数
    }

    Fixture(String sql, RuleSet prepareRules) { // 构造方法重载：创建测试夹具，只传入SQL语句和规则集
      this(sql, prepareRules, UnaryOperator.identity()); // 调用全参构造函数，使用恒等函数作为配置转换（不修改默认配置）
    }

    Fixture withSqlToRel(UnaryOperator<SqlToRelConverter.Config> transform) { // 方法：创建新的Fixture实例，应用额外的SqlToRelConverter配置转换
      final UnaryOperator<SqlToRelConverter.Config> newTransform = c -> // 创建新的转换函数，先应用当前转换，再应用新的转换
          transform.apply(this.sqlToRelConfigTransform.apply(c)); // 组合两个转换函数：先应用原有的转换，再应用新的转换
      return new Fixture(sql, prepareRules, newTransform); // 返回新的Fixture实例，保持sql和prepareRules不变，使用组合后的转换函数
    }

    /**
     * The default schema that is used in these tests provides tables sorted on the primary key. Due
     * to this scan operators always come with a {@link org.apache.calcite.rel.RelCollation} trait.
     * 测试中使用的默认模式提供了按主键排序的表。因此，扫描操作符总是带有RelCollation（排序）特征。
     * 这是测试SortRemoveRule的关键，因为如果表已经按主键排序，那么在join之后如果join保持了左输入的顺序，
     * 就不需要额外的sort操作符。
     */
    RelNode plan() throws Exception { // 方法：生成执行计划，将SQL转换为优化后的关系代数表达式
      final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根模式，true表示添加内置函数
      final SchemaPlus defSchema = rootSchema.add("hr", new HrClusteredSchema()); // 添加hr模式，使用HrClusteredSchema（表按主键聚类的模式）
      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
          .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
          .sqlToRelConverterConfig( // 设置SQL到关系代数转换器的配置
              sqlToRelConfigTransform.apply(SqlToRelConverter.config())) // 应用配置转换函数到默认配置
          .defaultSchema(defSchema) // 设置默认模式为hr模式
          .traitDefs(ConventionTraitDef.INSTANCE, RelCollationTraitDef.INSTANCE) // 定义特征：约定特征和排序特征
          .programs( // 设置优化程序
              Programs.of(prepareRules), // 第一个优化程序：使用prepareRules进行准备阶段的转换
              Programs.ofRules(CoreRules.SORT_REMOVE)) // 第二个优化程序：使用SORT_REMOVE规则移除冗余的Sort
          .build(); // 构建框架配置
      Planner planner = Frameworks.getPlanner(config); // 根据配置创建计划器
      SqlNode parse = planner.parse(sql); // 解析SQL语句，生成抽象语法树
      SqlNode validate = planner.validate(parse); // 验证抽象语法树，检查语义正确性
      RelRoot planRoot = planner.rel(validate); // 将验证后的SQL转换为关系代数表达式，得到关系根节点
      RelNode planBefore = planRoot.rel; // 获取初始的关系代数表达式（未优化的逻辑计划）
      RelTraitSet desiredTraits = planBefore.getTraitSet() // 获取当前特征集合
          .replace(EnumerableConvention.INSTANCE); // 替换约定特征为可枚举约定（将逻辑计划转换为物理可执行计划）
      RelNode planAfter = planner.transform(0, desiredTraits, planBefore); // 应用第一个优化程序（prepareRules），将逻辑计划转换为物理计划
      return planner.transform(1, desiredTraits, planAfter); // 应用第二个优化程序（SORT_REMOVE），移除冗余的Sort操作符，返回最终优化后的计划
    }

    Fixture assertThatPlan(Matcher<String> matcher) { // 方法：断言执行计划符合指定的匹配器
      try { // 捕获可能的异常
        RelNode actualPlan = plan(); // 生成实际的执行计划
        assertThat( // 断言执行计划
            SortRemoveRuleTest.toString(actualPlan), // 将执行计划转换为字符串
            matcher); // 使用匹配器验证计划字符串
      } catch (Exception e) { // 捕获异常
        throw new RuntimeException(e); // 将异常包装为运行时异常抛出
      }
      return this; // 返回当前Fixture实例，支持链式调用
    }
  }
}
