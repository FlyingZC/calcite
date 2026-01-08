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
package org.apache.calcite.rel.logical; // 逻辑关系表达式包，包含所有逻辑层面的关系操作符实现

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，标记当前类为实验性API
import org.apache.calcite.plan.Convention; // 导入调用约定接口，定义关系表达式的执行约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含优化器共享的上下文信息
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表，表示表在优化器中的抽象表示
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系表达式的物理和逻辑属性
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，所有关系表达式的基接口
import org.apache.calcite.rel.RelShuttle; // 导入关系穿梭器接口，用于遍历和修改关系表达式树
import org.apache.calcite.rel.core.RepeatUnion; // 导入RepeatUnion核心类，重复联合操作的基类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解，用于标记可能为null的参数

import java.util.List; // 导入Java集合框架的List接口

/**
 * LogicalRepeatUnion是RepeatUnion的子类，表示逻辑层面的重复联合操作
 * 重复联合操作用于实现递归查询，类似于SQL中的WITH RECURSIVE语句
 * 它由两部分组成：种子部分(seed)和迭代部分(iterative)
 * 种子部分是初始结果集，迭代部分是每轮迭代基于前一轮结果生成的数据
 * 通过重复将种子和迭代部分联合，直到达到迭代上限或不再产生新数据
 * 
 * 该类不针对任何特定的引擎或调用约定，是一个纯逻辑的关系操作符
 * 在优化过程中，它会被转换为物理实现，如EnumerableRepeatUnion
 *
 * <p>RepeatUnion的工作原理：
 * 1. 第一轮：输出种子部分的结果
 * 2. 后续轮：将前一轮迭代的结果与迭代部分进行计算，生成新结果
 * 3. 持续迭代直到满足终止条件（达到迭代上限或不再产生新数据）
 * 4. 将所有轮次的结果联合起来作为最终输出
 *
 * <p>典型应用场景：
 * - 递归查询（如组织架构树遍历、图遍历）
 * - 传递闭包计算
 * - 层次结构查询
 *
 * <p>注意：当前API是实验性的，可能会在没有通知的情况下发生变化
 */
@Experimental // 标记为实验性API，提醒用户该接口可能不稳定
public class LogicalRepeatUnion extends RepeatUnion { // 继承RepeatUnion基类，实现逻辑层面的重复联合操作

  //~ Constructors -----------------------------------------------------------
  // 构造方法区域标记，用于代码组织

  /**
   * 私有构造方法，创建LogicalRepeatUnion实例
   * 
   * @param cluster 关系优化集群，包含优化器共享的上下文信息（如类型系统、RexBuilder等）
   * @param traitSet 关系特征集合，定义该关系表达式的属性（如调用约定、排序、分区等）
   * @param seed 种子关系节点，递归查询的初始结果集，第一轮迭代直接输出这部分数据
   * @param iterative 迭代关系节点，每轮迭代基于前一轮结果生成新数据的逻辑
   * @param all 是否保留重复行，true表示保留重复（UNION ALL），false表示去重（UNION）
   * @param iterationLimit 迭代次数上限，-1表示无限制，正整数表示最大迭代次数
   * @param transientTable 临时表，用于存储中间迭代结果，可为null表示不使用临时表
   * 
   * 构造方法直接调用父类RepeatUnion的构造方法，将所有参数传递给父类处理
   * 使用private修饰确保只能通过静态工厂方法创建实例，保证构造的一致性
   */
  private LogicalRepeatUnion(RelOptCluster cluster, RelTraitSet traitSet,
      RelNode seed, RelNode iterative, boolean all, int iterationLimit,
      @Nullable RelOptTable transientTable) {
    super(cluster, traitSet, seed, iterative, all, iterationLimit, transientTable); // 调用父类构造方法初始化所有字段
  }

  /** 
 * 创建LogicalRepeatUnion实例的静态工厂方法（无迭代次数限制版本）
 * 
 * @param seed 种子关系节点，递归查询的初始结果集，第一轮迭代直接输出这部分数据
 * @param iterative 迭代关系节点，每轮迭代基于前一轮结果生成新数据的逻辑
 * @param all 是否保留重复行，true表示保留重复（UNION ALL），false表示去重（UNION）
 * @param transientTable 临时表，用于存储中间迭代结果，可为null表示不使用临时表
 * @return 新创建的LogicalRepeatUnion实例
 * 
 * 该方法是create方法的简化版本，默认设置迭代次数上限为-1（无限制）
 * 调用另一个重载的create方法，传入-1作为iterationLimit参数
 * 
 * 使用场景：当不需要限制迭代次数时使用此方法，让递归查询自然终止
 */
  public static LogicalRepeatUnion create(RelNode seed, RelNode iterative,
      boolean all, @Nullable RelOptTable transientTable) {
    return create(seed, iterative, all, -1, transientTable); // 调用重载方法，设置迭代次数为-1（无限制）
  }

