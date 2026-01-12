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
package org.apache.calcite.adapter.geode.rel;  // 声明包名，该类位于org.apache.calcite.adapter.geode.rel包下，是Geode适配器关系型操作相关的测试类

import org.apache.calcite.jdbc.CalciteConnection;  // 导入Calcite连接类，用于创建和管理Calcite数据库连接
import org.apache.calcite.schema.SchemaPlus;  // 导入SchemaPlus类，表示Calcite中的模式（schema），可以包含多个表
import org.apache.calcite.test.CalciteAssert;  // 导入Calcite断言工具类，用于编写测试断言
import org.apache.calcite.test.ConnectionFactory;  // 导入连接工厂接口，用于创建数据库连接

import org.apache.geode.cache.Cache;  // 导入Geode缓存接口，Geode是一个内存数据网格，Cache是核心数据容器
import org.apache.geode.cache.Region;  // 导入Geode区域接口，Region是Geode中数据的基本组织单位，类似于数据库表

import org.junit.jupiter.api.BeforeAll;  // 导入JUnit5的BeforeAll注解，标记在所有测试方法执行前只执行一次的静态方法
import org.junit.jupiter.api.Test;  // 导入JUnit5的Test注解，标记测试方法

import java.sql.Connection;  // 导入JDBC连接接口，用于数据库连接
import java.sql.DriverManager;  // 导入JDBC驱动管理器，用于获取数据库连接
import java.sql.SQLException;  // 导入SQL异常类，处理SQL操作中的异常
import java.util.Arrays;  // 导入Arrays工具类，用于数组操作

/**
 * Tests using {@code Bookshop} schema.  // 测试类，使用Bookshop（书店）模式进行测试，该类继承自AbstractGeodeTest基类
 * 
 * 该类是Apache Calcite Geode适配器的集成测试类，用于测试Calcite SQL查询引擎与Apache Geode内存数据网格的集成功能。
 * 主要测试场景包括：
 * 1. 基本SQL查询操作（SELECT、WHERE、ORDER BY、LIMIT等）
 * 2. 聚合函数（COUNT、MAX、MIN、SUM、AVG）和GROUP BY操作
 * 3. 嵌套PDX（Portable Data eXchange）对象的字段访问和过滤
 * 4. 复杂的AND/OR条件组合查询
 * 5. 查询计划验证和SQL到OQL（Object Query Language）的转换
 * 
 * 该测试类使用两个Geode Region作为数据源：
 * - BookMaster：存储书籍主数据，包含itemNumber、title、author、yearPublished、retailCost、description等字段
 * - BookCustomer：存储客户数据，包含嵌套的primaryAddress对象，演示如何访问嵌套字段
 * 
 * 测试数据通过JsonLoader从JSON文件加载到Geode Region中，确保测试环境的可重复性和独立性。
 */
class GeodeBookstoreTest extends AbstractGeodeTest {  // 定义测试类，继承自AbstractGeodeTest基类，复用Geode测试基础设施

  @BeforeAll  // JUnit5注解，标记该方法在所有测试方法执行前只执行一次
  public static void setUp() throws Exception {  // 静态初始化方法，用于设置测试环境，抛出异常以处理可能的错误
    Cache cache = POLICY.cache();  // 从基类获取Geode缓存实例，POLICY是基类中定义的缓存策略
    Region<?, ?> bookMaster =  cache.<String, Object>createRegionFactory().create("BookMaster");  // 创建名为BookMaster的Region，键类型为String，值类型为Object，用于存储书籍主数据
    new JsonLoader(bookMaster).loadClasspathResource("/book_master.json");  // 使用JsonLoader从类路径加载book_master.json文件中的数据到BookMaster Region

    Region<?, ?> bookCustomer =  cache.<String, Object>createRegionFactory().create("BookCustomer");  // 创建名为BookCustomer的Region，键类型为String，值类型为Object，用于存储客户数据
    new JsonLoader(bookCustomer).loadClasspathResource("/book_customer.json");  // 使用JsonLoader从类路径加载book_customer.json文件中的数据到BookCustomer Region

  }  // setUp方法结束

  private static Connection createConnection() throws SQLException {  // 私有静态方法，创建并返回Calcite数据库连接，抛出SQLException处理连接错误
    final Connection connection =  // 声明最终连接变量
        DriverManager.getConnection("jdbc:calcite:lex=JAVA");  // 使用DriverManager获取Calcite JDBC连接，lex=JAVA表示使用Java词法分析器
    final SchemaPlus root =  // 声明最终根Schema变量
        connection.unwrap(CalciteConnection.class).getRootSchema();  // 将连接解包为CalciteConnection并获取根Schema，rootSchema是所有子Schema的容器
    root.add("geode",  // 向根Schema添加名为"geode"的子Schema，这个名称将在SQL查询中使用
        new GeodeSchema(POLICY.cache(),  // 创建GeodeSchema实例，传入Geode缓存和要包含的Region列表
            Arrays.asList("BookMaster", "BookCustomer")));  // 将BookMaster和BookCustomer两个Region添加到GeodeSchema中，使其可以通过SQL查询
    return connection;  // 返回配置好的连接对象
  }  // createConnection方法结束

