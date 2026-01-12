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
 */ // Apache软件基金会许可证声明，说明本代码遵循Apache 2.0许可证，允许在特定条件下使用和修改
package org.apache.calcite.test; // 声明当前类所在的包为org.apache.calcite.test，这是Calcite框架的测试包

import org.apache.calcite.adapter.csv.CsvSchemaFactory; // 导入CSV Schema工厂类，用于创建基于CSV文件的Schema
import org.apache.calcite.adapter.csv.CsvStreamTableFactory; // 导入CSV流表工厂类，用于创建支持流式处理的CSV表
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，是Calcite JDBC驱动的核心连接接口
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示Calcite中的数据模式定义
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入SQL到关系代数转换器，负责将SQL语句转换为关系表达式
import org.apache.calcite.util.Sources; // 导入Sources工具类，用于处理各种数据源
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试相关的辅助方法
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种便捷方法

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类，用于创建不可修改的映射
import com.google.common.collect.Ordering; // 导入Google Guava的排序工具类，用于比较和排序

import org.checkerframework.checker.nullness.qual.Nullable; // 导入空值检查注解，用于标记可能为null的值
import org.junit.jupiter.api.Disabled; // 导入Junit5的Disabled注解，用于禁用测试方法
import org.junit.jupiter.api.Test; // 导入Junit5的Test注解，标记测试方法
import org.junit.jupiter.api.Timeout; // 导入Junit5的Timeout注解，设置测试超时时间
import org.junit.jupiter.params.ParameterizedTest; // 导入Junit5的参数化测试注解
import org.junit.jupiter.params.provider.MethodSource; // 导入Junit5的方法源提供者注解，用于提供参数化测试的数据

import java.io.File; // 导入Java文件类，用于文件操作
import java.io.PrintStream; // 导入打印流类，用于输出数据
import java.io.PrintWriter; // 导入打印写入器类，用于格式化输出
import java.net.URL; // 导入URL类，用于统一资源定位符处理
import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于获取数据库连接
import java.sql.PreparedStatement; // 导入预编译语句接口，用于执行参数化SQL
import java.sql.ResultSet; // 导入结果集接口，表示查询结果
import java.sql.ResultSetMetaData; // 导入结果集元数据接口，获取结果集的结构信息
import java.sql.SQLException; // 导入SQL异常类，处理数据库操作异常
import java.sql.Statement; // 导入语句接口，用于执行静态SQL语句
import java.sql.Timestamp; // 导入时间戳类，表示SQL TIMESTAMP类型
import java.util.ArrayList; // 导入动态数组列表类
import java.util.Arrays; // 导入数组工具类
import java.util.Collections; // 导入集合工具类
import java.util.Iterator; // 导入迭代器接口
import java.util.List; // 导入列表接口
import java.util.Properties; // 导入属性类，用于配置信息
import java.util.concurrent.ArrayBlockingQueue; // 导入数组阻塞队列类，用于线程间通信
import java.util.concurrent.BlockingQueue; // 导入阻塞队列接口
import java.util.concurrent.Callable; // 导入可调用接口，用于支持返回值的任务
import java.util.function.Consumer; // 导入消费者函数式接口
import java.util.stream.Stream; // 导入流接口，用于流式处理

import static org.apache.calcite.test.Matchers.isListOf; // 导入静态方法，用于匹配列表

import static org.hamcrest.CoreMatchers.anyOf; // 导入Hamcrest匹配器，用于断言任意一个条件
import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器，用于断言
import static org.hamcrest.CoreMatchers.isA; // 导入Hamcrest匹配器，用于断言类型
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest匹配器，用于断言null值
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言方法
import static org.junit.jupiter.api.Assertions.fail; // 导入Junit5的fail方法，用于测试失败

import static java.sql.Timestamp.valueOf; // 导入Timestamp的静态工厂方法
import static java.util.Objects.requireNonNull; // 导入Objects的requireNonNull方法，用于空值检查

/**
 * Unit test of the Calcite adapter for CSV.
 */ // Calcite CSV适配器的单元测试类，用于测试Calcite框架对CSV文件数据源的适配功能
class CsvTest { // CsvTest类：Calcite CSV适配器的测试类，包含各种测试方法来验证CSV数据源的查询、过滤、聚合等功能
  private void close(@Nullable Connection connection, // close方法：安全关闭JDBC连接和语句的辅助方法，参数connection为数据库连接对象，可能为null
      @Nullable Statement statement) { // 参数statement为SQL语句对象，可能为null
    if (statement != null) { // 如果语句对象不为null，则关闭语句
      try {
        statement.close(); // 关闭语句对象，释放相关资源
      } catch (SQLException e) { // 捕获SQL异常
        // ignore // 忽略关闭时的异常，因为连接可能已经关闭
      }
    }
    if (connection != null) { // 如果连接对象不为null，则关闭连接
      try {
        connection.close(); // 关闭数据库连接，释放数据库资源
      } catch (SQLException e) { // 捕获SQL异常
        // ignore // 忽略关闭时的异常
      }
    }
  } // close方法结束：确保连接和语句被正确关闭，避免资源泄漏


  /** Quotes a string for Java or JSON. */ // escapeString方法：为Java或JSON字符串添加引号并转义特殊字符
  private static String escapeString(String s) { // 参数s为需要转义的字符串
    return escapeString(new StringBuilder(), s).toString(); // 调用重载方法，使用StringBuilder构建转义后的字符串并返回
  } // escapeString方法结束：返回转义后的字符串，确保特殊字符被正确处理

  /** Quotes a string for Java or JSON, into a builder. */ // escapeString重载方法：将字符串转义并添加到StringBuilder中
  private static StringBuilder escapeString(StringBuilder buf, String s) { // 参数buf为字符串构建器，参数s为需要转义的字符串
    buf.append('"'); // 添加起始双引号
    int n = s.length(); // 获取字符串长度
    char lastChar = 0; // 记录上一个字符，用于处理换行符
    for (int i = 0; i < n; ++i) { // 遍历字符串中的每个字符
      char c = s.charAt(i); // 获取当前位置的字符
      switch (c) { // 根据字符类型进行转义处理
      case '\\': // 反斜杠字符
        buf.append("\\\\"); // 转义为双反斜杠
        break;
      case '"': // 双引号字符
        buf.append("\\\""); // 转义为反斜杠加双引号
        break;
      case '\n': // 换行符
        buf.append("\\n"); // 转义为\n
        break;
      case '\r': // 回车符
        if (lastChar != '\n') { // 如果前一个字符不是换行符
          buf.append("\\r"); // 转义为\r
        }
        break;
      default: // 其他字符
        buf.append(c); // 直接添加字符
        break;
      }
      lastChar = c; // 更新上一个字符记录
    }
    return buf.append('"'); // 添加结束双引号并返回StringBuilder
  } // escapeString重载方法结束：将字符串转义后添加到StringBuilder中，处理了各种特殊字符

  static Stream<String> explainFormats() { // explainFormats方法：提供解释计划格式的流，用于参数化测试
    return Stream.of("text", "dot"); // 返回包含"text"和"dot"两种格式的流，分别对应文本格式和图形格式
  } // explainFormats方法结束：返回支持的解释计划格式流

  /**
   * Tests the vanity driver.
   */ // testVanityDriver方法：测试vanity驱动（自定义JDBC驱动）的基本功能
  @Disabled // 禁用此测试，可能因为驱动已废弃或需要特殊环境
  @Test void testVanityDriver() throws SQLException { // 测试方法声明，可能抛出SQL异常
    Properties info = new Properties(); // 创建空的属性对象
    Connection connection = // 获取数据库连接
        DriverManager.getConnection("jdbc:csv:", info); // 使用CSV驱动的JDBC URL连接
    connection.close(); // 关闭连接
  } // testVanityDriver方法结束：验证vanity驱动能够正常建立和关闭连接

  /**
   * Tests the vanity driver with properties in the URL.
   */ // testVanityDriverArgsInUrl方法：测试在URL中传递属性的vanity驱动功能
  @Disabled // 禁用此测试
  @Test void testVanityDriverArgsInUrl() throws SQLException { // 测试方法声明
    Connection connection = // 获取数据库连接
        DriverManager.getConnection("jdbc:csv:" // 使用CSV驱动的JDBC URL
            + "directory='foo'"); // 在URL中指定目录属性为foo
    connection.close(); // 关闭连接
  } // testVanityDriverArgsInUrl方法结束：验证驱动能够从URL中解析属性

  /** Tests an inline schema with a non-existent directory. */ // testBadDirectory方法：测试使用不存在的目录的内联Schema
  @Test void testBadDirectory() throws SQLException { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", // 设置model属性为内联JSON模型
        "inline:" // 使用内联模型
            + "{\n" // JSON开始
            + "  version: '1.0',\n" // 模型版本
            + "   schemas: [\n" // Schema列表开始
            + "     {\n" // Schema定义开始
            + "       type: 'custom',\n" // Schema类型为自定义
            + "       name: 'bad',\n" // Schema名称为bad
            + "       factory: 'org.apache.calcite.adapter.csv.CsvSchemaFactory',\n" // 使用CSV Schema工厂
            + "       operand: {\n" // 工厂参数开始
            + "         directory: '/does/not/exist'\n" // 指定一个不存在的目录
            + "       }\n" // 工厂参数结束
            + "     }\n" // Schema定义结束
            + "   ]\n" // Schema列表结束
            + "}"); // JSON结束

