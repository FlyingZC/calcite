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
package org.apache.calcite.rex; // 声明包名，该类属于 org.apache.calcite.rex 包，用于处理关系表达式(Rex)相关功能

import org.apache.calcite.jdbc.CalciteSchema; // 导入 CalciteSchema 类，用于表示 Calcite 的模式(Schema)结构
import org.apache.calcite.rel.RelNode; // 导入 RelNode 类，表示关系代数树中的节点
import org.apache.calcite.rel.core.Project; // 导入 Project 类，表示投影操作的关系节点
import org.apache.calcite.runtime.Hook; // 导入 Hook 类，用于在运行时插入自定义行为
import org.apache.calcite.sql.SqlNode; // 导入 SqlNode 类，表示 SQL 抽象语法树(AST)中的节点
import org.apache.calcite.sql.parser.SqlParseException; // 导入 SqlParseException 类，表示 SQL 解析异常
import org.apache.calcite.sql.parser.SqlParser; // 导入 SqlParser 类，用于将 SQL 字符串解析为 SqlNode
import org.apache.calcite.test.SqlToRelTestBase; // 导入 SqlToRelTestBase 类，提供 SQL 到关系代数转换测试的基础功能
import org.apache.calcite.tools.FrameworkConfig; // 导入 FrameworkConfig 类，表示 Calcite 框架的配置
import org.apache.calcite.tools.Frameworks; // 导入 Frameworks 类，用于创建和配置 Calcite 框架实例
import org.apache.calcite.tools.Planner; // 导入 Planner 类，表示 SQL 查询规划器，负责将 SQL 转换为关系代数
import org.apache.calcite.tools.RelConversionException; // 导入 RelConversionException 类，表示关系转换异常
import org.apache.calcite.tools.ValidationException; // 导入 ValidationException 类，表示 SQL 验证异常
import org.apache.calcite.util.Closer; // 导入 Closer 类，用于资源管理，确保资源被正确关闭
import org.apache.calcite.util.TestUtil; // 导入 TestUtil 类，提供测试工具方法

import org.junit.jupiter.api.Test; // 导入 JUnit5 的 Test 注解，用于标记测试方法

import static org.hamcrest.MatcherAssert.assertThat; // 导入静态方法，用于断言测试结果
import static org.hamcrest.Matchers.hasToString; // 导入静态匹配器，用于验证对象的字符串表示

/**
 * Unit test for {@link org.apache.calcite.rex.RexSqlStandardConvertletTable}.
 * // RexSqlStandardConvertletTable 的单元测试类
 * // 该类用于测试 RexSqlStandardConvertletTable 的功能，该类负责将 RexNode(关系表达式节点)转换回 SqlNode(SQL 节点)
 * // 主要测试场景包括：
 * // 1. COALESCE 函数的转换
 * // 2. CASE 表达式的转换（带值和无值两种形式）
 * // 这个测试类验证了 RexSqlStandardConvertletTable 能够正确地将内部的关系表达式表示转换回标准的 SQL 语法
 * // 这对于代码生成、SQL 重写和查询优化后的结果展示非常重要
 */
class RexSqlStandardConvertletTableTest extends SqlToRelTestBase { // 定义测试类，继承自 SqlToRelTestBase 以获得基础的测试功能

  @Test void testCoalesce() { // 测试方法：测试 COALESCE 函数从 RexNode 转换为 SqlNode 的功能
    final Project project = // 声明并初始化 Project 关系节点，Project 表示投影操作
        (Project) convertSqlToRel("SELECT COALESCE(NULL, 'a')", false); // 调用辅助方法将 SQL 转换为关系代数，传入 COALESCE SQL 和 false 表示不简化 Rex
    final RexNode rex = project.getProjects().get(0); // 从 Project 节点中获取第一个投影表达式，即 COALESCE 函数对应的 RexNode
    final RexToSqlNodeConverter rexToSqlNodeConverter = rexToSqlNodeConverter(); // 获取 RexToSqlNodeConverter 转换器实例
    final SqlNode convertedSql = rexToSqlNodeConverter.convertNode(rex); // 将 RexNode 转换回 SqlNode，执行反向转换
    assertThat(convertedSql, // 断言转换结果
        hasToString("CASE WHEN NULL IS NOT NULL THEN NULL ELSE 'a' END")); // 验证转换后的 SqlNode 字符串表示是否为预期的 CASE 表达式
  } // COALESCE(NULL, 'a') 应该被转换为 CASE WHEN NULL IS NOT NULL THEN NULL ELSE 'a' END

