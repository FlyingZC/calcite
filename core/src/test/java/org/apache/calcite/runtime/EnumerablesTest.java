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

import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.EnumerableDefaults;
import org.apache.calcite.linq4j.JoinType;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.function.EqualityComparer;
import org.apache.calcite.linq4j.function.Function2;
import org.apache.calcite.linq4j.function.Functions;
import org.apache.calcite.linq4j.function.Predicate2;

import com.google.common.collect.Lists;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static com.google.common.collect.Lists.newArrayList;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;

/**
 * Unit tests for {@link org.apache.calcite.runtime.Enumerables}.
 * Enumerables 类的单元测试类，用于测试各种集合操作和连接操作的正确性
 * 本类主要测试以下功能：
 * 1. 半连接（Semi Join）：只返回左表中与右表匹配的行
 * 2. 反连接（Anti Join）：返回左表中不与右表匹配的行
 * 3. 合并连接（Merge Join）：基于排序的连接算法，支持多种连接类型
 * 4. 嵌套循环连接（Nested Loop Join）：基于嵌套循环的连接算法
 * 5. 哈希连接（Hash Join）：基于哈希表的连接算法
 * 6. 合并联合（Merge Union）：合并多个有序集合
 */
class EnumerablesTest {
  // 测试用的员工集合，包含4个员工记录，用于各种连接操作的测试数据
  private static final Enumerable<Emp> EMPS =
      Linq4j.asEnumerable(
          Arrays.asList(new Emp(10, "Fred"),
              new Emp(20, "Theodore"),
              new Emp(20, "Sebastian"),
              new Emp(30, "Joe")));

  // 测试用的部门集合，包含2个部门记录，用于各种连接操作的测试数据
  private static final Enumerable<Dept> DEPTS =
      Linq4j.asEnumerable(
          Arrays.asList(new Dept(20, "Sales"),
              new Dept(15, "Marketing")));

  // 将员工和部门对象转换为字符串的函数，用于测试结果的格式化输出
  // 格式：{员工姓名, 员工部门号, 部门部门号, 部门名称}
  private static final Function2<Emp, Dept, String> EMP_DEPT_TO_STRING =
      (v0, v1) -> "{" + (v0 == null ? null : v0.name)
          + ", " + (v0 == null ? null : v0.deptno)
          + ", " + (v1 == null ? null : v1.deptno)
          + ", " + (v1 == null ? null : v1.name)
          + "}";

  // 判断员工和部门部门号是否相等的谓词函数，用于连接条件
  private static final Predicate2<Emp, Dept> EMP_DEPT_EQUAL_DEPTNO =
      (e, d) -> e.deptno == d.deptno;
  // 判断部门和员工部门号是否相等的谓词函数，用于连接条件（参数顺序相反）
  private static final Predicate2<Dept, Emp> DEPT_EMP_EQUAL_DEPTNO =
      (d, e) -> d.deptno == e.deptno;

  @Test void testSemiJoinEmp() {
    // 测试员工表的半连接操作
    // 半连接：只返回左表（EMPS）中与右表（DEPTS）匹配的行
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：只返回部门号为20的两个员工（Theodore和Sebastian）
    // 因为部门表中只有部门号为20的Sales部门存在匹配
    assertThat(
        EnumerableDefaults.semiJoin(EMPS, DEPTS, e -> e.deptno, d -> d.deptno,
            Functions.identityComparer()).toList(),
        hasToString("[Emp(20, Theodore), Emp(20, Sebastian)]"));
  }

  @Test void testSemiJoinDept() {
    // 测试部门表的半连接操作
    // 半连接：只返回左表（DEPTS）中与右表（EMPS）匹配的行
    // 连接条件：部门部门号 = 员工部门号
    // 预期结果：只返回部门号为20的Sales部门
    // 因为员工表中只有部门号为20、30的员工存在匹配
    assertThat(
        EnumerableDefaults.semiJoin(DEPTS, EMPS, d -> d.deptno, e -> e.deptno,
            Functions.identityComparer()).toList(),
        hasToString("[Dept(20, Sales)]"));
  }

  @Test void testAntiJoinEmp() {
    // 测试员工表的反连接操作
    // 反连接：返回左表（EMPS）中不与右表（DEPTS）匹配的行
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：返回部门号为10和30的员工（Fred和Joe）
    // 因为这两个部门号在部门表中不存在匹配
    assertThat(
        EnumerableDefaults.antiJoin(EMPS, DEPTS, e -> e.deptno, d -> d.deptno,
            Functions.identityComparer()).toList(),
        hasToString("[Emp(10, Fred), Emp(30, Joe)]"));
  }

  @Test void testAntiJoinDept() {
    // 测试部门表的反连接操作
    // 反连接：返回左表（DEPTS）中不与右表（EMPS）匹配的行
    // 连接条件：部门部门号 = 员工部门号
    // 预期结果：返回部门号为15的Marketing部门
    // 因为这个部门号在员工表中不存在匹配
    assertThat(
        EnumerableDefaults.antiJoin(DEPTS, EMPS, d -> d.deptno, e -> e.deptno,
            Functions.identityComparer()).toList(),
        hasToString("[Dept(15, Marketing)]"));
  }

