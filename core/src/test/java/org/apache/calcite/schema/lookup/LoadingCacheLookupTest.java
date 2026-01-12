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
package org.apache.calcite.schema.lookup; // 指定当前测试类所在的包路径，org.apache.calcite.schema.lookup 包包含了各种查找相关的类和接口

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法，JUnit 会自动执行带有此注解的方法

import static org.hamcrest.CoreMatchers.nullValue; // 导入 nullValue 匹配器，用于验证返回值是否为 null
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 断言方法，用于编写测试断言，验证实际值是否符合预期
import static org.hamcrest.Matchers.equalTo; // 导入 equalTo 匹配器，用于验证实际值是否等于预期值

/**
 * LoadingCacheLookup 的单元测试类，用于验证 LoadingCacheLookup 类的各种查找功能是否正常工作。 // 类的作用说明
 * 
 * LoadingCacheLookup 是一个使用 Google Guava 的 LoadingCache 来加速查找操作的包装类， // 类的作用说明
 * 它将查找操作委托给另一个 Lookup 实例，并通过缓存机制提高性能。这个测试类验证了： // 类的作用说明
 * 1. 查找不存在的键时返回 null 的行为 // 类的作用说明
 * 2. 查找存在的键时返回正确值的行为 // 类的作用说明
 * 3. 忽略大小写查找时返回正确 Named 对象的行为 // 类的作用说明
 * 测试使用 FakeLookup 作为被委托的 Lookup 实例，FakeLookup 是一个基于 Map 的简单查找实现。 // 类的作用说明
 */
public class LoadingCacheLookupTest { // 定义 LoadingCacheLookupTest 测试类，这是一个公共的 JUnit 测试类

  private final Lookup<String> testee = // 成员变量：testee 是被测试的 Lookup<String> 实例，使用 final 修饰表示引用不可变，泛型类型 String 表示查找的元素类型是字符串
      new LoadingCacheLookup<>(new FakeLookup("test", "xxxx")); // 初始化 testee，创建一个 LoadingCacheLookup 实例，内部委托给一个 FakeLookup 实例，FakeLookup 中包含一个键值对 "test" -> "xxxx"

  @Test void testNull() { // 测试方法：testNull 用于测试查找不存在的键时是否返回 null，@Test 注解标记这是一个测试方法
    assertThat(testee.get("unknown"), nullValue()); // 断言：调用 testee.get("unknown") 查找不存在的键 "unknown"，期望返回值为 null，使用 nullValue() 匹配器验证
  } // 测试方法结束，验证了当查找的键不存在时，LoadingCacheLookup 能正确返回 null

  @Test void test() { // 测试方法：test 用于测试查找存在的键时是否返回正确的值，@Test 注解标记这是一个测试方法
    assertThat(testee.get("test"), equalTo("xxxx")); // 断言：调用 testee.get("test") 查找存在的键 "test"，期望返回值为 "xxxx"，使用 equalTo("xxxx") 匹配器验证返回值是否等于 "xxxx"
  } // 测试方法结束，验证了当查找的键存在时，LoadingCacheLookup 能正确返回对应的值

  @Test void testIgnoreCase() { // 测试方法：testIgnoreCase 用于测试忽略大小写查找时是否返回正确的 Named 对象，@Test 注解标记这是一个测试方法
    assertThat(testee.getIgnoreCase("TEST"), equalTo(new Named<>("test", "xxxx"))); // 断言：调用 testee.getIgnoreCase("TEST") 使用大写的 "TEST" 进行忽略大小写查找，期望返回一个 Named 对象，Named 对象中包含原始键 "test" 和对应的值 "xxxx"，使用 equalTo() 匹配器验证返回的 Named 对象是否正确
  } // 测试方法结束，验证了当使用忽略大小写查找时，LoadingCacheLookup 能正确返回包含原始键和值的 Named 对象，即使输入的键大小写与原始键不同

} // 类定义结束
