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
package org.apache.calcite.util; // 声明包名，该类位于 org.apache.calcite.util 包下，是 Calcite 工具包的一部分
import org.apache.calcite.linq4j.function.Function0; // 导入 Function0 函数式接口，用于延迟创建 List 对象

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的不可变列表类，用于创建不可修改的列表

import org.junit.jupiter.api.Test; // 导入 JUnit 5 的 Test 注解，用于标记测试方法

import java.util.ArrayList; // 导入 Java 标准库的 ArrayList 实现，用于对比测试
import java.util.Arrays; // 导入 Java 标准库的 Arrays 工具类，用于数组操作
import java.util.Collections; // 导入 Java 标准库的 Collections 工具类，提供集合操作的静态方法
import java.util.Iterator; // 导入 Java 标准库的 Iterator 接口，用于迭代遍历集合
import java.util.LinkedList; // 导入 Java 标准库的 LinkedList 实现，用于对比测试
import java.util.List; // 导入 Java 标准库的 List 接口，定义列表的基本行为
import java.util.ListIterator; // 导入 Java 标准库的 ListIterator 接口，支持双向遍历和修改
import java.util.Random; // 导入 Java 标准库的 Random 类，用于生成随机数

import static org.hamcrest.CoreMatchers.is; // 导入 Hamcrest 匹配器的 is 方法，用于断言值相等
import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 的断言方法，用于验证条件
import static org.hamcrest.Matchers.hasSize; // 导入 Hamcrest 的 hasSize 匹配器，用于验证集合大小
import static org.hamcrest.Matchers.hasToString; // 导入 Hamcrest 的 hasToString 匹配器，用于验证字符串表示
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入 JUnit 5 的 assertFalse 断言方法
import static org.junit.jupiter.api.Assertions.assertNull; // 导入 JUnit 5 的 assertNull 断言方法
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入 JUnit 5 的 assertTrue 断言方法
import static org.junit.jupiter.api.Assertions.fail; // 导入 JUnit 5 的 fail 方法，用于标记测试失败

/**
 * ChunkList 的单元测试和性能测试类
 * 
 * 本类用于全面测试 ChunkList 的功能正确性和性能表现，包括：
 * 1. 基本 List 操作：添加、删除、获取、清空、迭代等
 * 2. 边界条件测试：空列表、单元素列表、跨 chunk 操作等
 * 3. 随机操作测试：通过随机操作验证 ChunkList 与标准 List 实现的一致性
 * 4. 性能对比测试：对比 ChunkList、ArrayList、LinkedList 在不同场景下的性能
 * 
 * ChunkList 是一种基于分块（chunk）的列表实现，特别适合频繁添加和删除的场景
 * 它将元素存储在固定大小的块中，避免了 ArrayList 扩容时的数组复制开销
 */
