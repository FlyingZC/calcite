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
// Apache许可证声明,这是Apache Calcite项目的标准许可证头
package org.apache.calcite.adapter.jdbc; // 声明包名,该类位于org.apache.calcite.adapter.jdbc包下,是JDBC适配器包

import org.apache.calcite.avatica.AvaticaUtils; // 导入Avatica工具类,用于插件实例化等操作,Avatica是Calcite的底层JDBC框架
import org.apache.calcite.avatica.MetaImpl; // 导入Avatica元数据实现类,包含MetaTable等元数据结构定义
import org.apache.calcite.avatica.SqlType; // 导入SQL类型枚举,用于JDBC类型映射
import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解,标记不稳定的API
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类,用于代码生成和表达式树构建
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口,表示Calcite中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口,用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入关系数据类型实现类
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口,定义类型系统的行为
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型接口,用于延迟类型创建
import org.apache.calcite.schema.Schema; // 导入Schema接口,定义模式的基本行为
import org.apache.calcite.schema.SchemaFactory; // 导入Schema工厂接口,用于创建Schema实例
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口,扩展Schema接口,支持嵌套模式
import org.apache.calcite.schema.SchemaVersion; // 导入Schema版本接口,用于模式版本控制
import org.apache.calcite.schema.Schemas; // 导入Schemas工具类,提供模式相关的工具方法
import org.apache.calcite.schema.Table; // 导入Table接口,定义表的基本行为
import org.apache.calcite.schema.Wrapper; // 导入Wrapper接口,支持类型解包操作
import org.apache.calcite.schema.lookup.IgnoreCaseLookup; // 导入忽略大小写的查找实现
import org.apache.calcite.schema.lookup.LikePattern; // 导入Like模式类,用于模式匹配
import org.apache.calcite.schema.lookup.Lookup; // 导入查找接口,定义查找行为
import org.apache.calcite.sql.SqlDialect; // 导入SQL方言类,用于处理不同数据库的SQL语法差异
import org.apache.calcite.sql.SqlDialectFactory; // 导入SQL方言工厂接口,用于创建SQL方言实例
import org.apache.calcite.sql.SqlDialectFactoryImpl; // 导入SQL方言工厂实现类
import org.apache.calcite.sql.type.SqlTypeFactoryImpl; // 导入SQL类型工厂实现类
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举
import org.apache.calcite.util.LazyReference; // 导入延迟引用类,用于延迟加载
import org.apache.calcite.util.Pair; // 导入Pair类,用于存储键值对
import org.apache.calcite.util.Util; // 导入Util工具类,提供通用工具方法

import com.google.common.collect.ImmutableList; // 导入Guava不可变列表类
import com.google.common.collect.Ordering; // 导入Guava排序类,用于比较和排序

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解,用于标记可能为null的值
import org.slf4j.Logger; // 导入SLF4J日志接口
import org.slf4j.LoggerFactory; // 导入SLF4J日志工厂类

import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DatabaseMetaData; // 导入数据库元数据接口,用于获取数据库结构信息
import java.sql.ResultSet; // 导入结果集接口
import java.sql.SQLException; // 导入SQL异常类
import java.sql.Statement; // 导入语句接口
import java.util.List; // 导入列表接口
import java.util.Locale; // 导入区域设置类
import java.util.Map; // 导入映射接口
import java.util.Set; // 导入集合接口
import java.util.Spliterator; // 导入分割迭代器接口
import java.util.Spliterators; // 导入分割迭代器工具类
import java.util.function.BiFunction; // 导入双参数函数接口
import java.util.function.Consumer; // 导入消费者函数接口
import java.util.stream.Collectors; // 导入流收集器工具类
import java.util.stream.Stream; // 导入流接口
import java.util.stream.StreamSupport; // 导入流支持工具类
import javax.sql.DataSource; // 导入JDBC数据源接口

import static java.lang.Integer.parseInt; // 静态导入整数解析方法
import static java.util.Objects.requireNonNull; // 静态导入非空检查方法

/**
 * Implementation of {@link Schema} that is backed by a JDBC data source.
 * 这是Schema接口的实现类,由JDBC数据源支持
 * 
 * <p>The tables in the JDBC data source appear to be tables in this schema;
 * JDBC数据源中的表在这个模式中表现为Calcite的表
 * queries against this schema are executed against those tables, pushing down
 * as much as possible of the query logic to SQL.
 * 对该模式的查询会针对这些表执行,尽可能将查询逻辑下推到SQL
 * 
 * 这个类是Calcite连接外部JDBC数据库的核心类,它将JDBC数据库的表、字段等元数据
 * 映射为Calcite的Schema、Table、RelDataType等对象,使得Calcite可以查询JDBC数据源
 */
