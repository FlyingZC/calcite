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
package org.apache.calcite.jdbc; // 声明包名，表示该类属于 org.apache.calcite.jdbc 包

import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 类，用于表示关系数据类型
import org.apache.calcite.sql.test.SqlTests; // 导入 SqlTests 类，用于 SQL 类型测试工具
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SqlTypeName 枚举，表示 SQL 类型名称

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类，用于创建不可变列表

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.lang.reflect.Type; // 导入 Java 反射的 Type 接口，表示 Java 类型

import static org.apache.calcite.linq4j.tree.Types.RecordType; // 静态导入 RecordType 类，用于表示记录类型

import static org.hamcrest.CoreMatchers.is; // 静态导入 is 匹配器，用于断言相等
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入 assertThat 方法，用于断言
import static org.junit.jupiter.api.Assertions.assertTrue; // 静态导入 assertTrue 方法，用于断言为真

/**
 * Test for {@link org.apache.calcite.jdbc.JavaTypeFactoryImpl}. // JavaTypeFactoryImpl 的测试类
 * 该测试类用于验证 JavaTypeFactoryImpl 的功能，特别是结构体类型与 Java 类之间的映射关系
 * 主要测试场景包括：
 * 1. 单字段结构体类型的映射（通过 Java 类和通过字段列表两种方式）
 * 2. 双字段结构体类型的映射（通过 Java 类和通过字段列表两种方式）
 * 3. Java 类型转换为 SQL 类型后的字段可空性处理
 * 这些测试确保了 Calcite 能够正确处理不同结构体类型与 Java 类型之间的转换
 */
