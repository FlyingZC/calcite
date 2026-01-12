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
package org.apache.calcite.adapter.druid; // 定义包名，该类位于 Druid 适配器包中

import org.apache.calcite.config.CalciteConnectionConfig; // 导入 Calcite 连接配置类，用于获取连接相关配置
import org.apache.calcite.interpreter.BindableConvention; // 导入 BindableConvention，用于可绑定的关系表达式约定
import org.apache.calcite.plan.RelOptCluster; // 导入关系优化集群，包含一组关系表达式
import org.apache.calcite.plan.RelOptTable; // 导入优化表接口，表示优化过程中的表
import org.apache.calcite.rel.RelNode; // 导入关系表达式接口，是所有关系表达式的基类
import org.apache.calcite.rel.core.AggregateCall; // 导入聚合调用类，表示聚合函数调用
import org.apache.calcite.rel.core.TableScan; // 导入表扫描类，表示对表的扫描操作
import org.apache.calcite.rel.logical.LogicalTableScan; // 导入逻辑表扫描类，表示逻辑层面的表扫描
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示关系表达式的类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂，用于创建关系数据类型
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型接口，用于延迟创建数据类型
import org.apache.calcite.schema.Table; // 导入表接口，表示数据库表
import org.apache.calcite.schema.TranslatableTable; // 导入可翻译表接口，表示可以转换为关系表达式的表
import org.apache.calcite.schema.impl.AbstractTable; // 导入抽象表类，提供表的基本实现
import org.apache.calcite.sql.SqlCall; // 导入 SQL 调用接口，表示 SQL 函数调用
import org.apache.calcite.sql.SqlKind; // 导入 SQL 种类枚举，定义各种 SQL 操作类型
import org.apache.calcite.sql.SqlNode; // 导入 SQL 节点接口，表示 SQL 抽象语法树的节点
import org.apache.calcite.sql.SqlSelectKeyword; // 导入 SQL 选择关键字枚举，如 DISTINCT 等
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入 SQL 标准操作符表，包含标准 SQL 操作符
import org.apache.calcite.sql.type.SqlTypeName; // 导入 SQL 类型名称枚举，定义各种 SQL 数据类型

import com.google.common.collect.ImmutableList; // 导入 Guava 不可变列表类，提供线程安全的不可变列表
import com.google.common.collect.ImmutableMap; // 导入 Guava 不可变映射类，提供线程安全的不可变映射
import com.google.common.collect.ImmutableSet; // 导入 Guava 不可变集合类，提供线程安全的不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为 null 的值
import org.joda.time.DateTime; // 导入 Joda 日期时间类，用于处理日期时间
import org.joda.time.Interval; // 导入 Joda 时间间隔类，表示一个时间段
import org.joda.time.chrono.ISOChronology; // 导入 ISO 历法类，用于 ISO 标准的日期时间处理

import java.util.ArrayList; // 导入 Java 动态数组类，提供可变长度的数组实现
import java.util.List; // 导入 Java 列表接口，表示有序的元素集合
import java.util.Map; // 导入 Java 映射接口，表示键值对集合
import java.util.Set; // 导入 Java 集合接口，表示不重复元素的集合

import static com.google.common.base.Preconditions.checkArgument; // 导入前置条件检查方法，用于验证参数

import static java.util.Objects.requireNonNull; // 导入对象非空检查方法，确保对象不为 null

/**
 * Table mapped onto a Druid table.
 * 映射到 Druid 表的表类，用于将 Calcite 的表概念与 Druid 的数据源进行桥接
 * Druid 是一个分布式列式存储系统，主要用于实时 OLAP 分析
 * 该类实现了 TranslatableTable 接口，可以将表转换为关系表达式（RelNode）
 * 继承自 AbstractTable，提供了表的基本功能实现
 */
public class DruidTable extends AbstractTable implements TranslatableTable { // DruidTable 类定义，继承抽象表并实现可翻译表接口

  public static final String DEFAULT_TIMESTAMP_COLUMN = "__time"; // 默认时间戳列名，Druid 使用 __time 作为默认的时间列
  public static final Interval DEFAULT_INTERVAL = // 默认时间间隔，当查询没有指定时间约束时使用
      new Interval(new DateTime("1900-01-01", ISOChronology.getInstanceUTC()), // 时间间隔起始时间：1900年1月1日
          new DateTime("3000-01-01", ISOChronology.getInstanceUTC())); // 时间间隔结束时间：3000年1月1日

