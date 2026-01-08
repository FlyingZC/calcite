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
// 声明包名，表示这个类属于 org.apache.calcite 包，是 Apache Calcite SQL 框架的核心包之一
package org.apache.calcite;

// 导入 JavaTypeFactory 接口，用于创建和管理 Java 类型系统中的类型，在 Calcite 中用于 SQL 类型到 Java 类型的映射
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入 CalciteConnection 接口，表示 Calcite 的数据库连接，提供了获取类型工厂、查询提供者等功能
import org.apache.calcite.jdbc.CalciteConnection;
// 导入 QueryProvider 接口，用于执行 LINQ 风格的查询，是 Calcite 查询执行的核心接口
import org.apache.calcite.linq4j.QueryProvider;
// 导入 SchemaPlus 接口，表示 Calcite 中的模式（Schema），可以动态添加表、函数等对象
import org.apache.calcite.schema.SchemaPlus;

// 导入 Google Guava 库的 ImmutableMap 类，用于创建不可变的 Map，保证线程安全和数据不可变性
import com.google.common.collect.ImmutableMap;

// 导入 Checker Framework 的注解，用于标记可能为 null 的返回值，帮助进行空值检查
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 Serializable 接口，用于支持对象的序列化，使对象可以被保存到文件或通过网络传输
import java.io.Serializable;
// 导入 Map 接口，用于存储键值对数据
import java.util.Map;
// 导入 Function 函数式接口，用于表示接受一个参数并产生结果的函数
import java.util.function.Function;

// 导入 Objects 类的静态方法 requireNonNull，用于检查对象引用是否为 null，如果为 null 则抛出 NullPointerException
import static java.util.Objects.requireNonNull;

// 类文档注释：说明这是一个工具类，提供了创建和操作 DataContext 实例的静态方法
// DataContext 是 Calcite 中的核心接口，代表查询执行时的上下文环境，包含 schema、类型工厂、变量等信息
/** Utilities for {@link DataContext}. */
// 定义 DataContexts 工具类，使用 final 修饰防止被继承，所有成员都是静态的，不需要创建实例
public class DataContexts {
  // 私有构造方法，防止外部创建实例，确保这是一个纯工具类
  private DataContexts() {
  }

  // 静态常量，表示一个空的 DataContext 实例，不包含任何变量，用于需要 DataContext 但不需要变量的场景
  // EmptyDataContext 是一个内部类，实现了 DataContext 接口，所有 get 方法都返回 null
  /** Instance of {@link DataContext} that has no variables. */
  public static final DataContext EMPTY = new EmptyDataContext();

  // 静态工厂方法，根据给定的 Map 创建一个 DataContext 实例
  // Map 中的键值对会被存储在 DataContext 中，可以通过 get(String name) 方法获取
  // 参数 map：包含变量名和变量值的映射，键是变量名（String 类型），值是任意类型
  // 返回值：返回一个 MapDataContext 实例，该实例使用给定的 Map 作为数据源
  /** Returns an instance of {@link DataContext} with the given map. */
  public static DataContext of(Map<String, ?> map) {
    return new MapDataContext(map);
  }

  // 静态工厂方法，根据给定的 Function 创建一个 DataContext 实例
  // Function 是一个函数式接口，接受一个字符串参数（变量名），返回对应的变量值
  // 这种方式允许动态计算变量值，而不是预先存储所有变量
  // 参数 fn：函数对象，根据变量名返回对应的变量值，可能返回 null
  // 返回值：返回一个 FunctionDataContext 实例，该实例使用给定的 Function 作为数据源
  /** Returns an instance of {@link DataContext} with the given function. */
  public static DataContext of(Function<String, ? extends @Nullable Object> fn) {
    return new FunctionDataContext(fn);
  }

