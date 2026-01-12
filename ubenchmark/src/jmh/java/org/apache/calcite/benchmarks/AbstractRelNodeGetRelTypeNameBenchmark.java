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
package org.apache.calcite.benchmarks; // 定义包名，该类位于org.apache.calcite.benchmarks包下，用于性能基准测试

import org.apache.calcite.rel.AbstractRelNode; // 导入AbstractRelNode类，这是Calcite中所有关系表达式节点的抽象基类

import org.openjdk.jmh.annotations.Benchmark; // 导入Benchmark注解，用于标记基准测试方法
import org.openjdk.jmh.annotations.BenchmarkMode; // 导入BenchmarkMode注解，用于指定基准测试的模式（如平均时间、吞吐量等）
import org.openjdk.jmh.annotations.Fork; // 导入Fork注解，用于指定基准测试的fork进程数和JVM参数
import org.openjdk.jmh.annotations.Level; // 导入Level注解，用于指定Setup方法的调用级别（如Iteration、Trial等）
import org.openjdk.jmh.annotations.Measurement; // 导入Measurement注解，用于指定基准测试的测量迭代配置
import org.openjdk.jmh.annotations.Mode; // 导入Mode枚举，定义基准测试的模式类型（如AverageTime、Throughput等）
import org.openjdk.jmh.annotations.OutputTimeUnit; // 导入OutputTimeUnit注解，用于指定基准测试结果的时间单位
import org.openjdk.jmh.annotations.Param; // 导入Param注解，用于参数化基准测试，可以指定多个测试参数值
import org.openjdk.jmh.annotations.Scope; // 导入Scope枚举，定义状态对象的作用域（如Thread、Benchmark、Group等）
import org.openjdk.jmh.annotations.Setup; // 导入Setup注解，用于标记基准测试前的初始化方法
import org.openjdk.jmh.annotations.State; // 导入State注解，用于标记持有测试状态的对象
import org.openjdk.jmh.annotations.Threads; // 导入Threads注解，用于指定基准测试的线程数
import org.openjdk.jmh.annotations.Warmup; // 导入Warmup注解，用于指定基准测试的预热迭代配置
import org.openjdk.jmh.runner.Runner; // 导入Runner类，用于运行基准测试
import org.openjdk.jmh.runner.RunnerException; // 导入RunnerException类，用于处理基准测试运行时的异常
import org.openjdk.jmh.runner.options.Options; // 导入Options接口，用于配置基准测试的运行选项
import org.openjdk.jmh.runner.options.OptionsBuilder; // 导入OptionsBuilder类，用于构建基准测试的配置选项

import java.util.Random; // 导入Random类，用于生成随机数以选择不同的类名进行测试
import java.util.concurrent.TimeUnit; // 导入TimeUnit枚举，用于指定时间单位（如纳秒、毫秒、秒等）

/**
 * A benchmark of alternative implementations for {@link AbstractRelNode#getRelTypeName()}
 * method.
 */
// 这是一个基准测试类，用于测试AbstractRelNode.getRelTypeName()方法的不同实现方案的性能
// getRelTypeName()方法的作用是从完整的类名中提取简单的类名（去掉包名和内部类符号）
// 本类通过对比三种不同的实现方式，找出性能最优的方案
@Fork(value = 1, jvmArgsPrepend = "-Xmx1024m") // Fork注解：指定基准测试在1个独立的JVM进程中运行，并设置最大堆内存为1024MB，避免垃圾回收影响测试结果
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS) // Measurement注解：指定正式测量阶段运行10次迭代，每次迭代持续1秒，收集10次测量数据以获得更准确的平均结果
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS) // Warmup注解：指定预热阶段运行10次迭代，每次迭代持续1秒，让JVM进行JIT编译优化，避免冷启动影响测试结果
@Threads(1) // Threads注解：指定基准测试使用1个线程运行，避免多线程竞争带来的性能波动
@OutputTimeUnit(TimeUnit.NANOSECONDS) // OutputTimeUnit注解：指定基准测试结果以纳秒为单位输出，便于精确衡量性能差异
@BenchmarkMode(Mode.AverageTime) // BenchmarkMode注解：指定基准测试模式为平均时间模式，即测量每次操作的平均耗时
public class AbstractRelNodeGetRelTypeNameBenchmark { // 定义基准测试类，类名表示这是AbstractRelNode.getRelTypeName()方法的基准测试

