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
package org.apache.calcite.sql.type;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.RelRecordType;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test for {@link SqlTypeFactoryImpl}. // 测试类：用于测试SqlTypeFactoryImpl（SQL类型工厂实现类）的功能
 * 这个类包含了针对SQL类型工厂的各种测试用例，主要测试以下功能：
 * 1. 最小限制类型（leastRestrictive）的计算：确定多个类型中最通用的类型
 * 2. 类型精度（precision）的比较和最大值计算
 * 3. 数组类型（ARRAY）的优先级列表
 * 4. 结构化类型（STRUCT）的可空性设置
 * 5. Java Map类型的创建
 * 6. 带精度的时间戳类型创建
 * 7. UNKNOWN类型的一致性
 * 
 * 最小限制类型（Least Restrictive Type）是SQL类型系统中的一个重要概念，
 * 它指的是在多个类型中能够容纳所有类型值的"最通用"类型。
 * 例如：BIGINT和INTEGER的最小限制类型是BIGINT，因为BIGINT可以容纳INTEGER的所有值。
 */ // 测试类：用于测试SqlTypeFactoryImpl（SQL类型工厂实现类）的功能
class SqlTypeFactoryTest { // 测试类：用于测试SqlTypeFactoryImpl（SQL类型工厂实现类）的功能

