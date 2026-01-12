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
// Apache许可证声明
package org.apache.calcite.adapter.file; // 包声明：FileReaderTest类位于org.apache.calcite.adapter.file包下

import org.apache.calcite.util.Source; // 导入Source类，用于表示数据源（文件、URL等）
import org.apache.calcite.util.Sources; // 导入Sources工具类，用于创建Source对象
import org.apache.calcite.util.TestUtil; // 导入TestUtil工具类，提供测试辅助方法

import org.jsoup.select.Elements; // 导入Jsoup的Elements类，用于表示HTML元素集合
import org.junit.jupiter.api.Disabled; // 导入Disabled注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入Test注解，标记测试方法
import org.junit.jupiter.api.extension.ExtendWith; // 导入ExtendWith注解，用于扩展JUnit功能

import java.net.MalformedURLException; // 导入MalformedURLException异常类
import java.net.URL; // 导入URL类，用于表示统一资源定位符
import java.sql.Connection; // 导入Connection接口，表示数据库连接
import java.sql.DriverManager; // 导入DriverManager类，用于管理数据库驱动
import java.sql.ResultSet; // 导入ResultSet接口，表示查询结果集
import java.sql.Statement; // 导入Statement接口，用于执行SQL语句
import java.util.Iterator; // 导入Iterator接口，用于遍历集合
import java.util.Properties; // 导入Properties类，用于配置属性

import static org.apache.calcite.util.TestUtil.getJavaMajorVersion; // 静态导入获取Java主版本号的方法

import static org.hamcrest.CoreMatchers.instanceOf; // 静态导入instanceOf匹配器
import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat断言方法
import static org.junit.jupiter.api.Assertions.assertNotNull; // 静态导入assertNotNull断言方法
import static org.junit.jupiter.api.Assertions.assertThrows; // 静态导入assertThrows断言方法
import static org.junit.jupiter.api.Assertions.fail; // 静态导入fail方法
import static org.junit.jupiter.api.Assumptions.assumeTrue; // 静态导入assumeTrue假设方法

import static java.lang.System.getProperty; // 静态导入获取系统属性的方法
import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象非空

/**
 * Unit tests for FileReader.
 * // FileReader类的单元测试类
 * // 该类用于测试FileReader（文件读取器）的各种功能，包括：
 * // 1. 从URL读取HTML表格数据
 * // 2. 从本地文件读取CSV和JSON数据
 * // 3. 测试各种异常情况的处理
 * // 4. 测试通过JDBC接口查询文件数据
 * // FileReader是Calcite文件适配器的核心组件，用于将文件数据转换为可查询的关系表
 */
@ExtendWith(RequiresNetworkExtension.class) // 使用RequiresNetworkExtension扩展，标记需要网络的测试
class FileReaderTest { // FileReaderTest类：FileReader的单元测试类

  private static final Source CITIES_SOURCE = // 静态常量：美国城市列表的维基百科页面数据源
      Sources.url("http://en.wikipedia.org/wiki/List_of_United_States_cities_by_population"); // URL指向美国城市按人口排序的维基百科页面

  private static final Source STATES_SOURCE = // 静态常量：美国州列表的维基百科页面数据源
      Sources.url(
          "http://en.wikipedia.org/wiki/List_of_states_and_territories_of_the_United_States"); // URL指向美国州和地区的维基百科页面

  private static Source resource(String path) { // 静态方法：从类路径资源创建Source对象
    final URL url = // 定义URL变量
        requireNonNull(FileReaderTest.class.getResource("/" + path), "url"); // 获取类路径下的资源URL，如果为null则抛出NullPointerException
    return Sources.of(url); // 使用Sources工具类将URL转换为Source对象并返回
  }

  private static String resourcePath(String path) { // 静态方法：获取类路径资源的绝对路径
    return resource(path).file().getAbsolutePath(); // 调用resource方法获取Source对象，然后获取其文件路径的绝对路径并返回
  }

