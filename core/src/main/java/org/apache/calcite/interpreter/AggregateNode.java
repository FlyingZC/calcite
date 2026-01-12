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
// 声明包名,表示这个类属于 org.apache.calcite.interpreter 包,是 Calcite 解释器模块的一部分
package org.apache.calcite.interpreter;

// 导入 Calcite 数据上下文接口,用于提供执行环境信息
import org.apache.calcite.DataContext;
// 导入聚合添加上下文接口,用于在代码生成时提供聚合函数的上下文信息
import org.apache.calcite.adapter.enumerable.AggAddContext;
// 导入聚合实现状态类,用于跟踪聚合函数的实现状态
import org.apache.calcite.adapter.enumerable.AggImpState;
// 导入 Java 行格式枚举,定义 Java 表示行的不同格式
import org.apache.calcite.adapter.enumerable.JavaRowFormat;
// 导入物理类型接口,表示 Calcite 中的物理类型系统
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入物理类型实现类,提供物理类型的具体实现
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
// 导入 Rex 到 Lix 转换器,用于将 Rex 表达式转换为 LINQ 表达式
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
// 导入聚合添加上下文实现类,提供 AggAddContext 的具体实现
import org.apache.calcite.adapter.enumerable.impl.AggAddContextImpl;
// 导入 Java 类型工厂,用于创建 Java 类型
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入行构建器,用于构建 Row 对象
import org.apache.calcite.interpreter.Row.RowBuilder;
// 导入代码块构建器,用于构建 Java 代码块
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入表达式接口,表示 LINQ 表达式树的节点
import org.apache.calcite.linq4j.tree.Expression;
// 导入表达式工具类,提供创建各种表达式的静态方法
import org.apache.calcite.linq4j.tree.Expressions;
// 导入参数表达式,表示方法参数的表达式
import org.apache.calcite.linq4j.tree.ParameterExpression;
// 导入聚合关系节点,表示逻辑计划中的聚合操作
import org.apache.calcite.rel.core.Aggregate;
// 导入聚合调用类,表示对聚合函数的具体调用
import org.apache.calcite.rel.core.AggregateCall;
// 导入关系数据类型工厂,用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入 Rex 输入引用,表示对输入字段的引用
import org.apache.calcite.rex.RexInputRef;
// 导入 Rex 节点接口,表示关系表达式树的节点
import org.apache.calcite.rex.RexNode;
// 导入函数上下文工具类,用于创建函数上下文
import org.apache.calcite.runtime.FunctionContexts;
// 导入函数上下文接口,为用户定义函数提供上下文信息
import org.apache.calcite.schema.FunctionContext;
// 导入聚合函数实现类,表示用户定义的聚合函数
import org.apache.calcite.schema.impl.AggregateFunctionImpl;
// 导入 SQL 聚合函数接口,表示 SQL 中的聚合函数
import org.apache.calcite.sql.SqlAggFunction;
// 导入 SQL 内部操作符,包含 Calcite 内部使用的操作符
import org.apache.calcite.sql.fun.SqlInternalOperators;
// 导入 SQL 标准操作符表,包含所有标准 SQL 操作符
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
// 导入 SQL 一致性接口,定义 SQL 方言的一致性级别
import org.apache.calcite.sql.validate.SqlConformance;
// 导入 SQL 一致性枚举,提供标准的一致性级别实现
import org.apache.calcite.sql.validate.SqlConformanceEnum;
// 导入不可变位集合,用于高效地表示和操作位集合
import org.apache.calcite.util.ImmutableBitSet;
// 导入工具类,提供各种实用方法
import org.apache.calcite.util.Util;

// 导入 Google Guava 的不可变列表,提供不可变的列表实现
import com.google.common.collect.ImmutableList;

// 导入 Checker Framework 的可空注解,用于标记可能为 null 的类型
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入构造器类,用于反射获取类的构造方法
import java.lang.reflect.Constructor;
// 导入调用目标异常,表示通过反射调用方法时发生的异常
import java.lang.reflect.InvocationTargetException;
// 导入 BigDecimal 类,用于高精度十进制运算
import java.math.BigDecimal;
// 导入 ArrayList,提供动态数组实现
import java.util.ArrayList;
// 导入 HashMap,提供哈希表实现的映射
import java.util.HashMap;
// 导入 List 接口,定义列表的通用行为
import java.util.List;
// 导入 Map 接口,定义映射的通用行为
import java.util.Map;
// 导入双函数接口,表示接受两个参数并返回结果的函数
import java.util.function.BiFunction;
// 导入供应者接口,表示提供对象的工厂
import java.util.function.Supplier;

// 导入 Nullness 工具类的 castNonNull 方法,用于将可空类型转换为非空类型
import static org.apache.calcite.linq4j.Nullness.castNonNull;

// 导入 Objects 的 requireNonNull 方法,用于检查对象是否为 null
import static java.util.Objects.requireNonNull;

/**
 * 解释器节点,用于实现 {@link org.apache.calcite.rel.core.Aggregate} 关系节点
 *
 * 这个类是 Calcite 解释器模式的核心组件之一,负责在解释器模式下执行聚合操作。
 * 解释器模式是 Calcite 执行查询的一种方式,它通过解释器直接执行关系代数操作,
 * 而不是生成代码。这种方式适用于调试、测试以及某些特殊场景。
 *
 * 聚合操作是 SQL 查询中最常见的操作之一,包括 GROUP BY、COUNT、SUM、AVG、MIN、MAX 等。
 * 这个类实现了完整的聚合功能,支持:
 * 1. 单级和多级分组(GROUP BY)
 * 2. 多种聚合函数(COUNT, SUM, AVG, MIN, MAX 等)
 * 3. 聚合函数的过滤(HAVING 子句)
 * 4. 用户定义聚合函数(UDAF)
 * 5. 分组集合(GROUPING SETS)
 *
 * 工作原理:
 * 1. 接收上游节点产生的数据行
 * 2. 根据分组键将数据行分发到不同的分组
 * 3. 在每个分组中使用累加器(Accumulator)计算聚合值
 * 4. 当所有数据处理完成后,输出每个分组的聚合结果
 *
 * 关键设计:
 * - 使用累加器模式:每个聚合函数都有一个对应的累加器,用于增量计算聚合值
 * - 支持多种累加器实现:内置函数使用优化的实现,用户定义函数使用反射调用
 * - 支持分组集合:可以同时处理多个分组方式,生成多个聚合结果
 */
public class AggregateNode extends AbstractSingleNode<Aggregate> {
  // 分组列表,存储所有的分组(Grouping 对象)
  // 每个 Grouping 对象代表一种分组方式,支持 GROUPING SETS 功能
  // 例如: GROUP BY GROUPING SETS ((a,b), (a), ()) 会创建三个 Grouping 对象
  private final List<Grouping> groups = new ArrayList<>();
  // 所有分组的并集,包含所有分组集合中出现的所有字段位置
  // 用于确定输出行的长度和结构
  // 例如: GROUPING SETS ((0,1), (0,2)) 的 unionGroups 为 {0,1,2}
  private final ImmutableBitSet unionGroups;
  // 输出行的长度,等于分组字段数加上聚合函数数
  // 用于构建输出行时确定数组大小
  private final int outputRowLength;
  // 累加器工厂列表,每个工厂可以创建一个聚合函数的累加器
  // 工厂模式允许为每个聚合函数创建新的累加器实例
  // 支持多种聚合函数:COUNT, SUM, MIN, MAX, 用户定义函数等
  private final ImmutableList<AccumulatorFactory> accumulatorFactories;
  // 数据上下文,提供执行环境信息,如用户定义函数的上下文
  // 包含配置信息、会话信息等运行时数据
  private final DataContext dataContext;

  /**
   * 构造函数,初始化聚合节点
   *
   * @param compiler 编译器对象,用于编译表达式和获取数据上下文
   * @param rel 聚合关系节点,包含聚合操作的元数据信息
   *
   * 构造函数的主要任务:
   * 1. 初始化父类和基本成员变量
   * 2. 创建所有分组集合对应的 Grouping 对象
   * 3. 计算所有分组的并集,确定输出结构
   * 4. 为每个聚合函数创建对应的累加器工厂
   *
   * 分组集合(GROUPING SETS)支持:
   * - 允许在单个查询中指定多个分组方式
   * - 例如: GROUP BY GROUPING SETS ((dept_id, emp_id), (dept_id), ())
   *   这会生成三个分组:按部门和员工分组、只按部门分组、不分组(全表聚合)
   */
  public AggregateNode(Compiler compiler, Aggregate rel) {
    // 调用父类构造函数,初始化编译器和关系节点
    super(compiler, rel);
    // 从编译器中获取数据上下文,保存为成员变量
    // 数据上下文包含运行时配置、会话信息等,用于用户定义函数
    this.dataContext = compiler.getDataContext();

    // 初始化并集位集合,从空集合开始
    // 用于收集所有分组集合中出现的所有字段位置
    ImmutableBitSet union = ImmutableBitSet.of();

    // 遍历所有分组集合,创建对应的 Grouping 对象
    // groupSets 包含所有分组方式的字段位置集合
    for (ImmutableBitSet group : rel.getGroupSets()) {
      // 将当前分组集合与并集合并,累积所有出现的字段
      union = union.union(group);
      // 为当前分组集合创建 Grouping 对象并添加到 groups 列表
      // Grouping 对象负责管理该分组的所有累加器
      groups.add(new Grouping(group));
    }

    // 保存所有分组的并集,用于后续确定输出行的结构
    this.unionGroups = union;
    // 计算输出行的长度 = 分组字段数 + 聚合函数数
    // cardinality() 返回集合中元素的个数
    // getAggCallList() 返回所有聚合函数调用的列表
    this.outputRowLength = unionGroups.cardinality()
        + rel.getAggCallList().size();

    // 创建不可变列表的构建器,用于构建累加器工厂列表
    ImmutableList.Builder<AccumulatorFactory> builder = ImmutableList.builder();
    // 为每个聚合函数调用创建对应的累加器工厂
    for (AggregateCall aggregateCall : rel.getAggCallList()) {
      // 调用 getAccumulator 方法获取累加器工厂
      // false 表示不忽略过滤器,即如果聚合函数有过滤条件则要处理
      // @SuppressWarnings 注解忽略反射调用检查的警告
      @SuppressWarnings("method.invocation.invalid")
      AccumulatorFactory accumulator =
          getAccumulator(compiler, aggregateCall, false);
      // 将创建的累加器工厂添加到构建器
      builder.add(accumulator);
    }
    // 构建不可变的累加器工厂列表并保存
    // 不可变列表保证线程安全和防止意外修改
    accumulatorFactories = builder.build();
  }

  /**
   * 执行聚合操作的主方法
   *
   * 这个方法实现了聚合操作的两个阶段:
   * 1. 数据处理阶段:接收输入行并分发给各个分组进行累加
   * 2. 结果输出阶段:所有数据处理完成后,输出每个分组的聚合结果
   *
   * @throws InterruptedException 如果线程被中断
   *
   * 执行流程:
   * 1. 从上游节点(source)接收数据行
   * 2. 将每行数据发送到所有分组中(支持 GROUPING SETS)
   * 3. 每个分组根据其分组键找到对应的累加器列表并更新累加器
   * 4. 当所有数据处理完成后,调用每个分组的 end 方法输出结果
   * 5. 将结果发送到下游节点(sink)
   *
   * GROUPING SETS 处理:
   * - 如果有多个分组集合,同一行数据会被发送到多个分组
   * - 每个分组独立维护自己的累加器状态
   * - 最终会输出多个聚合结果行,每个分组集合一行
   */
  @Override public void run() throws InterruptedException {
    // 声明行变量,用于接收上游数据
    Row r;
    // 循环从上游节点接收数据行,直到返回 null 表示数据结束
    // source 是父类 AbstractSingleNode 提供的数据源
    while ((r = source.receive()) != null) {
      // 将当前行数据发送到所有分组中
      // 支持多个分组集合,同一行可能被处理多次
      for (Grouping group : groups) {
        // 调用分组的 send 方法,将行数据分发给该分组的累加器
        group.send(r);
      }
    }

    // 所有数据处理完成后,输出每个分组的聚合结果
    // 这是聚合操作的第二阶段
    for (Grouping group : groups) {
      // 调用分组的 end 方法,输出该分组所有累加器的最终结果
      // sink 是父类 AbstractSingleNode 提供的数据接收器
      group.end(sink);
    }
  }

