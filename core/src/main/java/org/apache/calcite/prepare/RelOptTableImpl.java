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
package org.apache.calcite.prepare; // 声明包名，该类属于org.apache.calcite.prepare包，用于SQL查询准备阶段

import org.apache.calcite.jdbc.CalciteSchema; // 导入CalciteSchema类，表示Calcite中的schema定义
import org.apache.calcite.linq4j.tree.Expression; // 导入Expression类，用于表示LINQ表达式树中的表达式
import org.apache.calcite.linq4j.tree.TableExpressionFactory; // 导入TableExpressionFactory接口，用于创建表表达式
import org.apache.calcite.materialize.Lattice; // 导入Lattice类，用于物化视图的多维度分析
import org.apache.calcite.plan.RelOptSchema; // 导入RelOptSchema接口，表示关系优化器中的schema
import org.apache.calcite.plan.RelOptTable; // 导入RelOptTable接口，表示关系优化器中的表
import org.apache.calcite.rel.RelCollation; // 导入RelCollation类，表示关系的排序规则
import org.apache.calcite.rel.RelDistribution; // 导入RelDistribution类，表示数据的分布方式
import org.apache.calcite.rel.RelDistributionTraitDef; // 导入RelDistributionTraitDef类，定义分布特征
import org.apache.calcite.rel.RelFieldCollation; // 导入RelFieldCollation类，表示字段的排序方向
import org.apache.calcite.rel.RelNode; // 导入RelNode接口，表示关系代数树中的节点
import org.apache.calcite.rel.RelReferentialConstraint; // 导入RelReferentialConstraint类，表示外键约束
import org.apache.calcite.rel.logical.LogicalTableScan; // 导入LogicalTableScan类，表示逻辑表扫描节点
import org.apache.calcite.rel.type.RelDataType; // 导入RelDataType接口，表示关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入RelDataTypeFactory接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入RelDataTypeField接口，表示关系数据类型中的字段
import org.apache.calcite.rel.type.RelProtoDataType; // 导入RelProtoDataType接口，表示可序列化的关系数据类型原型
import org.apache.calcite.rel.type.RelRecordType; // 导入RelRecordType类，表示记录类型
import org.apache.calcite.schema.ColumnStrategy; // 导入ColumnStrategy枚举，表示列的存储策略
import org.apache.calcite.schema.ModifiableTable; // 导入ModifiableTable接口，表示可修改的表
import org.apache.calcite.schema.Path; // 导入Path类，表示schema中的路径
import org.apache.calcite.schema.ScannableTable; // 导入ScannableTable接口，表示可扫描的表
import org.apache.calcite.schema.Schema; // 导入Schema接口，表示数据模式
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，扩展了Schema接口，提供更多功能
import org.apache.calcite.schema.SchemaVersion; // 导入SchemaVersion接口，表示schema的版本
import org.apache.calcite.schema.Schemas; // 导入Schemas工具类，提供schema相关的工具方法
import org.apache.calcite.schema.StreamableTable; // 导入StreamableTable接口，表示流式表
import org.apache.calcite.schema.Table; // 导入Table接口，表示数据表
import org.apache.calcite.schema.TemporalTable; // 导入TemporalTable接口，表示时态表
import org.apache.calcite.schema.TranslatableTable; // 导入TranslatableTable接口，表示可转换为关系代数的表
import org.apache.calcite.schema.Wrapper; // 导入Wrapper接口，用于包装和解包对象
import org.apache.calcite.schema.lookup.LikePattern; // 导入LikePattern类，表示LIKE匹配模式
import org.apache.calcite.schema.lookup.Lookup; // 导入Lookup接口，用于查找对象
import org.apache.calcite.sql.SqlAccessType; // 导入SqlAccessType枚举，表示SQL访问权限类型
import org.apache.calcite.sql.validate.SqlModality; // 导入SqlModality枚举，表示SQL模式（关系或流）
import org.apache.calcite.sql.validate.SqlMonotonicity; // 导入SqlMonotonicity枚举，表示单调性
import org.apache.calcite.sql2rel.InitializerExpressionFactory; // 导入InitializerExpressionFactory接口，用于创建初始化表达式
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入NullInitializerExpressionFactory类，创建空初始化表达式
import org.apache.calcite.util.ImmutableBitSet; // 导入ImmutableBitSet类，表示不可变的位集合
import org.apache.calcite.util.LazyReference; // 导入LazyReference类，表示延迟加载的引用
import org.apache.calcite.util.Pair; // 导入Pair类，表示键值对
import org.apache.calcite.util.Util; // 导入Util工具类，提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的ImmutableList类，表示不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，表示可能为null的值

