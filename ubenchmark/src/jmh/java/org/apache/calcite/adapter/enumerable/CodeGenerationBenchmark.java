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
package org.apache.calcite.adapter.enumerable; // 包声明：该类属于Calcite的Enumerable适配器模块，用于可枚举的关系代数操作

import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // 导入Java类型工厂实现类，用于创建和管理Java类型系统
import org.apache.calcite.linq4j.tree.ClassDeclaration; // 导入类声明树节点，用于表示生成的Java类的抽象语法树
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建和操作LINQ4J表达式树
import org.apache.calcite.plan.ConventionTraitDef; // 导入约定特征定义，用于定义关系表达式的调用约定（如Enumerable、Logical等）
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，用于管理关系表达式的共享资源（如RexBuilder、类型工厂等）
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，用于描述关系表达式的物理属性（如排序、分布、调用约定等）
import org.apache.calcite.plan.volcano.VolcanoPlanner; // 导入火山优化器，Calcite的基于代价的优化器实现
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系代数操作的基类
import org.apache.calcite.rel.core.JoinRelType; // 导入连接关系类型枚举（INNER、LEFT、RIGHT、FULL等）
import org.apache.calcite.rel.core.RelFactories; // 导入关系节点工厂类，用于创建各种关系节点
import org.apache.calcite.rel.rules.CoreRules; // 导入核心优化规则集合，包含常用的转换规则
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rex.RexBuilder; // 导入行表达式构建器，用于创建行表达式（RexNode）
import org.apache.calcite.rex.RexNode; // 导入行表达式接口，表示关系代数中的表达式
import org.apache.calcite.runtime.ArrayBindable; // 导入数组可绑定接口，用于将关系表达式绑定到数组数据源
import org.apache.calcite.runtime.Bindable; // 导入可绑定接口，用于将关系表达式绑定到数据源
import org.apache.calcite.runtime.Typed; // 导入类型化接口，用于表示具有类型信息的对象
import org.apache.calcite.runtime.Utilities; // 导入工具类，提供各种静态工具方法
import org.apache.calcite.tools.RelBuilder; // 导入关系构建器，用于以编程方式构建关系表达式树

import com.google.common.cache.Cache; // 导入Google Guava缓存接口，用于实现缓存功能
import com.google.common.cache.CacheBuilder; // 导入缓存构建器，用于创建和配置缓存实例

import org.codehaus.commons.compiler.CompileException; // 导入编译异常类，表示Java代码编译过程中的错误
import org.codehaus.commons.compiler.CompilerFactoryFactory; // 导入编译器工厂工厂类，用于获取编译器工厂实例
import org.codehaus.commons.compiler.IClassBodyEvaluator; // 导入类体求值器接口，用于动态编译和执行Java类
import org.codehaus.commons.compiler.ICompilerFactory; // 导入编译器工厂接口，用于创建编译器实例
import org.openjdk.jmh.annotations.Benchmark; // 导入JMH基准测试注解，标记需要测试的方法
import org.openjdk.jmh.annotations.BenchmarkMode; // 导入基准测试模式注解，指定测试模式（吞吐量、平均时间等）
import org.openjdk.jmh.annotations.Fork; // 导入Fork注解，配置测试进程的fork设置
import org.openjdk.jmh.annotations.Level; // 导入级别注解，指定Setup方法的调用级别（Trial、Iteration、Invocation）
import org.openjdk.jmh.annotations.Measurement; // 导入测量注解，配置基准测试的测量参数（迭代次数、时间等）
import org.openjdk.jmh.annotations.Mode; // 导入模式枚举，定义基准测试的测量模式
import org.openjdk.jmh.annotations.OutputTimeUnit; // 导入输出时间单位注解，指定测试结果的时间单位
import org.openjdk.jmh.annotations.Param; // 导入参数注解，用于在基准测试中定义参数化测试
import org.openjdk.jmh.annotations.Scope; // 导入作用域注解，指定状态对象的生命周期（Thread、Group、Benchmark）
import org.openjdk.jmh.annotations.Setup; // 导入Setup注解，标记在测试前执行的初始化方法
import org.openjdk.jmh.annotations.State; // 导入状态注解，标记用于存储测试状态数据的类
import org.openjdk.jmh.annotations.Threads; // 导入线程注解，指定测试使用的线程数
import org.openjdk.jmh.annotations.Warmup; // 导入预热注解，配置基准测试的预热参数
import org.openjdk.jmh.profile.GCProfiler; // 导入GC性能分析器，用于监控垃圾回收行为
import org.openjdk.jmh.runner.Runner; // 导入基准测试运行器，用于执行基准测试
import org.openjdk.jmh.runner.RunnerException; // 导入运行器异常类，表示基准测试运行过程中的错误
import org.openjdk.jmh.runner.options.Options; // 导入基准测试选项接口，用于配置测试参数
import org.openjdk.jmh.runner.options.OptionsBuilder; // 导入选项构建器，用于构建基准测试配置

