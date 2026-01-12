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
// Apache许可证头声明，说明该代码遵循Apache 2.0许可证
package org.apache.calcite.test;

import org.apache.calcite.util.Puffin; // 导入Puffin工具类，用于代码静态分析
import org.apache.calcite.util.Source; // 导入Source工具类，表示源代码文件
import org.apache.calcite.util.Sources; // 导入Sources工具类，用于创建Source对象
import org.apache.calcite.util.TestUnsafe; // 导入TestUnsafe工具类，提供测试相关的 Unsafe 操作
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用工具方法

import com.fasterxml.jackson.annotation.JsonCreator; // 导入Jackson注解，用于JSON反序列化
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 导入Jackson注解，用于忽略未知属性
import com.fasterxml.jackson.annotation.JsonProperty; // 导入Jackson注解，用于标记JSON属性
import com.fasterxml.jackson.databind.JavaType; // 导入Jackson类，用于表示Java类型
import com.fasterxml.jackson.databind.ObjectMapper; // 导入Jackson类，用于JSON序列化和反序列化
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper; // 导入Jackson类，用于YAML格式处理
import com.google.common.collect.ImmutableList; // 导入Guava类，用于创建不可变List
import com.google.common.collect.ImmutableSet; // 导入Guava类，用于创建不可变Set

import org.checkerframework.checker.nullness.qual.Nullable; // 导入注解，用于标记可空类型
import org.junit.jupiter.api.Test; // 导入JUnit5注解，用于标记测试方法

import java.io.BufferedReader; // 导入IO类，用于读取文本文件
import java.io.File; // 导入File类，用于文件操作
import java.io.IOException; // 导入异常类，用于处理IO异常
import java.io.PrintWriter; // 导入IO类，用于写入文本
import java.io.StringWriter; // 导入IO类，用于写入字符串
import java.nio.file.Path; // 导入NIO类，用于文件路径操作
import java.nio.file.Paths; // 导入NIO类，用于创建Path对象
import java.util.ArrayList; // 导入集合类，用于动态数组
import java.util.Comparator; // 导入函数式接口，用于比较器
import java.util.HashSet; // 导入集合类，用于哈希集合
import java.util.List; // 导入集合接口，用于列表
import java.util.Locale; // 导入类，用于本地化信息
import java.util.Set; // 导入集合接口，用于集合
import java.util.function.BiFunction; // 导入函数式接口，用于双参数函数
import java.util.function.Consumer; // 导入函数式接口，用于消费者函数
import java.util.regex.Matcher; // 导入正则表达式类，用于匹配器
import java.util.regex.Pattern; // 导入正则表达式类，用于模式
import java.util.stream.Stream; // 导入流API，用于流式处理

import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest匹配器，用于相等断言
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具
import static org.hamcrest.Matchers.empty; // 导入Hamcrest匹配器，用于空集合断言
import static org.hamcrest.Matchers.hasItem; // 导入Hamcrest匹配器，用于包含元素断言
import static org.hamcrest.Matchers.hasSize; // 导入Hamcrest匹配器，用于集合大小断言
import static org.hamcrest.Matchers.startsWith; // 导入Hamcrest匹配器，用于前缀断言
import static org.junit.jupiter.api.Assertions.fail; // 导入JUnit断言方法，用于失败断言
import static org.junit.jupiter.api.Assumptions.assumeTrue; // 导入JUnit假设方法，用于条件测试

import static java.lang.Integer.parseInt; // 导入静态方法，用于字符串转整数
import static java.util.regex.Pattern.compile; // 导入静态方法，用于编译正则表达式

/** Various automated checks on the code and git history. */ // 这是一个测试类，用于对代码和git历史进行各种自动化检查，包括代码风格检查、提交信息检查、文件排序检查等
class LintTest { // LintTest类定义，包含所有代码质量检查的测试方法
  /** Pattern that matches "[CALCITE-12]" or "[CALCITE-1234]" followed by a
   * space. */ // 正则表达式模式，用于匹配JIRA问题编号格式，如[CALCITE-12]或[CALCITE-1234]，后面必须跟一个空格
  private static final Pattern CALCITE_PATTERN = // 声明一个静态常量，用于匹配CALCITE JIRA问题编号的正则表达式模式
      compile("^(\\[CALCITE-[0-9]{1,4}][ ]).*"); // 编译正则表达式，匹配以[CALCITE-数字]开头且后面有空格的字符串
  private static final Path ROOT_PATH = Paths.get(System.getProperty("gradle.rootDir")); // 项目根目录路径，从gradle.rootDir系统属性获取，用于定位项目文件

  private static final String TERMINOLOGY_ERROR_MSG = // 终止语错误消息模板，用于提示术语使用不当
      "Message contains '%s' word; use one of the following instead: %s"; // 错误消息格式字符串，第一个%s是错误的术语，第二个%s是正确的术语列表
  private static final List<TermRule> TERM_RULES = initTerminologyRules(); // 术语规则列表，用于检查提交信息中术语的大小写是否正确

