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
// Apache许可证声明，定义了代码的使用权限和限制条件
package org.apache.calcite.jdbc; // 定义包名，该类位于org.apache.calcite.jdbc包下

import org.apache.calcite.avatica.AvaticaConnection; // 导入Avatica连接类，Avatica是Calcite的远程JDBC驱动框架
import org.apache.calcite.avatica.Meta; // 导入元数据接口，用于获取数据库元数据信息
import org.apache.calcite.avatica.SqlType; // 导入SQL类型枚举，定义各种SQL数据类型
import org.apache.calcite.avatica.remote.LocalJsonService; // 导入本地JSON服务，用于本地JSON格式的服务调用
import org.apache.calcite.avatica.remote.LocalService; // 导入本地服务，用于本地服务实现
import org.apache.calcite.avatica.remote.Service; // 导入服务接口，定义远程服务的基本接口
import org.apache.calcite.avatica.server.AvaticaJsonHandler; // 导入Avatica JSON处理器，用于处理JSON请求和响应
import org.apache.calcite.avatica.server.HttpServer; // 导入HTTP服务器，用于提供远程JDBC服务
import org.apache.calcite.avatica.server.Main; // 导入Avatica服务器主类，用于启动服务器
import org.apache.calcite.config.CalciteSystemProperty; // 导入Calcite系统属性配置类
import org.apache.calcite.test.CalciteAssert; // 导入Calcite测试断言工具类，用于简化测试代码
import org.apache.calcite.test.JdbcFrontLinqBackTest; // 导入JDBC前端LINQ后端测试类
import org.apache.calcite.test.schemata.hr.Employee; // 导入员工实体类，用于测试数据
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法
import org.apache.calcite.util.Util; // 导入工具类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的字段
import org.junit.jupiter.api.AfterAll; // 导入JUnit5的AfterAll注解，用于在所有测试后执行
import org.junit.jupiter.api.BeforeAll; // 导入JUnit5的BeforeAll注解，用于在所有测试前执行
import org.junit.jupiter.api.Disabled; // 导入JUnit5的Disabled注解，用于禁用测试
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，标记测试方法
import org.junit.jupiter.api.parallel.Execution; // 导入JUnit5的并行执行注解
import org.junit.jupiter.api.parallel.ExecutionMode; // 导入JUnit5的并行执行模式枚举

import java.io.PrintWriter; // 导入打印写入器，用于输出文本
import java.io.StringWriter; // 导入字符串写入器，用于构建字符串
import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制计算
import java.math.BigInteger; // 导入BigInteger类，用于大整数计算
import java.net.URL; // 导入URL类，用于统一资源定位符
import java.nio.charset.StandardCharsets; // 导入标准字符集，用于字符编码
import java.sql.Array; // 导入SQL数组接口
import java.sql.Blob; // 导入SQL二进制大对象接口
import java.sql.Clob; // 导入SQL字符大对象接口
import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于获取数据库连接
import java.sql.NClob; // 导入SQL国家字符集大对象接口
import java.sql.PreparedStatement; // 导入预编译语句接口
import java.sql.Ref; // 导入SQL引用接口
import java.sql.ResultSet; // 导入结果集接口
import java.sql.ResultSetMetaData; // 导入结果集元数据接口
import java.sql.RowId; // 导入行ID接口
import java.sql.SQLException; // 导入SQL异常类
import java.sql.SQLXML; // 导入SQL XML接口
import java.sql.Statement; // 导入语句接口
import java.sql.Struct; // 导入SQL结构化类型接口
import java.sql.Time; // 导入SQL时间类
import java.sql.Timestamp; // 导入SQL时间戳类
import java.text.DateFormat; // 导入日期格式化类
import java.text.ParseException; // 导入解析异常类
import java.util.ArrayList; // 导入数组列表类
import java.util.Calendar; // 导入日历类
import java.util.Date; // 导入日期类
import java.util.HashMap; // 导入哈希映射类
import java.util.List; // 导入列表接口
import java.util.Locale; // 导入地区类
import java.util.Map; // 导入映射接口

import static org.apache.calcite.test.Matchers.primitiveArrayWithSize; // 导入匹配器，用于验证原始数组大小

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest匹配器，用于验证相等性
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器，用于验证布尔值
import static org.hamcrest.CoreMatchers.not; // 导入Hamcrest匹配器，用于验证非条件
import static org.hamcrest.CoreMatchers.notNullValue; // 导入Hamcrest匹配器，用于验证非空值
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest匹配器，用于验证空值
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言方法
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入JUnit5断言方法，用于验证异常抛出

import static java.util.Objects.requireNonNull; // 导入对象工具方法，用于验证非空

/**
 * Test for Calcite's remote JDBC driver.
 * // Calcite远程JDBC驱动测试类
 *
 * <p>Technically, the test is thread-safe, however Calcite/Avatica have
 * thread-safety issues; see
 * <a href="https://issues.apache.org/jira/browse/CALCITE-2853">
 * [CALCITE-2853] avatica.MetaImpl and calcite.jdbc.CalciteMetaImpl are not
 * thread-safe</a>.
 * // 技术上这个测试是线程安全的，但是Calcite/Avatica存在线程安全问题，参见CALCITE-2853
 *
 * <p>Under JDK 23 and higher, this test requires
 * "{@code -Djava.security.manager=allow}" command-line arguments due to
 * Avatica's use of deprecated methods in {@link javax.security.auth.Subject}.
 * These arguments are set automatically if you run via Gradle.
 * // 在JDK 23及更高版本中，由于Avatica使用了javax.security.auth.Subject中的已弃用方法，
 * // 该测试需要"-Djava.security.manager=allow"命令行参数。如果通过Gradle运行，这些参数会自动设置。
 */
@Execution(ExecutionMode.SAME_THREAD) // 指定测试在同一个线程中执行，避免并发问题
class CalciteRemoteDriverTest { // Calcite远程JDBC驱动测试类定义
  public static final String LJS = Factory2.class.getName(); // 定义Factory2类的全限定名常量，用于创建本地JSON服务

  private final PrintWriter out = // 定义打印写入器成员变量，用于输出调试信息
      CalciteSystemProperty.DEBUG.value() ? Util.printWriter(System.out) // 如果开启了DEBUG系统属性，则输出到标准输出
          : new PrintWriter(new StringWriter()); // 否则输出到字符串写入器（丢弃输出）

  private static @Nullable Connection localConnection; // 定义本地连接成员变量，可能为null，用于本地测试
  private static @Nullable HttpServer start; // 定义HTTP服务器成员变量，可能为null，用于提供远程JDBC服务

  @BeforeAll public static void beforeClass() throws Exception { // 在所有测试方法执行前调用，初始化测试环境
    localConnection = CalciteAssert.hr().connect(); // 创建本地连接，连接到hr测试数据库

    // Make sure we pick an ephemeral port for the server
    // 确保为服务器选择一个临时端口（动态分配的端口）
    final String[] args = {Factory.class.getName()}; // 创建参数数组，包含Factory类的全限定名
    start = Main.start(args, 0, AvaticaJsonHandler::new); // 启动Avatica HTTP服务器，使用JSON处理器，端口为0表示自动分配
  }