  final DruidSchema schema; // Druid Schema 对象，表示包含此表的 Druid 模式，用于访问 Druid 数据源
  final String dataSource; // Druid 数据源名称，对应 Druid 中的 table 或 dataSource
  final @Nullable RelProtoDataType protoRowType; // 行数据类型的原型，用于延迟创建 RelDataType，可能为 null
  final ImmutableSet<String> metricFieldNames; // 度量字段名称集合，不可变集合，包含所有度量（metric）字段的名称
  final ImmutableList<Interval> intervals; // 时间间隔列表，不可变列表，定义了查询的时间范围
  final String timestampFieldName; // 时间戳字段名称，用于标识时间列，通常是 __time
  final ImmutableMap<String, List<ComplexMetric>> complexMetrics; // 复杂度量的映射，不可变映射，键为字段别名，值为复杂度量列表
  final ImmutableMap<String, SqlTypeName> allFields; // 所有字段的映射，不可变映射，键为字段名，值为 SQL 类型名称

  /**
   * Creates a Druid table.
   * 创建一个 Druid 表对象，初始化所有必要的成员变量
   *
   * @param schema Druid schema that contains this table - 包含此表的 Druid 模式对象
   * @param dataSource Druid data source name - Druid 数据源名称，对应 Druid 中的表名
   * @param protoRowType Field names and types - 字段名称和类型的原型，用于描述表的行结构
   * @param metricFieldNames Names of fields that are metrics - 度量字段的名称集合，这些字段是聚合计算的结果
   * @param intervals Default interval if query does not constrain the time, or null - 默认时间间隔，当查询没有时间约束时使用
   * @param timestampFieldName Name of the column that contains the time - 包含时间的列名称，通常是 __time
   */
  public DruidTable(DruidSchema schema, String dataSource, // 构造方法开始，接收 Druid 模式和数据源名称
      @Nullable RelProtoDataType protoRowType, Set<String> metricFieldNames, // 接收行类型原型和度量字段名称集合
      String timestampFieldName, // 接收时间戳字段名称
      @Nullable List<Interval> intervals, // 接收时间间隔列表
      @Nullable Map<String, List<ComplexMetric>> complexMetrics, // 接收复杂度量映射
      @Nullable Map<String, SqlTypeName> allFields) { // 接收所有字段的类型映射
    this.timestampFieldName = // 初始化时间戳字段名称
        requireNonNull(timestampFieldName, "timestampFieldName"); // 确保时间戳字段名称不为 null，否则抛出异常
    this.schema = requireNonNull(schema, "schema"); // 初始化 Druid 模式，确保不为 null
    this.dataSource = requireNonNull(dataSource, "dataSource"); // 初始化数据源名称，确保不为 null
    this.protoRowType = protoRowType; // 初始化行类型原型
    this.metricFieldNames = ImmutableSet.copyOf(metricFieldNames); // 将度量字段名称集合转换为不可变集合
    this.intervals = // 初始化时间间隔列表
        intervals == null ? ImmutableList.of(DEFAULT_INTERVAL) // 如果 intervals 为 null，使用默认时间间隔
            : ImmutableList.copyOf(intervals); // 否则，复制传入的时间间隔列表为不可变列表
    this.complexMetrics = // 初始化复杂度量映射
        complexMetrics == null ? ImmutableMap.of() // 如果 complexMetrics 为 null，使用空映射
            : ImmutableMap.copyOf(complexMetrics); // 否则，复制传入的复杂度量映射为不可变映射
    this.allFields = // 初始化所有字段映射
        allFields == null ? ImmutableMap.of() // 如果 allFields 为 null，使用空映射
            : ImmutableMap.copyOf(allFields); // 否则，复制传入的字段映射为不可变映射
  }