  @SuppressWarnings("Convert2MethodRef") // JDK 8 requires lambdas // 抑制警告，因为JDK 8需要使用lambda表达式而不是方法引用
  private Puffin.Program<GlobalState> makeProgram() { // 创建并返回一个Puffin程序对象，用于执行代码静态分析，包含所有lint规则
    return Puffin.builder(GlobalState::new, global -> new FileState(global)) // 使用Puffin构建器创建程序，GlobalState::new创建全局状态，lambda创建文件状态
        .add(line -> line.fnr() == 1, // 添加规则：如果是文件的第一行
            line -> line.globalState().fileCount++) // 则增加文件计数器，用于统计处理的文件数量

        // Skip directive // 跳过指令规则
        .add(line -> line.matches(".* lint:skip ([0-9]+).*"), // 添加规则：如果行包含"lint:skip n"注释
            line -> { // 执行跳过逻辑
              final Matcher matcher = line.matcher(".* lint:skip ([0-9]+).*"); // 创建匹配器提取跳过的行数
              if (matcher.matches()) { // 如果匹配成功
                int n = parseInt(matcher.group(1)); // 提取要跳过的行数n
                line.state().skipToLine = line.fnr() + n; // 设置跳过到的行号为当前行号加n
              }
            })

        // Trailing space // 尾随空格检查规则
        .add(line -> line.endsWith(" "), // 添加规则：如果行以空格结尾
            line -> line.state().message("Trailing space", line)) // 则报告尾随空格错误

        // Tab // 制表符检查规则
        .add(line -> line.contains("\t") // 添加规则：如果行包含制表符
                && !line.filename().endsWith(".txt") // 且不是.txt文件（允许txt文件使用tab）
                && !skipping(line), // 且不在跳过区域内
            line -> line.state().message("Tab", line)) // 则报告制表符错误

        // Comment without space // 注释空格检查规则
        .add(line -> line.matches(".* //[^ ].*") // 添加规则：如果行包含"// "后面紧跟非空格字符
                && !line.source().fileOpt() // 且不排除LintTest.java文件自身
                    .filter(f -> f.getName().equals("LintTest.java")).isPresent() // 过滤条件
                && !line.contains("//--") // 且不包含"--"注释
                && !line.contains("//~") // 且不包含"~"注释
                && !line.contains("//noinspection") // 且不包含IDEA抑制警告注释
                && !line.contains("//CHECKSTYLE"), // 且不包含Checkstyle注释
            line -> line.state().message("'//' must be followed by ' '", line)) // 则报告注释格式错误

        // In 'for (int i : list)', colon must be surrounded by space. // for循环冒号空格检查规则
        .add(line -> line.matches("^ *for \\(.*:.*") // 添加规则：如果行是for循环且包含冒号
                && !line.matches(".*[^ ][ ][:][ ][^ ].*") // 但冒号周围没有正确空格
                && isJava(line.filename()), // 且是Java文件
            line -> line.state().message("':' must be surrounded by ' '", line)) // 则报告冒号格式错误

        // Javadoc does not require '</p>', so we do not allow '</p>' // Javadoc段落标签检查规则
        .add(line -> line.state().inJavadoc() // 添加规则：如果在Javadoc注释中
                && line.contains("</p>"), // 且包含</p>结束标签
            line -> line.state().message("no '</p>'", line)) // 则报告不允许使用</p>错误

        // No "**/" // Javadoc结束标签格式检查规则
        .add(line -> line.contains(" **/") // 添加规则：如果包含" **/"（注意前面有空格）
                && line.state().inJavadoc(), // 且在Javadoc注释中
            line -> // 执行错误报告
                line.state().message("no '**/'; use '*/'", // 报告错误，提示使用"*/"而不是" **/"
                    line))

        // A Javadoc paragraph '<p>' must not be on its own line. // Javadoc段落标签位置检查规则
        .add(line -> line.matches("^ *\\* <p>"), // 添加规则：如果<p>标签在独立的一行
            line -> // 执行错误报告
                line.state().message("<p> must not be on its own line", // 报告<p>标签不能独占一行错误
                    line))

        // A Javadoc paragraph '<p>' must be preceded by a blank Javadoc // Javadoc段落标签前置空行检查规则
        // line. // 说明段落标签前需要有空白Javadoc行
        .add(line -> line.matches("^ *\\*"), // 添加规则：如果是Javadoc注释行（以"*"开头）
            line -> { // 执行逻辑
              final FileState f = line.state(); // 获取文件状态
              if (f.starLine == line.fnr() - 1) { // 如果前一行也是空白Javadoc行
                f.message("duplicate empty line in javadoc", line); // 报告重复空白行错误
              }
              f.starLine = line.fnr(); // 记录当前空白Javadoc行的行号
            })
        .add(line -> line.matches("^ *\\* <p>.*") // 添加规则：如果是包含<p>标签的Javadoc行
                && line.fnr() - 1 != line.state().starLine, // 且前一行不是空白Javadoc行
            line -> // 执行错误报告
                line.state().message("<p> must be preceded by blank line", // 报告<p>前缺少空白行错误
                    line))

        // A non-blank line following a blank line must have a '<p>' // Javadoc段落标签缺失检查规则
        .add(line -> line.state().inJavadoc() // 添加规则：如果在Javadoc注释中
                && line.state().ulCount == 0 // 且不在<ul>列表中
                && line.state().blockquoteCount == 0 // 且不在<blockquote>块引用中
                && line.contains("* ") // 且是普通Javadoc内容行
                && line.fnr() - 1 == line.state().starLine // 且前一行是空白Javadoc行
                && line.matches("^ *\\* [^<@].*") // 且行内容不以<或@开头
                && isJava(line.filename()), // 且是Java文件
            line -> line.state().message("missing '<p>'", line)) // 则报告缺少<p>标签错误

        // The first "@param" of a javadoc block must be preceded by a blank // Javadoc参数标签前置空行检查规则
        // line. // 说明第一个@param标签前需要有空白行
        .add(line -> line.matches("^ */\\*\\*.*"), // 添加规则：如果是Javadoc开始行（/**）
            line -> { // 执行初始化逻辑
              final FileState f = line.state(); // 获取文件状态
              f.javadocStartLine = line.fnr(); // 记录Javadoc开始行号
              f.blockquoteCount = 0; // 重置块引用计数器
              f.ulCount = 0; // 重置列表计数器
            })
        .add(line -> line.matches(".*\\*/"), // 添加规则：如果是Javadoc结束行（*/）
            line -> line.state().javadocEndLine = line.fnr()) // 记录Javadoc结束行号
        .add(line -> line.matches("^ *\\* @.*"), // 添加规则：如果是Javadoc标签行（@param、@return等）
            line -> { // 执行检查逻辑
              if (line.state().inJavadoc() // 如果在Javadoc注释中
                  && line.state().atLine < line.state().javadocStartLine // 且这是第一个@标签
                  && line.fnr() - 1 != line.state().starLine) { // 且前一行不是空白Javadoc行
                line.state().message( // 报告错误
                    "First @tag must be preceded by blank line", // 第一个@标签前缺少空白行
                    line);
              }
              line.state().atLine = line.fnr(); // 记录当前@标签行号
            })
        .add(line -> line.contains("<blockquote>"), // 添加规则：如果包含<blockquote>开始标签
            line -> line.state().blockquoteCount++) // 则增加块引用计数器
        .add(line -> line.contains("</blockquote>"), // 添加规则：如果包含</blockquote>结束标签
            line -> line.state().blockquoteCount--) // 则减少块引用计数器
        .add(line -> line.contains("<ul>"), // 添加规则：如果包含<ul>开始标签
            line -> line.state().ulCount++) // 则增加列表计数器
        .add(line -> line.contains("</ul>"), // 添加规则：如果包含</ul>结束标签
            line -> line.state().ulCount--) // 则减少列表计数器
        .build(); // 构建并返回Puffin程序对象
  }

