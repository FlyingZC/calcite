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
package org.apache.calcite.jdbc;

import org.apache.calcite.DataContext; // 导入数据上下文类，用于在查询执行过程中传递数据
import org.apache.calcite.adapter.java.AbstractQueryableTable; // 导入抽象可查询表类
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂
import org.apache.calcite.avatica.AvaticaStatement; // 导入Avatica语句类，Avatica是Calcite的子项目
import org.apache.calcite.avatica.AvaticaUtils; // 导入Avatica工具类
import org.apache.calcite.avatica.ColumnMetaData; // 导入列元数据类
import org.apache.calcite.avatica.Meta; // 导入元数据接口
import org.apache.calcite.avatica.MetaImpl; // 导入元数据实现基类
import org.apache.calcite.avatica.NoSuchStatementException; // 导入无语句异常类
import org.apache.calcite.avatica.QueryState; // 导入查询状态类
import org.apache.calcite.avatica.remote.TypedValue; // 导入远程类型值类
import org.apache.calcite.jdbc.CalcitePrepare.Context; // 导入Calcite准备上下文
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，LINQ风格的集合操作
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历集合
import org.apache.calcite.linq4j.Linq4j; // 导入LINQ4j工具类
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口
import org.apache.calcite.linq4j.function.Function1; // 导入单参数函数接口
import org.apache.calcite.linq4j.function.Functions; // 导入函数工具类
import org.apache.calcite.linq4j.function.Predicate1; // 导入单参数谓词接口
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂
import org.apache.calcite.rel.type.RelDataTypeFactoryImpl; // 导入关系数据类型工厂实现
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统
import org.apache.calcite.runtime.FlatLists; // 导入扁平列表工具类
import org.apache.calcite.runtime.Hook; // 导入钩子类，用于扩展点
import org.apache.calcite.schema.Schema; // 导入Schema接口
import org.apache.calcite.schema.SchemaPlus; // 导入增强Schema接口
import org.apache.calcite.schema.Table; // 导入表接口
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类
import org.apache.calcite.schema.impl.MaterializedViewTable; // 导入物化视图表类
import org.apache.calcite.schema.lookup.LikePattern; // 导入LIKE模式匹配类
import org.apache.calcite.server.CalciteServerStatement; // 导入Calcite服务器语句类
import org.apache.calcite.sql.SqlJdbcFunctionCall; // 导入SQL JDBC函数调用类
import org.apache.calcite.sql.SqlKind; // 导入SQL类型枚举
import org.apache.calcite.sql.SqlOperator; // 导入SQL操作符接口
import org.apache.calcite.sql.SqlOperatorTable; // 导入SQL操作符表接口
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举
import org.apache.calcite.tools.FrameworkConfig; // 导入框架配置类
import org.apache.calcite.tools.Frameworks; // 导入框架工具类
import org.apache.calcite.util.Holder; // 导入持有者类，用于传递可变对象
import org.apache.calcite.util.Pair; // 导入键值对类
import org.apache.calcite.util.Util; // 导入通用工具类

import com.google.common.annotations.VisibleForTesting; // 导入Google可见性注解
import com.google.common.collect.ImmutableList; // 导入不可变列表类
import com.google.common.collect.ImmutableMap; // 导入不可变映射类
import com.google.common.primitives.Ints; // 导入整数工具类
import com.google.common.primitives.Longs; // 导入长整型工具类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解

import java.lang.reflect.Field; // 导入反射字段类
import java.sql.Connection; // 导入JDBC连接接口
import java.sql.DatabaseMetaData; // 导入数据库元数据接口
import java.sql.SQLException; // 导入SQL异常类
import java.util.ArrayList; // 导入数组列表类
import java.util.Collections; // 导入集合工具类
import java.util.Iterator; // 导入迭代器接口
import java.util.List; // 导入列表接口
import java.util.Map; // 导入映射接口
import java.util.Optional; // 导入可选类

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法

/**
 * Helper for implementing the {@code getXxx} methods such as
 * {@link org.apache.calcite.avatica.AvaticaDatabaseMetaData#getTables}.
 * 这是实现getXxx方法(如getTables)的辅助类，用于提供数据库元数据信息
 * CalciteMetaImpl是Calcite框架中实现JDBC DatabaseMetaData接口的核心类
 * 它提供了获取数据库目录、Schema、表、列、函数等元数据的功能
 * 继承自Avatica的MetaImpl类，是Calcite连接的元数据提供者
 */
public class CalciteMetaImpl extends MetaImpl { // 定义CalciteMetaImpl类，继承自MetaImpl，提供Calcite特有的元数据实现
  static final Driver DRIVER = new Driver(); // 静态Driver实例，用于创建Calcite连接，是整个驱动程序的入口点

  private final CalciteMetaTableFactory metaTableFactory; // 元数据表工厂，用于创建MetaTable实例，负责将Calcite的Table对象转换为元数据格式
  private final CalciteMetaColumnFactory metaColumnFactory; // 元数据列工厂，用于创建MetaColumn实例，负责将Calcite的列信息转换为元数据格式

  /** The columns returned by {@link DatabaseMetaData#getCatalogs()}. */
  /** DatabaseMetaData.getCatalogs()方法返回的列名列表，定义了目录元数据的列结构 */
  public static final List<String> CATALOG_COLUMNS = // 定义返回的列名常量
      ImmutableList.of("TABLE_CAT"); // 包含一个列：TABLE_CAT（表目录名）

  /** Column names returned by {@link DatabaseMetaData#getColumns}. */
  /** DatabaseMetaData.getColumns()方法返回的列名列表，定义了列元数据的完整结构 */
  public static final List<String> COLUMN_COLUMNS = // 定义返回的列名常量
      ImmutableList.of("TABLE_CAT", // 表目录名
          "TABLE_SCHEM", // 表Schema名
          "TABLE_NAME", // 表名
          "COLUMN_NAME", // 列名
          "DATA_TYPE", // 数据类型（JDBC类型代码）
          "TYPE_NAME", // 类型名称（数据库特定类型名）
          "COLUMN_SIZE", // 列大小（字符长度或数值精度）
          "BUFFER_LENGTH", // 缓冲区长度（未使用）
          "DECIMAL_DIGITS", // 小数位数
          "NUM_PREC_RADIX", // 数值精度基数（通常为10或2）
          "NULLABLE", // 是否可为空
          "REMARKS", // 注释说明
          "COLUMN_DEF", // 列默认值
          "SQL_DATA_TYPE", // SQL数据类型（未使用）
          "SQL_DATETIME_SUB", // SQL日期时间子类型（未使用）
          "CHAR_OCTET_LENGTH", // 字符字节长度
          "ORDINAL_POSITION", // 列在表中的位置（从1开始）
          "IS_NULLABLE", // 是否可为空（字符串"YES"/"NO"）
          "SCOPE_CATALOG", // 作用域目录（未使用）
          "SCOPE_SCHEMA", // 作用域Schema（未使用）
          "SCOPE_TABLE", // 作用域表（未使用）
          "SOURCE_DATA_TYPE", // 源数据类型（用于DISTINCT类型）
          "IS_AUTOINCREMENT", // 是否自增
          "IS_GENERATEDCOLUMN"); // 是否生成列

  /** The columns returned by {@link DatabaseMetaData#getFunctions}. */
  /** DatabaseMetaData.getFunctions()方法返回的列名列表，定义了函数元数据的结构 */
  public static final List<String> FUNCTION_COLUMNS = // 定义返回的列名常量
      ImmutableList.of("FUNCTION_CAT", // 函数目录名
          "FUNCTION_SCHEM", // 函数Schema名
          "FUNCTION_NAME", // 函数名
          "REMARKS", // 注释说明
          "FUNCTION_TYPE", // 函数类型
          "SPECIFIC_NAME"); // 特定名称（用于重载函数）