  // 静态工厂方法，根据给定的 CalciteConnection 和 rootSchema 创建一个 DataContext 实例
  // 这个方法创建的 DataContext 包含完整的连接信息和 schema 信息，但不包含变量
  // 参数 connection：Calcite 数据库连接对象，提供了类型工厂、查询提供者等核心功能
  // 参数 rootSchema：根 schema 对象，包含了数据库的表、函数等元数据信息，可能为 null
  // 返回值：返回一个 DataContextImpl 实例，该实例包含连接和 schema 信息，使用空的 ImmutableMap 作为变量存储
  /** Returns an instance of {@link DataContext} with the given connection
   * and root schema but no variables. */
  public static DataContext of(CalciteConnection connection,
      @Nullable SchemaPlus rootSchema) {
    return new DataContextImpl(connection, rootSchema, ImmutableMap.of());
  }

  // 内部类文档注释：说明这是一个空的 DataContext 实现，不包含任何变量
  // 实现了 Serializable 接口以便在 Spark 等分布式计算框架中使用，支持序列化传输
  /** Implementation of {@link DataContext} that has no variables.
   *
   * <p>It is {@link Serializable} for Spark's benefit. */
  // 定义 EmptyDataContext 内部类，实现了 DataContext 和 Serializable 接口
  // 这个类作为其他 DataContext 实现的基类，提供了默认的空实现
  private static class EmptyDataContext implements DataContext, Serializable {
    // 重写 getRootSchema 方法，返回根 schema
    // 返回值：返回 null，表示没有根 schema
    @Override public @Nullable SchemaPlus getRootSchema() {
      return null;
    }

    // 重写 getTypeFactory 方法，返回类型工厂
    // 类型工厂用于创建和管理 SQL 类型到 Java 类型的映射
    // 返回值：抛出 UnsupportedOperationException，表示不支持此操作
    @Override public JavaTypeFactory getTypeFactory() {
      throw new UnsupportedOperationException();
    }

    // 重写 getQueryProvider 方法，返回查询提供者
    // 查询提供者用于执行 LINQ 风格的查询
    // 返回值：抛出 UnsupportedOperationException，表示不支持此操作
    @Override public QueryProvider getQueryProvider() {
      throw new UnsupportedOperationException();
    }

    // 重写 get 方法，根据变量名获取变量值
    // 参数 name：要获取的变量名
    // 返回值：返回 null，表示没有对应的变量值
    @Override public @Nullable Object get(String name) {
      return null;
    }
  }

  // 内部类文档注释：说明这是一个基于 Map 的 DataContext 实现
  // 使用 Map 存储变量名和变量值的映射关系
  // 注意：Map 中的键和值都不能为 null，如果需要表示某个键没有值，应该从 Map 中移除该键，而不是存储 null 值
  /** Implementation of {@link DataContext} backed by a Map.
   *
   * <p>Keys and values in the map must not be null. Rather than storing a null
   * value for a key, remove the key from the map; the effect will be the
   * same. */
  // 定义 MapDataContext 内部类，继承自 EmptyDataContext，复用其默认实现
  // 这个类使用 ImmutableMap 存储变量，保证线程安全
  private static class MapDataContext extends EmptyDataContext {
    // 成员变量：使用不可变的 Map 存储变量名和变量值的映射
    // ImmutableMap 保证数据不会被修改，提高了线程安全性
    private final ImmutableMap<String, ?> map;

    // 构造方法，接受一个 Map 参数并创建不可变的副本
    // 参数 map：包含变量名和变量值的映射，会被复制成不可变的 ImmutableMap
    MapDataContext(Map<String, ?> map) {
      this.map = ImmutableMap.copyOf(map);
    }

    // 重写 get 方法，从 Map 中获取变量值
    // 参数 name：要获取的变量名
    // 返回值：返回 Map 中对应的值，如果不存在则返回 null
    @Override public @Nullable Object get(String name) {
      return map.get(name);
    }
  }

