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
package org.apache.calcite.test; // 声明包名，该类属于 org.apache.calcite.test 包

import org.apache.calcite.config.CalciteConnectionProperty; // 导入 Calcite 连接属性配置类
import org.apache.calcite.jdbc.CalciteConnection; // 导入 Calcite 连接类
import org.apache.calcite.jdbc.CalcitePrepare; // 导入 Calcite 准备上下文接口
import org.apache.calcite.schema.Function; // 导入函数接口
import org.apache.calcite.schema.FunctionParameter; // 导入函数参数接口
import org.apache.calcite.server.DdlExecutorImpl; // 导入 DDL 执行器实现类
import org.apache.calcite.server.ServerDdlExecutor; // 导入服务器 DDL 执行器类
import org.apache.calcite.sql.SqlNode; // 导入 SQL 节点基类
import org.apache.calcite.sql.ddl.SqlCreateForeignSchema; // 导入创建外部模式 SQL 节点
import org.apache.calcite.sql.ddl.SqlCreateFunction; // 导入创建函数 SQL 节点
import org.apache.calcite.sql.ddl.SqlCreateMaterializedView; // 导入创建物化视图 SQL 节点
import org.apache.calcite.sql.ddl.SqlCreateSchema; // 导入创建模式 SQL 节点
import org.apache.calcite.sql.ddl.SqlCreateTable; // 导入创建表 SQL 节点
import org.apache.calcite.sql.ddl.SqlCreateTableLike; // 导入创建表 LIKE SQL 节点
import org.apache.calcite.sql.ddl.SqlCreateType; // 导入创建类型 SQL 节点
import org.apache.calcite.sql.ddl.SqlCreateView; // 导入创建视图 SQL 节点
import org.apache.calcite.sql.ddl.SqlDropFunction; // 导入删除函数 SQL 节点
import org.apache.calcite.sql.ddl.SqlDropMaterializedView; // 导入删除物化视图 SQL 节点
import org.apache.calcite.sql.ddl.SqlDropSchema; // 导入删除模式 SQL 节点
import org.apache.calcite.sql.ddl.SqlTruncateTable; // 导入清空表 SQL 节点

import org.junit.jupiter.api.Disabled; // 导入 JUnit 5 禁用测试注解
import org.junit.jupiter.api.Test; // 导入 JUnit 5 测试注解

import java.math.BigDecimal; // 导入 BigDecimal 类用于精确数值计算
import java.sql.Connection; // 导入 JDBC 连接接口
import java.sql.DriverManager; // 导入 JDBC 驱动管理器
import java.sql.ResultSet; // 导入 JDBC 结果集接口
import java.sql.SQLException; // 导入 JDBC SQL 异常类
import java.sql.Statement; // 导入 JDBC 语句接口
import java.sql.Struct; // 导入 JDBC 结构化类型接口
import java.util.ArrayList; // 导入 ArrayList 动态数组类
import java.util.List; // 导入 List 列表接口

import static org.apache.calcite.test.Matchers.isLinux; // 导入 Linux 系统匹配器

import static org.hamcrest.CoreMatchers.containsString; // 导入字符串包含匹配器
import static org.hamcrest.CoreMatchers.is; // 导入相等匹配器
import static org.hamcrest.CoreMatchers.notNullValue; // 导入非空匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言工具类
import static org.junit.jupiter.api.Assertions.assertArrayEquals; // 导入数组相等断言
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow; // 导入不抛出异常断言
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入假断言
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入抛出异常断言
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入真断言
import static org.junit.jupiter.api.Assertions.fail; // 导入失败断言

/**
 * ServerTest 类用于测试 Calcite 服务器模块和 DDL（数据定义语言）功能
 * 该类包含了对各种 DDL 操作的单元测试，包括创建/删除模式、表、视图、类型、函数等
 * 同时也测试了生成列（generated column）、类型转换、INSERT 操作等高级特性
 * 所有测试方法都使用 JUnit 5 框架进行编写，使用 JDBC 连接执行 SQL 语句
 */
class ServerTest { // 定义 ServerTest 测试类

  static final String URL = "jdbc:calcite:"; // 定义静态常量 URL，表示 Calcite JDBC 连接字符串，使用内存模式

