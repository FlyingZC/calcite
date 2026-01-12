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
package org.apache.calcite.test; // 包声明：测试类所在的包名，org.apache.calcite.test是Calcite框架的测试包

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema类：用于通过反射将Java对象转换为Calcite schema
import org.apache.calcite.adapter.jdbc.JdbcCatalogSchema; // 导入JdbcCatalogSchema类：表示JDBC目录schema，用于访问JDBC数据库的元数据
import org.apache.calcite.adapter.jdbc.JdbcSchema; // 导入JdbcSchema类：表示JDBC schema，用于将JDBC数据库表映射为Calcite表
import org.apache.calcite.config.CalciteSystemProperty; // 导入CalciteSystemProperty类：Calcite系统属性配置类
import org.apache.calcite.jdbc.CalciteConnection; // 导入CalciteConnection类：Calcite JDBC连接类，扩展了标准JDBC连接
import org.apache.calcite.jdbc.CalciteJdbc41Factory; // 导入CalciteJdbc41Factory类：用于创建Calcite JDBC 4.1连接的工厂类
import org.apache.calcite.jdbc.CalciteSchema; // 导入CalciteSchema类：Calcite schema的核心表示类
import org.apache.calcite.jdbc.Driver; // 导入Driver类：Calcite JDBC驱动类
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类：可变的Schema接口，允许动态添加子schema和表
import org.apache.calcite.schema.lookup.LikePattern; // 导入LikePattern类：用于模式匹配的类，支持SQL LIKE语法
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HrSchema类：测试用的人力资源schema，包含员工、部门等表

import org.apache.commons.dbcp2.BasicDataSource; // 导入BasicDataSource类：Apache Commons DBCP2的数据库连接池实现

import com.google.common.collect.Sets; // 导入Sets类：Google Guava提供的集合工具类

import org.junit.jupiter.api.Test; // 导入Test注解：JUnit 5的测试方法注解

import java.sql.Connection; // 导入Connection接口：JDBC连接接口，表示与数据库的会话
import java.sql.DriverManager; // 导入DriverManager类：JDBC驱动管理类，用于建立数据库连接
import java.sql.PreparedStatement; // 导入PreparedStatement接口：预编译的SQL语句接口
import java.sql.ResultSet; // 导入ResultSet接口：表示数据库查询结果集
import java.sql.SQLException; // 导入SQLException类：JDBC异常类
import java.sql.Statement; // 导入Statement接口：用于执行SQL语句的接口
import java.util.HashSet; // 导入HashSet类：哈希集合实现，不允许重复元素
import java.util.Properties; // 导入Properties类：属性集合类，用于存储配置信息
import java.util.Set; // 导入Set接口：集合接口，不允许重复元素
import java.util.concurrent.atomic.AtomicInteger; // 导入AtomicInteger类：原子整数类，支持并发安全的整型操作
import javax.sql.DataSource; // 导入DataSource接口：数据源接口，用于获取数据库连接

import static org.hamcrest.CoreMatchers.equalTo; // 导入equalTo匹配器：Hamcrest匹配器，用于判断两个值相等
import static org.hamcrest.CoreMatchers.is; // 导入is匹配器：Hamcrest匹配器，用于增强可读性
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat方法：Hamcrest断言方法
import static org.junit.jupiter.api.Assertions.fail; // 导入fail方法：JUnit断言方法，用于标记测试失败

