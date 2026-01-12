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
// Apache许可证头,声明版权和使用许可
package org.apache.calcite.test; // 包声明,属于org.apache.calcite.test测试包

// 导入Calcite核心类:RelOptUtil用于关系表达式工具方法,RelNode表示关系表达式节点
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelValidityChecker; // 关系表达式有效性检查器
import org.apache.calcite.rel.hint.Hintable; // 可提示接口,用于关系提示
import org.apache.calcite.rex.RexNode; // 行表达式节点,表示SQL表达式
import org.apache.calcite.sql.SqlExplainLevel; // SQL解释级别枚举
import org.apache.calcite.util.TestUtil; // 测试工具类
import org.apache.calcite.util.Util; // 通用工具类

// 导入Google Guava集合工具类
import com.google.common.collect.Lists; // Lists工具类,提供便捷的List创建方法
import com.google.common.collect.RangeSet; // 范围集合接口

// 导入API Guard注解,用于标记API稳定性
import org.apiguardian.api.API;
// 导入Hamcrest匹配器框架核心类
import org.hamcrest.BaseMatcher; // 基础匹配器抽象类
import org.hamcrest.CustomTypeSafeMatcher; // 自定义类型安全匹配器
import org.hamcrest.Description; // 匹配器描述接口
import org.hamcrest.Matcher; // 匹配器接口
import org.hamcrest.TypeSafeMatcher; // 类型安全匹配器基类
import org.hamcrest.core.Is; // Is匹配器,用于相等性判断
import org.hamcrest.core.StringContains; // 字符串包含匹配器

// 导入Java标准库类
import java.lang.reflect.Array; // 反射数组工具类
import java.nio.charset.Charset; // 字符集类
import java.sql.ResultSet; // JDBC结果集接口
import java.sql.SQLException; // SQL异常类
import java.util.ArrayList; // 动态数组列表
import java.util.Arrays; // 数组工具类
import java.util.Collections; // 集合工具类
import java.util.List; // 列表接口
import java.util.Objects; // 对象工具类,提供null安全方法
import java.util.function.Function; // 函数式接口,表示一个转换函数
import java.util.regex.Pattern; // 正则表达式编译类
import java.util.stream.StreamSupport; // 流支持工具类

// 静态导入,简化方法调用
import static com.google.common.base.Preconditions.checkArgument; // 参数校验工具
import static com.google.common.collect.ImmutableList.toImmutableList; // 转换为不可变列表

import static org.hamcrest.CoreMatchers.equalTo; // 相等匹配器
import static org.hamcrest.CoreMatchers.is; // Is匹配器
import static org.hamcrest.Matchers.closeTo; // 近似匹配器

/**
 * Matchers for testing SQL queries.
 * SQL查询测试的匹配器工具类
 * 
 * 这个类提供了大量的Hamcrest Matcher实现,用于在单元测试中验证SQL查询结果
 * 主要功能包括:
 * 1. 结果集匹配:验证JDBC ResultSet返回的数据是否符合预期
 * 2. 关系表达式匹配:验证RelNode(关系代数树)的结构和内容
 * 3. 行表达式匹配:验证RexNode(行表达式)的内容
 * 4. 数值匹配:验证数值是否在指定范围内或近似相等
 * 5. 字符串匹配:处理跨平台换行符问题
 * 6. 集合匹配:验证集合元素(支持无序比较)
 * 
 * 使用场景:
 * - Calcite集成测试中验证SQL执行结果
 * - 验证查询优化器生成的执行计划
 * - 测试SQL到关系代数的转换结果
 * - 验证数据类型转换和表达式求值
 * 
 * 设计特点:
 * - 基于Hamcrest框架,提供流畅的断言语法
 * - 支持跨平台测试(处理Windows/Unix换行符差异)
 * - 提供类型安全的匹配器实现
 * - 使用ThreadLocal传递实际结果用于错误描述
 */
public class Matchers { // 测试匹配器工具类,所有方法都是静态工厂方法

  // 正则表达式模式,用于匹配关系节点ID,格式如", id = 123"
  // 用于去除执行计划中不稳定的节点ID,使测试更可靠
  private static final Pattern PATTERN = Pattern.compile(", id = [0-9]+"); // 编译节点ID匹配模式

  /** A small positive value. */
  // 一个小的正数值,用于浮点数近似比较的容差值
  // 在比较两个浮点数是否"几乎相等"时,如果差值小于EPSILON则认为相等
  public static final double EPSILON = 1.0e-5; // 浮点数比较的容差值(0.00001)

