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
package org.apache.calcite.linq4j.function; // 声明包名，该测试类位于 org.apache.calcite.linq4j.function 包下

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.util.Arrays; // 导入 Arrays 工具类，用于创建数组列表
import java.util.Collections; // 导入 Collections 工具类，用于创建不可变集合
import java.util.List; // 导入 List 接口，表示有序集合
import java.util.IntFunction; // 导入 IntFunction 函数式接口，表示接受整数参数并返回结果的函数

import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 断言工具，用于验证测试结果
import static org.hamcrest.Matchers.hasToString; // 导入 hasToString 匹配器，用于验证对象的字符串表示
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入 assertFalse 断言方法，用于验证条件为假
import static org.junit.jupiter.api.Assertions.assertSame; // 导入 assertSame 断言方法，用于验证两个引用指向同一对象
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入 assertTrue 断言方法，用于验证条件为真
import static org.junit.jupiter.api.Assertions.fail; // 导入 fail 方法，用于标记测试失败

/**
 * Test for {@link Functions}. // FunctionTest 类：Functions 工具类的单元测试类，用于测试 Functions 类中提供的各种静态工具方法的正确性
 * 该类包含对 Functions.filter、Functions.exists、Functions.all 和 Functions.generate 等方法的测试用例
 * 通过这些测试，验证了 Functions 类中提供的函数式编程工具方法的正确性和边界条件处理
 * 所有测试方法都使用 JUnit 5 框架编写，使用 @Test 注解标记
 */ // 类注释结束
class FunctionTest { // FunctionTest 类定义：Functions 工具类的测试类，包含多个测试方法
  /** Unit test for {@link Functions#filter}. */ // testFilter 方法：测试 Functions.filter 方法的单元测试，验证过滤功能
  @Test void testFilter() { // testFilter 方法：测试 filter 方法，该方法根据给定的谓词过滤列表中的元素
    final List<String> abc = Arrays.asList("A", "B", "C", "D"); // 创建测试数据列表 abc，包含四个字符串元素 "A", "B", "C", "D"
    // a miss, then a hit // 注释：第一个测试用例，过滤掉 "B" 元素
    assertThat(Functions.filter(abc, v1 -> !v1.equals("B")), // 调用 filter 方法，过滤掉等于 "B" 的元素，使用 lambda 表达式作为谓词
        hasToString("[A, C, D]")); // 验证过滤后的结果为 [A, C, D]，即 "B" 被成功过滤掉
    // a hit, then all misses // 注释：第二个测试用例，只保留 "A" 元素
    assertThat(Functions.filter(abc, v1 -> v1.equals("A")), // 调用 filter 方法，只保留等于 "A" 的元素
        hasToString("[A]")); // 验证过滤后的结果为 [A]，即只保留了 "A" 元素
    // two hits, then a miss // 注释：第三个测试用例，过滤掉 "C" 元素
    assertThat(Functions.filter(abc, v1 -> !v1.equals("C")), // 调用 filter 方法，过滤掉等于 "C" 的元素
        hasToString("[A, B, D]")); // 验证过滤后的结果为 [A, B, D]，即 "C" 被成功过滤掉
    assertSame(Collections.emptyList(), // 验证当使用 falsePredicate1（始终返回 false 的谓词）时，返回的是空列表
        Functions.filter(abc, Functions.falsePredicate1())); // 调用 filter 方法，使用始终返回 false 的谓词，应该返回空列表
    assertSame(abc, // 验证当使用 truePredicate1（始终返回 true 的谓词）时，返回的是原列表对象本身
        Functions.filter(abc, Functions.truePredicate1())); // 调用 filter 方法，使用始终返回 true 的谓词，应该返回原列表
  } // testFilter 方法结束