class ChunkListTest { // 测试类定义，测试 ChunkList 的各种功能
  /**
   * ChunkList 的单元测试方法，全面测试 ChunkList 的基本功能
   * 
   * 测试内容包括：
   * 1. 构造函数：空列表、基于现有列表的复制构造
   * 2. 基本操作：add、get、remove、set、isEmpty、size
   * 3. 边界条件：空列表操作、索引越界异常
   * 4. null 值处理：支持 null 元素
   * 5. 批量操作：addAll、removeAll、contains
   * 6. 迭代操作：for-each 循环、indexOf
   * 7. 排序操作：Collections.sort
   * 8. 迭代器操作：ListIterator 的 add、remove
   * 
   * 该测试确保 ChunkList 的行为与标准 List 接口规范完全一致
   */
  @Test void testChunkList() { // 测试方法标记，JUnit 5 会自动执行此方法
    final ChunkList<Integer> list = new ChunkList<>(); // 创建一个空的 ChunkList 实例，用于后续测试
    final ChunkList<Integer> list0 = new ChunkList<>(list); // 基于空列表创建新的 ChunkList，测试复制构造函数，结果应该是空列表
    final ChunkList<Integer> list1 = new ChunkList<>(list); // 再创建一个基于空列表的 ChunkList
    list1.add(123); // 向 list1 添加一个元素 123，测试 add 方法
    assertThat(list, hasSize(0)); // 验证原 list 仍然是空的，说明构造函数是深拷贝或空拷贝
    assertThat(list0, hasSize(0)); // 验证 list0 仍然是空的
    assertThat(list1, hasSize(1)); // 验证 list1 现在有 1 个元素
    assertTrue(list.isEmpty()); // 验证 list 的 isEmpty 方法返回 true
    assertThat(list, hasToString("[]")); // 验证 list 的 toString 返回 "[]"

    try { // 测试从空列表删除元素，应该抛出 IndexOutOfBoundsException
      list.remove(0); // 尝试删除索引 0 的元素，但列表为空
      fail("expected exception"); // 如果没有抛出异常，测试失败
    } catch (IndexOutOfBoundsException e) { // 捕获预期的索引越界异常
      // ok // 异常被正确抛出，测试通过
    }

    try { // 测试获取负索引元素，应该抛出 IndexOutOfBoundsException
      list.get(-1); // 尝试获取索引 -1 的元素，索引为负
      fail("expected exception"); // 如果没有抛出异常，测试失败
    } catch (IndexOutOfBoundsException e) { // 捕获预期的索引越界异常
      // ok // 异常被正确抛出，测试通过
    }

    try { // 测试从空列表获取元素，应该抛出 IndexOutOfBoundsException
      list.get(0); // 尝试获取索引 0 的元素，但列表为空
      fail("expected exception"); // 如果没有抛出异常，测试失败
    } catch (IndexOutOfBoundsException e) { // 捕获预期的索引越界异常
      // ok // 异常被正确抛出，测试通过
    }

    list.add(7); // 向 list 添加元素 7
    assertThat(list, hasSize(1)); // 验证 list 现在有 1 个元素
    assertThat(list.get(0), is(7)); // 验证索引 0 的元素是 7
    assertFalse(list.isEmpty()); // 验证 list 不再是空的
    assertThat(list, hasToString("[7]")); // 验证 list 的 toString 返回 "[7]"

    list.add(9); // 添加元素 9 到列表末尾
    list.add(null); // 添加 null 值到列表，测试 ChunkList 对 null 的支持
    list.add(11); // 添加元素 11 到列表末尾
    assertThat(list, hasSize(4)); // 验证 list 现在有 4 个元素
    assertThat(list.get(0), is(7)); // 验证索引 0 的元素是 7
    assertThat(list.get(1), is(9)); // 验证索引 1 的元素是 9
    assertNull(list.get(2)); // 验证索引 2 的元素是 null
    assertThat(list.get(3), is(11)); // 验证索引 3 的元素是 11
    assertFalse(list.isEmpty()); // 验证 list 不为空
    assertThat(list, hasToString("[7, 9, null, 11]")); // 验证 list 的 toString 正确显示 null

    assertTrue(list.contains(9)); // 验证 list 包含元素 9
    assertFalse(list.contains(8)); // 验证 list 不包含元素 8

    list.addAll(Collections.nCopies(70, 1)); // 批量添加 70 个值为 1 的元素，测试 addAll 方法
    assertThat(list, hasSize(74)); // 验证 list 现在有 74 个元素（4 + 70）
    assertThat((int) list.get(40), is(1)); // 验证索引 40 的元素是 1（在批量添加的范围内）
    assertThat((int) list.get(70), is(1)); // 验证索引 70 的元素是 1（批量添加的最后一个元素）

    int n = 0; // 初始化计数器
    for (Integer integer : list) { // 使用 for-each 循环遍历 list，测试迭代器功能
      Util.discard(integer); // 丢弃元素值，只用于触发迭代
      ++n; // 计数器递增
    }
    assertThat(list, hasSize(n)); // 验证迭代次数等于列表大小，确保迭代器正确遍历所有元素

    int i = list.indexOf(null); // 查找 null 元素的索引
    assertThat(i, is(2)); // 验证 null 元素在索引 2 位置

    // can't sort if null is present // 注释：如果列表中有 null，不能进行排序
    list.set(2, 123); // 将索引 2 的 null 替换为 123，测试 set 方法

    i = list.indexOf(null); // 再次查找 null 元素
    assertThat(i, is(-1)); // 验证列表中不再包含 null 元素，返回 -1

    // sort an empty list // 注释：对空列表进行排序
    Collections.sort(list0); // 对空列表 list0 进行排序，应该正常执行
    assertThat(list0.isEmpty(), is(true)); // 验证 list0 仍然是空的

    // sort a list with 1 element // 注释：对只有一个元素的列表进行排序
    Collections.sort(list1); // 对只有一个元素的 list1 进行排序
    assertThat(list1, hasSize(1)); // 验证 list1 仍然有 1 个元素

    Collections.sort(list); // 对 list 进行排序，测试排序功能
    assertThat(list, hasSize(74)); // 验证排序后列表大小不变

    list.remove((Integer) 7); // 删除元素 7（通过对象值删除，不是索引），测试 remove(Object) 方法
    Collections.sort(list); // 再次排序
    assertThat((int) list.get(3), is(1)); // 验证索引 3 的元素是 1（排序后的结果）

    // remove all instances of a value that exists // 注释：删除所有存在的值
    boolean b = list.removeAll(Collections.singletonList(9)); // 删除所有值为 9 的元素
    assertTrue(b); // 验证返回 true，表示确实删除了元素

    // remove all instances of a non-existent value // 注释：删除所有不存在的值
    b = list.removeAll(Collections.singletonList(99)); // 尝试删除所有值为 99 的元素
    assertFalse(b); // 验证返回 false，表示没有删除任何元素

    // remove all instances of a value that occurs in the last chunk // 注释：删除最后一个块中的值
    list.add(12345); // 添加元素 12345，这会在最后一个 chunk 中
    b = list.removeAll(Collections.singletonList(12345)); // 删除所有值为 12345 的元素
    assertTrue(b); // 验证返回 true，表示删除了元素

    // remove all instances of a value that occurs in the last chunk but // 注释：删除最后一个块中的值，但不是最后一个值
    // not as the last value // 注释：不是作为最后一个值
    list.add(12345); // 添加 12345
    list.add(123); // 再添加 123，使 12345 不是最后一个值
    b = list.removeAll(Collections.singletonList(12345)); // 删除所有值为 12345 的元素
    assertTrue(b); // 验证返回 true，表示删除了元素

    assertThat(new ChunkList<>(Collections.nCopies(1000, 77)).size(), // 创建包含 1000 个 77 的 ChunkList 并验证大小
        is(1000)); // 验证大小为 1000

    // add to an empty list via iterator // 注释：通过迭代器向空列表添加元素
    //noinspection MismatchedQueryAndUpdateOfCollection // 忽略集合未使用的警告
    final ChunkList<String> list2 = new ChunkList<>(); // 创建新的 ChunkList 用于测试迭代器
    list2.listIterator(0).add("x"); // 通过 ListIterator 在位置 0 添加元素 "x"
    assertThat(list2, hasToString("[x]")); // 验证 list2 现在包含 "x"

    // add at start // 注释：在开头添加元素
    list2.add(0, "y"); // 在索引 0 位置添加 "y"
    assertThat(list2, hasToString("[y, x]")); // 验证 list2 现在是 ["y", "x"]

    list2.remove(0); // 删除索引 0 的元素 "y"
    assertThat(list2, hasToString("[x]")); // 验证 list2 现在只包含 "x"

    // clear a list of length 5, one element at a time, using an iterator // 注释：使用迭代器逐个清除长度为 5 的列表
    list2.clear(); // 清空 list2
    list2.addAll(ImmutableList.of("a", "b", "c", "d", "e")); // 添加 5 个元素
    assertThat(list2, hasSize(5)); // 验证 list2 大小为 5
    final ListIterator<String> listIterator = list2.listIterator(0); // 获取从位置 0 开始的 ListIterator
    assertThat(listIterator.next(), is("a")); // 获取第一个元素 "a"
    listIterator.remove(); // 删除刚刚获取的元素 "a"
    assertThat(listIterator.next(), is("b")); // 获取下一个元素 "b"
    listIterator.remove(); // 删除 "b"
    assertThat(listIterator.next(), is("c")); // 获取下一个元素 "c"
    listIterator.remove(); // 删除 "c"
    assertThat(listIterator.next(), is("d")); // 获取下一个元素 "d"
    listIterator.remove(); // 删除 "d"
    assertThat(list2, hasSize(1)); // 验证 list2 现在只剩 1 个元素
    assertThat(listIterator.next(), is("e")); // 获取最后一个元素 "e"
    listIterator.remove(); // 删除 "e"
    assertThat(list2, hasSize(0)); // 验证 list2 现在为空
  } // 测试方法结束

