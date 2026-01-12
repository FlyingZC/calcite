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
// Apache许可证声明，指定代码的使用权限和限制条件
package org.apache.calcite.test; // 声明该类属于org.apache.calcite.test包，这是Calcite测试代码的根包

import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性类，用于读取系统配置属性
import org.apache.calcite.test.schemata.foodmart.FoodmartSchema; // 导入FoodMart测试模式，用于提供测试数据模型
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法

import com.google.common.collect.ImmutableSet; // 导入Google Guava库的不可变集合类，用于创建不可修改的Set集合

import org.junit.jupiter.api.Disabled; // 导入JUnit 5的Disabled注解，用于标记禁用的测试方法
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.sql.Connection; // 导入JDBC连接接口，代表数据库连接
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于创建数据库连接
import java.sql.ResultSet; // 导入JDBC结果集接口，代表SQL查询结果
import java.sql.SQLException; // 导入JDBC异常类，处理数据库操作异常
import java.sql.Statement; // 导入JDBC语句接口，用于执行SQL语句
import java.util.Collection; // 导入Java集合接口，表示一组对象
import java.util.HashSet; // 导入Java HashSet类，实现Set接口的哈希集合
import java.util.Properties; // 导入Java Properties类，用于管理属性键值对
import java.util.Set; // 导入Java Set接口，表示不重复元素的集合
import java.util.function.Function; // 导入Java函数式接口，表示接受一个参数并产生结果的函数

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest匹配器，用于断言两个对象相等
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器，用于断言条件为真
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，用于执行断言检查

/**
 * Unit test of the Calcite adapter for Splunk.
 */
// 单元测试类：测试Calcite的Splunk适配器功能
// Splunk是一个大数据分析平台，Calcite通过适配器可以查询Splunk中的数据
// 该类测试了各种SQL查询在Splunk适配器上的执行情况
class SplunkAdapterTest {
  // 定义Splunk服务器的URL地址，使用HTTPS协议，默认端口8089
  public static final String SPLUNK_URL = "https://localhost:8089"; // Splunk管理端点的URL，用于REST API访问
  // 定义Splunk服务器的用户名，默认为admin
  public static final String SPLUNK_USER = "admin"; // Splunk管理员账户的用户名
  // 定义Splunk服务器的密码，默认为changeme（仅用于测试环境）
  public static final String SPLUNK_PASSWORD = "changeme"; // Splunk管理员账户的密码

  /** Whether this test is enabled. Tests are disabled unless we know that
   * Splunk is present and loaded with the requisite data. */
  // 判断测试是否启用的私有方法
  // 返回true表示启用测试，false表示禁用测试
  // 只有当系统属性TEST_SPLUNK设置为true时，测试才会执行
  // 这是为了避免在没有安装Splunk或没有加载测试数据的环境中运行测试
  private boolean enabled() {
    return CalciteSystemProperty.TEST_SPLUNK.value(); // 读取系统属性TEST_SPLUNK的值，决定是否启用Splunk测试
  }

  // 加载Splunk驱动类的私有方法
// 使用反射机制动态加载Splunk JDBC驱动类
// 这是JDBC连接数据库的标准做法，通过类加载器注册驱动
private void loadDriverClass() {
    try {
      Class.forName("org.apache.calcite.adapter.splunk.SplunkDriver"); // 通过反射加载Splunk驱动类，触发其静态代码块注册驱动
    } catch (ClassNotFoundException e) {
      throw new RuntimeException("driver not found", e); // 如果找不到驱动类，抛出运行时异常
    }
  }

  // 关闭数据库资源的私有方法
// 安全地关闭Statement和Connection对象，释放数据库资源
// 即使关闭过程中出现异常也会继续关闭其他资源
private void close(Connection connection, Statement statement) {
    if (statement != null) { // 检查Statement对象是否为null
      try {
        statement.close(); // 关闭Statement对象，释放相关资源
      } catch (SQLException e) {
        // ignore // 忽略关闭异常，继续执行
      }
    }
    if (connection != null) { // 检查Connection对象是否为null
      try {
        connection.close(); // 关闭Connection对象，释放数据库连接
      } catch (SQLException e) {
        // ignore // 忽略关闭异常
      }
    }
  }

