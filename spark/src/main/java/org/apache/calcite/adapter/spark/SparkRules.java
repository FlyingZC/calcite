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
package org.apache.calcite.adapter.spark; // 定义包名，表示这个类属于Spark适配器包

import org.apache.calcite.DataContext; // 导入DataContext类，用于提供数据上下文信息
import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 导入EnumerableConvention类，表示可枚举调用约定
import org.apache.calcite.adapter.enumerable.JavaRowFormat; // 导入JavaRowFormat类，表示Java行格式
import org.apache.calcite.adapter.enumerable.PhysType; // 导入PhysType类，表示物理类型
import org.apache.calcite.adapter.enumerable.PhysTypeImpl; // 导入PhysTypeImpl类，PhysType的实现类
import org.apache.calcite.adapter.enumerable.RexImpTable; // 导入RexImpTable类，用于Rex表达式实现表
import org.apache.calcite.adapter.enumerable.RexToLixTranslator; // 导入RexToLixTranslator类，用于将Rex表达式转换为Linq4j表达式
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory类，用于创建Java类型
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入BlockBuilder类，用于构建代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入BlockStatement类，表示代码块语句
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，表示表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions类，用于创建表达式
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression类，表示参数表达式
import org.apache.calcite.linq4j.tree.Primitive; // 导入Primitive类，表示基本类型
import org.apache.calcite.linq4j.tree.Types; // 导入Types类，用于类型操作
import org.apache.calcite.plan.Convention; // 导入Convention类，表示调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式优化集群
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，表示关系表达式优化成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，表示关系表达式优化器
import org.apache.calcite.plan.RelOptRule; // 导入RelOptRule类，表示关系表达式优化规则
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系特征集合
import org.apache.calcite.rel.RelNode; // 导入RelNode类，表示关系节点
import org.apache.calcite.rel.RelWriter; // 导入RelWriter类，用于写入关系表达式信息
import org.apache.calcite.rel.SingleRel; // 导入SingleRel类，表示单输入关系节点
import org.apache.calcite.rel.convert.ConverterRule; // 导入ConverterRule类，表示转换规则
import org.apache.calcite.rel.core.Values; // 导入Values类，表示Values操作符
import org.apache.calcite.rel.logical.LogicalCalc; // 导入LogicalCalc类，表示逻辑Calc操作符
import org.apache.calcite.rel.logical.LogicalValues; // 导入LogicalValues类，表示逻辑Values操作符
import org.apache.calcite.rel.metadata.RelMdUtil; // 导入RelMdUtil类，关系元数据工具类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，关系元数据查询类
import org.apache.calcite.rel.rules.CoreRules; // 导入CoreRules类，核心规则集合
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField类，表示关系数据类型字段
import org.apache.calcite.rex.RexLiteral; // 导入RexLiteral类，表示Rex字面量
import org.apache.calcite.rex.RexMultisetUtil; // 导入RexMultisetUtil类，Rex多集工具类
import org.apache.calcite.rex.RexProgram; // 导入RexProgram类，表示Rex程序
import org.apache.calcite.sql.validate.SqlConformance; // 导入SqlConformance类，SQL一致性
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SqlConformanceEnum类，SQL一致性枚举
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod类，内置方法
import org.apache.calcite.util.Pair; // 导入Pair类，表示键值对
import org.apache.calcite.util.Util; // 导入Util类，工具类

import org.apache.spark.api.java.JavaRDD; // 导入JavaRDD类，Spark的Java弹性分布式数据集
import org.apache.spark.api.java.JavaSparkContext; // 导入JavaSparkContext类，Spark的Java上下文
import org.apache.spark.api.java.function.FlatMapFunction; // 导入FlatMapFunction类，Spark的扁平化映射函数
import org.apache.spark.api.java.function.Function; // 导入Function类，Spark的函数接口

import com.google.common.collect.ImmutableList; // 导入ImmutableList类，不可变列表
import com.google.common.collect.Iterables; // 导入Iterables类，迭代器工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可空类型

