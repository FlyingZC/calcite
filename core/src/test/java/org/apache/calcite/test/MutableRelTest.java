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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能
// 本测试类专门用于测试 MutableRel（可变关系表达式）及其子类的功能
package org.apache.calcite.test;

// 导入关系优化规则相关类，用于定义转换规则
import org.apache.calcite.plan.RelOptRule;
// 导入关系工具类，提供关系节点的字符串表示、比较等工具方法
import org.apache.calcite.plan.RelOptUtil;
// 导入 HEP（启发式）规划器，用于基于规则的关系转换
import org.apache.calcite.plan.hep.HepPlanner;
// 导入 HEP 程序，用于定义优化规则的执行顺序和方式
import org.apache.calcite.plan.hep.HepProgram;
// 导入 HEP 程序构建器，用于构建优化程序
import org.apache.calcite.plan.hep.HepProgramBuilder;
// 导入关系节点基类，表示关系代数中的操作（如扫描、过滤、投影等）
import org.apache.calcite.rel.RelNode;
// 导入可变关系表达式基类，MutableRel 是 RelNode 的可变版本，便于修改和转换
import org.apache.calcite.rel.mutable.MutableRel;
// 导入可变关系工具类，提供 RelNode 和 MutableRel 之间的转换方法
import org.apache.calcite.rel.mutable.MutableRels;
// 导入可变扫描节点，表示对表或视图的扫描操作
import org.apache.calcite.rel.mutable.MutableScan;
// 导入核心规则集合，包含常用的关系转换规则（如过滤器转计算、投影转窗口等）
import org.apache.calcite.rel.rules.CoreRules;
// 导入关系数据类型，描述关系表达式的行类型（字段名、类型等）
import org.apache.calcite.rel.type.RelDataType;
// 导入框架配置，用于配置 Calcite 的各种参数和组件
import org.apache.calcite.tools.FrameworkConfig;
// 导入关系构建器，提供流式 API 用于构建关系表达式树
import org.apache.calcite.tools.RelBuilder;

// 导入 Google Guava 的不可变列表，提供线程安全的列表实现
import com.google.common.collect.ImmutableList;

// 导入 Hamcrest 断言工具，用于编写可读性强的测试断言
import org.hamcrest.MatcherAssert;
// 导入 JUnit 5 的 Test 注解，标记测试方法
import org.junit.jupiter.api.Test;

// 导入 Java 标准库的 List 接口，用于存储有序的对象集合
import java.util.List;

// 静态导入关系工具类的 equal 方法，用于比较关系类型是否相等
import static org.apache.calcite.plan.RelOptUtil.equal;
// 静态导入 Litmus.IGNORE，表示忽略某些检查
import static org.apache.calcite.util.Litmus.IGNORE;

// 静态导入 Hamcrest 的 equalTo 匹配器，用于断言对象相等
import static org.hamcrest.CoreMatchers.equalTo;
// 静态导入 Hamcrest 的 is 匹配器，用于断言条件成立
import static org.hamcrest.CoreMatchers.is;
// 静态导入 Hamcrest 的 not 匹配器，用于断言条件不成立
import static org.hamcrest.CoreMatchers.not;
// 静态导入 Hamcrest 的 assertThat 方法，用于执行断言
import static org.hamcrest.MatcherAssert.assertThat;
// 静态导入 JUnit 5 的 assertSame 方法，用于断言两个对象引用相同
import static org.junit.jupiter.api.Assertions.assertSame;
// 静态导入 JUnit 5 的 assertTrue 方法，用于断言条件为真
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MutableRel} sub-classes.
 * 测试 MutableRel（可变关系表达式）及其子类的功能
 * 
 * MutableRel 是 Calcite 中关系表达式的可变版本，与不可变的 RelNode 相比：
 * 1. MutableRel 允许修改其结构（如添加、删除、替换子节点）
 * 2. MutableRel 便于进行关系转换和优化操作
 * 3. MutableRel 可以在转换过程中保持父子关系
 * 
 * 本测试类主要验证：
 * - 各种关系操作（Aggregate、Filter、Project、Sort 等）到 MutableRel 的转换是否正确
 * - 从 MutableRel 转换回 RelNode 后是否保持原有的语义和结构
 * - MutableRel 的输入更新、父子关系维护等功能是否正常
 * - MutableScan 的等价性判断是否正确
 * 
 * 测试方法命名规则：testConvert{RelType} 表示测试某种关系类型的转换
 */
