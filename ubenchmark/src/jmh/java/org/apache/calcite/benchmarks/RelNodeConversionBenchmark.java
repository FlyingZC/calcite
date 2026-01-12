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
package org.apache.calcite.benchmarks; // 基准测试包，包含性能测试类

import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入可查询表的抽象基类，用于创建基于Java对象的表
import org.apache.calcite.config.Lex; // 导入词法配置类，用于设置SQL解析的词法规则（如MySQL、ORACLE等）
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，提供类似LINQ的查询能力
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供静态方法创建可枚举对象
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于执行LINQ查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，支持延迟执行的查询
import org.apache.calcite.rel.RelNode; // 导入关系表达式节点接口，代表关系代数操作
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，描述表或表达式的类型信息
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.schema.SchemaPlus; // 导入模式扩展接口，用于向模式中添加表和函数
import org.apache.calcite.schema.impl.AbstractTable; // 导入抽象表基类，用于定义自定义表
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，代表SQL语法树中的节点
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器，用于将SQL字符串解析为语法树
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举，定义标准SQL数据类型
import org.apache.calcite.tools.FrameworkConfig; // 导入框架配置接口，用于配置Calcite框架
import org.apache.calcite.tools.Frameworks; // 导入框架工具类，提供创建配置和规划器的方法
import org.apache.calcite.tools.Planner; // 导入规划器接口，负责SQL到关系代数的转换和优化
import org.apache.calcite.tools.Programs; // 导入程序工具类，提供预定义的优化规则集

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.openjdk.jmh.annotations.Benchmark; // 导入JMH基准测试注解，标记需要测试的方法
import org.openjdk.jmh.annotations.BenchmarkMode; // 导入基准测试模式注解，指定测试模式（如平均时间、吞吐量）
import org.openjdk.jmh.annotations.Fork; // 导入Fork注解，配置测试进程的fork次数和JVM参数
import org.openjdk.jmh.annotations.Level; // 导入级别枚举，指定Setup方法的调用时机
import org.openjdk.jmh.annotations.Measurement; // 导入测量注解，配置正式测试的迭代次数和时间
import org.openjdk.jmh.annotations.Mode; // 导入模式枚举，定义基准测试的测量模式
import org.openjdk.jmh.annotations.OutputTimeUnit; // 导入输出时间单位注解，指定结果的时间单位
import org.openjdk.jmh.annotations.Param; // 导入参数注解，用于指定基准测试的参数化值
import org.openjdk.jmh.annotations.Scope; // 导入作用域枚举，定义状态对象的生命周期
import org.openjdk.jmh.annotations.Setup; // 导入Setup注解，标记在基准测试前执行的初始化方法
import org.openjdk.jmh.annotations.State; // 导入状态注解，标记需要共享状态的类
import org.openjdk.jmh.annotations.Threads; // 导入线程数注解，指定并发执行的线程数
import org.openjdk.jmh.annotations.Warmup; // 导入预热注解，配置预热阶段的迭代次数和时间
import org.openjdk.jmh.profile.GCProfiler; // 导入GC性能分析器，用于监控垃圾回收行为
import org.openjdk.jmh.runner.Runner; // 导入运行器类，用于执行基准测试
import org.openjdk.jmh.runner.RunnerException; // 导入运行器异常类，表示基准测试运行时的错误
import org.openjdk.jmh.runner.options.Options; // 导入选项接口，配置基准测试的运行参数
import org.openjdk.jmh.runner.options.OptionsBuilder; // 导入选项构建器类，用于构建Options对象

import java.util.List; // 导入List接口，Java集合框架的基础接口
import java.util.Locale; // 导入Locale类，用于本地化相关的操作
import java.util.Random; // 导入随机数生成器类
import java.util.concurrent.TimeUnit; // 导入时间单位枚举，用于指定时间测量单位

/**
 * Benchmarks Conversion of Sql To RelNode and conversion of SqlNode to RelNode. // 本类用于基准测试SQL到RelNode的转换以及SqlNode到RelNode的转换性能
 * 这个基准测试类主要用于测量和比较以下两个转换过程的性能：
 * 1. SQL字符串 -> SqlNode -> RelNode（完整的解析+验证+转换过程）
 * 2. SqlNode -> RelNode（仅转换过程，跳过解析和验证）
 * 通过这两个测试，可以了解解析、验证和转换各阶段的性能开销，帮助优化Calcite的查询处理流程
 */
