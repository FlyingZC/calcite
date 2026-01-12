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
package org.apache.calcite.adapter.file; // 包声明：Calcite文件适配器测试类所在的包

import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，用于获取Calcite特定的连接功能
import org.apache.calcite.schema.Schema; // 导入模式接口，表示数据库模式
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入SQL到关系代数转换器，用于SQL优化
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试辅助方法

import com.google.common.collect.ImmutableMap; // 导入不可变Map类，用于创建不可修改的映射

import org.junit.jupiter.api.Disabled; // 导入禁用测试注解，用于标记暂时禁用的测试
import org.junit.jupiter.api.Test; // 导入测试注解，用于标记测试方法
import org.junit.jupiter.api.extension.ExtendWith; // 导入扩展注解，用于注册JUnit扩展
import org.junit.jupiter.params.ParameterizedTest; // 导入参数化测试注解，用于运行多次测试
import org.junit.jupiter.params.provider.MethodSource; // 导入方法源注解，用于提供测试参数

import java.math.BigDecimal; // 导入BigDecimal类，用于精确的十进制运算
import java.sql.Connection; // 导入连接接口，表示数据库连接
import java.sql.Date; // 导入日期类，表示SQL日期
import java.sql.DriverManager; // 导入驱动管理器类，用于获取数据库连接
import java.sql.PreparedStatement; // 导入预处理语句接口，用于执行参数化SQL
import java.sql.ResultSet; // 导入结果集接口，表示查询结果
import java.sql.ResultSetMetaData; // 导入结果集元数据接口，提供结果集的结构信息
import java.sql.SQLException; // 导入SQL异常类，表示SQL操作中的异常
import java.sql.Statement; // 导入语句接口，用于执行静态SQL语句
import java.sql.Time; // 导入时间类，表示SQL时间
import java.sql.Timestamp; // 导入时间戳类，表示SQL时间戳
import java.sql.Types; // 导入类型类，定义SQL类型的常量
import java.util.Properties; // 导入属性类，用于存储键值对配置
import java.util.function.Consumer; // 导入消费者函数接口，用于处理结果集
import java.util.stream.Stream; // 导入流接口，用于支持流式操作

import static org.apache.calcite.adapter.file.FileAdapterTests.sql; // 静态导入FileAdapterTests的sql方法，用于简化SQL测试

import static org.hamcrest.CoreMatchers.equalTo; // 静态导入equalTo匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器，用于断言
import static org.hamcrest.CoreMatchers.isA; // 静态导入isA匹配器，用于断言类型
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat方法，用于断言
import static org.junit.jupiter.api.Assertions.assertFalse; // 静态导入assertFalse方法，用于断言为假

/**
 * System test of the Calcite file adapter, which can read and parse
 * HTML tables over HTTP, and also read CSV and JSON files from the filesystem.
 * // Calcite文件适配器的系统测试类，用于测试Calcite文件适配器的各种功能
 * // 该适配器能够通过HTTP读取和解析HTML表格，并从文件系统读取CSV和JSON文件
 * // 测试覆盖了多种数据格式、查询操作、类型转换、优化规则等方面
 * // 主要测试场景包括：本地文件读取、URL读取、CSV/JSON格式处理、数据类型转换、
 * // 查询优化（投影下推、过滤下推）、聚合操作、连接操作、日期时间处理等
 * // 使用JUnit 5进行测试， RequiresNetworkExtension用于需要网络访问的测试
 */
@ExtendWith(RequiresNetworkExtension.class)
class FileAdapterTest {

  static Stream<String> explainFormats() {
    return Stream.of("text", "dot"); // 返回包含两种explain格式的Stream："text"文本格式和"dot"图形格式，用于参数化测试
  }

  /** Reads from a local file and checks the result. */
  @Test void testFileSelect() { // 测试从本地文件读取数据并验证结果
    final String sql = "select H1 from T1 where H0 = 'R1C0'"; // 构建SQL查询语句，从T1表中选择H1列，条件是H0列等于'R1C0'
    sql("testModel", sql).returns("H1=R1C1").ok(); // 执行SQL查询，期望返回结果为"H1=R1C1"，验证查询正确性
  }

  /** Reads from a local file without table headers &lt;TH&gt; and checks the
   * result. */
  @Test @RequiresNetwork void testNoThSelect() { // 测试从没有表头<TH>的本地文件读取数据并验证结果，需要网络访问
    final String sql = "select \"col1\" from T1_NO_TH where \"col0\" like 'R0%'"; // 构建SQL查询，从T1_NO_TH表选择col1列，条件是col0以'R0'开头
    sql("testModel", sql).returns("col1=R0C1").ok(); // 执行查询，期望返回"col1=R0C1"，验证无表头文件的读取能力
  }

  /** Reads from a local file - finds larger table even without &lt;TH&gt;
   * elements. */
  @Test void testFindBiggerNoTh() { // 测试从本地文件读取数据，即使没有<TH>元素也能找到更大的表格
    final String sql = "select \"col4\" from TABLEX2 where \"col0\" like 'R1%'"; // 构建SQL查询，从TABLEX2表选择col4列，条件是col0以'R1'开头
    sql("testModel", sql).returns("col4=R1C4").ok(); // 执行查询，期望返回"col4=R1C4"，验证系统能够识别无表头的大表格
  }

  /** Reads from a URL and checks the result. */
  @Disabled("[CALCITE-1789] Wikipedia format change breaks file adapter test") // 禁用此测试，因为Wikipedia格式变更导致文件适配器测试失败
  @Test @RequiresNetwork void testUrlSelect() { // 测试从URL读取数据并验证结果，需要网络访问
    final String sql = "select \"State\", \"Statehood\" from \"States_as_of\"\n" // 构建SQL查询，从States_as_of表选择State和Statehood列
        + "where \"State\" = 'California'"; // 条件是State等于'California'
    sql("wiki", sql).returns("State=California; Statehood=1850-09-09").ok(); // 执行查询，期望返回加利福尼亚州的州名和加入联邦日期
  }

  /** Reads the EMPS table. */
  @Test void testSalesEmps() { // 测试读取EMPS（员工）表的所有数据
    final String sql = "select * from sales.emps"; // 构建SQL查询，从sales.emps表选择所有列
    sql("sales", sql) // 使用sales模型执行查询
        .returns("EMPNO=100; NAME=Fred; DEPTNO=30", // 期望返回第1条员工记录
            "EMPNO=110; NAME=Eric; DEPTNO=20", // 期望返回第2条员工记录
            "EMPNO=110; NAME=John; DEPTNO=40", // 期望返回第3条员工记录
            "EMPNO=120; NAME=Wilma; DEPTNO=20", // 期望返回第4条员工记录
            "EMPNO=130; NAME=Alice; DEPTNO=40") // 期望返回第5条员工记录
        .ok(); // 验证查询成功
  }

  /** Reads the DEPTS table. */
  @Test void testSalesDepts() { // 测试读取DEPTS（部门）表的所有数据
    final String sql = "select * from sales.depts"; // 构建SQL查询，从sales.depts表选择所有列
    sql("sales", sql) // 使用sales模型执行查询
        .returns("DEPTNO=10; NAME=Sales", // 期望返回第1个部门记录
            "DEPTNO=20; NAME=Marketing", // 期望返回第2个部门记录
            "DEPTNO=30; NAME=Accounts") // 期望返回第3个部门记录
        .ok(); // 验证查询成功
  }

  /** Reads the DEPTS table from the CSV schema. */
  @Test void testCsvSalesDepts() { // 测试从CSV模式读取DEPTS（部门）表的所有数据
    final String sql = "select * from sales.depts"; // 构建SQL查询，从sales.depts表选择所有列
    sql("sales-csv", sql) // 使用sales-csv模型执行查询，测试CSV格式适配器
        .returns("DEPTNO=10; NAME=Sales", // 期望返回第1个部门记录
            "DEPTNO=20; NAME=Marketing", // 期望返回第2个部门记录
            "DEPTNO=30; NAME=Accounts") // 期望返回第3个部门记录
        .ok(); // 验证CSV格式的部门数据读取成功
  }

