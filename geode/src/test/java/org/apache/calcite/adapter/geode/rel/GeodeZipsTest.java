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
// Apache License 许可证头部，声明代码版权信息和使用许可
package org.apache.calcite.adapter.geode.rel; // 包声明，该类属于 org.apache.calcite.adapter.geode.rel 包

import org.apache.calcite.jdbc.CalciteConnection; // 导入 Calcite JDBC 连接类，用于获取 Calcite 特定的连接功能
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，用于表示可扩展的模式
import org.apache.calcite.schema.impl.ViewTable; // 导入 ViewTable 类，用于创建和管理视图表
import org.apache.calcite.test.CalciteAssert; // 导入 CalciteAssert 测试工具类，用于编写断言测试

import org.apache.geode.cache.Cache; // 导入 Geode Cache 接口，Geode 的核心缓存接口
import org.apache.geode.cache.Region; // 导入 Geode Region 接口，表示 Geode 中的数据区域
import org.apache.geode.cache.query.Query; // 导入 Geode Query 接口，用于执行 OQL 查询
import org.apache.geode.cache.query.QueryService; // 导入 Geode QueryService 接口，用于创建查询服务
import org.apache.geode.cache.query.SelectResults; // 导入 SelectResults 接口，表示查询结果集
import org.apache.geode.cache.query.internal.StructImpl; // 导入 StructImpl 类，表示结构化数据实现

import org.junit.jupiter.api.BeforeAll; // 导入 JUnit 5 的 BeforeAll 注解，标记在所有测试之前执行的方法
import org.junit.jupiter.api.Disabled; // 导入 JUnit 5 的 Disabled 注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，标记测试方法

import java.sql.Connection; // 导入 JDBC Connection 接口，表示数据库连接
import java.sql.DriverManager; // 导入 DriverManager 类，用于管理数据库驱动程序
import java.sql.SQLException; // 导入 SQLException 异常类，表示数据库操作异常
import java.util.Arrays; // 导入 Arrays 工具类，提供数组操作方法
import java.util.Collections; // 导入 Collections 工具类，提供集合操作方法
import java.util.Locale; // 导入 Locale 类，用于本地化操作
import java.util.Set; // 导入 Set 接口，表示不重复元素的集合
import java.util.TreeSet; // 导入 TreeSet 类，基于树结构的有序集合实现
import java.util.stream.Collectors; // 导入 Collectors 类，提供流式收集操作

/**
 * Tests based on {@code zips-min.json} dataset. Runs automatically as part of CI.
 * // 基于 zips-min.json 数据集的测试类，作为 CI（持续集成）的一部分自动运行
 * // 该类用于测试 Calcite Geode 适配器对美国邮政编码数据的查询功能
 * // 继承自 AbstractGeodeTest 基类，复用 Geode 测试的基础设施
 * // 主要测试功能包括：聚合查询（GROUP BY、SUM、MAX）、连接查询、数组元素访问、WHERE 条件过滤等
 */
class GeodeZipsTest extends AbstractGeodeTest { // 测试类定义，继承 AbstractGeodeTest 基类

  @BeforeAll // JUnit 注解，标记该方法在所有测试方法执行前运行一次
  public static void setUp() throws Exception { // 静态设置方法，用于初始化测试环境，可能抛出异常
    Cache cache = POLICY.cache(); // 从测试策略中获取 Geode Cache 实例，Cache 是 Geode 的核心缓存对象
    Region<?, ?> region =  cache.<String, Object>createRegionFactory().create("zips"); // 创建名为 "zips" 的 Geode Region，Region 是 Geode 中数据存储和管理的逻辑单元，类型为 String 到 Object 的映射
    new JsonLoader(region).loadClasspathResource("/zips-mini.json"); // 使用 JsonLoader 从类路径加载 zips-mini.json 文件，并将数据加载到 zips Region 中，JsonLoader 负责解析 JSON 并将数据转换为 Geode 可识别的格式
  }

