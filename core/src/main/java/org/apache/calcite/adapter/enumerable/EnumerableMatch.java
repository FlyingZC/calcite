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
package org.apache.calcite.adapter.enumerable; // 定义包名，该类位于enumerable适配器包中

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于处理Java类型映射
import org.apache.calcite.linq4j.MemoryFactory; // 导入内存工厂，用于处理内存中的数据
import org.apache.calcite.linq4j.Ord; // 导入Ord工具类，用于带索引的元素遍历
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入代码块语句，表示一个完整的代码块
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式基类，用于表示各种Java表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建各种表达式
import org.apache.calcite.linq4j.tree.MemberDeclaration; // 导入成员声明，用于表示类的成员（方法、字段等）
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入参数表达式，用于表示方法参数
import org.apache.calcite.linq4j.tree.Types; // 导入类型工具类，用于处理反射类型
import org.apache.calcite.plan.RelOptCluster; // 导入关系表达式优化集群，包含优化器的共享信息
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系算子的物理属性
import org.apache.calcite.rel.RelCollation; // 导入排序规则，定义数据的排序方式
import org.apache.calcite.rel.RelNode; // 导入关系节点基类，所有关系算子的基类
import org.apache.calcite.rel.core.Match; // 导入Match关系算子基类，实现SQL MATCH_RECOGNIZE功能
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示表或表达式的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂，用于创建类型
import org.apache.calcite.rex.RexBuilder; // 导入Rex表达式构建器，用于构建行表达式
import org.apache.calcite.rex.RexCall; // 导入Rex调用表达式，表示函数调用
import org.apache.calcite.rex.RexLiteral; // 导入Rex字面量表达式，表示常量值
import org.apache.calcite.rex.RexNode; // 导入Rex节点基类，所有行表达式的基类
import org.apache.calcite.rex.RexProgramBuilder; // 导入Rex程序构建器，用于构建Rex程序
import org.apache.calcite.rex.RexSubQuery; // 导入Rex子查询表达式
import org.apache.calcite.rex.RexVisitorImpl; // 导入Rex访问者实现基类，用于遍历Rex表达式树
import org.apache.calcite.runtime.Enumerables; // 导入枚举工具类，提供可枚举集合的扩展方法
import org.apache.calcite.sql.SqlMatchFunction; // 导入SQL匹配函数，如LAST、PREV、CLASSIFIER等
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法枚举，定义系统内置的方法引用
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合，用于高效表示列索引集合

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.lang.reflect.Modifier; // 导入反射修饰符类，用于访问类的修饰符信息
import java.lang.reflect.Type; // 导入Type接口，表示Java类型
import java.util.ArrayList; // 导入ArrayList动态数组
import java.util.List; // 导入List接口
import java.util.Map; // 导入Map接口
import java.util.SortedSet; // 导入SortedSet接口，有序集合
import java.util.function.Consumer; // 导入Consumer函数式接口，表示接受单个参数的操作
import java.util.function.Function; // 导入Function函数式接口，表示接受一个参数并返回结果的函数
import java.util.function.Predicate; // 导入Predicate函数式接口，表示断言函数

import static org.apache.calcite.adapter.enumerable.EnumUtils.NO_EXPRS; // 导入空表达式常量

import static java.util.Objects.requireNonNull; // 导入Objects工具的requireNonNull方法，用于参数校验

/** Implementation of {@link org.apache.calcite.rel.core.Match} in
 * {@link org.apache.calcite.adapter.enumerable.EnumerableConvention enumerable calling convention}. */
