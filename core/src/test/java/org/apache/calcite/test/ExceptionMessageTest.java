/* // Apache许可证头声明
 * Licensed to the Apache Software Foundation (ASF) under one or more // 授权给Apache软件基金会使用
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看NOTICE文件获取更多信息
 * this work for additional information regarding copyright ownership. // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权给你
 * (the "License"); you may not use this file except in compliance with // 你只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 你可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0 // Apache许可证2.0的URL地址
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意
 * distributed under the License is distributed on an "AS IS" BASIS, // 否则按"原样"基础分发软件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and // 查看许可证了解具体的权限和
 * limitations under the License. // 限制条件
 */
package org.apache.calcite.test; // 声明包名，属于Apache Calcite测试包

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入反射式Schema适配器，用于将Java类映射为数据库表
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite JDBC连接类，提供Calcite特有的数据库连接功能
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口，表示关系代数树中的一个节点
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，提供可扩展的Schema功能
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表，包含所有标准SQL函数和操作符
import org.apache.calcite.tools.FrameworkConfig; // 导入框架配置类，用于配置Calcite查询框架
import org.apache.calcite.tools.Frameworks; // 导入框架工具类，用于创建框架配置和构建器
import org.apache.calcite.tools.RelBuilder; // 导入关系表达式构建器，用于构建关系代数树
import org.apache.calcite.tools.RelRunner; // 导入关系表达式执行器，用于执行RelNode

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于获取数据库连接
import java.sql.PreparedStatement; // 导入预编译语句接口，用于执行参数化SQL查询
import java.sql.SQLException; // 导入SQL异常类，表示数据库操作中的错误
import java.sql.Statement; // 导入语句接口，用于执行静态SQL语句
import java.util.function.Function; // 导入函数式接口，用于表示一个接受参数并返回结果的函数

import static org.hamcrest.CoreMatchers.containsString; // 导入Hamcrest断言工具，检查字符串是否包含子串
import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest断言工具，检查两个值是否相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言方法，用于执行断言检查
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit 5的fail方法，用于标记测试失败

/** // Javadoc注释开始
 * Test cases to check that necessary information from underlying exceptions // 测试用例，检查来自底层异常的必要信息
 * is correctly propagated via {@link SQLException}s. // 是否通过SQLException正确传播
 */ // Javadoc注释结束
public class ExceptionMessageTest { // 定义ExceptionMessageTest测试类，用于测试异常消息传播机制
  /** // Javadoc注释开始
   * Simple reflective schema that provides valid and invalid entries. // 简单的反射式Schema，提供有效和无效的条目
   */ // Javadoc注释结束
  @SuppressWarnings("UnusedDeclaration") // 抑制未使用声明警告，因为TestSchema通过反射被使用
  public static class TestSchema { // 定义TestSchema静态内部类，作为测试用的数据Schema
    public Entry[] entries = { // 定义entries数组字段，包含可用的Entry对象，将被映射为数据库表
        new Entry(1, "name1"), // 创建第一个Entry对象，id为1，name为"name1"
        new Entry(2, "name2") // 创建第二个Entry对象，id为2，name为"name2"
    }; // entries数组初始化结束

    public Iterable<Entry> badEntries = () -> { // 定义badEntries字段，类型为Entry的可迭代对象，使用lambda表达式初始化
      throw new IllegalStateException("Can't iterate over badEntries"); // 抛出非法状态异常，模拟无法迭代的错误情况
    }; // badEntries的lambda表达式结束，用于测试异常传播
  } // TestSchema类定义结束

  /** // Javadoc注释开始
   * Entries made available in the reflective TestSchema. // 在反射式TestSchema中可用的条目
   */ // Javadoc注释结束
  public static class Entry { // 定义Entry静态内部类，表示数据表中的一行记录
    public final int id; // 定义id字段，类型为int，使用final修饰表示不可变
    public final String name; // 定义name字段，类型为String，使用final修饰表示不可变

    public Entry(int id, String name) { // Entry类的构造方法，接收id和name两个参数
      this.id = id; // 将参数id赋值给实例变量id
      this.name = name; // 将参数name赋值给实例变量name
    } // 构造方法结束
  } // Entry类定义结束

  /** Fixture. */ // Javadoc注释，表示这是一个测试夹具类
  private static class Fixture implements AutoCloseable { // 定义Fixture静态内部类，实现AutoCloseable接口用于自动资源管理
    private final CalciteConnection conn; // 定义conn字段，类型为CalciteConnection，使用final修饰表示不可变，用于存储数据库连接

    Fixture() throws SQLException { // Fixture类的构造方法，声明可能抛出SQLException异常
      Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 通过DriverManager获取Calcite JDBC连接，使用内存数据库
      this.conn = connection.unwrap(CalciteConnection.class); // 将普通Connection解包为CalciteConnection，获取Calcite特有功能
      SchemaPlus rootSchema = conn.getRootSchema(); // 获取根Schema，用于添加自定义Schema
      rootSchema.add("test", new ReflectiveSchema(new TestSchema())); // 向根Schema添加名为"test"的Schema，使用反射方式包装TestSchema
      conn.setSchema("test"); // 设置当前连接使用的Schema为"test"
    } // 构造方法结束

