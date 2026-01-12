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
package org.apache.calcite.test; // 声明包名为org.apache.calcite.test
import org.apache.calcite.DataContext; // 导入数据上下文接口，用于在查询执行期间访问运行时信息
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置接口
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接类，扩展标准JDBC连接
import org.apache.calcite.linq4j.AbstractEnumerable; // 导入抽象可枚举类，用于实现LINQ风格的枚举
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可遍历的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示表中的列类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.runtime.CalciteContextException; // 导入Calcite上下文异常类，表示查询执行时的上下文错误
import org.apache.calcite.schema.ScannableTable; // 导入可扫描表接口，表示可以全表扫描的表
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示数据库模式
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，扩展Schema接口，支持添加表和函数
import org.apache.calcite.schema.Statistic; // 导入统计信息接口，提供表的统计元数据
import org.apache.calcite.schema.Statistics; // 导入统计信息工具类，提供预定义的统计信息
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象Schema实现类，用于创建自定义Schema
import org.apache.calcite.sql.SqlCall; // 导入SQL调用类，表示SQL函数调用或操作符调用
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，表示SQL语法树的节点
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DriverManager; // 导入JDBC驱动管理器类，用于获取数据库连接
import java.sql.ResultSet; // 导入JDBC结果集接口，表示查询结果
import java.sql.SQLException; // 导入JDBC SQL异常类，表示数据库访问错误
import java.sql.Statement; // 导入JDBC语句接口，用于执行SQL语句
import java.util.Arrays; // 导入数组工具类，提供数组操作方法
import java.util.HashMap; // 导入HashMap类，实现Map接口的哈希表
import java.util.List; // 导入List接口，表示有序集合
import java.util.Map; // 导入Map接口，表示键值对映射

import static org.hamcrest.CoreMatchers.instanceOf; // 导入Hamcrest断言：检查对象是否为指定类型的实例
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言：检查两个值是否相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类，用于执行断言
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest匹配器：检查集合的大小
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit5断言方法，用于标记测试失败

/**
 * Test cases for
 * <a href="https://issues.apache.org/jira/browse/CALCITE-1386">[CALCITE-1386]
 * ITEM operator seems to ignore the value type of collection and assign the value to Object</a>.
 */
// 本测试类用于测试 Calcite 中集合类型（Map、嵌套Map、数组）的访问和操作
// 主要测试场景包括：
// 1. 访问嵌套Map中的值
// 2. 访问数组中的元素
// 3. 访问不存在的键时的行为
// 4. 使用ANY类型时的类型转换和操作
// 5. 边界情况处理（如数组越界）
// 6. 验证ITEM操作符是否正确处理集合的值类型
class CollectionTypeTest {
  @Test void testAccessNestedMap() throws Exception { // 测试访问嵌套Map和数组的基本功能
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象
    final String sql = "select \"ID\", \"MAPFIELD\"['c'] AS \"MAPFIELD_C\"," // 构建SQL查询：选择ID字段、MAPFIELD中键'c'的值（别名为MAPFIELD_C）
        + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where \"NESTEDMAPFIELD\"['a']['b'] = 2 AND \"ARRAYFIELD\"[2] = 200"; // WHERE条件：嵌套Map中a.b的值为2且数组索引2的值为200
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(1)); // 验证查询返回1行结果

