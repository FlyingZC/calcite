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
package org.apache.calcite.schema.lookup; // 指定当前类所在的包路径，org.apache.calcite.schema.lookup 包包含了各种查找相关的类和接口

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.nullValue; // 导入 nullValue 匹配器，用于验证返回值为 null
import static org.hamcrest.MatcherAssert.assertThat; // 导入 assertThat 断言方法，用于编写测试断言
import static org.hamcrest.Matchers.equalTo; // 导入 equalTo 匹配器，用于验证两个值相等

/**
 * Test for MappedLookup. // 这是一个测试类，用于测试 TransformingLookup（也称为 MappedLookup）的功能
 * 
 * TransformingLookupTest 是 TransformingLookup 类的单元测试类，用于验证 TransformingLookup 的各种查找功能是否正常工作。 // 类的作用说明
 * TransformingLookup 是一个包装器类，它基于另一个 Lookup 实现创建一个新的 Lookup，通过一个转换函数对查找到的实体进行转换。 // 类的作用说明
 * 这个测试类主要测试以下场景： // 类的作用说明
 * 1. 测试查找不存在的键时返回 null（testNull 方法） // 类的作用说明
 * 2. 测试查找存在的键时返回转换后的值（test 方法） // 类的作用说明
 * 3. 测试忽略大小写查找时返回转换后的 Named 对象（testIgnoreCase 方法） // 类的作用说明
 * 测试使用 FakeLookup 作为基础 Lookup，FakeLookup 包含一个键值对 {"a": "1"}，转换函数将名称和值拼接成 "name_value" 的格式。 // 类的作用说明
 */
class TransformingLookupTest { // 定义 TransformingLookupTest 测试类，用于测试 TransformingLookup 的功能
  private final Lookup<String> testee = // 成员变量：testee 是一个不可变的 Lookup<String> 对象，这是被测试的对象，它是一个 TransformingLookup 实例
      (new FakeLookup("a", "1")).map((value, name) -> name + "_" + value); // 创建一个 FakeLookup 实例，包含键值对 {"a": "1"}，然后调用 map 方法创建一个 TransformingLookup，转换函数将名称和值拼接成 "name_value" 格式，例如查找 "a" 时返回 "a_1"

  @Test void testNull() { // 测试方法：testNull 测试当查找不存在的键时，应该返回 null，@Test 注解标记这是一个测试方法
    assertThat(testee.get("c"), nullValue()); // 调用 testee.get("c") 查找键 "c"，由于 FakeLookup 中没有 "c" 这个键，所以应该返回 null，使用 assertThat 断言验证返回值为 null
  } // 测试方法结束，验证了查找不存在的键时正确返回 null

  @Test void test() { // 测试方法：test 测试当查找存在的键时，应该返回转换后的值，@Test 注解标记这是一个测试方法
    assertThat(testee.get("a"), equalTo("a_1")); // 调用 testee.get("a") 查找键 "a"，FakeLookup 中存在 "a" 这个键，对应的值是 "1"，转换函数将名称 "a" 和值 "1" 拼接成 "a_1"，使用 assertThat 断言验证返回值为 "a_1"
  } // 测试方法结束，验证了查找存在的键时正确返回转换后的值

  @Test void testIgnoreCase() { // 测试方法：testIgnoreCase 测试当忽略大小写查找存在的键时，应该返回转换后的 Named 对象，@Test 注解标记这是一个测试方法
    assertThat(testee.getIgnoreCase("A"), equalTo(new Named<>("a", "a_1"))); // 调用 testee.getIgnoreCase("A") 查找键 "A"（大写），FakeLookup 中存在小写的 "a"，忽略大小写查找会找到 "a"，对应的值是 "1"，转换函数将原始名称 "a" 和值 "1" 拼接成 "a_1"，返回 Named<>("a", "a_1")，使用 assertThat 断言验证返回值等于这个 Named 对象
  } // 测试方法结束，验证了忽略大小写查找存在的键时正确返回转换后的 Named 对象

} // 类定义结束，所有测试方法都验证了 TransformingLookup 的正确性
