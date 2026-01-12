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
package org.apache.calcite.adapter.enumerable; // 声明包名，表示该类属于org.apache.calcite.adapter.enumerable包

import org.apache.calcite.linq4j.function.Function1; // 导入Function1接口，表示一个接受一个参数并返回结果的函数
import org.apache.calcite.linq4j.tree.ConstantExpression; // 导入ConstantExpression类，表示常量表达式节点
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.FunctionExpression; // 导入FunctionExpression类，表示函数表达式节点
import org.apache.calcite.linq4j.tree.Node; // 导入Node接口，表示表达式树的节点基类
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression类，表示参数表达式节点
import org.apache.calcite.linq4j.tree.UnaryExpression; // 导入UnaryExpression类，表示一元表达式节点

import org.hamcrest.BaseMatcher; // 导入BaseMatcher类，用于自定义匹配器
import org.hamcrest.Description; // 导入Description类，用于描述匹配失败的信息
import org.junit.jupiter.api.Test; // 导入Test注解，用于标记测试方法

import java.lang.reflect.Type; // 导入Type接口，表示Java类型
import java.util.Arrays; // 导入Arrays工具类，用于数组操作
import java.util.Collections; // 导入Collections工具类，用于集合操作
import java.util.HashSet; // 导入HashSet类，表示哈希集合
import java.util.List; // 导入List接口，表示列表
import java.util.Objects; // 导入Objects工具类，用于对象操作
import java.util.Set; // 导入Set接口，表示集合

import static org.hamcrest.CoreMatchers.containsString; // 导入containsString匹配器，用于检查字符串包含
import static org.hamcrest.MatcherAssert.assertThat; // 导入断言方法，用于测试验证

/**
 * Test for
 * {@link org.apache.calcite.adapter.enumerable.EnumerableRelImplementor.TypeFinder}.
 * // 该测试类用于测试TypeFinder的功能，TypeFinder是一个访问者模式的实现，用于从表达式树中提取所有使用的Java类型
 * // TypeFinder在EnumerableRelImplementor中用于收集表达式树中引用的所有类型信息，这对于代码生成和类型检查非常重要
 */
class TypeFinderTest { // 定义测试类TypeFinderTest，用于测试TypeFinder类型查找器的功能

  @Test void testConstantExpression() { // 测试常量表达式的类型提取
    ConstantExpression expr = Expressions.constant(null, Integer.class); // 创建一个常量表达式，值为null，类型为Integer.class
    assertJavaCodeContains("(Integer) null\n", expr); // 验证生成的Java代码包含"(Integer) null"
    assertTypeContains(Integer.class, expr); // 验证从表达式中提取的类型包含Integer.class
  }

  @Test void testConvertExpression() { // 测试类型转换表达式的类型提取
    UnaryExpression expr = Expressions.convert_(Expressions.new_(String.class), Object.class); // 创建一个转换表达式，将new String()转换为Object类型
    assertJavaCodeContains("(Object) new String()\n", expr); // 验证生成的Java代码包含"(Object) new String()"
    assertTypeContains(Arrays.asList(String.class, Object.class), expr); // 验证从表达式中提取的类型包含String.class和Object.class
  }

  @Test void testFunctionExpression1() { // 测试函数表达式1的类型提取（返回参数本身）
    ParameterExpression param = Expressions.parameter(String.class, "input"); // 创建一个参数表达式，类型为String，参数名为"input"
    FunctionExpression expr = // 创建一个函数表达式
        Expressions.lambda(Function1.class, // 指定函数类型为Function1（接受一个参数）
            Expressions.block(Expressions.return_(null, param)), // 创建代码块，返回参数param
            param); // 指定函数的参数
    assertJavaCodeContains("new org.apache.calcite.linq4j.function.Function1() {\n" // 验证生成的Java代码包含Function1匿名类
        + "  public String apply(String input) {\n" // 验证包含apply方法，接受String参数，返回String
        + "    return input;\n" // 验证返回input参数
        + "  }\n" // 验证方法结束
        + "  public Object apply(Object input) {\n" // 验证包含泛型apply方法，接受Object参数
        + "    return apply(\n" // 验证调用类型化的apply方法
        + "      (String) input);\n" // 验证将input转换为String类型
        + "  }\n" // 验证方法结束
        + "}\n", expr); // 验证匿名类结束
    assertTypeContains(String.class, expr); // 验证从表达式中提取的类型包含String.class
  }

