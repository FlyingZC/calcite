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
// Apache Calcite Spark适配器包，包含与Apache Spark集成的相关类
package org.apache.calcite.adapter.spark;

// 导入EnumerableRel接口，定义了可枚举关系表达式的基本契约
import org.apache.calcite.adapter.enumerable.EnumerableRel;
// 导入EnumerableRelImplementor类，用于实现可枚举关系表达式
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
// 导入JavaRowFormat枚举，定义Java行格式
import org.apache.calcite.adapter.enumerable.JavaRowFormat;
// 导入PhysType接口，定义物理类型
import org.apache.calcite.adapter.enumerable.PhysType;
// 导入PhysTypeImpl类，物理类型的实现
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;
// 导入JavaTypeFactory接口，Java类型工厂
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入BlockBuilder类，用于构建代码块
import org.apache.calcite.linq4j.tree.BlockBuilder;
// 导入BlockStatement类，表示代码块语句
import org.apache.calcite.linq4j.tree.BlockStatement;
// 导入Expression类，表示表达式
import org.apache.calcite.linq4j.tree.Expression;
// 导入Expressions类，表达式工具类
import org.apache.calcite.linq4j.tree.Expressions;
// 导入ConventionTraitDef类，约定特征定义
import org.apache.calcite.plan.ConventionTraitDef;
// 导入RelOptCluster类，关系优化集群
import org.apache.calcite.plan.RelOptCluster;
// 导入RelOptCost接口，关系优化成本
import org.apache.calcite.plan.RelOptCost;
// 导入RelOptPlanner接口，关系优化器
import org.apache.calcite.plan.RelOptPlanner;
// 导入RelTraitSet类，关系特征集合
import org.apache.calcite.plan.RelTraitSet;
// 导入RelNode接口，关系节点
import org.apache.calcite.rel.RelNode;
// 导入ConverterImpl类，转换器的实现基类
import org.apache.calcite.rel.convert.ConverterImpl;
// 导入RelMetadataQuery类，关系元数据查询
import org.apache.calcite.rel.metadata.RelMetadataQuery;
// 导入SqlConformance接口，SQL一致性
import org.apache.calcite.sql.validate.SqlConformance;

// 导入Nullable注解，标记可能为null的值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入List接口
import java.util.List;

/**
 * 关系表达式，用于将输入从
 * {@link org.apache.calcite.adapter.spark.SparkRel#CONVENTION Spark约定}
 * 转换为 {@link org.apache.calcite.adapter.enumerable.EnumerableConvention}。
 *
 * <p>具体来说，这意味着调用RDD的
 * {@link org.apache.spark.api.java.JavaRDD#collect()} 方法
 * 并将其转换为可枚举集合。
 */
