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
package org.apache.calcite.adapter.innodb;

// AbstractQueryableTable：Calcite中可查询表的抽象基类，提供了LINQ查询能力
import org.apache.calcite.adapter.java.AbstractQueryableTable;
// AbstractEnumerable：抽象可枚举类，提供LINQ枚举能力
import org.apache.calcite.linq4j.AbstractEnumerable;
// Enumerable：可枚举接口，支持LINQ查询操作
import org.apache.calcite.linq4j.Enumerable;
// Enumerator：枚举器接口，用于逐个访问集合元素
import org.apache.calcite.linq4j.Enumerator;
// QueryProvider：查询提供者接口，提供查询执行能力
import org.apache.calcite.linq4j.QueryProvider;
// Queryable：可查询接口，支持LINQ查询表达式
import org.apache.calcite.linq4j.Queryable;
// Function1：单参数函数接口，用于函数式编程
import org.apache.calcite.linq4j.function.Function1;
// RelOptCluster：关系优化集群，包含优化器所需的上下文信息
import org.apache.calcite.plan.RelOptCluster;
// RelOptTable：关系优化表，包含表的优化信息
import org.apache.calcite.plan.RelOptTable;
// RelNode：关系节点，表示关系代数操作
import org.apache.calcite.rel.RelNode;
// RelDataType：关系数据类型，表示表或字段的数据类型
import org.apache.calcite.rel.type.RelDataType;
// RelDataTypeFactory：关系数据类型工厂，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// RelDataTypeField：关系数据类型字段，表示表中的列
import org.apache.calcite.rel.type.RelDataTypeField;
// RelDataTypeImpl：关系数据类型实现类
import org.apache.calcite.rel.type.RelDataTypeImpl;
// RelDataTypeSystem：关系数据类型系统，定义类型系统的规则
import org.apache.calcite.rel.type.RelDataTypeSystem;
// RelProtoDataType：关系数据类型原型，用于在不同类型工厂中创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType;
// SchemaPlus：扩展的Schema接口，支持unwrap操作
import org.apache.calcite.schema.SchemaPlus;
// TranslatableTable：可转换表接口，允许表转换为关系代数节点
import org.apache.calcite.schema.TranslatableTable;
// AbstractTableQueryable：抽象表可查询类，提供表查询的基础实现
import org.apache.calcite.schema.impl.AbstractTableQueryable;
// SqlTypeFactoryImpl：SQL类型工厂实现，用于创建SQL数据类型
import org.apache.calcite.sql.type.SqlTypeFactoryImpl;

// Constants：InnoDB常量定义，如主键名称等
import com.alibaba.innodb.java.reader.Constants;
// TableReader：InnoDB表读取器，用于读取InnoDB数据文件
import com.alibaba.innodb.java.reader.TableReader;
// TableReaderFactory：InnoDB表读取器工厂，用于创建表读取器实例
import com.alibaba.innodb.java.reader.TableReaderFactory;
// ComparisonOperator：比较操作符，如>=、<=等
import com.alibaba.innodb.java.reader.comparator.ComparisonOperator;
// GenericRecord：通用记录接口，表示InnoDB中的一行数据
import com.alibaba.innodb.java.reader.page.index.GenericRecord;
// KeyMeta：键元数据，描述索引的元数据信息
import com.alibaba.innodb.java.reader.schema.KeyMeta;
// TableDef：表定义，描述InnoDB表的完整结构
import com.alibaba.innodb.java.reader.schema.TableDef;
// RecordIterator：记录迭代器，用于遍历查询结果
import com.alibaba.innodb.java.reader.service.impl.RecordIterator;
// Suppliers：Guava工具类，提供延迟加载和缓存功能
import com.google.common.base.Suppliers;
// ImmutableList：Guava不可变列表，线程安全的列表实现
import com.google.common.collect.ImmutableList;
// ImmutableSet：Guava不可变集合，线程安全的集合实现
import com.google.common.collect.ImmutableSet;

// Logger：SLF4J日志接口，用于记录日志
import org.slf4j.Logger;
// LoggerFactory：SLF4J日志工厂，用于创建Logger实例
import org.slf4j.LoggerFactory;

// ArrayList：Java集合框架的动态数组实现
import java.util.ArrayList;
// Iterator：迭代器接口，用于遍历集合
import java.util.Iterator;
// List：列表接口，表示有序集合
import java.util.List;
// Map：映射接口，表示键值对集合
import java.util.Map;
// Set：集合接口，表示无序不重复集合
import java.util.Set;
// Supplier：Java 8函数式接口，提供延迟加载功能
import java.util.function.Supplier;
// Collectors：Java 8流收集器，用于流的聚合操作
import java.util.stream.Collectors;

