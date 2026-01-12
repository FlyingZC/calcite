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
package org.apache.calcite.slt; // 定义包名为 org.apache.calcite.slt，slt代表SQL Logic Test

import org.apache.calcite.slt.executors.CalciteExecutor; // 导入Calcite执行器，用于执行SQL逻辑测试
import org.apache.calcite.util.trace.CalciteTrace; // 导入Calcite跟踪工具，用于日志记录

import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合类

import net.hydromatic.sqllogictest.Main; // 导入SQL逻辑测试的主类
import net.hydromatic.sqllogictest.OptionsParser; // 导入选项解析器，用于解析测试选项
import net.hydromatic.sqllogictest.TestStatistics; // 导入测试统计信息类，记录测试执行结果

import org.junit.jupiter.api.Assumptions; // 导入JUnit 5的假设工具，用于跳过某些测试
import org.junit.jupiter.api.Disabled; // 导入JUnit 5的禁用注解
import org.junit.jupiter.api.DynamicTest; // 导入JUnit 5的动态测试类
import org.junit.jupiter.api.Tag; // 导入JUnit 5的标签注解，用于标记测试
import org.junit.jupiter.api.TestFactory; // 导入JUnit 5的测试工厂注解，用于动态生成测试
import org.slf4j.Logger; // 导入SLF4J日志接口

import java.io.BufferedReader; // 导入缓冲读取器，用于高效读取文本
import java.io.BufferedWriter; // 导入缓冲写入器，用于高效写入文本
import java.io.File; // 导入文件类，用于文件操作
import java.io.IOException; // 导入IO异常类
import java.io.InputStream; // 导入输入流类
import java.io.InputStreamReader; // 导入输入流读取器，将字节流转换为字符流
import java.io.OutputStream; // 导入输出流类
import java.io.OutputStreamWriter; // 导入输出流写入器，将字符流转换为字节流
import java.io.PrintStream; // 导入打印流，方便输出
import java.io.UnsupportedEncodingException; // 导入不支持的编码异常类
import java.nio.charset.StandardCharsets; // 导入标准字符集
import java.nio.file.Files; // 导入文件工具类，用于文件操作
import java.util.ArrayList; // 导入动态数组类
import java.util.Comparator; // 导入比较器接口
import java.util.HashMap; // 导入哈希映射类
import java.util.List; // 导入列表接口
import java.util.Map; // 导入映射接口
import java.util.Set; // 导入集合接口

import static org.junit.jupiter.api.Assertions.assertFalse; // 导入JUnit 5的断言方法

import static java.lang.Integer.parseInt; // 导入整数解析方法
import static java.util.Objects.requireNonNull; // 导入对象非空检查方法

/**
 * Tests using sql-logic-test suite. // 使用sql-logic-test测试套件进行测试
 *
 * <p>For each test file the number of failed tests is saved in a "golden" file. // 每个测试文件的失败测试数量保存在一个"golden"文件中
 * These results are checked in as part of the sltttestfailures.txt resource file. // 这些结果作为slttestfailures.txt资源文件的一部分被检入
 * Currently, there are quite a few errors, so this tool does not track of the actual // 目前有很多错误，所以这个工具不跟踪实际遇到的错误
 * errors that were encountered; we expect that, as bugs are fixed in Calcite, // 我们期望随着Calcite中bug的修复
 * the number of errors will shrink, and a more precise accounting method will be used. // 错误数量会减少，并使用更精确的统计方法
 *
 * <p>The tests will fail if any test script generates // 如果任何测试脚本生成的错误数量
 * more errors than the number from the golden file. // 超过golden文件中的数量，测试将失败
 */
public class SqlLogicTests { // SqlLogicTests类：使用SQL逻辑测试套件测试Calcite的核心测试类
  private static final Logger LOGGER = // 定义静态日志记录器，用于记录测试过程中的信息
      CalciteTrace.getTestTracer(SqlLogicTests.class); // 使用Calcite跟踪工具获取测试追踪器

  /**
   * Short summary of the results of a test execution. // 测试执行结果的简短摘要
   */
  public static class TestSummary { // TestSummary类：单个测试文件执行结果的摘要信息
    /**
     * File containing tests. // 包含测试的文件路径
     */
    final String file; // 测试文件名，存储测试文件的路径
    /**
     * Number of tests that have failed. // 失败的测试数量
     */
    final int failed; // 失败测试数，记录该测试文件中失败的测试用例数量