  /** Returns whether we are currently in a region where lint rules should not
   * be applied. */ // 判断当前是否处于应该跳过lint规则检查的区域
  private static boolean skipping(Puffin.Line<GlobalState, FileState> line) { // 私有静态方法，判断是否应该跳过当前行的检查
    return line.state().skipToLine >= 0 // 返回true如果设置了跳过终止行号
        && line.fnr() < line.state().skipToLine; // 且当前行号小于跳过终止行号
  }

  /** Returns whether we are in a file that contains Java code. */ // 判断是否在包含Java代码的文件中
  private static boolean isJava(String filename) { // 私有静态方法，根据文件扩展名判断是否为Java相关文件
    return filename.endsWith(".java") // 返回true如果是.java文件
        || filename.endsWith(".jj") // 或者是.jj文件（JavaCC语法文件）
        || filename.endsWith(".fmpp") // 或者是.fmpp文件（FreeMarker预处理文件）
        || filename.endsWith(".ftl") // 或者是.ftl文件（FreeMarker模板文件）
        || filename.equals("GuavaCharSource{memory}"); // 或者是内存中的测试源文件
  }

  @Test void testProgramWorks() { // 测试方法，验证lint程序能够正确检测到代码中的各种问题
    final String code = "class MyClass {\n" // 定义测试代码字符串，包含各种代码风格问题
        + "  /** Paragraph.\n" // 第2行：Javadoc注释开始
        + "   *\n" // 第3行：空白Javadoc行
        + "   * Missing p.\n" // 第4行：缺少<p>标签的段落
        + "   *\n" // 第5行：空白Javadoc行
        + "   * <p>\n" // 第6行：<p>标签独占一行（错误）
        + "   * <p>A paragraph (p must be preceded by blank line).\n" // 第7行：<p>前缺少空白行（错误）
        + "   *\n" // 第8行：空白Javadoc行
        + "   *\n" // 第9行：重复的空白Javadoc行（错误）
        + "   * <p>no p</p>\n" // 第10行：使用了</p>结束标签（错误）
        + "   * @see java.lang.String (should be preceded by blank line)\n" // 第11行：@see标签前缺少空白行（错误）
        + "   **/\n" // 第12行：使用了" **/"结束标签（错误）
        + "  String x = \"ok because it's not in javadoc:</p>\";\n" // 第13行：字符串中的</p>不检查
        + "  for (Map.Entry<String, Integer> e: entries) {\n" // 第14行：for循环冒号后缺少空格（错误）
        + "    //comment without space\n" // 第15行：注释后缺少空格（错误）
        + "  }\n" // 第16行：代码块结束
        + "  for (int i :tooFewSpacesAfter) {\n" // 第17行：冒号后缺少空格（错误）
        + "  }\n" // 第18行：代码块结束
        + "  for (int i  : tooManySpacesBefore) {\n" // 第19行：冒号前多了空格（错误）
        + "  }\n" // 第20行：代码块结束
        + "  for (int i :   tooManySpacesAfter) {\n" // 第21行：冒号后多了空格（错误）
        + "  }\n" // 第22行：代码块结束
        + "  for (int i : justRight) {\n" // 第23行：正确的冒号空格格式
        + "  }\n" // 第24行：代码块结束
        + "}\n"; // 第25行：类定义结束
    final String expectedMessages = "[" // 定义期望的错误消息字符串
        + "GuavaCharSource{memory}:4:" // 第4行错误：缺少<p>标签
        + "missing '<p>'\n" // 错误消息
        + "GuavaCharSource{memory}:6:" // 第6行错误：<p>标签独占一行
        + "<p> must not be on its own line\n" // 错误消息
        + "GuavaCharSource{memory}:7:" // 第7行错误：<p>前缺少空白行
        + "<p> must be preceded by blank line\n" // 错误消息
        + "GuavaCharSource{memory}:9:" // 第9行错误：重复空白行
        + "duplicate empty line in javadoc\n" // 错误消息
        + "GuavaCharSource{memory}:10:" // 第10行错误：使用了</p>
        + "no '</p>'\n" // 错误消息
        + "GuavaCharSource{memory}:11:" // 第11行错误：@标签前缺少空白行
        + "First @tag must be preceded by blank line\n" // 错误消息
        + "GuavaCharSource{memory}:12:" // 第12行错误：使用了" **/"
        + "no '**/'; use '*/'\n" // 错误消息
        + "GuavaCharSource{memory}:14:" // 第14行错误：冒号周围缺少空格
        + "':' must be surrounded by ' '\n" // 错误消息
        + "GuavaCharSource{memory}:15:" // 第15行错误：注释后缺少空格
        + "'//' must be followed by ' '\n" // 错误消息
        + "GuavaCharSource{memory}:17:" // 第17行错误：冒号后缺少空格
        + "':' must be surrounded by ' '\n" // 错误消息
        + "GuavaCharSource{memory}:19:" // 第19行错误：冒号前多了空格
        + "':' must be surrounded by ' '\n" // 错误消息
        + "GuavaCharSource{memory}:21:" // 第21行错误：冒号后多了空格
        + "':' must be surrounded by ' '\n" // 错误消息
        + ""; // 结束字符串
    final Puffin.Program<GlobalState> program = makeProgram(); // 创建lint程序对象
    final StringWriter sw = new StringWriter(); // 创建字符串写入器用于捕获输出
    final GlobalState g; // 声明全局状态变量
    try (PrintWriter pw = new PrintWriter(sw)) { // 使用try-with-resources创建打印写入器
      g = program.execute(Stream.of(Sources.of(code)), pw); // 执行lint程序，传入代码流和打印写入器
    } // 自动关闭打印写入器
    assertThat(g.messages.toString().replace(", ", "\n") // 断言：将错误消息列表转换为字符串，替换逗号为换行
            .replace(']', '\n'), // 替换右括号为换行
        is(expectedMessages)); // 验证错误消息与期望一致
  }

