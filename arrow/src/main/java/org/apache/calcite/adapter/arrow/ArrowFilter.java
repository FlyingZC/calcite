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
package org.apache.calcite.adapter.arrow; // 定义包名，表示这个类属于Arrow适配器模块

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系代数操作符的集群信息，包含类型工厂等共享对象
import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，用于表示关系代数操作符的执行成本估算
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner类，用于表示查询优化器，负责成本估算和规则应用
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系代数操作符的特征集合，如物理实现方式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，是所有关系代数操作符的基类，表示查询计划中的一个节点
import org.apache.calcite.rel.core.Filter; // 导入Filter类，表示过滤操作符的抽象实现，继承自SingleRel
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系代数操作符的元数据信息
import org.apache.calcite.rex.RexNode; // 导入RexNode类，表示行表达式，用于描述过滤条件等表达式

import java.util.List; // 导入List接口，用于存储匹配条件字符串列表

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于参数非空校验

/**
 * Implementation of a {@link org.apache.calcite.rel.core.Filter}
 * relational expression in Arrow.
 * Arrow适配器中Filter关系表达式的实现类，继承自Calcite的Filter抽象类并实现ArrowRel接口
 * 这个类负责在Arrow数据源上执行过滤操作，将SQL的WHERE条件转换为Arrow可以理解的过滤表达式
 */
class ArrowFilter extends Filter implements ArrowRel { // 定义ArrowFilter类，继承Filter基类并实现ArrowRel接口，表示Arrow数据源上的过滤操作
  private final List<String> match; // 定义成员变量match，存储过滤条件的字符串表示列表，这些字符串将被传递给Arrow执行引擎

  ArrowFilter(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, RexNode condition) { // 构造方法，创建ArrowFilter实例，参数包括集群信息、特征集、输入关系节点和过滤条件
    super(cluster, traitSet, input, condition); // 调用父类Filter的构造方法，初始化基本的过滤操作属性
    final ArrowTranslator translator = // 创建ArrowTranslator对象，用于将Calcite的Rex表达式转换为Arrow可理解的过滤表达式
        ArrowTranslator.create(cluster.getRexBuilder(), input.getRowType()); // 调用ArrowTranslator的静态工厂方法，传入RexBuilder和输入行类型信息
    this.match = translator.translateMatch(condition); // 调用translator的translateMatch方法，将RexNode条件转换为字符串列表并赋值给match成员变量

    assert getConvention() == ArrowRel.CONVENTION; // 断言当前节点的约定是Arrow约定，确保物理实现方式正确
    assert getConvention() == input.getConvention(); // 断言当前节点的约定与输入节点的约定一致，确保查询计划的约定一致性
  }

  @Override public RelOptCost computeSelfCost(RelOptPlanner planner, RelMetadataQuery mq) { // 重写computeSelfCost方法，计算当前过滤操作的执行成本，参数包括优化器和元数据查询对象
    final RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类的成本计算方法，获取基础成本估算
    return requireNonNull(cost, "cost").multiplyBy(0.1); // 将成本乘以0.1，表示Arrow上的过滤操作成本较低，返回调整后的成本值
  }

  @Override public ArrowFilter copy(RelTraitSet traitSet, RelNode input, RexNode condition) { // 重写copy方法，创建当前过滤操作的副本，参数包括新的特征集、输入节点和条件
    return new ArrowFilter(getCluster(), traitSet, input, condition); // 返回一个新的ArrowFilter实例，使用当前集群和传入的参数
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法，实现过滤操作，将过滤逻辑添加到实现器中，参数是Implementor对象
    implementor.visitInput(0, getInput()); // 调用实现器的visitInput方法，访问第0个输入节点（即过滤操作的输入），处理输入关系
    implementor.addFilters(match); // 调用实现器的addFilters方法，将match成员变量中的过滤条件添加到实现器中，供后续执行使用
  }
}
