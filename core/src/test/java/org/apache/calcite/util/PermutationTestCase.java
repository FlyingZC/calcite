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
package org.apache.calcite.util; // 包声明：该类位于org.apache.calcite.util包下，属于Calcite工具类包

import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现，用于创建Java类型系统
import org.apache.calcite.rel.core.Project; // 导入Project关系节点，表示投影操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器，用于构建行表达式
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest的is匹配器，用于相等性断言
import static org.hamcrest.CoreMatchers.not; // 导入Hamcrest的not匹配器，用于否定断言
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest的nullValue匹配器，用于null值断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest的断言方法
import static org.hamcrest.Matchers.equalTo; // 导入Hamcrest的equalTo匹配器
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest的hasToString匹配器，用于字符串表示断言
import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit 5的assertFalse断言方法
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5的assertTrue断言方法
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit 5的fail方法，用于标记测试失败

/**
 * Unit test for {@link Permutation}. // Permutation类的单元测试类，用于测试排列组合的各种操作和功能
 */ // 该类包含多个测试方法，验证Permutation类的核心功能包括创建、修改、克隆、反转、插入等操作
class PermutationTestCase {
  @Test void testOne() { // 测试Permutation的基本操作：创建、设置元素、获取逆排列
    final Permutation perm = new Permutation(4); // 创建一个长度为4的排列，初始为[0, 1, 2, 3]
    assertThat(perm, hasToString("[0, 1, 2, 3]")); // 验证排列的字符串表示是否为[0, 1, 2, 3]
    assertThat(perm.size(), hasToString("4")); // 验证排列的大小是否为4

    perm.set(0, 2); // 将位置0的元素设置为2，交换位置0和2的值
    assertThat(perm, hasToString("[2, 1, 0, 3]")); // 验证排列变为[2, 1, 0, 3]

    perm.set(1, 0); // 将位置1的元素设置为0，交换位置1和0的值
    assertThat(perm, hasToString("[2, 0, 1, 3]")); // 验证排列变为[2, 0, 1, 3]

    final Permutation invPerm = perm.inverse(); // 计算当前排列的逆排列
    assertThat(invPerm, hasToString("[1, 2, 0, 3]")); // 验证逆排列为[1, 2, 0, 3]

    // changing perm doesn't change inverse // 修改原排列不应该影响已计算的逆排列
    perm.set(0, 0); // 将位置0的元素设置为0，保持不变
    assertThat(perm, hasToString("[0, 2, 1, 3]")); // 验证排列变为[0, 2, 1, 3]
    assertThat(invPerm, hasToString("[1, 2, 0, 3]")); // 验证逆排列保持不变，仍为[1, 2, 0, 3]
  }

  @Test void testTwo() { // 测试Permutation的克隆、相等性比较和独立修改
    final Permutation perm = new Permutation(new int[]{3, 2, 0, 1}); // 使用指定数组创建排列[3, 2, 0, 1]
    assertFalse(perm.isIdentity()); // 验证该排列不是恒等排列
    assertThat(perm, hasToString("[3, 2, 0, 1]")); // 验证排列的字符串表示

    Permutation perm2 = (Permutation) perm.clone(); // 克隆当前排列，创建一个独立的副本
    assertThat(perm2, hasToString("[3, 2, 0, 1]")); // 验证克隆后的排列与原排列相同
    assertThat(perm, is(perm2)); // 验证perm等于perm2
    assertThat(perm2, is(perm)); // 验证perm2等于perm，对称性测试

    perm.set(2, 1); // 修改原排列，将位置2的元素设置为1
    assertThat(perm, hasToString("[3, 2, 1, 0]")); // 验证原排列变为[3, 2, 1, 0]
    assertThat(perm, not(equalTo(perm2))); // 验证原排列与克隆的排列不再相等

    // clone not affected // 验证克隆的排列不受原排列修改的影响
    assertThat(perm2, hasToString("[3, 2, 0, 1]")); // 验证克隆的排列保持不变

    perm2.set(2, 3); // 修改克隆的排列，将位置2的元素设置为3
    assertThat(perm2, hasToString("[0, 2, 3, 1]")); // 验证克隆的排列变为[0, 2, 3, 1]
  }

