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
package org.apache.calcite.linq4j.test;

import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.EnumerableDefaults;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.function.Function1;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performs a randomized test of {@link EnumerableDefaults#orderBy(Enumerable, Function1, Comparator, int, int)}.
 * // 对 EnumerableDefaults.orderBy 方法执行随机化测试，用于验证带偏移量和限制的排序操作的正确性
 * // 该测试类通过生成随机数据、随机排序规则、随机偏移量和限制数量，综合测试排序功能的正确性
 * // 主要测试点包括：1) 结果数量不超过 fetch 值；2) 结果按指定规则正确排序；3) offset 和 fetch 参数被正确应用
 */
class LimitSortTest {

  /** Row class. */
  // 行类，表示测试数据中的一行记录，用于构建测试数据集
  private static class Row {
    @Nullable String key; // 行的键值，可能为 null，用于排序和比较
    int index; // 行的索引，表示该行在原始数据中的位置，用于在键值相同时保持稳定排序

    @Override public String toString() {
      // 返回行的字符串表示，格式为 "key/index"，便于调试和日志输出
      return this.key + "/" + this.index;
    }
  }

  private Stream<Row> rowStream(long seed) {
    // 根据指定的随机种子生成一个随机行数据流，用于测试
    Random rnd = new Random(seed); // 使用传入的种子创建随机数生成器，确保可重现性
    int n = rnd.nextInt(1_000_000); // 随机生成 0 到 999999 之间的行数
    return IntStream.range(0, n).mapToObj(i -> { // 为每一行生成数据
      int a = n < 2 ? 0 : rnd.nextInt(n / 2); // 生成一个 0 到 n/2 之间的随机数，用于生成键值
      String k = Integer.toString(a, Character.MAX_RADIX); // 将随机数转换为字符串，使用最大基数（36进制）以获得更短的字符串
      Row r = new Row(); // 创建新的行对象
      r.key = rnd.nextBoolean() ? null : ("" + k); // 随机决定 key 为 null 或为生成的字符串，测试 null 值处理
      r.index = i; // 设置行的索引为当前循环索引
      return r; // 返回生成的行对象
    });
  }

  private Enumerable<Row> enumerable(long seed) {
    // 根据指定的随机种子创建一个可枚举对象，封装了随机生成的行数据流
    // 使用 Linq4j.asEnumerable 方法将数据流的迭代器转换为 Enumerable 对象
    return Linq4j.asEnumerable(() -> this.rowStream(seed).iterator());
  }

  @Test void test() {
    // 主测试方法，执行 5 次随机化测试以确保排序功能的正确性
    for (int i = 0; i < 5; i++) { // 循环执行 5 次测试
      long seed = System.nanoTime() ^ System.currentTimeMillis(); // 生成随机种子，结合纳秒时间和毫秒时间以增加随机性
      try {
        this.randomizedTest(seed); // 使用生成的种子执行随机化测试
      } catch (AssertionError e) {
        // replace with AssertionFailedError
        // 如果测试失败，将 AssertionError 包装为 RuntimeException 并输出失败种子，便于调试
        throw new RuntimeException("Failed for seed " + seed, e);
      }
    }
  }

  private void randomizedTest(final long seed) {
    // 核心测试方法，使用指定种子执行一次完整的随机化测试，验证 orderBy 方法在排序、偏移和限制方面的正确性
    Random rnd = new Random(seed); // 使用种子创建随机数生成器，确保测试可重现
    int fetch = rnd.nextInt(10_000) + 1; // 随机生成 1 到 10000 之间的 fetch 值，表示要获取的最大行数
    int tmp = rnd.nextInt(10_000); // 生成临时随机值用于计算 offset
    int offset = Math.max(0, (int) (tmp - .1 * tmp)); // 计算偏移量，跳过的行数，确保为非负数

    Comparator<String> natural = Comparator.naturalOrder(); // 创建自然顺序比较器（字典序）
    Comparator<String> cmp
        = rnd.nextBoolean() ? Comparator.nullsFirst(natural) : Comparator.nullsLast(natural); // 随机选择 null 值排在最前或最后

    Enumerable<Row> ordered =
        EnumerableDefaults.orderBy(this.enumerable(seed), // 调用 orderBy 方法对数据进行排序
            s -> s.key, // 键选择器函数，从每行中提取 key 字段作为排序依据
            cmp, // 使用随机生成的比较器
            offset, fetch); // 应用偏移量和限制数量

    List<Row> result = ordered.toList(); // 将排序后的可枚举对象转换为列表
    assertTrue(
        result.size() <= fetch, // 验证结果大小不超过 fetch 值
        "Fetch " + fetch + " has not been respected, result size was " + result.size()
            + ", offset " + offset); // 如果失败，输出详细的错误信息

    // check result is sorted correctly
    // 验证结果是否正确排序
    for (int i = 1; i < result.size(); i++) { // 遍历结果列表
      Row left = result.get(i - 1); // 获取前一个元素
      Row right = result.get(i); // 获取当前元素
      // use left < right instead of <=, as rows might not appear twice
      // 使用严格小于比较，因为相同的行可能不会出现两次
      assertTrue(isSmaller(left, right, cmp), // 验证前一个元素小于当前元素
          "The following elements have not been ordered correctly: " + left + " " + right); // 如果失败，输出未正确排序的元素
    }

    // check offset and fetch size have been respected
    // 验证偏移量和获取数量是否被正确应用
    @Nullable Row first; // 结果中的第一个元素
    @Nullable Row last; // 结果中的最后一个元素
    if (result.isEmpty()) { // 如果结果为空
      // may happen if the offset is bigger than the number of items
      // 可能发生在偏移量大于总行数的情况
      first = null; // 第一个元素设为 null
      last = null; // 最后一个元素设为 null
    } else { // 如果结果不为空
      first = result.get(0); // 获取第一个元素
      last = result.get(result.size() - 1); // 获取最后一个元素
    }

    int totalItems = 0; // 原始数据中的总行数
    int actOffset = 0; // 实际跳过的行数
    int actFetch = 0; // 实际获取的行数
    for (Row r : (Iterable<Row>) this.rowStream(seed)::iterator) { // 遍历原始数据流
      totalItems++; // 统计总行数
      if (isSmaller(r, first, cmp)) { // 如果当前行小于结果中的第一个元素
        actOffset++; // 增加实际跳过的行数
      } else if (isSmallerEq(r, last, cmp)) { // 如果当前行小于等于结果中的最后一个元素
        actFetch++; // 增加实际获取的行数
      }
    }

    // we can skip at most 'totalItems'
    // 最多只能跳过总行数
    int expOffset = Math.min(offset, totalItems); // 计算预期的偏移量
    assertThat("Offset has not been respected.", actOffset, is(expOffset)); // 验证实际偏移量是否等于预期偏移量
    // we can only fetch items if there are enough
    // 只有在有足够项目时才能获取
    int expFetch = Math.min(totalItems - expOffset, fetch); // 计算预期的获取数量
    assertThat("Fetch has not been respected.", actFetch, is(expFetch)); // 验证实际获取数量是否等于预期获取数量
  }

  /** A comparison function that takes the order of creation into account. */
  // 一个比较函数，在比较两个行时考虑创建顺序（索引），确保排序的稳定性
  private static boolean isSmaller(@Nullable Row left, @Nullable Row right,
      Comparator<String> cmp) {
    if (right == null) { // 如果右边的行是 null
      return true; // 左边的行小于右边的行（null 被视为最大值）
    }
    if (left == null) { // 如果左边的行是 null
      return false; // 左边的行不小于右边的行（null 被视为最大值）
    }
    int c = cmp.compare(left.key, right.key); // 使用提供的比较器比较两个行的 key 字段
    if (c != 0) { // 如果 key 不同
      return c < 0; // 返回比较结果，true 表示 left.key 小于 right.key
    }
    return left.index < right.index; // 如果 key 相同，则比较索引，确保稳定排序
  }

  /** See {@link #isSmaller(Row, Row, Comparator)}. */
  // 参见 isSmaller 方法，这是一个小于等于比较函数，用于验证获取的范围
  private static boolean isSmallerEq(@Nullable Row left, @Nullable Row right,
      Comparator<String> cmp) {
    if (right == null) { // 如果右边的行是 null
      return true; // 左边的行小于等于右边的行（null 被视为最大值）
    }
    if (left == null) { // 如果左边的行是 null
      return false; // 左边的行不小于等于右边的行（null 被视为最大值）
    }
    int c = cmp.compare(left.key, right.key); // 使用提供的比较器比较两个行的 key 字段
    if (c != 0) { // 如果 key 不同
      return c < 0; // 返回比较结果，true 表示 left.key 小于 right.key
    }
    return left.index <= right.index; // 如果 key 相同，则比较索引，使用小于等于以包含边界值
  }
}
