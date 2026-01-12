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
package org.apache.calcite.sql; // 声明包名，当前类属于org.apache.calcite.sql包，这是Calcite SQL抽象语法树(AST)的核心包

import org.apache.calcite.sql.parser.SqlParseException; // 导入SQL解析异常类，用于处理SQL解析过程中的错误
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器类，用于将SQL字符串解析为SqlNode对象
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析位置类，用于标记SQL语法元素在原始SQL文本中的位置
import org.apache.calcite.util.Litmus; // 导入Litmus工具类，用于在验证过程中控制错误处理行为
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试相关的辅助方法
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种实用方法如last、skip等

import org.hamcrest.CustomTypeSafeMatcher; // 导入Hamcrest测试框架的自定义类型安全匹配器基类
import org.hamcrest.Matcher; // 导入Hamcrest测试框架的Matcher接口，用于断言匹配
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，标记测试方法

import java.util.ArrayList; // 导入Java集合框架的ArrayList类，用于动态数组
import java.util.Arrays; // 导入Java工具类的Arrays类，提供数组操作方法
import java.util.List; // 导入Java集合框架的List接口，代表有序集合

import static org.hamcrest.CoreMatchers.is; // 静态导入Hamcrest的is匹配器，用于值相等断言
import static org.hamcrest.CoreMatchers.not; // 静态导入Hamcrest的not匹配器，用于取反断言
import static org.hamcrest.CoreMatchers.sameInstance; // 静态导入Hamcrest的sameInstance匹配器，用于对象引用相等断言
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入Hamcrest的assertThat方法，用于执行断言
import static org.hamcrest.Matchers.hasSize; // 静态导入Hamcrest的hasSize匹配器，用于集合大小断言

/**
 * Test of {@link SqlNode} and other SQL AST classes.
 * SqlNode和其他SQL抽象语法树(AST)类的测试类
 * 
 * 本测试类主要测试SqlNode及其相关类的核心功能，包括：
 * 1. SqlNodeList的各种构造方式和行为验证
 * 2. SqlCall的equalsDeep方法是否正确考虑了函数限定符（如DISTINCT）
 * 3. SqlRowTypeNameSpec的结构等价性比较
 * 
 * SqlNode是Calcite中所有SQL语法元素的基类，代表SQL抽象语法树的节点
 * 通过这些测试确保SQL解析和AST操作的正确性和一致性
 */
