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
package org.apache.calcite.util; // 声明包名，这个类属于 org.apache.calcite.util 包，是 Calcite 框架中的工具类包

import org.apache.calcite.runtime.ImmutablePairList; // 导入不可变的 PairList 实现类，用于创建不可修改的键值对列表
import org.apache.calcite.runtime.MapEntry; // 导入 MapEntry 类，用于表示 Map 的条目
import org.apache.calcite.runtime.PairList; // 导入 PairList 类，这是被测试的核心类，用于存储键值对列表

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的不可变列表类
import com.google.common.collect.ImmutableMap; // 导入 Google Guava 的不可变 Map 类
import com.google.common.collect.Lists; // 导入 Google Guava 的 Lists 工具类，提供列表操作方法

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.util.ArrayList; // 导入 Java 标准库的 ArrayList 动态数组类
import java.util.Arrays; // 导入 Java 标准库的 Arrays 数组工具类
import java.util.Collections; // 导入 Java 标准库的 Collections 集合工具类
import java.util.List; // 导入 Java 标准库的 List 接口
import java.util.Map; // 导入 Java 标准库的 Map 接口
import java.util.RandomAccess; // 导入 Java 标准库的 RandomAccess 标记接口，用于标记支持快速随机访问的列表
import java.util.function.BiPredicate; // 导入 Java 标准库的 BiPredicate 函数式接口，用于表示接受两个参数并返回布尔值的谓词

import static org.apache.calcite.test.Matchers.isListOf; // 导入静态方法，用于匹配列表内容

import static org.hamcrest.CoreMatchers.instanceOf; // 导入静态方法，用于验证对象是否是某个类的实例
import static org.hamcrest.CoreMatchers.is; // 导入静态方法，用于验证两个值是否相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入静态方法，用于断言验证
import static org.hamcrest.Matchers.empty; // 导入静态方法，用于验证集合是否为空
import static org.hamcrest.Matchers.hasSize; // 导入静态方法，用于验证集合的大小
import static org.hamcrest.Matchers.hasToString; // 导入静态方法，用于验证对象的 toString() 输出
import static org.hamcrest.Matchers.startsWith; // 导入静态方法，用于验证字符串是否以指定前缀开头
import static org.junit.jupiter.api.Assertions.assertThrows; // 导入静态方法，用于验证是否抛出指定异常
import static org.junit.jupiter.api.Assertions.fail; // 导入静态方法，用于标记测试失败

/** Unit test for {@code PairList}. */ // 类注释：这是 PairList 类的单元测试类，用于测试 PairList 的所有功能
class PairListTest { // 定义 PairListTest 测试类，包含所有 PairList 相关的测试方法
  /** Equivalent to {@link Pair#left} but without calling // 方法注释：这个方法等同于 Pair.left()，但不调用 PairList.leftList()，直接从 Map.Entry 列表中提取所有的键
   * {@link PairList#leftList()}. */ // 继续注释说明这个方法的作用
  private static <T, U> List<T> left( // 定义泛型静态方法 left，接受两个类型参数 T（键类型）和 U（值类型），返回键的列表
      final List<? extends Map.Entry<? extends T, ? extends U>> pairs) { // 参数 pairs 是一个 Map.Entry 列表，每个 Entry 包含一个键值对
    return Util.transform(pairs, Map.Entry::getKey); // 使用 Util.transform 方法将 pairs 列表中的每个 Map.Entry 转换为键（调用 getKey() 方法），返回新的列表
  } // 方法结束

  /** Equivalent to {@link Pair#right} but without calling // 方法注释：这个方法等同于 Pair.right()，但不调用 PairList.rightList()，直接从 Map.Entry 列表中提取所有的值
   * {@link PairList#rightList()}. */ // 继续注释说明这个方法的作用
  private static <T, U> List<U> right( // 定义泛型静态方法 right，接受两个类型参数 T（键类型）和 U（值类型），返回值的列表
      final List<? extends Map.Entry<? extends T, ? extends U>> pairs) { // 参数 pairs 是一个 Map.Entry 列表，每个 Entry 包含一个键值对
    return Util.transform(pairs, Map.Entry::getValue); // 使用 Util.transform 方法将 pairs 列表中的每个 Map.Entry 转换为值（调用 getValue() 方法），返回新的列表
  } // 方法结束