class MutableRelTest {

  // 测试聚合（Aggregate）操作的 MutableRel 转换
  // 验证包含 GROUP BY 和聚合函数（SUM）的 SQL 语句能正确转换为 MutableRel 并转换回来
  @Test void testConvertAggregate() {
    checkConvertMutableRel(
        "Aggregate", // 期望在转换后的 MutableRel 字符串中包含 "Aggregate" 关键字
        "select empno, sum(sal) from emp group by empno"); // 测试 SQL：按员工号分组并计算薪水总和
  }

  // 测试过滤（Filter）操作的 MutableRel 转换
  // 验证包含 WHERE 条件的 SQL 语句能正确转换为 MutableRel 并转换回来
  @Test void testConvertFilter() {
    checkConvertMutableRel(
        "Filter", // 期望在转换后的 MutableRel 字符串中包含 "Filter" 关键字
        "select * from emp where ename = 'DUMMY'"); // 测试 SQL：过滤员工名为 'DUMMY' 的记录
  }

  // 测试投影（Project）操作的 MutableRel 转换
  // 验证只选择特定列的 SQL 语句能正确转换为 MutableRel 并转换回来
  @Test void testConvertProject() {
    checkConvertMutableRel(
        "Project", // 期望在转换后的 MutableRel 字符串中包含 "Project" 关键字
        "select ename from emp"); // 测试 SQL：只选择员工姓名列
  }

  // 测试排序（Sort）操作的 MutableRel 转换
  // 验证包含 ORDER BY 的 SQL 语句能正确转换为 MutableRel 并转换回来
  @Test void testConvertSort() {
    checkConvertMutableRel(
        "Sort", // 期望在转换后的 MutableRel 字符串中包含 "Sort" 关键字
        "select * from emp order by ename"); // 测试 SQL：按员工姓名排序
  }

  // 测试计算（Calc）操作的 MutableRel 转换
  // Calc 是 Filter 和 Project 的组合，用于同时进行过滤和投影
  // 使用 CoreRules.FILTER_TO_CALC 规则将 Filter 转换为 Calc
  @Test void testConvertCalc() {
    checkConvertMutableRel(
        "Calc", // 期望在转换后的 MutableRel 字符串中包含 "Calc" 关键字
        "select * from emp where ename = 'DUMMY'", // 测试 SQL：过滤员工名为 'DUMMY' 的记录
        false, // 不进行去相关化（decorrelate）处理
        ImmutableList.of(CoreRules.FILTER_TO_CALC)); // 应用规则：将 Filter 转换为 Calc
  }

  // 测试窗口（Window）操作的 MutableRel 转换
  // Window 用于执行窗口函数（如分析函数、聚合函数 over 子句）
  // 使用 PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW 规则将包含窗口函数的投影转换为窗口操作
  @Test void testConvertWindow() {
    checkConvertMutableRel(
        "Window", // 期望在转换后的 MutableRel 字符串中包含 "Window" 关键字
        "select sal, avg(sal) over (partition by deptno) from emp", // 测试 SQL：计算每个部门的平均薪水
        false, // 不进行去相关化处理
        ImmutableList.of(CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW)); // 应用规则：将投影转换为逻辑投影和窗口
  }

  // 测试集合收集（Collect）操作的 MutableRel 转换
  // Collect 用于将子查询结果集收集为多集（multiset）类型
  @Test void testConvertCollect() {
    checkConvertMutableRel(
        "Collect", // 期望在转换后的 MutableRel 字符串中包含 "Collect" 关键字
        "select multiset(select deptno from dept) from (values(true))"); // 测试 SQL：将部门编号收集为多集
  }

  // 测试集合展开（Uncollect）操作的 MutableRel 转换
  // Uncollect 是 Collect 的逆操作，将多集展开为多行
  @Test void testConvertUncollect() {
    checkConvertMutableRel(
        "Uncollect", // 期望在转换后的 MutableRel 字符串中包含 "Uncollect" 关键字
        "select * from unnest(multiset[1,2])"); // 测试 SQL：将多集 [1,2] 展开为两行
  }