// SparkToEnumerableConverter类：将Spark关系表达式转换为可枚举关系表达式的转换器
// 继承自ConverterImpl，实现EnumerableRel接口
public class SparkToEnumerableConverter
    extends ConverterImpl
    implements EnumerableRel {
  // 构造方法：创建Spark到可枚举的转换器
  // 参数cluster: 关系优化集群，包含优化器共享的上下文信息
  // 参数traits: 关系特征集合，定义此节点的物理属性
  // 参数input: 输入关系节点，即要转换的Spark关系表达式
  protected SparkToEnumerableConverter(RelOptCluster cluster,
      RelTraitSet traits,
      RelNode input) {
    // 调用父类ConverterImpl的构造方法，传入集群、约定特征定义和输入
    // ConventionTraitDef.INSTANCE表示使用默认的约定特征定义
    super(cluster, ConventionTraitDef.INSTANCE, traits, input);
  }

  // copy方法：创建此节点的副本，可以用于优化过程中的规则转换
  // 参数traitSet: 新的特征集合
  // 参数inputs: 新的输入节点列表
  // 返回值: 新的SparkToEnumerableConverter节点
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    // 创建并返回新的SparkToEnumerableConverter实例
    // getCluster()获取当前集群，traitSet使用传入的新特征集
    // sole(inputs)从输入列表中获取唯一的输入节点
    return new SparkToEnumerableConverter(
        getCluster(), traitSet, sole(inputs));
  }

  // computeSelfCost方法：计算此节点的执行成本，用于优化器选择最优执行计划
  // 参数planner: 关系优化器
  // 参数mq: 关系元数据查询对象
  // 返回值: 计算出的成本对象
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) {
    // 调用父类的成本计算方法，然后将结果乘以0.01
    // 这表示此转换器的成本非常低，因为它只是调用RDD的collect()方法
    // 乘以0.01是为了鼓励优化器使用此转换规则
    return super.computeSelfCost(planner, mq).multiplyBy(.01);
  }

  // implement方法：实现可枚举关系表达式，生成执行代码
  // 参数implementor: 可枚举关系表达式实现器
  // 参数pref: 偏好设置，用于控制实现方式
  // 返回值: 实现结果，包含生成的代码块和物理类型信息
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) {
    // 生成如下代码结构：
    //   RDD rdd = ...;
    //   return SparkRuntime.asEnumerable(rdd);
    // 创建代码块构建器，用于生成Java代码
    final BlockBuilder list = new BlockBuilder();
    // 获取输入节点，强制转换为SparkRel类型
    // SparkRel是Spark关系表达式的接口
    final SparkRel child = (SparkRel) getInput();
    // 创建物理类型对象，用于描述输出行的Java类型
    // implementor.getTypeFactory()获取类型工厂
    // getRowType()获取当前节点的行类型
    // JavaRowFormat.CUSTOM表示使用自定义的Java行格式
    final PhysType physType =
        PhysTypeImpl.of(implementor.getTypeFactory(),
            getRowType(),
            JavaRowFormat.CUSTOM);
    // 创建Spark实现器实例，用于实现Spark关系表达式
    // SparkImplementorImpl是SparkRel.Implementor的实现类
    SparkRel.Implementor sparkImplementor =
        new SparkImplementorImpl(implementor);
    // 调用子节点的implementSpark方法，实现Spark逻辑
    // 返回SparkRel.Result对象，包含生成的代码块
    final SparkRel.Result result = child.implementSpark(sparkImplementor);
    // 将RDD变量的声明和初始化添加到代码块中
    // list.append()方法会在代码块中添加一个变量声明
    // "rdd"是变量名，result.block是初始化表达式
    final Expression rdd = list.append("rdd", result.block);
    // 调用SparkRuntime.asEnumerable()方法，将RDD转换为可枚举集合
    // Expressions.call()创建方法调用表达式
    // SparkMethod.AS_ENUMERABLE.method是要调用的方法
    // rdd是方法参数
    final Expression enumerable =
        list.append(
            "enumerable",
            Expressions.call(
                SparkMethod.AS_ENUMERABLE.method,
                rdd));
    // 添加return语句，返回可枚举集合
    // Expressions.return_()创建return语句
    list.add(
        Expressions.return_(null, enumerable));
    // 返回实现结果，包含物理类型和生成的代码块
    return implementor.result(physType, list.toBlock());
  }

  /** SparkRel.Implementor的实现类。
   * SparkRel.Implementor是Spark关系表达式实现器的接口
   * 这个内部类实现了该接口，用于在Spark到可枚举转换过程中实现Spark逻辑
   */
  private static class SparkImplementorImpl extends SparkRel.Implementor {
    // 成员变量：可枚举关系表达式实现器的引用
    // 用于访问类型工厂、Rex构建器等功能
    private final EnumerableRelImplementor implementor;

    // 构造方法：创建Spark实现器实例
    // 参数implementor: 可枚举关系表达式实现器
    SparkImplementorImpl(EnumerableRelImplementor implementor) {
      // 调用父类SparkRel.Implementor的构造方法
      // implementor.getRexBuilder()获取Rex表达式构建器
      super(implementor.getRexBuilder());
      // 保存可枚举实现器的引用
      this.implementor = implementor;
    }

    // result方法：创建Spark实现结果
    // 参数physType: 物理类型，描述输出行的Java类型
    // 参数blockStatement: 代码块语句，包含生成的代码
    // 返回值: SparkRel.Result对象，包含物理类型和代码块
    @Override public SparkRel.Result result(PhysType physType,
        BlockStatement blockStatement) {
      // 创建并返回新的SparkRel.Result实例
      return new SparkRel.Result(physType, blockStatement);
    }

    // visitInput方法：访问输入节点，实现Spark逻辑
    // 参数parent: 父节点，可以是null
    // 参数ordinal: 输入序号，表示是第几个输入
    // 参数input: 输入的Spark关系表达式
    // 返回值: SparkRel.Result对象，包含实现的代码块
    @Override SparkRel.Result visitInput(SparkRel parent, int ordinal, SparkRel input) {
      // 如果父节点不为null，则进行断言验证
      if (parent != null) {
        // 断言输入节点确实是父节点的第ordinal个输入
        // 这是一个防御性编程，确保输入节点正确
        assert input == parent.getInputs().get(ordinal);
      }
      // 调用输入节点的implementSpark方法，实现Spark逻辑
      // 递归地实现整个Spark关系表达式树
      return input.implementSpark(this);
    }

    // getTypeFactory方法：获取Java类型工厂
    // 返回值: Java类型工厂实例
    @Override public JavaTypeFactory getTypeFactory() {
      // 返回底层可枚举实现器的类型工厂
      return implementor.getTypeFactory();
    }

    // getConformance方法：获取SQL一致性设置
    // 返回值: SQL一致性对象
    @Override public SqlConformance getConformance() {
      // 返回底层可枚举实现器的SQL一致性设置
      return implementor.getConformance();
    }
  }
}