  /**
   * A state holding the full class names of all built-in implementors of the
   * {@link org.apache.calcite.rel.RelNode} interface.
   */
  // 这是一个状态类，用于持有Calcite中所有实现了RelNode接口的内置类的完整类名
  // 这些类名将用于基准测试，测试从完整类名中提取简单类名的不同实现方式的性能
  @State(Scope.Thread) // State注解：指定该状态对象的作用域为Thread级别，即每个测试线程都有自己独立的实例，避免线程间干扰
  public static class ClassNameState { // 定义静态内部类ClassNameState，用于存储和管理基准测试所需的类名数据

    private final String[] fullNames = new String[]{ // 定义字符串数组，存储所有实现了RelNode接口的类的完整类名
        "org.apache.calcite.interpreter.InterpretableRel", // 解释器可解释的关系节点，用于解释执行模式
        "org.apache.calcite.interpreter.BindableRel", // 可绑定的关系节点，用于绑定数据源执行
        "org.apache.calcite.adapter.enumerable.EnumerableInterpretable", // 可枚举的解释器节点，结合了枚举和解释执行
        "org.apache.calcite.adapter.enumerable.EnumerableRel", // 可枚举的关系节点基类，用于Java集合流式处理
        "org.apache.calcite.adapter.enumerable.EnumerableLimit", // 可枚举的Limit节点，实现结果集限制
        "org.apache.calcite.adapter.enumerable.EnumerableUnion", // 可枚举的Union节点，实现集合并集操作
        "org.apache.calcite.adapter.enumerable.EnumerableCollect", // 可枚举的Collect节点，实现集合收集操作
        "org.apache.calcite.adapter.enumerable.EnumerableTableFunctionScan", // 可枚举的表函数扫描节点，扫描表函数返回的结果
        "org.apache.calcite.adapter.enumerable.EnumerableValues", // 可枚举的Values节点，处理常量值
        "org.apache.calcite.adapter.enumerable.EnumerableSemiJoin", // 可枚举的半连接节点，实现半连接操作
        "org.apache.calcite.adapter.enumerable.EnumerableMinus", // 可枚举的差集节点，实现集合差集操作
        "org.apache.calcite.adapter.enumerable.EnumerableIntersect", // 可枚举的交集节点，实现集合交集操作
        "org.apache.calcite.adapter.enumerable.EnumerableUncollect", // 可枚举的反收集节点，将数组展开为多行
        "org.apache.calcite.adapter.enumerable.EnumerableMergeJoin", // 可枚举的合并连接节点，实现归并连接算法
        "org.apache.calcite.adapter.enumerable.EnumerableProject", // 可枚举的投影节点，实现字段投影和表达式计算
        "org.apache.calcite.adapter.enumerable.EnumerableFilter", // 可枚举的过滤节点，实现条件过滤
        "org.apache.calcite.adapter.jdbc.JdbcToEnumerableConverter", // JDBC到可枚举的转换器，将JDBC节点转换为可枚举节点
        "org.apache.calcite.adapter.enumerable.EnumerableNestedLoopJoin", // 可枚举的嵌套循环连接节点，实现嵌套循环连接算法
        "org.apache.calcite.adapter.enumerable.EnumerableTableScan", // 可枚举的表扫描节点，扫描表数据
        "org.apache.calcite.adapter.enumerable.EnumerableHashJoin", // 可枚举的哈希连接节点，实现哈希连接算法
        "org.apache.calcite.adapter.enumerable.EnumerableTableModify", // 可枚举的表修改节点，处理INSERT/UPDATE/DELETE操作
        "org.apache.calcite.adapter.enumerable.EnumerableAggregate", // 可枚举的聚合节点，实现GROUP BY和聚合函数
        "org.apache.calcite.adapter.enumerable.EnumerableCorrelate", // 可枚举的关联节点，实现相关子查询
        "org.apache.calcite.adapter.enumerable.EnumerableSort", // 可枚举的排序节点，实现ORDER BY排序
        "org.apache.calcite.adapter.enumerable.EnumerableWindow", // 可枚举的窗口函数节点，实现窗口函数计算
        "org.apache.calcite.plan.volcano.VolcanoPlannerTraitTest$FooRel", // Volcano规划器测试用的测试类
        "org.apache.calcite.adapter.enumerable.EnumerableCalc", // 可枚举的Calc节点，实现复杂的表达式计算
        "org.apache.calcite.adapter.enumerable.EnumerableInterpreter", // 可枚举的解释器节点
        "org.apache.calcite.adapter.geode.rel.GeodeToEnumerableConverter", // Geode到可枚举的转换器
        "org.apache.calcite.adapter.pig.PigToEnumerableConverter", // Pig到可枚举的转换器
        "org.apache.calcite.adapter.mongodb.MongoToEnumerableConverter", // MongoDB到可枚举的转换器
        "org.apache.calcite.adapter.csv.CsvTableScan", // CSV表扫描节点，扫描CSV文件
        "org.apache.calcite.adapter.spark.SparkToEnumerableConverter", // Spark到可枚举的转换器
        "org.apache.calcite.adapter.elasticsearch.ElasticsearchToEnumerableConverter", // Elasticsearch到可枚举的转换器
        "org.apache.calcite.adapter.file.FileTableScan", // 文件表扫描节点，扫描文件系统中的数据
        "org.apache.calcite.adapter.cassandra.CassandraToEnumerableConverter", // Cassandra到可枚举的转换器
        "org.apache.calcite.adapter.splunk.SplunkTableScan", // Splunk表扫描节点，扫描Splunk数据
        "org.apache.calcite.adapter.jdbc.JdbcRel", // JDBC关系节点基类
        "org.apache.calcite.adapter.jdbc.JdbcTableScan", // JDBC表扫描节点，通过JDBC扫描数据库表
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcJoin", // JDBC连接规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcCalc", // JDBC计算规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcProject", // JDBC投影规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcFilter", // JDBC过滤规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcAggregate", // JDBC聚合规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcSort", // JDBC排序规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcUnion", // JDBC并集规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcIntersect", // JDBC交集规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcMinus", // JDBC差集规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcTableModify", // JDBC表修改规则和节点
        "org.apache.calcite.adapter.jdbc.JdbcRules$JdbcValues", // JDBC常量值规则和节点
        "org.apache.calcite.tools.PlannerTest$MockJdbcTableScan", // 规划器测试用的模拟JDBC表扫描节点
        "org.apache.calcite.rel.AbstractRelNode", // 抽象关系节点基类，所有关系节点的父类
        "org.apache.calcite.rel.rules.MultiJoin", // 多连接节点，用于优化多个连接操作
        "org.apache.calcite.rel.core.TableFunctionScan", // 表函数扫描节点，扫描表函数返回的结果集
        "org.apache.calcite.rel.BiRel", // 双输入关系节点基类，表示有两个子节点的关系节点
        "org.apache.calcite.rel.SingleRel", // 单输入关系节点基类，表示只有一个子节点的关系节点
        "org.apache.calcite.rel.core.Values", // 常量值节点，处理VALUES子句
        "org.apache.calcite.rel.core.TableScan", // 表扫描节点基类，用于扫描表数据
        "org.apache.calcite.plan.hep.HepRelVertex", // HepPlanner的顶点节点，用于HepPlanner规划器
        "org.apache.calcite.plan.RelOptPlanReaderTest$MyRel", // 关系优化计划读取器测试用的测试类
        "org.apache.calcite.plan.volcano.TraitPropagationTest$PhysTable", // Volcano规划器特性传播测试用的物理表节点
        "org.apache.calcite.plan.volcano.PlannerTests$TestLeafRel", // 规划器测试用的叶子节点
        "org.apache.calcite.plan.volcano.RelSubset", // 关系子集，Volcano规划器中用于存储等价关系节点的集合
        "org.apache.calcite.rel.core.SetOp", // 集合操作节点基类，表示UNION、INTERSECT、MINUS等集合操作
        "org.apache.calcite.plan.volcano.VolcanoPlannerTraitTest$TestLeafRel", // Volcano规划器特性测试用的叶子节点
        "org.apache.calcite.adapter.druid.DruidQuery", // Druid查询节点，用于Apache Druid数据源
        "org.apache.calcite.sql2rel.RelStructuredTypeFlattener$SelfFlatteningRel", // 结构化类型扁平化过程中的自扁平化节点
        "org.apache.calcite.rel.convert.Converter", // 转换器节点基类，用于实现特性转换
        "org.apache.calcite.rel.convert.ConverterImpl", // 转换器节点实现类
        "org.apache.calcite.plan.volcano.TraitPropagationTest$Phys", // Volcano规划器特性传播测试用的物理节点
        "org.apache.calcite.plan.volcano.TraitPropagationTest$PhysTable", // Volcano规划器特性传播测试用的物理表节点
        "org.apache.calcite.plan.volcano.TraitPropagationTest$PhysSort", // Volcano规划器特性传播测试用的物理排序节点
        "org.apache.calcite.plan.volcano.TraitPropagationTest$PhysAgg", // Volcano规划器特性传播测试用的物理聚合节点
        "org.apache.calcite.plan.volcano.TraitPropagationTest$PhysProj", // Volcano规划器特性传播测试用的物理投影节点
        "org.apache.calcite.interpreter.BindableRel", // 可绑定关系节点，用于解释执行模式
        "org.apache.calcite.adapter.enumerable.EnumerableBindable", // 可枚举的可绑定节点
        "org.apache.calcite.interpreter.Bindables$BindableTableScan", // 可绑定的表扫描节点实现
        "org.apache.calcite.interpreter.Bindables$BindableFilter", // 可绑定的过滤节点实现
        "org.apache.calcite.interpreter.Bindables$BindableProject", // 可绑定的投影节点实现
        "org.apache.calcite.interpreter.Bindables$BindableSort", // 可绑定的排序节点实现
        "org.apache.calcite.interpreter.Bindables$BindableJoin", // 可绑定的连接节点实现
        "org.apache.calcite.interpreter.Bindables$BindableUnion", // 可绑定的并集节点实现
        "org.apache.calcite.interpreter.Bindables$BindableValues", // 可绑定的常量值节点实现
        "org.apache.calcite.interpreter.Bindables$BindableAggregate", // 可绑定的聚合节点实现
        "org.apache.calcite.interpreter.Bindables$BindableWindow", // 可绑定的窗口函数节点实现
        "org.apache.calcite.adapter.druid.DruidQuery", // Druid查询节点，用于Apache Druid数据源
        "org.apache.calcite.adapter.cassandra.CassandraRel", // Cassandra关系节点基类
        "org.apache.calcite.adapter.cassandra.CassandraFilter", // Cassandra过滤节点
        "org.apache.calcite.adapter.cassandra.CassandraProject", // Cassandra投影节点
        "org.apache.calcite.adapter.cassandra.CassandraLimit", // Cassandra限制节点
        "org.apache.calcite.adapter.cassandra.CassandraSort", // Cassandra排序节点
        "org.apache.calcite.adapter.cassandra.CassandraTableScan", // Cassandra表扫描节点
        "org.apache.calcite.adapter.mongodb.MongoRel", // MongoDB关系节点基类
        "org.apache.calcite.adapter.mongodb.MongoTableScan", // MongoDB表扫描节点
        "org.apache.calcite.adapter.mongodb.MongoProject", // MongoDB投影节点
        "org.apache.calcite.adapter.mongodb.MongoFilter", // MongoDB过滤节点
        "org.apache.calcite.adapter.mongodb.MongoAggregate", // MongoDB聚合节点
        "org.apache.calcite.adapter.mongodb.MongoSort", // MongoDB排序节点
        "org.apache.calcite.adapter.spark.SparkRel", // Spark关系节点基类
        "org.apache.calcite.adapter.spark.JdbcToSparkConverter", // JDBC到Spark的转换器
        "org.apache.calcite.adapter.spark.SparkRules$SparkValues", // Spark常量值规则和节点
        "org.apache.calcite.adapter.spark.EnumerableToSparkConverter", // 可枚举到Spark的转换器
        "org.apache.calcite.adapter.spark.SparkRules$SparkCalc", // Spark计算规则和节点
        "org.apache.calcite.adapter.elasticsearch.ElasticsearchRel", // Elasticsearch关系节点基类
        "org.apache.calcite.adapter.elasticsearch.ElasticsearchFilter", // Elasticsearch过滤节点
        "org.apache.calcite.adapter.elasticsearch.ElasticsearchProject", // Elasticsearch投影节点
        "org.apache.calcite.adapter.elasticsearch.ElasticsearchAggregate", // Elasticsearch聚合节点
        "org.apache.calcite.adapter.elasticsearch.ElasticsearchTableScan", // Elasticsearch表扫描节点
        "org.apache.calcite.adapter.elasticsearch.ElasticsearchSort", // Elasticsearch排序节点
        "org.apache.calcite.adapter.geode.rel.GeodeRel", // Geode关系节点基类
        "org.apache.calcite.adapter.geode.rel.GeodeSort", // Geode排序节点
        "org.apache.calcite.adapter.geode.rel.GeodeTableScan", // Geode表扫描节点
        "org.apache.calcite.adapter.geode.rel.GeodeProject", // Geode投影节点
        "org.apache.calcite.adapter.geode.rel.GeodeFilter", // Geode过滤节点
        "org.apache.calcite.adapter.geode.rel.GeodeAggregate", // Geode聚合节点
        "org.apache.calcite.adapter.pig.PigRel", // Pig关系节点基类
        "org.apache.calcite.adapter.pig.PigTableScan", // Pig表扫描节点
        "org.apache.calcite.adapter.pig.PigAggregate", // Pig聚合节点
        "org.apache.calcite.adapter.pig.PigJoin", // Pig连接节点
        "org.apache.calcite.adapter.pig.PigFilter", // Pig过滤节点
        "org.apache.calcite.adapter.pig.PigProject" // Pig投影节点
    };

