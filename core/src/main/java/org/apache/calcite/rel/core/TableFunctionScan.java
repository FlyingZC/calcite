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
 */ // Apache许可证声明，允许在符合Apache 2.0许可证的条件下使用、修改和分发此代码
package org.apache.calcite.rel.core; // 定义包名，该类位于calcite框架的rel.core包中，表示关系代数核心类

import org.apache.calcite.linq4j.Ord; // 导入Ord类，用于为集合元素提供索引，支持带序号的元素访问
import org.apache.calcite.plan.RelOptCluster; // 导入RelOptCluster类，表示关系表达式优化集群，包含所有共享的优化信息
import org.apache.calcite.plan.RelTraitSet; // 导入RelTraitSet类，表示关系表达式的特征集合，如排序、分区等物理属性
import org.apache.calcite.rel.AbstractRelNode; // 导入AbstractRelNode类，所有关系表达式的抽象基类，提供通用功能
import org.apache.calcite.rel.RelInput; // 导入RelInput类，用于从序列化输入中重建关系表达式
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，关系表达式的基本接口，表示查询树中的一个节点
import org.apache.calcite.rel.RelWriter; // 导入RelWriter接口，用于将关系表达式写入输出流，用于调试和解释
import org.apache.calcite.rel.hint.Hintable; // 导入Hintable接口，表示支持提示(Hint)的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入RelHint类，表示关系表达式的优化提示
import org.apache.calcite.rel.metadata.RelColumnMapping; // 导入RelColumnMapping类，表示列映射信息，用于描述输出列与输入列的关系
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入RelMetadataQuery类，用于查询关系表达式的元数据，如行数、统计信息等
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType类，表示关系数据类型，描述表或表达式的类型结构
import org.apache.calcite.rex.RexNode; // 导入RexNode类，行表达式的抽象基类，表示表达式树中的节点
import org.apache.calcite.rex.RexShuttle; // 导入RexShuttle类，用于遍历和转换RexNode表达式树

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，提供不可变的列表实现
import com.google.common.collect.ImmutableSet; // 导入Google Guava的ImmutableSet类，提供不可变的集合实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的字段或方法返回值

import java.lang.reflect.Type; // 导入Java反射的Type接口，表示Java类型
import java.util.ArrayList; // 导入ArrayList类，提供动态数组实现
import java.util.Collections; // 导入Collections类，提供集合操作的工具方法
import java.util.List; // 导入List接口，表示有序集合
import java.util.Set; // 导入Set接口，表示不重复元素的集合

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法，用于检查对象非null

/**
 * Relational expression that calls a table-valued function.
 * 关系表达式，用于调用表值函数(Table-Valued Function, TVF)
 *
 * <p>The function returns a result set.
 * 该函数返回一个结果集，类似于表的结构
 * It can appear as a leaf in a query tree,
 * 它可以作为查询树的叶子节点出现，表示从表值函数获取数据
 * or can be applied to relational inputs.
 * 或者可以应用于关系输入，将关系表达式作为参数传递给表值函数
 *
 * @see org.apache.calcite.rel.logical.LogicalTableFunctionScan
 * 参见LogicalTableFunctionScan类，这是TableFunctionScan的逻辑实现
 */
