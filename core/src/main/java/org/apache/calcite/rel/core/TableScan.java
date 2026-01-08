/*
 * Licensed to the Apache Software Foundation (ASF) under one or more // Apache 软件基金会许可证声明
 * contributor license agreements.  See the NOTICE file distributed with // 贡献者许可协议，查看随此工作分发的 NOTICE 文件以获取
 * this work for additional information regarding copyright ownership.  // 关于版权所有权的额外信息
 * The ASF licenses this file to you under the Apache License, Version 2.0 // ASF 根据 Apache 许可证 2.0 版本将此文件许可给您
 * (the "License"); you may not use this file except in compliance with // 您只能在遵守许可证的情况下使用此文件
 * the License.  You may obtain a copy of the License at // 您可以在以下地址获取许可证副本
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software // 除非适用法律要求或书面同意，否则根据许可证分发的软件
 * distributed under the License is distributed on an "AS IS" BASIS, // 是按"原样"基础分发的，不附任何明示或暗示的保证或条件
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. // 无论是明示的还是暗示的保证或条件
 * See the License for the specific language governing permissions and // 请参阅许可证以了解特定语言的权限和
 * limitations under the License. // 使用限制
 */
package org.apache.calcite.rel.core; // 声明包名，该类属于 org.apache.calcite.rel.core 包

import org.apache.calcite.plan.RelOptCluster; // 导入 RelOptCluster 类，表示关系表达式集群，包含优化器、类型工厂等共享资源
import org.apache.calcite.plan.RelOptCost; // 导入 RelOptCost 接口，表示关系表达式的成本估算
import org.apache.calcite.plan.RelOptPlanner; // 导入 RelOptPlanner 接口，表示查询优化器
import org.apache.calcite.plan.RelOptSchema; // 导入 RelOptSchema 接口，表示关系模式的集合，包含表等元数据
import org.apache.calcite.plan.RelOptTable; // 导入 RelOptTable 接口，表示优化器视角下的表，包含表的元数据信息
import org.apache.calcite.plan.RelTraitSet; // 导入 RelTraitSet 类，表示关系表达式的特征集合（如物理实现方式、排序等）
import org.apache.calcite.rel.AbstractRelNode; // 导入 AbstractRelNode 抽象类，作为所有关系表达式节点的基类
import org.apache.calcite.rel.RelInput; // 导入 RelInput 接口，用于从序列化输入创建关系表达式
import org.apache.calcite.rel.RelNode; // 导入 RelNode 接口，表示关系代数操作符的节点
import org.apache.calcite.rel.RelShuttle; // 导入 RelShuttle 接口，用于访问和遍历关系表达式树
import org.apache.calcite.rel.RelWriter; // 导入 RelWriter 接口，用于将关系表达式以可读形式输出
import org.apache.calcite.rel.hint.Hintable; // 导入 Hintable 接口，表示可以接受提示的关系表达式
import org.apache.calcite.rel.hint.RelHint; // 导入 RelHint 类，表示关系表达式的提示信息，用于影响优化器的决策
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入 RelMetadataQuery 类，用于查询关系表达式的元数据（如行数、唯一性等）
import org.apache.calcite.rel.type.RelDataType; // 导入 RelDataType 接口，表示关系数据类型（表的结构）
import org.apache.calcite.rel.type.RelDataTypeField; // 导入 RelDataTypeField 接口，表示关系数据类型中的字段（列）
import org.apache.calcite.rex.RexBuilder; // 导入 RexBuilder 类，用于构建行表达式（RexNode）
import org.apache.calcite.rex.RexNode; // 导入 RexNode 接口，表示行表达式，用于在关系代数中计算值
import org.apache.calcite.tools.RelBuilder; // 导入 RelBuilder 类，用于以流畅的 API 构建关系表达式树
import org.apache.calcite.util.ImmutableBitSet; // 导入 ImmutableBitSet 类，表示不可变的位集合，常用于标记使用的字段
import org.apache.calcite.util.ImmutableIntList; // 导入 ImmutableIntList 类，表示不可变的整数列表，常用于表示投影映射

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的 ImmutableList，表示不可变的列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的注解，表示可空类型

import java.util.ArrayList; // 导入 ArrayList 类，表示动态数组
import java.util.List; // 导入 List 接口，表示有序集合
import java.util.Set; // 导入 Set 接口，表示无序不重复集合

import static java.util.Objects.requireNonNull; // 静态导入 Objects.requireNonNull 方法，用于参数非空检查