  /** Compares a {@link PairList} with a {@link List} that should have // 方法注释：这个方法用于验证 PairList 与一个应该具有相同内容的 List 是否等价
   * equivalent contents. */ // 继续注释说明这个方法的作用
  private <T, U> void validate(PairList<T, U> pairList, // 定义泛型方法 validate，接受两个类型参数 T（键类型）和 U（值类型），用于验证 PairList 的正确性
      List<? extends Map.Entry<T, U>> list) { // 参数 list 是一个期望的 Map.Entry 列表，用于与 pairList 进行比较
    assertThat(pairList.isEmpty(), is(list.isEmpty())); // 验证 pairList 的 isEmpty() 方法返回值是否与 list 的 isEmpty() 相同
    assertThat(pairList, hasSize(list.size())); // 验证 pairList 的大小是否与 list 的大小相同
    assertThat(pairList.leftList(), hasSize(list.size())); // 验证 pairList.leftList() 返回的左列表大小是否与 list 的大小相同
    assertThat(pairList.rightList(), hasSize(list.size())); // 验证 pairList.rightList() 返回的右列表大小是否与 list 的大小相同
    assertThat(pairList.leftList(), is(left(list))); // 验证 pairList.leftList() 的内容是否与通过 left() 方法从 list 提取的键列表相同
    assertThat(pairList.leftList(), instanceOf(RandomAccess.class)); // 验证 pairList.leftList() 是否实现了 RandomAccess 接口，确保支持快速随机访问
    assertThat(pairList.rightList(), is(right(list))); // 验证 pairList.rightList() 的内容是否与通过 right() 方法从 list 提取的值列表相同
    assertThat(pairList.rightList(), instanceOf(RandomAccess.class)); // 验证 pairList.rightList() 是否实现了 RandomAccess 接口，确保支持快速随机访问

    // Check PairList.left(int) and PairList.right(int) // 注释说明：检查 PairList 的 left(int) 和 right(int) 方法
    for (int i = 0; i < list.size(); i++) { // 遍历 list 的所有索引
      Map.Entry<T, U> entry = list.get(i); // 获取 list 中索引 i 处的 Map.Entry 对象
      assertThat(pairList.left(i), is(entry.getKey())); // 验证 pairList.left(i) 返回的键是否与 entry.getKey() 相同
      assertThat(pairList.right(i), is(entry.getValue())); // 验证 pairList.right(i) 返回的值是否与 entry.getValue() 相同
    } // 循环结束

    final List<Map.Entry<T, U>> list2 = new ArrayList<>(pairList); // 创建一个新的 ArrayList，使用 pairList 的内容初始化
    assertThat(list2, is(list)); // 验证 list2 的内容是否与 list 相同

    // Check PairList.forEach(Consumer) // 注释说明：检查 PairList.forEach(Consumer) 方法
    list2.clear(); // 清空 list2
    //noinspection UseBulkOperation // 抑制警告：这里有意使用 forEach 而不是批量操作
    pairList.forEach(p -> list2.add(p)); // 使用 forEach 方法遍历 pairList，将每个元素添加到 list2 中
    assertThat(list2, is(list)); // 验证 list2 的内容是否与 list 相同

    // Check PairList.forEach(BiConsumer) // 注释说明：检查 PairList.forEach(BiConsumer) 方法
    list2.clear(); // 清空 list2
    pairList.forEach((t, u) -> list2.add(Pair.of(t, u))); // 使用 forEach 方法遍历 pairList，将每个键值对转换为 Pair 对象并添加到 list2 中
    assertThat(list2, is(list)); // 验证 list2 的内容是否与 list 相同

    // Check PairList.forEachIndexed // 注释说明：检查 PairList.forEachIndexed 方法
    list2.clear(); // 清空 list2
    pairList.forEachIndexed((i, t, u) -> { // 使用 forEachIndexed 方法遍历 pairList，提供索引、键和值
      assertThat(i, is(list2.size())); // 验证索引 i 是否等于 list2 的当前大小（确保索引正确）
      list2.add(Pair.of(t, u)); // 将键值对转换为 Pair 对象并添加到 list2 中
    }); // forEachIndexed 方法结束
    assertThat(list2, is(list)); // 验证 list2 的内容是否与 list 相同

    // Check PairList.immutable() // 注释说明：检查 PairList.immutable() 方法
    // Skip if there are no null keys or values // 注释说明：如果没有 null 键或值，则跳过某些检查
    if (list.stream().anyMatch(e -> e.getKey() == null)) { // 检查 list 中是否有任何键为 null
      // PairList.immutable should throw if there are null keys // 注释说明：如果有 null 键，PairList.immutable() 应该抛出异常
      try { // 开始 try 块，用于捕获异常
        Object o = pairList.immutable(); // 尝试调用 immutable() 方法创建不可变 PairList
        fail("expected error, got " + o); // 如果没有抛出异常，测试失败
      } catch (NullPointerException e) { // 捕获 NullPointerException
        assertThat(e.getMessage(), startsWith("key at index")); // 验证异常消息是否以 "key at index" 开头
      } // catch 块结束
    } else if (list.stream().anyMatch(e -> e.getValue() == null)) { // 检查 list 中是否有任何值为 null
      // PairList.immutable should throw if there are null values // 注释说明：如果有 null 值，PairList.immutable() 应该抛出异常
      try { // 开始 try 块，用于捕获异常
        Object o = pairList.immutable(); // 尝试调用 immutable() 方法创建不可变 PairList
        fail("expected error, got " + o); // 如果没有抛出异常，测试失败
      } catch (NullPointerException e) { // 捕获 NullPointerException
        assertThat(e.getMessage(), startsWith("value at index")); // 验证异常消息是否以 "value at index" 开头
      } // catch 块结束
    } else { // 如果没有 null 键和 null 值
      final PairList<T, U> immutablePairList = pairList.immutable(); // 调用 immutable() 方法创建不可变 PairList
      assertThat(immutablePairList, hasSize(list.size())); // 验证不可变 PairList 的大小是否与 list 相同
      assertThat(immutablePairList, is(list)); // 验证不可变 PairList 的内容是否与 list 相同

      assertThat(pairList.reversed(), is(Lists.reverse(list))); // 验证 pairList.reversed() 返回的反转列表是否与 Lists.reverse(list) 相同
      assertThat(immutablePairList.reversed(), is(Lists.reverse(list))); // 验证不可变 PairList 的反转列表是否与 Lists.reverse(list) 相同

      list2.clear(); // 清空 list2
      immutablePairList.forEach((k, v) -> list2.add(Pair.of(k, v))); // 使用 forEach 方法遍历不可变 PairList，将每个键值对转换为 Pair 对象并添加到 list2 中
      assertThat(list2, is(list)); // 验证 list2 的内容是否与 list 相同
    } // else 块结束
  } // validate 方法结束

