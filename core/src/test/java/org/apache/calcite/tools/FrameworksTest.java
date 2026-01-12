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
package org.apache.calcite.tools; // 声明包名，表示这个类属于 org.apache.calcite.tools 包
import org.apache.calcite.DataContext; // 导入 DataContext 接口，用于在查询执行时提供运行时上下文信息
import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入 EnumerableConvention，表示可枚举的调用约定
import org.apache.calcite.adapter.enumerable.EnumerableTableScan; // 导入 EnumerableTableScan，表示可枚举的表扫描操作
import org.apache.calcite.config.CalciteConnectionConfig; // 导入 CalciteConnectionConfig 接口，用于配置 Calcite 连接
import org.apache.calcite.config.CalciteConnectionConfigImpl; // 导入 CalciteConnectionConfigImpl 实现类
import org.apache.calcite.config.CalciteConnectionProperty; // 导入 CalciteConnectionProperty 枚举，定义连接属性
import org.apache.calcite.config.CalciteSystemProperty; // 导入 CalciteSystemProperty，定义系统属性
import org.apache.calcite.linq4j.Enumerable; // 导入 Enumerable 接口，表示可枚举的集合
import org.apache.calcite.linq4j.QueryProvider; // 导入 QueryProvider 接口，提供查询执行能力
import org.apache.calcite.linq4j.Queryable; // 导入 Queryable 接口，表示可查询的数据源
import org.apache.calcite.linq4j.tree.Expression; // 导入 Expression 类，表示 LINQ 表达式树
import org.apache.calcite.plan.ConventionTraitDef; // 导入 ConventionTraitDef，定义调用约定特征
import org.apache.calcite.plan.RelOptAbstractTable; // 导入 RelOptAbstractTable，表示抽象的优化表
import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster，表示关系代数优化集群
import org.apache.calcite.plan.RelOptPlanner; // 导入 RelOptPlanner 接口，定义优化器
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable 接口，表示优化器中的表
import org.apache.calcite.plan.RelOptUtil; // 导入 RelOptUtil 工具类，提供关系代数操作的工具方法
import org.apache.calcite.plan.RelTraitDef; // 导入 RelTraitDef 接口，定义关系特征
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系特征集合
import org.apache.calcite.plan.volcano.AbstractConverter; // 导入 AbstractConverter，表示抽象的转换规则
import org.apache.calcite.prepare.Prepare; // 导入 Prepare 类，用于 SQL 查询准备
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入 RelDistributionTraitDef，定义数据分布特征
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，表示关系代数节点
import org.apache.calcite.rel.core.TableModify; // 导入 TableModify 类，表示表修改操作
import org.apache.calcite.rel.logical.LogicalFilter; // 导入 LogicalFilter 类，表示逻辑过滤器
import org.apache.calcite.rel.logical.LogicalTableModify; // 导入 LogicalTableModify 类，表示逻辑表修改
import org.apache.calcite.rel.rules.CoreRules; // 导入 CoreRules 类，包含核心优化规则
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入 RelDataTypeFactory 接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入 RelDataTypeSystem 接口，定义类型系统
import org.apache.calcite.rel.type.RelDataTypeSystemImpl; // 导入 RelDataTypeSystemImpl 实现类
import org.apache.calcite.rex.RexBuilder; // 导入 RexBuilder 类，用于构建行表达式
import org.apache.calcite.rex.RexLiteral; // 导入 RexLiteral 类，表示字面量表达式
import org.apache.calcite.rex.RexNode; // 导入 RexNode 接口，表示行表达式节点
import org.apache.calcite.schema.ModifiableTable; // 导入 ModifiableTable 接口，表示可修改的表
import org.apache.calcite.schema.Path; // 导入 Path 类，表示 schema 路径
import org.apache.calcite.schema.ProjectableFilterableTable; // 导入 ProjectableFilterableTable 接口，表示可投影和过滤的表
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，表示增强的 schema
import org.apache.calcite.schema.Schemas; // 导入 Schemas 工具类，提供 schema 操作的工具方法
import org.apache.calcite.schema.Statistic; // 导入 Statistic 接口，表示表统计信息
import org.apache.calcite.schema.Statistics; // 导入 Statistics 工具类，提供统计信息的工厂方法
import org.apache.calcite.schema.Table; // 导入 Table 接口，表示数据表
import org.apache.calcite.schema.impl.AbstractSchema; // 导入 AbstractSchema 类，抽象的 schema 实现
import org.apache.calcite.schema.impl.AbstractTable; // 导入 AbstractTable 类，抽象的表实现
import org.apache.calcite.schema.lookup.LikePattern; // 导入 LikePattern 类，用于模式匹配
import org.apache.calcite.sql.SqlExplainFormat; // 导入 SqlExplainFormat 枚举，定义解释计划格式
import org.apache.calcite.sql.SqlExplainLevel; // 导入 SqlExplainLevel 枚举，定义解释计划详细级别
import org.apache.calcite.sql.SqlNode; // 导入 SqlNode 接口，表示 SQL 抽象语法树节点
import org.apache.calcite.sql.dialect.AnsiSqlDialect; // 导入 AnsiSqlDialect 类，表示 ANSI SQL 方言
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入 SqlStdOperatorTable 类，标准 SQL 操作符表
import org.apache.calcite.sql.parser.SqlParseException; // 导入 SqlParseException 类，SQL 解析异常
import org.apache.calcite.sql.parser.SqlParser; // 导入 SqlParser 类，SQL 解析器
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举，定义 SQL 类型名称
import org.apache.calcite.test.CalciteAssert; // 导入 CalciteAssert 类，用于测试断言
import org.apache.calcite.util.ImmutableBitSet; // 导入 ImmutableBitSet 类，不可变的位集合
import org.apache.calcite.util.TestUtil; // 导入 TestUtil 工具类，测试工具
import org.apache.calcite.util.Util; // 导入 Util 工具类，通用工具方法

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList，不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Nullable 注解，标记可能为 null 的类型
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解