  /** 
 * 创建LogicalRepeatUnion实例的静态工厂方法（带迭代次数限制版本）
 * 
 * @param seed 种子关系节点，递归查询的初始结果集，第一轮迭代直接输出这部分数据
 * @param iterative 迭代关系节点，每轮迭代基于前一轮结果生成新数据的逻辑
 * @param all 是否保留重复行，true表示保留重复（UNION ALL），false表示去重（UNION）
 * @param iterationLimit 迭代次数上限，-1表示无限制，正整数表示最大迭代次数
 * @param transientTable 临时表，用于存储中间迭代结果，可为null表示不使用临时表
 * @return 新创建的LogicalRepeatUnion实例
 * 
 * 该方法执行以下步骤：
 * 1. 从seed节点获取RelOptCluster（包含优化器共享的上下文信息）
 * 2. 创建RelTraitSet，设置为Convention.NONE（表示这是逻辑节点，不绑定到特定引擎）
 * 3. 调用私有构造方法创建LogicalRepeatUnion实例
 * 
 * 使用场景：当需要限制迭代次数以防止无限循环或控制资源消耗时使用此方法
 * 
 * Convention.NONE说明：表示这是一个纯逻辑的关系操作符，还没有转换为物理实现
 * 在后续的优化过程中，会被转换为具有特定Convention的物理节点（如Convention.ENUMERABLE）
 */
  public static LogicalRepeatUnion create(RelNode seed, RelNode iterative,
      boolean all, int iterationLimit, @Nullable RelOptTable transientTable) {
    RelOptCluster cluster = seed.getCluster(); // 从seed节点获取关系优化集群，确保所有节点共享相同的优化上下文
    RelTraitSet traitSet = cluster.traitSetOf(Convention.NONE); // 创建特征集合，设置为NONE约定，表示这是逻辑节点
    return new LogicalRepeatUnion(cluster, traitSet, seed, iterative, all, iterationLimit,
        transientTable); // 调用私有构造方法创建并返回LogicalRepeatUnion实例
  }

  //~ Methods ----------------------------------------------------------------
  // 方法区域标记，用于代码组织

  /**
   * 复制当前LogicalRepeatUnion节点，可以修改特征集合和输入节点
   * 该方法是RelNode接口的核心方法之一，用于在优化过程中创建新的关系表达式
   * 
   * @param traitSet 新的特征集合，可以包含不同的物理属性（如排序、分区、调用约定等）
   * @param inputs 新的输入节点列表，应该包含两个RelNode：seed和iterative
   * @return 新创建的LogicalRepeatUnion实例，具有指定的特征集合和输入节点
   * 
   * 该方法执行以下步骤：
   * 1. 断言traitSet包含Convention.NONE（如果适用），确保逻辑节点的约定正确
   * 2. 断言inputs列表大小为2，确保提供了正确的输入节点数量
   * 3. 使用当前节点的cluster、新的traitSet、新的输入节点以及当前的all、iterationLimit、transientTable创建新实例
   * 
   * 使用场景：
   * - 优化器在应用规则时需要创建修改后的节点
   - 转换逻辑节点为物理节点时修改traitSet
   * 重写关系表达式树时替换子节点
   * 
   * 注意：该方法不修改当前节点，而是返回一个新的节点实例
   */
  @Override public LogicalRepeatUnion copy(RelTraitSet traitSet,
      List<RelNode> inputs) {
    assert traitSet.containsIfApplicable(Convention.NONE); // 断言新的特征集合包含NONE约定，确保逻辑节点的一致性
    assert inputs.size() == 2; // 断言输入列表大小为2，因为RepeatUnion需要两个输入：seed和iterative
    return new LogicalRepeatUnion(getCluster(), traitSet,
        inputs.get(0), inputs.get(1), all, iterationLimit, transientTable); // 创建并返回新的LogicalRepeatUnion实例，保持原有的all、iterationLimit和transientTable
  }

  /**
   * 接受关系穿梭器(RelShuttle)的访问，实现访问者模式
   * 该方法是RelNode接口的核心方法之一，用于遍历和修改关系表达式树
   * 
   * @param shuttle 关系穿梭器，用于遍历关系表达式树的访问者对象
   * @return 穿梭器处理后的关系节点，可能是当前节点、修改后的节点或完全不同的节点
   * 
   * 访问者模式的作用：
   * - 将遍历和修改关系表达式树的逻辑与节点类本身分离
   * - 允许在不修改节点类的情况下添加新的遍历和修改操作
   * - 提供统一的接口来处理各种类型的关系节点
   * 
   * RelShuttle的典型用途：
   * - 遍历关系表达式树进行统计和分析
   * - 修改关系表达式树（如替换子节点、重写表达式）
   * - 验证关系表达式树的完整性
   * - 收集关系表达式树的信息（如使用的表、列等）
   * 
   * 使用场景：
   * - 优化器规则应用时遍历关系表达式树
   * - 调试和打印关系表达式树
   * - 验证关系表达式树的正确性
   * - 收集关系表达式树的元数据
   * 
   * 注意：该方法将控制权交给shuttle，由shuttle决定如何处理当前节点
   */
  @Override public RelNode accept(RelShuttle shuttle) {
    return shuttle.visit(this); // 调用shuttle的visit方法，将当前节点传递给穿梭器处理，返回处理后的节点
  }

} // 类定义结束
