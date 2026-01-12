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
package org.apache.calcite.test; // 包声明：该测试类属于org.apache.calcite.test包，用于测试Calcite框架中的用户定义函数功能

import org.apache.calcite.adapter.enumerable.CallImplementor; // 导入CallImplementor接口，用于实现可调用的函数
import org.apache.calcite.adapter.java.ReflectiveSchema; // 导入ReflectiveSchema类，用于通过反射创建基于Java对象的schema
import org.apache.calcite.jdbc.CalciteConnection; // 导入CalciteConnection接口，表示Calcite数据库连接
import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合元素添加索引
import org.apache.calcite.linq4j.function.SemiStrict; // 导入SemiStrict注解，用于标记半严格函数
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions类，用于构建LINQ表达式树
import org.apache.calcite.linq4j.tree.Types; // 导入Types类，用于类型相关的工具方法
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，表示可原型化的关系数据类型
import org.apache.calcite.schema.FunctionParameter; // 导入FunctionParameter接口，表示函数参数
import org.apache.calcite.schema.ImplementableFunction; // 导入ImplementableFunction接口，表示可实现的函数
import org.apache.calcite.schema.ScalarFunction; // 导入ScalarFunction接口，表示标量函数
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示可扩展的schema
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema类，抽象schema基类
import org.apache.calcite.schema.impl.ScalarFunctionImpl; // 导入ScalarFunctionImpl类，标量函数的实现
import org.apache.calcite.schema.impl.ViewTable; // 导入ViewTable类，表示视图表
import org.apache.calcite.sql.type.SqlTypeName; // 导入SqlTypeName枚举，表示SQL类型名称
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SqlConformanceEnum枚举，表示SQL兼容性级别
import org.apache.calcite.test.schemata.hr.HrSchema; // 导入HrSchema类，人力资源schema
import org.apache.calcite.util.Smalls; // 导入Smalls类，包含各种小型测试函数

import com.google.common.collect.ImmutableList; // 导入ImmutableList类，用于创建不可变列表

import org.junit.jupiter.api.Disabled; // 导入Disabled注解，用于禁用测试
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.lang.reflect.Method; // 导入Method类，用于反射调用方法
import java.sql.Connection; // 导入Connection接口，表示数据库连接
import java.sql.DriverManager; // 导入DriverManager类，用于管理数据库驱动
import java.sql.ResultSet; // 导入ResultSet接口，表示查询结果集
import java.sql.Statement; // 导入Statement接口，用于执行SQL语句
import java.util.ArrayList; // 导入ArrayList类，动态数组实现
import java.util.List; // 导入List接口，列表集合
import java.util.concurrent.atomic.AtomicInteger; // 导入AtomicInteger类，原子整数，用于线程安全的计数

import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat方法，用于断言匹配

/**
 * 用户定义函数(UDF)的测试类
 *
 * 该测试类专门用于测试Calcite框架中用户定义函数的完整功能，
 * 包括但不限于：
 * 1. 标量函数(Scalar Functions)：接收输入参数并返回单个值的函数
 * 2. 聚合函数(Aggregate Functions)：对一组值进行计算并返回单个结果的函数
 * 3. 函数重载(Function Overloading)：同名函数但参数类型或数量不同的多个版本
 * 4. 函数参数命名(Parameter Naming)：通过参数名传递参数
 * 5. 可选参数(Optional Parameters)：具有默认值的参数
 * 6. 空参数函数(Niladic Functions)：不需要参数的函数
 * 7. 半严格函数(Semi-Strict Functions)：可能返回null的函数
 * 8. 函数实例化(Function Instantiation)：函数对象的创建和管理
 * 9. 函数在视图中的使用(Functions in Views)：在视图定义中使用UDF
 * 10. 日期时间函数(Date/Time Functions)：处理日期和时间类型的函数
 * 11. 数组函数(Array Functions)：处理数组类型的函数
 * 12. 函数路径解析(Function Path Resolution)：通过schema路径查找函数
 * 13. 函数过滤器(Filter Clauses)：在聚合函数中使用WHERE子句过滤
 *
 * 注意：用户定义表函数(User-Defined Table Functions)的测试在{@link TableFunctionTest}类中
 *
 * 该类通过配置JSON模型来注册各种测试用的UDF，然后执行SQL查询来验证函数的正确性
 *
 * @see Smalls 包含各种测试用的UDF实现
 */
class UdfTest { // UdfTest类定义：用户定义函数的测试类，继承自Object，不继承任何其他类
  private CalciteAssert.AssertThat withUdf() { // withUdf方法：创建一个配置了各种UDF的CalciteAssert测试环境，返回AssertThat对象用于链式调用测试方法
    final String model = "{\n" // model变量：JSON格式的模型配置字符串，用于定义Calcite的schema、表和函数
        + "  version: '1.0',\n" // version字段：模型版本号，当前为1.0
        + "   schemas: [\n" // schemas字段：schema列表，用于定义数据库模式
        + "     {\n" // schema对象开始
        + "       name: 'adhoc',\n" // name字段：schema名称为'adhoc'，表示临时或即席schema
        + "       tables: [\n" // tables字段：表列表，定义该schema下的表
        + "         {\n" // 表对象开始
        + "           name: 'EMPLOYEES',\n" // name字段：表名称为'EMPLOYEES'，员工表
        + "           type: 'custom',\n" // type字段：表类型为'custom'，表示自定义表
        + "           factory: '" // factory字段：自定义表工厂的完整类名
        + JdbcTest.EmpDeptTableFactory.class.getName() // 获取EmpDeptTableFactory类的完整名称，用于创建员工部门表
        + "',\n"
        + "           operand: {'foo': true, 'bar': 345}\n" // operand字段：传递给工厂的参数对象，包含foo和bar两个属性
        + "         }\n" // 表对象结束
        + "       ],\n" // tables列表结束
        + "       functions: [\n" // functions字段：函数列表，定义该schema下的用户定义函数
        + "         {\n" // 函数对象开始
        + "           name: 'MY_PLUS',\n" // name字段：函数名称为'MY_PLUS'，自定义加法函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyPlusFunction.class.getName() // 获取MyPlusFunction类的完整名称，实现加法功能
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_DET_PLUS',\n" // name字段：函数名称为'MY_DET_PLUS'，确定性加法函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyDeterministicPlusFunction.class.getName() // 获取MyDeterministicPlusFunction类的完整名称，实现确定性加法
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_LEFT',\n" // name字段：函数名称为'MY_LEFT'，左截取字符串函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyLeftFunction.class.getName() // 获取MyLeftFunction类的完整名称，实现字符串左截取
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'ABCDE',\n" // name字段：函数名称为'ABCDE'，测试可选参数的函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyAbcdeFunction.class.getName() // 获取MyAbcdeFunction类的完整名称，测试可选参数功能
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_STR',\n" // name字段：函数名称为'MY_STR'，字符串转换函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyToStringFunction.class.getName() // 获取MyToStringFunction类的完整名称，实现字符串转换
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_DOUBLE',\n" // name字段：函数名称为'MY_DOUBLE'，双倍值函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyDoubleFunction.class.getName() // 获取MyDoubleFunction类的完整名称，实现值翻倍
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_EXCEPTION',\n" // name字段：函数名称为'MY_EXCEPTION'，异常处理函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyExceptionFunction.class.getName() // 获取MyExceptionFunction类的完整名称，测试异常处理
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_NILADIC_PARENTHESES',\n" // name字段：函数名称为'MY_NILADIC_PARENTHESES'，无参函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.MyNiladicParenthesesFunction.class.getName() // 获取MyNiladicParenthesesFunction类的完整名称，测试无参函数
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'COUNT_ARGS',\n" // name字段：函数名称为'COUNT_ARGS'，参数计数函数（0个参数）
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.CountArgs0Function.class.getName() // 获取CountArgs0Function类的完整名称，无参数版本
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'COUNT_ARGS',\n" // name字段：函数名称为'COUNT_ARGS'，参数计数函数（1个参数）
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.CountArgs1Function.class.getName() // 获取CountArgs1Function类的完整名称，1个参数版本
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'COUNT_ARGS',\n" // name字段：函数名称为'COUNT_ARGS'，参数计数函数（1个可空参数）
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.CountArgs1NullableFunction.class.getName() // 获取CountArgs1NullableFunction类的完整名称，1个可空参数版本
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'COUNT_ARGS',\n" // name字段：函数名称为'COUNT_ARGS'，参数计数函数（2个参数）
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.CountArgs2Function.class.getName() // 获取CountArgs2Function类的完整名称，2个参数版本
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_ABS',\n" // name字段：函数名称为'MY_ABS'，绝对值函数
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + java.lang.Math.class.getName() // 获取Math类的完整名称，使用Java标准库的abs方法
        + "',\n"
        + "           methodName: 'abs'\n" // methodName字段：指定要调用的方法名为'abs'，绝对值方法
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'NULL4',\n" // name字段：函数名称为'NULL4'，半严格函数测试
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.Null4Function.class.getName() // 获取Null4Function类的完整名称，测试半严格函数
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'NULL8',\n" // name字段：函数名称为'NULL8'，严格函数测试
        + "           className: '" // className字段：实现该函数的Java类完整名称
        + Smalls.Null8Function.class.getName() // 获取Null8Function类的完整名称，测试严格函数
        + "'\n"
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           className: '" // className字段：实现该函数的Java类完整名称（未指定name，使用类名）
        + Smalls.MultipleFunction.class.getName() // 获取MultipleFunction类的完整名称，包含多个方法
        + "',\n"
        + "           methodName: '*'\n" // methodName字段：通配符'*'表示注册该类的所有公共静态方法
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           className: '" // className字段：实现该函数的Java类完整名称（未指定name，使用类名）
        + Smalls.AllTypesFunction.class.getName() // 获取AllTypesFunction类的完整名称，包含所有类型测试函数
        + "',\n"
        + "           methodName: '*'\n" // methodName字段：通配符'*'表示注册该类的所有公共静态方法
        + "         }\n" // 函数对象结束
        + "       ]\n" // functions列表结束
        + "     }\n" // schema对象结束
        + "   ]\n" // schemas列表结束
        + "}"; // JSON模型配置字符串结束
    return CalciteAssert.model(model); // 返回：根据模型配置创建CalciteAssert.AssertThat对象，用于链式调用测试方法
  } // withUdf方法结束

  /** Tests a user-defined function that is defined in terms of a class with
   * non-static methods. */
  // 测试方法注释：测试基于非静态方法类定义的用户定义函数
  // 该测试验证了Calcite能够正确调用非静态方法实现的UDF
  // 注意：该测试已被禁用，因为存在间歇性失败的问题（CALCITE-1561）
  @Disabled("[CALCITE-1561] Intermittent test failures") // Disabled注解：禁用该测试，原因是间歇性失败，关联JIRA问题CALCITE-1561
  @Test void testUserDefinedFunction() { // testUserDefinedFunction方法：测试用户定义函数的基本功能
      final String sql = "select \"adhoc\".my_plus(\"deptno\", 100) as p\n" // sql变量：SQL查询语句，调用my_plus函数将deptno字段加100，结果别名为p
          + "from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询数据
      final AtomicInteger c = Smalls.MyPlusFunction.INSTANCE_COUNT.get(); // c变量：获取MyPlusFunction类的实例计数器，用于跟踪函数实例创建次数
      final int before = c.get(); // before变量：记录执行查询前的实例计数，用于后续比较
      withUdf().query(sql).returnsUnordered("P=110", // 执行查询并验证结果，期望返回4行结果，值分别为110, 120, 110, 110（对应deptno 10, 20, 10, 10各加100）
          "P=120",
          "P=110",
          "P=110");
      final int after = c.get(); // after变量：记录执行查询后的实例计数
      assertThat(after, is(before + 4)); // 断言：验证查询后实例计数增加了4（因为查询返回4行，每行调用一次函数），确保函数被正确调用
    } // testUserDefinedFunction方法结束
  /** As {@link #testUserDefinedFunction()}, but checks that the class is
   * instantiated exactly once, per
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1548">[CALCITE-1548]
   * Instantiate function objects once per query</a>. */
  // 测试方法注释：类似于testUserDefinedFunction()，但验证函数类在每次查询中只实例化一次
  // 该测试关联JIRA问题CALCITE-1548，确保确定性函数对象在每个查询中只实例化一次
  // 这是性能优化的关键，避免重复创建函数实例
  @Test void testUserDefinedFunctionInstanceCount() { // testUserDefinedFunctionInstanceCount方法：测试用户定义函数的实例化计数
      final String sql = "select \"adhoc\".my_det_plus(\"deptno\", 100) as p\n" // sql变量：SQL查询语句，使用确定性加法函数my_det_plus将deptno加100
          + "from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询数据
      final AtomicInteger c = Smalls.MyDeterministicPlusFunction.INSTANCE_COUNT.get(); // c变量：获取MyDeterministicPlusFunction类的实例计数器
      final int before = c.get(); // before变量：记录执行查询前的实例计数
      withUdf().query(sql).returnsUnordered("P=110", // 执行查询并验证结果，期望返回4行结果
          "P=120",
          "P=110",
          "P=110");
      final int after = c.get(); // after变量：记录执行查询后的实例计数
      assertThat(after, is(before + 1)); // 断言：验证查询后实例计数只增加了1（而不是4），证明确定性函数在整个查询中只实例化一次，这是性能优化的关键点
    } // testUserDefinedFunctionInstanceCount方法结束
  @Test void testUserDefinedFunctionB() { // testUserDefinedFunctionB方法：测试用户定义函数的另一种变体，验证my_double函数
    final String sql = "select \"adhoc\".my_double(\"deptno\") as p\n" // sql变量：SQL查询语句，调用my_double函数将deptno字段值翻倍
        + "from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询数据
    final String expected = "P=20\n" // expected变量：期望的查询结果字符串，包含4行数据，deptno值分别为10, 20, 10, 10，翻倍后为20, 40, 20, 20
        + "P=40\n"
        + "P=20\n"
        + "P=20\n";
    withUdf().query(sql).returns(expected); // 执行查询并验证结果是否与期望值完全匹配（包括顺序）
  } // testUserDefinedFunctionB方法结束