public final class JavaTypeFactoryTest { // 定义测试类，使用 final 修饰防止被继承
  private static final JavaTypeFactoryImpl TYPE_FACTORY = new JavaTypeFactoryImpl(); // 创建静态常量类型工厂实例，用于所有测试方法共享

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2677">[CALCITE-2677] // 引用 JIRA 问题链接
   * Struct types with one field are not mapped correctly to Java Classes</a>. // 问题描述：单字段结构体类型无法正确映射到 Java 类
   * 该测试验证通过 Java 类创建的单字段结构体类型能够正确映射回原始的 Java 类
   * 这是 CALCITE-2677 问题的第一个测试版本（V1），使用 Java 类作为结构体类型的定义 */  
  @Test void testGetJavaClassWithOneFieldStructDataTypeV1() { // 使用 @Test 注解标记测试方法，测试单字段结构体类型映射（版本1）
    RelDataType structWithOneField = TYPE_FACTORY.createStructType(OneFieldStruct.class); // 通过 Java 类创建单字段结构体类型，OneFieldStruct 是包含一个字段的内部类
    assertThat(TYPE_FACTORY.getJavaClass(structWithOneField), // 断言获取的 Java 类等于原始的 OneFieldStruct 类
        is(OneFieldStruct.class)); // 使用 is 匹配器验证类型一致性
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2677">[CALCITE-2677] // 引用 JIRA 问题链接
   * Struct types with one field are not mapped correctly to Java Classes</a>. // 问题描述：单字段结构体类型无法正确映射到 Java 类
   * 该测试验证通过字段列表创建的单字段结构体类型能够正确映射为 RecordType
   * 这是 CALCITE-2677 问题的第二个测试版本（V2），使用字段列表作为结构体类型的定义 */
  @Test void testGetJavaClassWithOneFieldStructDataTypeV2() { // 使用 @Test 注解标记测试方法，测试单字段结构体类型映射（版本2）
    RelDataType structWithOneField = // 声明结构体类型变量
        TYPE_FACTORY.createStructType( // 通过类型工厂创建结构体类型
            ImmutableList.of(TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER)), // 创建包含一个 INTEGER 类型的字段列表
            ImmutableList.of("intField")); // 创建字段名称列表，包含一个字段名 "intField"
    assertRecordType(TYPE_FACTORY.getJavaClass(structWithOneField)); // 断言获取的 Java 类型是 RecordType 类型
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2677">[CALCITE-2677] // 引用 JIRA 问题链接
   * Struct types with one field are not mapped correctly to Java Classes</a>. // 问题描述：单字段结构体类型无法正确映射到 Java 类
   * 该测试验证通过 Java 类创建的双字段结构体类型能够正确映射回原始的 Java 类
   * 这是 CALCITE-2677 问题的扩展测试，验证双字段结构体类型的映射是否正常 */
  @Test void testGetJavaClassWithTwoFieldsStructDataType() { // 使用 @Test 注解标记测试方法，测试双字段结构体类型映射
    RelDataType structWithTwoFields = TYPE_FACTORY.createStructType(TwoFieldStruct.class); // 通过 Java 类创建双字段结构体类型，TwoFieldStruct 是包含两个字段的内部类
    assertThat(TYPE_FACTORY.getJavaClass(structWithTwoFields), // 断言获取的 Java 类等于原始的 TwoFieldStruct 类
        is(TwoFieldStruct.class)); // 使用 is 匹配器验证类型一致性
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2677">[CALCITE-2677] // 引用 JIRA 问题链接
   * Struct types with one field are not mapped correctly to Java Classes</a>. // 问题描述：单字段结构体类型无法正确映射到 Java 类
   * 该测试验证通过字段列表创建的双字段结构体类型能够正确映射为 RecordType
   * 这是 CALCITE-2677 问题的扩展测试版本（V2），使用字段列表作为结构体类型的定义 */
  @Test void testGetJavaClassWithTwoFieldsStructDataTypeV2() { // 使用 @Test 注解标记测试方法，测试双字段结构体类型映射（版本2）
    RelDataType structWithTwoFields = // 声明结构体类型变量
        TYPE_FACTORY.createStructType( // 通过类型工厂创建结构体类型
            ImmutableList.of(TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER), // 创建包含 INTEGER 类型的字段
                TYPE_FACTORY.createSqlType(SqlTypeName.VARCHAR)), // 创建包含 VARCHAR 类型的字段
        ImmutableList.of("intField", "strField")); // 创建字段名称列表，包含两个字段名 "intField" 和 "strField"
    assertRecordType(TYPE_FACTORY.getJavaClass(structWithTwoFields)); // 断言获取的 Java 类型是 RecordType 类型
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3029">[CALCITE-3029] // 引用 JIRA 问题链接
   * Java-oriented field type is wrongly forced to be NOT NULL after being converted to // 问题描述：Java 类型的字段在转换为 SQL 类型后错误地被强制为 NOT NULL
   * SQL-oriented</a>. // Java 类型转换为 SQL 类型后的字段可空性处理
   * 该测试验证 Java 基本类型（如 int）在转换为 SQL 类型后应该被标记为 NOT NULL
   * 而包装类型（如 Integer）在转换为 SQL 类型后应该保持可空性
   * 这是 CALCITE-3029 问题的测试，确保类型转换时的可空性语义正确 */
  @Test void testFieldNullabilityAfterConvertingToSqlStructType() { // 使用 @Test 注解标记测试方法，测试字段可空性转换
    RelDataType javaStructType = // 声明 Java 结构体类型变量
        TYPE_FACTORY.createStructType( // 通过类型工厂创建结构体类型
            ImmutableList.of(TYPE_FACTORY.createJavaType(Integer.class), // 创建 Integer 类型字段（可空）
                TYPE_FACTORY.createJavaType(int.class)), // 创建 int 类型字段（不可空，基本类型）
        ImmutableList.of("a", "b")); // 创建字段名称列表，包含两个字段名 "a" 和 "b"
    RelDataType sqlStructType = TYPE_FACTORY.toSql(javaStructType); // 将 Java 结构体类型转换为 SQL 结构体类型
    assertThat(SqlTests.getTypeString(sqlStructType), // 断言 SQL 类型的字符串表示
        is("RecordType(INTEGER a, INTEGER NOT NULL b) NOT NULL")); // 验证字段 a 是可空的 INTEGER，字段 b 是 NOT NULL 的 INTEGER，整个结构体是 NOT NULL 的
  }

  private void assertRecordType(Type actual) { // 私有辅助方法，用于断言类型是 RecordType 类型
    assertTrue(actual instanceof RecordType, // 断言实际类型是 RecordType 的实例
        () -> "Type {" + actual.getTypeName() + "} is not a subtype of Types.RecordType"); // 如果断言失败，提供详细的错误信息，显示类型名称
  }

  /** Struct with one field. */ // 单字段结构体类，用于测试
  private static class OneFieldStruct { // 定义静态内部类，表示单字段结构体
    public Integer intField; // 定义一个公共的 Integer 类型字段，用于测试单字段结构体类型的映射
  }

  /** Struct with two fields. */ // 双字段结构体类，用于测试
  private static class TwoFieldStruct { // 定义静态内部类，表示双字段结构体
    public Integer intField; // 定义一个公共的 Integer 类型字段
    public String strField; // 定义一个公共的 String 类型字段，用于测试双字段结构体类型的映射
  }
}