import java.io.IOException; // 导入IO异常类，表示输入输出操作中的错误
import java.io.StringReader; // 导入字符串读取器，用于从字符串创建字符流
import java.util.ArrayList; // 导入动态数组列表类，用于存储可变长度的对象集合
import java.util.HashMap; // 导入哈希映射类，用于存储键值对
import java.util.List; // 导入列表接口，定义有序集合的通用方法
import java.util.concurrent.TimeUnit; // 导入时间单位枚举，用于表示时间单位（秒、毫秒、微秒等）

/**
 * A benchmark of the main methods that are dynamically generating and compiling
 * Java code at runtime.
 * 本类是一个基准测试类，用于测试Calcite框架中在运行时动态生成和编译Java代码的主要方法的性能
 * 该基准测试主要关注EnumerableRelImplementor和相关的代码生成机制，这些机制是Calcite将关系代数表达式
 * 转换为可执行Java代码的核心组件
 *
 * <p>The benchmark examines the behavior of existing methods and evaluates the
 * potential of adding a caching layer on top.
 * 该基准测试通过对比无缓存和有缓存两种场景，评估添加缓存层对代码生成性能的提升潜力
 * 在实际应用中，相同的查询计划可能会被重复执行，缓存生成的代码可以显著减少编译开销
 * 测试重点包括：1) 动态代码生成的性能开销 2) 编译过程的性能开销 3) 缓存策略的有效性
 */
@Fork(value = 1, jvmArgsPrepend = "-Xmx1024m") // JMH配置：使用1个进程fork，JVM最大堆内存设置为1024MB
@Measurement(iterations = 10, time = 1) // JMH配置：测量阶段执行10次迭代，每次迭代持续1秒
@Warmup(iterations = 0) // JMH配置：不进行预热迭代，直接开始测量
@Threads(1) // JMH配置：使用单线程执行基准测试
@OutputTimeUnit(TimeUnit.SECONDS) // JMH配置：输出结果的时间单位为秒
@BenchmarkMode(Mode.Throughput) // JMH配置：测试模式为吞吐量模式，即单位时间内执行的操作次数
public class CodeGenerationBenchmark { // 基准测试主类，用于测试Calcite代码生成和编译的性能

  /**
   * State holding the generated queries/plans and additional information
   * exploited by the embedded compiler in order to dynamically build a Java class.
   * QueryState是一个状态类，用于存储基准测试中生成的查询计划和相关的编译信息
   * 该类使用@State(Scope.Thread)注解，表示每个测试线程都有独立的实例
   * 该类负责：1) 生成不同配置的测试查询 2) 为每个查询创建关系表达式计划 3) 生成对应的Java源代码
   */
  @State(Scope.Thread) // JMH注解：该状态对象的作用域为线程级别，每个测试线程拥有独立的实例
  public static class QueryState { // 查询状态类，封装基准测试所需的查询和编译信息
    /**
     * The number of distinct queries to be generated.
     * queries参数表示要生成的不同查询的数量
     * 该参数使用@Param注解进行参数化测试，测试1、10、100、1000个不同查询的场景
     * 每个查询通过在WHERE子句中使用不同的常量值来区分，模拟实际应用中查询的差异
     */
    @Param({"1", "10", "100", "1000"}) // JMH参数注解：测试不同数量的查询场景
    int queries; // 要生成的不同查询的数量

    /**
     * The number of joins for each generated query.
     * joins参数表示每个生成的查询中包含的连接操作数量
     * 该参数使用@Param注解进行参数化测试，测试1、10、20个连接的场景
     * 更多的连接操作会生成更复杂的查询计划，从而产生更复杂的Java代码
     */
    @Param({"1", "10", "20"}) // JMH参数注解：测试不同连接数量的场景
    int joins; // 每个查询中包含的连接操作数量

