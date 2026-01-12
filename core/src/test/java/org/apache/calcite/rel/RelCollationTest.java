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
package org.apache.calcite.rel; // 声明包名，该类属于org.apache.calcite.rel包，这是Calcite框架中关系代数(Relational Algebra)的核心包

import org.apache.calcite.util.ImmutableIntList; // 导入不可变整数列表工具类，用于存储不可变的字段索引列表
import org.apache.calcite.util.mapping.Mapping; // 导入映射接口，用于字段索引之间的映射转换
import org.apache.calcite.util.mapping.Mappings; // 导入映射工具类，提供创建映射的静态工厂方法

import com.google.common.collect.Lists; // 导入Google Guava库的Lists工具类，提供便捷的列表操作方法

import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.ArrayList; // 导入Java标准库的ArrayList类，用于动态数组列表
import java.util.Arrays; // 导入Java标准库的Arrays类，提供数组操作的工具方法
import java.util.List; // 导入Java标准库的List接口，表示有序集合

import static org.apache.calcite.rel.RelCollations.EMPTY; // 静态导入RelCollations类的EMPTY常量，表示空的排序规则
import static org.apache.calcite.rel.RelFieldCollation.Direction.ASCENDING; // 静态导入ASCENDING常量，表示升序排序方向
import static org.apache.calcite.rel.RelFieldCollation.Direction.CLUSTERED; // 静态导入CLUSTERED常量，表示聚类排序方向
import static org.apache.calcite.rel.RelFieldCollation.Direction.DESCENDING; // 静态导入DESCENDING常量，表示降序排序方向
import static org.apache.calcite.rel.RelFieldCollation.Direction.STRICTLY_ASCENDING; // 静态导入STRICTLY_ASCENDING常量，表示严格升序(不允许null)
import static org.apache.calcite.rel.RelFieldCollation.Direction.STRICTLY_DESCENDING; // 静态导入STRICTLY_DESCENDING常量，表示严格降序(不允许null)

import static org.hamcrest.CoreMatchers.equalTo; // 静态导入equalTo匹配器，用于断言相等性
import static org.hamcrest.CoreMatchers.is; // 静态导入is匹配器，用于断言布尔值
import static org.hamcrest.MatcherAssert.assertThat; // 静态导入assertThat断言方法，用于测试验证

/**
 * Tests for {@link RelCollation} and {@link RelFieldCollation}.
 * RelCollationTest类：用于测试RelCollation(关系排序规则)和RelFieldCollation(字段排序规则)功能的单元测试类
 * 
 * RelCollation：表示关系表达式的排序规则，由多个RelFieldCollation组成，定义了数据如何排序
 * RelFieldCollation：表示单个字段的排序规则，包括字段索引和排序方向(升序、降序等)
 * 
 * 该测试类主要验证以下功能：
 * 1. 排序规则包含测试：验证某个排序规则是否包含指定的字段索引
 * 2. 无序包含测试：验证排序规则列表是否包含指定的字段集合(不考虑顺序)
 * 3. 排序规则比较测试：验证排序规则之间的比较逻辑
 * 4. 排序规则映射测试：验证字段映射对排序规则的影响
 * 5. 排序方向反转测试：验证排序方向的反转功能
 */