import scala.Tuple2; // 导入Tuple2类，Scala的二元组

import java.lang.reflect.Type; // 导入Type类，表示Java类型
import java.util.AbstractList; // 导入AbstractList类，抽象列表
import java.util.ArrayList; // 导入ArrayList类，动态数组
import java.util.Collections; // 导入Collections类，集合工具类
import java.util.List; // 导入List类，列表接口
import java.util.Locale; // 导入Locale类，地区设置
import java.util.Random; // 导入Random类，随机数生成器

/**
 * Rules for the {@link SparkRel#CONVENTION Spark calling convention}.
 * Spark调用约定规则的集合，定义了如何将关系表达式转换为Spark可执行的格式
 *
 * @see JdbcToSparkConverterRule
 */
public abstract class SparkRules { // 定义SparkRules抽象类，包含所有Spark相关的优化规则
  private SparkRules() {} // 私有构造方法，防止实例化，这是一个工具类

  /** Rule that converts from Spark to enumerable convention. */
  // 定义从Spark约定转换为可枚举约定的规则实例
  public static final SparkToEnumerableConverterRule SPARK_TO_ENUMERABLE = // Spark到可枚举转换规则
      SparkToEnumerableConverterRule.DEFAULT_CONFIG // 使用默认配置
          .toRule(SparkToEnumerableConverterRule.class); // 创建规则实例

  /** Rule that converts from enumerable to Spark convention. */
  // 定义从可枚举约定转换为Spark约定的规则实例
  public static final EnumerableToSparkConverterRule ENUMERABLE_TO_SPARK = // 可枚举到Spark转换规则
      EnumerableToSparkConverterRule.DEFAULT_CONFIG // 使用默认配置
          .toRule(EnumerableToSparkConverterRule.class); // 创建规则实例

  /** Rule that converts a {@link org.apache.calcite.rel.logical.LogicalCalc}
   * to a {@link org.apache.calcite.adapter.spark.SparkRules.SparkCalc}. */
  // 定义将逻辑Calc转换为Spark Calc的规则实例
  public static final SparkCalcRule SPARK_CALC_RULE = // Spark Calc规则
      SparkCalcRule.DEFAULT_CONFIG.toRule(SparkCalcRule.class); // 使用默认配置创建规则实例

  /** Rule that implements VALUES operator in Spark convention. */
  // 定义在Spark约定中实现VALUES操作符的规则实例
  public static final SparkValuesRule SPARK_VALUES_RULE = // Spark Values规则
      SparkValuesRule.DEFAULT_CONFIG.toRule(SparkValuesRule.class); // 使用默认配置创建规则实例

  public static List<RelOptRule> rules() { // 返回所有Spark相关规则的列表
    return ImmutableList.of( // 返回不可变的规则列表
        // TODO: add SparkProjectRule, SparkFilterRule, SparkProjectToCalcRule,
        // SparkFilterToCalcRule, and remove the following 2 rules.
        CoreRules.PROJECT_TO_CALC, // Project到Calc转换规则
        CoreRules.FILTER_TO_CALC, // Filter到Calc转换规则
        ENUMERABLE_TO_SPARK, // 可枚举到Spark转换规则
        SPARK_TO_ENUMERABLE, // Spark到可枚举转换规则
        SPARK_VALUES_RULE, // Spark Values规则
        SPARK_CALC_RULE); // Spark Calc规则
  }