public class JdbcSchema extends JdbcBaseSchema implements Schema, Wrapper { // JdbcSchema类继承自JdbcBaseSchema基类,实现Schema和Wrapper接口
  private static final Logger LOGGER = LoggerFactory.getLogger(JdbcSchema.class); // 日志记录器,用于记录运行时信息和错误

  final DataSource dataSource; // JDBC数据源,用于获取数据库连接,是连接外部数据库的核心对象
  final @Nullable String catalog; // 数据库目录名称,可为null,用于限定查询的数据库范围(如MySQL的数据库)
  final @Nullable String schema; // 模式名称,可为null,用于限定查询的模式范围(如PostgreSQL的schema)
  public final SqlDialect dialect; // SQL方言对象,用于处理不同数据库的SQL语法差异,如MySQL、PostgreSQL、Oracle等
  final JdbcConvention convention; // JDBC调用约定,定义了JDBC适配器的规则和转换逻辑
  private final LazyReference<Lookup<Table>> tables = new LazyReference<>(); // 表查找对象的延迟引用,按需加载表信息,避免启动时全量加载
  private final Lookup<JdbcSchema> subSchemas = Lookup.empty(); // 子模式查找对象,JdbcSchema不支持子模式,因此返回空查找

  @Experimental // 实验性功能注解,表示此API可能不稳定
  public static final ThreadLocal<@Nullable Foo> THREAD_METADATA = new ThreadLocal<>(); // 线程本地变量,用于存储自定义的元数据提供函数,可用于测试或扩展

  private static final Ordering<Iterable<Integer>> VERSION_ORDERING = // 版本排序器,用于比较JDBC版本号
      Ordering.<Integer>natural().lexicographical(); // 使用自然排序和字典序比较版本号

  /**
   * Creates a JDBC schema.
   * 创建JDBC模式构造方法
   *
   * @param dataSource Data source - JDBC数据源,用于获取数据库连接
   * @param dialect SQL dialect - SQL方言对象,处理不同数据库的SQL语法
   * @param convention Calling convention - JDBC调用约定,定义转换规则
   * @param catalog Catalog name, or null - 数据库目录名称,可为null
   * @param schema Schema name pattern - 模式名称,可为null
   */
  public JdbcSchema(DataSource dataSource, SqlDialect dialect, // 构造方法,初始化JdbcSchema实例
      JdbcConvention convention, @Nullable String catalog, @Nullable String schema) {
    this.dataSource = requireNonNull(dataSource, "dataSource"); // 设置数据源,非空检查,确保数据源不为null
    this.dialect = requireNonNull(dialect, "dialect"); // 设置SQL方言,非空检查,确保方言不为null
    this.convention = convention; // 设置调用约定,存储JdbcConvention对象
    this.catalog = catalog; // 设置目录名称,可为null
    this.schema = schema; // 设置模式名称,可为null
  }

  public static JdbcSchema create( // 静态工厂方法,创建JdbcSchema实例(使用默认SQL方言工厂)
      SchemaPlus parentSchema, // 父模式对象,用于构建表达式
      String name, // 模式名称
      DataSource dataSource, // JDBC数据源
      @Nullable String catalog, // 数据库目录名称
      @Nullable String schema) { // 模式名称
    return create(parentSchema, name, dataSource, // 调用重载方法,使用默认的SQL方言工厂
        SqlDialectFactoryImpl.INSTANCE, catalog, schema); // SqlDialectFactoryImpl.INSTANCE是默认的方言工厂
  }

  public static JdbcSchema create( // 静态工厂方法,创建JdbcSchema实例(使用自定义SQL方言工厂)
      SchemaPlus parentSchema, // 父模式对象,用于构建表达式
      String name, // 模式名称
      DataSource dataSource, // JDBC数据源
      SqlDialectFactory dialectFactory, // SQL方言工厂,用于创建特定数据库的方言
      @Nullable String catalog, // 数据库目录名称
      @Nullable String schema) { // 模式名称
    final Expression expression = // 创建子模式表达式,用于代码生成
        Schemas.subSchemaExpression(parentSchema, name, JdbcSchema.class); // 构建JdbcSchema的表达式树
    final SqlDialect dialect = createDialect(dialectFactory, dataSource); // 根据数据源创建SQL方言对象
    final JdbcConvention convention = // 创建JDBC调用约定对象
        JdbcConvention.of(dialect, expression, name); // JdbcConvention.of是静态工厂方法,创建约定实例
    return new JdbcSchema(dataSource, dialect, convention, catalog, schema); // 返回新创建的JdbcSchema实例
  }

