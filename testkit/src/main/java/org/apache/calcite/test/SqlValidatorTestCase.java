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
package org.apache.calcite.test; // 包声明：org.apache.calcite.test 包，包含 Calcite 测试相关类

import org.apache.calcite.sql.parser.StringAndPos; // 导入 StringAndPos 类：表示字符串及其位置信息，用于错误定位
import org.apache.calcite.sql.test.SqlTestFactory; // 导入 SqlTestFactory 类：SQL 测试工厂，用于创建测试所需的组件
import org.apache.calcite.sql.test.SqlValidatorTester; // 导入 SqlValidatorTester 类：SQL 验证器测试器，提供测试验证器的功能
import org.apache.calcite.sql.validate.SqlValidator; // 导入 SqlValidator 接口：SQL 验证器接口，用于验证 SQL 语句的语义正确性

/**
 * An abstract base class for implementing tests against {@link SqlValidator}. // 抽象基类：用于实现针对 SqlValidator 的测试
 *
 * <p>A derived class can refine this test in two ways. First, it can add // 派生类可以通过两种方式完善测试：第一，添加
 * {@code testXxx()} methods, to test more functionality. // testXxx() 方法来测试更多功能
 *
 * <p>Second, it can override the {@link #fixture()} method to return a // 第二，可以重写 fixture() 方法返回
 * different implementation of the {@link SqlValidatorFixture} object. This // SqlValidatorFixture 对象的不同实现。这
 * encapsulates the differences between test environments, for example, which // 封装了测试环境的差异，例如使用哪个
 * SQL parser or validator to use. // SQL 解析器或验证器
 */
public class SqlValidatorTestCase { // 类定义：SqlValidatorTestCase，SQL 验证器测试用例的抽象基类
  public static final SqlValidatorFixture FIXTURE = // 静态常量：默认的测试夹具（fixture），包含测试所需的所有组件
      new SqlValidatorFixture(SqlValidatorTester.DEFAULT, // 创建 SqlValidatorFixture 对象：使用默认的验证器测试器
          SqlTestFactory.INSTANCE, StringAndPos.of("?"), false, false); // 参数：测试工厂实例、字符串位置对象、两个布尔标志

  /** Creates a test case. */ // 构造方法注释：创建一个测试用例实例
  public SqlValidatorTestCase() { // 默认构造方法：创建 SqlValidatorTestCase 实例
  } // 构造方法结束

  //~ Methods ---------------------------------------------------------------- // 方法分隔符注释

  /** Creates a test fixture. Derived classes can override this method to // 创建测试夹具。派生类可以重写此方法
   * run the same set of tests in a different testing environment. */ // 以在不同的测试环境中运行相同的测试集
  public SqlValidatorFixture fixture() { // 方法：获取测试夹具，返回当前测试环境的 SqlValidatorFixture 对象
    return FIXTURE; // 返回默认的静态 FIXTURE 对象
  } // 方法结束

  /**
   * Creates a test context with a SQL query. // 创建带有 SQL 查询的测试上下文
   * Default catalog: {@link org.apache.calcite.test.catalog.MockCatalogReaderSimple#init()}. // 默认目录：MockCatalogReaderSimple 初始化的目录
   */
  public final SqlValidatorFixture sql(String sql) { // 方法：创建 SQL 查询测试上下文，参数 sql 是要测试的 SQL 语句
    return fixture().withSql(sql); // 返回：调用 fixture() 获取夹具，然后设置 SQL 语句
  } // 方法结束

  /** Creates a test context with a SQL expression. */ // 创建带有 SQL 表达式的测试上下文
  public final SqlValidatorFixture expr(String sql) { // 方法：创建 SQL 表达式测试上下文，参数 sql 是要测试的 SQL 表达式
    return fixture().withExpr(sql); // 返回：调用 fixture() 获取夹具，然后设置为表达式模式
  } // 方法结束

  /** Creates a test context with a SQL expression. // 创建带有 SQL 表达式的测试上下文
   * If an error occurs, the error is expected to span the entire expression. */ // 如果发生错误，错误预期覆盖整个表达式
  public final SqlValidatorFixture wholeExpr(String sql) { // 方法：创建整个表达式的测试上下文，参数 sql 是要测试的表达式
    return expr(sql).withWhole(true); // 返回：调用 expr() 创建表达式上下文，然后设置 withWhole(true) 标志
  } // 方法结束

  public final SqlValidatorFixture winSql(String sql) { // 方法：创建窗口函数相关的 SQL 测试上下文，参数 sql 是 SQL 语句
    return sql(sql); // 返回：直接调用 sql() 方法，实际上与普通 SQL 测试相同
  } // 方法结束

  public final SqlValidatorFixture win(String sql) { // 方法：创建窗口函数测试上下文，在 emp 表上添加窗口子句
    return sql("select * from emp " + sql); // 返回：构建 "select * from emp " + sql 的 SQL 语句进行测试
  } // 方法结束

  public SqlValidatorFixture winExp(String sql) { // 方法：创建窗口表达式测试上下文，参数 sql 是窗口中的表达式
    return winSql("select " + sql + " from emp window w as (order by deptno)"); // 返回：构建带窗口定义的 SQL 语句进行测试
  } // 方法结束

  public SqlValidatorFixture winExp2(String sql) { // 方法：创建窗口表达式测试上下文的另一种形式，参数 sql 是表达式
    return winSql("select " + sql + " from emp"); // 返回：构建不带窗口定义的 SQL 语句进行测试
  } // 方法结束

} // 类结束
