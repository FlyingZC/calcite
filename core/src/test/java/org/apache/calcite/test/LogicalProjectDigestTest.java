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
package org.apache.calcite.test; // 定义包名，该类属于 org.apache.calcite.test 包，用于测试 Apache Calcite 框架的功能

import org.apache.calcite.plan.RelOptUtil; // 导入 RelOptUtil 工具类，提供关系表达式（RelNode）的各种实用方法，如转换为字符串、比较、规范化等
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，这是 Calcite 中所有关系表达式（如扫描、过滤、投影等操作）的基类
import org.apache.calcite.rel.logical.LogicalProject; // 导入 LogicalProject 类，表示逻辑投影操作，用于选择、重命名或计算输入关系的字段
import org.apache.calcite.sql.SqlExplainLevel; // 导入 SqlExplainLevel 枚举，定义了 SQL 解释的不同详细级别（如 DIGEST_ATTRIBUTES、EXPPLAN_ATTRIBUTES 等）
import org.apache.calcite.tools.FrameworkConfig; // 导入 FrameworkConfig 接口，用于配置 Calcite 框架的基础设置，如类型系统、规则集等
import org.apache.calcite.tools.Frameworks; // 导入 Frameworks 工具类，用于创建框架配置和构建器
import org.apache.calcite.tools.RelBuilder; // 导入 RelBuilder 接口，提供流式 API 用于构建关系表达式树（RelNode）

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import static org.apache.calcite.test.Matchers.isLinux; // 导入静态方法 isLinux，用于断言字符串是否匹配 Linux 格式（处理换行符差异）

import static org.hamcrest.MatcherAssert.assertThat; // 导入静态方法 assertThat，用于执行断言验证

/**
 * Verifies digest for {@link LogicalProject}.
 * 验证 LogicalProject 的摘要（digest）功能
 * 
 * 本测试类用于验证 LogicalProject 关系表达式在不同解释级别下的摘要生成行为
 * 摘要（digest）是关系表达式的规范化字符串表示，用于比较和缓存
 * 主要关注点：
 * 1. 字段名称是否影响摘要生成（DIGEST_ATTRIBUTES 级别下不应包含字段名）
 * 2. 不同解释级别（DIGEST_ATTRIBUTES、EXPPLAN_ATTRIBUTES）的输出差异
 * 3. 简单投影场景的摘要格式
 * 
 * 摘要的作用：
 * - 用于关系表达式的规范化比较，忽略无关的差异（如字段别名）
 * - 用于计划缓存和重用
 * - 用于优化规则的匹配和触发
 */
class LogicalProjectDigestTest { // 定义测试类 LogicalProjectDigestTest，用于测试 LogicalProject 的摘要生成功能
  // 类名含义：LogicalProject（逻辑投影）+ Digest（摘要）+ Test（测试）
  // 该类没有成员变量，所有测试方法都是独立的，使用局部变量
  
  /** Planner does not compare. */ // 方法注释：测试规划器在比较时不应受字段名称影响
  @Test void fieldNamesDoNotInfluenceDigest() { // 测试方法：验证字段名称不影响摘要生成，使用 JUnit 5 的 @Test 注解标记
    // 方法名含义：fieldNames（字段名）+ DoNot（不）+ Influence（影响）+ Digest（摘要）
    // 测试目标：确保在 DIGEST_ATTRIBUTES 级别下，投影的字段别名不会出现在摘要中
    
    final RelBuilder rb = RelBuilder.create(Frameworks.newConfigBuilder().build()); // 创建 RelBuilder 实例，使用默认框架配置，RelBuilder 用于构建关系表达式树
    // Frameworks.newConfigBuilder().build() 创建一个空的框架配置构建器并构建配置
    // RelBuilder.create() 根据配置创建关系构建器，提供流式 API 构建查询计划
    
    final RelNode xAsEmpid = rb.values(new String[]{"x", "y", "z"}, 1, 2, 3) // 使用 RelBuilder 创建一个 LogicalValues 节点，包含三列（x, y, z）和一行数据（1, 2, 3）
        // values() 方法创建一个常量值节点，模拟表数据
        // new String[]{"x", "y", "z"} 定义列名
        // 1, 2, 3 定义一行数据，类型默认为 INTEGER
        .project( // 在值节点上创建投影操作，用于选择、重命名或计算字段
            rb.alias(rb.field("x"), "renamed_x"), // 将字段 x 重命名为 renamed_x，使用 alias() 方法创建字段别名
            // rb.field("x") 引用输入的第 0 个字段（x）
            // alias() 创建一个重命名的字段引用
            rb.alias(rb.field("y"), "renamed_y"), // 将字段 y 重命名为 renamed_y，引用输入的第 1 个字段（y）
            rb.alias(rb.literal("u"), "extra_field")) // 创建一个字面量字段，值为字符串 "u"，别名为 extra_field
            // literal() 创建常量表达式，不依赖输入字段
        .build(); // 构建完整的关系表达式树，返回 RelNode 对象
    // xAsEmpid 变量名暗示了测试意图：将 x 作为 empid（员工ID），但这里实际是重命名测试

    assertThat( // 使用 Hamcrest 的 assertThat 方法进行断言验证
        "project column name should not be included to the project digest", // 断言描述：投影列名不应包含在投影摘要中
        RelOptUtil.toString(xAsEmpid, SqlExplainLevel.DIGEST_ATTRIBUTES), // 将关系表达式转换为字符串，使用 DIGEST_ATTRIBUTES 级别
        // DIGEST_ATTRIBUTES 级别只包含影响语义的属性，忽略字段名等不影响语义的信息
        // 这对于规范化比较和缓存非常重要
        isLinux("" // 使用 isLinux 匹配器验证输出字符串，处理不同操作系统的换行符差异
            + "LogicalProject(inputs=[0..1], exprs=[['u']])\n" // 期望的摘要格式：只包含输入索引和表达式，不包含字段名
            // inputs=[0..1] 表示使用输入的第 0 和第 1 个字段（对应原始的 x 和 y）
            // exprs=[['u']] 表示包含一个字面量表达式 'u'
            // 注意：这里没有显示 renamed_x 和 renamed_y，说明字段名被忽略了
            + "  LogicalValues(type=[RecordType(INTEGER x, INTEGER y, INTEGER z)], tuples=[[{ 1, 2, 3 }]])\n")); // 子节点 LogicalValues 的完整类型信息

    assertThat( // 第二个断言：验证 EXPPLAN_ATTRIBUTES 级别的输出
        "project column names should be present in EXPPLAN_ATTRIBUTES", // 断言描述：投影列名应出现在 EXPPLAN_ATTRIBUTES 级别中
        RelOptUtil.toString(xAsEmpid, SqlExplainLevel.EXPPLAN_ATTRIBUTES), // 使用 EXPPLAN_ATTRIBUTES 级别转换关系表达式
        // EXPPLAN_ATTRIBUTES 级别包含所有属性，包括字段名，用于详细解释
        isLinux("" // 验证输出字符串
            + "LogicalProject(renamed_x=[$0], renamed_y=[$1], extra_field=['u'])\n" // 期望格式：包含完整的字段名和表达式
            // renamed_x=[$0] 表示 renamed_x 字段来自输入的第 0 个字段
            // renamed_y=[$1] 表示 renamed_y 字段来自输入的第 1 个字段
            // extra_field=['u'] 表示 extra_field 是字面量 'u'
            + "  LogicalValues(tuples=[[{ 1, 2, 3 }]])\n")); // 子节点 LogicalValues 的简化表示

    assertThat( // 第三个断言：验证默认的 toString 输出
        "project column names should be present with default RelOptUtil.toString(...)", // 断言描述：默认的 RelOptUtil.toString 应包含投影列名
        RelOptUtil.toString(xAsEmpid), // 使用默认级别（通常等同于 EXPPLAN_ATTRIBUTES）转换关系表达式
        // 默认的 toString 方法用于调试和日志记录，应包含完整信息
        isLinux("" // 验证输出字符串
            + "LogicalProject(renamed_x=[$0], renamed_y=[$1], extra_field=['u'])\n" // 期望格式与 EXPPLAN_ATTRIBUTES 相同
            + "  LogicalValues(tuples=[[{ 1, 2, 3 }]])\n")); // 子节点的简化表示
  } // 方法结束，验证了摘要生成在不同级别下的行为差异

