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
// Apache许可证头文件，声明版权和使用许可
package org.apache.calcite.test; // 定义包名，该测试类位于org.apache.calcite.test包下

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入反射模式类，用于通过反射创建schema
import org.apache.calcite.materialize.MaterializationService; // 导入物化视图服务类，用于管理物化视图
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表接口，表示优化器中的表
import org.apache.calcite.prepare.Prepare; // 导入准备类，用于SQL查询准备阶段
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，表示关系代数树中的节点
import org.apache.calcite.rel.RelReferentialConstraint; // 导入关系引用约束接口，表示外键约束
import org.apache.calcite.rel.RelReferentialConstraintImpl; // 导入关系引用约束实现类
import org.apache.calcite.rel.RelVisitor; // 导入关系访问者类，用于遍历关系代数树
import org.apache.calcite.rel.core.TableScan; // 导入表扫描类，表示对表的扫描操作
import org.apache.calcite.runtime.Hook; // 导入钩子类，用于在特定点插入自定义逻辑
import org.apache.calcite.schema.QueryableTable; // 导入可查询表接口，表示可以被查询的表
import org.apache.calcite.schema.TranslatableTable; // 导入可转换表接口，表示可以转换为关系代数的表
import org.apache.calcite.test.schemata.hr.Department; // 导入部门类，HR schema中的部门表
import org.apache.calcite.test.schemata.hr.DepartmentPlus; // 导入扩展部门类
import org.apache.calcite.test.schemata.hr.Dependent; // 导入家属类，HR schema中的家属表
import org.apache.calcite.test.schemata.hr.Employee; // 导入员工类，HR schema中的员工表
import org.apache.calcite.test.schemata.hr.Event; // 导入事件类
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HR schema类，人力资源模式的schema
import org.apache.calcite.test.schemata.hr.Location; // 导入位置类
import org.apache.calcite.test.schemata.hr.NullableTest; // 导入可空测试类
import org.apache.calcite.util.JsonBuilder; // 导入JSON构建器类，用于构建JSON字符串
import org.apache.calcite.util.Smalls; // 导入小型工具类，包含各种测试辅助方法
import org.apache.calcite.util.TryThreadLocal; // 导入线程本地工具类
import org.apache.calcite.util.mapping.IntPair; // 导入整数对类，用于表示列对

import com.google.common.collect.ImmutableList; // 导入不可变列表类，Google Guava库
import com.google.common.collect.Ordering; // 导入排序类，Google Guava库

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于静态检查
import org.junit.jupiter.api.Disabled; // 导入禁用测试注解，Jupiter测试框架
import org.junit.jupiter.api.Tag; // 导入标签注解，用于标记测试
import org.junit.jupiter.api.Test; // 导入测试注解，Jupiter测试框架

import java.sql.ResultSet; // 导入结果集接口，JDBC API
import java.sql.Timestamp; // 导入时间戳类，JDBC API
import java.util.ArrayList; // 导入数组列表类，Java集合框架
import java.util.Arrays; // 导入数组工具类
import java.util.Collections; // 导入集合工具类
import java.util.List; // 导入列表接口，Java集合框架
import java.util.Map; // 导入映射接口，Java集合框架
import java.util.function.Consumer; // 导入消费者函数式接口，Java 8

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器is方法
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具

/**
 * Integration tests for the materialized view rewrite mechanism. Each test has a
 * query and one or more materializations (what Oracle calls materialized views)
 * and checks that the materialization is used.
 */
// 物化视图重写机制的集成测试类，每个测试都有一个查询和一个或多个物化视图（Oracle中称为物化视图），并检查物化视图是否被使用
@Tag("slow") // 标记为慢速测试，执行时间较长
public class MaterializationTest { // 定义物化视图测试类
  private static final Consumer<ResultSet> CONTAINS_M0 = // 定义静态常量：检查结果集是否包含m0物化视图的消费者
      CalciteAssert.checkResultContains( // 调用CalciteAssert的checkResultContains方法
          "EnumerableTableScan(table=[[hr, m0]])"); // 检查是否包含对hr.m0表的枚举表扫描

  private static final Consumer<ResultSet> CONTAINS_LOCATIONS = // 定义静态常量：检查结果集是否包含locations物化视图的消费者
      CalciteAssert.checkResultContains( // 调用CalciteAssert的checkResultContains方法
          "EnumerableTableScan(table=[[hr, locations]])"); // 检查是否包含对hr.locations表的枚举表扫描

