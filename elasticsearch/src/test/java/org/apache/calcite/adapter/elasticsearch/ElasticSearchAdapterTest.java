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
package org.apache.calcite.adapter.elasticsearch; // 声明包名：org.apache.calcite.adapter.elasticsearch，这是 Elasticsearch 适配器的测试包

import org.apache.calcite.jdbc.CalciteConnection; // 导入 Calcite JDBC 连接类，用于获取 schema 和配置
import org.apache.calcite.rel.RelFieldCollation; // 导入关系表达式字段排序类，用于定义排序方向（升序/降序）
import org.apache.calcite.schema.Schema; // 导入 Schema 接口，表示数据库模式（表、视图等的集合）
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，扩展的 Schema 接口，支持动态添加子 schema
import org.apache.calcite.schema.impl.ViewTable; // 导入 ViewTable 类，用于创建 Calcite 视图
import org.apache.calcite.test.CalciteAssert; // 导入 Calcite 测试断言工具类，用于编写测试断言
import org.apache.calcite.test.ElasticsearchChecker; // 导入 ES 查询检查器，用于验证生成的 ES 查询
import org.apache.calcite.util.Bug; // 导入 Bug 类，用于标记已知的 bug 和修复状态
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法

import org.apache.http.HttpHost; // 导入 Apache HttpHost 类，用于表示 HTTP 主机地址（host:port）

import com.fasterxml.jackson.databind.node.ObjectNode; // 导入 Jackson 的 ObjectNode 类，用于表示 JSON 对象
import com.google.common.collect.ImmutableMap; // 导入 Guava 的不可变 Map 类，用于创建不可修改的映射
import com.google.common.io.LineProcessor; // 导入 Guava 的 LineProcessor 接口，用于逐行处理文件内容
import com.google.common.io.Resources; // 导入 Guava 的 Resources 工具类，用于读取类路径下的资源文件

import org.junit.jupiter.api.Assumptions; // 导入 JUnit 5 的 Assumptions 类，用于设置测试前提条件
import org.junit.jupiter.api.BeforeAll; // 导入 JUnit 5 的 BeforeAll 注解，标记在所有测试方法执行前运行一次的方法
import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，标记测试方法
import org.junit.jupiter.api.parallel.ResourceAccessMode; // 导入 JUnit 5 的资源访问模式枚举，定义资源的读写权限
import org.junit.jupiter.api.parallel.ResourceLock; // 导入 JUnit 5 的 ResourceLock 注解，用于控制测试方法的并发执行

import java.io.IOException; // 导入 IO 异常类，用于处理输入输出错误
import java.net.URL; // 导入 URL 类，用于表示统一资源定位符
import java.nio.charset.StandardCharsets; // 导入标准字符集类，提供 UTF-8 等字符集常量
import java.sql.Connection; // 导入 JDBC 连接接口，表示数据库连接
import java.sql.DriverManager; // 导入 JDBC 驱动管理器类，用于创建数据库连接
import java.sql.ResultSet; // 导入 JDBC 结果集接口，表示查询结果
import java.sql.SQLException; // 导入 SQL 异常类，用于处理数据库错误
import java.util.ArrayList; // 导入 ArrayList 类，动态数组实现
import java.util.Arrays; // 导入 Arrays 工具类，提供数组操作方法
import java.util.Collections; // 导入 Collections 工具类，提供集合操作方法
import java.util.HashMap; // 导入 HashMap 类，哈希表实现的 Map
import java.util.List; // 导入 List 接口，表示有序集合
import java.util.Locale; // 导入 Locale 类，用于地区相关的格式化
import java.util.Map; // 导入 Map 接口，表示键值对映射
import java.util.function.Consumer; // 导入 Consumer 函数式接口，表示接受一个参数且无返回值的操作

import static org.junit.jupiter.api.Assertions.assertEquals; // 导入 JUnit 5 的 assertEquals 断言方法，用于验证两个值相等
import static org.junit.jupiter.api.Assertions.assertNotNull; // 导入 JUnit 5 的 assertNotNull 断言方法，用于验证对象不为 null

import static java.util.Objects.requireNonNull; // 导入 Objects.requireNonNull 方法，用于验证参数不为 null

/**
 * Set of tests for ES adapter. Uses real instance via {@link EmbeddedElasticsearchPolicy}. Document
 * source is local {@code zips-mini.json} file (located in test classpath).
 * // Elasticsearch 适配器测试类集合。使用 EmbeddedElasticsearchPolicy 提供的真实 ES 实例进行测试。
 * // 文档数据来源是本地的 zips-mini.json 文件（位于测试类路径下）。
 * // 该测试类全面测试了 Calcite 与 Elasticsearch 的集成功能，包括：
 * // 1. 基本查询功能：SELECT、WHERE、过滤条件
 * // 2. 排序功能：ORDER BY、多字段排序、升序/降序
 * // 3. 分页功能：LIMIT、OFFSET
 * // 4. 聚合功能：COUNT、SUM、AVG、MIN、MAX、GROUP BY
 * // 5. 高级查询：IN、NOT IN、OR、AND、NOT 操作符
 * // 6. 近似计数：APPROX_COUNT_DISTINCT（使用 HyperLogLog++ 算法）
 * // 7. 视图支持：通过 Calcite 视图简化查询
 * // 8. 别名支持：ES 索引别名
 * // 9. SSL 配置：禁用 SSL 验证
 * // 10. 主机排序：确保缓存键的一致性
 * // 测试数据包含 149 条美国邮政编码记录，每条记录包含：id、city、loc（经纬度）、pop（人口）、state（州）
 */
@ResourceLock(value = "elasticsearch-scrolls", mode = ResourceAccessMode.READ)
class ElasticSearchAdapterTest {

  public static final EmbeddedElasticsearchPolicy NODE = EmbeddedElasticsearchPolicy.create(); // 嵌入式 Elasticsearch 实例策略对象，用于管理测试用的 ES 节点，提供索引创建、数据插入、客户端访问等功能

  /** Default index/type name. */
  private static final String ZIPS = "zips"; // 默认的 ES 索引名称，存储邮政编码数据
  private static final String ZIPS_ALIAS = "zips_alias"; // ES 索引别名，用于测试别名查询功能
  private static final int ZIPS_SIZE = 149; // 测试数据集中邮政编码记录的总数，用于验证查询结果的完整性