  private Matchers() {} // 私有构造函数,防止实例化(工具类模式)

  /** Allows passing the actual result from the {@code matchesSafely} method to
   * the {@code describeMismatchSafely} method that will show the difference. */
  // 线程局部变量,用于在matchesSafely方法和describeMismatchSafely方法之间传递实际结果
  // 当匹配失败时,需要将实际值传递给错误描述方法,以便显示差异
  // 使用ThreadLocal确保多线程环境下每个线程有自己独立的实际值
  private static final ThreadLocal<Object> THREAD_ACTUAL = new ThreadLocal<>(); // 线程局部变量,存储实际匹配结果

  /**
   * Creates a matcher that matches if the examined result set returns the
   * given collection of rows in some order.
   * 创建一个匹配器,验证结果集是否返回给定的行集合(顺序不重要)
   *
   * <p>Closes the result set after reading.
   * 读取后会关闭结果集,避免资源泄漏
   *
   * <p>For example:
   * <pre>assertThat(statement.executeQuery("select empno from emp"),
   *   returnsUnordered("empno=1234", "empno=100"));</pre>
   * 
   * 使用示例:验证SQL查询返回的员工编号是否包含1234和100(顺序不限)
   * 
   * 工作原理:
   * 1. 将预期的行字符串列表排序
   * 2. 读取ResultSet并转换为字符串列表
   * 3. 对实际结果排序
   * 4. 比较排序后的列表是否相等
   * 5. 使用ThreadLocal保存实际结果用于错误描述
   * 
   * @param lines 预期的行字符串数组,每行格式如"empno=1234"
   * @return ResultSet匹配器,验证结果集内容
   */
  public static Matcher<? super ResultSet> returnsUnordered(String... lines) { // 创建无序结果集匹配器
    final List<String> expectedList = Lists.newArrayList(lines); // 将预期行转换为可变列表
    Collections.sort(expectedList); // 对预期列表排序,支持无序比较

    return new CustomTypeSafeMatcher<ResultSet>(Arrays.toString(lines)) { // 返回自定义类型安全匹配器
      @Override protected void describeMismatchSafely(ResultSet item, // 描述匹配失败时的实际结果
          Description description) {
        final Object value = THREAD_ACTUAL.get(); // 从ThreadLocal获取实际结果
        THREAD_ACTUAL.remove(); // 清除ThreadLocal,避免内存泄漏
        description.appendText("was ").appendValue(value); // 添加实际值到描述
      }

      @Override protected boolean matchesSafely(ResultSet resultSet) { // 安全匹配方法(类型已检查)
        final List<String> actualList = new ArrayList<>(); // 创建实际结果列表
        try {
          CalciteAssert.toStringList(resultSet, actualList); // 将ResultSet转换为字符串列表
          resultSet.close(); // 关闭结果集,释放资源
        } catch (SQLException e) { // 捕获SQL异常
          throw TestUtil.rethrow(e); // 重新抛出为运行时异常
        }
        Collections.sort(actualList); // 对实际列表排序

        THREAD_ACTUAL.set(actualList); // 将实际结果存入ThreadLocal
        final boolean equals = actualList.equals(expectedList); // 比较实际和预期是否相等
        if (!equals) { // 如果不相等
          THREAD_ACTUAL.set(actualList); // 再次设置(可能重复,但确保有值)
        }
        return equals; // 返回匹配结果
      }
    };
  }

  public static <E extends Comparable> Matcher<Iterable<E>> equalsUnordered( // 创建可比较元素的无序相等匹配器
      E... lines) { // 可变参数,预期元素列表
    final List<String> expectedList = // 创建预期字符串列表
        Lists.newArrayList(toStringList(Arrays.asList(lines))); // 将元素转换为字符串并收集
    Collections.sort(expectedList); // 对预期列表排序
    final String description = Util.lines(expectedList); // 将列表转换为多行字符串作为描述
    return new CustomTypeSafeMatcher<Iterable<E>>(description) { // 返回自定义匹配器
      @Override protected void describeMismatchSafely(Iterable<E> actuals, // 描述匹配失败
          Description description) {
        final List<String> actualList = // 创建实际字符串列表
            Lists.newArrayList(toStringList(actuals)); // 转换实际元素为字符串
        Collections.sort(actualList); // 对实际列表排序
        description.appendText("was ") // 添加"was"前缀
            .appendValue(Util.lines(actualList)); // 添加实际值的多行表示
      }

      @Override protected boolean matchesSafely(Iterable<E> actuals) { // 安全匹配方法
        final List<String> actualList = // 转换实际元素为字符串列表
            Lists.newArrayList(toStringList(actuals));
        Collections.sort(actualList); // 排序实际列表
        return actualList.equals(expectedList); // 比较排序后的列表
      }
    };
  }

