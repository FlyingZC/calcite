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
// Apache许可证头，声明版权和使用许可
package org.apache.calcite.linq4j.test; // 定义包名，位于org.apache.calcite.linq4j.test包下

// 导入二进制表达式类，用于表示二元运算表达式（如加、减、乘、除等）
import org.apache.calcite.linq4j.tree.BinaryExpression;
// 导入代码块构建器类，用于构建Java代码块（由多个语句组成的代码块）
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入表达式基类，所有表达式类型的父类
import org.apache.calcite.linq4j.tree.Expression;
// 导入表达式类型枚举，定义各种表达式类型（如Add、Subtract、Multiply等）
import org.apache.calcite.linq4j.tree.ExpressionType;
// 导入表达式工具类，提供创建各种表达式的静态工厂方法
import org.apache.calcite.linq4j.tree.Expressions;
// 导入优化穿梭器类，用于遍历和优化表达式树
import org.apache.calcite.linq4j.tree.OptimizeShuttle;
// 导入参数表达式类，用于表示方法参数或局部变量
import org.apache.calcite.linq4j.tree.ParameterExpression;
// 导入穿梭器接口，表达式树访问者模式的基接口
import org.apache.calcite.linq4j.tree.Shuttle;

// 导入可空注解，用于标记可能为null的参数或返回值
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入BeforeEach注解，用于在每个测试方法执行前执行初始化操作
import org.junit.jupiter.api.BeforeEach;
// 导入Test注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入Method类，用于反射获取方法信息
import java.lang.reflect.Method;
// 导入Function接口，Java 8函数式接口，表示接受一个参数并产生结果的函数
import java.util.function.Function;

// 静态导入常量FOUR（值为4），用于测试
import static org.apache.calcite.linq4j.test.BlockBuilderBase.FOUR;
// 静态导入常量ONE（值为1），用于测试
import static org.apache.calcite.linq4j.test.BlockBuilderBase.ONE;
// 静态导入常量TWO（值为2），用于测试
import static org.apache.calcite.linq4j.test.BlockBuilderBase.TWO;

// 静态导入is匹配器，用于断言值相等
import static org.hamcrest.CoreMatchers.is;
// 静态导入assertThat方法，用于执行断言
import static org.hamcrest.MatcherAssert.assertThat;
// 静态导入hasToString匹配器，用于断言对象的toString()输出
import static org.hamcrest.Matchers.hasToString;

/**
 * Tests BlockBuilder. // 测试BlockBuilder类的单元测试类
 * // BlockBuilder是Calcite中用于动态生成Java代码块的核心工具类
 * // 它可以构建包含变量声明、赋值、控制流等语句的代码块
 * // 主要用于将SQL查询转换为可执行的Java代码
 */
class BlockBuilderTest { // BlockBuilderTest类：测试BlockBuilder功能的测试类
  BlockBuilder b; // 成员变量：BlockBuilder实例，用于在测试方法中构建代码块

  @BeforeEach // 注解：在每个测试方法执行前运行，用于初始化测试环境
  public void prepareBuilder() { // prepareBuilder方法：初始化BlockBuilder实例
    b = new BlockBuilder(true); // 创建新的BlockBuilder实例，参数true表示启用优化
  }

  @Test // 注解：标记这是一个测试方法
  void testReuseExpressionsFromUpperLevel() { // 测试方法：测试从上层作用域重用表达式
    // 在外层BlockBuilder中添加表达式"1 + 2"，并命名为"x"
    // BlockBuilder会检测到这个表达式与之前添加的相同表达式，从而重用变量x
    Expression x = b.append("x", Expressions.add(ONE, TWO)); // 创建加法表达式1+2，添加到BlockBuilder并返回变量x
    // 创建嵌套的BlockBuilder，传入true启用优化，传入b作为父BlockBuilder
    // 这样嵌套BlockBuilder可以访问和重用父BlockBuilder中的表达式
    BlockBuilder nested = new BlockBuilder(true, b); // 创建嵌套BlockBuilder，可以访问父作用域的表达式
    // 在嵌套BlockBuilder中添加表达式"1 + 2"，命名为"y"
    // 由于父BlockBuilder中已经有相同的表达式，这里会重用父BlockBuilder中的变量x
    Expression y = nested.append("y", Expressions.add(ONE, TWO)); // 添加表达式，实际会重用父BlockBuilder中的变量x
    // 在嵌套BlockBuilder中添加return语句，返回y + y（即x + x）
    nested.add(Expressions.return_(null, Expressions.add(y, y))); // 添加return语句，返回y+y的值
    // 将嵌套BlockBuilder生成的代码块添加到外层BlockBuilder中
    b.add(nested.toBlock()); // 将嵌套代码块添加到外层BlockBuilder
    // 验证生成的代码块是否符合预期
    // 期望的代码块是：声明final int x = 1 + 2; 然后在嵌套代码块中return x + x;
    assertThat(b.toBlock(), // 断言生成的代码块字符串等于期望值
        hasToString("{\n" // 期望代码块以左花括号开始
            + "  final int x = 1 + 2;\n" // 声明final变量x，值为1+2
            + "  {\n" // 嵌套代码块开始
            + "    return x + x;\n" // 返回x+x（重用了外层的变量x）
            + "  }\n" // 嵌套代码块结束
            + "}\n")); // 代码块结束
  }

