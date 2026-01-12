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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证
package org.apache.calcite.util; // 声明包名为org.apache.calcite.util，表示这个类属于Calcite工具包
import org.junit.jupiter.api.Tag; // 导入JUnit 5的Tag注解，用于标记测试类别（如慢速测试）
import org.junit.jupiter.api.Test; // 导入JUnit 5的Test注解，用于标记测试方法

import java.util.AbstractList; // 导入AbstractList抽象类，用于创建自定义列表
import java.util.ArrayList; // 导入ArrayList动态数组类，用于存储可变长度的元素集合
import java.util.Arrays; // 导入Arrays工具类，用于操作数组的静态方法
import java.util.Collection; // 导入Collection接口，表示集合的根接口
import java.util.LinkedHashSet; // 导入LinkedHashSet类，用于保持插入顺序的Set集合
import java.util.List; // 导入List接口，表示有序集合
import java.util.Random; // 导入Random类，用于生成随机数
import java.util.Set; // 导入Set接口，表示不重复元素的集合
import java.util.TreeSet; // 导入TreeSet类，用于自动排序的Set集合
import java.util.function.Function; // 导入Function函数式接口，用于函数式编程

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言库的is匹配器
import static org.hamcrest.CoreMatchers.nullValue; // 导入Hamcrest断言库的nullValue匹配器
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言库的assertThat方法
import static org.hamcrest.Matchers.empty; // 导入Hamcrest断言库的empty匹配器
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest断言库的hasSize匹配器
import static org.hamcrest.Matchers.hasToString; // 导入Hamcrest断言库的hasToString匹配器
import static org.junit.jupiter.api.Assertions.assertTrue; // 导入JUnit 5的assertTrue断言方法

import static java.util.Objects.requireNonNull; // 导入Objects工具类的requireNonNull方法，用于空值检查

/**
 * Unit test for {@link PartiallyOrderedSet}.
 * // PartiallyOrderedSet类的单元测试类
 * // PartiallyOrderedSet是Calcite中的一个偏序集合实现，用于维护具有偏序关系的元素集合
 * // 偏序关系是指集合中的某些元素之间存在"小于"或"大于"的关系，但不是所有元素都可以比较
 * // 例如：在集合{a, b, ab}中，a是ab的子集，b是ab的子集，但a和b之间没有偏序关系
 */
class PartiallyOrderedSetTest { // 测试类定义，用于测试PartiallyOrderedSet的各种功能
  private static final boolean DEBUG = false; // 调试标志，设置为true时会输出详细的调试信息，默认为false

  // 100, 250, 1000, 3000 are reasonable
  // 注释说明：这些是合理的测试规模值，用于控制测试数据的大小
  private static final int SCALE = 250; // 测试规模常量，设置为250，用于控制测试数据集的大小

  final long seed = new Random().nextLong(); // 随机数种子，用于生成可重复的随机序列，便于调试和重现问题
  final Random random = new Random(seed); // 使用指定种子创建的随机数生成器，保证测试结果的可重复性

  static final PartiallyOrderedSet.Ordering<String> STRING_SUBSET_ORDERING = // 定义字符串子集偏序关系，用于测试
      (e1, e2) -> { // Lambda表达式实现Ordering接口，定义两个字符串之间的偏序关系
        // e1 < e2 if every char in e1 is also in e2
        // 注释说明：如果e1中的每个字符都在e2中出现，则认为e1 < e2（e1是e2的子集）
        for (int i = 0; i < e1.length(); i++) { // 遍历e1字符串的每个字符
          if (e2.indexOf(e1.charAt(i)) < 0) { // 检查e1的当前字符是否在e2中存在
            return false; // 如果e1中有字符不在e2中，则返回false，表示e1不是e2的子集
          }
        }
        return true; // 如果e1的所有字符都在e2中，则返回true，表示e1是e2的子集
      };

  /** As an ordering, integers are ordered by division.
   * Top is 1, its children are primes, etc. */
  // 注释说明：作为偏序关系，整数按照整除关系排序，1是顶层元素，其子元素是质数等
  private static boolean isDivisor(int e1, int e2) { // 判断e1是否是e2的约数（e1能整除e2）
    return e2 % e1 == 0; // 如果e2除以e1的余数为0，说明e1是e2的约数，返回true
  }

