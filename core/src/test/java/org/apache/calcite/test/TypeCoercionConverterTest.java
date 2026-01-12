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
package org.apache.calcite.test; // 声明包名，该类位于org.apache.calcite.test包下，这是Calcite项目的测试包

import org.apache.calcite.sql.validate.implicit.TypeCoercion; // 导入TypeCoercion类，用于隐式类型转换的核心接口

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值
import org.junit.jupiter.api.AfterAll; // 导入AfterAll注解，用于在所有测试方法执行后执行的方法
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

/**
 * Test cases for implicit type coercion converter. See {@link TypeCoercion} doc
 * or <a href="https://docs.google.com/spreadsheets/d/1GhleX5h5W8-kJKh7NMJ4vtoE78pwfaZRJl88ULX_MgU/edit?usp=sharing">CalciteImplicitCasts</a>
 * for conversion details.
 * See {@link RelOptRulesTest} for an explanation of how to add tests.
 */
// 本类是隐式类型转换转换器的测试用例类，用于测试Calcite中的隐式类型转换功能
// 继承自SqlToRelTestBase基类，该基类提供了SQL到关系代数转换测试的基础功能
class TypeCoercionConverterTest extends SqlToRelTestBase { // 定义测试类，继承SqlToRelTestBase以获得SQL测试能力

  // 定义静态常量FIXTURE，这是SqlToRelFixture类型的测试装置对象
  // SqlToRelFixture是SqlToRelTestBase的测试装置，用于配置和执行SQL到关系代数的转换测试
  protected static final SqlToRelFixture FIXTURE = // 声明受保护的静态最终常量FIXTURE，用于测试装置的配置
      SqlToRelFixture.DEFAULT // 从默认的SqlToRelFixture开始配置
          .withDiffRepos(DiffRepository.lookup(TypeCoercionConverterTest.class)) // 设置差异仓库，用于比较实际输出和预期输出
          .withFactory(f -> f.withCatalogReader(TCatalogReader::create)) // 配置工厂方法，使用TCatalogReader创建目录读取器
          .withDecorrelate(false); // 设置为不进行去相关化处理

  @Nullable // 使用Nullable注解标记该字段可能为null
  private static DiffRepository diffRepos = null; // 声明静态的DiffRepository字段，用于存储差异仓库实例，初始为null

  @AfterAll // 使用AfterAll注解，表示该方法在所有测试方法执行完毕后执行一次
  public static void checkActualAndReferenceFiles() { // 定义静态方法，用于检查实际输出文件和参考文件是否一致
    if (diffRepos != null) { // 如果diffRepos不为null
      diffRepos.checkActualAndReferenceFiles(); // 调用差异仓库的检查方法，验证实际输出与预期输出是否匹配
    }
  }

  @Override // 使用Override注解，表示重写父类的方法
  public SqlToRelFixture fixture() { // 重写fixture方法，返回测试装置对象
    diffRepos = FIXTURE.diffRepos(); // 从FIXTURE中获取差异仓库并赋值给静态字段diffRepos
    return FIXTURE; // 返回配置好的FIXTURE测试装置
  }

  /** Test case for {@link TypeCoercion#commonTypeForBinaryComparison}. */
  // 测试方法，测试二元比较操作中的隐式类型转换，调用TypeCoercion的commonTypeForBinaryComparison方法
  @Test void testBinaryComparison() { // 定义测试方法，测试二元比较的类型转换
    // for constant cast, there is reduce rule // 注释说明：对于常量转换，有简化规则
    sql("select\n" // 执行SQL查询，测试各种比较操作的类型转换
        + "1<'1' as f0,\n" // 测试整数和字符串的小于比较，期望隐式转换
        + "1<='1' as f1,\n" // 测试整数和字符串的小于等于比较
        + "1>'1' as f2,\n" // 测试整数和字符串的大于比较
        + "1>='1' as f3,\n" // 测试整数和字符串的大于等于比较
        + "1='1' as f4,\n" // 测试整数和字符串的等于比较
        + "t1_date > t1_timestamp as f5,\n" // 测试日期和时间戳的比较
        + "'2' is not distinct from 2 as f6,\n" // 测试字符串和整数的IS NOT DISTINCT FROM比较
        + "'2019-09-23' between t1_date and t1_timestamp as f7,\n" // 测试字符串在日期和时间戳之间的BETWEEN操作
        + "cast('2019-09-23' as date) between t1_date and t1_timestamp as f8\n" // 测试显式转换后的BETWEEN操作
        + "from t1").ok(); // 从t1表查询，并断言SQL执行成功
  }

