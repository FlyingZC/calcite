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
package org.apache.calcite.adapter.druid; // 声明包名，该类属于org.apache.calcite.adapter.druid包，这是Calcite Druid适配器模块

import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置类，用于配置Calcite连接参数
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类，用于创建Java类型系统
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义类型系统的行为
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器，用于构建行表达式(RexNode)
import org.apache.calcite.rex.RexNode; // 导入行表达式接口，代表Calcite中的表达式节点
import org.apache.calcite.sql.fun.SqlInternalOperators; // 导入SQL内部操作符类，包含Calcite内部使用的特殊操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义SQL标准类型

import com.fasterxml.jackson.core.JsonFactory; // 导入Jackson JSON工厂类，用于创建JSON解析器/生成器
import com.fasterxml.jackson.core.JsonGenerator; // 导入Jackson JSON生成器接口，用于生成JSON输出
import com.google.common.collect.ImmutableList; // 导入Google Guava不可变列表类，提供线程安全的不可变列表
import com.google.common.collect.ImmutableSet; // 导入Google Guava不可变集合类，提供线程安全的不可变集合

import org.junit.jupiter.api.BeforeEach; // 导入JUnit 5的BeforeEach注解，标记在每个测试方法前执行的方法
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法
import org.mockito.Mockito; // 导入Mockito模拟框架，用于创建模拟对象

import java.io.IOException; // 导入IO异常类，处理输入输出异常
import java.io.StringWriter; // 导入字符串写入器类，用于将数据写入字符串缓冲区
import java.math.BigDecimal; // 导入BigDecimal类，用于高精度十进制运算
import java.util.List; // 导入List接口，表示有序集合

import static org.hamcrest.CoreMatchers.notNullValue; // 导入Hamcrest断言工具，检查值不为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具，执行断言检查
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest匹配器，检查对象的toString()方法返回值

/**
 * Tests generating Druid filters. // 测试Druid过滤器的生成
 * // 该测试类专门用于验证Calcite如何将SQL中的过滤条件转换为Druid查询所需的JSON过滤器格式
 * // Druid是Apache的一个分布式列式存储系统，支持高性能的实时和批处理查询
 * // Calcite作为SQL查询引擎，需要将SQL的WHERE条件转换为Druid原生的过滤器格式
 * // 主要测试场景包括：IN过滤器、NOT IN过滤器、BETWEEN过滤器等常见SQL过滤操作
 * // 每个测试方法都会验证生成的JSON格式是否符合Druid的规范要求
 */
class DruidQueryFilterTest { // 定义测试类，用于测试Druid查询过滤器的生成逻辑

  private DruidQuery druidQuery; // 声明DruidQuery类型的成员变量，用于模拟Druid查询对象，在测试中作为参数传递给过滤器转换方法

  @BeforeEach void testSetup() { // 使用JUnit 5的BeforeEach注解，标记该方法在每个测试方法执行前运行，用于初始化测试环境
    druidQuery = Mockito.mock(DruidQuery.class); // 使用Mockito创建DruidQuery的模拟对象，避免依赖真实的DruidQuery实现
    final CalciteConnectionConfig connectionConfigMock = Mockito // 创建CalciteConnectionConfig的模拟对象，用于配置连接参数
        .mock(CalciteConnectionConfig.class); // 调用Mockito.mock()方法创建模拟对象
    Mockito.when(connectionConfigMock.timeZone()).thenReturn("UTC"); // 设置模拟对象的timeZone()方法返回"UTC"时区，确保测试使用统一的时区设置
    Mockito.when(druidQuery.getConnectionConfig()).thenReturn(connectionConfigMock); // 设置druidQuery模拟对象的getConnectionConfig()方法返回上面创建的连接配置模拟对象
    Mockito.when(druidQuery.getDruidTable()) // 设置druidQuery模拟对象的getDruidTable()方法返回一个DruidTable对象
        .thenReturn( // 返回构造的DruidTable对象
            new DruidTable(Mockito.mock(DruidSchema.class), "dataSource", null, // 创建DruidTable实例：第一个参数是模拟的DruidSchema，第二个参数是数据源名称"dataSource"
                ImmutableSet.of(), "timestamp", null, null, // 后续参数：度量值集合为空、时间戳列为"timestamp"、其他参数为null
                null)); // 最后一个参数为null，表示没有额外的配置
  }

