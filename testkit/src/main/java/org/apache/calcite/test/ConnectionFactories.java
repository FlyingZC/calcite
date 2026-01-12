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
package org.apache.calcite.test; // 指定包名，该文件属于 org.apache.calcite.test 包

import org.apache.calcite.avatica.ConnectionProperty; // 导入 Avatica 连接属性类，用于定义连接配置属性
import org.apache.calcite.jdbc.CalciteConnection; // 导入 Calcite 连接类，表示 Calcite 数据库连接
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型，用于定义数据类型
import org.apache.calcite.runtime.FlatLists; // 导入扁平列表工具类，用于创建不可变列表
import org.apache.calcite.schema.Schema; // 导入 Schema 接口，表示数据库模式
import org.apache.calcite.schema.SchemaPlus; // 导入 SchemaPlus 接口，扩展的 Schema 接口

import org.apache.commons.dbcp2.PoolableConnection; // 导入可池化连接类，支持连接池
import org.apache.commons.dbcp2.PoolableConnectionFactory; // 导入可池化连接工厂类，用于创建池化连接
import org.apache.commons.dbcp2.PoolingDataSource; // 导入池化数据源类，管理连接池
import org.apache.commons.pool2.impl.GenericObjectPool; // 导入通用对象池类，实现对象池功能

import com.google.common.collect.ImmutableList; // 导入不可变列表类，提供线程安全的列表
import com.google.common.collect.ImmutableMap; // 导入不可变映射类，提供线程安全的映射

import java.sql.Connection; // 导入 JDBC 连接接口，表示数据库连接
import java.sql.DriverManager; // 导入驱动管理器类，用于管理数据库驱动
import java.sql.SQLException; // 导入 SQL 异常类，表示数据库操作异常
import java.util.Map; // 导入 Map 接口，表示键值对集合
import java.util.Objects; // 导入 Objects 工具类，提供对象操作方法
import java.util.Properties; // 导入 Properties 类，表示属性集合

import static java.util.Objects.requireNonNull; // 导入 requireNonNull 静态方法，用于非空检查

/** Utilities for {@link ConnectionFactory} and
 * {@link org.apache.calcite.test.CalciteAssert.ConnectionPostProcessor}. */ // 类文档注释：提供 ConnectionFactory 和 ConnectionPostProcessor 的工具类，用于创建和管理数据库连接工厂
public abstract class ConnectionFactories { // 定义抽象工具类 ConnectionFactories，不能被实例化
  /** The empty connection factory. */ // 空连接工厂的注释
  private static final ConnectionFactory EMPTY = // 声明私有的静态常量 EMPTY，类型为 ConnectionFactory，表示空的连接工厂
      new MapConnectionFactory(ImmutableMap.of(), ImmutableList.of()); // 创建 MapConnectionFactory 实例，传入空的不可变映射和空的不可变列表，实现空连接工厂

  /** Prevent instantiation of utility class. */ // 防止工具类实例化的注释
  private ConnectionFactories() { // 私有构造方法，防止外部实例化该工具类
  } // 构造方法体为空

  /** Returns an empty connection factory. */ // 返回空连接工厂的方法注释
  public static ConnectionFactory empty() { // 声明公共静态方法 empty，返回类型为 ConnectionFactory，用于获取空的连接工厂
    return EMPTY; // 返回静态常量 EMPTY，即空的连接工厂实例
  } // 方法结束

  /** Creates a connection factory that uses a single pooled connection,
   * as opposed to creating a new connection on each invocation. */ // 创建池化连接工厂的方法注释，使用单个池化连接而不是每次调用都创建新连接
  public static ConnectionFactory pool(ConnectionFactory connectionFactory) { // 声明公共静态方法 pool，参数为 ConnectionFactory，返回池化的连接工厂
    return connectionFactory instanceof PoolingConnectionFactory // 检查传入的连接工厂是否已经是 PoolingConnectionFactory 实例
        ? connectionFactory // 如果是，直接返回该连接工厂
        : new PoolingConnectionFactory(connectionFactory); // 如果不是，创建新的 PoolingConnectionFactory 包装原连接工厂
  } // 方法结束

  /** Returns a post-processor that adds a {@link CalciteAssert.SchemaSpec}
   * (set of schemes) to a connection. */ // 返回添加 SchemaSpec 的后处理器的方法注释
  public static CalciteAssert.ConnectionPostProcessor add( // 声明公共静态方法 add，返回连接后处理器
      CalciteAssert.SchemaSpec schemaSpec) { // 参数为 SchemaSpec，表示要添加的模式规范
    return new AddSchemaSpecPostProcessor(schemaSpec); // 创建并返回 AddSchemaSpecPostProcessor 实例
  } // 方法结束