  /** Tests {@link FileReader} URL instantiation - no path. */
  // 测试FileReader使用URL实例化（不指定路径）
  @Disabled("[CALCITE-3800] FileReaderTest#testFileReaderUrlNoPath() timeout for AppVeyor test") // 禁用该测试，因为AppVeyor测试环境超时
  @Test @RequiresNetwork public void testFileReaderUrlNoPath() throws FileReaderException { // 测试方法：测试FileReader从URL读取数据（不指定CSS选择器路径）
    // Under OpenJDK, test fails with the following, so skip test:
    //   javax.net.ssl.SSLHandshakeException:
    //   sun.security.validator.ValidatorException: PKIX path building failed:
    //   sun.security.provider.certpath.SunCertPathBuilderException:
    //   unable to find valid certification path to requested target
    // 在OpenJDK环境下，测试会因SSL握手异常失败，需要跳过测试
    final String r = getProperty("java.runtime.name"); // 获取Java运行时名称
    // http://openjdk.java.net/jeps/319 -> root certificates are bundled with JEP 10
    // Java 10+（JEP 319）已经内置了根证书
    assumeTrue(!r.equals("OpenJDK Runtime Environment") // 假设条件：如果不是OpenJDK运行时环境
            || getJavaMajorVersion() > 10, // 或者Java主版本大于10
        "Java 10+ should have root certificates (JEP 319). Runtime is " // 提示信息
            + r + ", Java major version is " + getJavaMajorVersion()); // 显示运行时名称和Java版本

    FileReader t = new FileReader(STATES_SOURCE); // 创建FileReader对象，使用STATES_SOURCE（美国州列表URL）作为数据源
    t.refresh(); // 调用refresh方法刷新数据，从URL获取并解析HTML内容
  }

  /** Tests {@link FileReader} URL instantiation - with path. */
  // 测试FileReader使用URL实例化（指定CSS选择器路径）
  @Disabled("[CALCITE-1789] Wikipedia format change breaks file adapter test") // 禁用该测试，因为维基百科格式改变导致文件适配器测试失败
  @Test @RequiresNetwork public void testFileReaderUrlWithPath() throws FileReaderException { // 测试方法：测试FileReader从URL读取数据（指定CSS选择器路径）
    FileReader t = // 创建FileReader对象
        new FileReader(CITIES_SOURCE, // 使用CITIES_SOURCE（美国城市列表URL）作为数据源
            "#mw-content-text > table.wikitable.sortable", 0); // CSS选择器：定位主内容文本区域中的可排序wikitable表格，0表示使用第一个匹配的表格
    t.refresh(); // 调用refresh方法刷新数据，从URL获取并解析HTML内容
  }

  /** Tests {@link FileReader} URL fetch. */
  // 测试FileReader从URL获取数据
  @Disabled("[CALCITE-1789] Wikipedia format change breaks file adapter test") // 禁用该测试，因为维基百科格式改变导致文件适配器测试失败
  @Test @RequiresNetwork public void testFileReaderUrlFetch() throws FileReaderException { // 测试方法：测试FileReader从URL获取并遍历数据
    FileReader t = // 创建FileReader对象
        new FileReader(STATES_SOURCE, // 使用STATES_SOURCE（美国州列表URL）作为数据源
            "#mw-content-text > table.wikitable.sortable", 0); // CSS选择器：定位主内容文本区域中的可排序wikitable表格，0表示使用第一个匹配的表格
    int i = 0; // 初始化计数器
    for (Elements row : t) { // 遍历FileReader中的每一行数据（FileReader实现了Iterable接口）
      i++; // 计数器递增
    }
    assertThat(i, is(51)); // 断言：验证行数为51（美国50个州加上哥伦比亚特区共51个）
  }

  /** Tests failed {@link FileReader} instantiation - malformed URL. */
  // 测试FileReader实例化失败的情况 - 格式错误的URL
  @Test void testFileReaderMalUrl() { // 测试方法：测试使用格式错误的URL创建FileReader
    try { // 尝试执行以下代码
      final Source badSource = Sources.url("bad" + CITIES_SOURCE.url()); // 创建一个格式错误的Source（在URL前加上"bad"前缀）
      fail("expected exception, got " + badSource); // 如果没有抛出异常，测试失败
    } catch (RuntimeException e) { // 捕获运行时异常
      assertThat(e.getCause(), instanceOf(MalformedURLException.class)); // 断言：验证异常原因是MalformedURLException（格式错误的URL异常）
      assertThat(e.getCause().getMessage(), is("unknown protocol: badhttp")); // 断言：验证异常消息为"unknown protocol: badhttp"
    }
  }

