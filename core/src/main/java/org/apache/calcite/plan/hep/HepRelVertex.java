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
package org.apache.calcite.plan.hep; // 声明包名，org.apache.calcite.plan.hep是HepPlanner（启发式优化器）相关的包

import org.apache.calcite.plan.RelOptCost; // 导入RelOptCost类，用于表示关系代数操作的成本
import org.apache.calcite.plan.RelOptPlanner; // 导入RelOptPlanner接口，关系代数优化器的基类
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，用于表示关系代数节点的特征集合
import org.apache.calcite.rel.AbstractRelNode; // 导入AbstractRelNode抽象类，RelNode的抽象实现
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数表达式中的节点
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系代数表达式写入输出流
import org.apache.calcite.rel.metadata.DelegatingMetadataRel; // 导入DelegatingMetadataRel接口，表示元数据委托的RelNode
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系代数节点的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系代数节点的数据类型

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可为null的参数或返回值

import java.util.List; // 导入List接口，Java集合框架的列表接口

import static com.google.common.base.Preconditions.checkArgument; // 导入checkArgument方法，用于检查参数是否满足条件

import static java.util.Objects.requireNonNull; // 导入requireNonNull方法，用于检查对象是否为null

/**
 * HepRelVertex wraps a real {@link RelNode} as a vertex in a DAG representing
 * the entire query expression.
 * HepRelVertex将一个真实的RelNode包装成一个顶点，该顶点位于表示整个查询表达式的有向无环图(DAG)中。
 * 
 * HepPlanner（启发式优化器）使用HepRelVertex来表示查询表达式中的每个节点。
 * 与传统的基于Volcano/Cascades模型的优化器不同，HepPlanner使用图结构来表示查询表达式，
 * 每个HepRelVertex都是图中的一个顶点，可以包含多个等价的RelNode实现。
 * 
 * 核心功能：
 * 1. 包装RelNode：将实际的RelNode包装成图中的顶点，便于在图结构中进行操作
 * 2. 委托模式：将大部分操作委托给内部包装的RelNode执行
 * 3. 元数据委托：实现DelegatingMetadataRel接口，将元数据查询委托给内部RelNode
 * 4. 替换实现：支持在不改变图结构的情况下替换顶点的实现（通过replaceRel方法）
 * 
 * 设计模式：
 * - 装饰器模式：HepRelVertex包装RelNode，添加图顶点的语义
 * - 委托模式：大部分方法直接委托给内部的currentRel执行
 * 
 * 使用场景：
 * - HepPlanner在进行规则匹配和转换时，操作的是HepRelVertex而不是直接的RelNode
 * - 支持在优化过程中多次替换顶点的实现，而不需要重建整个图结构
 * - 提供统一的接口访问不同等价的RelNode实现
 */
public class HepRelVertex extends AbstractRelNode implements DelegatingMetadataRel { // HepRelVertex类定义，继承AbstractRelNode并实现DelegatingMetadataRel接口
  //~ Instance fields --------------------------------------------------------
  // ~ Instance fields标记，用于分隔成员变量部分（这是Calcite源码的注释风格）

  /**
   * Wrapped rel currently chosen for implementation of expression.
   * 当前被选择用于实现该表达式的包装的RelNode。
   * 
   * 这个字段是HepRelVertex的核心，它保存了该顶点当前使用的RelNode实现。
   * 在优化过程中，同一个HepRelVertex可能对应多个等价的RelNode实现，
   * currentRel始终指向当前被选中的实现。
   * 
   * 特点：
   * - 可以通过replaceRel方法替换为新的实现
   * - 所有操作（explain、cost计算、元数据查询等）都委托给currentRel
   * - 在构造函数中被初始化，之后可以被多次替换
   * - 不能为null（构造函数中有requireNonNull检查）
   * - 不能是HepRelVertex类型（防止嵌套包装）
   * 
   * 生命周期：
   * 1. 构造时：通过构造函数参数传入并初始化
   * 2. 优化过程中：可能被replaceRel方法替换为新的等价实现
   * 3. 使用时：所有方法调用都委托给currentRel
   */
  private RelNode currentRel; // 私有成员变量，存储当前包装的RelNode实现

  //~ Constructors -----------------------------------------------------------
  // ~ Constructors标记，用于分隔构造函数部分

  HepRelVertex(RelNode rel) { // 构造函数，接收一个RelNode参数rel，创建HepRelVertex实例
    super(rel.getCluster(), rel.getTraitSet()); // 调用父类AbstractRelNode的构造函数，传入rel的Cluster和TraitSet
    currentRel = requireNonNull(rel, "rel"); // 初始化currentRel字段，使用requireNonNull确保rel不为null，否则抛出NullPointerException
    checkArgument(!(rel instanceof HepRelVertex)); // 检查rel不能是HepRelVertex类型，防止嵌套包装，否则抛出IllegalArgumentException
  } // 构造函数结束

  //~ Methods ----------------------------------------------------------------
  // ~ Methods标记，用于分隔方法部分