// 实现Match关系算子在Enumerable可枚举调用约定中的具体实现
// Match算子用于SQL的MATCH_RECOGNIZE子句，实现模式匹配功能，用于识别数据流中的复杂模式
// EnumerableConvention表示该算子生成可枚举的代码，可以使用LINQ风格的API进行数据访问
public class EnumerableMatch extends Match implements EnumerableRel { // 定义EnumerableMatch类，继承Match基类并实现EnumerableRel接口
  /**
   * Creates an EnumerableMatch.
   *
   * <p>Use {@link #create} unless you know what you're doing.
   */
  // 构造函数：创建一个EnumerableMatch实例
  // 参数说明：
  // - cluster: 关系优化集群，包含优化器的共享配置和上下文信息
  // - traitSet: 关系特征集合，定义该算子的物理属性（如调用约定、排序等）
  // - input: 输入关系节点，表示Match算子要处理的数据源
  // - rowType: 输出行类型，定义Match算子输出的行结构
  // - pattern: 模式表达式，定义要匹配的模式（如 "A B+" 表示A后跟一个或多个B）
  // - strictStart: 是否严格开始，true表示匹配必须从分区第一行开始
  // - strictEnd: 是否严格结束，true表示匹配必须到分区最后一行结束
  // - patternDefinitions: 模式定义映射，键为模式变量名，值为定义该变量的布尔表达式
  // - measures: 度量映射，键为度量名，值为计算该度量的表达式
  // - after: 匹配后的处理策略（如 SKIP TO NEXT ROW、SKIP PAST LAST ROW等）
  // - subsets: 子集映射，定义模式变量的子集，用于简化模式定义
  // - allRows: 是否返回所有行，true返回匹配的所有行，false只返回每个匹配的一行
  // - partitionKeys: 分区键，指定用于分区的列索引集合
  // - orderKeys: 排序键，定义分区内的排序规则
  // - interval: 时间间隔，用于定义匹配的时间窗口（可为null）
  public EnumerableMatch(RelOptCluster cluster, RelTraitSet traitSet,
      RelNode input, RelDataType rowType, RexNode pattern,
      boolean strictStart, boolean strictEnd,
      Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures,
      RexNode after, Map<String, ? extends SortedSet<String>> subsets,
      boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys,
      @Nullable RexNode interval) {
    super(cluster, traitSet, input, rowType, pattern, strictStart, strictEnd,
        patternDefinitions, measures, after, subsets, allRows, partitionKeys,
        orderKeys, interval); // 调用父类Match的构造函数，初始化所有继承的字段
  }

  /** Creates an EnumerableMatch. */
  // 静态工厂方法：创建一个EnumerableMatch实例（推荐使用此方法而非直接构造函数）
  // 参数说明与构造函数相同，但会自动处理traitSet的设置
  // - input: 输入关系节点，表示Match算子要处理的数据源
  // - rowType: 输出行类型，定义Match算子输出的行结构
  // - pattern: 模式表达式，定义要匹配的模式
  // - strictStart: 是否严格开始
  // - strictEnd: 是否严格结束
  // - patternDefinitions: 模式定义映射
  // - measures: 度量映射
  // - after: 匹配后的处理策略
  // - subsets: 子集映射
  // - allRows: 是否返回所有行
  // - partitionKeys: 分区键
  // - orderKeys: 排序键
  // - interval: 时间间隔
  // 返回：新创建的EnumerableMatch实例
  public static EnumerableMatch create(RelNode input, RelDataType rowType,
      RexNode pattern, boolean strictStart, boolean strictEnd,
      Map<String, RexNode> patternDefinitions, Map<String, RexNode> measures,
      RexNode after, Map<String, ? extends SortedSet<String>> subsets,
      boolean allRows, ImmutableBitSet partitionKeys, RelCollation orderKeys,
      @Nullable RexNode interval) {
    final RelOptCluster cluster = input.getCluster(); // 从输入节点获取优化集群
    final RelTraitSet traitSet =
        cluster.traitSetOf(EnumerableConvention.INSTANCE); // 创建包含EnumerableConvention特征的特征集合
    return new EnumerableMatch(cluster, traitSet, input, rowType, pattern,
        strictStart, strictEnd, patternDefinitions, measures, after, subsets,
        allRows, partitionKeys, orderKeys, interval); // 调用构造函数创建实例
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于复制关系节点
    return new EnumerableMatch(getCluster(), traitSet, inputs.get(0), getRowType(),
        pattern, strictStart, strictEnd, patternDefinitions, measures, after,
        subsets, allRows, partitionKeys, orderKeys, interval); // 创建新的EnumerableMatch实例，使用新的特征集合和输入
  }