  /** Tests that source code has no flaws. */ // 测试方法，验证项目源代码没有代码风格问题
  @Test void testLint() { // 测试方法定义
    assumeTrue(TestUnsafe.haveGit(), "Invalid git environment"); // 假设：必须在git环境中运行，否则跳过测试

    final Puffin.Program<GlobalState> program = makeProgram(); // 创建lint程序对象
    final List<File> files = TestUnsafe.getTextFiles(); // 获取所有文本文件列表

    final GlobalState g; // 声明全局状态变量
    try (PrintWriter pw = Util.printWriter(System.out)) { // 使用try-with-resources创建打印写入器输出到控制台
      g = program.execute(files.parallelStream().map(Sources::of), pw); // 并行执行lint程序，检查所有文件
    } // 自动关闭打印写入器

    g.messages.forEach(System.out::println); // 打印所有错误消息到控制台
    assertThat(g.messages, empty()); // 断言：错误消息列表必须为空，即没有代码问题
  }

  /** Tests that the most recent N commit messages are good. // 测试方法，验证最近的N条git提交消息符合规范
   *
   * <p>N needs to be large enough to verify multi-commit PRs, but not so large // N需要足够大以验证多提交的PR，但不能太大
   * that it fails because of historical commits. */ // 以免因为历史提交而失败
  @Test void testLintLog() { // 测试方法定义
    assumeTrue(TestUnsafe.haveGit(), "Invalid git environment"); // 假设：必须在git环境中运行，否则跳过测试

    int n = 7; // 设置检查最近7条提交消息
    final List<String> warnings = new ArrayList<>(); // 创建警告消息列表
    TestUnsafe.getCommitMessages(n, (message, rest) -> // 获取最近n条提交消息，对每条消息执行检查
        checkMessage(message, rest, warning -> // 调用checkMessage方法检查提交消息
            warnings.add("invalid git log message '" + message + "'; " // 如果有警告，添加到警告列表
                + warning))); // 拼接警告消息
    warnings.forEach(System.out::println); // 打印所有警告消息到控制台
    assertThat(warnings, empty()); // 断言：警告列表必须为空，即所有提交消息都符合规范
  }