  /**
   * Creates a JdbcSchema, taking credentials from a map.
   * 从映射中读取配置创建JdbcSchema,通常用于从model.json文件加载配置
   *
   * @param parentSchema Parent schema - 父模式对象
   * @param name Name - 模式名称
   * @param operand Map of property/value pairs - 属性值映射,包含JDBC连接配置
   * @return A JdbcSchema - 返回创建的JdbcSchema实例
   */
  public static JdbcSchema create( // 静态工厂方法,从配置映射创建JdbcSchema
      SchemaPlus parentSchema, // 父模式对象
      String name, // 模式名称
      Map<String, Object> operand) { // 配置映射,包含dataSource或jdbcUrl等配置
    DataSource dataSource; // 声明数据源变量
    try { // 开始异常处理
      final String dataSourceName = (String) operand.get("dataSource"); // 尝试获取dataSource配置项
      if (dataSourceName != null) { // 如果配置了dataSource
        dataSource = // 使用插件机制实例化数据源
            AvaticaUtils.instantiatePlugin(DataSource.class, dataSourceName); // 通过反射创建DataSource实例
      } else { // 如果没有配置dataSource,则使用JDBC URL方式
        final String jdbcUrl = (String) requireNonNull(operand.get("jdbcUrl"), "jdbcUrl"); // 获取JDBC URL,非空检查
        final String jdbcDriver = (String) operand.get("jdbcDriver"); // 获取JDBC驱动类名
        final String jdbcUser = (String) operand.get("jdbcUser"); // 获取JDBC用户名
        final String jdbcPassword = (String) operand.get("jdbcPassword"); // 获取JDBC密码
        dataSource = dataSource(jdbcUrl, jdbcDriver, jdbcUser, jdbcPassword); // 调用dataSource方法创建数据源
      }
    } catch (Exception e) { // 捕获异常
      throw new RuntimeException("Error while reading dataSource", e); // 抛出运行时异常,包装原始异常
    }
    String jdbcCatalog = (String) operand.get("jdbcCatalog"); // 获取JDBC目录配置
    String jdbcSchema = (String) operand.get("jdbcSchema"); // 获取JDBC模式配置
    String sqlDialectFactory = (String) operand.get("sqlDialectFactory"); // 获取SQL方言工厂配置

    if (sqlDialectFactory == null || sqlDialectFactory.isEmpty()) { // 如果没有配置方言工厂
      return JdbcSchema.create( // 使用默认方言工厂创建JdbcSchema
          parentSchema, name, dataSource, jdbcCatalog, jdbcSchema); // 传入父模式、名称、数据源、目录和模式
    } else { // 如果配置了自定义方言工厂
      SqlDialectFactory factory = // 实例化自定义方言工厂
          AvaticaUtils.instantiatePlugin(SqlDialectFactory.class, // 通过反射创建SqlDialectFactory实例
              sqlDialectFactory); // 使用配置的工厂类名
      return JdbcSchema.create(parentSchema, name, dataSource, factory, // 使用自定义方言工厂创建JdbcSchema
          jdbcCatalog, jdbcSchema); // 传入目录和模式
    }
  }

  /**
   * Returns a suitable SQL dialect for the given data source.
   * 为给定的数据源返回合适的SQL方言
   *
   * @param dataSource The data source - JDBC数据源
   *
   * @deprecated Use {@link #createDialect(SqlDialectFactory, DataSource)} instead
   * 已废弃,请使用带方言工厂参数的重载方法
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在2.0版本前移除
  public static SqlDialect createDialect(DataSource dataSource) { // 创建SQL方言的静态方法(已废弃)
    return createDialect(SqlDialectFactoryImpl.INSTANCE, dataSource); // 调用新方法,使用默认方言工厂
  }

  /** Returns a suitable SQL dialect for the given data source.
   * 为给定的数据源返回合适的SQL方言(使用方言工厂)
   */
  public static SqlDialect createDialect(SqlDialectFactory dialectFactory, // 创建SQL方言的静态方法
      DataSource dataSource) { // JDBC数据源
    return JdbcUtils.DialectPool.INSTANCE.get(dialectFactory, dataSource); // 从方言池中获取方言实例,使用缓存提高性能
  }

