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
package org.apache.calcite.test; // 声明包名，该类属于 org.apache.calcite.test 包

import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入可枚举规则集合，包含各种可枚举物理实现规则
import org.apache.calcite.plan.ConventionTraitDef; // 导入约定特征定义，用于定义关系代数节点的调用约定
import org.apache.calcite.plan.RelOptUtil; // 导入关系优化工具类，提供优化相关的实用方法
import org.apache.calcite.plan.volcano.VolcanoPlanner; // 导入火山优化器，Calcite 的基于成本和规则的优化器
import org.apache.calcite.rel.RelCollationTraitDef; // 导入排序特征定义，用于定义关系代数节点的排序属性
import org.apache.calcite.rel.rules.CoreRules; // 导入核心规则集合，包含各种核心转换规则
import org.apache.calcite.rel.rules.JoinPushThroughJoinRule; // 导入连接下推规则，用于优化连接操作

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为 null 的值
import org.junit.jupiter.api.AfterAll; // 导入 JUnit5 的 AfterAll 注解，用于在所有测试方法执行后执行
import org.junit.jupiter.api.Test; // 导入 JUnit5 的 Test 注解，用于标记测试方法

import java.util.function.Consumer; // 导入函数式接口 Consumer，用于接受单个参数并返回 void 的操作

/**
 * Unit test for top-down optimization. // 自顶向下优化单元测试类
 *
 * <p>As input, the test supplies a SQL statement and rules; the SQL is // 测试输入：SQL 语句和规则
 * translated into relational algebra and then fed into a // SQL 被转换为关系代数，然后输入到
 * {@link VolcanoPlanner}. The plan before and after "optimization" is // VolcanoPlanner 中。优化前后的计划
 * diffed against a reference file using {@link DiffRepository}. // 使用 DiffRepository 与参考文件进行对比
 *
 * <p>Procedure for adding a new test case: // 添加新测试用例的步骤：
 *
 * <ol>
 * <li>Add a new public test method for your rule, following the existing // 1. 为你的规则添加一个新的公共测试方法，遵循现有示例
 * examples. You'll have to come up with an SQL statement to which your rule // 你需要想出一个 SQL 语句，使你的规则能够有意义地应用
 * will apply in a meaningful way. See // 参见
 * {@link org.apache.calcite.test.catalog.MockCatalogReaderSimple} class // MockCatalogReaderSimple 类以了解模式的详细信息
 * for details on the schema.
 *
 * <li>Run the test. It should fail. Inspect the output in // 2. 运行测试。它应该失败。检查输出文件
 * {@code build/resources/test/.../TopDownOptTest_actual.xml}.
 *
 * <li>Verify that the "planBefore" is the correct // 3. 验证 "planBefore" 是你的 SQL 的正确转换
 * translation of your SQL, and that it contains the pattern on which your rule // 并且它包含你的规则应该触发的模式
 * is supposed to fire. If all is well, replace // 如果一切正常，用新的实际文件替换参考文件
 * {@code src/test/resources/.../TopDownOptTest.xml} with
 * the new {@code build/resources/test/.../TopDownOptTest_actual.xml}.
 *
 * <li>Run the test again. It should fail again, but this time it should contain // 4. 再次运行测试。它应该再次失败，但这次应该包含你的规则的 "planAfter" 条目
 * a "planAfter" entry for your rule. Verify that your rule applied its // 验证你的规则正确应用了转换，然后再次更新参考文件
 * transformation correctly, and then update the
 * {@code src/test/resources/.../TopDownOptTest.xml} file again.
 *
 * <li>Run the test one last time; this time it should pass. // 5. 最后一次运行测试；这次它应该通过
 * </ol>
 */
class TopDownOptTest { // TopDownOptTest 类：自顶向下优化测试类，用于测试 Calcite 的自顶向下优化器行为

  @Nullable // 可空注解，表示该字段可能为 null
  private static DiffRepository diffRepos = null; // 差异仓库静态字段，用于存储测试参考文件和实际输出之间的差异比较器

  @AfterAll // AfterAll 注解，表示该方法在所有测试方法执行后执行一次
  public static void checkActualAndReferenceFiles() { // 检查实际输出文件和参考文件的方法，确保测试结果与预期一致
    if (diffRepos != null) { // 如果差异仓库不为空
      diffRepos.checkActualAndReferenceFiles(); // 调用差异仓库的检查方法，比较实际输出和参考文件
    } // 结束 if 语句
  } // 结束 checkActualAndReferenceFiles 方法

  RelOptFixture fixture() { // 创建并返回关系优化测试夹具的方法，用于设置测试环境
    RelOptFixture fixture = RelOptFixture.DEFAULT // 创建默认的关系优化测试夹具
        .withDiffRepos(DiffRepository.lookup(TopDownOptTest.class)); // 查找并设置差异仓库，用于比较测试结果
    diffRepos = fixture.diffRepos(); // 保存差异仓库引用，以便后续检查
    return fixture; // 返回配置好的测试夹具
  } // 结束 fixture 方法

  RelOptFixture sql(String sql, Consumer<VolcanoPlanner> init) { // 配置 SQL 测试的方法，接受 SQL 语句和优化器初始化函数
    return fixture().sql(sql) // 获取测试夹具并设置 SQL 语句
        .withVolcanoPlanner(true, init); // 启用火山优化器并应用初始化配置
  } // 结束 sql 方法