  // 测试表修改（TableModify）操作的 MutableRel 转换
  // TableModify 用于执行 INSERT、UPDATE、DELETE 等 DML 操作
  @Test void testConvertTableModify() {
    checkConvertMutableRel(
        "TableModify", // 期望在转换后的 MutableRel 字符串中包含 "TableModify" 关键字
        "insert into dept select empno, ename from emp"); // 测试 SQL：将员工数据插入到部门表
  }

  // 测试采样（Sample）操作的 MutableRel 转换
  // Sample 用于从表中随机采样部分数据
  @Test void testConvertSample() {
    checkConvertMutableRel(
        "Sample", // 期望在转换后的 MutableRel 字符串中包含 "Sample" 关键字
        "select * from emp tablesample system(50) where empno > 5"); // 测试 SQL：系统采样 50% 的数据并过滤员工号大于 5 的记录
  }

  // 测试表函数扫描（TableFunctionScan）操作的 MutableRel 转换
  // TableFunctionScan 用于扫描表函数（如 TVF - Table Valued Function）的结果
  @Test void testConvertTableFunctionScan() {
    checkConvertMutableRel(
        "TableFunctionScan", // 期望在转换后的 MutableRel 字符串中包含 "TableFunctionScan" 关键字
        "select * from table(ramp(3))"); // 测试 SQL：扫描 ramp(3) 表函数的结果（生成 0,1,2 序列）
  }

  // 测试值（Values）操作的 MutableRel 转换
  // Values 用于表示字面值集合，常用于构造临时数据
  @Test void testConvertValues() {
    checkConvertMutableRel(
        "Values", // 期望在转换后的 MutableRel 字符串中包含 "Values" 关键字
        "select * from (values (1, 2))"); // 测试 SQL：从字面值 (1,2) 中选择数据
  }

  // 测试连接（Join）操作的 MutableRel 转换
  // Join 用于连接两个表或关系表达式
  @Test void testConvertJoin() {
    checkConvertMutableRel(
        "Join", // 期望在转换后的 MutableRel 字符串中包含 "Join" 关键字
        "select * from emp join dept using (deptno)"); // 测试 SQL：使用部门号连接员工表和部门表
  }

  // 测试半连接（SemiJoin）操作的 MutableRel 转换
  // SemiJoin 是一种特殊的连接，只返回左表中满足连接条件的行，相当于 EXISTS 子查询
  // 应用多个规则将 EXISTS 子查询转换为半连接
  @Test void testConvertSemiJoin() {
    // 定义测试 SQL：查询部门表中存在薪水大于 100 的员工的部门
    final String sql = "select * from dept where exists (\n"
        + "  select * from emp\n"
        + "  where emp.deptno = dept.deptno\n"
        + "  and emp.sal > 100)";
    checkConvertMutableRel(
        "Join", // 期望在转换后的 MutableRel 字符串中包含 "Join" 关键字（半连接也是一种连接）
        sql, // 测试 SQL
        true, // 进行去相关化处理，将相关子查询转换为独立查询
        ImmutableList.of(
            CoreRules.FILTER_PROJECT_TRANSPOSE, // 规则1：过滤器和投影转置
            CoreRules.FILTER_INTO_JOIN, // 规则2：将过滤器推入连接
            CoreRules.PROJECT_MERGE, // 规则3：合并投影
            CoreRules.PROJECT_TO_SEMI_JOIN)); // 规则4：将投影转换为半连接
  }

  // 测试相关（Correlate）操作的 MutableRel 转换
  // Correlate 用于处理相关子查询，允许子查询引用外层查询的列
  @Test void testConvertCorrelate() {
    // 定义测试 SQL：查询部门表中存在薪水大于 100 的员工的部门（使用相关子查询）
    final String sql = "select * from dept where exists (\n"
        + "  select * from emp\n"
        + "  where emp.deptno = dept.deptno\n"
        + "  and emp.sal > 100)";
    checkConvertMutableRel("Correlate", sql); // 期望在转换后的 MutableRel 字符串中包含 "Correlate" 关键字
  }

  // 测试联合（Union）操作的 MutableRel 转换
  // Union 用于合并两个查询结果集，自动去重
  @Test void testConvertUnion() {
    checkConvertMutableRel(
        "Union", // 期望在转换后的 MutableRel 字符串中包含 "Union" 关键字
        "select * from emp where deptno = 10"
        + "union select * from emp where ename like 'John%'"); // 测试 SQL：联合两个员工查询结果
  }