    @Override public void close() throws SQLException { // 重写AutoCloseable接口的close方法，用于关闭资源
      conn.close(); // 关闭CalciteConnection连接，释放数据库资源
    } // close方法结束

    private void runQuery(String sql) throws SQLException { // 定义runQuery私有方法，接收SQL字符串参数，执行SQL查询，可能抛出SQLException
      Statement stmt = conn.createStatement(); // 创建Statement对象，用于执行静态SQL语句
      try { // 开始try块，用于捕获查询执行中的异常
        stmt.executeQuery(sql); // 执行SQL查询，返回ResultSet结果集
      } finally { // finally块确保无论是否发生异常都会执行
        try { // 内层try块，用于捕获关闭Statement时的异常
          stmt.close(); // 关闭Statement对象，释放数据库资源
        } catch (Exception e) { // 捕获关闭Statement时可能发生的异常
          // We catch a possible exception on close so that we know we're not // 捕获关闭时可能发生的异常，以确保我们不会
          // masking the query exception with the close exception // 用关闭异常掩盖查询异常
          fail("Error on close"); // 如果关闭时发生异常，标记测试失败
        } // 内层try-catch块结束
      } // finally块结束
    } // runQuery方法结束

    /** Performs an action that requires a {@link RelBuilder}, and returns the // 执行需要RelBuilder的操作，并返回结果
     * result. */ // Javadoc注释结束
    private <T> T withRelBuilder(Function<RelBuilder, T> fn) // 定义withRelBuilder泛型私有方法，接收一个RelBuilder到T的函数
        throws SQLException { // 声明可能抛出SQLException异常
      final SchemaPlus rootSchema = // 获取根Schema，用于构建RelBuilder
          conn.unwrap(CalciteConnection.class).getRootSchema(); // 从CalciteConnection解包并获取根Schema
      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
          .defaultSchema(rootSchema) // 设置默认Schema为rootSchema
          .build(); // 构建FrameworkConfig配置对象
      final RelBuilder relBuilder = RelBuilder.create(config); // 使用配置创建RelBuilder对象，用于构建关系代数树
      return fn.apply(relBuilder); // 应用传入的函数到relBuilder，返回结果T
    } // withRelBuilder方法结束

    private void runQuery(Function<RelBuilder, RelNode> relFn) // 定义runQuery重载私有方法，接收一个RelBuilder到RelNode的函数
        throws SQLException { // 声明可能抛出SQLException异常
      final RelRunner relRunner = conn.unwrap(RelRunner.class); // 从连接中解包获取RelRunner，用于执行RelNode
      final RelNode relNode = withRelBuilder(relFn); // 使用withRelBuilder方法构建RelNode关系表达式树
      final PreparedStatement preparedStatement = // 使用RelRunner将RelNode转换为PreparedStatement
          relRunner.prepareStatement(relNode); // 准备执行RelNode关系表达式
      try { // 开始try块，用于捕获查询执行中的异常
        preparedStatement.executeQuery(); // 执行预编译语句，返回ResultSet结果集
      } finally { // finally块确保无论是否发生异常都会执行
        try { // 内层try块，用于捕获关闭PreparedStatement时的异常
          preparedStatement.close(); // 关闭PreparedStatement对象，释放数据库资源
        } catch (Exception e) { // 捕获关闭PreparedStatement时可能发生的异常
          fail("Error on close"); // 如果关闭时发生异常，标记测试失败
        } // 内层try-catch块结束
      } // finally块结束
    } // runQuery重载方法结束
  } // Fixture类定义结束

  @Test void testValidQuery() throws SQLException { // 定义测试方法testValidQuery，测试有效查询，可能抛出SQLException
    try (Fixture f = new Fixture()) { // 使用try-with-resources创建Fixture对象，自动管理资源
      // Just ensure that we're actually dealing with a valid connection // 确保我们实际使用的是有效连接
      // to be sure that the results of the other tests can be trusted // 以确保其他测试结果可信
      f.runQuery("select * from \"entries\""); // 执行查询，从entries表中选择所有数据
    } // try-with-resources自动关闭Fixture
  } // testValidQuery方法结束

  @Test void testNonSqlException() { // 定义测试方法testNonSqlException，测试非SQL异常的传播
    try (Fixture f = new Fixture()) { // 使用try-with-resources创建Fixture对象，自动管理资源
      f.runQuery("select * from \"badEntries\""); // 执行查询，从badEntries表中选择所有数据，该表会抛出异常
      fail("Query badEntries should result in an exception"); // 如果没有抛出异常，标记测试失败
    } catch (SQLException e) { // 捕获SQLException异常
      assertThat(e.getMessage(), // 断言异常消息
          equalTo("Error while executing SQL \"select * from \"badEntries\"\": " // 期望异常消息等于指定字符串
              + "Can't iterate over badEntries")); // 包含原始异常信息
    } // catch块结束
  } // testNonSqlException方法结束

