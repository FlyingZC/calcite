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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能
// 本包 org.apache.calcite.test 包含 Calcite 框架的测试类，用于验证核心功能的正确性
package org.apache.calcite.test;

// 导入 Aggregate 类，它是 Calcite 中表示聚合操作的关系代数节点，支持 GROUP BY、ROLLUP、CUBE 等分组方式
import org.apache.calcite.rel.core.Aggregate;
// 导入 ImmutableBitSet 类，它是 Calcite 中用于表示不可变位集合的工具类，常用于表示列索引集合
import org.apache.calcite.util.ImmutableBitSet;

// 导入 JUnit 5 的 Test 注解，用于标记测试方法
import org.junit.jupiter.api.Test;

// 导入 Java 标准库中的 ArrayList 和 List 类，用于存储分组集合
import java.util.ArrayList;
import java.util.List;

// 导入 Hamcrest 断言库的核心匹配器，用于编写更易读的测试断言
import static org.hamcrest.CoreMatchers.is;
// 导入 Hamcrest 的 MatcherAssert 类，提供 assertThat 方法用于断言
import static org.hamcrest.MatcherAssert.assertThat;
// 导入 Hamcrest 的 hasToString 匹配器，用于验证对象的字符串表示
import static org.hamcrest.Matchers.hasToString;
// 导入 JUnit 5 的 fail 方法，用于标记测试失败
import static org.junit.jupiter.api.Assertions.fail;

/**
 * InduceGroupingTypeTest 类：用于测试 Aggregate.Group.induce() 方法的单元测试类
 * 
 * Aggregate.Group 是 Calcite 中表示分组类型的枚举，包含四种类型：
 * - SIMPLE: 简单分组，只有一个 GROUP BY 集合
 * - ROLLUP: 分层小计分组，按指定顺序逐层减少分组字段
 * - CUBE: 多维交叉分组，生成所有可能的分组组合
 * - OTHER: 其他不规则的分组方式
 * 
 * induce() 方法的作用：根据给定的 groupSet（完整分组集合）和 groupSets（所有分组集合的列表）
 * 自动推断出该分组操作属于哪种类型（SIMPLE、ROLLUP、CUBE 或 OTHER）
 * 
 * 本测试类通过多种场景验证 induce() 方法的正确性，包括：
 * 1. 简单分组场景
 * 2. CUBE 分组场景（包括单字段和多字段）
 * 3. ROLLUP 分组场景（包括不同顺序的移除方式）
 * 4. 不规则分组场景
 * 5. 边界情况（空分组集、单字段分组集等）
 */