  // 测试差集（Minus）操作的 MutableRel 转换
  // Minus 用于返回第一个查询结果中不在第二个查询结果中的行（EXCEPT）
  @Test void testConvertMinus() {
    checkConvertMutableRel(
        "Minus", // 期望在转换后的 MutableRel 字符串中包含 "Minus" 关键字
        "select * from emp where deptno = 10"
        + "except select * from emp where ename like 'John%'"); // 测试 SQL：返回部门号为 10 但姓名不以 'John' 开头的员工
  }

  // 测试交集（Intersect）操作的 MutableRel 转换
  // Intersect 用于返回两个查询结果的交集
  @Test void testConvertIntersect() {
    checkConvertMutableRel(
        "Intersect", // 期望在转换后的 MutableRel 字符串中包含 "Intersect" 关键字
        "select * from emp where deptno = 10"
        + "intersect select * from emp where ename like 'John%'"); // 测试 SQL：返回既是部门号为 10 又是姓名以 'John' 开头的员工
  }

  // 测试更新 Union 的输入节点
  // 验证 MutableUnion 能够正确更新其子节点，并保持父子关系
  @Test void testUpdateInputOfUnion() {
    // 创建一个 Union 的 MutableRel，包含两个子查询
    MutableRel mutableRel =
        createMutableRel("select sal from emp where deptno = 10"
            + "union select sal from emp where ename like 'John%'");
    // 创建一个新的子查询 MutableRel，将条件从 deptno = 10 改为 deptno = 12
    MutableRel childMutableRel =
        createMutableRel("select sal from emp where deptno = 12");
    // 更新 Union 的第一个输入（索引 0）为新的子查询
    mutableRel.setInput(0, childMutableRel);
    // 将更新后的 MutableRel 转换回 RelNode 并转换为字符串表示
    String actual = RelOptUtil.toString(MutableRels.fromMutable(mutableRel));
    // 定义期望的字符串表示，验证更新是否正确
    String expected = ""
        + "LogicalUnion(all=[false])\n" // 逻辑联合，all=false 表示去重
        + "  LogicalProject(SAL=[$5])\n" // 逻辑投影，选择薪水列（索引 5）
        + "    LogicalFilter(condition=[=($7, 12)])\n" // 逻辑过滤，部门号等于 12（索引 7）
        + "      LogicalTableScan(table=[[CATALOG, SALES, EMP]])\n" // 逻辑表扫描，扫描 EMP 表
        + "  LogicalProject(SAL=[$5])\n" // 逻辑投影，选择薪水列
        + "    LogicalFilter(condition=[LIKE($1, 'John%')])\n" // 逻辑过滤，姓名以 'John' 开头（索引 1）
        + "      LogicalTableScan(table=[[CATALOG, SALES, EMP]])\n"; // 逻辑表扫描，扫描 EMP 表
    // 使用 Hamcrest 断言验证实际结果与期望结果是否匹配（isLinux 处理换行符差异）
    MatcherAssert.assertThat(actual, Matchers.isLinux(expected));
  }

  // 测试 Union 节点的父节点信息
  // 验证 Union 的所有子节点都能正确获取到父节点引用
  @Test void testParentInfoOfUnion() {
    // 创建一个 Union 的 MutableRel
    MutableRel mutableRel =
        createMutableRel("select sal from emp where deptno = 10"
            + "union select sal from emp where ename like 'John%'");
    // 遍历 Union 的所有输入子节点
    for (MutableRel input : mutableRel.getInputs()) {
      // 断言每个子节点的父节点都是 Union 节点本身
      assertSame(input.getParent(), mutableRel);
    }
  }

