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
package org.apache.calcite.benchmarks; // 声明包名，表示该类属于org.apache.calcite.benchmarks包

import org.openjdk.jmh.annotations.Benchmark; // 导入JMH的Benchmark注解，用于标记基准测试方法
import org.openjdk.jmh.annotations.BenchmarkMode; // 导入JMH的BenchmarkMode注解，用于指定基准测试模式
import org.openjdk.jmh.annotations.Fork; // 导入JMH的Fork注解，用于指定fork进程的数量和JVM参数
import org.openjdk.jmh.annotations.Level; // 导入JMH的Level注解，用于指定Setup方法的调用级别
import org.openjdk.jmh.annotations.Measurement; // 导入JMH的Measurement注解，用于指定测量迭代的配置
import org.openjdk.jmh.annotations.Mode; // 导入JMH的Mode枚举，定义了基准测试的模式（如吞吐量、平均时间等）
import org.openjdk.jmh.annotations.OutputTimeUnit; // 导入JMH的OutputTimeUnit注解，用于指定输出时间单位
import org.openjdk.jmh.annotations.Param; // 导入JMH的Param注解，用于指定参数化测试的参数值
import org.openjdk.jmh.annotations.Scope; // 导入JMH的Scope枚举，定义了状态对象的作用域（如线程、基准测试等）
import org.openjdk.jmh.annotations.Setup; // 导入JMH的Setup注解，用于标记初始化方法
import org.openjdk.jmh.annotations.State; // 导入JMH的State注解，用于标记状态类
import org.openjdk.jmh.annotations.Threads; // 导入JMH的Threads注解，用于指定线程数
import org.openjdk.jmh.annotations.Warmup; // 导入JMH的Warmup注解，用于指定预热迭代的配置
import org.openjdk.jmh.infra.Blackhole; // 导入JMH的Blackhole类，用于防止JVM优化掉基准测试代码
import org.openjdk.jmh.runner.Runner; // 导入JMH的Runner类，用于运行基准测试
import org.openjdk.jmh.runner.RunnerException; // 导入JMH的RunnerException类，表示基准测试运行时的异常
import org.openjdk.jmh.runner.options.Options; // 导入JMH的Options接口，表示基准测试的配置选项
import org.openjdk.jmh.runner.options.OptionsBuilder; // 导入JMH的OptionsBuilder类，用于构建基准测试的配置选项

import java.io.IOException; // 导入Java的IOException类，表示I/O异常
import java.io.PrintWriter; // 导入Java的PrintWriter类，用于格式化输出文本到字符输出流
import java.io.StringWriter; // 导入Java的StringWriter类，用于将输出写入字符串缓冲区
import java.io.Writer; // 导入Java的Writer抽象类，表示字符输出流
import java.util.concurrent.TimeUnit; // 导入Java的TimeUnit枚举，表示时间单位

/**
 * A benchmark of the most common patterns that are used to construct gradually
 * String objects.
 * // 这是一个基准测试类，用于测试最常用的逐步构建String对象的模式
 *
 * <p>The benchmark emphasizes on the build patterns that appear in the Calcite
 * project.
 * // 该基准测试重点关注Calcite项目中出现的构建模式
 */
@Fork(value = 1, jvmArgsPrepend = "-Xmx2048m") // 指定fork进程数为1，并在JVM启动时设置最大堆内存为2048MB
@Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS) // 指定测量迭代次数为10次，每次迭代时间为100毫秒
@Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS) // 指定预热迭代次数为10次，每次迭代时间为100毫秒
@Threads(1) // 指定使用1个线程运行基准测试
@OutputTimeUnit(TimeUnit.MILLISECONDS) // 指定输出时间单位为毫秒
@BenchmarkMode(Mode.Throughput) // 指定基准测试模式为吞吐量模式（单位时间内执行的次数）
public class StringConstructBenchmark { // 定义StringConstructBenchmark类，用于基准测试字符串构建的性能

  /**
   * A state holding a Writer object which is initialized only once at the beginning of the
   * benchmark.
   * // 一个状态类，持有一个Writer对象，该对象在基准测试开始时只初始化一次
   */
  @State(Scope.Thread) // 指定该状态类的作用域为线程级别，每个线程都有自己的实例
  public static class WriterState { // 定义WriterState静态内部类，用于持有Writer对象
    public Writer writer; // 定义Writer类型的公共成员变量，用于存储字符输出流对象

    @Setup(Level.Trial) // 使用Setup注解，指定该方法在基准测试Trial级别开始时执行一次
    public void setup() { // 定义setup方法，用于初始化Writer对象
      this.writer = new StringWriter(); // 创建StringWriter对象并赋值给writer成员变量
    }
  }

  /**
   * A state holding an Appendable object which is initialized after a fixed number of append
   * operations.
   * // 一个状态类，持有一个Appendable对象，该对象在执行固定次数的追加操作后被重新初始化
   */
  @State(Scope.Thread) // 指定该状态类的作用域为线程级别，每个线程都有自己的实例
  public static class AppenderState { // 定义AppenderState静态内部类，用于持有和管理Appendable对象
    /**
     * The type of the appender to be initialised.
     * // 要初始化的追加器类型
     */
    @Param({"StringBuilder", "StringWriter", "PrintWriter"}) // 使用Param注解指定参数化测试的参数值，分别为StringBuilder、StringWriter和PrintWriter
    public String appenderType; // 定义String类型的公共成员变量，用于存储追加器的类型名称