  /** The columns returned by {@link DatabaseMetaData#getSchemas()}. */
  /** DatabaseMetaData.getSchemas()方法返回的列名列表，定义了Schema元数据的结构 */
  public static final List<String> SCHEMA_COLUMNS = // 定义返回的列名常量
      ImmutableList.of("TABLE_SCHEM", // Schema名称
          "TABLE_CATALOG"); // Schema所属的目录

  /** The columns returned by {@link DatabaseMetaData#getTables}. */
  /** DatabaseMetaData.getTables()方法返回的列名列表，定义了表元数据的结构 */
  public static final List<String> TABLE_COLUMNS = // 定义返回的列名常量
      ImmutableList.of("TABLE_CAT", // 表目录名
          "TABLE_SCHEM", // 表Schema名
          "TABLE_NAME", // 表名
          "TABLE_TYPE", // 表类型（TABLE、VIEW等）
          "REMARKS", // 注释说明
          "TYPE_CAT", // 类型目录（用于类型化表）
          "TYPE_SCHEM", // 类型Schema（用于类型化表）
          "TYPE_NAME", // 类型名称（用于类型化表）
          "SELF_REFERENCING_COL_NAME", // 自引用列名（未使用）
          "REF_GENERATION"); // 引用生成方式（未使用）

  /** The columns returned by {@link DatabaseMetaData#getTableTypes()}. */
  /** DatabaseMetaData.getTableTypes()方法返回的列名列表，定义了表类型元数据的结构 */
  public static final List<String> TABLE_TYPE_COLUMNS = // 定义返回的列名常量
      ImmutableList.of("TABLE_TYPE"); // 表类型名称

  /** The columns returned by {@link DatabaseMetaData#getTypeInfo()}. */
  /** DatabaseMetaData.getTypeInfo()方法返回的列名列表，定义了数据类型信息的结构 */
  public static final List<String> TYPE_INFO_COLUMNS = // 定义返回的列名常量
      ImmutableList.of("TYPE_NAME", // 类型名称
          "DATA_TYPE", // JDBC数据类型代码
          "PRECISION", // 最大精度
          "LITERAL_PREFIX", // 字面量前缀（如字符串的引号）
          "LITERAL_SUFFIX", // 字面量后缀（如字符串的引号）
          "CREATE_PARAMS", // 创建参数（未使用）
          "NULLABLE", // 是否可为空
          "CASE_SENSITIVE", // 是否区分大小写
          "SEARCHABLE", // 是否可在WHERE子句中使用
          "UNSIGNED_ATTRIBUTE", // 是否无符号
          "FIXED_PREC_SCALE", // 是否固定精度和缩放
          "AUTO_INCREMENT", // 是否支持自增
          "LOCAL_TYPE_NAME", // 本地类型名称（未使用）
          "MINIMUM_SCALE", // 最小小数位数
          "MAXIMUM_SCALE", // 最大小数位数
          "SQL_DATA_TYPE", // SQL数据类型（未使用）
          "SQL_DATETIME_SUB", // SQL日期时间子类型（未使用）
          "NUM_PREC_RADIX"); // 数值精度基数

  /** Creates a CalciteMetaImpl.
   *
   * @deprecated Use {@link #create(CalciteConnection)} instead.
   */
  /** 创建CalciteMetaImpl实例的构造方法
   * @param connection Calcite连接实现
   * @deprecated 已废弃，请使用create(CalciteConnection)方法代替
   */
  @Deprecated // to be removed before 2.0 // 标记为已废弃，将在2.0版本前移除
  public CalciteMetaImpl(CalciteConnectionImpl connection) { // 构造方法，接收CalciteConnectionImpl参数
    this(connection, CalciteMetaTableFactoryImpl.INSTANCE, // 调用内部构造方法，使用默认的表工厂和列工厂实例
        CalciteMetaColumnFactoryImpl.INSTANCE);
  }

  /** Internal constructor. Protected to allow subclassing. */
  /** 内部构造方法，受保护以允许子类化
   * @param connection Calcite连接实现
   * @param metaTableFactory 元数据表工厂，用于创建MetaTable实例
   * @param metaColumnFactory 元数据列工厂，用于创建MetaColumn实例
   */
  protected CalciteMetaImpl(CalciteConnectionImpl connection, // 受保护的构造方法，允许子类继承
      CalciteMetaTableFactory metaTableFactory, // 元数据表工厂参数
      CalciteMetaColumnFactory metaColumnFactory) { // 元数据列工厂参数
    super(connection); // 调用父类MetaImpl的构造方法，传入连接对象
    this.connProps // 设置连接属性
        .setAutoCommit(false) // 设置自动提交为false（Calcite默认不支持事务）
        .setReadOnly(false) // 设置只读为false
        .setTransactionIsolation(Connection.TRANSACTION_NONE); // 设置事务隔离级别为TRANSACTION_NONE（无事务支持）
    this.connProps.setDirty(false); // 标记连接属性为未修改状态
    this.metaTableFactory = // 初始化元数据表工厂
        requireNonNull(metaTableFactory, "metaTableFactory"); // 确保metaTableFactory不为null，否则抛出NullPointerException
    this.metaColumnFactory = // 初始化元数据列工厂
        requireNonNull(metaColumnFactory, "metaColumnFactory"); // 确保metaColumnFactory不为null，否则抛出NullPointerException
  }

  /**
   * Creates a CalciteMetaImpl.
   *
   * @param connection Calcite connection
   */
  /** 创建CalciteMetaImpl实例的静态工厂方法
   * @param connection Calcite连接对象
   * @return 新创建的CalciteMetaImpl实例
   */
  public static CalciteMetaImpl create(CalciteConnection connection) { // 静态工厂方法，接收CalciteConnection参数
    return create(connection, CalciteMetaTableFactoryImpl.INSTANCE, // 调用重载的create方法，使用默认的表工厂和列工厂
        CalciteMetaColumnFactoryImpl.INSTANCE);
  }

  /**
   * Creates a CalciteMetaImpl.
   *
   * @param connection Calcite connection
   * @param metaTableFactory Factory for creating MetaTable (or subclass)
   * @param metaColumnFactory Factory for creating MetaColumn (or subclass)
   */
  /** 创建CalciteMetaImpl实例的静态工厂方法（完整版本）
   * @param connection Calcite连接对象
   * @param metaTableFactory 用于创建MetaTable（或其子类）的工厂
   * @param metaColumnFactory 用于创建MetaColumn（或其子类）的工厂
   * @return 新创建的CalciteMetaImpl实例
   */
  public static CalciteMetaImpl create(CalciteConnection connection, // 静态工厂方法，接收三个参数
      CalciteMetaTableFactory metaTableFactory, // 元数据表工厂参数
      CalciteMetaColumnFactory metaColumnFactory) { // 元数据列工厂参数
    return new CalciteMetaImpl((CalciteConnectionImpl) connection, // 创建CalciteMetaImpl实例，将连接转换为CalciteConnectionImpl
        metaTableFactory, metaColumnFactory); // 传入表工厂和列工厂
  }

  static <T extends Named> Predicate1<T> namedMatcher(final Pat pattern) { // 创建命名对象匹配器的静态方法，使用泛型T必须实现Named接口
    final Predicate1<String> predicate = LikePattern.matcher(pattern.s); // 创建字符串匹配谓词，使用LIKE模式匹配
    return v1 -> predicate.apply(v1.getName()); // 返回一个谓词，检查对象的名称是否匹配模式
  }

