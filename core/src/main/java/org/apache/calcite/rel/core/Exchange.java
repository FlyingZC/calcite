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
package org.apache.calcite.rel.core; // 声明包名，表示这个Exchange类属于org.apache.calcite.rel.core包

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式集群，包含类型工厂等共享资源
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost接口，表示关系操作的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，表示优化器
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution接口，表示数据的分布方式
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，定义分布特征
import org.apache.calcite.rel.RelDistributions; // 导入RelDistributions类，提供常用的分布方式常量
import org.apache.calcite.rel.RelInput; // 导入RelInput接口，用于从序列化数据创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系表达式节点
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系表达式写入输出流
import org.apache.calcite.rel.SingleRel; // 导入SingleRel类，表示只有一个输入的关系表达式基类
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种实用方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可能为null的值

import java.util.List; // 导入List接口，表示有序集合

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于检查参数不为null

/**
 * 关系表达式，对其输入强制执行特定的分布方式，而不改变其内容。
 * Exchange节点用于在分布式查询执行中重新分配数据，例如将数据广播到所有节点或按照某个键进行哈希分布。
 * 它是Calcite优化器中的物理算子，用于实现数据的重新分发策略。
 *
 * 主要应用场景：
 * 1. 当下游算子需要特定的数据分布时（如Join需要哈希分布）
 * 2. 当需要将数据广播到所有工作节点时
 * 3. 当需要将数据重新分区以满足并行处理需求时
 *
 * @see org.apache.calcite.rel.core.SortExchange // 参见SortExchange类，它是Exchange的变体，同时进行排序和分布
 */
public abstract class Exchange extends SingleRel { // Exchange抽象类继承自SingleRel，表示只有一个输入的关系表达式
  //~ Instance fields -------------------------------------------------------- // 成员变量分隔符

  public final RelDistribution distribution; // 数据分布方式，定义了数据如何在节点间分发（如BROADCAST、HASH_DISTRIBUTED、RANDOM等）

  //~ Constructors ----------------------------------------------------------- // 构造方法分隔符

  /**
   * 创建一个Exchange关系表达式。
   * 此构造方法用于在查询优化过程中创建Exchange节点，指定数据的分布方式。
   *
   * @param cluster   此关系表达式所属的集群，包含类型工厂等共享资源
   * @param traitSet  特征集合，包含此关系表达式的物理特征（如分布、排序等）
   * @param input     输入关系表达式，即需要重新分布的数据源
   * @param distribution 分布规范，定义数据如何分发（如BROADCAST、HASH_DISTRIBUTED、RANDOM、SINGLETON等）
   */
  protected Exchange(RelOptCluster cluster, RelTraitSet traitSet, RelNode input,
      RelDistribution distribution) { // 受保护的构造方法，只能由子类调用
    super(cluster, traitSet, input); // 调用父类SingleRel的构造方法，初始化集群、特征集和输入
    this.distribution = requireNonNull(distribution, "distribution"); // 使用requireNonNull确保distribution参数不为null，否则抛出NullPointerException

    assert traitSet.containsIfApplicable(distribution) // 断言特征集合包含适用的分布特征
        : "traits=" + traitSet + ", distribution=" + distribution; // 如果断言失败，输出特征集和分布信息
    assert distribution != RelDistributions.ANY; // 断言分布方式不能是ANY（ANY表示任意分布，不适合Exchange节点）
  }

  /**
   * 通过解析序列化输出来创建Exchange。
   * 此构造方法用于从持久化格式（如JSON）反序列化Exchange节点。
   * 它会规范化分布特征，确保使用规范的分布常量。
   */
  protected Exchange(RelInput input) { // 受保护的构造方法，从RelInput对象创建Exchange
    this(input.getCluster(), // 获取集群信息
        input.getTraitSet().plus(input.getDistribution()), // 获取特征集并添加分布特征
        input.getInput(), // 获取输入关系表达式
        RelDistributionTraitDef.INSTANCE.canonize(input.getDistribution())); // 规范化分布特征，转换为规范的分布常量
  }

  //~ Methods ---------------------------------------------------------------- // 方法分隔符

  @Override public final Exchange copy(RelTraitSet traitSet,
      List<RelNode> inputs) { // 重写copy方法，用于创建此关系表达式的副本
    return copy(traitSet, sole(inputs), distribution); // 调用抽象copy方法，传入新的特征集、唯一的输入和当前的分布方式
  }

  public abstract Exchange copy(RelTraitSet traitSet, RelNode newInput,
      RelDistribution newDistribution); // 抽象方法，子类必须实现，用于创建具有新特征集、新输入和新分布的Exchange副本

  /** Returns the distribution of the rows returned by this Exchange. */ // 返回此Exchange返回的行的分布方式
  public RelDistribution getDistribution() { // 获取分布方式的方法
    return distribution; // 返回成员变量distribution，表示数据的分布规范
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) { // 重写computeSelfCost方法，计算此Exchange节点的自身成本
    // Higher cost if rows are wider discourages pushing a project through an exchange. // 如果行更宽，成本更高，这会阻止将project下推到exchange下方
    double rowCount = mq.getRowCount(this); // 从元数据查询中获取此Exchange节点的行数
    double bytesPerRow = getRowType().getFieldCount() * 4; // 计算每行的字节数（假设每个字段4字节）
    return planner.getCostFactory().makeCost( // 使用优化器的成本工厂创建成本对象
        Util.nLogN(rowCount) * bytesPerRow, rowCount, 0); // CPU成本为n*log(n)*每行字节数，IO成本为行数，网络成本为0
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写explainTerms方法，用于生成此关系表达式的可读说明
    return super.explainTerms(pw) // 调用父类的explainTerms方法，获取基础的说明信息
        .item("distribution", distribution); // 添加分布方式信息到说明中
  }
}