// requireNonNull：Objects工具方法，用于检查对象是否为null
import static java.util.Objects.requireNonNull;

/**
 * Table based on an InnoDB data file.
 */
// InnodbTable类：基于InnoDB数据文件的表实现，继承自AbstractQueryableTable并实现TranslatableTable接口
// 该类提供了对InnoDB数据文件的访问能力，支持多种查询模式：
// 1. 主键点查询（PK_POINT_QUERY）：根据主键值精确查找单条记录
// 2. 主键范围查询（PK_RANGE_QUERY）：根据主键范围查找多条记录
// 3. 二级索引点查询（SK_POINT_QUERY）：根据二级索引值精确查找记录
// 4. 二级索引范围查询（SK_RANGE_QUERY）：根据二级索引范围查找记录
// 5. 主键全扫描（PK_FULL_SCAN）：扫描整个主键索引
// 6. 二级索引全扫描（SK_FULL_SCAN）：扫描整个二级索引
// 
// 该类还支持查询下推，将索引条件下推到存储层执行，提高查询性能
// 通过使用Supplier和memoize机制，延迟加载并缓存行类型和表定义，避免重复计算
// AbstractQueryableTable：Calcite中可查询表的抽象基类，提供了LINQ查询能力
// TranslatableTable：可转换表的接口，允许表转换为关系代数节点
public class InnodbTable extends AbstractQueryableTable
    implements TranslatableTable {
  // 日志记录器，用于记录调试信息和错误信息
  private static final Logger LOGGER = LoggerFactory.getLogger(InnodbTable.class);

  // 所属的InnoDB Schema对象，包含表的元数据和配置信息
  private final InnodbSchema schema;
  // 表名，标识InnoDB数据文件中的具体表
  private final String tableName;
  // 行类型数据原型提供者，使用Supplier延迟加载并通过memoize缓存结果，避免重复计算
  // RelProtoDataType：关系数据类型原型，用于在不同类型工厂中创建数据类型
  private final Supplier<RelProtoDataType> protoRowTypeSupplier =
      Suppliers.memoize(this::supplyProto);
  // 表定义提供者，使用Supplier延迟加载并通过memoize缓存结果
  // TableDef：InnoDB表的完整定义，包含列信息、索引信息等元数据
  private final Supplier<TableDef> tableDefSupplier =
      Suppliers.memoize(this::supplyTableDef);

  // 构造方法：创建InnodbTable实例
  // 参数schema：所属的InnoDB Schema对象
  // 参数tableName：表名
  public InnodbTable(InnodbSchema schema, String tableName) {
    // 调用父类构造方法，指定每行数据的类型为Object[]数组
    super(Object[].class);
    this.schema = schema;
    this.tableName = tableName;
  }

  // 重写toString方法，返回表的字符串表示形式
  // 返回值：包含表名的字符串，格式为"InnodbTable {tableName}"
  @Override public String toString() {
    return "InnodbTable {" + tableName + "}";
  }

  // 私有方法：提供关系数据类型原型
  // 返回值：从Schema中获取的RelProtoDataType对象
  // 该方法通过protoRowTypeSupplier的memoize机制缓存结果，避免重复调用
  private RelProtoDataType supplyProto() {
    return schema.getRelDataType(tableName);
  }

  // 重写方法：获取表的行数据类型
  // 参数typeFactory：关系数据类型工厂，用于创建具体的数据类型
  // 返回值：表的行数据类型，包含所有列的类型信息
  // 该方法通过protoRowTypeSupplier获取原型，然后应用到指定的类型工厂
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    return protoRowTypeSupplier.get().apply(typeFactory);
  }

  // 公共方法：获取InnoDB表的定义
  // 返回值：TableDef对象，包含表的完整元数据定义
  // 该方法通过tableDefSupplier的memoize机制缓存结果
  public TableDef getTableDef() {
    return tableDefSupplier.get();
  }

  // 私有方法：提供表定义
  // 返回值：从Schema中获取的TableDef对象
  // 该方法通过tableDefSupplier的memoize机制缓存结果，避免重复调用
  private TableDef supplyTableDef() {
    return schema.getTableDef(tableName);
  }

  /**
   * Get index name set.
   *
   * @return set of index names
   */
  // 获取表的所有索引名称集合
  // 返回值：包含主键和所有二级索引名称的不可变集合
  public Set<String> getIndexesNameSet() {
    // 创建不可变集合构建器
    return ImmutableSet.<String>builder()
        // 添加主键名称（PRIMARY）
        .add(Constants.PRIMARY_KEY_NAME)
        // 添加所有二级索引的名称：从表定义中获取二级键元数据列表，流式处理提取名称
        .addAll(getTableDef().getSecondaryKeyMetaList().stream()
            .map(KeyMeta::getName).collect(Collectors.toList()))
        // 构建不可变集合
        .build();
  }

  // 公共方法：执行查询（无参数版本）
  // 参数tableReaderFactory：InnoDB表读取器工厂，用于创建表读取器实例
  // 返回值：可枚举的结果集，包含查询返回的所有行数据
  // 该方法使用默认参数调用完整的query方法：空字段列表、空选择字段列表、空索引条件、升序排序
  public Enumerable<Object> query(final TableReaderFactory tableReaderFactory) {
    return query(tableReaderFactory, ImmutableList.of(), ImmutableList.of(),
        IndexCondition.EMPTY_CONDITION, true);
  }

  /**
   * Executes a query on the underlying InnoDB table.
   *
   * @param tableReaderFactory InnoDB Java table reader factory
   * @param fields             list of fields
   * @param selectFields       list of fields to project
   * @param condition          push down index condition
   * @param ascOrder           if scan ordering is ascending
   * @return Enumerator of results
   */
  // 核心方法：在底层InnoDB表上执行查询
  // 参数tableReaderFactory：InnoDB Java表读取器工厂，用于创建表读取器
  // 参数fields：字段列表，包含字段名和字段类型的映射
  // 参数selectFields：需要投影的字段列表，只返回这些字段的数据
  // 参数condition：下推的索引条件，包含查询类型、范围条件等信息
  // 参数ascOrder：是否按升序扫描
  // 返回值：可枚举的结果集，包含查询返回的所有行数据
  public Enumerable<Object> query(
      final TableReaderFactory tableReaderFactory,
      final List<Map.Entry<String, Class>> fields,
      final List<Map.Entry<String, String>> selectFields,
      final IndexCondition condition,
      final Boolean ascOrder) {
    // 从索引条件中提取查询类型：点查询、范围查询或全表扫描
    final QueryType queryType = condition.getQueryType();
    // 提取点查询的键值列表
    final List<Object> pointQueryKey = condition.getPointQueryKey();
    // 提取范围查询的下界比较操作符（如>=、>）
    final ComparisonOperator rangeQueryLowerOp = condition.getRangeQueryLowerOp();
    // 提取范围查询的下界键值列表
    final List<Object> rangeQueryLowerKey = condition.getRangeQueryLowerKey();
    // 提取范围查询的上界比较操作符（如<=、<）
    final ComparisonOperator rangeQueryUpperOp = condition.getRangeQueryUpperOp();
    // 提取范围查询的上界键值列表
    final List<Object> rangeQueryUpperKey = condition.getRangeQueryUpperKey();
    // 提取要使用的索引名称（主键或二级索引）
    final String indexName = condition.getIndexName();

    // Build the type of the resulting row based on the provided fields
    // 创建SQL类型工厂实现，使用默认的关系数据类型系统
    final RelDataTypeFactory typeFactory =
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT);
    // 创建类型工厂构建器，用于构建结果行的数据类型
    final RelDataTypeFactory.Builder fieldInfo = typeFactory.builder();
    // 获取表的行数据类型，包含所有字段的类型信息
    final RelDataType rowType = getRowType(typeFactory);

    // 定义函数：将字段添加到结果类型构建器中
    // 参数fieldName：字段名称
    // 返回值：Void（函数式接口要求）
    Function1<String, Void> addField = fieldName -> {
      // 从行类型中获取指定字段的元数据，如果不存在则抛出异常
      final RelDataTypeField field =
          requireNonNull(rowType.getField(fieldName, true, false));
      // 获取字段的数据类型
      RelDataType relDataType = field.getType();
      // 将字段添加到构建器中，保留字段的nullable属性
      fieldInfo.add(fieldName, relDataType).nullable(relDataType.isNullable());
      return null;
    };

    // 创建选中列名称列表，初始化容量为selectFields的大小
    List<String> selectedColumnNames = new ArrayList<>(selectFields.size());
    // 如果没有指定选择字段，则使用所有字段
    if (selectFields.isEmpty()) {
      // 遍历所有字段，将每个字段添加到结果类型中
      for (Map.Entry<String, Class> field : fields) {
        addField.apply(field.getKey());
      }
    } else {
      // 如果指定了选择字段，则只使用这些字段
      for (Map.Entry<String, String> field : selectFields) {
        // 将字段添加到结果类型中
        addField.apply(field.getKey());
        // 记录选中的列名称，用于后续查询
        selectedColumnNames.add(field.getKey());
      }
    }

    // 创建结果行数据类型原型，用于后续创建具体的数据类型
    final RelProtoDataType resultRowType = RelDataTypeImpl.proto(fieldInfo.build());

    // 使用表读取器工厂创建指定表的读取器实例
    TableReader tableReader = tableReaderFactory.createTableReader(tableName);
    // 打开表读取器，准备读取数据
    tableReader.open();
    // 返回一个抽象的可枚举对象，该对象提供枚举器来遍历查询结果
    return new AbstractEnumerable<Object>() {
      // 重写enumerator方法，创建结果枚举器
      @Override public Enumerator<Object> enumerator() {
        // 声明结果迭代器，用于遍历查询结果
        Iterator<GenericRecord> resultIterator;
        // 记录调试日志，输出查询参数信息，便于问题诊断
        LOGGER.debug("Create query iterator, queryType={}, indexName={}, "
                + "pointQueryKey={}, projection={}, rangeQueryKey={}{} AND {}{}, "
                + "ascOrder={}", queryType, indexName, pointQueryKey,
            selectedColumnNames, rangeQueryLowerKey, rangeQueryLowerOp,
            rangeQueryUpperKey, rangeQueryUpperOp, ascOrder);
        // 根据查询类型创建相应的结果迭代器
        switch (queryType) {
          // 主键点查询：根据主键值精确查找单条记录
          case PK_POINT_QUERY:
            // 使用主键查询，返回单条记录的迭代器
            resultIterator =
                RecordIterator.create(tableReader
                    .queryByPrimaryKey(pointQueryKey, selectedColumnNames));
            break;
          // 主键范围查询：根据主键范围查找多条记录
          case PK_RANGE_QUERY:
            // 使用主键范围查询，返回范围内记录的迭代器
            resultIterator =
                tableReader.getRangeQueryIterator(rangeQueryLowerKey,
                    rangeQueryLowerOp, rangeQueryUpperKey, rangeQueryUpperOp,
                    selectedColumnNames, ascOrder);
            break;
          // 二级索引点查询：根据二级索引值精确查找记录
          case SK_POINT_QUERY:
            // 使用二级索引查询，将点查询转换为范围查询（>= AND <=）
            resultIterator =
                tableReader.getRecordIteratorBySk(indexName, pointQueryKey,
                    ComparisonOperator.GTE, pointQueryKey,
                    ComparisonOperator.LTE, selectedColumnNames, ascOrder);
            break;
          // 二级索引范围查询：根据二级索引范围查找记录
          case SK_RANGE_QUERY:
          // 二级索引全扫描：扫描整个二级索引
          case SK_FULL_SCAN:
            // 使用二级索引范围查询或全扫描
            resultIterator =
                tableReader.getRecordIteratorBySk(indexName, rangeQueryLowerKey,
                    rangeQueryLowerOp, rangeQueryUpperKey, rangeQueryUpperOp,
                    selectedColumnNames, ascOrder);
            break;
          // 主键全扫描：扫描整个主键索引
          case PK_FULL_SCAN:
            // 查询所有记录，按主键顺序返回
            resultIterator =
                tableReader.getQueryAllIterator(selectedColumnNames, ascOrder);
            break;
          // 默认情况：查询类型无效，抛出断言错误
          default:
            throw new AssertionError("query type is invalid");
        }

        // 将结果行数据类型原型应用到类型工厂，创建具体的数据类型
        RelDataType rowType = resultRowType.apply(typeFactory);
        // 创建InnodbEnumerator实例，用于枚举查询结果
        // 参数resultIterator：结果迭代器
        // 参数rowType：结果行的数据类型
        return new InnodbEnumerator(resultIterator, rowType) {
          // 重写close方法，在枚举器关闭时释放资源
          @Override public void close() {
            // 调用父类的close方法，关闭枚举器
            super.close();
            // 关闭表读取器，释放底层资源
            tableReader.close();
          }
        };
      }
    };
  }

  // 重写方法：将表转换为可查询对象
  // 参数queryProvider：查询提供者，提供查询执行能力
  // 参数schema：Schema Plus对象，包含表所在的Schema
  // 参数tableName：表名
  // 返回值：可查询对象，支持LINQ查询操作
  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
      SchemaPlus schema, String tableName) {
    // 创建并返回InnodbQueryable实例
    return new InnodbQueryable<>(queryProvider, schema, this, tableName);
  }

  // 重写方法：将表转换为关系代数节点
  // 参数context：转换上下文，包含集群等优化信息
  // 参数relOptTable：关系优化表对象
  // 返回值：InnodbTableScan节点，表示对InnoDB表的扫描操作
  @Override public RelNode toRel(RelOptTable.ToRelContext context,
      RelOptTable relOptTable) {
    // 从上下文中获取关系优化集群
    final RelOptCluster cluster = context.getCluster();
    // 创建InnodbTableScan节点，表示表扫描操作
    // 参数cluster：关系优化集群
    // 参数traitSet：特征集合，指定使用InnoDB约定
    // 参数relOptTable：关系优化表对象
    // 参数this：InnodbTable实例
    // 参数null：索引，null表示使用主键或全表扫描
    // 参数context.getTableHints()：表提示信息
    return new InnodbTableScan(cluster, cluster.traitSetOf(InnodbRel.CONVENTION),
        relOptTable, this, null, context.getTableHints());
  }

  /**
   * Implementation of {@link org.apache.calcite.linq4j.Queryable} based on
   * a {@link org.apache.calcite.adapter.innodb.InnodbTable}.
   *
   * @param <T> element type
   */
  // InnodbQueryable内部类：基于InnodbTable的可查询实现
  // 继承自AbstractTableQueryable，提供了LINQ查询能力
  // 泛型参数T：元素类型，表示查询结果的行类型
  public static class InnodbQueryable<T> extends AbstractTableQueryable<T> {
    // 构造方法：创建InnodbQueryable实例
    // 参数queryProvider：查询提供者，提供查询执行能力
    // 参数schema：Schema Plus对象，包含表所在的Schema
    // 参数table：InnodbTable实例
    // 参数tableName：表名
    public InnodbQueryable(QueryProvider queryProvider, SchemaPlus schema,
        InnodbTable table, String tableName) {
      // 调用父类构造方法，初始化查询提供者、Schema、表和表名
      super(queryProvider, schema, table, tableName);
    }

    // 重写方法：创建枚举器，用于遍历查询结果
    // 返回值：枚举器对象，用于逐行访问查询结果
    @Override public Enumerator<T> enumerator() {
      // 调用表的query方法执行查询，获取可枚举的结果集
      //noinspection unchecked
      final Enumerable<T> enumerable =
          (Enumerable<T>) getTable().query(getTableReaderFactory());
      // 从可枚举对象中获取枚举器
      return enumerable.enumerator();
    }

    // 私有方法：获取InnodbTable实例
    // 返回值：InnodbTable对象，类型转换自父类的table字段
    private InnodbTable getTable() {
      return (InnodbTable) table;
    }

    // 私有方法：获取表读取器工厂
    // 返回值：TableReaderFactory实例，用于创建表读取器
    private TableReaderFactory getTableReaderFactory() {
      // 从Schema中解包获取InnodbSchema实例
      final InnodbSchema innodbSchema =
          requireNonNull(schema.unwrap(InnodbSchema.class));
      // 返回InnodbSchema中的表读取器工厂
      return innodbSchema.tableReaderFactory;
    }

    /**
     * Called via code-generation.
     *
     * @see org.apache.calcite.adapter.innodb.InnodbMethod#INNODB_QUERYABLE_QUERY
     */
    // 公共方法：执行查询，通过代码生成调用
    // 该方法支持Calcite的代码生成机制，在运行时动态调用
    // 参数fields：字段列表，包含字段名和字段类型的映射
    // 参数selectFields：需要投影的字段列表
    // 参数condition：下推的索引条件
    // 参数ascOrder：是否按升序扫描
    // 返回值：可枚举的结果集
    @SuppressWarnings("UnusedDeclaration")
    public Enumerable<Object> query(List<Map.Entry<String, Class>> fields,
        List<Map.Entry<String, String>> selectFields,
        IndexCondition condition, Boolean ascOrder) {
      // 委托给InnodbTable的query方法执行查询
      return getTable().query(getTableReaderFactory(), fields, selectFields,
          condition, ascOrder);
    }
  }
}