  /** Clears lists of various sizes. // 清除不同大小的列表，测试 clear 方法和各种删除方式
   * 
   * 测试不同大小的列表（0 到 129 个元素）的清除操作
   * 包括以下清除方式：
   * 1. 直接调用 clear() 方法
   * 2. 从头到尾逐个删除（remove(0)）
   * 3. 从尾到头逐个删除（remove(size-1)）
   * 4. 随机位置删除
   * 
   * 测试的关键点：
   * - 空列表的清除
   * - 单元素列表的清除
   * - 跨 chunk 边界的大小（32、64、65、66）
   * - 大列表的清除
   */
  @Test void testClear() { // 测试方法标记
    checkListClear(0); // 测试清除空列表
    checkListClear(1); // 测试清除单元素列表
    checkListClear(2); // 测试清除双元素列表
    checkListClear(32); // 测试清除 32 个元素（正好是一个 chunk 的大小）
    checkListClear(64); // 测试清除 64 个元素（两个 chunk）
    checkListClear(65); // 测试清除 65 个元素（跨越 chunk 边界）
    checkListClear(66); // 测试清除 66 个元素（跨越 chunk 边界）
    checkListClear(100); // 测试清除 100 个元素
    checkListClear(127); // 测试清除 127 个元素（接近 chunk 边界）
    checkListClear(128); // 测试清除 128 个元素（正好是 chunk 边界）
    checkListClear(129); // 测试清除 129 个元素（跨越 chunk 边界）
  } // 测试方法结束