  protected static Connection getRemoteConnection() throws SQLException { // 获取远程连接的方法
    final int port = requireNonNull(start, "start").getPort(); // 获取HTTP服务器的端口号，如果start为null则抛出异常
    return DriverManager.getConnection( // 通过JDBC驱动管理器获取远程连接
        "jdbc:avatica:remote:url=http://localhost:" + port); // 使用Avatica远程JDBC URL连接到本地服务器
  }

  @AfterAll public static void afterClass() throws Exception { // 在所有测试方法执行后调用，清理测试环境
    if (localConnection != null) { // 如果本地连接不为null
      localConnection.close(); // 关闭本地连接
      localConnection = null; // 将本地连接设置为null
    }

    if (start != null) { // 如果HTTP服务器不为null
      start.stop(); // 停止HTTP服务器
    }
  }

  private static ResultSet getSchemas(Connection connection) { // 获取数据库模式信息的私有静态方法
    try { // 尝试执行
      return connection.getMetaData().getSchemas(); // 返回连接的元数据中的模式信息结果集
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
    }
  }

  private static ResultSet getCatalogs(Connection connection) { // 获取数据库目录信息的私有静态方法
    try { // 尝试执行
      return connection.getMetaData().getCatalogs(); // 返回连接的元数据中的目录信息结果集
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
    }
  }

  private static ResultSet getColumns(Connection connection) { // 获取数据库列信息的私有静态方法
    try { // 尝试执行
      return connection.getMetaData().getColumns(null, null, null, null); // 返回连接的元数据中的所有列信息结果集
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
    }
  }

  private static ResultSet getTables(Connection connection) { // 获取数据库表信息的私有静态方法
    try { // 尝试执行
      return connection.getMetaData().getTables(null, null, null, null); // 返回连接的元数据中的所有表信息结果集
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
    }
  }

  private static ResultSet getTypeInfo(Connection connection) { // 获取数据库类型信息的私有静态方法
    try { // 尝试执行
      return connection.getMetaData().getTypeInfo(); // 返回连接的元数据中的类型信息结果集
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
    }
  }

  private static ResultSet getTableTypes(Connection connection) { // 获取数据库表类型信息的私有静态方法
    try { // 尝试执行
      return connection.getMetaData().getTableTypes(); // 返回连接的元数据中的表类型信息结果集
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
    }
  }

  @Test void testCatalogsLocal() throws Exception { // 测试本地目录信息的测试方法
    final Connection connection = // 创建连接，使用Factory2类创建本地JSON服务
        DriverManager.getConnection("jdbc:avatica:remote:factory=" + LJS);
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭
    final ResultSet resultSet = connection.getMetaData().getCatalogs(); // 获取目录信息结果集
    final ResultSetMetaData metaData = resultSet.getMetaData(); // 获取结果集元数据
    assertThat(metaData.getColumnCount(), is(1)); // 验证结果集只有1列
    assertThat(metaData.getColumnName(1), is("TABLE_CAT")); // 验证第一列名为TABLE_CAT
    assertThat(resultSet.next(), is(true)); // 验证结果集有一行数据
    assertThat(resultSet.next(), is(false)); // 验证结果集只有一行数据
    resultSet.close(); // 关闭结果集
    connection.close(); // 关闭连接
    assertThat(connection.isClosed(), is(true)); // 验证连接已关闭
  }

  @Test void testSchemasLocal() throws Exception { // 测试本地模式信息的测试方法
    final Connection connection = // 创建连接，使用Factory2类创建本地JSON服务
        DriverManager.getConnection("jdbc:avatica:remote:factory=" + LJS);
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭
    final ResultSet resultSet = connection.getMetaData().getSchemas(); // 获取模式信息结果集
    final ResultSetMetaData metaData = resultSet.getMetaData(); // 获取结果集元数据
    assertThat(metaData.getColumnCount(), is(2)); // 验证结果集有2列
    assertThat(metaData.getColumnName(1), is("TABLE_SCHEM")); // 验证第一列名为TABLE_SCHEM
    assertThat(metaData.getColumnName(2), is("TABLE_CATALOG")); // 验证第二列名为TABLE_CATALOG
    assertThat(resultSet.next(), is(true)); // 验证结果集有第一行数据
    assertThat(resultSet.getString(1), equalTo("POST")); // 验证第一行第一列为POST
    assertThat(resultSet.getString(2), nullValue()); // 验证第一行第二列为null
    assertThat(resultSet.next(), is(true)); // 验证结果集有第二行数据
    assertThat(resultSet.getString(1), equalTo("foodmart")); // 验证第二行第一列为foodmart
    assertThat(resultSet.getString(2), nullValue()); // 验证第二行第二列为null
    assertThat(resultSet.next(), is(true)); // 验证结果集有第三行数据
    assertThat(resultSet.next(), is(true)); // 验证结果集有第四行数据
    assertThat(resultSet.next(), is(false)); // 验证结果集只有四行数据
    resultSet.close(); // 关闭结果集
    connection.close(); // 关闭连接
    assertThat(connection.isClosed(), is(true)); // 验证连接已关闭
  }

  @Test void testMetaFunctionsLocal() throws Exception { // 测试本地元数据函数的测试方法
    final Connection connection = // 创建本地连接，连接到hr测试数据库
        CalciteAssert.hr().connect();
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭
    for (Meta.DatabaseProperty p : Meta.DatabaseProperty.values()) { // 遍历所有数据库属性
      switch (p) { // 根据属性类型进行不同的测试
      case GET_NUMERIC_FUNCTIONS: // 如果是获取数值函数属性
        assertThat(connection.getMetaData().getNumericFunctions(), // 验证数值函数不为空
            not(equalTo("")));
        break; // 跳出switch
      case GET_SYSTEM_FUNCTIONS: // 如果是获取系统函数属性
        assertThat(connection.getMetaData().getSystemFunctions(), // 验证系统函数不为null
            notNullValue());
        break; // 跳出switch
      case GET_TIME_DATE_FUNCTIONS: // 如果是获取时间日期函数属性
        assertThat(connection.getMetaData().getTimeDateFunctions(), // 验证时间日期函数不为空
            not(equalTo("")));
        break; // 跳出switch
      case GET_S_Q_L_KEYWORDS: // 如果是获取SQL关键字属性
        assertThat(connection.getMetaData().getSQLKeywords(), // 验证SQL关键字不为空
            not(equalTo("")));
        break; // 跳出switch
      case GET_STRING_FUNCTIONS: // 如果是获取字符串函数属性
        assertThat(connection.getMetaData().getStringFunctions(), // 验证字符串函数不为空
            not(equalTo("")));
        break; // 跳出switch
      default: // 其他属性不做处理
      }
    }
    connection.close(); // 关闭连接
    assertThat(connection.isClosed(), is(true)); // 验证连接已关闭
  }