  @Test void testFunctionExpression2() { // 测试函数表达式2的类型提取（返回常量）
    FunctionExpression expr = // 创建一个函数表达式
        Expressions.lambda(Function1.class, // 指定函数类型为Function1（接受一个参数）
            Expressions.block( // 创建代码块
                Expressions.return_(null, Expressions.constant(1L, Long.class))), // 返回一个Long类型的常量1L
        Expressions.parameter(String.class, "input")); // 指定函数的参数为String类型的"input"
    assertJavaCodeContains("new org.apache.calcite.linq4j.function.Function1() {\n" // 验证生成的Java代码包含Function1匿名类
        + "  public Long apply(String input) {\n" // 验证包含apply方法，接受String参数，返回Long
        + "    return Long.valueOf(1L);\n" // 验证返回Long.valueOf(1L)
        + "  }\n" // 验证方法结束
        + "  public Object apply(Object input) {\n" // 验证包含泛型apply方法，接受Object参数
        + "    return apply(\n" // 验证调用类型化的apply方法
        + "      (String) input);\n" // 验证将input转换为String类型
        + "  }\n" // 验证方法结束
        + "}\n", expr); // 验证匿名类结束
    assertTypeContains(Arrays.asList(String.class, Long.class), expr); // 验证从表达式中提取的类型包含String.class和Long.class
  }
  private void assertJavaCodeContains(String expected, Node node) { // 辅助方法：验证单个节点生成的Java代码包含预期字符串
    assertJavaCodeContains(expected, Collections.singletonList(node)); // 将节点包装成列表后调用重载方法
  }

  private void assertJavaCodeContains(String expected, List<Node> nodes) { // 辅助方法：验证节点列表生成的Java代码包含预期字符串
    final String javaCode = Expressions.toString(nodes, "\n", false); // 将节点列表转换为Java代码字符串，换行符为"\n"，不缩进
    assertThat(javaCode, containsString(expected)); // 使用Hamcrest断言验证生成的Java代码包含预期字符串
  }

  private void assertTypeContains(Type expectedType, Node node) { // 辅助方法：验证单个节点包含预期的类型
    assertTypeContains(Collections.singletonList(expectedType), // 将预期类型包装成列表
        Collections.singletonList(node)); // 将节点包装成列表后调用重载方法
  }

  private void assertTypeContains(List<Type> expectedType, Node node) { // 辅助方法：验证单个节点包含预期的类型列表
    assertTypeContains(expectedType, // 传入预期类型列表
        Collections.singletonList(node)); // 将节点包装成列表后调用重载方法
  }

  private void assertTypeContains(List<Type> expectedTypes, List<Node> nodes) { // 核心辅助方法：验证节点列表包含所有预期的类型
    final HashSet<Type> types = new HashSet<>(); // 创建一个空的HashSet用于存储从节点中提取的类型
    final EnumerableRelImplementor.TypeFinder typeFinder = // 创建TypeFinder实例，传入types集合用于收集类型
        new EnumerableRelImplementor.TypeFinder(types); // TypeFinder会遍历表达式树并将找到的所有类型添加到types集合中
    for (Node node : nodes) { // 遍历所有节点
      node.accept(typeFinder); // 让每个节点接受TypeFinder访问者的访问，触发类型收集
    }
    assertThat(types, new BaseMatcher<HashSet<Type>>() { // 使用自定义匹配器验证types集合包含所有预期类型
      @Override public boolean matches(Object o) { // 实现匹配逻辑
        final Set<Type> actual = (HashSet<Type>) o; // 将实际值转换为Set<Type>
        return actual.containsAll(expectedTypes); // 检查实际集合是否包含所有预期类型
      }

      @Override public void describeTo(Description description) { // 实现匹配失败时的描述
        description.appendText("Expected a set of types containing all of: ") // 添加预期描述文本
            .appendText(Objects.toString(expectedTypes)); // 添加预期类型的字符串表示
      }
    });
  }
}