  /** Planner rule that converts from enumerable to Spark convention.
   * 将可枚举约定转换为Spark约定的优化器规则
   *
   * @see #ENUMERABLE_TO_SPARK */
  static class EnumerableToSparkConverterRule extends ConverterRule { // 定义可枚举到Spark转换规则类，继承自ConverterRule
    /** Default configuration. */
    // 定义默认配置，指定转换条件：输入为任意RelNode，输入约定为可枚举约定，输出约定为Spark约定
    static final Config DEFAULT_CONFIG = Config.INSTANCE // 使用基础配置实例
        .withConversion(RelNode.class, EnumerableConvention.INSTANCE, // 设置转换：从RelNode和可枚举约定
            SparkRel.CONVENTION, "EnumerableToSparkConverterRule") // 转换到Spark约定，指定规则名称
        .withRuleFactory(EnumerableToSparkConverterRule::new); // 设置规则工厂方法

    EnumerableToSparkConverterRule(Config config) { // 构造方法，接收配置对象
      super(config); // 调用父类ConverterRule的构造方法
    }

    @Override public RelNode convert(RelNode rel) { // 转换方法，将输入关系节点转换为Spark约定
      return new EnumerableToSparkConverter(rel.getCluster(), // 创建EnumerableToSparkConverter实例，使用输入节点的集群
          rel.getTraitSet().replace(SparkRel.CONVENTION), rel); // 替换特征集为Spark约定，并传入原始关系节点
    }
  }

  /** Planner rule that converts from Spark to enumerable convention.
   * 将Spark约定转换为可枚举约定的优化器规则
   *
   * @see #SPARK_TO_ENUMERABLE */
  static class SparkToEnumerableConverterRule extends ConverterRule { // 定义Spark到可枚举转换规则类，继承自ConverterRule
    static final Config DEFAULT_CONFIG = Config.INSTANCE // 使用基础配置实例
        .withConversion(RelNode.class, SparkRel.CONVENTION, // 设置转换：从RelNode和Spark约定
            EnumerableConvention.INSTANCE, "SparkToEnumerableConverterRule") // 转换到可枚举约定，指定规则名称
        .withRuleFactory(SparkToEnumerableConverterRule::new); // 设置规则工厂方法

    SparkToEnumerableConverterRule(Config config) { // 构造方法，接收配置对象
      super(config); // 调用父类ConverterRule的构造方法
    }

    @Override public RelNode convert(RelNode rel) { // 转换方法，将输入关系节点转换为可枚举约定
      return new SparkToEnumerableConverter(rel.getCluster(), // 创建SparkToEnumerableConverter实例，使用输入节点的集群
          rel.getTraitSet().replace(EnumerableConvention.INSTANCE), rel); // 替换特征集为可枚举约定，并传入原始关系节点
    }
  }

  /** Planner rule that implements VALUES operator in Spark convention.
   * 在Spark约定中实现VALUES操作符的优化器规则
   *
   * @see #SPARK_VALUES_RULE */
  public static class SparkValuesRule extends ConverterRule { // 定义Spark Values规则类，继承自ConverterRule
    /** Default configuration. */
    // 定义默认配置，指定转换条件：输入为LogicalValues，输入约定为无约定，输出约定为Spark约定
    static final Config DEFAULT_CONFIG = Config.INSTANCE // 使用基础配置实例
        .withConversion(LogicalValues.class, Convention.NONE, // 设置转换：从LogicalValues和无约定
            SparkRel.CONVENTION, "SparkValuesRule") // 转换到Spark约定，指定规则名称
        .withRuleFactory(SparkValuesRule::new); // 设置规则工厂方法

    SparkValuesRule(Config config) { // 构造方法，接收配置对象
      super(config); // 调用父类ConverterRule的构造方法
    }

    @Override public RelNode convert(RelNode rel) { // 转换方法，将逻辑Values转换为Spark Values
      LogicalValues values = (LogicalValues) rel; // 将输入节点强制转换为LogicalValues类型
      return new SparkValues( // 创建SparkValues实例
          values.getCluster(), // 使用输入节点的集群
          values.getRowType(), // 使用输入节点的行类型
          values.getTuples(), // 使用输入节点的元组列表
          values.getTraitSet().replace(getOutTrait())); // 替换特征集为输出特征
    }
  }

