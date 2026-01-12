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
package org.apache.calcite.adapter.elasticsearch; // 声明该类属于 Elasticsearch 适配器包，用于测试 Elasticsearch 适配器的投影功能

import org.apache.calcite.jdbc.CalciteConnection; // 导入 Calcite 连接类，用于获取 Calcite 特定的连接功能
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 类，表示可扩展的 schema 对象
import org.apache.calcite.schema.impl.ViewTable; // 导入 ViewTable 类，表示 Calcite 中的视图表实现
import org.apache.calcite.schema.impl.ViewTableMacro; // 导入 ViewTableMacro 类，用于创建视图表的宏
import org.apache.calcite.test.CalciteAssert; // 导入 CalciteAssert 工具类，用于编写测试断言
import org.apache.calcite.test.ElasticsearchChecker; // 导入 ElasticsearchChecker 工具类，用于验证 Elasticsearch 查询
import org.apache.calcite.util.TestUtil; // 导入 TestUtil 工具类，提供测试实用方法

import com.fasterxml.jackson.databind.node.ObjectNode; // 导入 Jackson 的 ObjectNode 类，用于处理 JSON 对象节点
import com.google.common.collect.ImmutableMap; // 导入 Google Guava 的 ImmutableMap 类，用于创建不可变映射

import org.junit.jupiter.api.BeforeAll; // 导入 JUnit 5 的 BeforeAll 注解，用于在所有测试方法执行前运行一次的设置方法
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.junit.jupiter.api.parallel.ResourceAccessMode; // 导入 JUnit 5 的 ResourceAccessMode 枚举，用于指定资源访问模式
import org.junit.jupiter.api.parallel.ResourceLock; // 导入 JUnit 5 的 ResourceLock 注解，用于控制测试的并行执行

import java.sql.Connection; // 导入 JDBC Connection 接口，表示数据库连接
import java.sql.DriverManager; // 导入 DriverManager 类，用于管理 JDBC 驱动程序
import java.sql.ResultSet; // 导入 ResultSet 接口，表示数据库查询结果集
import java.sql.SQLException; // 导入 SQLException 类，表示数据库操作异常
import java.util.Arrays; // 导入 Arrays 工具类，提供数组操作方法
import java.util.Collections; // 导入 Collections 工具类，提供集合操作方法
import java.util.Locale; // 导入 Locale 类，用于本地化操作
import java.util.Map; // 导入 Map 接口，表示键值对映射
import java.util.function.Consumer; // 导入 Consumer 函数式接口，表示接受单个参数且无返回值的操作
import java.util.regex.PatternSyntaxException; // 导入 PatternSyntaxException 类，表示正则表达式语法异常

import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 的 assertThat 静态方法，用于断言
import static org.hamcrest.Matchers.hasToString; // 导入 Hamcrest 的 hasToString 匹配器，用于验证字符串表示
import static org.junit.jupiter.api.Assertions.fail; // 导入 JUnit 5 的 fail 静态方法，用于标记测试失败

/**
 * Checks renaming of fields (also upper, lower cases) during projections.
 * 检查投影操作期间字段的重命名（包括大写、小写情况）。
 * 该测试类专门用于验证 Elasticsearch 适配器在处理投影（projection）操作时，
 * 如何正确处理字段重命名、大小写转换以及嵌套字段的访问。
 * 投影是 SQL 查询中 SELECT 子句的核心功能，用于从结果集中选择特定的列。
 * 在 Elasticsearch 适配器中，投影操作需要正确映射到 Elasticsearch 的 _source 字段过滤机制。
 */
@ResourceLock(value = "elasticsearch-scrolls", mode = ResourceAccessMode.READ) // 使用资源锁确保测试方法按顺序执行，避免并发访问 Elasticsearch 滚动 API 导致的问题，READ 模式表示只读访问
class Projection2Test { // 定义 Projection2Test 测试类，用于测试 Elasticsearch 适配器的投影功能（第二个投影测试类）