  /**
   * 为聚合函数调用创建累加器工厂
   *
   * 这个方法根据聚合函数的类型返回相应的累加器工厂实现
   * 支持的聚合函数类型:
   * 1. COUNT - 计数函数
   * 2. SUM/SUM0 - 求和函数
   * 3. MAX/MIN - 最大值/最小值函数
   * 4. LITERAL_AGG - 字面量聚合
   * 5. 用户定义聚合函数(UDAF)
   * 6. 其他标准聚合函数(AVG 等)
   *
   * @param compiler 编译器,用于编译表达式
   * @param call 聚合函数调用,包含函数类型、参数等信息
   * @param ignoreFilter 是否忽略过滤条件
   *               true: 不处理过滤条件
   *               false: 如果有过滤条件则创建 FilterAccumulator 包装器
   * @return 累加器工厂,可以创建新的累加器实例
   *
   * 过滤条件处理:
   * - 如果聚合函数有过滤条件(filterArg >= 0)且不忽略过滤,
   *   则递归调用自身获取不带过滤的累加器工厂,然后用 FilterAccumulator 包装
   * - FilterAccumulator 会在调用内部累加器前检查过滤条件
   */
  private AccumulatorFactory getAccumulator(Compiler compiler,
      final AggregateCall call, boolean ignoreFilter) {
    // 检查聚合函数是否有过滤条件且不忽略过滤
    // filterArg >= 0 表示存在过滤条件,指向输入行中的布尔字段
    if (call.filterArg >= 0 && !ignoreFilter) {
      // 递归调用获取不带过滤的累加器工厂
      // ignoreFilter = true 表示这次调用不处理过滤
      final AccumulatorFactory factory = getAccumulator(compiler, call, true);
      // 返回一个工厂,创建的累加器会被 FilterAccumulator 包装
      // FilterAccumulator 会在调用内部累加器前检查过滤条件
      return () -> {
        // 从工厂获取基础累加器
        final Accumulator accumulator = factory.get();
        // 用 FilterAccumulator 包装基础累加器,传入过滤字段位置
        return new FilterAccumulator(accumulator, call.filterArg);
      };
    }
    // 获取聚合函数的类型(如 COUNT, SUM, MAX 等)
    final SqlAggFunction op = call.getAggregation();
    // 根据聚合函数类型创建对应的累加器工厂
    if (op == SqlStdOperatorTable.COUNT) {
      // COUNT 函数:返回创建 CountAccumulator 的工厂
      // CountAccumulator 是一个简单的计数器实现
      return () -> new CountAccumulator(call);
    } else if (op == SqlStdOperatorTable.SUM
        || op == SqlStdOperatorTable.SUM0) {
      // SUM 或 SUM0 函数:使用用户定义聚合函数实现
      // sumClass 根据数据类型返回对应的求和类(IntSum, LongSum, DoubleSum 等)
      final Class<?> clazz = sumClass(call);
      // 创建 UdaAccumulatorFactory,使用反射调用求和类的方法
      // op == SqlStdOperatorTable.SUM 表示如果是 SUM,空集合返回 null
      // SUM0 则空集合返回 0
      return new UdaAccumulatorFactory(getAggFunction(clazz), call,
          op == SqlStdOperatorTable.SUM, dataContext);
    } else if (op == SqlStdOperatorTable.MAX
        || op == SqlStdOperatorTable.MIN) {
      // MAX 或 MIN 函数:使用用户定义聚合函数实现
      // maxMinClass 根据数据类型返回对应的比较类(MaxInt, MinInt 等)
      final Class<?> clazz = maxMinClass(call);
      // 创建 UdaAccumulatorFactory,第三个参数 true 表示空集合返回 null
      return new UdaAccumulatorFactory(getAggFunction(clazz), call, true,
          dataContext);
    } else if (op == SqlInternalOperators.LITERAL_AGG) {
      // LITERAL_AGG:字面量聚合,返回常量值
      // 编译 Rex 表达式列表为标量
      final Scalar scalar = compiler.compile(call.rexList, null);
      // 执行标量获取常量值
      final Object value = scalar.execute(compiler.createContext());
      // 返回创建 LiteralAccumulator 的工厂,该累加器总是返回相同的值
      return () -> new LiteralAccumulator(value);
    } else {
      // 其他聚合函数(如 AVG 等):使用代码生成方式实现
      // 这是最通用的实现方式,通过生成 Java 代码来执行聚合

      // 获取 Java 类型工厂,用于创建 Java 类型
      final JavaTypeFactory typeFactory =
          (JavaTypeFactory) rel.getCluster().getTypeFactory();
      // 状态偏移量,用于支持多个聚合函数共享状态
      int stateOffset = 0;
      // 创建聚合实现状态对象,跟踪聚合函数的实现过程
      // 参数:状态偏移量、聚合调用、是否去重
      final AggImpState agg = new AggImpState(0, call, false);
      // 获取状态大小,即累加器需要维护的状态变量数量
      int stateSize = requireNonNull(agg.state, "agg.state").size();

      // 创建代码块构建器,用于生成 Java 代码
      final BlockBuilder builder2 = new BlockBuilder();
      // 创建输入行的物理类型,使用数组格式存储
      final PhysType inputPhysType =
          PhysTypeImpl.of(typeFactory, rel.getInput().getRowType(),
              JavaRowFormat.ARRAY);
      // 创建累加器状态的数据类型构建器
      final RelDataTypeFactory.Builder builder = typeFactory.builder();
      // 为每个状态变量添加类型信息
      for (Expression expression : agg.state) {
        builder.add("a",
            typeFactory.createJavaType((Class) expression.getType()));
      }
      // 创建累加器状态的物理类型,使用数组格式存储
      final PhysType accPhysType =
          PhysTypeImpl.of(typeFactory, builder.build(), JavaRowFormat.ARRAY);
      // 创建输入行参数表达式,用于生成的代码中引用输入行
      final ParameterExpression inParameter =
          Expressions.parameter(inputPhysType.getJavaRowType(), "in");
      // 创建累加器参数表达式,用于生成的代码中引用累加器状态
      final ParameterExpression acc_ =
          Expressions.parameter(accPhysType.getJavaRowType(), "acc");

      // 创建累加器字段引用列表,用于访问累加器状态中的各个字段
      List<Expression> accumulator = new ArrayList<>(stateSize);
      for (int j = 0; j < stateSize; j++) {
        // 创建对累加器数组中第 j 个字段的引用
        accumulator.add(accPhysType.fieldReference(acc_, j + stateOffset));
      }
      // 更新聚合状态中的累加器引用
      agg.state = accumulator;

      // 创建聚合添加上下文,用于生成添加值的代码
      // 这个匿名内部类提供了生成代码所需的各种信息
      AggAddContext addContext =
          new AggAddContextImpl(builder2, accumulator) {
            @Override public List<RexNode> rexArguments() {
              // 返回聚合函数的参数列表,转换为 Rex 输入引用
              List<RexNode> args = new ArrayList<>();
              for (int index : agg.call.getArgList()) {
                // 为每个参数位置创建 RexInputRef
                args.add(RexInputRef.of(index, inputPhysType.getRowType()));
              }
              return args;
            }

            @Override public @Nullable RexNode rexFilterArgument() {
              // 返回过滤参数,如果没有过滤则返回 null
              return agg.call.filterArg < 0
                  ? null
                  : RexInputRef.of(agg.call.filterArg,
                      inputPhysType.getRowType());
            }

            @Override public RexToLixTranslator rowTranslator() {
              // 创建 Rex 到 Lix 的转换器,用于将 Rex 表达式转换为 Java 表达式
              final SqlConformance conformance =
                  SqlConformanceEnum.DEFAULT; // TODO: 应该从实现器获取一致性级别
              return RexToLixTranslator.forAggregation(typeFactory,
                  currentBlock(),
                  new RexToLixTranslator.InputGetterImpl(inParameter,
                      inputPhysType),
                  conformance);
            }
          };

      // 调用聚合实现器的 implementAdd 方法,生成添加值的代码
      // 这会在 builder2 中生成 Java 代码
      agg.implementor.implementAdd(requireNonNull(agg.context, "agg.context"), addContext);

      // 创建上下文参数表达式,用于生成的代码
      final ParameterExpression context_ =
          Expressions.parameter(Context.class, "context");
      // 创建输出值数组参数表达式
      final ParameterExpression outputValues_ =
          Expressions.parameter(Object[].class, "outputValues");
      // 使用 Janino 编译器将生成的代码块编译为可执行的标量
      final Scalar.Producer addScalarProducer =
          JaninoRexCompiler.baz(context_, outputValues_, builder2.toBlock(),
              ImmutableList.of());
      // 初始化标量为 null(不需要初始化)
      final Scalar initScalar = castNonNull(null);
      // 应用数据上下文获取添加标量
      final Scalar addScalar = addScalarProducer.apply(dataContext);
      // 结束标量为 null(不需要特殊处理)
      final Scalar endScalar = castNonNull(null);
      // 返回标量累加器定义,使用生成的代码执行聚合
      return new ScalarAccumulatorDef(initScalar, addScalar, endScalar,
          rel.getInput().getRowType().getFieldCount(), stateSize, dataContext);
    }
  }

  /**
   * 根据聚合函数的数据类型返回对应的 MAX/MIN 实现类
   *
   * 这个方法为 MAX 和 MIN 聚合函数选择适当的实现类
   * 每种数据类型都有专门优化的实现,以提高性能
   *
   * @param call 聚合函数调用,包含函数类型和数据类型信息
   * @return 对应的 MAX/MIN 实现类
   *
   * 支持的数据类型:
   * - INTEGER -> MaxInt / MinInt
   * - REAL -> MaxFloat / MinFloat
   * - FLOAT/DOUBLE -> MaxDouble / MinDouble
   * - DECIMAL -> MaxBigDecimal / MinBigDecimal
   * - BOOLEAN -> MaxBoolean / MinBoolean
   * - 其他类型(包括 BIGINT) -> MaxLong / MinLong
   *
   * 这些实现类都遵循用户定义聚合函数的接口规范:
   * - init(): 初始化累加器
   * - add(accumulator, value): 添加新值
   * - merge(accumulator0, accumulator1): 合并两个累加器
   * - result(accumulator): 返回最终结果
   */
  private static Class<?> maxMinClass(AggregateCall call) {
    // 判断是 MAX 还是 MIN 函数
    boolean max = call.getAggregation() == SqlStdOperatorTable.MAX;
    // 根据数据类型选择对应的实现类
    switch (call.getType().getSqlTypeName()) {
    // INTEGER 类型:整数
    case INTEGER:
      return max ? MaxInt.class : MinInt.class;
    // REAL 类型:单精度浮点数(SQL 标准)
    case REAL:
      return max ? MaxFloat.class : MinFloat.class;
    // FLOAT 或 DOUBLE 类型:双精度浮点数
    case FLOAT:
    case DOUBLE:
      return max ? MaxDouble.class : MinDouble.class;
    // DECIMAL 类型:高精度十进制数
    case DECIMAL:
      return max ? MaxBigDecimal.class : MinBigDecimal.class;
    // BOOLEAN 类型:布尔值
    case BOOLEAN:
      return max ? MaxBoolean.class : MinBoolean.class;
    // 默认情况(包括 BIGINT):长整数
    default:
      return max ? MaxLong.class : MinLong.class;
    }
  }