  private static <E> Iterable<String> toStringList(Iterable<E> items) { // 将元素集合转换为字符串集合
    return StreamSupport.stream(items.spliterator(), false) // 创建流(非并行)
        .map(Object::toString) // 将每个元素转换为字符串
        .collect(toImmutableList()); // 收集为不可变列表
  }

  /**
   * Creates a matcher that matches when the examined object is within
   * {@code epsilon} of the specified {@code value}.
   * 创建一个匹配器,验证对象是否在指定值的epsilon范围内
   *
   * @deprecated Use {@link org.hamcrest.Matchers#closeTo(double, double)}
   * 已废弃,请使用Hamcrest的closeTo方法替代
   */
  @Deprecated // to be removed before 1.39 // 标记为废弃,将在1.39版本前移除
  public static <T extends Number> Matcher<T> within(T value, double epsilon) { // 创建数值范围匹配器
    return new IsWithin<>(value, epsilon); // 返回IsWithin匹配器实例
  }

  /**
   * Creates a matcher that matches when the examined object is within
   * {@link #EPSILON} of the specified <code>operand</code>.
   * 创建一个匹配器,验证Double类型对象是否在指定值的EPSILON范围内
   * 
   * EPSILON是类定义的常量(1.0e-5),用于处理浮点数精度问题
   * 
   * @param value 期望的数值
   * @return Double匹配器,验证数值是否在容差范围内
   */
  public static Matcher<Double> isAlmost(double value) { // 创建近似相等匹配器(使用默认EPSILON)
    return closeTo(value, EPSILON); // 使用Hamcrest的closeTo匹配器
  }

  /**
   * Creates a matcher that matches if the examined value is between bounds:
   * <code>min &le; value &le; max</code>.
   *
   * @param <T> value type
   * @param min Lower bound
   * @param max Upper bound
   */
  public static <T extends Comparable<T>> Matcher<T> between(T min, T max) {
    return new CustomTypeSafeMatcher<T>("between " + min + " and " + max) {
      @Override protected boolean matchesSafely(T item) {
        return min.compareTo(item) <= 0
            && item.compareTo(max) <= 0;
      }
    };
  }

  /** Creates a matcher by applying a function to a value before calling
   * another matcher. */
  public static <F, T> Matcher<F> compose(Matcher<T> matcher,
      Function<F, T> f) {
    return new ComposingMatcher<>(matcher, f);
  }

  /**
   * Creates a Matcher that matches when the examined string is equal to the
   * specified {@code value} when all Windows-style line endings ("\r\n")
   * have been converted to Unix-style line endings ("\n").
   *
   * <p>Thus, if {@code foo()} is a function that returns "hello{newline}world"
   * in the current operating system's line endings, then
   *
   * <blockquote>
   *   assertThat(foo(), isLinux("hello\nworld"));
   * </blockquote>
   *
   * <p>will succeed on all platforms.
   *
   * @see Util#toLinux(String)
   */
  public static Matcher<String> isLinux(final String value) {
    return compose(Is.is(value), input -> input == null ? null : Util.toLinux(input));
  }

  /** Matcher that matches a {@link RelNode} if the {@code RelNode} is valid
   * per {@link RelValidityChecker}. */
  public static Matcher<RelNode> relIsValid() {
    return new TypeSafeMatcher<RelNode>() {
      @Override public void describeTo(Description description) {
        description.appendText("rel is valid");
      }

      @Override protected boolean matchesSafely(RelNode rel) {
        RelValidityChecker checker = new RelValidityChecker();
        checker.go(rel);
        return checker.invalidCount() == 0;
      }
    };
  }

  /**
   * Creates a Matcher that matches a {@link RelNode} if its string
   * representation, after converting Windows-style line endings ("\r\n")
   * to Unix-style line endings ("\n"), is equal to the given {@code value}.
   */
  public static Matcher<RelNode> hasTree(final String value) {
    return compose(Is.is(value), input -> {
      // Convert RelNode to a string with Linux line-endings
      return Util.toLinux(RelOptUtil.toString(input));
    });
  }