  /** Reads the EMPS table from the CSV schema. */
  @Test void testCsvSalesEmps() { // 测试从CSV模式读取EMPS（员工）表的所有数据
    final String sql = "select * from sales.emps"; // 构建SQL查询，从sales.emps表选择所有列
    final String[] lines = { // 定义期望返回的员工记录数组
        "EMPNO=100; NAME=Fred; DEPTNO=10; GENDER=; CITY=; EMPID=30; AGE=25; SLACKER=true; MANAGER=false; JOINEDAT=1996-08-03", // 第1条员工记录，包含所有字段
        "EMPNO=110; NAME=Eric; DEPTNO=20; GENDER=M; CITY=San Francisco; EMPID=3; AGE=80; SLACKER=null; MANAGER=false; JOINEDAT=2001-01-01", // 第2条员工记录
        "EMPNO=110; NAME=John; DEPTNO=40; GENDER=M; CITY=Vancouver; EMPID=2; AGE=null; SLACKER=false; MANAGER=true; JOINEDAT=2002-05-03", // 第3条员工记录
        "EMPNO=120; NAME=Wilma; DEPTNO=20; GENDER=F; CITY=; EMPID=1; AGE=5; SLACKER=null; MANAGER=true; JOINEDAT=2005-09-07", // 第4条员工记录
        "EMPNO=130; NAME=Alice; DEPTNO=40; GENDER=F; CITY=Vancouver; EMPID=2; AGE=null; SLACKER=false; MANAGER=true; JOINEDAT=2007-01-01", // 第5条员工记录
    };
    sql("sales-csv", sql).returns(lines).ok(); // 使用sales-csv模型执行查询，验证CSV格式的员工数据读取成功
  }

  /** Reads the HEADER_ONLY table from the CSV schema. The CSV file has one
   * line - the column headers - but no rows of data. */
  @Test void testCsvSalesHeaderOnly() { // 测试从CSV模式读取只有表头的表，CSV文件只有一行列标题，没有数据行
    final String sql = "select * from sales.header_only"; // 构建SQL查询，从sales.header_only表选择所有列
    sql("sales-csv", sql).returns().ok(); // 执行查询，期望返回空结果集，验证只有表头的CSV文件处理正确
  }

  /** Reads the EMPTY table from the CSV schema. The CSV file has no lines,
   * therefore the table has a system-generated column called
   * "EmptyFileHasNoColumns". */
  @Test void testCsvSalesEmpty() { // 测试从CSV模式读取空表，CSV文件没有任何行，因此表有一个系统生成的列"EmptyFileHasNoColumns"
    final String sql = "select * from sales.\"EMPTY\""; // 构建SQL查询，从sales.EMPTY表选择所有列
    sql("sales-csv", sql) // 使用sales-csv模型执行查询
        .checking(FileAdapterTest::checkEmpty) // 使用checkEmpty方法验证结果集元数据
        .ok(); // 验证空CSV文件的处理正确
  }

  private static void checkEmpty(ResultSet resultSet) { // 验证空表结果集的私有静态方法
    try {
      final ResultSetMetaData metaData = resultSet.getMetaData(); // 获取结果集的元数据信息
      assertThat(metaData.getColumnCount(), is(1)); // 验证列数为1
      assertThat(metaData.getColumnName(1), is("EmptyFileHasNoColumns")); // 验证第1列的名称为系统生成的"EmptyFileHasNoColumns"
      assertThat(metaData.getColumnType(1), is(Types.BOOLEAN)); // 验证第1列的数据类型为BOOLEAN
      String actual = FileAdapterTests.toString(resultSet); // 将结果集转换为字符串
      assertThat(actual, is("")); // 验证结果为空字符串，表示没有数据行
    } catch (SQLException e) { // 捕获SQL异常
      throw TestUtil.rethrow(e); // 重新抛出异常
    }
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1754">[CALCITE-1754]
   * In Csv adapter, convert DATE and TIME values to int, and TIMESTAMP values
   * to long</a>. */
  @Test void testCsvGroupByTimestampAdd() { // 测试CSV适配器中DATE和TIME值转换为int，TIMESTAMP值转换为long的功能
    final String sql = "select count(*) as c,\n" // 构建SQL查询，统计数量并对时间戳加1天进行分组
        + "  {fn timestampadd(SQL_TSI_DAY, 1, JOINEDAT) } as t\n" // 使用SQL函数timestampadd在JOINEDAT上加1天
        + "from EMPS group by {fn timestampadd(SQL_TSI_DAY, 1, JOINEDAT ) } "; // 按加1天后的JOINEDAT分组
    sql("sales-csv", sql) // 使用sales-csv模型执行查询
        .returnsUnordered("C=1; T=1996-08-04", // 期望返回第一条结果（顺序不限）
            "C=1; T=2002-05-04", // 期望返回第二条结果
            "C=1; T=2005-09-08", // 期望返回第三条结果
            "C=1; T=2007-01-02", // 期望返回第四条结果
            "C=1; T=2001-01-02") // 期望返回第五条结果
        .ok(); // 验证查询成功
    final String sql2 = "select count(*) as c,\n" // 构建第二个SQL查询，统计数量并对时间戳加1个月进行分组
        + "  {fn timestampadd(SQL_TSI_MONTH, 1, JOINEDAT) } as t\n" // 使用SQL函数timestampadd在JOINEDAT上加1个月
        + "from EMPS group by {fn timestampadd(SQL_TSI_MONTH, 1, JOINEDAT ) } "; // 按加1个月后的JOINEDAT分组
    sql("sales-csv", sql2) // 使用sales-csv模型执行查询
        .returnsUnordered("C=1; T=2002-06-03", // 期望返回第一条结果
            "C=1; T=2005-10-07", // 期望返回第二条结果
            "C=1; T=2007-02-01", // 期望返回第三条结果
            "C=1; T=2001-02-01", // 期望返回第四条结果
            "C=1; T=1996-09-03").ok(); // 期望返回第五条结果，验证查询成功
    final String sql3 = "select\n" // 构建第三个SQL查询，选择去重的时间戳加1个月
        + " distinct {fn timestampadd(SQL_TSI_MONTH, 1, JOINEDAT) } as t\n" // 使用distinct去重
        + "from EMPS"; // 从EMPS表查询
    sql("sales-csv", sql3) // 使用sales-csv模型执行查询
        .returnsUnordered("T=2002-06-03", // 期望返回第一个时间戳（顺序不限）
            "T=2005-10-07", // 期望返回第二个时间戳
            "T=2007-02-01", // 期望返回第三个时间戳
            "T=2001-02-01", // 期望返回第四个时间戳
            "T=1996-09-03").ok(); // 期望返回第五个时间戳，验证DISTINCT功能
  }

  /** Reads the DEPTS table from the JSON schema. */
  @Test void testJsonSalesDepts() { // 测试从JSON模式读取DEPTS（部门）表的所有数据
    final String sql = "select * from sales.depts"; // 构建SQL查询，从sales.depts表选择所有列
    sql("sales-json", sql) // 使用sales-json模型执行查询，测试JSON格式适配器
        .returns("DEPTNO=10; NAME=Sales", // 期望返回第1个部门记录
            "DEPTNO=20; NAME=Marketing", // 期望返回第2个部门记录
            "DEPTNO=30; NAME=Accounts") // 期望返回第3个部门记录
        .ok(); // 验证JSON格式的部门数据读取成功
  }

  /** Reads the EMPS table from the JSON schema. */
  @Test void testJsonSalesEmps() { // 测试从JSON模式读取EMPS（员工）表的所有数据
    final String sql = "select * from sales.emps"; // 构建SQL查询，从sales.emps表选择所有列
    final String[] lines = { // 定义期望返回的员工记录数组
        "EMPNO=100; NAME=Fred; DEPTNO=10; GENDER=; CITY=; EMPID=30; AGE=25; SLACKER=true; MANAGER=false; JOINEDAT=1996-08-03", // 第1条员工记录
        "EMPNO=110; NAME=Eric; DEPTNO=20; GENDER=M; CITY=San Francisco; EMPID=3; AGE=80; SLACKER=null; MANAGER=false; JOINEDAT=2001-01-01", // 第2条员工记录
        "EMPNO=110; NAME=John; DEPTNO=40; GENDER=M; CITY=Vancouver; EMPID=2; AGE=null; SLACKER=false; MANAGER=true; JOINEDAT=2002-05-03", // 第3条员工记录
        "EMPNO=120; NAME=Wilma; DEPTNO=20; GENDER=F; CITY=; EMPID=1; AGE=5; SLACKER=null; MANAGER=true; JOINEDAT=2005-09-07", // 第4条员工记录
        "EMPNO=130; NAME=Alice; DEPTNO=40; GENDER=F; CITY=Vancouver; EMPID=2; AGE=null; SLACKER=false; MANAGER=true; JOINEDAT=2007-01-01", // 第5条员工记录
    };
    sql("sales-json", sql).returns(lines).ok(); // 使用sales-json模型执行查询，验证JSON格式的员工数据读取成功
  }

  /** Reads the EMPTY table from the JSON schema. The JSON file has no lines,
   * therefore the table has a system-generated column called
   * "EmptyFileHasNoColumns". */
  @Test void testJsonSalesEmpty() { // 测试从JSON模式读取空表，JSON文件没有任何行，因此表有一个系统生成的列"EmptyFileHasNoColumns"
    final String sql = "select * from sales.\"EMPTY\""; // 构建SQL查询，从sales.EMPTY表选择所有列
    sql("sales-json", sql) // 使用sales-json模型执行查询
        .checking(FileAdapterTest::checkEmpty) // 使用checkEmpty方法验证结果集元数据
        .ok(); // 验证空JSON文件的处理正确
  }

  /** Test returns the result of two json file joins. */
  @Test void testJsonJoinOnString() { // 测试两个JSON文件连接操作的结果
    final String sql = "select emps.EMPNO, emps.NAME, depts.deptno from emps\n" // 构建SQL查询，选择员工的EMPNO、NAME和部门的deptno
        + "join depts on emps.deptno = depts.deptno"; // 通过deptno连接emps和depts表
    final String[] lines = { // 定义期望返回的连接结果
        "EMPNO=100; NAME=Fred; DEPTNO=10", // 第1条连接结果
        "EMPNO=110; NAME=Eric; DEPTNO=20", // 第2条连接结果
        "EMPNO=120; NAME=Wilma; DEPTNO=20", // 第3条连接结果
    };
    sql("sales-json", sql).returns(lines).ok(); // 使用sales-json模型执行查询，验证JSON文件的连接操作
  }

  /** The folder contains both JSON files and CSV files joins. */
  @Test void testJsonWithCsvJoin() { // 测试包含JSON文件和CSV文件的混合连接操作
    final String sql = "select emps.empno,\n" // 构建SQL查询，选择员工的empno
        + " NAME,\n" // 选择员工的name
        + " \"DATE\".JOINEDAT\n" // 选择DATE表的JOINEDAT字段
        + " from \"DATE\"\n" // 从DATE表开始
        + "join emps on emps.empno = \"DATE\".EMPNO\n" // 通过empno连接emps表和DATE表
        + "order by empno, name, joinedat limit 3"; // 按empno、name、joinedat排序并限制返回3条
    final String[] lines = { // 定义期望返回的连接结果
        "EMPNO=100; NAME=Fred; JOINEDAT=1996-08-03", // 第1条连接结果
        "EMPNO=110; NAME=Eric; JOINEDAT=2001-01-01", // 第2条连接结果
        "EMPNO=110; NAME=Eric; JOINEDAT=2002-05-03", // 第3条连接结果
    };
    sql("sales-json", sql) // 使用sales-json模型执行查询
        .returns(lines) // 验证返回的结果
        .ok(); // 验证JSON和CSV文件的混合连接操作成功
  }

  /** Tests an inline schema with a non-existent directory. */
  @Test void testBadDirectory() throws SQLException { // 测试使用不存在的目录的内联模式
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", // 设置模型配置为内联JSON格式
        "inline:"
            + "{\n"
            + "  version: '1.0',\n" // 模型版本号
            + "   schemas: [\n" // 模式数组开始
            + "     {\n" // 第一个模式定义
            + "       type: 'custom',\n" // 模式类型为自定义
            + "       name: 'bad',\n" // 模式名称为'bad'
            + "       factory: 'org.apache.calcite.adapter.file.FileSchemaFactory',\n" // 使用文件模式工厂
            + "       operand: {\n" // 操作数开始
            + "         directory: '/does/not/exist'\n" // 指定一个不存在的目录路径
            + "       }\n" // 操作数结束
            + "     }\n" // 模式定义结束
            + "   ]\n" // 模式数组结束
            + "}"); // JSON结束

    Connection connection = // 获取Calcite连接
        DriverManager.getConnection("jdbc:calcite:", info); // 使用JDBC驱动管理器获取连接
    // must print "directory ... not found" to stdout, but not fail // 必须向stdout打印"directory ... not found"，但不能失败
    ResultSet tables = // 获取结果集
        connection.getMetaData().getTables(null, null, null, null); // 获取所有表的元数据
    tables.next(); // 移动到第一行
    tables.close(); // 关闭结果集
    connection.close(); // 关闭连接
  }