  /**
   * 根据聚合函数的数据类型返回对应的 SUM 实现类
   *
   * 这个方法为 SUM 和 SUM0 聚合函数选择适当的实现类
   * 每种数据类型都有专门优化的实现,以提高性能
   *
   * @param call 聚合函数调用,包含数据类型信息
   * @return 对应的 SUM 实现类
   *
   * 支持的数据类型:
   * - DOUBLE/REAL/FLOAT -> DoubleSum
   * - DECIMAL -> BigDecimalSum
   * - INTEGER -> IntSum
   * - BIGINT/其他 -> LongSum
   *
   * 注意:
   * - SUM 和 SUM0 使用相同的实现类
   * - 区别在于空集合的处理:SUM 返回 null,SUM0 返回 0
   * - 这个区别由 UdaAccumulatorFactory 的 nullIfEmpty 参数控制
   */
  private static Class<?> sumClass(AggregateCall call) {
    // 根据数据类型选择对应的求和实现类
    switch (call.type.getSqlTypeName()) {
    // 浮点数类型(包括 REAL)
    case DOUBLE:
    case REAL:
    case FLOAT:
      return DoubleSum.class;
    // 高精度十进制类型
    case DECIMAL:
      return BigDecimalSum.class;
    // 整数类型
    case INTEGER:
      return IntSum.class;
    // 长整数类型和默认情况
    case BIGINT:
    default:
      return LongSum.class;
    }
  }

  /**
   * 为给定的类创建聚合函数实现
   *
   * 这个方法将普通的 Java 类包装为 Calcite 的聚合函数实现
   * 使用反射来发现和调用类中的 init、add、merge、result 方法
   *
   * @param clazz 聚合函数的实现类
   *              这个类必须包含以下方法:
   *              - T init(): 初始化累加器
   *              - T add(T accumulator, ...args): 添加新值
   *              - T merge(T accumulator0, T accumulator1): 合并累加器
   *              - T result(T accumulator): 返回最终结果
   * @return 聚合函数实现对象,封装了反射调用逻辑
   * @throws NullPointerException 如果无法创建聚合函数实现
   *
   * 工作原理:
   * 1. 使用 AggregateFunctionImpl.create() 创建实现对象
   * 2. 该方法通过反射找到并缓存所有需要的方法
   * 3. 后续通过反射调用这些方法来实现聚合函数
   *
   * 错误处理:
   * - 如果类不符合聚合函数接口规范,会抛出异常
   * - requireNonNull 确保返回值不为 null
   */
  private static AggregateFunctionImpl getAggFunction(Class<?> clazz) {
    // 调用 AggregateFunctionImpl.create() 创建聚合函数实现
    // 这个方法会通过反射检查类结构并封装方法调用
    return requireNonNull(
        AggregateFunctionImpl.create(clazz),
        // 如果创建失败,提供详细的错误信息
        () -> "Unable to create AggregateFunctionImpl for " + clazz);
  }

  /** COUNT 函数的累加器实现
   *
   * 这个类实现了 COUNT 聚合函数的累加逻辑
   * COUNT 函数用于统计非空值的数量
   *
   * 特性:
   * - 支持 COUNT(*) 和 COUNT(column) 两种形式
   * - 只统计所有参数都不为 null 的行
   * - 使用 long 类型存储计数,支持大数量级
   *
   * 使用示例:
   * - COUNT(*): 统计所有行(调用时参数列表为空)
   * - COUNT(column): 统计指定列非 null 的行数
   * - COUNT(col1, col2): 统计两列都不为 null 的行数
   */
  private static class CountAccumulator implements Accumulator {
    // 聚合函数调用对象,包含参数列表等信息
    // 用于确定要统计哪些字段
    private final AggregateCall call;
    // 计数器,记录符合条件的行数
    // 使用 long 类型以支持大数量级
    long cnt;

    /**
     * 构造函数,初始化 COUNT 累加器
     *
     * @param call 聚合函数调用对象,包含参数列表
     *
     * 初始化:
     * - 保存聚合调用对象
     * - 将计数器初始化为 0
     */
    CountAccumulator(AggregateCall call) {
      // 保存聚合调用对象,用于后续访问参数列表
      this.call = call;
      // 初始化计数器为 0
      cnt = 0;
    }

    /**
     * 处理输入行,更新计数
     *
     * @param row 输入数据行
     *
     * 处理逻辑:
     * 1. 检查所有参数字段是否都不为 null
     * 2. 如果都不为 null,则计数器加 1
     * 3. 如果有任何一个参数为 null,则不计数
     *
     * 注意:
     * - COUNT(*) 的参数列表为空,此时所有行都计数
     * - COUNT(column) 只统计该列不为 null 的行
     * - 多参数时,所有参数都不为 null 才计数
     */
    @Override public void send(Row row) {
      // 标记是否所有参数都不为 null
      boolean notNull = true;
      // 遍历所有参数字段的位置
      for (Integer i : call.getArgList()) {
        // 检查该位置的值是否为 null
        if (row.getObject(i) == null) {
          // 如果有任何一个参数为 null,标记为 false 并跳出循环
          notNull = false;
          break;
        }
      }
      // 如果所有参数都不为 null,则计数器加 1
      if (notNull) {
        cnt++;
      }
    }

    /**
     * 返回聚合结果
     *
     * @return 计数结果(long 类型)
     *
     * 返回值:
     * - 返回 cnt 的值,即符合条件的行数
     * - 如果没有符合条件的行,返回 0
     */
    @Override public Object end() {
      // 返回计数值
      return cnt;
    }
  }

  /** LITERAL_AGG 函数的累加器实现
   *
   * 这个类实现了字面量聚合函数的累加逻辑
   * LITERAL_AGG 是 Calcite 的内部函数,用于返回常量值
   *
   * 特性:
   * - 不处理输入数据,总是返回相同的值
   * - 值在构造时确定,后续不会改变
   * - 支持任意类型的常量值
   *
   * 使用场景:
   * - 用于优化某些特殊的聚合查询
   * - 在查询重写过程中可能生成这种聚合
   * - 例如: SELECT 1 FROM t GROUP BY x 会生成 LITERAL_AGG(1)
   */
  private static class LiteralAccumulator implements Accumulator {
    // 要返回的常量值,可能为 null
    // 使用 @Nullable 注解标记可能为 null
    private final @Nullable Object value;

    /**
     * 构造函数,初始化字面量累加器
     *
     * @param value 要返回的常量值,可以为 null
     *
     * 初始化:
     * - 保存要返回的常量值
     * - 这个值在累加器的整个生命周期中不会改变
     */
    LiteralAccumulator(@Nullable Object value) {
      // 保存常量值
      this.value = value;
    }

    /**
     * 处理输入行(不执行任何操作)
     *
     * @param row 输入数据行(被忽略)
     *
     * 注意:
     * - 这个方法不执行任何操作
     * - 字面量聚合不需要处理输入数据
     * - 无论输入什么,都返回相同的值
     */
    @Override public void send(Row row) {
      // 不执行任何操作,字面量聚合不需要处理输入
    }

    /**
     * 返回聚合结果(常量值)
     *
     * @return 保存的常量值,可能为 null
     *
     * 返回值:
     * - 总是返回构造时传入的值
     * - 无论处理了多少行数据,结果都相同
     */
    @Override public @Nullable Object end() {
      // 返回保存的常量值
      return value;
    }
  }

  /**
   * 累加器工厂接口,用于创建累加器实例
   *
   * 这个接口扩展了 Java 的 Supplier 接口,是一个函数式接口
   * 工厂模式允许为每个聚合函数创建新的累加器实例
   *
   * 为什么需要工厂:
   * - 每个分组需要独立的累加器实例
   * - 累加器状态不能在多个分组间共享
   * - 工厂可以延迟创建累加器,节省资源
   *
   * 使用方式:
   * - 在 Grouping 中,为每个新的分组键创建新的累加器
   * - 工厂的 get() 方法被调用时创建新的累加器实例
   *
   * 实现类:
   * - CountAccumulator 的 lambda 表达式
   * - UdaAccumulatorFactory: 用户定义聚合函数工厂
   * - ScalarAccumulatorDef: 标量代码累加器工厂
   * - LiteralAccumulator 的 lambda 表达式
   */
  /** 创建 {@link Accumulator} 的工厂接口 */
  private interface AccumulatorFactory extends Supplier<Accumulator> {
  }

  /**
 * 基于标量代码片段的累加器工厂定义
 *
 * 这个类为使用生成的 Java 代码实现的聚合函数提供累加器工厂
 * 标量(Scalar)是通过 Janino 编译器在运行时编译的 Java 代码片段
 *
 * 工作原理:
 * 1. 在 getAccumulator 方法中生成 Java 代码
 * 2. 使用 Janino 编译器将代码编译为可执行的标量对象
 * 3. 标量对象包含执行聚合操作的编译后代码
 * 4. 每次调用 get() 创建新的累加器实例,每个实例有自己的状态
 *
 * 标量类型:
 * - initScalar: 初始化标量(通常为 null,不需要特殊初始化)
 * - addScalar: 添加标量,用于处理每行数据
 * - endScalar: 结束标量(通常为 null,直接返回累加器状态)
 *
 * 上下文:
 * - sendContext: 发送上下文,包含输入行和累加器状态
 * - endContext: 结束上下文,只包含累加器状态
 *
 * 使用场景:
 * - 复杂的聚合函数(如 AVG)
 * - 用户定义的聚合函数
 * - 需要代码生成的聚合函数
 */
/** 由 {@link Scalar} 代码片段驱动的累加器工厂 */
private static class ScalarAccumulatorDef implements AccumulatorFactory {
  // 初始化标量,用于初始化累加器状态
  // 通常为 null,因为大多数聚合函数不需要特殊初始化
  final Scalar initScalar;
  // 添加标量,用于处理每行输入数据
  // 包含编译后的 Java 代码,实现 add 操作
  final Scalar addScalar;
  // 结束标量,用于计算最终结果
  // 通常为 null,直接返回累加器状态
  final Scalar endScalar;
  // 发送上下文,用于执行 add 标量
  // 包含输入行数据和累加器状态
  final Context sendContext;
  // 结束上下文,用于执行 end 标量
  final Context endContext;
  // 输入行的长度(字段数)
  // 用于确定 sendContext.values 数组的大小
  final int rowLength;
  // 累加器状态的长度(状态变量数)
  // 用于确定累加器数组的大小
  final int accumulatorLength;

  /**
   * 构造函数,初始化标量累加器工厂定义
   *
   * @param initScalar 初始化标量(通常为 null)
   * @param addScalar 添加标量,包含处理每行数据的代码
   * @param endScalar 结束标量(通常为 null)
   * @param rowLength 输入行的字段数
   * @param accumulatorLength 累加器状态的变量数
   * @param root 根数据上下文,提供运行时环境信息
   *
   * 初始化过程:
   * 1. 保存所有标量和参数
   * 2. 创建发送上下文,包含输入行和累加器状态的数组
   * 3. 创建结束上下文,只包含累加器状态的数组
   */
  private ScalarAccumulatorDef(Scalar initScalar, Scalar addScalar,
      Scalar endScalar, int rowLength, int accumulatorLength,
      DataContext root) {
    // 保存初始化标量
    this.initScalar = initScalar;
    // 保存添加标量
    this.addScalar = addScalar;
    // 保存结束标量
    this.endScalar = endScalar;
    // 保存累加器状态长度
    this.accumulatorLength = accumulatorLength;
    // 保存输入行长度
    this.rowLength = rowLength;
    // 创建发送上下文,使用根数据上下文
    this.sendContext = new Context(root);
    // 初始化发送上下文的值数组,包含输入行和累加器状态
    // 数组大小 = 输入行长度 + 累加器状态长度
    this.sendContext.values = new Object[rowLength + accumulatorLength];
    // 创建结束上下文,使用根数据上下文
    this.endContext = new Context(root);
    // 初始化结束上下文的值数组,只包含累加器状态
    this.endContext.values = new Object[accumulatorLength];
  }

  /**
   * 创建新的累加器实例
   *
   * @return 新的标量累加器实例
   *
   * 创建过程:
   * 1. 创建新的累加器状态数组,初始化为默认值(null)
   * 2. 创建 ScalarAccumulator 对象,传入工厂定义和状态数组
   * 3. 每个累加器实例有独立的状态,不会相互影响
   */
  @Override public Accumulator get() {
    // 创建新的标量累加器实例
    // 传入工厂定义和新的空状态数组
    return new ScalarAccumulator(this, new Object[accumulatorLength]);
  }
}

