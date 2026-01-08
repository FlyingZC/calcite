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
package org.apache.calcite.jdbc; // 包声明:Calcite JDBC驱动程序所在的包

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂,用于将Java类型映射到Calcite类型系统
import org.apache.calcite.adapter.jdbc.JdbcSchema; // 导入JDBC模式,用于将外部JDBC数据源包装成Calcite可识别的schema
import org.apache.calcite.avatica.AvaticaConnection; // 导入Avatica连接基类,Avatica是Calcite的JDBC框架基础
import org.apache.calcite.avatica.BuiltInConnectionProperty; // 导入内置连接属性枚举,定义了Avatica框架支持的通用连接属性
import org.apache.calcite.avatica.ConnectionProperty; // 导入连接属性接口,用于定义和管理连接配置参数
import org.apache.calcite.avatica.DriverVersion; // 导入驱动版本信息类,用于管理驱动程序的版本号和兼容性
import org.apache.calcite.avatica.Handler; // 导入处理器接口,负责处理连接初始化等生命周期事件
import org.apache.calcite.avatica.HandlerImpl; // 导入处理器实现类,提供处理器接口的默认实现
import org.apache.calcite.avatica.Meta; // 导入元数据接口,提供数据库元数据查询功能
import org.apache.calcite.avatica.UnregisteredDriver; // 导入未注册驱动基类,Calcite Driver继承此类以实现JDBC驱动
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置接口,封装Calcite特定的连接参数
import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性枚举,定义Calcite特有的连接配置项
import org.apache.calcite.linq4j.function.Function0; // 导入无参数函数接口,用于延迟初始化和工厂模式
import org.apache.calcite.model.JsonSchema; // 导入JSON模式类,用于从JSON配置定义schema结构
import org.apache.calcite.model.ModelHandler; // 导入模型处理器,负责解析和加载schema模型配置
import org.apache.calcite.schema.SchemaFactory; // 导入schema工厂接口,用于创建自定义schema实例
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象schema基类,提供schema的基本实现
import org.apache.calcite.util.JsonBuilder; // 导入JSON构建器,用于构建JSON格式的schema配置
import org.apache.calcite.util.Util; // 导入工具类,提供各种通用工具方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解,用于标记可能为null的参数和返回值

import java.io.IOException; // 导入IO异常类,处理文件读写等IO操作中的错误
import java.sql.SQLException; // 导入SQL异常类,处理数据库操作中的错误
import java.util.ArrayList; // 导入动态数组列表,用于存储可变长度的对象集合
import java.util.Collection; // 导入集合接口,定义集合的通用操作
import java.util.Collections; // 导入集合工具类,提供集合操作的各种静态方法
import java.util.List; // 导入列表接口,定义有序集合的操作
import java.util.Map; // 导入映射接口,定义键值对集合的操作
import java.util.Properties; // 导入属性类,用于管理配置参数的键值对集合
import java.util.function.Supplier; // 导入供应商接口,用于延迟获取对象实例

import static java.util.Objects.requireNonNull; // 静态导入requireNonNull方法,用于参数非空校验

/**
 * Calcite JDBC driver.
 * Calcite JDBC驱动类,实现了JDBC 4.1规范,允许应用程序通过标准JDBC API访问Calcite查询引擎
 * 该类继承自UnregisteredDriver,是Calcite框架的入口点,负责建立数据库连接、管理连接属性、创建元数据等核心功能
 * Driver类支持通过JDBC URL(jdbc:calcite:)连接到Calcite,可以配置各种数据源(如CSV、JSON、JDBC等)作为schema
 */
public class Driver extends UnregisteredDriver { // Driver类继承UnregisteredDriver,实现Calcite JDBC驱动
  public static final String CONNECT_STRING_PREFIX = "jdbc:calcite:"; // JDBC连接字符串前缀常量,所有Calcite连接URL必须以此前缀开头,例如:jdbc:calcite:model=model.json

  protected final @Nullable Supplier<CalcitePrepare> prepareFactory; // CalcitePrepare对象的供应商工厂,用于创建语句准备器,可为null表示使用默认工厂;CalcitePrepare负责将SQL语句转换为可执行的查询计划

  static { // 静态初始化块,在类加载时执行
    new Driver().register(); // 创建Driver实例并注册到JDBC DriverManager,使应用程序可以通过DriverManager.getConnection()获取连接
  }