  /** Tests failed {@link FileReader} instantiation - bad URL. */
  // 测试FileReader实例化失败的情况 - 错误的URL
  @Test void testFileReaderBadUrl() { // 测试方法：测试使用错误的URL创建FileReader
    final String uri = // 定义字符串变量
        "http://ex.wikipedia.org/wiki/List_of_United_States_cities_by_population"; // 错误的URL（使用ex.wikipedia.org代替en.wikipedia.org）
    assertThrows(FileReaderException.class, () -> { // 断言：验证抛出FileReaderException异常
      FileReader t = new FileReader(Sources.url(uri), "table:eq(4)"); // 创建FileReader对象，使用错误的URL和CSS选择器
      t.refresh(); // 调用refresh方法刷新数据（应该会失败并抛出异常）
    });
  }

  /** Tests failed {@link FileReader} instantiation - bad selector. */
  // 测试FileReader实例化失败的情况 - 错误的CSS选择器
  @Test void testFileReaderBadSelector() { // 测试方法：测试使用错误的CSS选择器创建FileReader
    final Source source = resource("tableOK.html"); // 从类路径资源获取tableOK.html文件的Source对象
    assertThrows(FileReaderException.class, () -> { // 断言：验证抛出FileReaderException异常
      FileReader t = new FileReader(source, "table:eq(1)"); // 创建FileReader对象，使用错误的CSS选择器（选择第二个表格，但文件中只有一个表格）
      t.refresh(); // 调用refresh方法刷新数据（应该会失败并抛出异常）
    });
  }

  /** Test {@link FileReader} with static file - headings. */
  // 测试FileReader读取静态文件 - 获取表头
  @Test void testFileReaderHeadings() throws FileReaderException { // 测试方法：测试FileReader从静态文件获取表头
    final Source source = resource("tableOK.html"); // 从类路径资源获取tableOK.html文件的Source对象
    FileReader t = new FileReader(source); // 创建FileReader对象，使用本地HTML文件作为数据源
    Elements headings = t.getHeadings(); // 调用getHeadings方法获取表格的表头元素
    assertThat(headings.get(1).text(), is("H1")); // 断言：验证第二个表头的文本内容为"H1"
  }

  /** Test {@link FileReader} with static file - data. */
  // 测试FileReader读取静态文件 - 获取数据行
  @Test void testFileReaderData() throws FileReaderException { // 测试方法：测试FileReader从静态文件获取数据行
    final Source source = resource("tableOK.html"); // 从类路径资源获取tableOK.html文件的Source对象
    FileReader t = new FileReader(source); // 创建FileReader对象，使用本地HTML文件作为数据源
    Iterator<Elements> i = t.iterator(); // 获取FileReader的迭代器，用于遍历数据行
    Elements row = i.next(); // 获取第一行数据（第一个Elements对象）
    assertThat(row.get(2).text(), is("R0C2")); // 断言：验证第一行第三列的文本内容为"R0C2"（Row 0 Column 2）
    row = i.next(); // 获取第二行数据
    assertThat(row.get(0).text(), is("R1C0")); // 断言：验证第二行第一列的文本内容为"R1C0"（Row 1 Column 0）
  }

  /** Tests {@link FileReader} with bad static file - headings. */
  // 测试FileReader读取格式不规范的静态文件 - 获取表头
  @Test void testFileReaderHeadingsBadFile() throws FileReaderException { // 测试方法：测试FileReader从没有thead/tbody标签的HTML文件获取表头
    final Source source = resource("tableNoTheadTbody.html"); // 从类路径资源获取tableNoTheadTbody.html文件的Source对象（格式不规范的HTML文件）
    FileReader t = new FileReader(source); // 创建FileReader对象，使用格式不规范的HTML文件作为数据源
    Elements headings = t.getHeadings(); // 调用getHeadings方法获取表格的表头元素（即使没有thead标签也能正确解析）
    assertThat(headings.get(1).text(), is("H1")); // 断言：验证第二个表头的文本内容为"H1"
  }

