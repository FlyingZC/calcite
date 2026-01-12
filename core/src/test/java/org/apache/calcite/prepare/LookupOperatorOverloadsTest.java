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
// Apache许可证声明，说明代码版权和使用许可
package org.apache.calcite.prepare; // 声明包名，该类属于org.apache.calcite.prepare包，用于SQL查询准备阶段的测试

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建和管理Java类型系统
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接接口，代表与Calcite数据库的连接
import org.apache.calcite.jdbc.CalcitePrepare; // 导入Calcite准备类，用于SQL查询的准备和优化
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，代表可扩展的数据库模式
import org.apache.calcite.schema.TableFunction; // 导入表函数接口，代表可以返回表的函数
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象模式类，提供模式的基础实现
import org.apache.calcite.schema.impl.TableFunctionImpl; // 导入表函数实现类，用于创建表函数实例
import org.apache.calcite.server.CalciteServerStatement; // 导入Calcite服务器语句类，代表服务器端的SQL语句
import org.apache.calcite.sql.SqlFunctionCategory; // 导入SQL函数分类枚举，定义函数的类别类型
import org.apache.calcite.sql.SqlIdentifier; // 导入SQL标识符类，代表SQL中的标识符（如表名、函数名）
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符类，代表SQL中的操作符（如函数、运算符）
import org.apache.calcite.sql.SqlSyntax; // 导入SQL语法枚举，定义操作符的语法类型
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析位置类，记录SQL解析的位置信息
import org.apache.calcite.sql.validate.SqlNameMatcher; // 导入SQL名称匹配器接口，用于匹配SQL名称
import org.apache.calcite.sql.validate.SqlNameMatchers; // 导入SQL名称匹配器工厂类，用于创建名称匹配器
import org.apache.calcite.sql.validate.SqlUserDefinedTableFunction; // 导入SQL用户定义表函数类，代表用户定义的表函数
import org.apache.calcite.util.Smalls; // 导入Smalls工具类，提供测试用的辅助方法和数据

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，提供线程安全的不可变列表

import com.google.common.collect.Lists; // 导入Google Guava的Lists工具类，提供列表操作的便捷方法



import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可能为null的值

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法



import java.sql.Connection; // 导入JDBC连接接口，代表数据库连接

import java.sql.DriverManager; // 导入JDBC驱动管理器，用于获取数据库连接

import java.sql.SQLException; // 导入SQL异常类，处理SQL相关的异常

import java.util.ArrayList; // 导入Java集合框架的ArrayList类，提供动态数组实现

import java.util.List; // 导入Java集合框架的List接口，代表有序集合



import static org.apache.calcite.sql.SqlFunctionCategory.MATCH_RECOGNIZE; // 静态导入MATCH_RECOGNIZE函数类别，用于匹配识别

import static org.apache.calcite.sql.SqlFunctionCategory.USER_DEFINED_CONSTRUCTOR; // 静态导入用户定义构造函数类别

import static org.apache.calcite.sql.SqlFunctionCategory.USER_DEFINED_FUNCTION; // 静态导入用户定义函数类别

import static org.apache.calcite.sql.SqlFunctionCategory.USER_DEFINED_PROCEDURE; // 静态导入用户定义存储过程类别

import static org.apache.calcite.sql.SqlFunctionCategory.USER_DEFINED_SPECIFIC_FUNCTION; // 静态导入用户定义特定函数类别

import static org.apache.calcite.sql.SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION; // 静态导入用户定义表函数类别

import static org.apache.calcite.sql.SqlFunctionCategory.USER_DEFINED_TABLE_SPECIFIC_FUNCTION; // 静态导入用户定义特定表函数类别



import static org.apache.calcite.test.Matchers.isListOf; // 静态导入isListOf匹配器，用于验证列表内容



import static org.hamcrest.CoreMatchers.instanceOf; // 静态导入instanceOf匹配器，用于验证对象类型

import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器，用于验证值相等

import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat断言方法，用于单元测试断言



import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象非空

/**
 * Test for lookupOperatorOverloads() in {@link CalciteCatalogReader}.
 */