    /**
     * The number of disjunctions for each generated query.
     * whereClauseDisjunctions参数表示每个查询的WHERE子句中包含的OR条件数量
     * 该参数使用@Param注解进行参数化测试，测试1、10、100个OR条件的场景
     * 更多的OR条件会生成更复杂的过滤表达式，影响代码生成的复杂度
     */
    @Param({"1", "10", "100"}) // JMH参数注解：测试不同OR条件数量的场景
    int whereClauseDisjunctions; // 每个查询WHERE子句中OR条件的数量

    /**
     * The necessary plan information for every generated query.
     * planInfos数组存储每个生成查询的完整计划信息
     * 每个PlanInfo对象包含：1) 类声明表达式 2) EnumerableRel计划 3) 生成的Java源代码字符串
     * 该数组在setup方法中初始化，用于在基准测试中循环使用不同的查询计划
     */
    PlanInfo[] planInfos; // 存储每个查询的计划信息的数组

    /**
     * compilerFactory是Janino编译器的工厂实例
     * Janino是一个轻量级的Java编译器，可以在运行时动态编译Java源代码
     * Calcite使用Janino将生成的Java代码编译为可执行的类
     * 该工厂在setup方法中初始化，用于在compile方法中创建编译器实例
     */
    ICompilerFactory compilerFactory; // Janino编译器工厂，用于动态编译生成的Java代码

    /**
     * currentPlan是一个计数器，用于在基准测试中循环使用不同的查询计划
     * 每次调用nextPlan方法时，该计数器递增并取模，实现循环访问planInfos数组
     * 这样可以在基准测试中模拟多个查询的执行场景
     */
    private int currentPlan = 0; // 当前使用的查询计划索引，用于循环访问planInfos数组

    /**
     * setup方法使用@Setup(Level.Trial)注解，表示在每次基准测试试验开始时调用一次
     * 该方法负责初始化QueryState的所有必要数据，包括：
     * 1) 创建和配置VolcanoPlanner优化器
     * 2) 注册必要的优化规则
     * 3) 生成指定数量的测试查询
     * 4) 为每个查询创建关系表达式计划
     * 5) 生成每个查询对应的Java源代码
     * 6) 初始化编译器工厂
     */
    @Setup(Level.Trial) // JMH注解：在每次基准测试试验开始时调用一次
    public void setup() { // 初始化方法，设置基准测试所需的查询计划和编译环境
      planInfos = new PlanInfo[queries]; // 根据参数queries创建PlanInfo数组，用于存储每个查询的计划信息
      VolcanoPlanner planner = new VolcanoPlanner(); // 创建火山优化器实例，Calcite的基于代价的优化器
      planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 向优化器注册ConventionTraitDef，用于管理调用约定特征
      planner.addRule(CoreRules.FILTER_TO_CALC); // 注册FILTER_TO_CALC规则，将Filter节点转换为Calc节点
      planner.addRule(CoreRules.PROJECT_TO_CALC); // 注册PROJECT_TO_CALC规则，将Project节点转换为Calc节点
      planner.addRule(EnumerableRules.ENUMERABLE_CALC_RULE); // 注册ENUMERABLE_CALC_RULE，将Calc节点转换为EnumerableCalc
      planner.addRule(EnumerableRules.ENUMERABLE_JOIN_RULE); // 注册ENUMERABLE_JOIN_RULE，将Join节点转换为EnumerableJoin
      planner.addRule(EnumerableRules.ENUMERABLE_VALUES_RULE); // 注册ENUMERABLE_VALUES_RULE，将Values节点转换为EnumerableValues

      RelDataTypeFactory typeFactory = // 创建Java类型工厂实例，用于管理关系数据类型
          new JavaTypeFactoryImpl(org.apache.calcite.rel.type.RelDataTypeSystem.DEFAULT); // 使用默认的关系数据类型系统
      RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(typeFactory)); // 创建关系优化集群，包含优化器、RexBuilder和类型工厂
      RelTraitSet desiredTraits = // 创建期望的特征集合，用于指定目标物理属性
          cluster.traitSet().replace(EnumerableConvention.INSTANCE); // 将调用约定设置为EnumerableConvention，表示使用可枚举的物理实现