  @Test void testLeastRestrictiveWithAny() { // 测试方法：测试包含ANY类型时的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive(Lists.newArrayList(f.sqlBigInt, f.sqlAny)); // 调用类型工厂的leastRestrictive方法，计算BIGINT和ANY的最小限制类型，ANY是万能类型，所以结果应该是ANY
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.ANY)); // 验证计算得到的最小限制类型是ANY类型
  } // 测试方法结束

  @Test void testLeastRestrictiveWithNumbers() { // 测试方法：测试数值类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive(Lists.newArrayList(f.sqlBigInt, f.sqlInt)); // 调用类型工厂的leastRestrictive方法，计算BIGINT和INTEGER的最小限制类型，BIGINT可以容纳INTEGER的所有值，所以结果应该是BIGINT
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.BIGINT)); // 验证计算得到的最小限制类型是BIGINT类型
  } // 测试方法结束

  @Test void testLeastRestrictiveWithNullability() { // 测试方法：测试包含可空类型时的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive(Lists.newArrayList(f.sqlVarcharNullable, f.sqlAny)); // 调用类型工厂的leastRestrictive方法，计算可空的VARCHAR和ANY的最小限制类型，ANY是万能类型，所以结果应该是ANY
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.ANY)); // 验证计算得到的最小限制类型是ANY类型
    assertThat(leastRestrictive.isNullable(), is(true)); // 验证计算得到的最小限制类型是可空的，因为输入类型中有可空类型
  } // 测试方法结束

  /** Test case for // 测试用例：用于测试
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2994">[CALCITE-2994] // JIRA问题链接：CALCITE-2994
   * Least restrictive type among structs does not consider nullability</a>. */ // 问题描述：结构类型之间的最小限制类型没有考虑可空性
  @Test void testLeastRestrictiveWithNullableStruct() { // 测试方法：测试包含可空结构类型时的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive(ImmutableList.of(f.structOfIntNullable, f.structOfInt)); // 调用类型工厂的leastRestrictive方法，计算可空的结构类型和不可空的结构类型的最小限制类型，结果应该是可空的结构类型
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.ROW)); // 验证计算得到的最小限制类型是ROW（行/结构）类型
    assertThat(leastRestrictive.isNullable(), is(true)); // 验证计算得到的最小限制类型是可空的，因为输入类型中有可空类型
  } // 测试方法结束

  @Test void testLeastRestrictiveWithNull() { // 测试方法：测试包含NULL类型时的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive(Lists.newArrayList(f.sqlNull, f.sqlNull)); // 调用类型工厂的leastRestrictive方法，计算两个NULL类型的最小限制类型，结果应该是NULL类型
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.NULL)); // 验证计算得到的最小限制类型是NULL类型
    assertThat(leastRestrictive.isNullable(), is(true)); // 验证计算得到的最小限制类型是可空的，NULL类型本身是可空的
  } // 测试方法结束

  @Test void testLeastRestrictiveStructWithNull() { // 测试方法：测试NULL和结构类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive(Lists.newArrayList(f.sqlNull, f.structOfInt)); // 调用类型工厂的leastRestrictive方法，计算NULL和结构类型的最小限制类型，结果应该是可空的结构类型
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.ROW)); // 验证计算得到的最小限制类型是ROW（行/结构）类型
    assertThat(leastRestrictive.isNullable(), is(true)); // 验证计算得到的最小限制类型是可空的，因为NULL类型是可空的
  } // 测试方法结束

  @Test void testLeastRestrictiveForImpossibleWithArray() { // 测试方法：测试不兼容类型（数组和字符）之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算数组和字符类型的最小限制类型
            Lists.newArrayList(f.arraySqlChar10, f.sqlChar)); // 数组和字符类型不兼容，无法找到共同的最小限制类型
    assertNull(leastRestrictive); // 验证计算得到的最小限制类型为null，因为数组和字符类型不兼容
  } // 测试方法结束

  @Test void testLeastRestrictiveForArrays() { // 测试方法：测试数组类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算两个数组类型的最小限制类型
            Lists.newArrayList(f.arraySqlChar10, f.arraySqlChar1)); // 两个数组类型都是CHAR数组，只是精度不同，结果应该是精度更大的数组类型
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.ARRAY)); // 验证计算得到的最小限制类型是ARRAY（数组）类型
    assertThat(leastRestrictive.isNullable(), is(false)); // 验证计算得到的最小限制类型是不可空的，因为输入的数组类型都不可空
    assertThat(leastRestrictive.getComponentType().getPrecision(), is(10)); // 验证计算得到的数组元素类型的精度是10，取两个数组中精度更大的
  } // 测试方法结束

  @Test void testLeastRestrictiveForMultisets() { // 测试方法：测试多重集类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算两个多重集类型的最小限制类型
            Lists.newArrayList(f.multisetSqlChar10Nullable, f.multisetSqlChar1)); // 两个多重集类型都是CHAR多重集，只是精度不同，结果应该是精度更大的可空多重集类型
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.MULTISET)); // 验证计算得到的最小限制类型是MULTISET（多重集）类型
    assertThat(leastRestrictive.isNullable(), is(true)); // 验证计算得到的最小限制类型是可空的，因为输入的多重集类型中有可空类型
    assertThat(leastRestrictive.getComponentType().getPrecision(), is(10)); // 验证计算得到的多重集元素类型的精度是10，取两个多重集中精度更大的
  } // 测试方法结束

  @Test void testLeastRestrictiveForMultisetsAndArrays() { // 测试方法：测试多重集和数组类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算多重集和数组类型的最小限制类型
            Lists.newArrayList(f.multisetSqlChar10Nullable, f.arraySqlChar1)); // 多重集和数组类型可以兼容，结果应该是多重集类型（因为多重集更通用）
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.MULTISET)); // 验证计算得到的最小限制类型是MULTISET（多重集）类型
    assertThat(leastRestrictive.isNullable(), is(true)); // 验证计算得到的最小限制类型是可空的，因为输入的多重集类型是可空的
    assertThat(leastRestrictive.getComponentType().getPrecision(), is(10)); // 验证计算得到的元素类型的精度是10，取两个类型中精度更大的
  } // 测试方法结束

  @Test void testLeastRestrictiveForImpossibleWithMultisets() { // 测试方法：测试不兼容类型（多重集和映射）之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算多重集和映射类型的最小限制类型
            Lists.newArrayList(f.multisetSqlChar10Nullable, f.mapSqlChar1)); // 多重集和映射类型不兼容，无法找到共同的最小限制类型
    assertNull(leastRestrictive); // 验证计算得到的最小限制类型为null，因为多重集和映射类型不兼容
  } // 测试方法结束

  @Test void testLeastRestrictiveForMaps() { // 测试方法：测试映射类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算两个映射类型的最小限制类型
            Lists.newArrayList(f.mapSqlChar10Nullable, f.mapSqlChar1)); // 两个映射类型都是CHAR映射，只是精度不同，结果应该是精度更大的可空映射类型
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.MAP)); // 验证计算得到的最小限制类型是MAP（映射）类型
    assertThat(leastRestrictive.isNullable(), is(true)); // 验证计算得到的最小限制类型是可空的，因为输入的映射类型中有可空类型
    assertThat(leastRestrictive.getKeyType().getPrecision(), is(10)); // 验证计算得到的映射键类型的精度是10，取两个映射中精度更大的
    assertThat(leastRestrictive.getValueType().getPrecision(), is(10)); // 验证计算得到的映射值类型的精度是10，取两个映射中精度更大的
  } // 测试方法结束

  @Test void testLeastRestrictiveForTimestamps() { // 测试方法：测试时间戳类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算两个时间戳类型的最小限制类型
            Lists.newArrayList(f.sqlTimestampPrec0, f.sqlTimestampPrec3)); // 两个时间戳类型精度不同，结果应该是精度更大的时间戳类型
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.TIMESTAMP)); // 验证计算得到的最小限制类型是TIMESTAMP（时间戳）类型
    assertThat(leastRestrictive.isNullable(), is(false)); // 验证计算得到的最小限制类型是不可空的，因为输入的时间戳类型都不可空
    assertThat(leastRestrictive.getPrecision(), is(3)); // 验证计算得到的时间戳类型的精度是3，取两个时间戳中精度更大的
  } // 测试方法结束

  @Test void testLeastRestrictiveForTimestamps2() { // 测试方法：测试时间戳类型之间的最小限制类型计算（顺序相反）
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算两个时间戳类型的最小限制类型
            Lists.newArrayList(f.sqlTimestampPrec3, f.sqlTimestampPrec0)); // 两个时间戳类型精度不同，结果应该是精度更大的时间戳类型（顺序不影响结果）
    assertThat(leastRestrictive.getSqlTypeName(), is(SqlTypeName.TIMESTAMP)); // 验证计算得到的最小限制类型是TIMESTAMP（时间戳）类型
    assertThat(leastRestrictive.isNullable(), is(false)); // 验证计算得到的最小限制类型是不可空的，因为输入的时间戳类型都不可空
    assertThat(leastRestrictive.getPrecision(), is(3)); // 验证计算得到的时间戳类型的精度是3，取两个时间戳中精度更大的
  } // 测试方法结束

  @Test void testLeastRestrictiveForTimestampAndDate() { // 测试方法：测试时间戳和日期类型之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算时间戳和日期类型的最小限制类型
            Lists.newArrayList(f.sqlTimestampPrec3, f.sqlDate)); // 时间戳和日期类型不兼容，无法找到共同的最小限制类型
    assertNull(leastRestrictive); // 验证计算得到的最小限制类型为null，因为时间戳和日期类型不兼容
  } // 测试方法结束

  @Test void testLeastRestrictiveForImpossibleWithMaps() { // 测试方法：测试不兼容类型（映射和数组）之间的最小限制类型计算
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType leastRestrictive = // 声明一个RelDataType变量，用于存储计算得到的最小限制类型
        f.typeFactory.leastRestrictive( // 调用类型工厂的leastRestrictive方法，计算映射和数组类型的最小限制类型
            Lists.newArrayList(f.mapSqlChar10Nullable, f.arraySqlChar1)); // 映射和数组类型不兼容，无法找到共同的最小限制类型
    assertNull(leastRestrictive); // 验证计算得到的最小限制类型为null，因为映射和数组类型不兼容
  } // 测试方法结束

  /** Unit test for {@link SqlTypeUtil#comparePrecision(int, int)} // 单元测试：用于测试SqlTypeUtil类的comparePrecision方法（比较两个精度值）
   * and  {@link SqlTypeUtil#maxPrecision(int, int)}. */ // 和SqlTypeUtil类的maxPrecision方法（计算两个精度值的最大值）
  @Test void testMaxPrecision() { // 测试方法：测试精度比较和最大值计算
    final int un = RelDataType.PRECISION_NOT_SPECIFIED; // 声明一个常量，表示精度未指定
    checkPrecision(1, 1, 1, 0); // 调用checkPrecision方法验证：两个相同的精度1，最大精度是1，比较结果是0（相等）
    checkPrecision(2, 1, 2, 1); // 调用checkPrecision方法验证：精度2和1，最大精度是2，比较结果是1（2大于1）
    checkPrecision(2, 100, 100, -1); // 调用checkPrecision方法验证：精度2和100，最大精度是100，比较结果是-1（2小于100）
    checkPrecision(2, un, un, -1); // 调用checkPrecision方法验证：精度2和未指定，最大精度是未指定，比较结果是-1（已指定小于未指定）
    checkPrecision(un, 2, un, 1); // 调用checkPrecision方法验证：未指定和精度2，最大精度是未指定，比较结果是1（未指定大于已指定）
    checkPrecision(un, un, un, 0); // 调用checkPrecision方法验证：两个未指定，最大精度是未指定，比较结果是0（相等）
  } // 测试方法结束

  /** Unit test for {@link ArraySqlType#getPrecedenceList()}. */ // 单元测试：用于测试ArraySqlType类的getPrecedenceList方法（获取数组类型的优先级列表）
  @Test void testArrayPrecedenceList() { // 测试方法：测试数组类型的优先级列表
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    assertThat(checkPrecendenceList(f.arrayBigInt, f.arrayBigInt, f.arrayFloat), // 验证数组BIGINT类型在比较两个数组BIGINT和数组FLOAT时的优先级
        is(3)); // 预期结果是3，表示type1和type2都在优先级列表中且type2更通用
    assertThat( // 验证二维数组BIGINT类型在比较两个二维数组BIGINT和二维数组FLOAT时的优先级
        checkPrecendenceList(f.arrayOfArrayBigInt, f.arrayOfArrayBigInt, // 调用checkPrecendenceList方法
            f.arrayOfArrayFloat), is(3)); // 预期结果是3，表示type1和type2都在优先级列表中且type2更通用
    assertThat(checkPrecendenceList(f.sqlBigInt, f.sqlBigInt, f.sqlFloat), // 验证BIGINT类型在比较两个BIGINT和FLOAT时的优先级
        is(3)); // 预期结果是3，表示type1和type2都在优先级列表中且type2更通用
    assertThat( // 验证多重集BIGINT类型在比较两个多重集BIGINT和多重集FLOAT时的优先级
        checkPrecendenceList(f.multisetBigInt, f.multisetBigInt, // 调用checkPrecendenceList方法
            f.multisetFloat), is(3)); // 预期结果是3，表示type1和type2都在优先级列表中且type2更通用
    assertThat( // 验证数组BIGINT类型在比较两个数组BIGINT和可空数组BIGINT时的优先级
        checkPrecendenceList(f.arrayBigInt, f.arrayBigInt, // 调用checkPrecendenceList方法
            f.arrayBigIntNullable), is(0)); // 预期结果是0，表示type1和type2相等
    try { // 尝试执行以下代码
      int i = checkPrecendenceList(f.arrayBigInt, f.sqlBigInt, f.sqlInt); // 调用checkPrecendenceList方法，比较数组BIGINT、BIGINT和INTEGER
      fail("Expected assert, got " + i); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException异常
      assertThat(e.getMessage(), is("must contain type: BIGINT")); // 验证异常消息是"must contain type: BIGINT"，表示BIGINT类型不在优先级列表中
    } // try-catch块结束
  } // 测试方法结束

  private int checkPrecendenceList(RelDataType t, RelDataType type1, RelDataType type2) { // 私有辅助方法：检查类型t的优先级列表中type1和type2的优先级关系
    return t.getPrecedenceList().compareTypePrecedence(type1, type2); // 返回比较结果：-1表示type1优先级低于type2，0表示相等，1表示type1优先级高于type2，3表示type2更通用
  } // 私有辅助方法结束

  private void checkPrecision(int p0, int p1, int expectedMax, // 私有辅助方法：验证精度比较和最大值计算的正确性
      int expectedComparison) { // 参数：p0-第一个精度值，p1-第二个精度值，expectedMax-预期的最大精度，expectedComparison-预期的比较结果
    assertThat(SqlTypeUtil.maxPrecision(p0, p1), is(expectedMax)); // 验证maxPrecision(p0, p1)的结果是expectedMax
    assertThat(SqlTypeUtil.maxPrecision(p1, p0), is(expectedMax)); // 验证maxPrecision(p1, p0)的结果是expectedMax（顺序不影响结果）
    assertThat(SqlTypeUtil.maxPrecision(p0, p0), is(p0)); // 验证maxPrecision(p0, p0)的结果是p0
    assertThat(SqlTypeUtil.maxPrecision(p1, p1), is(p1)); // 验证maxPrecision(p1, p1)的结果是p1
    assertThat(SqlTypeUtil.comparePrecision(p0, p1), is(expectedComparison)); // 验证comparePrecision(p0, p1)的结果是expectedComparison
    assertThat(SqlTypeUtil.comparePrecision(p0, p0), is(0)); // 验证comparePrecision(p0, p0)的结果是0（相等）
    assertThat(SqlTypeUtil.comparePrecision(p1, p1), is(0)); // 验证comparePrecision(p1, p1)的结果是0（相等）
  } // 私有辅助方法结束

  /** Test case for // 测试用例：用于测试
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2464">[CALCITE-2464] // JIRA问题链接：CALCITE-2464
   * Allow to set nullability for columns of structured types</a>. */ // 问题描述：允许为结构类型的列设置可空性
  @Test void createStructTypeWithNullability() { // 测试方法：测试创建带可空性的结构类型
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataTypeFactory typeFactory = f.typeFactory; // 获取类型工厂实例
    List<RelDataTypeField> fields = new ArrayList<>(); // 创建字段列表，用于存储结构类型的字段
    RelDataTypeField field0 = // 创建第一个字段
        new RelDataTypeFieldImpl("i", 0, // 字段名称为"i"，索引为0
            typeFactory.createSqlType(SqlTypeName.INTEGER)); // 字段类型为INTEGER
    RelDataTypeField field1 = // 创建第二个字段
        new RelDataTypeFieldImpl("s", 1, // 字段名称为"s"，索引为1
            typeFactory.createSqlType(SqlTypeName.VARCHAR)); // 字段类型为VARCHAR
    fields.add(field0); // 将第一个字段添加到字段列表中
    fields.add(field1); // 将第二个字段添加到字段列表中
    final RelDataType recordType = new RelRecordType(fields); // 使用字段列表创建记录类型，默认不可空
    final RelDataType copyRecordType = // 创建记录类型的可空副本
        typeFactory.createTypeWithNullability(recordType, true); // 调用createTypeWithNullability方法，将recordType设置为可空
    assertFalse(recordType.isNullable()); // 验证原始记录类型是不可空的
    assertTrue(copyRecordType.isNullable()); // 验证副本记录类型是可空的
  } // 测试方法结束

  /** Test case for // 测试用例：用于测试
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3429">[CALCITE-3429] // JIRA问题链接：CALCITE-3429
   * AssertionError thrown for user-defined table function with map argument</a>. */ // 问题描述：用户定义的表函数使用map参数时抛出AssertionError
  @Test void testCreateTypeWithJavaMapType() { // 测试方法：测试使用Java Map类创建SQL类型
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    RelDataType relDataType = f.typeFactory.createJavaType(Map.class); // 使用Java Map类创建对应的SQL类型
    assertThat(relDataType.getSqlTypeName(), is(SqlTypeName.MAP)); // 验证创建的SQL类型是MAP类型
    assertThat(relDataType.getKeyType().getSqlTypeName(), is(SqlTypeName.ANY)); // 验证Map的键类型是ANY类型（因为Java Map的键类型是泛型的）

    try { // 尝试执行以下代码
      f.typeFactory.createSqlType(SqlTypeName.MAP); // 调用createSqlType方法创建MAP类型，这是不允许的
      fail(); // 如果没有抛出异常，则测试失败
    } catch (AssertionError e) { // 捕获AssertionError异常
      assertThat(e.getMessage(), is("use createMapType() instead")); // 验证异常消息是"use createMapType() instead"，表示应该使用createMapType方法而不是createSqlType方法
    } // try-catch块结束
  } // 测试方法结束

  /** Test case for // 测试用例：用于测试
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3924">[CALCITE-3924] // JIRA问题链接：CALCITE-3924
   * Fix flakey test to handle TIMESTAMP and TIMESTAMP(0) correctly</a>. */ // 问题描述：修复不稳定测试，正确处理TIMESTAMP和TIMESTAMP(0)
  @Test void testCreateSqlTypeWithPrecision() { // 测试方法：测试创建带精度的时间类型
    SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型
    checkCreateSqlTypeWithPrecision(f.typeFactory, SqlTypeName.TIME); // 验证TIME类型的不同精度创建
    checkCreateSqlTypeWithPrecision(f.typeFactory, SqlTypeName.TIMESTAMP); // 验证TIMESTAMP类型的不同精度创建
    checkCreateSqlTypeWithPrecision(f.typeFactory, SqlTypeName.TIME_WITH_LOCAL_TIME_ZONE); // 验证TIME_WITH_LOCAL_TIME_ZONE类型的不同精度创建
    checkCreateSqlTypeWithPrecision(f.typeFactory, SqlTypeName.TIME_TZ); // 验证TIME_TZ类型的不同精度创建
    checkCreateSqlTypeWithPrecision(f.typeFactory, SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE); // 验证TIMESTAMP_WITH_LOCAL_TIME_ZONE类型的不同精度创建
    checkCreateSqlTypeWithPrecision(f.typeFactory, SqlTypeName.TIMESTAMP_TZ); // 验证TIMESTAMP_TZ类型的不同精度创建
  } // 测试方法结束

  private void checkCreateSqlTypeWithPrecision( // 私有辅助方法：验证创建带精度的SQL类型的行为
      RelDataTypeFactory typeFactory, SqlTypeName sqlTypeName) { // 参数：typeFactory-类型工厂，sqlTypeName-要测试的SQL类型名称
    RelDataType ts = typeFactory.createSqlType(sqlTypeName); // 创建不带精度参数的类型（使用默认精度）
    RelDataType tsWithoutPrecision = typeFactory.createSqlType(sqlTypeName, -1); // 创建精度为-1的类型（表示未指定精度）
    RelDataType tsWithPrecision0 = typeFactory.createSqlType(sqlTypeName, 0); // 创建精度为0的类型
    RelDataType tsWithPrecision1 = typeFactory.createSqlType(sqlTypeName, 1); // 创建精度为1的类型
    RelDataType tsWithPrecision2 = typeFactory.createSqlType(sqlTypeName, 2); // 创建精度为2的类型
    RelDataType tsWithPrecision3 = typeFactory.createSqlType(sqlTypeName, 3); // 创建精度为3的类型
    // for instance, 8 exceeds max precision for timestamp which is 3 // 例如，8超过了timestamp的最大精度3
    RelDataType tsWithPrecision8 = typeFactory.createSqlType(sqlTypeName, 8); // 创建精度为8的类型（超过最大精度，会被截断到最大精度）

    assertThat(ts, hasToString(sqlTypeName.getName() + "(0)")); // 验证默认精度是0
    assertThat(ts.getFullTypeString(), is(sqlTypeName.getName() + "(0) NOT NULL")); // 验证完整类型字符串包含精度和NOT NULL
    assertThat(tsWithoutPrecision, hasToString(sqlTypeName.getName())); // 验证精度为-1时不显示精度
    assertThat(tsWithoutPrecision.getFullTypeString(), is(sqlTypeName.getName() + " NOT NULL")); // 验证完整类型字符串不包含精度
    assertThat(tsWithPrecision0, hasToString(sqlTypeName.getName() + "(0)")); // 验证精度为0时显示精度
    assertThat(tsWithPrecision0.getFullTypeString(), is(sqlTypeName.getName() + "(0) NOT NULL")); // 验证完整类型字符串包含精度和NOT NULL
    assertThat(tsWithPrecision1, hasToString(sqlTypeName.getName() + "(1)")); // 验证精度为1时显示精度
    assertThat(tsWithPrecision1.getFullTypeString(), is(sqlTypeName.getName() + "(1) NOT NULL")); // 验证完整类型字符串包含精度和NOT NULL
    assertThat(tsWithPrecision2, hasToString(sqlTypeName.getName() + "(2)")); // 验证精度为2时显示精度
    assertThat(tsWithPrecision2.getFullTypeString(), is(sqlTypeName.getName() + "(2) NOT NULL")); // 验证完整类型字符串包含精度和NOT NULL
    assertThat(tsWithPrecision3, hasToString(sqlTypeName.getName() + "(3)")); // 验证精度为3时显示精度
    assertThat(tsWithPrecision3.getFullTypeString(), is(sqlTypeName.getName() + "(3) NOT NULL")); // 验证完整类型字符串包含精度和NOT NULL
    assertThat(tsWithPrecision8, hasToString(sqlTypeName.getName() + "(3)")); // 验证精度为8时被截断到最大精度3
    assertThat(tsWithPrecision8.getFullTypeString(), is(sqlTypeName.getName() + "(3) NOT NULL")); // 验证完整类型字符串显示最大精度和NOT NULL

    assertThat(ts != tsWithoutPrecision, is(true)); // 验证默认精度类型和未指定精度类型不是同一个对象
    assertThat(ts == tsWithPrecision0, is(true)); // 验证默认精度类型和精度0类型是同一个对象（类型工厂会缓存）
    assertThat(tsWithPrecision3 == tsWithPrecision8, is(true)); // 验证精度3类型和精度8类型（被截断到3）是同一个对象（类型工厂会缓存）
  } // 私有辅助方法结束

  /** Test that the {@code UNKNOWN} type is a {@link BasicSqlType} and remains // 测试：验证UNKNOWN类型是BasicSqlType类型，并且在设置可空性后仍然保持为BasicSqlType类型
   * so when nullified. */ // 设置可空性后
  @Test void testUnknownCreateWithNullabilityTypeConsistency() { // 测试方法：测试UNKNOWN类型的可空性设置一致性
    final SqlTypeFixture f = new SqlTypeFixture(); // 创建SQL类型测试夹具，用于提供各种预定义的SQL类型

    final RelDataType unknownType = f.typeFactory.createUnknownType(); // 创建UNKNOWN类型
    assertThat(unknownType, instanceOf(BasicSqlType.class)); // 验证UNKNOWN类型是BasicSqlType类的实例
    assertThat(unknownType.getSqlTypeName(), is(SqlTypeName.UNKNOWN)); // 验证UNKNOWN类型的类型名称是UNKNOWN
    assertFalse(unknownType.isNullable()); // 验证UNKNOWN类型默认是不可空的
    assertThat(unknownType.getFullTypeString(), is("UNKNOWN NOT NULL")); // 验证UNKNOWN类型的完整类型字符串是"UNKNOWN NOT NULL"

    final RelDataType nullableType = // 创建UNKNOWN类型的可空副本
        f.typeFactory.createTypeWithNullability(unknownType, true); // 调用createTypeWithNullability方法，将unknownType设置为可空
    assertThat(nullableType, instanceOf(BasicSqlType.class)); // 验证可空的UNKNOWN类型仍然是BasicSqlType类的实例
    assertThat(nullableType.getSqlTypeName(), is(SqlTypeName.UNKNOWN)); // 验证可空的UNKNOWN类型的类型名称仍然是UNKNOWN
    assertTrue(nullableType.isNullable()); // 验证可空的UNKNOWN类型是可空的
    assertThat(nullableType.getFullTypeString(), is("UNKNOWN")); // 验证可空的UNKNOWN类型的完整类型字符串是"UNKNOWN"（不包含NOT NULL）

    final RelDataType unknownType2 = // 将可空的UNKNOWN类型设置回不可空
        f.typeFactory.createTypeWithNullability(nullableType, false); // 调用createTypeWithNullability方法，将nullableType设置为不可空
    assertThat(unknownType2, is(unknownType)); // 验证设置回不可空后，类型对象与原始的unknownType是同一个对象
    assertThat(unknownType2, instanceOf(BasicSqlType.class)); // 验证设置回不可空后，类型仍然是BasicSqlType类的实例
    assertThat(unknownType2.getSqlTypeName(), is(SqlTypeName.UNKNOWN)); // 验证设置回不可空后，类型名称仍然是UNKNOWN
    assertFalse(unknownType2.isNullable()); // 验证设置回不可空后，类型是不可空的
    assertThat(unknownType2.getFullTypeString(), is("UNKNOWN NOT NULL")); // 验证设置回不可空后，完整类型字符串是"UNKNOWN NOT NULL"
  } // 测试方法结束
} // 测试类结束
