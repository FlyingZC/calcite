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
package org.apache.calcite.util; // 声明包名，表示这个类属于org.apache.calcite.util包

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.BitSet; // 导入Java标准库的BitSet类，用于位集合操作
import java.util.Collections; // 导入Java标准库的Collections工具类，用于集合操作
import java.util.SortedMap; // 导入Java标准库的SortedMap接口，用于有序映射
import java.util.TreeMap; // 导入Java标准库的TreeMap类，SortedMap的实现

import static org.apache.calcite.test.Matchers.isListOf; // 导入自定义的isListOf匹配器，用于验证列表内容

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest的equalTo匹配器，用于判断相等
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的assertThat方法，用于断言
import static org.hamcrest.Matchers.empty; // 导入Hamcrest的empty匹配器，用于验证集合为空
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest的hasToString匹配器，用于验证toString输出
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit5的assertFalse方法，用于断言为假
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit5的assertTrue方法，用于断言为真
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit5的fail方法，用于标记测试失败

/**
 * Unit test for {@link org.apache.calcite.util.BitSets}.
 * BitSets类的单元测试类，用于测试BitSets工具类的各种功能
 * BitSets是Calcite框架中提供的一个工具类，用于对Java标准库的BitSet进行扩展操作
 */
class BitSetsTest { // BitSetsTest类定义，用于测试BitSets工具类的各种方法
  /**
   * Tests the method
   * {@link org.apache.calcite.util.BitSets#toIter(java.util.BitSet)}.
   * 测试BitSets.toIter()方法，该方法将BitSet转换为可迭代的整数集合
   * toIter方法返回一个Iterable<Integer>，可以遍历BitSet中所有被设置为true的位的索引
   */
  @Test void testToIterBitSet() { // 测试方法，使用@Test注解标记
    BitSet bitSet = new BitSet(); // 创建一个空的BitSet对象，初始没有任何位被设置

    assertToIterBitSet("", bitSet); // 断言空BitSet转换为迭代器后得到空字符串
    bitSet.set(0); // 设置BitSet的第0位为true
    assertToIterBitSet("0", bitSet); // 断言BitSet转换为迭代器后得到字符串"0"
    bitSet.set(1); // 设置BitSet的第1位为true
    assertToIterBitSet("0, 1", bitSet); // 断言BitSet转换为迭代器后得到字符串"0, 1"
    bitSet.clear(); // 清空BitSet中的所有位
    bitSet.set(10); // 设置BitSet的第10位为true
    assertToIterBitSet("10", bitSet); // 断言BitSet转换为迭代器后得到字符串"10"
  }