  /**
   * connect() 方法用于创建并返回一个 Calcite 数据库连接
   * 该方法配置了连接属性，包括解析器工厂、物化视图启用和函数库
   * @return 返回配置好的 Calcite 数据库连接对象
   * @throws SQLException 如果连接创建失败则抛出 SQL 异常
   */
  static Connection connect() throws SQLException { // 定义静态方法 connect，用于创建数据库连接，可能抛出 SQLException
    return DriverManager.getConnection(URL, // 使用 DriverManager 获取数据库连接，传入 URL 和属性
        CalciteAssert.propBuilder() // 创建属性构建器
            .set(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                ServerDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用 ServerDdlExecutor 的解析器工厂
            .set(CalciteConnectionProperty.MATERIALIZATIONS_ENABLED, // 设置物化视图启用属性
                "true") // 启用物化视图功能
            .set(CalciteConnectionProperty.FUN, "standard,oracle") // 设置函数库，包含标准函数和 Oracle 函数
            .build()); // 构建属性对象
  }

  /**
   * testAll() 方法是一个特殊的测试方法
   * 该方法包含对 DdlExecutorImpl 中所有重载 execute 方法的调用
   * 这个测试永远不会真正执行（因为 if (true) return），但它的存在是为了
   * 消除编译器关于这些方法未被调用的警告
   * 这些方法实际上是通过反射机制在其他地方被调用的
   */
  @Test void testAll() { // 定义测试方法 testAll，用于调用所有重载的 execute 方法以消除警告
    //noinspection ConstantConditions // 忽略常量条件警告
    if (true) { // 如果条件为真（总是为真）
      return; // 直接返回，不执行后续代码
    }
    final ServerDdlExecutor executor = ServerDdlExecutor.INSTANCE; // 获取 ServerDdlExecutor 单例实例
    final Object o = "x"; // 创建一个对象作为占位符，用于后续测试调用
    final CalcitePrepare.Context context = (CalcitePrepare.Context) o; // 将对象强制转换为 CalcitePrepare.Context 类型
    executor.execute((SqlNode) o, context); // 调用 execute 方法，传入 SqlNode 类型的参数
    executor.execute((SqlCreateFunction) o, context); // 调用 execute 方法，传入 SqlCreateFunction 类型的参数
    executor.execute((SqlCreateTable) o, context); // 调用 execute 方法，传入 SqlCreateTable 类型的参数
    executor.execute((SqlCreateTableLike) o, context); // 调用 execute 方法，传入 SqlCreateTableLike 类型的参数
    executor.execute((SqlCreateSchema) o, context); // 调用 execute 方法，传入 SqlCreateSchema 类型的参数
    executor.execute((SqlCreateMaterializedView) o, context); // 调用 execute 方法，传入 SqlCreateMaterializedView 类型的参数
    executor.execute((SqlCreateView) o, context); // 调用 execute 方法，传入 SqlCreateView 类型的参数
    executor.execute((SqlCreateType) o, context); // 调用 execute 方法，传入 SqlCreateType 类型的参数
    executor.execute((SqlCreateSchema) o, context); // 调用 execute 方法，传入 SqlCreateSchema 类型的参数（重复调用）
    executor.execute((SqlCreateForeignSchema) o, context); // 调用 execute 方法，传入 SqlCreateForeignSchema 类型的参数
    executor.execute((SqlDropMaterializedView) o, context); // 调用 execute 方法，传入 SqlDropMaterializedView 类型的参数
    executor.execute((SqlDropFunction) o, context); // 调用 execute 方法，传入 SqlDropFunction 类型的参数
    executor.execute((SqlDropSchema) o, context); // 调用 execute 方法，传入 SqlDropSchema 类型的参数
    executor.execute((SqlTruncateTable) o, context); // 调用 execute 方法，传入 SqlTruncateTable 类型的参数
  }

  /**
   * testStatement() 方法测试基本的 SQL 语句执行功能
   * 该方法创建连接、语句，并执行一个简单的 VALUES 查询
   * 验证结果集的遍历和数据读取是否正常工作
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testStatement() throws Exception { // 定义测试方法 testStatement，测试基本语句执行功能
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement(); // 创建语句对象
         ResultSet r = s.executeQuery("values 1, 2")) { // 执行 VALUES 查询，返回结果集
      assertThat(r.next(), is(true)); // 断言结果集有下一行数据
      assertThat(r.getString(1), notNullValue()); // 断言第一列的值不为空
      assertThat(r.next(), is(true)); // 断言结果集还有下一行数据
      assertThat(r.next(), is(false)); // 断言结果集没有更多数据
    } // 自动关闭资源（Connection、Statement、ResultSet）
  }

  /**
   * testCreateSchema() 方法测试创建模式（Schema）的各种语法
   * 包括：
   * 1. 基本的 CREATE SCHEMA 语句
   * 2. 在模式中创建表
   * 3. 向表中插入数据并查询
   * 4. CREATE SCHEMA IF NOT EXISTS 语法（不覆盖已存在的模式）
   * 5. CREATE OR REPLACE SCHEMA 语法（覆盖已存在的模式）
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateSchema() throws Exception { // 定义测试方法 testCreateSchema，测试创建模式功能
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create schema s"); // 执行创建模式语句，返回 false 表示没有结果集
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create table s.t (i int not null)"); // 在模式 s 中创建表 t，包含一个非空整型列 i
      assertThat(b, is(false)); // 断言返回值为 false
      int x = s.executeUpdate("insert into s.t values 1"); // 向表 s.t 插入值 1，返回影响的行数
      assertThat(x, is(1)); // 断言影响行数为 1
      try (ResultSet r = s.executeQuery("select count(*) from s.t")) { // 查询表 s.t 的行数
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt(1), is(1)); // 断言行数为 1
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      assertDoesNotThrow(() -> { // 断言不抛出异常
        s.execute("create schema if not exists s"); // 执行 CREATE SCHEMA IF NOT EXISTS，如果模式已存在则不报错
        s.executeUpdate("insert into s.t values 2"); // 向表 s.t 插入值 2，验证模式未被覆盖
      }, "IF NOT EXISTS should not overwrite the existing schema"); // 错误信息

      assertDoesNotThrow(() -> { // 断言不抛出异常
        s.execute("create or replace schema s"); // 执行 CREATE OR REPLACE SCHEMA，覆盖已存在的模式
        s.execute("create table s.t (i int not null)"); // 在覆盖后的模式中重新创建表 t
      }, "REPLACE must overwrite the existing schema"); // 错误信息
    } // 自动关闭资源
  }

  /**
   * testCreateTypeDocumentationExample() 方法测试 CREATE TYPE 的文档示例
   * 该测试用例对应 JIRA 问题 CALCITE-5905，验证 CREATE TYPE 文档的正确性
   * 测试内容包括：
   * 1. 创建一个地址类型 address_typ，包含街道、城市、州、邮编
   * 2. 创建一个员工类型 employee_typ，包含员工信息和嵌套的地址类型
   * 3. 使用类型构造函数创建员工记录并查询
   * 4. 验证结构化类型的数据访问和嵌套类型的处理
   * @throws SQLException 如果执行过程中出现 SQL 异常则抛出
   */
  @Test void testCreateTypeDocumentationExample() throws SQLException { // 定义测试方法，测试 CREATE TYPE 文档示例
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("CREATE TYPE address_typ AS (\n" // 创建地址类型 address_typ
          + "   street          VARCHAR(30),\n" // 街道字段，最多30个字符
          + "   city            VARCHAR(20),\n" // 城市字段，最多20个字符
          + "   state           CHAR(2),\n" // 州字段，固定2个字符
          + "   postal_code     VARCHAR(6))"); // 邮编字段，最多6个字符
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("CREATE TYPE employee_typ AS (\n" // 创建员工类型 employee_typ
          + "  employee_id       DECIMAL(6),\n" // 员工ID，6位十进制数
          + "  first_name        VARCHAR(20),\n" // 名，最多20个字符
          + "  last_name         VARCHAR(25),\n" // 姓，最多25个字符
          + "  email             VARCHAR(25),\n" // 邮箱，最多25个字符
          + "  phone_number      VARCHAR(20),\n" // 电话号码，最多20个字符
          + "  hire_date         DATE,\n" // 入职日期，日期类型
          + "  job_id            VARCHAR(10),\n" // 职位ID，最多10个字符
          + "  salary            DECIMAL(8,2),\n" // 薪水，8位数字，2位小数
          + "  commission_pct    DECIMAL(2,2),\n" // 佣金百分比，2位数字，2位小数
          + "  manager_id        DECIMAL(6),\n" // 经理ID，6位十进制数
          + "  department_id     DECIMAL(4),\n" // 部门ID，4位十进制数
          + "  address           address_typ)\n"); // 地址，嵌套的 address_typ 类型
      assertThat(b, is(false)); // 断言返回值为 false
      try (ResultSet r = // 使用 try-with-resources 创建结果集
               s.executeQuery("SELECT employee_typ(315, 'Francis', 'Logan', 'FLOGAN',\n" // 查询员工类型构造函数
                   + "    '555.777.2222', DATE '2004-05-01', 'SA_MAN', 11000, .15, 101, 110,\n" // 传入员工详细信息
                   + "     address_typ('376 Mission', 'San Francisco', 'CA', '94222'))")) { // 传入嵌套的地址类型
        assertThat(r.next(), is(true)); // 断言结果集有数据
        Struct obj = r.getObject(1, Struct.class); // 获取第一列的结构化对象
        Object[] data = obj.getAttributes(); // 获取结构化对象的所有属性
        assertThat(data[0], is(315)); // 断言 employee_id 为 315
        assertThat(data[1], is("Francis")); // 断言 first_name 为 Francis
        assertThat(data[2], is("Logan")); // 断言 last_name 为 Logan
        assertThat(data[3], is("FLOGAN")); // 断言 email 为 FLOGAN
        assertThat(data[4], is("555.777.2222")); // 断言 phone_number 为 555.777.2222
        assertThat(data[5], is(java.sql.Date.valueOf("2004-05-01"))); // 断言 hire_date 为 2004-05-01
        assertThat(data[6], is("SA_MAN")); // 断言 job_id 为 SA_MAN
        assertThat(data[7], is(11000)); // 断言 salary 为 11000
        assertThat(data[8], is(new BigDecimal(".15"))); // 断言 commission_pct 为 0.15
        assertThat(data[9], is(101)); // 断言 manager_id 为 101
        assertThat(data[10], is(110)); // 断言 department_id 为 110
        Struct address = (Struct) data[11]; // 获取嵌套的地址结构化对象
        assertArrayEquals(address.getAttributes(), // 断言地址属性数组
            new Object[] { "376 Mission", "San Francisco", "CA", "94222" }); // 期望的地址值
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testUnnest() 方法测试 UNNEST 操作符的错误处理
   * 该测试用例对应 JIRA 问题 CALCITE-6361
   * 测试当 UNNEST 的输入数据不是集合类型时，是否能正确抛出异常
   * 测试内容包括：
   * 1. 创建简单类型 simple
   * 2. 创建包含 simple 数组的类型 vec
   * 3. 创建包含 vec 类型的表 T
   * 4. 尝试对非集合类型使用 UNNEST，验证是否抛出正确异常
   * @throws SQLException 如果执行过程中出现 SQL 异常则抛出
   */
  @Test void testUnnest() throws SQLException { // 定义测试方法，测试 UNNEST 操作符的错误处理
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("CREATE TYPE simple AS (s INT, t BOOLEAN)"); // 创建简单类型 simple，包含 INT 和 BOOLEAN 字段
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("CREATE TYPE vec AS (fields SIMPLE ARRAY)"); // 创建类型 vec，包含 simple 类型的数组字段
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute(" CREATE TABLE T(col vec)"); // 创建表 T，包含 vec 类型的列 col
      assertThat(b, is(false)); // 断言返回值为 false
      SQLException e = // 捕获 SQL 异常
          assertThrows( // 断言抛出异常
              SQLException.class, // 期望抛出 SQLException
              () -> s.executeQuery("SELECT A.* FROM (T CROSS JOIN UNNEST(T.col) A)")); // 执行 UNNEST 查询，期望失败
      assertThat(e.getMessage(), containsString("UNNEST argument must be a collection")); // 断言异常消息包含指定文本
    } // 自动关闭资源
  }

  /**
   * testCreateType() 方法测试创建用户定义类型（UDT）的各种场景
   * 测试内容包括：
   * 1. 创建基本类型（BIGINT 别名）
   * 2. 创建嵌套类型（包含其他自定义类型）
   * 3. 使用 CREATE OR REPLACE 修改现有类型
   * 4. 在表中使用自定义类型
   * 5. 向自定义类型列插入数据
   * 6. 类型转换（CAST）操作
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateType() throws Exception { // 定义测试方法，测试创建用户定义类型功能
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create type mytype1 as BIGINT"); // 创建类型 mytype1，作为 BIGINT 的别名
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create or replace type mytype2 as (i int not null, jj mytype1)"); // 创建或替换类型 mytype2，包含非空整型字段 i 和 mytype1 类型的字段 jj
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create type mytype3 as (i int not null, jj mytype2)"); // 创建类型 mytype3，包含非空整型字段 i 和 mytype2 类型的字段 jj（三层嵌套）
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create or replace type mytype1 as DOUBLE"); // 创建或替换类型 mytype1，改为 DOUBLE 的别名
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create table t (c mytype1 NOT NULL)"); // 创建表 t，包含非空的 mytype1 类型列 c
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create type mytype4 as BIGINT"); // 创建类型 mytype4，作为 BIGINT 的别名
      assertThat(b, is(false)); // 断言返回值为 false
      int x = s.executeUpdate("insert into t values 12.0"); // 向表 t 插入值 12.0（DOUBLE 类型）
      assertThat(x, is(1)); // 断言影响行数为 1
      x = s.executeUpdate("insert into t values 3.0"); // 向表 t 插入值 3.0（DOUBLE 类型）
      assertThat(x, is(1)); // 断言影响行数为 1
      try (ResultSet r = s.executeQuery("select CAST(c AS mytype4) from t")) { // 查询表 t 并将 c 列转换为 mytype4 类型
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt(1), is(12)); // 断言第一行值为 12（DOUBLE 12.0 转换为 BIGINT 12）
        assertThat(r.next(), is(true)); // 断言结果集还有数据
        assertThat(r.getInt(1), is(3)); // 断言第二行值为 3（DOUBLE 3.0 转换为 BIGINT 3）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testDropType() 方法测试删除用户定义类型（UDT）的功能
   * 测试内容包括：
   * 1. 创建一个自定义类型
   * 2. 使用 DROP TYPE 删除该类型
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testDropType() throws Exception { // 定义测试方法，测试删除用户定义类型功能
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create type mytype1 as BIGINT"); // 创建类型 mytype1，作为 BIGINT 的别名
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("drop type mytype1"); // 删除类型 mytype1
      assertThat(b, is(false)); // 断言返回值为 false
    } // 自动关闭资源
  }

  /**
   * testCreateTable() 方法测试创建表的各种场景
   * 测试内容包括：
   * 1. 基本的 CREATE TABLE 语句
   * 2. 向表中插入数据
   * 3. 查询表数据并验证结果
   * 4. 结构化类型列的可空性设置（CALCITE-2464）
   * 5. 使用完全限定名称定义用户自定义类型
   * 6. CREATE OR REPLACE TABLE 语法（覆盖已存在的表）
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateTable() throws Exception { // 定义测试方法，测试创建表功能
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create table t (i int not null)"); // 创建表 t，包含一个非空整型列 i
      assertThat(b, is(false)); // 断言返回值为 false
      int x = s.executeUpdate("insert into t values 1"); // 向表 t 插入值 1
      assertThat(x, is(1)); // 断言影响行数为 1
      x = s.executeUpdate("insert into t values 3"); // 向表 t 插入值 3
      assertThat(x, is(1)); // 断言影响行数为 1
      try (ResultSet r = s.executeQuery("select sum(i) from t")) { // 查询表 t 的 i 列总和
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt(1), is(4)); // 断言总和为 4（1 + 3）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      // CALCITE-2464: 允许为结构化类型的列设置可空性
      b = s.execute("create type mytype as (i int)"); // 创建结构化类型 mytype，包含整型字段 i
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create table w (i int not null, j mytype)"); // 创建表 w，包含非空整型列 i 和可空的 mytype 类型列 j
      assertThat(b, is(false)); // 断言返回值为 false
      x = s.executeUpdate("insert into w values (1, NULL)"); // 向表 w 插入数据，j 列为 NULL
      assertThat(x, is(1)); // 断言影响行数为 1

      // 测试用户自定义类型作为组件标识符
      b = s.execute("create schema a"); // 创建模式 a
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create schema a.b"); // 创建嵌套模式 a.b
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create type a.b.mytype as (i varchar(5))"); // 在模式 a.b 中创建类型 mytype，包含 varchar(5) 字段
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create table t2 (i int not null, j a.b.mytype)"); // 创建表 t2，包含非空整型列 i 和 a.b.mytype 类型列 j
      assertThat(b, is(false)); // 断言返回值为 false
      x = s.executeUpdate("insert into t2 values (1, NULL)"); // 向表 t2 插入数据，j 列为 NULL
      assertThat(x, is(1)); // 断言影响行数为 1

      assertDoesNotThrow(() -> { // 断言不抛出异常
        s.execute("create or replace table t2 (i int not null)"); // 执行 CREATE OR REPLACE TABLE，覆盖表 t2，只保留一个列
        s.executeUpdate("insert into t2 values (1)"); // 向覆盖后的表 t2 插入数据
      }, "REPLACE must recreate the table, leaving only one column"); // 错误信息
    } // 自动关闭资源
  }

  /**
   * testCreateTableLike() 方法测试 CREATE TABLE ... LIKE 语法
   * 该测试用例对应 JIRA 问题 CALCITE-6022，支持在服务器模块中使用 CREATE TABLE ... LIKE DDL
   * 测试内容包括：
   * 1. 创建源表 t
   * 2. 使用 LIKE 子句创建新表 t2，复制表 t 的结构
   * 3. 向新表 t2 插入数据
   * 4. 查询新表 t2 的数据并验证结果
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateTableLike() throws Exception { // 定义测试方法，测试 CREATE TABLE ... LIKE 语法
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      s.execute("create table t (i int not null)"); // 创建源表 t，包含非空整型列 i
      s.execute("create table t2 like t"); // 使用 LIKE 子句创建表 t2，复制表 t 的结构
      int x = s.executeUpdate("insert into t2 values 1"); // 向表 t2 插入值 1
      assertThat(x, is(1)); // 断言影响行数为 1
      x = s.executeUpdate("insert into t2 values 3"); // 向表 t2 插入值 3
      assertThat(x, is(1)); // 断言影响行数为 1
      try (ResultSet r = s.executeQuery("select sum(i) from t2")) { // 查询表 t2 的 i 列总和
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt(1), is(4)); // 断言总和为 4（1 + 3）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testCreateTableLikeWithStoredGeneratedColumn() 方法测试 CREATE TABLE ... LIKE 语法与存储生成列（Stored Generated Column）
   * 该测试用例对应 JIRA 问题 CALCITE-6022，支持在服务器模块中使用 CREATE TABLE ... LIKE DDL
   * 测试内容包括：
   * 1. 创建包含存储生成列和默认值的源表 t
   * 2. 使用 INCLUDING DEFAULTS INCLUDING GENERATED 子句创建表 t2，复制所有列定义
   * 3. 向表 t2 插入数据，验证生成列和默认值是否正确计算
   * 4. 验证执行计划是否正确处理生成列
   * 5. 验证不能直接向生成列插入值
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateTableLikeWithStoredGeneratedColumn() throws Exception { // 定义测试方法，测试 LIKE 语法与存储生成列
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      s.execute("create table t (\n" // 创建源表 t
          + " h int not null,\n" // 列 h：非空整型
          + " i int,\n" // 列 i：可空整型
          + " j int as (i + 1) stored,\n" // 列 j：存储生成列，值为 i + 1
          + " k int default -1)\n"); // 列 k：整型，默认值为 -1
      s.execute("create table t2 like t including defaults including generated"); // 创建表 t2，复制表 t 的结构，包括默认值和生成列

      int x = s.executeUpdate("insert into t2 (h, i) values (3, 4)"); // 向表 t2 插入数据，只指定 h 和 i 列
      assertThat(x, is(1)); // 断言影响行数为 1

      final String sql1 = "explain plan for\n" // 定义 SQL 语句，查看 INSERT 的执行计划
          + "insert into t2 (h, i) values (3, 4)";
      try (ResultSet r = s.executeQuery(sql1)) { // 执行 EXPLAIN PLAN 查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        final String plan = "" // 定义期望的执行计划
            + "EnumerableTableModify(table=[[T2]], operation=[INSERT], flattened=[false])\n" // 表修改操作
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t1, $t2)], proj#0..1=[{exprs}], J=[$t3], K=[$t4])\n" // 计算表达式，包括生成列 J 和默认值 K
            + "    EnumerableValues(tuples=[[{ 3, 4 }]])\n"; // 值表达式
        assertThat(r.getString(1), isLinux(plan)); // 断言执行计划与期望一致
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      try (ResultSet r = s.executeQuery("select * from t2")) { // 查询表 t2 的所有数据
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt("H"), is(3)); // 断言 H 列值为 3
        assertThat(r.wasNull(), is(false)); // 断言 H 列不为 NULL
        assertThat(r.getInt("I"), is(4)); // 断言 I 列值为 4
        assertThat(r.getInt("J"), is(5)); // 断言 J 列值为 5（生成列：i + 1 = 4 + 1）
        assertThat(r.getInt("K"), is(-1)); // 断言 K 列值为 -1（默认值）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      SQLException e = // 捕获 SQL 异常
          assertThrows( // 断言抛出异常
              SQLException.class, () -> s.executeUpdate("insert into t2 values (3, 4, 5, 6)")); // 尝试向生成列 J 插入值，期望失败
      assertThat(e.getMessage(), containsString("Cannot INSERT into generated column 'J'")); // 断言异常消息包含指定文本
    } // 自动关闭资源
  }

  /**
   * testCreateTableLikeWithVirtualGeneratedColumn() 方法测试 CREATE TABLE ... LIKE 语法与虚拟生成列（Virtual Generated Column）
   * 该测试用例对应 JIRA 问题 CALCITE-6022，支持在服务器模块中使用 CREATE TABLE ... LIKE DDL
   * 测试内容包括：
   * 1. 创建包含虚拟生成列的源表 t
   * 2. 使用 INCLUDING DEFAULTS INCLUDING GENERATED 子句创建表 t2，复制所有列定义
   * 3. 向表 t2 插入数据，验证虚拟生成列是否正确计算
   * 4. 验证表 t 和 t2 的 INSERT 执行计划是否一致
   * 5. 验证不能直接向生成列插入值
   * 虚拟生成列与存储生成列的区别：虚拟列不占用存储空间，每次查询时动态计算
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateTableLikeWithVirtualGeneratedColumn() throws Exception { // 定义测试方法，测试 LIKE 语法与虚拟生成列
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      s.execute("create table t (\n" // 创建源表 t
          + " h int not null,\n" // 列 h：非空整型
          + " i int,\n" // 列 i：可空整型
          + " j int as (i + 1) virtual)\n"); // 列 j：虚拟生成列，值为 i + 1，不存储，查询时动态计算
      s.execute("create table t2 like t including defaults including generated"); // 创建表 t2，复制表 t 的结构，包括默认值和生成列

      int x = s.executeUpdate("insert into t2 (h, i) values (3, 4)"); // 向表 t2 插入数据，只指定 h 和 i 列
      assertThat(x, is(1)); // 断言影响行数为 1

      final String sql1 = "explain plan for\n" // 定义 SQL 语句，查看表 t 的 INSERT 执行计划
          + "insert into t (h, i) values (3, 4)";
      try (ResultSet r = s.executeQuery(sql1)) { // 执行表 t 的 EXPLAIN PLAN 查询
        final String sql2 = "explain plan for\n" // 定义 SQL 语句，查看表 t2 的 INSERT 执行计划
            + "insert into t2 (h, i) values (3, 4)";
        assertThat(r.next(), is(true)); // 断言结果集有数据
        final String plan = r.getString(1); // 获取表 t 的执行计划
        assertThat(r.next(), is(false)); // 断言没有更多数据

        ResultSet r2 = s.executeQuery(sql2); // 执行表 t2 的 EXPLAIN PLAN 查询
        assertThat(r2.next(), is(true)); // 断言结果集有数据
        assertThat(r2.getString(1).replace("T2", "T"), is(plan)); // 断言表 t2 的执行计划（替换 T2 为 T）与表 t 的执行计划一致
        assertThat(r2.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      try (ResultSet r = s.executeQuery("select * from t2")) { // 查询表 t2 的所有数据
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt("H"), is(3)); // 断言 H 列值为 3
        assertThat(r.wasNull(), is(false)); // 断言 H 列不为 NULL
        assertThat(r.getInt("I"), is(4)); // 断言 I 列值为 4
        assertThat(r.getInt("J"), is(5)); // 断言 J 列值为 5（虚拟生成列：i + 1 = 4 + 1）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      SQLException e = // 捕获 SQL 异常
          assertThrows( // 断言抛出异常
              SQLException.class, () -> s.executeUpdate("insert into t2 values (3, 4, 5)")); // 尝试向生成列 J 插入值，期望失败
      assertThat(e.getMessage(), containsString("Cannot INSERT into generated column 'J'")); // 断言异常消息包含指定文本
    } // 自动关闭资源
  }

  /**
   * testCreateTableLikeWithoutLikeOptions() 方法测试 CREATE TABLE ... LIKE 语法不包含任何 LIKE 选项
   * 该测试用例对应 JIRA 问题 CALCITE-6022，支持在服务器模块中使用 CREATE TABLE ... LIKE DDL
   * 测试内容包括：
   * 1. 创建包含存储生成列和默认值的源表 t
   * 2. 使用 LIKE 子句但不带任何选项创建表 t2，只复制列名和类型信息，不复制生成表达式和默认值
   * 3. 向表 t2 插入数据，验证生成列和默认值是否被排除
   * 4. 验证执行计划是否正确处理
   * 5. 验证可以向所有列（包括原本是生成列和默认值的列）插入值
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateTableLikeWithoutLikeOptions() throws Exception { // 定义测试方法，测试 LIKE 语法不带选项
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      s.execute("create table t (\n" // 创建源表 t
          + " h int not null,\n" // 列 h：非空整型
          + " i int,\n" // 列 i：可空整型
          + " j int as (i + 1) stored,\n" // 列 j：存储生成列，值为 i + 1
          + " k int default -1)"); // 列 k：整型，默认值为 -1
      // 在表 t2 中，只从表 t 复制列和类型信息，不包括生成表达式和默认表达式
      s.execute("create table t2 like t"); // 创建表 t2，只复制列名和类型，不复制生成表达式和默认值

      int x = s.executeUpdate("insert into t2 (h, i) values (3, 4)"); // 向表 t2 插入数据，只指定 h 和 i 列
      assertThat(x, is(1)); // 断言影响行数为 1

      final String sql1 = "explain plan for\n" // 定义 SQL 语句，查看 INSERT 的执行计划
          + "insert into t2 (h, i) values (3, 4)";
      try (ResultSet r = s.executeQuery(sql1)) { // 执行 EXPLAIN PLAN 查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        final String plan = "" // 定义期望的执行计划
            + "EnumerableTableModify(table=[[T2]], operation=[INSERT], flattened=[false])\n" // 表修改操作
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[null:INTEGER], proj#0..2=[{exprs}], K=[$t2])\n" // 计算表达式，K 列为 null（没有默认值）
            + "    EnumerableValues(tuples=[[{ 3, 4 }]])\n"; // 值表达式
        assertThat(r.getString(1), isLinux(plan)); // 断言执行计划与期望一致
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      try (ResultSet r = s.executeQuery("select * from t2")) { // 查询表 t2 的所有数据
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt("H"), is(3)); // 断言 H 列值为 3
        assertThat(r.wasNull(), is(false)); // 断言 H 列不为 NULL
        assertThat(r.getInt("I"), is(4)); // 断言 I 列值为 4
        assertThat(r.getInt("J"), is(0)); // 断言 J 列值为 0（排除了生成列，所以是默认值 0）
        assertThat(r.wasNull(), is(true)); // 断言 J 列为 NULL（因为排除了生成列）
        assertThat(r.getInt("K"), is(0)); // 断言 K 列值为 0（排除了默认值，所以是默认值 0）
        assertThat(r.wasNull(), is(true)); // 断言 K 列为 NULL（因为排除了默认值）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      x = s.executeUpdate("insert into t2 values (3, 4, 5, 6)"); // 向表 t2 插入所有列的值（包括原本是生成列和默认值的列）
      assertThat(x, is(1)); // 断言影响行数为 1
    } // 自动关闭资源
  }

  /**
   * testTruncateTable() 方法测试 TRUNCATE TABLE 语句的错误处理
   * 测试内容包括：
   * 1. 创建一个测试表
   * 2. 尝试使用 TRUNCATE TABLE ... RESTART IDENTITY 语法，验证是否抛出正确异常
   * 3. 验证异常消息包含 "RESTART IDENTIFY is not supported"
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testTruncateTable() throws Exception { // 定义测试方法，测试 TRUNCATE TABLE 语句的错误处理
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
        Statement s = c.createStatement()) { // 创建语句对象
      final boolean b = s.execute("create table t (i int not null)"); // 创建表 t，包含非空整型列 i
      assertThat(b, is(false)); // 断言返回值为 false

      final String errMsg = // 捕获异常消息
          assertThrows(SQLException.class, // 断言抛出 SQLException
              () -> s.execute("truncate table t restart identity")).getMessage(); // 尝试执行 TRUNCATE TABLE ... RESTART IDENTITY，期望失败
      assertThat(errMsg, containsString("RESTART IDENTIFY is not supported")); // 断言异常消息包含指定文本
    } // 自动关闭资源
  }

  /**
   * testCreateFunction() 方法测试 CREATE FUNCTION 语句的错误处理
   * 测试内容包括：
   * 1. 创建一个模式 s
   * 2. 尝试执行 CREATE FUNCTION 语句，验证是否抛出正确异常
   * 3. 验证异常消息包含 "CREATE FUNCTION is not supported"
   * 注意：Calcite 服务器模块当前不支持 CREATE FUNCTION 语句
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testCreateFunction() throws Exception { // 定义测试方法，测试 CREATE FUNCTION 语句的错误处理
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create schema s"); // 创建模式 s
      assertThat(b, is(false)); // 断言返回值为 false
      try { // 尝试执行 CREATE FUNCTION 语句
        boolean f = s.execute("create function if not exists s.t\n" // 尝试创建函数 s.t
                + "as 'org.apache.calcite.udf.TableFun.demoUdf'\n" // 指定函数实现类
                + "using jar 'file:/path/udf/udf-0.0.1-SNAPSHOT.jar'"); // 指定 JAR 文件路径
        fail("expected error, got " + f); // 如果没有抛出异常，则测试失败
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
            containsString("CREATE FUNCTION is not supported")); // 包含指定文本
      }
    } // 自动关闭资源
  }

  /**
   * testDropFunction() 方法测试 DROP FUNCTION 语句的各种场景
   * 测试内容包括：
   * 1. 创建一个模式 s
   * 2. 测试 DROP FUNCTION IF EXISTS 语法（函数不存在时不报错）
   * 3. 测试 DROP FUNCTION 语法（函数不存在时报错）
   * 4. 通过编程方式添加函数到根模式，然后删除
   * 5. 测试函数名称的大小写敏感性（Calcite 默认将标识符转换为大写）
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testDropFunction() throws Exception { // 定义测试方法，测试 DROP FUNCTION 语句的各种场景
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create schema s"); // 创建模式 s
      assertThat(b, is(false)); // 断言返回值为 false

      boolean f = s.execute("drop function if exists t"); // 尝试删除函数 t（如果存在），函数不存在也不报错
      assertThat(f, is(false)); // 断言返回值为 false

      try { // 尝试删除不存在的函数 t（不带 IF EXISTS）
        boolean f2 = s.execute("drop function t"); // 执行 DROP FUNCTION t
        assertThat(f2, is(false)); // 断言返回值为 false
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
                containsString("Error while executing SQL \"drop function t\":" // 包含错误信息
                        + " At line 1, column 15: Function 'T' not found")); // 函数 T 未找到（注意被转换为大写）
      }

      CalciteConnection calciteConnection = (CalciteConnection) c; // 将连接转换为 CalciteConnection 类型
      calciteConnection.getRootSchema().add("T", new Function() { // 向根模式添加函数 T（大写）
        @Override public List<FunctionParameter> getParameters() { // 重写 getParameters 方法
          return new ArrayList<>(); // 返回空的参数列表
        }
      });

      boolean f3 = s.execute("drop function t"); // 删除函数 t（会被转换为大写 T）
      assertThat(f3, is(false)); // 断言返回值为 false

      // 测试大小写敏感的函数名
      calciteConnection.getRootSchema().add("t", new Function() { // 向根模式添加函数 t（小写）
        @Override public List<FunctionParameter> getParameters() { // 重写 getParameters 方法
          return new ArrayList<>(); // 返回空的参数列表
        }
      });

      try { // 尝试删除函数 t（会被转换为大写 T，但实际存在的是小写 t）
        boolean f4 = s.execute("drop function t"); // 执行 DROP FUNCTION t
        assertThat(f4, is(false)); // 断言返回值为 false
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
                containsString("Error while executing SQL \"drop function t\":" // 包含错误信息
                        + " At line 1, column 15: Function 'T' not found")); // 函数 T 未找到（因为 t 被转换为大写）
      }
    } // 自动关闭资源
  }

  /**
   * testInsertCastedValueOfCompositeUdt() 方法测试向表中插入复合用户定义类型（UDT）的转换值
   * 该测试用例对应 JIRA 问题 CALCITE-3046
   * 测试内容包括：
   * 1. 创建复合类型 mytype，包含两个整型字段
   * 2. 创建包含 mytype 类型列的表 w
   * 3. 使用 CAST 将子查询结果转换为 mytype 类型并插入
   * 4. 验证插入操作成功
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testInsertCastedValueOfCompositeUdt() throws Exception { // 定义测试方法，测试插入复合 UDT 的转换值
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create type mytype as (i int, j int)"); // 创建复合类型 mytype，包含两个整型字段
      assertThat(b, is(false)); // 断言返回值为 false
      b = s.execute("create table w (i int not null, j mytype)"); // 创建表 w，包含非空整型列 i 和 mytype 类型列 j
      assertThat(b, is(false)); // 断言返回值为 false
      int x = s.executeUpdate("insert into w " // 向表 w 插入数据
          + "values (1, cast((select j from w limit 1) as mytype))"); // 使用 CAST 将子查询结果转换为 mytype 类型
      assertThat(x, is(1)); // 断言影响行数为 1
    } // 自动关闭资源
  }

  /**
   * testInsertCreateNewCompositeUdt() 方法测试向表中插入新创建的复合用户定义类型（UDT）值
   * 测试内容包括：
   * 1. 创建复合类型 mytype，包含两个整型字段
   * 2. 创建包含 mytype 类型列的表 w
   * 3. 使用类型构造函数 mytype(1, 1) 创建新实例并插入
   * 4. 查询表 w 并验证插入的数据是否正确
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testInsertCreateNewCompositeUdt() throws Exception { // 定义测试方法，测试插入新创建的复合 UDT 值
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
        Statement s = c.createStatement()) { // 创建语句对象
      boolean b = s.execute("create type mytype as (i int, j int)"); // 创建复合类型 mytype，包含两个整型字段
      assertFalse(b); // 断言返回值为 false
      b = s.execute("create table w (i int not null, j mytype)"); // 创建表 w，包含非空整型列 i 和 mytype 类型列 j
      assertFalse(b); // 断言返回值为 false
      int x = s.executeUpdate("insert into w " // 向表 w 插入数据
          + "values (1, mytype(1, 1))"); // 使用类型构造函数 mytype(1, 1) 创建新实例
      assertThat(x, is(1)); // 断言影响行数为 1

      try (ResultSet r = s.executeQuery("select * from w")) { // 查询表 w 的所有数据
        assertTrue(r.next()); // 断言结果集有数据
        assertThat(r.getInt("i"), is(1)); // 断言 i 列值为 1
        assertArrayEquals(r.getObject("j", Struct.class).getAttributes(), new Object[] {1, 1}); // 断言 j 列的属性数组为 [1, 1]
        assertFalse(r.next()); // 断言没有更多数据
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testStoredGeneratedColumn() 方法全面测试存储生成列（Stored Generated Column）的各种 INSERT 场景
   * 存储生成列会在插入数据时计算并存储值，占用存储空间，但查询时不需要重新计算
   * 测试内容包括：
   * 1. 创建包含存储生成列的表
   * 2. 成功插入数据并验证生成列的值
   * 3. 验证执行计划是否正确处理生成列
   * 4. 测试各种错误场景：
   *    - 不指定列列表时值数量不足
   *    - 不指定列列表时值数量过多
   *    - 不指定列列表时值数量正确但包含生成列
   * 5. 测试正确的插入方式：
   *    - 指定列列表，省略生成列
   *    - 指定列列表，包含生成列但使用 DEFAULT
   *    - 重新排序列的顺序
   * 6. 测试批量插入
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testStoredGeneratedColumn() throws Exception { // 定义测试方法，全面测试存储生成列的各种 INSERT 场景
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      final String sql0 = "create table t (\n" // 定义创建表的 SQL 语句
          + " h int not null,\n" // 列 h：非空整型
          + " i int,\n" // 列 i：可空整型
          + " j int as (i + 1) stored)"; // 列 j：存储生成列，值为 i + 1
      boolean b = s.execute(sql0); // 执行创建表语句
      assertThat(b, is(false)); // 断言返回值为 false

      int x; // 声明变量用于存储影响行数

      // 成功插入一行数据
      x = s.executeUpdate("insert into t (h, i) values (3, 4)"); // 向表 t 插入数据，只指定 h 和 i 列
      assertThat(x, is(1)); // 断言影响行数为 1

      final String sql1 = "explain plan for\n" // 定义 SQL 语句，查看 INSERT 的执行计划
          + "insert into t (h, i) values (3, 4)";
      try (ResultSet r = s.executeQuery(sql1)) { // 执行 EXPLAIN PLAN 查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        final String plan = "" // 定义期望的执行计划
            + "EnumerableTableModify(table=[[T]], operation=[INSERT], flattened=[false])\n" // 表修改操作
            + "  EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t1, $t2)], proj#0..1=[{exprs}], J=[$t3])\n" // 计算表达式，包括生成列 J
            + "    EnumerableValues(tuples=[[{ 3, 4 }]])\n"; // 值表达式
        assertThat(r.getString(1), isLinux(plan)); // 断言执行计划与期望一致
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      try (ResultSet r = s.executeQuery("select * from t")) { // 查询表 t 的所有数据
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt("H"), is(3)); // 断言 H 列值为 3
        assertThat(r.wasNull(), is(false)); // 断言 H 列不为 NULL
        assertThat(r.getInt("I"), is(4)); // 断言 I 列值为 4
        assertThat(r.getInt("J"), is(5)); // 断言 J 列值为 5（生成列：i + 1 = 4 + 1）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      // 不指定目标列列表；提供的值数量不足
      try { // 尝试插入数据，只提供 2 个值但表有 3 列
        x = s.executeUpdate("insert into t values (2, 3)"); // 执行 INSERT 语句
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
            containsString("Number of INSERT target columns (3) does not equal " // 包含错误信息
                + "number of source items (2)")); // 目标列数（3）不等于源项数（2）
      }

      // 不指定目标列列表；提供的值数量过多
      try { // 尝试插入数据，提供 4 个值但表只有 3 列
        x = s.executeUpdate("insert into t values (3, 4, 5, 6)"); // 执行 INSERT 语句
        fail("expected error, got " + x); // 如果没有抛出异常，则测试失败
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
            containsString("Number of INSERT target columns (3) does not equal " // 包含错误信息
                + "number of source items (4)")); // 目标列数（3）不等于源项数（4）
      }

      // 不指定目标列列表；
      // 源项数等于目标列数；
      // 但其中一个目标列是虚拟列（生成列）
      try { // 尝试插入数据，提供 3 个值但其中一个是生成列
        x = s.executeUpdate("insert into t values (3, 4, 5)"); // 执行 INSERT 语句
        fail("expected error, got " + x); // 如果没有抛出异常，则测试失败
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
            containsString("Cannot INSERT into generated column 'J'")); // 不能向生成列插入值
      }

      // 显式指定目标列列表，省略生成列
      x = s.executeUpdate("insert into t (h, i) values (1, 2)"); // 向表 t 插入数据，只指定 h 和 i 列
      assertThat(x, is(1)); // 断言影响行数为 1

      // 显式指定目标列列表，包含生成列但赋值为 DEFAULT
      x = s.executeUpdate("insert into t (h, i, j) values (1, 2, DEFAULT)"); // 向表 t 插入数据，j 列使用 DEFAULT
      assertThat(x, is(1)); // 断言影响行数为 1

      // 与前一个类似，重新排序列的顺序
      x = s.executeUpdate("insert into t (h, j, i) values (1, DEFAULT, 3)"); // 向表 t 插入数据，重新排序列顺序
      assertThat(x, is(1)); // 断言影响行数为 1

      // 目标列列表存在；
      // 目标列数等于非虚拟列数；
      // 但其中一个目标列是虚拟列（生成列）
      try { // 尝试插入数据，只指定 h 和 j 列，但 j 是生成列
        x = s.executeUpdate("insert into t (h, j) values (1, 3)"); // 执行 INSERT 语句
        fail("expected error, got " + x); // 如果没有抛出异常，则测试失败
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
            containsString("Cannot INSERT into generated column 'J'")); // 不能向生成列插入值
      }

      // 目标列列表存在且包含所有列；
      // 生成列的表达式不是 DEFAULT
      try { // 尝试插入数据，j 列使用表达式而不是 DEFAULT
        x = s.executeUpdate("insert into t (h, i, j) values (2, 3, 3 + 1)"); // 执行 INSERT 语句
        fail("expected error, got " + x); // 如果没有抛出异常，则测试失败
      } catch (SQLException e) { // 捕获 SQL 异常
        assertThat(e.getMessage(), // 断言异常消息
            containsString("Cannot INSERT into generated column 'J'")); // 不能向生成列插入值
      }
      x = s.executeUpdate("insert into t (h, i) values (0, 1)"); // 向表 t 插入数据
      assertThat(x, is(1)); // 断言影响行数为 1
      x = s.executeUpdate("insert into t (h, i, j) values (0, 1, DEFAULT)"); // 向表 t 插入数据，j 列使用 DEFAULT
      assertThat(x, is(1)); // 断言影响行数为 1
      x = s.executeUpdate("insert into t (j, i, h) values (DEFAULT, NULL, 7)"); // 向表 t 插入数据，重新排序并使用 NULL
      assertThat(x, is(1)); // 断言影响行数为 1
      x = s.executeUpdate("insert into t (h, i) values (6, 5), (7, 4)"); // 向表 t 批量插入两行数据
      assertThat(x, is(2)); // 断言影响行数为 2
      try (ResultSet r = s.executeQuery("select sum(i), count(*) from t")) { // 查询表 t 的 i 列总和和行数
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt(1), is(22)); // 断言 i 列总和为 22
        assertThat(r.getInt(2), is(10)); // 断言行数为 10
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testStoredGeneratedColumn2() 方法测试存储生成列的查询优化
   * 该测试使用 @Disabled 注解，表示当前尚未实现或无法正常工作
   * 测试内容包括：
   * 1. 创建包含存储生成列的表
   * 2. 查询时使用生成列的表达式作为条件（j = i + 1）
   * 3. 验证优化器是否能够利用约束条件优化查询
   * 4. 期望执行计划只包含表扫描，不包含过滤条件
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Disabled("not working yet") // 禁用此测试，因为功能尚未实现
  @Test void testStoredGeneratedColumn2() throws Exception { // 定义测试方法，测试存储生成列的查询优化
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      final String sql = "create table t (\n" // 定义创建表的 SQL 语句
          + " h int not null,\n" // 列 h：非空整型
          + " i int,\n" // 列 i：可空整型
          + " j int as (i + 1) stored)"; // 列 j：存储生成列，值为 i + 1
      boolean b = s.execute(sql); // 执行创建表语句
      assertThat(b, is(false)); // 断言返回值为 false

      // 优化器使用约束来优化掉条件
      final String sql2 = "explain plan for\n" // 定义 SQL 语句，查看查询的执行计划
          + "select * from t where j = i + 1"; // 查询条件使用生成列的表达式
      final String plan = "EnumerableTableScan(table=[[T]])\n"; // 期望的执行计划：只包含表扫描，没有过滤条件
      try (ResultSet r = s.executeQuery(sql2)) { // 执行 EXPLAIN PLAN 查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getString(1), is(plan)); // 断言执行计划与期望一致
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testVirtualColumn() 方法测试虚拟生成列（Virtual Generated Column）的功能
   * 虚拟生成列不占用存储空间，每次查询时动态计算
   * 测试内容包括：
   * 1. 创建包含虚拟生成列的表
   * 2. 向表中插入数据
   * 3. 查询表数据，验证虚拟列的值是否正确计算
   * 4. 验证执行计划中虚拟列被替换为表达式
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testVirtualColumn() throws Exception { // 定义测试方法，测试虚拟生成列的功能
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      final String sql0 = "create table t (\n" // 定义创建表的 SQL 语句
          + " h int not null,\n" // 列 h：非空整型
          + " i int,\n" // 列 i：可空整型
          + " j int as (i + 1) virtual)"; // 列 j：虚拟生成列，值为 i + 1，不存储，查询时动态计算
      boolean b = s.execute(sql0); // 执行创建表语句
      assertThat(b, is(false)); // 断言返回值为 false

      int x = s.executeUpdate("insert into t (h, i) values (1, 2)"); // 向表 t 插入数据，只指定 h 和 i 列
      assertThat(x, is(1)); // 断言影响行数为 1

      // 在执行计划中，"j" 被替换为 "i + 1"
      final String sql = "select * from t"; // 定义 SQL 语句，查询表 t 的所有数据
      try (ResultSet r = s.executeQuery(sql)) { // 执行查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getInt(1), is(1)); // 断言第 1 列（h）值为 1
        assertThat(r.getInt(2), is(2)); // 断言第 2 列（i）值为 2
        assertThat(r.getInt(3), is(3)); // 断言第 3 列（j）值为 3（虚拟生成列：i + 1 = 2 + 1）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      final String plan = "" // 定义期望的执行计划
          + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[1], expr#3=[+($t1, $t2)], proj#0..1=[{exprs}], J=[$t3])\n" // 计算表达式，虚拟列 J 被替换为 i + 1
          + "  EnumerableTableScan(table=[[T]])\n"; // 表扫描
      try (ResultSet r = s.executeQuery("explain plan for " + sql)) { // 执行 EXPLAIN PLAN 查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getString(1), isLinux(plan)); // 断言执行计划与期望一致
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testVirtualColumnWithFunctions() 方法测试虚拟生成列使用内置函数和库函数
   * 测试内容包括：
   * 1. 创建包含多个虚拟生成列的表，使用不同的函数
   * 2. 使用 char_length() 函数计算字符串长度
   * 3. 使用 rtrim() 函数去除字符串右侧空格
   * 4. 向表中插入数据
   * 5. 查询表数据，验证虚拟列的值是否正确计算
   * 6. 验证执行计划中虚拟列被替换为函数调用
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testVirtualColumnWithFunctions() throws Exception { // 定义测试方法，测试虚拟生成列使用函数
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      // 测试内置函数和库函数
      final String create = "create table t1 (\n" // 定义创建表的 SQL 语句
          + " h varchar(3) not null,\n" // 列 h：非空字符串，最多 3 个字符
          + " i varchar(3),\n" // 列 i：可空字符串，最多 3 个字符
          + " j int not null as (char_length(h)) virtual,\n" // 列 j：虚拟生成列，使用 char_length() 函数计算 h 的长度
          + " k varchar(3) null as (rtrim(i)) virtual)"; // 列 k：虚拟生成列，使用 rtrim() 函数去除 i 的右侧空格
      boolean b = s.execute(create); // 执行创建表语句
      assertThat(b, is(false)); // 断言返回值为 false

      int x = s.executeUpdate("insert into t1 (h, i) values ('abc', 'de ')"); // 向表 t1 插入数据
      assertThat(x, is(1)); // 断言影响行数为 1

      // 在执行计划中，"j" 被替换为 "char_length(h)"
      final String select = "select * from t1"; // 定义 SQL 语句，查询表 t1 的所有数据
      try (ResultSet r = s.executeQuery(select)) { // 执行查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getString(1), is("abc")); // 断言第 1 列（h）值为 "abc"
        assertThat(r.getString(2), is("de ")); // 断言第 2 列（i）值为 "de "（带空格）
        assertThat(r.getInt(3), is(3)); // 断言第 3 列（j）值为 3（char_length("abc") = 3）
        assertThat(r.getString(4), is("de")); // 断言第 4 列（k）值为 "de"（rtrim("de ") = "de"）
        assertThat(r.next(), is(false)); // 断言没有更多数据
      } // 自动关闭结果集

      final String plan = "" // 定义期望的执行计划
          + "EnumerableCalc(expr#0..1=[{inputs}], expr#2=[CHAR_LENGTH($t0)], " // 虚拟列 J 被替换为 CHAR_LENGTH(h)
          + "expr#3=[FLAG(TRAILING)], expr#4=[' '], " // TRIM 函数的参数
          + "expr#5=[TRIM($t3, $t4, $t1)], proj#0..2=[{exprs}], K=[$t5])\n" // 虚拟列 K 被替换为 TRIM(i)
          + "  EnumerableTableScan(table=[[T1]])\n"; // 表扫描
      try (ResultSet r = s.executeQuery("explain plan for " + select)) { // 执行 EXPLAIN PLAN 查询
        assertThat(r.next(), is(true)); // 断言结果集有数据
        assertThat(r.getString(1), isLinux(plan)); // 断言执行计划与期望一致
      } // 自动关闭结果集
    } // 自动关闭资源
  }

  /**
   * testDropWithFullyQualifiedNameWhenSchemaDoesntExist() 方法测试使用完全限定名称删除不存在的对象
   * 测试内容包括：
   * 1. 测试删除不存在的模式（schema）
   * 2. 测试删除不存在的表（table）
   * 3. 测试删除不存在的物化视图（materialized view）
   * 4. 测试删除不存在的视图（view）
   * 5. 测试删除不存在的类型（type）
   * 6. 测试删除不存在的函数（function）
   * 验证当模式不存在时，DROP 语句是否能正确报错
   * 验证 DROP IF EXISTS 语句是否能正确处理不存在的对象
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  @Test void testDropWithFullyQualifiedNameWhenSchemaDoesntExist() throws Exception { // 定义测试方法，测试删除不存在的对象
    try (Connection c = connect(); // 使用 try-with-resources 创建数据库连接
         Statement s = c.createStatement()) { // 创建语句对象
      checkDropWithFullyQualifiedNameWhenSchemaDoesntExist(s, "schema", "Schema"); // 检查删除模式
      checkDropWithFullyQualifiedNameWhenSchemaDoesntExist(s, "table", "Table"); // 检查删除表
      checkDropWithFullyQualifiedNameWhenSchemaDoesntExist(s, "materialized view", "Table"); // 检查删除物化视图
      checkDropWithFullyQualifiedNameWhenSchemaDoesntExist(s, "view", "View"); // 检查删除视图
      checkDropWithFullyQualifiedNameWhenSchemaDoesntExist(s, "type", "Type"); // 检查删除类型
      checkDropWithFullyQualifiedNameWhenSchemaDoesntExist(s, "function", "Function"); // 检查删除函数
    } // 自动关闭资源
  }

  /**
   * checkDropWithFullyQualifiedNameWhenSchemaDoesntExist() 辅助方法用于检查删除不存在的对象的行为
   * 该方法验证：
   * 1. 当使用完全限定名称（schema.object）删除不存在的对象时，是否抛出正确的异常
   * 2. 异常消息是否包含正确的对象类型
   * 3. DROP IF EXISTS 语句是否能正确处理不存在的对象（不抛出异常）
   * @param statement 语句对象，用于执行 SQL 语句
   * @param objectType 对象类型字符串（如 "schema", "table", "view" 等）
   * @param objectTypeInErrorMessage 异常消息中期望的对象类型字符串
   * @throws Exception 如果执行过程中出现错误则抛出异常
   */
  private void checkDropWithFullyQualifiedNameWhenSchemaDoesntExist( // 定义辅助方法，检查删除不存在的对象
      Statement statement, String objectType, String objectTypeInErrorMessage) throws Exception { // 参数：语句对象、对象类型、异常消息中的对象类型
    SQLException e = assertThrows(SQLException.class, () -> // 断言抛出 SQLException
        statement.execute("drop " + objectType + " s.o"), // 尝试删除不存在的对象 s.o
        "expected error because the object doesn't exist"); // 错误信息
    assertThat(e.getMessage(), containsString(objectTypeInErrorMessage + " 'O' not found")); // 断言异常消息包含指定文本

    statement.execute("drop " + objectType + " if exists s.o"); // 执行 DROP IF EXISTS，不抛出异常
  } // 方法结束
} // 类定义结束