  /** Creates a {@link DruidTable} by using the given {@link DruidConnectionImpl}
   * to populate the other parameters. The parameters may be partially populated.
   * 使用给定的 Druid 连接对象来填充其他参数，从而创建一个 Druid 表对象
   * 参数可能是部分填充的，通过连接对象获取完整的元数据信息
   *
   * @param druidSchema Druid schema - Druid 模式对象
   * @param dataSourceName Data source name in Druid, also table name - Druid 中的数据源名称，也作为表名
   * @param intervals Intervals, or null to use default - 时间间隔，如果为 null 则使用默认值
   * @param fieldMap Partially populated map of fields (dimensions plus metrics) - 部分填充的字段映射（维度和度量）
   * @param metricNameSet Partially populated set of metric names - 部分填充的度量名称集合
   * @param timestampColumnName Name of timestamp column, or null - 时间戳列名称，可能为 null
   * @param connection Connection used to find column definitions; Must be non-null - 用于查找列定义的 Druid 连接，必须非 null
   * @param complexMetrics List of complex metrics in Druid (thetaSketch, hyperUnique) - Druid 中的复杂度量列表（如 thetaSketch、hyperUnique）
   *
   * @return A table - 返回创建的表对象
   */
  static Table create(DruidSchema druidSchema, String dataSourceName, // 创建 Druid 表的静态方法
      List<Interval> intervals, Map<String, SqlTypeName> fieldMap, // 接收时间间隔和字段映射
      Set<String> metricNameSet, String timestampColumnName, // 接收度量名称集合和时间戳列名
      DruidConnectionImpl connection, Map<String, List<ComplexMetric>> complexMetrics) { // 接收 Druid 连接和复杂度量
    requireNonNull(connection, "connection"); // 确保 Druid 连接对象不为 null

    connection.metadata(dataSourceName, timestampColumnName, intervals, // 通过连接对象获取元数据信息，填充字段映射、度量名称集合等
            fieldMap, metricNameSet, complexMetrics); // 调用连接对象的 metadata 方法来获取完整的元数据

    return DruidTable.create(druidSchema, dataSourceName, intervals, fieldMap, // 调用另一个 create 方法重载，创建并返回 Druid 表对象
            metricNameSet, timestampColumnName, complexMetrics); // 使用填充后的参数创建表
  }

  /** Creates a {@link DruidTable} by copying the given parameters.
   * 通过复制给定的参数来创建一个 Druid 表对象
   * 这个方法假设所有参数都已经完全填充，不需要额外的元数据查询
   *
   * @param druidSchema Druid schema - Druid 模式对象
   * @param dataSourceName Data source name in Druid, also table name - Druid 中的数据源名称，也作为表名
   * @param intervals Intervals, or null to use default - 时间间隔，如果为 null 则使用默认值
   * @param fieldMap Fully populated map of fields (dimensions plus metrics) - 完全填充的字段映射（维度和度量）
   * @param metricNameSet Fully populated set of metric names - 完全填充的度量名称集合
   * @param timestampColumnName Name of timestamp column, or null - 时间戳列名称，可能为 null
   * @param complexMetrics List of complex metrics in Druid (thetaSketch, hyperUnique) - Druid 中的复杂度量列表（如 thetaSketch、hyperUnique）
   *
   * @return A table - 返回创建的 Druid 表对象
   */
  static Table create(DruidSchema druidSchema, String dataSourceName, // 创建 Druid 表的静态方法重载
      @Nullable List<Interval> intervals, Map<String, SqlTypeName> fieldMap, // 接收时间间隔和字段映射
      Set<String> metricNameSet, String timestampColumnName, // 接收度量名称集合和时间戳列名
      Map<String, List<ComplexMetric>> complexMetrics) { // 接收复杂度量映射
    final ImmutableMap<String, SqlTypeName> fields = // 创建字段映射的不可变副本
        ImmutableMap.copyOf(fieldMap); // 复制字段映射为不可变映射
    return new DruidTable(druidSchema, // 创建并返回新的 DruidTable 对象
        dataSourceName, // 传入数据源名称
        new MapRelProtoDataType(fields, timestampColumnName), // 创建行类型原型对象
        ImmutableSet.copyOf(metricNameSet), // 将度量名称集合转换为不可变集合
        timestampColumnName, // 传入时间戳列名称
        intervals, // 传入时间间隔列表
        complexMetrics, // 传入复杂度量映射
        fieldMap); // 传入字段映射
  }

