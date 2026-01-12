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
package org.apache.calcite.benchmarks; // 包声明：该类属于org.apache.calcite.benchmarks包，用于存放Calcite框架的性能基准测试类

import org.apache.calcite.sql.SqlNode; // 导入SqlNode类，表示SQL语法树中的节点，是所有SQL节点的基类
import org.apache.calcite.sql.parser.SqlParseException; // 导入SqlParseException类，用于处理SQL解析过程中出现的异常
import org.apache.calcite.sql.parser.SqlParser; // 导入SqlParser类，是Calcite的SQL解析器，负责将SQL字符串解析为SqlNode语法树

import org.openjdk.jmh.annotations.Benchmark; // 导入Benchmark注解，用于标记需要进行性能测试的方法
import org.openjdk.jmh.annotations.BenchmarkMode; // 导入BenchmarkMode注解，用于指定基准测试的模式（如平均时间、吞吐量等）
import org.openjdk.jmh.annotations.Fork; // 导入Fork注解，用于指定测试的fork次数和JVM参数
import org.openjdk.jmh.annotations.Measurement; // 导入Measurement注解，用于指定实际测量的迭代次数和时间
import org.openjdk.jmh.annotations.Mode; // 导入Mode枚举，定义了基准测试的各种模式（AverageTime、Throughput等）
import org.openjdk.jmh.annotations.OutputTimeUnit; // 导入OutputTimeUnit注解，用于指定输出结果的时间单位
import org.openjdk.jmh.annotations.Param; // 导入Param注解，用于为基准测试方法提供参数化的输入值
import org.openjdk.jmh.annotations.Scope; // 导入Scope枚举，定义了基准测试状态对象的作用域（Thread、Benchmark、Group）
import org.openjdk.jmh.annotations.Setup; // 导入Setup注解，用于标记在每次基准测试前执行的初始化方法
import org.openjdk.jmh.annotations.State; // 导入State注解，用于标记在基准测试中共享的状态对象
import org.openjdk.jmh.annotations.Threads; // 导入Threads注解，用于指定基准测试使用的线程数
import org.openjdk.jmh.annotations.Warmup; // 导入Warmup注解，用于指定预热阶段的迭代次数和时间
import org.openjdk.jmh.profile.GCProfiler; // 导入GCProfiler类，用于在基准测试中收集垃圾回收相关的性能数据
import org.openjdk.jmh.runner.Runner; // 导入Runner类，用于执行基准测试
import org.openjdk.jmh.runner.RunnerException; // 导入RunnerException类，用于处理基准测试运行过程中的异常
import org.openjdk.jmh.runner.options.Options; // 导入Options接口，用于配置基准测试的运行选项
import org.openjdk.jmh.runner.options.OptionsBuilder; // 导入OptionsBuilder类，用于构建基准测试的运行选项配置

import java.util.Random; // 导入Random类，用于生成随机数，在基准测试中用于生成随机SQL语句
import java.util.concurrent.TimeUnit; // 导入TimeUnit枚举，用于表示时间单位（秒、毫秒、微秒等）

/**
 * Benchmarks JavaCC-generated SQL parser.
 */
@Fork(value = 1, jvmArgsPrepend = "-Xmx128m") // Fork注解：指定测试进程fork次数为1次，并在JVM启动参数前添加-Xmx128m（设置最大堆内存为128MB）
@Measurement(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS) // Measurement注解：指定实际测量阶段执行7次迭代，每次迭代持续1秒
@Warmup(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS) // Warmup注解：指定预热阶段执行7次迭代，每次迭代持续1秒，让JVM进行JIT优化
@State(Scope.Thread) // State注解：指定该类的实例作用域为Thread级别，每个测试线程拥有独立的状态对象
@Threads(1) // Threads注解：指定基准测试使用1个线程执行
@BenchmarkMode(Mode.AverageTime) // BenchmarkMode注解：指定测试模式为平均时间，测量每次操作的平均耗时
@OutputTimeUnit(TimeUnit.MICROSECONDS) // OutputTimeUnit注解：指定输出结果的时间单位为微秒
public class ParserBenchmark { // ParserBenchmark类：用于测试Calcite SQL解析器性能的基准测试类，主要测试JavaCC生成的SQL解析器的性能

  @Param({ "1000" }) // Param注解：参数化配置，指定length参数的值为1000，表示生成的SQL语句长度为1000个字符
  int length; // 成员变量：表示生成的SQL语句的目标长度，单位是字符数

  @Param({ "true" }) // Param注解：参数化配置，指定comments参数的值为true，表示生成的SQL语句中包含注释
  boolean comments; // 成员变量：布尔值，表示是否在生成的SQL语句中包含注释（//开头的单行注释）

