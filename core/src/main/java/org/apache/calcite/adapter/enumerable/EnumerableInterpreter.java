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
package org.apache.calcite.adapter.enumerable; // 声明包名，表示这个类属于org.apache.calcite.adapter.enumerable包，该包包含可枚举适配器相关的类

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建和管理Java类型
import org.apache.calcite.interpreter.Interpreter; // 导入解释器接口，用于解释执行关系表达式
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示Java表达式
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，提供创建各种表达式的方法
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含优化器的共享资源
import org.apache.calcite.plan.RelOptCost; // 导入关系优化成本，表示执行计划的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入关系优化规划器，用于优化查询计划
import org.apache.calcite.plan.RelTraitSet; // 导入关系特征集合，定义关系节点的物理属性
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，表示关系代数表达式
import org.apache.calcite.rel.SingleRel; // 导入单输入关系节点基类，表示只有一个子节点的关系节点
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询接口，用于获取关系节点的元数据
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法类，包含Calcite提供的内置方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的返回值

import java.util.List; // 导入List接口，用于处理列表集合

/** Relational expression that executes its children using an interpreter. // 类注释：这是一个使用解释器执行其子节点的关系表达式
 *
 * <p>Although quite a few kinds of {@link org.apache.calcite.rel.RelNode} can // 虽然很多种RelNode都可以被解释执行
 * be interpreted, this is only created by default for // 但默认情况下，这个类只为FilterableTable和ProjectableFilterableTable创建
 * {@link org.apache.calcite.schema.FilterableTable} and // 可过滤表（支持过滤操作的表）
 * {@link org.apache.calcite.schema.ProjectableFilterableTable}. // 可投影可过滤表（支持投影和过滤操作的表）
 */
public class EnumerableInterpreter extends SingleRel // 类定义：EnumerableInterpreter继承自SingleRel，表示它是一个只有一个子节点的可枚举关系节点
    implements EnumerableRel { // 实现EnumerableRel接口，表示这个关系节点可以被转换为可枚举的Java代码
  private final double factor; // 成员变量：成本乘数因子，用于调整这个关系节点的执行成本，影响优化器的选择

  /**
   * Creates an EnumerableInterpreter. // 方法注释：创建一个EnumerableInterpreter实例
   *
   * <p>Use {@link #create} unless you know what you're doing. // 除非你知道自己在做什么，否则应该使用create方法而不是直接调用构造函数
   *
   * @param cluster Cluster // 参数：关系优化集群，包含优化器的共享资源和类型系统
   * @param traitSet Traits // 参数：关系特征集合，定义了这个关系节点的物理属性（如约定、排序等）
   * @param input Input relation // 参数：输入关系节点，即这个解释器要解释执行的子节点
   * @param factor Cost multiply factor // 参数：成本乘数因子，用于调整这个关系节点的执行成本
   */
  public EnumerableInterpreter(RelOptCluster cluster, RelTraitSet traitSet, // 构造函数开始：接收集群、特征集合、输入节点和成本因子
      RelNode input, double factor) { // 构造函数参数：输入关系节点和成本乘数因子
    super(cluster, traitSet, input); // 调用父类SingleRel的构造函数，初始化集群、特征集合和输入节点
    assert getConvention() instanceof EnumerableConvention; // 断言：确保这个节点的约定是EnumerableConvention，即这个节点可以被转换为可枚举代码
    this.factor = factor; // 将成本乘数因子保存到成员变量中
  }

  /**
   * Creates an EnumerableInterpreter. // 方法注释：工厂方法，创建一个EnumerableInterpreter实例
   *
   * @param input Input relation // 参数：输入关系节点，即要解释执行的子节点
   * @param factor Cost multiply factor // 参数：成本乘数因子，用于调整执行成本
   */
  public static EnumerableInterpreter create(RelNode input, double factor) { // 静态工厂方法：根据输入节点和成本因子创建EnumerableInterpreter
    final RelTraitSet traitSet = input.getTraitSet() // 获取输入节点的特征集合
        .replace(EnumerableConvention.INSTANCE); // 将特征集合中的约定替换为EnumerableConvention，确保节点可以被枚举
    return new EnumerableInterpreter(input.getCluster(), traitSet, input, // 调用构造函数创建新的EnumerableInterpreter实例
        factor); // 传入成本乘数因子
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写方法：计算这个关系节点的自身执行成本，用于优化器选择最优执行计划
      RelMetadataQuery mq) { // 参数：元数据查询对象，用于获取关系节点的元数据信息
    RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类的computeSelfCost方法，获取基础成本
    if (cost == null) { // 如果基础成本为null（无法计算成本）
      return null; // 返回null，表示无法计算成本
    }
    return cost.multiplyBy(factor); // 将基础成本乘以成本因子，返回调整后的成本，用于影响优化器的选择
  }

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写方法：复制这个关系节点，用于优化器生成等价的执行计划
    return new EnumerableInterpreter(getCluster(), traitSet, sole(inputs), // 创建新的EnumerableInterpreter实例，使用新的特征集合和输入节点
        factor); // 保持成本乘数因子不变
  }

  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 重写方法：实现这个关系节点，生成可执行的Java代码
    final JavaTypeFactory typeFactory = implementor.getTypeFactory(); // 获取Java类型工厂，用于创建Java类型
    final BlockBuilder builder = new BlockBuilder(); // 创建代码块构建器，用于构建Java代码块
    final PhysType physType = // 创建物理类型对象，描述输出行的物理表示形式
        PhysTypeImpl.of(typeFactory, getRowType(), JavaRowFormat.ARRAY); // 使用数组格式表示行数据
    final Expression interpreter_ = // 创建解释器表达式，用于生成解释器对象的代码
        builder.append("interpreter", // 添加一个名为"interpreter"的变量到代码块中
            Expressions.new_(Interpreter.class, implementor.getRootExpression(), // 创建Interpreter类的实例，传入根表达式
                implementor.stash(getInput(), RelNode.class))); // 将输入关系节点stash（暂存）到实现器中，并作为参数传入
    final Expression sliced_ = // 创建切片表达式，用于处理单列输出的特殊情况
        getRowType().getFieldCount() == 1 // 如果输出行只有一列
            ? Expressions.call(BuiltInMethod.SLICE0.method, interpreter_) // 调用SLICE0方法，将单列结果转换为标量值
            : interpreter_; // 否则直接使用解释器结果
    builder.add(sliced_); // 将切片表达式添加到代码块中，作为最终返回值
    return implementor.result(physType, builder.toBlock()); // 返回实现结果，包含物理类型和生成的代码块
  }
} // 类结束