class InduceGroupingTypeTest {
  // 测试方法：testInduceGroupingType - 测试 induce() 方法在各种分组场景下的行为
  // 该方法使用 groupSet = {1, 2, 4, 5} 作为基础分组集合，测试不同的 groupSets 组合
  @Test void testInduceGroupingType() {
    // 创建基础分组集合，包含列索引 1、2、4、5，表示这 4 个字段参与分组
    // ImmutableBitSet 是不可变的位集合，高效地表示一组整数索引
    final ImmutableBitSet groupSet = ImmutableBitSet.of(1, 2, 4, 5);

    // 测试场景 1: SIMPLE（简单分组）
    // SIMPLE 分组的特征：groupSets 只包含一个元素，且该元素等于 groupSet
    // 这是最基本的 GROUP BY 操作，只按指定的字段集合进行分组
    final List<ImmutableBitSet> groupSets = new ArrayList<>();
    // 将完整的分组集合添加到 groupSets 列表中
    groupSets.add(groupSet);
    // 断言：induce() 方法应该返回 Aggregate.Group.SIMPLE
    // 因为只有一个分组集合，且与 groupSet 完全一致
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.SIMPLE));

    // 测试场景 2: CUBE（多维交叉分组）- 单字段情况
    // CUBE 分组的特征：groupSets 包含 groupSet 的所有可能子集（幂集）
    // 当只有一个字段时，幂集只有两个元素：{2} 和 {}（空集）
    // 注意：单字段的 CUBE 也可以被识别为 ROLLUP，但 induce() 方法优先返回 CUBE
    groupSets.clear();
    // 创建单字段分组集合 {2}
    final ImmutableBitSet groupSet0 = ImmutableBitSet.of(2);
    // 添加分组集合 {2}
    groupSets.add(groupSet0);
    // 添加空分组集合 {}，表示全局聚合（不按任何字段分组）
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.CUBE
    // 因为包含了所有可能的分组组合（{2} 和 {}）
    assertThat(Aggregate.Group.induce(groupSet0, groupSets),
        is(Aggregate.Group.CUBE));
    // 断言：isRollup() 方法应该返回 true，因为单字段的 CUBE 也符合 ROLLUP 的定义
    assertThat(Aggregate.Group.isRollup(groupSet0, groupSets), is(true));
    // 断言：getRollup() 方法应该返回 [2]，表示 ROLLUP 的字段顺序
    assertThat(Aggregate.Group.getRollup(groupSets),
        hasToString("[2]"));

    // 测试场景 3: CUBE（多维交叉分组）- 多字段情况
    // 创建 groupSet 的幂集（所有可能的子集），并按自然顺序排序
    // groupSet = {1, 2, 4, 5} 的幂集包含 16 个元素：
    // {}, {1}, {2}, {4}, {5}, {1,2}, {1,4}, {1,5}, {2,4}, {2,5}, {4,5}, {1,2,4}, {1,2,5}, {1,4,5}, {2,4,5}, {1,2,4,5}
    final List<ImmutableBitSet> groupSets0 =
        ImmutableBitSet.ORDERING.sortedCopy(groupSet.powerSet());
    // 断言：induce() 方法应该返回 Aggregate.Group.CUBE
    // 因为 groupSets0 包含了 groupSet 的所有可能子集（幂集）
    assertThat(Aggregate.Group.induce(groupSet, groupSets0),
        is(Aggregate.Group.CUBE));
    // 断言：isRollup() 方法应该返回 false
    // 因为 CUBE 包含所有子集，而 ROLLUP 只包含按特定顺序逐层减少的子集
    assertThat(Aggregate.Group.isRollup(groupSet, groupSets0), is(false));

    // 测试场景 4: ROLLUP（分层小计分组）- 按顺序移除字段
    // ROLLUP 分组的特征：groupSets 包含按特定顺序逐层减少字段的分组集合
    // 每一层都比上一层少一个或多个字段，最终到达空集
    // 这个测试使用顺序移除的方式：{1,2,4,5} -> {1,2,4} -> {1,2} -> {1} -> {}
    groupSets.clear();
    // 添加完整分组集合 {1, 2, 4, 5}
    groupSets.add(ImmutableBitSet.of(1, 2, 4, 5));
    // 添加移除字段 5 后的分组集合 {1, 2, 4}
    groupSets.add(ImmutableBitSet.of(1, 2, 4));
    // 添加移除字段 4 后的分组集合 {1, 2}
    groupSets.add(ImmutableBitSet.of(1, 2));
    // 添加移除字段 2 后的分组集合 {1}
    groupSets.add(ImmutableBitSet.of(1));
    // 添加移除字段 1 后的空分组集合 {}
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.ROLLUP
    // 因为 groupSets 符合 ROLLUP 的定义：按顺序逐层减少字段
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.ROLLUP));
    // 断言：isRollup() 方法应该返回 true
    assertThat(Aggregate.Group.isRollup(groupSet, groupSets), is(true));
    // 断言：getRollup() 方法应该返回 [1, 2, 4, 5]
    // 表示 ROLLUP 的字段顺序是 1 -> 2 -> 4 -> 5
    assertThat(Aggregate.Group.getRollup(groupSets),
        hasToString("[1, 2, 4, 5]"));

    // 测试场景 5: ROLLUP（分层小计分组）- 非顺序移除字段
    // 这个测试演示了 ROLLUP 可以按任意顺序移除字段
    // 移除顺序：先移除 2，然后移除 1，最后移除 5
    // 分组集合变化：{1,2,4,5} -> {1,4,5} -> {4,5} -> {4} -> {}
    groupSets.clear();
    // 添加完整分组集合 {1, 2, 4, 5}
    groupSets.add(ImmutableBitSet.of(1, 2, 4, 5));
    // 添加移除字段 2 后的分组集合 {1, 4, 5}
    groupSets.add(ImmutableBitSet.of(1, 4, 5));
    // 添加移除字段 1 后的分组集合 {4, 5}
    groupSets.add(ImmutableBitSet.of(4, 5));
    // 添加移除字段 5 后的分组集合 {4}
    groupSets.add(ImmutableBitSet.of(4));
    // 添加移除字段 4 后的空分组集合 {}
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.ROLLUP
    // 虽然不是按索引顺序移除，但仍然符合 ROLLUP 的定义
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.ROLLUP));
    // 断言：getRollup() 方法应该返回 [4, 5, 1, 2]
    // 表示 ROLLUP 的字段顺序是 4 -> 5 -> 1 -> 2（反向索引顺序）
    assertThat(Aggregate.Group.getRollup(groupSets),
        hasToString("[4, 5, 1, 2]"));

    // 测试场景 6: ROLLUP（分层小计分组）- 反向顺序移除字段
    // 这个测试演示了 ROLLUP 可以按反向索引顺序移除字段
    // 移除顺序：5 -> 4 -> 2 -> 1
    // 分组集合变化：{1,2,4,5} -> {2,4,5} -> {4,5} -> {5} -> {}
    groupSets.clear();
    // 添加完整分组集合 {1, 2, 4, 5}
    groupSets.add(ImmutableBitSet.of(1, 2, 4, 5));
    // 添加移除字段 1 后的分组集合 {2, 4, 5}
    groupSets.add(ImmutableBitSet.of(2, 4, 5));
    // 添加移除字段 2 后的分组集合 {4, 5}
    groupSets.add(ImmutableBitSet.of(4, 5));
    // 添加移除字段 4 后的分组集合 {5}
    groupSets.add(ImmutableBitSet.of(5));
    // 添加移除字段 5 后的空分组集合 {}
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.ROLLUP
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.ROLLUP));
    // 断言：getRollup() 方法应该返回 [5, 4, 2, 1]
    // 表示 ROLLUP 的字段顺序是 5 -> 4 -> 2 -> 1（完全反向）
    assertThat(Aggregate.Group.getRollup(groupSets),
        hasToString("[5, 4, 2, 1]"));

    // 测试场景 7: OTHER（不规则分组）- 缺少中间层
    // 这个测试演示了不符合 ROLLUP 规则的分组方式
    // 分组集合：{1,2,4,5} -> {1,2,4} -> {1,2} -> {}
    // 缺少了 {1} 这一层，所以不是 ROLLUP
    groupSets.clear();
    // 添加完整分组集合 {1, 2, 4, 5}
    groupSets.add(ImmutableBitSet.of(1, 2, 4, 5));
    // 添加移除字段 5 后的分组集合 {1, 2, 4}
    groupSets.add(ImmutableBitSet.of(1, 2, 4));
    // 添加移除字段 4 后的分组集合 {1, 2}
    groupSets.add(ImmutableBitSet.of(1, 2));
    // 直接跳到空分组集合 {}，缺少 {1} 这一层
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为不符合 ROLLUP 的逐层减少规则（缺少 {1} 层）
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));

    // 测试场景 8: OTHER（不规则分组）- 缺少空集
    // 分组集合：{1,2,4,5} -> {1,2,4} -> {1,2} -> {1}
    // 缺少了空集 {}，所以不是 ROLLUP
    groupSets.clear();
    // 添加完整分组集合 {1, 2, 4, 5}
    groupSets.add(ImmutableBitSet.of(1, 2, 4, 5));
    // 添加移除字段 5 后的分组集合 {1, 2, 4}
    groupSets.add(ImmutableBitSet.of(1, 2, 4));
    // 添加移除字段 4 后的分组集合 {1, 2}
    groupSets.add(ImmutableBitSet.of(1, 2));
    // 添加移除字段 2 后的分组集合 {1}
    // 缺少空集 {}，所以不是 ROLLUP
    groupSets.add(ImmutableBitSet.of(1));
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为 ROLLUP 必须包含空集作为最后一级
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));

    // 测试场景 9: OTHER（不规则分组）- 包含不符合 ROLLUP 规则的分组
    // 分组集合：{1,2,4,5} -> {1,2,4} -> {1,2} -> {1,4} -> {}
    // {1,4} 不符合 ROLLUP 的逐层减少规则（它不是 {1,2} 的子集）
    groupSets.clear();
    // 添加完整分组集合 {1, 2, 4, 5}
    groupSets.add(ImmutableBitSet.of(1, 2, 4, 5));
    // 添加移除字段 5 后的分组集合 {1, 2, 4}
    groupSets.add(ImmutableBitSet.of(1, 2, 4));
    // 添加移除字段 4 后的分组集合 {1, 2}
    groupSets.add(ImmutableBitSet.of(1, 2));
    // 添加分组集合 {1, 4}，这不是 {1, 2} 的子集（移除了 2 但保留了 4）
    // 这违反了 ROLLUP 的规则，所以是 OTHER
    groupSets.add(ImmutableBitSet.of(1, 4));
    // 添加空分组集合 {}
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为 {1,4} 不符合 ROLLUP 的逐层减少规则
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));

    // 测试场景 10: 非法分组集合 - 第一个分组集合不是 groupSet
    // 分组集合：{1,2,5} -> {1,2,4} -> {1,2} -> {1} -> {}
    // 第一个分组集合 {1,2,5} 不等于 groupSet {1,2,4,5}，这是非法的
    groupSets.clear();
    // 添加分组集合 {1, 2, 5}，这不是 groupSet（缺少 4，多了 5）
    groupSets.add(ImmutableBitSet.of(1, 2, 5));
    // 添加分组集合 {1, 2, 4}
    groupSets.add(ImmutableBitSet.of(1, 2, 4));
    // 添加分组集合 {1, 2}
    groupSets.add(ImmutableBitSet.of(1, 2));
    // 添加分组集合 {1}
    groupSets.add(ImmutableBitSet.of(1));
    // 添加空分组集合 {}
    groupSets.add(ImmutableBitSet.of());

    try {
      // 尝试调用 induce() 方法，应该抛出 IllegalArgumentException
      // 因为第一个分组集合 {1,2,5} 不等于 groupSet {1,2,4,5}
      final Aggregate.Group x = Aggregate.Group.induce(groupSet, groupSets);
      // 如果没有抛出异常，测试失败
      fail("expected error, got " + x);
    } catch (IllegalArgumentException ignore) {
      // 捕获预期的 IllegalArgumentException，测试通过
      // ok
    }

    // 测试场景 11: OTHER - 即使排序后仍然是 OTHER
    // 对非法的分组集合进行排序，然后测试 induce() 方法
    List<ImmutableBitSet> groupSets1 =
        ImmutableBitSet.ORDERING.sortedCopy(groupSets);
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为排序后的分组集合仍然不符合 SIMPLE、ROLLUP 或 CUBE 的规则
    assertThat(Aggregate.Group.induce(groupSet, groupSets1),
        is(Aggregate.Group.OTHER));

    // 测试场景 12: OTHER - 空的 groupSets 列表
    groupSets.clear();
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为空的 groupSets 不是合法的分组方式
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));

    // 测试场景 13: OTHER - 只有空集的 groupSets
    groupSets.clear();
    // 只添加空分组集合 {}
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为只有空集，但 groupSet 不是空集，所以不匹配
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));
  }

  /**
   * 测试方法：testInduceGroupingType1 - 测试单字段分组集合的特殊情况
   * 
   * 单字段分组集合 {2} 的幂集只有两个元素：{2} 和 {}
   * 这种情况下，既可以被识别为 ROLLUP，也可以被识别为 CUBE
   * induce() 方法的策略是优先返回 CUBE（因为 CUBE 是更通用的概念）
   * 
   * 该方法测试以下场景：
   * 1. groupSets = {2}, {} -> 应该返回 CUBE（优先选择 CUBE 而不是 ROLLUP）
   * 2. groupSets = {} -> 应该返回 OTHER（只有空集，不匹配 groupSet）
   * 3. groupSets = {2} -> 应该返回 SIMPLE（只有一个分组集合）
   * 4. groupSets = 空列表 -> 应该返回 OTHER（空的 groupSets）
   */
  @Test void testInduceGroupingType1() {
    // 创建单字段分组集合 {2}
    final ImmutableBitSet groupSet = ImmutableBitSet.of(2);

    // 测试场景 1: 单字段的 CUBE（也可以是 ROLLUP，但优先选择 CUBE）
    // 分组集合：{2}, {}
    // Could be ROLLUP but we prefer CUBE
    List<ImmutableBitSet> groupSets = new ArrayList<>();
    // 添加分组集合 {2}
    groupSets.add(groupSet);
    // 添加空分组集合 {}
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.CUBE
    // 虽然这也符合 ROLLUP 的定义，但 induce() 优先返回 CUBE
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.CUBE));

    // 测试场景 2: 只有空集
    // 分组集合：{}
    groupSets = new ArrayList<>();
    // 只添加空分组集合 {}
    groupSets.add(ImmutableBitSet.of());
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为 groupSet 是 {2}，而 groupSets 只有 {}，不匹配
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));

    // 测试场景 3: SIMPLE（简单分组）
    // 分组集合：{2}
    groupSets = new ArrayList<>();
    // 只添加分组集合 {2}
    groupSets.add(groupSet);
    // 断言：induce() 方法应该返回 Aggregate.Group.SIMPLE
    // 因为只有一个分组集合，且等于 groupSet
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.SIMPLE));

    // 测试场景 4: 空的 groupSets 列表
    // 分组集合：空列表
    groupSets = new ArrayList<>();
    // 不添加任何分组集合
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为空的 groupSets 不是合法的分组方式
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));
  }

  /**
   * 测试方法：testInduceGroupingType0 - 测试空分组集合的特殊情况
   * 
   * 空分组集合 {} 表示不按任何字段分组，即全局聚合
   * 这种情况下：
   * - groupSets = {} -> 应该返回 SIMPLE（只有一个空分组集合）
   * - groupSets = 空列表 -> 应该返回 OTHER（空的 groupSets）
   * 
   * 注意：虽然空分组集合也可以被视为 CUBE 或 ROLLUP 的特殊情况
   * 但 induce() 方法选择返回 SIMPLE，因为这是最简单的表示
   */
  @Test void testInduceGroupingType0() {
    // 创建空分组集合 {}，表示不按任何字段分组（全局聚合）
    final ImmutableBitSet groupSet = ImmutableBitSet.of();

    // 测试场景 1: 空分组集合的 SIMPLE
    // 分组集合：{}
    // Could be CUBE or ROLLUP but we choose SIMPLE
    List<ImmutableBitSet> groupSets = new ArrayList<>();
    // 只添加空分组集合 {}
    groupSets.add(groupSet);
    // 断言：induce() 方法应该返回 Aggregate.Group.SIMPLE
    // 虽然空分组集合也可以被视为 CUBE 或 ROLLUP 的特殊情况
    // 但 induce() 选择返回 SIMPLE，因为这是最简单的表示
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.SIMPLE));

    // 测试场景 2: 空的 groupSets 列表
    // 分组集合：空列表
    groupSets = new ArrayList<>();
    // 不添加任何分组集合
    // 断言：induce() 方法应该返回 Aggregate.Group.OTHER
    // 因为空的 groupSets 不是合法的分组方式
    assertThat(Aggregate.Group.induce(groupSet, groupSets),
        is(Aggregate.Group.OTHER));
  }
}
