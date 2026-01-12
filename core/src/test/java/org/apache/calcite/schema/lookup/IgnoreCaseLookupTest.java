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
package org.apache.calcite.schema.lookup; // 声明包名，该类位于org.apache.calcite.schema.lookup包下，属于Calcite框架的Schema查找功能模块

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的Nullable注解，用于标记可能为null的返回值
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.Collections; // 导入Java集合工具类，用于创建不可变的单元素集合
import java.util.Set; // 导入Java Set接口，用于存储名称集合

import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest断言库的nullValue匹配器，用于验证值为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言库的assertThat方法，用于执行断言验证
import static org.hamcrest.Matchers.equalTo; // 导入Hamcrest断言库的equalTo匹配器，用于验证值相等

/**
 * Test for IgnoreCaseLookup. // IgnoreCaseLookup的单元测试类，用于测试不区分大小写的查找功能
 * // 该类通过匿名内部类的方式创建一个测试用的Lookup实例，验证IgnoreCaseLookup的核心功能
 * // 主要测试场景包括：不存在键的查找、精确匹配查找、不区分大小写查找
 */
class IgnoreCaseLookupTest { // 定义测试类，用于测试IgnoreCaseLookup接口的实现
  private final Lookup<String> testee = new IgnoreCaseLookup<String>() { // 声明并初始化被测试的Lookup对象，使用匿名内部类实现IgnoreCaseLookup抽象类，泛型参数String表示存储的值的类型
    @Override public @Nullable String get(final String name) { // 重写get方法，根据名称获取对应的值，@Nullable表示可能返回null，final String name表示参数name不可修改
      if ("a".equals(name)) { // 判断传入的name是否等于"a"，使用"a".equals(name)避免name为null时抛出NullPointerException
        return "1"; // 如果name等于"a"，返回字符串"1"作为对应的值
      }
      return null; // 如果name不等于"a"，返回null表示未找到对应的值
    }

    @Override public Set<String> getNames(final LikePattern pattern) { // 重写getNames方法，根据模式匹配获取所有符合条件的名称集合，final LikePattern pattern表示模式匹配对象
      return Collections.singleton("a"); // 返回只包含"a"的不可变单元素集合，表示该Lookup中只有一个名称"a"
    }
  };

  @Test void testNull() { // 使用@Test注解标记测试方法，测试查找不存在的键时返回null的场景
    assertThat(testee.get("c"), nullValue()); // 调用testee的get方法查找"c"，使用Hamcrest断言验证返回值为null，验证查找不存在的键时正确返回null
  }

  @Test void test() { // 使用@Test注解标记测试方法，测试精确匹配查找的场景
    assertThat(testee.get("a"), equalTo("1")); // 调用testee的get方法查找"a"，使用Hamcrest断言验证返回值等于"1"，验证精确匹配时正确返回对应的值
  }

  @Test void testIgnoreCase() { // 使用@Test注解标记测试方法，测试不区分大小写查找的场景，这是IgnoreCaseLookup的核心功能测试
    assertThat(testee.getIgnoreCase("A"), equalTo(new Named<>("a", "1"))); // 调用testee的getIgnoreCase方法查找"A"（大写），验证返回一个Named对象，其中原始名称为"a"（小写），值为"1"，验证不区分大小写查找功能正常工作
  }

} // 类定义结束