  @Test void testInsert() { // 测试Permutation的insertTarget方法，在指定位置插入目标元素
    Permutation perm = new Permutation(new int[]{3, 0, 4, 2, 1}); // 创建排列[3, 0, 4, 2, 1]
    perm.insertTarget(2); // 在位置2插入目标元素，排列大小增加1
    assertThat(perm, hasToString("[4, 0, 5, 3, 1, 2]")); // 验证插入后的排列为[4, 0, 5, 3, 1, 2]

    // insert at start // 测试在起始位置插入
    perm = new Permutation(new int[]{3, 0, 4, 2, 1}); // 重新创建排列
    perm.insertTarget(0); // 在位置0插入目标元素
    assertThat(perm, hasToString("[4, 1, 5, 3, 2, 0]")); // 验证插入后的排列为[4, 1, 5, 3, 2, 0]

    // insert at end // 测试在末尾位置插入
    perm = new Permutation(new int[]{3, 0, 4, 2, 1}); // 重新创建排列
    perm.insertTarget(5); // 在位置5（末尾）插入目标元素
    assertThat(perm, hasToString("[3, 0, 4, 2, 1, 5]")); // 验证插入后的排列为[3, 0, 4, 2, 1, 5]

    // insert into empty // 测试向空排列插入
    perm = new Permutation(new int[]{}); // 创建空排列
    perm.insertTarget(0); // 在位置0插入目标元素
    assertThat(perm, hasToString("[0]")); // 验证插入后的排列为[0]
  }

  @Test void testEmpty() { // 测试空排列的各种操作和边界条件
    final Permutation perm = new Permutation(0); // 创建一个长度为0的空排列
    assertTrue(perm.isIdentity()); // 验证空排列是恒等排列
    assertThat(perm, hasToString("[]")); // 验证空排列的字符串表示为[]
    assertThat(perm, is(perm)); // 验证空排列等于自身
    assertThat(perm, is(perm.inverse())); // 验证空排列等于其逆排列

    try { // 测试越界访问：尝试设置超出范围的索引
      perm.set(1, 0); // 尝试在位置1设置值，但排列长度为0
      fail("expected exception"); // 如果没有抛出异常则测试失败
    } catch (ArrayIndexOutOfBoundsException e) { // 捕获预期的数组越界异常
      // success // 成功捕获异常，测试通过
    }

    try { // 测试负索引访问：尝试使用负索引
      perm.set(-1, 2); // 尝试在位置-1设置值
      fail("expected exception"); // 如果没有抛出异常则测试失败
    } catch (ArrayIndexOutOfBoundsException e) { // 捕获预期的数组越界异常
      // success // 成功捕获异常，测试通过
    }
  }

  @Test void testProjectPermutation() { // 测试Project.getPermutation方法，验证从Project表达式列表中提取排列
    final RelDataTypeFactory typeFactory = new JavaTypeFactoryImpl(); // 创建Java类型工厂
    final RexBuilder builder = new RexBuilder(typeFactory); // 创建Rex表达式构建器
    final RelDataType doubleType = // 创建DOUBLE类型
        typeFactory.createSqlType(SqlTypeName.DOUBLE);

    // A project with [1, 1] is not a permutation, so should return null // 测试：包含重复引用的Project不是有效排列
    final Permutation perm = // 调用getPermutation方法，输入字段数为2，表达式列表为[1, 1]
        Project.getPermutation(2,
            ImmutableList.of(builder.makeInputRef(doubleType, 1), // 创建对输入字段1的引用
                builder.makeInputRef(doubleType, 1))); // 再次创建对输入字段1的引用，重复了
    assertThat(perm, nullValue()); // 验证返回null，因为[1, 1]不是有效排列（重复引用）

    // A project with [0, 1, 0] is not a permutation, so should return null // 测试：输出字段数多于输入字段数的Project不是有效排列
    final Permutation perm1 = // 调用getPermutation方法，输入字段数为2，表达式列表为[0, 1, 0]
        Project.getPermutation(2,
            ImmutableList.of(builder.makeInputRef(doubleType, 0), // 创建对输入字段0的引用
                builder.makeInputRef(doubleType, 1), // 创建对输入字段1的引用
                builder.makeInputRef(doubleType, 0))); // 再次创建对输入字段0的引用，重复了
    assertThat(perm1, nullValue()); // 验证返回null，因为[0, 1, 0]不是有效排列（重复引用）

    // A project of [1, 0] is a valid permutation! // 测试：有效的排列：交换两个字段的位置
    final Permutation perm2 = // 调用getPermutation方法，输入字段数为2，表达式列表为[1, 0]
        Project.getPermutation(2,
            ImmutableList.of(builder.makeInputRef(doubleType, 1), // 创建对输入字段1的引用
                builder.makeInputRef(doubleType, 0))); // 创建对输入字段0的引用
    assertThat(perm2, is(new Permutation(new int[]{1, 0}))); // 验证返回的排列为[1, 0]，这是一个有效的排列
  }
}