  @Test void testInFilter() throws IOException { // 测试方法：验证IN过滤器的生成逻辑，测试SQL中的IN操作符如何转换为Druid的JSON过滤器
    final Fixture f = new Fixture(); // 创建Fixture测试辅助对象，该对象封装了测试所需的类型工厂、Rex构建器等工具
    final List<? extends RexNode> listRexNodes = // 创建RexNode列表，包含IN操作符的所有操作数
        ImmutableList.of(f.rexBuilder.makeInputRef(f.varcharRowType, 0), // 第一个元素：创建对varcharRowType类型第0个字段的引用，即维度名称
            f.rexBuilder.makeExactLiteral(BigDecimal.valueOf(1)), // 第二个元素：创建精确字面量，值为1（BigDecimal类型）
            f.rexBuilder.makeExactLiteral(BigDecimal.valueOf(5)), // 第三个元素：创建精确字面量，值为5（BigDecimal类型）
            f.rexBuilder.makeLiteral("value1")); // 第四个元素：创建字符串字面量，值为"value1"

    RexNode inRexNode = // 创建RexNode表示IN操作符的调用
        f.rexBuilder.makeCall(SqlInternalOperators.DRUID_IN, listRexNodes); // 使用RexBuilder创建DRUID_IN操作符的调用节点，传入操作数列表
    DruidJsonFilter returnValue = DruidJsonFilter // 调用DruidJsonFilter的静态方法toDruidFilters，将RexNode转换为Druid的JSON过滤器
        .toDruidFilters(inRexNode, f.varcharRowType, druidQuery, f.rexBuilder); // 参数：RexNode表达式、行类型、DruidQuery对象、RexBuilder对象
    assertThat("Filter is null", returnValue, notNullValue()); // 断言：验证返回的过滤器对象不为null，确保转换成功
    JsonFactory jsonFactory = new JsonFactory(); // 创建Jackson JSON工厂对象，用于生成JSON
    final StringWriter sw = new StringWriter(); // 创建字符串写入器，用于接收生成的JSON字符串
    JsonGenerator jsonGenerator = jsonFactory.createGenerator(sw); // 使用JSON工厂创建JSON生成器，输出到字符串写入器
    returnValue.write(jsonGenerator); // 调用过滤器的write方法，将Druid过滤器对象写入JSON生成器
    jsonGenerator.close(); // 关闭JSON生成器，确保所有数据都写入字符串写入器

    assertThat(sw, // 断言：验证生成的JSON字符串是否符合预期格式
        hasToString("{\"type\":\"in\",\"dimension\":\"dimensionName\"," // 预期JSON包含type为"in"，dimension为"dimensionName"
            + "\"values\":[\"1\",\"5\",\"value1\"]}")); // values数组包含"1"、"5"、"value1"三个值
  }

  @Test void testNotInFilter() throws IOException { // 测试方法：验证NOT IN过滤器的生成逻辑，测试SQL中的NOT IN操作符如何转换为Druid的JSON过滤器
    final Fixture f = new Fixture(); // 创建Fixture测试辅助对象，该对象封装了测试所需的类型工厂、Rex构建器等工具
    final List<? extends RexNode> listRexNodes = // 创建RexNode列表，包含NOT IN操作符的所有操作数
        ImmutableList.of(f.rexBuilder.makeInputRef(f.varcharRowType, 0), // 第一个元素：创建对varcharRowType类型第0个字段的引用，即维度名称
            f.rexBuilder.makeExactLiteral(BigDecimal.valueOf(1)), // 第二个元素：创建精确字面量，值为1（BigDecimal类型）
            f.rexBuilder.makeExactLiteral(BigDecimal.valueOf(5)), // 第三个元素：创建精确字面量，值为5（BigDecimal类型）
            f.rexBuilder.makeLiteral("value1")); // 第四个元素：创建字符串字面量，值为"value1"

    RexNode notInRexNode = // 创建RexNode表示NOT IN操作符的调用
        f.rexBuilder.makeCall(SqlInternalOperators.DRUID_NOT_IN, listRexNodes); // 使用RexBuilder创建DRUID_NOT_IN操作符的调用节点，传入操作数列表
    DruidJsonFilter returnValue = DruidJsonFilter // 调用DruidJsonFilter的静态方法toDruidFilters，将RexNode转换为Druid的JSON过滤器
        .toDruidFilters(notInRexNode, f.varcharRowType, druidQuery, f.rexBuilder); // 参数：RexNode表达式、行类型、DruidQuery对象、RexBuilder对象
    assertThat("Filter is null", returnValue, notNullValue()); // 断言：验证返回的过滤器对象不为null，确保转换成功
    JsonFactory jsonFactory = new JsonFactory(); // 创建Jackson JSON工厂对象，用于生成JSON
    final StringWriter sw = new StringWriter(); // 创建字符串写入器，用于接收生成的JSON字符串
    JsonGenerator jsonGenerator = jsonFactory.createGenerator(sw); // 使用JSON工厂创建JSON生成器，输出到字符串写入器
    returnValue.write(jsonGenerator); // 调用过滤器的write方法，将Druid过滤器对象写入JSON生成器
    jsonGenerator.close(); // 关闭JSON生成器，确保所有数据都写入字符串写入器

    assertThat(sw, // 断言：验证生成的JSON字符串是否符合预期格式
        hasToString("{\"type\":\"not\",\"field\":{\"type\":\"in\",\"dimension\":" // 预期JSON包含type为"not"，field字段包含一个IN过滤器
            + "\"dimensionName\",\"values\":[\"1\",\"5\",\"value1\"]}}")); // IN过滤器的dimension为"dimensionName"，values数组包含"1"、"5"、"value1"
  }

