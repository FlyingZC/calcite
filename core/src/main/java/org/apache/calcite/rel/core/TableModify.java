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
 */ // Apache许可证2.0版本，允许自由使用、修改和分发
package org.apache.calcite.rel.core; // 核心关系表达式包，包含Calcite中的核心关系代数操作类

import org.apache.calcite.plan.RelOptCluster; // 关系表达式集群，包含同一查询中的所有关系表达式
import org.apache.calcite.plan.RelOptCost; // 关系表达式成本，用于优化器选择最优执行计划
import org.apache.calcite.plan.RelOptPlanner; // 关系优化器，负责查询优化
import org.apache.calcite.plan.RelOptSchema; // 关系优化模式，包含表和视图的元数据
import org.apache.calcite.plan.RelOptTable; // 关系优化表，表示表的定义和元数据
import org.apache.calcite.plan.RelOptUtil; // 关系优化工具类，提供各种辅助方法
import org.apache.calcite.plan.RelTraitSet; // 关系特征集合，定义关系表达式的物理属性
import org.apache.calcite.prepare.Prepare; // 查询准备相关类，包含CatalogReader等
import org.apache.calcite.rel.RelInput; // 关系输入，用于从序列化数据创建关系表达式
import org.apache.calcite.rel.RelNode; // 关系表达式接口，所有关系代数操作的基类
import org.apache.calcite.rel.RelWriter; // 关系写入器，用于输出关系表达式的字符串表示
import org.apache.calcite.rel.SingleRel; // 单子节点关系表达式基类
import org.apache.calcite.rel.externalize.RelEnumTypes; // 关系枚举类型，用于外部化
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 关系元数据查询，用于获取关系表达式的元数据
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型，表示一行数据的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂，用于创建数据类型
import org.apache.calcite.rex.RexNode; // 行表达式节点，表示表达式树中的节点
import org.apache.calcite.sql.SqlKind; // SQL操作类型枚举（如INSERT、UPDATE、DELETE等）
import org.apache.calcite.sql.type.SqlTypeUtil; // SQL类型工具类，提供类型操作方法

import org.checkerframework.checker.nullness.qual.MonotonicNonNull; // 注解：值从null变为非null后不再变回null
import org.checkerframework.checker.nullness.qual.Nullable; // 注解：值可以为null

import java.util.List; // Java集合框架List接口

import static com.google.common.base.Preconditions.checkArgument; // 静态导入：参数校验工具

import static java.util.Objects.requireNonNull; // 静态导入：对象非空校验工具

/**
 * Relational expression that modifies a table. // 表修改的关系表达式，表示对表进行修改操作的抽象类
 *
 * <p>It is similar to {@link org.apache.calcite.rel.core.TableScan}, // 类似于TableScan（表扫描），但表示修改表而不是读取表的请求
 * but represents a request to modify a table rather than read from it. // 它代表对表进行修改的请求，而不是读取
 * It takes one child which produces the modified rows. Those rows are: // 它有一个子节点，该子节点产生要修改的行。这些行是：
 *
 * <ul>
 * <li>For {@code INSERT}, those rows are the new values; // 对于INSERT操作，这些行是要插入的新值
 * <li>for {@code DELETE}, the old values; // 对于DELETE操作，这些行是要删除的旧行
 * <li>for {@code UPDATE}, all old values plus updated new values. // 对于UPDATE操作，这些行包含所有旧行值加上更新的新值
 * </ul>
 */
public abstract class TableModify extends SingleRel { // 抽象类：表修改操作，继承自SingleRel（单子节点关系表达式），表示INSERT/UPDATE/DELETE/MERGE等DML操作
  //~ Enums ------------------------------------------------------------------ // 枚举类型部分

  /**
   * Enumeration of supported modification operations. // 支持的修改操作枚举
   */
  public enum Operation {
    INSERT, // 插入操作：向表中插入新行
    UPDATE, // 更新操作：更新表中现有的行
    DELETE, // 删除操作：从表中删除行
    MERGE  // 合并操作：根据条件执行插入或更新（类似SQL的MERGE语句）
  }

