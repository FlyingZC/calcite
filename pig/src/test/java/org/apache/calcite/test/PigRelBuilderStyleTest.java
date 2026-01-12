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
package org.apache.calcite.test; // 声明包名，表示该类属于 org.apache.calcite.test 包

import org.apache.calcite.adapter.pig.PigAggregate; // 导入 Pig 聚合操作相关类
import org.apache.calcite.adapter.pig.PigFilter; // 导入 Pig 过滤操作相关类
import org.apache.calcite.adapter.pig.PigRel; // 导入 Pig 关系表达式接口
import org.apache.calcite.adapter.pig.PigRelFactories; // 导入 Pig 关系表达式工厂类
import org.apache.calcite.adapter.pig.PigRules; // 导入 Pig 优化规则类
import org.apache.calcite.adapter.pig.PigTable; // 导入 Pig 表类
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器接口
import org.apache.calcite.plan.RelOptRule; // 导入关系表达式优化规则接口
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口
import org.apache.calcite.rel.core.Filter; // 导入过滤操作关系表达式
import org.apache.calcite.rel.core.Join; // 导入连接操作关系表达式
import org.apache.calcite.rel.core.JoinRelType; // 导入连接类型枚举
import org.apache.calcite.rel.rules.CoreRules; // 导入核心优化规则集合
import org.apache.calcite.rel.rules.FilterAggregateTransposeRule; // 导入过滤和聚合交换规则
import org.apache.calcite.rel.rules.FilterJoinRule.FilterIntoJoinRule; // 导入过滤推入连接规则
import org.apache.calcite.schema.Schema; // 导入 Schema 接口
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，支持动态添加表
import org.apache.calcite.tools.FrameworkConfig; // 导入框架配置类
import org.apache.calcite.tools.Frameworks; // 导入框架工具类
import org.apache.calcite.tools.RelBuilder; // 导入关系表达式构建器接口
import org.apache.calcite.tools.RelBuilderFactory; // 导入关系表达式构建器工厂接口
import org.apache.calcite.util.TestUtil; // 导入测试工具类

import org.apache.hadoop.fs.Path; // 导入 Hadoop 文件系统路径类
import org.apache.pig.pigunit.Cluster; // 导入 Pig 集群类
import org.apache.pig.pigunit.PigTest; // 导入 Pig 测试类
import org.apache.pig.pigunit.pig.PigServer; // 导入 Pig 服务器类
import org.apache.pig.test.Util; // 导入 Pig 工具类

import org.junit.jupiter.api.AfterEach; // 导入 JUnit5 每个测试方法后执行的注解
import org.junit.jupiter.api.BeforeEach; // 导入 JUnit5 每个测试方法前执行的注解
import org.junit.jupiter.api.Disabled; // 导入 JUnit5 禁用测试的注解
import org.junit.jupiter.api.Test; // 导入 JUnit5 测试方法注解

import java.io.File; // 导入文件类

import static org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS; // 导入等于操作符
import static org.apache.calcite.sql.fun.SqlStdOperatorTable.GREATER_THAN; // 导入大于操作符

import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 断言方法
import static org.junit.jupiter.api.Assumptions.assumeTrue; // 导入 JUnit5 假设工具

/**
 * Tests for the {@code org.apache.calcite.adapter.pig} package that tests the
 * building of {@link PigRel} relational expressions using {@link RelBuilder} and
 * associated factories in {@link PigRelFactories}.
 */
// 本类是测试类，用于测试 org.apache.calcite.adapter.pig 包的功能
// 主要测试使用 RelBuilder 和 PigRelFactories 中的工厂来构建 PigRel 关系表达式
// 测试覆盖了 Pig 的各种操作，如扫描、过滤、聚合、连接等
// 继承自 AbstractPigTest，复用了 Pig 测试的基础功能
@Disabled // 标记为禁用状态，可能因为某些依赖或环境问题导致测试不稳定
class PigRelBuilderStyleTest extends AbstractPigTest { // 定义测试类，继承自抽象 Pig 测试基类

  PigRelBuilderStyleTest() { // 构造方法，在创建测试实例时执行
    assumeTrue(File.separatorChar == '/', // 假设文件分隔符必须是 '/'，这是 Unix/Linux 风格的路径分隔符
        () -> "Pig tests expects File.separatorChar to be /, actual one is " // 如果假设失败，显示错误消息
          + File.separatorChar); // 显示实际的文件分隔符
  } // 构造方法结束

