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
 */ // Apache许可证声明，表明该代码遵循Apache 2.0开源协议
package org.apache.calcite.adapter.jdbc; // 声明该类属于org.apache.calcite.adapter.jdbc包，这是Calcite JDBC适配器模块

import org.apache.calcite.DataContext; // 导入Calcite的数据上下文接口，用于在查询执行过程中传递运行时信息
import org.apache.calcite.linq4j.tree.Expression; // 导入LINQ4j的表达式类，用于构建表达式树
import org.apache.calcite.linq4j.tree.Expressions; // 导入LINQ4j的表达式工具类，提供创建表达式的静态方法
import org.apache.calcite.schema.Schema; // 导入Calcite的Schema接口，定义了数据库模式的基本行为
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，扩展了Schema接口，支持动态添加子Schema
import org.apache.calcite.schema.Schemas; // 导入Schemas工具类，提供Schema相关的静态工具方法
import org.apache.calcite.schema.Table; // 导入Table接口，定义了数据库表的基本行为
import org.apache.calcite.schema.Wrapper; // 导入Wrapper接口，支持将对象包装为其他类型
import org.apache.calcite.schema.lookup.IgnoreCaseLookup; // 导入IgnoreCaseLookup类，提供不区分大小写的查找功能
import org.apache.calcite.schema.lookup.LikePattern; // 导入LikePattern类，用于支持模式匹配的查找
import org.apache.calcite.schema.lookup.LoadingCacheLookup; // 导入LoadingCacheLookup类，提供带缓存的查找功能
import org.apache.calcite.schema.lookup.Lookup; // 导入Lookup接口，定义了通用的查找行为
import org.apache.calcite.sql.SqlDialect; // 导入SqlDialect类，表示SQL方言，处理不同数据库的SQL语法差异
import org.apache.calcite.sql.SqlDialectFactory; // 导入SqlDialectFactory接口，用于创建SqlDialect实例的工厂接口
import org.apache.calcite.sql.SqlDialectFactoryImpl; // 导入SqlDialectFactoryImpl类，SqlDialectFactory的默认实现
import org.apache.calcite.util.BuiltInMethod; // 导入BuiltInMethod类，定义了Calcite内置的方法引用

import com.google.common.base.Suppliers; // 导入Guava的Suppliers工具类，提供延迟初始化的Supplier创建方法
import com.google.common.collect.ImmutableSet; // 导入Guava的ImmutableSet类，提供不可变的Set集合实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的值

import java.sql.Connection; // 导入JDBC的Connection接口，表示与数据库的连接
import java.sql.ResultSet; // 导入JDBC的ResultSet接口，表示数据库查询结果集
import java.sql.SQLException; // 导入JDBC的SQLException类，表示数据库操作异常
import java.util.Optional; // 导入Java的Optional类，用于包装可能为null的值
import java.util.Set; // 导入Java的Set接口，表示不重复元素的集合
import java.util.function.Supplier; // 导入Java的Supplier函数式接口，用于延迟提供值
import javax.sql.DataSource; // 导入JDBC的DataSource接口，表示数据库连接池或数据源

import static java.util.Objects.requireNonNull; // 导入Objects类的静态方法requireNonNull，用于参数非空校验

/**
 * Schema based upon a JDBC catalog (database).
 * 基于JDBC目录(数据库)的Schema实现
 *
 * <p>This schema does not directly contain tables, but contains a sub-schema
 * for each schema in the catalog in the back-end. Each of those sub-schemas is
 * an instance of {@link JdbcSchema}.
 * 该Schema不直接包含表，而是为后端目录中的每个Schema包含一个子Schema。
 * 每个子Schema都是JdbcSchema的实例。
 *
 * <p>This schema is lazy: it does not compute the list of schema names until
 * the first call to {@link #subSchemas()} and {@link Lookup#get(String)}. Then it creates a
 * {@link JdbcSchema} for this schema name. Each JdbcSchema will populate its
 * tables on demand.
 * 该Schema采用延迟加载策略：直到第一次调用subSchemas()和Lookup.get(String)方法时，
 * 才会计算Schema名称列表。然后为每个Schema名称创建JdbcSchema实例。
 * 每个JdbcSchema会按需填充其表信息。
 */