@Fork(value = 1, jvmArgsPrepend = "-Xmx2048m") // Fork配置：fork 1个进程，JVM参数预置最大堆内存为2048MB，确保测试有足够内存
@Measurement(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS) // 测量配置：执行10次迭代，每次100毫秒，用于收集正式的测试数据
@Warmup(iterations = 10, time = 100, timeUnit = TimeUnit.MILLISECONDS) // 预热配置：执行10次迭代，每次100毫秒，用于JIT编译预热，避免冷启动影响测试结果
@BenchmarkMode(Mode.AverageTime) // 基准测试模式：使用平均时间模式，计算每次操作的平均耗时
@OutputTimeUnit(TimeUnit.MILLISECONDS) // 输出时间单位：结果以毫秒为单位显示，便于阅读和比较
@State(Scope.Benchmark) // 状态作用域：Benchmark级别，整个测试过程共享同一个状态实例
@Threads(1) // 线程配置：使用1个线程执行测试，避免多线程干扰，专注于单线程性能
public class RelNodeConversionBenchmark { // RelNode转换基准测试类，用于测试SQL到关系代数节点的转换性能

  /**
   * A common state needed for this benchmark. // 基准测试所需的通用状态基类，包含共享的初始化逻辑
   * 这个抽象状态类封装了两个基准测试所需的公共设置：
   * - 生成复杂的SQL查询语句（包含大量CASE WHEN表达式）
   * 创建测试用的Schema和Table（模拟数据表结构）
   * - 初始化Calcite规划器（Planner）
   * 子类可以继承这个基类，专注于各自特定的测试逻辑
   */
  public abstract static class RelNodeConversionBenchmarkState { // 抽象静态内部类，定义基准测试的通用状态
    String sql; // SQL查询字符串，存储生成的测试SQL语句
    Planner p; // Calcite规划器实例，用于解析、验证和转换SQL

    public void setup(int length, int columnLength) { // 设置方法，初始化基准测试所需的状态，length表示生成的表达式数量，columnLength表示表的列数
      // Create Sql // 创建SQL查询语句，生成包含大量复杂表达式的SELECT语句
      StringBuilder sb = new StringBuilder(); // 创建字符串构建器，用于高效拼接SQL语句
      sb.append("select 1 "); // 添加SELECT子句，选择常量1作为第一列
      Random rnd = new Random(); // 创建随机数生成器，用于生成随机的列引用和常量值
      rnd.setSeed(424242); // 设置随机数种子为固定值424242，确保每次测试生成的SQL语句一致，保证测试的可重复性
      for (int i = 0; i < length; i++) { // 循环length次，生成length个表达式列
        sb.append(", "); // 添加逗号分隔符，分隔多个选择列
        sb.append( // 添加一个复杂的CASE WHEN表达式到SQL中，用于测试复杂表达式的处理性能
            String.format(Locale.ROOT, "c%s / CASE WHEN c%s > %d THEN c%s ELSE c%s END ", // 格式化字符串生成表达式：列cX除以CASE WHEN表达式，CASE WHEN表达式比较列cY是否大于随机值，如果是则返回列cZ，否则返回列cW
                String.valueOf(rnd.nextInt(columnLength)), String.valueOf(i % columnLength), // 第一个参数：随机列索引（0到columnLength-1）；第二个参数：基于当前迭代次数的列索引（使用模运算确保在有效范围内）
                rnd.nextInt(columnLength), String.valueOf(rnd.nextInt(columnLength)), // 第三个参数：比较的随机值；第四个参数：THEN分支的随机列索引
                String.valueOf(rnd.nextInt(columnLength)))); // 第五个参数：ELSE分支的随机列索引
      }
      sb.append(" FROM test1"); // 添加FROM子句，指定查询来源表为test1
      sql = sb.toString(); // 将构建的SQL字符串赋值给成员变量sql，供后续解析使用

      // Create Schema and Table // 创建Schema和Table，为解析器提供元数据信息

      AbstractTable t = new AbstractQueryableTable(Integer.class) { // 创建抽象可查询表实例，泛型参数Integer表示表中元素类型为Integer
        final List<Integer> items = ImmutableList.of(); // 创建空的不可变列表，模拟表中的数据（实际为空，仅用于元数据）
        final Enumerable<Integer> enumerable = Linq4j.asEnumerable(items); // 将列表转换为可枚举对象，支持LINQ风格的查询操作

        @Override public <E> Queryable<E> asQueryable( // 重写asQueryable方法，将表转换为可查询对象，支持延迟执行查询
            QueryProvider queryProvider, SchemaPlus schema, String tableName) { // 参数：查询提供者、所属Schema、表名
          return (Queryable<E>) enumerable.asQueryable(); // 将可枚举对象转换为可查询对象并返回，强制类型转换为泛型E
        }

        @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，定义表的行类型（即列的结构和类型）
          RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建类型工厂构建器，用于构建行类型
          for (int i = 0; i < columnLength; i++) { // 循环columnLength次，创建columnLength个列
            builder.add(String.format(Locale.ROOT, "c%d", i), SqlTypeName.INTEGER); // 添加列到构建器，列名为c0、c1、c2...，类型为INTEGER，使用ROOT区域设置确保格式一致
          }
          return builder.build(); // 构建并返回完整的行类型对象
        }
      };

