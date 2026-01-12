/* // 行号:1
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可协议
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议
 * this work for additional information regarding copyright ownership. // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权此文件
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下位置获取许可证副本
 * // 行号:8
 * http://www.apache.org/licenses/LICENSE-2.0
 * // 行号:10
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的软件按"原样"分发
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不附带任何形式的保证或条件，无论是明示的还是暗示的
 * See the License for the specific language governing permissions and // 请参阅许可证以了解管理
 * limitations under the License. // 行号:15
 */
package org.apache.calcite.test; // 包声明：org.apache.calcite.test - Druid适配器集成测试包

import org.apache.calcite.adapter.druid.DruidSchema; // 导入DruidSchema类 - Druid模式定义
import org.apache.calcite.config.CalciteConnectionConfig; // 导入CalciteConnectionConfig - Calcite连接配置
import org.apache.calcite.config.CalciteConnectionProperty; // 导入CalciteConnectionProperty - Calcite连接属性枚举
import org.apache.calcite.config.CalciteSystemProperty; // 导入CalciteSystemProperty - Calcite系统属性
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType - 关系数据类型接口
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema - 抽象模式基类
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SqlStdOperatorTable - 标准SQL操作符表
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName - SQL类型名称枚举
import org.apache.calcite.util.Bug; // 导入Bug - 用于管理已知问题的工具类
import org.apache.calcite.util.TestUtil; // 导入TestUtil - 测试工具类

import com.google.common.collect.ArrayListMultimap; // 导入ArrayListMultimap - 基于ArrayList的多重映射
import com.google.common.collect.ImmutableList; // 导入ImmutableList - 不可变列表
import com.google.common.collect.Multimap; // 导入Multimap - 多重映射接口

import org.junit.jupiter.api.Assumptions; // 导入Assumptions - JUnit 5测试假设条件
import org.junit.jupiter.api.BeforeAll; // 导入BeforeAll - JUnit 5所有测试前初始化注解
import org.junit.jupiter.api.Test; // 导入Test - JUnit 5测试方法注解

import java.net.URL; // 导入URL - 统一资源定位符类
import java.sql.DatabaseMetaData; // 导入DatabaseMetaData - 数据库元数据接口
import java.sql.ResultSet; // 导入ResultSet - 结果集接口
import java.sql.SQLException; // 导入SQLException - SQL异常类

import static org.hamcrest.CoreMatchers.is; // 静态导入is - Hamcrest匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat - Hamcrest断言工具
import static org.hamcrest.Matchers.hasSize; // 静态导入hasSize - Hamcrest大小匹配器
import static org.junit.jupiter.api.Assertions.assertFalse; // 静态导入assertFalse - JUnit断言
import static org.junit.jupiter.api.Assertions.assertSame; // 静态导入assertSame - JUnit断言
import static org.junit.jupiter.api.Assertions.assertTrue; // 静态导入assertTrue - JUnit断言
import static org.junit.jupiter.api.Assumptions.assumeTrue; // 静态导入assumeTrue - JUnit假设

/** // 行号:51
 * Tests for the {@code org.apache.calcite.adapter.druid} package. // Druid适配器包的测试类
 * // 行号:53
 * <p>Druid must be up and running with foodmart and wikipedia datasets loaded. Follow the // Druid必须正在运行并已加载foodmart和wikipedia数据集
 * instructions on <a href="https://github.com/zabetak/calcite-druid-dataset">calcite-druid-dataset
 * </a> to setup Druid before launching these tests. // 在运行这些测试之前设置Druid
 * // 行号:57
 * <p>Features not yet implemented: // 尚未实现的功能：
 * <ul> // 行号:59
 *   <li>push LIMIT into "select" query</li> // 将LIMIT下推到select查询
 *   <li>push SORT and/or LIMIT into "groupBy" query</li> // 将SORT和/或LIMIT下推到groupBy查询
 *   <li>push HAVING into "groupBy" query</li> // 将HAVING下推到groupBy查询
 * </ul> // 行号:63
 * // 行号:64
 * <p>These tests use TIMESTAMP WITH LOCAL TIME ZONE type for the // 这些测试对Druid时间戳列使用TIMESTAMP WITH LOCAL TIME ZONE类型
 * Druid timestamp column, instead of TIMESTAMP type as // 而不是像DruidAdapter2IT那样使用TIMESTAMP类型
 * {@link DruidAdapter2IT}. // 行号:67
 */
public class DruidAdapterIT { // DruidAdapterIT类 - Druid适配器集成测试类
  /** URL of the "druid-foodmart" model. */
  public static final URL FOODMART = // 常量：foodmart模型配置文件URL
      DruidAdapterIT.class.getResource("/druid-foodmart-model.json"); // 从类路径加载foodmart模型JSON配置

  /** URL of the "druid-wiki" model // wiki模型的URL
   * and the "wikipedia" data set. */
  public static final URL WIKI = // 常量：wiki模型配置文件URL
      DruidAdapterIT.class.getResource("/druid-wiki-model.json"); // 从类路径加载wiki模型JSON配置

  /** URL of the "druid-wiki-no-columns" model // wiki无列模型的URL
   * and the "wikipedia" data set. */
  public static final URL WIKI_AUTO = // 常量：wiki自动列模型配置文件URL
      DruidAdapterIT.class.getResource("/druid-wiki-no-columns-model.json"); // 从类路径加载wiki无列模型JSON配置

  /** URL of the "druid-wiki-no-tables" model // wiki无表模型的URL
   * and the "wikipedia" data set. */
  public static final URL WIKI_AUTO2 = // 常量：wiki自动表模型配置文件URL
      DruidAdapterIT.class.getResource("/druid-wiki-no-tables-model.json"); // 从类路径加载wiki无表模型JSON配置

  private static final String VARCHAR_TYPE = // 私有常量：VARCHAR类型名称
      "VARCHAR"; // 字符串类型

  private static final String FOODMART_TABLE = "\"foodmart\""; // 私有常量：foodmart表名

  /** Whether to run this test. */
  private static boolean enabled() { // 检查是否启用Druid测试的方法
    return CalciteSystemProperty.TEST_DRUID.value(); // 返回系统属性calcite.test.druid的值
  } // 行号:97

  @BeforeAll // JUnit 5注解 - 在所有测试方法执行前运行一次
  public static void assumeDruidTestsEnabled() { // 假设Druid测试已启用，否则跳过测试的方法
    assumeTrue(enabled(), "Druid tests disabled. Add -Dcalcite.test.druid to enable it"); // 如果未启用Druid测试，则跳过并提示用户
  } // 行号:102

  /** Creates a query against FOODMART with approximate parameters. */
  private CalciteAssert.AssertQuery foodmartApprox(String sql) { // 创建foodmart近似查询的方法
    return approxQuery(FOODMART, sql); // 调用近似查询通用方法
  } // 行号:107

  /** Creates a query against WIKI with approximate parameters. */
  private CalciteAssert.AssertQuery wikiApprox(String sql) { // 创建wiki近似查询的方法
    return approxQuery(WIKI, sql); // 调用近似查询通用方法
  } // 行号:112

  private CalciteAssert.AssertQuery approxQuery(URL url, String sql) { // 创建近似查询的通用方法
    return CalciteAssert.that() // 返回断言构建器
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(url) // 设置模型URL
        .with(CalciteConnectionProperty.APPROXIMATE_DISTINCT_COUNT, true) // 启用近似去重计数
        .with(CalciteConnectionProperty.APPROXIMATE_TOP_N, true) // 启用近似Top N查询
        .with(CalciteConnectionProperty.APPROXIMATE_DECIMAL, true) // 启用近似小数运算
        .query(sql); // 设置SQL查询
  } // 行号:122

  /** Creates a fixture. */
  public static CalciteAssert.AssertThat fixture() { // 创建Calcite断言构建器的方法
    return CalciteAssert.that() // 返回断言构建器
        .enable(enabled()); // 根据enabled()方法启用或禁用
  } // 行号:128

  /** Creates a query against a data set given by a map. */
  private CalciteAssert.AssertQuery sql(String sql, URL url) { // 执行SQL查询的通用方法（带URL）
    return fixture() // 获取测试夹具
        .withModel(url) // 设置模型URL
        .query(sql); // 设置SQL查询
  } // 行号:135

  /** Creates a query against the {@link #FOODMART} data set. */
  private CalciteAssert.AssertQuery sql(String sql) { // 执行SQL查询的方法（使用默认FOODMART）
    return fixture() // 获取测试夹具
        .withModel(FOODMART) // 设置FOODMART模型
        .query(sql); // 设置SQL查询
  } // 行号:142

  /** Tests a query against the {@link #WIKI} data set. // 行号:144
   * // 行号:145
   * <p>Most of the others in this suite are against {@link #FOODMART}, // 行号:146
   * but our examples in "druid-adapter.md" use wikipedia. */
  @Test void testSelectDistinctWiki() { // 行号:148
    final String explain = "PLAN=" // 行号:149
        + "EnumerableInterpreter\n" // 行号:150
        + "  DruidQuery(table=[[wiki, wiki]], " // 行号:151
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:152
        + "filter=[=($13, 'Jeremy Corbyn')], projects=[[$5]], groups=[{0}], aggs=[[]])\n"; // 行号:153
    checkSelectDistinctWiki(WIKI) // 行号:154
        .explainContains(explain); // 行号:155
  } // 行号:156

  @Test void testSelectDistinctWikiNoColumns() { // 行号:158
    final String explain = "PLAN=" // 行号:159
        + "EnumerableInterpreter\n" // 行号:160
        + "  DruidQuery(table=[[wiki, wiki]], " // 行号:161
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:162
        + "filter=[=($16, 'Jeremy Corbyn')], projects=[[$6]], groups=[{0}], aggs=[[]])\n"; // 行号:163
    checkSelectDistinctWiki(WIKI_AUTO) // 行号:164
        .explainContains(explain); // 行号:165
  } // 行号:166

  @Test void testSelectDistinctWikiNoTables() { // 行号:168
    // Compared to testSelectDistinctWiki, table name is different (because it
    // is the raw dataSource name from Druid) and the field offsets are
    // different. This is expected.
    // Interval is different, as default is taken.
    final String sql = "select distinct \"countryName\"\n" // 行号:173
        + "from \"wikipedia\"\n" // 行号:174
        + "where \"page\" = 'Jeremy Corbyn'"; // 行号:175
    final String explain = "PLAN=" // 行号:176
        + "EnumerableInterpreter\n" // 行号:177
        + "  DruidQuery(table=[[wiki, wikipedia]], " // 行号:178
        + "intervals=[[1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z]], " // 行号:179
        + "filter=[=($16, 'Jeremy Corbyn')], projects=[[$6]], groups=[{0}], aggs=[[]])\n"; // 行号:180
    final String druidQuery = "{'queryType':'groupBy'," // 行号:181
        + "'dataSource':'wikipedia','granularity':'all'," // 行号:182
        + "'dimensions':[{'type':'default','dimension':'countryName','outputName':'countryName'," // 行号:183
        + "'outputType':'STRING'}],'limitSpec':{'type':'default'}," // 行号:184
        + "'filter':{'type':'selector','dimension':'page','value':'Jeremy Corbyn'}," // 行号:185
        + "'aggregations':[]," // 行号:186
        + "'intervals':['1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z']}"; // 行号:187
    sql(sql, WIKI_AUTO2) // 行号:188
        .returnsUnordered("countryName=United Kingdom", // 行号:189
            "countryName=null") // 行号:190
        .explainContains(explain) // 行号:191
        .queryContains(new DruidChecker(druidQuery)); // 行号:192
    // Because no tables are declared, foodmart is automatically present.
    sql("select count(*) as c from \"foodmart\"", WIKI_AUTO2) // 行号:194
        .returnsUnordered("C=86829"); // 行号:195
  } // 行号:196

  @Test void testSelectTimestampColumnNoTables1() { // 行号:198
    // Since columns are not explicitly declared, we use the default time
    // column in the query.
    final String sql = "select sum(\"added\")\n" // 行号:201
        + "from \"wikipedia\"\n" // 行号:202
        + "group by floor(\"__time\" to DAY)"; // 行号:203
    final String explain = "PLAN=" // 行号:204
        + "EnumerableCalc(expr#0..1=[{inputs}], EXPR$0=[$t1])\n" // 行号:205
        + "  EnumerableInterpreter\n" // 行号:206
        + "    DruidQuery(table=[[wiki, wikipedia]], intervals=[[1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z]], projects=[[FLOOR($0, FLAG(DAY)), $1]], groups=[{0}], aggs=[[SUM($1)]])\n"; // 行号:207
    final String druidQuery = "{'queryType':'timeseries'," // 行号:208
        + "'dataSource':'wikipedia','descending':false,'granularity':{'type':'period','period':'P1D','timeZone':'UTC'}," // 行号:209
        + "'aggregations':[{'type':'longSum','name':'EXPR$0','fieldName':'added'}]," // 行号:210
        + "'intervals':['1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z']," // 行号:211
        + "'context':{'skipEmptyBuckets':true}}"; // 行号:212
    sql(sql, WIKI_AUTO2) // 行号:213
        .explainContains(explain) // 行号:214
        .queryContains(new DruidChecker(druidQuery)); // 行号:215
  } // 行号:216

  @Test void testSelectTimestampColumnNoTables2() { // 行号:218
    // Since columns are not explicitly declared, we use the default time
    // column in the query.
    final String sql = "select cast(\"__time\" as timestamp) as \"__time\"\n" // 行号:221
        + "from \"wikipedia\"\n" // 行号:222
        + "limit 1\n"; // 行号:223
    final String explain = "PLAN=" // 行号:224
        + "EnumerableInterpreter\n" // 行号:225
        + "  DruidQuery(table=[[wiki, wikipedia]], intervals=[[1900-01-01T00:00:00.000Z/" // 行号:226
        + "3000-01-01T00:00:00.000Z]], projects=[[CAST($0):TIMESTAMP(0) NOT NULL]], fetch=[1])"; // 行号:227

    sql(sql, WIKI_AUTO2) // 行号:229
        .returnsUnordered("__time=2015-09-12 00:46:58") // 行号:230
        .explainContains(explain); // 行号:231
  } // 行号:232

  @Test void testSelectTimestampColumnNoTables3() { // 行号:234
    // Since columns are not explicitly declared, we use the default time
    // column in the query.
    final String sql = "select" // 行号:237
        + " cast(floor(\"__time\" to DAY) as timestamp) as \"day\", sum(\"added\")\n" // 行号:238
        + "from \"wikipedia\"\n" // 行号:239
        + "group by floor(\"__time\" to DAY)"; // 行号:240
    final String explain = // 行号:241
        "PLAN=EnumerableInterpreter\n" // 行号:242
            + "  DruidQuery(table=[[wiki, wikipedia]], intervals=[[1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z]], projects=[[FLOOR($0, FLAG(DAY)), $1]], groups=[{0}], aggs=[[SUM($1)]], post_projects=[[CAST($0):TIMESTAMP(0) NOT NULL, $1]])"; // 行号:243
    final String druidQuery = "{'queryType':'timeseries'," // 行号:244
        + "'dataSource':'wikipedia','descending':false,'granularity':{'type':'period','period':'P1D','timeZone':'UTC'}," // 行号:245
        + "'aggregations':[{'type':'longSum','name':'EXPR$1','fieldName':'added'}]," // 行号:246
        + "'intervals':['1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z']," // 行号:247
        + "'context':{'skipEmptyBuckets':true}}"; // 行号:248
    sql(sql, WIKI_AUTO2) // 行号:249
        .returnsUnordered("day=2015-09-12 00:00:00; EXPR$1=9385573") // 行号:250
        .explainContains(explain) // 行号:251
        .queryContains(new DruidChecker(druidQuery)); // 行号:252
  } // 行号:253

  @Test void testSelectTimestampColumnNoTables4() { // 行号:255
    // Since columns are not explicitly declared, we use the default time
    // column in the query.
    final String sql = "select sum(\"added\") as \"s\", \"page\", " // 行号:258
        + "cast(floor(\"__time\" to DAY) as timestamp) as \"day\"\n" // 行号:259
        + "from \"wikipedia\"\n" // 行号:260
        + "group by \"page\", floor(\"__time\" to DAY)\n" // 行号:261
        + "order by \"s\" desc"; // 行号:262
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:263
        + "  DruidQuery(table=[[wiki, wikipedia]], intervals=[[1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z]], projects=[[$16, FLOOR($0, FLAG(DAY)), $1]], groups=[{0, 1}], aggs=[[SUM($2)]], post_projects=[[$2, $0, CAST($1):TIMESTAMP(0) NOT NULL]], sort0=[0], dir0=[DESC])"; // 行号:264
    sql(sql, WIKI_AUTO2) // 行号:265
        .limit(1) // 行号:266
        .returnsUnordered("s=199818; page=User:QuackGuru/Electronic cigarettes 1; " // 行号:267
            + "day=2015-09-12 00:00:00") // 行号:268
        .explainContains(explain) // 行号:269
        .queryContains( // 行号:270
            new DruidChecker("'queryType':'groupBy'", "'limitSpec':{'type':'default'," // 行号:271
                + "'columns':[{'dimension':'s','direction':'descending','dimensionOrder':'numeric'}]}")); // 行号:272
  } // 行号:273

  @Test void testSkipEmptyBuckets() { // 行号:275
    final String sql = "select" // 行号:276
        + " cast(floor(\"__time\" to SECOND) as timestamp) as \"second\", sum(\"added\")\n" // 行号:277
        + "from \"wikipedia\"\n" // 行号:278
        + "where \"page\" = 'Jeremy Corbyn'\n" // 行号:279
        + "group by floor(\"__time\" to SECOND)"; // 行号:280
    final String druidQuery = "{'queryType':'timeseries'," // 行号:281
        + "'dataSource':'wikipedia','descending':false,'granularity':{'type':'period','period':'PT1S','timeZone':'UTC'}," // 行号:282
        + "'filter':{'type':'selector','dimension':'page','value':'Jeremy Corbyn'}," // 行号:283
        + "'aggregations':[{'type':'longSum','name':'EXPR$1','fieldName':'added'}]," // 行号:284
        + "'intervals':['1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z']," // 行号:285
        + "'context':{'skipEmptyBuckets':true}}"; // 行号:286
    sql(sql, WIKI_AUTO2) // 行号:287
        .limit(1) // 行号:288
        // Result without 'skipEmptyBuckets':true -> "second=2015-09-12 00:46:58; EXPR$1=0"
        .returnsUnordered("second=2015-09-12 01:20:19; EXPR$1=1075") // 行号:290
        .queryContains(new DruidChecker(druidQuery)); // 行号:291
  } // 行号:292

  private CalciteAssert.AssertQuery checkSelectDistinctWiki(URL url) { // 行号:294
    final String sql = "select distinct \"countryName\"\n" // 行号:295
        + "from \"wiki\"\n" // 行号:296
        + "where \"page\" = 'Jeremy Corbyn'"; // 行号:297
    final String druidQuery = "{'queryType':'groupBy'," // 行号:298
        + "'dataSource':'wikipedia','granularity':'all'," // 行号:299
        + "'dimensions':[{'type':'default','dimension':'countryName','outputName':'countryName'," // 行号:300
        + "'outputType':'STRING'}],'limitSpec':{'type':'default'}," // 行号:301
        + "'filter':{'type':'selector','dimension':'page','value':'Jeremy Corbyn'}," // 行号:302
        + "'aggregations':[]," // 行号:303
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:304
    return sql(sql, url) // 行号:305
        .returnsUnordered("countryName=United Kingdom", // 行号:306
            "countryName=null") // 行号:307
        .queryContains(new DruidChecker(druidQuery)); // 行号:308
  } // 行号:309

  /** Test case for // 行号:311
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1617">[CALCITE-1617]
   * Druid adapter: Send timestamp literals to Druid as local time, not // 行号:313
   * UTC</a>. */
  @Test void testFilterTime() { // 行号:315
    final String sql = "select cast(\"__time\" as timestamp) as \"__time\"\n" // 行号:316
        + "from \"wikipedia\"\n" // 行号:317
        + "where \"__time\" < '2015-10-12 00:00:00 UTC'"; // 行号:318
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:319
        + "  DruidQuery(table=[[wiki, wikipedia]]," // 行号:320
        + " intervals=[[1900-01-01T00:00:00.000Z/2015-10-12T00:00:00.000Z]], " // 行号:321
        + "projects=[[CAST($0):TIMESTAMP(0) NOT NULL]])"; // 行号:322
    final String druidQuery = "{'queryType':'scan'," // 行号:323
        + "'dataSource':'wikipedia'," // 行号:324
        + "'intervals':['1900-01-01T00:00:00.000Z/2015-10-12T00:00:00.000Z']," // 行号:325
        + "'virtualColumns':[{'type':'expression','name':'vc','expression':"; // 行号:326
    sql(sql, WIKI_AUTO2) // 行号:327
        .limit(2) // 行号:328
        .returnsUnordered("__time=2015-09-12 00:46:58", // 行号:329
            "__time=2015-09-12 00:47:00") // 行号:330
        .explainContains(explain) // 行号:331
        .queryContains(new DruidChecker(druidQuery)); // 行号:332
  } // 行号:333

  @Test void testFilterTimeDistinct() { // 行号:335
    final String sql = "select CAST(\"c1\" AS timestamp) as \"time\" from\n" // 行号:336
        + "(select distinct \"__time\" as \"c1\"\n" // 行号:337
        + "from \"wikipedia\"\n" // 行号:338
        + "where \"__time\" < '2015-10-12 00:00:00 UTC')"; // 行号:339
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:340
        + "  DruidQuery(table=[[wiki, wikipedia]], intervals=[[1900-01-01T00:00:00.000Z/" // 行号:341
        + "3000-01-01T00:00:00.000Z]], projects=[[$0]], groups=[{0}], aggs=[[]], " // 行号:342
        + "filter=[<($0, 2015-10-12 00:00:00)], projects=[[CAST($0):TIMESTAMP(0) NOT NULL]])\n"; // 行号:343
    final String subDruidQuery = "{'queryType':'groupBy','dataSource':'wikipedia'," // 行号:344
        + "'granularity':'all','dimensions':[{'type':'extraction'," // 行号:345
        + "'dimension':'__time','outputName':'extract'," // 行号:346
        + "'extractionFn':{'type':'timeFormat'"; // 行号:347
    sql(sql, WIKI_AUTO2) // 行号:348
        .limit(2) // 行号:349
        .returnsUnordered("time=2015-09-12 00:46:58", // 行号:350
            "time=2015-09-12 00:47:00") // 行号:351
        .explainContains(explain) // 行号:352
        .queryContains(new DruidChecker(subDruidQuery)); // 行号:353
  } // 行号:354

  @Test void testMetadataColumns() { // 行号:356
    sql("values 1") // 行号:357
        .withConnection(c -> { // 行号:358
          try { // 行号:359
            final DatabaseMetaData metaData = c.getMetaData(); // 行号:360
            final ResultSet r = // 行号:361
                metaData.getColumns(null, null, "foodmart", null); // 行号:362
            Multimap<String, Boolean> map = ArrayListMultimap.create(); // 行号:363
            while (r.next()) { // 行号:364
              map.put(r.getString("TYPE_NAME"), true); // 行号:365
            } // 行号:366
            if (CalciteSystemProperty.DEBUG.value()) { // 行号:367
              System.out.println(map); // 行号:368
            } // 行号:369
            // 1 timestamp, 2 float measure, 1 int measure, 88 dimensions
            assertThat(map.keySet(), hasSize(4)); // 行号:371
            assertThat(map.values(), hasSize(92)); // 行号:372
            assertThat(map.get("TIMESTAMP_WITH_LOCAL_TIME_ZONE(0) NOT NULL"), // 行号:373
                hasSize(1)); // 行号:374
            assertThat(map.get("DOUBLE"), hasSize(2)); // 行号:375
            assertThat(map.get("BIGINT"), hasSize(1)); // 行号:376
            assertThat(map.get(VARCHAR_TYPE), hasSize(88)); // 行号:377
          } catch (SQLException e) { // 行号:378
            throw TestUtil.rethrow(e); // 行号:379
          } // 行号:380
        }); // 行号:381
  } // 行号:382