class RelCollationTest { // RelCollationTest类：测试RelCollation和RelFieldCollation功能的单元测试类
  /** Unit test for {@link RelCollations#contains(List, ImmutableIntList)}.
   * testCollationContains方法：测试RelCollations.contains()方法的单元测试
   * 该方法用于验证给定的排序规则是否包含指定的字段索引列表
   * 
   * 测试要点：
   * - 单个字段包含性检查
   * - 多个字段按顺序包含性检查
   * - 空列表的处理
   * - 重复字段的处理(后出现的重复会被忽略)
   */
  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument") // 抑制编译器警告，允许Arrays.asList使用0个或1个参数
  @Test void testCollationContains() { // 测试方法：验证排序规则包含性检查功能
    final RelCollation collation21 = // 创建一个排序规则对象，包含两个字段的排序：字段2升序，字段1降序
        RelCollations.of( // 使用RelCollations.of()工厂方法创建排序规则
            new RelFieldCollation(2, ASCENDING), // 创建字段2的升序排序规则
            new RelFieldCollation(1, DESCENDING)); // 创建字段1的降序排序规则
    assertThat(RelCollations.contains(collation21, Arrays.asList(2)), is(true)); // 断言：排序规则包含字段2，期望结果为true
    assertThat(RelCollations.contains(collation21, Arrays.asList(1)), // 断言：排序规则不包含字段1(因为字段1不是第一个字段)，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation21, Arrays.asList(0)), // 断言：排序规则不包含字段0，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation21, Arrays.asList(2, 1)), // 断言：排序规则包含字段2和字段1(按顺序)，期望结果为true
        is(true)); // 断言期望值为true
    assertThat(RelCollations.contains(collation21, Arrays.asList(2, 0)), // 断言：排序规则不包含字段2和字段0(因为字段0不存在)，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation21, Arrays.asList(2, 1, 3)), // 断言：排序规则不包含字段2、1、3(因为字段3不存在)，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation21, Arrays.asList()), // 断言：排序规则包含空列表，期望结果为true(空列表总是被包含)
        is(true)); // 断言期望值为true

    // if there are duplicates in keys, later occurrences are ignored
    // 如果键中有重复项，后出现的重复项会被忽略
    assertThat(RelCollations.contains(collation21, Arrays.asList(2, 1, 2)), // 断言：排序规则包含2、1、2(重复的2被忽略)，期望结果为true
        is(true)); // 断言期望值为true
    assertThat(RelCollations.contains(collation21, Arrays.asList(2, 1, 1)), // 断言：排序规则包含2、1、1(重复的1被忽略)，期望结果为true
        is(true)); // 断言期望值为true
    assertThat(RelCollations.contains(collation21, Arrays.asList(1, 2, 1)), // 断言：排序规则不包含1、2、1(因为1不是第一个字段)，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation21, Arrays.asList(1, 1)), // 断言：排序规则不包含1、1(因为1不是第一个字段)，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation21, Arrays.asList(2, 2)), // 断言：排序规则包含2、2(重复的2被忽略)，期望结果为true
        is(true)); // 断言期望值为true

    final RelCollation collation1 = // 创建一个排序规则对象，只包含字段1的降序排序
        RelCollations.of( // 使用RelCollations.of()工厂方法创建排序规则
            new RelFieldCollation(1, DESCENDING)); // 创建字段1的降序排序规则
    assertThat(RelCollations.contains(collation1, Arrays.asList(1, 1)), // 断言：排序规则包含1、1(重复的1被忽略)，期望结果为true
        is(true)); // 断言期望值为true
    assertThat(RelCollations.contains(collation1, Arrays.asList(2, 2)), // 断言：排序规则不包含2、2(因为字段2不存在)，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation1, Arrays.asList(1, 2, 1)), // 断言：排序规则不包含1、2、1(因为字段2不存在)，期望结果为false
        is(false)); // 断言期望值为false
    assertThat(RelCollations.contains(collation1, Arrays.asList()), // 断言：排序规则包含空列表，期望结果为true(空列表总是被包含)
        is(true)); // 断言期望值为true
  }