  @Test // 注解：标记这是一个测试方法
  void testTestCustomOptimizer() { // 测试方法：测试自定义优化器
    // 创建匿名子类覆盖createOptimizeShuttle方法，提供自定义的优化逻辑
    BlockBuilder b = new BlockBuilder() { // 创建BlockBuilder匿名子类
      @Override // 注解：覆盖父类方法
      protected Shuttle createOptimizeShuttle() { // 创建自定义优化穿梭器
        return new OptimizeShuttle() { // 返回自定义的OptimizeShuttle实例
          @Override // 注解：覆盖父类方法
          public Expression visit(BinaryExpression binary, // visit方法：访问二元表达式节点
              Expression expression0, Expression expression1) { // 表达式的两个操作数
            // 检查是否是加法表达式，且左操作数是1，右操作数是2
            if (binary.getNodeType() == ExpressionType.Add // 如果是加法表达式
                && ONE.equals(expression0) && TWO.equals(expression1)) { // 且操作数为1和2
              return FOUR; // 直接返回常量4，优化1+2为4
            }
            // 其他情况使用父类的默认处理逻辑
            return super.visit(binary, expression0, expression1); // 调用父类方法继续处理
          }
        };
      }
    };
    // 添加return语句，返回1+2的结果
    b.add(Expressions.return_(null, Expressions.add(ONE, TWO))); // 添加return 1+2语句
    // 验证生成的代码块
    // 由于自定义优化器将1+2优化为4，所以生成的代码应该是return 4;
    assertThat(b.toBlock(), hasToString("{\n  return 4;\n}\n")); // 断言生成的代码为return 4;
  }

  // 私有辅助方法：创建包含同名变量的嵌套代码块
  // 用于测试BlockBuilder如何处理变量名冲突
  private BlockBuilder appendBlockWithSameVariable( // 方法签名：接受两个初始化表达式参数
      @Nullable Expression initializer1, @Nullable Expression initializer2) { // 参数1和参数2可能为null
    BlockBuilder outer = new BlockBuilder(); // 创建外层BlockBuilder
    ParameterExpression outerX = Expressions.parameter(int.class, "x"); // 创建外层参数表达式x，类型为int
    outer.add(Expressions.declare(0, outerX, initializer1)); // 在外层声明变量x，使用initializer1初始化
    outer.add(Expressions.statement(Expressions.assign(outerX, Expressions.constant(1)))); // 将x赋值为1

    BlockBuilder inner = new BlockBuilder(); // 创建内层BlockBuilder
    ParameterExpression innerX = Expressions.parameter(int.class, "x"); // 创建内层参数表达式x，类型为int
    inner.add(Expressions.declare(0, innerX, initializer2)); // 在内层声明变量x，使用initializer2初始化
    inner.add(Expressions.statement(Expressions.assign(innerX, Expressions.constant(42)))); // 将x赋值为42
    inner.add(Expressions.return_(null, innerX)); // 添加return语句，返回内层的x
    outer.append("x", inner.toBlock()); // 将内层代码块添加到外层，变量名为"x"
    return outer; // 返回外层BlockBuilder
  }

  @Test // 注解：标记这是一个测试方法
  void testRenameVariablesWithEmptyInitializer() { // 测试方法：测试无初始化器时的变量重命名
    // 调用辅助方法创建嵌套代码块，两个初始化器都为null
    BlockBuilder outer = appendBlockWithSameVariable(null, null); // 创建无初始化器的嵌套代码块

    // 验证生成的代码块
    // 由于内外层都有变量x，BlockBuilder应该将内层的x重命名为x0以避免冲突
    assertThat("x in the second block should be renamed to avoid name clash", // 断言消息
        Expressions.toString(outer.toBlock()), // 获取生成的代码块字符串
        is("{\n" // 期望代码块开始
            + "  int x;\n" // 外层声明变量x，无初始化
            + "  x = 1;\n" // 将x赋值为1
            + "  int x0;\n" // 内层变量x被重命名为x0，无初始化
            + "  x0 = 42;\n" // 将x0赋值为42
            + "}\n")); // 代码块结束
  }

