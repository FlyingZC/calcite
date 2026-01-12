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
package org.apache.calcite.util; // 包声明，声明该类属于 org.apache.calcite.util 包

import com.google.common.annotations.VisibleForTesting; // 导入 Google Guava 的可见性测试注解
import com.google.common.base.Suppliers; // 导入 Google Guava 的 Suppliers 工具类，用于延迟计算和缓存
import com.google.common.collect.ImmutableSortedSet; // 导入 Google Guava 的不可变有序集合

import org.junit.jupiter.api.Assertions; // 导入 JUnit 5 的断言工具类

import java.io.File; // 导入 Java 的文件类
import java.io.PrintWriter; // 导入 Java 的打印写入器类
import java.io.StringWriter; // 导入 Java 的字符串写入器类
import java.lang.reflect.InvocationTargetException; // 导入反射调用目标异常类
import java.net.URL; // 导入 Java 的 URL 类
import java.util.List; // 导入 Java 的列表接口
import java.util.SortedSet; // 导入 Java 的有序集合接口
import java.util.function.Supplier; // 导入 Java 的函数式接口 Supplier
import java.util.regex.Matcher; // 导入 Java 的正则表达式匹配器类
import java.util.regex.Pattern; // 导入 Java 的正则表达式模式类

import static com.google.common.base.Preconditions.checkArgument; // 导入 Google Guava 的参数检查工具

import static org.apache.calcite.util.Util.first; // 导入 Calcite 的 Util 工具类的 first 方法

import static org.hamcrest.MatcherAssert.assertThat; // 导入 Hamcrest 的断言工具
import static org.junit.jupiter.api.Assertions.fail; // 导入 JUnit 5 的失败断言方法

import static java.lang.Double.parseDouble; // 导入 Java 的字符串转双精度浮点数方法
import static java.lang.Integer.parseInt; // 导入 Java 的字符串转整数方法
import static java.util.Objects.requireNonNull; // 导入 Java 的对象非空检查方法

/**
 * Static utilities for JUnit tests. // JUnit 测试的静态工具类，提供各种测试辅助方法
 */
public abstract class TestUtil { // 抽象测试工具类，不能被实例化，只提供静态方法
  //~ Static fields/initializers --------------------------------------------- // 静态字段/初始化块分隔符

  private static final Pattern LINE_BREAK_PATTERN = // 行分隔符正则表达式模式，匹配各种类型的换行符
      Pattern.compile("\r\n|\r|\n"); // 编译正则表达式，匹配 Windows(CRLF)、旧Mac(CR)、Unix(LF)三种换行符

  private static final Pattern TAB_PATTERN = Pattern.compile("\t"); // 制表符正则表达式模式，用于匹配制表符

  private static final String LINE_BREAK = // Java 字符串中的换行符表示形式
      "\\\\n\"" + Util.LINE_SEPARATOR + " + \""; // 将换行符转换为 Java 字符串字面量的格式，如 "\n" + "\n" + "..."

  private static final String JAVA_VERSION = // Java 版本字符串
      System.getProperties().getProperty("java.version"); // 从系统属性中获取当前 Java 运行时的版本号

  public static final Version AVATICA_VERSION = // Avatica 版本常量，Avatica 是 Calcite 的子项目
      Version.of(first(System.getProperty("calcite.avatica.version"), "0")); // 从系统属性获取 Avatica 版本，如果不存在则使用 "0"

  private static final Supplier<Integer> GUAVA_MAJOR_VERSION = // Guava 主版本号的延迟计算提供者
      Suppliers.memoize(TestUtil::computeGuavaMajorVersion); // 使用 Suppliers.memoize 缓存计算结果，避免重复计算

  /** Matches a number with at least four zeros after the point. */ // 匹配小数点后至少有四个0的数字的正则表达式
  private static final Pattern TRAILING_ZERO_PATTERN = // 尾随零模式，用于识别浮点数精度误差
      Pattern.compile("-?[0-9]+\\.([0-9]*[1-9])?(00000*[0-9][0-9]?)"); // 匹配类似 12.300000006 这样的数字