  @Override public EnumerableRel.Result implement(EnumerableRelImplementor implementor,
      EnumerableRel.Prefer pref) { // 重写implement方法，实现该关系算子为可执行的Java代码
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建生成的Java代码
    final EnumerableRel input = (EnumerableRel) getInput(); // 获取输入关系节点并转换为EnumerableRel类型
    final Result result = implementor.visitChild(this, 0, input, pref); // 访问子节点，生成输入的可执行代码
    final PhysType physType =
        PhysTypeImpl.of(implementor.getTypeFactory(), input.getRowType(),
            result.format); // 创建物理类型对象，描述输入行的Java类型和格式
    final Expression inputExp =
        builder.append("input", result.block); // 将输入的表达式添加到代码块中，变量名为"input"

    PhysType inputPhysType = result.physType; // 获取输入的物理类型

    final PhysType keyPhysType =
        inputPhysType.project(partitionKeys.asList(), JavaRowFormat.LIST); // 创建分区键的物理类型，使用LIST格式
    final ParameterExpression row_ =
        Expressions.parameter(inputPhysType.getJavaRowType(), "row_"); // 创建行参数表达式，表示当前处理的行
    final Expression keySelector_ =
        builder.append("keySelector",
            inputPhysType.generateSelector(row_,
                partitionKeys.asList(),
                keyPhysType.getFormat())); // 生成分区键选择器表达式，用于从行中提取分区键

    final RelDataTypeFactory.Builder typeBuilder =
        implementor.getTypeFactory().builder(); // 创建类型构建器，用于构建输出行类型

    measures.forEach((name, value) ->
        typeBuilder.add(name, value.getType()).nullable(true)); // 遍历所有度量，将它们添加到输出类型中，并标记为可空

    final PhysType emitType =
        PhysTypeImpl.of(implementor.getTypeFactory(), typeBuilder.build(),
            result.format); // 创建输出物理类型，包含所有度量字段

    final Expression matcher_ = implementMatcher(implementor, physType, builder, row_); // 实现匹配器，用于执行模式匹配
    final Expression emitter_ = implementEmitter(implementor, emitType, physType); // 实现发射器，用于将匹配结果转换为输出行

    final MaxHistoryFutureVisitor visitor = new MaxHistoryFutureVisitor(); // 创建访问者，用于计算需要回溯和前瞻的行数
    patternDefinitions.values().forEach(pd -> pd.accept(visitor)); // 遍历所有模式定义，访问它们以计算历史和未来行数

    // Fetch
    // Calculate how many steps we need to look back or forward
    int history = visitor.getHistory(); // 获取需要回溯的历史行数（PREV函数的最大偏移量）
    int future = visitor.getFuture(); // 获取需要前瞻的未来行数（NEXT函数的最大偏移量）

    builder.add(
        Expressions.return_(null,
            Expressions.call(BuiltInMethod.MATCH.method, inputExp, keySelector_,
                matcher_, emitter_, Expressions.constant(history),
                Expressions.constant(future)))); // 添加返回语句，调用MATCH方法进行模式匹配
    return implementor.result(emitType, builder.toBlock()); // 返回实现结果，包含输出类型和生成的代码块
  }

