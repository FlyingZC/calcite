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
// Apache许可证声明，说明代码的授权和使用条件
package org.apache.calcite.test; // 声明包名，这个类位于org.apache.calcite.test包下

import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema类，用于将Java对象转换为Calcite Schema
import org.apache.calcite.jdbc.CalciteConnection; // 导入CalciteConnection接口，Calcite的JDBC连接接口
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示可扩展的Schema
import org.apache.calcite.schema.TableFunction; // 导入TableFunction接口，表示表函数
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema类，抽象Schema基类
import org.apache.calcite.sql.advise.SqlAdvisorGetHintsFunction; // 导入SqlAdvisorGetHintsFunction类，SQL建议提示函数（API版本1）
import org.apache.calcite.sql.advise.SqlAdvisorGetHintsFunction2; // 导入SqlAdvisorGetHintsFunction2类，SQL建议提示函数（API版本2）
import org.apache.calcite.sql.parser.StringAndPos; // 导入StringAndPos类，表示带有光标位置的字符串
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HrSchema类，人力资源测试Schema

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.sql.Connection; // 导入JDBC Connection接口
import java.sql.DriverManager; // 导入DriverManager类，用于管理JDBC驱动
import java.sql.PreparedStatement; // 导入PreparedStatement接口，预编译SQL语句
import java.sql.ResultSet; // 导入ResultSet接口，表示查询结果集
import java.sql.SQLException; // 导入SQLException类，SQL异常
import java.util.Properties; // 导入Properties类，用于存储配置属性
import java.util.function.Consumer; // 导入Consumer接口，用于消费结果集

/**
 * Tests for {@link org.apache.calcite.sql.advise.SqlAdvisor}.
 */
// 这是一个测试类，用于测试SqlAdvisor（SQL建议器）的功能
// SqlAdvisor是Calcite提供的一个功能，用于在用户输入SQL时提供智能提示和建议
// 该测试类通过JDBC方式测试SQL建议功能，支持两种不同的API版本
// 测试内容包括：列名提示、表名提示、Schema提示等场景
class SqlAdvisorJdbcTest { // 类名：SqlAdvisorJdbcTest，通过JDBC测试SQL建议功能的测试类

  private void adviseSql(int apiVersion, String sql, Consumer<ResultSet> checker) // 私有辅助方法：执行SQL建议测试
      throws SQLException { // 声明可能抛出SQL异常
    Properties info = new Properties(); // 创建Properties对象用于存储连接配置信息
    if (apiVersion == 1) { // 如果是API版本1
      info.put("lex", "JAVA"); // 设置词法分析器为JAVA模式（使用双引号作为标识符引用符）
      info.put("quoting", "DOUBLE_QUOTE"); // 设置引用符为双引号
    } else if (apiVersion == 2) { // 如果是API版本2
      info.put("lex", "SQL_SERVER"); // 设置词法分析器为SQL_SERVER模式（使用方括号作为标识符引用符）
      info.put("quoting", "BRACKET"); // 设置引用符为方括号
    }
    Connection connection = // 创建JDBC连接
        DriverManager.getConnection("jdbc:calcite:", info); // 通过DriverManager获取Calcite连接，传入连接URL和配置信息
    CalciteConnection calciteConnection = // 将普通连接转换为CalciteConnection
        connection.unwrap(CalciteConnection.class); // 使用unwrap方法获取CalciteConnection接口
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // 获取根Schema，这是所有Schema的父节点
    rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 在根Schema下添加名为"hr"的Schema，使用反射方式创建HrSchema
    SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 在根Schema下添加名为"s"的空Schema，用于注册get_hints函数
    calciteConnection.setSchema("hr"); // 设置当前Schema为"hr"，这样默认查询都在hr Schema下执行
    final TableFunction getHints = // 创建表函数实例，用于获取SQL建议
        apiVersion == 1 ? new SqlAdvisorGetHintsFunction() : new SqlAdvisorGetHintsFunction2(); // 根据API版本选择不同的实现类
    schema.add("get_hints", getHints); // 将get_hints函数注册到"s" Schema下，这样可以通过"s"."get_hints"调用
    String getHintsSql; // 声明SQL字符串变量，用于存储调用get_hints函数的SQL语句
    if (apiVersion == 1) { // 如果是API版本1
      getHintsSql = "select id, names, type from table(\"s\".\"get_hints\"(?, ?)) as t"; // 构造SQL：调用get_hints函数，返回id、names、type三列
    } else { // 如果是API版本2
      getHintsSql = "select id, names, type, replacement from table([s].[get_hints](?, ?)) as t"; // 构造SQL：调用get_hints函数，返回id、names、type、replacement四列（增加了replacement列）
    }

    PreparedStatement ps = connection.prepareStatement(getHintsSql); // 创建预编译语句，准备执行get_hints查询
    StringAndPos sap = StringAndPos.of(sql); // 将SQL字符串解析为StringAndPos对象，提取SQL文本和光标位置（^符号所在位置）
    ps.setString(1, sap.sql); // 设置第一个参数：要分析的SQL语句（去掉光标标记后的纯SQL）
    ps.setInt(2, sap.cursor); // 设置第二个参数：光标位置（从0开始的索引）
    final ResultSet resultSet = ps.executeQuery(); // 执行查询，获取SQL建议的结果集
    checker.accept(resultSet); // 使用传入的checker函数验证结果集（检查返回的建议是否符合预期）
    resultSet.close(); // 关闭结果集，释放资源
    connection.close(); // 关闭数据库连接，释放资源
  } // 方法结束

