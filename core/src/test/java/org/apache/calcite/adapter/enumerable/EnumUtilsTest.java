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
// Apache Calcite是一个动态数据管理框架，主要用于SQL解析、优化和执行，EnumUtils是可枚举适配器中的工具类，提供表达式转换和方法调用功能
package org.apache.calcite.adapter.enumerable; // 可枚举适配器包，包含将关系代数转换为可执行代码的实现

import org.apache.calcite.linq4j.tree.ConstantExpression; // LINQ4J常量表达式类，表示常量值的表达式树节点
import org.apache.calcite.linq4j.tree.Expression; // LINQ4J表达式基类，所有表达式树的节点都继承此类
import org.apache.calcite.linq4j.tree.Expressions; // LINQ4J表达式工厂类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.MethodCallExpression; // LINQ4J方法调用表达式类，表示方法调用的表达式树节点
import org.apache.calcite.linq4j.tree.ParameterExpression; // LINQ4J参数表达式类，表示参数的表达式树节点
import org.apache.calcite.runtime.SpatialTypeFunctions; // 空间类型函数类，提供空间数据相关的SQL函数实现
import org.apache.calcite.runtime.SqlFunctions; // SQL函数类，提供标准SQL函数的Java实现
import org.apache.calcite.runtime.XmlFunctions; // XML函数类，提供XML处理相关的SQL函数实现
import org.apache.calcite.util.BuiltInMethod; // 内置方法枚举，定义Calcite支持的内置方法

import org.junit.jupiter.api.Test; // JUnit5测试注解，标记测试方法

import java.math.BigDecimal; // Java大数类，用于精确的十进制运算
import java.util.Arrays; // Java数组工具类，提供数组操作方法

import static org.hamcrest.CoreMatchers.is; // Hamcrest匹配器，用于断言相等性
import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest断言工具，用于执行匹配器断言

/**
 * Tests for {@link EnumUtils}.
 * // EnumUtilsTest类：EnumUtils工具类的单元测试类，用于验证EnumUtils中类型转换和方法调用功能的正确性
 * // EnumUtils是Calcite可枚举适配器的核心工具类，负责将SQL表达式转换为Java表达式树
 * // 主要测试场景包括：日期时间类型转换、基本类型与包装类型转换、字符串转换、方法调用表达式生成
 */
public final class EnumUtilsTest { // EnumUtilsTest类，final表示不可被继承，只包含测试方法