  @Test void testMergeJoin() {
    // 测试合并连接操作（内连接）
    // 合并连接：基于排序的连接算法，要求两个输入集合都已按连接键排序
    // 连接类型：INNER（内连接），只返回两个表中匹配的行
    // 连接键：员工部门号 = 部门部门号
    // 结果格式化：将员工和部门对象拼接为字符串
    // 预期结果：
    // - 部门20的员工Theodore和Sebastian分别与部门20的Sales连接，产生2行
    // - 部门30的员工Joe和Greg分别与部门30的Research和Development连接，产生4行
    // - 总共6行结果
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Joe"),
                    new Emp(30, "Greg"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(15, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(30, "Research"),
                    new Dept(30, "Development"))),
            e -> e.deptno,
            d -> d.deptno,
            (v0, v1) -> v0 + ", " + v1, JoinType.INNER, null).toList(),
        hasToString("[Emp(20, Theodore), Dept(20, Sales),"
            + " Emp(20, Sebastian), Dept(20, Sales),"
            + " Emp(30, Joe), Dept(30, Research),"
            + " Emp(30, Joe), Dept(30, Development),"
            + " Emp(30, Greg), Dept(30, Research),"
            + " Emp(30, Greg), Dept(30, Development)]"));
  }

  @Test void testMergeJoinWithNullKeys() {
    // 测试包含NULL键的合并连接操作
    // 连接键：员工姓名 = 部门名称（注意：这里使用姓名作为连接键，而不是部门号）
    // NULL值处理：NULL键不会与任何值匹配（包括NULL本身）
    // 预期结果：
    // - 姓名为"Theodore"的员工(30, Theodore)与部门(30, Theodore)匹配
    // - 姓名为"Theodore"的员工(20, Theodore)与部门(30, Theodore)匹配
    // - 姓名为NULL的员工和部门不参与连接
    // - 姓名为"Fred"、"Sebastian"的员工没有匹配的部门
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(30, "Fred"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Theodore"),
                    new Emp(20, "Theodore"),
                    new Emp(40, null),
                    new Emp(30, null))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(15, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(30, "Theodore"),
                    new Dept(40, null))),
            e -> e.name,
            d -> d.name,
            (v0, v1) -> v0 + ", " + v1, JoinType.INNER, null).toList(),
        hasToString("[Emp(30, Theodore), Dept(30, Theodore),"
            + " Emp(20, Theodore), Dept(30, Theodore)]"));
  }

  @Test void testMergeJoin2() {
    // 测试多种连接类型的合并连接操作，覆盖各种边界情况
    // 测试INNER和SEMI连接类型
    final JoinType[] joinTypes = {JoinType.INNER, JoinType.SEMI};
    for (JoinType joinType : joinTypes) {
      // 测试用例1：匹配键在开始位置
      // 左表：[1, 3, 4]，右表：[1, 4]
      // INNER连接结果：[1, 4]（两个表都有的元素）
      // SEMI连接结果：[1, 4]（左表中与右表匹配的元素）
      testIntersect(
          newArrayList(1, 3, 4),
          newArrayList(1, 4),
          equalTo("[1, 4]"),
          joinType);
      // 测试用例2：匹配键在右表的开始和结束位置，但不在左表的开始和结束位置
      // 左表：[0, 1, 3, 4, 5]，右表：[1, 4]
      // INNER/SEMI连接结果：[1, 4]
      testIntersect(
          newArrayList(0, 1, 3, 4, 5),
          newArrayList(1, 4),
          equalTo("[1, 4]"),
          joinType);
      // 测试用例3：匹配键在左表的开始和结束位置，但不在右表的开始和结束位置
      // 左表：[1, 3, 4]，右表：[0, 1, 4, 5]
      // INNER/SEMI连接结果：[1, 4]
      testIntersect(
          newArrayList(1, 3, 4),
          newArrayList(0, 1, 4, 5),
          equalTo("[1, 4]"),
          joinType);
      // 测试用例4：匹配键不在左表或右表的开始或结束位置
      // 左表：[0, 2, 3, 4, 5]，右表：[1, 3, 4, 6]
      // INNER/SEMI连接结果：[3, 4]
      testIntersect(
          newArrayList(0, 2, 3, 4, 5),
          newArrayList(1, 3, 4, 6),
          equalTo("[3, 4]"),
          joinType);
      // 测试用例5：重复的匹配键
      // 左表：[1, 3, 4]，右表：[1, 1, 4, 4]
      // INNER连接结果：[1, 1, 4, 4]（笛卡尔积）
      // SEMI连接结果：[1, 4]（去重）
      testIntersect(
          newArrayList(1, 3, 4),
          newArrayList(1, 1, 4, 4),
          equalTo(joinType == JoinType.INNER ? "[1, 1, 4, 4]" : "[1, 4]"),
          joinType);
    }

    // ANTI连接测试：
    // 测试用例1：匹配键在开始位置
    // 左表：[1, 3, 4]，右表：[1, 4]
    // ANTI连接结果：[3]（左表中不与右表匹配的元素）
    testIntersect(
        newArrayList(1, 3, 4),
        newArrayList(1, 4),
        equalTo("[3]"),
        JoinType.ANTI);
    // 测试用例2：匹配键在右表的开始和结束位置
    // 左表：[0, 1, 3, 4, 5]，右表：[1, 4]
    // ANTI连接结果：[0, 3, 5]
    testIntersect(
        newArrayList(0, 1, 3, 4, 5),
        newArrayList(1, 4),
        equalTo("[0, 3, 5]"),
        JoinType.ANTI);
    // 测试用例3：匹配键在左表的开始和结束位置
    // 左表：[1, 3, 4]，右表：[0, 1, 4, 5]
    // ANTI连接结果：[3]
    testIntersect(
        newArrayList(1, 3, 4),
        newArrayList(0, 1, 4, 5),
        equalTo("[3]"),
        JoinType.ANTI);
    // 测试用例4：匹配键不在左表或右表的开始或结束位置
    // 左表：[0, 2, 3, 4, 5]，右表：[1, 3, 4, 6]
    // ANTI连接结果：[0, 2, 5]
    testIntersect(
        newArrayList(0, 2, 3, 4, 5),
        newArrayList(1, 3, 4, 6),
        equalTo("[0, 2, 5]"),
        JoinType.ANTI);
    // 测试用例5：重复的匹配键
    // 左表：[1, 3, 4]，右表：[1, 1, 4, 4]
    // ANTI连接结果：[3]
    testIntersect(
        newArrayList(1, 3, 4),
        newArrayList(1, 1, 4, 4),
        equalTo("[3]"),
        JoinType.ANTI);

    // LEFT连接测试：
    // 测试用例1：匹配键在开始位置
    // 左表：[1, 3, 4]，右表：[1, 4]
    // LEFT连接结果：[1-1, 3-null, 4-4]（左表所有元素，匹配的配对，不匹配的右表为null）
    // 加上null-null表示右表为空时的额外行
    testIntersect(
        newArrayList(1, 3, 4),
        newArrayList(1, 4),
        equalTo("[1-1, 3-null, 4-4]"),
        equalTo("[1-1, 3-null, 4-4, null-null]"),
        JoinType.LEFT);
    // 测试用例2：匹配键在右表的开始和结束位置
    // 左表：[0, 1, 3, 4, 5]，右表：[1, 4]
    // LEFT连接结果：[0-null, 1-1, 3-null, 4-4, 5-null]
    testIntersect(
        newArrayList(0, 1, 3, 4, 5),
        newArrayList(1, 4),
        equalTo("[0-null, 1-1, 3-null, 4-4, 5-null]"),
        equalTo("[0-null, 1-1, 3-null, 4-4, 5-null, null-null]"),
        JoinType.LEFT);
    // 测试用例3：匹配键在左表的开始和结束位置
    // 左表：[1, 3, 4]，右表：[0, 1, 4, 5]
    // LEFT连接结果：[1-1, 3-null, 4-4]
    testIntersect(
        newArrayList(1, 3, 4),
        newArrayList(0, 1, 4, 5),
        equalTo("[1-1, 3-null, 4-4]"),
        equalTo("[1-1, 3-null, 4-4, null-null]"),
        JoinType.LEFT);
    // 测试用例4：匹配键不在左表或右表的开始或结束位置
    // 左表：[0, 2, 3, 4, 5]，右表：[1, 3, 4, 6]
    // LEFT连接结果：[0-null, 2-null, 3-3, 4-4, 5-null]
    testIntersect(
        newArrayList(0, 2, 3, 4, 5),
        newArrayList(1, 3, 4, 6),
        equalTo("[0-null, 2-null, 3-3, 4-4, 5-null]"),
        equalTo("[0-null, 2-null, 3-3, 4-4, 5-null, null-null]"),
        JoinType.LEFT);
    // 测试用例5：重复的匹配键
    // 左表：[1, 3, 4]，右表：[1, 1, 4, 4]
    // LEFT连接结果：[1-1, 1-1, 3-null, 4-4, 4-4]（笛卡尔积）
    testIntersect(
        newArrayList(1, 3, 4),
        newArrayList(1, 1, 4, 4),
        equalTo("[1-1, 1-1, 3-null, 4-4, 4-4]"),
        equalTo("[1-1, 1-1, 3-null, 4-4, 4-4, null-null]"),
        JoinType.LEFT);
  }

  @Test void testMergeJoin3() {
    // 测试合并连接操作的边界情况：空集合和无重叠情况
    // 测试INNER和SEMI连接类型
    final JoinType[] joinTypes = {JoinType.INNER, JoinType.SEMI};
    for (JoinType joinType : joinTypes) {
      // 测试用例1：两个集合无重叠
      // 左表：[0, 2, 4]，右表：[1, 3, 5]
      // INNER/SEMI连接结果：[]（没有匹配的元素）
      testIntersect(
          Lists.newArrayList(0, 2, 4),
          Lists.newArrayList(1, 3, 5),
          equalTo("[]"),
          joinType);
      // 测试用例2：左表为空
      // 左表：[]，右表：[1, 3, 4, 6]
      // INNER/SEMI连接结果：[]
      testIntersect(
          new ArrayList<>(),
          newArrayList(1, 3, 4, 6),
          equalTo("[]"),
          joinType);
      // 测试用例3：右表为空
      // 左表：[3, 7]，右表：[]
      // INNER/SEMI连接结果：[]
      testIntersect(
          newArrayList(3, 7),
          new ArrayList<>(),
          equalTo("[]"),
          joinType);
      // 测试用例4：两个表都为空
      // 左表：[]，右表：[]
      // INNER/SEMI连接结果：[]
      testIntersect(
          new ArrayList<Integer>(),
          new ArrayList<>(),
          equalTo("[]"),
          joinType);
    }

    // ANTI连接测试：
    // 测试用例1：两个集合无重叠
    // 左表：[0, 2, 4]，右表：[1, 3, 5]
    // ANTI连接结果：[0, 2, 4]（左表所有元素都不匹配）
    testIntersect(
        newArrayList(0, 2, 4),
        newArrayList(1, 3, 5),
        equalTo("[0, 2, 4]"),
        JoinType.ANTI);
    // 测试用例2：左表为空
    // 左表：[]，右表：[1, 3, 4, 6]
    // ANTI连接结果：[]
    testIntersect(
        new ArrayList<>(),
        newArrayList(1, 3, 4, 6),
        equalTo("[]"),
        JoinType.ANTI);
    // 测试用例3：右表为空
    // 左表：[3, 7]，右表：[]
    // ANTI连接结果：[3, 7]（左表所有元素都不匹配）
    testIntersect(
        newArrayList(3, 7),
        new ArrayList<>(),
        equalTo("[3, 7]"),
        JoinType.ANTI);
    // 测试用例4：两个表都为空
    // 左表：[]，右表：[]
    // ANTI连接结果：[]
    testIntersect(
        new ArrayList<Integer>(),
        new ArrayList<>(),
        equalTo("[]"),
        JoinType.ANTI);

    // LEFT连接测试：
    // 测试用例1：两个集合无重叠
    // 左表：[0, 2, 4]，右表：[1, 3, 5]
    // LEFT连接结果：[0-null, 2-null, 4-null]（左表所有元素，右表都为null）
    testIntersect(
        newArrayList(0, 2, 4),
        newArrayList(1, 3, 5),
        equalTo("[0-null, 2-null, 4-null]"),
        equalTo("[0-null, 2-null, 4-null, null-null]"),
        JoinType.LEFT);
    // 测试用例2：左表为空
    // 左表：[]，右表：[1, 3, 4, 6]
    // LEFT连接结果：[]，加上null-null表示右表为空时的额外行
    testIntersect(
        new ArrayList<>(),
        newArrayList(1, 3, 4, 6),
        equalTo("[]"),
        equalTo("[null-null]"),
        JoinType.LEFT);
    // 测试用例3：右表为空
    // 左表：[3, 7]，右表：[]
    // LEFT连接结果：[3-null, 7-null]
    testIntersect(
        newArrayList(3, 7),
        new ArrayList<>(),
        equalTo("[3-null, 7-null]"),
        equalTo("[3-null, 7-null, null-null]"),
        JoinType.LEFT);
    // 测试用例4：两个表都为空
    // 左表：[]，右表：[]
    // LEFT连接结果：[]，加上null-null
    testIntersect(
        new ArrayList<Integer>(),
        new ArrayList<>(),
        equalTo("[]"),
        equalTo("[null-null]"),
        JoinType.LEFT);
  }

  // 测试连接操作的辅助方法（不带NULL测试）
// 参数说明：
// - list0: 左表数据
// - list1: 右表数据
// - matcher: 期望结果的匹配器
// - joinType: 连接类型（INNER/SEMI/ANTI/LEFT）
private static <T extends Comparable<T>> void testIntersect(
      List<T> list0, List<T> list1, org.hamcrest.Matcher<String> matcher, JoinType joinType) {
    // 调用重载方法，使用相同的matcher处理普通情况和NULL情况
    testIntersect(list0, list1, matcher, matcher, joinType);
  }

  // 测试连接操作的辅助方法（带NULL测试）
// 参数说明：
// - list0: 左表数据
// - list1: 右表数据
// - matcher: 普通情况下期望结果的匹配器
// - matcherNullLeft: 左表包含NULL时期望结果的匹配器
// - joinType: 连接类型（INNER/SEMI/ANTI/LEFT）
// 测试场景：
// 1. 普通情况：两个表都不包含NULL
// 2. 左表包含NULL：在左表末尾添加NULL
// 3. 右表包含NULL：在右表末尾添加NULL
// 4. 两个表都包含NULL：在两个表末尾都添加NULL
private static <T extends Comparable<T>> void testIntersect(
      List<T> list0, List<T> list1, org.hamcrest.Matcher<String> matcher,
      org.hamcrest.Matcher<String> matcherNullLeft, JoinType joinType) {
    // 测试普通情况：两个表都不包含NULL
    assertThat(intersect(list0, list1, joinType).toList(),
        hasToString(matcher));

    // 重复测试，在左表或右表的末尾添加NULL

    // 测试用例：左表末尾包含NULL
    // 在左表末尾添加NULL元素
    list0.add(null);
    // 验证结果是否符合预期
    assertThat(intersect(list0, list1, joinType).toList(),
        hasToString(matcherNullLeft));

    // 测试用例：右表末尾包含NULL
    // 移除左表的NULL
    list0.remove(list0.size() - 1);
    // 在右表末尾添加NULL元素
    list1.add(null);
    // 验证结果是否符合预期
    assertThat(intersect(list0, list1, joinType).toList(),
        hasToString(matcher));

    // 测试用例：左表和右表末尾都包含NULL
    // 在左表末尾添加NULL元素
    list0.add(null);
    // 验证结果是否符合预期
    assertThat(intersect(list0, list1, joinType).toList(),
        hasToString(matcherNullLeft));
  }

  // 执行连接操作的辅助方法