  /** Creates a Driver. */ // 无参构造方法注释
  public Driver() { // 公共无参构造方法,创建默认的Driver实例
    this(null); // 调用带参数的构造方法,传入null表示使用默认的CalcitePrepare工厂
  }

  /** Creates a Driver with a factory for {@code CalcitePrepare} objects; // 创建Driver的构造方法,可指定CalcitePrepare对象工厂
   * if the factory is null, the driver will call // 如果工厂为null,驱动将调用CalcitePrepare的默认工厂
   * {@link CalcitePrepare#DEFAULT_FACTORY}. */ // 通过DEFAULT_FACTORY获取默认的语句准备器
  protected Driver(@Nullable Supplier<CalcitePrepare> prepareFactory) { // 受保护的构造方法,接受一个CalcitePrepare供应商作为参数
    this.prepareFactory = prepareFactory; // 将传入的工厂赋值给成员变量,如果为null则后续会使用默认工厂
  }

  /** Creates a copy of this Driver with a new factory for creating // 创建当前Driver的副本,使用新的CalcitePrepare工厂
   * {@link CalcitePrepare}. // 该方法允许用户更改工厂而无需继承Driver类
   *
   * <p>Allows users of the Driver to change the factory without subclassing // 允许Driver用户在不继承Driver的情况下更改工厂
   * the Driver. But subclasses of the driver should override this method to // 但Driver的子类应该重写此方法以创建子类实例
   * create an instance of their subclass. // 确保返回的是子类类型而不是父类类型
   *
   * @param prepareFactory Supplier of a {@code CalcitePrepare} // 参数:CalcitePrepare对象的供应商
   * @return Driver with the provided prepareFactory // 返回值:使用指定工厂的新Driver实例
   */
  public Driver withPrepareFactory(Supplier<CalcitePrepare> prepareFactory) { // 创建带有新工厂的Driver副本
    requireNonNull(prepareFactory, "prepareFactory"); // 校验prepareFactory参数不能为null,否则抛出NullPointerException
    if (this.prepareFactory == prepareFactory) { // 如果新工厂与当前工厂是同一个对象(引用相等)
      return this; // 直接返回当前Driver实例,无需创建新对象
    }
    return new Driver(prepareFactory); // 创建新的Driver实例,使用指定的工厂
  }

  /** Creates a {@link CalcitePrepare} to be used to prepare a statement for // 创建CalcitePrepare对象,用于准备SQL语句以便执行
   * execution. // CalcitePrepare负责将SQL语句转换为可执行的查询计划
   *
   * <p>If you wish to use a custom prepare, either override this method or // 如果希望使用自定义的准备器,可以重写此方法或
   * call {@link #withPrepareFactory(Supplier)}. */ // 调用withPrepareFactory方法设置自定义工厂
  public CalcitePrepare createPrepare() { // 创建语句准备器
    if (prepareFactory != null) { // 如果prepareFactory成员变量不为null(即指定了自定义工厂)
      return prepareFactory.get(); // 调用工厂的get()方法获取CalcitePrepare实例
    }
    return CalcitePrepare.DEFAULT_FACTORY.apply(); // 否则使用默认工厂创建CalcitePrepare实例
  }

  /** Returns a factory with which to create a {@link CalcitePrepare}. // 返回用于创建CalcitePrepare对象的工厂
   *
   * <p>Now deprecated; if you wish to use a custom prepare, please call // 该方法已废弃;如果希望使用自定义准备器,请调用
   * {@link #withPrepareFactory(Supplier)} // withPrepareFactory方法
   * or override {@link #createPrepare()}. */ // 或重写createPrepare方法
  @Deprecated // to be removed before 2.0 // 标记为已废弃,将在2.0版本前移除
  protected Function0<CalcitePrepare> createPrepareFactory() { // 创建CalcitePrepare的工厂方法(已废弃)
    return CalcitePrepare.DEFAULT_FACTORY; // 返回默认的CalcitePrepare工厂
  }

  @Override protected String getConnectStringPrefix() { // 重写父类方法:获取JDBC连接字符串前缀
    return CONNECT_STRING_PREFIX; // 返回连接字符串前缀常量"jdbc:calcite:"
  }

