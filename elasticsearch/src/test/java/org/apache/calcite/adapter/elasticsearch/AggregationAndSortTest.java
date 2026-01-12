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
package org.apache.calcite.adapter.elasticsearch; // 声明包名,属于Elasticsearch适配器测试包

import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite JDBC连接类,用于获取Calcite特定的连接功能
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类,表示可扩展的Schema对象
import org.apache.calcite.schema.impl.ViewTable; // 导入ViewTable类,用于创建视图表
import org.apache.calcite.test.CalciteAssert; // 导入Calcite断言工具类,用于测试SQL查询
import org.apache.calcite.test.ElasticsearchChecker; // 导入Elasticsearch检查器,用于验证生成的ES查询
import org.apache.calcite.util.Bug; // 导入Bug工具类,用于跟踪已知的Calcite bug

import com.fasterxml.jackson.core.JsonParser; // 导入Jackson JSON解析器,用于解析JSON数据
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson对象映射器,用于JSON与Java对象转换
import com.fasterxml.jackson.databind.node.ObjectNode; // 导入Jackson对象节点,用于创建JSON对象
import com.google.common.collect.ImmutableMap; // 导入Guava不可变Map,用于创建不可变的映射

import org.junit.jupiter.api.Assumptions; // 导入JUnit5假设工具,用于条件性跳过测试
import org.junit.jupiter.api.BeforeAll; // 导入JUnit5 BeforeAll注解,标记在所有测试前执行的方法
import org.junit.jupiter.api.Test; // 导入JUnit5 Test注解,标记测试方法
import org.junit.jupiter.api.parallel.ResourceAccessMode; // 导入资源访问模式枚举,用于并行测试控制
import org.junit.jupiter.api.parallel.ResourceLock; // 导入资源锁注解,用于控制测试对共享资源的访问

import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器,用于获取数据库连接
import java.sql.SQLException; // 导入SQL异常类,处理数据库操作异常
import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.Arrays; // 导入数组工具类
import java.util.Collections; // 导入集合工具类
import java.util.List; // 导入List接口
import java.util.Locale; // 导入Locale类,用于本地化
import java.util.Map; // 导入Map接口

/**
 * Testing Elasticsearch aggregation transformations.
 * 测试Elasticsearch聚合转换功能的测试类
 * 该类专门用于验证Calcite与Elasticsearch集成时聚合操作的正确性
 * 包括GROUP BY、聚合函数(COUNT、SUM、MIN、MAX、AVG等)、排序等功能的测试
 * 同时测试了不同数据类型(keyword、date、integer、text)的聚合行为
 * 以及NULL值处理、近似计数、类型转换等高级特性
 */
@ResourceLock(value = "elasticsearch-scrolls", mode = ResourceAccessMode.READ) // 使用资源锁确保ES滚动操作在测试期间不会被并发修改,READ模式表示只读访问
class AggregationAndSortTest { // 定义测试类,用于测试Elasticsearch适配器的聚合和排序功能

  public static final EmbeddedElasticsearchPolicy NODE = EmbeddedElasticsearchPolicy.create(); // 创建嵌入式Elasticsearch策略实例,用于管理测试用的ES节点,静态final确保所有测试共享同一个ES实例

  private static final String NAME = "aggs"; // 定义索引名称常量,用于在ES中创建和操作测试索引

