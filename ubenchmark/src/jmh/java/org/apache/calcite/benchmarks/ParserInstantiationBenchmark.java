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
package org.apache.calcite.benchmarks;

import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks JavaCC-generated SqlBabelParserImpl and ImmutableSqlParser instantiation.
 * The instantiation time of parsers should not depend on the call stack depth.
 * See https://lists.apache.org/thread/xw35sdy1w1k8lvn1q1lr7xb93bkj0lpq
 * 
 * 此基准测试类用于评估 JavaCC 生成的 SqlBabelParserImpl 和 ImmutableSqlParser 的实例化性能。
 * 解析器的实例化时间不应该依赖于调用栈的深度，本测试旨在验证这一特性。
 * 
 * 主要功能：
 * 1. 测试不同解析器（core 和 babel）的实例化性能
 * 2. 验证实例化时间是否受调用栈深度影响
 * 3. 使用 JMH（Java Microbenchmark Harness）框架进行性能基准测试
 * 
 * 测试场景：
 * - 通过递归调用模拟不同的调用栈深度（0 和 100）
 * - 测试两种解析器：core 解析器和 babel 解析器
 * - 测量解析器实例化的平均耗时（微秒级）
 * 
 * 应用背景：
 * 在某些情况下，解析器的实例化可能会受到调用栈深度的影响，这可能导致性能问题。
 * 本测试旨在确保解析器的实例化性能不受调用栈深度的负面影响。
 */
@Fork(value = 1, jvmArgsPrepend = "-Xmx128m") // 指定 Fork 模式：fork 1 个进程，JVM 参数前置 -Xmx128m（最大堆内存 128MB），用于隔离测试环境
@Measurement(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS) // 测量配置：执行 7 次迭代，每次 1 秒，用于收集性能数据
@Warmup(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS) // 预热配置：执行 7 次迭代，每次 1 秒，让 JVM 优化代码达到稳定状态
@State(Scope.Thread) // 状态作用域：每个线程一个实例，保证线程安全
@Threads(1) // 线程数：使用 1 个线程执行测试，避免并发干扰
@BenchmarkMode(Mode.AverageTime) // 基准测试模式：测量平均执行时间
@OutputTimeUnit(TimeUnit.MICROSECONDS) // 输出时间单位：微秒，用于精确显示性能数据
public class ParserInstantiationBenchmark { // 解析器实例化基准测试类，用于测试解析器实例化性能

  @Param({"0", "100"}) // JMH 参数注解：指定 stackDepth 参数的两个测试值为 0 和 100，用于测试不同调用栈深度下的性能
  int stackDepth; // 成员变量：调用栈深度，通过递归调用来模拟不同的栈深度，验证实例化时间是否受栈深度影响

  @Param({"core", "babel"}) // JMH 参数注解：指定 parser 参数的两个测试值为 "core" 和 "babel"，用于测试不同解析器的性能
  String parser; // 成员变量：解析器类型，"core" 表示使用核心解析器，"babel" 表示使用 Babel 解析器（支持更多 SQL 方言）

  SqlParser.Config config; // 成员变量：SqlParser 配置对象，用于配置解析器的各种参数（如解析器工厂、词法分析器配置等）
  String sqlExpression; // 成员变量：待解析的 SQL 表达式字符串，本测试中使用简单的 "SELECT 1" 作为测试用例

  @Setup // JMH 注解：标记此方法为基准测试的初始化方法，在每个测试方法执行前调用
  public void setup() { // 初始化方法：设置测试所需的配置和参数
    // not important in this benchmark, see ParserBenchmark for varying string length
    sqlExpression = "SELECT 1"; // 设置测试用的 SQL 表达式，使用简单的 SELECT 1 语句，本测试关注实例化性能而非解析性能
    switch (parser) { // 根据参数选择的解析器类型进行配置
    case "core": // 如果是 core 解析器
      config = SqlParser.config(); // 使用默认的 SqlParser 配置，即核心解析器配置
      break; // 跳出 switch 语句
    case "babel": // 如果是 babel 解析器
      config = SqlParser.config().withParserFactory(SqlBabelParserImpl.FACTORY); // 使用 Babel 解析器工厂配置，Babel 支持更多 SQL 方言特性
      break; // 跳出 switch 语句
    default: // 如果是未知的解析器类型
      throw new RuntimeException("Unsupported parser: " + parser); // 抛出运行时异常，提示不支持的解析器类型
    }
  }

  @Benchmark // JMH 注解：标记此方法为基准测试方法，JMH 会多次执行此方法并收集性能数据
  public SqlParser instantiateParser() { // 基准测试方法：测试解析器实例化性能
    return call(stackDepth); // 调用递归方法来实例化解析器，传入指定的栈深度参数
  }

  // used to increase the stack depth
  private SqlParser call(final int depth) { // 私有递归方法：通过递归调用增加调用栈深度，用于测试栈深度对解析器实例化的影响
    if (depth == 0) { // 递归终止条件：当深度降为 0 时
      return SqlParser.create(sqlExpression, config); // 创建 SqlParser 实例，传入 SQL 表达式和配置对象，这是实际的实例化操作
    } // 结束 if 块
    return call(depth - 1); // 递归调用：深度减 1 继续递归，直到深度降为 0，这样可以构建指定深度的调用栈
  }

  public static void main(String[] args) throws RunnerException { // 主方法：用于独立运行基准测试，方便开发和调试
    Options opt = new OptionsBuilder().include(ParserInstantiationBenchmark.class.getSimpleName()) // 创建 JMH 选项构建器，并指定要运行的基准测试类为 ParserInstantiationBenchmark
        .detectJvmArgs() // 自动检测当前 JVM 参数，确保测试环境与运行环境一致
        .build(); // 构建 Options 对象，包含所有配置选项

    new Runner(opt).run(); // 创建 JMH 运行器并执行基准测试，运行器会根据配置执行所有测试方法并输出性能报告
  }
}
