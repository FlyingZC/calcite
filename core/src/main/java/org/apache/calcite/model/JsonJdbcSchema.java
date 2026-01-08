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
package org.apache.calcite.model;  // 定义包名，该类属于 org.apache.calcite.model 包，用于存储 Calcite 模型相关的类

import com.fasterxml.jackson.annotation.JsonCreator;  // 导入 Jackson 注解，用于标记 JSON 反序列化时使用的构造方法
import com.fasterxml.jackson.annotation.JsonProperty;  // 导入 Jackson 注解，用于标记 JSON 属性与 Java 字段的映射关系

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入 CheckerFramework 注解，用于标记可能为 null 的类型

import java.util.List;  // 导入 Java 集合框架中的 List 接口，用于存储路径列表

import static java.util.Objects.requireNonNull;  // 导入 Objects 类的静态方法，用于参数非空校验

/**
 * JSON object representing a schema that maps to a JDBC database.  // 类注释：表示映射到 JDBC 数据库的 JSON schema 对象
 *
 * <p>Like the base class {@link JsonSchema},  // 说明：像基类 JsonSchema 一样
 * occurs within {@link JsonRoot#schemas}.  // 说明：出现在 JsonRoot 的 schemas 列表中
 *
 * @see JsonRoot Description of JSON schema elements  // 参见：JsonRoot 类，其中描述了 JSON schema 元素的详细信息
 */
public class JsonJdbcSchema extends JsonSchema {  // 类定义：JsonJdbcSchema 继承自 JsonSchema，表示一个基于 JDBC 的 schema 定义
  /** The name of the JDBC driver class.  // 字段注释：JDBC 驱动类的全限定名
   *
   * <p>Optional. If not specified, uses whichever class the JDBC  // 说明：可选字段，如果未指定，则使用 JDBC DriverManager 自动选择的驱动类
   * {@link java.sql.DriverManager} chooses.  // 说明：由 DriverManager 根据 jdbcUrl 自动选择合适的驱动
   */
  public final @Nullable String jdbcDriver;  // 字段定义：JDBC 驱动类名，final 表示不可变，@Nullable 表示可以为 null

  /** The FQN of the {@link org.apache.calcite.sql.SqlDialectFactory} implementation.  // 字段注释：SqlDialectFactory 实现类的全限定名（FQN = Fully Qualified Name）
   *
   * <p>Optional. If not specified, uses whichever class the JDBC  // 说明：可选字段，如果未指定，则使用 JDBC DriverManager 自动选择的方言工厂
   * {@link java.sql.DriverManager} chooses.  // 说明：但实际上这里是指使用默认的 SqlDialectFactory 实现
   */
  public final @Nullable String sqlDialectFactory;  // 字段定义：SQL 方言工厂类名，用于生成特定数据库的 SQL 方言对象

  /** JDBC connect string, for example "jdbc:mysql://localhost/foodmart".  // 字段注释：JDBC 连接字符串，例如 "jdbc:mysql://localhost/foodmart"
   */
  public final String jdbcUrl;  // 字段定义：JDBC 连接 URL，必填字段，用于建立数据库连接

  /** JDBC user name.  // 字段注释：JDBC 用户名
   *
   * <p>Optional.  // 说明：可选字段，如果数据库不需要认证可以为 null
   */
  public final @Nullable String jdbcUser;  // 字段定义：JDBC 连接用户名，用于数据库身份验证

  /** JDBC connect string, for example "jdbc:mysql://localhost/foodmart".  // 字段注释：JDBC 连接密码（此处注释有误，应该是密码而不是连接字符串）
   *
   * <p>Optional.  // 说明：可选字段，如果数据库不需要密码可以为 null
   */
  public final @Nullable String jdbcPassword;  // 字段定义：JDBC 连接密码，与 jdbcUser 配合使用进行身份验证

  /** Name of the initial catalog in the JDBC data source.  // 字段注释：JDBC 数据源中的初始 catalog（目录）名称
   *
   * <p>Optional.  // 说明：可选字段，某些数据库（如 MySQL）使用 catalog 来组织数据库
   */
  public final @Nullable String jdbcCatalog;  // 字段定义：JDBC catalog 名称，用于指定连接的数据库目录

  /** Name of the initial schema in the JDBC data source.  // 字段注释：JDBC 数据源中的初始 schema（模式）名称
   *
   * <p>Optional.  // 说明：可选字段，某些数据库（如 PostgreSQL）使用 schema 来组织表
   */
  public final @Nullable String jdbcSchema;  // 字段定义：JDBC schema 名称，用于指定连接的数据库模式

  @JsonCreator  // 注解：标记此构造方法为 Jackson 反序列化 JSON 时使用的工厂方法
  public JsonJdbcSchema(  // 构造方法定义：用于创建 JsonJdbcSchema 对象，通过 Jackson 从 JSON 反序列化时自动调用
      @JsonProperty(value = "name", required = true) String name,  // 参数：schema 名称，必填，JsonProperty 指定 JSON 属性名为 "name"
      @JsonProperty("path") @Nullable List<Object> path,  // 参数：路径列表，可选，用于指定模型文件的路径
      @JsonProperty("cache") @Nullable Boolean cache,  // 参数：缓存标志，可选，用于控制是否缓存 schema 数据
      @JsonProperty("autoLattice") @Nullable Boolean autoLattice,  // 参数：自动 lattice 标志，可选，用于控制是否自动创建 lattice 结构
      @JsonProperty("jdbcDriver") @Nullable String jdbcDriver,  // 参数：JDBC 驱动类名，可选
      @JsonProperty("sqlDialectFactory") @Nullable String sqlDialectFactory,  // 参数：SQL 方言工厂类名，可选
      @JsonProperty(value = "jdbcUrl", required = true)  String jdbcUrl,  // 参数：JDBC 连接 URL，必填
      @JsonProperty("jdbcUser") @Nullable String jdbcUser,  // 参数：JDBC 用户名，可选
      @JsonProperty("jdbcPassword") @Nullable String jdbcPassword,  // 参数：JDBC 密码，可选
      @JsonProperty("jdbcCatalog") @Nullable String jdbcCatalog,  // 参数：JDBC catalog 名称，可选
      @JsonProperty("jdbcSchema") @Nullable String jdbcSchema) {  // 参数：JDBC schema 名称，可选
    super(name, path, cache, autoLattice);  // 调用父类 JsonSchema 的构造方法，初始化继承的成员变量
    this.jdbcDriver = jdbcDriver;  // 初始化 JDBC 驱动类名字段
    this.sqlDialectFactory = sqlDialectFactory;  // 初始化 SQL 方言工厂类名字段
    this.jdbcUrl = requireNonNull(jdbcUrl, "jdbcUrl");  // 初始化 JDBC URL 字段，并确保其不为 null，否则抛出 NullPointerException
    this.jdbcUser = jdbcUser;  // 初始化 JDBC 用户名字段
    this.jdbcPassword = jdbcPassword;  // 初始化 JDBC 密码字段
    this.jdbcCatalog = jdbcCatalog;  // 初始化 JDBC catalog 字段
    this.jdbcSchema = jdbcSchema;  // 初始化 JDBC schema 字段
  }  // 构造方法结束

  @Override public void accept(ModelHandler handler) {  // 方法定义：接受访问者模式中的处理器，用于处理此 schema 对象
    handler.visit(this);  // 调用处理器的 visit 方法，将当前 JsonJdbcSchema 对象传递给处理器进行处理
  }  // 方法结束
}  // 类定义结束