  @Test void testMeasureColumnsLocal() throws Exception { // 测试本地度量列的测试方法
    final Connection connection = makeConnectionWithMeasures(); // 创建包含度量的连接
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭
    final ResultSet resultSet = // 获取foo模式下salary列的元数据信息
        connection.getMetaData().getColumns(null, "foo", null, "salary");
    assertThat(resultSet.getMetaData().getColumnCount(), is(24)); // 验证结果集有24列（JDBC标准）
    final int typeNameIdx = resultSet.findColumn("TYPE_NAME"); // 查找TYPE_NAME列的索引
    final int dataTypeIdx = resultSet.findColumn("DATA_TYPE"); // 查找DATA_TYPE列的索引
    assertThat(resultSet.next(), is(true)); // 验证结果集有一行数据
    assertThat(resultSet.getString(typeNameIdx), // 验证类型名称为MEASURE<FLOAT NOT NULL> NOT NULL
        is("MEASURE<FLOAT NOT NULL> NOT NULL"));
    assertThat(resultSet.getInt(dataTypeIdx), is(6)); // 验证数据类型为6（表示FLOAT类型）
  }

  @Test void testRemoteCatalogs() { // 测试远程目录信息的测试方法
    CalciteAssert.hr() // 使用hr测试数据库
        .with(CalciteRemoteDriverTest::getRemoteConnection) // 使用远程连接
        .metaData(CalciteRemoteDriverTest::getCatalogs) // 获取目录信息
        .returns("TABLE_CAT=null\n"); // 验证返回结果为TABLE_CAT=null
  }

  @Test void testRemoteSchemas() { // 测试远程模式信息的测试方法
    CalciteAssert.hr() // 使用hr测试数据库
        .with(CalciteRemoteDriverTest::getRemoteConnection) // 使用远程连接
        .metaData(CalciteRemoteDriverTest::getSchemas) // 获取模式信息
        .returns("TABLE_SCHEM=POST; TABLE_CATALOG=null\n" // 验证返回结果包含所有模式
            + "TABLE_SCHEM=foodmart; TABLE_CATALOG=null\n"
            + "TABLE_SCHEM=hr; TABLE_CATALOG=null\n"
            + "TABLE_SCHEM=metadata; TABLE_CATALOG=null\n");
  }

  /** Checks that the default {@code getColumns()} response
   * contains the 24 standard columns specified in the JDBC specification
   * and in the correct order.
   * // 检查默认的getColumns()响应包含JDBC规范指定的24个标准列，并且顺序正确。
   */
  @Test void testRemoteColumns() { // 测试远程列信息的测试方法
    CalciteAssert.hr() // 使用hr测试数据库
        .with(CalciteRemoteDriverTest::getRemoteConnection) // 使用远程连接
        .metaData(CalciteRemoteDriverTest::getColumns) // 获取列信息
        .returns( // 验证返回结果包含所有24个标准列
            CalciteAssert.checkResultContains("TABLE_CAT=null; " // 验证包含所有JDBC标准列
                + "TABLE_SCHEM=POST; TABLE_NAME=EMPS; COLUMN_NAME=EMPNO; "
                + "DATA_TYPE=4; TYPE_NAME=INTEGER NOT NULL; COLUMN_SIZE=-1; "
                + "BUFFER_LENGTH=null; DECIMAL_DIGITS=null; NUM_PREC_RADIX=10; "
                + "NULLABLE=0; REMARKS=null; COLUMN_DEF=null; "
                + "SQL_DATA_TYPE=null; SQL_DATETIME_SUB=null; "
                + "CHAR_OCTET_LENGTH=-1; ORDINAL_POSITION=1; IS_NULLABLE=NO; "
                + "SCOPE_CATALOG=null; SCOPE_SCHEMA=null; SCOPE_TABLE=null; "
                + "SOURCE_DATA_TYPE=null; IS_AUTOINCREMENT=; "
                + "IS_GENERATEDCOLUMN="));
  }

  /** Checks that the default {@code getTables()} response contains the 10
   * standard columns specified in the JDBC specification and in the correct
   * order.
   * // 检查默认的getTables()响应包含JDBC规范指定的10个标准列，并且顺序正确。
   */
  @Test void testRemoteTables() { // 测试远程表信息的测试方法
    CalciteAssert.hr() // 使用hr测试数据库
        .with(CalciteRemoteDriverTest::getRemoteConnection) // 使用远程连接
        .metaData(CalciteRemoteDriverTest::getTables) // 获取表信息
        .returns( // 验证返回结果包含所有10个标准列
            CalciteAssert.checkResultContains("TABLE_CAT=null; " // 验证包含所有JDBC标准列
                + "TABLE_SCHEM=POST; TABLE_NAME=DEPT; TABLE_TYPE=VIEW; "
                + "REMARKS=null; TYPE_CAT=null; TYPE_SCHEM=null; "
                + "TYPE_NAME=null; SELF_REFERENCING_COL_NAME=null; "
                + "REF_GENERATION=null"));
  }

  @Test void testRemoteTypeInfo() { // 测试远程类型信息的测试方法
    // TypeInfo does not include internal types (NULL, SYMBOL, ANY, etc.)
    // TypeInfo不包含内部类型（NULL、SYMBOL、ANY等）
    CalciteAssert.hr() // 使用hr测试数据库
        .with(CalciteRemoteDriverTest::getRemoteConnection) // 使用远程连接
        .metaData(CalciteRemoteDriverTest::getTypeInfo) // 获取类型信息
        .returns(CalciteAssert.checkResultCount(is(45))); // 验证返回结果有45行（45种类型）
  }

  @Test void testRemoteTableTypes() { // 测试远程表类型信息的测试方法
    CalciteAssert.hr() // 使用hr测试数据库
        .with(CalciteRemoteDriverTest::getRemoteConnection) // 使用远程连接
        .metaData(CalciteRemoteDriverTest::getTableTypes) // 获取表类型信息
        .returns("TABLE_TYPE=TABLE\n" // 验证返回结果包含TABLE和VIEW两种表类型
            + "TABLE_TYPE=VIEW\n");
  }

  @Test void testRemoteExecuteQuery() { // 测试远程执行查询的测试方法
    CalciteAssert.hr() // 使用hr测试数据库
        .with(CalciteRemoteDriverTest::getRemoteConnection) // 使用远程连接
        .query("values (1, 'a'), (cast(null as integer), 'b')") // 执行VALUES查询
        .returnsUnordered("EXPR$0=1; EXPR$1=a", "EXPR$0=null; EXPR$1=b"); // 验证返回结果包含两行数据
  }