  // 内部类文档注释：说明这是一个基于 Function 的 DataContext 实现
  // 使用 Function 动态计算变量值，而不是预先存储所有变量
  // 这种方式更加灵活，可以根据需要动态生成变量值
  /** Implementation of {@link DataContext} backed by a Function. */
  // 定义 FunctionDataContext 内部类，继承自 EmptyDataContext，复用其默认实现
  // 这个类使用 Function 动态获取变量值
  private static class FunctionDataContext extends EmptyDataContext {
    // 成员变量：存储用于获取变量值的函数对象
    // Function 接受一个字符串参数（变量名），返回对应的变量值
    private final Function<String, ? extends @Nullable Object> fn;

    // 构造方法，接受一个 Function 参数
    // 参数 fn：函数对象，用于根据变量名获取变量值，不能为 null
    // 使用 requireNonNull 检查参数，如果为 null 则抛出 NullPointerException
    FunctionDataContext(Function<String, ? extends @Nullable Object> fn) {
      this.fn = requireNonNull(fn, "fn");
    }

    // 重写 get 方法，使用 Function 获取变量值
    // 参数 name：要获取的变量名
    // 返回值：调用 Function 的 apply 方法，传入变量名，返回计算得到的变量值
    @Override public @Nullable Object get(String name) {
      return fn.apply(name);
    }
  }

  // 内部类文档注释：说明这是一个完整的 DataContext 实现，基于 Map 并包含连接和 schema 信息
  // 这个类继承自 MapDataContext，复用了基于 Map 的变量存储功能
  // 同时添加了 CalciteConnection 和 SchemaPlus 支持，提供了完整的 DataContext 功能
  /** Implementation of {@link DataContext} backed by a Map. */
  // 定义 DataContextImpl 内部类，继承自 MapDataContext
  // 这个类提供了完整的 DataContext 实现，包括连接、schema 和变量
  private static class DataContextImpl extends MapDataContext {
    // 成员变量：Calcite 数据库连接对象，用于获取类型工厂和查询提供者
    // 连接对象包含了数据库的元数据、类型系统等核心信息
    private final CalciteConnection connection;
    // 成员变量：根 schema 对象，包含了数据库的表、函数等元数据信息
    // SchemaPlus 是一个可变的 schema 接口，支持动态添加表和函数
    private final @Nullable SchemaPlus rootSchema;

    // 构造方法，接受连接、schema 和变量 Map 三个参数
    // 参数 connection：Calcite 数据库连接对象，不能为 null
    // 参数 rootSchema：根 schema 对象，不能为 null
    // 参数 map：包含变量名和变量值的映射，会被传递给父类 MapDataContext
    // 使用 requireNonNull 检查 connection 和 rootSchema 参数，确保不为 null
    DataContextImpl(CalciteConnection connection,
        @Nullable SchemaPlus rootSchema, Map<String, Object> map) {
      super(map);
      this.connection = requireNonNull(connection, "connection");
      this.rootSchema = requireNonNull(rootSchema, "rootSchema");
    }

    // 重写 getTypeFactory 方法，从连接对象获取类型工厂
    // 类型工厂用于创建和管理 SQL 类型到 Java 类型的映射
    // 返回值：返回连接对象的类型工厂
    @Override public JavaTypeFactory getTypeFactory() {
      return connection.getTypeFactory();
    }

    // 重写 getRootSchema 方法，返回根 schema 对象
    // 返回值：返回存储的 rootSchema 对象
    @Override public @Nullable SchemaPlus getRootSchema() {
      return rootSchema;
    }

    // 重写 getQueryProvider 方法，从连接对象获取查询提供者
    // 查询提供者用于执行 LINQ 风格的查询
    // CalciteConnection 本身就实现了 QueryProvider 接口
    // 返回值：返回连接对象本身，因为它实现了 QueryProvider 接口
    @Override public QueryProvider getQueryProvider() {
      return connection;
    }
  }
}