  /** Basic test for {@link PairList}. */ // 方法注释：这是 PairList 的基本测试方法，测试 PairList 的核心功能
  @Test void testPairList() { // 测试方法：使用 @Test 注解标记，测试 PairList 的基本操作
    final PairList<Integer, String> pairList = PairList.of(); // 创建一个空的 PairList，键类型为 Integer，值类型为 String
    final List<Map.Entry<Integer, String>> list = new ArrayList<>(); // 创建一个空的 ArrayList 用于比较

    validate(pairList, list); // 验证空的 PairList 是否与空的 list 相同

    // add(T, U) // 注释说明：测试 add(T, U) 方法，添加一个键值对
    pairList.add(1, "a"); // 向 pairList 添加键值对 (1, "a")
    list.add(Pair.of(1, "a")); // 向 list 添加对应的 Pair 对象
    validate(pairList, list); // 验证添加后的 pairList 是否与 list 相同

    // add(Pair<T, U>) // 注释说明：测试 add(Pair<T, U>) 方法，添加一个 Pair 对象
    pairList.add(Pair.of(2, "b")); // 向 pairList 添加 Pair 对象 (2, "b")
    list.add(Pair.of(2, "b")); // 向 list 添加对应的 Pair 对象
    validate(pairList, list); // 验证添加后的 pairList 是否与 list 相同

    // add(T, U) // 注释说明：再次测试 add(T, U) 方法，添加一个键值对
    pairList.add(2, "bb"); // 向 pairList 添加键值对 (2, "bb")，注意键可以重复
    list.add(Pair.of(2, "bb")); // 向 list 添加对应的 Pair 对象
    validate(pairList, list); // 验证添加后的 pairList 是否与 list 相同

    // add(int, Pair<T, U>) // 注释说明：测试 add(int, Pair<T, U>) 方法，在指定位置插入一个 Pair 对象
    pairList.add(0, Pair.of(3, "c")); // 在索引 0 处插入 Pair 对象 (3, "c")
    list.add(0, Pair.of(3, "c")); // 在 list 的索引 0 处插入对应的 Pair 对象
    validate(pairList, list); // 验证插入后的 pairList 是否与 list 相同

    // add(int, T, U) // 注释说明：测试 add(int, T, U) 方法，在指定位置插入一个键值对
    pairList.add(0, 4, "d"); // 在索引 0 处插入键值对 (4, "d")
    list.add(0, Pair.of(4, "d")); // 在 list 的索引 0 处插入对应的 Pair 对象
    validate(pairList, list); // 验证插入后的 pairList 是否与 list 相同

    // remove(int) // 注释说明：测试 remove(int) 方法，移除指定位置的元素
    Map.Entry<Integer, String> x = pairList.remove(1); // 从 pairList 中移除索引 1 处的元素
    Map.Entry<Integer, String> y = list.remove(1); // 从 list 中移除索引 1 处的元素
    assertThat(x, is(y)); // 验证移除的元素是否相同
    validate(pairList, list); // 验证移除后的 pairList 是否与 list 相同

    // clear() // 注释说明：测试 clear() 方法，清空列表
    pairList.clear(); // 清空 pairList
    list.clear(); // 清空 list
    validate(pairList, list); // 验证清空后的 pairList 是否与 list 相同

    // clear() again // 注释说明：再次测试 clear() 方法，验证多次清空的安全性
    pairList.clear(); // 再次清空 pairList
    list.clear(); // 再次清空 list
    validate(pairList, list); // 验证清空后的 pairList 是否与 list 相同

    // add(T, U) having called clear // 注释说明：测试在调用 clear() 后添加元素
    pairList.add(-1, "c"); // 向已清空的 pairList 添加键值对 (-1, "c")
    list.add(Pair.of(-1, "c")); // 向已清空的 list 添加对应的 Pair 对象
    validate(pairList, list); // 验证添加后的 pairList 是否与 list 相同

    // addAll(PairList) // 注释说明：测试 addAll(PairList) 方法，批量添加另一个 PairList 的所有元素
    final PairList<Integer, String> pairList8 = PairList.copyOf(8, "x", 7, "y"); // 创建一个包含两个键值对的 PairList (8, "x") 和 (7, "y")
    pairList.addAll(pairList8); // 将 pairList8 的所有元素添加到 pairList 中
    list.addAll(pairList8); // 将 pairList8 的所有元素添加到 list 中
    validate(pairList, list); // 验证添加后的 pairList 是否与 list 相同

    // addAll(int, PairList) // 注释说明：测试 addAll(int, PairList) 方法，在指定位置批量添加另一个 PairList 的所有元素
    pairList.addAll(3, pairList8); // 在索引 3 处插入 pairList8 的所有元素
    list.addAll(3, pairList8); // 在 list 的索引 3 处插入 pairList8 的所有元素
    validate(pairList, list); // 验证插入后的 pairList 是否与 list 相同

    PairList<Integer, String> immutablePairList = pairList.immutable(); // 调用 immutable() 方法创建不可变 PairList
    assertThrows(UnsupportedOperationException.class, () -> // 验证不可变 PairList 是否抛出 UnsupportedOperationException
        immutablePairList.add(0, "")); // 尝试向不可变 PairList 添加元素，应该抛出异常
    validate(immutablePairList, list); // 验证不可变 PairList 是否与 list 相同

    // set(int, Pair<T, U>) // 注释说明：测试 set(int, Pair<T, U>) 方法，替换指定位置的元素为 Pair 对象
    pairList.set(2, 0, "p"); // 在索引 2 处设置键值对 (0, "p")
    list.set(2, Pair.of(0, "p")); // 在 list 的索引 2 处设置对应的 Pair 对象
    validate(pairList, list); // 验证设置后的 pairList 是否与 list 相同

    // set(int, T, U) // 注释说明：测试 set(int, T, U) 方法，替换指定位置的元素为键值对
    pairList.set(1, Pair.of(88, "q")); // 在索引 1 处设置 Pair 对象 (88, "q")
    list.set(1, Pair.of(88, "q")); // 在 list 的索引 1 处设置对应的 Pair 对象
    validate(pairList, list); // 验证设置后的 pairList 是否与 list 相同
  } // testPairList 方法结束