  /**
 * 基于标量代码片段的累加器实现
 *
 * 这个类使用编译后的 Java 代码执行聚合操作
 * 标量代码是在运行时通过 Janino 编译器生成的
 *
 * 工作流程:
 * 1. send(): 将输入行复制到上下文,执行 add 标量代码
 * 2. add 标量代码更新累加器状态数组
 * 3. end(): 将累加器状态复制到上下文,执行 end 标量代码
 * 4. 返回最终结果
 *
 * 性能特点:
 * - 代码在运行时编译,首次调用较慢
 * - 编译后的代码执行效率高
 * - 适合复杂的聚合函数
 *
 * 内存管理:
 * - values 数组存储累加器状态
 * - sendContext.values 临时存储输入行和状态
 * - endContext.values 临时存储状态用于计算结果
 */
/** 由 {@link Scalar} 代码片段驱动的累加器 */
private static class ScalarAccumulator implements Accumulator {
  // 工厂定义,包含所有标量和上下文信息
  // 用于访问 addScalar、endScalar 和上下文
  final ScalarAccumulatorDef def;
  // 累加器状态数组,存储聚合计算的中间状态
  // 数组长度由 accumulatorLength 决定
  final Object[] values;

  /**
   * 构造函数,初始化标量累加器
   *
   * @param def 工厂定义,包含标量和上下文
   * @param values 累加器状态数组(通常为空数组)
   *
   * 初始化:
   * - 保存工厂定义,用于访问标量和上下文
   * - 保存状态数组,用于存储聚合状态
   */
  private ScalarAccumulator(ScalarAccumulatorDef def, Object[] values) {
    // 保存工厂定义
    this.def = def;
    // 保存状态数组
    this.values = values;
  }

  /**
   * 处理输入行,更新累加器状态
   *
   * @param row 输入数据行
   *
   * 处理流程:
   * 1. 获取发送上下文的值数组
   * 2. 将输入行的值复制到数组前面
   * 3. 将累加器状态复制到数组后面
   * 4. 执行 add 标量代码,更新累加器状态
   *
   * 数据布局:
   * sendContext.values = [输入行字段..., 累加器状态...]
   *
   * 注意:
   * - add 标量代码会修改 this.values 数组
   * - 每次调用都会更新累加器状态
   */
  @Override public void send(Row row) {
    // 获取发送上下文的值数组,用于临时存储数据
    // requireNonNull 确保数组不为 null
    @Nullable Object[] sendValues =
        requireNonNull(def.sendContext.values, "def.sendContext.values");
    // 将输入行的值复制到 sendValues 数组的前面部分
    // 从位置 0 开始,复制 rowLength 个元素
    System.arraycopy(row.getValues(), 0, sendValues, 0,
        def.rowLength);
    // 将累加器状态复制到 sendValues 数组的后面部分
    // 从位置 rowLength 开始,复制所有状态值
    System.arraycopy(this.values, 0, sendValues, def.rowLength,
        this.values.length);
    // 执行 add 标量代码,更新累加器状态
    // 传入发送上下文和状态数组(用于接收更新后的状态)
    def.addScalar.execute(def.sendContext, this.values);
  }

  /**
   * 返回聚合结果
   *
   * @return 聚合结果,可能为 null
   *
   * 处理流程:
   * 1. 获取结束上下文
   * 2. 将累加器状态复制到结束上下文的值数组
   * 3. 执行 end 标量代码,计算最终结果
   * 4. 返回计算结果
   *
   * 注意:
   * - end 标量代码可能为 null,此时直接返回状态
   * - 结果可能为 null,取决于聚合函数的实现
   */
  @Override public @Nullable Object end() {
    // 获取结束上下文,用于计算最终结果
    Context endContext = requireNonNull(def.endContext, "def.endContext");
    // 获取结束上下文的值数组
    @Nullable Object[] values = requireNonNull(endContext.values, "endContext.values");
    // 将累加器状态复制到结束上下文的值数组
    System.arraycopy(this.values, 0, values, 0, this.values.length);
    // 执行 end 标量代码,计算并返回最终结果
    return def.endScalar.execute(endContext);
  }
}

  /**
 * 内部类,用于跟踪分组
 *
 * 这个类管理一个特定的分组方式(GROUPING SET 中的一个)
 * 每个分组方式对应一组分组键,维护该分组下所有累加器的状态
 *
 * 核心功能:
 * 1. 根据分组键将数据行分发到对应的累加器列表
 * 2. 为新的分组键创建新的累加器列表
 * 3. 输出该分组的所有聚合结果
 *
 * 数据结构:
 * - grouping: 分组键的位集合,指定哪些字段用于分组
 * - accumulators: 映射表,分组键 -> 累加器列表
 *
 * GROUPING SETS 支持:
 * - 每个 Grouping 对象代表一个分组集合
 * - 例如: GROUPING SETS ((a,b), (a)) 会创建两个 Grouping 对象
 * - 同一行数据会被发送到所有分组中
 */
private class Grouping {
  // 分组键的位集合,指定哪些字段用于分组
  // 例如: {0, 2} 表示使用第 0 和第 2 个字段作为分组键
  private final ImmutableBitSet grouping;
  // 累加器映射表,键是分组键(由分组字段值构成的 Row),值是累加器列表
  // 每个分组键对应一组累加器,每个累加器对应一个聚合函数
  private final Map<Row, AccumulatorList> accumulators = new HashMap<>();

  /**
   * 构造函数,初始化分组对象
   *
   * @param grouping 分组键的位集合,指定哪些字段用于分组
   *
   * 初始化:
   * - 保存分组键的位集合
   * - 创建空的累加器映射表
   */
  private Grouping(ImmutableBitSet grouping) {
    // 保存分组键的位集合
    this.grouping = grouping;
  }

  /**
   * 处理输入行,分发给对应的累加器
   *
   * @param row 输入数据行
   *
   * 处理流程:
   * 1. 从输入行中提取分组字段的值,构建分组键
   * 2. 检查该分组键是否已存在累加器列表
   * 3. 如果不存在,创建新的累加器列表
   * 4. 将输入行发送到对应的累加器列表
   *
   * 分组键构建:
   * - 只包含分组字段的值
   * - 顺序与 grouping 中的字段顺序一致
   * - 用于在 HashMap 中查找对应的累加器列表
   *
   * 累加器列表创建:
   * - 为每个聚合函数创建一个累加器
   * - 使用工厂模式创建,确保每个累加器独立
   * - 累加器列表的顺序与聚合函数的顺序一致
   */
  public void send(Row row) {
    // TODO: 修复这个行的大小问题
    // 创建行构建器,大小等于分组字段数
    RowBuilder builder = Row.newBuilder(grouping.cardinality());
    // 遍历分组字段的位置
    int j = 0;
    for (Integer i : grouping) {
      // 从输入行中提取分组字段的值,添加到分组键中
      builder.set(j++, row.getObject(i));
    }
    // 构建分组键,用于查找累加器列表
    Row key = builder.build();

    // 检查该分组键是否已存在累加器列表
    if (!accumulators.containsKey(key)) {
      // 不存在,创建新的累加器列表
      AccumulatorList list = new AccumulatorList();
      // 为每个聚合函数创建累加器并添加到列表
      for (AccumulatorFactory factory : accumulatorFactories) {
        // 调用工厂的 get() 方法创建新的累加器实例
        list.add(factory.get());
      }
      // 将累加器列表存入映射表
      accumulators.put(key, list);
    }

    // 将输入行发送到对应的累加器列表
    // 累加器列表会调用每个累加器的 send 方法
    accumulators.get(key).send(row);
  }

  /**
   * 输出该分组的所有聚合结果
   *
   * @param sink 数据接收器,用于发送结果到下游
   * @throws InterruptedException 如果线程被中断
   *
   * 处理流程:
   * 1. 遍历所有分组键及其累加器列表
   * 2. 为每个分组键构建输出行
   * 3. 设置输出行的分组字段值
   * 4. 调用累加器列表获取聚合结果
   * 5. 将输出行发送到下游
   *
   * 输出行构建:
   * - 长度 = 所有分组字段数 + 聚合函数数
   * - 分组字段位置:根据 unionGroups 确定
   * - 聚合结果位置:在分组字段之后
   *
   * 分组字段处理:
   * - 如果字段在当前分组中,设置对应的值
   * - 如果字段不在当前分组中,设置为 null
   * - 这是 GROUPING SETS 的特性,允许部分分组
   */
  public void end(Sink sink) throws InterruptedException {
    // 遍历所有分组键及其累加器列表
    for (Map.Entry<Row, AccumulatorList> e : accumulators.entrySet()) {
      // 获取分组键
      final Row key = e.getKey();
      // 获取累加器列表
      final AccumulatorList list = e.getValue();

      // 创建输出行构建器,大小等于输出行长度
      RowBuilder rb = Row.newBuilder(outputRowLength);
      // 当前输出行的字段索引
      int index = 0;
      // 遍历所有分组字段位置(unionGroups)
      for (Integer groupPos : unionGroups) {
        // 检查该字段是否在当前分组中
        if (grouping.get(groupPos)) {
          // 在当前分组中,设置对应的分组键值
          // 注意:这里使用 index 而不是 groupPos,因为 key 只包含当前分组的字段
          rb.set(index, key.getObject(index));
        }
        // 注意:当字段不在当前分组中时,应该设置为 null
        // TODO: 需要添加 else 分支设置为 false/null

        // 移动到下一个字段位置
        index++;
      }

      // 调用累加器列表的 end 方法,将聚合结果添加到输出行
      list.end(rb);

      // 将输出行发送到下游
      sink.send(rb.build());
    }
  }
}

  /**
 * 分组过程中使用的累加器列表
 *
 * 这个类继承自 ArrayList<Accumulator>,用于管理一个分组下的所有累加器
 * 每个累加器对应一个聚合函数,按顺序存储
 *
 * 核心功能:
 * 1. 将输入行发送到所有累加器进行更新
 * 2. 从所有累加器获取最终结果并添加到输出行
 *
 * 数据结构:
 * - 继承自 ArrayList,使用动态数组存储累加器
 * - 累加器的顺序与聚合函数的顺序一致
 *
 * 使用场景:
 * - 每个 Grouping 对象为每个分组键创建一个 AccumulatorList
 * - AccumulatorList 包含该分组所有聚合函数的累加器
 * - 当处理新行时,所有累加器都会被更新
 * - 当输出结果时,所有累加器的结果都会被收集
 */
/**
 * 分组过程中使用的累加器列表
 */
private static class AccumulatorList extends ArrayList<Accumulator> {
  /**
   * 将输入行发送到所有累加器
   *
   * @param row 输入数据行
   *
   * 处理流程:
   * 1. 遍历列表中的所有累加器
   * 2. 调用每个累加器的 send 方法
   * 3. 每个累加器根据其聚合函数逻辑更新内部状态
   *
   * 注意:
   * - 所有累加器都会处理同一行数据
   * - 不同累加器可能关注不同的字段
   * - 累加器的顺序与聚合函数的顺序一致
   */
  public void send(Row row) {
    // 遍历列表中的所有累加器
    for (Accumulator a : this) {
      // 调用累加器的 send 方法,更新其状态
      a.send(row);
    }
  }

