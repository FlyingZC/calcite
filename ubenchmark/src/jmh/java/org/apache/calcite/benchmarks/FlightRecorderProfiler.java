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
package org.apache.calcite.benchmarks; // 定义包名，该类位于 org.apache.calcite.benchmarks 包下

import org.openjdk.jmh.infra.BenchmarkParams; // 导入 JMH 基准测试参数类，用于获取基准测试的配置信息
import org.openjdk.jmh.infra.IterationParams; // 导入 JMH 迭代参数类，用于获取预热或测量阶段的配置信息
import org.openjdk.jmh.profile.ExternalProfiler; // 导入 JMH 外部分析器接口，实现该接口可以自定义性能分析工具
import org.openjdk.jmh.results.BenchmarkResult; // 导入 JMH 基准测试结果类，包含测试运行后的结果数据
import org.openjdk.jmh.results.Result; // 导入 JMH 结果类，表示单个测试指标的结果

import java.io.File; // 导入 Java 文件类，用于处理标准输出和标准错误文件
import java.util.Arrays; // 导入 Java 数组工具类，用于创建固定大小的列表
import java.util.Collection; // 导入 Java 集合接口，用于返回 JVM 选项集合
import java.util.Collections; // 导入 Java 集合工具类，用于创建空集合
import java.util.concurrent.TimeUnit; // 导入 Java 时间单位枚举，用于时间单位转换

/**
 * Captures Flight Recorder log. // 捕获 Java Flight Recorder 日志记录
 * Note: Flight Recorder is available in OracleJDK only. // 注意：Flight Recorder 仅在 OracleJDK 中可用
 * Usage of Flight Recorder in production requires a LICENSE FEE, however Flight Recorder is free // 在生产环境中使用 Flight Recorder 需要付费许可，但在测试系统中免费
 * for use in test systems. // 在测试系统中使用 Flight Recorder 是免费的
 * It is assumed you would not use Calcite benchmarks for running a production system, thus it is // 假设您不会使用 Calcite 基准测试来运行生产系统，因此它是安全的
 * believed to be safe. // 认为是安全的
 */
public class FlightRecorderProfiler implements ExternalProfiler { // 定义 FlightRecorderProfiler 类，实现 ExternalProfiler 接口，用于在 JMH 基准测试中集成 Java Flight Recorder
  @Override public Collection<String> addJVMInvokeOptions(BenchmarkParams params) { // 重写 ExternalProfiler 接口方法，用于添加 JVM 启动选项（在 java 命令中添加选项）
    return Collections.emptyList(); // 返回空集合，表示不添加任何 JVM 启动选项
  }

  @Override public Collection<String> addJVMOptions(BenchmarkParams params) { // 重写 ExternalProfiler 接口方法，用于添加 JVM 运行时选项（在 java 命令的参数部分添加选项）
    StringBuilder sb = new StringBuilder(); // 创建字符串构建器，用于构建文件名后缀
    for (String param : params.getParamsKeys()) { // 遍历基准测试的所有参数键名
      if (sb.length() != 0) { // 如果字符串构建器不为空（不是第一个参数）
        sb.append('-'); // 添加连字符分隔符
      }
      sb.append(param).append('-').append(params.getParam(param)); // 将参数键名和参数值拼接为 "键名-值" 的格式
    }

    long duration = // 计算总的录制时长（秒）
        getDurationSeconds(params.getWarmup()) + getDurationSeconds(params.getMeasurement()); // 预热阶段时长 + 测量阶段时长
    return Arrays.asList( // 返回包含 Flight Recorder 相关 JVM 选项的列表
        "-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder", // 解锁商业特性并启用 Flight Recorder 功能
        "-XX:StartFlightRecording=settings=profile,duration=" + duration + "s,filename=" // 启动 Flight Recorder，使用 profile 设置，录制时长为 duration 秒
            + params.getBenchmark() + "_" + sb + ".jfr"); // 输出文件名为 "基准测试名_参数组合.jfr"
  }

  private static long getDurationSeconds(IterationParams warmup) { // 私有静态方法，用于计算某个迭代阶段（预热或测量）的总持续时间（秒）
    return warmup.getTime().convertTo(TimeUnit.SECONDS) * warmup.getCount(); // 将单次迭代时间转换为秒，然后乘以迭代次数，得到总持续时间
  }

  @Override public void beforeTrial(BenchmarkParams benchmarkParams) { // 重写 ExternalProfiler 接口方法，在基准测试试验开始前调用
    // 方法体为空，表示在测试开始前不需要执行任何特殊操作
  }

  @Override public Collection<? extends Result> afterTrial(BenchmarkResult br, long pid, // 重写 ExternalProfiler 接口方法，在基准测试试验结束后调用
      File stdOut, File stdErr) { // 参数：br-基准测试结果对象，pid-进程ID，stdOut-标准输出文件，stdErr-标准错误文件
    return Collections.emptyList(); // 返回空集合，表示不产生额外的性能分析结果（FlightRecorder 的结果已经保存到 .jfr 文件中）
  }

  @Override public boolean allowPrintOut() { // 重写 ExternalProfiler 接口方法，询问是否允许打印标准输出
    return true; // 返回 true，允许打印标准输出
  }

  @Override public boolean allowPrintErr() { // 重写 ExternalProfiler 接口方法，询问是否允许打印标准错误
    return true; // 返回 true，允许打印标准错误
  }

  @Override public String getDescription() { // 重写 ExternalProfiler 接口方法，返回分析器的描述信息
    return "Collects Java Flight Recorder profile"; // 返回描述字符串，说明该分析器用于收集 Java Flight Recorder 性能分析数据
  }
}
