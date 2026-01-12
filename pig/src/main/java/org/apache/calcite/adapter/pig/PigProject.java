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
// Apache许可证头，声明代码版权和使用许可
package org.apache.calcite.adapter.pig; // 定义包名，该类属于org.apache.calcite.adapter.pig包，是Calcite中Pig适配器的一部分

import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，用于表示关系代数操作的聚类，包含查询优化器、类型工厂等共享资源
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable类，用于表示关系代数中的表对象，包含表的元数据信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系代数节点的特征集合，如调用约定、排序、分布等
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，是Calcite中所有关系代数节点的基类
import org.apache.calcite.rel.core.Project; // 导入Project类，是Calcite中投影操作的抽象实现，用于选择和计算表达式
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，用于表示关系数据类型，包含字段类型和结构信息
import org.apache.calcite.rex.RexNode; // 导入RexNode类，用于表示行表达式，是Calcite中表达式树的基类

import com.google.common.collect.ImmutableList; // 导入Guava库的ImmutableList类，用于创建不可变的列表
import com.google.common.collect.ImmutableSet; // 导入Guava库的ImmutableSet类，用于创建不可变的集合

import java.util.List; // 导入Java标准库的List接口，用于表示有序集合

/** Implementation of {@link org.apache.calcite.rel.core.Project} in
 * {@link PigRel#CONVENTION Pig calling convention}. */ // Javadoc注释：这是Project操作在Pig调用约定下的实现类
public class PigProject extends Project implements PigRel { // PigProject类定义，继承自Project基类并实现PigRel接口，表示Pig适配器中的投影操作

  /** Creates a PigProject. */ // Javadoc注释：创建一个PigProject实例的构造方法
  public PigProject(RelOptCluster cluster, RelTraitSet traitSet, RelNode input, // 构造方法定义，参数包括cluster（关系代数聚类）、traitSet（特征集合）、input（输入关系节点）
      List<? extends RexNode> projects, RelDataType rowType) { // 继续构造方法参数：projects（投影表达式列表）、rowType（输出行类型）
    super(cluster, traitSet, ImmutableList.of(), input, projects, rowType, ImmutableSet.of()); // 调用父类Project的构造方法，传入空列表作为变量，空集合作为标志位
    assert getConvention() == PigRel.CONVENTION; // 断言检查：确保当前节点的调用约定是Pig约定，如果不是则抛出断言错误
  }

  @Override public Project copy(RelTraitSet traitSet, RelNode input, List<RexNode> projects, // 重写copy方法，用于创建当前PigProject节点的副本，参数包括新的特征集合、输入节点、投影表达式列表和行类型
      RelDataType rowType) { // 继续方法参数：rowType（输出行类型）
    return new PigProject(input.getCluster(), traitSet, input, projects, rowType); // 返回一个新的PigProject实例，使用输入节点的聚类、新的特征集合、输入节点、投影表达式和行类型
  }

  @Override public void implement(Implementor implementor) { // 重写implement方法，用于实现将关系代数树转换为Pig脚本，参数implementor是实现器对象
    System.out.println(getTable()); // 打印当前节点操作的表信息，用于调试目的
  }

  /**
   * Override this method so it looks down the tree to find the table this node
   * is acting on.
   */ // Javadoc注释：重写此方法以便在关系代数树中向下查找当前节点操作的表
  @Override public RelOptTable getTable() { // 重写getTable方法，用于获取当前节点操作的表对象
      return getInput().getTable(); // 通过获取输入节点的表来返回当前节点操作的表，因为Project操作本身不直接操作表，而是操作其输入的表
    }
  } // PigProject类结束
