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
package org.apache.calcite.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.apache.calcite.runtime.BinarySearch.lowerBound;
import static org.apache.calcite.runtime.BinarySearch.upperBound;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import static java.util.Comparator.naturalOrder;

/**
 * Tests {@link org.apache.calcite.runtime.BinarySearch}.
 * // 二分搜索算法的测试类，用于验证 BinarySearch 类中的 lowerBound 和 upperBound 方法的正确性
 * // 该类通过多种测试用例来验证二分搜索在不同场景下的行为，包括：
 * // 1. 简单场景：数组中元素不重复的情况
 * // 2. 重复元素场景：数组中存在重复元素的情况
 * // 3. 缺失元素场景：搜索的元素不在数组中的情况
 * // 4. 空数组场景：数组为空的情况
 * // 5. 单元素场景：数组只有一个元素的情况
 * // 6. 全相同元素场景：数组中所有元素都相同的情况
 */
class BinarySearchTest {
  // 辅助方法：用于测试二分搜索的 lowerBound 和 upperBound 方法
  // 该方法验证在给定数组中搜索指定 key 时，lowerBound 和 upperBound 返回的索引是否符合预期
  // 参数说明：
  //   key: 要搜索的键值
  //   lower: 期望的 lowerBound 返回值（第一个 >= key 的元素索引，不存在则返回 -1）
  //   upper: 期望的 upperBound 返回值（第一个 > key 的元素索引，不存在则返回 -1）
  //   array: 要搜索的数组，必须是有序的
  private void search(int key, int lower, int upper, Integer... array) {
    // 验证 lowerBound 方法：断言在数组 array 中搜索 key 时，lowerBound 返回的索引等于期望值 lower
    // lowerBound 返回第一个 >= key 的元素的索引，如果不存在这样的元素则返回 -1
    assertThat("lower bound of " + key + " in " + Arrays.toString(array),
        lowerBound(array, key, naturalOrder()), is(lower));
    // 验证 upperBound 方法：断言在数组 array 中搜索 key 时，upperBound 返回的索引等于期望值 upper
    // upperBound 返回第一个 > key 的元素的索引，如果不存在这样的元素则返回 -1
    assertThat("upper bound of " + key + " in " + Arrays.toString(array),
        upperBound(array, key, naturalOrder()), is(upper));
  }

  // 测试简单场景：数组中元素不重复的情况
  // 测试用例说明：
  //   数组 [1, 2, 3] 是严格递增的，没有重复元素
  //   搜索 1：lowerBound=0（第一个 >=1 的元素在索引0），upperBound=0（第一个 >1 的元素在索引0，即元素2）
  //   搜索 2：lowerBound=1（第一个 >=2 的元素在索引1），upperBound=1（第一个 >2 的元素在索引1，即元素3）
  //   搜索 3：lowerBound=2（第一个 >=3 的元素在索引2），upperBound=2（第一个 >3 的元素不存在，返回2）
  @Test void testSimple() {
    // 在数组 [1, 2, 3] 中搜索 1，期望 lowerBound=0，upperBound=0
    search(1, 0, 0, 1, 2, 3);
    // 在数组 [1, 2, 3] 中搜索 2，期望 lowerBound=1，upperBound=1
    search(2, 1, 1, 1, 2, 3);
    // 在数组 [1, 2, 3] 中搜索 3，期望 lowerBound=2，upperBound=2
    search(3, 2, 2, 1, 2, 3);
  }

  // 测试重复元素场景：数组中存在重复元素的情况
  // 测试用例说明：
  //   数组 [1, 1, 2, 2, 3, 3] 包含重复元素
  //   搜索 1：lowerBound=0（第一个 >=1 的元素在索引0），upperBound=1（第一个 >1 的元素在索引1，即元素2）
  //   搜索 2：lowerBound=2（第一个 >=2 的元素在索引2），upperBound=3（第一个 >2 的元素在索引3，即元素3）
  //   搜索 3：lowerBound=4（第一个 >=3 的元素在索引4），upperBound=5（第一个 >3 的元素在索引5，即数组末尾）
  @Test void testRepeated() {
    // 在数组 [1, 1, 2, 2, 3, 3] 中搜索 1，期望 lowerBound=0，upperBound=1
    search(1, 0, 1, 1, 1, 2, 2, 3, 3);
    // 在数组 [1, 1, 2, 2, 3, 3] 中搜索 2，期望 lowerBound=2，upperBound=3
    search(2, 2, 3, 1, 1, 2, 2, 3, 3);
    // 在数组 [1, 1, 2, 2, 3, 3] 中搜索 3，期望 lowerBound=4，upperBound=5
    search(3, 4, 5, 1, 1, 2, 2, 3, 3);
  }