  /** VALUES construct implemented in Spark. */
  // Spark中实现的VALUES操作符，继承自Values并实现SparkRel接口
  public static class SparkValues extends Values implements SparkRel { // 定义Spark Values类，继承Values并实现SparkRel接口
    SparkValues( // 构造方法，创建Spark Values实例
        RelOptCluster cluster, // 关系表达式优化集群
        RelDataType rowType, // 行数据类型
        ImmutableList<ImmutableList<RexLiteral>> tuples, // 元组列表，每个元组包含多个字面量
        RelTraitSet traitSet) { // 关系特征集合
      super(cluster, rowType, tuples, traitSet); // 调用父类Values的构造方法
    }

    @Override public RelNode copy( // 复制方法，创建Spark Values的副本
        RelTraitSet traitSet, List<RelNode> inputs) { // 接收新的特征集合和输入列表
      assert inputs.isEmpty(); // 断言输入列表为空，Values操作符没有输入
      return new SparkValues( // 返回新的Spark Values实例
          getCluster(), rowType, tuples, traitSet); // 使用当前集群、行类型、元组和新特征集
    }

    @Override public Result implementSpark(Implementor implementor) { // 实现Spark约定的方法，返回执行结果
/*
            return Linq4j.asSpark(
                new Object[][] {
                    new Object[] {1, 2},
                    new Object[] {3, 4}
                });
*/
      final JavaTypeFactory typeFactory = // 获取Java类型工厂
          (JavaTypeFactory) getCluster().getTypeFactory(); // 从集群中获取类型工厂并转换为Java类型工厂
      final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建执行代码
      final PhysType physType = // 创建物理类型对象
          PhysTypeImpl.of(implementor.getTypeFactory(), // 使用实现器的类型工厂
              getRowType(), // 使用当前行类型
              JavaRowFormat.CUSTOM); // 使用自定义Java行格式
      final Type rowClass = physType.getJavaRowType(); // 获取Java行类型

      final List<Expression> expressions = new ArrayList<>(); // 创建表达式列表，用于存储所有行的表达式
      final List<RelDataTypeField> fields = rowType.getFieldList(); // 获取行类型的所有字段列表
      for (List<RexLiteral> tuple : tuples) { // 遍历每个元组
        final List<Expression> literals = new ArrayList<>(); // 创建字面量列表，用于存储当前行的所有字面量
        for (Pair<RelDataTypeField, RexLiteral> pair // 遍历字段和字面量的配对
            : Pair.zip(fields, tuple)) { // 将字段列表和元组列表配对
          literals.add( // 将字面量表达式添加到列表
              RexToLixTranslator.translateLiteral( // 将Rex字面量转换为Linq4j表达式
                  pair.right, // 字面量
                  pair.left.getType(), // 字段类型
                  typeFactory, // 类型工厂
                  RexImpTable.NullAs.NULL)); // 将null作为null处理
        }
        expressions.add(physType.record(literals)); // 将当前行的记录表达式添加到表达式列表
      }
      builder.add( // 添加返回语句到代码块
          Expressions.return_(null, // 创建返回表达式
              Expressions.call(SparkMethod.ARRAY_TO_RDD.method, // 调用arrayToRDD方法将数组转换为RDD
                  Expressions.call(SparkMethod.GET_SPARK_CONTEXT.method, // 调用getSparkContext方法获取Spark上下文
                      implementor.getRootExpression()), // 使用实现器的根表达式
                  Expressions.newArrayInit(Primitive.box(rowClass), // 创建数组初始化表达式，元素类型为包装后的行类型
                      expressions)))); // 使用表达式列表作为数组元素
      return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和代码块
    }
  }

