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
// Apache许可证声明：本软件遵循Apache 2.0许可证，允许在遵守许可证条款的前提下使用和分发
package org.apache.calcite.test; // 定义包名，该类位于org.apache.calcite.test测试包中

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表基类，用于实现自定义表的可查询功能
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接接口，提供对Calcite特定功能的访问
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历查询结果
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于执行LINQ查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可被查询的数据源
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeFieldImpl; // 导入关系数据类型字段实现类，表示表中的列
import org.apache.calcite.rel.type.RelRecordType; // 导入关系记录类型，表示结构化数据类型（如行类型）
import org.apache.calcite.rel.type.StructKind; // 导入结构类型枚举，定义记录类型的语义
import org.apache.calcite.runtime.PairList; // 导入键值对列表，用于存储字段名和类型的映射
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示可扩展的模式
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类，用于实现表的可查询功能
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL数据类型
import org.apache.calcite.util.Smalls; // 导入测试工具类，提供小型测试表和辅助方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表，用于存储不可变的字段列表
import com.google.common.collect.ImmutableMultiset; // 导入Google Guava的不可变多重集合，用于验证查询结果

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法

import java.sql.Connection; // 导入JDBC连接接口，表示数据库连接
import java.sql.DriverManager; // 导入JDBC驱动管理器，用于获取数据库连接
import java.sql.ResultSet; // 导入JDBC结果集接口，表示查询结果
import java.sql.ResultSetMetaData; // 导入JDBC结果集元数据接口，提供结果集的结构信息
import java.sql.Statement; // 导入JDBC语句接口，用于执行SQL语句

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest匹配器，用于断言值相等
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest匹配器，用于断言值为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言方法，用于验证测试结果

/** Test case for issue 85. */ // 类级别文档注释：这是针对issue 85的测试用例
// 类说明：TableInRootSchemaTest是一个测试类，用于测试在Calcite根模式（root schema）中添加表的功能
// 主要测试场景：
// 1. 测试在根模式中动态添加表是否会导致CalcitePrepareImpl出现问题
// 2. 测试可空ROW类型字段访问的可空性处理
// 3. 验证元数据（如表名、模式名、列名等）的正确性
class TableInRootSchemaTest { // 测试类定义，使用JUnit 5进行测试
  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-85">[CALCITE-85]
   * Adding a table to the root schema causes breakage in
   * CalcitePrepareImpl</a>. */
  // 方法说明：testAddingTableInRootSchema方法用于测试CALCITE-85问题
  // 该问题涉及在根模式中添加表时会导致CalcitePrepareImpl出现故障
  // 测试步骤：
  // 1. 创建Calcite连接
  // 2. 获取根模式并添加一个测试表SAMPLE
  // 3. 执行SQL查询（带GROUP BY的聚合查询）
  // 4. 验证查询结果的正确性
  // 5. 验证结果集元数据的正确性（列名、表名、模式名、列类型等）
  @Test void testAddingTableInRootSchema() throws Exception { // 测试方法声明，使用@Test注解标记，可能抛出异常
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 通过DriverManager获取Calcite数据库连接，使用默认配置
    CalciteConnection calciteConnection = // 将普通JDBC连接解包为CalciteConnection，以访问Calcite特定功能
        connection.unwrap(CalciteConnection.class);

    calciteConnection.getRootSchema().add("SAMPLE", new Smalls.SimpleTable()); // 获取根模式并添加一个名为"SAMPLE"的表，使用Smalls.SimpleTable作为表实现
    Statement statement = calciteConnection.createStatement(); // 创建SQL语句对象，用于执行SQL查询
    ResultSet resultSet = // 执行SQL查询：从SAMPLE表中选择A列和SUM(B)聚合值，按A列分组
        statement.executeQuery("select A, SUM(B) from SAMPLE group by A");

    assertThat( // 使用Hamcrest断言验证查询结果是否与预期匹配
        ImmutableMultiset.of( // 创建不可变多重集合，包含预期的查询结果行
            "A=foo; EXPR$1=8", // 预期结果行1：A列值为'foo'，SUM(B)聚合值为8（EXPR$1是自动生成的列名）
            "A=bar; EXPR$1=4"), // 预期结果行2：A列值为'bar'，SUM(B)聚合值为4
        equalTo(CalciteAssert.toSet(resultSet))); // 将实际结果集转换为多重集合并与预期值比较

    final ResultSetMetaData resultSetMetaData = resultSet.getMetaData(); // 获取结果集的元数据，包含列信息
    assertThat(resultSetMetaData.getColumnName(1), equalTo("A")); // 验证第1列的列名是否为"A"
    assertThat(resultSetMetaData.getTableName(1), equalTo("SAMPLE")); // 验证第1列所属的表名是否为"SAMPLE"
    assertThat(resultSetMetaData.getSchemaName(1), nullValue()); // 验证第1列所属的模式名是否为null（因为表在根模式中）
    assertThat(resultSetMetaData.getColumnClassName(1), // 验证第1列的Java类型类名是否为String类型
        equalTo("java.lang.String"));
    // Per JDBC, column name should be null. But DBUnit requires every column
    // to have a name, so the driver uses the label.
    // 根据JDBC规范，聚合列的列名应该为null，但DBUnit要求每列都有名称，所以驱动使用标签作为列名
    assertThat(resultSetMetaData.getColumnName(2), equalTo("EXPR$1")); // 验证第2列（SUM(B)聚合列）的列名是否为"EXPR$1"
    assertThat(resultSetMetaData.getTableName(2), nullValue()); // 验证第2列所属的表名是否为null（因为它是计算列）
    assertThat(resultSetMetaData.getSchemaName(2), nullValue()); // 验证第2列所属的模式名是否为null
    assertThat(resultSetMetaData.getColumnClassName(2), // 验证第2列的Java类型类名是否为Integer类型
        equalTo("java.lang.Integer"));
    resultSet.close(); // 关闭结果集，释放资源
    statement.close(); // 关闭语句对象，释放资源
    connection.close(); // 关闭数据库连接，释放资源
  } // 测试方法结束