  //~ Instance fields -------------------------------------------------------- // 实例字段部分

  /**
   * The connection to the optimizing session. // 与优化会话的连接，用于访问目录元数据
   */
  protected final Prepare.CatalogReader catalogReader; // 目录读取器：提供对表、列等元数据的访问，用于解析表名和列名

  /**
   * The table definition. // 表定义
   */
  protected final RelOptTable table; // 关系优化表：表示要修改的目标表，包含表的元数据信息（如列名、类型等）
  private final Operation operation; // 操作类型：表示要执行的DML操作类型（INSERT/UPDATE/DELETE/MERGE）
  private final @Nullable List<String> updateColumnList; // 更新列列表：UPDATE操作中要更新的列名列表，仅UPDATE/MERGE操作使用
  private final @Nullable List<RexNode> sourceExpressionList; // 源表达式列表：UPDATE操作中要设置的新值表达式列表，仅UPDATE操作使用
  private @MonotonicNonNull RelDataType inputRowType; // 输入行类型：子节点产生的行数据类型，延迟计算（首次访问时计算）
  private final boolean flattened; // 是否扁平化：指示是否将输入行类型扁平化（展开嵌套结构）

  //~ Constructors ----------------------------------------------------------- // 构造方法部分

  /**
   * Creates a {@code TableModify}. // 创建表修改关系表达式
   *
   * <p>The UPDATE operation has format like this: // UPDATE操作的格式示例：
   * <blockquote>
   *   <pre>UPDATE table SET iden1 = exp1, ident2 = exp2  WHERE condition</pre>
   * </blockquote>
   *
   * @param cluster    Cluster this relational expression belongs to // 关系表达式所属的集群
   * @param traitSet   Traits of this relational expression // 该关系表达式的特征集合（如物理实现方式）
   * @param table      Target table to modify // 要修改的目标表
   * @param catalogReader accessor to the table metadata. // 表元数据访问器
   * @param input      Sub-query or filter condition // 子查询或过滤条件（提供要修改的行）
   * @param operation  Modify operation (INSERT, UPDATE, DELETE) // 修改操作类型（INSERT/UPDATE/DELETE/MERGE）
   * @param updateColumnList List of column identifiers to be updated // 要更新的列标识符列表（如ident1, ident2）；非UPDATE操作时为null
   *           (e.g. ident1, ident2); null if not UPDATE
   * @param sourceExpressionList List of value expressions to be set // 要设置的值表达式列表（如exp1, exp2）；非UPDATE操作时为null
   *           (e.g. exp1, exp2); null if not UPDATE
   * @param flattened Whether set flattens the input row type // 是否扁平化输入行类型
   */
  protected TableModify(
      RelOptCluster cluster, // 参数：关系表达式集群
      RelTraitSet traitSet, // 参数：特征集合
      RelOptTable table, // 参数：目标表
      Prepare.CatalogReader catalogReader, // 参数：目录读取器
      RelNode input, // 参数：输入子节点
      Operation operation, // 参数：操作类型
      @Nullable List<String> updateColumnList, // 参数：更新列列表
      @Nullable List<RexNode> sourceExpressionList, // 参数：源表达式列表
      boolean flattened) { // 参数：是否扁平化
    super(cluster, traitSet, input); // 调用父类SingleRel的构造方法，设置集群、特征集合和输入子节点
    this.table = table; // 保存目标表
    this.catalogReader = catalogReader; // 保存目录读取器
    this.operation = operation; // 保存操作类型
    this.updateColumnList = updateColumnList; // 保存更新列列表
    this.sourceExpressionList = sourceExpressionList; // 保存源表达式列表
    if (operation == Operation.UPDATE) { // 如果是UPDATE操作
      requireNonNull(updateColumnList, "updateColumnList"); // 校验：更新列列表不能为null
      requireNonNull(sourceExpressionList, "sourceExpressionList"); // 校验：源表达式列表不能为null
      checkArgument(sourceExpressionList.size() == updateColumnList.size()); // 校验：表达式数量必须与列数量一致
    } else { // 如果不是UPDATE操作
      if (operation == Operation.MERGE) { // 如果是MERGE操作
        requireNonNull(updateColumnList, "updateColumnList"); // 校验：更新列列表不能为null（MERGE需要更新列列表）
      } else { // 如果是INSERT或DELETE操作
        checkArgument(updateColumnList == null); // 校验：更新列列表必须为null
      }
      checkArgument(sourceExpressionList == null); // 校验：源表达式列表必须为null
    }
    RelOptSchema relOptSchema = table.getRelOptSchema(); // 获取表的关系优化模式
    if (relOptSchema != null) { // 如果模式不为null
      cluster.getPlanner().registerSchema(relOptSchema); // 在优化器中注册该模式，确保优化器能访问表的元数据
    }
    this.flattened = flattened; // 保存是否扁平化的标志
  }