  // 测试可变表函数扫描节点的相等性
  // 验证相同的 SQL 生成的 MutableTableFunctionScan 节点是相等的
  @Test void testMutableTableFunctionScanEquals() {
    // 定义测试 SQL：扫描 ramp(3) 表函数
    final String sql = "SELECT * FROM TABLE(RAMP(3))";
    // 创建两个相同的 MutableRel
    final MutableRel mutableRel1 = createMutableRel(sql);
    final MutableRel mutableRel2 = createMutableRel(sql);
    // 将第一个 MutableRel 转换为 RelNode 并转换为字符串表示
    final String actual = RelOptUtil.toString(MutableRels.fromMutable(mutableRel1));
    // 定义期望的字符串表示
    final String expected = ""
        + "LogicalProject(I=[$0])\n" // 逻辑投影，选择第一列（I）
        + "  LogicalTableFunctionScan(invocation=[RAMP(3)], rowType=[RecordType(INTEGER I)])\n"; // 逻辑表函数扫描，调用 RAMP(3)，返回类型为 INTEGER I
    // 验证字符串表示是否正确
    MatcherAssert.assertThat(actual, Matchers.isLinux(expected));
    // 验证两个 MutableRel 是否相等
    assertThat(mutableRel2, is(mutableRel1));
  }

  // 验证 MutableScan 的等价性
  // 测试 MutableScan 的 equals 和 hashCode 方法是否正确实现
  // MutableScan 表示对表或视图的扫描操作
  @Test void testMutableScanEquivalence() {
    // 创建框架配置和关系构建器
    final FrameworkConfig config = RelBuilderTest.config().build();
    final RelBuilder builder = RelBuilder.create(config);

    // 测试1：相同的表名（EMP）应该生成相等的 MutableScan
    assertThat(mutableScanOf(builder, "EMP"),
        equalTo(mutableScanOf(builder, "EMP")));
    // 测试1：hashCode 也应该相等
    assertThat(mutableScanOf(builder, "EMP").hashCode(),
        equalTo(mutableScanOf(builder, "EMP").hashCode()));

    // 测试2：相同的完整表名（schema + table）应该生成相等的 MutableScan
    assertThat(mutableScanOf(builder, "scott", "EMP"),
        equalTo(mutableScanOf(builder, "scott", "EMP")));
    // 测试2：hashCode 也应该相等
    assertThat(mutableScanOf(builder, "scott", "EMP").hashCode(),
        equalTo(mutableScanOf(builder, "scott", "EMP").hashCode()));

    // 测试3：指定 schema 和不指定 schema 应该生成相等的 MutableScan（如果 schema 是默认的）
    assertThat(mutableScanOf(builder, "scott", "EMP"),
        equalTo(mutableScanOf(builder, "EMP")));
    // 测试3：hashCode 也应该相等
    assertThat(mutableScanOf(builder, "scott", "EMP").hashCode(),
        equalTo(mutableScanOf(builder, "EMP").hashCode()));

    // 测试4：不同的表名（EMP vs DEPT）应该生成不相等的 MutableScan
    assertThat(mutableScanOf(builder, "EMP"),
        not(equalTo(mutableScanOf(builder, "DEPT"))));
  }

  // 验证 RelNode 到 MutableRel 再转回 RelNode 的转换是否保持一致性
  // 这是一个重载方法，默认不进行去相关化处理，也不应用任何规则
  // 
  // @param rel 期望在 MutableRel 字符串中包含的关系类型名称（如 "Aggregate"、"Filter" 等）
  // @param sql 要测试的 SQL 语句
  private static void checkConvertMutableRel(String rel, String sql) {
    checkConvertMutableRel(rel, sql, false, null); // 调用完整版本的方法，decorrelate=false, rules=null
  }

