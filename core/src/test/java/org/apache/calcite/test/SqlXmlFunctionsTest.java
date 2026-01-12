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
// Apache Calcite是一个动态数据管理框架，主要用于SQL查询解析、优化和执行
// 本测试类专门用于测试XML处理相关的函数功能
package org.apache.calcite.test; // 定义测试类所在的包路径

import org.apache.calcite.runtime.CalciteException; // 导入Calcite异常类，用于处理Calcite框架中的异常情况
import org.apache.calcite.runtime.SqlFunctions; // 导入SQL函数工具类，包含各种SQL内置函数的实现
import org.apache.calcite.runtime.XmlFunctions; // 导入XML函数工具类，专门处理XML相关的操作
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举类，用于标识和引用Calcite的内置方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或参数
import org.hamcrest.Matcher; // 导入Matcher接口，用于断言匹配器，验证测试结果是否符合预期
import org.junit.jupiter.api.BeforeAll; // 导入BeforeAll注解，标记在所有测试方法执行前只运行一次的方法
import org.junit.jupiter.api.Test; // 导入Test注解，标记测试方法

import java.nio.file.Files; // 导入Files工具类，用于文件操作
import java.nio.file.Path; // 导入Path接口，表示文件系统中的路径
import java.util.function.Supplier; // 导入Supplier函数式接口，用于提供对象的供应商

import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，用于验证值相等
import static org.hamcrest.CoreMatchers.nullValue; // 导入nullValue匹配器，用于验证值为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言方法，用于验证测试结果
import static org.junit.jupiter.api.Assertions.fail; // 导入fail方法，用于标记测试失败

/**
 * Unit test for the methods in {@link SqlFunctions} that implement Xml processing functions.
 * // 这是一个单元测试类，用于测试SqlFunctions类中实现XML处理功能的方法
 * // 主要测试的XML函数包括：EXTRACTVALUE、EXISTSNODE、EXTRACT、XMLTRANSFORM等
 * // 这些函数是Calcite框架中处理XML数据的核心功能，用于在SQL查询中操作和转换XML数据
 * // 测试重点包括：正常功能测试、异常情况测试、安全漏洞测试（如XXE攻击防护）
 */
class SqlXmlFunctionsTest { // 定义测试类名称，测试XML函数功能

  // 定义一个简单的XML文档字符串，用于测试XML解析和提取功能
  private static final String XML = "<document>string</document>"; // 测试用XML文档，包含document根元素和string文本内容
  // 定义一个空的XSLT样式表字符串，用于测试XML转换功能
  private static final String XSLT = // XSLT（可扩展样式表语言转换）用于定义XML文档的转换规则
      "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"></xsl:stylesheet>"; // 空的XSLT样式表，不执行任何转换
  // 定义XPath表达式，用于定位XML文档中的document节点
  private static final String DOCUMENT_PATH = "/document"; // XPath路径表达式，表示从根节点选择document元素
  // 定义包含外部实体引用的XML字符串，用于测试XXE（XML外部实体）攻击防护
  private static @Nullable String xmlExternalEntity = null; // 可能为null的字符串，存储包含外部实体的恶意XML，初始化为null
  // 定义包含外部实体引用的XSLT字符串，用于测试XXE攻击防护
  private static @Nullable String xsltExternalEntity = null; // 可能为null的字符串，存储包含外部实体的恶意XSLT，初始化为null

  // 使用BeforeAll注解，表示此方法在所有测试方法执行前只运行一次，用于初始化测试环境
  @BeforeAll public static void setup() throws Exception { // 静态设置方法，抛出异常以处理可能的IO错误
    // 创建一个临时文件，用于模拟外部实体引用的目标文件
    final Path testFile = Files.createTempFile("foo", "temp"); // 在系统临时目录创建名为"foo"前缀、"temp"后缀的临时文件
    // 设置JVM退出时删除该临时文件，避免临时文件残留
    testFile.toFile().deleteOnExit(); // 注册删除钩子，确保程序结束时自动删除临时文件
    // 构建临时文件的文件URI路径
    final String filePath = "file:///" + testFile.toAbsolutePath(); // 获取临时文件的绝对路径并转换为file:// URI格式
    // 构建包含外部实体引用的XML文档，用于测试XXE攻击防护
    xmlExternalEntity = "<!DOCTYPE document [ <!ENTITY entity SYSTEM \"" + filePath // 定义DOCTYPE声明，包含外部实体entity，指向临时文件
        + "\"> ]><document>&entity;</document>"; // 完整的XML文档，引用外部实体entity，这是典型的XXE攻击向量
    // 构建包含外部实体引用的XSLT样式表，用于测试XXE攻击防护
    xsltExternalEntity = "<!DOCTYPE document [ <!ENTITY entity SYSTEM \"" + filePath // 定义DOCTYPE声明，包含外部实体entity
        + "\"> ]><xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">&entity;</xsl:stylesheet>"; // XSLT中引用外部实体，也是XXE攻击向量
  }