      RelBuilder relBuilder = RelFactories.LOGICAL_BUILDER.create(cluster, null); // 创建关系构建器，用于以编程方式构建关系表达式树
      // Generates queries of the following form depending on the configuration parameters.
      // 根据配置参数生成以下形式的查询：
      // SELECT `t`.`name`
      // FROM (VALUES (1, 'Value0')) AS `t` (`id`, `name`)
      // INNER JOIN (VALUES (1, 'Value1')) AS `t` (`id`, `name`) AS `t0` ON `t`.`id` = `t0`.`id`
      // INNER JOIN (VALUES (2, 'Value2')) AS `t` (`id`, `name`) AS `t1` ON `t`.`id` = `t1`.`id`
      // INNER JOIN (VALUES (3, 'Value3')) AS `t` (`id`, `name`) AS `t2` ON `t`.`id` = `t2`.`id`
      // INNER JOIN ...
      // WHERE
      //  `t`.`name` = 'name0' OR
      //  `t`.`name` = 'name1' OR
      //  `t`.`name` = 'name2' OR
      //  ...
      //  OR `t`.`id` = 0
      // 最后一个OR条件（即 t.id = $i）通过使用不同的常量值使查询彼此不同
      for (int i = 0; i < queries; i++) { // 循环生成指定数量的查询，每个查询都有不同的id常量值
        relBuilder.values(new String[]{"id", "name"}, 1, "Value" + 0); // 创建VALUES节点作为主表，包含id和name两个字段，初始值为(1, 'Value0')
        for (int j = 1; j <= joins; j++) { // 循环创建指定数量的连接表
          relBuilder
              .values(new String[]{"id", "name"}, j, "Value" + j) // 创建VALUES节点作为连接表，id值为j，name值为'Value' + j
              .join(JoinRelType.INNER, "id"); // 执行内连接操作，连接条件是id字段相等
        }

        List<RexNode> disjunctions = new ArrayList<>(); // 创建OR条件列表，用于存储WHERE子句中的所有OR条件
        for (int j = 0; j < whereClauseDisjunctions; j++) { // 循环创建指定数量的name字段等值条件
          disjunctions.add( // 将name字段的等值条件添加到OR条件列表
              relBuilder.equals( // 创建等值表达式
                  relBuilder.field("name"), // 引用name字段
                  relBuilder.literal("name" + j))); // 创建字符串字面量'name' + j
        }
        disjunctions.add( // 添加最后一个OR条件，使用id字段的等值条件，该条件的值使每个查询不同
            relBuilder.equals( // 创建等值表达式
                relBuilder.field("id"), // 引用id字段
                relBuilder.literal(i))); // 创建整数字面量i，这是使每个查询不同的关键
        RelNode query = // 构建完整的关系表达式查询
            relBuilder
                .filter(relBuilder.or(disjunctions)) // 添加过滤器，使用OR条件列表创建OR表达式
                .project(relBuilder.field("name")) // 添加投影，只输出name字段
                .build(); // 构建关系表达式树

        RelNode query0 = planner.changeTraits(query, desiredTraits); // 将查询的特征集合转换为期望的特征集合（EnumerableConvention）
        planner.setRoot(query0); // 将转换后的查询设置为优化器的根节点

        PlanInfo info = new PlanInfo(); // 创建PlanInfo对象，用于存储该查询的计划信息
        EnumerableRel plan = (EnumerableRel) planner.findBestExp(); // 调用优化器找到最优的物理执行计划，并强制转换为EnumerableRel类型

        EnumerableRelImplementor relImplementor = // 创建EnumerableRel实现器，负责将关系表达式转换为Java代码
            new EnumerableRelImplementor(plan.getCluster().getRexBuilder(), new HashMap<>()); // 使用RexBuilder和空的参数映射创建实现器
        info.classExpr = relImplementor.implementRoot(plan, EnumerableRel.Prefer.ARRAY); // 调用实现器生成类声明表达式，使用数组优先模式
        info.javaCode = // 将类声明表达式转换为Java源代码字符串
            Expressions.toString(info.classExpr.memberDeclarations, "\n", false); // 使用换行符分隔成员声明，不包含缩进
        info.plan = plan; // 保存优化后的EnumerableRel计划
        planInfos[i] = info; // 将PlanInfo对象保存到数组中
      }