public class JdbcCatalogSchema extends JdbcBaseSchema implements Wrapper { // 定义JdbcCatalogSchema类，继承JdbcBaseSchema基类，实现Wrapper接口
  final DataSource dataSource; // 成员变量：数据源，用于获取数据库连接，final修饰表示不可变
  public final SqlDialect dialect; // 成员变量：SQL方言，用于处理不同数据库的SQL语法差异，public final表示公开且不可变
  final JdbcConvention convention; // 成员变量：JDBC约定，定义了JDBC适配器的转换规则，final修饰表示不可变
  final String catalog; // 成员变量：目录名称，表示数据库的catalog名称，final修饰表示不可变
  private final Lookup<JdbcSchema> subSchemas; // 成员变量：子Schema查找器，用于查找和管理该catalog下的所有Schema，private final表示私有且不可变

  /** default schema name, lazily initialized. */
  // 默认Schema名称，延迟初始化，使用Supplier模式实现懒加载
  @SuppressWarnings({"method.invocation.invalid", "Convert2MethodRef"}) // 抑制编译器警告，忽略方法调用无效和lambda表达式转换的警告
  private final Supplier<Optional<String>> defaultSchemaName = // 成员变量：默认Schema名称的Supplier，使用Optional包装可能为null的值
      Suppliers.memoize(() -> Optional.ofNullable(computeDefaultSchemaName())); // 使用Guava的Suppliers.memoize方法实现缓存，确保computeDefaultSchemaName()只执行一次

  /** Creates a JdbcCatalogSchema. */
  // 创建JdbcCatalogSchema实例的构造方法
  public JdbcCatalogSchema(DataSource dataSource, SqlDialect dialect, // 构造方法参数：dataSource-数据源，dialect-SQL方言
      JdbcConvention convention, String catalog) { // 构造方法参数：convention-JDBC约定，catalog-目录名称
    this.dataSource = requireNonNull(dataSource, "dataSource"); // 初始化dataSource成员变量，使用requireNonNull进行非空校验
    this.dialect = requireNonNull(dialect, "dialect"); // 初始化dialect成员变量，使用requireNonNull进行非空校验
    this.convention = requireNonNull(convention, "convention"); // 初始化convention成员变量，使用requireNonNull进行非空校验
    this.catalog = catalog; // 初始化catalog成员变量
    this.subSchemas = new LoadingCacheLookup<>(new IgnoreCaseLookup<JdbcSchema>() { // 初始化subSchemas成员变量，创建一个带缓存的、不区分大小写的查找器
      @Override public @Nullable JdbcSchema get(String name) { // 重写get方法，根据Schema名称获取对应的JdbcSchema实例
        try (Connection connection = dataSource.getConnection(); // 尝试获取数据库连接，使用try-with-resources确保连接自动关闭
            ResultSet resultSet = // 创建ResultSet结果集，用于存储查询结果
                connection.getMetaData().getSchemas(catalog, name)) { // 获取数据库元数据，查询指定catalog和schema名称的信息
          while (resultSet.next()) { // 遍历结果集
            final String schemaName = // 定义变量存储Schema名称
                requireNonNull(resultSet.getString(1), // 从结果集第一列获取Schema名称
                    "got null schemaName from the database"); // 如果为null则抛出异常
            return new JdbcSchema(dataSource, dialect, convention, catalog, schemaName); // 创建并返回JdbcSchema实例
          }
        } catch (SQLException e) { // 捕获SQL异常
          throw new RuntimeException(e); // 将SQL异常转换为运行时异常抛出
        }
        return null; // 如果没有找到匹配的Schema，返回null
      }

      @Override public Set<String> getNames(LikePattern pattern) { // 重写getNames方法，根据模式匹配获取所有Schema名称
        final ImmutableSet.Builder<String> builder = // 创建不可变Set的构建器
            ImmutableSet.builder(); // 初始化构建器
        try (Connection connection = dataSource.getConnection(); // 尝试获取数据库连接，使用try-with-resources确保连接自动关闭
            ResultSet resultSet = // 创建ResultSet结果集，用于存储查询结果
                connection.getMetaData().getSchemas(catalog, pattern.pattern)) { // 获取数据库元数据，查询指定catalog和模式匹配的Schema信息
          while (resultSet.next()) { // 遍历结果集
            builder.add( // 将Schema名称添加到构建器中
                requireNonNull(resultSet.getString(1), // 从结果集第一列获取Schema名称
                    "got null schemaName from the database")); // 如果为null则抛出异常
          }
        } catch (SQLException e) { // 捕获SQL异常
          throw new RuntimeException(e); // 将SQL异常转换为运行时异常抛出
        }
        return builder.build(); // 构建并返回不可变的Set集合
      }
    });
  }

