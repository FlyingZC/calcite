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
package org.apache.calcite.util; // 声明包名,该测试类位于org.apache.calcite.util包下
import org.apache.calcite.runtime.Utilities; // 导入Calcite运行时工具类,用于比较等操作

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类
import com.google.common.collect.Iterables; // 导入Google Guava的迭代工具类
import com.google.common.primitives.Ints; // 导入Google Guava的int基本类型工具类

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解,用于标记测试方法

import java.nio.LongBuffer; // 导入Java NIO的LongBuffer类,用于处理long数组缓冲区
import java.util.ArrayList; // 导入Java集合框架的ArrayList类
import java.util.Arrays; // 导入Java数组工具类
import java.util.Collection; // 导入Java集合接口
import java.util.Collections; // 导入Java集合工具类
import java.util.HashSet; // 导入Java集合框架的HashSet类
import java.util.List; // 导入Java列表接口
import java.util.Set; // 导入Java集合接口
import java.util.SortedMap; // 导入Java排序映射接口
import java.util.TreeMap; // 导入Java排序映射实现类TreeMap
import java.util.TreeSet; // 导入Java排序集合实现类TreeSet
import java.util.function.BiConsumer; // 导入Java函数式接口BiConsumer,接受两个参数
import java.util.function.Consumer; // 导入Java函数式接口Consumer,接受一个参数
import java.util.function.IntConsumer; // 导入Java函数式接口IntConsumer,接受int参数
import java.util.function.IntPredicate; // 导入Java函数式接口IntPredicate,接受int参数返回boolean
import java.util.stream.Collectors; // 导入Java流收集器工具类

import static org.hamcrest.CoreMatchers.equalTo; // 导入Hamcrest匹配器,用于判断相等
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器,用于判断相等
import static org.hamcrest.CoreMatchers.sameInstance; // 导入Hamcrest匹配器,用于判断是否同一实例
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest匹配器,用于判断集合大小
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest匹配器,用于判断字符串表示
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit 5断言方法,判断为false
import static org.junit.jupiter.api.Assertions.assertNotSame; // 导入JUnit 5断言方法,判断不是同一实例
import static org.junit.jupiter.api.Assertions.assertSame; // 导入JUnit 5断言方法,判断是同一实例
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入JUnit 5断言方法,判断抛出异常
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5断言方法,判断为true
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit 5断言方法,标记测试失败