  @Test void testUserDefinedFunctionWithNull() { // testUserDefinedFunctionWithNull方法：测试用户定义函数处理null值的能力
    final String sql = "select \"adhoc\".my_det_plus(\"deptno\", 1 + null) as p\n" // sql变量：SQL查询语句，第二个参数为1+null，结果为null，测试函数如何处理null参数
        + "from \"adhoc\".EMPLOYEES where 1 > 0 or nullif(null, 1) is null"; // WHERE子句条件永远为true，确保返回所有行
    final AtomicInteger c = Smalls.MyDeterministicPlusFunction.INSTANCE_COUNT.get(); // c变量：获取MyDeterministicPlusFunction类的实例计数器
    final int before = c.get(); // before变量：记录执行查询前的实例计数
    withUdf() // 执行查询
        .query(sql)
        .returnsUnordered("P=null", // 验证结果，所有行的结果都应该是null，因为第二个参数是null
            "P=null",
            "P=null",
            "P=null");
    final int after = c.get(); // after变量：记录执行查询后的实例计数
    assertThat(after, is(before + 1)); // 断言：验证确定性函数在整个查询中只实例化一次，即使有null值
  } // testUserDefinedFunctionWithNull方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3195">[CALCITE-3195]
   * Handle a UDF that throws checked exceptions in the Enumerable code generator</a>. */
  // 测试方法注释：测试在Enumerable代码生成器中抛出受检异常的UDF
  // 该测试关联JIRA问题CALCITE-3195，验证Calcite能够正确处理UDF中抛出的异常
  // 测试了三种场景：直接调用、类型转换后调用、表达式作为参数调用
  @Test void testUserDefinedFunctionWithException() { // testUserDefinedFunctionWithException方法：测试用户定义函数的异常处理
    final String sql1 = "select \"adhoc\".my_exception(\"deptno\") as p\n" // sql1变量：第一个SQL查询，直接调用my_exception函数
        + "from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询数据
    final String expected1 = "P=20\n" // expected1变量：期望的查询结果，deptno值10, 20, 10, 10经过my_exception处理后变为20, 30, 20, 20
        + "P=30\n"
        + "P=20\n"
        + "P=20\n";
    withUdf().query(sql1).returns(expected1); // 执行第一个查询并验证结果

    final String sql2 = "select cast(\"adhoc\".my_exception(\"deptno\") as double) as p\n" // sql2变量：第二个SQL查询，将my_exception的返回值转换为double类型
        + "from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询数据
    final String expected2 = "P=20.0\n" // expected2变量：期望的查询结果，转换为double后的值
        + "P=30.0\n"
        + "P=20.0\n"
        + "P=20.0\n";
    withUdf().query(sql2).returns(expected2); // 执行第二个查询并验证结果

    final String sql3 = "select \"adhoc\".my_exception(\"deptno\" * 2 + 11) as p\n" // sql3变量：第三个SQL查询，使用表达式作为参数（deptno*2+11）
        + "from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询数据
    final String expected3 = "P=41\n" // expected3变量：期望的查询结果，计算结果：10*2+11=31, 20*2+11=51, 经my_exception处理后变为41, 61, 41, 41
        + "P=61\n"
        + "P=41\n"
        + "P=41\n";
    withUdf().query(sql3).returns(expected3); // 执行第三个查询并验证结果
  } // testUserDefinedFunctionWithException方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-937">[CALCITE-937]
   * User-defined function within view</a>. */
  // 测试方法注释：测试在视图中使用用户定义函数
  // 该测试关联JIRA问题CALCITE-937，验证UDF可以在视图定义中使用
  // 测试场景：创建一个包含UDF的视图，然后查询该视图，验证结果正确
  @Test void testUserDefinedFunctionInView() throws Exception { // testUserDefinedFunctionInView方法：测试在视图中使用用户定义函数，可能抛出异常
    Class.forName("org.apache.calcite.jdbc.Driver"); // 加载Calcite JDBC驱动类，用于建立数据库连接
    Connection connection = DriverManager.getConnection("jdbc:calcite:"); // connection变量：创建Calcite数据库连接，使用内存模式
    CalciteConnection calciteConnection = // calciteConnection变量：将普通连接解包为CalciteConnection，以访问Calcite特定功能
        connection.unwrap(CalciteConnection.class); // 通过unwrap方法获取底层CalciteConnection对象
    SchemaPlus rootSchema = calciteConnection.getRootSchema(); // rootSchema变量：获取根schema，用于添加自定义schema
    rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 在根schema中添加名为"hr"的schema，使用反射方式创建基于HrSchema对象的schema

    SchemaPlus post = rootSchema.add("POST", new AbstractSchema()); // post变量：在根schema中添加名为"POST"的空schema，用于存放自定义函数和视图
    post.add("MY_INCREMENT", // 在POST schema中添加名为"MY_INCREMENT"的自定义函数
        ScalarFunctionImpl.create(Smalls.MyIncrement.class, "eval")); // 使用ScalarFunctionImpl创建标量函数实现，基于Smalls.MyIncrement类的eval方法

    final String viewSql = "select \"empid\" as EMPLOYEE_ID,\n" // viewSql变量：视图定义的SQL语句，包含UDF调用
        + "  \"name\" || ' ' || \"name\" as EMPLOYEE_NAME,\n" // 将name字段与自身拼接，生成全名
        + "  \"salary\" as EMPLOYEE_SALARY,\n" // 薪资字段
        + "  POST.MY_INCREMENT(\"empid\", 10) as INCREMENTED_SALARY\n" // 调用UDF MY_INCREMENT，将empid加10作为增量薪资
        + "from \"hr\".\"emps\""; // 从hr schema的emps表中查询
    post.add("V_EMP", // 在POST schema中添加名为"V_EMP"的视图
        ViewTable.viewMacro(post, viewSql, ImmutableList.of(), // 使用ViewTable.viewMacro创建视图，传入schema、视图SQL和空参数列表
            ImmutableList.of("POST", "V_EMP"), null)); // 指定视图路径为["POST", "V_EMP"]

    final String result = "" // result变量：期望的查询结果字符串
        + "EMPLOYEE_ID=100; EMPLOYEE_NAME=Bill Bill;" // 第一行：员工ID 100，姓名Bill Bill，薪资10000.0，增量薪资110.0
        + " EMPLOYEE_SALARY=10000.0; INCREMENTED_SALARY=110.0\n"
        + "EMPLOYEE_ID=200; EMPLOYEE_NAME=Eric Eric;" // 第二行：员工ID 200，姓名Eric Eric，薪资8000.0，增量薪资220.0
        + " EMPLOYEE_SALARY=8000.0; INCREMENTED_SALARY=220.0\n"
        + "EMPLOYEE_ID=150; EMPLOYEE_NAME=Sebastian Sebastian;" // 第三行：员工ID 150，姓名Sebastian Sebastian，薪资7000.0，增量薪资165.0
        + " EMPLOYEE_SALARY=7000.0; INCREMENTED_SALARY=165.0\n"
        + "EMPLOYEE_ID=110; EMPLOYEE_NAME=Theodore Theodore;" // 第四行：员工ID 110，姓名Theodore Theodore，薪资11500.0，增量薪资121.0
        + " EMPLOYEE_SALARY=11500.0; INCREMENTED_SALARY=121.0\n";

    Statement statement = connection.createStatement(); // statement变量：创建SQL语句执行对象
    ResultSet resultSet = statement.executeQuery(viewSql); // resultSet变量：直接执行视图SQL并获取结果集
    assertThat(CalciteAssert.toString(resultSet), is(result)); // 断言：验证直接执行视图SQL的结果与期望值匹配
    resultSet.close(); // 关闭结果集，释放资源

    ResultSet viewResultSet = // viewResultSet变量：通过查询视图名称获取结果集
        statement.executeQuery("select * from \"POST\".\"V_EMP\""); // 执行查询视图V_EMP的SQL
    assertThat(CalciteAssert.toString(viewResultSet), is(result)); // 断言：验证查询视图的结果与期望值匹配
    statement.close(); // 关闭语句对象，释放资源
    connection.close(); // 关闭数据库连接，释放资源
  } // testUserDefinedFunctionInView方法结束