import java.lang.reflect.Type; // 导入 Type 接口，表示 Java 类型
import java.math.BigDecimal; // 导入 BigDecimal 类，高精度十进制数
import java.util.ArrayList; // 导入 ArrayList 类，动态数组
import java.util.Collection; // 导入 Collection 接口，集合接口
import java.util.List; // 导入 List 接口，列表接口
import java.util.Properties; // 导入 Properties 类，属性集合

import static org.hamcrest.CoreMatchers.equalTo; // 导入 equalTo 匹配器
import static org.hamcrest.CoreMatchers.is; // 导入 is 匹配器
import static org.hamcrest.CoreMatchers.nullValue; // 导入 nullValue 匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 断言方法
import static org.hamcrest.Matchers.hasSize; // 导入 hasSize 匹配器
import static org.junit.jupiter.api.Assertions.fail; // 导入 fail 方法，使测试失败

/**
 * Unit tests for methods in {@link Frameworks}. // Frameworks 工具类的单元测试类，测试 Calcite 框架的核心功能
 */ // 包括优化器、类型系统、验证器、连接配置、JDBC 值、项目推送、更新操作等方面的测试
public class FrameworksTest { // 定义 FrameworksTest 测试类
  @Test void testOptimize() { // 测试方法：测试优化器的基本功能，构建一个简单的查询计划并优化
    RelNode x = // 声明 RelNode 变量 x，用于存储优化后的关系代数节点
        Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> { // 使用 Frameworks.withPlanner 方法创建优化器并执行操作，传入 lambda 表达式
          final RelDataTypeFactory typeFactory = cluster.getTypeFactory(); // 从集群中获取类型工厂，用于创建数据类型
          final Table table = new AbstractTable() { // 创建一个匿名 AbstractTable 实例，定义一个虚拟表
            public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写 getRowType 方法，定义表的行类型
              final RelDataType stringType = // 创建字符串类型
                  typeFactory.createJavaType(String.class); // 使用 Java String 类创建类型
              final RelDataType integerType = // 创建整数类型
                  typeFactory.createJavaType(Integer.class); // 使用 Java Integer 类创建类型
              return typeFactory.builder() // 创建类型构建器
                  .add("s", stringType) // 添加名为 "s" 的字符串列
                  .add("i", integerType) // 添加名为 "i" 的整数列
                  .build(); // 构建并返回行类型
            }
          };

          // "SELECT * FROM myTable" // 注释：表示下面的代码对应 SQL 语句 "SELECT * FROM myTable"
          final RelOptAbstractTable relOptTable = // 创建优化器抽象表
              new RelOptAbstractTable(relOptSchema, "myTable", // 指定表名为 "myTable"
                  table.getRowType(typeFactory)) { // 使用上面定义的行类型
              }; // 匿名类结束
          final EnumerableTableScan tableRel = // 创建可枚举的表扫描关系节点
              EnumerableTableScan.create(cluster, relOptTable); // 使用集群和优化表创建扫描节点

          // "WHERE i > 1" // 注释：表示下面的代码对应 WHERE 条件 "i > 1"
          final RexBuilder rexBuilder = cluster.getRexBuilder(); // 获取行表达式构建器，用于构建条件表达式
          final RexNode condition = // 创建条件表达式节点
              rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, // 创建大于操作符调用
                  rexBuilder.makeFieldAccess( // 创建字段访问表达式
                      rexBuilder.makeRangeReference(tableRel), "i", true), // 访问表扫描节点的 "i" 字段
                  rexBuilder.makeExactLiteral(BigDecimal.ONE)); // 创建字面量 1
          final LogicalFilter filter = // 创建逻辑过滤器节点
              LogicalFilter.create(tableRel, condition); // 使用表扫描节点和条件创建过滤器

          // Specify that the result should be in Enumerable convention. // 注释：指定结果应该使用 Enumerable 调用约定
          final RelNode rootRel = filter; // 将过滤器作为根节点
          final RelOptPlanner planner = cluster.getPlanner(); // 获取优化器
          RelTraitSet desiredTraits = // 创建期望的特征集合
              cluster.traitSet().replace(EnumerableConvention.INSTANCE); // 将调用约定替换为 Enumerable
          final RelNode rootRel2 = planner.changeTraits(rootRel, desiredTraits); // 改变根节点的特征
          planner.setRoot(rootRel2); // 设置优化器的根节点