  @Override protected String getFactoryClassName(JdbcVersion jdbcVersion) { // 重写父类方法:根据JDBC版本获取工厂类名
    switch (jdbcVersion) { // 根据JDBC版本进行分支处理
    case JDBC_30: // JDBC 3.0版本
    case JDBC_40: // JDBC 4.0版本
      throw new IllegalArgumentException("JDBC version not supported: " // 抛出异常:Calcite不支持JDBC 3.0和4.0版本
          + jdbcVersion); // 在异常信息中包含不支持的版本号
    case JDBC_41: // JDBC 4.1版本
    default: // 默认情况(包括更高版本)
      return "org.apache.calcite.jdbc.CalciteJdbc41Factory"; // 返回JDBC 4.1工厂类的完整类名
    }
  }

  @Override protected DriverVersion createDriverVersion() { // 重写父类方法:创建驱动版本信息对象
    return CalciteDriverVersion.INSTANCE; // 返回Calcite驱动版本单例实例,包含版本号、名称等信息
  }

  @Override protected Handler createHandler() { // 重写父类方法:创建连接处理器,负责处理连接生命周期事件
    return new HandlerImpl() { // 返回HandlerImpl的匿名子类实例
      @Override public void onConnectionInit(AvaticaConnection connection_) // 重写方法:连接初始化时调用
          throws SQLException { // 声明可能抛出SQL异常
        final CalciteConnectionImpl connection = // 将AvaticaConnection强转为CalciteConnectionImpl类型
            (CalciteConnectionImpl) connection_; // connection_是父类类型的连接对象
        super.onConnectionInit(connection); // 调用父类的连接初始化方法,执行基础初始化逻辑
        final String model = model(connection); // 调用model方法获取schema模型配置(可能为null)
        if (model != null) { // 如果模型配置不为null(即指定了模型文件或内联模型)
          try { // 开始try块,处理可能的IO异常
            ModelHandler h = new ModelHandler(connection.getRootSchema(), model); // 创建模型处理器,解析模型配置并加载到根schema中
            String defaultName = h.defaultSchemaName(); // 从模型处理器获取默认schema名称
            if (defaultName != null) { // 如果默认schema名称不为null
              connection.setSchema(defaultName); // 设置连接的当前schema为默认schema
            }
          } catch (IOException e) { // 捕获IO异常(模型文件读取失败等)
            throw new SQLException(e); // 将IO异常包装为SQL异常并抛出
          }
        }
        connection.init(); // 调用连接的init方法,完成连接的最终初始化
      }

      @Nullable String model(CalciteConnectionImpl connection) { // 内部方法:获取schema模型配置,可能返回null
        String model = connection.config().model(); // 从连接配置中获取模型属性(可以是文件路径或内联JSON)
        if (model != null) { // 如果配置中已指定模型
          return model; // 直接返回该模型配置字符串
        }
        SchemaFactory schemaFactory = // 从连接配置中获取schema工厂(自定义schema创建器)
            connection.config().schemaFactory(SchemaFactory.class, null); // 如果未配置则返回null
        final Properties info = connection.getProperties(); // 获取连接的所有属性信息
        final String schemaName = Util.first(connection.config().schema(), "adhoc"); // 获取schema名称,如果未配置则使用默认值"adhoc"
        if (schemaFactory == null) { // 如果未指定自定义schema工厂
          final JsonSchema.Type schemaType = connection.config().schemaType(); // 从配置获取schema类型(JDBC/MAP等)
          if (schemaType != null) { // 如果配置了schema类型
            switch (schemaType) { // 根据schema类型选择对应的工厂
            case JDBC: // JDBC类型schema
              schemaFactory = JdbcSchema.Factory.INSTANCE; // 使用JDBC schema工厂,用于包装外部JDBC数据源
              break;
            case MAP: // MAP类型schema
              schemaFactory = AbstractSchema.Factory.INSTANCE; // 使用抽象schema工厂,创建内存map类型的schema
              break;
            default: // 其他类型
              break; // 不做处理
            }
          }
        }
        if (schemaFactory != null) { // 如果存在schema工厂(无论是自定义的还是根据类型选择的)
          final JsonBuilder json = new JsonBuilder(); // 创建JSON构建器,用于构建内联的schema配置
          final Map<String, @Nullable Object> root = json.map(); // 创建根map对象,表示JSON的根节点
          root.put("version", "1.0"); // 设置模型版本号为1.0
          root.put("defaultSchema", schemaName); // 设置默认schema名称
          final List<@Nullable Object> schemaList = json.list(); // 创建schema列表,用于存放所有schema定义
          root.put("schemas", schemaList); // 将schema列表添加到根map中
          final Map<String, @Nullable Object> schema = json.map(); // 创建单个schema的map对象
          schemaList.add(schema); // 将该schema添加到schema列表中
          schema.put("type", "custom"); // 设置schema类型为custom(自定义类型)
          schema.put("name", schemaName); // 设置schema名称
          schema.put("factory", schemaFactory.getClass().getName()); // 设置schema工厂的完整类名
          final Map<String, @Nullable Object> operandMap = json.map(); // 创建操作数map,用于存放工厂的配置参数
          schema.put("operand", operandMap); // 将操作数map添加到schema配置中
          for (Map.Entry<String, String> entry : Util.toMap(info).entrySet()) { // 遍历连接属性的所有键值对
            if (entry.getKey().startsWith("schema.")) { // 如果属性名以"schema."开头(表示schema配置参数)
              operandMap.put(entry.getKey().substring("schema.".length()), // 去掉"schema."前缀后作为操作数的键
                  entry.getValue()); // 属性值作为操作数的值
            }
          }
          return "inline:" + json.toJsonString(root); // 将构建的JSON对象转换为字符串,添加"inline:"前缀表示内联模型
        }
        return null; // 如果没有模型配置,返回null
      }
    };
  }

