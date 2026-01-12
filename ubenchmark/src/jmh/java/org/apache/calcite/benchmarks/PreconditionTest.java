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
package org.apache.calcite.benchmarks; // 包声明：该类位于org.apache.calcite.benchmarks包下，属于Calcite框架的性能基准测试模块

import org.openjdk.jmh.annotations.Benchmark; // 导入JMH注解：用于标记需要执行性能测试的基准测试方法
import org.openjdk.jmh.annotations.BenchmarkMode; // 导入JMH注解：用于指定基准测试的模式，如平均时间、吞吐量等
import org.openjdk.jmh.annotations.Mode; // 导入JMH枚举：定义了各种基准测试模式，如AverageTime、Throughput等
import org.openjdk.jmh.annotations.Scope; // 导入JMH注解：用于指定基准测试状态的作用域范围
import org.openjdk.jmh.annotations.State; // 导入JMH注解：用于标记存储测试状态的类，控制测试实例的共享级别
import org.openjdk.jmh.profile.GCProfiler; // 导入JMH性能分析器：用于在基准测试中监控和记录垃圾回收信息
import org.openjdk.jmh.runner.Runner; // 导入JMH运行器：用于执行基准测试的核心类
import org.openjdk.jmh.runner.RunnerException; // 导入JMH异常：表示基准测试运行过程中发生的异常
import org.openjdk.jmh.runner.options.Options; // 导入JMH选项接口：用于配置基准测试运行时的各种参数
import org.openjdk.jmh.runner.options.OptionsBuilder; // 导入JMH选项构建器：用于链式构建和配置基准测试选项

import static com.google.common.base.Preconditions.checkState; // 静态导入Google Guava库的前置条件检查方法，用于验证状态是否满足预期

/**
 * Checks if silent precondition has noticeable overhead. // 类注释：该基准测试类用于检查静默前置条件检查是否会产生明显的性能开销
 * PreconditionTest是一个JMH基准测试类，专门用于评估和测量前置条件检查操作的性能影响
 * 通过对比测试，可以了解使用Guava的checkState等前置条件检查方法对程序执行效率的影响程度
 * 这对于在Calcite框架中决定是否在关键路径上使用前置条件检查提供了数据支持
 */
@BenchmarkMode(Mode.AverageTime) // JMH注解：设置基准测试模式为平均时间，即测量方法执行的平均耗时
@State(Scope.Benchmark) // JMH注解：声明该类的状态作用域为Benchmark级别，表示在所有基准测试线程之间共享同一个实例
public class PreconditionTest { // 类定义：PreconditionTest类，用于测试前置条件检查的性能开销
  boolean fire = true; // 成员变量：布尔类型标志，用于控制前置条件检查是否触发异常，true表示条件满足
  String param = "world"; // 成员变量：字符串参数，用于在前置条件检查失败时构造错误消息，作为格式化参数

  @Benchmark // JMH注解：标记该方法为基准测试方法，JMH会多次调用此方法并统计性能数据
  public void testPrecondition() { // 基准测试方法：测试前置条件检查的性能，测量checkState方法的执行开销
    checkState(fire, "Hello %s", param); // 调用Guava的checkState方法，验证fire状态是否为true，否则抛出带格式化消息的IllegalStateException异常
  }

  public static void main(String[] args) throws RunnerException { // 主方法：基准测试的入口点，用于创建并执行JMH基准测试配置
    Options opt = new OptionsBuilder() // 创建JMH选项构建器对象，用于链式配置基准测试的各种参数
        .include(PreconditionTest.class.getSimpleName()) // 设置要包含的基准测试类，只执行PreconditionTest类中的测试方法
        .addProfiler(GCProfiler.class) // 添加GC性能分析器，用于监控和记录基准测试过程中的垃圾回收情况
        .detectJvmArgs() // 自动检测并应用当前JVM的参数，确保测试环境与实际运行环境一致
        .build(); // 构建并返回配置完成的Options对象

    new Runner(opt).run(); // 创建JMH运行器并执行基准测试，根据配置的opt参数运行所有测试方法并输出性能结果
  }

}