  private Expression implementEmitter(EnumerableRelImplementor implementor,
      PhysType physType, PhysType inputPhysType) { // 实现发射器表达式，用于将匹配结果转换为输出行
    final ParameterExpression rows_ =
        Expressions.parameter(Types.of(List.class, inputPhysType.getJavaRowType()), "rows"); // 创建参数表达式，表示匹配的行列表
    final ParameterExpression rowStates_ =
        Expressions.parameter(List.class, "rowStates"); // 创建参数表达式，表示每行的状态
    final ParameterExpression symbols_ =
        Expressions.parameter(List.class, "symbols"); // 创建参数表达式，表示每行匹配的模式符号
    final ParameterExpression match_ =
        Expressions.parameter(int.class, "match"); // 创建参数表达式，表示匹配编号
    final ParameterExpression consumer_ =
        Expressions.parameter(Consumer.class, "consumer"); // 创建参数表达式，表示结果消费者
    final ParameterExpression i_ = Expressions.parameter(int.class, "i"); // 创建循环索引参数表达式

    final ParameterExpression row_ =
        Expressions.parameter(inputPhysType.getJavaRowType(), "row"); // 创建行参数表达式，表示当前处理的行

    final BlockBuilder builder2 = new BlockBuilder(); // 创建内部代码块构建器，用于构建循环体

    // Add loop variable initialization
    builder2.add(
        Expressions.declare(0, row_,
            EnumUtils.convert(
                Expressions.call(rows_, BuiltInMethod.LIST_GET.method, i_),
                inputPhysType.getJavaRowType()))); // 声明并初始化行变量，从列表中获取第i行并进行类型转换

    RexBuilder rexBuilder = new RexBuilder(implementor.getTypeFactory()); // 创建Rex表达式构建器
    RexProgramBuilder rexProgramBuilder =
        new RexProgramBuilder(inputPhysType.getRowType(), rexBuilder); // 创建Rex程序构建器，用于构建度量计算程序
    for (Map.Entry<String, RexNode> entry : measures.entrySet()) { // 遍历所有度量
      rexProgramBuilder.addProject(entry.getValue(), entry.getKey()); // 将度量表达式添加到程序中，使用度量名作为输出字段名
    }

    final RexToLixTranslator translator =
        RexToLixTranslator.forAggregation(
            (JavaTypeFactory) getCluster().getTypeFactory(),
            builder2,
            new PassedRowsInputGetter(row_, rows_, inputPhysType),
            implementor.getConformance()); // 创建Rex到LINQ表达式的转换器，用于将Rex表达式转换为Java表达式

    final ParameterExpression result_ =
        Expressions.parameter(physType.getJavaRowType()); // 创建结果参数表达式，表示输出行

    builder2.add(
        Expressions.declare(Modifier.FINAL, result_,
            Expressions.new_(physType.getJavaRowType()))); // 声明并初始化结果对象，使用final修饰
    Ord.forEach(measures.values(), (measure, i) ->
        builder2.add(
            Expressions.statement(
                Expressions.assign(physType.fieldReference(result_, i),
                    implementMeasure(translator, rows_, symbols_, i_, row_,
                        measure))))); // 遍历所有度量，为每个度量字段赋值
    builder2.add(
        Expressions.statement(
            Expressions.call(consumer_, BuiltInMethod.CONSUMER_ACCEPT.method,
                result_))); // 调用消费者的accept方法，将结果行传递给消费者

    final BlockBuilder builder = new BlockBuilder(); // 创建外部代码块构建器，用于构建方法体

    // Loop Length

    // we have to use an explicit for (int i = ...) loop, as we need to know later
    // which of the matched rows are already passed (in MatchUtils), so foreach cannot be used
    builder.add(
        Expressions.for_(
            Expressions.declare(0, i_, Expressions.constant(0)), // 初始化循环变量i为0
            Expressions.lessThan(i_,
                Expressions.call(rows_, BuiltInMethod.COLLECTION_SIZE.method)), // 循环条件：i小于行列表大小
            Expressions.preIncrementAssign(i_), // 每次循环后i自增
            builder2.toBlock())); // 循环体为builder2构建的代码块

    return Expressions.new_(
        Types.of(Enumerables.Emitter.class), NO_EXPRS, // 创建Emitter匿名类实例
        Expressions.list(
            EnumUtils.overridingMethodDecl(
                BuiltInMethod.EMITTER_EMIT.method, // 重写emit方法
                ImmutableList.of(rows_, rowStates_, symbols_, match_,
                    consumer_), // 方法参数列表
                builder.toBlock()))); // 方法体为builder构建的代码块
  }

