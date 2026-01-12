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
package org.apache.calcite.linq4j.test; // 声明包名，表示该类属于org.apache.calcite.linq4j.test包

import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，用于反射获取方法
import org.apache.calcite.linq4j.tree.BinaryExpression; // 导入二元表达式类，表示二元运算表达式
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入块语句类，表示代码块
import org.apache.calcite.linq4j.tree.ConditionalStatement; // 导入条件语句类，表示if-else语句
import org.apache.calcite.linq4j.tree.ConstantExpression; // 导入常量表达式类，表示常量值
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式基类，所有表达式的父类
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工厂类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式类，表示变量或参数

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.lang.reflect.Method; // 导入Method类，用于反射获取方法信息
import java.lang.reflect.Modifier; // 导入Modifier类，用于获取修饰符信息

import static org.apache.calcite.linq4j.test.BlockBuilderBase.FALSE; // 静态导入BlockBuilderBase的FALSE常量，表示false布尔值表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.FOUR; // 静态导入BlockBuilderBase的FOUR常量，表示数字4的常量表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.NULL; // 静态导入BlockBuilderBase的NULL常量，表示null值表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.NULL_INTEGER; // 静态导入BlockBuilderBase的NULL_INTEGER常量，表示Integer类型的null值表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.ONE; // 静态导入BlockBuilderBase的ONE常量，表示数字1的常量表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.THREE; // 静态导入BlockBuilderBase的THREE常量，表示数字3的常量表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.TRUE; // 静态导入BlockBuilderBase的TRUE常量，表示true布尔值表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.TRUE_B; // 静态导入BlockBuilderBase的TRUE_B常量，表示Boolean.TRUE的常量表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.TWO; // 静态导入BlockBuilderBase的TWO常量，表示数字2的常量表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.bool; // 静态导入BlockBuilderBase的bool方法，用于创建布尔参数表达式
import static org.apache.calcite.linq4j.test.BlockBuilderBase.optimize; // 静态导入BlockBuilderBase的optimize方法，用于优化表达式

import static org.hamcrest.CoreMatchers.equalTo; // 静态导入equalTo匹配器，用于断言相等
import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat方法，用于执行断言

/**
 * Unit test for {@link org.apache.calcite.linq4j.tree.BlockBuilder}
 * optimization capabilities.
 * 这是BlockBuilder优化能力的单元测试类
 * 该类用于测试Calcite的Linq4j框架中的表达式优化器功能
 * 优化器能够对各种表达式进行常量折叠、逻辑简化、死代码消除等优化
 * 测试覆盖了三元运算符、二元运算符、逻辑运算、比较运算、条件语句等多种表达式的优化场景
 * 
 * 主要测试的优化类型包括：
 * 1. 常量折叠：将常量表达式在编译期计算并替换为结果值，如 1 == 2 优化为 false
 * 2. 逻辑简化：简化逻辑表达式，如 !(!a) 优化为 a
 * 3. 三元运算符优化：简化条件表达式，如 true ? 1 : 2 优化为 1
 * 4. 死代码消除：移除永远不会执行的代码分支
 * 5. 类型转换优化：移除不必要的类型转换
 * 6. 相同表达式消除：识别并消除重复的子表达式
 * 
 * 该类没有成员变量，所有测试方法都是独立的，使用JUnit5的@Test注解标记
 * 每个测试方法都验证一种特定的优化场景，确保优化器的正确性
 * 
 * 优化过程的工作原理：
 * - BlockBuilder在构建代码块时会对表达式树进行分析和变换
 * - 通过递归遍历表达式树，识别可以优化的模式
 * - 应用各种优化规则，生成更高效的表达式
 * - 最终生成优化后的代码字符串，与预期结果进行比对
 */