  @Test void testAddAll() { // 测试方法：使用 @Test 注解标记，测试 addAll 方法的各种场景
    PairList<String, Integer> pairList = PairList.of(); // 创建一个空的 PairList，键类型为 String，值类型为 Integer

    // MutablePairList (0 entries) // 注释说明：测试添加空的 MutablePairList
    pairList.addAll(PairList.of()); // 添加空的 PairList
    assertThat(pairList, hasSize(0)); // 验证 pairList 的大小是否为 0

    // MutablePairList (1 entry) // 注释说明：测试添加包含 1 个元素的 MutablePairList
    pairList.addAll(PairList.of("a", 1)); // 添加包含 1 个元素的 PairList ("a", 1)
    assertThat(pairList, hasSize(1)); // 验证 pairList 的大小是否为 1

    // MutablePairList (2 entries) // 注释说明：测试添加包含 2 个元素的 MutablePairList
    pairList.addAll(PairList.of(ImmutableMap.of("b", 2, "c", 3))); // 从 ImmutableMap 创建 PairList 并添加
    assertThat(pairList, hasSize(3)); // 验证 pairList 的大小是否为 3

    // EmptyImmutablePairList // 注释说明：测试添加空的 ImmutablePairList
    pairList.addAll(ImmutablePairList.of()); // 添加空的 ImmutablePairList
    assertThat(pairList, hasSize(3)); // 验证 pairList 的大小是否仍为 3

    // ImmutableList (0 entries) // 注释说明：测试添加空的 ImmutableList
    pairList.addAll(ImmutableList.of()); // 添加空的 ImmutableList
    assertThat(pairList, hasSize(3)); // 验证 pairList 的大小是否仍为 3

    // SingletonImmutablePairList // 注释说明：测试添加包含 1 个元素的 ImmutablePairList
    pairList.addAll(ImmutablePairList.of("d", 4)); // 添加包含 1 个元素的 ImmutablePairList ("d", 4)
    assertThat(pairList, hasSize(4)); // 验证 pairList 的大小是否为 4

    // ImmutableList (1 entry) // 注释说明：测试添加包含 1 个元素的 ImmutableList
    pairList.addAll(ImmutableList.of(new MapEntry<>("e", 5))); // 添加包含 1 个元素的 ImmutableList，元素是 MapEntry 对象
    assertThat(pairList, hasSize(5)); // 验证 pairList 的大小是否为 5

    // MutablePairList (2 entries) // 注释说明：测试添加包含 2 个元素的 MutablePairList
    pairList.addAll(PairList.copyOf("f", 6, "g", 7)); // 添加包含 2 个元素的 PairList ("f", 6) 和 ("g", 7)
    assertThat(pairList, hasSize(7)); // 验证 pairList 的大小是否为 7

    // ArrayImmutablePairList (2 entries, created from MutablePairList) // 注释说明：测试添加从 MutablePairList 创建的 ArrayImmutablePairList
    pairList.addAll(PairList.copyOf("h", 8, "i", 9).immutable()); // 创建 MutablePairList 后转为不可变再添加
    assertThat(pairList, hasSize(9)); // 验证 pairList 的大小是否为 9

    // ArrayImmutablePairList (3 entries, created using copyOf) // 注释说明：测试添加使用 copyOf 创建的 ArrayImmutablePairList
    pairList.addAll(ImmutablePairList.copyOf("j", 10, "k", 11, "l", 12)); // 添加包含 3 个元素的 ImmutablePairList
    assertThat(pairList, hasSize(12)); // 验证 pairList 的大小是否为 12

    // ArrayImmutablePairList (2 entries, created using copyOf) // 注释说明：测试添加使用 copyOf 创建的 ArrayImmutablePairList
    pairList.addAll(ImmutablePairList.copyOf("m", 13, "n", 14)); // 添加包含 2 个元素的 ImmutablePairList
    assertThat(pairList, hasSize(14)); // 验证 pairList 的大小是否为 14

    // ArrayImmutablePairList (1 entry, created using copyOf) // 注释说明：测试添加使用 copyOf 创建的 ArrayImmutablePairList
    pairList.addAll(ImmutablePairList.copyOf("o", 15)); // 添加包含 1 个元素的 ImmutablePairList
    assertThat(pairList, hasSize(15)); // 验证 pairList 的大小是否为 15

    assertThat(pairList, // 验证 pairList 的 toString() 输出
        hasToString("[<a, 1>, <b, 2>, <c, 3>, <d, 4>, <e, 5>, <f, 6>, " // 期望的字符串表示（第一部分）
            + "<g, 7>, <h, 8>, <i, 9>, <j, 10>, <k, 11>, <l, 12>, " // 期望的字符串表示（第二部分）
            + "<m, 13>, <n, 14>, <o, 15>]")); // 期望的字符串表示（第三部分）
  } // testAddAll 方法结束