  private static Expression implementMeasure(RexToLixTranslator translator,
      ParameterExpression rows_, ParameterExpression symbols_,
      ParameterExpression i_, ParameterExpression row_, RexNode value) { // 实现单个度量的表达式
    final SqlMatchFunction matchFunction; // 声明SQL匹配函数变量
    final MatchImplementor matchImplementor; // 声明匹配实现器变量
    switch (value.getKind()) { // 根据表达式类型进行分支处理
    case LAST: // LAST函数：获取匹配中最后一次出现的值
    case PREV: // PREV函数：获取匹配中前一次出现的值
    case CLASSIFIER: // CLASSIFIER函数：获取行的分类器（匹配的模式符号）
      matchFunction = (SqlMatchFunction) ((RexCall) value).getOperator(); // 获取匹配函数操作符
      matchImplementor = RexImpTable.INSTANCE.get(matchFunction); // 从实现表中获取对应的实现器

      // Work with the implementor
      return matchImplementor.implement(translator, (RexCall) value,
          row_, rows_, symbols_, i_); // 调用实现器生成该函数的Java表达式

    case RUNNING: // RUNNING修饰符：表示在匹配过程中计算
    case FINAL: // FINAL修饰符：表示在匹配完成后计算
      // See [CALCITE-3341], this should be changed a bit, to implement
      // FINAL behavior
      final List<RexNode> operands = ((RexCall) value).getOperands(); // 获取操作数列表
      assert operands.size() == 1; // 断言只有一个操作数

      switch (operands.get(0).getKind()) { // 检查操作数的类型
      case LAST: // RUNNING/FINAL + LAST
      case PREV: // RUNNING/FINAL + PREV
      case CLASSIFIER: // RUNNING/FINAL + CLASSIFIER
        final RexCall call = (RexCall) operands.get(0); // 获取内部调用
        matchFunction = (SqlMatchFunction) call.getOperator(); // 获取匹配函数操作符
        matchImplementor = RexImpTable.INSTANCE.get(matchFunction); // 获取实现器
        // Work with the implementor
        requireNonNull((PassedRowsInputGetter) translator.inputGetter, "inputGetter") // 确保输入获取器不为空
            .setIndex(null); // 设置索引为null，表示使用当前行
        return matchImplementor.implement(translator, call, row_, rows_,
            symbols_, i_); // 调用实现器生成表达式
      default:
        break;
      }
      return translator.translate(operands.get(0)); // 直接翻译操作数为Java表达式

    default:
      return translator.translate(value); // 默认情况：直接翻译表达式为Java表达式
    }
  }

  private Expression implementMatcher(EnumerableRelImplementor implementor,
      PhysType physType, BlockBuilder builder, ParameterExpression row_) { // 实现匹配器表达式，用于执行模式匹配
    final Expression patternBuilder_ =
        builder.append("patternBuilder",
            Expressions.call(BuiltInMethod.PATTERN_BUILDER.method)); // 创建模式构建器，用于构建模式定义
    final Expression automaton_ =
        builder.append("automaton",
            Expressions.call(implementPattern(patternBuilder_, pattern), // 实现模式表达式
                BuiltInMethod.PATTERN_TO_AUTOMATON.method)); // 将模式转换为自动机
    Expression matcherBuilder_ =
        builder.append("matcherBuilder",
            Expressions.call(BuiltInMethod.MATCHER_BUILDER.method, automaton_)); // 创建匹配器构建器
    final BlockBuilder builder2 = new BlockBuilder(); // 创建内部代码块构建器


    // Wrap a MemoryEnumerable around

    for (Map.Entry<String, RexNode> entry : patternDefinitions.entrySet()) { // 遍历所有模式定义
      // Translate REX to Expressions
      RexBuilder rexBuilder = new RexBuilder(implementor.getTypeFactory()); // 创建Rex表达式构建器
      RexProgramBuilder rexProgramBuilder =
          new RexProgramBuilder(physType.getRowType(), rexBuilder); // 创建Rex程序构建器

      rexProgramBuilder.addCondition(entry.getValue()); // 将模式定义条件添加到程序中

      final RexToLixTranslator.InputGetter inputGetter1 =
          new PrevInputGetter(row_, physType); // 创建前值输入获取器，用于获取PREV函数的值

      final Expression condition = RexToLixTranslator
          .translateCondition(rexProgramBuilder.getProgram(), // 翻译条件表达式为Java表达式
              (JavaTypeFactory) getCluster().getTypeFactory(),
              builder2,
              inputGetter1,
              implementor.allCorrelateVariables,
              implementor.getConformance());

      builder2.add(Expressions.return_(null, condition)); // 添加返回语句，返回条件表达式
      final Expression predicate_ =
          implementPredicate(physType, row_, builder2.toBlock()); // 实现谓词表达式

      matcherBuilder_ =
          Expressions.call(matcherBuilder_, // 调用匹配器构建器的add方法
              BuiltInMethod.MATCHER_BUILDER_ADD.method,
              Expressions.constant(entry.getKey()), // 模式变量名
              predicate_); // 谓词表达式
    }
    return builder.append("matcher",
        Expressions.call(matcherBuilder_,
            BuiltInMethod.MATCHER_BUILDER_BUILD.method)); // 构建匹配器并返回
  }

