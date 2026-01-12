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
// Apache License 2.0 许可证头部,声明版权和许可信息
package org.apache.calcite.test; // 定义包名,表示这个类属于 org.apache.calcite.test 包

import org.apache.calcite.avatica.util.TimeUnit; // 导入时间单位枚举,用于定义时间间隔的时间单位
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口,表示SQL中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口,用于创建各种SQL数据类型
import org.apache.calcite.sql.SqlIntervalQualifier; // 导入SQL间隔限定符类,用于定义时间间隔的类型
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表,包含所有标准SQL函数和操作符
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析位置类,用于标记SQL语句中元素的解析位置
import org.apache.calcite.sql.test.SqlTestFactory; // 导入SQL测试工厂类,用于创建SQL测试环境
import org.apache.calcite.sql.type.SqlTypeFamily; // 导入SQL类型家族枚举,表示SQL类型的分类(如数值类型、字符串类型等)
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举,定义所有SQL数据类型名称
import org.apache.calcite.sql.type.SqlTypeUtil; // 导入SQL类型工具类,提供类型判断和转换的静态方法
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口,用于验证SQL语句的语义正确性
import org.apache.calcite.sql.validate.implicit.AbstractTypeCoercion; // 导入抽象类型强制转换类,定义隐式类型转换的核心逻辑
import org.apache.calcite.sql.validate.implicit.TypeCoercion; // 导入类型强制转换接口,提供类型转换相关的方法
import org.apache.calcite.util.Pair; // 导入键值对工具类,用于存储两个关联的对象

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解,用于标记可能为null的参数或返回值
import org.junit.jupiter.api.Test; // 导入JUnit 5的测试注解,标记测试方法

import java.util.List; // 导入Java列表接口
import java.util.Map; // 导入Java映射接口

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest匹配器的is方法,用于断言值相等
import static org.hamcrest.CoreMatchers.notNullValue; // 静态导入Hamcrest匹配器的notNullValue方法,用于断言值不为null
import static org.hamcrest.CoreMatchers.nullValue; // 静态导入Hamcrest匹配器的nullValue方法,用于断言值为null
import static org.hamcrest.CoreMatchers.sameInstance; // 静态导入Hamcrest匹配器的sameInstance方法,用于断言是同一个对象实例
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的assertThat方法,用于执行断言

/**
 * Test cases for implicit type coercion. see {@link TypeCoercion} doc
 * or <a href="https://docs.google.com/spreadsheets/d/1GhleX5h5W8-kJKh7NMJ4vtoE78pwfaZRJl88ULX_MgU/edit?usp=sharing">CalciteImplicitCasts</a>
 * for conversion details.
 */
// 本类是隐式类型强制转换的测试用例类,用于测试Calcite中的类型转换机制
// 测试了各种SQL类型之间的隐式转换规则,包括数值类型、字符串类型、日期时间类型等
// 详细转换规则请参阅TypeCoercion类的文档或CalciteImplicitCasts电子表格
class TypeCoercionTest { // 定义测试类,类名为TypeCoercionTest

  public static final Fixture DEFAULT_FIXTURE = // 定义公共静态常量,默认的测试夹具(Fixture)实例
      Fixture.create(SqlTestFactory.INSTANCE); // 使用SQL测试工厂实例创建Fixture对象

  //~ Helper methods ---------------------------------------------------------
  // 辅助方法分隔符,以下是辅助方法区域

  public Fixture fixture() { // 定义公共方法,获取默认的Fixture实例
    return DEFAULT_FIXTURE; // 返回默认的Fixture对象
  }

  public static SqlValidatorFixture sql(String sql) { // 定义公共静态方法,为SQL语句创建验证器夹具
    return validatorFixture() // 获取验证器夹具
        .withSql(sql); // 设置要验证的SQL语句
  }

  public static SqlValidatorFixture expr(String sql) { // 定义公共静态方法,为表达式创建验证器夹具
    return validatorFixture() // 获取验证器夹具
        .withExpr(sql); // 设置要验证的表达式
  }

  private static SqlValidatorFixture validatorFixture() { // 定义私有静态方法,创建验证器夹具
    return SqlValidatorTestCase.FIXTURE // 获取SQL验证器测试用例的默认Fixture
        .withCatalogReader(TCatalogReader::create); // 设置目录读取器为TCatalogReader的创建函数
  }

  private static ImmutableList<RelDataType> combine( // 定义私有静态方法,合并两个类型列表
      List<RelDataType> list0, // 第一个类型列表参数
      List<RelDataType> list1) { // 第二个类型列表参数
    return ImmutableList.<RelDataType>builder() // 创建不可变列表的构建器
        .addAll(list0) // 添加第一个列表的所有元素
        .addAll(list1) // 添加第二个列表的所有元素
        .build(); // 构建不可变列表并返回
  }

  private static ImmutableList<RelDataType> combine( // 定义私有静态方法,合并三个类型列表的重载版本
      List<RelDataType> list0, // 第一个类型列表参数
      List<RelDataType> list1, // 第二个类型列表参数
      List<RelDataType> list2) { // 第三个类型列表参数
    return ImmutableList.<RelDataType>builder() // 创建不可变列表的构建器
        .addAll(list0) // 添加第一个列表的所有元素
        .addAll(list1) // 添加第二个列表的所有元素
        .addAll(list2) // 添加第三个列表的所有元素
        .build(); // 构建不可变列表并返回
  }

  //~ Tests ------------------------------------------------------------------
  // 测试方法分隔符,以下是测试方法区域