  @BeforeAll // 该注解表示此方法在所有测试方法执行前运行一次,用于初始化测试环境
  public static void setupInstance() throws Exception { // 静态初始化方法,设置测试用的Elasticsearch实例和数据
    final Map<String, String> mappings = ImmutableMap.<String, String>builder() // 创建字段映射配置,定义ES索引中各字段的数据类型
        .put("cat1", "keyword") // 定义cat1字段为keyword类型,适合精确匹配和聚合
        .put("cat2", "keyword") // 定义cat2字段为keyword类型
        .put("cat3", "keyword") // 定义cat3字段为keyword类型
        .put("cat4", "date") // 定义cat4字段为date类型,用于日期处理
        .put("cat5", "integer") // 定义cat5字段为integer类型,用于整数数值
        .put("cat6", "text") // 定义cat6字段为text类型,用于全文搜索
        .put("val1", "long") // 定义val1字段为long类型,用于长整型数值
        .put("val2", "long") // 定义val2字段为long类型
        .build(); // 构建不可变的映射Map

    NODE.createIndex(NAME, mappings); // 在ES节点中创建名为"aggs"的索引,并应用字段映射

    String doc1 = "{cat1:'a', cat2:'g', val1:1, cat4:'2018-01-01', cat5:1}"; // 定义第一个测试文档JSON字符串,包含多个字段
    String doc2 = "{cat2:'g', cat3:'y', val2:5, cat4:'2019-12-12', cat6:'text1'}"; // 定义第二个测试文档JSON字符串,部分字段为null
    String doc3 = "{cat1:'b', cat2:'h', cat3:'z', cat5:2, val1:7, val2:42}"; // 定义第三个测试文档JSON字符串

    final ObjectMapper mapper = new ObjectMapper() // 创建Jackson对象映射器实例,用于JSON解析
        .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES) // 允许JSON字段名不加引号,使JSON更易读
        .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES); // 允许使用单引号,避免过多的引号转义

    final List<ObjectNode> docs = new ArrayList<>(); // 创建文档列表,用于存储解析后的JSON对象节点
    for (String text : Arrays.asList(doc1, doc2, doc3)) { // 遍历三个文档字符串
      docs.add((ObjectNode) mapper.readTree(text)); // 将JSON字符串解析为ObjectNode并添加到列表中
    }

    NODE.insertBulk(NAME, docs); // 批量插入文档到ES索引中,完成测试数据的初始化
  }

  private static Connection createConnection() throws SQLException { // 创建并返回Calcite JDBC连接的方法,用于建立与ES的连接
    final Connection connection = // 创建JDBC连接对象
        DriverManager.getConnection("jdbc:calcite:lex=JAVA"); // 使用Calcite JDBC驱动获取连接,lex=JAVA表示使用Java词法分析器
    final SchemaPlus root = // 获取根Schema对象
        connection.unwrap(CalciteConnection.class).getRootSchema(); // 将连接解包为CalciteConnection并获取根Schema

    root.add("elastic", // 向根Schema添加名为"elastic"的子Schema
        new ElasticsearchSchema(NODE.restClient(), NODE.mapper(), NAME)); // 创建ElasticsearchSchema实例,传入ES REST客户端、映射器和索引名称

    // add calcite view programmatically
    final String viewSql = // 定义视图SQL语句,将ES的_MAP字段映射为结构化列
        String.format(Locale.ROOT, // 使用ROOT语言环境格式化字符串
            "select _MAP['cat1'] AS \"cat1\", " // 从_MAP中提取cat1字段并重命名为cat1
                + " _MAP['cat2'] AS \"cat2\", " // 从_MAP中提取cat2字段并重命名为cat2
                + " _MAP['cat3'] AS \"cat3\", " // 从_MAP中提取cat3字段并重命名为cat3
                + " _MAP['cat4'] AS \"cat4\", " // 从_MAP中提取cat4字段并重命名为cat4
                + " _MAP['cat5'] AS \"cat5\", " // 从_MAP中提取cat5字段并重命名为cat5
                + " _MAP['cat6'] AS \"cat6\", " // 从_MAP中提取cat6字段并重命名为cat6
                + " _MAP['val1'] AS \"val1\", " // 从_MAP中提取val1字段并重命名为val1
                + " _MAP['val2'] AS \"val2\" " // 从_MAP中提取val2字段并重命名为val2
                + " from \"elastic\".\"%s\"", // 从elastic schema的指定索引查询
            NAME); // 使用索引名称替换占位符

    root.add("view", // 向根Schema添加名为"view"的视图
        ViewTable.viewMacro(root, viewSql, // 创建视图表宏,传入根Schema和视图SQL
            Collections.singletonList("elastic"), // 指定视图依赖的schema路径列表
            Arrays.asList("elastic", "view"), // 指定视图的完整路径
            false)); // 不强制使用流式视图
    return connection; // 返回创建的JDBC连接
  }

  /**
   * Currently the patterns like below will be converted to Search in range
   * which is not supported in elastic search adapter.
   * (val1 >= 10 and val1 <= 20)
   * (val1 <= 10 or val1 >=20)
   * (val1 <= 10) or (val1 > 15 and val1 <= 20)
   * So disable this test case until the translation from Search in range
   * to rang Query in ES is implemented.
   */
  @Test void searchInRange() { // 测试范围查询功能的方法,验证BETWEEN类型的查询条件
    Assumptions.assumeTrue(Bug.CALCITE_4645_FIXED, "CALCITE-4645"); // 假设检查:只有当CALCITE-4645 bug修复后才执行此测试,否则跳过
    CalciteAssert.that() // 创建Calcite断言构建器
        .with(AggregationAndSortTest::createConnection) // 使用createConnection方法创建数据库连接
        .query("select count(*) from view where val1 >= 10 and val1 <=20") // 执行SQL查询:统计val1在10到20之间的记录数
        .returns("EXPR$0=1\n"); // 验证查询结果返回1条记录(只有doc3的val1=7不满足,其他满足)

    CalciteAssert.that() // 创建第二个断言
        .with(AggregationAndSortTest::createConnection) // 使用createConnection方法创建连接
        .query("select count(*) from view where val1 <= 10 or val1 >=20") // 执行SQL查询:统计val1小于等于10或大于等于20的记录数
        .returns("EXPR$0=2\n"); // 验证查询结果返回2条记录

    CalciteAssert.that() // 创建第三个断言
        .with(AggregationAndSortTest::createConnection) // 使用createConnection方法创建连接
        .query("select count(*) from view where val1 <= 10 or (val1 > 15 and val1 <= 20)") // 执行复杂的范围查询
        .returns("EXPR$0=2\n"); // 验证查询结果返回2条记录
  }

  @Test void countStar() { // 测试COUNT(*)聚合函数的方法,验证基本的计数功能
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*) from view") // 执行查询:统计视图中的总记录数
        .queryContains( // 验证生成的ES查询包含特定内容
            ElasticsearchChecker.elasticsearchChecker( // 创建ES查询检查器
                "_source:false, 'stored_fields': '_none_', size:0, track_total_hits:true")) // 检查ES查询参数:不返回源字段,不返回存储字段,大小为0,跟踪总命中数
        .returns("EXPR$0=3\n"); // 验证返回3条记录

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*) from view where cat1 = 'a'") // 查询cat1等于'a'的记录数
        .returns("EXPR$0=1\n"); // 验证返回1条记录(doc1)

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*) from view where cat1 in ('a', 'b')") // 查询cat1在('a','b')中的记录数
        .returns("EXPR$0=2\n"); // 验证返回2条记录(doc1和doc3)

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*) from view where val1 in (10, 20)") // 查询val1在(10,20)中的记录数
        .returns("EXPR$0=0\n"); // 验证返回0条记录(没有文档的val1是10或20)

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*) from view where cat4 in ('2018-01-01', '2019-12-12')") // 查询cat4在指定日期中的记录数
        .returns("EXPR$0=2\n"); // 验证返回2条记录(doc1和doc2)

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*) from view where cat4 not in ('2018-01-01', '2019-12-12')") // 查询cat4不在指定日期中的记录数
        .returns("EXPR$0=1\n"); // 验证返回1条记录(doc3的cat4为null)
  }

  @Test void all() { // 测试多个聚合函数同时使用的方法,验证COUNT、SUM、MIN、MAX等聚合函数
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*), sum(val1), sum(val2) from view") // 执行查询:同时计算count、sum(val1)和sum(val2)
        .queryContains( // 验证生成的ES查询
            ElasticsearchChecker.elasticsearchChecker( // 创建ES查询检查器
                "_source:false, size:0, track_total_hits:true", // 基础查询参数
                "'stored_fields': '_none_'", // 不返回存储字段
                "aggregations:{'EXPR$0.value_count.field': '_id'", // 检查聚合:使用_id字段进行计数
                    "'EXPR$1.sum.field': 'val1'", // 检查聚合:对val1字段求和
                    "'EXPR$2.sum.field': 'val2'}")) // 检查聚合:对val2字段求和
        .returns("EXPR$0=3; EXPR$1=8.0; EXPR$2=47.0\n"); // 验证结果:总数3,val1总和8.0(1+7),val2总和47.0(5+42)

    CalciteAssert.that() // 创建第二个断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select min(val1), max(val2), count(*) from view") // 执行查询:计算min(val1)、max(val2)和count(*)
        .queryContains( // 验证生成的ES查询
            ElasticsearchChecker.elasticsearchChecker( // 创建ES查询检查器
                "_source:false, 'stored_fields': '_none_', size:0, track_total_hits:true", // 基础查询参数
                "aggregations:{'EXPR$0.min.field': 'val1'", // 检查聚合:计算val1的最小值
                "'EXPR$1.max.field': 'val2'", // 检查聚合:计算val2的最大值
                "'EXPR$2.value_count.field': '_id'}")) // 检查聚合:使用_id字段计数
        .returns("EXPR$0=1.0; EXPR$1=42.0; EXPR$2=3\n"); // 验证结果:最小值1.0,最大值42.0,总数3
  }

  @Test void cat1() { // 测试按cat1字段分组的方法,验证单列GROUP BY功能
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, sum(val1), sum(val2) from view group by cat1") // 执行查询:按cat1分组,计算每组val1和val2的和
        .returnsUnordered("cat1=null; EXPR$1=0.0; EXPR$2=5.0", // 验证结果:cat1为null的组,val1和0.0,val2和5.0
                        "cat1=a; EXPR$1=1.0; EXPR$2=0.0", // 验证结果:cat1='a'的组,val1和1.0,val2和0.0
                        "cat1=b; EXPR$1=7.0; EXPR$2=42.0"); // 验证结果:cat1='b'的组,val1和7.0,val2和42.0

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, count(*) from view group by cat1") // 执行查询:按cat1分组,计算每组的记录数
        .returnsUnordered("cat1=null; EXPR$1=1", // 验证结果:cat1为null的组有1条记录
            "cat1=a; EXPR$1=1", // 验证结果:cat1='a'的组有1条记录
            "cat1=b; EXPR$1=1"); // 验证结果:cat1='b'的组有1条记录

    // different order for agg functions
    CalciteAssert.that() // 创建断言,测试聚合函数顺序不同的情况
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select count(*), cat1 from view group by cat1") // 执行查询:count(*)在前,cat1在后
        .returnsUnordered("EXPR$0=1; cat1=a", // 验证结果:cat1='a'的组有1条记录
            "EXPR$0=1; cat1=b", // 验证结果:cat1='b'的组有1条记录
            "EXPR$0=1; cat1=null"); // 验证结果:cat1为null的组有1条记录

    CalciteAssert.that() // 创建断言,测试多个聚合函数
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, count(*), sum(val1), sum(val2) from view group by cat1") // 执行查询:按cat1分组,计算count、sum(val1)、sum(val2)
        .returnsUnordered("cat1=a; EXPR$1=1; EXPR$2=1.0; EXPR$3=0.0", // 验证结果:cat1='a'的组
                "cat1=b; EXPR$1=1; EXPR$2=7.0; EXPR$3=42.0", // 验证结果:cat1='b'的组
                "cat1=null; EXPR$1=1; EXPR$2=0.0; EXPR$3=5.0"); // 验证结果:cat1为null的组
  }

  @Test void cat2() { // 测试按cat2字段分组的方法,验证MIN和MAX聚合函数
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat2, min(val1), max(val1), min(val2), max(val2) from view group by cat2") // 执行查询:按cat2分组,计算min(val1)、max(val1)、min(val2)、max(val2)
        .returnsUnordered("cat2=g; EXPR$1=1.0; EXPR$2=1.0; EXPR$3=5.0; EXPR$4=5.0", // 验证结果:cat2='g'的组,val1最小和最大都是1.0,val2最小和最大都是5.0
            "cat2=h; EXPR$1=7.0; EXPR$2=7.0; EXPR$3=42.0; EXPR$4=42.0"); // 验证结果:cat2='h'的组,val1最小和最大都是7.0,val2最小和最大都是42.0

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat2, sum(val1), sum(val2) from view group by cat2") // 执行查询:按cat2分组,计算sum(val1)和sum(val2)
        .returnsUnordered("cat2=g; EXPR$1=1.0; EXPR$2=5.0", // 验证结果:cat2='g'的组,val1和1.0,val2和5.0
                  "cat2=h; EXPR$1=7.0; EXPR$2=42.0"); // 验证结果:cat2='h'的组,val1和7.0,val2和42.0

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat2, count(*) from view group by cat2") // 执行查询:按cat2分组,计算每组的记录数
        .returnsUnordered("cat2=g; EXPR$1=2", // 验证结果:cat2='g'的组有2条记录(doc1和doc2)
                  "cat2=h; EXPR$1=1"); // 验证结果:cat2='h'的组有1条记录(doc3)
  }

  @Test void cat1Cat2() { // 测试按cat1和cat2两个字段分组的方法,验证多列GROUP BY功能
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, cat2, sum(val1), sum(val2) from view group by cat1, cat2") // 执行查询:按cat1和cat2分组,计算sum(val1)和sum(val2)
        .returnsUnordered("cat1=a; cat2=g; EXPR$2=1.0; EXPR$3=0.0", // 验证结果:cat1='a',cat2='g'的组
            "cat1=null; cat2=g; EXPR$2=0.0; EXPR$3=5.0", // 验证结果:cat1=null,cat2='g'的组
            "cat1=b; cat2=h; EXPR$2=7.0; EXPR$3=42.0"); // 验证结果:cat1='b',cat2='h'的组

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, cat2, count(*) from view group by cat1, cat2") // 执行查询:按cat1和cat2分组,计算每组的记录数
        .returnsUnordered("cat1=a; cat2=g; EXPR$2=1", // 验证结果:cat1='a',cat2='g'的组有1条记录
            "cat1=null; cat2=g; EXPR$2=1", // 验证结果:cat1=null,cat2='g'的组有1条记录
            "cat1=b; cat2=h; EXPR$2=1"); // 验证结果:cat1='b',cat2='h'的组有1条记录
  }

  @Test void cat1Cat3() { // 测试按cat1和cat3两个字段分组的方法,验证包含null值的多列GROUP BY
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, cat3, sum(val1), sum(val2) from view group by cat1, cat3") // 执行查询:按cat1和cat3分组,计算sum(val1)和sum(val2)
        .returnsUnordered("cat1=a; cat3=null; EXPR$2=1.0; EXPR$3=0.0", // 验证结果:cat1='a',cat3=null的组
            "cat1=null; cat3=y; EXPR$2=0.0; EXPR$3=5.0", // 验证结果:cat1=null,cat3='y'的组
            "cat1=b; cat3=z; EXPR$2=7.0; EXPR$3=42.0"); // 验证结果:cat1='b',cat3='z'的组
  }

  /** Tests the {@link org.apache.calcite.sql.SqlKind#ANY_VALUE} aggregate
   * function. */
  @Test void anyValue() { // 测试ANY_VALUE聚合函数的方法,该函数返回组中的任意一个非null值
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, any_value(cat2) from view group by cat1") // 执行查询:按cat1分组,返回每组中cat2的任意值
        .returnsUnordered("cat1=a; EXPR$1=g", // 验证结果:cat1='a'的组,返回cat2='g'
            "cat1=null; EXPR$1=g", // 验证结果:cat1=null的组,返回cat2='g'
            "cat1=b; EXPR$1=h"); // 验证结果:cat1='b'的组,返回cat2='h'

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat2, any_value(cat1) from view group by cat2") // 执行查询:按cat2分组,返回每组中cat1的任意值
        .returnsUnordered("cat2=g; EXPR$1=a", // 验证结果:cat2='g'的组,返回cat1='a'(也可能是null)
            "cat2=h; EXPR$1=b"); // 验证结果:cat2='h'的组,返回cat1='b'

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat2, any_value(cat3) from view group by cat2") // 执行查询:按cat2分组,返回每组中cat3的任意值
        .returnsUnordered("cat2=g; EXPR$1=y", // 验证结果:cat2='g'的组,返回cat3='y'(也可能是null)
            "cat2=h; EXPR$1=z"); // 验证结果:cat2='h'的组,返回cat3='z'
  }

  @Test void anyValueWithOtherAgg() { // 测试ANY_VALUE与其他聚合函数配合使用的方法
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, any_value(cat2), max(val1) from view group by cat1") // 执行查询:按cat1分组,计算any_value(cat2)和max(val1)
        .returnsUnordered("cat1=a; EXPR$1=g; EXPR$2=1.0", // 验证结果:cat1='a'的组,any_value(cat2)='g',max(val1)=1.0
            "cat1=null; EXPR$1=g; EXPR$2=null", // 验证结果:cat1=null的组,any_value(cat2)='g',max(val1)=null
            "cat1=b; EXPR$1=h; EXPR$2=7.0"); // 验证结果:cat1='b'的组,any_value(cat2)='h',max(val1)=7.0

    CalciteAssert.that() // 创建断言,测试聚合函数顺序不同的情况
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select max(val1), cat1, any_value(cat2) from view group by cat1") // 执行查询:max(val1)在前,cat1和any_value在后
        .returnsUnordered("EXPR$0=1.0; cat1=a; EXPR$2=g", // 验证结果:cat1='a'的组,max(val1)=1.0,any_value(cat2)='g'
            "EXPR$0=null; cat1=null; EXPR$2=g", // 验证结果:cat1=null的组,max(val1)=null,any_value(cat2)='g'
            "EXPR$0=7.0; cat1=b; EXPR$2=h"); // 验证结果:cat1='b'的组,max(val1)=7.0,any_value(cat2)='h'

    CalciteAssert.that() // 创建断言,测试any_value在前的情况
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select any_value(cat2), cat1, max(val1) from view group by cat1") // 执行查询:any_value(cat2)在前
        .returnsUnordered("EXPR$0=g; cat1=a; EXPR$2=1.0", // 验证结果:cat1='a'的组,any_value(cat2)='g',max(val1)=1.0
            "EXPR$0=g; cat1=null; EXPR$2=null", // 验证结果:cat1=null的组,any_value(cat2)='g',max(val1)=null
            "EXPR$0=h; cat1=b; EXPR$2=7.0"); // 验证结果:cat1='b'的组,any_value(cat2)='h',max(val1)=7.0
  }

  @Test void cat1Cat2Cat3() { // 测试按cat1、cat2、cat3三个字段分组的方法,验证三列GROUP BY功能
    CalciteAssert.that() // 创建断言构建器
            .with(AggregationAndSortTest::createConnection) // 设置连接
            .query("select cat1, cat2, cat3, count(*), sum(val1), sum(val2) from view " // 执行查询:按cat1、cat2、cat3分组
                + "group by cat1, cat2, cat3") // GROUP BY子句
            .returnsUnordered("cat1=a; cat2=g; cat3=null; EXPR$3=1; EXPR$4=1.0; EXPR$5=0.0", // 验证结果:cat1='a',cat2='g',cat3=null的组
                    "cat1=b; cat2=h; cat3=z; EXPR$3=1; EXPR$4=7.0; EXPR$5=42.0", // 验证结果:cat1='b',cat2='h',cat3='z'的组
                    "cat1=null; cat2=g; cat3=y; EXPR$3=1; EXPR$4=0.0; EXPR$5=5.0"); // 验证结果:cat1=null,cat2='g',cat3='y'的组
  }

  /**
   * Group by
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/date.html">
   * date</a> data type.
   */
  @Test void dateCat() { // 测试按date类型字段分组的方法,验证日期字段的GROUP BY功能
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat4, sum(val1) from view group by cat4") // 执行查询:按cat4(date类型)分组,计算sum(val1)
        .returnsUnordered("cat4=1514764800000; EXPR$1=1.0", // 验证结果:cat4=2018-01-01(时间戳1514764800000)的组,val1和1.0
            "cat4=1576108800000; EXPR$1=0.0", // 验证结果:cat4=2019-12-12(时间戳1576108800000)的组,val1和0.0
            "cat4=null; EXPR$1=7.0"); // 验证结果:cat4为null的组,val1和7.0
  }

  /**
   * Group by
   * <a href="https://www.elastic.co/guide/en/elasticsearch/reference/current/number.html">
   * number</a> data type.
   */
  @Test void integerCat() { // 测试按integer类型字段分组的方法,验证数值字段的GROUP BY功能
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat5, sum(val1) from view group by cat5") // 执行查询:按cat5(integer类型)分组,计算sum(val1)
        .returnsUnordered("cat5=1; EXPR$1=1.0", // 验证结果:cat5=1的组,val1和1.0
            "cat5=null; EXPR$1=0.0", // 验证结果:cat5为null的组,val1和0.0
            "cat5=2; EXPR$1=7.0"); // 验证结果:cat5=2的组,val1和7.0
  }

  /**
   * Validate {@link org.apache.calcite.sql.fun.SqlStdOperatorTable#APPROX_COUNT_DISTINCT}.
   */
  @Test void approximateCountDistinct() { // 测试APPROX_COUNT_DISTINCT近似计数函数的方法,该函数用于快速估算不同值的数量
    // approx_count_distinct counts distinct *non-null* values
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select approx_count_distinct(cat1) from view") // 执行查询:计算cat1的不同值数量(近似)
        .returnsUnordered("EXPR$0=2"); // 验证结果:cat1有2个不同的非null值('a'和'b')

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select approx_count_distinct(cat2) from view") // 执行查询:计算cat2的不同值数量(近似)
        .returnsUnordered("EXPR$0=2"); // 验证结果:cat2有2个不同的非null值('g'和'h')

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, approx_count_distinct(val1) from view group by cat1") // 执行查询:按cat1分组,计算每组val1的不同值数量
        .returnsUnordered("cat1=a; EXPR$1=1", // 验证结果:cat1='a'的组,val1有1个不同值(1)
                          "cat1=b; EXPR$1=1", // 验证结果:cat1='b'的组,val1有1个不同值(7)
                          "cat1=null; EXPR$1=0"); // 验证结果:cat1=null的组,val1有0个不同值(只有null)
    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, approx_count_distinct(val2) from view group by cat1") // 执行查询:按cat1分组,计算每组val2的不同值数量
        .returnsUnordered("cat1=a; EXPR$1=0", // 验证结果:cat1='a'的组,val2有0个不同值(只有null)
                          "cat1=b; EXPR$1=1", // 验证结果:cat1='b'的组,val2有1个不同值(42)
                          "cat1=null; EXPR$1=1"); // 验证结果:cat1=null的组,val2有1个不同值(5)
  }

  /** Tests aggregation with cast,
   * {@code select max(cast(_MAP['foo'] as integer)) from tbl}. */
  @Test void aggregationWithCast() { // 测试聚合函数与类型转换配合使用的方法
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query( // 执行查询:对_MAP中的字段进行类型转换后聚合
            String.format(Locale.ROOT, "select max(cast(_MAP['val1'] as integer)) as v1, " // 将val1转换为integer后计算最大值,别名为v1
                + "min(cast(_MAP['val2'] as integer)) as v2 from elastic.%s", NAME)) // 将val2转换为integer后计算最小值,别名为v2
        .queryContains( // 验证生成的ES查询
            ElasticsearchChecker.elasticsearchChecker( // 创建ES查询检查器
            "_source:false, 'stored_fields': '_none_', size:0, track_total_hits:true", // 基础查询参数
            "aggregations:{'v1.max.field': 'val1'", // 检查聚合:对val1字段计算最大值
            "'v2.min.field': 'val2'}")) // 检查聚合:对val2字段计算最小值
        .returnsUnordered("v1=7; v2=5"); // 验证结果:最大值7,最小值5
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4954">[CALCITE-4954]
   * Group TEXT field failed in Elasticsearch Adapter</a>.
   */
  @Test void testGroupTextField() { // 测试按TEXT类型字段分组的方法,验证TEXT字段的GROUP BY功能(CALCITE-4954 bug修复验证)
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat6, count(1) as CNT from view group by cat6") // 执行查询:按cat6(text类型)分组,计算每组的记录数
        .returnsUnordered("cat6=null; CNT=2", // 验证结果:cat6为null的组有2条记录(doc1和doc3)
            "cat6=text1; CNT=1"); // 验证结果:cat6='text1'的组有1条记录(doc2)

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat1, cat6 from view group by cat1, cat6") // 执行查询:按cat1和cat6分组
        .returnsUnordered("cat1=a; cat6=null", // 验证结果:cat1='a',cat6=null的组
            "cat1=b; cat6=null", // 验证结果:cat1='b',cat6=null的组
            "cat1=null; cat6=text1"); // 验证结果:cat1=null,cat6='text1'的组
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4860">[CALCITE-4860]
   * In Elasticsearch adapter, support NULLS FIRST and NULLS LAST query</a>.
   */
  @Test void testNullsSort() { // 测试NULLS FIRST和NULLS LAST排序功能的方法,验证null值在排序中的位置(CALCITE-4860 bug修复验证)
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat6, cat5 from view order by cat5 desc nulls last") // 执行查询:按cat5降序排序,null值排在最后
        .returns("cat6=null; cat5=2\n" // 验证结果:第一条记录cat5=2,非null值排在前面
            + "cat6=null; cat5=1\n" // 验证结果:第二条记录cat5=1
            + "cat6=text1; cat5=null\n"); // 验证结果:第三条记录cat5=null,排在最后

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat6, cat5 from view order by cat5 desc nulls first") // 执行查询:按cat5降序排序,null值排在最前
        .returns("cat6=text1; cat5=null\n" // 验证结果:第一条记录cat5=null,排在最前
            + "cat6=null; cat5=2\n" // 验证结果:第二条记录cat5=2
            + "cat6=null; cat5=1\n"); // 验证结果:第三条记录cat5=1

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat6, cat5 from view order by cat5 asc nulls last") // 执行查询:按cat5升序排序,null值排在最后
        .returns("cat6=null; cat5=1\n" // 验证结果:第一条记录cat5=1,最小值排在前面
            + "cat6=null; cat5=2\n" // 验证结果:第二条记录cat5=2
            + "cat6=text1; cat5=null\n"); // 验证结果:第三条记录cat5=null,排在最后

    CalciteAssert.that() // 创建断言
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat6, cat5 from view order by cat5 asc nulls first") // 执行查询:按cat5升序排序,null值排在最前
        .returns("cat6=text1; cat5=null\n" // 验证结果:第一条记录cat5=null,排在最前
            + "cat6=null; cat5=1\n" // 验证结果:第二条记录cat5=1
            + "cat6=null; cat5=2\n"); // 验证结果:第三条记录cat5=2
  }

  @Test void testOrderByWithGroupBy() { // 测试GROUP BY与ORDER BY结合使用的方法
    // Once CALCITE-4868 is fixed, we can enable this test
    Assumptions.assumeTrue(Bug.CALCITE_4868_FIXED, "CALCITE-4868"); // 假设检查:只有当CALCITE-4868 bug修复后才执行此测试
    CalciteAssert.that() // 创建断言构建器
        .with(AggregationAndSortTest::createConnection) // 设置连接
        .query("select cat6, cat5 from view group by cat6, cat5 " // 执行查询:按cat6和cat5分组
            + "order by cat5 desc nulls last") // 按cat5降序排序,null值排在最后
        .returns("cat6=null; cat5=2\n" // 验证结果:第一条记录cat5=2
            + "cat6=null; cat5=1\n" // 验证结果:第二条记录cat5=1
            + "cat6=text1; cat5=null\n"); // 验证结果:第三条记录cat5=null,排在最后
  }
}
