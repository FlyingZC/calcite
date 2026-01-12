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
package org.apache.calcite.linq4j.test;

import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.BlockStatement;
import org.apache.calcite.linq4j.tree.Blocks;
import org.apache.calcite.linq4j.tree.ClassDeclaration;
import org.apache.calcite.linq4j.tree.DeclarationStatement;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.FieldDeclaration;
import org.apache.calcite.linq4j.tree.FunctionExpression;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.apache.calcite.linq4j.tree.NewExpression;
import org.apache.calcite.linq4j.tree.Node;
import org.apache.calcite.linq4j.tree.ParameterExpression;
import org.apache.calcite.linq4j.tree.Shuttle;
import org.apache.calcite.linq4j.tree.Types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.apache.calcite.linq4j.test.BlockBuilderBase.ONE;
import static org.apache.calcite.linq4j.test.BlockBuilderBase.TWO;
import static org.apache.calcite.linq4j.test.util.RecordHelper.createInstance;
import static org.apache.calcite.linq4j.test.util.RecordHelper.createRecordClass;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;

/**
 * Unit test for {@link org.apache.calcite.linq4j.tree.Expression}
 * and subclasses.
 * // 单元测试类,用于测试org.apache.calcite.linq4j.tree.Expression及其子类的功能
 * // 这个类全面测试了Calcite的LINQ4J表达式树(Expression Tree)系统
 * // 表达式树是Calcite用于动态生成Java代码的核心机制,允许在运行时构建、编译和执行表达式
 * // 主要测试内容包括:
 * // 1. Lambda表达式测试 - 测试各种类型的lambda表达式创建、编译和执行
 * // 2. 二元运算测试 - 测试不同数据类型的算术运算(+,-,*,/)
 * // 3. 表达式字符串化测试 - 验证表达式树能正确转换为Java源代码字符串
 * // 4. 常量表达式测试 - 测试各种类型常量的表达式表示
 * // 5. 控制流表达式测试 - 测试if、while、for、try-catch等控制流语句的表达式表示
 * // 6. 类型系统测试 - 验证表达式类型推断的正确性
 * // 7. 表达式编译测试 - 测试表达式树能否正确编译为可执行代码
 * // 8. 代码块构建器测试 - 测试BlockBuilder用于优化和构建代码块的功能
 * // 9. 集合字面量测试 - 测试List、Set、Map等集合类型的常量表达式
 * // 10. 子表达式消除测试 - 验证公共子表达式消除优化
 * // 这些测试确保Calcite能够正确地将SQL查询转换为可执行的Java代码
 */
public class ExpressionTest {

  @Test void testLambdaCallsBinaryOpInt() {
    // A parameter for the lambda expression.
    // 创建一个参数表达式,类型为int,参数名为"arg"
    // ParameterExpression是表达式树中表示参数的节点
    ParameterExpression paramExpr =
        Expressions.parameter(Integer.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds 1 to the parameter value.
    // 创建一个lambda表达式,该表达式接收一个int参数,返回参数值加2的结果
    // Expressions.lambda()方法用于创建函数表达式
    // Expressions.add()创建一个加法表达式,将参数和常量2相加
    // Arrays.asList(paramExpr)指定lambda表达式的参数列表
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(2)),
            Arrays.asList(paramExpr));

    // Print out the expression.
    // 将表达式树转换为Java源代码字符串
    // Expressions.toString()方法会将表达式树序列化为可读的Java代码
    // 这对于调试和代码生成非常有用
    String s = Expressions.toString(lambdaExpr);
    // 验证生成的代码字符串是否符合预期
    // 生成的代码是一个实现Function1接口的匿名类
    // Function1是Calcite提供的函数式接口,表示接受一个参数的函数
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public int apply(int arg) {\n"
            + "    return arg + 2;\n"
            + "  }\n"
            + "  public Object apply(Integer arg) {\n"
            + "    return apply(\n"
            + "      arg.intValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Integer) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 1
    // 编译lambda表达式并动态调用它
    // compile()方法将表达式树编译为可执行的Java代码
    // dynamicInvoke()方法动态调用编译后的函数,传入参数1
    // 这是Calcite实现动态代码执行的关键机制
    Integer n = (Integer) lambdaExpr.compile().dynamicInvoke(1);

    // This code example produces the following output:
    //
    // arg -> (arg +2)
    // 3
    // 验证结果不为null且等于3
    // 这证明了表达式树能够正确编译和执行
    assertThat(n, notNullValue());
    assertThat(n, is(3));
  }

  @Test void testLambdaCallsBinaryOpShort() {
    // A parameter for the lambda expression.
    // 创建一个short类型的参数表达式
    // Short.TYPE表示基本类型short,而不是包装类Short
    ParameterExpression paramExpr =
        Expressions.parameter(Short.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds 1 to the parameter value.
    // 定义short类型的常量2
    Short a = 2;
    // 创建lambda表达式,对short参数执行加法运算
    // 注意:short类型的运算会自动提升为int类型
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(a)),
            Arrays.asList(paramExpr));

    // Print out the expression.
    // 将表达式树转换为字符串并验证
    // 生成的代码中,short类型的常量需要显式类型转换:(short)2
    // 返回类型为int,因为short运算会提升为int
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public int apply(short arg) {\n"
            + "    return arg + (short)2;\n"
            + "  }\n"
            + "  public Object apply(Short arg) {\n"
            + "    return apply(\n"
            + "      arg.shortValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Short) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 1.
    // 定义输入参数值为1
    Short b = 1;
    // 编译并执行lambda表达式,传入short值1
    // 结果应该是int类型的3
    Integer n = (Integer) lambdaExpr.compile().dynamicInvoke(b);

    // This code example produces the following output:
    //
    // arg -> (arg +2)
    // 3
    // 验证结果正确
    assertThat(n, notNullValue());
    assertThat(n, is(3));
  }

  @Test void testLambdaCallsBinaryOpByte() {
    // A parameter for the lambda expression.
    // 创建一个byte类型的参数表达式
    // Byte.TYPE表示基本类型byte
    ParameterExpression paramExpr =
        Expressions.parameter(Byte.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds 1 to the parameter value.
    // 创建lambda表达式,对byte参数执行加法运算
    // 使用Byte.valueOf("2")创建Byte包装类对象
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(Byte.valueOf("2"))),
            Arrays.asList(paramExpr));

    // Print out the expression.
    // 将表达式树转换为字符串并验证
    // byte类型的常量需要显式类型转换:(byte)2
    // 返回类型为int,因为byte运算会提升为int
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public int apply(byte arg) {\n"
            + "    return arg + (byte)2;\n"
            + "  }\n"
            + "  public Object apply(Byte arg) {\n"
            + "    return apply(\n"
            + "      arg.byteValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Byte) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 1.
    // 编译并执行lambda表达式,传入byte值1
    // Byte.valueOf("1")将字符串"1"转换为Byte对象
    Integer n = (Integer) lambdaExpr.compile().dynamicInvoke(Byte.valueOf("1"));

    // This code example produces the following output:
    //
    // arg -> (arg +2)
    // 3
    // 验证结果正确
    assertThat(n, notNullValue());
    assertThat(n, is(3));
  }

  @Test void testLambdaCallsBinaryOpDouble() {
    // A parameter for the lambda expression.
    // 创建一个double类型的参数表达式
    // Double.TYPE表示基本类型double
    ParameterExpression paramExpr =
        Expressions.parameter(Double.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds 1 to the parameter value.
    // 创建lambda表达式,对double参数执行加法运算
    // 2d表示double字面量2.0
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(2d)),
            Arrays.asList(paramExpr));

    // Print out the expression.
    // 将表达式树转换为字符串并验证
    // double类型的常量使用D后缀表示:2.0D
    // 返回类型为double,保持浮点类型
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public double apply(double arg) {\n"
            + "    return arg + 2.0D;\n"
            + "  }\n"
            + "  public Object apply(Double arg) {\n"
            + "    return apply(\n"
            + "      arg.doubleValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Double) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 1.5.
    // 编译并执行lambda表达式,传入double值1.5
    // 1.5d表示double字面量1.5
    Double n = (Double) lambdaExpr.compile().dynamicInvoke(1.5d);

    // This code example produces the following output:
    //
    // arg -> (arg +2)
    // 3.5
    // 验证结果正确:1.5 + 2.0 = 3.5
    assertThat(n, notNullValue());
    assertThat(n, is(3.5D));
  }