  /**
   * Rule to convert a {@link org.apache.calcite.rel.logical.LogicalCalc} to an
   * {@link org.apache.calcite.adapter.spark.SparkRules.SparkCalc}.
   * 将逻辑Calc转换为Spark Calc的规则
   *
   * @see #SPARK_CALC_RULE
   */
  private static class SparkCalcRule extends ConverterRule { // 定义Spark Calc规则类，继承自ConverterRule
    /** Default configuration. */
    // 定义默认配置，指定转换条件：输入为LogicalCalc，输入约定为无约定，输出约定为Spark约定
    static final Config DEFAULT_CONFIG = Config.INSTANCE // 使用基础配置实例
        .withConversion(LogicalCalc.class, Convention.NONE, SparkRel.CONVENTION, // 设置转换：从LogicalCalc和无约定到Spark约定
            "SparkCalcRule") // 指定规则名称
        .withRuleFactory(SparkCalcRule::new); // 设置规则工厂方法

    SparkCalcRule(Config config) { // 构造方法，接收配置对象
      super(config); // 调用父类ConverterRule的构造方法
    }

    @Override public RelNode convert(RelNode rel) { // 转换方法，将逻辑Calc转换为Spark Calc
      final LogicalCalc calc = (LogicalCalc) rel; // 将输入节点强制转换为LogicalCalc类型

      // If there's a multiset, let FarragoMultisetSplitter work on it
      // first.
      // 如果程序中包含多集，让FarragoMultisetSplitter先处理
      final RexProgram program = calc.getProgram(); // 获取Calc的Rex程序
      if (RexMultisetUtil.containsMultiset(program) // 检查程序是否包含多集操作
          || program.containsAggs()) { // 或检查程序是否包含聚合函数
        return null; // 如果包含多集或聚合，返回null表示不进行转换
      }

      return new SparkCalc( // 创建Spark Calc实例
          rel.getCluster(), // 使用输入节点的集群
          rel.getTraitSet().replace(SparkRel.CONVENTION), // 替换特征集为Spark约定
          convert(calc.getInput(), // 转换输入节点
              calc.getInput().getTraitSet().replace(SparkRel.CONVENTION)), // 将输入节点的特征集替换为Spark约定
          program); // 使用原始的Rex程序
    }
  }

  /** Implementation of {@link org.apache.calcite.rel.core.Calc}
   * in Spark convention. */
  // Spark约定中Calc操作符的实现，继承自SingleRel并实现SparkRel接口
  public static class SparkCalc extends SingleRel implements SparkRel { // 定义Spark Calc类，继承SingleRel并实现SparkRel接口
    private final RexProgram program; // Rex程序，包含Calc的所有表达式和条件

    public SparkCalc(RelOptCluster cluster, // 构造方法，创建Spark Calc实例
        RelTraitSet traitSet, // 关系特征集合
        RelNode input, // 输入关系节点
        RexProgram program) { // Rex程序
      super(cluster, traitSet, input); // 调用父类SingleRel的构造方法
      assert getConvention() == SparkRel.CONVENTION; // 断言当前约定为Spark约定
      assert !program.containsAggs(); // 断言程序不包含聚合函数
      this.program = program; // 保存Rex程序
      this.rowType = program.getOutputRowType(); // 设置行类型为程序的输出行类型
    }

    @Deprecated // to be removed before 2.0
    // 已废弃的构造方法，将在2.0版本前移除
    public SparkCalc(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 接收集群、特征集、输入和程序
        RexProgram program, int flags) { // 接收flags参数（已废弃）
      this(cluster, traitSet, input, program); // 调用主构造方法
      Util.discard(flags); // 丢弃flags参数
    }

    @Override public RelWriter explainTerms(RelWriter pw) { // 重写解释方法，用于输出Calc的详细信息
      return program.explainCalc(super.explainTerms(pw)); // 使用程序解释Calc的术语
    }

    @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写行数估算方法
      return RelMdUtil.estimateFilteredRows(getInput(), program, mq); // 使用RelMdUtil估算过滤后的行数
    }