  @Test void testBetweenFilterStringCase() throws IOException { // 测试方法：验证BETWEEN过滤器的字符串类型生成逻辑，测试SQL中的BETWEEN操作符如何转换为Druid的JSON过滤器
    final Fixture f = new Fixture(); // 创建Fixture测试辅助对象，该对象封装了测试所需的类型工厂、Rex构建器等工具
    final List<RexNode> listRexNodes = // 创建RexNode列表，包含BETWEEN操作符的所有操作数
        ImmutableList.of(f.rexBuilder.makeLiteral(false), // 第一个元素：创建布尔字面量，值为false（表示是否包含边界）
            f.rexBuilder.makeInputRef(f.varcharRowType, 0), // 第二个元素：创建对varcharRowType类型第0个字段的引用，即维度名称
            f.rexBuilder.makeLiteral("lower-bound"), // 第三个元素：创建字符串字面量，值为"lower-bound"（下界值）
            f.rexBuilder.makeLiteral("upper-bound")); // 第四个元素：创建字符串字面量，值为"upper-bound"（上界值）
    RelDataType relDataType = f.typeFactory.createSqlType(SqlTypeName.BOOLEAN); // 创建BOOLEAN类型的RelDataType对象，表示BETWEEN操作符返回布尔类型
    RexNode betweenRexNode = // 创建RexNode表示BETWEEN操作符的调用
        f.rexBuilder.makeCall(relDataType, // 指定返回类型为BOOLEAN
            SqlInternalOperators.DRUID_BETWEEN, listRexNodes); // 使用RexBuilder创建DRUID_BETWEEN操作符的调用节点，传入操作数列表

    DruidJsonFilter returnValue = DruidJsonFilter // 调用DruidJsonFilter的静态方法toDruidFilters，将RexNode转换为Druid的JSON过滤器
        .toDruidFilters(betweenRexNode, f.varcharRowType, druidQuery, f.rexBuilder); // 参数：RexNode表达式、行类型、DruidQuery对象、RexBuilder对象
    assertThat("Filter is null", returnValue, notNullValue()); // 断言：验证返回的过滤器对象不为null，确保转换成功
    JsonFactory jsonFactory = new JsonFactory(); // 创建Jackson JSON工厂对象，用于生成JSON
    final StringWriter sw = new StringWriter(); // 创建字符串写入器，用于接收生成的JSON字符串
    JsonGenerator jsonGenerator = jsonFactory.createGenerator(sw); // 使用JSON工厂创建JSON生成器，输出到字符串写入器
    returnValue.write(jsonGenerator); // 调用过滤器的write方法，将Druid过滤器对象写入JSON生成器
    jsonGenerator.close(); // 关闭JSON生成器，确保所有数据都写入字符串写入器
    assertThat(sw, // 断言：验证生成的JSON字符串是否符合预期格式
        hasToString("{\"type\":\"bound\",\"dimension\":\"dimensionName\"," // 预期JSON包含type为"bound"（边界过滤器），dimension为"dimensionName"
            + "\"lower\":\"lower-bound\",\"lowerStrict\":false," // lower为"lower-bound"，lowerStrict为false（包含下界）
            + "\"upper\":\"upper-bound\",\"upperStrict\":false," // upper为"upper-bound"，upperStrict为false（包含上界）
            + "\"ordering\":\"lexicographic\"}")); // ordering为"lexicographic"（字典序排序，用于字符串比较）
  }

  /** Everything a test needs for a healthy, active life. */ // Javadoc注释：Fixture类提供了测试所需的所有工具和资源，让测试能够正常运行
  static class Fixture { // 定义静态内部类Fixture，作为测试辅助类，封装测试所需的共享资源和工具
    final JavaTypeFactoryImpl typeFactory = // 创建Java类型工厂实现对象，用于创建和管理Calcite中的数据类型
        new JavaTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认的关系数据类型系统初始化类型工厂
    final RexBuilder rexBuilder = new RexBuilder(typeFactory); // 创建Rex表达式构建器对象，用于构建各种Rex表达式节点，传入类型工厂作为参数
    final DruidTable druidTable = // 创建DruidTable对象，表示Druid数据表
        new DruidTable(Mockito.mock(DruidSchema.class), "dataSource", null, // 构造参数：模拟的DruidSchema、数据源名称"dataSource"、度量值列表为null
            ImmutableSet.of(), "timestamp", null, null, // 构造参数：度量值集合为空、时间戳列名为"timestamp"、其他参数为null
                null); // 最后一个参数为null，表示没有额外的配置
    final RelDataType varcharType = // 创建VARCHAR类型的RelDataType对象，表示字符串类型
        typeFactory.createSqlType(SqlTypeName.VARCHAR); // 使用类型工厂创建VARCHAR类型的对象
    final RelDataType varcharRowType = typeFactory.builder() // 创建行类型构建器，用于构造包含多个字段的行类型
        .add("dimensionName", varcharType) // 添加字段：字段名为"dimensionName"，类型为varcharType（VARCHAR类型）
        .build(); // 构建行类型对象，完成字段的添加
  }
}
