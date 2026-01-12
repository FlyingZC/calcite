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
package org.apache.calcite.test; // 定义包名，表示该类属于 org.apache.calcite.test 包

import org.apache.calcite.plan.Contexts; // 导入 Contexts 类，用于创建上下文对象
import org.apache.calcite.plan.RelOptUtil; // 导入 RelOptUtil 类，用于关系表达式优化工具
import org.apache.calcite.plan.RelTraitDef; // 导入 RelTraitDef 类，定义关系表达式的特征
import org.apache.calcite.rel.RelNode; // 导入 RelNode 类，表示关系表达式节点
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 类，表示扩展的模式
import org.apache.calcite.sql.parser.SqlParser; // 导入 SqlParser 类，用于 SQL 解析器
import org.apache.calcite.tools.Frameworks; // 导入 Frameworks 类，用于创建 Calcite 框架配置
import org.apache.calcite.tools.PigRelBuilder; // 导入 PigRelBuilder 类，用于构建 Pig 风格的关系表达式
import org.apache.calcite.tools.Programs; // 导入 Programs 类，用于定义优化程序
import org.apache.calcite.tools.RelBuilder; // 导入 RelBuilder 类，用于构建关系表达式
import org.apache.calcite.util.Util; // 导入 Util 类，提供通用工具方法

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.util.List; // 导入 List 接口，用于列表集合
import java.util.function.Function; // 导入 Function 接口，用于函数式编程
import java.util.function.UnaryOperator; // 导入 UnaryOperator 接口，用于一元操作函数

import static org.hamcrest.CoreMatchers.is; // 导入 is 匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 方法，用于断言

/**
 * PigRelBuilder 的单元测试类
 * 
 * 本类用于测试 PigRelBuilder 的各种功能，PigRelBuilder 是 Calcite 中用于构建
 * Pig Latin 风格关系表达式的构建器。Pig Latin 是 Apache Pig 的脚本语言，
 * 用于数据流处理和分析。本测试类验证了 PigRelBuilder 能够正确地将 Pig Latin
 * 操作转换为 Calcite 的关系代数表达式。
 * 
 * 主要测试的操作包括：
 * - scan: 扫描表
 * - distinct: 去重
 * - filter: 过滤
 * - group: 分组
 * - load: 加载数据
 * 
 * 每个测试方法都验证了 PigRelBuilder 生成的关系表达式是否符合预期的逻辑计划。
 */