  /** Tests {@link FileReader} with bad static file - data. */
  // 测试FileReader读取格式不规范的静态文件 - 获取数据行
  @Test void testFileReaderDataBadFile() throws FileReaderException { // 测试方法：测试FileReader从没有thead/tbody标签的HTML文件获取数据行
    final Source source = resource("tableNoTheadTbody.html"); // 从类路径资源获取tableNoTheadTbody.html文件的Source对象（格式不规范的HTML文件）
    FileReader t = new FileReader(source); // 创建FileReader对象，使用格式不规范的HTML文件作为数据源
    Iterator<Elements> i = t.iterator(); // 获取FileReader的迭代器，用于遍历数据行
    Elements row = i.next(); // 获取第一行数据
    assertThat(row.get(2).text(), is("R0C2")); // 断言：验证第一行第三列的文本内容为"R0C2"
    row = i.next(); // 获取第二行数据
    assertThat(row.get(0).text(), is("R1C0")); // 断言：验证第二行第一列的文本内容为"R1C0"
  }

  /** Tests {@link FileReader} with no headings static file - data. */
  // 测试FileReader读取没有表头的静态文件 - 获取数据行
  @Test void testFileReaderDataNoTh() throws FileReaderException { // 测试方法：测试FileReader从没有th标签的HTML文件获取数据行
    final Source source = resource("tableNoTH.html"); // 从类路径资源获取tableNoTH.html文件的Source对象（没有表头的HTML文件）
    FileReader t = new FileReader(source); // 创建FileReader对象，使用没有表头的HTML文件作为数据源
    Iterator<Elements> i = t.iterator(); // 获取FileReader的迭代器，用于遍历数据行
    Elements row = i.next(); // 获取第一行数据
    assertThat(row.get(2).text(), is("R0C2")); // 断言：验证第一行第三列的文本内容为"R0C2"
  }

  /** Tests {@link FileReader} iterator with a static file. */
  // 测试FileReader的迭代器功能 - 使用静态文件
  @Test void testFileReaderIterator() throws FileReaderException { // 测试方法：测试FileReader的迭代器功能
    final Source source = resource("tableOK.html"); // 从类路径资源获取tableOK.html文件的Source对象
    FileReader t = new FileReader(source); // 创建FileReader对象，使用本地HTML文件作为数据源
    Elements row = null; // 初始化row变量为null
    for (Elements aT : t) { // 使用增强for循环遍历FileReader（FileReader实现了Iterable接口）
      row = aT; // 将当前行赋值给row变量（循环结束后，row将保存最后一行数据）
    }
    assertNotNull(row); // 断言：验证row不为null（确保至少有一行数据）
    assertThat(row.get(1).text(), is("R2C1")); // 断言：验证最后一行第二列的文本内容为"R2C1"
  }

