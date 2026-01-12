/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache软件基金会许可证声明，表明此代码遵循Apache 2.0许可证
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，详见NOTICE文件中的版权信息
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0  // ASF根据Apache 2.0许可证授权您使用此文件
 * (the "License"); you may not use this file except in compliance with // ("许可证")；除非遵守许可证，否则您不得使用此文件
 * the License.  You may obtain a copy of the License at  // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0  // Apache 2.0许可证的官方网址
 *
 * Unless required by applicable law or agreed to in writing, software  // 除非适用法律要求或书面同意，否则
 * distributed under the License is distributed on an "AS IS" BASIS,  // 根据许可证分发的软件是按"原样"基础分发的，
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  // 不提供任何形式的明示或暗示的保证或条件
 * See the License for the specific language governing permissions and  // 请参阅许可证以了解许可证下的特定语言
 * limitations under the License.  // 以及使用限制
 */
package org.apache.calcite.adapter.spark;  // 定义包名，表示这个类属于Calcite的Spark适配器模块

import org.apache.calcite.adapter.enumerable.EnumerableConvention;  // 导入EnumerableConvention，表示可枚举的约定，用于定义可迭代的物理属性
import org.apache.calcite.adapter.enumerable.JavaRowFormat;  // 导入JavaRowFormat，定义Java行的格式化方式
import org.apache.calcite.adapter.enumerable.PhysType;  // 导入PhysType，表示物理类型的抽象，用于描述数据的物理表示
import org.apache.calcite.adapter.enumerable.PhysTypeImpl;  // 导入PhysTypeImpl，PhysType的具体实现类
import org.apache.calcite.linq4j.tree.BlockBuilder;  // 导入BlockBuilder，用于构建代码块的构建器，用于生成Java表达式
import org.apache.calcite.linq4j.tree.Expression;  // 导入Expression，表示LINQ4J中的表达式抽象
import org.apache.calcite.linq4j.tree.Expressions;  // 导入Expressions，提供创建各种表达式的工具方法
import org.apache.calcite.plan.ConventionTraitDef;  // 导入ConventionTraitDef，定义约定特征的抽象，用于描述物理实现的约定
import org.apache.calcite.plan.RelOptCluster;  // 导入RelOptCluster，关系表达式优化集群，包含优化上下文和工厂
import org.apache.calcite.plan.RelOptCost;  // 导入RelOptCost，表示关系操作符的成本模型
import org.apache.calcite.plan.RelOptPlanner;  // 导入RelOptPlanner，关系表达式优化器的抽象
import org.apache.calcite.plan.RelTraitSet;  // 导入RelTraitSet，关系特征集合，描述关系节点的物理属性
import org.apache.calcite.rel.RelNode;  // 导入RelNode，关系节点的抽象，表示关系代数中的一个操作
import org.apache.calcite.rel.convert.ConverterImpl;  // 导入ConverterImpl，转换器的基类实现，用于实现关系表达式的转换
import org.apache.calcite.rel.metadata.RelMetadataQuery;  // 导入RelMetadataQuery，用于查询关系节点的元数据信息

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记可能为null的值

import java.util.List;  // 导入List接口，表示有序集合

import static java.util.Objects.requireNonNull;  // 静态导入requireNonNull方法，用于检查对象不为null

/**
 * Relational expression that converts input of {@link EnumerableConvention}  // 关系表达式，用于将输入从可枚举约定（EnumerableConvention）
 * into {@link SparkRel#CONVENTION Spark convention}.  // 转换为Spark约定（SparkRel#CONVENTION）
 *
 * <p>Concretely, this means iterating over the contents of an  // 具体来说，这意味着遍历
 * {@link org.apache.calcite.linq4j.Enumerable}, storing them in a list, and  // 可枚举对象（Enumerable）的内容，将其存储在列表中，然后
 * building an {@link org.apache.spark.rdd.RDD} on top of it.  // 在其上构建一个Spark的弹性分布式数据集（RDD）
 */