  /**
   * 从所有累加器获取最终结果并添加到输出行
   *
   * @param r 输出行构建器,用于构建输出行
   *
   * 处理流程:
   * 1. 计算输出行中聚合结果的起始位置
   * 2. 遍历所有累加器
   * 3. 调用每个累加器的 end 方法获取结果
   * 4. 将结果添加到输出行的对应位置
   *
   * 输出行结构:
   * [分组字段1, 分组字段2, ..., 聚合结果1, 聚合结果2, ...]
   *
   * 位置计算:
   * - 聚合结果从位置 (r.size() - size()) 开始
   * - 每个聚合结果占据一个位置
   */
  public void end(RowBuilder r) {
    // 遍历所有累加器,同时计算输出行中的位置
    // accIndex: 累加器索引
    // rowIndex: 输出行中的字段索引,从聚合结果起始位置开始
    for (int accIndex = 0, rowIndex = r.size() - size();
        rowIndex < r.size(); rowIndex++, accIndex++) {
      // 调用累加器的 end 方法获取结果
      // 将结果设置到输出行的对应位置
      r.set(rowIndex, get(accIndex).end());
    }
  }
}

/**
 * 定义聚合函数的实现接口
 *
 * 这个接口定义了所有累加器必须实现的方法
 * 累加器是聚合函数的核心组件,负责增量计算聚合值
 *
 * 核心方法:
 * 1. send(): 处理输入行,更新累加器状态
 * 2. end(): 返回聚合结果
 *
 * 设计模式:
 * - 累加器模式:增量计算,避免存储所有数据
 * - 策略模式:不同的聚合函数有不同的实现
 *
 * 实现类:
 * - CountAccumulator: COUNT 函数
 * - LiteralAccumulator: 字面量聚合
 * - ScalarAccumulator: 标量代码实现
 * - UdaAccumulator: 用户定义聚合函数
 * - FilterAccumulator: 过滤包装器
 *
 * 使用方式:
 * 1. 创建累加器实例(通过工厂)
 * 2. 对每行数据调用 send 方法
 * 3. 所有数据处理完成后调用 end 方法获取结果
 */
/**
 * 定义函数实现,如 {@code count()} 和 {@code sum()}
 */
private interface Accumulator {
  /**
   * 处理输入行,更新累加器状态
   *
   * @param row 输入数据行
   *
   * 功能:
   * - 根据聚合函数的逻辑更新内部状态
   * - 不同聚合函数有不同的更新逻辑
   *
   * 实现示例:
   * - COUNT: 如果参数不为 null,计数器加 1
   * - SUM: 将参数值累加到累加器
   * - MAX: 如果参数值大于当前值,更新当前值
   * - MIN: 如果参数值小于当前值,更新当前值
   */
  void send(Row row);

  /**
   * 返回聚合结果
   *
   * @return 聚合结果,可能为 null
   *
   * 功能:
   * - 基于累加器的内部状态计算最终结果
   * - 不同聚合函数有不同的计算逻辑
   *
   * 返回值:
   * - COUNT: 计数值
   * - SUM: 累加和
   * - MAX: 最大值
   * - MIN: 最小值
   * - AVG: 平均值(需要额外计数)
   *
   * 注意:
   * - 如果没有输入数据,可能返回 null
   * - SUM0 例外,空集合返回 0
   */
  @Nullable Object end();
}

  /**
 * INTEGER 类型值的 SUM 函数实现,作为用户定义聚合函数
 *
 * 这个类实现了对整数类型值的求和聚合函数
 * 遵循用户定义聚合函数的接口规范
 *
 * 用户定义聚合函数接口:
 * - init(): 初始化累加器
 * - add(accumulator, value): 添加新值到累加器
 * - merge(accumulator0, accumulator1): 合并两个累加器(用于分布式计算)
 * - result(accumulator): 返回最终结果
 *
 * 使用场景:
 * - SQL: SUM(integer_column)
 * - 用于整数列的求和计算
 *
 * 性能特点:
 * - 使用基本类型 int,避免装箱拆箱
 * - 简单的加法运算,性能高效
 *
 * 注意:
 * - 没有溢出检查,大数可能溢出
 * - 空集合返回 0(SUM0)或 null(SUM),由调用方控制
 */
/** 作为用户定义聚合函数实现的 {@code SUM} 函数,用于 INTEGER 值 */
public static class IntSum {
  /**
   * 构造函数
   *
   * 注意:
   * - 不需要初始化任何状态
   * - init() 方法负责初始化累加器
   */
  public IntSum() {
  }

  /**
   * 初始化累加器
   *
   * @return 初始累加器值(0)
   *
   * 功能:
   * - 返回求和的初始值
   * - 对于求和函数,初始值应该是 0
   */
  public int init() {
    return 0;
  }

  /**
   * 添加新值到累加器
   *
   * @param accumulator 当前累加器值
   * @param v 要添加的新值
   * @return 更新后的累加器值
   *
   * 功能:
   * - 将新值加到累加器上
   * - 返回新的累加器值
   */
  public int add(int accumulator, int v) {
    return accumulator + v;
  }

  /**
   * 合并两个累加器
   *
   * @param accumulator0 第一个累加器值
   * @param accumulator1 第二个累加器值
   * @return 合并后的累加器值
   *
   * 功能:
   * - 用于分布式计算或并行聚合
   * - 将两个部分的和合并为一个总和
   * - 例如: map-reduce 场景中的 reduce 阶段
   */
  public int merge(int accumulator0, int accumulator1) {
    return accumulator0 + accumulator1;
  }

  /**
   * 返回最终结果
   *
   * @param accumulator 累加器值
   * @return 聚合结果
   *
   * 功能:
   * - 对于求和函数,结果就是累加器值本身
   * - 其他聚合函数可能需要额外计算(如 AVG 需要除以计数)
   */
  public int result(int accumulator) {
    return accumulator;
  }
}

/**
 * BIGINT 类型值的 SUM 函数实现,作为用户定义聚合函数
 *
 * 这个类实现了对长整数类型值的求和聚合函数
 * 遵循用户定义聚合函数的接口规范
 *
 * 用户定义聚合函数接口:
 * - init(): 初始化累加器
 * - add(accumulator, value): 添加新值到累加器
 * - merge(accumulator0, accumulator1): 合并两个累加器
 * - result(accumulator): 返回最终结果
 *
 * 使用场景:
 * - SQL: SUM(bigint_column)
 * - 用于长整数列的求和计算
 * - 适用于大数求和,范围比 INTEGER 更大
 *
 * 性能特点:
 * - 使用基本类型 long,避免装箱拆箱
 * - 简单的加法运算,性能高效
 *
 * 注意:
 * - 没有溢出检查,极大数可能溢出
 * - 空集合返回 0(SUM0)或 null(SUM),由调用方控制
 */
/** 作为用户定义聚合函数实现的 {@code SUM} 函数,用于 BIGINT 值 */
public static class LongSum {
  /**
   * 构造函数
   *
   * 注意:
   * - 不需要初始化任何状态
   * - init() 方法负责初始化累加器
   */
  public LongSum() {
  }

  /**
   * 初始化累加器
   *
   * @return 初始累加器值(0L)
   *
   * 功能:
   * - 返回求和的初始值
   * - 对于求和函数,初始值应该是 0
   */
  public long init() {
    return 0L;
  }

  /**
   * 添加新值到累加器
   *
   * @param accumulator 当前累加器值
   * @param v 要添加的新值
   * @return 更新后的累加器值
   *
   * 功能:
   * - 将新值加到累加器上
   * - 返回新的累加器值
   */
  public long add(long accumulator, long v) {
    return accumulator + v;
  }

  /**
   * 合并两个累加器
   *
   * @param accumulator0 第一个累加器值
   * @param accumulator1 第二个累加器值
   * @return 合并后的累加器值
   *
   * 功能:
   * - 用于分布式计算或并行聚合
   * - 将两个部分的和合并为一个总和
   */
  public long merge(long accumulator0, long accumulator1) {
    return accumulator0 + accumulator1;
  }

  /**
   * 返回最终结果
   *
   * @param 累加器值
   * @return 聚合结果
   *
   * 功能:
   * - 对于求和函数,结果就是累加器值本身
   */
  public long result(long accumulator) {
    return accumulator;
  }
}

/**
 * DOUBLE 类型值的 SUM 函数实现,作为用户定义聚合函数
 *
 * 这个类实现了对双精度浮点数类型值的求和聚合函数
 * 遵循用户定义聚合函数的接口规范
 *
 * 用户定义聚合函数接口:
 * - init(): 初始化累加器
 * - add(accumulator, value): 添加新值到累加器
 * - merge(accumulator0, accumulator1): 合并两个累加器
 * - result(accumulator): 返回最终结果
 *
 * 使用场景:
 * - SQL: SUM(double_column)
 * - 用于浮点数列的求和计算
 * - 适用于需要高精度浮点运算的场景
 *
 * 性能特点:
 * - 使用基本类型 double,避免装箱拆箱
 * - 简单的加法运算,性能高效
 *
 * 注意:
 * - 浮点数运算可能有精度损失
 * - 没有溢出检查,极大数可能溢出为 Infinity
 * - 空集合返回 0(SUM0)或 null(SUM),由调用方控制
 */
/** 作为用户定义聚合函数实现的 {@code SUM} 函数,用于 DOUBLE 值 */
public static class DoubleSum {
  /**
   * 构造函数
   *
   * 注意:
   * - 不需要初始化任何状态
   * - init() 方法负责初始化累加器
   */
  public DoubleSum() {
  }

  /**
   * 初始化累加器
   *
   * @return 初始累加器值(0.0)
   *
   * 功能:
   * - 返回求和的初始值
   * - 对于求和函数,初始值应该是 0
   */
  public double init() {
    return 0D;
  }

  /**
   * 添加新值到累加器
   *
   * @param accumulator 当前累加器值
   * @param v 要添加的新值
   * @return 更新后的累加器值
   *
   * 功能:
   * - 将新值加到累加器上
   * - 返回新的累加器值
   */
  public double add(double accumulator, double v) {
    return accumulator + v;
  }

  /**
   * 合并两个累加器
   *
   * @param accumulator0 第一个累加器值
   * @param accumulator1 第二个累加器值
   * @return 合并后的累加器值
   *
   * 功能:
   * - 用于分布式计算或并行聚合
   * - 将两个部分的和合并为一个总和
   */
  public double merge(double accumulator0, double accumulator1) {
    return accumulator0 + accumulator1;
  }

  /**
   * 返回最终结果
   *
   * @param 累加器值
   * @return 聚合结果
   *
   * 功能:
   * - 对于求和函数,结果就是累加器值本身
   */
  public double result(double accumulator) {
    return accumulator;
  }
}

  /**
 * BigDecimal 类型值的 SUM 函数实现,作为用户定义聚合函数
 *
 * 这个类实现了对高精度十进制类型值的求和聚合函数
 * 遵循用户定义聚合函数的接口规范
 *
 * 用户定义聚合函数接口:
 * - init(): 初始化累加器
 * - add(accumulator, value): 添加新值到累加器
 * - merge(accumulator0, accumulator1): 合并两个累加器
 * - result(accumulator): 返回最终结果
 *
 * 使用场景:
 * - SQL: SUM(decimal_column)
 * - 用于需要精确计算的财务、科学计算等场景
 * - 适用于不能容忍浮点数精度损失的场景
 *
 * 性能特点:
 * - 使用 BigDecimal 类,保证精度
 * - 运算性能比基本类型慢,但精度高
 * - 不会出现浮点数精度损失
 *
 * 注意:
 * - 不会溢出,因为 BigDecimal 可以表示任意精度的数
 * - 性能比基本类型慢
 * - 空集合返回 0(SUM0)或 null(SUM),由调用方控制
 */
/** 作为用户定义聚合函数实现的 {@code SUM} 函数,用于 BigDecimal 值 */
public static class BigDecimalSum {
  /**
   * 构造函数
   *
   * 注意:
   * - 不需要初始化任何状态
   * - init() 方法负责初始化累加器
   */
  public BigDecimalSum(){
  }

  /**
   * 初始化累加器
   *
   * @return 初始累加器值(BigDecimal("0"))
   *
   * 功能:
   * - 返回求和的初始值
   * - 使用字符串 "0" 创建 BigDecimal,避免浮点数精度问题
   */
  public BigDecimal init() {
    return new BigDecimal("0");
  }

  /**
   * 添加新值到累加器
   *
   * @param accumulator 当前累加器值
   * @param v 要添加的新值
   * @return 更新后的累加器值
   *
   * 功能:
   * - 使用 BigDecimal 的 add 方法进行精确加法
   * - 不会出现浮点数精度损失
   */
  public BigDecimal add(BigDecimal accumulator, BigDecimal v) {
    return accumulator.add(v);
  }

  /**
   * 合并两个累加器
   *
   * @param accumulator0 第一个累加器值
   * @param accumulator01 第二个累加器值(参数名拼写错误,应为 accumulator1)
   * @return 合并后的累加器值
   *
   * 功能:
   * - 用于分布式计算或并行聚合
   * - 将两个部分的和合并为一个总和
   * - 直接调用 add 方法
   */
  public BigDecimal merge(BigDecimal accumulator0, BigDecimal accumulator01) {
    return add(accumulator0, accumulator01);
  }

