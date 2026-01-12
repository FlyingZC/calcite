/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to you under the Apache License, Version 2.0
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
package org.apache.calcite.test; // 包声明：该测试类位于org.apache.calcite.test包下，用于测试Calcite框架中JSON相关功能

import org.apache.calcite.runtime.CalciteException; // 导入Calcite异常类，用于处理Calcite运行时异常
import org.apache.calcite.runtime.JsonFunctions; // 导入JSON函数类，包含所有JSON处理的核心实现
import org.apache.calcite.runtime.SqlFunctions; // 导入SQL函数类，提供SQL函数的运行时支持
import org.apache.calcite.sql.SqlJsonConstructorNullClause; // 导入JSON构造函数的NULL处理子句枚举
import org.apache.calcite.sql.SqlJsonExistsErrorBehavior; // 导入JSON_EXISTS函数的错误行为枚举
import org.apache.calcite.sql.SqlJsonQueryEmptyOrErrorBehavior; // 导入JSON_QUERY函数的空值或错误行为枚举
import org.apache.calcite.sql.SqlJsonQueryWrapperBehavior; // 导入JSON_QUERY函数的包装行为枚举
import org.apache.calcite.sql.SqlJsonValueEmptyOrErrorBehavior; // 导入JSON_VALUE函数的空值或错误行为枚举
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举，用于标识Calcite内置函数

import com.google.common.primitives.Longs; // 导入Google Guava的Long工具类，用于处理long类型的集合
import com.jayway.jsonpath.InvalidJsonException; // 导入JsonPath库的无效JSON异常类
import com.jayway.jsonpath.PathNotFoundException; // 导入JsonPath库的路径未找到异常类

import org.hamcrest.BaseMatcher; // 导入Hamcrest测试框架的基础匹配器类
import org.hamcrest.Description; // 导入Hamcrest测试框架的描述接口
import org.hamcrest.Matcher; // 导入Hamcrest测试框架的匹配器接口
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入Java集合框架的ArrayList动态数组类
import java.util.Arrays; // 导入Java集合框架的Arrays工具类，用于数组操作
import java.util.Collections; // 导入Java集合框架的Collections工具类，提供集合的静态方法
import java.util.HashMap; // 导入Java集合框架的HashMap哈希映射类
import java.util.List; // 导入Java集合框架的List接口
import java.util.Map; // 导入Java集合框架的Map接口
import java.util.Objects; // 导入Java的Objects工具类，提供对象操作方法
import java.util.function.Supplier; // 导入Java函数式接口Supplier，用于提供值的函数
import java.util.stream.Collectors; // 导入Java流式API的Collectors工具类，用于流收集操作

import static org.apache.calcite.test.Matchers.isListOf; // 静态导入：断言结果为列表的匹配器

import static org.hamcrest.CoreMatchers.is; // 静态导入：Hamcrest的相等性匹配器
import static org.hamcrest.CoreMatchers.nullValue; // 静态导入：Hamcrest的null值匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入：Hamcrest的断言方法
import static org.junit.jupiter.api.Assertions.fail; // 静态导入：JUnit 5的失败断言方法

/**
 * Unit test for the methods in {@link SqlFunctions} that implement JSON processing functions.
 * 这是一个单元测试类，用于测试SqlFunctions类中实现JSON处理功能的方法
 * 该类覆盖了Calcite框架中所有JSON相关的SQL函数，包括：
 * - JSON_VALUE: 从JSON文档中提取标量值
 * - JSON_QUERY: 从JSON文档中提取对象或数组
 * - JSON_EXISTS: 检查JSON路径是否存在
 * - JSON_OBJECT: 构造JSON对象
 * - JSON_ARRAY: 构造JSON数组
 * - JSON_TYPE: 返回JSON值的类型
 * - JSON_DEPTH: 返回JSON文档的最大深度
 * - JSON_LENGTH: 返回JSON数组的长度
 * - JSON_KEYS: 返回JSON对象的键
 * - JSON_REMOVE: 删除JSON文档中的数据
 * - JSON_INSERT: 插入数据到JSON文档
 * - JSON_REPLACE: 替换JSON文档中的数据
 * - JSON_SET: 设置JSON文档中的数据
 * - JSON_PRETTY: 格式化JSON文档
 * - JSON_STORAGE_SIZE: 返回JSON文档的存储大小
 * - JSON_OBJECTAGG: 聚合JSON对象
 * - JSON_ARRAYAGG: 聚合JSON数组
 * - IS_JSON_VALUE: 检查是否为有效的JSON值
 * - IS_JSON_OBJECT: 检查是否为JSON对象
 * - IS_JSON_ARRAY: 检查是否为JSON数组
 * - IS_JSON_SCALAR: 检查是否为JSON标量值
 */
class SqlJsonFunctionsTest { // 测试类定义：SqlJsonFunctionsTest，测试所有JSON相关函数的功能

  @Test void testJsonValueExpression() { // 测试方法：测试JSON_VALUE_EXPRESSION函数，验证JSON值表达式的解析功能
    assertJsonValueExpression("{}", // 断言：空JSON对象{}应该被正确解析为JsonValueContext
        is(JsonFunctions.JsonValueContext.withJavaObj(Collections.emptyMap()))); // 期望结果：包含空Map的JsonValueContext对象
  } // 测试方法结束

  @Test void testJsonNullExpression() { // 测试方法：测试JSON null表达式，验证null值的解析功能
    assertJsonValueExpression("null", // 断言：JSON null字符串应该被正确解析为JsonValueContext
        is(JsonFunctions.JsonValueContext.withJavaObj(null))); // 期望结果：包含Java null的JsonValueContext对象
  } // 测试方法结束