  /**
   * Basically similar to {@link #hasTree(String)}, except for expanding RelNode's detail info.
   * For example, default nulls direction will be compared through this.
   */
  public static Matcher<RelNode> hasExpandedTree(final String value) {
    return compose(Is.is(value), input -> {
      // Convert RelNode to a string with Linux line-endings
      return Util.toLinux(RelOptUtil.toString(input, SqlExplainLevel.EXPPLAN_ATTRIBUTES, true));
    });
  }

  /**
   * Creates a Matcher that matches a {@link RelNode} if its field
   * names, converting to a list, are equal to the given {@code value}.
   */
  public static Matcher<RelNode> hasFieldNames(String fieldNames) {
    return new TypeSafeMatcher<RelNode>() {
      @Override public void describeTo(Description description) {
        description.appendText("has fields ").appendText(fieldNames);
      }

      @Override protected boolean matchesSafely(RelNode r) {
        return r.getRowType().getFieldNames().toString().equals(fieldNames);
      }
    };
  }
  /**
   * Creates a Matcher that matches a {@link RelNode} if its string
   * representation, after converting Windows-style line endings ("\r\n")
   * to Unix-style line endings ("\n"), contains the given {@code value}
   * as a substring.
   */
  public static Matcher<RelNode> inTree(final String value) {
    return compose(StringContains.containsString(value), input -> {
      // Convert RelNode to a string with Linux line-endings
      return Util.toLinux(RelOptUtil.toString(input));
    });
  }

  /**
   * Creates a Matcher that matches a {@link RexNode} if its string
   * representation, after converting Windows-style line endings ("\r\n")
   * to Unix-style line endings ("\n"), is equal to the given {@code value}.
   */
  public static Matcher<RexNode> hasRex(final String value) {
    return compose(Is.is(value), input -> {
      // Convert RexNode to a string with Linux line-endings
      return Util.toLinux(input.toString());
    });
  }

  /**
   * Creates a Matcher that matches a {@link RelNode} if its hints string
   * representation is equal to the given {@code value}.
   */
  public static Matcher<RelNode> hasHints(final String value) {
    return compose(Is.is(value),
        input -> input instanceof Hintable
            ? ((Hintable) input).getHints().toString()
            : "[]");
  }

  /**
   * Creates a Matcher that matches a {@link RangeSet} if its string
   * representation, after changing "&#2025;" to "..",
   * is equal to the given {@code value}.
   *
   * <p>This method is necessary because {@link RangeSet#toString()} changed
   * behavior. Guava 19 - 28 used a unicode symbol; Guava 29 onwards uses "..".
   */
  @SuppressWarnings("rawtypes")
  public static Matcher<RangeSet> isRangeSet(final String value) {
    return compose(Is.is(value), input -> sanitizeRangeSet(input.toString()));
  }

  /** Changes all '\u2025' (a unicode symbol denoting a range) to '..',
   * consistent with Guava 29+. */
  public static String sanitizeRangeSet(String string) {
    return string.replace("\u2025", "..");
  }

  /**
   * Creates a {@link Matcher} that matches execution plan and trims {@code , id=123} node ids.
   * {@link RelNode#getId()} is not stable across runs, so this matcher enables to trim those.
   *
   * @param value execpted execution plan
   * @return matcher
   */
  @API(since = "1.22", status = API.Status.EXPERIMENTAL)
  public static Matcher<String> containsWithoutNodeIds(String value) {
    return compose(StringContains.containsString(value), Matchers::trimNodeIds);
  }

  /**
   * Creates a matcher that matches when the examined string is equal to the
   * specified <code>operand</code> when all Windows-style line endings ("\r\n")
   * have been converted to Unix-style line endings ("\n").
   *
   * <p>Thus, if {@code foo()} is a function that returns "hello{newline}world"
   * in the current operating system's line endings, then
   *
   * <blockquote>
   *   assertThat(foo(), isLinux("hello\nworld"));
   * </blockquote>
   *
   * <p>will succeed on all platforms.
   *
   * @see Util#toLinux(String)
   */
  public static Matcher<String> containsStringLinux(String value) {
    return compose(StringContains.containsString(value), Util::toLinux);
  }

  public static String trimNodeIds(String s) {
    return PATTERN.matcher(s).replaceAll("");
  }