  /**
   * Test case for {@link TypeCoercion#getTightestCommonType}.
   */
  // 测试方法,测试TypeCoercion#getTightestCommonType方法,该方法用于获取两个类型的最紧致公共类型
  // 最紧致公共类型是指能够同时表示两个类型的"最小"类型,避免不必要的精度损失
  @Test void testGetTightestCommonType() { // 定义测试方法
    // NULL
    final Fixture f = fixture(); // 获取测试夹具实例
    f.checkCommonType(f.nullType, f.nullType, f.nullType, true); // 测试NULL类型与NULL类型的公共类型,期望结果为NULL,且对称
    // BOOLEAN
    f.checkCommonType(f.nullType, f.booleanType, f.nullableBooleanType, true); // 测试NULL与BOOLEAN的公共类型,期望为可空BOOLEAN
    f.checkCommonType(f.booleanType, f.booleanType, f.booleanType, true); // 测试BOOLEAN与BOOLEAN的公共类型,期望为BOOLEAN
    f.checkCommonType(f.intType, f.booleanType, null, true); // 测试INT与BOOLEAN的公共类型,期望为null(不兼容)
    f.checkCommonType(f.bigintType, f.booleanType, null, true); // 测试BIGINT与BOOLEAN的公共类型,期望为null(不兼容)
    // INT
    f.checkCommonType(f.nullType, f.tinyintType, f.nullableTinyintType, true); // 测试NULL与TINYINT的公共类型,期望为可空TINYINT
    f.checkCommonType(f.nullType, f.intType, f.nullableIntType, true); // 测试NULL与INT的公共类型,期望为可空INT
    f.checkCommonType(f.nullType, f.bigintType, f.nullableBigintType, true); // 测试NULL与BIGINT的公共类型,期望为可空BIGINT
    f.checkCommonType(f.smallintType, f.intType, f.intType, true); // 测试SMALLINT与INT的公共类型,期望为INT(提升到更大类型)
    f.checkCommonType(f.smallintType, f.bigintType, f.bigintType, true); // 测试SMALLINT与BIGINT的公共类型,期望为BIGINT
    f.checkCommonType(f.intType, f.bigintType, f.bigintType, true); // 测试INT与BIGINT的公共类型,期望为BIGINT
    f.checkCommonType(f.bigintType, f.bigintType, f.bigintType, true); // 测试BIGINT与BIGINT的公共类型,期望为BIGINT
    // FLOAT/DOUBLE
    f.checkCommonType(f.nullType, f.realType, f.nullableRealType, true); // 测试NULL与REAL的公共类型,期望为可空REAL
    f.checkCommonType(f.nullType, f.doubleType, f.nullableDoubleType, true); // 测试NULL与DOUBLE的公共类型,期望为可空DOUBLE
    // Use RelDataTypeFactory#leastRestrictive to find the common type; it's not
    // symmetric but it's ok because precision does not become lower.
    // 使用RelDataTypeFactory#leastRestrictive查找公共类型;它不是对称的,但没关系因为精度不会降低
    f.checkCommonType(f.realType, f.doubleType, f.doubleType, false); // 测试REAL与DOUBLE的公共类型,期望为DOUBLE,不对称
    f.checkCommonType(f.realType, f.realType, f.realType, true); // 测试REAL与REAL的公共类型,期望为REAL
    f.checkCommonType(f.doubleType, f.doubleType, f.doubleType, true); // 测试DOUBLE与DOUBLE的公共类型,期望为DOUBLE
    // EXACT + FRACTIONAL
    // 精确数值类型与浮点类型的组合
    f.checkCommonType(f.intType, f.realType, f.realType, true); // 测试INT与REAL的公共类型,期望为REAL(转换为浮点)
    f.checkCommonType(f.intType, f.doubleType, f.doubleType, true); // 测试INT与DOUBLE的公共类型,期望为DOUBLE
    f.checkCommonType(f.bigintType, f.realType, f.realType, true); // 测试BIGINT与REAL的公共类型,期望为REAL
    f.checkCommonType(f.bigintType, f.doubleType, f.doubleType, true); // 测试BIGINT与DOUBLE的公共类型,期望为DOUBLE
    // Fixed precision decimal
    // 固定精度的小数类型
    RelDataType decimal54 = // 创建DECIMAL(5,4)类型,即5位精度4位小数
        f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 5, 4);
    RelDataType decimal71 = // 创建DECIMAL(7,1)类型,即7位精度1位小数
        f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 7, 1);
    f.checkCommonType(decimal54, decimal71, null, true); // 测试DECIMAL(5,4)与DECIMAL(7,1)的公共类型,期望为null(小数位不同)
    f.checkCommonType(decimal54, f.doubleType, null, true); // 测试DECIMAL与DOUBLE的公共类型,期望为null
    f.checkCommonType(decimal54, f.intType, null, true); // 测试DECIMAL与INT的公共类型,期望为null
    // CHAR/VARCHAR
    f.checkCommonType(f.nullType, f.charType, f.nullableCharType, true); // 测试NULL与CHAR的公共类型,期望为可空CHAR
    f.checkCommonType(f.charType, f.varcharType, f.varcharType, true); // 测试CHAR与VARCHAR的公共类型,期望为VARCHAR(提升到可变长度)
    f.checkCommonType(f.intType, f.charType, null, true); // 测试INT与CHAR的公共类型,期望为null(不兼容)
    f.checkCommonType(f.doubleType, f.charType, null, true); // 测试DOUBLE与CHAR的公共类型,期望为null(不兼容)
    // TIMESTAMP
    f.checkCommonType(f.nullType, f.timestampType, f.nullableTimestampType, true); // 测试NULL与TIMESTAMP的公共类型,期望为可空TIMESTAMP
    f.checkCommonType(f.timestampType, f.timestampType, f.timestampType, true); // 测试TIMESTAMP与TIMESTAMP的公共类型,期望为TIMESTAMP
    f.checkCommonType(f.dateType, f.timestampType, f.timestampType, true); // 测试DATE与TIMESTAMP的公共类型,期望为TIMESTAMP(DATE提升为TIMESTAMP)
    f.checkCommonType(f.intType, f.timestampType, null, true); // 测试INT与TIMESTAMP的公共类型,期望为null(不兼容)
    f.checkCommonType(f.varcharType, f.timestampType, null, true); // 测试VARCHAR与TIMESTAMP的公共类型,期望为null(不兼容)
    // STRUCT
    // 结构化类型(复杂类型)的测试
    f.checkCommonType(f.nullType, f.mapType(f.intType, f.charType), // 测试NULL与MAP类型的公共类型
        f.mapType(f.intType, f.charType), true); // 期望为MAP类型
    f.checkCommonType(f.nullType, f.recordType(ImmutableList.of()), // 测试NULL与空记录类型的公共类型
        f.recordType(ImmutableList.of()), true); // 期望为空记录类型
    f.checkCommonType(f.charType, f.mapType(f.intType, f.charType), null, true); // 测试CHAR与MAP的公共类型,期望为null(不兼容)
    f.checkCommonType(f.arrayType(f.intType), f.recordType(ImmutableList.of()), // 测试ARRAY与空记录的公共类型
        null, true); // 期望为null(不兼容)

    f.checkCommonType(f.recordType("a", f.intType), // 测试字段名不同的记录类型
        f.recordType("b", f.intType), null, true); // 期望为null(字段名不匹配)
    f.checkCommonType(f.recordType("a", f.intType), // 测试相同的记录类型
        f.recordType("a", f.intType), f.recordType("a", f.intType), true); // 期望为相同的记录类型
    f.checkCommonType(f.recordType("a", f.arrayType(f.intType)), // 测试包含数组的记录类型
        f.recordType("a", f.arrayType(f.intType)), // 相同的数组记录类型
        f.recordType("a", f.arrayType(f.intType)), true); // 期望为相同的记录类型
  }

  /** Test case for {@link TypeCoercion#getWiderTypeForTwo}
   * and {@link TypeCoercion#getWiderTypeFor}. */
  // 测试方法,测试TypeCoercion#getWiderTypeForTwo和getWiderTypeFor方法
  // 这些方法用于获取两个类型中更宽(能表示更大范围)的类型,用于类型提升
  @Test void testWiderTypeFor() { // 定义测试方法
    final Fixture f = fixture(); // 获取测试夹具实例
    // DECIMAL please see details in SqlTypeFactoryImpl#leastRestrictiveSqlType.
    // DECIMAL类型测试,详细规则请参考SqlTypeFactoryImpl#leastRestrictiveSqlType方法
    f.checkWiderType(f.decimalType(5, 4), f.decimalType(7, 1), // 测试DECIMAL(5,4)与DECIMAL(7,1)的更宽类型
        f.decimalType(10, 4), true, true); // 期望为DECIMAL(10,4),允许字符串提升,对称
    f.checkWiderType(f.decimalType(5, 4), f.doubleType, f.doubleType, true, // 测试DECIMAL与DOUBLE的更宽类型
        true); // 期望为DOUBLE,允许字符串提升,对称
    f.checkWiderType(f.decimalType(5, 4), f.intType, f.decimalType(14, 4), true, // 测试DECIMAL与INT的更宽类型
        true); // 期望为DECIMAL(14,4),允许字符串提升,对称
    f.checkWiderType(f.decimalType(5, 4), f.bigintType, f.decimalType(19, 0), // 测试DECIMAL与BIGINT的更宽类型
        true, true); // 期望为DECIMAL(19,0),允许字符串提升,对称
    // Array
    // 数组类型的测试
    f.checkWiderType(f.arrayType(f.smallintType), f.arrayType(f.doubleType), // 测试SMALLINT数组与DOUBLE数组的更宽类型
        f.arrayType(f.doubleType), true, true); // 期望为DOUBLE数组,允许字符串提升,对称
    f.checkWiderType(f.arrayType(f.timestampType), f.arrayType(f.varcharType), // 测试TIMESTAMP数组与VARCHAR数组的更宽类型
        f.arrayType(f.varcharType), true, true); // 期望为VARCHAR数组,允许字符串提升,对称
    f.checkWiderType(f.arrayType(f.intType), f.arrayType(f.bigintType), // 测试INT数组与BIGINT数组的更宽类型
        f.arrayType(f.bigintType), true, true); // 期望为BIGINT数组,允许字符串提升,对称
    // No string promotion
    // 不允许字符串提升的情况
    f.checkWiderType(f.intType, f.charType, null, false, true); // 测试INT与CHAR,不允许字符串提升时期望为null
    f.checkWiderType(f.timestampType, f.charType, null, false, true); // 测试TIMESTAMP与CHAR,不允许字符串提升时期望为null
    f.checkWiderType(f.arrayType(f.bigintType), f.arrayType(f.charType), null, // 测试BIGINT数组与CHAR数组,不允许字符串提升时期望为null
        false, true); // 不允许字符串提升,对称
    f.checkWiderType(f.arrayType(f.charType), f.arrayType(f.timestampType), null, // 测试CHAR数组与TIMESTAMP数组,不允许字符串提升时期望为null
        false, true); // 不允许字符串提升,对称
    // String promotion
    // 允许字符串提升的情况
    f.checkWiderType(f.intType, f.charType, f.varcharType, true, true); // 测试INT与CHAR,允许字符串提升时期望为VARCHAR
    f.checkWiderType(f.timestampType, f.charType, f.varcharType, true, true); // 测试TIMESTAMP与CHAR,允许字符串提升时期望为VARCHAR
    f.checkWiderType(f.arrayType(f.bigintType), f.arrayType(f.varcharType), // 测试BIGINT数组与VARCHAR数组
        f.arrayType(f.varcharType), true, true); // 期望为VARCHAR数组,允许字符串提升,对称
    f.checkWiderType(f.arrayType(f.charType), f.arrayType(f.timestampType), // 测试CHAR数组与TIMESTAMP数组
        f.arrayType(f.varcharType), true, true); // 期望为VARCHAR数组,允许字符串提升,对称
  }

  /** Test set operations: UNION, INTERSECT, EXCEPT type coercion. */
  // 测试方法,测试集合操作(UNION、INTERSECT、EXCEPT)的类型强制转换
  // 集合操作要求参与操作的结果集具有兼容的类型,需要进行类型提升和转换
  @Test void testSetOperations() { // 定义测试方法
    // union
    // UNION操作测试,合并两个结果集,自动进行类型转换
    sql("select 1 from (values(true)) union select '2' from (values(true))") // 测试INT与VARCHAR的UNION
        .type("RecordType(VARCHAR NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR
    sql("select 1 from (values(true)) union select '2' from (values(true))" // 测试三个UNION链式操作
        + "union select '3' from (values(true))")
        .type("RecordType(VARCHAR NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR
    sql("select 1, '2' from (values(true, false)) union select '3', 4 from (values(true, false))") // 测试多列UNION
        .type("RecordType(VARCHAR NOT NULL EXPR$0, VARCHAR NOT NULL EXPR$1) NOT NULL"); // 两列都转换为VARCHAR
    sql("select '1' from (values(true)) union values 2") // 测试VARCHAR与INT的UNION
        .type("RecordType(VARCHAR NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR
    sql("select (select 1+2 from (values true)) tt from (values(true)) union values '2'") // 测试子查询与VARCHAR的UNION
        .type("RecordType(VARCHAR TT) NOT NULL"); // 期望结果类型为VARCHAR,列名为TT
    // union with star
    // 使用星号(*)的UNION操作
    sql("select * from (values(1, '3')) union select * from (values('2', 4))") // 测试使用星号的UNION
        .type("RecordType(VARCHAR NOT NULL EXPR$0, VARCHAR NOT NULL EXPR$1) NOT NULL"); // 两列都转换为VARCHAR
    sql("select 1 from (values(true)) union values (select '1' from (values (true)) as tt)") // 测试子查询与VALUES的UNION
        .type("RecordType(VARCHAR EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR
    // union with func
    // 包含函数的UNION操作
    sql("select LOCALTIME from (values(true)) union values '1'") // 测试LOCALTIME函数与VARCHAR的UNION
        .type("RecordType(VARCHAR NOT NULL LOCALTIME) NOT NULL"); // 期望结果类型为VARCHAR
    sql("select t1_int, t1_decimal, t1_smallint, t1_double from t1 " // 测试多表多列的复杂UNION
        + "union select t2_varchar20, t2_decimal, t2_real, t2_bigint from t2 " // 第二个UNION
        + "union select t1_varchar20, t1_decimal, t1_real, t1_double from t1 " // 第三个UNION
        + "union select t2_varchar20, t2_decimal, t2_smallint, t2_double from t2") // 第四个UNION
        .type("RecordType(VARCHAR NOT NULL T1_INT," // 期望第一列转换为VARCHAR
            + " DECIMAL(19, 0) NOT NULL T1_DECIMAL," // 第二列保持DECIMAL
            + " REAL NOT NULL T1_SMALLINT," // 第三列保持REAL
            + " DOUBLE NOT NULL T1_DOUBLE) NOT NULL"); // 第四列保持DOUBLE
    // (int) union (int) union (varchar(20))
    // 测试INT、INT、VARCHAR的UNION,验证字符串提升规则
    sql("select t1_int from t1 " // 第一个查询:INT类型
        + "union select t2_int from t2 " // 第二个查询:INT类型
        + "union select t1_varchar20 from t1") // 第三个查询:VARCHAR类型
        .columnType("VARCHAR NOT NULL"); // 期望结果类型为VARCHAR(字符串提升)

    // (varchar(20)) union (int) union (int)
    // 测试VARCHAR、INT、INT的UNION,验证字符串提升规则
    sql("select t1_varchar20 from t1 " // 第一个查询:VARCHAR类型
        + "union select t2_int from t2 " // 第二个查询:INT类型
        + "union select t1_int from t1") // 第三个查询:INT类型
        .columnType("VARCHAR NOT NULL"); // 期望结果类型为VARCHAR(字符串提升)

    // date union timestamp
    // 测试DATE与TIMESTAMP的UNION
    sql("select t1_date, t1_timestamp from t1\n" // 第一个查询:DATE和TIMESTAMP
        + "union select t2_timestamp, t2_date from t2") // 第二个查询:TIMESTAMP和DATE
        .type("RecordType(TIMESTAMP(0) NOT NULL T1_DATE," // 第一列:DATE提升为TIMESTAMP
            + " TIMESTAMP(0) NOT NULL T1_TIMESTAMP) NOT NULL"); // 第二列:保持TIMESTAMP

    // intersect
    // INTERSECT操作测试,求两个结果集的交集
    sql("select t1_int, t1_decimal, t1_smallint, t1_double from t1 " // 第一个查询
        + "intersect select t2_varchar20, t2_decimal, t2_real, t2_bigint from t2 ") // 第二个查询
        .type("RecordType(VARCHAR NOT NULL T1_INT," // 第一列:INT转换为VARCHAR
            + " DECIMAL(19, 0) NOT NULL T1_DECIMAL," // 第二列:保持DECIMAL
            + " REAL NOT NULL T1_SMALLINT," // 第三列:保持REAL
            + " DOUBLE NOT NULL T1_DOUBLE) NOT NULL"); // 第四列:保持DOUBLE
    // except
    // EXCEPT操作测试,求两个结果集的差集
    sql("select t1_int, t1_decimal, t1_smallint, t1_double from t1 " // 第一个查询
        + "except select t2_varchar20, t2_decimal, t2_real, t2_bigint from t2 ") // 第二个查询
        .type("RecordType(VARCHAR NOT NULL T1_INT," // 第一列:INT转换为VARCHAR
            + " DECIMAL(19, 0) NOT NULL T1_DECIMAL," // 第二列:保持DECIMAL
            + " REAL NOT NULL T1_SMALLINT," // 第三列:保持REAL
            + " DOUBLE NOT NULL T1_DOUBLE) NOT NULL"); // 第四列:保持DOUBLE
  }

  /** Test arithmetic expressions with string type arguments. */
  // 测试方法,测试包含字符串类型参数的算术表达式
  // 验证字符串能否隐式转换为数值类型参与算术运算
  @Test void testArithmeticExpressionsWithStrings() { // 定义测试方法
    SqlValidatorFixture f = validatorFixture(); // 获取SQL验证器夹具
    // for null type in binary arithmetic.
    // 测试二元算术运算中的NULL类型
    expr("1 + null").ok(); // 测试加法:整数 + NULL,期望成功
    expr("1 - null").ok(); // 测试减法:整数 - NULL,期望成功
    expr("1 / null").ok(); // 测试除法:整数 / NULL,期望成功
    expr("1 * null").ok(); // 测试乘法:整数 * NULL,期望成功
    expr("MOD(1, null)").ok(); // 测试取模:MOD(整数, NULL),期望成功

    sql("select 1+'2', 2-'3', 2*'3', 2/'3', MOD(4,'3') " // 测试整数与字符串的各种算术运算
        + "from (values (true, true, true, true, true))") // 使用VALUES子句生成测试数据
        .type("RecordType(INTEGER NOT NULL EXPR$0, " // 第一列:1+'2'的结果类型为INTEGER
            + "INTEGER NOT NULL EXPR$1, " // 第二列:2-'3'的结果类型为INTEGER
            + "INTEGER NOT NULL EXPR$2, " // 第三列:2*'3'的结果类型为INTEGER
            + "INTEGER NOT NULL EXPR$3, " // 第四列:2/'3'的结果类型为INTEGER
            + "DECIMAL(19, 9) " // 第五列:MOD(4,'3')的结果类型为DECIMAL
            + "NOT NULL EXPR$4) NOT NULL");
    expr("select abs(t1_varchar20) from t1").ok(); // 测试ABS函数:对VARCHAR列求绝对值,期望成功
    expr("select sum(t1_varchar20) from t1").ok(); // 测试SUM函数:对VARCHAR列求和,期望成功
    expr("select avg(t1_varchar20) from t1").ok(); // 测试AVG函数:对VARCHAR列求平均值,期望成功

    f.setFor(SqlStdOperatorTable.STDDEV_POP); // 设置标准差总体函数
    f.setFor(SqlStdOperatorTable.STDDEV_SAMP); // 设置标准差样本函数
    expr("select STDDEV_POP(t1_varchar20) from t1").ok(); // 测试总体标准差函数,期望成功
    expr("select STDDEV_SAMP(t1_varchar20) from t1").ok(); // 测试样本标准差函数,期望成功
    expr("select -(t1_varchar20) from t1").ok(); // 测试负号运算符:对VARCHAR列取负,期望成功
    expr("select +(t1_varchar20) from t1").ok(); // 测试正号运算符:对VARCHAR列取正,期望成功
    f.setFor(SqlStdOperatorTable.VAR_POP); // 设置方差总体函数
    f.setFor(SqlStdOperatorTable.VAR_SAMP); // 设置方差样本函数
    expr("select VAR_POP(t1_varchar20) from t1").ok(); // 测试总体方差函数,期望成功
    expr("select VAR_SAMP(t1_varchar20) from t1").ok(); // 测试样本方差函数,期望成功
    // test divide with strings
    // 测试字符串与不同类型数值的除法运算
    expr("'12.3'/5") // 测试字符串除以整数
        .columnType("INTEGER NOT NULL"); // 期望结果类型为INTEGER
    expr("'12.3'/cast(5 as bigint)") // 测试字符串除以BIGINT
        .columnType("BIGINT NOT NULL"); // 期望结果类型为BIGINT
    expr("'12.3'/cast(5 as float)") // 测试字符串除以FLOAT
        .columnType("FLOAT NOT NULL"); // 期望结果类型为FLOAT
    expr("'12.3'/cast(5 as double)") // 测试字符串除以DOUBLE
        .columnType("DOUBLE NOT NULL"); // 期望结果类型为DOUBLE
    expr("'12.3'/5.1") // 测试字符串除以DECIMAL
        .columnType("DECIMAL(19, 6) NOT NULL"); // 期望结果类型为DECIMAL(19,6)
    expr("12.3/'5.1'") // 测试DECIMAL除以字符串
        .columnType("DECIMAL(19, 6) NOT NULL"); // 期望结果类型为DECIMAL(19,6)
    // test binary arithmetic with two strings.
    // 测试两个字符串之间的二元算术运算
    expr("'12.3' + '5'") // 测试字符串加法
        .columnType("DECIMAL(19, 9) NOT NULL"); // 期望结果类型为DECIMAL(19,9)
    expr("'12.3' - '5'") // 测试字符串减法
        .columnType("DECIMAL(19, 9) NOT NULL"); // 期望结果类型为DECIMAL(19,9)
    expr("'12.3' * '5'") // 测试字符串乘法
        .columnType("DECIMAL(19, 18) NOT NULL"); // 期望结果类型为DECIMAL(19,18)
    expr("'12.3' / '5'") // 测试字符串除法
        .columnType("DECIMAL(19, 6) NOT NULL"); // 期望结果类型为DECIMAL(19,6)
  }

  /** Test cases for binary comparison expressions. */
  // 测试方法,测试二元比较表达式的类型强制转换
  // 验证不同类型之间的比较操作能否自动进行类型转换
  @Test void testBinaryComparisonCoercion() { // 定义测试方法
    expr("'2' = 3").columnType("BOOLEAN NOT NULL"); // 测试字符串与整数的等于比较,结果为BOOLEAN
    expr("'2' > 3").columnType("BOOLEAN NOT NULL"); // 测试字符串与整数的大于比较,结果为BOOLEAN
    expr("'2' >= 3").columnType("BOOLEAN NOT NULL"); // 测试字符串与整数的大于等于比较,结果为BOOLEAN
    expr("'2' < 3").columnType("BOOLEAN NOT NULL"); // 测试字符串与整数的小于比较,结果为BOOLEAN
    expr("'2' <= 3").columnType("BOOLEAN NOT NULL"); // 测试字符串与整数的小于等于比较,结果为BOOLEAN
    expr("'2' is distinct from 3").columnType("BOOLEAN NOT NULL"); // 测试IS DISTINCT FROM操作,结果为BOOLEAN
    expr("'2' is not distinct from 3").columnType("BOOLEAN NOT NULL"); // 测试IS NOT DISTINCT FROM操作,结果为BOOLEAN
    // NULL operand
    // 测试包含NULL操作数的比较表达式
    expr("'2' = null").columnType("BOOLEAN"); // 测试字符串与NULL的等于比较,结果为可空BOOLEAN
    expr("'2' > null").columnType("BOOLEAN"); // 测试字符串与NULL的大于比较,结果为可空BOOLEAN
    expr("'2' >= null").columnType("BOOLEAN"); // 测试字符串与NULL的大于等于比较,结果为可空BOOLEAN
    expr("'2' < null").columnType("BOOLEAN"); // 测试字符串与NULL的小于比较,结果为可空BOOLEAN
    expr("'2' <= null").columnType("BOOLEAN"); // 测试字符串与NULL的小于等于比较,结果为可空BOOLEAN
    expr("'2' is distinct from null").columnType("BOOLEAN NOT NULL"); // 测试IS DISTINCT FROM NULL,结果为BOOLEAN
    expr("'2' is not distinct from null").columnType("BOOLEAN NOT NULL"); // 测试IS NOT DISTINCT FROM NULL,结果为BOOLEAN
    // BETWEEN operator
    // 测试BETWEEN操作符的类型转换
    expr("'2' between 1 and 3").columnType("BOOLEAN NOT NULL"); // 测试字符串在整数范围内的BETWEEN,结果为BOOLEAN
    expr("NULL between 1 and 3").columnType("BOOLEAN"); // 测试NULL在整数范围内的BETWEEN,结果为可空BOOLEAN
    sql("select '2019-09-23' between t1_date and t1_timestamp from t1") // 测试日期字符串在DATE和TIMESTAMP之间的BETWEEN
        .columnType("BOOLEAN NOT NULL"); // 期望结果为BOOLEAN
    sql("select t1_date between '2019-09-23' and t1_timestamp from t1") // 测试DATE在日期字符串和TIMESTAMP之间的BETWEEN
        .columnType("BOOLEAN NOT NULL"); // 期望结果为BOOLEAN
    sql("select cast('2019-09-23' as date) between t1_date and t1_timestamp from t1") // 测试CAST后的DATE在DATE和TIMESTAMP之间的BETWEEN
        .columnType("BOOLEAN NOT NULL"); // 期望结果为BOOLEAN
    sql("select t1_date between cast('2019-09-23' as date) and t1_timestamp from t1") // 测试DATE在CAST后的DATE和TIMESTAMP之间的BETWEEN
        .columnType("BOOLEAN NOT NULL"); // 期望结果为BOOLEAN
  }

  @Test void testComparisonCoercion() { // 定义测试方法,测试比较操作的类型强制转换规则
    // NULL
    // NULL类型的比较测试
    final Fixture f = fixture(); // 获取测试夹具实例
    f.comparisonCommonType(f.nullType, f.nullType, f.nullType); // 测试NULL与NULL的比较公共类型,期望为NULL
    // BOOLEAN
    // BOOLEAN类型的比较测试
    f.comparisonCommonType(f.nullType, f.booleanType, null); // 测试NULL与BOOLEAN的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.booleanType, f.booleanType, f.booleanType); // 测试BOOLEAN与BOOLEAN的比较公共类型,期望为BOOLEAN
    f.comparisonCommonType(f.intType, f.booleanType, null); // 测试INT与BOOLEAN的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.bigintType, f.booleanType, null); // 测试BIGINT与BOOLEAN的比较公共类型,期望为null(不兼容)
    // INT
    // 整数类型的比较测试
    f.comparisonCommonType(f.smallintType, f.intType, f.intType); // 测试SMALLINT与INT的比较公共类型,期望为INT
    f.comparisonCommonType(f.smallintType, f.bigintType, f.bigintType); // 测试SMALLINT与BIGINT的比较公共类型,期望为BIGINT
    f.comparisonCommonType(f.intType, f.bigintType, f.bigintType); // 测试INT与BIGINT的比较公共类型,期望为BIGINT
    f.comparisonCommonType(f.bigintType, f.bigintType, f.bigintType); // 测试BIGINT与BIGINT的比较公共类型,期望为BIGINT
    // FLOAT/DOUBLE
    // 浮点类型的比较测试
    f.comparisonCommonType(f.realType, f.doubleType, f.doubleType); // 测试REAL与DOUBLE的比较公共类型,期望为DOUBLE
    f.comparisonCommonType(f.realType, f.realType, f.realType); // 测试REAL与REAL的比较公共类型,期望为REAL
    f.comparisonCommonType(f.doubleType, f.doubleType, f.doubleType); // 测试DOUBLE与DOUBLE的比较公共类型,期望为DOUBLE
    // EXACT + FRACTIONAL
    // 精确数值与浮点类型的比较测试
    f.comparisonCommonType(f.intType, f.realType, f.realType); // 测试INT与REAL的比较公共类型,期望为REAL
    f.comparisonCommonType(f.intType, f.doubleType, f.doubleType); // 测试INT与DOUBLE的比较公共类型,期望为DOUBLE
    f.comparisonCommonType(f.bigintType, f.realType, f.realType); // 测试BIGINT与REAL的比较公共类型,期望为REAL
    f.comparisonCommonType(f.bigintType, f.doubleType, f.doubleType); // 测试BIGINT与DOUBLE的比较公共类型,期望为DOUBLE
    // Fixed precision decimal
    // 固定精度小数类型的比较测试
    RelDataType decimal54 = // 创建DECIMAL(5,4)类型
        f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 5, 4);
    RelDataType decimal71 = // 创建DECIMAL(7,1)类型
        f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 7, 1);
    RelDataType decimal104 = // 创建DECIMAL(10,4)类型
        f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 10, 4);
    RelDataType decimal144 = // 创建DECIMAL(14,4)类型
        f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 14, 4);
    f.comparisonCommonType(decimal54, decimal71, decimal104); // 测试DECIMAL(5,4)与DECIMAL(7,1)的比较公共类型,期望为DECIMAL(10,4)
    f.comparisonCommonType(decimal54, f.doubleType, f.doubleType); // 测试DECIMAL与DOUBLE的比较公共类型,期望为DOUBLE
    f.comparisonCommonType(decimal54, f.intType, decimal144); // 测试DECIMAL与INT的比较公共类型,期望为DECIMAL(14,4)
    // CHAR/VARCHAR
    // 字符串类型的比较测试
    f.comparisonCommonType(f.charType, f.varcharType, f.varcharType); // 测试CHAR与VARCHAR的比较公共类型,期望为VARCHAR
    f.comparisonCommonType(f.intType, f.charType, f.intType); // 测试INT与CHAR的比较公共类型,期望为INT(字符串转换为数值)
    f.comparisonCommonType(f.doubleType, f.charType, f.doubleType); // 测试DOUBLE与CHAR的比较公共类型,期望为DOUBLE(字符串转换为数值)
    // TIMESTAMP
    // 时间戳类型的比较测试
    f.comparisonCommonType(f.timestampType, f.timestampType, f.timestampType); // 测试TIMESTAMP与TIMESTAMP的比较公共类型,期望为TIMESTAMP
    f.comparisonCommonType(f.dateType, f.timestampType, f.timestampType); // 测试DATE与TIMESTAMP的比较公共类型,期望为TIMESTAMP(DATE提升)
    f.comparisonCommonType(f.intType, f.timestampType, null); // 测试INT与TIMESTAMP的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.varcharType, f.timestampType, f.timestampType); // 测试VARCHAR与TIMESTAMP的比较公共类型,期望为TIMESTAMP(字符串转换为时间戳)
    // generic
    // 通用类型的比较测试
    f.comparisonCommonType(f.charType, f.mapType(f.intType, f.charType), null); // 测试CHAR与MAP的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.arrayType(f.intType), f.recordType(ImmutableList.of()), // 测试ARRAY与空记录的比较公共类型
        null); // 期望为null(不兼容)
    f.comparisonCommonType(f.recordType("a", f.intType), // 测试相同记录类型的比较公共类型
        f.recordType("a", f.intType), f.recordType("a", f.intType)); // 期望为相同的记录类型
    f.comparisonCommonType(f.recordType("a", f.intType), // 测试字段名相同但类型不同的记录类型
        f.recordType("a", f.charType), f.recordType("a", f.intType)); // 期望为INT类型(CHAR转换为INT)
    f.comparisonCommonType(f.recordType("a", f.arrayType(f.intType)), // 测试包含数组的记录类型
        f.recordType("a", f.arrayType(f.intType)), // 相同的数组记录类型
        f.recordType("a", f.arrayType(f.intType))); // 期望为相同的记录类型

    // Nullable types
    // 可空类型的比较测试
    // BOOLEAN
    f.comparisonCommonType(f.booleanType, f.nullableBooleanType, f.nullableBooleanType); // 测试BOOLEAN与可空BOOLEAN的比较公共类型,期望为可空BOOLEAN
    f.comparisonCommonType(f.nullableBooleanType, f.booleanType, f.nullableBooleanType); // 测试可空BOOLEAN与BOOLEAN的比较公共类型,期望为可空BOOLEAN
    f.comparisonCommonType(f.nullableBooleanType, f.nullableBooleanType, f.nullableBooleanType); // 测试可空BOOLEAN与可空BOOLEAN的比较公共类型,期望为可空BOOLEAN
    f.comparisonCommonType(f.nullableIntType, f.booleanType, null); // 测试可空INT与BOOLEAN的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.bigintType, f.nullableBooleanType, null); // 测试BIGINT与可空BOOLEAN的比较公共类型,期望为null(不兼容)
    // INT
    f.comparisonCommonType(f.nullableSmallintType, f.intType, f.nullableIntType); // 测试可空SMALLINT与INT的比较公共类型,期望为可空INT
    f.comparisonCommonType(f.smallintType, f.nullableBigintType, f.nullableBigintType); // 测试SMALLINT与可空BIGINT的比较公共类型,期望为可空BIGINT
    f.comparisonCommonType(f.nullableIntType, f.bigintType, f.nullableBigintType); // 测试可空INT与BIGINT的比较公共类型,期望为可空BIGINT
    f.comparisonCommonType(f.bigintType, f.nullableBigintType, f.nullableBigintType); // 测试BIGINT与可空BIGINT的比较公共类型,期望为可空BIGINT
    // FLOAT/DOUBLE
    f.comparisonCommonType(f.realType, f.nullableDoubleType, f.nullableDoubleType); // 测试REAL与可空DOUBLE的比较公共类型,期望为可空DOUBLE
    f.comparisonCommonType(f.nullableRealType, f.realType, f.nullableRealType); // 测试可空REAL与REAL的比较公共类型,期望为可空REAL
    f.comparisonCommonType(f.doubleType, f.nullableDoubleType, f.nullableDoubleType); // 测试DOUBLE与可空DOUBLE的比较公共类型,期望为可空DOUBLE
    // EXACT + FRACTIONAL
    f.comparisonCommonType(f.intType, f.nullableRealType, f.nullableRealType); // 测试INT与可空REAL的比较公共类型,期望为可空REAL
    f.comparisonCommonType(f.nullableIntType, f.doubleType, f.nullableDoubleType); // 测试可空INT与DOUBLE的比较公共类型,期望为可空DOUBLE
    f.comparisonCommonType(f.bigintType, f.nullableRealType, f.nullableRealType); // 测试BIGINT与可空REAL的比较公共类型,期望为可空REAL
    f.comparisonCommonType(f.nullableBigintType, f.doubleType, f.nullableDoubleType); // 测试可空BIGINT与DOUBLE的比较公共类型,期望为可空DOUBLE

    RelDataType nullableDecimal54 = // 创建可空的DECIMAL(5,4)类型
        f.typeFactory.createTypeWithNullability(
            f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 5, 4), true);
    RelDataType nullableDecimal144 = // 创建可空的DECIMAL(14,4)类型
        f.typeFactory.createTypeWithNullability(
          f.typeFactory.createSqlType(SqlTypeName.DECIMAL, 14, 4), true);
    f.comparisonCommonType(nullableDecimal54, f.doubleType, f.nullableDoubleType); // 测试可空DECIMAL与DOUBLE的比较公共类型,期望为可空DOUBLE
    f.comparisonCommonType(decimal54, f.nullableIntType, nullableDecimal144); // 测试DECIMAL与可空INT的比较公共类型,期望为可空DECIMAL(14,4)
    // CHAR/VARCHAR
    f.comparisonCommonType(f.nullableCharType, f.varcharType, f.nullableVarcharType); // 测试可空CHAR与VARCHAR的比较公共类型,期望为可空VARCHAR
    f.comparisonCommonType(f.intType, f.nullableCharType, f.nullableIntType); // 测试INT与可空CHAR的比较公共类型,期望为可空INT
    f.comparisonCommonType(f.doubleType, f.nullableCharType, f.nullableDoubleType); // 测试DOUBLE与可空CHAR的比较公共类型,期望为可空DOUBLE
    // TIMESTAMP
    f.comparisonCommonType(f.timestampType, f.nullableTimestampType, f.nullableTimestampType); // 测试TIMESTAMP与可空TIMESTAMP的比较公共类型,期望为可空TIMESTAMP
    f.comparisonCommonType(f.nullableDateType, f.timestampType, f.nullableTimestampType); // 测试可空DATE与TIMESTAMP的比较公共类型,期望为可空TIMESTAMP
    f.comparisonCommonType(f.nullableIntType, f.timestampType, null); // 测试可空INT与TIMESTAMP的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.varcharType, f.nullableTimestampType, f.nullableTimestampType); // 测试VARCHAR与可空TIMESTAMP的比较公共类型,期望为可空TIMESTAMP
    // generic
    f.comparisonCommonType(f.charType, f.mapType(f.intType, f.nullableCharType), null); // 测试CHAR与MAP(包含可空CHAR)的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.arrayType(f.nullableIntType), f.recordType(ImmutableList.of()), // 测试可空INT数组与空记录的比较公共类型
        null); // 期望为null(不兼容)
    f.comparisonCommonType(f.recordType("a", f.nullableIntType), // 测试记录类型(包含可空INT)与记录类型(包含INT)的比较公共类型
        f.recordType("a", f.intType), f.recordType("a", f.nullableIntType)); // 期望为可空INT记录类型
    f.comparisonCommonType(f.recordType("a", f.intType), // 测试记录类型(包含INT)与记录类型(包含可空CHAR)的比较公共类型
        f.recordType("a", f.nullableCharType), f.recordType("a", f.nullableIntType)); // 期望为可空INT记录类型(CHAR转换为INT)
    f.comparisonCommonType(f.recordType("a", f.arrayType(f.nullableIntType)), // 测试包含可空INT数组的记录类型
        f.recordType("a", f.arrayType(f.intType)), // 与包含INT数组的记录类型比较
        f.recordType("a", f.arrayType(f.nullableIntType))); // 期望为包含可空INT数组的记录类型
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6631">[CALCITE-6631]
   * The common type for a comparison operator returns the wrong type
   * when comparing a Java type long with a SQL type INTEGER</a>. */
  // 测试方法,测试Java类型与SQL类型比较的类型强制转换
  // 针对CALCITE-6631问题:Java类型long与SQL类型INTEGER比较时返回错误的公共类型
  @Test void testComparisonCoercionWithJavaType() { // 定义测试方法
    final Fixture f = fixture(); // 获取测试夹具实例
    f.comparisonCommonType(f.intJavaType, f.bigintJavaType, null); // 测试Java Integer与Java Long的比较公共类型,期望为null(不兼容)
    f.comparisonCommonType(f.intJavaType, f.bigintType, null); // 测试Java Integer与SQL BIGINT的比较公共类型,期望为null(不兼容)
  }


  /** Test case for case when expression and COALESCE operator. */
  // 测试方法,测试CASE WHEN表达式和COALESCE操作符的类型强制转换
  // 这些操作符需要从多个可能的返回类型中选择一个公共类型
  @Test void testCaseWhen() { // 定义测试方法
    // coalesce
    // COALESCE操作符测试,返回第一个非NULL值
    // double int float
    sql("select COALESCE(t1_double, t1_int, t1_real) from t1") // 测试DOUBLE、INT、REAL的COALESCE
        .type("RecordType(DOUBLE NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为DOUBLE(最宽的数值类型)
    // bigint int decimal
    sql("select COALESCE(t1_bigint, t1_int, t1_decimal) from t1") // 测试BIGINT、INT、DECIMAL的COALESCE
        .type("RecordType(DECIMAL(19, 0) NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为DECIMAL(19,0)
    // null int
    sql("select COALESCE(null, t1_int) from t1") // 测试NULL与INT的COALESCE
        .type("RecordType(INTEGER EXPR$0) NOT NULL"); // 期望结果类型为INTEGER(可空)
    // timestamp varchar
    sql("select COALESCE(t1_varchar20, t1_timestamp) from t1") // 测试VARCHAR与TIMESTAMP的COALESCE
        .type("RecordType(VARCHAR NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR(字符串提升)
    // null float int
    sql("select COALESCE(null, t1_real, t1_int) from t1") // 测试NULL、REAL、INT的COALESCE
        .type("RecordType(REAL EXPR$0) NOT NULL"); // 期望结果类型为REAL(可空)
    // null int decimal double
    sql("select COALESCE(null, t1_int, t1_decimal, t1_double) from t1") // 测试NULL、INT、DECIMAL、DOUBLE的COALESCE
        .type("RecordType(DOUBLE EXPR$0) NOT NULL"); // 期望结果类型为DOUBLE(最宽的数值类型)
    // null float double varchar
    sql("select COALESCE(null, t1_real, t1_double, t1_varchar20) from t1") // 测试NULL、REAL、DOUBLE、VARCHAR的COALESCE
        .type("RecordType(VARCHAR EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR(字符串提升)
    // timestamp int varchar
    sql("select COALESCE(t1_timestamp, t1_int, t1_varchar20) from t1") // 测试TIMESTAMP、INT、VARCHAR的COALESCE
        .type("RecordType(TIMESTAMP(0) NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为TIMESTAMP(优先保留时间类型)
    // timestamp date
    sql("select COALESCE(t1_timestamp, t1_date) from t1") // 测试TIMESTAMP与DATE的COALESCE
        .type("RecordType(TIMESTAMP(0) NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为TIMESTAMP(DATE提升为TIMESTAMP)
    // date timestamp
    sql("select COALESCE(t1_timestamp, t1_date) from t1") // 测试TIMESTAMP与DATE的COALESCE(顺序相反)
        .type("RecordType(TIMESTAMP(0) NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为TIMESTAMP
    // null date timestamp
    sql("select COALESCE(t1_timestamp, t1_date) from t1") // 测试TIMESTAMP与DATE的COALESCE(重复测试)
        .type("RecordType(TIMESTAMP(0) NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为TIMESTAMP

    // case when
    // CASE WHEN表达式测试,根据条件返回不同类型
    // smallint int char
    sql("select case " // 测试CASE WHEN表达式
        + "when 1 > 0 then t2_smallint " // 第一个分支返回SMALLINT
        + "when 2 > 3 then t2_int " // 第二个分支返回INT
        + "else t2_varchar20 end from t2") // ELSE分支返回VARCHAR
        .type("RecordType(VARCHAR NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR(字符串提升)
    // boolean int char
    sql("select case " // 测试包含BOOLEAN的CASE WHEN
        + "when 1 > 0 then t2_boolean " // 第一个分支返回BOOLEAN
        + "when 2 > 3 then t2_int " // 第二个分支返回INT
        + "else t2_varchar20 end from t2") // ELSE分支返回VARCHAR
        .type("RecordType(VARCHAR NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为VARCHAR(字符串提升)
    // float decimal
    sql("select case when 1 > 0 then t2_real else t2_decimal end from t2") // 测试REAL与DECIMAL的CASE WHEN
        .type("RecordType(DOUBLE NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为DOUBLE
    // bigint decimal
    sql("select case when 1 > 0 then t2_bigint else t2_decimal end from t2") // 测试BIGINT与DECIMAL的CASE WHEN
        .type("RecordType(DECIMAL(19, 0) NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为DECIMAL(19,0)
    // date timestamp
    sql("select case when 1 > 0 then t2_date else t2_timestamp end from t2") // 测试DATE与TIMESTAMP的CASE WHEN
        .type("RecordType(TIMESTAMP(0) NOT NULL EXPR$0) NOT NULL"); // 期望结果类型为TIMESTAMP(DATE提升为TIMESTAMP)
  }

  /** Test for {@link AbstractTypeCoercion#implicitCast}. */
  // 测试方法,测试AbstractTypeCoercion#implicitCast方法
  // 该方法用于判断是否可以进行隐式类型转换,以及转换后的目标类型
  @Test void testImplicitCasts() { // 定义测试方法
    final Fixture f = fixture(); // 获取测试夹具实例
    // TINYINT
    // 测试TINYINT类型的隐式转换规则
    ImmutableList<RelDataType> charTypes = f.charTypes; // 获取所有字符类型列表
    RelDataType checkedType1 = f.typeFactory.createSqlType(SqlTypeName.TINYINT); // 创建TINYINT类型
    f.checkShouldCast(checkedType1, combine(f.numericTypes, charTypes)); // 检查TINYINT应该能转换为数值类型和字符类型
    f.shouldCast(checkedType1, SqlTypeFamily.DECIMAL, // 测试TINYINT转换为DECIMAL类型
        f.typeFactory.decimalOf(checkedType1)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType1, SqlTypeFamily.NUMERIC, checkedType1); // 测试TINYINT转换为NUMERIC类型,期望保持原类型
    f.shouldCast(checkedType1, SqlTypeFamily.INTEGER, checkedType1); // 测试TINYINT转换为INTEGER类型,期望保持原类型
    f.shouldCast(checkedType1, SqlTypeFamily.EXACT_NUMERIC, checkedType1); // 测试TINYINT转换为EXACT_NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType1, SqlTypeFamily.APPROXIMATE_NUMERIC); // 测试TINYINT不能转换为APPROXIMATE_NUMERIC(浮点)类型

    // SMALLINT
    // 测试SMALLINT类型的隐式转换规则
    RelDataType checkedType2 = f.smallintType; // 获取SMALLINT类型
    f.checkShouldCast(checkedType2, combine(f.numericTypes, charTypes)); // 检查SMALLINT应该能转换为数值类型和字符类型
    f.shouldCast(checkedType2, SqlTypeFamily.DECIMAL, // 测试SMALLINT转换为DECIMAL类型
        f.typeFactory.decimalOf(checkedType2)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType2, SqlTypeFamily.NUMERIC, checkedType2); // 测试SMALLINT转换为NUMERIC类型,期望保持原类型
    f.shouldCast(checkedType2, SqlTypeFamily.INTEGER, checkedType2); // 测试SMALLINT转换为INTEGER类型,期望保持原类型
    f.shouldCast(checkedType2, SqlTypeFamily.EXACT_NUMERIC, checkedType2); // 测试SMALLINT转换为EXACT_NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType2, SqlTypeFamily.APPROXIMATE_NUMERIC); // 测试SMALLINT不能转换为APPROXIMATE_NUMERIC类型

    // INT
    // 测试INT类型的隐式转换规则
    RelDataType checkedType3 = f.intType; // 获取INT类型
    f.checkShouldCast(checkedType3, combine(f.numericTypes, charTypes)); // 检查INT应该能转换为数值类型和字符类型
    f.shouldCast(checkedType3, SqlTypeFamily.DECIMAL, // 测试INT转换为DECIMAL类型
        f.typeFactory.decimalOf(checkedType3)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType3, SqlTypeFamily.NUMERIC, checkedType3); // 测试INT转换为NUMERIC类型,期望保持原类型
    f.shouldCast(checkedType3, SqlTypeFamily.INTEGER, checkedType3); // 测试INT转换为INTEGER类型,期望保持原类型
    f.shouldCast(checkedType3, SqlTypeFamily.EXACT_NUMERIC, checkedType3); // 测试INT转换为EXACT_NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType3, SqlTypeFamily.APPROXIMATE_NUMERIC); // 测试INT不能转换为APPROXIMATE_NUMERIC类型

    // BIGINT
    // 测试BIGINT类型的隐式转换规则
    RelDataType checkedType4 = f.bigintType; // 获取BIGINT类型
    f.checkShouldCast(checkedType4, combine(f.numericTypes, charTypes)); // 检查BIGINT应该能转换为数值类型和字符类型
    f.shouldCast(checkedType4, SqlTypeFamily.DECIMAL, // 测试BIGINT转换为DECIMAL类型
        f.typeFactory.decimalOf(checkedType4)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType4, SqlTypeFamily.NUMERIC, checkedType4); // 测试BIGINT转换为NUMERIC类型,期望保持原类型
    f.shouldCast(checkedType4, SqlTypeFamily.INTEGER, checkedType4); // 测试BIGINT转换为INTEGER类型,期望保持原类型
    f.shouldCast(checkedType4, SqlTypeFamily.EXACT_NUMERIC, checkedType4); // 测试BIGINT转换为EXACT_NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType4, SqlTypeFamily.APPROXIMATE_NUMERIC); // 测试BIGINT不能转换为APPROXIMATE_NUMERIC类型

    // FLOAT/REAL
    // 测试REAL(单精度浮点)类型的隐式转换规则
    RelDataType checkedType5 = f.realType; // 获取REAL类型
    f.checkShouldCast(checkedType5, combine(f.numericTypes, charTypes)); // 检查REAL应该能转换为数值类型和字符类型
    f.shouldCast(checkedType5, SqlTypeFamily.DECIMAL, // 测试REAL转换为DECIMAL类型
        f.typeFactory.decimalOf(checkedType5)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType5, SqlTypeFamily.NUMERIC, checkedType5); // 测试REAL转换为NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType5, SqlTypeFamily.INTEGER); // 测试REAL不能转换为INTEGER类型
    f.shouldCast(checkedType5, SqlTypeFamily.EXACT_NUMERIC, // 测试REAL转换为EXACT_NUMERIC类型
        f.typeFactory.decimalOf(checkedType5)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType5, SqlTypeFamily.APPROXIMATE_NUMERIC, checkedType5); // 测试REAL转换为APPROXIMATE_NUMERIC类型,期望保持原类型

    // DOUBLE
    // 测试DOUBLE(双精度浮点)类型的隐式转换规则
    RelDataType checkedType6 = f.doubleType; // 获取DOUBLE类型
    f.checkShouldCast(checkedType6, combine(f.numericTypes, charTypes)); // 检查DOUBLE应该能转换为数值类型和字符类型
    f.shouldCast(checkedType6, SqlTypeFamily.DECIMAL, // 测试DOUBLE转换为DECIMAL类型
        f.typeFactory.decimalOf(checkedType6)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType6, SqlTypeFamily.NUMERIC, checkedType6); // 测试DOUBLE转换为NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType6, SqlTypeFamily.INTEGER); // 测试DOUBLE不能转换为INTEGER类型
    f.shouldCast(checkedType6, SqlTypeFamily.EXACT_NUMERIC, // 测试DOUBLE转换为EXACT_NUMERIC类型
        f.typeFactory.decimalOf(checkedType5)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType6, SqlTypeFamily.APPROXIMATE_NUMERIC, checkedType6); // 测试DOUBLE转换为APPROXIMATE_NUMERIC类型,期望保持原类型

    // DECIMAL(10, 2)
    // 测试DECIMAL(10,2)类型的隐式转换规则
    RelDataType checkedType7 = f.decimalType(10, 2); // 创建DECIMAL(10,2)类型
    f.checkShouldCast(checkedType7, combine(f.numericTypes, charTypes)); // 检查DECIMAL应该能转换为数值类型和字符类型
    f.shouldCast(checkedType7, SqlTypeFamily.DECIMAL, // 测试DECIMAL转换为DECIMAL类型
        f.typeFactory.decimalOf(checkedType7)); // 期望转换为对应的DECIMAL类型
    f.shouldCast(checkedType7, SqlTypeFamily.NUMERIC, checkedType7); // 测试DECIMAL转换为NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType7, SqlTypeFamily.INTEGER); // 测试DECIMAL不能转换为INTEGER类型
    f.shouldCast(checkedType7, SqlTypeFamily.EXACT_NUMERIC, checkedType7); // 测试DECIMAL转换为EXACT_NUMERIC类型,期望保持原类型
    f.shouldNotCast(checkedType7, SqlTypeFamily.APPROXIMATE_NUMERIC); // 测试DECIMAL不能转换为APPROXIMATE_NUMERIC类型

    // BINARY
    // 测试BINARY(二进制)类型的隐式转换规则
    RelDataType checkedType8 = f.binaryType; // 获取BINARY类型
    f.checkShouldCast(checkedType8, combine(f.binaryTypes, charTypes)); // 检查BINARY应该能转换为二进制类型和字符类型
    f.shouldNotCast(checkedType8, SqlTypeFamily.DECIMAL); // 测试BINARY不能转换为DECIMAL类型
    f.shouldNotCast(checkedType8, SqlTypeFamily.NUMERIC); // 测试BINARY不能转换为NUMERIC类型
    f.shouldNotCast(checkedType8, SqlTypeFamily.INTEGER); // 测试BINARY不能转换为INTEGER类型

    // BOOLEAN
    // 测试BOOLEAN类型的隐式转换规则
    RelDataType checkedType9 = f.booleanType; // 获取BOOLEAN类型
    f.checkShouldCast(checkedType9, combine(f.booleanTypes, charTypes)); // 检查BOOLEAN应该能转换为布尔类型和字符类型
    f.shouldNotCast(checkedType9, SqlTypeFamily.DECIMAL); // 测试BOOLEAN不能转换为DECIMAL类型
    f.shouldNotCast(checkedType9, SqlTypeFamily.NUMERIC); // 测试BOOLEAN不能转换为NUMERIC类型
    f.shouldNotCast(checkedType9, SqlTypeFamily.INTEGER); // 测试BOOLEAN不能转换为INTEGER类型

    // CHARACTER
    // 测试CHARACTER(字符)类型的隐式转换规则
    RelDataType checkedType10 = f.varcharType; // 获取VARCHAR类型作为字符类型的代表
    ImmutableList.Builder<RelDataType> builder = ImmutableList.builder(); // 创建列表构建器
    for (RelDataType type : f.atomicTypes) { // 遍历所有原子类型
      if (!SqlTypeUtil.isBoolean(type)) { // 如果不是布尔类型
        builder.add(type); // 添加到构建器中
      }
    }
    builder.addAll(f.geometryTypes); // 添加所有几何类型
    f.checkShouldCast(checkedType10, builder.build()); // 检查VARCHAR应该能转换为除BOOLEAN外的所有原子类型和几何类型
    f.shouldCast(checkedType10, SqlTypeFamily.DECIMAL, // 测试VARCHAR转换为DECIMAL类型
        SqlTypeUtil.getMaxPrecisionScaleDecimal(f.typeFactory)); // 期望转换为最大精度的DECIMAL
    f.shouldCast(checkedType10, SqlTypeFamily.NUMERIC, // 测试VARCHAR转换为NUMERIC类型
        SqlTypeUtil.getMaxPrecisionScaleDecimal(f.typeFactory)); // 期望转换为最大精度的DECIMAL
    f.shouldNotCast(checkedType10, SqlTypeFamily.BOOLEAN); // 测试VARCHAR不能转换为BOOLEAN类型

    // DATE
    // 测试DATE(日期)类型的隐式转换规则
    RelDataType checkedType11 = f.dateType; // 获取DATE类型
    f.checkShouldCast( // 检查DATE应该能转换的类型
        checkedType11, // DATE类型
        combine(ImmutableList.of(f.timestampType, checkedType11), // 可以转换为TIMESTAMP和DATE本身
            charTypes)); // 以及字符类型
    f.shouldNotCast(checkedType11, SqlTypeFamily.DECIMAL); // 测试DATE不能转换为DECIMAL类型
    f.shouldNotCast(checkedType11, SqlTypeFamily.NUMERIC); // 测试DATE不能转换为NUMERIC类型
    f.shouldNotCast(checkedType11, SqlTypeFamily.INTEGER); // 测试DATE不能转换为INTEGER类型

    // TIME
    // 测试TIME(时间)类型的隐式转换规则
    RelDataType checkedType12 = f.timeType; // 获取TIME类型
    f.checkShouldCast( // 检查TIME应该能转换的类型
        checkedType12, // TIME类型
        combine(ImmutableList.of(checkedType12), charTypes)); // 可以转换为TIME本身和字符类型
    f.shouldNotCast(checkedType12, SqlTypeFamily.DECIMAL); // 测试TIME不能转换为DECIMAL类型
    f.shouldNotCast(checkedType12, SqlTypeFamily.NUMERIC); // 测试TIME不能转换为NUMERIC类型
    f.shouldNotCast(checkedType12, SqlTypeFamily.INTEGER); // 测试TIME不能转换为INTEGER类型

    // TIMESTAMP
    // 测试TIMESTAMP(时间戳)类型的隐式转换规则
    RelDataType checkedType13 = f.timestampType; // 获取TIMESTAMP类型
    f.checkShouldCast( // 检查TIMESTAMP应该能转换的类型
        checkedType13, // TIMESTAMP类型
        combine(ImmutableList.of(f.dateType, checkedType13), // 可以转换为DATE和TIMESTAMP本身
            charTypes)); // 以及字符类型
    f.shouldNotCast(checkedType13, SqlTypeFamily.DECIMAL); // 测试TIMESTAMP不能转换为DECIMAL类型
    f.shouldNotCast(checkedType13, SqlTypeFamily.NUMERIC); // 测试TIMESTAMP不能转换为NUMERIC类型
    f.shouldNotCast(checkedType13, SqlTypeFamily.INTEGER); // 测试TIMESTAMP不能转换为INTEGER类型

    // NULL
    // 测试NULL类型的隐式转换规则
    RelDataType checkedType14 = f.nullType; // 获取NULL类型
    f.checkShouldCast(checkedType14, f.allTypes); // 检查NULL应该能转换为所有类型
    f.shouldCast(checkedType14, SqlTypeFamily.DECIMAL, f.decimalType); // 测试NULL转换为DECIMAL类型,期望为DECIMAL
    f.shouldCast(checkedType14, SqlTypeFamily.NUMERIC, f.intType); // 测试NULL转换为NUMERIC类型,期望为INT

    // INTERVAL
    // 测试INTERVAL(间隔)类型的隐式转换规则
    RelDataType checkedType15 = // 创建YEAR-MONTH间隔类型
        f.typeFactory.createSqlIntervalType(
            new SqlIntervalQualifier(TimeUnit.YEAR, TimeUnit.MONTH, // 年月间隔
                SqlParserPos.ZERO)); // 解析位置为零
    f.checkShouldCast(checkedType15, ImmutableList.of(checkedType15)); // 检查INTERVAL只能转换为INTERVAL本身
    f.shouldNotCast(checkedType15, SqlTypeFamily.DECIMAL); // 测试INTERVAL不能转换为DECIMAL类型
    f.shouldNotCast(checkedType15, SqlTypeFamily.NUMERIC); // 测试INTERVAL不能转换为NUMERIC类型
    f.shouldNotCast(checkedType15, SqlTypeFamily.INTEGER); // 测试INTERVAL不能转换为INTEGER类型
  }

  /** Test case for {@link TypeCoercion#builtinFunctionCoercion}. */
  // 测试方法,测试TypeCoercion#builtinFunctionCoercion方法
  // 该方法用于处理内置函数的类型强制转换
  @Test void testBuiltinFunctionCoercion() { // 定义测试方法
    // concat
    // 测试CONCAT(连接)操作符的类型转换
    expr("'ab'||'cde'") // 测试两个字符串的连接
        .columnType("CHAR(5) NOT NULL"); // 期望结果类型为CHAR(5)
    expr("null||'cde'") // 测试NULL与字符串的连接
        .columnType("VARCHAR"); // 期望结果类型为VARCHAR(可空)
    expr("1||'234'") // 测试整数与字符串的连接
        .columnType("VARCHAR NOT NULL"); // 期望结果类型为VARCHAR
    expr("select ^'a'||t1_binary^ from t1") // 测试字符串与BINARY的连接(应该失败)
        .fails("(?s).*Cannot apply.*"); // 期望失败,提示无法应用
    // smallint int double
    expr("select t1_smallint||t1_int||t1_double from t1") // 测试SMALLINT、INT、DOUBLE的连接
        .columnType("VARCHAR"); // 期望结果类型为VARCHAR(可空)
    // boolean float smallint
    expr("select t1_boolean||t1_real||t1_smallint from t1") // 测试BOOLEAN、REAL、SMALLINT的连接
        .columnType("VARCHAR"); // 期望结果类型为VARCHAR(可空)
    // decimal
    expr("select t1_decimal||t1_varchar20 from t1") // 测试DECIMAL与VARCHAR的连接
        .columnType("VARCHAR"); // 期望结果类型为VARCHAR(可空)
    // date timestamp
    expr("select t1_timestamp||t1_date from t1") // 测试TIMESTAMP与DATE的连接
        .columnType("VARCHAR"); // 期望结果类型为VARCHAR(可空)
  }

  /** Test case for {@link TypeCoercion#querySourceCoercion}. */
  // 测试方法,测试TypeCoercion#querySourceCoercion方法
  // 该方法用于处理查询源(INSERT、UPDATE等)的类型强制转换
  @Test void testQuerySourceCoercion() { // 定义测试方法
    final String expectRowType = "RecordType(" // 定义期望的行类型字符串
        + "VARCHAR(20) NOT NULL t1_varchar20, " // VARCHAR(20)列
        + "SMALLINT NOT NULL t1_smallint, " // SMALLINT列
        + "INTEGER NOT NULL t1_int, " // INTEGER列
        + "BIGINT NOT NULL t1_bigint, " // BIGINT列
        + "REAL NOT NULL t1_real, " // REAL列
        + "DOUBLE NOT NULL t1_double, " // DOUBLE列
        + "DECIMAL(19, 0) NOT NULL t1_decimal, " // DECIMAL列
        + "TIMESTAMP(0) NOT NULL t1_timestamp, " // TIMESTAMP列
        + "DATE NOT NULL t1_date, " // DATE列
        + "BINARY(1) NOT NULL t1_binary, " // BINARY列
        + "BOOLEAN NOT NULL t1_boolean) NOT NULL"; // BOOLEAN列

    final String sql = "insert into t1 select t2_smallint, t2_int, t2_bigint, t2_real,\n" // 定义INSERT SQL语句
        + "t2_double, t2_decimal, t2_int, t2_date, t2_timestamp, t2_varchar20, t2_int from t2"; // 从t2表查询数据插入到t1表
    sql(sql).type(expectRowType); // 验证SQL的返回类型是否符合期望

    final String sql1 = "insert into ^t1^(t1_varchar20, t1_date, t1_int)\n" // 定义INSERT SQL语句(指定列)
        + "select t2_smallint, t2_timestamp, t2_real from t2"; // 从t2表查询数据
    sql(sql1).fails("(?s).*Column 't1_smallint' has no default value and does not allow NULLs.*"); // 期望失败,提示缺少必填列

    final String sql2 = "update t1 set t1_varchar20=123, " // 定义UPDATE SQL语句
        + "t1_date=TIMESTAMP '2020-01-03 10:14:34', t1_int=12.3"; // 更新多个列,包含类型转换
    sql(sql2).type(expectRowType); // 验证SQL的返回类型是否符合期望
  }

  //~ Inner Class ------------------------------------------------------------
  // 内部类分隔符,以下是内部类定义区域

  /** Everything you need to run a test. */
  // 静态内部类Fixture,提供运行测试所需的所有工具和类型定义
  // 该类封装了类型强制转换测试所需的环境,包括类型工厂、类型强制转换器以及各种测试类型
  static class Fixture { // 定义静态内部类Fixture
    final TypeCoercion typeCoercion; // 类型强制转换器实例,用于执行类型转换逻辑
    final RelDataTypeFactory typeFactory; // 关系数据类型工厂实例,用于创建各种SQL数据类型

    // type category.
    // 类型分类:按类别组织的类型列表
    final ImmutableList<RelDataType> numericTypes; // 所有数值类型的不可变列表(包括TINYINT、SMALLINT、INT、BIGINT、REAL、DOUBLE、DECIMAL)
    final ImmutableList<RelDataType> atomicTypes; // 所有原子类型的不可变列表(包括数值、日期时间、字符串、布尔、几何等基本类型)
    final ImmutableList<RelDataType> allTypes; // 所有类型的不可变列表(包括原子类型、复杂类型、NULL、INTERVAL等)
    final ImmutableList<RelDataType> charTypes; // 所有字符类型的不可变列表(包括CHAR、VARCHAR等)
    final ImmutableList<RelDataType> binaryTypes; // 所有二进制类型的不可变列表(包括BINARY、VARBINARY等)
    final ImmutableList<RelDataType> booleanTypes; // 所有布尔类型的不可变列表
    final ImmutableList<RelDataType> geometryTypes; // 所有几何类型的不可变列表

    // single types
    // 单一类型:各种具体的SQL数据类型
    final RelDataType nullType; // NULL类型,表示空值
    final RelDataType booleanType; // BOOLEAN类型,表示布尔值
    final RelDataType nullableBooleanType; // 可空的BOOLEAN类型
    final RelDataType tinyintType; // TINYINT类型,8位整数
    final RelDataType nullableTinyintType; // 可空的TINYINT类型
    final RelDataType smallintType; // SMALLINT类型,16位整数
    final RelDataType nullableSmallintType; // 可空的SMALLINT类型
    final RelDataType intType; // INTEGER类型,32位整数
    final RelDataType intJavaType; // Java Integer类型(用于测试Java类型与SQL类型的交互)
    final RelDataType nullableIntType; // 可空的INTEGER类型
    final RelDataType bigintType; // BIGINT类型,64位整数
    final RelDataType bigintJavaType; // Java Long类型(用于测试Java类型与SQL类型的交互)
    final RelDataType nullableBigintType; // 可空的BIGINT类型
    final RelDataType realType; // REAL类型,单精度浮点数
    final RelDataType nullableRealType; // 可空的REAL类型
    final RelDataType doubleType; // DOUBLE类型,双精度浮点数
    final RelDataType nullableDoubleType; // 可空的DOUBLE类型
    final RelDataType decimalType; // DECIMAL类型,固定精度小数
    final RelDataType nullableDecimalType; // 可空的DECIMAL类型
    final RelDataType dateType; // DATE类型,日期
    final RelDataType nullableDateType; // 可空的DATE类型
    final RelDataType timeType; // TIME类型,时间
    final RelDataType nullableTimeType; // 可空的TIME类型
    final RelDataType timestampType; // TIMESTAMP类型,时间戳
    final RelDataType nullableTimestampType; // 可空的TIMESTAMP类型
    final RelDataType binaryType; // BINARY类型,固定长度二进制
    final RelDataType nullableBinaryType; // 可空的BINARY类型
    final RelDataType varbinaryType; // VARBINARY类型,可变长度二进制
    final RelDataType nullableVarbinaryType; // 可空的VARBINARY类型
    final RelDataType charType; // CHAR类型,固定长度字符串
    final RelDataType nullableCharType; // 可空的CHAR类型
    final RelDataType varcharType; // VARCHAR类型,可变长度字符串
    final RelDataType nullableVarcharType; // 可空的VARCHAR类型
    final RelDataType varchar20Type; // VARCHAR(20)类型,长度为20的可变长度字符串
    final RelDataType nullableVarchar20Type; // 可空的VARCHAR(20)类型
    final RelDataType geometryType; // GEOMETRY类型,几何图形类型
    final RelDataType nullableGeometryType; // 可空的GEOMETRY类型

    /** Creates a Fixture. */
    // 静态工厂方法,创建Fixture实例
    // 该方法使用SQL测试工厂创建验证器,然后从验证器中提取类型工厂和类型强制转换器
    public static Fixture create(SqlTestFactory testFactory) { // 定义静态工厂方法
      final SqlValidator validator = testFactory.createValidator(); // 使用测试工厂创建SQL验证器
      return new Fixture(validator.getTypeFactory(), validator.getTypeCoercion()); // 创建并返回Fixture实例
    }

    protected Fixture(RelDataTypeFactory typeFactory, // 受保护的构造方法
        TypeCoercion typeCoercion) { // 接收类型工厂和类型强制转换器作为参数
      this.typeFactory = typeFactory; // 保存类型工厂实例
      this.typeCoercion = typeCoercion; // 保存类型强制转换器实例

      // Initialize single types
      // 初始化单一类型
      nullType = this.typeFactory.createSqlType(SqlTypeName.NULL); // 创建NULL类型
      booleanType = this.typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 创建BOOLEAN类型
      nullableBooleanType = this.typeFactory.createTypeWithNullability(booleanType, true); // 创建可空的BOOLEAN类型
      tinyintType = this.typeFactory.createSqlType(SqlTypeName.TINYINT); // 创建TINYINT类型
      nullableTinyintType = this.typeFactory.createTypeWithNullability(tinyintType, true); // 创建可空的TINYINT类型
      smallintType = this.typeFactory.createSqlType(SqlTypeName.SMALLINT); // 创建SMALLINT类型
      nullableSmallintType = this.typeFactory.createTypeWithNullability(smallintType, true); // 创建可空的SMALLINT类型
      intType = this.typeFactory.createSqlType(SqlTypeName.INTEGER); // 创建INTEGER类型
      intJavaType = this.typeFactory.createJavaType(Integer.class); // 创建Java Integer类型
      nullableIntType = this.typeFactory.createTypeWithNullability(intType, true); // 创建可空的INTEGER类型
      bigintType = this.typeFactory.createSqlType(SqlTypeName.BIGINT); // 创建BIGINT类型
      bigintJavaType = this.typeFactory.createJavaType(Long.class); // 创建Java Long类型
      nullableBigintType = this.typeFactory.createTypeWithNullability(bigintType, true); // 创建可空的BIGINT类型
      realType = this.typeFactory.createSqlType(SqlTypeName.REAL); // 创建REAL类型
      nullableRealType = this.typeFactory.createTypeWithNullability(realType, true); // 创建可空的REAL类型
      doubleType = this.typeFactory.createSqlType(SqlTypeName.DOUBLE); // 创建DOUBLE类型
      nullableDoubleType = this.typeFactory.createTypeWithNullability(doubleType, true); // 创建可空的DOUBLE类型
      decimalType = this.typeFactory.createSqlType(SqlTypeName.DECIMAL); // 创建DECIMAL类型
      nullableDecimalType = this.typeFactory.createTypeWithNullability(decimalType, true); // 创建可空的DECIMAL类型
      dateType = this.typeFactory.createSqlType(SqlTypeName.DATE); // 创建DATE类型
      nullableDateType = this.typeFactory.createTypeWithNullability(dateType, true); // 创建可空的DATE类型
      timeType = this.typeFactory.createSqlType(SqlTypeName.TIME); // 创建TIME类型
      nullableTimeType = this.typeFactory.createTypeWithNullability(timeType, true); // 创建可空的TIME类型
      timestampType = this.typeFactory.createSqlType(SqlTypeName.TIMESTAMP); // 创建TIMESTAMP类型
      nullableTimestampType = this.typeFactory.createTypeWithNullability(timestampType, true); // 创建可空的TIMESTAMP类型
      binaryType = this.typeFactory.createSqlType(SqlTypeName.BINARY); // 创建BINARY类型
      nullableBinaryType = this.typeFactory.createTypeWithNullability(binaryType, true); // 创建可空的BINARY类型
      varbinaryType = this.typeFactory.createSqlType(SqlTypeName.VARBINARY); // 创建VARBINARY类型
      nullableVarbinaryType = this.typeFactory.createTypeWithNullability(varbinaryType, true); // 创建可空的VARBINARY类型
      charType = this.typeFactory.createSqlType(SqlTypeName.CHAR); // 创建CHAR类型
      nullableCharType = this.typeFactory.createTypeWithNullability(charType, true); // 创建可空的CHAR类型
      varcharType = this.typeFactory.createSqlType(SqlTypeName.VARCHAR); // 创建VARCHAR类型
      nullableVarcharType = this.typeFactory.createTypeWithNullability(varcharType, true); // 创建可空的VARCHAR类型
      varchar20Type = this.typeFactory.createSqlType(SqlTypeName.VARCHAR, 20); // 创建VARCHAR(20)类型
      nullableVarchar20Type = this.typeFactory.createTypeWithNullability(varchar20Type, true); // 创建可空的VARCHAR(20)类型
      geometryType = this.typeFactory.createSqlType(SqlTypeName.GEOMETRY); // 创建GEOMETRY类型
      nullableGeometryType = this.typeFactory.createTypeWithNullability(geometryType, true); // 创建可空的GEOMETRY类型

      // Initialize category types
      // 初始化类型分类

      // INT
      // 初始化数值类型列表
      ImmutableList.Builder<RelDataType> builder = ImmutableList.builder(); // 创建列表构建器
      for (SqlTypeName typeName : SqlTypeName.INT_TYPES) { // 遍历所有整数类型名称
        builder.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      numericTypes = builder.build(); // 构建不可变的数值类型列表
      // ATOMIC
      // 初始化原子类型列表
      ImmutableList.Builder<RelDataType> builder3 = ImmutableList.builder(); // 创建列表构建器
      for (SqlTypeName typeName : SqlTypeName.DATETIME_TYPES) { // 遍历所有日期时间类型名称
        builder3.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      builder3.addAll(numericTypes); // 添加所有数值类型
      for (SqlTypeName typeName : SqlTypeName.STRING_TYPES) { // 遍历所有字符串类型名称
        builder3.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      for (SqlTypeName typeName : SqlTypeName.BOOLEAN_TYPES) { // 遍历所有布尔类型名称
        builder3.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      for (SqlTypeName typeName : SqlTypeName.GEOMETRY_TYPES) { // 遍历所有几何类型名称
        builder3.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      atomicTypes = builder3.build(); // 构建不可变的原子类型列表
      // COMPLEX
      // 初始化复杂类型列表
      ImmutableList.Builder<RelDataType> builder4 = ImmutableList.builder(); // 创建列表构建器
      builder4.add(this.typeFactory.createArrayType(intType, -1)); // 添加INT数组类型
      builder4.add(this.typeFactory.createArrayType(varcharType, -1)); // 添加VARCHAR数组类型
      builder4.add(this.typeFactory.createMapType(varcharType, varcharType)); // 添加MAP类型(键值都是VARCHAR)
      builder4.add(this.typeFactory.createStructType(ImmutableList.of(Pair.of("a1", varcharType)))); // 添加单字段结构体类型
      List<? extends Map.Entry<String, RelDataType>> ll = // 创建多字段结构体的字段列表
          ImmutableList.of(Pair.of("a1", varbinaryType), Pair.of("a2", intType)); // 包含VARBINARY和INT两个字段
      builder4.add(this.typeFactory.createStructType(ll)); // 添加多字段结构体类型
      ImmutableList<RelDataType> complexTypes = builder4.build(); // 构建不可变的复杂类型列表
      // ALL
      // 初始化所有类型列表
      SqlIntervalQualifier intervalQualifier = // 创建时间间隔限定符
          new SqlIntervalQualifier(TimeUnit.DAY, TimeUnit.MINUTE, SqlParserPos.ZERO); // 定义DAY到MINUTE的间隔
      allTypes = // 合并所有类型
          combine(atomicTypes, complexTypes, // 合并原子类型和复杂类型
              ImmutableList.of(nullType, // 添加NULL类型
                  this.typeFactory.createSqlIntervalType(intervalQualifier))); // 添加INTERVAL类型

      // CHARACTERS
      // 初始化字符类型列表
      ImmutableList.Builder<RelDataType> builder6 = ImmutableList.builder(); // 创建列表构建器
      for (SqlTypeName typeName : SqlTypeName.CHAR_TYPES) { // 遍历所有字符类型名称
        builder6.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      charTypes = builder6.build(); // 构建不可变的字符类型列表
      // BINARY
      // 初始化二进制类型列表
      ImmutableList.Builder<RelDataType> builder7 = ImmutableList.builder(); // 创建列表构建器
      for (SqlTypeName typeName : SqlTypeName.BINARY_TYPES) { // 遍历所有二进制类型名称
        builder7.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      binaryTypes = builder7.build(); // 构建不可变的二进制类型列表
      // BOOLEAN
      // 初始化布尔类型列表
      ImmutableList.Builder<RelDataType> builder8 = ImmutableList.builder(); // 创建列表构建器
      for (SqlTypeName typeName : SqlTypeName.BOOLEAN_TYPES) { // 遍历所有布尔类型名称
        builder8.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      booleanTypes = builder8.build(); // 构建不可变的布尔类型列表
      // GEOMETRY
      // 初始化几何类型列表
      ImmutableList.Builder<RelDataType> builder9 = ImmutableList.builder(); // 创建列表构建器
      for (SqlTypeName typeName : SqlTypeName.GEOMETRY_TYPES) { // 遍历所有几何类型名称
        builder9.add(this.typeFactory.createSqlType(typeName)); // 创建对应的类型并添加到构建器
      }
      geometryTypes = builder9.build(); // 构建不可变的几何类型列表
    }

    public Fixture withTypeFactory(RelDataTypeFactory typeFactory) { // 公共方法,创建使用新类型工厂的Fixture实例
      return new Fixture(typeFactory, typeCoercion); // 返回新的Fixture实例,保持原有的类型强制转换器
    }

    //~ Tool methods -----------------------------------------------------------
    // 工具方法分隔符,以下是工具方法区域

    RelDataType arrayType(RelDataType type) { // 工具方法,创建数组类型
      return typeFactory.createArrayType(type, -1); // 创建指定元素类型的数组,-1表示未指定最大长度
    }

    RelDataType mapType(RelDataType keyType, RelDataType valType) { // 工具方法,创建MAP类型
      return typeFactory.createMapType(keyType, valType); // 创建指定键类型和值类型的MAP
    }

    RelDataType recordType(String name, RelDataType type) { // 工具方法,创建单字段记录类型
      return typeFactory.createStructType(ImmutableList.of(Pair.of(name, type))); // 创建包含一个字段的记录类型
    }

    RelDataType recordType(List<? extends Map.Entry<String, RelDataType>> pairs) { // 工具方法,创建多字段记录类型
      return typeFactory.createStructType(pairs); // 创建包含多个字段的记录类型
    }

    RelDataType decimalType(int precision, int scale) { // 工具方法,创建DECIMAL类型
      return typeFactory.createSqlType(SqlTypeName.DECIMAL, precision, scale); // 创建指定精度和小数位数的DECIMAL类型
    }

    /** Decision method for {@link AbstractTypeCoercion#implicitCast}. */
    // 判断方法,用于验证类型应该能够进行隐式转换
    // 该方法测试从源类型到目标类型家族的隐式转换是否能够成功,并验证转换后的类型是否符合期望
    private void shouldCast( // 定义私有方法
        RelDataType from, // 源类型参数
        SqlTypeFamily family, // 目标类型家族参数
        RelDataType expected) { // 期望的转换后类型参数
      if (family == null) { // 如果目标类型家族为null
        // ROW type do not have a family.
        // ROW类型没有类型家族,直接返回
        return; // 直接返回,不进行测试
      }
      RelDataType castedType = // 调用隐式转换方法
          ((AbstractTypeCoercion) typeCoercion).implicitCast(from, family); // 从源类型转换到目标类型家族
      String reason = "Failed to cast from " + from.getSqlTypeName() // 构建错误消息
          + " to " + family; // 包含源类型和目标类型家族
      assertThat(reason, castedType, notNullValue()); // 断言转换后的类型不为null
      assertThat(reason, // 断言转换后的类型符合期望
          from.equals(castedType) // 要么源类型等于转换后类型(无需转换)
              || SqlTypeUtil.equalSansNullability(typeFactory, castedType, expected) // 要么忽略可空性后相等
              || expected.getSqlTypeName().getFamily().contains(castedType), // 要么转换后类型属于期望类型的家族
          is(true)); // 断言结果为true
    }

    private void shouldNotCast( // 定义私有方法,验证类型不应该能够进行隐式转换
        RelDataType from, // 源类型参数
        SqlTypeFamily family) { // 目标类型家族参数
      if (family == null) { // 如果目标类型家族为null
        // ROW type do not have a family.
        // ROW类型没有类型家族,直接返回
        return; // 直接返回,不进行测试
      }
      RelDataType castedType = // 调用隐式转换方法
          ((AbstractTypeCoercion) typeCoercion).implicitCast(from, family); // 尝试从源类型转换到目标类型家族
      assertThat("Should not be able to cast from " + from.getSqlTypeName() // 断言不应该能够转换
          + " to " + family, // 包含源类型和目标类型家族
          castedType, nullValue()); // 断言转换后的类型为null(表示不能转换)
    }

    private void checkShouldCast(RelDataType checked, List<RelDataType> types) { // 定义私有方法,批量检查类型转换
      for (RelDataType type : allTypes) { // 遍历所有类型
        if (contains(types, type)) { // 如果类型在允许转换的列表中
          shouldCast(checked, type.getSqlTypeName().getFamily(), type); // 验证应该能够转换
        } else { // 如果类型不在允许转换的列表中
          shouldNotCast(checked, type.getSqlTypeName().getFamily()); // 验证不应该能够转换
        }
      }
    }

    // some data types has the same type family, i.e. TIMESTAMP and
    // TIMESTAMP_WITH_LOCAL_TIME_ZONE all have TIMESTAMP family.
    // 某些数据类型具有相同的类型家族,例如TIMESTAMP和TIMESTAMP_WITH_LOCAL_TIME_ZONE都属于TIMESTAMP家族
    private static boolean contains(List<RelDataType> types, RelDataType type) { // 定义私有静态方法,检查类型是否在列表中
      for (RelDataType type1 : types) { // 遍历类型列表
        if (type1.equals(type) // 如果类型完全相等
            || type1.getSqlTypeName().getFamily() == type.getSqlTypeName().getFamily()) { // 或者类型家族相同
          return true; // 返回true,表示包含
        }
      }
      return false; // 返回false,表示不包含
    }

    private String toStringNullable(@Nullable Object o1) { // 定义私有方法,将对象转换为字符串,处理null情况
      if (o1 == null) { // 如果对象为null
        return "NULL"; // 返回"NULL"字符串
      }
      return o1.toString(); // 否则返回对象的toString()结果
    }

    /** Decision method for finding a common type. */
    // 判断方法,用于查找两个类型的公共类型
    // 该方法测试getTightestCommonType方法,验证两个类型的最紧致公共类型是否符合期望
    private void checkCommonType( // 定义私有方法
        RelDataType type1, // 第一个类型参数
        RelDataType type2, // 第二个类型参数
        @Nullable RelDataType expected, // 期望的公共类型参数
        boolean isSymmetric) { // 是否对称参数,如果为true则测试type2和type1的顺序
      RelDataType result = typeCoercion.getTightestCommonType(type1, type2); // 调用方法获取公共类型
      assertThat("Expected " + toStringNullable(expected) // 断言公共类型符合期望
          + " as common type for " + type1.toString() // 包含两个类型
          + " and " + type2.toString() // 和期望结果
          + ", but found " + toStringNullable(result), // 以及实际结果
          result, // 断言实际结果
          sameInstance(expected)); // 与期望结果是同一个实例
      if (isSymmetric) { // 如果需要测试对称性
        RelDataType result1 = typeCoercion.getTightestCommonType(type2, type1); // 反转顺序调用方法
        assertThat("Expected " + toStringNullable(expected) // 断言反转后的结果也符合期望
            + " as common type for " + type2 // 包含反转后的两个类型
            + " and " + type1 // 和期望结果
            + ", but found " + toStringNullable(result1), // 以及实际结果
            result1, sameInstance(expected)); // 断言反转后的结果与期望结果是同一个实例
      }
    }

    private void comparisonCommonType( // 定义私有方法,查找比较操作的公共类型
        RelDataType type1, // 第一个类型参数
        RelDataType type2, // 第二个类型参数
        @Nullable RelDataType expected) { // 期望的比较公共类型参数
      RelDataType result = typeCoercion.commonTypeForBinaryComparison(type1, type2); // 调用方法获取比较公共类型
      assertThat("Expected " + toStringNullable(expected) // 断言比较公共类型符合期望
              + " as comparison common type for " + type1 // 包含两个类型
              + " and " + type2 // 和期望结果
              + ", but found " + toStringNullable(result), // 以及实际结果
          result, // 断言实际结果
          sameInstance(expected)); // 与期望结果是同一个实例
      RelDataType result1 = typeCoercion.commonTypeForBinaryComparison(type2, type1); // 反转顺序调用方法
      assertThat("Expected " + toStringNullable(expected) // 断言反转后的结果也符合期望
              + " as common type for " + type2 // 包含反转后的两个类型
              + " and " + type1 // 和期望结果
              + ", but found " + toStringNullable(result1), // 以及实际结果
          result1, sameInstance(expected)); // 断言反转后的结果与期望结果是同一个实例
    }

    /** Decision method for finding a wider type. */
    // 判断方法,用于查找两个类型中更宽的类型
    // 该方法测试getWiderTypeForTwo方法,验证两个类型的更宽类型是否符合期望
    private void checkWiderType( // 定义私有方法
        RelDataType type1, // 第一个类型参数
        RelDataType type2, // 第二个类型参数
        @Nullable RelDataType expected, // 期望的更宽类型参数
        boolean stringPromotion, // 是否允许字符串提升参数
        boolean symmetric) { // 是否对称参数,如果为true则测试type2和type1的顺序
      RelDataType result = // 调用方法获取更宽类型
          typeCoercion.getWiderTypeForTwo(type1, type2, stringPromotion); // 传入两个类型和字符串提升标志
      assertThat("Expected " // 断言更宽类型符合期望
          + toStringNullable(expected) // 包含期望结果
          + " as common type for " + type1.toString() // 和两个类型
          + " and " + type2.toString() // 以及实际结果
          + ", but found " + toStringNullable(result),
          result, sameInstance(expected)); // 断言实际结果与期望结果是同一个实例
      if (symmetric) { // 如果需要测试对称性
        RelDataType result1 = // 反转顺序调用方法
            typeCoercion.getWiderTypeForTwo(type2, type1, stringPromotion); // 传入反转后的两个类型
        assertThat("Expected " + toStringNullable(expected) // 断言反转后的结果也符合期望
            + " as common type for " + type2 // 包含反转后的两个类型
            + " and " + type1 // 和期望结果
            + ", but found " + toStringNullable(result1), // 以及实际结果
            result1, sameInstance(expected)); // 断言反转后的结果与期望结果是同一个实例
      }
    }
  }
}