  /** Matches a number with at least four nines after the point. */ // 匹配小数点后至少有四个9的数字的正则表达式
  private static final Pattern TRAILING_NINE_PATTERN = // 尾随九模式，用于识别浮点数精度误差
      Pattern.compile("-?[0-9]+\\.([0-9]*[0-8])?(99999*[0-9][0-9]?)"); // 匹配类似 -12.37999999991 这样的数字

  /** This is to be used by {@link #rethrow(Throwable, String)} to add extra information via
   * {@link Throwable#addSuppressed(Throwable)}. */ // 内部异常类，用于在重新抛出异常时添加额外信息
  private static class ExtraInformation extends Throwable { // 继承 Throwable，用于存储额外的异常信息
    ExtraInformation(String message) { // 构造方法，接收消息字符串
      super(message); // 调用父类 Throwable 的构造方法
    }
  }

  //~ Methods ---------------------------------------------------------------- // 方法分隔符

  public static void assertEqualsVerbose( // 断言两个字符串相等，失败时输出详细的信息
      String expected, // 期望的字符串值
      String actual) { // 实际的字符串值
    Assertions.assertEquals(expected, actual, // 使用 JUnit 5 的 assertEquals 方法进行断言
        () -> "Expected:\n" // 提供一个 lambda 表达式作为失败消息，包含期望值、实际值和 Java 字符串表示
            + expected // 拼接期望的字符串
            + "\nActual:\n" // 拼接实际的字符串标签
            + actual // 拼接实际的字符串
            + "\nActual java:\n" // 拼接 Java 字符串表示的标签
            + toJavaString(actual) + '\n'); // 将实际字符串转换为 Java 字符串字面量格式
  }

  public static void assertThatScientific(String value, org.hamcrest.Matcher<String> matcher) { // 断言字符串值转换为科学计数法后匹配给定的匹配器
    double d = parseDouble(value); // 将字符串值解析为双精度浮点数
    assertThat(Util.toScientificNotation(d), matcher); // 将浮点数转换为科学计数法字符串，然后使用 Hamcrest 匹配器进行断言
  }

  /**
   * Converts a string (which may contain quotes and newlines) into a java
   * literal. // 将字符串（可能包含引号和换行符）转换为 Java 字符串字面量
   *
   * <p>For example, // 例如：
   *
   * <blockquote><pre>{@code
   * string with "quotes" split
   * across lines}</pre></blockquote> // 原始字符串
   *
   * <p>becomes // 转换为：
   *
   * <blockquote><pre>{@code
   * "string with \"quotes\" split" + "\n"
   *   + "across lines"}</pre></blockquote> // Java 字符串字面量
   */
  public static String quoteForJava(String s) { // 将字符串转换为 Java 字符串字面量
    s = Util.replace(s, "\\", "\\\\"); // 将反斜杠转义为双反斜杠
    s = Util.replace(s, "\"", "\\\""); // 将双引号转义为反斜杠加双引号
    s = LINE_BREAK_PATTERN.matcher(s).replaceAll(LINE_BREAK); // 将换行符转换为 Java 字符串字面量的换行格式
    s = TAB_PATTERN.matcher(s).replaceAll("\\\\t"); // 将制表符转换为转义的制表符
    s = "\"" + s + "\""; // 在字符串前后添加双引号
    final String spurious = " + \n\"\""; // 定义需要移除的虚假后缀
    if (s.endsWith(spurious)) { // 如果字符串以虚假后缀结尾
      s = s.substring(0, s.length() - spurious.length()); // 移除虚假后缀
    }
    return s; // 返回转换后的 Java 字符串字面量
  }