  /** As an ordering, bottom is 1, parents are primes, etc. */
  // 注释说明：作为偏序关系，1是底层元素，父元素是质数等（与isDivisor相反）
  private static boolean isDivisorInverse(Integer e1, Integer e2) { // 判断e2是否是e1的约数（e2能整除e1，即e1是e2的倍数）
    return isDivisor(e2, e1); // 调用isDivisor方法，交换参数顺序，判断e2是否是e1的约数
  }

  /** As an ordering, integers are ordered by bit inclusion.
   * E.g. the children of 14 (1110) are 12 (1100), 10 (1010) and 6 (0110). */
  // 注释说明：作为偏序关系，整数按照位包含关系排序，例如14（二进制1110）的子元素是12（1100）、10（1010）和6（0110）
  private static boolean isBitSubset(int e1, int e2) { // 判断e1的位是否是e2的位的子集（e1的所有位在e2中都有）
    return (e2 & e1) == e2; // 使用位与运算，如果e2与e1的结果等于e2，说明e2的所有位都在e1中，即e1是e2的超集
  }

  /** As an ordering, integers are ordered by bit inclusion.
   * E.g. the parents of 14 (1110) are 12 (1100), 10 (1010) and 6 (0110). */
  // 注释说明：作为偏序关系，整数按照位包含关系排序，例如14（二进制1110）的父元素是12（1100）、10（1010）和6（0110）
  private static boolean isBitSuperset(Integer e1, Integer e2) { // 判断e1的位是否是e2的位的超集（e1包含e2的所有位）
    return (e2 & e1) == e1; // 使用位与运算，如果e2与e1的结果等于e1，说明e1的所有位都在e2中，即e1是e2的子集
  }