  /** Test cases for {@link TypeCoercion#inOperationCoercion}. */
  // 测试方法，测试IN操作中的隐式类型转换，调用TypeCoercion的inOperationCoercion方法
  @Test void testInOperation() { // 定义测试方法，测试IN操作的类型转换
    sql("select\n" // 执行SQL查询，测试IN操作的各种类型转换场景
        + "1 in ('1', '2', '3') as f0,\n" // 测试整数在字符串列表中的IN操作
        + "(1, 2) in (('1', '2')) as f1,\n" // 测试整数元组在字符串元组列表中的IN操作
        + "(1, 2) in (('1', '2'), ('3', '4')) as f2\n" // 测试整数元组在多个字符串元组列表中的IN操作
        + "from (values (true, true, true))").ok(); // 从值构造的表中查询，并断言SQL执行成功
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6485">[CALCITE-6485]
   * AssertionError When an IN list containing NULL
   * has an implicit coercion type converter</a>. */
  // 测试方法，测试IN列表中包含NULL时的隐式类型转换，解决CALCITE-6485问题
  @Test void testInOperationWithNull() { // 定义测试方法，测试包含NULL的IN操作
    sql("select\n" // 执行SQL查询，测试包含NULL的各种IN操作场景
        + "1 in (null, '2', '3') as f0,\n" // 测试整数在包含NULL和字符串的列表中的IN操作
        + "1 in ('1', null, '3') as f1,\n" // 测试整数在包含字符串和NULL的列表中的IN操作
        + "(1, 2) in ((null, '2')) as f2,\n" // 测试整数元组在包含NULL的元组列表中的IN操作
        + "(1, 2) in (('1', null)) as f3,\n" // 测试整数元组在包含NULL的元组列表中的IN操作
        + "(1, 2) in (('1', '2'), ('1', cast(null as char))) as f4,\n" // 测试整数元组在包含显式NULL转换的元组列表中的IN操作
        + "(1, 2) in (('1', '3'), ('1', cast(null as char))) as f5\n" // 测试整数元组在多个包含NULL转换的元组列表中的IN操作
        + "from (values (null, true, null, null, true, null))").ok(); // 从值构造的表中查询，并断言SQL执行成功
  }

  @Test void testNotInOperation() { // 定义测试方法，测试NOT IN操作的类型转换
    sql("select\n" // 执行SQL查询，测试NOT IN操作的各种类型转换场景
        + "1 not in ('1', '2', '3') as f0,\n" // 测试整数不在字符串列表中的NOT IN操作
        + "(1, 2) not in (('1', '2')) as f1,\n" // 测试整数元组不在字符串元组列表中的NOT IN操作
        + "(1, 2) not in (('1', '2'), ('3', '4')) as f2\n" // 测试整数元组不在多个字符串元组列表中的NOT IN操作
        + "from (values (false, false, false))").ok(); // 从值构造的表中查询，并断言SQL执行成功
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6485">[CALCITE-6485]
   * AssertionError When an IN list containing NULL
   * has an implicit coercion type converter</a>. */
  // 测试方法，测试NOT IN列表中包含NULL时的隐式类型转换，解决CALCITE-6485问题
  @Test void testNotInOperationWithNull() { // 定义测试方法，测试包含NULL的NOT IN操作
    sql("select\n" // 执行SQL查询，测试包含NULL的各种NOT IN操作场景
        + "1 not in (null, '2', '3') as f0,\n" // 测试整数不在包含NULL和字符串的列表中的NOT IN操作
        + "1 not in ('1', null, '3') as f1,\n" // 测试整数不在包含字符串和NULL的列表中的NOT IN操作
        + "(1, 2) not in ((null, '2')) as f2,\n" // 测试整数元组不在包含NULL的元组列表中的NOT IN操作
        + "(1, 2) not in (('1', null)) as f3,\n" // 测试整数元组不在包含NULL的元组列表中的NOT IN操作
        + "(1, 2) not in (('1', '2'), ('1', cast(null as char))) as f4,\n" // 测试整数元组不在包含显式NULL转换的元组列表中的NOT IN操作
        + "(1, 2) not in (('2', '3'), ('1', cast(null as char))) as f5\n" // 测试整数元组不在多个包含NULL转换的元组列表中的NOT IN操作
        + "from (values (null, false, null, null, false, null))").ok(); // 从值构造的表中查询，并断言SQL执行成功
  }


  /** Test cases for {@link TypeCoercion#inOperationCoercion}. */
  // 测试方法，测试日期和时间戳在IN操作中的隐式类型转换
  @Test void testInDateTimestamp() { // 定义测试方法，测试日期和时间戳的IN操作
    sql("select (t1_timestamp, t1_date)\n" // 执行SQL查询，测试时间戳和日期元组的IN操作
        + "in ((DATE '2020-04-16', TIMESTAMP '2020-04-16 11:40:53'))\n" // 测试元组是否在包含日期和时间戳字面值的列表中
        + "from t1").ok(); // 从t1表查询，并断言SQL执行成功
  }

  /** Test case for
   * {@link org.apache.calcite.sql.validate.implicit.TypeCoercionImpl}.{@code booleanEquality}. */
  // 测试方法，测试布尔值相等性比较中的隐式类型转换，调用TypeCoercionImpl的booleanEquality方法
  @Test void testBooleanEquality() { // 定义测试方法，测试布尔值相等性的类型转换
    // REVIEW Danny 2018-05-16: Now we do not support cast between numeric <-> boolean for
    // Calcite execution runtime, but we still add cast in the plan so other systems
    // using Calcite can rewrite Cast operator implementation.
    // for this case, we replace the boolean literal with numeric 1.
    // 注释说明：Calcite运行时不支持数值和布尔值之间的转换，但在计划中仍添加转换，以便其他系统可以重写转换操作符实现
    sql("select\n" // 执行SQL查询，测试数值和布尔值的相等性比较
        + "1=true as f0,\n" // 测试整数和布尔字面值的相等比较
        + "1.0=true as f1,\n" // 测试浮点数和布尔字面值的相等比较
        + "0.0=true=true as f2,\n" // 测试浮点数、布尔字面值和布尔字面值的链式相等比较
        + "1.23=t1_boolean as f3,\n" // 测试浮点数和表字段布尔值的相等比较
        + "t1_smallint=t1_boolean as f4,\n" // 测试表字段小整数和表字段布尔值的相等比较
        + "10000000000=true as f5\n" // 测试大整数和布尔字面值的相等比较
        + "from t1").ok(); // 从t1表查询，并断言SQL执行成功
  }

  @Test void testCaseWhen() { // 定义测试方法，测试CASE WHEN表达式中的隐式类型转换
    sql("select case when 1 > 0 then t2_bigint else t2_decimal end from t2") // 执行SQL查询，测试CASE WHEN中不同类型分支的类型转换
        .ok(); // 断言SQL执行成功
  }

  @Test void testBuiltinFunctionCoercion() { // 定义测试方法，测试内置函数的隐式类型转换
    sql("select 1||'a' from (values true)").ok(); // 执行SQL查询，测试字符串连接操作符||中整数和字符串的类型转换
  }

  @Test void testStarImplicitTypeCoercion() { // 定义测试方法，测试SELECT *中的隐式类型转换
    sql("select * from (values(1, '3')) union select * from (values('2', 4))") // 执行SQL查询，测试UNION操作中不同类型列的隐式转换
        .ok(); // 断言SQL执行成功
  }

  @Test void testIntegerImplicitTypeCast1() { // 定义测试方法，测试整数的隐式类型转换场景1
    sql("with\n" // 执行SQL查询，使用WITH子句定义公共表表达式
        + "t1(x) as (select * from  (values (cast(1 as bigint)),(cast(2 as bigint))) as t1),\n" // 定义t1表，包含bigint类型的值
        + "t2(x) as (select * from  (values (3),(4)) as t2)\n" // 定义t2表，包含整数类型的值
        + "select *\n" // 选择所有列
        + "from t1\n" // 从t1表查询
        + "where t1.x in (select t2.x from t2)") // 测试bigint和整数在IN子查询中的隐式转换
        .ok(); // 断言SQL执行成功
  }

  @Test void testIntegerImplicitTypeCast2() { // 定义测试方法，测试整数的隐式类型转换场景2
    sql("with\n" // 执行SQL查询，使用WITH子句定义公共表表达式
        + "t1(x) as (select * from  (values (cast(1 as tinyint)),(cast(2 as tinyint))) as t1),\n" // 定义t1表，包含tinyint类型的值
        + "t2(x) as (select * from  (values (3),(4)) as t2)\n" // 定义t2表，包含整数类型的值
        + "select *\n" // 选择所有列
        + "from t1\n" // 从t1表查询
        + "where t1.x in (select t2.x from t2)") // 测试tinyint和整数在IN子查询中的隐式转换
        .ok(); // 断言SQL执行成功
  }

  @Test void testSetOperation() { // 定义测试方法，测试集合操作的隐式类型转换
    // int decimal smallint double // 注释说明第一行的列类型
    // char decimal float bigint // 注释说明第二行的列类型
    // char decimal float double // 注释说明第三行的列类型
    // char decimal smallint double // 注释说明第四行的列类型
    final String sql = "select t1_int, t1_decimal, t1_smallint, t1_double from t1 " // 定义SQL字符串，从t1表选择整数、小数、小整数、双精度浮点数
        + "union select t2_varchar20, t2_decimal, t2_real, t2_bigint from t2 " // 与t2表的字符串、小数、实数、大整数进行UNION操作
        + "union select t1_varchar20, t1_decimal, t1_real, t1_double from t1 " // 与t1表的字符串、小数、实数、双精度浮点数进行UNION操作
        + "union select t2_varchar20, t2_decimal, t2_smallint, t2_double from t2"; // 与t2表的字符串、小数、小整数、双精度浮点数进行UNION操作
    sql(sql).ok(); // 执行SQL查询，并断言SQL执行成功
  }

  @Test void testInsertQuerySourceCoercion() { // 定义测试方法，测试INSERT语句中查询源的隐式类型转换
    final String sql = "insert into t1 select t2_smallint, t2_int, t2_bigint, t2_real,\n" // 定义SQL字符串，从t2表选择数据插入到t1表
        + "t2_double, t2_decimal, t2_int, t2_date, t2_timestamp, t2_varchar20, t2_int from t2"; // 选择各种类型的列进行插入
    sql(sql).ok(); // 执行SQL查询，并断言SQL执行成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4897">[CALCITE-4897]
   * Set operation in DML, implicit type conversion is not complete</a>. */
  // 测试方法，测试INSERT语句中使用UNION的查询源的隐式类型转换，解决CALCITE-4897问题
  @Test void testInsertUnionQuerySourceCoercion() { // 定义测试方法，测试INSERT UNION查询的类型转换
    final String sql = "insert into t1 " // 定义SQL字符串，向t1表插入数据
        + "select 'a', 1, 1.0," // 第一行数据，包含字符串、整数、浮点数
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false union " // 其他列的值，使用UNION连接
        + "select 'b', 2, 2," // 第二行数据
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false union " // 其他列的值
        + "select 'c', CAST(3 AS SMALLINT), 3.0," // 第三行数据，包含显式类型转换
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false union " // 其他列的值
        + "select 'd', 4, 4.0," // 第四行数据
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false union " // 其他列的值
        + "select 'e', 5, 5.0," // 第五行数据
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false"; // 其他列的值
    sql(sql).ok(); // 执行SQL查询，并断言SQL执行成功
  }

  @Test void testInsertValuesQuerySourceCoercion() { // 定义测试方法，测试INSERT VALUES语句的隐式类型转换
    final String sql = "insert into t1 values " // 定义SQL字符串，向t1表插入VALUES数据
        + "('a', 1, 1.0," // 第一行数据，包含字符串、整数、浮点数
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false), " // 其他列的值
        + "('b', 2,  2," // 第二行数据
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false), " // 其他列的值
        + "('c', CAST(3 AS SMALLINT),  3.0," // 第三行数据，包含显式类型转换
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false), " // 其他列的值
        + "('d', 4, 4.0," // 第四行数据
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false), " // 其他列的值
        + "('e', 5, 5.0," // 第五行数据
        + " 0, 0, 0, 0, TIMESTAMP '2021-11-28 00:00:00', date '2021-11-28', x'0A', false)"; // 其他列的值
    sql(sql).ok(); // 执行SQL查询，并断言SQL执行成功
  }

  @Test void testUpdateQuerySourceCoercion() { // 定义测试方法，测试UPDATE语句中查询源的隐式类型转换
    final String sql = "update t1 set t1_varchar20=123, " // 定义SQL字符串，更新t1表的数据
        + "t1_date=TIMESTAMP '2020-01-03 10:14:34', t1_int=12.3"; // 更新字符串字段为整数，日期字段为时间戳，整数字段为浮点数
    sql(sql).ok(); // 执行SQL查询，并断言SQL执行成功
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-5130">[CALCITE-5130]
   * AssertionError: "Conversion to relational algebra failed to preserve datatypes"
   * when union VARCHAR literal and CAST(null AS INTEGER) </a>. */
  // 测试方法，测试UNION操作中VARCHAR字面量和CAST(null AS INTEGER)的隐式类型转换，解决CALCITE-5130问题
  @Test void testCastNullAsIntUnionChar() { // 定义测试方法，测试CAST NULL为整数与字符串的UNION操作
    String sql = "select CAST(null AS INTEGER) union select '10'"; // 定义SQL字符串，测试CAST NULL为整数与字符串字面量的UNION操作
    sql(sql).ok(); // 执行SQL查询，并断言SQL执行成功
  }
} // 类定义结束