  /** Generates code for a predicate. */
  // 生成谓词的代码
  // 参数说明：
  // - physType: 物理类型，描述行的Java类型和格式
  // - rows_: 行参数表达式
  // - body: 代码块语句，包含谓词的实现逻辑
  // 返回：谓词表达式（Predicate接口的匿名实现）
  private static Expression implementPredicate(PhysType physType,
      ParameterExpression rows_, BlockStatement body) {
    final List<MemberDeclaration> memberDeclarations = new ArrayList<>(); // 创建成员声明列表
    ParameterExpression row_ =
        Expressions.parameter(
            Types.of(MemoryFactory.Memory.class,
            physType.getJavaRowType()), "row_"); // 创建行参数表达式，类型为Memory<E>
    Expressions.assign(row_,
        Expressions.call(rows_, BuiltInMethod.MEMORY_GET0.method)); // 调用get0方法获取当前行

    // Implement the Predicate here based on the pattern definition

    // Add a predicate method:
    //
    //   public boolean test(E row, List<E> rows) {
    //     return ...;
    //   }
    // 添加谓词方法：test方法，用于测试行是否满足模式定义
    memberDeclarations.add(
        EnumUtils.overridingMethodDecl(
            BuiltInMethod.PREDICATE_TEST.method, // 重写test方法
            ImmutableList.of(row_), // 方法参数列表
            body)); // 方法体
    if (EnumerableRules.BRIDGE_METHODS) { // 如果需要桥接方法
      // Add a bridge method:
      //
      //   public boolean test(Object row, Object rows) {
      //     return this.test(row, (List) rows);
      //   }
      // 添加桥接方法：用于兼容Object类型的参数
      final ParameterExpression row0_ =
          Expressions.parameter(Object.class, "row"); // 创建Object类型的行参数
      @SuppressWarnings("unused")
      final ParameterExpression rowsO_ =
          Expressions.parameter(Object.class, "rows"); // 创建Object类型的行列表参数
      BlockBuilder bridgeBody = new BlockBuilder(); // 创建桥接方法体构建器
      bridgeBody.add(
          Expressions.return_(null,
              Expressions.call(
                  Expressions.parameter(Comparable.class, "this"), // 调用this的test方法
                  BuiltInMethod.PREDICATE_TEST.method,
                  Expressions.convert_(row0_, // 将Object参数转换为具体类型
                      Types.of(MemoryFactory.Memory.class,
                          physType.getJavaRowType()))))); // 转换为Memory<E>类型
      memberDeclarations.add(
          EnumUtils.overridingMethodDecl(
              BuiltInMethod.PREDICATE_TEST.method, // 重写test方法（桥接版本）
              ImmutableList.of(row0_), // 方法参数列表
              bridgeBody.toBlock())); // 方法体
    }
    return Expressions.new_(Types.of(Predicate.class), NO_EXPRS, // 创建Predicate匿名类实例
        memberDeclarations); // 成员声明列表
  }