  /** Same query as {@link #testRemoteExecuteQuery()}, run without the test
   * infrastructure.
   * // 与testRemoteExecuteQuery()相同的查询，但不使用测试基础设施运行。
   */
  @Test void testRemoteExecuteQuery2() throws Exception { // 测试远程执行查询的测试方法（不使用测试基础设施）
    try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接，使用try-with-resources自动关闭
      final Statement statement = remoteConnection.createStatement(); // 创建语句对象
      final String sql = "values (1, 'a'), (cast(null as integer), 'b')"; // 定义SQL查询
      final ResultSet resultSet = statement.executeQuery(sql); // 执行查询获取结果集
      int n = 0; // 初始化计数器
      while (resultSet.next()) { // 遍历结果集
        ++n; // 递增计数器
      }
      assertThat(n, equalTo(2)); // 验证结果集有2行数据
    }
  }

  /** For each (source, destination) type, make sure that we can convert bind
   * variables.
   * // 对于每个（源类型，目标类型）组合，确保能够转换绑定变量。
   */
  @Test void testParameterConvert() throws Exception { // 测试参数转换的测试方法
    final StringBuilder sql = new StringBuilder("select 1"); // 构建SQL语句，初始为select 1
    final Map<SqlType, Integer> map = new HashMap<>(); // 创建映射，存储SQL类型到参数索引的映射
    for (Map.Entry<Class, SqlType> entry : SqlType.getSetConversions()) { // 遍历所有set转换（从Java类型到SQL类型）
      final SqlType sqlType = entry.getValue(); // 获取SQL类型
      switch (sqlType) { // 根据SQL类型进行过滤
      case BIT: // 跳过BIT类型
      case LONGVARCHAR: // 跳过LONGVARCHAR类型
      case LONGVARBINARY: // 跳过LONGVARBINARY类型
      case NCHAR: // 跳过NCHAR类型
      case NVARCHAR: // 跳过NVARCHAR类型
      case LONGNVARCHAR: // 跳过LONGNVARCHAR类型
      case BLOB: // 跳过BLOB类型
      case CLOB: // 跳过CLOB类型
      case NCLOB: // 跳过NCLOB类型
      case ARRAY: // 跳过ARRAY类型
      case REF: // 跳过REF类型
      case STRUCT: // 跳过STRUCT类型
      case DATALINK: // 跳过DATALINK类型
      case ROWID: // 跳过ROWID类型
      case JAVA_OBJECT: // 跳过JAVA_OBJECT类型
      case SQLXML: // 跳过SQLXML类型
        continue; // 跳过当前类型
      }
      if (!map.containsKey(sqlType)) { // 如果映射中不包含该SQL类型
        sql.append(", cast(? as ").append(sqlType).append(")"); // 添加cast表达式到SQL语句
        map.put(sqlType, map.size() + 1); // 将SQL类型映射到参数索引
      }
    }
    sql.append(" from (values 1)"); // 添加FROM子句
    final PreparedStatement statement = // 创建预编译语句
        localConnection.prepareStatement(sql.toString());
    for (Map.Entry<SqlType, Integer> entry : map.entrySet()) { // 遍历映射中的所有SQL类型
      statement.setNull(entry.getValue(), entry.getKey().id); // 将所有参数设置为null
    }
    for (Map.Entry<Class, SqlType> entry : SqlType.getSetConversions()) { // 再次遍历所有set转换
      final SqlType sqlType = entry.getValue(); // 获取SQL类型
      if (!map.containsKey(sqlType)) { // 如果映射中不包含该SQL类型
        continue; // 跳过
      }
      int param = map.get(sqlType); // 获取参数索引
      Class clazz = entry.getKey(); // 获取Java类型
      for (Object sampleValue : values(sqlType.boxedClass())) { // 遍历所有样本值
        switch (sqlType) { // 根据SQL类型进行过滤
        case DATE: // 跳过DATE类型
        case TIME: // 跳过TIME类型
        case TIMESTAMP: // 跳过TIMESTAMP类型
          continue; // FIXME: 暂时跳过
        }
        if (clazz == Calendar.class) { // 如果是Calendar类型
          continue; // FIXME: 暂时跳过
        }
        final Object o; // 定义转换后的对象
        try { // 尝试转换
          o = convert(sampleValue, clazz); // 转换样本值到目标类型
        } catch (IllegalArgumentException | ParseException e) { // 捕获转换异常
          continue; // 跳过该值
        }
        out.println("check " + o + " (originally " // 输出调试信息
            + sampleValue.getClass() + ", now " + o.getClass()
            + ") converted to " + sqlType);
        if (o instanceof Double && o.equals(Double.POSITIVE_INFINITY) // 如果是正无穷大
            || o instanceof Float && o.equals(Float.POSITIVE_INFINITY)) { // 如果是正无穷大
          continue; // 跳过
        }
        statement.setObject(param, o, sqlType.id); // 设置参数值
        final ResultSet resultSet = statement.executeQuery(); // 执行查询
        assertThat(resultSet.next(), is(true)); // 验证结果集有一行数据
        out.println(resultSet.getString(param + 1)); // 输出结果值
      }
    }
    statement.close(); // 关闭语句
  }

  /** Check that the "set" conversion table looks like Table B-5 in JDBC 4.1
   * specification
   * // 检查"set"转换表是否与JDBC 4.1规范中的表B-5一致。
   */
  @Test void testTableB5() { // 测试JDBC 4.1规范表B-5的测试方法
    SqlType[] columns = { // 定义SQL类型列数组
        SqlType.TINYINT, SqlType.SMALLINT, SqlType.INTEGER, SqlType.BIGINT, // 整数类型
        SqlType.REAL, SqlType.FLOAT, SqlType.DOUBLE, SqlType.DECIMAL, // 浮点数类型
        SqlType.NUMERIC, SqlType.BIT, SqlType.BOOLEAN, SqlType.CHAR, // 其他类型
        SqlType.VARCHAR, SqlType.LONGVARCHAR, SqlType.BINARY, SqlType.VARBINARY,
        SqlType.LONGVARBINARY, SqlType.DATE, SqlType.TIME, SqlType.TIMESTAMP,
        SqlType.ARRAY, SqlType.BLOB, SqlType.CLOB, SqlType.STRUCT, SqlType.REF,
        SqlType.DATALINK, SqlType.JAVA_OBJECT, SqlType.ROWID, SqlType.NCHAR,
        SqlType.NVARCHAR, SqlType.LONGNVARCHAR, SqlType.NCLOB, SqlType.SQLXML
    };
    Class[] rows = { // 定义Java类型行数组
        String.class, BigDecimal.class, Boolean.class, Byte.class, Short.class, // 基本类型
        Integer.class, Long.class, Float.class, Double.class, byte[].class,
        BigInteger.class, java.sql.Date.class, Time.class, Timestamp.class,
        Array.class, Blob.class, Clob.class, Struct.class, Ref.class,
        URL.class, Class.class, RowId.class, NClob.class, SQLXML.class,
        Calendar.class, java.util.Date.class
    };
    for (Class row : rows) { // 遍历所有Java类型
      final String s = row == Date.class ? row.getName() : row.getSimpleName(); // 获取类型名称
      out.print(pad(s)); // 输出填充后的类型名称
      for (SqlType column : columns) { // 遍历所有SQL类型
        out.print(SqlType.canSet(row, column) ? "x " : ". "); // 输出是否可以set
      }
      out.println(); // 换行
    }
  }

  private String pad(String x) { // 填充字符串到20个字符的私有方法
    while (x.length() < 20) { // 当字符串长度小于20时
      x = x + " "; // 添加空格
    }
    return x; // 返回填充后的字符串
  }

  /** Check that the "get" conversion table looks like Table B-5 in JDBC 4.1
   * specification
   * // 检查"get"转换表是否与JDBC 4.1规范中的表B-5一致。
   */
  @Test void testTableB6() { // 测试JDBC 4.1规范表B-6的测试方法
    SqlType[] columns = { // 定义SQL类型列数组
        SqlType.TINYINT, SqlType.SMALLINT, SqlType.INTEGER, SqlType.BIGINT, // 整数类型
        SqlType.REAL, SqlType.FLOAT, SqlType.DOUBLE, SqlType.DECIMAL, // 浮点数类型
        SqlType.NUMERIC, SqlType.BIT, SqlType.BOOLEAN, SqlType.CHAR, // 其他类型
        SqlType.VARCHAR, SqlType.LONGVARCHAR, SqlType.BINARY, SqlType.VARBINARY,
        SqlType.LONGVARBINARY, SqlType.DATE, SqlType.TIME, SqlType.TIMESTAMP,
        SqlType.CLOB, SqlType.BLOB, SqlType.ARRAY, SqlType.REF,
        SqlType.DATALINK, SqlType.STRUCT, SqlType.JAVA_OBJECT, SqlType.ROWID,
        SqlType.NCHAR, SqlType.NVARCHAR, SqlType.LONGNVARCHAR, SqlType.NCLOB,
        SqlType.SQLXML
    };
    final PrintWriter out = // 创建打印写入器
        CalciteSystemProperty.DEBUG.value() // 如果开启了DEBUG系统属性
            ? Util.printWriter(System.out) // 则输出到标准输出
            : new PrintWriter(new StringWriter()); // 否则输出到字符串写入器
    for (SqlType.Method row : SqlType.Method.values()) { // 遍历所有get方法
      out.print(pad(row.methodName)); // 输出填充后的方法名称
      for (SqlType column : columns) { // 遍历所有SQL类型
        out.print(SqlType.canGet(row, column) ? "x " : ". "); // 输出是否可以get
      }
      out.println(); // 换行
    }
  }

  /** Checks {@link Statement#execute} on a query over a remote connection.
   *
   * <p>Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-646">[CALCITE-646]
   * AvaticaStatement execute method broken over remote JDBC</a>.
   * // 检查远程连接上的Statement#execute方法。这是CALCITE-646的测试用例。
   */
  @Test void testRemoteStatementExecute() throws Exception { // 测试远程语句执行的测试方法
    try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接
      final Statement statement = remoteConnection.createStatement(); // 创建语句对象
      final boolean status = statement.execute("values (1, 2), (3, 4), (5, 6)"); // 执行查询
      assertThat(status, is(true)); // 验证返回true（表示有结果集）
      final ResultSet resultSet = statement.getResultSet(); // 获取结果集
      int n = 0; // 初始化计数器
      while (resultSet.next()) { // 遍历结果集
        ++n; // 递增计数器
      }
      assertThat(n, equalTo(3)); // 验证结果集有3行数据
    }
  }

  @Test void testAvaticaConnectionException() { // 测试Avatica连接异常的测试方法
    assertThrows(SQLException.class, () -> { // 验证抛出SQLException异常
      try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接
        remoteConnection.isValid(-1); // 调用isValid方法，参数为-1应该抛出异常
      }
    });
  }

  @Test void testAvaticaStatementException() { // 测试Avatica语句异常的测试方法
    assertThrows(SQLException.class, () -> { // 验证抛出SQLException异常
      try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接
        try (Statement statement = remoteConnection.createStatement()) { // 创建语句对象
          statement.setCursorName("foo"); // 调用setCursorName方法，应该抛出异常
        }
      }
    });
  }

  @Test void testAvaticaStatementGetMoreResults() throws Exception { // 测试Avatica语句获取更多结果的测试方法
    try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接
      try (Statement statement = remoteConnection.createStatement()) { // 创建语句对象
        assertThat(statement.getMoreResults(), is(false)); // 验证getMoreResults返回false（没有更多结果）
      }
    }
  }

  @Test void testRemoteExecute() throws Exception { // 测试远程执行的测试方法
    try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接
      ResultSet resultSet = // 执行查询获取结果集
          remoteConnection.createStatement().executeQuery(
              "select * from \"hr\".\"emps\"");
      int count = 0; // 初始化计数器
      while (resultSet.next()) { // 遍历结果集
        ++count; // 递增计数器
      }
      assertThat(count > 0, is(true)); // 验证结果集有数据
    }
  }

  @Test void testRemoteExecuteMaxRow() throws Exception { // 测试远程执行最大行数的测试方法
    try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接
      Statement statement = remoteConnection.createStatement(); // 创建语句对象
      statement.setMaxRows(2); // 设置最大行数为2
      ResultSet resultSet = // 执行查询获取结果集
          statement.executeQuery("select * from \"hr\".\"emps\"");
      int count = 0; // 初始化计数器
      while (resultSet.next()) { // 遍历结果集
        ++count; // 递增计数器
      }
      assertThat(count, equalTo(2)); // 验证结果集只有2行数据
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-661">[CALCITE-661]
   * Remote fetch in Calcite JDBC driver</a>.
   * // CALCITE-661的测试用例，测试Calcite JDBC驱动中的远程获取。
   */
  @Test void testRemotePrepareExecute() throws Exception { // 测试远程预编译执行的测试方法
    try (Connection remoteConnection = getRemoteConnection()) { // 获取远程连接
      final PreparedStatement preparedStatement = // 创建预编译语句
          remoteConnection.prepareStatement("select * from \"hr\".\"emps\"");
      ResultSet resultSet = preparedStatement.executeQuery(); // 执行查询获取结果集
      int count = 0; // 初始化计数器
      while (resultSet.next()) { // 遍历结果集
        ++count; // 递增计数器
      }
      assertThat(count > 0, is(true)); // 验证结果集有数据
    }
  }

  public static Connection makeConnection(boolean withMeasures) // 创建连接的公共静态方法，withMeasures参数指示是否包含度量
      throws Exception { // 可能抛出异常
    List<Employee> employees = new ArrayList<>(); // 创建员工列表
    for (int i = 1; i <= 101; i++) { // 循环101次
      employees.add(new Employee(i, 0, "first", 0f, null)); // 添加101个员工对象
    }
    return JdbcFrontLinqBackTest.makeConnection(employees, withMeasures); // 使用JdbcFrontLinqBackTest创建连接
  }

  /** Creates a connection without measures.
   * // 创建不包含度量的连接。
   */
  public static Connection makeConnection() throws Exception { // 创建连接的公共静态方法（不包含度量）
    return makeConnection(false); // 调用makeConnection方法，参数为false
  }

  /** Creates a connection with measures.
   * // 创建包含度量的连接。
   */
  public static Connection makeConnectionWithMeasures() throws Exception { // 创建连接的公共静态方法（包含度量）
    return makeConnection(true); // 调用makeConnection方法，参数为true
  }

  @Test void testLocalStatementFetch() throws Exception { // 测试本地语句获取的测试方法
    Connection conn = makeConnection(); // 创建连接
    String sql = "select * from \"foo\".\"bar\""; // 定义SQL查询
    Statement statement = conn.createStatement(); // 创建语句对象
    boolean status = statement.execute(sql); // 执行查询
    assertThat(status, is(true)); // 验证返回true（表示有结果集）
    ResultSet resultSet = statement.getResultSet(); // 获取结果集
    int count = 0; // 初始化计数器
    while (resultSet.next()) { // 遍历结果集
      count += 1; // 递增计数器
    }
    assertThat(count, is(101)); // 验证结果集有101行数据
  }

  @Disabled("Cannot yet execute query with virtual measures") // 禁用测试，原因：还不能执行包含虚拟度量的查询
  @Test void testLocalStatementResultSetMeasureMetadata() throws Exception { // 测试本地语句结果集度量元数据的测试方法
    Connection conn = makeConnectionWithMeasures(); // 创建包含度量的连接
    String sql = "select * from \"foo\".\"bar\""; // 定义SQL查询
    Statement statement = conn.createStatement(); // 创建语句对象
    boolean status = statement.execute(sql); // 执行查询
    assertThat(status, is(true)); // 验证返回true（表示有结果集）
    ResultSet resultSet = statement.getResultSet(); // 获取结果集
    String typeName = resultSet.getMetaData().getColumnTypeName(4); // 获取第4列的类型名称
    Integer ordinal = resultSet.getMetaData().getColumnType(4); // 获取第4列的数据类型
    assertThat(typeName, is("MEASURE<FLOAT>")); // 验证类型名称为MEASURE<FLOAT>
    assertThat(ordinal, is(6)); // 验证数据类型为6（表示FLOAT类型）
  }

  /** Test that returns all result sets in one go.
   * // 测试一次性返回所有结果集。
   */
  @Test void testLocalPreparedStatementFetch() throws Exception { // 测试本地预编译语句获取的测试方法
    Connection conn = makeConnection(); // 创建连接
    assertThat(conn.isClosed(), is(false)); // 验证连接未关闭
    String sql = "select * from \"foo\".\"bar\""; // 定义SQL查询
    PreparedStatement preparedStatement = conn.prepareStatement(sql); // 创建预编译语句
    assertThat(conn.isClosed(), is(false)); // 验证连接未关闭
    boolean status = preparedStatement.execute(); // 执行查询
    assertThat(status, is(true)); // 验证返回true（表示有结果集）
    ResultSet resultSet = preparedStatement.getResultSet(); // 获取结果集
    assertThat(resultSet, notNullValue()); // 验证结果集不为null
    int count = 0; // 初始化计数器
    while (resultSet.next()) { // 遍历结果集
      assertThat(resultSet.getObject(1), notNullValue()); // 验证第一列不为null
      count += 1; // 递增计数器
    }
    assertThat(count, is(101)); // 验证结果集有101行数据
  }

  @Test void testRemoteStatementFetch() throws Exception { // 测试远程语句获取的测试方法
    final Connection connection = // 创建连接，使用LocalServiceMoreFactory类
        DriverManager.getConnection("jdbc:avatica:remote:factory="
            + LocalServiceMoreFactory.class.getName());
    String sql = "select * from \"foo\".\"bar\""; // 定义SQL查询
    Statement statement = connection.createStatement(); // 创建语句对象
    boolean status = statement.execute(sql); // 执行查询
    assertThat(status, is(true)); // 验证返回true（表示有结果集）
    ResultSet resultSet = statement.getResultSet(); // 获取结果集
    int count = 0; // 初始化计数器
    while (resultSet.next()) { // 遍历结果集
      count += 1; // 递增计数器
    }
    assertThat(count, is(101)); // 验证结果集有101行数据
  }

  @Test void testRemotePreparedStatementFetch() throws Exception { // 测试远程预编译语句获取的测试方法
    final Connection connection = // 创建连接，使用LocalServiceMoreFactory类
        DriverManager.getConnection("jdbc:avatica:remote:factory="
            + LocalServiceMoreFactory.class.getName());
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭

    String sql = "select * from \"foo\".\"bar\""; // 定义SQL查询
    PreparedStatement preparedStatement = connection.prepareStatement(sql); // 创建预编译语句
    assertThat(preparedStatement.isClosed(), is(false)); // 验证预编译语句未关闭

    boolean status = preparedStatement.execute(); // 执行查询
    assertThat(status, is(true)); // 验证返回true（表示有结果集）
    ResultSet resultSet = preparedStatement.getResultSet(); // 获取结果集
    assertThat(resultSet, notNullValue()); // 验证结果集不为null

    int count = 0; // 初始化计数器
    while (resultSet.next()) { // 遍历结果集
      assertThat(resultSet.getObject(1), notNullValue()); // 验证第一列不为null
      count += 1; // 递增计数器
    }
    assertThat(count, is(101)); // 验证结果集有101行数据
  }

  /** Service factory that creates a Calcite instance with more data.
   * // 创建包含更多数据的Calcite实例的服务工厂。
   */
  public static class LocalServiceMoreFactory implements Service.Factory { // LocalServiceMoreFactory类，实现Service.Factory接口
    @Override public Service create(AvaticaConnection connection) { // 创建服务的方法
      try { // 尝试执行
        Connection conn = makeConnection(); // 创建连接
        final CalciteMetaImpl meta = // 创建CalciteMetaImpl元数据实现
            CalciteMetaImpl.create(conn.unwrap(CalciteConnection.class)); // 从CalciteConnection创建元数据
        return new LocalService(meta); // 返回本地服务
      } catch (Exception e) { // 捕获异常
        throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
      }
    }
  }

  /** A bunch of sample values of various types.
   * // 各种类型的样本值集合。
   */
  private static final List<Object> SAMPLE_VALUES = // 定义样本值列表常量
      ImmutableList.of(false, true, // 布尔值
          // byte
          // 字节值
          (byte) 0, (byte) 1, Byte.MIN_VALUE, Byte.MAX_VALUE, // 字节最小值和最大值
          // short
          // 短整型值
          (short) 0, (short) 1, Short.MIN_VALUE, Short.MAX_VALUE, // 短整型最小值和最大值
          (short) Byte.MIN_VALUE, (short) Byte.MAX_VALUE, // 字节范围
          // int
          // 整型值
          0, 1, -3, Integer.MIN_VALUE, Integer.MAX_VALUE, // 整型最小值和最大值
          (int) Short.MIN_VALUE, (int) Short.MAX_VALUE, // 短整型范围
          (int) Byte.MIN_VALUE, (int) Byte.MAX_VALUE, // 字节范围
          // long
          // 长整型值
          0L, 1L, -2L, Long.MIN_VALUE, Long.MAX_VALUE, // 长整型最小值和最大值
          (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE, // 整型范围
          (long) Short.MIN_VALUE, (long) Short.MAX_VALUE, // 短整型范围
          (long) Byte.MIN_VALUE, (long) Byte.MAX_VALUE, // 字节范围
          // float
          // 浮点型值
          0F, 1.5F, -10F, Float.MIN_VALUE, Float.MAX_VALUE, // 浮点型最小值和最大值
          // double
          // 双精度浮点型值
          0D, Math.PI, Double.MIN_VALUE, Double.MAX_VALUE, // 双精度浮点型最小值和最大值
          (double) Float.MIN_VALUE, (double) Float.MAX_VALUE, // 浮点型范围
          (double) Integer.MIN_VALUE, (double) Integer.MAX_VALUE, // 整型范围
          // BigDecimal
          // 大十进制值
          BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.valueOf(2.5D), // 零、一、2.5
          // Next one causes problems for most types
          // 下一个值对大多数类型有问题
          // BigDecimal.valueOf(Double.MAX_VALUE),
          BigDecimal.valueOf(Long.MIN_VALUE), // 长整型最小值
          // datetime
          // 日期时间值
          new Timestamp(0), // 时间戳
          new java.sql.Date(0), // SQL日期
          new Time(0), // SQL时间
          // string
          // 字符串值
          "", "foo", " foo! Baz ", // 空字符串、foo、带空格的字符串
          // byte[]
          // 字节数组
          new byte[0], "hello".getBytes(StandardCharsets.UTF_8)); // 空字节数组、hello的字节数组

  private static List<Object> values(Class clazz) { // 获取指定类型的样本值的私有静态方法
    final List<Object> list = new ArrayList<>(); // 创建结果列表
    for (Object sampleValue : SAMPLE_VALUES) { // 遍历所有样本值
      if (sampleValue.getClass() == clazz) { // 如果样本值的类型与指定类型匹配
        list.add(sampleValue); // 添加到结果列表
      }
    }
    return list; // 返回结果列表
  }

  private Object convert(Object o, Class clazz) throws ParseException { // 转换对象到指定类型的私有方法
    if (o.getClass() == clazz) { // 如果对象类型与目标类型相同
      return o; // 直接返回对象
    }
    if (clazz == String.class) { // 如果目标是字符串类型
      return o.toString(); // 转换为字符串
    }
    if (clazz == Boolean.class) { // 如果目标是布尔类型
      return o instanceof Number // 如果是数字类型
          && ((Number) o).intValue() != 0 // 且值不为0
          || o instanceof String // 或者是字符串类型
          && ((String) o).equalsIgnoreCase("true"); // 且值等于true（忽略大小写）
    }
    if (clazz == byte[].class) { // 如果目标是字节数组类型
      if (o instanceof String) { // 如果源对象是字符串
        return ((String) o).getBytes(StandardCharsets.UTF_8); // 转换为UTF-8编码的字节数组
      }
    }
    if (clazz == Timestamp.class) { // 如果目标是时间戳类型
      if (o instanceof String) { // 如果源对象是字符串
        return Timestamp.valueOf((String) o); // 转换为时间戳
      }
    }
    if (clazz == Time.class) { // 如果目标是时间类型
      if (o instanceof String) { // 如果源对象是字符串
        return Time.valueOf((String) o); // 转换为时间
      }
    }
    if (clazz == java.sql.Date.class) { // 如果目标是SQL日期类型
      if (o instanceof String) { // 如果源对象是字符串
        return java.sql.Date.valueOf((String) o); // 转换为SQL日期
      }
    }
    if (clazz == java.util.Date.class) { // 如果目标是日期类型
      if (o instanceof String) { // 如果源对象是字符串
        final DateFormat dateFormat = // 创建日期格式化器
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, // 使用短格式
                Locale.ROOT); // 使用根地区
        return dateFormat.parse((String) o); // 解析字符串为日期
      }
    }
    if (clazz == Calendar.class) { // 如果目标是日历类型
      if (o instanceof String) { // 如果源对象是字符串
        return Util.calendar(); // TODO: 返回当前日历，需要完善
      }
    }
    if (o instanceof Boolean) { // 如果源对象是布尔类型
      o = (Boolean) o ? 1 : 0; // 转换为1或0
    }
    if (o instanceof Number) { // 如果源对象是数字类型
      final Number number = (Number) o; // 转换为Number类型
      if (Number.class.isAssignableFrom(clazz)) { // 如果目标是数字类型
        if (clazz == BigDecimal.class) { // 如果目标是BigDecimal
          if (o instanceof Double || o instanceof Float) { // 如果源对象是浮点类型
            return new BigDecimal(number.doubleValue()); // 使用double值创建BigDecimal
          } else { // 否则
            return new BigDecimal(number.longValue()); // 使用long值创建BigDecimal
          }
        } else if (clazz == BigInteger.class) { // 如果目标是BigInteger
          return new BigInteger(o.toString()); // 使用字符串创建BigInteger
        } else if (clazz == Byte.class || clazz == byte.class) { // 如果目标是字节类型
          return number.byteValue(); // 转换为字节值
        } else if (clazz == Short.class || clazz == short.class) { // 如果目标是短整型
          return number.shortValue(); // 转换为短整型值
        } else if (clazz == Integer.class || clazz == int.class) { // 如果目标是整型
          return number.intValue(); // 转换为整型值
        } else if (clazz == Long.class || clazz == long.class) { // 如果目标是长整型
          return number.longValue(); // 转换为长整型值
        } else if (clazz == Float.class || clazz == float.class) { // 如果目标是浮点型
          return number.floatValue(); // 转换为浮点型值
        } else if (clazz == Double.class || clazz == double.class) { // 如果目标是双精度浮点型
          return number.doubleValue(); // 转换为双精度浮点型值
        }
      }
    }
    if (Number.class.isAssignableFrom(clazz)) { // 如果目标是数字类型
      if (clazz == BigDecimal.class) { // 如果目标是BigDecimal
        return new BigDecimal(o.toString()); // 使用字符串创建BigDecimal
      } else if (clazz == BigInteger.class) { // 如果目标是BigInteger
        return new BigInteger(o.toString()); // 使用字符串创建BigInteger
      } else if (clazz == Byte.class || clazz == byte.class) { // 如果目标是字节类型
        return Byte.valueOf(o.toString()); // 使用字符串创建字节值
      } else if (clazz == Short.class || clazz == short.class) { // 如果目标是短整型
        return Short.valueOf(o.toString()); // 使用字符串创建短整型值
      } else if (clazz == Integer.class || clazz == int.class) { // 如果目标是整型
        return Integer.valueOf(o.toString()); // 使用字符串创建整型值
      } else if (clazz == Long.class || clazz == long.class) { // 如果目标是长整型
        return Long.valueOf(o.toString()); // 使用字符串创建长整型值
      } else if (clazz == Float.class || clazz == float.class) { // 如果目标是浮点型
        return Float.valueOf(o.toString()); // 使用字符串创建浮点型值
      } else if (clazz == Double.class || clazz == double.class) { // 如果目标是双精度浮点型
        return Double.valueOf(o.toString()); // 使用字符串创建双精度浮点型值
      }
    }
    throw new AssertionError("cannot convert " + o + "(" + o.getClass() // 抛出断言错误，表示无法转换
        + ") to " + clazz);
  }

  /** Factory that creates a {@link Meta} that can see the test databases.
   * // 创建能够看到测试数据库的Meta的工厂。
   */
  public static class Factory implements Meta.Factory { // Factory类，实现Meta.Factory接口
    public Meta create(List<String> args) { // 创建Meta的方法
      try { // 尝试执行
        final Connection connection = CalciteAssert.hr().connect(); // 创建连接到hr测试数据库
        return CalciteMetaImpl.create((CalciteConnection) connection); // 创建CalciteMetaImpl实例
      } catch (Exception e) { // 捕获异常
        throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
      }
    }
  }

  /** Factory that creates a {@code LocalJsonService}.
   * // 创建LocalJsonService的工厂。
   */
  public static class Factory2 implements Service.Factory { // Factory2类，实现Service.Factory接口
    public Service create(AvaticaConnection connection) { // 创建服务的方法
      try { // 尝试执行
        Connection localConnection = CalciteAssert.hr().connect(); // 创建本地连接
        final Meta meta = CalciteConnectionImpl.TROJAN // 使用TROJAN方法获取Meta
            .getMeta((CalciteConnectionImpl) localConnection); // 从CalciteConnectionImpl获取Meta
        return new LocalJsonService(new LocalService(meta)); // 返回LocalJsonService实例
      } catch (Exception e) { // 捕获异常
        throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
      }
    }
  }

  /** Factory that creates a Service with connection to a modifiable table.
   * // 创建连接到可修改表的服务的工厂。
   */
  public static class LocalServiceModifiableFactory implements Service.Factory { // LocalServiceModifiableFactory类，实现Service.Factory接口
    @Override public Service create(AvaticaConnection connection) { // 创建服务的方法
      try { // 尝试执行
        Connection conn = JdbcFrontLinqBackTest.makeConnection(); // 创建连接
        final CalciteMetaImpl meta = // 创建CalciteMetaImpl实例
            CalciteMetaImpl.create(conn.unwrap(CalciteConnection.class)); // 从CalciteConnection创建元数据
        return new LocalService(meta); // 返回LocalService实例
      } catch (Exception e) { // 捕获异常
        throw TestUtil.rethrow(e); // 将异常重新抛出为运行时异常
      }
    }
  }

  /** Test remote Statement insert.
   * // 测试远程Statement插入。
   */
  @Test void testInsert() throws Exception { // 测试插入的测试方法
    final Connection connection = // 创建连接，使用LocalServiceModifiableFactory类
        DriverManager.getConnection("jdbc:avatica:remote:factory="
            + LocalServiceModifiableFactory.class.getName());
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭
    Statement statement = connection.createStatement(); // 创建语句对象
    assertThat(statement.isClosed(), is(false)); // 验证语句未关闭

    String sql = "insert into \"foo\".\"bar\" values (1, 1, 'second', 2, 2)"; // 定义插入SQL
    boolean status = statement.execute(sql); // 执行插入
    assertThat(status, is(false)); // 验证返回false（表示没有结果集）
    ResultSet resultSet = statement.getResultSet(); // 获取结果集
    assertThat(resultSet, nullValue()); // 验证结果集为null
    int updateCount = statement.getUpdateCount(); // 获取更新行数
    assertThat(updateCount, is(1)); // 验证更新了1行
    connection.close(); // 关闭连接
  }

  /** Test remote Statement batched insert.
   * // 测试远程Statement批量插入。
   */
  @Test void testInsertBatch() throws Exception { // 测试批量插入的测试方法
    final Connection connection = // 创建连接，使用LocalServiceModifiableFactory类
        DriverManager.getConnection("jdbc:avatica:remote:factory="
            + LocalServiceModifiableFactory.class.getName());
    assertThat(connection.getMetaData().supportsBatchUpdates(), is(true)); // 验证支持批量更新
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭
    Statement statement = connection.createStatement(); // 创建语句对象
    assertThat(statement.isClosed(), is(false)); // 验证语句未关闭

    String sql = "insert into \"foo\".\"bar\" values (1, 1, 'second', 2, 2)"; // 定义插入SQL
    statement.addBatch(sql); // 添加批量操作
    statement.addBatch(sql); // 再次添加批量操作
    int[] updateCounts = statement.executeBatch(); // 执行批量操作
    assertThat(updateCounts, primitiveArrayWithSize(2)); // 验证返回2个更新计数
    assertThat(updateCounts[0], is(1)); // 验证第一个操作更新了1行
    assertThat(updateCounts[1], is(1)); // 验证第二个操作更新了1行
    ResultSet resultSet = statement.getResultSet(); // 获取结果集
    assertThat(resultSet, nullValue()); // 验证结果集为null

    // Now empty batch
    // 现在是空批次
    statement.clearBatch(); // 清空批量操作
    updateCounts = statement.executeBatch(); // 执行批量操作
    assertThat(updateCounts, primitiveArrayWithSize(0)); // 验证返回0个更新计数
    resultSet = statement.getResultSet(); // 获取结果集
    assertThat(resultSet, nullValue()); // 验证结果集为null

    connection.close(); // 关闭连接
  }

  /**
   * Remote PreparedStatement insert WITHOUT bind variables.
   * // 远程PreparedStatement插入，不使用绑定变量。
   */
  @Test void testRemotePreparedStatementInsert() throws Exception { // 测试远程PreparedStatement插入的测试方法
    final Connection connection = // 创建连接，使用LocalServiceModifiableFactory类
        DriverManager.getConnection("jdbc:avatica:remote:factory="
            + LocalServiceModifiableFactory.class.getName());
    assertThat(connection.isClosed(), is(false)); // 验证连接未关闭

    String sql = "insert into \"foo\".\"bar\" values (1, 1, 'second', 2, 2)"; // 定义插入SQL
    PreparedStatement preparedStatement = connection.prepareStatement(sql); // 创建预编译语句
    assertThat(preparedStatement.isClosed(), is(false)); // 验证预编译语句未关闭

    boolean status = preparedStatement.execute(); // 执行插入
    assertThat(status, is(false)); // 验证返回false（表示没有结果集）
    ResultSet resultSet = preparedStatement.getResultSet(); // 获取结果集
    assertThat(resultSet, nullValue()); // 验证结果集为null
    int updateCount = preparedStatement.getUpdateCount(); // 获取更新行数
    assertThat(updateCount, is(1)); // 验证更新了1行
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3338">[CALCITE-3338]
   * Error with executeBatch and preparedStatement when using RemoteMeta</a>.
   * // CALCITE-3338的测试用例，测试使用RemoteMeta时executeBatch和preparedStatement的错误。
   */
  @Test void testInsertBatchWithPreparedStatement() throws Exception { // 测试PreparedStatement批量插入的测试方法
    final Connection connection = // 创建连接，使用LocalServiceModifiableFactory类
        DriverManager.getConnection("jdbc:avatica:remote:factory="
            + LocalServiceModifiableFactory.class.getName());

    PreparedStatement pst = // 创建预编译语句，使用参数占位符
        connection.prepareStatement("insert into \"foo\".\"bar\"\n"
            + "values (?, ?, ?, ?, ?)");
    pst.setInt(1, 1); // 设置第一个参数为1
    pst.setInt(2, 1); // 设置第二个参数为1
    pst.setString(3, "second"); // 设置第三个参数为"second"
    pst.setInt(4, 1); // 设置第四个参数为1
    pst.setInt(5, 1); // 设置第五个参数为1
    pst.addBatch(); // 添加批量操作
    pst.addBatch(); // 再次添加批量操作

    int[] updateCounts = pst.executeBatch(); // 执行批量操作
    assertThat(updateCounts, primitiveArrayWithSize(2)); // 验证返回2个更新计数
    assertThat(updateCounts[0], is(1)); // 验证第一个操作更新了1行
    assertThat(updateCounts[1], is(1)); // 验证第二个操作更新了1行
    ResultSet resultSet = pst.getResultSet(); // 获取结果集
    assertThat(resultSet, nullValue()); // 验证结果集为null

    connection.close(); // 关闭连接
  }

  /**
   * Remote PreparedStatement insert WITH bind variables.
   * // 远程PreparedStatement插入，使用绑定变量。
   */
  @Test void testRemotePreparedStatementInsert2() { // 测试远程PreparedStatement插入（使用绑定变量）的测试方法
  } // 测试方法体为空，待实现
} // 类定义结束