// 参数说明：
// - list0: 左表数据
// - list1: 右表数据
// - joinType: 连接类型（INNER/SEMI/ANTI/LEFT）
// 返回值：连接结果的字符串表示
// 实现说明：
// - 对于LEFT连接：结果格式为"左值-右值"
// - 对于其他连接类型：结果格式为"左值"
private static <T extends Comparable<T>> Enumerable<String> intersect(
      List<T> list0, List<T> list1, JoinType joinType) {
    // 如果是LEFT连接，需要输出左值和右值
    if (joinType == JoinType.LEFT) {
      return EnumerableDefaults.mergeJoin(
          Linq4j.asEnumerable(list0),
          Linq4j.asEnumerable(list1),
          Functions.identitySelector(),  // 左键：元素本身
          Functions.identitySelector(),  // 右键：元素本身
          (v0, v1) -> String.valueOf(v0) + "-" + String.valueOf(v1),  // 结果格式：左值-右值
          JoinType.LEFT,
          null);
    }
    // 对于INNER/SEMI/ANTI连接，只输出左值
    return EnumerableDefaults.mergeJoin(
        Linq4j.asEnumerable(list0),
        Linq4j.asEnumerable(list1),
        Functions.identitySelector(),  // 左键：元素本身
        Functions.identitySelector(),  // 右键：元素本身
        (v0, v1) -> String.valueOf(v0),  // 结果格式：左值
        joinType,
        null);
  }

  @Test void testMergeJoinWithPredicate() {
    // 测试带谓词条件的合并连接操作
    // 谓词条件：在连接键匹配的基础上，还需要满足额外的条件
    // 创建测试数据：两个员工列表
    final List<Emp> listEmp1 =
        Arrays.asList(new Emp(1, "Fred"),
            new Emp(2, "Fred"),
            new Emp(3, "Joe"),
            new Emp(4, "Joe"),
            new Emp(5, "Peter"));
    final List<Emp> listEmp2 =
        Arrays.asList(new Emp(2, "Fred"),
            new Emp(3, "Fred"),
            new Emp(3, "Joe"),
            new Emp(5, "Joe"),
            new Emp(6, "Peter"));

    // 测试用例1：左表deptno < 右表deptno
    // 连接键：姓名
    // 谓词：e1.deptno < e2.deptno
    // 预期结果：
    // - Fred: (1,2), (1,3), (2,3) - 3对
    // - Joe: (3,5), (4,5) - 2对
    // - Peter: (5,6) - 1对
    // 总共6对
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(listEmp1),
            Linq4j.asEnumerable(listEmp2),
            e1 -> e1.name,
            e2 -> e2.name,
            (e1, e2) -> e1.deptno < e2.deptno,  // 谓词条件
            (v0, v1) -> v0 + "-" + v1, JoinType.INNER, null, null).toList(),
        hasToString("["
            + "Emp(1, Fred)-Emp(2, Fred), "
            + "Emp(1, Fred)-Emp(3, Fred), "
            + "Emp(2, Fred)-Emp(3, Fred), "
            + "Emp(3, Joe)-Emp(5, Joe), "
            + "Emp(4, Joe)-Emp(5, Joe), "
            + "Emp(5, Peter)-Emp(6, Peter)]"));

    // 测试用例2：左表deptno > 右表deptno（交换表的顺序）
    // 连接键：姓名
    // 谓词：e2.deptno > e1.deptno
    // 预期结果与测试用例1相同，只是顺序相反
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(listEmp2),
            Linq4j.asEnumerable(listEmp1),
            e2 -> e2.name,
            e1 -> e1.name,
            (e2, e1) -> e2.deptno > e1.deptno,  // 谓词条件
            (v0, v1) -> v0 + "-" + v1, JoinType.INNER, null, null).toList(),
        hasToString("["
            + "Emp(2, Fred)-Emp(1, Fred), "
            + "Emp(3, Fred)-Emp(1, Fred), "
            + "Emp(3, Fred)-Emp(2, Fred), "
            + "Emp(5, Joe)-Emp(3, Joe), "
            + "Emp(5, Joe)-Emp(4, Joe), "
            + "Emp(6, Peter)-Emp(5, Peter)]"));

    // 测试用例3：左表deptno == 右表deptno * 2
    // 连接键：姓名
    // 谓词：e1.deptno == e2.deptno * 2
    // 预期结果：空（没有满足条件的对）
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(listEmp1),
            Linq4j.asEnumerable(listEmp2),
            e1 -> e1.name,
            e2 -> e2.name,
            (e1, e2) -> e1.deptno == e2.deptno * 2,  // 谓词条件
            (v0, v1) -> v0 + "-" + v1, JoinType.INNER, null, null).toList(),
        hasToString("[]"));

    // 测试用例4：右表deptno == 左表deptno * 2
    // 连接键：姓名
    // 谓词：e2.deptno == e1.deptno * 2
    // 预期结果：只有Emp(2, Fred)-Emp(1, Fred)满足条件（2 == 1*2）
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(listEmp2),
            Linq4j.asEnumerable(listEmp1),
            e2 -> e2.name,
            e1 -> e1.name,
            (e2, e1) -> e2.deptno == e1.deptno * 2,  // 谓词条件
            (v0, v1) -> v0 + "-" + v1, JoinType.INNER, null, null).toList(),
        hasToString("[Emp(2, Fred)-Emp(1, Fred)]"));

    // 测试用例5：右表deptno == 左表deptno + 2
    // 连接键：姓名
    // 谓词：e2.deptno == e1.deptno + 2
    // 预期结果：
    // - Emp(3, Fred)-Emp(1, Fred) 满足条件（3 == 1+2）
    // - Emp(5, Joe)-Emp(3, Joe) 满足条件（5 == 3+2）
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(listEmp2),
            Linq4j.asEnumerable(listEmp1),
            e2 -> e2.name,
            e1 -> e1.name,
            (e2, e1) -> e2.deptno == e1.deptno + 2,  // 谓词条件
            (v0, v1) -> v0 + "-" + v1, JoinType.INNER, null, null).toList(),
        hasToString("[Emp(3, Fred)-Emp(1, Fred), Emp(5, Joe)-Emp(3, Joe)]"));
  }

  @Test void testMergeSemiJoin() {
    // 测试合并半连接操作
    // 半连接：只返回左表中与右表匹配的行
    // 左表：部门表，包含5个部门
    // 右表：员工表，包含6个员工
    // 连接键：部门号
    // 预期结果：
    // - 部门10(Marketing)匹配员工10(Fred) -> 保留
    // - 部门20(Sales)匹配员工20(Theodore)和20(Sebastian) -> 保留
    // - 部门25(HR)没有匹配的员工 -> 不保留
    // - 部门30(Research)匹配员工30(Joe)和30(Greg) -> 保留
    // - 部门40(Development)没有匹配的员工 -> 不保留
    // 最终结果：[Dept(10, Marketing), Dept(20, Sales), Dept(30, Research)]
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(10, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(25, "HR"),
                    new Dept(30, "Research"),
                    new Dept(40, "Development"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Joe"),
                    new Emp(30, "Greg"),
                    new Emp(50, "Mary"))),
            d -> d.deptno,
            e -> e.deptno,
            null,  // 没有额外的谓词条件
            (v0, v1) -> v0,  // 只返回左表的部门对象
            JoinType.SEMI,
            null, null).toList(),
        hasToString("[Dept(10, Marketing),"
            + " Dept(20, Sales),"
            + " Dept(30, Research)]"));
  }

  @Test void testMergeSemiJoinWithPredicate() {
    // 测试带谓词条件的合并半连接操作
    // 半连接：只返回左表中与右表匹配且满足谓词条件的行
    // 左表：部门表
    // 右表：员工表
    // 连接键：部门号
    // 谓词条件：员工姓名包含字母"a"
    // 预期结果：
    // - 部门10(Marketing)：匹配员工10(Fred)，但Fred不包含"a" -> 不保留
    // - 部门20(Sales)：匹配员工20(Theodore)和20(Sebastian)，Sebastian包含"a" -> 保留
    // - 部门25(HR)：没有匹配的员工 -> 不保留
    // - 部门30(Research)：匹配员工30(Joe)和30(Greg)，都不包含"a" -> 不保留
    // - 部门40(Development)：没有匹配的员工 -> 不保留
    // 最终结果：[Dept(20, Sales)]
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(10, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(25, "HR"),
                    new Dept(30, "Research"),
                    new Dept(40, "Development"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Joe"),
                    new Emp(30, "Greg"),
                    new Emp(50, "Mary"))),
            d -> d.deptno,
            e -> e.deptno,
            (d, e) -> e.name.contains("a"),  // 谓词条件：员工姓名包含"a"
            (v0, v1) -> v0,  // 只返回左表的部门对象
            JoinType.SEMI,
            null, null).toList(),
        hasToString("[Dept(20, Sales)]"));
  }

  @Test void testMergeSemiJoinWithNullKeys() {
    // 测试包含NULL键的合并半连接操作
    // 半连接：只返回左表中与右表匹配且满足谓词条件的行
    // 左表：员工表（包含NULL姓名）
    // 右表：部门表（包含NULL名称）
    // 连接键：员工姓名 = 部门名称
    // 谓词条件：员工姓名以"T"开头
    // 预期结果：
    // - 员工(30, "Fred")：没有匹配的部门 -> 不保留
    // - 员工(20, "Sebastian")：没有匹配的部门 -> 不保留
    // - 员工(30, "Theodore")：匹配部门(30, "Theodore")和(25, "Theodore")，且以"T"开头 -> 保留
    // - 员工(20, "Zoey")：没有匹配的部门（部门33是"Zoey"但部门号不匹配） -> 不保留
    // - 员工(40, null)：NULL键不匹配任何值 -> 不保留
    // - 员工(30, null)：NULL键不匹配任何值 -> 不保留
    // 最终结果：[Emp(30, Theodore)]
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(30, "Fred"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Theodore"),
                    new Emp(20, "Zoey"),
                    new Emp(40, null),
                    new Emp(30, null))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(15, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(30, "Theodore"),
                    new Dept(25, "Theodore"),
                    new Dept(33, "Zoey"),
                    new Dept(40, null))),
            e -> e.name,
            d -> d.name,
            (e, d) -> e.name.startsWith("T"),  // 谓词条件：员工姓名以"T"开头
            (v0, v1) -> v0,  // 只返回左表的员工对象
            JoinType.SEMI,
            null, null).toList(),
        hasToString("[Emp(30, Theodore)]"));
  }


  @Test void testMergeAntiJoin() {
    // 测试合并反连接操作
    // 反连接：返回左表中不与右表匹配的行
    // 左表：部门表
    // 右表：员工表
    // 连接键：部门号
    // 预期结果：
    // - 部门10(Marketing)：匹配员工10(Fred) -> 不保留
    // - 部门20(Sales)：匹配员工20(Theodore)和20(Sebastian) -> 不保留
    // - 部门25(HR)：没有匹配的员工 -> 保留
    // - 部门30(Research)：匹配员工30(Joe)和30(Greg) -> 不保留
    // - 部门40(Development)：没有匹配的员工 -> 保留
    // 最终结果：[Dept(25, HR), Dept(40, Development)]
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                new Dept(10, "Marketing"),
                new Dept(20, "Sales"),
                new Dept(25, "HR"),
                new Dept(30, "Research"),
                new Dept(40, "Development"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                new Emp(10, "Fred"),
                new Emp(20, "Theodore"),
                new Emp(20, "Sebastian"),
                new Emp(30, "Joe"),
                new Emp(30, "Greg"),
                new Emp(50, "Mary"))),
            d -> Integer.valueOf(d.deptno),
            e -> Integer.valueOf(e.deptno),
            null,  // 没有额外的谓词条件
            (v0, v1) -> v0,  // 只返回左表的部门对象
            JoinType.ANTI,
            null, null).toList(),
        hasToString("[Dept(25, HR), Dept(40, Development)]"));
  }

  @Test void testMergeAntiJoinWithPredicate() {
    // 测试带谓词条件的合并反连接操作
    // 反连接：返回左表中不与右表匹配或匹配但不满足谓词条件的行
    // 左表：部门表
    // 右表：员工表
    // 连接键：部门号
    // 谓词条件：员工姓名以"F"或"S"开头
    // 预期结果：
    // - 部门10(Marketing)：匹配员工10(Fred)，Fred以"F"开头 -> 不保留
    // - 部门20(Sales)：匹配员工20(Theodore)和20(Sebastian)，Sebastian以"S"开头 -> 不保留
    // - 部门25(HR)：没有匹配的员工 -> 保留
    // - 部门30(Research)：匹配员工30(Joe)和30(Greg)，都不以"F"或"S"开头 -> 保留
    // - 部门40(Development)：没有匹配的员工 -> 保留
    // 最终结果：[Dept(25, HR), Dept(30, Research), Dept(40, Development)]
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                new Dept(10, "Marketing"),
                new Dept(20, "Sales"),
                new Dept(25, "HR"),
                new Dept(30, "Research"),
                new Dept(40, "Development"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                new Emp(10, "Fred"),
                new Emp(20, "Theodore"),
                new Emp(20, "Sebastian"),
                new Emp(30, "Joe"),
                new Emp(30, "Greg"),
                new Emp(50, "Mary"))),
            d -> d.deptno,
            e -> e.deptno,
            (d, e) -> e.name.startsWith("F") || e.name.startsWith("S"),  // 谓词条件
            (v0, v1) -> v0,  // 只返回左表的部门对象
            JoinType.ANTI,
            null, null).toList(),
        hasToString("[Dept(25, HR), Dept(30, Research), "
            + "Dept(40, Development)]"));
  }

  @Test void testMergeAntiJoinWithNullKeys() {
    // 测试包含NULL键的合并反连接操作
    // 反连接：返回左表中不与右表匹配或匹配但不满足谓词条件的行
    // 左表：员工表（包含NULL姓名）
    // 右表：部门表（包含NULL名称）
    // 连接键：员工姓名 = 部门名称
    // 谓词条件：部门号 < 30
    // 预期结果：
    // - 员工(30, "Fred")：没有匹配的部门 -> 保留
    // - 员工(20, "Sebastian")：没有匹配的部门 -> 保留
    // - 员工(30, "Theodore")：匹配部门(30, "Theodore")和(25, "Theodore")，部门25满足deptno<30 -> 不保留
    // - 员工(20, "Zoey")：没有匹配的部门（部门33是"Zoey"但部门号不匹配） -> 保留
    // - 员工(40, null)：NULL键不匹配任何值 -> 不保留（因为NULL在反连接中被过滤）
    // - 员工(30, null)：NULL键不匹配任何值 -> 不保留
    // 最终结果：[Emp(30, Fred), Emp(20, Sebastian), Emp(20, Zoey)]
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                new Emp(30, "Fred"),
                new Emp(20, "Sebastian"),
                new Emp(30, "Theodore"),
                new Emp(20, "Zoey"),
                new Emp(40, null),
                new Emp(30, null))),
            Linq4j.asEnumerable(
                Arrays.asList(
                new Dept(15, "Marketing"),
                new Dept(20, "Sales"),
                new Dept(30, "Theodore"),
                new Dept(25, "Theodore"),
                new Dept(33, "Zoey"),
                new Dept(40, null))),
            e -> e.name,
            d -> d.name,
            (e, d) -> d.deptno < 30,  // 谓词条件：部门号小于30
            (v0, v1) -> v0,  // 只返回左表的员工对象
            JoinType.ANTI,
            null, null).toList(),
        hasToString("[Emp(30, Fred), Emp(20, Sebastian), Emp(20, Zoey)]"));
  }

  @Test void testMergeLeftJoin() {
    // 测试合并左连接操作
    // 左连接：返回左表所有行，右表匹配的行，不匹配的右表为null
    // 左表：部门表
    // 右表：员工表
    // 连接键：部门号
    // 预期结果：
    // - 部门10(Marketing)：匹配员工10(Fred) -> Dept(10, Marketing)-Emp(10, Fred)
    // - 部门20(Sales)：匹配员工20(Theodore)和20(Sebastian) -> 2行
    // - 部门25(HR)：没有匹配的员工 -> Dept(25, HR)-null
    // - 部门30(Research)：匹配员工30(Joe)和30(Greg) -> 2行
    // - 部门40(Development)：没有匹配的员工 -> Dept(40, Development)-null
    // 最终结果：7行（包括2行null）
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(10, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(25, "HR"),
                    new Dept(30, "Research"),
                    new Dept(40, "Development"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Joe"),
                    new Emp(30, "Greg"),
                    new Emp(50, "Mary"))),
            d -> d.deptno,
            e -> e.deptno,
            null,  // 没有额外的谓词条件
            (v0, v1) -> v0 + "-" + v1,  // 结果格式：部门-员工
            JoinType.LEFT,
            null, null).toList(),
        hasToString("[Dept(10, Marketing)-Emp(10, Fred),"
            + " Dept(20, Sales)-Emp(20, Theodore),"
            + " Dept(20, Sales)-Emp(20, Sebastian),"
            + " Dept(25, HR)-null,"
            + " Dept(30, Research)-Emp(30, Joe),"
            + " Dept(30, Research)-Emp(30, Greg),"
            + " Dept(40, Development)-null]"));
  }

  @Test void testMergeLeftJoinWithPredicate() {
    // 测试带谓词条件的合并左连接操作
    // 左连接：返回左表所有行，右表匹配且满足谓词条件的行，不匹配或不满足条件的右表为null
    // 左表：部门表
    // 右表：员工表
    // 连接键：部门号
    // 谓词条件：员工姓名包含字母"a"
    // 预期结果：
    // - 部门10(Marketing)：匹配员工10(Fred)，但Fred不包含"a" -> Dept(10, Marketing)-null
    // - 部门20(Sales)：匹配员工20(Theodore)和20(Sebastian)，只有Sebastian包含"a" -> Dept(20, Sales)-Emp(20, Sebastian)
    // - 部门25(HR)：没有匹配的员工 -> Dept(25, HR)-null
    // - 部门30(Research)：匹配员工30(Joe)和30(Greg)，都不包含"a" -> Dept(30, Research)-null
    // - 部门40(Development)：没有匹配的员工 -> Dept(40, Development)-null
    // 最终结果：5行（包括4行null）
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(10, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(25, "HR"),
                    new Dept(30, "Research"),
                    new Dept(40, "Development"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Joe"),
                    new Emp(30, "Greg"),
                    new Emp(50, "Mary"))),
            d -> d.deptno,
            e -> e.deptno,
            (d, e) -> e.name.contains("a"),  // 谓词条件：员工姓名包含"a"
            (v0, v1) -> v0 + "-" + v1,  // 结果格式：部门-员工
            JoinType.LEFT,
            null, null).toList(),
        hasToString("[Dept(10, Marketing)-null,"
            + " Dept(20, Sales)-Emp(20, Sebastian),"
            + " Dept(25, HR)-null,"
            + " Dept(30, Research)-null,"
            + " Dept(40, Development)-null]"));
  }

  @Test void testMergeLeftJoinWithNullKeys() {
    // 测试包含NULL键的合并左连接操作
    // 左连接：返回左表所有行，右表匹配且满足谓词条件的行，不匹配或不满足条件的右表为null
    // 左表：员工表（包含NULL姓名）
    // 右表：部门表（包含NULL名称）
    // 连接键：员工姓名 = 部门名称
    // 谓词条件：员工姓名以"T"开头
    // 预期结果：
    // - 员工(30, "Fred")：没有匹配的部门 -> Emp(30, Fred)-null
    // - 员工(20, "Sebastian")：没有匹配的部门 -> Emp(20, Sebastian)-null
    // - 员工(30, "Theodore")：匹配部门(30, "Theodore")和(25, "Theodore")，且以"T"开头 -> 2行
    // - 员工(20, "Zoey")：没有匹配的部门 -> Emp(20, Zoey)-null
    // - 员工(40, null)：NULL键不匹配任何值 -> Emp(40, null)-null
    // - 员工(30, null)：NULL键不匹配任何值 -> Emp(30, null)-null
    // 最终结果：7行（包括5行null）
    assertThat(
        EnumerableDefaults.mergeJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(30, "Fred"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Theodore"),
                    new Emp(20, "Zoey"),
                    new Emp(40, null),
                    new Emp(30, null))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(15, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(30, "Theodore"),
                    new Dept(25, "Theodore"),
                    new Dept(33, "Zoey"),
                    new Dept(40, null))),
            e -> e.name,
            d -> d.name,
            (e, d) -> e.name.startsWith("T"),  // 谓词条件：员工姓名以"T"开头
            (v0, v1) -> v0 + "-" + v1,  // 结果格式：员工-部门
            JoinType.LEFT,
            null, null).toList(),
        hasToString("[Emp(30, Fred)-null,"
            + " Emp(20, Sebastian)-null,"
            + " Emp(30, Theodore)-Dept(30, Theodore),"
            + " Emp(30, Theodore)-Dept(25, Theodore),"
            + " Emp(20, Zoey)-null,"
            + " Emp(40, null)-null,"
            + " Emp(30, null)-null]"));
  }

  @Test void testNestedLoopJoin() {
    // 测试嵌套循环连接操作（内连接）
    // 嵌套循环连接：通过双重循环遍历左表和右表，找出满足连接条件的行
    // 左表：员工表（4个员工）
    // 右表：部门表（2个部门）
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：
    // - 员工10(Fred)：部门号10，没有匹配的部门 -> 不输出
    // - 员工20(Theodore)：部门号20，匹配部门20(Sales) -> {Theodore, 20, 20, Sales}
    // - 员工20(Sebastian)：部门号20，匹配部门20(Sales) -> {Sebastian, 20, 20, Sales}
    // - 员工30(Joe)：部门号30，没有匹配的部门 -> 不输出
    // 最终结果：2行
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS, DEPTS, EMP_DEPT_EQUAL_DEPTNO,
            EMP_DEPT_TO_STRING, JoinType.INNER).toList(),
        hasToString("[{Theodore, 20, 20, Sales}, {Sebastian, 20, 20, Sales}]"));
  }

  @Test void testNestedLoopLeftJoin() {
    // 测试嵌套循环连接操作（左连接）
    // 左连接：返回左表所有行，右表匹配的行，不匹配的右表为null
    // 左表：员工表（4个员工）
    // 右表：部门表（2个部门）
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：
    // - 员工10(Fred)：部门号10，没有匹配的部门 -> {Fred, 10, null, null}
    // - 员工20(Theodore)：部门号20，匹配部门20(Sales) -> {Theodore, 20, 20, Sales}
    // - 员工20(Sebastian)：部门号20，匹配部门20(Sales) -> {Sebastian, 20, 20, Sales}
    // - 员工30(Joe)：部门号30，没有匹配的部门 -> {Joe, 30, null, null}
    // 最终结果：4行（包括2行null）
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS, DEPTS, EMP_DEPT_EQUAL_DEPTNO,
            EMP_DEPT_TO_STRING, JoinType.LEFT).toList(),
        hasToString("[{Fred, 10, null, null}, {Theodore, 20, 20, Sales}, "
            + "{Sebastian, 20, 20, Sales}, {Joe, 30, null, null}]"));
  }

  @Test void testNestedLoopRightJoin() {
    // 测试嵌套循环连接操作（右连接）
    // 右连接：返回右表所有行，左表匹配的行，不匹配的左表为null
    // 左表：员工表（4个员工）
    // 右表：部门表（2个部门）
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：
    // - 部门20(Sales)：匹配员工20(Theodore)和20(Sebastian) -> 2行
    // - 部门15(Marketing)：没有匹配的员工 -> {null, null, 15, Marketing}
    // 最终结果：3行（包括1行null）
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS, DEPTS, EMP_DEPT_EQUAL_DEPTNO,
            EMP_DEPT_TO_STRING, JoinType.RIGHT).toList(),
        hasToString("[{Theodore, 20, 20, Sales}, {Sebastian, 20, 20, Sales}, "
            + "{null, null, 15, Marketing}]"));
  }

  @Test void testNestedLoopFullJoin() {
    // 测试嵌套循环连接操作（全连接）
    // 全连接：返回左表和右表所有行，匹配的配对，不匹配的为null
    // 左表：员工表（4个员工）
    // 右表：部门表（2个部门）
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：
    // - 员工10(Fred)：部门号10，没有匹配的部门 -> {Fred, 10, null, null}
    // - 员工20(Theodore)：部门号20，匹配部门20(Sales) -> {Theodore, 20, 20, Sales}
    // - 员工20(Sebastian)：部门号20，匹配部门20(Sales) -> {Sebastian, 20, 20, Sales}
    // - 员工30(Joe)：部门号30，没有匹配的部门 -> {Joe, 30, null, null}
    // - 部门15(Marketing)：没有匹配的员工 -> {null, null, 15, Marketing}
    // 最终结果：5行（包括3行null）
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS, DEPTS, EMP_DEPT_EQUAL_DEPTNO,
            EMP_DEPT_TO_STRING, JoinType.FULL).toList(),
        hasToString("[{Fred, 10, null, null}, {Theodore, 20, 20, Sales}, "
            + "{Sebastian, 20, 20, Sales}, {Joe, 30, null, null}, "
            + "{null, null, 15, Marketing}]"));
  }

  @Test void testNestedLoopFullJoinLeftEmpty() {
    // 测试嵌套循环连接操作（全连接，左表为空）
    // 左表：空员工表
    // 右表：部门表（2个部门）
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：右表所有行，左表为null
    // 最终结果：2行，都是null-部门
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS.take(0), DEPTS,
                EMP_DEPT_EQUAL_DEPTNO, EMP_DEPT_TO_STRING, JoinType.FULL)
            .orderBy(Functions.identitySelector()).toList(),
        hasToString("[{null, null, 15, Marketing}, {null, null, 20, Sales}]"));
  }

  @Test void testNestedLoopFullJoinRightEmpty() {
    // 测试嵌套循环连接操作（全连接，右表为空）
    // 左表：员工表（4个员工）
    // 右表：空部门表
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：左表所有行，右表为null
    // 最终结果：4行，都是员工-null
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS, DEPTS.take(0),
            EMP_DEPT_EQUAL_DEPTNO, EMP_DEPT_TO_STRING, JoinType.FULL).toList(),
        hasToString("[{Fred, 10, null, null}, {Theodore, 20, null, null}, "
            + "{Sebastian, 20, null, null}, {Joe, 30, null, null}]"));
  }

  @Test void testNestedLoopFullJoinBothEmpty() {
    // 测试嵌套循环连接操作（全连接，两个表都为空）
    // 左表：空员工表
    // 右表：空部门表
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：空结果
    // 最终结果：0行
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS.take(0), DEPTS.take(0),
            EMP_DEPT_EQUAL_DEPTNO, EMP_DEPT_TO_STRING, JoinType.FULL).toList(),
        hasToString("[]"));
  }

  @Test void testNestedLoopSemiJoinEmp() {
    // 测试嵌套循环连接操作（半连接，员工表）
    // 半连接：只返回左表中与右表匹配的行
    // 左表：员工表（4个员工）
    // 右表：部门表（2个部门）
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：只返回部门号为20的员工
    // 最终结果：[Emp(20, Theodore), Emp(20, Sebastian)]
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS, DEPTS, EMP_DEPT_EQUAL_DEPTNO,
            (e, d) -> e.toString(), JoinType.SEMI).toList(),
        hasToString("[Emp(20, Theodore), Emp(20, Sebastian)]"));
  }

  @Test void testNestedLoopSemiJoinDept() {
    // 测试嵌套循环连接操作（半连接，部门表）
    // 半连接：只返回左表中与右表匹配的行
    // 左表：部门表（2个部门）
    // 右表：员工表（4个员工）
    // 连接条件：部门部门号 = 员工部门号
    // 预期结果：只返回部门号为20的部门
    // 最终结果：[Dept(20, Sales)]
    assertThat(
        EnumerableDefaults.nestedLoopJoin(DEPTS, EMPS, DEPT_EMP_EQUAL_DEPTNO,
            (d, e) -> d.toString(), JoinType.SEMI).toList(),
        hasToString("[Dept(20, Sales)]"));
  }

  @Test void testNestedLoopAntiJoinEmp() {
    // 测试嵌套循环连接操作（反连接，员工表）
    // 反连接：返回左表中不与右表匹配的行
    // 左表：员工表（4个员工）
    // 右表：部门表（2个部门）
    // 连接条件：员工部门号 = 部门部门号
    // 预期结果：只返回部门号不为20的员工
    // 最终结果：[Emp(10, Fred), Emp(30, Joe)]
    assertThat(
        EnumerableDefaults.nestedLoopJoin(EMPS, DEPTS, EMP_DEPT_EQUAL_DEPTNO,
            (e, d) -> e.toString(), JoinType.ANTI).toList(),
        hasToString("[Emp(10, Fred), Emp(30, Joe)]"));
  }

  @Test void testNestedLoopAntiJoinDept() {
    // 测试嵌套循环连接操作（反连接，部门表）
    // 反连接：返回左表中不与右表匹配的行
    // 左表：部门表（2个部门）
    // 右表：员工表（4个员工）
    // 连接条件：部门部门号 = 员工部门号
    // 预期结果：只返回部门号不为20的部门
    // 最终结果：[Dept(15, Marketing)]
    assertThat(
        EnumerableDefaults.nestedLoopJoin(DEPTS, EMPS, DEPT_EMP_EQUAL_DEPTNO,
            (d, e) -> d.toString(), JoinType.ANTI).toList(),
        hasToString("[Dept(15, Marketing)]"));
  }

  @Test @Disabled // TODO fix this
  // 测试模式匹配操作（已禁用，需要修复）
  // 模式匹配：在数据流中查找符合特定模式的序列
  // 这是一个高级功能，用于识别数据中的模式
  public void testMatch() {
    // 创建员工集合，包含4个员工
    final Enumerable<Emp> emps =
        Linq4j.asEnumerable(
            Arrays.asList(new Emp(20, "Theodore"),
                new Emp(10, "Fred"),
                new Emp(20, "Sebastian"),
                new Emp(30, "Joe")));

    // 创建模式：A后面跟着B（序列模式）
    // 这个模式表示查找连续的两个元素，第一个是A类型，第二个是B类型
    final Pattern p =
        Pattern.builder()
            .symbol("A")  // 第一个符号：A
            .symbol("B").seq()  // 第二个符号：B，与A构成序列
            .build();

    // 创建匹配器，定义A和B的匹配条件
    // A类型：部门号为20的员工
    // B类型：部门号不为20的员工
    final Matcher<Emp> matcher =
        Matcher.<Emp>builder(p.toAutomaton())
            .add("A", s -> s.get().deptno == 20)  // A的匹配条件：部门号等于20
            .add("B", s -> s.get().deptno != 20)  // B的匹配条件：部门号不等于20
            .build();

    // 创建发射器，用于输出匹配的结果
    // 当找到匹配的模式时，发射器会生成输出
    final Enumerables.Emitter<Emp, String> emitter =
        (rows, rowStates, rowSymbols, match, consumer) -> {
          // 遍历匹配的行
          for (int i = 0; i < rows.size(); i++) {
            // 如果行符号为null，跳过
            if (rowSymbols == null) {
              continue;
            }
            // 如果当前行是A类型，输出匹配结果
            if ("A".equals(rowSymbols.get(i))) {
              consumer.accept(
                  String.format(Locale.ENGLISH, "%s %s %d", rows, rowStates,
                      match));  // 输出格式：行列表 行状态 匹配编号
            }
          }
        };

    // 执行模式匹配
    // 参数：员工集合、分区函数（emp -> 0L，表示所有数据在一个分区）、匹配器、发射器、最小匹配长度、最大匹配长度
    final Enumerable<String> matches =
        Enumerables.match(emps, emp -> 0L, matcher, emitter, 1, 1);
    // 验证匹配结果
    // 预期结果：
    // - 第一个匹配：[Emp(20, Theodore), Emp(10, Fred)]，Theodore是A（部门号20），Fred是B（部门号10）
    // - 第二个匹配：[Emp(20, Sebastian), Emp(30, Joe)]，Sebastian是A（部门号20），Joe是B（部门号30）
    assertThat(matches.toList(),
        hasToString("[[Emp(20, Theodore), Emp(10, Fred)] null 1, "
            + "[Emp(20, Sebastian), Emp(30, Joe)] null 2]"));
  }

  @Test void testInnerHashJoin() {
    // 测试哈希连接操作（内连接）
    // 哈希连接：将右表构建为哈希表，然后遍历左表查找匹配的行
    // 左表：员工表（5个员工）
    // 右表：部门表（4个部门）
    // 连接键：员工部门号 = 部门部门号
    // 预期结果：
    // - 员工10(Fred)：部门号10，没有匹配的部门 -> 不输出
    // - 员工20(Theodore)：部门号20，匹配部门20(Sales) -> 1行
    // - 员工20(Sebastian)：部门号20，匹配部门20(Sales) -> 1行
    // - 员工30(Joe)：部门号30，匹配部门30(Research)和30(Development) -> 2行
    // - 员工30(Greg)：部门号30，匹配部门30(Research)和30(Development) -> 2行
    // 最终结果：6行
    assertThat(
        EnumerableDefaults.hashJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Joe"),
                    new Emp(30, "Greg"))),
            Linq4j.asEnumerable(
                Arrays.asList(new Dept(15, "Marketing"), new Dept(20, "Sales"),
                    new Dept(30, "Research"), new Dept(30, "Development"))),
            e -> e.deptno,
            d -> d.deptno,
            (v0, v1) -> v0 + ", " + v1, null)
            .toList(),
        hasToString("[Emp(20, Theodore), Dept(20, Sales),"
            + " Emp(20, Sebastian), Dept(20, Sales),"
            + " Emp(30, Joe), Dept(30, Research),"
            + " Emp(30, Joe), Dept(30, Development),"
            + " Emp(30, Greg), Dept(30, Research),"
            + " Emp(30, Greg), Dept(30, Development)]"));
  }

  @Test void testLeftHashJoinWithNonEquiConditions() {
    // 测试哈希连接操作（左连接，带非等值条件）
    // 左连接：返回左表所有行，右表匹配且满足非等值条件的行，不匹配或不满足条件的右表为null
    // 左表：员工表（5个员工）
    // 右表：部门表（4个部门）
    // 连接键：员工部门号 = 部门部门号
    // 非等值条件：员工部门号 < 30
    // 预期结果：
    // - 员工10(Fred)：部门号10，没有匹配的部门 -> Emp(10, Fred), null
    // - 员工20(Theodore)：部门号20，匹配部门20(Sales)，且20<30 -> Emp(20, Theodore), Dept(20, Sales)
    // - 员工20(Sebastian)：部门号20，匹配部门20(Sales)，且20<30 -> Emp(20, Sebastian), Dept(20, Sales)
    // - 员工30(Joe)：部门号30，匹配部门30(Research)和30(Development)，但30<30不成立 -> Emp(30, Joe), null
    // - 员工30(Greg)：部门号30，匹配部门30(Research)和30(Development)，但30<30不成立 -> Emp(30, Greg), null
    // 最终结果：5行（包括3行null）
    assertThat(
        EnumerableDefaults.hashJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Joe"),
                    new Emp(30, "Greg"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(15, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(30, "Research"),
                    new Dept(30, "Development"))),
            e -> e.deptno,
            d -> d.deptno,
            (v0, v1) -> v0 + ", " + v1, null, false, true,
            (v0, v1) -> v0.deptno < 30)  // 非等值条件：员工部门号小于30
            .toList(),
        hasToString("[Emp(10, Fred), null,"
            + " Emp(20, Theodore), Dept(20, Sales),"
            + " Emp(20, Sebastian), Dept(20, Sales),"
            + " Emp(30, Joe), null,"
            + " Emp(30, Greg), null]"));
  }

  @Test void testRightHashJoinWithNonEquiConditions() {
    // 测试哈希连接操作（右连接，带非等值条件）
    // 右连接：返回右表所有行，左表匹配且满足非等值条件的行，不匹配或不满足条件的左表为null
    // 左表：员工表（4个员工）
    // 右表：部门表（4个部门）
    // 连接键：员工部门号 = 部门部门号
    // 非等值条件：员工部门号 < 30
    // 预期结果：
    // - 部门15(Marketing)：没有匹配的员工 -> null, Dept(15, Marketing)
    // - 部门20(Sales)：匹配员工20(Theodore)和20(Sebastian)，且20<30 -> 2行
    // - 部门30(Research)：匹配员工30(Greg)，但30<30不成立 -> null, Dept(30, Research)
    // - 部门30(Development)：匹配员工30(Greg)，但30<30不成立 -> null, Dept(30, Development)
    // 最终结果：5行（包括3行null）
    assertThat(
        EnumerableDefaults.hashJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Greg"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(15, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(30, "Research"),
                    new Dept(30, "Development"))),
            e -> e.deptno,
            d -> d.deptno,
            (v0, v1) -> v0 + ", " + v1, null, true, false,
            (v0, v1) -> v0.deptno < 30)  // 非等值条件：员工部门号小于30
            .toList(),
        hasToString("[Emp(20, Theodore), Dept(20, Sales),"
            + " Emp(20, Sebastian), Dept(20, Sales),"
            + " null, Dept(15, Marketing),"
            + " null, Dept(30, Research),"
            + " null, Dept(30, Development)]"));
  }

  @Test void testFullHashJoinWithNonEquiConditions() {
    // 测试哈希连接操作（全连接，带非等值条件）
    // 全连接：返回左表和右表所有行，匹配且满足非等值条件的配对，不匹配或不满足条件的为null
    // 左表：员工表（4个员工）
    // 右表：部门表（4个部门）
    // 连接键：员工部门号 = 部门部门号
    // 非等值条件：员工部门号 < 30
    // 预期结果：
    // - 员工10(Fred)：部门号10，没有匹配的部门 -> Emp(10, Fred), null
    // - 员工20(Theodore)：部门号20，匹配部门20(Sales)，且20<30 -> Emp(20, Theodore), Dept(20, Sales)
    // - 员工20(Sebastian)：部门号20，匹配部门20(Sales)，且20<30 -> Emp(20, Sebastian), Dept(20, Sales)
    // - 员工30(Greg)：部门号30，匹配部门30(Research)和30(Development)，但30<30不成立 -> Emp(30, Greg), null
    // - 部门15(Marketing)：没有匹配的员工 -> null, Dept(15, Marketing)
    // - 部门30(Research)：匹配员工30(Greg)，但30<30不成立 -> null, Dept(30, Research)
    // - 部门30(Development)：匹配员工30(Greg)，但30<30不成立 -> null, Dept(30, Development)
    // 最终结果：7行（包括5行null）
    assertThat(
        EnumerableDefaults.hashJoin(
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Emp(10, "Fred"),
                    new Emp(20, "Theodore"),
                    new Emp(20, "Sebastian"),
                    new Emp(30, "Greg"))),
            Linq4j.asEnumerable(
                Arrays.asList(
                    new Dept(15, "Marketing"),
                    new Dept(20, "Sales"),
                    new Dept(30, "Research"),
                    new Dept(30, "Development"))),
            e -> e.deptno,
            d -> d.deptno,
            (v0, v1) -> v0 + ", " + v1, null, true, true,
            (v0, v1) -> v0.deptno < 30)  // 非等值条件：员工部门号小于30
            .toList(),
        hasToString("[Emp(10, Fred), null,"
            + " Emp(20, Theodore), Dept(20, Sales),"
            + " Emp(20, Sebastian), Dept(20, Sales),"
            + " Emp(30, Greg), null,"
            + " null, Dept(15, Marketing),"
            + " null, Dept(30, Research),"
            + " null, Dept(30, Development)]"));
  }

  @Test void testMergeUnionAllEmptyOnRight() {
    // 测试合并联合操作（UNION ALL，右表为空）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，第二个为空
    // 排序键：员工部门号
    // 排序顺序：升序
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：只有第一个集合的行
    // 最终结果：[Emp(20, Lilly), Emp(30, Joe), Emp(30, Greg)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Lilly"),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg"))),
                Linq4j.emptyEnumerable()),
            e -> e.deptno,
            INTEGER_ASC,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, Lilly), Emp(30, Joe), Emp(30, Greg)]"));
  }

  @Test void testMergeUnionAllEmptyOnLeft() {
    // 测试合并联合操作（UNION ALL，左表为空）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，第一个为空
    // 排序键：员工部门号
    // 排序顺序：升序
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：只有第二个集合的行
    // 最终结果：[Emp(20, Lilly), Emp(30, Joe), Emp(30, Greg)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.emptyEnumerable(),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Lilly"),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg")))),
            e -> e.deptno,
            INTEGER_ASC,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, Lilly), Emp(30, Joe), Emp(30, Greg)]"));
  }

  @Test void testMergeUnionAllEmptyOnBoth() {
    // 测试合并联合操作（UNION ALL，两个表都为空）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个空集合
    // 排序键：员工部门号
    // 排序顺序：升序
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：空结果
    // 最终结果：[]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.emptyEnumerable(),
                Linq4j.emptyEnumerable()),
            e -> e.deptno,
            INTEGER_ASC,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[]"));
  }

  @Test void testMergeUnionAllOrderByDeptAsc2inputs() {
    // 测试合并联合操作（UNION ALL，2个输入，按部门号升序）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，都已按部门号排序
    // 排序键：员工部门号
    // 排序顺序：升序
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：按部门号升序合并所有行
    // 最终结果：[Emp(10, Fred), Emp(20, Lilly), Emp(30, Joe), Emp(30, Greg), Emp(30, Theodore), Emp(40, Sebastian)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Lilly"),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(10, "Fred"),
                        new Emp(30, "Theodore"),
                        new Emp(40, "Sebastian")))),
            e -> e.deptno,
            INTEGER_ASC,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(10, Fred), Emp(20, Lilly), Emp(30, Joe), "
            + "Emp(30, Greg), Emp(30, Theodore), Emp(40, Sebastian)]"));
  }

  @Test void testMergeUnionAllOrderByDeptAsc3inputs() {
    // 测试合并联合操作（UNION ALL，3个输入，按部门号升序）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：3个集合，都已按部门号排序
    // 排序键：员工部门号
    // 排序顺序：升序
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：按部门号升序合并所有行，包含重复的员工
    // 最终结果：[Emp(10, Fred), Emp(15, Phyllis), Emp(18, Maddie), Emp(20, Lilly), Emp(22, Jenny), Emp(30, Joe), Emp(30, Greg), Emp(30, Joe), Emp(40, Sebastian), Emp(42, Susan)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Lilly"),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(15, "Phyllis"),
                        new Emp(18, "Maddie"),
                        new Emp(22, "Jenny"),
                        new Emp(42, "Susan"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(10, "Fred"),
                        new Emp(30, "Joe"),
                        new Emp(40, "Sebastian")))),
            e -> e.deptno,
            INTEGER_ASC,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(10, Fred), Emp(15, Phyllis), Emp(18, Maddie], "
            + "Emp(20, Lilly), Emp(22, Jenny), Emp(30, Joe), Emp(30, Greg), "
            + "Emp(30, Joe), Emp(40, Sebastian), Emp(42, Susan)]"));
  }

  @Test void testMergeUnionOrderByDeptAsc3inputs() {
    // 测试合并联合操作（UNION，3个输入，按部门号升序）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：3个集合，都已按部门号排序，包含重复的员工
    // 排序键：员工部门号
    // 排序顺序：升序
    // UNION：去重，只保留唯一的行
    // 预期结果：按部门号升序合并所有行，去除重复的员工
    // 注意：有多个Emp(15, Phyllis)和Emp(30, Joe)，但只保留一个
    // 最终结果：[Emp(10, Fred), Emp(15, Phyllis), Emp(18, Maddie), Emp(20, Lilly), Emp(22, Jenny), Emp(30, Joe), Emp(30, Greg), Emp(40, Sebastian), Emp(42, Susan)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(15, "Phyllis"),
                        new Emp(15, "Phyllis"),
                        new Emp(20, "Lilly"),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(15, "Phyllis"),
                        new Emp(18, "Maddie"),
                        new Emp(22, "Jenny"),
                        new Emp(30, "Joe"),
                        new Emp(42, "Susan"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(10, "Fred"),
                        new Emp(15, "Phyllis"),
                        new Emp(30, "Joe"),
                        new Emp(30, "Joe"),
                        new Emp(40, "Sebastian")))),
            e -> e.deptno,
            INTEGER_ASC,
            false,  // false表示UNION（去重），true表示UNION ALL（不去重）
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(10, Fred), Emp(15, Phyllis), Emp(18, Maddie], "
            + "Emp(20, Lilly), Emp(22, Jenny), Emp(30, Joe), Emp(30, Greg), "
            + "Emp(40, Sebastian), Emp(42, Susan)]"));
  }

  @Test void testMergeUnionAllOrderByDeptDesc2inputs() {
    // 测试合并联合操作（UNION ALL，2个输入，按部门号降序）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，都已按部门号降序排序
    // 排序键：员工部门号
    // 排序顺序：降序
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：按部门号降序合并所有行
    // 最终结果：[Emp(50, Fred), Emp(42, Lilly), Emp(30, Joe), Emp(30, Greg), Emp(30, Theodore), Emp(10, Sebastian)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(42, "Lilly"),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(50, "Fred"),
                        new Emp(30, "Theodore"),
                        new Emp(10, "Sebastian")))),
            e -> e.deptno,
            INTEGER_DESC,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(50, Fred), Emp(42, Lilly), Emp(30, Joe), "
            + "Emp(30, Greg), Emp(30, Theodore), Emp(10, Sebastian)]"));
  }

  @Test void testMergeUnionAllOrderByDeptDesc3inputs() {
    // 测试合并联合操作（UNION ALL，3个输入，按部门号降序）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：3个集合，都已按部门号降序排序，包含重复的员工
    // 排序键：员工部门号
    // 排序顺序：降序
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：按部门号降序合并所有行，包含重复的员工
    // 注意：有多个Emp(22, Jenny)和Emp(20, Joe)，都保留
    // 最终结果：[Emp(50, Fred), Emp(45, Phyllis), Emp(42, Maddie), Emp(35, Lilly), Emp(22, Jenny), Emp(22, Jenny), Emp(22, Jenny), Emp(20, Joe), Emp(20, Greg), Emp(20, Theodore), Emp(15, Sebastian), Emp(12, Susan)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(35, "Lilly"),
                        new Emp(22, "Jenny"),
                        new Emp(20, "Joe"),
                        new Emp(20, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(45, "Phyllis"),
                        new Emp(42, "Maddie"),
                        new Emp(22, "Jenny"),
                        new Emp(22, "Jenny"),
                        new Emp(12, "Susan"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(50, "Fred"),
                        new Emp(20, "Theodore"),
                        new Emp(15, "Sebastian")))),
            e -> e.deptno,
            INTEGER_DESC,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(50, Fred), Emp(45, Phyllis), Emp(42, Maddie), "
            + "Emp(35, Lilly), Emp(22, Jenny), Emp(22, Jenny), "
            + "Emp(22, Jenny), Emp(20, Joe), Emp(20, Greg), "
            + "Emp(20, Theodore), Emp(15, Sebastian), Emp(12, Susan)]"));
  }

  @Test void testMergeUnionOrderByDeptDesc3inputs() {
    // 测试合并联合操作（UNION，3个输入，按部门号降序）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：3个集合，都已按部门号降序排序，包含重复的员工
    // 排序键：员工部门号
    // 排序顺序：降序
    // UNION：去重，只保留唯一的行
    // 预期结果：按部门号降序合并所有行，去除重复的员工
    // 注意：有多个Emp(22, Jenny)和Emp(20, Joe)，但只保留一个
    // 最终结果：[Emp(50, Fred), Emp(45, Phyllis), Emp(42, Maddie), Emp(35, Lilly), Emp(22, Jenny), Emp(20, Joe), Emp(20, Greg), Emp(20, Theodore), Emp(15, Sebastian), Emp(12, Susan)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(35, "Lilly"),
                        new Emp(22, "Jenny"),
                        new Emp(22, "Jenny"),
                        new Emp(20, "Joe"),
                        new Emp(20, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(45, "Phyllis"),
                        new Emp(42, "Maddie"),
                        new Emp(22, "Jenny"),
                        new Emp(12, "Susan"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(50, "Fred"),
                        new Emp(22, "Jenny"),
                        new Emp(20, "Theodore"),
                        new Emp(20, "Joe"),
                        new Emp(15, "Sebastian")))),
            e -> e.deptno,
            INTEGER_DESC,
            false,  // false表示UNION（去重），true表示UNION ALL（不去重）
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(50, Fred), Emp(45, Phyllis), Emp(42, Maddie), "
            + "Emp(35, Lilly), Emp(22, Jenny), Emp(20, Joe), Emp(20, Greg), "
            + "Emp(20, Theodore), Emp(15, Sebastian), Emp(12, Susan)]"));
  }

  @Test void testMergeUnionAllOrderByNameAscNullsFirst() {
    // 测试合并联合操作（UNION ALL，按姓名升序，NULL值排在前面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：升序，NULL值排在前面
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：NULL值排在前面，然后按姓名升序排列
    // 注意：有多个Emp(20, null)，都保留
    // 最终结果：[Emp(20, null), Emp(10, null), Emp(20, null), Emp(30, Greg), Emp(30, Sebastian), Emp(10, Theodore)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(10, null),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(30, "Sebastian"),
                        new Emp(10, "Theodore")))),
            e -> e.name,
            STRING_ASC_NULLS_FIRST,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, null), Emp(10, null), Emp(20, null), "
            + "Emp(30, Greg), Emp(30, Sebastian), Emp(10, Theodore)]"));
  }

  @Test void testMergeUnionOrderByNameAscNullsFirst() {
    // 测试合并联合操作（UNION，按姓名升序，NULL值排在前面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：升序，NULL值排在前面
    // UNION：去重，只保留唯一的行
    // 预期结果：NULL值排在前面，然后按姓名升序排列，去除重复的员工
    // 注意：有多个Emp(20, null)，但只保留一个
    // 最终结果：[Emp(20, null), Emp(10, null), Emp(30, Greg), Emp(30, Sebastian), Emp(10, Theodore)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(10, null),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(30, "Sebastian"),
                        new Emp(10, "Theodore")))),
            e -> e.name,
            STRING_ASC_NULLS_FIRST,
            false,  // false表示UNION（去重），true表示UNION ALL（不去重）
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, null), Emp(10, null), Emp(30, Greg), "
            + "Emp(30, Sebastian), Emp(10, Theodore)]"));
  }

  @Test void testMergeUnionAllOrderByNameDescNullsFirst() {
    // 测试合并联合操作（UNION ALL，按姓名降序，NULL值排在前面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：降序，NULL值排在前面
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：NULL值排在前面，然后按姓名降序排列
    // 注意：有多个Emp(20, null)，都保留
    // 最终结果：[Emp(20, null), Emp(10, null), Emp(20, null), Emp(30, Theodore), Emp(10, Sebastian), Emp(30, Greg)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(10, null),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(30, "Theodore"),
                        new Emp(10, "Sebastian")))),
            e -> e.name,
            STRING_DESC_NULLS_FIRST,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, null), Emp(10, null), Emp(20, null), "
            + "Emp(30, Theodore), Emp(10, Sebastian), Emp(30, Greg)]"));
  }

  @Test void testMergeUnionOrderByNameDescNullsFirst() {
    // 测试合并联合操作（UNION，按姓名降序，NULL值排在前面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：降序，NULL值排在前面
    // UNION：去重，只保留唯一的行
    // 预期结果：NULL值排在前面，然后按姓名降序排列，去除重复的员工
    // 注意：有多个Emp(20, null)，但只保留一个
    // 最终结果：[Emp(20, null), Emp(10, null), Emp(30, Theodore), Emp(10, Sebastian), Emp(30, Greg)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(10, null),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(30, "Theodore"),
                        new Emp(10, "Sebastian")))),
            e -> e.name,
            STRING_DESC_NULLS_FIRST,
            false,  // false表示UNION（去重），true表示UNION ALL（不去重）
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, null), Emp(10, null), Emp(30, Theodore), "
            + "Emp(10, Sebastian), Emp(30, Greg)]"));
  }

  @Test void testMergeUnionAllOrderByNameAscNullsLast() {
    // 测试合并联合操作（UNION ALL，按姓名升序，NULL值排在后面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：升序，NULL值排在后面
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：按姓名升序排列，NULL值排在后面
    // 注意：有多个Emp(20, Greg)、Emp(10, null)和Emp(30, null)，都保留
    // 最终结果：[Emp(20, Greg), Emp(20, Greg), Emp(30, Sebastian), Emp(30, Theodore), Emp(10, null), Emp(30, null), Emp(10, null)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Greg"),
                        new Emp(10, null),
                        new Emp(30, null))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Greg"),
                        new Emp(30, "Sebastian"),
                        new Emp(30, "Theodore"),
                        new Emp(10, null)))),
            e -> e.name,
            STRING_ASC_NULLS_LAST,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, Greg), Emp(20, Greg), Emp(30, Sebastian), "
            + "Emp(30, Theodore), Emp(10, null), Emp(30, null), "
            + "Emp(10, null)]"));
  }

  @Test void testMergeUnionOrderByNameAscNullsLast() {
    // 测试合并联合操作（UNION，按姓名升序，NULL值排在后面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：升序，NULL值排在后面
    // UNION：去重，只保留唯一的行
    // 预期结果：按姓名升序排列，NULL值排在后面，去除重复的员工
    // 注意：有多个Emp(20, Greg)、Emp(10, null)和Emp(30, null)，但只保留一个
    // 最终结果：[Emp(20, Greg), Emp(30, Sebastian), Emp(30, Theodore), Emp(10, null), Emp(30, null)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Greg"),
                        new Emp(10, null),
                        new Emp(30, null))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Greg"),
                        new Emp(30, "Sebastian"),
                        new Emp(30, "Theodore"),
                        new Emp(10, null)))),
            e -> e.name,
            STRING_ASC_NULLS_LAST,
            false,  // false表示UNION（去重），true表示UNION ALL（不去重）
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(20, Greg), Emp(30, Sebastian), "
            + "Emp(30, Theodore), Emp(10, null), Emp(30, null)]"));
  }

  @Test void testMergeUnionAllOrderByNameDescNullsLast() {
    // 测试合并联合操作（UNION ALL，按姓名降序，NULL值排在后面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：降序，NULL值排在后面
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：按姓名降序排列，NULL值排在后面
    // 注意：有多个Emp(20, Greg)、Emp(10, null)和Emp(30, null)，都保留
    // 最终结果：[Emp(30, Theodore), Emp(30, Sebastian), Emp(20, Greg), Emp(20, Greg), Emp(10, null), Emp(30, null), Emp(10, null)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Greg"),
                        new Emp(10, null),
                        new Emp(30, null))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(30, "Theodore"),
                        new Emp(30, "Sebastian"),
                        new Emp(20, "Greg"),
                        new Emp(10, null)))),
            e -> e.name,
            STRING_DESC_NULLS_LAST,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(30, Theodore), Emp(30, Sebastian), Emp(20, Greg), "
            + "Emp(20, Greg), Emp(10, null), Emp(30, null), Emp(10, null)]"));
  }

  @Test void testMergeUnionOrderByNameDescNullsLast() {
    // 测试合并联合操作（UNION，按姓名降序，NULL值排在后面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：2个集合，包含NULL姓名的员工
    // 排序键：员工姓名
    // 排序顺序：降序，NULL值排在后面
    // UNION：去重，只保留唯一的行
    // 预期结果：按姓名降序排列，NULL值排在后面，去除重复的员工
    // 注意：有多个Emp(20, Greg)、Emp(10, null)和Emp(30, null)，但只保留一个
    // 最终结果：[Emp(30, Theodore), Emp(30, Sebastian), Emp(20, Greg), Emp(10, null), Emp(30, null)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Greg"),
                        new Emp(10, null),
                        new Emp(30, null))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(30, "Theodore"),
                        new Emp(30, "Sebastian"),
                        new Emp(20, "Greg"),
                        new Emp(10, null)))),
            e -> e.name,
            STRING_DESC_NULLS_LAST,
            false,  // false表示UNION（去重），true表示UNION ALL（不去重）
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(30, Theodore), Emp(30, Sebastian), Emp(20, Greg), "
            + "Emp(10, null), Emp(30, null)]"));
  }

  @Test void testMergeUnionAllOrderByDeptAscNameDescNullsFirst() {
    // 测试合并联合操作（UNION ALL，复合排序键：部门号升序+姓名降序，NULL值排在前面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：5个集合，包含NULL姓名的员工
    // 排序键：复合键（部门号升序，姓名降序）
    // NULL值处理：姓名的NULL值排在前面
    // UNION ALL：保留所有行（包括重复行）
    // 预期结果：先按部门号升序，同一部门内按姓名降序，NULL姓名排在前面
    // 注意：有多个Emp(20, Lilly)、Emp(22, null)等，都保留
    // 最终结果：[Emp(10, null), Emp(10, Fred), Emp(20, null), Emp(20, Lilly), Emp(20, Lilly), Emp(20, Lilly), Emp(20, Antoine), Emp(20, Annie), Emp(22, null), Emp(22, null), Emp(22, Jenny), Emp(30, Joe), Emp(30, Joe), Emp(30, Greg), Emp(40, Sebastian), Emp(42, Susan), Emp(50, Lolly)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(new Emp(10, null))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Lilly"),
                        new Emp(20, "Lilly"),
                        new Emp(20, "Antoine"),
                        new Emp(22, null),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(20, "Annie"),
                        new Emp(22, "Jenny"),
                        new Emp(42, "Susan"))),
                Linq4j.asEnumerable(
                    Arrays.asList(new Emp(50, "Lolly"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(10, "Fred"),
                        new Emp(20, "Lilly"),
                        new Emp(22, null),
                        new Emp(30, "Joe"),
                        new Emp(40, "Sebastian")))),
            e -> e,
            DEPT_ASC_AND_NAME_DESC_NULLS_FIRST,
            true,
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(10, null), Emp(10, Fred), Emp(20, null), "
            + "Emp(20, Lilly), Emp(20, Lilly), Emp(20, Lilly), "
            + "Emp(20, Antoine), Emp(20, Annie), Emp(22, null), "
            + "Emp(22, null), Emp(22, Jenny), Emp(30, Joe), Emp(30, Joe), "
            + "Emp(30, Greg), Emp(40, Sebastian), Emp(42, Susan), "
            + "Emp(50, Lolly)]"));
  }

  @Test void testMergeUnionOrderByDeptAscNameDescNullsFirst() {
    // 测试合并联合操作（UNION，复合排序键：部门号升序+姓名降序，NULL值排在前面）
    // 合并联合：合并多个有序集合，保持排序顺序
    // 输入：5个集合，包含NULL姓名的员工
    // 排序键：复合键（部门号升序，姓名降序）
    // NULL值处理：姓名的NULL值排在前面
    // UNION：去重，只保留唯一的行
    // 预期结果：先按部门号升序，同一部门内按姓名降序，NULL姓名排在前面，去除重复的员工
    // 注意：有多个Emp(20, Lilly)、Emp(22, null)等，但只保留一个
    // 最终结果：[Emp(10, null), Emp(10, Fred), Emp(20, null), Emp(20, Lilly), Emp(20, Antoine), Emp(20, Annie), Emp(22, null), Emp(22, Jenny), Emp(30, Joe), Emp(30, Greg), Emp(40, Sebastian), Emp(42, Susan), Emp(50, Lolly)]
    assertThat(
        EnumerableDefaults.mergeUnion(
            Arrays.asList(
                Linq4j.asEnumerable(
                    Arrays.asList(new Emp(10, null))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, "Lilly"),
                        new Emp(20, "Lilly"),
                        new Emp(20, "Antoine"),
                        new Emp(22, null),
                        new Emp(30, "Joe"),
                        new Emp(30, "Greg"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(20, null),
                        new Emp(20, "Annie"),
                        new Emp(22, "Jenny"),
                        new Emp(42, "Susan"))),
                Linq4j.asEnumerable(
                    Arrays.asList(new Emp(50, "Lolly"))),
                Linq4j.asEnumerable(
                    Arrays.asList(
                        new Emp(10, "Fred"),
                        new Emp(20, "Lilly"),
                        new Emp(22, null),
                        new Emp(30, "Joe"),
                        new Emp(40, "Sebastian")))),
            e -> e,
            DEPT_ASC_AND_NAME_DESC_NULLS_FIRST,
            false,  // false表示UNION（去重），true表示UNION ALL（不去重）
            EMP_EQUALITY_COMPARER).toList(),
        hasToString("[Emp(10, null), Emp(10, Fred), Emp(20, null), "
            + "Emp(20, Lilly), Emp(20, Antoine), Emp(20, Annie), "
            + "Emp(22, null), Emp(22, Jenny), Emp(30, Joe), Emp(30, Greg), "
            + "Emp(40, Sebastian), Emp(42, Susan), Emp(50, Lolly)]"));
  }

  // 整数升序比较器：使用Integer的自然顺序进行比较