  /** Tests reading a CSV file via the file adapter. Based on the test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1952">[CALCITE-1952]
   * NPE in planner</a>. */
  // 测试通过文件适配器读取CSV文件。基于[CALCITE-1952]规划器空指针异常的测试用例
  @Test void testCsvFile() throws Exception { // 测试方法：测试通过JDBC接口查询CSV文件中的数据
    Properties info = new Properties(); // 创建Properties对象，用于存储连接配置信息
    final String path = resourcePath("sales-csv"); // 获取sales-csv目录的绝对路径（包含CSV文件）
    final String model = "inline:" // 定义模型字符串，使用inline前缀表示内联JSON配置
        + "{\n" // JSON配置开始
        + "  \"version\": \"1.0\",\n" // 模型版本号
        + "  \"defaultSchema\": \"XXX\",\n" // 默认schema名称
        + "  \"schemas\": [\n" // schemas数组开始
        + "    {\n" // 第一个schema配置开始
        + "      \"name\": \"FILES\",\n" // schema名称为FILES
        + "      \"type\": \"custom\",\n" // schema类型为custom（自定义）
        + "      \"factory\": \"org.apache.calcite.adapter.file.FileSchemaFactory\",\n" // 工厂类：FileSchemaFactory，用于创建文件schema
        + "      \"operand\": {\n" // 工厂参数开始
        + "        \"directory\": " + TestUtil.escapeString(path) + "\n" // 目录参数：CSV文件所在目录的路径
        + "      }\n" // 工厂参数结束
        + "    }\n" // 第一个schema配置结束
        + "  ]\n" // schemas数组结束
        + "}"; // JSON配置结束
    info.put("model", model); // 将模型配置放入Properties对象
    info.put("lex", "JAVA"); // 设置词法分析器为JAVA（标识符大小写敏感）

    try (Connection connection = // 使用try-with-resources语句创建数据库连接（自动关闭）
             DriverManager.getConnection("jdbc:calcite:", info); // 通过DriverManager获取Calcite JDBC连接
         Statement stmt = connection.createStatement()) { // 创建Statement对象，用于执行SQL语句
      final String sql = "select * from FILES.DEPTS"; // 定义SQL查询语句：查询FILES schema下的DEPTS表的所有数据
      final ResultSet rs = stmt.executeQuery(sql); // 执行查询，获取结果集
      assertThat(rs.next(), is(true)); // 断言：验证有第一行数据
      assertThat(rs.getString(1), is("10")); // 断言：验证第一行第一列的值为"10"
      assertThat(rs.next(), is(true)); // 断言：验证有第二行数据
      assertThat(rs.getString(1), is("20")); // 断言：验证第二行第一列的值为"20"
      assertThat(rs.next(), is(true)); // 断言：验证有第三行数据
      assertThat(rs.getString(1), is("30")); // 断言：验证第三行第一列的值为"30"
      assertThat(rs.next(), is(false)); // 断言：验证没有第四行数据（已遍历完所有数据）
      rs.close(); // 关闭结果集（虽然try-with-resources会自动关闭，但显式关闭是良好的编程习惯）
    } // try-with-resources结束，自动关闭Statement和Connection
  }

  /**
   * Tests reading a JSON file via the file adapter.
   */
  // 测试通过文件适配器读取JSON文件
  @Test void testJsonFile() throws Exception { // 测试方法：测试通过JDBC接口查询JSON文件中的数据
    Properties info = new Properties(); // 创建Properties对象，用于存储连接配置信息
    final String path = resourcePath("sales-json"); // 获取sales-json目录的绝对路径（包含JSON文件）
    final String model = "inline:" // 定义模型字符串，使用inline前缀表示内联JSON配置
        + "{\n" // JSON配置开始
        + "  \"version\": \"1.0\",\n" // 模型版本号
        + "  \"defaultSchema\": \"XXX\",\n" // 默认schema名称
        + "  \"schemas\": [\n" // schemas数组开始
        + "    {\n" // 第一个schema配置开始
        + "      \"name\": \"FILES\",\n" // schema名称为FILES
        + "      \"type\": \"custom\",\n" // schema类型为custom（自定义）
        + "      \"factory\": \"org.apache.calcite.adapter.file.FileSchemaFactory\",\n" // 工厂类：FileSchemaFactory，用于创建文件schema
        + "      \"operand\": {\n" // 工厂参数开始
        + "        \"directory\": " + TestUtil.escapeString(path) + "\n" // 目录参数：JSON文件所在目录的路径
        + "      }\n" // 工厂参数结束
        + "    }\n" // 第一个schema配置结束
        + "  ]\n" // schemas数组结束
        + "}"; // JSON配置结束
    info.put("model", model); // 将模型配置放入Properties对象
    info.put("lex", "JAVA"); // 设置词法分析器为JAVA（标识符大小写敏感）

    try (Connection connection = // 使用try-with-resources语句创建数据库连接（自动关闭）
             DriverManager.getConnection("jdbc:calcite:", info); // 通过DriverManager获取Calcite JDBC连接
         Statement stmt = connection.createStatement()) { // 创建Statement对象，用于执行SQL语句
      final String sql = "select * from FILES.DEPTS"; // 定义SQL查询语句：查询FILES schema下的DEPTS表的所有数据
      final ResultSet rs = stmt.executeQuery(sql); // 执行查询，获取结果集
      assertThat(rs.next(), is(true)); // 断言：验证有第一行数据
      assertThat(rs.getString(1), is("10")); // 断言：验证第一行第一列的值为"10"
      assertThat(rs.next(), is(true)); // 断言：验证有第二行数据
      assertThat(rs.getString(1), is("20")); // 断言：验证第二行第一列的值为"20"
      assertThat(rs.next(), is(true)); // 断言：验证有第三行数据
      assertThat(rs.getString(1), is("30")); // 断言：验证第三行第一列的值为"30"
      assertThat(rs.next(), is(false)); // 断言：验证没有第四行数据（已遍历完所有数据）
      rs.close(); // 关闭结果集
    } // try-with-resources结束，自动关闭Statement和Connection
  }

