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
// Apache许可证2.0：本文件遵循Apache软件基金会开源许可证2.0版本
// 许可证允许：商业使用、修改、分发、私人使用
// 限制条件：必须包含许可证和版权声明、必须说明变更、不能使用作者名义背书
 */
package org.apache.calcite.rel.logical;

// 导入可枚举约定：表示可枚举的物理实现约定
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
// 导入可枚举规则：包含将逻辑节点转换为可枚举物理节点的规则集合
import org.apache.calcite.adapter.enumerable.EnumerableRules;
// 导入关系优化器：用于执行查询优化和规则匹配
import org.apache.calcite.plan.RelOptPlanner;
// 导入关系优化规则：定义了如何转换关系表达式的规则接口
import org.apache.calcite.plan.RelOptRule;
// 导入关系节点：关系表达式树的基类
import org.apache.calcite.rel.RelNode;
// 导入连接关系类型：定义连接类型（INNER、LEFT、RIGHT、FULL等）
import org.apache.calcite.rel.core.JoinRelType;
// 导入核心规则：包含Calcite的核心优化规则
import org.apache.calcite.rel.rules.CoreRules;
// 导入相关变量：用于相关子查询中的变量表示
import org.apache.calcite.rex.RexCorrelVariable;
// 导入SchemaPlus：Calcite的Schema接口，用于管理表和函数
import org.apache.calcite.schema.SchemaPlus;
// 导入SQL节点：表示SQL语句的抽象语法树节点
import org.apache.calcite.sql.SqlNode;
// 导入SQL标准操作符表：包含标准SQL操作符（如=、+、-等）
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
// 导入SQL到关系转换器：将SQL语句转换为关系表达式
import org.apache.calcite.sql2rel.SqlToRelConverter;
// 导入Calcite断言工具：提供测试用的Schema和工具方法
import org.apache.calcite.test.CalciteAssert;
// 导入RelBuilder测试工具：提供测试用的RelBuilder配置
import org.apache.calcite.test.RelBuilderTest;
// 导入框架配置：Calcite框架的配置接口
import org.apache.calcite.tools.FrameworkConfig;
// 导入框架工具：用于创建Calcite框架实例的工具类
import org.apache.calcite.tools.Frameworks;
// 导入规划器接口：用于解析、验证和优化SQL查询
import org.apache.calcite.tools.Planner;
// 导入程序接口：表示一个优化程序，可以执行一系列规则
import org.apache.calcite.tools.Program;
// 导入程序工具：提供常用的优化程序实现
import org.apache.calcite.tools.Programs;
// 导入关系构建器：用于以编程方式构建关系表达式树
import org.apache.calcite.tools.RelBuilder;
// 导入规则集工具：用于创建规则集
import org.apache.calcite.tools.RuleSets;
// 导入Holder：用于持有可变值的容器类
import org.apache.calcite.util.Holder;
// 导入测试工具：提供测试用的工具方法
import org.apache.calcite.util.TestUtil;

// 导入Google Guava的不可变列表：提供不可变的列表实现
import com.google.common.collect.ImmutableList;
// 导入Google Guava的不可变集合：提供不可变的集合实现
import com.google.common.collect.ImmutableSet;

// 导入可空注解：用于标记可能为null的参数
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入JUnit5的Test注解：标记测试方法
import org.junit.jupiter.api.Test;

// 导入Locale类：用于本地化相关的操作
import java.util.Locale;

// 导入hasTree匹配器：用于验证关系表达式树的匹配器
import static org.apache.calcite.test.Matchers.hasTree;

// 导入is匹配器：Hamcrest的相等匹配器
import static org.hamcrest.CoreMatchers.is;
// 导入断言工具：Hamcrest的断言工具类
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link ToLogicalConverter}.
 */