  /** Creates a JDBC data source with the given specification.
   * 根据给定的规范创建JDBC数据源
   */
  public static DataSource dataSource(String url, @Nullable String driverClassName, // 创建JDBC数据源的静态方法
      @Nullable String username, @Nullable String password) { // JDBC URL、驱动类名、用户名、密码
    if (url.startsWith("jdbc:hsqldb:")) { // 如果是HSQLDB数据库
      // Prevent hsqldb from screwing up java.util.logging.
      // 防止HSQLDB破坏java.util.logging配置
      System.setProperty("hsqldb.reconfig_logging", "false"); // 设置系统属性,禁用HSQLDB的日志重配置
    }
    return JdbcUtils.DataSourcePool.INSTANCE.get(url, driverClassName, username, // 从数据源池中获取数据源实例,使用缓存
        password); // 返回缓存的DataSource实例,避免重复创建
  }

  @Override public Lookup<Table> tables() { // 实现Schema接口的tables方法,返回表查找对象
    return tables.getOrCompute(() -> new IgnoreCaseLookup<Table>() { // 使用延迟引用,按需创建表查找对象(忽略大小写)
      @Override public @Nullable Table get(String name) { // 实现get方法,根据表名获取表对象
        try (Stream<MetaImpl.MetaTable> s = getMetaTableStream(name)) { // 使用try-with-resources获取表元数据流
          return s.findFirst().map(it -> jdbcTableMapper(it)).orElse(null); // 找到第一个匹配的表,映射为JdbcTable对象,或返回null
        } // 自动关闭流
      }

      @Override public Set<String> getNames(LikePattern pattern) { // 实现getNames方法,根据模式获取表名集合
        try (Stream<MetaImpl.MetaTable> s = getMetaTableStream(pattern.pattern)) { // 使用try-with-resources获取表元数据流
          return s.map(it -> it.tableName).collect(Collectors.toSet()); // 将表名映射为字符串,收集到Set中
        } // 自动关闭流
      }
    }); // 返回延迟计算的表查找对象
  }

  @Override public Lookup<? extends Schema> subSchemas() { // 实现Schema接口的subSchemas方法,返回子模式查找对象
    return subSchemas; // 返回空的子模式查找对象,JdbcSchema不支持子模式
  }


  @Override public boolean isMutable() { // 实现Schema接口的isMutable方法,判断模式是否可变
    return false; // 返回false,JdbcSchema是不可变的,因为底层数据库结构可能随时变化
  }

  @Override public Schema snapshot(SchemaVersion version) { // 实现Schema接口的snapshot方法,创建模式快照
    return this; // 返回this,因为JdbcSchema本身就是不可变的,不需要创建快照
  }

  // Used by generated code.
// 供生成的代码使用,用于访问数据源
public DataSource getDataSource() { // 获取数据源的公共方法
    return dataSource; // 返回JDBC数据源对象
  }

  @Override public Expression getExpression(@Nullable SchemaPlus parentSchema, String name) { // 实现Schema接口的getExpression方法,获取表达式
    requireNonNull(parentSchema, "parentSchema must not be null for JdbcSchema"); // 非空检查,父模式不能为null
    return Schemas.subSchemaExpression(parentSchema, name, JdbcSchema.class); // 构建子模式表达式,用于代码生成
  }

  private Stream<MetaImpl.MetaTable> getMetaTableStream(String tableNamePattern) { // 私有方法,获取表元数据流
    final Pair<@Nullable String, @Nullable String> catalogSchema = getCatalogSchema(); // 获取目录和模式对
    final Stream<MetaImpl.MetaTable> tableDefs; // 声明表定义流
    Connection connection = null; // 声明连接对象
    ResultSet resultSet = null; // 声明结果集对象
    try { // 开始异常处理
      connection = dataSource.getConnection(); // 从数据源获取数据库连接
      final DatabaseMetaData metaData = connection.getMetaData(); // 获取数据库元数据
      resultSet = // 查询表信息
          metaData.getTables(catalogSchema.left, catalogSchema.right, tableNamePattern, null); // 获取匹配的表列表
      tableDefs = asStream(connection, resultSet) // 将结果集转换为流
          .map(JdbcSchema::metaDataMapper); // 将ResultSet映射为MetaTable对象
    } catch (SQLException e) { // 捕获SQL异常
      close(connection, null, resultSet); // 关闭连接和结果集
      throw new RuntimeException( // 抛出运行时异常
          "Exception while reading tables", e); // 包装异常信息
    }
    return tableDefs; // 返回表元数据流
  }