  String sql; // 成员变量：存储生成的待解析SQL语句字符串，在setup方法中初始化
  SqlParser parser; // 成员变量：SqlParser实例，用于解析SQL语句，在parseCached方法中复用以测试解析器缓存效果

  @Setup // Setup注解：标记此方法为基准测试前的初始化方法，在每次测试前执行一次
  public void setup() { // setup方法：初始化测试数据，生成指定长度的随机SQL语句
    StringBuilder sb = new StringBuilder((int) (length * 1.2)); // 创建StringBuilder对象，初始容量为length的1.2倍，预留空间避免扩容
    sb.append("select 1"); // 添加SQL语句的开头部分"select 1"
    Random rnd = new Random(); // 创建Random随机数生成器对象
    rnd.setSeed(424242); // 设置随机数种子为424242，确保每次生成的SQL语句相同，保证测试结果的可重复性
    for (; sb.length() < length;) { // 外层循环：持续添加内容直到SQL语句长度达到指定的length值
      for (int i = 0; i < 7 && sb.length() < length; i++) { // 内层循环：每行最多添加7个字段
        sb.append(", "); // 添加字段分隔符", "
        switch (rnd.nextInt(3)) { // 根据随机数（0、1、2）选择添加不同类型的字段值
        case 0: // 当随机数为0时
          sb.append("?"); // 添加问号"?"，表示SQL中的动态参数占位符
          break; // 跳出switch语句
        case 1: // 当随机数为1时
          sb.append(rnd.nextInt()); // 添加一个随机整数
          break; // 跳出switch语句
        case 2: // 当随机数为2时
          sb.append('\'').append(rnd.nextLong()).append(rnd.nextLong()) // 添加单引号开始字符串字面量，然后添加两个随机长整数
              .append('\''); // 添加单引号结束字符串字面量
          break; // 跳出switch语句
        default: // 默认情况（实际上不会执行，因为nextInt(3)只返回0、1、2）
          break; // 跳出switch语句
        }
      }
      if (comments && sb.length() < length) { // 如果comments参数为true且SQL长度还未达到目标值
        sb.append("// sb.append('\\'').append(rnd.nextLong()).append(rnd.nextLong()).append(rnd" // 添加一行SQL注释（//开头），注释内容模拟代码
            + ".nextLong())"); // 注释内容续接，包含模拟的代码字符串
      }
      sb.append('\n'); // 添加换行符，开始新的一行
    }
    sb.append(" from dual"); // 添加SQL语句的FROM子句，使用dual表（Oracle中的虚拟表）
    parser = SqlParser.create("values(1)"); // 创建SqlParser实例，传入"values(1)"作为初始SQL，用于测试解析器缓存效果
    sql = sb.toString(); // 将StringBuilder转换为String，赋值给sql成员变量，供后续解析方法使用
  }

  @Benchmark // Benchmark注解：标记此方法为基准测试方法，JMH会多次执行此方法并测量性能
  public SqlNode parseCached() throws SqlParseException { // parseCached方法：测试使用缓存的SqlParser实例解析SQL的性能
    return parser.parseQuery(sql); // 使用预先创建的parser实例解析sql字符串，返回解析后的SqlNode语法树节点
  }

  @Benchmark // Benchmark注解：标记此方法为基准测试方法，JMH会多次执行此方法并测量性能
  public SqlNode parseNonCached() throws SqlParseException { // parseNonCached方法：测试每次创建新SqlParser实例解析SQL的性能（不使用缓存）
    return SqlParser.create(sql).parseQuery(); // 每次都创建新的SqlParser实例并解析sql字符串，返回解析后的SqlNode语法树节点
  }

  public static void main(String[] args) throws RunnerException { // main方法：程序的入口点，用于手动运行基准测试
    Options opt = new OptionsBuilder() // 创建OptionsBuilder对象，用于构建基准测试的运行配置
        .include(ParserBenchmark.class.getSimpleName()) // include方法：指定只运行ParserBenchmark类的基准测试
        .addProfiler(GCProfiler.class) // addProfiler方法：添加GCProfiler性能分析器，用于收集垃圾回收相关数据
        .addProfiler(FlightRecorderProfiler.class) // addProfiler方法：添加FlightRecorderProfiler性能分析器（注意：此类可能需要额外导入）
        .detectJvmArgs() // detectJvmArgs方法：自动检测当前JVM的参数并应用到基准测试中
        .build(); // build方法：构建最终的Options配置对象

    new Runner(opt).run(); // 创建Runner实例并传入配置对象，调用run方法执行基准测试
  }

}