  private ConnectionFactory newConnectionFactory() {  // 私有方法，创建并返回一个ConnectionFactory实例，用于生成数据库连接
    return GeodeBookstoreTest::createConnection;  // 返回方法引用，指向本类的createConnection静态方法，实现ConnectionFactory接口
  }  // newConnectionFactory方法结束

  private CalciteAssert.AssertThat calciteAssert() {  // 私有方法，创建并返回CalciteAssert.AssertThat构建器，用于编写测试断言
    return CalciteAssert.that()  // 调用CalciteAssert的静态工厂方法that()创建AssertThat构建器
        .with(newConnectionFactory());  // 使用之前创建的ConnectionFactory配置构建器，使其能够获取数据库连接
  }  // calciteAssert方法结束

  @Test void testSelect() {  // 测试方法，测试基本的SELECT查询功能
    calciteAssert()  // 获取Calcite断言构建器
        .query("select * from geode.BookMaster")  // 执行SQL查询，从geode.BookMaster表中选择所有列
        .returnsCount(3);  // 验证查询返回的行数为3行
  }  // testSelect方法结束

  @Test void testWhereEqual() {  // 测试方法，测试带等值条件的WHERE子句
    String expectedQuery = "SELECT * FROM /BookMaster WHERE itemNumber = 123";  // 定义期望的Geode OQL查询语句，用于验证SQL到OQL的转换

    calciteAssert()  // 获取Calcite断言构建器
        .query("select * from geode.BookMaster WHERE itemNumber = 123")  // 执行SQL查询，查找itemNumber等于123的书籍
        .returnsCount(1)  // 验证查询返回1行结果
        .returns("itemNumber=123; description=Run on sentences and drivel on all things mundane;"  // 验证返回的第一行数据内容
            + " retailCost=34.99; yearPublished=2011; author=Daisy Mae West; title=A Treatise of "
            + "Treatises\n")  // 验证返回数据的详细信息，包括所有字段值
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划包含预期的操作符，GeodeToEnumerableConverter将Geode结果转换为可枚举格式
            + "  GeodeFilter(condition=[=(CAST($0):INTEGER, 123)])\n"  // 验证包含GeodeFilter过滤器，条件是将第0列转换为整数并与123比较
            + "    GeodeTableScan(table=[[geode, BookMaster]])")  // 验证包含GeodeTableScan表扫描操作，扫描geode.BookMaster表
        .queryContains(GeodeAssertions.query(expectedQuery));  // 验证生成的OQL查询包含期望的查询语句
  }  // testWhereEqual方法结束

  @Test void testWhereWithAnd() {  // 测试方法，测试带AND条件的WHERE子句
    calciteAssert()  // 获取Calcite断言构建器
        .query("select * from geode.BookMaster WHERE itemNumber > 122 "  // 执行SQL查询，查找itemNumber大于122且小于等于123的书籍
            + "AND itemNumber <= 123")  // 使用AND连接两个条件，形成范围查询
        .returnsCount(1)  // 验证查询返回1行结果
        .returns("itemNumber=123; description=Run on sentences and drivel on all things mundane; "  // 验证返回的数据内容
            + "retailCost=34.99; yearPublished=2011; author=Daisy Mae West; title=A Treatise of "
            + "Treatises\n")  // 验证返回的书籍详细信息
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划结构
            + "  GeodeFilter(condition=[SEARCH($0, Sarg[(122..123]])])\n"  // 验证使用SEARCH操作符进行范围搜索，Sarg表示搜索参数，(122..123]表示大于122且小于等于123
            + "    GeodeTableScan(table=[[geode, BookMaster]])")  // 验证表扫描操作
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT * FROM /BookMaster "  // 期望的OQL查询语句
                + "WHERE itemNumber > 122 AND itemNumber <= 123"));  // OQL中的AND条件
  }  // testWhereWithAnd方法结束

  @Test void testWhereWithOr() {  // 测试方法，测试带OR条件的WHERE子句
    String expectedQuery = "SELECT author AS author FROM /BookMaster "  // 定义期望的OQL查询语句，使用IN SET优化OR条件
        + "WHERE itemNumber IN SET(123, 789)";  // OQL使用IN SET来表示多个值的OR条件

    calciteAssert()  // 获取Calcite断言构建器
        .query("select author from geode.BookMaster "  // 执行SQL查询，选择author字段
            + "WHERE itemNumber = 123 OR itemNumber = 789")  // 使用OR连接两个等值条件
        .returnsCount(2)  // 验证查询返回2行结果
        .returnsUnordered("author=Jim Heavisides", "author=Daisy Mae West")  // 验证返回的author值，不关心顺序
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(author=[$4])\n"  // 验证包含GeodeProject投影操作，选择第4列（author字段）
            + "    GeodeFilter(condition=[SEARCH(CAST($0):INTEGER, Sarg[123, 789])])\n"  // 验证过滤器使用SEARCH和Sarg进行多值匹配
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n")  // 验证表扫描
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query(expectedQuery));  // 验证包含期望的IN SET查询
  }  // testWhereWithOr方法结束

  @Test void testWhereWithAndOr() {  // 测试方法，测试AND和OR组合的复杂WHERE条件
    calciteAssert()  // 获取Calcite断言构建器
        .query("SELECT author from geode.BookMaster "  // 执行SQL查询，选择author字段
            + "WHERE (itemNumber > 123 AND itemNumber = 789) "  // 第一个条件：itemNumber大于123且等于789（这个条件永远为false）
            + "OR author='Daisy Mae West'")  // 第二个条件：author等于'Daisy Mae West'，使用OR连接
        .returnsCount(2)  // 验证查询返回2行结果
        .returnsUnordered("author=Jim Heavisides", "author=Daisy Mae West")  // 验证返回的author值
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(author=[$4])\n"  // 验证投影操作
            + "    GeodeFilter(condition=[OR(AND(>($0, 123), =(CAST($0):INTEGER, 789)), "  // 验证复杂的过滤条件，OR包含AND
            + "=(CAST($4):VARCHAR, 'Daisy Mae West'))])\n"  // 验证第二个条件是对第4列（author）进行字符串比较
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n"  // 验证表扫描
            + "\n")  // 验证计划末尾
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT author AS author FROM /BookMaster "  // 期望的OQL查询
                + "WHERE (itemNumber > 123 AND itemNumber = 789) OR author = 'Daisy Mae West'"));  // OQL中的复杂条件
  }  // testWhereWithAndOr方法结束

  // TODO: Not supported YET  // 注释说明该功能尚未支持
  @Test void testWhereWithOrAnd() {  // 测试方法，测试OR和AND的另一种组合（这种组合目前尚未完全支持）
    calciteAssert()  // 获取Calcite断言构建器
        .query("SELECT author from geode.BookMaster "  // 执行SQL查询
            + "WHERE (itemNumber > 100 OR itemNumber = 789) "  // 第一个条件：itemNumber大于100或等于789
            + "AND author='Daisy Mae West'")  // 第二个条件：author等于'Daisy Mae West'，使用AND连接整个OR表达式
        .returnsCount(1)  // 验证查询返回1行结果
        .returnsUnordered("author=Daisy Mae West")  // 验证返回的author值
        .explainContains("");  // 验证查询计划为空，因为这种组合尚未完全支持
  }  // testWhereWithOrAnd方法结束

  @Test void testProjectionsAndWhereGreatThan() {  // 测试方法，测试投影（SELECT指定列）和大于条件的WHERE子句
    calciteAssert()  // 获取Calcite断言构建器
        .query("select author from geode.BookMaster WHERE itemNumber > 123")  // 执行SQL查询，选择author字段，条件是itemNumber大于123
        .returnsCount(2)  // 验证查询返回2行结果
        .returns("author=Clarence Meeks\n"  // 验证返回的第一行数据
            + "author=Jim Heavisides\n")  // 验证返回的第二行数据
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(author=[$4])\n"  // 验证投影操作，选择第4列（author字段）
            + "    GeodeFilter(condition=[>($0, 123)])\n"  // 验证过滤器，条件是第0列大于123
            + "      GeodeTableScan(table=[[geode, BookMaster]])")  // 验证表扫描
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT author AS author "  // 期望的OQL查询
                + "FROM /BookMaster WHERE itemNumber > 123"));  // OQL中的大于条件
  }  // testProjectionsAndWhereGreatThan方法结束

  @Test void testLimit() {  // 测试方法，测试LIMIT子句功能
    calciteAssert()  // 获取Calcite断言构建器
        .query("select * from geode.BookMaster LIMIT 1")  // 执行SQL查询，限制只返回1行结果
        .returnsCount(1)  // 验证查询返回1行结果
        .returns("itemNumber=123; description=Run on sentences and drivel on all things mundane; "  // 验证返回的数据内容
            + "retailCost=34.99; yearPublished=2011; author=Daisy Mae West; title=A Treatise of "
            + "Treatises\n")  // 验证返回的书籍详细信息
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeSort(fetch=[1])\n"  // 验证包含GeodeSort操作，fetch=[1]表示只获取第一条记录
            + "    GeodeTableScan(table=[[geode, BookMaster]])");  // 验证表扫描
  }  // testLimit方法结束

  @Test void testSortWithProjection() {  // 测试方法，测试ORDER BY排序和投影
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished from geode.BookMaster ORDER BY yearPublished ASC")  // 执行SQL查询，选择yearPublished字段并按升序排序
        .returnsCount(3)  // 验证查询返回3行结果
        .returns("yearPublished=1971\n"  // 验证第一行，最早的年份
            + "yearPublished=2011\n"  // 验证第二行
            + "yearPublished=2011\n")  // 验证第三行，相同的年份
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeSort(sort0=[$0], dir0=[ASC])\n"  // 验证包含排序操作，sort0=[$0]表示按第0列排序，dir0=[ASC]表示升序
            + "    GeodeProject(yearPublished=[$3])\n"  // 验证投影操作，选择第3列（yearPublished字段）
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testSortWithProjection方法结束

  @Test void testSortWithProjectionAndLimit() {  // 测试方法，测试ORDER BY排序、投影和LIMIT的组合
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished from geode.BookMaster ORDER BY yearPublished "  // 执行SQL查询，选择yearPublished字段并排序
            + "LIMIT 2")  // 限制只返回2行结果
        .returnsCount(2)  // 验证查询返回2行结果
        .returns("yearPublished=1971\n"  // 验证第一行，最早的年份
            + "yearPublished=2011\n")  // 验证第二行
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(yearPublished=[$3])\n"  // 验证投影操作
            + "    GeodeSort(sort0=[$3], dir0=[ASC], fetch=[2])\n"  // 验证排序操作，按第3列升序排序，fetch=[2]表示只获取2条
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testSortWithProjectionAndLimit方法结束

  @Test void testSortBy2Columns() {  // 测试方法，测试按多列排序
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished, itemNumber from geode.BookMaster ORDER BY "  // 执行SQL查询，选择两个字段
            + "yearPublished ASC, itemNumber DESC")  // 按yearPublished升序排序，yearPublished相同时按itemNumber降序排序
        .returnsCount(3)  // 验证查询返回3行结果
        .returns("yearPublished=1971; itemNumber=456\n"  // 验证第一行，1971年的456号
            + "yearPublished=2011; itemNumber=789\n"  // 验证第二行，2011年的789号（降序）
            + "yearPublished=2011; itemNumber=123\n")  // 验证第三行，2011年的123号
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT yearPublished AS yearPublished, "  // 期望的OQL查询
              + "itemNumber AS itemNumber "  // 选择两个字段
              + "FROM /BookMaster ORDER BY yearPublished ASC, itemNumber DESC"));  // 按两个字段排序
  }  // testSortBy2Columns方法结束

  //
  // geode Group By and Aggregation Function Support  // 注释分隔符，下面是Geode GROUP BY和聚合函数支持的测试
  //

  /**
   * OQL Error: Query contains group by columns not present in projected fields  // 注释说明OQL错误：GROUP BY列不在投影字段中
   * Solution: Automatically expand the projections to include all missing GROUP By columns.  // 解决方案：自动扩展投影以包含所有缺失的GROUP BY列
   */
  @Test void testAddMissingGroupByColumnToProjectedFields() {  // 测试方法，测试自动添加缺失的GROUP BY列到投影字段
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished from geode.BookMaster GROUP BY  yearPublished, "  // 执行SQL查询，SELECT只包含yearPublished，但GROUP BY包含yearPublished和author
            + "author")  // GROUP BY包含author字段，但SELECT中没有，需要自动添加
        .returnsCount(3)  // 验证查询返回3行结果（因为有3个不同的yearPublished+author组合）
        .returns("yearPublished=1971\n"  // 验证返回的yearPublished值
            + "yearPublished=2011\n"  // 验证返回的yearPublished值
            + "yearPublished=2011\n")  // 验证返回的yearPublished值
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(yearPublished=[$0])\n"  // 验证投影操作，只输出yearPublished（第0列）
            + "    GeodeAggregate(group=[{3, 4}])\n"  // 验证聚合操作，group=[{3, 4}]表示按第3列（yearPublished）和第4列（author）分组
            + "      GeodeTableScan(table=[[geode, BookMaster]])");  // 验证表扫描
  }  // testAddMissingGroupByColumnToProjectedFields方法结束

  /**
   * When the group by columns match the projected fields, the optimizers removes the projected  // 注释说明：当GROUP BY列与投影字段匹配时，优化器会移除投影关系
   * relation.  // 注释说明：移除投影关系以优化查询计划
   */
  @Test void testMissingProjectRelationOnGroupByColumnMatchingProjectedFields() {  // 测试方法，测试GROUP BY列与投影字段匹配时优化器移除投影关系
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished from geode.BookMaster GROUP BY yearPublished")  // 执行SQL查询，SELECT和GROUP BY都包含yearPublished
        .returnsCount(2)  // 验证查询返回2行结果（2个不同的yearPublished值）
        .returns("yearPublished=1971\n"  // 验证第一个yearPublished值
            + "yearPublished=2011\n")  // 验证第二个yearPublished值
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeAggregate(group=[{3}])\n"  // 验证只有聚合操作，没有额外的投影操作，因为GROUP BY列与SELECT列相同
            + "    GeodeTableScan(table=[[geode, BookMaster]])");  // 验证表扫描
  }  // testMissingProjectRelationOnGroupByColumnMatchingProjectedFields方法结束

  /**
   * When the group by columns match the projected fields, the optimizers removes the projected  // 注释说明：当GROUP BY列与投影字段匹配时，优化器会移除投影关系
   * relation.  // 注释说明：移除投影关系以优化查询计划
   */
  @Test void testMissingProjectRelationOnGroupByColumnMatchingProjectedFields2() {  // 测试方法，测试GROUP BY列与投影字段匹配时优化器移除投影关系（带聚合函数）
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished, MAX(retailCost) from geode.BookMaster GROUP BY "  // 执行SQL查询，SELECT包含yearPublished和聚合函数MAX(retailCost)
            + "yearPublished")  // GROUP BY包含yearPublished，与SELECT中的yearPublished匹配
        .returnsCount(2)  // 验证查询返回2行结果
        .returns("yearPublished=1971; EXPR=11.99\n"  // 验证第一行，1971年的最大零售价
            + "yearPublished=2011; EXPR=59.99\n")  // 验证第二行，2011年的最大零售价
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeAggregate(group=[{3}], EXPR=[MAX($2)])\n"  // 验证聚合操作，group=[{3}]表示按第3列（yearPublished）分组，EXPR=[MAX($2)]表示计算第2列（retailCost）的最大值
            + "    GeodeTableScan(table=[[geode, BookMaster]])");  // 验证表扫描
  }  // testMissingProjectRelationOnGroupByColumnMatchingProjectedFields2方法结束

  @Test void testCount() {  // 测试方法，测试COUNT聚合函数
    calciteAssert()  // 获取Calcite断言构建器
        .query("select COUNT(retailCost) from geode.BookMaster")  // 执行SQL查询，计算retailCost字段非NULL值的数量
        .returnsCount(1)  // 验证查询返回1行结果
        .returns("EXPR$0=3\n")  // 验证返回的计数值为3
        .returnsValue("3")  // 验证返回的值为3
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeAggregate(group=[{}], EXPR$0=[COUNT($2)])\n"  // 验证聚合操作，group=[{}]表示没有分组（全表聚合），EXPR$0=[COUNT($2)]表示计算第2列（retailCost）的计数
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testCount方法结束

  @Test void testCountStar() {  // 测试方法，测试COUNT(*)聚合函数
    calciteAssert()  // 获取Calcite断言构建器
        .query("select COUNT(*) from geode.BookMaster")  // 执行SQL查询，计算表中的总行数
        .returnsCount(1)  // 验证查询返回1行结果
        .returns("EXPR$0=3\n")  // 验证返回的计数值为3
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeAggregate(group=[{}], EXPR$0=[COUNT()])\n"  // 验证聚合操作，group=[{}]表示没有分组，EXPR$0=[COUNT()]表示计算行数
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testCountStar方法结束

  @Test void testCountInGroupBy() {  // 测试方法，测试COUNT函数在GROUP BY中的使用
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished, COUNT(retailCost) from geode.BookMaster GROUP BY "  // 执行SQL查询，按yearPublished分组并计算每组的retailCost数量
            + "yearPublished")  // GROUP BY使用yearPublished字段
        .returnsCount(2)  // 验证查询返回2行结果（2个不同的yearPublished值）
        .returns("yearPublished=1971; EXPR=1\n"  // 验证第一行，1971年有1条记录
            + "yearPublished=2011; EXPR=2\n")  // 验证第二行，2011年有2条记录
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeAggregate(group=[{3}], EXPR=[COUNT($2)])\n"  // 验证聚合操作，group=[{3}]表示按第3列（yearPublished）分组，EXPR=[COUNT($2)]表示计算第2列（retailCost）的计数
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testCountInGroupBy方法结束

  @Test void testMaxMinSumAvg() {  // 测试方法，测试MAX、MIN、SUM、AVG聚合函数
    calciteAssert()  // 获取Calcite断言构建器
        .query("select MAX(retailCost), MIN(retailCost), SUM(retailCost), AVG"  // 执行SQL查询，计算retailCost的最大值、最小值、总和和平均值
            + "(retailCost) from geode.BookMaster")  // 对整个表进行聚合计算
        .returnsCount(1)  // 验证查询返回1行结果
        .returns("EXPR$0=59.99; EXPR=11.99; EXPR$2=106.97000122070312; "  // 验证返回的聚合结果，EXPR$0是MAX，EXPR是MIN，EXPR$2是SUM
            + "EXPR$3=35.65666580200195\n")  // 验证EXPR$3是AVG（平均值）
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeAggregate(group=[{}], EXPR$0=[MAX($2)], EXPR=[MIN($2)], EXPR$2=[SUM($2)"  // 验证聚合操作，包含多个聚合函数，都作用于第2列（retailCost）
            + "], EXPR$3=[AVG($2)])\n"  // 验证AVG函数
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testMaxMinSumAvg方法结束

  @Test void testMaxMinSumAvgInGroupBy() {  // 测试方法，测试MAX、MIN、SUM、AVG聚合函数在GROUP BY中的使用
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished, MAX(retailCost), MIN(retailCost), SUM"  // 执行SQL查询，按yearPublished分组并计算每组的多种聚合值
            + "(retailCost), AVG(retailCost) from geode.BookMaster "  // 包含MAX、MIN、SUM、AVG四个聚合函数
            + "GROUP BY  yearPublished")  // 按yearPublished分组
        .returnsCount(2)  // 验证查询返回2行结果
        .returns("yearPublished=2011; EXPR=59.99; EXPR$2=34.99; EXPR$3=94.9800033569336; "  // 验证第一行，2011年的聚合结果
            + "EXPR$4=47.4900016784668\n"  // 验证2011年的平均值
            + "yearPublished=1971; EXPR=11.99; EXPR$2=11.99; EXPR$3=11.989999771118164; "  // 验证第二行，1971年的聚合结果
            + "EXPR$4=11.989999771118164\n")  // 验证1971年的平均值
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeAggregate(group=[{3}], EXPR=[MAX($2)], EXPR$2=[MIN($2)], EXPR$3=[SUM($2)"  // 验证聚合操作，按第3列（yearPublished）分组，计算第2列（retailCost）的各种聚合值
            + "], EXPR$4=[AVG($2)])\n"  // 验证AVG函数
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testMaxMinSumAvgInGroupBy方法结束

  @Test void testGroupBy() {  // 测试方法，测试GROUP BY功能
    calciteAssert()  // 获取Calcite断言构建器
        .query("select yearPublished, MAX(retailCost) AS MAXCOST, author from "  // 执行SQL查询，选择yearPublished、MAX(retailCost)别名为MAXCOST、author
            + "geode.BookMaster GROUP BY yearPublished, author")  // 按yearPublished和author两个字段分组
        .returnsCount(3)  // 验证查询返回3行结果（3个不同的yearPublished+author组合）
        .returnsUnordered("yearPublished=2011; MAXCOST=59.99; author=Jim Heavisides",  // 验证第一行数据，使用无序比较
            "yearPublished=1971; MAXCOST=11.99; author=Clarence Meeks",  // 验证第二行数据
            "yearPublished=2011; MAXCOST=34.99; author=Daisy Mae West")  // 验证第三行数据
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(yearPublished=[$0], MAXCOST=[$2], author=[])\n"  // 验证投影操作，输出yearPublished（第0列）、MAXCOST（第2列）、author（第1列）
            + "    GeodeAggregate(group=[{3, 4}], MAXCOST=[MAX($2)])\n"  // 验证聚合操作，group=[{3, 4}]表示按第3列（yearPublished）和第4列（author）分组，MAXCOST=[MAX($2)]表示计算第2列（retailCost）的最大值
            + "      GeodeTableScan(table=[[geode, BookMaster]])\n");  // 验证表扫描
  }  // testGroupBy方法结束

  @Test void testSelectWithNestedPdx() {  // 测试方法，测试选择包含嵌套PDX对象的记录
    calciteAssert()  // 获取Calcite断言构建器
        .query("select * from geode.BookCustomer limit 2")  // 执行SQL查询，从BookCustomer表选择所有字段，限制返回2行
        .returnsCount(2)  // 验证查询返回2行结果
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeSort(fetch=[2])\n"  // 验证排序操作，fetch=[2]表示只获取2条记录
            + "      GeodeTableScan(table=[[geode, BookCustomer]])\n");  // 验证表扫描，扫描BookCustomer表
  }  // testSelectWithNestedPdx方法结束

  @Test void testSelectWithNestedPdx2() {  // 测试方法，测试选择嵌套PDX对象字段
    calciteAssert()  // 获取Calcite断言构建器
        .query("select primaryAddress from geode.BookCustomer limit 2")  // 执行SQL查询，选择primaryAddress字段（这是一个嵌套的PDX对象）
        .returnsCount(2)  // 验证查询返回2行结果
        .returns("primaryAddress=PDX[addressLine1,addressLine2,addressLine3,city,state,"  // 验证第一行，primaryAddress是一个PDX对象，包含多个字段
            + "postalCode,country,phoneNumber,addressTag]\n"  // 验证PDX对象包含的字段列表
            + "primaryAddress=PDX[addressLine1,addressLine2,addressLine3,city,state,postalCode,"  // 验证第二行
            + "country,phoneNumber,addressTag]\n")  // 验证PDX对象包含的字段列表
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(primaryAddress=[$3])\n"  // 验证投影操作，选择第3列（primaryAddress字段）
            + "    GeodeSort(fetch=[2])\n"  // 验证排序操作，只获取2条记录
            + "      GeodeTableScan(table=[[geode, BookCustomer]])\n");  // 验证表扫描
  }  // testSelectWithNestedPdx2方法结束

  @Test void testSelectWithNestedPdxFieldAccess() {  // 测试方法，测试访问嵌套PDX对象的字段
    calciteAssert()  // 获取Calcite断言构建器
        .query("select primaryAddress['city'] as city from geode.BookCustomer limit 2")  // 执行SQL查询，使用数组语法访问primaryAddress对象的city字段
        .returnsCount(2)  // 验证查询返回2行结果
        .returns("city=Topeka\n"  // 验证第一行的city值
            + "city=San Francisco\n")  // 验证第二行的city值
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(city=[ITEM($3, 'city')])\n"  // 验证投影操作，使用ITEM函数访问第3列（primaryAddress）的'city'字段
            + "    GeodeSort(fetch=[2])\n"  // 验证排序操作，只获取2条记录
            + "      GeodeTableScan(table=[[geode, BookCustomer]])\n");  // 验证表扫描
  }  // testSelectWithNestedPdxFieldAccess方法结束

  @Test void testSelectWithNullFieldValue() {  // 测试方法，测试选择NULL字段值
    calciteAssert()  // 获取Calcite断言构建器
        .query("select primaryAddress['addressLine2'] from geode.BookCustomer limit"  // 执行SQL查询，访问primaryAddress的addressLine2字段
            + " 2")  // 限制返回2行结果
        .returnsCount(2)  // 验证查询返回2行结果
        .returns("EXPR$0=null\n"  // 验证第一行，addressLine2字段的值为null
            + "EXPR$0=null\n")  // 验证第二行，addressLine2字段的值为null
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(EXPR$0=[ITEM($3, 'addressLine2')])\n"  // 验证投影操作，使用ITEM函数访问第3列（primaryAddress）的'addressLine2'字段
            + "    GeodeSort(fetch=[2])\n"  // 验证排序操作，只获取2条记录
            + "      GeodeTableScan(table=[[geode, BookCustomer]])\n");  // 验证表扫描
  }  // testSelectWithNullFieldValue方法结束

  @Test void testFilterWithNestedField() {  // 测试方法，测试使用嵌套字段进行过滤
    calciteAssert()  // 获取Calcite断言构建器
        .query("SELECT primaryAddress['postalCode'] AS postalCode\n"  // 执行SQL查询，选择primaryAddress的postalCode字段
            + "FROM geode.BookCustomer\n"  // 从BookCustomer表查询
            + "WHERE primaryAddress['postalCode'] > '0'\n")  // WHERE条件是postalCode大于'0'
        .returnsCount(3)  // 验证查询返回3行结果
        .returns("postalCode=50505\n"  // 验证第一行的postalCode值
            + "postalCode=50505\n"  // 验证第二行的postalCode值
            + "postalCode=50505\n")  // 验证第三行的postalCode值
        .explainContains("PLAN=GeodeToEnumerableConverter\n"  // 验证查询计划
            + "  GeodeProject(postalCode=[ITEM($3, 'postalCode')])\n"  // 验证投影操作，使用ITEM函数访问postalCode字段
            + "    GeodeFilter(condition=[>(ITEM($3, 'postalCode'), '0')])\n"  // 验证过滤条件，使用ITEM函数访问postalCode并与'0'比较
            + "      GeodeTableScan(table=[[geode, BookCustomer]])\n")  // 验证表扫描
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT primaryAddress.postalCode AS postalCode "  // 期望的OQL查询，使用点号访问嵌套字段
                + "FROM /BookCustomer WHERE primaryAddress.postalCode > '0'"));  // OQL中的过滤条件
  }  // testFilterWithNestedField方法结束

  @Test void testSqlSimple() {  // 测试方法，测试简单的SQL查询
    calciteAssert()  // 获取Calcite断言构建器
        .query("SELECT itemNumber FROM geode.BookMaster WHERE itemNumber > 123")  // 执行SQL查询，选择itemNumber字段，条件是itemNumber大于123
        .runs()  // 验证查询能够成功执行
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT itemNumber AS itemNumber "  // 期望的OQL查询
                + "FROM /BookMaster WHERE itemNumber > 123"));  // OQL中的条件
  }  // testSqlSimple方法结束

  @Test void testSqlSingleNumberWhereFilter() {  // 测试方法，测试单个数字的WHERE过滤条件
    calciteAssert().query("SELECT * FROM geode.BookMaster "  // 执行SQL查询，选择所有字段
        + "WHERE itemNumber = 123")  // WHERE条件是itemNumber等于123
        .runs()  // 验证查询能够成功执行
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT * FROM /BookMaster "  // 期望的OQL查询
                + "WHERE itemNumber = 123"));  // OQL中的等值条件
  }  // testSqlSingleNumberWhereFilter方法结束

  @Test void testSqlDistinctSort() {  // 测试方法，测试DISTINCT和ORDER BY的组合
    calciteAssert().query("SELECT DISTINCT itemNumber, author "  // 执行SQL查询，选择不重复的itemNumber和author组合
        + "FROM geode.BookMaster ORDER BY itemNumber, author").runs();  // 按itemNumber和author排序，验证查询能够成功执行
  }  // testSqlDistinctSort方法结束

  @Test void testSqlDistinctSort2() {  // 测试方法，测试使用GROUP BY实现DISTINCT功能
    calciteAssert().query("SELECT itemNumber, author "  // 执行SQL查询，选择itemNumber和author
        + "FROM geode.BookMaster GROUP BY itemNumber, author ORDER BY itemNumber, "  // 使用GROUP BY实现去重，然后排序
        + "author").runs();  // 验证查询能够成功执行
  }  // testSqlDistinctSort2方法结束

  @Test void testSqlDistinctSort3() {  // 测试方法，测试DISTINCT所有字段
    calciteAssert().query("SELECT DISTINCT * FROM geode.BookMaster").runs();  // 执行SQL查询，选择所有不重复的记录，验证查询能够成功执行
  }  // testSqlDistinctSort3方法结束


  @Test void testSqlLimit2() {  // 测试方法，测试DISTINCT和LIMIT的组合
    calciteAssert().query("SELECT DISTINCT * FROM geode.BookMaster LIMIT 2").runs();  // 执行SQL查询，选择所有不重复的记录，限制返回2行，验证查询能够成功执行
  }  // testSqlLimit2方法结束


  @Test void testSqlDisjunction() {  // 测试方法，测试OR条件（析取）
    String expectedQuery = "SELECT author AS author FROM /BookMaster "  // 定义期望的OQL查询语句
        + "WHERE itemNumber IN SET(123, 789)";  // OQL使用IN SET来表示OR条件

    calciteAssert().query("SELECT author FROM geode.BookMaster "  // 执行SQL查询，选择author字段
        + "WHERE itemNumber = 789 OR itemNumber = 123").runs()  // WHERE条件使用OR连接两个等值条件
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query(expectedQuery));  // 验证包含期望的IN SET查询
  }  // testSqlDisjunction方法结束

  @Test void testSqlConjunction() {  // 测试方法，测试AND条件（合取）
    calciteAssert().query("SELECT author FROM geode.BookMaster "  // 执行SQL查询，选择author字段
        + "WHERE itemNumber = 789 AND author = 'Jim Heavisides'")  // WHERE条件使用AND连接两个条件
        .runs()  // 验证查询能够成功执行
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT author AS author FROM /BookMaster "  // 期望的OQL查询
                + "WHERE itemNumber = 789 AND author = 'Jim Heavisides'"));  // OQL中的AND条件
  }  // testSqlConjunction方法结束

  @Test void testSqlBookMasterWhere() {  // 测试方法，测试BookMaster表的WHERE条件
    calciteAssert().query("select author, title from geode.BookMaster "  // 执行SQL查询，选择author和title字段
        + "WHERE author = 'Jim Heavisides' LIMIT 2")  // WHERE条件是author等于'Jim Heavisides'，限制返回2行
        .runs()  // 验证查询能够成功执行
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query("SELECT author AS author, title AS title FROM /BookMaster "  // 期望的OQL查询
                + "WHERE author = 'Jim Heavisides' LIMIT 2"));  // OQL中的条件和LIMIT
  }  // testSqlBookMasterWhere方法结束

  @Test void testSqlBookMasterCount() {  // 测试方法，测试COUNT查询
    calciteAssert().query("select count(*) from geode.BookMaster").runs();  // 执行SQL查询，计算表中的总行数，验证查询能够成功执行
  }  // testSqlBookMasterCount方法结束

  @Test void testInSetFilterWithNestedStringField() {  // 测试方法，测试使用IN SET过滤嵌套字符串字段
    String expectedQuery = "SELECT primaryAddress.city AS city FROM /BookCustomer "  // 定义期望的OQL查询语句
        + "WHERE primaryAddress.city IN SET('Topeka', 'San Francisco')";  // OQL使用IN SET来过滤嵌套字段

    calciteAssert()  // 获取Calcite断言构建器
        .query("SELECT primaryAddress['city'] AS city\n"  // 执行SQL查询，选择primaryAddress的city字段
            + "FROM geode.BookCustomer\n"  // 从BookCustomer表查询
            + "WHERE primaryAddress['city'] = 'Topeka' OR primaryAddress['city'] = 'San Francisco'\n")  // WHERE条件使用OR连接两个等值条件
        .returnsCount(3)  // 验证查询返回3行结果
        .queryContains(  // 验证生成的OQL查询
            GeodeAssertions.query(expectedQuery));  // 验证包含期望的IN SET查询
  }  // testInSetFilterWithNestedStringField方法结束
}  // GeodeBookstoreTest类结束
