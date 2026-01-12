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
package org.apache.calcite.runtime; // 包声明：org.apache.calcite.runtime包，包含运行时相关的类
import org.apache.calcite.linq4j.MemoryFactory; // 导入MemoryFactory类，用于内存管理工厂
import org.apache.calcite.test.Matchers; // 导入Matchers类，提供测试匹配器工具

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.hamcrest.core.Is; // 导入Hamcrest的Is匹配器
import org.junit.jupiter.api.Test; // 导入JUnit5的Test注解，用于标记测试方法

import java.util.AbstractList; // 导入抽象列表类
import java.util.List; // 导入列表接口
import java.util.stream.Collectors; // 导入流收集器工具类

import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具
import static org.hamcrest.Matchers.hasToString; // 导入hasToString匹配器

/** Unit tests for {@link Automaton}. */ // 类注释：Automaton类的单元测试类
class AutomatonTest { // 测试类定义：AutomatonTest，用于测试Automaton（自动机）类的各种功能

  /** Creates a Matcher that matches a list of
   * {@link org.apache.calcite.runtime.Matcher.PartialMatch} if they
   * a formatted to a given string. */ // 方法注释：创建一个Matcher，用于匹配部分匹配列表，检查它们的格式化字符串是否符合给定的值
  private static <E> org.hamcrest.Matcher<List<Matcher.PartialMatch<E>>> // 泛型方法定义：返回一个Hamcrest Matcher，用于匹配部分匹配列表
      isMatchList(final String value) { // 方法名：isMatchList，参数value是期望的字符串表示
    return Matchers.compose(Is.is(value), // 返回组合匹配器：使用Is.is(value)作为基础匹配器
        match -> match.stream().map(pm -> pm.rows).collect(Collectors.toList()) // 转换函数：将部分匹配列表转换为行列表的字符串表示
            .toString()); // 将列表转换为字符串
  }

  @Test void testSimple() { // 测试方法：testSimple，测试简单的模式匹配功能
    // pattern(a) // 注释：定义一个简单的模式，只包含符号"a"
    final Pattern p = Pattern.builder().symbol("a").build(); // 创建模式：使用构建器创建一个只包含符号"a"的模式
    assertThat(p, hasToString("a")); // 断言：验证模式的字符串表示为"a"

    final String[] rows = {"", "a", "", "a"}; // 测试数据：定义输入行数组，包含空字符串和包含"a"的字符串
    final Matcher<String> matcher = // 创建匹配器：基于模式创建String类型的匹配器
        Matcher.<String>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get().contains("a")) // 添加符号"a"的匹配规则：检查字符串是否包含"a"
            .build(); // 构建匹配器
    final String expected = "[[a], [a]]"; // 期望结果：两个部分匹配，每个匹配包含一个包含"a"的行