  // 测试EXTRACTVALUE函数的基本功能和异常处理
  @Test void testExtractValue() { // 测试方法，验证EXTRACTVALUE函数的正确性和异常处理
    // 断言EXTRACTVALUE能正确提取XML中指定XPath路径的文本内容
    assertExtractValue("<a>ccc<b>ddd</b></a>", "/a", is("ccc")); // 从XML "<a>ccc<b>ddd</b></a>" 中提取路径"/a"的文本，期望结果为"ccc"

    // 准备测试数据：一个包含嵌套元素的XML文档
    String input = "<a>ccc<b>ddd</b></a>"; // 输入XML，包含a元素和嵌套的b元素
    // 构造预期的错误消息，用于验证异常处理
    String message = "Invalid input for EXTRACTVALUE: xml: '" + input + "', xpath expression: '#'"; // 错误消息，包含无效的输入和XPath表达式
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，包含错误消息和null原因
    // 断言EXTRACTVALUE在遇到无效XPath表达式时抛出预期的异常
    assertExtractValueFailed(input, "#", Matchers.expectThrowable(expected)); // 验证当XPath为"#"时抛出异常，异常消息与预期匹配
  }

  // 测试EXTRACTVALUE函数对包含外部实体的XML文档的防护能力
  @Test void testExtractValueExternalEntity() { // 测试方法，验证EXTRACTVALUE能拒绝包含外部实体的XML
    // 构造预期的错误消息，提示XML包含外部实体
    String message = "Invalid input for EXTRACTVALUE: xml: '" // 错误消息开始部分，标识EXTRACTVALUE函数的无效输入
        + xmlExternalEntity + "', xpath expression: '" + DOCUMENT_PATH + "'"; // 完整的错误消息，包含恶意XML和XPath路径
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，用于验证安全防护机制
    // 断言EXTRACTVALUE拒绝处理包含外部实体的XML，抛出安全异常
    assertExtractValueFailed(xmlExternalEntity, DOCUMENT_PATH, // 验证处理包含外部实体的XML时抛出异常
        Matchers.expectThrowable(expected)); // 验证抛出的异常与预期异常匹配
  }

  // 测试EXISTSNODE函数对包含外部实体的XML文档的防护能力
  @Test void testExistsNodeExternalEntity() { // 测试方法，验证EXISTSNODE能拒绝包含外部实体的XML
    // 构造预期的错误消息，提示XML包含外部实体
    String message = "Invalid input for EXISTSNODE xpath: '" // 错误消息开始部分，标识EXISTSNODE函数的无效输入
        + DOCUMENT_PATH + "', namespace: '" + null + "'"; // 完整的错误消息，包含XPath路径和null命名空间
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，用于验证安全防护机制
    // 断言EXISTSNODE拒绝处理包含外部实体的XML，抛出安全异常
    assertExistsNodeFailed(xmlExternalEntity, DOCUMENT_PATH, null, // 验证处理包含外部实体的XML时抛出异常
        Matchers.expectThrowable(expected)); // 验证抛出的异常与预期异常匹配
  }