  /**
   * Creates a matcher that matches if the examined value is expected throwable.
   *
   * @param expected Throwable to match.
   */
  public static Matcher<? super Throwable> expectThrowable(Throwable expected) {
    return new BaseMatcher<Throwable>() {
      @Override public boolean matches(Object item) {
        if (!(item instanceof Throwable)) {
          return false;
        }
        Throwable error = (Throwable) item;
        return expected != null
            && Objects.equals(error.getClass(), expected.getClass())
            && Objects.equals(error.getMessage(), expected.getMessage());
      }

      @Override public void describeTo(Description description) {
        description.appendText("is ").appendText(expected.toString());
      }
    };
  }

  /**
   * Creates a matcher that matches if the examined value has a given name.
   *
   * @param charsetName Name of character set
   *
   * @see Charset#forName
   */
  public static Matcher<Charset> isCharset(String charsetName) {
    return new TypeSafeMatcher<Charset>() {
      @Override public void describeTo(Description description) {
        description.appendText("is charset ").appendText(charsetName);
      }

      @Override protected boolean matchesSafely(Charset item) {
        return item.name().equals(charsetName);
      }
    };
  }

  /**
   * Matcher that succeeds for any collection that, when converted to strings
   * and sorted on those strings, matches the given reference string.
   *
   * <p>Use it as an alternative to {@link Is#is} if items in your
   * list might occur in any order.
   *
   * <p>For example:
   *
   * <pre>{@code
   * List<Integer> ints = Arrays.asList(2, 500, 12);
   * assertThat(ints, sortsAs("[12, 2, 500]");
   * }</pre>
   */
  public static <T> Matcher<Iterable<T>> sortsAs(final String value) {
    return compose(equalTo(value), item -> {
      final List<String> strings = new ArrayList<>();
      for (T t : item) {
        strings.add(t.toString());
      }
      Collections.sort(strings);
      return strings.toString();
    });
  }

  /** Returns a matcher that tests whether an object is an array (including a
   * primitive array) with a given size.
   *
   * <p>Compare to {@link org.hamcrest.Matchers#arrayWithSize(int)}, which does
   * not allow primitive arrays. */
  public static Matcher<Object> primitiveArrayWithSize(int i) {
    return new CustomTypeSafeMatcher<Object>("array with size " + i) {
      @Override protected boolean matchesSafely(Object o) {
        return o.getClass().isArray()
            && Array.getLength(o) == i;
      }
    };
  }

  /** Returns a matcher that tests whether an object is a list
   * with the given contents.
   *
   * <p>If the list is empty use {@link org.hamcrest.Matchers#empty}. */
  @SafeVarargs
  public static <E> Matcher<Object> isListOf(E... es) {
    return is(Arrays.asList(es));
  }

  /** Matcher that tests whether the numeric value is within a given difference
   * another value.
   *
   * @param <T> Value type
   */
  public static class IsWithin<T extends Number> extends BaseMatcher<T> {
    private final T expectedValue;
    private final double epsilon;

    public IsWithin(T expectedValue, double epsilon) {
      checkArgument(epsilon >= 0D);
      this.expectedValue = expectedValue;
      this.epsilon = epsilon;
    }

    @Override public boolean matches(Object actualValue) {
      return isWithin(actualValue, expectedValue, epsilon);
    }

    @Override public void describeTo(Description description) {
      description.appendValue(expectedValue + " +/-" + epsilon);
    }

    private static boolean isWithin(Object actual, Number expected,
        double epsilon) {
      if (actual == null) {
        return expected == null;
      }
      if (actual.equals(expected)) {
        return true;
      }
      final double a = ((Number) actual).doubleValue();
      final double min = expected.doubleValue() - epsilon;
      final double max = expected.doubleValue() + epsilon;
      return min <= a && a <= max;
    }
  }

  /** Matcher that transforms the input value using a function before
   * passing to another matcher.
   *
   * @param <F> From type: the type of value to be matched
   * @param <T> To type: type returned by function, and the resulting matcher
   */
  private static class ComposingMatcher<F, T> extends TypeSafeMatcher<F> {
    private final Matcher<T> matcher;
    private final Function<F, T> f;

    ComposingMatcher(Matcher<T> matcher, Function<F, T> f) {
      this.matcher = matcher;
      this.f = f;
    }

    @Override protected boolean matchesSafely(F item) {
      return Unsafe.matches(matcher, f.apply(item));
    }

    @Override public void describeTo(Description description) {
      matcher.describeTo(description);
    }

    @Override protected void describeMismatchSafely(F item,
        Description mismatchDescription) {
      mismatchDescription.appendText("was ").appendValue(f.apply(item));
    }
  }
}
