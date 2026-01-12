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
package org.apache.calcite.linq4j.test; // 包声明：该类位于org.apache.calcite.linq4j.test包下，是Calcite LINQ4J测试包的一部分

import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ风格的查询操作方法
import org.apache.calcite.linq4j.Lookup; // 导入Lookup接口，表示一种键到多个值的映射关系（类似多值Map）

import org.junit.jupiter.api.BeforeEach; // 导入JUnit5的BeforeEach注解，用于标记在每个测试方法执行前运行的初始化方法
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入ArrayList类，用于创建动态数组列表
import java.util.List; // 导入List接口，表示有序集合

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器的is方法，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具类，提供可读性强的断言方法
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5的assertTrue断言方法，用于断言条件为真

/**
 * Unit tests for {@link Lookup} and {@code LookupImpl}.
 */ // 类级Javadoc注释：这是Lookup接口和LookupImpl实现类的单元测试类
class LookupImplTest { // 类定义：LookupImplTest测试类，用于测试Lookup接口的实现

  private Lookup<Integer, String> impl; // 成员变量：声明一个Lookup类型的实例，键类型为Integer（部门编号），值类型为String（员工姓名），用于测试

  @BeforeEach // JUnit5注解：标记setUp方法在每个测试方法执行前运行，用于初始化测试环境
  public void setUp() { // setUp方法：测试初始化方法，在每个测试方法执行前被调用，用于创建和配置Lookup实例
    impl = // 将创建的Lookup实例赋值给成员变量impl，供后续测试方法使用
        Linq4j.asEnumerable(Linq4jTest.emps) // 使用Linq4j工具类将emps数组转换为可枚举的Enumerable集合，emps是Linq4jTest中定义的员工测试数据数组
            .toLookup(Linq4jTest.EMP_DEPTNO_SELECTOR, // 将Enumerable转换为Lookup，使用EMP_DEPTNO_SELECTOR作为键选择器（从员工对象中提取部门编号）
                Linq4jTest.EMP_NAME_SELECTOR); // 使用EMP_NAME_SELECTOR作为值选择器（从员工对象中提取员工姓名），最终生成一个按部门编号分组的员工姓名查找表
  }

  @Test // JUnit5注解：标记testPut方法为测试方法，JUnit会自动运行此方法
  void testPut() { // 测试方法：测试Lookup的put方法，验证向Lookup中添加新的键值对的功能
    int initSize = impl.size(); // 获取Lookup的初始大小（键的数量），用于后续验证put操作是否正确增加了键的数量
    impl.put(99, Linq4j.asEnumerable(new String[]{"A", "B"})); // 调用Lookup的put方法，向Lookup中添加一个键为99、值为包含"A"和"B"两个字符串的可枚举集合
    assertTrue(impl.containsKey(99)); // 断言：验证Lookup现在包含键99，确保put操作成功添加了新键
    assertThat(impl.size() - 1, is(initSize)); // 断言：验证Lookup的大小比初始大小增加了1（impl.size() - 1应该等于initSize），确保put操作正确增加了键的数量
  }

  @Test // JUnit5注解：标记testContainsValue方法为测试方法，JUnit会自动运行此方法
  void testContainsValue() { // 测试方法：测试Lookup的containsValue方法，验证查找特定值集合的功能
    List<String> list = new ArrayList<>(); // 创建一个新的ArrayList集合，用于存储测试数据
    list.add("C"); // 向list中添加字符串"C"
    list.add("D"); // 向list中添加字符串"D"，此时list包含["C", "D"]
    List<String> list2 = new ArrayList<>(list); // 创建list的副本list2，用于测试containsValue方法是否能够识别值相等的集合
    impl.put(100, Linq4j.asEnumerable(list)); // 调用Lookup的put方法，将键100与包含["C", "D"]的可枚举集合关联起来
    assertTrue(impl.containsValue(list)); // 断言：验证Lookup包含值list（原始集合），确保containsValue方法能够正确识别已添加的值
    assertTrue(impl.containsValue(list2)); // 断言：验证Lookup包含值list2（list的副本），确保containsValue方法基于值内容而非引用进行比较
  }
} // 类结束：LookupImplTest类定义结束