  @Test void testCaseWithValue() { // 测试方法：测试带值的 CASE 表达式转换
    final Project project = // 声明并初始化 Project 关系节点
            (Project) convertSqlToRel( // 调用辅助方法将 SQL 转换为关系代数
                    "SELECT CASE NULL WHEN NULL THEN NULL ELSE 'a' END", false); // 传入带值的 CASE 表达式 SQL，false 表示不简化 Rex
    final RexNode rex = project.getProjects().get(0); // 从 Project 节点中获取第一个投影表达式，即 CASE 表达式对应的 RexNode
    final RexToSqlNodeConverter rexToSqlNodeConverter = rexToSqlNodeConverter(); // 获取 RexToSqlNodeConverter 转换器实例
    final SqlNode convertedSql = rexToSqlNodeConverter.convertNode(rex); // 将 RexNode 转换回 SqlNode
    assertThat(convertedSql, // 断言转换结果
        hasToString("CASE WHEN NULL = NULL THEN NULL ELSE 'a' END")); // 验证转换后的 SqlNode 字符串表示，注意带值的 CASE 使用 = 运算符
  } // CASE NULL WHEN NULL THEN NULL ELSE 'a' END 应该被转换为 CASE WHEN NULL = NULL THEN NULL ELSE 'a' END

  @Test void testCaseNoValue() { // 测试方法：测试不带值的 CASE 表达式转换
    final String sql = "SELECT CASE WHEN NULL IS NULL THEN NULL ELSE 'a' END"; // 定义不带值的 CASE 表达式 SQL 字符串
    final Project project = (Project) convertSqlToRel(sql, false); // 将 SQL 转换为关系代数，false 表示不简化 Rex
    final RexNode rex = project.getProjects().get(0); // 从 Project 节点中获取第一个投影表达式，即 CASE 表达式对应的 RexNode
    final RexToSqlNodeConverter rexToSqlNodeConverter = rexToSqlNodeConverter(); // 获取 RexToSqlNodeConverter 转换器实例
    final SqlNode convertedSql = rexToSqlNodeConverter.convertNode(rex); // 将 RexNode 转换回 SqlNode
    assertThat(convertedSql, // 断言转换结果
        hasToString("CASE WHEN NULL IS NULL THEN NULL ELSE 'a' END")); // 验证转换后的 SqlNode 字符串表示保持不变
  } // 不带值的 CASE 表达式应该保持原样转换

  private RelNode convertSqlToRel(String sql, boolean simplifyRex) { // 私有辅助方法：将 SQL 字符串转换为关系代数节点
    final FrameworkConfig config = // 声明并初始化框架配置对象
        Frameworks.newConfigBuilder() // 使用构建器模式创建框架配置
            .defaultSchema(CalciteSchema.createRootSchema(false).plus()) // 设置默认模式，创建一个空的根模式
            .parserConfig(SqlParser.config()) // 设置解析器配置，使用默认的 SQL 解析器配置
            .build(); // 构建配置对象
    final Planner planner = Frameworks.getPlanner(config); // 获取规划器实例，使用配置创建规划器
    try (Closer closer = new Closer()) { // 使用 try-with-resources 确保资源正确关闭
      closer.add(Hook.REL_BUILDER_SIMPLIFY.addThread(Hook.propertyJ(simplifyRex))); // 添加 Hook 来控制是否简化 Rex 表达式
      final SqlNode parsed = planner.parse(sql); // 第一步：解析 SQL 字符串为 SqlNode（抽象语法树）
      final SqlNode validated = planner.validate(parsed); // 第二步：验证 SqlNode，检查语义正确性
      return planner.rel(validated).rel; // 第三步：将验证后的 SqlNode 转换为关系代数树并返回 RelNode
    } catch (SqlParseException | RelConversionException | ValidationException e) { // 捕获可能发生的异常
      throw TestUtil.rethrow(e); // 将检查异常转换为运行时异常并重新抛出
    } // 异常处理结束
  } // 该方法实现了完整的 SQL 到关系代数的转换流程：解析 -> 验证 -> 转换

  private static RexToSqlNodeConverter rexToSqlNodeConverter() { // 私有静态辅助方法：创建并返回 RexToSqlNodeConverter 实例
    final RexSqlStandardConvertletTable convertletTable = // 声明并初始化转换表
        new RexSqlStandardConvertletTable(); // 创建 RexSqlStandardConvertletTable 实例，该表定义了各种 RexNode 到 SqlNode 的转换规则
    return new RexToSqlNodeConverterImpl(convertletTable); // 创建并返回 RexToSqlNodeConverterImpl 实例，使用转换表进行转换
  } // 该方法封装了转换器的创建逻辑，确保所有测试使用相同的转换配置

} // 类定义结束