/**
 * Relational operator that returns the contents of a table. // 关系操作符，用于返回表的内容（扫描表数据）
 * 
 * TableScan 是 Calcite 关系代数中的叶子节点，表示从数据源（如表、视图）读取数据的操作
 * 这是查询执行计划的起点，所有数据流都从 TableScan 开始
 * 该类是抽象类，具体的表扫描实现（如 EnumerableTableScan、LogicalTableScan 等）需要继承此类
 * TableScan 实现了 Hintable 接口，可以接受优化器提示（如使用特定索引、并行度等）
 */
public abstract class TableScan // 定义抽象的 TableScan 类
    extends AbstractRelNode implements Hintable { // 继承 AbstractRelNode，实现 Hintable 接口
  //~ Instance fields -------------------------------------------------------- // 实例字段分隔符

  /**
   * The table definition. // 表的定义，包含表的元数据信息
   * 
   * RelOptTable 是优化器视角下的表，包含：
   * - 表的名称和限定名
   * - 表的行类型（字段列表）
   * - 表的行数统计信息
   * - 表所属的模式（RelOptSchema）
   * - 表的访问接口（如 Table 接口）
   * 
   * 该字段是 final 的，一旦初始化就不能修改
   * 该字段是 protected 的，子类可以直接访问
   */
  protected final RelOptTable table; // 表的定义，存储表的元数据信息

  /**
   * The table hints. // 表的提示信息列表
   * 
   * RelHint 是用于影响优化器决策的提示，例如：
   * - 使用特定索引的提示
   * - 并行度提示
   * - 缓存策略提示
   * - 数据分布提示
   * 
   * hints 是不可变列表，确保线程安全
   * 提示信息可以帮助优化器生成更好的执行计划
   */
  protected final ImmutableList<RelHint> hints; // 表的提示信息列表，用于指导优化器

  //~ Constructors ----------------------------------------------------------- // 构造方法分隔符

  /**
   * 主构造方法，创建 TableScan 实例
   * 
   * @param cluster 关系表达式集群，包含优化器、类型工厂等共享资源，所有关系表达式共享同一个 cluster
   * @param traitSet 关系表达式的特征集合，定义了物理实现方式（如 Convention）、排序、分区等特征
   * @param hints 表的提示信息列表，用于影响优化器的决策，如索引选择、并行度等
   * @param table 表的定义，包含表的元数据信息（名称、类型、行数等）
   */
  protected TableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法，接收集群、特征集、提示和表参数
      List<RelHint> hints, RelOptTable table) { // 提示列表和表定义参数
    super(cluster, traitSet); // 调用父类 AbstractRelNode 的构造方法，初始化集群和特征集
    this.table = requireNonNull(table, "table"); // 使用 requireNonNull 确保 table 参数不为 null，否则抛出 NullPointerException
    RelOptSchema relOptSchema = table.getRelOptSchema(); // 获取表所属的关系模式（RelOptSchema），模式是表的集合
    if (relOptSchema != null) { // 如果表所属的模式不为 null
      cluster.getPlanner().registerSchema(relOptSchema); // 将模式注册到优化器中，使优化器能够访问该模式下的表
    }
    this.hints = ImmutableList.copyOf(hints); // 将提示列表转换为不可变的 ImmutableList，确保线程安全
  }

  /**
   * 已废弃的构造方法，将在 2.0 版本之前移除
   * 
   * 该构造方法不接受 hints 参数，内部使用空列表
   * 建议使用带 hints 参数的构造方法
   * 
   * @param cluster 关系表达式集群
   * @param traitSet 关系表达式的特征集合
   * @param table 表的定义
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在 2.0 版本之前移除
  protected TableScan(RelOptCluster cluster, RelTraitSet traitSet, // 构造方法，接收集群、特征集和表参数
      RelOptTable table) { // 表定义参数
    this(cluster, traitSet, ImmutableList.of(), table); // 调用主构造方法，传入空的提示列表 ImmutableList.of()
  }

  /**
   * 通过解析序列化输出创建 TableScan 实例
   * 
   * 该构造方法用于从序列化的输入（如 JSON、XML）反序列化 TableScan 对象
   * 常用于查询计划的持久化和恢复
   * 
   * @param input 序列化的输入对象，包含 cluster、traitSet 和 table 信息
   */
  /**
   * Creates a TableScan by parsing serialized output. // 通过解析序列化输出创建 TableScan
   */
  protected TableScan(RelInput input) { // 构造方法，接收序列化输入对象
    this(input.getCluster(), input.getTraitSet(), ImmutableList.of(), input.getTable("table")); // 调用主构造方法，从输入中提取集群、特征集和表信息，提示列表为空
  }

  //~ Methods ---------------------------------------------------------------- // 方法分隔符

  /**
   * 估算表的行数
   * 
   * 该方法用于查询优化，优化器根据行数估算来选择最优的执行计划
   * 行数估算影响连接顺序、连接算法选择等优化决策
   * 
   * @param mq 元数据查询对象，用于查询关系表达式的元数据
   * @return 表的行数估算值
   */
  @Override public double estimateRowCount(RelMetadataQuery mq) { // 重写 estimateRowCount 方法，接收元数据查询对象
    return table.getRowCount(); // 返回表的行数，从 RelOptTable 中获取预定义的行数统计信息
  }

  /**
   * 获取表的定义
   * 
   * @return 表的定义（RelOptTable），包含表的元数据信息
   */
  @Override public RelOptTable getTable() { // 重写 getTable 方法
    return table; // 返回表的定义
  }

  /**
   * 计算自身操作的代价
   * 
   * 代价计算是查询优化的核心，优化器根据代价选择最优的执行计划
   * 代价通常包括三个维度：
   * - dRows: 处理的行数
   * - dCpu: CPU 使用量
   * - dIo: I/O 操作量
   * 
   * TableScan 的代价计算：
   * - dRows: 从元数据查询获取行数
   * - dCpu: 行数 + 1（确保非零代价，避免优化器认为操作是免费的）
   * - dIo: 0（假设数据已经在内存中，或者 I/O 代价由其他组件计算）
   * 
   * @param planner 查询优化器，用于创建代价对象
   * @param mq 元数据查询对象，用于查询行数等元数据
   * @return 计算出的代价对象
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 重写 computeSelfCost 方法，接收优化器和元数据查询对象
      RelMetadataQuery mq) { // 元数据查询对象
    double dRows = mq.getRowCount(this); // 从元数据查询中获取此 TableScan 的行数估算
    double dCpu = dRows + 1; // ensure non-zero cost // CPU 代价为行数 + 1，确保代价不为零
    double dIo = 0; // I/O 代价为 0，假设数据已在内存中或 I/O 由其他组件计算
    return planner.getCostFactory().makeCost(dRows, dCpu, dIo); // 使用优化器的代价工厂创建代价对象并返回
  }

  /**
   * 推导行的类型（表的结构）
   * 
   * 行类型描述了表的结构，包括字段名称、字段类型、是否可为空等信息
   * 这是类型推导系统的一部分，确保查询计划中的类型一致性
   * 
   * @return 表的行类型（RelDataType）
   */
  @Override public RelDataType deriveRowType() { // 重写 deriveRowType 方法
    return table.getRowType(); // 返回表的行类型，从 RelOptTable 中获取
  }

  /**
   * 返回给定表的恒等投影映射
   * 
   * 恒等投影是指不改变字段的顺序和数量，即 [0, 1, 2, ..., n-1]
   * 例如：表有 3 个字段，恒等投影为 [0, 1, 2]
   * 
   * 静态方法，可以直接通过类名调用
   * 
   * @param table 表的定义
   * @return 恒等投影映射列表
   */
  /** Returns an identity projection for the given table. */ // 返回给定表的恒等投影
  public static ImmutableIntList identity(RelOptTable table) { // 静态方法，接收表定义
    return ImmutableIntList.identity(table.getRowType().getFieldCount()); // 返回恒等投影列表，长度等于表的字段数
  }

  /**
   * 返回当前表的恒等投影映射
   * 
   * 实例方法，返回当前 TableScan 对象所表示的表的恒等投影
   * 
   * @return 恒等投影映射列表
   */
  /** Returns an identity projection. */ // 返回恒等投影
  public ImmutableIntList identity() { // 实例方法
    return identity(table); // 调用静态 identity 方法，传入当前表定义
  }

  /**
   * 输出关系表达式的可读描述
   * 
   * 该方法用于查询计划的解释和调试，将关系表达式以可读形式输出
   * 默认输出表的全限定名
   * 
   * @param pw 关系表达式写入器，用于构建输出内容
   * @return 关系表达式写入器，支持链式调用
   */
  @Override public RelWriter explainTerms(RelWriter pw) { // 重写 explainTerms 方法，接收关系表达式写入器
    return super.explainTerms(pw) // 调用父类的 explainTerms 方法，输出基础信息
        .item("table", table.getQualifiedName()); // 添加表的全限定名到输出中
  }

  /**
   * 投影表的字段子集，并请求"额外"字段
   * 
   * 该方法用于列裁剪优化，只读取需要的字段，减少 I/O 和内存使用
   * 
   * 默认实现假设表无法直接进行字段裁剪或提供额外字段，因此：
   * 1. 如果需要所有字段且没有额外字段，直接返回 this
   * 2. 否则，添加一个 Project 操作符，将不需要的字段过滤掉，额外字段填充 NULL
   * 
   * 子类（如某些特定数据源的 TableScan）可以重写此方法，直接在数据源层面进行字段裁剪
   * 
   * @param fieldsUsed 位图，标记消费者需要的字段索引（如 {0, 2} 表示需要第 0 和第 2 个字段）
   * @param extraFields 额外字段集合，这些字段不在表的官方类型中，但消费者需要
   * @param relBuilder 关系表达式构建器，用于创建 Project 操作符
   * @return 投影所需字段的关系表达式
   */
  /**
   * Projects a subset of the fields of the table, and also asks for "extra" // 投影表的字段子集，并请求"额外"字段
   * fields that were not included in the table's official type. // 这些额外字段未包含在表的官方类型中
   *
   * <p>The default implementation assumes that tables cannot do either of // 默认实现假设表无法直接进行字段裁剪或提供额外字段
   * these operations, therefore it adds a {@link Project} that projects // 因此添加一个 Project 操作符
   * {@code NULL} values for the extra fields, using the // 为额外字段投影 NULL 值
   * {@link RelBuilder#project(Iterable)} method. // 使用 RelBuilder.project 方法
   *
   * <p>Sub-classes, representing table types that have these capabilities, // 子类如果支持这些功能（如某些特定数据源）
   * should override. // 应该重写此方法
   *
   * @param fieldsUsed  Bitmap of the fields desired by the consumer // 位图，标记消费者需要的字段
   * @param extraFields Extra fields, not advertised in the table's row-type, // 额外字段，不在表的行类型中声明
   *                    wanted by the consumer // 但消费者需要
   * @param relBuilder Builder used to create a Project // 用于创建 Project 操作符的构建器
   * @return Relational expression that projects the desired fields // 投影所需字段的关系表达式
   */
  public RelNode project(ImmutableBitSet fieldsUsed, // project 方法，接收使用的字段位图
      Set<RelDataTypeField> extraFields, // 额外字段集合
      RelBuilder relBuilder) { // 关系表达式构建器
    final int fieldCount = getRowType().getFieldCount(); // 获取表的字段总数
    if (fieldsUsed.equals(ImmutableBitSet.range(fieldCount)) // 如果使用的字段等于所有字段（即需要所有字段）
        && extraFields.isEmpty()) { // 且没有额外字段
      return this; // 直接返回 this，不需要添加 Project 操作符
    }
    int fieldSize = fieldsUsed.size() + extraFields.size(); // 计算最终投影的字段总数（使用的字段 + 额外字段）
    final List<RexNode> exprList = new ArrayList<>(fieldSize); // 创建表达式列表，用于存储投影表达式
    final List<String> nameList = new ArrayList<>(fieldSize); // 创建名称列表，用于存储字段名称
    final RexBuilder rexBuilder = getCluster().getRexBuilder(); // 获取 RexBuilder，用于构建行表达式
    final List<RelDataTypeField> fields = getRowType().getFieldList(); // 获取表的字段列表

    // Project the subset of fields. // 投影使用的字段子集
    for (int i : fieldsUsed) { // 遍历使用的字段索引
      RelDataTypeField field = fields.get(i); // 获取字段对象
      exprList.add(rexBuilder.makeInputRef(this, i)); // 创建输入引用表达式，引用当前 TableScan 的第 i 个字段
      nameList.add(field.getName()); // 添加字段名称到名称列表
    }

    // Project nulls for the extra fields. (Maybe a sub-class table has // 为额外字段投影 NULL 值（子类表可能有额外字段，但当前表没有）
    // extra fields, but we don't.) // 但我们没有）
    for (RelDataTypeField extraField : extraFields) { // 遍历额外字段
      exprList.add(rexBuilder.makeNullLiteral(extraField.getType())); // 创建 NULL 字面量表达式，类型与额外字段一致
      nameList.add(extraField.getName()); // 添加额外字段名称到名称列表
    }

    return relBuilder.push(this).project(exprList, nameList).build(); // 使用 relBuilder 构建投影：push 当前节点 -> project 表达式和名称 -> build 构建关系表达式
  }

  /**
   * 接受关系表达式访问器
   * 
   * 该方法是访问者模式的一部分，用于遍历和修改关系表达式树
   * RelShuttle 可以访问树中的每个节点，并进行转换或收集信息
   * 
   * @param shuttle 关系表达式访问器
   * @return 访问后的关系表达式（可能是转换后的新节点）
   */
  @Override public RelNode accept(RelShuttle shuttle) { // 重写 accept 方法，接收关系表达式访问器
    return shuttle.visit(this); // 调用访问器的 visit 方法，传入当前 TableScan 对象
  }

  /**
   * 获取表的提示信息列表
   * 
   * 提示信息用于影响优化器的决策，如索引选择、并行度、缓存策略等
   * 
   * @return 提示信息的不可变列表
   */
  @Override public ImmutableList<RelHint> getHints() { // 重写 getHints 方法
    return hints; // 返回提示信息列表
  }
}