  /**
   * Used to create {@code zips} index and insert zip data in bulk.
   * // 用于创建 zips 索引并批量插入邮政编码数据到 Elasticsearch 中。
   * // 该方法在所有测试方法执行前运行一次，完成以下操作：
   // 1. 定义索引的映射结构（mapping），指定字段类型：city 和 state 为 keyword 类型，pop 为 long 类型
   // 2. 创建名为 "zips" 的索引
   // 3. 为该索引创建别名 "zips_alias"
   // 4. 从类路径下的 zips-mini.json 文件中读取测试数据
   // 5. 将每行 JSON 数据转换为 ObjectNode 对象，并将 _id 字段重命名为 id（因为 _id 是 ES 保留字段）
   // 6. 使用批量插入 API 将所有数据写入索引
   // 7. 验证数据不为空，否则抛出异常
   * @throws Exception when instance setup failed
   // 当实例设置失败时抛出异常，包括：索引创建失败、数据读取失败、批量插入失败等
   */
  @BeforeAll
  public static void setupInstance() throws Exception {
    final Map<String, String> mapping =
        ImmutableMap.of("city", "keyword", "state", "keyword", "pop", "long"); // 创建不可变的映射配置，定义索引字段类型：city 和 state 为 keyword（精确匹配），pop 为 long（数值类型）

    NODE.createIndex(ZIPS, mapping); // 在 ES 实例中创建名为 "zips" 的索引，并应用上面定义的字段映射
    NODE.createAlias(ZIPS, ZIPS_ALIAS); // 为 "zips" 索引创建别名 "zips_alias"，允许通过别名访问同一个索引

    // load records from file // 从文件加载记录
    final List<ObjectNode> bulk = new ArrayList<>(); // 创建列表用于存储批量插入的 JSON 文档对象
    final URL url =
        requireNonNull(
            ElasticSearchAdapterTest.class.getResource("/zips-mini.json"),
            "url"); // 获取测试数据文件的 URL 路径，文件位于类路径根目录下，如果找不到则抛出 NullPointerException
    Resources.readLines(url,
        StandardCharsets.UTF_8, new LineProcessor<Void>() { // 使用 Guava 的 Resources 工具按 UTF-8 编码逐行读取文件内容，使用 LineProcessor 处理每一行
          @Override public boolean processLine(String line) throws IOException { // 处理文件中的每一行数据
            line = line.replace("_id", "id"); // _id is a reserved attribute in ES // 将 _id 替换为 id，因为 _id 是 ES 的保留字段，不能作为文档属性
            bulk.add((ObjectNode) NODE.mapper().readTree(line)); // 使用 ES 的 ObjectMapper 将 JSON 字符串解析为 ObjectNode 对象，并添加到批量列表中
            return true; // 返回 true 表示继续处理下一行
          }

          @Override public Void getResult() { // 处理完所有行后调用，返回最终结果
            return null; // 不需要返回结果，返回 null
          }
        });

    if (bulk.isEmpty()) { // 检查批量列表是否为空
      throw new IllegalStateException("No records to index. Empty file ?"); // 如果为空，抛出异常，可能是文件为空或读取失败
    }

    NODE.insertBulk(ZIPS, bulk); // 使用 ES 客户端的批量插入 API，将所有文档一次性插入到 "zips" 索引中
  }