    assertThat(matcher.match(rows), isMatchList(expected)); // 断言：验证匹配结果与期望结果一致
  }

  @Test void testSequence() { // 测试方法：testSequence，测试序列模式匹配功能
    // pattern(a b) // 注释：定义一个序列模式，要求符号"a"后面跟着符号"b"
    final Pattern p = // 创建模式：使用构建器创建序列模式
        Pattern.builder().symbol("a").symbol("b").seq().build(); // 添加符号"a"和"b"，然后使用seq()将它们组合成序列
    assertThat(p, hasToString("a b")); // 断言：验证模式的字符串表示为"a b"

    final String[] rows = {"", "a", "", "ab", "a", "ab", "b", "b"}; // 测试数据：包含各种组合的输入行
    final Matcher<String> matcher = // 创建匹配器：基于序列模式创建String类型的匹配器
        Matcher.<String>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get().contains("a")) // 添加符号"a"的匹配规则
            .add("b", s -> s.get().contains("b")) // 添加符号"b"的匹配规则
            .build(); // 构建匹配器
    final String expected = "[[a, ab], [ab, b]]"; // 期望结果：两个序列匹配，第一个是"a"后跟"ab"，第二个是"ab"后跟"b"
    assertThat(matcher.match(rows), isMatchList(expected)); // 断言：验证匹配结果与期望结果一致
  }

  @Test void testStar() { // 测试方法：testStar，测试星号（零次或多次）模式匹配功能
    // pattern(a* b) // 注释：定义一个模式，要求零个或多个"a"后面跟着一个"b"
    final Pattern p = Pattern.builder() // 创建模式：使用构建器创建模式
        .symbol("a").star() // 添加符号"a"并应用star()操作符（零次或多次）
        .symbol("b").seq().build(); // 添加符号"b"并使用seq()将它们组合
    assertThat(p, hasToString("(a)* b")); // 断言：验证模式的字符串表示为"(a)* b"

    final String[] rows = {"", "a", "", "b", "", "ab", "a", "ab", "b", "b"}; // 测试数据：包含各种组合的输入行
    final Matcher<String> matcher = // 创建匹配器：基于模式创建String类型的匹配器
        Matcher.<String>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get().contains("a")) // 添加符号"a"的匹配规则
            .add("b", s -> s.get().contains("b")) // 添加符号"b"的匹配规则
            .build(); // 构建匹配器
    final String expected = "[[b], [ab], [ab], [ab, a, ab], [a, ab], [b], [ab, b], [ab, a, ab, b], " // 期望结果：多个匹配，包括零个"a"和多个"a"的情况
        + "[a, ab, b], [b]]"; // 期望结果的续行
    assertThat(matcher.match(rows), isMatchList(expected)); // 断言：验证匹配结果与期望结果一致
  }

  @Test void testPlus() { // 测试方法：testPlus，测试加号（一次或多次）模式匹配功能
    // pattern(a+ b) // 注释：定义一个模式，要求一个或多个"a"后面跟着一个"b"
    final Pattern p = Pattern.builder() // 创建模式：使用构建器创建模式
        .symbol("a").plus() // 添加符号"a"并应用plus()操作符（一次或多次）
        .symbol("b").seq().build(); // 添加符号"b"并使用seq()将它们组合
    assertThat(p, hasToString("(a)+ b")); // 断言：验证模式的字符串表示为"(a)+ b"

    final String[] rows = {"", "a", "", "b", "", "ab", "a", "ab", "b", "b"}; // 测试数据：包含各种组合的输入行
    final Matcher<String> matcher = // 创建匹配器：基于模式创建String类型的匹配器
        Matcher.<String>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get().contains("a")) // 添加符号"a"的匹配规则
            .add("b", s -> s.get().contains("b")) // 添加符号"b"的匹配规则
            .build(); // 构建匹配器
    final String expected = "[[ab, a, ab], [a, ab], [ab, b], [ab, a, ab, b], [a, ab, b]]"; // 期望结果：只包含至少一个"a"的匹配
    assertThat(matcher.match(rows), isMatchList(expected)); // 断言：验证匹配结果与期望结果一致
  }

  @Test void testOr() { // 测试方法：testOr，测试或运算模式匹配功能
    // pattern(a+ b) // 注释：定义一个模式，要求"a"或"b"（实际上是a|b）
    final Pattern p = Pattern.builder() // 创建模式：使用构建器创建模式
        .symbol("a") // 添加符号"a"
        .symbol("b").or() // 添加符号"b"并应用or()操作符
        .build(); // 构建模式
    assertThat(p, hasToString("a|b")); // 断言：验证模式的字符串表示为"a|b"

    final String[] rows = {"", "a", "", "b", "", "ab", "a", "ab", "b", "b"}; // 测试数据：包含各种组合的输入行
    final Matcher<String> matcher = // 创建匹配器：基于模式创建String类型的匹配器
        Matcher.<String>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get().contains("a")) // 添加符号"a"的匹配规则
            .add("b", s -> s.get().contains("b")) // 添加符号"b"的匹配规则
            .build(); // 构建匹配器
    final String expected = "[[a], [b], [ab], [ab], [a], [ab], [ab], [b], [b]]"; // 期望结果：所有包含"a"或"b"的匹配
    assertThat(matcher.match(rows), isMatchList(expected)); // 断言：验证匹配结果与期望结果一致
  }

  @Test void testOptional() { // 测试方法：testOptional，测试可选模式匹配功能
    // pattern(a+ b) // 注释：定义一个模式，要求"a"后跟可选的"b"，再跟"c"
    final Pattern p = Pattern.builder() // 创建模式：使用构建器创建模式
        .symbol("a") // 添加符号"a"
        .symbol("b").optional().seq() // 添加符号"b"并应用optional()操作符（零次或一次），然后seq()
        .symbol("c").seq() // 添加符号"c"并应用seq()
        .build(); // 构建模式
    assertThat(p, hasToString("a b? c")); // 断言：验证模式的字符串表示为"a b? c"

    final String rows = "acabcabbc"; // 测试数据：包含各种字符组合的字符串
    final Matcher<Character> matcher = // 创建匹配器：基于模式创建Character类型的匹配器
        Matcher.<Character>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get() == 'a') // 添加符号"a"的匹配规则：检查字符是否为'a'
            .add("b", s -> s.get() == 'b') // 添加符号"b"的匹配规则：检查字符是否为'b'
            .add("c", s -> s.get() == 'c') // 添加符号"c"的匹配规则：检查字符是否为'c'
            .build(); // 构建匹配器
    final String expected = "[[a, c], [a, b, c]]"; // 期望结果：两个匹配，一个不带"b"，一个带"b"
    assertThat(matcher.match(chars(rows)), isMatchList(expected)); // 断言：验证匹配结果与期望结果一致
  }

  @Test void testRepeat() { // 测试方法：testRepeat，测试重复模式匹配功能
    // pattern(a b{0, 2} c) // 注释：定义一个模式，要求"a"后跟0到2个"b"，再跟"c"
    checkRepeat(0, 2, "a (b){0, 2} c", "[[a, c], [a, b, c], [a, b, b, c]]"); // 调用checkRepeat方法测试0到2次重复
    // pattern(a b{0, 1} c) // 注释：定义一个模式，要求"a"后跟0到1个"b"，再跟"c"
    checkRepeat(0, 1, "a (b){0, 1} c", "[[a, c], [a, b, c]]"); // 调用checkRepeat方法测试0到1次重复
    // pattern(a b{1, 1} c) // 注释：定义一个模式，要求"a"后跟1个"b"，再跟"c"
    checkRepeat(1, 1, "a (b){1} c", "[[a, b, c]]"); // 调用checkRepeat方法测试1次重复
    // pattern(a b{1,3} c) // 注释：定义一个模式，要求"a"后跟1到3个"b"，再跟"c"
    checkRepeat(1, 3, "a (b){1, 3} c", // 调用checkRepeat方法测试1到3次重复
        "[[a, b, c], [a, b, b, c], [a, b, b, b, c]]"); // 期望结果：1到3个"b"的所有可能组合
    // pattern(a b{1,2} c) // 注释：定义一个模式，要求"a"后跟1到2个"b"，再跟"c"
    checkRepeat(1, 2, "a (b){1, 2} c", "[[a, b, c], [a, b, b, c]]"); // 调用checkRepeat方法测试1到2次重复
    // pattern(a b{2,3} c) // 注释：定义一个模式，要求"a"后跟2到3个"b"，再跟"c"
    checkRepeat(2, 3, "a (b){2, 3} c", "[[a, b, b, c], [a, b, b, b, c]]"); // 调用checkRepeat方法测试2到3次重复
  }

  private void checkRepeat(int minRepeat, int maxRepeat, String pattern, // 私有方法：checkRepeat，用于测试重复模式
      String expected) { // 参数：minRepeat最小重复次数，maxRepeat最大重复次数，pattern模式字符串，expected期望结果
    final Pattern p = Pattern.builder() // 创建模式：使用构建器创建模式
        .symbol("a") // 添加符号"a"
        .symbol("b").repeat(minRepeat, maxRepeat).seq() // 添加符号"b"并应用repeat(minRepeat, maxRepeat)操作符
        .symbol("c").seq() // 添加符号"c"并应用seq()
        .build(); // 构建模式
    assertThat(p, hasToString(pattern)); // 断言：验证模式的字符串表示与给定的pattern参数一致

    final String rows = "acabcabbcabbbcabbbbcabdbc"; // 测试数据：包含各种字符组合的字符串
    final Matcher<Character> matcher = // 创建匹配器：基于模式创建Character类型的匹配器
        Matcher.<Character>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get() == 'a') // 添加符号"a"的匹配规则
            .add("b", s -> s.get() == 'b') // 添加符号"b"的匹配规则
            .add("c", s -> s.get() == 'c') // 添加符号"c"的匹配规则
            .build(); // 构建匹配器
    assertThat(matcher.match(chars(rows)), isMatchList(expected)); // 断言：验证匹配结果与期望结果一致
  }

  @Test void testRepeatComposite() { // 测试方法：testRepeatComposite，测试复合重复模式匹配功能
    // pattern(a (b a){1, 2} c) // 注释：定义一个模式，要求"a"后跟1到2个"(b a)"序列，再跟"c"
    final Pattern p = Pattern.builder() // 创建模式：使用构建器创建模式
        .symbol("a") // 添加符号"a"
        .symbol("b").symbol("a").seq() // 添加符号"b"和"a"，然后使用seq()将它们组合成"(b a)"序列
        .repeat(1, 2).seq() // 应用repeat(1, 2)操作符，允许1到2次重复，然后seq()
        .symbol("c").seq() // 添加符号"c"并应用seq()
        .build(); // 构建模式
    assertThat(p, hasToString("a (b a){1, 2} c")); // 断言：验证模式的字符串表示为"a (b a){1, 2} c"

    final String rows = "acabcabbcabbbcabbbbcabdbcabacababcababac"; // 测试数据：包含各种字符组合的字符串
    final Matcher<Character> matcher = // 创建匹配器：基于模式创建Character类型的匹配器
        Matcher.<Character>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("a", s -> s.get() == 'a') // 添加符号"a"的匹配规则
            .add("b", s -> s.get() == 'b') // 添加符号"b"的匹配规则
            .add("c", s -> s.get() == 'c') // 添加符号"c"的匹配规则
            .build(); // 构建匹配器
    assertThat(matcher.match(chars(rows)), // 断言：验证匹配结果
        isMatchList("[[a, b, a, c], [a, b, a, c], [a, b, a, b, a, c]]")); // 期望结果：包含1次和2次"(b a)"重复的匹配
  }

  @Test void testResultWithLabels() { // 测试方法：testResultWithLabels，测试带标签的结果匹配功能
    // pattern(a) // 注释：定义一个序列模式，包含符号"A"和"B"
    final Pattern p = Pattern.builder() // 创建模式：使用构建器创建模式
        .symbol("A") // 添加符号"A"
        .symbol("B").seq() // 添加符号"B"并使用seq()将它们组合成序列
        .build(); // 构建模式
    assertThat(p, hasToString("A B")); // 断言：验证模式的字符串表示为"A B"

    final String[] rows = {"", "a", "ab", "a", "b"}; // 测试数据：包含各种组合的输入行
    final Matcher<String> matcher = // 创建匹配器：基于模式创建String类型的匹配器
        Matcher.<String>builder(p.toAutomaton()) // 使用模式转换为自动机来构建匹配器
            .add("A", s -> s.get().contains("a")) // 添加符号"A"的匹配规则：检查字符串是否包含"a"
            .add("B", s -> s.get().contains("b")) // 添加符号"B"的匹配规则：检查字符串是否包含"b"
            .build(); // 构建匹配器
    final Matcher.PartitionState<String> partitionState = // 创建分区状态：用于跟踪匹配过程中的状态
        matcher.createPartitionState(0, 0); // 调用createPartitionState方法创建初始分区状态
    final ImmutableList.Builder<Matcher.PartialMatch<String>> builder = // 创建不可变列表构建器：用于收集部分匹配结果
        ImmutableList.builder(); // 初始化构建器
    MemoryFactory<String> memoryFactory = new MemoryFactory<>(0, 0); // 创建内存工厂：用于管理内存分配
    for (String row : rows) { // 遍历每一行输入数据
      memoryFactory.add(row); // 将当前行添加到内存工厂
      builder.addAll( // 将匹配结果添加到构建器
          matcher.matchOneWithSymbols(memoryFactory.create(), partitionState)); // 对当前行进行匹配，使用符号标签
    }
    assertThat(builder.build(), // 断言：验证构建的匹配结果列表
        hasToString("[[(A, a), (B, ab)], [(A, a), (B, b)]]")); // 期望结果：带符号标签的匹配项，显示符号名称和实际匹配的行
  }

  /** Converts a string into an iterable collection of its characters. */ // 方法注释：将字符串转换为其字符的可迭代集合
  private static Iterable<Character> chars(String s) { // 私有静态方法：chars，将字符串转换为字符的Iterable
    return new AbstractList<Character>() { // 返回匿名AbstractList实现：提供对字符串字符的列表式访问
      @Override public Character get(int index) { // 重写get方法：获取指定索引处的字符
        return s.charAt(index); // 返回字符串中指定索引的字符
      }

      @Override public int size() { // 重写size方法：返回字符串的长度
        return s.length(); // 返回字符串的长度
      }
    };
  }
} // 类结束：AutomatonTest类定义结束