  @Test void testPoset() { // 测试方法：测试PartiallyOrderedSet的基本功能
    String empty = "''"; // 定义空字符串变量，表示空集
    String abcd = "'abcd'"; // 定义包含字符a、b、c、d的字符串变量
    final PartiallyOrderedSet<String> poset = // 创建一个字符串类型的偏序集合，使用字符串子集偏序关系
        new PartiallyOrderedSet<>(STRING_SUBSET_ORDERING); // 使用STRING_SUBSET_ORDERING作为偏序关系的比较器
    assertThat(poset, hasSize(0)); // 断言：验证新创建的偏序集合大小为0

    final StringBuilder buf = new StringBuilder(); // 创建字符串构建器，用于存储输出信息
    poset.out(buf); // 将偏序集合的内容输出到字符串构建器中
    TestUtil.assertEqualsVerbose( // 验证输出内容是否符合预期
        "PartiallyOrderedSet size: 0 elements: {\n" // 预期的输出内容：大小为0的空集合
            + "}", // 预期的输出内容结束
        buf.toString()); // 实际的输出内容

    poset.add("a"); // 向偏序集合中添加元素"a"
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.add("b"); // 向偏序集合中添加元素"b"
    printValidate(poset); // 打印并验证偏序集合的有效性

    poset.clear(); // 清空偏序集合中的所有元素
    assertThat(poset, hasSize(0)); // 断言：验证清空后的偏序集合大小为0
    poset.add(empty); // 向偏序集合中添加空字符串元素
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.add(abcd); // 向偏序集合中添加包含字符a、b、c、d的字符串
    printValidate(poset); // 打印并验证偏序集合的有效性
    assertThat(poset, hasSize(2)); // 断言：验证偏序集合大小为2
    assertThat(poset.getNonChildren(), hasToString("['abcd']")); // 断言：验证没有子元素的元素是"'abcd'"
    assertThat(poset.getNonParents(), hasToString("['']")); // 断言：验证没有父元素的元素是空字符串

    final String ab = "'ab'"; // 定义包含字符a、b的字符串变量
    poset.add(ab); // 向偏序集合中添加包含字符a、b的字符串
    printValidate(poset); // 打印并验证偏序集合的有效性
    assertThat(poset, hasSize(3)); // 断言：验证偏序集合大小为3
    assertThat(poset.getChildren(empty), hasToString("[]")); // 断言：验证空字符串没有子元素
    assertThat(poset.getParents(empty), hasToString("['ab']")); // 断言：验证空字符串的父元素是"'ab'"
    assertThat(poset.getChildren(abcd), hasToString("['ab']")); // 断言：验证"'abcd'"的子元素是"'ab'"
    assertThat(poset.getParents(abcd), hasToString("[]")); // 断言：验证"'abcd'"没有父元素
    assertThat(poset.getChildren(ab), hasToString("['']")); // 断言：验证"'ab'"的子元素是空字符串
    assertThat(poset.getParents(ab), hasToString("['abcd']")); // 断言：验证"'ab'"的父元素是"'abcd'"

    // "bcd" is child of "abcd" and parent of ""
    // 注释说明："'bcd'"是"'abcd'"的子元素，也是空字符串的父元素
    final String bcd = "'bcd'"; // 定义包含字符b、c、d的字符串变量
    assertThat(poset.getParents(bcd, true), hasToString("['abcd']")); // 断言：验证"'bcd'"的父元素（包括间接父元素）是"'abcd'"
    assertThat(poset.getParents(bcd, false), nullValue()); // 断言：验证"'bcd'"的直接父元素为null（因为"'bcd'"不在集合中）
    assertThat(poset.getParents(bcd), nullValue()); // 断言：验证"'bcd'"的父元素为null（因为"'bcd'"不在集合中）
    assertThat(poset.getChildren(bcd, true), hasToString("['']")); // 断言：验证"'bcd'"的子元素（包括间接子元素）是空字符串
    assertThat(poset.getChildren(bcd, false), nullValue()); // 断言：验证"'bcd'"的直接子元素为null（因为"'bcd'"不在集合中）
    assertThat(poset.getChildren(bcd), nullValue()); // 断言：验证"'bcd'"的子元素为null（因为"'bcd'"不在集合中）

    poset.add(bcd); // 向偏序集合中添加包含字符b、c、d的字符串
    printValidate(poset); // 打印并验证偏序集合的有效性
    assertTrue(poset.isValid(false)); // 断言：验证偏序集合的有效性（不进行详细检查）
    assertThat(poset.getChildren(bcd), hasToString("['']")); // 断言：验证"'bcd'"的子元素是空字符串
    assertThat(poset.getParents(bcd), hasToString("['abcd']")); // 断言：验证"'bcd'"的父元素是"'abcd'"
    assertThat(poset.getChildren(abcd), hasToString("['ab', 'bcd']")); // 断言：验证"'abcd'"的子元素是"'ab'"和"'bcd'"

    buf.setLength(0); // 清空字符串构建器
    poset.out(buf); // 将偏序集合的内容输出到字符串构建器中
    TestUtil.assertEqualsVerbose( // 验证输出内容是否符合预期
        "PartiallyOrderedSet size: 4 elements: {\n" // 预期的输出内容：大小为4的集合
            + "  'abcd' parents: [] children: ['ab', 'bcd']\n" // 预期的输出内容："'abcd'"的父元素为空，子元素为"'ab'"和"'bcd'"
            + "  'ab' parents: ['abcd'] children: ['']\n" // 预期的输出内容："'ab'"的父元素是"'abcd'"，子元素是空字符串
            + "  'bcd' parents: ['abcd'] children: ['']\n" // 预期的输出内容："'bcd'"的父元素是"'abcd'"，子元素是空字符串
            + "  '' parents: ['ab', 'bcd'] children: []\n" // 预期的输出内容：空字符串的父元素是"'ab'"和"'bcd'"，子元素为空
            + "}", // 预期的输出内容结束
        buf.toString()); // 实际的输出内容

    final String b = "'b'"; // 定义包含字符b的字符串变量

    // ancestors of an element not in the set
    // 注释说明：获取不在集合中的元素的祖先元素
    assertEqualsList("['ab', 'abcd', 'bcd']", poset.getAncestors(b)); // 断言：验证"'b'"的祖先元素是"'ab'"、"'abcd'"和"'bcd'"

    poset.add(b); // 向偏序集合中添加包含字符b的字符串
    printValidate(poset); // 打印并验证偏序集合的有效性
    assertThat(poset.getNonChildren(), hasToString("['abcd']")); // 断言：验证没有子元素的元素是"'abcd'"
    assertThat(poset.getNonParents(), hasToString("['']")); // 断言：验证没有父元素的元素是空字符串
    assertThat(poset.getChildren(b), hasToString("['']")); // 断言：验证"'b'"的子元素是空字符串
    assertEqualsList("['ab', 'bcd']", poset.getParents(b)); // 断言：验证"'b'"的父元素是"'ab'"和"'bcd'"
    assertThat(poset.getChildren(b), hasToString("['']")); // 断言：验证"'b'"的子元素是空字符串
    assertThat(poset.getChildren(abcd), hasToString("['ab', 'bcd']")); // 断言：验证"'abcd'"的子元素是"'ab'"和"'bcd'"
    assertThat(poset.getChildren(bcd), hasToString("['b']")); // 断言：验证"'bcd'"的子元素是"'b'"
    assertThat(poset.getChildren(ab), hasToString("['b']")); // 断言：验证"'ab'"的子元素是"'b'"
    assertEqualsList("['ab', 'abcd', 'bcd']", poset.getAncestors(b)); // 断言：验证"'b'"的祖先元素是"'ab'"、"'abcd'"和"'bcd'"

    // descendants and ancestors of an element with no descendants
    // 注释说明：获取没有子元素的元素的子元素和祖先元素
    assertThat(poset.getDescendants(empty), hasToString("[]")); // 断言：验证空字符串没有子元素
    assertEqualsList( // 断言：验证空字符串的祖先元素
        "['ab', 'abcd', 'b', 'bcd']", // 预期的祖先元素列表
        poset.getAncestors(empty)); // 实际的祖先元素

    // some more ancestors of missing elements
    // 注释说明：获取更多不在集合中的元素的祖先元素
    assertEqualsList("['abcd']", poset.getAncestors("'ac'")); // 断言：验证"'ac'"的祖先元素是"'abcd'"
    assertEqualsList("[]", poset.getAncestors("'z'")); // 断言：验证"'z'"的祖先元素为空（因为"'z'"不在任何元素的子集中）
    assertEqualsList("['ab', 'abcd']", poset.getAncestors("'a'")); // 断言：验证"'a'"的祖先元素是"'ab'"和"'abcd'"
  }