class PigRelBuilderTest { // 定义 PigRelBuilderTest 测试类，用于测试 PigRelBuilder 的功能
  /** Creates a config based on the "scott" schema. */ // 创建基于 "scott" 模式的配置
  public static Frameworks.ConfigBuilder config() { // 定义静态方法 config，返回 Frameworks.ConfigBuilder 对象
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根模式对象，启用元数据缓存
    return Frameworks.newConfigBuilder() // 创建新的配置构建器
        .parserConfig(SqlParser.Config.DEFAULT) // 设置 SQL 解析器配置为默认配置
        .defaultSchema( // 设置默认模式
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.SCOTT_WITH_TEMPORAL)) // 添加 scott 模式（包含时间类型）
        .traitDefs((List<RelTraitDef>) null) // 设置特征定义为 null，使用默认特征
        .programs(Programs.heuristicJoinOrder(Programs.RULE_SET, true, 2)); // 设置优化程序为启发式连接顺序优化，启用规则集，最大迭代次数为 2
  }

  static PigRelBuilder createBuilder( // 定义静态方法 createBuilder，用于创建 PigRelBuilder 实例
      UnaryOperator<RelBuilder.Config> transform) { // 接收一个一元操作函数，用于转换 RelBuilder 配置
    final Frameworks.ConfigBuilder configBuilder = config(); // 获取配置构建器
    configBuilder.context( // 设置上下文
        Contexts.of(transform.apply(RelBuilder.Config.DEFAULT))); // 应用转换函数到默认配置，并创建上下文
    return PigRelBuilder.create(configBuilder.build()); // 创建并返回 PigRelBuilder 实例
  }

  /** Converts a relational expression to a sting with linux line-endings. */ // 将关系表达式转换为使用 Linux 换行符的字符串
  private String str(RelNode r) { // 定义私有方法 str，接收 RelNode 参数，返回字符串
    return Util.toLinux(RelOptUtil.toString(r)); // 使用 RelOptUtil 将关系表达式转换为字符串，并统一换行符为 Linux 风格（\n）
  }

  @Test void testScan() { // 测试 scan 方法，验证表扫描功能
    // Equivalent SQL: // 等价的 SQL 语句
    //   SELECT * // 选择所有列
    //   FROM emp // 从 emp 表
    final PigRelBuilder builder = PigRelBuilder.create(config().build()); // 创建 PigRelBuilder 实例
    final RelNode root = builder // 构建关系表达式根节点
        .scan("EMP") // 扫描 EMP 表
        .build(); // 构建关系表达式
    assertThat(str(root), // 断言生成的计划字符串
        is("LogicalTableScan(table=[[scott, EMP]])\n")); // 期望的输出是逻辑表扫描节点
  }

  @Test void testCogroup() {} // 测试 cogroup 方法，验证共同分组功能（当前为空实现）
  @Test void testCross() {} // 测试 cross 方法，验证交叉连接功能（当前为空实现）
  @Test void testCube() {} // 测试 cube 方法，验证立方体操作功能（当前为空实现）
  @Test void testDefine() {} // 测试 define 方法，验证定义功能（当前为空实现）
  @Test void testDistinct() { // 测试 distinct 方法，验证去重功能
    // Syntax: // Pig Latin 语法
    //   alias = DISTINCT alias [PARTITION BY partitioner] [PARALLEL n]; // 去重语法：别名 = DISTINCT 别名 [PARTITION BY 分区器] [PARALLEL 并行度]
    final PigRelBuilder builder = PigRelBuilder.create(config().build()); // 创建 PigRelBuilder 实例
    final RelNode root = builder // 构建关系表达式根节点
        .scan("EMP") // 扫描 EMP 表
        .project(builder.field("DEPTNO")) // 投影 DEPTNO 字段
        .distinct() // 执行去重操作
        .build(); // 构建关系表达式
    final String plan = "LogicalAggregate(group=[{0}])\n" // 期望的计划：逻辑聚合节点，按第 0 列分组
        + "  LogicalProject(DEPTNO=[$7])\n" // 逻辑投影节点，选择 DEPTNO 列（原始表的第 7 列）
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // 逻辑表扫描节点，扫描 scott 模式的 EMP 表
    assertThat(str(root), is(plan)); // 断言生成的计划与期望的计划一致
  }

  @Test void testFilter() { // 测试 filter 方法，验证过滤功能
    // Syntax: // Pig Latin 语法
    //  FILTER name BY expr // 过滤语法：FILTER 名称 BY 表达式
    // Example: // 示例
    //  output_var = FILTER input_var BY (field1 is not null); // 输出变量 = FILTER 输入变量 BY (字段1 不为空)
    final PigRelBuilder builder = PigRelBuilder.create(config().build()); // 创建 PigRelBuilder 实例
    final RelNode root = builder // 构建关系表达式根节点
        .load("EMP.csv", null, null) // 加载 EMP.csv 文件（第二个和第三个参数为 null 表示使用默认加载器和模式）
        .filter(builder.isNotNull(builder.field("MGR"))) // 过滤 MGR 字段不为空的记录
        .build(); // 构建关系表达式
    final String plan = "LogicalFilter(condition=[IS NOT NULL($3)])\n" // 期望的计划：逻辑过滤节点，条件是第 3 列不为空
        + "  LogicalTableScan(table=[[scott, EMP]])\n"; // 逻辑表扫描节点，扫描 scott 模式的 EMP 表
    assertThat(str(root), is(plan)); // 断言生成的计划与期望的计划一致
  }

  @Test void testForeach() {} // 测试 foreach 方法，验证遍历功能（当前为空实现）

  @Test void testGroup() { // 测试 group 方法，验证分组功能
    // Syntax: // Pig Latin 语法
    //   alias = GROUP alias { ALL | BY expression} // 分组语法：别名 = GROUP 别名 { ALL | BY 表达式}
    //     [, alias ALL | BY expression ...] [USING 'collected' | 'merge'] // [，别名 ALL | BY 表达式 ...] [USING 'collected' | 'merge']
    //     [PARTITION BY partitioner] [PARALLEL n]; // [PARTITION BY 分区器] [PARALLEL 并行度]
    // Equivalent to Pig Latin: // 等价的 Pig Latin 语句
    //   r = GROUP e BY (deptno, job); // r = GROUP e BY (deptno, job);
    final Function<PigRelBuilder, RelNode> f = builder -> // 定义一个函数，接收 PigRelBuilder，返回 RelNode
        builder.scan("EMP") // 扫描 EMP 表
            .group(null, null, -1, builder.groupKey("DEPTNO", "JOB").alias("e")) // 按 DEPTNO 和 JOB 分组，别名为 e，参数 null, null, -1 表示使用默认的分区和并行配置
            .build(); // 构建关系表达式
    final String plan = "" // 期望的计划字符串
        + "LogicalAggregate(group=[{0, 1}], EMP=[COLLECT($2)])\n" // 逻辑聚合节点，按第 0 和 1 列分组，收集第 2 列到 EMP 列
        + "  LogicalProject(JOB=[$2], DEPTNO=[$7], " // 逻辑投影节点，选择 JOB（第 2 列）和 DEPTNO（第 7 列）
        + "$f8=[ROW($0, $1, $2, $3, $4, $5, $6, $7)])\n" // 创建一个包含所有列的行作为第 8 列
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // 逻辑表扫描节点，扫描 scott 模式的 EMP 表
    assertThat(str(f.apply(createBuilder(b -> b))), is(plan)); // 断言生成的计划与期望的计划一致（使用默认配置）

    // now without pruning // 现在测试不剪枝的情况
    final String plan2 = "" // 期望的计划字符串（不剪枝版本）
        + "LogicalAggregate(group=[{2, 7}], EMP=[COLLECT($8)])\n" // 逻辑聚合节点，按第 2 和 7 列分组（原始列位置），收集第 8 列到 EMP 列
        + "  LogicalProject(EMPNO=[$0], ENAME=[$1], JOB=[$2], MGR=[$3], " // 逻辑投影节点，选择所有列
        + "HIREDATE=[$4], SAL=[$5], COMM=[$6], DEPTNO=[$7], $f8=[ROW($0, $1, $2, $3, $4, $5, $6, $7)])\n" // 创建一个包含所有列的行作为第 8 列
        + "    LogicalTableScan(table=[[scott, EMP]])\n"; // 逻辑表扫描节点，扫描 scott 模式的 EMP 表
    assertThat( // 断言
        str(f.apply(createBuilder(b -> b.withPruneInputOfAggregate(false)))), // 使用不剪枝聚合输入的配置
        is(plan2)); // 期望的计划与不剪枝版本一致
  }

  @Test void testGroup2() { // 测试 group2 方法，验证多表分组功能
    // Equivalent to Pig Latin: // 等价的 Pig Latin 语句
    //   r = GROUP e BY deptno, d BY deptno; // r = GROUP e BY deptno, d BY deptno;
    final PigRelBuilder builder = PigRelBuilder.create(config().build()); // 创建 PigRelBuilder 实例
    final RelNode root = builder // 构建关系表达式根节点
        .scan("EMP") // 扫描 EMP 表
        .scan("DEPT") // 扫描 DEPT 表
        .group(null, null, -1, // 执行分组操作，参数 null, null, -1 表示使用默认的分区和并行配置
            builder.groupKey("DEPTNO").alias("e"), // 按 DEPTNO 分组，别名为 e（来自 EMP 表）
            builder.groupKey("DEPTNO").alias("d")) // 按 DEPTNO 分组，别名为 d（来自 DEPT 表）
        .build(); // 构建关系表达式
    final String plan = "LogicalJoin(condition=[=($0, $2)], joinType=[inner])\n" // 期望的计划：逻辑连接节点，条件是第 0 列等于第 2 列，连接类型为内连接
        + "  LogicalAggregate(group=[{0}], EMP=[COLLECT($1)])\n" // 左子树：逻辑聚合节点，按第 0 列分组，收集第 1 列到 EMP 列
        + "    LogicalProject(EMPNO=[$0], $f8=[ROW($0, $1, $2, $3, $4, $5, $6, $7)])\n" // 逻辑投影节点，选择 EMPNO 和创建一个包含所有列的行
        + "      LogicalTableScan(table=[[scott, EMP]])\n" // 逻辑表扫描节点，扫描 scott 模式的 EMP 表
        + "  LogicalAggregate(group=[{0}], DEPT=[COLLECT($1)])\n" // 右子树：逻辑聚合节点，按第 0 列分组，收集第 1 列到 DEPT 列
        + "    LogicalProject(DEPTNO=[$0], $f3=[ROW($0, $1, $2)])\n" // 逻辑投影节点，选择 DEPTNO 和创建一个包含所有列的行
        + "      LogicalTableScan(table=[[scott, DEPT]])\n"; // 逻辑表扫描节点，扫描 scott 模式的 DEPT 表
    assertThat(str(root), is(plan)); // 断言生成的计划与期望的计划一致
  }

  @Test void testImport() {} // 测试 import 方法，验证导入功能（当前为空实现）
  @Test void testJoinInner() {} // 测试 joinInner 方法，验证内连接功能（当前为空实现）
  @Test void testJoinOuter() {} // 测试 joinOuter 方法，验证外连接功能（当前为空实现）
  @Test void testLimit() {} // 测试 limit 方法，验证限制功能（当前为空实现）

  @Test void testLoad() { // 测试 load 方法，验证加载数据功能
    // Syntax: // Pig Latin 语法
    //   LOAD 'data' [USING function] [AS schema]; // 加载语法：LOAD '数据文件' [USING 函数] [AS 模式]
    // Equivalent to Pig Latin: // 等价的 Pig Latin 语句
    //   LOAD 'EMPS.csv' // LOAD 'EMPS.csv'
    final PigRelBuilder builder = PigRelBuilder.create(config().build()); // 创建 PigRelBuilder 实例
    final RelNode root = builder // 构建关系表达式根节点
        .load("EMP.csv", null, null) // 加载 EMP.csv 文件（第二个和第三个参数为 null 表示使用默认加载器和模式）
        .build(); // 构建关系表达式
    assertThat(str(root), // 断言生成的计划字符串
        is("LogicalTableScan(table=[[scott, EMP]])\n")); // 期望的输出是逻辑表扫描节点
  }

  @Test void testMapReduce() {} // 测试 mapReduce 方法，验证 MapReduce 功能（当前为空实现）
  @Test void testOrderBy() {} // 测试 orderBy 方法，验证排序功能（当前为空实现）
  @Test void testRank() {} // 测试 rank 方法，验证排名功能（当前为空实现）
  @Test void testSample() {} // 测试 sample 方法，验证采样功能（当前为空实现）
  @Test void testSplit() {} // 测试 split 方法，验证分割功能（当前为空实现）
  @Test void testStore() {} // 测试 store 方法，验证存储功能（当前为空实现）
  @Test void testUnion() {} // 测试 union 方法，验证联合功能（当前为空实现）
}