  /** Returns a post-processor that adds {@link Schema} and sets it as
   * default. */ // 返回添加 Schema 并设置为默认的后处理器的方法注释
  public static CalciteAssert.ConnectionPostProcessor add(String name, // 声明公共静态方法 add，返回连接后处理器，参数为模式名称
      Schema schema) { // 参数为 Schema 实例
    return new AddSchemaPostProcessor(name, schema); // 创建并返回 AddSchemaPostProcessor 实例
  } // 方法结束

  /** Returns a post-processor that sets a default schema name. */ // 返回设置默认模式名称的后处理器的方法注释
  public static CalciteAssert.ConnectionPostProcessor setDefault( // 声明公共静态方法 setDefault，返回连接后处理器
      String schema) { // 参数为模式名称字符串
    return new DefaultSchemaPostProcessor(schema); // 创建并返回 DefaultSchemaPostProcessor 实例
  } // 方法结束

  /** Returns a post-processor that adds a type. */ // 返回添加类型的后处理器的方法注释
  public static CalciteAssert.ConnectionPostProcessor addType(String name, // 声明公共静态方法 addType，返回连接后处理器，参数为类型名称
      RelProtoDataType protoDataType) { // 参数为关系原型数据类型
    return new AddTypePostProcessor(name, protoDataType); // 创建并返回 AddTypePostProcessor 实例
  } // 方法结束

  /** Connection factory that uses a given map of (name, value) pairs and
   * optionally an initial schema. */ // MapConnectionFactory 类文档注释：使用给定的（名称，值）对映射和可选的初始模式的连接工厂
  private static class MapConnectionFactory implements ConnectionFactory { // 定义私有静态内部类 MapConnectionFactory，实现 ConnectionFactory 接口
    private final ImmutableMap<String, String> map; // 声明私有的不可变映射成员变量，存储连接属性的键值对
    private final ImmutableList<CalciteAssert.ConnectionPostProcessor> postProcessors; // 声明私有的不可变列表成员变量，存储连接后处理器

    MapConnectionFactory(ImmutableMap<String, String> map, // 构造方法，参数为不可变映射
        ImmutableList<CalciteAssert.ConnectionPostProcessor> postProcessors) { // 参数为不可变的后处理器列表
      this.map = requireNonNull(map, "map"); // 初始化 map 成员变量，使用 requireNonNull 进行非空检查
      this.postProcessors = requireNonNull(postProcessors, "postProcessors"); // 初始化 postProcessors 成员变量，使用 requireNonNull 进行非空检查
    } // 构造方法结束

    @Override public boolean equals(Object obj) { // 重写 equals 方法，用于比较两个 MapConnectionFactory 实例是否相等
      return this == obj // 如果是同一个对象引用，返回 true
          || obj.getClass() == MapConnectionFactory.class // 如果对象类型相同
          && ((MapConnectionFactory) obj).map.equals(map) // 且 map 成员变量相等
          && ((MapConnectionFactory) obj).postProcessors.equals(postProcessors); // 且 postProcessors 成员变量相等，则返回 true
    } // equals 方法结束

    @Override public int hashCode() { // 重写 hashCode 方法，用于生成对象的哈希码
      return Objects.hash(map, postProcessors); // 使用 Objects.hash 方法基于 map 和 postProcessors 生成哈希码
    } // hashCode 方法结束

    @Override public Connection createConnection() throws SQLException { // 实现 ConnectionFactory 接口的 createConnection 方法，创建数据库连接
      final Properties info = new Properties(); // 创建 Properties 对象，用于存储连接属性
      for (Map.Entry<String, String> entry : map.entrySet()) { // 遍历 map 中的每个键值对
        info.setProperty(entry.getKey(), entry.getValue()); // 将键值对设置到 Properties 对象中
      } // 遍历结束
      Connection connection = // 声明 Connection 变量
          DriverManager.getConnection("jdbc:calcite:", info); // 使用 DriverManager 获取 Calcite 数据库连接，传入 JDBC URL 和属性
      for (CalciteAssert.ConnectionPostProcessor postProcessor : postProcessors) { // 遍历所有后处理器
        connection = postProcessor.apply(connection); // 依次应用每个后处理器处理连接
      } // 遍历结束
      return connection; // 返回处理后的连接
    } // createConnection 方法结束