  private static final Ordering<Iterable<String>> CASE_INSENSITIVE_LIST_COMPARATOR = // 定义静态常量：不区分大小写的字符串列表比较器
      Ordering.from(String.CASE_INSENSITIVE_ORDER).lexicographical(); // 从不区分大小写的字符串顺序创建比较器，并按字典序排列

  private static final Ordering<Iterable<List<String>>> CASE_INSENSITIVE_LIST_LIST_COMPARATOR = // 定义静态常量：不区分大小写的字符串列表的列表比较器
      CASE_INSENSITIVE_LIST_COMPARATOR.lexicographical(); // 基于字符串列表比较器创建字典序比较器

  private static final String HR_FKUK_SCHEMA = "{\n" // 定义静态常量：HR FK-UK schema的JSON配置字符串
      + "       type: 'custom',\n" // schema类型为自定义
      + "       name: 'hr',\n" // schema名称为hr
      + "       factory: '" // 指定工厂类
      + ReflectiveSchema.Factory.class.getName() // 使用反射schema工厂类
      + "',\n"
      + "       operand: {\n" // 工厂参数
      + "         class: '" + HrFKUKSchema.class.getName() + "'\n" // 指定HrFKUKSchema类作为schema实现
      + "       }\n"
      + "     }\n"; // JSON配置结束

  private static final String HR_FKUK_MODEL = "{\n" // 定义静态常量：HR FK-UK模型的JSON配置字符串
      + "  version: '1.0',\n" // 模型版本为1.0
      + "  defaultSchema: 'hr',\n" // 默认schema为hr
      + "   schemas: [\n" // schemas数组开始
      + HR_FKUK_SCHEMA // 引用HR_FKUK_SCHEMA配置
      + "   ]\n" // schemas数组结束
      + "}"; // 模型配置结束

  @Test void testScan() { // 测试方法：测试基本的表扫描物化视图重写
    CalciteAssert.that() // 创建CalciteAssert断言构建器
        .withMaterializations( // 配置物化视图
            "{\n" // JSON配置开始
                + "  version: '1.0',\n" // 版本1.0
                + "  defaultSchema: 'SCOTT_CLONE',\n" // 默认schema为SCOTT_CLONE
                + "  schemas: [ {\n" // schemas数组开始
                + "    name: 'SCOTT_CLONE',\n" // schema名称
                + "    type: 'custom',\n" // 自定义类型
                + "    factory: 'org.apache.calcite.adapter.clone.CloneSchema$Factory',\n" // 使用克隆schema工厂
                + "    operand: {\n" // 工厂参数开始
                + "      jdbcDriver: '" + JdbcTest.SCOTT.driver + "',\n" // JDBC驱动
                + "      jdbcUser: '" + JdbcTest.SCOTT.username + "',\n" // JDBC用户名
                + "      jdbcPassword: '" + JdbcTest.SCOTT.password + "',\n" // JDBC密码
                + "      jdbcUrl: '" + JdbcTest.SCOTT.url + "',\n" // JDBC URL
                + "      jdbcSchema: 'SCOTT'\n" // JDBC schema名称
                + "   } } ]\n" // 工厂参数和schemas数组结束
                + "}", // JSON配置结束
            "m0", // 物化视图名称
            "select empno, deptno from emp order by deptno") // 物化视图SQL
        .query( // 执行查询
            "select empno, deptno from emp") // 查询SQL
        .enableMaterializations(true) // 启用物化视图
        .explainContains("EnumerableTableScan(table=[[SCOTT_CLONE, m0]])") // 验证执行计划包含对m0的扫描
        .sameResultWithMaterializationsDisabled(); // 验证禁用物化视图后结果相同
  } // 测试方法结束

