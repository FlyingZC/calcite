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
// Apache许可证声明：表明此文件遵循Apache 2.0许可证
package org.apache.calcite.jdbc; // 声明此类属于org.apache.calcite.jdbc包，是Calcite JDBC驱动层的核心实现

import org.apache.calcite.DataContext; // 导入数据上下文接口，用于在查询执行过程中传递数据和配置
import org.apache.calcite.DataContexts; // 导入数据上下文工具类，提供创建和管理DataContext的方法
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口，用于在Java类型和SQL类型之间转换
import org.apache.calcite.avatica.AvaticaConnection; // 导入Avatica连接基类，CalciteConnection继承此类提供JDBC连接功能
import org.apache.calcite.avatica.AvaticaFactory; // 导入Avatica工厂接口，用于创建JDBC对象（连接、语句等）
import org.apache.calcite.avatica.AvaticaSite; // 导入Avatica站点类，用于参数绑定和类型转换
import org.apache.calcite.avatica.AvaticaStatement; // 导入Avatica语句基类，所有Calcite语句都基于此类
import org.apache.calcite.avatica.Helper; // 导入Avatica辅助类，提供异常处理等通用功能
import org.apache.calcite.avatica.InternalProperty; // 导入Avatica内部属性类，用于配置连接的内部行为
import org.apache.calcite.avatica.Meta; // 导入Avatica元数据接口，定义了JDBC操作的元数据规范
import org.apache.calcite.avatica.MetaImpl; // 导入Avatica元数据实现基类，提供元数据操作的默认实现
import org.apache.calcite.avatica.NoSuchStatementException; // 导入找不到语句异常，当访问不存在的语句时抛出
import org.apache.calcite.avatica.UnregisteredDriver; // 导入未注册驱动类，Avatica驱动的基类
import org.apache.calcite.avatica.remote.TypedValue; // 导入类型化值类，用于远程调用时的参数传递
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置接口，定义连接的配置参数
import org.apache.calcite.config.CalciteConnectionConfigImpl; // 导入Calcite连接配置实现类，提供配置参数的具体实现
import org.apache.calcite.jdbc.CalcitePrepare.Context; // 导入Calcite准备上下文接口，用于SQL准备阶段的上下文信息
import org.apache.calcite.linq4j.BaseQueryable; // 导入LINQ4J基础可查询类，提供LINQ查询的基础实现
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可以进行枚举操作的数据集合
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历数据集合
import org.apache.calcite.linq4j.Ord; // 导入有序元素包装类，为元素添加索引信息
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于执行LINQ查询
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可以进行查询操作的数据源
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于表示LINQ表达式树
import org.apache.calcite.linq4j.tree.Expressions; // 导入表达式工具类，用于创建和操作表达式
import org.apache.calcite.materialize.Lattice; // 导入立方体类，用于物化视图的立方体结构
import org.apache.calcite.materialize.MaterializationService; // 导入物化服务类，管理物化视图的创建和使用
import org.apache.calcite.plan.RelOptUtil; // 导入关系表达式优化工具类，提供关系代数操作的辅助方法
import org.apache.calcite.prepare.CalciteCatalogReader; // 导入Calcite目录读取器，用于读取元数据信息
import org.apache.calcite.rel.type.DelegatingTypeSystem; // 导入委托类型系统类，用于包装和修改类型系统行为
import org.apache.calcite.rel.type.RelDataTypeSystem; // 导入关系数据类型系统接口，定义SQL类型系统
import org.apache.calcite.rel.type.TimeFrameSet; // 导入时间框架集合类，用于时间相关的类型转换
import org.apache.calcite.rel.type.TimeFrames; // 导入时间框架类，定义时间相关的类型转换规则
import org.apache.calcite.runtime.Hook; // 导入Hook类，允许在特定执行点插入自定义逻辑
import org.apache.calcite.schema.SchemaPlus; // 导入Schema增强接口，提供Schema的扩展功能
import org.apache.calcite.schema.SchemaVersion; // 导入Schema版本接口，用于Schema的版本控制
import org.apache.calcite.schema.Schemas; // 导入Schema工具类，提供Schema操作的辅助方法
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象Schema类，所有Schema实现的基础类
import org.apache.calcite.schema.impl.LongSchemaVersion; // 导入长整型Schema版本实现，使用时间戳作为版本号
import org.apache.calcite.schema.impl.ViewTable; // 导入视图表类，用于表示逻辑视图
import org.apache.calcite.server.CalciteServer; // 导入Calcite服务器接口，管理服务器端的语句执行
import org.apache.calcite.server.CalciteServerStatement; // 导入Calcite服务器语句接口，表示服务器端的语句对象
import org.apache.calcite.sql.advise.SqlAdvisor; // 导入SQL建议器，提供SQL补全和错误提示功能
import org.apache.calcite.sql.advise.SqlAdvisorValidator; // 导入SQL建议验证器，用于SQL建议的验证
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表，定义所有SQL标准操作符
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器，用于将SQL字符串解析为语法树
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口，用于验证SQL语法和语义
import org.apache.calcite.sql.validate.SqlValidatorWithHints; // 导入带提示的SQL验证器接口，提供验证和建议功能
import org.apache.calcite.tools.RelRunner; // 导入关系表达式运行器接口，用于直接执行RelNode
import org.apache.calcite.util.BuiltInMethod; // 导入内置方法类，定义Calcite的内置方法
import org.apache.calcite.util.Holder; // 导入持有者类，用于包装可变对象
import org.apache.calcite.util.Util; // 导入工具类，提供通用的静态方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，提供线程安全的列表实现
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类，提供线程安全的Map实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的参数或返回值