  @Test void testSelectDistinct() { // 行号:384
    final String explain = "PLAN=" // 行号:385
        + "EnumerableInterpreter\n" // 行号:386
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$30]], groups=[{0}], aggs=[[]])"; // 行号:387
    final String sql = "select distinct \"state_province\" from \"foodmart\""; // 行号:388
    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'," // 行号:389
        + "'dimensions':[{'type':'default','dimension':'state_province','outputName':'state_province'" // 行号:390
        + ",'outputType':'STRING'}],'limitSpec':{'type':'default'}," // 行号:391
        + "'aggregations':[]," // 行号:392
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:393
    sql(sql) // 行号:394
        .returnsUnordered("state_province=CA", // 行号:395
            "state_province=OR", // 行号:396
            "state_province=WA") // 行号:397
        .explainContains(explain) // 行号:398
        .queryContains(new DruidChecker(druidQuery)); // 行号:399
  } // 行号:400

  @Test void testSelectGroupBySum() { // 行号:402
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:403
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:404
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:405
        + "projects=[[$30, CAST($89):INTEGER]], groups=[{0}], aggs=[[SUM($1)]])"; // 行号:406
    final String sql = "select \"state_province\", sum(cast(\"unit_sales\" as integer)) as u\n" // 行号:407
        + "from \"foodmart\"\n" // 行号:408
        + "group by \"state_province\""; // 行号:409
    sql(sql) // 行号:410
        .returnsUnordered("state_province=CA; U=74748", // 行号:411
            "state_province=OR; U=67659", // 行号:412
            "state_province=WA; U=124366") // 行号:413
        .explainContains(explain); // 行号:414
  } // 行号:415

  @Test void testGroupbyMetric() { // 行号:417
    final String sql = "select  \"store_sales\" ,\"product_id\" from \"foodmart\" " // 行号:418
        + "where \"product_id\" = 1020" + "group by \"store_sales\" ,\"product_id\" "; // 行号:419
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:420
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:421
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:422
        + "filter=[=(CAST($1):INTEGER, 1020)]," // 行号:423
        + " projects=[[$90, $1]], groups=[{0, 1}], aggs=[[]])"; // 行号:424
    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'," // 行号:425
        + "'dimensions':[{'type':'default','dimension':'store_sales',\"outputName\":\"store_sales\"," // 行号:426
        + "'outputType':'DOUBLE'},{'type':'default','dimension':'product_id','outputName':" // 行号:427
        + "'product_id','outputType':'STRING'}],'limitSpec':{'type':'default'}," // 行号:428
        + "'filter':{'type':'bound','dimension':'product_id','lower':'1020','lowerStrict':false," // 行号:429
        + "'upper':'1020','upperStrict':false,'ordering':'numeric'},'aggregations':[]," // 行号:430
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:431
    sql(sql) // 行号:432
        .explainContains(plan) // 行号:433
        .queryContains(new DruidChecker(druidQuery)) // 行号:434
        .returnsUnordered("store_sales=0.51; product_id=1020", // 行号:435
            "store_sales=1.02; product_id=1020", // 行号:436
            "store_sales=1.53; product_id=1020", // 行号:437
            "store_sales=2.04; product_id=1020", // 行号:438
            "store_sales=2.55; product_id=1020"); // 行号:439
  } // 行号:440

  @Test void testPushSimpleGroupBy() { // 行号:442
    final String sql = "select \"product_id\" from \"foodmart\" where " // 行号:443
        + "\"product_id\" = 1020 group by \"product_id\""; // 行号:444
    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:445
        + "'granularity':'all','dimensions':[{'type':'default'," // 行号:446
        + "'dimension':'product_id','outputName':'product_id','outputType':'STRING'}]," // 行号:447
        + "'limitSpec':{'type':'default'},'filter':{'type':'bound','dimension':'product_id'," // 行号:448
        + "'lower':'1020','lowerStrict':false,'upper':'1020','upperStrict':false," // 行号:449
        + "'ordering':'numeric'},'aggregations':[]," // 行号:450
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:451
    sql(sql).returnsUnordered("product_id=1020").queryContains(new DruidChecker(druidQuery)); // 行号:452
  } // 行号:453

  @Test void testComplexPushGroupBy() { // 行号:455
    final String innerQuery = "select \"product_id\" as \"id\" from \"foodmart\" where " // 行号:456
        + "\"product_id\" = 1020"; // 行号:457
    final String sql = "select \"id\" from (" + innerQuery + ") group by \"id\""; // 行号:458
    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:459
        + "'granularity':'all'," // 行号:460
        + "'dimensions':[{'type':'default','dimension':'product_id','outputName':'product_id'," // 行号:461
        + "'outputType':'STRING'}],'limitSpec':{'type':'default'}," // 行号:462
        + "'filter':{'type':'bound','dimension':'product_id','lower':'1020','lowerStrict':false," // 行号:463
        + "'upper':'1020','upperStrict':false,'ordering':'numeric'},'aggregations':[]," // 行号:464
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:465
    sql(sql) // 行号:466
        .returnsUnordered("id=1020") // 行号:467
        .queryContains(new DruidChecker(druidQuery)); // 行号:468
  } // 行号:469

  /** Test case for // 行号:471
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1281">[CALCITE-1281]
   * Druid adapter wrongly returns all numeric values as int or float</a>. */
  @Test void testSelectCount() { // 行号:474
    final String sql = "select count(*) as c from \"foodmart\""; // 行号:475
    sql(sql) // 行号:476
        .returns(input -> { // 行号:477
          try { // 行号:478
            assertThat(input.next(), is(true)); // 行号:479
            assertThat(input.getInt(1), is(86829)); // 行号:480
            assertThat(input.getLong(1), is(86829L)); // 行号:481
            assertThat(input.getString(1), is("86829")); // 行号:482
            assertThat(input.wasNull(), is(false)); // 行号:483
            assertThat(input.next(), is(false)); // 行号:484
          } catch (SQLException e) { // 行号:485
            throw TestUtil.rethrow(e); // 行号:486
          } // 行号:487
        }); // 行号:488
  } // 行号:489

  @Test void testSort() { // 行号:491
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:492
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:493
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$39, $30]], " // 行号:494
        + "groups=[{0, 1}], aggs=[[]], sort0=[1], sort1=[0], dir0=[ASC], dir1=[DESC])"; // 行号:495
    final String sql = "select distinct \"gender\", \"state_province\"\n" // 行号:496
        + "from \"foodmart\" order by 2, 1 desc"; // 行号:497
    sql(sql) // 行号:498
        .returnsOrdered("gender=M; state_province=CA", // 行号:499
            "gender=F; state_province=CA", // 行号:500
            "gender=M; state_province=OR", // 行号:501
            "gender=F; state_province=OR", // 行号:502
            "gender=M; state_province=WA", // 行号:503
            "gender=F; state_province=WA") // 行号:504
        .queryContains( // 行号:505
            new DruidChecker("{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'," // 行号:506
                + "'dimensions':[{'type':'default','dimension':'gender','outputName':'gender'," // 行号:507
                + "'outputType':'STRING'},{'type':'default','dimension':'state_province'," // 行号:508
                + "'outputName':'state_province','outputType':'STRING'}],'limitSpec':" // 行号:509
                + "{'type':'default','columns':[{'dimension':'state_province','direction':'ascending'" // 行号:510
                + ",'dimensionOrder':'lexicographic'},{'dimension':'gender','direction':'descending'," // 行号:511
                + "'dimensionOrder':'lexicographic'}]},'aggregations':[]," // 行号:512
                + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}")) // 行号:513
        .explainContains(explain); // 行号:514
  } // 行号:515

  @Test void testSortLimit() { // 行号:517
    final String explain = "PLAN=EnumerableLimit(offset=[2], fetch=[3])\n" // 行号:518
        + "  EnumerableInterpreter\n" // 行号:519
        + "    DruidQuery(table=[[foodmart, foodmart]], " // 行号:520
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$39, $30]], " // 行号:521
        + "groups=[{0, 1}], aggs=[[]], sort0=[1], sort1=[0], dir0=[ASC], dir1=[DESC])"; // 行号:522
    final String sql = "select distinct \"gender\", \"state_province\"\n" // 行号:523
        + "from \"foodmart\"\n" // 行号:524
        + "order by 2, 1 desc offset 2 rows fetch next 3 rows only"; // 行号:525
    sql(sql) // 行号:526
        .returnsOrdered("gender=M; state_province=OR", // 行号:527
            "gender=F; state_province=OR", // 行号:528
            "gender=M; state_province=WA") // 行号:529
        .explainContains(explain); // 行号:530
  } // 行号:531

  @Test void testOffsetLimit() { // 行号:533
    // We do not yet push LIMIT into a Druid "select" query as a "threshold".
    // It is not possible to push OFFSET into Druid "select" query.
    final String sql = "select \"state_province\", \"product_name\"\n" // 行号:536
        + "from \"foodmart\"\n" // 行号:537
        + "offset 2 fetch next 3 rows only"; // 行号:538
    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart'," // 行号:539
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:540
        + "'columns':['state_province','product_name']," // 行号:541
        + "'resultFormat':'compactedList'}"; // 行号:542
    sql(sql) // 行号:543
        .runs() // 行号:544
        .queryContains(new DruidChecker(druidQuery)); // 行号:545
  } // 行号:546

  @Test void testLimit() { // 行号:548
    final String sql = "select \"gender\", \"state_province\"\n" // 行号:549
        + "from \"foodmart\" fetch next 3 rows only"; // 行号:550
    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart'," // 行号:551
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:552
        + "'columns':['gender','state_province']," // 行号:553
        + "'resultFormat':'compactedList','limit':3"; // 行号:554
    sql(sql) // 行号:555
        .runs() // 行号:556
        .queryContains(new DruidChecker(druidQuery)); // 行号:557
  } // 行号:558

  /** Test case for // 行号:560
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2804">[CALCITE-2804]
   * Cast does not work in Druid when casting to timestamp</a>. */
  @Test void testCastToTimestamp() { // 行号:563
    final String sql = "select cast(\"timestamp\" as timestamp) from \"foodmart\""; // 行号:564
    final String druidQuery = "timestamp_format(\\\"__time\\\"," // 行号:565
        + "'yyyy-MM-dd\\\\u0027T\\\\u0027HH:mm:ss.SSS\\\\u0027Z\\\\u0027'," // 行号:566
        + "'America/New_York'),'yyyy-MM-dd\\\\u0027T\\\\u0027HH:mm:ss.SSS\\\\u0027Z\\\\u0027','UTC')\""; // 行号:567

    CalciteAssert.that() // 行号:569
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "America/New_York") // 行号:572
        .query(sql) // 行号:573
        .runs() // 行号:574
        .queryContains(new DruidChecker(false, druidQuery)); // 行号:575
  } // 行号:576

  @Test void testDistinctLimit() { // 行号:578
    final String sql = "select distinct \"gender\", \"state_province\"\n" // 行号:579
        + "from \"foodmart\" fetch next 3 rows only"; // 行号:580
    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:581
        + "'granularity':'all','dimensions':[{'type':'default','dimension':'gender'," // 行号:582
        + "'outputName':'gender','outputType':'STRING'}," // 行号:583
        + "{'type':'default','dimension':'state_province','outputName':'state_province'," // 行号:584
        + "'outputType':'STRING'}],'limitSpec':{'type':'default'," // 行号:585
        + "'limit':3,'columns':[]}," // 行号:586
        + "'aggregations':[]," // 行号:587
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:588
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:589
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:590
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$39, $30]], " // 行号:591
        + "groups=[{0, 1}], aggs=[[]], fetch=[3])"; // 行号:592
    sql(sql) // 行号:593
        .runs() // 行号:594
        .explainContains(explain) // 行号:595
        .queryContains(new DruidChecker(druidQuery)) // 行号:596
        .returnsUnordered("gender=F; state_province=CA", "gender=F; state_province=OR", // 行号:597
            "gender=F; state_province=WA"); // 行号:598
  } // 行号:599

  /** Test case for // 行号:601
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1578">[CALCITE-1578]
   * Druid adapter: wrong semantics of topN query limit with granularity</a>. */
  @Test void testGroupBySortLimit() { // 行号:604
    final String sql = "select \"brand_name\", \"gender\", sum(\"unit_sales\") as s\n" // 行号:605
        + "from \"foodmart\"\n" // 行号:606
        + "group by \"brand_name\", \"gender\"\n" // 行号:607
        + "order by s desc limit 3"; // 行号:608
    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:609
        + "'granularity':'all','dimensions':[{'type':'default'," // 行号:610
        + "'dimension':'brand_name','outputName':'brand_name','outputType':'STRING'}," // 行号:611
        + "{'type':'default','dimension':'gender','outputName':'gender','outputType':'STRING'}]," // 行号:612
        + "'limitSpec':{'type':'default','limit':3,'columns':[{'dimension':'S'," // 行号:613
        + "'direction':'descending','dimensionOrder':'numeric'}]}," // 行号:614
        + "'aggregations':[{'type':'longSum','name':'S','fieldName':'unit_sales'}]," // 行号:615
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:616
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:617
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:618
        + "2992-01-10T00:00:00.000Z]], projects=[[$2, $39, $89]], groups=[{0, 1}], " // 行号:619
        + "aggs=[[SUM($2)]], sort0=[2], dir0=[DESC], fetch=[3])"; // 行号:620
    sql(sql) // 行号:621
        .runs() // 行号:622
        .returnsOrdered("brand_name=Hermanos; gender=M; S=4286", // 行号:623
            "brand_name=Hermanos; gender=F; S=4183", // 行号:624
            "brand_name=Tell Tale; gender=F; S=4033") // 行号:625
        .explainContains(explain) // 行号:626
        .queryContains(new DruidChecker(druidQuery)); // 行号:627
  } // 行号:628

  /** Test case for // 行号:630
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1587">[CALCITE-1587]
   * Druid adapter: topN returns approximate results</a>. */
  @Test void testGroupBySingleSortLimit() { // 行号:633
    checkGroupBySingleSortLimit(false); // 行号:634
  } // 行号:635

  /** As {@link #testGroupBySingleSortLimit}, but allowing approximate results // 行号:637
   * due to {@link CalciteConnectionConfig#approximateDistinctCount()}. // 行号:638
   * Therefore we send a "topN" query to Druid. */
  @Test void testGroupBySingleSortLimitApprox() { // 行号:640
    checkGroupBySingleSortLimit(true); // 行号:641
  } // 行号:642

  private void checkGroupBySingleSortLimit(boolean approx) { // 行号:644
    final String sql = "select \"brand_name\", sum(\"unit_sales\") as s\n" // 行号:645
        + "from \"foodmart\"\n" // 行号:646
        + "group by \"brand_name\"\n" // 行号:647
        + "order by s desc limit 3"; // 行号:648
    final String approxDruid = "{'queryType':'topN','dataSource':'foodmart','granularity':'all'," // 行号:649
        + "'dimension':{'type':'default','dimension':'brand_name','outputName':'brand_name','outputType':'STRING'},'metric':'S'," // 行号:650
        + "'aggregations':[{'type':'longSum','name':'S','fieldName':'unit_sales'}]," // 行号:651
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:652
        + "'threshold':3}"; // 行号:653
    final String exactDruid = "{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'," // 行号:654
        + "'dimensions':[{'type':'default','dimension':'brand_name','outputName':'brand_name'," // 行号:655
        + "'outputType':'STRING'}],'limitSpec':{'type':'default','limit':3,'columns':" // 行号:656
        + "[{'dimension':'S','direction':'descending','dimensionOrder':'numeric'}]},'aggregations':" // 行号:657
        + "[{'type':'longSum','name':'S','fieldName':'unit_sales'}]," // 行号:658
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:659
    final String druidQuery = approx ? approxDruid : exactDruid; // 行号:660
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:661
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:662
        + "2992-01-10T00:00:00.000Z]], projects=[[$2, $89]], groups=[{0}], " // 行号:663
        + "aggs=[[SUM($1)]], sort0=[1], dir0=[DESC], fetch=[3])"; // 行号:664
    CalciteAssert.that() // 行号:665
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.APPROXIMATE_TOP_N, approx) // 行号:668
        .query(sql) // 行号:669
        .runs() // 行号:670
        .returnsOrdered("brand_name=Hermanos; S=8469", // 行号:671
            "brand_name=Tell Tale; S=7877", // 行号:672
            "brand_name=Ebony; S=7438") // 行号:673
        .explainContains(explain) // 行号:674
        .queryContains(new DruidChecker(druidQuery)); // 行号:675
  } // 行号:676

  /** Test case for // 行号:678
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1578">[CALCITE-1578]
   * Druid adapter: wrong semantics of groupBy query limit with granularity</a>. // 行号:680
   * // 行号:681
   * <p>Before CALCITE-1578 was fixed, this would use a "topN" query but return // 行号:682
   * the wrong results. */
  @Test void testGroupByDaySortDescLimit() { // 行号:684
    final String sql = "select \"brand_name\"," // 行号:685
        + " cast(floor(\"timestamp\" to DAY) as timestamp) as d," // 行号:686
        + " sum(\"unit_sales\") as s\n" // 行号:687
        + "from \"foodmart\"\n" // 行号:688
        + "group by \"brand_name\", floor(\"timestamp\" to DAY)\n" // 行号:689
        + "order by s desc limit 30"; // 行号:690
    final String explain = // 行号:691
        "PLAN=EnumerableInterpreter\n" // 行号:692
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:693
            + "2992-01-10T00:00:00.000Z]], projects=[[$2, FLOOR($0, FLAG(DAY)), $89]], " // 行号:694
            + "groups=[{0, 1}], aggs=[[SUM($2)]], post_projects=[[$0, " // 行号:695
            + "CAST($1):TIMESTAMP(0) NOT NULL, $2]], sort0=[2], dir0=[DESC], fetch=[30])"; // 行号:696
    sql(sql) // 行号:697
        .runs() // 行号:698
        .returnsStartingWith("brand_name=Ebony; D=1997-07-27 00:00:00; S=135", // 行号:699
            "brand_name=Tri-State; D=1997-05-09 00:00:00; S=120", // 行号:700
            "brand_name=Hermanos; D=1997-05-09 00:00:00; S=115") // 行号:701
        .explainContains(explain) // 行号:702
        .queryContains( // 行号:703
            new DruidChecker("'queryType':'groupBy'", "'granularity':'all'", "'limitSpec" // 行号:704
                + "':{'type':'default','limit':30,'columns':[{'dimension':'S'," // 行号:705
                + "'direction':'descending','dimensionOrder':'numeric'}]}")); // 行号:706
  } // 行号:707

  /** Test case for // 行号:709
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1579">[CALCITE-1579]
   * Druid adapter: wrong semantics of groupBy query limit with // 行号:711
   * granularity</a>. // 行号:712
   * // 行号:713
   * <p>Before CALCITE-1579 was fixed, this would use a "groupBy" query but // 行号:714
   * wrongly try to use a {@code limitSpec} to sort and filter. (A "topN" query // 行号:715
   * was not possible because the sort was {@code ASC}.) */
  @Test void testGroupByDaySortLimit() { // 行号:717
    final String sql = "select \"brand_name\"," // 行号:718
        + " cast(floor(\"timestamp\" to DAY) as timestamp) as d," // 行号:719
        + " sum(\"unit_sales\") as s\n" // 行号:720
        + "from \"foodmart\"\n" // 行号:721
        + "group by \"brand_name\", floor(\"timestamp\" to DAY)\n" // 行号:722
        + "order by s desc limit 30"; // 行号:723
    final String druidQueryPart1 = "{'queryType':'groupBy','dataSource':'foodmart'"; // 行号:724
    final String druidQueryPart2 = "'limitSpec':{'type':'default','limit':30," // 行号:725
        + "'columns':[{'dimension':'S','direction':'descending'," // 行号:726
        + "'dimensionOrder':'numeric'}]},'aggregations':[{'type':'longSum'," // 行号:727
        + "'name':'S','fieldName':'unit_sales'}]," // 行号:728
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:729
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:730
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:731
        + "2992-01-10T00:00:00.000Z]], projects=[[$2, FLOOR($0, FLAG(DAY)), $89]], groups=[{0, 1}], " // 行号:732
        + "aggs=[[SUM($2)]], post_projects=[[$0, CAST($1):TIMESTAMP(0) NOT NULL, $2]], " // 行号:733
        + "sort0=[2], dir0=[DESC], fetch=[30])"; // 行号:734
    sql(sql) // 行号:735
        .runs() // 行号:736
        .returnsStartingWith("brand_name=Ebony; D=1997-07-27 00:00:00; S=135", // 行号:737
            "brand_name=Tri-State; D=1997-05-09 00:00:00; S=120", // 行号:738
            "brand_name=Hermanos; D=1997-05-09 00:00:00; S=115") // 行号:739
        .explainContains(explain) // 行号:740
        .queryContains(new DruidChecker(druidQueryPart1, druidQueryPart2)); // 行号:741
  } // 行号:742

  /** Test case for // 行号:744
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1580">[CALCITE-1580]
   * Druid adapter: Wrong semantics for ordering within groupBy queries</a>. */
  @Test void testGroupByDaySortDimension() { // 行号:747
    final String sql = "select" // 行号:748
        + " \"brand_name\", cast(floor(\"timestamp\" to DAY) as timestamp) as d," // 行号:749
        + " sum(\"unit_sales\") as s\n" // 行号:750
        + "from \"foodmart\"\n" // 行号:751
        + "group by \"brand_name\", floor(\"timestamp\" to DAY)\n" // 行号:752
        + "order by \"brand_name\""; // 行号:753
    final String subDruidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:754
        + "'granularity':'all','dimensions':[{'type':'default'," // 行号:755
        + "'dimension':'brand_name','outputName':'brand_name','outputType':'STRING'}," // 行号:756
        + "{'type':'extraction','dimension':'__time'," // 行号:757
        + "'outputName':'floor_day','extractionFn':{'type':'timeFormat'"; // 行号:758
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:759
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:760
        + "2992-01-10T00:00:00.000Z]], projects=[[$2, FLOOR($0, FLAG(DAY)), $89]], groups=[{0, 1}]," // 行号:761
        + " aggs=[[SUM($2)]], post_projects=[[$0, CAST($1):TIMESTAMP(0) NOT NULL, $2]], " // 行号:762
        + "sort0=[0], dir0=[ASC])"; // 行号:763
    sql(sql) // 行号:764
        .runs() // 行号:765
        .returnsStartingWith("brand_name=ADJ; D=1997-01-11 00:00:00; S=2", // 行号:766
            "brand_name=ADJ; D=1997-01-12 00:00:00; S=3", // 行号:767
            "brand_name=ADJ; D=1997-01-17 00:00:00; S=3") // 行号:768
        .explainContains(explain) // 行号:769
        .queryContains(new DruidChecker(subDruidQuery)); // 行号:770
  } // 行号:771

  /** Tests a query that contains no GROUP BY and is therefore executed as a // 行号:773
   * Druid "select" query. */
  @Test void testFilterSortDesc() { // 行号:775
    final String sql = "select \"product_name\" from \"foodmart\"\n" // 行号:776
        + "where \"product_id\" BETWEEN '1500' AND '1502'\n" // 行号:777
        + "order by \"state_province\" desc, \"product_id\""; // 行号:778
    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart'," // 行号:779
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:780
        + "'filter':{'type':'and','fields':[" // 行号:781
        + "{'type':'bound','dimension':'product_id','lower':'1500','lowerStrict':false,'ordering':'lexicographic'}," // 行号:782
        + "{'type':'bound','dimension':'product_id','upper':'1502','upperStrict':false,'ordering':'lexicographic'}]}," // 行号:783
        + "'columns':['product_name','state_province','product_id']," // 行号:784
        + "'resultFormat':'compactedList'"; // 行号:785
    sql(sql) // 行号:786
        .limit(4) // 行号:787
        .returns(resultSet -> { // 行号:788
          try { // 行号:789
            for (int i = 0; i < 4; i++) { // 行号:790
              assertTrue(resultSet.next()); // 行号:791
              assertThat(resultSet.getString("product_name"), // 行号:792
                  is("Fort West Dried Apricots")); // 行号:793
            } // 行号:794
            assertFalse(resultSet.next()); // 行号:795
          } catch (SQLException e) { // 行号:796
            throw TestUtil.rethrow(e); // 行号:797
          } // 行号:798
        }) // 行号:799
        .queryContains(new DruidChecker(druidQuery)); // 行号:800
  } // 行号:801

  /** As {@link #testFilterSortDesc()} but the bounds are numeric. */
  @Test void testFilterSortDescNumeric() { // 行号:804
    final String sql = "select \"product_name\" from \"foodmart\"\n" // 行号:805
        + "where \"product_id\" BETWEEN 1500 AND 1502\n" // 行号:806
        + "order by \"state_province\" desc, \"product_id\""; // 行号:807
    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart'," // 行号:808
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:809
        + "'filter':{'type':'and','fields':[" // 行号:810
        + "{'type':'bound','dimension':'product_id','lower':'1500','lowerStrict':false,'ordering':'numeric'}," // 行号:811
        + "{'type':'bound','dimension':'product_id','upper':'1502','upperStrict':false,'ordering':'numeric'}]}," // 行号:812
        + "'columns':['product_name','state_province','product_id']," // 行号:813
        + "'resultFormat':'compactedList'"; // 行号:814
    sql(sql) // 行号:815
        .limit(4) // 行号:816
        .returns(resultSet -> { // 行号:817
          try { // 行号:818
            for (int i = 0; i < 4; i++) { // 行号:819
              assertTrue(resultSet.next()); // 行号:820
              assertThat(resultSet.getString("product_name"), // 行号:821
                  is("Fort West Dried Apricots")); // 行号:822
            } // 行号:823
            assertFalse(resultSet.next()); // 行号:824
          } catch (SQLException e) { // 行号:825
            throw TestUtil.rethrow(e); // 行号:826
          } // 行号:827
        }) // 行号:828
        .queryContains(new DruidChecker(druidQuery)); // 行号:829
  } // 行号:830

  /** Tests a query whose filter removes all rows. */
  @Test void testFilterOutEverything() { // 行号:833
    final String sql = "select \"product_name\" from \"foodmart\"\n" // 行号:834
        + "where \"product_id\" = -1"; // 行号:835
    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart'," // 行号:836
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:837
        + "'filter':{'type':'bound','dimension':'product_id','lower':'-1','lowerStrict':false," // 行号:838
        + "'upper':'-1','upperStrict':false,'ordering':'numeric'}," // 行号:839
        + "'columns':['product_name']," // 行号:840
        + "'resultFormat':'compactedList'}"; // 行号:841
    sql(sql) // 行号:842
        .limit(4) // 行号:843
        .returnsUnordered() // 行号:844
        .queryContains(new DruidChecker(druidQuery)); // 行号:845
  } // 行号:846

  /** As {@link #testFilterSortDescNumeric()} but with a filter that cannot // 行号:848
   * be pushed down to Druid. */
  @Test void testNonPushableFilterSortDesc() { // 行号:850
    final String sql = "select \"product_name\" from \"foodmart\"\n" // 行号:851
        + "where cast(\"product_id\" as integer) - 1500 BETWEEN 0 AND 2\n" // 行号:852
        + "order by \"state_province\" desc, \"product_id\""; // 行号:853
    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart'," // 行号:854
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z'],"; // 行号:855
    final String druidFilter = "\"filter\":{\"type\":\"and\"," // 行号:856
        + "\"fields\":[{\"type\":\"expression\",\"expression\":\"((CAST(\\\"product_id\\\""; // 行号:857
    final String druidQuery2 = "'columns':['product_name','state_province','product_id']," // 行号:858
        + "'resultFormat':'compactedList'}"; // 行号:859

    sql(sql) // 行号:861
        .limit(4) // 行号:862
        .returns(resultSet -> { // 行号:863
          try { // 行号:864
            for (int i = 0; i < 4; i++) { // 行号:865
              assertTrue(resultSet.next()); // 行号:866
              assertThat(resultSet.getString("product_name"), // 行号:867
                  is("Fort West Dried Apricots")); // 行号:868
            } // 行号:869
            assertFalse(resultSet.next()); // 行号:870
          } catch (SQLException e) { // 行号:871
            throw TestUtil.rethrow(e); // 行号:872
          } // 行号:873
        }) // 行号:874
        .queryContains(new DruidChecker(druidQuery, druidFilter, druidQuery2)); // 行号:875
  } // 行号:876

  @Test void testUnionPlan() { // 行号:878
    final String sql = "select distinct \"gender\" from \"foodmart\"\n" // 行号:879
        + "union all\n" // 行号:880
        + "select distinct \"marital_status\" from \"foodmart\""; // 行号:881
    final String explain = "PLAN=" // 行号:882
        + "EnumerableUnion(all=[true])\n" // 行号:883
        + "  EnumerableInterpreter\n" // 行号:884
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$39]], groups=[{0}], aggs=[[]])\n" // 行号:885
        + "  EnumerableInterpreter\n" // 行号:886
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$37]], groups=[{0}], aggs=[[]])\n"; // 行号:887
    sql(sql) // 行号:888
        .explainContains(explain) // 行号:889
        .returnsUnordered("gender=F", // 行号:890
            "gender=M", // 行号:891
            "gender=M", // 行号:892
            "gender=S"); // 行号:893
  } // 行号:894

  @Test void testFilterUnionPlan() { // 行号:896
    final String sql = "select * from (\n" // 行号:897
        + "  select distinct \"gender\" from \"foodmart\"\n" // 行号:898
        + "  union all\n" // 行号:899
        + "  select distinct \"marital_status\" from \"foodmart\")\n" // 行号:900
        + "where \"gender\" = 'M'"; // 行号:901
    final String explain = "PLAN=" // 行号:902
        + "EnumerableInterpreter\n" // 行号:903
        + "  BindableFilter(condition=[=($0, 'M')])\n" // 行号:904
        + "    BindableUnion(all=[true])\n" // 行号:905
        + "      DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$39]], groups=[{0}], aggs=[[]])\n" // 行号:906
        + "      DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$37]], groups=[{0}], aggs=[[]])"; // 行号:907
    sql(sql) // 行号:908
        .explainContains(explain) // 行号:909
        .returnsUnordered("gender=M", // 行号:910
            "gender=M"); // 行号:911
  } // 行号:912

  @Test void testCountGroupByEmpty() { // 行号:914
    final String druidQuery = "{'queryType':'timeseries','dataSource':'foodmart'," // 行号:915
        + "'descending':false,'granularity':'all'," // 行号:916
        + "'aggregations':[{'type':'count','name':'EXPR$0'}]," // 行号:917
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:918
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:919
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:920
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:921
        + "2992-01-10T00:00:00.000Z]], groups=[{}], aggs=[[COUNT()]])"; // 行号:922
    final String sql = "select count(*) from \"foodmart\""; // 行号:923
    sql(sql) // 行号:924
        .returnsUnordered("EXPR$0=86829") // 行号:925
        .queryContains(new DruidChecker(druidQuery)) // 行号:926
        .explainContains(explain); // 行号:927
  } // 行号:928

  @Test void testGroupByOneColumnNotProjected() { // 行号:930
    final String sql = "select count(*) as c from \"foodmart\"\n" // 行号:931
        + "group by \"state_province\" order by 1"; // 行号:932
    sql(sql) // 行号:933
        .returnsOrdered("C=21610", // 行号:934
            "C=24441", // 行号:935
            "C=40778"); // 行号:936
  } // 行号:937

  /** Unlike {@link #testGroupByTimeAndOneColumnNotProjected()}, we cannot use // 行号:939
   * "topN" because we have a global limit, and that requires // 行号:940
   * {@code granularity: all}. */
  @Test void testGroupByTimeAndOneColumnNotProjectedWithLimit() { // 行号:942
    final String sql = "select count(*) as \"c\"," // 行号:943
        + " cast(floor(\"timestamp\" to MONTH) as timestamp) as \"month\"\n" // 行号:944
        + "from \"foodmart\"\n" // 行号:945
        + "group by floor(\"timestamp\" to MONTH), \"state_province\"\n" // 行号:946
        + "order by \"c\" desc limit 3"; // 行号:947
    sql(sql) // 行号:948
        .returnsOrdered("c=4070; month=1997-12-01 00:00:00", // 行号:949
            "c=4033; month=1997-11-01 00:00:00", // 行号:950
            "c=3511; month=1997-07-01 00:00:00") // 行号:951
        .queryContains(new DruidChecker("'queryType':'groupBy'")); // 行号:952
  } // 行号:953

  @Test void testGroupByTimeAndOneMetricNotProjected() { // 行号:955
    final String sql = "select" // 行号:956
        + " count(*) as \"c\"," // 行号:957
        + " cast(floor(\"timestamp\" to MONTH) as timestamp) as \"month\"," // 行号:958
        + " floor(\"store_sales\") as sales\n" // 行号:959
        + "from \"foodmart\"\n" // 行号:960
        + "group by floor(\"timestamp\" to MONTH), \"state_province\", floor" // 行号:961
        + "(\"store_sales\")\n" // 行号:962
        + "order by \"c\" desc limit 3"; // 行号:963
    sql(sql).returnsOrdered("c=494; month=1997-11-01 00:00:00; SALES=5.0", // 行号:964
        "c=475; month=1997-12-01 00:00:00; SALES=5.0", // 行号:965
        "c=468; month=1997-03-01 00:00:00; SALES=5.0").queryContains(new DruidChecker("'queryType':'groupBy'")); // 行号:966
  } // 行号:967

  @Test void testGroupByTimeAndOneColumnNotProjected() { // 行号:969
    final String sql = "select count(*) as \"c\",\n" // 行号:970
        + "  cast(floor(\"timestamp\" to MONTH) as timestamp) as \"month\"\n" // 行号:971
        + "from \"foodmart\"\n" // 行号:972
        + "group by floor(\"timestamp\" to MONTH), \"state_province\"\n" // 行号:973
        + "having count(*) > 3500"; // 行号:974
    sql(sql) // 行号:975
        .returnsUnordered("c=3511; month=1997-07-01 00:00:00", // 行号:976
            "c=4033; month=1997-11-01 00:00:00", // 行号:977
            "c=4070; month=1997-12-01 00:00:00") // 行号:978
        .queryContains(new DruidChecker("'queryType':'groupBy'")); // 行号:979
  } // 行号:980

  @Test void testOrderByOneColumnNotProjected() { // 行号:982
    // Result including state: CA=24441, OR=21610, WA=40778
    final String sql = "select count(*) as c from \"foodmart\"\n" // 行号:984
        + "group by \"state_province\" order by \"state_province\""; // 行号:985
    sql(sql) // 行号:986
        .returnsOrdered("C=24441", // 行号:987
            "C=21610", // 行号:988
            "C=40778"); // 行号:989
  } // 行号:990

  @Test void testGroupByOneColumn() { // 行号:992
    final String sql = "select \"state_province\", count(*) as c\n" // 行号:993
        + "from \"foodmart\"\n" // 行号:994
        + "group by \"state_province\"\n" // 行号:995
        + "order by \"state_province\""; // 行号:996
    String explain = "PLAN=EnumerableInterpreter\n" // 行号:997
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:998
        + "2992-01-10T00:00:00.000Z]], projects=[[$30]], groups=[{0}], " // 行号:999
        + "aggs=[[COUNT()]], sort0=[0], dir0=[ASC])"; // 行号:1000
    sql(sql) // 行号:1001
        .limit(2) // 行号:1002
        .returnsOrdered("state_province=CA; C=24441", // 行号:1003
            "state_province=OR; C=21610") // 行号:1004
        .explainContains(explain); // 行号:1005
  } // 行号:1006

  @Test void testGroupByOneColumnReversed() { // 行号:1008
    final String sql = "select count(*) as c, \"state_province\"\n" // 行号:1009
        + "from \"foodmart\"\n" // 行号:1010
        + "group by \"state_province\"\n" // 行号:1011
        + "order by \"state_province\""; // 行号:1012
    sql(sql) // 行号:1013
        .limit(2) // 行号:1014
        .returnsOrdered("C=24441; state_province=CA", // 行号:1015
            "C=21610; state_province=OR"); // 行号:1016
  } // 行号:1017

  @Test void testGroupByAvgSumCount() { // 行号:1019
    final String sql = "select \"state_province\",\n" // 行号:1020
        + " avg(\"unit_sales\") as a,\n" // 行号:1021
        + " sum(\"unit_sales\") as s,\n" // 行号:1022
        + " count(\"store_sqft\") as c,\n" // 行号:1023
        + " count(*) as c0\n" // 行号:1024
        + "from \"foodmart\"\n" // 行号:1025
        + "group by \"state_province\"\n" // 行号:1026
        + "order by 1"; // 行号:1027
    sql(sql) // 行号:1028
        .limit(2) // 行号:1029
        .returnsUnordered("state_province=CA; A=3; S=74748; C=16347; C0=24441", // 行号:1030
            "state_province=OR; A=3; S=67659; C=21610; C0=21610") // 行号:1031
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1032
            + "  BindableProject(state_province=[$0], A=[/(CASE(=($2, 0), null:BIGINT, $1), $2)], " // 行号:1033
            + "S=[CASE(=($2, 0), null:BIGINT, $1)], C=[$3], C0=[$4])\n" // 行号:1034
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:1035
            + "2992-01-10T00:00:00.000Z]], projects=[[$30, $89, $71]], groups=[{0}], " // 行号:1036
            + "aggs=[[$SUM0($1), COUNT($1), COUNT($2), COUNT()]], sort0=[0], dir0=[ASC])") // 行号:1037
        .queryContains( // 行号:1038
            new DruidChecker("{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'" // 行号:1039
                + ",'dimensions':[{'type':'default','dimension':'state_province','outputName':'state_province'" // 行号:1040
                + ",'outputType':'STRING'}],'limitSpec':" // 行号:1041
                + "{'type':'default','columns':[{'dimension':'state_province'," // 行号:1042
                + "'direction':'ascending','dimensionOrder':'lexicographic'}]},'aggregations':" // 行号:1043
                + "[{'type':'longSum','name':'$f1','fieldName':'unit_sales'},{'type':'filtered'," // 行号:1044
                + "'filter':{'type':'not','field':{'type':'selector','dimension':'unit_sales'," // 行号:1045
                + "'value':null}},'aggregator':{'type':'count','name':'$f2','fieldName':'unit_sales'}}" // 行号:1046
                + ",{'type':'filtered','filter':{'type':'not','field':{'type':'selector'," // 行号:1047
                + "'dimension':'store_sqft','value':null}},'aggregator':{'type':'count','name':'C'," // 行号:1048
                + "'fieldName':'store_sqft'}},{'type':'count','name':'C0'}]," // 行号:1049
                + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}")); // 行号:1050
  } // 行号:1051

  @Test void testGroupByMonthGranularity() { // 行号:1053
    final String sql = "select sum(\"unit_sales\") as s,\n" // 行号:1054
        + " count(\"store_sqft\") as c\n" // 行号:1055
        + "from \"foodmart\"\n" // 行号:1056
        + "group by floor(\"timestamp\" to MONTH) order by s"; // 行号:1057
    String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'"; // 行号:1058
    sql(sql) // 行号:1059
        .limit(3) // 行号:1060
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1061
            + "  BindableProject(S=[$1], C=[$2])\n" // 行号:1062
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:1063
            + "2992-01-10T00:00:00.000Z]], projects=[[FLOOR($0, FLAG(MONTH)), $89, $71]], " // 行号:1064
            + "groups=[{0}], aggs=[[SUM($1), COUNT($2)]], sort0=[1], dir0=[ASC])") // 行号:1065
        .returnsOrdered("S=19958; C=5606", "S=20179; C=5523", "S=20388; C=5591") // 行号:1066
        .queryContains(new DruidChecker(druidQuery)); // 行号:1067
  } // 行号:1068

  /** Test case for // 行号:1070
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1577">[CALCITE-1577]
   * Druid adapter: Incorrect result - limit on timestamp disappears</a>. */
  @Test void testGroupByMonthGranularitySort() { // 行号:1073
    final String sql = "select sum(\"unit_sales\") as s,\n" // 行号:1074
        + " count(\"store_sqft\") as c\n" // 行号:1075
        + "from \"foodmart\"\n" // 行号:1076
        + "group by floor(\"timestamp\" to MONTH)\n" // 行号:1077
        + "order by floor(\"timestamp\" to MONTH) ASC"; // 行号:1078
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:1079
        + "  BindableProject(S=[$1], C=[$2], EXPR$2=[$0])\n" // 行号:1080
        + "    DruidQuery(table=[[foodmart, foodmart]], " // 行号:1081
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[FLOOR($0, " // 行号:1082
        + "FLAG(MONTH)), $89, $71]], groups=[{0}], aggs=[[SUM($1), COUNT($2)]], sort0=[0], " // 行号:1083
        + "dir0=[ASC])"; // 行号:1084
    sql(sql) // 行号:1085
        .explainContains(explain) // 行号:1086
        .returnsOrdered("S=21628; C=5957", // 行号:1087
            "S=20957; C=5842", // 行号:1088
            "S=23706; C=6528", // 行号:1089
            "S=20179; C=5523", // 行号:1090
            "S=21081; C=5793", // 行号:1091
            "S=21350; C=5863", // 行号:1092
            "S=23763; C=6762", // 行号:1093
            "S=21697; C=5915", // 行号:1094
            "S=20388; C=5591", // 行号:1095
            "S=19958; C=5606", // 行号:1096
            "S=25270; C=7026", // 行号:1097
            "S=26796; C=7338"); // 行号:1098
  } // 行号:1099

  @Test void testGroupByMonthGranularitySortLimit() { // 行号:1101
    final String sql = "select cast(floor(\"timestamp\" to MONTH) as timestamp) as m,\n" // 行号:1102
        + " sum(\"unit_sales\") as s,\n" // 行号:1103
        + " count(\"store_sqft\") as c\n" // 行号:1104
        + "from \"foodmart\"\n" // 行号:1105
        + "group by floor(\"timestamp\" to MONTH)\n" // 行号:1106
        + "order by floor(\"timestamp\" to MONTH) limit 3"; // 行号:1107
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:1108
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:1109
        + "2992-01-10T00:00:00.000Z]], projects=[[FLOOR($0, FLAG(MONTH)), $89, $71]], groups=[{0}], " // 行号:1110
        + "aggs=[[SUM($1), COUNT($2)]], post_projects=[[CAST($0):TIMESTAMP(0) NOT NULL, $1, $2, $0]]" // 行号:1111
        + ", sort0=[3], dir0=[ASC], fetch=[3])"; // 行号:1112
    sql(sql) // 行号:1113
        .returnsOrdered("M=1997-01-01 00:00:00; S=21628; C=5957", // 行号:1114
            "M=1997-02-01 00:00:00; S=20957; C=5842", // 行号:1115
            "M=1997-03-01 00:00:00; S=23706; C=6528") // 行号:1116
        .explainContains(explain); // 行号:1117
  } // 行号:1118

  @Test void testGroupByDayGranularity() { // 行号:1120
    final String sql = "select sum(\"unit_sales\") as s,\n" // 行号:1121
        + " count(\"store_sqft\") as c\n" // 行号:1122
        + "from \"foodmart\"\n" // 行号:1123
        + "group by floor(\"timestamp\" to DAY) order by c desc"; // 行号:1124
    String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'"; // 行号:1125
    sql(sql) // 行号:1126
        .limit(3) // 行号:1127
        .queryContains(new DruidChecker(druidQuery)) // 行号:1128
        .returnsOrdered("S=3850; C=1230", "S=3342; C=1071", "S=3219; C=1024"); // 行号:1129
  } // 行号:1130

  @Test void testGroupByMonthGranularityFiltered() { // 行号:1132
    final String sql = "select sum(\"unit_sales\") as s,\n" // 行号:1133
        + " count(\"store_sqft\") as c\n" // 行号:1134
        + "from \"foodmart\"\n" // 行号:1135
        + "where \"timestamp\" >= '1996-01-01 00:00:00 UTC' and " // 行号:1136
        + " \"timestamp\" < '1998-01-01 00:00:00 UTC'\n" // 行号:1137
        + "group by floor(\"timestamp\" to MONTH) order by s asc"; // 行号:1138
    String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'"; // 行号:1139

    sql(sql) // 行号:1141
        .limit(3) // 行号:1142
        .returnsOrdered("S=19958; C=5606", "S=20179; C=5523", "S=20388; C=5591") // 行号:1143
        .queryContains(new DruidChecker(druidQuery)); // 行号:1144
  } // 行号:1145

  @Test void testTopNMonthGranularity() { // 行号:1147
    final String sql = "select sum(\"unit_sales\") as s,\n" // 行号:1148
        + "max(\"unit_sales\") as m,\n" // 行号:1149
        + "\"state_province\" as p\n" // 行号:1150
        + "from \"foodmart\"\n" // 行号:1151
        + "group by \"state_province\", floor(\"timestamp\" to MONTH)\n" // 行号:1152
        + "order by s desc limit 3"; // 行号:1153
    // Cannot use a Druid "topN" query, granularity != "all";
    // have to use "groupBy" query followed by external Sort and fetch.
    final String explain = "PLAN=" // 行号:1156
        + "EnumerableCalc(expr#0..3=[{inputs}], S=[$t2], M=[$t3], P=[$t0])\n" // 行号:1157
        + "  EnumerableInterpreter\n" // 行号:1158
        + "    DruidQuery(table=[[foodmart, foodmart]], " // 行号:1159
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$30, FLOOR" // 行号:1160
        + "($0, FLAG(MONTH)), $89]], groups=[{0, 1}], aggs=[[SUM($2), MAX($2)]], sort0=[2], " // 行号:1161
        + "dir0=[DESC], fetch=[3])"; // 行号:1162
    final String druidQueryPart1 = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:1163
        + "'granularity':'all','dimensions':[{'type':'default'," // 行号:1164
        + "'dimension':'state_province',\"outputName\":\"state_province\",\"outputType\":\"STRING\"}," // 行号:1165
        + "{'type':'extraction','dimension':'__time'," // 行号:1166
        + "'outputName':'floor_month','extractionFn':{'type':'timeFormat','format'"; // 行号:1167
    final String druidQueryPart2 = "'limitSpec':{'type':'default','limit':3," // 行号:1168
        + "'columns':[{'dimension':'S','direction':'descending'," // 行号:1169
        + "'dimensionOrder':'numeric'}]},'aggregations':[{'type':'longSum'," // 行号:1170
        + "'name':'S','fieldName':'unit_sales'},{'type':'longMax','name':'M'," // 行号:1171
        + "'fieldName':'unit_sales'}]," // 行号:1172
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:1173
    sql(sql) // 行号:1174
        .returnsUnordered("S=12399; M=6; P=WA", // 行号:1175
            "S=12297; M=7; P=WA", // 行号:1176
            "S=10640; M=6; P=WA") // 行号:1177
        .explainContains(explain) // 行号:1178
        .queryContains(new DruidChecker(druidQueryPart1, druidQueryPart2)); // 行号:1179
  } // 行号:1180

  @Test void testTopNDayGranularityFiltered() { // 行号:1182
    final String sql = "select sum(\"unit_sales\") as s,\n" // 行号:1183
        + "max(\"unit_sales\") as m,\n" // 行号:1184
        + "\"state_province\" as p\n" // 行号:1185
        + "from \"foodmart\"\n" // 行号:1186
        + "where \"timestamp\" >= '1997-01-01 00:00:00 UTC' and " // 行号:1187
        + " \"timestamp\" < '1997-09-01 00:00:00 UTC'\n" // 行号:1188
        + "group by \"state_province\", floor(\"timestamp\" to DAY)\n" // 行号:1189
        + "order by s desc limit 6"; // 行号:1190
    final String explain = "PLAN=EnumerableCalc(expr#0..3=[{inputs}], S=[$t2], M=[$t3], P=[$t0])\n" // 行号:1191
        + "  EnumerableInterpreter\n" // 行号:1192
        + "    DruidQuery(table=[[foodmart, foodmart]], " // 行号:1193
        + "intervals=[[1997-01-01T00:00:00.000Z/1997-09-01T00:00:00.000Z]], projects=[[$30, FLOOR" // 行号:1194
        + "($0, FLAG(DAY)), $89]], groups=[{0, 1}], aggs=[[SUM($2), MAX($2)]], sort0=[2], " // 行号:1195
        + "dir0=[DESC], fetch=[6])"; // 行号:1196
    final String druidQueryType = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:1197
        + "'granularity':'all','dimensions'"; // 行号:1198
    final String limitSpec = "'limitSpec':{'type':'default','limit':6," // 行号:1199
        + "'columns':[{'dimension':'S','direction':'descending','dimensionOrder':'numeric'}]}"; // 行号:1200
    sql(sql) // 行号:1201
        .returnsOrdered("S=2527; M=5; P=OR", // 行号:1202
            "S=2525; M=6; P=OR", // 行号:1203
            "S=2238; M=6; P=OR", // 行号:1204
            "S=1715; M=5; P=OR", // 行号:1205
            "S=1691; M=5; P=OR", // 行号:1206
            "S=1629; M=5; P=WA") // 行号:1207
        .explainContains(explain) // 行号:1208
        .queryContains(new DruidChecker(druidQueryType, limitSpec)); // 行号:1209
  } // 行号:1210

  @Test void testGroupByHaving() { // 行号:1212
    final String sql = "select \"state_province\" as s, count(*) as c\n" // 行号:1213
        + "from \"foodmart\"\n" // 行号:1214
        + "group by \"state_province\" having count(*) > 23000 order by 1"; // 行号:1215
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:1216
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:1217
        + "2992-01-10T00:00:00.000Z]], projects=[[$30]], groups=[{0}], aggs=[[COUNT()]], " // 行号:1218
        + "filter=[>($1, 23000)], sort0=[0], dir0=[ASC])"; // 行号:1219
    sql(sql) // 行号:1220
        .returnsOrdered("S=CA; C=24441", // 行号:1221
            "S=WA; C=40778") // 行号:1222
        .explainContains(explain); // 行号:1223
  } // 行号:1224

  @Test void testGroupComposite() { // 行号:1226
    // Note: We don't push down SORT-LIMIT yet
    final String sql = "select count(*) as c, \"state_province\", \"city\"\n" // 行号:1228
        + "from \"foodmart\"\n" // 行号:1229
        + "group by \"state_province\", \"city\"\n" // 行号:1230
        + "order by c desc limit 2"; // 行号:1231
    final String explain = "PLAN=EnumerableCalc(expr#0..2=[{inputs}], C=[$t2], " // 行号:1232
        + "state_province=[$t0], city=[$t1])\n" // 行号:1233
        + "  EnumerableInterpreter\n" // 行号:1234
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$30, $29]], groups=[{0, 1}], aggs=[[COUNT()]], sort0=[2], dir0=[DESC], fetch=[2])"; // 行号:1235
    sql(sql) // 行号:1236
        .returnsOrdered("C=7394; state_province=WA; city=Spokane", // 行号:1237
            "C=3958; state_province=WA; city=Olympia") // 行号:1238
        .explainContains(explain); // 行号:1239
  } // 行号:1240

  /** Tests that distinct-count is pushed down to Druid and evaluated using // 行号:1242
   * "cardinality". The result is approximate, but gives the correct result in // 行号:1243
   * this example when rounded down using FLOOR. */
  @Test void testDistinctCount() { // 行号:1245
    final String sql = "select \"state_province\",\n" // 行号:1246
        + " floor(count(distinct \"city\")) as cdc\n" // 行号:1247
        + "from \"foodmart\"\n" // 行号:1248
        + "group by \"state_province\"\n" // 行号:1249
        + "order by 2 desc limit 2"; // 行号:1250
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:1251
        + "  BindableSort(sort0=[$1], dir0=[DESC], fetch=[2])\n" // 行号:1252
        + "    BindableProject(state_province=[$0], CDC=[FLOOR($1)])\n" // 行号:1253
        + "      BindableAggregate(group=[{0}], agg#0=[COUNT($1)])\n" // 行号:1254
        + "        DruidQuery(table=[[foodmart, foodmart]], " // 行号:1255
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:1256
        + "projects=[[$30, $29]], groups=[{0, 1}], aggs=[[]])"; // 行号:1257
    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:1258
        + "'granularity':'all','dimensions':[" // 行号:1259
        + "{'type':'default','dimension':'state_province','outputName':'state_province','outputType':'STRING'}," // 行号:1260
        + "{'type':'default','dimension':'city','outputName':'city','outputType':'STRING'}]," // 行号:1261
        + "'limitSpec':{'type':'default'},'aggregations':[]," // 行号:1262
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:1263
    sql(sql) // 行号:1264
        .explainContains(explain) // 行号:1265
        .queryContains(new DruidChecker(druidQuery)) // 行号:1266
        .returnsUnordered("state_province=CA; CDC=45", // 行号:1267
            "state_province=WA; CDC=22"); // 行号:1268
  } // 行号:1269

  /** Tests that projections of columns are pushed into the DruidQuery, and // 行号:1271
   * projections of expressions that Druid cannot handle (in this case, a // 行号:1272
   * literal 0) stay up. */
  @Test void testProject() { // 行号:1274
    final String sql = "select \"product_name\", 0 as zero\n" // 行号:1275
        + "from \"foodmart\"\n" // 行号:1276
        + "order by \"product_name\""; // 行号:1277
    final String explain = "PLAN=EnumerableSort(sort0=[$0], dir0=[ASC])\n" // 行号:1278
        + "  EnumerableInterpreter\n" // 行号:1279
        + "    DruidQuery(table=[[foodmart, foodmart]], " // 行号:1280
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$3, 0]])"; // 行号:1281
    sql(sql) // 行号:1282
        .limit(2) // 行号:1283
        .returnsUnordered("product_name=ADJ Rosy Sunglasses; ZERO=0", // 行号:1284
            "product_name=ADJ Rosy Sunglasses; ZERO=0") // 行号:1285
        .explainContains(explain); // 行号:1286
  } // 行号:1287

  @Test void testFilterDistinct() { // 行号:1289
    final String sql = "select distinct \"state_province\", \"city\",\n" // 行号:1290
        + "  \"product_name\"\n" // 行号:1291
        + "from \"foodmart\"\n" // 行号:1292
        + "where \"product_name\" = 'High Top Dried Mushrooms'\n" // 行号:1293
        + "and \"quarter\" in ('Q2', 'Q3')\n" // 行号:1294
        + "and \"state_province\" = 'WA'"; // 行号:1295
    final String druidQuery1 = "{\"queryType\":\"groupBy\"," // 行号:1296
        + "\"dataSource\":\"foodmart\",\"granularity\":\"all\""; // 行号:1297
    final String druidQuery2 = "\"filter\":{\"type\":\"and\",\"fields\":[{\"type\":" // 行号:1298
        + "\"selector\",\"dimension\":\"product_name\",\"value\":\"High Top Dried Mushrooms\"}," // 行号:1299
        + "{\"type\":\"or\",\"fields\":[{\"type\":\"selector\",\"dimension\":" // 行号:1300
        + "\"quarter\",\"value\":\"Q2\"},{\"type\":\"selector\",\"dimension\":\"quarter\"," // 行号:1301
        + "\"value\":\"Q3\"}]},{\"type\":\"selector\",\"dimension\":" // 行号:1302
        + "\"state_province\",\"value\":\"WA\"}]},\"aggregations\":[]," // 行号:1303
        + "\"postAggregations\":[{\"type\":\"expression\"," // 行号:1304
        + "\"name\":\"state_province\",\"expression\":\"'WA'\"},{\"type\":\"expression\"," // 行号:1305
        + "\"name\":\"product_name\",\"expression\":\"'High Top Dried Mushrooms'\"}]," // 行号:1306
        + "\"intervals\":[\"1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z\"]}"; // 行号:1307
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:1308
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1309
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:1310
        + "filter=[AND(" // 行号:1311
        + "=($3, 'High Top Dried Mushrooms'), " // 行号:1312
        + "SEARCH($87, Sarg['Q2':VARCHAR, 'Q3':VARCHAR]:VARCHAR), " // 行号:1313
        + "=($30, 'WA'))], " // 行号:1314
        + "projects=[[$29]], groups=[{0}], aggs=[[]], " // 行号:1315
        + "post_projects=[[CAST('WA':VARCHAR):VARCHAR, $0, " // 行号:1316
        + "CAST('High Top Dried Mushrooms':VARCHAR):VARCHAR]])\n"; // 行号:1317
    sql(sql) // 行号:1318
        .queryContains(new DruidChecker(false, druidQuery1, druidQuery2)) // 行号:1319
        .explainContains(explain) // 行号:1320
        .returnsUnordered( // 行号:1321
            "state_province=WA; city=Bremerton; product_name=High Top Dried Mushrooms", // 行号:1322
            "state_province=WA; city=Everett; product_name=High Top Dried Mushrooms", // 行号:1323
            "state_province=WA; city=Kirkland; product_name=High Top Dried Mushrooms", // 行号:1324
            "state_province=WA; city=Lynnwood; product_name=High Top Dried Mushrooms", // 行号:1325
            "state_province=WA; city=Olympia; product_name=High Top Dried Mushrooms", // 行号:1326
            "state_province=WA; city=Port Orchard; product_name=High Top Dried Mushrooms", // 行号:1327
            "state_province=WA; city=Puyallup; product_name=High Top Dried Mushrooms", // 行号:1328
            "state_province=WA; city=Spokane; product_name=High Top Dried Mushrooms", // 行号:1329
            "state_province=WA; city=Tacoma; product_name=High Top Dried Mushrooms", // 行号:1330
            "state_province=WA; city=Yakima; product_name=High Top Dried Mushrooms"); // 行号:1331
  } // 行号:1332

  @Test void testFilter() { // 行号:1334
    final String sql = "select \"state_province\", \"city\",\n" // 行号:1335
        + "  \"product_name\"\n" // 行号:1336
        + "from \"foodmart\"\n" // 行号:1337
        + "where \"product_name\" = 'High Top Dried Mushrooms'\n" // 行号:1338
        + "and \"quarter\" in ('Q2', 'Q3')\n" // 行号:1339
        + "and \"state_province\" = 'WA'"; // 行号:1340
    final String druidQuery = "{'queryType':'scan'," // 行号:1341
        + "'dataSource':'foodmart'," // 行号:1342
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:1343
        + "'filter':{'type':'and','fields':[" // 行号:1344
        + "{'type':'selector','dimension':'product_name','value':'High Top Dried Mushrooms'}," // 行号:1345
        + "{'type':'or','fields':[" // 行号:1346
        + "{'type':'selector','dimension':'quarter','value':'Q2'}," // 行号:1347
        + "{'type':'selector','dimension':'quarter','value':'Q3'}]}," // 行号:1348
        + "{'type':'selector','dimension':'state_province','value':'WA'}]}," // 行号:1349
        + "'columns':['state_province','city','product_name']," // 行号:1350
        + "'resultFormat':'compactedList'}"; // 行号:1351
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:1352
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1353
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:1354
        + "filter=[AND(" // 行号:1355
        + "=($3, 'High Top Dried Mushrooms'), " // 行号:1356
        + "SEARCH($87, Sarg['Q2':VARCHAR, 'Q3':VARCHAR]:VARCHAR), " // 行号:1357
        + "=($30, 'WA'))], " // 行号:1358
        + "projects=[[$30, $29, $3]])\n"; // 行号:1359
    sql(sql) // 行号:1360
        .queryContains(new DruidChecker(druidQuery)) // 行号:1361
        .explainContains(explain) // 行号:1362
        .returnsUnordered( // 行号:1363
            "state_province=WA; city=Bremerton; product_name=High Top Dried Mushrooms", // 行号:1364
            "state_province=WA; city=Everett; product_name=High Top Dried Mushrooms", // 行号:1365
            "state_province=WA; city=Kirkland; product_name=High Top Dried Mushrooms", // 行号:1366
            "state_province=WA; city=Lynnwood; product_name=High Top Dried Mushrooms", // 行号:1367
            "state_province=WA; city=Olympia; product_name=High Top Dried Mushrooms", // 行号:1368
            "state_province=WA; city=Port Orchard; product_name=High Top Dried Mushrooms", // 行号:1369
            "state_province=WA; city=Puyallup; product_name=High Top Dried Mushrooms", // 行号:1370
            "state_province=WA; city=Puyallup; product_name=High Top Dried Mushrooms", // 行号:1371
            "state_province=WA; city=Spokane; product_name=High Top Dried Mushrooms", // 行号:1372
            "state_province=WA; city=Spokane; product_name=High Top Dried Mushrooms", // 行号:1373
            "state_province=WA; city=Spokane; product_name=High Top Dried Mushrooms", // 行号:1374
            "state_province=WA; city=Tacoma; product_name=High Top Dried Mushrooms", // 行号:1375
            "state_province=WA; city=Yakima; product_name=High Top Dried Mushrooms", // 行号:1376
            "state_province=WA; city=Yakima; product_name=High Top Dried Mushrooms", // 行号:1377
            "state_province=WA; city=Yakima; product_name=High Top Dried Mushrooms"); // 行号:1378
  } // 行号:1379

  /** Tests that conditions applied to time units extracted via the EXTRACT // 行号:1381
   * function become ranges on the timestamp column // 行号:1382
   * // 行号:1383
   * <p>Test case for // 行号:1384
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1334">[CALCITE-1334]
   * Convert predicates on EXTRACT function calls into date ranges</a>. */
  @Test void testFilterTimestamp() { // 行号:1387
    String sql = "select count(*) as c\n" // 行号:1388
        + "from \"foodmart\"\n" // 行号:1389
        + "where extract(year from \"timestamp\") = 1997\n" // 行号:1390
        + "and extract(month from \"timestamp\") in (4, 6)\n"; // 行号:1391
    final String explain = "PLAN=EnumerableInterpreter\n" // 行号:1392
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1997-04-01T00:00:00.000Z/" // 行号:1393
        + "1997-05-01T00:00:00.000Z, 1997-06-01T00:00:00.000Z/1997-07-01T00:00:00.000Z]]," // 行号:1394
        + " projects=[[0]], groups=[{}], aggs=[[COUNT()]])"; // 行号:1395
    CalciteAssert.AssertQuery q = sql(sql) // 行号:1396
        .returnsUnordered("C=13500"); // 行号:1397
    Assumptions.assumeTrue(Bug.CALCITE_4213_FIXED, "CALCITE-4213"); // 行号:1398
    q.explainContains(explain); // 行号:1399
  } // 行号:1400

  @Test void testFilterSwapped() { // 行号:1402
    String sql = "select \"state_province\"\n" // 行号:1403
        + "from \"foodmart\"\n" // 行号:1404
        + "where 'High Top Dried Mushrooms' = \"product_name\""; // 行号:1405
    final String explain = "EnumerableInterpreter\n" // 行号:1406
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], filter=[=('High Top Dried Mushrooms', $3)], projects=[[$30]])"; // 行号:1407
    final String druidQuery = "'filter':{'type':'selector','dimension':'product_name'," // 行号:1408
        + "'value':'High Top Dried Mushrooms'}"; // 行号:1409
    sql(sql) // 行号:1410
        .explainContains(explain) // 行号:1411
        .queryContains(new DruidChecker(druidQuery)); // 行号:1412
  } // 行号:1413

  /** Tests a query that exposed several bugs in the interpreter. */
  @Test void testWhereGroupBy() { // 行号:1416
    String sql = "select \"wikipedia\".\"countryName\" as \"c0\",\n" // 行号:1417
        + " sum(\"wikipedia\".\"count\") as \"m1\",\n" // 行号:1418
        + " sum(\"wikipedia\".\"deleted\") as \"m2\",\n" // 行号:1419
        + " sum(\"wikipedia\".\"delta\") as \"m3\"\n" // 行号:1420
        + "from \"wiki\" as \"wikipedia\"\n" // 行号:1421
        + "where (\"wikipedia\".\"countryName\" in ('Colombia', 'France',\n" // 行号:1422
        + " 'Germany', 'India', 'Italy', 'Russia', 'United Kingdom',\n" // 行号:1423
        + " 'United States') or \"wikipedia\".\"countryName\" is null)\n" // 行号:1424
        + "group by \"wikipedia\".\"countryName\""; // 行号:1425
    String druidQuery = "{'type':'selector','dimension':'countryName','value':null}"; // 行号:1426
    sql(sql, WIKI) // 行号:1427
        .queryContains(new DruidChecker(druidQuery)) // 行号:1428
        .returnsCount(9); // 行号:1429
  } // 行号:1430

  @Test void testGroupByMetricAndExtractTime() { // 行号:1432
    final String sql = "SELECT count(*)," // 行号:1433
        + " cast(floor(\"timestamp\" to DAY) as timestamp), \"store_sales\" " // 行号:1434
        + "FROM \"foodmart\"\n" // 行号:1435
        + "GROUP BY \"store_sales\", floor(\"timestamp\" to DAY)\n ORDER BY \"store_sales\" DESC\n" // 行号:1436
        + "LIMIT 10\n"; // 行号:1437
    sql(sql).queryContains(new DruidChecker("{\"queryType\":\"groupBy\"")); // 行号:1438
  } // 行号:1439

  @Test void testFilterOnDouble() { // 行号:1441
    String sql = "select \"product_id\" from \"foodmart\"\n" // 行号:1442
        + "where cast(\"product_id\" as double) < 0.41024 and \"product_id\" < 12223"; // 行号:1443
    sql(sql).queryContains( // 行号:1444
        new DruidChecker("'type':'bound','dimension':'product_id','upper':'0.41024'", // 行号:1445
            "'upper':'12223'")); // 行号:1446
  } // 行号:1447

  @Test void testPushAggregateOnTime() { // 行号:1449
    String sql = "select \"product_id\", cast(\"timestamp\" as timestamp) as \"time\" " // 行号:1450
        + "from \"foodmart\" " // 行号:1451
        + "where \"product_id\" = 1016 " // 行号:1452
        + "and \"timestamp\" < '1997-01-03 00:00:00 UTC' " // 行号:1453
        + "and \"timestamp\" > '1990-01-01 00:00:00 UTC' " // 行号:1454
        + "group by \"timestamp\", \"product_id\" "; // 行号:1455
    String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:1456
        + "'granularity':'all','dimensions':[{'type':'extraction'," // 行号:1457
        + "'dimension':'__time','outputName':'extract'," // 行号:1458
        + "'extractionFn':{'type':'timeFormat','format':'yyyy-MM-dd"; // 行号:1459
    sql(sql) // 行号:1460
        .returnsUnordered("product_id=1016; time=1997-01-02 00:00:00") // 行号:1461
        .queryContains(new DruidChecker(druidQuery)); // 行号:1462
  } // 行号:1463

  @Test void testPushAggregateOnTimeWithExtractYear() { // 行号:1465
    String sql = "select EXTRACT( year from \"timestamp\") as \"year\",\"product_id\" from " // 行号:1466
        + "\"foodmart\" where \"product_id\" = 1016 and " // 行号:1467
        + "\"timestamp\" < cast('1999-01-02' as timestamp) and \"timestamp\" > cast" // 行号:1468
        + "('1997-01-01' as timestamp)" + " group by " // 行号:1469
        + " EXTRACT( year from \"timestamp\"), \"product_id\" "; // 行号:1470
    sql(sql) // 行号:1471
        .queryContains( // 行号:1472
            new DruidChecker( // 行号:1473
                ",'granularity':'all'", // 行号:1474
                "{'type':'extraction'," // 行号:1475
                    + "'dimension':'__time','outputName':'extract_year'," // 行号:1476
                    + "'extractionFn':{'type':'timeFormat','format':'yyyy'," // 行号:1477
                    + "'timeZone':'UTC','locale':'en-US'}}")) // 行号:1478
        .returnsUnordered("year=1997; product_id=1016"); // 行号:1479
  } // 行号:1480

  @Test void testPushAggregateOnTimeWithExtractMonth() { // 行号:1482
    String sql = "select EXTRACT( month from \"timestamp\") as \"month\",\"product_id\" from " // 行号:1483
        + "\"foodmart\" where \"product_id\" = 1016 and " // 行号:1484
        + "\"timestamp\" < cast('1997-06-02' as timestamp) and \"timestamp\" > cast" // 行号:1485
        + "('1997-01-01' as timestamp)" + " group by " // 行号:1486
        + " EXTRACT( month from \"timestamp\"), \"product_id\" "; // 行号:1487
    sql(sql) // 行号:1488
        .queryContains( // 行号:1489
            new DruidChecker( // 行号:1490
                ",'granularity':'all'", // 行号:1491
                "{'type':'extraction'," // 行号:1492
                    + "'dimension':'__time','outputName':'extract_month'," // 行号:1493
                    + "'extractionFn':{'type':'timeFormat','format':'M'," // 行号:1494
                    + "'timeZone':'UTC','locale':'en-US'}}")) // 行号:1495
        .returnsUnordered("month=1; product_id=1016", "month=2; product_id=1016", // 行号:1496
            "month=3; product_id=1016", "month=4; product_id=1016", "month=5; product_id=1016"); // 行号:1497
  } // 行号:1498

  @Test void testPushAggregateOnTimeWithExtractDay() { // 行号:1500
    String sql = "select EXTRACT( day from \"timestamp\") as \"day\"," // 行号:1501
        + "\"product_id\" from \"foodmart\"" // 行号:1502
        + " where \"product_id\" = 1016 and " // 行号:1503
        + "\"timestamp\" < cast('1997-01-20' as timestamp) and \"timestamp\" > cast" // 行号:1504
        + "('1997-01-01' as timestamp)" + " group by " // 行号:1505
        + " EXTRACT( day from \"timestamp\"), \"product_id\" "; // 行号:1506
    sql(sql) // 行号:1507
        .queryContains( // 行号:1508
            new DruidChecker( // 行号:1509
                ",'granularity':'all'", // 行号:1510
                "{'type':'extraction'," // 行号:1511
                    + "'dimension':'__time','outputName':'extract_day'," // 行号:1512
                    + "'extractionFn':{'type':'timeFormat','format':'d'," // 行号:1513
                    + "'timeZone':'UTC','locale':'en-US'}}")) // 行号:1514
        .returnsUnordered("day=2; product_id=1016", "day=10; product_id=1016", // 行号:1515
            "day=13; product_id=1016", "day=16; product_id=1016"); // 行号:1516
  } // 行号:1517

  @Test void testPushAggregateOnTimeWithExtractHourOfDay() { // 行号:1519
    String sql = // 行号:1520
        "select EXTRACT( hour from \"timestamp\") as \"hourOfDay\",\"product_id\"  from " // 行号:1521
            + "\"foodmart\" where \"product_id\" = 1016 and " // 行号:1522
            + "\"timestamp\" < cast('1997-06-02' as timestamp) and \"timestamp\" > cast" // 行号:1523
            + "('1997-01-01' as timestamp)" + " group by " // 行号:1524
            + " EXTRACT( hour from \"timestamp\"), \"product_id\" "; // 行号:1525
    sql(sql) // 行号:1526
        .queryContains(new DruidChecker("'queryType':'groupBy'")) // 行号:1527
        .returnsUnordered("hourOfDay=0; product_id=1016"); // 行号:1528
  } // 行号:1529

  @Test void testPushAggregateOnTimeWithExtractYearMonthDay() { // 行号:1531
    String sql = "select EXTRACT( day from \"timestamp\") as \"day\", EXTRACT( month from " // 行号:1532
        + "\"timestamp\") as \"month\",  EXTRACT( year from \"timestamp\") as \"year\",\"" // 行号:1533
        + "product_id\"  from \"foodmart\" where \"product_id\" = 1016 and " // 行号:1534
        + "\"timestamp\" < cast('1997-01-20' as timestamp) and \"timestamp\" > cast" // 行号:1535
        + "('1997-01-01' as timestamp)" // 行号:1536
        + " group by " // 行号:1537
        + " EXTRACT( day from \"timestamp\"), EXTRACT( month from \"timestamp\")," // 行号:1538
        + " EXTRACT( year from \"timestamp\"), \"product_id\" "; // 行号:1539
    sql(sql) // 行号:1540
        .queryContains( // 行号:1541
            new DruidChecker( // 行号:1542
                ",'granularity':'all'", // 行号:1543
                "{'type':'extraction'," // 行号:1544
                    + "'dimension':'__time','outputName':'extract_day'," // 行号:1545
                    + "'extractionFn':{'type':'timeFormat','format':'d'," // 行号:1546
                    + "'timeZone':'UTC','locale':'en-US'}}", // 行号:1547
                "{'type':'extraction'," // 行号:1548
                    + "'dimension':'__time','outputName':'extract_month'," // 行号:1549
                    + "'extractionFn':{'type':'timeFormat','format':'M'," // 行号:1550
                    + "'timeZone':'UTC','locale':'en-US'}}", // 行号:1551
                "{'type':'extraction'," // 行号:1552
                    + "'dimension':'__time','outputName':'extract_year'," // 行号:1553
                    + "'extractionFn':{'type':'timeFormat','format':'yyyy'," // 行号:1554
                    + "'timeZone':'UTC','locale':'en-US'}}")) // 行号:1555
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1556
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1557
            + "intervals=[[1997-01-01T00:00:00.001Z/1997-01-20T00:00:00.000Z]], " // 行号:1558
            + "filter=[=(CAST($1):INTEGER, 1016)], projects=[[EXTRACT(FLAG(DAY), $0), EXTRACT(FLAG(MONTH), $0), " // 行号:1559
            + "EXTRACT(FLAG(YEAR), $0), $1]], groups=[{0, 1, 2, 3}], aggs=[[]])\n") // 行号:1560
        .returnsUnordered("day=2; month=1; year=1997; product_id=1016", // 行号:1561
            "day=10; month=1; year=1997; product_id=1016", // 行号:1562
            "day=13; month=1; year=1997; product_id=1016", // 行号:1563
            "day=16; month=1; year=1997; product_id=1016"); // 行号:1564
  } // 行号:1565

  @Test void testPushAggregateOnTimeWithExtractYearMonthDayWithOutRenaming() { // 行号:1567
    String sql = "select EXTRACT( day from \"timestamp\"), EXTRACT( month from " // 行号:1568
        + "\"timestamp\"), EXTRACT( year from \"timestamp\"),\"" // 行号:1569
        + "product_id\"  from \"foodmart\" where \"product_id\" = 1016 and " // 行号:1570
        + "\"timestamp\" < cast('1997-01-20' as timestamp) and \"timestamp\" > cast" // 行号:1571
        + "('1997-01-01' as timestamp)" // 行号:1572
        + " group by " // 行号:1573
        + " EXTRACT( day from \"timestamp\"), EXTRACT( month from \"timestamp\")," // 行号:1574
        + " EXTRACT( year from \"timestamp\"), \"product_id\" "; // 行号:1575
    sql(sql) // 行号:1576
        .queryContains( // 行号:1577
            new DruidChecker( // 行号:1578
                ",'granularity':'all'", // 行号:1579
                "{'type':'extraction'," // 行号:1580
                    + "'dimension':'__time','outputName':'extract_day'," // 行号:1581
                    + "'extractionFn':{'type':'timeFormat','format':'d'," // 行号:1582
                    + "'timeZone':'UTC','locale':'en-US'}}", // 行号:1583
                "{'type':'extraction'," // 行号:1584
                    + "'dimension':'__time','outputName':'extract_month'," // 行号:1585
                    + "'extractionFn':{'type':'timeFormat','format':'M'," // 行号:1586
                    + "'timeZone':'UTC','locale':'en-US'}}", // 行号:1587
                "{'type':'extraction'," // 行号:1588
                    + "'dimension':'__time','outputName':'extract_year'," // 行号:1589
                    + "'extractionFn':{'type':'timeFormat','format':'yyyy'," // 行号:1590
                    + "'timeZone':'UTC','locale':'en-US'}}")) // 行号:1591
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1592
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1593
            + "intervals=[[1997-01-01T00:00:00.001Z/1997-01-20T00:00:00.000Z]], " // 行号:1594
            + "filter=[=(CAST($1):INTEGER, 1016)], projects=[[EXTRACT(FLAG(DAY), $0), EXTRACT(FLAG(MONTH), $0), " // 行号:1595
            + "EXTRACT(FLAG(YEAR), $0), $1]], groups=[{0, 1, 2, 3}], aggs=[[]])\n") // 行号:1596
        .returnsUnordered("EXPR$0=2; EXPR$1=1; EXPR$2=1997; product_id=1016", // 行号:1597
            "EXPR$0=10; EXPR$1=1; EXPR$2=1997; product_id=1016", // 行号:1598
            "EXPR$0=13; EXPR$1=1; EXPR$2=1997; product_id=1016", // 行号:1599
            "EXPR$0=16; EXPR$1=1; EXPR$2=1997; product_id=1016"); // 行号:1600
  } // 行号:1601

  @Test void testPushAggregateOnTimeWithExtractWithOutRenaming() { // 行号:1603
    String sql = "select EXTRACT( day from \"timestamp\"), " // 行号:1604
        + "\"product_id\" as \"dayOfMonth\" from \"foodmart\" " // 行号:1605
        + "where \"product_id\" = 1016 and \"timestamp\" < cast('1997-01-20' as timestamp) " // 行号:1606
        + "and \"timestamp\" > cast('1997-01-01' as timestamp)" // 行号:1607
        + " group by " // 行号:1608
        + " EXTRACT( day from \"timestamp\"), EXTRACT( day from \"timestamp\")," // 行号:1609
        + " \"product_id\" "; // 行号:1610
    sql(sql) // 行号:1611
        .queryContains( // 行号:1612
            new DruidChecker( // 行号:1613
                ",'granularity':'all'", // 行号:1614
                "{'type':'extraction'," // 行号:1615
                    + "'dimension':'__time','outputName':'extract_day'," // 行号:1616
                    + "'extractionFn':{'type':'timeFormat','format':'d'," // 行号:1617
                    + "'timeZone':'UTC','locale':'en-US'}}")) // 行号:1618
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1619
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1620
            + "intervals=[[1997-01-01T00:00:00.001Z/1997-01-20T00:00:00.000Z]], " // 行号:1621
            + "filter=[=(CAST($1):INTEGER, 1016)], projects=[[EXTRACT(FLAG(DAY), $0), $1]], " // 行号:1622
            + "groups=[{0, 1}], aggs=[[]])\n") // 行号:1623
        .returnsUnordered("EXPR$0=2; dayOfMonth=1016", "EXPR$0=10; dayOfMonth=1016", // 行号:1624
            "EXPR$0=13; dayOfMonth=1016", "EXPR$0=16; dayOfMonth=1016"); // 行号:1625
  } // 行号:1626

  @Test void testPushComplexFilter() { // 行号:1628
    String sql = "select sum(\"store_sales\") from \"foodmart\" " // 行号:1629
        + "where EXTRACT( year from \"timestamp\") = 1997 and " // 行号:1630
        + "\"cases_per_pallet\" >= 8 and \"cases_per_pallet\" <= 10 and " // 行号:1631
        + "\"units_per_case\" < 15 "; // 行号:1632
    String druidQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:1633
        + "'granularity':'all','filter':{'type':'and','fields':[{'type':'bound','dimension':" // 行号:1634
        + "'cases_per_pallet','lower':'8','lowerStrict':false,'ordering':'numeric'}," // 行号:1635
        + "{'type':'bound','dimension':'cases_per_pallet','upper':'10','upperStrict':false," // 行号:1636
        + "'ordering':'numeric'},{'type':'bound','dimension':'units_per_case','upper':'15'," // 行号:1637
        + "'upperStrict':true,'ordering':'numeric'}]},'aggregations':[{'type':'doubleSum'," // 行号:1638
        + "'name':'EXPR$0','fieldName':'store_sales'}],'intervals':['1997-01-01T00:00:00.000Z/" // 行号:1639
        + "1998-01-01T00:00:00.000Z'],'context':{'skipEmptyBuckets':false}}"; // 行号:1640
    sql(sql) // 行号:1641
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1642
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1643
            + "intervals=[[1997-01-01T00:00:00.000Z/1998-01-01T00:00:00.000Z]], " // 行号:1644
            + "filter=[AND(SEARCH(CAST($11):INTEGER, Sarg[[8..10]]), <(CAST($10):INTEGER, 15))], " // 行号:1645
            + "projects=[[$90]], groups=[{}], aggs=[[SUM($0)]])\n") // 行号:1646
        .returnsUnordered("EXPR$0=75364.1") // 行号:1647
        .queryContains(new DruidChecker(druidQuery)); // 行号:1648
  } // 行号:1649

  @Test void testPushOfFilterExtractionOnDayAndMonth() { // 行号:1651
    String sql = "SELECT \"product_id\" , EXTRACT(day from \"timestamp\"), EXTRACT(month from " // 行号:1652
        + "\"timestamp\") from \"foodmart\" WHERE  EXTRACT(day from \"timestamp\") >= 30 AND " // 行号:1653
        + "EXTRACT(month from \"timestamp\") = 11 " // 行号:1654
        + "AND  \"product_id\" >= 1549 group by \"product_id\", EXTRACT(day from " // 行号:1655
        + "\"timestamp\"), EXTRACT(month from \"timestamp\")"; // 行号:1656
    sql(sql) // 行号:1657
        .returnsUnordered("product_id=1549; EXPR$1=30; EXPR$2=11", // 行号:1658
            "product_id=1553; EXPR$1=30; EXPR$2=11"); // 行号:1659
  } // 行号:1660

  @Test void testPushOfFilterExtractionOnDayAndMonthAndYear() { // 行号:1662
    String sql = "SELECT \"product_id\" , EXTRACT(day from \"timestamp\"), EXTRACT(month from " // 行号:1663
        + "\"timestamp\") , EXTRACT(year from \"timestamp\") from \"foodmart\" " // 行号:1664
        + "WHERE  EXTRACT(day from \"timestamp\") >= 30 AND EXTRACT(month from \"timestamp\") = 11 " // 行号:1665
        + "AND  \"product_id\" >= 1549 AND EXTRACT(year from \"timestamp\") = 1997" // 行号:1666
        + "group by \"product_id\", EXTRACT(day from \"timestamp\"), " // 行号:1667
        + "EXTRACT(month from \"timestamp\"), EXTRACT(year from \"timestamp\")"; // 行号:1668
    sql(sql) // 行号:1669
        .returnsUnordered("product_id=1549; EXPR$1=30; EXPR$2=11; EXPR$3=1997", // 行号:1670
            "product_id=1553; EXPR$1=30; EXPR$2=11; EXPR$3=1997") // 行号:1671
        .queryContains( // 行号:1672
            new DruidChecker("{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'")); // 行号:1673
  } // 行号:1674

  @Test void testFilterExtractionOnMonthWithBetween() { // 行号:1676
    String sqlQuery = "SELECT \"product_id\", EXTRACT(month from \"timestamp\") FROM \"foodmart\"" // 行号:1677
        + " WHERE EXTRACT(month from \"timestamp\") BETWEEN 10 AND 11 AND  \"product_id\" >= 1558" // 行号:1678
        + " GROUP BY \"product_id\", EXTRACT(month from \"timestamp\")"; // 行号:1679
    String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'"; // 行号:1680
    sql(sqlQuery) // 行号:1681
        .returnsUnordered("product_id=1558; EXPR$1=10", "product_id=1558; EXPR$1=11", // 行号:1682
            "product_id=1559; EXPR$1=11") // 行号:1683
        .queryContains(new DruidChecker(druidQuery)); // 行号:1684
  } // 行号:1685

  @Test void testFilterExtractionOnMonthWithIn() { // 行号:1687
    String sqlQuery = "SELECT \"product_id\", EXTRACT(month from \"timestamp\") FROM \"foodmart\"" // 行号:1688
        + " WHERE EXTRACT(month from \"timestamp\") IN (10, 11) AND  \"product_id\" >= 1558" // 行号:1689
        + " GROUP BY \"product_id\", EXTRACT(month from \"timestamp\")"; // 行号:1690
    sql(sqlQuery) // 行号:1691
        .returnsUnordered("product_id=1558; EXPR$1=10", "product_id=1558; EXPR$1=11", // 行号:1692
            "product_id=1559; EXPR$1=11") // 行号:1693
        .queryContains( // 行号:1694
            new DruidChecker("{'queryType':'groupBy'," // 行号:1695
                + "'dataSource':'foodmart','granularity':'all'," // 行号:1696
                + "'dimensions':[{'type':'default','dimension':'product_id','outputName':'product_id','outputType':'STRING'}," // 行号:1697
                + "{'type':'extraction','dimension':'__time','outputName':'extract_month'," // 行号:1698
                + "'extractionFn':{'type':'timeFormat','format':'M','timeZone':'UTC'," // 行号:1699
                + "'locale':'en-US'}}],'limitSpec':{'type':'default'}," // 行号:1700
                + "'filter':{'type':'and','fields':[{'type':'bound'," // 行号:1701
                + "'dimension':'product_id','lower':'1558','lowerStrict':false," // 行号:1702
                + "'ordering':'numeric'},{'type':'or','fields':[{'type':'bound','dimension':'__time'" // 行号:1703
                + ",'lower':'10','lowerStrict':false,'upper':'10','upperStrict':false," // 行号:1704
                + "'ordering':'numeric','extractionFn':{'type':'timeFormat'," // 行号:1705
                + "'format':'M','timeZone':'UTC','locale':'en-US'}},{'type':'bound'," // 行号:1706
                + "'dimension':'__time','lower':'11','lowerStrict':false,'upper':'11'," // 行号:1707
                + "'upperStrict':false,'ordering':'numeric','extractionFn':{'type':'timeFormat'," // 行号:1708
                + "'format':'M','timeZone':'UTC','locale':'en-US'}}]}]}," // 行号:1709
                + "'aggregations':[]," // 行号:1710
                + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}")); // 行号:1711
  } // 行号:1712

  @Test void testPushOfOrderByWithMonthExtract() { // 行号:1714
    String sqlQuery = "SELECT  extract(month from \"timestamp\") as m , \"product_id\", SUM" // 行号:1715
        + "(\"unit_sales\") as s FROM \"foodmart\"" // 行号:1716
        + " WHERE \"product_id\" >= 1558" // 行号:1717
        + " GROUP BY extract(month from \"timestamp\"), \"product_id\" order by m, s, " // 行号:1718
        + "\"product_id\""; // 行号:1719
    sql(sqlQuery).queryContains( // 行号:1720
        new DruidChecker("{'queryType':'groupBy','dataSource':'foodmart'," // 行号:1721
            + "'granularity':'all','dimensions':[{'type':'extraction'," // 行号:1722
            + "'dimension':'__time','outputName':'extract_month'," // 行号:1723
            + "'extractionFn':{'type':'timeFormat','format':'M','timeZone':'UTC'," // 行号:1724
            + "'locale':'en-US'}},{'type':'default','dimension':'product_id','outputName':" // 行号:1725
            + "'product_id','outputType':'STRING'}]," // 行号:1726
            + "'limitSpec':{'type':'default','columns':[{'dimension':'extract_month'," // 行号:1727
            + "'direction':'ascending','dimensionOrder':'numeric'},{'dimension':'S'," // 行号:1728
            + "'direction':'ascending','dimensionOrder':'numeric'}," // 行号:1729
            + "{'dimension':'product_id','direction':'ascending'," // 行号:1730
            + "'dimensionOrder':'lexicographic'}]},'filter':{'type':'bound'," // 行号:1731
            + "'dimension':'product_id','lower':'1558','lowerStrict':false," // 行号:1732
            + "'ordering':'numeric'},'aggregations':[{'type':'longSum','name':'S'," // 行号:1733
            + "'fieldName':'unit_sales'}]," // 行号:1734
            + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}")) // 行号:1735
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1736
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1737
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:1738
            + "filter=[>=(CAST($1):INTEGER, 1558)], projects=[[EXTRACT(FLAG(MONTH), $0), $1, $89]], " // 行号:1739
            + "groups=[{0, 1}], aggs=[[SUM($2)]], sort0=[0], sort1=[2], sort2=[1], " // 行号:1740
            + "dir0=[ASC], dir1=[ASC], dir2=[ASC])"); // 行号:1741
  } // 行号:1742


  @Test void testGroupByFloorTimeWithoutLimit() { // 行号:1745
    final String sql = "select cast(floor(\"timestamp\" to MONTH) as timestamp) as \"month\"\n" // 行号:1746
        + "from \"foodmart\"\n" // 行号:1747
        + "group by floor(\"timestamp\" to MONTH)\n" // 行号:1748
        + "order by \"month\" DESC"; // 行号:1749
    sql(sql) // 行号:1750
        .queryContains(new DruidChecker("'queryType':'timeseries'", "'descending':true")) // 行号:1751
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1752
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z" // 行号:1753
            + "/2992-01-10T00:00:00.000Z]], projects=[[FLOOR($0, FLAG(MONTH))]], groups=[{0}], " // 行号:1754
            + "aggs=[[]], post_projects=[[CAST($0):TIMESTAMP(0) NOT NULL]], sort0=[0], dir0=[DESC])"); // 行号:1755

  } // 行号:1757

  @Test void testGroupByFloorTimeWithLimit() { // 行号:1759
    final String sql = "select" // 行号:1760
        + " cast(floor(\"timestamp\" to MONTH) as timestamp) as \"floorOfMonth\"\n" // 行号:1761
        + "from \"foodmart\"\n" // 行号:1762
        + "group by floor(\"timestamp\" to MONTH)\n" // 行号:1763
        + "order by \"floorOfMonth\" DESC LIMIT 3"; // 行号:1764
    final String explain = // 行号:1765
        "PLAN=EnumerableInterpreter\n" // 行号:1766
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:1767
            + "2992-01-10T00:00:00.000Z]], projects=[[FLOOR($0, FLAG(MONTH))]], groups=[{0}], " // 行号:1768
            + "aggs=[[]], post_projects=[[CAST($0):TIMESTAMP(0) NOT NULL]], sort0=[0], dir0=[DESC], fetch=[3])"; // 行号:1769
    sql(sql) // 行号:1770
        .explainContains(explain) // 行号:1771
        .returnsOrdered("floorOfMonth=1997-12-01 00:00:00", "floorOfMonth=1997-11-01 00:00:00", // 行号:1772
            "floorOfMonth=1997-10-01 00:00:00") // 行号:1773
        .queryContains(new DruidChecker("'queryType':'groupBy'", "'direction':'descending'")); // 行号:1774
  } // 行号:1775

  @Test void testPushofOrderByYearWithYearMonthExtract() { // 行号:1777
    String sqlQuery = "SELECT year(\"timestamp\") as y, extract(month from \"timestamp\") as m , " // 行号:1778
        + "\"product_id\", SUM" // 行号:1779
        + "(\"unit_sales\") as s FROM \"foodmart\"" // 行号:1780
        + " WHERE \"product_id\" >= 1558" // 行号:1781
        + " GROUP BY year(\"timestamp\"), extract(month from \"timestamp\"), \"product_id\" order" // 行号:1782
        + " by y DESC, m ASC, s DESC, \"product_id\" LIMIT 3"; // 行号:1783
    final String expectedPlan = "PLAN=EnumerableInterpreter\n" // 行号:1784
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1785
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:1786
        + "filter=[>=(CAST($1):INTEGER, 1558)], projects=[[EXTRACT(FLAG(YEAR), $0), " // 行号:1787
        + "EXTRACT(FLAG(MONTH), $0), $1, $89]], groups=[{0, 1, 2}], aggs=[[SUM($3)]], sort0=[0], " // 行号:1788
        + "sort1=[1], sort2=[3], sort3=[2], dir0=[DESC], " // 行号:1789
        + "dir1=[ASC], dir2=[DESC], dir3=[ASC], fetch=[3])"; // 行号:1790
    final String expectedDruidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:1791
        + "'granularity':'all','dimensions':[{'type':'extraction'," // 行号:1792
        + "'dimension':'__time','outputName':'extract_year'," // 行号:1793
        + "'extractionFn':{'type':'timeFormat','format':'yyyy','timeZone':'UTC'," // 行号:1794
        + "'locale':'en-US'}},{'type':'extraction','dimension':'__time'," // 行号:1795
        + "'outputName':'extract_month','extractionFn':{'type':'timeFormat'," // 行号:1796
        + "'format':'M','timeZone':'UTC','locale':'en-US'}},{'type':'default'," // 行号:1797
        + "'dimension':'product_id','outputName':'product_id','outputType':'STRING'}]," // 行号:1798
        + "'limitSpec':{'type':'default','limit':3," // 行号:1799
        + "'columns':[{'dimension':'extract_year','direction':'descending'," // 行号:1800
        + "'dimensionOrder':'numeric'},{'dimension':'extract_month'," // 行号:1801
        + "'direction':'ascending','dimensionOrder':'numeric'},{'dimension':'S'," // 行号:1802
        + "'direction':'descending','dimensionOrder':'numeric'}," // 行号:1803
        + "{'dimension':'product_id','direction':'ascending'," // 行号:1804
        + "'dimensionOrder':'lexicographic'}]},'filter':{'type':'bound'," // 行号:1805
        + "'dimension':'product_id','lower':'1558','lowerStrict':false," // 行号:1806
        + "'ordering':'numeric'},'aggregations':[{'type':'longSum','name':'S'," // 行号:1807
        + "'fieldName':'unit_sales'}]," // 行号:1808
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:1809
    sql(sqlQuery).explainContains(expectedPlan).queryContains(new DruidChecker(expectedDruidQuery)) // 行号:1810
        .returnsOrdered("Y=1997; M=1; product_id=1558; S=6", "Y=1997; M=1; product_id=1559; S=6", // 行号:1811
            "Y=1997; M=2; product_id=1558; S=24"); // 行号:1812
  } // 行号:1813

  @Test void testPushofOrderByMetricWithYearMonthExtract() { // 行号:1815
    String sqlQuery = "SELECT year(\"timestamp\") as y, extract(month from \"timestamp\") as m , " // 行号:1816
        + "\"product_id\", SUM(\"unit_sales\") as s FROM \"foodmart\"" // 行号:1817
        + " WHERE \"product_id\" >= 1558" // 行号:1818
        + " GROUP BY year(\"timestamp\"), extract(month from \"timestamp\"), \"product_id\" order" // 行号:1819
        + " by s DESC, m DESC, \"product_id\" LIMIT 3"; // 行号:1820
    final String expectedPlan = "PLAN=EnumerableInterpreter\n" // 行号:1821
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:1822
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:1823
        + "filter=[>=(CAST($1):INTEGER, 1558)], projects=[[EXTRACT(FLAG(YEAR), $0), " // 行号:1824
        + "EXTRACT(FLAG(MONTH), $0), $1, $89]], groups=[{0, 1, 2}], aggs=[[SUM($3)]], " // 行号:1825
        + "sort0=[3], sort1=[1], sort2=[2], dir0=[DESC], dir1=[DESC], dir2=[ASC], fetch=[3])"; // 行号:1826
    final String expectedDruidQueryType = "'queryType':'groupBy'"; // 行号:1827
    sql(sqlQuery) // 行号:1828
        .returnsOrdered("Y=1997; M=12; product_id=1558; S=30", "Y=1997; M=3; product_id=1558; S=29", // 行号:1829
            "Y=1997; M=5; product_id=1558; S=27") // 行号:1830
        .explainContains(expectedPlan) // 行号:1831
        .queryContains(new DruidChecker(expectedDruidQueryType)); // 行号:1832
  } // 行号:1833

  @Test void testGroupByTimeSortOverMetrics() { // 行号:1835
    final String sqlQuery = "SELECT count(*) as c , SUM(\"unit_sales\") as s," // 行号:1836
        + " cast(floor(\"timestamp\" to month) as timestamp)" // 行号:1837
        + " FROM \"foodmart\" group by floor(\"timestamp\" to month) order by s DESC"; // 行号:1838
    sql(sqlQuery) // 行号:1839
        .returnsOrdered("C=8716; S=26796; EXPR$2=1997-12-01 00:00:00", // 行号:1840
            "C=8231; S=25270; EXPR$2=1997-11-01 00:00:00", // 行号:1841
            "C=7752; S=23763; EXPR$2=1997-07-01 00:00:00", // 行号:1842
            "C=7710; S=23706; EXPR$2=1997-03-01 00:00:00", // 行号:1843
            "C=7038; S=21697; EXPR$2=1997-08-01 00:00:00", // 行号:1844
            "C=7033; S=21628; EXPR$2=1997-01-01 00:00:00", // 行号:1845
            "C=6912; S=21350; EXPR$2=1997-06-01 00:00:00", // 行号:1846
            "C=6865; S=21081; EXPR$2=1997-05-01 00:00:00", // 行号:1847
            "C=6844; S=20957; EXPR$2=1997-02-01 00:00:00", // 行号:1848
            "C=6662; S=20388; EXPR$2=1997-09-01 00:00:00", // 行号:1849
            "C=6588; S=20179; EXPR$2=1997-04-01 00:00:00", // 行号:1850
            "C=6478; S=19958; EXPR$2=1997-10-01 00:00:00") // 行号:1851
        .queryContains(new DruidChecker("'queryType':'groupBy'")) // 行号:1852
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:1853
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:1854
            + "2992-01-10T00:00:00.000Z]], projects=[[FLOOR($0, FLAG(MONTH)), $89]], groups=[{0}], " // 行号:1855
            + "aggs=[[COUNT(), SUM($1)]], post_projects=[[$1, $2, CAST($0):TIMESTAMP(0) NOT NULL]]," // 行号:1856
            + " sort0=[1], dir0=[DESC])"); // 行号:1857
  } // 行号:1858

  @Test void testNumericOrderingOfOrderByOperatorFullTime() { // 行号:1860
    final String sqlQuery = "SELECT cast(\"timestamp\" as timestamp) as \"timestamp\"," // 行号:1861
        + " count(*) as c, SUM(\"unit_sales\") as s FROM " // 行号:1862
        + "\"foodmart\" group by \"timestamp\" order by \"timestamp\" DESC, c DESC, s LIMIT 5"; // 行号:1863
    final String druidSubQuery = "'limitSpec':{'type':'default','limit':5," // 行号:1864
        + "'columns':[{'dimension':'extract','direction':'descending'," // 行号:1865
        + "'dimensionOrder':'lexicographic'},{'dimension':'C'," // 行号:1866
        + "'direction':'descending','dimensionOrder':'numeric'},{'dimension':'S'," // 行号:1867
        + "'direction':'ascending','dimensionOrder':'numeric'}]}," // 行号:1868
        + "'aggregations':[{'type':'count','name':'C'},{'type':'longSum'," // 行号:1869
        + "'name':'S','fieldName':'unit_sales'}]"; // 行号:1870
    sql(sqlQuery) // 行号:1871
        .returnsOrdered("timestamp=1997-12-30 00:00:00; C=22; S=36", // 行号:1872
            "timestamp=1997-12-29 00:00:00; C=321; S=982", // 行号:1873
            "timestamp=1997-12-28 00:00:00; C=480; S=1496", // 行号:1874
            "timestamp=1997-12-27 00:00:00; C=363; S=1156", // 行号:1875
            "timestamp=1997-12-26 00:00:00; C=144; S=420") // 行号:1876
        .queryContains(new DruidChecker(druidSubQuery)); // 行号:1877
  } // 行号:1878

  @Test void testNumericOrderingOfOrderByOperatorTimeExtract() { // 行号:1880
    final String sqlQuery = "SELECT extract(day from \"timestamp\") as d, extract(month from " // 行号:1881
        + "\"timestamp\") as m,  year(\"timestamp\") as y , count(*) as c, SUM(\"unit_sales\")  " // 行号:1882
        + "as s FROM " // 行号:1883
        + "\"foodmart\" group by  extract(day from \"timestamp\"), extract(month from \"timestamp\"), " // 行号:1884
        + "year(\"timestamp\")  order by d DESC, m ASC, y DESC LIMIT 5"; // 行号:1885
    final String druidSubQuery = "'limitSpec':{'type':'default','limit':5," // 行号:1886
        + "'columns':[{'dimension':'extract_day','direction':'descending'," // 行号:1887
        + "'dimensionOrder':'numeric'},{'dimension':'extract_month'," // 行号:1888
        + "'direction':'ascending','dimensionOrder':'numeric'}," // 行号:1889
        + "{'dimension':'extract_year','direction':'descending'," // 行号:1890
        + "'dimensionOrder':'numeric'}]}"; // 行号:1891
    sql(sqlQuery) // 行号:1892
        .returnsOrdered("D=30; M=3; Y=1997; C=114; S=351", // 行号:1893
            "D=30; M=5; Y=1997; C=24; S=34", // 行号:1894
            "D=30; M=6; Y=1997; C=73; S=183", // 行号:1895
            "D=30; M=7; Y=1997; C=29; S=54", // 行号:1896
            "D=30; M=8; Y=1997; C=137; S=422") // 行号:1897
        .queryContains(new DruidChecker(druidSubQuery)); // 行号:1898
  } // 行号:1899

  @Test void testNumericOrderingOfOrderByOperatorStringDims() { // 行号:1901
    final String sqlQuery = "SELECT \"brand_name\", count(*) as c, SUM(\"unit_sales\")  " // 行号:1902
        + "as s FROM " // 行号:1903
        + "\"foodmart\" group by \"brand_name\" order by \"brand_name\"  DESC LIMIT 5"; // 行号:1904
    final String druidSubQuery = "'limitSpec':{'type':'default','limit':5," // 行号:1905
        + "'columns':[{'dimension':'brand_name','direction':'descending'," // 行号:1906
        + "'dimensionOrder':'lexicographic'}]}"; // 行号:1907
    sql(sqlQuery) // 行号:1908
        .returnsOrdered("brand_name=Washington; C=576; S=1775", // 行号:1909
            "brand_name=Walrus; C=457; S=1399", // 行号:1910
            "brand_name=Urban; C=299; S=924", // 行号:1911
            "brand_name=Tri-State; C=2339; S=7270", // 行号:1912
            "brand_name=Toucan; C=123; S=380") // 行号:1913
        .queryContains(new DruidChecker(druidSubQuery)); // 行号:1914
  } // 行号:1915

  @Test void testGroupByWeekExtract() { // 行号:1917
    final String sql = "SELECT extract(week from \"timestamp\") from \"foodmart\" where " // 行号:1918
        + "\"product_id\" = 1558 and extract(week from \"timestamp\") IN (10, 11) group by extract" // 行号:1919
        + "(week from \"timestamp\")"; // 行号:1920

    final String druidQuery = "{'queryType':'groupBy','dataSource':'foodmart'," // 行号:1922
        + "'granularity':'all','dimensions':[{'type':'extraction'," // 行号:1923
        + "'dimension':'__time','outputName':'extract_week'," // 行号:1924
        + "'extractionFn':{'type':'timeFormat','format':'w','timeZone':'UTC'," // 行号:1925
        + "'locale':'en-US'}}],'limitSpec':{'type':'default'}," // 行号:1926
        + "'filter':{'type':'and','fields':[{'type':'bound','dimension':'product_id'," // 行号:1927
        + "'lower':'1558','lowerStrict':false,'upper':'1558','upperStrict':false," // 行号:1928
        + "'ordering':'numeric'},{'type':'or'," // 行号:1929
        + "'fields':[{'type':'bound','dimension':'__time','lower':'10','lowerStrict':false," // 行号:1930
        + "'upper':'10','upperStrict':false,'ordering':'numeric'," // 行号:1931
        + "'extractionFn':{'type':'timeFormat','format':'w','timeZone':'UTC'," // 行号:1932
        + "'locale':'en-US'}},{'type':'bound','dimension':'__time','lower':'11','lowerStrict':false," // 行号:1933
        + "'upper':'11','upperStrict':false,'ordering':'numeric'," // 行号:1934
        + "'extractionFn':{'type':'timeFormat','format':'w'," // 行号:1935
        + "'timeZone':'UTC','locale':'en-US'}}]}]}," // 行号:1936
        + "'aggregations':[]," // 行号:1937
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}"; // 行号:1938
    sql(sql) // 行号:1939
        .returnsOrdered("EXPR$0=10", // 行号:1940
            "EXPR$0=11") // 行号:1941
        .queryContains(new DruidChecker(druidQuery)); // 行号:1942
  } // 行号:1943

  /** Test case for // 行号:1945
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1765">[CALCITE-1765]
   * Druid adapter: Gracefully handle granularity that cannot be pushed to // 行号:1947
   * extraction function</a>. */
  @Test void testTimeExtractThatCannotBePushed() { // 行号:1949
    final String sql = "SELECT extract(CENTURY from \"timestamp\") from \"foodmart\" where " // 行号:1950
        + "\"product_id\" = 1558 group by extract(CENTURY from \"timestamp\")"; // 行号:1951
    final String plan = "PLAN=" // 行号:1952
        + "EnumerableAggregate(group=[{0}])\n" // 行号:1953
        + "  EnumerableInterpreter\n" // 行号:1954
        + "    BindableProject(EXPR$0=[EXTRACT(FLAG(CENTURY), $0)])\n" // 行号:1955
        + "      DruidQuery(table=[[foodmart, foodmart]], " // 行号:1956
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:1957
        + "filter=[=(CAST($1):INTEGER, 1558)], projects=[[$0]])\n"; // 行号:1958
    sql(sql).explainContains(plan).queryContains(new DruidChecker("'queryType':'scan'")) // 行号:1959
        .returnsUnordered("EXPR$0=20"); // 行号:1960
  } // 行号:1961

  /** Test case for // 行号:1963
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1770">[CALCITE-1770]
   * Druid adapter: CAST(NULL AS ...) gives NPE</a>. */
  @Test void testPushCast() { // 行号:1966
    final String sql = "SELECT \"product_id\"\n" // 行号:1967
        + "from \"foodmart\"\n" // 行号:1968
        + "where \"product_id\" = cast(NULL as varchar)\n" // 行号:1969
        + "group by \"product_id\" order by \"product_id\" limit 5"; // 行号:1970
    final String plan = "EnumerableValues(tuples=[[]])"; // 行号:1971
    sql(sql).explainContains(plan); // 行号:1972
  } // 行号:1973

  @Test void testFalseFilter() { // 行号:1975
    String sql = "Select count(*) as c from \"foodmart\" where false"; // 行号:1976
    final String plan = "EnumerableAggregate(group=[{}], C=[COUNT()])\n" // 行号:1977
        + "  EnumerableValues(tuples=[[]])"; // 行号:1978
    sql(sql) // 行号:1979
        .explainContains(plan) // 行号:1980
        .returnsUnordered("C=0"); // 行号:1981
  } // 行号:1982

  @Test void testTrueFilter() { // 行号:1984
    String sql = "Select count(*) as c from \"foodmart\" where true"; // 行号:1985
    sql(sql).returnsUnordered("C=86829"); // 行号:1986
  } // 行号:1987

  @Test void testFalseFilterCaseConjectionWithTrue() { // 行号:1989
    String sql = "Select count(*) as c from \"foodmart\" where " // 行号:1990
        + "\"product_id\" = 1558 and (true or false)"; // 行号:1991
    sql(sql).returnsUnordered("C=60").queryContains(new DruidChecker("'queryType':'timeseries'")); // 行号:1992
  } // 行号:1993

  /** Test case for // 行号:1995
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1769">[CALCITE-1769]
   * Druid adapter: Push down filters involving numeric cast of literals</a>. */
  @Test void testPushCastNumeric() { // 行号:1998
    String druidQuery = "'filter':{'type':'bound','dimension':'product_id'," // 行号:1999
        + "'upper':'10','upperStrict':true,'ordering':'numeric'}"; // 行号:2000
    fixture() // 行号:2001
        .withModel(FOODMART) // 设置FOODMART模型
        .withRel(b -> { // 行号:2003
          // select product_id
          // from foodmart.foodmart
          // where product_id < cast(10 as varchar)
          final RelDataType intType = // 行号:2007
              b.getTypeFactory().createSqlType(SqlTypeName.INTEGER); // 行号:2008
          return b.scan("foodmart", "foodmart") // 行号:2009
              .filter( // 行号:2010
                  b.call(SqlStdOperatorTable.LESS_THAN, // 行号:2011
                      b.getRexBuilder().makeCall(intType, // 行号:2012
                          SqlStdOperatorTable.CAST, // 行号:2013
                          ImmutableList.of(b.field("product_id"))), // 行号:2014
                      b.getRexBuilder().makeCall(intType, // 行号:2015
                          SqlStdOperatorTable.CAST, // 行号:2016
                          ImmutableList.of(b.literal("10"))))) // 行号:2017
              .project(b.field("product_id")) // 行号:2018
              .build(); // 行号:2019
        }) // 行号:2020
        .queryContains(new DruidChecker(druidQuery)); // 行号:2021
  } // 行号:2022

  @Test void testPushFieldEqualsLiteral() { // 行号:2024
    fixture() // 行号:2025
        .withModel(FOODMART) // 设置FOODMART模型
        .withRel(b -> { // 行号:2027
          // select count(*) as c
          // from foodmart.foodmart
          // where product_id = 'id'
          return b.scan("foodmart", "foodmart") // 行号:2031
              .filter( // 行号:2032
                  b.call(SqlStdOperatorTable.EQUALS, b.field("product_id"), // 行号:2033
                      b.literal("id"))) // 行号:2034
              .aggregate(b.groupKey(), b.countStar("c")) // 行号:2035
              .build(); // 行号:2036
        }) // 行号:2037
        // Should return one row, "c=0"; logged
        // [CALCITE-1775] "GROUP BY ()" on empty relation should return 1 row
        .returnsUnordered("c=0") // 行号:2040
        .queryContains(new DruidChecker("'queryType':'timeseries'")); // 行号:2041
  } // 行号:2042

  @Test void testPlusArithmeticOperation() { // 行号:2044
    final String sqlQuery = "select sum(\"store_sales\") + sum(\"store_cost\") as a, " // 行号:2045
        + "\"store_state\" from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2046
    String postAggString = "type':'expression','name':'A','expression':'(\\'$f1\\' + \\'$f2\\')'}]"; // 行号:2047
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2048
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2049
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $90, $91]], groups=[{0}], " // 行号:2050
        + "aggs=[[SUM($1), SUM($2)]], post_projects=[[+($1, $2), $0]], sort0=[0], dir0=[DESC])"; // 行号:2051
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2052
        .explainContains(plan) // 行号:2053
        .queryContains(new DruidChecker(postAggString)); // 行号:2054
    q.returnsOrdered("A=369117.5279; store_state=WA", // 行号:2055
        "A=222698.2651; store_state=CA", // 行号:2056
        "A=199049.5706; store_state=OR"); // 行号:2057
  } // 行号:2058

  @Test void testDivideArithmeticOperation() { // 行号:2060
    final String sqlQuery = "select \"store_state\", sum(\"store_sales\") / sum(\"store_cost\") " // 行号:2061
        + "as a from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2062
    String postAggString = "[{'type':'expression','name':'A','expression':'(\\'$f1\\' / \\'$f2\\')"; // 行号:2063
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2064
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2065
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $90, $91]], groups=[{0}], " // 行号:2066
        + "aggs=[[SUM($1), SUM($2)]], post_projects=[[$0, /($1, $2)]], sort0=[1], dir0=[DESC])"; // 行号:2067
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2068
        .explainContains(plan) // 行号:2069
        .queryContains(new DruidChecker(postAggString)); // 行号:2070
    Assumptions.assumeTrue(Bug.CALCITE_4204_FIXED, "CALCITE-4204"); // 行号:2071
    q.returnsOrdered("store_state=OR; A=2.506091302943239", // 行号:2072
        "store_state=CA; A=2.505379741272971", // 行号:2073
        "store_state=WA; A=2.5045806163801996"); // 行号:2074
  } // 行号:2075

  @Test void testMultiplyArithmeticOperation() { // 行号:2077
    final String sqlQuery = "select \"store_state\", sum(\"store_sales\") * sum(\"store_cost\") " // 行号:2078
        + "as a from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2079
    String postAggString = "{'type':'expression','name':'A','expression':'(\\'$f1\\' * \\'$f2\\')'"; // 行号:2080
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2081
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2082
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $90, $91]], groups=[{0}], aggs=[[SUM($1)," // 行号:2083
        + " SUM($2)]], post_projects=[[$0, *($1, $2)]], sort0=[1], dir0=[DESC])"; // 行号:2084
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2085
        .explainContains(plan) // 行号:2086
        .queryContains(new DruidChecker(postAggString)); // 行号:2087
    Assumptions.assumeTrue(Bug.CALCITE_4204_FIXED, "CALCITE-4204"); // 行号:2088
    q.returnsOrdered("store_state=WA; A=2.7783838325212463E10", // 行号:2089
        "store_state=CA; A=1.0112000537448784E10", // 行号:2090
        "store_state=OR; A=8.077425041941243E9"); // 行号:2091
  } // 行号:2092

  @Test void testMinusArithmeticOperation() { // 行号:2094
    final String sqlQuery = "select \"store_state\", sum(\"store_sales\") - sum(\"store_cost\") " // 行号:2095
        + "as a from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2096
    String postAggString = "'postAggregations':[{'type':'expression','name':'A'," // 行号:2097
        + "'expression':'(\\'$f1\\' - \\'$f2\\')'}]"; // 行号:2098
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2099
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2100
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $90, $91]], groups=[{0}], aggs=[[SUM($1), " // 行号:2101
        + "SUM($2)]], post_projects=[[$0, -($1, $2)]], sort0=[1], dir0=[DESC])"; // 行号:2102
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2103
        .explainContains(plan) // 行号:2104
        .queryContains(new DruidChecker(postAggString)); // 行号:2105
    q.returnsOrdered("store_state=WA; A=158468.9121", // 行号:2106
        "store_state=CA; A=95637.4149", // 行号:2107
        "store_state=OR; A=85504.5694"); // 行号:2108
  } // 行号:2109

  @Test void testConstantPostAggregator() { // 行号:2111
    final String sqlQuery = "select \"store_state\", sum(\"store_sales\") + 100 as a from " // 行号:2112
        + "\"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2113
    String postAggString = "{'type':'expression','name':'A','expression':'(\\'$f1\\' + 100)'}"; // 行号:2114
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2115
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2116
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $90]], groups=[{0}], aggs=[[SUM($1)]], " // 行号:2117
        + "post_projects=[[$0, +($1, 100)]], sort0=[1], dir0=[DESC])"; // 行号:2118
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2119
        .explainContains(plan) // 行号:2120
        .queryContains(new DruidChecker(postAggString)); // 行号:2121
    q.returnsOrdered("store_state=WA; A=263893.22", // 行号:2122
        "store_state=CA; A=159267.84", // 行号:2123
        "store_state=OR; A=142377.07"); // 行号:2124
  } // 行号:2125

  @Test void testRecursiveArithmeticOperation() { // 行号:2127
    final String sqlQuery = "select \"store_state\", -1 * (a + b) as c from (select " // 行号:2128
        + "(sum(\"store_sales\")-sum(\"store_cost\")) / (count(*) * 3) " // 行号:2129
        + "AS a,sum(\"unit_sales\") AS b, \"store_state\"  from \"foodmart\"  group " // 行号:2130
        + "by \"store_state\") order by c desc"; // 行号:2131
    String postAggString = "'postAggregations':[{'type':'expression','name':'C','expression':" // 行号:2132
        + "'(-1 * (((\\'$f1\\' - \\'$f2\\') / (\\'$f3\\' * 3)) + \\'B\\'))'}]"; // 行号:2133
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2134
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2135
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $90, $91, $89]], groups=[{0}], " // 行号:2136
        + "aggs=[[SUM($1), SUM($2), COUNT(), SUM($3)]], post_projects=[[$0, *(-1, +(/(-($1, $2), " // 行号:2137
        + "*($3, 3)), $4))]], sort0=[1], dir0=[DESC])"; // 行号:2138
    sql(sqlQuery, FOODMART) // 行号:2139
        .returnsOrdered("store_state=OR; C=-67660.31890435601", // 行号:2140
            "store_state=CA; C=-74749.30433035882", // 行号:2141
            "store_state=WA; C=-124367.29537914316") // 行号:2142
        .explainContains(plan) // 行号:2143
        .queryContains(new DruidChecker(postAggString)); // 行号:2144
  } // 行号:2145

  /** Turn on now {@code count(distinct ...)}. */
  @Test void testHyperUniquePostAggregator() { // 行号:2148
    final String sqlQuery = "select \"store_state\", sum(\"store_cost\") / count(distinct " // 行号:2149
        + "\"brand_name\") as a from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2150
    final String postAggString = "[{'type':'expression','name':'A'," // 行号:2151
        + "'expression':'(\\'$f1\\' / \\'$f2\\')'}]"; // 行号:2152
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2153
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2154
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $91, $2]], groups=[{0}], aggs=[[SUM($1), " // 行号:2155
        + "COUNT(DISTINCT $2)]], post_projects=[[$0, /($1, $2)]], sort0=[1], dir0=[DESC])"; // 行号:2156
    foodmartApprox(sqlQuery) // 行号:2157
        .runs() // 行号:2158
        .explainContains(plan) // 行号:2159
        .queryContains(new DruidChecker(postAggString)); // 行号:2160
  } // 行号:2161

  @Test void testExtractFilterWorkWithPostAggregations() { // 行号:2163
    final String sql = "SELECT \"store_state\", \"brand_name\", sum(\"store_sales\") - " // 行号:2164
        + "sum(\"store_cost\") as a  from \"foodmart\" where extract (week from \"timestamp\")" // 行号:2165
        + " IN (10,11) and \"brand_name\"='Bird Call' group by \"store_state\", \"brand_name\""; // 行号:2166
    final String druidQuery = "\"postAggregations\":[{\"type\":" // 行号:2167
        + "\"expression\",\"name\":\"brand_name\"," // 行号:2168
        + "\"expression\":\"'Bird Call'\"},{\"type\":\"expression\",\"name\":\"A\"," // 行号:2169
        + "\"expression\":\"(\\\"$f1\\\" - \\\"$f2\\\")\"}]"; // 行号:2170
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2171
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:2172
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], filter=[AND(=("; // 行号:2173
    sql(sql, FOODMART) // 行号:2174
        .explainContains(plan) // 行号:2175
        .returnsOrdered("store_state=CA; brand_name=Bird Call; A=34.3646", // 行号:2176
            "store_state=OR; brand_name=Bird Call; A=39.1636", // 行号:2177
            "store_state=WA; brand_name=Bird Call; A=53.7425") // 行号:2178
        .queryContains(new DruidChecker(false, druidQuery)); // 行号:2179
  } // 行号:2180

  @Test void testExtractFilterWorkWithPostAggregationsWithConstant() { // 行号:2182
    final String sql = "SELECT \"store_state\", 'Bird Call' as \"brand_name\", " // 行号:2183
        + "sum(\"store_sales\") - sum(\"store_cost\") as a  from \"foodmart\" " // 行号:2184
        + "where extract (week from \"timestamp\")" // 行号:2185
        + " IN (10,11) and \"brand_name\"='Bird Call' group by \"store_state\""; // 行号:2186
    final String druidQuery = "type':'expression','name':'A','expression':'(\\'$f1\\' - \\'$f2\\')"; // 行号:2187
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2188
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:2189
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:2190
        + "filter=[AND(=($2, 'Bird Call'), OR(=(EXTRACT(FLAG(WEEK), $0), 10), " // 行号:2191
        + "=(EXTRACT(FLAG(WEEK), $0), 11)))], projects=[[$63, $90, $91]], " // 行号:2192
        + "groups=[{0}], aggs=[[SUM($1), SUM($2)]], post_projects=[[$0, 'Bird Call', -($1, $2)]])"; // 行号:2193
    sql(sql, FOODMART) // 行号:2194
        .returnsOrdered("store_state=CA; brand_name=Bird Call; A=34.3646", // 行号:2195
            "store_state=OR; brand_name=Bird Call; A=39.1636", // 行号:2196
            "store_state=WA; brand_name=Bird Call; A=53.7425") // 行号:2197
        .explainContains(plan) // 行号:2198
        .queryContains(new DruidChecker(druidQuery)); // 行号:2199
  } // 行号:2200

  @Test void testSingleAverageFunction() { // 行号:2202
    final String sqlQuery = "select \"store_state\", sum(\"store_cost\") / count(*) as a from " // 行号:2203
        + "\"foodmart\" group by \"store_state\" order by a desc"; // 行号:2204
    String postAggString = "\"postAggregations\":[{\"type\":\"expression\",\"name\":\"A\"," // 行号:2205
        + "\"expression\":\"(\\\"$f1\\\" / \\\"$f2\\\")"; // 行号:2206
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2207
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2208
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $91]], groups=[{0}], " // 行号:2209
        + "aggs=[[SUM($1), COUNT()]], post_projects=[[$0, /($1, $2)]], sort0=[1], dir0=[DESC])"; // 行号:2210
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2211
        .explainContains(plan) // 行号:2212
        .queryContains(new DruidChecker(postAggString)); // 行号:2213
    Assumptions.assumeTrue(Bug.CALCITE_4204_FIXED, "CALCITE-4204"); // 行号:2214
    q.returnsOrdered("store_state=OR; A=2.6271402406293403", // 行号:2215
        "store_state=CA; A=2.599338206292706", // 行号:2216
        "store_state=WA; A=2.5828708592868717"); // 行号:2217
  } // 行号:2218

  @Test void testPartiallyPostAggregation() { // 行号:2220
    final String sqlQuery = "select \"store_state\"," // 行号:2221
        + " sum(\"store_sales\") / sum(\"store_cost\") as a," // 行号:2222
        + " case when sum(\"unit_sales\")=0 then 1.0 else sum(\"unit_sales\") end as b " // 行号:2223
        + "from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2224
    final String postAggString = "'postAggregations':[{'type':'expression','name':'A'," // 行号:2225
        + "'expression':'(\\'$f1\\' / \\'$f2\\')'},{'type':'expression','name':'B'," // 行号:2226
        + "'expression':'case_searched((\\'$f3\\' == 0),1,CAST(\\'$f3\\'"; // 行号:2227
    final String plan = // 行号:2228
        "DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2229
            + "2992-01-10T00:00:00.000Z]], projects=[[$63, $90, $91, $89]], groups=[{0}], " // 行号:2230
            + "aggs=[[SUM($1), SUM($2), SUM($3)]], post_projects=[[$0, /($1, $2), " // 行号:2231
            + "CASE(=($3, 0), 1:DECIMAL(19, 0), CAST($3):DECIMAL(19, 0))]], sort0=[1], dir0=[DESC])"; // 行号:2232
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2233
        .explainContains(plan) // 行号:2234
        .queryContains(new DruidChecker(postAggString)); // 行号:2235
    Assumptions.assumeTrue(Bug.CALCITE_4204_FIXED, "CALCITE-4204"); // 行号:2236
    q.returnsOrdered("store_state=OR; A=2.506091302943239; B=67659.0", // 行号:2237
        "store_state=CA; A=2.505379741272971; B=74748.0", // 行号:2238
        "store_state=WA; A=2.5045806163801996; B=124366.0"); // 行号:2239
  } // 行号:2240

  @Test void testDuplicateReferenceOnPostAggregation() { // 行号:2242
    final String sqlQuery = "select \"store_state\", a, a - b as c from (select \"store_state\", " // 行号:2243
        + "sum(\"store_sales\") + 100 as a, sum(\"store_cost\") as b from \"foodmart\"  group by " // 行号:2244
        + "\"store_state\") order by a desc"; // 行号:2245
    String postAggString = "[{'type':'expression','name':'A','expression':'(\\'$f1\\' + 100)'}," // 行号:2246
        + "{'type':'expression','name':'C','expression':'((\\'$f1\\' + 100) - \\'B\\')'}]"; // 行号:2247
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2248
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:2249
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:2250
        + "projects=[[$63, $90, $91]], groups=[{0}], aggs=[[SUM($1), SUM($2)]], " // 行号:2251
        + "post_projects=[[$0, +($1, 100), -(+($1, 100), $2)]], sort0=[1], dir0=[DESC])"; // 行号:2252
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2253
        .explainContains(plan) // 行号:2254
        .queryContains(new DruidChecker(postAggString)); // 行号:2255
    q.returnsOrdered("store_state=WA; A=263893.22; C=158568.9121", // 行号:2256
        "store_state=CA; A=159267.84; C=95737.4149", // 行号:2257
        "store_state=OR; A=142377.07; C=85604.5694"); // 行号:2258
  } // 行号:2259

  @Test void testDivideByZeroDoubleTypeInfinity() { // 行号:2261
    final String sqlQuery = "select \"store_state\", sum(\"store_cost\") / 0 as a from " // 行号:2262
        + "\"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2263
    String postAggString = "'type':'expression','name':'A','expression':'(\\'$f1\\' / 0)'}]"; // 行号:2264
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2265
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2266
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $91]], groups=[{0}], aggs=[[SUM($1)]], " // 行号:2267
        + "post_projects=[[$0, /($1, 0)]], sort0=[1], dir0=[DESC])"; // 行号:2268
    sql(sqlQuery, FOODMART) // 行号:2269
        .returnsOrdered("store_state=CA; A=Infinity", // 行号:2270
            "store_state=OR; A=Infinity", // 行号:2271
            "store_state=WA; A=Infinity") // 行号:2272
        .explainContains(plan) // 行号:2273
        .queryContains(new DruidChecker(postAggString)); // 行号:2274
  } // 行号:2275

  @Test void testDivideByZeroDoubleTypeNegInfinity() { // 行号:2277
    final String sqlQuery = "select \"store_state\", -1.0 * sum(\"store_cost\") / 0 as " // 行号:2278
        + "a from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2279
    String postAggString = "\"postAggregations\":[{\"type\":\"expression\",\"name\":\"A\"," // 行号:2280
        + "\"expression\":\"((-1.0 * \\\"$f1\\\") / 0)\"}],"; // 行号:2281
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2282
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2283
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $91]], groups=[{0}], aggs=[[SUM($1)]], " // 行号:2284
        + "post_projects=[[$0, /(*(-1.0:DECIMAL(2, 1), $1), 0)]], sort0=[1], dir0=[DESC])"; // 行号:2285
    sql(sqlQuery, FOODMART) // 行号:2286
        .returnsOrdered("store_state=CA; A=-Infinity", // 行号:2287
            "store_state=OR; A=-Infinity", // 行号:2288
            "store_state=WA; A=-Infinity") // 行号:2289
        .explainContains(plan) // 行号:2290
        .queryContains(new DruidChecker(postAggString)); // 行号:2291
  } // 行号:2292

  @Test void testDivideByZeroDoubleTypeNaN() { // 行号:2294
    final String sqlQuery = "select \"store_state\", (sum(\"store_cost\") - sum(\"store_cost\")) " // 行号:2295
        + "/ 0 as a from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2296
    final String postAggString = "'postAggregations':[{'type':'expression','name':'A'," // 行号:2297
        + "'expression':'((\\'$f1\\' - \\'$f1\\') / 0)'}"; // 行号:2298
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2299
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2300
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $91]], groups=[{0}], aggs=[[SUM($1)]], " // 行号:2301
        + "post_projects=[[$0, /(-($1, $1), 0)]], sort0=[1], dir0=[DESC])"; // 行号:2302
    sql(sqlQuery, FOODMART) // 行号:2303
        .returnsOrdered("store_state=CA; A=NaN", // 行号:2304
            "store_state=OR; A=NaN", // 行号:2305
            "store_state=WA; A=NaN") // 行号:2306
        .explainContains(plan) // 行号:2307
        .queryContains(new DruidChecker(postAggString)); // 行号:2308
  } // 行号:2309

  @Test void testDivideByZeroIntegerType() { // 行号:2311
    final String sqlQuery = "select \"store_state\"," // 行号:2312
        + " (count(*) - count(*)) / 0 as a " // 行号:2313
        + "from \"foodmart\"  group by \"store_state\" order by a desc"; // 行号:2314
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2315
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2316
        + "2992-01-10T00:00:00.000Z]], projects=[[$63]], groups=[{0}], aggs=[[COUNT()]], " // 行号:2317
        + "post_projects=[[$0, /(-($1, $1), 0)]], sort0=[1], dir0=[DESC])"; // 行号:2318
    sql(sqlQuery, FOODMART) // 行号:2319
        .explainContains(plan) // 行号:2320
        .throws_("Server returned HTTP response code: 500"); // 行号:2321
    // TODO It seems like calcite is not handling 500 error,
    // need to catch it and parse exception message from druid,
    // e.g., throws_("/ by zero");
  } // 行号:2325

  @Test void testInterleaveBetweenAggregateAndGroupOrderByOnMetrics() { // 行号:2327
    final String sqlQuery = "select \"store_state\", \"brand_name\", \"A\" " // 行号:2328
        + "from (\n" // 行号:2329
        + "  select sum(\"store_sales\")-sum(\"store_cost\") as a, \"store_state\"" // 行号:2330
        + ", \"brand_name\"\n" // 行号:2331
        + "  from \"foodmart\"\n" // 行号:2332
        + "  group by \"store_state\", \"brand_name\" ) subq\n" // 行号:2333
        + "order by \"A\" limit 5"; // 行号:2334
    String postAggString = "\"postAggregations\":[{\"type\":\"expression\",\"name\":\"A\"," // 行号:2335
        + "\"expression\":\"(\\\"$f2\\\" - \\\"$f3\\\")\"}"; // 行号:2336
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2337
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2338
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $2, $90, $91]], groups=[{0, 1}], " // 行号:2339
        + "aggs=[[SUM($2), SUM($3)]], post_projects=[[$0, $1, -($2, $3)]], sort0=[2], dir0=[ASC], " // 行号:2340
        + "fetch=[5])"; // 行号:2341
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2342
        .explainContains(plan) // 行号:2343
        .queryContains(new DruidChecker(postAggString)); // 行号:2344
    q.returnsOrdered("store_state=CA; brand_name=King; A=21.4632", // 行号:2345
        "store_state=OR; brand_name=Symphony; A=32.176", // 行号:2346
        "store_state=CA; brand_name=Toretti; A=32.2465", // 行号:2347
        "store_state=WA; brand_name=King; A=34.6104", // 行号:2348
        "store_state=OR; brand_name=Toretti; A=36.3"); // 行号:2349
  } // 行号:2350

  @Test void testInterleaveBetweenAggregateAndGroupOrderByOnDimension() { // 行号:2352
    final String sqlQuery = "select \"store_state\", \"brand_name\", \"A\" " // 行号:2353
        + "from\n" // 行号:2354
        + "(select \"store_state\", sum(\"store_sales\")+sum(\"store_cost\") " // 行号:2355
        + "as a, \"brand_name\" from \"foodmart\" group by \"store_state\", \"brand_name\") " // 行号:2356
        + "order by \"brand_name\", \"store_state\" limit 5"; // 行号:2357
    final String postAggString = "'postAggregations':[{'type':'expression','name':'A'," // 行号:2358
        + "'expression':'(\\'$f2\\' + \\'$f3\\')'}]"; // 行号:2359
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2360
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2361
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $2, $90, $91]], groups=[{0, 1}], " // 行号:2362
        + "aggs=[[SUM($2), SUM($3)]], post_projects=[[$0, $1, +($2, $3)]], " // 行号:2363
        + "sort0=[1], sort1=[0], dir0=[ASC], dir1=[ASC], fetch=[5])"; // 行号:2364
    CalciteAssert.AssertQuery q = sql(sqlQuery, FOODMART) // 行号:2365
        .explainContains(plan) // 行号:2366
        .queryContains(new DruidChecker(postAggString)); // 行号:2367
    q.returnsOrdered("store_state=CA; brand_name=ADJ; A=222.1524", // 行号:2368
        "store_state=OR; brand_name=ADJ; A=186.6036", // 行号:2369
        "store_state=WA; brand_name=ADJ; A=216.9912", // 行号:2370
        "store_state=CA; brand_name=Akron; A=250.349", // 行号:2371
        "store_state=OR; brand_name=Akron; A=278.6972"); // 行号:2372
  } // 行号:2373

  @Test void testOrderByOnMetricsInSelectDruidQuery() { // 行号:2375
    final String sqlQuery = "select" // 行号:2376
        + " \"store_sales\" as a, \"store_cost\" as b," // 行号:2377
        + " \"store_sales\" - \"store_cost\" as c " // 行号:2378
        + "from \"foodmart\" " // 行号:2379
        + "where \"timestamp\" >= '1997-01-01 00:00:00 UTC' " // 行号:2380
        + "and \"timestamp\" < '1997-09-01 00:00:00 UTC' " // 行号:2381
        + "order by c limit 5"; // 行号:2382
    String queryType = "'queryType':'scan'"; // 行号:2383
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:2384
        + "  BindableSort(sort0=[$2], dir0=[ASC], fetch=[5])\n" // 行号:2385
        + "    DruidQuery(table=[[foodmart, foodmart]], " // 行号:2386
        + "intervals=[[1997-01-01T00:00:00.000Z/1997-09-01T00:00:00.000Z]], " // 行号:2387
        + "projects=[[$90, $91, -($90, $91)]])"; // 行号:2388
    sql(sqlQuery, FOODMART) // 行号:2389
        .returnsOrdered("A=0.51; B=0.2448; C=0.2652", // 行号:2390
            "A=0.51; B=0.2397; C=0.2703", // 行号:2391
            "A=0.57; B=0.285; C=0.285", // 行号:2392
            "A=0.5; B=0.21; C=0.29", // 行号:2393
            "A=0.57; B=0.2793; C=0.2907") // 行号:2394
        .explainContains(plan) // 行号:2395
        .queryContains(new DruidChecker(queryType)); // 行号:2396
  } // 行号:2397

  /** Tests whether an aggregate with a filter clause has its filter factored out // 行号:2399
   * when there is no outer filter. */
  @Test void testFilterClauseFactoredOut() { // 行号:2401
    // Logically equivalent to
    // select sum("store_sales") from "foodmart" where "the_year" >= 1997
    String sql = "select sum(\"store_sales\") " // 行号:2404
        + "filter (where \"the_year\" >= 1997) from \"foodmart\""; // 行号:2405
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2406
        + "'granularity':'all','filter':{'type':'bound','dimension':'the_year','lower':'1997'," // 行号:2407
        + "'lowerStrict':false,'ordering':'numeric'},'aggregations':[{'type':'doubleSum','name'" // 行号:2408
        + ":'EXPR$0','fieldName':'store_sales'}],'intervals':['1900-01-09T00:00:00.000Z/2992-01" // 行号:2409
        + "-10T00:00:00.000Z'],'context':{'skipEmptyBuckets':false}}"; // 行号:2410

    sql(sql).queryContains(new DruidChecker(expectedQuery)); // 行号:2412
  } // 行号:2413

  /** Tests whether filter clauses with filters that are always true // 行号:2415
   * disappear. */
  @Test void testFilterClauseAlwaysTrueGone() { // 行号:2417
    // Logically equivalent to
    // select sum("store_sales") from "foodmart"
    String sql = "select sum(\"store_sales\") filter (where 1 = 1) from \"foodmart\""; // 行号:2420
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2421
        + "'granularity':'all','aggregations':[{'type':'doubleSum','name':'EXPR$0','fieldName':" // 行号:2422
        + "'store_sales'}],'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2423
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2424

    sql(sql).queryContains(new DruidChecker(expectedQuery)); // 行号:2426
  } // 行号:2427

  /** Tests whether filter clauses with filters that are always true disappear // 行号:2429
   * in the presence of another aggregate without a filter clause. */
  @Test void testFilterClauseAlwaysTrueWithAggGone1() { // 行号:2431
    // Logically equivalent to
    // select sum("store_sales"), sum("store_cost") from "foodmart"
    String sql = "select sum(\"store_sales\") filter (where 1 = 1), " // 行号:2434
        + "sum(\"store_cost\") from \"foodmart\""; // 行号:2435
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2436
        + "'granularity':'all','aggregations':[{'type':'doubleSum','name':'EXPR$0','fieldName':" // 行号:2437
        + "'store_sales'},{'type':'doubleSum','name':'EXPR$1','fieldName':'store_cost'}]," // 行号:2438
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2439
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2440

    sql(sql).queryContains(new DruidChecker(expectedQuery)); // 行号:2442
  } // 行号:2443

  /** Tests whether filter clauses with filters that are always true disappear // 行号:2445
   * in the presence of another aggregate with a filter clause. */
  @Test void testFilterClauseAlwaysTrueWithAggGone2() { // 行号:2447
    // Logically equivalent to
    // select sum("store_sales"),
    // sum("store_cost") filter (where "store_state" = 'CA') from "foodmart"
    String sql = "select sum(\"store_sales\") filter (where 1 = 1), " // 行号:2451
        + "sum(\"store_cost\") filter (where \"store_state\" = 'CA') " // 行号:2452
        + "from \"foodmart\""; // 行号:2453
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2454
        + "'granularity':'all','aggregations':[{'type':'doubleSum','name':'EXPR$0','fieldName'" // 行号:2455
        + ":'store_sales'},{'type':'filtered','filter':{'type':'selector','dimension':" // 行号:2456
        + "'store_state','value':'CA'},'aggregator':{'type':'doubleSum','name':'EXPR$1'," // 行号:2457
        + "'fieldName':'store_cost'}}],'intervals':" // 行号:2458
        + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2459
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2460

    sql(sql).queryContains(new DruidChecker(expectedQuery)); // 行号:2462
  } // 行号:2463

  /** Tests whether an existing outer filter is untouched when an aggregate has // 行号:2465
   * a filter clause that is always true. */
  @Test void testOuterFilterRemainsWithAlwaysTrueClause() { // 行号:2467
    // Logically equivalent to
    // select sum("store_sales"), sum("store_cost") from "foodmart" where "store_city" = 'Seattle'
    String sql = "select sum(\"store_sales\") filter (where 1 = 1), sum(\"store_cost\") " // 行号:2470
        + "from \"foodmart\" where \"store_city\" = 'Seattle'"; // 行号:2471
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2472
        + "'granularity':'all','filter':{'type':'selector','dimension':'store_city'," // 行号:2473
        + "'value':'Seattle'},'aggregations':[{'type':'doubleSum','name':'EXPR$0'," // 行号:2474
        + "'fieldName':'store_sales'},{'type':'doubleSum','name':'EXPR$1'," // 行号:2475
        + "'fieldName':'store_cost'}],'intervals':" // 行号:2476
        + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2477
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2478

    sql(sql).queryContains(new DruidChecker(expectedQuery)); // 行号:2480
  } // 行号:2481

  /** Tests that an aggregate with a filter clause that is always false does not // 行号:2483
   * get pushed in. */
  @Test void testFilterClauseAlwaysFalseNotPushed() { // 行号:2485
    String sql = "select sum(\"store_sales\") filter (where 1 > 1) from \"foodmart\""; // 行号:2486
    // Calcite takes care of the unsatisfiable filter
    String expectedSubExplain = "PLAN=" // 行号:2488
        + "EnumerableInterpreter\n" // 行号:2489
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:2490
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:2491
        + "filter=[false], projects=[[$90, false]], groups=[{}], aggs=[[SUM($0)]])"; // 行号:2492
    sql(sql) // 行号:2493
        .queryContains( // 行号:2494
            new DruidChecker("{\"queryType\":\"timeseries\"," // 行号:2495
                + "\"dataSource\":\"foodmart\",\"descending\":false,\"granularity\":\"all\"," // 行号:2496
                + "\"filter\":{\"type\":\"expression\",\"expression\":\"1 == 2\"}," // 行号:2497
                + "\"aggregations\":[{\"type\":\"doubleSum\",\"name\":\"EXPR$0\"," // 行号:2498
                + "\"fieldName\":\"store_sales\"}]," // 行号:2499
                + "\"intervals\":[\"1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z\"]," // 行号:2500
                + "\"context\":{\"skipEmptyBuckets\":false}}")) // 行号:2501
        .explainContains(expectedSubExplain); // 行号:2502
  } // 行号:2503

  /** Tests that an aggregate with a filter clause that is always false does not // 行号:2505
   * get pushed when there is already an outer filter. */
  @Test void testFilterClauseAlwaysFalseNotPushedWithFilter() { // 行号:2507
    String sql = "select sum(\"store_sales\") filter (where 1 > 1) " // 行号:2508
        + "from \"foodmart\" where \"store_city\" = 'Seattle'"; // 行号:2509
    String expectedSubExplain = "PLAN=" // 行号:2510
        + "EnumerableInterpreter\n" // 行号:2511
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:2512
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], filter=[AND" // 行号:2513
        + "(false, =($62, 'Seattle'))], projects=[[$90, false]], groups=[{}], aggs=[[SUM" // 行号:2514
        + "($0)]])"; // 行号:2515

    sql(sql) // 行号:2517
        .explainContains(expectedSubExplain) // 行号:2518
        .queryContains( // 行号:2519
            new DruidChecker("\"filter\":{\"type" // 行号:2520
                + "\":\"and\",\"fields\":[{\"type\":\"expression\",\"expression\":\"1 == 2\"}," // 行号:2521
                + "{\"type\":\"selector\",\"dimension\":\"store_city\",\"value\":\"Seattle\"}]}")); // 行号:2522
  } // 行号:2523

  /** Tests that an aggregate with a filter clause that is the same as the outer // 行号:2525
   * filter has no references to that filter, and that the original outer filter // 行号:2526
   * remains. */
  @Test void testFilterClauseSameAsOuterFilterGone() { // 行号:2528
    // Logically equivalent to
    // select sum("store_sales") from "foodmart" where "store_city" = 'Seattle'
    String sql = "select sum(\"store_sales\") filter (where \"store_city\" = 'Seattle') " // 行号:2531
        + "from \"foodmart\" where \"store_city\" = 'Seattle'"; // 行号:2532
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2533
        + "'granularity':'all','filter':{'type':'selector','dimension':'store_city','value':" // 行号:2534
        + "'Seattle'},'aggregations':[{'type':'doubleSum','name':'EXPR$0','fieldName':" // 行号:2535
        + "'store_sales'}],'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2536
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2537

    sql(sql) // 行号:2539
        .queryContains(new DruidChecker(expectedQuery)) // 行号:2540
        .returnsUnordered("EXPR$0=52644.07"); // 行号:2541
  } // 行号:2542

  /** Tests that an aggregate with a filter clause in the presence of another // 行号:2544
   * aggregate without a filter clause does not have its filter factored out // 行号:2545
   * into the outer filter. */
  @Test void testFilterClauseNotFactoredOut1() { // 行号:2547
    String sql = "select sum(\"store_sales\") filter (where \"store_state\" = 'CA'), " // 行号:2548
        + "sum(\"store_cost\") from \"foodmart\""; // 行号:2549
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2550
        + "'granularity':'all','aggregations':[{'type':'filtered','filter':{'type':'selector'," // 行号:2551
        + "'dimension':'store_state','value':'CA'},'aggregator':{'type':'doubleSum','name':" // 行号:2552
        + "'EXPR$0','fieldName':'store_sales'}},{'type':'doubleSum','name':'EXPR$1','fieldName'" // 行号:2553
        + ":'store_cost'}],'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2554
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2555

    sql(sql).queryContains(new DruidChecker(expectedQuery)); // 行号:2557
  } // 行号:2558

  /** Tests that an aggregate with a filter clause in the presence of another // 行号:2560
   * aggregate without a filter clause, and an outer filter does not have its // 行号:2561
   * filter factored out into the outer filter. */
  @Test void testFilterClauseNotFactoredOut2() { // 行号:2563
    String sql = "select sum(\"store_sales\") filter (where \"store_state\" = 'CA'), " // 行号:2564
        + "sum(\"store_cost\") from \"foodmart\" where \"the_year\" >= 1997"; // 行号:2565
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2566
        + "'granularity':'all','filter':{'type':'bound','dimension':'the_year','lower':'1997'," // 行号:2567
        + "'lowerStrict':false,'ordering':'numeric'},'aggregations':[{'type':'filtered'," // 行号:2568
        + "'filter':{'type':'selector','dimension':'store_state','value':'CA'},'aggregator':{" // 行号:2569
        + "'type':'doubleSum','name':'EXPR$0','fieldName':'store_sales'}},{'type':'doubleSum'," // 行号:2570
        + "'name':'EXPR$1','fieldName':'store_cost'}]," // 行号:2571
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2572
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2573

    sql(sql).queryContains(new DruidChecker(expectedQuery)); // 行号:2575
  } // 行号:2576

  /** Tests that multiple aggregates with filter clauses have their filters // 行号:2578
   * extracted to the outer filter field for data pruning. */
  @Test void testFilterClausesFactoredForPruning1() { // 行号:2580
    String sql = "select " // 行号:2581
        + "sum(\"store_sales\") filter (where \"store_state\" = 'CA'), " // 行号:2582
        + "sum(\"store_sales\") filter (where \"store_state\" = 'WA') " // 行号:2583
        + "from \"foodmart\""; // 行号:2584
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2585
        + "'granularity':'all','filter':{'type':'or','fields':[{'type':'selector','dimension':" // 行号:2586
        + "'store_state','value':'CA'},{'type':'selector','dimension':'store_state'," // 行号:2587
        + "'value':'WA'}]},'aggregations':[{'type':'filtered','filter':{'type':'selector'," // 行号:2588
        + "'dimension':'store_state','value':'CA'},'aggregator':{'type':'doubleSum','name':" // 行号:2589
        + "'EXPR$0','fieldName':'store_sales'}},{'type':'filtered','filter':{'type':'selector'," // 行号:2590
        + "'dimension':'store_state','value':'WA'},'aggregator':{'type':'doubleSum','name':" // 行号:2591
        + "'EXPR$1','fieldName':'store_sales'}}],'intervals':" // 行号:2592
        + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2593
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2594

    sql(sql) // 行号:2596
        .queryContains(new DruidChecker(expectedQuery)) // 行号:2597
        .returnsUnordered("EXPR$0=159167.84; EXPR$1=263793.22"); // 行号:2598
  } // 行号:2599

  /** Tests that multiple aggregates with filter clauses have their filters // 行号:2601
   * extracted to the outer filter field for data pruning in the presence of an // 行号:2602
   * outer filter. */
  @Test void testFilterClausesFactoredForPruning2() { // 行号:2604
    String sql = "select " // 行号:2605
        + "sum(\"store_sales\") filter (where \"store_state\" = 'CA'), " // 行号:2606
        + "sum(\"store_sales\") filter (where \"store_state\" = 'WA') " // 行号:2607
        + "from \"foodmart\" where \"brand_name\" = 'Super'"; // 行号:2608
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2609
        + "'granularity':'all','filter':{'type':'and','fields':[{'type':'or','fields':[{'type':" // 行号:2610
        + "'selector','dimension':'store_state','value':'CA'},{'type':'selector','dimension':" // 行号:2611
        + "'store_state','value':'WA'}]},{'type':'selector','dimension':'brand_name','value':" // 行号:2612
        + "'Super'}]},'aggregations':[{'type':'filtered','filter':{'type':'selector'," // 行号:2613
        + "'dimension':'store_state','value':'CA'},'aggregator':{'type':'doubleSum','name':" // 行号:2614
        + "'EXPR$0','fieldName':'store_sales'}},{'type':'filtered','filter':{'type':'selector'," // 行号:2615
        + "'dimension':'store_state','value':'WA'},'aggregator':{'type':'doubleSum','name':" // 行号:2616
        + "'EXPR$1','fieldName':'store_sales'}}],'intervals':" // 行号:2617
        + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2618
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2619

    sql(sql) // 行号:2621
        .queryContains(new DruidChecker(expectedQuery)) // 行号:2622
        .returnsUnordered("EXPR$0=2600.01; EXPR$1=4486.44"); // 行号:2623
  } // 行号:2624

  /** Tests that multiple aggregates with the same filter clause have them // 行号:2626
   * factored out in the presence of an outer filter, and that they no longer // 行号:2627
   * refer to those filters. */
  @Test void testMultipleFiltersFactoredOutWithOuterFilter() { // 行号:2629
    // Logically Equivalent to
    // select sum("store_sales"), sum("store_cost")
    // from "foodmart" where "brand_name" = 'Super' and "store_state" = 'CA'
    String sql = "select " // 行号:2633
        + "sum(\"store_sales\") filter (where \"store_state\" = 'CA'), " // 行号:2634
        + "sum(\"store_cost\") filter (where \"store_state\" = 'CA') " // 行号:2635
        + "from \"foodmart\" " // 行号:2636
        + "where \"brand_name\" = 'Super'"; // 行号:2637
    // Aggregates should lose reference to any filter clause
    String expectedAggregateExplain = "aggs=[[SUM($0), SUM($2)]]"; // 行号:2639
    String expectedQuery = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:2640
        + "'granularity':'all','filter':{'type':'and','fields':[{'type':'selector','dimension':" // 行号:2641
        + "'store_state','value':'CA'},{'type':'selector','dimension':'brand_name','value':" // 行号:2642
        + "'Super'}]},'aggregations':[{'type':'doubleSum','name':'EXPR$0','fieldName':" // 行号:2643
        + "'store_sales'},{'type':'doubleSum','name':'EXPR$1','fieldName':'store_cost'}]," // 行号:2644
        + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2645
        + "'context':{'skipEmptyBuckets':false}}"; // 行号:2646

    sql(sql) // 行号:2648
        .queryContains(new DruidChecker(expectedQuery)) // 行号:2649
        .explainContains(expectedAggregateExplain) // 行号:2650
        .returnsUnordered("EXPR$0=2600.01; EXPR$1=1013.162"); // 行号:2651
  } // 行号:2652

  /** Tests that when the resulting filter from factoring filter clauses out is // 行号:2654
   * always false, that they are still pushed to Druid to handle. */
  @Test void testOuterFilterFalseAfterFactorSimplification() { // 行号:2656
    // Normally we would factor out "the_year" > 1997 into the outer filter to prune the data
    // before aggregation and simplify the expression, but in this case that would produce:
    // "the_year" > 1997 AND "the_year" <= 1997 -> false (after simplification)
    // Since Druid cannot handle a "false" filter, we revert back to the
    // pre-simplified version. i.e the filter should be "the_year" > 1997 and "the_year" <= 1997
    // and let Druid handle an unsatisfiable expression
    String sql = "select sum(\"store_sales\") filter (where \"the_year\" > 1997) " // 行号:2663
        + "from \"foodmart\" where \"the_year\" <= 1997"; // 行号:2664

    String expectedFilter = "filter':{'type':'and','fields':[{'type':'bound','dimension':'the_year'" // 行号:2666
        + ",'lower':'1997','lowerStrict':true,'ordering':'numeric'},{'type':'bound'," // 行号:2667
        + "'dimension':'the_year','upper':'1997','upperStrict':false,'ordering':'numeric'}]}"; // 行号:2668
    String context = "'skipEmptyBuckets':false"; // 行号:2669

    sql(sql) // 行号:2671
        .queryContains(new DruidChecker(expectedFilter, context)); // 行号:2672
  } // 行号:2673

  /** Tests that aggregates with filter clauses that Druid cannot handle are not // 行号:2675
   * pushed in as filtered aggregates. */
  @Test void testFilterClauseNotPushable() { // 行号:2677
    // Currently the adapter does not support the LIKE operator
    String sql = "select sum(\"store_sales\") " // 行号:2679
        + "filter (where \"the_year\" like '199_') from \"foodmart\""; // 行号:2680
    String expectedSubExplain = // 行号:2681
        "PLAN=EnumerableInterpreter\n" // 行号:2682
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:2683
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], filter=[LIKE" // 行号:2684
            + "($83, '199_')], projects=[[$90, IS TRUE(LIKE($83, '199_'))]], groups=[{}], " // 行号:2685
            + "aggs=[[SUM($0)]])"; // 行号:2686

    sql(sql) // 行号:2688
        .explainContains(expectedSubExplain) // 行号:2689
        .queryContains( // 行号:2690
            new DruidChecker("\"filter\":{\"type" // 行号:2691
                + "\":\"expression\",\"expression\":\"like(\\\"the_year\\\",")); // 行号:2692
  } // 行号:2693

  @Test void testFilterClauseWithMetricRef() { // 行号:2695
    String sql = "select" // 行号:2696
        + " sum(\"store_sales\") filter (where \"store_cost\" > 10) " // 行号:2697
        + "from \"foodmart\""; // 行号:2698
    String expectedSubExplain = "PLAN=" // 行号:2699
        + "EnumerableInterpreter\n" // 行号:2700
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:2701
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], filter=[>" // 行号:2702
        + "($91, 10.0E0)], projects=[[$90, IS TRUE(>($91, 10.0E0))]], groups=[{}], aggs=[[SUM($0)" // 行号:2703
        + "]])"; // 行号:2704

    sql(sql) // 行号:2706
        .explainContains(expectedSubExplain) // 行号:2707
        .queryContains( // 行号:2708
            new DruidChecker("\"queryType\":\"timeseries\"", "\"filter\":{\"type\":\"bound\"," // 行号:2709
                + "\"dimension\":\"store_cost\",\"lower\":\"10.0\",\"lowerStrict\":true," // 行号:2710
                + "\"ordering\":\"numeric\"}")) // 行号:2711
        .returnsUnordered("EXPR$0=25.06"); // 行号:2712
  } // 行号:2713

  @Test void testFilterClauseWithMetricRefAndAggregates() { // 行号:2715
    String sql = "select sum(\"store_sales\"), \"product_id\" " // 行号:2716
        + "from \"foodmart\" " // 行号:2717
        + "where \"product_id\" > 1553 and \"store_cost\" > 5 " // 行号:2718
        + "group by \"product_id\""; // 行号:2719
    String expectedSubExplain = "PLAN=" // 行号:2720
        + "EnumerableCalc(expr#0..1=[{inputs}], EXPR$0=[$t1], product_id=[$t0])\n" // 行号:2721
        + "  EnumerableInterpreter\n" // 行号:2722
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00" // 行号:2723
        + ".000Z/2992-01-10T00:00:00.000Z]], filter=[AND(>(CAST($1):INTEGER, 1553), >($91, 5.0E0))], " // 行号:2724
        + "projects=[[$1, $90]], groups=[{0}], aggs=[[SUM($1)]])"; // 行号:2725

    CalciteAssert.AssertQuery q = sql(sql) // 行号:2727
        .explainContains(expectedSubExplain) // 行号:2728
        .queryContains( // 行号:2729
            new DruidChecker("\"queryType\":\"groupBy\"", "{\"type\":\"bound\"," // 行号:2730
                + "\"dimension\":\"store_cost\",\"lower\":\"5.0\",\"lowerStrict\":true," // 行号:2731
                + "\"ordering\":\"numeric\"}")); // 行号:2732
    q.returnsUnordered("EXPR$0=10.16; product_id=1554", // 行号:2733
        "EXPR$0=45.05; product_id=1556", // 行号:2734
        "EXPR$0=88.5; product_id=1555"); // 行号:2735
  } // 行号:2736

  @Test void testFilterClauseWithMetricAndTimeAndAggregates() { // 行号:2738
    String sql = "select sum(\"store_sales\"), \"product_id\"" // 行号:2739
        + "from \"foodmart\" " // 行号:2740
        + "where \"product_id\" > 1555 " // 行号:2741
        + "and \"store_cost\" > 5 " // 行号:2742
        + "and extract(year from \"timestamp\") = 1997 " // 行号:2743
        + "group by floor(\"timestamp\" to DAY),\"product_id\""; // 行号:2744
    sql(sql) // 行号:2745
        .queryContains( // 行号:2746
            new DruidChecker("\"queryType\":\"groupBy\"", "{\"type\":\"bound\"," // 行号:2747
                + "\"dimension\":\"store_cost\",\"lower\":\"5.0\",\"lowerStrict\":true," // 行号:2748
                + "\"ordering\":\"numeric\"}")) // 行号:2749
        .returnsUnordered("EXPR$0=10.6; product_id=1556", // 行号:2750
            "EXPR$0=10.6; product_id=1556", // 行号:2751
            "EXPR$0=10.6; product_id=1556", // 行号:2752
            "EXPR$0=13.25; product_id=1556"); // 行号:2753
  } // 行号:2754

  /** Tests that an aggregate with a nested filter clause has its filter // 行号:2756
   * factored out. */
  @Test void testNestedFilterClauseFactored() { // 行号:2758
    // Logically equivalent to
    // select sum("store_sales") from "foodmart" where "store_state" in ('CA', 'OR')
    String sql = "select sum(\"store_sales\") " // 行号:2761
        + "filter (where \"store_state\" = 'CA' or \"store_state\" = 'OR') " // 行号:2762
        + "from \"foodmart\""; // 行号:2763

    String expectedFilterJson = "" // 行号:2765
        + "filter':{'type':'or','fields':[{'type':'selector','dimension':" // 行号:2766
        + "'store_state','value':'CA'},{'type':'selector'," // 行号:2767
        + "'dimension':'store_state','value':'OR'}]}"; // 行号:2768

    String expectedAggregateJson = "'aggregations':[{'type':'doubleSum'," // 行号:2770
        + "'name':'EXPR$0','fieldName':'store_sales'}]"; // 行号:2771

    sql(sql) // 行号:2773
        .queryContains(new DruidChecker(expectedFilterJson)) // 行号:2774
        .queryContains(new DruidChecker(expectedAggregateJson)) // 行号:2775
        .returnsUnordered("EXPR$0=301444.91"); // 行号:2776
  } // 行号:2777

  /** Tests that aggregates with nested filters have their filters factored out // 行号:2779
   * into the outer filter for data pruning while still holding a reference to // 行号:2780
   * the filter clause. */
  @Test void testNestedFilterClauseInAggregates() { // 行号:2782
    String sql = "select " // 行号:2783
        + "sum(\"store_sales\") filter " // 行号:2784
        + "(where \"store_state\" = 'CA' and \"the_month\" = 'October'), " // 行号:2785
        + "sum(\"store_cost\") filter " // 行号:2786
        + "(where \"store_state\" = 'CA' and \"the_day\" = 'Monday') " // 行号:2787
        + "from \"foodmart\""; // 行号:2788

    // (store_state = CA AND the_month = October) OR (store_state = CA AND the_day = Monday)
    String expectedFilterJson = "filter':{'type':'or','fields':[{'type':'and','fields':[{'type':" // 行号:2791
        + "'selector','dimension':'store_state','value':'CA'},{'type':'selector','dimension':" // 行号:2792
        + "'the_month','value':'October'}]},{'type':'and','fields':[{'type':'selector'," // 行号:2793
        + "'dimension':'store_state','value':'CA'},{'type':'selector','dimension':'the_day'," // 行号:2794
        + "'value':'Monday'}]}]}"; // 行号:2795

    String expectedAggregatesJson = "'aggregations':[{'type':'filtered','filter':{'type':'and'," // 行号:2797
        + "'fields':[{'type':'selector','dimension':'store_state','value':'CA'},{'type':" // 行号:2798
        + "'selector','dimension':'the_month','value':'October'}]},'aggregator':{'type':" // 行号:2799
        + "'doubleSum','name':'EXPR$0','fieldName':'store_sales'}},{'type':'filtered'," // 行号:2800
        + "'filter':{'type':'and','fields':[{'type':'selector','dimension':'store_state'," // 行号:2801
        + "'value':'CA'},{'type':'selector','dimension':'the_day','value':'Monday'}]}," // 行号:2802
        + "'aggregator':{'type':'doubleSum','name':'EXPR$1','fieldName':'store_cost'}}]"; // 行号:2803

    sql(sql) // 行号:2805
        .queryContains(new DruidChecker(expectedFilterJson)) // 行号:2806
        .queryContains(new DruidChecker(expectedAggregatesJson)) // 行号:2807
        .returnsUnordered("EXPR$0=13077.79; EXPR$1=9830.7799"); // 行号:2808
  } // 行号:2809

  /** // 行号:2811
   * Test case for // 行号:2812
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1805">[CALCITE-1805]
   * Druid adapter cannot handle count column without adding support for nested // 行号:2814
   * queries</a>. // 行号:2815
   */
  @Test void testCountColumn() { // 行号:2817
    final String sql = "SELECT count(\"countryName\") FROM (SELECT \"countryName\" FROM " // 行号:2818
        + "\"wikipedia\" WHERE \"countryName\"  IS NOT NULL) as a"; // 行号:2819
    sql(sql, WIKI_AUTO2) // 行号:2820
        .returnsUnordered("EXPR$0=3799"); // 行号:2821

    final String sql2 = "SELECT count(\"countryName\") FROM (SELECT \"countryName\" FROM " // 行号:2823
        + "\"wikipedia\") as a"; // 行号:2824
    final String plan2 = "PLAN=EnumerableInterpreter\n" // 行号:2825
        + "  DruidQuery(table=[[wiki, wikipedia]], " // 行号:2826
        + "intervals=[[1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z]], projects=[[$6]], " // 行号:2827
        + "groups=[{}], aggs=[[COUNT($0)]])"; // 行号:2828
    sql(sql2, WIKI_AUTO2) // 行号:2829
        .returnsUnordered("EXPR$0=3799") // 行号:2830
        .explainContains(plan2); // 行号:2831

    final String sql3 = "SELECT count(*), count(\"countryName\") FROM \"wikipedia\""; // 行号:2833
    final String plan3 = "PLAN=EnumerableInterpreter\n" // 行号:2834
        + "  DruidQuery(table=[[wiki, wikipedia]], " // 行号:2835
        + "intervals=[[1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z]], projects=[[$6]], " // 行号:2836
        + "groups=[{}], aggs=[[COUNT(), COUNT($0)]])"; // 行号:2837
    sql(sql3, WIKI_AUTO2) // 行号:2838
        .explainContains(plan3); // 行号:2839
  } // 行号:2840


  @Test void testCountColumn2() { // 行号:2843
    final String sql = "SELECT count(\"countryName\") FROM (SELECT \"countryName\" FROM " // 行号:2844
        + "\"wikipedia\" WHERE \"countryName\"  IS NOT NULL) as a"; // 行号:2845
    sql(sql, WIKI_AUTO2) // 行号:2846
        .queryContains(new DruidChecker("timeseries")) // 行号:2847
        .returnsUnordered("EXPR$0=3799"); // 行号:2848
  } // 行号:2849

  @Test void testCountWithNonNull() { // 行号:2851
    final String sql = "select count(\"timestamp\") from \"foodmart\"\n"; // 行号:2852
    final String druidQuery = "{'queryType':'timeseries','dataSource':'foodmart'"; // 行号:2853
    sql(sql) // 行号:2854
        .returnsUnordered("EXPR$0=86829") // 行号:2855
        .queryContains(new DruidChecker(druidQuery)); // 行号:2856
  } // 行号:2857

  /** // 行号:2859
   * Test to make sure the "not" filter has only 1 field, rather than an array of fields. // 行号:2860
   */
  @Test void testNotFilterForm() { // 行号:2862
    String sql = "select count(distinct \"the_month\") from " // 行号:2863
        + "\"foodmart\" where \"the_month\" <> 'October'"; // 行号:2864
    String druidFilter = "'filter':{'type':'not'," // 行号:2865
        + "'field':{'type':'selector','dimension':'the_month','value':'October'}}"; // 行号:2866
    // Check that the filter actually worked, and that druid was responsible for the filter
    sql(sql, FOODMART) // 行号:2868
        .queryContains(new DruidChecker(druidFilter)) // 行号:2869
        .returnsOrdered("EXPR$0=11"); // 行号:2870
  } // 行号:2871

  /** Tests that {@code count(distinct ...)} gets pushed to Druid when // 行号:2873
   * approximate results are acceptable. */
  @Test void testDistinctCountWhenApproxResultsAccepted() { // 行号:2875
    String sql = "select count(distinct \"store_state\") from \"foodmart\""; // 行号:2876
    String expectedSubExplain = "PLAN=EnumerableInterpreter\n" // 行号:2877
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$63]], groups=[{}], aggs=[[COUNT(DISTINCT $0)]])"; // 行号:2878
    String expectedAggregate = "{'type':'cardinality','name':" // 行号:2879
        + "'EXPR$0','fieldNames':['store_state']}"; // 行号:2880

    testCountWithApproxDistinct(true, sql, expectedSubExplain, expectedAggregate); // 行号:2882
  } // 行号:2883

  /** Tests that {@code count(distinct ...)} doesn't get pushed to Druid when // 行号:2885
   * approximate results are not acceptable. */
  @Test void testDistinctCountWhenApproxResultsNotAccepted() { // 行号:2887
    String sql = "select count(distinct \"store_state\") from \"foodmart\""; // 行号:2888
    String expectedSubExplain = "PLAN=" // 行号:2889
        + "EnumerableAggregate(group=[{}], EXPR$0=[COUNT($0)])\n" // 行号:2890
        + "  EnumerableInterpreter\n" // 行号:2891
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00" // 行号:2892
        + ".000Z/2992-01-10T00:00:00.000Z]], projects=[[$63]], groups=[{0}], aggs=[[]])\n"; // 行号:2893
    testCountWithApproxDistinct(false, sql, expectedSubExplain); // 行号:2894
  } // 行号:2895

  @Test void testDistinctCountOnMetric() { // 行号:2897
    final String sql = "select count(distinct \"store_sales\") from \"foodmart\" " // 行号:2898
        + "where \"store_state\" = 'WA'"; // 行号:2899
    final String expectedSubExplainNoApprox = "PLAN=" // 行号:2900
        + "EnumerableAggregate(group=[{}], EXPR$0=[COUNT($0)])\n" // 行号:2901
        + "  EnumerableInterpreter\n" // 行号:2902
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00" // 行号:2903
        + ".000Z/2992-01-10T00:00:00.000Z]], filter=[=($63, 'WA')], projects=[[$90]], " // 行号:2904
        + "groups=[{0}], aggs=[[]])"; // 行号:2905
    final String expectedSubPlanWithApprox = "PLAN=EnumerableInterpreter\n" // 行号:2906
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00" // 行号:2907
        + ".000Z/2992-01-10T00:00:00.000Z]], filter=[=($63, 'WA')], projects=[[$90]], " // 行号:2908
        + "groups=[{}], aggs=[[COUNT(DISTINCT $0)]])"; // 行号:2909

    testCountWithApproxDistinct(true, sql, expectedSubPlanWithApprox, "'queryType':'timeseries'"); // 行号:2911
    testCountWithApproxDistinct(false, sql, expectedSubExplainNoApprox, "'queryType':'groupBy'"); // 行号:2912
  } // 行号:2913

  /** Tests that a count on a metric does not get pushed into Druid. */
  @Test void testCountOnMetric() { // 行号:2916
    String sql = "select \"brand_name\", count(\"store_sales\") from \"foodmart\" " // 行号:2917
        + "group by \"brand_name\""; // 行号:2918
    String expectedSubExplain = "PLAN=EnumerableInterpreter\n" // 行号:2919
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], projects=[[$2, $90]], groups=[{0}], aggs=[[COUNT($1)]])"; // 行号:2920

    testCountWithApproxDistinct(true, sql, expectedSubExplain, "\"queryType\":\"groupBy\""); // 行号:2922
    testCountWithApproxDistinct(false, sql, expectedSubExplain, "\"queryType\":\"groupBy\""); // 行号:2923
  } // 行号:2924

  /** Tests that {@code count(*)} is pushed into Druid. */
  @Test void testCountStar() { // 行号:2927
    String sql = "select count(*) from \"foodmart\""; // 行号:2928
    String expectedSubExplain = "PLAN=EnumerableInterpreter\n" // 行号:2929
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2930
        + "2992-01-10T00:00:00.000Z]], groups=[{}], aggs=[[COUNT()]])"; // 行号:2931

    sql(sql).explainContains(expectedSubExplain); // 行号:2933
  } // 行号:2934


  @Test void testCountOnMetricRenamed() { // 行号:2937
    String sql = "select \"B\", count(\"A\") from " // 行号:2938
        + "(select \"unit_sales\" as \"A\", \"store_state\" as \"B\" from \"foodmart\") " // 行号:2939
        + "group by \"B\""; // 行号:2940
    String expectedSubExplain = "PLAN=EnumerableInterpreter\n" // 行号:2941
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2942
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $89]], groups=[{0}], aggs=[[COUNT($1)]])"; // 行号:2943

    testCountWithApproxDistinct(true, sql, expectedSubExplain); // 行号:2945
    testCountWithApproxDistinct(false, sql, expectedSubExplain); // 行号:2946
  } // 行号:2947

  @Test void testDistinctCountOnMetricRenamed() { // 行号:2949
    final String sql = "select \"B\", count(distinct \"A\") from " // 行号:2950
        + "(select \"unit_sales\" as \"A\", \"store_state\" as \"B\" from \"foodmart\") " // 行号:2951
        + "group by \"B\""; // 行号:2952
    final String expectedSubExplainNoApprox = "PLAN=" // 行号:2953
        + "EnumerableAggregate(group=[{0}], EXPR$1=[COUNT($1)])\n" // 行号:2954
        + "  EnumerableInterpreter\n" // 行号:2955
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:2956
        + "2992-01-10T00:00:00.000Z]], projects=[[$63, $89]], groups=[{0, 1}], aggs=[[]])"; // 行号:2957
    final String expectedPlanWithApprox = "PLAN=" // 行号:2958
        + "EnumerableInterpreter\n" // 行号:2959
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00" // 行号:2960
        + ".000Z/2992-01-10T00:00:00.000Z]], projects=[[$63, $89]], groups=[{0}], aggs=[[COUNT" // 行号:2961
        + "(DISTINCT $1)]])\n"; // 行号:2962

    testCountWithApproxDistinct(true, sql, expectedPlanWithApprox, "'queryType':'groupBy'"); // 行号:2964
    testCountWithApproxDistinct(false, sql, expectedSubExplainNoApprox, "'queryType':'groupBy'"); // 行号:2965
  } // 行号:2966

  private void testCountWithApproxDistinct(boolean approx, String sql, String expectedExplain) { // 行号:2968
    testCountWithApproxDistinct(approx, sql, expectedExplain, ""); // 行号:2969
  } // 行号:2970

  private void testCountWithApproxDistinct(boolean approx, String sql, String expectedExplain, // 行号:2972
      String expectedDruidQuery) { // 行号:2973
    CalciteAssert.that() // 行号:2974
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.APPROXIMATE_DISTINCT_COUNT, approx) // 行号:2977
        .query(sql) // 行号:2978
        .runs() // 行号:2979
        .explainContains(expectedExplain) // 行号:2980
        .queryContains(new DruidChecker(expectedDruidQuery)); // 行号:2981
  } // 行号:2982

  /** Tests the use of count(distinct ...) on a complex metric column in // 行号:2984
   * SELECT. */
  @Test void testCountDistinctOnComplexColumn() { // 行号:2986
    // Because approximate distinct count has not been enabled
    sql("select count(distinct \"user_id\") from \"wiki\"", WIKI) // 行号:2988
        .failsAtValidation("Rolled up column 'user_id' is not allowed in COUNT"); // 行号:2989

    foodmartApprox("select count(distinct \"customer_id\") from \"foodmart\"") // 行号:2991
        // customer_id gets transformed into its actual underlying sketch column,
        // customer_id_ts. The thetaSketch aggregation is used to compute the count distinct.
        .queryContains( // 行号:2994
            new DruidChecker("{'queryType':'timeseries','dataSource':" // 行号:2995
                + "'foodmart','descending':false,'granularity':'all','aggregations':[{'type':" // 行号:2996
                + "'thetaSketch','name':'EXPR$0','fieldName':'customer_id_ts'}]," // 行号:2997
                + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:2998
                + "'context':{'skipEmptyBuckets':false}}")) // 行号:2999
        .returnsUnordered("EXPR$0=5581"); // 行号:3000

    foodmartApprox("select sum(\"store_sales\"), " // 行号:3002
        + "count(distinct \"customer_id\") filter (where \"store_state\" = 'CA') " // 行号:3003
        + "from \"foodmart\" where \"the_month\" = 'October'") // 行号:3004
        // Check that filtered aggregations work correctly
        .queryContains( // 行号:3006
            new DruidChecker("{'type':'filtered','filter':" // 行号:3007
                + "{'type':'selector','dimension':'store_state','value':'CA'},'aggregator':" // 行号:3008
                + "{'type':'thetaSketch','name':'EXPR$1','fieldName':'customer_id_ts'}}]")) // 行号:3009
        .returnsUnordered("EXPR$0=42342.27; EXPR$1=459"); // 行号:3010
  } // 行号:3011

  /** Tests the use of other aggregations with complex columns. */
  @Test void testAggregationsWithComplexColumns() { // 行号:3014
    wikiApprox("select count(\"user_id\") from \"wiki\"") // 行号:3015
        .failsAtValidation("Rolled up column 'user_id' is not allowed in COUNT"); // 行号:3016

    wikiApprox("select sum(\"user_id\") from \"wiki\"") // 行号:3018
        .failsAtValidation("Cannot apply 'SUM' to arguments of type " // 行号:3019
            + "'SUM(<VARBINARY>)'. Supported form(s): 'SUM(<NUMERIC>)'"); // 行号:3020

    wikiApprox("select avg(\"user_id\") from \"wiki\"") // 行号:3022
        .failsAtValidation("Cannot apply 'AVG' to arguments of type " // 行号:3023
            + "'AVG(<VARBINARY>)'. Supported form(s): 'AVG(<NUMERIC>)'"); // 行号:3024

    wikiApprox("select max(\"user_id\") from \"wiki\"") // 行号:3026
        .failsAtValidation("Rolled up column 'user_id' is not allowed in MAX"); // 行号:3027

    wikiApprox("select min(\"user_id\") from \"wiki\"") // 行号:3029
        .failsAtValidation("Rolled up column 'user_id' is not allowed in MIN"); // 行号:3030
  } // 行号:3031

  /** Tests post-aggregation support with +, -, /, * operators. */
  @Test void testPostAggregationWithComplexColumns() { // 行号:3034
    foodmartApprox("select " // 行号:3035
        + "(count(distinct \"customer_id\") * 2) + " // 行号:3036
        + "count(distinct \"customer_id\") - " // 行号:3037
        + "(3 * count(distinct \"customer_id\")) " // 行号:3038
        + "from \"foodmart\"") // 行号:3039
        .queryContains( // 行号:3040
            new DruidChecker("\"postAggregations\":[{\"type\":\"expression\"," // 行号:3041
                + "\"name\":\"EXPR$0\",\"expression\":\"(((\\\"$f0\\\" * 2) + \\\"$f0\\\")" // 行号:3042
                + " - (3 * \\\"$f0\\\"))\"}]")) // 行号:3043
        .returnsUnordered("EXPR$0=0"); // 行号:3044

    foodmartApprox("select " // 行号:3046
        + "\"the_month\" as \"month\", " // 行号:3047
        + "sum(\"store_sales\") / count(distinct \"customer_id\") as \"avg$\" " // 行号:3048
        + "from \"foodmart\" group by \"the_month\"") // 行号:3049
        .queryContains( // 行号:3050
            new DruidChecker("'postAggregations':[{'type':'expression'," // 行号:3051
                + "'name':'avg$','expression':'(\\'$f1\\' / \\'$f2\\')'}]")) // 行号:3052
        .returnsUnordered("month=January; avg$=32.62155444126063", // 行号:3053
            "month=February; avg$=33.102021036814484", // 行号:3054
            "month=March; avg$=33.84970906630567", // 行号:3055
            "month=April; avg$=32.557517084282296", // 行号:3056
            "month=May; avg$=32.42617797228287", // 行号:3057
            "month=June; avg$=33.93093562874239", // 行号:3058
            "month=July; avg$=34.36859097127213", // 行号:3059
            "month=August; avg$=32.81181818181806", // 行号:3060
            "month=September; avg$=33.327733840304155", // 行号:3061
            "month=October; avg$=32.74730858468674", // 行号:3062
            "month=November; avg$=34.51727684346705", // 行号:3063
            "month=December; avg$=33.62788665879565"); // 行号:3064

    final String druid = "'postAggregations':[{'type':'expression','name':'EXPR$0'," // 行号:3066
        + "'expression':'((\\'$f0\\' + 100) - (\\'$f0\\' * 2))'}]"; // 行号:3067
    final String sql = "select (count(distinct \"user_id\") + 100) - " // 行号:3068
        + "(count(distinct \"user_id\") * 2) from \"wiki\""; // 行号:3069
    wikiApprox(sql) // 行号:3070
        .queryContains(new DruidChecker(druid)) // 行号:3071
        .returnsUnordered("EXPR$0=-10590"); // 行号:3072

    // Change COUNT(DISTINCT ...) to APPROX_COUNT_DISTINCT(...) and get
    // same result even if approximation is off by default.
    final String sql2 = "select (approx_count_distinct(\"user_id\") + 100) - " // 行号:3076
        + "(approx_count_distinct(\"user_id\") * 2) from \"wiki\""; // 行号:3077
    sql(sql2, WIKI) // 行号:3078
        .queryContains(new DruidChecker(druid)) // 行号:3079
        .returnsUnordered("EXPR$0=-10590"); // 行号:3080
  } // 行号:3081

  /** // 行号:3083
   * Test to make sure that if a complex metric is also a dimension, then // 行号:3084
   * {@link org.apache.calcite.adapter.druid.DruidTable} should allow it to be used like any other // 行号:3085
   * column. // 行号:3086
   * */
  @Test void testComplexMetricAlsoDimension() { // 行号:3088
    foodmartApprox("select \"customer_id\" from \"foodmart\"") // 行号:3089
        .runs(); // 行号:3090

    foodmartApprox("select count(distinct \"the_month\"), \"customer_id\" " // 行号:3092
        + "from \"foodmart\" group by \"customer_id\"") // 行号:3093
        .queryContains( // 行号:3094
            new DruidChecker("{'queryType':'groupBy','dataSource':'foodmart'," // 行号:3095
                + "'granularity':'all','dimensions':[{'type':'default','dimension':" // 行号:3096
                + "'customer_id','outputName':'customer_id','outputType':'STRING'}]," // 行号:3097
                + "'limitSpec':{'type':'default'},'aggregations':[{" // 行号:3098
                + "'type':'cardinality','name':'EXPR$0','fieldNames':['the_month']}]," // 行号:3099
                + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']}")); // 行号:3100
  } // 行号:3101

  /** // 行号:3103
   * Test to make sure that SELECT * doesn't fail, and that the rolled up column is not requested // 行号:3104
   * in the JSON query. // 行号:3105
   * */
  @Test void testSelectStarWithRollUp() { // 行号:3107
    final String sql = "select * from \"wiki\" limit 5"; // 行号:3108
    sql(sql, WIKI) // 行号:3109
        // make sure user_id column is not present
        .queryContains( // 行号:3111
            new DruidChecker("{'queryType':'scan','dataSource':'wikipedia','intervals':" // 行号:3112
                + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z'],'virtualColumns':" // 行号:3113
                + "[{'type':'expression','name':'vc','expression':'\\'__time\\''," // 行号:3114
                + "'outputType':'LONG'}],'columns':['vc','channel','cityName','comment'," // 行号:3115
                + "'countryIsoCode','countryName','isAnonymous','isMinor','isNew','isRobot'," // 行号:3116
                + "'isUnpatrolled','metroCode','namespace','page','regionIsoCode','regionName'," // 行号:3117
                + "'count','added','deleted','delta'],'resultFormat':'compactedList','limit':5}")); // 行号:3118
  } // 行号:3119

  /** // 行号:3121
   * Test to make sure that the mapping from a Table name to a Table returned from // 行号:3122
   * {@link org.apache.calcite.adapter.druid.DruidSchema} is always the same Java object. // 行号:3123
   * */
  @Test void testTableMapReused() { // 行号:3125
    AbstractSchema schema = new DruidSchema("http://localhost:8082", "http://localhost:8081", true);
    assertSame(schema.tables().get("wikipedia"), schema.tables().get("wikipedia")); // 行号:3127
  } // 行号:3128

  @Test void testPushEqualsCastDimension() { // 行号:3130
    final String sqlQuery = "select sum(\"store_cost\") as a " // 行号:3131
        + "from \"foodmart\" " // 行号:3132
        + "where cast(\"product_id\" as double) = 1016.0"; // 行号:3133
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:3134
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3135
        + "filter=[=(CAST($1):DOUBLE, 1016.0E0)], projects=[[$91]], groups=[{}], aggs=[[SUM($0)]])"; // 行号:3136
    final String druidQuery = // 行号:3137
        "{'queryType':'timeseries','dataSource':'foodmart','descending':false,'granularity':'all'," // 行号:3138
            + "'filter':{'type':'bound','dimension':'product_id','lower':'1016.0'," // 行号:3139
            + "'lowerStrict':false,'upper':'1016.0','upperStrict':false,'ordering':'numeric'}," // 行号:3140
            + "'aggregations':[{'type':'doubleSum','name':'A','fieldName':'store_cost'}]," // 行号:3141
            + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:3142
            + "'context':{'skipEmptyBuckets':false}}"; // 行号:3143
    sql(sqlQuery, FOODMART) // 行号:3144
        .explainContains(plan) // 行号:3145
        .queryContains(new DruidChecker(druidQuery)) // 行号:3146
        .returnsUnordered("A=85.3164"); // 行号:3147

    final String sqlQuery2 = "select sum(\"store_cost\") as a " // 行号:3149
        + "from \"foodmart\" " // 行号:3150
        + "where cast(\"product_id\" as double) <= 1016.0 " // 行号:3151
        + "and cast(\"product_id\" as double) >= 1016.0"; // 行号:3152
    sql(sqlQuery2, FOODMART) // 行号:3153
        .returnsUnordered("A=85.3164"); // 行号:3154
  } // 行号:3155

  @Test void testPushNotEqualsCastDimension() { // 行号:3157
    final String sqlQuery = "select sum(\"store_cost\") as a " // 行号:3158
        + "from \"foodmart\" " // 行号:3159
        + "where cast(\"product_id\" as double) <> 1016.0"; // 行号:3160
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:3161
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3162
        + "filter=[<>(CAST($1):DOUBLE, 1016.0E0)], projects=[[$91]], groups=[{}], aggs=[[SUM($0)]])"; // 行号:3163
    final String druidQuery = // 行号:3164
        "{'queryType':'timeseries','dataSource':'foodmart','descending':false,'granularity':'all'," // 行号:3165
            + "'filter':{'type':'not','field':{'type':'bound','dimension':'product_id','" // 行号:3166
            + "lower':'1016.0','lowerStrict':false,'upper':'1016.0','upperStrict':false,'ordering':'numeric'}}," // 行号:3167
            + "'aggregations':[{'type':'doubleSum','name':'A','fieldName':'store_cost'}]," // 行号:3168
            + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z'],'context':{'skipEmptyBuckets':false}}"; // 行号:3169
    sql(sqlQuery, FOODMART) // 行号:3170
        .explainContains(plan) // 行号:3171
        .returnsUnordered("A=225541.9172") // 行号:3172
        .queryContains(new DruidChecker(druidQuery)); // 行号:3173

    final String sqlQuery2 = "select sum(\"store_cost\") as a " // 行号:3175
        + "from \"foodmart\" " // 行号:3176
        + "where cast(\"product_id\" as double) < 1016.0 " // 行号:3177
        + "or cast(\"product_id\" as double) > 1016.0"; // 行号:3178
    sql(sqlQuery2, FOODMART) // 行号:3179
        .returnsUnordered("A=225541.9172"); // 行号:3180
  } // 行号:3181

  @Test void testIsNull() { // 行号:3183
    final String sql = "select count(*) as c " // 行号:3184
        + "from \"foodmart\" " // 行号:3185
        + "where \"product_id\" is null"; // 行号:3186
    final String druidQuery = // 行号:3187
        "{'queryType':'timeseries','dataSource':'foodmart','descending':false,'granularity':'all'," // 行号:3188
            + "'filter':{'type':'selector','dimension':'product_id','value':null}," // 行号:3189
            + "'aggregations':[{'type':'count','name':'C'}]," // 行号:3190
            + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:3191
            + "'context':{'skipEmptyBuckets':false}}"; // 行号:3192
    sql(sql, FOODMART) // 行号:3193
        .queryContains(new DruidChecker(druidQuery)) // 行号:3194
        .returnsUnordered("C=0") // 行号:3195
        .returnsCount(1); // 行号:3196
  } // 行号:3197

  @Test void testIsNotNull() { // 行号:3199
    final String sql = "select count(*) as c " // 行号:3200
        + "from \"foodmart\" " // 行号:3201
        + "where \"product_id\" is not null"; // 行号:3202
    final String druidQuery = // 行号:3203
        "{'queryType':'timeseries','dataSource':'foodmart','descending':false,'granularity':'all'," // 行号:3204
            + "'filter':{'type':'not','field':{'type':'selector','dimension':'product_id','value':null}}," // 行号:3205
            + "'aggregations':[{'type':'count','name':'C'}]," // 行号:3206
            + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z']," // 行号:3207
            + "'context':{'skipEmptyBuckets':false}}"; // 行号:3208
    sql(sql, FOODMART) // 行号:3209
        .queryContains(new DruidChecker(druidQuery)) // 行号:3210
        .returnsUnordered("C=86829"); // 行号:3211
  } // 行号:3212

  @Test void testFilterWithFloorOnTime() { // 行号:3214
    // Test filter on floor on time column is pushed to druid
    final String sql = // 行号:3216
        "Select cast(floor(\"timestamp\" to MONTH) as timestamp) as t from \"foodmart\" where " // 行号:3217
            + "floor(\"timestamp\" to MONTH) between '1997-01-01 00:00:00 UTC'" // 行号:3218
            + "and '1997-03-01 00:00:00 UTC' order by t limit 2"; // 行号:3219

    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart','intervals':" // 行号:3221
        + "['1997-01-01T00:00:00.000Z/1997-04-01T00:00:00.000Z'],'virtualColumns':" // 行号:3222
        + "[{'type':'expression','name':'vc','expression':'timestamp_floor("; // 行号:3223
    sql(sql, FOODMART) // 行号:3224
        .returnsOrdered("T=1997-01-01 00:00:00", "T=1997-01-01 00:00:00") // 行号:3225
        .queryContains( // 行号:3226
            new DruidChecker(druidQuery)); // 行号:3227
  } // 行号:3228

  @Test void testSelectFloorOnTimeWithFilterOnFloorOnTime() { // 行号:3230
    final String sql = "Select cast(floor(\"timestamp\" to MONTH) as timestamp) as t from " // 行号:3231
        + "\"foodmart\" where floor(\"timestamp\" to MONTH) >= '1997-05-01 00:00:00 UTC' order by t" // 行号:3232
        + " limit 1"; // 行号:3233
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:3234
        + "  BindableSort(sort0=[$0], dir0=[ASC], fetch=[1])\n" // 行号:3235
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:3236
        + "2992-01-10T00:00:00.000Z]], filter=[>=(FLOOR($0, FLAG(MONTH)), 1997-05-01 00:00:00)], " // 行号:3237
        + "projects=[[CAST(FLOOR($0, FLAG(MONTH))):TIMESTAMP(0) NOT NULL]])"; // 行号:3238

    sql(sql, FOODMART).returnsOrdered("T=1997-05-01 00:00:00").explainContains(plan); // 行号:3240
  } // 行号:3241

  @Test void testTimeWithFilterOnFloorOnTimeAndCastToTimestamp() { // 行号:3243
    final String sql = "Select cast(floor(\"timestamp\" to MONTH) as timestamp) as t from " // 行号:3244
        + "\"foodmart\" where floor(\"timestamp\" to MONTH) >= cast('1997-05-01 00:00:00' as TIMESTAMP) order by t" // 行号:3245
        + " limit 1"; // 行号:3246
    final String druidQuery = "{'queryType':'scan','dataSource':'foodmart','intervals':" // 行号:3247
        + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z'],'filter':{'type':'bound'," // 行号:3248
        + "'dimension':'__time','lower':'1997-05-01T00:00:00.000Z'," // 行号:3249
        + "'lowerStrict':false,'ordering':'lexicographic','"; // 行号:3250
    sql(sql, FOODMART) // 行号:3251
        .returnsOrdered("T=1997-05-01 00:00:00") // 行号:3252
        .queryContains(new DruidChecker(druidQuery)); // 行号:3253
  } // 行号:3254

  @Test void testTimeWithFilterOnFloorOnTimeWithTimezone() { // 行号:3256
    final String sql = "Select cast(\"__time\" as timestamp) as t from " // 行号:3257
        + "\"wikipedia\" where floor(\"__time\" to HOUR) >= cast('2015-09-12 08:00:00'" // 行号:3258
        + " as TIMESTAMP) order by t limit 1"; // 行号:3259
    final String druidQueryPart1 = "filter\":{\"type\":\"bound\",\"dimension\":\"__time\"," // 行号:3260
        + "\"lower\":\"2015-09-12T08:00:00.000Z\",\"lowerStrict\":false," // 行号:3261
        + "\"ordering\":\"lexicographic\",\"extractionFn\":{\"type\":\"timeFormat\"," // 行号:3262
        + "\"format\":\"yyyy-MM-dd"; // 行号:3263
    final String druidQueryPart2 = "\"granularity\":{\"type\":\"period\",\"period\":\"PT1H\"," // 行号:3264
        + "\"timeZone\":\"Asia/Kolkata\"},\"timeZone\":\"UTC\"," // 行号:3265
        + "\"locale\":\"und\"}}"; // 行号:3266

    CalciteAssert.that() // 行号:3268
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(WIKI_AUTO2) // 行号:3270
        .with(CalciteConnectionProperty.TIME_ZONE, "Asia/Kolkata") // 行号:3271
        .query(sql) // 行号:3272
        .runs() // 行号:3273
        .queryContains(new DruidChecker(druidQueryPart1, druidQueryPart2)) // 行号:3274
        .returnsOrdered("T=2015-09-12 14:00:01"); // 行号:3275
  } // 行号:3276

  @Test void testTimeWithFilterOnFloorOnTimeWithTimezoneConversion() { // 行号:3278
    final String sql = "Select cast(\"__time\" as timestamp) as t, \"countryName\" as s, " // 行号:3279
        + "count(*) as c from \"wikipedia\" where floor(\"__time\" to HOUR)" // 行号:3280
        + " >= '2015-09-12 08:00:00 Asia/Kolkata' group by cast(\"__time\" as timestamp), \"countryName\"" // 行号:3281
        + " order by t limit 4"; // 行号:3282
    final String druidQueryPart1 = "filter\":{\"type\":\"bound\",\"dimension\":\"__time\"," // 行号:3283
        + "\"lower\":\"2015-09-12T02:30:00.000Z\",\"lowerStrict\":false," // 行号:3284
        + "\"ordering\":\"lexicographic\",\"extractionFn\":{\"type\":\"timeFormat\"," // 行号:3285
        + "\"format\":\"yyyy-MM-dd"; // 行号:3286
    final String druidQueryPart2 = "\"granularity\":{\"type\":\"period\",\"period\":\"PT1H\"," // 行号:3287
        + "\"timeZone\":\"Asia/Kolkata\"},\"timeZone\":\"UTC\"," // 行号:3288
        + "\"locale\":\"und\"}}"; // 行号:3289
    CalciteAssert.that() // 行号:3290
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(WIKI_AUTO2) // 行号:3292
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "Asia/Kolkata") // 行号:3293
        .query(sql) // 行号:3294
        .runs() // 行号:3295
        .queryContains(new DruidChecker(druidQueryPart1, druidQueryPart2)) // 行号:3296
        .returnsOrdered("T=2015-09-12 08:00:02; S=null; C=1", // 行号:3297
            "T=2015-09-12 08:00:04; S=null; C=1", // 行号:3298
            "T=2015-09-12 08:00:05; S=null; C=1", // 行号:3299
            "T=2015-09-12 08:00:07; S=null; C=1"); // 行号:3300
  } // 行号:3301

  @Test void testTimeWithFilterOnFloorOnTimeWithTimezoneConversionCast() { // 行号:3303
    final String sql = "Select cast(\"__time\" as timestamp) as t, \"countryName\" as s, " // 行号:3304
        + "count(*) as c from \"wikipedia\" where floor(\"__time\" to HOUR)" // 行号:3305
        + " >= '2015-09-12 08:00:00 Asia/Kolkata' group by cast(\"__time\" as timestamp), \"countryName\"" // 行号:3306
        + " order by t limit 4"; // 行号:3307
    final String druidQueryPart1 = "filter\":{\"type\":\"bound\",\"dimension\":\"__time\"," // 行号:3308
        + "\"lower\":\"2015-09-12T02:30:00.000Z\",\"lowerStrict\":false," // 行号:3309
        + "\"ordering\":\"lexicographic\",\"extractionFn\":{\"type\":\"timeFormat\"," // 行号:3310
        + "\"format\":\"yyyy-MM-dd"; // 行号:3311
    final String druidQueryPart2 = "\"granularity\":{\"type\":\"period\",\"period\":\"PT1H\"," // 行号:3312
        + "\"timeZone\":\"Asia/Kolkata\"},\"timeZone\":\"UTC\"," // 行号:3313
        + "\"locale\":\"und\"}}"; // 行号:3314

    CalciteAssert.that() // 行号:3316
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(WIKI_AUTO2) // 行号:3318
        .with(CalciteConnectionProperty.TIME_ZONE, "Asia/Kolkata") // 行号:3319
        .query(sql) // 行号:3320
        .runs() // 行号:3321
        .queryContains(new DruidChecker(druidQueryPart1, druidQueryPart2)) // 行号:3322
        .returnsOrdered("T=2015-09-12 08:00:02; S=null; C=1", // 行号:3323
            "T=2015-09-12 08:00:04; S=null; C=1", // 行号:3324
            "T=2015-09-12 08:00:05; S=null; C=1", // 行号:3325
            "T=2015-09-12 08:00:07; S=null; C=1"); // 行号:3326
  } // 行号:3327

  /** Test case for // 行号:3329
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2122">[CALCITE-2122]
   * DateRangeRules issues</a>. */
  @Test void testCombinationOfValidAndNotValidAndInterval() { // 行号:3332
    final String sql = "SELECT COUNT(*) FROM \"foodmart\" " // 行号:3333
        + "WHERE  \"timestamp\" < CAST('1998-01-02' as TIMESTAMP) AND " // 行号:3334
        + "EXTRACT(MONTH FROM \"timestamp\") = 01 AND EXTRACT(YEAR FROM \"timestamp\") = 1996 "; // 行号:3335
    sql(sql, FOODMART) // 行号:3336
        .runs() // 行号:3337
        .queryContains(new DruidChecker("{\"queryType\":\"timeseries\"")); // 行号:3338
  } // 行号:3339

  @Test void testFloorToDateRangeWithTimeZone() { // 行号:3341
    final String sql = "Select cast(floor(\"timestamp\" to MONTH) as timestamp) as t from " // 行号:3342
        + "\"foodmart\" where floor(\"timestamp\" to MONTH) >= '1997-05-01 00:00:00 Asia/Kolkata' " // 行号:3343
        + "and floor(\"timestamp\" to MONTH) < '1997-05-02 00:00:00 Asia/Kolkata' order by t" // 行号:3344
        + " limit 1"; // 行号:3345
    final String druidQuery = "{\"queryType\":\"scan\",\"dataSource\":\"foodmart\",\"intervals\":" // 行号:3346
        + "[\"1997-04-30T18:30:00.000Z/1997-05-31T18:30:00.000Z\"],\"virtualColumns\":[{\"type\":" // 行号:3347
        + "\"expression\",\"name\":\"vc\",\"expression\":\"timestamp_parse"; // 行号:3348
    CalciteAssert.that() // 行号:3349
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "Asia/Kolkata") // 行号:3352
        .query(sql) // 行号:3353
        .runs() // 行号:3354
        .queryContains(new DruidChecker(druidQuery)) // 行号:3355
        .returnsOrdered("T=1997-05-01 00:00:00"); // 行号:3356
  } // 行号:3357

  @Test void testExpressionsFilter() { // 行号:3359
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where ABS(-EXP(LN(SQRT" // 行号:3360
        + "(\"store_sales\")))) = 1"; // 行号:3361
    sql(sql, FOODMART) // 行号:3362
        .queryContains(new DruidChecker("pow(\\\"store_sales\\\"")) // 行号:3363
        .returnsUnordered("EXPR$0=32"); // 行号:3364
  } // 行号:3365

  @Test void testExpressionsFilter2() { // 行号:3367
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where CAST(SQRT(ABS(-\"store_sales\"))" // 行号:3368
        + " /2 as INTEGER) = 1"; // 行号:3369
    sql(sql, FOODMART) // 行号:3370
        .queryContains(new DruidChecker("(CAST((pow(abs((- \\\"store_sales\\\")),0.5) / 2),")) // 行号:3371
        .returnsUnordered("EXPR$0=62449"); // 行号:3372
  } // 行号:3373

  @Test void testExpressionsLikeFilter() { // 行号:3375
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where \"product_id\" LIKE '1%'"; // 行号:3376
    sql(sql, FOODMART) // 行号:3377
        .queryContains( // 行号:3378
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"like")) // 行号:3379
        .returnsUnordered("EXPR$0=36839"); // 行号:3380
  } // 行号:3381

  @Test void testExpressionsSTRLENFilter() { // 行号:3383
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where CHAR_LENGTH(\"product_id\") = 2"; // 行号:3384
    sql(sql, FOODMART) // 行号:3385
        .queryContains( // 行号:3386
            new DruidChecker("\"expression\":\"(strlen(\\\"product_id\\\") == 2")) // 行号:3387
        .returnsUnordered("EXPR$0=4876"); // 行号:3388
  } // 行号:3389

  @Test void testExpressionsUpperLowerFilter() { // 行号:3391
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where upper(lower(\"city\")) = " // 行号:3392
        + "'SPOKANE'"; // 行号:3393
    sql(sql, FOODMART) // 行号:3394
        .queryContains( // 行号:3395
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"(upper" // 行号:3396
                + "(lower(\\\"city\\\")) ==", "SPOKANE")) // 行号:3397
        .returnsUnordered("EXPR$0=7394"); // 行号:3398
  } // 行号:3399

  @Test void testExpressionsLowerUpperFilter() { // 行号:3401
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where lower(upper(\"city\")) = " // 行号:3402
        + "'spokane'"; // 行号:3403
    sql(sql, FOODMART) // 行号:3404
        .queryContains( // 行号:3405
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"(lower" // 行号:3406
                + "(upper(\\\"city\\\")) ==", "spokane")) // 行号:3407
        .returnsUnordered("EXPR$0=7394"); // 行号:3408
  } // 行号:3409

  @Test void testExpressionsLowerFilterNotMatching() { // 行号:3411
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where lower(\"city\") = 'Spokane'"; // 行号:3412
    sql(sql, FOODMART) // 行号:3413
        .queryContains( // 行号:3414
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"(lower" // 行号:3415
                + "(\\\"city\\\") ==", "Spokane")) // 行号:3416
        .returnsUnordered("EXPR$0=0"); // 行号:3417
  } // 行号:3418

  @Test void testExpressionsLowerFilterMatching() { // 行号:3420
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where lower(\"city\") = 'spokane'"; // 行号:3421
    sql(sql, FOODMART) // 行号:3422
        .queryContains( // 行号:3423
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"(lower" // 行号:3424
                + "(\\\"city\\\") ==", "spokane")) // 行号:3425
        .returnsUnordered("EXPR$0=7394"); // 行号:3426
  } // 行号:3427

  @Test void testExpressionsUpperFilterNotMatching() { // 行号:3429
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where upper(\"city\") = 'Spokane'"; // 行号:3430
    sql(sql, FOODMART) // 行号:3431
        .queryContains( // 行号:3432
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"(upper" // 行号:3433
                + "(\\\"city\\\") ==", "Spokane")) // 行号:3434
        .returnsUnordered("EXPR$0=0"); // 行号:3435
  } // 行号:3436

  @Test void testExpressionsUpperFilterMatching() { // 行号:3438
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where upper(\"city\") = 'SPOKANE'"; // 行号:3439
    sql(sql, FOODMART) // 行号:3440
        .queryContains( // 行号:3441
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"(upper" // 行号:3442
                + "(\\\"city\\\") ==", "SPOKANE")) // 行号:3443
        .returnsUnordered("EXPR$0=7394"); // 行号:3444
  } // 行号:3445

  @Test void testExpressionsConcatFilter() { // 行号:3447
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where (\"city\" || '_extra') = " // 行号:3448
        + "'Spokane_extra'"; // 行号:3449
    sql(sql, FOODMART) // 行号:3450
        .queryContains( // 行号:3451
            new DruidChecker("{\"type\":\"expression\",\"expression\":\"(concat" // 行号:3452
                + "(\\\"city\\\",", "Spokane_extra")) // 行号:3453
        .returnsUnordered("EXPR$0=7394"); // 行号:3454
  } // 行号:3455

  @Test void testExpressionsNotNull() { // 行号:3457
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where (\"city\" || 'extra') IS NOT NULL"; // 行号:3458
    sql(sql, FOODMART) // 行号:3459
        .queryContains( // 行号:3460
            new DruidChecker("{\"type\":\"expression\",\"expression\":\"(concat" // 行号:3461
                + "(\\\"city\\\",", "!= null")) // 行号:3462
        .returnsUnordered("EXPR$0=86829"); // 行号:3463
  } // 行号:3464

  @Test void testComplexExpressionsIsNull() { // 行号:3466
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where ( cast(null as INTEGER) + cast" // 行号:3467
        + "(\"city\" as INTEGER)) IS NULL"; // 行号:3468
    sql(sql, FOODMART) // 行号:3469
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3470
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3471
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3472
            + "groups=[{}], aggs=[[COUNT()]])") // 行号:3473
        .queryContains( // 行号:3474
            new DruidChecker( // 行号:3475
                "{\"queryType\":\"timeseries\",\"dataSource\":\"foodmart\"," // 行号:3476
                    + "\"descending\":false,\"granularity\":\"all\"," // 行号:3477
                    + "\"aggregations\":[{\"type\":\"count\",\"name\":\"EXPR$0\"}]," // 行号:3478
                    + "\"intervals\":[\"1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z\"]," // 行号:3479
                    + "\"context\":{\"skipEmptyBuckets\":false}}")) // 行号:3480
        .returnsUnordered("EXPR$0=86829"); // 行号:3481
  } // 行号:3482

  @Test void testExpressionsConcatFilterMultipleColumns() { // 行号:3484
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where (\"city\" || \"state_province\")" // 行号:3485
        + " = 'SpokaneWA'"; // 行号:3486
    sql(sql, FOODMART) // 行号:3487
        .queryContains( // 行号:3488
            new DruidChecker("(concat(\\\"city\\\",\\\"state_province\\\") ==", "SpokaneWA")) // 行号:3489
        .returnsUnordered("EXPR$0=7394"); // 行号:3490
  } // 行号:3491

  @Test void testAndCombinationOfExpAndSimpleFilter() { // 行号:3493
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where (\"city\" || \"state_province\")" // 行号:3494
        + " = 'SpokaneWA' " // 行号:3495
        + "AND \"state_province\" = 'WA'"; // 行号:3496
    sql(sql, FOODMART) // 行号:3497
        .queryContains( // 行号:3498
            new DruidChecker("(concat(\\\"city\\\",\\\"state_province\\\") ==", // 行号:3499
                "SpokaneWA", // 行号:3500
                "{\"type\":\"selector\",\"dimension\":\"state_province\",\"value\":\"WA\"}]}")) // 行号:3501
        .returnsUnordered("EXPR$0=7394"); // 行号:3502
  } // 行号:3503

  @Test void testOrCombinationOfExpAndSimpleFilter() { // 行号:3505
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where (\"city\" || \"state_province\")" // 行号:3506
        + " = 'SpokaneWA' " // 行号:3507
        + "OR (\"state_province\" = 'CA' AND \"city\" IS NOT NULL)"; // 行号:3508
    sql(sql, FOODMART) // 行号:3509
        .queryContains( // 行号:3510
            new DruidChecker("(concat(\\\"city\\\",\\\"state_province\\\") ==", // 行号:3511
                "SpokaneWA", "{\"type\":\"and\",\"fields\":[{\"type\":\"selector\"," // 行号:3512
                + "\"dimension\":\"state_province\",\"value\":\"CA\"},{\"type\":\"not\"," // 行号:3513
                + "\"field\":{\"type\":\"selector\",\"dimension\":\"city\",\"value\":null}}]}")) // 行号:3514
        .returnsUnordered("EXPR$0=31835"); // 行号:3515
  } // 行号:3516

  @Test void testColumnAEqColumnB() { // 行号:3518
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where \"city\" = \"state_province\""; // 行号:3519
    sql(sql, FOODMART) // 行号:3520
        .queryContains( // 行号:3521
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"" // 行号:3522
                + "(\\\"city\\\" == \\\"state_province\\\")\"}")) // 行号:3523
        .returnsUnordered("EXPR$0=0"); // 行号:3524
  } // 行号:3525

  @Test void testColumnANotEqColumnB() { // 行号:3527
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where \"city\" <> \"state_province\""; // 行号:3528
    sql(sql, FOODMART) // 行号:3529
        .queryContains( // 行号:3530
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"" // 行号:3531
                + "(\\\"city\\\" != \\\"state_province\\\")\"}")) // 行号:3532
        .returnsUnordered("EXPR$0=86829"); // 行号:3533
  } // 行号:3534

  @Test void testAndCombinationOfComplexExpAndSimpleFilter() { // 行号:3536
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where ((\"city\" || " // 行号:3537
        + "\"state_province\") = 'SpokaneWA' OR (\"city\" || '_extra') = 'Spokane_extra') " // 行号:3538
        + "AND \"state_province\" = 'WA'"; // 行号:3539
    sql(sql, FOODMART) // 行号:3540
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3541
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3542
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], filter=[AND(OR(=" // 行号:3543
            + "(||($29, $30), 'SpokaneWA'), =(||($29, '_extra'), 'Spokane_extra')), =($30, 'WA'))" // 行号:3544
            + "], groups=[{}], aggs=[[COUNT()]])") // 行号:3545
        .queryContains( // 行号:3546
            new DruidChecker("(concat(\\\"city\\\",\\\"state_province\\\") ==", // 行号:3547
                "SpokaneWA", "{\"type\":\"selector\",\"dimension\":\"state_province\"," // 行号:3548
                + "\"value\":\"WA\"}]}")) // 行号:3549
        .returnsUnordered("EXPR$0=7394"); // 行号:3550
  } // 行号:3551

  @Test void testExpressionsFilterWithCast() { // 行号:3553
    final String sql = "SELECT COUNT(*) FROM \"foodmart\" where CAST(( SQRT(\"store_sales\") - 1 " // 行号:3554
        + ") / 3 + 1 AS INTEGER) > 1"; // 行号:3555
    sql(sql, FOODMART) // 行号:3556
        .queryContains( // 行号:3557
            new DruidChecker("(CAST((((pow(\\\"store_sales\\\",0.5) - 1) / 3) + 1)", "LONG")) // 行号:3558
        .returnsUnordered("EXPR$0=476"); // 行号:3559
  } // 行号:3560

  @Test void testExpressionsFilterWithCastTimeToDateToChar() { // 行号:3562
    final String sql = "SELECT COUNT(*) FROM \"foodmart\" where CAST(CAST(\"timestamp\" as " // 行号:3563
        + "DATE) as VARCHAR) = '1997-01-01'"; // 行号:3564
    sql(sql, FOODMART) // 行号:3565
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3566
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3567
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3568
            + "filter=[=(CAST(CAST($0):DATE NOT NULL):VARCHAR NOT NULL, '1997-01-01')], " // 行号:3569
            + "groups=[{}], aggs=[[COUNT()]])") // 行号:3570
        .queryContains( // 行号:3571
            new DruidChecker("{\"type\":\"expression\"," // 行号:3572
                + "\"expression\":\"(timestamp_format(timestamp_floor(")) // 行号:3573
        .returnsUnordered("EXPR$0=117"); // 行号:3574
  } // 行号:3575

  @Test void testExpressionsFilterWithExtract() { // 行号:3577
    final String sql = "SELECT COUNT(*) FROM \"foodmart\"  where CAST((EXTRACT(MONTH FROM " // 行号:3578
        + "\"timestamp\") - 1 ) / 3 + 1 AS INTEGER) = 1"; // 行号:3579
    sql(sql, FOODMART) // 行号:3580
        .queryContains( // 行号:3581
            new DruidChecker(",\"filter\":{\"type\":\"expression\",\"expression\":\"(((" // 行号:3582
                + "(timestamp_extract(\\\"__time\\\"", "MONTH", ") - 1) / 3) + 1) == 1")) // 行号:3583
        .returnsUnordered("EXPR$0=21587"); // 行号:3584
  } // 行号:3585

  @Test void testExtractYearFilterExpression() { // 行号:3587
    final String sql = "SELECT count(*) from \"foodmart\" WHERE" // 行号:3588
        + " EXTRACT(YEAR from \"timestamp\") + 1 > 1997"; // 行号:3589
    final String filterPart1 = "'filter':{'type':'expression','expression':" // 行号:3590
        + "'((timestamp_extract(\\'__time\\'"; // 行号:3591
    final String filterTimezoneName = "America/Los_Angeles"; // 行号:3592
    CalciteAssert.that() // 行号:3593
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), filterTimezoneName) // 行号:3596
        .query(sql) // 行号:3597
        .runs() // 行号:3598
        .returnsOrdered("EXPR$0=86712") // 行号:3599
        .queryContains(new DruidChecker(filterPart1, filterTimezoneName)); // 行号:3600
  } // 行号:3601

  @Test void testExtractMonthFilterExpression() { // 行号:3603
    final String sql = "SELECT count(*) from \"foodmart\" WHERE" // 行号:3604
        + " EXTRACT(MONTH from \"timestamp\") + 1 = 02"; // 行号:3605
    final String filterPart1 = "'filter':{'type':'expression','expression':" // 行号:3606
        + "'((timestamp_extract(\\'__time\\'"; // 行号:3607
    final String filterTimezoneName = "America/Los_Angeles"; // 行号:3608
    CalciteAssert.that() // 行号:3609
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), filterTimezoneName) // 行号:3612
        .query(sql) // 行号:3613
        .runs() // 行号:3614
        .returnsOrdered("EXPR$0=7043") // 行号:3615
        .queryContains(new DruidChecker(filterPart1, filterTimezoneName, "MONTH", "== 2")); // 行号:3616
  } // 行号:3617

  @Test void testExtractHourFilterExpression() { // 行号:3619
    final String sql = "SELECT EXTRACT(HOUR from \"timestamp\") " // 行号:3620
        + "from \"foodmart\" WHERE EXTRACT(HOUR from \"timestamp\") = 17 " // 行号:3621
        + "group by EXTRACT(HOUR from \"timestamp\") "; // 行号:3622
    CalciteAssert.that() // 行号:3623
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "America/Los_Angeles") // 行号:3626
        .query(sql) // 行号:3627
        .runs() // 行号:3628
        .returnsOrdered("EXPR$0=17"); // 行号:3629

    final String sql2 = "SELECT EXTRACT(HOUR from \"timestamp\") " // 行号:3631
        + "from \"foodmart\" WHERE" // 行号:3632
        + " EXTRACT(HOUR from \"timestamp\") = 19 " // 行号:3633
        + "group by EXTRACT(HOUR from \"timestamp\") "; // 行号:3634
    CalciteAssert.that() // 行号:3635
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "EST") // 行号:3638
        .query(sql2) // 行号:3639
        .runs() // 行号:3640
        .returnsOrdered("EXPR$0=19"); // 行号:3641

    final String sql3 = "SELECT EXTRACT(HOUR from \"timestamp\") " // 行号:3643
        + "from \"foodmart\" WHERE EXTRACT(HOUR from \"timestamp\") = 0 " // 行号:3644
        + "group by EXTRACT(HOUR from \"timestamp\") "; // 行号:3645
    CalciteAssert.that() // 行号:3646
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "UTC") // 行号:3649
        .query(sql3) // 行号:3650
        .runs() // 行号:3651
        .returnsOrdered("EXPR$0=0"); // 行号:3652
  } // 行号:3653

  @Test void testExtractHourFilterExpressionWithCast() { // 行号:3655
    final String sql = "SELECT EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) " // 行号:3656
        + "from \"foodmart\" WHERE EXTRACT(HOUR from \"timestamp\") = 17 " // 行号:3657
        + "group by EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) "; // 行号:3658
    CalciteAssert.that() // 行号:3659
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "America/Los_Angeles") // 行号:3662
        .query(sql) // 行号:3663
        .runs() // 行号:3664
        .returnsOrdered("EXPR$0=17"); // 行号:3665

    final String sql2 = "SELECT EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) " // 行号:3667
        + "from \"foodmart\" WHERE" // 行号:3668
        + " EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) = 19 " // 行号:3669
        + "group by EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) "; // 行号:3670
    CalciteAssert.that() // 行号:3671
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "EST") // 行号:3674
        .query(sql2) // 行号:3675
        .runs() // 行号:3676
        .returnsOrdered("EXPR$0=19"); // 行号:3677

    final String sql3 = "SELECT EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) " // 行号:3679
        + "from \"foodmart\" WHERE EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) = 0 " // 行号:3680
        + "group by EXTRACT(HOUR from CAST(\"timestamp\" AS TIMESTAMP)) "; // 行号:3681
    CalciteAssert.that() // 行号:3682
        .enable(enabled()) // 根据enabled()方法启用或禁用
        .withModel(FOODMART) // 设置FOODMART模型
        .with(CalciteConnectionProperty.TIME_ZONE.camelName(), "UTC") // 行号:3685
        .query(sql3) // 行号:3686
        .runs() // 行号:3687
        .returnsOrdered("EXPR$0=0"); // 行号:3688
  } // 行号:3689

  @Test void testTimeFloorExpressions() { // 行号:3691

    final String sql = // 行号:3693
        "SELECT CAST(FLOOR(\"timestamp\" to DAY) as TIMESTAMP) as d from \"foodmart\" WHERE " // 行号:3694
            + "CAST(FLOOR(CAST(\"timestamp\" AS DATE) to MONTH) AS DATE) = " // 行号:3695
            + " CAST('1997-01-01' as DATE) GROUP BY  floor(\"timestamp\" to DAY) order by d limit 3"; // 行号:3696
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:3697
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3698
        + "intervals=[[1997-01-01T00:00:00.000Z/1997-02-01T00:00:00.000Z]], " // 行号:3699
        + "projects=[[FLOOR($0, FLAG(DAY))]], groups=[{0}], aggs=[[]], " // 行号:3700
        + "post_projects=[[CAST($0):TIMESTAMP(0) NOT NULL]], sort0=[0], dir0=[ASC], fetch=[3])"; // 行号:3701
    sql(sql, FOODMART) // 行号:3702
        .explainContains(plan) // 行号:3703
        .returnsOrdered("D=1997-01-01 00:00:00", "D=1997-01-02 00:00:00", "D=1997-01-03 00:00:00"); // 行号:3704
  } // 行号:3705

  @Test void testDruidTimeFloorAndTimeParseExpressions() { // 行号:3707
    final String sql = "SELECT CAST(\"timestamp\" AS TIMESTAMP), count(*) " // 行号:3708
        + "from \"foodmart\" WHERE " // 行号:3709
        + "CAST(('1997' || '-01' || '-01') AS DATE) = CAST(\"timestamp\" AS DATE) " // 行号:3710
        + "GROUP BY \"timestamp\""; // 行号:3711
    sql(sql, FOODMART) // 行号:3712
        .returnsOrdered("EXPR$0=1997-01-01 00:00:00; EXPR$1=117") // 行号:3713
        .queryContains( // 行号:3714
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"" // 行号:3715
                + "(852076800000 == timestamp_floor(timestamp_parse(timestamp_format(")); // 行号:3716
  } // 行号:3717

  @Test void testDruidTimeFloorAndTimeParseExpressions2() { // 行号:3719
    Assumptions.assumeTrue(Bug.CALCITE_4205_FIXED, "CALCITE-4205"); // 行号:3720
    final String sql = "SELECT CAST(\"timestamp\" AS TIMESTAMP), count(*) " // 行号:3721
        + "from \"foodmart\" WHERE " // 行号:3722
        + "CAST(('1997' || '-01' || '-01') AS TIMESTAMP) = CAST(\"timestamp\" AS TIMESTAMP) " // 行号:3723
        + "GROUP BY \"timestamp\""; // 行号:3724
    sql(sql, FOODMART) // 行号:3725
        .queryContains( // 行号:3726
            new DruidChecker("\"filter\":{\"type\":\"expression\",\"expression\":\"" // 行号:3727
                + "(timestamp_parse(concat(concat(")) // 行号:3728
        .returnsOrdered("EXPR$0=1997-01-01 00:00:00; EXPR$1=117"); // 行号:3729
  } // 行号:3730

  @Test void testFilterFloorOnMetricColumn() { // 行号:3732
    final String sql = "SELECT count(*) from \"foodmart\" WHERE floor(\"store_sales\") = 23"; // 行号:3733
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:3734
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3735
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]]," // 行号:3736
        + " filter=[=(FLOOR($90), 23.0E0)], groups=[{}], aggs=[[COUNT()]]"; // 行号:3737
    sql(sql, FOODMART) // 行号:3738
        .returnsOrdered("EXPR$0=2") // 行号:3739
        .explainContains(plan) // 行号:3740
        .queryContains(new DruidChecker("\"queryType\":\"timeseries\"")); // 行号:3741
  } // 行号:3742


  @Test void testExpressionFilterSimpleColumnAEqColumnB() { // 行号:3745
    final String sql = "SELECT count(*) from \"foodmart\" where \"product_id\" = \"city\""; // 行号:3746
    sql(sql, FOODMART) // 行号:3747
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3748
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3749
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3750
            + "filter=[=($1, $29)], groups=[{}], aggs=[[COUNT()]])") // 行号:3751
        .queryContains( // 行号:3752
            new DruidChecker("\"filter\":{\"type\":\"expression\"," // 行号:3753
                + "\"expression\":\"(\\\"product_id\\\" == \\\"city\\\")\"}")) // 行号:3754
        .returnsOrdered("EXPR$0=0"); // 行号:3755
  } // 行号:3756

  @Test void testCastPlusMathOps() { // 行号:3758
    final String sql = "SELECT COUNT(*) FROM " + FOODMART_TABLE // 行号:3759
        + "WHERE (CAST(\"product_id\" AS INTEGER) + 1 * \"store_sales\")/(\"store_cost\" - 5) " // 行号:3760
        + "<= floor(\"store_sales\") * 25 + 2"; // 行号:3761
    sql(sql, FOODMART) // 行号:3762
        .queryContains( // 行号:3763
            new DruidChecker( // 行号:3764
                "\"filter\":{\"type\":\"expression\",\"expression\":\"(((CAST(\\\"product_id\\\", ", // 行号:3765
                "LONG", // 行号:3766
                ") + \\\"store_sales\\\") / (\\\"store_cost\\\" - 5))", // 行号:3767
                " <= ((floor(\\\"store_sales\\\") * 25) + 2))\"}")) // 行号:3768
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3769
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3770
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3771
            + "filter=[<=(/(+(CAST($1):INTEGER, $90), -($91, 5)), +(*(FLOOR($90), 25), 2))], " // 行号:3772
            + "groups=[{}], aggs=[[COUNT()]])") // 行号:3773
        .returnsOrdered("EXPR$0=82129"); // 行号:3774
  } // 行号:3775

  @Test void testBooleanFilterExpressions() { // 行号:3777
    final String sql = "SELECT count(*) from " + FOODMART_TABLE // 行号:3778
        + " WHERE (CAST((\"product_id\" <> '1') AS BOOLEAN)) IS TRUE"; // 行号:3779
    sql(sql, FOODMART) // 行号:3780
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3781
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3782
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3783
            + "filter=[<>($1, '1')], groups=[{}], aggs=[[COUNT()]])") // 行号:3784
        .queryContains(new DruidChecker("\"queryType\":\"timeseries\"")) // 行号:3785
        .returnsOrdered("EXPR$0=86803"); // 行号:3786
  } // 行号:3787


  @Test void testCombinationOfValidAndNotValidFilters() { // 行号:3790
    final String sql = "SELECT COUNT(*) FROM " + FOODMART_TABLE // 行号:3791
        + "WHERE ((CAST(\"product_id\" AS INTEGER) + 1 * \"store_sales\")/(\"store_cost\" - 5) " // 行号:3792
        + "<= floor(\"store_sales\") * 25 + 2) AND \"timestamp\" < CAST('1997-01-02' as TIMESTAMP)" // 行号:3793
        + "AND CAST(\"store_sales\" > 0 AS BOOLEAN) IS TRUE " // 行号:3794
        + "AND \"product_id\" like '1%' AND \"store_cost\" > 1 " // 行号:3795
        + "AND EXTRACT(MONTH FROM \"timestamp\") = 01 AND EXTRACT(DAY FROM \"timestamp\") = 01 " // 行号:3796
        + "AND EXTRACT(MONTH FROM \"timestamp\") / 4 + 1 = 1 "; // 行号:3797
    final String queryType = "{'queryType':'timeseries','dataSource':'foodmart'"; // 行号:3798
    final String filterExp1 = "{'type':'expression','expression':'(((CAST(\\'product_id\\'"; // 行号:3799
    final String filterExpPart2 =  " \\'store_sales\\') / (\\'store_cost\\' - 5)) " // 行号:3800
        + "<= ((floor(\\'store_sales\\') * 25) + 2))'}"; // 行号:3801
    final String likeExpressionFilter = "{'type':'expression','expression':'like(\\'product_id\\'"; // 行号:3802
    final String likeExpressionFilter2 = "1%"; // 行号:3803
    final String simpleBound = "{'type':'bound','dimension':'store_cost','lower':'1.0'," // 行号:3804
        + "'lowerStrict':true,'ordering':'numeric'}"; // 行号:3805
    final String timeSimpleFilter = // 行号:3806
        "{'type':'bound','dimension':'__time','upper':'1997-01-02T00:00:00.000Z'," // 行号:3807
            + "'upperStrict':true,'ordering':'lexicographic','extractionFn':{'type':'timeFormat','format':'yyyy-MM-dd"; // 行号:3808
    final String simpleExtractFilterMonth = "{'type':'bound','dimension':'__time','lower':'1'," // 行号:3809
        + "'lowerStrict':false,'upper':'1','upperStrict':false,'ordering':'numeric'," // 行号:3810
        + "'extractionFn':{'type':'timeFormat','format':'M','timeZone':'UTC','locale':'en-US'}}"; // 行号:3811
    final String simpleExtractFilterDay = "{'type':'bound','dimension':'__time','lower':'1'," // 行号:3812
        + "'lowerStrict':false,'upper':'1','upperStrict':false,'ordering':'numeric'," // 行号:3813
        + "'extractionFn':{'type':'timeFormat','format':'d','timeZone':'UTC','locale':'en-US'}}"; // 行号:3814
    final String quarterAsExpressionFilter = "{'type':'expression','expression':" // 行号:3815
        + "'(((timestamp_extract(\\'__time\\'"; // 行号:3816
    final String quarterAsExpressionFilter2 = "MONTH"; // 行号:3817
    final String quarterAsExpressionFilterTimeZone = "UTC"; // 行号:3818
    final String quarterAsExpressionFilter3 = "/ 4) + 1) == 1)'}]}"; // 行号:3819
    // should use JSON filter instead of Druid expression after the fix of:
    // 1. https://issues.apache.org/jira/browse/CALCITE-2590
    // 2. https://issues.apache.org/jira/browse/CALCITE-2838
    final String booleanAsFilter = "{\"type\":\"bound\",\"dimension\":\"store_sales\"," // 行号:3823
        + "\"lower\":\"0.0\",\"lowerStrict\":true,\"ordering\":\"numeric\"}"; // 行号:3824
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:3825
        + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3826
        + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3827
        + "filter=[AND(<=(/(+(CAST($1):INTEGER, $90), -($91, 5)), +(*(FLOOR($90), 25), 2)), " // 行号:3828
        + ">($90, 0.0E0), LIKE($1, '1%'), >($91, 1.0E0), <($0, 1997-01-02 00:00:00), " // 行号:3829
        + "=(EXTRACT(FLAG(MONTH), $0), 1), =(EXTRACT(FLAG(DAY), $0), 1), " // 行号:3830
        + "=(+(/(EXTRACT(FLAG(MONTH), $0), 4), 1), 1))], groups=[{}], aggs=[[COUNT()]])"; // 行号:3831
    sql(sql, FOODMART) // 行号:3832
        .returnsOrdered("EXPR$0=36") // 行号:3833
        .explainContains(plan) // 行号:3834
        .queryContains( // 行号:3835
            new DruidChecker( // 行号:3836
                queryType, filterExp1, filterExpPart2, likeExpressionFilter, likeExpressionFilter2, // 行号:3837
                simpleBound, timeSimpleFilter, simpleExtractFilterMonth, simpleExtractFilterDay, // 行号:3838
                quarterAsExpressionFilter, quarterAsExpressionFilterTimeZone, // 行号:3839
                quarterAsExpressionFilter2, quarterAsExpressionFilter3, booleanAsFilter)); // 行号:3840
  } // 行号:3841


  @Test void testCeilFilterExpression() { // 行号:3844
    final String sql = "SELECT COUNT(*) FROM " + FOODMART_TABLE + " WHERE ceil(\"store_sales\") > 1" // 行号:3845
        + " AND ceil(\"timestamp\" TO DAY) < CAST('1997-01-05' AS TIMESTAMP)" // 行号:3846
        + " AND ceil(\"timestamp\" TO MONTH) < CAST('1997-03-01' AS TIMESTAMP)" // 行号:3847
        + " AND ceil(\"timestamp\" TO HOUR) > CAST('1997-01-01' AS TIMESTAMP) " // 行号:3848
        + " AND ceil(\"timestamp\" TO MINUTE) > CAST('1997-01-01' AS TIMESTAMP) " // 行号:3849
        + " AND ceil(\"timestamp\" TO SECOND) > CAST('1997-01-01' AS TIMESTAMP) "; // 行号:3850
    final String plan = "PLAN=EnumerableInterpreter\n" // 行号:3851
        + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1997-01-01T00:00:00.001Z/" // 行号:3852
        + "1997-01-04T00:00:00.001Z]], filter=[>(CEIL($90), 1.0E0)], groups=[{}], aggs=[[COUNT()]])"; // 行号:3853
    sql(sql, FOODMART) // 行号:3854
        .explainContains(plan) // 行号:3855
        .returnsOrdered("EXPR$0=408"); // 行号:3856
  } // 行号:3857

  @Test void testSubStringExpressionFilter() { // 行号:3859
    final String sql = // 行号:3860
        "SELECT COUNT(*) AS C, SUBSTRING(\"product_id\" from 1 for 4) FROM " + FOODMART_TABLE // 行号:3861
            + " WHERE SUBSTRING(\"product_id\" from 1 for 4) like '12%' " // 行号:3862
            + " AND CHARACTER_LENGTH(\"product_id\") = 4" // 行号:3863
            + " AND SUBSTRING(\"product_id\" from 3 for 1) = '2'" // 行号:3864
            + " AND CAST(SUBSTRING(\"product_id\" from 2 for 1) AS INTEGER) = 2" // 行号:3865
            + " AND CAST(SUBSTRING(\"product_id\" from 4 for 1) AS INTEGER) = 7" // 行号:3866
            + " AND CAST(SUBSTRING(\"product_id\" from 4) AS INTEGER) = 7" // 行号:3867
            + " Group by SUBSTRING(\"product_id\" from 1 for 4)"; // 行号:3868
    final String plan = "PLAN=" // 行号:3869
        + "EnumerableCalc(expr#0..1=[{inputs}], C=[$t1], EXPR$1=[$t0])\n" // 行号:3870
        + "  EnumerableInterpreter\n" // 行号:3871
        + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00" // 行号:3872
        + ".000Z/2992-01-10T00:00:00.000Z]], filter=[AND(LIKE(SUBSTRING($1, 1, 4), '12%'), =" // 行号:3873
        + "(CHAR_LENGTH($1), 4), =(SUBSTRING($1, 3, 1), '2'), =(CAST(SUBSTRING($1, 2, 1))" // 行号:3874
        + ":INTEGER, 2), =(CAST(SUBSTRING($1, 4, 1)):INTEGER, 7), =(CAST(SUBSTRING($1, 4))" // 行号:3875
        + ":INTEGER, 7))], projects=[[SUBSTRING($1, 1, 4)]], groups=[{0}], aggs=[[COUNT()]])\n"; // 行号:3876
    sql(sql, FOODMART) // 行号:3877
        .returnsOrdered("C=60; EXPR$1=1227") // 行号:3878
        .explainContains(plan) // 行号:3879
        .queryContains( // 行号:3880
            new DruidChecker("\"queryType\":\"groupBy\"", "substring(\\\"product_id\\\"", // 行号:3881
                "\"(strlen(\\\"product_id\\\")", // 行号:3882
                ",\"virtualColumns\":[{\"type\":\"expression\",\"name\":\"vc\"," // 行号:3883
                    + "\"expression\":\"substring(\\\"product_id\\\", 0, 4)\"," // 行号:3884
                    + "\"outputType\":\"STRING\"}]")); // 行号:3885
  } // 行号:3886

  @Test void testSubStringWithNonConstantIndexes() { // 行号:3888
    final String sql = "SELECT COUNT(*) FROM " // 行号:3889
        + FOODMART_TABLE // 行号:3890
        + " WHERE SUBSTRING(\"product_id\" from CAST(\"store_cost\" as INT)/1000 + 2  " // 行号:3891
        + "for CAST(\"product_id\" as INT)) like '1%'"; // 行号:3892

    sql(sql, FOODMART).returnsOrdered("EXPR$0=10893") // 行号:3894
        .queryContains( // 行号:3895
            new DruidChecker("\"queryType\":\"timeseries\"", "like(substring(\\\"product_id\\\"")) // 行号:3896
        .explainContains( // 行号:3897
            "PLAN=EnumerableInterpreter\n  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3898
                + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3899
                + "filter=[LIKE(SUBSTRING($1, +(/(CAST($91):INTEGER, 1000), 2), CAST($1):INTEGER), '1%')], " // 行号:3900
                + "groups=[{}], aggs=[[COUNT()]])\n\n"); // 行号:3901
  } // 行号:3902

  @Test void testSubStringWithNonConstantIndex() { // 行号:3904
    final String sql = "SELECT COUNT(*) FROM " // 行号:3905
        + FOODMART_TABLE // 行号:3906
        + " WHERE SUBSTRING(\"product_id\" from CAST(\"store_cost\" as INT)/1000 + 1) like '1%'"; // 行号:3907

    sql(sql, FOODMART).returnsOrdered("EXPR$0=36839") // 行号:3909
        .queryContains(new DruidChecker("like(substring(\\\"product_id\\\"")) // 行号:3910
        .explainContains( // 行号:3911
            "PLAN=EnumerableInterpreter\n  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3912
                + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3913
                + "filter=[LIKE(SUBSTRING($1, +(/(CAST($91):INTEGER, 1000), 1)), '1%')]," // 行号:3914
                + " groups=[{}], aggs=[[COUNT()]])\n\n"); // 行号:3915
  } // 行号:3916


  /** // 行号:3919
   * Test case for // 行号:3920
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2098">[CALCITE-2098]
   * Push filters to Druid Query Scan when we have OR of AND clauses</a>. // 行号:3922
   * // 行号:3923
   * <p>Need to make sure that when there we have a valid filter with no // 行号:3924
   * conjunction we still push all the valid filters. // 行号:3925
   */
  @Test void testFilterClauseWithNoConjunction() { // 行号:3927
    String sql = "select sum(\"store_sales\")" // 行号:3928
        + "from \"foodmart\" where \"product_id\" > 1555 or \"store_cost\" > 5 or extract(year " // 行号:3929
        + "from \"timestamp\") = 1997 " // 行号:3930
        + "group by floor(\"timestamp\" to DAY),\"product_id\""; // 行号:3931
    sql(sql) // 行号:3932
        .queryContains( // 行号:3933
            new DruidChecker("\"queryType\":\"groupBy\"", "{\"type\":\"bound\"," // 行号:3934
                + "\"dimension\":\"store_cost\",\"lower\":\"5.0\",\"lowerStrict\":true," // 行号:3935
                + "\"ordering\":\"numeric\"}")) // 行号:3936
        .runs(); // 行号:3937
  } // 行号:3938

  /** // 行号:3940
   * Test case for // 行号:3941
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2123">[CALCITE-2123]
   * Bug in the Druid Filter Translation when Comparing String Ref to a Constant // 行号:3943
   * Number</a>. // 行号:3944
   */
  @Test void testBetweenFilterWithCastOverNumeric() { // 行号:3946
    final String sql = "SELECT COUNT(*) FROM " + FOODMART_TABLE + " WHERE \"product_id\" = 16.0"; // 行号:3947
    // After CALCITE-2302 the Druid query changed a bit and the type of the
    // filter became an expression (instead of a bound filter) but it still
    // seems correct.
    sql(sql, FOODMART).runs().queryContains( // 行号:3951
        new DruidChecker( // 行号:3952
            false, // 行号:3953
            "\"filter\":{\"type\":\"bound\",\"dimension\":\"product_id\"," // 行号:3954
                + "\"lower\":\"16.000000000\",\"lowerStrict\":false,\"upper\":\"16.000000000\"," // 行号:3955
                + "\"upperStrict\":false,\"ordering\":\"numeric\"}")); // 行号:3956
  } // 行号:3957

  @Test void testTrigonometryMathFunctions() { // 行号:3959
    final String sql = "SELECT COUNT(*) FROM " + FOODMART_TABLE + "WHERE " // 行号:3960
        + "SIN(\"store_cost\") > SIN(20) AND COS(\"store_sales\") > COS(20) " // 行号:3961
        + "AND FLOOR(TAN(\"store_cost\")) = 2 " // 行号:3962
        + "AND ABS(TAN(\"store_cost\") - SIN(\"store_cost\") / COS(\"store_cost\")) < 10e-7"; // 行号:3963
    sql(sql, FOODMART) // 行号:3964
        .returnsOrdered("EXPR$0=2") // 行号:3965
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3966
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3967
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3968
            + "filter=[AND(>(SIN($91), 0.9129452507276277E0), >(COS($90), 0.40808206181339196E0), =(FLOOR(TAN($91)), 2.0E0), " // 行号:3969
            + "<(ABS(-(TAN($91), /(SIN($91), COS($91)))), 1.0E-6))], " // 行号:3970
            + "groups=[{}], aggs=[[COUNT()]])"); // 行号:3971

    final String sql1 = "SELECT COUNT(*) FROM " + FOODMART_TABLE + " WHERE " // 行号:3973
        + "COT(\"store_cost\") > COT(20) AND ASIN(\"store_sales\") > ASIN(1) " // 行号:3974
        + "AND FLOOR(ACOS(\"store_cost\")) = 2 " // 行号:3975
        + "AND ABS(ATAN(\"store_cost\") - ATAN(\"store_cost\") / ATAN(\"store_cost\")) < 10e-7"; // 行号:3976
    sql(sql1, FOODMART) // 行号:3977
        .returnsOrdered("EXPR$0=0") // 行号:3978
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3979
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3980
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3981
            + "filter=[AND(>(COT($91), 0.4469951089489167E0), >(ASIN($90), 1.5707963267948966E0), =(FLOOR(ACOS($91)), 2.0E0), " // 行号:3982
            + "<(ABS(-(ATAN($91), /(ATAN($91), ATAN($91)))), 1.0E-6))], " // 行号:3983
            + "groups=[{}], aggs=[[COUNT()]])"); // 行号:3984

    final String sql2 = "SELECT COUNT(*) FROM " + FOODMART_TABLE + " WHERE " // 行号:3986
        + "FLOOR(ATAN2(\"store_cost\", \"store_cost\")) = 2 "; // 行号:3987
    sql(sql2, FOODMART) // 行号:3988
        .returnsOrdered("EXPR$0=0") // 行号:3989
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:3990
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:3991
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:3992
            + "filter=[=(FLOOR(ATAN2($91, $91)), 2.0E0)], " // 行号:3993
            + "groups=[{}], aggs=[[COUNT()]])"); // 行号:3994

  } // 行号:3996

  @Test void testCastLiteralToTimestamp() { // 行号:3998
    final String sql = "SELECT COUNT(*) FROM " // 行号:3999
        + FOODMART_TABLE + " WHERE \"timestamp\" < CAST('1997-01-02' as TIMESTAMP)" // 行号:4000
        + " AND EXTRACT(MONTH FROM \"timestamp\") / 4 + 1 = 1 "; // 行号:4001
    sql(sql, FOODMART) // 行号:4002
        .returnsOrdered("EXPR$0=117") // 行号:4003
        .queryContains( // 行号:4004
            new DruidChecker("{'queryType':'timeseries','dataSource':'foodmart'," // 行号:4005
                + "'descending':false,'granularity':'all','filter':{'type':'and','fields':" // 行号:4006
                + "[{'type':'bound','dimension':'__time','upper':'1997-01-02T00:00:00.000Z'," // 行号:4007
                + "'upperStrict':true,'ordering':'lexicographic'," // 行号:4008
                + "'extractionFn':{'type':'timeFormat','format':'yyyy-MM-dd", // 行号:4009
                "{'type':'expression','expression':'(((timestamp_extract(\\'__time\\',", // 行号:4010
                "/ 4) + 1) == 1)'}]},", // 行号:4011
                "'aggregations':[{'type':'count','name':'EXPR$0'}]," // 行号:4012
                    + "'intervals':['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z'],'context':{'skipEmptyBuckets':false}}")); // 行号:4013
  } // 行号:4014

  @Test void testNotTrueSimpleFilter() { // 行号:4016
    final String sql = "SELECT COUNT(*) FROM " + FOODMART_TABLE + "WHERE " // 行号:4017
        + "(\"product_id\" = 1020 ) IS NOT TRUE AND (\"product_id\" = 1020 ) IS FALSE"; // 行号:4018
    final String result = "EXPR$0=86773"; // 行号:4019
    sql(sql, FOODMART) // 行号:4020
        .returnsOrdered(result) // 行号:4021
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4022
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:4023
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:4024
            + "filter=[<>(CAST($1):INTEGER, 1020)]," // 行号:4025
            + " groups=[{}], aggs=[[COUNT()]])"); // 行号:4026
    final String sql2 = "SELECT COUNT(*) FROM " + FOODMART_TABLE + "WHERE " // 行号:4027
        + "\"product_id\" <> 1020"; // 行号:4028
    sql(sql2, FOODMART).returnsOrdered(result); // 行号:4029
  } // 行号:4030

  // ADDING COMPLEX PROJECT PUSHDOWN

  @Test void testPushOfSimpleMathOps() { // 行号:4034
    final String sql = // 行号:4035
        "SELECT COS(\"store_sales\") + 1, SIN(\"store_cost\"), EXTRACT(DAY from \"timestamp\") + 1  as D FROM " // 行号:4036
            + FOODMART_TABLE + "WHERE \"store_sales\" < 20 order by D limit 3"; // 行号:4037
    sql(sql, FOODMART) // 行号:4038
        .runs() // 行号:4039
        .returnsOrdered("EXPR$0=1.060758881219386; EXPR$1=0.5172204046388567; D=2", // 行号:4040
            "EXPR$0=0.8316025520509229; EXPR$1=0.6544084288365644; D=2", // 行号:4041
            "EXPR$0=0.24267723077545622; EXPR$1=0.9286289016881148; D=2") // 行号:4042
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4043
            + "  BindableSort(sort0=[$2], dir0=[ASC], fetch=[3])\n" // 行号:4044
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4045
            + "2992-01-10T00:00:00.000Z]], filter=[<($90, 20.0E0)], projects=[[+(COS($90), 1), SIN($91)," // 行号:4046
            + " +(EXTRACT(FLAG(DAY), $0), 1)]])"); // 行号:4047
  } // 行号:4048

  @Test void testPushOfSimpleColumnAPlusColumnB() { // 行号:4050
    final String sql = // 行号:4051
        "SELECT COS(\"store_sales\" + \"store_cost\") + 1, EXTRACT(DAY from \"timestamp\") + 1  as D FROM " // 行号:4052
            + FOODMART_TABLE + "WHERE \"store_sales\" < 20 order by D limit 3"; // 行号:4053
    sql(sql, FOODMART) // 行号:4054
        .runs() // 行号:4055
        .returnsOrdered("EXPR$0=0.5357357987441458; D=2", // 行号:4056
            "EXPR$0=0.22760480207557643; D=2", // 行号:4057
            "EXPR$0=0.11259322182897047; D=2") // 行号:4058
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4059
            + "  BindableSort(sort0=[$1], dir0=[ASC], fetch=[3])\n" // 行号:4060
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4061
            + "2992-01-10T00:00:00.000Z]], filter=[<($90, 20.0E0)], projects=[[+(COS(+($90, $91)), 1), " // 行号:4062
            + "+(EXTRACT(FLAG(DAY), $0), 1)]])"); // 行号:4063
  } // 行号:4064

  @Test void testSelectExtractMonth() { // 行号:4066
    final String sql = "SELECT  EXTRACT(YEAR FROM \"timestamp\") FROM " + FOODMART_TABLE; // 行号:4067
    sql(sql, FOODMART) // 行号:4068
        .limit(1) // 行号:4069
        .returnsOrdered("EXPR$0=1997") // 行号:4070
        .explainContains("DruidQuery(table=[[foodmart, foodmart]], intervals=" // 行号:4071
            + "[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:4072
            + "projects=[[EXTRACT(FLAG(YEAR), $0)]])") // 行号:4073
        .queryContains( // 行号:4074
            new DruidChecker("\"virtualColumns\":[{\"type\":\"expression\",\"name\":\"vc\"," // 行号:4075
                + "\"expression\":\"timestamp_extract(\\\"__time\\\"")); // 行号:4076
  } // 行号:4077

  @Test void testAggOnArithmeticProject() { // 行号:4079
    final String sql = "SELECT SUM(\"store_sales\" + 1) FROM " + FOODMART_TABLE; // 行号:4080
    sql(sql, FOODMART) // 行号:4081
        .returnsOrdered("EXPR$0=652067.13") // 行号:4082
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4083
            + "  DruidQuery(table=[[foodmart, foodmart]], " // 行号:4084
            + "intervals=[[1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z]], " // 行号:4085
            + "projects=[[+($90, 1)]], groups=[{}], aggs=[[SUM($0)]])") // 行号:4086
        .queryContains( // 行号:4087
            new DruidChecker("\"queryType\":\"timeseries\"", // 行号:4088
                "\"doubleSum\",\"name\":\"EXPR$0\",\"expression\":\"(\\\"store_sales\\\" + 1)\"")); // 行号:4089
  } // 行号:4090

  @Test void testAggOnArithmeticProject2() { // 行号:4092
    final String sql = "SELECT SUM(-\"store_sales\" * 2) as S FROM " + FOODMART_TABLE // 行号:4093
        + "Group by \"timestamp\" order by s LIMIT 2"; // 行号:4094
    sql(sql, FOODMART) // 行号:4095
        .returnsOrdered("S=-15918.02", // 行号:4096
            "S=-14115.96") // 行号:4097
        .explainContains("PLAN=EnumerableCalc(expr#0..1=[{inputs}], S=[$t1])\n" // 行号:4098
            + "  EnumerableInterpreter\n" // 行号:4099
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4100
            + "2992-01-10T00:00:00.000Z]], projects=[[$0, *(-($90), 2)]], groups=[{0}], " // 行号:4101
            + "aggs=[[SUM($1)]], sort0=[1], dir0=[ASC], fetch=[2])") // 行号:4102
        .queryContains( // 行号:4103
            new DruidChecker("'queryType':'groupBy'", "'granularity':'all'", // 行号:4104
                "{'dimension':'S','direction':'ascending','dimensionOrder':'numeric'}", // 行号:4105
                "{'type':'doubleSum','name':'S','expression':'((- \\'store_sales\\') * 2)'}]")); // 行号:4106
  } // 行号:4107

  @Test void testAggOnArithmeticProject3() { // 行号:4109
    final String sql = "SELECT SUM(-\"store_sales\" * 2)-Max(\"store_cost\" * \"store_cost\") AS S," // 行号:4110
        + "Min(\"store_sales\" + \"store_cost\") as S2 FROM " + FOODMART_TABLE // 行号:4111
        + "Group by \"timestamp\" order by s LIMIT 2"; // 行号:4112
    sql(sql, FOODMART) // 行号:4113
        .returnsOrdered("S=-16003.314460250002; S2=1.4768", // 行号:4114
            "S=-14181.57; S2=0.8094") // 行号:4115
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4116
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4117
            + "2992-01-10T00:00:00.000Z]], projects=[[$0, *(-($90), 2), *($91, $91), +($90, $91)]]," // 行号:4118
            + " groups=[{0}], aggs=[[SUM($1), MAX($2), MIN($3)]], post_projects=[[-($1, $2), $3]]," // 行号:4119
            + " sort0=[0], dir0=[ASC], fetch=[2])") // 行号:4120
        .queryContains( // 行号:4121
            new DruidChecker(",\"aggregations\":[{\"type\":\"doubleSum\",\"name\":\"$f1\"," // 行号:4122
                + "\"expression\":\"((- \\\"store_sales\\\") * 2)\"},{\"type\":\"doubleMax\",\"name\"" // 行号:4123
                + ":\"$f2\",\"expression\":\"(\\\"store_cost\\\" * \\\"store_cost\\\")\"}," // 行号:4124
                + "{\"type\":\"doubleMin\",\"name\":\"S2\",\"expression\":\"(\\\"store_sales\\\" " // 行号:4125
                + "+ \\\"store_cost\\\")\"}],\"postAggregations\":[{\"type\":\"expression\"," // 行号:4126
                + "\"name\":\"S\",\"expression\":\"(\\\"$f1\\\" - \\\"$f2\\\")\"}]")); // 行号:4127
  } // 行号:4128

  @Test void testGroupByVirtualColumn() { // 行号:4130
    final String sql = // 行号:4131
        "SELECT \"product_id\" || '_' ||\"city\", SUM(\"store_sales\" + " // 行号:4132
            + "CAST(\"cost\" AS DOUBLE)) as S FROM " + FOODMART_TABLE // 行号:4133
            + "GROUP BY \"product_id\" || '_' || \"city\" LIMIT 2"; // 行号:4134
    sql(sql, FOODMART) // 行号:4135
        .returnsOrdered("EXPR$0=1000_Albany; S=12385.21", "EXPR$0=1000_Altadena; S=8.07") // 行号:4136
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4137
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4138
            + "2992-01-10T00:00:00.000Z]], projects=[[||(||($1, '_'), $29), " // 行号:4139
            + "+($90, CAST($53):DOUBLE)]], groups=[{0}], aggs=[[SUM($1)]], fetch=[2])") // 行号:4140
        .queryContains( // 行号:4141
            new DruidChecker("'queryType':'groupBy'", // 行号:4142
                "{'type':'doubleSum','name':'S','expression':'(\\'store_sales\\' + CAST(\\'cost\\'", // 行号:4143
                "'expression':'concat(concat(\\'product_id\\'", // 行号:4144
                "{'type':'default','dimension':'vc','outputName':'vc','outputType':'STRING'}]," // 行号:4145
                    + "'virtualColumns':[{'type':'expression','name':'vc")); // 行号:4146
  } // 行号:4147

  @Test void testCountOverVirtualColumn() { // 行号:4149
    final String sql = "SELECT COUNT(\"product_id\" || '_' || \"city\") FROM " // 行号:4150
        + FOODMART_TABLE + "WHERE \"state_province\" = 'CA'"; // 行号:4151
    sql(sql, FOODMART) // 行号:4152
        .returnsOrdered("EXPR$0=24441") // 行号:4153
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4154
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4155
            + "2992-01-10T00:00:00.000Z]], filter=[=($30, 'CA')], projects=[[||(||($1, '_'), $29)]]," // 行号:4156
            + " groups=[{}], aggs=[[COUNT($0)]])") // 行号:4157
        .queryContains( // 行号:4158
            new DruidChecker("\"queryType\":\"timeseries\"", // 行号:4159
                "\"aggregator\":{\"type\":\"count\",\"name\":\"EXPR$0\",\"expression\":" // 行号:4160
                    + "\"concat(concat(\\\"product_id\\\"", // 行号:4161
                "\"aggregations\":[{\"type\":\"filtered\",\"filter\":{\"type\":\"not\",\"field\":" // 行号:4162
                    + "{\"type\":\"expression\",\"expression\":\"concat(concat(\\\"product_id\\\"")); // 行号:4163
  } // 行号:4164

  @Test void testAggOverStringToLong() { // 行号:4166
    final String sql = "SELECT SUM(cast(\"product_id\" AS INTEGER)) FROM " + FOODMART_TABLE; // 行号:4167
    sql(sql, FOODMART) // 行号:4168
        .queryContains( // 行号:4169
            new DruidChecker("{'queryType':'timeseries','dataSource':'foodmart'," // 行号:4170
                + "'descending':false,'granularity':'all','aggregations':[{'type':'longSum'," // 行号:4171
                + "'name':'EXPR$0','expression':'CAST(\\'product_id\\'", "LONG")) // 行号:4172
        .returnsOrdered("EXPR$0=68222919"); // 行号:4173
  } // 行号:4174

  @Test void testAggOnTimeExtractColumn() { // 行号:4176
    final String sql = "SELECT SUM(EXTRACT(MONTH FROM \"__time\")) FROM \"wikipedia\""; // 行号:4177
    sql(sql, WIKI_AUTO2) // 行号:4178
        .returnsOrdered("EXPR$0=353196") // 行号:4179
        .queryContains( // 行号:4180
            new DruidChecker("{'queryType':'timeseries','dataSource':'wikipedia'," // 行号:4181
                + "'descending':false,'granularity':'all','aggregations':[{" // 行号:4182
                + "'type':'longSum','name':'EXPR$0','expression':'timestamp_extract(\\'__time\\'")); // 行号:4183
  } // 行号:4184

  @Test void testAggOnTimeExtractColumn2() { // 行号:4186
    final String sql = "SELECT MAX(EXTRACT(MONTH FROM \"timestamp\")) FROM \"foodmart\""; // 行号:4187
    sql(sql, FOODMART) // 行号:4188
        .returnsOrdered("EXPR$0=12") // 行号:4189
        .queryContains( // 行号:4190
            new DruidChecker("{'queryType':'timeseries','dataSource':'foodmart'," // 行号:4191
                + "'descending':false,'granularity':'all','aggregations':[{" // 行号:4192
                + "'type':'longMax','name':'EXPR$0','expression':'timestamp_extract(\\'__time\\'")); // 行号:4193
  } // 行号:4194

  @Test void testStackedAggregateFilters() { // 行号:4196
    final String sql = "SELECT COUNT(\"product_id\") filter (WHERE \"state_province\" = 'CA' " // 行号:4197
        + "OR \"store_sales\" > 100 AND \"product_id\" <> '100'), count(*) FROM " + FOODMART_TABLE; // 行号:4198
    final String query = "{'queryType':'timeseries','dataSource':'foodmart','descending':false," // 行号:4199
        + "'granularity':'all','aggregations':[{'type':'filtered','filter':{'type':'or','fields':" // 行号:4200
        + "[{'type':'selector','dimension':'state_province','value':'CA'},{'type':'and','fields':" // 行号:4201
        + "[{'type':'bound','dimension':'store_sales','lower':'100.0','lowerStrict':true," // 行号:4202
        + "'ordering':'numeric'},{'type':'not','field':{'type':'selector','dimension':'product_id'," // 行号:4203
        + "'value':'100'}}]}]},'aggregator':{'type':'filtered','filter':{'type':'not'," // 行号:4204
        + "'field':{'type':'selector','dimension':'product_id','value':null}},'aggregator':" // 行号:4205
        + "{'type':'count','name':'EXPR$0','fieldName':'product_id'}}}," // 行号:4206
        + "{'type':'count','name':'EXPR$1'}],'intervals':['1900-01-09T00:00:00.000Z/" // 行号:4207
        + "2992-01-10T00:00:00.000Z'],'context':{'skipEmptyBuckets':false}}"; // 行号:4208

    sql(sql, FOODMART) // 行号:4210
        .returnsOrdered("EXPR$0=24441; EXPR$1=86829") // 行号:4211
        .queryContains(new DruidChecker(query)); // 行号:4212
  } // 行号:4213

  @Test void testCastOverPostAggregates() { // 行号:4215
    final String sql = // 行号:4216
        "SELECT CAST(COUNT(*) + SUM(\"store_sales\") as INTEGER) FROM " + FOODMART_TABLE; // 行号:4217
    sql(sql, FOODMART) // 行号:4218
        .returnsOrdered("EXPR$0=652067") // 行号:4219
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4220
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4221
            + "2992-01-10T00:00:00.000Z]], projects=[[$90]], groups=[{}], " // 行号:4222
            + "aggs=[[COUNT(), SUM($0)]], post_projects=[[CAST(+($0, $1)):INTEGER]])"); // 行号:4223
  } // 行号:4224

  @Test void testSubStringOverPostAggregates() { // 行号:4226
    final String sql = // 行号:4227
        "SELECT \"product_id\", SUBSTRING(\"product_id\" from 1 for 2) FROM " + FOODMART_TABLE // 行号:4228
            + " GROUP BY \"product_id\""; // 行号:4229
    sql(sql, FOODMART) // 行号:4230
        .limit(3) // 行号:4231
        .returnsOrdered("product_id=1; EXPR$1=1", // 行号:4232
            "product_id=10; EXPR$1=10", // 行号:4233
            "product_id=100; EXPR$1=10") // 行号:4234
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4235
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4236
            + "2992-01-10T00:00:00.000Z]], projects=[[$1]], groups=[{0}], aggs=[[]], " // 行号:4237
            + "post_projects=[[$0, SUBSTRING($0, 1, 2)]])"); // 行号:4238
  } // 行号:4239

  @Test void testTableQueryExtractYearQuarter() { // 行号:4241
    final String sql = "SELECT * FROM (SELECT CAST((MONTH(\"timestamp\") - 1) / 3 + 1 AS BIGINT)" // 行号:4242
        + "AS qr_timestamp_ok,  SUM(\"store_sales\") AS sum_store_sales, YEAR(\"timestamp\") AS yr_timestamp_ok" // 行号:4243
        + " FROM \"foodmart\" GROUP BY CAST((MONTH(\"timestamp\") - 1) / 3 + 1 AS BIGINT)," // 行号:4244
        + " YEAR(\"timestamp\")) LIMIT_ZERO LIMIT 1"; // 行号:4245

    final String extract_year = "{\"type\":\"extraction\",\"dimension\":\"__time\",\"outputName\":" // 行号:4247
        + "\"extract_year\",\"extractionFn\":{\"type\":\"timeFormat\",\"format\":\"yyyy\"," // 行号:4248
        + "\"timeZone\":\"UTC\",\"locale\":\"en-US\"}}"; // 行号:4249

    final String extract_expression = "\"expression\":\"(((timestamp_extract(\\\"__time\\\","; // 行号:4251
    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4252
        .queryContains( // 行号:4253
            new DruidChecker("\"queryType\":\"groupBy\"", extract_year, extract_expression)) // 行号:4254
        .explainContains("PLAN=EnumerableCalc(expr#0..2=[{inputs}], QR_TIMESTAMP_OK=[$t0], " // 行号:4255
            + "SUM_STORE_SALES=[$t2], YR_TIMESTAMP_OK=[$t1])\n" // 行号:4256
            + "  EnumerableInterpreter\n" // 行号:4257
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4258
            + "2992-01-10T00:00:00.000Z]], projects=[[+(/(-(EXTRACT(FLAG(MONTH), $0), 1), 3), 1), " // 行号:4259
            + "EXTRACT(FLAG(YEAR), $0), $90]], groups=[{0, 1}], aggs=[[SUM($2)]], fetch=[1])"); // 行号:4260
    q.returnsOrdered( // 行号:4261
        "QR_TIMESTAMP_OK=1; SUM_STORE_SALES=139628.35; YR_TIMESTAMP_OK=1997"); // 行号:4262
  } // 行号:4263

  @Test void testTableauQueryExtractMonthDayYear() { // 行号:4265
    final String sql = "SELECT * FROM (SELECT (((YEAR(\"foodmart\".\"timestamp\") * 10000) + " // 行号:4266
        + "(MONTH(\"foodmart\".\"timestamp\") * 100)) + " // 行号:4267
        + "EXTRACT(DAY FROM \"foodmart\".\"timestamp\")) AS md_t_timestamp_ok,\n" // 行号:4268
        + "  SUM(\"foodmart\".\"store_sales\") AS sum_t_other_ok\n" // 行号:4269
        + "FROM \"foodmart\"\n" // 行号:4270
        + "GROUP BY (((YEAR(\"foodmart\".\"timestamp\") * 10000) + (MONTH(\"foodmart\".\"timestamp\")" // 行号:4271
        + " * 100)) + EXTRACT(DAY FROM\"foodmart\".\"timestamp\"))) LIMIT 1"; // 行号:4272
    sql(sql, FOODMART) // 行号:4273
        .returnsOrdered("MD_T_TIMESTAMP_OK=19970101; SUM_T_OTHER_OK=706.34") // 行号:4274
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4275
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4276
            + "2992-01-10T00:00:00.000Z]], projects=[[+(+(*(EXTRACT(FLAG(YEAR), $0), 10000), " // 行号:4277
            + "*(EXTRACT(FLAG(MONTH), $0), 100)), EXTRACT(FLAG(DAY), $0)), $90]], groups=[{0}], " // 行号:4278
            + "aggs=[[SUM($1)]], fetch=[1])") // 行号:4279
        .queryContains(new DruidChecker("\"queryType\":\"groupBy\"")); // 行号:4280
  } // 行号:4281

  @Test void testTableauQuerySubStringHourMinutes() { // 行号:4283
    final String sql = "SELECT * FROM (SELECT CAST(SUBSTRING(CAST(CAST(\"foodmart\".\"timestamp\" " // 行号:4284
        + "AS TIMESTAMP) AS VARCHAR) from 12 for 2) AS INT) AS hr_t_timestamp_ok,\n" // 行号:4285
        + "  MINUTE(\"foodmart\".\"timestamp\") AS mi_t_timestamp_ok,\n" // 行号:4286
        + "  SUM(\"foodmart\".\"store_sales\") AS sum_t_other_ok, EXTRACT(HOUR FROM \"timestamp\") " // 行号:4287
        + " AS hr_t_timestamp_ok2 FROM  \"foodmart\" GROUP BY " // 行号:4288
        + " CAST(SUBSTRING(CAST(CAST(\"foodmart\".\"timestamp\" AS TIMESTAMP) AS VARCHAR) from 12 for 2 ) AS INT)," // 行号:4289
        + "  MINUTE(\"foodmart\".\"timestamp\"), EXTRACT(HOUR FROM \"timestamp\")) LIMIT 1"; // 行号:4290
    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4291
        .explainContains("PLAN=EnumerableCalc(expr#0..3=[{inputs}], proj#0..1=[{exprs}], " // 行号:4292
            + "SUM_T_OTHER_OK=[$t3], HR_T_TIMESTAMP_OK2=[$t2])\n" // 行号:4293
            + "  EnumerableInterpreter\n" // 行号:4294
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4295
            + "2992-01-10T00:00:00.000Z]], projects=[[CAST(SUBSTRING(CAST(CAST($0):TIMESTAMP(0) " // 行号:4296
            + "NOT NULL):VARCHAR " // 行号:4297
            + "NOT NULL, 12, 2)):INTEGER NOT NULL, EXTRACT(FLAG(MINUTE), $0), " // 行号:4298
            + "EXTRACT(FLAG(HOUR), $0), $90]], groups=[{0, 1, 2}], aggs=[[SUM($3)]], fetch=[1])") // 行号:4299
        .queryContains(new DruidChecker("\"queryType\":\"groupBy\"")); // 行号:4300
    q.returnsOrdered("HR_T_TIMESTAMP_OK=0; MI_T_TIMESTAMP_OK=0; " // 行号:4301
        + "SUM_T_OTHER_OK=565238.13; HR_T_TIMESTAMP_OK2=0"); // 行号:4302
  } // 行号:4303

  @Test void testTableauQueryMinutesSecondsExtract() { // 行号:4305
    final String sql =  "SELECT * FROM (SELECT SECOND(\"timestamp\") AS sc_t_timestamp_ok," // 行号:4306
        + "MINUTE(\"timestamp\") AS mi_t_timestamp_ok,  SUM(\"store_sales\") AS sum_store_sales " // 行号:4307
        + " FROM \"foodmart\" GROUP BY SECOND(\"timestamp\"), MINUTE(\"timestamp\"))" // 行号:4308
        + " LIMIT_ZERO LIMIT 1"; // 行号:4309
    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4310
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4311
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4312
            + "2992-01-10T00:00:00.000Z]], projects=[[EXTRACT(FLAG(SECOND), $0), " // 行号:4313
            + "EXTRACT(FLAG(MINUTE), $0), $90]], groups=[{0, 1}], aggs=[[SUM($2)]], fetch=[1])") // 行号:4314
        .queryContains(new DruidChecker("\"queryType\":\"groupBy\"")); // 行号:4315
    q.returnsOrdered( // 行号:4316
        "SC_T_TIMESTAMP_OK=0; MI_T_TIMESTAMP_OK=0; SUM_STORE_SALES=565238.13"); // 行号:4317
  } // 行号:4318

  @Test void testQueryWithExtractsTimes() { // 行号:4320
    final String sql = "SELECT * FROM (SELECT QUARTER(\"__time\") AS QUARTER ," // 行号:4321
        + "EXTRACT(WEEK FROM \"__time\") AS WEEK, DAYOFWEEK(\"__time\") AS DAYOFWEEK, " // 行号:4322
        + "DAYOFMONTH(\"__time\") AS DAYOFMONTH, DAYOFYEAR(\"__time\") AS DAYOFYEAR, " // 行号:4323
        + "SUM(\"added\") AS sum_added  FROM \"wikipedia\" GROUP BY EXTRACT(WEEK FROM \"__time\")," // 行号:4324
        + " DAYOFWEEK(\"__time\"), DAYOFMONTH(\"__time\"), DAYOFYEAR(\"__time\") ," // 行号:4325
        + " QUARTER(\"__time\") order by sum_added) LIMIT_ZERO LIMIT 1"; // 行号:4326

    sql(sql, WIKI_AUTO2) // 行号:4328
        .returnsOrdered("QUARTER=3; WEEK=37; DAYOFWEEK=6; DAYOFMONTH=12;" // 行号:4329
            + " DAYOFYEAR=255; SUM_ADDED=9385573") // 行号:4330
        .explainContains("PLAN=EnumerableCalc(expr#0..5=[{inputs}], QUARTER=[$t4], WEEK=[$t0], " // 行号:4331
            + "DAYOFWEEK=[$t1], DAYOFMONTH=[$t2], DAYOFYEAR=[$t3], SUM_ADDED=[$t5])\n" // 行号:4332
            + "  EnumerableInterpreter\n" // 行号:4333
            + "    DruidQuery(table=[[wiki, wikipedia]], " // 行号:4334
            + "intervals=[[1900-01-01T00:00:00.000Z/3000-01-01T00:00:00.000Z]], " // 行号:4335
            + "projects=[[EXTRACT(FLAG(WEEK), $0), EXTRACT(FLAG(DOW), $0), " // 行号:4336
            + "EXTRACT(FLAG(DAY), $0), EXTRACT(FLAG(DOY), $0), EXTRACT(FLAG(QUARTER), $0), $1]], " // 行号:4337
            + "groups=[{0, 1, 2, 3, 4}], aggs=[[SUM($5)]], fetch=[1])") // 行号:4338
        .queryContains(new DruidChecker("\"queryType\":\"groupBy\"")); // 行号:4339
  } // 行号:4340

  @Test void testCastConcatOverPostAggregates() { // 行号:4342
    final String sql = // 行号:4343
        "SELECT CAST(COUNT(*) + SUM(\"store_sales\") as VARCHAR) || '_' || CAST(SUM(\"store_cost\") " // 行号:4344
            + "AS VARCHAR) FROM " + FOODMART_TABLE; // 行号:4345
    sql(sql, FOODMART) // 行号:4346
        .returnsOrdered("EXPR$0=652067.1299999986_225627.2336000002") // 行号:4347
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4348
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4349
            + "2992-01-10T00:00:00.000Z]], projects=[[$90, $91]], groups=[{}], aggs=[[COUNT(), " // 行号:4350
            + "SUM($0), SUM($1)]], post_projects=[[||(||(CAST(+($0, $1)):VARCHAR, '_'), " // 行号:4351
            + "CAST($2):VARCHAR)]])"); // 行号:4352
  } // 行号:4353

  @Test void testHavingSpecs() { // 行号:4355
    final String sql = "SELECT \"product_id\" AS P, SUM(\"store_sales\") AS S FROM \"foodmart\" " // 行号:4356
        + " GROUP BY  \"product_id\" HAVING  SUM(\"store_sales\") > 220  ORDER BY P LIMIT 2"; // 行号:4357
    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4358
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4359
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4360
            + "2992-01-10T00:00:00.000Z]], projects=[[$1, $90]], groups=[{0}], aggs=[[SUM($1)]], " // 行号:4361
            + "filter=[>($1, 220.0E0)], sort0=[0], dir0=[ASC], fetch=[2])") // 行号:4362
        .queryContains( // 行号:4363
            new DruidChecker("'having':{'type':'filter','filter':{'type':'bound'," // 行号:4364
                + "'dimension':'S','lower':'220.0','lowerStrict':true,'ordering':'numeric'}}")); // 行号:4365
    q.returnsOrdered("P=1; S=236.55", "P=10; S=230.04"); // 行号:4366
  } // 行号:4367

  @Test void testTransposableHavingFilter() { // 行号:4369
    final String sql = "SELECT \"product_id\" AS P, SUM(\"store_sales\") AS S FROM \"foodmart\" " // 行号:4370
        + " GROUP BY  \"product_id\" HAVING  SUM(\"store_sales\") > 220 AND \"product_id\" > '10'" // 行号:4371
        + "  ORDER BY P LIMIT 2"; // 行号:4372
    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4373
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4374
            + "  DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4375
            + "2992-01-10T00:00:00.000Z]], filter=[>($1, '10')], projects=[[$1, $90]], groups=[{0}]," // 行号:4376
            + " aggs=[[SUM($1)]], filter=[>($1, 220.0E0)], sort0=[0], dir0=[ASC], fetch=[2])\n") // 行号:4377
        .queryContains( // 行号:4378
            new DruidChecker("{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'")); // 行号:4379
    q.returnsOrdered("P=100; S=343.2", "P=1000; S=532.62"); // 行号:4380
  } // 行号:4381

  @Test void testProjectSameColumnMultipleTimes() { // 行号:4383
    final String sql = // 行号:4384
        "SELECT \"product_id\" as prod_id1, \"product_id\" as prod_id2, " // 行号:4385
            + "\"store_sales\" as S1, \"store_sales\" as S2 FROM " + FOODMART_TABLE // 行号:4386
            + " order by prod_id1 LIMIT 1"; // 行号:4387
    sql(sql, FOODMART) // 行号:4388
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4389
            + "  BindableSort(sort0=[$0], dir0=[ASC], fetch=[1])\n" // 行号:4390
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4391
            + "2992-01-10T00:00:00.000Z]], projects=[[$1, $1, $90, $90]])") // 行号:4392
        .queryContains( // 行号:4393
            new DruidChecker("{'queryType':'scan','dataSource':'foodmart','intervals':" // 行号:4394
                + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z'],'virtualColumns':[" // 行号:4395
                + "{'type':'expression','name':'vc','expression':'\\'product_id\\'','outputType':" // 行号:4396
                + "'STRING'},{'type':'expression','name':'vc0','expression':'\\'store_sales\\''," // 行号:4397
                + "'outputType':'DOUBLE'}],'columns':['product_id','vc','store_sales','vc0']," // 行号:4398
                + "'resultFormat':'compactedList'}")) // 行号:4399
        .returnsOrdered("PROD_ID1=1; PROD_ID2=1; S1=11.4; S2=11.4"); // 行号:4400
  } // 行号:4401

  @Test void testProjectSameMetricsColumnMultipleTimes() { // 行号:4403
    final String sql = // 行号:4404
        "SELECT \"product_id\" as prod_id1, \"product_id\" as prod_id2, " // 行号:4405
            + "\"store_sales\" as S1, \"store_sales\" as S2 FROM " + FOODMART_TABLE // 行号:4406
            + " order by prod_id1 LIMIT 1"; // 行号:4407
    sql(sql, FOODMART) // 行号:4408
        .explainContains("PLAN=EnumerableInterpreter\n" // 行号:4409
            + "  BindableSort(sort0=[$0], dir0=[ASC], fetch=[1])\n" // 行号:4410
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4411
            + "2992-01-10T00:00:00.000Z]], projects=[[$1, $1, $90, $90]])") // 行号:4412
        .queryContains( // 行号:4413
            new DruidChecker("{\"queryType\":\"scan\",\"dataSource\":\"foodmart\",\"intervals\":" // 行号:4414
                + "[\"1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z\"],\"virtualColumns\":" // 行号:4415
                + "[{\"type\":\"expression\",\"name\":\"vc\",\"expression\":\"\\\"product_id\\\"\"," // 行号:4416
                + "\"outputType\":\"STRING\"},{\"type\":\"expression\",\"name\":\"vc0\"," // 行号:4417
                + "\"expression\":\"\\\"store_sales\\\"\",\"outputType\":\"DOUBLE\"}],\"columns\":" // 行号:4418
                + "[\"product_id\",\"vc\",\"store_sales\",\"vc0\"],\"resultFormat\":\"compactedList\"}")) // 行号:4419
        .returnsOrdered("PROD_ID1=1; PROD_ID2=1; S1=11.4; S2=11.4"); // 行号:4420
  } // 行号:4421

  @Test void testAggSameColumnMultipleTimes() { // 行号:4423
    final String sql = // 行号:4424
        "SELECT \"product_id\" as prod_id1, \"product_id\" as prod_id2, " // 行号:4425
            + "SUM(\"store_sales\") as S1, SUM(\"store_sales\") as S2 FROM " + FOODMART_TABLE // 行号:4426
            + " GROUP BY \"product_id\" ORDER BY prod_id2 LIMIT 1"; // 行号:4427
    sql(sql, FOODMART) // 行号:4428
        .explainContains("PLAN=EnumerableCalc(expr#0..1=[{inputs}], PROD_ID1=[$t0], " // 行号:4429
            + "PROD_ID2=[$t0], S1=[$t1], S2=[$t1])\n" // 行号:4430
            + "  EnumerableInterpreter\n" // 行号:4431
            + "    DruidQuery(table=[[foodmart, foodmart]], intervals=[[1900-01-09T00:00:00.000Z/" // 行号:4432
            + "2992-01-10T00:00:00.000Z]], projects=[[$1, $90]], groups=[{0}], aggs=[[SUM($1)]], " // 行号:4433
            + "sort0=[0], dir0=[ASC], fetch=[1])") // 行号:4434
        .queryContains( // 行号:4435
            new DruidChecker("\"queryType\":\"groupBy\"")) // 行号:4436
        .returnsOrdered("PROD_ID1=1; PROD_ID2=1; S1=236.55; S2=236.55"); // 行号:4437
  } // 行号:4438

  @Test void testGroupBy1() { // 行号:4440
    final String sql = "SELECT SUM(\"store_sales\") FROM \"foodmart\" " // 行号:4441
        + "GROUP BY 1 HAVING (COUNT(1) > 0)"; // 行号:4442
    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4443
        .queryContains( // 行号:4444
            new DruidChecker("{'queryType':'groupBy','dataSource':'foodmart','granularity':'all'," // 行号:4445
                + "'dimensions':[{'type':'default','dimension':'vc','outputName':'vc','outputType':'LONG'}]," // 行号:4446
                + "'virtualColumns':[{'type':'expression','name':'vc','expression':'1','outputType':'LONG'}]," // 行号:4447
                + "'limitSpec':{'type':'default'},'aggregations':[{'type':'doubleSum','name':'EXPR$0'," // 行号:4448
                + "'fieldName':'store_sales'},{'type':'count','name':'$f2'}],'intervals':" // 行号:4449
                + "['1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z'],'having':" // 行号:4450
                + "{'type':'filter','filter':{'type':'bound','dimension':'$f2','lower':'0'," // 行号:4451
                + "'lowerStrict':true,'ordering':'numeric'}}}")); // 行号:4452
    q.returnsOrdered("EXPR$0=565238.13"); // 行号:4453
  } // 行号:4454

  @Test void testFloorQuarter() { // 行号:4456
    String sql = "SELECT floor(\"timestamp\" TO quarter), SUM(\"store_sales\") FROM " // 行号:4457
        + FOODMART_TABLE // 行号:4458
        + " GROUP BY floor(\"timestamp\" TO quarter)"; // 行号:4459

    sql(sql, FOODMART).queryContains( // 行号:4461
        new DruidChecker( // 行号:4462
            "{\"queryType\":\"timeseries\",\"dataSource\":\"foodmart\",\"descending\":false," // 行号:4463
                + "\"granularity\":{\"type\":\"period\",\"period\":\"P3M\",\"timeZone\":\"UTC\"}," // 行号:4464
                + "\"aggregations\":[{\"type\":\"doubleSum\",\"name\":\"EXPR$1\",\"fieldName\":\"store_sales\"}]," // 行号:4465
                + "\"intervals\":[\"1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z\"],\"context\":{\"skipEmptyBuckets\":true}}")); // 行号:4466
  } // 行号:4467

  @Test void testFloorQuarterPlusDim() { // 行号:4469
    String sql = // 行号:4470
        "SELECT floor(\"timestamp\" TO quarter),\"product_id\",  SUM(\"store_sales\") FROM " // 行号:4471
            + FOODMART_TABLE // 行号:4472
            + " GROUP BY floor(\"timestamp\" TO quarter), \"product_id\""; // 行号:4473

    sql(sql, FOODMART).queryContains( // 行号:4475
        new DruidChecker( // 行号:4476
            "{\"queryType\":\"groupBy\",\"dataSource\":\"foodmart\",\"granularity\":\"all\",\"dimensions\":" // 行号:4477
                + "[{\"type\":\"extraction\",\"dimension\":\"__time\",\"outputName\":\"floor_quarter\",\"extractionFn\":{\"type\":\"timeFormat\"", // 行号:4478
            "\"granularity\":{\"type\":\"period\",\"period\":\"P3M\",\"timeZone\":\"UTC\"},\"timeZone\":\"UTC\",\"locale\":\"und\"}}," // 行号:4479
                + "{\"type\":\"default\",\"dimension\":\"product_id\",\"outputName\":\"product_id\",\"outputType\":\"STRING\"}]," // 行号:4480
                + "\"limitSpec\":{\"type\":\"default\"},\"aggregations\":[{\"type\":\"doubleSum\",\"name\":\"EXPR$2\",\"fieldName\":\"store_sales\"}]," // 行号:4481
                + "\"intervals\":[\"1900-01-09T00:00:00.000Z/2992-01-10T00:00:00.000Z\"]}")); // 行号:4482
  } // 行号:4483


  @Test void testExtractQuarterPlusDim() { // 行号:4486
    String sql = // 行号:4487
        "SELECT EXTRACT(quarter from \"timestamp\"),\"product_id\",  SUM(\"store_sales\") FROM " // 行号:4488
            + FOODMART_TABLE // 行号:4489
            + " WHERE \"product_id\" = 1" // 行号:4490
            + " GROUP BY EXTRACT(quarter from \"timestamp\"), \"product_id\""; // 行号:4491

    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4493
        .queryContains( // 行号:4494
            new DruidChecker( // 行号:4495
                "{\"queryType\":\"groupBy\",\"dataSource\":\"foodmart\",\"granularity\":\"all\",\"dimensions\":" // 行号:4496
                    + "[{\"type\":\"default\",\"dimension\":\"vc\",\"outputName\":\"vc\",\"outputType\":\"LONG\"}," // 行号:4497
                    + "{\"type\":\"default\",\"dimension\":\"product_id\",\"outputName\":\"product_id\",\"outputType\":\"STRING\"}]," // 行号:4498
                    + "\"virtualColumns\":[{\"type\":\"expression\",\"name\":\"vc\",\"expression\":\"timestamp_extract(\\\"__time\\\",", // 行号:4499
                "QUARTER")); // 行号:4500
    q.returnsOrdered("EXPR$0=1; product_id=1; EXPR$2=37.05", // 行号:4501
        "EXPR$0=2; product_id=1; EXPR$2=62.7", // 行号:4502
        "EXPR$0=3; product_id=1; EXPR$2=88.35", // 行号:4503
        "EXPR$0=4; product_id=1; EXPR$2=48.45"); // 行号:4504
  } // 行号:4505

  @Test void testExtractQuarter() { // 行号:4507
    String sql = "SELECT EXTRACT(quarter from \"timestamp\"),  SUM(\"store_sales\") FROM " // 行号:4508
        + FOODMART_TABLE // 行号:4509
        + " GROUP BY EXTRACT(quarter from \"timestamp\")"; // 行号:4510

    CalciteAssert.AssertQuery q = sql(sql, FOODMART) // 行号:4512
        .queryContains( // 行号:4513
            new DruidChecker( // 行号:4514
                "{\"queryType\":\"groupBy\",\"dataSource\":\"foodmart\",\"granularity\":\"all\"," // 行号:4515
                    + "\"dimensions\":[{\"type\":\"default\",\"dimension\":\"vc\",\"outputName\":\"vc\",\"outputType\":\"LONG\"}]," // 行号:4516
                    + "\"virtualColumns\":[{\"type\":\"expression\",\"name\":\"vc\",\"expression\":\"timestamp_extract(\\\"__time\\\",", // 行号:4517
                "QUARTER")); // 行号:4518
    q.returnsOrdered("EXPR$0=1; EXPR$1=139628.35", // 行号:4519
        "EXPR$0=2; EXPR$1=132666.27", // 行号:4520
        "EXPR$0=3; EXPR$1=140271.89", // 行号:4521
        "EXPR$0=4; EXPR$1=152671.62"); // 行号:4522
  } // 行号:4523


  /** // 行号:4526
   * Test case for // 行号:4527
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2262">[CALCITE-2262]
   * Druid adapter: Allow count(*) to be pushed when other aggregate functions // 行号:4529
   * are present</a>. // 行号:4530
   */
  @Test void testSelectCountStarPlusOtherAggs() { // 行号:4532
    final String sql = "SELECT COUNT(*), SUM(\"store_sales\"), COUNT(\"store_sales\") FROM " // 行号:4533
        + FOODMART_TABLE; // 行号:4534
    sql(sql, FOODMART) // 行号:4535
        .returnsOrdered("EXPR$0=86829; EXPR$1=565238.13; EXPR$2=86829") // 行号:4536
        .queryContains( // 行号:4537
            new DruidChecker("{'queryType':'timeseries'", "'context':{'skipEmptyBuckets':false}}")); // 行号:4538

  } // 行号:4540

  @Test void testGroupByWithBooleanExpression() { // 行号:4542
    final String sql = "SELECT \"product_id\" > 1000 as pid_category, COUNT(\"store_sales\") FROM " // 行号:4543
        + FOODMART_TABLE + "GROUP BY \"product_id\" > 1000"; // 行号:4544
    sql(sql, FOODMART) // 行号:4545
        .returnsOrdered("PID_CATEGORY=0; EXPR$1=55789", // 行号:4546
            "PID_CATEGORY=1; EXPR$1=31040") // 行号:4547
        .queryContains( // 行号:4548
            new DruidChecker("{\"queryType\":\"groupBy\"", // 行号:4549
                "\"dimension\":\"vc\",\"outputName\":\"vc\",\"outputType\":\"LONG\"}]")); // 行号:4550

  } // 行号:4552
} // 行号:4553