// 测试类注释：此类用于测试CalciteCatalogReader中的lookupOperatorOverloads()方法
// lookupOperatorOverloads()方法是Calcite中用于查找操作符重载的核心方法
// 该测试类验证了在目录中查找用户定义函数的能力，包括表函数的查找
// 测试内容包括：函数分类验证、大小写敏感查找、操作符重载查找等
class LookupOperatorOverloadsTest { // 测试类定义，用于测试CalciteCatalogReader的lookupOperatorOverloads()方法

  private void checkFunctionType(int size, @Nullable String name, // 私有辅助方法，用于验证函数类型和名称，参数size表示期望的操作符数量，name表示期望的函数名称，operatorList表示待验证的操作符列表
      List<SqlOperator> operatorList) { // 操作符列表参数，包含所有查找到的SQL操作符
    assertThat(size, is(operatorList.size())); // 断言验证：实际操作符数量应该等于期望的数量size

    for (SqlOperator op : operatorList) { // 遍历操作符列表中的每个操作符
      assertThat(op, instanceOf(SqlUserDefinedTableFunction.class)); // 断言验证：每个操作符都应该是SqlUserDefinedTableFunction类型（用户定义表函数）
      assertThat(op.getName(), is(name)); // 断言验证：每个操作符的名称应该等于期望的函数名称name
    } // 结束for循环
  } // 结束checkFunctionType方法

  private static void check(List<SqlFunctionCategory> actuals, // 私有静态辅助方法，用于验证函数类别列表，参数actuals表示实际的函数类别列表
      SqlFunctionCategory... expecteds) { // 可变参数，表示期望的函数类别列表
    assertThat(actuals, isListOf(expecteds)); // 断言验证：使用isListOf匹配器验证实际的函数类别列表是否包含所有期望的类别
  } // 结束check方法

  @Test void testIsUserDefined() { // 测试方法：测试SqlFunctionCategory.isUserDefined()方法是否正确识别用户定义的函数类别
    List<SqlFunctionCategory> cats = new ArrayList<>(); // 创建一个列表，用于存储所有用户定义的函数类别
    for (SqlFunctionCategory c : SqlFunctionCategory.values()) { // 遍历所有可能的SQL函数类别
      if (c.isUserDefined()) { // 判断当前函数类别是否为用户定义的
        cats.add(c); // 如果是用户定义的，则添加到列表中
      } // 结束if语句
    } // 结束for循环
    check(cats, USER_DEFINED_FUNCTION, USER_DEFINED_PROCEDURE, // 调用check方法验证列表内容，期望包含以下用户定义函数类别
        USER_DEFINED_CONSTRUCTOR, USER_DEFINED_SPECIFIC_FUNCTION, // 用户定义构造函数类别
        USER_DEFINED_TABLE_FUNCTION, USER_DEFINED_TABLE_SPECIFIC_FUNCTION); // 用户定义表函数类别和特定表函数类别
  } // 结束testIsUserDefined方法

  @Test void testIsTableFunction() { // 测试方法：测试SqlFunctionCategory.isTableFunction()方法是否正确识别表函数类别
    List<SqlFunctionCategory> cats = new ArrayList<>(); // 创建一个列表，用于存储所有表函数类别
    for (SqlFunctionCategory c : SqlFunctionCategory.values()) { // 遍历所有可能的SQL函数类别
      if (c.isTableFunction()) { // 判断当前函数类别是否为表函数
        cats.add(c); // 如果是表函数，则添加到列表中
      } // 结束if语句
    } // 结束for循环
    check(cats, USER_DEFINED_TABLE_FUNCTION, // 调用check方法验证列表内容，期望包含以下表函数类别
        USER_DEFINED_TABLE_SPECIFIC_FUNCTION, MATCH_RECOGNIZE); // 用户定义表函数类别、特定表函数类别和匹配识别类别
  } // 结束testIsTableFunction方法