  /**
   * Tests the vanity driver.
   */
  // 测试Splunk的虚荣驱动（vanity driver）
  // 虚荣驱动是指使用JDBC URL但通过Properties传递连接参数的方式
  // 这是JDBC的一种标准连接方式，将连接参数与URL分离
  @Test void testVanityDriver() throws SQLException {
    loadDriverClass(); // 加载Splunk驱动类
    if (!enabled()) { // 检查是否启用了Splunk测试
      return; // 如果未启用，直接返回，不执行后续测试
    }
    Properties info = new Properties(); // 创建Properties对象用于存储连接参数
    info.setProperty("url", SPLUNK_URL); // 设置Splunk服务器URL属性
    info.put("user", SPLUNK_USER); // 设置用户名属性
    info.put("password", SPLUNK_PASSWORD); // 设置密码属性
    Connection connection =
        DriverManager.getConnection("jdbc:splunk:", info); // 使用驱动管理器获取Splunk连接，URL为jdbc:splunk:，参数在Properties中
    connection.close(); // 关闭数据库连接，释放资源
  }

  /**
   * Tests the vanity driver with properties in the URL.
   */
  // 测试将连接参数直接嵌入到URL中的虚荣驱动
  // 这种方式将所有连接参数（URL、用户名、密码）都编码到JDBC URL字符串中
  // 参数之间用分号分隔，每个参数用引号括起来
  @Test void testVanityDriverArgsInUrl() throws SQLException {
    loadDriverClass(); // 加载Splunk驱动类
    if (!enabled()) { // 检查是否启用了Splunk测试
      return; // 如果未启用，直接返回
    }
    Connection connection =
        DriverManager.getConnection("jdbc:splunk:" // 使用驱动管理器获取连接，所有参数都在URL中
            + "url='" + SPLUNK_URL + "'" // 拼接Splunk服务器URL参数
            + ";user='" + SPLUNK_USER + "'" // 拼接用户名参数
            + ";password='" + SPLUNK_PASSWORD + "'"); // 拼接密码参数
    connection.close(); // 关闭数据库连接
  }

  // 定义SQL测试字符串数组，包含各种SQL查询语句
// 这些语句用于测试Splunk适配器对不同SQL语法的支持情况
// 包括基本查询、聚合、分组、排序、连接等操作
static final String[] SQL_STRINGS = {
      "select \"source\", \"sourcetype\"\n" // 基本查询：选择source和sourcetype两个字段
          + "from \"splunk\".\"splunk\"", // 从splunk.splunk表中查询

      "select \"sourcetype\"\n" // 单字段查询：只选择sourcetype字段
          + "from \"splunk\".\"splunk\"", // 从splunk.splunk表中查询

      "select distinct \"sourcetype\"\n" // 去重查询：选择不重复的sourcetype值
          + "from \"splunk\".\"splunk\"", // 从splunk.splunk表中查询

      "select count(\"sourcetype\")\n" // 聚合查询：计算sourcetype字段的行数
          + "from \"splunk\".\"splunk\"", // 从splunk.splunk表中查询

      // gives wrong answer, not error. currently returns same as count.
      "select count(distinct \"sourcetype\")\n" // 去重计数查询：计算sourcetype的不同值数量（当前实现有bug）
          + "from \"splunk\".\"splunk\"", // 从splunk.splunk表中查询

      "select \"sourcetype\", count(\"source\")\n" // 分组聚合查询：按sourcetype分组，计算每组的source数量
          + "from \"splunk\".\"splunk\"\n" // 从splunk.splunk表中查询
          + "group by \"sourcetype\"", // 按sourcetype字段分组

      "select \"sourcetype\", count(\"source\") as c\n" // 分组+排序查询：按sourcetype分组并按计数降序排序
          + "from \"splunk\".\"splunk\"\n" // 从splunk.splunk表中查询
          + "group by \"sourcetype\"\n" // 按sourcetype字段分组
          + "order by c desc\n", // 按计数c降序排列

      // group + order
      "select s.\"product_id\", count(\"source\") as c\n" // 带过滤条件的分组查询：按产品ID分组统计
          + "from \"splunk\".\"splunk\" as s\n" // 从splunk.splunk表中查询，使用别名s
          + "where s.\"sourcetype\" = 'access_combined_wcookie'\n" // 过滤条件：只查询access_combined_wcookie类型
          + "group by s.\"product_id\"\n" // 按product_id字段分组
          + "order by c desc\n", // 按计数c降序排列

      // non-advertised field
      "select s.\"sourcetype\", s.\"action\" from \"splunk\".\"splunk\" as s", // 选择非公开字段action的查询

      "select s.\"source\", s.\"product_id\", s.\"product_name\", s.\"method\"\n" // 多字段查询：选择多个字段
          + "from \"splunk\".\"splunk\" as s\n" // 从splunk.splunk表中查询，使用别名s
          + "where s.\"sourcetype\" = 'access_combined_wcookie'\n", // 过滤条件：只查询access_combined_wcookie类型

      "select p.\"product_name\", s.\"action\"\n" // 连接查询：Splunk表与MySQL表连接
          + "from \"splunk\".\"splunk\" as s\n" // 从splunk.splunk表中查询，使用别名s
          + "  join \"mysql\".\"products\" as p\n" // 与mysql.products表连接，使用别名p
          + "on s.\"product_id\" = p.\"product_id\"", // 连接条件：product_id相等

      "select s.\"source\", s.\"product_id\", p.\"product_name\", p.\"price\"\n" // 带过滤条件的连接查询
          + "from \"splunk\".\"splunk\" as s\n" // 从splunk.splunk表中查询，使用别名s
          + "    join \"mysql\".\"products\" as p\n" // 与mysql.products表连接，使用别名p
          + "    on s.\"product_id\" = p.\"product_id\"\n" // 连接条件：product_id相等
          + "where s.\"sourcetype\" = 'access_combined_wcookie'\n", // 过滤条件：只查询access_combined_wcookie类型
  };