  @Disabled("CALCITE-3660") // 禁用此测试，关联的 JIRA 问题号 CALCITE-3660
  @Test void testScanAndFilter() { // 测试方法：测试扫描表并应用过滤操作
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t") // 扫描名为 "t" 的表
        .filter(builder.call(GREATER_THAN, builder.field("tc0"), builder.literal("abc"))).build(); // 添加过滤条件：tc0 > 'abc'
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载数据
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义数据字段类型
            + "t = FILTER t BY (tc0 > 'abc');", // 期望的 Pig 脚本第二部分：过滤数据
        new String[] { "(b,2)", "(c,3)" }); // 期望的查询结果数组
  } // 测试方法结束

  @Test @Disabled("CALCITE-1751") // 禁用此测试，关联的 JIRA 问题号 CALCITE-1751
  public void testImplWithMultipleFilters() { // 测试方法：测试扫描表并应用多个过滤条件（AND 逻辑）
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t") // 扫描名为 "t" 的表
        .filter( // 添加过滤条件
            builder.and(builder.call(GREATER_THAN, builder.field("tc0"), builder.literal("abc")), // 第一个条件：tc0 > 'abc'
                builder.call(EQUALS, builder.field("tc1"), builder.literal("3")))) // 第二个条件：tc1 == '3'，使用 AND 连接
        .build(); // 构建关系表达式树
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载数据
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义数据字段类型
            + "t = FILTER t BY (tc0 > 'abc') AND (tc1 == '3');", // 期望的 Pig 脚本第二部分：使用 AND 连接两个过滤条件
        new String[] { "(c,3)" }); // 期望的查询结果数组，只有一行数据满足两个条件
  } // 测试方法结束

  @Test @Disabled("CALCITE-1751") // 禁用此测试，关联的 JIRA 问题号 CALCITE-1751
  public void testImplWithGroupByAndCount() { // 测试方法：测试分组聚合和计数操作
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t") // 扫描名为 "t" 的表
        .aggregate(builder.groupKey("tc0"), builder.count(false, "c", builder.field("tc1"))) // 按 tc0 字段分组，并统计每组中 tc1 的数量
        .build(); // 构建关系表达式树
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载数据
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义数据字段类型
            + "t = GROUP t BY (tc0);\n" // 期望的 Pig 脚本第二部分：按 tc0 字段分组
            + "t = FOREACH t {\n" // 期望的 Pig 脚本第三部分：遍历每个分组
            + "  GENERATE group AS tc0, COUNT(t.tc1) AS c;\n" // 生成分组键和计数值
            + "};", // 期望的 Pig 脚本第四部分：结束 FOREACH 块
        new String[] { "(a,1)", "(b,1)", "(c,1)" }); // 期望的查询结果数组，每个分组各有一条记录
  } // 测试方法结束

  @Test void testImplWithCountWithoutGroupBy() { // 测试方法：测试不带分组的全局计数操作
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t") // 扫描名为 "t" 的表
        .aggregate(builder.groupKey(), builder.count(false, "c", builder.field("tc0"))).build(); // 不分组，统计所有行中 tc0 的总数
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载数据
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义数据字段类型
            + "t = GROUP t ALL;\n" // 期望的 Pig 脚本第二部分：将所有数据分组到一个组中
            + "t = FOREACH t {\n" // 期望的 Pig 脚本第三部分：遍历这个唯一分组
            + "  GENERATE COUNT(t.tc0) AS c;\n" // 生成计数值
            + "};", // 期望的 Pig 脚本第四部分：结束 FOREACH 块
        new String[] { "(3)" }); // 期望的查询结果数组，只有一个值表示总数
  } // 测试方法结束

  @Test @Disabled("CALCITE-1751") // 禁用此测试，关联的 JIRA 问题号 CALCITE-1751
  public void testImplWithGroupByMultipleFields() { // 测试方法：测试按多个字段分组聚合
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t") // 扫描名为 "t" 的表
        .aggregate(builder.groupKey("tc1", "tc0"), builder.count(false, "c", builder.field("tc1"))) // 按 tc1 和 tc0 两个字段分组，并统计每组中 tc1 的数量
        .build(); // 构建关系表达式树
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载数据
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义数据字段类型
            + "t = GROUP t BY (tc0, tc1);\n" // 期望的 Pig 脚本第二部分：按 tc0 和 tc1 两个字段分组
            + "t = FOREACH t {\n" // 期望的 Pig 脚本第三部分：遍历每个分组
            + "  GENERATE group.tc0 AS tc0, group.tc1 AS tc1, COUNT(t.tc1) AS c;\n" // 生成分组键和计数值
            + "};", // 期望的 Pig 脚本第四部分：结束 FOREACH 块
        new String[] { "(a,1,1)", "(b,2,1)", "(c,3,1)" }); // 期望的查询结果数组，每个组合的分组各有一条记录
  } // 测试方法结束

  @Disabled("CALCITE-3660") // 禁用此测试，关联的 JIRA 问题号 CALCITE-3660
  @Test void testImplWithGroupByCountDistinct() { // 测试方法：测试分组后的去重计数操作
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t") // 扫描名为 "t" 的表
        .aggregate(builder.groupKey("tc1", "tc0"), builder.count(true, "c", builder.field("tc1"))) // 按 tc1 和 tc0 分组，统计每组中 tc1 的不重复值数量（第一个参数 true 表示去重）
        .build(); // 构建关系表达式树
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载数据
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义数据字段类型
            + "t = GROUP t BY (tc0, tc1);\n" // 期望的 Pig 脚本第二部分：按 tc0 和 tc1 分组
            + "t = FOREACH t {\n" // 期望的 Pig 脚本第三部分：遍历每个分组
            + "  tc1_DISTINCT = DISTINCT t.tc1;\n" // 期望的 Pig 脚本第四部分：对 tc1 字段进行去重操作
            + "  GENERATE group.tc0 AS tc0, group.tc1 AS tc1, COUNT(tc1_DISTINCT) AS c;\n" // 期望的 Pig 脚本第五部分：生成分组键和去重计数
            + "};", // 期望的 Pig 脚本第六部分：结束 FOREACH 块
        new String[] { "(a,1,1)", "(b,2,1)", "(c,3,1)" }); // 期望的查询结果数组
  } // 测试方法结束

  @Disabled("CALCITE-3660") // 禁用此测试，关联的 JIRA 问题号 CALCITE-3660
  @Test void testImplWithJoin() { // 测试方法：测试连接操作（内连接）和过滤
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t").scan("s") // 扫描两个表 "t" 和 "s"
        .join(JoinRelType.INNER, // 执行内连接操作
            builder.equals(builder.field(2, 0, "tc1"), builder.field(2, 1, "sc0"))) // 连接条件：t.tc1 == s.sc0
        .filter(builder.call(GREATER_THAN, builder.field("tc0"), builder.literal("a"))).build(); // 添加过滤条件：tc0 > 'a'
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载表 t
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义表 t 的字段类型
            + "t = FILTER t BY (tc0 > 'a');\n" // 期望的 Pig 脚本第二部分：过滤表 t
            + "s = LOAD 'target/data2.txt" // 期望的 Pig 脚本第三部分：加载表 s
            + "' USING PigStorage() AS (sc0:chararray, sc1:chararray);\n" // 定义表 s 的字段类型
            + "t = JOIN t BY tc1 , s BY sc0;", // 期望的 Pig 脚本第四部分：执行连接操作
        new String[] { "(b,2,2,label2)" }); // 期望的查询结果数组，只有一条记录满足连接和过滤条件
  } // 测试方法结束

  @Test @Disabled("CALCITE-1751") // 禁用此测试，关联的 JIRA 问题号 CALCITE-1751
  public void testImplWithJoinAndGroupBy() { // 测试方法：测试左连接、过滤和分组聚合的组合操作
    final SchemaPlus schema = createTestSchema(); // 创建测试用的 Schema，包含测试表
    final RelBuilder builder = createRelBuilder(schema); // 创建关系表达式构建器，使用 Pig 工厂
    final RelNode node = builder.scan("t").scan("s") // 扫描两个表 "t" 和 "s"
        .join(JoinRelType.LEFT, // 执行左连接操作，保留左表的所有记录
            builder.equals(builder.field(2, 0, "tc1"), builder.field(2, 1, "sc0"))) // 连接条件：t.tc1 == s.sc0
        .filter(builder.call(GREATER_THAN, builder.field("tc0"), builder.literal("abc"))) // 添加过滤条件：tc0 > 'abc'
        .aggregate(builder.groupKey("tc1"), builder.count(false, "c", builder.field("sc1"))) // 按 tc1 分组，统计每组中 sc1 的数量
        .build(); // 构建关系表达式树
    final RelNode optimized = optimizeWithVolcano(node); // 使用 Volcano 优化器优化关系表达式树
    assertScriptAndResults("t", getPigScript(optimized, schema), // 断言生成的 Pig 脚本和结果是否符合预期
        "t = LOAD 'target/data.txt" // 期望的 Pig 脚本第一部分：加载表 t
            + "' USING PigStorage() AS (tc0:chararray, tc1:chararray);\n" // 定义表 t 的字段类型
            + "t = FILTER t BY (tc0 > 'abc');\n" // 期望的 Pig 脚本第二部分：过滤表 t
            + "s = LOAD 'target/data2.txt" // 期望的 Pig 脚本第三部分：加载表 s
            + "' USING PigStorage() AS (sc0:chararray, sc1:chararray);\n" // 定义表 s 的字段类型
            + "t = JOIN t BY tc1 LEFT, s BY sc0;\n" // 期望的 Pig 脚本第四部分：执行左连接操作
            + "t = GROUP t BY (tc1);\n" // 期望的 Pig 脚本第五部分：按 tc1 分组
            + "t = FOREACH t {\n" // 期望的 Pig 脚本第六部分：遍历每个分组
            + "  GENERATE group AS tc1, COUNT(t.sc1) AS c;\n" // 期望的 Pig 脚本第七部分：生成分组键和计数值
            + "};", // 期望的 Pig 脚本第八部分：结束 FOREACH 块
        new String[] { "(2,1)", "(3,0)" }); // 期望的查询结果数组，包含两个分组的结果
  } // 测试方法结束

  private SchemaPlus createTestSchema() { // 私有辅助方法：创建测试用的 Schema，包含测试表
    SchemaPlus result = Frameworks.createRootSchema(false); // 创建根 Schema，false 表示不添加默认的元数据表
    result.add("t", // 向 Schema 中添加名为 "t" 的表
        new PigTable("target/data.txt", // 指定表的数据文件路径
        new String[] { "tc0", "tc1" })); // 指定表的字段名称
    result.add("s", // 向 Schema 中添加名为 "s" 的表
        new PigTable("target/data2.txt", // 指定表的数据文件路径
        new String[] { "sc0", "sc1" })); // 指定表的字段名称
    return result; // 返回创建好的 Schema
  } // 方法结束

  private RelBuilder createRelBuilder(SchemaPlus schema) { // 私有辅助方法：创建配置了 Pig 工厂的 RelBuilder
    final FrameworkConfig config = Frameworks.newConfigBuilder().defaultSchema(schema) // 创建框架配置构建器，设置默认 Schema
        .context(PigRelFactories.ALL_PIG_REL_FACTORIES) // 设置上下文，使用所有 Pig 关系表达式工厂
        .build(); // 构建配置对象
    return RelBuilder.create(config); // 使用配置创建并返回 RelBuilder 实例
  } // 方法结束

  private RelNode optimizeWithVolcano(RelNode root) { // 私有辅助方法：使用 Volcano 优化器优化关系表达式树
    RelOptPlanner planner = getVolcanoPlanner(root); // 获取配置好规则的 Volcano 优化器
    return planner.findBestExp(); // 执行优化过程，找到最优的关系表达式树并返回
  } // 方法结束

  private RelOptPlanner getVolcanoPlanner(RelNode root) { // 私有辅助方法：获取并配置 Volcano 优化器
    final RelBuilderFactory builderFactory = // 创建关系表达式构建器工厂
        RelBuilder.proto(PigRelFactories.ALL_PIG_REL_FACTORIES); // 使用 Pig 工厂创建原型
    final RelOptPlanner planner = root.getCluster().getPlanner(); // 获取关系表达式簇中的优化器（VolcanoPlanner）
    for (RelOptRule r : PigRules.ALL_PIG_OPT_RULES) { // 遍历所有 Pig 优化规则
      planner.addRule(r); // 将规则添加到优化器中
    } // 循环结束
    planner.removeRule(CoreRules.FILTER_AGGREGATE_TRANSPOSE); // 移除默认的过滤聚合交换规则
    planner.removeRule(CoreRules.FILTER_INTO_JOIN); // 移除默认的过滤推入连接规则
    planner.addRule(CoreRules.FILTER_AGGREGATE_TRANSPOSE.config // 添加自定义的过滤聚合交换规则
        .withRelBuilderFactory(builderFactory) // 设置关系表达式构建器工厂
        .as(FilterAggregateTransposeRule.Config.class) // 转换为配置类
        .withOperandFor(PigFilter.class, PigAggregate.class) // 指定操作数为 PigFilter 和 PigAggregate
        .toRule()); // 创建规则并添加
    planner.addRule( // 添加自定义的过滤推入连接规则
        CoreRules.FILTER_INTO_JOIN.config // 使用基础规则配置
            .withRelBuilderFactory(builderFactory) // 设置关系表达式构建器工厂
            .withOperandSupplier(b0 -> // 指定操作数提供器
                b0.operand(Filter.class).oneInput(b1 -> // Filter 操作有一个输入
                    b1.operand(Join.class).anyInputs())) // Join 操作可以是任何输入
            .withDescription("FilterJoinRule:filter") // 设置规则描述
            .as(FilterIntoJoinRule.FilterIntoJoinRuleConfig.class) // 转换为配置类
            .withSmart(true) // 启用智能模式
            .withPredicate((join, joinType, exp) -> true) // 设置谓词，总是返回 true
            .toRule()); // 创建规则并添加
    planner.setRoot(root); // 设置优化器的根节点
    return planner; // 返回配置好的优化器
  } // 方法结束

  private void assertScriptAndResults(String relAliasForStore, String script, // 私有辅助方法：断言 Pig 脚本和执行结果是否符合预期
      String expectedScript, String[] expectedResults) { // 参数：存储别名、实际脚本、期望脚本、期望结果
    try { // 开始 try 块，捕获可能的异常
      assertThat(script, is(expectedScript)); // 断言实际生成的脚本与期望脚本完全一致
      script = script + "\nSTORE " + relAliasForStore + " INTO 'myoutput;"; // 在脚本末尾添加 STORE 语句，将结果存储到输出文件
      PigTest pigTest = new PigTest(script.split("[\\r\\n]+")); // 创建 Pig 测试实例，按换行符分割脚本
      pigTest.assertOutputAnyOrder(expectedResults); // 断言输出结果与期望结果匹配（顺序不重要）
    } catch (Exception e) { // 捕获所有异常
      throw TestUtil.rethrow(e); // 重新抛出异常，保留原始异常类型
    } // try-catch 块结束
  } // 方法结束

  private String getPigScript(RelNode root, Schema schema) { // 私有辅助方法：将关系表达式树转换为 Pig 脚本字符串
    PigRel.Implementor impl = new PigRel.Implementor(); // 创建 Pig 实现器，用于遍历关系表达式树并生成 Pig 脚本
    impl.visitChild(0, root); // 从根节点开始遍历关系表达式树，生成对应的 Pig 脚本
    return impl.getScript(); // 返回生成的 Pig 脚本字符串
  } // 方法结束

  @AfterEach // JUnit5 注解，在每个测试方法执行后运行
  public void shutdownPigServer() { // 测试后清理方法：关闭 Pig 服务器
    PigServer pigServer = PigTest.getPigServer(); // 获取 Pig 服务器实例
    if (pigServer != null) { // 检查服务器实例是否存在
      pigServer.shutdown(); // 关闭 Pig 服务器，释放资源
    } // if 块结束
  } // 方法结束

  @BeforeEach // JUnit5 注解，在每个测试方法执行前运行
  public void setupDataFilesForPigServer() throws Exception { // 测试前准备方法：设置 Pig 测试所需的数据文件
    System.getProperties().setProperty("pigunit.exectype", // 设置系统属性，指定 Pig 执行类型
        Util.getLocalTestMode().toString()); // 使用本地测试模式
    Cluster cluster = PigTest.getCluster(); // 获取 Pig 集群实例
    // Put the data files in target/ so they don't dirty the local git checkout
    cluster.update( // 将数据文件更新到集群中
        new Path(getFullPathForTestDataFile("data.txt")), // 源文件路径：测试数据文件 data.txt
        new Path("target/data.txt")); // 目标路径：target/data.txt，避免污染 git 工作目录
    cluster.update( // 更新第二个数据文件
        new Path(getFullPathForTestDataFile("data2.txt")), // 源文件路径：测试数据文件 data2.txt
        new Path("target/data2.txt")); // 目标路径：target/data2.txt
  } // 方法结束
}