/** Test case for joining tables from two different JDBC databases. */ // 类文档注释：用于测试从两个不同JDBC数据库连接表的测试用例
class MultiJdbcSchemaJoinTest { // 类声明：多JDBC Schema连接测试类，测试Calcite在跨数据库连接场景下的功能
  @Test void test() throws SQLException { // 测试方法声明：测试基本的跨数据库连接功能，抛出SQLException异常
    // Create two databases // 注释：创建两个数据库
    // It's two times hsqldb, but imagine they are different rdbms's // 注释：这里使用了两次HSQLDB，但可以想象成是不同的关系型数据库系统
    final String db1 = TempDb.INSTANCE.getUrl(); // 声明并初始化：获取第一个临时数据库的JDBC连接URL
    Connection c1 = DriverManager.getConnection(db1, "", ""); // 创建连接：使用DriverManager建立与第一个数据库的连接，用户名和密码为空
    Statement stmt1 = c1.createStatement(); // 创建语句：从连接c1创建Statement对象，用于执行SQL语句
    stmt1.execute("create table table1(id varchar(10) not null primary key, " // 执行SQL：在第一个数据库中创建table1表，包含id主键字段和field1字段
        + "field1 varchar(10))"); // SQL语句续接：table1表的第二个字段定义
    stmt1.execute("insert into table1 values('a', 'aaaa')"); // 执行SQL：向table1表插入一条记录，id为'a'，field1为'aaaa'
    c1.close(); // 关闭连接：关闭与第一个数据库的连接，释放资源

    final String db2 = TempDb.INSTANCE.getUrl(); // 声明并初始化：获取第二个临时数据库的JDBC连接URL
    Connection c2 = DriverManager.getConnection(db2, "", ""); // 创建连接：使用DriverManager建立与第二个数据库的连接，用户名和密码为空
    Statement stmt2 = c2.createStatement(); // 创建语句：从连接c2创建Statement对象，用于执行SQL语句
    stmt2.execute("create table table2(id varchar(10) not null primary key, " // 执行SQL：在第二个数据库中创建table2表，包含id主键字段和field1字段
        + "field1 varchar(10))"); // SQL语句续接：table2表的第二个字段定义
    stmt2.execute("insert into table2 values('a', 'aaaa')"); // 执行SQL：向table2表插入一条记录，id为'a'，field1为'aaaa'
    c2.close(); // 关闭连接：关闭与第二个数据库的连接，释放资源

    // Connect via calcite to these databases // 注释：通过Calcite连接到这两个数据库
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建连接：建立与Calcite的JDBC连接，使用默认配置
    CalciteConnection calciteConnection = // 类型转换：将JDBC连接解包为CalciteConnection，以访问Calcite特定功能
        connection.unwrap(CalciteConnection.class); // 解包操作：获取底层的CalciteConnection对象
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema：从Calcite连接获取根SchemaPlus对象，用于添加子schema
    final DataSource ds1 = // 声明并初始化：为第一个数据库创建数据源
        JdbcSchema.dataSource(db1, "org.hsqldb.jdbcDriver", "", ""); // 创建数据源：使用JdbcSchema创建数据源，指定URL、驱动类、用户名和密码
    rootSchema.add("DB1", // 添加schema：向根schema添加名为"DB1"的子schema
        JdbcSchema.create(rootSchema, "DB1", ds1, null, null)); // 创建JDBC schema：创建JdbcSchema实例并添加到根schema中
    final DataSource ds2 = // 声明并初始化：为第二个数据库创建数据源
        JdbcSchema.dataSource(db2, "org.hsqldb.jdbcDriver", "", ""); // 创建数据源：使用JdbcSchema创建数据源，指定URL、驱动类、用户名和密码
    rootSchema.add("DB2", // 添加schema：向根schema添加名为"DB2"的子schema
        JdbcSchema.create(rootSchema, "DB2", ds2, null, null)); // 创建JDBC schema：创建JdbcSchema实例并添加到根schema中

    Statement stmt3 = connection.createStatement(); // 创建语句：从Calcite连接创建Statement对象，用于执行SQL查询
    ResultSet rs = stmt3.executeQuery("select table1.id, table1.field1 " // 执行查询：执行跨数据库连接查询，从DB1.table1和DB2.table2连接获取数据
        + "from db1.table1 join db2.table2 on table1.id = table2.id"); // SQL语句续接：指定连接条件为table1.id等于table2.id
    assertThat(CalciteAssert.toString(rs), equalTo("ID=a; FIELD1=aaaa\n")); // 断言：验证查询结果是否为预期值，使用CalciteAssert工具将结果集转换为字符串比较
  }