  /** Tests {@link PairList#of(Map)} and {@link PairList#toImmutableMap()}. */ // 方法注释：测试 PairList.of(Map) 和 PairList.toImmutableMap() 方法
  @Test void testPairListOfMap() { // 测试方法：使用 @Test 注解标记，测试从 Map 创建 PairList 和从 PairList 转换为 ImmutableMap
    final ImmutableMap<String, Integer> map = ImmutableMap.of("a", 1, "b", 2); // 创建一个包含两个键值对的 ImmutableMap
    final PairList<String, Integer> pairList = PairList.of(map); // 从 ImmutableMap 创建 PairList
    assertThat(pairList, hasSize(2)); // 验证 pairList 的大小是否为 2
    assertThat(pairList, hasToString("[<a, 1>, <b, 2>]")); // 验证 pairList 的 toString() 输出

    final List<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet()); // 从 map 的 entrySet 创建 ArrayList
    validate(pairList, list); // 验证 pairList 是否与 list 相同

    final ImmutableMap<String, Integer> map2 = pairList.toImmutableMap(); // 调用 toImmutableMap() 方法将 PairList 转换为 ImmutableMap
    assertThat(map2, is(map)); // 验证转换后的 map2 是否与原始 map 相同

    // After calling toImmutableMap, you can modify the list and call // 注释说明：调用 toImmutableMap 后，可以修改列表并再次调用 toImmutableMap
    // toImmutableMap again. // 继续注释说明
    pairList.add("c", 3); // 向 pairList 添加新的键值对 ("c", 3)
    list.add(Pair.of("c", 3)); // 向 list 添加对应的 Pair 对象
    validate(pairList, list); // 验证添加后的 pairList 是否与 list 相同
    assertThat(pairList, hasToString("[<a, 1>, <b, 2>, <c, 3>]")); // 验证 pairList 的 toString() 输出
    final ImmutableMap<String, Integer> map3 = pairList.toImmutableMap(); // 再次调用 toImmutableMap() 方法
    assertThat(map3, hasToString("{a=1, b=2, c=3}")); // 验证转换后的 map3 的 toString() 输出