  @Test void testLogMatcher() { // 测试方法，验证提交消息检查规则的各种情况
    final BiFunction<String, String, List<String>> f = (subject, body) -> { // 创建双参数函数，检查提交消息
      final List<String> warnings = new ArrayList<>(); // 创建警告列表
      checkMessage(subject, body, warnings::add); // 调用checkMessage方法检查消息
      return warnings; // 返回警告列表
    };
    assertThat(f.apply(" [CALCITE-1234] abc", ""), // 测试：开头有空格的提交消息
        hasItem("starts with space")); // 断言：应该检测到开头有空格错误
    assertThat(f.apply("[CALCITE-1234]  abc", ""), // 测试：JIRA编号后有两个空格
        hasItem("starts with space")); // 断言：应该检测到开头有空格错误
    assertThat(f.apply("[CALCITE-12b]  abc", ""), // 测试：JIRA编号包含非数字字符
        hasItem("malformed [CALCITE-nnnn] reference")); // 断言：应该检测到格式错误
    assertThat(f.apply("[CALCITE-12345]  abc", ""), // 测试：JIRA编号超过4位数字
        hasItem("malformed [CALCITE-nnnn] reference")); // 断言：应该检测到格式错误
    assertThat(f.apply("[CALCITE-1234]: abc", ""), // 测试：JIRA编号后使用冒号而不是空格
        hasItem("malformed [CALCITE-nnnn] reference")); // 断言：应该检测到格式错误
    assertThat(f.apply("CALCITE-1234: abc", ""), // 测试：JIRA编号缺少方括号
        hasItem("malformed [CALCITE-nnnn] reference")); // 断言：应该检测到格式错误
    assertThat(f.apply("[CALCITE-12] Abc", ""), // 测试：正确的2位JIRA编号
        empty()); // 断言：应该没有错误
    assertThat(f.apply("[CALCITE-123] Abc", ""), // 测试：正确的3位JIRA编号
        empty()); // 断言：应该没有错误
    assertThat(f.apply("[CALCITE-1234] Fix problem with foo", ""), // 测试：消息中包含"fix"
        hasItem("contains 'fix' or 'fixes'; you should describe the " // 断言：应该检测到包含fix错误
            + "problem, not what you did")); // 错误消息
    assertThat(f.apply("[CALCITE-1234] Baz doesn't buzz", ""), // 测试：正确的消息格式
        empty()); // 断言：应该没有错误
    assertThat(f.apply("[CALCITE-1234] Baz doesn't buzz.", ""), // 测试：消息以句号结尾
        hasItem("ends with period")); // 断言：应该检测到以句号结尾错误
    assertThat(f.apply("[CALCITE-1234]  Two problems.", ""), // 测试：同时有开头空格和结尾句号
        hasSize(2)); // 断言：应该有2个错误
    assertThat(f.apply("[CALCITE-1234]  Two problems.", ""), // 测试：同时有开头空格和结尾句号
        hasItem("ends with period")); // 断言：应该检测到以句号结尾错误
    assertThat(f.apply("[CALCITE-1234]  Two problems.", ""), // 测试：同时有开头空格和结尾句号
        hasItem("starts with space")); // 断言：应该检测到开头有空格错误
    assertThat(f.apply("Cosmetic: Move everything one character to left", ""), // 测试：没有JIRA编号的提交
        empty()); // 断言：应该没有错误
    assertThat( // 测试：包含多个JIRA编号的提交
        f.apply("Finishing up [CALCITE-4937], remove workarounds for " // 提交消息
            + "[CALCITE-4877]", ""), // 包含两个JIRA编号
        empty()); // 断言：应该没有错误
    assertThat(f.apply("Fix typo in filterable-model.yaml", ""), // 测试：修改配置文件的提交
        empty()); // 断言：应该没有错误
    assertThat( // 测试：回退提交
        f.apply("Revert \"[CALCITE-4817] Expand SubstitutionVisitor\"", ""), // 回退提交格式
        empty()); // 断言：应该没有错误
    assertThat(f.apply("[CALCITE-4817] cannot start with lower-case", ""), // 测试：首字母小写
        hasSize(1)); // 断言：应该有1个错误
    assertThat(f.apply("[CALCITE-4817] cannot start with lower-case", ""), // 测试：首字母小写
        hasItem("Message must start with upper-case letter")); // 断言：应该检测到首字母小写错误
    assertThat(f.apply("[MINOR] Lint", ""), // 测试：以[MINOR]开头而不是[CALCITE-nnnn]
        hasItem("starts with '[', and is not '[CALCITE-nnnn]'")); // 断言：应该检测到格式错误

    // If 'Lint:skip' occurs in the body, no checks are performed // 如果提交消息体中包含"Lint:skip"，则不执行检查
    assertThat( // 测试：包含Lint:skip的提交
        f.apply("[CALCITE-4817] cannot start with lower-case", // 有错误的提交消息
            "Body line 1\n" // 提交消息体第一行
                + "\n" // 空行
                + "Lint:skip"), // Lint:skip指令
        empty()); // 断言：应该没有错误（因为跳过了检查）
  }