private static final Comparator<Integer> INTEGER_ASC = Integer::compare;
// 整数降序比较器：使用升序比较器的反向
private static final Comparator<Integer> INTEGER_DESC = INTEGER_ASC.reversed();

// 字符串升序比较器：使用String的自然顺序进行比较
private static final Comparator<String> STRING_ASC = Comparator.naturalOrder();
// 字符串降序比较器：使用升序比较器的反向
private static final Comparator<String> STRING_DESC = STRING_ASC.reversed();

// 字符串升序比较器（NULL值排在前面）：NULL值小于任何非NULL值
private static final Comparator<String> STRING_ASC_NULLS_FIRST =
      Comparator.nullsFirst(STRING_ASC);
// 字符串升序比较器（NULL值排在后面）：NULL值大于任何非NULL值
private static final Comparator<String> STRING_ASC_NULLS_LAST =
      Comparator.nullsLast(STRING_ASC);
// 字符串降序比较器（NULL值排在前面）：NULL值小于任何非NULL值
private static final Comparator<String> STRING_DESC_NULLS_FIRST =
      Comparator.nullsFirst(STRING_DESC);
// 字符串降序比较器（NULL值排在后面）：NULL值大于任何非NULL值
private static final Comparator<String> STRING_DESC_NULLS_LAST =
      Comparator.nullsLast(STRING_DESC);

