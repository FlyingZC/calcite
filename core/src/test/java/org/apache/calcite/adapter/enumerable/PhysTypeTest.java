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
package org.apache.calcite.adapter.enumerable; // 定义包名，该类属于org.apache.calcite.adapter.enumerable包，用于测试可枚举适配器中的物理类型实现

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口，用于创建Java类型
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，LINQ4J框架的核心接口
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示LINQ表达式树
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL类型

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.junit.jupiter.api.Test; // 导入JUnit5的测试注解

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具类

/**
 * Test for {@link org.apache.calcite.adapter.enumerable.PhysTypeImpl}. // PhysTypeImpl类的测试类，用于测试物理类型实现的各种功能
 * 该测试类主要验证PhysTypeImpl在处理结构体类型时的正确性，特别是针对单字段和多字段结构体的类型映射
 */
public final class PhysTypeTest { // 定义最终的测试类PhysTypeTest，用于测试PhysTypeImpl的功能
  private static final JavaTypeFactory TYPE_FACTORY = new JavaTypeFactoryImpl(); // 静态常量：Java类型工厂实例，用于在测试中创建各种SQL和Java类型

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2677">[CALCITE-2677] // 关联JIRA问题CALCITE-2677
   * Struct types with one field are not mapped correctly to Java Classes</a>. */ // 问题描述：单字段结构体类型无法正确映射到Java类
  @Test void testFieldClassOnColumnOfOneFieldStructType() { // 测试方法：测试单字段结构体类型的字段类映射
    RelDataType columnType = // 定义列类型，该列本身是一个结构体类型
        TYPE_FACTORY.createStructType( // 创建结构体类型
            ImmutableList.of(TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER)), // 结构体包含一个INTEGER类型的字段
            ImmutableList.of("intField")); // 字段名称为"intField"
    RelDataType rowType = // 定义行类型，该行包含一个结构体类型的列
        TYPE_FACTORY.createStructType(ImmutableList.of(columnType), // 创建结构体类型，包含一个columnType类型的列
            ImmutableList.of("structField")); // 列名称为"structField"

    PhysType rowPhysType = // 创建物理类型对象，用于表示行的物理表示
        PhysTypeImpl.of(TYPE_FACTORY, rowType, JavaRowFormat.ARRAY); // 使用ARRAY格式创建PhysTypeImpl实例
    assertThat(rowPhysType.fieldClass(0), is(Object[].class)); // 断言：验证第0个字段的Java类是Object[].class，确保单字段结构体被正确映射
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2677">[CALCITE-2677] // 关联JIRA问题CALCITE-2677
   * Struct types with one field are not mapped correctly to Java Classes</a>. */ // 问题描述：单字段结构体类型无法正确映射到Java类
  @Test void testFieldClassOnColumnOfTwoFieldStructType() { // 测试方法：测试双字段结构体类型的字段类映射
    RelDataType columnType = // 定义列类型，该列本身是一个包含两个字段的结构体类型
        TYPE_FACTORY.createStructType( // 创建结构体类型
            ImmutableList.of(TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER), // 第一个字段：INTEGER类型
                TYPE_FACTORY.createSqlType(SqlTypeName.VARCHAR)), // 第二个字段：VARCHAR类型
            ImmutableList.of("intField", "strField")); // 字段名称分别为"intField"和"strField"
    RelDataType rowType = // 定义行类型，该行包含一个双字段结构体类型的列
        TYPE_FACTORY.createStructType(ImmutableList.of(columnType), // 创建结构体类型，包含一个columnType类型的列
            ImmutableList.of("structField")); // 列名称为"structField"

    PhysType rowPhysType = // 创建物理类型对象，用于表示行的物理表示
        PhysTypeImpl.of(TYPE_FACTORY, rowType, JavaRowFormat.ARRAY); // 使用ARRAY格式创建PhysTypeImpl实例
    assertThat(rowPhysType.fieldClass(0), is(Object[].class)); // 断言：验证第0个字段的Java类是Object[].class，确保双字段结构体被正确映射
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-3364">[CALCITE-3364] // 关联JIRA问题CALCITE-3364
   * Can't group table function result due to a type cast error if table function // 问题描述：如果表函数返回单值行，由于类型转换错误无法对表函数结果进行分组
   * returns a row with a single value</a>. */ // 当表函数返回包含单个值的行时
  @Test void testOneColumnJavaRowFormatConversion() { // 测试方法：测试单列Java行格式转换
    RelDataType rowType = // 定义行类型，该行只包含一个INTEGER类型的列
        TYPE_FACTORY.createStructType( // 创建结构体类型
            ImmutableList.of(TYPE_FACTORY.createSqlType(SqlTypeName.INTEGER)), // 包含一个INTEGER类型的字段
            ImmutableList.of("intField")); // 字段名称为"intField"
    final PhysType rowPhysType = // 创建物理类型对象，用于表示行的物理表示
        PhysTypeImpl.of(TYPE_FACTORY, rowType, JavaRowFormat.ARRAY, false); // 使用ARRAY格式创建PhysTypeImpl实例，最后一个参数false表示不优化
    final Expression e = // 创建表达式对象，用于表示类型转换表达式
        rowPhysType.convertTo(Expressions.parameter(Enumerable.class, "input"), // 将输入的Enumerable参数从ARRAY格式转换为SCALAR格式
            JavaRowFormat.SCALAR); // 目标格式为SCALAR（标量）格式
    final String expected = "input.select(new org.apache.calcite.linq4j.function.Function1() {\n" // 预期的转换表达式字符串
        + "  public int apply(Object[] o) {\n" // apply方法接受Object[]数组参数
        + "    return org.apache.calcite.runtime.SqlFunctions.toInt(o[0]);\n" // 使用SqlFunctions.toInt将数组的第一个元素转换为int类型
        + "  }\n" // 方法结束
        + "  public Object apply(Object o) {\n" // 另一个重载的apply方法，接受Object参数
        + "    return apply(\n" // 调用上面的apply方法
        + "      (Object[]) o);\n" // 将Object强制转换为Object[]类型
        + "  }\n" // 方法结束
        + "}\n" // 匿名内部类结束
        + ")"; // select方法调用结束
    assertThat(expected, is(Expressions.toString(e))); // 断言：验证生成的表达式字符串与预期字符串匹配
  }
} // 类定义结束