  /** Generates code for a pattern.
   *
   * <p>For example, for the pattern {@code (A B)}, generates
   * {@code patternBuilder.symbol("A").symbol("B").seq()}. */
  // 生成模式的代码
  // 参数说明：
  // - patternBuilder_: 模式构建器表达式
  // - pattern: 模式Rex表达式
  // 返回：构建后的模式构建器表达式
  // 例如：对于模式 (A B)，生成 patternBuilder.symbol("A").symbol("B").seq()
  private static Expression implementPattern(Expression patternBuilder_,
          RexNode pattern) {
    switch (pattern.getKind()) { // 根据模式表达式的类型进行分支处理
    case LITERAL: // 字面量：表示单个模式符号
      final String symbol = ((RexLiteral) pattern).getValueAs(String.class); // 获取符号名称（字符串）
      return Expressions.call(patternBuilder_, // 调用symbol方法添加符号
          BuiltInMethod.PATTERN_BUILDER_SYMBOL.method,
          Expressions.constant(symbol)); // 传入符号名称常量

    case PATTERN_CONCAT: // 模式连接：表示多个模式符号的序列
      final RexCall concat = (RexCall) pattern; // 获取连接调用
      for (Ord<RexNode> operand : Ord.zip(concat.operands)) { // 遍历所有操作数
        patternBuilder_ = implementPattern(patternBuilder_, operand.e); // 递归实现每个操作数
        if (operand.i > 0) { // 如果不是第一个操作数
          patternBuilder_ =
              Expressions.call(patternBuilder_, // 调用seq方法表示序列关系
                  BuiltInMethod.PATTERN_BUILDER_SEQ.method);
        }
      }
      return patternBuilder_; // 返回构建后的模式构建器

    default:
      throw new AssertionError("unknown kind: " + pattern); // 未知类型，抛出断言错误
    }
  }

  /**
   * Visitor that finds out how much "history" we need in the past and future.
   */
  // 访问者类：用于计算需要回溯的历史行数和前瞻的未来行数
  // 这对于实现PREV和NEXT函数非常重要，因为它们需要访问相对当前行的其他行
  private static class MaxHistoryFutureVisitor extends RexVisitorImpl<Void> { // 定义访问者类，继承RexVisitorImpl
    private int history; // 历史行数，表示需要回溯的最大行数（用于PREV函数）
    private int future; // 未来行数，表示需要前瞻的最大行数（用于NEXT函数）

    protected MaxHistoryFutureVisitor() { // 构造函数
      super(true); // 调用父类构造函数，参数true表示深度优先遍历
    }

    public int getHistory() { // 获取历史行数
      return history; // 返回history字段
    }

    public int getFuture() { // 获取未来行数
      return future; // 返回future字段
    }

    @Override public Void visitCall(RexCall call) { // 重写visitCall方法，访问Rex调用表达式
      call.operands.forEach(o -> o.accept(this)); // 递归访问所有操作数
      final RexLiteral operand; // 声明字面量操作数变量
      switch (call.op.kind) { // 根据操作符类型进行分支处理
      case PREV: // PREV函数：访问前n行的值
        operand = (RexLiteral) call.getOperands().get(1); // 获取第二个操作数（偏移量）
        final int prev =
            requireNonNull(operand.getValueAs(Integer.class), // 获取偏移量整数值
                () -> "operand in " + call); // 如果为null则抛出异常
        this.history = Math.max(this.history, prev); // 更新历史行数为当前值和偏移量的最大值
        break;
      case NEXT: // NEXT函数：访问后n行的值
        operand = (RexLiteral) call.getOperands().get(1); // 获取第二个操作数（偏移量）
        final int next =
            requireNonNull(operand.getValueAs(Integer.class), // 获取偏移量整数值
                () -> "operand in " + call); // 如果为null则抛出异常
        this.future = Math.max(this.future, next); // 更新未来行数为当前值和偏移量的最大值
        break;
      default:
        break; // 其他操作符不做处理
      }
      return null; // 返回null（Void类型）
    }

    @Override public Void visitSubQuery(RexSubQuery subQuery) { // 重写visitSubQuery方法，访问子查询
      return null; // 返回null，不处理子查询
    }
  }