  public static final EmbeddedElasticsearchPolicy NODE = EmbeddedElasticsearchPolicy.create(); // 声明公共静态常量 NODE，表示嵌入式 Elasticsearch 实例策略，通过 create() 方法创建，用于在测试中启动和管理 Elasticsearch 节点

  private static final String NAME = "nested"; // 声明私有静态常量 NAME，表示 Elasticsearch 索引名称，值为 "nested"，用于在测试中创建和引用该索引

  @BeforeAll // 使用 JUnit 5 的 BeforeAll 注解，表示该方法在所有测试方法执行前只运行一次，用于设置测试环境
  public static void setupInstance() throws Exception { // 定义静态方法 setupInstance，抛出 Exception 异常，用于初始化 Elasticsearch 测试实例
    final Map<String, String> mappings = // 声明不可变映射 mappings，用于定义 Elasticsearch 索引的字段映射关系，指定每个字段的数据类型
        ImmutableMap.<String, String>builder() // 创建 ImmutableMap 构建器，用于构建不可变的字段映射
            .put("a", "long") // 添加字段映射：字段 "a" 的类型为 long（长整型），表示顶层字段 a
            .put("b", "nested") // 添加字段映射：字段 "b" 的类型为 nested（嵌套对象），表示 b 是一个嵌套对象
            .put("b.a", "long") // 添加字段映射：嵌套字段 "b.a" 的类型为 long，表示嵌套对象 b 中的 a 字段
            .put("b.b", "long") // 添加字段映射：嵌套字段 "b.b" 的类型为 long，表示嵌套对象 b 中的 b 字段
            .put("b.c", "nested") // 添加字段映射：嵌套字段 "b.c" 的类型为 nested，表示 b.c 是一个二级嵌套对象
            .put("b.c.a", "keyword") // 添加字段映射：二级嵌套字段 "b.c.a" 的类型为 keyword（关键字类型），用于全文搜索和精确匹配
            .build(); // 构建不可变的映射对象

    NODE.createIndex(NAME, mappings); // 调用 Elasticsearch 节点的 createIndex 方法创建名为 "nested" 的索引，并应用上面定义的字段映射

    String doc = "{'a': 1, 'b':{'a': 2, 'b':'3', 'c':{'a': 'foo'}}}".replace('\'', '"'); // 创建测试文档字符串，包含嵌套结构，使用单引号后替换为双引号以符合 JSON 格式，包含：顶层字段 a=1，嵌套对象 b 包含 a=2, b='3', c={'a': 'foo'}
    NODE.insertDocument(NAME, (ObjectNode) NODE.mapper().readTree(doc)); // 将文档插入到 Elasticsearch 索引中，使用 mapper() 获取 JSON 映射器，readTree() 将字符串解析为 JSON 树，强制转换为 ObjectNode 后插入
  }