import java.lang.reflect.Type; // 导入Java类型类，用于反射获取类型信息
import java.sql.ResultSet; // 导入JDBC结果集接口，表示查询结果
import java.sql.SQLException; // 导入JDBC异常类，所有JDBC操作的异常基类
import java.util.HashMap; // 导入HashMap类，提供键值对存储
import java.util.Iterator; // 导入迭代器接口，用于遍历集合
import java.util.LinkedHashMap; // 导入LinkedHashMap类，保持插入顺序的Map实现
import java.util.List; // 导入List接口，表示有序集合
import java.util.Locale; // 导入Locale类，表示特定的地理、政治和文化区域
import java.util.Map; // 导入Map接口，表示键值对映射
import java.util.Properties; // 导入Properties类，用于存储配置属性
import java.util.TimeZone; // 导入时区类，表示时区信息
import java.util.concurrent.atomic.AtomicBoolean; // 导入原子布尔类，提供线程安全的布尔操作
import java.util.function.Supplier; // 导入Supplier函数式接口，用于延迟计算

import static com.google.common.base.Preconditions.checkArgument; // 导入参数检查工具，用于验证方法参数

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入非空转换工具，用于消除空值警告

import static java.util.Objects.requireNonNull; // 导入对象检查工具，用于确保对象不为null

/**
 * Implementation of JDBC connection
 * in the Calcite engine.
 * Calcite引擎中JDBC连接的实现类
 *
 * <p>Abstract to allow newer versions of JDBC to add methods.
 * 抽象类，允许新版本的JDBC添加方法而不破坏兼容性
 */