          // Now, plan. // 注释：现在开始优化
          return planner.findBestExp(); // 调用优化器找到最佳执行计划
        }); // lambda 表达式结束
    String s = // 声明字符串变量 s，用于存储计划文本
        RelOptUtil.dumpPlan("", x, SqlExplainFormat.TEXT, // 将优化后的计划转储为文本格式
            SqlExplainLevel.EXPPLAN_ATTRIBUTES); // 使用 EXPPLAN_ATTRIBUTES 详细级别
    assertThat(Util.toLinux(s), // 断言计划文本（转换为 Linux 行结束符）
        equalTo("EnumerableFilter(condition=[>($1, 1)])\n" // 期望的输出：可枚举过滤器，条件为大于 1
            + "  EnumerableTableScan(table=[[myTable]])\n")); // 期望的输出：可枚举表扫描，表名为 myTable
  } // testOptimize 方法结束

  /** Unit test to test create root schema which has no "metadata" schema. */ // 单元测试：测试创建没有 "metadata" schema 的根 schema
  @Test void testCreateRootSchemaWithNoMetadataSchema() { // 测试方法：测试创建不包含元数据 schema 的根 schema
    SchemaPlus rootSchema = Frameworks.createRootSchema(false); // 创建根 schema，参数 false 表示不添加元数据 schema
    assertThat(rootSchema.subSchemas().getNames(LikePattern.any()), hasSize(0)); // 断言：根 schema 的子 schema 数量应该为 0
  } // testCreateRootSchemaWithNoMetadataSchema 方法结束

  /** Tests that validation (specifically, inferring the result of adding // 测试验证器（特别是推断两个 DECIMAL(19, 0) 值相加的结果）
   * two DECIMAL(19, 0) values together) happens differently with a type system // 在使用允许更大最大精度的类型系统时会有不同的行为
   * that allows a larger maximum precision for decimals.
   *
   * <p>Test case for // 测试用例对应 JIRA issue
   * <a href="https://issues.apache.org/jira/browse/CALCITE-413">[CALCITE-413] // CALCITE-413: 添加 RelDataTypeSystem 插件，允许 DECIMAL 的不同最大精度
   * Add RelDataTypeSystem plugin, allowing different max precision of a
   * DECIMAL</a>.
   *
   * <p>Also tests the plugin system, by specifying implementations of a // 同时测试插件系统，通过指定插件接口的实现（使用公共和私有构造函数）
   * plugin interface with public and private constructors. */
  @Test void testTypeSystem() { // 测试方法：测试类型系统插件功能
    checkTypeSystem(19, Frameworks.newConfigBuilder().build()); // 检查默认类型系统，期望精度为 19
    checkTypeSystem(25, Frameworks.newConfigBuilder() // 检查 HiveLikeTypeSystem，期望精度为 25
        .typeSystem(HiveLikeTypeSystem.INSTANCE).build()); // 使用 HiveLikeTypeSystem 实例
    checkTypeSystem(31, Frameworks.newConfigBuilder() // 检查 HiveLikeTypeSystem2，期望精度为 31
        .typeSystem(new HiveLikeTypeSystem2()).build()); // 使用 HiveLikeTypeSystem2 实例
  } // testTypeSystem 方法结束

  private void checkTypeSystem(final int expected, FrameworkConfig config) { // 私有方法：检查类型系统的精度是否符合期望
    Frameworks.withPrepare(config, // 使用指定的配置执行准备操作
        (cluster, relOptSchema, rootSchema, statement) -> { // lambda 表达式：接收集群、优化 schema、根 schema 和语句
          final RelDataType type = // 创建 DECIMAL 类型，精度 30，标度 2
              cluster.getTypeFactory() // 获取类型工厂
                  .createSqlType(SqlTypeName.DECIMAL, 30, 2); // 创建 DECIMAL(30, 2) 类型
          final RexLiteral literal = // 创建字面量，值为 1，类型为 DECIMAL(30, 2)
              cluster.getRexBuilder().makeExactLiteral(BigDecimal.ONE, type); // 使用精确字面量创建
          final RexNode call = // 创建加法表达式：1 + 1
              cluster.getRexBuilder().makeCall(SqlStdOperatorTable.PLUS, // 使用加法操作符
                  literal, // 第一个操作数：字面量 1
                  literal); // 第二个操作数：字面量 1
          assertThat(call.getType().getPrecision(), is(expected)); // 断言：结果类型的精度应该等于期望值
          return null; // 返回 null
        }); // lambda 表达式结束
  } // checkTypeSystem 方法结束

  /** Tests that the validator expands identifiers by default. // 测试验证器默认是否展开标识符
   *
   * <p>Test case for // 测试用例对应 JIRA issue
   * <a href="https://issues.apache.org/jira/browse/CALCITE-593">[CALCITE-593] // CALCITE-593: Frameworks 中的验证器应该展开标识符
   * Validator in Frameworks should expand identifiers</a>.
   */
  @Test void testFrameworksValidatorWithIdentifierExpansion() // 测试方法：测试验证器的标识符展开功能
      throws Exception { // 可能抛出异常
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根 schema，参数 true 表示添加元数据 schema
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
        .defaultSchema( // 设置默认 schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR)) // 添加 HR schema
        .build(); // 构建配置
    final Planner planner = Frameworks.getPlanner(config); // 获取规划器实例
    SqlNode parse = planner.parse("select * from \"emps\" "); // 解析 SQL 语句："select * from \"emps\""
    SqlNode val = planner.validate(parse); // 验证解析后的 SQL 节点

    String valStr = // 将验证后的节点转换为 SQL 字符串
        val.toSqlString(AnsiSqlDialect.DEFAULT, false).getSql(); // 使用 ANSI SQL 方言，不进行美化

    String expandedStr = // 期望的展开后的 SQL 字符串
        "SELECT `emps`.`empid`, `emps`.`deptno`, `emps`.`name`, `emps`.`salary`, `emps`.`commission`\n" // 展开为所有列
            + "FROM `hr`.`emps` AS `emps`"; // 包含 schema 名称和表别名
    assertThat(Util.toLinux(valStr), equalTo(expandedStr)); // 断言：验证后的 SQL 应该等于期望的展开字符串
  } // testFrameworksValidatorWithIdentifierExpansion 方法结束

  /** Test for {@link Path}. */ // 测试 Path 类的功能
  @Test void testSchemaPath() { // 测试方法：测试 schema 路径功能
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根 schema，添加元数据 schema
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
        .defaultSchema( // 设置默认 schema
            CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.HR)) // 添加 HR schema
        .build(); // 构建配置
    final Path path = Schemas.path(config.getDefaultSchema()); // 获取默认 schema 的路径
    assertThat(path, hasSize(2)); // 断言：路径应该有 2 个元素（根和 hr）
    assertThat(path.get(0).left, is("")); // 断言：第一个元素的名称应该是空字符串（根）
    assertThat(path.get(1).left, is("hr")); // 断言：第二个元素的名称应该是 "hr"
    assertThat(path.names(), hasSize(1)); // 断言：路径名称列表应该有 1 个元素
    assertThat(path.names().get(0), is("hr")); // 断言：第一个名称应该是 "hr"
    assertThat(path.schemas(), hasSize(2)); // 断言：schema 列表应该有 2 个元素

    final Path parent = path.parent(); // 获取父路径
    assertThat(parent, hasSize(1)); // 断言：父路径应该有 1 个元素（根）
    assertThat(parent.names(), hasSize(0)); // 断言：父路径的名称列表应该为空

    final Path grandparent = parent.parent(); // 获取祖父路径
    assertThat(grandparent, hasSize(0)); // 断言：祖父路径应该为空

    try { // 尝试获取祖父路径的父路径（应该抛出异常）
      Object o = grandparent.parent(); // 调用 parent() 方法
      fail("expected exception, got " + o); // 如果没有抛出异常，测试失败
    } catch (IllegalArgumentException e) { // 捕获预期的异常
      // ok // 异常是预期的，测试通过
    } // try-catch 结束
  } // testSchemaPath 方法结束

  /** Unit test for {@link CalciteConnectionConfigImpl#set} // 单元测试：测试 CalciteConnectionConfigImpl 的 set 方法
   * and {@link CalciteConnectionConfigImpl#isSet}. */ // 和 isSet 方法
  @Test void testConnectionConfig() { // 测试方法：测试连接配置的设置和查询功能
    final CalciteConnectionProperty forceDecorrelate = // 获取强制去相关属性
        CalciteConnectionProperty.FORCE_DECORRELATE; // 用于控制是否强制去相关子查询
    final CalciteConnectionProperty lenientOperatorLookup = // 获取宽松操作符查找属性
        CalciteConnectionProperty.LENIENT_OPERATOR_LOOKUP; // 用于控制操作符查找的宽松程度
    final CalciteConnectionProperty caseSensitive = // 获取大小写敏感属性
        CalciteConnectionProperty.CASE_SENSITIVE; // 用于控制标识符是否区分大小写
    final CalciteConnectionProperty model = // 获取模型属性
        CalciteConnectionProperty.MODEL; // 用于指定模型文件路径

    final Properties p = new Properties(); // 创建 Properties 对象，用于存储配置属性
    p.setProperty(forceDecorrelate.camelName(), // 设置强制去相关属性为 false
        Boolean.toString(false)); // 使用驼峰命名法设置属性名
    p.setProperty(lenientOperatorLookup.camelName(), // 设置宽松操作符查找属性为 false
        Boolean.toString(false)); // 使用驼峰命名法设置属性名

    final CalciteConnectionConfigImpl c = new CalciteConnectionConfigImpl(p); // 使用 Properties 创建连接配置实例

    assertThat(c.lenientOperatorLookup(), is(false)); // 断言：宽松操作符查找应该是 false
    assertThat(c.isSet(lenientOperatorLookup), is(true)); // 断言：宽松操作符查找属性应该已设置
    assertThat(c.caseSensitive(), is(true)); // 断言：大小写敏感应该是 true（默认值）
    assertThat(c.isSet(caseSensitive), is(false)); // 断言：大小写敏感属性应该未设置
    assertThat(c.forceDecorrelate(), is(false)); // 断言：强制去相关应该是 false
    assertThat(c.isSet(forceDecorrelate), is(true)); // 断言：强制去相关属性应该已设置
    assertThat(c.model(), nullValue()); // 断言：模型应该是 null
    assertThat(c.isSet(model), is(false)); // 断言：模型属性应该未设置

    final CalciteConnectionConfigImpl c2 = c // 基于配置 c 创建新配置 c2
        .set(lenientOperatorLookup, Boolean.toString(true)) // 设置宽松操作符查找为 true
        .set(caseSensitive, Boolean.toString(true)); // 设置大小写敏感为 true

    assertThat(c2.lenientOperatorLookup(), is(true)); // 断言：宽松操作符查找应该是 true
    assertThat(c2.isSet(lenientOperatorLookup), is(true)); // 断言：宽松操作符查找属性应该已设置
    assertThat("same value as for c", c2.caseSensitive(), is(true)); // 断言：大小写敏感应该与 c 相同
    assertThat("set to the default value", c2.isSet(caseSensitive), is(true)); // 断言：即使设置为默认值，属性也应该已设置
    assertThat(c2.forceDecorrelate(), is(false)); // 断言：强制去相关应该是 false
    assertThat(c2.isSet(forceDecorrelate), is(true)); // 断言：强制去相关属性应该已设置
    assertThat(c2.model(), nullValue()); // 断言：模型应该是 null
    assertThat(c2.isSet(model), is(false)); // 断言：模型属性应该未设置
    assertThat("retrieves default because not set", c2.schema(), nullValue()); // 断言：schema 应该返回默认值 null

    // Create a config similar to c2 but starting from an empty Properties. // 注释：从空的 Properties 创建类似 c2 的配置
    final CalciteConnectionConfigImpl c3 = CalciteConnectionConfig.DEFAULT; // 获取默认的连接配置
    final CalciteConnectionConfigImpl c4 = c3 // 基于默认配置创建新配置 c4
        .set(lenientOperatorLookup, Boolean.toString(true)) // 设置宽松操作符查找为 true
        .set(caseSensitive, Boolean.toString(true)); // 设置大小写敏感为 true
    assertThat(c4.lenientOperatorLookup(), is(true)); // 断言：宽松操作符查找应该是 true
    assertThat(c4.isSet(lenientOperatorLookup), is(true)); // 断言：宽松操作符查找属性应该已设置
    assertThat(c4.caseSensitive(), is(true)); // 断言：大小写敏感应该是 true
    assertThat("set to the default value", c4.isSet(caseSensitive), is(true)); // 断言：即使设置为默认值，属性也应该已设置
    assertThat("different from c2", c4.forceDecorrelate(), is(true)); // 断言：强制去相关应该与 c2 不同（默认值 true）
    assertThat("different from c2", c4.isSet(forceDecorrelate), is(false)); // 断言：强制去相关属性应该未设置（与 c2 不同）
    assertThat(c4.model(), nullValue()); // 断言：模型应该是 null
    assertThat(c4.isSet(model), is(false)); // 断言：模型属性应该未设置
    assertThat("retrieves default because not set", c4.schema(), nullValue()); // 断言：schema 应该返回默认值 null

    // Call 'unset' on a few properties. // 注释：对一些属性调用 unset 方法
    final CalciteConnectionConfigImpl c5 = c2.unset(lenientOperatorLookup); // 取消设置宽松操作符查找属性
    assertThat(c5.isSet(lenientOperatorLookup), is(false)); // 断言：宽松操作符查找属性应该未设置
    assertThat(c5.lenientOperatorLookup(), is(false)); // 断言：宽松操作符查找应该返回默认值 false
    assertThat(c5.isSet(caseSensitive), is(true)); // 断言：大小写敏感属性应该已设置
    assertThat(c5.caseSensitive(), is(true)); // 断言：大小写敏感应该是 true

    // Call 'set' on properties that have already been set. // 注释：对已经设置的属性调用 set 方法
    final CalciteConnectionConfigImpl c6 = c5 // 基于配置 c5 创建新配置 c6
        .set(lenientOperatorLookup, Boolean.toString(false)) // 设置宽松操作符查找为 false
        .set(forceDecorrelate, Boolean.toString(true)); // 设置强制去相关为 true
    assertThat(c6.isSet(lenientOperatorLookup), is(true)); // 断言：宽松操作符查找属性应该已设置
    assertThat(c6.lenientOperatorLookup(), is(false)); // 断言：宽松操作符查找应该是 false
    assertThat(c6.isSet(caseSensitive), is(true)); // 断言：大小写敏感属性应该已设置
    assertThat(c6.caseSensitive(), is(true)); // 断言：大小写敏感应该是 true
    assertThat(c6.isSet(forceDecorrelate), is(true)); // 断言：强制去相关属性应该已设置
    assertThat(c6.forceDecorrelate(), is(true)); // 断言：强制去相关应该是 true
  } // testConnectionConfig 方法结束

  /** Test case for // 测试用例对应 JIRA issue
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1996">[CALCITE-1996] // CALCITE-1996: VALUES 语法问题
   * VALUES syntax</a>.
   *
   * <p>With that bug, running a VALUES query would succeed before running a // 在该 bug 中，VALUES 查询在读取 JDBC 表的查询之前运行会成功，
   * query that reads from a JDBC table, but fail after it. Before, the plan // 但在之后运行会失败。之前计划会使用 EnumerableValues，
   * would use {@link org.apache.calcite.adapter.enumerable.EnumerableValues}, // 但之后会使用 JdbcRules.JdbcValues，并生成无效的 SQL 语法
   * but after, it would use
   * {@link org.apache.calcite.adapter.jdbc.JdbcRules.JdbcValues}, and would
   * generate invalid SQL syntax.
   *
   * <p>Even though the SQL generator has been fixed, we are still interested in // 尽管 SQL 生成器已修复，我们仍然对 JDBC 约定如何嵌入优化器状态感兴趣
   * how JDBC convention gets lodged in the planner's state. */
  @Test void testJdbcValues() throws Exception { // 测试方法：测试 JDBC 值查询问题
    CalciteAssert.that() // 创建 Calcite 断言
        .with(CalciteAssert.SchemaSpec.JDBC_SCOTT) // 使用 JDBC_SCOTT schema
        .doWithConnection(connection -> { // 使用连接执行操作
          try { // 尝试执行
            final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置
                .defaultSchema(connection.getRootSchema()) // 设置默认 schema
                .build(); // 构建配置
            final RelBuilder builder = RelBuilder.create(config); // 创建关系构建器
            final RelRunner runner = connection.unwrap(RelRunner.class); // 获取关系运行器

            final RelNode values = // 创建 VALUES 关系节点
                builder.values(new String[]{"a", "b"}, "X", 1, "Y", 2) // 创建两行数据：("X", 1) 和 ("Y", 2)
                    .project(builder.field("a")) // 投影字段 "a"
                    .build(); // 构建关系节点

            // If you run the "values" query before the "scan" query, // 注释：如果在 "scan" 查询之前运行 "values" 查询，
            // everything works fine. JdbcValues is never instantiated in any // 一切正常。JdbcValues 在任何查询中都不会实例化
            // of the 3 queries.
            if (false) { // 条件为 false，下面的代码不会执行
              runner.prepareStatement(values).executeQuery(); // 执行 VALUES 查询
            } // if 结束

            final RelNode scan = builder.scan("JDBC_SCOTT", "EMP").build(); // 创建表扫描关系节点
            runner.prepareStatement(scan).executeQuery(); // 执行表扫描查询
            builder.clear(); // 清除构建器状态

            // running this after the scott query causes the exception // 注释：在 scott 查询之后运行这个会导致异常
            RelRunner runner2 = connection.unwrap(RelRunner.class); // 再次获取关系运行器
            runner2.prepareStatement(values).executeQuery(); // 执行 VALUES 查询（应该会成功，因为 bug 已修复）
          } catch (Exception e) { // 捕获异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          } // try-catch 结束
        }); // lambda 表达式结束
  } // testJdbcValues 方法结束

  /** Test case for // 测试用例对应 JIRA issue

     * <a href="https://issues.apache.org/jira/browse/CALCITE-3228">[CALCITE-3228] // CALCITE-3228: 应用 ProjectScanRule:interpreter 规则时出错

     * Error while applying rule ProjectScanRule:interpreter</a>

     *

     * <p>This bug appears under the following conditions: // 该 bug 在以下条件下出现：

     * 1) have an aggregate with group by and multi aggregate calls. // 1) 有一个带有 group by 和多个聚合调用的聚合

     * 2) the aggregate can be removed during optimization. // 2) 聚合可以在优化过程中被移除

     * 3) all aggregate calls are simplified to the same reference. // 3) 所有聚合调用都被简化为相同的引用

     *  */

    @Test void testPushProjectToScan() throws Exception { // 测试方法：测试将项目推送到扫描操作

      Table table = new TableImpl(); // 创建测试表实例

      final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根 schema，添加元数据 schema

      SchemaPlus schema = rootSchema.add("x", new AbstractSchema()); // 添加名为 "x" 的 schema

      schema.add("MYTABLE", table); // 在 schema 中添加名为 "MYTABLE" 的表

      List<RelTraitDef> traitDefs = new ArrayList<>(); // 创建特征定义列表

      traitDefs.add(ConventionTraitDef.INSTANCE); // 添加调用约定特征定义

      traitDefs.add(RelDistributionTraitDef.INSTANCE); // 添加数据分布特征定义

      SqlParser.Config parserConfig = // 创建解析器配置

          SqlParser.Config.DEFAULT // 使用默认配置

              .withCaseSensitive(false); // 设置为不区分大小写

  

      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器

          .parserConfig(parserConfig) // 设置解析器配置

          .defaultSchema(schema) // 设置默认 schema

          .traitDefs(traitDefs) // 设置特征定义

          // define the rules you want to apply // 注释：定义要应用的规则

          .ruleSets( // 设置规则集

              RuleSets.ofList(AbstractConverter.ExpandConversionRule.INSTANCE, // 添加扩展转换规则

                  CoreRules.PROJECT_TABLE_SCAN)) // 添加项目表扫描规则

          .programs(Programs.ofRules(Programs.RULE_SET)) // 设置程序

          .build(); // 构建配置

  

      final String sql = "select min(id) as mi, max(id) as ma\n" // SQL 查询字符串：选择 id 的最小值和最大值

          + "from mytable where id=1 group by id"; // 从 mytable 表中，id=1 的记录按 id 分组

      executeQuery(config, sql, CalciteSystemProperty.DEBUG.value()); // 执行查询，传入配置、SQL 和调试标志

    } // testPushProjectToScan 方法结束

  /** Test case for // 测试用例对应 JIRA issue
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2039">[CALCITE-2039] // CALCITE-2039: 将项目推送到 ProjectableFilterableTable 时出现 AssertionError
   * AssertionError when pushing project to ProjectableFilterableTable</a>
   * using UPDATE via {@link Frameworks}. */ // 通过 Frameworks 使用 UPDATE 操作
  @Test void testUpdate() throws Exception { // 测试方法：测试更新操作
    Table table = new TableImpl(); // 创建测试表实例
    final SchemaPlus rootSchema = Frameworks.createRootSchema(true); // 创建根 schema，添加元数据 schema
    SchemaPlus schema = rootSchema.add("x", new AbstractSchema()); // 添加名为 "x" 的 schema
    schema.add("MYTABLE", table); // 在 schema 中添加名为 "MYTABLE" 的表
    List<RelTraitDef> traitDefs = new ArrayList<>(); // 创建特征定义列表
    traitDefs.add(ConventionTraitDef.INSTANCE); // 添加调用约定特征定义
    traitDefs.add(RelDistributionTraitDef.INSTANCE); // 添加数据分布特征定义
    SqlParser.Config parserConfig = // 创建解析器配置
        SqlParser.Config.DEFAULT // 使用默认配置
            .withCaseSensitive(false); // 设置为不区分大小写

    final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
        .parserConfig(parserConfig) // 设置解析器配置
        .defaultSchema(schema) // 设置默认 schema
        .traitDefs(traitDefs) // 设置特征定义
        // define the rules you want to apply // 注释：定义要应用的规则
        .ruleSets( // 设置规则集
            RuleSets.ofList(AbstractConverter.ExpandConversionRule.INSTANCE)) // 添加扩展转换规则
        .programs(Programs.ofRules(Programs.RULE_SET)) // 设置程序
        .build(); // 构建配置
    executeQuery(config, " UPDATE MYTABLE set id=7 where id=1", // 执行 UPDATE 查询：将 id=1 的记录的 id 更新为 7
        CalciteSystemProperty.DEBUG.value()); // 传入调试标志
  } // testUpdate 方法结束

  private void executeQuery(FrameworkConfig config, // 私有方法：执行查询，传入框架配置
      @SuppressWarnings("SameParameterValue") String query, boolean debug) // 传入查询字符串和调试标志（忽略参数未使用警告）
      throws RelConversionException, SqlParseException, ValidationException { // 可能抛出关系转换异常、SQL 解析异常和验证异常
    Planner planner = Frameworks.getPlanner(config); // 获取规划器实例
    if (debug) { // 如果启用调试
      System.out.println("Query:" + query); // 打印查询字符串
    } // if 结束
    SqlNode n = planner.parse(query); // 解析 SQL 查询为 SQL 节点
    n = planner.validate(n); // 验证 SQL 节点
    RelNode root = planner.rel(n).project(); // 将 SQL 节点转换为关系节点
    if (debug) { // 如果启用调试
      System.out.println( // 打印逻辑计划
          RelOptUtil.dumpPlan("-- Logical Plan", root, SqlExplainFormat.TEXT, // 使用文本格式和摘要属性级别
              SqlExplainLevel.DIGEST_ATTRIBUTES)); // 设置摘要属性级别
    } // if 结束
    RelOptCluster cluster = root.getCluster(); // 获取关系优化集群
    final RelOptPlanner optPlanner = cluster.getPlanner(); // 获取优化器

    RelTraitSet desiredTraits  = // 创建期望的特征集合
        cluster.traitSet().replace(EnumerableConvention.INSTANCE); // 将调用约定替换为 Enumerable
    final RelNode newRoot = optPlanner.changeTraits(root, desiredTraits); // 改变根节点的特征
    if (debug) { // 如果启用调试
      System.out.println( // 打印中间计划
          RelOptUtil.dumpPlan("-- Mid Plan", newRoot, SqlExplainFormat.TEXT, // 使用文本格式和摘要属性级别
              SqlExplainLevel.DIGEST_ATTRIBUTES)); // 设置摘要属性级别
    } // if 结束
    optPlanner.setRoot(newRoot); // 设置优化器的根节点
    RelNode bestExp = optPlanner.findBestExp(); // 查找最佳执行计划
    if (debug) { // 如果启用调试
      System.out.println( // 打印最佳计划
          RelOptUtil.dumpPlan("-- Best Plan", bestExp, SqlExplainFormat.TEXT, // 使用文本格式和摘要属性级别
              SqlExplainLevel.DIGEST_ATTRIBUTES)); // 设置摘要属性级别
    } // if 结束
  } // executeQuery 方法结束

  /** Modifiable, filterable table. */ // 可修改、可过滤的表
  private static class TableImpl extends AbstractTable // 私有静态内部类：表实现，继承 AbstractTable
      implements ModifiableTable, ProjectableFilterableTable { // 实现 ModifiableTable 和 ProjectableFilterableTable 接口
    TableImpl() {} // 默认构造函数

    public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写 getRowType 方法，定义表的行类型
      return typeFactory.builder() // 创建类型构建器
          .add("id", typeFactory.createSqlType(SqlTypeName.INTEGER)) // 添加名为 "id" 的整数列
          .add("name", typeFactory.createSqlType(SqlTypeName.INTEGER)) // 添加名为 "name" 的整数列
          .build(); // 构建并返回行类型
    } // getRowType 方法结束

    public Statistic getStatistic() { // 重写 getStatistic 方法，返回表的统计信息
      return Statistics.of(15D, // 返回统计信息：行数为 15
          ImmutableList.of(ImmutableBitSet.of(0)), // 第 0 列（id）是唯一的
          ImmutableList.of()); // 没有外键
    } // getStatistic 方法结束

    public Enumerable<@Nullable Object[]> scan(DataContext root, List<RexNode> filters, // 重写 scan 方法，扫描表数据
        int @Nullable [] projects) { // 传入数据上下文、过滤条件和投影列
      throw new UnsupportedOperationException(); // 抛出不支持操作异常
    } // scan 方法结束

    public Collection getModifiableCollection() { // 重写 getModifiableCollection 方法，获取可修改的集合
      throw new UnsupportedOperationException(); // 抛出不支持操作异常
    } // getModifiableCollection 方法结束

    public TableModify toModificationRel(RelOptCluster cluster, // 重写 toModificationRel 方法，创建修改关系节点
        RelOptTable table, Prepare.CatalogReader catalogReader, RelNode child, // 传入集群、表、目录读取器、子节点
        TableModify.Operation operation, List<String> updateColumnList, // 传入操作类型、更新列列表
        List<RexNode> sourceExpressionList, boolean flattened) { // 传入源表达式列表和扁平化标志
      return LogicalTableModify.create(table, catalogReader, child, operation, // 创建逻辑表修改节点
          updateColumnList, sourceExpressionList, flattened); // 返回逻辑表修改节点
    } // toModificationRel 方法结束

    public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 重写 asQueryable 方法，转换为可查询对象
        SchemaPlus schema, String tableName) { // 传入查询提供者、schema 和表名
      throw new UnsupportedOperationException(); // 抛出不支持操作异常
    } // asQueryable 方法结束

    public Type getElementType() { // 重写 getElementType 方法，获取元素类型
      return Object.class; // 返回 Object 类型
    } // getElementType 方法结束

    public Expression getExpression(SchemaPlus schema, String tableName, // 重写 getExpression 方法，获取表达式
        Class clazz) { // 传入 schema、表名和类
      return null; // 返回 null
    } // getExpression 方法结束
  } // TableImpl 内部类结束

  /** Dummy type system, similar to Hive's, accessed via an INSTANCE member. */ // 虚拟类型系统，类似于 Hive 的，通过 INSTANCE 成员访问
  public static class HiveLikeTypeSystem extends RelDataTypeSystemImpl { // 公共静态内部类：类似 Hive 的类型系统，继承 RelDataTypeSystemImpl
    public static final RelDataTypeSystem INSTANCE = new HiveLikeTypeSystem(); // 静态实例，用于单例模式

    private HiveLikeTypeSystem() {} // 私有构造函数，防止外部实例化

    @Override public int getMaxNumericPrecision() { // 重写 getMaxNumericPrecision 方法，获取最大数值精度
      assert super.getMaxNumericPrecision() == 19; // 断言：父类的最大数值精度是 19
      return getMaxPrecision(SqlTypeName.DECIMAL); // 返回 DECIMAL 类型的最大精度
    } // getMaxNumericPrecision 方法结束

    @Override public int getMaxPrecision(SqlTypeName typeName) { // 重写 getMaxPrecision 方法，获取指定类型的最大精度
      switch (typeName) { // 根据 typeName 切换
      case DECIMAL: // 如果是 DECIMAL 类型
        return 25; // 返回最大精度 25
      default: // 其他类型
        return super.getMaxPrecision(typeName); // 返回父类的最大精度
      } // switch 结束
    } // getMaxPrecision 方法结束
  } // HiveLikeTypeSystem 内部类结束

  /** Dummy type system, similar to Hive's, accessed via a public default // 虚拟类型系统，类似于 Hive 的，通过公共默认构造函数访问
   * constructor. */
  public static class HiveLikeTypeSystem2 extends RelDataTypeSystemImpl { // 公共静态内部类：类似 Hive 的类型系统 2，继承 RelDataTypeSystemImpl
    public HiveLikeTypeSystem2() {} // 公共默认构造函数

    @Override public int getMaxNumericPrecision() { // 重写 getMaxNumericPrecision 方法，获取最大数值精度
      assert super.getMaxNumericPrecision() == 19; // 断言：父类的最大数值精度是 19
      return getMaxPrecision(SqlTypeName.DECIMAL); // 返回 DECIMAL 类型的最大精度
    } // getMaxNumericPrecision 方法结束

    @Override public int getMaxPrecision(SqlTypeName typeName) { // 重写 getMaxPrecision 方法，获取指定类型的最大精度
      switch (typeName) { // 根据 typeName 切换
      case DECIMAL: // 如果是 DECIMAL 类型
        return 38; // 返回最大精度 38
      default: // 其他类型
        return super.getMaxPrecision(typeName); // 返回父类的最大精度
      } // switch 结束
    } // getMaxPrecision 方法结束
  } // HiveLikeTypeSystem2 内部类结束
} // FrameworksTest 类结束