  /**
   * Tests that iterating over a BitSet yields the expected string.
   * 测试遍历BitSet是否产生预期的字符串
   *
   * @param expected Expected string，期望的字符串表示
   * @param bitSet   Bit set，要测试的BitSet对象
   */
  private void assertToIterBitSet(final String expected, BitSet bitSet) { // 私有辅助方法，用于验证toIter的结果
    StringBuilder buf = new StringBuilder(); // 创建StringBuilder对象用于构建结果字符串
    for (int i : BitSets.toIter(bitSet)) { // 遍历BitSet.toIter()返回的可迭代集合，i是被设置为true的位的索引
      if (buf.length() > 0) { // 如果buf不为空，说明不是第一个元素
        buf.append(", "); // 在元素之间添加逗号和空格分隔符
      }
      buf.append(i); // 将当前位的索引添加到buf中
    }
    assertThat(buf, hasToString(expected)); // 使用Hamcrest断言验证buf的toString结果是否等于expected
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.BitSets#toList(java.util.BitSet)}.
   * 测试BitSets.toList()方法，该方法将BitSet转换为整数列表
   * toList方法返回一个List<Integer>，包含BitSet中所有被设置为true的位的索引
   */
  @Test void testToListBitSet() { // 测试方法，使用@Test注解标记
    BitSet bitSet = new BitSet(10); // 创建一个初始容量为10的BitSet对象
    assertThat(Collections.<Integer>emptyList(), is(BitSets.toList(bitSet))); // 断言空BitSet转换为列表后等于空列表
    bitSet.set(5); // 设置BitSet的第5位为true
    assertThat(BitSets.toList(bitSet), isListOf(5)); // 断言BitSet转换为列表后等于[5]
    bitSet.set(3); // 设置BitSet的第3位为true
    assertThat(BitSets.toList(bitSet), isListOf(3, 5)); // 断言BitSet转换为列表后等于[3, 5]，注意列表是有序的
  }

  /**
   * Tests the method {@link org.apache.calcite.util.BitSets#of(int...)}.
   * 测试BitSets.of()方法，该方法根据一组整数创建BitSet
   * of方法接受可变参数，返回一个包含这些索引位置的BitSet
   */
  @Test void testBitSetOf() { // 测试方法，使用@Test注解标记
    assertThat(BitSets.toList(BitSets.of(0, 4, 2)), isListOf(0, 2, 4)); // 断言根据整数0,4,2创建BitSet后转换为列表等于[0,2,4]，注意结果是有序的
    assertThat(BitSets.toList(BitSets.of()), empty()); // 断言不传参数创建的BitSet转换为列表后为空列表
  }

  /**
   * Tests the method {@link org.apache.calcite.util.BitSets#range(int, int)}.
   * 测试BitSets.range()方法，该方法创建一个包含指定范围内所有整数的BitSet
   * range(from, to)方法创建一个包含从from（包含）到to（不包含）之间所有整数的BitSet
   */
  @Test void testBitSetsRange() { // 测试方法，使用@Test注解标记
    assertThat(BitSets.toList(BitSets.range(0, 4)), isListOf(0, 1, 2, 3)); // 断言range(0,4)创建的BitSet包含0,1,2,3
    assertThat(BitSets.toList(BitSets.range(1, 4)), isListOf(1, 2, 3)); // 断言range(1,4)创建的BitSet包含1,2,3
    assertThat(BitSets.toList(BitSets.range(2, 2)), empty()); // 断言range(2,2)创建的BitSet为空，因为起始和结束相同
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.BitSets#toArray(java.util.BitSet)}.
   * 测试BitSets.toArray()方法，该方法将BitSet转换为整数数组
   * toArray方法返回一个int[]数组，包含BitSet中所有被设置为true的位的索引
   */
  @Test void testBitSetsToArray() { // 测试方法，使用@Test注解标记
    int[][] arrays = {{}, {0}, {0, 2}, {1, 65}, {100}}; // 定义多个测试用的整数数组
    for (int[] array : arrays) { // 遍历每个测试数组
      assertThat(BitSets.toArray(BitSets.of(array)), is(array)); // 断言将数组转为BitSet再转回数组，结果应该与原数组相同
    }
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.BitSets#union(java.util.BitSet, java.util.BitSet...)}.
   * 测试BitSets.union()方法，该方法计算多个BitSet的并集
   * union方法接受一个主BitSet和多个可选的BitSet参数，返回包含所有参数中被设置为true的位的BitSet
   */
  @Test void testBitSetsUnion() { // 测试方法，使用@Test注解标记
    assertThat(BitSets.union(BitSets.of(1), BitSets.of(3)), // 断言{1}和{3}的并集是{1,3}
        hasToString("{1, 3}")); // 使用hasToString匹配器验证toString输出
    assertThat(BitSets.union(BitSets.of(1), BitSets.of(3, 100)), // 断言{1}和{3,100}的并集是{1,3,100}
        hasToString("{1, 3, 100}")); // 使用hasToString匹配器验证toString输出
    assertThat( // 断言多个BitSet的并集
        BitSets.union(BitSets.of(1), BitSets.of(2), BitSets.of(), BitSets.of(3)), // {1},{2},{},{3}的并集
        hasToString("{1, 2, 3}")); // 结果应该是{1,2,3}
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.BitSets#contains(java.util.BitSet, java.util.BitSet)}.
   * 测试BitSets.contains()方法，该方法判断一个BitSet是否包含另一个BitSet
   * contains方法返回true当且仅当第二个BitSet中所有被设置为true的位在第一个BitSet中也都被设置为true
   */
  @Test void testBitSetsContains() { // 测试方法，使用@Test注解标记
    assertTrue(BitSets.contains(BitSets.range(0, 5), BitSets.range(2, 4))); // 断言{0,1,2,3,4}包含{2,3}
    assertTrue(BitSets.contains(BitSets.range(0, 5), BitSets.of(4))); // 断言{0,1,2,3,4}包含{4}
    assertFalse(BitSets.contains(BitSets.range(0, 5), BitSets.of(14))); // 断言{0,1,2,3,4}不包含{14}
    assertFalse(BitSets.contains(BitSets.range(20, 25), BitSets.of(14))); // 断言{20,21,22,23,24}不包含{14}
    final BitSet empty = BitSets.of(); // 创建一个空的BitSet
    assertTrue(BitSets.contains(BitSets.range(20, 25), empty)); // 断言任何BitSet都包含空BitSet
    assertTrue(BitSets.contains(empty, empty)); // 断言空BitSet包含空BitSet
    assertFalse(BitSets.contains(empty, BitSets.of(0))); // 断言空BitSet不包含{0}
    assertFalse(BitSets.contains(empty, BitSets.of(1))); // 断言空BitSet不包含{1}
    assertFalse(BitSets.contains(empty, BitSets.of(1000))); // 断言空BitSet不包含{1000}
    assertTrue(BitSets.contains(BitSets.of(1, 4, 7), BitSets.of(1, 4, 7))); // 断言BitSet包含自身
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.BitSets#of(ImmutableIntList)}.
   * 测试BitSets.of()方法的重载版本，该方法根据ImmutableIntList创建BitSet
   * of方法接受一个ImmutableIntList参数，返回一个包含列表中所有整数的BitSet
   */
  @Test void testBitSetOfImmutableIntList() { // 测试方法，使用@Test注解标记
    ImmutableIntList list = ImmutableIntList.of(); // 创建一个空的ImmutableIntList
    assertThat(BitSets.of(list), equalTo(new BitSet())); // 断言空列表转换为BitSet后等于空BitSet

    list = ImmutableIntList.of(2, 70, 5, 0); // 创建包含2,70,5,0的ImmutableIntList
    assertThat(BitSets.of(list), equalTo(BitSets.of(0, 2, 5, 70))); // 断言列表转换为BitSet后等于直接用of(0,2,5,70)创建的BitSet
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.BitSets#previousClearBit(java.util.BitSet, int)}.
   * 测试BitSets.previousClearBit()方法，该方法查找指定位置之前的第一个未被设置的位
   * previousClearBit方法返回小于等于指定索引的最大索引，该索引处的位未被设置为true
   */
  @Test void testPreviousClearBit() { // 测试方法，使用@Test注解标记
    assertThat(BitSets.previousClearBit(BitSets.of(), 10), equalTo(10)); // 断言空BitSet中，位置10之前的第一个clear位是10本身
    assertThat(BitSets.previousClearBit(BitSets.of(), 0), equalTo(0)); // 断言空BitSet中，位置0之前的第一个clear位是0本身
    assertThat(BitSets.previousClearBit(BitSets.of(), -1), equalTo(-1)); // 断言空BitSet中，位置-1之前的第一个clear位是-1本身
    try { // 测试异常情况
      final int actual = BitSets.previousClearBit(BitSets.of(), -2); // 尝试查找-2之前的clear位，这应该抛出异常
      fail("expected exception, got " + actual); // 如果没有抛出异常，测试失败
    } catch (IndexOutOfBoundsException e) { // 捕获预期的IndexOutOfBoundsException异常
      // ok，测试通过
    }
    assertThat(BitSets.previousClearBit(BitSets.of(0, 1, 3, 4), 4), equalTo(2)); // 断言在{0,1,3,4}中，位置4之前的第一个clear位是2
    assertThat(BitSets.previousClearBit(BitSets.of(0, 1, 3, 4), 3), equalTo(2)); // 断言在{0,1,3,4}中，位置3之前的第一个clear位是2
    assertThat(BitSets.previousClearBit(BitSets.of(0, 1, 3, 4), 2), equalTo(2)); // 断言在{0,1,3,4}中，位置2之前的第一个clear位是2本身（因为位置2就是clear的）
    assertThat(BitSets.previousClearBit(BitSets.of(0, 1, 3, 4), 1), // 断言在{0,1,3,4}中，位置1之前的第一个clear位
        equalTo(-1)); // 是-1，因为0和1都被设置了，没有更小的clear位
    assertThat(BitSets.previousClearBit(BitSets.of(1, 3, 4), 1), equalTo(0)); // 断言在{1,3,4}中，位置1之前的第一个clear位是0
  }

  /** Tests the method {@link BitSets#closure(java.util.SortedMap)}.
   * 测试BitSets.closure()方法，该方法计算传递闭包
   * closure方法接受一个SortedMap<Integer, BitSet>，其中每个键值对表示一个位置及其可达位置
   * 方法返回一个新映射，其中每个位置的值包含该位置通过传递关系可达的所有位置
   */
  @Test void testClosure() { // 测试方法，使用@Test注解标记
    final SortedMap<Integer, BitSet> empty = new TreeMap<>(); // 创建一个空的TreeMap
    assertThat(BitSets.closure(empty), equalTo(empty)); // 断言空映射的闭包仍然是空映射

    // Map with an entry for each position.
    // 创建一个包含每个位置条目的映射
    final SortedMap<Integer, BitSet> map = new TreeMap<>(); // 创建TreeMap用于存储位置和可达位置的关系
    map.put(0, BitSets.of(3)); // 位置0可以到达位置3
    map.put(1, BitSets.of()); // 位置1无法到达任何位置
    map.put(2, BitSets.of(7)); // 位置2可以到达位置7
    map.put(3, BitSets.of(4, 12)); // 位置3可以到达位置4和12
    map.put(4, BitSets.of()); // 位置4无法到达任何位置
    map.put(5, BitSets.of()); // 位置5无法到达任何位置
    map.put(6, BitSets.of()); // 位置6无法到达任何位置
    map.put(7, BitSets.of()); // 位置7无法到达任何位置
    map.put(8, BitSets.of()); // 位置8无法到达任何位置
    map.put(9, BitSets.of()); // 位置9无法到达任何位置
    map.put(10, BitSets.of()); // 位置10无法到达任何位置
    map.put(11, BitSets.of()); // 位置11无法到达任何位置
    map.put(12, BitSets.of()); // 位置12无法到达任何位置
    final String original = map.toString(); // 保存原始映射的字符串表示，用于后续验证原映射未被修改
    final String expected = // 预期的闭包结果字符串
        "{0={3, 4, 12}, 1={}, 2={7}, 3={3, 4, 12}, 4={4, 12}, 5={}, 6={}, 7={7}, 8={}, 9={}, 10={}, 11={}, 12={4, 12}}"; // 解释：0->3->4->12，所以0可以通过传递到达3,4,12；3包含自身(自反)加上3->4->12；4可以通过传递到达12
    assertThat(BitSets.closure(map), hasToString(expected)); // 断言闭包的结果等于预期值
    assertThat("argument modified", map, hasToString(original)); // 断言原始映射未被修改

    // Now a similar map with missing entries. Same result.
    // 现在使用一个类似但缺少某些条目的映射，应该得到相同的结果
    final SortedMap<Integer, BitSet> map2 = new TreeMap<>(); // 创建第二个TreeMap，这次不包含所有位置
    map2.put(0, BitSets.of(3)); // 位置0可以到达位置3
    map2.put(2, BitSets.of(7)); // 位置2可以到达位置7
    map2.put(3, BitSets.of(4, 12)); // 位置3可以到达位置4和12
    map2.put(9, BitSets.of()); // 位置9无法到达任何位置
    final String original2 = map2.toString(); // 保存原始映射的字符串表示
    assertThat(BitSets.closure(map2), hasToString(expected)); // 断言即使映射缺少某些条目，闭包结果仍然相同
    assertThat("argument modified", map2, hasToString(original2)); // 断言原始映射未被修改
  }
} // 类定义结束