  private static Connection createConnection() throws SQLException { // 私有静态方法，创建并返回 Calcite JDBC 连接，可能抛出 SQL 异常
    final Connection connection = // 声明 Calcite 连接变量
        DriverManager.getConnection("jdbc:calcite:lex=JAVA"); // 通过 DriverManager 获取 Calcite JDBC 连接，使用 JAVA 词法分析器
    final SchemaPlus root = // 声明根 Schema 变量
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为 CalciteConnection 并获取根 Schema，SchemaPlus 是可扩展的 Schema 接口

    root.add("geode", new GeodeSchema(POLICY.cache(), Collections.singleton("zips"))); // 向根 Schema 添加名为 "geode" 的 GeodeSchema，该 Schema 包含 "zips" Region，GeodeSchema 是 Calcite 与 Geode 数据源的适配器

    // add calcite view programmatically // 注释：通过编程方式添加 Calcite 视图
    final String viewSql = "select \"_id\" AS \"id\", \"city\", \"loc\", " // 定义视图 SQL 语句，从 zips 表中选择字段并进行类型转换
        + "cast(\"pop\" AS integer) AS \"pop\", cast(\"state\" AS char(2)) AS \"state\" " // 将 pop 字段转换为 integer 类型，state 字段转换为 char(2) 类型
        + "from \"geode\".\"zips\""; // 从 geode schema 的 zips 表中查询数据


    root.add("view", // 向根 Schema 添加名为 "view" 的视图
        ViewTable.viewMacro(root, viewSql, // 使用 ViewTable 工厂方法创建视图宏，传入根 Schema 和视图 SQL
            Collections.singletonList("geode"), // 指定视图依赖的 schema 路径列表，这里是 "geode"
            Arrays.asList("geode", "view"), false)); // 指定视图的完整路径列表，false 表示不强制延迟解析

    return connection; // 返回创建的 Calcite 连接对象
  }

  private CalciteAssert.AssertThat calciteAssert() { // 私有方法，创建并返回 CalciteAssert 断言构建器，用于编写测试断言
    return CalciteAssert.that() // 创建 CalciteAssert 实例，开始构建断言
        .with(GeodeZipsTest::createConnection); // 使用 createConnection 方法作为连接提供者，with 方法接受一个函数式接口，用于创建测试连接
  }