  /** Unit test for {@link Functions#exists}. */ // testExists 方法：测试 Functions.exists 方法的单元测试，验证存在性判断功能
  @Test void testExists() { // testExists 方法：测试 exists 方法，该方法判断集合中是否存在满足给定谓词的元素
    final List<Integer> ints = Arrays.asList(1, 10, 2); // 创建测试数据列表 ints，包含三个整数元素 1, 10, 2
    final List<Integer> empty = Collections.emptyList(); // 创建空列表 empty，用于测试边界条件
    assertFalse( // 验证集合中不存在大于 20 的元素
        Functions.exists(ints, v1 -> v1 > 20)); // 调用 exists 方法，判断是否存在大于 20 的元素，应该返回 false
    assertFalse( // 验证空列表中不存在任何元素，即使使用 falsePredicate1
        Functions.exists(empty, Functions.falsePredicate1())); // 调用 exists 方法，对空列表使用始终返回 false 的谓词，应该返回 false
    assertFalse( // 验证空列表中不存在任何元素，即使使用 truePredicate1
        Functions.exists(empty, Functions.truePredicate1())); // 调用 exists 方法，对空列表使用始终返回 true 的谓词，应该返回 false（因为列表为空）
  } // testExists 方法结束

  /** Unit test for {@link Functions#all}. */ // testAll 方法：测试 Functions.all 方法的单元测试，验证全量判断功能
  @Test void testAll() { // testAll 方法：测试 all 方法，该方法判断集合中的所有元素是否都满足给定谓词
    final List<Integer> ints = Arrays.asList(1, 10, 2); // 创建测试数据列表 ints，包含三个整数元素 1, 10, 2
    final List<Integer> empty = Collections.emptyList(); // 创建空列表 empty，用于测试边界条件
    assertFalse( // 验证并非所有元素都大于 20
        Functions.all(ints, v1 -> v1 > 20)); // 调用 all 方法，判断是否所有元素都大于 20，应该返回 false
    assertTrue( // 验证所有元素都小于 20
        Functions.all(ints, v1 -> v1 < 20)); // 调用 all 方法，判断是否所有元素都小于 20，应该返回 true
    assertFalse( // 验证并非所有元素都小于 10
        Functions.all(ints, v1 -> v1 < 10)); // 调用 all 方法，判断是否所有元素都小于 10，应该返回 false（因为 10 不小于 10）
    assertTrue( // 验证空列表的所有元素都满足 falsePredicate1（因为空列表的 all 操作总是返回 true）
        Functions.all(empty, Functions.falsePredicate1())); // 调用 all 方法，对空列表使用始终返回 false 的谓词，应该返回 true（数学上的全称量词，空集为真）
    assertTrue( // 验证空列表的所有元素都满足 truePredicate1（因为空列表的 all 操作总是返回 true）
        Functions.all(empty, Functions.truePredicate1())); // 调用 all 方法，对空列表使用始终返回 true 的谓词，应该返回 true
  } // testAll 方法结束

  /** Unit test for {@link Functions#generate}. */ // testGenerate 方法：测试 Functions.generate 方法的单元测试，验证列表生成功能
  @Test void testGenerate() { // testGenerate 方法：测试 generate 方法，该方法根据给定的函数生成指定长度的列表
    final IntFunction<String> xx = // 定义一个 IntFunction<String> 类型的匿名函数，用于生成递归字符串
        new IntFunction<String>() { // 创建 IntFunction 匿名内部类实例
          public String apply(int a0) { // apply 方法：根据整数参数生成字符串，实现递归逻辑
            return a0 == 0 ? "0" : "x" + apply(a0 - 1); // 如果参数为 0，返回 "0"；否则返回 "x" 递归调用 apply(a0-1) 的结果
          } // apply 方法结束
        }; // IntFunction 匿名内部类定义结束
    assertThat(Functions.generate(0, xx), hasToString("[]")); // 验证生成长度为 0 的列表，结果为空列表 []
    assertThat(Functions.generate(1, xx), hasToString("[0]")); // 验证生成长度为 1 的列表，结果为 [0]，因为 apply(0) 返回 "0"
    assertThat(Functions.generate(3, xx), hasToString("[0, x0, xx0]")); // 验证生成长度为 3 的列表，结果为 [0, x0, xx0]，因为 apply(0)="0", apply(1)="x0", apply(2)="xx0"
    try { // 开始 try 块，用于测试异常情况
      final List<String> generate = Functions.generate(-2, xx); // 尝试生成长度为 -2 的列表，这应该抛出异常
      fail("expected error, got " + generate); // 如果没有抛出异常，则测试失败
    } catch (IllegalArgumentException e) { // 捕获 IllegalArgumentException 异常
      // ok // 验证捕获到预期的异常，测试通过
    } // catch 块结束
  } // testGenerate 方法结束
} // FunctionTest 类定义结束