    Connection connection = // 获取数据库连接
        DriverManager.getConnection("jdbc:calcite:", info); // 使用Calcite JDBC驱动连接
    // must print "directory ... not found" to stdout, but not fail // 必须输出目录未找到的消息，但不能失败
    ResultSet tables = // 获取表的元数据
        connection.getMetaData().getTables(null, null, null, null); // 查询所有表
    tables.next(); // 移动到第一行（即使没有表也会有一行）
    tables.close(); // 关闭结果集
    connection.close(); // 关闭连接
  } // testBadDirectory方法结束：验证系统能够优雅地处理不存在的目录错误

  /**
   * Reads from a table.
   */ // testSelect方法：测试从表中读取数据的基本SELECT查询
  @Test void testSelect() { // 测试方法声明
    sql("model", "select * from EMPS").ok(); // 执行SQL查询并验证结果，从EMPS表中选择所有列
  } // testSelect方法结束：验证基本的SELECT查询能够正常工作

  @Test void testSelectSingleProjectGz() { // testSelectSingleProjectGz方法：测试从压缩的CSV表中选择单个列
    sql("smart", "select name from EMPS").ok(); // 执行SQL查询，从EMPS表中选择name列，使用smart模型
  } // testSelectSingleProjectGz方法结束：验证单列查询能够正常工作

  @Test void testSelectSingleProject() { // testSelectSingleProject方法：测试从DEPTS表中选择单个列
    sql("smart", "select name from DEPTS").ok(); // 执行SQL查询，从DEPTS表中选择name列，使用smart模型
  } // testSelectSingleProject方法结束：验证单列查询能够正常工作

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-898">[CALCITE-898]
   * Type inference multiplying Java long by SQL INTEGER</a>. */ // testSelectLongMultiplyInteger方法：测试Java long类型与SQL INTEGER类型相乘的类型推断问题
  @Test void testSelectLongMultiplyInteger() { // 测试方法声明
    final String sql = "select empno * 3 as e3\n" // SQL查询：将empno乘以3并命名为e3
        + "from long_emps where empno = 100"; // 从long_emps表中选择empno为100的记录

    sql("bug", sql).checking(resultSet -> { // 执行SQL并检查结果
      try {
        assertThat(resultSet.next(), is(true)); // 验证有结果返回
        Long o = (Long) resultSet.getObject(1); // 获取第一列的值，应为Long类型
        assertThat(o, is(300L)); // 验证结果为300L
        assertThat(resultSet.next(), is(false)); // 验证只有一条记录
      } catch (SQLException e) { // 捕获SQL异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    }).ok(); // 验证测试通过
  } // testSelectLongMultiplyInteger方法结束：验证long与integer相乘的类型推断正确

  @Test void testCustomTable() { // testCustomTable方法：测试自定义表功能
    sql("model-with-custom-table", "select * from CUSTOM_TABLE.EMPS").ok(); // 执行SQL查询，从自定义表CUSTOM_TABLE.EMPS中选择所有列
  } // testCustomTable方法结束：验证自定义表查询能够正常工作

  @Test void testPushDownProjectDumb() { // testPushDownProjectDumb方法：测试在dumb模型下不会触发投影下推规则
    // rule does not fire, because we're using 'dumb' tables in simple model // 规则不会触发，因为我们在简单模型中使用dumb表
    final String sql = "explain plan for select * from EMPS"; // SQL查询：解释EMPS表的查询计划
    final String expected = "PLAN=EnumerableTableScan(table=[[SALES, EMPS]])\n"; // 预期结果：使用EnumerableTableScan而不是CsvTableScan
    sql("model", sql).returns(expected).ok(); // 执行SQL并验证结果与预期一致
  } // testPushDownProjectDumb方法结束：验证dumb模型不会触发优化规则

  @Test void testPushDownProject() { // testPushDownProject方法：测试在smart模型下会触发投影下推规则
    final String sql = "explain plan for select * from EMPS"; // SQL查询：解释EMPS表的查询计划
    final String expected = "PLAN=CsvTableScan(table=[[SALES, EMPS]], " // 预期结果：使用CsvTableScan并指定所有字段索引
        + "fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]])\n"; // 所有字段的索引列表
    sql("smart", sql).returns(expected).ok(); // 执行SQL并验证结果与预期一致
    // make sure that it works... // 确保实际查询也能正常工作
    sql("smart", "select * from EMPS") // 执行实际查询
        .returns( // 验证返回的结果
            "EMPNO=100; NAME=Fred; DEPTNO=10; GENDER=; CITY=; EMPID=30; AGE=25; SLACKER=true; MANAGER=false; JOINEDAT=1996-08-03", // 第一条记录
            "EMPNO=110; NAME=Eric; DEPTNO=20; GENDER=M; CITY=San Francisco; EMPID=3; AGE=80; SLACKER=null; MANAGER=false; JOINEDAT=2001-01-01", // 第二条记录
            "EMPNO=110; NAME=John; DEPTNO=40; GENDER=M; CITY=Vancouver; EMPID=2; AGE=null; SLACKER=false; MANAGER=true; JOINEDAT=2002-05-03", // 第三条记录
            "EMPNO=120; NAME=Wilma; DEPTNO=20; GENDER=F; CITY=; EMPID=1; AGE=5; SLACKER=null; MANAGER=true; JOINEDAT=2005-09-07", // 第四条记录
            "EMPNO=130; NAME=Alice; DEPTNO=40; GENDER=F; CITY=Vancouver; EMPID=2; AGE=null; SLACKER=false; MANAGER=true; JOINEDAT=2007-01-01") // 第五条记录
        .ok(); // 验证测试通过
  } // testPushDownProject方法结束：验证smart模型能够正确触发投影下推优化

  @Test void testPushDownProject2() { // testPushDownProject2方法：测试只选择部分列时的投影下推
    sql("smart", "explain plan for select name, empno from EMPS") // SQL查询：解释只选择name和empno列的查询计划
        .returns("PLAN=CsvTableScan(table=[[SALES, EMPS]], fields=[[1, 0]])\n") // 预期结果：只读取索引为1和0的字段（name和empno）
        .ok(); // 验证测试通过
    // make sure that it works... // 确保实际查询也能正常工作
    sql("smart", "select name, empno from EMPS") // 执行实际查询
        .returns( // 验证返回的结果
            "NAME=Fred; EMPNO=100", // 第一条记录
            "NAME=Eric; EMPNO=110", // 第二条记录
            "NAME=John; EMPNO=110", // 第三条记录
            "NAME=Wilma; EMPNO=120", // 第四条记录
            "NAME=Alice; EMPNO=130") // 第五条记录
        .ok(); // 验证测试通过
  } // testPushDownProject2方法结束：验证部分列查询能够正确触发投影下推，只读取需要的列

  @ParameterizedTest // 参数化测试注解
  @MethodSource("explainFormats") // 使用explainFormats方法提供参数
  void testPushDownProjectAggregate(String format) { // testPushDownProjectAggregate方法：测试带聚合的投影下推，参数format为解释计划格式
    String expected = null; // 预期结果字符串
    String extra = null; // 额外的SQL子句
    switch (format) { // 根据格式类型设置预期结果
    case "dot": // 图形格式（dot）
      expected = "PLAN=digraph {\n" // 预期结果：dot格式的图形表示
          + "\"CsvTableScan\\ntable = [SALES, EMPS\\n]\\nfields = [3]\\n\" -> " // CsvTableScan节点，只读取gender字段（索引3）
          + "\"EnumerableAggregate\\ngroup = {0}\\nEXPR$1 = COUNT()\\n\" [label=\"0\"]\n" // EnumerableAggregate节点，按gender分组并计数
          + "}\n"; // 图结束
      extra = " as dot "; // 额外的SQL子句：指定为dot格式
      break;
    case "text": // 文本格式
      expected = "PLAN=" // 预期结果：文本格式的树状表示
          + "EnumerableAggregate(group=[{0}], EXPR$1=[COUNT()])\n" // 聚合节点
          + "  CsvTableScan(table=[[SALES, EMPS]], fields=[[3]])\n"; // 表扫描节点，只读取gender字段
      extra = ""; // 无额外子句
      break;
    }
    final String sql = "explain plan " + extra + "for\n" // SQL查询：解释查询计划
        + "select gender, count(*) from EMPS group by gender"; // 按gender分组并计算数量
    sql("smart", sql).returns(expected).ok(); // 执行SQL并验证结果与预期一致
  } // testPushDownProjectAggregate方法结束：验证聚合查询能够正确触发投影下推

  @ParameterizedTest // 参数化测试注解
  @MethodSource("explainFormats") // 使用explainFormats方法提供参数
  void testPushDownProjectAggregateWithFilter(String format) { // testPushDownProjectAggregateWithFilter方法：测试带过滤和聚合的投影下推
    String expected = null; // 预期结果字符串
    String extra = null; // 额外的SQL子句
    switch (format) { // 根据格式类型设置预期结果
    case "dot": // 图形格式
      expected = "PLAN=digraph {\n" // 预期结果：dot格式的图形表示
          + "\"EnumerableCalc\\nexpr#0..1 = {inputs}\\nexpr#2 = 'F':VARCHAR\\nexpr#3 = =($t1, $t2)" // 计算节点，过滤gender='F'
          + "\\nproj#0..1 = {exprs}\\n$condition = $t3\" -> \"EnumerableAggregate\\ngroup = " // 计算节点指向聚合节点
          + "{}\\nEXPR$0 = MAX($0)\\n\" [label=\"0\"]\n" // 聚合节点，计算最大值
          + "\"CsvTableScan\\ntable = [SALES, EMPS\\n]\\nfields = [0, 3]\\n\" -> " // 表扫描节点，只读取empno和gender字段
          + "\"EnumerableCalc\\nexpr#0..1 = {inputs}\\nexpr#2 = 'F':VARCHAR\\nexpr#3 = =($t1, $t2)" // 表扫描指向计算节点
          + "\\nproj#0..1 = {exprs}\\n$condition = $t3\" [label=\"0\"]\n" // 计算节点详情
          + "}\n"; // 图结束
      extra = " as dot "; // 额外的SQL子句：指定为dot格式
      break;
    case "text": // 文本格式
      expected = "PLAN=" // 预期结果：文本格式的树状表示
          + "EnumerableAggregate(group=[{}], EXPR$0=[MAX($0)])\n" // 聚合节点，计算最大值
          + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=['F':VARCHAR], " // 计算节点，过滤条件
          + "expr#3=[=($t1, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 计算节点详情
          + "    CsvTableScan(table=[[SALES, EMPS]], fields=[[0, 3]])\n"; // 表扫描节点，只读取empno和gender字段
      extra = ""; // 无额外子句
      break;
    }
    final String sql = "explain plan " + extra + " for\n" // SQL查询：解释查询计划
        + "select max(empno) from EMPS where gender='F'"; // 查询gender为F的员工的最大empno
    sql("smart", sql).returns(expected).ok(); // 执行SQL并验证结果与预期一致
  } // testPushDownProjectAggregateWithFilter方法结束：验证带过滤的聚合查询能够正确触发投影下推

  @ParameterizedTest // 参数化测试注解
  @MethodSource("explainFormats") // 使用explainFormats方法提供参数
  void testPushDownProjectAggregateNested(String format) { // testPushDownProjectAggregateNested方法：测试嵌套聚合的投影下推
    String expected = null; // 预期结果字符串
    String extra = null; // 额外的SQL子句
    switch (format) { // 根据格式类型设置预期结果
    case "dot": // 图形格式
      expected = "PLAN=digraph {\n" // 预期结果：dot格式的图形表示
          + "\"EnumerableAggregate\\ngroup = {0, 1}\\nQTY = COUNT()\\n\" -> " // 内层聚合节点，按name和gender分组计数
          + "\"EnumerableAggregate\\ngroup = {1}\\nEXPR$1 = MAX($2)\\n\" [label=\"0\"]\n" // 外层聚合节点，按gender分组计算最大值
          + "\"CsvTableScan\\ntable = [SALES, EMPS\\n]\\nfields = [1, 3]\\n\" -> " // 表扫描节点，只读取name和gender字段
          + "\"EnumerableAggregate\\ngroup = {0, 1}\\nQTY = COUNT()\\n\" [label=\"0\"]\n" // 表扫描指向内层聚合节点
          + "}\n"; // 图结束
      extra = " as dot "; // 额外的SQL子句：指定为dot格式
      break;
    case "text": // 文本格式
      expected = "PLAN=" // 预期结果：文本格式的树状表示
          + "EnumerableAggregate(group=[{1}], EXPR$1=[MAX($2)])\n" // 外层聚合节点
          + "  EnumerableAggregate(group=[{0, 1}], QTY=[COUNT()])\n" // 内层聚合节点
          + "    CsvTableScan(table=[[SALES, EMPS]], fields=[[1, 3]])\n"; // 表扫描节点，只读取name和gender字段
      extra = ""; // 无额外子句
      break;
    }
    final String sql = "explain plan " + extra + " for\n" // SQL查询：解释查询计划
        + "select gender, max(qty)\n" // 按gender分组，计算qty的最大值
        + "from (\n" // 子查询开始
        + "  select name, gender, count(*) qty\n" // 按name和gender分组，计算数量
        + "  from EMPS\n" // 从EMPS表查询
        + "  group by name, gender) t\n" // 子查询结束，别名为t
        + "group by gender"; // 按gender分组
    sql("smart", sql).returns(expected).ok(); // 执行SQL并验证结果与预期一致
  } // testPushDownProjectAggregateNested方法结束：验证嵌套聚合查询能够正确触发投影下推

  @Test void testFilterableSelect() { // testFilterableSelect方法：测试可过滤表的基本查询
    sql("filterable-model", "select name from EMPS").ok(); // 执行SQL查询，从可过滤模型的EMPS表中选择name列
  } // testFilterableSelect方法结束：验证可过滤表的基本查询能够正常工作

  @Test void testFilterableSelectStar() { // testFilterableSelectStar方法：测试可过滤表的SELECT *查询
    sql("filterable-model", "select * from EMPS").ok(); // 执行SQL查询，从可过滤模型的EMPS表中选择所有列
  } // testFilterableSelectStar方法结束：验证可过滤表的SELECT *查询能够正常工作

  /** Filter that can be fully handled by CsvFilterableTable. */ // testFilterableWhere方法：测试可以完全由CsvFilterableTable处理的过滤条件
  @Test void testFilterableWhere() { // 测试方法声明
    final String sql = // SQL查询字符串
        "select empno, gender, name from EMPS where name = 'John'"; // 查询name为John的员工
    sql("filterable-model", sql) // 使用可过滤模型执行SQL
        .returns("EMPNO=110; GENDER=M; NAME=John").ok(); // 验证返回结果为John的记录
  } // testFilterableWhere方法结束：验证简单的等值过滤能够正确下推到CsvFilterableTable

  /** Filter that can be partly handled by CsvFilterableTable. */ // testFilterableWhere2方法：测试可以部分由CsvFilterableTable处理的过滤条件
  @Test void testFilterableWhere2() { // 测试方法声明
    final String sql = "select empno, gender, name from EMPS\n" // SQL查询字符串
        + " where gender = 'F' and empno > 125"; // 查询gender为F且empno大于125的员工
    sql("filterable-model", sql) // 使用可过滤模型执行SQL
        .returns("EMPNO=130; GENDER=F; NAME=Alice").ok(); // 验证返回结果为Alice的记录
  } // testFilterableWhere2方法结束：验证组合过滤条件能够正确处理

  /** Filter that can be slightly handled by CsvFilterableTable. */ // testFilterableWhere3方法：测试只能轻微由CsvFilterableTable处理的过滤条件
  @Test void testFilterableWhere3() { // 测试方法声明
    final String sql = "select empno, gender, name from EMPS\n" // SQL查询字符串
            + " where gender <> 'M' and empno > 125"; // 查询gender不等于M且empno大于125的员工
    sql("filterable-model", sql) // 使用可过滤模型执行SQL
        .returns("EMPNO=130; GENDER=F; NAME=Alice") // 验证返回结果为Alice的记录
        .ok(); // 验证测试通过
  } // testFilterableWhere3方法结束：验证不等于操作符的过滤条件能够正确处理

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2272">[CALCITE-2272]
   * Incorrect result for {@code name like '%E%' and city not like '%W%'}</a>.
   */ // testFilterableWhereWithNot1方法：测试带有NOT LIKE的过滤条件，修复CALCITE-2272问题
  @Test void testFilterableWhereWithNot1() { // 测试方法声明
    sql("filterable-model", // 使用可过滤模型
        "select name, empno from EMPS " // SQL查询字符串
            + "where name like '%E%' and city not like '%W%' ") // 查询name包含E且city不包含W的员工
        .returns("NAME=Eric; EMPNO=110") // 验证返回结果为Eric的记录
        .ok(); // 验证测试通过
  } // testFilterableWhereWithNot1方法结束：验证NOT LIKE操作符能够正确处理

  /** Similar to {@link #testFilterableWhereWithNot1()};
   * But use the same column. */ // testFilterableWhereWithNot2方法：类似于testFilterableWhereWithNot1，但在同一列上使用LIKE和NOT LIKE
  @Test void testFilterableWhereWithNot2() { // 测试方法声明
    sql("filterable-model", // 使用可过滤模型
        "select name, empno from EMPS " // SQL查询字符串
            + "where name like '%i%' and name not like '%W%' ") // 查询name包含i且不包含W的员工
        .returns("NAME=Eric; EMPNO=110", // 验证返回第一条结果为Eric的记录
            "NAME=Alice; EMPNO=130") // 验证返回第二条结果为Alice的记录
        .ok(); // 验证测试通过
  } // testFilterableWhereWithNot2方法结束：验证同一列上的LIKE和NOT LIKE组合能够正确处理

  @Test void testJson() { // testJson方法：测试JSON类型数据的查询
    final String sql = "select * from archers\n"; // SQL查询：从archers表中选择所有列
    final String[] lines = { // 预期的结果行数组
        "id=19990101; dow=Friday; longDate=New Years Day; title=Tractor trouble.; " // 第一条记录
            + "characters=[Alice, Bob, Xavier]; script=Julian Hyde; summary=; " // 包含数组类型的字段
            + "lines=[Bob's tractor got stuck in a field., " // lines字段也是数组类型
            + "Alice and Xavier hatch a plan to surprise Charlie.]", // 数组中的第二个元素
        "id=19990103; dow=Sunday; longDate=Sunday 3rd January; " // 第二条记录
            + "title=Charlie's surprise.; characters=[Alice, Zebedee, Charlie, Xavier]; " // 包含多个角色
            + "script=William Shakespeare; summary=; " // 脚本作者
            + "lines=[Charlie is very surprised by Alice and Xavier's surprise plan.]", // lines字段内容
    };
    sql("bug", sql) // 使用bug模型执行SQL
        .returns(lines) // 验证返回结果与预期一致
        .ok(); // 验证测试通过
  } // testJson方法结束：验证JSON类型数据能够正确解析和查询

  private Fluent sql(String model, String sql) { // sql方法：创建Fluent对象以执行SQL测试，参数model为模型名称，参数sql为SQL语句
    return new Fluent(model, sql, this::output); // 返回Fluent对象，使用output方法作为结果处理器
  } // sql方法结束：提供流式API来执行和验证SQL查询

  /** Returns a function that checks the contents of a result set against an
   * expected string. */ // expect方法：返回一个函数，用于检查结果集内容是否符合预期
  private static Consumer<ResultSet> expect(final String... expected) { // 参数expected为预期的结果行数组
    return resultSet -> { // 返回一个Consumer函数，接收ResultSet参数
      try {
        final List<String> lines = new ArrayList<>(); // 创建列表存储实际结果
        CsvTest.collect(lines, resultSet); // 收集结果集中的所有行
        assertThat(lines, isListOf(expected)); // 验证实际结果与预期结果一致
      } catch (SQLException e) { // 捕获SQL异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    };
  } // expect方法结束：返回一个验证结果集的Consumer函数

  /** Returns a function that checks the contents of a result set against an
   * expected string. */ // expectUnordered方法：返回一个函数，用于无序检查结果集内容是否符合预期
  private static Consumer<ResultSet> expectUnordered(String... expected) { // 参数expected为预期的结果行数组
    final List<String> expectedLines = // 创建预期行的排序列表
        Ordering.natural().immutableSortedCopy(Arrays.asList(expected)); // 对预期行进行自然排序
    return resultSet -> { // 返回一个Consumer函数，接收ResultSet参数
      try {
        final List<String> lines = new ArrayList<>(); // 创建列表存储实际结果
        CsvTest.collect(lines, resultSet); // 收集结果集中的所有行
        Collections.sort(lines); // 对实际结果进行排序
        assertThat(lines, is(expectedLines)); // 验证排序后的实际结果与预期结果一致
      } catch (SQLException e) { // 捕获SQL异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    };
  } // expectUnordered方法结束：返回一个无序验证结果集的Consumer函数

  private void checkSql(String sql, String model, Consumer<ResultSet> fn) // checkSql方法：执行SQL查询并使用提供的函数验证结果，参数sql为SQL语句，参数model为模型名称，参数fn为结果验证函数
      throws SQLException { // 可能抛出SQL异常
    Connection connection = null; // 初始化连接对象为null
    Statement statement = null; // 初始化语句对象为null
    try {
      Properties info = new Properties(); // 创建属性对象
      info.put("model", jsonPath(model)); // 设置model属性为JSON模型文件路径
      connection = DriverManager.getConnection("jdbc:calcite:", info); // 获取Calcite数据库连接
      statement = connection.createStatement(); // 创建SQL语句对象
      final ResultSet resultSet = // 执行SQL查询
          statement.executeQuery( // 执行查询
              sql); // SQL语句
      fn.accept(resultSet); // 调用验证函数处理结果集
    } finally {
      close(connection, statement); // 确保关闭连接和语句
    }
  } // checkSql方法结束：执行SQL查询并验证结果，确保资源被正确释放

  private String jsonPath(String model) { // jsonPath方法：获取JSON模型文件的绝对路径，参数model为模型名称
    return resourcePath(model + ".json"); // 调用resourcePath方法，添加.json后缀
  } // jsonPath方法结束：返回JSON模型文件的绝对路径

  private String resourcePath(String path) { // resourcePath方法：获取资源文件的绝对路径，参数path为资源路径
    final URL url = requireNonNull(CsvTest.class.getResource("/" + path)); // 从classpath获取资源URL，确保不为null
    return Sources.of(url).file().getAbsolutePath(); // 将URL转换为绝对路径
  } // resourcePath方法结束：返回资源文件的绝对路径

  private static void collect(List<String> result, ResultSet resultSet) // collect方法：收集结果集中的所有行到列表中，参数result为结果列表，参数resultSet为结果集
      throws SQLException { // 可能抛出SQL异常
    final StringBuilder buf = new StringBuilder(); // 创建字符串构建器
    while (resultSet.next()) { // 遍历结果集的每一行
      buf.setLength(0); // 清空构建器
      int n = resultSet.getMetaData().getColumnCount(); // 获取列数
      String sep = ""; // 初始化分隔符为空字符串
      for (int i = 1; i <= n; i++) { // 遍历每一列
        buf.append(sep) // 添加分隔符
            .append(resultSet.getMetaData().getColumnLabel(i)) // 添加列名
            .append("=") // 添加等号
            .append(resultSet.getString(i)); // 添加列值
        sep = "; "; // 更新分隔符为分号加空格
      }
      result.add(Util.toLinux(buf.toString())); // 将行转换为Linux换行符格式并添加到结果列表
    }
  } // collect方法结束：收集结果集中的所有行到列表中，每行格式为"列名1=值1; 列名2=值2; ..."

  private void output(ResultSet resultSet, PrintStream out) // output方法：将结果集输出到打印流，参数resultSet为结果集，参数out为输出流
      throws SQLException { // 可能抛出SQL异常
    final ResultSetMetaData metaData = resultSet.getMetaData(); // 获取结果集元数据
    final int columnCount = metaData.getColumnCount(); // 获取列数
    while (resultSet.next()) { // 遍历结果集的每一行
      for (int i = 1;; i++) { // 遍历每一列
        out.print(resultSet.getString(i)); // 输出列值
        if (i < columnCount) { // 如果不是最后一列
          out.print(", "); // 输出逗号和空格
        } else { // 如果是最后一列
          out.println(); // 输出换行符
          break; // 退出列循环
        }
      }
    }
  } // output方法结束：将结果集格式化输出到打印流，每行用逗号分隔列值

  @Test void testJoinOnString() { // testJoinOnString方法：测试基于字符串列的JOIN操作
    final String sql = "select * from emps\n" // SQL查询：从emps表
        + "join depts on emps.name = depts.name"; // 与depts表连接，连接条件为name相等
    sql("smart", sql).ok(); // 执行SQL并验证结果
  } // testJoinOnString方法结束：验证字符串列的JOIN操作能够正常工作

  @Test void testWackyColumns() { // testWackyColumns方法：测试具有奇怪列名的表
    final String sql = "select * from wacky_column_names where false"; // SQL查询：从不返回任何结果的查询
    sql("bug", sql).returns().ok(); // 执行SQL并验证结果

    final String sql2 = "select \"joined at\", \"naME\"\n" // SQL查询：选择带空格和大小写混合的列名
        + "from wacky_column_names\n" // 从wacky_column_names表
        + "where \"2gender\" = 'F'"; // 过滤条件：2gender列等于F
    sql("bug", sql2) // 使用bug模型执行SQL
        .returns("joined at=2005-09-07; naME=Wilma", // 验证第一条结果
            "joined at=2007-01-01; naME=Alice") // 验证第二条结果
        .ok(); // 验证测试通过
  } // testWackyColumns方法结束：验证奇怪列名能够正确处理

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1754">[CALCITE-1754]
   * In Csv adapter, convert DATE and TIME values to int, and TIMESTAMP values
   * to long</a>. */ // testGroupByTimestampAdd方法：测试在GROUP BY中使用timestampadd函数，修复CALCITE-1754问题
  @Test void testGroupByTimestampAdd() { // 测试方法声明
    final String sql = "select count(*) as c,\n" // SQL查询：计算数量
        + "  {fn timestampadd(SQL_TSI_DAY, 1, JOINEDAT) } as t\n" // 使用timestampadd函数给JOINEDAT加1天
        + "from EMPS group by {fn timestampadd(SQL_TSI_DAY, 1, JOINEDAT ) } "; // 按加1天后的日期分组
    sql("model", sql) // 使用model模型执行SQL
        .returnsUnordered("C=1; T=1996-08-04", // 验证第一条结果
            "C=1; T=2002-05-04", // 验证第二条结果
            "C=1; T=2005-09-08", // 验证第三条结果
            "C=1; T=2007-01-02", // 验证第四条结果
            "C=1; T=2001-01-02") // 验证第五条结果
        .ok(); // 验证测试通过

    final String sql2 = "select count(*) as c,\n" // 第二个SQL查询：计算数量
        + "  {fn timestampadd(SQL_TSI_MONTH, 1, JOINEDAT) } as t\n" // 使用timestampadd函数给JOINEDAT加1个月
        + "from EMPS group by {fn timestampadd(SQL_TSI_MONTH, 1, JOINEDAT ) } "; // 按加1个月后的日期分组
    sql("model", sql2) // 使用model模型执行SQL
        .returnsUnordered("C=1; T=2002-06-03", // 验证第一条结果
            "C=1; T=2005-10-07", // 验证第二条结果
            "C=1; T=2007-02-01", // 验证第三条结果
            "C=1; T=2001-02-01", // 验证第四条结果
            "C=1; T=1996-09-03") // 验证第五条结果
        .ok(); // 验证测试通过
  } // testGroupByTimestampAdd方法结束：验证timestampadd函数在GROUP BY中能够正常工作

  @Test void testUnionGroupByWithoutGroupKey() { // testUnionGroupByWithoutGroupKey方法：测试UNION中的GROUP BY
    final String sql = "select count(*) as c1 from EMPS group by NAME\n" // SQL查询：按NAME分组并计数
        + "union\n" // 使用UNION合并结果
        + "select count(*) as c1 from EMPS group by NAME"; // 第二个查询：按NAME分组并计数
    sql("model", sql).ok(); // 执行SQL并验证结果
  } // testUnionGroupByWithoutGroupKey方法结束：验证UNION中的GROUP BY能够正常工作

  @Test void testBoolean() { // testBoolean方法：测试布尔类型的查询
    sql("smart", "select empno, slacker from emps where slacker") // SQL查询：查询slacker为true的员工
        .returns("EMPNO=100; SLACKER=true").ok(); // 验证返回结果
  } // testBoolean方法结束：验证布尔类型能够正确处理

  @Test void testReadme() { // testReadme方法：测试README文档中的示例查询
    final String sql = "SELECT d.name, COUNT(*) cnt" // SQL查询：选择部门名称和员工数量
        + " FROM emps AS e" // 从员工表，别名为e
        + " JOIN depts AS d ON e.deptno = d.deptno" // 与部门表连接，连接条件为部门号相等
        + " GROUP BY d.name"; // 按部门名称分组
    sql("smart", sql) // 使用smart模型执行SQL
        .returns("NAME=Sales; CNT=1", "NAME=Marketing; CNT=2").ok(); // 验证返回结果
  } // testReadme方法结束：验证README中的示例查询能够正常工作

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-824">[CALCITE-824]
   * Type inference when converting IN clause to semijoin</a>. */ // testInToSemiJoinWithCast方法：测试IN子句转换为半连接时的类型推断，修复CALCITE-824问题
  @Test void testInToSemiJoinWithCast() { // 测试方法声明
    // Note that the IN list needs at least 20 values to trigger the rewrite
    // to a semijoin. Try it both ways. // 注意：IN列表需要至少20个值才能触发重写为半连接
    final String sql = "SELECT e.name\n" // SQL查询：选择员工姓名
        + "FROM emps AS e\n" // 从员工表，别名为e
        + "WHERE cast(e.empno as bigint) in "; // 过滤条件：empno转换为bigint后在IN列表中
    final int threshold = SqlToRelConverter.DEFAULT_IN_SUB_QUERY_THRESHOLD; // 获取IN子查询重写的阈值
    sql("smart", sql + range(130, threshold - 5)) // 测试IN列表值小于阈值的情况
        .returns("NAME=Alice").ok(); // 验证返回结果
    sql("smart", sql + range(130, threshold)) // 测试IN列表值等于阈值的情况
        .returns("NAME=Alice").ok(); // 验证返回结果
    sql("smart", sql + range(130, threshold + 1000)) // 测试IN列表值大于阈值的情况
        .returns("NAME=Alice").ok(); // 验证返回结果
  } // testInToSemiJoinWithCast方法结束：验证IN子句转换为半连接时的类型推断正确

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1051">[CALCITE-1051]
   * Underflow exception due to scaling IN clause literals</a>. */ // testInToSemiJoinWithoutCast方法：测试IN子句转换时的下溢异常，修复CALCITE-1051问题
  @Test void testInToSemiJoinWithoutCast() { // 测试方法声明
    final String sql = "SELECT e.name\n" // SQL查询：选择员工姓名
        + "FROM emps AS e\n" // 从员工表，别名为e
        + "WHERE e.empno in " // 过滤条件：empno在IN列表中
        + range(130, SqlToRelConverter.DEFAULT_IN_SUB_QUERY_THRESHOLD); // 生成IN列表
    sql("smart", sql).returns("NAME=Alice").ok(); // 执行SQL并验证返回结果
  } // testInToSemiJoinWithoutCast方法结束：验证IN子句转换时不会出现下溢异常

  private String range(int first, int count) { // range方法：生成IN子句的值列表，参数first为起始值，参数count为数量
    final StringBuilder sb = new StringBuilder(); // 创建字符串构建器
    for (int i = 0; i < count; i++) { // 循环count次
      sb.append(i == 0 ? "(" : ", ").append(first + i); // 添加值，第一个值前加左括号，其他值前加逗号
    }
    return sb.append(')').toString(); // 添加右括号并返回字符串
  } // range方法结束：生成格式为"(first, first+1, ..., first+count-1)"的字符串

  @Test void testDateType() throws SQLException { // testDateType方法：测试日期类型的处理
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性

    try (Connection connection = // 使用try-with-resources获取连接
        DriverManager.getConnection("jdbc:calcite:", info)) { // 获取Calcite数据库连接
      ResultSet res = // 获取列元数据
          connection.getMetaData().getColumns(null, null, // 查询列元数据
              "DATE", "JOINEDAT"); // 查询DATE表的JOINEDAT列
      res.next(); // 移动到第一行
      assertThat(java.sql.Types.DATE, is(res.getInt("DATA_TYPE"))); // 验证数据类型为DATE

      res = // 获取列元数据
          connection.getMetaData().getColumns(null, null, // 查询列元数据
              "DATE", "JOINTIME"); // 查询DATE表的JOINTIME列
      res.next(); // 移动到第一行
      assertThat(java.sql.Types.TIME, is(res.getInt("DATA_TYPE"))); // 验证数据类型为TIME

      res = // 获取列元数据
          connection.getMetaData().getColumns(null, null, // 查询列元数据
              "DATE", "JOINTIMES"); // 查询DATE表的JOINTIMES列
      res.next(); // 移动到第一行
      assertThat(java.sql.Types.TIMESTAMP, is(res.getInt("DATA_TYPE"))); // 验证数据类型为TIMESTAMP

      Statement statement = connection.createStatement(); // 创建SQL语句对象
      final String sql = "select \"JOINEDAT\", \"JOINTIME\", \"JOINTIMES\" " // SQL查询
          + "from \"DATE\" where EMPNO = 100"; // 查询EMPNO为100的记录
      ResultSet resultSet = statement.executeQuery(sql); // 执行查询
      resultSet.next(); // 移动到第一行

      // date // 验证日期类型
      assertThat(resultSet.getDate(1).getClass(), is(java.sql.Date.class)); // 验证返回类型为java.sql.Date
      assertThat(resultSet.getDate(1), is(java.sql.Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 验证时间类型
      assertThat(resultSet.getTime(2).getClass(), is(java.sql.Time.class)); // 验证返回类型为java.sql.Time
      assertThat(resultSet.getTime(2), is(java.sql.Time.valueOf("00:01:02"))); // 验证时间值

      // timestamp // 验证时间戳类型
      assertThat(resultSet.getTimestamp(3).getClass(), is(Timestamp.class)); // 验证返回类型为Timestamp
      assertThat(resultSet.getTimestamp(3), // 验证时间戳值
          is(Timestamp.valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    }
  } // testDateType方法结束：验证DATE、TIME和TIMESTAMP类型能够正确处理

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1072">[CALCITE-1072]
   * CSV adapter incorrectly parses TIMESTAMP values after noon</a>. */ // testDateType2方法：测试中午之后的TIMESTAMP值解析，修复CALCITE-1072问题
  @Test void testDateType2() throws SQLException { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性

    try (Connection connection = // 使用try-with-resources获取连接
        DriverManager.getConnection("jdbc:calcite:", info)) { // 获取Calcite数据库连接
      Statement statement = connection.createStatement(); // 创建SQL语句对象
      final String sql = "select * from \"DATE\"\n" // SQL查询
          + "where EMPNO >= 140 and EMPNO < 200"; // 过滤EMPNO在140到200之间的记录
      ResultSet resultSet = statement.executeQuery(sql); // 执行查询
      int n = 0; // 记录数量计数器
      while (resultSet.next()) { // 遍历结果集
        ++n; // 增加计数
        final int empId = resultSet.getInt(1); // 获取EMPNO
        final String date = resultSet.getString(2); // 获取日期
        final String time = resultSet.getString(3); // 获取时间
        final String timestamp = resultSet.getString(4); // 获取时间戳
        assertThat(date, is("2015-12-31")); // 验证日期值
        switch (empId) { // 根据EMPID验证时间和时间戳
        case 140: // EMPID为140的情况
          assertThat(time, is("07:15:56")); // 验证时间值
          assertThat(timestamp, is("2015-12-31 07:15:56")); // 验证时间戳值
          break;
        case 150: // EMPID为150的情况
          assertThat(time, is("13:31:21")); // 验证时间值（中午之后）
          assertThat(timestamp, is("2015-12-31 13:31:21")); // 验证时间戳值（中午之后）
          break;
        default: // 其他情况
          throw new AssertionError(); // 抛出断言错误
        }
      }
      assertThat(n, is(2)); // 验证有2条记录
      resultSet.close(); // 关闭结果集
      statement.close(); // 关闭语句
    }
  } // testDateType2方法结束：验证中午之后的TIMESTAMP值能够正确解析

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1673">[CALCITE-1673]
   * Query with ORDER BY or GROUP BY on TIMESTAMP column throws
   * CompileException</a>. */ // testTimestampGroupBy方法：测试TIMESTAMP列的GROUP BY，修复CALCITE-1673问题
  @Test void testTimestampGroupBy() throws SQLException { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性
    // Use LIMIT to ensure that results are deterministic without ORDER BY // 使用LIMIT确保结果确定性
    final String sql = "select \"EMPNO\", \"JOINTIMES\"\n" // SQL查询
        + "from (select * from \"DATE\" limit 1)\n" // 从DATE表选择一条记录
        + "group by \"EMPNO\",\"JOINTIMES\""; // 按EMPNO和JOINTIMES分组
    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:", info); // 获取Calcite数据库连接
         Statement statement = connection.createStatement(); // 创建SQL语句对象
         ResultSet resultSet = statement.executeQuery(sql)) { // 执行查询
      assertThat(resultSet.next(), is(true)); // 验证有结果返回
      final Timestamp timestamp = resultSet.getTimestamp(2); // 获取JOINTIMES列的值
      assertThat(timestamp, isA(Timestamp.class)); // 验证类型为Timestamp
      // Note: This logic is time zone specific, but the same time zone is
      // used in the CSV adapter and this test, so they should cancel out. // 注意：此逻辑与时区相关，但CSV适配器和测试使用相同的时区，所以应该相互抵消
      assertThat(timestamp, is(valueOf("1996-08-03 00:01:02.0"))); // 验证时间戳值
    }
  } // testTimestampGroupBy方法结束：验证TIMESTAMP列的GROUP BY能够正常工作

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3772">[CALCITE-3772]
   * RelFieldTrimmer incorrectly trims fields when the query includes correlated-subquery</a>.
   */ // testCorrelatedSubquery方法：测试相关子查询的字段修剪问题，修复CALCITE-3772问题
  @Test public void testCorrelatedSubquery() throws SQLException { // 测试方法声明
    final String sql = "SELECT a, (SELECT count(*) FROM NUMBERS AS x WHERE x.b<NUMBERS.b)\n" // SQL查询：选择a列和相关子查询的结果
        + "FROM NUMBERS where e>100 order by a"; // 从NUMBERS表查询e大于100的记录，按a排序
    sql("bug", sql).returns("A=104; EXPR$1=0", // 验证第一条结果
        "A=107; EXPR$1=1", // 验证第二条结果
        "A=111; EXPR$1=2", // 验证第三条结果
        "A=115; EXPR$1=3", // 验证第四条结果
        "A=121; EXPR$1=4").ok(); // 验证第五条结果
  } // testCorrelatedSubquery方法结束：验证相关子查询能够正确处理字段修剪

  /** As {@link #testTimestampGroupBy()} but with ORDER BY. */ // testTimestampOrderBy方法：类似于testTimestampGroupBy，但使用ORDER BY
  @Test void testTimestampOrderBy() throws SQLException { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性
    final String sql = "select \"EMPNO\",\"JOINTIMES\" from \"DATE\"\n" // SQL查询
        + "order by \"JOINTIMES\""; // 按JOINTIMES排序
    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:", info); // 获取Calcite数据库连接
         Statement statement = connection.createStatement(); // 创建SQL语句对象
         ResultSet resultSet = statement.executeQuery(sql)) { // 执行查询
      assertThat(resultSet.next(), is(true)); // 验证有结果返回
      final Timestamp timestamp = resultSet.getTimestamp(2); // 获取JOINTIMES列的值
      assertThat(timestamp, is(valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    }
  } // testTimestampOrderBy方法结束：验证TIMESTAMP列的ORDER BY能够正常工作

  /** As {@link #testTimestampGroupBy()} but with ORDER BY as well as GROUP
   * BY. */ // testTimestampGroupByAndOrderBy方法：类似于testTimestampGroupBy，但同时使用GROUP BY和ORDER BY
  @Test void testTimestampGroupByAndOrderBy() throws SQLException { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性
    final String sql = "select \"EMPNO\", \"JOINTIMES\" from \"DATE\"\n" // SQL查询
        + "group by \"EMPNO\",\"JOINTIMES\" order by \"JOINTIMES\""; // 按EMPNO和JOINTIMES分组，并按JOINTIMES排序
    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:", info); // 获取Calcite数据库连接
         Statement statement = connection.createStatement(); // 创建SQL语句对象
         ResultSet resultSet = statement.executeQuery(sql)) { // 执行查询
      assertThat(resultSet.next(), is(true)); // 验证有结果返回
      final Timestamp timestamp = resultSet.getTimestamp(2); // 获取JOINTIMES列的值
      assertThat(timestamp, is(valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    }
  } // testTimestampGroupByAndOrderBy方法结束：验证TIMESTAMP列的GROUP BY和ORDER BY组合能够正常工作

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1031">[CALCITE-1031]
   * In prepared statement, CsvScannableTable.scan is called twice</a>. To see
   * the bug, place a breakpoint in CsvScannableTable.scan, and note that it is
   * called twice. It should only be called once. */ // testPrepared方法：测试预编译语句中CsvScannableTable.scan被调用两次的问题，修复CALCITE-1031问题
  @Test void testPrepared() throws SQLException { // 测试方法声明
    final Properties properties = new Properties(); // 创建属性对象
    properties.setProperty("caseSensitive", "true"); // 设置大小写敏感
    try (Connection connection = // 使用try-with-resources获取连接
        DriverManager.getConnection("jdbc:calcite:", properties)) { // 获取Calcite数据库连接
      final CalciteConnection calciteConnection = // 解包为CalciteConnection
          connection.unwrap(CalciteConnection.class); // 获取CalciteConnection对象

      final Schema schema = // 创建CSV Schema
          CsvSchemaFactory.INSTANCE // 使用CSV Schema工厂
              .create(calciteConnection.getRootSchema(), "x", // 在根Schema下创建名为x的Schema
                  ImmutableMap.of("directory", // 创建不可变Map
                      resourcePath("sales"), "flavor", "scannable")); // 设置目录和flavor参数
      calciteConnection.getRootSchema().add("TEST", schema); // 将Schema添加到根Schema
      final String sql = "select * from \"TEST\".\"DEPTS\" where \"NAME\" = ?"; // SQL查询：使用参数占位符
      final PreparedStatement statement2 = // 创建预编译语句
          calciteConnection.prepareStatement(sql); // 准备SQL语句

      statement2.setString(1, "Sales"); // 设置参数值为Sales
      final ResultSet resultSet1 = statement2.executeQuery(); // 执行查询
      Consumer<ResultSet> expect = expect("DEPTNO=10; NAME=Sales"); // 创建期望结果
      expect.accept(resultSet1); // 验证结果
    }
  } // testPrepared方法结束：验证预编译语句中scan方法只被调用一次

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1054">[CALCITE-1054]
   * NPE caused by wrong code generation for Timestamp fields</a>. */ // testFilterOnNullableTimestamp方法：测试可空Timestamp字段的过滤，修复CALCITE-1054问题
  @Test void testFilterOnNullableTimestamp() throws Exception { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性

    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 获取Calcite数据库连接
      final Statement statement = connection.createStatement(); // 创建SQL语句对象

      // date // 测试日期类型过滤
      final String sql1 = "select JOINEDAT from \"DATE\"\n" // SQL查询：选择JOINEDAT列
          + "where JOINEDAT < {d '2000-01-01'}\n" // 过滤条件：JOINEDAT小于2000-01-01
          + "or JOINEDAT >= {d '2017-01-01'}"; // 或JOINEDAT大于等于2017-01-01
      final ResultSet joinedAt = statement.executeQuery(sql1); // 执行查询
      assertThat(joinedAt.next(), is(true)); // 验证有结果返回
      assertThat(joinedAt.getDate(1), is(java.sql.Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 测试时间类型过滤
      final String sql2 = "select JOINTIME from \"DATE\"\n" // SQL查询：选择JOINTIME列
          + "where JOINTIME >= {t '07:00:00'}\n" // 过滤条件：JOINTIME大于等于07:00:00
          + "and JOINTIME < {t '08:00:00'}"; // 且JOINTIME小于08:00:00
      final ResultSet joinTime = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTime.next(), is(true)); // 验证有结果返回
      assertThat(joinTime.getTime(1), is(java.sql.Time.valueOf("07:15:56"))); // 验证时间值

      // timestamp // 测试时间戳类型过滤
      final String sql3 = "select JOINTIMES,\n" // SQL查询：选择JOINTIMES列和计算后的值
          + "  {fn timestampadd(SQL_TSI_DAY, 1, JOINTIMES)}\n" // 使用timestampadd函数加1天
          + "from \"DATE\"\n" // 从DATE表
          + "where (JOINTIMES >= {ts '2003-01-01 00:00:00'}\n" // 过滤条件：JOINTIMES在2003-01-01到2006-01-01之间
          + "and JOINTIMES < {ts '2006-01-01 00:00:00'})\n" // 或在2003-01-01到2007-01-01之间
          + "or (JOINTIMES >= {ts '2003-01-01 00:00:00'}\n" // 第二个条件
          + "and JOINTIMES < {ts '2007-01-01 00:00:00'})"; // 第二个条件的结束
      final ResultSet joinTimes = statement.executeQuery(sql3); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有结果返回
      assertThat(joinTimes.getTimestamp(1), // 验证第一列的时间戳值
          is(java.sql.Timestamp.valueOf("2005-09-07 00:00:00"))); // 验证时间戳值
      assertThat(joinTimes.getTimestamp(2), // 验证第二列的时间戳值
          is(java.sql.Timestamp.valueOf("2005-09-08 00:00:00"))); // 验证时间戳值

      final String sql4 = "select JOINTIMES, extract(year from JOINTIMES)\n" // SQL查询：选择JOINTIMES和提取的年份
          + "from \"DATE\""; // 从DATE表
      final ResultSet joinTimes2 = statement.executeQuery(sql4); // 执行查询
      assertThat(joinTimes2.next(), is(true)); // 验证有结果返回
      assertThat(joinTimes2.getTimestamp(1), // 验证第一列的时间戳值
          is(java.sql.Timestamp.valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    }
  } // testFilterOnNullableTimestamp方法结束：验证可空Timestamp字段的过滤能够正常工作

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1118">[CALCITE-1118]
   * NullPointerException in EXTRACT with WHERE ... IN clause if field has null
   * value</a>. */ // testFilterOnNullableTimestamp2方法：测试EXTRACT函数与IN子句组合时的空值问题，修复CALCITE-1118问题
  @Test void testFilterOnNullableTimestamp2() throws Exception { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性

    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 获取Calcite数据库连接
      final Statement statement = connection.createStatement(); // 创建SQL语句对象
      final String sql1 = "select extract(year from JOINTIMES)\n" // SQL查询：提取年份
          + "from \"DATE\"\n" // 从DATE表
          + "where extract(year from JOINTIMES) in (2006, 2007)"; // 过滤条件：年份在2006或2007中
      final ResultSet joinTimes = statement.executeQuery(sql1); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有结果返回
      assertThat(joinTimes.getInt(1), is(2007)); // 验证年份值为2007

      final String sql2 = "select extract(year from JOINTIMES),\n" // SQL查询：提取年份并计数
          + "  count(0) from \"DATE\"\n" // 从DATE表
          + "where extract(year from JOINTIMES) between 2007 and 2016\n" // 过滤条件：年份在2007到2016之间
          + "group by extract(year from JOINTIMES)"; // 按年份分组
      final ResultSet joinTimes2 = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTimes2.next(), is(true)); // 验证有结果返回
      assertThat(joinTimes2.getInt(1), is(2007)); // 验证年份值为2007
      assertThat(joinTimes2.getLong(2), is(1L)); // 验证数量为1
      assertThat(joinTimes2.next(), is(true)); // 验证有更多结果
      assertThat(joinTimes2.getInt(1), is(2015)); // 验证年份值为2015
      assertThat(joinTimes2.getLong(2), is(2L)); // 验证数量为2
    }
  } // testFilterOnNullableTimestamp2方法结束：验证EXTRACT函数与IN子句组合时能够正确处理空值

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1427">[CALCITE-1427]
   * Code generation incorrect (does not compile) for DATE, TIME and TIMESTAMP
   * fields</a>. */ // testNonNullFilterOnDateType方法：测试日期类型的非空过滤，修复CALCITE-1427问题
  @Test void testNonNullFilterOnDateType() throws SQLException { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性

    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 获取Calcite数据库连接
      final Statement statement = connection.createStatement(); // 创建SQL语句对象

      // date // 测试日期类型的非空过滤
      final String sql1 = "select JOINEDAT from \"DATE\"\n" // SQL查询：选择JOINEDAT列
          + "where JOINEDAT is not null"; // 过滤条件：JOINEDAT不为null
      final ResultSet joinedAt = statement.executeQuery(sql1); // 执行查询
      assertThat(joinedAt.next(), is(true)); // 验证有结果返回
      assertThat(joinedAt.getDate(1).getClass(), equalTo(java.sql.Date.class)); // 验证返回类型
      assertThat(joinedAt.getDate(1), is(java.sql.Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 测试时间类型的非空过滤
      final String sql2 = "select JOINTIME from \"DATE\"\n" // SQL查询：选择JOINTIME列
          + "where JOINTIME is not null"; // 过滤条件：JOINTIME不为null
      final ResultSet joinTime = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTime.next(), is(true)); // 验证有结果返回
      assertThat(joinTime.getTime(1).getClass(), equalTo(java.sql.Time.class)); // 验证返回类型
      assertThat(joinTime.getTime(1), is(java.sql.Time.valueOf("00:01:02"))); // 验证时间值

      // timestamp // 测试时间戳类型的非空过滤
      final String sql3 = "select JOINTIMES from \"DATE\"\n" // SQL查询：选择JOINTIMES列
          + "where JOINTIMES is not null"; // 过滤条件：JOINTIMES不为null
      final ResultSet joinTimes = statement.executeQuery(sql3); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有结果返回
      assertThat(joinTimes.getTimestamp(1).getClass(), // 验证返回类型
          equalTo(java.sql.Timestamp.class)); // 验证返回类型
      assertThat(joinTimes.getTimestamp(1), // 验证时间戳值
          is(java.sql.Timestamp.valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    }
  } // testNonNullFilterOnDateType方法结束：验证日期类型的非空过滤能够正常工作

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1427">[CALCITE-1427]
   * Code generation incorrect (does not compile) for DATE, TIME and TIMESTAMP
   * fields</a>. */ // testGreaterThanFilterOnDateType方法：测试日期类型的大于过滤，修复CALCITE-1427问题
  @Test void testGreaterThanFilterOnDateType() throws SQLException { // 测试方法声明
    Properties info = new Properties(); // 创建属性对象
    info.put("model", jsonPath("bug")); // 设置model属性

    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 获取Calcite数据库连接
      final Statement statement = connection.createStatement(); // 创建SQL语句对象

      // date // 测试日期类型的大于过滤
      final String sql1 = "select JOINEDAT from \"DATE\"\n" // SQL查询：选择JOINEDAT列
          + "where JOINEDAT > {d '1990-01-01'}"; // 过滤条件：JOINEDAT大于1990-01-01
      final ResultSet joinedAt = statement.executeQuery(sql1); // 执行查询
      assertThat(joinedAt.next(), is(true)); // 验证有结果返回
      assertThat(joinedAt.getDate(1).getClass(), equalTo(java.sql.Date.class)); // 验证返回类型
      assertThat(joinedAt.getDate(1), is(java.sql.Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 测试时间类型的大于过滤
      final String sql2 = "select JOINTIME from \"DATE\"\n" // SQL查询：选择JOINTIME列
          + "where JOINTIME > {t '00:00:00'}"; // 过滤条件：JOINTIME大于00:00:00
      final ResultSet joinTime = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTime.next(), is(true)); // 验证有结果返回
      assertThat(joinTime.getTime(1).getClass(), equalTo(java.sql.Time.class)); // 验证返回类型
      assertThat(joinTime.getTime(1), is(java.sql.Time.valueOf("00:01:02"))); // 验证时间值

      // timestamp // 测试时间戳类型的大于过滤
      final String sql3 = "select JOINTIMES from \"DATE\"\n" // SQL查询：选择JOINTIMES列
          + "where JOINTIMES > {ts '1990-01-01 00:00:00'}"; // 过滤条件：JOINTIMES大于1990-01-01 00:00:00
      final ResultSet joinTimes = statement.executeQuery(sql3); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有结果返回
      assertThat(joinTimes.getTimestamp(1).getClass(), // 验证返回类型
          equalTo(java.sql.Timestamp.class)); // 验证返回类型
      assertThat(joinTimes.getTimestamp(1), // 验证时间戳值
          is(java.sql.Timestamp.valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    }
  } // testGreaterThanFilterOnDateType方法结束：验证日期类型的大于过滤能够正常工作

  @Disabled("CALCITE-1894: there's a bug in the test code, so it does not test what it should") // 禁用此测试，因为测试代码有bug
  @Test @Timeout(10) public void testCsvStream() throws Exception { // testCsvStream方法：测试CSV流式处理，超时时间为10秒
    final File file = File.createTempFile("stream", "csv"); // 创建临时CSV文件
    final String model = "{\n" // 创建内联JSON模型
        + "  version: '1.0',\n" // 模型版本
        + "  defaultSchema: 'STREAM',\n" // 默认Schema为STREAM
        + "  schemas: [\n" // Schema列表
        + "    {\n" // Schema定义
        + "      name: 'SS',\n" // Schema名称
        + "      tables: [\n" // 表列表
        + "        {\n" // 表定义
        + "          name: 'DEPTS',\n" // 表名称
        + "          type: 'custom',\n" // 表类型为自定义
        + "          factory: '" + CsvStreamTableFactory.class.getName() // 使用CSV流表工厂
        + "',\n" // 工厂类名
        + "          stream: {\n" // 流式配置
        + "            stream: true\n" // 启用流式处理
        + "          },\n" // 流式配置结束
        + "          operand: {\n" // 工厂参数
        + "            file: " + escapeString(file.getAbsolutePath()) + ",\n" // CSV文件路径
        + "            flavor: \"scannable\"\n" // flavor为scannable
        + "          }\n" // 工厂参数结束
        + "        }\n" // 表定义结束
        + "      ]\n" // 表列表结束
        + "    }\n" // Schema定义结束
        + "  ]\n" // Schema列表结束
        + "}"; // JSON结束
    final String[] strings = { // CSV数据行数组
        "DEPTNO:int,NAME:string", // 表头行
        "10,\"Sales\"", // 第一行数据
        "20,\"Marketing\"", // 第二行数据
        "30,\"Engineering\"" // 第三行数据
    };

    try (Connection connection = // 使用try-with-resources获取连接
             DriverManager.getConnection("jdbc:calcite:model=inline:" + model); // 获取Calcite数据库连接
         PrintWriter pw = Util.printWriter(file); // 创建打印写入器
         Worker<Void> worker = new Worker<>()) { // 创建工作线程
      final Thread thread = new Thread(worker); // 创建线程
      thread.start(); // 启动线程

      // Add some rows so that the table can deduce its row type. // 添加一些行以便表能够推断其行类型
      final Iterator<String> lines = Arrays.asList(strings).iterator(); // 创建行迭代器
      pw.println(lines.next()); // header // 写入表头
      pw.flush(); // 刷新缓冲区
      worker.queue.put(writeLine(pw, lines.next())); // first row // 写入第一行数据
      worker.queue.put(writeLine(pw, lines.next())); // second row // 写入第二行数据
      final CalciteConnection calciteConnection = // 解包为CalciteConnection
          connection.unwrap(CalciteConnection.class); // 获取CalciteConnection对象
      final String sql = "select stream * from \"SS\".\"DEPTS\""; // SQL查询：流式查询
      final PreparedStatement statement = // 创建预编译语句
          calciteConnection.prepareStatement(sql); // 准备SQL语句
      final ResultSet resultSet = statement.executeQuery(); // 执行查询
      int count = 0; // 记录数量计数器
      try {
        while (resultSet.next()) { // 遍历结果集
          ++count; // 增加计数
          if (lines.hasNext()) { // 如果还有更多行
            worker.queue.put(sleep(10)); // 睡眠10毫秒
            worker.queue.put(writeLine(pw, lines.next())); // 写入下一行
          } else { // 如果没有更多行
            worker.queue.put(cancel(statement)); // 取消语句
          }
        }
        fail("expected exception, got end of data"); // 应该抛出异常，但得到了数据结束
      } catch (SQLException e) { // 捕获SQL异常
        assertThat(e.getMessage(), is("Statement canceled")); // 验证异常消息
      }
      assertThat(count, anyOf(is(strings.length - 2), is(strings.length - 1))); // 验证记录数量
      assertThat(worker.e, nullValue()); // 验证工作线程没有异常
      assertThat(worker.v, nullValue()); // 验证工作线程没有返回值
    } finally {
      Util.discard(file.delete()); // 删除临时文件
    }
  } // testCsvStream方法结束：验证CSV流式处理能够正常工作

  /** Creates a command that appends a line to the CSV file. */ // writeLine方法：创建一个向CSV文件追加行的命令
  private Callable<Void> writeLine(final PrintWriter pw, final String line) { // 参数pw为打印写入器，参数line为要写入的行
    return () -> { // 返回一个Callable对象
      pw.println(line); // 写入行
      pw.flush(); // 刷新缓冲区
      return null; // 返回null
    };
  } // writeLine方法结束：返回一个向CSV文件写入行的Callable对象

  /** Creates a command that sleeps. */ // sleep方法：创建一个睡眠命令
  private Callable<Void> sleep(final long millis) { // 参数millis为睡眠毫秒数
    return () -> { // 返回一个Callable对象
      Thread.sleep(millis); // 睡眠指定毫秒数
      return null; // 返回null
    };
  } // sleep方法结束：返回一个睡眠指定时间的Callable对象

  /** Creates a command that cancels a statement. */ // cancel方法：创建一个取消语句的命令
  private Callable<Void> cancel(final Statement statement) { // 参数statement为要取消的语句对象
    return () -> { // 返回一个Callable对象
      statement.cancel(); // 取消语句
      return null; // 返回null
    };
  } // cancel方法结束：返回一个取消语句的Callable对象

  private Void output(ResultSet resultSet) { // output方法：将结果集输出到标准输出，参数resultSet为结果集
    try {
      output(resultSet, System.out); // 调用重载方法，输出到System.out
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 重新抛出异常
    }
    return null; // 返回null
  } // output方法结束：将结果集输出到标准输出

  /** Receives commands on a queue and executes them on its own thread.
   * Call {@link #close} to terminate.
   *
   * @param <E> Result value of commands
   */ // Worker内部类：在队列上接收命令并在自己的线程上执行它们，调用close方法来终止
  private static class Worker<E> implements Runnable, AutoCloseable { // Worker类：实现了Runnable和AutoCloseable接口
    /** Queue of commands. */ // 命令队列
    final BlockingQueue<Callable<E>> queue = // 阻塞队列，用于存储命令
        new ArrayBlockingQueue<>(5); // 创建容量为5的数组阻塞队列

    /** Value returned by the most recent command. */ // 最近一个命令返回的值
    private @Nullable E v; // 泛型类型的返回值，可能为null

    /** Exception thrown by a command or queue wait. */ // 命令或队列等待抛出的异常
    private @Nullable Exception e; // 异常对象，可能为null

    /** The poison pill command. */ // 毒丸命令，用于终止工作线程
    final Callable<E> end = () -> null; // 返回null的Callable对象，作为终止信号

    public void run() { // run方法：工作线程的运行方法
      try {
        for (;;) { // 无限循环
          final Callable<E> c = queue.take(); // 从队列中取出命令，阻塞直到有命令可用
          if (c == end) { // 如果是毒丸命令
            return; // 退出循环，终止线程
          }
          this.v = c.call(); // 执行命令并保存返回值
        }
      } catch (Exception e) { // 捕获异常
        this.e = e; // 保存异常
      }
    } // run方法结束：持续从队列中取出命令并执行，直到收到毒丸命令

    public void close() { // close方法：关闭工作线程
      try {
        queue.put(end); // 将毒丸命令放入队列，通知线程终止
      } catch (InterruptedException e) { // 捕获中断异常
        // ignore // 忽略异常
      }
    } // close方法结束：通过发送毒丸命令来终止工作线程
  } // Worker内部类结束：提供线程安全的命令执行机制

  /** Fluent API to perform test actions. */ // Fluent内部类：提供流式API来执行测试操作
  private class Fluent { // Fluent类：内部类，提供流式测试API
    private final String model; // 模型名称
    private final String sql; // SQL语句
    private final Consumer<ResultSet> expect; // 结果验证函数

    Fluent(String model, String sql, Consumer<ResultSet> expect) { // 构造方法
      this.model = model; // 初始化模型名称
      this.sql = sql; // 初始化SQL语句
      this.expect = expect; // 初始化结果验证函数
    } // 构造方法结束：初始化Fluent对象

    /** Runs the test. */ // ok方法：运行测试
    Fluent ok() { // ok方法声明
      try {
        checkSql(sql, model, expect); // 执行SQL并验证结果
        return this; // 返回this以支持链式调用
      } catch (SQLException e) { // 捕获SQL异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    } // ok方法结束：执行测试并验证结果

    /** Assigns a function to call to test whether output is correct. */ // checking方法：分配一个函数来测试输出是否正确
    Fluent checking(Consumer<ResultSet> expect) { // checking方法声明，参数expect为结果验证函数
      return new Fluent(model, sql, expect); // 返回新的Fluent对象，使用新的验证函数
    } // checking方法结束：设置新的结果验证函数

    /** Sets the rows that are expected to be returned from the SQL query. */ // returns方法：设置SQL查询预期返回的行
    Fluent returns(String... expectedLines) { // returns方法声明，参数expectedLines为预期的行数组
      return checking(expect(expectedLines)); // 调用checking方法，使用expect函数
    } // returns方法结束：设置预期的结果行

    /** Sets the rows that are expected to be returned from the SQL query,
     * in no particular order. */ // returnsUnordered方法：设置SQL查询预期返回的行，顺序不限
    Fluent returnsUnordered(String... expectedLines) { // returnsUnordered方法声明，参数expectedLines为预期的行数组
      return checking(expectUnordered(expectedLines)); // 调用checking方法，使用expectUnordered函数
    } // returnsUnordered方法结束：设置无序预期的结果行
  } // Fluent内部类结束：提供流式API来执行和验证SQL测试
} // CsvTest类结束：CSV适配器的测试类，包含各种测试方法来验证CSV数据源的功能