  @Test void testGetNonParentsOnLteIntPosetReturnsMinValue() { // 测试方法：测试在小于等于偏序关系中获取没有父元素的元素返回最小值
    PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用小于等于作为偏序关系
        new PartiallyOrderedSet<>((i, j) -> i <= j, Arrays.asList(20, 30, 40)); // 使用Lambda表达式定义小于等于偏序关系，初始化集合包含20、30、40
    assertThat(poset.getNonParents(), hasToString("[20]")); // 断言：验证没有父元素的元素是20（最小值）
  }

  @Test void testGetNonChildrenOnLteIntPosetReturnsMaxValue() { // 测试方法：测试在小于等于偏序关系中获取没有子元素的元素返回最大值
    PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用小于等于作为偏序关系
        new PartiallyOrderedSet<>((i, j) -> i <= j, Arrays.asList(20, 30, 40)); // 使用Lambda表达式定义小于等于偏序关系，初始化集合包含20、30、40
    assertThat(poset.getNonChildren(), hasToString("[40]")); // 断言：验证没有子元素的元素是40（最大值）
  }

  @Test void testGetNonParentsOnGteIntPosetReturnsMaxValue() { // 测试方法：测试在大于等于偏序关系中获取没有父元素的元素返回最大值
    PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用大于等于作为偏序关系
        new PartiallyOrderedSet<>((i, j) -> i >= j, Arrays.asList(20, 30, 40)); // 使用Lambda表达式定义大于等于偏序关系，初始化集合包含20、30、40
    assertThat(poset.getNonParents(), hasToString("[40]")); // 断言：验证没有父元素的元素是40（最大值）
  }

  @Test void testGetNonChildrenOnGteIntPosetReturnsMinValue() { // 测试方法：测试在大于等于偏序关系中获取没有子元素的元素返回最小值
    PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用大于等于作为偏序关系
        new PartiallyOrderedSet<>((i, j) -> i >= j, Arrays.asList(20, 30, 40)); // 使用Lambda表达式定义大于等于偏序关系，初始化集合包含20、30、40
    assertThat(poset.getNonChildren(), hasToString("[20]")); // 断言：验证没有子元素的元素是20（最小值）
  }