  // 静态工厂方法：创建JdbcCatalogSchema实例，使用默认的SQL方言工厂
  public static JdbcCatalogSchema create( // 方法声明：创建JdbcCatalogSchema的静态工厂方法
      @Nullable SchemaPlus parentSchema, // 参数：父Schema，可以为null，@Nullable注解表示可能为null
      String name, // 参数：Schema名称
      DataSource dataSource, // 参数：数据源
      String catalog) { // 参数：目录名称
    return create(parentSchema, name, dataSource, // 调用重载的create方法，传入默认的SQL方言工厂
        SqlDialectFactoryImpl.INSTANCE, catalog); // 使用SqlDialectFactoryImpl.INSTANCE作为默认的SQL方言工厂
  }

  // 静态工厂方法：创建JdbcCatalogSchema实例，支持自定义SQL方言工厂
  public static JdbcCatalogSchema create( // 方法声明：创建JdbcCatalogSchema的静态工厂方法
      @Nullable SchemaPlus parentSchema, // 参数：父Schema，可以为null，@Nullable注解表示可能为null
      String name, // 参数：Schema名称
      DataSource dataSource, // 参数：数据源
      SqlDialectFactory dialectFactory, // 参数：SQL方言工厂，用于创建SQL方言实例
      String catalog) { // 参数：目录名称
    final Expression expression = // 定义变量存储表达式，用于在代码生成时引用Schema
        parentSchema != null // 判断父Schema是否不为null
            ? Schemas.subSchemaExpression(parentSchema, name, // 如果不为null，使用Schemas工具类创建子Schema表达式
                JdbcCatalogSchema.class) // 指定Schema类型为JdbcCatalogSchema
            : Expressions.call(DataContext.ROOT, // 如果为null，使用DataContext.ROOT作为表达式基础
                BuiltInMethod.DATA_CONTEXT_GET_ROOT_SCHEMA.method); // 调用内置方法获取根Schema
    final SqlDialect dialect = // 定义变量存储SQL方言
        JdbcSchema.createDialect(dialectFactory, dataSource); // 使用JdbcSchema的静态方法创建SQL方言
    final JdbcConvention convention = // 定义变量存储JDBC约定
        JdbcConvention.of(dialect, expression, name); // 使用JdbcConvention.of方法创建JDBC约定
    return new JdbcCatalogSchema(dataSource, dialect, convention, catalog); // 创建并返回JdbcCatalogSchema实例
  }

  @Override public Lookup<Table> tables() { // 重写父类方法：获取表的查找器
    return Lookup.empty(); // 返回空的查找器，因为JdbcCatalogSchema不直接包含表，表包含在子Schema中
  }

  @Override public Lookup<? extends Schema> subSchemas() { // 重写父类方法：获取子Schema的查找器
    return subSchemas; // 返回子Schema查找器，该查找器会延迟加载和管理所有子Schema
  }

  private @Nullable String computeDefaultSchemaName() { // 私有方法：计算默认的Schema名称，@Nullable注解表示返回值可能为null
    try (Connection connection = dataSource.getConnection()) { // 尝试获取数据库连接，使用try-with-resources确保连接自动关闭
      return connection.getSchema(); // 从连接中获取当前Schema名称并返回
    } catch (SQLException e) { // 捕获SQL异常
      throw new RuntimeException(e); // 将SQL异常转换为运行时异常抛出
    }
  }

  /** Returns the name of the default sub-schema. */
  // 公开方法：返回默认子Schema的名称
  public @Nullable String getDefaultSubSchemaName() { // 方法声明：获取默认子Schema名称，@Nullable注解表示返回值可能为null
    return defaultSchemaName.get().orElse(null); // 从Supplier中获取Optional值，如果存在则返回，否则返回null
  }

  /** Returns the data source. */
  // 公开方法：返回数据源
  public DataSource getDataSource() { // 方法声明：获取数据源
    return dataSource; // 返回dataSource成员变量
  }


  @Override public <T extends Object> @Nullable T unwrap(Class<T> clazz) { // 实现Wrapper接口方法：将当前对象包装为指定类型
    if (clazz.isInstance(this)) { // 判断当前对象是否是指定类型的实例
      return clazz.cast(this); // 如果是，将当前对象转换为指定类型并返回
    }
    if (clazz == DataSource.class) { // 判断指定类型是否为DataSource类
      return clazz.cast(getDataSource()); // 如果是，将数据源转换为指定类型并返回
    }
    return null; // 如果都不是，返回null
  }
}