  /**
   * Reads from a table.
   */
  @Test void testSelect() { // 测试从表中读取数据
    sql("model", "select * from EMPS").ok(); // 执行查询，从EMPS表选择所有列，验证查询成功
  }

  @Test void testSelectSingleProjectGz() { // 测试从gzip压缩文件中选择单个列
    sql("smart", "select name from EMPS").ok(); // 执行查询，从EMPS表选择name列，验证压缩文件的读取能力
  }

  @Test void testSelectSingleProject() { // 测试从表中选择单个列（投影优化）
    sql("smart", "select name from DEPTS").ok(); // 执行查询，从DEPTS表选择name列，验证投影下推优化
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-898">[CALCITE-898]
   * Type inference multiplying Java long by SQL INTEGER</a>. */
  @Test void testSelectLongMultiplyInteger() { // 测试Java long类型与SQL INTEGER类型相乘的类型推断
    final String sql = "select empno * 3 as e3\n" // 构建SQL查询，将empno乘以3并别名为e3
        + "from long_emps where empno = 100"; // 从long_emps表查询，条件是empno等于100

    sql("bug", sql).checking(resultSet -> { // 使用bug模型执行查询并验证结果
      try {
        assertThat(resultSet.next(), is(true)); // 验证有第一行结果
        Long o = (Long) resultSet.getObject(1); // 获取第一列的值，期望是Long类型
        assertThat(o, is(300L)); // 验证值为300L（100 * 3）
        assertThat(resultSet.next(), is(false)); // 验证只有一行结果
      } catch (SQLException e) { // 捕获SQL异常
        throw TestUtil.rethrow(e); // 重新抛出异常
      }
    }).ok(); // 验证查询成功
  }

  @Test void testCustomTable() { // 测试自定义表功能
    sql("model-with-custom-table", "select * from CUSTOM_TABLE.EMPS").ok(); // 执行查询，从CUSTOM_TABLE.EMPS表选择所有列，验证自定义表功能
  }

  @Test void testPushDownProject() { // 测试投影下推优化（选择所有列）
    final String sql = "explain plan for select * from EMPS"; // 构建SQL查询，解释EMPS表选择所有列的执行计划
    final String expected = "PLAN=CsvTableScan(table=[[SALES, EMPS]], " // 期望的执行计划包含CsvTableScan
        + "fields=[[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]])\n"; // 字段索引列表，表示读取所有列
    sql("smart", sql).returns(expected).ok(); // 执行查询，验证投影下推到CSV表扫描
  }

  @Test void testPushDownProject2() { // 测试投影下推优化（选择部分列）
    sql("smart", "explain plan for select name, empno from EMPS") // 解释选择name和empno列的执行计划
        .returns("PLAN=CsvTableScan(table=[[SALES, EMPS]], fields=[[1, 0]])\n") // 期望只读取字段索引1和0（name和empno）
        .ok(); // 验证投影下推成功
    // make sure that it works... // 确保查询正常工作
    sql("smart", "select name, empno from EMPS") // 执行实际查询
        .returns("NAME=Fred; EMPNO=100", // 期望返回第1条结果
            "NAME=Eric; EMPNO=110", // 期望返回第2条结果
            "NAME=John; EMPNO=110", // 期望返回第3条结果
            "NAME=Wilma; EMPNO=120", // 期望返回第4条结果
            "NAME=Alice; EMPNO=130") // 期望返回第5条结果
        .ok(); // 验证查询结果正确
  }

  @ParameterizedTest // 参数化测试
  @MethodSource("explainFormats") // 使用explainFormats方法作为参数源
  void testPushDownProjectAggregate(String format) { // 测试投影下推和聚合操作的组合优化
    String expected = null; // 期望的执行计划结果
    String extra = null; // 额外的SQL格式说明
    switch (format) { // 根据格式类型设置期望结果
    case "dot": // DOT图形格式
      expected = "PLAN=digraph {\n" // 期望的DOT格式执行计划
          + "\"CsvTableScan\\ntable = [SALES, EMPS\\n]\\nfields = [3]\\n\" -> " // CSV表扫描节点，只读取字段3（gender）
          + "\"EnumerableAggregate\\ngroup = {0}\\nEXPR$1 = COUNT()\\n\" [label=\"0\"]\n" // 聚合节点，按gender分组并计数
          + "}\n"; // DOT格式结束
      extra = " as dot "; // 额外格式说明
      break; // 跳出switch
    case "text": // 文本格式
      expected = "PLAN=" // 期望的文本格式执行计划
          + "EnumerableAggregate(group=[{0}], EXPR$1=[COUNT()])\n" // 聚合操作，按第0列分组并计数
          + "  CsvTableScan(table=[[SALES, EMPS]], fields=[[3]])\n"; // CSV表扫描，只读取字段3
      extra = ""; // 无额外格式说明
      break; // 跳出switch
    }
    final String sql = "explain plan " + extra + " for\n" // 构建SQL查询，解释执行计划
        + "select gender, count(*) from EMPS group by gender"; // 按gender分组并统计数量
    sql("smart", sql).returns(expected).ok(); // 执行查询，验证投影下推和聚合优化的执行计划
  }

  @ParameterizedTest // 参数化测试
  @MethodSource("explainFormats") // 使用explainFormats方法作为参数源
  void testPushDownProjectAggregateWithFilter(String format) { // 测试投影下推、过滤和聚合操作的组合优化
    String expected = null; // 期望的执行计划结果
    String extra = null; // 额外的SQL格式说明
    switch (format) { // 根据格式类型设置期望结果
    case "dot": // DOT图形格式
      expected = "PLAN=digraph {\n" // 期望的DOT格式执行计划
          + "\"EnumerableCalc\\nexpr#0..1 = {inputs}\\nexpr#2 = 'F':VARCHAR\\nexpr#3 = =($t1, $t2)" // 计算节点，过滤gender='F'
          + "\\nproj#0..1 = {exprs}\\n$condition = $t3\" -> \"EnumerableAggregate\\ngroup = " // 连接到聚合节点
          + "{}\\nEXPR$0 = MAX($0)\\n\" [label=\"0\"]\n" // 聚合节点，计算最大值
          + "\"CsvTableScan\\ntable = [SALES, EMPS\\n]\\nfields = [0, 3]\\n\" -> " // CSV表扫描，只读取字段0和3
          + "\"EnumerableCalc\\nexpr#0..1 = {inputs}\\nexpr#2 = 'F':VARCHAR\\nexpr#3 = =($t1, $t2)" // 第二个计算节点
          + "\\nproj#0..1 = {exprs}\\n$condition = $t3\" [label=\"0\"]\n" // 连接到聚合节点
          + "}\n"; // DOT格式结束
      extra = " as dot "; // 额外格式说明
      break; // 跳出switch
    case "text": // 文本格式
      expected = "PLAN=" // 期望的文本格式执行计划
          + "EnumerableAggregate(group=[{}], EXPR$0=[MAX($0)])\n" // 聚合操作，计算最大值
          + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=['F':VARCHAR], " // 计算节点，过滤gender='F'
          + "expr#3=[=($t1, $t2)], proj#0..1=[{exprs}], $condition=[$t3])\n" // 计算条件和投影
          + "    CsvTableScan(table=[[SALES, EMPS]], fields=[[0, 3]])\n"; // CSV表扫描，只读取字段0和3
      extra = ""; // 无额外格式说明
      break; // 跳出switch
    }
    final String sql = "explain plan " + extra + " for\n" // 构建SQL查询，解释执行计划
        + "select max(empno) from EMPS where gender='F'"; // 查询女性员工的最大empno
    sql("smart", sql).returns(expected).ok(); // 执行查询，验证投影下推、过滤和聚合优化的执行计划
  }

  @ParameterizedTest // 参数化测试
  @MethodSource("explainFormats") // 使用explainFormats方法作为参数源
  void testPushDownProjectAggregateNested(String format) { // 测试嵌套聚合操作的投影下推优化
    String expected = null; // 期望的执行计划结果
    String extra = null; // 额外的SQL格式说明
    switch (format) { // 根据格式类型设置期望结果
    case "dot": // DOT图形格式
      expected = "PLAN=digraph {\n" // 期望的DOT格式执行计划
          + "\"EnumerableAggregate\\ngroup = {0, 1}\\nQTY = COUNT()\\n\" -> " // 内层聚合节点，按name和gender分组计数
          + "\"EnumerableAggregate\\ngroup = {1}\\nEXPR$1 = MAX($2)\\n\" [label=\"0\"]\n" // 外层聚合节点，按gender分组求最大值
          + "\"CsvTableScan\\ntable = [SALES, EMPS\\n]\\nfields = [1, 3]\\n\" -> " // CSV表扫描，只读取字段1和3（name和gender）
          + "\"EnumerableAggregate\\ngroup = {0, 1}\\nQTY = COUNT()\\n\" [label=\"0\"]\n" // 连接到内层聚合节点
          + "}\n"; // DOT格式结束
      extra = " as dot "; // 额外格式说明
      break; // 跳出switch
    case "text": // 文本格式
      expected = "PLAN=" // 期望的文本格式执行计划
          + "EnumerableAggregate(group=[{1}], EXPR$1=[MAX($2)])\n" // 外层聚合，按gender分组求qty的最大值
          + "  EnumerableAggregate(group=[{0, 1}], QTY=[COUNT()])\n" // 内层聚合，按name和gender分组计数
          + "    CsvTableScan(table=[[SALES, EMPS]], fields=[[1, 3]])\n"; // CSV表扫描，只读取字段1和3
      extra = ""; // 无额外格式说明
      break; // 跳出switch
    }
    final String sql = "explain plan " + extra + " for\n" // 构建SQL查询，解释执行计划
        + "select gender, max(qty)\n" // 选择gender和qty的最大值
        + "from (\n" // 从子查询中
        + "  select name, gender, count(*) qty\n" // 子查询：选择name、gender和计数
        + "  from EMPS\n" // 从EMPS表
        + "  group by name, gender) t\n" // 按name和gender分组
        + "group by gender"; // 外层按gender分组
    sql("smart", sql).returns(expected).ok(); // 执行查询，验证嵌套聚合和投影下推的执行计划
  }

  @Test void testFilterableSelect() { // 测试从可过滤表中选择数据
    sql("filterable-model", "select name from EMPS").ok(); // 执行查询，从EMPS表选择name列，验证可过滤表的基本查询
  }

  @Test void testFilterableSelectStar() { // 测试从可过滤表中选择所有列
    sql("filterable-model", "select * from EMPS").ok(); // 执行查询，从EMPS表选择所有列，验证可过滤表的星号查询
  }

  /** Filter that can be fully handled by CsvFilterableTable. */
  @Test void testFilterableWhere() { // 测试可以完全由CsvFilterableTable处理的过滤条件
    final String sql = // 构建SQL查询
        "select empno, gender, name from EMPS where name = 'John'"; // 选择empno、gender、name，条件是name等于'John'
    sql("filterable-model", sql) // 使用filterable-model执行查询
        .returns("EMPNO=110; GENDER=M; NAME=John").ok(); // 期望返回John的记录，验证过滤下推成功
  }

  /** Filter that can be partly handled by CsvFilterableTable. */
  @Test void testFilterableWhere2() { // 测试可以部分由CsvFilterableTable处理的过滤条件
    final String sql = "select empno, gender, name from EMPS\n" // 构建SQL查询，选择empno、gender、name
        + " where gender = 'F' and empno > 125"; // 条件是gender等于'F'且empno大于125
    sql("filterable-model", sql) // 使用filterable-model执行查询
        .returns("EMPNO=130; GENDER=F; NAME=Alice").ok(); // 期望返回Alice的记录，验证部分过滤下推成功
  }

  /** Filter that can be slightly handled by CsvFilterableTable. */
  @Test void testFilterableWhere3() { // 测试可以轻微由CsvFilterableTable处理的过滤条件
    final String sql = "select empno, gender, name from EMPS\n" // 构建SQL查询，选择empno、gender、name
        + " where gender <> 'M' and empno > 125"; // 条件是gender不等于'M'且empno大于125
    sql("filterable-model", sql) // 使用filterable-model执行查询
        .returns("EMPNO=130; GENDER=F; NAME=Alice") // 期望返回Alice的记录
        .ok(); // 验证轻微过滤下推成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2272">[CALCITE-2272]
   * Incorrect result for {@code name like '%E%' and city not like '%W%'}</a>.
   */
  @Test void testFilterableWhereWithNot1() { // 测试带有NOT LIKE的过滤条件（CALCITE-2272）
    sql("filterable-model", // 使用filterable-model执行查询
        "select name, empno from EMPS " // 选择name和empno
            + "where name like '%E%' and city not like '%W%' ") // 条件是name包含'E'且city不包含'W'
        .returns("NAME=Eric; EMPNO=110") // 期望返回Eric的记录
        .ok(); // 验证NOT LIKE过滤条件正确
  }

  /** Similar to {@link #testFilterableWhereWithNot1()};
   * But use the same column. */
  @Test void testFilterableWhereWithNot2() { // 类似于testFilterableWhereWithNot1，但使用同一列
    sql("filterable-model", // 使用filterable-model执行查询
        "select name, empno from EMPS " // 选择name和empno
            + "where name like '%i%' and name not like '%W%' ") // 条件是name包含'i'且name不包含'W'
        .returns("NAME=Eric; EMPNO=110", // 期望返回Eric的记录
            "NAME=Alice; EMPNO=130") // 期望返回Alice的记录
        .ok(); // 验证同一列上的LIKE和NOT LIKE过滤条件正确
  }

  @Test void testJson() { // 测试读取JSON格式的复杂嵌套数据
    final String sql = "select * from archers\n"; // 构建SQL查询，从archers表选择所有列
    final String[] lines = { // 定义期望返回的记录数组
        "id=19990101; dow=Friday; longDate=New Years Day; title=Tractor trouble.; " // 第1条记录
            + "characters=[Alice, Bob, Xavier]; script=Julian Hyde; summary=; " // 包含数组字段characters和lines
            + "lines=[Bob's tractor got stuck in a field., " // 数组字段lines的第一个元素
            + "Alice and Xavier hatch a plan to surprise Charlie.]", // 数组字段lines的第二个元素
        "id=19990103; dow=Sunday; longDate=Sunday 3rd January; " // 第2条记录
            + "title=Charlie's surprise.; characters=[Alice, Zebedee, Charlie, Xavier]; " // 包含更长的characters数组
            + "script=William Shakespeare; summary=; " // script字段
            + "lines=[Charlie is very surprised by Alice and Xavier's surprise plan.]", // 数组字段lines
    };
    sql("bug", sql) // 使用bug模型执行查询
        .returns(lines) // 验证返回的结果
        .ok(); // 验证JSON复杂嵌套数据的读取成功
  }

  @Test void testJoinOnString() { // 测试基于字符串字段的连接操作
    final String sql = "select * from emps\n" // 构建SQL查询，从emps表选择所有列
        + "join depts on emps.name = depts.name"; // 通过name字段连接emps和depts表
    sql("smart", sql).ok(); // 执行查询，验证字符串字段的连接操作成功
  }

  @Test void testWackyColumns() { // 测试处理奇怪的列名（包含空格、数字、大小写混合等）
    final String sql = "select * from wacky_column_names where false"; // 构建SQL查询，从wacky_column_names表选择所有列，条件为false（不返回数据）
    sql("bug", sql).returns().ok(); // 执行查询，验证奇怪列名的表可以正常访问

    final String sql2 = "select \"joined at\", \"naME\"\n" // 构建第二个SQL查询，选择带空格和大小写混合的列名
        + "from wacky_column_names\n" // 从wacky_column_names表
        + "where \"2gender\" = 'F'"; // 条件是带数字前缀的列名等于'F'
    sql("bug", sql2) // 使用bug模型执行查询
        .returns("joined at=2005-09-07; naME=Wilma", // 期望返回Wilma的记录
            "joined at=2007-01-01; naME=Alice") // 期望返回Alice的记录
        .ok(); // 验证奇怪列名的查询成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1754">[CALCITE-1754]
   * In Csv adapter, convert DATE and TIME values to int, and TIMESTAMP values
   * to long</a>. */
  @Test void testGroupByTimestampAdd() { // 测试CSV适配器中时间戳加法操作的分组功能
    final String sql = "select count(*) as c,\n" // 构建SQL查询，统计数量并对时间戳加1天进行分组
        + "  {fn timestampadd(SQL_TSI_DAY, 1, JOINEDAT) } as t\n" // 使用SQL函数timestampadd在JOINEDAT上加1天
        + "from EMPS group by {fn timestampadd(SQL_TSI_DAY, 1, JOINEDAT ) } "; // 按加1天后的JOINEDAT分组
    sql("model", sql) // 使用model执行查询
        .returnsUnordered("C=1; T=1996-08-04", // 期望返回第一条结果（顺序不限）
            "C=1; T=2002-05-04", // 期望返回第二条结果
            "C=1; T=2005-09-08", // 期望返回第三条结果
            "C=1; T=2007-01-02", // 期望返回第四条结果
            "C=1; T=2001-01-02") // 期望返回第五条结果
        .ok(); // 验证查询成功

    final String sql2 = "select count(*) as c,\n" // 构建第二个SQL查询，统计数量并对时间戳加1个月进行分组
        + "  {fn timestampadd(SQL_TSI_MONTH, 1, JOINEDAT) } as t\n" // 使用SQL函数timestampadd在JOINEDAT上加1个月
        + "from EMPS group by {fn timestampadd(SQL_TSI_MONTH, 1, JOINEDAT ) } "; // 按加1个月后的JOINEDAT分组
    sql("model", sql2) // 使用model执行查询
        .returnsUnordered("C=1; T=2002-06-03", // 期望返回第一条结果
            "C=1; T=2005-10-07", // 期望返回第二条结果
            "C=1; T=2007-02-01", // 期望返回第三条结果
            "C=1; T=2001-02-01", // 期望返回第四条结果
            "C=1; T=1996-09-03") // 期望返回第五条结果，验证查询成功
        .ok();
  }

  @Test void testUnionGroupByWithoutGroupKey() { // 测试UNION操作结合GROUP BY
    final String sql = "select count(*) as c1 from EMPS group by NAME\n" // 第一个子查询：按NAME分组统计数量
        + "union\n" // UNION操作
        + "select count(*) as c1 from EMPS group by NAME"; // 第二个子查询：按NAME分组统计数量
    sql("model", sql).ok(); // 执行查询，验证UNION与GROUP BY的组合操作成功
  }

  @Test void testBoolean() { // 测试布尔类型的过滤条件
    sql("smart", "select empno, slacker from emps where slacker") // 构建SQL查询，选择empno和slacker，条件是slacker为true
        .returns("EMPNO=100; SLACKER=true").ok(); // 期望返回slacker为true的记录，验证布尔类型过滤
  }

  @Test void testReadme() { // 测试README中的示例查询
    final String sql = "SELECT d.name, COUNT(*) cnt" // 构建SQL查询，选择部门名称和员工数量
        + " FROM emps AS e" // 从emps表（别名为e）
        + " JOIN depts AS d ON e.deptno = d.deptno" // 通过deptno连接emps和depts表
        + " GROUP BY d.name"; // 按部门名称分组
    sql("smart", sql) // 使用smart模型执行查询
        .returns("NAME=Sales; CNT=1", "NAME=Marketing; CNT=2").ok(); // 期望返回Sales部门1人，Marketing部门2人
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-824">[CALCITE-824]
   * Type inference when converting IN clause to semijoin</a>. */
  @Test void testInToSemiJoinWithCast() { // 测试IN子句转换为半连接时的类型推断
    // Note that the IN list needs at least 20 values to trigger the rewrite // 注意：IN列表需要至少20个值才能触发重写
    // to a semijoin. Try it both ways. // 转换为半连接。尝试两种方式。
    final String sql = "SELECT e.name\n" // 构建SQL查询，选择员工姓名
        + "FROM emps AS e\n" // 从emps表（别名为e）
        + "WHERE cast(e.empno as bigint) in "; // 条件是empno转换为bigint后在IN列表中
    final int threshold = SqlToRelConverter.DEFAULT_IN_SUB_QUERY_THRESHOLD; // 获取IN子查询重写的默认阈值
    sql("smart", sql + range(130, threshold - 5)) // 使用低于阈值的IN列表
        .returns("NAME=Alice").ok(); // 期望返回Alice，验证类型转换正确
    sql("smart", sql + range(130, threshold)) // 使用等于阈值的IN列表
        .returns("NAME=Alice").ok(); // 期望返回Alice，验证半连接重写触发
    sql("smart", sql + range(130, threshold + 1000)) // 使用远高于阈值的IN列表
        .returns("NAME=Alice").ok(); // 期望返回Alice，验证大量IN值的处理
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1051">[CALCITE-1051]
   * Underflow exception due to scaling IN clause literals</a>. */
  @Test void testInToSemiJoinWithoutCast() { // 测试IN子句无类型转换时的半连接重写
    final String sql = "SELECT e.name\n" // 构建SQL查询，选择员工姓名
        + "FROM emps AS e\n" // 从emps表（别名为e）
        + "WHERE e.empno in " // 条件是empno在IN列表中
        + range(130, SqlToRelConverter.DEFAULT_IN_SUB_QUERY_THRESHOLD); // 使用默认阈值的IN列表
    sql("smart", sql).returns("NAME=Alice").ok(); // 期望返回Alice，验证无类型转换的IN子句处理
  }

  private String range(int first, int count) { // 生成IN列表的辅助方法
    final StringBuilder sb = new StringBuilder(); // 创建StringBuilder对象
    for (int i = 0; i < count; i++) { // 循环count次
      sb.append(i == 0 ? "(" : ", ").append(first + i); // 第一次添加"("，否则添加"，"，然后添加数值
    }
    return sb.append(')').toString(); // 添加")"并返回字符串
  }

  @Test void testDecimalType() { // 测试DECIMAL（十进制）数据类型
    sql("sales-csv", "select BUDGET from sales.\"DECIMAL\"") // 构建SQL查询，从DECIMAL表选择BUDGET列
        .checking(resultSet -> { // 验证结果集元数据
          try {
            ResultSetMetaData metaData = resultSet.getMetaData(); // 获取结果集元数据
            assertThat(metaData.getColumnTypeName(1), is("DECIMAL")); // 验证列类型名为DECIMAL
            assertThat(metaData.getPrecision(1), is(18)); // 验证精度为18
            assertThat(metaData.getScale(1), is(2)); // 验证小数位数为2
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        })
        .ok(); // 验证DECIMAL类型正确
  }

  @Test void testDecimalTypeArithmeticOperations() { // 测试DECIMAL类型的算术运算
    sql("sales-csv", "select BUDGET + 100.0 from sales.\"DECIMAL\" where DEPTNO = 10") // 测试加法运算
        .checking(resultSet -> { // 验证结果
          try {
            resultSet.next(); // 移动到第一行
            final BigDecimal bd200 = new BigDecimal("200"); // 期望结果为200
            assertThat(resultSet.getBigDecimal(1).compareTo(bd200), is(0)); // 验证加法结果正确
            assertFalse(resultSet.next()); // 验证只有一行结果
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        })
        .ok(); // 验证加法运算成功
    sql("sales-csv", "select BUDGET - 100.0 from sales.\"DECIMAL\" where DEPTNO = 10") // 测试减法运算
        .checking(resultSet -> { // 验证结果
          try {
            resultSet.next(); // 移动到第一行
            final BigDecimal bd0 = BigDecimal.ZERO; // 期望结果为0
            assertThat(resultSet.getBigDecimal(1).compareTo(bd0), is(0)); // 验证减法结果正确
            assertFalse(resultSet.next()); // 验证只有一行结果
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        })
        .ok(); // 验证减法运算成功
    sql("sales-csv", "select BUDGET * 0.01 from sales.\"DECIMAL\" where DEPTNO = 10") // 测试乘法运算
        .checking(resultSet -> { // 验证结果
          try {
            resultSet.next(); // 移动到第一行
            final BigDecimal bd1 = new BigDecimal("1"); // 期望结果为1
            assertThat(resultSet.getBigDecimal(1).compareTo(bd1), is(0)); // 验证乘法结果正确
            assertFalse(resultSet.next()); // 验证只有一行结果
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        })
        .ok(); // 验证乘法运算成功
    sql("sales-csv", "select BUDGET / 100 from sales.\"DECIMAL\" where DEPTNO = 10") // 测试除法运算
        .checking(resultSet -> { // 验证结果
          try {
            resultSet.next(); // 移动到第一行
            final BigDecimal bd1 = new BigDecimal("1"); // 期望结果为1
            assertThat(resultSet.getBigDecimal(1).compareTo(bd1), is(0)); // 验证除法结果正确
            assertFalse(resultSet.next()); // 验证只有一行结果
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 重新抛出异常
          }
        })
        .ok(); // 验证除法运算成功
  }

  @Test void testDateType() throws SQLException { // 测试日期时间类型（DATE、TIME、TIMESTAMP）
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug

    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 使用JDBC驱动管理器获取连接
      ResultSet res = // 获取结果集
          connection.getMetaData().getColumns(null, null, // 获取列元数据
              "DATE", "JOINEDAT"); // 查询DATE表的JOINEDAT列
      res.next(); // 移动到第一行
      assertThat(Types.DATE, is(res.getInt("DATA_TYPE"))); // 验证数据类型为DATE

      res = // 获取结果集
          connection.getMetaData().getColumns(null, null, // 获取列元数据
              "DATE", "JOINTIME"); // 查询DATE表的JOINTIME列
      res.next(); // 移动到第一行
      assertThat(Types.TIME, is(res.getInt("DATA_TYPE"))); // 验证数据类型为TIME

      res = // 获取结果集
          connection.getMetaData().getColumns(null, null, // 获取列元数据
              "DATE", "JOINTIMES"); // 查询DATE表的JOINTIMES列
      res.next(); // 移动到第一行
      assertThat(Types.TIMESTAMP, is(res.getInt("DATA_TYPE"))); // 验证数据类型为TIMESTAMP

      Statement statement = connection.createStatement(); // 创建语句对象
      final String sql = "select \"JOINEDAT\", \"JOINTIME\", \"JOINTIMES\" " // 构建SQL查询
          + "from \"DATE\" where EMPNO = 100"; // 选择日期时间列，条件是EMPNO等于100
      ResultSet resultSet = statement.executeQuery(sql); // 执行查询
      resultSet.next(); // 移动到第一行

      // date // 验证DATE类型
      assertThat(resultSet.getDate(1).getClass(), is(Date.class)); // 验证返回Date类
      assertThat(resultSet.getDate(1), is(Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 验证TIME类型
      assertThat(resultSet.getTime(2).getClass(), is(Time.class)); // 验证返回Time类
      assertThat(resultSet.getTime(2), is(Time.valueOf("00:01:02"))); // 验证时间值

      // timestamp // 验证TIMESTAMP类型
      assertThat(resultSet.getTimestamp(3).getClass(), is(Timestamp.class)); // 验证返回Timestamp类
      assertThat(resultSet.getTimestamp(3), // 验证时间戳值
          is(Timestamp.valueOf("1996-08-03 00:01:02")));
    } // 自动关闭连接
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1072">[CALCITE-1072]
   * CSV adapter incorrectly parses TIMESTAMP values after noon</a>. */
  @Test void testDateType2() throws SQLException { // 测试CSV适配器正确解析中午之后的时间戳值
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug

    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 使用JDBC驱动管理器获取连接
      Statement statement = connection.createStatement(); // 创建语句对象
      final String sql = "select * from \"DATE\"\n" // 构建SQL查询
          + "where EMPNO >= 140 and EMPNO < 200"; // 选择所有列，条件是EMPNO在140到200之间
      ResultSet resultSet = statement.executeQuery(sql); // 执行查询
      int n = 0; // 计数器
      while (resultSet.next()) { // 遍历结果集
        ++n; // 计数加1
        final int empId = resultSet.getInt(1); // 获取EMPNO
        final String date = resultSet.getString(2); // 获取日期
        final String time = resultSet.getString(3); // 获取时间
        final String timestamp = resultSet.getString(4); // 获取时间戳
        assertThat(date, is("2015-12-31")); // 验证日期值
        switch (empId) { // 根据EMPNO验证时间值
        case 140: // 如果是140
          assertThat(time, is("07:15:56")); // 验证时间值（上午）
          assertThat(timestamp, is("2015-12-31 07:15:56")); // 验证时间戳值
          break; // 跳出switch
        case 150: // 如果是150
          assertThat(time, is("13:31:21")); // 验证时间值（下午）
          assertThat(timestamp, is("2015-12-31 13:31:21")); // 验证时间戳值
          break; // 跳出switch
        default: // 其他情况
          throw new AssertionError(); // 抛出断言错误
        }
      }
      assertThat(n, is(2)); // 验证有2条记录
      resultSet.close(); // 关闭结果集
      statement.close(); // 关闭语句
    } // 自动关闭连接
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1673">[CALCITE-1673]
   * Query with ORDER BY or GROUP BY on TIMESTAMP column throws
   * CompileException</a>. */
  @Test void testTimestampGroupBy() throws SQLException { // 测试TIMESTAMP列的GROUP BY操作
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug
    // Use LIMIT to ensure that results are deterministic without ORDER BY // 使用LIMIT确保结果在没有ORDER BY时也是确定的
    final String sql = "select \"EMPNO\", \"JOINTIMES\"\n" // 构建SQL查询，选择EMPNO和JOINTIMES
        + "from (select * from \"DATE\" limit 1)\n" // 从DATE表选择1条记录
        + "group by \"EMPNO\",\"JOINTIMES\""; // 按EMPNO和JOINTIMES分组
    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info); // 使用JDBC驱动管理器获取连接
         Statement statement = connection.createStatement(); // 创建语句对象
         ResultSet resultSet = statement.executeQuery(sql)) { // 执行查询
      assertThat(resultSet.next(), is(true)); // 验证有第一行结果
      final Timestamp timestamp = resultSet.getTimestamp(2); // 获取时间戳值
      assertThat(timestamp, isA(Timestamp.class)); // 验证是Timestamp类
      // Note: This logic is time zone specific, but the same time zone is // 注意：此逻辑与时区相关，但CSV适配器和测试使用相同的时区
      // used in the CSV adapter and this test, so they should cancel out. // 所以它们应该相互抵消
      assertThat(timestamp, is(Timestamp.valueOf("1996-08-03 00:01:02.0"))); // 验证时间戳值
    } // 自动关闭资源
  }

  /** As {@link #testTimestampGroupBy()} but with ORDER BY. */
  @Test void testTimestampOrderBy() throws SQLException { // 类似于testTimestampGroupBy，但使用ORDER BY
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug
    final String sql = "select \"EMPNO\",\"JOINTIMES\" from \"DATE\"\n" // 构建SQL查询，选择EMPNO和JOINTIMES
        + "order by \"JOINTIMES\""; // 按JOINTIMES排序
    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info); // 使用JDBC驱动管理器获取连接
         Statement statement = connection.createStatement(); // 创建语句对象
         ResultSet resultSet = statement.executeQuery(sql)) { // 执行查询
      assertThat(resultSet.next(), is(true)); // 验证有第一行结果
      final Timestamp timestamp = resultSet.getTimestamp(2); // 获取时间戳值
      assertThat(timestamp, is(Timestamp.valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    } // 自动关闭资源
  }

  /** As {@link #testTimestampGroupBy()} but with ORDER BY as well as GROUP
   * BY. */
  @Test void testTimestampGroupByAndOrderBy() throws SQLException { // 类似于testTimestampGroupBy，但同时使用GROUP BY和ORDER BY
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug
    final String sql = "select \"EMPNO\", \"JOINTIMES\" from \"DATE\"\n" // 构建SQL查询，选择EMPNO和JOINTIMES
        + "group by \"EMPNO\",\"JOINTIMES\" order by \"JOINTIMES\""; // 按EMPNO和JOINTIMES分组，并按JOINTIMES排序
    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info); // 使用JDBC驱动管理器获取连接
         Statement statement = connection.createStatement(); // 创建语句对象
         ResultSet resultSet = statement.executeQuery(sql)) { // 执行查询
      assertThat(resultSet.next(), is(true)); // 验证有第一行结果
      final Timestamp timestamp = resultSet.getTimestamp(2); // 获取时间戳值
      assertThat(timestamp, is(Timestamp.valueOf("1996-08-03 00:01:02"))); // 验证时间戳值
    } // 自动关闭资源
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1031">[CALCITE-1031]
   * In prepared statement, CsvScannableTable.scan is called twice</a>. To see
   * the bug, place a breakpoint in CsvScannableTable.scan, and note that it is
   * called twice. It should only be called once. */
  @Test void testPrepared() throws SQLException { // 测试预处理语句，验证CsvScannableTable.scan只被调用一次
    final Properties properties = new Properties(); // 创建Properties对象
    properties.setProperty("caseSensitive", "true"); // 设置大小写敏感
    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", properties)) { // 使用JDBC驱动管理器获取连接
      final CalciteConnection calciteConnection = // 获取Calcite连接
          connection.unwrap(CalciteConnection.class); // 解包为CalciteConnection

      final Schema schema = // 创建模式
          FileSchemaFactory.INSTANCE // 使用文件模式工厂实例
              .create(calciteConnection.getRootSchema(), "x", // 创建模式，名称为"x"
                  ImmutableMap.of("directory", // 配置参数
                      FileAdapterTests.resourcePath("sales-csv"), "flavor", "scannable")); // 目录和flavor
      calciteConnection.getRootSchema().add("TEST", schema); // 将模式添加到根模式
      final String sql = "select * from \"TEST\".\"DEPTS\" where \"NAME\" = ?"; // 构建SQL查询，使用参数占位符
      final PreparedStatement statement2 = // 创建预处理语句
          calciteConnection.prepareStatement(sql); // 准备SQL语句

      statement2.setString(1, "Sales"); // 设置第一个参数为"Sales"
      final ResultSet resultSet1 = statement2.executeQuery(); // 执行查询
      Consumer<ResultSet> expect = FileAdapterTests.expect("DEPTNO=10; NAME=Sales"); // 创建期望结果
      expect.accept(resultSet1); // 验证结果
    } // 自动关闭资源
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1054">[CALCITE-1054]
   * NPE caused by wrong code generation for Timestamp fields</a>. */
  @Test void testFilterOnNullableTimestamp() throws Exception { // 测试可空时间戳字段的过滤操作
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug

    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 使用JDBC驱动管理器获取连接
      final Statement statement = connection.createStatement(); // 创建语句对象

      // date // 测试DATE类型过滤
      final String sql1 = "select JOINEDAT from \"DATE\"\n" // 构建SQL查询，选择JOINEDAT
          + "where JOINEDAT < {d '2000-01-01'}\n" // 条件是JOINEDAT小于2000-01-01
          + "or JOINEDAT >= {d '2017-01-01'}"; // 或JOINEDAT大于等于2017-01-01
      final ResultSet joinedAt = statement.executeQuery(sql1); // 执行查询
      assertThat(joinedAt.next(), is(true)); // 验证有第一行结果
      assertThat(joinedAt.getDate(1), is(Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 测试TIME类型过滤
      final String sql2 = "select JOINTIME from \"DATE\"\n" // 构建SQL查询，选择JOINTIME
          + "where JOINTIME >= {t '07:00:00'}\n" // 条件是JOINTIME大于等于07:00:00
          + "and JOINTIME < {t '08:00:00'}"; // 且JOINTIME小于08:00:00
      final ResultSet joinTime = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTime.next(), is(true)); // 验证有第一行结果
      assertThat(joinTime.getTime(1), is(Time.valueOf("07:15:56"))); // 验证时间值

      // timestamp // 测试TIMESTAMP类型过滤
      final String sql3 = "select JOINTIMES,\n" // 构建SQL查询，选择JOINTIMES和计算值
          + "  {fn timestampadd(SQL_TSI_DAY, 1, JOINTIMES)}\n" // 时间戳加1天
          + "from \"DATE\"\n" // 从DATE表
          + "where (JOINTIMES >= {ts '2003-01-01 00:00:00'}\n" // 条件1：JOINTIMES在2003-2006之间
          + "and JOINTIMES < {ts '2006-01-01 00:00:00'})\n"
          + "or (JOINTIMES >= {ts '2003-01-01 00:00:00'}\n" // 条件2：JOINTIMES在2003-2007之间
          + "and JOINTIMES < {ts '2007-01-01 00:00:00'})";
      final ResultSet joinTimes = statement.executeQuery(sql3); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有第一行结果
      assertThat(joinTimes.getTimestamp(1), // 验证时间戳值
          is(Timestamp.valueOf("2005-09-07 00:00:00")));
      assertThat(joinTimes.getTimestamp(2), // 验证加1天后的时间戳值
          is(Timestamp.valueOf("2005-09-08 00:00:00")));

      final String sql4 = "select JOINTIMES, extract(year from JOINTIMES)\n" // 构建SQL查询，选择JOINTIMES和年份
          + "from \"DATE\""; // 从DATE表
      final ResultSet joinTimes2 = statement.executeQuery(sql4); // 执行查询
      assertThat(joinTimes2.next(), is(true)); // 验证有第一行结果
      assertThat(joinTimes2.getTimestamp(1), // 验证时间戳值
          is(Timestamp.valueOf("1996-08-03 00:01:02")));
    } // 自动关闭资源
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1118">[CALCITE-1118]
   * NullPointerException in EXTRACT with WHERE ... IN clause if field has null
   * value</a>. */
  @Test void testFilterOnNullableTimestamp2() throws Exception { // 测试EXTRACT函数与IN子句结合使用时的空值处理
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug

    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 使用JDBC驱动管理器获取连接
      final Statement statement = connection.createStatement(); // 创建语句对象
      final String sql1 = "select extract(year from JOINTIMES)\n" // 构建SQL查询，提取年份
          + "from \"DATE\"\n" // 从DATE表
          + "where extract(year from JOINTIMES) in (2006, 2007)"; // 条件是年份在2006或2007中
      final ResultSet joinTimes = statement.executeQuery(sql1); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有第一行结果
      assertThat(joinTimes.getInt(1), is(2007)); // 验证年份为2007

      final String sql2 = "select extract(year from JOINTIMES),\n" // 构建SQL查询，提取年份和计数
          + "  count(0) from \"DATE\"\n" // 从DATE表
          + "where extract(year from JOINTIMES) between 2007 and 2016\n" // 条件是年份在2007-2016之间
          + "group by extract(year from JOINTIMES)"; // 按年份分组
      final ResultSet joinTimes2 = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTimes2.next(), is(true)); // 验证有第一行结果
      assertThat(joinTimes2.getInt(1), is(2007)); // 验证年份为2007
      assertThat(joinTimes2.getLong(2), is(1L)); // 验证计数为1
      assertThat(joinTimes2.next(), is(true)); // 验证有第二行结果
      assertThat(joinTimes2.getInt(1), is(2015)); // 验证年份为2015
      assertThat(joinTimes2.getLong(2), is(2L)); // 验证计数为2
    } // 自动关闭资源
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1118">[CALCITE-1118]
   * NullPointerException in EXTRACT with WHERE ... IN clause if field has null
   * value</a>. */
  @Test void testNonNullFilterOnDateType() throws SQLException { // 测试日期时间类型字段的IS NOT NULL过滤
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug

    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 使用JDBC驱动管理器获取连接
      final Statement statement = connection.createStatement(); // 创建语句对象

      // date // 测试DATE类型的IS NOT NULL过滤
      final String sql1 = "select JOINEDAT from \"DATE\"\n" // 构建SQL查询，选择JOINEDAT
          + "where JOINEDAT is not null"; // 条件是JOINEDAT不为空
      final ResultSet joinedAt = statement.executeQuery(sql1); // 执行查询
      assertThat(joinedAt.next(), is(true)); // 验证有第一行结果
      assertThat(joinedAt.getDate(1).getClass(), equalTo(Date.class)); // 验证返回Date类
      assertThat(joinedAt.getDate(1), is(Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 测试TIME类型的IS NOT NULL过滤
      final String sql2 = "select JOINTIME from \"DATE\"\n" // 构建SQL查询，选择JOINTIME
          + "where JOINTIME is not null"; // 条件是JOINTIME不为空
      final ResultSet joinTime = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTime.next(), is(true)); // 验证有第一行结果
      assertThat(joinTime.getTime(1).getClass(), equalTo(Time.class)); // 验证返回Time类
      assertThat(joinTime.getTime(1), is(Time.valueOf("00:01:02"))); // 验证时间值

      // timestamp // 测试TIMESTAMP类型的IS NOT NULL过滤
      final String sql3 = "select JOINTIMES from \"DATE\"\n" // 构建SQL查询，选择JOINTIMES
          + "where JOINTIMES is not null"; // 条件是JOINTIMES不为空
      final ResultSet joinTimes = statement.executeQuery(sql3); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有第一行结果
      assertThat(joinTimes.getTimestamp(1).getClass(), // 验证返回Timestamp类
          equalTo(Timestamp.class));
      assertThat(joinTimes.getTimestamp(1), // 验证时间戳值
          is(Timestamp.valueOf("1996-08-03 00:01:02")));
    } // 自动关闭资源
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1427">[CALCITE-1427]
   * Code generation incorrect (does not compile) for DATE, TIME and TIMESTAMP
   * fields</a>. */
  @Test void testGreaterThanFilterOnDateType() throws SQLException { // 测试日期时间类型字段的大于过滤
    Properties info = new Properties(); // 创建Properties对象用于存储连接信息
    info.put("model", FileAdapterTests.jsonPath("bug")); // 设置模型为bug

    try (Connection connection = // 获取Calcite连接
             DriverManager.getConnection("jdbc:calcite:", info)) { // 使用JDBC驱动管理器获取连接
      final Statement statement = connection.createStatement(); // 创建语句对象

      // date // 测试DATE类型的大于过滤
      final String sql1 = "select JOINEDAT from \"DATE\"\n" // 构建SQL查询，选择JOINEDAT
          + "where JOINEDAT > {d '1990-01-01'}"; // 条件是JOINEDAT大于1990-01-01
      final ResultSet joinedAt = statement.executeQuery(sql1); // 执行查询
      assertThat(joinedAt.next(), is(true)); // 验证有第一行结果
      assertThat(joinedAt.getDate(1).getClass(), equalTo(Date.class)); // 验证返回Date类
      assertThat(joinedAt.getDate(1), is(Date.valueOf("1996-08-03"))); // 验证日期值

      // time // 测试TIME类型的大于过滤
      final String sql2 = "select JOINTIME from \"DATE\"\n" // 构建SQL查询，选择JOINTIME
          + "where JOINTIME > {t '00:00:00'}"; // 条件是JOINTIME大于00:00:00
      final ResultSet joinTime = statement.executeQuery(sql2); // 执行查询
      assertThat(joinTime.next(), is(true)); // 验证有第一行结果
      assertThat(joinTime.getTime(1).getClass(), equalTo(Time.class)); // 验证返回Time类
      assertThat(joinTime.getTime(1), is(Time.valueOf("00:01:02"))); // 验证时间值

      // timestamp // 测试TIMESTAMP类型的大于过滤
      final String sql3 = "select JOINTIMES from \"DATE\"\n" // 构建SQL查询，选择JOINTIMES
          + "where JOINTIMES > {ts '1990-01-01 00:00:00'}"; // 条件是JOINTIMES大于1990-01-01 00:00:00
      final ResultSet joinTimes = statement.executeQuery(sql3); // 执行查询
      assertThat(joinTimes.next(), is(true)); // 验证有第一行结果
      assertThat(joinTimes.getTimestamp(1).getClass(), // 验证返回Timestamp类
          equalTo(Timestamp.class));
      assertThat(joinTimes.getTimestamp(1), // 验证时间戳值
          is(Timestamp.valueOf("1996-08-03 00:01:02")));
    } // 自动关闭资源
  }
}