    @Override public ConnectionFactory with(String property, Object value) { // 实现 ConnectionFactory 接口的 with 方法，添加属性
      return new MapConnectionFactory( // 创建新的 MapConnectionFactory 实例
          FlatLists.append(this.map, property, value.toString()), // 使用 FlatLists.append 将新属性添加到现有 map 中
          postProcessors); // 保持原有的后处理器列表
    } // with 方法结束

    @Override public ConnectionFactory with(ConnectionProperty property, Object value) { // 实现 ConnectionFactory 接口的 with 方法，添加连接属性
      if (!property.type().valid(value, property.valueClass())) { // 检查值是否与属性类型匹配
        throw new IllegalArgumentException(); // 如果不匹配，抛出非法参数异常
      } // 检查结束
      return with(property.camelName(), value.toString()); // 调用另一个 with 方法，使用属性的驼峰命名和字符串值
    } // with 方法结束

    @Override public ConnectionFactory with( // 实现 ConnectionFactory 接口的 with 方法，添加后处理器
        CalciteAssert.ConnectionPostProcessor postProcessor) { // 参数为连接后处理器
      ImmutableList.Builder<CalciteAssert.ConnectionPostProcessor> builder = // 创建不可变列表构建器
          ImmutableList.builder(); // 初始化构建器
      builder.addAll(postProcessors); // 添加所有现有的后处理器
      builder.add(postProcessor); // 添加新的后处理器
      return new MapConnectionFactory(map, builder.build()); // 创建新的 MapConnectionFactory 实例，使用新的后处理器列表
    } // with 方法结束
  } // MapConnectionFactory 类结束

  /** Post-processor that adds a {@link Schema} and sets it as default. */ // AddSchemaPostProcessor 类文档注释：添加 Schema 并设置为默认的后处理器
  private static class AddSchemaPostProcessor // 定义私有静态内部类 AddSchemaPostProcessor
      implements CalciteAssert.ConnectionPostProcessor { // 实现 ConnectionPostProcessor 接口
    private final String name; // 声明私有的最终成员变量，存储模式名称
    private final Schema schema; // 声明私有的最终成员变量，存储 Schema 实例

    AddSchemaPostProcessor(String name, Schema schema) { // 构造方法，参数为模式名称和 Schema 实例
      this.name = requireNonNull(name, "name"); // 初始化 name 成员变量，使用 requireNonNull 进行非空检查
      this.schema = requireNonNull(schema, "schema"); // 初始化 schema 成员变量，使用 requireNonNull 进行非空检查
    } // 构造方法结束

    @Override public Connection apply(Connection connection) throws SQLException { // 实现 ConnectionPostProcessor 接口的 apply 方法，处理连接
      CalciteConnection con = connection.unwrap(CalciteConnection.class); // 将连接解包为 CalciteConnection 类型
      SchemaPlus rootSchema = con.getRootSchema(); // 获取根 Schema
      rootSchema.add(name, schema); // 将 Schema 添加到根 Schema 中
      connection.setSchema(name); // 设置连接的默认 Schema
      return connection; // 返回处理后的连接
    } // apply 方法结束
  } // AddSchemaPostProcessor 类结束

  /** Post-processor that adds a type. */ // AddTypePostProcessor 类文档注释：添加类型到连接的后处理器
  private static class AddTypePostProcessor // 定义私有静态内部类 AddTypePostProcessor
      implements CalciteAssert.ConnectionPostProcessor { // 实现 ConnectionPostProcessor 接口
    private final String name; // 声明私有的最终成员变量，存储类型名称
    private final RelProtoDataType protoDataType; // 声明私有的最终成员变量，存储关系原型数据类型

    AddTypePostProcessor(String name, RelProtoDataType protoDataType) { // 构造方法，参数为类型名称和关系原型数据类型
      this.name = requireNonNull(name, "name"); // 初始化 name 成员变量，使用 requireNonNull 进行非空检查
      this.protoDataType = requireNonNull(protoDataType, "protoDataType"); // 初始化 protoDataType 成员变量，使用 requireNonNull 进行非空检查
    } // 构造方法结束

    @Override public Connection apply(Connection connection) throws SQLException { // 实现 ConnectionPostProcessor 接口的 apply 方法，处理连接
      CalciteConnection con = connection.unwrap(CalciteConnection.class); // 将连接解包为 CalciteConnection 类型
      SchemaPlus rootSchema = con.getRootSchema(); // 获取根 Schema
      rootSchema.add(name, protoDataType); // 将类型添加到根 Schema 中
      return connection; // 返回处理后的连接
    } // apply 方法结束
  } // AddTypePostProcessor 类结束

