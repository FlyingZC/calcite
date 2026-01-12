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
// Apache许可证头，声明代码版权和使用许可
package org.apache.calcite.test; // 包声明，该类属于org.apache.calcite.test包

import org.apache.calcite.sql.test.SqlOperatorFixture; // 导入SqlOperatorFixture类，用于SQL操作符测试
import org.apache.calcite.util.DelegatingInvocationHandler; // 导入DelegatingInvocationHandler类，用于动态代理处理

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可空参数

import java.lang.reflect.Proxy; // 导入Proxy类，用于创建动态代理对象
import java.util.regex.Pattern; // 导入Pattern类，用于正则表达式匹配和替换

/** Utilities for {@link SqlOperatorFixture}. */ // 类文档注释：SqlOperatorFixture的工具类
class SqlOperatorFixtures { // SqlOperatorFixtures类定义，提供SqlOperatorFixture的实用工具方法
  private SqlOperatorFixtures() { // 私有构造函数，防止实例化
  } // 构造函数体为空，这是一个工具类，不需要实例化

  /** Returns a fixture that converts each CAST test into a test for
   * SAFE_CAST or TRY_CAST. */ // 方法文档：返回一个将CAST测试转换为SAFE_CAST或TRY_CAST测试的fixture
  static SqlOperatorFixture safeCastWrapper(SqlOperatorFixture fixture, String functionName) { // 静态方法，创建一个包装器，将CAST调用转换为指定的安全转换函数
    return (SqlOperatorFixture) Proxy.newProxyInstance( // 使用Java动态代理创建代理对象
        SqlOperatorTest.class.getClassLoader(), // 使用SqlOperatorTest类的类加载器
        new Class[]{SqlOperatorFixture.class}, // 代理实现的接口数组，这里只实现SqlOperatorFixture接口
        new SqlOperatorFixtureInvocationHandler(fixture, functionName)); // 创建调用处理器，处理方法调用
  } // 方法结束，返回代理对象