  /**
   * Tests that IS NULL/IS NOT NULL is properly implemented for non-strict
   * functions.
   */
  // 测试方法注释：测试非严格函数的IS NULL/IS NOT NULL实现
  // 非严格函数是指在参数为null时仍然会被调用的函数
  // 该测试验证Calcite能够正确处理非严格函数的null值判断
  @Test void testNotNullImplementor() { // testNotNullImplementor方法：测试非严格函数的null处理实现
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query( // 第一个查询：测试在非严格函数外层使用upper函数
        "select upper(\"adhoc\".my_str(\"name\")) as p from \"adhoc\".EMPLOYEES") // 调用my_str函数将name转换为字符串，然后用upper转大写
        .returns("P=<BILL>\n" // 验证结果，my_str返回的字符串被尖括号包围，然后转大写
            + "P=<ERIC>\n"
            + "P=<SEBASTIAN>\n"
            + "P=<THEODORE>\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n" // 第二个查询：测试使用IS NOT NULL过滤非严格函数的结果
        + "where \"adhoc\".my_str(\"name\") is not null") // WHERE子句检查my_str的结果是否不为null
        .returns("P=Bill\n" // 验证结果，返回所有员工姓名（因为my_str不会返回null）
            + "P=Eric\n"
            + "P=Sebastian\n"
            + "P=Theodore\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n" // 第三个查询：测试在非严格函数参数中使用upper
        + "where \"adhoc\".my_str(upper(\"name\")) is not null") // 先将name转大写，再传给my_str
        .returns("P=Bill\n" // 验证结果，返回所有员工姓名
            + "P=Eric\n"
            + "P=Sebastian\n"
            + "P=Theodore\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n" // 第四个查询：测试在非严格函数结果外层使用upper
        + "where upper(\"adhoc\".my_str(\"name\")) is not null") // 先调用my_str，再对结果转大写，然后判断是否不为null
        .returns("P=Bill\n" // 验证结果，返回所有员工姓名
            + "P=Eric\n"
            + "P=Sebastian\n"
            + "P=Theodore\n");
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n" // 第五个查询：测试使用IS NULL过滤
        + "where \"adhoc\".my_str(\"name\") is null") // WHERE子句检查my_str的结果是否为null
        .returns(""); // 验证结果，返回空结果（因为my_str不会返回null）
    with.query("select \"name\" as p from \"adhoc\".EMPLOYEES\n" // 第六个查询：测试嵌套调用非严格函数并比较结果
        + "where \"adhoc\".my_str(upper(\"adhoc\".my_str(\"name\")" // 嵌套调用：my_str -> upper -> my_str，然后判断结果是否等于'8'
        + ")) ='8'")
        .returns(""); // 验证结果，返回空结果（因为没有结果等于'8'）
  } // testNotNullImplementor方法结束

  /** Tests that we generate the appropriate checks for a "semi-strict"
   * function.
   *
   * <p>The difference between "strict" and "semi-strict" functions is that a
   * "semi-strict" function might return null even if none of its arguments
   * are null. (Both always return null if one of their arguments is null.)
   * Thus, a nasty function is more unpredictable.
   *
   * @see SemiStrict */
  // 测试方法注释：测试半严格函数的适当检查生成
  // 严格函数(Strict)：如果任何参数为null，则返回null
  // 半严格函数(Semi-Strict)：即使所有参数都不为null，也可能返回null；但如果任何参数为null，则返回null
  // 这种函数更不可预测，需要特殊的null检查机制
  @see SemiStrict // 参见SemiStrict注解，用于标记半严格函数
  @Test void testSemiStrict() { // testSemiStrict方法：测试半严格函数的行为
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    final String sql = "select\n" // sql变量：第一个SQL查询，测试null4函数（半严格函数）
        + "  \"adhoc\".null4(upper(\"name\")) as p\n" // 调用null4函数，参数是upper("name")，null4会根据输入值决定是否返回null
        + " from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询
    with.query(sql) // 执行查询
        .returnsUnordered("P=null", // 验证结果：前两个员工（Bill, Eric）的name经过null4处理后返回null
            "P=null",
            "P=SEBASTIAN", // 后两个员工（Sebastian, Theodore）的name经过null4处理后返回原值
            "P=THEODORE");
    // my_str is non-strict; it must be called when args are null
    // 注释：my_str是非严格函数，即使参数为null也必须被调用
    final String sql2 = "select\n" // sql2变量：第二个SQL查询，测试嵌套调用半严格函数和非严格函数
        + "  \"adhoc\".my_str(upper(\"adhoc\".null4(\"name\"))) as p\n" // 嵌套调用：null4 -> upper -> my_str
        + " from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询
    with.query(sql2) // 执行查询
        .returnsUnordered("P=<null>", // 验证结果：前两个员工的结果是<null>（null4返回null，upper处理null返回null，my_str处理null返回<null>）
            "P=<null>",
            "P=<SEBASTIAN>", // 后两个员工的结果是<SEBASTIAN>和<THEODORE>
            "P=<THEODORE>");
    // null8 throws NPE if its argument is null,
    // so we had better know that null4 might return null
    // 注释：null8如果参数为null会抛出NPE，所以我们必须知道null4可能返回null
    final String sql3 = "select\n" // sql3变量：第三个SQL查询，测试严格函数接收半严格函数的结果
        + "  \"adhoc\".null8(\"adhoc\".null4(\"name\")) as p\n" // 嵌套调用：null4 -> null8，null8是严格函数，不能处理null
        + " from \"adhoc\".EMPLOYEES"; // 从adhoc schema的EMPLOYEES表中查询
    with.query(sql3) // 执行查询
        .returnsUnordered("P=null", // 验证结果：前两个和最后一个员工的结果是null（null4返回null，null8检测到null返回null）
            "P=null",
            "P=Sebastian", // 只有Sebastian的结果不是null（null4返回非null值，null8正常处理）
            "P=null");
  } // testSemiStrict方法结束

  /** Tests derived return type of user-defined function. */
  // 测试方法注释：测试用户定义函数的派生返回类型
  // 派生返回类型是指函数的返回类型是根据参数类型和函数实现自动推导出来的
  // 该测试验证Calcite能够正确推导和UDF的返回类型
  @Test void testUdfDerivedReturnType() { // testUdfDerivedReturnType方法：测试UDF的派生返回类型
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query( // 第一个查询：测试my_double函数的返回类型在聚合函数中的推导
        "select max(\"adhoc\".my_double(\"deptno\")) as p from \"adhoc\".EMPLOYEES") // 调用my_double将deptno翻倍，然后用max求最大值
        .returns("P=40\n"); // 验证结果，最大值为40（deptno最大值20翻倍）
    with.query("select max(\"adhoc\".my_str(\"name\")) as p\n" // 第二个查询：测试my_str函数的返回类型在聚合函数中的推导
        + "from \"adhoc\".EMPLOYEES\n" // 从adhoc schema的EMPLOYEES表中查询
        + "where \"adhoc\".my_str(\"name\") is null") // WHERE子句过滤my_str结果为null的行（实际上没有这样的行）
        .returns("P=null\n"); // 验证结果，max返回null（因为没有满足条件的行）
  } // testUdfDerivedReturnType方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6645">[CALCITE-6645]
   * User-defined function with niladic parentheses</a>. */
  // 测试方法注释：测试无参函数（niladic function）的括号使用
  // 该测试关联JIRA问题CALCITE-6645，验证不同SQL兼容性级别下无参函数的语法
  // MySQL 5允许无参函数使用括号，Oracle 10不允许
  @Test void testUserDefinedFunctionWithNiladicParentheses() { // testUserDefinedFunctionWithNiladicParentheses方法：测试无参函数的括号使用
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.with(SqlConformanceEnum.MYSQL_5) // 设置SQL兼容性为MySQL 5，允许无参函数使用括号
        .query("select \"adhoc\".my_niladic_parentheses() as p\n" // 查询：使用括号调用无参函数
            + "from \"adhoc\".EMPLOYEES limit 1") // 从EMPLOYEES表中查询，限制返回1行
        .returns("P=foo\n"); // 验证结果，返回值是"foo"
    with.with(SqlConformanceEnum.ORACLE_10) // 设置SQL兼容性为Oracle 10，不允许无参函数使用括号
        .query("select \"adhoc\".my_niladic_parentheses as p\n" // 查询：不使用括号调用无参函数
            + "from \"adhoc\".EMPLOYEES limit 1") // 从EMPLOYEES表中查询，限制返回1行
        .returns("P=foo\n"); // 验证结果，返回值是"foo"
    with.with(SqlConformanceEnum.DEFAULT) // 设置SQL兼容性为默认值
        .query("select \"adhoc\".my_niladic_parentheses as p\n" // 查询：不使用括号调用无参函数
            + "from \"adhoc\".EMPLOYEES limit 1") // 从EMPLOYEES表中查询，限制返回1行
        .returns("P=foo\n"); // 验证结果，返回值是"foo"
    with.with(SqlConformanceEnum.DEFAULT) // 设置SQL兼容性为默认值
        .query("select \"adhoc\".my_niladic_parentheses() as p\n" // 查询：使用括号调用无参函数
            + "from \"adhoc\".EMPLOYEES limit 1") // 从EMPLOYEES表中查询，限制返回1行
        .returns("P=foo\n"); // 验证结果，返回值是"foo"
    // wrong niladic function with mysql_5 conformance
    // 注释：在MySQL 5兼容性下，不使用括号调用无参函数是错误的
    with.with(SqlConformanceEnum.MYSQL_5) // 设置SQL兼容性为MySQL 5
        .query("select \"adhoc\".my_niladic_parentheses as p\n" // 查询：不使用括号调用无参函数（错误）
            + "from \"adhoc\".EMPLOYEES limit 1") // 从EMPLOYEES表中查询，限制返回1行
        .throws_("Table 'adhoc' not found"); // 期望抛出异常，因为解析器将my_niladic_parentheses误认为是表名
    // wrong niladic function with oracle_10 conformance
    // 注释：在Oracle 10兼容性下，使用括号调用无参函数是错误的
    with.with(SqlConformanceEnum.ORACLE_10) // 设置SQL兼容性为Oracle 10
        .query("select \"adhoc\".my_niladic_parentheses() as p\n" // 查询：使用括号调用无参函数（错误）
            + "from \"adhoc\".EMPLOYEES limit 1") // 从EMPLOYEES表中查询，限制返回1行
        .throws_("No match found for function signature MY_NILADIC_PARENTHESES()"); // 期望抛出异常，因为没有匹配的函数签名
  } // testUserDefinedFunctionWithNiladicParentheses方法结束

  /** Tests a user-defined function that has multiple overloads. */
  // 测试方法注释：测试具有多个重载版本的用户定义函数
  // 函数重载是指同一个函数名，但参数类型或数量不同的多个版本
  // 该测试验证Calcite能够正确处理函数重载
  @Test void testUdfOverloaded() { // testUdfOverloaded方法：测试用户定义函数的重载
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    // MYSQL_5 conformance support niladic function with parentheses
    // 注释：MySQL 5兼容性支持无参函数使用括号
    with.with(SqlConformanceEnum.MYSQL_5) // 设置SQL兼容性为MySQL 5
        .query("values (\"adhoc\".count_args(),\n" // 查询：调用count_args()无参版本，期望返回0
        + " \"adhoc\".count_args(0),\n" // 调用count_args(0)单参版本，期望返回1
        + " \"adhoc\".count_args(0, 0))") // 调用count_args(0, 0)双参版本，期望返回2
        .returns("EXPR$0=0; EXPR$1=1; EXPR$2=2\n"); // 验证结果，三个调用的返回值分别为0, 1, 2
    // MYSQL_5 conformance support niladic function with parentheses
    // 注释：MySQL 5兼容性支持无参函数使用括号
    with.with(SqlConformanceEnum.MYSQL_5) // 设置SQL兼容性为MySQL 5
        .query("select max(\"adhoc\".count_args()) as p0,\n" // 查询：在聚合函数中调用count_args()无参版本
        + " min(\"adhoc\".count_args(0)) as p1,\n" // 在聚合函数中调用count_args(0)单参版本
        + " max(\"adhoc\".count_args(0, 0)) as p2\n" // 在聚合函数中调用count_args(0, 0)双参版本
        + "from \"adhoc\".EMPLOYEES limit 1") // 从EMPLOYEES表中查询，限制返回1行
        .returns("P0=0; P1=1; P2=2\n"); // 验证结果，三个聚合函数的返回值分别为0, 1, 2
  } // testUdfOverloaded方法结束

  @Test void testUdfOverloadedNullable() { // testUdfOverloadedNullable方法：测试可空参数的函数重载
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    // MYSQL_5 conformance support niladic function with parentheses
    // 注释：MySQL 5兼容性支持无参函数使用括号
    with.with(SqlConformanceEnum.MYSQL_5) // 设置SQL兼容性为MySQL 5
        .query("values (\"adhoc\".count_args(),\n" // 查询：调用count_args()无参版本，期望返回0
        + " \"adhoc\".count_args(cast(null as smallint)),\n" // 调用count_args(null)单参可空版本，期望返回-1
        + " \"adhoc\".count_args(0, 0))") // 调用count_args(0, 0)双参版本，期望返回2
        .returns("EXPR$0=0; EXPR$1=-1; EXPR$2=2\n"); // 验证结果，三个调用的返回值分别为0, -1, 2
  } // testUdfOverloadedNullable方法结束

  /** Tests passing parameters to user-defined function by name. */