  /** Post-processor that sets a default schema name. */ // DefaultSchemaPostProcessor 类文档注释：设置默认模式名称的后处理器
  private static class DefaultSchemaPostProcessor // 定义私有静态内部类 DefaultSchemaPostProcessor
      implements CalciteAssert.ConnectionPostProcessor { // 实现 ConnectionPostProcessor 接口
    private final String name; // 声明私有的最终成员变量，存储默认模式名称

    DefaultSchemaPostProcessor(String name) { // 构造方法，参数为模式名称
      this.name = name; // 初始化 name 成员变量
    } // 构造方法结束

    @Override public Connection apply(Connection connection) throws SQLException { // 实现 ConnectionPostProcessor 接口的 apply 方法，处理连接
      connection.setSchema(name); // 设置连接的默认 Schema
      return connection; // 返回处理后的连接
    } // apply 方法结束
  } // DefaultSchemaPostProcessor 类结束

  /** Post-processor that adds a {@link CalciteAssert.SchemaSpec}
   * (set of schemes) to a connection. */ // AddSchemaSpecPostProcessor 类文档注释：添加 SchemaSpec（模式集合）到连接的后处理器
  private static class AddSchemaSpecPostProcessor // 定义私有静态内部类 AddSchemaSpecPostProcessor
      implements CalciteAssert.ConnectionPostProcessor { // 实现 ConnectionPostProcessor 接口
    private final CalciteAssert.SchemaSpec schemaSpec; // 声明私有的最终成员变量，存储模式规范

    AddSchemaSpecPostProcessor(CalciteAssert.SchemaSpec schemaSpec) { // 构造方法，参数为模式规范
      this.schemaSpec = schemaSpec; // 初始化 schemaSpec 成员变量
    } // 构造方法结束

    @Override public Connection apply(Connection connection) throws SQLException { // 实现 ConnectionPostProcessor 接口的 apply 方法，处理连接
      CalciteConnection con = connection.unwrap(CalciteConnection.class); // 将连接解包为 CalciteConnection 类型
      SchemaPlus rootSchema = con.getRootSchema(); // 获取根 Schema
      switch (schemaSpec) { // 根据 schemaSpec 的值进行分支处理
      case CLONE_FOODMART: // 如果是 CLONE_FOODMART 模式
      case JDBC_FOODMART_WITH_LATTICE: // 如果是 JDBC_FOODMART_WITH_LATTICE 模式
        CalciteAssert.addSchema(rootSchema, CalciteAssert.SchemaSpec.JDBC_FOODMART); // 先添加 JDBC_FOODMART 模式
        // fall through // 注释：继续执行 default 分支
      default: // 默认分支
        CalciteAssert.addSchema(rootSchema, schemaSpec); // 添加指定的模式规范
      } // switch 语句结束
      con.setSchema(schemaSpec.schemaName); // 设置连接的默认 Schema 为 schemaSpec 的 schemaName
      return connection; // 返回处理后的连接
    } // apply 方法结束
  } // AddSchemaSpecPostProcessor 类结束

  /** Connection factory that uses the same instance of connections. */ // PoolingConnectionFactory 类文档注释：使用相同连接实例的连接工厂（连接池）
  private static class PoolingConnectionFactory implements ConnectionFactory { // 定义私有静态内部类 PoolingConnectionFactory，实现 ConnectionFactory 接口
    private final PoolingDataSource<PoolableConnection> dataSource; // 声明私有的最终成员变量，存储池化数据源

    PoolingConnectionFactory(final ConnectionFactory factory) { // 构造方法，参数为连接工厂
      final PoolableConnectionFactory connectionFactory = // 创建可池化连接工厂
          new PoolableConnectionFactory(factory::createConnection, null); // 使用工厂的 createConnection 方法作为连接提供者
      connectionFactory.setRollbackOnReturn(false); // 设置连接返回时不自动回滚
      this.dataSource = // 初始化 dataSource 成员变量
          new PoolingDataSource<>(new GenericObjectPool<>(connectionFactory)); // 创建池化数据源，使用通用对象池管理连接
    } // 构造方法结束

    @Override public Connection createConnection() throws SQLException { // 实现 ConnectionFactory 接口的 createConnection 方法，创建数据库连接
      return dataSource.getConnection(); // 从连接池中获取一个连接
    } // createConnection 方法结束
  } // PoolingConnectionFactory 类结束
} // ConnectionFactories 类结束