  private static Connection createConnection() throws SQLException { // 创建 Calcite 数据库连接，并配置 Elasticsearch 适配器和视图
    final Connection connection =
        DriverManager.getConnection("jdbc:calcite:lex=JAVA"); // 使用 JDBC 驱动创建 Calcite 连接，设置词法分析器为 JAVA 模式（标识符区分大小写）
    final SchemaPlus root =
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 解包连接获取 CalciteConnection 对象，然后获取根 schema（schema 的顶层容器）

    root.add("elastic",
        new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), null)); // 在根 schema 下添加名为 "elastic" 的子 schema，使用 ElasticsearchSchema 适配器连接到真实的 ES 实例，传入 REST 客户端、JSON 映射器和空配置

    // add calcite view programmatically // 以编程方式添加 Calcite 视图，简化查询语法
    final String viewSql = "select cast(_MAP['city'] AS varchar(20)) AS \"city\", " // 视图的 SQL 定义：将 ES 的 MAP 类型字段转换为结构化字段
        + " cast(_MAP['loc'][0] AS float) AS \"longitude\",\n" // 提取经度（loc 数组的第一个元素）并转换为 float
        + " cast(_MAP['loc'][1] AS float) AS \"latitude\",\n" // 提取纬度（loc 数组的第二个元素）并转换为 float
        + " cast(_MAP['pop'] AS integer) AS \"pop\", " // 转换人口为 integer 类型
        + " cast(_MAP['state'] AS varchar(2)) AS \"state\", " // 转换州代码为 varchar(2) 类型
        + " cast(_MAP['id'] AS varchar(5)) AS \"id\" " // 转换 ID 为 varchar(5) 类型
        + "from \"elastic\".\"zips\""; // 从 elastic schema 的 zips 表（实际是 ES 索引）中查询

    root.add("zips",
        ViewTable.viewMacro(root, viewSql,
            Collections.singletonList("elastic"), // 视图依赖的 schema 路径列表
            Arrays.asList("elastic", "view"), // 视图的完整路径名称
            false)); // 是否可修改（false 表示只读视图）

    return connection; // 返回配置好的连接对象
  }

  private CalciteAssert.AssertThat calciteAssert() { // 创建 Calcite 断言构建器，用于编写测试断言
    return CalciteAssert.that() // 创建 CalciteAssert 构建器实例
        .with(ElasticSearchAdapterTest::createConnection); // 设置连接提供者，使用 createConnection 方法创建测试用的数据库连接
  }

  /** Tests using a Calcite view. */
  // 测试使用 Calcite 视图的查询功能
  @Test void view() { // 测试方法：验证通过 Calcite 视图查询数据的功能
    calciteAssert() // 创建断言构建器
        .query("select * from zips where city = 'BROOKLYN'") // 执行 SQL 查询：从 zips 视图中选择城市为 'BROOKLYN' 的所有记录
        .returns("city=BROOKLYN; longitude=-73.956985; latitude=40.646694; " // 验证返回的第一条记录，包含完整的字段值
            + "pop=111396; state=NY; id=11226\n")
        .returnsCount(1); // 验证返回的记录总数为 1
  }

  @Test void emptyResult() { // 测试方法：验证空结果集的处理
    calciteAssert() // 创建断言构建器
        .query("select * from zips limit 0") // 执行 SQL 查询：使用 limit 0 返回空结果集
        .returnsCount(0); // 验证返回的记录数为 0

    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips where _MAP['Foo'] = '_MISSING_'") // 执行 SQL 查询：查询不存在的字段 'Foo'，应该返回空结果
        .returnsCount(0); // 验证返回的记录数为 0
  }

  @Test void testDisableSSL() throws SQLException { // 测试方法：验证禁用 SSL 验证的配置
    Connection connection =
        DriverManager.getConnection("jdbc:calcite:lex=JAVA"); // 创建 Calcite 连接
    final SchemaPlus root =
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 获取根 schema

    final CalciteConnection calciteConnection =
        connection.unwrap(CalciteConnection.class); // 解包为 CalciteConnection 对象

    final ElasticsearchSchemaFactory esSchemaFactory = new ElasticsearchSchemaFactory(); // 创建 ES schema 工厂实例
    Map<String, Object> options = new HashMap<>(); // 创建配置选项映射
    String hosts = "[\"" + NODE.restClient().getNodes()
        .get(0).getHost().toString() + "\"]"; // 获取 ES 节点的主机地址并格式化为 JSON 数组字符串
    options.put("username", "user1"); // 设置用户名（测试用）
    options.put("password", "password"); // 设置密码（测试用）
    options.put("pathPrefix", ""); // 设置路径前缀为空
    options.put("disableSSLVerification", "true"); // 禁用 SSL 证书验证（用于测试环境）
    options.put("hosts", hosts); // 设置 ES 主机列表

    final Schema esSchmea =
        esSchemaFactory.create(calciteConnection.getRootSchema(), "es_no_ssl", options); // 使用工厂创建 ES schema，传入根 schema、schema 名称和配置选项

    assertNotNull(esSchmea); // 断言创建的 schema 不为 null
  }

  @Test void basic() { // 测试方法：验证基本的查询功能
    calciteAssert() // 创建断言构建器
        // by default elastic returns max 10 records // 默认情况下 ES 最多返回 10 条记录
        .query("select * from elastic.zips") // 执行 SQL 查询：从 elastic.zips 表（ES 索引）中选择所有记录
        .runs(); // 验证查询能够成功执行（不检查具体结果）

    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips where _MAP['city'] = 'BROOKLYN'") // 执行 SQL 查询：查询城市为 'BROOKLYN' 的记录（使用 _MAP 访问 ES 的 MAP 类型字段）
        .returnsCount(1); // 验证返回的记录数为 1

    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips where"
            + " _MAP['city'] in ('BROOKLYN', 'WASHINGTON')") // 执行 SQL 查询：使用 IN 操作符查询城市为 'BROOKLYN' 或 'WASHINGTON' 的记录
        .returnsCount(2); // 验证返回的记录数为 2

    // lower-case // 测试大小写敏感性
    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips where "
            + "_MAP['city'] in ('brooklyn', 'Brooklyn', 'BROOK') ") // 执行 SQL 查询：使用小写和混合大小写的城市名（ES keyword 类型区分大小写）
        .returnsCount(0); // 验证返回的记录数为 0（因为 keyword 类型精确匹配，大小写不同）

    // missing field // 测试不存在的字段
    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips where _MAP['CITY'] = 'BROOKLYN'") // 执行 SQL 查询：使用大写的字段名 'CITY'（ES 字段名区分大小写）
        .returnsCount(0); // 验证返回的记录数为 0（因为字段名不存在）


    // limit 0 // 测试 limit 0
    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips limit 0") // 执行 SQL 查询：使用 limit 0 返回空结果
        .returnsCount(0); // 验证返回的记录数为 0
  }

  @Test void testAlias() { // 测试方法：验证 ES 索引别名的查询功能
    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips_alias") // 执行 SQL 查询：通过别名 zips_alias 查询数据（别名指向 zips 索引）
        .returnsCount(ZIPS_SIZE); // 验证返回的记录数为 149（ZIPS_SIZE 常量的值）
  }

  @Test void testSort() { // 测试方法：验证排序功能（使用 Calcite 视图）
    final String explain = "PLAN=ElasticsearchToEnumerableConverter\n" // 定义预期的执行计划字符串
        + "  ElasticsearchSort(sort0=[$4], dir0=[ASC])\n" // ES 排序节点：按第 5 个字段（state）升序排序
        + "    ElasticsearchProject(city=[CAST(ITEM($0, 'city')):VARCHAR(20)], longitude=[CAST(ITEM(ITEM($0, 'loc'), 0)):FLOAT], latitude=[CAST(ITEM(ITEM($0, 'loc'), 1)):FLOAT], pop=[CAST(ITEM($0, 'pop')):INTEGER], state=[CAST(ITEM($0, 'state')):VARCHAR(2)], id=[CAST(ITEM($0, 'id')):VARCHAR(5)])\n" // ES 投影节点：从 MAP 中提取字段并进行类型转换
        + "      ElasticsearchTableScan(table=[[elastic, zips]])"; // ES 表扫描节点：扫描 zips 索引

    calciteAssert() // 创建断言构建器
        .query("select * from zips order by state") // 执行 SQL 查询：从 zips 视图选择所有记录，按 state 字段升序排序
        .returnsCount(ZIPS_SIZE) // 验证返回的记录数为 149
        .returns(sortedResultSetChecker("state", RelFieldCollation.Direction.ASCENDING)) // 验证结果集按 state 字段升序排列
        .explainContains(explain); // 验证执行计划包含预期的字符串
  }

  @Test void testSortLimit() { // 测试方法：验证排序 + 分页功能（OFFSET + FETCH）
    final String sql = "select state, pop from zips\n" // 定义 SQL 查询：选择 state 和 pop 字段
        + "order by state, pop offset 2 rows fetch next 3 rows only"; // 按 state 和 pop 升序排序，跳过前 2 行，取接下来的 3 行
    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .returnsUnordered("state=AK; pop=32383", // 验证返回的 3 条记录（顺序不重要）
            "state=AL; pop=42124",
            "state=AL; pop=43862")
        .queryContains( // 验证生成的 ES 查询包含预期的参数
            ElasticsearchChecker.elasticsearchChecker( // 创建 ES 查询检查器
                "'_source' : ['state', 'pop']", // 检查 _source 字段只包含 state 和 pop
                "sort: [ {state: {'missing':'_last', 'order':'asc'}}, " // 检查排序配置：state 升序，缺失值排在最后
                    + "{pop: {'missing':'_last', 'order':'asc'}}]", // 检查排序配置：pop 升序，缺失值排在最后
                "from: 2", // 检查分页偏移量为 2
                "size: 3")); // 检查返回大小为 3
  }

  /**
   * Throws {@code AssertionError} if result set is not sorted by {@code column}.
   * {@code null}s are ignored.
   * // 如果结果集未按指定列排序，则抛出 AssertionError 异常。
   * // null 值会被忽略，不参与排序验证。
   * // 该方法创建一个 ResultSet 的 Consumer 函数，用于验证结果集的排序是否正确。
   * // 验证逻辑：
   // 1. 遍历结果集，提取指定列的所有非 null 值（必须是 Comparable 类型）
   // 2. 检查相邻元素是否符合预期的排序方向
   // 3. 如果发现不符合排序的情况，抛出 AssertionError 并显示详细信息
   *
   * @param column column to be extracted (as comparable object).
   // 要提取和验证排序的列名
   * @param direction ascending / descending
   // 排序方向：升序（ASCENDING）或降序（DESCENDING）
   * @return consumer which throws exception
   // 返回一个 Consumer<ResultSet> 函数，该函数会检查结果集排序并在不符合时抛出异常
   */
  private static Consumer<ResultSet> sortedResultSetChecker(String column,
      RelFieldCollation.Direction direction) { // 创建结果集排序检查器
    requireNonNull(column, "column"); // 验证列名不为 null
    return rset -> { // 返回一个 Lambda 函数，接收 ResultSet 参数
      try { // 尝试处理结果集
        final List<Comparable<?>> states = new ArrayList<>(); // 创建列表存储所有可比较的值
        while (rset.next()) { // 遍历结果集的每一行
          Object object = rset.getObject(column); // 获取指定列的值
          if (object != null && !(object instanceof Comparable)) { // 检查值不为 null 且实现了 Comparable 接口
            final String message = String.format(Locale.ROOT, "%s is not comparable", object); // 创建错误消息
            throw new IllegalStateException(message); // 抛出异常，因为值不可比较
          }
          if (object != null) { // 如果值不为 null
            //noinspection rawtypes // 忽略原始类型警告
            states.add((Comparable) object); // 将值添加到列表中
          }
        }
        for (int i = 0; i < states.size() - 1; i++) { // 遍历列表，检查相邻元素的排序
          //noinspection rawtypes // 忽略原始类型警告
          final Comparable current = states.get(i); // 获取当前元素
          //noinspection rawtypes // 忽略原始类型警告
          final Comparable next = states.get(i + 1); // 获取下一个元素
          //noinspection unchecked // 忽略未检查类型转换警告
          final int cmp = current.compareTo(next); // 比较两个元素
          if (direction == RelFieldCollation.Direction.ASCENDING ? cmp > 0 : cmp < 0) { // 根据排序方向检查是否违反排序规则
            final String message =
                String.format(Locale.ROOT, // 创建详细的错误消息
                    "Column %s NOT sorted (%s): %s (index:%d) > %s (index:%d) count: %d",
                    column, direction, current, i, next, i + 1, states.size()); // 包含列名、方向、当前值、索引、下一个值、总数等信息
            throw new AssertionError(message); // 抛出断言错误
          }
        }
      } catch (SQLException e) { // 捕获 SQL 异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    };
  }

  /**
   * Sorting (and aggregating) directly on items without a view.
   *
   * <p>Queries of type:
   * {@code select _MAP['a'] from elastic order by _MAP['b']}
   * // 在不使用 Calcite 视图的情况下，直接对 MAP 类型的字段进行排序和聚合操作。
   * // 这种查询类型直接访问 Elasticsearch 的 MAP 类型字段，通过 _MAP['字段名'] 的方式访问。
   * // 测试场景包括：
   // 1. 直接对 MAP 字段排序
   // 2. 条件过滤 + 排序
   // 3. OR 条件查询 + 排序
   // 4. 投影 MAP 字段
   // 5. 别名 + 排序
   // 6. 聚合 + GROUP BY + 排序
   */
  @Test void testSortNoSchema() { // 测试方法：验证不使用视图时直接对 MAP 字段排序和聚合的功能
    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips order by _MAP['city']") // 执行 SQL 查询：从 elastic.zips 表选择所有记录，按 _MAP['city'] 字段排序
        .returnsCount(ZIPS_SIZE); // 验证返回的记录数为 149

    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips where _MAP['state'] = 'NY' order by _MAP['city']") // 执行 SQL 查询：查询 NY 州的记录，按城市排序
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker( // 创建 ES 查询检查器
            "query:{'constant_score':{filter:{term:{state:'NY'}}}}", // 检查查询使用 constant_score 过滤器，精确匹配 state='NY'
            "sort:[{city:{'missing':'_last', 'order':'asc'}}]", // 检查排序配置：按 city 升序，缺失值排在最后
            String.format(Locale.ROOT, "size:%s", ElasticsearchTransport.DEFAULT_FETCH_SIZE))) // 检查返回大小为默认值
        .returnsOrdered( // 验证返回的记录按顺序排列
          "_MAP={id=11226, city=BROOKLYN, loc=[-73.956985, 40.646694], pop=111396, state=NY}", // 第一条记录
          "_MAP={id=11373, city=JACKSON HEIGHTS, loc=[-73.878551, 40.740388], pop=88241, state=NY}", // 第二条记录
          "_MAP={id=10021, city=NEW YORK, loc=[-73.958805, 40.768476], pop=106564, state=NY}"); // 第三条记录

    calciteAssert() // 创建断言构建器
        .query("select _MAP['state'] from elastic.zips order by _MAP['city']") // 执行 SQL 查询：只选择 state 字段，按 city 字段排序
        .returnsCount(ZIPS_SIZE); // 验证返回的记录数为 149

    calciteAssert() // 创建断言构建器
        .query("select * from elastic.zips where _MAP['state'] = 'NY' or " // 执行 SQL 查询：使用 OR 条件查询
            + "_MAP['city'] = 'BROOKLYN'"
            + " order by _MAP['city']") // 按 city 字段排序
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker( // 创建 ES 查询检查器
                "query:{'dis_max':{'queries':[{'bool':{'should':" // 检查查询使用 dis_max 查询（取匹配度最高的查询结果）
                    + "[{'term':{'state':'NY'}},{'term':" // 包含两个 should 条件：state='NY' 或 city='BROOKLYN'
                    + "{'city':'BROOKLYN'}}]}}]}},'sort':[{'city':{'missing':'_last', 'order':'asc'}}]", // 排序配置
                String.format(Locale.ROOT, "size:%s", // 检查返回大小
                    ElasticsearchTransport.DEFAULT_FETCH_SIZE))); // 使用默认值

    calciteAssert() // 创建断言构建器
        .query("select _MAP['city'] from elastic.zips where _MAP['state'] = 'NY' " // 执行 SQL 查询：选择 NY 州的城市
            + "order by _MAP['city']") // 按城市排序
        .returnsOrdered("EXPR$0=BROOKLYN", // 验证返回的记录按顺序排列
            "EXPR$0=JACKSON HEIGHTS",
            "EXPR$0=NEW YORK");

    calciteAssert() // 创建断言构建器
        .query("select _MAP['city'] as city, _MAP['state'] from elastic.zips " // 执行 SQL 查询：选择 city 和 state，使用别名
            + "order by _MAP['city'] asc") // 按 city 升序排序
        .returns(sortedResultSetChecker("city", RelFieldCollation.Direction.ASCENDING)) // 验证结果集按 city 升序排列
        .returnsCount(ZIPS_SIZE); // 验证返回的记录数为 149

    calciteAssert() // 创建断言构建器
        .query("select _MAP['city'] as city, _MAP['state'] from elastic.zips " // 执行 SQL 查询：选择 city 和 state，使用别名
            + "order by _MAP['city'] desc") // 按 city 降序排序
        .returns(sortedResultSetChecker("city", RelFieldCollation.Direction.DESCENDING)) // 验证结果集按 city 降序排列
        .returnsCount(ZIPS_SIZE); // 验证返回的记录数为 149

    calciteAssert() // 创建断言构建器
        .query("select max(_MAP['pop']), min(_MAP['pop']), _MAP['state'] from elastic.zips " // 执行 SQL 查询：聚合查询，计算每个州的最大和最小人口
            + "group by _MAP['state'] order by _MAP['state'] limit 3") // 按 state 分组，排序，限制返回 3 条
        .returnsOrdered("EXPR$0=32383.0; EXPR$1=23238.0; EXPR$2=AK", // 验证返回的记录按顺序排列
             "EXPR$0=44165.0; EXPR$1=42124.0; EXPR$2=AL",
             "EXPR$0=53532.0; EXPR$1=37428.0; EXPR$2=AR");

    calciteAssert() // 创建断言构建器
        .query("select max(_MAP['pop']), min(_MAP['pop']), _MAP['state'] from elastic.zips " // 执行 SQL 查询：聚合查询，计算 NY 州的最大和最小人口
            + "where _MAP['state'] = 'NY' group by _MAP['state'] order by _MAP['state'] limit 3") // 过滤 NY 州，分组，排序
        .returns("EXPR$0=111396.0; EXPR$1=88241.0; EXPR$2=NY\n"); // 验证返回的记录
  }

  /** Tests sorting by multiple fields (in different direction: asc/desc). */
  // 测试多字段排序功能（包括不同的排序方向：升序/降序）
  @Test void sortAscDesc() { // 测试方法：验证多字段排序，每个字段可以有不同的排序方向
    final String sql = "select city, state, pop from zips\n" // 定义 SQL 查询：选择 city、state、pop 字段
        + "order by pop desc, state asc, city desc limit 3"; // 按 pop 降序、state 升序、city 降序排序，限制返回 3 条
    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .returnsOrdered("city=CHICAGO; state=IL; pop=112047", // 验证返回的 3 条记录按顺序排列
             "city=BROOKLYN; state=NY; pop=111396",
             "city=NEW YORK; state=NY; pop=106564")
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker( // 创建 ES 查询检查器
                "'_source':['city','state','pop']", // 检查 _source 字段只包含 city、state、pop
                "sort:[{pop:{'missing':'_first', 'order':'desc'}}, " // 检查排序配置：pop 降序，缺失值排在最前
                    + "{state:{'missing':'_last', 'order':'asc'}}, " // 检查排序配置：state 升序，缺失值排在最后
                    + "{city:{'missing':'_first', 'order':'desc'}}]", // 检查排序配置：city 降序，缺失值排在最前
                "size:3")); // 检查返回大小为 3
  }

  @Test void testOffsetLimit() { // 测试方法：验证 OFFSET + FETCH 分页功能
    final String sql = "select state, id from zips\n" // 定义 SQL 查询：选择 state 和 id 字段
        + "offset 2 fetch next 3 rows only"; // 跳过前 2 行，取接下来的 3 行
    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .runs() // 验证查询能够成功执行
        .returnsCount(3) // 验证返回的记录数为 3
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker( // 创建 ES 查询检查器
                "_source : ['state', 'id']", // 检查 _source 字段只包含 state 和 id
                "from: 2", // 检查分页偏移量为 2
                "size: 3")); // 检查返回大小为 3
  }

  @Test void testLimit() { // 测试方法：验证 FETCH ONLY 分页功能（只有 LIMIT，没有 OFFSET）
    final String sql = "select state, id from zips\n" // 定义 SQL 查询：选择 state 和 id 字段
        + "fetch next 3 rows only"; // 只取前 3 行

    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .runs() // 验证查询能够成功执行
        .returnsCount(3) // 验证返回的记录数为 3
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker( // 创建 ES 查询检查器
                "'_source':['state','id']", // 检查 _source 字段只包含 state 和 id
                "size:3")); // 检查返回大小为 3
  }

  @Test void limit2() { // 测试方法：验证 LIMIT 语法（另一种写法）
    final String sql = "select id from zips limit 5"; // 定义 SQL 查询：选择 id 字段，限制返回 5 条（使用 LIMIT 语法）
    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .runs() // 验证查询能够成功执行
        .returnsCount(5) // 验证返回的记录数为 5
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker( // 创建 ES 查询检查器
                "'_source':['id']", // 检查 _source 字段只包含 id
                "size:5")); // 检查返回大小为 5
  }

  @Test void testFilterSort() { // 测试方法：验证过滤 + 排序功能
    final String sql = "select * from zips\n" // 定义 SQL 查询：选择所有字段
        + "where state = 'CA' and pop >= 94000\n" // 过滤条件：州为 CA 且人口 >= 94000
        + "order by state, pop"; // 按 state 和 pop 升序排序
    final String explain = "PLAN=ElasticsearchToEnumerableConverter\n" // 定义预期的执行计划字符串
        + "  ElasticsearchSort(sort0=[$4], sort1=[$3], dir0=[ASC], dir1=[ASC])\n" // ES 排序节点：按第 5 和第 4 个字段升序排序
        + "    ElasticsearchProject(city=[CAST(ITEM($0, 'city')):VARCHAR(20)], longitude=[CAST(ITEM(ITEM($0, 'loc'), 0)):FLOAT], latitude=[CAST(ITEM(ITEM($0, 'loc'), 1)):FLOAT], pop=[CAST(ITEM($0, 'pop')):INTEGER], state=[CAST(ITEM($0, 'state')):VARCHAR(2)], id=[CAST(ITEM($0, 'id')):VARCHAR(5)])\n" // ES 投影节点：从 MAP 中提取字段并进行类型转换
        + "      ElasticsearchFilter(condition=[AND(=(CAST(CAST(ITEM($0, 'state')):VARCHAR(2)):CHAR(2), 'CA'), >=(CAST(ITEM($0, 'pop')):INTEGER, 94000))])\n" // ES 过滤节点：应用 AND 条件过滤
        + "        ElasticsearchTableScan(table=[[elastic, zips]])\n\n"; // ES 表扫描节点：扫描 zips 索引
    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .returnsOrdered("city=NORWALK; longitude=-118.081767; latitude=33.90564;" // 验证返回的 3 条记录按顺序排列
                + " pop=94188; state=CA; id=90650",
            "city=LOS ANGELES; longitude=-118.258189; latitude=34.007856;"
                + " pop=96074; state=CA; id=90011",
            "city=BELL GARDENS; longitude=-118.17205; latitude=33.969177;"
                + " pop=99568; state=CA; id=90201")
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'query' : " // 检查查询部分
                    + "{'constant_score':{filter:{bool:" // 使用 constant_score 过滤器提高查询性能
                    + "{must:[{term:{state:'CA'}}, " // must 条件：精确匹配 state='CA'
                    + "{range:{pop:{gte:94000}}}]}}}}", // must 条件：范围查询 pop >= 94000
                "'script_fields': {longitude:{script:'params._source.loc[0]'}, " // 检查脚本字段：使用脚本提取经度
                    + "latitude:{script:'params._source.loc[1]'}, " // 检查脚本字段：使用脚本提取纬度
                    + "city:{script: 'params._source.city'}, " // 检查脚本字段：使用脚本提取城市
                    + "pop:{script: 'params._source.pop'}, " // 检查脚本字段：使用脚本提取人口
                    + "state:{script: 'params._source.state'}, " // 检查脚本字段：使用脚本提取州
                    + "id:{script: 'params._source.id'}}", // 检查脚本字段：使用脚本提取 ID
                "sort: [ {state: {'missing':'_last', 'order':'asc'}}, " // 检查排序配置：state 升序
                    + "{pop: {'missing':'_last', 'order':'asc'}}]", // 检查排序配置：pop 升序
                String.format(Locale.ROOT, "size:%s", ElasticsearchTransport.DEFAULT_FETCH_SIZE))) // 检查返回大小为默认值
        .explainContains(explain); // 验证执行计划包含预期的字符串
  }

  @Test void testDismaxQuery() { // 测试方法：验证 OR 条件查询（使用 dis_max 查询）
    final String sql = "select * from zips\n" // 定义 SQL 查询：选择所有字段
        + "where state = 'CA' or pop >= 94000\n" // 过滤条件：州为 CA 或人口 >= 94000（OR 条件）
        + "order by state, pop"; // 按 state 和 pop 升序排序
    final String explain = "PLAN=ElasticsearchToEnumerableConverter\n" // 定义预期的执行计划字符串
        + "  ElasticsearchSort(sort0=[$4], sort1=[$3], dir0=[ASC], dir1=[ASC])\n" // ES 排序节点：按第 5 和第 4 个字段升序排序
        + "    ElasticsearchProject(city=[CAST(ITEM($0, 'city')):VARCHAR(20)], longitude=[CAST(ITEM(ITEM($0, 'loc'), 0)):FLOAT], latitude=[CAST(ITEM(ITEM($0, 'loc'), 1)):FLOAT], pop=[CAST(ITEM($0, 'pop')):INTEGER], state=[CAST(ITEM($0, 'state')):VARCHAR(2)], id=[CAST(ITEM($0, 'id')):VARCHAR(5)])\n" // ES 投影节点：从 MAP 中提取字段并进行类型转换
        + "      ElasticsearchFilter(condition=[OR(=(CAST(CAST(ITEM($0, 'state')):VARCHAR(2)):CHAR(2), 'CA'), >=(CAST(ITEM($0, 'pop')):INTEGER, 94000))])\n" // ES 过滤节点：应用 OR 条件过滤
        + "        ElasticsearchTableScan(table=[[elastic, zips]])\n\n"; // ES 表扫描节点：扫描 zips 索引
    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'query' : " // 检查查询部分
                    + "{'dis_max':{'queries':[{bool:" // 使用 dis_max 查询（取匹配度最高的查询结果）
                    + "{should:[{term:{state:'CA'}}, " // should 条件：精确匹配 state='CA'
                    + "{range:{pop:{gte:94000}}}]}}]}}", // should 条件：范围查询 pop >= 94000
                "'script_fields': {longitude:{script:'params._source.loc[0]'}, " // 检查脚本字段：使用脚本提取经度
                    + "latitude:{script:'params._source.loc[1]'}, " // 检查脚本字段：使用脚本提取纬度
                    + "city:{script: 'params._source.city'}, " // 检查脚本字段：使用脚本提取城市
                    + "pop:{script: 'params._source.pop'}, " // 检查脚本字段：使用脚本提取人口
                    + "state:{script: 'params._source.state'}, " // 检查脚本字段：使用脚本提取州
                    + "id:{script: 'params._source.id'}}", // 检查脚本字段：使用脚本提取 ID
                "sort: [ {state: {'missing':'_last', 'order':'asc'}}, " // 检查排序配置：state 升序
                    + "{pop: {'missing':'_last', 'order':'asc'}}]", // 检查排序配置：pop 升序
                String.format(Locale.ROOT, "size:%s", // 检查返回大小
                    ElasticsearchTransport.DEFAULT_FETCH_SIZE))) // 使用默认值
        .explainContains(explain); // 验证执行计划包含预期的字符串
  }

  @Test void testFilterSortDesc() { // 测试方法：验证过滤 + 降序排序功能
    Assumptions.assumeTrue(Bug.CALCITE_4645_FIXED, "CALCITE-4645"); // 假设 BUG CALCITE-4645 已修复，否则跳过测试
    final String sql = "select * from zips\n" // 定义 SQL 查询：选择所有字段
        + "where pop BETWEEN 95000 AND 100000\n" // 过滤条件：人口在 95000 到 100000 之间
        + "order by state desc, pop"; // 按 state 降序、pop 升序排序
    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .limit(4) // 限制返回 4 条记录
        .returnsOrdered( // 验证返回的记录按顺序排列
            "city=LOS ANGELES; longitude=-118.258189; latitude=34.007856; pop=96074; state=CA; id=90011",
            "city=BELL GARDENS; longitude=-118.17205; latitude=33.969177; pop=99568; state=CA; id=90201");
  }

  @Test void testInPlan() { // 测试方法：验证 IN 操作符的查询功能
    final String[] searches = { // 定义预期的 ES 查询字符串数组
        "query: {'constant_score':{filter:{terms:{pop:" // 查询部分：使用 constant_score 过滤器和 terms 查询
            + "[96074, 99568]}}}}", // terms 查询：pop 字段匹配 96074 或 99568
        "script_fields: {longitude:{script:'params._source.loc[0]'}, " // 脚本字段：使用脚本提取经度
            +  "latitude:{script:'params._source.loc[1]'}, " // 脚本字段：使用脚本提取纬度
            +  "city:{script: 'params._source.city'}, " // 脚本字段：使用脚本提取城市
            +  "pop:{script: 'params._source.pop'}, " // 脚本字段：使用脚本提取人口
            +  "state:{script: 'params._source.state'}, " // 脚本字段：使用脚本提取州
            +  "id:{script: 'params._source.id'}}", // 脚本字段：使用脚本提取 ID
        String.format(Locale.ROOT, "size:%d", ElasticsearchTransport.DEFAULT_FETCH_SIZE) // 返回大小：使用默认值
    };

    calciteAssert() // 创建断言构建器
        .query("select * from zips where pop in (96074, 99568)") // 执行 SQL 查询：查询人口为 96074 或 99568 的记录
        .returnsUnordered( // 验证返回的 2 条记录（顺序不重要）
            "city=BELL GARDENS; longitude=-118.17205; latitude=33.969177; pop=99568; state=CA; id=90201",
            "city=LOS ANGELES; longitude=-118.258189; latitude=34.007856; pop=96074; state=CA; id=90011")
        .queryContains(ElasticsearchChecker.elasticsearchChecker(searches)); // 验证生成的 ES 查询包含预期的字符串
  }

  @Test void testZips() { // 测试方法：验证基本的查询功能（选择特定字段）
    calciteAssert() // 创建断言构建器
        .query("select state, city from zips") // 执行 SQL 查询：从 zips 视图选择 state 和 city 字段
        .returnsCount(ZIPS_SIZE); // 验证返回的记录数为 149
  }

  @Test void testProject() { // 测试方法：验证投影功能（包括常量表达式）
    final String sql = "select state, city, 0 as zero\n" // 定义 SQL 查询：选择 state、city 字段和常量 0（别名为 zero）
        + "from zips\n" // 从 zips 视图查询
        + "order by state, city"; // 按 state 和 city 升序排序

    calciteAssert() // 创建断言构建器
        .query(sql) // 执行 SQL 查询
        .limit(2) // 限制返回 2 条记录
        .returnsUnordered("state=AK; city=ANCHORAGE; zero=0", // 验证返回的 2 条记录（顺序不重要）
            "state=AK; city=FAIRBANKS; zero=0")
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("script_fields:" // 检查脚本字段
                    + "{zero:{script:'0'}," // 常量字段：使用脚本返回 0
                    + "state:{script:'params._source.state'}," // state 字段：使用脚本提取 state
                    + "city:{script:'params._source.city'}}", // city 字段：使用脚本提取 city
                "sort:[{state:{'missing':'_last', 'order':'asc'}}, " // 检查排序配置：state 升序
                    + "{city:{'missing':'_last', 'order':'asc'}}]", // 检查排序配置：city 升序
                String.format(Locale.ROOT, "size:%d", ElasticsearchTransport.DEFAULT_FETCH_SIZE))); // 检查返回大小为默认值
  }

  @Test void testFilter() { // 测试方法：验证过滤功能（WHERE 子句）
    final String explain = "PLAN=ElasticsearchToEnumerableConverter\n" // 定义预期的执行计划字符串
        + "  ElasticsearchProject(state=[CAST(ITEM($0, 'state')):VARCHAR(2)], city=[CAST(ITEM($0, 'city')):VARCHAR(20)])\n" // ES 投影节点：从 MAP 中提取 state 和 city 字段并进行类型转换
        + "    ElasticsearchFilter(condition=[=(CAST(CAST(ITEM($0, 'state')):VARCHAR(2)):CHAR(2), 'CA')])\n" // ES 过滤节点：应用等值过滤条件 state = 'CA'
        + "      ElasticsearchTableScan(table=[[elastic, zips]])"; // ES 表扫描节点：扫描 zips 索引

    calciteAssert() // 创建断言构建器
        .query("select state, city from zips where state = 'CA'") // 执行 SQL 查询：查询 CA 州的 state 和 city
        .limit(3) // 限制返回 3 条记录
        .returnsUnordered("state=CA; city=BELL GARDENS", // 验证返回的 3 条记录（顺序不重要）
            "state=CA; city=LOS ANGELES",
            "state=CA; city=NORWALK")
        .explainContains(explain); // 验证执行计划包含预期的字符串
  }

  @Test void testFilterReversed() { // 测试方法：验证反向比较操作符（< 和 >）
    calciteAssert() // 创建断言构建器
        .query("select state, city from zips where 'WI' < state order by city") // 执行 SQL 查询：查询 state > 'WI' 的记录（使用反向比较）
        .limit(2) // 限制返回 2 条记录
        .returnsUnordered("state=WV; city=BECKLEY", // 验证返回的 2 条记录（顺序不重要）
            "state=WY; city=CHEYENNE");
    calciteAssert() // 创建断言构建器
        .query("select state, city from zips where state > 'WI' order by city") // 执行 SQL 查询：查询 state > 'WI' 的记录（使用正向比较）
        .limit(2) // 限制返回 2 条记录
        .returnsUnordered("state=WV; city=BECKLEY", // 验证返回的 2 条记录（顺序不重要）
            "state=WY; city=CHEYENNE");
  }

  @Test void agg1() { // 测试方法：验证聚合函数（COUNT、MIN、MAX、SUM、AVG）
    calciteAssert() // 创建断言构建器
        .query("select count(*) from zips") // 执行 SQL 查询：计算 zips 表的总记录数
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false（不返回源文档）
            "size:0", "'stored_fields': '_none_'", "track_total_hits:true")) // 检查 size 为 0，不返回文档，只返回聚合结果
        .returns("EXPR$0=149\n"); // 验证返回的计数结果为 149

    // check with limit (should still return correct result). // 检查带有 limit 的查询（应该仍返回正确结果）
    calciteAssert() // 创建断言构建器
        .query("select count(*) from zips limit 1") // 执行 SQL 查询：计算总记录数，限制返回 1 条
        .returns("EXPR$0=149\n"); // 验证返回的计数结果为 149（limit 不影响聚合结果）

    calciteAssert() // 创建断言构建器
        .query("select count(*) as cnt from zips") // 执行 SQL 查询：计算总记录数，使用别名 cnt
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
            "'stored_fields': '_none_'", // 检查 stored_fields 为 _none_
            "size:0", "track_total_hits:true")) // 检查 size 为 0
        .returns("cnt=149\n"); // 验证返回的计数结果为 149

    calciteAssert() // 创建断言构建器
        .query("select min(pop), max(pop) from zips") // 执行 SQL 查询：计算人口的最小值和最大值
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
            "size:0", // 检查 size 为 0
            "track_total_hits:true", // 检查 track_total_hits 为 true
            "'stored_fields': '_none_'", // 检查 stored_fields 为 _none_
            "aggregations:{'EXPR$0':{min:{field:'pop'}},'EXPR$1':{max:" // 检查聚合配置：min 和 max 聚合
                + "{field:'pop'}}}")) // 聚合字段为 pop
        .returns("EXPR$0=21; EXPR$1=112047\n"); // 验证返回的结果：最小值 21，最大值 112047

    calciteAssert() // 创建断言构建器
        .query("select min(pop) as min1, max(pop) as max1 from zips") // 执行 SQL 查询：计算人口的最小值和最大值，使用别名
        .returns("min1=21; max1=112047\n"); // 验证返回的结果：最小值 21，最大值 112047

    calciteAssert() // 创建断言构建器
        .query("select count(*), max(pop), min(pop), sum(pop), avg(pop) from zips") // 执行 SQL 查询：计算多个聚合函数
        .returns("EXPR$0=149; EXPR$1=112047; EXPR$2=21; EXPR$3=7865489; EXPR$4=52788\n"); // 验证返回的结果：count=149, max=112047, min=21, sum=7865489, avg=52788
  }

  @Test void groupBy() { // 测试方法：验证 GROUP BY 聚合功能（包括 DISTINCT）
    // distinct // 测试 DISTINCT 查询（去重）
    calciteAssert() // 创建断言构建器
        .query("select distinct state\n" // 执行 SQL 查询：查询不重复的 state 值
            + "from zips\n" // 从 zips 视图查询
            + "limit 6") // 限制返回 6 条记录
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("_source:false", // 检查 _source 为 false
                "size:0", "'stored_fields': '_none_'", // 检查 size 为 0
                "aggregations:{'g_state':{'terms':{'field':'state','missing':'__MISSING__', 'size' : 6}}}")) // 检查聚合配置：使用 terms 聚合对 state 字段去重
        .returnsOrdered("state=AK", // 验证返回的 6 条记录按顺序排列
            "state=AL",
            "state=AR",
            "state=AZ",
            "state=CA",
            "state=CO");

    // without aggregate function // 测试不带聚合函数的 GROUP BY
    calciteAssert() // 创建断言构建器
        .query("select state, city\n" // 执行 SQL 查询：选择 state 和 city
            + "from zips\n" // 从 zips 视图查询
            + "group by state, city\n" // 按 state 和 city 分组
            + "order by city limit 10") // 按 city 升序排序，限制返回 10 条
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
                "size:0", "'stored_fields': '_none_'", // 检查 size 为 0
                "aggregations:{'g_city':{'terms':{'field':'city','missing':'__MISSING__','size':10,'order':{'_key':'asc'}}", // 检查聚合配置：先按 city 分组
                "aggregations:{'g_state':{'terms':{'field':'state','missing':'__MISSING__','size':10}}}}}}")) // 再按 state 分组（嵌套聚合）
        .returnsOrdered("state=SD; city=ABERDEEN", // 验证返回的 10 条记录按顺序排列
            "state=SC; city=AIKEN",
            "state=TX; city=ALTON",
            "state=IA; city=AMES",
            "state=AK; city=ANCHORAGE",
            "state=MD; city=BALTIMORE",
            "state=ME; city=BANGOR",
            "state=KS; city=BAVARIA",
            "state=NJ; city=BAYONNE",
            "state=OR; city=BEAVERTON");

    // ascending // 测试升序排序的 GROUP BY
    calciteAssert() // 创建断言构建器
        .query("select min(pop), max(pop), state\n" // 执行 SQL 查询：计算每个州的最小和最大人口
            + "from zips\n" // 从 zips 视图查询
            + "group by state\n" // 按 state 分组
            + "order by state limit 3") // 按 state 升序排序，限制返回 3 条
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
                "size:0", "'stored_fields': '_none_'", // 检查 size 为 0
                "aggregations:{'g_state':{terms:{field:'state',missing:'__MISSING__',size:3," // 检查聚合配置：按 state 分组
                    + " order:{'_key':'asc'}}", // 按 state 升序排序
                "aggregations:{'EXPR$0':{min:{field:'pop'}},'EXPR$1':{max:{field:'pop'}}}}}")) // 嵌套聚合：min 和 max
        .returnsOrdered("EXPR$0=23238; EXPR$1=32383; state=AK", // 验证返回的 3 条记录按顺序排列
            "EXPR$0=42124; EXPR$1=44165; state=AL",
            "EXPR$0=37428; EXPR$1=53532; state=AR");

    // just one aggregation function // 测试只有一个聚合函数的 GROUP BY
    calciteAssert() // 创建断言构建器
        .query("select min(pop), state\n" // 执行 SQL 查询：计算每个州的最小人口
            + "from zips\n" // 从 zips 视图查询
            + "group by state\n" // 按 state 分组
            + "order by state limit 3") // 按 state 升序排序，限制返回 3 条
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
                "size:0", // 检查 size 为 0
                "'stored_fields': '_none_'", // 检查 stored_fields 为 _none_
                "aggregations:{'g_state':{terms:{field:'state',missing:'__MISSING__'," // 检查聚合配置：按 state 分组
                    + "size:3, order:{'_key':'asc'}}", // 按 state 升序排序
                "aggregations:{'EXPR$0':{min:{field:'pop'}} }}}")) // 嵌套聚合：min
        .returnsOrdered("EXPR$0=23238; state=AK", // 验证返回的 3 条记录按顺序排列
            "EXPR$0=42124; state=AL",
            "EXPR$0=37428; state=AR");

    // group by count // 测试 COUNT 聚合函数的 GROUP BY
    calciteAssert() // 创建断言构建器
        .query("select count(city), state\n" // 执行 SQL 查询：计算每个州的城市数量
            + "from zips\n" // 从 zips 视图查询
            + "group by state\n" // 按 state 分组
            + "order by state limit 3") // 按 state 升序排序，限制返回 3 条
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
                "size:0", // 检查 size 为 0
                "'stored_fields': '_none_'", // 检查 stored_fields 为 _none_
                "aggregations:{'g_state':{terms:{field:'state',missing:'__MISSING__'," // 检查聚合配置：按 state 分组
                    + " size:3, order:{'_key':'asc'}}", // 按 state 升序排序
                "aggregations:{'EXPR$0':{'value_count':{field:'city'}} }}}")) // 嵌套聚合：value_count
        .returnsOrdered("EXPR$0=3; state=AK", // 验证返回的 3 条记录按顺序排列
            "EXPR$0=3; state=AL",
            "EXPR$0=3; state=AR");

    // descending // 测试降序排序的 GROUP BY
    calciteAssert() // 创建断言构建器
        .query("select min(pop), max(pop), state\n" // 执行 SQL 查询：计算每个州的最小和最大人口
            + "from zips\n" // 从 zips 视图查询
            + "group by state\n" // 按 state 分组
            + "order by state desc limit 3") // 按 state 降序排序，限制返回 3 条
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
                "size:0", // 检查 size 为 0
                "'stored_fields': '_none_'", // 检查 stored_fields 为 _none_
                "aggregations:{'g_state':{terms:{field:'state',missing:'__MISSING__'," // 检查聚合配置：按 state 分组
                    + "size:3, order:{'_key':'desc'}}", // 按 state 降序排序
                "aggregations:{'EXPR$0':{min:{field:'pop'}},'EXPR$1':" // 嵌套聚合：min 和 max
                    + "{max:{field:'pop'}}}}}")) 
        .returnsOrdered("EXPR$0=25968; EXPR$1=33107; state=WY", // 验证返回的 3 条记录按顺序排列
            "EXPR$0=45196; EXPR$1=70185; state=WV",
            "EXPR$0=51008; EXPR$1=57187; state=WI");
  }

  /** Tests the {@code NOT} operator. */
  // 测试 NOT 操作符的功能
  @Test void notOperator() { // 测试方法：验证 NOT 操作符（包括 NOT IN）
    // largest zips (states) in mini-zip by pop (sorted) : IL, NY, CA, MI // 按人口排序的最大州：IL、NY、CA、MI
    calciteAssert() // 创建断言构建器
        .query("select count(*), max(pop) from zips where state not in ('IL')") // 执行 SQL 查询：查询非 IL 州的记录数和最大人口
        .returns("EXPR$0=146; EXPR$1=111396\n"); // 验证返回的结果：146 条记录，最大人口 111396

    calciteAssert() // 创建断言构建器
        .query("select count(*), max(pop) from zips where not state in ('IL')") // 执行 SQL 查询：使用 NOT 操作符（与上面的查询等价）
        .returns("EXPR$0=146; EXPR$1=111396\n"); // 验证返回的结果：146 条记录，最大人口 111396

    calciteAssert() // 创建断言构建器
        .query("select count(*), max(pop) from zips where not state not in ('IL')") // 执行 SQL 查询：双重 NOT（否定之否定，等价于 state in ('IL')）
        .returns("EXPR$0=3; EXPR$1=112047\n"); // 验证返回的结果：3 条记录（IL 州），最大人口 112047

    calciteAssert() // 创建断言构建器
        .query("select count(*), max(pop) from zips where state not in ('IL', 'NY')") // 执行 SQL 查询：查询非 IL 和 NY 州的记录
        .returns("EXPR$0=143; EXPR$1=99568\n"); // 验证返回的结果：143 条记录，最大人口 99568

    calciteAssert() // 创建断言构建器
        .query("select count(*), max(pop) from zips where state not in ('IL', 'NY', 'CA')") // 执行 SQL 查询：查询非 IL、NY 和 CA 州的记录
        .returns("EXPR$0=140; EXPR$1=84712\n"); // 验证返回的结果：140 条记录，最大人口 84712

  }

  /**
   * Test of {@link org.apache.calcite.sql.fun.SqlStdOperatorTable#APPROX_COUNT_DISTINCT} which
   * will be translated to
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-metrics-cardinality-aggregation.html">Cardinality Aggregation</a>
   * (approximate counts using HyperLogLog++ algorithm).
   * // 测试 APPROX_COUNT_DISTINCT 函数，该函数会被转换为 Elasticsearch 的 Cardinality 聚合
   * // （使用 HyperLogLog++ 算法进行近似计数）。
   * // HyperLogLog++ 是一种概率算法，用于快速估算基数（不重复值的数量），在大数据集上性能优异。
   * // 该算法的优点：
   // 1. 内存占用小：只需要 12KB 的内存即可估算数十亿个不同值的基数
   // 2. 计算速度快：不需要遍历所有数据
   // 3. 可合并：多个 HyperLogLog++ 结构可以合并
   // // 缺点：
   // 1. 精度有限：标准误差约为 0.81%（可配置）
   // 2. 不适合小数据集：小数据集上的误差较大
   */
  @Test void approximateCount() { // 测试方法：验证近似计数函数（APPROX_COUNT_DISTINCT）
    calciteAssert() // 创建断言构建器
        .query("select state, approx_count_distinct(city), approx_count_distinct(pop) from zips" // 执行 SQL 查询：计算每个州的城市和人口的不重复值数量（近似计数）
            + " group by state order by state limit 3") // 按 state 分组，排序，限制返回 3 条
        .queryContains( // 验证生成的 ES 查询
            ElasticsearchChecker.elasticsearchChecker("'_source':false", // 检查 _source 为 false
            "size:0", "'stored_fields': '_none_'", // 检查 size 为 0
            "aggregations:{'g_state':{terms:{field:'state', missing:'__MISSING__', size:3, " // 检查聚合配置：按 state 分组
                + "order:{'_key':'asc'}}", // 按 state 升序排序
            "aggregations:{'EXPR$1':{cardinality:{field:'city'}}", // 嵌套聚合：使用 cardinality 聚合计算 city 的基数
                "'EXPR$2':{cardinality:{field:'pop'}} " // 嵌套聚合：使用 cardinality 聚合计算 pop 的基数
                + " }}}")) 
        .returnsOrdered("state=AK; EXPR$1=3; EXPR$2=3", // 验证返回的 3 条记录按顺序排列：AK 州有 3 个城市和 3 个人口值
            "state=AL; EXPR$1=3; EXPR$2=3", // AL 州有 3 个城市和 3 个人口值
            "state=AR; EXPR$1=3; EXPR$2=3"); // AR 州有 3 个城市和 3 个人口值
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6725">[CALCITE-6725]
   * The caching mechanism key in ElasticsearchSchemaFactory is affected by the order of hosts</a>.
   * // 测试 CALCITE-6725 问题：ElasticsearchSchemaFactory 中的缓存机制键受主机顺序影响。
   * // 问题描述：
   * // 当用户以不同的顺序传递相同的主机列表时（例如 ["host1", "host2"] 和 ["host2", "host1"]），
   * // ElasticsearchSchemaFactory 会创建不同的缓存键，导致创建了重复的 schema 实例。
   * // 这不仅浪费内存，还可能导致连接泄漏。
   * // 解决方案：
   * // 在创建缓存键之前，对主机列表进行排序，确保相同的主机集总是生成相同的缓存键。
   * // 排序规则：按主机的字符串表示（host:port）进行升序排序。
   */
  @Test void testSortHosts() { // 测试方法：验证主机列表排序功能（确保缓存键的一致性）
    HttpHost host1 = HttpHost.create("192.168.1.200:8080"); // 创建第一个 ES 主机对象（IP: 192.168.1.200, 端口: 8080）
    HttpHost host2 = HttpHost.create("192.168.1.100:8080"); // 创建第二个 ES 主机对象（IP: 192.168.1.100, 端口: 8080）
    HttpHost host3 = HttpHost.create("192.168.1.150:8080"); // 创建第三个 ES 主机对象（IP: 192.168.1.150, 端口: 8080）
    List<HttpHost> hosts = Arrays.asList(host1, host2, host3); // 创建主机列表（顺序：200, 100, 150）
    List<HttpHost> sortedHosts = ElasticsearchSchemaFactory.getSortedHost(hosts); // 调用 getSortedHost 方法对主机列表排序
    assertEquals(3, sortedHosts.size()); // 验证排序后的列表大小为 3
    assertEquals("http://192.168.1.100:8080", sortedHosts.get(0).toString()); // 验证第一个主机是 192.168.1.100（按 IP 升序）
    assertEquals("http://192.168.1.150:8080", sortedHosts.get(1).toString()); // 验证第二个主机是 192.168.1.150（按 IP 升序）
    assertEquals("http://192.168.1.200:8080", sortedHosts.get(2).toString()); // 验证第三个主机是 192.168.1.200（按 IP 升序）
  }

}