  // 测试缺失元素场景：搜索的元素不在数组中的情况
  // 测试用例说明：
  //   数组 [1, 2, 4] 中缺少元素 3
  //   搜索 0：lowerBound=-1（没有 >=0 的元素），upperBound=-1（没有 >0 的元素）
  //   搜索 3：lowerBound=2（第一个 >=3 的元素在索引2，即元素4），upperBound=1（第一个 >3 的元素在索引1，即元素4）
  //   搜索 5：lowerBound=3（没有 >=5 的元素，返回数组长度），upperBound=3（没有 >5 的元素，返回数组长度）
  @Test void testMissing() {
    // 在数组 [1, 2, 4] 中搜索 0（小于所有元素），期望 lowerBound=-1，upperBound=-1
    search(0, -1, -1, 1, 2, 4);
    // 在数组 [1, 2, 4] 中搜索 3（介于2和4之间），期望 lowerBound=2，upperBound=1
    search(3, 2, 1, 1, 2, 4);
    // 在数组 [1, 2, 4] 中搜索 5（大于所有元素），期望 lowerBound=3，upperBound=3
    search(5, 3, 3, 1, 2, 4);
  }

  // 测试空数组场景：数组为空的情况
  // 测试用例说明：
  //   空数组中搜索任何元素都应该返回 -1
  //   搜索 42：lowerBound=-1（数组为空，没有元素），upperBound=-1（数组为空，没有元素）
  @Test void testEmpty() {
    // 在空数组中搜索 42，期望 lowerBound=-1，upperBound=-1
    search(42, -1, -1);
  }

  // 测试单元素场景：数组只有一个元素的情况
  // 测试用例说明：
  //   数组 [42] 只有一个元素
  //   搜索 41：lowerBound=-1（没有 >=41 的元素），upperBound=-1（没有 >41 的元素）
  //   搜索 42：lowerBound=0（第一个 >=42 的元素在索引0），upperBound=0（第一个 >42 的元素不存在，返回0）
  //   搜索 43：lowerBound=1（没有 >=43 的元素，返回数组长度），upperBound=1（没有 >43 的元素，返回数组长度）
  @Test void testSingle() {
    // 在数组 [42] 中搜索 41（小于数组元素），期望 lowerBound=-1，upperBound=-1
    search(41, -1, -1, 42);
    // 在数组 [42] 中搜索 42（等于数组元素），期望 lowerBound=0，upperBound=0
    search(42, 0, 0, 42);
    // 在数组 [42] 中搜索 43（大于数组元素），期望 lowerBound=1，upperBound=1
    search(43, 1, 1, 42);
  }

  // 测试全相同元素场景：数组中所有元素都相同的情况
  // 测试用例说明：
  //   数组 [1, 1, 1, 1] 中所有元素都是 1
  //   搜索 1：lowerBound=0（第一个 >=1 的元素在索引0），upperBound=3（第一个 >1 的元素不存在，返回数组长度）
  //   搜索 0：lowerBound=-1（没有 >=0 的元素），upperBound=-1（没有 >0 的元素）
  //   搜索 2：lowerBound=4（没有 >=2 的元素，返回数组长度），upperBound=4（没有 >2 的元素，返回数组长度）
  @Test void testAllTheSame() {
    // 在数组 [1, 1, 1, 1] 中搜索 1（等于所有元素），期望 lowerBound=0，upperBound=3
    search(1, 0, 3, 1, 1, 1, 1);
    // 在数组 [1, 1, 1, 1] 中搜索 0（小于所有元素），期望 lowerBound=-1，upperBound=-1
    search(0, -1, -1, 1, 1, 1, 1);
    // 在数组 [1, 1, 1, 1] 中搜索 2（大于所有元素），期望 lowerBound=4，upperBound=4
    search(2, 4, 4, 1, 1, 1, 1);
  }
}