  @Test void testLambdaCallsBinaryOpLong() {
    // A parameter for the lambda expression.
    // 创建一个long类型的参数表达式
    // Long.TYPE表示基本类型long
    ParameterExpression paramExpr =
        Expressions.parameter(Long.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds 1L to the parameter value.
    // 创建lambda表达式,对long参数执行加法运算
    // 2L表示long字面量2
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(2L)),
            Arrays.asList(paramExpr));
    // Print out the expression.
    // 将表达式树转换为字符串并验证
    // long类型的常量使用L后缀表示:2L
    // 返回类型为long,保持长整型
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public long apply(long arg) {\n"
            + "    return arg + 2L;\n"
            + "  }\n"
            + "  public Object apply(Long arg) {\n"
            + "    return apply(\n"
            + "      arg.longValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Long) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 1L.
    // 编译并执行lambda表达式,传入long值1
    Long n = (Long) lambdaExpr.compile().dynamicInvoke(1L);

    // This code example produces the following output:
    //
    // arg -> (arg +2)
    // 3
    // 验证结果正确:1L + 2L = 3L
    assertThat(n, notNullValue());
    assertThat(n, is(3L));
  }

  @Test void testLambdaCallsBinaryOpFloat() {
    // A parameter for the lambda expression.
    // 创建一个float类型的参数表达式
    // Float.TYPE表示基本类型float
    ParameterExpression paramExpr =
        Expressions.parameter(Float.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds 1f to the parameter value.
    // 创建lambda表达式,对float参数执行加法运算
    // 2.0f表示float字面量2.0
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(2.0f)),
            Arrays.asList(paramExpr));
    // Print out the expression.
    // 将表达式树转换为字符串并验证
    // float类型的常量使用F后缀表示:2.0F
    // 返回类型为float,保持单精度浮点类型
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public float apply(float arg) {\n"
            + "    return arg + 2.0F;\n"
            + "  }\n"
            + "  public Object apply(Float arg) {\n"
            + "    return apply(\n"
            + "      arg.floatValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Float) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 1f
    // 编译并执行lambda表达式,传入float值1.0
    Float n = (Float) lambdaExpr.compile().dynamicInvoke(1f);

    // This code example produces the following output:
    //
    // arg -> (arg +2)
    // 3.0
    // 验证结果正确:1.0f + 2.0f = 3.0f
    assertThat(n, notNullValue());
    assertThat(n, is(3f));
  }

  @Test void testLambdaCallsBinaryOpMixType() {
    // A parameter for the lambda expression.
    // 创建一个long类型的参数表达式
    ParameterExpression paramExpr =
        Expressions.parameter(Long.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds (int)10 to the parameter value.
    // 创建lambda表达式,对long参数执行加法运算
    // 这里测试混合类型运算:long参数 + int常量10
    // 在Java中,int和long相加时,int会自动提升为long
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(10)),
            Arrays.asList(paramExpr));
    // Print out the expression.
    // 将表达式树转换为字符串并验证
    // int常量10在long运算中会被自动提升为long类型
    // 返回类型为long
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public long apply(long arg) {\n"
            + "    return arg + 10;\n"
            + "  }\n"
            + "  public Object apply(Long arg) {\n"
            + "    return apply(\n"
            + "      arg.longValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Long) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 5L.
    // 编译并执行lambda表达式,传入long值5
    Long n = (Long) lambdaExpr.compile().dynamicInvoke(5L);

    // This code example produces the following output:
    //
    // arg -> (arg +10)
    // 15
    // 验证结果正确:5L + 10 = 15L
    assertThat(n, notNullValue());
    assertThat(n, is(15L));
  }