public abstract class TableFunctionScan extends AbstractRelNode
    implements Hintable { // 声明TableFunctionScan为抽象类，继承AbstractRelNode，实现Hintable接口以支持优化提示
  //~ Instance fields --------------------------------------------------------
  // 实例字段部分：定义了TableFunctionScan类的所有成员变量

  private final RexNode rexCall; // 表值函数的调用表达式，使用RexNode表示函数调用，包含函数名和参数

  private final @Nullable Type elementType; // 实现此表的集合的元素类型，使用Java反射Type表示，可能为null表示未知类型

  private ImmutableList<RelNode> inputs; // 输入关系表达式列表，表值函数可以接受0个或多个关系表达式作为输入参数

  protected final @Nullable ImmutableSet<RelColumnMapping> columnMappings; // 列映射集合，描述输出列与输入列之间的映射关系，可能为null表示未知映射

  protected final ImmutableList<RelHint> hints; // 优化提示列表，包含应用于此节点的所有优化提示，用于影响查询优化器的决策

  //~ Constructors -----------------------------------------------------------
  // 构造方法部分：定义了创建TableFunctionScan实例的各种方式

  /**
   * Creates a <code>TableFunctionScan</code>.
   * 创建一个TableFunctionScan实例，这是最完整的构造方法，包含所有参数
   *
   * @param cluster        Cluster that this relational expression belongs to
   *                       此关系表达式所属的集群，包含优化器共享的上下文信息
   * @param inputs         0 or more relational inputs
   *                       0个或多个关系输入，作为表值函数的参数
   * @param hints          hints of this node.
   *                       此节点的优化提示列表，用于指导优化器的决策
   * @param traitSet       Trait set
   *                       特征集合，定义此关系表达式的物理属性，如排序、分区等
   * @param rexCall        Function invocation expression
   *                       函数调用表达式，使用RexNode表示，包含函数名和参数
   * @param elementType    Element type of the collection that will implement
   *                       this table
   *                       实现此表的集合的元素类型，使用Java反射Type表示
   * @param rowType        Row type produced by function
   *                       函数产生的行类型，描述输出表的结构和字段类型
   * @param columnMappings Column mappings associated with this function
   *                       与此函数关联的列映射集合，描述输出列与输入列的映射关系
   */
  protected TableFunctionScan(
      RelOptCluster cluster, // 关系优化集群参数
      RelTraitSet traitSet, // 特征集合参数
      List<RelHint> hints, // 优化提示列表参数
      List<RelNode> inputs, // 关系输入列表参数
      RexNode rexCall, // 函数调用表达式参数
      @Nullable Type elementType, // 元素类型参数，可能为null
      RelDataType rowType, // 行类型参数
      @Nullable Set<RelColumnMapping> columnMappings) { // 列映射集合参数，可能为null
    super(cluster, traitSet); // 调用父类AbstractRelNode的构造方法，初始化集群和特征集合
    this.rexCall = rexCall; // 保存函数调用表达式到成员变量
    this.elementType = elementType; // 保存元素类型到成员变量
    this.rowType = rowType; // 保存行类型到父类的成员变量
    this.inputs = ImmutableList.copyOf(inputs); // 创建输入列表的不可变副本并保存
    this.columnMappings = // 处理列映射集合
        columnMappings == null ? null : ImmutableSet.copyOf(columnMappings); // 如果columnMappings为null则保存null，否则创建不可变副本
    this.hints = ImmutableList.copyOf(hints); // 创建优化提示列表的不可变副本并保存
  }

  /**
   * Creates a <code>TableFunctionScan</code>.
   * 创建一个TableFunctionScan实例，这是简化版本的构造方法，不包含hints参数
   *
   * @param cluster        Cluster that this relational expression belongs to
   *                       此关系表达式所属的集群，包含优化器共享的上下文信息
   * @param inputs         0 or more relational inputs
   *                       0个或多个关系输入，作为表值函数的参数
   * @param traitSet       Trait set
   *                       特征集合，定义此关系表达式的物理属性
   * @param rexCall        Function invocation expression
   *                       函数调用表达式，使用RexNode表示
   * @param elementType    Element type of the collection that will implement
   *                       this table
   *                       实现此表的集合的元素类型
   * @param rowType        Row type produced by function
   *                       函数产生的行类型
   * @param columnMappings Column mappings associated with this function
   *                       与此函数关联的列映射集合
   */
  protected TableFunctionScan(
      RelOptCluster cluster, // 关系优化集群参数
      RelTraitSet traitSet, // 特征集合参数
      List<RelNode> inputs, // 关系输入列表参数
      RexNode rexCall, // 函数调用表达式参数
      @Nullable Type elementType, // 元素类型参数，可能为null
      RelDataType rowType, // 行类型参数
      @Nullable Set<RelColumnMapping> columnMappings) { // 列映射集合参数，可能为null
    this(cluster, traitSet, ImmutableList.of(), inputs, rexCall, // 调用完整构造方法，hints参数传入空列表
        elementType, rowType, columnMappings);
  }

  /**
   * Creates a TableFunctionScan by parsing serialized output.
   * 通过解析序列化输出来创建TableFunctionScan实例，用于从持久化格式重建对象
   */
  protected TableFunctionScan(RelInput input) { // 接受RelInput参数，包含序列化的关系表达式信息
    this( // 调用完整构造方法
        input.getCluster(), input.getTraitSet(), // 从RelInput获取集群和特征集合
        Collections.emptyList(), input.getInputs(), // 创建空的hints列表并获取输入列表
        requireNonNull(input.getExpression("invocation"), "invocation"), // 获取invocation表达式，确保非null
        (Type) input.get("elementType"), // 获取elementType并转换为Type类型
        input.getRowType("rowType"), // 获取rowType
        ImmutableSet.of()); // 创建空的列映射集合
  }

  //~ Methods ----------------------------------------------------------------
  // 方法部分：定义了TableFunctionScan类的所有方法

  @Override public final TableFunctionScan copy(RelTraitSet traitSet,
      List<RelNode> inputs) { // 重写父类的copy方法，创建此关系表达式的副本，只修改特征集合和输入
    return copy(traitSet, inputs, rexCall, elementType, getRowType(), // 调用完整的copy方法，保持其他参数不变
        columnMappings);
  }

  /**
   * Copies this relational expression, substituting traits and
   * inputs.
   * 复制此关系表达式，可以替换特征集合、输入、函数调用等所有参数
   *
   * @param traitSet       Traits
   *                       新的特征集合，定义副本的物理属性
   * @param inputs         0 or more relational inputs
   *                       新的输入列表，作为表值函数的参数
   * @param rexCall        Function invocation expression
   *                       新的函数调用表达式
   * @param elementType    Element type of the collection that will implement
   *                       this table
   *                       新的元素类型
   * @param rowType        Row type produced by function
   *                       新的行类型
   * @param columnMappings Column mappings associated with this function
   *                       新的列映射集合
   * @return Copy of this relational expression, substituting traits and
   * inputs
   *       返回此关系表达式的副本，使用指定的参数替换原有参数
   */
  public abstract TableFunctionScan copy( // 抽象方法，子类必须实现，用于创建副本
      RelTraitSet traitSet, // 新的特征集合参数
      List<RelNode> inputs, // 新的输入列表参数
      RexNode rexCall, // 新的函数调用表达式参数
      @Nullable Type elementType, // 新的元素类型参数，可能为null
      RelDataType rowType, // 新的行类型参数
      @Nullable Set<RelColumnMapping> columnMappings); // 新的列映射集合参数，可能为null

  @Override public List<RelNode> getInputs() { // 重写父类的getInputs方法，获取输入关系表达式列表
    return inputs; // 返回输入列表
  }

  @Override public RelNode accept(RexShuttle shuttle) { // 重写父类的accept方法，接受RexShuttle访问器来转换表达式树
    RexNode rexCall = shuttle.apply(this.rexCall); // 使用shuttle访问器处理函数调用表达式，可能返回修改后的表达式
    if (rexCall == this.rexCall) { // 如果处理后的表达式与原表达式相同，表示没有修改
      return this; // 返回当前对象，不需要创建副本
    }
    return copy(traitSet, inputs, rexCall, elementType, getRowType(), // 表达式被修改，创建包含新表达式的副本
        columnMappings);
  }

  @Override public void replaceInput(int ordinalInParent, RelNode p) { // 重写父类的replaceInput方法，替换指定位置的关系输入
    final List<RelNode> newInputs = new ArrayList<>(inputs); // 创建输入列表的可变副本
    newInputs.set(ordinalInParent, p); // 将指定位置的关系输入替换为新的RelNode
    inputs = ImmutableList.copyOf(newInputs); // 创建不可变副本并赋值给inputs成员变量
    recomputeDigest(); // 重新计算此关系表达式的摘要，用于缓存和比较
  }

  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写父类的estimateRowCount方法，估算此关系表达式输出的行数
    // Calculate result as the sum of the input row count estimates,
    // 计算结果为输入行数估算的总和
    // assuming there are any, otherwise use the superclass default.  So
    // 假设有输入，否则使用父类的默认值。因此：
    // for a no-input UDX, behave like an AbstractRelNode; for a one-input
    // 对于无输入的UDX(用户定义函数)，行为类似AbstractRelNode；对于单输入的UDX
    // UDX, behave like a SingleRel; for a multi-input UDX, behave like
    // 行为类似SingleRel；对于多输入的UDX，行为类似UNION ALL
    // UNION ALL.  TODO jvs 10-Sep-2007: UDX-supplied costing metadata.
    // TODO: 需要支持UDX提供的成本元数据
    if (inputs.isEmpty()) { // 如果输入列表为空，表示没有关系输入
      return super.estimateRowCount(mq); // 调用父类的默认估算方法
    }
    double nRows = 0.0; // 初始化总行数为0
    for (RelNode input : inputs) { // 遍历所有输入关系表达式
      Double d = mq.getRowCount(input); // 使用元数据查询获取每个输入的估算行数
      if (d != null) { // 如果行数不为null
        nRows += d; // 累加到总行数
      }
    }
    return nRows; // 返回估算的总行数
  }

  /**
   * Returns function invocation expression.
   * 返回函数调用表达式
   *
   * <p>Within this rexCall, instances of
   * 在此rexCall中，RexInputRef的实例引用的是
   * {@link org.apache.calcite.rex.RexInputRef} refer to entire input
   * 整个输入RelNode，而不是它们的字段
   * {@link org.apache.calcite.rel.RelNode}s rather than their fields.
   * 这意味着RexInputRef引用的是整个关系输入，而不是具体的列
   *
   * @return function invocation expression
   *       返回函数调用表达式，使用RexNode表示
   */
  public RexNode getCall() { // 获取表值函数的调用表达式
    return rexCall; // 返回成员变量rexCall
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写父类的explainTerms方法，将此关系表达式的信息写入RelWriter
    super.explainTerms(pw); // 调用父类方法，写入基本信息
    for (Ord<RelNode> ord : Ord.zip(inputs)) { // 遍历输入列表，为每个输入创建带索引的Ord对象
      pw.input("input#" + ord.i, ord.e); // 将每个输入写入RelWriter，键格式为"input#索引"
    }
    pw.item("invocation", rexCall) // 写入函数调用表达式
      .item("rowType", rowType); // 写入行类型
    if (elementType != null) { // 如果elementType不为null
      pw.item("elementType", elementType); // 写入元素类型
    }
    return pw; // 返回RelWriter对象
  }

  /**
   * Returns set of mappings known for this table function, or null if unknown
   * (not the same as empty!).
   * 返回此表函数已知的列映射集合，如果未知则返回null（注意：null与空集合不同！）
   *
   * @return set of mappings known for this table function, or null if unknown
   * (not the same as empty!)
   *       返回此表函数已知的列映射集合，如果未知则返回null（注意：null与空集合不同！）
   */
  public @Nullable Set<RelColumnMapping> getColumnMappings() { // 获取列映射集合
    return columnMappings; // 返回成员变量columnMappings，可能为null
  }

  /**
   * Returns element type of the collection that will implement this table.
   * 返回将实现此表的集合的元素类型
   *
   * @return element type of the collection that will implement this table
   *       返回将实现此表的集合的元素类型，使用Java反射Type表示
   */
  public @Nullable Type getElementType() { // 获取元素类型
    return elementType; // 返回成员变量elementType，可能为null
  }

  @Override public ImmutableList<RelHint> getHints() { // 重写Hintable接口的getHints方法，获取优化提示列表
    return hints; // 返回成员变量hints，包含所有应用于此节点的优化提示
  }
} // 类定义结束