  /** Represents a table with no data. An abstract base class,
   * derived classes need to define the schema. */
  // 类说明：EmptyTable是一个抽象内部类，表示一个没有数据的表
  // 继承自AbstractQueryableTable，实现了可查询表的功能
  // 派生类需要定义表的schema（行类型），但数据为空
  private abstract static class EmptyTable extends AbstractQueryableTable { // 私有抽象静态内部类，继承AbstractQueryableTable
    protected EmptyTable() { // 构造方法：调用父类构造函数，指定元素类型为Object[]数组
      super(Object[].class); // 传递Object[].class给父类，表示表中每行是一个对象数组
    }

    @Override public <T> Queryable<T> asQueryable( // 重写asQueryable方法，将表转换为可查询对象
        QueryProvider queryProvider, SchemaPlus schema, String tableName) { // 参数：查询提供者、模式、表名
      return new AbstractTableQueryable<T>(queryProvider, schema, this, // 创建抽象表可查询对象
          tableName) { // 匿名内部类实现AbstractTableQueryable
        @Override public Enumerator<T> enumerator() { // 重写enumerator方法，返回枚举器用于遍历数据
          return new Enumerator<T>() { // 创建枚举器匿名内部类
            @Override public T current() { // 重写current方法，返回当前元素
              return null; // 返回null，因为表是空的
            }

            @Override public boolean moveNext() { // 重写moveNext方法，移动到下一个元素
              // Table is empty
              // 表是空的，所以总是返回false表示没有更多数据
              return false; // 返回false，表示没有下一个元素
            }

            @Override public void reset() {} // 重写reset方法，重置枚举器（空实现）

            @Override public void close() {} // 重写close方法，关闭枚举器（空实现）
          }; // 匿名枚举器类结束
        } // enumerator方法结束
      }; // 匿名AbstractTableQueryable类结束
    } // asQueryable方法结束
  } // EmptyTable类结束