      try { // 尝试获取Janino编译器工厂
        compilerFactory = // 创建编译器工厂实例
            CompilerFactoryFactory.getDefaultCompilerFactory( // 获取默认的编译器工厂
                CodeGenerationBenchmark.class.getClassLoader()); // 使用当前类的类加载器
      } catch (Exception e) { // 捕获获取编译器工厂时的异常
        throw new IllegalStateException( // 抛出非法状态异常
            "Unable to instantiate java compiler", e); // 异常消息：无法实例化Java编译器
      }

    }

    /**
     * compile方法使用Janino编译器将生成的Java源代码编译为可执行的Bindable实例
     * 该方法是基准测试的核心操作之一，测试动态编译的性能开销
     * 
     * @param plan EnumerableRel关系表达式计划，用于确定实现的接口类型
     * @param className 生成的Java类的类名
     * @param code 生成的Java源代码字符串
     * @return Bindable实例，可以执行查询并返回结果
     * @throws CompileException 当Java代码编译失败时抛出
     * @throws IOException 当读取代码字符串时发生IO错误时抛出
     */
    Bindable compile(EnumerableRel plan, String className, String code) // 编译方法，将Java源代码编译为Bindable实例
        throws CompileException, IOException { // 声明可能抛出的异常类型
      final StringReader stringReader = new StringReader(code); // 创建字符串读取器，将Java源代码字符串转换为字符流
      IClassBodyEvaluator cbe = compilerFactory.newClassBodyEvaluator(); // 创建类体求值器，用于编译和执行Java类
      cbe.setClassName(className); // 设置生成的Java类的类名
      cbe.setExtendedClass(Utilities.class); // 设置生成的类继承自Utilities类，提供工具方法支持
      cbe.setImplementedInterfaces( // 设置生成的类实现的接口
          plan.getRowType().getFieldCount() == 1 // 判断查询结果是否只包含一个字段
              ? new Class[]{Bindable.class, Typed.class} // 如果只有一个字段，实现Bindable和Typed接口
              : new Class[]{ArrayBindable.class}); // 如果有多个字段，实现ArrayBindable接口
      cbe.setParentClassLoader(EnumerableInterpretable.class.getClassLoader()); // 设置父类加载器，确保可以访问Calcite的类
      return (Bindable) cbe.createInstance(stringReader); // 编译并创建类的实例，强制转换为Bindable类型并返回
    }