// 测试方法注释：测试通过参数名传递参数给用户定义函数
// 命名参数允许调用者按名称而不是位置传递参数，提高了代码可读性
// 该测试验证Calcite能够正确处理命名参数的各种场景
  @Test void testUdfArgumentName() { // testUdfArgumentName方法：测试通过参数名传递参数
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    // arguments in physical order
    // 注释：按物理顺序传递参数（参数名顺序与函数定义一致）
    with.query("values (\"adhoc\".my_left(\"s\" -> 'hello', \"n\" -> 3))") // 查询：使用命名参数调用my_left函数，s参数为'hello'，n参数为3
        .returns("EXPR$0=hel\n"); // 验证结果，返回'hello'的前3个字符'hel'
    // arguments in reverse order
    // 注释：按相反顺序传递参数（参数名顺序与函数定义相反）
    with.query("values (\"adhoc\".my_left(\"n\" -> 3, \"s\" -> 'hello'))") // 查询：使用命名参数调用my_left函数，n参数为3，s参数为'hello'（顺序相反）
        .returns("EXPR$0=hel\n"); // 验证结果，返回'hello'的前3个字符'hel'（结果相同）
    with.query("values (\"adhoc\".my_left(\"n\" -> 1 + 2, \"s\" -> 'hello'))") // 查询：使用表达式作为参数值，n参数为1+2=3
        .returns("EXPR$0=hel\n"); // 验证结果，返回'hello'的前3个字符'hel'
    // duplicate argument names
    // 注释：重复的参数名
    with.query("values (\"adhoc\".my_left(\"n\" -> 3, \"n\" -> 2, \"s\" -> 'hello'))") // 查询：n参数重复指定两次
        .throws_("Duplicate argument name 'n'"); // 期望抛出异常，报告参数名重复
    // invalid argument names
    // 注释：无效的参数名
    with.query("values (\"adhoc\".my_left(\"n\" -> 3, \"m\" -> 2, \"s\" -> 'h'))") // 查询：使用了不存在的参数名m
        .throws_("No match found for function signature " // 期望抛出异常，报告找不到匹配的函数签名
            + "MY_LEFT(n -> <NUMERIC>, m -> <NUMERIC>, s -> <CHARACTER>)");
    // missing arguments
    // 注释：缺少参数
    with.query("values (\"adhoc\".my_left(\"n\" -> 3))") // 查询：只提供了n参数，缺少s参数
        .throws_("No match found for function signature MY_LEFT(n -> <NUMERIC>)"); // 期望抛出异常，报告找不到匹配的函数签名
    with.query("values (\"adhoc\".my_left(\"s\" -> 'hello'))") // 查询：只提供了s参数，缺少n参数
        .throws_("No match found for function signature MY_LEFT(s -> <CHARACTER>)"); // 期望抛出异常，报告找不到匹配的函数签名
    // arguments of wrong type, will do implicitly type coercion.
    // 注释：参数类型错误，会进行隐式类型转换
    with.query("values (\"adhoc\".my_left(\"n\" -> 'hello', \"s\" -> 'x'))") // 查询：n参数类型错误，期望数字但提供了字符串'hello'
        .throws_("java.lang.NumberFormatException: For input string: \"hello\""); // 期望抛出异常，因为'hello'无法转换为数字
    with.query("values (\"adhoc\".my_left(\"n\" -> '1', \"s\" -> 'x'))") // 查询：n参数类型错误，但'1'可以转换为数字1
        .returns("EXPR$0=x\n"); // 验证结果，隐式类型转换成功，返回'x'的前1个字符'x'
    with.query("values (\"adhoc\".my_left(\"n\" -> 1, \"s\" -> 0))") // 查询：s参数类型错误，期望字符串但提供了数字0
        .returns("EXPR$0=0\n"); // 验证结果，隐式类型转换成功，返回0的前1个字符'0'
  } // testUdfArgumentName方法结束

  /** Tests calling a user-defined function some of whose parameters are
   * optional. */
  // 测试方法注释：测试调用具有可选参数的用户定义函数
  // 可选参数是指在调用时可以省略的参数，通常有默认值
  // 该测试验证Calcite能够正确处理可选参数的各种场景
  @Test void testUdfArgumentOptional() { // testUdfArgumentOptional方法：测试可选参数的使用
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query("values (\"adhoc\".abcde(a->1,b->2,c->3,d->4,e->5))") // 查询：使用命名参数提供所有5个参数
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: 5}\n"); // 验证结果，返回包含所有值的字符串
    with.query("values (\"adhoc\".abcde(1,2,3,4,CAST(NULL AS INTEGER)))") // 查询：使用位置参数，最后一个参数为null
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: null}\n"); // 验证结果，e参数为null
    with.query("values (\"adhoc\".abcde(a->1,b->2,c->3,d->4))") // 查询：省略可选参数e
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: null}\n"); // 验证结果，e参数为null（默认值）
    with.query("values (\"adhoc\".abcde(a->1,b->2,c->3))") // 查询：省略可选参数d和e
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: null, e: null}\n"); // 验证结果，d和e参数为null（默认值）
    with.query("values (\"adhoc\".abcde(a->1,e->5,c->3))") // 查询：跳过可选参数b和d，直接指定a、c、e
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: 5}\n"); // 验证结果，b和d参数为null（默认值）
    with.query("values (\"adhoc\".abcde(1,2,3))") // 查询：使用位置参数，只提供前3个参数
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: null, e: null}\n"); // 验证结果，d和e参数为null（默认值）
    with.query("values (\"adhoc\".abcde(1,2,3,4))") // 查询：使用位置参数，只提供前4个参数
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: null}\n"); // 验证结果，e参数为null（默认值）
    with.query("values (\"adhoc\".abcde(1,2,3,4,5))") // 查询：使用位置参数，提供所有5个参数
        .returns("EXPR$0={a: 1, b: 2, c: 3, d: 4, e: 5}\n"); // 验证结果，返回包含所有值的字符串
    with.query("values (\"adhoc\".abcde(1,2))") // 查询：只提供2个参数，少于必需参数数量
        .throws_("No match found for function signature ABCDE(<NUMERIC>, <NUMERIC>)"); // 期望抛出异常，报告找不到匹配的函数签名
    with.query("values (\"adhoc\".abcde(1,DEFAULT,3))") // 查询：使用DEFAULT关键字指定可选参数b使用默认值
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: null}\n"); // 验证结果，b参数为null（默认值）
    // implicit type coercion.
    // 注释：隐式类型转换
    with.query("values (\"adhoc\".abcde(1,DEFAULT,'abcde'))") // 查询：c参数类型错误，期望数字但提供了字符串'abcde'
        .throws_("java.lang.NumberFormatException: For input string: \"abcde\""); // 期望抛出异常，因为'abcde'无法转换为数字
    with.query("values (\"adhoc\".abcde(1,DEFAULT,'123'))") // 查询：c参数类型错误，但'123'可以转换为数字123
        .returns("EXPR$0={a: 1, b: null, c: 123, d: null, e: null}\n"); // 验证结果，隐式类型转换成功
    with.query("values (\"adhoc\".abcde(true))") // 查询：a参数类型错误，期望数字但提供了布尔值true
        .throws_("No match found for function signature ABCDE(<BOOLEAN>)"); // 期望抛出异常，报告找不到匹配的函数签名
    with.query("values (\"adhoc\".abcde(true,DEFAULT))") // 查询：a参数类型错误，期望数字但提供了布尔值true
        .throws_("No match found for function signature ABCDE(<BOOLEAN>, <ANY>)"); // 期望抛出异常，报告找不到匹配的函数签名
    with.query("values (\"adhoc\".abcde(1,DEFAULT,3,DEFAULT))") // 查询：使用DEFAULT关键字指定可选参数b和d使用默认值
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: null}\n"); // 验证结果，b和d参数为null（默认值）
    with.query("values (\"adhoc\".abcde(1,2,DEFAULT))") // 查询：c参数不是可选参数，不能使用DEFAULT
        .throws_("DEFAULT is only allowed for optional parameters"); // 期望抛出异常，报告DEFAULT只能用于可选参数
    with.query("values (\"adhoc\".abcde(a->1,b->2,c->DEFAULT))") // 查询：c参数不是可选参数，不能使用DEFAULT
        .throws_("DEFAULT is only allowed for optional parameters"); // 期望抛出异常，报告DEFAULT只能用于可选参数
    with.query("values (\"adhoc\".abcde(a->1,b->DEFAULT,c->3))") // 查询：使用DEFAULT关键字指定可选参数b使用默认值
        .returns("EXPR$0={a: 1, b: null, c: 3, d: null, e: null}\n"); // 验证结果，b参数为null（默认值）
  } // testUdfArgumentOptional方法结束

  /** Test for
   * {@link org.apache.calcite.runtime.CalciteResource#requireDefaultConstructor(String)}. */
  // 测试方法注释：测试非静态用户定义函数必须有无参公共构造函数
  // 该测试关联CalciteResource#requireDefaultConstructor方法
  // 验证Calcite能够正确检测并报告UDF类缺少无参构造函数的错误
  @Test void testUserDefinedFunction2() { // testUserDefinedFunction2方法：测试非静态UDF的构造函数要求
    String message = "Declaring class " // message变量：期望的错误消息字符串
        + "'org.apache.calcite.util.Smalls$AwkwardFunction' of non-static " // 指明是非静态用户定义函数
        + "user-defined function must have a public constructor with zero " // 要求必须有零参数的公共构造函数
        + "parameters"; // 错误消息结束
    withBadUdf(Smalls.AwkwardFunction.class).connectThrows(message); // 调用withBadUdf方法创建测试环境并验证连接时抛出指定错误消息
  } // testUserDefinedFunction2方法结束

  /** Tests user-defined function, with multiple methods per class. */
  // 测试方法注释：测试一个类中包含多个方法的用户定义函数
  // 该测试验证Calcite能够正确处理：
  // 1. 同一个类中的多个方法（通过methodName指定）
  // 2. 函数重载（同名方法不同参数）
  // 3. 静态方法与非静态方法的区别
  @Test void testUserDefinedFunctionWithMethodName() { // testUserDefinedFunctionWithMethodName方法：测试多方法类中的UDF
    // java.lang.Math has abs(int) and abs(double).
    // 注释：java.lang.Math类有abs(int)和abs(double)两个重载方法
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query("values abs(-4)").returnsValue("4"); // 查询：调用Math.abs(-4)，验证返回4（int版本）
    with.query("values abs(-4.5)").returnsValue("4.5"); // 查询：调用Math.abs(-4.5)，验证返回4.5（double版本）

    // 3 overloads of "fun1", another method "fun2", but method "nonStatic"
    // cannot be used as a function
    // 注释：fun1有3个重载版本，还有另一个方法fun2，但nonStatic方法不能用作函数
    with.query("values \"adhoc\".\"fun1\"(2)").returnsValue("4"); // 查询：调用fun1(2)，期望返回4（2*2）
    with.query("values \"adhoc\".\"fun1\"(2, 3)").returnsValue("5"); // 查询：调用fun1(2, 3)，期望返回5（2+3）
    with.query("values \"adhoc\".\"fun1\"('Foo Bar')").returnsValue("foo bar"); // 查询：调用fun1('Foo Bar')，期望返回'foo bar'（转小写）
    with.query("values \"adhoc\".\"fun2\"(10)").returnsValue("30"); // 查询：调用fun2(10)，期望返回30（10*3）
    with.query("values \"adhoc\".\"nonStatic\"(2)") // 查询：尝试调用nonStatic(2)，但这是非静态方法
        .throws_("No match found for function signature nonStatic(<NUMERIC>)"); // 期望抛出异常，报告找不到匹配的函数签名
  } // testUserDefinedFunctionWithMethodName方法结束

  /** Tests user-defined aggregate function. */
  // 测试方法注释：测试用户定义的聚合函数
  // 聚合函数是对一组值进行计算并返回单个结果的函数（如SUM、AVG、COUNT等）
  // 该测试验证Calcite能够正确处理自定义聚合函数，包括：
  // 1. 静态方法实现的聚合函数
  // 2. 实例方法实现的聚合函数
  // 3. GROUP BY子句中的聚合函数
  // 4. 隐式类型转换
  // 5. 错误处理（参数数量、类型等）
  @Test void testUserDefinedAggregateFunction() { // testUserDefinedAggregateFunction方法：测试用户定义的聚合函数
    final String empDept = JdbcTest.EmpDeptTableFactory.class.getName(); // empDept变量：获取EmpDeptTableFactory类的完整名称
    final String sum = Smalls.MyStaticSumFunction.class.getName(); // sum变量：获取MyStaticSumFunction类的完整名称（静态方法实现的聚合函数）
    final String sum2 = Smalls.MySumFunction.class.getName(); // sum2变量：获取MySumFunction类的完整名称（实例方法实现的聚合函数）
    final CalciteAssert.AssertThat with = CalciteAssert.model("{\n" // with变量：创建包含自定义聚合函数的模型配置
        + "  version: '1.0',\n" // 版本号1.0
        + "   schemas: [\n" // schema列表开始
        + "     {\n" // schema对象开始
        + "       name: 'adhoc',\n" // schema名称为'adhoc'
        + "       tables: [\n" // 表列表开始
        + "         {\n" // 表对象开始
        + "           name: 'EMPLOYEES',\n" // 表名称为'EMPLOYEES'
        + "           type: 'custom',\n" // 表类型为'custom'
        + "           factory: '" + empDept + "',\n" // 自定义表工厂类名
        + "           operand: {'foo': true, 'bar': 345}\n" // 传递给工厂的参数
        + "         }\n" // 表对象结束
        + "       ],\n" // 表列表结束
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM',\n" // 函数名称为'MY_SUM'
        + "           className: '" + sum + "'\n" // 实现该函数的Java类名
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM2',\n" // 函数名称为'MY_SUM2'
        + "           className: '" + sum2 + "'\n" // 实现该函数的Java类名
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     }\n" // schema对象结束
        + "   ]\n" // schema列表结束
        + "}") // JSON模型配置结束
        .withDefaultSchema("adhoc"); // 设置默认schema为'adhoc'
    with.withDefaultSchema(null) // 清除默认schema
        .query( // 查询：使用完全限定名调用聚合函数
            "select \"adhoc\".my_sum(\"deptno\") as p from \"adhoc\".EMPLOYEES\n") // 计算所有deptno的总和
        .returns("P=50\n"); // 验证结果，deptno总和为50（10+20+10+10）
    with.query("select my_sum(\"empid\"), \"deptno\" as p from EMPLOYEES\n") // 查询：使用聚合函数但SELECT中有非聚合列
        .throws_( // 期望抛出异常
            "Expression 'deptno' is not being grouped"); // 错误消息：deptno列没有被分组
    with.query("select my_sum(\"deptno\") as p from EMPLOYEES\n") // 查询：在默认schema下调用聚合函数
        .returns("P=50\n"); // 验证结果，deptno总和为50
    // implicit type coercion.
    // 注释：隐式类型转换
    with.query("select my_sum(\"name\") as p from EMPLOYEES\n") // 查询：尝试对字符串字段求和
        .throws_("java.lang.NumberFormatException: For input string: \"Bill\""); // 期望抛出异常，因为字符串无法转换为数字
    with.query("select my_sum(\"deptno\", 1) as p from EMPLOYEES\n") // 查询：提供两个参数
        .throws_( // 期望抛出异常
            "No match found for function signature MY_SUM(<NUMERIC>, <NUMERIC>)"); // 错误消息：找不到匹配的函数签名
    with.query("select my_sum() as p from EMPLOYEES\n") // 查询：不提供任何参数
        .throws_( // 期望抛出异常
            "No match found for function signature MY_SUM()"); // 错误消息：找不到匹配的函数签名
    with.query("select \"deptno\", my_sum(\"deptno\") as p from EMPLOYEES\n" // 查询：使用GROUP BY分组，计算每组的deptno总和
        + "group by \"deptno\"") // 按deptno分组
        .returnsUnordered( // 验证结果（不保证顺序）
            "deptno=20; P=20", // deptno=20组的总和为20
            "deptno=10; P=30"); // deptno=10组的总和为30（10+10+10）
    with.query("select \"deptno\", my_sum2(\"deptno\") as p from EMPLOYEES\n" // 查询：使用实例方法实现的聚合函数
        + "group by \"deptno\"") // 按deptno分组
        .returnsUnordered("deptno=20; P=20", "deptno=10; P=30"); // 验证结果，与静态方法版本相同
  } // testUserDefinedAggregateFunction方法结束

  /** Tests user-defined aggregate function. */
  // 测试方法注释：测试具有多个参数的用户定义聚合函数
  // 该测试验证Calcite能够正确处理多参数的聚合函数，包括：
  // 1. 两个参数的聚合函数（带过滤功能）
  // 2. 三个参数的聚合函数（带过滤功能）
  // 3. 函数重载（同名函数不同参数）
  // 4. GROUP BY子句中的多参数聚合函数
  // 5. 隐式类型转换
  @Test void testUserDefinedAggregateFunctionWithMultipleParameters() { // testUserDefinedAggregateFunctionWithMultipleParameters方法：测试多参数聚合函数
    final String empDept = JdbcTest.EmpDeptTableFactory.class.getName(); // empDept变量：获取EmpDeptTableFactory类的完整名称
    final String sum21 = Smalls.MyTwoParamsSumFunctionFilter1.class.getName(); // sum21变量：获取MyTwoParamsSumFunctionFilter1类的完整名称（两参数聚合函数版本1）
    final String sum22 = Smalls.MyTwoParamsSumFunctionFilter2.class.getName(); // sum22变量：获取MyTwoParamsSumFunctionFilter2类的完整名称（两参数聚合函数版本2）
    final String sum31 = Smalls.MyThreeParamsSumFunctionWithFilter1.class.getName(); // sum31变量：获取MyThreeParamsSumFunctionWithFilter1类的完整名称（三参数聚合函数版本1）
    final String sum32 = Smalls.MyThreeParamsSumFunctionWithFilter2.class.getName(); // sum32变量：获取MyThreeParamsSumFunctionWithFilter2类的完整名称（三参数聚合函数版本2）
    final CalciteAssert.AssertThat with = CalciteAssert.model("{\n" // with变量：创建包含多参数聚合函数的模型配置
        + "  version: '1.0',\n" // 版本号1.0
        + "   schemas: [\n" // schema列表开始
        + "     {\n" // schema对象开始
        + "       name: 'adhoc',\n" // schema名称为'adhoc'
        + "       tables: [\n" // 表列表开始
        + "         {\n" // 表对象开始
        + "           name: 'EMPLOYEES',\n" // 表名称为'EMPLOYEES'
        + "           type: 'custom',\n" // 表类型为'custom'
        + "           factory: '" + empDept + "',\n" // 自定义表工厂类名
        + "           operand: {'foo': true, 'bar': 345}\n" // 传递给工厂的参数
        + "         }\n" // 表对象结束
        + "       ],\n" // 表列表结束
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM2',\n" // 函数名称为'MY_SUM2'
        + "           className: '" + sum21 + "'\n" // 实现该函数的Java类名（版本1）
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM2',\n" // 函数名称为'MY_SUM2'（重载）
        + "           className: '" + sum22 + "'\n" // 实现该函数的Java类名（版本2）
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM3',\n" // 函数名称为'MY_SUM3'
        + "           className: '" + sum31 + "'\n" // 实现该函数的Java类名（版本1）
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM3',\n" // 函数名称为'MY_SUM3'（重载）
        + "           className: '" + sum32 + "'\n" // 实现该函数的Java类名（版本2）
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     }\n" // schema对象结束
        + "   ]\n" // schema列表结束
        + "}") // JSON模型配置结束
        .withDefaultSchema("adhoc"); // 设置默认schema为'adhoc'
    with.withDefaultSchema(null) // 清除默认schema
        .query("select \"adhoc\".my_sum3(\"deptno\",\"name\",'Eric') as p\n" // 查询：调用三参数聚合函数，第三个参数是过滤条件'Eric'
            + "from \"adhoc\".EMPLOYEES\n") // 从EMPLOYEES表中查询
        .returns("P=20\n"); // 验证结果，只有name='Eric'的行的deptno被求和，结果为20
    with.query("select \"adhoc\".my_sum3(\"empid\",\"deptno\",\"commission\") as p " // 查询：调用三参数聚合函数，第三个参数是commission字段
        + "from \"adhoc\".EMPLOYEES\n") // 从EMPLOYEES表中查询
        .returns("P=330\n"); // 验证结果，empid+deptno+commission的总和为330
    with.query("select \"adhoc\".my_sum3(\"empid\",\"deptno\",\"commission\"),\n" // 查询：使用聚合函数但SELECT中有非聚合列
        + "  \"name\"\n" // 非聚合列name
        + "from \"adhoc\".EMPLOYEES\n") // 从EMPLOYEES表中查询
        .throws_("Expression 'name' is not being grouped"); // 期望抛出异常，报告name列没有被分组
    with.query("select \"name\",\n" // 查询：使用GROUP BY分组，计算每组的聚合值
        + "  \"adhoc\".my_sum3(\"empid\",\"deptno\",\"commission\") as p\n" // 调用三参数聚合函数
        + "from \"adhoc\".EMPLOYEES\n" // 从EMPLOYEES表中查询
        + "group by \"name\"") // 按name分组
        .returnsUnordered("name=Theodore; P=0", // 验证结果，Theodore组的聚合值为0
            "name=Eric; P=220", // Eric组的聚合值为220
            "name=Bill; P=110", // Bill组的聚合值为110
            "name=Sebastian; P=0"); // Sebastian组的聚合值为0
    // implicit type coercion.
    // 注释：隐式类型转换
    with.query("select \"adhoc\".my_sum3(\"empid\",\"deptno\",\"salary\") as p\n" // 查询：第三个参数是salary字段（double类型）
        + "from \"adhoc\".EMPLOYEES\n"); // 从EMPLOYEES表中查询（不验证结果）
    with.query("select \"adhoc\".my_sum3(\"empid\",\"deptno\",\"name\") as p\n" // 查询：第三个参数是name字段（字符串类型）
        + "from \"adhoc\".EMPLOYEES\n"); // 从EMPLOYEES表中查询（不验证结果）
    with.query("select \"adhoc\".my_sum2(\"commission\",250) as p\n" // 查询：调用两参数聚合函数，第二个参数是常量250
        + "from \"adhoc\".EMPLOYEES\n") // 从EMPLOYEES表中查询
        .returns("P=1500\n"); // 验证结果，commission+250的总和为1500
    // implicit type coercion.
    // 注释：隐式类型转换
    with.query("select \"adhoc\".my_sum2(\"name\",250) as p\n" // 查询：第一个参数是name字段（字符串类型），期望数字类型
        + "from \"adhoc\".EMPLOYEES\n") // 从EMPLOYEES表中查询
        .throws_("java.lang.NumberFormatException: For input string: \"Bill\""); // 期望抛出异常，因为字符串无法转换为数字
    // implicit type coercion.
    // 注释：隐式类型转换
    with.query("select \"adhoc\".my_sum2(\"empid\",0.0) as p\n" // 查询：第二个参数是0.0（double类型），期望int类型
        + "from \"adhoc\".EMPLOYEES\n") // 从EMPLOYEES表中查询
        .returns("P=560\n"); // 验证结果，empid+0.0的总和为560（隐式类型转换成功）
  } // testUserDefinedAggregateFunctionWithMultipleParameters方法结束

  /** Test for
   * {@link org.apache.calcite.runtime.CalciteResource#firstParameterOfAdd(String)}. */
  // 测试方法注释：测试聚合函数的add方法第一个参数必须是累加器
  // 该测试关联CalciteResource#firstParameterOfAdd方法
  // 验证Calcite能够正确检测并报告聚合函数add方法参数顺序的错误
  // 聚合函数的标准实现要求：add方法的第一个参数必须是累加器（init方法的返回类型）
  @Test void testUserDefinedAggregateFunction3() { // testUserDefinedAggregateFunction3方法：测试聚合函数add方法的参数顺序
    String message = "Caused by: java.lang.RuntimeException: In user-defined " // message变量：期望的错误消息字符串
        + "aggregate class 'org.apache.calcite.util.Smalls$SumFunctionBadIAdd'" // 指明是哪个聚合类
        + ", first parameter to 'add' method must be the accumulator (the " // 要求add方法的第一个参数必须是累加器
        + "return type of the 'init' method)"; // 累加器是init方法的返回类型
    withBadUdf(Smalls.SumFunctionBadIAdd.class).connectThrows(message); // 调用withBadUdf方法创建测试环境并验证连接时抛出指定错误消息
  } // testUserDefinedAggregateFunction3方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1434">[CALCITE-1434]
   * AggregateFunctionImpl doesnt work if the class implements a generic
   * interface</a>. */
  // 测试方法注释：测试实现泛型接口的聚合函数类
  // 该测试关联JIRA问题CALCITE-1434，验证Calcite能够正确处理实现泛型接口的聚合函数类
  // 泛型接口可能会导致类型擦除问题，该测试确保Calcite能够正确处理这种情况
  @Test void testUserDefinedAggregateFunctionImplementsInterface() { // testUserDefinedAggregateFunctionImplementsInterface方法：测试实现泛型接口的聚合函数
    final String empDept = JdbcTest.EmpDeptTableFactory.class.getName(); // empDept变量：获取EmpDeptTableFactory类的完整名称
    final String mySum3 = Smalls.MySum3.class.getName(); // mySum3变量：获取MySum3类的完整名称（实现泛型接口的聚合函数）
    final String model = "{\n" // model变量：JSON格式的模型配置字符串
        + "  version: '1.0',\n" // 版本号1.0
        + "   schemas: [\n" // schema列表开始
        + "     {\n" // schema对象开始
        + "       name: 'adhoc',\n" // schema名称为'adhoc'
        + "       tables: [\n" // 表列表开始
        + "         {\n" // 表对象开始
        + "           name: 'EMPLOYEES',\n" // 表名称为'EMPLOYEES'
        + "           type: 'custom',\n" // 表类型为'custom'
        + "           factory: '" + empDept + "',\n" // 自定义表工厂类名
        + "           operand: {'foo': true, 'bar': 345}\n" // 传递给工厂的参数
        + "         }\n" // 表对象结束
        + "       ],\n" // 表列表结束
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM3',\n" // 函数名称为'MY_SUM3'
        + "           className: '" + mySum3 + "'\n" // 实现该函数的Java类名（实现泛型接口）
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     }\n" // schema对象结束
        + "   ]\n" // schema列表结束
        + "}"; // JSON模型配置结束
    final CalciteAssert.AssertThat with = CalciteAssert.model(model) // with变量：根据模型配置创建CalciteAssert.AssertThat对象
        .withDefaultSchema("adhoc"); // 设置默认schema为'adhoc'

    with.query("select my_sum3(\"deptno\") as p from EMPLOYEES\n") // 查询：在默认schema下调用聚合函数
        .returns("P=50\n"); // 验证结果，deptno总和为50
    with.withDefaultSchema(null) // 清除默认schema
        .query("select \"adhoc\".my_sum3(\"deptno\") as p\n" // 查询：使用完全限定名调用聚合函数
            + "from \"adhoc\".EMPLOYEES\n") // 从adhoc schema的EMPLOYEES表中查询
        .returns("P=50\n"); // 验证结果，deptno总和为50
    with.query("select my_sum3(\"empid\"), \"deptno\" as p from EMPLOYEES\n") // 查询：使用聚合函数但SELECT中有非聚合列
        .throws_("Expression 'deptno' is not being grouped"); // 期望抛出异常，报告deptno列没有被分组
    with.query("select my_sum3(\"deptno\") as p from EMPLOYEES\n") // 查询：在默认schema下调用聚合函数
        .returns("P=50\n"); // 验证结果，deptno总和为50
    // implicit type coercion.
    // 注释：隐式类型转换
    with.query("select my_sum3(\"name\") as p from EMPLOYEES\n") // 查询：尝试对字符串字段求和
        .throws_("java.lang.NumberFormatException: For input string: \"Bill\""); // 期望抛出异常，因为字符串无法转换为数字
    with.query("select my_sum3(\"deptno\", 1) as p from EMPLOYEES\n") // 查询：提供两个参数
        .throws_("No match found for function signature " // 期望抛出异常
            + "MY_SUM3(<NUMERIC>, <NUMERIC>)"); // 错误消息：找不到匹配的函数签名
    with.query("select my_sum3() as p from EMPLOYEES\n") // 查询：不提供任何参数
        .throws_("No match found for function signature MY_SUM3()"); // 期望抛出异常，找不到匹配的函数签名
    with.query("select \"deptno\", my_sum3(\"deptno\") as p from EMPLOYEES\n" // 查询：使用GROUP BY分组，计算每组的deptno总和
        + "group by \"deptno\"") // 按deptno分组
        .returnsUnordered("deptno=20; P=20", // 验证结果（不保证顺序）
            "deptno=10; P=30"); // deptno=20组的总和为20，deptno=10组的总和为30
  } // testUserDefinedAggregateFunctionImplementsInterface方法结束

  private static CalciteAssert.AssertThat withBadUdf(Class<?> clazz) { // withBadUdf方法：创建一个配置了有问题的UDF的测试环境，用于测试错误处理
    final String empDept = JdbcTest.EmpDeptTableFactory.class.getName(); // empDept变量：获取EmpDeptTableFactory类的完整名称
    final String className = clazz.getName(); // className变量：获取传入的类的完整名称
    return CalciteAssert.model("{\n" // 返回：根据模型配置创建CalciteAssert.AssertThat对象
        + "  version: '1.0',\n" // 版本号1.0
        + "   schemas: [\n" // schema列表开始
        + "     {\n" // schema对象开始
        + "       name: 'adhoc',\n" // schema名称为'adhoc'
        + "       tables: [\n" // 表列表开始
        + "         {\n" // 表对象开始
        + "           name: 'EMPLOYEES',\n" // 表名称为'EMPLOYEES'
        + "           type: 'custom',\n" // 表类型为'custom'
        + "           factory: '" + empDept + "',\n" // 自定义表工厂类名
        + "           operand: {'foo': true, 'bar': 345}\n" // 传递给工厂的参数
        + "         }\n" // 表对象结束
        + "       ],\n" // 表列表结束
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'AWKWARD',\n" // 函数名称为'AWKWARD'（表示这是一个有问题的函数）
        + "           className: '" + className + "'\n" // 实现该函数的Java类名（传入的类）
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     }\n" // schema对象结束
        + "   ]\n" // schema列表结束
        + "}") // JSON模型配置结束
        .withDefaultSchema("adhoc"); // 设置默认schema为'adhoc'
  } // withBadUdf方法结束

  /** Tests user-defined aggregate function with FILTER.
   *
   * <p>Also tests that we do not try to push ADAF to JDBC source. */
  // 测试方法注释：测试带有FILTER子句的用户定义聚合函数
  // FILTER子句允许在聚合时过滤行，只对满足条件的行进行聚合
  // 该测试还验证Calcite不会尝试将聚合函数下推到JDBC源（ADAF = Aggregate Defined As Function）
  @Test void testUserDefinedAggregateFunctionWithFilter() { // testUserDefinedAggregateFunctionWithFilter方法：测试带FILTER子句的聚合函数
    final String sum = Smalls.MyStaticSumFunction.class.getName(); // sum变量：获取MyStaticSumFunction类的完整名称
    final String sum2 = Smalls.MySumFunction.class.getName(); // sum2变量：获取MySumFunction类的完整名称
    final CalciteAssert.AssertThat with = CalciteAssert.model("{\n" // with变量：创建包含自定义聚合函数的模型配置
        + "  version: '1.0',\n" // 版本号1.0
        + "   schemas: [\n" // schema列表开始
        + JdbcTest.SCOTT_SCHEMA // 包含Scott schema（经典的emp和dept表）
        + ",\n" // schema分隔符
        + "     {\n" // schema对象开始
        + "       name: 'adhoc',\n" // schema名称为'adhoc'
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM',\n" // 函数名称为'MY_SUM'
        + "           className: '" + sum + "'\n" // 实现该函数的Java类名
        + "         },\n" // 函数对象结束
        + "         {\n" // 函数对象开始
        + "           name: 'MY_SUM2',\n" // 函数名称为'MY_SUM2'
        + "           className: '" + sum2 + "'\n" // 实现该函数的Java类名
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     }\n" // schema对象结束
        + "   ]\n" // schema列表结束
        + "}") // JSON模型配置结束
        .withDefaultSchema("adhoc"); // 设置默认schema为'adhoc'
    with.query("select deptno, \"adhoc\".my_sum(deptno) as p\n" // 查询：按deptno分组，计算每组的deptno总和
        + "from scott.emp\n" // 从scott schema的emp表中查询
        + "group by deptno\n") // 按deptno分组
        .returns( // 验证结果
            "DEPTNO=20; P=100\n" // deptno=20组的总和为100
                + "DEPTNO=10; P=30\n" // deptno=10组的总和为30
                + "DEPTNO=30; P=180\n"); // deptno=30组的总和为180

    with.query("select deptno,\n" // 查询：按deptno分组，使用FILTER子句过滤不同条件的聚合
        + "  \"adhoc\".my_sum(deptno) filter (where job = 'CLERK') as c,\n" // 只对job='CLERK'的行求和
        + "  \"adhoc\".my_sum(deptno) filter (where job = 'XXX') as x\n" // 只对job='XXX'的行求和（没有这样的行）
        + "from scott.emp\n" // 从scott schema的emp表中查询
        + "group by deptno\n") // 按deptno分组
        .returns( // 验证结果
            "DEPTNO=20; C=40; X=0\n" // deptno=20组，CLERK的总和为40，XXX的总和为0
                + "DEPTNO=10; C=10; X=0\n" // deptno=10组，CLERK的总和为10，XXX的总和为0
                + "DEPTNO=30; C=30; X=0\n"); // deptno=30组，CLERK的总和为30，XXX的总和为0
  } // testUserDefinedAggregateFunctionWithFilter方法结束

  /** Tests resolution of functions using schema paths. */
  // 测试方法注释：测试使用schema路径解析函数
  // Schema路径允许一个schema继承或引用其他schema中的函数
  // 该测试验证Calcite能够正确处理：
  // 1. 默认schema中的函数调用
  // 2. 使用完全限定名调用其他schema中的函数
  // 3. 通过path属性继承其他schema的函数
  @Test void testPath() { // testPath方法：测试schema路径解析函数
    final String name = Smalls.MyPlusFunction.class.getName(); // name变量：获取MyPlusFunction类的完整名称
    final CalciteAssert.AssertThat with = CalciteAssert.model("{\n" // with变量：创建包含多个schema的模型配置
        + "  version: '1.0',\n" // 版本号1.0
        + "   schemas: [\n" // schema列表开始
        + "     {\n" // schema对象开始
        + "       name: 'adhoc',\n" // schema名称为'adhoc'
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'MY_PLUS',\n" // 函数名称为'MY_PLUS'
        + "           className: '" + name + "'\n" // 实现该函数的Java类名
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     },\n" // schema对象结束
        + "     {\n" // schema对象开始
        + "       name: 'adhoc2',\n" // schema名称为'adhoc2'
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'MY_PLUS2',\n" // 函数名称为'MY_PLUS2'
        + "           className: '" + name + "'\n" // 实现该函数的Java类名
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     },\n" // schema对象结束
        + "     {\n" // schema对象开始
        + "       name: 'adhoc3',\n" // schema名称为'adhoc3'
        + "       path: ['adhoc2','adhoc3'],\n" // path字段：指定schema路径，adhoc3可以访问adhoc2的函数
        + "       functions: [\n" // 函数列表开始
        + "         {\n" // 函数对象开始
        + "           name: 'MY_PLUS3',\n" // 函数名称为'MY_PLUS3'
        + "           className: '" + name + "'\n" // 实现该函数的Java类名
        + "         }\n" // 函数对象结束
        + "       ]\n" // 函数列表结束
        + "     }\n" // schema对象结束
        + "   ]\n" // schema列表结束
        + "}"); // JSON模型配置结束

    final String err = "No match found for function signature"; // err变量：期望的错误消息前缀
    final String res = "EXPR$0=2\n"; // res变量：期望的正确结果（1+1=2）

    // adhoc can see own function MY_PLUS but not adhoc2.MY_PLUS2 unless
    // qualified
    // 注释：adhoc可以看到自己的函数MY_PLUS，但看不到adhoc2.MY_PLUS2，除非使用完全限定名
    final CalciteAssert.AssertThat adhoc = with.withDefaultSchema("adhoc"); // adhoc变量：设置默认schema为'adhoc'
    adhoc.query("values MY_PLUS(1, 1)").returns(res); // 查询：调用adhoc自己的MY_PLUS函数，成功
    adhoc.query("values MY_PLUS2(1, 1)").throws_(err); // 查询：尝试调用MY_PLUS2函数，失败（不在当前schema中）
    adhoc.query("values \"adhoc2\".MY_PLUS(1, 1)").throws_(err); // 查询：尝试调用adhoc2.MY_PLUS函数，失败（adhoc2中没有MY_PLUS）
    adhoc.query("values \"adhoc2\".MY_PLUS2(1, 1)").returns(res); // 查询：使用完全限定名调用adhoc2.MY_PLUS2函数，成功

    // adhoc2 can see own function MY_PLUS2 but not adhoc2.MY_PLUS unless
    // qualified
    // 注释：adhoc2可以看到自己的函数MY_PLUS2，但看不到adhoc.MY_PLUS，除非使用完全限定名
    final CalciteAssert.AssertThat adhoc2 = with.withDefaultSchema("adhoc2"); // adhoc2变量：设置默认schema为'adhoc2'
    adhoc2.query("values MY_PLUS2(1, 1)").returns(res); // 查询：调用adhoc2自己的MY_PLUS2函数，成功
    adhoc2.query("values MY_PLUS(1, 1)").throws_(err); // 查询：尝试调用MY_PLUS函数，失败（不在当前schema中）
    adhoc2.query("values \"adhoc\".MY_PLUS(1, 1)").returns(res); // 查询：使用完全限定名调用adhoc.MY_PLUS函数，成功

    // adhoc3 can see own adhoc2.MY_PLUS2 because in path, with or without
    // qualification, but can only see adhoc.MY_PLUS with qualification
    // 注释：adhoc3可以看到adhoc2.MY_PLUS2（因为在path中），无论是否使用限定名，但只能使用限定名看到adhoc.MY_PLUS
    final CalciteAssert.AssertThat adhoc3 = with.withDefaultSchema("adhoc3"); // adhoc3变量：设置默认schema为'adhoc3'
    adhoc3.query("values MY_PLUS2(1, 1)").returns(res); // 查询：调用adhoc2的MY_PLUS2函数（通过path），成功
    adhoc3.query("values MY_PLUS(1, 1)").throws_(err); // 查询：尝试调用MY_PLUS函数，失败（不在path中）
    adhoc3.query("values \"adhoc\".MY_PLUS(1, 1)").returns(res); // 查询：使用完全限定名调用adhoc.MY_PLUS函数，成功
  } // testPath方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-986">[CALCITE-986]
   * User-defined function with Date or Timestamp parameters</a>. */
  // 测试方法注释：测试具有Date或Timestamp参数的用户定义函数
  // 该测试关联JIRA问题CALCITE-986，验证Calcite能够正确处理日期时间类型的参数
  // 测试内容包括：
  // 1. DATE类型参数的处理
  // 2. TIME类型参数的处理
  // 3. TIMESTAMP类型参数的处理
  // 4. null值的处理
  @Test void testDate() { // testDate方法：测试日期时间类型参数的UDF
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query("values \"adhoc\".\"dateFun\"(DATE '1970-01-01')") // 查询：调用dateFun函数，参数是DATE '1970-01-01'
        .returnsValue("0"); // 验证结果，返回0（1970-01-01距离epoch的毫秒数为0）
    with.query("values \"adhoc\".\"dateFun\"(DATE '1970-01-02')") // 查询：调用dateFun函数，参数是DATE '1970-01-02'
        .returnsValue("86400000"); // 验证结果，返回86400000（一天的毫秒数）
    with.query("values \"adhoc\".\"dateFun\"(cast(null as date))") // 查询：调用dateFun函数，参数是null
        .returnsValue("-1"); // 验证结果，返回-1（null值的特殊处理）
    with.query("values \"adhoc\".\"timeFun\"(TIME '00:00:00')") // 查询：调用timeFun函数，参数是TIME '00:00:00'
        .returnsValue("0"); // 验证结果，返回0（午夜0点距离epoch的毫秒数为0）
    with.query("values \"adhoc\".\"timeFun\"(TIME '00:01:30')") // 查询：调用timeFun函数，参数是TIME '00:01:30'
        .returnsValue("90000"); // 验证结果，返回90000（1分30秒的毫秒数）
    with.query("values \"adhoc\".\"timeFun\"(cast(null as time))") // 查询：调用timeFun函数，参数是null
        .returnsValue("-1"); // 验证结果，返回-1（null值的特殊处理）
    with.query("values \"adhoc\".\"timestampFun\"(TIMESTAMP '1970-01-01 00:00:00')") // 查询：调用timestampFun函数，参数是TIMESTAMP '1970-01-01 00:00:00'
        .returnsValue("0"); // 验证结果，返回0（epoch时间点）
    with.query("values \"adhoc\".\"timestampFun\"(TIMESTAMP '1970-01-02 00:01:30')") // 查询：调用timestampFun函数，参数是TIMESTAMP '1970-01-02 00:01:30'
        .returnsValue("86490000"); // 验证结果，返回86490000（1天1分30秒的毫秒数）
    with.query("values \"adhoc\".\"timestampFun\"(cast(null as timestamp))") // 查询：调用timestampFun函数，参数是null
        .returnsValue("-1"); // 验证结果，返回-1（null值的特殊处理）
  } // testDate方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1041">[CALCITE-1041]
   * User-defined function returns DATE or TIMESTAMP value</a>. */
  // 测试方法注释：测试返回DATE或TIMESTAMP值的用户定义函数
  // 该测试关联JIRA问题CALCITE-1041，验证Calcite能够正确处理返回日期时间类型的UDF
  // 测试内容包括：
  // 1. 返回DATE类型的函数
  // 2. 返回TIME类型的函数
  // 3. 返回TIMESTAMP类型的函数
  // 4. null值的处理
  @Test void testReturnDate() { // testReturnDate方法：测试返回日期时间类型的UDF
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query("values \"adhoc\".\"toDateFun\"(0)") // 查询：调用toDateFun函数，参数是0
        .returnsValue("1970-01-01"); // 验证结果，返回1970-01-01（epoch日期）
    with.query("values \"adhoc\".\"toDateFun\"(1)") // 查询：调用toDateFun函数，参数是1
        .returnsValue("1970-01-02"); // 验证结果，返回1970-01-02（1天后）
    with.query("values \"adhoc\".\"toDateFun\"(cast(null as bigint))") // 查询：调用toDateFun函数，参数是null
        .returnsValue(null); // 验证结果，返回null
    with.query("values \"adhoc\".\"toTimeFun\"(0)") // 查询：调用toTimeFun函数，参数是0
        .returnsValue("00:00:00"); // 验证结果，返回00:00:00（午夜0点）
    with.query("values \"adhoc\".\"toTimeFun\"(90000)") // 查询：调用toTimeFun函数，参数是90000
        .returnsValue("00:01:30"); // 验证结果，返回00:01:30（1分30秒）
    with.query("values \"adhoc\".\"toTimeFun\"(cast(null as bigint))") // 查询：调用toTimeFun函数，参数是null
        .returnsValue(null); // 验证结果，返回null
    with.query("values \"adhoc\".\"toTimestampFun\"(0)") // 查询：调用toTimestampFun函数，参数是0
        .returnsValue("1970-01-01 00:00:00"); // 验证结果，返回1970-01-01 00:00:00（epoch时间点）
    with.query("values \"adhoc\".\"toTimestampFun\"(86490000)") // 查询：调用toTimestampFun函数，参数是86490000
        .returnsValue("1970-01-02 00:01:30"); // 验证结果，返回1970-01-02 00:01:30（1天1分30秒后）
    with.query("values \"adhoc\".\"toTimestampFun\"(cast(null as bigint))") // 查询：调用toTimestampFun函数，参数是null
        .returnsValue(null); // 验证结果，返回null
  } // testReturnDate方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1881">[CALCITE-1881]
   * Can't distinguish overloaded user-defined functions that have DATE and
   * TIMESTAMP arguments</a>. */
  // 测试方法注释：测试具有DATE和TIMESTAMP参数的重载用户定义函数
  // 该测试关联JIRA问题CALCITE-1881，验证Calcite能够正确区分DATE和TIMESTAMP参数的重载函数
  // 测试内容包括：
  // 1. DATE类型的处理
  // 2. TIMESTAMP类型的处理
  // 3. TIME类型的处理
  // 4. 不同时间值的正确转换
  @Test void testDateAndTimestamp() { // testDateAndTimestamp方法：测试日期时间类型重载函数
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query("values \"adhoc\".\"toLong\"(DATE '1970-01-15')") // 查询：调用toLong函数，参数是DATE '1970-01-15'
        .returns("EXPR$0=1209600000\n"); // 验证结果，返回1209600000（14天的毫秒数）
    with.query("values \"adhoc\".\"toLong\"(DATE '2002-08-11')") // 查询：调用toLong函数，参数是DATE '2002-08-11'
        .returns("EXPR$0=1029024000000\n"); // 验证结果，返回1029024000000（从1970-01-01到2002-08-11的毫秒数）
    with.query("values \"adhoc\".\"toLong\"(DATE '2003-04-11')") // 查询：调用toLong函数，参数是DATE '2003-04-11'
        .returns("EXPR$0=1050019200000\n"); // 验证结果，返回1050019200000（从1970-01-01到2003-04-11的毫秒数）
    with.query("values \"adhoc\".\"toLong\"(TIMESTAMP '2003-04-11 00:00:00')") // 查询：调用toLong函数，参数是TIMESTAMP '2003-04-11 00:00:00'
        .returns("EXPR$0=1050019200000\n"); // 验证结果，返回1050019200000（与DATE '2003-04-11'相同，因为时间是00:00:00）
    with.query("values \"adhoc\".\"toLong\"(TIMESTAMP '2003-04-11 00:00:06')") // 查询：调用toLong函数，参数是TIMESTAMP '2003-04-11 00:00:06'
        .returns("EXPR$0=1050019206000\n"); // 验证结果，返回1050019206000（比00:00:00多6秒）
    with.query("values \"adhoc\".\"toLong\"(TIMESTAMP '2003-04-18 01:20:00')") // 查询：调用toLong函数，参数是TIMESTAMP '2003-04-18 01:20:00'
        .returns("EXPR$0=1050628800000\n"); // 验证结果，返回1050628800000（从epoch到该时间点的毫秒数）
    with.query("values \"adhoc\".\"toLong\"(TIME '00:20:00')") // 查询：调用toLong函数，参数是TIME '00:20:00'
        .returns("EXPR$0=1200000\n"); // 验证结果，返回1200000（20分钟的毫秒数）
    with.query("values \"adhoc\".\"toLong\"(TIME '00:20:10')") // 查询：调用toLong函数，参数是TIME '00:20:10'
        .returns("EXPR$0=1210000\n"); // 验证结果，返回1210000（20分10秒的毫秒数）
    with.query("values \"adhoc\".\"toLong\"(TIME '01:20:00')") // 查询：调用toLong函数，参数是TIME '01:20:00'
        .returns("EXPR$0=4800000\n"); // 验证结果，返回4800000（1小时20分钟的毫秒数）
  } // testDateAndTimestamp方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2053">[CALCITE-2053]
   * Overloaded user-defined functions that have Double and BigDecimal arguments
   * will goes wrong </a>. */
  // 测试方法注释：测试具有Double和BigDecimal参数的重载用户定义函数
  // 该测试关联JIRA问题CALCITE-2053，验证Calcite能够正确处理Double和BigDecimal类型的重载函数
  // 测试内容包括：
  // 1. Double类型的处理
  // 2. BigDecimal（decimal）类型的处理
  // 3. Float类型的处理
  // 4. Integer类型的隐式转换
  @Test void testBigDecimalAndLong() { // testBigDecimalAndLong方法：测试Double和BigDecimal类型重载函数
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query("values \"adhoc\".\"toDouble\"(cast(1.0 as double))") // 查询：调用toDouble函数，参数是1.0（double类型）
            .returns("EXPR$0=1.0\n"); // 验证结果，返回1.0
    with.query("values \"adhoc\".\"toDouble\"(cast(1.0 as decimal))") // 查询：调用toDouble函数，参数是1.0（decimal类型）
            .returns("EXPR$0=1.0\n"); // 验证结果，返回1.0
    with.query("values \"adhoc\".\"toDouble\"(cast(1 as double))") // 查询：调用toDouble函数，参数是1转换为double
            .returns("EXPR$0=1.0\n"); // 验证结果，返回1.0
    with.query("values \"adhoc\".\"toDouble\"(cast(1 as decimal))") // 查询：调用toDouble函数，参数是1转换为decimal
            .returns("EXPR$0=1.0\n"); // 验证结果，返回1.0
    with.query("values \"adhoc\".\"toDouble\"(cast(1 as float))") // 查询：调用toDouble函数，参数是1转换为float
            .returns("EXPR$0=1.0\n"); // 验证结果，返回1.0
    with.query("values \"adhoc\".\"toDouble\"(cast(1.0 as float))") // 查询：调用toDouble函数，参数是1.0转换为float
            .returns("EXPR$0=1.0\n"); // 验证结果，返回1.0
  } // testBigDecimalAndLong方法结束

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1041">[CALCITE-1041]
   * User-defined function returns DATE or TIMESTAMP value</a>. */
  // 测试方法注释：测试在WHERE子句中使用返回TIMESTAMP值的用户定义函数
  // 该测试关联JIRA问题CALCITE-1041，验证Calcite能够正确处理返回日期时间类型的UDF在IN子句中的使用
  // 测试内容包括：
  // 1. 使用CAST转换字符串为TIMESTAMP
  * 2. 使用TIMESTAMP字面量
  * 3. 使用字符串隐式转换为TIMESTAMP
  @Test void testReturnDate2() { // testReturnDate2方法：测试返回TIMESTAMP的UDF在WHERE子句中的使用
    final CalciteAssert.AssertThat with = withUdf(); // with变量：获取配置了UDF的测试环境
    with.query("select * from (values 0) as t(c)\n" // 查询：从值表中选择，c=0
        + "where \"adhoc\".\"toTimestampFun\"(c) in (\n" // WHERE子句：调用toTimestampFun(0)并在IN列表中比较
        + "  cast('1970-01-01 00:00:00' as timestamp),\n" // IN列表的第一个值，使用CAST转换为timestamp
        + "  cast('1997-02-01 00:00:00' as timestamp))") // IN列表的第二个值，使用CAST转换为timestamp
        .returnsValue("0"); // 验证结果，返回0（因为toTimestampFun(0)='1970-01-01 00:00:00'在IN列表中）
    with.query("select * from (values 0) as t(c)\n" // 查询：从值表中选择，c=0
        + "where \"adhoc\".\"toTimestampFun\"(c) in (\n" // WHERE子句：调用toTimestampFun(0)并在IN列表中比较
        + "  timestamp '1970-01-01 00:00:00',\n" // IN列表的第一个值，使用TIMESTAMP字面量
        + "  timestamp '1997-02-01 00:00:00')") // IN列表的第二个值，使用TIMESTAMP字面量
        .returnsValue("0"); // 验证结果，返回0（因为toTimestampFun(0)='1970-01-01 00:00:00'在IN列表中）
    with.query("select * from (values 0) as t(c)\n" // 查询：从值表中选择，c=0
        + "where \"adhoc\".\"toTimestampFun\"(c) in (\n" // WHERE子句：调用toTimestampFun(0)并在IN列表中比较
        + "  '1970-01-01 00:00:00',\n" // IN列表的第一个值，使用字符串（隐式转换为timestamp）
        + "  '1997-02-01 00:00:00')") // IN列表的第二个值，使用字符串（隐式转换为timestamp）
        .returnsValue("0"); // 验证结果，返回0（因为toTimestampFun(0)='1970-01-01 00:00:00'在IN列表中）
  } // testReturnDate2方法结束

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1834">[CALCITE-1834]
   * User-defined function for Arrays</a>.
   */
  // 测试方法注释：测试数组类型的用户定义函数
  // 该测试关联JIRA问题CALCITE-1834，验证Calcite能够正确处理数组类型的UDF
  // 测试内容包括：
  // 1. 数组类型的参数
  * 2. 数组类型的返回值
  * 3. 数组函数的重载（Integer数组和Double数组）
  @Test void testArrayUserDefinedFunction() throws Exception { // testArrayUserDefinedFunction方法：测试数组类型的UDF，可能抛出异常
    try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) { // connection变量：创建Calcite数据库连接，使用try-with-resources自动关闭
      CalciteConnection calciteConnection = // calciteConnection变量：将普通连接解包为CalciteConnection
          connection.unwrap(CalciteConnection.class); // 通过unwrap方法获取底层CalciteConnection对象
      SchemaPlus rootSchema = calciteConnection.getRootSchema(); // rootSchema变量：获取根schema
      rootSchema.add("hr", new ReflectiveSchema(new HrSchema())); // 在根schema中添加名为"hr"的schema，基于HrSchema对象

      SchemaPlus post = rootSchema.add("POST", new AbstractSchema()); // post变量：在根schema中添加名为"POST"的空schema
      post.add("ARRAY_APPEND", new ArrayAppendDoubleFunction()); // 在POST schema中添加ARRAY_APPEND函数（Double数组版本）
      post.add("ARRAY_APPEND", new ArrayAppendIntegerFunction()); // 在POST schema中添加ARRAY_APPEND函数（Integer数组版本，重载）
      final String sql = "select \"empid\" as EMPLOYEE_ID,\n" // sql变量：SQL查询语句
          + "  \"name\" || ' ' || \"name\" as EMPLOYEE_NAME,\n" // 将name字段与自身拼接
          + "  \"salary\" as EMPLOYEE_SALARY,\n" // 薪资字段
          + "  POST.ARRAY_APPEND(ARRAY[1,2,3], \"deptno\") as DEPARTMENTS\n" // 调用ARRAY_APPEND函数，将deptno追加到数组[1,2,3]
          + "from \"hr\".\"emps\""; // 从hr schema的emps表中查询

      final String result = "" // result变量：期望的查询结果字符串
          + "EMPLOYEE_ID=100; EMPLOYEE_NAME=Bill Bill;" // 第一行：员工ID 100，姓名Bill Bill，薪资10000.0，部门数组[1, 2, 3, 10]
          + " EMPLOYEE_SALARY=10000.0; DEPARTMENTS=[1, 2, 3, 10]\n"
          + "EMPLOYEE_ID=200; EMPLOYEE_NAME=Eric Eric;" // 第二行：员工ID 200，姓名Eric Eric，薪资8000.0，部门数组[1, 2, 3, 20]
          + " EMPLOYEE_SALARY=8000.0; DEPARTMENTS=[1, 2, 3, 20]\n"
          + "EMPLOYEE_ID=150; EMPLOYEE_NAME=Sebastian Sebastian;" // 第三行：员工ID 150，姓名Sebastian Sebastian，薪资7000.0，部门数组[1, 2, 3, 10]
          + " EMPLOYEE_SALARY=7000.0; DEPARTMENTS=[1, 2, 3, 10]\n"
          + "EMPLOYEE_ID=110; EMPLOYEE_NAME=Theodore Theodore;" // 第四行：员工ID 110，姓名Theodore Theodore，薪资11500.0，部门数组[1, 2, 3, 10]
          + " EMPLOYEE_SALARY=11500.0; DEPARTMENTS=[1, 2, 3, 10]\n";

      try (Statement statement = connection.createStatement(); // statement变量：创建SQL语句执行对象，使用try-with-resources自动关闭
           ResultSet resultSet = statement.executeQuery(sql)) { // resultSet变量：执行SQL查询并获取结果集
        assertThat(CalciteAssert.toString(resultSet), is(result)); // 断言：验证查询结果与期望值匹配
      } // try-with-resources自动关闭resultSet和statement
    } // try-with-resources自动关闭connection
  } // testArrayUserDefinedFunction方法结束

  /**
   * Base class for functions that append arrays.
   */
  // 内部类注释：数组追加函数的基类
  // 该抽象类实现了ScalarFunction和ImplementableFunction接口
  // 提供了数组追加函数的通用实现，子类只需指定具体的数组类型
  private abstract static class ArrayAppendScalarFunction // ArrayAppendScalarFunction类：数组追加函数的基类
      implements ScalarFunction, ImplementableFunction { // 实现ScalarFunction接口（标量函数）和ImplementableFunction接口（可实现函数）
    public List<FunctionParameter> getParameters() { // getParameters方法：获取函数参数列表
      final List<FunctionParameter> parameters = new ArrayList<>(); // parameters变量：创建空的参数列表
      for (final Ord<RelProtoDataType> type : Ord.zip(getParams())) { // 遍历参数类型，使用Ord添加索引
        parameters.add( // 将参数添加到列表
            new FunctionParameter() { // 创建匿名FunctionParameter对象
              public int getOrdinal() { // getOrdinal方法：返回参数的序号（位置）
                return type.i; // 返回参数的索引
              }

              public String getName() { // getName方法：返回参数的名称
                return "arg" + type.i; // 返回参数名称，格式为"arg0", "arg1"等
              }

              public RelDataType getType(RelDataTypeFactory typeFactory) { // getType方法：返回参数的类型
                return type.e.apply(typeFactory); // 应用类型工厂获取实际类型
              }

              public boolean isOptional() { // isOptional方法：返回参数是否可选
                return false; // 返回false，表示参数不是可选的
              }
            }); // 匿名FunctionParameter对象结束
      } // for循环结束
      return parameters; // 返回参数列表
    } // getParameters方法结束

    protected abstract List<RelProtoDataType> getParams(); // getParams抽象方法：获取参数类型列表，由子类实现

    @Override public CallImplementor getImplementor() { // getImplementor方法：获取函数实现器
      return (translator, call, nullAs) -> { // 返回lambda表达式，实现CallImplementor接口
        Method lookupMethod = // lookupMethod变量：查找要调用的方法
            Types.lookupMethod(Smalls.AllTypesFunction.class, // 在AllTypesFunction类中查找
                "arrayAppendFun", List.class, Integer.class); // 方法名为arrayAppendFun，参数类型为List和Integer
        return Expressions.call(lookupMethod, // 返回方法调用表达式
            translator.translateList(call.getOperands(), nullAs)); // 翻译调用参数并传递给方法
      }; // lambda表达式结束
    } // getImplementor方法结束
  } // ArrayAppendScalarFunction类结束

  /** Function with signature "f(ARRAY OF INTEGER, INTEGER) returns ARRAY OF
   * INTEGER". */
  // 内部类注释：Integer数组追加函数
  // 该类实现了将Integer元素追加到Integer数组的功能
  // 函数签名：f(ARRAY OF INTEGER, INTEGER) returns ARRAY OF INTEGER
  private static class ArrayAppendIntegerFunction // ArrayAppendIntegerFunction类：Integer数组追加函数
      extends ArrayAppendScalarFunction { // 继承ArrayAppendScalarFunction基类
    @Override public RelDataType getReturnType(RelDataTypeFactory typeFactory) { // getReturnType方法：返回函数的返回类型
      return typeFactory.createArrayType( // 创建数组类型
          typeFactory.createSqlType(SqlTypeName.INTEGER), -1); // 元素类型为INTEGER，-1表示未知长度
    } // getReturnType方法结束

    @Override public List<RelProtoDataType> getParams() { // getParams方法：返回参数类型列表
      return ImmutableList.of( // 返回不可变列表
          typeFactory -> typeFactory.createArrayType( // 第一个参数：INTEGER数组类型
              typeFactory.createSqlType(SqlTypeName.INTEGER), -1), // 元素类型为INTEGER，-1表示未知长度
          typeFactory -> typeFactory.createSqlType(SqlTypeName.INTEGER)); // 第二个参数：INTEGER类型
    } // getParams方法结束
  } // ArrayAppendIntegerFunction类结束

  /** Function with signature "f(ARRAY OF DOUBLE, INTEGER) returns ARRAY OF
   * DOUBLE". */
  // 内部类注释：Double数组追加函数
  // 该类实现了将Integer元素追加到Double数组的功能
  // 注意：第二个参数是INTEGER类型，会被隐式转换为DOUBLE
  // 函数签名：f(ARRAY OF DOUBLE, INTEGER) returns ARRAY OF DOUBLE
  private static class ArrayAppendDoubleFunction // ArrayAppendDoubleFunction类：Double数组追加函数
      extends ArrayAppendScalarFunction { // 继承ArrayAppendScalarFunction基类
    public RelDataType getReturnType(RelDataTypeFactory typeFactory) { // getReturnType方法：返回函数的返回类型
      return typeFactory.createArrayType( // 创建数组类型
          typeFactory.createSqlType(SqlTypeName.DOUBLE), -1); // 元素类型为DOUBLE，-1表示未知长度
    } // getReturnType方法结束

    public List<RelProtoDataType> getParams() { // getParams方法：返回参数类型列表
      return ImmutableList.of( // 返回不可变列表
          typeFactory -> typeFactory.createArrayType( // 第一个参数：DOUBLE数组类型
              typeFactory.createSqlType(SqlTypeName.DOUBLE), -1), // 元素类型为DOUBLE，-1表示未知长度
          typeFactory -> typeFactory.createSqlType(SqlTypeName.INTEGER)); // 第二个参数：INTEGER类型（会隐式转换为DOUBLE）
    } // getParams方法结束
  } // ArrayAppendDoubleFunction类结束

} // UdfTest类结束
