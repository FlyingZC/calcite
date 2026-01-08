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
// Apache许可证声明，声明代码版权和使用许可
package org.apache.calcite.adapter.jdbc; // 声明包名，表示该类属于JDBC适配器包

// 导入Calcite核心类：DataContext提供数据上下文环境
import org.apache.calcite.DataContext;
// 导入抽象可查询表基类，JdbcTable继承此类以支持LINQ查询
import org.apache.calcite.adapter.java.AbstractQueryableTable;
// 导入Java类型工厂，用于在Java类型和SQL类型之间转换
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入列元数据表示类，用于描述列的属性
import org.apache.calcite.avatica.ColumnMetaData;
// 导入Calcite连接类，提供Calcite特定的连接功能
import org.apache.calcite.jdbc.CalciteConnection;
// 导入可枚举接口，表示可以遍历的数据集合
import org.apache.calcite.linq4j.Enumerable;
// 导入枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.Enumerator;
// 导入查询提供者接口，用于创建和执行查询
import org.apache.calcite.linq4j.QueryProvider;
// 导入可查询接口，支持LINQ查询操作
import org.apache.calcite.linq4j.Queryable;
// 导入约定接口，定义关系代数操作的调用约定
import org.apache.calcite.plan.Convention;
// 导入关系优化集群，包含一组关系表达式和共享环境
import org.apache.calcite.plan.RelOptCluster;
// 导入关系优化表，表示优化器中的表引用
import org.apache.calcite.plan.RelOptTable;
// 导入目录读取器，用于读取元数据目录信息
import org.apache.calcite.prepare.Prepare.CatalogReader;
// 导入关系节点接口，所有关系表达式都实现此接口
import org.apache.calcite.rel.RelNode;
// 导入表修改节点，表示INSERT、UPDATE、DELETE等修改操作
import org.apache.calcite.rel.core.TableModify;
// 导入表修改操作枚举，定义INSERT、UPDATE、DELETE等操作类型
import org.apache.calcite.rel.core.TableModify.Operation;
// 导入逻辑表修改节点，表示逻辑层面的表修改操作
import org.apache.calcite.rel.logical.LogicalTableModify;
// 导入关系数据类型接口，表示表或表达式的数据类型
import org.apache.calcite.rel.type.RelDataType;
// 导入关系数据类型工厂接口，用于创建关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory;
// 导入关系原型数据类型接口，可以延迟创建数据类型
import org.apache.calcite.rel.type.RelProtoDataType;
// 导入行表达式节点接口，表示关系表达式中的表达式
import org.apache.calcite.rex.RexNode;
// 导入结果集可枚举类，用于将JDBC结果集转换为可枚举对象
import org.apache.calcite.runtime.ResultSetEnumerable;
// 导入可修改表接口，支持表的修改操作
import org.apache.calcite.schema.ModifiableTable;
// 导入可扫描表接口，支持表的扫描操作
import org.apache.calcite.schema.ScannableTable;
// 导入Schema接口，表示数据库模式
import org.apache.calcite.schema.Schema;
// 导入SchemaPlus接口，扩展了Schema接口，提供更多功能
import org.apache.calcite.schema.SchemaPlus;
// 导入可转换表接口，支持将表转换为关系表达式
import org.apache.calcite.schema.TranslatableTable;
// 导入抽象表可查询类，为表提供LINQ查询功能
import org.apache.calcite.schema.impl.AbstractTableQueryable;
// 导入SQL标识符类，表示SQL中的标识符（如表名、列名）
import org.apache.calcite.sql.SqlIdentifier;
// 导入SQL节点列表类，表示SQL节点的列表
import org.apache.calcite.sql.SqlNodeList;
// 导入SQL SELECT语句类，表示SQL查询语句
import org.apache.calcite.sql.SqlSelect;
// 导入SQL写入器配置类，配置SQL格式化输出
import org.apache.calcite.sql.SqlWriterConfig;
// 导入SQL解析位置类，表示SQL语法元素在源代码中的位置
import org.apache.calcite.sql.parser.SqlParserPos;
// 导入SQL美化写入器类，用于格式化SQL输出
import org.apache.calcite.sql.pretty.SqlPrettyWriter;
// 导入SQL字符串类，封装SQL语句及其方言信息
import org.apache.calcite.sql.util.SqlString;
// 导入Pair类，表示键值对
import org.apache.calcite.util.Pair;
// 导入工具类，提供各种实用方法
import org.apache.calcite.util.Util;