  /**
   * Converts a string (which may contain quotes and newlines) into a java
   * literal. // 将字符串（可能包含引号和换行符）转换为 Java 字符串字面量
   *
   * <p>For example, // 例如：
   *
   * <blockquote><pre>{code
   * string with "quotes" split
   * across lines}</pre></blockquote> // 原始字符串
   *
   * <p>becomes // 转换为：
   *
   * <blockquote><pre>{@code
   * TestUtil.fold("string with \"quotes\" split\n",
   *     + "across lines")}</pre></blockquote> // 使用 TestUtil.fold 方法的格式
   */
  public static String toJavaString(String s) { // 将字符串转换为 Java 字符串字面量，使用 fold 方法的格式
    // Convert [string with "quotes" split // 将 [string with "quotes" split
    // across lines] // across lines]
    // into [fold( // 转换为 [fold(
    // "string with \"quotes\" split\n" // "string with \"quotes\" split\n"
    // + "across lines")] // + "across lines")]
    //
    s = Util.replace(s, "\"", "\\\""); // 将双引号转义为反斜杠加双引号
    s = LINE_BREAK_PATTERN.matcher(s).replaceAll(LINE_BREAK); // 将换行符转换为 Java 字符串字面量的换行格式
    s = TAB_PATTERN.matcher(s).replaceAll("\\\\t"); // 将制表符转换为转义的制表符
    s = "\"" + s + "\""; // 在字符串前后添加双引号
    String spurious = "\n \\+ \"\""; // 定义需要移除的虚假后缀（与 quoteForJava 不同）
    if (s.endsWith(spurious)) { // 如果字符串以虚假后缀结尾
      s = s.substring(0, s.length() - spurious.length()); // 移除虚假后缀
    }
    return s; // 返回转换后的 Java 字符串字面量
  }

  /**
   * Combines an array of strings, each representing a line, into a single
   * string containing line separators. // 将字符串数组合并为一个包含行分隔符的单个字符串
   */
  public static String fold(String... strings) { // 将多个字符串（每行一个）合并为一个字符串
    StringBuilder buf = new StringBuilder(); // 创建字符串构建器
    for (String string : strings) { // 遍历字符串数组
      buf.append(string); // 将字符串添加到构建器
      buf.append('\n'); // 在每个字符串后添加换行符
    }
    return buf.toString(); // 返回合并后的字符串
  }

  /** Quotes a string for Java or JSON. */ // 将字符串转义为 Java 或 JSON 格式
  public static String escapeString(String s) { // 转义字符串中的特殊字符
    return escapeString(new StringBuilder(), s).toString(); // 调用重载方法并转换为字符串
  }

  /** Quotes a string for Java or JSON, into a builder. */ // 将字符串转义为 Java 或 JSON 格式，写入到构建器
  public static StringBuilder escapeString(StringBuilder buf, String s) { // 转义字符串中的特殊字符并写入构建器
    buf.append('"'); // 添加起始双引号
    int n = s.length(); // 获取字符串长度
    char lastChar = 0; // 初始化上一个字符为 0
    for (int i = 0; i < n; ++i) { // 遍历字符串的每个字符
      char c = s.charAt(i); // 获取当前位置的字符
      switch (c) { // 根据字符类型进行转义
      case '\\': // 如果是反斜杠
        buf.append("\\\\"); // 转义为双反斜杠
        break; // 跳出 switch
      case '"': // 如果是双引号
        buf.append("\\\""); // 转义为反斜杠加双引号
        break; // 跳出 switch
      case '\n': // 如果是换行符
        buf.append("\\n"); // 转义为 \n
        break; // 跳出 switch
      case '\r': // 如果是回车符
        if (lastChar != '\n') { // 如果上一个字符不是换行符
          buf.append("\\r"); // 转义为 \r（避免 \r\n 被转义两次）
        }
        break; // 跳出 switch
      default: // 其他字符
        buf.append(c); // 直接添加字符
        break; // 跳出 switch
      }
      lastChar = c; // 记录当前字符作为下一个循环的上一个字符
    }
    return buf.append('"'); // 添加结束双引号并返回构建器
  }