  @Test void testJsonApiCommonSyntax() { // 测试方法：测试JSON API通用语法，验证不同路径模式的解析功能
    assertJsonApiCommonSyntax("{\"foo\": \"bar\"}", "$.foo", // 断言：默认模式（STRICT）下，路径$.foo应该返回"bar"
        contextMatches( // 使用自定义匹配器验证JsonPathContext
            JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.STRICT, "bar"))); // 期望结果：STRICT模式下值为"bar"的上下文
    assertJsonApiCommonSyntax("{\"foo\": \"bar\"}", "lax $.foo", // 断言：LAX模式下，路径$.foo应该返回"bar"
        contextMatches( // 使用自定义匹配器验证JsonPathContext
            JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.LAX, "bar"))); // 期望结果：LAX模式下值为"bar"的上下文
    assertJsonApiCommonSyntax("{\"foo\": \"bar\"}", "strict $.foo", // 断言：显式STRICT模式下，路径$.foo应该返回"bar"
        contextMatches( // 使用自定义匹配器验证JsonPathContext
            JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.STRICT, "bar"))); // 期望结果：STRICT模式下值为"bar"的上下文
    assertJsonApiCommonSyntax("{\"foo\": \"bar\"}", "lax $.foo1", // 断言：LAX模式下，不存在的路径应该返回null
        contextMatches( // 使用自定义匹配器验证JsonPathContext
            JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.LAX, null))); // 期望结果：LAX模式下值为null的上下文
    assertJsonApiCommonSyntax("{\"foo\": \"bar\"}", "strict $.foo1", // 断言：STRICT模式下，不存在的路径应该抛出异常
        contextMatches( // 使用自定义匹配器验证JsonPathContext
            JsonFunctions.JsonPathContext.withStrictException( // 期望结果：包含PathNotFoundException的上下文
                new PathNotFoundException("No results for path: $['foo1']")))); // 异常信息：路径未找到
    assertJsonApiCommonSyntax("{\"foo\": 100}", "lax $.foo", // 断言：LAX模式下，数值类型的路径应该正确返回
        contextMatches( // 使用自定义匹配器验证JsonPathContext
            JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.LAX, 100))); // 期望结果：LAX模式下值为100的上下文
  } // 测试方法结束

  @Test void testJsonExists() { // 测试方法：测试JSON_EXISTS函数，验证JSON路径存在性检查功能
    assertJsonExists( // 断言：路径存在时，不同错误行为都应该返回true
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.STRICT, "bar"), // 输入：STRICT模式下值为"bar"的上下文
        SqlJsonExistsErrorBehavior.FALSE, // 错误行为：FALSE（不存在时返回false）
        is(true)); // 期望结果：true
    assertJsonExists( // 断言：路径存在时，不同错误行为都应该返回true
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.STRICT, "bar"), // 输入：STRICT模式下值为"bar"的上下文
        SqlJsonExistsErrorBehavior.TRUE, // 错误行为：TRUE（不存在时返回true）
        is(true)); // 期望结果：true
    assertJsonExists( // 断言：路径存在时，不同错误行为都应该返回true
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.STRICT, "bar"), // 输入：STRICT模式下值为"bar"的上下文
        SqlJsonExistsErrorBehavior.UNKNOWN, // 错误行为：UNKNOWN（不存在时返回null）
        is(true)); // 期望结果：true
    assertJsonExists( // 断言：路径存在时，不同错误行为都应该返回true
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.STRICT, "bar"), // 输入：STRICT模式下值为"bar"的上下文
        SqlJsonExistsErrorBehavior.ERROR, // 错误行为：ERROR（不存在时抛出异常）
        is(true)); // 期望结果：true

    assertJsonExists( // 断言：LAX模式下路径不存在（值为null）时，不同错误行为应该返回false
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.LAX, null), // 输入：LAX模式下值为null的上下文
        SqlJsonExistsErrorBehavior.FALSE, // 错误行为：FALSE
        is(false)); // 期望结果：false
    assertJsonExists( // 断言：LAX模式下路径不存在（值为null）时，不同错误行为应该返回false
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.LAX, null), // 输入：LAX模式下值为null的上下文
        SqlJsonExistsErrorBehavior.TRUE, // 错误行为：TRUE
        is(false)); // 期望结果：false
    assertJsonExists( // 断言：LAX模式下路径不存在（值为null）时，不同错误行为应该返回false
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.LAX, null), // 输入：LAX模式下值为null的上下文
        SqlJsonExistsErrorBehavior.UNKNOWN, // 错误行为：UNKNOWN
        is(false)); // 期望结果：false
    assertJsonExists( // 断言：LAX模式下路径不存在（值为null）时，不同错误行为应该返回false
        JsonFunctions.JsonPathContext.withJavaObj(JsonFunctions.PathMode.LAX, null), // 输入：LAX模式下值为null的上下文
        SqlJsonExistsErrorBehavior.ERROR, // 错误行为：ERROR
        is(false)); // 期望结果：false

    assertJsonExists( // 断言：STRICT模式下路径不存在（抛出异常）时，FALSE错误行为应该返回false
        JsonFunctions.JsonPathContext.withStrictException(new Exception("test message")), // 输入：包含异常的上下文
        SqlJsonExistsErrorBehavior.FALSE, // 错误行为：FALSE
        is(false)); // 期望结果：false
    assertJsonExists( // 断言：STRICT模式下路径不存在（抛出异常）时，TRUE错误行为应该返回true
        JsonFunctions.JsonPathContext.withStrictException(new Exception("test message")), // 输入：包含异常的上下文
        SqlJsonExistsErrorBehavior.TRUE, // 错误行为：TRUE
        is(true)); // 期望结果：true
    assertJsonExists( // 断言：STRICT模式下路径不存在（抛出异常）时，UNKNOWN错误行为应该返回null
        JsonFunctions.JsonPathContext.withStrictException(new Exception("test message")), // 输入：包含异常的上下文
        SqlJsonExistsErrorBehavior.UNKNOWN, // 错误行为：UNKNOWN
        nullValue()); // 期望结果：null
    assertJsonExistsFailed( // 断言：STRICT模式下路径不存在（抛出异常）时，ERROR错误行为应该抛出异常
        JsonFunctions.JsonPathContext.withStrictException(new Exception("test message")), // 输入：包含异常的上下文
        SqlJsonExistsErrorBehavior.ERROR, // 错误行为：ERROR
        errorMatches(new RuntimeException("java.lang.Exception: test message"))); // 期望结果：RuntimeException异常
  } // 测试方法结束

  @Test void testJsonValueAny() { // 测试方法：测试JSON_VALUE函数，验证从JSON中提取标量值的功能
    assertJsonValueAny( // 断言：正常情况下，LAX模式提取值应该返回"bar"
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, "bar"),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        is("bar")); // 期望结果："bar"
    assertJsonValueAny( // 断言：LAX模式下值为null时，NULL空值行为应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        nullValue()); // 期望结果：null
    assertJsonValueAny( // 断言：LAX模式下值为null时，DEFAULT空值行为应该返回默认值
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        SqlJsonValueEmptyOrErrorBehavior.DEFAULT, // 空值行为：DEFAULT
        "empty", // 空值默认值："empty"
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        is("empty")); // 期望结果："empty"
    assertJsonValueAnyFailed( // 断言：LAX模式下值为null时，ERROR空值行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        SqlJsonValueEmptyOrErrorBehavior.ERROR, // 空值行为：ERROR
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        errorMatches( // 期望结果：CalciteException异常
            new CalciteException("Empty result of JSON_VALUE function is not " // 异常信息：JSON_VALUE函数不允许空结果
                + "allowed", null)));
    assertJsonValueAny( // 断言：LAX模式下值为空列表时，NULL空值行为应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为空列表的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, Collections.emptyList()),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        nullValue()); // 期望结果：null
    assertJsonValueAny( // 断言：LAX模式下值为空列表时，DEFAULT空值行为应该返回默认值
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为空列表的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, Collections.emptyList()),
        SqlJsonValueEmptyOrErrorBehavior.DEFAULT, // 空值行为：DEFAULT
        "empty", // 空值默认值："empty"
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        is("empty")); // 期望结果："empty"
    assertJsonValueAnyFailed( // 断言：LAX模式下值为空列表时，ERROR空值行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为空列表的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, Collections.emptyList()),
        SqlJsonValueEmptyOrErrorBehavior.ERROR, // 空值行为：ERROR
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        errorMatches( // 期望结果：CalciteException异常
            new CalciteException("Empty result of JSON_VALUE function is not " // 异常信息：JSON_VALUE函数不允许空结果
                + "allowed", null)));
    assertJsonValueAny( // 断言：抛出异常时，NULL空值行为应该返回null
        JsonFunctions.JsonPathContext // 输入：包含异常的上下文
            .withStrictException(new Exception("test message")),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        nullValue()); // 期望结果：null
    assertJsonValueAny( // 断言：抛出异常时，NULL空值行为和DEFAULT错误行为应该返回默认值
        JsonFunctions.JsonPathContext // 输入：包含异常的上下文
            .withStrictException(new Exception("test message")),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.DEFAULT, // 错误行为：DEFAULT
        "empty", // 错误默认值："empty"
        is("empty")); // 期望结果："empty"
    assertJsonValueAnyFailed( // 断言：抛出异常时，ERROR错误行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：包含异常的上下文
            .withStrictException(new Exception("test message")),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.ERROR, // 错误行为：ERROR
        null, // 错误默认值：null
        errorMatches( // 期望结果：RuntimeException异常
            new RuntimeException("java.lang.Exception: test message")));
    assertJsonValueAny( // 断言：STRICT模式下值为空列表时，NULL空值行为应该返回null
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为空列表的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, Collections.emptyList()),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        null, // 错误默认值：null
        nullValue()); // 期望结果：null
    assertJsonValueAny( // 断言：STRICT模式下值为空列表时，DEFAULT空值行为应该返回默认值
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为空列表的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, Collections.emptyList()),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.DEFAULT, // 错误行为：DEFAULT
        "empty", // 错误默认值："empty"
        is("empty")); // 期望结果："empty"
    assertJsonValueAnyFailed( // 断言：STRICT模式下值为空列表时，ERROR错误行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为空列表的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, Collections.emptyList()),
        SqlJsonValueEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        null, // 空值默认值：null
        SqlJsonValueEmptyOrErrorBehavior.ERROR, // 错误行为：ERROR
        null, // 错误默认值：null
        errorMatches( // 期望结果：CalciteException异常
            new CalciteException("Strict jsonpath mode requires scalar value, " // 异常信息：STRICT模式要求标量值
                + "and the actual value is: '[]'", null)));
  } // 测试方法结束

  @Test void testJsonQuery() { // 测试方法：测试JSON_QUERY函数，验证从JSON中提取对象或数组的功能
    assertJsonQuery( // 断言：LAX模式下提取列表，WITHOUT_ARRAY包装应该返回JSON字符串
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, Collections.singletonList("bar")),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY（不包装为数组）
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("[\"bar\"]")); // 期望结果：JSON字符串"[\"bar\"]"
    assertJsonQuery( // 断言：LAX模式下值为null时，WITHOUT_ARRAY包装应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        nullValue()); // 期望结果：null
    assertJsonQuery( // 断言：LAX模式下值为null时，EMPTY_ARRAY空值行为应该返回空数组
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_ARRAY, // 空值行为：EMPTY_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("[]")); // 期望结果：空数组"[]"
    assertJsonQuery( // 断言：LAX模式下值为null时，EMPTY_OBJECT空值行为应该返回空对象
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_OBJECT, // 空值行为：EMPTY_OBJECT
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("{}")); // 期望结果：空对象"{}"
    assertJsonQueryFailed( // 断言：LAX模式下值为null时，ERROR空值行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.ERROR, // 空值行为：ERROR
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        errorMatches( // 期望结果：CalciteException异常
            new CalciteException("Empty result of JSON_QUERY function is not " // 异常信息：JSON_QUERY函数不允许空结果
                + "allowed", null)));

    assertJsonQuery( // 断言：LAX模式下值为标量"bar"时，WITHOUT_ARRAY包装应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, "bar"),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        nullValue()); // 期望结果：null
    assertJsonQuery( // 断言：LAX模式下值为标量"bar"时，EMPTY_ARRAY空值行为应该返回空数组
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, "bar"),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_ARRAY, // 空值行为：EMPTY_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("[]")); // 期望结果：空数组"[]"
    assertJsonQuery( // 断言：LAX模式下值为标量"bar"时，EMPTY_OBJECT空值行为应该返回空对象
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, "bar"),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_OBJECT, // 空值行为：EMPTY_OBJECT
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("{}")); // 期望结果：空对象"{}"
    assertJsonQueryFailed( // 断言：LAX模式下值为标量"bar"时，ERROR空值行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, "bar"),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.ERROR, // 空值行为：ERROR
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        errorMatches( // 期望结果：CalciteException异常
            new CalciteException("Empty result of JSON_QUERY function is not " // 异常信息：JSON_QUERY函数不允许空结果
                + "allowed", null)));
    assertJsonQuery( // 断言：抛出异常时，EMPTY_ARRAY错误行为应该返回空数组
        JsonFunctions.JsonPathContext // 输入：包含异常的上下文
            .withStrictException(new Exception("test message")),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_ARRAY, // 错误行为：EMPTY_ARRAY
        is("[]")); // 期望结果：空数组"[]"
    assertJsonQuery( // 断言：抛出异常时，EMPTY_OBJECT错误行为应该返回空对象
        JsonFunctions.JsonPathContext // 输入：包含异常的上下文
            .withStrictException(new Exception("test message")),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_OBJECT, // 错误行为：EMPTY_OBJECT
        is("{}")); // 期望结果：空对象"{}"
    assertJsonQueryFailed( // 断言：抛出异常时，ERROR错误行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：包含异常的上下文
            .withStrictException(new Exception("test message")),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.ERROR, // 错误行为：ERROR
        errorMatches( // 期望结果：RuntimeException异常
            new RuntimeException("java.lang.Exception: test message")));
    assertJsonQuery( // 断言：STRICT模式下值为标量"bar"时，WITHOUT_ARRAY包装应该返回null
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, "bar"),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        nullValue()); // 期望结果：null
    assertJsonQuery( // 断言：STRICT模式下值为标量"bar"时，EMPTY_ARRAY错误行为应该返回空数组
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, "bar"),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_ARRAY, // 错误行为：EMPTY_ARRAY
        is("[]")); // 期望结果：空数组"[]"
    assertJsonQueryFailed( // 断言：STRICT模式下值为标量"bar"时，ERROR错误行为应该抛出异常
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, "bar"),
        SqlJsonQueryWrapperBehavior.WITHOUT_ARRAY, // 包装行为：WITHOUT_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.ERROR, // 错误行为：ERROR
        errorMatches( // 期望结果：CalciteException异常
            new CalciteException("Strict jsonpath mode requires array or " // 异常信息：STRICT模式要求数组或对象值
                + "object value, and the actual value is: 'bar'", null)));

    // wrapper behavior test // 包装行为测试

    assertJsonQuery( // 断言：STRICT模式下值为标量"bar"时，WITH_UNCONDITIONAL_ARRAY包装应该无条件包装为数组
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, "bar"),
        SqlJsonQueryWrapperBehavior.WITH_UNCONDITIONAL_ARRAY, // 包装行为：WITH_UNCONDITIONAL_ARRAY（无条件包装）
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("[\"bar\"]")); // 期望结果：包装后的数组"[\"bar\"]"

    assertJsonQuery( // 断言：STRICT模式下值为标量"bar"时，WITH_CONDITIONAL_ARRAY包装应该条件包装为数组
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, "bar"),
        SqlJsonQueryWrapperBehavior.WITH_CONDITIONAL_ARRAY, // 包装行为：WITH_CONDITIONAL_ARRAY（条件包装）
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("[\"bar\"]")); // 期望结果：包装后的数组"[\"bar\"]"

    assertJsonQuery( // 断言：STRICT模式下值为列表["bar"]时，WITH_UNCONDITIONAL_ARRAY包装应该无条件再包装
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT,
                Collections.singletonList("bar")),
        SqlJsonQueryWrapperBehavior.WITH_UNCONDITIONAL_ARRAY, // 包装行为：WITH_UNCONDITIONAL_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("[[\"bar\"]]")); // 期望结果：双重包装的数组"[[\"bar\"]]"
    assertJsonQuery( // 断言：STRICT模式下值为列表["bar"]时，WITH_CONDITIONAL_ARRAY包装不应该再包装
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT,
                Collections.singletonList("bar")),
        SqlJsonQueryWrapperBehavior.WITH_CONDITIONAL_ARRAY, // 包装行为：WITH_CONDITIONAL_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        is("[\"bar\"]")); // 期望结果：保持原样"[\"bar\"]"

    // jsonize test // jsonize测试

    assertJsonQuery( // 断言：不jsonize时，应该返回Java对象列表
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT,
                Collections.singletonList("bar")),
        SqlJsonQueryWrapperBehavior.WITH_CONDITIONAL_ARRAY, // 包装行为：WITH_CONDITIONAL_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 空值行为：NULL
        SqlJsonQueryEmptyOrErrorBehavior.NULL, // 错误行为：NULL
        false, // jsonize：false（不转换为JSON字符串）
        isListOf("bar")); // 期望结果：Java列表["bar"]
    assertJsonQuery( // 断言：抛出异常时，EMPTY_ARRAY错误行为应该返回空列表
        JsonFunctions.JsonPathContext // 输入：包含异常的上下文
            .withUnknownException(new Exception("test message")),
        SqlJsonQueryWrapperBehavior.WITH_CONDITIONAL_ARRAY, // 包装行为：WITH_CONDITIONAL_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_ARRAY, // 空值行为：EMPTY_ARRAY
        SqlJsonQueryEmptyOrErrorBehavior.EMPTY_ARRAY, // 错误行为：EMPTY_ARRAY
        false, // jsonize：false
        is(Collections.emptyList())); // 期望结果：空列表
  } // 测试方法结束

  @Test void testJsonize() { // 测试方法：测试JSONIZE函数，验证将Java对象转换为JSON字符串的功能
    assertJsonize(new HashMap<>(), // 断言：空HashMap应该转换为空JSON对象
        is("{}")); // 期望结果：空对象"{}"
  } // 测试方法结束

  @Test void assertJsonPretty() { // 测试方法：测试JSON_PRETTY函数，验证JSON格式化功能
    assertJsonPretty( // 断言：空对象应该格式化为带空格的JSON
        JsonFunctions.JsonValueContext.withJavaObj(new HashMap<>()), is("{ }")); // 期望结果：格式化后的空对象"{ }"
    assertJsonPretty( // 断言：列表应该格式化为带空格的JSON数组
        JsonFunctions.JsonValueContext.withJavaObj(Longs.asList(1, 2)), is("[ 1, 2 ]")); // 期望结果：格式化后的数组"[ 1, 2 ]"
  } // 测试方法结束

  @Test void testDejsonize() { // 测试方法：测试DEJSONIZE函数，验证将JSON字符串转换为Java对象的功能
    assertDejsonize("{}", // 断言：空JSON对象字符串应该转换为空Map
        is(Collections.emptyMap())); // 期望结果：空Map
    assertDejsonize("[]", // 断言：空JSON数组字符串应该转换为空List
        is(Collections.emptyList())); // 期望结果：空List

    // expect exception thrown // 期望抛出异常
    final String message = "com.fasterxml.jackson.core.JsonParseException: " // 定义预期的异常消息
        + "Unexpected close marker '}': expected ']' (for Array starting at " // 异常详情：意外的关闭标记
        + "[Source: (String)\"[}\"; line: 1, column: 1])\n at [Source: " // 异常位置信息
        + "(String)\"[}\"; line: 1, column: 3]";
    assertDejsonizeFailed("[}", // 断言：无效的JSON字符串应该抛出异常
        errorMatches(new InvalidJsonException(message))); // 期望结果：InvalidJsonException异常
  } // 测试方法结束

  @Test void testJsonObject() { // 测试方法：测试JSON_OBJECT函数，验证构造JSON对象的功能
    assertJsonObject(is("{}"), SqlJsonConstructorNullClause.NULL_ON_NULL); // 断言：无参数时应该返回空对象，NULL_ON_NULL策略
    assertJsonObject( // 断言：键值对"foo":"bar"应该构造为JSON对象
        is("{\"foo\":\"bar\"}"), SqlJsonConstructorNullClause.NULL_ON_NULL, // NULL_ON_NULL策略：null值保留为JSON null
        "foo", // 键："foo"
        "bar"); // 值："bar"
    assertJsonObject( // 断言：值为null时，NULL_ON_NULL策略应该保留为JSON null
        is("{\"foo\":null}"), SqlJsonConstructorNullClause.NULL_ON_NULL, // NULL_ON_NULL策略
        "foo", // 键："foo"
        null); // 值：null
    assertJsonObject( // 断言：值为null时，ABSENT_ON_NULL策略应该省略该键
        is("{}"), SqlJsonConstructorNullClause.ABSENT_ON_NULL, // ABSENT_ON_NULL策略：null值省略键
        "foo", // 键："foo"
        null); // 值：null
  } // 测试方法结束

  @Test void testJsonType() { // 测试方法：测试JSON_TYPE函数，验证返回JSON值类型的功能
    assertJsonType(is("OBJECT"), "{}"); // 断言：空对象应该返回类型"OBJECT"
    assertJsonType(is("ARRAY"), // 断言：包含元素的数组应该返回类型"ARRAY"
        "[\"foo\",null]");
    assertJsonType(is("NULL"), "null"); // 断言：null值应该返回类型"NULL"
    assertJsonType(is("BOOLEAN"), "false"); // 断言：布尔值应该返回类型"BOOLEAN"
    assertJsonType(is("INTEGER"), "12"); // 断言：整数应该返回类型"INTEGER"
    assertJsonType(is("DOUBLE"), "11.22"); // 断言：浮点数应该返回类型"DOUBLE"
  } // 测试方法结束

  @Test void testJsonDepth() { // 测试方法：测试JSON_DEPTH函数，验证计算JSON文档深度的功能
    assertJsonDepth(is(1), "{}"); // 断言：空对象的深度应该为1
    assertJsonDepth(is(1), "false"); // 断言：布尔值的深度应该为1
    assertJsonDepth(is(1), "12"); // 断言：整数的深度应该为1
    assertJsonDepth(is(1), "11.22"); // 断言：浮点数的深度应该为1
    assertJsonDepth(is(2), // 断言：包含元素的数组的深度应该为2
        "[\"foo\",null]");
    assertJsonDepth(is(3), // 断言：嵌套结构的深度应该为3
        "{\"a\": [10, true]}");
    assertJsonDepth(nullValue(), "null"); // 断言：null值的深度应该返回null
  } // 测试方法结束

  @Test void testJsonLength() { // 测试方法：测试JSON_LENGTH函数，验证计算JSON数组长度的功能
    assertJsonLength( // 断言：LAX模式下，列表的长度应该为1
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, Collections.singletonList("bar")),
        is(1)); // 期望结果：长度为1
    assertJsonLength( // 断言：LAX模式下，null值的长度应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        nullValue()); // 期望结果：null
    assertJsonLength( // 断言：STRICT模式下，列表的长度应该为1
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, Collections.singletonList("bar")),
        is(1)); // 期望结果：长度为1
    assertJsonLength( // 断言：LAX模式下，标量值"bar"的长度应该为1（视为单元素数组）
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, "bar"),
        is(1)); // 期望结果：长度为1
  } // 测试方法结束

  @Test void testJsonKeys() { // 测试方法：测试JSON_KEYS函数，验证获取JSON对象键的功能
    assertJsonKeys( // 断言：LAX模式下，列表不是对象，应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, Collections.singletonList("bar")),
        is("null")); // 期望结果：字符串"null"
    assertJsonKeys( // 断言：LAX模式下，null值应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为null的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, null),
        is("null")); // 期望结果：字符串"null"
    assertJsonKeys( // 断言：STRICT模式下，列表不是对象，应该返回null
        JsonFunctions.JsonPathContext // 输入：STRICT模式下值为列表["bar"]的上下文
            .withJavaObj(JsonFunctions.PathMode.STRICT, Collections.singletonList("bar")),
        is("null")); // 期望结果：字符串"null"
    assertJsonKeys( // 断言：LAX模式下，标量值"bar"不是对象，应该返回null
        JsonFunctions.JsonPathContext // 输入：LAX模式下值为"bar"的上下文
            .withJavaObj(JsonFunctions.PathMode.LAX, "bar"),
        is("null")); // 期望结果：字符串"null"
  } // 测试方法结束

  @Test void testJsonRemove() { // 测试方法：测试JSON_REMOVE函数，验证从JSON文档中删除数据的功能
    assertJsonRemove( // 断言：删除路径$.a应该从对象中移除该键
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new String[]{"$.a"}, // 路径：删除$.a
        is("{\"b\":[2]}")); // 期望结果：删除后的对象
    assertJsonRemove( // 断言：删除多个路径应该从对象中移除多个键
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new String[]{"$.a", "$.b"}, // 路径：删除$.a和$.b
        is("{}")); // 期望结果：空对象
  } // 测试方法结束

  @Test void testJsonStorageSize() { // 测试方法：测试JSON_STORAGE_SIZE函数，验证计算JSON文档存储大小的功能
    assertJsonStorageSize("[100, \"sakila\", [1, 3, 5], 425.05]", is(29)); // 断言：复杂JSON文档的存储大小应该为29字节
    assertJsonStorageSize("null", is(4)); // 断言：null值的存储大小应该为4字节
    assertJsonStorageSize(JsonFunctions.JsonValueContext.withJavaObj(null), is(4)); // 断言：Java null的存储大小应该为4字节
  } // 测试方法结束

  @Test void testJsonObjectAggAdd() { // 测试方法：测试JSON_OBJECTAGG_ADD函数，验证向聚合对象添加键值对的功能
    Map<String, Object> map = new HashMap<>(); // 创建空的HashMap用于聚合
    Map<String, Object> expected = new HashMap<>(); // 创建期望的Map
    expected.put("foo", "bar"); // 添加期望的键值对
    assertJsonObjectAggAdd(map, "foo", "bar", // 断言：添加键值对"foo":"bar"到聚合对象
        SqlJsonConstructorNullClause.NULL_ON_NULL, is(expected)); // NULL_ON_NULL策略
    expected.put("foo1", null); // 添加期望的键值对，值为null
    assertJsonObjectAggAdd(map, "foo1", null, // 断言：添加键值对"foo1":null到聚合对象
        SqlJsonConstructorNullClause.NULL_ON_NULL, is(expected)); // NULL_ON_NULL策略：保留null
    assertJsonObjectAggAdd(map, "foo2", null, // 断言：添加键值对"foo2":null到聚合对象
        SqlJsonConstructorNullClause.ABSENT_ON_NULL, is(expected)); // ABSENT_ON_NULL策略：省略null
  } // 测试方法结束

  @Test void testJsonArray() { // 测试方法：测试JSON_ARRAY函数，验证构造JSON数组的功能
    assertJsonArray(is("[]"), SqlJsonConstructorNullClause.NULL_ON_NULL); // 断言：无参数时应该返回空数组，NULL_ON_NULL策略
    assertJsonArray( // 断言：单元素应该构造为JSON数组
        is("[\"foo\"]"), SqlJsonConstructorNullClause.NULL_ON_NULL, "foo"); // NULL_ON_NULL策略
    assertJsonArray( // 断言：多元素包括null应该构造为JSON数组
        is("[\"foo\",null]"), SqlJsonConstructorNullClause.NULL_ON_NULL, // NULL_ON_NULL策略：保留null
        "foo", // 元素1："foo"
        null); // 元素2：null
    assertJsonArray( // 断言：多元素包括null，ABSENT_ON_NULL策略应该省略null
        is("[\"foo\"]"), // 期望结果：只包含"foo"的数组
        SqlJsonConstructorNullClause.ABSENT_ON_NULL, // ABSENT_ON_NULL策略：省略null
        "foo", // 元素1："foo"
        null); // 元素2：null（将被省略）
  } // 测试方法结束

  @Test void testJsonArrayAggAdd() { // 测试方法：测试JSON_ARRAYAGG_ADD函数，验证向聚合数组添加元素的功能
    List<Object> list = new ArrayList<>(); // 创建空的ArrayList用于聚合
    List<Object> expected = new ArrayList<>(); // 创建期望的List
    expected.add("foo"); // 添加期望的元素
    assertJsonArrayAggAdd(list, "foo", // 断言：添加元素"foo"到聚合数组
        SqlJsonConstructorNullClause.NULL_ON_NULL, is(expected)); // NULL_ON_NULL策略
    expected.add(null); // 添加期望的元素null
    assertJsonArrayAggAdd(list, null, // 断言：添加元素null到聚合数组
        SqlJsonConstructorNullClause.NULL_ON_NULL, is(expected)); // NULL_ON_NULL策略：保留null
    assertJsonArrayAggAdd(list, null, // 断言：添加元素null到聚合数组
        SqlJsonConstructorNullClause.ABSENT_ON_NULL, is(expected)); // ABSENT_ON_NULL策略：省略null
  } // 测试方法结束

  @Test void testJsonPredicate() { // 测试方法：测试JSON谓词函数，验证JSON类型检查功能
    assertIsJsonValue("[]", is(true)); // 断言：空数组是有效的JSON值
    assertIsJsonValue("{}", is(true)); // 断言：空对象是有效的JSON值
    assertIsJsonValue("100", is(true)); // 断言：数字是有效的JSON值
    assertIsJsonValue("{]", is(false)); // 断言：无效的JSON字符串不是有效的JSON值
    assertIsJsonValue(null, nullValue()); // 断言：null输入应该返回null
    assertIsJsonObject("[]", is(false)); // 断言：数组不是JSON对象
    assertIsJsonObject("{}", is(true)); // 断言：空对象是JSON对象
    assertIsJsonObject("100", is(false)); // 断言：数字不是JSON对象
    assertIsJsonObject("{]", is(false)); // 断言：无效的JSON字符串不是JSON对象
    assertIsJsonObject(null, nullValue()); // 断言：null输入应该返回null
    assertIsJsonArray("[]", is(true)); // 断言：空数组是JSON数组
    assertIsJsonArray("{}", is(false)); // 断言：对象不是JSON数组
    assertIsJsonArray("100", is(false)); // 断言：数字不是JSON数组
    assertIsJsonArray("{]", is(false)); // 断言：无效的JSON字符串不是JSON数组
    assertIsJsonArray(null, nullValue()); // 断言：null输入应该返回null
    assertIsJsonScalar("[]", is(false)); // 断言：数组不是JSON标量值
    assertIsJsonScalar("{}", is(false)); // 断言：对象不是JSON标量值
    assertIsJsonScalar("100", is(true)); // 断言：数字是JSON标量值
    assertIsJsonScalar("{]", is(false)); // 断言：无效的JSON字符串不是JSON标量值
    assertIsJsonScalar(null, nullValue()); // 断言：null输入应该返回null
  } // 测试方法结束

  @Test public void testJsonInsert() { // 测试方法：测试JSON_INSERT函数，验证向JSON文档插入数据的功能
    assertJsonInsert( // 断言：插入数据到JSON对象
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new Object[]{"$.a", 10, "$.c", "[true]"}, // 键值对：插入$.a=10和$.c="[true]"
        is("{\"a\":1,\"b\":[2],\"c\":\"[true]\"}")); // 期望结果：插入后的对象（注意：$.a已存在，所以不会覆盖）
    assertJsonInsert( // 断言：插入数据到根路径
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new Object[]{"$", 10, "$.c", "[true]"}, // 键值对：插入$=10和$.c="[true]"
        is("{\"a\":1,\"b\":[2],\"c\":\"[true]\"}")); // 期望结果：插入后的对象（注意：$已存在，所以不会覆盖）
  } // 测试方法结束

  @Test public void testJsonReplace() { // 测试方法：测试JSON_REPLACE函数，验证替换JSON文档中数据的功能
    assertJsonReplace( // 断言：替换JSON对象中的数据
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new Object[]{"$.a", 10, "$.c", "[true]"}, // 键值对：替换$.a=10和$.c="[true]"
        is("{\"a\":10,\"b\":[2]}")); // 期望结果：替换后的对象（注意：$.c不存在，所以不会插入）
    assertJsonReplace( // 断言：替换根路径的数据
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new Object[]{"$", 10, "$.c", "[true]"}, // 键值对：替换$=10和$.c="[true]"
        is("10")); // 期望结果：替换后的值（注意：$存在，所以会被替换）
  } // 测试方法结束

  @Test public void testJsonSet() { // 测试方法：测试JSON_SET函数，验证设置JSON文档中数据的功能
    assertJsonSet( // 断言：设置JSON对象中的数据
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new Object[]{"$.a", 10, "$.c", "[true]"}, // 键值对：设置$.a=10和$.c="[true]"
        is("{\"a\":10,\"b\":[2],\"c\":\"[true]\"}")); // 期望结果：设置后的对象（注意：$.c不存在，会被插入）
    assertJsonSet( // 断言：设置根路径的数据
        JsonFunctions.jsonValueExpression("{\"a\": 1, \"b\": [2]}"), // 输入：JSON对象
        new Object[]{"$", 10, "$.c", "[true]"}, // 键值对：设置$=10和$.c="[true]"
        is("10")); // 期望结果：设置后的值（注意：$存在，会被替换）
  } // 测试方法结束

  private void assertJsonValueExpression(String input, // 私有辅助方法：断言JSON_VALUE_EXPRESSION函数的输出
      Matcher<? super JsonFunctions.JsonValueContext> matcher) { // 参数：输入字符串和期望的匹配器
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_VALUE_EXPRESSION, input), // 生成调用描述
        JsonFunctions.jsonValueExpression(input), matcher); // 调用jsonValueExpression函数并断言结果
  } // 方法结束

  private void assertJsonApiCommonSyntax(String input, String pathSpec, // 私有辅助方法：断言JSON_API_COMMON_SYNTAX函数的输出（字符串输入）
      Matcher<? super JsonFunctions.JsonPathContext> matcher) { // 参数：输入字符串、路径规范和期望的匹配器
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_API_COMMON_SYNTAX, input, pathSpec), // 生成调用描述
        JsonFunctions.jsonApiCommonSyntax(input, pathSpec), matcher); // 调用jsonApiCommonSyntax函数并断言结果
  } // 方法结束

  private void assertJsonApiCommonSyntax(JsonFunctions.JsonValueContext input, // 私有辅助方法：断言JSON_API_COMMON_SYNTAX函数的输出（JsonValueContext输入）
      String pathSpec, Matcher<? super JsonFunctions.JsonPathContext> matcher) { // 参数：输入上下文、路径规范和期望的匹配器
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_API_COMMON_SYNTAX, input, pathSpec), // 生成调用描述
        JsonFunctions.jsonApiCommonSyntax(input, pathSpec), matcher); // 调用jsonApiCommonSyntax函数并断言结果
  } // 方法结束

  private void assertJsonExists(JsonFunctions.JsonPathContext context, // 私有辅助方法：断言JSON_EXISTS函数的输出
      SqlJsonExistsErrorBehavior errorBehavior, // 参数：路径上下文、错误行为和期望的匹配器
      Matcher<? super Boolean> matcher) {
    final JsonFunctions.StatefulFunction f = // 创建有状态函数实例
        new JsonFunctions.StatefulFunction();
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_EXISTS2, context, errorBehavior), // 生成调用描述
        f.jsonExists(context, errorBehavior), matcher); // 调用jsonExists函数并断言结果
  } // 方法结束

  private void assertJsonExistsFailed(JsonFunctions.JsonPathContext context, // 私有辅助方法：断言JSON_EXISTS函数应该抛出异常
      SqlJsonExistsErrorBehavior errorBehavior, // 参数：路径上下文、错误行为和期望的异常匹配器
      Matcher<? super Throwable> matcher) {
    final JsonFunctions.StatefulFunction f = // 创建有状态函数实例
        new JsonFunctions.StatefulFunction();
    assertFailed( // 断言：验证函数调用应该失败
        invocationDesc(BuiltInMethod.JSON_EXISTS2, context, errorBehavior), // 生成调用描述
        () -> f.jsonExists( // 使用lambda表达式调用jsonExists函数
            context, errorBehavior), matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonValueAny(JsonFunctions.JsonPathContext context, // 私有辅助方法：断言JSON_VALUE函数的输出
      SqlJsonValueEmptyOrErrorBehavior emptyBehavior, // 参数：路径上下文、空值行为、空值默认值、错误行为、错误默认值和期望的匹配器
      Object defaultValueOnEmpty,
      SqlJsonValueEmptyOrErrorBehavior errorBehavior,
      Object defaultValueOnError,
      Matcher<Object> matcher) {
    final JsonFunctions.StatefulFunction f = // 创建有状态函数实例
        new JsonFunctions.StatefulFunction();
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_VALUE, context, emptyBehavior, // 生成调用描述
            defaultValueOnEmpty, errorBehavior, defaultValueOnError),
        f.jsonValue(context, emptyBehavior, defaultValueOnEmpty, // 调用jsonValue函数并断言结果
            errorBehavior, defaultValueOnError),
        matcher);
  } // 方法结束

  private void assertJsonValueAnyFailed(JsonFunctions.JsonPathContext input, // 私有辅助方法：断言JSON_VALUE函数应该抛出异常
      SqlJsonValueEmptyOrErrorBehavior emptyBehavior, // 参数：路径上下文、空值行为、空值默认值、错误行为、错误默认值和期望的异常匹配器
      Object defaultValueOnEmpty,
      SqlJsonValueEmptyOrErrorBehavior errorBehavior,
      Object defaultValueOnError,
      Matcher<? super Throwable> matcher) {
    final JsonFunctions.StatefulFunction f = // 创建有状态函数实例
        new JsonFunctions.StatefulFunction();
    assertFailed( // 断言：验证函数调用应该失败
        invocationDesc(BuiltInMethod.JSON_VALUE, input, emptyBehavior, // 生成调用描述
            defaultValueOnEmpty, errorBehavior, defaultValueOnError),
        () -> f.jsonValue(input, emptyBehavior, // 使用lambda表达式调用jsonValue函数
            defaultValueOnEmpty, errorBehavior, defaultValueOnError),
        matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonQuery(JsonFunctions.JsonPathContext input, // 私有辅助方法：断言JSON_QUERY函数的输出（默认jsonize=true）
      SqlJsonQueryWrapperBehavior wrapperBehavior, // 参数：路径上下文、包装行为、空值行为、错误行为和期望的匹配器
      SqlJsonQueryEmptyOrErrorBehavior emptyBehavior,
      SqlJsonQueryEmptyOrErrorBehavior errorBehavior,
      Matcher<? super Object> matcher) {
    assertJsonQuery(input, wrapperBehavior, emptyBehavior, errorBehavior, true, matcher); // 调用重载方法，jsonize=true
  } // 方法结束

  private void assertJsonQuery(JsonFunctions.JsonPathContext input, // 私有辅助方法：断言JSON_QUERY函数的输出（指定jsonize参数）
      SqlJsonQueryWrapperBehavior wrapperBehavior, // 参数：路径上下文、包装行为、空值行为、错误行为、jsonize标志和期望的匹配器
      SqlJsonQueryEmptyOrErrorBehavior emptyBehavior,
      SqlJsonQueryEmptyOrErrorBehavior errorBehavior,
      boolean jsonize,
      Matcher<? super Object> matcher) {
    final JsonFunctions.StatefulFunction f = // 创建有状态函数实例
        new JsonFunctions.StatefulFunction();
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_QUERY, input, wrapperBehavior, // 生成调用描述
            emptyBehavior, errorBehavior),
        f.jsonQuery(input, wrapperBehavior, emptyBehavior, // 调用jsonQuery函数并断言结果
            errorBehavior, jsonize),
        matcher);
  } // 方法结束

  private void assertJsonQueryFailed(JsonFunctions.JsonPathContext input, // 私有辅助方法：断言JSON_QUERY函数应该抛出异常
      SqlJsonQueryWrapperBehavior wrapperBehavior, // 参数：路径上下文、包装行为、空值行为、错误行为和期望的异常匹配器
      SqlJsonQueryEmptyOrErrorBehavior emptyBehavior,
      SqlJsonQueryEmptyOrErrorBehavior errorBehavior,
      Matcher<? super Throwable> matcher) {
    final JsonFunctions.StatefulFunction f = // 创建有状态函数实例
        new JsonFunctions.StatefulFunction();
    assertFailed( // 断言：验证函数调用应该失败
        invocationDesc(BuiltInMethod.JSON_QUERY, input, wrapperBehavior, // 生成调用描述
            emptyBehavior, errorBehavior),
        () -> f.jsonQuery(input, wrapperBehavior, emptyBehavior, // 使用lambda表达式调用jsonQuery函数
            errorBehavior, true),
        matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonize(Object input, // 私有辅助方法：断言JSONIZE函数的输出
      Matcher<? super String> matcher) { // 参数：输入对象和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.JSONIZE, input), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonize(input), // 调用jsonize函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonPretty(JsonFunctions.JsonValueContext input, // 私有辅助方法：断言JSON_PRETTY函数的输出
      Matcher<? super String> matcher) { // 参数：输入上下文和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.JSON_PRETTY, input), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonPretty(input), // 调用jsonPretty函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonPrettyFailed(JsonFunctions.JsonValueContext input, // 私有辅助方法：断言JSON_PRETTY函数应该抛出异常
      Matcher<? super Throwable> matcher) { // 参数：输入上下文和期望的异常匹配器
    assertFailed(invocationDesc(BuiltInMethod.JSON_PRETTY, input), // 断言：验证函数调用应该失败
        () -> JsonFunctions.jsonPretty(input), // 使用lambda表达式调用jsonPretty函数
        matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonLength(JsonFunctions.JsonPathContext input, // 私有辅助方法：断言JSON_LENGTH函数的输出
      Matcher<? super Integer> matcher) { // 参数：输入上下文和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.JSON_LENGTH, input), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonLength(input), // 调用jsonLength函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonLengthFailed(JsonFunctions.JsonValueContext input, // 私有辅助方法：断言JSON_LENGTH函数应该抛出异常
      Matcher<? super Throwable> matcher) { // 参数：输入上下文和期望的异常匹配器
    assertFailed(invocationDesc(BuiltInMethod.JSON_LENGTH, input), // 断言：验证函数调用应该失败
        () -> JsonFunctions.jsonLength(input), // 使用lambda表达式调用jsonLength函数
        matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonKeys(JsonFunctions.JsonPathContext input, // 私有辅助方法：断言JSON_KEYS函数的输出
      Matcher<? super String> matcher) { // 参数：输入上下文和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.JSON_KEYS, input), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonKeys(input), // 调用jsonKeys函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonKeysFailed(JsonFunctions.JsonValueContext input, // 私有辅助方法：断言JSON_KEYS函数应该抛出异常
      Matcher<? super Throwable> matcher) { // 参数：输入上下文和期望的异常匹配器
    assertFailed(invocationDesc(BuiltInMethod.JSON_KEYS, input), // 断言：验证函数调用应该失败
        () -> JsonFunctions.jsonKeys(input), // 使用lambda表达式调用jsonKeys函数
        matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonRemove(JsonFunctions.JsonValueContext input, // 私有辅助方法：断言JSON_REMOVE函数的输出
      String[] pathSpecs, Matcher<? super String> matcher) { // 参数：输入上下文、路径规范数组和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.JSON_REMOVE, input, pathSpecs), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonRemove(input, pathSpecs), // 调用jsonRemove函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonStorageSize(String input, // 私有辅助方法：断言JSON_STORAGE_SIZE函数的输出（字符串输入）
      Matcher<? super Integer> matcher) { // 参数：输入字符串和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.JSON_STORAGE_SIZE, input), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonStorageSize(input), // 调用jsonStorageSize函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonStorageSize(JsonFunctions.JsonValueContext input, // 私有辅助方法：断言JSON_STORAGE_SIZE函数的输出（JsonValueContext输入）
      Matcher<? super Integer> matcher) { // 参数：输入上下文和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.JSON_STORAGE_SIZE, input), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonStorageSize(input), // 调用jsonStorageSize函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonStorageSizeFailed(String input, // 私有辅助方法：断言JSON_STORAGE_SIZE函数应该抛出异常
      Matcher<? super Throwable> matcher) { // 参数：输入字符串和期望的异常匹配器
    assertFailed(invocationDesc(BuiltInMethod.JSON_STORAGE_SIZE, input), // 断言：验证函数调用应该失败
        () -> JsonFunctions.jsonStorageSize(input), // 使用lambda表达式调用jsonStorageSize函数
        matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonInsert(JsonFunctions.JsonValueContext jsonDoc, // 私有辅助方法：断言JSON_INSERT函数的输出
      Object[] kvs, // 参数：JSON文档、键值对数组和期望的匹配器
      Matcher<? super String> matcher) {
    assertThat(invocationDesc(BuiltInMethod.JSON_INSERT, jsonDoc, kvs), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonInsert(jsonDoc, kvs), // 调用jsonInsert函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonReplace(JsonFunctions.JsonValueContext jsonDoc, // 私有辅助方法：断言JSON_REPLACE函数的输出
      Object[] kvs, // 参数：JSON文档、键值对数组和期望的匹配器
      Matcher<? super String> matcher) {
    assertThat(invocationDesc(BuiltInMethod.JSON_REPLACE, jsonDoc, kvs), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonReplace(jsonDoc, kvs), // 调用jsonReplace函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonSet(JsonFunctions.JsonValueContext jsonDoc, // 私有辅助方法：断言JSON_SET函数的输出
      Object[] kvs, // 参数：JSON文档、键值对数组和期望的匹配器
      Matcher<? super String> matcher) {
    assertThat(invocationDesc(BuiltInMethod.JSON_SET, jsonDoc, kvs), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.jsonSet(jsonDoc, kvs), // 调用jsonSet函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertDejsonize(String input, // 私有辅助方法：断言DEJSONIZE函数的输出
      Matcher<Object> matcher) { // 参数：输入字符串和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.DEJSONIZE, input), // 断言：验证实际结果与期望结果匹配
        JsonFunctions.dejsonize(input), // 调用dejsonize函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertDejsonizeFailed(String input, // 私有辅助方法：断言DEJSONIZE函数应该抛出异常
      Matcher<? super Throwable> matcher) { // 参数：输入字符串和期望的异常匹配器
    assertFailed(invocationDesc(BuiltInMethod.DEJSONIZE, input), // 断言：验证函数调用应该失败
        () -> JsonFunctions.dejsonize(input), // 使用lambda表达式调用dejsonize函数
        matcher); // 期望的异常匹配器
  } // 方法结束

  private void assertJsonObject(Matcher<? super String> matcher, // 私有辅助方法：断言JSON_OBJECT函数的输出
      SqlJsonConstructorNullClause nullClause, // 参数：期望的匹配器、NULL处理子句和键值对
      Object... kvs) {
    assertThat(invocationDesc(BuiltInMethod.JSON_OBJECT, nullClause, kvs), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.jsonObject(nullClause, kvs), // 调用jsonObject函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonType(Matcher<? super String> matcher, // 私有辅助方法：断言JSON_TYPE函数的输出
      String input) { // 参数：期望的匹配器和输入字符串
    assertThat(invocationDesc(BuiltInMethod.JSON_TYPE, input), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.jsonType(input), // 调用jsonType函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonDepth(Matcher<? super Integer> matcher, // 私有辅助方法：断言JSON_DEPTH函数的输出
      String input) { // 参数：期望的匹配器和输入字符串
    assertThat(invocationDesc(BuiltInMethod.JSON_DEPTH, input), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.jsonDepth(input), // 调用jsonDepth函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonObjectAggAdd(Map map, String k, Object v, // 私有辅助方法：断言JSON_OBJECTAGG_ADD函数的输出
      SqlJsonConstructorNullClause nullClause, // 参数：Map对象、键、值、NULL处理子句和期望的匹配器
      Matcher<? super Map> matcher) {
    JsonFunctions.jsonObjectAggAdd(map, k, v, nullClause); // 调用jsonObjectAggAdd函数向Map添加键值对
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_ARRAYAGG_ADD, map, k, v, nullClause), // 生成调用描述
        map, matcher); // 验证Map内容
  } // 方法结束

  private void assertJsonArray(Matcher<? super String> matcher, // 私有辅助方法：断言JSON_ARRAY函数的输出
      SqlJsonConstructorNullClause nullClause, Object... elements) { // 参数：期望的匹配器、NULL处理子句和元素数组
    assertThat(invocationDesc(BuiltInMethod.JSON_ARRAY, nullClause, elements), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.jsonArray(nullClause, elements), // 调用jsonArray函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertJsonArrayAggAdd(List list, Object element, // 私有辅助方法：断言JSON_ARRAYAGG_ADD函数的输出
      SqlJsonConstructorNullClause nullClause, // 参数：List对象、元素、NULL处理子句和期望的匹配器
      Matcher<? super List> matcher) {
    JsonFunctions.jsonArrayAggAdd(list, element, nullClause); // 调用jsonArrayAggAdd函数向List添加元素
    assertThat( // 断言：验证实际结果与期望结果匹配
        invocationDesc(BuiltInMethod.JSON_ARRAYAGG_ADD, list, element, // 生成调用描述
            nullClause),
        list, matcher); // 验证List内容
  } // 方法结束

  private void assertIsJsonValue(String input, // 私有辅助方法：断言IS_JSON_VALUE函数的输出
      Matcher<? super Boolean> matcher) { // 参数：输入字符串和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.IS_JSON_VALUE, input), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.isJsonValue(input), // 调用isJsonValue函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertIsJsonScalar(String input, // 私有辅助方法：断言IS_JSON_SCALAR函数的输出
      Matcher<? super Boolean> matcher) { // 参数：输入字符串和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.IS_JSON_SCALAR, input), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.isJsonScalar(input), // 调用isJsonScalar函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertIsJsonArray(String input, // 私有辅助方法：断言IS_JSON_ARRAY函数的输出
      Matcher<? super Boolean> matcher) { // 参数：输入字符串和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.IS_JSON_ARRAY, input), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.isJsonArray(input), // 调用isJsonArray函数
        matcher); // 期望的匹配器
  } // 方法结束

  private void assertIsJsonObject(String input, // 私有辅助方法：断言IS_JSON_OBJECT函数的输出
      Matcher<? super Boolean> matcher) { // 参数：输入字符串和期望的匹配器
    assertThat(invocationDesc(BuiltInMethod.IS_JSON_OBJECT, input), // 断言：验证实际结果与期望结果匹配
        SqlFunctions.isJsonObject(input), // 调用isJsonObject函数
        matcher); // 期望的匹配器
  } // 方法结束

  private static String invocationDesc(BuiltInMethod method, Object... args) { // 私有静态辅助方法：生成函数调用描述字符串
    return Arrays.stream(args) // 将参数数组转换为流
        .map(Objects::toString) // 将每个参数转换为字符串
        .collect(Collectors.joining(", ", method.getMethodName() + "(", ")")); // 用逗号连接参数，包装在方法名和括号中
  } // 方法结束

  private void assertFailed(String invocationDesc, Supplier<?> supplier, // 私有辅助方法：断言函数调用应该失败并抛出异常
      Matcher<? super Throwable> matcher) { // 参数：调用描述、函数提供者和期望的异常匹配器
    try { // 尝试执行函数
      supplier.get(); // 调用函数
      fail("expect exception, but not: " + invocationDesc); // 如果没有抛出异常，测试失败
    } catch (Throwable t) { // 捕获异常
      assertThat(invocationDesc, t, matcher); // 断言异常与期望匹配
    } // 结束try-catch
  } // 方法结束

  private Matcher<? super Throwable> errorMatches(Throwable expected) { // 私有辅助方法：创建异常匹配器，用于验证异常类型和消息
    return new BaseMatcher<Throwable>() { // 返回自定义的BaseMatcher实现
      @Override public boolean matches(Object item) { // 匹配方法：验证对象是否匹配期望的异常
        if (!(item instanceof Throwable)) { // 如果对象不是Throwable类型
          return false; // 返回不匹配
        } // 结束if
        Throwable error = (Throwable) item; // 强制转换为Throwable
        return expected != null // 验证期望异常不为null
            && Objects.equals(error.getClass(), expected.getClass()) // 验证异常类型相同
            && Objects.equals(error.getMessage(), expected.getMessage()); // 验证异常消息相同
      } // 匹配方法结束

      @Override public void describeTo(Description description) { // 描述方法：生成期望值的描述
        description.appendText("is ").appendText(expected.toString()); // 添加描述文本
      } // 描述方法结束
    }; // 匿名内部类结束
  } // 方法结束

  private BaseMatcher<JsonFunctions.JsonPathContext> contextMatches( // 私有辅助方法：创建JsonPathContext匹配器
      JsonFunctions.JsonPathContext expected) { // 参数：期望的JsonPathContext对象
    return new BaseMatcher<JsonFunctions.JsonPathContext>() { // 返回自定义的BaseMatcher实现
      @Override public boolean matches(Object item) { // 匹配方法：验证对象是否匹配期望的JsonPathContext
        if (!(item instanceof JsonFunctions.JsonPathContext)) { // 如果对象不是JsonPathContext类型
          return false; // 返回不匹配
        } // 结束if
        JsonFunctions.JsonPathContext context = (JsonFunctions.JsonPathContext) item; // 强制转换为JsonPathContext
        if (Objects.equals(context.mode, expected.mode) // 验证模式相同
            && Objects.equals(context.obj, expected.obj)) { // 验证对象相同
          if (context.exc == null && expected.exc == null) { // 如果两个异常都为null
            return true; // 返回匹配
          } // 结束if
          return context.exc != null && expected.exc != null // 验证两个异常都不为null
              && Objects.equals(context.exc.getClass(), expected.exc.getClass()) // 验证异常类型相同
              && Objects.equals(context.exc.getMessage(), expected.exc.getMessage()); // 验证异常消息相同
        } // 结束if
        return false; // 返回不匹配
      } // 匹配方法结束

      @Override public void describeTo(Description description) { // 描述方法：生成期望值的描述
        description.appendText("is ").appendText(expected.toString()); // 添加描述文本
      } // 描述方法结束
    }; // 匿名内部类结束
  } // 方法结束
} // 类结束