class OptimizerTest { // 定义OptimizerTest类，用于测试BlockBuilder的优化功能
  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeComparison() { // 测试比较表达式的优化功能，验证相同常量比较能否正确优化为true
    assertThat(optimize(Expressions.equal(ONE, ONE)), // 调用optimize方法优化表达式 1 == 1，期望优化为常量true
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true的代码块，验证常量折叠优化是否生效
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAlwaysTrue() { // 测试条件为常量true的三元运算符优化，验证能否简化为直接返回true分支的值
    // true ? 1 : 2  注释说明：当条件为true时，三元运算符应该优化为直接返回true分支的值1
    assertThat(optimize(Expressions.condition(TRUE, ONE, TWO)), // 创建三元表达式：true ? 1 : 2，然后调用optimize进行优化
        is("{\n  return 1;\n}\n")); // 断言优化后的结果是返回1的代码块，验证三元运算符的常量条件优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAlwaysFalse() { // 测试条件为常量false的三元运算符优化，验证能否简化为直接返回false分支的值
    // false ? 1 : 2  注释说明：当条件为false时，三元运算符应该优化为直接返回false分支的值2
    assertThat(optimize(Expressions.condition(FALSE, ONE, TWO)), // 创建三元表达式：false ? 1 : 2，然后调用optimize进行优化
        is("{\n  return 2;\n}\n")); // 断言优化后的结果是返回2的代码块，验证三元运算符的常量条件优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAlwaysSame() { // 测试两个分支值相同的三元运算符优化，验证能否简化为直接返回该值
    // bool ? 1 : 1  注释说明：当两个分支的值相同时，无论条件如何，结果都是1，应该优化为直接返回1
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：bool ? 1 : 1，两个分支的值都是1
                Expressions.parameter(boolean.class, "bool"), ONE, ONE)), // 参数表达式bool作为条件，ONE作为两个分支的值
        is("{\n  return 1;\n}\n")); // 断言优化后的结果是返回1的代码块，验证三元运算符的相同分支优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNonOptimizableTernary() { // 测试不可优化的三元运算符，验证保持原样不进行不当优化
    // bool ? 1 : 2  注释说明：条件是变量，两个分支值不同，无法进行常量折叠优化，应该保持原样
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：bool ? 1 : 2，条件是变量bool，两个分支值不同
                Expressions.parameter(boolean.class, "bool"), ONE, TWO)), // 参数表达式bool作为条件，ONE和TWO作为两个分支的值
        is("{\n  return bool ? 1 : 2;\n}\n")); // 断言优化后的结果保持原样，验证不会对无法优化的表达式进行不当修改
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryRotateNot() { // 测试条件为非运算的三元运算符优化，验证能否通过反转条件来交换分支
    // !bool ? 1 : 2  注释说明：条件是!bool，可以优化为 bool ? 2 : 1，通过反转条件并交换分支实现
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：!bool ? 1 : 2，条件是bool的非运算
                Expressions.not(Expressions.parameter(boolean.class, "bool")), // 使用not表达式对bool参数取反作为条件
                ONE, TWO)), // ONE和TWO作为两个分支的值
        is("{\n  return bool ? 2 : 1;\n}\n")); // 断言优化后的结果是 bool ? 2 : 1，验证通过反转条件交换分支的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryRotateEqualFalse() { // 测试条件为等于false的比较表达式，验证能否优化为反转条件并交换分支
    // bool == false ? 1 : 2  注释说明：条件是bool == false，等价于!bool，可以优化为 bool ? 2 : 1
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：bool == false ? 1 : 2
                Expressions.equal(Expressions.parameter(boolean.class, "bool"), // 创建相等比较表达式，比较bool参数和FALSE常量
                    FALSE), // FALSE作为比较的右操作数
                ONE, TWO)), // ONE和TWO作为两个分支的值
        is("{\n  return bool ? 2 : 1;\n}\n")); // 断言优化后的结果是 bool ? 2 : 1，验证相等false比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAtrueB() { // 测试三元运算符true分支为true的优化，验证能否简化为逻辑或运算
    // a ? true : b  === a || b  注释说明：如果a为true则返回true，否则返回b，等价于a || b的逻辑或运算
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：a ? true : b
                Expressions.parameter(boolean.class, "a"), // 参数表达式a作为条件
                TRUE, Expressions.parameter(boolean.class, "b"))), // TRUE作为true分支，参数表达式b作为false分支
        is("{\n  return a || b;\n}\n")); // 断言优化后的结果是 a || b，验证三元运算符简化为逻辑或运算的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAtrueNull() { // 测试三元运算符的true分支为TRUE_B，false分支为null的情况，验证类型转换处理
    // a ? Boolean.TRUE : null  === a ? Boolean.TRUE : (Boolean) null  注释说明：测试布尔类型的null值处理
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：a ? Boolean.TRUE : null
                Expressions.parameter(boolean.class, "a"), // 参数表达式a作为条件
                TRUE_B, Expressions.constant(null, Boolean.class))), // TRUE_B作为true分支，Boolean类型的null常量作为false分支
        is("{\n  return a ? Boolean.TRUE : null;\n}\n")); // 断言优化后的结果保持原样，验证null值的类型转换是否正确处理
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAtrueBoxed() { // 测试三元运算符true分支为TRUE_B，false分支为Boolean.valueOf(b)的优化
    // a ? Boolean.TRUE : Boolean.valueOf(b)  === a || b  注释说明：包装类型的true和valueOf调用，可以简化为逻辑或
    assertThat(
        optimize(
            Expressions.condition(Expressions.parameter(boolean.class, "a"), // 创建三元表达式：a ? Boolean.TRUE : Boolean.valueOf(b)
                TRUE_B, // TRUE_B作为true分支
                Expressions.call(Boolean.class, "valueOf", // 创建Boolean.valueOf(b)的方法调用表达式作为false分支
                    Expressions.parameter(boolean.class, "b")))), // 参数表达式b作为valueOf方法的参数
        is("{\n  return a || Boolean.valueOf(b);\n}\n")); // 断言优化后的结果是 a || Boolean.valueOf(b)，验证包装类型的三元运算符优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryABtrue() { // 测试三元运算符false分支为true的优化，验证能否简化为逻辑或运算
    // a ? b : true  === !a || b  注释说明：如果a为true则返回b，否则返回true，等价于!a || b的逻辑或运算
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：a ? b : true
                Expressions.parameter(boolean.class, "a"), // 参数表达式a作为条件
                Expressions.parameter(boolean.class, "b"), TRUE)), // 参数表达式b作为true分支，TRUE作为false分支
        is("{\n  return (!a) || b;\n}\n")); // 断言优化后的结果是 (!a) || b，验证三元运算符简化为逻辑或运算的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAfalseB() { // 测试三元运算符true分支为false的优化，验证能否简化为逻辑与运算
    // a ? false : b === !a && b  注释说明：如果a为true则返回false，否则返回b，等价于!a && b的逻辑与运算
    assertThat(
        optimize(
            Expressions.condition( // 创建三元表达式：a ? false : b
                Expressions.parameter(boolean.class, "a"), // 参数表达式a作为条件
                FALSE, Expressions.parameter(boolean.class, "b"))), // FALSE作为true分支，参数表达式b作为false分支
        is("{\n  return (!a) && b;\n}\n")); // 断言优化后的结果是 (!a) && b，验证三元运算符简化为逻辑与运算的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryABfalse() { // 测试三元运算符false分支为false的优化，验证能否简化为逻辑与运算
    // a ? b : false === a && b  注释说明：如果a为true则返回b，否则返回false，等价于a && b的逻辑与运算
    assertThat(
        optimize(
            Expressions.condition(Expressions.parameter(boolean.class, "a"), // 创建三元表达式：a ? b : false
                Expressions.parameter(boolean.class, "b"), FALSE)), // 参数表达式b作为true分支，FALSE作为false分支
        is("{\n  return a && b;\n}\n")); // 断言优化后的结果是 a && b，验证三元运算符简化为逻辑与运算的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryInEqualABCeqB() { // 测试嵌套在相等比较中的三元运算符优化，验证能否简化为逻辑或表达式
    // (v ? (Integer) null : inp0_) == null  注释说明：比较三元表达式结果与null，可以简化为 v || inp0_ == null
    assertThat(
        optimize(
            Expressions.equal( // 创建相等比较表达式，比较三元表达式的结果与null
                Expressions.condition(Expressions.parameter(boolean.class, "v"), // 创建三元表达式：v ? (Integer) null : inp0_
                    NULL_INTEGER, // v为true时返回NULL_INTEGER
                    Expressions.parameter(Integer.class, "inp0_")), // v为false时返回inp0_参数
            NULL)), // 与NULL常量进行比较
        is("{\n  return v || inp0_ == null;\n}\n")); // 断言优化后的结果是 v || inp0_ == null，验证嵌套三元运算符的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryNullCasting1() { // 测试三元运算符中null值的类型转换优化，验证类型转换的正确处理
    assertThat(
        optimize(
            Expressions.equal( // 创建相等比较表达式，比较三元表达式结果与Long.valueOf(2L)
                Expressions.condition(Expressions.parameter(boolean.class, "v"), // 创建三元表达式：v ? (Long) 1L : (Long) null
                    new ConstantExpression(Long.class, 1L), // v为true时返回Long类型的1L
                    new ConstantExpression(Long.class, null)), // v为false时返回Long类型的null
                new ConstantExpression(Long.class, 2L))), // 与Long类型的2L进行比较
        is("{\n  return (v ? Long.valueOf(1L) : null) == Long.valueOf(2L);\n}\n")); // 断言优化后的结果保持类型转换

    assertThat(
        optimize(
            Expressions.equal( // 创建相等比较表达式，比较三元表达式结果与Long.valueOf(2L)
                Expressions.condition(Expressions.parameter(boolean.class, "v"), // 创建三元表达式：v ? (Long) null : (Long) 1L
                    new ConstantExpression(Long.class, null), // v为true时返回Long类型的null
                    new ConstantExpression(Long.class, 1L)), // v为false时返回Long类型的1L
                new ConstantExpression(Long.class, 2L))), // 与Long类型的2L进行比较
        is("{\n  return (v ? null : Long.valueOf(1L)) == Long.valueOf(2L);\n}\n")); // 断言优化后的结果保持类型转换

    assertThat(
        optimize(
            Expressions.equal( // 创建相等比较表达式，比较三元表达式结果与Long.valueOf(2L)
                Expressions.condition(Expressions.parameter(boolean.class, "v"), // 创建三元表达式：v ? (Object) null : (Long) 1L
                    new ConstantExpression(Object.class, null), // v为true时返回Object类型的null
                    new ConstantExpression(Long.class, 1L)), // v为false时返回Long类型的1L
                new ConstantExpression(Long.class, 2L))), // 与Long类型的2L进行比较
        is("{\n  return (v ? null : Long.valueOf(1L)) == Long.valueOf(2L);\n}\n")); // 断言优化后的结果，验证不同类型null的处理
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryNullCasting2() { // 测试块语句中三元运算符和逻辑或运算的null类型转换优化
    ParameterExpression o = Expressions.parameter(Boolean.class, "o"); // 创建Boolean类型的参数表达式o，用于声明变量
    ParameterExpression v = Expressions.parameter(Boolean.class, "v"); // 创建Boolean类型的参数表达式v，用于声明变量

    BlockStatement bl = // 创建块语句，包含变量声明
        Expressions.block( // 使用Expressions.block方法创建代码块
            Expressions.declare(0, v, // 声明变量v，修饰符为0（无修饰符）
                new ConstantExpression(Boolean.class, false)), // 初始化值为Boolean.valueOf(false)
        Expressions.declare(0, o, // 声明变量o，修饰符为0
            Expressions.condition(v, // 初始化值为三元表达式：v ? null : true
                new ConstantExpression(Object.class, null), // v为true时返回Object类型的null
                new ConstantExpression(Boolean.class, true)))); // v为false时返回Boolean.valueOf(true)

    assertThat(optimize(bl), // 优化块语句bl
        is("{\n  Boolean v = Boolean.valueOf(false);\n" // 验证优化后的结果，v的声明保持不变
            + "  Boolean o = v ? null : Boolean.valueOf(true);\n}\n")); // o的三元表达式保持类型转换

    bl = // 创建新的块语句
        Expressions.block( // 使用Expressions.block方法创建代码块
            Expressions.declare(0, o, // 声明变量o，修饰符为0
            Expressions.orElse( // 初始化值为逻辑或表达式：true || null
                new ConstantExpression(Boolean.class, true), // 左操作数为Boolean.valueOf(true)
                new ConstantExpression(Boolean.class, null)))); // 右操作数为Boolean类型的null

    assertThat(optimize(bl), // 优化块语句bl
        is("{\n  Boolean o = Boolean.valueOf(true) || (Boolean) null;\n}\n")); // 验证逻辑或表达式保持类型转换

    bl = // 创建新的块语句
        Expressions.block( // 使用Expressions.block方法创建代码块
            Expressions.declare(0, o, // 声明变量o，修饰符为0
            Expressions.orElse( // 初始化值为逻辑或表达式：null || true
                new ConstantExpression(Boolean.class, null), // 左操作数为Boolean类型的null
                new ConstantExpression(Boolean.class, true)))); // 右操作数为Boolean.valueOf(true)

    assertThat(optimize(bl), // 优化块语句bl
        is("{\n  Boolean o = (Boolean) null || Boolean.valueOf(true);\n}\n")); // 验证逻辑或表达式保持类型转换
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeBinaryNullCasting1() { // 测试条件语句中二元运算和null赋值的优化
    ParameterExpression x = Expressions.variable(String.class, "x"); // 创建String类型的变量表达式x
    ConstantExpression one = new ConstantExpression(String.class, "one"); // 创建String类型的常量表达式"one"
    ConstantExpression second = new ConstantExpression(String.class, null); // 创建String类型的常量表达式null

    ConstantExpression innerExp = new ConstantExpression(Long.class, 2L); // 创建Long类型的常量表达式2L
    ParameterExpression y = Expressions.parameter(Long.class, "y"); // 创建Long类型的参数表达式y
    BinaryExpression exp0 = Expressions.greaterThan(y, innerExp); // 创建二元表达式：y > 2L
    ConditionalStatement finalExp = // 创建条件语句
        Expressions.ifThenElse(exp0, Expressions.assign(x, one), // 如果y > 2L，则x = "one"
            Expressions.assign(x, second)); // 否则x = null

    assertThat(optimize(finalExp), // 优化条件语句finalExp
        is("{\n  if (y > Long.valueOf(2L)) {\n" // 验证优化后的结果，条件保持不变
            + "    return x = \"one\";\n" // true分支的赋值语句
            + "  } else {\n" // else分支
            + "    return x = null;\n" // false分支的赋值语句
            + "  }\n}\n")); // 验证条件语句的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeBinaryNullCasting2() { // 测试赋值语句中逻辑或运算的null类型转换优化
    // Boolean x;  注释说明：声明Boolean类型的变量x
    ParameterExpression x = Expressions.variable(Boolean.class, "x"); // 创建Boolean类型的变量表达式x
    ParameterExpression y = Expressions.variable(Boolean.class, "y"); // 创建Boolean类型的变量表达式y
    // Boolean y = x || (Boolean) null;  注释说明：y的赋值表达式，包含逻辑或运算和null类型转换
    BinaryExpression yt = // 创建二元表达式（赋值表达式）
        Expressions.assign( // 创建赋值表达式：y = x || (Boolean) null
            y, Expressions.orElse(x, // 左操作数是变量y，右操作数是逻辑或表达式
            new ConstantExpression(Boolean.class, null))); // 逻辑或的右操作数是Boolean类型的null
    assertThat(optimize(yt), // 优化赋值表达式yt
        is("{\n  return y = x || (Boolean) null;\n}\n")); // 验证优化后的结果保持逻辑或运算和类型转换
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryInEqualABCeqC() { // 测试嵌套在相等比较中的三元运算符优化，验证能否简化为逻辑或表达式
    // (v ? inp0_ : (Integer) null) == null  注释说明：比较三元表达式结果与null，可以简化为 (!v) || inp0_ == null
    assertThat(
        optimize(
            Expressions.equal( // 创建相等比较表达式，比较三元表达式的结果与null
                Expressions.condition(Expressions.parameter(boolean.class, "v"), // 创建三元表达式：v ? inp0_ : (Integer) null
                    Expressions.parameter(Integer.class, "inp0_"), // v为true时返回inp0_参数
                    NULL_INTEGER), // v为false时返回NULL_INTEGER
            NULL)), // 与NULL常量进行比较
        is("{\n  return (!v) || inp0_ == null;\n}\n")); // 断言优化后的结果是 (!v) || inp0_ == null，验证嵌套三元运算符的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAeqBBA() { // 测试条件为相等比较且分支为参数的三元运算符优化
    // a == b ? b : a  注释说明：如果a等于b则返回b，否则返回a，等价于直接返回a
    ParameterExpression a = Expressions.parameter(boolean.class, "a"); // 创建Boolean类型的参数表达式a
    ParameterExpression b = Expressions.parameter(boolean.class, "b"); // 创建Boolean类型的参数表达式b
    assertThat(optimize(Expressions.condition(Expressions.equal(a, b), b, a)), // 创建三元表达式：a == b ? b : a，然后优化
        is("{\n  return a;\n}\n")); // 断言优化后的结果是返回a，验证相等比较的三元运算符优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAeqBAB() { // 测试条件为相等比较且分支为参数的三元运算符优化
    // a == b ? a : b  注释说明：如果a等于b则返回a，否则返回b，等价于直接返回b
    ParameterExpression a = Expressions.parameter(boolean.class, "a"); // 创建Boolean类型的参数表达式a
    ParameterExpression b = Expressions.parameter(boolean.class, "b"); // 创建Boolean类型的参数表达式b
    assertThat(optimize(Expressions.condition(Expressions.equal(a, b), a, b)), // 创建三元表达式：a == b ? a : b，然后优化
        is("{\n  return b;\n}\n")); // 断言优化后的结果是返回b，验证相等比较的三元运算符优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryInEqualABCneqB() { // 测试嵌套在不等比较中的三元运算符优化
    // (v ? (Integer) null : inp0_) != null  注释说明：比较三元表达式结果与null，可以简化为 !(v || inp0_ == null)
    assertThat(
        optimize(
            Expressions.notEqual( // 创建不等比较表达式，比较三元表达式的结果与null
                Expressions.condition(Expressions.parameter(boolean.class, "v"), // 创建三元表达式：v ? (Integer) null : inp0_
                    NULL_INTEGER, // v为true时返回NULL_INTEGER
                    Expressions.parameter(Integer.class, "inp0_")), // v为false时返回inp0_参数
            NULL)), // 与NULL常量进行比较
        is("{\n  return (!(v || inp0_ == null));\n}\n")); // 断言优化后的结果是 (!(v || inp0_ == null))，验证嵌套三元运算符的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryInEqualABCneqC() { // 测试嵌套在不等比较中的三元运算符优化
    // (v ? inp0_ : (Integer) null) != null  注释说明：比较三元表达式结果与null，可以简化为 !((!v) || inp0_ == null)
    assertThat(
        optimize(
            Expressions.notEqual( // 创建不等比较表达式，比较三元表达式的结果与null
                Expressions.condition(Expressions.parameter(boolean.class, "v"), // 创建三元表达式：v ? inp0_ : (Integer) null
                    Expressions.parameter(Integer.class, "inp0_"), // v为true时返回inp0_参数
                    NULL_INTEGER), // v为false时返回NULL_INTEGER
            NULL)), // 与NULL常量进行比较
        is("{\n  return (!((!v) || inp0_ == null));\n}\n")); // 断言优化后的结果是 !((!v) || inp0_ == null)，验证嵌套三元运算符的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAneqBBA() { // 测试条件为不等比较且分支为参数的三元运算符优化
    // a != b ? b : a  注释说明：如果a不等于b则返回b，否则返回a，等价于直接返回b
    ParameterExpression a = Expressions.parameter(boolean.class, "a"); // 创建Boolean类型的参数表达式a
    ParameterExpression b = Expressions.parameter(boolean.class, "b"); // 创建Boolean类型的参数表达式b
    assertThat(
        optimize(Expressions.condition(Expressions.notEqual(a, b), b, a)), // 创建三元表达式：a != b ? b : a，然后优化
        is("{\n  return b;\n}\n")); // 断言优化后的结果是返回b，验证不等比较的三元运算符优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOptimizeTernaryAneqBAB() { // 测试条件为不等比较且分支为参数的三元运算符优化
    // a != b ? a : b  注释说明：如果a不等于b则返回a，否则返回b，等价于直接返回a
    ParameterExpression a = Expressions.parameter(boolean.class, "a"); // 创建Boolean类型的参数表达式a
    ParameterExpression b = Expressions.parameter(boolean.class, "b"); // 创建Boolean类型的参数表达式b
    assertThat(
        optimize(Expressions.condition(Expressions.notEqual(a, b), a, b)), // 创建三元表达式：a != b ? a : b，然后优化
        is("{\n  return a;\n}\n")); // 断言优化后的结果是返回a，验证不等比较的三元运算符优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testAndAlsoTrueBool() { // 测试逻辑与运算的左操作数为true的优化
    // true && bool  注释说明：true与任何值的逻辑与等于该值本身，可以优化为直接返回bool
    assertThat(
        optimize(
            Expressions.andAlso(TRUE, // 创建逻辑与表达式：true && bool
                Expressions.parameter(boolean.class, "bool"))), // 右操作数是参数表达式bool
        is("{\n  return bool;\n}\n")); // 断言优化后的结果是返回bool，验证左操作数为true的逻辑与优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testAndAlsoBoolTrue() { // 测试逻辑与运算的右操作数为true的优化
    // bool && true  注释说明：任何值与true的逻辑与等于该值本身，可以优化为直接返回bool
    assertThat(
        optimize(
            Expressions.andAlso( // 创建逻辑与表达式：bool && true
                Expressions.parameter(boolean.class, "bool"), TRUE)), // 左操作数是参数表达式bool，右操作数是TRUE
        is("{\n  return bool;\n}\n")); // 断言优化后的结果是返回bool，验证右操作数为true的逻辑与优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testAndAlsoFalseBool() { // 测试逻辑与运算的左操作数为false的优化
    // false && bool  注释说明：false与任何值的逻辑与等于false，可以优化为直接返回false
    assertThat(
        optimize(
            Expressions.andAlso(FALSE, // 创建逻辑与表达式：false && bool
                Expressions.parameter(boolean.class, "bool"))), // 右操作数是参数表达式bool
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证左操作数为false的逻辑与优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testAndAlsoNullBool() { // 测试逻辑与运算的左操作数为null的情况
    // null && bool  注释说明：null与bool的逻辑与无法确定结果，保持原样
    assertThat(
        optimize(
            Expressions.andAlso(NULL, // 创建逻辑与表达式：null && bool
                Expressions.parameter(boolean.class, "bool"))), // 右操作数是参数表达式bool
        is("{\n  return null && bool;\n}\n")); // 断言优化后的结果保持原样，验证null值的逻辑与运算不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testAndAlsoXY() { // 测试逻辑与运算的两个操作数都是变量的情况
    // x && y  注释说明：两个变量的逻辑与无法进一步优化，保持原样
    assertThat(
        optimize(
            Expressions.andAlso( // 创建逻辑与表达式：x && y
                Expressions.parameter(boolean.class, "x"), // 左操作数是参数表达式x
                Expressions.parameter(boolean.class, "y"))), // 右操作数是参数表达式y
        is("{\n  return x && y;\n}\n")); // 断言优化后的结果保持原样，验证变量的逻辑与运算不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testAndAlsoXX() { // 测试逻辑与运算的两个操作数相同的情况
    // x && x  注释说明：x与x的逻辑与等于x本身，可以优化为直接返回x
    ParameterExpression x = Expressions.parameter(boolean.class, "x"); // 创建Boolean类型的参数表达式x
    assertThat(optimize(Expressions.andAlso(x, x)), // 创建逻辑与表达式：x && x，然后优化
        is("{\n  return x;\n}\n")); // 断言优化后的结果是返回x，验证相同操作数的逻辑与优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOrElseTrueBool() { // 测试逻辑或运算的左操作数为true的优化
    // true || bool  注释说明：true与任何值的逻辑或等于true，可以优化为直接返回true
    assertThat(
        optimize(
            Expressions.orElse(TRUE, // 创建逻辑或表达式：true || bool
                Expressions.parameter(boolean.class, "bool"))), // 右操作数是参数表达式bool
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证左操作数为true的逻辑或优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOrElseFalseBool() { // 测试逻辑或运算的左操作数为false的优化
    // false || bool  注释说明：false与任何值的逻辑或等于该值本身，可以优化为直接返回bool
    assertThat(
        optimize(
            Expressions.orElse(FALSE, // 创建逻辑或表达式：false || bool
                Expressions.parameter(boolean.class, "bool"))), // 右操作数是参数表达式bool
        is("{\n  return bool;\n}\n")); // 断言优化后的结果是返回bool，验证左操作数为false的逻辑或优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOrElseNullBool() { // 测试逻辑或运算的左操作数为null的情况
    // null || bool  注释说明：null与bool的逻辑或无法确定结果，保持原样
    assertThat(
        optimize(
            Expressions.orElse(NULL, // 创建逻辑或表达式：null || bool
                Expressions.parameter(boolean.class, "bool"))), // 右操作数是参数表达式bool
        is("{\n  return null || bool;\n}\n")); // 断言优化后的结果保持原样，验证null值的逻辑或运算不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOrElseXY() { // 测试逻辑或运算的两个操作数都是变量的情况
    // x || y  注释说明：两个变量的逻辑或无法进一步优化，保持原样
    assertThat(
        optimize(
            Expressions.orElse( // 创建逻辑或表达式：x || y
                Expressions.parameter(boolean.class, "x"), // 左操作数是参数表达式x
                Expressions.parameter(boolean.class, "y"))), // 右操作数是参数表达式y
        is("{\n  return x || y;\n}\n")); // 断言优化后的结果保持原样，验证变量的逻辑或运算不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testOrElseXX() { // 测试逻辑或运算的两个操作数相同的情况
    // x || x  注释说明：x与x的逻辑或等于x本身，可以优化为直接返回x
    ParameterExpression x = Expressions.parameter(boolean.class, "x"); // 创建Boolean类型的参数表达式x
    assertThat(optimize(Expressions.orElse(x, x)), // 创建逻辑或表达式：x || x，然后优化
        is("{\n  return x;\n}\n")); // 断言优化后的结果是返回x，验证相同操作数的逻辑或优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualSameConst() { // 测试相同常量的相等比较优化
    // 1 == 1  注释说明：相同常量的比较结果为true，可以优化为常量true
    assertThat(optimize(Expressions.equal(ONE, Expressions.constant(1))), // 创建相等比较表达式：1 == 1，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证相同常量比较的常量折叠优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualDifferentConst() { // 测试不同常量的相等比较优化
    // 1 == 2  注释说明：不同常量的比较结果为false，可以优化为常量false
    assertThat(optimize(Expressions.equal(ONE, TWO)), // 创建相等比较表达式：1 == 2，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证不同常量比较的常量折叠优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualSameExpr() { // 测试相同表达式的相等比较优化
    // x == x  注释说明：表达式与自身的比较结果为true，可以优化为常量true
    ParameterExpression x = Expressions.parameter(int.class, "x"); // 创建int类型的参数表达式x
    assertThat(optimize(Expressions.equal(x, x)), // 创建相等比较表达式：x == x，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证相同表达式比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualDifferentExpr() { // 测试不同表达式的相等比较
    // x == y  注释说明：两个不同变量的比较无法进一步优化，保持原样
    ParameterExpression x = Expressions.parameter(int.class, "x"); // 创建int类型的参数表达式x
    ParameterExpression y = Expressions.parameter(int.class, "y"); // 创建int类型的参数表达式y
    assertThat(optimize(Expressions.equal(x, y)), // 创建相等比较表达式：x == y，然后优化
        is("{\n  return x == y;\n}\n")); // 断言优化后的结果保持原样，验证不同变量比较不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualPrimitiveNull() { // 测试基本类型与null的相等比较优化
    // (int) x == null  注释说明：基本类型永远不会等于null，可以优化为常量false
    ParameterExpression x = Expressions.parameter(int.class, "x"); // 创建int类型的参数表达式x
    assertThat(optimize(Expressions.equal(x, NULL)), // 创建相等比较表达式：x == null，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证基本类型与null比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualObjectNull() { // 测试对象类型与null的相等比较
    // (Integer) x == null  注释说明：对象类型可能与null相等，保持原样
    ParameterExpression x = Expressions.parameter(Integer.class, "x"); // 创建Integer类型的参数表达式x
    assertThat(optimize(Expressions.equal(x, NULL)), // 创建相等比较表达式：x == null，然后优化
        is("{\n  return x == null;\n}\n")); // 断言优化后的结果保持原样，验证对象类型与null比较不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualStringNull() { // 测试字符串常量与null的相等比较优化
    // "Y" == null  注释说明：非null字符串常量永远不会等于null，可以优化为常量false
    assertThat(optimize(Expressions.equal(Expressions.constant("Y"), NULL)), // 创建相等比较表达式："Y" == null，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证字符串常量与null比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualTypedNullUntypedNull() { // 测试类型化null与无类型null的相等比较优化
    // (Integer) null == null  注释说明：类型化null与无类型null相等，可以优化为常量true
    assertThat(optimize(Expressions.equal(NULL_INTEGER, NULL)), // 创建相等比较表达式：(Integer) null == null，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证类型化null与无类型null比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualUnypedNullTypedNull() { // 测试无类型null与类型化null的相等比较优化
    // null == (Integer) null  注释说明：无类型null与类型化null相等，可以优化为常量true
    assertThat(optimize(Expressions.equal(NULL, NULL_INTEGER)), // 创建相等比较表达式：null == (Integer) null，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证无类型null与类型化null比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualBoolTrue() { // 测试布尔变量与true的相等比较优化
    // x == true  注释说明：布尔变量与true的比较等价于直接返回该变量
    ParameterExpression x = Expressions.parameter(boolean.class, "x"); // 创建Boolean类型的参数表达式x
    assertThat(optimize(Expressions.equal(x, TRUE)), // 创建相等比较表达式：x == true，然后优化
        is("{\n  return x;\n}\n")); // 断言优化后的结果是返回x，验证布尔变量与true比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testEqualBoolFalse() { // 测试布尔变量与false的相等比较优化
    // x == false  注释说明：布尔变量与false的比较等价于返回该变量的非运算
    ParameterExpression x = Expressions.parameter(boolean.class, "x"); // 创建Boolean类型的参数表达式x
    assertThat(optimize(Expressions.equal(x, FALSE)), // 创建相等比较表达式：x == false，然后优化
        is("{\n  return (!x);\n}\n")); // 断言优化后的结果是返回(!x)，验证布尔变量与false比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualSameConst() { // 测试相同常量的不等比较优化
    // 1 != 1  注释说明：相同常量的不等比较结果为false，可以优化为常量false
    assertThat(optimize(Expressions.notEqual(ONE, Expressions.constant(1))), // 创建不等比较表达式：1 != 1，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证相同常量不等比较的常量折叠优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualDifferentConst() { // 测试不同常量的不等比较优化
    // 1 != 2  注释说明：不同常量的不等比较结果为true，可以优化为常量true
    assertThat(optimize(Expressions.notEqual(ONE, TWO)), // 创建不等比较表达式：1 != 2，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证不同常量不等比较的常量折叠优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualSameExpr() { // 测试相同表达式的不等比较优化
    // x != x  注释说明：表达式与自身的不等比较结果为false，可以优化为常量false
    ParameterExpression x = Expressions.parameter(int.class, "x"); // 创建int类型的参数表达式x
    assertThat(optimize(Expressions.notEqual(x, x)), // 创建不等比较表达式：x != x，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证相同表达式不等比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualDifferentExpr() { // 测试不同表达式的不等比较
    // x != y  注释说明：两个不同变量的不等比较无法进一步优化，保持原样
    ParameterExpression x = Expressions.parameter(int.class, "x"); // 创建int类型的参数表达式x
    ParameterExpression y = Expressions.parameter(int.class, "y"); // 创建int类型的参数表达式y
    assertThat(optimize(Expressions.notEqual(x, y)), // 创建不等比较表达式：x != y，然后优化
        is("{\n  return x != y;\n}\n")); // 断言优化后的结果保持原样，验证不同变量不等比较不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualPrimitiveNull() { // 测试基本类型与null的不等比较优化
    // (int) x == null  注释说明：基本类型永远不会等于null，所以不等于null永远为true
    ParameterExpression x = Expressions.parameter(int.class, "x"); // 创建int类型的参数表达式x
    assertThat(optimize(Expressions.notEqual(x, NULL)), // 创建不等比较表达式：x != null，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证基本类型与null不等比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualObjectNull() { // 测试对象类型与null的不等比较
    // (Integer) x == null  注释说明：对象类型可能与null不等，保持原样
    ParameterExpression x = Expressions.parameter(Integer.class, "x"); // 创建Integer类型的参数表达式x
    assertThat(optimize(Expressions.notEqual(x, NULL)), // 创建不等比较表达式：x != null，然后优化
        is("{\n  return x != null;\n}\n")); // 断言优化后的结果保持原样，验证对象类型与null不等比较不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualStringNull() { // 测试字符串常量与null的不等比较优化
    // "Y" != null  注释说明：非null字符串常量永远不会等于null，所以不等于null永远为true
    assertThat(optimize(Expressions.notEqual(Expressions.constant("Y"), NULL)), // 创建不等比较表达式："Y" != null，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证字符串常量与null不等比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualTypedNullUntypedNull() { // 测试类型化null与无类型null的不等比较优化
    // (Integer) null != null  注释说明：类型化null与无类型null相等，所以不等比较结果为false
    assertThat(optimize(Expressions.notEqual(NULL_INTEGER, NULL)), // 创建不等比较表达式：(Integer) null != null，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证类型化null与无类型null不等比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualUnypedNullTypedNull() { // 测试无类型null与类型化null的不等比较优化
    // null != (Integer) null  注释说明：无类型null与类型化null相等，所以不等比较结果为false
    assertThat(optimize(Expressions.notEqual(NULL, NULL_INTEGER)), // 创建不等比较表达式：null != (Integer) null，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证无类型null与类型化null不等比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualBoolTrue() { // 测试布尔变量与true的不等比较优化
    // x != true  注释说明：布尔变量与true的不等比较等价于返回该变量的非运算
    ParameterExpression x = Expressions.parameter(boolean.class, "x"); // 创建Boolean类型的参数表达式x
    assertThat(optimize(Expressions.notEqual(x, TRUE)), // 创建不等比较表达式：x != true，然后优化
        is("{\n  return (!x);\n}\n")); // 断言优化后的结果是返回(!x)，验证布尔变量与true不等比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEqualBoolFalse() { // 测试布尔变量与false的不等比较优化
    // x != false  注释说明：布尔变量与false的不等比较等价于直接返回该变量
    ParameterExpression x = Expressions.parameter(boolean.class, "x"); // 创建Boolean类型的参数表达式x
    assertThat(optimize(Expressions.notEqual(x, FALSE)), // 创建不等比较表达式：x != false，然后优化
        is("{\n  return x;\n}\n")); // 断言优化后的结果是返回x，验证布尔变量与false不等比较的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testMultipleFolding() { // 测试多层嵌套表达式的常量折叠优化
    // (1 == 2 ? 3 : 4) != (5 != 6 ? 4 : 8) ? 9 : 10  注释说明：复杂嵌套表达式的多层常量折叠
    assertThat(
        optimize(
            Expressions.condition( // 创建最外层的三元表达式：(1 == 2 ? 3 : 4) != (5 != 6 ? 4 : 8) ? 9 : 10
                Expressions.notEqual( // 条件部分：比较两个三元表达式的结果
                    Expressions.condition(Expressions.equal(ONE, TWO), // 第一个三元表达式：1 == 2 ? 3 : 4
                        Expressions.constant(3), Expressions.constant(4)), // 1 == 2为false，所以结果是4
                    Expressions.condition( // 第二个三元表达式：5 != 6 ? 4 : 8
                        Expressions.notEqual( // 条件部分：5 != 6
                            Expressions.constant(5), Expressions.constant(6)), // 5 != 6为true
                        Expressions.constant(4), Expressions.constant(8))), // 所以结果是4
                Expressions.constant(9), // 4 != 4为false，所以返回false分支的值10
                Expressions.constant(10))), // 最终结果是10
        is("{\n  return 10;\n}\n")); // 断言优化后的结果是返回10，验证多层嵌套表达式的常量折叠优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfTrue() { // 测试条件为true的if语句优化
    // if (true) {return 1}  注释说明：条件为true的if语句可以优化为直接执行if块内的语句
    assertThat(
        optimize(Expressions.ifThen(TRUE, Expressions.return_(null, ONE))), // 创建if语句：if (true) {return 1}，然后优化
        is("{\n  return 1;\n}\n")); // 断言优化后的结果是返回1，验证条件为true的if语句优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfTrueElse() { // 测试条件为true的if-else语句优化
    // if (true) {return 1} else {return 2}  注释说明：条件为true的if-else语句可以优化为直接执行if块内的语句
    assertThat(
        optimize(
            Expressions.ifThenElse(TRUE, // 创建if-else语句：if (true) {return 1} else {return 2}，然后优化
                Expressions.return_(null, ONE), // if块：return 1
                Expressions.return_(null, TWO))), // else块：return 2
        is("{\n  return 1;\n}\n")); // 断言优化后的结果是返回1，验证条件为true的if-else语句优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfFalse() { // 测试条件为false的if语句优化
    // if (false) {return 1}  注释说明：条件为false的if语句可以优化为空语句（死代码消除）
    assertThat(
        optimize(Expressions.ifThen(FALSE, Expressions.return_(null, ONE))), // 创建if语句：if (false) {return 1}，然后优化
        is("{}")); // 断言优化后的结果是空代码块，验证条件为false的if语句优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfFalseElse() { // 测试条件为false的if-else语句优化
    // if (false) {return 1} else {return 2}  注释说明：条件为false的if-else语句可以优化为直接执行else块内的语句
    assertThat(
        optimize(
            Expressions.ifThenElse(FALSE, // 创建if-else语句：if (false) {return 1} else {return 2}，然后优化
                Expressions.return_(null, ONE), // if块：return 1
                Expressions.return_(null, TWO))), // else块：return 2
        is("{\n  return 2;\n}\n")); // 断言优化后的结果是返回2，验证条件为false的if-else语句优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfBoolTrue() { // 测试if-else if语句中else if条件为true的优化
    // if (bool) {return 1} else if (true) {return 2}  注释说明：else if条件为true，可以优化为else语句块
    Expression bool = Expressions.parameter(boolean.class, "bool"); // 创建Boolean类型的参数表达式bool
    assertThat(
        optimize(
            Expressions.ifThenElse(bool, // 创建if-else if语句：if (bool) {return 1} else if (true) {return 2}，然后优化
                Expressions.return_(null, ONE), // if块：return 1
                TRUE, // else if条件：true
                Expressions.return_(null, TWO))), // else if块：return 2
        is("{\n" // 验证优化后的结果
            + "  if (bool) {\n" // 保持if语句
            + "    return 1;\n" // if块内容
            + "  } else {\n" // else if优化为else
            + "    return 2;\n" // else块内容
            + "  }\n"
            + "}\n")); // 验证else if条件为true的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfBoolTrueElse() { // 测试if-else if-else语句中else if条件为true的优化
    // if (bool) {return 1} else if (true) {return 2} else {return 3}  注释说明：else if条件为true，最后的else会被消除
    Expression bool = Expressions.parameter(boolean.class, "bool"); // 创建Boolean类型的参数表达式bool
    assertThat(
        optimize(
            Expressions.ifThenElse(bool, // 创建if-else if-else语句：if (bool) {return 1} else if (true) {return 2} else {return 3}，然后优化
                Expressions.return_(null, ONE), // if块：return 1
                TRUE, // else if条件：true
                Expressions.return_(null, TWO), // else if块：return 2
                Expressions.return_(null, THREE))), // else块：return 3
        is("{\n" // 验证优化后的结果
            + "  if (bool) {\n" // 保持if语句
            + "    return 1;\n" // if块内容
            + "  } else {\n" // else if优化为else，最后的else被消除（死代码）
            + "    return 2;\n" // else块内容
            + "  }\n"
            + "}\n")); // 验证else if条件为true且存在else的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfBoolFalse() { // 测试if-else if语句中else if条件为false的优化
    // if (bool) {return 1} else if (false) {return 2}  注释说明：else if条件为false，可以消除该分支（死代码消除）
    Expression bool = Expressions.parameter(boolean.class, "bool"); // 创建Boolean类型的参数表达式bool
    assertThat(
        optimize(
            Expressions.ifThenElse(bool, // 创建if-else if语句：if (bool) {return 1} else if (false) {return 2}，然后优化
                Expressions.return_(null, ONE), // if块：return 1
                FALSE, // else if条件：false
                Expressions.return_(null, TWO))), // else if块：return 2
        is("{\n" // 验证优化后的结果
            + "  if (bool) {\n" // 保持if语句
            + "    return 1;\n" // if块内容
            + "  }\n" // else if被消除（死代码）
            + "}\n")); // 验证else if条件为false的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfBoolFalseElse() { // 测试if-else if-else语句中else if条件为false的优化
    // if (bool) {return 1} else if (false) {return 2} else {return 3}  注释说明：else if条件为false，可以优化为if-else语句
    Expression bool = Expressions.parameter(boolean.class, "bool"); // 创建Boolean类型的参数表达式bool
    assertThat(
        optimize(
            Expressions.ifThenElse(bool, // 创建if-else if-else语句：if (bool) {return 1} else if (false) {return 2} else {return 3}，然后优化
                Expressions.return_(null, ONE), // if块：return 1
                FALSE, // else if条件：false
                Expressions.return_(null, TWO), // else if块：return 2
                Expressions.return_(null, THREE))), // else块：return 3
        is("{\n" // 验证优化后的结果
            + "  if (bool) {\n" // 保持if语句
            + "    return 1;\n" // if块内容
            + "  } else {\n" // else if优化为else
            + "    return 3;\n" // else块内容（原else块）
            + "  }\n"
            + "}\n")); // 验证else if条件为false且存在else的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testConditionalIfBoolFalseTrue() { // 测试复杂的if-else if-else if-else语句优化
    // if (bool) {1} else if (false) {2} if (true) {4} else {5}  注释说明：多个else if条件优化，false分支消除，true分支保留
    Expression bool = Expressions.parameter(boolean.class, "bool"); // 创建Boolean类型的参数表达式bool
    assertThat(
        optimize(
            Expressions.ifThenElse(bool, // 创建复杂的if-else if-else if-else语句，然后优化
                Expressions.return_(null, ONE), // if块：return 1
                FALSE, // 第一个else if条件：false
                Expressions.return_(null, TWO), // 第一个else if块：return 2
                TRUE, // 第二个else if条件：true
                Expressions.return_(null, FOUR), // 第二个else if块：return 4
                Expressions.return_(null, Expressions.constant(5)))), // else块：return 5
        is("{\n" // 验证优化后的结果
            + "  if (bool) {\n" // 保持if语句
            + "    return 1;\n" // if块内容
            + "  } else {\n" // 第一个else if（false）被消除，第二个else if（true）优化为else
            + "    return 4;\n" // else块内容（原第二个else if块）
            + "  }\n" // 最后的else被消除（死代码）
            + "}\n")); // 验证复杂条件语句的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testCastIntToShort() { // 测试int到short的类型转换优化
    // return (short) 1 --> return (short) 1  注释说明：int到short的转换需要保留，因为可能导致精度丢失
    assertThat(optimize(Expressions.convert_(ONE, short.class)), // 创建类型转换表达式：(short) 1，然后优化
        is("{\n  return (short)1;\n}\n")); // 断言优化后的结果保留类型转换，验证int到short转换不被不当优化
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testCastIntToInt() { // 测试int到int的类型转换优化
    // return (int) 1 --> return 1  注释说明：相同类型的转换可以消除
    assertThat(optimize(Expressions.convert_(ONE, int.class)), // 创建类型转换表达式：(int) 1，然后优化
        is("{\n  return 1;\n}\n")); // 断言优化后的结果消除了类型转换，验证相同类型转换的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testCastIntToLong() { // 测试int到long的类型转换优化
    // return (long) 1 --> return 1L  注释说明：int到long的转换需要保留，但常量值会相应转换
    assertThat(optimize(Expressions.convert_(ONE, long.class)), // 创建类型转换表达式：(long) 1，然后优化
        is("{\n  return 1L;\n}\n")); // 断言优化后的结果保留类型转换但常量值变为long类型，验证int到long转换的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotTrue() { // 测试对true的非运算优化
    // !true -> false  注释说明：对true的非运算等于false，可以优化为常量false
    assertThat(optimize(Expressions.not(TRUE)), // 创建非运算表达式：!true，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证对true的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotFalse() { // 测试对false的非运算优化
    // !false -> true  注释说明：对false的非运算等于true，可以优化为常量true
    assertThat(optimize(Expressions.not(FALSE)), // 创建非运算表达式：!false，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证对false的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotNotA() { // 测试双重非运算的优化
    // !!a -> a  注释说明：双重非运算等于原值，可以优化为直接返回原值
    assertThat(optimize(Expressions.not(Expressions.not(bool("a")))), // 创建双重非运算表达式：!!a，然后优化
        is("{\n  return a;\n}\n")); // 断言优化后的结果是返回a，验证双重非运算的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotEq() { // 测试对相等比较的非运算优化
    // !(a == b) -> a != b  注释说明：对相等比较的非运算等于不等比较
    assertThat(
        optimize(Expressions.not(Expressions.equal(bool("a"), bool("b")))), // 创建非运算表达式：!(a == b)，然后优化
        is("{\n  return a != b;\n}\n")); // 断言优化后的结果是 a != b，验证对相等比较的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotNeq() { // 测试对不等比较的非运算优化
    // !(a != b) -> a == b  注释说明：对不等比较的非运算等于相等比较
    assertThat(
        optimize(
            Expressions.not(Expressions.notEqual(bool("a"), bool("b")))), // 创建非运算表达式：!(a != b)，然后优化
        is("{\n  return a == b;\n}\n")); // 断言优化后的结果是 a == b，验证对不等比较的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotGt() { // 测试对大于比较的非运算优化
    // !(a > b) -> a <= b  注释说明：对大于比较的非运算等于小于等于比较
    assertThat(
        optimize(
            Expressions.not(Expressions.greaterThan(bool("a"), bool("b")))), // 创建非运算表达式：!(a > b)，然后优化
        is("{\n  return a <= b;\n}\n")); // 断言优化后的结果是 a <= b，验证对大于比较的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotGte() { // 测试对大于等于比较的非运算优化
    // !(a >= b) -> a < b  注释说明：对大于等于比较的非运算等于小于比较
    assertThat(
        optimize(
            Expressions.not(
                Expressions.greaterThanOrEqual(bool("a"), bool("b")))), // 创建非运算表达式：!(a >= b)，然后优化
        is("{\n  return a < b;\n}\n")); // 断言优化后的结果是 a < b，验证对大于等于比较的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotLt() { // 测试对小于比较的非运算优化
    // !(a < b) -> a >= b  注释说明：对小于比较的非运算等于大于等于比较
    assertThat(
        optimize(
            Expressions.not(Expressions.lessThan(bool("a"), bool("b")))), // 创建非运算表达式：!(a < b)，然后优化
        is("{\n  return a >= b;\n}\n")); // 断言优化后的结果是 a >= b，验证对小于比较的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testNotLte() { // 测试对小于等于比较的非运算优化
    // !(a <= b) -> a > b  注释说明：对小于等于比较的非运算等于大于比较
    assertThat(
        optimize(
            Expressions.not(
                Expressions.lessThanOrEqual(bool("a"), bool("b")))), // 创建非运算表达式：!(a <= b)，然后优化
        is("{\n  return a > b;\n}\n")); // 断言优化后的结果是 a > b，验证对小于等于比较的非运算优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void booleanValueOfTrue() { // 测试Boolean.valueOf(true)的优化
    // Boolean.valueOf(true) -> true  注释说明：Boolean.valueOf(true)可以优化为原始类型true
    assertThat(optimize(Expressions.call(Boolean.class, "valueOf", TRUE)), // 创建方法调用表达式：Boolean.valueOf(true)，然后优化
        is("{\n  return true;\n}\n")); // 断言优化后的结果是返回true，验证Boolean.valueOf(true)的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testBooleanValueOfFalse() { // 测试Boolean.valueOf(false)的优化
    // Boolean.valueOf(false) -> false  注释说明：Boolean.valueOf(false)可以优化为原始类型false
    assertThat(optimize(Expressions.call(Boolean.class, "valueOf", FALSE)), // 创建方法调用表达式：Boolean.valueOf(false)，然后优化
        is("{\n  return false;\n}\n")); // 断言优化后的结果是返回false，验证Boolean.valueOf(false)的优化是否正确
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法
  void testAssign() { // 测试赋值语句的优化，验证不会错误地消除变量赋值
    // long x = 0;  注释说明：声明并初始化变量x为0
    // final long y = System.currentTimeMillis();  注释说明：声明final变量y，初始化为当前时间毫秒数
    // if (System.nanoTime() > 0) {  注释说明：如果纳秒时间大于0
    //   x = y;  注释说明：则将y的值赋给x
    // }  注释说明：if语句结束
    // System.out.println(x);  注释说明：输出x的值
    //
    // In bug https://github.com/julianhyde/linq4j/issues/27, this was  注释说明：在bug #27中，这段代码被错误地优化为
    // incorrectly optimized to  注释说明：错误优化的结果如下
    //
    // if (System.nanoTime() > 0L) {  注释说明：如果纳秒时间大于0
    //    System.currentTimeMillis();  注释说明：只调用方法但不赋值（错误！）
    // }  注释说明：if语句结束
    // System.out.println(0L);  注释说明：输出0L（错误！应该是x的值）
    final ParameterExpression x_ = Expressions.parameter(long.class, "x"); // 创建long类型的参数表达式x_，用于声明变量x
    final ParameterExpression y_ = Expressions.parameter(long.class, "y"); // 创建long类型的参数表达式y_，用于声明变量y
    final Method mT = Linq4j.getMethod("java.lang.System", "currentTimeMillis"); // 使用反射获取System.currentTimeMillis方法
    final Method mNano = Linq4j.getMethod("java.lang.System", "nanoTime"); // 使用反射获取System.nanoTime方法
    final ConstantExpression zero = Expressions.constant(0L); // 创建常量表达式0L
    assertThat(
        optimize(
            Expressions.block( // 创建块语句，包含变量声明、条件判断和输出语句
                Expressions.declare(0, x_, zero), // 声明变量x，初始化为0L
                Expressions.declare(Modifier.FINAL, y_, Expressions.call(mT)), // 声明final变量y，初始化为System.currentTimeMillis()的返回值
                Expressions.ifThen( // 创建if语句
                    Expressions.greaterThan(Expressions.call(mNano), zero), // 条件：System.nanoTime() > 0L
                    Expressions.statement(Expressions.assign(x_, y_))), // if块：x = y
                Expressions.statement( // 创建语句表达式
                    Expressions.call( // 创建方法调用表达式
                        Expressions.field(null, System.class, "out"), // 获取System.out字段
                        "println", // 调用println方法
                        x_)))), // 参数是变量x
        equalTo("{\n" // 验证优化后的结果，确保赋值语句不被错误消除
            + "  long x = 0L;\n" // x的声明保持不变
            + "  if (System.nanoTime() > 0L) {\n" // if语句保持不变
            + "    x = System.currentTimeMillis();\n" // 赋值语句保持不变（关键！）
            + "  }\n" // if语句结束
            + "  System.out.println(x);\n" // 输出语句保持不变，输出的是x而不是0L
            + "}\n")); // 验证赋值语句优化是否正确，确保不会错误地消除变量赋值
  }

  @Test // 使用JUnit5的@Test注解，标记这是一个测试方法

    void testAssign2() { // 测试赋值语句的优化，验证不会错误地合并多次方法调用

      // long x = 0;  注释说明：声明并初始化变量x为0

      // final long y = System.currentTimeMillis();  注释说明：声明final变量y，初始化为当前时间毫秒数

      // if (System.currentTimeMillis() > 0) {  注释说明：如果当前时间毫秒数大于0

      //   x = y;  注释说明：则将y的值赋给x

      // }  注释说明：if语句结束

      //

      // Make sure we don't fold two calls to System.currentTimeMillis into one.  注释说明：确保不会将两次System.currentTimeMillis调用合并为一次

      final ParameterExpression x_ = Expressions.parameter(long.class, "x"); // 创建long类型的参数表达式x_，用于声明变量x

      final ParameterExpression y_ = Expressions.parameter(long.class, "y"); // 创建long类型的参数表达式y_，用于声明变量y

      final Method mT = Linq4j.getMethod("java.lang.System", "currentTimeMillis"); // 使用反射获取System.currentTimeMillis方法

      final ConstantExpression zero = Expressions.constant(0L); // 创建常量表达式0L

      assertThat(

          optimize( // 优化块语句

  

              Expressions.block( // 创建块语句，包含变量声明和条件判断

                  Expressions.declare(0, x_, zero), // 声明变量x，初始化为0L

                  Expressions.declare(Modifier.FINAL, y_, Expressions.call(mT)), // 声明final变量y，初始化为System.currentTimeMillis()的调用（第一次）

                  Expressions.ifThen( // 创建if语句

                      Expressions.greaterThan(Expressions.call(mT), zero), // 条件：System.currentTimeMillis() > 0L（第二次调用，不能与第一次合并！）

                      Expressions.statement(Expressions.assign(x_, y_))))), // if块：x = y

          equalTo("{\n" // 验证优化后的结果，确保两次方法调用不被错误合并

              + "  long x = 0L;\n" // x的声明保持不变

              + "  if (System.currentTimeMillis() > 0L) {\n" // if语句保持不变，条件中的System.currentTimeMillis()调用保持不变

              + "    x = System.currentTimeMillis();\n" // 赋值语句保持不变，使用的是y的值（第一次调用的结果）

              + "  }\n" // if语句结束

              + "}\n")); // 验证赋值语句优化是否正确，确保不会错误地合并两次方法调用

    }

  } // 类定义结束