  // 测试XMLTRANSFORM函数对包含外部实体的XML文档的防护能力
  @Test void testXmlTransformExternalEntity() { // 测试方法，验证XMLTRANSFORM能拒绝包含外部实体的XML
    // 构造预期的错误消息，提示XML包含外部实体
    String message = "Invalid input for XMLTRANSFORM xml: '" + xmlExternalEntity + "'"; // 错误消息，标识XMLTRANSFORM函数的无效输入
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，用于验证安全防护机制
    // 断言XMLTRANSFORM拒绝处理包含外部实体的XML，抛出安全异常
    assertXmlTransformFailed(xmlExternalEntity, XSLT, Matchers.expectThrowable(expected)); // 验证处理包含外部实体的XML时抛出异常
  }

  // 测试XMLTRANSFORM函数对包含外部实体的XSLT样式表的防护能力
  @Test void testXmlTransformExternalEntityXslt() { // 测试方法，验证XMLTRANSFORM能拒绝包含外部实体的XSLT
    // 构造预期的错误消息，提示XSLT包含外部实体
    String message = "Illegal xslt specified : '" + xsltExternalEntity + "'"; // 错误消息，标识XSLT样式表包含非法内容
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，用于验证安全防护机制
    // 断言XMLTRANSFORM拒绝使用包含外部实体的XSLT，抛出安全异常
    assertXmlTransformFailed(XML, xsltExternalEntity, Matchers.expectThrowable(expected)); // 验证使用包含外部实体的XSLT时抛出异常
  }

  // 测试XMLTRANSFORM函数的基本功能和异常处理
  @Test void testXmlTransform() { // 测试方法，验证XMLTRANSFORM函数的正确性和异常处理
    // 断言XMLTRANSFORM处理null或空输入时返回null
    assertXmlTransform(null, "", nullValue()); // XML为null时，期望返回null
    assertXmlTransform("", null, nullValue()); // XSLT为null时，期望返回null

    // 准备测试数据：一个无效的XSLT样式表（不完整的XML）
    String xslt = "<"; // 无效的XSLT，只有一个开始标记，XML格式不完整
    // 构造预期的错误消息，提示XSLT格式非法
    String message = "Illegal xslt specified : '" + xslt + "'"; // 错误消息，标识XSLT样式表格式非法
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，用于验证异常处理
    // 断言XMLTRANSFORM在遇到非法XSLT时抛出预期的异常
    assertXmlTransformFailed("", xslt, Matchers.expectThrowable(expected)); // 验证处理非法XSLT时抛出异常
  }

  // 测试EXTRACT函数的基本功能和异常处理
  @Test void testExtractXml() { // 测试方法，验证EXTRACT函数的正确性和异常处理
    // 断言EXTRACT处理null或空输入时返回null
    assertExtractXml(null, "", null, nullValue()); // XML为null时，期望返回null
    assertExtractXml("", null, null, nullValue()); // XPath为null时，期望返回null

    // 准备测试数据：一个无效的XPath表达式
    String xpath = "<"; // 无效的XPath，以"<"开头，不符合XPath语法
    // 准备测试数据：一个命名空间
    String namespace = "a"; // 命名空间标识符
    // 构造预期的错误消息，提示XPath表达式非法
    String message = // 错误消息，标识EXTRACT函数的无效输入
        "Invalid input for EXTRACT xpath: '" + xpath + "', namespace: '" + namespace + "'"; // 完整的错误消息，包含非法XPath和命名空间
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，用于验证异常处理
    // 断言EXTRACT在遇到非法XPath时抛出预期的异常
    assertExtractXmlFailed("", xpath, namespace, Matchers.expectThrowable(expected)); // 验证处理非法XPath时抛出异常
  }


  // 测试EXISTSNODE函数的基本功能和异常处理
  @Test void testExistsNode() { // 测试方法，验证EXISTSNODE函数的正确性和异常处理
    // 断言EXISTSNODE处理null或空输入时返回null
    assertExistsNode(null, "", null, nullValue()); // XML为null时，期望返回null
    assertExistsNode("", null, null, nullValue()); // XPath为null时，期望返回null

    // 准备测试数据：一个无效的XPath表达式
    String xpath = "<"; // 无效的XPath，以"<"开头，不符合XPath语法
    // 准备测试数据：一个命名空间
    String namespace = "a"; // 命名空间标识符
    // 构造预期的错误消息，提示XPath表达式非法
    String message = // 错误消息，标识EXISTSNODE函数的无效输入
        "Invalid input for EXISTSNODE xpath: '" + xpath + "', namespace: '" + namespace + "'"; // 完整的错误消息，包含非法XPath和命名空间
    // 创建预期的CalciteException异常对象
    CalciteException expected = new CalciteException(message, null); // 构造Calcite异常，用于验证异常处理
    // 断言EXISTSNODE在遇到非法XPath时抛出预期的异常
    assertExistsNodeFailed("", xpath, namespace, Matchers.expectThrowable(expected)); // 验证处理非法XPath时抛出异常
  }