  @Override public void explain(RelWriter pw) { // 重写explain方法，用于将关系代数表达式写入RelWriter输出流
    currentRel.explain(pw); // 委托给内部包装的currentRel执行explain操作，输出该RelNode的执行计划
  } // explain方法结束

  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 重写copy方法，用于复制RelNode，接收新的特征集和输入列表
    assert traitSet.equals(this.traitSet); // 断言新的traitSet必须等于当前traitSet，HepRelVertex不支持修改特征集
    assert inputs.equals(this.getInputs()); // 断言新的inputs必须等于当前inputs，HepRelVertex不支持修改输入
    return this; // 返回this，因为HepRelVertex是不可变的，不支持真正的复制操作
  } // copy方法结束

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写computeSelfCost方法，计算自身的成本，接收优化器和元数据查询对象作为参数
      RelMetadataQuery mq) { // 参数RelMetadataQuery mq，用于查询元数据
    // HepRelMetadataProvider is supposed to intercept this
    // HepRelMetadataProvider应该拦截这个方法调用
    // and redirect to the real rels. But sometimes it doesn't.
    // 并重定向到真实的RelNode。但有时它不会拦截。
    return planner.getCostFactory().makeTinyCost(); // 返回一个极小的成本值，因为HepRelVertex本身不执行实际操作，成本应该由内部的currentRel计算
  } // computeSelfCost方法结束

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写estimateRowCount方法，估算输出行数，接收元数据查询对象作为参数
    return mq.getRowCount(currentRel); // 委托给元数据查询对象，查询内部currentRel的行数并返回
  } // estimateRowCount方法结束

  @Override protected RelDataType deriveRowType() { // 重写deriveRowType方法，推导输出行的数据类型
    return currentRel.getRowType(); // 返回内部currentRel的行类型
  } // deriveRowType方法结束

  /**
   * Replaces the implementation for this expression with a new one.
   * 用一个新的实现替换当前表达式的实现。
   * 
   * 这个方法是HepRelVertex的核心功能之一，允许在优化过程中替换顶点的实现，
   * 而不需要改变图的结构。这是HepPlanner能够高效进行规则匹配和转换的关键。
   * 
   * 使用场景：
   * 1. 规则匹配成功后，将旧的RelNode替换为转换后的新RelNode
   * 2. 在优化过程中发现更好的等价实现，进行替换
   * 3. 在多次规则应用过程中，逐步改进查询计划
   * 
   * 特点：
   * - 包访问权限（包级私有），只有HepPlanner包内的类可以调用
   * - 直接修改currentRel字段，不创建新的HepRelVertex对象
   * - 不改变图结构，只改变顶点的实现
   * - 不做类型检查，调用者需要确保newRel是合法的
   * 
   * @param newRel new expression
   * @param newRel 新的RelNode表达式，将替换当前的currentRel
   */
  void replaceRel(RelNode newRel) { // replaceRel方法，用新的RelNode替换当前的currentRel
    currentRel = newRel; // 将currentRel字段设置为newRel，完成替换
  } // replaceRel方法结束

  /**
   * Returns current implementation chosen for this vertex.
   * 返回当前为该顶点选择的实现。
   * 
   * 这个方法提供了对内部currentRel的访问接口，允许外部获取当前选中的RelNode实现。
   * 
   * 使用场景：
   * 1. HepPlanner在规则匹配时，需要访问顶点的当前实现
   * 2. 在元数据查询时，需要委托给实际的RelNode
   * 3. 在输出执行计划时，需要显示实际的RelNode
   * 
   * 返回值：
   * - 返回当前的currentRel，永远不会为null
   * - 返回的RelNode是实际执行查询计划的节点
   * 
   * @return 当前的RelNode实现
   */
  public RelNode getCurrentRel() { // getCurrentRel方法，返回当前包装的RelNode
    return currentRel; // 返回currentRel字段
  } // getCurrentRel方法结束

  @Override public RelNode stripped() { // 重写stripped方法，返回剥离了包装的RelNode
    return currentRel; // 返回内部的currentRel，剥离HepRelVertex的包装
  } // stripped方法结束

  /**
   * Returns {@link RelNode} for metadata.
   * 返回用于元数据的RelNode。
   * 
   * 这个方法实现了DelegatingMetadataRel接口，用于元数据查询的委托。
   * 当查询HepRelVertex的元数据时，实际查询的是内部currentRel的元数据。
   * 
   * 设计目的：
   * - HepRelVertex本身不存储元数据，所有元数据都由内部的RelNode提供
   * - 通过委托模式，避免在HepRelVertex中重复实现元数据查询逻辑
   * - 确保元数据的一致性，因为查询的是实际RelNode的元数据
   * 
   * 元数据类型包括：
   * - 行数（rowCount）
   * - 成本（cost）
   * - 唯一键（uniqueKeys）
   * - 分布信息（distribution）
   * - 排序信息（collation）等
   * 
   * @return 用于元数据查询的RelNode，即currentRel
   */
  @Override public RelNode getMetadataDelegateRel() { // 重写getMetadataDelegateRel方法，返回元数据委托的RelNode
    return currentRel; // 返回currentRel，将元数据查询委托给它
  } // getMetadataDelegateRel方法结束

  @Override public boolean deepEquals(@Nullable Object obj) { // 重写deepEquals方法，深度比较两个HepRelVertex是否相等，接收一个可能为null的对象
    return this == obj // 首先检查是否是同一个对象引用，如果是则直接返回true
        || (obj instanceof HepRelVertex // 如果不是同一个对象，检查obj是否是HepRelVertex类型
            && currentRel == ((HepRelVertex) obj).currentRel); // 如果是HepRelVertex，比较两者的currentRel是否是同一个对象引用
  } // deepEquals方法结束

  @Override public int deepHashCode() { // 重写deepHashCode方法，计算HepRelVertex的深度哈希码
    return currentRel.getId(); // 返回内部currentRel的ID作为哈希码，确保相同的RelNode有相同的哈希码
  } // deepHashCode方法结束

  @Override public String getDigest() { // 重写getDigest方法，返回该RelNode的摘要信息，用于调试和日志输出
    return "HepRelVertex(" + currentRel + ')'; // 返回格式为"HepRelVertex(" + currentRel + ")"的字符串，显示包装的RelNode
  } // getDigest方法结束
} // HepRelVertex类定义结束