    @Param({"11", "31", "63"}) // Param注解：指定参数化测试，分别使用11、31、63三个不同的种子值进行测试，验证不同随机种子对性能的影响
    private long seed; // 随机数生成器的种子值，用于确保每次测试的随机数序列可重复，便于对比测试结果

    private Random r = null; // 随机数生成器对象，用于从fullNames数组中随机选择类名进行测试，初始化为null，在setupRandom方法中创建

    /**
     * Sets up the random number generator at the beginning of each iteration.
     *
     * <p>To have relatively comparable results the generator should always use
     * the same seed for the whole duration of the benchmark.
     */
    // 在每次迭代开始时设置随机数生成器，确保使用相同的种子值，以便获得可比较的测试结果
    @Setup(Level.Iteration) // Setup注解：指定该方法在每个迭代开始前执行，Level.Iteration表示每次迭代都会调用一次
    public void setupRandom() { // 定义setupRandom方法，用于初始化随机数生成器
      r = new Random(seed); // 使用指定的种子值创建Random对象，确保每次测试的随机数序列相同，便于结果对比
    }

    /**
     * Returns a pseudo-random class name that corresponds to an implementor of the RelNode
     * interface.
     */
    // 返回一个伪随机的类名，该类名对应于RelNode接口的一个实现类
    public String nextName() { // 定义nextName方法，用于从fullNames数组中随机选择一个类名
      return fullNames[r.nextInt(fullNames.length)]; // 使用随机数生成器生成一个0到fullNames.length-1之间的随机索引，返回对应索引的类名
    }
  }