  // 辅助方法：断言EXISTSNODE函数的返回值符合预期
  private void assertExistsNode(String xml, String xpath, String namespace, // 方法参数：XML文档、XPath表达式、命名空间、期望的匹配器
      Matcher<? super Integer> matcher) { // 匹配器类型为Integer或其父类，用于验证EXISTSNODE的返回值
    // 构造方法描述字符串，用于断言消息中显示
    String methodDesc = BuiltInMethod.EXISTS_NODE.getMethodName() // 获取EXISTSNODE方法名称
        + "(" + String.join(", ", xml, xpath, namespace) + ")"; // 构造完整的调用描述，包含所有参数
    // 使用assertThat断言EXISTSNODE的实际返回值与期望值匹配
    assertThat(methodDesc, XmlFunctions.existsNode(xml, xpath, namespace), matcher); // 调用XmlFunctions.existsNode并验证结果
  }

  // 辅助方法：断言EXISTSNODE函数抛出预期的异常
  private void assertExistsNodeFailed(String xml, String xpath, String namespace, // 方法参数：XML文档、XPath表达式、命名空间、期望的异常匹配器
      Matcher<? super Throwable> matcher) { // 匹配器类型为Throwable或其父类，用于验证抛出的异常
    // 构造方法描述字符串，用于断言消息中显示
    String methodDesc = BuiltInMethod.EXISTS_NODE.getMethodName() // 获取EXISTSNODE方法名称
        + "(" + String.join(", ", xml, xpath, namespace) + ")"; // 构造完整的调用描述，包含所有参数
    // 调用assertFailed方法，验证EXISTSNODE抛出预期的异常
    assertFailed(methodDesc, () -> XmlFunctions.existsNode(xml, xpath, namespace), matcher); // 使用lambda表达式调用EXISTSNODE并验证异常
  }

  // 辅助方法：断言EXTRACT函数的返回值符合预期
  private void assertExtractXml(String xml, String xpath, String namespace, // 方法参数：XML文档、XPath表达式、命名空间、期望的匹配器
      Matcher<? super String> matcher) { // 匹配器类型为String或其父类，用于验证EXTRACT的返回值
    // 构造方法描述字符串，用于断言消息中显示
    String methodDesc = BuiltInMethod.EXTRACT_XML.getMethodName() // 获取EXTRACT方法名称
        + "(" + String.join(", ", xml, xpath, namespace) + ")"; // 构造完整的调用描述，包含所有参数
    // 使用assertThat断言EXTRACT的实际返回值与期望值匹配
    assertThat(methodDesc, XmlFunctions.extractXml(xml, xpath, namespace), matcher); // 调用XmlFunctions.extractXml并验证结果
  }

  // 辅助方法：断言EXTRACT函数抛出预期的异常
  private void assertExtractXmlFailed(String xml, String xpath, String namespace, // 方法参数：XML文档、XPath表达式、命名空间、期望的异常匹配器
      Matcher<? super Throwable> matcher) { // 匹配器类型为Throwable或其父类，用于验证抛出的异常
    // 构造方法描述字符串，用于断言消息中显示
    String methodDesc = BuiltInMethod.EXTRACT_XML.getMethodName() // 获取EXTRACT方法名称
        + "(" + String.join(", ", xml, xpath, namespace) + ")"; // 构造完整的调用描述，包含所有参数
    // 调用assertFailed方法，验证EXTRACT抛出预期的异常
    assertFailed(methodDesc, () -> XmlFunctions.extractXml(xml, xpath, namespace), matcher); // 使用lambda表达式调用EXTRACT并验证异常
  }