  /**
   * Tests reading two JSON file with join via the file adapter.
   */
  // 测试通过文件适配器读取两个JSON文件并进行连接查询
  @Test void testJsonFileWithJoin() throws Exception { // 测试方法：测试通过JDBC接口对两个JSON文件进行JOIN查询
    Properties info = new Properties(); // 创建Properties对象，用于存储连接配置信息
    final String path = resourcePath("sales-json"); // 获取sales-json目录的绝对路径（包含JSON文件）
    final String model = "inline:" // 定义模型字符串，使用inline前缀表示内联JSON配置
        + "{\n" // JSON配置开始
        + "  \"version\": \"1.0\",\n" // 模型版本号
        + "  \"defaultSchema\": \"XXX\",\n" // 默认schema名称
        + "  \"schemas\": [\n" // schemas数组开始
        + "    {\n" // 第一个schema配置开始
        + "      \"name\": \"FILES\",\n" // schema名称为FILES
        + "      \"type\": \"custom\",\n" // schema类型为custom（自定义）
        + "      \"factory\": \"org.apache.calcite.adapter.file.FileSchemaFactory\",\n" // 工厂类：FileSchemaFactory，用于创建文件schema
        + "      \"operand\": {\n" // 工厂参数开始
        + "        \"directory\": " + TestUtil.escapeString(path) + "\n" // 目录参数：JSON文件所在目录的路径
        + "      }\n" // 工厂参数结束
        + "    }\n" // 第一个schema配置结束
        + "  ]\n" // schemas数组结束
        + "}"; // JSON配置结束
    info.put("model", model); // 将模型配置放入Properties对象
    info.put("lex", "JAVA"); // 设置词法分析器为JAVA（标识符大小写敏感）

    try (Connection connection = // 使用try-with-resources语句创建数据库连接（自动关闭）
             DriverManager.getConnection("jdbc:calcite:", info); // 通过DriverManager获取Calcite JDBC连接
         Statement stmt = connection.createStatement()) { // 创建Statement对象，用于执行SQL语句
      final String sql = "select a.EMPNO,a.NAME,a.CITY,b.DEPTNO " // 定义SQL查询语句：连接查询EMPPS和DEPTS表
          + "from FILES.EMPS a, FILES.DEPTS b where a.DEPTNO = b.DEPTNO"; // 连接条件：两个表的DEPTNO字段相等
      final ResultSet rs = stmt.executeQuery(sql); // 执行查询，获取结果集
      assertThat(rs.next(), is(true)); // 断言：验证有第一行数据
      assertThat(rs.getString(1), is("100")); // 断言：验证第一行第一列（EMPNO）的值为"100"
      assertThat(rs.next(), is(true)); // 断言：验证有第二行数据
      assertThat(rs.getString(1), is("110")); // 断言：验证第二行第一列（EMPNO）的值为"110"
      assertThat(rs.next(), is(true)); // 断言：验证有第三行数据
      assertThat(rs.getString(1), is("120")); // 断言：验证第三行第一列（EMPNO）的值为"120"
      assertThat(rs.next(), is(false)); // 断言：验证没有第四行数据（已遍历完所有数据）
      rs.close(); // 关闭结果集
    } // try-with-resources结束，自动关闭Statement和Connection
  }
} // FileReaderTest类结束