    TestSummary(String file, int failed) { // TestSummary构造方法：创建测试摘要对象
      this.file = file; // 初始化测试文件名
      this.failed = failed; // 初始化失败测试数量
    }

    /**
     * Parses a TestSummary from a string. // 从字符串解析TestSummary对象
     * The inverse of 'toString'. // toString方法的逆操作
     *
     * @return The parsed TestSummary or null on failure. // 返回解析的TestSummary对象，失败则返回null
     */
    public static TestSummary parse(String line) { // parse方法：将字符串解析为TestSummary对象
      String[] parts = line.split(":"); // 使用冒号分隔字符串，获取文件名和失败数
      if (parts.length != 2) { // 如果分隔后不是两部分，说明格式错误
        return null; // 返回null表示解析失败
      }
      try { // 尝试解析失败数量
        int failed = parseInt(parts[1]); // 将第二部分转换为整数
        return new TestSummary(parts[0], failed); // 创建并返回TestSummary对象
      } catch (NumberFormatException ex) { // 捕获数字格式异常
        return null; // 返回null表示解析失败
      }
    }

    @Override public String toString() { // toString方法：将TestSummary转换为字符串
      return this.file + ":" + this.failed; // 返回格式为"文件名:失败数"的字符串
    }

    /**
     * Check if the 'other' TestSummaries indicate a regressions // 检查'other' TestSummary是否表示回归
     * when compared to 'this'. // 与'this'相比
     *
     * @param other TestSummary to compare against. // 要比较的TestSummary对象
     * @return 'true' if 'other' is a regression from 'this'. // 如果'other'比'this'更差则返回true
     */
    public boolean isRegression(TestSummary other) { // isRegression方法：判断是否出现回归（失败数增加）
      return other.failed > this.failed; // 如果other的失败数大于this的失败数，说明出现回归
    }
  }

  /**
   * Summary for all tests executed. // 所有已执行测试的摘要
   */
  public static class AllTestSummaries { // AllTestSummaries类：管理所有测试文件的执行结果摘要
    /**
     * Map test summary name to test summary. // 将测试摘要名称映射到测试摘要对象
     */
    final Map<String, TestSummary> testResults; // 测试结果映射，key为文件名，value为TestSummary对象

    AllTestSummaries() { // AllTestSummaries构造方法：初始化测试摘要集合
      this.testResults = new HashMap<>(); // 创建空的HashMap用于存储测试结果
    }

    void add(TestSummary summary) { // add方法：添加一个测试摘要到集合中
      this.testResults.put(summary.file, summary); // 使用文件名作为key，将TestSummary存入map
    }