  /** A helper for {@link #safeCastWrapper(SqlOperatorFixture, String)} that provides
   * alternative implementations of methods in {@link SqlOperatorFixture}.
   *
   * <p>Must be public, so that its methods can be seen via reflection. */ // 内部类文档：safeCastWrapper的辅助类，提供SqlOperatorFixture方法的替代实现
  @SuppressWarnings("unused") // 抑制未使用警告，因为方法是通过反射调用的
  public static class SqlOperatorFixtureInvocationHandler // SqlOperatorFixtureInvocationHandler内部类定义，继承DelegatingInvocationHandler
      extends DelegatingInvocationHandler { // 继承DelegatingInvocationHandler，实现动态代理的调用处理
    static final Pattern CAST_PATTERN = Pattern.compile("(?i)\\bCAST\\("); // 静态常量，正则表达式模式，用于匹配CAST函数调用，(?i)表示忽略大小写
    static final Pattern NOT_NULL_PATTERN = Pattern.compile(" NOT NULL"); // 静态常量，正则表达式模式，用于匹配NOT NULL约束

    final SqlOperatorFixture f; // final成员变量，被代理的原始SqlOperatorFixture对象
    final String functionName; // final成员变量，要替换CAST的目标函数名，如SAFE_CAST或TRY_CAST

    SqlOperatorFixtureInvocationHandler(SqlOperatorFixture f, String functionName) { // 构造函数，初始化调用处理器
      this.f = f; // 将传入的fixture对象保存到成员变量f中
      this.functionName = functionName; // 将传入的函数名保存到成员变量functionName中
    } // 构造函数结束

    @Override protected Object getTarget() { // 重写父类方法，返回被代理的目标对象
      return f; // 返回原始的SqlOperatorFixture对象
    } // 方法结束

    String addSafe(String sql) { // 私有方法，将SQL中的CAST替换为指定的安全转换函数
      return CAST_PATTERN.matcher(sql).replaceAll(functionName + "("); // 使用正则表达式匹配CAST(并替换为functionName(
    } // 方法结束，返回替换后的SQL字符串

    String removeNotNull(String type) { // 私有方法，从类型字符串中移除NOT NULL约束
      return NOT_NULL_PATTERN.matcher(type).replaceAll(""); // 使用正则表达式匹配NOT NULL并替换为空字符串
    } // 方法结束，返回移除NOT NULL后的类型字符串

    /** Proxy for
     * {@link SqlOperatorFixture#checkCastToString(String, String, String, SqlOperatorFixture.CastType)}. */ // 方法文档：代理checkCastToString方法
    public void checkCastToString(String value, @Nullable String type, // 公共方法，代理checkCastToString，用于检查类型转换为字符串的结果
        @Nullable String expected, SqlOperatorFixture.CastType castType) { // 参数：值、类型、期望结果、转换类型
      f.checkCastToString(addSafe(value), // 调用原始fixture的checkCastToString方法，对value进行CAST替换
          type == null ? null : removeNotNull(type), expected, castType); // 如果type不为null，则移除NOT NULL约束
    } // 方法结束

    /** Proxy for {@link SqlOperatorFixture#checkBoolean(String, Boolean)}. */ // 方法文档：代理checkBoolean方法（原文注释可能有误，实际代理checkFails）
    public void checkFails(String expression, @Nullable Boolean result) { // 公共方法，代理checkFails，检查表达式是否失败
      f.checkBoolean(addSafe(expression), result); // 调用原始fixture的checkBoolean方法，对表达式进行CAST替换
    } // 方法结束

    /** Proxy for {@link SqlOperatorFixture#checkNull(String)}. */ // 方法文档：代理checkNull方法
    public void checkNull(String expression) { // 公共方法，代理checkNull，检查表达式是否返回NULL
      f.checkNull(addSafe(expression)); // 调用原始fixture的checkNull方法，对表达式进行CAST替换
    } // 方法结束

    /** Proxy for
     * {@link SqlOperatorFixture#checkFails(String, String, boolean)}. */ // 方法文档：代理checkFails方法（检查表达式是否失败）
    public void checkFails(String expression, String expectedError, boolean runtime) { // 公共方法，代理checkFails，检查表达式是否失败并验证错误信息
      f.checkFails(addSafe(expression), expectedError, runtime); // 调用原始fixture的checkFails方法，对表达式进行CAST替换
    } // 方法结束

    /** Proxy for
     * {@link SqlOperatorFixture#checkScalar(String, Object, String)}. */ // 方法文档：代理checkScalar方法
    public void checkScalar(String expression, Object result, String resultType) { // 公共方法，代理checkScalar，检查标量表达式的结果
      f.checkScalar(addSafe(expression), result, removeNotNull(resultType)); // 调用原始fixture的checkScalar方法，对表达式进行CAST替换，移除结果类型的NOT NULL
    } // 方法结束

    /** Proxy for {@link SqlOperatorFixture#checkScalarExact(String, int)}. */ // 方法文档：代理checkScalarExact方法
    public void checkScalarExact(String expression, int result) { // 公共方法，代理checkScalarExact，检查标量表达式的精确整型结果
      f.checkScalarExact(addSafe(expression), result); // 调用原始fixture的checkScalarExact方法，对表达式进行CAST替换
    } // 方法结束

    /** Proxy for
     * {@link SqlOperatorFixture#checkScalarExact(String, String, String)}. */ // 方法文档：代理checkScalarExact方法（重载版本）
    public void checkScalarExact(String expression, String expectedType, String result) { // 公共方法，代理checkScalarExact，检查标量表达式的精确结果和类型
      f.checkScalarExact(addSafe(expression), removeNotNull(expectedType), result); // 调用原始fixture的checkScalarExact方法，对表达式进行CAST替换，移除期望类型的NOT NULL
    } // 方法结束

    /** Proxy for
     * {@link SqlOperatorFixture#checkString(String, String, String)}. */ // 方法文档：代理checkString方法
    public void checkString(String expression, String result, String resultType) { // 公共方法，代理checkString，检查字符串表达式的结果
      f.checkString(addSafe(expression), result, removeNotNull(resultType)); // 调用原始fixture的checkString方法，对表达式进行CAST替换，移除结果类型的NOT NULL
    } // 方法结束
  } // SqlOperatorFixtureInvocationHandler内部类结束
} // SqlOperatorFixtures类结束