  /**
   * 返回最终结果
   *
   * @param 累加器值
   * @return 聚合结果
   *
   * 功能:
   * - 对于求和函数,结果就是累加器值本身
   */
  public BigDecimal result(BigDecimal accumulator) {
    return accumulator;
  }
}

  /**
 * 数值类型比较聚合方法的通用实现,作为用户定义聚合函数
 *
 * 这个类提供了 MAX 和 MIN 聚合函数的通用实现
 * 使用泛型支持多种数值类型
 *
 * 设计模式:
 * - 策略模式:通过传入不同的比较函数实现 MAX 或 MIN
 * - 模板方法:定义了聚合函数的通用流程
 *
 * 用户定义聚合函数接口:
 * - init(): 初始化累加器
 * - add(accumulator, value): 添加新值到累加器
 * - merge(accumulator0, accumulator1): 合并两个累加器
 * - result(accumulator): 返回最终结果
 *
 * 泛型参数:
 * - T: 数值类型(Integer, Long, Float, Double, BigDecimal 等)
 *
 * 使用场景:
 * - MAX 函数: initialValue = 最小值, comparisonFunction = Math::max
 * - MIN 函数: initialValue = 最大值, comparisonFunction = Math::min
 *
 * 性能特点:
 * - 使用函数式接口,避免重复代码
 * - 支持所有实现了比较操作的数值类型
 */
/** 数值类型比较聚合方法的通用实现,作为用户定义聚合函数
 *
 * @param <T> 数值类型
 */
public static class NumericComparison<T> {
  // 初始值,根据是 MAX 还是 MIN 而定
  // MAX: 初始值为该类型的最小值
  // MIN: 初始值为该类型的最大值
  private final T initialValue;
  // 比较函数,用于比较两个值并返回结果
  // MAX: Math::max
  // MIN: Math::min
  private final BiFunction<T, T, T> comparisonFunction;

  /**
   * 构造函数,初始化比较聚合函数
   *
   * @param initialValue 初始值
   * @param comparisonFunction 比较函数
   *
   * 参数说明:
   * - initialValue: 对于 MAX 应该是最小值,对于 MIN 应该是最大值
   * - comparisonFunction: 接受两个参数,返回比较结果
   *
   * 使用示例:
   * - MAX: new NumericComparison<>(Integer.MIN_VALUE, Math::max)
   * - MIN: new NumericComparison<>(Integer.MAX_VALUE, Math::min)
   */
  public NumericComparison(T initialValue, BiFunction<T, T, T> comparisonFunction) {
    // 保存初始值
    this.initialValue = initialValue;
    // 保存比较函数
    this.comparisonFunction = comparisonFunction;
  }

  /**
   * 初始化累加器
   *
   * @return 初始累加器值
   *
   * 功能:
   * - 返回初始值
   * - MAX 返回最小值,MIN 返回最大值
   */
  public T init() {
    return this.initialValue;
  }

  /**
   * 添加新值到累加器
   *
   * @param accumulator 当前累加器值
   * @param value 要比较的新值
   * @return 比较后的新累加器值
   *
   * 功能:
   * - 使用比较函数比较累加器值和新值
   * - 返回较大或较小的值(取决于 comparisonFunction)
   */
  public T add(T accumulator, T value) {
    return this.comparisonFunction.apply(accumulator, value);
  }

  /**
   * 合并两个累加器
   *
   * @param accumulator0 第一个累加器值
   * @param accumulator1 第二个累加器值
   * @return 合并后的累加器值
   *
   * 功能:
   * - 用于分布式计算或并行聚合
   * - 比较两个累加器的值,返回较大或较小的值
   */
  public T merge(T accumulator0, T accumulator1) {
    return add(accumulator0, accumulator1);
  }

  /**
   * 返回最终结果
   *
   * @param 累加器值
   * @return 聚合结果
   *
   * 功能:
   * - 对于比较函数,结果就是累加器值本身
   */
  public T result(T accumulator) {
    return accumulator;
  }
}

  /**
 * MIN 函数的整数实现,计算整数值的最小值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对整数类型值的最小值计算
 *
 * 使用场景:
 * - SQL: MIN(integer_column)
 * - 用于查找整数列的最小值
 *
 * 工作原理:
 * - 初始值: Integer.MAX_VALUE(整数最大值)
 * - 比较函数: Math::min(返回两个数中较小的)
 * - 第一条数据会替换初始值,后续数据会与当前最小值比较
 *
 * 性能特点:
 * - 使用基本类型 int,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MIN} 函数,计算 {@code integer} 值的最小值 */
public static class MinInt extends NumericComparison<Integer> {
  /**
   * 构造函数,初始化 MIN 整数聚合函数
   *
   * 初始化:
   * - 初始值: Integer.MAX_VALUE(确保任何实际值都会更小)
   * - 比较函数: Math::min(返回两个数中较小的)
   */
  public MinInt() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Integer.MAX_VALUE, Math::min);
  }
}

/**
 * MIN 函数的长整数实现,计算长整数值的最小值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对长整数类型值的最小值计算
 *
 * 使用场景:
 * - SQL: MIN(bigint_column)
 * - 用于查找长整数列的最小值
 * - 适用于大数范围的最小值计算
 *
 * 工作原理:
 * - 初始值: Long.MAX_VALUE(长整数最大值)
 * - 比较函数: Math::min(返回两个数中较小的)
 * - 第一条数据会替换初始值,后续数据会与当前最小值比较
 *
 * 性能特点:
 * - 使用基本类型 long,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MIN} 函数,计算 {@code long} 值的最小值 */
public static class MinLong extends NumericComparison<Long> {
  /**
   * 构造函数,初始化 MIN 长整数聚合函数
   *
   * 初始化:
   * - 初始值: Long.MAX_VALUE(确保任何实际值都会更小)
   * - 比较函数: Math::min(返回两个数中较小的)
   */
  public MinLong() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Long.MAX_VALUE, Math::min);
  }
}

/**
 * MIN 函数的浮点数实现,计算浮点数值的最小值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对浮点数类型值的最小值计算
 *
 * 使用场景:
 * - SQL: MIN(float_column)
 * - 用于查找浮点数列的最小值
 *
 * 工作原理:
 * - 初始值: Float.MAX_VALUE(浮点数最大值)
 * - 比较函数: Math::min(返回两个数中较小的)
 * - 第一条数据会替换初始值,后续数据会与当前最小值比较
 *
 * 性能特点:
 * - 使用基本类型 float,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 浮点数比较可能有精度问题
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MIN} 函数,计算 {@code float} 值的最小值 */
public static class MinFloat extends NumericComparison<Float> {
  /**
   * 构造函数,初始化 MIN 浮点数聚合函数
   *
   * 初始化:
   * - 初始值: Float.MAX_VALUE(确保任何实际值都会更小)
   * - 比较函数: Math::min(返回两个数中较小的)
   */
  public MinFloat() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Float.MAX_VALUE, Math::min);
  }
}

/**
 * MIN 函数的双精度浮点数实现,计算双精度浮点数值的最小值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对双精度浮点数类型值的最小值计算
 *
 * 使用场景:
 * - SQL: MIN(double_column) 或 MIN(real_column)
 * - 用于查找双精度浮点数列的最小值
 * - 适用于需要高精度浮点运算的场景
 *
 * 工作原理:
 * - 初始值: Double.MAX_VALUE(双精度浮点数最大值)
 * - 比较函数: Math::min(返回两个数中较小的)
 * - 第一条数据会替换初始值,后续数据会与当前最小值比较
 *
 * 性能特点:
 * - 使用基本类型 double,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 浮点数比较可能有精度问题
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MIN} 函数,计算 {@code double} 和 {@code real} 值的最小值 */
public static class MinDouble extends NumericComparison<Double> {
  /**
   * 构造函数,初始化 MIN 双精度浮点数聚合函数
   *
   * 初始化:
   * - 初始值: Double.MAX_VALUE(确保任何实际值都会更小)
   * - 比较函数: Math::min(返回两个数中较小的)
   */
  public MinDouble() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Double.MAX_VALUE, Math::min);
  }
}

  /**
 * MIN 函数的 BigDecimal 实现,计算高精度十进制数值的最小值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对 BigDecimal 类型值的最小值计算
 *
 * 使用场景:
 * - SQL: MIN(decimal_column)
 * - 用于查找高精度十进制数列的最小值
 * - 适用于需要精确计算的财务、科学计算等场景
 *
 * 工作原理:
 * - 初始值: BigDecimal(Double.MAX_VALUE)(确保任何实际值都会更小)
 * - 比较函数: BigDecimal::min(返回两个数中较小的)
 * - 第一条数据会替换初始值,后续数据会与当前最小值比较
 *
 * 性能特点:
 * - 使用 BigDecimal 类,保证精度
 * - 运算性能比基本类型慢,但精度高
 * - 不会出现浮点数精度损失
 *
 * 注意:
 * - 不会溢出,因为 BigDecimal 可以表示任意精度的数
 * - 性能比基本类型慢
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 */
/** 作为用户定义聚合函数实现的 {@code MIN} 函数,计算 {@code BigDecimal} 值的最小值 */
public static class MinBigDecimal extends NumericComparison<BigDecimal> {
  /**
   * 构造函数,初始化 MIN BigDecimal 聚合函数
   *
   * 初始化:
   * - 初始值: BigDecimal(Double.MAX_VALUE)(确保任何实际值都会更小)
   * - 比较函数: MinBigDecimal::min(自定义的 min 方法)
   */
  public MinBigDecimal() {
    // 调用父类构造函数,传入初始值和比较函数
    // 使用 Double.MAX_VALUE 创建 BigDecimal 作为初始值
    super(new BigDecimal(Double.MAX_VALUE), MinBigDecimal::min);
  }

  /**
   * 返回两个 BigDecimal 中较小的值
   *
   * @param a 第一个 BigDecimal 值
   * @param b 第二个 BigDecimal 值
   * @return 较小的值
   *
   * 功能:
   * - 使用 BigDecimal 的 min 方法
   * - 保证精度,不会出现浮点数精度损失
   */
  public static BigDecimal min(BigDecimal a, BigDecimal b) {
    return a.min(b);
  }
}

/**
 * MIN 函数的布尔值实现,计算布尔值的最小值,作为用户定义聚合函数
 *
 * 这个类实现了对布尔类型值的最小值计算
 *
 * 布尔值的最小值定义:
 * - FALSE < TRUE
 * - 因此 MIN 返回 FALSE(逻辑与:所有值都为 TRUE 时才返回 TRUE)
 *
 * 使用场景:
 * - SQL: MIN(boolean_column)
 * - 用于查找布尔列的最小值
 * - 语义上等同于逻辑与(AND)操作
 *
 * 工作原理:
 * - 初始值: Boolean.TRUE(确保任何实际值都会更小或相等)
 * - 比较函数: 逻辑与(AND)
 * - 只有所有值都为 TRUE 时,结果才为 TRUE
 *
 * 性能特点:
 * - 使用基本类型 boolean,避免装箱拆箱
 * - 简单的逻辑运算,性能高效
 *
 * 注意:
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 * - 语义上等同于 ALL() 函数
 */
/** 作为用户定义聚合函数实现的 {@code MIN} 函数,计算 {@code boolean} 值的最小值 */
public static class MinBoolean {
  /**
   * 构造函数
   *
   * 注意:
   * - 不需要初始化任何状态
   * - init() 方法负责初始化累加器
   */
  public MinBoolean() { }

  /**
   * 初始化累加器
   *
   * @return 初始累加器值(Boolean.TRUE)
   *
   * 功能:
   * - 返回初始值 TRUE
   * - 确保任何实际值(FALSE)都会更小
   */
  public Boolean init() {
    return Boolean.TRUE;
  }

  /**
   * 添加新值到累加器
   *
   * @param accumulator 当前累加器值
   * @param value 要比较的新值
   * @return 比较后的新累加器值
   *
   * 功能:
   * - 执行逻辑与(AND)操作
   * - 只有当 accumulator 和 value 都为 TRUE 时,结果才为 TRUE
   * - 这符合 MIN 的语义(FALSE < TRUE)
   */
  public Boolean add(Boolean accumulator, Boolean value) {
    return accumulator && value;
  }