  // 验证 RelNode 到 MutableRel 再转回 RelNode 的转换是否保持一致性
  // 这是核心测试方法，执行以下步骤：
  // 1. 将 SQL 转换为 RelNode（原始关系表达式）
  // 2. 如果提供了规则，使用 HEP 规划器应用规则进行优化
  // 3. 将 RelNode 转换为 MutableRel（可变关系表达式）
  // 4. 将 MutableRel 转换回 RelNode（新的关系表达式）
  // 5. 验证三个方面的正确性：
  //    - MutableRel 的字符串表示包含预期的关系类型
  //    - MutableRel 的行类型与原始 RelNode 的行类型相同
  //    - 新 RelNode 的字符串表示与原始 RelNode 的字符串表示相同
  // 
  // @param rel 期望在 MutableRel 字符串中包含的关系类型名称
  // @param sql 要测试的 SQL 语句
  // @param decorrelate 是否进行去相关化处理（将相关子查询转换为独立查询）
  // @param rules 要应用的优化规则列表（可为 null）
  private static void checkConvertMutableRel(
      String rel, String sql, boolean decorrelate, List<RelOptRule> rules) {
    // 创建 SQL 到关系表达式的测试夹具，设置 SQL 和去相关化选项
    final SqlToRelFixture fixture =
        SqlToRelFixture.DEFAULT.withSql(sql).withDecorrelate(decorrelate);
    // 将 SQL 转换为 RelNode（原始关系表达式）
    RelNode origRel = fixture.toRel();
    // 如果提供了优化规则，使用 HEP 规划器进行优化
    if (rules != null) {
      // 构建 HEP 程序，添加规则集合
      final HepProgram hepProgram =
          new HepProgramBuilder().addRuleCollection(rules).build();
      // 创建 HEP 规划器
      final HepPlanner hepPlanner = new HepPlanner(hepProgram);
      // 设置原始关系表达式为规划器的根节点
      hepPlanner.setRoot(origRel);
      // 执行规划，应用规则并返回优化后的关系表达式
      origRel = hepPlanner.findBestExp();
    }
    // 将 RelNode 转换为 MutableRel（可变关系表达式）
    final MutableRel mutableRel = MutableRels.toMutable(origRel);
    // 将 MutableRel 转换回 RelNode（新的关系表达式）
    final RelNode newRel = MutableRels.fromMutable(mutableRel);

    // 检查1：验证 MutableRel 的深度字符串表示是否包含预期的关系类型
    final String mutableRelStr = mutableRel.deep(); // 获取 MutableRel 的深度字符串表示
    final String msg1 =
        "Mutable rel: " + mutableRelStr + " does not contain target rel: " + rel;
    assertTrue(mutableRelStr.contains(rel), msg1); // 断言字符串包含预期的关系类型

    // 检查2：验证 MutableRel 的行类型与原始 RelNode 的行类型是否相同
    final RelDataType origRelType = origRel.getRowType(); // 获取原始 RelNode 的行类型
    final RelDataType mutableRelType = mutableRel.rowType; // 获取 MutableRel 的行类型
    final String msg2 =
        "Mutable rel's row type does not match with the original rel.\n"
        + "Original rel type: " + origRelType
        + ";\nMutable rel type: " + mutableRelType;
    // 使用 RelOptUtil.equal 方法比较两个行类型是否相等（忽略某些差异）
    assertTrue(
        equal(
            "origRelType", origRelType,
            "mutableRelType", mutableRelType,
            IGNORE),
        msg2);

    // 检查3：验证从 MutableRel 转换回的新 RelNode 是否与原始 RelNode 完全相同
    final String origRelStr = RelOptUtil.toString(origRel); // 获取原始 RelNode 的字符串表示
    final String newRelStr = RelOptUtil.toString(newRel); // 获取新 RelNode 的字符串表示
    final String msg3 =
        "The converted new rel is different from the original rel.\n"
        + "Original rel: " + origRelStr + ";\nNew rel: " + newRelStr;
    // 断言两个字符串表示相同
    assertThat(msg3, newRelStr, is(origRelStr));
  }

  // 创建 MutableRel 的辅助方法
  // 将 SQL 语句转换为 RelNode，再转换为 MutableRel
  // 
  // @param sql 要转换的 SQL 语句
  // @return 对应的 MutableRel 对象
  private static MutableRel createMutableRel(String sql) {
    // 使用默认的 SqlToRelFixture 将 SQL 转换为 RelNode
    RelNode rel = SqlToRelFixture.DEFAULT.withSql(sql).toRel();
    // 将 RelNode 转换为 MutableRel 并返回
    return MutableRels.toMutable(rel);
  }

  // 创建 MutableScan 的辅助方法
  // 使用 RelBuilder 创建表扫描节点，然后转换为 MutableScan
  // 
  // @param builder 关系构建器，用于构建关系表达式
  // @param tableNames 表名数组，可以是 ["表名"] 或 ["schema", "表名"] 的形式
  // @return 对应的 MutableScan 对象
  private MutableScan mutableScanOf(RelBuilder builder, String... tableNames) {
    // 使用 RelBuilder 扫描指定的表并构建 RelNode
    final RelNode scan = builder.scan(tableNames).build();
    // 将 RelNode 转换为 MutableRel 并强制转换为 MutableScan
    return (MutableScan) MutableRels.toMutable(scan);
  }
}