  @Test // 注解：标记这是一个测试方法
  void testRenameVariablesWithInitializer() { // 测试方法：测试有初始化器时的变量重命名
    // 调用辅助方法创建嵌套代码块，外层初始化为7，内层初始化为8
    BlockBuilder outer = // 创建外层BlockBuilder
        appendBlockWithSameVariable(Expressions.constant(7), // 外层初始化器为7
            Expressions.constant(8)); // 内层初始化器为8

    // 验证生成的代码块
    // 由于内外层都有变量x，BlockBuilder应该将内层的x重命名为x0以避免冲突
    assertThat("x in the second block should be renamed to avoid name clash", // 断言消息
        Expressions.toString(outer.toBlock()), // 获取生成的代码块字符串
        is("{\n" // 期望代码块开始
            + "  int x = 7;\n" // 外层声明变量x，初始化为7
            + "  x = 1;\n" // 将x赋值为1
            + "  int x0 = 8;\n" // 内层变量x被重命名为x0，初始化为8
            + "  x0 = 42;\n" // 将x0赋值为42
            + "}\n")); // 代码块结束
  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2413">[CALCITE-2413] // JIRA问题链接
   * RexToLixTranslator does not generate correct declaration of Methods with // 问题描述
   * generic return types</a>. // 泛型返回类型的方法声明不正确
   */
  @Test // 注解：标记这是一个测试方法
  void genericMethodCall() throws NoSuchMethodException { // 测试方法：测试泛型方法调用
    BlockBuilder bb = new BlockBuilder(); // 创建BlockBuilder实例
    // 添加一个方法调用表达式到BlockBuilder
    // 调用Identity类的apply方法，传入参数"test"
    // Identity是一个泛型类，apply方法接受Object类型参数，返回Object类型
    bb.append("_i", // 将结果赋值给变量_i
        Expressions.call( // 创建方法调用表达式
            Expressions.new_(Identity.class), // 创建Identity类的实例
            Identity.class.getMethod("apply", Object.class), // 获取apply方法（通过反射）
            Expressions.constant("test"))); // 传入常量参数"test"

    // 验证生成的代码块
    // 期望生成的代码正确处理泛型方法调用
    assertThat( // 断言开始
        Expressions.toString(bb.toBlock()), is("{\n" // 期望代码块字符串
            + "  final Object _i = new org.apache.calcite.linq4j.test.BlockBuilderTest.Identity()" // 声明final变量_i
            + ".apply(\"test\");\n" // 调用apply方法，传入"test"
            + "}\n")); // 代码块结束

  }

  /** Test case for // 测试用例说明
   * <a href="https://issues.apache.org/jira/browse/CALCITE-2611">[CALCITE-2611] // JIRA问题链接
   * Linq4j code generation failure if one side of an OR contains // 问题描述
   * UNKNOWN</a>. // OR表达式包含UNKNOWN时代码生成失败
   */
  @Test // 注解：标记这是一个测试方法
  void testOptimizeBoxedFalseEqNull() { // 测试方法：测试优化装箱的false等于null
    BlockBuilder outer = new BlockBuilder(); // 创建BlockBuilder实例
    // 添加一个相等比较表达式：Boolean.FALSE == null
    // BOXED_FALSE_EXPR是OptimizeShuttle中定义的常量，表示装箱的false值
    outer.append( // 添加表达式到BlockBuilder
        Expressions.equal( // 创建相等比较表达式
            OptimizeShuttle.BOXED_FALSE_EXPR, // 左操作数：装箱的false
            Expressions.constant(null))); // 右操作数：null

    // 验证生成的代码块
    // 期望BlockBuilder的优化器将Boolean.FALSE == null优化为false
    assertThat("Expected to optimize Boolean.FALSE = null to false", // 断言消息
        Expressions.toString(outer.toBlock()), // 获取生成的代码块字符串
        is("{\n" // 期望代码块开始
            + "  return false;\n" // 期望生成的代码直接返回false
            + "}\n")); // 代码块结束
  }

  /**
   * Class with generics to validate if {@link Expressions#call(Method, Expression...)} works. // 带泛型的类，用于验证Expressions.call方法是否正常工作
   *
   * @param <I> result type // 泛型参数I：结果类型
   */
  static class Identity<I> implements Function<I, I> { // Identity类：实现Function接口的恒等函数类
    @Override // 注解：覆盖接口方法
    public I apply(I i) { // apply方法：接受参数i，返回i本身
      return i; // 直接返回输入参数，实现恒等函数
    }
  }

} // 类结束