  @Test void testSyntaxError() { // 定义测试方法testSyntaxError，测试SQL语法错误的异常消息
    try (Fixture f = new Fixture()) { // 使用try-with-resources创建Fixture对象，自动管理资源
      f.runQuery("invalid sql"); // 执行无效的SQL语句，应该导致语法错误
      fail("Query should fail"); // 如果没有抛出异常，标记测试失败
    } catch (SQLException e) { // 捕获SQLException异常
      assertThat(e.getMessage(), // 断言异常消息
          equalTo("Error while executing SQL \"invalid sql\": parse failed: " // 期望异常消息等于指定字符串
              + "Non-query expression encountered in illegal context")); // 包含解析失败的详细信息
    } // catch块结束
  } // testSyntaxError方法结束

  @Test void testSemanticError() { // 定义测试方法testSemanticError，测试SQL语义错误的异常消息
    try (Fixture f = new Fixture()) { // 使用try-with-resources创建Fixture对象，自动管理资源
      // implicit type coercion. // 隐式类型转换
      f.runQuery("select \"name\" - \"id\" from \"entries\""); // 执行查询，尝试对字符串和整数进行减法运算，应该导致语义错误
    } catch (SQLException e) { // 捕获SQLException异常
      assertThat(e.getMessage(), // 断言异常消息
          containsString("Cannot apply '-' to arguments")); // 期望异常消息包含指定字符串，说明无法应用减法操作符
    } // catch块结束
  } // testSemanticError方法结束

  @Test void testNonexistentTable() { // 定义测试方法testNonexistentTable，测试查询不存在的表时的异常消息
    try (Fixture f = new Fixture()) { // 使用try-with-resources创建Fixture对象，自动管理资源
      f.runQuery("select name from \"nonexistentTable\""); // 执行查询，从不存在的表中选择数据，应该导致错误
      fail("Query should fail"); // 如果没有抛出异常，标记测试失败
    } catch (SQLException e) { // 捕获SQLException异常
      assertThat(e.getMessage(), // 断言异常消息
          containsString("Object 'nonexistentTable' not found")); // 期望异常消息包含指定字符串，说明对象未找到
    } // catch块结束
  } // testNonexistentTable方法结束

  /** Runs a query via {@link RelRunner}. */ // Javadoc注释，说明通过RelRunner执行查询
  @Test void testValidRelNodeQuery() throws SQLException { // 定义测试方法testValidRelNodeQuery，测试通过RelRunner执行有效的RelNode查询
    try (Fixture f = new Fixture()) { // 使用try-with-resources创建Fixture对象，自动管理资源
      final Function<RelBuilder, RelNode> relFn = b -> // 定义从RelBuilder构建RelNode的函数，使用lambda表达式
          b.scan("test", "entries") // 扫描test.entries表，生成LogicalTableScan节点
              .project(b.field("name")) // 投影name字段，生成LogicalProject节点
              .build(); // 构建完整的RelNode关系表达式树
      f.runQuery(relFn); // 使用Fixture的runQuery方法执行RelNode查询
    } // try-with-resources自动关闭Fixture
  } // testValidRelNodeQuery方法结束

  /** Runs a query via {@link RelRunner} that is expected to fail, // 通过RelRunner执行预期会失败的查询
   * and checks that the exception correctly describes the RelNode tree. // 并检查异常是否正确描述了RelNode树
   *
   * <p>Test case for // 测试用例针对
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4585">[CALCITE-4585] // JIRA问题CALCITE-4585
   * If a query is executed via RelRunner.prepare(RelNode) and fails, the // 如果通过RelRunner.prepare(RelNode)执行查询失败
   * exception should report the RelNode plan, not the SQL</a>. */ // 异常应该报告RelNode计划，而不是SQL
  @Test void testRelNodeQueryException() { // 定义测试方法testRelNodeQueryException，测试RelNode查询异常的传播
    try (Fixture f = new Fixture()) { // 使用try-with-resources创建Fixture对象，自动管理资源
      final Function<RelBuilder, RelNode> relFn = b -> // 定义从RelBuilder构建RelNode的函数，使用lambda表达式
          b.scan("test", "entries") // 扫描test.entries表，生成LogicalTableScan节点
              .project(b.call(SqlStdOperatorTable.ABS, b.field("name"))) // 投影ABS(name)表达式，对字符串调用ABS函数应该失败
              .build(); // 构建完整的RelNode关系表达式树
      f.runQuery(relFn); // 使用Fixture的runQuery方法执行RelNode查询
      fail("RelNode query about entries should result in an exception"); // 如果没有抛出异常，标记测试失败
    } catch (SQLException e) { // 捕获SQLException异常
      String message = "Error while preparing plan [" // 定义期望的异常消息字符串，包含RelNode计划
          + "LogicalProject($f0=[ABS($1)])\n" // LogicalProject节点，对第1列应用ABS函数
          + "  LogicalTableScan(table=[[test, entries]])\n" // LogicalTableScan节点，扫描test.entries表
          + "]"; // 消息字符串结束
      assertThat(e.getMessage(), Matchers.isLinux(message)); // 断言异常消息等于期望的RelNode计划字符串
    } // catch块结束
  } // testRelNodeQueryException方法结束
} // ExceptionMessageTest类定义结束