// 导入Google Guava的Suppliers类，提供延迟加载功能
import com.google.common.base.Suppliers;

// 导入可空注解，用于标记可能为null的返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入SQL异常类，处理JDBC操作中的异常
import java.sql.SQLException;
// 导入ArrayList类，动态数组实现
import java.util.ArrayList;
// 导入Collection接口，表示集合
import java.util.Collection;
// 导入List接口，表示有序集合
import java.util.List;
// 导入Supplier函数式接口，用于延迟计算
import java.util.function.Supplier;

// 静态导入requireNonNull方法，用于参数非空检查
import static java.util.Objects.requireNonNull;

/**
 * Queryable that gets its data from a table within a JDBC connection.
 * 可查询对象，从JDBC连接中的表获取数据
 *
 * <p>The idea is not to read the whole table, however. The idea is to use
 * this as a building block for a query, by applying Queryable operators
 * such as
 * {@link org.apache.calcite.linq4j.Queryable#where(org.apache.calcite.linq4j.function.Predicate2)}.
 * The resulting queryable can then be converted to a SQL query, which can be
 * executed efficiently on the JDBC server.
 * 核心思想不是读取整个表，而是将其作为查询的构建块，通过应用可查询操作符（如where过滤），
 * 生成的可查询对象可以转换为SQL查询，然后在JDBC服务器上高效执行
 */