public class EnumerableToSparkConverter  // 定义类名：EnumerableToSparkConverter，表示从可枚举到Spark的转换器
    extends ConverterImpl  // 继承ConverterImpl，这是一个转换器的基类，用于实现从一个约定到另一个约定的转换
    implements SparkRel {  // 实现SparkRel接口，表明这个类是一个Spark关系表达式，可以在Spark中执行
  protected EnumerableToSparkConverter(RelOptCluster cluster,  // 受保护的构造方法，参数cluster：关系优化集群，提供优化上下文
      RelTraitSet traits, RelNode input) {  // 参数traits：关系特征集合，定义物理属性；参数input：输入关系节点，要被转换的子节点
    super(cluster, ConventionTraitDef.INSTANCE, traits, input);  // 调用父类构造方法，传入集群、约定特征定义、特征集和输入节点，初始化转换器
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {  // 重写copy方法，用于复制当前关系节点，参数traitSet：新的特征集合；参数inputs：新的输入节点列表
    return new EnumerableToSparkConverter(  // 返回一个新的EnumerableToSparkConverter实例，实现节点的深拷贝
        getCluster(), traitSet, sole(inputs));  // 传入当前集群、新的特征集和唯一的输入节点（sole方法确保只有一个输入）
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,  // 重写computeSelfCost方法，计算当前节点的自身成本，参数planner：优化器实例；参数mq：元数据查询对象
      RelMetadataQuery mq) {  // 参数mq：RelMetadataQuery，用于查询关系节点的元数据信息
    final RelOptCost cost = requireNonNull(super.computeSelfCost(planner, mq));  // 调用父类的成本计算方法，并确保结果不为null，cost表示基础成本
    return cost.multiplyBy(.01);  // 将成本乘以0.01，表示这个转换器的成本很低，鼓励优化器优先使用这个转换
  }

  @Override public Result implementSpark(Implementor implementor) {  // 重写implementSpark方法，实现Spark执行逻辑，参数implementor：实现器对象，用于生成Spark代码
    // Generate:  // 注释说明：要生成的代码结构如下
    //   Enumerable source = ...;  // 生成可枚举源对象
    //   return SparkRuntime.createRdd(sparkContext, source);  // 返回通过SparkRuntime创建的RDD
    if (true) {  // 条件判断，当前总是为true，表示这个转换器尚未完全实现
      throw new RuntimeException("EnumerableToSparkConverter is not implemented");  // 抛出运行时异常，提示这个转换器还未实现
    }
    final BlockBuilder list = new BlockBuilder();  // 创建代码块构建器，用于构建要生成的Java代码块
    final PhysType physType =  // 创建物理类型对象，描述数据的物理表示形式
        PhysTypeImpl.of(  // 使用PhysTypeImpl工厂方法创建物理类型
            implementor.getTypeFactory(), getRowType(),  // 参数：类型工厂和行类型，用于确定数据的Java类型结构
            JavaRowFormat.CUSTOM);  // 使用自定义的Java行格式
    final Expression source = null; // TODO:  // 定义源表达式，当前为null，TODO标记表示需要实现，应该生成可枚举源的表达式
    final Expression sparkContext =  // 定义Spark上下文表达式，用于获取Spark的执行环境
        Expressions.call(  // 创建方法调用表达式
            SparkMethod.GET_SPARK_CONTEXT.method,  // 调用GET_SPARK_CONTEXT方法，获取Spark上下文
            implementor.getRootExpression());  // 参数：实现器的根表达式，用于定位Spark上下文
    final Expression rdd =  // 定义RDD表达式，表示要创建的Spark RDD
        list.append(  // 将RDD表达式添加到代码块构建器中
            "rdd",  // 变量名为"rdd"
            Expressions.call(  // 创建方法调用表达式
                SparkMethod.CREATE_RDD.method,  // 调用CREATE_RDD方法，创建RDD
                sparkContext,  // 第一个参数：Spark上下文
                source));  // 第二个参数：数据源（可枚举对象）
    list.add(  // 将返回语句添加到代码块中
        Expressions.return_(null, rdd));  // 添加return语句，返回创建的RDD
    return implementor.result(physType, list.toBlock());  // 返回实现结果，包含物理类型和生成的代码块
  }
}  // 类定义结束