  @Test void testSqlAdvisorGetHintsFunction() throws SQLException { // 测试方法：测试API版本1的SQL建议功能
    adviseSql(1, "select e.e^ from \"emps\" e", // 调用adviseSql方法，传入API版本1和测试SQL（^表示光标位置，在"e.e"后面）
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=e; names=null; type=MATCH", // 预期结果1：id为"e"，names为null，type为MATCH（表示匹配到表别名e）
            "id=empid; names=[empid]; type=COLUMN")); // 预期结果2：id为"empid"，names为["empid"]，type为COLUMN（建议补全为empid列）
  } // 测试方法结束

  @Test void testSqlAdvisorGetHintsFunction2() throws SQLException { // 测试方法：测试API版本2的SQL建议功能
    adviseSql(2, "select [e].e^ from [emps] e", // 调用adviseSql方法，传入API版本2和测试SQL（使用方括号引用标识符）
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=e; names=null; type=MATCH; replacement=null", // 预期结果1：id为"e"，names为null，type为MATCH，replacement为null（匹配到表别名e，无替换建议）
            "id=empid; names=[empid]; type=COLUMN; replacement=empid")); // 预期结果2：id为"empid"，names为["empid"]，type为COLUMN，replacement为"empid"（建议补全为empid列，并提供替换文本）
  } // 测试方法结束

  @Test void testSqlAdvisorNonExistingColumn() throws SQLException { // 测试方法：测试在错误的列名后获取建议的场景
    adviseSql(1, "select e.empdid_wrong_name.^ from \"hr\".\"emps\" e", // 调用adviseSql方法，测试SQL中列名"empdid_wrong_name"是错误的（应该是empid），光标在错误列名后面
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=*; names=[*]; type=KEYWORD", // 预期结果1：建议使用通配符*，表示选择所有列
            "id=; names=null; type=MATCH")); // 预期结果2：空匹配，表示当前位置可以继续输入
  } // 测试方法结束

  @Test void testSqlAdvisorNonStructColumn() throws SQLException { // 测试方法：测试在非结构化列后获取建议的场景
    adviseSql(1, "select e.\"empid\".^ from \"hr\".\"emps\" e", // 调用adviseSql方法，测试SQL中"empid"是一个普通列（不是结构化类型），光标在列名后面
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=*; names=[*]; type=KEYWORD", // 预期结果1：建议使用通配符*，表示选择所有列
            "id=; names=null; type=MATCH")); // 预期结果2：空匹配，表示当前位置可以继续输入
  } // 测试方法结束

  @Test void testSqlAdvisorSubSchema() throws SQLException { // 测试方法：测试在子Schema后获取表建议的场景（API版本1）
    adviseSql(1, "select * from \"hr\".^.test_test_test", // 调用adviseSql方法，测试SQL中光标在"hr."后面，期望提示hr Schema下的表
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=; names=null; type=MATCH", // 预期结果1：空匹配，表示当前位置可以继续输入
            "id=hr.dependents; names=[hr, dependents]; type=TABLE", // 预期结果2：建议hr.dependents表
            "id=hr.depts; names=[hr, depts]; type=TABLE", // 预期结果3：建议hr.depts表
            "id=hr.emps; names=[hr, emps]; type=TABLE", // 预期结果4：建议hr.emps表
            "id=hr.locations; names=[hr, locations]; type=TABLE", // 预期结果5：建议hr.locations表
            "id=hr; names=[hr]; type=SCHEMA")); // 预期结果6：建议hr Schema本身
  } // 测试方法结束

  @Test void testSqlAdvisorSubSchema2() throws SQLException { // 测试方法：测试在子Schema后获取表建议的场景（API版本2）
    adviseSql(2, "select * from [hr].^.test_test_test", // 调用adviseSql方法，测试SQL中光标在"hr."后面，使用方括号引用标识符
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=; names=null; type=MATCH; replacement=null", // 预期结果1：空匹配，无替换建议
            "id=hr.dependents; names=[hr, dependents]; type=TABLE; replacement=dependents", // 预期结果2：建议hr.dependents表，替换文本为"dependents"
            "id=hr.depts; names=[hr, depts]; type=TABLE; replacement=depts", // 预期结果3：建议hr.depts表，替换文本为"depts"
            "id=hr.emps; names=[hr, emps]; type=TABLE; replacement=emps", // 预期结果4：建议hr.emps表，替换文本为"emps"
            "id=hr.locations; names=[hr, locations]; type=TABLE; replacement=locations", // 预期结果5：建议hr.locations表，替换文本为"locations"
            "id=hr; names=[hr]; type=SCHEMA; replacement=hr")); // 预期结果6：建议hr Schema，替换文本为"hr"
  } // 测试方法结束

  @Test void testSqlAdvisorTableInSchema() throws SQLException { // 测试方法：测试在Schema后获取表建议的场景
    adviseSql(1, "select * from \"hr\".^", // 调用adviseSql方法，测试SQL中光标在"hr\""后面，期望提示hr Schema下的表
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=; names=null; type=MATCH", // 预期结果1：空匹配，表示当前位置可以继续输入
            "id=hr.dependents; names=[hr, dependents]; type=TABLE", // 预期结果2：建议hr.dependents表
            "id=hr.depts; names=[hr, depts]; type=TABLE", // 预期结果3：建议hr.depts表
            "id=hr.emps; names=[hr, emps]; type=TABLE", // 预期结果4：建议hr.emps表
            "id=hr.locations; names=[hr, locations]; type=TABLE", // 预期结果5：建议hr.locations表
            "id=hr; names=[hr]; type=SCHEMA")); // 预期结果6：建议hr Schema本身
  } // 测试方法结束

  /**
   * Tests {@link org.apache.calcite.sql.advise.SqlAdvisorGetHintsFunction}.
   */
  // 测试SqlAdvisorGetHintsFunction的功能，在FROM子句中获取Schema和表的提示
  @Test void testSqlAdvisorSchemaNames() throws SQLException { // 测试方法：测试在FROM子句中获取Schema和表建议的场景
    adviseSql(1, "select empid from \"emps\" e, ^", // 调用adviseSql方法，测试SQL中光标在FROM子句的逗号后面，期望提示可以连接的Schema和表
        CalciteAssert.checkResultUnordered( // 使用CalciteAssert验证结果集（不关心顺序）
            "id=; names=null; type=MATCH", // 预期结果1：空匹配，表示当前位置可以继续输入
            "id=(; names=[(]; type=KEYWORD", // 预期结果2：建议左括号关键字，用于子查询
            "id=LATERAL; names=[LATERAL]; type=KEYWORD", // 预期结果3：建议LATERAL关键字，用于横向连接
            "id=TABLE; names=[TABLE]; type=KEYWORD", // 预期结果4：建议TABLE关键字，用于表表达式
            "id=UNNEST; names=[UNNEST]; type=KEYWORD", // 预期结果5：建议UNNEST关键字，用于展开数组
            "id=hr; names=[hr]; type=SCHEMA", // 预期结果6：建议hr Schema
            "id=metadata; names=[metadata]; type=SCHEMA", // 预期结果7：建议metadata Schema（系统元数据Schema）
            "id=s; names=[s]; type=SCHEMA", // 预期结果8：建议s Schema（包含get_hints函数的Schema）
            "id=hr.dependents; names=[hr, dependents]; type=TABLE", // 预期结果9：建议hr.dependents表
            "id=hr.depts; names=[hr, depts]; type=TABLE", // 预期结果10：建议hr.depts表
            "id=hr.emps; names=[hr, emps]; type=TABLE", // 预期结果11：建议hr.emps表
            "id=hr.locations; names=[hr, locations]; type=TABLE")); // 预期结果12：建议hr.locations表
  } // 测试方法结束

} // 类结束，SqlAdvisorJdbcTest类的定义到此结束