// JdbcTable类定义：继承AbstractQueryableTable以支持LINQ查询
// 实现TranslatableTable接口，支持转换为关系表达式
// 实现ScannableTable接口，支持表扫描操作
// 实现ModifiableTable接口，支持表修改操作
public class JdbcTable extends AbstractQueryableTable
    implements TranslatableTable, ScannableTable, ModifiableTable {
  // 使用Suppliers.memoize创建延迟加载的行类型原型提供者
  // methodref.receiver.bound.invalid警告可以忽略，这是类型检查器的误报
  // memoize确保supplyProto方法只被调用一次，结果被缓存
  @SuppressWarnings("methodref.receiver.bound.invalid")
  private final Supplier<RelProtoDataType> protoRowTypeSupplier =
      Suppliers.memoize(this::supplyProto);
  // JDBC Schema引用，包含数据源、方言、目录等信息
  public final JdbcSchema jdbcSchema;
  // JDBC目录名称，标识表所在的数据库目录
  public final String jdbcCatalogName;
  // JDBC模式名称，标识表所在的模式（schema）
  public final String jdbcSchemaName;
  // JDBC表名称，标识具体的表名
  public final String jdbcTableName;
  // JDBC表类型，标识表的类型（如表、视图、系统表等）
  public final Schema.TableType jdbcTableType;

  // JdbcTable构造方法：初始化JDBC表对象
  // 参数：
  //   jdbcSchema - JDBC Schema对象，包含数据源和方言信息
  //   jdbcCatalogName - 目录名称
  //   jdbcSchemaName - 模式名称
  //   jdbcTableName - 表名称
  //   jdbcTableType - 表类型
  JdbcTable(JdbcSchema jdbcSchema, String jdbcCatalogName,
      String jdbcSchemaName, String jdbcTableName,
      Schema.TableType jdbcTableType) {
    super(Object[].class); // 调用父类构造方法，指定每行数据类型为Object数组
    this.jdbcSchema = requireNonNull(jdbcSchema, "jdbcSchema"); // 设置jdbcSchema，非空检查
    this.jdbcCatalogName = jdbcCatalogName; // 设置目录名称，允许为null
    this.jdbcSchemaName = jdbcSchemaName; // 设置模式名称，允许为null
    this.jdbcTableName = requireNonNull(jdbcTableName, "jdbcTableName"); // 设置表名，非空检查
    this.jdbcTableType = requireNonNull(jdbcTableType, "jdbcTableType"); // 设置表类型，非空检查
  }

  // 重写toString方法，返回表的字符串表示
  @Override public String toString() {
    return "JdbcTable {" + jdbcTableName + "}"; // 返回包含表名的字符串
  }

  // 重写getJdbcTableType方法，返回JDBC表类型
  @Override public Schema.TableType getJdbcTableType() {
    return jdbcTableType; // 返回表类型（TABLE、VIEW等）
  }

  // 重写unwrap方法，将此对象解包为指定的类型
  // 参数：aClass - 要解包的目标类型
  // 返回：如果此对象可以转换为指定类型，则返回转换后的对象，否则返回null
  @Override public <C extends Object> @Nullable C unwrap(Class<C> aClass) {
    // 如果请求的是数据源类型，返回JDBC Schema的数据源
    if (aClass.isInstance(jdbcSchema.getDataSource())) {
      return aClass.cast(jdbcSchema.getDataSource());
    // 如果请求的是方言类型，返回JDBC Schema的方言
    } else if (aClass.isInstance(jdbcSchema.dialect)) {
      return aClass.cast(jdbcSchema.dialect);
    // 否则调用父类的unwrap方法
    } else {
      return super.unwrap(aClass);
    }
  }

  // 重写getRowType方法，获取表的行类型（即表的结构，包含所有列及其类型）
  // 参数：typeFactory - 关系数据类型工厂，用于创建数据类型
  // 返回：表的行类型，描述表中所有列的名称和类型
  @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    // 使用延迟加载的提供者获取行类型原型，然后应用到类型工厂上
    return protoRowTypeSupplier.get().apply(typeFactory);
  }

  // 私有方法：提供行类型原型
  // 从JDBC Schema中获取表的元数据，并转换为关系数据类型原型
  // 返回：关系数据类型原型，可以延迟创建实际的数据类型
  private RelProtoDataType supplyProto() {
    try {
      // 调用JdbcSchema的getRelDataType方法获取表的元数据
      return jdbcSchema.getRelDataType(
          jdbcCatalogName, // 传入目录名称
          jdbcSchemaName, // 传入模式名称
          jdbcTableName); // 传入表名称
    } catch (SQLException e) { // 捕获SQL异常
      // 如果获取元数据失败，抛出运行时异常
      throw new RuntimeException(
          "Exception while reading definition of table '" + jdbcTableName
              + "'", e);
    }
  }

  // 私有方法：获取字段类型信息列表
  // 将表的每个字段转换为（列表示类型，JDBC类型序号）的配对列表
  // 参数：typeFactory - Java类型工厂
  // 返回：字段类型配对列表，每个配对包含列表示类型和JDBC类型序号
  private List<Pair<ColumnMetaData.Rep, Integer>> fieldClasses(
      final JavaTypeFactory typeFactory) {
    // 获取表的行类型
    final RelDataType rowType = getRowType(typeFactory);
    // 使用Util.transform转换每个字段
    return Util.transform(rowType.getFieldList(), f -> {
      // 获取字段的类型
      final RelDataType type = f.getType();
      // 获取字段对应的Java类
      final Class clazz = (Class) typeFactory.getJavaClass(type);
      // 获取列表示类型（如PRIMITIVE_BOOLEAN、PRIMITIVE_INT等）
      // 如果找不到对应的表示类型，则使用OBJECT
      final ColumnMetaData.Rep rep =
          Util.first(ColumnMetaData.Rep.of(clazz),
              ColumnMetaData.Rep.OBJECT);
      // 返回配对：列表示类型和SQL类型的JDBC序号
      return Pair.of(rep, type.getSqlTypeName().getJdbcOrdinal());
    });
  }

  // 生成SQL查询字符串
  // 创建一个简单的SELECT * FROM table语句
  // 返回：SQL字符串对象，包含生成的SQL语句
  SqlString generateSql() {
    // 创建SELECT列表，使用通配符*表示选择所有列
    final SqlNodeList selectList = SqlNodeList.SINGLETON_STAR;
    // 创建SQL SELECT节点
    SqlSelect node =
        new SqlSelect(SqlParserPos.ZERO, // 解析位置设置为0
            SqlNodeList.EMPTY, // 关键字列表为空
            selectList, // SELECT列表为*
            tableName(), // FROM子句使用tableName()方法生成的表名
            null, // WHERE子句为空
            null, // GROUP BY子句为空
            null, // HAVING子句为空
            null, // 窗口定义列表为空
            null, // ORDER BY子句为空
            null, // OFFSET子句为空
            null, // FETCH子句为空
            null, // 锁子句为空
            null); // 提示子句为空
    // 创建SQL写入器配置
    final SqlWriterConfig config = SqlPrettyWriter.config()
        .withAlwaysUseParentheses(true) // 总是使用括号
        .withDialect(jdbcSchema.dialect); // 使用JDBC Schema的方言
    // 创建SQL美化写入器
    final SqlPrettyWriter writer = new SqlPrettyWriter(config);
    // 将SQL节点解析为SQL字符串
    node.unparse(writer, 0, 0);
    // 返回SQL字符串
    return writer.toSqlString();
  }

  /** Returns the table name, qualified with catalog and schema name if
   * applicable, as a parse tree node ({@link SqlIdentifier}).
   * 返回表名，如果适用则包含目录和模式名称，作为解析树节点（SqlIdentifier）返回 */
  public SqlIdentifier tableName() {
    // 创建名称列表，初始容量为3（catalog、schema、table）
    final List<String> names = new ArrayList<>(3);
    // 如果JDBC Schema有目录，添加到名称列表
    if (jdbcSchema.catalog != null) {
      names.add(jdbcSchema.catalog);
    }
    // 如果JDBC Schema有模式，添加到名称列表
    if (jdbcSchema.schema != null) {
      names.add(jdbcSchema.schema);
    }
    // 添加表名到名称列表
    names.add(jdbcTableName);
    // 创建并返回SQL标识符，包含完整的限定名称
    return new SqlIdentifier(names, SqlParserPos.ZERO);
  }

  // 重写toRel方法，将表转换为关系表达式
  // 这是Calcite优化器将逻辑表转换为物理扫描节点的关键方法
  // 参数：
  //   context - 转换上下文，提供集群和提示信息
  //   relOptTable - 关系优化表对象
  // 返回：JdbcTableScan关系节点，表示对JDBC表的扫描操作
  @Override public RelNode toRel(RelOptTable.ToRelContext context,
      RelOptTable relOptTable) {
    // 创建并返回JdbcTableScan节点
    // 使用优化集群、表提示、优化表、当前JdbcTable对象和JDBC约定
    return new JdbcTableScan(context.getCluster(), context.getTableHints(), relOptTable, this,
        jdbcSchema.convention);
  }

  // 重写asQueryable方法，将表转换为可查询对象
  // 这是LINQ查询的入口点，允许使用LINQ操作符查询表
  // 参数：
  //   queryProvider - 查询提供者，用于创建和执行查询
  //   schema - Schema Plus对象，提供额外的Schema功能
  //   tableName - 表名称
  // 返回：JdbcTableQueryable可查询对象，支持LINQ查询操作
  @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
      SchemaPlus schema, String tableName) {
    // 创建并返回JdbcTableQueryable对象
    return new JdbcTableQueryable<>(queryProvider, schema, tableName);
  }

  // 重写scan方法，扫描表并返回可枚举的数据集合
  // 这是执行表扫描的实际方法，会生成SQL并执行
  // 参数：root - 数据上下文，提供类型工厂等信息
  // 返回：可枚举的对象数组集合，每个对象数组表示一行数据
  @Override public Enumerable<@Nullable Object[]> scan(DataContext root) {
    // 从数据上下文中获取Java类型工厂
    JavaTypeFactory typeFactory = root.getTypeFactory();
    // 生成SQL查询语句
    final SqlString sql = generateSql();
    // 创建结果集可枚举对象
    // 使用JDBC Schema的数据源执行SQL
    // 使用JdbcUtils的行构建器工厂将结果集转换为对象数组
    return ResultSetEnumerable.of(jdbcSchema.getDataSource(), sql.getSql(),
        JdbcUtils.rowBuilderFactory2(fieldClasses(typeFactory)));
  }

  // 重写getModifiableCollection方法，获取可修改的集合
  // 返回：null，表示JDBC表不支持直接修改集合
  @Override public @Nullable Collection getModifiableCollection() {
    return null;
  }

  // 重写toModificationRel方法，将表修改操作转换为关系表达式
  // 这是Calcite处理INSERT、UPDATE、DELETE等操作的关键方法
  // 参数：
  //   cluster - 关系优化集群
  //   table - 要修改的表
  //   catalogReader - 目录读取器
  //   input - 输入关系节点（提供要插入或更新的数据）
  //   operation - 修改操作类型（INSERT、UPDATE、DELETE）
  //   updateColumnList - 更新的列列表（UPDATE操作时使用）
  //   sourceExpressionList - 源表达式列表（UPDATE操作时使用）
  //   flattened - 是否扁平化
  // 返回：LogicalTableModify关系节点，表示逻辑层面的表修改操作
  @Override public TableModify toModificationRel(RelOptCluster cluster,
      RelOptTable table, CatalogReader catalogReader, RelNode input,
      Operation operation, @Nullable List<String> updateColumnList,
      @Nullable List<RexNode> sourceExpressionList, boolean flattened) {
    // 注册JDBC约定到优化器的规划器
    jdbcSchema.convention.register(cluster.getPlanner());

    // 创建并返回LogicalTableModify节点
    // 使用NONE约定，表示这是逻辑层面的修改操作
    return new LogicalTableModify(cluster, cluster.traitSetOf(Convention.NONE),
        table, catalogReader, input, operation, updateColumnList,
        sourceExpressionList, flattened);
  }

  /** Enumerable that returns the contents of a {@link JdbcTable} by connecting
   * to the JDBC data source.
   * 可枚举对象，通过连接JDBC数据源返回JdbcTable的内容
   *
   * @param <T> element type 元素类型 */
  // JdbcTableQueryable内部类：提供对JDBC表的LINQ查询功能
  // 继承AbstractTableQueryable，实现可查询接口
  private class JdbcTableQueryable<T> extends AbstractTableQueryable<T> {
    // 构造方法：初始化JDBC表可查询对象
    // 参数：
    //   queryProvider - 查询提供者
    //   schema - Schema Plus对象
    //   tableName - 表名称
    JdbcTableQueryable(QueryProvider queryProvider, SchemaPlus schema,
        String tableName) {
      // 调用父类构造方法，传入查询提供者、Schema、JdbcTable对象和表名
      super(queryProvider, schema, JdbcTable.this, tableName);
    }

    // 重写toString方法，返回可查询对象的字符串表示
    @Override public String toString() {
      return "JdbcTableQueryable {table: " + tableName + "}"; // 返回包含表名的字符串
    }

    // 重写enumerator方法，创建枚举器用于遍历数据
    // 这是实际执行查询并返回数据的方法
    // 返回：枚举器对象，用于逐行遍历查询结果
    @Override public Enumerator<T> enumerator() {
      // 从查询提供者（CalciteConnection）中获取Java类型工厂
      final JavaTypeFactory typeFactory =
          ((CalciteConnection) queryProvider).getTypeFactory();
      // 生成SQL查询语句
      final SqlString sql = generateSql();
      // 获取字段类型配对列表
      final List<Pair<ColumnMetaData.Rep, Integer>> pairs =
          fieldClasses(typeFactory);
      // 创建结果集可枚举对象并获取枚举器
      // 使用JDBC Schema的数据源执行SQL
      // 使用JdbcUtils的行构建器工厂将结果集转换为对象
      @SuppressWarnings({"rawtypes", "unchecked"})
      final Enumerable<T> enumerable =
          (Enumerable) ResultSetEnumerable.of(jdbcSchema.getDataSource(),
              sql.getSql(), JdbcUtils.rowBuilderFactory2(pairs));
      // 返回枚举器
      return enumerable.enumerator();
    }
  }
}