// 员工复合比较器：先按部门号升序，再按姓名降序（NULL值排在前面）
// 这是一个复合比较器，用于多字段排序
private static final Comparator<Emp> DEPT_ASC_AND_NAME_DESC_NULLS_FIRST =
      Comparator.<Emp>comparingInt(emp -> emp.deptno)
          .thenComparing(emp -> emp.name, STRING_DESC_NULLS_FIRST);

// 员工相等性比较器：使用对象的equals方法进行比较
// 用于在UNION操作中判断两个员工是否相同
private static final EqualityComparer<Emp> EMP_EQUALITY_COMPARER = Functions.identityComparer();

  /** Employee record. */
// 员工记录类，用于测试连接操作
// 包含员工的基本信息：部门号和姓名
private static class Emp {
    // 员工所属的部门号，用于连接操作
    final int deptno;
    // 员工姓名，可以为NULL，用于测试NULL值处理
    final @Nullable String name;

    // 构造函数：创建一个员工对象
    // 参数：
    // - deptno: 员工所属的部门号
    // - name: 员工姓名，可以为NULL
    Emp(int deptno, @Nullable String name) {
      this.deptno = deptno;
      this.name = name;
    }

    // 重写equals方法：判断两个员工对象是否相等
    // 相等条件：部门号相同且姓名相同（使用Objects.equals处理NULL）
    @Override public boolean equals(Object o) {
      if (this == o) {
        return true;  // 同一个对象，直接返回true
      }
      if (o == null || this.getClass() != o.getClass()) {
        return false;  // 对象为null或类型不同，返回false
      }
      final Emp emp = (Emp) o;
      return this.deptno == emp.deptno && Objects.equals(this.name, emp.name);  // 比较部门号和姓名
    }

    // 重写hashCode方法：根据部门号和姓名计算哈希值
    // 使用Objects.hash方法自动处理NULL值
    @Override public int hashCode() {
      return Objects.hash(this.deptno, this.name);
    }

    // 重写toString方法：返回员工的字符串表示
    // 格式：Emp(部门号, 姓名)
    @Override public String toString() {
      return "Emp(" + deptno + ", " + name + ")";
    }
  }

  /** Department record. */
// 部门记录类，用于测试连接操作
// 包含部门的基本信息：部门号和部门名称
private static class Dept {
    // 部门号，用于连接操作
    final int deptno;
    // 部门名称，可以为NULL，用于测试NULL值处理
    final @Nullable String name;

    // 构造函数：创建一个部门对象
    // 参数：
    // - deptno: 部门号
    // - name: 部门名称，可以为NULL
    Dept(int deptno, @Nullable String name) {
      this.deptno = deptno;
      this.name = name;
    }

    // 重写toString方法：返回部门的字符串表示
    // 格式：Dept(部门号, 部门名称)
    @Override public String toString() {
      return "Dept(" + deptno + ", " + name + ")";
    }
  }
}