  @Test void testPosetTricky() { // 测试方法：测试一个复杂的偏序集合场景
    final PartiallyOrderedSet<String> poset = // 创建一个字符串类型的偏序集合，使用字符串子集偏序关系
        new PartiallyOrderedSet<>(STRING_SUBSET_ORDERING); // 使用STRING_SUBSET_ORDERING作为偏序关系的比较器

    // A tricky little poset with 4 elements:
    // {a <= ab and ac, b < ab, ab, ac}
    // 注释说明：一个复杂的小型偏序集合，包含4个元素：{a <= ab和ac, b < ab, ab, ac}
    poset.clear(); // 清空偏序集合中的所有元素
    poset.add("'a'"); // 向偏序集合中添加元素"'a'"
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.add("'b'"); // 向偏序集合中添加元素"'b'"
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.add("'ac'"); // 向偏序集合中添加元素"'ac'"
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.add("'ab'"); // 向偏序集合中添加元素"'ab'"
    printValidate(poset); // 打印并验证偏序集合的有效性
    assertThat(poset.getNonChildren(), hasToString("['ac', 'ab']")); // 断言：验证没有子元素的元素是"'ac'"和"'ab'"
    assertThat(poset.getNonParents(), hasToString("['a', 'b']")); // 断言：验证没有父元素的元素是"'a'"和"'b'"
  }

  @Test void testPosetBits() { // 测试方法：测试使用位包含关系的偏序集合
    final PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用位超集偏序关系
        new PartiallyOrderedSet<>(PartiallyOrderedSetTest::isBitSuperset); // 使用isBitSuperset方法作为偏序关系的比较器
    poset.add(2112); // 向偏序集合中添加2112（二进制为100001000000，表示第6位和第11位为1）
    poset.add(2240); // 向偏序集合中添加2240（二进制为100011000000，表示第6、7、11位为1）
    poset.add(2496); // 向偏序集合中添加2496（二进制为100111000000，表示第6、7、8、11位为1）
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.remove(2240); // 从偏序集合中移除2240
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.add(2240); // 向偏序集合中重新添加2240
    printValidate(poset); // 打印并验证偏序集合的有效性
  }

  @Tag("slow") // 标记为慢速测试
  @Test void testPosetBitsLarge() { // 测试方法：测试大规模的位包含关系偏序集合
    // It takes 80 seconds, and the computations are exactly the same every time
    // 注释说明：这个测试需要80秒，并且每次计算结果都相同
    final PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用位超集偏序关系
        new PartiallyOrderedSet<>(PartiallyOrderedSetTest::isBitSuperset); // 使用isBitSuperset方法作为偏序关系的比较器
    checkPosetBitsLarge(poset, 30000, 2921, 164782); // 调用辅助方法验证大规模偏序集合，参数为：集合、最大值30000、预期大小2921、预期父元素数量164782
  }

  @Tag("slow") // 标记为慢速测试
  @Test void testPosetBitsLarge2() { // 测试方法：测试大规模的位包含关系偏序集合（使用自定义的子元素和父元素生成器）
    final int n = 30000; // 定义最大值为30000
    final PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用位超集偏序关系
        new PartiallyOrderedSet<>(PartiallyOrderedSetTest::isBitSuperset, // 使用isBitSuperset方法作为偏序关系的比较器
            (Function<Integer, Iterable<Integer>>) i -> { // 定义子元素生成器，用于生成给定元素的直接子元素
              int r = requireNonNull(i, "i"); // bits not yet cleared // 获取元素的值，确保不为null
              final List<Integer> list = new ArrayList<>(); // 创建列表用于存储子元素
              for (int z = 1; r != 0; z <<= 1) { // 遍历元素的每一位，从最低位开始
                if ((i & z) != 0) { // 如果当前位为1
                  list.add(i ^ z); // 将当前位清零后的值添加到子元素列表中
                  r ^= z; // 清除当前位
                }
              }
              return list; // 返回子元素列表
            },
            i -> { // 定义父元素生成器，用于生成给定元素的直接父元素
              requireNonNull(i, "i"); // 确保元素不为null
              final List<Integer> list = new ArrayList<>(); // 创建列表用于存储父元素
              for (int z = 1; z <= n; z <<= 1) { // 遍历所有可能的位，从最低位开始
                if ((i & z) == 0) { // 如果当前位为0
                  list.add(i | z); // 将当前位设置为1后的值添加到父元素列表中
                }
              }
              return list; // 返回父元素列表
            });
    checkPosetBitsLarge(poset, n, 2921, 11961); // 调用辅助方法验证大规模偏序集合，参数为：集合、最大值n、预期大小2921、预期父元素数量11961
  }