    final Map<String, Integer> emptyMap = ImmutableMap.of(); // 创建一个空的 ImmutableMap
    final PairList<String, Integer> emptyPairList = PairList.of(emptyMap); // 从空的 ImmutableMap 创建 PairList
    assertThat(emptyPairList.isEmpty(), is(true)); // 验证 emptyPairList 是否为空
    validate(emptyPairList, Collections.emptyList()); // 验证 emptyPairList 是否与空的 list 相同
  } // testPairListOfMap 方法结束

  /** Tests {@link PairList#withCapacity(int)}. */ // 方法注释：测试 PairList.withCapacity(int) 方法，用于创建具有指定初始容量的 PairList
  @Test void testPairListWithCapacity() { // 测试方法：使用 @Test 注解标记，测试 withCapacity 方法
    final PairList<String, Integer> list = PairList.withCapacity(100); // 创建一个初始容量为 100 的 PairList
    assertThat(list, hasSize(0)); // 验证 list 的大小是否为 0
    assertThat(list, empty()); // 验证 list 是否为空
    assertThat(list, hasToString("[]")); // 验证 list 的 toString() 输出

    list.add("a", 1); // 向 list 添加键值对 ("a", 1)
    list.add("b", 2); // 向 list 添加键值对 ("b", 2)
    assertThat(list, hasSize(2)); // 验证 list 的大小是否为 2
    assertThat(list, hasToString("[<a, 1>, <b, 2>]")); // 验证 list 的 toString() 输出

    final Map.Entry<String, Integer> entry = list.remove(0); // 从 list 中移除索引 0 处的元素
    assertThat(entry.getKey(), is("a")); // 验证移除的元素的键是否为 "a"
    assertThat(entry.getValue(), is(1)); // 验证移除的元素的值是否为 1
    assertThat(list, hasToString("[<b, 2>]")); // 验证 list 的 toString() 输出
  } // testPairListWithCapacity 方法结束

  @Test void testPairListOf() { // 测试方法：使用 @Test 注解标记，测试 PairList.of() 方法的各种重载
    final PairList<String, Integer> list0 = PairList.of(); // 创建一个空的 PairList
    assertThat(list0, hasSize(0)); // 验证 list0 的大小是否为 0
    assertThat(list0, empty()); // 验证 list0 是否为空
    assertThat(list0, hasToString("[]")); // 验证 list0 的 toString() 输出

    final PairList<String, Integer> list1 = PairList.of("a", 1); // 创建一个包含 1 个键值对的 PairList ("a", 1)
    assertThat(list1, hasSize(1)); // 验证 list1 的大小是否为 1
    assertThat(list1, hasToString("[<a, 1>]")); // 验证 list1 的 toString() 输出

    final PairList<String, Integer> list3 = // 创建一个包含 3 个键值对的 PairList
        PairList.copyOf("a", 1, "b", null, "c", 3); // 使用 copyOf 方法创建，包含 null 值
    assertThat(list3, hasSize(3)); // 验证 list3 的大小是否为 3
    assertThat(list3, hasToString("[<a, 1>, <b, null>, <c, 3>]")); // 验证 list3 的 toString() 输出

    assertThrows(IllegalArgumentException.class, // 验证是否抛出 IllegalArgumentException
        () -> PairList.copyOf("a", 1, "b", 2, "c"), // 尝试使用奇数个参数创建 PairList，应该抛出异常
        "odd number of arguments"); // 期望的异常消息
  } // testPairListOf 方法结束

  @Test void testTransform() { // 测试方法：使用 @Test 注解标记，测试 transform 和相关方法
    final PairList<String, Integer> list3 = // 创建一个包含 3 个键值对的 PairList
        PairList.copyOf("a", 1, null, 5, "c", 3); // 使用 copyOf 方法创建，包含 null 键
    assertThat(list3.transform((s, i) -> s + i), // 测试 transform 方法，将键值对转换为字符串拼接
        isListOf("a1", "null5", "c3")); // 验证转换后的列表内容
    assertThat(list3.transform2((s, i) -> s + i), // 测试 transform2 方法，将键值对转换为字符串拼接
        isListOf("a1", "null5", "c3")); // 验证转换后的列表内容

    final PairList<String, Integer> list0 = PairList.of(); // 创建一个空的 PairList
    assertThat(list0.transform((s, i) -> s + i), empty()); // 验证空列表的 transform 结果是否为空

    final BiPredicate<String, Integer> gt2 = (s, i) -> i > 2; // 创建一个 BiPredicate，检查值是否大于 2
    assertThat(list3.anyMatch(gt2), is(true)); // 验证是否有任何元素满足条件（值大于 2）
    assertThat(list3.allMatch(gt2), is(false)); // 验证是否所有元素都满足条件（值大于 2）
    assertThat(list3.noMatch(gt2), is(false)); // 验证是否没有元素满足条件（值大于 2）

    final BiPredicate<String, Integer> negative = (s, i) -> i < 0; // 创建一个 BiPredicate，检查值是否小于 0
    assertThat(list3.anyMatch(negative), is(false)); // 验证是否有任何元素满足条件（值小于 0）
    assertThat(list3.allMatch(negative), is(false)); // 验证是否所有元素都满足条件（值小于 0）
    assertThat(list3.noMatch(negative), is(true)); // 验证是否没有元素满足条件（值小于 0）

    final BiPredicate<String, Integer> positive = (s, i) -> i > 0; // 创建一个 BiPredicate，检查值是否大于 0
    assertThat(list3.anyMatch(positive), is(true)); // 验证是否有任何元素满足条件（值大于 0）
    assertThat(list3.allMatch(positive), is(true)); // 验证是否所有元素都满足条件（值大于 0）
    assertThat(list3.noMatch(positive), is(false)); // 验证是否没有元素满足条件（值大于 0）

    final BiPredicate<String, Integer> isNull = (s, i) -> s == null; // 创建一个 BiPredicate，检查键是否为 null
    assertThat(list3.anyMatch(isNull), is(true)); // 验证是否有任何元素满足条件（键为 null）
    assertThat(list3.allMatch(isNull), is(false)); // 验证是否所有元素都满足条件（键为 null）
    assertThat(list3.noMatch(isNull), is(false)); // 验证是否没有元素满足条件（键为 null）

    // All predicates behave the same on the empty list // 注释说明：所有谓词在空列表上的行为都相同
    Arrays.asList(gt2, negative, positive, isNull).forEach(p -> { // 遍历所有谓词
      assertThat(list0.anyMatch(p), is(false)); // 验证空列表的 anyMatch 结果是否为 false
      assertThat(list0.allMatch(p), is(true)); // 验证空列表的 allMatch 结果是否为 true（平凡真）
      assertThat(list0.noMatch(p), is(true)); // 验证空列表的 noMatch 结果是否为 true
    }); // forEach 方法结束
  } // testTransform 方法结束

  @Test void testBuilder() { // 测试方法：使用 @Test 注解标记，测试 PairList.Builder 构建器模式
    final PairList.Builder<String, Integer> b = PairList.builder(); // 创建一个 PairList.Builder 对象
    final List<Pair<String, Integer>> list = new ArrayList<>(); // 创建一个 ArrayList 用于比较

    final PairList<String, Integer> list0 = b.build(); // 调用 build() 方法构建空的 PairList
    validate(list0, list); // 验证构建的 PairList 是否与 list 相同

    final ImmutablePairList<String, Integer> list0i = b.buildImmutable(); // 调用 buildImmutable() 方法构建空的不可变 PairList
    validate(list0i, list); // 验证构建的不可变 PairList 是否与 list 相同

    b.add("a", 1); // 向构建器添加键值对 ("a", 1)
    list.add(Pair.of("a", 1)); // 向 list 添加对应的 Pair 对象
    final PairList<String, Integer> list1 = b.build(); // 调用 build() 方法构建 PairList
    validate(list1, list); // 验证构建的 PairList 是否与 list 相同

    b.add("b", 2); // 向构建器添加键值对 ("b", 2)
    b.add("c", null); // 向构建器添加键值对 ("c", null)，包含 null 值
    list.add(Pair.of("b", 2)); // 向 list 添加对应的 Pair 对象
    list.add(Pair.of("c", null)); // 向 list 添加对应的 Pair 对象
    final PairList<String, Integer> list3 = b.build(); // 调用 build() 方法构建 PairList
    validate(list3, list); // 验证构建的 PairList 是否与 list 相同

    // Reverse PairList in place // 注释说明：原地反转 PairList
    list3.reverse(); // 调用 reverse() 方法反转 list3
    validate(list3, Lists.reverse(list)); // 验证反转后的 list3 是否与 Lists.reverse(list) 相同

    // Singleton list with null key // 注释说明：测试包含 null 键的单元素列表
    final PairList.Builder<String, Integer> b2 = PairList.builder(); // 创建一个新的 PairList.Builder 对象
    list.clear(); // 清空 list
    b2.add(null, 5); // 向构建器添加键值对 (null, 5)，包含 null 键
    list.add(Pair.of(null, 5)); // 向 list 添加对应的 Pair 对象
    validate(b2.build(), list); // 验证构建的 PairList 是否与 list 相同

    // Singleton list with null value // 注释说明：测试包含 null 值的单元素列表
    final PairList.Builder<String, Integer> b3 = PairList.builder(); // 创建一个新的 PairList.Builder 对象
    list.clear(); // 清空 list
    b3.add("x", null); // 向构建器添加键值对 ("x", null)，包含 null 值
    list.add(Pair.of("x", null)); // 向 list 添加对应的 Pair 对象
    validate(b3.build(), list); // 验证构建的 PairList 是否与 list 相同
  } // testBuilder 方法结束

  @Test void testReversed() { // 测试方法：使用 @Test 注解标记，测试 reverse 和 reversed 方法
    final PairList<Integer, Integer> list = PairList.of(); // 创建一个空的 PairList
    assertThat("empty list", list, hasToString("[]")); // 验证空列表的 toString() 输出

    list.reverse(); // 调用 reverse() 方法反转空列表
    assertThat("empty list, reversed", list, hasToString("[]")); // 验证反转后的空列表的 toString() 输出
    assertThat(list.reversed(), is(Lists.reverse(list))); // 验证 reversed() 方法返回的反转列表是否与 Lists.reverse(list) 相同
    assertThat(list.reversed().reversed(), is(list)); // 验证两次反转是否返回原始列表

    list.add(1, 2); // 向列表添加键值对 (1, 2)
    list.reverse(); // 调用 reverse() 方法反转单元素列表
    assertThat("singleton list, reversed", list, hasToString("[<1, 2>]")); // 验证反转后的单元素列表的 toString() 输出
    assertThat(list.reversed(), is(Lists.reverse(list))); // 验证 reversed() 方法返回的反转列表是否与 Lists.reverse(list) 相同
    assertThat(list.reversed().reversed(), is(list)); // 验证两次反转是否返回原始列表

    list.reverse(); // 再次调用 reverse() 方法反转单元素列表
    assertThat("singleton list reversed twice", list, // 验证两次反转后的单元素列表的 toString() 输出
        hasToString("[<1, 2>]")); // 期望的字符串表示
    assertThat(list.reversed(), is(Lists.reverse(list))); // 验证 reversed() 方法返回的反转列表是否与 Lists.reverse(list) 相同
    assertThat(list.reversed().reversed(), is(list)); // 验证两次反转是否返回原始列表

    list.add(3, 4); // 向列表添加键值对 (3, 4)
    list.reverse(); // 调用 reverse() 方法反转偶数长度列表
    assertThat("list with even length, reversed", list, // 验证反转后的偶数长度列表的 toString() 输出
        hasToString("[<3, 4>, <1, 2>]")); // 期望的字符串表示
    assertThat(list.reversed(), is(Lists.reverse(list))); // 验证 reversed() 方法返回的反转列表是否与 Lists.reverse(list) 相同
    assertThat(list.reversed().reversed(), is(list)); // 验证两次反转是否返回原始列表

    list.reverse(); // 再次调用 reverse() 方法反转偶数长度列表
    assertThat("list with even length, reversed twice", list, // 验证两次反转后的偶数长度列表的 toString() 输出
        hasToString("[<1, 2>, <3, 4>]")); // 期望的字符串表示
    assertThat(list.reversed(), is(Lists.reverse(list))); // 验证 reversed() 方法返回的反转列表是否与 Lists.reverse(list) 相同
    assertThat(list.reversed().reversed(), is(list)); // 验证两次反转是否返回原始列表

    list.add(5, 6); // 向列表添加键值对 (5, 6)
    list.reverse(); // 调用 reverse() 方法反转奇数长度列表
    assertThat("list with odd length, reversed", list, // 验证反转后的奇数长度列表的 toString() 输出
        hasToString("[<5, 6>, <3, 4>, <1, 2>]")); // 期望的字符串表示
    assertThat(list.reversed(), is(Lists.reverse(list))); // 验证 reversed() 方法返回的反转列表是否与 Lists.reverse(list) 相同
    assertThat(list.reversed().reversed(), is(list)); // 验证两次反转是否返回原始列表
  } // testReversed 方法结束
} // PairListTest 类结束