  @Test void testIsSpecific() { // 测试方法：测试SqlFunctionCategory.isSpecific()方法是否正确识别特定函数类别
    List<SqlFunctionCategory> cats = new ArrayList<>(); // 创建一个列表，用于存储所有特定函数类别
    for (SqlFunctionCategory c : SqlFunctionCategory.values()) { // 遍历所有可能的SQL函数类别
      if (c.isSpecific()) { // 判断当前函数类别是否为特定函数
        cats.add(c); // 如果是特定函数，则添加到列表中
      } // 结束if语句
    } // 结束for循环
    check(cats, USER_DEFINED_SPECIFIC_FUNCTION, // 调用check方法验证列表内容，期望包含以下特定函数类别
        USER_DEFINED_TABLE_SPECIFIC_FUNCTION); // 用户定义特定函数类别和用户定义特定表函数类别
  } // 结束testIsSpecific方法

  @Test void testIsUserDefinedNotSpecificFunction() { // 测试方法：测试isUserDefinedNotSpecificFunction()方法是否正确识别非特定的用户定义函数类别
    List<SqlFunctionCategory> cats = new ArrayList<>(); // 创建一个列表，用于存储所有非特定的用户定义函数类别
    for (SqlFunctionCategory sqlFunctionCategory : SqlFunctionCategory.values()) { // 遍历所有可能的SQL函数类别
      if (sqlFunctionCategory.isUserDefinedNotSpecificFunction()) { // 判断当前函数类别是否为非特定的用户定义函数
        cats.add(sqlFunctionCategory); // 如果是，则添加到列表中
      } // 结束if语句
    } // 结束for循环
    check(cats, USER_DEFINED_FUNCTION, USER_DEFINED_TABLE_FUNCTION); // 调用check方法验证列表内容，期望包含用户定义函数和用户定义表函数
  } // 结束testIsUserDefinedNotSpecificFunction方法

  @Test void testLookupCaseSensitively() throws SQLException { // 测试方法：测试在大小写敏感模式下查找操作符重载的功能
    checkInternal(true); // 调用内部检查方法，传入true表示启用大小写敏感模式
  } // 结束testLookupCaseSensitively方法

  @Test void testLookupCaseInSensitively() throws SQLException { // 测试方法：测试在大小写不敏感模式下查找操作符重载的功能
    checkInternal(false); // 调用内部检查方法，传入false表示禁用大小写敏感模式（即大小写不敏感）
  } // 结束testLookupCaseInSensitively方法