  void checkPosetBitsLarge(PartiallyOrderedSet<Integer> poset, int n, // 辅助方法：验证大规模位包含关系偏序集合
      int expectedSize, int expectedParentCount) { // 参数：偏序集合、最大值n、预期大小、预期父元素数量
    final Random random = new Random(1); // 使用固定种子1创建随机数生成器，保证测试结果可重复
    int count = 0; // 计数器，记录成功添加的元素数量
    int parentCount = 0; // 计数器，记录父元素的总数量
    for (int i = 0; i < n; i++) { // 遍历0到n-1
      if (random.nextInt(10) == 0) { // 以10%的概率执行添加操作
        if (poset.add(random.nextInt(n * 2))) { // 尝试向偏序集合中添加一个随机数（范围0到2n-1）
          ++count; // 如果添加成功，计数器加1
        }
      }
      final List<Integer> parents = // 获取随机数的父元素列表
          poset.getParents(random.nextInt(n * 2), true); // 参数：随机数、true表示包含间接父元素
      parentCount += parents.size(); // 累加父元素数量
    }
    assertThat(poset, hasSize(count)); // 断言：验证偏序集合的大小等于计数器count
    assertThat(poset, hasSize(expectedSize)); // 断言：验证偏序集合的大小等于预期大小expectedSize
    assertThat(parentCount, is(expectedParentCount)); // 断言：验证父元素总数等于预期值expectedParentCount
  }

  @Test void testPosetBitsRemoveParent() { // 测试方法：测试移除父元素后的偏序集合
    final PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合，使用位超集偏序关系
        new PartiallyOrderedSet<>(PartiallyOrderedSetTest::isBitSuperset); // 使用isBitSuperset方法作为偏序关系的比较器
    poset.add(66); // 向偏序集合中添加66（二进制为1000010，表示第2位和第6位为1）
    poset.add(68); // 向偏序集合中添加68（二进制为1000100，表示第3位和第6位为1）
    poset.add(72); // 向偏序集合中添加72（二进制为1001000，表示第4位和第6位为1）
    poset.add(64); // 向偏序集合中添加64（二进制为1000000，表示第6位为1）
    printValidate(poset); // 打印并验证偏序集合的有效性
    poset.remove(64); // 从偏序集合中移除64（第6位的元素）
    printValidate(poset); // 打印并验证偏序集合的有效性
  }

  @Test void testDivisorPoset() { // 测试方法：测试使用整除关系的偏序集合
    PartiallyOrderedSet<Integer> integers = // 创建一个整数类型的偏序集合，使用整除偏序关系
        new PartiallyOrderedSet<>(PartiallyOrderedSetTest::isDivisor, // 使用isDivisor方法作为偏序关系的比较器
            range(1, 1000)); // 初始化集合包含1到999的整数
    assertThat(new TreeSet<>(integers.getDescendants(120)), // 断言：验证120的子元素（约数）列表
        hasToString("[1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 24, 30, 40, 60]")); // 预期的子元素列表（120的所有约数）
    assertThat(new TreeSet<>(integers.getAncestors(120)), // 断言：验证120的祖先元素（倍数）列表
        hasToString("[240, 360, 480, 600, 720, 840, 960]")); // 预期的祖先元素列表（120在1-999范围内的所有倍数）
    assertThat(integers.getDescendants(1), empty()); // 断言：验证1没有子元素（因为1是最小的约数）
    assertThat(integers.getAncestors(1), hasSize(998)); // 断言：验证1有998个祖先元素（2到999的所有整数）
    assertThat(integers.isValid(true), is(true)); // 断言：验证偏序集合的有效性（进行详细检查）
  }

  @Test void testDivisorSeries() { // 测试方法：测试使用整除关系的序列偏序集合
    checkPoset(PartiallyOrderedSetTest::isDivisor, DEBUG, range(1, SCALE * 3), // 调用辅助方法验证偏序集合，参数：整除偏序关系、调试标志、1到SCALE*3-1的序列、不进行移除
        false); // 参数：不进行移除操作
  }

  @Test void testDivisorRandom() { // 测试方法：测试使用整除关系的随机偏序集合
    boolean ok = false; // 标志变量，用于标记测试是否成功
    try { // 尝试执行测试
      checkPoset(PartiallyOrderedSetTest::isDivisor, DEBUG, // 调用辅助方法验证偏序集合，参数：整除偏序关系、调试标志、随机生成的整数序列、不进行移除
          random(random, SCALE, SCALE * 3), false); // 参数：随机生成的整数序列、不进行移除操作
      ok = true; // 如果测试成功，设置标志为true
    } finally { // 无论测试成功与否，都执行以下代码
      if (!ok) { // 如果测试失败
        System.out.println("Random seed: " + seed); // 输出随机数种子，便于调试和重现问题
      }
    }
  }