  @Test void testLambdaCallsBinaryOpMixDoubleType() {
    // A parameter for the lambda expression.
    // 创建一个double类型的参数表达式
    ParameterExpression paramExpr =
        Expressions.parameter(Double.TYPE, "arg");

    // This expression represents a lambda expression
    // that adds 10.1d to the parameter value.
    // 创建lambda表达式,对double参数执行加法运算
    // 这里测试混合浮点类型运算
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.add(paramExpr, Expressions.constant(10.1d)),
            Arrays.asList(paramExpr));
    // Print out the expression.
    // 将表达式树转换为字符串并验证
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public double apply(double arg) {\n"
            + "    return arg + 10.1D;\n"
            + "  }\n"
            + "  public Object apply(Double arg) {\n"
            + "    return apply(\n"
            + "      arg.doubleValue());\n"
            + "  }\n"
            + "  public Object apply(Object arg) {\n"
            + "    return apply(\n"
            + "      (Double) arg);\n"
            + "  }\n"
            + "}\n"));

    // Compile and run the lambda expression.
    // The value of the parameter is 5.0f.
    // 编译并执行lambda表达式,传入float值5.0
    // 注意:传入的是float类型,但参数期望double类型
    // float会自动提升为double
    Double n = (Double) lambdaExpr.compile().dynamicInvoke(5.0f);

    // This code example produces the following output:
    //
    // arg -> (arg +10.1d)
    // 15.1d
    // 验证结果正确:5.0 + 10.1 = 15.1
    assertThat(n, notNullValue());
    assertThat(n, is(15.1d));
  }

  @Test void testLambdaPrimitiveTwoArgs() {
    // Parameters for the lambda expression.
    // 创建两个int类型的参数表达式
    // 这个测试验证了多参数lambda表达式的支持
    ParameterExpression paramExpr =
        Expressions.parameter(int.class, "key");
    ParameterExpression param2Expr =
        Expressions.parameter(int.class, "key2");

    // 创建一个接受两个参数的lambda表达式
    // Expressions.block()创建一个代码块表达式
    // Expressions.return_()创建一个return语句
    // 这个lambda表达式只返回第一个参数,忽略第二个参数
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.block((Type) null,
                Expressions.return_(null, paramExpr)),
            Arrays.asList(paramExpr, param2Expr));

    // Print out the expression.
    // 将表达式树转换为字符串并验证
    // 生成的代码实现Function2接口,表示接受两个参数的函数
    // Function2会生成三个重载方法:
    // 1. apply(int key, int key2) - 基本类型版本
    // 2. apply(Integer key, Integer key2) - 包装类型版本
    // 3. apply(Object key, Object key2) - Object类型版本
    String s = Expressions.toString(lambdaExpr);
    assertThat(s,
        is("new org.apache.calcite.linq4j.function.Function2() {\n"
            + "  public int apply(int key, int key2) {\n"
            + "    return key;\n"
            + "  }\n"
            + "  public Integer apply(Integer key, Integer key2) {\n"
            + "    return apply(\n"
            + "      key.intValue(),\n"
            + "      key2.intValue());\n"
            + "  }\n"
            + "  public Integer apply(Object key, Object key2) {\n"
            + "    return apply(\n"
            + "      (Integer) key,\n"
            + "      (Integer) key2);\n"
            + "  }\n"
            + "}\n"));
  }

  @Test void testLambdaCallsTwoArgMethod() throws NoSuchMethodException {
    // A parameter for the lambda expression.
    // 创建三个参数表达式:
    // 1. String类型的参数s,表示要截取的字符串
    // 2. int类型的参数begin,表示开始索引
    // 3. int类型的参数end,表示结束索引
    ParameterExpression paramS =
        Expressions.parameter(String.class, "s");
    ParameterExpression paramBegin =
        Expressions.parameter(Integer.TYPE, "begin");
    ParameterExpression paramEnd =
        Expressions.parameter(Integer.TYPE, "end");

    // This expression represents a lambda expression
    // that adds 1 to the parameter value.
    // 创建lambda表达式,调用String.substring(int beginIndex, int endIndex)方法
    // Expressions.call()创建方法调用表达式
    // 第一个参数paramS是方法调用的目标对象
    // String.class.getMethod()通过反射获取substring方法
    // paramBegin和paramEnd是方法调用的参数
    // 最后三个参数指定lambda表达式的参数列表
    FunctionExpression lambdaExpr =
        Expressions.lambda(
            Expressions.call(
                paramS,
                String.class.getMethod(
                    "substring", Integer.TYPE, Integer.TYPE),
                paramBegin,
                paramEnd), paramS, paramBegin, paramEnd);

    // Compile and run the lambda expression.
    // 编译并执行lambda表达式
    // 传入参数:"hello world", 3, 7
    // substring(3, 7)会返回"lo w"(从索引3开始,到索引7结束,不包括索引7)
    String s =
        (String) lambdaExpr.compile().dynamicInvoke("hello world", 3, 7);

    // 验证结果正确
    assertThat(s, is("lo w"));
  }

  @Test void testFoldAnd() {
    // empty list yields true
    // 测试空列表的foldAnd和foldOr操作
    // 空列表的AND操作返回true(AND的幺元)
    // 空列表的OR操作返回false(OR的幺元)
    final List<Expression> list0 = Collections.emptyList();
    assertThat(
        Expressions.toString(
            Expressions.foldAnd(list0)),
        is("true"));
    assertThat(
        Expressions.toString(
            Expressions.foldOr(list0)),
        is("false"));

    // 创建包含多个表达式的列表
    // 包含两个相等比较和一个true常量
    final List<Expression> list1 =
        Arrays.asList(
            Expressions.equal(Expressions.constant(1), Expressions.constant(2)),
            Expressions.equal(Expressions.constant(3), Expressions.constant(4)),
            Expressions.constant(true),
            Expressions.equal(Expressions.constant(5),
                Expressions.constant(6)));
    // true is eliminated from AND
    // foldAnd会优化表达式,消除AND操作中的true常量
    // 因为 x && true 等价于 x
    assertThat(
        Expressions.toString(
            Expressions.foldAnd(list1)),
        is("1 == 2 && 3 == 4 && 5 == 6"));
    // a single true makes OR true
    // foldOr会优化表达式,如果OR操作中有true,整个表达式就是true
    // 因为 x || true 等价于 true
    assertThat(
        Expressions.toString(
            Expressions.foldOr(list1)),
        is("true"));

    // 创建只包含true的列表
    final List<Expression> list2 =
        Collections.singletonList(
            Expressions.constant(true));
    // 单个true的AND和OR都是true
    assertThat(
        Expressions.toString(
            Expressions.foldAnd(list2)),
        is("true"));
    assertThat(
        Expressions.toString(
            Expressions.foldOr(list2)),
        is("true"));

    // 创建包含false的列表
    final List<Expression> list3 =
        Arrays.asList(
            Expressions.equal(Expressions.constant(1), Expressions.constant(2)),
            Expressions.constant(false),
            Expressions.equal(Expressions.constant(5),
                Expressions.constant(6)));
    // false causes whole list to be false
    // foldAnd会优化表达式,如果AND操作中有false,整个表达式就是false
    // 因为 x && false 等价于 false
    assertThat(
        Expressions.toString(
            Expressions.foldAnd(list3)),
        is("false"));
    // foldOr会优化表达式,消除OR操作中的false常量
    // 因为 x || false 等价于 x
    assertThat(
        Expressions.toString(
            Expressions.foldOr(list3)),
        is("1 == 2 || 5 == 6"));
  }

  @Test void testWrite() {
    // 测试混合类型的加法表达式字符串化
    // 1(int) + 2.0F(float) + 3L(long) + 4L(Long包装类)
    // 注意:Long类型的包装类会使用Long.valueOf()包装
    assertThat(
        Expressions.toString(
            Expressions.add(
                Expressions.add(
                    Expressions.add(
                        Expressions.constant(1),
                        Expressions.constant(2F, Float.TYPE)),
                    Expressions.constant(3L, Long.TYPE)),
                Expressions.constant(4L, Long.class))),
        is("1 + 2.0F + 3L + Long.valueOf(4L)"));

    // 测试BigDecimal常量的字符串化
    // BigDecimal.valueOf(314159260, 8)表示3.14159260
    // 输出时会优化为BigDecimal.valueOf(31415926L, 7),表示相同的值
    assertThat(
        Expressions.toString(
            Expressions.constant(
                BigDecimal.valueOf(314159260, 8))),
        is("java.math.BigDecimal.valueOf(31415926L, 7)"));

    // Parentheses needed, to override the left-associativity of +.
    // 测试括号的使用
    // 加法运算是左结合的,1 + (2 + 3)需要括号来改变结合顺序
    assertThat(
        Expressions.toString(
            Expressions.add(
                Expressions.constant(1),
                Expressions.add(
                    Expressions.constant(2),
                    Expressions.constant(3)))),
        is("1 + (2 + 3)"));

    // No parentheses needed; higher precedence of * achieves the desired
    // effect.
    // 测试运算符优先级
    // 乘法优先级高于加法,所以1 + 2 * 3不需要括号
    assertThat(
        Expressions.toString(
            Expressions.add(
                Expressions.constant(1),
                Expressions.multiply(
                    Expressions.constant(2),
                    Expressions.constant(3)))),
        is("1 + 2 * 3"));

    // 测试乘法和加法的组合
    // 1 * (2 + 3)需要括号,因为乘法优先级高于加法
    assertThat(
        Expressions.toString(
            Expressions.multiply(
                Expressions.constant(1),
                Expressions.add(
                    Expressions.constant(2),
                    Expressions.constant(3)))),
        is("1 * (2 + 3)"));

    // Parentheses needed, to overcome right-associativity of =.
    // 测试赋值运算符的右结合性
    // 赋值运算是右结合的,(1 = 2) = 3需要括号
    assertThat(
        Expressions.toString(
            Expressions.assign(
                Expressions.assign(
                    Expressions.constant(1), Expressions.constant(2)),
                Expressions.constant(3))),
        is("(1 = 2) = 3"));

    // Ternary operator.
    // 测试嵌套的三元运算符
    // 1 < 2 ? (3 < 4 ? 5 : 6) : 7 < 8 ? 9 : 10
    // 内层的三元运算符需要括号
    assertThat(
        Expressions.toString(
            Expressions.condition(
                Expressions.lessThan(
                    Expressions.constant(1),
                    Expressions.constant(2)),
                Expressions.condition(
                    Expressions.lessThan(
                        Expressions.constant(3),
                        Expressions.constant(4)),
                    Expressions.constant(5),
                    Expressions.constant(6)),
                Expressions.condition(
                    Expressions.lessThan(
                        Expressions.constant(7),
                        Expressions.constant(8)),
                    Expressions.constant(9),
                    Expressions.constant(10)))),
        is("1 < 2 ? (3 < 4 ? 5 : 6) : 7 < 8 ? 9 : 10"));

    // 测试类型转换表达式
    // 0 + (double) (2 + 3)
    // 类型转换需要括号
    assertThat(
        Expressions.toString(
            Expressions.add(
                Expressions.constant(0),
                Expressions.convert_(
                    Expressions.add(
                        Expressions.constant(2), Expressions.constant(3)),
                    Double.TYPE))),
        is("0 + (double) (2 + 3)"));

    // "--5" would be a syntax error
    // 测试双重取反
    // Java中--5是语法错误,所以需要括号: (- (- 5))
    assertThat(
        Expressions.toString(
            Expressions.negate(
                Expressions.negate(
                    Expressions.constant(5)))),
        is("(- (- 5))"));

    // 测试字段访问表达式
    // 访问Employee对象的empno字段
    assertThat(
        Expressions.toString(
            Expressions.field(
                Expressions.parameter(Linq4jTest.Employee.class, "a"),
                "empno")),
        is("a.empno"));

    // 测试数组长度字段访问
    // 访问数组的length字段
    assertThat(
        Expressions.toString(
            Expressions.field(
                Expressions.parameter(Object[].class, "a"),
                "length")),
        is("a.length"));

    // 测试静态字段访问
    // 访问Collections类的EMPTY_LIST静态字段
    assertThat(
        Expressions.toString(
            Expressions.field(
                null, Collections.class, "EMPTY_LIST")),
        is("java.util.Collections.EMPTY_LIST"));

    final ParameterExpression paramX =
        Expressions.parameter(String.class, "x");
    assertThat(
        Expressions.toString(
            Expressions.lambda(
                Function1.class,
                Expressions.call(
                    paramX, "length", Collections.emptyList()),
                Arrays.asList(paramX))),
        is("new org.apache.calcite.linq4j.function.Function1() {\n"
            + "  public int apply(String x) {\n"
            + "    return x.length();\n"
            + "  }\n"
            + "  public Object apply(Object x) {\n"
            + "    return apply(\n"
            + "      (String) x);\n"
            + "  }\n"
            + "}\n"));

    // 1-dimensional array with initializer
    assertThat(
        Expressions.toString(
            Expressions.newArrayInit(
                String.class,
                Expressions.constant("foo"),
                Expressions.constant(null),
                Expressions.constant("bar\"baz"))),
        is("new String[] {\n"
            + "  \"foo\",\n"
            + "  null,\n"
            + "  \"bar\\\"baz\"}"));

    // 2-dimensional array with initializer
    assertThat(
        Expressions.toString(
            Expressions.newArrayInit(
                String.class,
                2,
                Expressions.constant(new String[] {"foo", "bar"}),
                Expressions.constant(null),
                Expressions.constant(new String[] {null}))),
        is("new String[][] {\n"
            + "  new String[] {\n"
            + "    \"foo\",\n"
            + "    \"bar\"},\n"
            + "  null,\n"
            + "  new String[] {\n"
            + "    null}}"));

    // 1-dimensional array
    assertThat(
        Expressions.toString(
            Expressions.newArrayBounds(
                String.class,
                1,
                Expressions.add(
                    Expressions.parameter(0, int.class, "x"),
                    Expressions.constant(1)))),
        is("new String[x + 1]"));

    // 3-dimensional array
    assertThat(
        Expressions.toString(
            Expressions.newArrayBounds(
                String.class,
                3,
                Expressions.add(
                    Expressions.parameter(0, int.class, "x"),
                    Expressions.constant(1)))),
        is("new String[x + 1][][]"));

    assertThat(
        Expressions.toString(
            Expressions.convert_(
                Expressions.call(
                    Expressions.convert_(
                        Expressions.convert_(
                            Expressions.constant("foo"),
                            Object.class),
                        String.class),
                    "length",
                    Collections.emptyList()),
                Integer.TYPE)),
        is("(int) ((String) (Object) \"foo\").length()"));

    // resolving a static method
    assertThat(
        Expressions.toString(
            Expressions.call(
                Integer.class,
                "valueOf",
                Collections.<Expression>singletonList(
                    Expressions.constant("0123")))),
        is("Integer.valueOf(\"0123\")"));

    // precedence of not and instanceof
    assertThat(
        Expressions.toString(
            Expressions.not(
                Expressions.typeIs(
                    Expressions.parameter(Object.class, "o"),
                    String.class))),
        is("(!(o instanceof String))"));

    // not not
    assertThat(
        Expressions.toString(
            Expressions.not(
                Expressions.not(
                    Expressions.typeIs(
                        Expressions.parameter(Object.class, "o"),
                        String.class)))),
        is("(!(!(o instanceof String)))"));
  }

  @Test void testWriteConstant() {
    // array of primitives
    // 测试基本类型数组的常量表达式
    // int数组会直接使用new int[] {...}语法
    assertThat(
        Expressions.toString(
            Expressions.constant(new int[]{1, 2, -1})),
        is("new int[] {\n"
            + "  1,\n"
            + "  2,\n"
            + "  -1}"));

    // primitive
    // 测试基本类型常量
    // int类型的负数直接输出
    assertThat(
        Expressions.toString(
            Expressions.constant(-12)),
        is("-12"));

    // short类型的常量需要显式类型转换
    assertThat(
        Expressions.toString(
            Expressions.constant((short) -12)),
        is("(short)-12"));

    // byte类型的常量需要显式类型转换
    assertThat(
        Expressions.toString(
            Expressions.constant((byte) -12)),
        is("(byte)-12"));

    // boxed primitives
    // 测试包装类型常量
    // Integer包装类使用Integer.valueOf()方法
    assertThat(
        Expressions.toString(
            Expressions.constant(1, Integer.class)),
        is("Integer.valueOf(1)"));

    // Double包装类使用Double.valueOf()方法,带D后缀
    assertThat(
        Expressions.toString(
            Expressions.constant(-3.14, Double.class)),
        is("Double.valueOf(-3.14D)"));

    // Boolean包装类使用Boolean.valueOf()方法
    assertThat(
        Expressions.toString(
            Expressions.constant(true, Boolean.class)),
        is("Boolean.valueOf(true)"));

    // primitive with explicit class
    // 测试显式指定基本类型的常量
    // int类型直接输出数字
    assertThat(
        Expressions.toString(
            Expressions.constant(1, int.class)),
        is("1"));

    // short类型需要显式类型转换
    assertThat(
        Expressions.toString(
            Expressions.constant(1, short.class)),
        is("(short)1"));

    // byte类型需要显式类型转换
    assertThat(
        Expressions.toString(
            Expressions.constant(1, byte.class)),
        is("(byte)1"));

    // double类型直接输出数字,带D后缀
    assertThat(
        Expressions.toString(
            Expressions.constant(-3.14, double.class)),
        is("-3.14D"));

    // boolean类型直接输出true或false
    assertThat(
        Expressions.toString(
            Expressions.constant(true, boolean.class)),
        is("true"));

    // objects and nulls
    // 测试对象和null的常量表达式
    // String数组使用new String[] {...}语法
    assertThat(
        Expressions.toString(
            Expressions.constant(new String[] {"foo", null})),
        is("new String[] {\n"
            + "  \"foo\",\n"
            + "  null}"));

    // string
    // 测试字符串常量
    // 字符串中的引号需要转义为\"
    assertThat(
        Expressions.toString(
            Expressions.constant("hello, \"world\"!")),
        is("\"hello, \\\"world\\\"!\""));

    // enum
    // 测试枚举常量
    // 枚举常量使用完全限定名
    assertThat(
        Expressions.toString(
            Expressions.constant(MyEnum.X)),
        is("org.apache.calcite.linq4j.test.ExpressionTest.MyEnum.X"));

    // array of enum
    // 测试枚举数组常量
    assertThat(
        Expressions.toString(
            Expressions.constant(new MyEnum[]{MyEnum.X, MyEnum.Y})),
        is("new org.apache.calcite.linq4j.test.ExpressionTest.MyEnum[] {\n"
            + "  org.apache.calcite.linq4j.test.ExpressionTest.MyEnum.X,\n"
            + "  org.apache.calcite.linq4j.test.ExpressionTest.MyEnum.Y}"));

    // class
    // 测试Class对象常量
    // 使用.class语法
    assertThat(
        Expressions.toString(
            Expressions.constant(String.class)),
        is("java.lang.String.class"));

    // array class
    // 测试数组Class对象常量
    assertThat(
        Expressions.toString(
            Expressions.constant(int[].class)),
        is("int[].class"));

    // 二维数组Class对象
    assertThat(
        Expressions.toString(
            Expressions.constant(List[][].class)),
        is("java.util.List[][].class"));

    // automatically call constructor if it matches fields
    // 测试对象数组常量
    // 如果对象有匹配字段的构造函数,会自动调用构造函数
    // Linq4jTest.emps是Employee对象的数组
    assertThat(
        Expressions.toString(
            Expressions.constant(Linq4jTest.emps)),
        is("new org.apache.calcite.linq4j.test.Linq4jTest.Employee[] {\n"
            + "  new org.apache.calcite.linq4j.test.Linq4jTest.Employee(\n"
            + "    100,\n"
            + "    \"Fred\",\n"
            + "    10),\n"
            + "  new org.apache.calcite.linq4j.test.Linq4jTest.Employee(\n"
            + "    110,\n"
            + "    \"Bill\",\n"
            + "    30),\n"
            + "  new org.apache.calcite.linq4j.test.Linq4jTest.Employee(\n"
            + "    120,\n"
            + "    \"Eric\",\n"
            + "    10),\n"
            + "  new org.apache.calcite.linq4j.test.Linq4jTest.Employee(\n"
            + "    130,\n"
            + "    \"Janet\",\n"
            + "    10)}"));
  }

  /** Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6244">[CALCITE-6244]
   * Allow passing record as constant expression</a>. */
  @Test void testWriteRecordConstant(@TempDir Path tempDir) {
    // 创建一个record类,用于测试record类型的常量表达式
    // record是Java 14引入的特性,用于定义不可变的数据类
    // createRecordClass会在临时目录中生成RecordModel类
    Class<?> recordClass = createRecordClass(tempDir, "RecordModel");

    // Call constructor for record
    // 测试包含record对象的ImmutableSet常量表达式
    // 创建四个RecordModel实例,放入ImmutableSet中
    // 每个RecordModel实例通过createInstance创建,传入字符串和整数
    // 验证生成的表达式字符串是否正确
    // 应该生成:ImmutableSet.of(new RecordModel("test1", 1), ...)
    assertThat(
        Expressions.toString(
            Expressions.constant(
                ImmutableSet.of(createInstance(recordClass, "test1", 1),
                    createInstance(recordClass, "test2", 2),
                    createInstance(recordClass, "test3", 3),
                    createInstance(recordClass, "test4", 4)))),
        is("com.google.common.collect.ImmutableSet.of(new RecordModel(\n"
            +  "  \"test1\",\n"
            +  "  1),new RecordModel(\n"
            +  "  \"test2\",\n"
            +  "  2),new RecordModel(\n"
            +  "  \"test3\",\n"
            +  "  3),new RecordModel(\n"
            +  "  \"test4\",\n"
            +  "  4))"));
  }

  @Test void testWriteArray() {
    assertThat(
        Expressions.toString(
            Expressions.add(
                Expressions.constant(1),
                Expressions.arrayIndex(
                    Expressions.variable(int[].class, "integers"),
                    Expressions.add(
                        Expressions.constant(2),
                        Expressions.variable(int.class, "index"))))),
        is("1 + integers[2 + index]"));
  }

  @Test void testWriteAnonymousClass() {
    // final List<String> baz = Arrays.asList("foo", "bar");
    // new AbstractList<String>() {
    //     public int size() {
    //         return baz.size();
    //     }
    //     public String get(int index) {
    //         return ((String) baz.get(index)).toUpperCase();
    //     }
    // }
    final ParameterExpression bazParameter =
        Expressions.parameter(
            Types.of(List.class, String.class),
            "baz");
    final ParameterExpression indexParameter =
        Expressions.parameter(
            Integer.TYPE,
            "index");
    BlockStatement e =
        Expressions.block(
            Expressions.declare(
                Modifier.FINAL,
                bazParameter,
                Expressions.call(
                    Arrays.class,
                    "asList",
                    Arrays.<Expression>asList(
                        Expressions.constant("foo"),
                        Expressions.constant("bar")))),
            Expressions.statement(
                Expressions.new_(
                    Types.of(AbstractList.class, String.class),
                    Collections.emptyList(),
                    Arrays.asList(
                        Expressions.fieldDecl(
                            Modifier.PUBLIC | Modifier.FINAL,
                            Expressions.parameter(
                                String.class,
                                "qux"),
                            Expressions.constant("xyzzy")),
                        Expressions.methodDecl(
                            Modifier.PUBLIC,
                            Integer.TYPE,
                            "size",
                            Collections.emptyList(),
                            Blocks.toFunctionBlock(
                                Expressions.call(
                                    bazParameter,
                                    "size",
                                    Collections.emptyList()))),
                        Expressions.methodDecl(
                            Modifier.PUBLIC,
                            String.class,
                            "get",
                            Arrays.asList(indexParameter),
                            Blocks.toFunctionBlock(
                                Expressions.call(
                                    Expressions.convert_(
                                        Expressions.call(
                                            bazParameter,
                                            "get",
                                            Arrays.<Expression>asList(
                                                indexParameter)),
                                        String.class),
                                    "toUpperCase",
                                    ImmutableList.of())))))));
    assertThat(Expressions.toString(e),
        is("{\n"
            + "  final java.util.List<String> baz = java.util.Arrays.asList(\"foo\", \"bar\");\n"
            + "  new java.util.AbstractList<String>(){\n"
            + "    public final String qux = \"xyzzy\";\n"
            + "    public int size() {\n"
            + "      return baz.size();\n"
            + "    }\n"
            + "\n"
            + "    public String get(int index) {\n"
            + "      return ((String) baz.get(index)).toUpperCase();\n"
            + "    }\n"
            + "\n"
            + "  };\n"
            + "}\n"));
  }

  @Test void testWriteWhile() {
    DeclarationStatement xDecl =
        Expressions.declare(0, "x", Expressions.constant(10));
    DeclarationStatement yDecl =
        Expressions.declare(0, "y", Expressions.constant(0));
    Node node =
        Expressions.block(xDecl, yDecl,
            Expressions.while_(
                Expressions.lessThan(xDecl.parameter, Expressions.constant(5)),
                Expressions.statement(
                    Expressions.preIncrementAssign(yDecl.parameter))));
    assertThat(node,
        hasToString("{\n"
                + "  int x = 10;\n"
                + "  int y = 0;\n"
                + "  while (x < 5) {\n"
                + "    (++y);\n"
                + "  }\n"
                + "}\n"));
  }

  @Test void testWriteTryCatchFinally() {
    final ParameterExpression cce_ =
        Expressions.parameter(Modifier.FINAL, ClassCastException.class, "cce");
    final ParameterExpression re_ =
        Expressions.parameter(0, RuntimeException.class, "re");
    Node node =
        Expressions.tryCatchFinally(
            Expressions.block(
                Expressions.return_(null,
                    Expressions.call(
                        Expressions.constant("foo"),
                        "length"))),
            Expressions.statement(
                Expressions.call(
                    Expressions.constant("foo"),
                    "toUpperCase")),
            Expressions.catch_(cce_,
                Expressions.return_(null, Expressions.constant(null))),
            Expressions.catch_(re_,
                Expressions.throw_(
                    Expressions.new_(IndexOutOfBoundsException.class))));
    assertThat(Expressions.toString(node),
        is("try {\n"
            + "  return \"foo\".length();\n"
            + "} catch (final ClassCastException cce) {\n"
            + "  return null;\n"
            + "} catch (RuntimeException re) {\n"
            + "  throw new IndexOutOfBoundsException();\n"
            + "} finally {\n"
            + "  \"foo\".toUpperCase();\n"
            + "}\n"));
  }

  @Test void testWriteTryFinally() {
    Node node =
        Expressions.ifThen(
            Expressions.constant(true),
            Expressions.tryFinally(
                Expressions.block(
                    Expressions.return_(null,
                        Expressions.call(
                            Expressions.constant("foo"),
                            "length"))),
                Expressions.statement(
                    Expressions.call(
                        Expressions.constant("foo"),
                        "toUpperCase"))));
    assertThat(Expressions.toString(node),
        is("if (true) {\n"
            + "  try {\n"
            + "    return \"foo\".length();\n"
            + "  } finally {\n"
            + "    \"foo\".toUpperCase();\n"
            + "  }\n"
            + "}\n"));
  }

  @Test void testWriteTryCatch() {
    final ParameterExpression cce_ =
        Expressions.parameter(Modifier.FINAL, ClassCastException.class, "cce");
    final ParameterExpression re_ =
        Expressions.parameter(0, RuntimeException.class, "re");
    Node node =
        Expressions.tryCatch(
            Expressions.block(
                Expressions.return_(null,
                    Expressions.call(Expressions.constant("foo"), "length"))),
            Expressions.catch_(cce_,
                Expressions.return_(null, Expressions.constant(null))),
            Expressions.catch_(re_,
                Expressions.return_(null,
                    Expressions.call(re_, "toString"))));
    assertThat(Expressions.toString(node),
        is("try {\n"
            + "  return \"foo\".length();\n"
            + "} catch (final ClassCastException cce) {\n"
            + "  return null;\n"
            + "} catch (RuntimeException re) {\n"
            + "  return re.toString();\n"
            + "}\n"));
  }

  @Test void testType() {
    // Type of ternary operator is the gcd of its arguments.
    // 测试三元运算符的类型推断
    // 三元运算符的类型是其两个分支类型的最大公约数(gcd)
    // int和long的最大公约数是long
    assertThat(
        Expressions.condition(
            Expressions.constant(true),
            Expressions.constant(5),
            Expressions.constant(6L)).getType(),
        is(long.class));
    // long和int的最大公约数也是long
    assertThat(
        Expressions.condition(
            Expressions.constant(true),
            Expressions.constant(5L),
            Expressions.constant(6)).getType(),
        is(long.class));

    // If one of the arguments is null constant, it is implicitly coerced.
    // 测试null常量的类型推断
    // 如果三元运算符的一个分支是null,它的类型会被隐式转换为另一个分支的类型
    // String和null的最大公约数是String
    assertThat(
        Expressions.condition(
            Expressions.constant(true),
            Expressions.constant("xxx"),
            Expressions.constant(null)).getType(),
        is(String.class));
    // int和null的最大公约数是Integer(包装类),而不是int
    assertThat(
        Expressions.condition(
            Expressions.constant(true),
            Expressions.constant(0),
            Expressions.constant(null)).getType(),
        is(Integer.class));

    // In Java, "-" applied to short and byte yield int.
    // 测试取反运算符的类型推断
    // 在Java中,short和byte的取反运算会提升为int类型
    // double的取反还是double
    assertThat(Expressions.negate(Expressions.constant((double) 1)).getType(),
        is(double.class));
    // float的取反还是float
    assertThat(Expressions.negate(Expressions.constant((float) 1)).getType(),
        is(float.class));
    // long的取反还是long
    assertThat(Expressions.negate(Expressions.constant((long) 1)).getType(),
        is(long.class));
    // int的取反还是int
    assertThat(Expressions.negate(Expressions.constant(1)).getType(),
        is(int.class));
    // short的取反会提升为int
    assertThat(Expressions.negate(Expressions.constant((short) 1)).getType(),
        is(int.class));
    // byte的取反会提升为int
    assertThat(Expressions.negate(Expressions.constant((byte) 1)).getType(),
        is(int.class));
  }

  @Test void testCompile() {
    // Creating a parameter for the expression tree.
    // 创建一个String类型的参数表达式
    // 这个参数将用于lambda表达式中
    ParameterExpression param = Expressions.parameter(String.class);

    // Creating an expression for the method call and specifying its
    // parameter.
    // 创建方法调用表达式
    // 调用Integer类的静态方法valueOf(String s)
    // param作为方法调用的参数
    MethodCallExpression methodCall =
        Expressions.call(
            Integer.class,
            "valueOf",
            Collections.<Expression>singletonList(param));

    // The following statement first creates an expression tree,
    // then compiles it, and then runs it.
    // 创建lambda表达式,将methodCall作为lambda体
    // lambda接受一个String参数,返回Integer
    // compile()方法将表达式树编译为可执行的Java代码
    // dynamicInvoke()方法动态调用编译后的函数
    // 传入字符串"1234",应该返回Integer对象1234
    int x =
        Expressions.<Function1<String, Integer>>lambda(
            methodCall,
            new ParameterExpression[] { param })
            .getFunction()
            .apply("1234");
    // 验证结果正确
    assertThat(x, is(1234));
  }

  @Test void testBlockBuilder() {
    // 测试BlockBuilder的非优化模式
    // 在非优化模式下,所有中间变量都会被保留
    checkBlockBuilder(
        false,
        "{\n"
            + "  final int three = 1 + 2;\n"
            + "  final int six = three * 2;\n"
            + "  final int nine = three * three;\n"
            + "  final int eighteen = three + six + nine;\n"
            + "  return eighteen;\n"
            + "}\n");
    // 测试BlockBuilder的优化模式
    // 在优化模式下,只使用一次的中间变量会被内联,减少变量声明
    checkBlockBuilder(
        true,
        "{\n"
            + "  final int three = 1 + 2;\n"
            + "  return three + three * 2 + three * three;\n"
            + "}\n");
  }

  public void checkBlockBuilder(boolean optimizing, String expected) {
    // 创建BlockBuilder实例,optimizing参数控制是否启用优化
    // BlockBuilder是Calcite用于构建代码块的辅助类
    // 它可以管理变量声明、表达式优化等
    BlockBuilder statements = new BlockBuilder(optimizing);
    // 使用append方法添加表达式,BlockBuilder会自动管理变量声明
    // 第一个参数是变量名,第二个参数是表达式
    // BlockBuilder会检测表达式是否相同,避免重复声明
    Expression one =
        statements.append(
            "one", Expressions.constant(1));
    Expression two =
        statements.append(
            "two", Expressions.constant(2));
    // three = one + two = 1 + 2 = 3
    Expression three =
        statements.append(
            "three", Expressions.add(one, two));
    // six = three * two = 3 * 2 = 6
    Expression six =
        statements.append(
            "six",
            Expressions.multiply(three, two));
    // nine = three * three = 3 * 3 = 9
    Expression nine =
        statements.append(
            "nine",
            Expressions.multiply(three, three));
    // eighteen = three + six + nine = 3 + 6 + 9 = 18
    Expression eighteen =
        statements.append(
            "eighteen",
            Expressions.add(
                Expressions.add(three, six),
                nine));
    // 添加return语句
    statements.add(Expressions.return_(null, eighteen));
    // 将BlockBuilder转换为BlockStatement
    BlockStatement expression = statements.toBlock();
    // 验证生成的代码字符串是否符合预期
    assertThat(Expressions.toString(expression), is(expected));
    // 使用Shuttle访问者模式遍历表达式树,用于测试
    expression.accept(new Shuttle());
  }

  @Test void testBlockBuilder2() {
    BlockBuilder statements = new BlockBuilder();
    Expression element =
        statements.append(
            "element", Expressions.constant(null));
    Expression comparator =
        statements.append(
            "comparator", Expressions.constant(null, Comparator.class));
    Expression treeSet =
        statements.append(
            "treeSet",
            Expressions.new_(
                TreeSet.class,
                Arrays.asList(comparator)));
    statements.add(
        Expressions.return_(
            null,
            Expressions.call(
                treeSet,
                "add",
                element)));
    BlockStatement expression = statements.toBlock();
    final String expected = "{\n"
        + "  final java.util.TreeSet treeSet = new java.util.TreeSet(\n"
        + "    (java.util.Comparator) null);\n"
        + "  return treeSet.add(null);\n"
        + "}\n";
    assertThat(Expressions.toString(expression), is(expected));
    expression.accept(new Shuttle());
  }

  @Test void testBlockBuilder3() {
/*
    int a = 1;
    int b = a + 2;
    int c = a + 3;
    int d = a + 4;
    int e = {
      int b = a + 3;
      foo(b);
    }
    bar(a, b, c, d, e);
*/
    BlockBuilder builder0 = new BlockBuilder();
    final Expression a = builder0.append("_a", Expressions.constant(1));
    final Expression b =
        builder0.append("_b", Expressions.add(a, Expressions.constant(2)));
    final Expression c =
        builder0.append("_c", Expressions.add(a, Expressions.constant(3)));
    final Expression d =
        builder0.append("_d", Expressions.add(a, Expressions.constant(4)));

    BlockBuilder builder1 = new BlockBuilder();
    final Expression b1 =
        builder1.append("_b", Expressions.add(a, Expressions.constant(3)));
    builder1.add(
        Expressions.statement(
            Expressions.call(ExpressionTest.class, "foo", b1)));
    final Expression e = builder0.append("e", builder1.toBlock());
    builder0.add(
        Expressions.statement(
            Expressions.call(ExpressionTest.class, "bar", a, b, c, d, e)));
    // With the bug in BlockBuilder.append(String, BlockExpression),
    //    bar(1, _b, _c, _d, foo(_d));
    // Correct result is
    //    bar(1, _b, _c, _d, foo(_c));
    // because _c has the same expression (a + 3) as inner b.
    BlockStatement expression = builder0.toBlock();
    assertThat(Expressions.toString(expression),
        is("{\n"
            + "  final int _b = 1 + 2;\n"
            + "  final int _c = 1 + 3;\n"
            + "  final int _d = 1 + 4;\n"
            + "  final int _b0 = 1 + 3;\n"
            + "  org.apache.calcite.linq4j.test.ExpressionTest.bar(1, _b, _c, _d, org.apache.calcite.linq4j.test.ExpressionTest.foo(_b0));\n"
            + "}\n"));
    expression.accept(new Shuttle());
  }

  @Test void testConstantExpression() {
    final Expression constant =
        Expressions.constant(new Object[] {
            1,
            new Object[] {
                (byte) 1, (short) 2, (int) 3, (long) 4,
                (float) 5, (double) 6, (char) 7, true, "string", null
            },
            new AllType(true, (byte) 100, (char) 101, (short) 102, 103,
                104L, (float) 105, 106D, new BigDecimal(107),
                new BigInteger("108"), "109", null)
        });
    assertThat(constant,
        hasToString("new Object[] {\n"
            + "  1,\n"
            + "  new Object[] {\n"
            + "    (byte)1,\n"
            + "    (short)2,\n"
            + "    3,\n"
            + "    4L,\n"
            + "    5.0F,\n"
            + "    6.0D,\n"
            + "    (char)7,\n"
            + "    true,\n"
            + "    \"string\",\n"
            + "    null},\n"
            + "  new org.apache.calcite.linq4j.test.ExpressionTest.AllType(\n"
            + "    true,\n"
            + "    (byte)100,\n"
            + "    (char)101,\n"
            + "    (short)102,\n"
            + "    103,\n"
            + "    104L,\n"
            + "    105.0F,\n"
            + "    106.0D,\n"
            + "    java.math.BigDecimal.valueOf(107L),\n"
            + "    new java.math.BigInteger(\"108\"),\n"
            + "    \"109\",\n"
            + "    null)}"));
    constant.accept(new Shuttle());
  }

  @Test void testBigDecimalConstantExpression() {
    assertThat(
        Expressions.toString(Expressions.constant("104", BigDecimal.class)),
        is("java.math.BigDecimal.valueOf(104L)"));
    assertThat(
        Expressions.toString(Expressions.constant("1000", BigDecimal.class)),
        is("java.math.BigDecimal.valueOf(1L, -3)"));
    assertThat(
        Expressions.toString(Expressions.constant(1000, BigDecimal.class)),
        is("java.math.BigDecimal.valueOf(1L, -3)"));
    assertThat(
        Expressions.toString(Expressions.constant(107, BigDecimal.class)),
        is("java.math.BigDecimal.valueOf(107L)"));
    assertThat(
        Expressions.toString(
            Expressions.constant(199999999999999L, BigDecimal.class)),
        is("java.math.BigDecimal.valueOf(199999999999999L)"));
    assertThat(
        Expressions.toString(Expressions.constant(12.34, BigDecimal.class)),
        is("java.math.BigDecimal.valueOf(1234L, 2)"));
  }

  @Test void testObjectConstantExpression() {
    assertThat(
        Expressions.toString(Expressions.constant((byte) 100, Object.class)),
        is("(byte)100"));
    assertThat(
        Expressions.toString(Expressions.constant((char) 100, Object.class)),
        is("(char)100"));
    assertThat(
        Expressions.toString(Expressions.constant((short) 100, Object.class)),
        is("(short)100"));
    assertThat(Expressions.toString(Expressions.constant(100L, Object.class)),
        is("100L"));
    assertThat(Expressions.toString(Expressions.constant(100F, Object.class)),
        is("100.0F"));
    assertThat(Expressions.toString(Expressions.constant(100D, Object.class)),
        is("100.0D"));
  }

  @Test void testClassDecl() {
    final NewExpression newExpression =
        Expressions.new_(
            Object.class,
            ImmutableList.of(),
            Arrays.asList(
                Expressions.fieldDecl(
                    Modifier.PUBLIC | Modifier.FINAL,
                    Expressions.parameter(String.class, "foo"),
                    Expressions.constant("bar")),
                new ClassDeclaration(
                    Modifier.PUBLIC | Modifier.STATIC,
                    "MyClass",
                    null,
                    ImmutableList.of(),
                    Arrays.asList(
                        new FieldDeclaration(
                            0,
                            Expressions.parameter(int.class, "x"),
                            Expressions.constant(0)))),
                Expressions.fieldDecl(
                    0,
                    Expressions.parameter(int.class, "i"))));
    assertThat(Expressions.toString(newExpression),
        is("new Object(){\n"
            + "  public final String foo = \"bar\";\n"
            + "  public static class MyClass {\n"
            + "    int x = 0;\n"
            + "  }\n"
            + "  int i;\n"
            + "}"));
    newExpression.accept(new Shuttle());
  }

  @Test void testReturn() {
    assertThat(
        Expressions.toString(
            Expressions.ifThenElse(
                Expressions.constant(true),
                Expressions.return_(null),
                Expressions.return_(null, Expressions.constant(1)))),
        is("if (true) {\n"
            + "  return;\n"
            + "} else {\n"
            + "  return 1;\n"
            + "}\n"));
  }

  @Test void testIfElseIfElse() {
    assertThat(
        Expressions.toString(
            Expressions.ifThenElse(
                Expressions.constant(true),
                Expressions.return_(null),
                Expressions.constant(false),
                Expressions.return_(null),
                Expressions.return_(null, Expressions.constant(1)))),
        is("if (true) {\n"
            + "  return;\n"
            + "} else if (false) {\n"
            + "  return;\n"
            + "} else {\n"
            + "  return 1;\n"
            + "}\n"));
  }