  /** Makes sure that {@link #test} is re-entrant. // 方法文档注释：确保test()方法是可重入的（可以被多次调用而不影响结果）
   * Effectively a test for {@code TempDb}. */ // 文档注释续：实际上是对TempDb类的测试，验证临时数据库管理器的正确性
  @Test void test2() throws SQLException, ClassNotFoundException { // 测试方法声明：测试test()方法的重入性，可能抛出SQLException和ClassNotFoundException异常
    test(); // 调用方法：直接调用test()方法，验证其可以被多次成功执行
  }

  /** Tests {@link org.apache.calcite.adapter.jdbc.JdbcCatalogSchema}. */ // 方法文档注释：测试JdbcCatalogSchema类的功能，该类用于访问JDBC数据库的目录元数据
  @Test void test3() throws SQLException { // 测试方法声明：测试JdbcCatalogSchema的功能，抛出SQLException异常
    final BasicDataSource dataSource = new BasicDataSource(); // 声明并初始化：创建BasicDataSource实例，作为数据库连接池
    dataSource.setUrl(TempDb.INSTANCE.getUrl()); // 配置数据源：设置数据库连接URL
    dataSource.setUsername(""); // 配置数据源：设置用户名为空
    dataSource.setPassword(""); // 配置数据源：设置密码为空
    final JdbcCatalogSchema schema = // 声明并初始化：创建JdbcCatalogSchema实例
        JdbcCatalogSchema.create(null, "", dataSource, "PUBLIC"); // 创建catalog schema：指定父schema为null，名称为空，使用数据源，默认catalog为PUBLIC
    assertThat(schema.subSchemas().getNames(LikePattern.any()), // 断言：获取所有子schema的名称，使用LikePattern.any()匹配所有模式
        is(Sets.newHashSet("INFORMATION_SCHEMA", "PUBLIC", "SYSTEM_LOBS"))); // 验证：断言子schema名称集合包含INFORMATION_SCHEMA、PUBLIC和SYSTEM_LOBS
    final CalciteSchema rootSchema0 = // 声明并初始化：创建Calcite根schema
        CalciteSchema.createRootSchema(false, false, "", schema); // 创建根schema：指定不缓存、不添加默认schema，名称为空，使用JdbcCatalogSchema作为根
    final Driver driver = new Driver(); // 声明并初始化：创建Calcite JDBC驱动实例
    final CalciteJdbc41Factory factory = new CalciteJdbc41Factory(); // 声明并初始化：创建JDBC 4.1工厂实例，用于创建连接
    final String sql = "select count(*) as c from information_schema.schemata"; // 声明并初始化：SQL查询语句，统计information_schema.schemata表中的记录数
    try (Connection connection = // try-with-resources：创建Calcite连接，使用工厂方法
             factory.newConnection(driver, factory, // 创建连接：使用驱动、工厂、URL、属性、根schema和null参数创建连接
                 "jdbc:calcite:", new Properties(), rootSchema0, null); // 参数：JDBC URL、空属性、根schema和null
         Statement stmt3 = connection.createStatement(); // 创建语句：从连接创建Statement对象
         ResultSet rs = stmt3.executeQuery(sql)) { // 执行查询：执行SQL查询并获取结果集
      assertThat(CalciteAssert.toString(rs), equalTo("C=3\n")); // 断言：验证查询结果为"C=3\n"，表示有3个schema
    } // 自动关闭：try-with-resources会自动关闭Connection、Statement和ResultSet
  }