    AllTestSummaries read(InputStream stream) { // read方法：从输入流读取测试摘要数据
      try (BufferedReader reader = // 使用try-with-resources自动关闭资源
               new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { // 创建缓冲读取器，使用UTF-8编码
        reader.lines().forEach(line -> { // 逐行读取输入流
          TestSummary summary = TestSummary.parse(line); // 将每行解析为TestSummary对象
          if (summary != null) { // 如果解析成功
            this.add(summary); // 添加到测试结果集合中
          } else { // 如果解析失败
            LOGGER.warn("Could not parse line " + line); // 记录警告日志
          }
        });
        return this; // 返回当前对象，支持链式调用
      } catch (IOException ex) { // 捕获IO异常
        // Wrapping the IOException makes it easier to use this method in the // 将IOException包装为RuntimeException
        // initializer of a static variable. // 使其更容易在静态变量初始化器中使用
        throw new RuntimeException(ex); // 抛出运行时异常
      }
    }

    boolean regression(TestSummary summary) { // regression方法：检查测试摘要是否表示回归
      TestSummary original = this.testResults.get(summary.file); // 从历史数据中获取该文件的原始摘要
      if (original == null) { // 如果找不到历史数据
        LOGGER.warn("No historical data for test " + summary.file); // 记录警告日志
        return false; // 返回false，不认为是回归
      }
      if (original.isRegression(summary)) { // 检查是否出现回归
        LOGGER.error("Regression: " + original.file // 记录错误日志，显示回归信息
            + " had " + original.failed + " failures, now has " + summary.failed);
        return true; // 返回true表示出现回归
      }
      return false; // 返回false表示没有回归
    }

    @Override public String toString() { // toString方法：将所有测试摘要转换为字符串
      List<TestSummary> results = new ArrayList<>(this.testResults.values()); // 将map的值转换为列表
      results.sort(Comparator.comparing(left -> left.file)); // 按文件名排序
      StringBuilder result = new StringBuilder(); // 创建字符串构建器
      for (TestSummary summary : results) { // 遍历所有测试摘要
        result.append(summary.toString()); // 添加每个摘要的字符串表示
        result.append(System.lineSeparator()); // 添加系统行分隔符
      }
      return result.toString(); // 返回构建的字符串
    }

    /**
     * Write the test results to the specified file. // 将测试结果写入指定文件
     */
    public void writeToFile(File file) throws IOException { // writeToFile方法：将测试摘要写入文件
      try (BufferedWriter writer = // 使用try-with-resources自动关闭资源
               new BufferedWriter( // 创建缓冲写入器
                   new OutputStreamWriter( // 创建输出流写入器
                       Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) { // 使用UTF-8编码
        writer.write(this.toString()); // 将toString的结果写入文件
      }
    }
  }

  /**
   * Summaries produced for the current run. // 当前运行产生的摘要
   * Must be static since it is written by the `findRegressions` // 必须是静态的，因为它被静态方法`findRegressions`写入
   * static method.
   */
  private static final AllTestSummaries SUMMARIES = new AllTestSummaries(); // 当前测试运行的摘要集合，静态常量

  private static final String GOLDEN_FILE = "/slttestfailures.txt"; // Golden文件路径，存储历史测试失败数
  /**
   * Summaries checked-in as resources that we compare against. // 作为资源检入的摘要，用于比较
   */
  private static final AllTestSummaries GOLDEN_SUMMARIES = // Golden摘要集合，从资源文件加载的历史数据
      new AllTestSummaries() // 创建新的AllTestSummaries对象
          .read(SqlLogicTests.class.getResourceAsStream(GOLDEN_FILE)); // 从资源文件读取历史测试摘要
  /**
   * The following tests currently timeout during execution. // 以下测试在执行时当前会超时
   * Technically these are Calcite bugs. // 技术上这些是Calcite的bug
   */
  private static final Set<String> TIMEOUT = // 超时测试集合，包含已知会超时的测试文件
      ImmutableSet.of("test/select5.test", // 使用ImmutableSet创建不可变集合
          "test/random/groupby/slt_good_10.test"); // 包含两个会超时的测试文件

  /**
   * The following tests contain SQL statements that are not supported by HSQLDB. // 以下测试包含HSQLDB不支持的SQL语句
   */
  private static final Set<String> UNSUPPORTED = // 不支持的测试集合，包含HSQLDB不支持的测试文件
      ImmutableSet.of("test/evidence/slt_lang_replace.test", // 使用ImmutableSet创建不可变集合
          "test/evidence/slt_lang_createtrigger.test", // 包含REPLACE语句
          "test/evidence/slt_lang_droptrigger.test", // 包含DROP TRIGGER语句
          "test/evidence/slt_lang_update.test", // 包含UPDATE语句
          "test/evidence/slt_lang_reindex.test"); // 包含REINDEX语句

  private static void runTestFile(String testFile) { // runTestFile方法：运行单个测试文件
    Assumptions.assumeFalse(TIMEOUT.contains(testFile), testFile + " currently timeouts"); // 如果测试会超时则跳过
    Assumptions.assumeFalse(UNSUPPORTED.contains(testFile), // 如果测试包含不支持的语句则跳过
        testFile + " contains unsupported statements");
    OptionsParser options = new OptionsParser(false, nullStream(), nullStream()); // 创建选项解析器，禁用详细输出
    CalciteExecutor.register(options); // 注册Calcite执行器到选项解析器
    TestStatistics res; // 声明测试统计变量
    try { // 尝试执行测试
      res = Main.execute(options, "-e", "calcite", testFile); // 使用calcite引擎执行测试文件
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 包装为运行时异常抛出
    }
    checkStatsForSingleRun(res); // 检查单次运行的统计信息
    TestSummary summary = new TestSummary(testFile, res.getFailedTestCount()); // 创建测试摘要
    boolean regression = GOLDEN_SUMMARIES.regression(summary); // 检查是否出现回归
    assertFalse(regression, "Regression in " + summary.file); // 断言没有出现回归
    // The following is only useful if a new golden file need to be created // 以下代码仅在需要创建新的golden文件时有用
    SUMMARIES.add(summary); // 将摘要添加到当前运行摘要集合
  }

  private static PrintStream nullStream() { // nullStream方法：创建一个丢弃所有输出的空流
    try { // 尝试创建打印流
      return new PrintStream(new OutputStream() { // 创建匿名OutputStream子类
        @Override public void write(final int b) { // 重写write方法
          // Do nothing // 什么都不做，丢弃所有输出
        }
      }, false, "UTF-8"); // 创建打印流，不自动刷新，使用UTF-8编码
    } catch (UnsupportedEncodingException e) { // 捕获不支持的编码异常
      throw new RuntimeException(e); // 包装为运行时异常抛出
    }
  }

  private static void checkStatsForSingleRun(TestStatistics stats) { // checkStatsForSingleRun方法：检查单次运行的统计信息
    requireNonNull(stats, "stats"); // 确保stats不为null
    if (stats.getParseFailureCount() > 0) { // 如果有解析失败
      throw new IllegalStateException("Failed to parse file"); // 抛出异常表示文件解析失败
    } else if (stats.getIgnoredTestCount() > 0) { // 如果有被忽略的测试
      throw new IllegalStateException("File was ignored"); // 抛出异常表示文件被忽略
    } else if (stats.getTestFileCount() > 1) { // 如果测试文件数量大于1
      throw new IllegalStateException("Running multiple files not supported"); // 抛出异常表示不支持运行多个文件
    }
  }

  @TestFactory @Tag("slow") // 使用TestFactory注解标记为测试工厂，使用Tag标记为慢速测试
  List<DynamicTest> testSlow() { // testSlow方法：生成慢速测试列表
    return generateTests(ImmutableSet.of("select1.test")); // 只生成select1.test这一个测试
  }

  @TestFactory @Disabled("This takes very long, should be run manually") // 使用TestFactory注解，禁用此测试，因为运行时间很长
  List<DynamicTest> testAll() { // testAll方法：生成所有测试的列表
    // Run in parallel each test file.  There are 622 of these, each taking // 并行运行每个测试文件，共有622个测试文件
    // a few minutes. // 每个文件需要几分钟
    return generateTests(Main.getTestList()); // 生成所有测试文件的动态测试
  }

  /**
   * Generate a list of all the tests that can be executed. // 生成所有可执行测试的列表
   *
   * @param testFiles Names of files containing tests. // 包含测试的文件名集合
   */
  private List<DynamicTest> generateTests(Set<String> testFiles) { // generateTests方法：根据测试文件集合生成动态测试列表
    List<DynamicTest> result = new ArrayList<>(); // 创建动态测试列表
    for (String test : testFiles) { // 遍历每个测试文件
      DynamicTest dynamicTest = DynamicTest.dynamicTest(test, () -> runTestFile(test)); // 为每个文件创建动态测试
      result.add(dynamicTest); // 将动态测试添加到结果列表
    }
    return result; // 返回动态测试列表
  }

  /**
   * Create the golden reference file with test results. // 创建包含测试结果的golden参考文件
   */
  public static void createGoldenFile() throws IOException { // createGoldenFile方法：创建golden文件
    // Currently this method is not invoked. // 当前此方法未被调用
    // It can be used to create a new version of the GOLDEN_FILE // 它可以用于创建新版本的GOLDEN_FILE
    // when bugs in Calcite are fixed.  It should be called after // 当Calcite中的bug被修复时使用
    // all tests have executed and passed. // 应该在所有测试执行并通过后调用
    File file = new File(GOLDEN_FILE); // 创建golden文件对象
    if (!file.exists()) { // 如果文件不存在
      SUMMARIES.writeToFile(file); // 将当前摘要写入文件
    }
  }
}