  /**
   * 辅助方法：测试不同方式清除列表
   * 
   * @param n 列表的初始大小
   * 
   * 测试四种不同的清除方式：
   * case 0: 使用 clear() 方法直接清空
   * case 1: 从头部逐个删除（最坏情况，每次删除都需要移动后续元素）
   * case 2: 从尾部逐个删除（最优情况，不需要移动元素）
   * case 3: 随机位置删除（测试随机访问和删除的混合场景）
   * 
   * 每种方式都要确保最终列表为空
   */
  private void checkListClear(int n) { // 私有辅助方法，检查列表清除
    for (int i = 0; i < 4; i++) { // 循环测试四种不同的清除方式
      ChunkList<String> list = new ChunkList<>(Collections.nCopies(n, "z")); // 创建包含 n 个 "z" 的列表
      assertThat(list, hasSize(n)); // 验证列表初始大小为 n
      switch (i) { // 根据循环变量选择清除方式
      case 0: // 第一种方式：直接调用 clear()
        list.clear(); // 调用 clear 方法清空列表
        break; // 跳出 switch
      case 1: // 第二种方式：从头到尾逐个删除
        for (int j = 0; j < n; j++) { // 循环 n 次
          list.remove(0); // 每次删除索引 0 的元素，这会导致后续元素前移
        }
        break; // 跳出 switch
      case 2: // 第三种方式：从尾到头逐个删除
        for (int j = 0; j < n; j++) { // 循环 n 次
          list.remove(list.size() - 1); // 每次删除最后一个元素，不需要移动其他元素
        }
        break; // 跳出 switch
      case 3: // 第四种方式：随机位置删除
        Random random = new Random(); // 创建随机数生成器
        for (int j = 0; j < n; j++) { // 循环 n 次
          list.remove(random.nextInt(list.size())); // 随机选择一个索引并删除
        }
        break; // 跳出 switch
      }
      assertThat(list.isEmpty(), is(true)); // 验证列表最终为空
    }
  } // 辅助方法结束

  /**
   * Removing via an iterator. // 通过迭代器删除元素的测试
   * 
   * 测试 ListIterator 的 remove 方法的行为：
   * 1. 在没有调用 next() 或 previous() 之前调用 remove() 应该抛出 IllegalStateException
   * 2. 调用 next() 后可以删除刚刚返回的元素
   * 3. 连续删除多个元素
   * 4. 删除所有元素后，hasNext() 应该返回 false
   * 
   * 这确保 ChunkList 的迭代器实现符合 ListIterator 接口规范
   */
  @Test void testIterator() { // 测试方法标记
    final ChunkList<String> list = new ChunkList<>(); // 创建空的 ChunkList
    list.add("a"); // 添加元素 "a"
    list.add("b"); // 添加元素 "b"
    final ListIterator<String> listIterator = list.listIterator(0); // 获取从位置 0 开始的 ListIterator
    try { // 测试在未调用 next() 之前调用 remove()
      listIterator.remove(); // 尝试删除元素，但还没有调用 next()
      fail("excepted exception"); // 如果没有抛出异常，测试失败
    } catch (IllegalStateException e) { // 捕获预期的非法状态异常
      // ok // 异常被正确抛出，测试通过
    }
    listIterator.next(); // 调用 next() 获取第一个元素 "a"
    listIterator.remove(); // 删除刚刚获取的元素 "a"
    assertThat(list, hasSize(1)); // 验证列表现在只有 1 个元素
    assertThat(listIterator.hasNext(), is(true)); // 验证还有下一个元素
    listIterator.next(); // 调用 next() 获取下一个元素 "b"
    listIterator.remove(); // 删除元素 "b"
    assertThat(list, hasSize(0)); // 验证列表现在为空
    assertThat(listIterator.hasNext(), is(false)); // 验证没有下一个元素了
  } // 测试方法结束

