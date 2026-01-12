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
package org.apache.calcite.schema.lookup; // 声明包名，该类位于org.apache.calcite.schema.lookup包下，用于测试ConcatLookup类的功能

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest的nullValue匹配器，用于验证返回值为null的情况
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言工具，用于编写可读性强的断言语句
import static org.hamcrest.Matchers.equalTo; // 导入Hamcrest的equalTo匹配器，用于验证两个值相等

/**
 * Test for ConcatLookup. // ConcatLookup类的单元测试类，用于验证ConcatLookup类合并多个Lookup实例后的查找功能是否正常工作
 */
class ConcatLookupTest { // 测试类定义，用于测试ConcatLookup类的各种查找场景
  private final Lookup<String> testee = // 定义测试对象testee，类型为Lookup<String>，使用final修饰表示初始化后不可改变
      Lookup.concat(new FakeLookup("a", "1"), // 使用Lookup.concat静态方法创建ConcatLookup实例，传入第一个FakeLookup，包含键值对"a"->"1"
          new FakeLookup("b", "2"), // 传入第二个FakeLookup，包含键值对"b"->"2"
          new FakeLookup("d", "4"), // 传入第三个FakeLookup，包含键值对"d"->"4"
          new FakeLookup("d", "5")); // 传入第四个FakeLookup，包含键值对"d"->"5"，注意这里有两个键"d"，测试重复键的处理

  @Test void testNull() { // 测试方法：测试在所有Lookup中都不存在的键的查找行为，验证返回值是否为null
    assertThat(testee.get("c"), nullValue()); // 调用testee的get方法查找键"c"，断言返回值为null，因为没有任何FakeLookup包含键"c"
  }

  @Test void test() { // 测试方法：测试基本查找功能，验证能否正确找到存在的键对应的值
    assertThat(testee.get("a"), equalTo("1")); // 调用testee的get方法查找键"a"，断言返回值等于"1"，验证ConcatLookup能正确从第一个FakeLookup中找到"a"对应的值
  }

  @Test void testIgnoreCase() { // 测试方法：测试忽略大小写的查找功能，验证能否忽略大小写找到键对应的值
    assertThat(testee.getIgnoreCase("B"), equalTo(new Named<>("b", "2"))); // 调用testee的getIgnoreCase方法查找键"B"（大写），断言返回值等于Named对象，包含原始键名"b"和值"2"，验证忽略大小写查找功能正常
  }

  @Test void testCommonNames() { // 测试方法：测试重复键的处理，验证当多个Lookup中存在相同键时，ConcatLookup返回第一个匹配的值
    assertThat(testee.getIgnoreCase("D"), equalTo(new Named<>("d", "4"))); // 调用testee的getIgnoreCase方法查找键"D"（大写），断言返回值等于Named对象，包含原始键名"d"和值"4"，验证当有两个"d"键时，返回第一个匹配的值"4"而不是"5"
  }
}