  /**
   * Quotes a pattern. // 转义正则表达式模式中的特殊字符
   */
  public static String quotePattern(String s) { // 转义正则表达式特殊字符
    return s.replace("\\", "\\\\") // 转义反斜杠
        .replace(".", "\\.") // 转义点号
        .replace("+", "\\+") // 转义加号
        .replace("{", "\\{") // 转义左花括号
        .replace("}", "\\}") // 转义右花括号
        .replace("|", "\\||") // 转义竖线
        .replace("$", "\\$") // 转义美元符号
        .replace("?", "\\?") // 转义问号
        .replace("*", "\\*") // 转义星号
        .replace("(", "\\(") // 转义左圆括号
        .replace(")", "\\)") // 转义右圆括号
        .replace("[", "\\[") // 转义左方括号
        .replace("]", "\\]") // 转义右方括号
        .replace("\n", "\\n") // 转义换行符
        .replace("^", "\\^"); // 转义脱字符
  }

  /** Removes floating-point rounding errors from the end of a string. // 从字符串末尾移除浮点数舍入误差
   *
   * <p>{@code 12.300000006} becomes {@code 12.3}; // 例如：12.300000006 变为 12.3
   * {@code -12.37999999991} becomes {@code -12.38}. */ // -12.37999999991 变为 -12.38
  public static String correctRoundedFloat(String s) { // 修正浮点数舍入误差
    if (s == null) { // 如果字符串为 null
      return s; // 直接返回 null
    }
    final Matcher m = TRAILING_ZERO_PATTERN.matcher(s); // 创建尾随零模式的匹配器
    if (m.matches()) { // 如果匹配尾随零模式
      s = s.substring(0, s.length() - m.group(2).length()); // 移除尾随的零
    }
    final Matcher m2 = TRAILING_NINE_PATTERN.matcher(s); // 创建尾随九模式的匹配器
    if (m2.matches()) { // 如果匹配尾随九模式
      s = s.substring(0, s.length() - m2.group(2).length()); // 移除尾随的九
      if (s.length() > 0) { // 如果字符串不为空
        final char c = s.charAt(s.length() - 1); // 获取最后一个字符
        switch (c) { // 根据最后一个字符进行进位处理
        case '0': // 如果是 0
        case '1': // 如果是 1
        case '2': // 如果是 2
        case '3': // 如果是 3
        case '4': // 如果是 4
        case '5': // 如果是 5
        case '6': // 如果是 6
        case '7': // 如果是 7
        case  '8': // 如果是 8
          // '12.3499999996' became '12.34', now we make it '12.35' // 12.3499999996 变为 12.34，现在进位为 12.35
          s = s.substring(0, s.length() - 1) + (char) (c + 1); // 移除最后一位并加 1
          break; // 跳出 switch
        case '.': // 如果是小数点
          // '12.9999991' became '12.', which we leave as is. // 12.9999991 变为 12.，保持不变
          break; // 跳出 switch
        }
      }
    }
    return s; // 返回修正后的字符串
  }

  /**
   * Returns the Java major version: 7 for JDK 1.7, 8 for JDK 8, 10 for
   * JDK 10, etc. depending on current system property {@code java.version}. // 返回 Java 主版本号：JDK 1.7 返回 7，JDK 8 返回 8，JDK 10 返回 10，等
   */
  public static int getJavaMajorVersion() { // 获取当前 Java 运行时的主版本号
    return majorVersionFromString(JAVA_VERSION); // 调用方法从版本字符串解析主版本号
  }