  @Test void testDivisorRandomWithRemoval() { // 测试方法：测试使用整除关系的随机偏序集合（包含移除操作）
    boolean ok = false; // 标志变量，用于标记测试是否成功
    try { // 尝试执行测试
      checkPoset(PartiallyOrderedSetTest::isDivisor, DEBUG, // 调用辅助方法验证偏序集合，参数：整除偏序关系、调试标志、随机生成的整数序列、进行移除
          random(random, SCALE, SCALE * 3), true); // 参数：随机生成的整数序列、进行移除操作
      ok = true; // 如果测试成功，设置标志为true
    } finally { // 无论测试成功与否，都执行以下代码
      if (!ok) { // 如果测试失败
        System.out.println("Random seed: " + seed); // 输出随机数种子，便于调试和重现问题
      }
    }
  }

  @Test void testDivisorInverseSeries() { // 测试方法：测试使用反向整除关系的序列偏序集合
    checkPoset(PartiallyOrderedSetTest::isDivisorInverse, DEBUG, // 调用辅助方法验证偏序集合，参数：反向整除偏序关系、调试标志、1到SCALE*3-1的序列、不进行移除
        range(1, SCALE * 3), false); // 参数：不进行移除操作
  }

  @Test void testDivisorInverseRandom() { // 测试方法：测试使用反向整除关系的随机偏序集合
    boolean ok = false; // 标志变量，用于标记测试是否成功
    try { // 尝试执行测试
      checkPoset(PartiallyOrderedSetTest::isDivisorInverse, DEBUG, random(random, SCALE, SCALE * 3), // 调用辅助方法验证偏序集合，参数：反向整除偏序关系、调试标志、随机生成的整数序列、不进行移除
          false); // 参数：不进行移除操作
      ok = true; // 如果测试成功，设置标志为true
    } finally { // 无论测试成功与否，都执行以下代码
      if (!ok) { // 如果测试失败
        System.out.println("Random seed: " + seed); // 输出随机数种子，便于调试和重现问题
      }
    }
  }

  @Test void testDivisorInverseRandomWithRemoval() { // 测试方法：测试使用反向整除关系的随机偏序集合（包含移除操作）
    boolean ok = false; // 标志变量，用于标记测试是否成功
    try { // 尝试执行测试
      checkPoset(PartiallyOrderedSetTest::isDivisorInverse, DEBUG, random(random, SCALE, SCALE * 3), // 调用辅助方法验证偏序集合，参数：反向整除偏序关系、调试标志、随机生成的整数序列、进行移除
          true); // 参数：进行移除操作
      ok = true; // 如果测试成功，设置标志为true
    } finally { // 无论测试成功与否，都执行以下代码
      if (!ok) { // 如果测试失败
        System.out.println("Random seed: " + seed); // 输出随机数种子，便于调试和重现问题
      }
    }
  }

  @Test void testSubsetSeries() { // 测试方法：测试使用位子集关系的序列偏序集合
    checkPoset(PartiallyOrderedSetTest::isBitSubset, DEBUG, range(1, SCALE / 2), false); // 调用辅助方法验证偏序集合，参数：位子集偏序关系、调试标志、1到SCALE/2-1的序列、不进行移除
  }

  @Test void testSubsetRandom() { // 测试方法：测试使用位子集关系的随机偏序集合
    boolean ok = false; // 标志变量，用于标记测试是否成功
    try { // 尝试执行测试
      checkPoset(PartiallyOrderedSetTest::isBitSubset, DEBUG, // 调用辅助方法验证偏序集合，参数：位子集偏序关系、调试标志、随机生成的整数序列、不进行移除
          random(random, SCALE / 4, SCALE), false); // 参数：随机生成的整数序列、不进行移除操作
      ok = true; // 如果测试成功，设置标志为true
    } finally { // 无论测试成功与否，都执行以下代码
      if (!ok) { // 如果测试失败
        System.out.println("Random seed: " + seed); // 输出随机数种子，便于调试和重现问题
      }
    }
  }