    /**
     * The maximum number of appends before resetting the appender.
     *
     * <p>If the value is small then the appender is reinitialized very often,
     * making the instantiation of the appender the dominant operation of the
     * benchmark.
     * // 重置追加器之前允许的最大追加次数
     * // 如果该值较小，则追加器会被频繁重新初始化，使得追加器的实例化成为基准测试的主要操作
     */
    @Param({"1", "256", "512", "1024"}) // 使用Param注解指定参数化测试的参数值，分别为1、256、512和1024
    public int maxAppends; // 定义int类型的公共成员变量，用于存储最大追加次数

    /**
     * The appender that is currently used.
     * // 当前使用的追加器对象
     */
    private Appendable appender; // 定义Appendable类型的私有成员变量，用于存储当前使用的追加器对象

    /**
     * The number of append operations performed so far.
     * // 到目前为止执行的追加操作次数
     */
    private int nAppends = 0; // 定义int类型的私有成员变量，用于记录已执行的追加操作次数，初始值为0

    @Setup(Level.Iteration) // 使用Setup注解，指定该方法在每次迭代开始时执行
    public void setup() { // 定义setup方法，用于初始化AppenderState对象
      reset(); // 调用reset方法重置状态
    }

    private void reset() { // 定义reset方法，用于重置追加器和计数器
      nAppends = 0; // 将追加操作计数器重置为0
      if (appenderType.equals("StringBuilder")) { // 如果追加器类型是StringBuilder
        this.appender = new StringBuilder(); // 创建StringBuilder对象并赋值给appender成员变量
      } else if (appenderType.equals("StringWriter")) { // 如果追加器类型是StringWriter
        this.appender = new StringWriter(); // 创建StringWriter对象并赋值给appender成员变量
      } else if (appenderType.equals("PrintWriter")) { // 如果追加器类型是PrintWriter
        this.appender = new PrintWriter(new StringWriter()); // 创建PrintWriter对象（包装StringWriter）并赋值给appender成员变量
      } else { // 如果追加器类型不是以上任何一种
        throw new IllegalStateException( // 抛出IllegalStateException异常
            "The specified appender type (" + appenderType + ") is not supported."); // 异常消息：指定的追加器类型不受支持
      }
    }

    Appendable getOrCreateAppender() { // 定义getOrCreateAppender方法，用于获取或创建追加器对象
      if (nAppends >= maxAppends) { // 如果当前追加次数达到或超过最大追加次数
        reset(); // 调用reset方法重置追加器和计数器
      }
      nAppends++; // 追加操作计数器加1
      return appender; // 返回当前的追加器对象
    }

  }

  @Benchmark // 使用Benchmark注解标记该方法为基准测试方法
  public StringBuilder initStringBuilder() { // 定义initStringBuilder方法，用于测试StringBuilder对象的初始化性能
    return new StringBuilder(); // 创建并返回一个新的StringBuilder对象
  }

  @Benchmark // 使用Benchmark注解标记该方法为基准测试方法
  public StringWriter initStringWriter() { // 定义initStringWriter方法，用于测试StringWriter对象的初始化性能
    return new StringWriter(); // 创建并返回一个新的StringWriter对象
  }

  @Benchmark // 使用Benchmark注解标记该方法为基准测试方法
  public PrintWriter initPrintWriter(WriterState writerState) { // 定义initPrintWriter方法，用于测试PrintWriter对象的初始化性能
    return new PrintWriter(writerState.writer); // 创建并返回一个新的PrintWriter对象，使用writerState中的writer作为底层输出流
  }

  /**
   * Benchmarks the performance of instantiating different {@link Appendable} objects and appending
   * the same string a fixed number of times.
   * // 基准测试不同Appendable对象的实例化性能以及追加固定次数相同字符串的性能
   *
   * @param bh blackhole used as an optimization fence // bh参数：Blackhole对象，用作优化屏障，防止JVM优化掉基准测试代码
   * @param appenderState the state holds the type of the appender and the number of appends that
   * need to be performed before resetting the appender // appenderState参数：状态对象，持有追加器类型和重置前需要执行的追加次数
   * @throws IOException if the append operation encounters an I/O problem // 抛出IOException：如果追加操作遇到I/O问题
   */
  @Benchmark // 使用Benchmark注解标记该方法为基准测试方法
  public void appendString(Blackhole bh, AppenderState appenderState) throws IOException { // 定义appendString方法，用于测试追加字符串的性能
    bh.consume(appenderState.getOrCreateAppender().append("placeholder")); // 获取或创建追加器，追加"placeholder"字符串，并将结果传递给Blackhole以防止优化
  }

  public static void main(String[] args) throws RunnerException { // 定义main方法，用于运行基准测试
    Options opt = new OptionsBuilder() // 创建OptionsBuilder对象，用于构建基准测试配置选项
        .include(StringConstructBenchmark.class.getName()) // 指定要运行的基准测试类为StringConstructBenchmark
        .detectJvmArgs() // 自动检测JVM参数
        .build(); // 构建Options对象

    new Runner(opt).run(); // 创建Runner对象并运行基准测试
  }
}
