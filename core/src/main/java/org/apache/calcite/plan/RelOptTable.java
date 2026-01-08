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
package org.apache.calcite.plan; // 声明包名，该类位于 org.apache.calcite.plan 包下，是 Calcite 优化器计划包的核心接口

import org.apache.calcite.linq4j.tree.Expression; // 导入 LINQ 表达式类，用于代码生成，支持将表转换为可执行的表达式
import org.apache.calcite.rel.RelCollation; // 导入关系排序类，描述表的物理排序属性（如升序、降序）
import org.apache.calcite.rel.RelDistribution; // 导入关系分布类，描述表的物理分布属性（如分布式、分区）
import org.apache.calcite.rel.RelNode; // 导入关系节点类，代表关系代数表达式树中的节点
import org.apache.calcite.rel.RelReferentialConstraint; // 导入引用约束类，表示外键约束关系
import org.apache.calcite.rel.RelRoot; // 导入关系根节点类，代表完整的关系表达式树
import org.apache.calcite.rel.hint.RelHint; // 导入关系提示类，用于向优化器传递优化提示信息
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入关系元数据查询类，用于查询表的元数据信息
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型类，描述表的行类型结构
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段类，描述表中的列信息
import org.apache.calcite.schema.ColumnStrategy; // 导入列策略类，描述列的填充策略（如默认值、虚拟列等）
import org.apache.calcite.schema.Wrapper; // 导入包装器接口，支持类型转换和包装
import org.apache.calcite.util.ImmutableBitSet; // 导入不可变位集合类，用于高效表示列索引集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为 null 的返回值

import java.util.List; // 导入 Java 集合框架的 List 接口，用于存储有序集合

/**
 * Represents a relational dataset in a {@link RelOptSchema}. It has methods to
 * describe and implement itself.
 * 表示关系优化模式（RelOptSchema）中的一个关系数据集（表）。它提供了描述和实现自身的方法。
 * 这个接口是 Calcite 优化器中表的核心抽象，定义了表在查询优化过程中需要提供的所有信息和方法。
 * 优化器通过这个接口获取表的元数据、统计信息、约束条件等，并将表转换为关系表达式树（RelNode）进行优化。
 */
public interface RelOptTable extends Wrapper { // 定义 RelOptTable 接口，继承自 Wrapper 接口，Wrapper 允许将表包装为其他类型
  //~ Methods ---------------------------------------------------------------- // 方法部分的分隔标记，表示以下是方法定义区域

  /**
   * Obtains an identifier for this table. The identifier must be unique with
   * respect to the Connection producing this table.
   * 获取此表的标识符（限定名）。该标识符在生成此表的连接中必须是唯一的。
   * 限定名通常是一个字符串列表，表示表的完整路径，例如 ["catalog", "schema", "tableName"]。
   * 优化器使用这个标识符来唯一识别表，避免表名冲突。
   *
   * @return qualified name 返回表的限定名，类型为字符串列表
   */
  List<String> getQualifiedName(); // 声明获取表限定名的方法，返回包含完整路径的字符串列表

  /**
   * Returns an estimate of the number of rows in the table.
   * 返回表中行数的估计值。这个估计值用于查询优化器的成本计算。
   * 优化器根据行数估计来选择最优的执行计划，例如决定是否使用索引、选择连接顺序等。
   * 这是一个估计值，不是精确值，通常来自统计信息。
   *
   * @return double 返回行数的估计值，类型为 double
   */
  double getRowCount(); // 声明获取表行数估计的方法，返回 double 类型的行数

  /**
   * Describes the type of rows returned by this table.
   * 描述此表返回的行的类型。行类型包含了表的所有列信息，包括列名、数据类型等。
   * 这是表结构的核心定义，优化器通过这个信息了解表的 schema，进行类型检查和转换。
   * RelDataType 是 Calcite 中描述关系数据类型的抽象，可以包含嵌套类型、数组类型等复杂类型。
   *
   * @return RelDataType 返回表的行类型，包含所有列的类型信息
   */
  RelDataType getRowType(); // 声明获取表行类型的方法，返回 RelDataType 对象