  /**
   * Unit test for {@link ChunkList} that applies random // 对 ChunkList 应用随机操作的单元测试
   * operations. // 这是一种模糊测试方法，通过大量随机操作验证 ChunkList 的正确性
   * 
   * 测试策略：
   * 1. 使用固定的随机种子确保测试的可重复性
   * 2. 同时对 ChunkList 和 ArrayList 执行相同的随机操作
   * 3. 每次操作后验证两个列表的状态是否一致
   * 4. 测试多种初始状态：空列表、从空列表开始、从预填充列表开始
   * 
   * 随机操作包括：
   * - 删除最后一个元素
   * - 在末尾添加元素
   * - 遍历迭代
   * - 删除所有特定值的元素
   * - 在随机位置删除
   * - 通过迭代器在随机位置添加
   * - 清空列表
   * - 在随机位置插入
   * 
   * 这种测试方法可以发现边界条件和复杂场景下的 bug
   */
  @Test void testRandom() { // 测试方法标记
    final int iterationCount = 10000; // 定义每次测试的迭代次数为 10000 次
    checkRandom(new Random(1), new ChunkList<Integer>(), // 使用种子 1 的随机数，从空列表开始测试
        new ArrayList<Integer>(), iterationCount); // 同时对 ArrayList 执行相同操作作为参考
    final Random random = new Random(2); // 使用种子 2 的随机数
    for (int j = 0; j < 10; j++) { // 重复测试 10 次
      checkRandom(random, new ChunkList<Integer>(), new ArrayList<Integer>(), // 从空列表开始
          iterationCount); // 执行 10000 次随机操作
    }
    final ChunkList<Integer> chunkList = // 创建预填充的 ChunkList
        new ChunkList<>(Collections.nCopies(1000, 5)); // 包含 1000 个值为 5 的元素
    final List<Integer> referenceList = new ArrayList<>(chunkList); // 创建相同的 ArrayList 作为参考
    checkRandom(new Random(3), chunkList, referenceList, iterationCount); // 使用种子 3 从预填充列表开始测试
  } // 测试方法结束