  /**
   * Detects java major version given long format of full JDK version. // 根据完整的 JDK 版本字符串检测 Java 主版本号
   * See <a href="http://openjdk.java.net/jeps/223">JEP 223: New Version-String Scheme</a>. // 参考 JEP 223 新版本字符串方案
   *
   * @param version current version as string usually from {@code java.version} property. // 版本字符串，通常来自 java.version 属性
   * @return major java version ({@code 8, 9, 10, 11} etc.) // 返回 Java 主版本号（8、9、10、11 等）
   */
  @VisibleForTesting // 标注为测试可见，允许测试类访问
  static int majorVersionFromString(String version) { // 从版本字符串解析主版本号
    requireNonNull(version, "version"); // 检查版本字符串不为 null

    if (version.startsWith("1.")) { // 如果版本以 "1." 开头（JDK 8 及以前的格式）
      // running on version <= 8 (expecting string of type: x.y.z*) // 运行在版本 <= 8 上（期望格式为 x.y.z*）
      final String[] versions = version.split("\\."); // 用点号分割版本字符串
      return parseInt(versions[1]); // 解析第二个部分作为主版本号（如 1.8.0 中的 8）
    }
    // probably running on > 8 (just get first integer which is major version) // 可能运行在 > 8 上（直接获取第一个整数作为主版本号）
    Matcher matcher = Pattern.compile("^\\d+").matcher(version); // 创建匹配开头数字的正则表达式
    if (!matcher.lookingAt()) { // 如果版本字符串不以数字开头
      throw new IllegalArgumentException("Can't parse (detect) JDK version from " + version); // 抛出非法参数异常
    }

    return parseInt(matcher.group()); // 解析匹配到的数字作为主版本号
  }

  /** Returns the Guava major version. */ // 返回 Guava 库的主版本号
  public static int getGuavaMajorVersion() { // 获取当前使用的 Guava 库的主版本号
    return GUAVA_MAJOR_VERSION.get(); // 从缓存的 Supplier 中获取 Guava 主版本号
  }

  /** Computes the Guava major version. */ // 计算 Guava 主版本号
  private static int computeGuavaMajorVersion() { // 通过检查特定类的存在性来确定 Guava 版本
    // A list of classes and the Guava version that they were introduced. // 类列表和它们引入的 Guava 版本
    // The list should not contain any classes that are removed in future // 列表不应包含在将来版本中被移除的类
    // versions of Guava. // 的 Guava 版本
    return new VersionChecker() // 创建版本检查器
        .tryClass(2, "com.google.common.collect.ImmutableList") // 检查 Guava 2 引入的 ImmutableList 类
        .tryClass(14, "com.google.common.reflect.Parameter") // 检查 Guava 14 引入的 Parameter 类
        .tryClass(17, "com.google.common.base.VerifyException") // 检查 Guava 17 引入的 VerifyException 类
        .tryClass(21, "com.google.common.io.RecursiveDeleteOption") // 检查 Guava 21 引入的 RecursiveDeleteOption 类
        .tryClass(23, "com.google.common.util.concurrent.FluentFuture") // 检查 Guava 23 引入的 FluentFuture 类
        .tryClass(26, "com.google.common.util.concurrent.ExecutionSequencer") // 检查 Guava 26 引入的 ExecutionSequencer 类
        .bestVersion; // 返回检测到的最高版本号
  }

  /** Returns the JVM vendor. */ // 返回 JVM 厂商
  public static String getJavaVirtualMachineVendor() { // 获取当前 Java 虚拟机的厂商名称
    return System.getProperty("java.vm.vendor"); // 从系统属性中获取 java.vm.vendor 属性
  }