  @Benchmark // Benchmark注解：标记这是一个基准测试方法，JMH会自动调用该方法进行性能测试
  public String useStringLastIndexOfTwoTimesV1(ClassNameState state) { // 定义基准测试方法，测试第一种实现方式的性能，该方法使用String.lastIndexOf()两次，第一次查找'$'，第二次查找'.'
    String cn = state.nextName(); // 从状态对象中获取一个随机的完整类名，例如"org.apache.calcite.adapter.jdbc.JdbcRules$JdbcJoin"
    int i = cn.lastIndexOf("$"); // 使用String.lastIndexOf()方法查找字符串中最后一次出现'$'的位置，'$'是Java内部类的分隔符，例如JdbcRules$JdbcJoin中的'$'
    if (i >= 0) { // 如果找到了'$'符号（即这是一个内部类）
      return cn.substring(i + 1); // 返回'$'符号之后的子字符串，即内部类的简单类名，例如从"org.apache.calcite.adapter.jdbc.JdbcRules$JdbcJoin"返回"JdbcJoin"
    }
    i = cn.lastIndexOf("."); // 如果没有找到'$'符号，则查找最后一次出现'.'的位置，'.'是Java包名的分隔符，例如org.apache.calcite.rel.AbstractRelNode中的'.'
    if (i >= 0) { // 如果找到了'.'符号（即这是一个有包名的类）
      return cn.substring(i + 1); // 返回'.'符号之后的子字符串，即类的简单类名，例如从"org.apache.calcite.rel.AbstractRelNode"返回"AbstractRelNode"
    }
    return cn; // 如果既没有找到'$'也没有找到'.'，说明类名本身就没有包名和内部类分隔符，直接返回原始类名
  }