  /**
   * 合并两个累加器
   *
   * @param accumulator0 第一个累加器值
   * @param accumulator1 第二个累加器值
   * @return 合并后的累加器值
   *
   * 功能:
   * - 用于分布式计算或并行聚合
   * - 执行逻辑与(AND)操作
   */
  public Boolean merge(Boolean accumulator0, Boolean accumulator1) {
    return add(accumulator0, accumulator1);
  }

  /**
   * 返回最终结果
   *
   * @param 累加器值
   * @return 聚合结果
   *
   * 功能:
   * - 对于 MIN 布尔值,结果就是累加器值本身
   */
  public Boolean result(Boolean accumulator) {
    return accumulator;
  }
}

  /**
 * MAX 函数的整数实现,计算整数值的最大值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对整数类型值的最大值计算
 *
 * 使用场景:
 * - SQL: MAX(integer_column)
 * - 用于查找整数列的最大值
 *
 * 工作原理:
 * - 初始值: Integer.MIN_VALUE(整数最小值)
 * - 比较函数: Math::max(返回两个数中较大的)
 * - 第一条数据会替换初始值,后续数据会与当前最大值比较
 *
 * 性能特点:
 * - 使用基本类型 int,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MAX} 函数,计算 {@code integer} 值的最大值 */
public static class MaxInt extends NumericComparison<Integer> {
  /**
   * 构造函数,初始化 MAX 整数聚合函数
   *
   * 初始化:
   * - 初始值: Integer.MIN_VALUE(确保任何实际值都会更大)
   * - 比较函数: Math::max(返回两个数中较大的)
   */
  public MaxInt() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Integer.MIN_VALUE, Math::max);
  }
}

/**
 * MAX 函数的长整数实现,计算长整数值的最大值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对长整数类型值的最大值计算
 *
 * 使用场景:
 * - SQL: MAX(bigint_column)
 * - 用于查找长整数列的最大值
 * - 适用于大数范围的最大值计算
 *
 * 工作原理:
 * - 初始值: Long.MIN_VALUE(长整数最小值)
 * - 比较函数: Math::max(返回两个数中较大的)
 * - 第一条数据会替换初始值,后续数据会与当前最大值比较
 *
 * 性能特点:
 * - 使用基本类型 long,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MAX} 函数,计算 {@code long} 值的最大值 */
public static class MaxLong extends NumericComparison<Long> {
  /**
   * 构造函数,初始化 MAX 长整数聚合函数
   *
   * 初始化:
   * - 初始值: Long.MIN_VALUE(确保任何实际值都会更大)
   * - 比较函数: Math::max(返回两个数中较大的)
   */
  public MaxLong() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Long.MIN_VALUE, Math::max);
  }
}

/**
 * MAX 函数的浮点数实现,计算浮点数值的最大值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对浮点数类型值的最大值计算
 *
 * 使用场景:
 * - SQL: MAX(float_column)
 * - 用于查找浮点数列的最大值
 *
 * 工作原理:
 * - 初始值: Float.MIN_VALUE(浮点数最小值)
 * - 比较函数: Math::max(返回两个数中较大的)
 * - 第一条数据会替换初始值,后续数据会与当前最大值比较
 *
 * 性能特点:
 * - 使用基本类型 float,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 浮点数比较可能有精度问题
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MAX} 函数,计算 {@code float} 值的最大值 */
public static class MaxFloat extends NumericComparison<Float> {
  /**
   * 构造函数,初始化 MAX 浮点数聚合函数
   *
   * 初始化:
   * - 初始值: Float.MIN_VALUE(确保任何实际值都会更大)
   * - 比较函数: Math::max(返回两个数中较大的)
   */
  public MaxFloat() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Float.MIN_VALUE, Math::max);
  }
}

/**
 * MAX 函数的双精度浮点数实现,计算双精度浮点数值的最大值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对双精度浮点数类型值的最大值计算
 *
 * 使用场景:
 * - SQL: MAX(double_column) 或 MAX(real_column)
 * - 用于查找双精度浮点数列的最大值
 * - 适用于需要高精度浮点运算的场景
 *
 * 工作原理:
 * - 初始值: Double.MIN_VALUE(双精度浮点数最小值)
 * - 比较函数: Math::max(返回两个数中较大的)
 * - 第一条数据会替换初始值,后续数据会与当前最大值比较
 *
 * 性能特点:
 * - 使用基本类型 double,避免装箱拆箱
 * - 简单的比较运算,性能高效
 *
 * 注意:
 * - 浮点数比较可能有精度问题
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 */
/** 作为用户定义聚合函数实现的 {@code MAX} 函数,计算 {@code double} 和 {@code real} 值的最大值 */
public static class MaxDouble extends NumericComparison<Double> {
  /**
   * 构造函数,初始化 MAX 双精度浮点数聚合函数
   *
   * 初始化:
   * - 初始值: Double.MIN_VALUE(确保任何实际值都会更大)
   * - 比较函数: Math::max(返回两个数中较大的)
   */
  public MaxDouble() {
    // 调用父类构造函数,传入初始值和比较函数
    super(Double.MIN_VALUE, Math::max);
  }
}

  /**
 * MAX 函数的 BigDecimal 实现,计算高精度十进制数值的最大值,作为用户定义聚合函数
 *
 * 这个类继承自 NumericComparison,实现了对 BigDecimal 类型值的最大值计算
 *
 * 使用场景:
 * - SQL: MAX(decimal_column)
 * - 用于查找高精度十进制数列的最大值
 * - 适用于需要精确计算的财务、科学计算等场景
 *
 * 工作原理:
 * - 初始值: BigDecimal(Double.MIN_VALUE)(确保任何实际值都会更大)
 * - 比较函数: BigDecimal::max(返回两个数中较大的)
 * - 第一条数据会替换初始值,后续数据会与当前最大值比较
 *
 * 性能特点:
 * - 使用 BigDecimal 类,保证精度
 * - 运算性能比基本类型慢,但精度高
 * - 不会出现浮点数精度损失
 *
 * 注意:
 * - 不会溢出,因为 BigDecimal 可以表示任意精度的数
 * - 性能比基本类型慢
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 */
/** 作为用户定义聚合函数实现的 {@code MAX} 函数,计算 {@code BigDecimal} 值的最大值 */
public static class MaxBigDecimal extends NumericComparison<BigDecimal> {
  /**
   * 构造函数,初始化 MAX BigDecimal 聚合函数
   *
   * 初始化:
   * - 初始值: BigDecimal(Double.MIN_VALUE)(确保任何实际值都会更大)
   * - 比较函数: MaxBigDecimal::max(自定义的 max 方法)
   */
  public MaxBigDecimal() {
    // 调用父类构造函数,传入初始值和比较函数
    // 使用 Double.MIN_VALUE 创建 BigDecimal 作为初始值
    super(new BigDecimal(Double.MIN_VALUE), MaxBigDecimal::max);
  }

  /**
   * 返回两个 BigDecimal 中较大的值
   *
   * @param a 第一个 BigDecimal 值
   * @param b 第二个 BigDecimal 值
   * @return 较大的值
   *
   * 功能:
   * - 使用 BigDecimal 的 max 方法
   * - 保证精度,不会出现浮点数精度损失
   */
  public static BigDecimal max(BigDecimal a, BigDecimal b) {
    return a.max(b);
  }
}

/**
 * MAX 函数的布尔值实现,计算布尔值的最大值,作为用户定义聚合函数
 *
 * 这个类实现了对布尔类型值的最大值计算
 *
 * 布尔值的最大值定义:
 * - FALSE < TRUE
 * - 因此 MAX 返回 TRUE(逻辑或:只要有一个值为 TRUE 就返回 TRUE)
 *
 * 使用场景:
 * - SQL: MAX(boolean_column)
 * - 用于查找布尔列的最大值
 * - 语义上等同于逻辑或(OR)操作
 *
 * 工作原理:
 * - 初始值: Boolean.FALSE(确保任何实际值都会更大或相等)
 * - 比较函数: 逻辑或(OR)
   * 只要有一个值为 TRUE,结果就为 TRUE
 *
 * 性能特点:
 * - 使用基本类型 boolean,避免装箱拆箱
 * - 简单的逻辑运算,性能高效
 *
 * 注意:
 * - 空集合返回 null(由调用方的 nullIfEmpty 参数控制)
 * - 如果所有值都是 null,返回 null
 * - 语义上等同于 ANY() 或 SOME() 函数
 */
/** 作为用户定义聚合函数实现的 {@code MAX} 函数,计算 {@code boolean} 值的最大值 */
public static class MaxBoolean {
  /**
   * 构造函数
   *
   * 注意:
   * - 不需要初始化任何状态
   * - init() 方法负责初始化累加器
   */
  public MaxBoolean() { }

  /**
   * 初始化累加器
   *
   * @return 初始累加器值(Boolean.FALSE)
   *
   * 功能:
   * - 返回初始值 FALSE
   * - 确保任何实际值(TRUE)都会更大
   */
  public Boolean init() {
    return Boolean.FALSE;
  }

  /**
   * 添加新值到累加器
   *
   * @param accumulator 当前累加器值
   * @param value 要比较的新值
   * @return 比较后的新累加器值
   *
   * 功能:
   * - 执行逻辑或(OR)操作
   * - 只要 accumulator 或 value 中有一个为 TRUE,结果就为 TRUE
   * - 这符合 MAX 的语义(FALSE < TRUE)
   */
  public Boolean add(Boolean accumulator, Boolean value) {
    return accumulator || value;
  }

  /**
   * 合并两个累加器
   *
   * @param accumulator0 第一个累加器值
   * @param accumulator1 第二个累加器值
   * @return 合并后的累加器值
   *
   * 功能:
   * - 用于分布式计算或并行聚合
   * - 执行逻辑或(OR)操作
   */
  public Boolean merge(Boolean accumulator0, Boolean accumulator1) {
    return add(accumulator0, accumulator1);
  }

  /**
   * 返回最终结果
   *
   * @param 累加器值
   * @return 聚合结果
   *
   * 功能:
   * - 对于 MAX 布尔值,结果就是累加器值本身
   */
  public Boolean result(Boolean accumulator) {
    return accumulator;
  }
}

  /**
 * 基于用户定义聚合函数的累加器工厂
 *
 * 这个类为用户定义聚合函数(UDAF)提供累加器工厂
 * 使用反射调用用户定义的聚合函数实现
 *
 * 用户定义聚合函数规范:
 * - 必须包含 init() 方法:初始化累加器
 * - 必须包含 add(accumulator, ...args) 方法:添加新值
 * - 必须包含 merge(accumulator0, accumulator1) 方法:合并累加器
 * - 必须包含 result(accumulator) 方法:返回结果
 *
 * 支持的构造方式:
 * 1. 无参构造函数:用于静态方法或不需要上下文的函数
 * 2. FunctionContext 参数构造函数:用于需要运行时上下文的函数
 *
 * 使用场景:
 * - SUM, MIN, MAX 等内置函数
 * - 用户自定义的聚合函数
 * - 需要特殊逻辑的聚合函数
 *
 * 限制:
 * - 当前实现只支持单参数聚合函数
 * - 多参数聚合函数会抛出 UnsupportedOperationException
 */
/** 基于用户定义聚合函数的累加器工厂 */
private static class UdaAccumulatorFactory implements AccumulatorFactory {
  // 聚合函数实现对象,包含反射调用的方法信息
  // 通过 AggregateFunctionImpl.create() 创建
  final AggregateFunctionImpl aggFunction;
  // 参数位置,指定聚合函数使用输入行的哪个字段
  // 例如: SUM(salary) 的 argOrdinal 为 salary 字段的位置
  final int argOrdinal;
  // 聚合函数实例,可能为 null
  // 如果聚合函数使用静态方法,instance 为 null
  // 如果聚合函数是实例方法,instance 为创建的对象
  public final @Nullable Object instance;
  // 空集合是否返回 null
  // true: 如果没有输入数据,返回 null
  // false: 如果没有输入数据,返回初始值
  public final boolean nullIfEmpty;

