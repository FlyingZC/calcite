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
// Apache许可证声明，允许在遵守Apache 2.0许可证的前提下使用、修改和分发本代码
package org.apache.calcite.test; // 声明包名，本类位于org.apache.calcite.test测试包中
import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入可枚举约定，用于可枚举的物理实现
import org.apache.calcite.adapter.enumerable.EnumerableHashJoin; // 导入可枚举哈希连接实现
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 导入可枚able规则集合
import org.apache.calcite.plan.Convention; // 导入约定接口，定义关系代数表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含表达式工厂和类型系统
import org.apache.calcite.plan.RelOptRuleCall; // 导入关系优化规则调用对象
import org.apache.calcite.plan.RelOptUtil; // 导入关系优化工具类，提供各种实用方法
import org.apache.calcite.plan.RelRule; // 导入关系规则基类
import org.apache.calcite.plan.hep.HepPlanner; // 导入HepPlanner（启发式规划器），用于基于规则的转换
import org.apache.calcite.plan.hep.HepProgram; // 导入Hep程序，定义规则执行的顺序
import org.apache.calcite.plan.hep.HepProgramBuilder; // 导入Hep程序构建器，用于构建Hep程序
import org.apache.calcite.plan.volcano.AbstractConverter; // 导入Volcano规划器的抽象转换器规则
import org.apache.calcite.rel.RelCollationTraitDef; // 导入排序特征定义，用于描述排序顺序
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系代数节点的基类
import org.apache.calcite.rel.RelShuttleImpl; // 导入关系穿梭器实现，用于遍历关系表达式树
import org.apache.calcite.rel.RelVisitor; // 导入关系访问者，用于访问关系表达式树
import org.apache.calcite.rel.convert.ConverterRule; // 导入转换规则基类，用于在不同约定间转换
import org.apache.calcite.rel.core.Aggregate; // 导入聚合节点，表示聚合操作
import org.apache.calcite.rel.core.Calc; // 导入计算节点，表示计算操作（投影+过滤）
import org.apache.calcite.rel.core.Filter; // 导入过滤节点，表示过滤操作
import org.apache.calcite.rel.core.Join; // 导入连接节点，表示连接操作
import org.apache.calcite.rel.core.JoinInfo; // 导入连接信息，包含连接条件和等价键
import org.apache.calcite.rel.core.Snapshot; // 导入快照节点，表示时间旅行查询
import org.apache.calcite.rel.core.TableFunctionScan; // 导入表函数扫描节点
import org.apache.calcite.rel.core.TableScan; // 导入表扫描节点，表示从表读取数据
import org.apache.calcite.rel.core.Window; // 导入窗口节点，表示窗口函数操作
import org.apache.calcite.rel.hint.HintPredicate; // 导入提示谓词，用于判断提示是否适用于某个关系节点
import org.apache.calcite.rel.hint.HintPredicates; // 导入常用提示谓词集合
import org.apache.calcite.rel.hint.HintStrategy; // 导入提示策略，定义如何处理提示
import org.apache.calcite.rel.hint.HintStrategyTable; // 导入提示策略表，存储提示名称到策略的映射
import org.apache.calcite.rel.hint.Hintable; // 导入可提示接口，表示可以接受提示的关系节点
import org.apache.calcite.rel.hint.RelHint; // 导入关系提示，表示SQL提示
import org.apache.calcite.rel.logical.LogicalAggregate; // 导入逻辑聚合节点
import org.apache.calcite.rel.logical.LogicalCorrelate; // 导入逻辑关联节点，用于去相关
import org.apache.calcite.rel.logical.LogicalFilter; // 导入逻辑过滤节点
import org.apache.calcite.rel.logical.LogicalIntersect; // 导入逻辑交集节点
import org.apache.calcite.rel.logical.LogicalJoin; // 导入逻辑连接节点
import org.apache.calcite.rel.logical.LogicalMinus; // 导入逻辑差集节点
import org.apache.calcite.rel.logical.LogicalProject; // 导入逻辑投影节点
import org.apache.calcite.rel.logical.LogicalSort; // 导入逻辑排序节点
import org.apache.calcite.rel.logical.LogicalUnion; // 导入逻辑并集节点
import org.apache.calcite.rel.logical.LogicalValues; // 导入逻辑值节点
import org.apache.calcite.rel.rules.CoreRules; // 导入核心规则集合
import org.apache.calcite.sql.SqlDelete; // 导入SQL删除语句节点
import org.apache.calcite.sql.SqlInsert; // 导入SQL插入语句节点
import org.apache.calcite.sql.SqlMerge; // 导入SQL合并语句节点
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口
import org.apache.calcite.sql.SqlNodeList; // 导入SQL节点列表
import org.apache.calcite.sql.SqlTableRef; // 导入SQL表引用节点
import org.apache.calcite.sql.SqlUpdate; // 导入SQL更新语句节点
import org.apache.calcite.sql.SqlUtil; // 导入SQL工具类
import org.apache.calcite.sql.test.SqlTestFactory; // 导入SQL测试工厂
import org.apache.calcite.sql.test.SqlTester; // 导入SQL测试器接口
import org.apache.calcite.tools.RuleSet; // 导入规则集接口
import org.apache.calcite.tools.RuleSets; // 导入规则集工具类
import org.apache.calcite.util.Litmus; // 导入Litmus枚举，定义错误处理策略
import org.apache.calcite.util.Util; // 导入通用工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解
import org.immutables.value.Value; // 导入不可变值注解
import org.junit.jupiter.api.AfterAll; // 导入JUnit5的AfterAll注解
import org.junit.jupiter.api.Assertions; // 导入JUnit5断言类
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解

import java.util.ArrayList; // 导入ArrayList列表类
import java.util.Arrays; // 导入Arrays数组工具类
import java.util.List; // 导入List接口
import java.util.Locale; // 导入Locale地区设置类
import java.util.function.Predicate; // 导入Predicate谓词函数接口
import java.util.function.UnaryOperator; // 导入UnaryOperator一元操作符接口
import java.util.stream.Collectors; // 导入Collectors收集器工具
import java.util.stream.Stream; // 导入Stream流接口

import static org.apache.calcite.test.Matchers.relIsValid; // 导入关系表达式有效匹配器
import static org.apache.calcite.test.SqlToRelTestBase.NL; // 导入换行符常量

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言方法
import static org.hamcrest.Matchers.hasSize; // 导入hasSize匹配器
import static org.hamcrest.collection.IsIn.in; // 导入in匹配器
import static org.junit.jupiter.api.Assertions.assertArrayEquals; // 导入数组相等断言
import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入非空断言
import static org.junit.jupiter.api.Assertions.fail; // 导入失败断言

import static java.util.Objects.requireNonNull; // 导入requireNonNull方法

/**
 * Unit test for {@link org.apache.calcite.rel.hint.RelHint}.
 * See {@link RelOptRulesTest} for an explanation of how to add tests.
 */
// 本类是RelHint（关系提示）的单元测试类，用于测试SQL提示在Calcite中的转换和传播机制
// 关系提示是SQL中的一种机制，允许用户影响查询优化器的行为，例如选择特定的连接算法或索引
class SqlHintsConverterTest { // SqlHintsConverterTest类：测试SQL提示转换功能的测试类

  static final Fixture FIXTURE = // 创建静态的Fixture测试夹具实例，用于SQL到关系表达式的转换测试
      new Fixture(SqlTestFactory.INSTANCE, // 使用单例的SQL测试工厂
          DiffRepository.lookup(SqlHintsConverterTest.class), // 查找差异仓库，用于比较测试结果
          "?", false, false) // 初始SQL为"?"，不启用去相关，不启用字段修剪
          .withFactory(f -> // 配置工厂函数
              f.withSqlToRelConfig(c -> // 配置SQL到关系表达式的配置
                  c.withHintStrategyTable(HintTools.HINT_STRATEGY_TABLE) // 设置提示策略表，定义如何处理各种提示
                      .withExpand(true))); // 启用子查询展开