  private void checkInternal(boolean caseSensitive) throws SQLException { // 私有内部检查方法，用于测试操作符重载查找功能，参数caseSensitive表示是否启用大小写敏感模式
    final SqlNameMatcher nameMatcher = // 创建SQL名称匹配器，用于函数名称的匹配
        SqlNameMatchers.withCaseSensitive(caseSensitive); // 根据caseSensitive参数创建相应的大小写敏感或不敏感的名称匹配器
    final String schemaName = "MySchema"; // 定义测试用的模式名称为"MySchema"
    final String funcName = "MyFUNC"; // 定义测试用的函数名称为"MyFUNC"（大小写混合，用于测试大小写敏感）
    final String anotherName = "AnotherFunc"; // 定义另一个测试用的函数名称为"AnotherFunc"

    try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) { // 使用try-with-resources创建Calcite数据库连接，连接字符串为"jdbc:calcite:"
      CalciteConnection calciteConnection = // 将JDBC连接解包为Calcite连接类型
          connection.unwrap(CalciteConnection.class); // 使用unwrap方法获取底层的CalciteConnection对象
      SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根模式（root schema），这是所有模式的顶层容器
      SchemaPlus schema = rootSchema.add(schemaName, new AbstractSchema()); // 在根模式下添加一个名为"MySchema"的新模式，使用AbstractSchema作为基础实现
      final TableFunction table = // 创建第一个表函数实例
          requireNonNull(TableFunctionImpl.create(Smalls.MAZE_METHOD)); // 使用Smalls.MAZE_METHOD方法创建表函数，requireNonNull确保创建结果不为null
      schema.add(funcName, table); // 将第一个表函数添加到模式中，函数名为"MyFUNC"
      schema.add(anotherName, table); // 将同一个表函数添加到模式中，但使用不同的名称"AnotherFunc"（测试函数查找）
      final TableFunction table2 = // 创建第二个表函数实例
          requireNonNull(TableFunctionImpl.create(Smalls.MAZE3_METHOD)); // 使用Smalls.MAZE3_METHOD方法创建另一个表函数，requireNonNull确保创建结果不为null
      schema.add(funcName, table2); // 将第二个表函数添加到模式中，使用相同的函数名"MyFUNC"（测试函数重载）

      final CalciteServerStatement statement = // 创建Calcite服务器语句对象
          connection.createStatement().unwrap(CalciteServerStatement.class); // 创建JDBC语句并解包为CalciteServerStatement类型
      final CalcitePrepare.Context prepareContext = // 创建准备上下文，用于SQL查询的准备阶段
          statement.createPrepareContext(); // 通过语句对象创建准备上下文，包含类型工厂、配置等信息
      final JavaTypeFactory typeFactory = prepareContext.getTypeFactory(); // 从准备上下文中获取Java类型工厂，用于创建和管理类型
      CalciteCatalogReader reader = // 创建Calcite目录读取器，用于在目录中查找操作符和函数
          new CalciteCatalogReader(prepareContext.getRootSchema(), // 使用准备上下文的根模式创建目录读取器
              ImmutableList.of(), typeFactory, prepareContext.config()); // 传入空路径列表、类型工厂和配置对象

      final List<SqlOperator> operatorList = new ArrayList<>(); // 创建操作符列表，用于存储查找到的操作符
      SqlIdentifier myFuncIdentifier = // 创建SQL标识符对象，代表要查找的函数
          new SqlIdentifier(Lists.newArrayList(schemaName, funcName), null, // 创建包含模式名和函数名的标识符，使用Lists.newArrayList创建列表
              SqlParserPos.ZERO, null); // 使用ZERO位置（表示位置未知）和null的限定符
      reader.lookupOperatorOverloads(myFuncIdentifier, // 调用目录读取器的lookupOperatorOverloads方法查找操作符重载
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION, SqlSyntax.FUNCTION, // 指定查找用户定义表函数，语法类型为FUNCTION
          operatorList, nameMatcher); // 将查找到的操作符添加到operatorList中，使用nameMatcher进行名称匹配
      checkFunctionType(2, funcName, operatorList); // 验证查找到的操作符：期望有2个操作符，名称为"MyFUNC"

      operatorList.clear(); // 清空操作符列表，准备进行下一次查找测试
      reader.lookupOperatorOverloads(myFuncIdentifier, // 再次调用目录读取器的lookupOperatorOverloads方法查找操作符重载
          SqlFunctionCategory.USER_DEFINED_FUNCTION, SqlSyntax.FUNCTION, // 这次指定查找用户定义函数（不是表函数）
          operatorList, nameMatcher); // 使用相同的标识符和名称匹配器
      checkFunctionType(0, null, operatorList); // 验证查找到的操作符：期望有0个操作符（因为查找到的是表函数，不是普通函数）

      operatorList.clear(); // 清空操作符列表，准备查找另一个函数
      SqlIdentifier anotherFuncIdentifier = // 创建另一个SQL标识符对象
          new SqlIdentifier(Lists.newArrayList(schemaName, anotherName), null, // 创建包含模式名和另一个函数名的标识符
              SqlParserPos.ZERO, null); // 使用ZERO位置和null的限定符
      reader.lookupOperatorOverloads(anotherFuncIdentifier, // 调用目录读取器的lookupOperatorOverloads方法查找操作符重载
          SqlFunctionCategory.USER_DEFINED_TABLE_FUNCTION, SqlSyntax.FUNCTION, // 指定查找用户定义表函数，语法类型为FUNCTION
          operatorList, nameMatcher); // 将查找到的操作符添加到operatorList中，使用nameMatcher进行名称匹配
      checkFunctionType(1, anotherName, operatorList); // 验证查找到的操作符：期望有1个操作符，名称为"AnotherFunc"
    } // 结束try-with-resources块，自动关闭连接
  } // 结束checkInternal方法
} // 结束LookupOperatorOverloadsTest类