  /**
   * 构造函数,初始化用户定义聚合函数累加器工厂
   *
   * @param aggFunction 聚合函数实现对象
   * @param call 聚合函数调用,包含参数列表等信息
   * @param nullIfEmpty 空集合是否返回 null
   * @param dataContext 数据上下文,用于创建函数实例
   *
   * 初始化过程:
   * 1. 保存聚合函数实现
   * 2. 验证参数数量(当前只支持单参数)
   * 3. 保存参数位置
   * 4. 创建聚合函数实例(如果需要)
   * 5. 保存空集合处理标志
   *
   * 异常:
   * - 如果参数数量不为 1,抛出 UnsupportedOperationException
   */
  UdaAccumulatorFactory(AggregateFunctionImpl aggFunction,
      AggregateCall call, boolean nullIfEmpty, DataContext dataContext) {
    // 保存聚合函数实现
    this.aggFunction = aggFunction;
    // 验证参数数量,当前实现只支持单参数
    if (call.getArgList().size() != 1) {
      // 抛出不支持操作异常
      throw new UnsupportedOperationException("in current implementation, "
          + "aggregate must have precisely one argument");
    }
    // 获取参数位置(输入行中的字段索引)
    argOrdinal = call.getArgList().get(0);
    // 创建聚合函数实例(如果需要)
    instance = createInstance(aggFunction, dataContext);
    // 保存空集合处理标志
    this.nullIfEmpty = nullIfEmpty;
  }

  /**
   * 创建聚合函数实例
   *
   * @param aggFunction 聚合函数实现对象
   * @param dataContext 数据上下文
   * @return 聚合函数实例,如果使用静态方法则返回 null
   *
   * 创建过程:
   * 1. 如果是静态方法,返回 null
   * 2. 尝试使用无参构造函数创建实例
   * 3. 如果失败,尝试使用 FunctionContext 参数构造函数
   * 4. 如果都失败,抛出异常
   *
   * 支持的构造函数:
   * - 无参构造函数: public MyClass() {}
   * - FunctionContext 构造函数: public MyClass(FunctionContext ctx) {}
   *
   * 异常处理:
   * - 静态方法返回 null
   * - 创建失败抛出运行时异常
   */
  static @Nullable Object createInstance(AggregateFunctionImpl aggFunction,
      DataContext dataContext) {
    // 如果是静态方法,不需要实例,返回 null
    if (aggFunction.isStatic) {
      return null;
    }
    // 尝试使用无参构造函数创建实例
    try {
      // 获取无参构造函数
      final Constructor<?> constructor =
          aggFunction.declaringClass.getConstructor();
      // 创建实例
      return constructor.newInstance();
    } catch (InstantiationException | IllegalAccessException
        | NoSuchMethodException | InvocationTargetException e) {
      // 忽略异常,尝试下一个构造函数
    }
    // 尝试使用 FunctionContext 参数构造函数创建实例
    try {
      // 获取带 FunctionContext 参数的构造函数
      final Constructor<?> constructor =
          aggFunction.declaringClass.getConstructor(FunctionContext.class);
      // 创建参数数组
      final Object[] args = new Object[aggFunction.getParameters().size()];
      // 创建函数上下文
      final FunctionContext functionContext =
          FunctionContexts.of(dataContext, args);
      // 创建实例
      return constructor.newInstance(functionContext);
    } catch (InstantiationException | IllegalAccessException
        | NoSuchMethodException | InvocationTargetException e) {
      // 将检查异常转换为运行时异常
      throw Util.toUnchecked(e);
    }
  }

  /**
   * 创建新的累加器实例
   *
   * @return 新的用户定义聚合函数累加器
   *
   * 创建过程:
   * 1. 创建新的 UdaAccumulator 实例
   * 2. 传入工厂引用,用于访问聚合函数信息和实例
   * 3. 每个累加器实例有独立的状态
   */
  @Override public Accumulator get() {
    // 创建新的 UdaAccumulator 实例
    return new UdaAccumulator(this);
  }
}

  /**
 * 基于用户定义聚合函数的累加器实现
 *
 * 这个类使用反射调用用户定义聚合函数的方法来实现聚合操作
 * 每个累加器实例维护独立的状态
 *
 * 工作流程:
 * 1. 构造时调用 init() 方法初始化累加器状态
 * 2. 每次调用 send() 时调用 add() 方法更新状态
 * 3. 调用 end() 时调用 result() 方法返回最终结果
 *
 * 反射调用:
 * - 使用 Method.invoke() 调用用户定义的方法
 * - 支持静态方法和实例方法
 * - 异常被转换为运行时异常
 *
 * 空集合处理:
 * - 跟踪是否处理过任何数据(empty 标志)
 * - 根据工厂的 nullIfEmpty 设置决定空集合返回值
 *
 * Null 处理:
 * - 如果参数为 null,则不更新累加器
 * - 这符合 SQL 聚合函数的语义
 */
/** 基于用户定义聚合函数的累加器 */
private static class UdaAccumulator implements Accumulator {
  // 工厂引用,用于访问聚合函数信息和实例
  private final UdaAccumulatorFactory factory;
  // 累加器当前值,由用户定义的 add 方法更新
  private @Nullable Object value;
  // 空标志,指示是否处理过任何数据
  // true: 没有处理过数据
  // false: 至少处理过一条数据
  private boolean empty;

  /**
   * 构造函数,初始化用户定义聚合函数累加器
   *
   * @param factory 工厂对象,包含聚合函数信息和实例
   *
   * 初始化过程:
   * 1. 保存工厂引用
   * 2. 调用 init() 方法初始化累加器状态
   * 3. 设置空标志为 true
   *
   * 异常处理:
   * - 反射调用失败时抛出运行时异常
   */
  UdaAccumulator(UdaAccumulatorFactory factory) {
    // 保存工厂引用
    this.factory = factory;
    try {
      // 调用用户定义的 init() 方法初始化累加器
      // 使用反射调用,instance 可能为 null(静态方法)
      this.value = factory.aggFunction.initMethod.invoke(factory.instance);
    } catch (IllegalAccessException | InvocationTargetException e) {
      // 将检查异常转换为运行时异常
      throw new RuntimeException(e);
    }
    // 初始状态为空(还没有处理过数据)
    this.empty = true;
  }

  /**
   * 处理输入行,更新累加器状态
   *
   * @param row 输入数据行
   *
   * 处理流程:
   * 1. 构建参数数组: [累加器值, 参数值]
   * 2. 检查参数是否为 null
   * 3. 如果参数不为 null,调用 add() 方法更新累加器
   * 4. 设置空标志为 false
   *
   * Null 处理:
   * - 如果参数为 null,则不更新累加器
   * - 这符合 SQL 聚合函数的语义
   * - 例如: SUM(col) 跳过 col 为 null 的行
   *
   * 异常处理:
   * - 反射调用失败时抛出运行时异常
   */
  @Override public void send(Row row) {
    // 构建参数数组: [累加器值, 参数值]
    // 注意: 参数数组长度固定为 2,即使有多个参数(当前实现只支持单参数)
    final @Nullable Object[] args = {value, row.getValues()[factory.argOrdinal]};
    // 检查参数是否为 null(从索引 1 开始,因为索引 0 是累加器值)
    for (int i = 1; i < args.length; i++) {
      if (args[i] == null) {
        // 如果有任何一个参数为 null,不更新累加器
        // 这符合 SQL 聚合函数的语义
        return; // one of the arguments is null; don't add to the total
      }
    }
    try {
      // 调用用户定义的 add() 方法更新累加器
      // 使用反射调用,instance 可能为 null(静态方法)
      value = factory.aggFunction.addMethod.invoke(factory.instance, args);
    } catch (IllegalAccessException | InvocationTargetException e) {
      // 将检查异常转换为运行时异常
      throw new RuntimeException(e);
    }
    // 标记为非空(已经处理过数据)
    empty = false;
  }

  /**
   * 返回聚合结果
   *
   * @return 聚合结果,可能为 null
   *
   * 处理流程:
   * 1. 检查是否为空集合且需要返回 null
   * 2. 如果不返回 null,调用 result() 方法获取结果
   * 3. 返回结果值
   *
   * 空集合处理:
   * - 如果空标志为 true 且 nullIfEmpty 为 true,返回 null
   * - 否则返回初始值(result 方法的结果)
   *
   * 异常处理:
   * - 反射调用失败时抛出运行时异常
   */
  @Override public @Nullable Object end() {
    // 检查是否为空集合且需要返回 null
    if (factory.nullIfEmpty && empty) {
      // 空集合返回 null
      return null;
    }
    // 构建参数数组: [累加器值]
    final @Nullable Object[] args = {value};
    try {
      // 确保聚合函数和结果方法不为 null
      AggregateFunctionImpl aggFunction =
          requireNonNull(factory.aggFunction, "factory.aggFunction");
      // 调用用户定义的 result() 方法获取最终结果
      // 使用反射调用,instance 可能为 null(静态方法)
      return requireNonNull(aggFunction.resultMethod, "aggFunction.resultMethod")
          .invoke(factory.instance, args);
    } catch (IllegalAccessException | InvocationTargetException e) {
      // 将检查异常转换为运行时异常
      throw new RuntimeException(e);
    }
  }
}

  /**
 * 应用过滤器的累加器包装器
 *
 * 这个类为其他累加器添加过滤功能
 * 只有当过滤条件为 true 时,才会调用内部累加器
 *
 * 使用场景:
 * - 聚合函数的过滤条件(如 COUNT(col) FILTER (WHERE condition))
 * - HAVING 子句
 * - 条件聚合
 *
 * 工作原理:
 * 1. 包装一个基础累加器
 * 2. 在 send() 方法中检查过滤条件
 * 3. 只有过滤条件为 true 时才调用基础累加器
 * 4. end() 方法直接委托给基础累加器
 *
 * 过滤条件:
 * - 过滤条件是输入行中的一个布尔字段
 * - 字段位置由 filterArg 指定
 * - 只有当该字段为 Boolean.TRUE 时才处理
 *
 * 设计模式:
 * - 装饰器模式:为累加器添加过滤功能
 * - 委托模式:将操作委托给内部累加器
 */
/** 对另一个累加器应用过滤器的累加器。过滤器是输入行中的 BOOLEAN 字段。 */
private static class FilterAccumulator implements Accumulator {
  // 内部累加器,实际的聚合操作由它执行
  private final Accumulator accumulator;
  // 过滤字段的位置,指定输入行中的哪个字段作为过滤条件
  private final int filterArg;

  /**
   * 构造函数,初始化过滤累加器
   *
   * @param accumulator 内部累加器,执行实际的聚合操作
   * @param filterArg 过滤字段的位置
   *
   * 初始化:
   * - 保存内部累加器引用
   * - 保存过滤字段位置
   */
  FilterAccumulator(Accumulator accumulator, int filterArg) {
    // 保存内部累加器
    this.accumulator = accumulator;
    // 保存过滤字段位置
    this.filterArg = filterArg;
  }

  /**
   * 处理输入行,应用过滤条件
   *
   * @param row 输入数据行
   *
   * 处理流程:
   * 1. 检查过滤字段的值
   * 2. 如果值为 Boolean.TRUE,调用内部累加器的 send 方法
   * 3. 否则,跳过该行数据
   *
   * 过滤逻辑:
   * - 只有当 row.getValues()[filterArg] == Boolean.TRUE 时才处理
   * - 其他情况(包括 null 和 false)都跳过
   *
   * 注意:
   * - 过滤字段必须是布尔类型
   * - 使用严格相等比较,必须是 Boolean.TRUE 对象
   */
  @Override public void send(Row row) {
    // 检查过滤字段的值是否为 Boolean.TRUE
    if (row.getValues()[filterArg] == Boolean.TRUE) {
      // 过滤条件满足,调用内部累加器的 send 方法
      accumulator.send(row);
    }
    // 否则,跳过该行数据,不更新累加器
  }

  /**
   * 返回聚合结果
   *
   * @return 聚合结果,可能为 null
   *
   * 功能:
   * - 直接委托给内部累加器的 end 方法
   * - 不需要额外的过滤逻辑
   *
   * 注意:
   * - 如果没有数据通过过滤条件,返回值取决于内部累加器
   * - 例如: COUNT 过滤后可能返回 0
   */
  @Override public @Nullable Object end() {
    // 直接委托给内部累加器的 end 方法
    return accumulator.end();
  }
}
}