  /**
   * Creates a TableModify by parsing serialized output. // 通过解析序列化输出来创建TableModify（用于从序列化数据恢复对象）
   */
  protected TableModify(RelInput input) { // 参数：关系输入对象，包含序列化的数据
    this(input.getCluster(), // 从序列化数据获取集群
        input.getTraitSet(), // 从序列化数据获取特征集合
        input.getTable("table"), // 从序列化数据获取表
        requireNonNull( // 校验并获取目录读取器
            (Prepare.CatalogReader) input.getTable("table").getRelOptSchema(), // 从表的RelOptSchema获取CatalogReader
            "relOptSchema"), // 如果为null则抛出异常
        input.getInput(), // 从序列化数据获取输入子节点
        requireNonNull(input.getEnum("operation", Operation.class), "operation"), // 从序列化数据获取操作类型枚举
        input.getStringList("updateColumnList"), // 从序列化数据获取更新列列表
        input.getExpressionList("sourceExpressionList"), // 从序列化数据获取源表达式列表
        input.getBoolean("flattened", false)); // 从序列化数据获取扁平化标志，默认为false
  }

  //~ Methods ---------------------------------------------------------------- // 方法部分

  public Prepare.CatalogReader getCatalogReader() { // 获取目录读取器
    return catalogReader; // 返回目录读取器，用于访问表和列的元数据
  }

  @Override public RelOptTable getTable() { // 重写：获取目标表
    return table; // 返回要修改的目标表
  }

  public @Nullable List<String> getUpdateColumnList() { // 获取更新列列表
    return updateColumnList; // 返回UPDATE操作中要更新的列名列表，可能为null
  }

  public @Nullable List<RexNode> getSourceExpressionList() { // 获取源表达式列表
    return sourceExpressionList; // 返回UPDATE操作中要设置的新值表达式列表，可能为null
  }

  public boolean isFlattened() { // 判断是否扁平化
    return flattened; // 返回true表示输入行类型已被扁平化（嵌套结构被展开）
  }

  public Operation getOperation() { // 获取操作类型
    return operation; // 返回DML操作类型（INSERT/UPDATE/DELETE/MERGE）
  }

  public boolean isInsert() { // 判断是否为INSERT操作
    return operation == Operation.INSERT; // 返回true表示这是插入操作
  }

  public boolean isUpdate() { // 判断是否为UPDATE操作
    return operation == Operation.UPDATE; // 返回true表示这是更新操作
  }

  public boolean isDelete() { // 判断是否为DELETE操作
    return operation == Operation.DELETE; // 返回true表示这是删除操作
  }

  public boolean isMerge() { // 判断是否为MERGE操作
    return operation == Operation.MERGE; // 返回true表示这是合并操作
  }