  @Benchmark // Benchmark注解：标记这是一个基准测试方法，JMH会自动调用该方法进行性能测试
  public String useStringLastIndexOfTwoTimeV2(ClassNameState state) { // 定义基准测试方法，测试第二种实现方式的性能，该方法与V1版本逻辑相同，但使用char类型的参数调用lastIndexOf()而不是String类型
    String cn = state.nextName(); // 从状态对象中获取一个随机的完整类名，例如"org.apache.calcite.adapter.jdbc.JdbcRules$JdbcJoin"
    int i = cn.lastIndexOf('$'); // 使用char类型的参数'$'调用lastIndexOf()方法，查找字符串中最后一次出现'$'的位置，使用char参数可能比String参数更高效，因为避免了创建临时String对象
    if (i >= 0) { // 如果找到了'$'符号（即这是一个内部类）
      return cn.substring(i + 1); // 返回'$'符号之后的子字符串，即内部类的简单类名，例如从"org.apache.calcite.adapter.jdbc.JdbcRules$JdbcJoin"返回"JdbcJoin"
    }
    i = cn.lastIndexOf('.'); // 如果没有找到'$'符号，则使用char类型的参数'.'调用lastIndexOf()方法，查找最后一次出现'.'的位置
    if (i >= 0) { // 如果找到了'.'符号（即这是一个有包名的类）
      return cn.substring(i + 1); // 返回'.'符号之后的子字符串，即类的简单类名，例如从"org.apache.calcite.rel.AbstractRelNode"返回"AbstractRelNode"
    }
    return cn; // 如果既没有找到'$'也没有找到'.'，说明类名本身就没有包名和内部类分隔符，直接返回原始类名
  }