  /** Unit test for {@link RelCollations#collationsContainKeysOrderless(List, List)}.
   * testCollationsContainKeysOrderless方法：测试RelCollations.collationsContainKeysOrderless()方法的单元测试
   * 该方法用于验证排序规则列表是否包含指定的字段集合(不考虑字段顺序)
   * 
   * 测试要点：
   * - 单个排序规则包含多个字段的情况
   * - 字段顺序不影响判断结果
   * - 重复字段的处理
   * - 不存在的字段的处理
   */
  @Test void testCollationsContainKeysOrderless() { // 测试方法：验证排序规则列表无序包含性检查功能
    final List<RelCollation> collations = Lists.newArrayList(collation(2, 3, 1)); // 创建排序规则列表，包含一个排序规则：字段2、3、1
    assertThat( // 断言：排序规则列表包含字段2、2(重复字段)，期望结果为true
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(2, 2)), is(true)); // 断言期望值为true
    assertThat( // 断言：排序规则列表包含字段2、3，期望结果为true
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(2, 3)), is(true)); // 断言期望值为true
    assertThat( // 断言：排序规则列表包含字段3、2(顺序不同)，期望结果为true(无序比较)
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(3, 2)), is(true)); // 断言期望值为true
    assertThat( // 断言：排序规则列表包含字段3、2、1，期望结果为true
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(3, 2, 1)), is(true)); // 断言期望值为true
    assertThat( // 断言：排序规则列表不包含字段3、2、1、0(字段0不存在)，期望结果为false
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(3, 2, 1, 0)), is(false)); // 断言期望值为false
    assertThat( // 断言：排序规则列表不包含字段2、3、0(字段0不存在)，期望结果为false
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(2, 3, 0)), is(false)); // 断言期望值为false
    assertThat( // 断言：排序规则列表不包含字段1(单独字段1不满足包含条件)，期望结果为false
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(1)), is(false)); // 断言期望值为false
    assertThat( // 断言：排序规则列表不包含字段3、1(缺少字段2)，期望结果为false
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(3, 1)), is(false)); // 断言期望值为false
    assertThat( // 断言：排序规则列表不包含字段0(字段0不存在)，期望结果为false
        RelCollations.collationsContainKeysOrderless( // 调用collationsContainKeysOrderless方法进行无序包含检查
        collations, Arrays.asList(0)), is(false)); // 断言期望值为false
  }

  /** Unit test for {@link RelCollations#keysContainCollationsOrderless(List, List)}.
   * testKeysContainCollationsOrderless方法：测试RelCollations.keysContainCollationsOrderless()方法的单元测试
   * 该方法用于验证字段列表是否包含指定的排序规则列表中的所有字段(不考虑顺序)
   * 
   * 测试要点：
   * - 字段列表包含排序规则中所有字段的情况
   * - 字段顺序不影响判断结果
   * - 重复字段的处理
   * - 不存在的字段的处理
   */
  @Test void testKeysContainCollationsOrderless() { // 测试方法：验证字段列表无序包含排序规则功能
    final List<Integer> keys = Arrays.asList(2, 3, 1); // 创建字段列表，包含字段2、3、1
    assertThat( // 断言：字段列表包含排序规则(2, 2)，期望结果为true(字段2存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(2, 2))), is(true)); // 断言期望值为true
    assertThat( // 断言：字段列表包含排序规则(2, 3)，期望结果为true(字段2和3都存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(2, 3))), is(true)); // 断言期望值为true
    assertThat( // 断言：字段列表包含排序规则(3, 2)，期望结果为true(字段3和2都存在，顺序不影响)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(3, 2))), is(true)); // 断言期望值为true
    assertThat( // 断言：字段列表包含排序规则(3, 2, 1)，期望结果为true(所有字段都存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(3, 2, 1))), is(true)); // 断言期望值为true
    assertThat( // 断言：字段列表不包含排序规则(3, 2, 1, 0)，期望结果为false(字段0不存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(3, 2, 1, 0))), is(false)); // 断言期望值为false
    assertThat( // 断言：字段列表不包含排序规则(2, 3, 0)，期望结果为false(字段0不存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(2, 3, 0))), is(false)); // 断言期望值为false
    assertThat( // 断言：字段列表包含排序规则(1)，期望结果为true(字段1存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(1))), is(true)); // 断言期望值为true
    assertThat( // 断言：字段列表包含排序规则(3, 1)，期望结果为true(字段3和1都存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(3, 1))), is(true)); // 断言期望值为true
    assertThat( // 断言：字段列表不包含排序规则(0)，期望结果为false(字段0不存在)
        RelCollations.keysContainCollationsOrderless( // 调用keysContainCollationsOrderless方法进行无序包含检查
            keys, Lists.newArrayList(collation(0))), is(false)); // 断言期望值为false
  }

  /**
   * Unit test for {@link org.apache.calcite.rel.RelCollationImpl#compareTo}.
   * testCollationCompare方法：测试RelCollationImpl.compareTo()方法的单元测试
   * 该方法用于比较两个排序规则的大小关系
   * 
   * 比较规则：
   * - 先比较字段数量，数量多的更大
   * - 如果字段数量相同，则逐个比较字段索引，索引大的更大
   * - 返回值：0表示相等，正数表示大于，负数表示小于
   * 
   * 测试要点：
   * - 相同排序规则的比较
   * - 不同字段数量的比较
   * - 相同字段数量但不同字段的比较
   * - 空排序规则的比较
   */
  @Test void testCollationCompare() { // 测试方法：验证排序规则比较功能
    assertThat(collation(1, 2).compareTo(collation(1, 2)), equalTo(0)); // 断言：排序规则(1, 2)与(1, 2)比较，期望结果为0(相等)
    assertThat(collation(1, 2).compareTo(collation(1)), equalTo(1)); // 断言：排序规则(1, 2)与(1)比较，期望结果为1(大于，因为字段数量更多)
    assertThat(collation(1).compareTo(collation(1, 2)), equalTo(-1)); // 断言：排序规则(1)与(1, 2)比较，期望结果为-1(小于，因为字段数量更少)
    assertThat(collation(1, 3).compareTo(collation(1, 2)), equalTo(1)); // 断言：排序规则(1, 3)与(1, 2)比较，期望结果为1(大于，因为第二个字段3 > 2)
    assertThat(collation(0, 3).compareTo(collation(1, 2)), equalTo(-1)); // 断言：排序规则(0, 3)与(1, 2)比较，期望结果为-1(小于，因为第一个字段0 < 1)
    assertThat(collation().compareTo(collation(0)), equalTo(-1)); // 断言：空排序规则与(0)比较，期望结果为-1(小于，因为字段数量更少)
    assertThat(collation(1).compareTo(collation()), equalTo(1)); // 断言：排序规则(1)与空排序规则比较，期望结果为1(大于，因为字段数量更多)
  }

  @Test void testCollationMapping() { // 测试方法：验证排序规则在字段映射下的转换功能
    final int n = 10; // Mapping source count. // 定义源字段数量为10，用于创建映射
    // [0]
    RelCollation collation0 = collation(0); // 创建包含字段0的排序规则
    assertThat(collation0.apply(mapping(n, 0)), is(collation0)); // 断言：应用映射(0->0)后，排序规则保持不变
    assertThat(collation0.apply(mapping(n, 1)), is(EMPTY)); // 断言：应用映射(1->0)后，字段0不存在于映射中，返回空排序规则
    assertThat(collation0.apply(mapping(n, 0, 1)), is(collation0)); // 断言：应用映射(0->0, 1->1)后，排序规则保持不变
    assertThat(collation0.apply(mapping(n, 1, 0)), is(collation(1))); // 断言：应用映射(1->0, 0->1)后，字段0映射到字段1
    assertThat(collation0.apply(mapping(n, 3, 1, 0)), is(collation(2))); // 断言：应用映射(3->0, 1->1, 0->2)后，字段0映射到字段2

    // [0,1]
    RelCollation collation01 = collation(0, 1); // 创建包含字段0和1的排序规则
    assertThat(collation01.apply(mapping(n, 0)), is(collation(0))); // 断言：应用映射(0->0)后，字段1不存在于映射中，只剩下字段0
    assertThat(collation01.apply(mapping(n, 1)), is(EMPTY)); // 断言：应用映射(1->0)后，字段0和1都不存在于映射中，返回空排序规则
    assertThat(collation01.apply(mapping(n, 2)), is(EMPTY)); // 断言：应用映射(2->0)后，字段0和1都不存在于映射中，返回空排序规则
    assertThat(collation01.apply(mapping(n, 0, 1)), is(collation01)); // 断言：应用映射(0->0, 1->1)后，排序规则保持不变
    assertThat(collation01.apply(mapping(n, 1, 0)), is(collation(1, 0))); // 断言：应用映射(1->0, 0->1)后，字段0和1互换位置
    assertThat(collation01.apply(mapping(n, 3, 1, 0)), is(collation(2, 1))); // 断言：应用映射(3->0, 1->1, 0->2)后，字段0映射到2，字段1映射到1
    assertThat(collation01.apply(mapping(n, 3, 2, 0)), is(collation(2))); // 断言：应用映射(3->0, 2->1, 0->2)后，字段0映射到2，字段1不存在于映射中

    // [2,3,4]
    RelCollation collation234 = collation(2, 3, 4); // 创建包含字段2、3、4的排序规则
    assertThat(collation234.apply(mapping(n, 0)), is(EMPTY)); // 断言：应用映射(0->0)后，字段2、3、4都不存在于映射中，返回空排序规则
    assertThat(collation234.apply(mapping(n, 1)), is(EMPTY)); // 断言：应用映射(1->0)后，字段2、3、4都不存在于映射中，返回空排序规则
    assertThat(collation234.apply(mapping(n, 2)), is(collation(0))); // 断言：应用映射(2->0)后，字段2映射到0，字段3、4不存在于映射中
    assertThat(collation234.apply(mapping(n, 3)), is(EMPTY)); // 断言：应用映射(3->0)后，字段2、3、4都不存在于映射中，返回空排序规则
    assertThat(collation234.apply(mapping(n, 4)), is(EMPTY)); // 断言：应用映射(4->0)后，字段2、3、4都不存在于映射中，返回空排序规则
    assertThat(collation234.apply(mapping(n, 5)), is(EMPTY)); // 断言：应用映射(5->0)后，字段2、3、4都不存在于映射中，返回空排序规则
    assertThat(collation234.apply(mapping(n, 0, 1, 2)), is(collation(2))); // 断言：应用映射(0->0, 1->1, 2->2)后，字段2映射到2，字段3、4不存在于映射中
    assertThat(collation234.apply(mapping(n, 3, 2)), is(collation(1, 0))); // 断言：应用映射(3->0, 2->1)后，字段2映射到1，字段3映射到0，字段4不存在于映射中
    assertThat(collation234.apply(mapping(n, 3, 2, 4)), is(collation(1, 0, 2))); // 断言：应用映射(3->0, 2->1, 4->2)后，字段2映射到1，字段3映射到0，字段4映射到2
    assertThat(collation234.apply(mapping(n, 3, 2, 4)), is(collation(1, 0, 2))); // 断言：重复验证相同的映射结果
    assertThat(collation234.apply(mapping(n, 4, 3, 2, 0)), is(collation(2, 1, 0))); // 断言：应用映射(4->0, 3->1, 2->2, 0->3)后，字段2映射到2，字段3映射到1，字段4映射到0
    assertThat(collation234.apply(mapping(n, 3, 4, 0)), is(EMPTY)); // 断言：应用映射(3->0, 4->1, 0->2)后，字段2不存在于映射中，返回空排序规则

    // [9] , 9 < mapping.sourceCount()
    RelCollation collation9 = collation(n - 1); // 创建包含字段9的排序规则(字段索引9小于源字段数量10)
    assertThat(collation9.apply(mapping(n, 0)), is(EMPTY)); // 断言：应用映射(0->0)后，字段9不存在于映射中，返回空排序规则
    assertThat(collation9.apply(mapping(n, 1)), is(EMPTY)); // 断言：应用映射(1->0)后，字段9不存在于映射中，返回空排序规则
    assertThat(collation9.apply(mapping(n, 2)), is(EMPTY)); // 断言：应用映射(2->0)后，字段9不存在于映射中，返回空排序规则
    assertThat(collation9.apply(mapping(n, n - 1)), is(collation(0))); // 断言：应用映射(9->0)后，字段9映射到字段0
  }

  /**
   * Unit test for {@link RelFieldCollation.Direction#reverse()}.
   * testDirectionReverse方法：测试RelFieldCollation.Direction.reverse()方法的单元测试
   * 该方法用于反转排序方向
   * 
   * 反转规则：
   * - ASCENDING(升序) <-> DESCENDING(降序)
   * - STRICTLY_ASCENDING(严格升序) <-> STRICTLY_DESCENDING(严格降序)
   * - CLUSTERED(聚类) -> CLUSTERED(聚类，保持不变)
   * 
   * 测试要点：
   * - 升序和降序的反转
   * - 严格升序和严格降序的反转
   * - 聚类排序的反转(应保持不变)
   */
  @Test void testDirectionReverse() { // 测试方法：验证排序方向反转功能
    assertThat(ASCENDING.reverse(), is(DESCENDING)); // 断言：升序反转后变为降序
    assertThat(DESCENDING.reverse(), is(ASCENDING)); // 断言：降序反转后变为升序
    assertThat(STRICTLY_ASCENDING.reverse(), is(STRICTLY_DESCENDING)); // 断言：严格升序反转后变为严格降序
    assertThat(STRICTLY_DESCENDING.reverse(), is(STRICTLY_ASCENDING)); // 断言：严格降序反转后变为严格升序
    assertThat(CLUSTERED.reverse(), is(CLUSTERED)); // 断言：聚类排序反转后仍为聚类排序(保持不变)
  }

  private static RelCollation collation(int... ordinals) { // collation辅助方法：根据字段索引创建排序规则对象
    final List<RelFieldCollation> list = new ArrayList<>(); // 创建字段排序规则列表
    for (int ordinal : ordinals) { // 遍历所有字段索引
      list.add(new RelFieldCollation(ordinal)); // 为每个字段索引创建默认升序的RelFieldCollation对象并添加到列表
    }
    return RelCollations.of(list); // 使用字段排序规则列表创建RelCollation对象并返回
  }

  private static Mapping mapping(int sourceCount, int... sources) { // mapping辅助方法：创建字段映射对象
    return Mappings.target(ImmutableIntList.of(sources), sourceCount); // 使用Mappings.target()方法创建目标映射，将sources数组映射到目标索引
  }
} // 类结束