abstract class CalciteConnectionImpl // 定义抽象类CalciteConnectionImpl，是Calcite JDBC连接的核心实现
    extends AvaticaConnection // 继承自AvaticaConnection，获得基础的JDBC连接功能
    implements CalciteConnection, QueryProvider { // 实现CalciteConnection和QueryProvider接口，提供Calcite特有的连接和查询功能
  public final JavaTypeFactory typeFactory; // 类型工厂，用于在Java类型和SQL类型之间进行转换，final表示初始化后不可修改

  final CalciteSchema rootSchema; // 根Schema，包含所有子Schema和表的元数据，是整个Schema树的根节点
  final Supplier<CalcitePrepare> prepareFactory; // SQL准备工厂，Supplier函数式接口用于延迟创建CalcitePrepare实例
  final CalciteServer server = new CalciteServerImpl(); // Calcite服务器实例，用于管理服务器端的语句执行和状态

  // must be package-protected
  // 必须是包保护的，不能为public
  static final Trojan TROJAN = createTrojan(); // 特洛伊对象，用于访问AvaticaStatement的私有参数值，通过反射获取参数

  /**
   * Creates a CalciteConnectionImpl.
   * 创建CalciteConnectionImpl实例
   *
   * <p>Not public; method is called only from the driver.
   * 非公共方法，仅由驱动程序调用
   *
   * @param driver Driver // 驱动程序实例，通常是Driver类
   * @param factory Factory for JDBC objects // JDBC对象工厂，用于创建连接、语句等JDBC对象
   * @param url Server URL // 服务器URL，连接字符串
   * @param info Other connection properties // 其他连接属性，包含配置参数
   * @param rootSchema Root schema, or null // 根Schema，可为null（如果为null则创建默认根Schema）
   * @param typeFactory Type factory, or null // 类型工厂，可为null（如果为null则根据配置创建）
   */
  protected CalciteConnectionImpl(Driver driver, AvaticaFactory factory, // 构造方法，使用protected修饰，仅允许Driver类创建实例
      String url, Properties info, @Nullable CalciteSchema rootSchema, // 接收URL、连接属性、根Schema和类型工厂参数
      @Nullable JavaTypeFactory typeFactory) { // 类型工厂参数，可为null
    super(driver, factory, url, info); // 调用父类AvaticaConnection的构造方法，初始化基础的JDBC连接
    CalciteConnectionConfig cfg = new CalciteConnectionConfigImpl(info); // 从连接属性创建Calcite连接配置对象
    this.prepareFactory = driver::createPrepare; // 创建SQL准备工厂，使用Driver的createPrepare方法
    if (typeFactory != null) { // 如果提供了类型工厂
      this.typeFactory = typeFactory; // 直接使用提供的类型工厂
    } else { // 如果没有提供类型工厂
      RelDataTypeSystem typeSystem = // 从配置中获取类型系统，如果没有配置则使用默认类型系统
          cfg.typeSystem(RelDataTypeSystem.class, RelDataTypeSystem.DEFAULT);
      if (cfg.conformance().shouldConvertRaggedUnionTypesToVarying()) { // 如果配置要求将不规则联合类型转换为可变类型
        typeSystem = // 创建委托类型系统，包装原始类型系统并修改其行为
            new DelegatingTypeSystem(typeSystem) { // 匿名内部类，继承DelegatingTypeSystem
              @Override public boolean // 重写shouldConvertRaggedUnionTypesToVarying方法
              shouldConvertRaggedUnionTypesToVarying() { // 定义是否将不规则联合类型转换为可变类型
                return true; // 返回true，表示启用转换
              }
            };
      }
      this.typeFactory = new JavaTypeFactoryImpl(typeSystem); // 使用类型系统创建Java类型工厂实现
    }
    this.rootSchema = // 设置根Schema，如果提供了rootSchema则使用，否则创建新的根Schema
        requireNonNull(rootSchema != null // 使用requireNonNull确保rootSchema不为null
            ? rootSchema // 如果提供了rootSchema则使用它
            : CalciteSchema.createRootSchema(true)); // 否则创建新的根Schema，true表示启用缓存
    // Add dual table metadata when isSupportedDualTable return true
    // 当支持Dual表时，添加Dual表元数据
    if (cfg.conformance().isSupportedDualTable()) { // 如果配置支持Dual表（Oracle风格的单行表）
      SchemaPlus schemaPlus = this.rootSchema.plus(); // 获取SchemaPlus对象，提供Schema的扩展功能
      // Dual table contains one row with a value X
      // Dual表包含一行，值为X
      schemaPlus.add( // 向Schema中添加表
          "DUAL", ViewTable.viewMacro(schemaPlus, "VALUES ('X')", // 添加名为"DUAL"的视图表，使用VALUES ('X')创建单行表
          ImmutableList.of(), null, false)); // 参数列表为空，无参数，非临时表
    }
    checkArgument(this.rootSchema.isRoot(), "must be root schema"); // 验证rootSchema确实是根Schema，否则抛出异常
    this.properties.put(InternalProperty.CASE_SENSITIVE, cfg.caseSensitive()); // 设置大小写敏感属性到连接属性中
    this.properties.put(InternalProperty.UNQUOTED_CASING, cfg.unquotedCasing()); // 设置未加引号的标识符大小写规则
    this.properties.put(InternalProperty.QUOTED_CASING, cfg.quotedCasing()); // 设置加引号的标识符大小写规则
    this.properties.put(InternalProperty.QUOTING, cfg.quoting()); // 设置引号风格（单引号、双引号等）
  }

  CalciteMetaImpl meta() { // 获取Calcite元数据实现
    return (CalciteMetaImpl) meta; // 将父类的meta对象强制转换为CalciteMetaImpl类型并返回
  }

  @Override public CalciteConnectionConfig config() { // 重写config方法，返回连接配置
    return new CalciteConnectionConfigImpl(info); // 从连接属性info创建并返回Calcite连接配置对象
  }

  @Override public Context createPrepareContext() { // 重写createPrepareContext方法，创建SQL准备上下文
    return new ContextImpl(this); // 创建并返回ContextImpl实例，传入当前连接对象
  }

  /** Called after the constructor has completed and the model has been
   * loaded.
   * 在构造函数完成且模型加载后调用
   */
  void init() { // 初始化方法，用于设置物化视图等初始化操作
    final MaterializationService service = MaterializationService.instance(); // 获取物化服务单例实例
    for (CalciteSchema.LatticeEntry e : Schemas.getLatticeEntries(rootSchema)) { // 遍历根Schema中的所有立方体条目
      final Lattice lattice = e.getLattice(); // 获取立方体对象，立方体定义了物化视图的结构
      for (Lattice.Tile tile : lattice.computeTiles()) { // 计算立方体的所有瓦片（瓦片是物化视图的分区）
        service.defineTile(lattice, tile.bitSet(), tile.measures, e.schema, // 在物化服务中定义瓦片，包含立方体、维度、度量等信息
            true, true); // true表示强制创建，true表示使用缓存
      }
    }
  }

  @Override public <T> T unwrap(Class<T> iface) throws SQLException { // 重写unwrap方法，用于解包为特定接口
    if (iface == RelRunner.class) { // 如果请求解包为RelRunner接口（用于直接执行RelNode）
      return iface.cast((RelRunner) rel -> // 将lambda表达式转换为RelRunner接口
          prepareStatement_(CalcitePrepare.Query.of(rel), // 创建RelNode查询对象
              ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, // 结果集类型为仅向前，并发模式为只读
              getHoldability())); // 获取结果集的可保持性
    }
    return super.unwrap(iface); // 如果不是RelRunner，调用父类的unwrap方法处理
  }

  @Override public CalciteStatement createStatement(int resultSetType, // 重写createStatement方法，创建普通SQL语句
      int resultSetConcurrency, int resultSetHoldability) throws SQLException { // 接收结果集类型、并发模式和可保持性参数
    return (CalciteStatement) super.createStatement(resultSetType, // 调用父类方法创建语句并强制转换为CalciteStatement
        resultSetConcurrency, resultSetHoldability);
  }

  @Override public CalcitePreparedStatement prepareStatement( // 重写prepareStatement方法，创建预处理语句
      String sql, // SQL字符串
      int resultSetType, // 结果集类型
      int resultSetConcurrency, // 结果集并发模式
      int resultSetHoldability) throws SQLException { // 结果集可保持性
    final CalcitePrepare.Query<Object> query = CalcitePrepare.Query.of(sql); // 从SQL字符串创建查询对象
    return prepareStatement_(query, resultSetType, resultSetConcurrency, // 调用内部方法创建预处理语句
        resultSetHoldability);
  }

  private CalcitePreparedStatement prepareStatement_( // 私有方法，创建预处理语句的内部实现
      CalcitePrepare.Query<?> query, // 查询对象，可以是SQL或RelNode
      int resultSetType, // 结果集类型
      int resultSetConcurrency, // 结果集并发模式
      int resultSetHoldability) throws SQLException { // 结果集可保持性
    try { // 开始try块，捕获可能的异常
      final Meta.Signature signature = // 解析查询，获取查询签名（包含列信息、参数类型等）
          parseQuery(query, createPrepareContext(), -1); // -1表示不限制最大行数
      final CalcitePreparedStatement calcitePreparedStatement = // 使用工厂创建预处理语句
          (CalcitePreparedStatement) factory.newPreparedStatement(this, null, // 传入连接、null（表示无参数）、签名和结果集配置
              signature, resultSetType, resultSetConcurrency, resultSetHoldability);
      server.getStatement(calcitePreparedStatement.handle).setSignature(signature); // 在服务器端设置语句的签名
      return calcitePreparedStatement; // 返回创建的预处理语句
    } catch (Exception e) { // 捕获所有异常
      String message = query.rel == null // 根据查询类型构建错误消息
          ? "Error while preparing statement [" + query.sql + "]" // 如果是SQL查询，显示SQL语句
          : "Error while preparing plan [" + RelOptUtil.toString(query.rel) + "]"; // 如果是RelNode，显示关系表达式
      throw Helper.INSTANCE.createException(message, e); // 创建并抛出SQL异常，包含错误消息和原始异常
    }
  }

  <T> CalcitePrepare.CalciteSignature<T> parseQuery( // 泛型方法，解析查询并返回Calcite签名
      CalcitePrepare.Query<T> query, // 查询对象
      CalcitePrepare.Context prepareContext, long maxRowCount) { // 准备上下文和最大行数限制
    CalcitePrepare.Dummy.push(prepareContext); // 将准备上下文压入Dummy栈，用于线程局部变量管理
    try { // 开始try块
      final CalcitePrepare prepare = prepareFactory.get(); // 从工厂获取CalcitePrepare实例
      return prepare.prepareSql(prepareContext, query, Object[].class, // 准备SQL，返回签名，参数类型为Object数组
          maxRowCount); // 传入最大行数限制
    } finally { // finally块确保资源清理
      CalcitePrepare.Dummy.pop(prepareContext); // 从Dummy栈中弹出准备上下文
    }
  }

  @Override public AtomicBoolean getCancelFlag(Meta.StatementHandle handle) // 重写getCancelFlag方法，获取语句的取消标志
      throws NoSuchStatementException { // 可能抛出找不到语句异常
    final CalciteServerStatement serverStatement = server.getStatement(handle); // 从服务器获取语句对象
    return ((CalciteServerStatementImpl) serverStatement).cancelFlag; // 返回语句的原子布尔取消标志
  }

  // CalciteConnection methods
  // CalciteConnection接口方法

  @Override public SchemaPlus getRootSchema() { // 重写getRootSchema方法，获取根Schema
    return rootSchema.plus(); // 返回SchemaPlus对象，提供Schema的扩展功能
  }

  @Override public JavaTypeFactory getTypeFactory() { // 重写getTypeFactory方法，获取类型工厂
    return typeFactory; // 返回类型工厂实例
  }

  @Override public Properties getProperties() { // 重写getProperties方法，获取连接属性
    return info; // 返回连接属性对象
  }

  // QueryProvider methods
  // QueryProvider接口方法

  @Override public <T> Queryable<T> createQuery( // 重写createQuery方法，创建可查询对象（使用Class类型）
      Expression expression, Class<T> rowType) { // 表达式和行类型（使用Class）
    return new CalciteQueryable<>(this, rowType, expression); // 创建并返回CalciteQueryable实例
  }

  @Override public <T> Queryable<T> createQuery(Expression expression, Type rowType) { // 重写createQuery方法，创建可查询对象（使用Type类型）
    return new CalciteQueryable<>(this, rowType, expression); // 创建并返回CalciteQueryable实例
  }

  @Override public <T> T execute(Expression expression, Type type) { // 重写execute方法，执行表达式（使用Type类型）
    return castNonNull(null); // TODO: 当前返回null，待实现
  }

  @Override public <T> T execute(Expression expression, Class<T> type) { // 重写execute方法，执行表达式（使用Class类型）
    return castNonNull(null); // TODO: 当前返回null，待实现
  }

  @Override public <T> Enumerator<T> executeQuery(Queryable<T> queryable) { // 重写executeQuery方法，执行可查询对象
    try { // 开始try块
      CalciteStatement statement = (CalciteStatement) createStatement(); // 创建普通语句对象
      CalcitePrepare.CalciteSignature<T> signature = // 准备可查询对象，获取签名
          statement.prepare(queryable);
      return enumerable(statement.handle, signature, null).enumerator(); // 获取可枚举对象并返回其枚举器
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e); // 包装为运行时异常并抛出
    }
  }

  public <T> Enumerable<T> enumerable(Meta.StatementHandle handle, // 公共方法，根据语句句柄创建可枚举对象
      CalcitePrepare.CalciteSignature<T> signature, // 查询签名
      @Nullable List<TypedValue> parameterValues0) throws SQLException { // 参数值列表，可为null
    Map<String, Object> map = new LinkedHashMap<>(); // 创建LinkedHashMap存储参数，保持插入顺序
    AvaticaStatement statement = lookupStatement(handle); // 根据句柄查找语句对象
    final List<TypedValue> parameterValues; // 声明参数值列表
    if (parameterValues0 == null || parameterValues0.isEmpty()) { // 如果未提供参数值或参数值为空
      parameterValues = TROJAN.getParameterValues(statement); // 使用特洛伊对象从语句中获取参数值
    } else { // 如果提供了参数值
      parameterValues = parameterValues0; // 使用提供的参数值
    }

    if (MetaImpl.checkParameterValueHasNull(parameterValues)) { // 检查参数值中是否有未绑定的null值
      throw new SQLException("exception while executing query: unbound parameter"); // 抛出SQL异常，提示有未绑定的参数
    }

    Ord.forEach(parameterValues, // 遍历参数值列表，Ord.forEach提供索引
        (e, i) -> map.put("?" + i, e.toLocal())); // 将参数以"?0", "?1"等格式存入map，e.toLocal()转换为本地值
    map.putAll(signature.internalParameters); // 将签名中的内部参数也存入map
    final AtomicBoolean cancelFlag; // 声明取消标志
    try { // 开始try块
      cancelFlag = getCancelFlag(handle); // 获取语句的取消标志
    } catch (NoSuchStatementException e) { // 捕获找不到语句异常
      throw new RuntimeException(e); // 包装为运行时异常并抛出
    }
    map.put(DataContext.Variable.CANCEL_FLAG.camelName, cancelFlag); // 将取消标志存入map，用于查询执行过程中的取消检查
    int queryTimeout = statement.getQueryTimeout(); // 获取查询超时时间（秒）
    // Avoid overflow
    // 避免溢出
    if (queryTimeout > 0 && queryTimeout < Integer.MAX_VALUE / 1000) { // 如果超时时间有效且不会溢出
      map.put(DataContext.Variable.TIMEOUT.camelName, queryTimeout * 1000L); // 将超时时间转换为毫秒并存入map
    }
    final DataContext dataContext = createDataContext(map, signature.rootSchema); // 创建数据上下文，传入参数map和根Schema
    return signature.enumerable(dataContext); // 使用数据上下文创建可枚举对象并返回
  }

  public DataContext createDataContext(Map<String, Object> parameterValues, // 公共方法，创建数据上下文
      @Nullable CalciteSchema rootSchema) { // 根Schema，可为null
    if (config().spark()) { // 如果配置为Spark模式
      return DataContexts.EMPTY; // 返回空数据上下文（Spark有自己的执行环境）
    }
    return new DataContextImpl(this, parameterValues, rootSchema); // 创建并返回DataContextImpl实例
  }

  // do not make public
  // 不要设为public
  UnregisteredDriver getDriver() { // 获取驱动程序实例，包私有方法
    return driver; // 返回驱动程序
  }

  // do not make public
  // 不要设为public
  AvaticaFactory getFactory() { // 获取工厂实例，包私有方法
    return factory; // 返回工厂
  }

  /** Implementation of Queryable.
   * Queryable的实现
   *
   * @param <T> element type // 元素类型
   */
  static class CalciteQueryable<T> extends BaseQueryable<T> { // 静态内部类，实现Queryable接口，表示可查询的数据源
    CalciteQueryable(CalciteConnection connection, Type elementType, // 构造方法，接收连接、元素类型和表达式
        Expression expression) { // 表达式树，表示查询逻辑
      super(connection, elementType, expression); // 调用父类BaseQueryable的构造方法
    }

    public CalciteConnection getConnection() { // 获取连接方法
      return (CalciteConnection) provider; // 将provider（QueryProvider）强制转换为CalciteConnection并返回
    }
  }

  /** Implementation of Server.
   * Server的实现
   */
  private static class CalciteServerImpl implements CalciteServer { // 静态内部类，实现CalciteServer接口，管理服务器端的语句
    final Map<Integer, CalciteServerStatement> statementMap = new HashMap<>(); // 语句映射表，键为语句ID，值为语句对象

    @Override public void removeStatement(Meta.StatementHandle h) { // 重写removeStatement方法，移除语句
      statementMap.remove(h.id); // 从映射表中根据语句ID移除语句
    }

    @Override public void addStatement(CalciteConnection connection, // 重写addStatement方法，添加语句
        Meta.StatementHandle h) { // 语句句柄
      final CalciteConnectionImpl c = (CalciteConnectionImpl) connection; // 将连接强制转换为CalciteConnectionImpl
      final CalciteServerStatement previous = // 将新语句放入映射表，并获取之前的值（如果有）
          statementMap.put(h.id, new CalciteServerStatementImpl(c)); // 创建新的CalciteServerStatementImpl实例
      if (previous != null) { // 如果之前已存在该ID的语句
        throw new AssertionError(); // 抛出断言错误，表示不应该有重复的语句ID
      }
    }

    @Override public CalciteServerStatement getStatement(Meta.StatementHandle h) // 重写getStatement方法，获取语句
        throws NoSuchStatementException { // 可能抛出找不到语句异常
      CalciteServerStatement statement = statementMap.get(h.id); // 从映射表中根据ID获取语句
      if (statement == null) { // 如果语句不存在
        throw new NoSuchStatementException(h); // 抛出找不到语句异常
      }
      return statement; // 返回语句对象
    }
  }

  /** Schema that has no parents.
   * 没有父级的Schema
   */
  static class RootSchema extends AbstractSchema { // 静态内部类，继承AbstractSchema，表示根Schema
    RootSchema() { // 构造方法
      super(); // 调用父类AbstractSchema的构造方法
    }

    @Override public Expression getExpression(@Nullable SchemaPlus parentSchema, // 重写getExpression方法，获取表达式
        String name) { // Schema名称
      return Expressions.call( // 创建方法调用表达式
          DataContext.ROOT, // 调用DataContext.ROOT
          BuiltInMethod.DATA_CONTEXT_GET_ROOT_SCHEMA.method); // 调用getRootSchema方法
    }
  }

  /** Implementation of DataContext.
   * DataContext的实现
   */
  static class DataContextImpl implements DataContext { // 静态内部类，实现DataContext接口，提供查询执行时的上下文信息
    private final ImmutableMap<Object, Object> map; // 不可变Map，存储所有上下文变量
    private final @Nullable CalciteSchema rootSchema; // 根Schema，可为null
    private final QueryProvider queryProvider; // 查询提供者，用于执行LINQ查询
    private final JavaTypeFactory typeFactory; // 类型工厂，用于类型转换

    DataContextImpl(CalciteConnectionImpl connection, // 构造方法，接收连接、参数和根Schema
        Map<String, Object> parameters, @Nullable CalciteSchema rootSchema) { // 参数Map和根Schema
      this.queryProvider = connection; // 设置查询提供者为连接对象
      this.typeFactory = connection.getTypeFactory(); // 获取类型工厂
      this.rootSchema = rootSchema; // 设置根Schema

      // Store the time at which the query started executing. The SQL
      // standard says that functions such as CURRENT_TIMESTAMP return the
      // same value throughout the query.
      // 存储查询开始执行的时间。SQL标准规定CURRENT_TIMESTAMP等函数在整个查询过程中返回相同的值
      final Holder<Long> timeHolder = Holder.of(System.currentTimeMillis()); // 创建持有者，存储当前时间戳

      // Give a hook chance to alter the clock.
      // 给Hook机会修改时钟
      Hook.CURRENT_TIME.run(timeHolder); // 运行CURRENT_TIME Hook，允许修改时间
      final long time = timeHolder.get(); // 获取（可能被修改的）时间戳
      final TimeZone timeZone = connection.getTimeZone(); // 获取连接的时区
      final TimeFrameSet timeFrameSet = // 推导时间框架集，用于时间相关的类型转换
          connection.typeFactory.getTypeSystem()
              .deriveTimeFrameSet(TimeFrames.CORE); // 使用核心时间框架
      final long localOffset = timeZone.getOffset(time); // 计算本地时区偏移量（毫秒）
      final long currentOffset = localOffset; // 当前时区偏移量（与本地相同）
      final long sysOffset = TimeZone.getDefault().getOffset(time); // 系统默认时区偏移量
      final String user = "sa"; // 用户名，默认为"sa"（system administrator）
      final String systemUser = System.getProperty("user.name"); // 系统用户名，从系统属性获取
      final String localeName = connection.config().locale(); // 从配置获取区域设置名称
      final Locale locale = localeName != null // 解析区域设置
          ? Util.parseLocale(localeName) : Locale.ROOT; // 如果配置了则解析，否则使用ROOT区域

      // Give a hook chance to alter standard input, output, error streams.
      // 给Hook机会修改标准输入、输出、错误流
      final Holder<Object[]> streamHolder = // 创建持有者，存储标准流
          Holder.of(new Object[] {System.in, System.out, System.err}); // 初始化为系统标准流
      Hook.STANDARD_STREAMS.run(streamHolder); // 运行STANDARD_STREAMS Hook，允许修改标准流

      ImmutableMap.Builder<Object, Object> builder = ImmutableMap.builder(); // 创建不可变Map构建器
      builder.put(Variable.UTC_TIMESTAMP.camelName, time) // 添加UTC时间戳
          .put(Variable.CURRENT_TIMESTAMP.camelName, time + currentOffset) // 添加当前时间戳（带时区偏移）
          .put(Variable.LOCAL_TIMESTAMP.camelName, time + localOffset) // 添加本地时间戳
          .put(Variable.SYS_TIMESTAMP.camelName, time + sysOffset) // 添加系统时间戳
          .put(Variable.TIME_ZONE.camelName, timeZone) // 添加时区
          .put(Variable.TIME_FRAME_SET.camelName, timeFrameSet) // 添加时间框架集
          .put(Variable.USER.camelName, user) // 添加用户名
          .put(Variable.SYSTEM_USER.camelName, systemUser) // 添加系统用户名
          .put(Variable.LOCALE.camelName, locale) // 添加区域设置
          .put(Variable.STDIN.camelName, streamHolder.get()[0]) // 添加标准输入流
          .put(Variable.STDOUT.camelName, streamHolder.get()[1]) // 添加标准输出流
          .put(Variable.STDERR.camelName, streamHolder.get()[2]); // 添加标准错误流
      for (Map.Entry<String, Object> entry : parameters.entrySet()) { // 遍历参数Map
        Object e = entry.getValue(); // 获取参数值
        if (e == null) { // 如果值为null
          e = AvaticaSite.DUMMY_VALUE; // 替换为虚拟值，用于区分null和未设置
        }
        builder.put(entry.getKey(), e); // 将参数添加到Map中
      }
      map = builder.build(); // 构建不可变Map
    }

    @Override public synchronized @Nullable Object get(String name) { // 重写get方法，获取上下文变量（同步方法，线程安全）
      Object o = map.get(name); // 从Map中获取值
      if (o == AvaticaSite.DUMMY_VALUE) { // 如果是虚拟值
        return null; // 返回null
      }
      if (o == null && Variable.SQL_ADVISOR.camelName.equals(name)) { // 如果值为null且请求的是SQL建议器
        return getSqlAdvisor(); // 延迟创建并返回SQL建议器
      }
      return o; // 返回Map中的值
    }

    private SqlAdvisor getSqlAdvisor() { // 私有方法，获取SQL建议器
      final CalciteConnectionImpl con = (CalciteConnectionImpl) queryProvider; // 将查询提供者转换为连接
      final String schemaName; // 声明Schema名称
      try { // 开始try块
        schemaName = con.getSchema(); // 获取当前Schema名称
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 包装为运行时异常
      }
      final List<String> schemaPath = // 构建Schema路径
          schemaName == null // 如果Schema名称为null
              ? ImmutableList.of() // 返回空列表
              : ImmutableList.of(schemaName); // 否则返回包含Schema名称的列表
      final SqlValidatorWithHints validator = // 创建带提示的SQL验证器
          new SqlAdvisorValidator(SqlStdOperatorTable.instance(), // 使用标准操作符表
              new CalciteCatalogReader(requireNonNull(rootSchema, "rootSchema"), // 创建目录读取器，使用根Schema
                  schemaPath, typeFactory, con.config()), // 传入Schema路径、类型工厂和配置
              typeFactory, SqlValidator.Config.DEFAULT); // 传入类型工厂和默认验证器配置
      final CalciteConnectionConfig config = con.config(); // 获取连接配置
      // This duplicates org.apache.calcite.prepare.CalcitePrepareImpl.prepare2_
      // 这与CalcitePrepareImpl.prepare2_中的代码重复
      final SqlParser.Config parserConfig = SqlParser.config() // 创建SQL解析器配置
          .withQuotedCasing(config.quotedCasing()) // 设置加引号的标识符大小写规则
          .withUnquotedCasing(config.unquotedCasing()) // 设置未加引号的标识符大小写规则
          .withQuoting(config.quoting()) // 设置引号风格
          .withConformance(config.conformance()) // 设置SQL兼容性级别
          .withCaseSensitive(config.caseSensitive()); // 设置是否大小写敏感
      return new SqlAdvisor(validator, parserConfig); // 创建并返回SQL建议器
    }

    @Override public @Nullable SchemaPlus getRootSchema() { // 重写getRootSchema方法，获取根Schema
      return rootSchema == null ? null : rootSchema.plus(); // 如果根Schema为null返回null，否则返回SchemaPlus对象
    }

    @Override public JavaTypeFactory getTypeFactory() { // 重写getTypeFactory方法，获取类型工厂
      return typeFactory; // 返回类型工厂
    }

    @Override public QueryProvider getQueryProvider() { // 重写getQueryProvider方法，获取查询提供者
      return queryProvider; // 返回查询提供者
    }
  }

  /** Implementation of Context.
   * Context的实现
   */
  static class ContextImpl implements CalcitePrepare.Context { // 静态内部类，实现CalcitePrepare.Context接口，提供SQL准备上下文
    private final CalciteConnectionImpl connection; // 连接对象
    private final CalciteSchema mutableRootSchema; // 可变的根Schema（实际Schema）
    private final CalciteSchema rootSchema; // 根Schema快照（不可变，用于事务隔离）

    ContextImpl(CalciteConnectionImpl connection) { // 构造方法，接收连接对象
      this.connection = requireNonNull(connection, "connection"); // 设置连接，确保不为null
      long now = System.currentTimeMillis(); // 获取当前时间戳
      SchemaVersion schemaVersion = new LongSchemaVersion(now); // 使用时间戳创建Schema版本
      this.mutableRootSchema = connection.rootSchema; // 设置可变根Schema为连接的根Schema
      this.rootSchema = mutableRootSchema.createSnapshot(schemaVersion); // 创建根Schema快照，用于事务隔离
    }

    @Override public JavaTypeFactory getTypeFactory() { // 重写getTypeFactory方法，获取类型工厂
      return connection.typeFactory; // 返回连接的类型工厂
    }

    @Override public CalciteSchema getRootSchema() { // 重写getRootSchema方法，获取根Schema
      return rootSchema; // 返回根Schema快照
    }

    @Override public CalciteSchema getMutableRootSchema() { // 重写getMutableRootSchema方法，获取可变根Schema
      return mutableRootSchema; // 返回可变根Schema
    }

    @Override public List<String> getDefaultSchemaPath() { // 重写getDefaultSchemaPath方法，获取默认Schema路径
      final String schemaName; // 声明Schema名称
      try { // 开始try块
        schemaName = connection.getSchema(); // 获取连接的当前Schema名称
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 包装为运行时异常
      }
      return schemaName == null // 如果Schema名称为null
          ? ImmutableList.of() // 返回空列表
          : ImmutableList.of(schemaName); // 否则返回包含Schema名称的列表
    }

    @Override public @Nullable List<String> getObjectPath() { // 重写getObjectPath方法，获取对象路径
      return null; // 返回null，表示没有特定对象路径
    }

    @Override public CalciteConnectionConfig config() { // 重写config方法，获取连接配置
      return connection.config(); // 返回连接的配置
    }

    @Override public DataContext getDataContext() { // 重写getDataContext方法，获取数据上下文
      return connection.createDataContext(ImmutableMap.of(), // 创建数据上下文，参数Map为空
          rootSchema); // 使用根Schema快照
    }

    @Override public RelRunner getRelRunner() { // 重写getRelRunner方法，获取关系表达式运行器
      final RelRunner runner; // 声明运行器
      try { // 开始try块
        runner = connection.unwrap(RelRunner.class); // 从连接解包获取RelRunner
      } catch (SQLException e) { // 捕获SQL异常
        throw new RuntimeException(e); // 包装为运行时异常
      }
      if (runner == null) { // 如果运行器为null
        throw new UnsupportedOperationException(); // 抛出不支持操作异常
      }
      return runner; // 返回运行器
    }

    @Override public CalcitePrepare.SparkHandler spark() { // 重写spark方法，获取Spark处理器
      final boolean enable = config().spark(); // 从配置获取是否启用Spark
      return CalcitePrepare.Dummy.getSparkHandler(enable); // 返回Spark处理器（如果启用则返回实际处理器，否则返回虚拟处理器）
    }
  }

  /** Implementation of {@link CalciteServerStatement}.
   * CalciteServerStatement的实现
   */
  static class CalciteServerStatementImpl // 静态内部类，实现CalciteServerStatement接口，表示服务器端的语句
      implements CalciteServerStatement {
    private final CalciteConnectionImpl connection; // 连接对象
    private @Nullable Iterator<Object> iterator; // 结果集迭代器，可为null
    private Meta.@Nullable Signature signature; // 查询签名，可为null
    private final AtomicBoolean cancelFlag = new AtomicBoolean(); // 原子布尔取消标志，用于取消语句执行

    CalciteServerStatementImpl(CalciteConnectionImpl connection) { // 构造方法，接收连接对象
      this.connection = requireNonNull(connection, "connection"); // 设置连接，确保不为null
    }

    @Override public Context createPrepareContext() { // 重写createPrepareContext方法，创建准备上下文
      return connection.createPrepareContext(); // 使用连接创建准备上下文
    }

    @Override public CalciteConnection getConnection() { // 重写getConnection方法，获取连接
      return connection; // 返回连接对象
    }

    @Override public void setSignature(Meta.Signature signature) { // 重写setSignature方法，设置查询签名
      this.signature = signature; // 设置签名
    }

    @Override public Meta.@Nullable Signature getSignature() { // 重写getSignature方法，获取查询签名
      return signature; // 返回签名
    }

    @Override public @Nullable Iterator<Object> getResultSet() { // 重写getResultSet方法，获取结果集迭代器
      return iterator; // 返回迭代器
    }

    @Override public void setResultSet(Iterator<Object> iterator) { // 重写setResultSet方法，设置结果集迭代器
      this.iterator = iterator; // 设置迭代器
    }
  }
}