  /**
   * Returns the {@link RelOptSchema} this table belongs to.
   * 返回此表所属的关系优化模式（RelOptSchema）。
   * RelOptSchema 是表的集合，代表一个 schema 或 catalog。
   * 通过这个方法，表可以知道它属于哪个 schema，从而可以访问同一 schema 中的其他表。
   * 这对于解析引用关系、处理外键约束等非常重要。
   *
   * @return RelOptSchema 返回表所属的 RelOptSchema 对象，可能为 null
   */
  @Nullable RelOptSchema getRelOptSchema(); // 声明获取表所属模式的方法，使用 @Nullable 注解表示可能返回 null

  /**
   * Converts this table into a {@link RelNode relational expression}.
   * 将此表转换为关系表达式（RelNode）。
   *
   * <p>The {@link org.apache.calcite.plan.RelOptPlanner planner} calls this
   * method to convert a table into an initial relational expression,
   * generally something abstract, such as a
   * {@link org.apache.calcite.rel.logical.LogicalTableScan},
   * then optimizes this expression by
   * applying {@link org.apache.calcite.plan.RelOptRule rules} to transform it
   * into more efficient access methods for this table.
   * 关系优化器（RelOptPlanner）调用此方法将表转换为初始的关系表达式，
   * 通常是一个抽象的表达式，例如逻辑表扫描（LogicalTableScan），
   * 然后通过应用优化规则（RelOptRule）来优化这个表达式，
   * 将其转换为针对此表的更高效的访问方法。
   * 这是查询优化的第一步，将逻辑表转换为可执行的关系算子树。
   * 通过不同的实现，可以生成不同的扫描算子，如全表扫描、索引扫描等。
   *
   * @param context ToRelContext 转换上下文，包含转换所需的信息，如集群、提示等
   * @return RelNode 返回代表此表的关系表达式节点
   */
  RelNode toRel(ToRelContext context); // 声明将表转换为关系表达式的方法，接收 ToRelContext 上下文参数

  /**
   * Returns a description of the physical ordering (or orderings) of the rows
   * returned from this table.
   * 返回从此表返回的行的物理排序（或多个排序）的描述。
   * 这个信息对于优化器非常重要，可以帮助优化器利用现有的排序来避免额外的排序操作。
   * 例如，如果表已经按照某个列排序，优化器可以利用这个信息来优化 ORDER BY 操作。
   * RelCollation 描述了排序的列和排序方向（升序或降序）。
   *
   * @see RelMetadataQuery#collations(RelNode) 参见关系元数据查询中的 collations 方法
   * @return List<RelCollation> 返回排序描述列表，可能为 null（表示无排序）
   */
  @Nullable List<RelCollation> getCollationList(); // 声明获取表排序信息的方法，使用 @Nullable 注解

  /**
   * Returns a description of the physical distribution of the rows
   * in this table.
   * 返回此表中行的物理分布的描述。
   * 分布信息描述了数据在集群中的分布方式，例如哈希分布、范围分布、随机分布等。
   * 这对于分布式查询优化非常重要，可以帮助优化器决定数据重分布策略。
   * 例如，如果两个表按照相同的键分布，优化器可以避免数据重分布，直接进行本地连接。
   * RelDistribution 描述了分布的类型和分布键。
   *
   * @see RelMetadataQuery#distribution(RelNode) 参见关系元数据查询中的 distribution 方法
   * @return RelDistribution 返回分布描述，可能为 null（表示无分布信息）
   */
  @Nullable RelDistribution getDistribution(); // 声明获取表分布信息的方法，使用 @Nullable 注解

  /**
   * Returns whether the given columns are a key or a superset of a unique key
   * of this table.
   * 返回给定的列是否是此表的主键或超集（包含主键）。
   * 主键信息对于查询优化非常重要，可以帮助优化器：
   * 1. 识别唯一性约束，消除重复操作
   * 2. 优化连接操作，例如使用嵌套循环连接
   * 3. 推导数据分布的均匀性
   * ImmutableBitSet 使用位集合高效地表示列索引，避免创建大量对象。
   *
   * @param columns Ordinals of key columns 列的序号（从0开始的索引）
   * @return Whether the given columns are a key or a superset of a key 返回给定列是否是主键或主键超集
   */
  boolean isKey(ImmutableBitSet columns); // 声明判断列是否为主键的方法，接收列索引集合参数