  private static List<TermRule> initTerminologyRules() { // 初始化术语规则列表，定义各种数据库和技术的正确大小写格式
    ImmutableList.Builder<TermRule> rules = ImmutableList.builder(); // 创建不可变列表构建器
    rules.add(new TermRule("\\bmysql\\b", "MySQL")); // 添加MySQL术语规则，要求大写
    rules.add(new TermRule("\\bmssql\\b", "MSSQL")); // 添加MSSQL术语规则，要求大写
    rules.add(new TermRule("\\bpostgresql\\b", "PostgreSQL")); // 添加PostgreSQL术语规则，要求正确大小写
    rules.add(new TermRule("\\bhive\\b", "Hive")); // 添加Hive术语规则，要求大写
    rules.add(new TermRule("\\bspark\\b", "Spark")); // 添加Spark术语规则，要求大写
    rules.add(new TermRule("\\barrow\\b", "Arrow")); // 添加Arrow术语规则，要求大写
    rules.add(new TermRule("\\bpresto\\b", "Presto")); // 添加Presto术语规则，要求大写
    rules.add(new TermRule("\\boracle\\b", "Oracle")); // 添加Oracle术语规则，要求大写
    rules.add(new TermRule("\\bbigquery\\b", "BigQuery")); // 添加BigQuery术语规则，要求正确大小写
    rules.add(new TermRule("\\bredshift\\b", "Redshift")); // 添加Redshift术语规则，要求正确大小写
    rules.add(new TermRule("\\bsnowflake\\b", "Snowflake")); // 添加Snowflake术语规则，要求正确大小写
    rules.add(new TermRule("\\bsqlite\\b", "SQLite")); // 添加SQLite术语规则，要求正确大小写
    return rules.build(); // 构建并返回不可变术语规则列表
  }

  /**
   * A rule for defining valid patterns for terms. // 术语规则类，用于定义术语的有效模式
   */
  private static final class TermRule { // 私有静态内部类，表示术语规则
    private final Pattern termPattern; // 术语匹配模式（正则表达式）
    private final Set<String> validTerms; // 有效的术语集合

    TermRule(String regex, String... validTerms) { // 构造函数，接收正则表达式和有效术语
      this.termPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE); // 编译正则表达式，设置为不区分大小写
      this.validTerms = ImmutableSet.copyOf(validTerms); // 创建不可变的有效术语集合
    }