  @Override protected Collection<ConnectionProperty> getConnectionProperties() { // 重写父类方法:获取所有支持的连接属性集合
    final List<ConnectionProperty> list = new ArrayList<>(); // 创建动态列表用于存储连接属性
    Collections.addAll(list, BuiltInConnectionProperty.values()); // 将Avatica内置连接属性添加到列表中(如user、password等)
    Collections.addAll(list, CalciteConnectionProperty.values()); // 将Calcite特有的连接属性添加到列表中(如model、schema等)
    return list; // 返回连接属性列表,供连接配置使用
  }

  @Override public Meta createMeta(AvaticaConnection connection) { // 重写父类方法:创建元数据对象,用于查询数据库元数据
    final CalciteConnectionConfig config = // 将连接配置强转为CalciteConnectionConfig类型
        (CalciteConnectionConfig) connection.config(); // 获取连接的配置对象
    CalciteMetaTableFactory metaTableFactory = // 从配置获取表元数据工厂,用于创建表元数据对象
        config.metaTableFactory(CalciteMetaTableFactory.class, // 指定期望的工厂类型
            CalciteMetaTableFactoryImpl.INSTANCE); // 如果未配置则使用默认实现
    CalciteMetaColumnFactory metaColumnFactory = // 从配置获取列元数据工厂,用于创建列元数据对象
        config.metaColumnFactory(CalciteMetaColumnFactory.class, // 指定期望的工厂类型
            CalciteMetaColumnFactoryImpl.INSTANCE); // 如果未配置则使用默认实现
    return CalciteMetaImpl.create((CalciteConnectionImpl) connection, // 创建并返回CalciteMetaImpl实例,传入连接和两个工厂
        metaTableFactory, metaColumnFactory); // CalciteMetaImpl负责提供数据库的元数据查询功能
  }

  /** Creates an internal connection. */ // 方法注释:创建内部连接,用于Calcite内部使用
  CalciteConnection connect(CalciteSchema rootSchema, // 参数:根schema对象,定义了数据库的schema层级结构
      @Nullable JavaTypeFactory typeFactory) { // 参数:Java类型工厂,可为null表示使用默认类型系统
    return (CalciteConnection) ((CalciteFactory) factory) // 通过工厂创建连接,将factory强转为CalciteFactory类型
        .newConnection(this, factory, CONNECT_STRING_PREFIX, new Properties(), // 调用工厂的newConnection方法创建连接,传入驱动、工厂、连接前缀、空属性、根schema和类型工厂
            rootSchema, typeFactory); // 返回CalciteConnection类型的连接对象
  }

  /** Creates an internal connection. */ // 方法注释:创建内部连接的另一个重载版本,支持传入自定义属性
  CalciteConnection connect(CalciteSchema rootSchema, // 参数:根schema对象,定义了数据库的schema层级结构
      @Nullable JavaTypeFactory typeFactory, Properties properties) { // 参数:Java类型工厂(可为null)和连接属性对象
    return (CalciteConnection) ((CalciteFactory) factory) // 通过工厂创建连接,将factory强转为CalciteFactory类型
        .newConnection(this, factory, CONNECT_STRING_PREFIX, properties, // 调用工厂的newConnection方法,传入驱动的所有配置参数
            rootSchema, typeFactory); // 返回CalciteConnection类型的连接对象,包含指定的schema和类型工厂
  }
}