  /**
   * Returns the appropriate {@link ComplexMetric} that is mapped from the given <code>alias</code>
   * if it exists, and is used in the expected context with the given {@link AggregateCall}.
   * Otherwise, returns <code>null</code>.
   * 返回与给定别名映射的适当复杂度量，如果该度量存在并且在给定的聚合调用上下文中可以使用
   * 如果没有找到合适的复杂度量，则返回 null
   * 复杂度量是 Druid 中特殊类型的度量，如 thetaSketch、hyperUnique 等，用于近似计算
   */
  public @Nullable ComplexMetric resolveComplexMetric(String alias, // 解析复杂度量的方法
      AggregateCall call) { // 接收别名和聚合调用对象
    List<ComplexMetric> potentialMetrics = getComplexMetricsFrom(alias); // 根据别名获取潜在的复杂度量列表

    // It's possible that multiple complex metrics match the AggregateCall,
    // but for now we only return the first that matches
    // 可能有多个复杂度量匹配聚合调用，但目前我们只返回第一个匹配的
    for (ComplexMetric complexMetric : potentialMetrics) { // 遍历所有潜在的复杂度量
      if (complexMetric.canBeUsed(call)) { // 检查该复杂度量是否可以在当前聚合调用中使用
        return complexMetric; // 如果可以使用，返回这个复杂度量
      }
    }

    return null; // 如果没有找到匹配的复杂度量，返回 null
  }

  @Override public boolean isRolledUp(String column) { // 重写 isRolledUp 方法，判断给定列是否是上卷列
    // The only rolled up columns we care about are Complex Metrics (aka sketches).
    // 我们关心的唯一上卷列是复杂度量（也称为草图），这些通常是近似计算的数据结构
    // But we also need to check if this column name is a dimension
    // 但我们还需要检查此列名是否是维度，维度列不是上卷列
    return complexMetrics.get(column) != null // 如果该列名在复杂度量映射中存在
            && allFields.get(column) != SqlTypeName.VARCHAR; // 并且该列的类型不是 VARCHAR（VARCHAR 通常是维度类型）
  }

  @Override public boolean rolledUpColumnValidInsideAgg(String column, SqlCall call, // 重写 rolledUpColumnValidInsideAgg 方法，判断上卷列在聚合中是否有效
      @Nullable SqlNode parent, @Nullable CalciteConnectionConfig config) { // 接收列名、聚合调用、父节点和连接配置
    assert isRolledUp(column); // 断言该列是上卷列，如果不是则抛出异常
    // Our rolled up columns are only allowed in COUNT(DISTINCT ...) aggregate functions.
    // 我们的上卷列只允许在 COUNT(DISTINCT ...) 聚合函数中使用
    // We only allow this when approximate results are acceptable.
    // 只有当允许近似结果时，才允许使用这些上卷列
    return ((config != null // 检查配置不为 null
                && config.approximateDistinctCount() // 并且配置允许近似去重计数
                && isCountDistinct(call)) // 并且当前调用是 COUNT(DISTINCT)
            || call.getOperator() == SqlStdOperatorTable.APPROX_COUNT_DISTINCT) // 或者操作符是 APPROX_COUNT_DISTINCT
        && call.getOperandList().size() == 1 // for COUNT(a_1, a_2, ... a_n). n should be 1 - 确保只有一个操作数
        && isValidParentKind(parent); // 并且父节点的类型是有效的
  }

  private static boolean isValidParentKind(SqlNode node) { // 私有静态方法，判断父节点的类型是否有效
    return node.getKind() == SqlKind.SELECT // 如果父节点是 SELECT 语句，则有效
            || node.getKind() == SqlKind.FILTER // 或者父节点是 FILTER 语句，则有效
            || isSupportedPostAggOperation(node.getKind()); // 或者父节点是支持的后聚合操作，则有效
  }

  private static boolean isCountDistinct(SqlCall call) { // 私有静态方法，判断聚合调用是否是 COUNT(DISTINCT)
    return call.getKind() == SqlKind.COUNT // 首先检查操作类型是否是 COUNT
            && call.getFunctionQuantifier() != null // 并且函数限定符不为 null（DISTINCT 就是一个限定符）
            && call.getFunctionQuantifier().getValue() == SqlSelectKeyword.DISTINCT; // 并且限定符的值是 DISTINCT
  }

  // Post aggs support +, -, /, * so we should allow the parent of a count distinct to be any one of
  // those.
  // 后聚合操作支持 +, -, /, *，因此我们应该允许 count distinct 的父节点是这些操作中的任何一个
  // 后聚合操作是在 Druid 中对聚合结果进行进一步计算的操作
  private static boolean isSupportedPostAggOperation(SqlKind kind) { // 私有静态方法，判断是否是支持的后聚合操作
    return kind == SqlKind.PLUS // 如果是加法操作
            || kind == SqlKind.MINUS // 或者是减法操作
            || kind == SqlKind.DIVIDE // 或者是除法操作
            || kind == SqlKind.TIMES; // 或者是乘法操作
  }