// ToLogicalConverterTest类：ToLogicalConverter转换器的完整测试套件
// 
// 【类的作用】
// 该测试类用于验证ToLogicalConverter转换器的正确性。ToLogicalConverter是一个关键组件，
// 负责将物理关系表达式（使用EnumerableConvention等物理约定）转换回逻辑关系表达式
// （使用LogicalConvention逻辑约定）。这种转换在多种场景下非常重要：
// 1. 优化器调试：允许开发者查看逻辑计划与物理计划的对比
// 2. 计划验证：确保物理转换没有改变查询的语义
// 3. 跨系统互操作：某些系统可能需要逻辑计划而不是物理计划
// 4. 计划重写：在物理计划之上应用额外的逻辑优化规则
//
// 【测试覆盖范围】
// 该测试类覆盖了Calcite中所有主要的关系操作符：
// - Values：常量值集合（testValues）
// - TableScan：表扫描（testScan）
// - Project：投影/选择字段（testProject）
// - Filter：过滤条件（testFilter）
// - Sort：排序（testSort）
// - Limit：限制结果数量（testLimit）
// - Sort+Limit：排序和限制的组合（testSortLimit）
// - Aggregate：聚合函数和分组（testAggregate）
// - Join：连接操作（testJoin）
// - Correlate：相关子查询（testCorrelation）
// - Union：联合操作（testUnion）
// - Intersect：交集操作（testIntersect）
// - Minus：差集操作（testMinus）
// - Uncollect：取消集合/展开数组（testUncollect）
// - Window：窗口函数（testWindow、testWindowExclude）
// - TableModify：表修改操作（testTableModify）
//
// 【测试方法】
// 每个测试方法都遵循相同的模式：
// 1. 构建一个逻辑关系表达式（使用RelBuilder或SQL）
// 2. 使用toPhysical方法将逻辑表达式转换为物理表达式
// 3. 使用toLogical方法将物理表达式转换回逻辑表达式
// 4. 验证物理计划和逻辑计划的树结构是否符合预期
//
// 【关键组件】
// - RULE_SET：包含所有用于逻辑到物理转换的规则
// - frameworkConfig()：创建Calcite框架配置
// - builder()：创建RelBuilder实例
// - rel(String sql)：将SQL转换为关系表达式
// - toPhysical(RelNode rel)：将逻辑计划转换为物理计划
// - toLogical(RelNode rel)：将物理计划转换回逻辑计划
// - verify(...)：验证转换结果的正确性
//
// 【重要概念】
// - LogicalConvention：逻辑约定，表示逻辑层面的关系表达式
// - EnumerableConvention：物理约定，表示可枚举的物理实现
// - RelNode：关系表达式节点，构成关系代数树的节点
// - RelBuilder：关系表达式构建器，用于以编程方式构建关系表达式树
// - TraitSet：特征集合，描述关系表达式的属性（如约定、排序、分布等）
class ToLogicalConverterTest {
  // RULE_SET：规则集，包含用于将逻辑计划转换为物理计划的所有规则
  // 这些规则包括：
  // - PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW：将Project转换为LogicalProject和LogicalWindow
  // - ENUMERABLE_VALUES_RULE：将LogicalValues转换为EnumerableValues
  // - ENUMERABLE_JOIN_RULE：将LogicalJoin转换为EnumerableHashJoin等
  // - ENUMERABLE_CORRELATE_RULE：将LogicalCorrelate转换为EnumerableCorrelate
  // - ENUMERABLE_PROJECT_RULE：将LogicalProject转换为EnumerableProject
  // - ENUMERABLE_FILTER_RULE：将LogicalFilter转换为EnumerableFilter
  // - ENUMERABLE_AGGREGATE_RULE：将LogicalAggregate转换为EnumerableAggregate
  // - ENUMERABLE_SORT_RULE：将LogicalSort转换为EnumerableSort
  // - ENUMERABLE_LIMIT_RULE：将LogicalSort的limit部分转换为EnumerableLimit
  // - ENUMERABLE_COLLECT_RULE：将LogicalCollect转换为EnumerableCollect
  // - ENUMERABLE_UNCOLLECT_RULE：将LogicalUncollect转换为EnumerableUncollect
  // - ENUMERABLE_UNION_RULE：将LogicalUnion转换为EnumerableUnion
  // - ENUMERABLE_INTERSECT_RULE：将LogicalIntersect转换为EnumerableIntersect
  // - ENUMERABLE_MINUS_RULE：将LogicalMinus转换为EnumerableMinus
  // - ENUMERABLE_WINDOW_RULE：将LogicalWindow转换为EnumerableWindow
  // - ENUMERABLE_TABLE_SCAN_RULE：将LogicalTableScan转换为EnumerableTableScan
  // - TO_INTERPRETER：将可枚举计划转换为解释器计划
  private static final ImmutableSet<RelOptRule> RULE_SET =
      ImmutableSet.of(
          CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW,
          EnumerableRules.ENUMERABLE_VALUES_RULE,
          EnumerableRules.ENUMERABLE_JOIN_RULE,
          EnumerableRules.ENUMERABLE_CORRELATE_RULE,
          EnumerableRules.ENUMERABLE_PROJECT_RULE,
          EnumerableRules.ENUMERABLE_FILTER_RULE,
          EnumerableRules.ENUMERABLE_AGGREGATE_RULE,
          EnumerableRules.ENUMERABLE_SORT_RULE,
          EnumerableRules.ENUMERABLE_LIMIT_RULE,
          EnumerableRules.ENUMERABLE_COLLECT_RULE,
          EnumerableRules.ENUMERABLE_UNCOLLECT_RULE,
          EnumerableRules.ENUMERABLE_UNION_RULE,
          EnumerableRules.ENUMERABLE_INTERSECT_RULE,
          EnumerableRules.ENUMERABLE_MINUS_RULE,
          EnumerableRules.ENUMERABLE_WINDOW_RULE,
          EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
          EnumerableRules.TO_INTERPRETER);

  // DEFAULT_REL_CONFIG：SQL到关系表达式转换器的默认配置
  // withTrimUnusedFields(false)：不修剪未使用的字段，保持字段完整性
  private static final SqlToRelConverter.Config DEFAULT_REL_CONFIG =
      SqlToRelConverter.config().withTrimUnusedFields(false);

