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

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法，表示该方法是一个单元测试

import static org.hamcrest.CoreMatchers.nullValue; // 导入 Hamcrest 的 nullValue 匹配器，用于验证返回值是否为 null
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 的 assertThat 断言方法，用于在测试中进行断言验证
import static org.hamcrest.Matchers.equalTo; // 导入 Hamcrest 的 equalTo 匹配器，用于验证两个值是否相等

/**
 * Test for CachedLookup. // 这是一个测试类，用于测试 SnapshotLookup（快照查找）类的功能
 * 
 * SnapshotLookupTest 类是用于测试 SnapshotLookup 实现类的单元测试类。 // 类的作用说明
 * SnapshotLookup 是一个包装类，它能够对底层的 Lookup 实现创建快照， // 类的作用说明
 * 从而在启用快照时缓存查找结果，提高查找性能。这个测试类验证了 // 类的作用说明
 * SnapshotLookup 的核心功能，包括精确查找、不存在的键查找、以及忽略大小写的查找。 // 类的作用说明
 * 测试使用 JUnit 5 框架和 Hamcrest 断言库来验证功能的正确性。 // 类的作用说明
 */
class SnapshotLookupTest { // 定义 SnapshotLookupTest 测试类，这是一个没有访问修饰符的类，只在包内可见
  private final Lookup<String> testee = new SnapshotLookup<>(new FakeLookup("a", "1")); // 成员变量：testee 是被测试的 SnapshotLookup 实例，它包装了一个 FakeLookup 实例，FakeLookup 初始化时包含一个键值对 "a" -> "1"，Lookup<String> 表示这是一个字符串类型的查找器

  @Test void testNull() { // 测试方法：testNull 用于测试查找不存在的键时是否返回 null，@Test 注解标记这是一个测试方法
    assertThat(testee.get("c"), nullValue()); // 断言：调用 testee 的 get 方法查找键 "c"，期望返回值为 null，因为 FakeLookup 中只有键 "a"，没有键 "c"
  } // 测试方法结束

  @Test void test() { // 测试方法：test 用于测试查找存在的键时是否返回正确的值，@Test 注解标记这是一个测试方法
    assertThat(testee.get("a"), equalTo("1")); // 断言：调用 testee 的 get 方法查找键 "a"，期望返回值为 "1"，因为 FakeLookup 中键 "a" 对应的值是 "1"
  } // 测试方法结束

  @Test void testIgnoreCase() { // 测试方法：testIgnoreCase 用于测试忽略大小写的查找功能，@Test 注解标记这是一个测试方法
    assertThat(testee.getIgnoreCase("A"), equalTo(new Named<>("a", "1"))); // 断言：调用 testee 的 getIgnoreCase 方法查找键 "A"（大写），期望返回一个 Named 对象，该对象包含原始键 "a"（小写）和对应的值 "1"，这验证了忽略大小写的查找功能
  } // 测试方法结束

} // 类定义结束