  @Test void testGroupByView() { // 测试方法，测试在视图上执行 GROUP BY 聚合查询
    calciteAssert() // 创建断言构建器
        .query("SELECT state, SUM(pop) FROM view GROUP BY state") // 执行 SQL 查询：从 view 视图中按 state 分组并计算 pop 的总和
        .returnsCount(51) // 验证查询返回 51 行结果（美国 50 个州加上华盛顿特区）
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query("SELECT state AS state, " // GeodeAssertions 工具类用于验证生成的 OQL 查询语句
                + "SUM(pop) AS EXPR$1 FROM /zips GROUP BY state")); // 期望生成的 OQL 查询：从 /zips Region 中按 state 分组并计算 pop 总和，EXPR$1 是 Calcite 自动生成的聚合列别名
  }

  @Test @Disabled("Currently fails") // 测试方法，测试在视图上执行带别名的 GROUP BY 聚合查询，当前被禁用因为测试失败
  public void testGroupByViewWithAliases() { // 公共测试方法，测试视图上的 GROUP BY 查询并使用列别名
    calciteAssert() // 创建断言构建器
        .query("SELECT state as st, SUM(pop) po " // 执行 SQL 查询：从 view 视图中按 state 分组，计算 pop 总和，并为列设置别名（st 和 po）
            + "FROM view GROUP BY state") // 查询来源为 view 视图，按 state 字段分组
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query("SELECT state, SUM(pop) AS po FROM /zips GROUP BY state")) // 期望生成的 OQL 查询：从 /zips Region 中按 state 分组，计算 pop 总和并使用别名 po
        .returnsCount(51) // 验证查询返回 51 行结果
        .explainContains("PLAN=GeodeToEnumerableConverter\n" // 验证执行计划包含指定的操作符树
            + "  GeodeAggregate(group=[{1}], po=[SUM($0)])\n" // GeodeAggregate 聚合操作符，按第 1 列分组，计算第 0 列的和并命名为 po
            + "    GeodeProject(pop=[CAST($3):INTEGER], state=[CAST($4):VARCHAR(2) CHARACTER SET" // GeodeProject 投影操作符，将第 3 列转换为 INTEGER 类型作为 pop，将第 4 列转换为 VARCHAR(2) 类型作为 state
            + " \"ISO-8859-1\" COLLATE \"ISO-8859-1$en_US$primary\"])\n" // 指定字符集和排序规则
            + "      GeodeTableScan(table=[[geode, zips]])\n"); // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表
  }

  @Test void testGroupByRaw() { // 测试方法，测试在原始 Geode 表上执行 GROUP BY 聚合查询
    calciteAssert() // 创建断言构建器
        .query("SELECT state as st, SUM(pop) po " // 执行 SQL 查询：从 geode.zips 表中按 state 分组，计算 pop 总和，并为列设置别名
            + "FROM geode.zips GROUP BY state") // 查询来源为 geode schema 的 zips 表，按 state 字段分组
        .returnsCount(51) // 验证查询返回 51 行结果
        .explainContains("PLAN=GeodeToEnumerableConverter\n" // 验证执行计划包含指定的操作符树
            + "  GeodeAggregate(group=[{4}], po=[SUM($3)])\n" // GeodeAggregate 聚合操作符，按第 4 列（state）分组，计算第 3 列（pop）的和并命名为 po
            + "    GeodeTableScan(table=[[geode, zips]])\n"); // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表
  }

  @Test void testGroupByRawWithAliases() { // 测试方法，测试在原始 Geode 表上执行带别名的 GROUP BY 聚合查询
    calciteAssert() // 创建断言构建器
        .query("SELECT state AS st, SUM(pop) AS po " // 执行 SQL 查询：从 geode.zips 表中按 state 分组，计算 pop 总和，并为列设置别名（st 和 po）
            + "FROM geode.zips GROUP BY state") // 查询来源为 geode schema 的 zips 表，按 state 字段分组
        .returnsCount(51) // 验证查询返回 51 行结果
        .explainContains("PLAN=GeodeToEnumerableConverter\n" // 验证执行计划包含指定的操作符树
            + "  GeodeAggregate(group=[{4}], po=[SUM($3)])\n" // GeodeAggregate 聚合操作符，按第 4 列（state）分组，计算第 3 列（pop）的和并命名为 po
            + "    GeodeTableScan(table=[[geode, zips]])\n"); // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表
  }

  @Test void testMaxRaw() { // 测试方法，测试在视图上执行 MAX 聚合函数查询
    calciteAssert() // 创建断言构建器
        .query("SELECT MAX(pop) FROM view") // 执行 SQL 查询：从 view 视图中计算 pop 字段的最大值
        .returns("EXPR$0=112047\n") // 验证查询返回的结果为 EXPR$0=112047，EXPR$0 是 Calcite 自动生成的聚合列别名，112047 是最大人口数
        .queryContains(GeodeAssertions.query("SELECT MAX(pop) AS EXPR$0 FROM /zips")); // 验证生成的 Geode OQL 查询包含指定的子查询：从 /zips Region 中计算 pop 的最大值
  }

  @Test @Disabled("Currently fails") // 测试方法，测试 Geode 表的连接查询，当前被禁用因为测试失败
  public void testJoin() { // 公共测试方法，测试自连接查询
    calciteAssert() // 创建断言构建器
        .query("SELECT r._id FROM geode.zips AS v " // 执行 SQL 查询：从 geode.zips 表的自连接中选择 _id 字段
            + "JOIN geode.zips AS r ON v._id = r._id LIMIT 1") // 将 geode.zips 表与自身连接，连接条件是 _id 相等，限制结果为 1 行
        .returnsCount(1) // 验证查询返回 1 行结果
        .explainContains("PLAN=EnumerableCalc(expr#0..2=[{inputs}], _id1=[$t0])\n" // 验证执行计划包含指定的操作符树，EnumerableCalc 是可枚举的计算操作符
            + "  EnumerableLimit(fetch=[1])\n" // EnumerableLimit 限制操作符，只获取 1 行结果
            + "    EnumerableHashJoin(condition=[=($1, $2)], joinType=[inner])\n" // EnumerableHashJoin 哈希连接操作符，使用哈希表进行内连接，连接条件是第 1 列等于第 2 列
            + "      GeodeToEnumerableConverter\n" // GeodeToEnumerableConverter 转换器，将 Geode 结果集转换为可枚举的结果集
            + "        GeodeProject(_id=[$0], _id0=[CAST($0):VARCHAR CHARACTER SET " // GeodeProject 投影操作符，第 0 列作为 _id，将第 0 列转换为 VARCHAR 类型作为 _id0
            + "\"ISO-8859-1\" COLLATE \"ISO-8859-1$en_US$primary\"])\n" // 指定字符集和排序规则
            + "          GeodeTableScan(table=[[geode, zips]])\n" // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表（左表）
            + "      GeodeToEnumerableConverter\n" // GeodeToEnumerableConverter 转换器，将 Geode 结果集转换为可枚举的结果集
            + "        GeodeProject(_id0=[CAST($0):VARCHAR CHARACTER SET \"ISO-8859-1\" COLLATE " // GeodeProject 投影操作符，将第 0 列转换为 VARCHAR 类型作为 _id0
            + "\"ISO-8859-1$en_US$primary\"])\n" // 指定字符集和排序规则
            + "          GeodeTableScan(table=[[geode, zips]])\n"); // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表（右表）
  }

  @Test void testSelectLocItem() { // 测试方法，测试访问数组类型字段（loc）的元素
    calciteAssert() // 创建断言构建器
        .query("SELECT loc[0] as lat, loc[1] as lon " // 执行 SQL 查询：从 view 视图中选择 loc 数组的第 0 个元素作为纬度（lat），第 1 个元素作为经度（lon）
            + "FROM view LIMIT 1") // 查询来源为 view 视图，限制结果为 1 行
        .returns("lat=-105.007985; lon=39.840562\n") // 验证查询返回的结果：纬度为 -105.007985，经度为 39.840562
        .explainContains("PLAN=GeodeToEnumerableConverter\n" // 验证执行计划包含指定的操作符树
            + "  GeodeProject(lat=[ITEM($2, 0)], lon=[ITEM($2, 1)])\n" // GeodeProject 投影操作符，使用 ITEM 函数访问第 2 列（loc 数组）的第 0 个元素作为 lat，第 1 个元素作为 lon
            + "    GeodeSort(fetch=[1])\n" // GeodeSort 排序操作符，获取前 1 行结果
            + "      GeodeTableScan(table=[[geode, zips]])\n"); // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表
  }

  @Test void testItemPredicate() { // 测试方法，测试在 WHERE 条件中使用数组元素访问
    calciteAssert() // 创建断言构建器
        .query("SELECT loc[0] as lat, loc[1] as lon " // 执行 SQL 查询：从 view 视图中选择 loc 数组的第 0 个元素作为纬度，第 1 个元素作为经度
            + "FROM view WHERE loc[0] < 0 LIMIT 1") // 查询条件：loc 数组的第 0 个元素（纬度）小于 0，限制结果为 1 行
        .returnsCount(1) // 验证查询返回 1 行结果
        .returns("lat=-105.007985; lon=39.840562\n") // 验证查询返回的结果：纬度为 -105.007985，经度为 39.840562
        .explainContains("PLAN=GeodeToEnumerableConverter\n" // 验证执行计划包含指定的操作符树
            + "  GeodeProject(lat=[ITEM($2, 0)], lon=[ITEM($2, 1)])\n" // GeodeProject 投影操作符，使用 ITEM 函数访问第 2 列（loc 数组）的第 0 和第 1 个元素
            + "    GeodeSort(fetch=[1])\n" // GeodeSort 排序操作符，获取前 1 行结果
            + "      GeodeFilter(condition=[<(ITEM($2, 0), 0)])\n" // GeodeFilter 过滤操作符，过滤条件是第 2 列的第 0 个元素小于 0
            + "        GeodeTableScan(table=[[geode, zips]])\n") // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query("SELECT loc[0] AS lat, " // GeodeAssertions 工具类用于验证生成的 OQL 查询语句
                + "loc[1] AS lon FROM /zips WHERE loc[0] < 0 LIMIT 1")); // 期望生成的 OQL 查询：从 /zips Region 中选择 loc 数组的元素，过滤条件是 loc[0] < 0

    calciteAssert() // 创建断言构建器
        .query("SELECT loc[0] as lat, loc[1] as lon " // 执行 SQL 查询：从 view 视图中选择 loc 数组的第 0 个元素作为纬度，第 1 个元素作为经度
            + "FROM view WHERE loc[0] > 0 LIMIT 1") // 查询条件：loc 数组的第 0 个元素（纬度）大于 0，限制结果为 1 行
        .returnsCount(0) // 验证查询返回 0 行结果（因为美国邮编的纬度都是负数或接近 0）
        .explainContains("PLAN=GeodeToEnumerableConverter\n" // 验证执行计划包含指定的操作符树
            + "  GeodeProject(lat=[ITEM($2, 0)], lon=[ITEM($2, 1)])\n" // GeodeProject 投影操作符，使用 ITEM 函数访问第 2 列（loc 数组）的第 0 和第 1 个元素
            + "    GeodeSort(fetch=[1])\n" // GeodeSort 排序操作符，获取前 1 行结果
            + "      GeodeFilter(condition=[>(ITEM($2, 0), 0)])\n" // GeodeFilter 过滤操作符，过滤条件是第 2 列的第 0 个元素大于 0
            + "        GeodeTableScan(table=[[geode, zips]])\n") // GeodeTableScan 表扫描操作符，扫描 geode schema 的 zips 表
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query("SELECT loc[0] AS lat, " // GeodeAssertions 工具类用于验证生成的 OQL 查询语句
                + "loc[1] AS lon FROM /zips WHERE loc[0] > 0 LIMIT 1")); // 期望生成的 OQL 查询：从 /zips Region 中选择 loc 数组的元素，过滤条件是 loc[0] > 0
  }

  @Test void testWhereWithOrForStringField() { // 测试方法，测试在 WHERE 条件中使用 OR 连接字符串字段的等值比较
    String expectedQuery = "SELECT state AS state FROM /zips " // 定义期望生成的 Geode OQL 查询语句
        + "WHERE state IN SET('MA', 'RI')"; // 使用 IN SET 语法优化 OR 条件，state 等于 'MA' 或 'RI'
    calciteAssert() // 创建断言构建器
        .query("SELECT state as state " // 执行 SQL 查询：从 view 视图中选择 state 字段
            + "FROM view WHERE state = 'MA' OR state = 'RI'") // 查询条件：state 等于 'MA' 或 'RI'，使用 OR 连接两个等值条件
        .returnsCount(6) // 验证查询返回 6 行结果（马萨诸塞州和罗德岛州的所有邮编）
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query(expectedQuery)); // 验证生成的查询使用 IN SET 语法优化了 OR 条件
  }

  @Test void testWhereWithOrForNumericField() { // 测试方法，测试在 WHERE 条件中使用 OR 连接数值字段的等值比较
    calciteAssert() // 创建断言构建器
        .query("SELECT pop as pop " // 执行 SQL 查询：从 view 视图中选择 pop 字段
            + "FROM view WHERE pop = 34035 OR pop = 40173") // 查询条件：pop 等于 34035 或 40173，使用 OR 连接两个等值条件
        .returnsCount(2) // 验证查询返回 2 行结果
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query("SELECT pop AS pop FROM /zips WHERE pop IN SET(34035, 40173)")); // 期望生成的 OQL 查询：使用 IN SET 语法优化 OR 条件，pop 等于 34035 或 40173
  }

  @Test void testWhereWithOrForNestedNumericField() { // 测试方法，测试在 WHERE 条件中使用 OR 连接嵌套数值字段的等值比较
    String expectedQuery = "SELECT loc[1] AS lan FROM /zips " // 定义期望生成的 Geode OQL 查询语句
        + "WHERE loc[1] IN SET(43.218525, 44.098538)"; // 使用 IN SET 语法优化 OR 条件，loc 数组的第 1 个元素等于 43.218525 或 44.098538

    calciteAssert() // 创建断言构建器
        .query("SELECT loc[1] as lan " // 执行 SQL 查询：从 view 视图中选择 loc 数组的第 1 个元素
            + "FROM view WHERE loc[1] = 43.218525 OR loc[1] = 44.098538") // 查询条件：loc 数组的第 1 个元素等于 43.218525 或 44.098538，使用 OR 连接两个等值条件
        .returnsCount(2) // 验证查询返回 2 行结果
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query(expectedQuery)); // 验证生成的查询使用 IN SET 语法优化了 OR 条件
  }

  @Test void testWhereWithOrForLargeValueList() throws Exception { // 测试方法，测试在 WHERE 条件中使用 OR 连接大量值的等值比较
    Cache cache = POLICY.cache(); // 从测试策略中获取 Geode Cache 实例
    QueryService queryService = cache.getQueryService(); // 从 Cache 中获取 QueryService，用于创建和执行 OQL 查询
    Query query = queryService.newQuery("select state as state from /zips"); // 创建 OQL 查询，从 /zips Region 中选择 state 字段
    SelectResults results = (SelectResults) query.execute(); // 执行查询并获取结果集，SelectResults 是 Geode 的查询结果接口

    Set<String> stateList = (Set<String>) results.stream().map(s -> { // 使用流式处理将结果集转换为 Set 集合
      StructImpl struct = (StructImpl) s; // 将结果元素转换为 StructImpl 结构化对象
      return struct.get("state"); // 从结构化对象中获取 state 字段的值
    })
        .collect(Collectors.toCollection(TreeSet::new)); // 收集结果到 TreeSet 中，TreeSet 会自动排序

    String stateListPredicate = stateList.stream() // 使用流式处理构建 OR 条件谓词
        .map(s -> String.format(Locale.ROOT, "state = '%s'", s)) // 将每个 state 值格式化为 "state = 'XX'" 的形式
        .collect(Collectors.joining(" OR ")); // 使用 " OR " 连接所有条件

    String stateListStr = "'" + String.join("', '", stateList) + "'"; // 将 state 列表格式化为字符串，用单引号和逗号分隔

    String queryToBeExecuted = "SELECT state as state FROM view WHERE " + stateListPredicate; // 构造要执行的 SQL 查询语句

    String expectedQuery = "SELECT state AS state FROM /zips WHERE state " // 定义期望生成的 Geode OQL 查询语句
        + "IN SET(" + stateListStr + ")"; // 使用 IN SET 语法优化大量的 OR 条件

    calciteAssert() // 创建断言构建器
        .query(queryToBeExecuted) // 执行构造的 SQL 查询
        .returnsCount(149) // 验证查询返回 149 行结果（zips-mini.json 中的所有邮编）
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query(expectedQuery)); // 验证生成的查询使用 IN SET 语法优化了大量的 OR 条件
  }

  @Test void testSqlSingleStringWhereFilter() { // 测试方法，测试在 WHERE 条件中使用单个字符串字段的等值比较
    String expectedQuery = "SELECT state AS state FROM /zips " // 定义期望生成的 Geode OQL 查询语句
        + "WHERE state = 'NY'"; // 过滤条件：state 等于 'NY'（纽约州）
    calciteAssert() // 创建断言构建器
        .query("SELECT state as state " // 执行 SQL 查询：从 view 视图中选择 state 字段
            + "FROM view WHERE state = 'NY'") // 查询条件：state 等于 'NY'
        .returnsCount(3) // 验证查询返回 3 行结果（纽约州的 3 个邮编）
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query(expectedQuery)); // 验证生成的查询与期望的查询一致
  }

  @Test @Disabled("Currently fails") // 测试方法，测试在 WHERE 条件中使用 OR 连接不同类型字段的等值比较，当前被禁用因为测试失败
  public void testWhereWithOrWithEmptyResult() { // 公共测试方法，测试混合类型的 OR 条件，预期返回空结果
    String expectedQuery = "SELECT state AS state FROM /zips " // 定义期望生成的 Geode OQL 查询语句
        + "WHERE state IN SET('', true, false, 123, 13.892)"; // 使用 IN SET 语法优化 OR 条件，state 等于空字符串、布尔值或数值
    calciteAssert() // 创建断言构建器
        .query("SELECT state as state " // 执行 SQL 查询：从 view 视图中选择 state 字段
            + "FROM view WHERE state = '' OR state = null OR " // 查询条件：state 等于空字符串、null、布尔值或数值
            + "state = true OR state = false OR state = true OR " // 继续条件：true 或 false
            + "state = 123 OR state = 13.892") // 继续条件：123 或 13.892
        .returnsCount(0) // 验证查询返回 0 行结果（因为 state 字段是字符串类型，不会匹配数值或布尔值）
        .queryContains( // 验证生成的 Geode OQL 查询包含指定的子查询
            GeodeAssertions.query(expectedQuery)); // 验证生成的查询使用 IN SET 语法优化了 OR 条件
  }
} // 类定义结束，GeodeZipsTest 测试类定义完成