  static Predicate1<String> matcher(final Pat pattern) { // 创建字符串匹配器的静态方法
    return LikePattern.matcher(pattern.s); // 返回一个谓词，检查字符串是否匹配LIKE模式
  }

  @Override public StatementHandle createStatement(ConnectionHandle ch) { // 重写父类的createStatement方法，创建语句句柄
    final StatementHandle h = super.createStatement(ch); // 调用父类方法创建语句句柄
    final CalciteConnectionImpl calciteConnection = getConnection(); // 获取Calcite连接实现
    calciteConnection.server.addStatement(calciteConnection, h); // 将语句句柄添加到服务器的语句管理器中
    return h; // 返回创建的语句句柄
  }

  @Override public void closeStatement(StatementHandle h) { // 重写父类的closeStatement方法，关闭语句
    final CalciteConnectionImpl calciteConnection = getConnection(); // 获取Calcite连接实现
    @SuppressWarnings("unused") // 抑制未使用变量的警告
    final CalciteServerStatement stmt; // 声明服务器语句变量
    try { // 尝试获取语句
      stmt = calciteConnection.server.getStatement(h); // 从服务器获取语句对象
    } catch (NoSuchStatementException e) { // 捕获语句不存在异常
      // statement is not valid; nothing to do // 语句无效，无需处理
      return; // 直接返回
    }
    // stmt.close(); // TODO: implement // TODO: 需要实现语句关闭功能
    calciteConnection.server.removeStatement(h); // 从服务器的语句管理器中移除语句句柄
  }

  private <E> MetaResultSet createResultSet(Enumerable<E> enumerable, // 创建结果集的私有方法，使用泛型E
      Class clazz, List<String> names) { // clazz参数指定元素类型，names参数指定列名列表
    assert !names.isEmpty(); // 断言列名列表不为空
    final List<ColumnMetaData> columns = new ArrayList<>(names.size()); // 创建列元数据列表，容量与列名数量相同
    final List<Field> fields = new ArrayList<>(names.size()); // 创建字段列表，用于反射访问
    final List<String> fieldNames = new ArrayList<>(names.size()); // 创建字段名称列表
    for (String name : names) { // 遍历每个列名
      final int index = fields.size(); // 获取当前索引
      final String fieldName = AvaticaUtils.toCamelCase(name); // 将列名转换为驼峰命名的字段名
      Field field; // 声明字段变量
      try { // 尝试获取公共字段
        field = clazz.getField(fieldName); // 通过反射获取公共字段
      } catch (NoSuchFieldException e) { // 捕获字段不存在异常
        try { // 尝试获取声明字段（包括私有字段）
          // Check if subclass contains the desired field. // 检查子类是否包含所需字段
          field = clazz.getDeclaredField(fieldName); // 通过反射获取声明字段
        } catch (NoSuchFieldException e2) { // 捕获字段不存在异常
          throw new RuntimeException(e2); // 抛出运行时异常
        }
      }
      columns.add(columnMetaData(name, index, field.getType(), false)); // 添加列元数据到列表
      fields.add(field); // 添加字段到列表
      fieldNames.add(fieldName); // 添加字段名到列表
    }
    //noinspection unchecked // 抑制未检查的转换警告
    final Iterable<Object> iterable = (Iterable<Object>) (Iterable) enumerable; // 将可枚举对象转换为Iterable<Object>
    return createResultSet(Collections.emptyMap(), // 调用createResultSet方法创建结果集，传入空的内部参数映射
        columns, CursorFactory.record(clazz, fields, fieldNames), // 传入列元数据和记录游标工厂
        new Frame(0, true, iterable)); // 创建初始帧，偏移量为0，完成标志为true
  }

