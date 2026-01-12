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
package org.apache.calcite.test; // 声明包名，该类属于org.apache.calcite.test包

import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性配置类
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接接口
import org.apache.calcite.linq4j.tree.Primitive; // 导入LINQ4J的原始类型工具类
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，用于管理schema
import org.apache.calcite.schema.Table; // 导入表接口
import org.apache.calcite.schema.TableFunction; // 导入表函数接口
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象Schema实现类
import org.apache.calcite.schema.impl.TableFunctionImpl; // 导入表函数实现类
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SQL兼容性枚举
import org.apache.calcite.util.Smalls; // 导入Smalls工具类，包含测试用的表函数方法
import org.apache.calcite.util.TestUtil; // 导入测试工具类

import org.junit.jupiter.api.Disabled; // 导入Junit5的Disabled注解，用于禁用测试
import org.junit.jupiter.api.Test; // 导入Junit5的Test注解，标识测试方法

import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器
import java.sql.PreparedStatement; // 导入预编译语句接口
import java.sql.ResultSet; // 导入结果集接口
import java.sql.SQLException; // 导入SQL异常类
import java.sql.Statement; // 导入语句接口
import java.util.ArrayList; // 导入ArrayList集合类
import java.util.List; // 导入List接口

import static org.hamcrest.CoreMatchers.containsString; // 导入Hamcrest断言工具：包含字符串匹配
import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest断言工具：相等匹配
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言工具：is匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具
import static org.junit.jupiter.api.Assertions.fail; // 导入Junit5的fail方法

/**
 * 用户自定义表函数的测试类
 * 该类用于测试Calcite中用户自定义表函数的各种功能，包括：
 * 1. 表函数的基本调用
 * 2. 表函数与字面量参数的交互
 * 3. 表函数与数组、Map参数的交互
 * 4. 表函数的动态结构（根据参数返回不同行类型）
 * 5. 表函数的命名参数支持
 * 6. 表函数的游标输入支持
 * 7. 表函数的CROSS APPLY和LEFT OUTER APPLY操作
 * 8. 表函数的分组聚合操作
 *
 * @see UdfTest // 参见用户定义函数测试类
 * @see Smalls // 参见Smalls工具类，包含测试用的表函数实现
 */
class TableFunctionTest { // 定义TableFunctionTest测试类
  private CalciteAssert.AssertThat with() { // 私有辅助方法：创建并返回一个配置好的CalciteAssert断言对象，用于测试表函数
    final String c = Smalls.class.getName(); // 获取Smalls类的全限定名，用于在模型配置中引用
    final String m = Smalls.MULTIPLICATION_TABLE_METHOD.getName(); // 获取乘法表函数的方法名
    final String m2 = Smalls.FIBONACCI_TABLE_METHOD.getName(); // 获取斐波那契表函数的方法名
    final String m3 = Smalls.FIBONACCI_LIMIT_TABLE_METHOD.getName(); // 获取带限制的斐波那契表函数的方法名
    return CalciteAssert.model("{\n" // 创建CalciteAssert对象，使用JSON模型配置schema和函数
        + "  version: '1.0',\n" // 模型版本号
        + "   schemas: [\n" // 定义schema列表
        + "     {\n" // 开始定义第一个schema
        + "       name: 's',\n" // schema名称为's'
        + "       functions: [\n" // 定义该schema下的函数列表
        + "         {\n" // 开始定义第一个函数
        + "           name: 'multiplication',\n" // 函数名为'multiplication'
        + "           className: '" + c + "',\n" // 函数所在类名为Smalls类
        + "           methodName: '" + m + "'\n" // 调用MULTIPLICATION_TABLE_METHOD方法
        + "         }, {\n" // 开始定义第二个函数
        + "           name: 'fibonacci',\n" // 函数名为'fibonacci'
        + "           className: '" + c + "',\n" // 函数所在类名为Smalls类
        + "           methodName: '" + m2 + "'\n" // 调用FIBONACCI_TABLE_METHOD方法
        + "         }, {\n" // 开始定义第三个函数
        + "           name: 'fibonacci2',\n" // 函数名为'fibonacci2'
        + "           className: '" + c + "',\n" // 函数所在类名为Smalls类
        + "           methodName: '" + m3 + "'\n" // 调用FIBONACCI_LIMIT_TABLE_METHOD方法
        + "         }\n" // 结束第三个函数定义
        + "       ]\n" // 结束函数列表
        + "     }\n" // 结束schema定义
        + "   ]\n" // 结束schema列表
        + "}") // 结束JSON模型配置
        .withDefaultSchema("s"); // 设置默认schema为's'
  } // 方法结束