  // testDateTypeToInnerTypeConvert方法：测试日期时间类型到内部类型的转换功能
  // 验证EnumUtils.convert方法能否正确处理java.sql.Date、java.sql.Time、java.sql.Timestamp到Java基本类型的转换
  // 测试点包括：转换为基本类型(int/long)和包装类型(Integer/Long)时生成不同的函数调用
  @Test void testDateTypeToInnerTypeConvert() { // 测试方法，使用JUnit5注解
    // java.sql.Date x; // 注释说明：创建一个java.sql.Date类型的参数表达式，变量名为"x"，用于后续类型转换测试
    final ParameterExpression date = // 声明一个final参数表达式变量，表示日期参数
        Expressions.parameter(0, java.sql.Date.class, "x"); // 使用Expressions工厂创建参数表达式，索引为0，类型为java.sql.Date，名称为"x"
    final Expression dateToInt = // 声明表达式变量，存储日期转换为int基本类型的结果
        EnumUtils.convert(date, int.class); // 调用EnumUtils.convert方法，将日期参数转换为int基本类型，生成SqlFunctions.toInt(x)调用
    final Expression dateToInteger = // 声明表达式变量，存储日期转换为Integer包装类型的结果
        EnumUtils.convert(date, Integer.class); // 调用EnumUtils.convert方法，将日期参数转换为Integer包装类型，生成SqlFunctions.toIntOptional(x)调用
    assertThat(Expressions.toString(dateToInt), // 断言：将dateToInt表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.toInt(x)")); // 期望生成的表达式是调用SqlFunctions.toInt方法，传入参数x
    assertThat(Expressions.toString(dateToInteger), // 断言：将dateToInteger表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.toIntOptional(x)")); // 期望生成的表达式是调用SqlFunctions.toIntOptional方法，传入参数x，Optional表示可能返回null

    // java.sql.Time x; // 注释说明：创建一个java.sql.Time类型的参数表达式，变量名为"x"，用于时间类型转换测试
    final ParameterExpression time = // 声明一个final参数表达式变量，表示时间参数
        Expressions.parameter(0, java.sql.Time.class, "x"); // 使用Expressions工厂创建参数表达式，索引为0，类型为java.sql.Time，名称为"x"
    final Expression timeToInt = // 声明表达式变量，存储时间转换为int基本类型的结果
        EnumUtils.convert(time, int.class); // 调用EnumUtils.convert方法，将时间参数转换为int基本类型，生成SqlFunctions.toInt(x)调用
    final Expression timeToInteger = // 声明表达式变量，存储时间转换为Integer包装类型的结果
        EnumUtils.convert(time, Integer.class); // 调用EnumUtils.convert方法，将时间参数转换为Integer包装类型，生成SqlFunctions.toIntOptional(x)调用
    assertThat(Expressions.toString(timeToInt), // 断言：将timeToInt表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.toInt(x)")); // 期望生成的表达式是调用SqlFunctions.toInt方法，传入参数x
    assertThat(Expressions.toString(timeToInteger), // 断言：将timeToInteger表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.toIntOptional(x)")); // 期望生成的表达式是调用SqlFunctions.toIntOptional方法，传入参数x

    // java.sql.TimeStamp x; // 注释说明：创建一个java.sql.Timestamp类型的参数表达式，变量名为"x"，用于时间戳类型转换测试
    final ParameterExpression timestamp = // 声明一个final参数表达式变量，表示时间戳参数
        Expressions.parameter(0, java.sql.Timestamp.class, "x"); // 使用Expressions工厂创建参数表达式，索引为0，类型为java.sql.Timestamp，名称为"x"
    final Expression timeStampToLongPrimitive = // 声明表达式变量，存储时间戳转换为long基本类型的结果
        EnumUtils.convert(timestamp, long.class); // 调用EnumUtils.convert方法，将时间戳参数转换为long基本类型，生成SqlFunctions.toLong(x)调用
    final Expression timeStampToLong = // 声明表达式变量，存储时间戳转换为Long包装类型的结果
        EnumUtils.convert(timestamp, Long.class); // 调用EnumUtils.convert方法，将时间戳参数转换为Long包装类型，生成SqlFunctions.toLongOptional(x)调用
    assertThat(Expressions.toString(timeStampToLongPrimitive), // 断言：将timeStampToLongPrimitive表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.toLong(x)")); // 期望生成的表达式是调用SqlFunctions.toLong方法，传入参数x
    assertThat(Expressions.toString(timeStampToLong), // 断言：将timeStampToLong表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.toLongOptional(x)")); // 期望生成的表达式是调用SqlFunctions.toLongOptional方法，传入参数x
  }

  // testTypeConvertFromPrimitiveToBox方法：测试基本类型到包装类型的转换功能
  // 验证EnumUtils.convert方法能否正确处理int类型到所有包装类型(Byte、Short、Integer、Long、Float、Double、Character)的转换
  // 测试点包括：先转换为其他基本类型再转换为包装类型，以及直接从int转换为各种包装类型
  @Test void testTypeConvertFromPrimitiveToBox() { // 测试方法，使用JUnit5注解
    final Expression intVariable = // 声明一个final表达式变量，表示int类型的变量
        Expressions.parameter(0, int.class, "intV"); // 使用Expressions工厂创建参数表达式，索引为0，类型为int，名称为"intV"

    // (byte)(int) -> Byte: Byte.valueOf((byte) intV) // 注释说明：测试int先转换为byte基本类型，再转换为Byte包装类型
    final Expression bytePrimitiveConverted = // 声明表达式变量，存储int转换为byte基本类型的结果
        Expressions.convert_(intVariable, byte.class); // 使用Expressions.convert_方法执行类型转换，将int转换为byte基本类型
    final Expression converted0 = // 声明表达式变量，存储byte基本类型转换为Byte包装类型的结果
        EnumUtils.convert(bytePrimitiveConverted, Byte.class); // 调用EnumUtils.convert方法，将byte表达式转换为Byte包装类型，生成Byte.valueOf调用
    assertThat(Expressions.toString(converted0), // 断言：将converted0表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Byte.valueOf((byte) intV)")); // 期望生成的表达式是调用Byte.valueOf方法，传入强制类型转换后的intV

    // (char)(int) -> Character: Character.valueOf((char) intV) // 注释说明：测试int先转换为char基本类型，再转换为Character包装类型
    final Expression characterPrimitiveConverted = // 声明表达式变量，存储int转换为char基本类型的结果
        Expressions.convert_(intVariable, char.class); // 使用Expressions.convert_方法执行类型转换，将int转换为char基本类型
    final Expression converted1 = // 声明表达式变量，存储char基本类型转换为Character包装类型的结果
        EnumUtils.convert(characterPrimitiveConverted, Character.class); // 调用EnumUtils.convert方法，将char表达式转换为Character包装类型，生成Character.valueOf调用
    assertThat(Expressions.toString(converted1), // 断言：将converted1表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Character.valueOf((char) intV)")); // 期望生成的表达式是调用Character.valueOf方法，传入强制类型转换后的intV

    // (short)(int) -> Short: Short.valueOf((short) intV) // 注释说明：测试int先转换为short基本类型，再转换为Short包装类型
    final Expression shortPrimitiveConverted = // 声明表达式变量，存储int转换为short基本类型的结果
        Expressions.convert_(intVariable, short.class); // 使用Expressions.convert_方法执行类型转换，将int转换为short基本类型
    final Expression converted2 = // 声明表达式变量，存储short基本类型转换为Short包装类型的结果
        EnumUtils.convert(shortPrimitiveConverted, Short.class); // 调用EnumUtils.convert方法，将short表达式转换为Short包装类型，生成Short.valueOf调用
    assertThat(Expressions.toString(converted2), // 断言：将converted2表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Short.valueOf((short) intV)")); // 期望生成的表达式是调用Short.valueOf方法，传入强制类型转换后的intV

    // (long)(int) -> Long: Long.valueOf(intV) // 注释说明：测试int先转换为long基本类型，再转换为Long包装类型
    final Expression longPrimitiveConverted = // 声明表达式变量，存储int转换为long基本类型的结果
        Expressions.convert_(intVariable, long.class); // 使用Expressions.convert_方法执行类型转换，将int转换为long基本类型
    final Expression converted3 = // 声明表达式变量，存储long基本类型转换为Long包装类型的结果
        EnumUtils.convert(longPrimitiveConverted, Long.class); // 调用EnumUtils.convert方法，将long表达式转换为Long包装类型，生成Long.valueOf调用
    assertThat(Expressions.toString(converted3), // 断言：将converted3表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Long.valueOf(intV)")); // 期望生成的表达式是调用Long.valueOf方法，传入intV（无需强制类型转换，因为long可以接收int）

    // (float)(int) -> Float: Float.valueOf(intV) // 注释说明：测试int先转换为float基本类型，再转换为Float包装类型
    final Expression floatPrimitiveConverted = // 声明表达式变量，存储int转换为float基本类型的结果
        Expressions.convert_(intVariable, float.class); // 使用Expressions.convert_方法执行类型转换，将int转换为float基本类型
    final Expression converted4 = // 声明表达式变量，存储float基本类型转换为Float包装类型的结果
        EnumUtils.convert(floatPrimitiveConverted, Float.class); // 调用EnumUtils.convert方法，将float表达式转换为Float包装类型，生成Float.valueOf调用
    assertThat(Expressions.toString(converted4), // 断言：将converted4表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Float.valueOf(intV)")); // 期望生成的表达式是调用Float.valueOf方法，传入intV（无需强制类型转换，因为float可以接收int）

    // (double)(int) -> Double: Double.valueOf(intV) // 注释说明：测试int先转换为double基本类型，再转换为Double包装类型
    final Expression doublePrimitiveConverted = // 声明表达式变量，存储int转换为double基本类型的结果
        Expressions.convert_(intVariable, double.class); // 使用Expressions.convert_方法执行类型转换，将int转换为double基本类型
    final Expression converted5 = // 声明表达式变量，存储double基本类型转换为Double包装类型的结果
        EnumUtils.convert(doublePrimitiveConverted, Double.class); // 调用EnumUtils.convert方法，将double表达式转换为Double包装类型，生成Double.valueOf调用
    assertThat(Expressions.toString(converted5), // 断言：将converted5表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Double.valueOf(intV)")); // 期望生成的表达式是调用Double.valueOf方法，传入intV（无需强制类型转换，因为double可以接收int）

    final Expression byteConverted = // 声明表达式变量，存储int直接转换为Byte包装类型的结果
        EnumUtils.convert(intVariable, Byte.class); // 调用EnumUtils.convert方法，直接将int表达式转换为Byte包装类型，生成Byte.valueOf((byte) intV)调用
    assertThat(Expressions.toString(byteConverted), // 断言：将byteConverted表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Byte.valueOf((byte) intV)")); // 期望生成的表达式是调用Byte.valueOf方法，传入强制类型转换为byte的intV

    final Expression shortConverted = // 声明表达式变量，存储int直接转换为Short包装类型的结果
        EnumUtils.convert(intVariable, Short.class); // 调用EnumUtils.convert方法，直接将int表达式转换为Short包装类型，生成Short.valueOf((short) intV)调用
    assertThat(Expressions.toString(shortConverted), // 断言：将shortConverted表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Short.valueOf((short) intV)")); // 期望生成的表达式是调用Short.valueOf方法，传入强制类型转换为short的intV

    final Expression integerConverted = // 声明表达式变量，存储int直接转换为Integer包装类型的结果
        EnumUtils.convert(intVariable, Integer.class); // 调用EnumUtils.convert方法，直接将int表达式转换为Integer包装类型，生成Integer.valueOf(intV)调用
    assertThat(Expressions.toString(integerConverted), // 断言：将integerConverted表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Integer.valueOf(intV)")); // 期望生成的表达式是调用Integer.valueOf方法，传入intV

    final Expression longConverted = // 声明表达式变量，存储int直接转换为Long包装类型的结果
        EnumUtils.convert(intVariable, Long.class); // 调用EnumUtils.convert方法，直接将int表达式转换为Long包装类型，生成Long.valueOf((long) intV)调用
    assertThat(Expressions.toString(longConverted), // 断言：将longConverted表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Long.valueOf((long) intV)")); // 期望生成的表达式是调用Long.valueOf方法，传入强制类型转换为long的intV

    final Expression floatConverted = // 声明表达式变量，存储int直接转换为Float包装类型的结果
        EnumUtils.convert(intVariable, Float.class); // 调用EnumUtils.convert方法，直接将int表达式转换为Float包装类型，生成Float.valueOf((float) intV)调用
    assertThat(Expressions.toString(floatConverted), // 断言：将floatConverted表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Float.valueOf((float) intV)")); // 期望生成的表达式是调用Float.valueOf方法，传入强制类型转换为float的intV

    final Expression doubleConverted = // 声明表达式变量，存储int直接转换为Double包装类型的结果
        EnumUtils.convert(intVariable, Double.class); // 调用EnumUtils.convert方法，直接将int表达式转换为Double包装类型，生成Double.valueOf((double) intV)调用
    assertThat(Expressions.toString(doubleConverted), // 断言：将doubleConverted表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("Double.valueOf((double) intV)")); // 期望生成的表达式是调用Double.valueOf方法，传入强制类型转换为double的intV
  }

  // testTypeConvertToString方法：测试null值转换为String类型的功能
  // 验证EnumUtils.convert方法能否正确处理null值到String类型的转换
  // 测试点包括：无类型的null和Object类型的null转换为String时的不同处理方式
  @Test void testTypeConvertToString() { // 测试方法，使用JUnit5注解
    // Constant Expression: "null" // 注释说明：创建一个无类型的null常量表达式
    final ConstantExpression nullLiteral1 = Expressions.constant(null); // 使用Expressions.constant方法创建null常量表达式，未指定类型
    // Constant Expression: "(Object) null" // 注释说明：创建一个Object类型的null常量表达式
    final ConstantExpression nullLiteral2 = Expressions.constant(null, Object.class); // 使用Expressions.constant方法创建null常量表达式，指定类型为Object
    final Expression e1 = EnumUtils.convert(nullLiteral1, String.class); // 调用EnumUtils.convert方法，将无类型null转换为String类型
    final Expression e2 = EnumUtils.convert(nullLiteral2, String.class); // 调用EnumUtils.convert方法，将Object类型null转换为String类型
    assertThat(Expressions.toString(e1), is("(String) null")); // 断言：验证无类型null转换为String的结果是"(String) null"
    assertThat(Expressions.toString(e2), is("(String) (Object) null")); // 断言：验证Object类型null转换为String的结果是"(String) (Object) null"，保留了Object类型信息
  }

  // testMethodCallExpression方法：测试方法调用表达式的生成功能
  // 验证EnumUtils.call方法能否正确生成各种方法调用表达式，包括参数类型匹配和自动类型转换
  // 测试点包括：Object.class参数类型、Object.class参数值、BigDecimal与long类型的自动转换、int到BigDecimal的自动转换
  @Test void testMethodCallExpression() { // 测试方法，使用JUnit5注解
    // test for Object.class method parameter type // 注释说明：测试方法参数类型为Object.class的情况
    final ConstantExpression arg0 = Expressions.constant(1, int.class); // 创建值为1的int类型常量表达式，作为第一个参数
    final ConstantExpression arg1 = Expressions.constant("x", String.class); // 创建值为"x"的String类型常量表达式，作为第二个参数
    final MethodCallExpression arrayMethodCall = // 声明方法调用表达式变量，存储调用SqlFunctions.array方法的结果
        EnumUtils.call(null, SqlFunctions.class, // 调用EnumUtils.call方法，null表示目标对象为null（静态方法），SqlFunctions.class是方法所在类
            BuiltInMethod.ARRAY.getMethodName(), Arrays.asList(arg0, arg1)); // 使用BuiltInMethod.ARRAY获取方法名，参数列表包含arg0和arg1
    assertThat(Expressions.toString(arrayMethodCall), // 断言：将arrayMethodCall表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.array(1, \"x\")")); // 期望生成的表达式是调用SqlFunctions.array方法，传入参数1和"x"

    // test for Object.class argument type // 注释说明：测试方法参数值为Object.class类型（null值）的情况
    final ConstantExpression nullLiteral = Expressions.constant(null); // 创建null常量表达式，未指定类型
    final MethodCallExpression xmlExtractMethodCall = // 声明方法调用表达式变量，存储调用XmlFunctions.extractValue方法的结果
        EnumUtils.call(null, XmlFunctions.class, // 调用EnumUtils.call方法，null表示目标对象为null（静态方法），XmlFunctions.class是方法所在类
            BuiltInMethod.EXTRACT_VALUE.getMethodName(), // 使用BuiltInMethod.EXTRACT_VALUE获取方法名
            Arrays.asList(arg1, nullLiteral)); // 参数列表包含arg1（字符串"x"）和nullLiteral（null值）
    assertThat(Expressions.toString(xmlExtractMethodCall), // 断言：将xmlExtractMethodCall表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.XmlFunctions.extractValue(\"x\", (String) null)")); // 期望生成的表达式是调用XmlFunctions.extractValue方法，第二个参数被转换为(String) null

    // test "mod(decimal, long)" match to "mod(decimal, decimal)" // 注释说明：测试mod方法调用时，long参数自动转换为BigDecimal的情况
    final ConstantExpression arg2 = Expressions.constant(12.5, BigDecimal.class); // 创建值为12.5的BigDecimal类型常量表达式，作为第一个参数
    final ConstantExpression arg3 = Expressions.constant(3, long.class); // 创建值为3的long类型常量表达式，作为第二个参数
    final MethodCallExpression modMethodCall = // 声明方法调用表达式变量，存储调用SqlFunctions.mod方法的结果
        EnumUtils.call(null, SqlFunctions.class, "mod", // 调用EnumUtils.call方法，null表示目标对象为null（静态方法），SqlFunctions.class是方法所在类，方法名为"mod"
            Arrays.asList(arg2, arg3)); // 参数列表包含arg2（BigDecimal 12.5）和arg3（long 3），EnumUtils会自动将long转换为BigDecimal以匹配方法签名
    assertThat(Expressions.toString(modMethodCall), // 断言：将modMethodCall表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SqlFunctions.mod(" // 期望生成的表达式是调用SqlFunctions.mod方法，两个参数都被转换为BigDecimal类型
            + "java.math.BigDecimal.valueOf(125L, 1), " // 第一个参数12.5转换为BigDecimal.valueOf(125L, 1)，表示125/10=12.5
            + "new java.math.BigDecimal(\n  3L))")); // 第二个参数3转换为new BigDecimal(3L)，注意这里使用了换行符

    // test "ST_MakePoint(int, int)" match to "ST_MakePoint(decimal, decimal)" // 注释说明：测试ST_MakePoint方法调用时，int参数自动转换为BigDecimal的情况
    final ConstantExpression arg4 = Expressions.constant(1, int.class); // 创建值为1的int类型常量表达式，作为第一个参数（x坐标）
    final ConstantExpression arg5 = Expressions.constant(2, int.class); // 创建值为2的int类型常量表达式，作为第二个参数（y坐标）
    final MethodCallExpression geoMethodCall = // 声明方法调用表达式变量，存储调用SpatialTypeFunctions.ST_MakePoint方法的结果
        EnumUtils.call(null, SpatialTypeFunctions.class, "ST_MakePoint", // 调用EnumUtils.call方法，null表示目标对象为null（静态方法），SpatialTypeFunctions.class是方法所在类，方法名为"ST_MakePoint"
            Arrays.asList(arg4, arg5)); // 参数列表包含arg4（int 1）和arg5（int 2），EnumUtils会自动将int转换为BigDecimal以匹配方法签名
    assertThat(Expressions.toString(geoMethodCall), // 断言：将geoMethodCall表达式转换为字符串，验证生成的表达式字符串是否符合预期
        is("org.apache.calcite.runtime.SpatialTypeFunctions.ST_MakePoint(" // 期望生成的表达式是调用SpatialTypeFunctions.ST_MakePoint方法，两个参数都被转换为BigDecimal类型
            + "new java.math.BigDecimal(\n  1), " // 第一个参数1转换为new BigDecimal(1)，表示x坐标
            + "new java.math.BigDecimal(\n  2))")); // 第二个参数2转换为new BigDecimal(2)，表示y坐标，注意这里使用了换行符
  }
}
