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
package org.apache.calcite.rel; // 声明包名，表示此类属于 org.apache.calcite.rel 包，是 Calcite 关系代数框架的核心包

import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，表示关系表达式的集群，包含优化器上下文信息
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系表达式的特征集合，如物理属性、排序属性等
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery 类，用于查询关系表达式的元数据信息，如行数、唯一键等
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 类，表示关系数据的类型信息，即行的结构定义

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList 类，用于创建不可变列表

import java.util.List; // 导入 Java 标准库的 List 接口，用于表示列表集合

/**
 * Abstract base class for relational expressions with a single input. // 单输入关系表达式的抽象基类
 *
 * <p>It is not required that single-input relational expressions use this // 单输入关系表达式不必须使用此类作为基类
 * class as a base class. However, default implementations of methods make life // 但默认的方法实现可以简化开发工作
 * easier.
 */
public abstract class SingleRel extends AbstractRelNode { // 定义抽象类 SingleRel，继承自 AbstractRelNode，表示只有一个输入的关系表达式
  //~ Instance fields -------------------------------------------------------- // 实例字段区域标记

  protected RelNode input; // 声明受保护的成员变量 input，类型为 RelNode，表示此关系表达式的唯一输入节点

  //~ Constructors ----------------------------------------------------------- // 构造方法区域标记

  /**
   * Creates a <code>SingleRel</code>. // 创建一个 SingleRel 实例
   *
   * @param cluster Cluster this relational expression belongs to // 参数 cluster 表示此关系表达式所属的集群，包含优化器等上下文信息
   * @param input   Input relational expression // 参数 input 表示输入的关系表达式，即此节点的子节点
   */
  protected SingleRel( // 定义受保护的构造方法，用于创建 SingleRel 实例
      RelOptCluster cluster, // 参数 cluster：关系表达式集群，提供优化器上下文
      RelTraitSet traits, // 参数 traits：关系表达式特征集合，定义物理实现属性
      RelNode input) { // 参数 input：输入的关系表达式节点
    super(cluster, traits); // 调用父类 AbstractRelNode 的构造方法，初始化集群和特征集
    this.input = input; // 将传入的 input 参数赋值给成员变量 input，保存对子节点的引用
  }

  //~ Methods ---------------------------------------------------------------- // 方法区域标记

  public RelNode getInput() { // 定义公共方法 getInput，用于获取此关系表达式的输入节点
    return input; // 返回成员变量 input，即唯一的关系表达式子节点
  }

  @Override public List<RelNode> getInputs() { // 重写父类方法 getInputs，返回所有输入节点的列表
    return ImmutableList.of(input); // 使用 Guava 的 ImmutableList 创建包含 input 的不可变列表并返回
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写父类方法 estimateRowCount，估算此关系表达式的行数
    // Not necessarily correct, but a better default than AbstractRelNode's 1.0 // 这个估算不一定准确，但比父类 AbstractRelNode 返回 1.0 的默认值更好
    return mq.getRowCount(input); // 使用 RelMetadataQuery 查询输入节点的行数并返回，作为当前节点行数的估算
  }

  @Override public void childrenAccept(RelVisitor visitor) { // 重写父类方法 childrenAccept，让访问者访问子节点
    visitor.visit(input, 0, this); // 调用访问者的 visit 方法，传入输入节点、序号 0 和当前节点 this
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写父类方法 explainTerms，用于生成关系表达式的解释信息
    return super.explainTerms(pw) // 调用父类的 explainTerms 方法获取基础解释信息
        .input("input", getInput()); // 调用 input 方法添加输入节点的信息到解释中，键名为 "input"
  }

  @Override public void replaceInput( // 重写父类方法 replaceInput，用于替换指定位置的输入节点
      int ordinalInParent, // 参数 ordinalInParent：输入节点在父节点中的序号
      RelNode rel) { // 参数 rel：新的关系表达式节点，用于替换旧的输入
    assert ordinalInParent == 0; // 断言 ordinalInParent 必须为 0，因为 SingleRel 只有一个输入
    this.input = rel; // 将新的关系表达式节点 rel 赋值给成员变量 input，完成替换
    recomputeDigest(); // 调用 recomputeDigest 方法重新计算此关系表达式的摘要信息，用于缓存失效等
  }

  @Override protected RelDataType deriveRowType() { // 重写父类方法 deriveRowType，用于推导此关系表达式的行类型
    return input.getRowType(); // 直接返回输入节点的行类型，因为单输入节点通常保持相同的输出行结构
  }
}