  /** Returns the root directory of the source tree. */ // 返回源代码树的根目录
  public static File getBaseDir(Class<?> klass) { // 根据给定的类获取项目根目录
    // Algorithm: // 算法：
    // 1) Find location of TestUtil.class // 1) 找到 TestUtil.class 的位置
    // 2) Climb via getParentFile() until we detect pom.xml // 2) 通过 getParentFile() 向上查找，直到找到 pom.xml
    // 3) It means we've got BASE/testkit/pom.xml, and we need to get BASE // 3) 这意味着我们找到了 BASE/testkit/pom.xml，需要获取 BASE
    final URL resource = klass.getResource(klass.getSimpleName() + ".class"); // 获取类的资源 URL
    final File classFile = // 将资源 URL 转换为文件
        Sources.of(requireNonNull(resource, "resource")).file(); // 使用 Sources 工具类将 URL 转换为 File 对象

    File file = classFile.getAbsoluteFile(); // 获取类文件的绝对路径
    for (int i = 0; i < 42; i++) { // 最多向上查找 42 层（防止无限循环）
      if (isProjectDir(file)) { // 如果当前目录是项目目录
        // Ok, file == BASE/testkit/ // 好的，file == BASE/testkit/
        break; // 跳出循环
      }
      file = file.getParentFile(); // 向上一级目录
    }
    if (!isProjectDir(file)) { // 如果仍然没有找到项目目录
      fail("Could not find pom.xml, build.gradle.kts or gradle.properties. " // 测试失败，输出错误信息
          + "Started with " + classFile.getAbsolutePath() // 从类文件的绝对路径开始
          + ", the current path is " + file.getAbsolutePath()); // 当前路径是
    }
    return file.getParentFile(); // 返回项目根目录（BASE）
  }

  private static boolean isProjectDir(File dir) { // 检查目录是否是项目目录
    return new File(dir, "pom.xml").isFile() // 检查是否存在 pom.xml 文件（Maven 项目）
        || new File(dir, "build.gradle.kts").isFile() // 或存在 build.gradle.kts 文件（Gradle Kotlin DSL 项目）
        || new File(dir, "gradle.properties").isFile(); // 或存在 gradle.properties 文件（Gradle 项目）
  }

  /** Given a list, returns the number of elements that are not between an
   * element that is less and an element that is greater. */ // 给定一个列表，返回不在较小元素和较大元素之间的元素集合
  public static <E extends Comparable<E>> SortedSet<E> outOfOrderItems(List<E> list) { // 查找列表中乱序的元素
    E previous = null; // 初始化前一个元素为 null
    final ImmutableSortedSet.Builder<E> b = ImmutableSortedSet.naturalOrder(); // 创建自然顺序的不可变有序集合构建器
    for (E e : list) { // 遍历列表中的每个元素
      if (previous != null && previous.compareTo(e) > 0) { // 如果前一个元素不为 null 且大于当前元素（即乱序）
        b.add(e); // 将当前元素添加到结果集合中
      }
      previous = e; // 更新前一个元素为当前元素
    }
    return b.build(); // 构建并返回不可变的有序集合
  }

  /** Checks if exceptions have give substring. That is handy to prevent logging SQL text twice */ // 检查异常链中是否包含指定的子字符串，这有助于避免重复记录 SQL 文本
  public static boolean hasMessage(Throwable t, String substring) { // 检查异常或其原因中是否包含指定子字符串
    while (t != null) { // 遍历异常链
      String message = t.getMessage(); // 获取异常消息
      if (message != null && message.contains(substring)) { // 如果消息不为 null 且包含子字符串
        return true; // 返回 true
      }
      t = t.getCause(); // 获取异常的原因
    }
    return false; // 未找到子字符串，返回 false
  }

  /** Rethrows given exception keeping stacktraces clean and compact. */ // 重新抛出给定的异常，保持堆栈跟踪干净和紧凑
  public static <E extends Throwable> RuntimeException rethrow(Throwable e) throws E { // 重新抛出异常
    if (e instanceof InvocationTargetException) { // 如果是反射调用目标异常
      e = e.getCause(); // 获取实际的目标异常
    }
    throw (E) e; // 强制转换为 E 类型并抛出
  }

  /** Rethrows given exception keeping stacktraces clean and compact. */ // 重新抛出给定的异常，保持堆栈跟踪干净和紧凑
  public static <E extends Throwable> RuntimeException rethrow(Throwable e, // 重新抛出异常，并添加额外信息
      String message) throws E { // 接收异常和消息
    e.addSuppressed(new ExtraInformation(message)); // 将额外信息作为被抑制的异常添加到原始异常
    throw (E) e; // 强制转换为 E 类型并抛出
  }