  /**
   * 测试带有字面量参数的表函数
   * 该测试验证了如何：
   * 1. 创建Calcite连接
   * 2. 注册用户自定义表函数到schema中
   * 3. 在SQL查询中调用表函数
   * 4. 对表函数返回的结果进行过滤
   */
  @Test void testTableFunction() throws SQLException { // 测试方法：测试表函数的基本功能，可能会抛出SQL异常
      try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) { // 使用try-with-resources创建Calcite JDBC连接，连接字符串为"jdbc:calcite:"
        CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
            connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口，用于访问schema等高级功能
        SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema，这是schema层次结构的顶层
        SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema，使用AbstractSchema作为实现
        final TableFunction table = // 创建表函数对象
            TableFunctionImpl.create(Smalls.GENERATE_STRINGS_METHOD); // 使用TableFunctionImpl工厂方法创建表函数，传入GENERATE_STRINGS_METHOD方法
        schema.add("GenerateStrings", table); // 将表函数注册到schema中，名称为'GenerateStrings'
        final String sql = "select *\n" // 构建SQL查询字符串
            + "from table(\"s\".\"GenerateStrings\"(5)) as t(n, c)\n" // 调用表函数GenerateStrings，传入参数5，返回别名为t的表，列名为n和c
            + "where char_length(c) > 3"; // 添加过滤条件：只选择长度大于3的字符串
        ResultSet resultSet = connection.createStatement().executeQuery(sql); // 创建语句对象并执行查询，获得结果集
        assertThat(CalciteAssert.toString(resultSet), // 使用CalciteAssert工具将结果集转换为字符串
            equalTo("N=4; C=abcd\n")); // 断言结果等于预期值，表示只有长度为4的字符串'abcd'满足条件
      } // try-with-resources自动关闭连接
    } // 方法结束
  /**
   * 测试带有两个相同参数的相关子查询是否被正确处理
   * 该测试验证了：
   * 1. 表函数在相关子查询中的使用
   * 2. 表函数接收来自外层查询的参数
   * 3. 表函数返回null值时的处理
   */
  @Test void testInterpretFunctionWithInitializer() throws SQLException { // 测试方法：测试表函数在相关子查询中的行为，可能会抛出SQL异常
      try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) { // 使用try-with-resources创建Calcite JDBC连接
        CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
            connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
        SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
        SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
        final TableFunction table = // 创建表函数对象
            TableFunctionImpl.create(Smalls.DUMMY_TABLE_METHOD_WITH_TWO_PARAMS); // 使用有两个参数的虚拟表方法创建表函数
        final String callMethodName = Smalls.DUMMY_TABLE_METHOD_WITH_TWO_PARAMS.getName(); // 获取方法名称，用于动态构建SQL
        schema.add(callMethodName, table); // 将表函数注册到schema中，使用方法名作为函数名
        final String sql = "select x, (select * from table (\"s\".\"" + callMethodName + "\"(x, x))) " // 构建SQL查询，包含相关子查询
            + "from (values (2), (4)) as t (x)"; // 从值表(2)和(4)中选择x，并在子查询中调用表函数，传入两个相同的x参数
        ResultSet resultSet = connection.createStatement().executeQuery(sql); // 执行查询获得结果集
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            equalTo("X=2; EXPR$1=null\nX=4; EXPR$1=null\n")); // 断言结果等于预期值，表示表函数返回null
      } // try-with-resources自动关闭连接
    } // 方法结束
  @Test void testTableFunctionWithArrayParameter() throws SQLException { // 测试方法：测试表函数接收数组参数的功能，可能会抛出SQL异常
    try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) { // 使用try-with-resources创建Calcite JDBC连接
      CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
          connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
      SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
      SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
      final TableFunction table = // 创建表函数对象
          TableFunctionImpl.create(Smalls.GENERATE_STRINGS_OF_INPUT_SIZE_METHOD); // 使用生成指定长度字符串的方法创建表函数
      schema.add("GenerateStringsOfInputSize", table); // 将表函数注册到schema中，名称为'GenerateStringsOfInputSize'
      final String sql = "select *\n" // 构建SQL查询字符串
          + "from table(\"s\".\"GenerateStringsOfInputSize\"(ARRAY[5,4,3,1,2])) as t(n, c)\n" // 调用表函数，传入数组参数[5,4,3,1,2]
          + "where char_length(c) > 3"; // 添加过滤条件：只选择长度大于3的字符串
      ResultSet resultSet = connection.createStatement().executeQuery(sql); // 执行查询获得结果集
      assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
          equalTo("N=4; C=abcd\n")); // 断言结果等于预期值，表示只有长度为4的字符串'abcd'满足条件
    } // try-with-resources自动关闭连接
  } // 方法结束

  @Test void testTableFunctionWithMapParameter() throws SQLException { // 测试方法：测试表函数接收Map参数的功能，可能会抛出SQL异常
    try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) { // 使用try-with-resources创建Calcite JDBC连接
      CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
          connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
      SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
      SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
      final TableFunction table = // 创建表函数对象
          TableFunctionImpl.create(Smalls.GENERATE_STRINGS_OF_INPUT_MAP_SIZE_METHOD); // 使用生成Map指定长度字符串的方法创建表函数
      schema.add("GenerateStringsOfInputMapSize", table); // 将表函数注册到schema中，名称为'GenerateStringsOfInputMapSize'
      final String sql = "select *\n" // 构建SQL查询字符串
          + "from table(\"s\".\"GenerateStringsOfInputMapSize\"(Map[5,4,3,1])) as t(n, c)\n" // 调用表函数，传入Map参数[5,4,3,1]
          + "where char_length(c) > 0"; // 添加过滤条件：只选择长度大于0的字符串
      ResultSet resultSet = connection.createStatement().executeQuery(sql); // 执行查询获得结果集
      assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
          equalTo("N=1; C=a\n")); // 断言结果等于预期值，表示只有长度为1的字符串'a'满足条件
    } // try-with-resources自动关闭连接
  } // 方法结束

  /**
   * 测试实现了ScannableTable接口并返回单列的表函数
   * 该测试验证了：
   * 1. 表函数实现ScannableTable接口
   * 2. 表函数返回单列数据
   * 3. 表函数接收多个参数
   */
  @Test void testScannableTableFunction() throws SQLException { // 测试方法：测试可扫描表函数的功能，可能会抛出SQL异常
      Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建Calcite JDBC连接
      CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
          connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
      SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
      SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
      final TableFunction table = TableFunctionImpl.create(Smalls.MAZE_METHOD); // 使用MAZE_METHOD方法创建表函数
      schema.add("Maze", table); // 将表函数注册到schema中，名称为'Maze'
      final String sql = "select *\n" // 构建SQL查询字符串
          + "from table(\"s\".\"Maze\"(5, 3, 1))"; // 调用Maze表函数，传入参数5, 3, 1
      ResultSet resultSet = connection.createStatement().executeQuery(sql); // 执行查询获得结果集
      final String result = "S=abcde\n" // 预期结果的第一行
          + "S=xyz\n" // 预期结果的第二行
          + "S=generate(w=5, h=3, s=1)\n"; // 预期结果的第三行，显示生成参数
      assertThat(CalciteAssert.toString(resultSet), is(result)); // 断言结果等于预期值
    } // 方法结束
  /** 与 {@link #testScannableTableFunction()} 类似，但使用命名参数 */
  @Test void testScannableTableFunctionWithNamedParameters() // 测试方法：测试使用命名参数的可扫描表函数，可能会抛出SQL异常
      throws SQLException { // 方法声明
      Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建Calcite JDBC连接
      CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
          connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
      SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
      SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
      final TableFunction table = TableFunctionImpl.create(Smalls.MAZE2_METHOD); // 使用MAZE2_METHOD方法创建表函数（支持命名参数）
      schema.add("Maze", table); // 将表函数注册到schema中，名称为'Maze'
      final String sql = "select *\n" // 构建第一个SQL查询字符串
          + "from table(\"s\".\"Maze\"(5, 3, 1))"; // 使用位置参数调用Maze表函数
      final Statement statement = connection.createStatement(); // 创建语句对象
      ResultSet resultSet = statement.executeQuery(sql); // 执行第一个查询获得结果集
      final String result = "S=abcde\n" // 预期结果的前两行
          + "S=xyz\n"; // 预期结果的前两行
      assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
          is(result + "S=generate2(w=5, h=3, s=1)\n")); // 断言结果等于预期值，包含生成参数

      final String sql2 = "select *\n" // 构建第二个SQL查询字符串
          + "from table(\"s\".\"Maze\"(WIDTH -> 5, HEIGHT -> 3, SEED -> 1))"; // 使用命名参数调用Maze表函数
      resultSet = statement.executeQuery(sql2); // 执行第二个查询获得结果集
      assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
          is(result + "S=generate2(w=5, h=3, s=1)\n")); // 断言结果等于预期值，与第一个查询结果相同

      final String sql3 = "select *\n" // 构建第三个SQL查询字符串
          + "from table(\"s\".\"Maze\"(HEIGHT -> 3, WIDTH -> 5))"; // 使用命名参数调用Maze表函数，省略SEED参数
      resultSet = statement.executeQuery(sql3); // 执行第三个查询获得结果集
      assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
          is(result + "S=generate2(w=5, h=3, s=null)\n")); // 断言结果等于预期值，SEED参数为null
      connection.close(); // 关闭连接
    } // 方法结束
  /** 与 {@link #testScannableTableFunction()} 类似，但使用命名参数 */
  @Test void testMultipleScannableTableFunctionWithNamedParameters() // 测试方法：测试多个使用命名参数的可扫描表函数，可能会抛出SQL异常
      throws SQLException { // 方法声明
      try (Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 使用try-with-resources创建Calcite JDBC连接
           Statement statement = connection.createStatement()) { // 同时创建语句对象，也会自动关闭
        CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
            connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
        SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
        SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
        final TableFunction table1 = TableFunctionImpl.create(Smalls.MAZE_METHOD); // 使用MAZE_METHOD方法创建第一个表函数
        schema.add("Maze", table1); // 将第一个表函数注册到schema中，名称为'Maze'
        final TableFunction table2 = TableFunctionImpl.create(Smalls.MAZE2_METHOD); // 使用MAZE2_METHOD方法创建第二个表函数
        schema.add("Maze", table2); // 将第二个表函数注册到schema中，名称为'Maze'（覆盖第一个）
        final TableFunction table3 = TableFunctionImpl.create(Smalls.MAZE3_METHOD); // 使用MAZE3_METHOD方法创建第三个表函数
        schema.add("Maze", table3); // 将第三个表函数注册到schema中，名称为'Maze'（覆盖第二个）
        final String sql = "select *\n" // 构建第一个SQL查询字符串
            + "from table(\"s\".\"Maze\"(5, 3, 1))"; // 使用位置参数调用Maze表函数
        ResultSet resultSet = statement.executeQuery(sql); // 执行第一个查询获得结果集
        final String result = "S=abcde\n" // 预期结果的前两行
            + "S=xyz\n"; // 预期结果的前两行
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            is(result + "S=generate(w=5, h=3, s=1)\n")); // 断言结果等于预期值，调用第一个表函数

        final String sql2 = "select *\n" // 构建第二个SQL查询字符串
            + "from table(\"s\".\"Maze\"(WIDTH -> 5, HEIGHT -> 3, SEED -> 1))"; // 使用命名参数调用Maze表函数
        resultSet = statement.executeQuery(sql2); // 执行第二个查询获得结果集
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            is(result + "S=generate2(w=5, h=3, s=1)\n")); // 断言结果等于预期值，调用第二个表函数

        final String sql3 = "select *\n" // 构建第三个SQL查询字符串
            + "from table(\"s\".\"Maze\"(HEIGHT -> 3, WIDTH -> 5))"; // 使用命名参数调用Maze表函数，省略SEED参数
        resultSet = statement.executeQuery(sql3); // 执行第三个查询获得结果集
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            is(result + "S=generate2(w=5, h=3, s=null)\n")); // 断言结果等于预期值，调用第二个表函数，SEED为null

        final String sql4 = "select *\n" // 构建第四个SQL查询字符串
            + "from table(\"s\".\"Maze\"(FOO -> 'a'))"; // 使用命名参数调用Maze表函数，只传递FOO参数
        resultSet = statement.executeQuery(sql4); // 执行第四个查询获得结果集
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            is(result + "S=generate3(foo=a)\n")); // 断言结果等于预期值，调用第三个表函数
      } // try-with-resources自动关闭连接和语句
    } // 方法结束
  /**
   * 测试根据实际调用参数返回不同行类型的表函数
   * 该测试验证了：
   * 1. 表函数的动态结构特性
   * 2. 表函数可以根据参数返回不同的列结构
   * 3. 使用预编译语句传递参数
   */
  @Test void testTableFunctionDynamicStructure() throws SQLException { // 测试方法：测试表函数的动态结构功能，可能会抛出SQL异常
      Connection connection = getConnectionWithMultiplyFunction(); // 获取配置了乘法表函数的连接
      final PreparedStatement ps = connection.prepareStatement("select *\n" // 创建预编译语句对象
          + "from table(\"s\".\"multiplication\"(4, 3, ?))\n"); // SQL查询：调用乘法表函数，前两个参数固定，第三个参数使用占位符
      ps.setInt(1, 100); // 设置第一个占位符的值为100
      ResultSet resultSet = ps.executeQuery(); // 执行查询获得结果集
      assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
          equalTo("row_name=row 0; c1=101; c2=102; c3=103; c4=104\n" // 预期结果的第一行：4列，每列值递增
              + "row_name=row 1; c1=102; c2=104; c3=106; c4=108\n" // 预期结果的第二行：每列值比上一行增加1
              + "row_name=row 2; c1=103; c2=106; c3=109; c4=112\n")); // 预期结果的第三行：每列值比上一行增加1
    } // 方法结束
  /**
   * 测试表函数的非空参数必须以字面量形式提供
   * 该测试验证了：
   * 1. 表函数的非空参数不能使用参数化查询
   * 2. 尝试使用参数化查询会抛出异常
   * 3. 异常消息包含详细的参数类型信息
   */
  @Disabled("SQLException does not include message from nested exception") // 禁用该测试，因为SQLException不包含嵌套异常的消息
  @Test void testTableFunctionNonNullableMustBeLiterals() // 测试方法：测试表函数非空参数必须是字面量，可能会抛出SQL异常
      throws SQLException { // 方法声明
      Connection connection = getConnectionWithMultiplyFunction(); // 获取配置了乘法表函数的连接
      try { // 尝试执行查询
        final PreparedStatement ps = connection.prepareStatement("select *\n" // 创建预编译语句对象
            + "from table(\"s\".\"multiplication\"(?, 3, 100))\n"); // SQL查询：第一个参数使用占位符（错误用法）
        ps.setInt(1, 100); // 设置第一个占位符的值为100
        ResultSet resultSet = ps.executeQuery(); // 执行查询获得结果集
        fail("Should fail, got " + resultSet); // 如果执行成功，则测试失败
      } catch (SQLException e) { // 捕获SQL异常
        assertThat(e.getMessage(), // 断言异常消息包含特定内容
            containsString("Wrong arguments for table function 'public static " // 检查异常消息是否包含"表函数参数错误"
                + "org.apache.calcite.schema.QueryableTable " // 检查异常消息是否包含返回类型
                + "org.apache.calcite.test.JdbcTest" // 检查异常消息是否包含类名
                + ".multiplicationTable(int,int,java.lang.Integer)'" // 检查异常消息是否包含方法签名
                + " call. Expected '[int, int, class" // 检查异常消息是否包含期望的参数类型
                + "java.lang.Integer]', actual '[null, 3, 100]'")); // 检查异常消息是否包含实际的参数类型
      } // try-catch结束
    } // 方法结束
  private Connection getConnectionWithMultiplyFunction() throws SQLException { // 私有辅助方法：创建并返回配置了乘法表函数的连接，可能会抛出SQL异常
    Connection connection = // 创建JDBC连接对象
        DriverManager.getConnection("jdbc:calcite:"); // 使用Calcite JDBC驱动创建连接
    CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
        connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口，用于访问schema等高级功能
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema，这是schema层次结构的顶层
    SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema，使用AbstractSchema作为实现
    final TableFunction table = // 创建表函数对象
        TableFunctionImpl.create(Smalls.MULTIPLICATION_TABLE_METHOD); // 使用TableFunctionImpl工厂方法创建乘法表函数
    schema.add("multiplication", table); // 将乘法表函数注册到schema中，名称为'multiplication'
    return connection; // 返回配置好的连接对象
  } // 方法结束

  /**
   * 测试接收游标输入的表函数
   * 该测试验证了：
   * 1. 表函数可以接收游标作为输入参数
   * 2. 游标参数来自另一个表函数的查询结果
   * 3. 表函数可以处理游标数据并返回新的结果
   */
  @Disabled("CannotPlanException: Node [rel#18:Subset#4.ENUMERABLE.[]] " // 禁用该测试，因为无法实现计划异常
      + "could not be implemented") // 异常详情：节点无法实现
  @Test void testTableFunctionCursorInputs() throws SQLException { // 测试方法：测试表函数接收游标输入的功能，可能会抛出SQL异常
      try (Connection connection = // 使用try-with-resources创建Calcite JDBC连接
               DriverManager.getConnection("jdbc:calcite:")) { // 连接字符串为"jdbc:calcite:"
        CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
            connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
        SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
        SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
        final TableFunction table = // 创建表函数对象
            TableFunctionImpl.create(Smalls.GENERATE_STRINGS_METHOD); // 使用GENERATE_STRINGS_METHOD方法创建表函数
        schema.add("GenerateStrings", table); // 将表函数注册到schema中，名称为'GenerateStrings'
        final TableFunction add = // 创建另一个表函数对象
            TableFunctionImpl.create(Smalls.PROCESS_CURSOR_METHOD); // 使用PROCESS_CURSOR_METHOD方法创建处理游标的表函数
        schema.add("process", add); // 将处理游标的表函数注册到schema中，名称为'process'
        final PreparedStatement ps = connection.prepareStatement("select *\n" // 创建预编译语句对象
            + "from table(\"s\".\"process\"(2,\n" // 调用process表函数，第一个参数为2，第二个参数为游标
            + "cursor(select * from table(\"s\".\"GenerateStrings\"(?)))\n" // 游标参数：从GenerateStrings表函数查询结果中获取
            + ")) as t(u)\n" // 结果别名为t，列名为u
            + "where u > 3"); // 添加过滤条件：只选择大于3的值
        ps.setInt(1, 5); // 设置第一个占位符的值为5
        ResultSet resultSet = ps.executeQuery(); // 执行查询获得结果集
        // GenerateStrings returns 0..4, then 2 is added (process function), // 注释：GenerateStrings返回0..4，然后加上2（process函数）
        // thus 2..6, finally where u > 3 leaves just 4..6 // 注释：结果为2..6，最后where u > 3只留下4..6
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            equalTo("u=4\n" // 预期结果的第一行
                + "u=5\n" // 预期结果的第二行
                + "u=6\n")); // 预期结果的第三行
      } // try-with-resources自动关闭连接
    } // 方法结束
  /**
   * 测试接收多个游标输入的表函数
   * 该测试验证了：
   * 1. 表函数可以接收多个游标作为输入参数
   * 2. 游标参数来自不同表函数的查询结果
   * 3. 表函数可以合并处理多个游标数据
   */
  @Disabled("CannotPlanException: Node [rel#24:Subset#6.ENUMERABLE.[]] " // 禁用该测试，因为无法实现计划异常
      + "could not be implemented") // 异常详情：节点无法实现
  @Test void testTableFunctionCursorsInputs() throws SQLException { // 测试方法：测试表函数接收多个游标输入的功能，可能会抛出SQL异常
      try (Connection connection = getConnectionWithMultiplyFunction()) { // 使用try-with-resources获取配置了乘法表函数的连接
        CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
            connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
        SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
        SchemaPlus schema = rootSchema.subSchemas().get("s"); // 从根schema的子schema中获取名为's'的schema
        final TableFunction table = // 创建表函数对象
            TableFunctionImpl.create(Smalls.GENERATE_STRINGS_METHOD); // 使用GENERATE_STRINGS_METHOD方法创建表函数
        schema.add("GenerateStrings", table); // 将表函数注册到schema中，名称为'GenerateStrings'
        final TableFunction add = // 创建另一个表函数对象
            TableFunctionImpl.create(Smalls.PROCESS_CURSORS_METHOD); // 使用PROCESS_CURSORS_METHOD方法创建处理多个游标的表函数
        schema.add("process", add); // 将处理多个游标的表函数注册到schema中，名称为'process'
        final PreparedStatement ps = connection.prepareStatement("select *\n" // 创建预编译语句对象
            + "from table(\"s\".\"process\"(2,\n" // 调用process表函数，第一个参数为2，后面跟着两个游标参数
            + "cursor(select * from table(\"s\".\"multiplication\"(5,5,0))),\n" // 第一个游标参数：从multiplication表函数查询结果中获取
            + "cursor(select * from table(\"s\".\"GenerateStrings\"(?)))\n" // 第二个游标参数：从GenerateStrings表函数查询结果中获取
            + ")) as t(u)\n" // 结果别名为t，列名为u
            + "where u > 3"); // 添加过滤条件：只选择大于3的值
        ps.setInt(1, 5); // 设置第一个占位符的值为5
        ResultSet resultSet = ps.executeQuery(); // 执行查询获得结果集
        // GenerateStrings produce 0..4 // 注释：GenerateStrings产生0..4
        // multiplication produce 1..5 // 注释：multiplication产生1..5
        // process sums and adds 2 // 注释：process函数对两个游标求和并加上2
        // sum is 2 + 1..9 == 3..9 // 注释：最终结果为2 + 1..9，即3..9
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            equalTo("u=4\n" // 预期结果的第一行
                + "u=5\n" // 预期结果的第二行
                + "u=6\n" // 预期结果的第三行
                + "u=7\n" // 预期结果的第四行
                + "u=8\n" // 预期结果的第五行
                + "u=9\n")); // 预期结果的第六行
      } // try-with-resources自动关闭连接
    } // 方法结束
  /** 测试在FROM子句中使用表函数的查询
   *
   * @see Smalls#multiplicationTable // 参见Smalls类中的multiplicationTable方法 */
  @Test void testUserDefinedTableFunction() { // 测试方法：测试用户自定义表函数的基本查询功能
      final String q = "select *\n" // 构建SQL查询字符串
          + "from table(\"s\".\"multiplication\"(2, 3, 100))\n"; // 调用multiplication表函数，传入参数2, 3, 100
      with().query(q) // 使用with()方法创建的断言对象执行查询
          .returnsUnordered( // 断言返回结果（不关心顺序）
              "row_name=row 0; c1=101; c2=102", // 预期结果的第一行
              "row_name=row 1; c1=102; c2=104", // 预期结果的第二行
              "row_name=row 2; c1=103; c2=106"); // 预期结果的第三行
    } // 方法结束
  /** 测试在FROM子句中使用表函数的查询
   * 尝试在WHERE子句中引用表函数的列，但大小写错误
   *
   * @see Smalls#multiplicationTable // 参见Smalls类中的multiplicationTable方法 */
  @Test void testUserDefinedTableFunction2() { // 测试方法：测试表函数列名大小写敏感的错误处理
      final String q = "select c1\n" // 构建SQL查询字符串，选择c1列
          + "from table(\"s\".\"multiplication\"(2, 3, 100))\n" // 调用multiplication表函数
          + "where c1 + 2 < c2"; // WHERE子句中使用c1和c2（注意这里用的是小写）
      with().query(q) // 使用with()方法创建的断言对象执行查询
          .throws_("Column 'C1' not found in any table; did you mean 'c1'?"); // 断言抛出异常，提示列名大小写错误
    } // 方法结束
  /** 测试在FROM子句中使用表函数的查询
   * 在WHERE子句中正确引用列（使用引号保持大小写）
   *
   * @see Smalls#multiplicationTable // 参见Smalls类中的multiplicationTable方法 */
  @Test void testUserDefinedTableFunction3() { // 测试方法：测试表函数列名使用引号保持大小写的正确用法
      final String q = "select \"c1\"\n" // 构建SQL查询字符串，选择c1列（使用引号）
          + "from table(\"s\".\"multiplication\"(2, 3, 100))\n" // 调用multiplication表函数
          + "where \"c1\" + 2 < \"c2\""; // WHERE子句中使用引号引用c1和c2列
      with().query(q).returnsUnordered("c1=103"); // 断言返回结果（不关心顺序），只有c1=103满足条件
    } // 方法结束
  /** 与 {@link #testUserDefinedTableFunction3()} 类似，但为整数参数提供字符字面量参数 */
  @Test void testUserDefinedTableFunction4() { // 测试方法：测试表函数参数类型自动转换
      final String q = "select \"c1\"\n" // 构建SQL查询字符串，选择c1列（使用引号）
          + "from table(\"s\".\"multiplication\"('2', 3, 100))\n" // 调用multiplication表函数，第一个参数使用字符字面量'2'
          + "where \"c1\" + 2 < \"c2\""; // WHERE子句中使用引号引用c1和c2列
      with().query(q).returnsUnordered("c1=103"); // 断言返回结果（不关心顺序），字符'2'被自动转换为整数2
    } // 方法结束
  @Test void testUserDefinedTableFunction5() { // 测试方法：测试表函数参数数量不匹配的错误处理
    final String q = "select *\n" // 构建SQL查询字符串
        + "from table(\"s\".\"multiplication\"(3, 100))\n" // 调用multiplication表函数，只传入2个参数（错误，需要3个）
        + "where c1 + 2 < c2"; // WHERE子句
    final String e = "No match found for function signature " // 预期错误消息前缀
        + "multiplication(<NUMERIC>, <NUMERIC>)"; // 预期错误消息后缀，表示找不到匹配的函数签名
    with().query(q).throws_(e); // 断言抛出异常，异常消息包含指定的函数签名错误
  } // 方法结束

  @Test void testUserDefinedTableFunction6() { // 测试方法：测试斐波那契表函数（无参数版本）
    final String q = "select *\n" // 构建SQL查询字符串
        + "from table(\"s\".\"fibonacci\"())"; // 调用fibonacci表函数，无参数
    with().query(q) // 使用with()方法创建的断言对象执行查询
        .returns(r -> { // 使用自定义断言验证返回结果
          try { // 尝试处理结果集
            final List<Long> numbers = new ArrayList<>(); // 创建列表存储斐波那契数列
            while (r.next() && numbers.size() < 13) { // 遍历结果集，最多读取13个数
              numbers.add(r.getLong(1)); // 将第一列的值添加到列表中
            } // while循环结束
            final long[] expectedNumbers = {1, 1, 2, 3, 5, 8, 13, 21, 34, 55, // 预期的斐波那契数列前13项
                89, 144, 233}; // 预期的斐波那契数列后3项
            assertThat(numbers, is(Primitive.asList(expectedNumbers))); // 断言实际结果等于预期结果
          } catch (SQLException e) { // 捕获SQL异常
            throw TestUtil.rethrow(e); // 使用TestUtil工具重新抛出异常
          } // try-catch结束
        }); // returns方法结束
  } // 方法结束

  @Test void testUserDefinedTableFunction7() { // 测试方法：测试斐波那契表函数（带限制参数版本）并过滤结果
    final String q = "select *\n" // 构建SQL查询字符串
        + "from table(\"s\".\"fibonacci2\"(20))\n" // 调用fibonacci2表函数，传入参数20（限制斐波那契数列不超过20）
        + "where n > 7"; // WHERE子句：只选择大于7的数
    with().query(q).returnsUnordered("N=13", "N=8"); // 断言返回结果（不关心顺序），只有8和13满足条件
  } // 方法结束

  @Test void testUserDefinedTableFunction8() { // 测试方法：测试斐波那契表函数的聚合查询
    final String q = "select count(*) as c\n" // 构建SQL查询字符串，统计行数
        + "from table(\"s\".\"fibonacci2\"(20))"; // 调用fibonacci2表函数，传入参数20
    with().query(q).returnsUnordered("C=7"); // 断言返回结果（不关心顺序），斐波那契数列中小于20的数有7个
  } // 方法结束

  /** 测试用例：
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3364">[CALCITE-3364]
   * 如果表函数返回单值行，则无法对表函数结果进行分组，因为存在类型转换错误</a> */
  @Test void testUserDefinedTableFunction9() { // 测试方法：测试表函数单值行的分组操作
      final String q = "select \"N\" + 1 as c\n" // 构建SQL查询字符串，将N列的值加1
          + "from table(\"s\".\"fibonacci2\"(3))\n" // 调用fibonacci2表函数，传入参数3
          + "group by \"N\""; // 按N列分组
      with().query(q).returnsUnordered("C=2", // 断言返回结果（不关心顺序），第一个分组结果
          "C=3", // 断言返回结果（不关心顺序），第二个分组结果
          "C=4"); // 断言返回结果（不关心顺序），第三个分组结果
    } // 方法结束
  @Test void testCrossApply() { // 测试方法：测试CROSS APPLY操作符与表函数的结合使用
    final String q1 = "select *\n" // 构建第一个SQL查询字符串
        + "from (values 2, 5) as t (c)\n" // 从值表(2)和(5)中选择，别名为t，列名为c
        + "cross apply table(\"s\".\"fibonacci2\"(c))"; // 使用CROSS APPLY调用fibonacci2表函数，传入c列的值
    final String q2 = "select *\n" // 构建第二个SQL查询字符串
        + "from (values 2, 5) as t (c)\n" // 从值表(2)和(5)中选择，别名为t，列名为c
        + "cross apply table(\"s\".\"fibonacci2\"(t.c))"; // 使用CROSS APPLY调用fibonacci2表函数，传入t.c列的值（显式指定表别名）
    for (String q : new String[] {q1, q2}) { // 遍历两个查询
      with() // 使用with()方法创建的断言对象
          .with(CalciteConnectionProperty.CONFORMANCE, // 设置连接属性
              SqlConformanceEnum.LENIENT) // 设置SQL兼容性为宽松模式
          .query(q) // 执行查询
          .returnsUnordered("C=2; N=1", // 断言返回结果（不关心顺序），第一行
              "C=2; N=1", // 断言返回结果（不关心顺序），第二行
              "C=2; N=2", // 断言返回结果（不关心顺序），第三行
              "C=5; N=1", // 断言返回结果（不关心顺序），第四行
              "C=5; N=1", // 断言返回结果（不关心顺序），第五行
              "C=5; N=2", // 断言返回结果（不关心顺序），第六行
              "C=5; N=3", // 断言返回结果（不关心顺序），第七行
              "C=5; N=5"); // 断言返回结果（不关心顺序），第八行
    } // for循环结束
  } // 方法结束

  /** 测试用例：
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2004">[CALCITE-2004]
   * 使用表函数的左外连接应用生成了错误的计划</a> */
  @Test void testLeftOuterApply() { // 测试方法：测试LEFT OUTER APPLY操作符与表函数的结合使用
      final String sql = "select *\n" // 构建SQL查询字符串
          + "from (values 4) as t (c)\n" // 从值表(4)中选择，别名为t，列名为c
          + "left join lateral table(\"s\".\"fibonacci2\"(c)) as R(n) on c=n"; // 使用LEFT OUTER JOIN LATERAL调用fibonacci2表函数，连接条件为c=n
      with() // 使用with()方法创建的断言对象
          .with(CalciteConnectionProperty.CONFORMANCE, // 设置连接属性
              SqlConformanceEnum.LENIENT) // 设置SQL兼容性为宽松模式
          .query(sql) // 执行查询
          .returnsUnordered("C=4; N=null"); // 断言返回结果（不关心顺序），fibonacci2(4)没有返回4，所以N为null
    } // 方法结束
  /** 测试用例：
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2382">[CALCITE-2382]
   * 子查询侧向连接到表函数</a> */
  @Test void testInlineViewLateralTableFunction() throws SQLException { // 测试方法：测试内联视图与LATERAL表函数的结合使用，可能会抛出SQL异常
      try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) { // 使用try-with-resources创建Calcite JDBC连接
        CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
            connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
        SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
        SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
        final TableFunction table = // 创建表函数对象
            TableFunctionImpl.create(Smalls.GENERATE_STRINGS_METHOD); // 使用GENERATE_STRINGS_METHOD方法创建表函数
        schema.add("GenerateStrings", table); // 将表函数注册到schema中，名称为'GenerateStrings'
        Table tbl = new ScannableTableTest.SimpleTable(); // 创建简单表对象
        schema.add("t", tbl); // 将表注册到schema中，名称为't'

        final String sql = "select *\n" // 构建SQL查询字符串
            + "from (select 5 as f0 from \"s\".\"t\") \"a\",\n" // 内联视图：从t表中选择5，别名为a，列名为f0
            + "  lateral table(\"s\".\"GenerateStrings\"(f0)) as t(n, c)\n" // 使用LATERAL调用GenerateStrings表函数，传入f0列的值
            + "where char_length(c) > 3"; // WHERE子句：只选择长度大于3的字符串
        ResultSet resultSet = connection.createStatement().executeQuery(sql); // 执行查询获得结果集
        final String expected = "F0=5; N=4; C=abcd\n" // 预期结果的第一行
            + "F0=5; N=4; C=abcd\n" // 预期结果的第二行
            + "F0=5; N=4; C=abcd\n" // 预期结果的第三行
            + "F0=5; N=4; C=abcd\n"; // 预期结果的第四行
        assertThat(CalciteAssert.toString(resultSet), equalTo(expected)); // 断言结果等于预期值
      } // try-with-resources自动关闭连接
    } // 方法结束
  /** 测试用例：
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4448">[CALCITE-4448]
   * 使用TableMacro用户定义表函数与QueryableTable</a> */
  @Test void testQueryableTableWithTableMacro() throws SQLException { // 测试方法：测试QueryableTable与TableMacro的结合使用，可能会抛出SQL异常
      try (Connection connection = // 使用try-with-resources创建Calcite JDBC连接
          DriverManager.getConnection("jdbc:calcite:")) { // 连接字符串为"jdbc:calcite:"
        CalciteConnection calciteConnection = // 将JDBC连接解包为CalciteConnection类型
            connection.unwrap(CalciteConnection.class); // 获取Calcite特定连接接口
        SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根schema
        SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根schema下添加名为's'的子schema
        schema.add("simple", new Smalls.SimpleTableMacro()); // 将SimpleTableMacro注册到schema中，名称为'simple'

        String sql = "select * from table(\"s\".\"simple\"())"; // 构建SQL查询字符串，调用simple表函数
        ResultSet resultSet = connection.createStatement().executeQuery(sql); // 执行查询获得结果集
        String expected = "A=foo; B=5\n" // 预期结果的第一行
            + "A=bar; B=4\n" // 预期结果的第二行
            + "A=foo; B=3\n"; // 预期结果的第三行
        assertThat(CalciteAssert.toString(resultSet), // 将结果集转换为字符串
            equalTo(expected)); // 断言结果等于预期值
      } // try-with-resources自动关闭连接
    } // 方法结束
  } // 类结束