      // Create Planner // 创建规划器，用于SQL的解析、验证和转换

      final SchemaPlus schema = Frameworks.createRootSchema(true); // 创建根Schema对象，参数true表示启用默认Schema，作为所有表的容器
      schema.add("test1", t); // 将创建的表t添加到Schema中，表名为"test1"，与SQL中的FROM子句对应

      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器，用于配置Calcite的运行环境
          .parserConfig(SqlParser.config().withLex(Lex.MYSQL)) // 配置解析器：使用MySQL词法规则（如反引号标识符、特定关键字处理）
          .defaultSchema(schema) // 设置默认Schema为上面创建的schema，解析时自动在此Schema中查找表
          .programs(Programs.ofRules(Programs.RULE_SET)) // 设置优化程序：使用Calcite的标准规则集，包含各种优化规则（如谓词下推、投影消除等）
          .build(); // 构建配置对象
      p = Frameworks.getPlanner(config); // 根据配置获取规划器实例，该规划器将使用配置的词法规则、Schema和优化规则
    }
  }

  /**
   * A state holding information needed to parse. // 保存解析所需信息的状态类，用于测试完整的SQL解析流程
   * 这个状态类专门用于测试从SQL字符串到RelNode的完整转换过程，包括：
   * - SQL解析（parse）：将SQL字符串解析为SqlNode语法树
   * - SQL验证（validate）：验证SqlNode的语义正确性
   * - 关系转换（rel）：将SqlNode转换为RelNode关系代数树
   * 通过这个测试可以评估整个转换链路的性能
   */
  @State(Scope.Thread) // 状态作用域：Thread级别，每个测试线程有独立的状态实例，避免线程间干扰
  public static class SqlToRelNodeBenchmarkState extends RelNodeConversionBenchmarkState { // SQL到RelNode基准测试状态类，继承通用状态基类
    @Param({"10000"}) // 参数注解：指定length参数值为10000，表示生成10000个表达式列，用于测试大规模查询的性能
    int length; // 表达式数量参数，控制生成SQL的复杂度

    @Param({"10", "100", "1000"}) // 参数注解：指定columnLength参数值为10、100、1000三个值，JMH会分别测试这三个列数配置，评估不同列数对性能的影响
    int columnLength; // 列数参数，控制表的列数，影响元数据大小和表达式复杂度

    @Setup(Level.Iteration) // Setup注解：在每次迭代前调用setup方法，Level.Iteration表示每个测试迭代开始前都会重新初始化状态
    public void setUp() { // 设置方法，初始化测试状态
      super.setup(length, columnLength); // 调用父类的setup方法，执行通用的初始化逻辑（生成SQL、创建Schema、初始化规划器）
    }

    public RelNode parse() throws Exception { // 解析方法，执行完整的SQL到RelNode转换流程，可能抛出异常
      SqlNode n = p.parse(sql); // 调用规划器的parse方法，将SQL字符串解析为SqlNode语法树，这是转换的第一步
      n = p.validate(n); // 调用规划器的validate方法，验证SqlNode的语义正确性（如表是否存在、类型是否匹配），这是转换的第二步
      RelNode rel = p.rel(n).project(); // 调用规划器的rel方法，将验证后的SqlNode转换为RelNode关系代数树，project()方法返回投影后的RelNode，这是转换的第三步
      p.close(); // 关闭规划器，释放资源，每次测试后都重新创建规划器以确保测试的独立性
      p.reset(); // 重置规划器状态，准备下一次使用
      return rel; // 返回转换后的RelNode对象
    }
  }

  @Benchmark // 基准测试注解：标记此方法为需要性能测试的基准方法
  public RelNode parse(SqlToRelNodeBenchmarkState state) throws Exception { // 基准测试方法，测试SQL到RelNode的完整转换性能，参数为状态对象
    return state.parse(); // 调用状态对象的parse方法，执行转换并返回RelNode，JMH会多次调用此方法并测量平均耗时
  }

  /**
   * A state holding information needed to convert To Rel. // 保存转换到RelNode所需信息的状态类，用于测试SqlNode到RelNode的转换性能
   * 这个状态类专门用于测试从已解析的SqlNode到RelNode的转换过程，不包括SQL解析和验证阶段：
   * - 在Setup阶段预先完成SQL解析和验证，得到SqlNode
   * - 基准测试只测量SqlNode到RelNode的转换性能
   * 通过与SqlToRelNodeBenchmarkState对比，可以分离出解析和验证阶段的性能开销
   */
  @State(Scope.Thread) // 状态作用域：Thread级别，每个测试线程有独立的状态实例
  public static class SqlNodeToRelNodeBenchmarkState extends RelNodeConversionBenchmarkState { // SqlNode到RelNode基准测试状态类
    @Param({"10000"}) // 参数注解：指定length参数值为10000，与SqlToRelNodeBenchmarkState保持一致，确保测试的可比性
    int length; // 表达式数量参数

    @Param({"10", "100", "1000"}) // 参数注解：指定columnLength参数值为10、100、1000，与SqlToRelNodeBenchmarkState保持一致
    int columnLength; // 列数参数
    SqlNode sqlNode; // SqlNode成员变量，存储预先解析和验证的SQL语法树节点

    @Setup(Level.Iteration) // Setup注解：在每次迭代前调用setup方法
    public void setUp() { // 设置方法，初始化测试状态
      super.setup(length, columnLength); // 调用父类的setup方法，执行通用初始化
      try { // 捕获可能的异常
        sqlNode = p.validate(p.parse(sql)); // 预先完成SQL解析和验证，将结果存储在sqlNode成员变量中，这样基准测试就只测量转换性能
      } catch (Exception e) { // 捕获异常
        e.printStackTrace(); // 打印异常堆栈，便于调试
      }
    }

    public RelNode convertToRel() throws Exception { // 转换方法，仅执行SqlNode到RelNode的转换
      return p.rel(sqlNode).project(); // 调用规划器的rel方法，将预先验证的SqlNode转换为RelNode，project()返回投影后的RelNode
    }
  }

  @Benchmark // 基准测试注解：标记此方法为需要性能测试的基准方法
  public RelNode convertToRel(SqlNodeToRelNodeBenchmarkState state) throws Exception { // 基准测试方法，测试SqlNode到RelNode的转换性能
    return state.convertToRel(); // 调用状态对象的convertToRel方法，执行转换并返回RelNode
  }

  public static void main(String[] args) throws RunnerException { // 主方法，用于手动运行基准测试，参数为命令行参数
    Options opt = new OptionsBuilder() // 创建选项构建器，用于配置基准测试的运行选项
        .include(RelNodeConversionBenchmark.class.getSimpleName()) // 包含当前基准测试类，只运行这个类的测试
        .addProfiler(GCProfiler.class) // 添加GC性能分析器，监控垃圾回收行为，帮助分析内存使用情况
        .addProfiler(FlightRecorderProfiler.class) // 添加Java Flight Recorder性能分析器，提供更详细的JVM性能数据
        .detectJvmArgs() // 自动检测JVM参数，使用当前JVM的配置
        .build(); // 构建选项对象

    new Runner(opt).run(); // 创建运行器并运行基准测试，执行测试并输出结果
  }

}