  @Test void testValuesTraitRequest() { // 测试 VALUES 表达式的排序特征请求，验证自顶向下优化器能否正确处理 VALUES 的排序需求
    final String sql = "SELECT * from (values (1, 1), (2, 1), (1, 2), (2, 2))\n" // 定义 SQL 语句：从 VALUES 表达式中选择数据，包含四行数据，每行两个值
        + "as t(a, b) order by b, a"; // 将 VALUES 表达式命名为 t，包含列 a 和 b，按 b 升序、a 升序排序
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testValuesTraitRequest 方法

  @Test void testValuesTraitRequestNeg() { // 测试 VALUES 表达式的排序特征请求的负例，验证优化器在无法满足排序需求时的行为
    final String sql = "SELECT * from (values (1, 1), (2, 1), (3, 2), (2, 2))\n" // 定义 SQL 语句：从 VALUES 表达式中选择数据，包含四行数据，其中第三行的第一个值是 3，与前面的数据不同
        + "as t(a, b) order by b, a"; // 将 VALUES 表达式命名为 t，包含列 a 和 b，按 b 升序、a 升序排序
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testValuesTraitRequestNeg 方法

  @Test void testSortAgg() { // 测试排序聚合，验证优化器能否正确处理带排序和限制的聚合查询
    final String sql = "select mgr, count(*) from sales.emp\n" // 定义 SQL 语句：从 sales.emp 表中选择 mgr 列和计数，按 mgr 分组
        + "group by mgr order by mgr desc nulls last limit 5"; // 按 mgr 降序排序，null 值排在最后，限制结果为 5 行
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortAgg 方法

  @Test void testSortAggPartialKey() { // 测试部分键的排序聚合，验证优化器能否正确处理带多个分组列和复杂排序的聚合查询
    final String sql = "select mgr,deptno,comm,count(*) from sales.emp\n" // 定义 SQL 语句：从 sales.emp 表中选择 mgr、deptno、comm 列和计数，按 mgr、deptno、comm 分组
        + "group by mgr,deptno,comm\n" // 按 mgr、deptno、comm 三个列分组
        + "order by comm desc nulls last, deptno nulls first"; // 按 comm 降序排序（null 最后），然后按 deptno 升序排序（null 最前）
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortAggPartialKey 方法

  @Test void testSortMergeJoin() { // 测试排序合并连接，验证优化器能否正确处理带排序的合并连接
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename 和 job 都相等
        + "order by r.job desc nulls last, r.ename nulls first"; // 按 r.job 降序排序（null 最后），然后按 r.ename 升序排序（null 最前）
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoin 方法

  @Test void testSortMergeJoinSubsetKey() { // 测试排序合并连接的子集键，验证优化器能否正确处理排序键是连接键子集的情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename 和 job 都相等
        + "order by r.job desc nulls last"; // 只按 r.job 降序排序（null 最后），这是连接键的子集
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoinSubsetKey 方法

  @Test void testSortMergeJoinSubsetKey2() { // 测试排序合并连接的子集键的第二种情况，包含三个连接键
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job and r.sal = s.sal\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename、job 和 sal 都相等
        + "order by r.sal, r.ename desc nulls last"; // 按 r.sal 升序排序，然后按 r.ename 降序排序（null 最后），这是连接键的子集
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoinSubsetKey2 方法

  @Test void testSortMergeJoinSupersetKey() { // 测试排序合并连接的超集键，验证优化器能否正确处理排序键包含连接键的情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename 和 job 都相等
        + "order by r.job desc nulls last, r.ename, r.sal desc"; // 按 r.job 降序排序（null 最后），然后按 r.ename 升序排序，最后按 r.sal 降序排序，这包含连接键和额外字段
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoinSupersetKey 方法

  @Test void testSortMergeJoinRight() { // 测试排序合并连接的右侧排序，验证优化器能否正确处理基于右侧表的排序
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename 和 job 都相等
        + "order by s.job desc nulls last, s.ename nulls first"; // 按 s.job（右侧表）降序排序（null 最后），然后按 s.ename（右侧表）升序排序（null 最前）
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoinRight 方法

  @Test void testSortMergeJoinRightSubsetKey() { // 测试排序合并连接的右侧子集键，验证优化器能否正确处理基于右侧表连接键子集的排序
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename 和 job 都相等
        + "order by s.job desc nulls last"; // 只按 s.job（右侧表）降序排序（null 最后），这是右侧表连接键的子集
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoinRightSubsetKey 方法

  @Test void testSortMergeJoinRightSubsetKey2() { // 测试排序合并连接的右侧子集键的第二种情况，包含三个连接键
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job and r.sal = s.sal\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename、job 和 sal 都相等
        + "order by s.sal, s.ename desc nulls last"; // 按 s.sal（右侧表）升序排序，然后按 s.ename（右侧表）降序排序（null 最后），这是右侧表连接键的子集
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoinRightSubsetKey2 方法

  @Test void testSortMergeJoinRightSupersetKey() { // 测试排序合并连接的右侧超集键，验证优化器能否正确处理基于右侧表连接键超集的排序
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 连接 sales.emp 表（别名 r）和 sales.bonus 表（别名 s），连接条件是 ename 和 job 都相等
        + "order by s.job desc nulls last, s.ename, s.sal desc"; // 按 s.job（右侧表）降序排序（null 最后），然后按 s.ename（右侧表）升序排序，最后按 s.sal（右侧表）降序排序，这包含右侧表连接键和额外字段
    sql(sql, this::initPlanner).check(); // 执行 SQL 测试，使用默认优化器初始化配置，并检查结果是否符合预期
  } // 结束 testSortMergeJoinRightSupersetKey 方法

  @Test void testMergeJoinDeriveLeft1() { // 测试合并连接从左侧推导排序，验证优化器能否从左侧聚合结果推导出所需的排序
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, max(sal) from sales.emp group by ename, job) r\n" // 从 sales.emp 表中选择 ename、job 和最大 sal，按 ename 和 job 分组，结果别名为 r
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，强制使用合并连接
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testMergeJoinDeriveLeft1 方法

  @Test void testMergeJoinDeriveLeft2() { // 测试合并连接从左侧推导排序的第二种情况，包含三个分组列
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr, max(sal) from sales.emp group by ename, job, mgr) r\n" // 从 sales.emp 表中选择 ename、job、mgr 和最大 sal，按 ename、job 和 mgr 分组，结果别名为 r
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，强制使用合并连接
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testMergeJoinDeriveLeft2 方法

  @Test void testMergeJoinDeriveRight1() { // 测试合并连接从右侧推导排序，验证优化器能否从右侧聚合结果推导出所需的排序
    final String sql = "select * from sales.bonus s join\n" // 定义 SQL 语句：选择所有列，sales.bonus 表作为左侧（别名 s）
        + "(select ename, job, max(sal) from sales.emp group by ename, job) r\n" // 从 sales.emp 表中选择 ename、job 和最大 sal，按 ename 和 job 分组，结果别名为 r，作为右侧
        + "on r.job=s.job and r.ename=s.ename"; // 连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，强制使用合并连接
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testMergeJoinDeriveRight1 方法

  @Test void testMergeJoinDeriveRight2() { // 测试合并连接从右侧推导排序的第二种情况，包含三个分组列
    final String sql = "select * from sales.bonus s join\n" // 定义 SQL 语句：选择所有列，sales.bonus 表作为左侧（别名 s）
        + "(select ename, job, mgr, max(sal) from sales.emp group by ename, job, mgr) r\n" // 从 sales.emp 表中选择 ename、job、mgr 和最大 sal，按 ename、job 和 mgr 分组，结果别名为 r，作为右侧
        + "on r.job=s.job and r.ename=s.ename"; // 连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则，强制使用合并连接
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testMergeJoinDeriveRight2 方法

  // Order by left field(s): push down sort to left input. // 按左侧字段排序：将排序下推到左侧输入
  @Test void testCorrelateInnerJoinDeriveLeft() { // 测试相关内连接从左侧推导排序，验证优化器能否将排序下推到相关连接的左侧
    final String sql = "select * from emp e\n" // 定义 SQL 语句：选择所有列，emp 表别名为 e
        + "join dept d on e.deptno=d.deptno\n" // 与 dept 表（别名 d）内连接，连接条件是 deptno 相等
        + "order by e.ename"; // 按 e.ename（左侧表字段）排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.JOIN_TO_CORRELATE); // 添加连接到相关连接的转换规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testCorrelateInnerJoinDeriveLeft 方法

  // Order by contains right field: sort cannot be pushed down. // 排序包含右侧字段：排序不能下推
  @Test void testCorrelateInnerJoinNoDerive() { // 测试相关内连接无法推导排序的情况，验证优化器在排序包含右侧字段时的行为
    final String sql = "select * from emp e\n" // 定义 SQL 语句：选择所有列，emp 表别名为 e
        + "join dept d on e.deptno=d.deptno\n" // 与 dept 表（别名 d）内连接，连接条件是 deptno 相等
        + "order by e.ename, d.name"; // 按 e.ename（左侧字段）和 d.name（右侧字段）排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.JOIN_TO_CORRELATE); // 添加连接到相关连接的转换规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testCorrelateInnerJoinNoDerive 方法

  // Order by left field(s): push down sort to left input. // 按左侧字段排序：将排序下推到左侧输入
  @Test void testCorrelateLeftJoinDeriveLeft() { // 测试相关左连接从左侧推导排序，验证优化器能否将排序下推到相关左连接的左侧
    final String sql = "select * from emp e\n" // 定义 SQL 语句：选择所有列，emp 表别名为 e
        + "left join dept d on e.deptno=d.deptno\n" // 与 dept 表（别名 d）左连接，连接条件是 deptno 相等
        + "order by e.ename"; // 按 e.ename（左侧表字段）排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.JOIN_TO_CORRELATE); // 添加连接到相关连接的转换规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testCorrelateLeftJoinDeriveLeft 方法

  // Order by contains right field: sort cannot be pushed down. // 排序包含右侧字段：排序不能下推
  @Test void testCorrelateLeftJoinNoDerive() { // 测试相关左连接无法推导排序的情况，验证优化器在排序包含右侧字段时的行为
    final String sql = "select * from emp e\n" // 定义 SQL 语句：选择所有列，emp 表别名为 e
        + "left join dept d on e.deptno=d.deptno\n" // 与 dept 表（别名 d）左连接，连接条件是 deptno 相等
        + "order by e.ename, d.name"; // 按 e.ename（左侧字段）和 d.name（右侧字段）排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.JOIN_TO_CORRELATE); // 添加连接到相关连接的转换规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testCorrelateLeftJoinNoDerive 方法

  // Order by left field(s): push down sort to left input. // 按左侧字段排序：将排序下推到左侧输入
  @Test void testCorrelateSemiJoinDeriveLeft() { // 测试相关半连接从左侧推导排序，验证优化器能否将排序下推到相关半连接的左侧
    final String sql = "select * from dept d\n" // 定义 SQL 语句：选择所有列，dept 表别名为 d
        + "where exists (select 1 from emp e where e.deptno=d.deptno)\n" // 使用 EXISTS 子查询，检查 emp 表中是否存在 deptno 相等的记录
        + "order by d.name"; // 按 d.name（左侧表字段）排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.JOIN_TO_CORRELATE); // 添加连接到相关连接的转换规则
      p.addRule(CoreRules.JOIN_TO_SEMI_JOIN); // 添加连接到半连接的转换规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则
    }).withExpand(true).check(); // 启用子查询展开并执行 SQL 测试，检查结果是否符合预期
  } // 结束 testCorrelateSemiJoinDeriveLeft 方法

  // test if "order by mgr desc nulls last" can be pushed through the projection ("select mgr"). // 测试 "order by mgr desc nulls last" 是否能通过投影下推
  @Test void testSortProject() { // 测试排序通过投影下推，验证优化器能否将排序下推到投影操作之前
    final String sql = "select mgr from sales.emp order by mgr desc nulls last"; // 定义 SQL 语句：从 sales.emp 表中选择 mgr 列，按 mgr 降序排序（null 最后）
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProject 方法

  // test that Sort cannot push through projection because of non-trival call // 测试排序不能通过投影下推，因为存在非平凡调用
  // (e.g. RexCall(sal * -1)). In this example, the reason is that "sal * -1" // （例如 RexCall(sal * -1)）。在这个例子中，原因是 "sal * -1"
  // creates opposite ordering if Sort is pushed down. // 如果排序下推会产生相反的排序
  @Test void testSortProjectOnRexCall() { // 测试排序在包含表达式的投影上的行为，验证优化器能否正确处理非平凡表达式
    final String sql = "select ename, sal * -1 as sal, mgr from\n" // 定义 SQL 语句：从 sales.emp 表中选择 ename、sal * -1（别名为 sal）和 mgr
        + "sales.emp order by ename desc, sal desc, mgr desc nulls last"; // 按 ename 降序、sal 降序、mgr 降序排序（null 最后）
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectOnRexCall 方法

  // test that Sort can push through projection when cast is monotonic. // 测试当类型转换是单调时，排序可以通过投影下推
  @Test void testSortProjectWhenCastLeadingToMonotonic() { // 测试排序在单调类型转换上的投影下推
    final String sql = "select deptno from sales.emp order by cast(deptno as float) desc"; // 定义 SQL 语句：从 sales.emp 表中选择 deptno，按 deptno 转换为 float 后降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectWhenCastLeadingToMonotonic 方法

  // test that Sort cannot push through projection when cast is not monotonic. // 测试当类型转换不是单调时，排序不能通过投影下推
  @Test void testSortProjectWhenCastLeadingToNonMonotonic() { // 测试排序在非单调类型转换上的投影下推
    final String sql = "select deptno from sales.emp order by cast(deptno as varchar) desc"; // 定义 SQL 语句：从 sales.emp 表中选择 deptno，按 deptno 转换为 varchar 后降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectWhenCastLeadingToNonMonotonic 方法

  // No sort on left join input. // 左连接输入不需要排序
  @Test void testSortProjectDeriveWhenCastLeadingToMonotonic() { // 测试单调类型转换的投影在连接中的排序推导
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, cast(job as varchar) as job, max_sal + 1 from\n" // 从子查询中选择 ename、job 转换为 varchar、max_sal + 1
        + "(select ename, job, max(sal) as max_sal from sales.emp group by ename, job) t) r\n" // 子查询：从 sales.emp 表中选择 ename、job 和最大 sal，按 ename 和 job 分组，结果别名为 t
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDeriveWhenCastLeadingToMonotonic 方法

  // need sort on left join input. // 左连接输入需要排序
  @Test void testSortProjectDeriveOnRexCall() { // 测试包含表达式的投影在连接中的排序推导
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, sal * -1 as sal, max_job from\n" // 从子查询中选择 ename、sal * -1（别名为 sal）和 max_job
        + "(select ename, sal, max(job) as max_job from sales.emp group by ename, sal) t) r\n" // 子查询：从 sales.emp 表中选择 ename、sal 和最大 job，按 ename 和 sal 分组，结果别名为 t
        + "join sales.bonus s on r.sal=s.sal and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 sal 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDeriveOnRexCall 方法

  // need sort on left join input. // 左连接输入需要排序
  @Test void testSortProjectDeriveWhenCastLeadingToNonMonotonic() { // 测试非单调类型转换的投影在连接中的排序推导
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, cast(job as numeric) as job, max_sal + 1 from\n" // 从子查询中选择 ename、job 转换为 numeric、max_sal + 1
        + "(select ename, job, max(sal) as max_sal from sales.emp group by ename, job) t) r\n" // 子查询：从 sales.emp 表中选择 ename、job 和最大 sal，按 ename 和 job 分组，结果别名为 t
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDeriveWhenCastLeadingToNonMonotonic 方法

  // no Sort need for left join input. // 左连接输入不需要排序
  @Test void testSortProjectDerive3() { // 测试投影排序推导的第三种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, cast(job as varchar) as job, sal + 1 from\n" // 从子查询中选择 ename、job 转换为 varchar、sal + 1
        + "(select ename, job, sal from sales.emp limit 100) t) r\n" // 子查询：从 sales.emp 表中选择 ename、job 和 sal，限制 100 行，结果别名为 t
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDerive3 方法

  // need Sort on left join input. // 左连接输入需要排序
  @Test void testSortProjectDerive4() { // 测试投影排序推导的第四种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, cast(job as bigint) as job, sal + 1 from\n" // 从子查询中选择 ename、job 转换为 bigint、sal + 1
        + "(select ename, job, sal from sales.emp limit 100) t) r\n" // 子查询：从 sales.emp 表中选择 ename、job 和 sal，限制 100 行，结果别名为 t
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDerive4 方法

  // test if top projection can enforce sort when inner sort cannot produce satisfying ordering. // 测试当内部排序无法产生满足要求的排序时，顶部投影是否能强制排序
  @Test void testSortProjectDerive5() { // 测试投影排序推导的第五种情况
    final String sql = "select ename, empno*-1, job from\n" // 定义 SQL 语句：从子查询中选择 ename、empno * -1 和 job
        + "(select * from sales.emp order by ename, empno, job limit 10) order by ename, job"; // 子查询：从 sales.emp 表中选择所有列，按 ename、empno、job 排序，限制 10 行；然后按 ename、job 排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDerive5 方法

  @Test void testSortProjectDerive() { // 测试投影排序推导的基本情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, max_sal + 1 from\n" // 从子查询中选择 ename、job 和 max_sal + 1
        + "(select ename, job, max(sal) as max_sal from sales.emp group by ename, job) t) r\n" // 子查询：从 sales.emp 表中选择 ename、job 和最大 sal，按 ename 和 job 分组，结果别名为 t
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDerive 方法

  // need Sort on projection. // 投影需要排序
  @Test void testSortProjectDerive2() { // 测试投影排序推导的第二种情况，包含 DISTINCT
    final String sql = "select distinct ename, sal*-2, mgr\n" // 定义 SQL 语句：从子查询中选择不同的 ename、sal * -2 和 mgr
        + "from (select ename, mgr, sal from sales.emp order by ename, mgr, sal limit 100) t"; // 子查询：从 sales.emp 表中选择 ename、mgr 和 sal，按 ename、mgr、sal 排序，限制 100 行，结果别名为 t
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDerive2 方法

  @Test void testSortProjectDerive6() { // 测试投影排序推导的第六种情况
    final String sql = "select comm, deptno, slacker from\n" // 定义 SQL 语句：从子查询中选择 comm、deptno 和 slacker
        + "(select * from sales.emp order by comm, deptno, slacker limit 10) t\n" // 子查询：从 sales.emp 表中选择所有列，按 comm、deptno、slacker 排序，限制 10 行，结果别名为 t
        + "order by comm, slacker"; // 按 comm、slacker 排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortProjectDerive6 方法

  // test traits push through filter. // 测试排序特征通过过滤器的下推
  @Test void testSortFilter() { // 测试排序通过过滤器下推
    final String sql = "select ename, job, mgr, max_sal from\n" // 定义 SQL 语句：从子查询中选择 ename、job、mgr 和 max_sal
        + "(select ename, job, mgr, max(sal) as max_sal from sales.emp group by ename, job, mgr) as t\n" // 子查询：从 sales.emp 表中选择 ename、job、mgr 和最大 sal，按 ename、job、mgr 分组，结果别名为 t
        + "where max_sal > 1000\n" // 过滤条件：max_sal 大于 1000
        + "order by mgr desc, ename"; // 按 mgr 降序、ename 升序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortFilter 方法

  // test traits derivation in filter. // 测试过滤器中的排序特征推导
  @Test void testSortFilterDerive() { // 测试过滤器中的排序特征推导
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, max_sal from\n" // 从子查询中选择 ename、job 和 max_sal
        + "(select ename, job, max(sal) as max_sal from sales.emp group by ename, job) t where job > 1000) r\n" // 子查询：从 sales.emp 表中选择 ename、job 和最大 sal，按 ename 和 job 分组，然后过滤 job > 1000，结果别名为 r
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortFilterDerive 方法

  // Not push down sort for hash join in full outer join case. // 全外连接情况下，哈希连接不将排序下推
  @Test void testHashJoinFullOuterJoinNotPushDownSort() { // 测试哈希全外连接不将排序下推
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "sales.emp r full outer join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // sales.emp 表（别名 r）与 sales.bonus 表（别名 s）全外连接，连接条件是 ename 和 job 都相等
        + "order by r.job desc nulls last, r.ename nulls first"; // 按 r.job 降序排序（null 最后），然后按 r.ename 升序排序（null 最前）
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinFullOuterJoinNotPushDownSort 方法

  // Push down sort to left input. // 将排序下推到左侧输入
  @Test void testHashJoinLeftOuterJoinPushDownSort() { // 测试哈希左外连接将排序下推到左侧输入
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select contactno, email from customer.contact_peek) r left outer join\n" // 从 customer.contact_peek 表中选择 contactno 和 email，结果别名为 r，作为左侧
        + "(select acctno, type from customer.account) s\n" // 从 customer.account 表中选择 acctno 和 type，结果别名为 s，作为右侧
        + "on r.contactno=s.acctno and r.email=s.type\n" // 连接条件：contactno = acctno 且 email = type
        + "order by r.contactno desc, r.email desc"; // 按 r.contactno 降序、r.email 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinLeftOuterJoinPushDownSort 方法

  // Push down sort to left input. // 将排序下推到左侧输入
  @Test void testHashJoinLeftOuterJoinPushDownSort2() { // 测试哈希左外连接将排序下推到左侧输入的第二种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "customer.contact_peek r left outer join\n" // customer.contact_peek 表（别名 r）作为左侧
        + "customer.account s\n" // customer.account 表（别名 s）作为右侧
        + "on r.contactno=s.acctno and r.email=s.type\n" // 连接条件：contactno = acctno 且 email = type
        + "order by r.fname desc"; // 按 r.fname 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinLeftOuterJoinPushDownSort2 方法

  // Push down sort to left input. // 将排序下推到左侧输入
  @Test void testHashJoinInnerJoinPushDownSort() { // 测试哈希内连接将排序下推到左侧输入
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select contactno, email from customer.contact_peek) r inner join\n" // 从 customer.contact_peek 表中选择 contactno 和 email，结果别名为 r，作为左侧
        + "(select acctno, type from customer.account) s\n" // 从 customer.account 表中选择 acctno 和 type，结果别名为 s，作为右侧
        + "on r.contactno=s.acctno and r.email=s.type\n" // 连接条件：contactno = acctno 且 email = type
        + "order by r.contactno desc, r.email desc"; // 按 r.contactno 降序、r.email 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinInnerJoinPushDownSort 方法

  // do not push down sort. // 不将排序下推
  @Test void testHashJoinRightOuterJoinPushDownSort() { // 测试哈希右外连接不将排序下推
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select contactno, email from customer.contact_peek) r right outer join\n" // 从 customer.contact_peek 表中选择 contactno 和 email，结果别名为 r，作为左侧
        + "(select acctno, type from customer.account) s\n" // 从 customer.account 表中选择 acctno 和 type，结果别名为 s，作为右侧
        + "on r.contactno=s.acctno and r.email=s.type\n" // 连接条件：contactno = acctno 且 email = type
        + "order by s.acctno desc, s.type desc"; // 按 s.acctno 降序、s.type 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinRightOuterJoinPushDownSort 方法

  // push sort to left input // 将排序下推到左侧输入
  @Test void testNestedLoopJoinLeftOuterJoinPushDownSort() { // 测试嵌套循环左外连接将排序下推到左侧输入
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + " customer.contact_peek r left outer join\n" // customer.contact_peek 表（别名 r）作为左侧
        + "customer.account s\n" // customer.account 表（别名 s）作为右侧
        + "on r.contactno>s.acctno and r.email<s.type\n" // 连接条件：contactno > acctno 且 email < type
        + "order by r.contactno desc, r.email desc"; // 按 r.contactno 降序、r.email 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testNestedLoopJoinLeftOuterJoinPushDownSort 方法

  // push sort to left input // 将排序下推到左侧输入
  @Test void testNestedLoopJoinLeftOuterJoinPushDownSort2() { // 测试嵌套循环左外连接将排序下推到左侧输入的第二种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + " customer.contact_peek r left outer join\n" // customer.contact_peek 表（别名 r）作为左侧
        + "customer.account s\n" // customer.account 表（别名 s）作为右侧
        + "on r.contactno>s.acctno and r.email<s.type\n" // 连接条件：contactno > acctno 且 email < type
        + "order by r.fname desc"; // 按 r.fname 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testNestedLoopJoinLeftOuterJoinPushDownSort2 方法

  // do not push sort to left input cause sort keys are on right input. // 不将排序下推到左侧输入，因为排序键在右侧输入上
  @Test void testNestedLoopJoinLeftOuterJoinSortKeyOnRightInput() { // 测试嵌套循环左外连接排序键在右侧输入的情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + " customer.contact_peek r left outer join\n" // customer.contact_peek 表（别名 r）作为左侧
        + "customer.account s\n" // customer.account 表（别名 s）作为右侧
        + "on r.contactno>s.acctno and r.email<s.type\n" // 连接条件：contactno > acctno 且 email < type
        + "order by s.acctno desc, s.type desc"; // 按 s.acctno 降序、s.type 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testNestedLoopJoinLeftOuterJoinSortKeyOnRightInput 方法

  // do not push down sort to right input because traits propagation does not work // 不将排序下推到右侧输入，因为特征传播不适用于
  // for right/full outer join. // 右外连接/全外连接
  @Test void testNestedLoopJoinRightOuterJoinSortPushDown() { // 测试嵌套循环右外连接的排序下推
    final String sql = "select r.contactno, r.email, s.acctno, s.type from\n" // 定义 SQL 语句：选择 r.contactno、r.email、s.acctno 和 s.type
        + " customer.contact_peek r right outer join\n" // customer.contact_peek 表（别名 r）作为左侧
        + "customer.account s\n" // customer.account 表（别名 s）作为右侧
        + "on r.contactno>s.acctno and r.email<s.type\n" // 连接条件：contactno > acctno 且 email < type
        + "order by s.acctno desc, s.type desc"; // 按 s.acctno 降序、s.type 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testNestedLoopJoinRightOuterJoinSortPushDown 方法

  // Collation can be derived from left input so that top Sort is removed. // 排序可以从左侧输入推导，从而移除顶部排序
  @Test void testHashJoinTraitDerivation() { // 测试哈希连接的排序特征推导
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr from sales.emp order by ename desc, job desc, mgr limit 10) r\n" // 从 sales.emp 表中选择 ename、job 和 mgr，按 ename 降序、job 降序、mgr 排序，限制 10 行，结果别名为 r
        + "join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 ename 和 job 都相等
        + "order by r.ename desc, r.job desc"; // 按 r.ename 降序、r.job 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinTraitDerivation 方法

  // Collation can be derived from left input so that top Sort is removed. // 排序可以从左侧输入推导，从而移除顶部排序
  @Test void testHashJoinTraitDerivation2() { // 测试哈希连接的排序特征推导的第二种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr from sales.emp order by mgr desc limit 10) r\n" // 从 sales.emp 表中选择 ename、job 和 mgr，按 mgr 降序排序，限制 10 行，结果别名为 r
        + "join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 ename 和 job 都相等
        + "order by r.mgr desc"; // 按 r.mgr 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinTraitDerivation2 方法

  // Collation derived from left input is not what the top Sort needs. // 从左侧输入推导的排序不是顶部排序所需要的
  @Test void testHashJoinTraitDerivationNegativeCase() { // 测试哈希连接的排序特征推导的负例
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr from sales.emp order by mgr desc limit 10) r\n" // 从 sales.emp 表中选择 ename、job 和 mgr，按 mgr 降序排序，限制 10 行，结果别名为 r
        + "join sales.bonus s on r.ename=s.ename and r.job=s.job\n" // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 ename 和 job 都相等
        + "order by r.mgr"; // 按 r.mgr 升序排序（与子查询中的降序相反）
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则，强制使用哈希连接
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testHashJoinTraitDerivationNegativeCase 方法

  // Collation can be derived from left input so that top Sort is removed. // 排序可以从左侧输入推导，从而移除顶部排序
  @Test void testNestedLoopJoinTraitDerivation() { // 测试嵌套循环连接的排序特征推导
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr from sales.emp order by ename desc, job desc, mgr limit 10) r\n" // 从 sales.emp 表中选择 ename、job 和 mgr，按 ename 降序、job 降序、mgr 排序，限制 10 行，结果别名为 r
        + "join sales.bonus s on r.ename>s.ename and r.job<s.job\n" // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 ename > ename 且 job < job
        + "order by r.ename desc, r.job desc"; // 按 r.ename 降序、r.job 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testNestedLoopJoinTraitDerivation 方法

  // Collation can be derived from left input so that top Sort is removed. // 排序可以从左侧输入推导，从而移除顶部排序
  @Test void testNestedLoopJoinTraitDerivation2() { // 测试嵌套循环连接的排序特征推导的第二种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr from sales.emp order by mgr limit 10) r\n" // 从 sales.emp 表中选择 ename、job 和 mgr，按 mgr 升序排序，限制 10 行，结果别名为 r
        + "join sales.bonus s on r.ename>s.ename and r.job<s.job\n" // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 ename > ename 且 job < job
        + "order by r.mgr"; // 按 r.mgr 升序排序

    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testNestedLoopJoinTraitDerivation2 方法

  // Collation derived from left input is not what the top Sort needs. // 从左侧输入推导的排序不是顶部排序所需要的
  @Test void testNestedLoopJoinTraitDerivationNegativeCase() { // 测试嵌套循环连接的排序特征推导的负例
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr from sales.emp order by mgr limit 10) r\n" // 从 sales.emp 表中选择 ename、job 和 mgr，按 mgr 升序排序，限制 10 行，结果别名为 r
        + "join sales.bonus s on r.ename>s.ename and r.job<s.job\n" // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 ename > ename 且 job < job
        + "order by r.mgr desc"; // 按 r.mgr 降序排序（与子查询中的升序相反）
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testNestedLoopJoinTraitDerivationNegativeCase 方法

  // test if "order by mgr desc nulls last" can be pushed through the calc ("select mgr"). // 测试 "order by mgr desc nulls last" 是否能通过 Calc 节点下推
  @Test void testSortCalc() { // 测试排序通过 Calc 节点下推
    final String sql = "select mgr from sales.emp order by mgr desc nulls last"; // 定义 SQL 语句：从 sales.emp 表中选择 mgr 列，按 mgr 降序排序（null 最后）
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalc 方法

  // test that Sort cannot push through calc because of non-trival call // 测试排序不能通过 Calc 节点下推，因为存在非平凡调用
  // (e.g. RexCall(sal * -1)). In this example, the reason is that "sal * -1" // （例如 RexCall(sal * -1)）。在这个例子中，原因是 "sal * -1"
  // creates opposite ordering if Sort is pushed down. // 如果排序下推会产生相反的排序
  @Test void testSortCalcOnRexCall() { // 测试排序在包含表达式的 Calc 节点上的行为
    final String sql = "select ename, sal * -1 as sal, mgr from\n" // 定义 SQL 语句：从 sales.emp 表中选择 ename、sal * -1（别名为 sal）和 mgr
        + "sales.emp order by ename desc, sal desc, mgr desc nulls last"; // 按 ename 降序、sal 降序、mgr 降序排序（null 最后）
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalcOnRexCall 方法

  // test that Sort can push through calc when cast is monotonic. // 测试当类型转换是单调时，排序可以通过 Calc 节点下推
  @Test void testSortCalcWhenCastLeadingToMonotonic() { // 测试排序在单调类型转换的 Calc 节点上的下推
    final String sql = "select cast(deptno as float) from sales.emp order by deptno desc"; // 定义 SQL 语句：从 sales.emp 表中选择 deptno 转换为 float，按 deptno 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalcWhenCastLeadingToMonotonic 方法

  // test that Sort cannot push through calc when cast is not monotonic. // 测试当类型转换不是单调时，排序不能通过 Calc 节点下推
  @Test void testSortCalcWhenCastLeadingToNonMonotonic() { // 测试排序在非单调类型转换的 Calc 节点上的下推
    final String sql = "select deptno from sales.emp order by cast(deptno as varchar) desc"; // 定义 SQL 语句：从 sales.emp 表中选择 deptno，按 deptno 转换为 varchar 后降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalcWhenCastLeadingToNonMonotonic 方法

  // test traits push through calc with filter. // 测试排序特征通过带过滤器的 Calc 节点下推
  @Test void testSortCalcWithFilter() { // 测试排序通过带过滤器的 Calc 节点下推
    final String sql = "select ename, job, mgr, max_sal from\n" // 定义 SQL 语句：从子查询中选择 ename、job、mgr 和 max_sal
        + "(select ename, job, mgr, max(sal) as max_sal from sales.emp group by ename, job, mgr) as t\n" // 子查询：从 sales.emp 表中选择 ename、job、mgr 和最大 sal，按 ename、job、mgr 分组，结果别名为 t
        + "where max_sal > 1000\n" // 过滤条件：max_sal 大于 1000
        + "order by mgr desc, ename"; // 按 mgr 降序、ename 升序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(CoreRules.FILTER_TO_CALC); // 添加过滤器到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
      p.removeRule(EnumerableRules.ENUMERABLE_FILTER_RULE); // 移除可枚举过滤器规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalcWithFilter 方法

  // Do not need Sort for calc. // Calc 节点不需要排序
  @Test void testSortCalcDerive1() { // 测试 Calc 节点的排序推导的第一种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, max_sal + 1 from\n" // 从子查询中选择 ename、job 和 max_sal + 1
        + "(select ename, job, max(sal) as max_sal from sales.emp " // 子查询：从 sales.emp 表中选择 ename、job 和最大 sal
        + "group by ename, job) t) r\n" // 按 ename 和 job 分组，结果别名为 t
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalcDerive1 方法

  // Need Sort for calc. // Calc 节点需要排序
  @Test void testSortCalcDerive2() { // 测试 Calc 节点的排序推导的第二种情况，包含 DISTINCT
    final String sql = "select distinct ename, sal*-2, mgr\n" // 定义 SQL 语句：从子查询中选择不同的 ename、sal * -2 和 mgr
        + "from (select ename, mgr, sal from sales.emp order by ename, mgr, sal limit 100) t"; // 子查询：从 sales.emp 表中选择 ename、mgr 和 sal，按 ename、mgr、sal 排序，限制 100 行，结果别名为 t
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalcDerive2 方法

  // Do not need Sort for left join input. // 左连接输入不需要排序
  @Test void testSortCalcDerive3() { // 测试 Calc 节点的排序推导的第三种情况
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, cast(job as varchar) as job, sal + 1 from\n" // 从子查询中选择 ename、job 转换为 varchar、sal + 1
        + "(select ename, job, sal from sales.emp limit 100) t) r\n" // 子查询：从 sales.emp 表中选择 ename、job 和 sal，限制 100 行，结果别名为 t
        + "join sales.bonus s on r.job=s.job and r.ename=s.ename"; // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 job 和 ename 都相等
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.addRule(CoreRules.PROJECT_TO_CALC); // 添加投影到 Calc 的转换规则
      p.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 添加可枚举 Calc 规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.removeRule(EnumerableRules.ENUMERABLE_PROJECT_RULE); // 移除可枚举投影规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testSortCalcDerive3 方法

  // push sort to left input // 将排序下推到左侧输入
  @Test void testBatchNestedLoopJoinLeftOuterJoinPushDownSort() { // 测试批量嵌套循环左外连接将排序下推到左侧输入
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + " customer.contact_peek r left outer join\n" // customer.contact_peek 表（别名 r）作为左侧
        + "customer.account s\n" // customer.account 表（别名 s）作为右侧
        + "on r.contactno>s.acctno and r.email<s.type\n" // 连接条件：contactno > acctno 且 email < type
        + "order by r.contactno desc, r.email desc"; // 按 r.contactno 降序、r.email 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加可枚举批量嵌套循环连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testBatchNestedLoopJoinLeftOuterJoinPushDownSort 方法

  // Collation can be derived from left input so that top Sort is removed. // 排序可以从左侧输入推导，从而移除顶部排序
  @Test void testBatchNestedLoopJoinTraitDerivation() { // 测试批量嵌套循环连接的排序特征推导
    final String sql = "select * from\n" // 定义 SQL 语句：选择所有列
        + "(select ename, job, mgr from sales.emp order by ename desc, job desc, mgr limit 10) r\n" // 从 sales.emp 表中选择 ename、job 和 mgr，按 ename 降序、job 降序、mgr 排序，限制 10 行，结果别名为 r
        + "join sales.bonus s on r.ename>s.ename and r.job<s.job\n" // 将结果 r 与 sales.bonus 表 s 连接，连接条件是 ename > ename 且 job < job
        + "order by r.ename desc, r.job desc"; // 按 r.ename 降序、r.job 降序排序
    sql(sql, p -> { // 配置优化器
      initPlanner(p); // 初始化优化器，添加默认规则和配置
      p.removeRule(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE); // 移除可枚举合并连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 移除可枚举连接规则
      p.removeRule(EnumerableRules.ENUMERABLE_SORT_RULE); // 移除可枚举排序规则，强制使用自顶向下优化
      p.addRule(EnumerableRules.ENUMERABLE_BATCH_NESTED_LOOP_JOIN_RULE); // 添加可枚举批量嵌套循环连接规则
    }).check(); // 执行 SQL 测试并检查结果是否符合预期
  } // 结束 testBatchNestedLoopJoinTraitDerivation 方法

  void initPlanner(VolcanoPlanner planner) { // 初始化火山优化器的方法，配置优化器的规则和特征定义
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加约定特征定义，用于定义关系代数节点的调用约定（如逻辑、物理等）
    planner.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加排序特征定义，用于定义关系代数节点的排序属性

    RelOptUtil.registerDefaultRules(planner, false, false); // 注册默认的优化规则到优化器，false 参数表示不注册某些特定规则

    // Remove to Keep deterministic join order. // 移除规则以保持确定性的连接顺序
    planner.removeRule(CoreRules.JOIN_COMMUTE); // 移除连接交换规则，防止左右连接输入的顺序被交换
    planner.removeRule(JoinPushThroughJoinRule.LEFT); // 移除左侧连接下推规则，防止连接被下推到另一个连接的左侧
    planner.removeRule(JoinPushThroughJoinRule.RIGHT); // 移除右侧连接下推规则，防止连接被下推到另一个连接的右侧

    // Always use sorted agg. // 始终使用排序聚合
    planner.addRule(EnumerableRules.ENUMERABLE_SORTED_AGGREGATE_RULE); // 添加可枚举排序聚合规则，强制使用排序聚合实现
    planner.removeRule(EnumerableRules.ENUMERABLE_AGGREGATE_RULE); // 移除可枚举聚合规则，防止使用哈希聚合实现

    // pushing down sort should be handled by top-down optimization. // 排序下推应该由自顶向下优化处理
    planner.removeRule(CoreRules.SORT_PROJECT_TRANSPOSE); // 移除排序投影转置规则，防止通过传统规则下推排序

    // Sort will only be pushed down by traits propagation. // 排序只能通过特征传播下推
    planner.removeRule(CoreRules.SORT_JOIN_TRANSPOSE); // 移除排序连接转置规则，防止通过传统规则下推排序
    planner.removeRule(CoreRules.SORT_JOIN_COPY); // 移除排序连接复制规则，防止通过传统规则下推排序
  } // 结束 initPlanner 方法
} // 结束 TopDownOptTest 类