  /** Returns string representation of the given {@link Throwable}. */ // 返回给定 Throwable 的字符串表示（堆栈跟踪）
  public static String printStackTrace(Throwable t) { // 将异常的堆栈跟踪转换为字符串
    StringWriter sw = new StringWriter(); // 创建字符串写入器
    PrintWriter pw = new PrintWriter(sw); // 创建打印写入器，输出到字符串写入器
    t.printStackTrace(pw); // 将异常的堆栈跟踪写入打印写入器
    pw.flush(); // 刷新打印写入器
    return sw.toString(); // 返回字符串写入器的内容
  }

  /** Checks whether a given class exists, and updates a version if it does. */ // 检查给定的类是否存在，如果存在则更新版本号
  private static class VersionChecker { // 版本检查器内部类，用于通过检查类的存在性确定版本
    int bestVersion = -1; // 当前检测到的最高版本号，初始为 -1

    VersionChecker tryClass(int version, String className) { // 尝试检查指定版本的类是否存在
      try { // 尝试加载类
        Class.forName(className); // 使用反射加载指定名称的类
        bestVersion = Math.max(version, bestVersion); // 如果类存在，更新最高版本号
      } catch (ClassNotFoundException e) { // 如果类不存在
        // ignore // 忽略异常
      }
      return this; // 返回 this，支持链式调用
    }
  }

  /** Returns a {@code CharSequence} that contains a given string repeated
   * {@code count} times. Unlike a String with the same contents, it
   * is virtual, and only becomes real when, say, someone calls
   * {@link StringBuilder#append(CharSequence)} with it. */ // 返回一个包含给定字符串重复 count 次的 CharSequence。与具有相同内容的 String 不同，它是虚拟的，只有在有人调用 StringBuilder.append(CharSequence) 时才会真正计算
  public static CharSequence repeat(String s, int count) { // 创建重复字符串的虚拟字符序列
    final int length = s.length() * count; // 计算重复后的总长度
    return new RepeatCharSequence(s, length); // 返回一个虚拟的重复字符序列
  }

  /** CharSequence that repeats a given string up to a given length. */ // 重复给定字符串到指定长度的 CharSequence 实现
  private static class RepeatCharSequence implements CharSequence { // 内部类，实现 CharSequence 接口
    private final int length; // 字符序列的总长度
    private final String s; // 要重复的原始字符串

    RepeatCharSequence(String s, int length) { // 构造方法，接收字符串和总长度
      this.s = requireNonNull(s, "s"); // 检查字符串不为 null
      this.length = length; // 设置总长度
      checkArgument(!s.isEmpty()); // 检查字符串不为空
      checkArgument(length >= 0); // 检查长度非负
    }

    @Override public String toString() { // 重写 toString 方法
      //noinspection StringBufferReplaceableByString // 忽略警告，这里必须使用 StringBuilder 来触发虚拟计算
      return new StringBuilder().append(this).toString(); // 使用 StringBuilder 触发虚拟字符序列的计算
    }

    @Override public int length() { // 重写 length 方法
      return length; // 返回总长度
    }

    @Override public char charAt(int index) { // 重写 charAt 方法
      return s.charAt(index % s.length()); // 通过取模运算获取对应位置的字符
    }

    @Override public CharSequence subSequence(int start, int end) { // 重写 subSequence 方法
      final int offset = start % s.length(); // 计算起始位置在原始字符串中的偏移量
      if (offset == 0) { // 如果偏移量为 0（即从原始字符串的开头开始）
        return new RepeatCharSequence(s, end - start); // 返回新的重复字符序列
      }
      final String rotated = s.substring(offset) + s.substring(0, offset); // 旋转原始字符串，从偏移量处开始
      return new RepeatCharSequence(rotated, end - start); // 返回基于旋转字符串的新重复字符序列
    }
  }
}