  @Test void testViewMaterialization() { // 测试方法：测试视图物化
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      String materialize = "select \"depts\".\"name\"\n" // 定义物化视图SQL：选择部门名称
          + "from \"depts\"\n" // 从部门表
          + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")"; // 与员工表连接
      String query = "select \"depts\".\"name\"\n" // 定义查询SQL：与物化视图相同的查询
          + "from \"depts\"\n" // 从部门表
          + "join \"emps\" on (\"emps\".\"deptno\" = \"depts\".\"deptno\")"; // 与员工表连接

      CalciteAssert.that() // 创建断言构建器
          .withMaterializations(HR_FKUK_MODEL, true, "matview", materialize) // 配置物化视图，使用HR_FKUK_MODEL，启用自动创建，名称为matview
          .query(query) // 执行查询
          .enableMaterializations(true) // 启用物化视图
          .explainMatches( // 验证执行计划匹配
              "", CalciteAssert.checkResultContains( // 检查结果包含
              "EnumerableValues(tuples=[[{ 'noname' }]])")).returnsValue("noname"); // 验证返回值为noname
    } // try-with-resources结束，自动清理资源
  } // 测试方法结束

  @Test void testTableModify() { // 测试方法：测试表修改操作的物化视图重写
    final String m = "select \"deptno\", \"empid\", \"name\"" // 定义物化视图SQL：选择部门编号、员工ID和姓名
        + "from \"emps\" where \"deptno\" = 10"; // 从员工表筛选部门编号为10的记录
    final String q = "upsert into \"dependents\"" // 定义查询SQL：执行upsert操作到dependents表
        + "select \"empid\" + 1 as x, \"name\"" // 选择员工ID加1作为x，以及姓名
        + "from \"emps\" where \"deptno\" = 10"; // 从员工表筛选部门编号为10的记录

    final List<List<List<String>>> substitutedNames = new ArrayList<>(); // 创建列表用于存储被替换的表名
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      CalciteAssert.that() // 创建断言构建器
          .withMaterializations(HR_FKUK_MODEL, // 使用HR_FKUK_MODEL
              "m0", m) // 配置物化视图m0
          .query(q) // 执行查询
          .withHook(Hook.SUB, (Consumer<RelNode>) r -> // 使用SUB钩子捕获关系节点
              substitutedNames.add(new TableNameVisitor().run(r))) // 使用TableNameVisitor访问关系节点并收集表名
          .enableMaterializations(true) // 启用物化视图
          .explainContains("hr, m0"); // 验证执行计划包含hr.m0
    } catch (Exception e) { // 捕获异常
      // Table "dependents" not modifiable. // dependents表不可修改
    } // try-catch结束
    assertThat(substitutedNames, is(list3(new String[][][]{{{"hr", "m0"}}}))); // 断言被替换的表名包含hr.m0
  } // 测试方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-761">[CALCITE-761]
   * Pre-populated materializations</a>. */
  // 测试用例：预填充的物化视图（CALCITE-761）
  @Test void testPrePopulated() { // 测试方法：测试预填充的物化视图
    String q = "select distinct \"deptno\" from \"emps\""; // 定义查询SQL：从员工表选择不同的部门编号
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      CalciteAssert.that() // 创建断言构建器
          .withMaterializations( // 配置物化视图
              HR_FKUK_MODEL, builder -> { // 使用HR_FKUK_MODEL和builder函数
                final Map<String, Object> map = builder.map(); // 创建物化视图配置map
                map.put("table", "locations"); // 设置物化视图表名为locations
                String sql = "select distinct `deptno` as `empid`, '' as `name`\n" // 定义物化视图SQL：选择不同的部门编号作为员工ID，空字符串作为名称
                    + "from `emps`"; // 从员工表
                final String sql2 = sql.replace("`", "\""); // 将反引号替换为双引号
                map.put("sql", sql2); // 设置物化视图SQL
                return ImmutableList.of(map); // 返回包含map的不可变列表
              })
          .query(q) // 执行查询
          .enableMaterializations(true) // 启用物化视图
          .sameResultWithMaterializationsDisabled(); // 验证禁用物化视图后结果相同
    } // try-with-resources结束，自动清理资源
  } // 测试方法结束

  @Test void testViewSchemaPath() { // 测试方法：测试视图schema路径
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      final String m = "select empno, deptno from emp"; // 定义物化视图SQL：选择员工编号和部门编号
      final String q = "select deptno from scott.emp"; // 定义查询SQL：从scott.emp选择部门编号
      final List<String> path = ImmutableList.of("SCOTT"); // 定义schema路径：包含SCOTT
      final JsonBuilder builder = new JsonBuilder(); // 创建JSON构建器
      final String model = "{\n" // 定义模型JSON配置
          + "  version: '1.0',\n" // 版本1.0
          + "  defaultSchema: 'hr',\n" // 默认schema为hr
          + "  schemas: [\n" // schemas数组开始
          + JdbcTest.SCOTT_SCHEMA // 引用SCOTT schema配置
          + "  ,\n"
          + "    {\n" // hr schema配置开始
          + "      materializations: [\n" // 物化视图数组开始
          + "        {\n" // 物化视图配置开始
          + "          table: 'm0',\n" // 物化视图表名
          + "          view: 'm0v',\n" // 视图名称
          + "          sql: " + builder.toJsonString(m) + ",\n" // 物化视图SQL
          + "          viewSchemaPath: " + builder.toJsonString(path) // 视图schema路径
          + "        }\n" // 物化视图配置结束
          + "      ],\n" // 物化视图数组结束
          + "      type: 'custom',\n" // 自定义类型
          + "      name: 'hr',\n" // schema名称
          + "      factory: 'org.apache.calcite.adapter.java.ReflectiveSchema$Factory',\n" // 使用反射schema工厂
          + "      operand: {\n" // 工厂参数开始
          + "        class: '" + HrSchema.class.getName() + "'\n" // 指定HrSchema类
          + "      }\n" // 工厂参数结束
          + "    }\n" // hr schema配置结束
          + "  ]\n" // schemas数组结束
          + "}"; // 模型配置结束
      CalciteAssert.that() // 创建断言构建器
          .withModel(model) // 使用模型配置
          .query(q) // 执行查询
          .enableMaterializations(true) // 启用物化视图
          .explainMatches("", CONTAINS_M0) // 验证执行计划匹配包含m0
          .sameResultWithMaterializationsDisabled(); // 验证禁用物化视图后结果相同
    } // try-with-resources结束，自动清理资源
  } // 测试方法结束

  @Test void testMultiMaterializationMultiUsage() { // 测试方法：测试多个物化视图的多次使用
    String q = "select *\n" // 定义查询SQL：选择所有列
        + "from (select * from \"emps\" where \"empid\" < 300)\n" // 从员工ID小于300的员工子查询
        + "join (select \"deptno\", count(*) as c from \"emps\" group by \"deptno\") using (\"deptno\")"; // 与按部门编号分组的员工计数子查询连接
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      CalciteAssert.that() // 创建断言构建器
          .withMaterializations(HR_FKUK_MODEL, // 使用HR_FKUK_MODEL
              "m0", "select \"deptno\", count(*) as c, sum(\"empid\") as s from \"emps\" group by \"deptno\"", // 配置物化视图m0：按部门分组统计
              "m1", "select * from \"emps\" where \"empid\" < 500") // 配置物化视图m1：员工ID小于500
          .query(q) // 执行查询
          .enableMaterializations(true) // 启用物化视图
          .explainContains("EnumerableTableScan(table=[[hr, m0]])") // 验证执行计划包含对m0的扫描
          .explainContains("EnumerableTableScan(table=[[hr, m1]])") // 验证执行计划包含对m1的扫描
          .sameResultWithMaterializationsDisabled(); // 验证禁用物化视图后结果相同
    } // try-with-resources结束，自动清理资源
  } // 测试方法结束

  @Disabled("Creating mv for depts considering all its column throws exception") // 禁用此测试：为depts表创建包含所有列的物化视图会抛出异常
  @Test void testMultiMaterializationOnJoinQuery() { // 测试方法：测试连接查询上的多个物化视图
    final String q = "select *\n" // 定义查询SQL：选择所有列
        + "from \"emps\"\n" // 从员工表
        + "join \"depts\" using (\"deptno\") where \"empid\" < 300 " // 与部门表连接，员工ID小于300
        + "and \"depts\".\"deptno\" > 200"; // 且部门编号大于200
    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      CalciteAssert.that() // 创建断言构建器
          .withMaterializations(HR_FKUK_MODEL, // 使用HR_FKUK_MODEL
              "m0", "select * from \"emps\" where \"empid\" < 500", // 配置物化视图m0：员工ID小于500
              "m1", "select * from \"depts\" where \"deptno\" > 100") // 配置物化视图m1：部门编号大于100
          .query(q) // 执行查询
          .enableMaterializations(true) // 启用物化视图
          .explainContains("EnumerableTableScan(table=[[hr, m0]])") // 验证执行计划包含对m0的扫描
          .explainContains("EnumerableTableScan(table=[[hr, m1]])") // 验证执行计划包含对m1的扫描
          .sameResultWithMaterializationsDisabled(); // 验证禁用物化视图后结果相同
    } // try-with-resources结束，自动清理资源
  } // 测试方法结束

  @Test void testMaterializationSubstitution() { // 测试方法：测试物化视图替换
    String q = "select *\n" // 定义查询SQL：选择所有列
        + "from (select * from \"emps\" where \"empid\" < 300)\n" // 从员工ID小于300的员工子查询
        + "join (select * from \"emps\" where \"empid\" < 200) using (\"empid\")"; // 与员工ID小于200的员工子查询连接

    final String[][][] expectedNames = { // 定义期望的替换表名三维数组
        {{"hr", "emps"}, {"hr", "m0"}}, // 原表emps和物化视图m0的组合
        {{"hr", "emps"}, {"hr", "m1"}}, // 原表emps和物化视图m1的组合
        {{"hr", "m0"}, {"hr", "emps"}}, // 物化视图m0和原表emps的组合
        {{"hr", "m0"}, {"hr", "m0"}}, // 物化视图m0和物化视图m0的组合
        {{"hr", "m0"}, {"hr", "m1"}}, // 物化视图m0和物化视图m1的组合
        {{"hr", "m1"}, {"hr", "emps"}}, // 物化视图m1和原表emps的组合
        {{"hr", "m1"}, {"hr", "m0"}}, // 物化视图m1和物化视图m0的组合
        {{"hr", "m1"}, {"hr", "m1"}}}; // 物化视图m1和物化视图m1的组合

    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      final List<List<List<String>>> substitutedNames = new ArrayList<>(); // 创建列表用于存储被替换的表名
      CalciteAssert.that() // 创建断言构建器
          .withMaterializations(HR_FKUK_MODEL, // 使用HR_FKUK_MODEL
              "m0", "select * from \"emps\" where \"empid\" < 300", // 配置物化视图m0：员工ID小于300
              "m1", "select * from \"emps\" where \"empid\" < 600") // 配置物化视图m1：员工ID小于600
          .query(q) // 执行查询
          .withHook(Hook.SUB, (Consumer<RelNode>) r -> // 使用SUB钩子捕获关系节点
              substitutedNames.add(new TableNameVisitor().run(r))) // 使用TableNameVisitor访问关系节点并收集表名
          .enableMaterializations(true) // 启用物化视图
          .sameResultWithMaterializationsDisabled(); // 验证禁用物化视图后结果相同
      substitutedNames.sort(CASE_INSENSITIVE_LIST_LIST_COMPARATOR); // 对替换的表名列表进行排序
      assertThat(substitutedNames, is(list3(expectedNames))); // 断言替换的表名与期望值匹配
    } // try-with-resources结束，自动清理资源
  } // 测试方法结束

  @Test void testMaterializationSubstitution2() { // 测试方法：测试物化视图替换的第二个变体，包含基于m1的m2
    String q = "select *\n" // 定义查询SQL：选择所有列
        + "from (select * from \"emps\" where \"empid\" < 300)\n" // 从员工ID小于300的员工子查询
        + "join (select * from \"emps\" where \"empid\" < 200) using (\"empid\")"; // 与员工ID小于200的员工子查询连接

    final String[][][] expectedNames = { // 定义期望的替换表名三维数组
        {{"hr", "emps"}, {"hr", "m0"}}, // 原表emps和物化视图m0的组合
        {{"hr", "emps"}, {"hr", "m1"}}, // 原表emps和物化视图m1的组合
        {{"hr", "emps"}, {"hr", "m2"}}, // 原表emps和物化视图m2的组合
        {{"hr", "m0"}, {"hr", "emps"}}, // 物化视图m0和原表emps的组合
        {{"hr", "m0"}, {"hr", "m0"}}, // 物化视图m0和物化视图m0的组合
        {{"hr", "m0"}, {"hr", "m1"}}, // 物化视图m0和物化视图m1的组合
        {{"hr", "m0"}, {"hr", "m2"}}, // 物化视图m0和物化视图m2的组合
        {{"hr", "m1"}, {"hr", "emps"}}, // 物化视图m1和原表emps的组合
        {{"hr", "m1"}, {"hr", "m0"}}, // 物化视图m1和物化视图m0的组合
        {{"hr", "m1"}, {"hr", "m1"}}, // 物化视图m1和物化视图m1的组合
        {{"hr", "m1"}, {"hr", "m2"}}, // 物化视图m1和物化视图m2的组合
        {{"hr", "m2"}, {"hr", "emps"}}, // 物化视图m2和原表emps的组合
        {{"hr", "m2"}, {"hr", "m0"}}, // 物化视图m2和物化视图m0的组合
        {{"hr", "m2"}, {"hr", "m1"}}, // 物化视图m2和物化视图m1的组合
        {{"hr", "m2"}, {"hr", "m2"}}}; // 物化视图m2和物化视图m2的组合

    try (TryThreadLocal.Memo ignored = Prepare.THREAD_TRIM.push(true)) { // 使用try-with-resources设置线程本地变量，启用字段修剪
      MaterializationService.setThreadLocal(); // 设置线程本地物化视图服务
      final List<List<List<String>>> substitutedNames = new ArrayList<>(); // 创建列表用于存储被替换的表名
      CalciteAssert.that() // 创建断言构建器
          .withMaterializations(HR_FKUK_MODEL, // 使用HR_FKUK_MODEL
              "m0", "select * from \"emps\" where \"empid\" < 300", // 配置物化视图m0：员工ID小于300
              "m1", "select * from \"emps\" where \"empid\" < 600", // 配置物化视图m1：员工ID小于600
              "m2", "select * from \"m1\"") // 配置物化视图m2：基于m1
          .query(q) // 执行查询
          .withHook(Hook.SUB, (Consumer<RelNode>) r -> // 使用SUB钩子捕获关系节点
              substitutedNames.add(new TableNameVisitor().run(r))) // 使用TableNameVisitor访问关系节点并收集表名
          .enableMaterializations(true) // 启用物化视图
          .sameResultWithMaterializationsDisabled(); // 验证禁用物化视图后结果相同
      substitutedNames.sort(CASE_INSENSITIVE_LIST_LIST_COMPARATOR); // 对替换的表名列表进行排序
      assertThat(substitutedNames, is(list3(expectedNames))); // 断言替换的表名与期望值匹配
    } // try-with-resources结束，自动清理资源
  } // 测试方法结束

  private static <E> List<List<List<E>>> list3(E[][][] as) { // 辅助方法：将三维数组转换为三层嵌套的不可变列表
    final ImmutableList.Builder<List<List<E>>> builder = // 创建不可变列表构建器
        ImmutableList.builder(); // 初始化构建器
    for (E[][] a : as) { // 遍历三维数组的每个二维数组
      builder.add(list2(a)); // 调用list2方法将二维数组转换为列表并添加到构建器
    } // 循环结束
    return builder.build(); // 构建并返回三层嵌套的不可变列表
  } // 方法结束

  private static <E> List<List<E>> list2(E[][] as) { // 辅助方法：将二维数组转换为两层嵌套的不可变列表
    final ImmutableList.Builder<List<E>> builder = ImmutableList.builder(); // 创建不可变列表构建器
    for (E[] a : as) { // 遍历二维数组的每个一维数组
      builder.add(ImmutableList.copyOf(a)); // 将一维数组转换为不可变列表并添加到构建器
    } // 循环结束
    return builder.build(); // 构建并返回两层嵌套的不可变列表
  } // 方法结束

  /**
   * Implementation of RelVisitor to extract substituted table names.
   */
  // 关系访问者实现，用于提取被替换的表名
  private static class TableNameVisitor extends RelVisitor { // 定义表名访问者内部类，继承RelVisitor
    private final List<List<String>> names = new ArrayList<>(); // 成员变量：存储表名的列表

    List<List<String>> run(RelNode input) { // 方法：运行访问者并收集表名
      go(input); // 调用父类go方法开始遍历关系节点
      return names; // 返回收集到的表名列表
    } // 方法结束

    @Override public void visit(RelNode node, int ordinal, @Nullable RelNode parent) { // 重写visit方法：访问每个关系节点
      if (node instanceof TableScan) { // 如果节点是表扫描节点
        RelOptTable table = node.getTable(); // 获取节点对应的表
        List<String> qName = table.getQualifiedName(); // 获取表的完全限定名
        names.add(qName); // 将表名添加到列表中
      } // if结束
      super.visit(node, ordinal, parent); // 调用父类visit方法继续遍历子节点
    } // 方法结束
  } // 内部类结束

  /**
   * Hr schema with FK-UK relationship.
   */
  // HR schema，包含外键-唯一键关系
  public static class HrFKUKSchema { // 定义HR FK-UK schema内部类
    @Override public String toString() { // 重写toString方法
      return "HrFKUKSchema"; // 返回schema名称
    } // 方法结束

    public final Employee[] emps = { // 成员变量：员工数组
        new Employee(100, 10, "Bill", 10000, 1000), // 员工1：ID=100，部门=10，姓名=Bill，薪水=10000，佣金=1000
        new Employee(200, 20, "Eric", 8000, 500), // 员工2：ID=200，部门=20，姓名=Eric，薪水=8000，佣金=500
        new Employee(150, 10, "Sebastian", 7000, null), // 员工3：ID=150，部门=10，姓名=Sebastian，薪水=7000，佣金=null
        new Employee(110, 10, "Theodore", 10000, 250), // 员工4：ID=110，部门=10，姓名=Theodore，薪水=10000，佣金=250
    }; // 数组初始化结束
    public final Department[] depts = { // 成员变量：部门数组
        new Department(10, "Sales", Arrays.asList(emps[0], emps[2], emps[3]), // 部门1：ID=10，名称=Sales，员工=[Bill, Sebastian, Theodore]，位置=(-122, 38)
            new Location(-122, 38)),
        new Department(30, "Marketing", ImmutableList.of(), // 部门2：ID=30，名称=Marketing，员工=[]，位置=(0, 52)
            new Location(0, 52)),
        new Department(20, "HR", Collections.singletonList(emps[1]), null), // 部门3：ID=20，名称=HR，员工=[Eric]，位置=null
    }; // 数组初始化结束
    public final DepartmentPlus[] depts2 = { // 成员变量：扩展部门数组
        new DepartmentPlus(10, "Sales", Arrays.asList(emps[0], emps[2], emps[3]), // 扩展部门1：ID=10，名称=Sales，员工=[Bill, Sebastian, Theodore]，位置=(-122, 38)，时间戳=0
            new Location(-122, 38), new Timestamp(0)),
        new DepartmentPlus(30, "Marketing", ImmutableList.of(), // 扩展部门2：ID=30，名称=Marketing，员工=[]，位置=(0, 52)，时间戳=0
            new Location(0, 52), new Timestamp(0)),
        new DepartmentPlus(20, "HR", Collections.singletonList(emps[1]), // 扩展部门3：ID=20，名称=HR，员工=[Eric]，位置=null，时间戳=0
            null, new Timestamp(0)),
    }; // 数组初始化结束
    public final Dependent[] dependents = { // 成员变量：家属数组
        new Dependent(10, "Michael"), // 家属1：员工ID=10，姓名=Michael
        new Dependent(10, "Jane"), // 家属2：员工ID=10，姓名=Jane
    }; // 数组初始化结束
    public final NullableTest[] nullables = { // 成员变量：可空测试数组
        new NullableTest(null, null, 1), // 可空测试1：三个字段分别为null, null, 1
    }; // 数组初始化结束
    public final Dependent[] locations = { // 成员变量：位置数组（使用Dependent类模拟）
        new Dependent(10, "San Francisco"), // 位置1：员工ID=10，城市=San Francisco
        new Dependent(20, "San Diego"), // 位置2：员工ID=20，城市=San Diego
    }; // 数组初始化结束
    public final Event[] events = { // 成员变量：事件数组
        new Event(100, new Timestamp(0)), // 事件1：员工ID=100，时间戳=0
        new Event(200, new Timestamp(0)), // 事件2：员工ID=200，时间戳=0
        new Event(150, new Timestamp(0)), // 事件3：员工ID=150，时间戳=0
        new Event(110, null), // 事件4：员工ID=110，时间戳=null
    }; // 数组初始化结束

    public final RelReferentialConstraint rcs0 = // 成员变量：关系引用约束，定义emps和depts之间的外键关系
        RelReferentialConstraintImpl.of( // 创建关系引用约束实现
            ImmutableList.of("hr", "emps"), // 源表：hr.emps
            ImmutableList.of("hr", "depts"), // 目标表：hr.depts
            ImmutableList.of(IntPair.of(1, 0))); // 列对：emps的第1列(deptno)引用depts的第0列(deptno)

    public QueryableTable foo(int count) { // 方法：生成可查询表，用于测试
      return Smalls.generateStrings(count); // 返回生成的字符串表
    } // 方法结束

    public TranslatableTable view(String s) { // 方法：创建可转换表视图
      return Smalls.view(s); // 返回基于SQL字符串的视图
    } // 方法结束

    public TranslatableTable matview() { // 方法：创建物化视图
      return Smalls.strView("noname"); // 返回返回"noname"的字符串视图
    } // 方法结束
  } // 内部类结束
} // 类结束