@Test void testSubExpressionElimination() {
    // Test for common sub-expression elimination.
    // 测试公共子表达式消除(CSE)优化
    // BlockBuilder的优化模式会识别相同的表达式,避免重复计算
    // 创建启用了优化的BlockBuilder
    final BlockBuilder builder = new BlockBuilder(true);
    // 创建Object类型的参数p
    ParameterExpression x = Expressions.parameter(Object.class, "p");
    // current4 = (Object[]) p,将参数转换为Object数组
    Expression current4 =
        builder.append("current4",
            Expressions.convert_(x, Object[].class));
    // v = (Short) current4[4],获取数组第4个元素并转换为Short
    Expression v =
        builder.append("v",
            Expressions.convert_(
                Expressions.arrayIndex(current4, Expressions.constant(4)),
                Short.class));
    // v0 = (Number) v,将v转换为Number
    Expression v0 =
        builder.append("v0",
            Expressions.convert_(v, Number.class));
    // v1 = (Short) current4[4],这个表达式和v相同
    // 由于启用了优化,BlockBuilder会识别这个表达式与v相同,会重用v
    Expression v1 =
        builder.append("v1",
            Expressions.convert_(
                Expressions.arrayIndex(current4, Expressions.constant(4)),
                Short.class));
    // v2 = (Number) v,这个表达式和v0相同
    // BlockBuilder会识别这个表达式与v0相同,会重用v0
    Expression v2 =
        builder.append("v2", Expressions.convert_(v, Number.class));
    // v3 = (Short) current4[4],这个表达式也和v相同
    Expression v3 =
        builder.append("v3",
        Expressions.convert_(
            Expressions.arrayIndex(current4, Expressions.constant(4)),
            Short.class));
    // v4 = (Number) v3,由于v3被优化为v,所以v4也会被优化
    Expression v4 =
        builder.append("v4",
            Expressions.convert_(v3, Number.class));
    // v5 = v4.intValue(),调用Number的intValue方法
    Expression v5 = builder.append("v5", Expressions.call(v4, "intValue"));
    // v6 = v2 == null ? null : v5 == 1997
    // 这是一个三元表达式,检查v2是否为null
    Expression v6 =
        builder.append("v6",
            Expressions.condition(
                Expressions.equal(v2, Expressions.constant(null)),
                Expressions.constant(null),
                Expressions.equal(v5, Expressions.constant(1997))));
    // 添加return语句
    builder.add(Expressions.return_(null, v6));
    // 验证生成的代码
    // 由于公共子表达式消除,生成的代码非常简洁:
    // 只有v被声明,其他重复的表达式都被优化掉了
    // v0, v1, v2, v3, v4, v5都被内联或重用
    assertThat(Expressions.toString(builder.toBlock()),
        is("{\n"
            + "  final Short v = (Short) ((Object[]) p)[4];\n"
            + "  return (Number) v == null ? null : ("
            + "(Number) v).intValue() == 1997;\n"
            + "}\n"));
  }

  @Test void testFor() throws NoSuchFieldException {
    final BlockBuilder builder = new BlockBuilder();
    final ParameterExpression i_ = Expressions.parameter(int.class, "i");
    builder.add(
        Expressions.for_(
            Expressions.declare(
                0, i_, Expressions.constant(0)),
            Expressions.lessThan(i_, Expressions.constant(10)),
            Expressions.postIncrementAssign(i_),
            Expressions.block(
                Expressions.statement(
                    Expressions.call(
                        Expressions.field(
                            null, System.class.getField("out")),
                        "println",
                        i_)))));
    assertThat(Expressions.toString(builder.toBlock()),
        is("{\n"
            + "  for (int i = 0; i < 10; i++) {\n"
            + "    System.out.println(i);\n"
            + "  }\n"
            + "}\n"));
  }

  @Test void testFor2() {
    final BlockBuilder builder = new BlockBuilder();
    final ParameterExpression i_ = Expressions.parameter(int.class, "i");
    final ParameterExpression j_ = Expressions.parameter(int.class, "j");
    builder.add(
        Expressions.for_(
            Arrays.asList(
                Expressions.declare(
                    0, i_, Expressions.constant(0)),
                Expressions.declare(
                    0, j_, Expressions.constant(10))),
            null,
            null,
            Expressions.block(
                Expressions.ifThen(
                    Expressions.lessThan(
                        Expressions.preIncrementAssign(i_),
                        Expressions.preDecrementAssign(j_)),
                    Expressions.break_(null)))));
    assertThat(Expressions.toString(builder.toBlock()),
        is("{\n"
            + "  for (int i = 0, j = 10; ; ) {\n"
            + "    if ((++i) < (--j)) {\n"
            + "      break;\n"
            + "    }\n"
            + "  }\n"
            + "}\n"));
  }

  @Test void testForEach() {
    final BlockBuilder builder = new BlockBuilder();
    final ParameterExpression i_ = Expressions.parameter(int.class, "i");
    final ParameterExpression list_ = Expressions.parameter(List.class, "list");
    builder.add(
        Expressions.forEach(i_, list_,
            Expressions.ifThen(
                Expressions.lessThan(
                    Expressions.constant(1),
                    Expressions.constant(2)),
                Expressions.break_(null))));
    assertThat(Expressions.toString(builder.toBlock()),
        is("{\n"
            + "  for (int i : list) {\n"
            + "    if (1 < 2) {\n"
            + "      break;\n"
            + "    }\n"
            + "  }\n"
            + "}\n"));
  }

  @Test void testEmptyListLiteral() {
    assertThat(Expressions.toString(Expressions.constant(Arrays.asList())),
        is("java.util.Collections.EMPTY_LIST"));
  }

  @Test void testOneElementListLiteral() {
    assertThat(Expressions.toString(Expressions.constant(Arrays.asList(1))),
        is("java.util.Arrays.asList(1)"));
  }

  @Test void testTwoElementsListLiteral() {
    assertThat(Expressions.toString(Expressions.constant(Arrays.asList(1, 2))),
        is("java.util.Arrays.asList(1,\n"
            + "  2)"));
  }

  @Test void testNestedListsLiteral() {
    assertThat(
        Expressions.toString(
            Expressions.constant(
                Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4)))),
        is("java.util.Arrays.asList(java.util.Arrays.asList(1,\n"
            + "    2),\n"
            + "  java.util.Arrays.asList(3,\n"
            + "    4))"));
  }

  @Test void testEmptyMapLiteral() {
    assertThat(Expressions.toString(Expressions.constant(new HashMap<>())),
        is("com.google.common.collect.ImmutableMap.of()"));
  }

  @Test void testOneElementMapLiteral() {
    assertThat(
        Expressions.toString(
            Expressions.constant(Collections.singletonMap("abc", 42))),
        is("com.google.common.collect.ImmutableMap.of(\"abc\", 42)"));
  }

  @Test void testTwoElementsMapLiteral() {
    assertThat(
        Expressions.toString(
            Expressions.constant(ImmutableMap.of("abc", 42, "def", 43))),
        is("com.google.common.collect.ImmutableMap.of(\"abc\", 42,\n"
            + "\"def\", 43)"));
  }

  @Test void testTenElementsMapLiteral() {
    Map<String, String> map = new LinkedHashMap<>(); // for consistent output
    for (int i = 0; i < 10; i++) {
      map.put("key_" + i, "value_" + i);
    }
    assertThat(Expressions.toString(Expressions.constant(map)),
        is("com.google.common.collect.ImmutableMap.builder()"
            + ".put(\"key_0\", \"value_0\")\n"
            + ".put(\"key_1\", \"value_1\")\n"
            + ".put(\"key_2\", \"value_2\")\n"
            + ".put(\"key_3\", \"value_3\")\n"
            + ".put(\"key_4\", \"value_4\")\n"
            + ".put(\"key_5\", \"value_5\")\n"
            + ".put(\"key_6\", \"value_6\")\n"
            + ".put(\"key_7\", \"value_7\")\n"
            + ".put(\"key_8\", \"value_8\")\n"
            + ".put(\"key_9\", \"value_9\").build()"));
  }

  @Test void testEvaluate() {
    Expression x = Expressions.add(ONE, TWO);
    Object value = Expressions.evaluate(x);
    assertThat(value, is(3));
  }

  @Test void testEmptySetLiteral() {
    assertThat(Expressions.toString(Expressions.constant(new HashSet<>())),
        is("com.google.common.collect.ImmutableSet.of()"));
  }

  @Test void testOneElementSetLiteral() {
    assertThat(Expressions.toString(Expressions.constant(Sets.newHashSet(1))),
        is("com.google.common.collect.ImmutableSet.of(1)"));
  }

  @Test void testTwoElementsSetLiteral() {
    assertThat(
        Expressions.toString(Expressions.constant(ImmutableSet.of(1, 2))),
        is("com.google.common.collect.ImmutableSet.of(1,2)"));
  }

  @Test void testTenElementsSetLiteral() {
    Set<Integer> set = new LinkedHashSet<>(); // for consistent output
    for (int i = 0; i < 10; i++) {
      set.add(i);
    }
    assertThat(Expressions.toString(Expressions.constant(set)),
        is("com.google.common.collect.ImmutableSet.builder().add(0)\n"
            + ".add(1)\n"
            + ".add(2)\n"
            + ".add(3)\n"
            + ".add(4)\n"
            + ".add(5)\n"
            + ".add(6)\n"
            + ".add(7)\n"
            + ".add(8)\n"
            + ".add(9).build()"));
  }

  @Test void testTenElementsLinkedHashSetLiteral() {
    Set<Integer> set = new LinkedHashSet<>(); // for consistent output
    for (int i = 0; i < 10; i++) {
      set.add(i);
    }
    assertThat(Expressions.toString(Expressions.constant(set)),
        is("com.google.common.collect.ImmutableSet.builder().add(0)\n"
            + ".add(1)\n"
            + ".add(2)\n"
            + ".add(3)\n"
            + ".add(4)\n"
            + ".add(5)\n"
            + ".add(6)\n"
            + ".add(7)\n"
            + ".add(8)\n"
            + ".add(9).build()"));
  }

  @Test void testTenElementsSetStringLiteral() {
    Set<String> set = new LinkedHashSet<>(); // for consistent output
    for (int i = 10; i > 0; i--) {
      set.add(String.valueOf(i));
    }
    assertThat(Expressions.toString(Expressions.constant(set)),
        is("com.google.common.collect.ImmutableSet.builder().add(\"10\")\n"
            + ".add(\"9\")\n"
            + ".add(\"8\")\n"
            + ".add(\"7\")\n"
            + ".add(\"6\")\n"
            + ".add(\"5\")\n"
            + ".add(\"4\")\n"
            + ".add(\"3\")\n"
            + ".add(\"2\")\n"
            + ".add(\"1\").build()"));
  }

  /** An enum. */
  // 定义一个枚举类型,用于测试枚举常量的表达式生成
  // 包含两个枚举值:X和Y
  // Y枚举值重写了toString()方法,返回"YYY"
  enum MyEnum {
    X,  // 普通枚举值,toString()返回"X"
    Y {  // 带有匿名类实现的枚举值
      public String toString() {
        return "YYY";  // 重写toString方法,返回"YYY"
      }
    }
  }

  // 静态辅助方法,用于测试
  // 接受一个int参数,返回0
  // 这个方法在testBlockBuilder3中被调用
  public static int foo(int x) {
    return 0;
  }

  // 静态辅助方法,用于测试
  // 接受五个int参数,返回0
  // 这个方法在testBlockBuilder3中被调用
  public static int bar(int v, int w, int x, int y, int z) {
    return 0;
  }

  /** A class with a field for each type of interest. */
  // 测试辅助类,包含各种类型的字段
  // 用于测试对象常量的表达式生成
  // 所有字段都是final的,表示不可变
  public static class AllType {
    // boolean类型字段
    public final boolean b;
    // byte类型字段
    public final byte y;
    // char类型字段
    public final char c;
    // short类型字段
    public final short s;
    public final int i;
    public final long l;
    public final float f;
    public final double d;
    public final BigDecimal bd;
    public final BigInteger bi;
    public final String str;
    public final @Nullable Object o;

    // AllType类的构造函数
    // 接受所有字段的初始化值
    // 参数包括所有Java基本类型和常用对象类型
    // @Nullable注解表示o参数可以为null
    public AllType(boolean b, byte y, char c, short s, int i, long l, float f,
        double d, BigDecimal bd, BigInteger bi, String str, @Nullable Object o) {
      // 初始化所有字段
      this.b = b;  // boolean字段
      this.y = y;  // byte字段
      this.c = c;  // char字段
      this.s = s;  // short字段
      this.i = i;  // int字段
      this.l = l;  // long字段
      this.f = f;  // float字段
      this.d = d;  // double字段
      this.bd = bd;  // BigDecimal字段
      this.bi = bi;  // BigInteger字段
      this.str = str;  // String字段
      this.o = o;  // Object字段,可以为null
    }
  }
}