  /**
   * 辅助方法：执行随机操作并验证 ChunkList 与参考列表的一致性
   * 
   * @param random 随机数生成器，用于生成随机操作
   * @param list 被测试的 ChunkList
   * @param list2 参考列表（ArrayList），用于验证 ChunkList 的正确性
   * @param iterationCount 迭代次数
   * 
   * 该方法通过以下方式确保 ChunkList 的正确性：
   * 1. 每次迭代开始时验证 ChunkList 的内部状态（isValid）
   * 2. 对两个列表执行相同的操作
   * 3. 验证操作结果的一致性（返回值、列表大小）
   * 4. 验证列表内容完全相同
   * 5. 通过计数器验证添加和删除操作的平衡性
   * 
   * 随机操作的分布（10 种情况）：
   * case 0: 删除最后一个元素（10% 概率）
   * case 1: 在末尾添加元素（10% 概率）
   * case 2: 遍历迭代（10% 概率）
   * case 3: 删除所有特定值的元素（10% 概率）
   * case 4: 在随机位置删除（10% 概率）
   * case 5: 通过迭代器在随机位置添加（10% 概率）
   * case 6: 清空列表（0.5% 概率，因为只有 1/200）
   * case 7-9: 在随机位置插入（30% 概率）
   */
  void checkRandom( // 辅助方法，执行随机操作测试
      Random random, // 随机数生成器
      ChunkList<Integer> list, // 被测试的 ChunkList
      List<Integer> list2, // 参考列表
      int iterationCount) { // 迭代次数
    int removeCount = 0; // 记录删除操作的总次数
    int addCount = 0; // 记录添加操作的总次数
    int size; // 临时变量，存储列表大小
    int e; // 临时变量，存储随机生成的元素值
    final int initialCount = list.size(); // 记录列表的初始大小
    for (int i = 0; i < iterationCount; i++) { // 执行指定次数的迭代
      assert list.isValid(true); // 断言 ChunkList 的内部状态有效（调试用的验证方法）
      switch (random.nextInt(10)) { // 随机选择一种操作（0-9）
      case 0: // case 0: 删除最后一个元素
        // remove last // 注释：删除最后一个元素
        if (!list.isEmpty()) { // 如果列表不为空
          assertThat(list2.isEmpty(), is(false)); // 验证参考列表也不为空
          list.remove(list.size() - 1); // 删除 ChunkList 的最后一个元素
          list2.remove(list2.size() - 1); // 删除参考列表的最后一个元素
          ++removeCount; // 删除计数加 1
        }
        break; // 跳出 switch
      case 1: // case 1: 在末尾添加元素
        // add to end // 注释：在末尾添加元素
        e = random.nextInt(1000); // 生成 0-999 之间的随机数
        list.add(e); // 将随机数添加到 ChunkList 末尾
        list2.add(e); // 将随机数添加到参考列表末尾
        ++addCount; // 添加计数加 1
        break; // 跳出 switch
      case 2: // case 2: 遍历迭代
        int n = 0; // 初始化遍历计数器
        size = list.size(); // 获取当前列表大小
        assertThat(list, hasSize(list2.size())); // 验证两个列表大小相同
        for (Integer integer : list) { // 使用 for-each 遍历 ChunkList
          Util.discard(integer); // 丢弃元素值
          assertTrue(n++ < size); // 验证遍历次数不超过列表大小
        }
        break; // 跳出 switch
      case 3: // case 3: 删除所有特定值的元素
        // remove all instances of a particular value // 注释：删除所有特定值的元素
        size = list.size(); // 记录删除前的列表大小
        final List<Integer> zz = Collections.singletonList(random.nextInt(500)); // 创建包含一个随机值的列表
        boolean b = list.removeAll(zz); // 从 ChunkList 删除所有该值
        boolean b2 = list2.removeAll(zz); // 从参考列表删除所有该值
        assertThat(b, is(b2)); // 验证两个操作的返回值相同
        if (b) { // 如果确实删除了元素
          assertTrue(list.size() < size); // 验证 ChunkList 大小减小
          assertTrue(list2.size() < size); // 验证参考列表大小减小
        } else { // 如果没有删除任何元素
          assertThat(list, hasSize(size)); // 验证 ChunkList 大小不变
          assertThat(list2, hasSize(size)); // 验证参考列表大小不变
        }
        removeCount += size - list.size(); // 更新删除计数（删除的元素数量）
        break; // 跳出 switch
      case 4: // case 4: 在随机位置删除
        // remove at random position // 注释：在随机位置删除
        if (!list.isEmpty()) { // 如果列表不为空
          e = random.nextInt(list.size()); // 生成一个随机索引
          list.remove(e); // 删除 ChunkList 中该索引的元素
          list2.remove(e); // 删除参考列表中该索引的元素
          ++removeCount; // 删除计数加 1
        }
        break; // 跳出 switch
      case 5: // case 5: 通过迭代器在随机位置添加
        // add at random position // 注释：在随机位置添加
        int count = random.nextInt(list.size() + 1); // 生成一个随机位置（0 到 size）
        ListIterator<Integer> it = list.listIterator(); // 获取 ChunkList 的迭代器
        ListIterator<Integer> it2 = list2.listIterator(); // 获取参考列表的迭代器
        for (int j = 0; j < count; j++) { // 将迭代器移动到指定位置
          it.next(); // ChunkList 迭代器前进
          it2.next(); // 参考列表迭代器前进
        }
        size = list.size(); // 记录当前列表大小
        it.add(size); // 通过 ChunkList 迭代器添加元素（值为当前大小）
        it2.add(size); // 通过参考列表迭代器添加元素
        ++addCount; // 添加计数加 1
        break; // 跳出 switch
      case 6: // case 6: 清空列表
        // clear // 注释：清空列表
        if (random.nextInt(200) == 0) { // 只有 1/200 的概率执行清空操作
          removeCount += list.size(); // 记录删除的元素数量
          list.clear(); // 清空 ChunkList
          list2.clear(); // 清空参考列表
        }
        break; // 跳出 switch
      default: // case 7-9: 在随机位置插入
        // add at random position // 注释：在随机位置插入
        int pos = random.nextInt(list.size() + 1); // 生成一个随机位置
        e = list.size(); // 使用当前列表大小作为元素值
        list.add(pos, e); // 在 ChunkList 的指定位置插入元素
        list2.add(pos, e); // 在参考列表的指定位置插入元素
        ++addCount; // 添加计数加 1
        break; // 跳出 switch
      }
      assertThat(initialCount + addCount - removeCount, is(list.size())); // 验证列表大小符合预期
      assertThat(list2, is(list)); // 验证两个列表内容完全相同
    }
  } // 辅助方法结束