class SqlNodeTest { // 测试类定义：SqlNodeTest，用于测试SqlNode及其相关类的功能
  @Test void testSqlNodeList() { // 测试方法：测试SqlNodeList的各种构造方式和行为
    SqlParserPos zero = SqlParserPos.ZERO; // 创建零位置对象，表示SQL解析位置为原点，用于标记语法元素在SQL文本中的位置
    checkList(new SqlNodeList(zero)); // 测试空列表：创建一个空的SqlNodeList并验证其行为，确保空列表的基本操作正确
    checkList(SqlNodeList.SINGLETON_STAR); // 测试单例星号列表：验证SINGLETON_STAR常量（代表"*"）的行为，这是SQL中常见的通配符
    checkList(SqlNodeList.SINGLETON_EMPTY); // 测试单例空列表：验证SINGLETON_EMPTY常量的行为，确保空列表的单例模式正常工作
    checkList( // 测试包含多个元素的列表：验证包含字符串字面量和标识符的SqlNodeList的行为
        SqlNodeList.of(zero, // 使用of工厂方法创建SqlNodeList，传入位置参数和元素列表
            Arrays.asList(SqlLiteral.createCharString("x", zero), // 创建字符字面量"x"，测试字符串在AST中的表示
                new SqlIdentifier("y", zero)))); // 创建标识符"y"，测试SQL标识符在AST中的表示
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4402">[CALCITE-4402]
   * SqlCall#equalsDeep does not take into account the function quantifier</a>.
   * 测试用例：验证SqlCall的equalsDeep方法是否正确考虑函数限定符（如DISTINCT）
   * 
   * 这个测试针对CALCITE-4402问题，该问题指出equalsDeep方法在比较两个SqlCall时
   * 没有考虑函数限定符（如DISTINCT、ALL等），导致count(distinct a)和count(a)被认为相等
   * 
   * 测试内容：
   * 1. count(a)应该与count(a)深度相等
   * 2. count(distinct a)应该与count(distinct a)深度相等
   * 3. count(distinct a)不应该与count(a)深度相等（关键测试点）
   */
  @Test void testCountEqualsDeep() { // 测试方法：测试COUNT函数的equalsDeep方法是否正确处理DISTINCT限定符
    assertThat("count(a)", isEqualsDeep("count(a)")); // 断言：count(a)应该与自身深度相等，验证基本功能正常
    assertThat("count(distinct a)", isEqualsDeep("count(distinct a)")); // 断言：count(distinct a)应该与自身深度相等，验证DISTINCT情况
    assertThat("count(distinct a)", not(isEqualsDeep("count(a)"))); // 断言：count(distinct a)不应该与count(a)相等，这是核心测试，验证DISTINCT限定符被正确处理
  }

  /**
   * Test case for
   * <a href="https://issues.apache.org/jira/browse/CALCITE-6100">[CALCITE-6100]
   * The equalsDeep of SqlRowTypeNameSpec should return true if two SqlRowTypeNameSpecs are
   * structurally equivalent</a>.
   * 测试用例：验证SqlRowTypeNameSpec的equalsDeep方法是否正确判断结构等价性
   * 
   * 这个测试针对CALCITE-6100问题，该问题涉及行类型名称规范（ROW类型）的深度相等比较
   * 
   * 测试内容：
   * 1. CAST(a AS ROW(field INTEGER))应该与自身深度相等
   * 
   * 行类型（ROW）是SQL中的复合类型，可以包含多个字段，每个字段有名称和类型
   * 这个测试确保在比较两个ROW类型时，能够正确识别它们的结构是否相同
   */
  @Test void testRowEqualsDeep() { // 测试方法：测试ROW类型的equalsDeep方法是否正确判断结构等价性
    assertThat("CAST(a AS ROW(field INTEGER))", // 断言：相同的ROW类型CAST表达式应该深度相等
        isEqualsDeep("CAST(a AS ROW(field INTEGER))")); // 验证包含ROW类型定义的CAST表达式的深度相等比较
  }

  private static Matcher<String> isEqualsDeep(String sqlExpected) { // 私有静态方法：创建一个自定义匹配器，用于验证两个SQL表达式是否深度相等
    return new CustomTypeSafeMatcher<String>("isDeepEqual") { // 返回一个自定义的类型安全匹配器，描述为"isDeepEqual"
      @Override protected boolean matchesSafely(String sqlActual) { // 重写匹配方法：安全地执行匹配逻辑，不会抛出类型转换异常
        try { // 开始try块，捕获可能的SQL解析异常
          SqlNode sqlNodeActual = parseExpression(sqlActual); // 解析实际的SQL表达式字符串为SqlNode对象
          SqlNode sqlNodeExpected = parseExpression(sqlExpected); // 解析期望的SQL表达式字符串为SqlNode对象

          return sqlNodeActual.equalsDeep(sqlNodeExpected, Litmus.IGNORE); // 使用equalsDeep方法深度比较两个SqlNode，Litmus.IGNORE表示忽略验证错误
        } catch (SqlParseException e) { // 捕获SQL解析异常
          throw TestUtil.rethrow(e); // 使用测试工具重新抛出异常，保持原始异常类型
        }
      }
    };
  }

  private static SqlNode parseExpression(String sql) throws SqlParseException { // 私有静态方法：解析SQL表达式字符串为SqlNode对象，可能抛出解析异常
    return SqlParser.create(sql).parseExpression(); // 创建SQL解析器并解析表达式，返回解析后的SqlNode对象
  }

  /** Compares a list to its own backing list. */
  private void checkList(SqlNodeList nodeList) { // 私有方法：比较SqlNodeList与其后备列表，验证它们的一致性
    checkLists(nodeList, nodeList.getList(), 0); // 调用checkLists方法，将SqlNodeList与其内部列表进行比较，初始深度为0
  }

  /** Checks that two lists are identical. */
  private <E> void checkLists(List<E> list0, List<E> list1, int depth) { // 私有泛型方法：检查两个列表是否完全相同，depth参数用于控制递归深度
    assertThat(list0.hashCode(), is(list1.hashCode())); // 断言：两个列表的hashCode应该相等，验证哈希一致性
    assertThat(list0.equals(list1), is(true)); // 断言：两个列表的equals方法应该返回true，验证内容相等
    assertThat(list0, hasSize(list1.size())); // 断言：两个列表的大小应该相同，验证元素数量一致
    assertThat(list0.isEmpty(), is(list1.isEmpty())); // 断言：两个列表的isEmpty状态应该相同，验证空状态一致
    if (!list0.isEmpty()) { // 如果列表不为空，执行以下检查
      assertThat(list0.get(0), sameInstance(list1.get(0))); // 断言：两个列表的第一个元素应该是同一个对象实例，验证对象引用一致
      assertThat(Util.last(list0), sameInstance(Util.last(list1))); // 断言：两个列表的最后一个元素应该是同一个对象实例，验证尾部引用一致
      if (depth == 0) { // 如果递归深度为0，执行递归检查（防止无限递归）
        checkLists(Util.skip(list0, 1), Util.skip(list1, 1), depth + 1); // 递归检查跳过第一个元素后的子列表，深度加1
      }
    }
    assertThat(collect(list0), is(list1)); // 断言：从list0收集的元素应该与list1相等，验证迭代一致性
    assertThat(collect(list1), is(list0)); // 断言：从list1收集的元素应该与list0相等，验证双向一致性
  }

  private static <E> List<E> collect(Iterable<E> iterable) { // 私有静态泛型方法：将可迭代对象转换为ArrayList列表
    final List<E> list = new ArrayList<>(); // 创建一个新的ArrayList用于存储元素
    for (E e : iterable) { // 遍历可迭代对象的每个元素
      list.add(e); // 将元素添加到列表中
    }
    return list; // 返回包含所有元素的列表
  }
} // 类定义结束