  @Benchmark // Benchmark注解：标记这是一个基准测试方法，JMH会自动调用该方法进行性能测试
  public String useCustomLastIndexOf(ClassNameState state) { // 定义基准测试方法，测试第三种实现方式的性能，该方法使用自定义的循环查找，从字符串末尾向前遍历，同时查找'$'和'.'
    String cn = state.nextName(); // 从状态对象中获取一个随机的完整类名，例如"org.apache.calcite.adapter.jdbc.JdbcRules$JdbcJoin"
    int i = cn.length(); // 获取字符串的长度，初始化索引i为字符串长度，准备从字符串末尾开始向前遍历
    while (--i >= 0) { // 使用while循环从字符串末尾向前遍历，先执行--i（将索引减1），然后检查是否大于等于0，这样可以避免数组越界
      if (cn.charAt(i) == '$' || cn.charAt(i) == '.') { // 获取当前位置的字符，检查是否是'$'或'.'，使用||运算符同时检查两种分隔符
        return cn.substring(i + 1); // 如果找到了分隔符（'$'或'.'），返回该位置之后的子字符串，即简单类名，这种方法只需要一次遍历就能找到最后一个分隔符
      }
    }
    return cn; // 如果遍历完整个字符串都没有找到分隔符，说明类名本身就没有包名和内部类分隔符，直接返回原始类名
  }

  public static void main(String[] args) throws RunnerException { // 定义main方法，作为基准测试的入口点，可以手动运行基准测试
    Options opt = new OptionsBuilder() // 创建OptionsBuilder对象，用于构建基准测试的配置选项
        .include(AbstractRelNodeGetRelTypeNameBenchmark.class.getName()) // 指定要运行的基准测试类，只运行当前类中的所有@Benchmark方法
        .detectJvmArgs() // 自动检测当前JVM的参数，包括-Xmx、-Xms等内存设置，确保基准测试在相同的JVM配置下运行
        .build(); // 构建Options对象，完成配置

    new Runner(opt).run(); // 创建Runner对象并运行基准测试，Runner会根据配置执行所有@Benchmark方法，并输出性能测试结果
  }
} // 类定义结束