  /**
     * 性能测试方法，对比 ChunkList 与其他 List 实现的性能
     * 
     * 测试场景：
     * 1. 大规模添加操作：添加 1000 万个元素，测试 ChunkList 的扩容性能
     * 2. 遍历操作：遍历 1000 万个元素，测试迭代器性能
     * 3. 删除操作：删除 10% 的元素，测试迭代器删除性能
     * 4. 随机访问：随机获取元素，测试 get 操作性能
     * 5. 混合操作：添加、删除、插入、获取的混合场景
     * 
     * 测试的数据规模：100k、1m、10m
     * 
     * 注意：只有在 Benchmark.enabled() 返回 true 时才会执行
     * 这允许在 CI/CD 环境中跳过耗时的性能测试
     */
    @Test void testPerformance() { // 测试方法标记
      if (!Benchmark.enabled()) { // 检查性能测试是否启用
        return; // 如果未启用，直接返回
      }
      //noinspection unchecked // 忽略未检查的类型转换警告
      final Iterable<Pair<Function0<List<Integer>>, String>> factories0 = // 创建列表工厂的迭代器
          Pair.zip( // 将两个列表配对
              Arrays.asList(ArrayList::new, LinkedList::new, ChunkList::new), // 三种 List 的工厂方法
              Arrays.asList("ArrayList", "LinkedList", "ChunkList-64")); // 对应的名称
      final List<Pair<Function0<List<Integer>>, String>> factories1 = // 创建可修改的列表
          new ArrayList<>(); // 初始化 ArrayList
      for (Pair<Function0<List<Integer>>, String> pair : factories0) { // 遍历所有工厂
        factories1.add(pair); // 添加到可修改列表
      }
      List<Pair<Function0<List<Integer>>, String>> factories = // 选择要测试的工厂
          factories1.subList(2, 3); // 只选择 ChunkList（索引 2）
      Iterable<Pair<Integer, String>> sizes = // 创建数据规模的迭代器
          Pair.zip( // 将两个列表配对
              Arrays.asList(100000, 1000000, 10000000), // 三种数据规模
              Arrays.asList("100k", "1m", "10m")); // 对应的名称
      for (final Pair<Function0<List<Integer>>, String> pair : factories) { // 遍历所有工厂
        new Benchmark("add 10m values, " + pair.right, statistician -> { // 创建基准测试：添加 1000 万个元素
          final List<Integer> list = pair.left.apply(); // 创建新的列表实例
          long start = System.currentTimeMillis(); // 记录开始时间
          for (int i = 0; i < 10000000; i++) { // 循环 1000 万次
            list.add(1); // 添加元素 1
          }
          statistician.record(start); // 记录耗时
          return null; // 返回 null
        },
        10).run(); // 运行 10 次取平均值
      }
      for (final Pair<Function0<List<Integer>>, String> pair : factories) { // 遍历所有工厂
        new Benchmark("iterate over 10m values, " + pair.right, statistician -> { // 创建基准测试：遍历 1000 万个元素
          final List<Integer> list = pair.left.apply(); // 创建新的列表实例
          list.addAll(Collections.nCopies(10000000, 1)); // 添加 1000 万个元素
          long start = System.currentTimeMillis(); // 记录开始时间
          int count = 0; // 初始化计数器
          for (Integer integer : list) { // 遍历所有元素
            count += integer; // 累加元素值
          }
          statistician.record(start); // 记录耗时
          assert count == 10000000; // 验证累加结果正确
          return null; // 返回 null
        },
        10).run(); // 运行 10 次取平均值
      }
      for (final Pair<Function0<List<Integer>>, String> pair : factories) { // 遍历所有工厂
        for (final Pair<Integer, String> size : sizes) { // 遍历所有数据规模
          if (size.left > 1000000) { // 如果数据规模超过 100 万
            continue; // 跳过此规模（避免耗时过长）
          }
          new Benchmark("delete 10% of " + size.right + " values, " + pair.right, // 创建基准测试：删除 10% 的元素
              statistician -> {
                final List<Integer> list = pair.left.apply(); // 创建新的列表实例
                list.addAll(Collections.nCopies(size.left, 1)); // 添加指定数量的元素
                long start = System.currentTimeMillis(); // 记录开始时间
                int n = 0; // 初始化计数器
                for (Iterator<Integer> it = list.iterator(); it.hasNext();) { // 使用迭代器遍历
                  Integer integer = it.next(); // 获取下一个元素
                  Util.discard(integer); // 丢弃元素值
                  if (n++ % 10 == 0) { // 每 10 个元素删除 1 个
                    it.remove(); // 删除当前元素
                  }
                }
                statistician.record(start); // 记录耗时
                return null; // 返回 null
              },
              10).run(); // 运行 10 次取平均值
        }
      }
      for (final Pair<Function0<List<Integer>>, String> pair : factories) { // 遍历所有工厂
        for (final Pair<Integer, String> size : sizes) { // 遍历所有数据规模
          if (size.left > 1000000) { // 如果数据规模超过 100 万
            continue; // 跳过此规模
          }
          new Benchmark("get from " + size.right + " values, " // 创建基准测试：随机获取元素
              + (size.left / 1000) + " times, " + pair.right, statistician -> {
            final List<Integer> list = pair.left.apply(); // 创建新的列表实例
            list.addAll(Collections.nCopies(size.left, 1)); // 添加指定数量的元素
            final int probeCount = size.left / 1000; // 计算获取次数（数据规模的 1/1000）
            final Random random = new Random(1); // 创建随机数生成器（固定种子）
            long start = System.currentTimeMillis(); // 记录开始时间
            int n = 0; // 初始化累加器
            for (int i = 0; i < probeCount; i++) { // 循环获取指定次数
              n += list.get(random.nextInt(list.size())); // 随机获取元素并累加
            }
            assert n == probeCount; // 验证累加结果正确（所有元素都是 1）
            statistician.record(start); // 记录耗时
            return null; // 返回 null
          },
              10).run(); // 运行 10 次取平均值
        }
      }
      for (final Pair<Function0<List<Integer>>, String> pair : factories) { // 遍历所有工厂
        for (final Pair<Integer, String> size : sizes) { // 遍历所有数据规模
          if (size.left > 1000000) { // 如果数据规模超过 100 万
            continue; // 跳过此规模
          }
          new Benchmark( // 创建基准测试：混合操作
              "add " + size.right // 添加指定数量的元素
              + " values, delete 10%, insert 20%, get 1%, using " // 删除 10%，插入 20%，获取 1%
              + pair.right, statistician -> {
            final List<Integer> list = pair.left.apply(); // 创建新的列表实例
            final int probeCount = size.left / 100; // 计算获取次数
            long start = System.currentTimeMillis(); // 记录开始时间
            list.addAll(Collections.nCopies(size.left, 1)); // 添加指定数量的元素
            final Random random = new Random(1); // 创建随机数生成器（固定种子）
            for (Iterator<Integer> it = list.iterator(); // 遍历列表
                 it.hasNext();) {
              Integer integer = it.next(); // 获取下一个元素
              Util.discard(integer); // 丢弃元素值
              if (random.nextInt(10) == 0) { // 10% 的概率删除
                it.remove(); // 删除当前元素
              }
            }
            for (ListIterator<Integer> it = list.listIterator(); // 使用列表迭代器遍历
                 it.hasNext();) {
              Integer integer = it.next(); // 获取下一个元素
              Util.discard(integer); // 丢弃元素值
              if (random.nextInt(5) == 0) { // 20% 的概率插入
                it.add(2); // 插入元素 2
              }
            }
            int n = 0; // 初始化累加器
            for (int i = 0; i < probeCount; i++) { // 循环获取指定次数
              n += list.get(random.nextInt(list.size())); // 随机获取元素并累加
            }
            assert n > probeCount; // 验证累加结果大于 probeCount（因为插入了 2）
            statistician.record(start); // 记录耗时
            return null; // 返回 null
          },
              10).run(); // 运行 10 次取平均值
        }
      }
    } // 测试方法结束
  } // 类定义结束