  // 辅助方法：断言XMLTRANSFORM函数的返回值符合预期
  private void assertXmlTransform(String xml, String xslt, // 方法参数：XML文档、XSLT样式表、期望的匹配器
      Matcher<? super String> matcher) { // 匹配器类型为String或其父类，用于验证XMLTRANSFORM的返回值
    // 构造方法描述字符串，用于断言消息中显示
    String methodDesc = // 方法描述字符串
        BuiltInMethod.XML_TRANSFORM.getMethodName() + "(" + String.join(", ", xml, xslt) + ")"; // 获取XMLTRANSFORM方法名称并构造完整调用描述
    // 使用assertThat断言XMLTRANSFORM的实际返回值与期望值匹配
    assertThat(methodDesc, XmlFunctions.xmlTransform(xml, xslt), matcher); // 调用XmlFunctions.xmlTransform并验证结果
  }

  // 辅助方法：断言XMLTRANSFORM函数抛出预期的异常
  private void assertXmlTransformFailed(String xml, String xslt, // 方法参数：XML文档、XSLT样式表、期望的异常匹配器
      Matcher<? super Throwable> matcher) { // 匹配器类型为Throwable或其父类，用于验证抛出的异常
    // 构造方法描述字符串，用于断言消息中显示
    String methodDesc = // 方法描述字符串
        BuiltInMethod.XML_TRANSFORM.getMethodName() + "(" + String.join(", ", xml, xslt) + ")"; // 获取XMLTRANSFORM方法名称并构造完整调用描述
    // 调用assertFailed方法，验证XMLTRANSFORM抛出预期的异常
    assertFailed(methodDesc, () -> XmlFunctions.xmlTransform(xml, xslt), matcher); // 使用lambda表达式调用XMLTRANSFORM并验证异常
  }

  // 辅助方法：断言EXTRACTVALUE函数的返回值符合预期
  private void assertExtractValue(String input, String xpath, // 方法参数：XML文档、XPath表达式、期望的匹配器
      Matcher<? super String> matcher) { // 匹配器类型为String或其父类，用于验证EXTRACTVALUE的返回值
    // 构造方法描述字符串，用于断言消息中显示
    String extractMethodDesc = // 方法描述字符串
        BuiltInMethod.EXTRACT_VALUE.getMethodName() + "(" + String.join(", ", input) + ")"; // 获取EXTRACTVALUE方法名称并构造完整调用描述
    // 使用assertThat断言EXTRACTVALUE的实际返回值与期望值匹配
    assertThat(extractMethodDesc, XmlFunctions.extractValue(input, xpath), matcher); // 调用XmlFunctions.extractValue并验证结果
  }

  // 辅助方法：断言EXTRACTVALUE函数抛出预期的异常
  private void assertExtractValueFailed(String input, String xpath, // 方法参数：XML文档、XPath表达式、期望的异常匹配器
      Matcher<? super Throwable> matcher) { // 匹配器类型为Throwable或其父类，用于验证抛出的异常
    // 构造方法描述字符串，用于断言消息中显示
    String extractMethodDesc = // 方法描述字符串
        BuiltInMethod.EXTRACT_VALUE.getMethodName() + "(" + String.join(", ", input, xpath) + ")"; // 获取EXTRACTVALUE方法名称并构造完整调用描述
    // 调用assertFailed方法，验证EXTRACTVALUE抛出预期的异常
    assertFailed(extractMethodDesc, () -> XmlFunctions.extractValue(input, xpath), matcher); // 使用lambda表达式调用EXTRACTVALUE并验证异常
  }

  // 通用辅助方法：断言某个操作抛出预期的异常
  private void assertFailed(String invocationDesc, Supplier<?> supplier, // 方法参数：调用描述、操作提供者（lambda表达式）、期望的异常匹配器
      Matcher<? super Throwable> matcher) { // 匹配器类型为Throwable或其父类，用于验证抛出的异常
    try { // 开始try块，尝试执行可能抛出异常的操作
      supplier.get(); // 调用Supplier的get方法，执行实际的操作
      fail("expect exception, but not: " + invocationDesc); // 如果没有抛出异常，则测试失败，输出调用描述
    } catch (Throwable t) { // 捕获所有类型的异常
      assertThat(invocationDesc, t, matcher); // 验证捕获的异常与期望的异常匹配
    }
  }
}