  private static Stream<ResultSet> asStream(Connection connection, ResultSet resultSet) { // 私有静态方法,将ResultSet转换为Stream
    return StreamSupport.stream( // 使用StreamSupport创建流
        new Spliterators.AbstractSpliterator<ResultSet>( // 创建抽象分割迭代器
            Long.MAX_VALUE, Spliterator.ORDERED) { // 无限大小,有序
          @Override public boolean tryAdvance(Consumer<? super ResultSet> action) { // 实现tryAdvance方法,尝试前进到下一个元素
            try { // 开始异常处理
              if (!resultSet.next()) { // 尝试移动到下一行
                return false; // 如果没有更多行,返回false
              }
              action.accept(resultSet); // 将当前ResultSet传递给消费者
              return true; // 返回true,表示成功处理
            } catch (SQLException ex) { // 捕获SQL异常
              throw new RuntimeException(ex); // 抛出运行时异常
            }
          }
        }, false).onClose(() -> close(connection, null, resultSet)); // false表示非并行,设置关闭回调,关闭连接和结果集
  }

  private JdbcTable jdbcTableMapper(MetaImpl.MetaTable tableDef) { // 私有方法,将MetaTable映射为JdbcTable
    return new JdbcTable(this, tableDef.tableCat, tableDef.tableSchem, tableDef.tableName, // 创建JdbcTable实例,传入schema、目录、模式、表名
        getTableType(tableDef.tableType)); // 获取并传入表类型
  }