import java.util.AbstractList; // 导入AbstractList抽象类，用于实现自定义列表
import java.util.Collection; // 导入Collection接口，表示集合
import java.util.List; // 导入List接口，表示列表
import java.util.Set; // 导入Set接口，表示集合

import static java.util.Objects.requireNonNull; // 导入requireNonNull静态方法，用于检查参数非空

/**
 * Implementation of {@link org.apache.calcite.plan.RelOptTable}.
 * RelOptTable接口的实现类，表示关系优化器中的表。
 * 该类封装了表的元数据信息，包括表名、行类型、统计信息等，
 * 并提供了将表转换为关系代数节点的能力。
 * 它是Calcite在查询优化过程中表示表的核心类，负责：
 * 1. 存储表的元数据（名称、类型、统计信息）
 * 2. 提供表的访问权限和约束信息
 * 3. 支持表转换为关系代数节点（RelNode）
 * 4. 提供列策略信息（虚拟列、存储列等）
 * 5. 支持表的扩展和复制
 */
public class RelOptTableImpl extends Prepare.AbstractPreparingTable { // RelOptTableImpl类，继承自Prepare.AbstractPreparingTable
  private final @Nullable RelOptSchema schema; // 关系优化器schema，包含表所属的schema信息，可能为null
  private final RelDataType rowType; // 表的行类型，定义了表中所有字段的数据类型和名称
  private final @Nullable Table table; // 底层表对象，可能是ScannableTable、TranslatableTable等，可能为null
  private final @Nullable TableExpressionFactory tableExpressionFactory; // 表表达式工厂，用于生成访问表数据的表达式，可能为null
  private final ImmutableList<String> names; // 表的完全限定名列表，如["schema", "table"]或["catalog", "schema", "table"]

  /** Estimate for the row count, or null.
   * 行数估计值，或null
   *
   * <p>If not null, overrides the estimate from the actual table.
   * 如果不为null，则覆盖实际表中的行数估计值
   *
   * <p>Useful when a table that contains a materialized query result is being
   * used to replace a query expression that wildly underestimates the row
   * count. Now the materialized table can tell the same lie.
   * 当包含物化查询结果的表被用来替换一个严重低估行数的查询表达式时非常有用。
   * 现在物化表可以"说同样的谎言"（即保持相同的低估行数）。
   */
  private final @Nullable Double rowCount; // 行数估计值，Double类型，可能为null

  private RelOptTableImpl( // 私有构造方法，创建RelOptTableImpl实例
      @Nullable RelOptSchema schema, // 关系优化器schema参数
      RelDataType rowType, // 行类型参数
      List<String> names, // 表名列表参数
      @Nullable Table table, // 表对象参数
      @Nullable TableExpressionFactory tableExpressionFactory, // 表表达式工厂参数
      @Nullable Double rowCount) { // 行数估计参数
    this.schema = schema; // 将schema参数赋值给成员变量
    this.rowType = requireNonNull(rowType, "rowType"); // 检查rowType非空并赋值，为null则抛出异常
    this.names = ImmutableList.copyOf(names); // 将names列表复制为不可变列表并赋值
    this.table = table; // may be null // 将table参数赋值，可能为null
    this.tableExpressionFactory = tableExpressionFactory; // may be null // 将tableExpressionFactory赋值，可能为null
    this.rowCount = rowCount; // may be null // 将rowCount赋值，可能为null
  }