/**
 * Unit test for {@link org.apache.calcite.util.ImmutableBitSet}. // ImmutableBitSet类的单元测试类
 * 该测试类全面测试了ImmutableBitSet(不可变位集合)的所有功能,包括:
 * 1. 迭代器功能 - 遍历位集合中的所有设置位
 * 2. 构造方法 - 各种方式创建位集合
 * 3. 范围操作 - 创建指定范围的位集合
 * 4. 集合操作 - 并集、交集、包含关系等
 * 5. 位操作 - 设置、清除、查找等
 * 6. 转换操作 - 转换为数组、列表、集合等
 * 7. 比较操作 - 排序、比较等
 * 8. 特殊功能 - 闭包计算、幂集生成等
 */
  /** Tests the method {@link ImmutableBitSet#iterator()}. */
  @Test void testIterator() {
    assertToIterBitSet("", ImmutableBitSet.of());
    assertToIterBitSet("0", ImmutableBitSet.of(0));
    assertToIterBitSet("0, 1", ImmutableBitSet.of(0, 1));
    assertToIterBitSet("10", ImmutableBitSet.of(10));

    check((bitSet, list) -> {
      final List<Integer> list2 = new ArrayList<>();
      for (Integer integer : bitSet) {
        list2.add(integer);
      }
      assertThat(list2, equalTo(list));
    });
  }

  /** Tests the method {@link ImmutableBitSet#of(int)}. */ // 测试ImmutableBitSet.of(int)单元素构造方法
  @Test void testSingletonConstructor() { // 定义测试方法testSingletonConstructor
    IntConsumer c = i -> { // 定义IntConsumer函数式接口,接受int参数i
      final ImmutableBitSet s0 = ImmutableBitSet.of(i); // 使用of(int)方法创建包含元素i的位集合
      final ImmutableBitSet s1 = ImmutableBitSet.of(ImmutableIntList.of(i)); // 使用of(ImmutableIntList)方法创建位集合
      final ImmutableBitSet s2 = ImmutableBitSet.of(Collections.singleton(i)); // 使用of(Set)方法创建位集合
      final ImmutableBitSet s3 = // 通过设置和清除操作创建包含元素i的位集合
          ImmutableBitSet.of(99, 100).set(i).clear(100).clear(99); // 先创建{99,100},设置i,然后清除99和100
      assertThat(s0.cardinality(), is(1)); // 断言s0的基数(元素个数)为1
      assertThat(s0, is(s1)); // 断言s0和s1相等
      assertThat(s0, is(s2)); // 断言s0和s2相等
      assertThat(s0, is(s3)); // 断言s0和s3相等
      assertThat(s1, is(s2)); // 断言s1和s2相等
      assertThat(s1, is(s3)); // 断言s1和s3相等
      assertThat(s2, is(s3)); // 断言s2和s3相等
    };
    c.accept(0); // 测试元素0
    c.accept(1); // 测试元素1
    c.accept(63); // 测试元素63(一个long的边界值)
    c.accept(64); // 测试元素64(跨越long边界的值)
  }

  @Test void testNegative() { // 定义测试方法testNegative,测试负数索引
    assertThrows(IndexOutOfBoundsException.class, // 断言抛出IndexOutOfBoundsException异常
        () -> ImmutableBitSet.of(-1)); // 尝试创建包含-1的位集合,应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, // 断言抛出IndexOutOfBoundsException异常
        () -> ImmutableBitSet.of(-2)); // 尝试创建包含-2的位集合,应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, // 断言抛出IndexOutOfBoundsException异常
        () -> ImmutableBitSet.of(1, 10, -1, 63)); // 尝试创建包含-1的位集合,应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, // 断言抛出IndexOutOfBoundsException异常
        () -> ImmutableBitSet.of(-1, 10)); // 尝试创建包含-1的位集合,应该抛出异常
    assertThrows(IndexOutOfBoundsException.class, // 断言抛出IndexOutOfBoundsException异常
        () -> ImmutableBitSet.of(Collections.singleton(-2))); // 尝试创建包含-2的位集合,应该抛出异常
  }

  /**
   * Tests that iterating over an
   * {@link org.apache.calcite.util.ImmutableBitSet} yields the expected string.
   * // 测试遍历ImmutableBitSet是否产生预期的字符串
   *
   * @param expected Expected string // 预期的字符串表示
   * @param bitSet   Bit set // 要测试的位集合
   */
  private void assertToIterBitSet(String expected, ImmutableBitSet bitSet) { // 定义辅助方法assertToIterBitSet
    StringBuilder buf = new StringBuilder(); // 创建StringBuilder用于构建字符串
    for (int i : bitSet) { // 使用增强for循环遍历位集合
      if (buf.length() > 0) { // 如果缓冲区不为空
        buf.append(", "); // 添加逗号和空格分隔符
      }
      buf.append(i); // 添加当前元素
    }
    assertThat(buf, hasToString(expected)); // 断言构建的字符串与预期字符串相等

    // Now check that bitSet.stream() does the same as bitSet.iterator(). // 现在检查stream()方法与iterator()方法是否产生相同结果
    buf.setLength(0); // 清空StringBuilder
    bitSet.stream().forEach(i -> { // 使用stream()方法遍历位集合
      if (buf.length() > 0) { // 如果缓冲区不为空
        buf.append(", "); // 添加逗号和空格分隔符
      }
      buf.append(i); // 添加当前元素
    });
    assertThat(buf, hasToString(expected)); // 断言stream()方法产生的字符串与预期字符串相等
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#toList()}.
   * // 测试ImmutableBitSet.toList()方法,将位集合转换为列表
   */
  @Test void testToList() { // 定义测试方法testToList
    check((bitSet, list) -> assertThat(bitSet.toList(), equalTo(list))); // 使用check方法验证toList()结果与预期列表相等
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#stream()}.
   * // 测试ImmutableBitSet.stream()方法,将位集合转换为流
   */
  @Test void testStream() { // 定义测试方法testStream
    check((bitSet, list) -> // 使用check方法验证stream()方法
        assertThat(bitSet.stream().boxed().collect(Collectors.toList()), // 将int流装箱为Integer流并收集为List
            equalTo(list))); // 断言结果与预期列表相等
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#forEachInt}.
   * // 测试ImmutableBitSet.forEachInt()方法,使用IntConsumer遍历位集合
   */
  @Test void testForEachInt() { // 定义测试方法testForEachInt
    check((bitSet, list) -> { // 使用check方法验证forEachInt()方法
      final List<Integer> list2 = new ArrayList<>(); // 创建新的ArrayList用于存储遍历结果
      bitSet.forEachInt(list2::add); // 使用forEachInt方法,将每个元素添加到list2中
      assertThat(list2, equalTo(list)); // 断言遍历结果与预期列表相等
    });
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#forEach}.
   * // 测试ImmutableBitSet.forEach()方法,使用Consumer<Integer>遍历位集合
   */
  @Test void testForEachInteger() { // 定义测试方法testForEachInteger
    check((bitSet, list) -> { // 使用check方法验证forEach()方法
      final List<Integer> list2 = new ArrayList<>(); // 创建新的ArrayList用于存储遍历结果
      bitSet.forEach(list2::add); // 使用forEach方法,将每个元素添加到list2中
      assertThat(list2, equalTo(list)); // 断言遍历结果与预期列表相等
    });
  }

  private void check(BiConsumer<ImmutableBitSet, List<Integer>> consumer) { // 定义辅助方法check,用于测试各种位集合操作
    consumer.accept(ImmutableBitSet.of(), Collections.emptyList()); // 测试空位集合
    consumer.accept(ImmutableBitSet.of(5), Collections.singletonList(5)); // 测试单个元素5
    consumer.accept(ImmutableBitSet.of(3, 5), Arrays.asList(3, 5)); // 测试两个元素3和5
    consumer.accept(ImmutableBitSet.of(63), Collections.singletonList(63)); // 测试元素63(long边界值)
    consumer.accept(ImmutableBitSet.of(64), Collections.singletonList(64)); // 测试元素64(跨越long边界)
    consumer.accept(ImmutableBitSet.of(3, 63), Arrays.asList(3, 63)); // 测试元素3和63
    consumer.accept(ImmutableBitSet.of(3, 64), Arrays.asList(3, 64)); // 测试元素3和64
    consumer.accept(ImmutableBitSet.of(0, 4, 2), Arrays.asList(0, 2, 4)); // 测试元素0,4,2(验证排序)
  }

  /**
   * Tests the method {@link BitSets#range(int, int)}.
   * // 测试ImmutableBitSet.range()方法,创建指定范围的位集合
   */
  @Test void testRange() { // 定义测试方法testRange
    final List<Integer> list0123 = Arrays.asList(0, 1, 2, 3); // 创建包含0,1,2,3的列表
    final List<Integer> list123 = Arrays.asList(1, 2, 3); // 创建包含1,2,3的列表
    final List<Integer> listEmpty = Collections.emptyList(); // 创建空列表

    assertThat(ImmutableBitSet.range(0, 4).toList(), is(list0123)); // 测试range(0,4),应该返回{0,1,2,3}
    assertThat(ImmutableBitSet.range(1, 4).toList(), is(list123)); // 测试range(1,4),应该返回{1,2,3}
    assertThat(ImmutableBitSet.range(4).toList(), is(list0123)); // 测试range(4),应该返回{0,1,2,3}
    assertThat(ImmutableBitSet.range(0).toList(), is(listEmpty)); // 测试range(0),应该返回空集合
    assertThat(ImmutableBitSet.range(2, 2).toList(), is(listEmpty)); // 测试range(2,2),应该返回空集合

    assertThat(ImmutableBitSet.range(63, 66), // 测试range(63,66),跨越long边界
        hasToString("{63, 64, 65}")); // 应该返回{63,64,65}
    assertThat(ImmutableBitSet.range(65, 68), // 测试range(65,68)
        hasToString("{65, 66, 67}")); // 应该返回{65,66,67}
    assertThat(ImmutableBitSet.range(65, 65), hasToString("{}")); // 测试range(65,65),应该返回空集合
    assertThat(ImmutableBitSet.range(65, 65).length(), equalTo(0)); // 测试range(65,65)的长度为0
    assertThat(ImmutableBitSet.range(65, 165).cardinality(), equalTo(100)); // 测试range(65,165),应该包含100个元素

    // Same tests as above, using a builder. // 使用builder进行相同的测试
    assertThat(ImmutableBitSet.builder().set(63, 66).build(), // 使用builder设置范围63到66
        hasToString("{63, 64, 65}")); // 应该返回{63,64,65}
    assertThat(ImmutableBitSet.builder().set(65, 68).build(), // 使用builder设置范围65到68
        hasToString("{65, 66, 67}")); // 应该返回{65,66,67}
    assertThat(ImmutableBitSet.builder().set(65, 65).build(), // 使用builder设置范围65到65
        hasToString("{}")); // 应该返回空集合
    assertThat(ImmutableBitSet.builder().set(65, 65).build().length(), // 测试空范围的长度
        equalTo(0)); // 应该为0
    assertThat(ImmutableBitSet.builder().set(65, 165).build().cardinality(), // 测试大范围的基数
        equalTo(100)); // 应该为100

    final ImmutableBitSet e0 = ImmutableBitSet.range(0, 0); // 创建空范围位集合
    final ImmutableBitSet e1 = ImmutableBitSet.of(); // 创建空位集合
    assertThat(e0, is(e1)); // 断言两者相等
    assertThat(e0.hashCode(), equalTo(e1.hashCode())); // 断言两者的hashCode相等

    // Empty builder returns the singleton empty set. // 空builder返回单例空集合
    assertThat(ImmutableBitSet.builder().build(), // 创建空builder并构建
        sameInstance(ImmutableBitSet.of())); // 应该返回与ImmutableBitSet.of()相同的实例
  }

  @Test void testCompare() { // 定义测试方法testCompare,测试位集合的比较功能
    final List<ImmutableBitSet> sorted = getSortedList(); // 获取测试用的位集合列表
    for (int i = 0; i < sorted.size(); i++) { // 外层循环,遍历列表中的每个元素
      for (int j = 0; j < sorted.size(); j++) { // 内层循环,遍历列表中的每个元素
        final ImmutableBitSet set0 = sorted.get(i); // 获取索引i的位集合
        final ImmutableBitSet set1 = sorted.get(j); // 获取索引j的位集合
        int c = set0.compareTo(set1); // 比较两个位集合
        if (c == 0) { // 如果比较结果为0,表示相等
          assertTrue(i == j || i == 3 && j == 4 || i == 4 && j == 3); // 断言相等的情况
        } else { // 如果不相等
          assertThat(Utilities.compare(i, j), is(c)); // 断言比较结果与索引比较一致
        }
        assertThat(set0.equals(set1), is(c == 0)); // 断言equals方法与compareTo方法一致
        assertThat(set1.equals(set0), is(c == 0)); // 断言equals方法是对称的
      }
    }
  }

  @Test void testCompare2() { // 定义测试方法testCompare2,测试使用COMPARATOR排序
    final List<ImmutableBitSet> sorted = getSortedList(); // 获取测试用的位集合列表
    sorted.sort(ImmutableBitSet.COMPARATOR); // 使用ImmutableBitSet的COMPARATOR进行排序
    assertThat(sorted, // 断言排序后的结果
        hasToString("[{0, 1, 3}, {0, 1}, {1, 1000}, {1}, {1}, {2, 3}, {}]")); // 期望的排序结果
  }

  private List<ImmutableBitSet> getSortedList() { // 定义辅助方法getSortedList,返回测试用的位集合列表
    return Arrays.asList( // 返回包含各种位集合的列表
        ImmutableBitSet.of(), // 空位集合
        ImmutableBitSet.of(0, 1), // 包含0和1的位集合
        ImmutableBitSet.of(0, 1, 3), // 包含0,1,3的位集合
        ImmutableBitSet.of(1), // 包含1的位集合
        ImmutableBitSet.of(1), // 另一个包含1的位集合(用于测试相等)
        ImmutableBitSet.of(1, 1000), // 包含1和1000的位集合
        ImmutableBitSet.of(2, 3)); // 包含2和3的位集合
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#toArray}.
   * // 测试ImmutableBitSet.toArray()方法,将位集合转换为int数组
   */
  @Test void testToArray() { // 定义测试方法testToArray
    int[][] arrays = {{}, {0}, {0, 2}, {1, 65}, {100}}; // 创建测试用的int数组数组
    for (int[] array : arrays) { // 遍历每个测试数组
      assertThat(ImmutableBitSet.of(array).toArray(), equalTo(array)); // 断言toArray()结果与原数组相等
    }
  }

  /**
   * Tests the methods
   * {@link org.apache.calcite.util.ImmutableBitSet#toList}, and
   * {@link org.apache.calcite.util.ImmutableBitSet#asList} and
   * {@link org.apache.calcite.util.ImmutableBitSet#asSet}.
   * // 测试toList()、asList()和asSet()方法
   */
  @Test void testAsList() { // 定义测试方法testAsList
    final List<ImmutableBitSet> list = getSortedList(); // 获取测试用的位集合列表

    // create a set of integers in and not in the lists // 创建包含和不包含在列表中的整数集合
    final Set<Integer> integers = new HashSet<>(); // 创建HashSet用于存储测试整数
    for (ImmutableBitSet set : list) { // 遍历每个位集合
      for (Integer integer : set) { // 遍历位集合中的每个整数
        integers.add(integer); // 添加整数本身
        integers.add(integer + 1); // 添加整数+1
        integers.add(integer + 10); // 添加整数+10
      }
    }

    for (ImmutableBitSet bitSet : list) { // 遍历每个位集合
      final List<Integer> list1 = bitSet.toList(); // 使用toList()转换为列表
      final List<Integer> listView = bitSet.asList(); // 使用asList()获取列表视图
      final Set<Integer> setView = bitSet.asSet(); // 使用asSet()获取集合视图
      assertThat(list1, hasSize(bitSet.cardinality())); // 断言toList()结果大小与基数一致
      assertThat(listView, hasSize(bitSet.cardinality())); // 断言asList()结果大小与基数一致
      assertThat(setView, hasSize(bitSet.cardinality())); // 断言asSet()结果大小与基数一致
      assertThat(list1, hasToString(listView.toString())); // 断言toList()和asList()字符串表示相同
      assertThat(list1, hasToString(setView.toString())); // 断言toList()和asSet()字符串表示相同
      assertThat(list1.equals(listView), is(true)); // 断言toList()和asList()相等
      assertThat(list1.hashCode(), equalTo(listView.hashCode())); // 断言hashCode相同

      final Set<Integer> set = new HashSet<>(list1); // 从list1创建HashSet
      assertThat(setView.hashCode(), is(set.hashCode())); // 断言asSet()的hashCode与HashSet相同
      assertThat(setView, equalTo(set)); // 断言asSet()与HashSet相等

      for (Integer integer : integers) { // 遍历测试整数集合
        final boolean b = list1.contains(integer); // 检查list1是否包含该整数
        assertThat(listView.contains(integer), is(b)); // 断言listView包含结果与list1一致
        assertThat(setView.contains(integer), is(b)); // 断言setView包含结果与list1一致
      }
    }
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#union(ImmutableBitSet)}.
   * // 测试ImmutableBitSet.union()方法,计算两个位集合的并集
   */
  @Test void testUnion() { // 定义测试方法testUnion
    assertThat(ImmutableBitSet.of(1).union(ImmutableBitSet.of(3)), // 测试{1}和{3}的并集
        hasToString("{1, 3}")); // 应该返回{1,3}
    assertThat(ImmutableBitSet.of(1).union(ImmutableBitSet.of(3, 100)), // 测试{1}和{3,100}的并集
        hasToString("{1, 3, 100}")); // 应该返回{1,3,100}
    ImmutableBitSet x = // 使用rebuild()方法构建位集合
        ImmutableBitSet.of(1) // 从{1}开始
            .rebuild() // 创建builder
            .addAll(ImmutableBitSet.of(2)) // 添加{2}
            .addAll(ImmutableBitSet.of()) // 添加空集合
            .addAll(ImmutableBitSet.of(3)) // 添加{3}
            .build(); // 构建最终的位集合
    assertThat(x, hasToString("{1, 2, 3}")); // 断言结果为{1,2,3}
  }

  @Test void testIntersect() { // 定义测试方法testIntersect,测试交集运算
    assertThat(ImmutableBitSet.of(1, 2, 3, 100, 200) // 测试{1,2,3,100,200}和{2,100}的交集
        .intersect(ImmutableBitSet.of(2, 100)), // 应该返回{2,100}
        hasToString("{2, 100}"));
    assertThat(ImmutableBitSet.of(1, 3, 5, 101, 20001) // 测试不相交的两个集合
        .intersect(ImmutableBitSet.of(2, 100)), // 应该返回空集合
        sameInstance(ImmutableBitSet.of())); // 断言返回单例空集合
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#contains(org.apache.calcite.util.ImmutableBitSet)}.
   * // 测试ImmutableBitSet.contains()方法,判断一个位集合是否包含另一个位集合
   */
  @Test void testBitSetsContains() { // 定义测试方法testBitSetsContains
    assertTrue(ImmutableBitSet.range(0, 5) // 测试{0,1,2,3,4}是否包含{2,3}
        .contains(ImmutableBitSet.range(2, 4))); // 应该返回true
    assertTrue(ImmutableBitSet.range(0, 5).contains(ImmutableBitSet.range(4))); // 测试是否包含{4}
    assertFalse(ImmutableBitSet.range(0, 5).contains(ImmutableBitSet.of(14))); // 测试是否包含{14},应该返回false
    assertFalse(ImmutableBitSet.range(20, 25).contains(ImmutableBitSet.of(14))); // 测试{20-24}是否包含{14},应该返回false
    final ImmutableBitSet empty = ImmutableBitSet.of(); // 创建空位集合
    assertTrue(ImmutableBitSet.range(20, 25).contains(empty)); // 测试任何集合都包含空集合
    assertTrue(empty.contains(empty)); // 测试空集合包含空集合
    assertFalse(empty.contains(ImmutableBitSet.of(0))); // 测试空集合不包含{0}
    assertFalse(empty.contains(ImmutableBitSet.of(1))); // 测试空集合不包含{1}
    assertFalse(empty.contains(ImmutableBitSet.of(63))); // 测试空集合不包含{63}
    assertFalse(empty.contains(ImmutableBitSet.of(64))); // 测试空集合不包含{64}
    assertFalse(empty.contains(ImmutableBitSet.of(1000))); // 测试空集合不包含{1000}
    assertTrue(ImmutableBitSet.of(1, 4, 7) // 测试{1,4,7}是否包含{1,4,7}
        .contains(ImmutableBitSet.of(1, 4, 7))); // 应该返回true(相同集合)
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#of(org.apache.calcite.util.ImmutableIntList)}.
   * // 测试从ImmutableIntList创建ImmutableBitSet的方法
   */
  @Test void testBitSetOfImmutableIntList() { // 定义测试方法testBitSetOfImmutableIntList
    ImmutableIntList list = ImmutableIntList.of(); // 创建空的ImmutableIntList
    assertThat(ImmutableBitSet.of(list), equalTo(ImmutableBitSet.of())); // 断言从空列表创建的位集合为空

    list = ImmutableIntList.of(2, 70, 5, 0); // 创建包含2,70,5,0的ImmutableIntList
    assertThat(ImmutableBitSet.of(list), // 从列表创建位集合
        equalTo(ImmutableBitSet.of(0, 2, 5, 70))); // 断言结果为排序后的{0,2,5,70}
  }

  /**
   * Tests the method
   * {@link org.apache.calcite.util.ImmutableBitSet#previousClearBit(int)}.
   * // 测试previousClearBit()方法,查找指定位置之前的第一个清除位
   */
  @Test void testPreviousClearBit() { // 定义测试方法testPreviousClearBit
    assertThat(ImmutableBitSet.of().previousClearBit(10), equalTo(10)); // 空集合中10之前第一个清除位是10
    assertThat(ImmutableBitSet.of().previousClearBit(0), equalTo(0)); // 空集合中0之前第一个清除位是0
    assertThat(ImmutableBitSet.of().previousClearBit(-1), equalTo(-1)); // 空集合中-1之前第一个清除位是-1
    try { // 测试异常情况
      final int actual = ImmutableBitSet.of().previousClearBit(-2); // 尝试查找-2之前的清除位
      fail("expected exception, got " + actual); // 应该抛出异常,如果没有抛出则测试失败
    } catch (IndexOutOfBoundsException e) { // 捕获IndexOutOfBoundsException
      // ok // 异常是预期的
    }
    assertThat(ImmutableBitSet.of(0, 1, 3, 4).previousClearBit(4), equalTo(2)); // {0,1,3,4}中4之前第一个清除位是2
    assertThat(ImmutableBitSet.of(0, 1, 3, 4).previousClearBit(3), equalTo(2)); // {0,1,3,4}中3之前第一个清除位是2
    assertThat(ImmutableBitSet.of(0, 1, 3, 4).previousClearBit(2), equalTo(2)); // {0,1,3,4}中2之前第一个清除位是2
    assertThat(ImmutableBitSet.of(0, 1, 3, 4).previousClearBit(1), // {0,1,3,4}中1之前第一个清除位
        equalTo(-1)); // 应该是-1
    assertThat(ImmutableBitSet.of(1, 3, 4).previousClearBit(1), equalTo(0)); // {1,3,4}中1之前第一个清除位是0
  }

  @Test void testBuilder() { // 定义测试方法testBuilder,测试builder功能
    assertThat(ImmutableBitSet.builder().set(9) // 创建builder,设置位9
            .set(100) // 设置位100
            .set(1000) // 设置位1000
            .clear(250) // 清除位250(未设置,无效果)
            .set(88) // 设置位88
            .clear(100) // 清除位100
            .clear(1000) // 清除位1000
            .build(), // 构建最终的位集合
        hasToString("{9, 88}")); // 断言结果为{9,88}
  }

  /** Unit test for
   * {@link org.apache.calcite.util.ImmutableBitSet.Builder#build(ImmutableBitSet)}. */ // 测试Builder.build(ImmutableBitSet)方法
  @Test void testBuilderUseOriginal() { // 定义测试方法testBuilderUseOriginal
    final ImmutableBitSet fives = ImmutableBitSet.of(5, 10, 15); // 创建包含5,10,15的位集合
    final ImmutableBitSet fives1 = // 使用rebuild()方法构建
        fives.rebuild() // 创建builder
            .clear(2) // 清除位2(未设置,无效果)
            .set(10) // 设置位10(已设置,无效果)
            .build(); // 构建位集合
    assertSame(fives1, fives); // 断言返回的是原始实例(因为没有实际修改)
    final ImmutableBitSet fives2 = // 使用builder构建
        ImmutableBitSet.builder() // 创建新builder
            .addAll(fives) // 添加fives的所有位
            .clear(2) // 清除位2(未设置,无效果)
            .set(10) // 设置位10(已设置,无效果)
            .build(fives); // 使用原始实例作为参数构建
    assertSame(fives2, fives); // 断言返回的是原始实例
    final ImmutableBitSet fives3 = // 使用builder构建但不提供原始实例
        ImmutableBitSet.builder() // 创建新builder
            .addAll(fives) // 添加fives的所有位
            .clear(2) // 清除位2(未设置,无效果)
            .set(10) // 设置位10(已设置,无效果)
            .build(); // 构建位集合
    assertNotSame(fives3, fives); // 断言返回的不是原始实例(因为没有提供原始实例)
    assertThat(fives, is(fives3)); // 断言两者相等
    assertThat(fives2, is(fives3)); // 断言两者相等
  }

  @Test void testIndexOf() { // 定义测试方法testIndexOf,测试indexOf()方法
    assertThat(ImmutableBitSet.of(0, 2, 4).indexOf(0), equalTo(0)); // 测试元素0的索引,应该返回0
    assertThat(ImmutableBitSet.of(0, 2, 4).indexOf(2), equalTo(1)); // 测试元素2的索引,应该返回1
    assertThat(ImmutableBitSet.of(0, 2, 4).indexOf(3), equalTo(-1)); // 测试元素3不存在,应该返回-1
    assertThat(ImmutableBitSet.of(0, 2, 4).indexOf(4), equalTo(2)); // 测试元素4的索引,应该返回2
    assertThat(ImmutableBitSet.of(0, 2, 4).indexOf(5), equalTo(-1)); // 测试元素5不存在,应该返回-1
    assertThat(ImmutableBitSet.of(0, 2, 4).indexOf(-1), equalTo(-1)); // 测试元素-1不存在,应该返回-1
    assertThat(ImmutableBitSet.of(0, 2, 4).indexOf(-2), equalTo(-1)); // 测试元素-2不存在,应该返回-1
    assertThat(ImmutableBitSet.of().indexOf(-1), equalTo(-1)); // 测试空集合中-1不存在,应该返回-1
    assertThat(ImmutableBitSet.of().indexOf(-2), equalTo(-1)); // 测试空集合中-2不存在,应该返回-1
    assertThat(ImmutableBitSet.of().indexOf(0), equalTo(-1)); // 测试空集合中0不存在,应该返回-1
    assertThat(ImmutableBitSet.of().indexOf(1000), equalTo(-1)); // 测试空集合中1000不存在,应该返回-1
  }

  /** Tests {@link ImmutableBitSet.Builder#buildAndReset()}. */ // 测试Builder.buildAndReset()方法
  @Test void testReset() { // 定义测试方法testReset
    final ImmutableBitSet.Builder builder = ImmutableBitSet.builder(); // 创建builder
    builder.set(2); // 设置位2
    assertThat(builder.build(), hasToString("{2}")); // 构建并断言结果为{2}
    try { // 测试builder只能使用一次
      builder.set(4); // 尝试再次使用builder
      fail("expected exception"); // 应该抛出异常
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), is("can only use builder once")); // 断言异常消息
    }
    try { // 测试builder只能使用一次
      final ImmutableBitSet bitSet = builder.build(); // 尝试再次构建
      fail("expected exception, got " + bitSet); // 应该抛出异常
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), is("can only use builder once")); // 断言异常消息
    }
    try { // 测试builder只能使用一次
      final ImmutableBitSet bitSet = builder.buildAndReset(); // 尝试再次构建并重置
      fail("expected exception, got " + bitSet); // 应该抛出异常
    } catch (IllegalArgumentException e) { // 捕获IllegalArgumentException
      assertThat(e.getMessage(), is("can only use builder once")); // 断言异常消息
    }

    final ImmutableBitSet.Builder builder2 = ImmutableBitSet.builder(); // 创建新的builder
    builder2.set(2); // 设置位2
    assertThat(builder2.buildAndReset(), hasToString("{2}")); // 构建并重置,结果为{2}
    assertThat(builder2.buildAndReset(), hasToString("{}")); // 再次构建并重置,结果为空集合
    builder2.set(151); // 设置位151
    builder2.set(3); // 设置位3
    assertThat(builder2.buildAndReset(), hasToString("{3, 151}")); // 构建并重置,结果为{3,151}
  }

  @Test void testNth() { // 定义测试方法testNth,测试nth()方法
    assertThat(ImmutableBitSet.of(0, 2, 4).nth(0), equalTo(0)); // 测试获取第0个元素,应该返回0
    assertThat(ImmutableBitSet.of(0, 2, 4).nth(1), equalTo(2)); // 测试获取第1个元素,应该返回2
    assertThat(ImmutableBitSet.of(0, 2, 4).nth(2), equalTo(4)); // 测试获取第2个元素,应该返回4
    assertThat(ImmutableBitSet.of(0, 2, 63).nth(2), equalTo(63)); // 测试获取第2个元素,应该返回63
    assertThat(ImmutableBitSet.of(0, 2, 64).nth(2), equalTo(64)); // 测试获取第2个元素,应该返回64
    assertThat(ImmutableBitSet.of(64).nth(0), equalTo(64)); // 测试获取第0个元素,应该返回64
    assertThat(ImmutableBitSet.of(64, 65).nth(0), equalTo(64)); // 测试获取第0个元素,应该返回64
    assertThat(ImmutableBitSet.of(64, 65).nth(1), equalTo(65)); // 测试获取第1个元素,应该返回65
    assertThat(ImmutableBitSet.of(64, 128).nth(1), equalTo(128)); // 测试获取第1个元素,应该返回128
    try { // 测试异常情况
      ImmutableBitSet.of().nth(0); // 尝试从空集合获取第0个元素
      fail("expected throw"); // 应该抛出异常
    } catch (IndexOutOfBoundsException e) { // 捕获IndexOutOfBoundsException
      // ok // 异常是预期的
    }
    try { // 测试异常情况
      ImmutableBitSet.of().nth(1); // 尝试从空集合获取第1个元素
      fail("expected throw"); // 应该抛出异常
    } catch (IndexOutOfBoundsException e) { // 捕获IndexOutOfBoundsException
      // ok // 异常是预期的
    }
    try { // 测试异常情况
      ImmutableBitSet.of(64).nth(1); // 尝试从{64}获取第1个元素(不存在)
      fail("expected throw"); // 应该抛出异常
    } catch (IndexOutOfBoundsException e) { // 捕获IndexOutOfBoundsException
      // ok // 异常是预期的
    }
    try { // 测试异常情况
      ImmutableBitSet.of(64).nth(-1); // 尝试从{64}获取第-1个元素(无效索引)
      fail("expected throw"); // 应该抛出异常
    } catch (IndexOutOfBoundsException e) { // 捕获IndexOutOfBoundsException
      // ok // 异常是预期的
    }
  }

  /** Tests the method
   * {@link org.apache.calcite.util.BitSets#closure(java.util.SortedMap)}. */ // 测试closure()方法,计算闭包
  @Test void testClosure() { // 定义测试方法testClosure
    final SortedMap<Integer, ImmutableBitSet> empty = new TreeMap<>(); // 创建空的SortedMap
    assertThat(ImmutableBitSet.closure(empty), equalTo(empty)); // 断言空映射的闭包是空映射

    // Currently you need an entry for each position, otherwise you get an NPE. // 目前需要为每个位置提供条目,否则会抛出NPE
    // We should fix that. // 我们应该修复这个问题
    final SortedMap<Integer, ImmutableBitSet> map = new TreeMap<>(); // 创建SortedMap用于测试闭包
    map.put(0, ImmutableBitSet.of(3)); // 位置0指向{3}
    map.put(1, ImmutableBitSet.of()); // 位置1指向空集合
    map.put(2, ImmutableBitSet.of(7)); // 位置2指向{7}
    map.put(3, ImmutableBitSet.of(4, 12)); // 位置3指向{4,12}
    map.put(4, ImmutableBitSet.of()); // 位置4指向空集合
    map.put(5, ImmutableBitSet.of()); // 位置5指向空集合
    map.put(6, ImmutableBitSet.of()); // 位置6指向空集合
    map.put(7, ImmutableBitSet.of()); // 位置7指向空集合
    map.put(8, ImmutableBitSet.of()); // 位置8指向空集合
    map.put(9, ImmutableBitSet.of()); // 位置9指向空集合
    map.put(10, ImmutableBitSet.of()); // 位置10指向空集合
    map.put(11, ImmutableBitSet.of()); // 位置11指向空集合
    map.put(12, ImmutableBitSet.of()); // 位置12指向空集合
    final String original = map.toString(); // 保存原始映射的字符串表示
    final String expected = // 期望的闭包结果
        "{0={3, 4, 12}, 1={}, 2={7}, 3={3, 4, 12}, 4={4, 12}, 5={}, 6={}, 7={7}, 8={}, 9={}, 10={}, 11={}, 12={4, 12}}";
    assertThat(ImmutableBitSet.closure(map), hasToString(expected)); // 断言闭包结果与期望一致
    assertThat("argument modified", map, hasToString(original)); // 断言原始映射未被修改

    // Now a similar map with missing entries. Same result. // 现在使用缺少条目的映射,结果相同
    final SortedMap<Integer, ImmutableBitSet> map2 = new TreeMap<>(); // 创建新的SortedMap
    map2.put(0, ImmutableBitSet.of(3)); // 位置0指向{3}
    map2.put(2, ImmutableBitSet.of(7)); // 位置2指向{7}
    map2.put(3, ImmutableBitSet.of(4, 12)); // 位置3指向{4,12}
    map2.put(9, ImmutableBitSet.of()); // 位置9指向空集合
    final String original2 = map2.toString(); // 保存原始映射的字符串表示
    assertThat(ImmutableBitSet.closure(map2), hasToString(expected)); // 断言闭包结果与期望一致
    assertThat("argument modified", map2, hasToString(original2)); // 断言原始映射未被修改
  }

  @Test void testPowerSet() { // 定义测试方法testPowerSet,测试幂集生成
    final ImmutableBitSet empty = ImmutableBitSet.of(); // 创建空位集合
    assertThat(Iterables.size(empty.powerSet()), equalTo(1)); // 断言空集合的幂集大小为1(只有空集)
    assertThat(empty.powerSet(), hasToString("[{}]")); // 断言空集合的幂集为[{}]

    final ImmutableBitSet single = ImmutableBitSet.of(2); // 创建包含单个元素2的位集合
    assertThat(Iterables.size(single.powerSet()), equalTo(2)); // 断言单元素集合的幂集大小为2
    assertThat(single.powerSet(), hasToString("[{}, {2}]")); // 断言幂集为[{}, {2}]

    final ImmutableBitSet two = ImmutableBitSet.of(2, 10); // 创建包含两个元素2和10的位集合
    assertThat(Iterables.size(two.powerSet()), equalTo(4)); // 断言两元素集合的幂集大小为4(2^2)
    assertThat(two.powerSet(), hasToString("[{}, {10}, {2}, {2, 10}]")); // 断言幂集为[{}, {10}, {2}, {2,10}]

    final ImmutableBitSet seventeen = ImmutableBitSet.range(3, 20); // 创建包含17个元素的位集合
    assertThat(Iterables.size(seventeen.powerSet()), equalTo(131072)); // 断言17元素集合的幂集大小为2^17=131072
  }

  @Test void testCreateLongs() { // 定义测试方法testCreateLongs,测试从long数组创建位集合
    assertThat(ImmutableBitSet.valueOf(0L), equalTo(ImmutableBitSet.of())); // 测试从0L创建,应该返回空集合
    assertThat(ImmutableBitSet.valueOf(0xAL), // 测试从0xA(二进制1010)创建
        equalTo(ImmutableBitSet.of(1, 3))); // 应该返回{1,3}(第1位和第3位被设置)
    assertThat(ImmutableBitSet.valueOf(0xAL, 0, 0), // 测试从多个long创建
        equalTo(ImmutableBitSet.of(1, 3))); // 应该返回{1,3}
    assertThat(ImmutableBitSet.valueOf(0, 0, 0xAL, 0), // 测试从多个long创建,0xA在第三个位置
        equalTo(ImmutableBitSet.of(129, 131))); // 应该返回{129,131}(128+1和128+3)
  }

  @Test void testCreateLongBuffer() { // 定义测试方法testCreateLongBuffer,测试从LongBuffer创建位集合
    assertThat(ImmutableBitSet.valueOf(LongBuffer.wrap(new long[] {})), // 测试从空LongBuffer创建
        equalTo(ImmutableBitSet.of())); // 应该返回空集合
    assertThat(ImmutableBitSet.valueOf(LongBuffer.wrap(new long[] {0xAL})), // 测试从包含0xA的LongBuffer创建
        equalTo(ImmutableBitSet.of(1, 3))); // 应该返回{1,3}
    assertThat( // 测试从包含多个long的LongBuffer创建
        ImmutableBitSet.valueOf(LongBuffer.wrap(new long[] {0, 0, 0xAL, 0})), // 0xA在第三个位置
        equalTo(ImmutableBitSet.of(129, 131))); // 应该返回{129,131}
  }

  @Test void testToLongArray() { // 定义测试方法testToLongArray,测试转换为long数组
    final ImmutableBitSet bitSet = ImmutableBitSet.of(29, 4, 1969); // 创建包含29,4,1969的位集合
    assertThat(ImmutableBitSet.valueOf(bitSet.toLongArray()), // 测试转换为long数组后再转换回来
        equalTo(bitSet)); // 应该与原位集合相等
    assertThat(ImmutableBitSet.valueOf(LongBuffer.wrap(bitSet.toLongArray())), // 测试转换为LongBuffer后再转换回来
        equalTo(bitSet)); // 应该与原位集合相等
  }

  @Test void testSet() { // 定义测试方法testSet,测试set()方法
    final ImmutableBitSet bitSet = ImmutableBitSet.of(29, 4, 1969); // 创建包含29,4,1969的位集合
    final ImmutableBitSet bitSet2 = ImmutableBitSet.of(29, 4, 1969, 30); // 创建包含29,4,1969,30的位集合
    assertThat(bitSet.set(30), equalTo(bitSet2)); // 测试设置位30,应该返回bitSet2
    assertThat(bitSet.set(30).set(30), equalTo(bitSet2)); // 测试重复设置位30,应该返回bitSet2
    assertThat(bitSet.set(29), equalTo(bitSet)); // 测试设置已存在的位29,应该返回原位集合
    assertThat(bitSet.setIf(30, false), equalTo(bitSet)); // 测试条件设置位30(条件为false),应该返回原位集合
    assertThat(bitSet.setIf(30, true), equalTo(bitSet2)); // 测试条件设置位30(条件为true),应该返回bitSet2
  }

  @Test void testClear() { // 定义测试方法testClear,测试clear()方法
    final ImmutableBitSet bitSet = ImmutableBitSet.of(29, 4, 1969); // 创建包含29,4,1969的位集合
    final ImmutableBitSet bitSet2 = ImmutableBitSet.of(4, 1969); // 创建包含4,1969的位集合
    assertThat(bitSet.clear(29), equalTo(bitSet2)); // 测试清除位29,应该返回bitSet2
    assertThat(bitSet.clear(29).clear(29), equalTo(bitSet2)); // 测试重复清除位29,应该返回bitSet2
    assertThat(bitSet.clear(29).clear(4).clear(29).clear(1969), // 测试清除所有位
        equalTo(ImmutableBitSet.of())); // 应该返回空集合
    assertThat(bitSet.clearIf(29, false), equalTo(bitSet)); // 测试条件清除位29(条件为false),应该返回原位集合
    assertThat(bitSet.clearIf(29, true), equalTo(bitSet2)); // 测试条件清除位29(条件为true),应该返回bitSet2
  }

  @Test void testSet2() { // 定义测试方法testSet2,测试set(boolean)方法
    final ImmutableBitSet bitSet = ImmutableBitSet.of(29, 4, 1969); // 创建包含29,4,1969的位集合
    final ImmutableBitSet bitSet2 = ImmutableBitSet.of(29, 4, 1969, 30); // 创建包含29,4,1969,30的位集合
    assertThat(bitSet.set(30, false), sameInstance(bitSet)); // 测试设置位30为false,应该返回原实例
    assertThat(bitSet.set(30, true), equalTo(bitSet2)); // 测试设置位30为true,应该返回bitSet2
    assertThat(bitSet.set(29, true), sameInstance(bitSet)); // 测试设置已存在的位29为true,应该返回原实例
  }

  @Test void testShift() { // 定义测试方法testShift,测试shift()方法
    final ImmutableBitSet bitSet = ImmutableBitSet.of(29, 4, 1969); // 创建包含29,4,1969的位集合
    assertThat(bitSet.shift(0), is(bitSet)); // 测试偏移0,应该返回原位集合
    assertThat(bitSet.shift(1), is(ImmutableBitSet.of(30, 5, 1970))); // 测试偏移+1,应该返回{30,5,1970}
    assertThat(bitSet.shift(-4), is(ImmutableBitSet.of(25, 0, 1965))); // 测试偏移-4,应该返回{25,0,1965}
    try { // 测试异常情况
      final ImmutableBitSet x = bitSet.shift(-5); // 尝试偏移-5,会导致负数索引
      fail("Expected error, got " + x); // 应该抛出异常
    } catch (ArrayIndexOutOfBoundsException ignored) { // 捕获ArrayIndexOutOfBoundsException
      // Exact message is not specified by Java // Java未指定确切的错误消息
    }
    final ImmutableBitSet empty = ImmutableBitSet.of(); // 创建空位集合
    assertThat(empty.shift(-100), is(empty)); // 测试空集合的偏移,应该返回空集合
  }

  @Test void testGet2() { // 定义测试方法testGet2,测试get(from, to)方法
    final ImmutableBitSet bitSet = ImmutableBitSet.of(29, 4, 1969); // 创建包含29,4,1969的位集合
    assertThat(bitSet.get(0, 8), is(ImmutableBitSet.of(4))); // 测试获取范围[0,8),应该返回{4}
    assertThat(bitSet.get(0, 5), is(ImmutableBitSet.of(4))); // 测试获取范围[0,5),应该返回{4}
    assertThat(bitSet.get(0, 4), is(ImmutableBitSet.of())); // 测试获取范围[0,4),应该返回空集合
    assertThat(bitSet.get(4, 4), is(ImmutableBitSet.of())); // 测试获取范围[4,4),应该返回空集合
    assertThat(bitSet.get(5, 5), is(ImmutableBitSet.of())); // 测试获取范围[5,5),应该返回空集合
    assertThat(bitSet.get(4, 5), is(ImmutableBitSet.of(4))); // 测试获取范围[4,5),应该返回{4}
    assertThat(bitSet.get(4, 1000), is(ImmutableBitSet.of(4, 29))); // 测试获取范围[4,1000),应该返回{4,29}
    assertThat(bitSet.get(4, 32), is(ImmutableBitSet.of(4, 29))); // 测试获取范围[4,32),应该返回{4,29}
    assertThat(bitSet.get(2000, 10000), is(ImmutableBitSet.of())); // 测试获取范围[2000,10000),应该返回空集合
    assertThat(bitSet.get(1000, 10000), is(ImmutableBitSet.of(1969))); // 测试获取范围[1000,10000),应该返回{1969}
    assertThat(bitSet.get(5, 10000), is(ImmutableBitSet.of(29, 1969))); // 测试获取范围[5,10000),应该返回{29,1969}
    assertThat(bitSet.get(65, 10000), is(ImmutableBitSet.of(1969))); // 测试获取范围[65,10000),应该返回{1969}

    final ImmutableBitSet emptyBitSet = ImmutableBitSet.of(); // 创建空位集合
    assertThat(emptyBitSet.get(0, 4), is(ImmutableBitSet.of())); // 测试空集合的范围获取
    assertThat(emptyBitSet.get(0, 0), is(ImmutableBitSet.of())); // 测试空集合的范围获取
    assertThat(emptyBitSet.get(0, 10000), is(ImmutableBitSet.of())); // 测试空集合的范围获取
    assertThat(emptyBitSet.get(7, 10000), is(ImmutableBitSet.of())); // 测试空集合的范围获取
    assertThat(emptyBitSet.get(73, 10000), is(ImmutableBitSet.of())); // 测试空集合的范围获取
  }

  /**
   * Test case for {@link ImmutableBitSet#allContain(Collection, int)}.
   * // 测试allContain()方法,判断集合中的所有位集合是否都包含指定的整数
   */
  @Test void testAllContain() { // 定义测试方法testAllContain
    ImmutableBitSet set1 = ImmutableBitSet.of(0, 1, 2, 3); // 创建包含0,1,2,3的位集合
    ImmutableBitSet set2 = ImmutableBitSet.of(2, 3, 4, 5); // 创建包含2,3,4,5的位集合
    ImmutableBitSet set3 = ImmutableBitSet.of(3, 4, 5, 6); // 创建包含3,4,5,6的位集合

    Collection<ImmutableBitSet> collection1 = ImmutableList.of(set1, set2, set3); // 创建包含三个位集合的集合
    assertTrue(ImmutableBitSet.allContain(collection1, 3)); // 测试所有位集合都包含3,应该返回true
    assertFalse(ImmutableBitSet.allContain(collection1, 0)); // 测试所有位集合都包含0,应该返回false(set2和set3不包含)

    Collection<ImmutableBitSet> collection2 = ImmutableList.of(set1, set2); // 创建包含两个位集合的集合
    assertTrue(ImmutableBitSet.allContain(collection2, 2)); // 测试所有位集合都包含2,应该返回true
    assertTrue(ImmutableBitSet.allContain(collection2, 3)); // 测试所有位集合都包含3,应该返回true
    assertFalse(ImmutableBitSet.allContain(collection2, 4)); // 测试所有位集合都包含4,应该返回false(set1不包含)
  }

  /**
   * Test case for {@link ImmutableBitSet#anyMatch(IntPredicate)}
   * and {@link ImmutableBitSet#allMatch(IntPredicate)}.
   * // 测试anyMatch()和allMatch()方法
   *
   * <p>Checks a variety of predicates (is even, is zero, always true,
   * always false) and their negations on a variety of bit sets.
   * // 检查各种谓词(是否偶数、是否为零、总是真、总是假)及其否定在各种位集合上的表现
   */
  @Test void testAnyMatch() { // 定义测试方法testAnyMatch
    BiConsumer<ImmutableBitSet, IntPredicate> c = (bitSet, predicate) -> { // 定义测试函数
      final Set<Integer> integerSet = new HashSet<>(bitSet.asList()); // 将位集合转换为Set
      assertThat(bitSet.anyMatch(predicate), // 测试anyMatch方法
          is(integerSet.stream().anyMatch(predicate::test))); // 断言与Stream的anyMatch结果一致
      assertThat(bitSet.allMatch(predicate), // 测试allMatch方法
          is(integerSet.stream().allMatch(predicate::test))); // 断言与Stream的allMatch结果一致
    };

    BiConsumer<ImmutableBitSet, IntPredicate> c2 = (bitSet, predicate) -> { // 定义测试函数,同时测试谓词及其否定
      c.accept(bitSet, predicate); // 测试谓词
      c.accept(bitSet, predicate.negate()); // 测试谓词的否定
    };

    final ImmutableBitSet set0 = ImmutableBitSet.of(); // 创建空位集合
    final ImmutableBitSet set1 = ImmutableBitSet.of(0, 1, 2, 3); // 创建包含0,1,2,3的位集合
    final ImmutableBitSet set2 = ImmutableBitSet.of(0, 2, 4, 8); // 创建包含0,2,4,8的位集合
    Consumer<IntPredicate> c3 = predicate -> { // 定义测试函数,在所有位集合上测试谓词
      c2.accept(set0, predicate); // 在空位集合上测试
      c2.accept(set1, predicate); // 在set1上测试
      c2.accept(set2, predicate); // 在set2上测试
    };

    final IntPredicate isZero = i -> i == 0; // 定义谓词:是否为零
    final IntPredicate isEven = i -> i % 2 == 0; // 定义谓词:是否为偶数
    final IntPredicate alwaysTrue = i -> true; // 定义谓词:总是真
    final IntPredicate alwaysFalse = i -> false; // 定义谓词:总是假
    c3.accept(isZero); // 测试isZero谓词
    c3.accept(isEven); // 测试isEven谓词
    c3.accept(alwaysTrue); // 测试alwaysTrue谓词
    c3.accept(alwaysFalse); // 测试alwaysFalse谓词
  }

  /** Test case for
   * {@link org.apache.calcite.util.ImmutableBitSet#toImmutableBitSet()}. */ // 测试Stream收集器
  @Test void testCollector() { // 定义测试方法testCollector
    checkCollector(0, 20); // 测试收集器,输入0和20
    checkCollector(); // 测试收集器,输入空数组
    checkCollector(1, 63); // 测试收集器,输入1和63
    checkCollector(1, 63, 1); // 测试收集器,输入1,63,1(包含重复)
    checkCollector(0, 257); // 测试收集器,输入0和257
    checkCollector(1024, 257); // 测试收集器,输入1024和257
  }

  private void checkCollector(int... integers) { // 定义辅助方法checkCollector,测试收集器功能
    final List<Integer> list = Ints.asList(integers); // 将int数组转换为List
    final List<Integer> sortedUniqueList = new ArrayList<>(new TreeSet<>(list)); // 创建排序且唯一的列表
    final ImmutableBitSet bitSet = // 使用Stream收集器创建位集合
        list.stream().collect(ImmutableBitSet.toImmutableBitSet()); // 收集为ImmutableBitSet
    assertThat(bitSet.asList(), is(sortedUniqueList)); // 断言结果与排序且唯一的列表一致
  }
} // 类定义结束