  private static MetaImpl.MetaTable metaDataMapper(ResultSet resultSet) { // 私有静态方法,将ResultSet映射为MetaTable
    try { // 开始异常处理
      return new MetaImpl.MetaTable(resultSet.getString(1), resultSet.getString(2), // 创建MetaTable对象,从ResultSet读取目录、模式、表名、表类型
          resultSet.getString(3), // 第3列是表名
          resultSet.getString(4)); // 第4列是表类型
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
  }

  private static TableType getTableType(String tableTypeName) { // 私有静态方法,将表类型字符串映射为TableType枚举
    // Clean up table type. In particular, this ensures that 'SYSTEM TABLE',
    // 清理表类型名称。特别是确保'SYSTEM TABLE'(Phoenix等数据库返回的)映射为TableType.SYSTEM_TABLE
    // returned by Phoenix among others, maps to TableType.SYSTEM_TABLE.
    // 我们知道枚举常量是大写且无空格的,所以这样做不会让情况变得更糟
    // We know enum constants are upper-case without spaces, so we can't
    // make things worse.
    //
    // PostgreSQL returns tableTypeName==null for pg_toast* tables
    // PostgreSQL对pg_toast*表返回tableTypeName==null
    // This can happen if you start JdbcSchema off a "public" PG schema
    // 如果从"public" PG模式启动JdbcSchema,可能会发生这种情况
    // The tables are not designed to be queried by users, however we do
    // 这些表不是设计给用户查询的,但我们不过滤它们,因为保留所有其他表类型
    // not filter them as we keep all the other table types.
    final String tableTypeName2 = // 清理后的表类型名称
        tableTypeName == null // 如果表类型名称为null
            ? null // 保持为null
            : tableTypeName.toUpperCase(Locale.ROOT).replace(' ', '_'); // 否则转为大写,空格替换为下划线
    final TableType tableType = // 将字符串映射为TableType枚举
        Util.enumVal(TableType.OTHER, tableTypeName2); // 如果映射失败,返回TableType.OTHER
    if (tableType == TableType.OTHER && tableTypeName2 != null) { // 如果是未知类型且名称不为null
      LOGGER.info("Unknown table type: {}", tableTypeName2); // 记录日志,提示未知表类型
    }
    return tableType; // 返回表类型枚举
  }

  /** Returns [major, minor] version from a database metadata.
   * 从数据库元数据返回[主版本, 次版本]版本号
   */
  private static List<Integer> version(DatabaseMetaData metaData) throws SQLException { // 私有静态方法,获取JDBC版本号
    return ImmutableList.of(metaData.getJDBCMajorVersion(), // 获取JDBC主版本号
        metaData.getJDBCMinorVersion()); // 获取JDBC次版本号
  }

  /** Returns a pair of (catalog, schema) for the current connection.
   * 返回当前连接的(目录, 模式)对
   */
  private Pair<@Nullable String, @Nullable String> getCatalogSchema() { // 私有方法,获取目录和模式
    try (Connection connection = dataSource.getConnection()) { // 使用try-with-resources获取连接
      final DatabaseMetaData metaData = connection.getMetaData(); // 获取数据库元数据
      final List<Integer> version41 = ImmutableList.of(4, 1); // JDBC 4.1版本号
      String catalog = this.catalog; // 初始化目录为成员变量值
      String schema = this.schema; // 初始化模式为成员变量值
      final boolean jdbc41OrAbove = // 判断是否为JDBC 4.1或更高版本
          VERSION_ORDERING.compare(version(metaData), version41) >= 0; // 比较版本号
      if (catalog == null && jdbc41OrAbove) { // 如果目录为null且支持JDBC 4.1
        // From JDBC 4.1, catalog and schema can be retrieved from the connection
        // 从JDBC 4.1开始,可以从连接对象获取目录和模式
        // object, hence try to get it from there if it was not specified by user
        // 因此如果用户没有指定,尝试从连接对象获取
        catalog = connection.getCatalog(); // 从连接获取当前目录
      }
      if (schema == null && jdbc41OrAbove) { // 如果模式为null且支持JDBC 4.1
        schema = connection.getSchema(); // 从连接获取当前模式
        if ("".equals(schema)) { // PostgreSQL有时返回空字符串
          schema = null; // PostgreSQL returns useless "" sometimes // 将空字符串转为null
        }
      }
      if ((catalog == null || schema == null) // 如果目录或模式仍为null
          && metaData.getDatabaseProductName().equals("PostgreSQL")) { // 且是PostgreSQL数据库
        final String sql = "select current_database(), current_schema()"; // 使用SQL查询获取当前数据库和模式
        try (Statement statement = connection.createStatement(); // 创建语句对象
            ResultSet resultSet = statement.executeQuery(sql)) { // 执行查询
          if (resultSet.next()) { // 如果有结果
            catalog = resultSet.getString(1); // 获取当前数据库(目录)
            schema = resultSet.getString(2); // 获取当前模式
          }
        } // 自动关闭语句和结果集
      }
      return Pair.of(catalog, schema); // 返回目录和模式对
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
  }

  RelProtoDataType getRelDataType(String catalogName, String schemaName, // 获取关系数据类型的原型,用于延迟创建类型
      String tableName) throws SQLException { // 抛出SQL异常
    Connection connection = null; // 声明连接对象
    try { // 开始异常处理
      connection = dataSource.getConnection(); // 获取数据库连接
      DatabaseMetaData metaData = connection.getMetaData(); // 获取数据库元数据
      return getRelDataType(metaData, catalogName, schemaName, tableName); // 调用重载方法获取关系数据类型
    } finally { // finally块确保资源释放
      close(connection, null, null); // 关闭连接
    }
  }

  RelProtoDataType getRelDataType(DatabaseMetaData metaData, String catalogName, // 获取关系数据类型的原型(使用数据库元数据)
      String schemaName, String tableName) throws SQLException { // 抛出SQL异常
    final ResultSet resultSet = // 查询表的列信息
        metaData.getColumns(catalogName, schemaName, tableName, null); // 获取指定表的所有列

    // Temporary type factory, just for the duration of this method. Allowable
    // 临时类型工厂,仅在此方法期间使用。这是允许的
    // because we're creating a proto-type, not a type; before being used, the
    // 因为我们创建的是原型类型,而不是实际类型;在使用之前
    // proto-type will be copied into a real type factory.
    // 原型类型将被复制到实际的类型工厂中
    final RelDataTypeFactory typeFactory = // 创建临时类型工厂
        new SqlTypeFactoryImpl(RelDataTypeSystem.DEFAULT); // 使用默认类型系统
    final RelDataTypeFactory.Builder fieldInfo = typeFactory.builder(); // 创建类型构建器
    while (resultSet.next()) { // 遍历每一列
      final String columnName = requireNonNull(resultSet.getString(4), "columnName"); // 获取列名(第4列),非空检查
      final int dataType = resultSet.getInt(5); // 获取数据类型(第5列)
      final String typeString = resultSet.getString(6); // 获取类型名称(第6列)
      final int precision; // 声明精度变量
      final int scale; // 声明标度变量
      switch (SqlType.valueOf(dataType)) { // 根据数据类型处理精度和标度
      case TIMESTAMP: // 如果是时间戳类型
      case TIME: // 如果是时间类型
        precision = resultSet.getInt(9); // SCALE // 使用第9列作为精度
        scale = 0; // 标度设为0
        break; // 跳出switch
      default: // 其他类型
        precision = resultSet.getInt(7); // SIZE // 使用第7列作为精度
        scale = resultSet.getInt(9); // SCALE // 使用第9列作为标度
        break; // 跳出switch
      }
      RelDataType sqlType = // 创建SQL类型
          sqlType(typeFactory, dataType, precision, scale, typeString); // 调用sqlType方法创建类型
      boolean nullable = resultSet.getInt(11) != DatabaseMetaData.columnNoNulls; // 判断是否可为空(第11列)
      fieldInfo.add(columnName, sqlType).nullable(nullable); // 添加字段信息,设置列名、类型和可空性
    }
    resultSet.close(); // 关闭结果集
    return RelDataTypeImpl.proto(fieldInfo.build()); // 返回原型数据类型
  }

  private static RelDataType sqlType(RelDataTypeFactory typeFactory, int dataType, // 私有静态方法,根据JDBC类型创建RelDataType
      int precision, int scale, @Nullable String typeString) { // 精度、标度和类型字符串
    // Fall back to ANY if type is unknown
    // 如果类型未知,回退到ANY类型
    final SqlTypeName sqlTypeName = // 将JDBC类型映射为SqlTypeName
        Util.first(SqlTypeName.getNameForJdbcType(dataType), SqlTypeName.ANY); // 如果映射失败,使用ANY
    switch (sqlTypeName) { // 根据SQL类型名称处理
    case ARRAY: // 如果是数组类型
      RelDataType component = null; // 声明元素类型
      if (typeString != null && typeString.endsWith(" ARRAY")) { // 如果类型字符串以" ARRAY"结尾
        // E.g. hsqldb gives "INTEGER ARRAY", so we deduce the component type
        // 例如,hsqldb返回"INTEGER ARRAY",所以我们推断元素类型为"INTEGER"
        // "INTEGER".
        final String remaining = // 提取元素类型字符串
            typeString.substring(0, typeString.length() - " ARRAY".length()); // 去掉" ARRAY"后缀
        component = parseTypeString(typeFactory, remaining); // 解析元素类型
      }
      if (component == null) { // 如果元素类型仍为null
        component = // 创建可空的ANY类型作为元素类型
            typeFactory.createTypeWithNullability( // 创建可空类型
                typeFactory.createSqlType(SqlTypeName.ANY), true); // ANY类型,可空
      }
      return typeFactory.createArrayType(component, -1); // 返回数组类型,-1表示未知维度
    default: // 其他类型
      break; // 跳出switch
    }
    if (precision >= 0 // 如果精度>=0
        && scale >= 0 // 且标度>=0
        && sqlTypeName.allowsPrecScale(true, true)) { // 且类型支持精度和标度
      return typeFactory.createSqlType(sqlTypeName, precision, scale); // 创建带精度和标度的类型
    } else if (precision >= 0 && sqlTypeName.allowsPrecNoScale()) { // 如果精度>=0且类型只支持精度
      return typeFactory.createSqlType(sqlTypeName, precision); // 创建带精度的类型
    } else { // 其他情况
      assert sqlTypeName.allowsNoPrecNoScale(); // 断言类型不支持精度和标度
      return typeFactory.createSqlType(sqlTypeName); // 创建不带精度和标度的类型
    }
  }

  /** Given "INTEGER", returns BasicSqlType(INTEGER).
   * 给定"INTEGER",返回BasicSqlType(INTEGER)
   * Given "VARCHAR(10)", returns BasicSqlType(VARCHAR, 10).
   * 给定"VARCHAR(10)",返回BasicSqlType(VARCHAR, 10)
   * Given "NUMERIC(10, 2)", returns BasicSqlType(NUMERIC, 10, 2).
   * 给定"NUMERIC(10, 2)",返回BasicSqlType(NUMERIC, 10, 2)
   */
  private static RelDataType parseTypeString(RelDataTypeFactory typeFactory, // 私有静态方法,解析类型字符串
      String typeString) { // 类型字符串,如"VARCHAR(10)"或"NUMERIC(10, 2)"
    int precision = -1; // 初始化精度为-1(未知)
    int scale = -1; // 初始化标度为-1(未知)
    int open = typeString.indexOf("("); // 查找左括号位置
    if (open >= 0) { // 如果找到左括号
      int close = typeString.indexOf(")", open); // 查找右括号位置
      if (close >= 0) { // 如果找到右括号
        String rest = typeString.substring(open + 1, close); // 提取括号内的内容
        typeString = typeString.substring(0, open); // 提取类型名称
        int comma = rest.indexOf(","); // 查找逗号位置
        if (comma >= 0) { // 如果找到逗号(有精度和标度)
          precision = parseInt(rest.substring(0, comma)); // 解析精度
          scale = parseInt(rest.substring(comma)); // 解析标度
        } else { // 如果没有逗号(只有精度)
          precision = parseInt(rest); // 解析精度
        }
      }
    }
    try { // 开始异常处理
      final SqlTypeName typeName = SqlTypeName.valueOf(typeString); // 将类型字符串转换为SqlTypeName枚举
      return typeName.allowsPrecScale(true, true) // 如果类型支持精度和标度
          ? typeFactory.createSqlType(typeName, precision, scale) // 创建带精度和标度的类型
          : typeName.allowsPrecScale(true, false) // 如果类型只支持精度
          ? typeFactory.createSqlType(typeName, precision) // 创建带精度的类型
          : typeFactory.createSqlType(typeName); // 创建不带精度和标度的类型
    } catch (IllegalArgumentException e) { // 捕获非法参数异常
      return typeFactory.createTypeWithNullability( // 返回可空的ANY类型
          typeFactory.createSqlType(SqlTypeName.ANY), true); // ANY类型,可空
    }
  }

  @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) { // 实现Wrapper接口的unwrap方法,解包为指定类型
    if (clazz.isInstance(this)) { // 如果请求的类型是JdbcSchema或其父类
      return clazz.cast(this); // 返回this
    }
    if (clazz == DataSource.class) { // 如果请求的是DataSource类型
      return clazz.cast(getDataSource()); // 返回数据源对象
    }
    return null; // 返回null,表示不支持解包
  }


  private static void close( // 私有静态方法,关闭JDBC资源
      @Nullable Connection connection, // 数据库连接,可为null
      @Nullable Statement statement, // 语句对象,可为null
      @Nullable ResultSet resultSet) { // 结果集,可为null
    if (resultSet != null) { // 如果结果集不为null
      try { // 开始异常处理
        resultSet.close(); // 关闭结果集
      } catch (SQLException e) { // 捕获SQL异常
        // ignore // 忽略异常
      }
    }
    if (statement != null) { // 如果语句对象不为null
      try { // 开始异常处理
        statement.close(); // 关闭语句对象
      } catch (SQLException e) { // 捕获SQL异常
        // ignore // 忽略异常
      }
    }
    if (connection != null) { // 如果连接不为null
      try { // 开始异常处理
        connection.close(); // 关闭连接
      } catch (SQLException e) { // 捕获SQL异常
        // ignore // 忽略异常
      }
    }
  }

  /** Schema factory that creates a
   * Schema工厂,用于创建JdbcSchema实例
   * {@link org.apache.calcite.adapter.jdbc.JdbcSchema}.
   *
   * <p>This allows you to create a jdbc schema inside a model.json file, like
   * 这允许你在model.json文件中创建JDBC模式,例如:
   * this:
   *
   * <blockquote><pre>
   * {
   *   "version": "1.0",
   *   "defaultSchema": "FOODMART_CLONE",
   *   "schemas": [
   *     {
   *       "name": "FOODMART_CLONE",
   *       "type": "custom",
   *       "factory": "org.apache.calcite.adapter.jdbc.JdbcSchema$Factory",
   *       "operand": {
   *         "jdbcDriver": "com.mysql.jdbc.Driver",
   *         "jdbcUrl": "jdbc:mysql://localhost/foodmart",
   *         "jdbcUser": "foodmart",
   *         "jdbcPassword": "foodmart"
   *       }
   *     }
   *   ]
   * }</pre></blockquote>
   */
  public static class Factory implements SchemaFactory { // 公共静态内部类,实现SchemaFactory接口
    public static final Factory INSTANCE = new Factory(); // 单例实例,用于工厂模式

    private Factory() {} // 私有构造方法,确保单例

    @Override public Schema create( // 实现SchemaFactory接口的create方法
        SchemaPlus parentSchema, // 父模式对象
        String name, // 模式名称
        Map<String, Object> operand) { // 配置映射
      return JdbcSchema.create(parentSchema, name, operand); // 调用JdbcSchema的静态create方法创建实例
    }
  }

  /** Do not use.
   * 不要使用此接口
   */
  @Experimental // 实验性功能注解
  public interface Foo // 公共接口,用于自定义元数据提供
      extends BiFunction<@Nullable String, @Nullable String, Iterable<MetaImpl.MetaTable>> { // 继承双参数函数接口,接收目录和模式,返回表元数据集合
  }
} // 类定义结束