  public static RelOptTableImpl create( // 静态工厂方法，创建RelOptTableImpl实例
      @Nullable RelOptSchema schema, // 关系优化器schema参数
      RelDataType rowType, // 行类型参数
      List<String> names, // 表名列表参数
      Expression expression) { // LINQ表达式参数
    return new RelOptTableImpl(schema, rowType, names, null, // 创建新实例，table为null
        c -> expression, null); // tableExpressionFactory为lambda表达式，rowCount为null
  }

  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public static RelOptTableImpl create( // 静态工厂方法（已废弃版本）
      @Nullable RelOptSchema schema, // 关系优化器schema参数
      RelDataType rowType, // 行类型参数
      List<String> names, // 表名列表参数
      Table table, // 表对象参数
      Expression expression) { // LINQ表达式参数
    return create(schema, rowType, names, table, c -> expression); // 调用另一个create方法，将expression转换为lambda
  }

  /**
   * Creates {@link RelOptTableImpl} instance with specified arguments
   * and row count obtained from table statistic.
   * 使用指定参数创建RelOptTableImpl实例，并从表统计信息中获取行数
   *
   * @param schema table schema // 表的schema
   * @param rowType table row type // 表的行类型
   * @param names full table path // 表的完整路径
   * @param table table // 表对象
   * @param expressionFactory expression function for accessing table data
   *                          in the generated code // 用于在生成的代码中访问表数据的表达式工厂函数
   *
   * @return {@link RelOptTableImpl} instance // 返回RelOptTableImpl实例
   */
  public static RelOptTableImpl create( // 静态工厂方法，创建RelOptTableImpl实例
      @Nullable RelOptSchema schema, // 关系优化器schema参数
      RelDataType rowType, // 行类型参数
      List<String> names, // 表名列表参数
      Table table, // 表对象参数
      TableExpressionFactory expressionFactory) { // 表表达式工厂参数
    return new RelOptTableImpl(schema, rowType, names, table, // 创建新实例
        expressionFactory, table.getStatistic().getRowCount()); // 从表统计信息中获取行数
  }

  public static RelOptTableImpl create(@Nullable RelOptSchema schema, RelDataType rowType, // 静态工厂方法
      Table table, Path path) { // 表对象和路径参数
    final SchemaPlus schemaPlus = MySchemaPlus.create(path); // 使用路径创建SchemaPlus对象
    return new RelOptTableImpl(schema, rowType, Pair.left(path), table, // 创建新实例，使用路径的第一个元素作为表名
        c -> Schemas.getTableExpression(schemaPlus, Util.last(path).left, table, c), // 创建表表达式工厂
        table.getStatistic().getRowCount()); // 从表统计信息中获取行数
  }

  public static RelOptTableImpl create(@Nullable RelOptSchema schema, RelDataType rowType, // 静态工厂方法
      final CalciteSchema.TableEntry tableEntry, @Nullable Double rowCount) { // 表条目和行数参数
    final Table table = tableEntry.getTable(); // 从表条目中获取表对象
    return new RelOptTableImpl(schema, rowType, tableEntry.path(), table, // 创建新实例
        c -> Schemas.getTableExpression(tableEntry.schema.plus(), tableEntry.name, table, c), // 创建表表达式工厂
        rowCount); // 使用传入的行数
  }

  /**
   * Creates a copy of this RelOptTable. The new RelOptTable will have newRowType.
   * 创建此RelOptTable的副本，新的RelOptTable将具有新的行类型
   */
  public RelOptTableImpl copy(RelDataType newRowType) { // 复制方法，使用新的行类型
    return new RelOptTableImpl(this.schema, newRowType, this.names, this.table, // 创建新实例，使用新的行类型
        this.tableExpressionFactory, this.rowCount); // 其他参数保持不变
  }