    @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写成本计算方法
        RelMetadataQuery mq) { // 接收优化器和元数据查询
      double dRows = mq.getRowCount(this); // 获取当前节点的行数
      double dCpu = mq.getRowCount(getInput()) // CPU成本 = 输入行数 * 表达式数量
          * program.getExprCount(); // 表达式数量
      double dIo = 0; // IO成本为0，因为Calc不涉及IO操作
      return planner.getCostFactory().makeCost(dRows, dCpu, dIo); // 创建并返回成本对象
    }

    @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写复制方法，创建Spark Calc的副本
      return new SparkCalc( // 返回新的Spark Calc实例
          getCluster(), // 使用当前集群
          traitSet, // 使用新的特征集
          sole(inputs), // 使用输入列表中的唯一输入
          program); // 使用原始的Rex程序
    }

    @Deprecated // to be removed before 2.0
    public int getFlags() { // 已废弃的方法，将在2.0版本前移除
      return 1; // 返回固定值1
    }

    @Override public Result implementSpark(Implementor implementor) { // 实现Spark约定的方法，返回执行结果
      final JavaTypeFactory typeFactory = implementor.getTypeFactory(); // 获取Java类型工厂
      final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器
      final SparkRel child = (SparkRel) getInput(); // 获取输入节点并转换为SparkRel类型

      final Result result = implementor.visitInput(this, 0, child); // 访问输入节点，获取实现结果

      final PhysType physType = // 创建物理类型对象
          PhysTypeImpl.of( // 使用PhysTypeImpl创建物理类型
              typeFactory, getRowType(), JavaRowFormat.CUSTOM); // 使用类型工厂、行类型和自定义格式

      // final RDD<Employee> inputRdd = <<child adapter>>;
      // return inputRdd.flatMap(
      //   new FlatMapFunction<Employee, X>() {
      //          public List<X> call(Employee e) {
      //              if (!(e.empno < 10)) {
      //                  return Collections.emptyList();
      //              }
      //              return Collections.singletonList(
      //                  new X(...)));
      //          }
      //      })


      Type outputJavaType = physType.getJavaRowType(); // 获取输出Java类型
      @SuppressWarnings("unused") // 抑制未使用警告
      final Type rddType = // 定义RDD类型
          Types.of( // 创建类型对象
              JavaRDD.class, outputJavaType); // JavaRDD类型，元素类型为输出Java类型
      Type inputJavaType = result.physType.getJavaRowType(); // 获取输入Java类型
      final Expression inputRdd_ = // 定义输入RDD表达式
          builder.append( // 将表达式添加到代码块
              "inputRdd", // 变量名
              result.block); // 使用结果的代码块

      BlockBuilder builder2 = new BlockBuilder(); // 创建第二个代码块构建器，用于构建flatMap函数体

      final ParameterExpression e_ = // 定义参数表达式，表示输入元素
          Expressions.parameter(inputJavaType, "e"); // 创建参数，类型为输入Java类型，名称为"e"
      if (program.getCondition() != null) { // 如果程序有条件表达式
        Expression condition = // 将条件转换为Linq4j表达式
            RexToLixTranslator.translateCondition( // 转换条件表达式
                program, // Rex程序
                typeFactory, // 类型工厂
                builder2, // 代码块构建器
                new RexToLixTranslator.InputGetterImpl(e_, result.physType), // 输入获取器实现
                null, implementor.getConformance()); // SQL一致性
        builder2.add( // 添加条件判断语句
            Expressions.ifThen( // 如果条件不满足，返回空列表
                Expressions.not(condition), // 取反条件
                Expressions.return_(null, // 返回语句
                    Expressions.call( // 调用Collections.emptyList方法
                        BuiltInMethod.COLLECTIONS_EMPTY_LIST.method)))); // 返回空列表
      }

      final SqlConformance conformance = SqlConformanceEnum.DEFAULT; // 使用默认SQL一致性
      List<Expression> expressions = // 将投影表达式转换为Linq4j表达式列表
          RexToLixTranslator.translateProjects( // 转换投影表达式
              program, // Rex程序
              typeFactory, // 类型工厂
              conformance, // SQL一致性
              builder2, // 代码块构建器
              null, // 输入获取器
              null, // 输入引用
              DataContext.ROOT, // 数据上下文根
              new RexToLixTranslator.InputGetterImpl(e_, result.physType), // 输入获取器实现
              null); // 输出类型
      builder2.add( // 添加返回语句
          Expressions.return_(null, // 返回包含投影结果的列表
              Expressions.convert_( // 转换为List类型
                  Expressions.call( // 调用Collections.singletonList方法
                      BuiltInMethod.COLLECTIONS_SINGLETON_LIST.method, // 单元素列表方法
                      physType.record(expressions)), // 创建记录表达式
                  List.class))); // 转换为List类型

      final BlockStatement callBody = builder2.toBlock(); // 将第二个代码块转换为语句块
      builder.add( // 添加返回语句到第一个代码块
          Expressions.return_( // 返回flatMap操作的结果
              null,
              Expressions.call( // 调用RDD的flatMap方法
                  inputRdd_, // 输入RDD
                  SparkMethod.RDD_FLAT_MAP.method, // flatMap方法
                  Expressions.lambda( // 创建lambda表达式
                      SparkRuntime.CalciteFlatMapFunction.class, // 函数类
                      callBody, // 函数体
                      e_)))); // 参数
      return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和代码块
    }
  }

  // Play area

  public static void main(String[] args) { // 主方法，用于测试Spark功能
    final JavaSparkContext sc = new JavaSparkContext("local[1]", "calcite"); // 创建本地Spark上下文，单线程
    final JavaRDD<String> file = sc.textFile("/usr/share/dict/words"); // 读取系统字典文件为RDD
    System.out.println( // 输出单词首字母的不同数量
        file.map(s -> s.substring(0, Math.min(s.length(), 1))) // 提取每个单词的首字母
            .distinct().count()); // 去重并计数
    file.cache(); // 将RDD缓存到内存中
    String s = // 按首字母分组并统计每个首字母的单词数量
        file.groupBy((Function<String, String>) s1 -> // 按首字母分组
                s1.substring(0, Math.min(s1.length(), 1))) // 提取首字母
            //CHECKSTYLE: IGNORE 1
            .map((Function<Tuple2<String, Iterable<String>>, Object>) pair -> // 将分组结果转换为字符串
                pair._1() + ":" + Iterables.size(pair._2())) // 格式为"首字母:数量"
            .collect() // 收集结果到列表
            .toString(); // 转换为字符串
    System.out.print(s); // 输出分组统计结果

    final JavaRDD<Integer> rdd = // 创建一个自定义的整数RDD
        sc.parallelize(new AbstractList<Integer>() { // 使用抽象列表作为数据源
          final Random random = new Random(); // 创建随机数生成器
          @Override public Integer get(int index) { // 获取指定索引的元素
            System.out.println("get(" + index + ")"); // 输出获取操作
            return random.nextInt(100); // 返回0-99的随机数
          }

          @Override public int size() { // 返回列表大小
            System.out.println("size"); // 输出大小查询操作
            return 10; // 返回固定大小10
          }
        });
    System.out.println( // 按奇偶分组并输出结果
        rdd.groupBy((Function<Integer, Integer>) integer -> integer % 2).collect().toString()); // 按模2分组
    System.out.println( // 过滤以'a'开头的单词并转换为大写及长度
        file.flatMap((FlatMapFunction<String, Pair<String, Integer>>) x -> { // 扁平化映射操作
          if (!x.startsWith("a")) { // 如果单词不以'a'开头
            return Collections.emptyIterator(); // 返回空迭代器
          }
          return Collections.singletonList( // 返回包含单个元素的列表
              Pair.of(x.toUpperCase(Locale.ROOT), x.length())).iterator(); // 创建大写单词和长度的键值对
        })
            .take(5) // 取前5个结果
            .toString()); // 转换为字符串输出
  }
}