  // frameworkConfig方法：创建框架配置对象
  // 返回值：FrameworkConfig对象，包含Calcite框架运行所需的所有配置
  // 功能：
  // 1. 创建根Schema（true表示添加内置函数）
  // 2. 添加JDBC_FOODMART测试Schema到根Schema
  // 3. 构建配置，设置默认Schema和SQL到关系转换器配置
  private static FrameworkConfig frameworkConfig() {
    // 创建根Schema，true表示添加Calcite内置函数
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true);
    // 添加JDBC_FOODMART测试Schema（包含employee、department等测试表）
    final SchemaPlus schema =
        CalciteAssert.addSchema(rootSchema,
            CalciteAssert.SchemaSpec.JDBC_FOODMART);
    // 构建框架配置，设置默认Schema和SQL转换配置
    return Frameworks.newConfigBuilder()
        .defaultSchema(schema)
        .sqlToRelConverterConfig(DEFAULT_REL_CONFIG)
        .build();
  }

  // builder方法：创建关系表达式构建器
  // 返回值：RelBuilder对象，用于以编程方式构建关系表达式树
  // 功能：使用RelBuilderTest的配置创建一个新的RelBuilder实例
  private static RelBuilder builder() {
    return RelBuilder.create(RelBuilderTest.config().build());
  }

  // rel方法：将SQL字符串转换为关系表达式
  // 参数sql：要解析的SQL语句字符串
  // 返回值：RelNode对象，表示SQL语句对应的关系表达式树
  // 功能：
  // 1. 创建Planner实例
  // 2. 解析SQL字符串为SqlNode（语法树）
  // 3. 验证SqlNode（语义分析）
  // 4. 将验证后的SqlNode转换为RelNode（关系表达式树）
  // 5. 捕获异常并重新抛出
  private static RelNode rel(String sql) {
    // 获取框架配置对应的Planner实例
    final Planner planner = Frameworks.getPlanner(frameworkConfig());
    try {
      // 解析SQL字符串为抽象语法树（SqlNode）
      SqlNode parse = planner.parse(sql);
      // 验证SQL语句的语义（表名、字段名、类型检查等）
      SqlNode validate = planner.validate(parse);
      // 将验证后的SQL转换为关系表达式树（RelNode）
      return planner.rel(validate).rel;
    } catch (Exception e) {
      // 捕获异常并重新抛出，保留原始异常类型
      throw TestUtil.rethrow(e);
    }
  }

  // toPhysical方法：将逻辑关系表达式转换为物理关系表达式
  // 参数rel：输入的逻辑关系表达式
  // 返回值：物理关系表达式（使用EnumerableConvention）
  // 功能：
  // 1. 获取关系表达式对应的优化器
  // 2. 清空优化器状态
  // 3. 添加所有转换规则到优化器
  // 4. 使用Program执行优化，将逻辑计划转换为物理计划
  // 5. 将TraitSet替换为EnumerableConvention（表示可枚举的物理实现）
  private static RelNode toPhysical(RelNode rel) {
    // 获取关系表达式所在Cluster的优化器
    final RelOptPlanner planner = rel.getCluster().getPlanner();
    // 清空优化器之前的状态
    planner.clear();
    // 将所有转换规则添加到优化器
    for (RelOptRule rule : RULE_SET) {
      planner.addRule(rule);
    }

    // 创建Program对象，用于执行优化过程
    final Program program = Programs.of(RuleSets.ofList(planner.getRules()));
    // 运行优化程序，将逻辑计划转换为物理计划
    // replace(EnumerableConvention.INSTANCE)将TraitSet替换为可枚举约定
    return program.run(planner, rel, rel.getTraitSet().replace(EnumerableConvention.INSTANCE),
        ImmutableList.of(), ImmutableList.of());
  }

  // toLogical方法：将物理关系表达式转换回逻辑关系表达式
  // 参数rel：输入的物理关系表达式
  // 返回值：逻辑关系表达式
  // 功能：使用ToLogicalConverter访问器遍历物理计划树，将每个物理节点转换为对应的逻辑节点
  private static RelNode toLogical(RelNode rel) {
    // 创建ToLogicalConverter实例，传入RelBuilder用于构建新的逻辑节点
    // accept方法启动访问者模式遍历，将物理节点转换为逻辑节点
    return rel.accept(new ToLogicalConverter(builder()));
  }

  // verify方法：验证关系表达式的物理和逻辑转换是否正确
  // 参数rel：输入的关系表达式（通常是逻辑表达式）
  // 参数expectedPhysical：预期的物理计划树字符串表示
  // 参数expectedLogical：预期的逻辑计划树字符串表示
  // 功能：
  // 1. 将输入关系表达式转换为物理计划
  // 2. 将物理计划转换回逻辑计划
  // 3. 验证物理计划是否符合预期
  // 4. 验证逻辑计划是否符合预期
  private void verify(RelNode rel, String expectedPhysical, String expectedLogical) {
    // 将逻辑表达式转换为物理表达式
    RelNode physical = toPhysical(rel);
    // 将物理表达式转换回逻辑表达式
    RelNode logical = toLogical(physical);
    // 验证物理计划的树结构是否符合预期
    assertThat(physical, hasTree(expectedPhysical));
    // 验证逻辑计划的树结构是否符合预期
    assertThat(logical, hasTree(expectedLogical));
  }

  @Test void testValues() {
    // Equivalent SQL:
    //   VALUES (true, 1), (false, -50) AS t(a, b)
    // testValues方法：测试VALUES操作符的转换
    // 功能：验证LogicalValues和EnumerableValues之间的相互转换
    final RelBuilder builder = builder();
    // 使用RelBuilder构建Values关系表达式
    // values方法：创建一个包含两行数据的Values节点
    // new String[]{"a", "b"}：指定列名为a和b
    // true, 1, false, -50：两行数据，第一行(true, 1)，第二行(false, -50)
    final RelNode rel =
        builder
            .values(new String[]{"a", "b"}, true, 1, false, -50)
            .build();
    // 验证转换结果：
    // 1. 物理计划应该是EnumerableValues
    // 2. 逻辑计划应该是LogicalValues
    // 3. 两者都应该包含相同的数据元组
    verify(rel,
        "EnumerableValues(tuples=[[{ true, 1 }, { false, -50 }]])\n",
        "LogicalValues(tuples=[[{ true, 1 }, { false, -50 }]])\n");
  }

  @Test void testScan() {
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    // testScan方法：测试表扫描操作符的转换
    // 功能：验证LogicalTableScan和EnumerableTableScan之间的相互转换
    final RelNode rel =
        builder()
            .scan("EMP")  // 扫描EMP表（来自scott schema）
            .build();
    // 验证转换结果：
    // 1. 物理计划应该是EnumerableTableScan，扫描scott.EMP表
    // 2. 逻辑计划应该是LogicalTableScan，扫描scott.EMP表
    verify(rel,
        "EnumerableTableScan(table=[[scott, EMP]])\n",
        "LogicalTableScan(table=[[scott, EMP]])\n");
  }

  @Test void testProject() {
    // Equivalent SQL:
    //   SELECT deptno
    //   FROM emp
    // testProject方法：测试投影操作符的转换
    // 功能：验证LogicalProject和EnumerableProject之间的相互转换
    final RelBuilder builder = builder();
    // 构建关系表达式：扫描EMP表，然后投影出DEPTNO字段
    final RelNode rel =
        builder.scan("EMP")
            .project(builder.field("DEPTNO"))  // 只输出DEPTNO字段
            .build();
    // 定义预期的物理计划：EnumerableProject + EnumerableTableScan
    // DEPTNO=[$7]：输出字段DEPTNO，对应输入的第7个字段（0-based索引）
    String expectedPhysical = ""
        + "EnumerableProject(DEPTNO=[$7])\n"
        + "  EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalProject + LogicalTableScan
    String expectedLogical = ""
        + "LogicalProject(DEPTNO=[$7])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testFilter() {
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   WHERE deptno = 10
    // testFilter方法：测试过滤操作符的转换
    // 功能：验证LogicalFilter和EnumerableFilter之间的相互转换
    final RelBuilder builder = builder();
    // 构建关系表达式：扫描EMP表，然后过滤出DEPTNO=10的记录
    final RelNode rel =
        builder.scan("EMP")
            .filter(
                builder.equals(  // 创建等于条件表达式
                    builder.field("DEPTNO"),  // DEPTNO字段
                    builder.literal(10)))  // 字面量10
            .build();
    // 定义预期的物理计划：EnumerableFilter + EnumerableTableScan
    // condition=[=($7, 10)]：过滤条件是第7个字段等于10
    String expectedPhysical = ""
        + "EnumerableFilter(condition=[=($7, 10)])\n"
        + "  EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalFilter + LogicalTableScan
    String expectedLogical = ""
        + "LogicalFilter(condition=[=($7, 10)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testSort() {
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY 3
    // testSort方法：测试排序操作符的转换
    // 功能：验证LogicalSort和EnumerableSort之间的相互转换
    final RelBuilder builder = builder();
    // 构建关系表达式：扫描EMP表，然后按第3个字段（索引2）升序排序
    final RelNode rel =
        builder.scan("EMP")
            .sort(builder.field(2))  // 按索引为2的字段（第3列）排序
            .build();
    // 定义预期的物理计划：EnumerableSort + EnumerableTableScan
    // sort0=[$2], dir0=[ASC]：按第2个字段升序排序
    String expectedPhysical = ""
        + "EnumerableSort(sort0=[$2], dir0=[ASC])\n"
        + "  EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalSort + LogicalTableScan
    String expectedLogical = ""
        + "LogicalSort(sort0=[$2], dir0=[ASC])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testLimit() {
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   FETCH 10
    // testLimit方法：测试限制操作符的转换
    // 功能：验证Limit操作符在物理和逻辑计划中的表示差异
    // 注意：物理计划使用EnumerableLimit，逻辑计划使用LogicalSort（带fetch参数）
    final RelBuilder builder = builder();
    // 构建关系表达式：扫描EMP表，然后限制返回前10条记录
    final RelNode rel =
        builder.scan("EMP")
            .limit(0, 10)  // offset=0（不跳过），fetch=10（取前10条）
            .build();
    // 定义预期的物理计划：EnumerableLimit + EnumerableTableScan
    // fetch=[10]：只取前10条记录
    String expectedPhysical = ""
        + "EnumerableLimit(fetch=[10])\n"
        + "  EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalSort（带fetch参数）+ LogicalTableScan
    // 注意：在逻辑计划中，Limit被表示为LogicalSort的fetch属性
    String expectedLogical = ""
        + "LogicalSort(fetch=[10])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testSortLimit() {
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   ORDER BY deptno DESC FETCH 10
    // testSortLimit方法：测试排序+限制组合操作符的转换
    // 功能：验证Sort和Limit组合在物理和逻辑计划中的表示差异
    // 注意：物理计划将Sort和Limit分离为两个节点，逻辑计划合并为一个LogicalSort节点
    final RelBuilder builder = builder();
    // 构建关系表达式：扫描EMP表，按DEPTNO降序排序，然后取前10条
    final RelNode rel =
        builder.scan("EMP")
            .sortLimit(-1, 10, builder.desc(builder.field("DEPTNO")))  // offset=-1（忽略），fetch=10，按DEPTNO降序
            .build();
    // 定义预期的物理计划：EnumerableLimit + EnumerableSort + EnumerableTableScan
    // 物理计划将Sort和Limit分离为两个独立的节点
    String expectedPhysical = ""
        + "EnumerableLimit(fetch=[10])\n"
        + "  EnumerableSort(sort0=[$7], dir0=[DESC])\n"
        + "    EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalSort（同时包含sort和fetch）+ LogicalTableScan
    // 逻辑计划将Sort和Limit合并为一个LogicalSort节点
    String expectedLogical = ""
        + "LogicalSort(sort0=[$7], dir0=[DESC], fetch=[10])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testAggregate() {
    // Equivalent SQL:
    //   SELECT deptno, COUNT(sal) AS c
    //   FROM emp
    //   GROUP BY deptno
    // testAggregate方法：测试聚合操作符的转换
    // 功能：验证LogicalAggregate和EnumerableAggregate之间的相互转换
    final RelBuilder builder = builder();
    // 构建关系表达式：扫描EMP表，按DEPTNO分组，计算每组的SAL数量
    final RelNode rel =
        builder.scan("EMP")
            .aggregate(builder.groupKey(builder.field("DEPTNO")),  // 分组键：DEPTNO字段
                builder.count(false, "C", builder.field("SAL")))  // 聚合函数：COUNT(SAL)，别名为C，false表示不忽略null
            .build();
    // 定义预期的物理计划：EnumerableAggregate + EnumerableTableScan
    // group=[{7}]：按第7个字段分组
    // C=[COUNT($5)]：计算第5个字段的计数，别名为C
    String expectedPhysical = ""
        + "EnumerableAggregate(group=[{7}], C=[COUNT($5)])\n"
        + "  EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalAggregate + LogicalTableScan
    String expectedLogical = ""
        + "LogicalAggregate(group=[{7}], C=[COUNT($5)])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testJoin() {
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   JOIN dept ON emp.deptno = dept.deptno
    // testJoin方法：测试连接操作符的转换
    // 功能：验证LogicalJoin和EnumerableHashJoin之间的相互转换
    // 注意：物理计划使用HashJoin实现（基于哈希的连接算法），逻辑计划只表示连接逻辑
    final RelBuilder builder = builder();
    // 构建关系表达式：扫描EMP和DEPT表，然后进行内连接
    final RelNode rel =
        builder.scan("EMP")  // 扫描EMP表
            .scan("DEPT")  // 扫描DEPT表
            .join(JoinRelType.INNER,  // 内连接
                builder.call(SqlStdOperatorTable.EQUALS,  // 连接条件：等于
                    builder.field(2, 0, "DEPTNO"),  // 左表（EMP）的DEPTNO字段（2个输入中的第0个输入的第0个字段）
                    builder.field(2, 1, "DEPTNO")))  // 右表（DEPT）的DEPTNO字段（2个输入中的第1个输入的第0个字段）
            .build();
    // 定义预期的物理计划：EnumerableHashJoin + 两个EnumerableTableScan
    // condition=[=($7, $8)]：连接条件是左表的第7个字段等于右表的第8个字段
    // joinType=[inner]：内连接
    String expectedPhysical = ""
        + "EnumerableHashJoin(condition=[=($7, $8)], joinType=[inner])\n"
        + "  EnumerableTableScan(table=[[scott, EMP]])\n"
        + "  EnumerableTableScan(table=[[scott, DEPT]])\n";
    // 定义预期的逻辑计划：LogicalJoin + 两个LogicalTableScan
    String expectedLogical = ""
        + "LogicalJoin(condition=[=($7, $8)], joinType=[inner])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n"
        + "  LogicalTableScan(table=[[scott, DEPT]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testDeepEquals() {
    // Equivalent SQL:
    //   SELECT *
    //   FROM emp
    //   JOIN dept ON emp.deptno = dept.deptno
    // testDeepEquals方法：测试关系表达式的深度相等性比较
    // 功能：验证RelNode的equals、deepEquals和deepHashCode方法的行为
    // 说明：
    // - equals()：默认使用对象标识比较（引用相等），两个相同的RelNode对象返回false
    // - deepEquals()：深度比较，比较RelNode的结构和内容，相同的RelNode返回true
    // - deepHashCode()：深度哈希码，用于深度相等对象的哈希比较
    final RelBuilder builder = builder();
    // 创建两个相同的关系表达式数组
    RelNode[] rels = new RelNode[2];
    for (int i = 0; i < 2; i++) {
      // 构建相同的Join关系表达式两次
      rels[i] = builder.scan("EMP")
          .scan("DEPT")
          .join(JoinRelType.INNER,
              builder.call(SqlStdOperatorTable.EQUALS,
                  builder.field(2, 0, "DEPTNO"),
                  builder.field(2, 1, "DEPTNO")))
          .build();
    }

    // Currently, default implementation uses identity equals
    // 验证默认的equals方法使用对象标识比较（引用相等）
    // 两个结构相同但不同的对象，equals返回false
    assertThat(rels[0].equals(rels[1]), is(false));
    // 输入节点也是不同的对象，equals返回false
    assertThat(rels[0].getInput(0).equals(rels[1].getInput(0)), is(false));

    // Deep equals and hashCode check
    // 验证深度相等性：两个结构相同的RelNode，deepEquals返回true
    assertThat(rels[0].deepEquals(rels[1]), is(true));
    // 验证深度哈希码：深度相等的对象，deepHashCode应该相等
    assertThat(rels[0].deepHashCode() == rels[1].deepHashCode(), is(true));
  }

  @Test void testCorrelation() {
    // testCorrelation方法：测试相关子查询操作符的转换
    // 功能：验证LogicalCorrelate和EnumerableCorrelate之间的相互转换
    // 说明：Correlate用于处理相关子查询，允许右侧访问左侧的变量
    final RelBuilder builder = builder();
    // 创建Holder用于保存相关变量
    final Holder<@Nullable RexCorrelVariable> v = Holder.empty();
    // 构建相关连接关系表达式
    final RelNode rel = builder.scan("EMP")  // 扫描EMP表（左侧）
        .variable(v::set)  // 创建相关变量，保存到Holder中
        .scan("DEPT")  // 扫描DEPT表（右侧）
        .filter(
            builder.equals(builder.field(0), builder.field(v.get(), "DEPTNO")))  // 过滤条件：DEPT表的第0个字段等于相关变量的DEPTNO字段
        .join(JoinRelType.LEFT,  // 左连接
            builder.equals(builder.field(2, 0, "SAL"),  // 连接条件：EMP表的SAL字段
                builder.literal(1000)),  // 等于1000
            ImmutableSet.of(v.get().id))  // 相关变量ID集合
        .build();
    // 定义预期的物理计划：EnumerableCorrelate + EnumerableTableScan + EnumerableFilter + EnumerableFilter + EnumerableTableScan
    // correlation=[$cor0]：相关变量名称为$cor0
    // joinType=[left]：左连接
    // requiredColumns=[{5, 7}]：需要的列索引（SAL和DEPTNO）
    String expectedPhysical = ""
        + "EnumerableCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{5, 7}])\n"
        + "  EnumerableTableScan(table=[[scott, EMP]])\n"
        + "  EnumerableFilter(condition=[=($cor0.SAL, 1000)])\n"
        + "    EnumerableFilter(condition=[=($0, $cor0.DEPTNO)])\n"
        + "      EnumerableTableScan(table=[[scott, DEPT]])\n";
    // 定义预期的逻辑计划：LogicalCorrelate + LogicalTableScan + LogicalFilter + LogicalFilter + LogicalTableScan
    String expectedLogical = ""
        + "LogicalCorrelate(correlation=[$cor0], joinType=[left], requiredColumns=[{5, 7}])\n"
        + "  LogicalTableScan(table=[[scott, EMP]])\n"
        + "  LogicalFilter(condition=[=($cor0.SAL, 1000)])\n"
        + "    LogicalFilter(condition=[=($0, $cor0.DEPTNO)])\n"
        + "      LogicalTableScan(table=[[scott, DEPT]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testUnion() {
    // Equivalent SQL:
    //   SELECT deptno FROM emp
    //   UNION ALL
    //   SELECT deptno FROM dept
    // testUnion方法：测试联合操作符的转换
    // 功能：验证LogicalUnion和EnumerableUnion之间的相互转换
    // 说明：Union用于合并多个查询的结果集，all=true表示保留重复行
    final RelBuilder builder = builder();
    // 构建UNION ALL关系表达式
    RelNode rel =
        builder.scan("DEPT")  // 扫描DEPT表
            .project(builder.field("DEPTNO"))  // 投影DEPTNO字段
            .scan("EMP")  // 扫描EMP表
            .project(builder.field("DEPTNO"))  // 投影DEPTNO字段
            .union(true)  // UNION ALL（保留重复行）
            .build();
    // 定义预期的物理计划：EnumerableUnion + 两个分支（每个分支包含Project和TableScan）
    // all=[true]：保留重复行
    String expectedPhysical = ""
        + "EnumerableUnion(all=[true])\n"
        + "  EnumerableProject(DEPTNO=[$0])\n"
        + "    EnumerableTableScan(table=[[scott, DEPT]])\n"
        + "  EnumerableProject(DEPTNO=[$7])\n"
        + "    EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalUnion + 两个分支
    String expectedLogical = ""
        + "LogicalUnion(all=[true])\n"
        + "  LogicalProject(DEPTNO=[$0])\n"
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"
        + "  LogicalProject(DEPTNO=[$7])\n"
        + "    LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testIntersect() {
    // Equivalent SQL:
    //   SELECT deptno FROM emp
    //   INTERSECT ALL
    //   SELECT deptno FROM dept
    // testIntersect方法：测试交集操作符的转换
    // 功能：验证LogicalIntersect和EnumerableIntersect之间的相互转换
    // 说明：Intersect用于返回多个查询结果的交集，all=true表示保留重复行
    final RelBuilder builder = builder();
    // 构建INTERSECT ALL关系表达式
    RelNode rel =
        builder.scan("DEPT")  // 扫描DEPT表
            .project(builder.field("DEPTNO"))  // 投影DEPTNO字段
            .scan("EMP")  // 扫描EMP表
            .project(builder.field("DEPTNO"))  // 投影DEPTNO字段
            .intersect(true)  // INTERSECT ALL（保留重复行）
            .build();
    // 定义预期的物理计划：EnumerableIntersect + 两个分支
    // all=[true]：保留重复行
    String expectedPhysical = ""
        + "EnumerableIntersect(all=[true])\n"
        + "  EnumerableProject(DEPTNO=[$0])\n"
        + "    EnumerableTableScan(table=[[scott, DEPT]])\n"
        + "  EnumerableProject(DEPTNO=[$7])\n"
        + "    EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalIntersect + 两个分支
    String expectedLogical = ""
        + "LogicalIntersect(all=[true])\n"
        + "  LogicalProject(DEPTNO=[$0])\n"
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"
        + "  LogicalProject(DEPTNO=[$7])\n"
        + "    LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testMinus() {
    // Equivalent SQL:
    //   SELECT deptno FROM emp
    //   EXCEPT ALL
    //   SELECT deptno FROM dept
    // testMinus方法：测试差集操作符的转换
    // 功能：验证LogicalMinus和EnumerableMinus之间的相互转换
    // 说明：Minus（EXCEPT）用于返回第一个查询结果中不存在于第二个查询结果的行，all=true表示保留重复行
    final RelBuilder builder = builder();
    // 构建EXCEPT ALL关系表达式
    RelNode rel =
        builder.scan("DEPT")  // 扫描DEPT表
            .project(builder.field("DEPTNO"))  // 投影DEPTNO字段
            .scan("EMP")  // 扫描EMP表
            .project(builder.field("DEPTNO"))  // 投影DEPTNO字段
            .minus(true)  // EXCEPT ALL（保留重复行）
            .build();
    // 定义预期的物理计划：EnumerableMinus + 两个分支
    // all=[true]：保留重复行
    String expectedPhysical = ""
        + "EnumerableMinus(all=[true])\n"
        + "  EnumerableProject(DEPTNO=[$0])\n"
        + "    EnumerableTableScan(table=[[scott, DEPT]])\n"
        + "  EnumerableProject(DEPTNO=[$7])\n"
        + "    EnumerableTableScan(table=[[scott, EMP]])\n";
    // 定义预期的逻辑计划：LogicalMinus + 两个分支
    String expectedLogical = ""
        + "LogicalMinus(all=[true])\n"
        + "  LogicalProject(DEPTNO=[$0])\n"
        + "    LogicalTableScan(table=[[scott, DEPT]])\n"
        + "  LogicalProject(DEPTNO=[$7])\n"
        + "    LogicalTableScan(table=[[scott, EMP]])\n";
    // 验证转换结果
    verify(rel, expectedPhysical, expectedLogical);
  }

  @Test void testUncollect() {
    // testUncollect方法：测试取消集合操作符的转换
    // 功能：验证Uncollect操作符在物理和逻辑计划中的表示
    // 说明：Uncollect用于将数组或集合展开为多行数据（类似UNNEST）
    // SQL示例：从department表收集所有department_id，然后展开为多行
    final String sql = ""
        + "select did\n"
        + "from unnest(select collect(\"department_id\") as deptid"
        + "            from \"department\") as t(did)";
    // 定义预期的物理计划：EnumerableUncollect + EnumerableAggregate + JdbcToEnumerableConverter + JdbcProject + JdbcTableScan
    // EnumerableUncollect：将数组展开为多行
    // EnumerableAggregate：使用COLLECT函数将department_id聚合成数组
    // JdbcToEnumerableConverter：将JDBC计划转换为可枚举计划
    String expectedPhysical = ""
        + "EnumerableUncollect\n"
        + "  EnumerableAggregate(group=[{}], DEPTID=[COLLECT($0)])\n"
        + "    JdbcToEnumerableConverter\n"
        + "      JdbcProject(department_id=[$0])\n"
        + "        JdbcTableScan(table=[[foodmart, department]])\n";
    // 定义预期的逻辑计划：Uncollect + LogicalAggregate + LogicalProject + LogicalTableScan
    // 注意：逻辑计划中没有JdbcToEnumerableConverter，因为这是物理层面的转换
    String expectedLogical = ""
        + "Uncollect\n"
        + "  LogicalAggregate(group=[{}], DEPTID=[COLLECT($0)])\n"
        + "    LogicalProject(department_id=[$0])\n"
        + "      LogicalTableScan(table=[[foodmart, department]])\n";
    // 验证转换结果
    verify(rel(sql), expectedPhysical, expectedLogical);
  }

  @Test void testWindow() {
    // testWindow方法：测试窗口函数操作符的转换
    // 功能：验证LogicalWindow和EnumerableWindow之间的相互转换
    // 说明：Window用于执行窗口函数（如RANK、SUM、AVG等），支持OVER子句
    // SQL示例：按hire_date排序，计算每行的排名（RANK）
    String sql = "SELECT rank() over (order by \"hire_date\") FROM \"employee\"";
    // 定义预期的物理计划：EnumerableProject + EnumerableWindow + JdbcToEnumerableConverter + JdbcTableScan
    // EnumerableProject：投影窗口函数的结果（第17个字段）
    // EnumerableWindow：执行窗口函数，按第9个字段（hire_date）排序，计算RANK()
    // JdbcToEnumerableConverter：将JDBC计划转换为可枚举计划
    String expectedPhysical = ""
        + "EnumerableProject($0=[$17])\n"
        + "  EnumerableWindow(window#0=[window(order by [9] aggs [RANK()])])\n"
        + "    JdbcToEnumerableConverter\n"
        + "      JdbcTableScan(table=[[foodmart, employee]])\n";
    // 定义预期的逻辑计划：LogicalProject + LogicalWindow + LogicalTableScan
    String expectedLogical = ""
        + "LogicalProject($0=[$17])\n"
        + "  LogicalWindow(window#0=[window(order by [9] aggs [RANK()])])\n"
        + "    LogicalTableScan(table=[[foodmart, employee]])\n";
    // 验证转换结果
    verify(rel(sql), expectedPhysical, expectedLogical);
  }

  // testWindowExcludeImp方法：测试窗口函数的EXCLUDE子句转换（实现方法）
