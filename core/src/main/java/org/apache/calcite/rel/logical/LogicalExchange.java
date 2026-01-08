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
package org.apache.calcite.rel.logical; // 声明包名，LogicalExchange类位于org.apache.calcite.rel.logical包中

import org.apache.calcite.plan.Convention; // 导入Convention类，用于定义关系代数表达式的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系代数表达式的集群，包含优化器的相关信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系代数表达式的特征集合，如分布方式、排序等
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution接口，定义数据的分布策略（如广播、哈希、随机等）
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，定义RelDistribution特征的元数据
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化数据中反序列化关系代数表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式树的节点
import org.apache.calcite.rel.RelShuttle; // 导入RelShuttle接口，用于遍历和修改关系代数表达式树
import org.apache.calcite.rel.core.Exchange; // 导入Exchange抽象类，LogicalExchange的父类，表示数据交换操作

/**
 * Sub-class of {@link Exchange} not // LogicalExchange是Exchange的子类，表示逻辑层面的数据交换操作
 * targeted at any particular engine or calling convention. // 不针对任何特定的引擎或调用约定，是通用的逻辑交换操作
 * 
 * 类作用详细说明：
 * LogicalExchange是Calcite关系代数框架中表示数据交换操作的逻辑节点。数据交换是指在分布式查询执行中，
 * 将数据从一个节点重新分布到其他节点的过程。这个类不涉及具体的物理实现细节，只描述逻辑上的数据分布需求。
 * 
 * 主要应用场景：
 * 1. 分布式查询：当查询需要在多个节点上并行执行时，需要将数据按照特定策略（如哈希、广播等）重新分布
 * 2. Join操作：在执行分布式Join时，需要将Join键相同的数据发送到同一个节点
 * 3. 聚合操作：在执行分布式聚合时，需要将相同分组键的数据发送到同一个节点
 * 
 * 与物理Exchange的区别：
 * - LogicalExchange只描述"需要什么样的数据分布"，不关心"如何实现"
 * - 物理Exchange（如EnumerableExchange、SparkExchange等）负责具体的实现细节
 * - 优化器会将LogicalExchange转换为物理Exchange
 * 
 * 特征（Trait）：
 * - Convention.NONE：表示这是逻辑节点，不绑定到任何特定的物理实现
 * - RelDistribution：定义数据的分布策略（如SINGLETON、BROADCAST、HASH_DISTRIBUTED等）
 * 
 * 继承关系：
 * - 继承自Exchange抽象类，Exchange又继承自SingleRel（单输入关系节点）
 * - 实现了RelNode接口，可以参与关系代数表达式树的构建和优化
 */
public final class LogicalExchange extends Exchange { // 定义LogicalExchange类，final表示不能被继承，继承自Exchange
  private LogicalExchange(RelOptCluster cluster, RelTraitSet traitSet, // 私有构造方法，使用工厂方法create()创建实例
      RelNode input, RelDistribution distribution) { // 参数：cluster-集群信息，traitSet-特征集合，input-输入节点，distribution-分布策略
    super(cluster, traitSet, input, distribution); // 调用父类Exchange的构造方法，初始化基本属性
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言特征集合中包含Convention.NONE，确保这是逻辑节点
  }

  /**
   * Creates a LogicalExchange by parsing serialized output. // 通过解析序列化输出创建LogicalExchange实例
   * 这是一个反序列化构造方法，用于从序列化的数据中重建LogicalExchange对象
   * 主要用于查询计划的持久化和跨进程传输
   */
  public LogicalExchange(RelInput input) { // 公开构造方法，接收RelInput对象（包含序列化的节点信息）
    super(input); // 调用父类Exchange的反序列化构造方法，从RelInput中恢复节点状态
  }

  /**
   * Creates a LogicalExchange. // 创建LogicalExchange实例的静态工厂方法
   * 这是创建LogicalExchange对象的推荐方式，确保特征集合的正确设置
   *
   * @param input     Input relational expression // 输入的关系代数表达式，即需要重新分布的数据源
   * @param distribution Distribution specification // 数据分布策略，指定如何将数据分布到各个节点
   * 
   * 返回值说明：
   * 返回配置好的LogicalExchange实例，包含正确的特征集合
   * 
   * 实现细节：
   * 1. 从输入节点获取RelOptCluster（集群信息）
   * 2. 规范化分布策略（canonize），确保使用标准化的分布对象
   * 3. 构建特征集合：将输入节点的特征集替换为Convention.NONE和指定的分布策略
   * 4. 创建并返回LogicalExchange实例
   */
  public static LogicalExchange create(RelNode input, // 静态工厂方法，接收输入节点
      RelDistribution distribution) { // 接收分布策略
    RelOptCluster cluster = input.getCluster(); // 获取输入节点的集群信息，包含优化器上下文
    distribution = RelDistributionTraitDef.INSTANCE.canonize(distribution); // 规范化分布策略，转换为其规范形式（例如将自定义的哈希分布转换为标准形式）
    RelTraitSet traitSet = // 构建新的特征集合
        input.getTraitSet().replace(Convention.NONE).replace(distribution); // 将输入节点的特征集替换为：1）Convention.NONE（逻辑调用约定）2）指定的分布策略
    return new LogicalExchange(cluster, traitSet, input, distribution); // 创建并返回LogicalExchange实例
  }

  //~ Methods ---------------------------------------------------------------- // 方法分隔注释，以下是类的方法定义

  @Override public Exchange copy(RelTraitSet traitSet, RelNode newInput, // 重写copy方法，用于创建当前节点的副本
      RelDistribution newDistribution) { // 参数：traitSet-新的特征集合，newInput-新的输入节点，newDistribution-新的分布策略
    return new LogicalExchange(getCluster(), traitSet, newInput, // 返回新的LogicalExchange实例，使用当前集群信息、新特征集、新输入节点和新分布策略
        newDistribution);
  }

  @Override public RelNode accept(RelShuttle shuttle) { // 重写accept方法，接受RelShuttle访问者，用于遍历和修改关系代数表达式树
    return shuttle.visit(this); // 将访问权交给RelShuttle，让访问者决定如何处理这个节点（可以修改、替换或保持不变）
  }
}