  private Connection setup() throws SQLException { // 私有方法声明：设置测试环境，创建JDBC数据库表和Calcite schema，抛出SQLException异常
    // Create a jdbc database & table // 注释：创建一个JDBC数据库和表
    final String db = TempDb.INSTANCE.getUrl(); // 声明并初始化：获取临时数据库的JDBC连接URL
    Connection c1 = DriverManager.getConnection(db, "", ""); // 创建连接：建立与临时数据库的连接
    Statement stmt1 = c1.createStatement(); // 创建语句：从连接c1创建Statement对象
    // This is a table we can join with the emps from the hr schema // 注释：这是一个可以与hr schema中的emps表连接的表
    stmt1.execute("create table table1(id integer not null primary key, " // 执行SQL：创建table1表，包含integer类型的id主键和varchar类型的field1字段
        + "field1 varchar(10))"); // SQL语句续接：table1表的第二个字段定义
    stmt1.execute("insert into table1 values(100, 'foo')"); // 执行SQL：向table1表插入id为100、field1为'foo'的记录
    stmt1.execute("insert into table1 values(200, 'bar')"); // 执行SQL：向table1表插入id为200、field1为'bar'的记录
    c1.close(); // 关闭连接：关闭与数据库的连接

    // Make a Calcite schema with both a jdbc schema and a non-jdbc schema // 注释：创建一个同时包含JDBC schema和非JDBC schema的Calcite schema
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建连接：建立与Calcite的JDBC连接
    CalciteConnection calciteConnection = // 类型转换：将JDBC连接解包为CalciteConnection
        connection.unwrap(CalciteConnection.class); // 解包操作：获取底层的CalciteConnection对象
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema：从Calcite连接获取根SchemaPlus对象
    rootSchema.add("DB", // 添加schema：向根schema添加名为"DB"的JDBC schema
        JdbcSchema.create(rootSchema, "DB", // 创建JDBC schema：使用JdbcSchema创建schema，包含table1表
            JdbcSchema.dataSource(db, "org.hsqldb.jdbcDriver", "", ""), // 创建数据源：为临时数据库创建数据源
            null, null)); // 参数：catalog和schema映射都为null
    rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 添加schema：向根schema添加名为"hr"的ReflectiveSchema，基于HrSchema Java类
    return connection; // 返回连接：返回配置好的Calcite连接
  }

  @Test void testJdbcWithEnumerableHashJoin() throws SQLException { // 测试方法声明：测试JDBC表与Enumerable表使用哈希连接的场景，抛出SQLException异常
    // This query works correctly // 注释：这个查询可以正确执行
    String query = "select t.id, t.field1 " // 声明并初始化：SQL查询语句，从DB.table1和hr.emps表连接获取数据
        + "from db.table1 t join \"hr\".\"emps\" e on e.\"empid\" = t.id"; // SQL语句续接：连接条件为emps表的empid字段等于table1表的id字段
    final Set<Integer> expected = Sets.newHashSet(100, 200); // 声明并初始化：预期结果集，包含id 100和200
    assertThat(runQuery(setup(), query), equalTo(expected)); // 断言：执行查询并验证结果与预期一致
  }

  @Test void testEnumerableWithJdbcJoin() throws SQLException { // 测试方法声明：测试Enumerable表与JDBC表连接的场景（连接顺序反转），抛出SQLException异常
    //  * compared to testJdbcWithEnumerableHashJoin, the join order is reversed // 注释：与testJdbcWithEnumerableHashJoin相比，连接顺序反转了
    //  * the query fails with a CannotPlanException // 注释：该查询会抛出CannotPlanException异常（无法规划查询）
    String query = "select t.id, t.field1 " // 声明并初始化：SQL查询语句，从hr.emps和DB.table1表连接获取数据（连接顺序反转）
        + "from \"hr\".\"emps\" e join db.table1 t on e.\"empid\" = t.id"; // SQL语句续接：连接条件为emps表的empid字段等于table1表的id字段
    final Set<Integer> expected = Sets.newHashSet(100, 200); // 声明并初始化：预期结果集，包含id 100和200
    assertThat(runQuery(setup(), query), equalTo(expected)); // 断言：执行查询并验证结果与预期一致
  }