  private static Connection createConnection() throws SQLException { // 定义私有静态方法 createConnection，抛出 SQLException 异常，用于创建并配置 Calcite 数据库连接
    final Connection connection = // 声明 Connection 对象，表示数据库连接
        DriverManager.getConnection("jdbc:calcite:"); // 使用 DriverManager 获取 Calcite JDBC 连接，连接字符串 "jdbc:calcite:" 表示连接到内存中的 Calcite 实例
    final SchemaPlus root = // 声明 SchemaPlus 对象，表示根 schema（模式）
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为 CalciteConnection 类型，然后获取根 schema，根 schema 是所有其他 schema 的父容器

    root.add("elastic", new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), NAME)); // 将 ElasticsearchSchema 添加到根 schema 中，命名为 "elastic"，传递 Elasticsearch REST 客户端、JSON 映射器和索引名称作为参数

    // add calcite view programmatically
    final String viewSql = // 声明视图 SQL 字符串，用于创建 Calcite 视图
        String.format(Locale.ROOT, "select _MAP['a'] AS \"a\", " // 使用 String.format 格式化 SQL，使用 ROOT locale 确保格式一致性，从 _MAP 中提取字段并重命名
            + " _MAP['b.a']  AS \"b.a\", " // 提取嵌套字段 b.a 并重命名为 "b.a"，_MAP 是 Elasticsearch 适配器用于存储文档内容的特殊字段
            + " _MAP['b.b'] AS \"b.b\", " // 提取嵌套字段 b.b 并重命名为 "b.b"
            + " _MAP['b.c.a'] AS \"b.c.a\", " // 提取二级嵌套字段 b.c.a 并重命名为 "b.c.a"
            + " _MAP['_id'] AS \"id\" " // _id field is implicit
            + " from \"elastic\".\"%s\"", NAME); // 从 "elastic" schema 中的指定索引（NAME 变量值）查询数据

    ViewTableMacro macro = // 声明 ViewTableMacro 对象，表示视图表的宏定义
        ViewTable.viewMacro(root, viewSql, Collections.singletonList("elastic"), // 调用 ViewTable.viewMacro 创建视图宏，传递根 schema、视图 SQL、路径列表（这里只有 "elastic"）
            Arrays.asList("elastic", "view"), false); // 传递视图路径（["elastic", "view"]）和是否可修改标志（false 表示只读）
    root.add("VIEW", macro); // 将视图宏添加到根 schema 中，命名为 "VIEW"，这样就可以通过 "VIEW" 名称访问该视图
    return connection; // 返回配置好的数据库连接对象
  }

  @Test void projection() { // 使用 Test 注解标记测试方法，测试基本的投影功能，验证从视图中查询字段并正确返回结果
    CalciteAssert.that() // 创建 CalciteAssert 测试构建器，用于构建和执行测试断言
        .with(Projection2Test::createConnection) // 使用 createConnection 方法创建数据库连接，将该连接传递给测试框架
        .query("select \"a\", \"b.a\", \"b.b\", \"b.c.a\" from view") // 执行 SQL 查询，从 "view" 视图中选择四个字段：a、b.a、b.b、b.c.a，使用双引号引用字段名以保留大小写和特殊字符
        .returns("a=1; b.a=2; b.b=3; b.c.a=foo\n"); // 验证查询结果，期望返回一行数据，字段值分别为 a=1, b.a=2, b.b=3, b.c.a=foo，以分号分隔，末尾有换行符
  }

  @Test void projection2() { // 使用 Test 注解标记测试方法，测试直接从 _MAP 中投影字段，包括不存在的字段，验证空值处理
    String sql = String.format(Locale.ROOT, "select _MAP['a'], _MAP['b.a'], _MAP['b.b'], " // 声明 SQL 查询字符串，使用 String.format 格式化，从 _MAP 中直接提取字段，使用 ROOT locale 确保格式一致性
        + "_MAP['b.c.a'], _MAP['missing'], _MAP['b.missing'] from \"elastic\".\"%s\"", NAME); // 继续查询：提取 b.c.a 字段，以及两个不存在的字段 missing 和 b.missing，从 "elastic" schema 的指定索引查询

    CalciteAssert.that() // 创建 CalciteAssert 测试构建器
        .with(Projection2Test::createConnection) // 使用 createConnection 方法创建数据库连接
        .query(sql) // 执行上面构建的 SQL 查询
        .returns("EXPR$0=1; EXPR$1=2; EXPR$2=3; EXPR$3=foo; EXPR$4=null; EXPR$5=null\n"); // 验证查询结果，期望返回一行数据：前四个字段的值分别为 1, 2, 3, foo，后两个字段（不存在的）返回 null，使用 EXPR$0、EXPR$1 等自动生成的列名
  }

  @Test void projection3() { // 使用 Test 注解标记测试方法，测试 SELECT * 查询和混合投影（* 加上特定字段）
    CalciteAssert.that() // 创建 CalciteAssert 测试构建器
        .with(Projection2Test::createConnection) // 使用 createConnection 方法创建数据库连接
        .query( // 执行查询
            String.format(Locale.ROOT, "select * from \"elastic\".\"%s\"", NAME)) // 使用 String.format 构建 SQL，执行 SELECT * 查询，返回所有字段，从 "elastic" schema 的指定索引查询
        .returns("_MAP={a=1, b={a=2, b=3, c={a=foo}}}\n"); // 验证查询结果，期望返回 _MAP 字段，包含完整的文档结构：a=1, b={a=2, b=3, c={a=foo}}

    CalciteAssert.that() // 创建第二个 CalciteAssert 测试构建器
        .with(Projection2Test::createConnection) // 使用 createConnection 方法创建数据库连接
        .query( // 执行查询
            String.format(Locale.ROOT, "select *, _MAP['a'] from \"elastic\".\"%s\"", NAME)) // 使用 String.format 构建 SQL，执行 SELECT * 加上特定字段 _MAP['a'] 的查询
        .returns("_MAP={a=1, b={a=2, b=3, c={a=foo}}}; EXPR$1=1\n"); // 验证查询结果，期望返回两个字段：_MAP（完整文档）和 EXPR$1（_MAP['a'] 的值 1）
  }

  /**
   * Test that {@code _id} field is available when queried explicitly.
   * 测试 {@code _id} 字段在显式查询时是否可用。
   * _id 是 Elasticsearch 中每个文档的唯一标识符，类似于数据库的主键。
   * 该测试验证了 _id 字段的各种查询场景，包括：
   * 1. 从视图中查询 _id 字段
   * 2. 查询多个 _id 字段
   * 3. _id 与其他字段组合查询
   * 4. 直接从 _MAP 中查询 _id
   * 5. _id 字段的别名使用
   * 6. 验证 _id 字段不会隐式包含在 SELECT * 中
   *
   * @see <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-id-field.html">ID Field</a>
   */
  @Test void projectionWithIdField() { // 使用 Test 注解标记测试方法，测试 _id 字段的投影功能
    final CalciteAssert.AssertThat fixture = // 声明 CalciteAssert.AssertThat 对象，表示测试fixture（测试夹具）
        CalciteAssert.that() // 创建 CalciteAssert 测试构建器
            .with(Projection2Test::createConnection); // 使用 createConnection 方法创建数据库连接并配置到 fixture

    fixture.query("select \"id\" from view") // 执行查询：从 "view" 视图中选择 "id" 字段（id 是在 createConnection 中通过 _MAP['_id'] AS "id" 定义的别名）
        .returns(regexMatch("id=\\p{Graph}+")); // 验证结果：期望返回 id 字段，值为任意可打印字符（\\p{Graph}+ 匹配一个或多个可打印字符）

    fixture.query("select \"id\", \"id\" from view") // 执行查询：从视图中选择两个 "id" 字段（测试重复字段）
        .returns(regexMatch("id=\\p{Graph}+; id=\\p{Graph}+")); // 验证结果：期望返回两个 id 字段，值相同，用分号分隔

    fixture.query("select \"id\", \"a\" from view") // 执行查询：从视图中选择 "id" 和 "a" 字段
        .returns(regexMatch("id=\\p{Graph}+; a=1")); // 验证结果：期望返回 id 和 a 字段，id 为任意可打印字符，a 值为 1

    fixture.query("select \"a\", \"id\" from view") // 执行查询：从视图中选择 "a" 和 "id" 字段（测试字段顺序）
        .returns(regexMatch("a=1; id=\\p{Graph}+")); // 验证结果：期望返回 a 和 id 字段，顺序与查询一致

    // single _id column
    final String sql1 = String.format(Locale.ROOT, "select _MAP['_id'] " // 声明 SQL 查询字符串，从 _MAP 中直接查询 _id 字段
        + " from \"elastic\".\"%s\"", NAME); // 从 "elastic" schema 的指定索引查询
    fixture.query(sql1) // 执行上面构建的 SQL 查询
        .returns(regexMatch("EXPR$0=\\p{Graph}+")); // 验证结果：期望返回 EXPR$0 字段（自动生成的列名），值为任意可打印字符

    // multiple columns: _id and a
    final String sql2 = String.format(Locale.ROOT, "select _MAP['_id'], _MAP['a'] " // 声明 SQL 查询字符串，从 _MAP 中查询 _id 和 a 字段
        + " from \"elastic\".\"%s\"", NAME); // 从 "elastic" schema 的指定索引查询
    fixture.query(sql2) // 执行上面构建的 SQL 查询
        .returns(regexMatch("EXPR$0=\\p{Graph}+; EXPR$1=1")); // 验证结果：期望返回两个字段，EXPR$0 为任意可打印字符，EXPR$1 值为 1

    // multiple _id columns
    final String sql3 = String.format(Locale.ROOT, "select _MAP['_id'], _MAP['_id'] " // 声明 SQL 查询字符串，从 _MAP 中查询两个 _id 字段
        + " from \"elastic\".\"%s\"", NAME); // 从 "elastic" schema 的指定索引查询
    fixture.query(sql3) // 执行上面构建的 SQL 查询
        .returns(regexMatch("EXPR$0=\\p{Graph}+; EXPR$1=\\p{Graph}+")); // 验证结果：期望返回两个 EXPR 字段，值相同

    // _id column with same alias
    final String sql4 = String.format(Locale.ROOT, "select _MAP['_id'] as \"_id\" " // 声明 SQL 查询字符串，从 _MAP 中查询 _id 字段并使用别名 "_id"
        + " from \"elastic\".\"%s\"", NAME); // 从 "elastic" schema 的指定索引查询
    fixture.query(sql4) // 执行上面构建的 SQL 查询
        .returns(regexMatch("_id=\\p{Graph}+")); // 验证结果：期望返回 _id 字段（使用别名），值为任意可打印字符

    // _id field not available implicitly
    String sql5 = // 声明 SQL 查询字符串
        String.format(Locale.ROOT, "select * from \"elastic\".\"%s\"", NAME); // 执行 SELECT * 查询，测试 _id 是否隐式包含
    fixture.query(sql5) // 执行上面构建的 SQL 查询
        .returns(regexMatch("_MAP={a=1, b={a=2, b=3, c={a=foo}}}")); // 验证结果：期望只返回 _MAP 字段，不包含 _id，证明 _id 不会隐式包含在 SELECT * 中

    String sql6 = // 声明 SQL 查询字符串
        String.format(Locale.ROOT, // 使用 String.format 格式化 SQL
            "select *, _MAP['_id'] from \"elastic\".\"%s\"", NAME); // 执行 SELECT * 加上 _MAP['_id'] 的查询
    fixture.query(sql6) // 执行上面构建的 SQL 查询
        .returns(regexMatch("_MAP={a=1, b={a=2, b=3, c={a=foo}}}; EXPR$1=\\p{Graph}+")); // 验证结果：期望返回 _MAP 字段和 EXPR$1 字段（_id 的值）
  }

  /**
   * Avoid using scripting for simple projections.
   * 避免在简单投影中使用脚本。
   *
   * <p> When projecting simple fields (without expression) no
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-scripting.html">scripting</a>
   * should be used just
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-request-source-filtering.html">_source</a>.
   * 当投影简单字段（不包含表达式）时，不应该使用 Elasticsearch 的脚本功能，
   * 而应该使用 _source 字段过滤机制。
   * 
   * 脚本功能虽然强大但性能较差，而 _source 过滤是 Elasticsearch 原生的高性能字段选择机制。
   * 该测试验证了 Elasticsearch 适配器在处理简单投影时，正确地使用 _source 过滤而不是脚本。
   */
  @Test void simpleProjectionNoScripting() { // 使用 Test 注解标记测试方法，测试简单投影不使用脚本功能
    CalciteAssert.that() // 创建 CalciteAssert 测试构建器
        .with(Projection2Test::createConnection) // 使用 createConnection 方法创建数据库连接
        .query( // 执行查询
            String.format(Locale.ROOT, "select _MAP['_id'], _MAP['a'], _MAP['b.a'] from " // 使用 String.format 构建 SQL，从 _MAP 中选择 _id、a 和 b.a 字段
                + " \"elastic\".\"%s\" where _MAP['b.a'] = 2", NAME)) // 添加 WHERE 条件，筛选 b.a 等于 2 的文档
        .queryContains( // 验证生成的 Elasticsearch 查询包含特定的内容
            ElasticsearchChecker.elasticsearchChecker("'query.constant_score.filter.term.b.a':2", // 使用 ElasticsearchChecker 检查生成的查询包含 term 查询，条件为 b.a = 2
                "_source:['a', 'b.a']", // 检查查询使用 _source 过滤，只返回 a 和 b.a 字段，而不是使用脚本
                "size:5196")) // 检查查询的 size 参数（默认值）
        .returns(regexMatch("EXPR$0=\\p{Graph}+; EXPR$1=1; EXPR$2=2")); // 验证查询结果：期望返回三个字段，EXPR$0（_id）为任意可打印字符，EXPR$1（a）为 1，EXPR$2（b.a）为 2

  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4450">[CALCITE-4450]
   * ElasticSearch query with varchar literal projection fails with JsonParseException</a>.
   * 测试用例，用于验证修复 CALCITE-4450 问题：Elasticsearch 查询中包含 varchar 字面量投影时失败并抛出 JsonParseException。
   * 该问题出现在尝试在投影中使用字符串字面量时，Elasticsearch 适配器未能正确处理。
   * 该测试验证了字符串字面量投影现在可以正常工作。 */
  @Test void projectionStringLiteral() { // 使用 Test 注解标记测试方法，测试字符串字面量的投影功能
    CalciteAssert.that() // 创建 CalciteAssert 测试构建器
        .with(Projection2Test::createConnection) // 使用 createConnection 方法创建数据库连接
        .query( // 执行查询
            String.format(Locale.ROOT, "select 'foo' as \"lit\"\n" // 使用 String.format 构建 SQL，选择字符串字面量 'foo' 并使用别名 "lit"
                + "from \"elastic\".\"%s\"", NAME)) // 从 "elastic" schema 的指定索引查询
        .returns("lit=foo\n"); // 验证查询结果：期望返回 lit 字段，值为 "foo"
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4450">[CALCITE-4450]
   * ElasticSearch query with varchar literal projection fails with JsonParseException</a>.
   * 测试用例，用于验证修复 CALCITE-4450 问题：Elasticsearch 查询中包含 varchar 字面量投影时失败并抛出 JsonParseException。
   * 该测试特别关注字符串字面量与列字段混合投影的场景，以及包含转义引号的字符串字面量。
   * 这进一步验证了字符串字面量投影的完整性和正确性。 */
  @Test void projectionStringLiteralAndColumn() { // 使用 Test 注解标记测试方法，测试字符串字面量与列字段的混合投影
    CalciteAssert.that() // 创建 CalciteAssert 测试构建器
        .with(Projection2Test::createConnection) // 使用 createConnection 方法创建数据库连接
        .query( // 执行查询
            String.format(Locale.ROOT, "select 'foo\\\"bar\\\"' as \"lit\", _MAP['a'] as \"a\"\n" // 使用 String.format 构建 SQL，选择包含转义引号的字符串字面量 'foo"bar"' 作为 "lit"，以及 _MAP['a'] 作为 "a"
                + "from \"elastic\".\"%s\"", NAME)) // 从 "elastic" schema 的指定索引查询
        .returns("lit=foo\\\"bar\\\"; a=1\n"); // 验证查询结果：期望返回两个字段，lit 值为 'foo"bar"'（包含引号），a 值为 1
  }

  /**
   * Allows values to contain regular expressions instead of exact values.
   * 允许值包含正则表达式而不是精确值。
   * 
   * 该方法是一个辅助方法，用于在测试中验证查询结果时，支持使用正则表达式匹配字段值。
   * 这对于某些无法预测确切值（如 _id 字段）的场景非常有用。
   * 
   * <pre>
   *   {@code
   *      key1=foo1; key2=\\w+; key4=\\d{3,4}
   *   }
   * </pre>
   * 上面的示例表示：key1 必须精确匹配 "foo1"，key2 可以匹配任何单词字符（\\w+），key4 必须匹配 3 或 4 位数字。
   *
   * @param lines lines with regexp
   * 参数 lines：包含正则表达式的行数组，每行表示一个期望的结果行，格式为 "key1=value1; key2=value2; ..."
   * @return consumer to be used in {@link org.apache.calcite.test.CalciteAssert.AssertQuery}
   * 返回值：一个 Consumer<ResultSet> 对象，用于在 CalciteAssert.AssertQuery 中验证查询结果
   */
  private static Consumer<ResultSet> regexMatch(String...lines) { // 定义私有静态方法 regexMatch，接受可变参数 lines（字符串数组），返回一个 ResultSet 的 Consumer
    return rset -> { // 返回一个 lambda 表达式，接受 ResultSet 参数 rset，表示要验证的结果集
      try { // 开始 try-catch 块，捕获可能的 SQLException
        final int columnCount = rset.getMetaData().getColumnCount(); // 获取结果集的列数，通过元数据对象
        final StringBuilder actual = new StringBuilder(); // 创建 StringBuilder 对象，用于构建实际的查询结果字符串
        int processedRows = 0; // 声明并初始化已处理的行数计数器
        boolean fail = false; // 声明并初始化失败标志，默认为 false
        while (rset.next()) { // 遍历结果集的每一行，使用 next() 方法移动到下一行
          if (processedRows >= lines.length) { // 检查已处理的行数是否超过期望的行数（lines 数组的长度）
            fail = true; // 如果超过，设置失败标志为 true，表示结果行数多于期望
          }

          for (int i = 1; i <= columnCount; i++) { // 遍历当前行的每一列，列索引从 1 开始
            final String name = rset.getMetaData().getColumnName(i); // 获取当前列的名称，通过元数据对象
            final String value = rset.getString(i); // 获取当前列的值，作为字符串
            actual.append(name).append('=').append(value); // 将列名和值追加到 actual 字符串构建器中，格式为 "name=value"
            if (i < columnCount) { // 检查当前列是否不是最后一列
              actual.append("; "); // 如果不是最后一列，追加分号和空格作为分隔符
            }

            // don't re-check if already failed
            if (!fail) { // 检查是否已经失败，如果已经失败则跳过验证逻辑
              // splitting string of type: key1=val1; key2=val2
              final String keyValue = lines[processedRows].split("; ")[i - 1]; // 从期望行字符串中提取第 i 个键值对，使用 "; " 分割，索引为 i-1（因为数组从 0 开始）
              final String[] parts = keyValue.split("=", 2); // 将键值对按等号分割，限制分割次数为 2（以支持值中包含等号的情况）
              final String expectedName = parts[0]; // 获取期望的列名（分割后的第一部分）
              final String expectedValue = parts[1]; // 获取期望的列值（分割后的第二部分）

              boolean valueMatches = expectedValue.equals(value); // 首先尝试精确匹配期望值和实际值

              if (!valueMatches) { // 如果精确匹配失败
                // try regex
                try { // 尝试使用正则表达式匹配
                  valueMatches = value != null && value.matches(expectedValue); // 检查实际值不为 null 且匹配期望的正则表达式模式
                } catch (PatternSyntaxException ignore) { // 捕获正则表达式语法异常
                  // probably not a regular expression
                  // 忽略异常，说明期望值不是正则表达式
                }
              }

              fail = !(name.equals(expectedName) && valueMatches); // 更新失败标志：如果列名不匹配或值不匹配，则设置为 true
            }

          }

          processedRows++; // 增加已处理行数计数器
        }

        // also check that processed same number of rows
        fail &= processedRows == lines.length; // 检查处理的行数是否等于期望的行数，使用 &= 运算符（逻辑与赋值），如果行数不匹配则 fail 为 true

        if (fail) { // 如果验证失败
          assertThat(actual, hasToString(String.join("\n", lines))); // 使用 Hamcrest 断言验证 actual 字符串是否等于期望的字符串（lines 数组用换行符连接）
          fail("Should have failed on previous line, but for some reason didn't"); // 调用 fail() 方法标记测试失败，并输出错误消息
        }
      } catch (SQLException e) { // 捕获 SQLException 异常
        throw TestUtil.rethrow(e); // 使用 TestUtil.rethrow() 方法重新抛出异常，保留原始异常类型
      }
    };
  }
}