    /**
     * nextPlan方法用于循环获取不同的查询计划索引
     * 该方法在基准测试中被调用，用于模拟多个查询的执行场景
     * 
     * @return 当前查询计划的索引，然后递增计数器
     */
    int nextPlan() { // 获取下一个查询计划的索引
      int ret = currentPlan; // 保存当前索引作为返回值
      currentPlan = (currentPlan + 1) % queries; // 递增计数器并取模，实现循环访问
      return ret; // 返回当前索引
    }
  }

  /** Plan information. */
  private static class PlanInfo { // PlanInfo内部类，用于存储单个查询的完整计划信息
    ClassDeclaration classExpr; // 类声明表达式，表示生成的Java类的抽象语法树
    EnumerableRel plan; // EnumerableRel关系表达式计划，优化后的物理执行计划
    String javaCode; // Java源代码字符串，从类声明表达式转换而来
  }

  /**
   * State holding a cache that is initialized
   * once at the beginning of each iteration.
   * CacheState是一个状态类，用于在基准测试中维护缓存实例
   * 该类使用@State(Scope.Thread)注解，表示每个测试线程都有独立的缓存实例
   * 缓存使用Guava Cache实现，用于存储Java代码到Bindable实例的映射
   */
  @State(Scope.Thread) // JMH注解：该状态对象的作用域为线程级别
  public static class CacheState { // 缓存状态类，封装基准测试中使用的缓存
    @Param({"10", "100", "1000"}) // JMH参数注解：测试不同缓存大小的场景
    int cacheSize; // 缓存的最大容量，测试10、100、1000三种大小

    Cache<String, Bindable> cache; // Guava缓存实例，键为Java代码字符串，值为编译后的Bindable实例

    /**
     * setup方法使用@Setup(Level.Iteration)注解，表示在每次基准测试迭代开始时调用
     * 该方法负责创建和配置缓存实例
     * 缓存配置包括：1) 最大容量 2) 并发级别设置为1（单线程访问）
     */
    @Setup(Level.Iteration) // JMH注解：在每次基准测试迭代开始时调用
    public void setup() { // 初始化方法，创建缓存实例
      cache = CacheBuilder.newBuilder().maximumSize(cacheSize).concurrencyLevel(1).build(); // 使用CacheBuilder创建缓存，设置最大容量和并发级别
    }

  }


  /**
   * Benchmarks the part creating Bindable instances from
   * {@link EnumerableInterpretable#getBindable(ClassDeclaration, String, int)}
   * method without any additional caching layer.
   * 该基准测试方法测试不使用缓存时创建Bindable实例的性能
   * 该方法模拟Calcite原始的代码生成和编译流程，每次都需要重新编译Java代码
   * 这是基准测试的基准场景，用于与有缓存的场景进行对比
   * 
   * @param state QueryState状态对象，包含查询计划和编译器
   * @return Bindable实例，可以执行查询并返回结果
   * @throws Exception 当编译过程中发生错误时抛出
   */
  @Benchmark // JMH注解：标记该方法为基准测试方法
  public Bindable<?> getBindableNoCache(QueryState state) throws Exception { // 无缓存的基准测试方法
    PlanInfo info = state.planInfos[state.nextPlan()]; // 获取当前查询的计划信息
    return state.compile(info.plan, info.classExpr.name, info.javaCode); // 调用compile方法编译Java代码并返回Bindable实例
  }

  /**
   * Benchmarks the part of creating Bindable instances from
   * {@link EnumerableInterpretable#getBindable(ClassDeclaration, String, int)}
   * method with an additional cache layer.
   * 该基准测试方法测试使用缓存时创建Bindable实例的性能
   * 该方法在编译前先检查缓存，如果缓存中已存在相同的代码，则直接返回缓存的实例
   * 这是基准测试的优化场景，用于评估缓存对性能的提升效果
   * 
   * @param jState QueryState状态对象，包含查询计划和编译器
   * @param chState CacheState状态对象，包含缓存实例
   * @return Bindable实例，可以执行查询并返回结果
   * @throws Exception 当编译过程中发生错误时抛出
   */
  @Benchmark // JMH注解：标记该方法为基准测试方法
  public Bindable<?> getBindableWithCache( // 有缓存的基准测试方法
      QueryState jState, // QueryState状态对象，包含查询计划信息
      CacheState chState) throws Exception { // CacheState状态对象，包含缓存实例
    PlanInfo info = jState.planInfos[jState.nextPlan()]; // 获取当前查询的计划信息
    Cache<String, Bindable> cache = chState.cache; // 获取缓存实例

    EnumerableInterpretable.StaticFieldDetector detector = // 创建静态字段检测器，用于检测生成的代码是否包含静态字段
        new EnumerableInterpretable.StaticFieldDetector(); // 静态字段检测器实例
    info.classExpr.accept(detector); // 遍历类声明表达式，检测是否包含静态字段
    if (!detector.containsStaticField) { // 如果不包含静态字段，可以使用缓存
      return cache.get( // 从缓存中获取Bindable实例
          info.javaCode, // 缓存键：Java源代码字符串
          () -> jState.compile(info.plan, info.classExpr.name, info.javaCode)); // 缓存未命中时的加载函数：编译Java代码
    }
    throw new IllegalStateException("Benchmark queries should not arrive here"); // 如果包含静态字段，抛出异常（基准测试查询不应到达这里）
  }

  /**
   * main方法是基准测试的入口点，用于手动运行基准测试
   * 该方法配置基准测试选项并启动测试运行器
   * 
   * @param args 命令行参数（未使用）
   * @throws RunnerException 当基准测试运行过程中发生错误时抛出
   */
  public static void main(String[] args) throws RunnerException { // 主方法，基准测试的入口点
    Options opt = new OptionsBuilder() // 创建选项构建器，用于配置基准测试参数
        .include(CodeGenerationBenchmark.class.getName()) // 包含CodeGenerationBenchmark类中的所有基准测试方法
        .addProfiler(GCProfiler.class) // 添加GC性能分析器，用于监控垃圾回收行为
        .detectJvmArgs() // 自动检测JVM参数
        .build(); // 构建选项对象

    new Runner(opt).run(); // 创建运行器并运行基准测试
  }
} // 类定义结束