  static final RelOptFixture RULE_FIXTURE = // 创建静态的RelOptFixture优化测试夹具，用于测试优化规则
      RelOptFixture.DEFAULT // 使用默认的优化测试夹具
          .withDiffRepos(DiffRepository.lookup(SqlHintsConverterTest.class)) // 设置差异仓库
          .withConfig(c -> // 配置函数
              c.withHintStrategyTable(HintTools.HINT_STRATEGY_TABLE)); // 设置提示策略表

  @Nullable // 标注可能为null
  private static DiffRepository diffRepos = null; // 静态的差异仓库引用，用于在测试结束后检查文件

  @AfterAll // 在所有测试方法执行后执行
  public static void checkActualAndReferenceFiles() { // 检查实际输出文件和参考文件是否一致
    if (diffRepos != null) { // 如果差异仓库不为空
      diffRepos.checkActualAndReferenceFiles(); // 检查并比较实际文件和参考文件
    }
  }

  protected Fixture fixture() { // 获取Fixture测试夹具的受保护方法
    return FIXTURE; // 返回静态的FIXTURE实例
  }

  protected RelOptFixture ruleFixture() { // 获取RelOptFixture优化测试夹具的受保护方法
    diffRepos = RULE_FIXTURE.diffRepos(); // 保存差异仓库引用
    return RULE_FIXTURE; // 返回静态的RULE_FIXTURE实例
  }

  /** Sets the SQL statement for a test. */
  // 为测试设置SQL语句的方法
  public final Fixture sql(String sql) { // 接收SQL字符串，返回配置好的Fixture对象
    return fixture().sql(sql); // 调用fixture的sql方法设置SQL语句
  }

  //~ Tests ------------------------------------------------------------------
  // 测试方法区域开始