  /**
   * Returns a list of unique keys, empty list if no key exist,
   * the result should be consistent with {@code isKey}.
   * 返回唯一键列表，如果没有键则返回空列表，结果应与 {@code isKey} 方法保持一致。
   * 一个表可能有多个唯一键，例如主键和其他唯一索引。
   * 优化器使用这个信息来了解表的约束条件，从而进行更智能的优化。
   * 每个唯一键用 ImmutableBitSet 表示，包含组成该键的列索引。
   *
   * @return List<ImmutableBitSet> 返回唯一键列表，可能为 null（表示未知或无键）
   */
  @Nullable List<ImmutableBitSet> getKeys(); // 声明获取所有唯一键的方法，返回键的列表

  /**
   * Returns the referential constraints existing for this table. These constraints
   * are represented over other tables using {@link RelReferentialConstraint} nodes.
   * 返回此表存在的引用约束（外键关系）。这些约束使用 {@link RelReferentialConstraint} 节点来表示与其他表的关系。
   * 引用约束描述了表之间的外键关系，例如：
   * - 订单表中的 customer_id 引用客户表中的 id
   * - 订单明细表中的 order_id 引用订单表中的 id
   * 优化器使用这些信息来：
   * 1. 优化连接操作，选择更高效的连接算法
   * 2. 推导数据分布和基数
   * 3. 执行查询重写，如消除不必要的连接
   * 4. 支持语义优化，如利用外键关系简化查询
   *
   * @return List<RelReferentialConstraint> 返回引用约束列表，可能为 null（表示无约束或未知）
   */
  @Nullable List<RelReferentialConstraint> getReferentialConstraints(); // 声明获取引用约束的方法

  /**
   * Generates code for this table.
   * 为此表生成代码。
   * 这个方法用于代码生成场景，将表转换为可执行的 LINQ 表达式。
   * 生成的代码可以直接执行，用于数据访问和查询。
   * 例如，可以生成查询特定表的 C# 或 Java 代码。
   * 这对于将 SQL 查询转换为可执行代码非常有用，特别是在 Linq4j 框架中。
   *
   * @param clazz The desired collection class; for example {@code Queryable}. 期望的集合类，例如 {@code Queryable}
   * @return the code for the table, or null if code generation is not supported 返回表的代码，如果不支持代码生成则返回 null
   */
  @Nullable Expression getExpression(Class clazz); // 声明生成表代码的方法，接收目标类类型参数

  /** Returns a table with the given extra fields.
   * 返回一个包含给定额外字段的表。
   *
   * <p>The extended table includes the fields of this base table plus the
   * extended fields that do not have the same name as a field in the base
   * table.
   * 扩展后的表包含基础表的所有字段，加上扩展字段（与基础表字段名不同的字段）。
   * 这个方法用于表的扩展，例如：
   * - 添加计算列
   * - 添加虚拟列
   * - 添加派生列
   * 如果扩展字段与基础表字段同名，则会被忽略（不重复添加）。
   * 这对于视图展开、查询重写等场景非常有用。
   *
   * @param extendedFields List<RelDataTypeField> 要添加的扩展字段列表
   * @return RelOptTable 返回扩展后的表对象
   */
  RelOptTable extend(List<RelDataTypeField> extendedFields); // 声明扩展表的方法，接收扩展字段列表

  /** Returns a list describing how each column is populated. The list has the
   * same number of entries as there are fields, and is immutable.
   * 返回描述每列如何填充的列表。该列表与字段数量相同，并且是不可变的。
   * ColumnStrategy 描述了列的填充策略，包括：
   * - NOT_NULL: 列不允许为 null
   * - NULLABLE: 列允许为 null
   * - DEFAULT: 列有默认值
   * - VIRTUAL: 列是虚拟列（计算列）
   * - STORED: 列是存储的计算列
   * 优化器使用这些信息来：
   * 1. 优化查询计划，例如跳过虚拟列的读取
   * 2. 处理 null 值语义
   * 3. 应用默认值
   * 4. 优化写入操作
   *
   * @return List<ColumnStrategy> 返回列策略列表，与表的列一一对应
   */
  List<ColumnStrategy> getColumnStrategies(); // 声明获取列策略列表的方法