  @Override public RelDataType deriveRowType() { // 重写：推导此关系表达式的输出行类型
    return RelOptUtil.createDmlRowType( // 创建DML操作的行类型
        SqlKind.INSERT, // 使用INSERT类型（DML操作都返回相同的行类型）
        getCluster().getTypeFactory()); // 使用集群的类型工厂创建类型
  }

  @Override public RelDataType getExpectedInputRowType(int ordinalInParent) { // 重写：获取期望的输入行类型
    assert ordinalInParent == 0; // 断言：TableModify只有一个子节点，所以序号必须为0

    if (inputRowType != null) { // 如果输入行类型已计算过
      return inputRowType; // 直接返回缓存的值
    }

    final RelDataTypeFactory typeFactory = getCluster().getTypeFactory(); // 获取类型工厂
    final RelDataType rowType = table.getRowType(); // 获取表的行类型
    switch (operation) { // 根据操作类型构造输入行类型
    case UPDATE: // 如果是UPDATE操作
      if (updateColumnList == null) { // 校验：更新列列表不能为null
        throw new AssertionError("updateColumnList must not be null for "
            + operation); // 抛出断言错误
      }
      inputRowType = // 创建连接类型：包含旧行值和新值
          typeFactory.createJoinType(rowType, // 第一个部分：旧行的所有列
              getCatalogReader().createTypeFromProjection(rowType, // 第二个部分：要更新的列的新值
                  updateColumnList)); // 根据列名列表创建投影类型
      break;
    case MERGE: // 如果是MERGE操作
      if (updateColumnList == null) { // 校验：更新列列表不能为null
        throw new AssertionError("updateColumnList must not be null for "
            + operation); // 抛出断言错误
      }
      inputRowType = // 创建连接类型：包含两行旧行值（用于匹配）和更新值
          typeFactory.createJoinType( // 外层连接
              typeFactory.createJoinType(rowType, rowType), // 内层连接：两行旧行值（用于MERGE的匹配条件）
              getCatalogReader().createTypeFromProjection(rowType, // 第三部分：要更新的列的新值
                  updateColumnList)); // 根据列名列表创建投影类型
      break;
    default: // 如果是INSERT或DELETE操作
      inputRowType = rowType; // 输入行类型就是表的行类型
      break;
    }

    if (flattened) { // 如果需要扁平化
      inputRowType = // 扁平化记录类型：展开嵌套的记录结构
          SqlTypeUtil.flattenRecordType( // 扁平化工具方法
              typeFactory, // 类型工厂
              inputRowType, // 要扁平化的类型
              null); // 扁平化的字段名前缀（null表示不添加前缀）
    }

    return inputRowType; // 返回计算出的输入行类型
  }

  @Override public RelWriter explainTerms(RelWriter pw) { // 重写：输出关系表达式的解释信息（用于查询计划的可视化）
    return super.explainTerms(pw) // 调用父类方法，添加基础信息
        .item("table", table.getQualifiedName()) // 添加表名
        .item("operation", RelEnumTypes.fromEnum(getOperation())) // 添加操作类型（转换为字符串）
        .itemIf("updateColumnList", updateColumnList, updateColumnList != null) // 条件添加：更新列列表（仅当不为null时）
        .itemIf("sourceExpressionList", sourceExpressionList, // 条件添加：源表达式列表（仅当不为null时）
            sourceExpressionList != null)
        .item("flattened", flattened); // 添加扁平化标志
  }

  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner,
      RelMetadataQuery mq) { // 重写：计算此关系表达式的自身成本（用于优化器选择最优执行计划）
    // REVIEW jvs 21-Apr-2006:  Just for now... // 注释：这是一个临时的简化实现，后续可能需要改进
    double rowCount = mq.getRowCount(this); // 获取此操作影响的行数
    return planner.getCostFactory().makeCost(rowCount, 0, 0); // 创建成本对象：只考虑行数，CPU和I/O成本都设为0（简化实现）
  }
}