  @Test void testQueryHint() { // 测试查询级别的提示
    final String sql = HintTools.withHint("select /*+ %s */ *\n" // 使用HintTools工具类构建带提示的SQL语句
        + "from emp e1\n" // 从emp表e1
        + "inner join dept d1 on e1.deptno = d1.deptno\n" // 内连接dept表d1
        + "inner join emp e2 on e1.ename = e2.job"); // 再内连接emp表e2
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testQueryHintWithLiteralOptions() { // 测试带字面量选项的查询提示
    final String sql = "select /*+ time_zone(1, 1.23, 'a bc', -1.0) */ *\n" // SQL带time_zone提示，包含多种字面量值
        + "from emp"; // 从emp表查询
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testNestedQueryHint() { // 测试嵌套查询提示
    final String sql = "select /*+ resource(parallelism='3'), repartition(10) */ empno\n" // 外层查询带resource和repartition提示
        + "from (select /*+ resource(mem='20Mb')*/ empno, ename from emp)"; // 子查询带resource提示
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testTwoLevelNestedQueryHint() { // 测试两级嵌套查询提示
    final String sql = "select /*+ resource(parallelism='3'), no_hash_join */ empno\n" // 外层查询带resource和no_hash_join提示
        + "from (select /*+ resource(mem='20Mb')*/ empno, ename\n" // 子查询带resource提示
        + "from emp left join dept on emp.deptno = dept.deptno)"; // 子查询包含左连接
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testThreeLevelNestedQueryHint() { // 测试三级嵌套查询提示
    final String sql = "select /*+ index(idx1), no_hash_join */ * from emp /*+ index(empno) */\n" // 外层查询和emp表都带提示
        + "e1 join dept/*+ index(deptno) */ d1 on e1.deptno = d1.deptno\n" // dept表也带提示
        + "join emp e2 on d1.name = e2.job"; // 第二次连接emp表
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testFourLevelNestedQueryHint() { // 测试四级嵌套查询提示
    final String sql = "select /*+ index(idx1), no_hash_join */ * from emp /*+ index(empno) */\n" // 外层查询和emp表带提示
        + "e1 join dept/*+ index(deptno) */ d1 on e1.deptno = d1.deptno join\n" // dept表带提示
        + "(select max(sal) as sal from emp /*+ index(empno) */) e2 on e1.sal = e2.sal"; // 子查询中的emp表也带提示
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testAggregateHints() { // 测试聚合提示
    final String sql = "select /*+ AGG_STRATEGY(TWO_PHASE), RESOURCE(mem='1024') */\n" // 外层聚合带AGG_STRATEGY和RESOURCE提示
        + "count(deptno), avg_sal from (\n" // 统计deptno数量
        + "select /*+ AGG_STRATEGY(ONE_PHASE) */ avg(sal) as avg_sal, deptno\n" // 内层聚合带ONE_PHASE策略提示
        + "from emp group by deptno) group by avg_sal"; // 按avg_sal分组
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testCorrelateHints() { // 测试关联提示
    final String sql = "select /*+ use_hash_join (orders, products_temporal) */ stream *\n" // 查询带use_hash_join提示
        + "from orders join products_temporal for system_time as of orders.rowtime\n" // 时间旅行连接
        + "on orders.productid = products_temporal.productid and orders.orderId is not null"; // 连接条件
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testCrossCorrelateHints() { // 测试交叉关联提示
    final String sql = "select /*+ use_hash_join (orders, products_temporal) */ stream *\n" // 查询带use_hash_join提示
        + "from orders, products_temporal for system_time as of orders.rowtime"; // 交叉时间旅行连接
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testFilterHints() { // 测试过滤提示
    final String sql = "select /*+ resource(parallelism='3') */ avg(sal) as avg_sal, deptno\n" // 查询带resource提示
            + "from emp group by deptno having avg(sal) > 5000"; // 聚合后过滤
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testUnionHints() { // 测试并集提示
    final String sql = "select /*+ breakable */ deptno from\n" // 查询带breakable提示
            + "(select ename, deptno from emp\n" // 子查询1：从emp表选择
            + "union all\n" // 并集操作
            + "select name, deptno from dept)"; // 子查询2：从dept表选择
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testMinusHints() { // 测试差集提示
    final String sql = "select /*+ breakable */ deptno from\n" // 查询带breakable提示
        + "(select ename, deptno from emp\n" // 子查询1：从emp表选择
        + "except all\n" // 差集操作
        + "select name, deptno from dept)"; // 子查询2：从dept表选择
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testIntersectHints() { // 测试交集提示
    final String sql = "select /*+ breakable */ deptno from\n" // 查询带breakable提示
        + "(select ename, deptno from emp\n" // 子查询1：从emp表选择
        + "intersect all\n" // 交集操作
        + "select name, deptno from dept)"; // 子查询2：从dept表选择
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testSortHints() { // 测试排序提示
    final String sql = "select /*+ async_merge */ empno from emp order by empno, empno desc"; // 查询带async_merge提示并排序
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testValuesHints() { // 测试值提示
    final String sql = "select /*+ resource(parallelism='3') */ a, max(b), max(b + 1)\n" // 查询带resource提示
        + "from (values (1, 2)) as t(a, b)\n" // 从值构造表
        + "group by a"; // 按a分组
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testWindowHints() { // 测试窗口提示
    final String sql = "select /*+ mini_batch */ last_value(deptno)\n" // 查询带mini_batch提示和窗口函数
        + "over (order by empno rows 2 following) from emp"; // 窗口定义
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testSnapshotHints() { // 测试快照提示
    final String sql = "select /*+ fast_snapshot(products_temporal) */ stream * from\n" // 查询带fast_snapshot提示
        + " orders join products_temporal for system_time as of timestamp '2022-08-11 15:00:00'\n" // 时间旅行快照
        + " on orders.productid = products_temporal.productid"; // 连接条件
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testTableFunctionScanHints() { // 测试表函数扫描提示
    final String sql = "select /*+ resource(parallelism='3') */ * from TABLE(ramp(5))"; // 查询带resource提示和表函数
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testHintsInSubQueryWithDecorrelation() { // 测试带去相关的子查询提示
    final String sql = "select /*+ resource(parallelism='3'), AGG_STRATEGY(TWO_PHASE) */\n" // 外层查询带提示
        + "sum(e1.empno) from emp e1, dept d1\n" // 求和
        + "where e1.deptno = d1.deptno\n" // 连接条件
        + "and e1.sal> (\n" // 子查询条件
        + "select /*+ resource(cpu='2') */ avg(e2.sal) from emp e2 where e2.deptno = d1.deptno)"; // 子查询带提示
    sql(sql).withDecorrelate(true).ok(); // 启用去相关并执行验证
  }

  @Test void testHintsInSubQueryWithDecorrelation2() { // 测试带去相关的子查询提示（第二组）
    final String sql = "select /*+ properties(k1='v1', k2='v2'), index(ename), no_hash_join */\n" // 外层查询带多个提示
        + "sum(e1.empno) from emp e1, dept d1\n" // 求和
        + "where e1.deptno = d1.deptno\n" // 连接条件
        + "and e1.sal> (\n" // 子查询条件
        + "select /*+ properties(k1='v1', k2='v2'), index(ename), no_hash_join */\n" // 子查询带相同提示
        + "  avg(e2.sal)\n" // 计算平均值
        + "  from emp e2\n" // 从emp表
        + "  where e2.deptno = d1.deptno)"; // 子查询条件
    sql(sql).withDecorrelate(true).ok(); // 启用去相关并执行验证
  }

  @Test void testHintsInSubQueryWithDecorrelation3() { // 测试带去相关的子查询提示（第三组）
    final String sql = "select /*+ resource(parallelism='3'), index(ename), no_hash_join */\n" // 外层查询带提示
        + "sum(e1.empno) from emp e1, dept d1\n" // 求和
        + "where e1.deptno = d1.deptno\n" // 连接条件
        + "and e1.sal> (\n" // 子查询条件
        + "select /*+ resource(cpu='2'), index(ename), no_hash_join */\n" // 子查询带不同提示
        + "  avg(e2.sal)\n" // 计算平均值
        + "  from emp e2\n" // 从emp表
        + "  where e2.deptno = d1.deptno)"; // 子查询条件
    sql(sql).withDecorrelate(true).ok(); // 启用去相关并执行验证
  }

  @Test void testHintsInSubQueryWithoutDecorrelation() { // 测试不带去相关的子查询提示
    final String sql = "select /*+ resource(parallelism='3') */\n" // 外层查询带提示
        + "sum(e1.empno) from emp e1, dept d1\n" // 求和
        + "where e1.deptno = d1.deptno\n" // 连接条件
        + "and e1.sal> (\n" // 子查询条件
        + "select /*+ resource(cpu='2') */ avg(e2.sal) from emp e2 where e2.deptno = d1.deptno)"; // 子查询带提示
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testInvalidQueryHint() { // 测试无效的查询提示
    final String sql = "select /*+ weird_hint */ empno\n" // SQL带未注册的提示
        + "from (select /*+ resource(mem='20Mb')*/ empno, ename\n" // 子查询带有效提示
        + "from emp left join dept on emp.deptno = dept.deptno)"; // 左连接
    sql(sql).warns("Hint: WEIRD_HINT should be registered in the HintStrategyTable"); // 验证产生警告

    final String sql1 = "select /*+ resource(mem='20Mb')*/ empno\n" // SQL带有效提示
        + "from (select /*+ weird_kv_hint(k1='v1') */ empno, ename\n" // 子查询带未注册的键值提示
        + "from emp left join dept on emp.deptno = dept.deptno)"; // 左连接
    sql(sql1).warns("Hint: WEIRD_KV_HINT should be registered in the HintStrategyTable"); // 验证产生警告

    final String sql2 = "select /*+ AGG_STRATEGY(OPTION1) */\n" // SQL带无效的AGG_STRATEGY选项
        + "ename, avg(sal)\n" // 选择字段
        + "from emp group by ename"; // 按ename分组
    final String error2 = "Hint AGG_STRATEGY only allows single option, " // 错误消息
        + "allowed options: [ONE_PHASE, TWO_PHASE]"; // 允许的选项
    sql(sql2).warns(error2); // 验证产生警告
    // Change the error handler to validate again.
    // 更改错误处理器再次验证
    sql(sql2).withFactory(f -> // 配置工厂
        f.withSqlToRelConfig(c -> // 配置SQL到关系表达式
            c.withHintStrategyTable( // 设置提示策略表
                HintTools.createHintStrategies( // 创建提示策略
                    HintStrategyTable.builder().errorHandler(Litmus.THROW))))) // 设置错误处理器为抛出异常
        .fails(error2); // 验证失败并抛出指定错误
  }

  @Test void testTableHintsInJoin() { // 测试连接中的表提示
    final String sql = "select\n" // 选择
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp /*+ index(idx1, idx2) */\n" // emp表带index提示
        + "join dept /*+ properties(k1='v1', k2='v2') */\n" // dept表带properties提示
        + "on emp.deptno = dept.deptno"; // 连接条件
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testTableHintsInSelect() { // 测试SELECT中的表提示
    final String sql = HintTools.withHint("select * from emp /*+ %s */"); // 使用HintTools构建带提示的SQL
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testSameHintsWithDifferentInheritPath() { // 测试相同提示在不同继承路径
    final String sql = "select /*+ properties(k1='v1', k2='v2') */\n" // 外层查询带properties提示
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp /*+ index(idx1, idx2) */\n" // emp表带index提示
        + "join dept /*+ properties(k1='v1', k2='v2') */\n" // dept表也带properties提示
        + "on emp.deptno = dept.deptno"; // 连接条件
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testTableHintsInInsert() throws Exception { // 测试INSERT中的表提示
    final String sql = HintTools.withHint("insert into dept /*+ %s */ (deptno, name) " // 构建带提示的INSERT语句
        + "select deptno, name from dept"); // 查询部分
    final SqlInsert insert = (SqlInsert) sql(sql).parseQuery(); // 解析SQL得到SqlInsert节点
    assert insert.getTargetTable() instanceof SqlTableRef; // 断言目标表是SqlTableRef类型
    final SqlTableRef tableRef = (SqlTableRef) insert.getTargetTable(); // 获取表引用
    List<RelHint> hints = // 获取提示列表
        SqlUtil.getRelHint(HintTools.HINT_STRATEGY_TABLE, // 使用提示策略表
            (SqlNodeList) tableRef.getOperandList().get(1)); // 从操作数列表获取提示
    assertHintsEquals( // 断言提示相等
        Arrays.asList(HintTools.PROPS_HINT, HintTools.IDX_HINT, // 期望的提示列表
            HintTools.JOIN_HINT),
        hints); // 实际的提示列表
  }

  @Test void testTableHintsInUpdate() throws Exception { // 测试UPDATE中的表提示
    final String sql = HintTools.withHint("update emp /*+ %s */ " // 构建带提示的UPDATE语句
        + "set name = 'test' where deptno = 1"); // 更新条件和值
    final SqlUpdate sqlUpdate = (SqlUpdate) sql(sql).parseQuery(); // 解析SQL得到SqlUpdate节点
    assert sqlUpdate.getTargetTable() instanceof SqlTableRef; // 断言目标表是SqlTableRef类型
    final SqlTableRef tableRef = (SqlTableRef) sqlUpdate.getTargetTable(); // 获取表引用
    List<RelHint> hints = // 获取提示列表
        SqlUtil.getRelHint(HintTools.HINT_STRATEGY_TABLE, // 使用提示策略表
            (SqlNodeList) tableRef.getOperandList().get(1)); // 从操作数列表获取提示
    assertHintsEquals( // 断言提示相等
        Arrays.asList(HintTools.PROPS_HINT, HintTools.IDX_HINT, // 期望的提示列表
            HintTools.JOIN_HINT),
        hints); // 实际的提示列表
  }

  @Test void testTableHintsInDelete() throws Exception { // 测试DELETE中的表提示
    final String sql = HintTools.withHint("delete from emp /*+ %s */ where deptno = 1"); // 构建带提示的DELETE语句
    final SqlDelete sqlDelete = (SqlDelete) sql(sql).parseQuery(); // 解析SQL得到SqlDelete节点
    assert sqlDelete.getTargetTable() instanceof SqlTableRef; // 断言目标表是SqlTableRef类型
    final SqlTableRef tableRef = (SqlTableRef) sqlDelete.getTargetTable(); // 获取表引用
    List<RelHint> hints = // 获取提示列表
        SqlUtil.getRelHint(HintTools.HINT_STRATEGY_TABLE, // 使用提示策略表
            (SqlNodeList) tableRef.getOperandList().get(1)); // 从操作数列表获取提示
    assertHintsEquals( // 断言提示相等
        Arrays.asList(HintTools.PROPS_HINT, HintTools.IDX_HINT, // 期望的提示列表
            HintTools.JOIN_HINT),
        hints); // 实际的提示列表
  }

  @Test void testTableHintsInMerge() throws Exception { // 测试MERGE中的表提示
    final String sql = "merge into emps\n" // MERGE语句开始
        + "/*+ %s */ e\n" // 目标表带提示占位符
        + "using tempemps as t\n" // 源表
        + "on e.empno = t.empno\n" // 匹配条件
        + "when matched then update\n" // 匹配时更新
        + "set name = t.name, deptno = t.deptno, salary = t.salary * .1\n" // 更新字段
        + "when not matched then insert (name, dept, salary)\n" // 不匹配时插入
        + "values(t.name, 10, t.salary * .15)"; // 插入值
    final String sql1 = HintTools.withHint(sql); // 使用HintTools填充提示

    final SqlMerge sqlMerge = (SqlMerge) sql(sql1).parseQuery(); // 解析SQL得到SqlMerge节点
    assert sqlMerge.getTargetTable() instanceof SqlTableRef; // 断言目标表是SqlTableRef类型
    final SqlTableRef tableRef = (SqlTableRef) sqlMerge.getTargetTable(); // 获取表引用
    List<RelHint> hints = // 获取提示列表
        SqlUtil.getRelHint(HintTools.HINT_STRATEGY_TABLE, // 使用提示策略表
            (SqlNodeList) tableRef.getOperandList().get(1)); // 从操作数列表获取提示
    assertHintsEquals( // 断言提示相等
        Arrays.asList(HintTools.PROPS_HINT, HintTools.IDX_HINT, // 期望的提示列表
            HintTools.JOIN_HINT),
        hints); // 实际的提示列表
  }

  @Test void testInvalidTableHints() { // 测试无效的表提示
    final String sql = "select\n" // 选择
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp /*+ weird_hint(idx1, idx2) */\n" // emp表带未注册的提示
        + "join dept /*+ properties(k1='v1', k2='v2') */\n" // dept表带有效提示
        + "on emp.deptno = dept.deptno"; // 连接条件
    sql(sql).warns("Hint: WEIRD_HINT should be registered in the HintStrategyTable"); // 验证产生警告

    final String sql1 = "select\n" // 选择
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp /*+ index(idx1, idx2) */\n" // emp表带有效提示
        + "join dept /*+ weird_kv_hint(k1='v1', k2='v2') */\n" // dept表带未注册的键值提示
        + "on emp.deptno = dept.deptno"; // 连接条件
    sql(sql1).warns("Hint: WEIRD_KV_HINT should be registered in the HintStrategyTable"); // 验证产生警告
  }

  @Test void testJoinHintRequiresSpecificInputs() { // 测试连接提示需要特定的输入
    final String sql = "select /*+ use_hash_join(r, s), use_hash_join(emp, dept) */\n" // SQL带两个use_hash_join提示
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp join dept on emp.deptno = dept.deptno"; // 连接emp和dept表
    // Hint use_hash_join(r, s) expect to be ignored by the join node.
    // 提示use_hash_join(r, s)预期被连接节点忽略，因为表名不匹配
    sql(sql).ok(); // 执行SQL并验证结果正确
  }

  @Test void testHintsForCalc() { // 测试Calc节点的提示
    final String sql = "select /*+ resource(mem='1024MB')*/ ename, sal, deptno from emp"; // SQL带resource提示
    final RelNode rel = sql(sql).toRel(); // 将SQL转换为关系表达式
    final RelHint hint = RelHint.builder("RESOURCE") // 构建RESOURCE提示
        .hintOption("MEM", "1024MB") // 设置MEM选项
        .build(); // 构建提示对象
    // planner rule to convert Project to Calc.
    // 规划器规则将Project转换为Calc
    HepProgram program = new HepProgramBuilder() // 创建Hep程序构建器
        .addRuleInstance(CoreRules.PROJECT_TO_CALC) // 添加Project到Calc的转换规则
        .build(); // 构建Hep程序
    HepPlanner planner = new HepPlanner(program); // 创建Hep规划器
    planner.setRoot(rel); // 设置关系表达式为根节点
    RelNode newRel = planner.findBestExp(); // 查找最佳表达式
    new ValidateHintVisitor(hint, Calc.class).go(newRel); // 验证Calc节点包含期望的提示
  }

  @Test void testHintsPropagationInHepPlannerRules() { // 测试提示在Hep规划器规则中的传播
    final String sql = "select /*+ use_hash_join(r, s), use_hash_join(emp, dept) */\n" // SQL带提示
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp join dept on emp.deptno = dept.deptno"; // 连接条件
    final RelNode rel = sql(sql).toRel(); // 将SQL转换为关系表达式
    final RelHint hint = RelHint.builder("USE_HASH_JOIN") // 构建USE_HASH_JOIN提示
        .inheritPath(0) // 设置继承路径
        .hintOption("EMP") // 添加EMP选项
        .hintOption("DEPT") // 添加DEPT选项
        .build(); // 构建提示对象
    // Validate Hep planner.
    // 验证Hep规划器
    HepProgram program = new HepProgramBuilder() // 创建Hep程序构建器
        .addRuleInstance(MockJoinRule.INSTANCE) // 添加模拟连接规则
        .build(); // 构建Hep程序
    HepPlanner planner = new HepPlanner(program); // 创建Hep规划器
    planner.setRoot(rel); // 设置关系表达式为根节点
    RelNode newRel = planner.findBestExp(); // 查找最佳表达式
    new ValidateHintVisitor(hint, Join.class).go(newRel); // 验证Join节点包含期望的提示
  }

  @Test void testHintsPropagationInVolcanoPlannerRules() { // 测试提示在Volcano规划器规则中的传播
    final String sql = "select /*+ use_hash_join(r, s), use_hash_join(emp, dept) */\n" // SQL带提示
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp join dept on emp.deptno = dept.deptno"; // 连接条件
    final RelHint hint = RelHint.builder("USE_HASH_JOIN") // 构建USE_HASH_JOIN提示
        .inheritPath(0) // 设置继承路径
        .hintOption("EMP") // 添加EMP选项
        .hintOption("DEPT") // 添加DEPT选项
        .build(); // 构建提示对象
    // Validate Volcano planner.
    // 验证Volcano规划器
    RuleSet ruleSet = // 创建规则集
        RuleSets.ofList(MockEnumerableJoinRule.create(hint), // 添加模拟可枚举连接规则（验证提示）
            CoreRules.FILTER_PROJECT_TRANSPOSE, // 添加过滤和投影转置规则
            CoreRules.FILTER_MERGE, // 添加过滤合并规则
            CoreRules.PROJECT_MERGE, // 添加投影合并规则
            EnumerableRules.ENUMERABLE_JOIN_RULE, // 添加可枚举连接规则
            EnumerableRules.ENUMERABLE_PROJECT_RULE, // 添加可枚举投影规则
            EnumerableRules.ENUMERABLE_FILTER_RULE, // 添加可枚举过滤规则
            EnumerableRules.ENUMERABLE_SORT_RULE, // 添加可枚举排序规则
            EnumerableRules.ENUMERABLE_LIMIT_RULE, // 添加可枚举限制规则
            EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE); // 添加可枚举表扫描规则
    ruleFixture() // 获取优化测试夹具
        .sql(sql) // 设置SQL
        .withVolcanoPlanner(false, p -> { // 使用Volcano规划器
          p.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加排序特征定义
          RelOptUtil.registerDefaultRules(p, false, false); // 注册默认规则
          ruleSet.forEach(p::addRule); // 添加规则集中的所有规则
        })
        .check(); // 检查结果
  }

  @Test void testHintsPropagationInVolcanoPlannerRules2() { // 测试提示在Volcano规划器规则中的传播（第二组）
    final String sql = "select /*+ no_hash_join */ ename, job\n" // SQL带no_hash_join提示
        + "from emp where not exists (select 1 from dept where emp.deptno = dept.deptno)"; // NOT EXISTS子查询
    final RelHint hint = RelHint.builder("NO_HASH_JOIN") // 构建NO_HASH_JOIN提示
        .inheritPath(0, 0) // 设置继承路径（两个层级）
        .build(); // 构建提示对象
    // Validate Volcano planner.
    // 验证Volcano规划器
    RuleSet ruleSet = // 创建规则集
        RuleSets.ofList(MockEnumerableJoinRule.create(hint));  // 添加模拟可枚举连接规则（验证提示）
    ruleFixture() // 获取优化测试夹具
        .sql(sql) // 设置SQL
        .withTrim(true) // 启用字段修剪
        .withVolcanoPlanner(false, p -> { // 使用Volcano规划器
          p.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加排序特征定义
          RelOptUtil.registerDefaultRules(p, false, false); // 注册默认规则
          ruleSet.forEach(p::addRule); // 添加规则集中的所有规则
        })
        .withExpand(true) // 启用子查询展开
        .check(); // 检查结果
  }

  @Test void testHintsPropagationInVolcanoPlannerRules3() { // 测试提示在Volcano规划器规则中的传播（第三组）
    final String sql = "select /*+ no_hash_join */ ename, job\n" // SQL带no_hash_join提示
        + "from emp where not exists (select 1 from dept where emp.deptno = dept.deptno) order by ename"; // NOT EXISTS子查询并排序
    final RelHint hint = RelHint.builder("NO_HASH_JOIN") // 构建NO_HASH_JOIN提示
        .inheritPath(0, 0, 0) // 设置继承路径（三个层级）
        .build(); // 构建提示对象
    // Validate Volcano planner.
    // 验证Volcano规划器
    RuleSet ruleSet = // 创建规则集
        RuleSets.ofList(MockEnumerableJoinRule.create(hint)); // 添加模拟可枚举连接规则（验证提示）
    ruleFixture() // 获取优化测试夹具
        .sql(sql) // 设置SQL
        .withTrim(true) // 启用字段修剪
        .withVolcanoPlanner(false, p -> { // 使用Volcano规划器
          p.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加排序特征定义
          RelOptUtil.registerDefaultRules(p, false, false); // 注册默认规则
          ruleSet.forEach(p::addRule); // 添加规则集中的所有规则
        })
        .withExpand(true) // 启用子查询展开
        .check(); // 检查结果
  }

  @Test void testHintsPropagateWithDifferentKindOfRels() { // 测试提示在不同类型关系节点间的传播
    final String sql = "select /*+ AGG_STRATEGY(TWO_PHASE) */\n" // SQL带AGG_STRATEGY提示
        + "ename, avg(sal)\n" // 选择字段
        + "from emp group by ename"; // 按ename分组
    final RelNode rel = sql(sql).toRel(); // 将SQL转换为关系表达式
    final RelHint hint = RelHint.builder("AGG_STRATEGY") // 构建AGG_STRATEGY提示
        .inheritPath(0) // 设置继承路径
        .hintOption("TWO_PHASE") // 添加TWO_PHASE选项
        .build(); // 构建提示对象
    // AggregateReduceFunctionsRule does the transformation:
    // AGG -> PROJECT + AGG
    // AggregateReduceFunctionsRule执行转换：聚合 -> 投影 + 聚合
    HepProgram program = new HepProgramBuilder() // 创建Hep程序构建器
        .addRuleInstance(CoreRules.AGGREGATE_REDUCE_FUNCTIONS) // 添加聚合函数归约规则
        .build(); // 构建Hep程序
    HepPlanner planner = new HepPlanner(program); // 创建Hep规划器
    planner.setRoot(rel); // 设置关系表达式为根节点
    RelNode newRel = planner.findBestExp(); // 查找最佳表达式
    new ValidateHintVisitor(hint, Aggregate.class).go(newRel); // 验证Aggregate节点包含期望的提示
  }

  @Test void testUseMergeJoin() { // 测试使用合并连接
    final String sql = "select /*+ use_merge_join(emp, dept) */\n" // SQL带use_merge_join提示
        + "ename, job, sal, dept.name\n" // 字段列表
        + "from emp join dept on emp.deptno = dept.deptno"; // 连接条件
    RuleSet ruleSet = // 创建规则集
        RuleSets.ofList(EnumerableRules.ENUMERABLE_MERGE_JOIN_RULE, // 添加可枚举合并连接规则
        EnumerableRules.ENUMERABLE_JOIN_RULE, // 添加可枚举连接规则
        EnumerableRules.ENUMERABLE_PROJECT_RULE, // 添加可枚举投影规则
        EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE, // 添加可枚举表扫描规则
        EnumerableRules.ENUMERABLE_SORT_RULE, // 添加可枚举排序规则
        AbstractConverter.ExpandConversionRule.INSTANCE); // 添加扩展转换规则

    ruleFixture() // 获取优化测试夹具
        .sql(sql) // 设置SQL
        .withVolcanoPlanner(false, planner -> { // 使用Volcano规划器
          planner.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加排序特征定义
          ruleSet.forEach(planner::addRule); // 添加规则集中的所有规则
        })
        .check(); // 检查结果
  }

  @Test void testHintExcludeRules() { // 测试提示排除规则
    final String sql = "select empno from (select * from " // 嵌套查询
            + "(select /*+ preserved_project */ empno, ename, deptno from emp)" // 最内层带preserved_project提示
            + " where deptno = 20)"; // 过滤条件

    final RelNode rel = ruleFixture().sql(sql).toRel(); // 将SQL转换为关系表达式
    HepProgram program = new HepProgramBuilder() // 创建Hep程序构建器
            .addRuleInstance(CoreRules.FILTER_PROJECT_TRANSPOSE) // 添加过滤投影转置规则
            .build(); // 构建Hep程序
    HepPlanner planner = new HepPlanner(program); // 创建Hep规划器
    planner.setRoot(rel); // 设置关系表达式为根节点
    RelNode newRel = planner.findBestExp(); // 查找最佳表达式
    Assertions.assertTrue(rel.deepEquals(newRel), // 断言两个关系表达式深度相等
        "Expected:\n" // 期望的结果
        + RelOptUtil.toString(rel) + "Computed:\n" // 实际的结果
        + RelOptUtil.toString(newRel)); // 比较结果
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域开始

  private static boolean equalsStringList(List<String> l, List<String> r) { // 比较两个字符串列表是否相等
    if (l.size() != r.size()) { // 如果大小不同
      return false; // 返回false
    }
    for (String s : l) { // 遍历左侧列表
      if (!r.contains(s)) { // 如果右侧列表不包含当前元素
        return false; // 返回false
      }
    }
    return true; // 返回true
  }

  private static void assertHintsEquals(List<RelHint> expected, List<RelHint> actual) { // 断言两个提示列表相等
    assertArrayEquals(expected.toArray(new RelHint[0]), // 将期望列表转为数组
        actual.toArray(new RelHint[0])); // 将实际列表转为数组并比较
  }

  //~ Inner Class ------------------------------------------------------------
  // 内部类区域开始

  /** A Mock rule to validate the hint. */
  // 模拟规则，用于验证提示
  public static class MockJoinRule extends RelRule<MockJoinRule.Config> { // MockJoinRule类：模拟连接规则
    public static final MockJoinRule INSTANCE = ImmutableMockJoinRuleConfig.builder() // 创建规则实例
        .build() // 构建配置
        .withOperandSupplier(b -> // 设置操作数提供者
            b.operand(LogicalJoin.class).anyInputs()) // 匹配LogicalJoin节点，任意输入
        .withDescription("MockJoinRule") // 设置描述
        .as(Config.class) // 转换为Config类型
        .toRule(); // 转换为规则

    MockJoinRule(Config config) { // 构造函数
      super(config); // 调用父类构造函数
    }

    @Override public void onMatch(RelOptRuleCall call) { // 规则匹配时调用
      LogicalJoin join = call.rel(0); // 获取连接节点
      assertThat(join.getHints(), hasSize(1)); // 断言连接节点有1个提示
      call.transformTo( // 转换为新节点
          LogicalJoin.create(join.getLeft(), // 创建新的LogicalJoin
              join.getRight(), // 右输入
              join.getHints(), // 提示列表
              join.getCondition(), // 连接条件
              join.getVariablesSet(), // 变量集合
              join.getJoinType())); // 连接类型
    }

    /** Rule configuration. */
    // 规则配置接口
    @Value.Immutable // 不可变值注解
    @Value.Style(typeImmutable = "ImmutableMockJoinRuleConfig") // 设置不可变类型名称
    public interface Config extends RelRule.Config { // Config接口：规则配置
      @Override default MockJoinRule toRule() { // 转换为规则的方法
        return new MockJoinRule(this); // 返回MockJoinRule实例
      }
    }
  }

  /** A Mock rule to validate the hint.
   * This rule also converts the rel to EnumerableConvention. */
  // 模拟规则，用于验证提示，同时将关系节点转换为可枚举约定
  private static class MockEnumerableJoinRule extends ConverterRule { // MockEnumerableJoinRule类：模拟可枚举连接规则
    static MockEnumerableJoinRule create(RelHint hint) { // 创建规则实例
      return Config.INSTANCE // 使用配置实例
          .withConversion(LogicalJoin.class, Convention.NONE, // 设置转换：从LogicalJoin到可枚举约定
              EnumerableConvention.INSTANCE, "MockEnumerableJoinRule") // 目标约定和描述
          .withRuleFactory(c -> new MockEnumerableJoinRule(c, hint)) // 设置规则工厂
          .toRule(MockEnumerableJoinRule.class); // 转换为规则
    }

    MockEnumerableJoinRule(Config config, RelHint hint) { // 构造函数
      super(config); // 调用父类构造函数
      this.expectedHint = hint; // 保存期望的提示
    }

    private final RelHint expectedHint; // 期望的提示

    @Override public RelNode convert(RelNode rel) { // 转换方法
      LogicalJoin join = (LogicalJoin) rel; // 转换为LogicalJoin
      assertThat(join.getHints(), hasSize(1)); // 断言连接节点有1个提示
      assertThat(join.getHints().get(0), is(expectedHint)); // 断言提示等于期望提示
      List<RelNode> newInputs = new ArrayList<>(); // 创建新输入列表
      for (RelNode input : join.getInputs()) { // 遍历输入
        if (!(input.getConvention() instanceof EnumerableConvention)) { // 如果输入不是可枚举约定
          input = // 转换输入
            convert( // 调用转换方法
              input, // 输入节点
              input.getTraitSet() // 获取特征集合
                .replace(EnumerableConvention.INSTANCE)); // 替换为可枚举约定
        }
        newInputs.add(input); // 添加到新输入列表
      }
      final RelOptCluster cluster = join.getCluster(); // 获取集群
      final RelNode left = newInputs.get(0); // 获取左输入
      final RelNode right = newInputs.get(1); // 获取右输入
      final JoinInfo info = join.analyzeCondition(); // 分析连接条件
      return EnumerableHashJoin.create( // 创建可枚举哈希连接
        left, // 左输入
        right, // 右输入
        info.getEquiCondition(left, right, cluster.getRexBuilder()), // 等价条件
        join.getVariablesSet(), // 变量集合
        join.getJoinType()); // 连接类型
    }
  }

  /** A visitor to validate a hintable node has specific hint. */
  // 访问者，用于验证可提示节点是否包含特定提示
  private static class ValidateHintVisitor extends RelVisitor { // ValidateHintVisitor类：验证提示访问者
    private final RelHint expectedHint; // 期望的提示
    private final Class<?> clazz; // 节点类型

    /**
     * Creates the validate visitor.
     *
     * @param hint  the hint to validate
     * @param clazz the node type to validate the hint with
     */
    // 创建验证访问者
    ValidateHintVisitor(RelHint hint, Class<?> clazz) { // 构造函数
      this.expectedHint = hint; // 保存期望的提示
      this.clazz = clazz; // 保存节点类型
    }

    @Override public void visit( // 访问方法
        RelNode node, // 节点
        int ordinal, // 序号
        @Nullable RelNode parent) { // 父节点
      if (clazz.isInstance(node)) { // 如果节点是指定类型
        Hintable rel = (Hintable) node; // 转换为Hintable
        assertThat(rel.getHints(), hasSize(1)); // 断言有1个提示
        assertThat(rel.getHints().get(0), is(expectedHint)); // 断言提示等于期望提示
      }
      super.visit(node, ordinal, parent); // 调用父类访问方法
    }
  }

  /** Test fixture. */
  // 测试夹具类
  private static class Fixture { // Fixture类：SQL测试夹具
    private final String sql; // SQL语句
    private final DiffRepository diffRepos; // 差异仓库
    private final SqlTestFactory factory; // SQL测试工厂
    private final SqlTester tester = SqlToRelFixture.TESTER; // SQL测试器
    private final List<String> hintsCollect = new ArrayList<>(); // 提示收集列表
    private final boolean decorrelate; // 是否启用去相关
    private final boolean trim; // 是否启用字段修剪

    Fixture(SqlTestFactory factory, DiffRepository diffRepos, String sql, // 构造函数
        boolean decorrelate, boolean trim) {
      this.factory = requireNonNull(factory, "factory"); // 验证工厂非空
      this.sql = requireNonNull(sql, "sql"); // 验证SQL非空
      this.diffRepos = requireNonNull(diffRepos, "diffRepos"); // 验证差异仓库非空
      this.decorrelate = decorrelate; // 保存去相关标志
      this.trim = trim; // 保存修剪标志
    }

    Fixture sql(String sql) { // 设置SQL语句
      return new Fixture(factory, diffRepos, sql, decorrelate, trim); // 返回新的Fixture实例
    }

    /** Creates a new Sql instance with new factory
     * applied with the {@code transform}. */
    // 使用转换函数创建新的SQL实例
    Fixture withFactory(UnaryOperator<SqlTestFactory> transform) { // 配置工厂
      final SqlTestFactory factory = transform.apply(this.factory); // 应用转换
      return new Fixture(factory, diffRepos, sql, decorrelate, trim); // 返回新的Fixture实例
    }

    Fixture withDecorrelate(boolean decorrelate) { // 设置去相关标志
      return new Fixture(factory, diffRepos, sql, decorrelate, trim); // 返回新的Fixture实例
    }

    void ok() { // 验证结果正确的方法
      assertHintsEquals(sql, "${hints}"); // 断言提示相等
    }


    private void assertHintsEquals( // 断言提示相等的私有方法
        String sql, // SQL语句
        String hint) { // 提示字符串
      diffRepos.assertEquals("sql", "${sql}", sql); // 比较SQL
      String sql2 = diffRepos.expand("sql", sql); // 展开SQL变量
      final RelNode rel = // 转换SQL为关系表达式
          tester.convertSqlToRel(factory, sql2, decorrelate, trim) // 调用转换方法
              .project(); // 投影

      assertNotNull(rel); // 断言关系表达式非空
      assertThat(rel, relIsValid()); // 断言关系表达式有效

      final HintCollector collector = new HintCollector(hintsCollect); // 创建提示收集器
      rel.accept(collector); // 接受访问者，收集提示
      StringBuilder builder = new StringBuilder(NL); // 创建字符串构建器
      for (String hintLine : hintsCollect) { // 遍历提示收集列表
        builder.append(hintLine).append(NL); // 添加提示行
      }
      diffRepos.assertEquals("hints", hint, builder.toString()); // 比较提示
    }

    void fails(String failedMsg) { // 验证失败的方法
      try { // 尝试执行
        tester.convertSqlToRel(factory, sql, decorrelate, trim); // 转换SQL
        fail("Unexpected exception"); // 如果没有异常，测试失败
      } catch (AssertionError e) { // 捕获断言错误
        assertThat(e.getMessage(), is(failedMsg)); // 断言错误消息匹配
      }
    }

    void warns(String expectWarning) { // 验证警告的方法
      MockAppender appender = new MockAppender(); // 创建模拟追加器
      MockLogger logger = new MockLogger(); // 创建模拟日志记录器
      logger.addAppender(appender); // 添加追加器到日志记录器
      try { // 尝试执行
        tester.convertSqlToRel(factory, sql, decorrelate, trim); // 转换SQL
      } finally { // 无论成功或失败
        logger.removeAppender(appender); // 移除追加器
      }
      appender.loggingEvents.add(expectWarning); // TODO: remove // 添加期望警告（TODO：移除）
      assertThat(expectWarning, is(in(appender.loggingEvents))); // 断言期望警告在日志事件中
    }

    SqlNode parseQuery() throws Exception { // 解析查询的方法
      return tester.parseQuery(factory, sql); // 解析SQL并返回SQL节点
    }

    RelNode toRel() { // 转换为关系表达式的方法
      return tester.convertSqlToRel(factory, sql, decorrelate, trim).rel; // 转换SQL并返回关系表达式
    }

    /** A shuttle to collect all the hints within the relational expression into a collection. */
    // 穿梭器，用于收集关系表达式中的所有提示
    private static class HintCollector extends RelShuttleImpl { // HintCollector类：提示收集器
      private final List<String> hintsCollect; // 提示收集列表

      HintCollector(List<String> hintsCollect) { // 构造函数
        this.hintsCollect = hintsCollect; // 保存提示收集列表
      }

      @Override public RelNode visit(TableScan scan) { // 访问表扫描节点
        if (!scan.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("TableScan:" + scan.getHints()); // 添加表扫描提示
        }
        return super.visit(scan); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalJoin join) { // 访问逻辑连接节点
        if (!join.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("LogicalJoin:" + join.getHints()); // 添加逻辑连接提示
        }
        return super.visit(join); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalProject project) { // 访问逻辑投影节点
        if (!project.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Project:" + project.getHints()); // 添加投影提示
        }
        return super.visit(project); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalAggregate aggregate) { // 访问逻辑聚合节点
        if (!aggregate.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Aggregate:" + aggregate.getHints()); // 添加聚合提示
        }
        return super.visit(aggregate); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalCorrelate correlate) { // 访问逻辑关联节点
        if (!correlate.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Correlate:" + correlate.getHints()); // 添加关联提示
        }
        return super.visit(correlate); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalFilter filter) { // 访问逻辑过滤节点
        if (!filter.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Filter:" + filter.getHints()); // 添加过滤提示
        }
        return super.visit(filter); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalUnion union) { // 访问逻辑并集节点
        if (!union.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Union:" + union.getHints()); // 添加并集提示
        }
        return super.visit(union); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalIntersect intersect) { // 访问逻辑交集节点
        if (!intersect.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Intersect:" + intersect.getHints()); // 添加交集提示
        }
        return super.visit(intersect); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalMinus minus) { // 访问逻辑差集节点
        if (!minus.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Minus:" + minus.getHints()); // 添加差集提示
        }
        return super.visit(minus); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalSort sort) { // 访问逻辑排序节点
        if (!sort.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Sort:" + sort.getHints()); // 添加排序提示
        }
        return super.visit(sort); // 调用父类访问方法
      }

      @Override public RelNode visit(LogicalValues values) { // 访问逻辑值节点
        if (!values.getHints().isEmpty()) { // 如果有提示
          this.hintsCollect.add("Values:" + values.getHints()); // 添加值提示
        }
        return super.visit(values); // 调用父类访问方法
      }

      @Override public RelNode visit(RelNode other) { // 访问其他关系节点
        if (other instanceof Window) { // 如果是窗口节点
          Window window = (Window) other; // 转换为Window
          if (!window.getHints().isEmpty()) { // 如果有提示
            this.hintsCollect.add("Window:" + window.getHints()); // 添加窗口提示
          }
        } else if (other instanceof Snapshot) { // 如果是快照节点
          Snapshot snapshot = (Snapshot) other; // 转换为Snapshot
          if (!snapshot.getHints().isEmpty()) { // 如果有提示
            this.hintsCollect.add("Snapshot:" + snapshot.getHints()); // 添加快照提示
          }
        } else if (other instanceof TableFunctionScan) { // 如果是表函数扫描节点
          TableFunctionScan scan = (TableFunctionScan) other; // 转换为TableFunctionScan
          if (!scan.getHints().isEmpty()) { // 如果有提示
            this.hintsCollect.add("TableFunctionScan:" + scan.getHints()); // 添加表函数扫描提示
          }
        }
        return super.visit(other); // 调用父类访问方法
      }
    }
  }

  /** Mock appender to collect the logging events. */
  // 模拟追加器，用于收集日志事件
  private static class MockAppender { // MockAppender类：模拟追加器
    final List<String> loggingEvents = new ArrayList<>(); // 日志事件列表

    void append(String event) { // 追加事件
      loggingEvents.add(event); // 添加到日志事件列表
    }
  }

  /** An utterly useless Logger; a placeholder so that the test compiles and
   * trivially succeeds. */
  // 一个完全无用的日志记录器；占位符，使测试能够编译和简单成功
  private static class MockLogger { // MockLogger类：模拟日志记录器
    void addAppender(MockAppender appender) { // 添加追加器
    } // 空实现

    void removeAppender(MockAppender appender) { // 移除追加器
    } // 空实现
  }

  /** Define some tool members and methods for hints test. */
  // 定义提示测试的工具成员和方法
  private static class HintTools { // HintTools类：提示工具类
    //~ Static fields/initializers ---------------------------------------------
    // 静态字段/初始化器区域开始

    static final String HINT = "properties(k1='v1', k2='v2'), index(ename), no_hash_join"; // 默认提示字符串

    static final RelHint PROPS_HINT = RelHint.builder("PROPERTIES") // 构建PROPERTIES提示
        .hintOption("K1", "v1") // 设置K1选项
        .hintOption("K2", "v2") // 设置K2选项
        .build(); // 构建提示

    static final RelHint IDX_HINT = RelHint.builder("INDEX") // 构建INDEX提示
        .hintOption("ENAME") // 设置ENAME选项
        .build(); // 构建提示

    static final RelHint JOIN_HINT = RelHint.builder("NO_HASH_JOIN").build(); // 构建NO_HASH_JOIN提示

    static final HintStrategyTable HINT_STRATEGY_TABLE = createHintStrategies(); // 创建提示策略表

    //~ Methods ----------------------------------------------------------------
    // 方法区域开始

    /**
     * Creates mock hint strategies.
     *
     * @return HintStrategyTable instance
     */
    // 创建模拟提示策略
    private static HintStrategyTable createHintStrategies() { // 创建提示策略表
      return createHintStrategies(HintStrategyTable.builder()); // 调用重载方法
    }

    /**
     * Creates mock hint strategies with given builder.
     *
     * @return HintStrategyTable instance
     */
    // 使用给定构建器创建模拟提示策略
    static HintStrategyTable createHintStrategies(HintStrategyTable.Builder builder) { // 创建提示策略表
      return builder // 返回构建的提示策略表
        .hintStrategy("no_hash_join", HintPredicates.JOIN) // 注册no_hash_join提示策略，适用于连接
        .hintStrategy("time_zone", HintPredicates.SET_VAR) // 注册time_zone提示策略，用于设置变量
        .hintStrategy("REPARTITION", HintPredicates.SET_VAR) // 注册REPARTITION提示策略，用于设置变量
        .hintStrategy("index", HintPredicates.TABLE_SCAN) // 注册index提示策略，适用于表扫描
        .hintStrategy("properties", HintPredicates.TABLE_SCAN) // 注册properties提示策略，适用于表扫描
        .hintStrategy( // 注册resource提示策略
            "resource", HintPredicates.or( // 使用OR谓词组合多个适用场景
            HintPredicates.PROJECT, HintPredicates.AGGREGATE, // 适用于投影和聚合
                HintPredicates.CALC, HintPredicates.VALUES, // 适用于计算和值
                HintPredicates.FILTER, HintPredicates.TABLE_FUNCTION_SCAN)) // 适用于过滤和表函数扫描
        .hintStrategy("AGG_STRATEGY", // 注册AGG_STRATEGY提示策略
            HintStrategy.builder(HintPredicates.AGGREGATE) // 适用于聚合
                .optionChecker( // 设置选项检查器
                    (hint, errorHandler) -> errorHandler.check( // 检查选项
                    hint.listOptions.size() == 1 // 只允许1个选项
                        && (hint.listOptions.get(0).equalsIgnoreCase("ONE_PHASE") // 选项必须是ONE_PHASE
                        || hint.listOptions.get(0).equalsIgnoreCase("TWO_PHASE")), // 或TWO_PHASE
                    "Hint {} only allows single option, " // 错误消息
                        + "allowed options: [ONE_PHASE, TWO_PHASE]", // 允许的选项
                    hint.hintName)).build()) // 使用提示名称
        .hintStrategy("use_hash_join", // 注册use_hash_join提示策略
          HintPredicates.or( // 使用OR谓词组合
              HintPredicates.and(HintPredicates.CORRELATE, temporalJoinWithFixedTableName()), // 时间旅行关联连接
              HintPredicates.and(HintPredicates.JOIN, joinWithFixedTableName()))) // 指定表名的连接
        .hintStrategy("breakable", HintPredicates.SETOP) // 注册breakable提示策略，适用于集合操作
        .hintStrategy("async_merge", HintPredicates.SORT) // 注册async_merge提示策略，适用于排序
        .hintStrategy("mini_batch", // 注册mini_batch提示策略
                HintPredicates.and(HintPredicates.WINDOW, HintPredicates.PROJECT)) // 适用于窗口和投影
        .hintStrategy("fast_snapshot", HintPredicates.SNAPSHOT) // 注册fast_snapshot提示策略，适用于快照
        .hintStrategy("use_merge_join", // 注册use_merge_join提示策略
            HintStrategy.builder( // 构建提示策略
                HintPredicates.and(HintPredicates.JOIN, joinWithFixedTableName())) // 适用于指定表名的连接
                .excludedRules(EnumerableRules.ENUMERABLE_JOIN_RULE).build()) // 排除可枚举连接规则
              .hintStrategy( // 注册preserved_project提示策略
                  "preserved_project", HintStrategy.builder( // 构建提示策略
               HintPredicates.PROJECT).excludedRules(CoreRules.FILTER_PROJECT_TRANSPOSE).build()) // 排除过滤投影转置规则
        .build(); // 构建提示策略表
    }

    /** Returns a {@link HintPredicate} for temporal join with specified table references. */
    // 返回指定表引用的时间旅行连接的提示谓词
    private static HintPredicate temporalJoinWithFixedTableName() { // 时间旅行连接谓词
      return (hint, rel) -> { // 返回谓词函数
        if (!(rel instanceof LogicalCorrelate)) { // 如果不是关联节点
          return false; // 返回false
        }
        LogicalCorrelate correlate = (LogicalCorrelate) rel; // 转换为关联节点
        Predicate<RelNode> isScan = r -> r instanceof TableScan; // 表扫描谓词
        if (!(isScan.test(correlate.getLeft()))) { // 如果左输入不是表扫描
          return false; // 返回false
        }
        RelNode rightInput = correlate.getRight(); // 获取右输入
        Predicate<RelNode> isSnapshotOnScan = r -> r instanceof Snapshot // 快照扫描谓词
            && isScan.test(((Snapshot) r).getInput()); // 快照的输入是表扫描
        RelNode rightScan; // 右扫描节点
        if (isSnapshotOnScan.test(rightInput)) { // 如果右输入是快照扫描
          rightScan = ((Snapshot) rightInput).getInput(); // 获取扫描节点
        } else if (rightInput instanceof Filter // 如果右输入是过滤节点
            && isSnapshotOnScan.test(((Filter) rightInput).getInput())) { // 且过滤的输入是快照扫描
          rightScan = ((Snapshot) ((Filter) rightInput).getInput()).getInput(); // 获取扫描节点
        } else { // 否则
          // right child of correlate must be a snapshot on table scan directly or a Filter which
          // input is snapshot on table scan
          // 关联的右子节点必须是表扫描上的快照，或者输入是表扫描上快照的过滤节点
          return false; // 返回false
        }
        final List<String> tableNames = hint.listOptions; // 获取提示中的表名列表
        final List<String> inputTables = Stream.of(correlate.getLeft(), rightScan) // 获取输入表流
            .map(scan -> Util.last(scan.getTable().getQualifiedName())) // 提取表名
            .collect(Collectors.toList()); // 收集为列表
        return equalsStringList(inputTables, tableNames); // 比较表名列表
      };
    }

    /** Returns a {@link HintPredicate} for join with specified table references. */
    // 返回指定表引用的连接的提示谓词
    private static HintPredicate joinWithFixedTableName() { // 连接谓词
      return (hint, rel) -> { // 返回谓词函数
        if (!(rel instanceof LogicalJoin)) { // 如果不是连接节点
          return false; // 返回false
        }
        LogicalJoin join = (LogicalJoin) rel; // 转换为连接节点
        final List<String> tableNames = hint.listOptions; // 获取提示中的表名列表
        final List<String> inputTables = join.getInputs().stream() // 获取输入表流
            .filter(input -> input instanceof TableScan) // 过滤出表扫描节点
            .map(scan -> Util.last(scan.getTable().getQualifiedName())) // 提取表名
            .collect(Collectors.toList()); // 收集为列表
        return equalsStringList(tableNames, inputTables); // 比较表名列表
      };
    }

    /** Format the query with hint {@link #HINT}. */
    // 使用提示格式化查询
    static String withHint(String sql) { // 格式化SQL提示
      return String.format(Locale.ROOT, sql, HINT); // 使用ROOT地区格式化字符串
    }
  }
}