// 参数excludeClause：EXCLUDE子句的SQL文本（如"exclude current row"）
// 参数expectedExcludeString：预期的EXCLUDE字符串表示（如"EXCLUDE CURRENT ROW "）
// 功能：验证窗口函数中EXCLUDE子句在物理和逻辑计划中的正确表示
// 说明：EXCLUDE子句用于在窗口帧计算中排除某些行（CURRENT ROW、GROUP、TIES、NO OTHERS）
void testWindowExcludeImp(String excludeClause, String expectedExcludeString) {
    // forbiddenApiTest requires to use Locale even though it's no needed here.
    // 构建SQL语句：使用SUM窗口函数，按hire_date排序，窗口帧从UNBOUNDED PRECEDING到CURRENT ROW，并应用EXCLUDE子句
    String sql = String.format(Locale.ROOT, "SELECT sum(\"salary\") over (order by \"hire_date\" "
        + "rows between unbounded preceding and current row %s) FROM \"employee\"", excludeClause);
    // 定义预期的物理计划：包含EXCLUDE子句的EnumerableWindow
    String expectedPhysical =
        String.format(Locale.ROOT, "EnumerableProject($0=[$17])\n"
            + "  EnumerableWindow(window#0=[window(order by [9] rows between"
            + " UNBOUNDED PRECEDING and CURRENT ROW %saggs [SUM($11)])])\n"
            + "    JdbcToEnumerableConverter\n"
            + "      JdbcTableScan(table=[[foodmart, employee]])\n", expectedExcludeString);
    // 定义预期的逻辑计划：包含EXCLUDE子句的LogicalWindow
    String expectedLogical =
        String.format(Locale.ROOT, "LogicalProject($0=[$17])\n"
            + "  LogicalWindow(window#0=[window(order by [9] rows between"
            + " UNBOUNDED PRECEDING and CURRENT ROW %saggs [SUM($11)])])\n"
            + "    LogicalTableScan(table=[[foodmart, employee]])\n", expectedExcludeString);
    // 验证转换结果
    verify(rel(sql), expectedPhysical, expectedLogical);
  }

  @Test void testWindowExclude() {
    // testWindowExclude方法：测试窗口函数的各种EXCLUDE子句
    // 功能：验证四种EXCLUDE子句的正确转换
    // 说明：
    // - EXCLUDE CURRENT ROW：排除当前行
    // - EXCLUDE GROUP：排除当前行及其对等行
    // - EXCLUDE TIES：排除与当前行值相同的行（除了当前行本身）
    // - EXCLUDE NO OTHERS：不排除任何行（默认行为，字符串表示为空）
    testWindowExcludeImp("exclude current row", "EXCLUDE CURRENT ROW ");  // 测试EXCLUDE CURRENT ROW
    testWindowExcludeImp("exclude group", "EXCLUDE GROUP ");  // 测试EXCLUDE GROUP
    testWindowExcludeImp("exclude ties", "EXCLUDE TIES ");  // 测试EXCLUDE TIES
    testWindowExcludeImp("exclude no others", "");  // 测试EXCLUDE NO OTHERS（预期字符串为空）
  }

  @Test void testTableModify() {
    // testTableModify方法：测试表修改操作符的转换
    // 功能：验证LogicalTableModify和JdbcTableModify之间的相互转换
    // 说明：TableModify用于执行INSERT、UPDATE、DELETE等数据修改操作
    // SQL示例：将employee表的所有数据插入到employee表（自插入）
    final String sql = "insert into \"employee\" select * from \"employee\"";
    // 定义预期的物理计划：JdbcToEnumerableConverter + JdbcTableModify + JdbcTableScan
    // JdbcTableModify：执行INSERT操作，flattened=true表示扁平化处理
    // JdbcTableScan：扫描employee表获取要插入的数据
    final String expectedPhysical = ""
        + "JdbcToEnumerableConverter\n"
        + "  JdbcTableModify(table=[[foodmart, employee]], operation=[INSERT], flattened=[true])\n"
        + "    JdbcTableScan(table=[[foodmart, employee]])\n";
    // 定义预期的逻辑计划：LogicalTableModify + LogicalTableScan
    // 注意：逻辑计划中没有JdbcToEnumerableConverter
    final String expectedLogical = ""
        + "LogicalTableModify(table=[[foodmart, employee]], "
        + "operation=[INSERT], flattened=[true])\n"
        + "  LogicalTableScan(table=[[foodott, employee]])\n";
    // 验证转换结果
    verify(rel(sql), expectedPhysical, expectedLogical);
  }

}