  // 定义预期会出错的SQL测试字符串数组
  // 这些SQL语句用于测试Splunk适配器对错误SQL的处理能力
  // 包括语法错误、字段不存在、pushdown规则限制等情况
  static final String[] ERROR_SQL_STRINGS = {
      // gives error in SplunkPushDownRule
      "select count(*) from \"splunk\".\"splunk\"", // 使用count(*)查询，会在SplunkPushDownRule中报错

      // gives no rows; suspect off-by-one because no base fields are
      // referenced
      "select s.\"product_id\", s.\"product_name\", s.\"method\"\n" // 查询不存在的字段，可能返回空结果
          + "from \"splunk\".\"splunk\" as s\n" // 从splunk.splunk表中查询
          + "where s.\"sourcetype\" = 'access_combined_wcookie'\n", // 过滤条件

      // horrible error if you access a field that doesn't exist
      "select s.\"sourcetype\", s.\"access\"\n" // 查询不存在的access字段，会导致严重错误
          + "from \"splunk\".\"splunk\" as s", // 从splunk.splunk表中查询
  };

  // Fields:
  // sourcetype=access_* // 说明：Splunk数据中的sourcetype字段值以access_开头
  // action (purchase | update) // 说明：action字段的可能值为purchase或update
  // method (GET | POST) // 说明：method字段的可能值为GET或POST

  /**
   * Reads from a table.
   */
  // 测试从Splunk表中读取数据的基本SELECT查询
  // 验证Splunk适配器能够正确执行简单的SQL查询并返回结果
  @Test void testSelect() throws SQLException {
    final String sql = "select \"source\", \"sourcetype\"\n" // 定义SQL查询：选择source和sourcetype字段
        + "from \"splunk\".\"splunk\""; // 从splunk.splunk表中查询
    checkSql(sql, resultSet -> { // 执行SQL并验证结果，传入一个函数来处理结果集
      try {
        if (!(resultSet.next() && resultSet.next() && resultSet.next())) { // 检查结果集是否有至少3行数据
          throw new AssertionError("expected at least 3 rows"); // 如果不足3行，抛出断言错误
        }
        return null; // 返回null（函数需要返回Void）
      } catch (SQLException e) {
        throw TestUtil.rethrow(e); // 将SQLException包装为运行时异常抛出
      }
    });
  }

  // 测试SELECT DISTINCT查询，验证去重功能
  // 检查Splunk适配器能否正确返回不重复的sourcetype值
  @Test void testSelectDistinct() throws SQLException {
    checkSql( // 执行SQL查询并验证结果
        "select distinct \"sourcetype\"\n" // SQL：选择不重复的sourcetype值
        + "from \"splunk\".\"splunk\"", // 从splunk.splunk表中查询
        expect("sourcetype=access_combined_wcookie", // 期望结果：包含access_combined_wcookie类型
            "sourcetype=vendor_sales", // 期望结果：包含vendor_sales类型
            "sourcetype=secure")); // 期望结果：包含secure类型
  }

  // 创建一个用于验证查询结果的函数
  // 该函数接受结果集，将实际结果与期望结果进行比较
  // 使用函数式编程的方式，返回一个Function<ResultSet, Void>对象
  private static Function<ResultSet, Void> expect(final String... lines) {
    final Collection<String> expected = ImmutableSet.copyOf(lines); // 将期望的行转换为不可变集合
    return a0 -> { // 返回一个函数，接受ResultSet参数
      try {
        Collection<String> actual =
            CalciteAssert.toStringList(a0, new HashSet<>()); // 将结果集转换为字符串集合
        assertThat(actual, equalTo(expected)); // 断言实际结果等于期望结果
        return null; // 返回null
      } catch (SQLException e) {
        throw TestUtil.rethrow(e); // 将SQLException包装为运行时异常抛出
      }
    };
  }