  /** Can expand a view into relational expressions.
   * 可以将视图展开为关系表达式。
   * 这个接口定义了视图展开的能力，用于将 SQL 视图转换为具体的关系表达式树。
   * 视图展开是查询优化的重要步骤，将视图的定义替换为实际的关系代数表达式。
   * 这样优化器可以对视图进行优化，就像处理普通表一样。
   */
  interface ViewExpander { // 定义视图展开器接口，用于将视图转换为关系表达式
    /**
     * Returns a relational expression that is to be substituted for an access
     * to a SQL view.
     * 返回要替换 SQL 视图访问的关系表达式。
     * 这个方法接收视图的定义信息，将视图展开为具体的关系表达式树。
     * 展开后的表达式可以被优化器进一步优化。
     * 视图展开包括：
     * 1. 解析视图的 SQL 定义
     * 2. 将视图的查询转换为关系表达式
     * 3. 处理视图的参数化和引用
     *
     * @param rowType Row type of the view 视图的行类型，描述视图的列结构
     * @param queryString Body of the view 视图的主体（SQL 查询字符串）
     * @param schemaPath Path of a schema wherein to find referenced tables 用于查找引用表的 schema 路径
     * @param viewPath Path of the view, ending with its name; may be null 视图的路径，以视图名结尾；可能为 null
     * @return RelRoot 返回关系根节点，代表展开后的完整关系表达式树
     */
    RelRoot expandView(RelDataType rowType, String queryString, // 声明展开视图的方法，接收视图定义参数
        List<String> schemaPath, @Nullable List<String> viewPath); // schemaPath 和 viewPath 参数
  }

  /** Contains the context needed to convert a a table into a relational
   * expression.
   * 包含将表转换为关系表达式所需的上下文信息。
   * 这个接口扩展了 ViewExpander，除了视图展开能力外，还提供了转换表所需的其他信息。
   * 上下文信息包括：
   * 1. 关系集群（RelOptCluster）：包含类型系统、表达式工厂等共享资源
   * 2. 表提示（RelHint）：优化器提示，用于指导优化过程
   * 3. 视图展开能力：可以展开引用的视图
   * 这个上下文在表转换过程中传递，确保转换过程有足够的信息。
   */
  interface ToRelContext extends ViewExpander { // 定义表转换上下文接口，继承自 ViewExpander
    /**
     * Returns the cluster.
     * 返回关系集群（RelOptCluster）。
     * 关系集群是查询优化和执行的共享上下文，包含：
     * 1. 类型系统（RelDataTypeFactory）：用于创建和操作数据类型
     * 2. 表达式工厂（RexBuilder）：用于创建关系表达式
     * 3. 优化器（RelOptPlanner）：用于优化查询
     * 4. 其他共享资源和配置
     * 集群确保所有关系节点共享相同的类型系统和优化器配置。
     *
     * @return RelOptCluster 返回关系集群对象
     */
    RelOptCluster getCluster(); // 声明获取关系集群的方法

    /**
     * Returns the table hints of the table to convert,
     * usually you can use the hints to pass along some dynamic params.
     * 返回要转换的表的提示信息。
     * 通常可以使用这些提示来传递一些动态参数。
     * 表提示（RelHint）是用户或系统提供的优化提示，用于指导优化器的行为。
     * 例如：
     * - 强制使用某个索引
     * - 指定连接算法
     * - 设置并行度
     * - 传递运行时参数
     * 提示可以帮助优化器生成更优的执行计划，特别是在统计信息不准确的情况下。
     *
     * @return the hints attached to the table, never null 返回附加到表的提示列表，从不为 null
     */
    List<RelHint> getTableHints(); // 声明获取表提示的方法
  }
}