  private <E> void printValidate(PartiallyOrderedSet<E> poset) { // 辅助方法：打印并验证偏序集合的有效性
    if (DEBUG) { // 如果调试标志为true
      dump(poset); // 输出偏序集合的详细信息
    }
    assertTrue(poset.isValid(DEBUG)); // 断言：验证偏序集合的有效性（根据调试标志决定是否进行详细检查）
  }

  public void checkPoset( // 辅助方法：验证偏序集合的各种操作
      PartiallyOrderedSet.Ordering<Integer> ordering, // 参数：偏序关系比较器
      boolean debug, // 参数：调试标志
      Iterable<Integer> generator, // 参数：元素生成器（用于生成要添加到集合中的元素）
      boolean remove) { // 参数：是否进行移除操作
    final PartiallyOrderedSet<Integer> poset = // 创建一个整数类型的偏序集合
        new PartiallyOrderedSet<>(ordering); // 使用指定的偏序关系比较器
    int n = 0; // 计数器，记录添加的元素数量
    int z = 0; // 计数器，用于控制移除操作的频率
    if (debug) { // 如果调试标志为true
      dump(poset); // 输出偏序集合的详细信息
    }
    for (int i : generator) { // 遍历元素生成器生成的每个元素
      if (remove && z++ % 2 == 0) { // 如果需要进行移除操作，且当前元素是偶数个
        if (debug) { // 如果调试标志为true
          System.out.println("remove " + i); // 输出移除操作的日志
        }
        poset.remove(i); // 从偏序集合中移除当前元素
        if (debug) { // 如果调试标志为true
          dump(poset); // 输出偏序集合的详细信息
        }
        continue; // 跳过后续操作，继续处理下一个元素
      }
      if (debug) { // 如果调试标志为true
        System.out.println("add " + i); // 输出添加操作的日志
      }
      poset.add(i); // 向偏序集合中添加当前元素
      if (debug) { // 如果调试标志为true
        dump(poset); // 输出偏序集合的详细信息
      }
      assertThat(poset, hasSize(++n)); // 断言：验证偏序集合的大小等于计数器n（每次添加后加1）
      if (i < 100) { // 如果当前元素小于100
        if (!poset.isValid(false)) { // 如果偏序集合无效（不进行详细检查）
          dump(poset); // 输出偏序集合的详细信息，便于调试
        }
        assertTrue(poset.isValid(true)); // 断言：验证偏序集合的有效性（进行详细检查）
      }
    }
    assertTrue(poset.isValid(true)); // 断言：验证偏序集合的有效性（进行详细检查）

    final StringBuilder buf = new StringBuilder(); // 创建字符串构建器
    poset.out(buf); // 将偏序集合的内容输出到字符串构建器中
    assertTrue(buf.length() > 0); // 断言：验证输出内容不为空
  }

  private <E> void dump(PartiallyOrderedSet<E> poset) { // 辅助方法：输出偏序集合的详细信息
    final StringBuilder buf = new StringBuilder(); // 创建字符串构建器
    poset.out(buf); // 将偏序集合的内容输出到字符串构建器中
    System.out.println(buf); // 输出偏序集合的详细信息到控制台
  }

  private static Collection<Integer> range( // 辅助方法：生成指定范围内的整数序列
      final int start, final int end) { // 参数：起始值（包含）、结束值（不包含）
    return new AbstractList<Integer>() { // 返回一个抽象列表的实现
      @Override public Integer get(int index) { // 重写get方法，获取指定索引的元素
        return start + index; // 返回起始值加上索引的值
      }

      @Override public int size() { // 重写size方法，获取列表的大小
        return end - start; // 返回结束值减去起始值
      }
    };
  }

  private static Iterable<Integer> random( // 辅助方法：生成指定数量的随机整数序列
      Random random, final int size, final int max) { // 参数：随机数生成器、序列大小、最大值
    final Set<Integer> set = new LinkedHashSet<>(); // 创建一个LinkedHashSet，用于存储随机数（保持插入顺序且不重复）
    while (set.size() < size) { // 当集合的大小小于指定大小时，继续循环
      set.add(random.nextInt(max) + 1); // 生成一个1到max之间的随机数，添加到集合中
    }
    return set; // 返回随机数集合
  }

  private static void assertEqualsList(String expected, List<String> ss) { // 辅助方法：验证字符串列表是否等于预期值
    assertThat(new TreeSet<>(ss), hasToString(expected)); // 断言：验证将列表转换为TreeSet后的字符串表示是否等于预期值
  }

}