  /** Helper class for the test for [CALCITE-6764] below. */
  // 类说明：TableWithNullableRowInMap是一个辅助测试类，用于测试CALCITE-6764问题
  // 该问题涉及MAP中包含可空ROW类型时字段访问的可空性
  // 该表包含一个列P，其类型为MAP<VARCHAR, ROW(VARCHAR)>，其中ROW类型是可空的
  // 对应SQL声明：CREATE TABLE T(P MAP<VARCHAR, ROW(K VARCHAR NON NULL, S VARCHAR NULL)>);
  private static class TableWithNullableRowInMap extends EmptyTable { // 私有静态内部类，继承EmptyTable
    protected TableWithNullableRowInMap() { // 构造方法：调用父类构造函数
      super(); // 调用EmptyTable的无参构造函数
    }

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型
      final PairList<String, RelDataType> columnDesc = PairList.withCapacity(1); // 创建字段描述列表，容量为1
      // Schema contains a column whose type is MAP<VARCHAR, ROW(VARCHAR)>, but
      // the ROW type can be nullable.  This can conceivably be created by a
      // declaration such as
      // CREATE TABLE T(P MAP<VARCHAR, ROW(K VARCHAR NON NULL, S VARCHAR NULL)>);
      // Schema包含一个列，其类型为MAP<VARCHAR, ROW(VARCHAR)>，但ROW类型可以是可空的
      // 这可以通过如下声明创建：CREATE TABLE T(P MAP<VARCHAR, ROW(K VARCHAR NON NULL, S VARCHAR NULL)>);
      final RelDataType colType = // 定义列类型
          typeFactory.createMapType(typeFactory.createSqlType(SqlTypeName.VARCHAR), // 创建MAP类型，键为VARCHAR类型
            new RelRecordType( // 创建ROW类型作为MAP的值类型
                StructKind.PEEK_FIELDS, // 结构类型为PEEK_FIELDS，允许通过字段名访问
                  ImmutableList.of( // 字段列表
                      new RelDataTypeFieldImpl("K", 0, // 创建字段K，索引为0，类型为VARCHAR且不可为空
                          typeFactory.createSqlType(SqlTypeName.VARCHAR)),
                      new RelDataTypeFieldImpl("S", 1, // 创建字段S，索引为1，类型为VARCHAR且可为空
                          typeFactory.createTypeWithNullability( // 创建可空类型
                              typeFactory.createSqlType(SqlTypeName.VARCHAR), true))), // VARCHAR类型，允许为空
                true)); // ROW类型本身是可空的
      columnDesc.add("P", colType); // 将列P及其类型添加到字段描述列表
      return typeFactory.createStructType(columnDesc); // 创建结构化类型（行类型）并返回
    } // getRowType方法结束
  } // TableWithNullableRowInMap类结束

  /** Helper class for the test for [CALCITE-6764] below. */
  // 类说明：TableWithNullableRowToplevel是一个辅助测试类，用于测试CALCITE-6764问题
  // 该问题涉及顶层可空ROW类型字段访问的可空性
  // 该表包含两个列P和Q，都是可空的ROW类型，其中Q包含嵌套的ROW类型
  // 对应SQL声明：CREATE TABLE T(P ROW(K VARCHAR NOT NULL) NULL, Q ROW(S ROW(L VARCHAR NOT NULL, M VARCHAR NULL) NULL) NULL);
  private static class TableWithNullableRowToplevel extends EmptyTable { // 私有静态内部类，继承EmptyTable
    protected TableWithNullableRowToplevel() { // 构造方法：调用父类构造函数
      super(); // 调用EmptyTable的无参构造函数
    }

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型
      final PairList<String, RelDataType> columnDesc = PairList.withCapacity(1); // 创建字段描述列表，容量为1
      // This table can conceivably be created by a declaration such as
      // CREATE TABLE T(P ROW(K VARCHAR NOT NULL) NULL,
      //                Q ROW(S ROW(L VARCHAR NOT NULL, M VARCHAR NULL) NULL) NULL);
      // 该表可以通过如下声明创建：CREATE TABLE T(P ROW(K VARCHAR NOT NULL) NULL, Q ROW(S ROW(L VARCHAR NOT NULL, M VARCHAR NULL) NULL) NULL);
      final RelDataType pColType = // 定义列P的类型
          new RelRecordType( // 创建ROW类型
              StructKind.PEEK_FIELDS, ImmutableList.of( // 结构类型为PEEK_FIELDS，字段列表
                  new RelDataTypeFieldImpl( // 创建字段K
                      "K", 0, typeFactory.createSqlType(SqlTypeName.VARCHAR))), // 字段名K，索引0，VARCHAR类型
          true); // ROW类型本身是可空的
      final RelDataType sType = // 定义嵌套的ROW类型S（作为列Q的字段）
          new RelRecordType( // 创建ROW类型
              StructKind.PEEK_FIELDS, ImmutableList.of( // 结构类型为PEEK_FIELDS，字段列表
              new RelDataTypeFieldImpl( // 创建字段L
                  "L", 0, typeFactory.createSqlType(SqlTypeName.VARCHAR)), // 字段名L，索引0，VARCHAR类型
              new RelDataTypeFieldImpl( // 创建字段M
                  "M", 1, typeFactory.createSqlType(SqlTypeName.VARCHAR))), // 字段名M，索引1，VARCHAR类型
              false); // ROW类型本身是不可空的
      final RelDataType qColType = // 定义列Q的类型
          new RelRecordType( // 创建ROW类型
              StructKind.PEEK_FIELDS, ImmutableList.of( // 结构类型为PEEK_FIELDS，字段列表
              new RelDataTypeFieldImpl("S", 0, sType)), // 创建字段S，索引0，类型为上面定义的sType
              true); // ROW类型本身是可空的
      columnDesc.add("P", pColType); // 将列P及其类型添加到字段描述列表
      columnDesc.add("Q", qColType); // 将列Q及其类型添加到字段描述列表
      return typeFactory.createStructType(columnDesc); // 创建结构化类型（行类型）并返回
    } // getRowType方法结束
  } // TableWithNullableRowToplevel类结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6764">[CALCITE-6764]
   * Field access from a nullable ROW should be nullable</a>. */
  // 方法说明：testNullableRowInMap方法用于测试CALCITE-6764问题
  // 该问题涉及从可空ROW类型访问字段时的可空性处理
  // 测试场景：访问MAP中可空ROW类型的字段
  // 期望行为：从可空ROW访问的字段也应该是可空的
  // 修复前的问题：验证器会抛出AssertionFailure，因为类型转换失败
  @Test void testNullableRowInMap() throws Exception { // 测试方法声明，可能抛出异常
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建Calcite数据库连接
    CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class); // 解包为CalciteConnection
    calciteConnection.getRootSchema().add("T", new TableWithNullableRowInMap()); // 在根模式中添加表T，使用TableWithNullableRowInMap作为表实现
    Statement statement = calciteConnection.createStatement(); // 创建SQL语句对象
    // Without the fix to this issue the Validator crashes with an AssertionFailure:
    // java.lang.RuntimeException: java.lang.AssertionError:
    // Conversion to relational algebra failed to preserve datatypes:
    // validated type:
    // 在修复此问题之前，验证器会崩溃并抛出AssertionFailure异常
    // 错误信息：Conversion to relational algebra failed to preserve datatypes
    // 原因：从可空ROW访问字段时，类型系统未能正确保持可空性
    ResultSet resultSet = statement.executeQuery("SELECT P['a'].K, P['a'].S FROM T"); // 执行SQL查询：从MAP列P中访问键'a'对应的ROW的K和S字段
    resultSet.close(); // 关闭结果集
    statement.close(); // 关闭语句对象
    connection.close(); // 关闭数据库连接
  } // 测试方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6764">[CALCITE-6764]
   * Field access from a nullable ROW should be nullable</a>. */
  // 方法说明：testNullableRowTopLevel方法用于测试CALCITE-6764问题
  // 该问题涉及从顶层可空ROW类型访问字段时的可空性处理
  // 测试场景：访问顶层可空ROW类型的字段，包括嵌套ROW类型的字段
  // 期望行为：从可空ROW访问的字段也应该是可空的
  // 修复前的问题：验证器会抛出AssertionFailure，因为类型转换失败
  @Test void testNullableRowTopLevel() throws Exception { // 测试方法声明，可能抛出异常
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // 创建Calcite数据库连接
    CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class); // 解包为CalciteConnection
    calciteConnection.getRootSchema().add("T", new TableWithNullableRowToplevel()); // 在根模式中添加表T，使用TableWithNullableRowToplevel作为表实现
    Statement statement = calciteConnection.createStatement(); // 创建SQL语句对象
    // Without the fix to this issue the Validator crashes with an AssertionFailure:
    // java.lang.RuntimeException: java.lang.AssertionError:
    // Conversion to relational algebra failed to preserve datatypes:
    // validated type:
    // 在修复此问题之前，验证器会崩溃并抛出AssertionFailure异常
    // 错误信息：Conversion to relational algebra failed to preserve datatypes
    // 原因：从可空ROW访问字段时，类型系统未能正确保持可空性
    ResultSet resultSet = statement.executeQuery("SELECT T.P.K, T.Q.S, T.Q.S.L, T.Q.S.M FROM T"); // 执行SQL查询：访问顶层ROW列P的字段K，以及嵌套ROW列Q的字段S及其子字段L和M
    resultSet.close(); // 关闭结果集
    statement.close(); // 关闭语句对象
    connection.close(); // 关闭数据库连接
  } // 测试方法结束
} // 测试类结束