  /**
   * A special Getter that is able to return a field from a list of objects.
   */
  // 特殊的输入获取器：能够从对象列表中返回字段
  // 用于实现RUNNING和FINAL修饰符下的度量计算，可以访问匹配的历史行
  static class PassedRowsInputGetter implements RexToLixTranslator.InputGetter { // 定义PassedRowsInputGetter类，实现InputGetter接口
    private @Nullable Expression index; // 索引表达式，用于指定访问哪一行（null表示当前行）
    private final ParameterExpression row; // 行参数表达式
    private final ParameterExpression passedRows; // 已传递行列表参数表达式
    private final Function<Expression, RexToLixTranslator.InputGetter> generator; // 输入获取器生成函数
    private final PhysType physType; // 物理类型

    PassedRowsInputGetter(ParameterExpression row, ParameterExpression passedRows,
        PhysType physType) { // 构造函数
      this.row = row; // 初始化行参数
      this.passedRows = passedRows; // 初始化已传递行列表
      generator = e -> new RexToLixTranslator.InputGetterImpl(e, physType); // 初始化生成器函数
      this.physType = physType; // 初始化物理类型
    }

    void setIndex(@Nullable Expression index) { // 设置索引表达式
      this.index = index; // 更新index字段
    }

    @Override public Expression field(BlockBuilder list, int index, // 重写field方法，获取字段表达式
        @Nullable Type storageType) {
      if (this.index == null) { // 如果索引为null（表示当前行）
        return generator.apply(this.row).field(list, index, storageType); // 直接从当前行获取字段
      }

      return Expressions.condition( // 否则使用条件表达式
          Expressions.greaterThanOrEqual(this.index, Expressions.constant(0)), // 条件：索引 >= 0
          generator.apply( // 真值分支：从指定索引的行获取字段
              EnumUtils.convert( // 类型转换
                  Expressions.call(this.passedRows, // 调用列表的get方法
                      BuiltInMethod.LIST_GET.method, this.index),
                  physType.getJavaRowType())) // 转换为行类型
              .field(list, index, storageType), // 获取字段
          Expressions.constant(null)); // 假值分支：返回null
    }
  }

  /**
   * A special Getter that "interchanges" the PREV and the field call.
   */
  // 特殊的输入获取器：用于处理PREV函数和字段调用的"交换"
  // PREV函数需要访问前n行，这个获取器通过Memory的get1方法实现
  static class PrevInputGetter implements RexToLixTranslator.InputGetter { // 定义PrevInputGetter类，实现InputGetter接口
    private @Nullable Expression offset; // 偏移量表达式，用于指定向前回溯的行数
    private final ParameterExpression row; // 行参数表达式
    private final Function<Expression, RexToLixTranslator.InputGetter> generator; // 输入获取器生成函数
    private final PhysType physType; // 物理类型

    PrevInputGetter(ParameterExpression row, PhysType physType) { // 构造函数
      this.row = row; // 初始化行参数
      generator = e -> new RexToLixTranslator.InputGetterImpl(e, physType); // 初始化生成器函数
      this.physType = physType; // 初始化物理类型
    }

    void setOffset(@Nullable Expression offset) { // 设置偏移量表达式
      this.offset = offset; // 更新offset字段
    }

    @Override public Expression field(BlockBuilder list, int index, // 重写field方法，获取字段表达式
        @Nullable Type storageType) {
      final ParameterExpression row =
          Expressions.parameter(physType.getJavaRowType()); // 创建行参数表达式
      final ParameterExpression tmp =
          Expressions.parameter(Object.class); // 创建临时Object类型参数
      list.add(
          Expressions.declare(0, tmp, // 声明临时变量
              Expressions.call(this.row, BuiltInMethod.MEMORY_GET1.method, // 调用Memory的get1方法获取前offset行
                  requireNonNull(offset, "offset")))); // 偏移量不能为null
      list.add(
          Expressions.declare(0, row,
              Expressions.convert_(tmp, physType.getJavaRowType()))); // 将临时变量转换为行类型

      // Add return statement if here is a null!
      list.add(
          Expressions.ifThen(
              Expressions.equal(tmp, Expressions.constant(null)), // 如果临时变量为null
              Expressions.return_(null, Expressions.constant(false)))); // 返回false（表示不匹配）

      return generator.apply(row).field(list, index, storageType); // 从行中获取字段
    }
  }
}