  @Test void testEnumerableWithJdbcJoinWithWhereClause() // 测试方法声明：测试Enumerable表与JDBC表连接并添加WHERE条件的场景
      throws SQLException { // 方法续接：抛出SQLException异常
    // Same query as above but with a where condition added: // 注释：与上面的查询相同，但添加了WHERE条件
    //  * the good: this query does not give a CannotPlanException // 注释：好的方面：这个查询不会抛出CannotPlanException
    //  * the bad: the result is wrong: there is only one emp called Bill. // 注释：坏的方面：结果是错误的，只有一个名为Bill的员工
    //             The query plan shows the join condition is always true, // 注释：查询计划显示连接条件总是为真
    //             afaics, the join condition is pushed down to the non-jdbc // 注释：据我所见，连接条件被下推到非JDBC表
    //             table. It might have something to do with the cast that // 注释：这可能与连接条件中引入的类型转换有关
    //             is introduced in the join condition. // 注释续：这个问题可能与连接条件中引入的类型转换有关
    String query = "select t.id, t.field1 " // 声明并初始化：SQL查询语句，添加WHERE条件过滤name为'Bill'的员工
        + "from \"hr\".\"emps\" e join db.table1 t on e.\"empid\" = t.id" // SQL语句续接：连接条件为emps表的empid字段等于table1表的id字段
        + " where e.\"name\" = 'Bill'"; // SQL语句续接：WHERE条件，筛选name字段值为'Bill'的记录
    final Set<Integer> expected = Sets.newHashSet(100); // 声明并初始化：预期结果集，只包含id 100（Bill的empid）
    assertThat(runQuery(setup(), query), equalTo(expected)); // 断言：执行查询并验证结果与预期一致
  }

  private Set<Integer> runQuery(Connection calciteConnection, String query) // 私有方法声明：执行查询并返回结果集中的id集合，参数为Calcite连接和SQL查询语句
      throws SQLException { // 方法续接：抛出SQLException异常
    // Print out the plan // 注释：打印查询计划（如果启用调试模式）
    Statement stmt = calciteConnection.createStatement(); // 创建语句：从连接创建Statement对象
    try { // try块：开始异常处理
      ResultSet rs; // 声明结果集变量：用于存储查询结果
      if (CalciteSystemProperty.DEBUG.value()) { // 条件判断：如果启用了调试模式
        rs = stmt.executeQuery("explain plan for " + query); // 执行SQL：执行EXPLAIN PLAN命令，获取查询执行计划
        rs.next(); // 移动游标：移动到结果集的第一行
        System.out.println(rs.getString(1)); // 输出：打印查询计划字符串
      }

      // Run the actual query // 注释：执行实际的查询
      rs = stmt.executeQuery(query); // 执行查询：执行原始SQL查询并获取结果集
      Set<Integer> ids = new HashSet<>(); // 声明并初始化：创建HashSet集合用于存储id值
      while (rs.next()) { // 循环：遍历结果集的每一行
        ids.add(rs.getInt(1)); // 添加元素：将结果集第一列（id字段）的整数值添加到集合中
      } // 循环结束
      return ids; // 返回：返回id集合
    } finally { // finally块：确保资源释放
      stmt.close(); // 关闭语句：关闭Statement对象，释放资源
    } // try-finally结束
  }