  @Test void testProjectDigestWithOneTrivialField() { // 测试方法：验证只有一个简单字段的投影摘要生成
    // 方法名含义：test（测试）+ Project（投影）+ Digest（摘要）+ WithOneTrivialField（带一个简单字段）
    // 测试目标：验证当投影只包含一个简单字段引用时，摘要的格式是否正确
    // 简单字段（trivial field）指的是直接引用输入字段，不涉及计算或转换
    
    final FrameworkConfig config = RelBuilderTest.config().build(); // 创建框架配置，使用 RelBuilderTest 提供的配置模板
    // RelBuilderTest.config() 返回一个配置构建器，预设了测试环境的标准配置
    // .build() 构建最终的 FrameworkConfig 对象
    // 使用测试配置确保测试环境的一致性和可重复性
    
    final RelBuilder builder = RelBuilder.create(config); // 根据配置创建 RelBuilder 实例
    // RelBuilder 是构建关系表达式树的核心工具，提供流式 API
    // 使用配置创建确保使用相同的类型系统、规则集等
    
    final RelNode rel = builder // 使用 RelBuilder 构建关系表达式树
        .scan("EMP") // 扫描名为 "EMP" 的表，创建 LogicalTableScan 节点
        // scan() 方法在逻辑计划中表示从表读取数据
        // "EMP" 是表名，对应测试数据中的 scott.EMP 表
        .project(builder.field("EMPNO")) // 在表扫描上创建投影，只选择 EMPNO 字段
        // builder.field("EMPNO") 创建对 EMPNO 字段的引用
        // 这是一个简单投影，只传递一个字段，不进行任何计算
        .build(); // 构建完整的关系表达式树，返回 RelNode 对象
    // rel 变量包含：LogicalTableScan(EMP) -> LogicalProject(EMPNO)
    
    String digest = RelOptUtil.toString(rel, SqlExplainLevel.DIGEST_ATTRIBUTES); // 将关系表达式转换为摘要字符串
    // 使用 DIGEST_ATTRIBUTES 级别获取摘要
    // 摘要用于规范化比较，忽略不影响语义的细节
    // digest 变量存储生成的摘要字符串
    
    final String expected = "" // 定义期望的摘要字符串
        + "LogicalProject(inputs=[0])\n" // 期望的投影节点：inputs=[0] 表示使用输入的第 0 个字段
        // 只有一个输入字段，对应 EMPNO
        // 没有表达式列表（exprs），因为只是简单引用
        + "  LogicalTableScan(table=[[scott, EMP]])\n"; // 期望的表扫描节点：显示完整的表路径
        // table=[[scott, EMP]] 表示 schema 名为 scott，表名为 EMP
        // 缩进表示层级关系（子节点）
    
    assertThat(digest, isLinux(expected)); // 断言验证：实际生成的摘要与期望摘要匹配
    // digest 是实际生成的摘要
    // isLinux(expected) 处理换行符差异，确保跨平台兼容性
    // 如果不匹配，测试失败并显示差异
  } // 方法结束，验证了简单投影的摘要格式
} // 类定义结束，LogicalProjectDigestTest 类的所有测试方法已完成