  @Override protected MetaResultSet createResultSet( // 重写父类的createResultSet方法，创建结果集
      Map<String, Object> internalParameters, List<ColumnMetaData> columns, // 内部参数映射和列元数据列表
      CursorFactory cursorFactory, final Frame firstFrame) { // 游标工厂和第一帧数据
    try { // 尝试创建结果集
      final CalciteConnectionImpl connection = getConnection(); // 获取Calcite连接实现
      final AvaticaStatement statement = connection.createStatement(); // 创建Avatica语句对象
      final CalcitePrepare.CalciteSignature<Object> signature = // 创建Calcite签名对象
          new CalcitePrepare.CalciteSignature<Object>("", // 空字符串作为SQL
              ImmutableList.of(), internalParameters, null, // 空的参数列表、内部参数和根路径
              columns, cursorFactory, null, ImmutableList.of(), -1, // 列元数据、游标工厂、空参数、空列表、负的更新计数
              null, Meta.StatementType.SELECT) { // 空的验证器，语句类型为SELECT
            @Override public Enumerable<Object> enumerable( // 重写enumerable方法，返回可枚举对象
                DataContext dataContext) { // 数据上下文参数
              return Linq4j.asEnumerable(firstFrame.rows); // 将第一帧的行转换为可枚举对象
            }
          };
      return MetaResultSet.create(connection.id, statement.getId(), true, // 创建元数据结果集，传入连接ID、语句ID、自动关闭标志
          signature, firstFrame); // 传入签名和第一帧
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
  }

  CalciteConnectionImpl getConnection() { // 获取Calcite连接实现的私有方法
    return (CalciteConnectionImpl) connection; // 将连接对象强制转换为CalciteConnectionImpl并返回
  }

  @Override public Map<DatabaseProperty, Object> getDatabaseProperties(ConnectionHandle ch) { // 重写父类的getDatabaseProperties方法，获取数据库属性
    final ImmutableMap.Builder<DatabaseProperty, Object> builder = // 创建不可变映射构建器
        ImmutableMap.builder(); // 用于构建数据库属性映射
    for (DatabaseProperty p : DatabaseProperty.values()) { // 遍历所有数据库属性枚举值
      addProperty(builder, p); // 调用addProperty方法添加每个属性
    }
    return builder.build(); // 构建并返回不可变映射
  }

  private static ImmutableMap.Builder<DatabaseProperty, Object> addProperty( // 添加数据库属性的私有静态方法
      ImmutableMap.Builder<DatabaseProperty, Object> builder, // 映射构建器
      DatabaseProperty p) { // 数据库属性枚举
    switch (p) { // 根据属性类型进行分支处理
    case GET_S_Q_L_KEYWORDS: // 如果是获取SQL关键字属性
      return builder.put(p, // 添加SQL关键字到映射
          SqlParser.create("").getMetadata().getJdbcKeywords()); // 通过SQL解析器获取JDBC关键字列表
    case GET_NUMERIC_FUNCTIONS: // 如果是获取数值函数属性
      return builder.put(p, SqlJdbcFunctionCall.getNumericFunctions()); // 添加数值函数列表到映射
    case GET_STRING_FUNCTIONS: // 如果是获取字符串函数属性
      return builder.put(p, SqlJdbcFunctionCall.getStringFunctions()); // 添加字符串函数列表到映射
    case GET_SYSTEM_FUNCTIONS: // 如果是获取系统函数属性
      return builder.put(p, SqlJdbcFunctionCall.getSystemFunctions()); // 添加系统函数列表到映射
    case GET_TIME_DATE_FUNCTIONS: // 如果是获取时间日期函数属性
      return builder.put(p, SqlJdbcFunctionCall.getTimeDateFunctions()); // 添加时间日期函数列表到映射
    default: // 其他未处理的属性
      return builder; // 直接返回构建器，不添加任何内容
    }
  }

  @Override public MetaResultSet getTables(ConnectionHandle ch, // 重写父类的getTables方法，获取表元数据
      String catalog, // 目录名称参数
      final Pat schemaPattern, // Schema模式匹配参数
      final Pat tableNamePattern, // 表名模式匹配参数
      final List<String> typeList) { // 表类型列表参数（如TABLE、VIEW）
    final Predicate1<MetaTable> typeFilter; // 声明表类型过滤谓词
    if (typeList == null) { // 如果表类型列表为null
      typeFilter = Functions.truePredicate1(); // 创建总是返回true的谓词，不过滤表类型
    } else { // 如果表类型列表不为null
      typeFilter = v1 -> typeList.contains(v1.tableType); // 创建谓词，只保留类型在列表中的表
    }
    final Predicate1<MetaSchema> schemaMatcher = namedMatcher(schemaPattern); // 创建Schema名称匹配谓词
    Enumerable<MetaTable> tables = schemas(catalog) // 获取指定目录下的所有Schema
        .where(schemaMatcher) // 过滤出匹配模式的Schema
        .selectMany(schema -> tables(schema, new LikePattern(tableNamePattern.s))) // 对每个Schema，获取匹配表名模式的表
        .where(typeFilter); // 根据表类型进行过滤
    return createResultSet(tables, // 创建结果集，传入表的可枚举对象
        metaTableFactory.getMetaTableClass(), // 使用表工厂获取MetaTable类
        metaTableFactory.getColumnNames()); // 使用表工厂获取列名列表
  }

  @Override public MetaResultSet getTypeInfo(ConnectionHandle ch) { // 重写父类的getTypeInfo方法，获取数据类型信息
    return createResultSet(allTypeInfo(), // 创建结果集，传入所有类型信息的可枚举对象
        MetaTypeInfo.class, TYPE_INFO_COLUMNS); // 使用MetaTypeInfo类和类型信息列名列表
  }

  @Override public MetaResultSet getColumns(ConnectionHandle ch, // 重写父类的getColumns方法，获取列元数据
      String catalog, // 目录名称参数
      Pat schemaPattern, // Schema模式匹配参数
      Pat tableNamePattern, // 表名模式匹配参数
      Pat columnNamePattern) { // 列名模式匹配参数
    final Predicate1<MetaSchema> schemaMatcher = namedMatcher(schemaPattern); // 创建Schema名称匹配谓词
    final Predicate1<MetaColumn> columnMatcher = // 创建列名称匹配谓词
        namedMatcher(columnNamePattern); // 使用namedMatcher方法创建
    return createResultSet(schemas(catalog) // 创建结果集，从指定目录的Schema开始
            .where(schemaMatcher) // 过滤出匹配模式的Schema
            .selectMany(schema -> tables(schema, new LikePattern(tableNamePattern.s))) // 对每个Schema，获取匹配表名模式的表
            .selectMany(this::columns) // 对每个表，获取所有列
            .where(columnMatcher), // 过滤出匹配列名模式的列
        metaColumnFactory.getMetaColumnClass(), // 使用列工厂获取MetaColumn类
        metaColumnFactory.getColumnNames()); // 使用列工厂获取列名列表
  }

  Enumerable<MetaCatalog> catalogs() { // 获取目录列表的方法
    final String catalog; // 声明目录名称变量
    try { // 尝试获取目录名称
      catalog = connection.getCatalog(); // 从连接对象获取当前目录
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
    return Linq4j.asEnumerable( // 将目录对象转换为可枚举对象
        ImmutableList.of(new MetaCatalog(catalog))); // 创建包含单个目录的不可变列表
  }

  Enumerable<MetaTableType> tableTypes() { // 获取表类型列表的方法
    return Linq4j.asEnumerable( // 将表类型对象转换为可枚举对象
        ImmutableList.of( // 创建不可变列表
            new MetaTableType("TABLE"), new MetaTableType("VIEW"))); // 包含TABLE和VIEW两种表类型
  }

  Enumerable<MetaSchema> schemas(final String catalog) { // 获取Schema列表的方法
    return Linq4j.asEnumerable( // 将Schema对象转换为可枚举对象
        getConnection().rootSchema.getSubSchemaMap().values()) // 获取根Schema的所有子Schema
        .select((Function1<CalciteSchema, MetaSchema>) calciteSchema -> // 将CalciteSchema转换为MetaSchema
            new CalciteMetaSchema(calciteSchema, catalog, // 创建CalciteMetaSchema对象
                calciteSchema.getName())) // 传入CalciteSchema、目录名和Schema名
        .orderBy((Function1<MetaSchema, Comparable>) metaSchema -> // 对Schema进行排序
            (Comparable) FlatLists.of(Util.first(metaSchema.tableCatalog, ""), // 使用扁平列表创建排序键，目录名优先
                metaSchema.tableSchem)); // 然后按Schema名排序
  }

  Enumerable<MetaTable> tables(String catalog) { // 获取指定目录下所有表的方法
    return schemas(catalog) // 获取指定目录下的所有Schema
        .selectMany(schema -> // 对每个Schema
            tables(schema, LikePattern.any())); // 获取所有表（使用any模式匹配所有表名）
  }

  Enumerable<MetaTable> tables(final MetaSchema schema_, LikePattern tableNamePattern) { // 获取指定Schema下匹配表名模式的表
    final CalciteMetaSchema schema = (CalciteMetaSchema) schema_; // 将MetaSchema强制转换为CalciteMetaSchema
    return Linq4j.asEnumerable(schema.calciteSchema.getTableNames(tableNamePattern)) // 获取匹配表名模式的表名列表
        .select(name -> { // 对每个表名
          final Table table = // 获取表对象
              requireNonNull(schema.calciteSchema.getTable(name, true), // 尝试获取表（区分大小写）
                  () -> "table " + name + " is not found (case sensitive)") // 如果表不存在，提供错误消息
                  .getTable(); // 从CalciteSchema.Table获取实际的Table对象
          return metaTableFactory.createTable(table, schema.tableCatalog, // 使用表工厂创建MetaTable对象
              schema.tableSchem, name); // 传入表对象、目录名、Schema名和表名
        })
        .concat( // 连接另一个可枚举对象
            Linq4j.asEnumerable( // 将基于无参函数的表转换为可枚举对象
                schema.calciteSchema.getTablesBasedOnNullaryFunctions() // 获取基于无参函数的表（如宏表）
                    .entrySet()) // 获取键值对集合
                .select(pair -> { // 对每个键值对
                  final Table table = pair.getValue(); // 获取表对象
                  return metaTableFactory.createTable(table, // 使用表工厂创建MetaTable对象
                      schema.tableCatalog, // 传入目录名
                      schema.tableSchem, // 传入Schema名
                      pair.getKey()); // 传入表名（键）
                }));
  }

  private ImmutableList<MetaTypeInfo> getAllDefaultType() { // 获取所有默认类型信息的私有方法
    final ImmutableList.Builder<MetaTypeInfo> allTypeList = // 创建不可变列表构建器
        ImmutableList.builder(); // 用于构建类型信息列表
    final CalciteConnectionImpl conn = (CalciteConnectionImpl) connection; // 获取Calcite连接实现
    final RelDataTypeSystem typeSystem = conn.typeFactory.getTypeSystem(); // 获取类型系统
    for (SqlTypeName sqlTypeName : SqlTypeName.values()) { // 遍历所有SQL类型名称
      if (sqlTypeName.isSpecial()) { // 如果是特殊类型
        // Skip internal types (NULL, ANY, SYMBOL, SARG). // 跳过内部类型
        continue; // 继续下一个类型
      }
      allTypeList.add( // 添加类型信息到列表
          new MetaTypeInfo(sqlTypeName.getName(), // 类型名称
              sqlTypeName.getJdbcOrdinal(), // JDBC类型代码
              typeSystem.getMaxPrecision(sqlTypeName), // 最大精度
              typeSystem.getLiteral(sqlTypeName, true), // 字面量前缀
              typeSystem.getLiteral(sqlTypeName, false), // 字面量后缀
              // All types are nullable // 所有类型都可为空
              (short) DatabaseMetaData.typeNullable, // 可为空标志
              typeSystem.isCaseSensitive(sqlTypeName), // 是否区分大小写
              // Making all type searchable; we may want to // 将所有类型标记为可搜索；可能需要
              // be specific and declare under SqlTypeName // 更具体地在SqlTypeName下声明
              (short) DatabaseMetaData.typeSearchable, // 可搜索标志
              false, // 无符号属性（默认为false）
              false, // 固定精度和缩放（默认为false）
              typeSystem.isAutoincrement(sqlTypeName), // 是否支持自增
              (short) typeSystem.getMinScale(sqlTypeName), // 最小小数位数
              (short) typeSystem.getMaxScale(sqlTypeName), // 最大小数位数
              typeSystem.getNumTypeRadix(sqlTypeName))); // 数值精度基数
    }
    return allTypeList.build(); // 构建并返回不可变列表
  }

  protected Enumerable<MetaTypeInfo> allTypeInfo() { // 获取所有类型信息的受保护方法
    return Linq4j.asEnumerable(getAllDefaultType()); // 将默认类型列表转换为可枚举对象
  }

  public Enumerable<MetaColumn> columns(final MetaTable table_) { // 获取表的列信息的方法
    final CalciteMetaTable table = (CalciteMetaTable) table_; // 将MetaTable强制转换为CalciteMetaTable
    final RelDataType rowType = // 获取表的行类型
        table.calciteTable.getRowType(getConnection().typeFactory); // 使用连接的类型工厂获取行类型
    return Linq4j.asEnumerable(rowType.getFieldList()) // 将字段列表转换为可枚举对象
        .select(field -> { // 对每个字段
          final int precision = // 计算列的精度
              field.getType().getSqlTypeName().allowsPrec() // 如果类型允许精度
                  && !(field.getType() // 且不是Java类型
                  instanceof RelDataTypeFactoryImpl.JavaType)
                  ? field.getType().getPrecision() // 则使用类型的精度
                  : -1; // 否则使用-1表示不适用
          // MEASURE is a special case. We want to surface the type returned // MEASURE是特殊情况。我们想要返回
          // after aggregation rather than its default java.sql.Type, // 聚合后的类型，而不是默认的java.sql.Type
          // OTHER(1111). // OTHER(1111)。
          final int jdbcOrdinal = // 计算JDBC类型代码
              Optional.ofNullable(field.getType().getMeasureElementType()) // 如果存在度量元素类型
                  .map(RelDataType::getSqlTypeName) // 获取SQL类型名称
                  .map(SqlTypeName::getJdbcOrdinal) // 获取JDBC类型代码
                  .orElse(field.getType().getSqlTypeName().getJdbcOrdinal()); // 否则使用字段类型的JDBC代码
          return metaColumnFactory.createColumn( // 使用列工厂创建MetaColumn对象
              table.calciteTable, // 传入表对象
              table.tableCat, // 传入目录名
              table.tableSchem, // 传入Schema名
              table.tableName, // 传入表名
              field.getName(), // 传入列名
              jdbcOrdinal, // 传入JDBC类型代码
              field.getType().getFullTypeString(), // 传入完整类型字符串
              precision, // 传入精度
              field.getType().getSqlTypeName().allowsScale() // 如果类型允许小数位数
                  ? field.getType().getScale() // 则使用类型的小数位数
                  : null, // 否则为null
              10, // 数值精度基数（固定为10）
              field.getType().isNullable() // 如果类型可为空
                  ? DatabaseMetaData.columnNullable // 则使用可为空标志
                  : DatabaseMetaData.columnNoNulls, // 否则使用不可为空标志
              precision, // 传入精度（作为字符字节长度）
              field.getIndex() + 1, // 传入列位置（从1开始）
              field.getType().isNullable() ? "YES" : "NO"); // 传入是否可为空的字符串表示
        });
  }

  @Override public MetaResultSet getSchemas(ConnectionHandle ch, String catalog, // 重写父类的getSchemas方法，获取Schema元数据
      Pat schemaPattern) { // Schema模式匹配参数
    final Predicate1<MetaSchema> schemaMatcher = namedMatcher(schemaPattern); // 创建Schema名称匹配谓词
    return createResultSet(schemas(catalog).where(schemaMatcher), // 创建结果集，过滤出匹配模式的Schema
        MetaSchema.class, SCHEMA_COLUMNS); // 使用MetaSchema类和Schema列名列表
  }

  @Override public MetaResultSet getCatalogs(ConnectionHandle ch) { // 重写父类的getCatalogs方法，获取目录元数据
    return createResultSet(catalogs(), // 创建结果集，传入目录的可枚举对象
        MetaCatalog.class, CATALOG_COLUMNS); // 使用MetaCatalog类和目录列名列表
  }

  @Override public MetaResultSet getTableTypes(ConnectionHandle ch) { // 重写父类的getTableTypes方法，获取表类型元数据
    return createResultSet(tableTypes(), // 创建结果集，传入表类型的可枚举对象
        MetaTableType.class, TABLE_TYPE_COLUMNS); // 使用MetaTableType类和表类型列名列表
  }

  @Override public MetaResultSet getFunctions(ConnectionHandle ch, // 重写父类的getFunctions方法，获取函数元数据
      String catalog, // 目录名称参数
      Pat schemaPattern, // Schema模式匹配参数
      Pat functionNamePattern) { // 函数名模式匹配参数
    final Predicate1<MetaSchema> schemaMatcher = namedMatcher(schemaPattern); // 创建Schema名称匹配谓词
    return createResultSet(schemas(catalog) // 创建结果集，从指定目录的Schema开始
            .where(schemaMatcher) // 过滤出匹配模式的Schema
            .selectMany(schema -> functions(schema, catalog, matcher(functionNamePattern))) // 对每个Schema，获取匹配函数名模式的函数
            .orderBy(x -> // 对函数进行排序
                (Comparable) FlatLists.of( // 使用扁平列表创建排序键
                    x.functionCat, x.functionSchem, x.functionName, x.specificName)), // 按目录、Schema、函数名、特定名称排序
        MetaFunction.class, FUNCTION_COLUMNS); // 使用MetaFunction类和函数列名列表
  }

  Enumerable<MetaFunction> functions(final MetaSchema schema_, final String catalog) { // 获取指定Schema下所有函数的方法
    final CalciteMetaSchema schema = (CalciteMetaSchema) schema_; // 将MetaSchema强制转换为CalciteMetaSchema
    Enumerable<MetaFunction> opTableFunctions = Linq4j.emptyEnumerable(); // 初始化操作符表函数为空可枚举对象
    if (schema.calciteSchema.schema.equals(MetadataSchema.INSTANCE)) { // 如果Schema是元数据Schema
      SqlOperatorTable opTable = getConnection().config() // 获取连接配置
          .fun(SqlOperatorTable.class, SqlStdOperatorTable.instance()); // 获取SQL操作符表
      List<SqlOperator> q = opTable.getOperatorList(); // 获取操作符列表
      opTableFunctions = Linq4j.asEnumerable(q) // 将操作符列表转换为可枚举对象
          .where(op -> SqlKind.FUNCTION.contains(op.getKind())) // 过滤出函数类型的操作符
          .select(op -> // 对每个操作符
              new MetaFunction( // 创建MetaFunction对象
                  catalog, // 传入目录名
                  schema.getName(), // 传入Schema名
                  op.getName(), // 传入操作符名
                  (short) DatabaseMetaData.functionResultUnknown, // 传入函数结果未知标志
                  op.getName())); // 传入特定名称（与操作符名相同）
    }
    return Linq4j.asEnumerable(schema.calciteSchema.getFunctionNames()) // 将函数名列表转换为可枚举对象
        .selectMany(name -> // 对每个函数名
            Linq4j.asEnumerable(schema.calciteSchema.getFunctions(name, true)) // 获取该名称的所有函数（区分大小写）
                // exclude materialized views from the result set // 从结果集中排除物化视图
                .where(fn -> !(fn instanceof MaterializedViewTable.MaterializedViewTableMacro)) // 过滤掉物化视图宏
                .select(fnx -> // 对每个函数
                    new MetaFunction( // 创建MetaFunction对象
                        catalog, // 传入目录名
                        schema.getName(), // 传入Schema名
                        name, // 传入函数名
                        (short) DatabaseMetaData.functionResultUnknown, // 传入函数结果未知标志
                        name // 传入特定名称（与函数名相同）
                    )
                )
        )
        .concat(opTableFunctions); // 连接操作符表函数
  }

  Enumerable<MetaFunction> functions(final MetaSchema schema, final String catalog, // 获取指定Schema下匹配函数名模式的函数
      final Predicate1<String> functionNameMatcher) { // 函数名匹配谓词参数
    return functions(schema, catalog) // 获取所有函数
        .where(v1 -> functionNameMatcher.apply(v1.functionName)); // 过滤出匹配函数名模式的函数
  }

  @Override public Iterable<Object> createIterable(StatementHandle handle, QueryState state, // 重写父类的createIterable方法，创建可迭代对象
      Signature signature, @Nullable List<TypedValue> parameterValues, @Nullable Frame firstFrame) { // 签名、参数值和第一帧参数
    // Drop QueryState // 忽略QueryState参数
    return _createIterable(handle, signature, parameterValues, firstFrame); // 调用内部方法_createIterable
  }

  Iterable<Object> _createIterable(StatementHandle handle, // 创建可迭代对象的内部方法
      Signature signature, @Nullable List<TypedValue> parameterValues, @Nullable Frame firstFrame) { // 签名、参数值和第一帧参数
    try { // 尝试创建可迭代对象
      //noinspection unchecked // 抑制未检查的转换警告
      final CalcitePrepare.CalciteSignature<Object> calciteSignature = // 将签名强制转换为Calcite签名
          (CalcitePrepare.CalciteSignature<Object>) signature;
      return getConnection().enumerable(handle, calciteSignature, parameterValues); // 调用连接的enumerable方法获取可迭代对象
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e.getMessage()); // 抛出运行时异常，只包含消息
    }
  }

  @Override public StatementHandle prepare(ConnectionHandle ch, String sql, // 重写父类的prepare方法，准备SQL语句
      long maxRowCount) { // 最大行数参数
    final StatementHandle h = createStatement(ch); // 创建语句句柄
    final CalciteConnectionImpl calciteConnection = getConnection(); // 获取Calcite连接实现

    final CalciteServerStatement statement; // 声明服务器语句变量
    try { // 尝试获取语句
      statement = calciteConnection.server.getStatement(h); // 从服务器获取语句对象
    } catch (NoSuchStatementException e) { // 捕获语句不存在异常
      // Not possible. We just created a statement. // 不可能发生，我们刚创建了语句
      throw new AssertionError("missing statement", e); // 抛出断言错误
    }
    final Context context = statement.createPrepareContext(); // 创建准备上下文
    final CalcitePrepare.Query<Object> query = toQuery(context, sql); // 将SQL字符串转换为Query对象
    h.signature = calciteConnection.parseQuery(query, context, maxRowCount); // 解析查询并设置签名
    statement.setSignature(h.signature); // 设置语句的签名
    return h; // 返回语句句柄
  }

  @SuppressWarnings("deprecation") // 抑制已废弃方法的警告
  @Override public ExecuteResult prepareAndExecute(StatementHandle h, // 重写父类的prepareAndExecute方法，准备并执行SQL语句
      String sql, long maxRowCount, PrepareCallback callback) // SQL、最大行数和回调参数
      throws NoSuchStatementException { // 可能抛出语句不存在异常
    return prepareAndExecute(h, sql, maxRowCount, -1, callback); // 调用重载方法，最大第一帧行数为-1
  }

  @Override public ExecuteResult prepareAndExecute(StatementHandle h, // 重写父类的prepareAndExecute方法，准备并执行SQL语句（完整版本）
      String sql, long maxRowCount, int maxRowsInFirstFrame, // SQL、最大行数和最大第一帧行数参数
      PrepareCallback callback) throws NoSuchStatementException { // 回调参数，可能抛出语句不存在异常
    final CalcitePrepare.CalciteSignature<Object> signature; // 声明Calcite签名变量
    try { // 尝试准备并执行
      final int updateCount; // 声明更新计数变量
      synchronized (callback.getMonitor()) { // 同步回调的监视器
        callback.clear(); // 清除回调状态
        final CalciteConnectionImpl calciteConnection = getConnection(); // 获取Calcite连接实现
        final CalciteServerStatement statement = // 获取服务器语句
            calciteConnection.server.getStatement(h);
        final Context context = statement.createPrepareContext(); // 创建准备上下文
        final CalcitePrepare.Query<Object> query = toQuery(context, sql); // 将SQL转换为Query对象
        signature = calciteConnection.parseQuery(query, context, maxRowCount); // 解析查询
        statement.setSignature(signature); // 设置语句签名
        switch (signature.statementType) { // 根据语句类型进行分支处理
        case CREATE: // 如果是CREATE语句
        case DROP: // 如果是DROP语句
        case ALTER: // 如果是ALTER语句
        case OTHER_DDL: // 如果是其他DDL语句
          updateCount = 0; // DDL不产生结果集，更新计数为0
          break; // 跳出switch
        default: // 其他语句类型（SELECT、DML等）
          updateCount = -1; // SELECT和DML产生结果集，更新计数为-1
          break; // 跳出switch
        }
        callback.assign(signature, null, updateCount); // 分配签名、空帧和更新计数到回调
      }
      callback.execute(); // 执行回调
      final MetaResultSet metaResultSet = // 创建元数据结果集
          MetaResultSet.create(h.connectionId, h.id, false, signature, null, updateCount); // 传入连接ID、语句ID、自动关闭标志、签名、空帧和更新计数
      return new ExecuteResult(ImmutableList.of(metaResultSet)); // 返回执行结果，包含结果集列表
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
    // TODO: share code with prepare and createIterable // TODO: 与prepare和createIterable共享代码
  }

  /** Wraps the SQL string in a
   * {@link org.apache.calcite.jdbc.CalcitePrepare.Query} object, giving the
   * {@link Hook#STRING_TO_QUERY} hook chance to override.
   */
  /** 将SQL字符串包装在CalcitePrepare.Query对象中，给Hook.STRING_TO_QUERY钩子机会覆盖
   * @param context 准备上下文
   * @param sql SQL字符串
   * @return Query对象
   */
  private static CalcitePrepare.Query<Object> toQuery( // 将SQL转换为Query对象的私有静态方法
          Context context, String sql) { // 上下文和SQL字符串参数
    final Holder<CalcitePrepare.Query<Object>> queryHolder = // 创建Query的持有者
        Holder.of(CalcitePrepare.Query.of(sql)); // 初始值为SQL字符串转换的Query
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
        .parserConfig(SqlParser.Config.DEFAULT) // 设置默认解析器配置
        .defaultSchema(context.getRootSchema().plus()) // 设置默认Schema
        .build(); // 构建配置
    Hook.STRING_TO_QUERY.run(Pair.of(config, queryHolder)); // 运行STRING_TO_QUERY钩子，允许覆盖Query
    return queryHolder.get(); // 返回持有者中的Query对象
  }

  @Override public Frame fetch(StatementHandle h, long offset, // 重写父类的fetch方法，获取查询结果
      int fetchMaxRowCount) throws NoSuchStatementException { // 偏移量和最大获取行数参数，可能抛出语句不存在异常
    final CalciteConnectionImpl calciteConnection = getConnection(); // 获取Calcite连接实现
    CalciteServerStatement stmt = calciteConnection.server.getStatement(h); // 获取服务器语句
    final Signature signature = // 获取语句签名
        requireNonNull(stmt.getSignature(), // 确保签名不为null
            () -> "stmt.getSignature() is null for " + stmt); // 否则提供错误消息
    final Iterator<Object> iterator; // 声明迭代器变量
    Iterator<Object> stmtResultSet = stmt.getResultSet(); // 获取语句的结果集迭代器
    if (stmtResultSet == null) { // 如果结果集迭代器为null
      final Iterable<Object> iterable = // 创建可迭代对象
          _createIterable(h, signature, null, null); // 调用内部方法创建可迭代对象
      iterator = iterable.iterator(); // 获取迭代器
      stmt.setResultSet(iterator); // 设置语句的结果集迭代器
    } else { // 如果结果集迭代器已存在
      iterator = stmtResultSet; // 直接使用现有的迭代器
    }
    final List rows = // 收集行数据
        MetaImpl.collect(signature.cursorFactory, // 使用游标工厂
            LimitIterator.of(iterator, fetchMaxRowCount), // 使用限制迭代器限制行数
            new ArrayList<>()); // 收集到新的ArrayList中
    boolean done = fetchMaxRowCount == 0 || rows.size() < fetchMaxRowCount; // 判断是否完成（最大行数为0或行数小于最大行数）
    @SuppressWarnings("unchecked") List<Object> rows1 = (List<Object>) rows; // 强制转换为Object列表
    return new Meta.Frame(offset, done, rows1); // 返回新的帧，包含偏移量、完成标志和行数据
  }

  @SuppressWarnings("deprecation") // 抑制已废弃方法的警告
  @Override public ExecuteResult execute(StatementHandle h, // 重写父类的execute方法，执行已准备的语句
      List<TypedValue> parameterValues, long maxRowCount) // 参数值列表和最大行数参数
      throws NoSuchStatementException { // 可能抛出语句不存在异常
    return execute(h, parameterValues, Ints.saturatedCast(maxRowCount)); // 调用重载方法，将maxRowCount转换为int
  }

  @Override public ExecuteResult execute(StatementHandle h, // 重写父类的execute方法，执行已准备的语句（完整版本）
      List<TypedValue> parameterValues, int maxRowsInFirstFrame) // 参数值列表和最大第一帧行数参数
      throws NoSuchStatementException { // 可能抛出语句不存在异常
    final CalciteConnectionImpl calciteConnection = getConnection(); // 获取Calcite连接实现
    CalciteServerStatement stmt = calciteConnection.server.getStatement(h); // 获取服务器语句
    final Signature signature = // 获取语句签名
        requireNonNull(stmt.getSignature(), // 确保签名不为null
            () -> "stmt.getSignature() is null for " + stmt); // 否则提供错误消息

    MetaResultSet metaResultSet; // 声明元数据结果集变量
    if (signature.statementType.canUpdate()) { // 如果语句类型可以更新（INSERT、UPDATE、DELETE等）
      final Iterable<Object> iterable = // 创建可迭代对象
          _createIterable(h, signature, parameterValues, null); // 调用内部方法创建可迭代对象
      final Iterator<Object> iterator = iterable.iterator(); // 获取迭代器
      stmt.setResultSet(iterator); // 设置语句的结果集迭代器
      metaResultSet = // 创建更新计数结果集
          MetaResultSet.count(h.connectionId, h.id, // 传入连接ID和语句ID
              ((Number) iterator.next()).intValue()); // 从迭代器获取更新计数并转换为int
    } else { // 如果语句类型不能更新（SELECT等）
      // Don't populate the first frame. // 不填充第一帧
      // It's not worth saving a round-trip, since we're local. // 不值得节省往返时间，因为我们是本地的
      final Meta.Frame frame = // 创建空的第一帧
          new Meta.Frame(0, false, Collections.emptyList()); // 偏移量为0，未完成，空列表
      metaResultSet = // 创建结果集
          MetaResultSet.create(h.connectionId, h.id, false, signature, frame); // 传入连接ID、语句ID、自动关闭标志、签名和帧
    }

    return new ExecuteResult(ImmutableList.of(metaResultSet)); // 返回执行结果，包含结果集列表
  }

  @Override public ExecuteBatchResult executeBatch(StatementHandle h, // 重写父类的executeBatch方法，执行批量语句
      List<List<TypedValue>> parameterValueLists) throws NoSuchStatementException { // 参数值列表的列表参数，可能抛出语句不存在异常
    final List<Long> updateCounts = new ArrayList<>(); // 创建更新计数列表
    for (List<TypedValue> parameterValueList : parameterValueLists) { // 遍历每个参数值列表
      ExecuteResult executeResult = execute(h, parameterValueList, -1); // 执行单条语句，最大行数为-1（无限制）
      final long updateCount = // 获取更新计数
          executeResult.resultSets.size() == 1 // 如果结果集大小为1
              ? executeResult.resultSets.get(0).updateCount // 则使用第一个结果集的更新计数
              : -1L; // 否则为-1
      updateCounts.add(updateCount); // 添加更新计数到列表
    }
    return new ExecuteBatchResult(Longs.toArray(updateCounts)); // 返回批量执行结果，将Long列表转换为long数组
  }

  @Override public ExecuteBatchResult prepareAndExecuteBatch( // 重写父类的prepareAndExecuteBatch方法，准备并执行批量语句
      final StatementHandle h, // 语句句柄参数
      List<String> sqlCommands) throws NoSuchStatementException { // SQL命令列表参数，可能抛出语句不存在异常
    final CalciteConnectionImpl calciteConnection = getConnection(); // 获取Calcite连接实现
    final CalciteServerStatement statement = // 获取服务器语句
        calciteConnection.server.getStatement(h);
    final List<Long> updateCounts = new ArrayList<>(); // 创建更新计数列表
    final Meta.PrepareCallback callback = // 创建准备回调
        new Meta.PrepareCallback() { // 匿名内部类
          long updateCount; // 更新计数字段
          @Nullable Signature signature; // 签名字段，可能为null

          @Override public Object getMonitor() { // 重写getMonitor方法
            return statement; // 返回语句对象作为监视器
          }

          @Override public void clear() {} // 重写clear方法，清除状态（空实现）

          @Override public void assign(Meta.Signature signature, Meta.@Nullable Frame firstFrame, // 重写assign方法，分配签名和帧
              long updateCount) { // 签名、第一帧和更新计数参数
            this.signature = signature; // 保存签名
            this.updateCount = updateCount; // 保存更新计数
          }

          @Override public void execute() { // 重写execute方法，执行语句
            Signature signature = requireNonNull(this.signature, "signature"); // 确保签名不为null
            if (signature.statementType.canUpdate()) { // 如果语句类型可以更新
              final Iterable<Object> iterable = // 创建可迭代对象
                  _createIterable(h, signature, ImmutableList.of(), // 调用内部方法，使用空参数列表
                      null); // 第一帧为null
              final Iterator<Object> iterator = iterable.iterator(); // 获取迭代器
              updateCount = ((Number) iterator.next()).longValue(); // 获取更新计数并转换为long
            }
            updateCounts.add(updateCount); // 添加更新计数到列表
          }
        };
    for (String sqlCommand : sqlCommands) { // 遍历每个SQL命令
      Util.discard(prepareAndExecute(h, sqlCommand, -1L, -1, callback)); // 准备并执行SQL命令，丢弃返回值
    }
    return new ExecuteBatchResult(Longs.toArray(updateCounts)); // 返回批量执行结果，将Long列表转换为long数组
  }

  /** A trojan-horse method, subject to change without notice. */
  /** 一个特洛伊木马方法，可能会在没有通知的情况下更改（仅供测试使用）
   * @param connection Calcite连接对象
   * @return 数据上下文对象
   */
  @VisibleForTesting // 标记为仅用于测试
  public static DataContext createDataContext(CalciteConnection connection) { // 创建数据上下文的静态方法
    return ((CalciteConnectionImpl) connection) // 将连接转换为CalciteConnectionImpl
        .createDataContext(ImmutableMap.of(), // 创建数据上下文，传入空的参数映射
            CalciteSchema.from(connection.getRootSchema())); // 传入从根Schema转换的CalciteSchema
  }

  /** A trojan-horse method, subject to change without notice. */
  /** 一个特洛伊木马方法，可能会在没有通知的情况下更改（仅供测试使用）
   * @param schema CalciteSchema对象
   * @param typeFactory Java类型工厂，可能为null
   * @return Calcite连接对象
   */
  @VisibleForTesting // 标记为仅用于测试
  public static CalciteConnection connect(CalciteSchema schema, // 创建Calcite连接的静态方法
      @Nullable JavaTypeFactory typeFactory) { // Schema和类型工厂参数
    return DRIVER.connect(schema, typeFactory); // 调用驱动的connect方法创建连接
  }

  @Override public boolean syncResults(StatementHandle h, QueryState state, // 重写父类的syncResults方法，同步结果
      long offset) { // 偏移量参数
    // Doesn't have application in Calcite itself. // 在Calcite本身中没有应用
    throw new UnsupportedOperationException(); // 抛出不支持操作异常
  }

  @Override public void commit(ConnectionHandle ch) { // 重写父类的commit方法，提交事务
    throw new UnsupportedOperationException(); // 抛出不支持操作异常（Calcite不支持事务）
  }

  @Override public void rollback(ConnectionHandle ch) { // 重写父类的rollback方法，回滚事务
    throw new UnsupportedOperationException(); // 抛出不支持操作异常（Calcite不支持事务）
  }

  /** Metadata describing a Calcite table. */
  /** 描述Calcite表的元数据类 */
  public static class CalciteMetaTable extends MetaTable { // 定义CalciteMetaTable类，继承自MetaTable
    private final Table calciteTable; // Calcite表对象，存储实际的表实现

    /**
     *  Creates a CalciteMetaTable.
     *
     * @param calciteTable Table
     * @param tableCat Table catalog, or null
     * @param tableSchem Table schema, or null
     * @param tableName Table name
     */
    /** 创建CalciteMetaTable实例的构造方法
     * @param calciteTable Calcite表对象
     * @param tableCat 表目录名，可能为null
     * @param tableSchem 表Schema名，可能为null
     * @param tableName 表名
     */
    public CalciteMetaTable(Table calciteTable, String tableCat, // 构造方法，接收表对象和元数据参数
        String tableSchem, String tableName) { // Schema名和表名参数
      super(tableCat, tableSchem, tableName, // 调用父类MetaTable的构造方法
          calciteTable.getJdbcTableType().jdbcName); // 传入JDBC表类型名称
      this.calciteTable = requireNonNull(calciteTable, "calciteTable"); // 确保calciteTable不为null，否则抛出NullPointerException
    }
  }

  /** Metadata describing a Calcite schema. */
  /** 描述Calcite Schema的元数据类 */
  private static class CalciteMetaSchema extends MetaSchema { // 定义CalciteMetaSchema类，继承自MetaSchema
    private final CalciteSchema calciteSchema; // CalciteSchema对象，存储实际的Schema实现

    CalciteMetaSchema(CalciteSchema calciteSchema, // 构造方法，接收CalciteSchema和元数据参数
        String tableCatalog, String tableSchem) { // 目录名和Schema名参数
      super(tableCatalog, tableSchem); // 调用父类MetaSchema的构造方法
      this.calciteSchema = calciteSchema; // 保存CalciteSchema对象
    }
  }

  /** Table whose contents are metadata.
   *
   * @param <E> element type */
  /** 内容为元数据的表抽象类
   * @param <E> 元素类型
   */
  abstract static class MetadataTable<E> extends AbstractQueryableTable { // 定义MetadataTable抽象类，继承自AbstractQueryableTable
    MetadataTable(Class<E> clazz) { // 构造方法，接收元素类型参数
      super(clazz); // 调用父类构造方法，传入元素类型
    }

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写getRowType方法，获取行类型
      return ((JavaTypeFactory) typeFactory).createType(elementType); // 使用Java类型工厂创建元素类型的RelDataType
    }

    @Override public Schema.TableType getJdbcTableType() { // 重写getJdbcTableType方法，获取JDBC表类型
      return Schema.TableType.SYSTEM_TABLE; // 返回系统表类型
    }

    @SuppressWarnings("unchecked") // 抑制未检查的转换警告
    @Override public Class<E> getElementType() { // 重写getElementType方法，获取元素类型
      return (Class<E>) elementType; // 强制转换并返回元素类型
    }

    protected abstract Enumerator<E> enumerator(CalciteMetaImpl connection); // 抽象方法，创建枚举器，子类必须实现

    @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 重写asQueryable方法，创建可查询对象
        SchemaPlus schema, String tableName) { // 查询提供者、Schema和表名参数
      return new AbstractTableQueryable<T>(queryProvider, schema, this, // 创建抽象表可查询对象
          tableName) { // 传入参数和this
        @SuppressWarnings("unchecked") // 抑制未检查的转换警告
        @Override public Enumerator<T> enumerator() { // 重写enumerator方法，创建枚举器
          return (Enumerator<T>) MetadataTable.this.enumerator( // 调用外部类的enumerator方法
              ((CalciteConnectionImpl) queryProvider).meta()); // 从查询提供者获取CalciteMetaImpl
        }
      };
    }
  }

