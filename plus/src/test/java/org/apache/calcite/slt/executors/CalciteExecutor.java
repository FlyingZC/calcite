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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.slt.executors; // 定义包名，位于Calcite项目的SQL Logic Test执行器包中

import org.apache.calcite.adapter.jdbc.JdbcSchema; // 导入Calcite的JDBC适配器Schema类，用于将外部JDBC数据源包装为Calcite Schema
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite JDBC连接类，提供Calcite特有的连接功能
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus类，Calcite中可扩展的Schema接口

import net.hydromatic.sqllogictest.OptionsParser; // 导入SQL逻辑测试的选项解析器，用于解析测试配置
import net.hydromatic.sqllogictest.SltSqlStatement; // 导入SQL逻辑测试的SQL语句类，表示一个待执行的SQL语句
import net.hydromatic.sqllogictest.executors.HsqldbExecutor; // 导入HSQLDB执行器，用于执行DDL和DML语句
import net.hydromatic.sqllogictest.executors.JdbcExecutor; // 导入JDBC执行器基类，提供基础的JDBC执行功能

import java.io.IOException; // 导入IO异常类，处理文件读写异常
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于建立数据库连接
import java.sql.SQLException; // 导入SQL异常类，处理数据库操作异常
import java.util.Set; // 导入Set集合接口，用于存储已知的bug列表
import javax.sql.DataSource; // 导入JDBC数据源接口，表示数据库连接池

/**
 * Executor for SQL logic tests using Calcite's JDBC adapter.
 */
// 这是一个使用Calcite JDBC适配器执行SQL逻辑测试的执行器类
// 它继承自JdbcExecutor，将Calcite作为查询引擎，而将HSQLDB作为数据存储
// 主要用于验证Calcite的SQL解析、优化和执行功能
public class CalciteExecutor extends JdbcExecutor { // CalciteExecutor类，继承JdbcExecutor基类，实现Calcite特有的SQL执行逻辑
  /**
   * This executor is used for executing the statements (CREATE TABLE,
   * CREATE VIEW, INSERT).  Queries are executed by Calcite.
   */
  // 这个成员变量是一个JdbcExecutor实例，专门用于执行DDL和DML语句
  // 包括CREATE TABLE（创建表）、CREATE VIEW（创建视图）、INSERT（插入数据）等语句
  // 而实际的SELECT查询语句则由Calcite引擎本身来执行和优化
  // 这样设计的好处是利用HSQLDB来存储和管理数据，而用Calcite来处理复杂的查询逻辑
  private final JdbcExecutor statementExecutor; // 声明一个final类型的语句执行器，用于管理数据定义和数据操作语句

  public static void register(OptionsParser parser) { // 注册方法，将CalciteExecutor注册到选项解析器中，使其可以通过"calcite"名称被调用
    parser.registerExecutor("calcite", () -> { // 使用lambda表达式注册名为"calcite"的执行器工厂
      OptionsParser.SuppliedOptions options = parser.getOptions(); // 从解析器获取配置选项，包含测试运行所需的各种参数
      HsqldbExecutor statementExecutor = new HsqldbExecutor(options); // 创建HSQLDB执行器实例，用于执行DDL和DML语句
      try { // 开始try-catch块，捕获可能的IO和SQL异常
        CalciteExecutor result = new CalciteExecutor(options, statementExecutor); // 创建CalciteExecutor实例，传入配置和HSQLDB执行器
        Set<String> bugs = options.readBugsFile(); // 从配置中读取已知的bug列表，这些bug在测试中需要避免
        result.avoid(bugs); // 调用avoid方法，将已知的bug列表设置到执行器中，避免触发这些已知问题
        return result; // 返回创建好的CalciteExecutor实例
      } catch (IOException | SQLException e) { // 捕获IO异常或SQL异常
        throw new RuntimeException(e); // 将捕获的异常包装为运行时异常抛出
      }
    });
  }

  @Override public void establishConnection() throws SQLException { // 重写establishConnection方法，用于建立数据库连接
    this.statementExecutor.establishConnection(); // 委托给内部的statementExecutor建立连接，即建立HSQLDB连接
  }

  @Override public void dropAllTables() throws SQLException { // 重写dropAllTables方法，用于删除所有表
    this.statementExecutor.dropAllTables(); // 委托给内部的statementExecutor删除所有表，即删除HSQLDB中的所有表
  }

  @Override public void dropAllViews() throws SQLException { // 重写dropAllViews方法，用于删除所有视图
    this.statementExecutor.dropAllViews(); // 委托给内部的statementExecutor删除所有视图，即删除HSQLDB中的所有视图
  }

  @Override public void statement(SltSqlStatement statement) throws SQLException { // 重写statement方法，用于执行SQL语句
    this.statementExecutor.statement(statement); // 委托给内部的statementExecutor执行SQL语句，包括DDL和DML语句
  }

  public CalciteExecutor(OptionsParser.SuppliedOptions options, JdbcExecutor statementExecutor)
      throws SQLException { // 构造方法，接收配置选项和语句执行器作为参数
    super(options, "jdbc:calcite:lex=ORACLE", "", ""); // 调用父类JdbcExecutor的构造方法，传入Calcite JDBC URL，使用ORACLE词法规则
    this.statementExecutor = statementExecutor; // 将传入的语句执行器保存到成员变量中
    // Build our connection
    // 建立Calcite连接，使用ORACLE词法规则（lex=ORACLE表示使用Oracle风格的标识符引用和大小写处理）
    this.connection = // 将创建的连接赋值给父类的connection成员变量
        DriverManager.getConnection("jdbc:calcite:lex=ORACLE"); // 通过DriverManager获取Calcite JDBC连接
    CalciteConnection calciteConnection = this.connection.unwrap(CalciteConnection.class); // 将普通JDBC连接解包为CalciteConnection，以便访问Calcite特有功能
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取Calcite连接的根Schema，这是所有Schema的容器
    DataSource hsqldb = // 创建HSQLDB数据源，用于连接到HSQLDB数据库
        JdbcSchema.dataSource(statementExecutor.dbUrl, // 使用statementExecutor的数据库URL创建数据源
        "org.hsqldb.jdbcDriver", // 指定HSQLDB的JDBC驱动类名
        "", // 用户名为空字符串
        ""); // 密码为空字符串
    final String schemaName = "SLT"; // 定义Schema名称为"SLT"（SQL Logic Test的缩写）
    JdbcSchema jdbcSchema = JdbcSchema.create(rootSchema, schemaName, hsqldb, null, null); // 创建JdbcSchema实例，将HSQLDB数据源包装为Calcite Schema
    rootSchema.add(schemaName, jdbcSchema); // 将创建的JdbcSchema添加到根Schema中，使其在Calcite中可用
    calciteConnection.setSchema(schemaName); // 设置当前连接的默认Schema为"SLT"，这样后续查询会默认在这个Schema中执行
  }
}