  /** Returns the list of {@link ComplexMetric} that match the given
   * <code>alias</code> if it exists, otherwise returns an empty list, never
   * <code>null</code>.
   * 返回与给定别名匹配的复杂度量列表，如果存在则返回对应的列表
   * 如果不存在则返回空列表，永远不会返回 null
   */
  public List<ComplexMetric> getComplexMetricsFrom(String alias) { // 获取与给定别名关联的复杂度量列表
    return complexMetrics.containsKey(alias) // 如果复杂度量映射中包含该别名
            ? complexMetrics.get(alias) // 则返回对应的复杂度量列表
            : new ArrayList<>(); // 否则返回一个新的空数组列表
  }

  /** Returns whether the given <code>alias</code> is a reference to a
   * registered {@link ComplexMetric}.
   * 返回给定的别名是否是对已注册复杂度量的引用
   * 复杂度量是 Druid 中特殊的度量类型，用于近似计算，如 thetaSketch、hyperUnique 等
   */
  public boolean isComplexMetric(String alias) { // 判断给定的别名是否是复杂度量
    return complexMetrics.get(alias) != null; // 如果在复杂度量映射中能找到该别名，则返回 true
  }

  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写 getRowType 方法，获取表的行类型
    final RelDataType rowType = protoRowType.apply(typeFactory); // 使用行类型原型和类型工厂创建实际的行类型
    final List<String> fieldNames = rowType.getFieldNames(); // 获取行类型中的所有字段名称
    checkArgument(fieldNames.contains(timestampFieldName)); // 检查字段名列表中包含时间戳字段名
    checkArgument(fieldNames.containsAll(metricFieldNames)); // 检查字段名列表中包含所有度量字段名
    return rowType; // 返回创建的行类型
  }

  @Override public RelNode toRel(RelOptTable.ToRelContext context, // 重写 toRel 方法，将表转换为关系表达式
      RelOptTable relOptTable) { // 接收转换上下文和优化表对象
    final RelOptCluster cluster = context.getCluster(); // 从上下文中获取关系优化集群
    final TableScan scan = LogicalTableScan.create(cluster, relOptTable, ImmutableList.of()); // 创建逻辑表扫描节点
    return DruidQuery.create(cluster, // 创建并返回 Druid 查询节点
        cluster.traitSetOf(BindableConvention.INSTANCE), relOptTable, this, // 使用 BindableConvention 特性集
        ImmutableList.of(scan)); // 将表扫描节点作为 Druid 查询的输入
  }

  public boolean isMetric(String name) { // 判断给定的字段名是否是度量字段
    return metricFieldNames.contains(name); // 检查度量字段名称集合中是否包含该字段名
  }

  /** Creates a {@link RelDataType} from a map of
   * field names and types.
   * 从字段名称和类型的映射创建关系数据类型
   * 这是一个内部静态类，实现了 RelProtoDataType 接口
   * 用于将字段映射转换为 Calcite 的关系数据类型
   */
  private static class MapRelProtoDataType implements RelProtoDataType { // 内部静态类，实现关系原型数据类型接口
    private final ImmutableMap<String, SqlTypeName> fields; // 字段名到 SQL 类型的不可变映射
    private final String timestampColumn; // 时间戳列的名称

    MapRelProtoDataType(ImmutableMap<String, SqlTypeName> fields, String timestampColumn) { // 构造方法
      this.fields = fields; // 初始化字段映射
      this.timestampColumn = timestampColumn; // 初始化时间戳列名
    }

    @Override public RelDataType apply(RelDataTypeFactory typeFactory) { // 实现 apply 方法，创建关系数据类型
      final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 使用类型工厂创建构建器
      for (Map.Entry<String, SqlTypeName> field : fields.entrySet()) { // 遍历所有字段
        final String key = field.getKey(); // 获取字段名
        builder.add(key, field.getValue()) // 向构建器添加字段及其类型
            // Druid's time column is always not null and the only column called __time.
            // Druid 的时间列总是非 null 的，并且是唯一名为 __time 的列
            .nullable(!timestampColumn.equals(key)); // 如果不是时间戳列，则设置为可空；时间戳列不可空
      }
      return builder.build(); // 构建并返回关系数据类型
    }
  }
}