  @Override public String toString() { // 重写toString方法，返回对象的字符串表示
    return "RelOptTableImpl{" // 返回RelOptTableImpl的字符串表示
        + "schema=" + schema // 包含schema信息
        + ", names= " + names // 包含表名列表
        + ", table=" + table // 包含表对象
        + ", rowType=" + rowType // 包含行类型
        + '}'; // 结束大括号
  }

  public static RelOptTableImpl create(@Nullable RelOptSchema schema, // 静态工厂方法
      RelDataType rowType, Table table, ImmutableList<String> names) { // 行类型、表对象和表名列表参数
    assert table instanceof TranslatableTable // 断言表是TranslatableTable类型
        || table instanceof ScannableTable // 或者是ScannableTable类型
        || table instanceof ModifiableTable; // 或者是ModifiableTable类型
    return new RelOptTableImpl(schema, rowType, names, table, null, null); // 创建新实例，不设置表达式工厂和行数
  }

  @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) { // 重写unwrap方法，用于解包对象
    if (clazz.isInstance(this)) { // 如果请求的类型是当前类或其子类
      return clazz.cast(this); // 返回当前对象
    }
    if (clazz.isInstance(table)) { // 如果请求的类型是table的类型
      return clazz.cast(table); // 返回table对象
    }
    if (table instanceof Wrapper) { // 如果table实现了Wrapper接口
      final T t = ((Wrapper) table).unwrap(clazz); // 尝试从table中解包出请求的类型
      if (t != null) { // 如果解包成功
        return t; // 返回解包的对象
      }
    }
    if (clazz == CalciteSchema.class && schema != null) { // 如果请求CalciteSchema类型且schema不为null
      return clazz.cast( // 返回CalciteSchema对象
          Schemas.subSchema(((CalciteCatalogReader) schema).rootSchema, // 从根schema中查找子schema
              Util.skipLast(getQualifiedName()))); // 使用去掉最后一个元素的表名列表
    }
    return null; // 无法解包，返回null
  }

  @Override public @Nullable Expression getExpression(Class clazz) { // 重写getExpression方法，获取表表达式
    if (tableExpressionFactory == null) { // 如果表表达式工厂为null
      return null; // 返回null
    }
    return tableExpressionFactory.create(clazz); // 使用工厂创建表达式
  }

  @Override protected RelOptTable extend(Table extendedTable) { // 重写extend方法，扩展表
    RelOptSchema schema = requireNonNull(getRelOptSchema(), "relOptSchema"); // 获取关系优化器schema，确保非null
    final RelDataType extendedRowType = // 获取扩展表的行类型
        extendedTable.getRowType(schema.getTypeFactory()); // 使用schema的类型工厂获取行类型
    return new RelOptTableImpl(schema, extendedRowType, getQualifiedName(), // 创建新的RelOptTableImpl实例
        extendedTable, tableExpressionFactory, getRowCount()); // 使用扩展表和当前的其他属性
  }

  @Override public boolean equals(@Nullable Object obj) { // 重写equals方法，比较对象是否相等
    return obj instanceof RelOptTableImpl // 如果obj是RelOptTableImpl类型
        && this.rowType.equals(((RelOptTableImpl) obj).getRowType()) // 且行类型相等
        && this.table == ((RelOptTableImpl) obj).table; // 且表对象相同（使用==比较引用）
  }

  @Override public int hashCode() { // 重写hashCode方法，返回对象的哈希码
    return (this.table == null) // 如果table为null
        ? super.hashCode() : this.table.hashCode(); // 返回父类的hashCode，否则返回table的hashCode
  }
  @Override public double getRowCount() { // 重写getRowCount方法，获取表的行数估计值
    if (rowCount != null) { // 如果成员变量rowCount不为null
      return rowCount; // 返回成员变量的值
    }
    if (table != null) { // 如果table不为null
      final Double rowCount = table.getStatistic().getRowCount(); // 从表统计信息中获取行数
      if (rowCount != null) { // 如果统计信息中的行数不为null
        return rowCount; // 返回统计信息中的行数
      }
    }
    return 100d; // 默认返回100作为行数估计值
  }

  @Override public @Nullable RelOptSchema getRelOptSchema() { // 重写getRelOptSchema方法，获取关系优化器schema
    return schema; // 返回成员变量schema
  }

  @Override public RelNode toRel(ToRelContext context) { // 重写toRel方法，将表转换为关系代数节点
    // Make sure rowType's list is immutable. If rowType is DynamicRecordType, creates a new
    // RelOptTable by replacing with immutable RelRecordType using the same field list.
    // 确保rowType的列表是不可变的。如果rowType是DynamicRecordType，使用相同的字段列表创建
    // 不可变的RelRecordType来替换，从而创建一个新的RelOptTable
    if (this.getRowType().isDynamicStruct()) { // 如果当前行类型是动态结构类型
      final RelDataType staticRowType = new RelRecordType(getRowType().getFieldList()); // 创建静态记录类型
      final RelOptTable relOptTable = this.copy(staticRowType); // 复制当前表，使用静态行类型
      return relOptTable.toRel(context); // 递归调用toRel方法转换新表
    }

    // If there are any virtual columns, create a copy of this table without
    // those virtual columns.
    // 如果存在任何虚拟列，创建一个不包含这些虚拟列的表副本
    final List<ColumnStrategy> strategies = getColumnStrategies(); // 获取所有列的策略
    if (strategies.contains(ColumnStrategy.VIRTUAL)) { // 如果存在虚拟列
      final RelDataTypeFactory.Builder b = // 创建类型工厂构建器
          context.getCluster().getTypeFactory().builder(); // 从上下文的集群中获取类型工厂
      for (RelDataTypeField field : rowType.getFieldList()) { // 遍历行类型中的所有字段
        if (strategies.get(field.getIndex()) != ColumnStrategy.VIRTUAL) { // 如果该字段不是虚拟列
          b.add(field.getName(), field.getType()); // 添加字段名称和类型到构建器
        }
      }
      final RelOptTable relOptTable = // 创建新的RelOptTableImpl实例，不包含虚拟列
          new RelOptTableImpl(this.schema, b.build(), this.names, this.table, // 使用新构建的行类型
              this.tableExpressionFactory, this.rowCount) { // 其他参数保持不变
            @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) { // 重写unwrap方法
              if (clazz.isAssignableFrom(InitializerExpressionFactory.class)) { // 如果请求的是初始化表达式工厂
                return clazz.cast(NullInitializerExpressionFactory.INSTANCE); // 返回空初始化表达式工厂
              }
              return super.unwrap(clazz); // 否则调用父类的unwrap方法
            }
          };
      return relOptTable.toRel(context); // 递归调用toRel方法转换新表
    }

    if (table instanceof TranslatableTable) { // 如果表实现了TranslatableTable接口
      return ((TranslatableTable) table).toRel(context, this); // 调用表的toRel方法转换
    }
    return LogicalTableScan.create(context.getCluster(), this, context.getTableHints()); // 创建逻辑表扫描节点
  }

  @Override public @Nullable List<RelCollation> getCollationList() { // 重写getCollationList方法，获取排序规则列表
    if (table != null) { // 如果table不为null
      return table.getStatistic().getCollations(); // 从表统计信息中获取排序规则
    }
    return ImmutableList.of(); // 返回空列表
  }

  @Override public @Nullable RelDistribution getDistribution() { // 重写getDistribution方法，获取数据分布方式
    if (table != null) { // 如果table不为null
      return table.getStatistic().getDistribution(); // 从表统计信息中获取分布方式
    }
    return RelDistributionTraitDef.INSTANCE.getDefault(); // 返回默认的分布方式
  }

  @Override public boolean isKey(ImmutableBitSet columns) { // 重写isKey方法，检查给定的列集合是否构成键
    if (table != null) { // 如果table不为null
      return table.getStatistic().isKey(columns); // 从表统计信息中检查是否为键
    }
    return false; // 返回false
  }

  @Override public @Nullable List<ImmutableBitSet> getKeys() { // 重写getKeys方法，获取所有键的列集合
    if (table != null) { // 如果table不为null
      return table.getStatistic().getKeys(); // 从表统计信息中获取所有键
    }
    return ImmutableList.of(); // 返回空列表
  }

  @Override public @Nullable List<RelReferentialConstraint> getReferentialConstraints() { // 重写getReferentialConstraints方法，获取引用约束（外键）
    if (table != null) { // 如果table不为null
      return table.getStatistic().getReferentialConstraints(); // 从表统计信息中获取引用约束
    }
    return ImmutableList.of(); // 返回空列表
  }

  @Override public RelDataType getRowType() { // 重写getRowType方法，获取表的行类型
    return rowType; // 返回成员变量rowType
  }

  @Override public boolean supportsModality(SqlModality modality) { // 重写supportsModality方法，检查表是否支持指定的模式
    switch (modality) { // 根据模式类型判断
    case STREAM: // 如果是流模式
      return table instanceof StreamableTable; // 检查表是否实现了StreamableTable接口
    default: // 默认情况（关系模式）
      return !(table instanceof StreamableTable); // 检查表不是流式表
    }
  }

  @Override public boolean isTemporal() { // 重写isTemporal方法，检查表是否为时态表
    return table instanceof TemporalTable; // 检查表是否实现了TemporalTable接口
  }

  @Override public List<String> getQualifiedName() { // 重写getQualifiedName方法，获取表的完全限定名
    return names; // 返回成员变量names
  }

  @Override public SqlMonotonicity getMonotonicity(String columnName) { // 重写getMonotonicity方法，获取列的单调性
    if (table == null) { // 如果table为null
      return SqlMonotonicity.NOT_MONOTONIC; // 返回非单调
    }
    List<RelCollation> collations = table.getStatistic().getCollations(); // 获取排序规则列表
    if (collations == null) { // 如果排序规则列表为null
      return SqlMonotonicity.NOT_MONOTONIC; // 返回非单调
    }
    for (RelCollation collation : collations) { // 遍历每个排序规则
      final RelFieldCollation fieldCollation = // 获取排序规则中的第一个字段排序
          collation.getFieldCollations().get(0); // 获取第一个字段排序信息
      final int fieldIndex = fieldCollation.getFieldIndex(); // 获取字段索引
      if (fieldIndex < rowType.getFieldCount() // 如果字段索引在行类型范围内
          && rowType.getFieldNames().get(fieldIndex).equals(columnName)) { // 且字段名匹配
        return fieldCollation.direction.monotonicity(); // 返回该字段的单调性
      }
    }
    return SqlMonotonicity.NOT_MONOTONIC; // 未找到匹配的字段，返回非单调
  }

  @Override public SqlAccessType getAllowedAccess() { // 重写getAllowedAccess方法，获取允许的访问权限
    return SqlAccessType.ALL; // 返回所有权限（读、写等）
  }

  /** Helper for {@link #getColumnStrategies()}. */
  /** {@link #getColumnStrategies()}的辅助方法 */
  public static List<ColumnStrategy> columnStrategies(final RelOptTable table) { // 静态方法，获取列策略列表
    final int fieldCount = table.getRowType().getFieldCount(); // 获取字段数量
    final InitializerExpressionFactory ief = // 获取初始化表达式工厂
        Util.first(table.unwrap(InitializerExpressionFactory.class), // 尝试从表中解包出初始化表达式工厂
            NullInitializerExpressionFactory.INSTANCE); // 如果失败，使用空初始化表达式工厂
    return new AbstractList<ColumnStrategy>() { // 返回一个抽象列表的匿名子类
      @Override public int size() { // 重写size方法，返回列表大小
        return fieldCount; // 返回字段数量
      }

      @Override public ColumnStrategy get(int index) { // 重写get方法，获取指定索引的列策略
        return ief.generationStrategy(table, index); // 使用初始化表达式工厂获取列的生成策略
      }
    };
  }

  /** Converts the ordinal of a field into the ordinal of a stored field.
   * That is, it subtracts the number of virtual fields that come before it. */
  /** 将字段的序号转换为存储字段的序号，即减去在它之前的虚拟字段数量 */
  public static int realOrdinal(final RelOptTable table, int i) { // 静态方法，计算实际存储字段的序号
    List<ColumnStrategy> strategies = table.getColumnStrategies(); // 获取列策略列表
    int n = 0; // 虚拟字段计数器
    for (int j = 0; j < i; j++) { // 遍历当前字段之前的所有字段
      switch (strategies.get(j)) { // 根据列策略判断
      case VIRTUAL: // 如果是虚拟列
        ++n; // 虚拟字段计数加1
        break; // 跳出switch
      default: // 其他情况
        break; // 不做处理
      }
    }
    return i - n; // 返回实际存储字段的序号（原序号减去虚拟字段数）
  }

  /** Returns the row type of a table after any {@link ColumnStrategy#VIRTUAL}
   * columns have been removed. This is the type of the records that are
   * actually stored. */
  /** 返回移除所有{@link ColumnStrategy#VIRTUAL}列后表的行类型，
   * 这是实际存储的记录类型 */
  public static RelDataType realRowType(RelOptTable table) { // 静态方法，获取实际存储的行类型
    final RelDataType rowType = table.getRowType(); // 获取表的行类型
    final List<ColumnStrategy> strategies = columnStrategies(table); // 获取列策略列表
    if (!strategies.contains(ColumnStrategy.VIRTUAL)) { // 如果不存在虚拟列
      return rowType; // 直接返回原行类型
    }
    final RelDataTypeFactory.Builder builder = // 创建类型工厂构建器
        requireNonNull(table.getRelOptSchema(), // 确保关系优化器schema非null
            () -> "relOptSchema for table " + table).getTypeFactory().builder(); // 获取类型工厂并创建构建器
    for (RelDataTypeField field : rowType.getFieldList()) { // 遍历行类型中的所有字段
      if (strategies.get(field.getIndex()) != ColumnStrategy.VIRTUAL) { // 如果该字段不是虚拟列
        builder.add(field); // 添加字段到构建器
      }
    }
    return builder.build(); // 构建并返回新的行类型
  }

  /** Implementation of {@link SchemaPlus} that wraps a regular schema and knows
   * its name and parent.
   *
   * <p>It is read-only, and functionality is limited in other ways, it but
   * allows table expressions to be generated. */
  /** {@link SchemaPlus}的实现，包装常规schema并知道其名称和父级。
   *
   * <p>它是只读的，在其他方面功能受限，但允许生成表表达式。
   */
  private static class MySchemaPlus implements SchemaPlus { // 私有静态内部类，实现SchemaPlus接口
    private final @Nullable SchemaPlus parent; // 父级schema，可能为null
    private final String name; // schema名称
    private final Schema schema; // 被包装的schema对象
    private final LazyReference<Lookup<? extends SchemaPlus>> subSchemas = new LazyReference<>(); // 子schema的延迟加载引用


    MySchemaPlus(@Nullable SchemaPlus parent, String name, Schema schema) { // 构造方法
      this.parent = parent; // 设置父级schema
      this.name = name; // 设置schema名称
      this.schema = schema; // 设置被包装的schema
    }

    public static MySchemaPlus create(Path path) { // 静态工厂方法，从路径创建MySchemaPlus
      final Pair<String, Schema> pair = Util.last(path); // 获取路径中的最后一对（名称，schema）
      final SchemaPlus parent; // 声明父级schema变量
      if (path.size() == 1) { // 如果路径只有一个元素
        parent = null; // 父级为null
      } else { // 否则
        parent = create(path.parent()); // 递归创建父级schema
      }
      return new MySchemaPlus(parent, pair.left, pair.right); // 创建并返回MySchemaPlus实例
    }

    @Override public @Nullable SchemaPlus getParentSchema() { // 重写getParentSchema方法
      return parent; // 返回父级schema
    }

    @Override public String getName() { // 重写getName方法
      return name; // 返回schema名称
    }

    @Deprecated @Override public @Nullable SchemaPlus getSubSchema(String name) { // 重写getSubSchema方法（已废弃）
      return subSchemas().get(name); // 从子schema查找器中获取指定名称的schema
    }

    @Override public SchemaPlus add(String name, Schema schema) { // 重写add方法，添加schema
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public void add(String name, Table table) { // 重写add方法，添加表
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public boolean removeTable(String name) { // 重写removeTable方法，移除表
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public void add(String name, // 重写add方法，添加函数
        org.apache.calcite.schema.Function function) { // 函数参数
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public void add(String name, RelProtoDataType type) { // 重写add方法，添加类型
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public void add(String name, Lattice lattice) { // 重写add方法，添加Lattice
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public boolean isMutable() { // 重写isMutable方法
      return schema.isMutable(); // 返回被包装schema的可变性
    }

    @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) { // 重写unwrap方法
      return null; // 返回null（不支持解包）
    }

    @Override public void setPath(ImmutableList<ImmutableList<String>> path) { // 重写setPath方法
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public void setCacheEnabled(boolean cache) { // 重写setCacheEnabled方法
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }

    @Override public boolean isCacheEnabled() { // 重写isCacheEnabled方法
      return false; // 返回false（不支持缓存）
    }

    @Override public Lookup<Table> tables() { // 重写tables方法
      return schema.tables(); // 返回被包装schema的表查找器
    }

    @Override public Lookup<? extends SchemaPlus> subSchemas() { // 重写subSchemas方法
      return subSchemas.getOrCompute( // 获取或计算子schema查找器
          () -> schema.subSchemas().map((s, key) -> new MySchemaPlus(this, key, s))); // 将每个子schema包装为MySchemaPlus
    }

    @Deprecated @Override public @Nullable Table getTable(String name) { // 重写getTable方法（已废弃）
      return tables().get(name); // 从表查找器中获取指定名称的表
    }

    @Deprecated @Override public Set<String> getTableNames() { // 重写getTableNames方法（已废弃）
      return schema.tables().getNames(LikePattern.any()); // 获取所有表名称（匹配任意模式）
    }

    @Override public @Nullable RelProtoDataType getType(String name) { // 重写getType方法
      return schema.getType(name); // 返回被包装schema的类型
    }

    @Override public Set<String> getTypeNames() { // 重写getTypeNames方法
      return schema.getTypeNames(); // 返回被包装schema的类型名称集合
    }

    @Override public Collection<org.apache.calcite.schema.Function> // 重写getFunctions方法
    getFunctions(String name) { // 函数名称参数
      return schema.getFunctions(name); // 返回被包装schema的函数集合
    }

    @Override public Set<String> getFunctionNames() { // 重写getFunctionNames方法
      return schema.getFunctionNames(); // 返回被包装schema的函数名称集合
    }

    @Deprecated @Override public Set<String> getSubSchemaNames() { // 重写getSubSchemaNames方法（已废弃）
      return schema.subSchemas().getNames(LikePattern.any()); // 获取所有子schema名称（匹配任意模式）
    }

    @Override public Expression getExpression(@Nullable SchemaPlus parentSchema, // 重写getExpression方法
        String name) { // schema名称参数
      return schema.getExpression(parentSchema, name); // 返回被包装schema的表达式
    }

    @Override public Schema snapshot(SchemaVersion version) { // 重写snapshot方法
      throw new UnsupportedOperationException(); // 抛出不支持操作异常（只读）
    }
  }
}