  /** Iterator that returns at most {@code limit} rows from an underlying
   * {@link Iterator}.
   *
   * @param <E> element type */
  /** 限制迭代器，从底层迭代器返回最多limit行
   * @param <E> 元素类型
   */
  private static class LimitIterator<E> implements Iterator<E> { // 定义LimitIterator类，实现Iterator接口
    private final Iterator<E> iterator; // 底层迭代器
    private final long limit; // 最大行数限制
    int i = 0; // 当前行数计数器

    private LimitIterator(Iterator<E> iterator, long limit) { // 私有构造方法，接收迭代器和限制参数
      this.iterator = iterator; // 保存迭代器
      this.limit = limit; // 保存限制
    }

    static <E> Iterator<E> of(Iterator<E> iterator, long limit) { // 静态工厂方法，创建限制迭代器
      if (limit <= 0) { // 如果限制小于等于0
        return iterator; // 直接返回原始迭代器（不限制）
      }
      return new LimitIterator<>(iterator, limit); // 否则创建新的限制迭代器
    }

    @Override public boolean hasNext() { // 重写hasNext方法，检查是否有下一个元素
      return iterator.hasNext() && i < limit; // 返回底层迭代器是否有下一个元素且当前行数小于限制
    }

    @Override public E next() { // 重写next方法，获取下一个元素
      ++i; // 增加行数计数器
      return iterator.next(); // 返回底层迭代器的下一个元素
    }

    @Override public void remove() { // 重写remove方法，移除当前元素
      throw new UnsupportedOperationException(); // 抛出不支持操作异常
    }
  }
}