  @Test void testSchemaConsistency() throws Exception { // 测试方法声明：测试schema的一致性，当数据库结构发生变化时的行为，抛出Exception异常
    // Create a database // 注释：创建一个数据库
    final String db = TempDb.INSTANCE.getUrl(); // 声明并初始化：获取临时数据库的JDBC连接URL
    Connection c1 = DriverManager.getConnection(db, "", ""); // 创建连接：建立与临时数据库的连接
    Statement stmt1 = c1.createStatement(); // 创建语句：从连接c1创建Statement对象
    stmt1.execute("create table table1(id varchar(10) not null primary key, " // 执行SQL：创建table1表，包含varchar类型的id主键和field1字段
        + "field1 varchar(10))"); // SQL语句续接：table1表的第二个字段定义

    // Connect via calcite to these databases // 注释：通过Calcite连接到这个数据库
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建连接：建立与Calcite的JDBC连接
    CalciteConnection calciteConnection = // 类型转换：将JDBC连接解包为CalciteConnection
        connection.unwrap(CalciteConnection.class); // 解包操作：获取底层的CalciteConnection对象
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema：从Calcite连接获取根SchemaPlus对象
    final DataSource ds = // 声明并初始化：为数据库创建数据源
        JdbcSchema.dataSource(db, "org.hsqldb.jdbcDriver", "", ""); // 创建数据源：使用JdbcSchema创建数据源
    rootSchema.add("DB", JdbcSchema.create(rootSchema, "DB", ds, null, null)); // 添加schema：向根schema添加名为"DB"的JDBC schema

    Statement stmt3 = connection.createStatement(); // 创建语句：从Calcite连接创建Statement对象
    ResultSet rs; // 声明结果集变量：用于存储查询结果

    // fails, table does not exist // 注释：预期失败，因为表不存在
    try { // try块：尝试执行查询
      rs = stmt3.executeQuery("select * from db.table2"); // 执行查询：查询不存在的table2表
      fail("expected error, got " + rs); // 断言失败：如果没有抛出异常，标记测试失败
    } catch (SQLException e) { // 捕获异常：捕获SQLException异常
      assertThat(e.getCause().getCause().getMessage(), // 断言：验证异常消息
          equalTo("Object 'TABLE2' not found within 'DB'")); // 验证：期望的异常消息是"Object 'TABLE2' not found within 'DB'"
    } // try-catch结束

    stmt1.execute("create table table2(id varchar(10) not null primary key, " // 执行SQL：在数据库中创建table2表
        + "field1 varchar(10))"); // SQL语句续接：table2表的第二个字段定义
    stmt1.execute("insert into table2 values('a', 'aaaa')"); // 执行SQL：向table2表插入一条记录

    PreparedStatement stmt2 = // 声明并初始化：创建预编译语句对象
        connection.prepareStatement("select * from db.table2"); // 创建预编译语句：准备查询table2表的SQL语句

    stmt1.execute("alter table table2 add column field2 varchar(10)"); // 执行SQL：修改table2表，添加field2字段

    // "field2" not visible to stmt2 // 注释：field2字段对stmt2不可见（因为stmt2是之前创建的）
    rs = stmt2.executeQuery(); // 执行查询：使用预编译语句执行查询
    assertThat(CalciteAssert.toString(rs), equalTo("ID=a; FIELD1=aaaa\n")); // 断言：验证结果不包含field2字段

    // "field2" visible to a new query // 注释：field2字段对新查询可见
    rs = stmt3.executeQuery("select * from db.table2"); // 执行查询：使用新的Statement执行查询
    assertThat(CalciteAssert.toString(rs), // 断言：验证查询结果
        equalTo("ID=a; FIELD1=aaaa; FIELD2=null\n")); // 验证：期望结果包含field2字段，值为null
    c1.close(); // 关闭连接：关闭与数据库的连接
  }

  /** Pool of temporary databases. */ // 内部类文档注释：临时数据库池，用于管理测试用的临时数据库实例
  static class TempDb { // 内部类声明：临时数据库管理类，负责生成唯一的数据库URL
    public static final TempDb INSTANCE = new TempDb(); // 静态常量：单例实例，全局唯一的TempDb对象

    private final AtomicInteger id = new AtomicInteger(1); // 成员变量：原子整数计数器，用于生成唯一的数据库ID，初始值为1

    TempDb() {} // 构造方法：私有构造方法，防止外部实例化（虽然不是private，但通过单例模式使用）

    /** Allocates a URL for a new Hsqldb database. */ // 方法文档注释：为新的HSQLDB数据库分配URL
    public String getUrl() { // 公共方法声明：获取新的数据库URL
      return "jdbc:hsqldb:mem:db" + id.getAndIncrement(); // 返回：生成并返回唯一的内存数据库URL，格式为jdbc:hsqldb:mem:db{id}
    } // 方法结束
  } // 内部类结束
} // 类结束
