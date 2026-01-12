/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看NOTICE文件获取版权信息
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version 2.0 // ASF根据Apache 2.0许可证授权此文件
 * (the "License"); you may not use this file except in compliance with // 除非遵守许可证，否则不得使用此文件
 * the License.  You may obtain a copy of the License at // 可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS, // 根据许可证分发的内容按"原样"提供
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 不提供任何明示或暗示的保证或条件
 * See the License for the specific language governing permissions and // 查看许可证了解具体语言权限和
 * limitations under the License. // 限制条件
 */
package org.apache.calcite.linq4j; // 声明包名：org.apache.calcite.linq4j，这是Calcite LINQ4J框架的核心包
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入ArrayList类，用于动态数组列表
import java.util.List; // 导入List接口，用于列表集合
import java.util.stream.Collectors; // 导入Collectors类，用于流收集操作
import java.util.stream.IntStream; // 导入IntStream类，用于整数流操作

import static org.hamcrest.CoreMatchers.is; // 导入is匹配器，用于断言值相等
import static org.hamcrest.CoreMatchers.nullValue; // 导入nullValue匹配器，用于断言值为null
import static org.hamcrest.MatcherAssert.assertThat; // 导入assertThat方法，用于执行断言
import static org.hamcrest.Matchers.hasSize; // 导入hasSize匹配器，用于断言集合大小
import static org.hamcrest.Matchers.hasToString; // 导入hasToString匹配器，用于断言字符串表示

/** Tests for {@link org.apache.calcite.linq4j.MemoryEnumerable}. */ // Javadoc注释：MemoryEnumerable类的测试类
class MemoryEnumerableTest { // 定义MemoryEnumerableTest测试类，用于测试MemoryEnumerable的功能

  @Test void testHistoryAndFuture() { // 测试方法：测试MemoryEnumerable的历史和未来访问功能
    final Enumerable<Integer> input = // 创建一个Enumerable<Integer>对象作为输入数据
        Linq4j.asEnumerable(IntStream.range(0, 100) // 使用Linq4j.asEnumerable将流转换为可枚举对象，范围从0到99
            .boxed().collect(Collectors.toList())); // 将int流装箱为Integer流，并收集为List

    final MemoryEnumerable<Integer> integers = new MemoryEnumerable<>(input, 5, 1); // 创建MemoryEnumerable对象，参数：输入数据、历史窗口大小5、未来窗口大小1
    final Enumerator<MemoryFactory.Memory<Integer>> enumerator = integers.enumerator(); // 获取MemoryEnumerable的枚举器，用于遍历数据

    final List<MemoryFactory.Memory<Integer>> results = new ArrayList<>(); // 创建结果列表，用于存储遍历得到的Memory对象
    while (enumerator.moveNext()) { // 循环：移动到下一个元素，如果存在更多元素则继续
      final MemoryFactory.Memory<Integer> current = enumerator.current(); // 获取当前元素的Memory对象
      results.add(current); // 将当前Memory对象添加到结果列表中
    }

    assertThat(results, hasSize(100)); // 断言：验证结果列表的大小为100（即遍历了100个元素）
    // First entry // 注释：验证第一个元素的历史和未来访问
    assertThat((int) results.get(0).get(), is(0)); // 断言：第一个元素的当前值为0
    assertThat((int) results.get(0).get(1), is(1)); // 断言：第一个元素的未来第1个位置（索引1）的值为1
    assertThat(results.get(0).get(-2), nullValue()); // 断言：第一个元素的历史第2个位置（索引-2）的值为null（因为不存在）
    // Last entry // 注释：验证最后一个元素的历史和未来访问
    assertThat((int) results.get(99).get(), is(99)); // 断言：最后一个元素的当前值为99
    assertThat((int) results.get(99).get(-2), is(97)); // 断言：最后一个元素的历史第2个位置（索引-2）的值为97
    assertThat(results.get(99).get(1), nullValue()); // 断言：最后一个元素的未来第1个位置（索引1）的值为null（因为不存在）
  }

  @Test void testModularInteger() { // 测试方法：测试ModularInteger类的模运算功能
    final ModularInteger modularInteger = new ModularInteger(4, 5); // 创建ModularInteger对象，值为4，模数为5（表示4 mod 5）
    assertThat(modularInteger, hasToString("4 mod 5")); // 断言：验证ModularInteger的字符串表示为"4 mod 5"

    final ModularInteger plus = modularInteger.plus(1); // 执行加法操作：4 + 1 = 5，模5后结果为0
    assertThat(plus, hasToString("0 mod 5")); // 断言：验证加法结果的字符串表示为"0 mod 5"

    final ModularInteger minus = modularInteger.plus(-6); // 执行加法操作：4 + (-6) = -2，模5后结果为3（因为-2 mod 5 = 3）
    assertThat(minus, hasToString("3 mod 5")); // 断言：验证加法结果的字符串表示为"3 mod 5"
  }
}