    /**
     * Checks whether the input satisfies the rule. // 检查输入是否满足规则
     * Returns an error message if the check fails and empty string if the input is valid. // 如果检查失败返回错误消息，如果输入有效返回空字符串
     */
    String check(String input) { // 检查方法，验证输入字符串中的术语格式
      final Matcher m = termPattern.matcher(input); // 创建匹配器
      if (m.find() && !validTerms.contains(m.group(0))) { // 如果找到匹配且不在有效术语集合中
        return String.format(Locale.ROOT, TERMINOLOGY_ERROR_MSG, m.group(0), validTerms); // 返回格式化的错误消息
      }
      return ""; // 返回空字符串表示检查通过
    }
  }

  private static void checkMessage(String subject, String body, // 检查git提交消息是否符合规范
      Consumer<String> consumer) { // 接收提交消息主题、消息体和错误消费者
    if (body.contains("Lint:skip")) { // 如果消息体中包含"Lint:skip"
      return; // 则跳过所有检查
    }
    String subject2 = subject; // 创建主题的副本用于处理
    if (subject.startsWith("[CALCITE-") // 如果主题以[CALCITE-开头
        || subject.startsWith("CALCITE-")) { // 或者以CALCITE-开头
      Matcher m = CALCITE_PATTERN.matcher(subject); // 创建匹配器检查JIRA编号格式
      if (m.matches()) { // 如果匹配成功
        subject2 = subject.substring(m.toMatchResult().end(1)); // 提取JIRA编号后的部分
      } else { // 如果匹配失败
        consumer.accept("malformed [CALCITE-nnnn] reference"); // 报告JIRA编号格式错误
      }
      if (subject2.matches("(?i).*\\b(fix|fixes)\\b.*")) { // 如果消息中包含"fix"或"fixes"（不区分大小写）
        consumer.accept("contains 'fix' or 'fixes'; you should describe the " // 报告错误
            + "problem, not what you did"); // 提示应该描述问题而不是描述做了什么
      }
    }
    if (subject2.startsWith("[")) { // 如果主题以方括号开头（但不是[CALCITE-nnnn]）
      consumer.accept("starts with '[', and is not '[CALCITE-nnnn]'"); // 报告格式错误
    }
    if (subject2.startsWith(" ")) { // 如果主题以空格开头
      consumer.accept("starts with space"); // 报告开头有空格错误
    }
    if (subject.endsWith(".")) { // 如果主题以句号结尾
      consumer.accept("ends with period"); // 报告以句号结尾错误
    }
    if (subject.endsWith(" ")) { // 如果主题以空格结尾
      consumer.accept("ends with space"); // 报告结尾有空格错误
    }
    if (subject2.matches("[a-z].*")) { // 如果主题以小写字母开头
      consumer.accept("Message must start with upper-case letter"); // 报告首字母必须大写错误
    }
    if (subject2.matches("^Chore.*\\b")) { // 如果主题以"Chore"开头
      consumer.accept("Message cannot start with the Chore keyword"); // 报告不能以Chore开头错误
    }

    // Check for keywords that should be capitalized // 检查需要大写的关键词
    for (TermRule tRule : TERM_RULES) { // 遍历所有术语规则
      String error = tRule.check(subject2); // 检查主题中的术语格式
      if (!error.isEmpty()) { // 如果有错误
        consumer.accept(error); // 将错误消息传递给消费者
      }
    }
  }

  @Test void testCheckMessageWithInvalidDBMSTerms() { // 测试方法，验证提交消息中数据库术语大小写错误的检测
    Set<String> invalidTerms = new HashSet<>(); // 创建无效术语集合
    invalidTerms.add("mysql"); // 添加小写的mysql
    invalidTerms.add("Mysql"); // 添加首字母大写的Mysql
    invalidTerms.add("MYSQL"); // 添加全大写的MYSQL
    invalidTerms.add("postgresql"); // 添加小写的postgresql
    invalidTerms.add("POSTGRESQL"); // 添加全大写的POSTGRESQL
    invalidTerms.add("Mssql"); // 添加首字母大写的Mssql
    invalidTerms.add("RedShift"); // 添加大小写错误的RedShift
    invalidTerms.add("SnowFlake"); // 添加大小写错误的SnowFlake
    invalidTerms.add("hiVe"); // 添加大小写错误的hiVe
    invalidTerms.add("HiVe"); // 添加大小写错误的HiVe
    for (String iTerm : invalidTerms) { // 遍历所有无效术语
      String msg = "Add support for " + iTerm + " dialect"; // 创建包含无效术语的提交消息
      List<String> errors = new ArrayList<>(); // 创建错误列表
      checkMessage(msg, "", errors::add); // 检查提交消息，收集错误
      assertThat("Failed to find error in:" + msg, errors, hasSize(1)); // 断言：应该检测到1个错误
      assertThat(errors.get(0), // 验证错误消息
          startsWith(String.format(Locale.ROOT, TERMINOLOGY_ERROR_MSG, iTerm, ""))); // 应该以术语错误消息开头
    }
  }

  @Test void testCheckMessageWithValidDBMSTerms() { // 测试方法，验证提交消息中正确格式的数据库术语不会触发错误
    Set<String> validTerms = new HashSet<>(); // 创建有效术语集合
    validTerms.add("MySQL"); // 添加正确格式的MySQL
    validTerms.add("PostgreSQL"); // 添加正确格式的PostgreSQL
    validTerms.add("MSSQL"); // 添加正确格式的MSSQL
    validTerms.add("Redshift"); // 添加正确格式的Redshift
    validTerms.add("Snowflake"); // 添加正确格式的Snowflake
    validTerms.add("Hive"); // 添加正确格式的Hive
    for (String vTerm : validTerms) { // 遍历所有有效术语
      String msg = "Add support for " + vTerm + " dialect"; // 创建包含有效术语的提交消息
      List<String> errors = new ArrayList<>(); // 创建错误列表
      checkMessage(msg, "", errors::add); // 检查提交消息，收集错误
      assertThat(errors, empty()); // 断言：错误列表应该为空
    }
  }

  /** Ensures that the {@code contributors.yml} file is sorted by name. */ // 测试方法，确保contributors.yml文件按名称排序
  @Test void testContributorsFileIsSorted() throws IOException { // 测试方法定义，可能抛出IO异常
    final ObjectMapper mapper = new YAMLMapper(); // 创建YAML映射器用于解析YAML文件
    final File contributorsFile = ROOT_PATH.resolve("site/_data/contributors.yml").toFile(); // 获取contributors.yml文件
    JavaType listType = // 定义Java类型
        mapper.getTypeFactory() // 获取类型工厂
            .constructCollectionType(List.class, Contributor.class); // 构建Contributor列表类型
    List<Contributor> contributors = // 读取贡献者列表
        mapper.readValue(contributorsFile, listType); // 从YAML文件解析贡献者数据
    Contributor contributor = // 查找第一个排序错误的贡献者
        firstOutOfOrder(contributors, // 检查贡献者列表
            Comparator.comparing(c -> c.name, String.CASE_INSENSITIVE_ORDER)); // 使用不区分大小写的名称比较器
    if (contributor != null) { // 如果找到排序错误的贡献者
      fail("contributor '" + contributor.name + "' is out of order"); // 测试失败，报告排序错误的贡献者
    }
  }

  /** Ensures that the {@code .mailmap} file is sorted. */ // 测试方法，确保.mailmap文件已排序
  @Test void testMailmapFile() { // 测试方法定义
    final File mailmapFile = ROOT_PATH.resolve(".mailmap").toFile(); // 获取.mailmap文件
    final List<String> lines = new ArrayList<>(); // 创建行列表
    forEachLineIn(mailmapFile, line -> { // 遍历文件中的每一行
      if (!line.startsWith("#")) { // 如果行不是注释（不以#开头）
        lines.add(line); // 则添加到列表中
      }
    });
    String line = firstOutOfOrder(lines, String.CASE_INSENSITIVE_ORDER); // 查找第一个排序错误的行
    if (line != null) { // 如果找到排序错误的行
      fail("line '" + line + "' is out of order"); // 测试失败，报告排序错误的行
    }
  }

  /** Performs an action for each line in a file. */ // 对文件中的每一行执行操作
  private static void forEachLineIn(File file, Consumer<String> consumer) { // 私有静态方法，遍历文件行并执行消费者函数
    try (BufferedReader r = Util.reader(file)) { // 使用try-with-resources创建缓冲读取器
      for (;;) { // 无限循环
        String line = r.readLine(); // 读取一行
        if (line == null) { // 如果读取到文件末尾
          break; // 退出循环
        }
        consumer.accept(line); // 对每行执行消费者操作
      }
    } catch (IOException e) { // 捕获IO异常
      throw Util.throwAsRuntime(e); // 将IO异常包装为运行时异常抛出
    }
  }

  /** Returns the first element in a list that is out of order, or null if the
   * list is sorted. */ // 返回列表中第一个排序错误的元素，如果列表已排序则返回null
  private static <E> @Nullable E firstOutOfOrder(Iterable<E> elements, // 私有静态泛型方法，查找第一个排序错误的元素
      Comparator<E> comparator) { // 接收元素集合和比较器
    E previous = null; // 前一个元素，初始为null
    for (E e : elements) { // 遍历所有元素
      if (previous != null && comparator.compare(previous, e) > 0) { // 如果前一个元素不为null且大于当前元素
        return e; // 返回当前元素（第一个排序错误的元素）
      }
      previous = e; // 更新前一个元素
    }
    return null; // 返回null表示列表已排序
  }

  /** Warning that code is not as it should be. */ // 消息类，表示代码不符合规范的警告
  private static class Message { // 私有静态内部类，存储lint检查发现的错误消息
    final Source source; // 源代码文件对象
    final int line; // 错误所在行号
    final String message; // 错误消息内容

    Message(Source source, int line, String message) { // 构造函数，初始化消息对象
      this.source = source; // 设置源代码文件
      this.line = line; // 设置行号
      this.message = message; // 设置错误消息
    }

    @Override public String toString() { // 重写toString方法，返回格式化的错误消息
      return source + ":" + line + ":" + message; // 返回"文件:行号:消息"格式
    }
  }

  /** Internal state of the lint rules. */ // lint规则的全局内部状态
  private static class GlobalState { // 私有静态内部类，存储lint检查的全局状态
    int fileCount = 0; // 已处理的文件数量
    final List<Message> messages = new ArrayList<>(); // 错误消息列表
  }

  /** Internal state of the lint rules, per file. */ // lint规则的文件内部状态
  private static class FileState { // 私有静态内部类，存储单个文件的lint检查状态
    final GlobalState global; // 全局状态引用
    int skipToLine; // 跳过到的行号（用于lint:skip指令）
    int starLine; // 上一个Javadoc空白行（以"*"开头）的行号
    int atLine; // 上一个Javadoc标签（@param、@return等）的行号
    int javadocStartLine; // Javadoc注释开始行号
    int javadocEndLine; // Javadoc注释结束行号
    int blockquoteCount; // 嵌套的<blockquote>块引用计数
    int ulCount; // 嵌套的<ul>无序列表计数

    FileState(GlobalState global) { // 构造函数，初始化文件状态
      this.global = global; // 设置全局状态引用
    }

    void message(String message, Puffin.Line<GlobalState, FileState> line) { // 添加错误消息到全局状态
      global.messages.add(new Message(line.source(), line.fnr(), message)); // 创建消息对象并添加到全局消息列表
    }

    public boolean inJavadoc() { // 判断是否在Javadoc注释中
      return javadocEndLine < javadocStartLine; // 返回true如果结束行号小于开始行号（表示Javadoc未结束）
    }
  }

  /** Contributor element in "contributors.yaml" file. */ // 贡献者类，表示contributors.yaml文件中的贡献者元素
  @JsonIgnoreProperties(ignoreUnknown = true) // Jackson注解，忽略YAML文件中的未知属性
  private static class Contributor { // 私有静态内部类，表示贡献者
    final String name; // 贡献者名称

    @JsonCreator Contributor(@JsonProperty("name") String name) { // Jackson注解，标记为JSON反序列化构造函数
      this.name = name; // 设置贡献者名称
    }
  }
} // LintTest类结束