    // JDBC doesn't support Map / Nested Map so just relying on string representation
    String expectedRow = "ID=2; MAPFIELD_C=4; NESTEDMAPFIELD={a={b=2, c=4}}; " // 期望的结果行：ID为2，MAPFIELD中'c'键的值为4，嵌套Map结构为{a={b=2, c=4}}
        + "ARRAYFIELD=[100, 200, 300]"; // 数组值为[100, 200, 300]
    assertThat(resultStrings.get(0), is(expectedRow)); // 验证实际结果与期望结果一致
  }

  @Test void testAccessNonExistKeyFromMap() throws Exception { // 测试访问Map中不存在的键时的行为
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    // this shouldn't throw any Exceptions on runtime, just don't return any rows.
    final String sql = "select \"ID\"," // 构建SQL查询：选择ID字段
        + " \"MAPFIELD\", \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及Map字段、嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where \"MAPFIELD\"['a'] = 2"; // WHERE条件：MAPFIELD中键'a'的值为2（该键不存在）
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(0)); // 验证查询返回0行结果（因为键'a'不存在）
  }

  @Test void testAccessNonExistKeyFromNestedMap() throws Exception { // 测试访问嵌套Map中不存在的键时的行为
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    // this shouldn't throw any Exceptions on runtime, just don't return any rows.
    final String sql = "select \"ID\", \"MAPFIELD\"," // 构建SQL查询：选择ID字段和Map字段
        + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where \"NESTEDMAPFIELD\"['b']['c'] = 4"; // WHERE条件：嵌套Map中b.c的值为4（键'b'不存在）
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(0)); // 验证查询返回0行结果（因为键'b'不存在）
  }

  @Test void testInvalidAccessUseStringForIndexOnArray() throws Exception { // 测试使用字符串作为数组索引时的错误处理
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    try {
      final String sql = "select \"ID\"," // 构建SQL查询：选择ID字段
          + " \"MAPFIELD\", \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及Map字段、嵌套Map字段和数组字段
          + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
          + "where \"ARRAYFIELD\"['a'] = 200"; // WHERE条件：使用字符串'a'作为数组索引（这是无效的，数组索引应该是整数）
      statement.executeQuery(sql); // 执行SQL查询，预期会抛出异常

      fail("This query shouldn't be evaluated properly"); // 如果没有抛出异常，则测试失败
    } catch (SQLException e) { // 捕获SQL异常
      Throwable e2 = e.getCause(); // 获取异常的根本原因
      assertThat(e2, is(instanceOf(CalciteContextException.class))); // 验证根本原因是CalciteContextException类型
    }
  }

  @Test void testNestedArrayOutOfBoundAccess() throws Exception { // 测试数组越界访问时的行为
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    final String sql = "select \"ID\"," // 构建SQL查询：选择ID字段
        + " \"MAPFIELD\", \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及Map字段、嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where \"ARRAYFIELD\"[10] = 200"; // WHERE条件：访问数组索引10（超出数组边界，数组只有3个元素）
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表

    // this is against SQL standard definition...
    // SQL standard states that data exception should be occurred
    // when accessing array with out of bound index.
    // but PostgreSQL breaks it, and this is more convenient since it guarantees runtime safety.
    assertThat(resultStrings, hasSize(0)); // 验证查询返回0行结果（越界访问不抛出异常，而是返回空结果，这是为了运行时安全）
  }

  @Test void testAccessNestedMapWithAnyType() throws Exception { // 测试使用ANY类型字段时的访问和类型转换
    Connection connection = setupConnectionWithNestedAnyTypeTable(); // 建立包含ANY类型嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    final String sql = "select \"ID\", \"MAPFIELD\"['c'] AS \"MAPFIELD_C\"," // 构建SQL查询：选择ID字段、MAPFIELD中键'c'的值（别名为MAPFIELD_C）
        + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where CAST(\"NESTEDMAPFIELD\"['a']['b'] AS INTEGER) = 2" // WHERE条件：将嵌套Map中a.b的值转换为整数并与2比较
        + " AND CAST(\"ARRAYFIELD\"[2] AS INTEGER) = 200"; // 并且将数组索引2的值转换为整数并与200比较
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(1)); // 验证查询返回1行结果

    // JDBC doesn't support Map / Nested Map so just relying on string representation
    String expectedRow = "ID=2; MAPFIELD_C=4; NESTEDMAPFIELD={a={b=2, c=4}}; " // 期望的结果行：ID为2，MAPFIELD中'c'键的值为4，嵌套Map结构为{a={b=2, c=4}}
        + "ARRAYFIELD=[100, 200, 300]"; // 数组值为[100, 200, 300]
    assertThat(resultStrings.get(0), is(expectedRow)); // 验证实际结果与期望结果一致
  }

  @Test void testAccessNestedMapWithAnyTypeWithoutCast() throws Exception { // 测试使用ANY类型字段时不进行显式类型转换的比较操作
    Connection connection = setupConnectionWithNestedAnyTypeTable(); // 建立包含ANY类型嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    // placing literal earlier than ANY type is intended: do not modify
    final String sql = "select \"ID\", \"MAPFIELD\"['c'] AS \"MAPFIELD_C\"," // 构建SQL查询：选择ID字段、MAPFIELD中键'c'的值（别名为MAPFIELD_C）
        + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where \"NESTEDMAPFIELD\"['a']['b'] = 2 AND 200.0 = \"ARRAYFIELD\"[2]"; // WHERE条件：字面量在ANY类型之前，测试隐式类型转换

    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(1)); // 验证查询返回1行结果

    // JDBC doesn't support Map / Nested Map so just relying on string representation
    String expectedRow = "ID=2; MAPFIELD_C=4; NESTEDMAPFIELD={a={b=2, c=4}}; " // 期望的结果行：ID为2，MAPFIELD中'c'键的值为4，嵌套Map结构为{a={b=2, c=4}}
        + "ARRAYFIELD=[100, 200, 300]"; // 数组值为[100, 200, 300]
    assertThat(resultStrings.get(0), is(expectedRow)); // 验证实际结果与期望结果一致
  }


  @Test void testArithmeticToAnyTypeWithoutCast() throws Exception { // 测试对ANY类型字段进行算术运算和比较操作而不进行显式类型转换
    Connection connection = setupConnectionWithNestedAnyTypeTable(); // 建立包含ANY类型嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    // placing literal earlier than ANY type is intended: do not modify
    final String sql = "select \"ID\", \"MAPFIELD\"['c'] AS \"MAPFIELD_C\"," // 构建SQL查询：选择ID字段、MAPFIELD中键'c'的值（别名为MAPFIELD_C）
        + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where \"NESTEDMAPFIELD\"['a']['b'] + 1.0 = 3 " // WHERE条件：测试加法运算，2 + 1.0 = 3
        + "AND \"NESTEDMAPFIELD\"['a']['b'] * 2.0 = 4 " // 测试乘法运算，2 * 2.0 = 4
        + "AND \"NESTEDMAPFIELD\"['a']['b'] > 1" // 测试大于比较，2 > 1
        + "AND \"NESTEDMAPFIELD\"['a']['b'] >= 2" // 测试大于等于比较，2 >= 2
        + "AND 100.1 <> \"ARRAYFIELD\"[2] - 100.0" // 测试不等于比较，100.1 <> 200 - 100.0 = 100.0
        + "AND 100.0 = \"ARRAYFIELD\"[2] / 2" // 测试除法运算，100.0 = 200 / 2
        + "AND 99.9 < \"ARRAYFIELD\"[2] / 2" // 测试小于比较，99.9 < 200 / 2 = 100.0
        + "AND 100.0 <= \"ARRAYFIELD\"[2] / 2" // 测试小于等于比较，100.0 <= 200 / 2 = 100.0
        + "AND '200' <> \"STRINGARRAYFIELD\"[1]" // 测试字符串不等于比较，'200' <> '100'
        + "AND '200' = \"STRINGARRAYFIELD\"[2]" // 测试字符串等于比较，'200' = '200'
        + "AND '100' < \"STRINGARRAYFIELD\"[2]"; // 测试字符串小于比较，'100' < '200'

    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(1)); // 验证查询返回1行结果

    // JDBC doesn't support Map / Nested Map so just relying on string representation
    String expectedRow = "ID=2; MAPFIELD_C=4; NESTEDMAPFIELD={a={b=2, c=4}}; " // 期望的结果行：ID为2，MAPFIELD中'c'键的值为4，嵌套Map结构为{a={b=2, c=4}}
        + "ARRAYFIELD=[100, 200, 300]"; // 数组值为[100, 200, 300]
    assertThat(resultStrings.get(0), is(expectedRow)); // 验证实际结果与期望结果一致
  }

  @Test void testAccessNonExistKeyFromMapWithAnyType() throws Exception { // 测试使用ANY类型时访问Map中不存在的键的行为
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    // this shouldn't throw any Exceptions on runtime, just don't return any rows.
    final String sql = "select \"ID\", \"MAPFIELD\", " // 构建SQL查询：选择ID字段和Map字段
        + "\"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where CAST(\"MAPFIELD\"['a'] AS INTEGER) = 2"; // WHERE条件：将MAPFIELD中键'a'的值转换为整数并与2比较（键'a'不存在）
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(0)); // 验证查询返回0行结果（因为键'a'不存在）
  }

  @Test void testAccessNonExistKeyFromNestedMapWithAnyType() throws Exception { // 测试使用ANY类型时访问嵌套Map中不存在的键的行为
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    // this shouldn't throw any Exceptions on runtime, just don't return any rows.
    final String sql = "select \"ID\", \"MAPFIELD\"," // 构建SQL查询：选择ID字段和Map字段
        + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where CAST(\"NESTEDMAPFIELD\"['b']['c'] AS INTEGER) = 4"; // WHERE条件：将嵌套Map中b.c的值转换为整数并与4比较（键'b'不存在）
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将 将结果集转换为字符串列表
    assertThat(resultStrings, hasSize(0)); // 验证查询返回0行结果（因为键'b'不存在）
  }

  @Test void testInvalidAccessUseStringForIndexOnArrayWithAnyType() throws Exception { // 测试使用ANY类型时用字符串作为数组索引的错误处理
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    try {
      final String sql = "select \"ID\", \"MAPFIELD\"," // 构建SQL查询：选择ID字段和Map字段
          + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
          + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
          + "where CAST(\"ARRAYFIELD\"['a'] AS INTEGER) = 200"; // WHERE条件：使用字符串'a'作为数组索引并转换为整数（这是无效的）
      statement.executeQuery(sql); // 执行SQL查询，预期会抛出异常

      fail("This query shouldn't be evaluated properly"); // 如果没有抛出异常，则测试失败
    } catch (SQLException e) { // 捕获SQL异常
      Throwable e2 = e.getCause(); // 获取异常的根本原因
      assertThat(e2, is(instanceOf(CalciteContextException.class))); // 验证根本原因是CalciteContextException类型
    }
  }

  @Test void testNestedArrayOutOfBoundAccessWithAnyType() throws Exception { // 测试使用ANY类型时数组越界访问的行为
    Connection connection = setupConnectionWithNestedTable(); // 建立包含嵌套表结构的数据库连接

    final Statement statement = connection.createStatement(); // 创建SQL语句执行对象

    final String sql = "select \"ID\", \"MAPFIELD\"," // 构建SQL查询：选择ID字段和Map字段
        + " \"NESTEDMAPFIELD\", \"ARRAYFIELD\" " // 以及嵌套Map字段和数组字段
        + "from \"s\".\"nested\" " // 从schema 's' 的表 'nested' 中查询
        + "where CAST(\"ARRAYFIELD\"[10] AS INTEGER) = 200"; // WHERE条件：访问数组索引10并转换为整数（超出数组边界）
    final ResultSet resultSet = statement.executeQuery(sql); // 执行SQL查询并获取结果集
    final List<String> resultStrings = CalciteAssert.toList(resultSet); // 将结果集转换为字符串列表

    // this is against SQL standard definition...
    // SQL standard states that data exception should be occurred
    // when accessing array with out of bound index.
    // but PostgreSQL breaks it, and this is more convenient since it guarantees runtime safety.
    assertThat(resultStrings, hasSize(0)); // 验证查询返回0行结果（越界访问不抛出异常，而是返回空结果）
  }

  private Connection setupConnectionWithNestedTable() throws SQLException { // 设置包含嵌套表结构的数据库连接
    Connection connection = // 创建Calcite数据库连接
        DriverManager.getConnection("jdbc:calcite:"); // 使用JDBC驱动管理器获取连接
    CalciteConnection calciteConnection = // 解包为Calcite连接以访问特定功能
        connection.unwrap(CalciteConnection.class); // 将标准JDBC连接转换为Calcite连接
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根Schema
    SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根Schema下添加名为's'的新Schema
    schema.add("nested", new NestedCollectionTable()); // 在Schema's'下添加名为'nested'的表，该表包含嵌套集合类型
    return connection; // 返回配置好的数据库连接
  }

  private Connection setupConnectionWithNestedAnyTypeTable() throws SQLException { // 设置包含ANY类型嵌套表结构的数据库连接
    Connection connection = // 创建Calcite数据库连接
        DriverManager.getConnection("jdbc:calcite:"); // 使用JDBC驱动管理器获取连接
    CalciteConnection calciteConnection = // 解包为Calcite连接以访问特定功能
        connection.unwrap(CalciteConnection.class); // 将标准JDBC连接转换为Calcite连接
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根Schema
    SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根Schema下添加名为's'的新Schema
    schema.add("nested", new NestedCollectionWithAnyTypeTable()); // 在Schema's'下添加名为'nested'的表，该表使用ANY类型的嵌套集合
    return connection; // 返回配置好的数据库连接
  }

  public static Enumerator<Object[]> nestedRecordsEnumerator() { // 创建用于遍历嵌套记录的枚举器
    final Object[][] records = setupNestedRecords(); // 设置嵌套记录数据

    return new Enumerator<Object[]>() { // 返回一个新的枚举器实例
      int row = -1; // 当前行索引，初始化为-1表示尚未开始
      int returnCount = 0; // 返回的记录计数
      Object @Nullable [] current; // 当前记录，可为null

      @Override public Object[] current() { // 获取当前记录
        return current; // 返回当前记录对象
      }

      @Override public boolean moveNext() { // 移动到下一条记录
        while (++row < 5) { // 递增行索引，检查是否小于5（总共有5条记录）
          this.current = records[row]; // 设置当前记录为records数组的第row行
          ++returnCount; // 递增返回计数
          return true; // 返回true表示成功移动到下一条记录
        }
        return false; // 返回false表示已遍历完所有记录
      }

      @Override public void reset() { // 重置枚举器到初始状态
        row = -1; // 重置行索引为-1
      }

      @Override public void close() { // 关闭枚举器并释放资源
        current = null; // 清空当前记录引用
      }
    };
  }

  private static Object[][] setupNestedRecords() { // 设置嵌套记录数据，包含Map、嵌套Map和数组
    List<Integer> ints = Arrays.asList(100, 200, 300); // 创建整数列表，用于数组字段
    List<String> strings = Arrays.asList("100", "200", "300"); // 创建字符串列表，用于字符串数组字段

    Object[][] records = new Object[5][]; // 创建5行记录的二维数组

    for (int i = 0; i < 5; ++i) { // 循环创建5条记录
      Map<String, Integer> map = new HashMap<>(); // 创建Map，键为String类型，值为Integer类型
      map.put("b", i); // 在Map中放入键"b"，值为i（当前索引）
      map.put("c", i * i); // 在Map中放入键"c"，值为i的平方
      Map<String, Map<String, Integer>> mm = new HashMap<>(); // 创建嵌套Map，键为String类型，值为Map类型
      mm.put("a", map); // 在嵌套Map中放入键"a"，值为之前创建的map
      records[i] = new Object[] {i, map, mm, ints, strings}; // 将记录数组赋值给records的第i行，包含：ID、Map、嵌套Map、整数数组、字符串数组
    }

    return records; // 返回创建的记录数组
  }

  /** Table that returns columns which include complicated collection type via the ScannableTable
   * interface. */
  // 实现ScannableTable接口的表，返回包含复杂集合类型的列
  // 该表包含以下列：
  // - ID: 整数类型，表示记录ID
  // - MAPFIELD: Map类型，键为VARCHAR，值为INTEGER
  // - NESTEDMAPFIELD: 嵌套Map类型，键为VARCHAR，值为Map（键为VARCHAR，值为INTEGER）
  // - ARRAYFIELD: 数组类型，元素为INTEGER
  // - STRINGARRAYFIELD: 数组类型，元素为VARCHAR
  public static class NestedCollectionTable implements ScannableTable { // 实现ScannableTable接口，表示可扫描的表
    public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 获取表的行类型结构

      RelDataType nullableVarcharType = typeFactory // 创建可空的VARCHAR类型
          .createTypeWithNullability(typeFactory.createSqlType(SqlTypeName.VARCHAR), true); // 创建VARCHAR类型并设置为可空
      RelDataType nullableIntegerType = typeFactory // 创建可空的INTEGER类型
          .createTypeWithNullability(typeFactory.createSqlType(SqlTypeName.INTEGER), true); // 创建INTEGER类型并设置为可空
      RelDataType nullableMapType = typeFactory // 创建可空的Map类型
          .createTypeWithNullability( // 创建Map类型并设置为可空
              typeFactory.createMapType(nullableVarcharType, nullableIntegerType), // Map的键为VARCHAR，值为INTEGER
              true); // 设置为可空
      return typeFactory.builder() // 使用类型工厂构建器构建行类型
          .add("ID", SqlTypeName.INTEGER) // 添加ID列，类型为INTEGER
          .add("MAPFIELD", // 添加MAPFIELD列
              typeFactory.createTypeWithNullability( // 创建可空类型
                typeFactory.createMapType(nullableVarcharType, nullableIntegerType), true)) // Map类型，键为VARCHAR，值为INTEGER
          .add("NESTEDMAPFIELD", typeFactory // 添加NESTEDMAPFIELD列
              .createTypeWithNullability( // 创建可空类型
                  typeFactory.createMapType(nullableVarcharType, nullableMapType), true)) // 嵌套Map类型，键为VARCHAR，值为Map
          .add("ARRAYFIELD", typeFactory // 添加ARRAYFIELD列
              .createTypeWithNullability( // 创建可空类型
                  typeFactory.createArrayType(nullableIntegerType, -1L), true)) // 数组类型，元素为INTEGER，-1L表示未指定最大长度
          .add("STRINGARRAYFIELD", typeFactory // 添加STRINGARRAYFIELD列
              .createTypeWithNullability( // 创建可空类型
                  typeFactory.createArrayType(nullableVarcharType, -1L), true)) // 数组类型，元素为VARCHAR，-1L表示未指定最大长度
          .build(); // 构建并返回行类型
    }


    public Statistic getStatistic() { // 获取表的统计信息
      return Statistics.UNKNOWN; // 返回未知统计信息
    }

    public Schema.TableType getJdbcTableType() { // 获取JDBC表类型
      return Schema.TableType.TABLE; // 返回表类型为TABLE
    }

    public Enumerable<@Nullable Object[]> scan(DataContext root) { // 扫描表并返回可枚举的结果
      return new AbstractEnumerable<Object[]>() { // 创建抽象可枚举对象
        public Enumerator<Object[]> enumerator() { // 创建枚举器
          return nestedRecordsEnumerator(); // 返回嵌套记录的枚举器
        }
      };
    }

    @Override public boolean isRolledUp(String column) { // 检查指定列是否为上卷列
      return false; // 返回false，表示没有上卷列
    }

    @Override public boolean rolledUpColumnValidInsideAgg( // 检查上卷列在聚合函数中是否有效
        String column, SqlCall call, @Nullable SqlNode parent, // 列名、SQL调用、父节点、连接配置
        @Nullable CalciteConnectionConfig config) { // 参数说明：column为列名，call为SQL调用，parent为父SQL节点，config为连接配置
      return false; // 返回false，表示上卷列在聚合中无效
    }
  }

  /** Table that returns columns which include complicated collection type via the ScannableTable
   * interface. */
  // 实现ScannableTable接口的表，返回包含复杂集合类型的列，但使用ANY类型
  // 与NestedCollectionTable的区别在于，该表的所有集合字段都使用ANY类型
  // ANY类型允许在运行时进行类型转换，提供了更大的灵活性
  // 该表包含以下列：
  // - ID: 整数类型
  // - MAPFIELD: ANY类型（实际存储Map<String, Integer>）
  // - NESTEDMAPFIELD: ANY类型（实际存储嵌套Map）
  // - ARRAYFIELD: ANY类型（实际存储整数数组）
  // - STRINGARRAYFIELD: ANY类型（实际存储字符串数组）
  public static class NestedCollectionWithAnyTypeTable implements ScannableTable { // 实现ScannableTable接口，使用ANY类型的可扫描表
    public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 获取表的行类型结构
      return typeFactory.builder() // 使用类型工厂构建器构建行类型
          .add("ID", SqlTypeName.INTEGER) // 添加ID列，类型为INTEGER
          .add("MAPFIELD", SqlTypeName.ANY) // 添加MAPFIELD列，类型为ANY（可以存储任意类型的Map）
          .add("NESTEDMAPFIELD", SqlTypeName.ANY) // 添加NESTEDMAPFIELD列，类型为ANY（可以存储任意类型的嵌套Map）
          .add("ARRAYFIELD", SqlTypeName.ANY) // 添加ARRAYFIELD列，类型为ANY（可以存储任意类型的数组）
          .add("STRINGARRAYFIELD", SqlTypeName.ANY) // 添加STRINGARRAYFIELD列，类型为ANY（可以存储任意类型的字符串数组）
          .build(); // 构建并返回行类型
    }

    public Statistic getStatistic() { // 获取表的统计信息
      return Statistics.UNKNOWN; // 返回未知统计信息
    }

    public Schema.TableType getJdbcTableType() { // 获取JDBC表类型
      return Schema.TableType.TABLE; // 返回表类型为TABLE
    }

    public Enumerable<@Nullable Object[]> scan(DataContext root) { // 扫描表并返回可枚举的结果
      return new AbstractEnumerable<Object[]>() { // 创建抽象可枚举对象
        public Enumerator<Object[]> enumerator() { // 创建枚举器
          return nestedRecordsEnumerator(); // 返回嵌套记录的枚举器（与NestedCollectionTable共享相同的数据源）
        }
      };
    }

    @Override public boolean isRolledUp(String column) { // 检查指定列是否为上卷列
      return false; // 返回false，表示没有上卷列
    }

    @Override public boolean rolledUpColumnValidInsideAgg(String column, // 检查上卷列在聚合函数中是否有效
        SqlCall call, @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 参数说明：column为列名，call为SQL调用，parent为父SQL节点，config为连接配置
      return false; // 返回false，表示上卷列在聚合中无效
    }
  }
}