  /** "status" is not a built-in column but we know it has some values in the
   * test data. */
  // 测试查询非内置列（built-in column）
  // status字段不是Splunk的预定义字段，但在测试数据中有值
  // 验证Splunk适配器能够查询动态字段
  @Test void testSelectNonBuiltInColumn() throws SQLException {
    checkSql( // 执行SQL查询并验证结果
        "select \"status\"\n" // SQL：选择status字段
        + "from \"splunk\".\"splunk\"", a0 -> { // 从splunk.splunk表中查询，传入结果集处理函数
          final Set<String> actual = new HashSet<>(); // 创建集合存储实际结果
          try {
            while (a0.next()) { // 遍历结果集的每一行
              actual.add(a0.getString(1)); // 获取第一列的值并添加到集合
            }
            assertThat(actual.contains("404"), is(true)); // 断言结果中包含"404"状态码
            return null; // 返回null
          } catch (SQLException e) {
            throw TestUtil.rethrow(e); // 将SQLException包装为运行时异常抛出
          }
        });
  }

  // 测试Splunk表与JDBC表（FoodMart）的连接查询
  // 使用CAST函数进行类型转换
  // 注意：此测试被禁用，因为ON子句中的CAST导致无法生成执行计划
  @Disabled("cannot plan due to CAST in ON clause") // 禁用此测试，说明原因
  @Test void testJoinToJdbc() throws SQLException {
    checkSql( // 执行SQL查询
        "select p.\"product_name\", /*s.\"product_id\",*/ s.\"action\"\n" // SQL：选择product_name和action字段（product_id被注释）
            + "from \"splunk\".\"splunk\" as s\n" // 从splunk.splunk表查询，别名s
            + "join \"foodmart\".\"product\" as p\n" // 与foodmart.product表连接，别名p
            + "on cast(s.\"product_id\" as integer) = p.\"product_id\"\n" // 连接条件：将product_id转换为整数后比较
            + "where s.\"action\" = 'PURCHASE'", // 过滤条件：action为PURCHASE
        null); // 不进行结果验证
  }

  // 测试GROUP BY分组查询，验证聚合和排序功能
  // 按host字段分组，统计每个host的source数量，并按计数降序排列
  @Test void testGroupBy() throws SQLException {
    checkSql( // 执行SQL查询并验证结果
        "select s.\"host\", count(\"source\") as c\n" // SQL：选择host字段和source的计数（别名为c）
            + "from \"splunk\".\"splunk\" as s\n" // 从splunk.splunk表查询，别名s
            + "group by s.\"host\"\n" // 按host字段分组
            + "order by c desc\n", // 按计数c降序排列
        expect("host=vendor_sales; C=30244", // 期望结果：vendor_sales主机有30244条记录
            "host=www1; C=24221", // 期望结果：www1主机有24221条记录
            "host=www3; C=22975", // 期望结果：www3主机有22975条记录
            "host=www2; C=22595", // 期望结果：www2主机有22595条记录
            "host=mailsv; C=9829")); // 期望结果：mailsv主机有9829条记录
  }

  // 执行SQL查询并验证结果的核心方法
  // 该方法负责建立Splunk连接、执行SQL、处理结果集并清理资源
  // 是所有测试方法的底层实现
  private void checkSql(String sql, Function<ResultSet, Void> f)
      throws SQLException {
    if (!enabled()) { // 检查是否启用了Splunk测试
      return; // 如果未启用，直接返回
    }
    loadDriverClass(); // 加载Splunk驱动类
    Connection connection = null; // 初始化连接对象为null
    Statement statement = null; // 初始化语句对象为null
    try {
      Properties info = new Properties(); // 创建Properties对象存储连接参数
      info.put("url", SPLUNK_URL); // 设置Splunk服务器URL
      info.put("user", SPLUNK_USER); // 设置用户名
      info.put("password", SPLUNK_PASSWORD); // 设置密码
      info.put("model", "inline:" + FoodmartSchema.FOODMART_MODEL); // 设置数据模型，使用FoodMart模型
      connection = DriverManager.getConnection("jdbc:splunk:", info); // 获取Splunk数据库连接
      statement = connection.createStatement(); // 创建SQL语句对象
      final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询，获取结果集
      f.apply(resultSet); // 应用传入的函数处理结果集（验证结果）
      resultSet.close(); // 关闭结果集
    } finally {
      close(connection, statement); // 在finally块中确保关闭连接和语句
    }
  }
} // 类定义结束
