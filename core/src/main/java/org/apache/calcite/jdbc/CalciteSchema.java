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
// Apache许可证声明，说明代码的版权和使用条款
package org.apache.calcite.jdbc; // 包声明，该类属于org.apache.calcite.jdbc包，是Calcite JDBC相关的核心包

import org.apache.calcite.linq4j.function.Experimental; // 导入实验性功能注解，用于标记实验性的API
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，用于代码生成和表达式处理
import org.apache.calcite.materialize.Lattice; // 导入Lattice类，用于物化视图和立方体优化
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型，用于延迟创建数据类型
import org.apache.calcite.schema.Function; // 导入函数接口，代表Calcite中的标量函数、表函数等
import org.apache.calcite.schema.Schema; // 导入Schema接口，代表数据库模式或命名空间
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，扩展的Schema接口，提供更多功能
import org.apache.calcite.schema.SchemaVersion; // 导入SchemaVersion类，用于Schema版本控制
import org.apache.calcite.schema.Table; // 导入Table接口，代表数据库表
import org.apache.calcite.schema.TableMacro; // 导入TableMacro接口，代表表宏（返回表的函数）
import org.apache.calcite.schema.Wrapper; // 导入Wrapper接口，用于类型包装和解包
import org.apache.calcite.schema.impl.MaterializedViewTable; // 导入物化视图表实现
import org.apache.calcite.schema.impl.StarTable; // 导入星型表实现，用于OLAP查询
import org.apache.calcite.schema.lookup.LikePattern; // 导入LikePattern类，用于模式匹配
import org.apache.calcite.schema.lookup.Lookup; // 导入Lookup接口，提供名称查找功能
import org.apache.calcite.schema.lookup.Named; // 导入Named接口，代表有名称的对象
import org.apache.calcite.util.LazyReference; // 导入LazyReference类，实现延迟加载引用
import org.apache.calcite.util.NameMap; // 导入NameMap类，支持大小写不敏感的名称映射
import org.apache.calcite.util.NameMultimap; // 导入NameMultimap类，支持一对多的名称映射
import org.apache.calcite.util.NameSet; // 导入NameSet类，支持大小写不敏感的名称集合
import org.apache.calcite.util.Pair; // 导入Pair类，用于存储键值对

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表
import com.google.common.collect.ImmutableSortedMap; // 导入Google Guava的不可变有序映射
import com.google.common.collect.ImmutableSortedSet; // 导入Google Guava的不可变有序集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.util.ArrayList; // 导入ArrayList动态数组类
import java.util.Collection; // 导入Collection集合接口
import java.util.List; // 导入List列表接口
import java.util.Map; // 导入Map映射接口
import java.util.NavigableMap; // 导入NavigableMap可导航映射接口
import java.util.NavigableSet; // 导入NavigableSet可导航集合接口
import java.util.Set; // 导入Set集合接口

import static com.google.common.base.Preconditions.checkArgument; // 导入参数检查工具

import static java.util.Objects.requireNonNull; // 导入对象非空检查工具

/**
 * Schema.
 * Schema（模式）类
 *
 * <p>Wrapper around user-defined schema used internally.
 * 用户定义Schema的内部包装类，Calcite内部使用
 * 
 * 这个类是Calcite中Schema的内部表示，它包装了用户定义的Schema对象，
 * 并提供了额外的功能，如缓存、子Schema管理、表管理、函数管理等。
 * 它是Calcite JDBC层和Schema层之间的桥梁。
 */
public abstract class CalciteSchema { // 抽象类，表示Calcite中的Schema包装器

  private final @Nullable CalciteSchema parent; // 父Schema引用，可为null（根Schema没有父Schema）
  public final Schema schema; // 底层的用户定义Schema对象，实际存储数据的地方
  public final String name; // Schema的名称，用于标识这个Schema
  /** Tables explicitly defined in this schema. Does not include tables in
   * {@link #schema}. */
  // 在此Schema中显式定义的表映射。不包含底层schema中的表
  // 只包含通过add方法显式添加的表，不包含底层Schema中隐含的表
  protected final NameMap<TableEntry> tableMap; // 表条目映射，键为表名，值为TableEntry对象
  private final LazyReference<Lookup<TableEntry>> tables = new LazyReference<>(); // 表查找的延迟引用，用于懒加载表查找结果
  protected final NameMultimap<FunctionEntry> functionMap; // 函数条目多重映射，支持同名函数（函数重载）
  protected final NameMap<TypeEntry> typeMap; // 类型条目映射，存储自定义数据类型
  protected final NameMap<LatticeEntry> latticeMap; // Lattice（立方体）条目映射，用于物化视图优化
  protected final NameSet functionNames; // 函数名称集合，存储所有函数名称
  protected final NameMap<FunctionEntry> nullaryFunctionMap; // 无参函数映射，存储不需要参数的函数（可当作表使用）
  protected final NameMap<CalciteSchema> subSchemaMap; // 子Schema映射，存储所有子Schema
  private final LazyReference<Lookup<CalciteSchema>> subSchemas = new LazyReference<>(); // 子Schema查找的延迟引用
  private @Nullable List<? extends List<String>> path; // Schema路径，用于函数解析的搜索路径

  // 构造方法：创建CalciteSchema实例
  protected CalciteSchema(@Nullable CalciteSchema parent, Schema schema, // 父Schema和底层Schema对象
      String name, // Schema名称
      @Nullable NameMap<CalciteSchema> subSchemaMap, // 子Schema映射（可为null）
      @Nullable NameMap<TableEntry> tableMap, // 表映射（可为null）
      @Nullable NameMap<LatticeEntry> latticeMap, // Lattice映射（可为null）
      @Nullable NameMap<TypeEntry> typeMap, // 类型映射（可为null）
      @Nullable NameMultimap<FunctionEntry> functionMap, // 函数映射（可为null）
      @Nullable NameSet functionNames, // 函数名称集合（可为null）
      @Nullable NameMap<FunctionEntry> nullaryFunctionMap, // 无参函数映射（可为null）
      @Nullable List<? extends List<String>> path) { // 路径（可为null）
    this.parent = parent; // 设置父Schema
    this.schema = schema; // 设置底层Schema
    this.name = name; // 设置Schema名称
    this.tableMap = tableMap != null ? tableMap : new NameMap<>(); // 如果tableMap为null则创建新的，否则使用传入的
    this.latticeMap = latticeMap != null ? latticeMap : new NameMap<>(); // 如果latticeMap为null则创建新的
    this.subSchemaMap = subSchemaMap != null ? subSchemaMap : new NameMap<>(); // 如果subSchemaMap为null则创建新的
    if (functionMap == null) { // 如果函数映射为null
      this.functionMap = new NameMultimap<>(); // 创建新的函数多重映射
      this.functionNames = new NameSet(); // 创建新的函数名称集合
      this.nullaryFunctionMap = new NameMap<>(); // 创建新的无参函数映射
    } else { // 如果函数映射不为null
      // If you specify functionMap, you must also specify functionNames and
      // nullaryFunctionMap.
      // 如果指定了functionMap，必须同时指定functionNames和nullaryFunctionMap
      this.functionMap = functionMap; // 使用传入的函数映射
      this.functionNames = requireNonNull(functionNames, "functionNames"); // 确保functionNames不为null
      this.nullaryFunctionMap = // 确保nullaryFunctionMap不为null
          requireNonNull(nullaryFunctionMap, "nullaryFunctionMap");
    }
    if (typeMap == null) { // 如果类型映射为null
      this.typeMap = new NameMap<>(); // 创建新的类型映射
    } else { // 如果类型映射不为null
      this.typeMap = typeMap; // 使用传入的类型映射
    }
    this.path = path; // 设置路径
  }

  // 获取表查找对象，返回显式表和隐式表的合并结果
  public Lookup<TableEntry> tables() { // 返回表查找对象
    return this.tables.getOrCompute(() -> // 使用延迟引用获取或计算表查找结果
        Lookup.concat( // 合并两个查找结果
            Lookup.of(this.tableMap), // 显式定义的表
            enhanceLookup(schema.tables().map((s, n) -> tableEntry(n, s))))); // 底层Schema中的隐式表，通过enhanceLookup增强

  }

  // 获取子Schema查找对象，返回显式子Schema和隐式子Schema的合并结果
  public Lookup<CalciteSchema> subSchemas() { // 返回子Schema查找对象
    return subSchemas.getOrCompute(() -> // 使用延迟引用获取或计算子Schema查找结果
        Lookup.concat( // 合并两个查找结果
            Lookup.of(this.subSchemaMap), // 显式定义的子Schema
            enhanceLookup(schema.subSchemas().map((s, n) -> createSubSchema(s, n))))); // 底层Schema中的隐式子Schema

  }

  /** The derived class is able to enhance the lookup e.g. by introducing a cache. */
  // 派生类可以增强查找功能，例如引入缓存
  // 这是一个钩子方法，允许子类对查找结果进行增强处理
  protected <S> Lookup<S> enhanceLookup(Lookup<S> lookup) { // 泛型方法，增强查找结果
    return lookup; // 默认实现直接返回原查找结果，子类可以重写此方法添加缓存等增强功能
  }

  /** Creates a sub-schema with a given name that is defined implicitly. */
  // 创建具有给定名称的隐式定义的子Schema
  // 抽象方法，由子类实现，用于从底层Schema创建隐式的子Schema
  protected abstract CalciteSchema createSubSchema(CalciteSchema this, // 当前CalciteSchema实例
      Schema schema, String name); // 底层Schema对象和名称

  /** Returns a type with a given name that is defined implicitly
   * (that is, by the underlying {@link Schema} object, not explicitly
   * by a call to {@link #add(String, RelProtoDataType)}), or null. */
  // 返回具有给定名称的隐式定义的类型（即由底层Schema对象定义，而非通过add方法显式添加），如果不存在则返回null
  // 抽象方法，由子类实现，用于从底层Schema获取隐式类型
  protected abstract @Nullable TypeEntry getImplicitType(String name, // 类型名称
                                                boolean caseSensitive); // 是否区分大小写

  /** Returns table function with a given name and zero arguments that is
   * defined implicitly (that is, by the underlying {@link Schema} object,
   * not explicitly by a call to {@link #add(String, Function)}), or null. */
  // 返回具有给定名称且无参数的隐式定义的表函数（即由底层Schema对象定义，而非通过add方法显式添加），如果不存在则返回null
  // 抽象方法，由子类实现，用于从底层Schema获取隐式的无参表函数
  protected abstract @Nullable TableEntry getImplicitTableBasedOnNullaryFunction(String tableName, // 表名
      boolean caseSensitive); // 是否区分大小写

  /** Adds implicit functions to a builder. */
  // 将隐式函数添加到构建器中
  // 抽象方法，由子类实现，用于从底层Schema收集隐式函数
  protected abstract void addImplicitFunctionsToBuilder( // 方法签名
      ImmutableList.Builder<Function> builder, // 函数列表构建器
      String name, boolean caseSensitive); // 函数名称和是否区分大小写

  /** Adds implicit function names to a builder. */
  // 将隐式函数名称添加到构建器中
  // 抽象方法，由子类实现，用于从底层Schema收集隐式函数名称
  protected abstract void addImplicitFuncNamesToBuilder( // 方法签名
      ImmutableSortedSet.Builder<String> builder); // 字符串有序集合构建器

  /** Adds implicit type names to a builder. */
  // 将隐式类型名称添加到构建器中
  // 抽象方法，由子类实现，用于从底层Schema收集隐式类型名称
  protected abstract void addImplicitTypeNamesToBuilder( // 方法签名
      ImmutableSortedSet.Builder<String> builder); // 字符串有序集合构建器

  /** Adds implicit table functions to a builder. */
  // 将隐式表函数添加到构建器中
  // 抽象方法，由子类实现，用于从底层Schema收集隐式的无参表函数
  protected abstract void addImplicitTablesBasedOnNullaryFunctionsToBuilder( // 方法签名
      ImmutableSortedMap.Builder<String, Table> builder); // 有序映射构建器，键为表名，值为Table对象

  /** Returns a snapshot representation of this CalciteSchema. */
  // 返回此CalciteSchema的快照表示
  // 抽象方法，由子类实现，用于创建Schema的不可变快照
  protected abstract CalciteSchema snapshot( // 方法签名
      @Nullable CalciteSchema parent, SchemaVersion version); // 父Schema和Schema版本

  protected abstract boolean isCacheEnabled(); // 抽象方法，判断是否启用了缓存

  public abstract void setCache(boolean cache); // 抽象方法，设置是否启用缓存

  /** Creates a TableEntryImpl with no SQLs. */
  // 创建没有SQL语句的TableEntryImpl对象
  // 受保护方法，用于创建表条目
  protected TableEntryImpl tableEntry(String name, Table table) { // 表名和表对象
    return new TableEntryImpl(this, name, table, ImmutableList.of()); // 创建TableEntryImpl实例，SQL列表为空
  }

  /** Creates a TableEntryImpl with no SQLs. */
  // 创建没有SQL语句的TypeEntryImpl对象
  // 受保护方法，用于创建类型条目
  protected TypeEntryImpl typeEntry(String name, RelProtoDataType relProtoDataType) { // 类型名和关系原型数据类型
    return new TypeEntryImpl(this, name, relProtoDataType); // 创建TypeEntryImpl实例
  }

  /** Defines a table within this schema. */
  // 在此Schema中定义一个表
  // 公共方法，用于添加表到Schema中
  public TableEntry add(String tableName, Table table) { // 表名和表对象
    return add(tableName, table, ImmutableList.of()); // 调用重载方法，SQL列表为空
  }

  /** Defines a table within this schema. */
  // 在此Schema中定义一个表（带SQL语句列表）
  // 公共方法，用于添加表到Schema中，并关联SQL语句
  public TableEntry add(String tableName, Table table, // 表名和表对象
      ImmutableList<String> sqls) { // SQL语句列表
    final TableEntryImpl entry = // 创建表条目实现
        new TableEntryImpl(this, tableName, table, sqls); // 使用当前Schema、表名、表对象和SQL列表创建
    tableMap.put(tableName, entry); // 将表条目添加到表映射中
    return entry; // 返回创建的表条目
  }

  /** Defines a type within this schema. */
  // 在此Schema中定义一个类型
  // 公共方法，用于添加自定义数据类型到Schema中
  public TypeEntry add(String name, RelProtoDataType type) { // 类型名和关系原型数据类型
    final TypeEntry entry = // 创建类型条目
        new TypeEntryImpl(this, name, type); // 使用当前Schema、类型名和数据类型创建
    typeMap.put(name, entry); // 将类型条目添加到类型映射中
    return entry; // 返回创建的类型条目
  }

  // 私有方法：添加函数到Schema中
  private FunctionEntry add(String name, Function function) { // 函数名和函数对象
    final FunctionEntryImpl entry = // 创建函数条目实现
        new FunctionEntryImpl(this, name, function); // 使用当前Schema、函数名和函数对象创建
    functionMap.put(name, entry); // 将函数条目添加到函数映射中
    functionNames.add(name); // 将函数名添加到函数名称集合中
    if (function.getParameters().isEmpty()) { // 如果函数没有参数
      nullaryFunctionMap.put(name, entry); // 将函数添加到无参函数映射中（可作为表使用）
    }
    return entry; // 返回创建的函数条目
  }

  // 私有方法：添加Lattice到Schema中
  private LatticeEntry add(String name, Lattice lattice) { // Lattice名称和Lattice对象
    if (latticeMap.containsKey(name, false)) { // 如果已存在同名Lattice（不区分大小写）
      throw new RuntimeException("Duplicate lattice '" + name + "'"); // 抛出运行时异常
    }
    final LatticeEntryImpl entry = new LatticeEntryImpl(this, name, lattice); // 创建Lattice条目实现
    latticeMap.put(name, entry); // 将Lattice条目添加到Lattice映射中
    return entry; // 返回创建的Lattice条目
  }

  // 获取根Schema
  // 公共方法，沿着父Schema链向上查找直到找到根Schema（parent为null的Schema）
  public CalciteSchema root() { // 方法签名
    for (CalciteSchema schema = this;;) { // 从当前Schema开始循环
      if (schema.parent == null) { // 如果父Schema为null，说明是根Schema
        return schema; // 返回根Schema
      }
      schema = schema.parent; // 否则继续向上查找父Schema
    }
  }

  /** Returns whether this is a root schema. */
  // 返回此Schema是否为根Schema
  // 公共方法，通过判断parent是否为null来确定
  public boolean isRoot() { // 方法签名
    return parent == null; // 如果父Schema为null，则是根Schema
  }

  /** Returns the path of an object in this schema. */
  // 返回此Schema中对象的路径
  // 公共方法，构建从根Schema到当前Schema（及对象）的完整路径
  public List<String> path(@Nullable String name) { // 对象名称（可为null）
    final List<String> list = new ArrayList<>(); // 创建路径列表
    if (name != null) { // 如果对象名称不为null
      list.add(name); // 将对象名称添加到路径末尾
    }
    for (CalciteSchema s = this; s != null; s = s.parent) { // 从当前Schema向上遍历到根Schema
      if (s.parent != null || !s.name.equals("")) { // 如果不是根Schema，或者是根Schema但名称不为空字符串
        // Omit the root schema's name from the path if it's the empty string,
        // which it usually is.
        // 如果根Schema的名称是空字符串（通常情况），则从路径中省略根Schema的名称
        list.add(s.name); // 将Schema名称添加到路径中
      }
    }
    return ImmutableList.copyOf(list).reverse(); // 创建不可变副本并反转顺序（从根到当前）
  }

  // 获取指定名称的子Schema
  // 公共方法，根据名称和大小写敏感性获取子Schema
  public final @Nullable CalciteSchema getSubSchema(String schemaName, // Schema名称
      boolean caseSensitive) { // 是否区分大小写
    return caseSensitive // 根据大小写敏感性选择查找方式
        ? subSchemas().get(schemaName) // 如果区分大小写，直接获取
        : Named.entityOrNull(subSchemas().getIgnoreCase(schemaName)); // 如果不区分大小写，使用忽略大小写的方式获取
  }

  /** Adds a child schema of this schema. */
  // 添加此Schema的子Schema
  // 抽象方法，由子类实现，用于添加子Schema
  public abstract CalciteSchema add(String name, Schema schema); // Schema名称和Schema对象

  /** Returns a table that materializes the given SQL statement. */
  // 返回物化给定SQL语句的表
  // 公共方法，在显式定义的表中查找包含指定SQL语句的表
  public final @Nullable TableEntry getTableBySql(String sql) { // SQL语句字符串
    for (TableEntry tableEntry : tableMap.map().values()) { // 遍历所有显式定义的表
      if (tableEntry.sqls.contains(sql)) { // 如果表的SQL列表包含指定的SQL语句
        return tableEntry; // 返回该表条目
      }
    }
    return null; // 如果没有找到，返回null
  }

  /** Returns a table with the given name. Does not look for views. */
  // 返回具有给定名称的表。不查找视图
  // 公共方法，根据名称和大小写敏感性获取表条目
  public final @Nullable TableEntry getTable(String tableName, boolean caseSensitive) { // 表名和是否区分大小写
    return Lookup.get(tables(), tableName, caseSensitive); // 使用Lookup工具从表集合中获取表
  }

  // 获取Schema名称
  // 公共方法，返回此Schema的名称
  public String getName() { // 方法签名
    return name; // 返回Schema名称
  }

  // 获取SchemaPlus对象
  // 公共方法，返回此CalciteSchema的SchemaPlus包装器
  public SchemaPlus plus() { // 方法签名
    return new SchemaPlusImpl(); // 创建并返回SchemaPlusImpl实例
  }

  // 从SchemaPlus获取CalciteSchema
  // 静态公共方法，将SchemaPlus转换回CalciteSchema
  public static CalciteSchema from(SchemaPlus plus) { // SchemaPlus对象
    return ((SchemaPlusImpl) plus).calciteSchema(); // 强制转换为SchemaPlusImpl并获取其CalciteSchema
  }

  /** Returns the default path resolving functions from this schema.
   *
   * <p>The path consists is a list of lists of strings.
   * Each list of strings represents the path of a schema from the root schema.
   * For example, [[], [foo], [foo, bar, baz]] represents three schemas: the
   * root schema "/" (level 0), "/foo" (level 1) and "/foo/bar/baz" (level 3).
   *
   * @return Path of this schema; never null, may be empty
   */
  // 返回从此Schema解析函数的默认路径
  //
  // <p>路径由字符串列表的列表组成。
  // 每个字符串列表代表从根Schema到某个Schema的路径。
  // 例如，[[], [foo], [foo, bar, baz]] 表示三个Schema：
  // 根Schema "/"（第0级）、"/foo"（第1级）和"/foo/bar/baz"（第3级）。
  //
  // @return 此Schema的路径；永不为null，可能为空
  public List<? extends List<String>> getPath() { // 方法签名
    if (path != null) { // 如果路径已设置
      return path; // 返回已设置的路径
    }
    // Return a path consisting of just this schema.
    // 返回仅包含此Schema的路径
    return ImmutableList.of(path(null)); // 返回包含当前Schema路径的不可变列表
  }

  /** Returns a collection of sub-schemas, both explicit (defined using
   * {@link #add(String, org.apache.calcite.schema.Schema)}) and implicit. */
  // 返回子Schema集合，包括显式定义的（通过add方法）和隐式的
  // 公共方法，获取所有子Schema的有序映射
  public final NavigableMap<String, CalciteSchema> getSubSchemaMap() { // 方法签名
    final ImmutableSortedMap.Builder<String, CalciteSchema> builder = // 创建有序映射构建器
        new ImmutableSortedMap.Builder<>(NameSet.COMPARATOR); // 使用NameSet的比较器（支持大小写不敏感比较）
    final Lookup<CalciteSchema> schemas = subSchemas(); // 获取子Schema查找对象
    for (String name : schemas.getNames(LikePattern.any())) { // 遍历所有子Schema名称（使用任意模式匹配）
      builder.put(name, requireNonNull(schemas.get(name))); // 将Schema名称和Schema对象添加到构建器中
    }
    return builder.build(); // 构建并返回不可变的有序映射
  }

  /** Returns a collection of lattices.
   *
   * <p>All are explicit (defined using {@link #add(String, Lattice)}). */
  // 返回Lattice集合
  //
  // <p>所有都是显式定义的（通过add方法）
  // 公共方法，获取所有Lattice的有序映射
  public NavigableMap<String, LatticeEntry> getLatticeMap() { // 方法签名
    return ImmutableSortedMap.copyOf(latticeMap.map()); // 返回latticeMap映射的不可变有序副本
  }

  /** Returns the set of all table names. Includes implicit and explicit tables
   * and functions with zero parameters. */
  // 返回所有表名称的集合。包括隐式和显式表以及无参函数
  // 公共方法，获取所有表名称（包括通过无参函数创建的表）
  public final Set<String> getTableNames() { // 方法签名
    return getTableNames(LikePattern.any()); // 调用重载方法，使用任意模式匹配所有表名
  }

  /** Returns the set of table names filtered by the given pattern.
   * Includes implicit and explicit tables and functions with zero parameters. */
  // 返回根据给定模式过滤的表名称集合。包括隐式和显式表以及无参函数
  // 公共方法，根据模式匹配获取表名称
  public final Set<String> getTableNames(LikePattern pattern) { // 模式对象
    return tables().getNames(pattern); // 使用Lookup工具从表集合中获取匹配模式的表名
  }

  /** Returns the set of all types names. */
  // 返回所有类型名称的集合
  // 公共方法，获取所有类型名称（包括显式和隐式类型）
  public final NavigableSet<String> getTypeNames() { // 方法签名
    final ImmutableSortedSet.Builder<String> builder = // 创建有序集合构建器
        new ImmutableSortedSet.Builder<>(NameSet.COMPARATOR); // 使用NameSet的比较器
    // Add explicit types.
    // 添加显式类型
    builder.addAll(typeMap.map().keySet()); // 将显式类型映射中的所有键（类型名）添加到构建器
    // Add implicit types.
    // 添加隐式类型
    addImplicitTypeNamesToBuilder(builder); // 调用抽象方法，让子类添加隐式类型名称
    return builder.build(); // 构建并返回不可变的有序集合
  }

  /** Returns a type, explicit and implicit, with a given
   * name. Never null. */
  // 返回具有给定名称的类型，包括显式和隐式类型。如果不存在则返回null
  // 公共方法，根据名称和大小写敏感性获取类型条目
  public final @Nullable TypeEntry getType(String name, boolean caseSensitive) { // 类型名和是否区分大小写
    for (Map.Entry<String, TypeEntry> entry // 遍历显式类型映射中匹配名称的条目
        : typeMap.range(name, caseSensitive).entrySet()) { // 使用range方法获取匹配的条目范围
      return entry.getValue(); // 返回找到的类型条目
    }
    return getImplicitType(name, caseSensitive); // 如果显式类型中没找到，查找隐式类型
  }

  /** Returns a collection of all functions, explicit and implicit, with a given
   * name. Never null. */
  // 返回具有给定名称的所有函数集合，包括显式和隐式函数。如果不存在则返回空集合
  // 公共方法，根据名称和大小写敏感性获取函数集合（支持函数重载）
  public final Collection<Function> getFunctions(String name, boolean caseSensitive) { // 函数名和是否区分大小写
    final ImmutableList.Builder<Function> builder = ImmutableList.builder(); // 创建函数列表构建器
    // Add explicit functions.
    // 添加显式函数
    for (FunctionEntry functionEntry // 遍历显式函数映射中匹配名称的条目
        : Pair.right(functionMap.range(name, caseSensitive))) { // 使用Pair.right提取条目值
      builder.add(functionEntry.getFunction()); // 将函数对象添加到构建器
    }
    // Add implicit functions.
    // 添加隐式函数
    addImplicitFunctionsToBuilder(builder, name, caseSensitive); // 调用抽象方法，让子类添加隐式函数
    return builder.build(); // 构建并返回不可变的函数列表
  }

  /** Returns the list of function names in this schema, both implicit and
   * explicit, never null. */
  // 返回此Schema中的函数名称列表，包括隐式和显式函数，永不为null
  // 公共方法，获取所有函数名称
  public final NavigableSet<String> getFunctionNames() { // 方法签名
    final ImmutableSortedSet.Builder<String> builder = // 创建有序集合构建器
        new ImmutableSortedSet.Builder<>(NameSet.COMPARATOR); // 使用NameSet的比较器
    // Add explicit functions, case-sensitive.
    // 添加显式函数（区分大小写）
    builder.addAll(functionMap.map().keySet()); // 将显式函数映射中的所有键（函数名）添加到构建器
    // Add implicit functions, case-sensitive.
    // 添加隐式函数（区分大小写）
    addImplicitFuncNamesToBuilder(builder); // 调用抽象方法，让子类添加隐式函数名称
    return builder.build(); // 构建并返回不可变的有序集合
  }

  /** Returns tables derived from explicit and implicit functions
   * that take zero parameters. */
  // 返回从显式和隐式的无参函数派生的表
  // 公共方法，获取所有由无参函数（TableMacro）创建的表
  public final NavigableMap<String, Table> getTablesBasedOnNullaryFunctions() { // 方法签名
    ImmutableSortedMap.Builder<String, Table> builder = // 创建有序映射构建器
        new ImmutableSortedMap.Builder<>(NameSet.COMPARATOR); // 使用NameSet的比较器
    for (Map.Entry<String, FunctionEntry> entry // 遍历无参函数映射中的所有条目
        : nullaryFunctionMap.map().entrySet()) { // 获取所有无参函数
      final Function function = entry.getValue().getFunction(); // 获取函数对象
      if (function instanceof TableMacro) { // 如果函数是表宏（返回表的函数）
        assert function.getParameters().isEmpty(); // 断言函数没有参数
        final Table table = ((TableMacro) function).apply(ImmutableList.of()); // 调用表宏，传入空参数列表，获取表
        builder.put(entry.getKey(), table); // 将函数名和表对象添加到构建器
      }
    }
    // add tables derived from implicit functions
    // 添加从隐式函数派生的表
    addImplicitTablesBasedOnNullaryFunctionsToBuilder(builder); // 调用抽象方法，让子类添加隐式无参表函数
    return builder.build(); // 构建并返回不可变的有序映射
  }

  /** Returns a tables derived from explicit and implicit functions
   * that take zero parameters. */
  // 返回从显式和隐式的无参函数派生的表
  // 公共方法，根据名称和大小写敏感性获取由无参函数创建的表条目
  public final @Nullable TableEntry getTableBasedOnNullaryFunction(String tableName, // 表名
      boolean caseSensitive) { // 是否区分大小写
    for (Map.Entry<String, FunctionEntry> entry // 遍历无参函数映射中匹配名称的条目
        : nullaryFunctionMap.range(tableName, caseSensitive).entrySet()) { // 使用range方法获取匹配的条目范围
      final Function function = entry.getValue().getFunction(); // 获取函数对象
      if (function instanceof TableMacro) { // 如果函数是表宏
        assert function.getParameters().isEmpty(); // 断言函数没有参数
        final Table table = ((TableMacro) function).apply(ImmutableList.of()); // 调用表宏获取表
        return tableEntry(tableName, table); // 创建并返回表条目
      }
    }
    return getImplicitTableBasedOnNullaryFunction(tableName, caseSensitive); // 如果显式函数中没找到，查找隐式函数
  }

  /** Creates a snapshot of this CalciteSchema as of the specified time. All
   * explicit objects in this CalciteSchema will be copied into the snapshot
   * CalciteSchema, while the contents of the snapshot of the underlying schema
   * should not change as specified in {@link Schema#snapshot(SchemaVersion)}.
   * Snapshots of explicit sub schemas will be created and copied recursively.
   *
   * <p>Currently, to accommodate the requirement of creating tables on the fly
   * for materializations, the snapshot will still use the same table map and
   * lattice map as in the original CalciteSchema instead of making copies.
   *
   * @param version The current schema version
   *
   * @return the schema snapshot.
   */
  // 创建此CalciteSchema在指定时间的快照。此CalciteSchema中的所有显式对象将被复制到快照CalciteSchema中，
  // 而底层Schema的快照内容不应改变，如Schema#snapshot(SchemaVersion)中所指定。
  // 显式子Schema的快照将被递归创建和复制。
  //
  // <p>目前，为了满足物化视图动态创建表的需求，快照仍将使用与原始CalciteSchema相同的表映射和Lattice映射，
  // 而不是创建副本。
  //
  // @param version 当前的Schema版本
  //
  // @return Schema快照
  public CalciteSchema createSnapshot(SchemaVersion version) { // Schema版本对象
    checkArgument(this.isRoot(), "must be root schema"); // 检查必须是根Schema，否则抛出异常
    return snapshot(null, version); // 调用抽象方法创建快照，父Schema为null
  }

  /** Returns a subset of a map whose keys match the given string
   * case-insensitively.
   *
   * @deprecated use NameMap
   */
  // 返回映射的子集，其键与给定字符串不区分大小写匹配
  //
  // @deprecated 使用NameMap
  @Deprecated // to be removed before 2.0 // 标记为过时，将在2.0版本前移除
  protected static <V> NavigableMap<String, V> find(NavigableMap<String, V> map, // 映射对象
      String s) { // 要匹配的字符串
    return NameMap.immutableCopyOf(map).range(s, false); // 创建映射的不可变副本并返回不区分大小写的范围
  }

  /** Returns a subset of a set whose values match the given string
   * case-insensitively.
   *
   * @deprecated use NameSet
   */
  // 返回集合的子集，其值与给定字符串不区分大小写匹配
  //
  // @deprecated 使用NameSet
  @Deprecated // to be removed before 2.0 // 标记为过时，将在2.0版本前移除
  protected static Iterable<String> find(NavigableSet<String> set, String name) { // 集合对象和要匹配的字符串
    return NameSet.immutableCopyOf(set).range(name, false); // 创建集合的不可变副本并返回不区分大小写的范围
  }

  /** Creates a root schema.
   *
   * <p>When <code>addMetadataSchema</code> argument is true adds a "metadata"
   * schema containing definitions of tables, columns etc. to root schema.
   * By default, creates a {@link CachingCalciteSchema}.
   */
  // 创建根Schema
  //
  // <p>当addMetadataSchema参数为true时，向根Schema添加一个"metadata" Schema，
  // 其中包含表、列等的定义。
  // 默认情况下，创建CachingCalciteSchema（带缓存的Schema）。
  public static CalciteSchema createRootSchema(boolean addMetadataSchema) { // 是否添加元数据Schema
    return createRootSchema(addMetadataSchema, true); // 调用重载方法，默认启用缓存
  }

  /** Creates a root schema.
   *
   * @param addMetadataSchema Whether to add a "metadata" schema containing
   *              definitions of tables, columns etc.
   * @param cache If true create {@link CachingCalciteSchema};
   *                if false create {@link SimpleCalciteSchema}
   */
  // 创建根Schema
  //
  // @param addMetadataSchema 是否添加包含表、列等定义的"metadata" Schema
  // @param cache 如果为true创建CachingCalciteSchema；如果为false创建SimpleCalciteSchema
  public static CalciteSchema createRootSchema(boolean addMetadataSchema, // 是否添加元数据Schema
      boolean cache) { // 是否启用缓存
    return createRootSchema(addMetadataSchema, cache, ""); // 调用重载方法，Schema名称为空字符串
  }

  /** Creates a root schema.
   *
   * @param addMetadataSchema Whether to add a "metadata" schema containing
   *              definitions of tables, columns etc.
   * @param cache If true create {@link CachingCalciteSchema};
   *                if false create {@link SimpleCalciteSchema}
   * @param name Schema name
   */
  // 创建根Schema
  //
  // @param addMetadataSchema 是否添加包含表、列等定义的"metadata" Schema
  // @param cache 如果为true创建CachingCalciteSchema；如果为false创建SimpleCalciteSchema
  // @param name Schema名称
  public static CalciteSchema createRootSchema(boolean addMetadataSchema, // 是否添加元数据Schema
      boolean cache, String name) { // 是否启用缓存和Schema名称
    final Schema rootSchema = new CalciteConnectionImpl.RootSchema(); // 创建Calcite连接的根Schema对象
    return createRootSchema(addMetadataSchema, cache, name, rootSchema); // 调用重载方法，传入Schema对象
  }

  @Experimental // 标记为实验性API
  public static CalciteSchema createRootSchema(boolean addMetadataSchema, // 是否添加元数据Schema
      boolean cache, String name, Schema schema) { // 是否启用缓存、Schema名称和Schema对象
    CalciteSchema rootSchema; // 声明根Schema变量
    if (cache) { // 如果启用缓存
      rootSchema = new CachingCalciteSchema(null, schema, name); // 创建带缓存的CalciteSchema
    } else { // 如果不启用缓存
      rootSchema = new SimpleCalciteSchema(null, schema, name); // 创建简单的CalciteSchema
    }
    if (addMetadataSchema) { // 如果需要添加元数据Schema
      rootSchema.add("metadata", MetadataSchema.INSTANCE); // 添加名为"metadata"的元数据Schema
    }
    return rootSchema; // 返回创建的根Schema
  }

  @Experimental // 标记为实验性API
  public boolean removeSubSchema(String name) { // 移除子Schema的方法
    return subSchemaMap.remove(name) != null; // 从子Schema映射中移除指定名称的Schema，返回是否成功
  }

  @Experimental // 标记为实验性API
  public boolean removeTable(String name) { // 移除表的方法
    return tableMap.remove(name) != null; // 从表映射中移除指定名称的表，返回是否成功
  }

  @Experimental // 标记为实验性API
  public boolean removeFunction(String name) { // 移除函数的方法
    final FunctionEntry remove = nullaryFunctionMap.remove(name); // 从无参函数映射中移除指定名称的函数
    if (remove == null) { // 如果函数不存在
      return false; // 返回失败
    }
    functionMap.remove(name, remove); // 从函数映射中移除该函数
    return true; // 返回成功
  }

  @Experimental // 标记为实验性API
  public boolean removeType(String name) { // 移除类型的方法
    return typeMap.remove(name) != null; // 从类型映射中移除指定名称的类型，返回是否成功
  }

  /**
   * Entry in a schema, such as a table or sub-schema.
   * Schema中的条目，例如表或子Schema
   *
   * <p>Each object's name is a property of its membership in a schema;
   * therefore in principle it could belong to several schemas, or
   * even the same schema several times, with different names. In this
   * respect, it is like an inode in a Unix file system.
   *
   * <p>每个对象的名称是其所属Schema的属性；
   * 因此原则上它可以属于多个Schema，甚至可以多次属于同一个Schema，但名称不同。
   * 在这方面，它就像Unix文件系统中的inode。
   *
   * <p>The members of a schema must have unique names.
   *
   * <p>Schema的成员必须具有唯一的名称。
   */
  public abstract static class Entry { // 抽象静态内部类，表示Schema中的条目
    public final CalciteSchema schema; // 条目所属的CalciteSchema
    public final String name; // 条目的名称

    protected Entry(CalciteSchema schema, String name) { // 构造方法
      this.schema = requireNonNull(schema, "schema"); // 设置所属Schema，确保不为null
      this.name = requireNonNull(name, "name"); // 设置名称，确保不为null
    }

    /** Returns this object's path. For example ["hr", "emps"]. */
    // 返回此对象的路径。例如["hr", "emps"]
    public final List<String> path() { // 方法签名
      return schema.path(name); // 调用所属Schema的path方法，传入此条目的名称
    }
  }

  /** Membership of a table in a schema. */
  // 表在Schema中的成员资格（表条目）
  public abstract static class TableEntry extends Entry { // 抽象静态内部类，继承自Entry，表示表条目
    public final ImmutableList<String> sqls; // 与此表关联的SQL语句列表（用于物化视图）

    protected TableEntry(CalciteSchema schema, String name, // 构造方法
        ImmutableList<String> sqls) { // SQL语句列表
      super(schema, name); // 调用父类构造方法
      this.sqls = requireNonNull(sqls, "sqls"); // 设置SQL列表，确保不为null
    }

    public abstract Table getTable(); // 抽象方法，获取表对象
  }

  /** Membership of a type in a schema. */
  // 类型在Schema中的成员资格（类型条目）
  public abstract static class TypeEntry extends Entry { // 抽象静态内部类，继承自Entry，表示类型条目
    protected TypeEntry(CalciteSchema schema, String name) { // 构造方法
      super(schema, name); // 调用父类构造方法
    }

    public abstract RelProtoDataType getType(); // 抽象方法，获取关系原型数据类型
  }

  /** Membership of a function in a schema. */
  // 函数在Schema中的成员资格（函数条目）
  public abstract static class FunctionEntry extends Entry { // 抽象静态内部类，继承自Entry，表示函数条目
    protected FunctionEntry(CalciteSchema schema, String name) { // 构造方法
      super(schema, name); // 调用父类构造方法
    }

    public abstract Function getFunction(); // 抽象方法，获取函数对象

    /** Whether this represents a materialized view. (At a given point in time,
     * it may or may not be materialized as a table.) */
    // 此条目是否代表物化视图。（在给定时间点，它可能已物化为表，也可能未物化）
    public abstract boolean isMaterialization(); // 抽象方法，判断是否为物化视图
  }

  /** Membership of a lattice in a schema. */
  // Lattice在Schema中的成员资格（Lattice条目）
  public abstract static class LatticeEntry extends Entry { // 抽象静态内部类，继承自Entry，表示Lattice条目
    protected LatticeEntry(CalciteSchema schema, String name) { // 构造方法
      super(schema, name); // 调用父类构造方法
    }

    public abstract Lattice getLattice(); // 抽象方法，获取Lattice对象

    public abstract TableEntry getStarTable(); // 抽象方法，获取星型表条目
  }

  /** Implementation of {@link SchemaPlus} based on a
   * {@link org.apache.calcite.jdbc.CalciteSchema}. */
  // 基于CalciteSchema的SchemaPlus接口实现
  // 私有内部类，实现了SchemaPlus接口，为CalciteSchema提供扩展功能
  private class SchemaPlusImpl implements SchemaPlus { // 类声明
    CalciteSchema calciteSchema() { // 获取底层CalciteSchema的方法
      return CalciteSchema.this; // 返回外部类实例
    }

    @Override public @Nullable SchemaPlus getParentSchema() { // 重写方法：获取父Schema
      return parent == null ? null : parent.plus(); // 如果父Schema为null返回null，否则返回父Schema的SchemaPlus包装器
    }

    @Override public String getName() { // 重写方法：获取Schema名称
      return CalciteSchema.this.getName(); // 返回外部类的名称
    }

    @Override public boolean isMutable() { // 重写方法：判断Schema是否可变
      return schema.isMutable(); // 返回底层Schema的可变性
    }

    @Override public void setCacheEnabled(boolean cache) { // 重写方法：设置是否启用缓存
      CalciteSchema.this.setCache(cache); // 调用外部类的setCache方法
    }

    @Override public boolean isCacheEnabled() { // 重写方法：判断是否启用了缓存
      return CalciteSchema.this.isCacheEnabled(); // 调用外部类的isCacheEnabled方法
    }

    @Override public Schema snapshot(SchemaVersion version) { // 重写方法：创建Schema快照
      throw new UnsupportedOperationException(); // 抛出不支持操作异常
    }

    @Override public Expression getExpression(@Nullable SchemaPlus parentSchema, String name) { // 重写方法：获取表达式
      return schema.getExpression(parentSchema, name); // 调用底层Schema的getExpression方法
    }

    @Override public Lookup<Table> tables() { // 重写方法：获取表查找对象
      return CalciteSchema.this.tables().map((table, name) -> table.getTable()); // 将TableEntry转换为Table对象
    }

    @Override public Lookup<? extends SchemaPlus> subSchemas() { // 重写方法：获取子Schema查找对象
      return CalciteSchema.this.subSchemas().map((schema, name) -> schema.plus()); // 将CalciteSchema转换为SchemaPlus对象
    }

    @Deprecated @Override public @Nullable Table getTable(String name) { // 重写方法：获取表（已过时）
      final TableEntry entry = CalciteSchema.this.getTable(name, true); // 获取表条目（区分大小写）
      return entry == null ? null : entry.getTable(); // 如果条目为null返回null，否则返回表对象
    }

    @Deprecated @Override public Set<String> getTableNames() { // 重写方法：获取表名称集合（已过时）
      return CalciteSchema.this.getTableNames(LikePattern.any()); // 获取所有表名称
    }

    @Override public @Nullable RelProtoDataType getType(String name) { // 重写方法：获取类型
      final TypeEntry entry = CalciteSchema.this.getType(name, true); // 获取类型条目（区分大小写）
      return entry == null ? null : entry.getType(); // 如果条目为null返回null，否则返回类型对象
    }

    @Override public Set<String> getTypeNames() { // 重写方法：获取类型名称集合
      return CalciteSchema.this.getTypeNames(); // 调用外部类的getTypeNames方法
    }

    @Override public Collection<Function> getFunctions(String name) { // 重写方法：获取函数集合
      return CalciteSchema.this.getFunctions(name, true); // 调用外部类的getFunctions方法（区分大小写）
    }

    @Override public NavigableSet<String> getFunctionNames() { // 重写方法：获取函数名称集合
      return CalciteSchema.this.getFunctionNames(); // 调用外部类的getFunctionNames方法
    }

    @Deprecated @Override public @Nullable SchemaPlus getSubSchema(String name) { // 重写方法：获取子Schema（已过时）
      return subSchemas().get(name); // 从子Schema查找中获取指定名称的SchemaPlus
    }

    @Deprecated @Override public Set<String> getSubSchemaNames() { // 重写方法：获取子Schema名称集合（已过时）
      return subSchemas().getNames(LikePattern.any()); // 获取所有子Schema名称
    }

    @Override public SchemaPlus add(String name, Schema schema) { // 重写方法：添加子Schema
      final CalciteSchema calciteSchema = CalciteSchema.this.add(name, schema); // 调用外部类的add方法
      return calciteSchema.plus(); // 返回新Schema的SchemaPlus包装器
    }

    @Override public <T extends Object> T unwrap(Class<T> clazz) { // 重写方法：解包为指定类型
      if (clazz.isInstance(this)) { // 如果此类是指定类型的实例
        return clazz.cast(this); // 强制转换并返回
      }
      if (clazz.isInstance(CalciteSchema.this)) { // 如果外部类是指定类型的实例
        return clazz.cast(CalciteSchema.this); // 强制转换并返回
      }
      if (clazz.isInstance(CalciteSchema.this.schema)) { // 如果底层Schema是指定类型的实例
        return clazz.cast(CalciteSchema.this.schema); // 强制转换并返回
      }
      if (schema instanceof Wrapper) { // 如果底层Schema实现了Wrapper接口
        return ((Wrapper) schema).unwrapOrThrow(clazz); // 调用Wrapper的解包方法
      }
      throw new ClassCastException("not a " + clazz); // 抛出类型转换异常
    }

    @Override public void setPath(ImmutableList<ImmutableList<String>> path) { // 重写方法：设置路径
      CalciteSchema.this.path = path; // 设置外部类的路径
    }

    @Override public void add(String name, Table table) { // 重写方法：添加表
      CalciteSchema.this.add(name, table); // 调用外部类的add方法
    }

    @Override public boolean removeTable(String name) { // 重写方法：移除表
      return CalciteSchema.this.removeTable(name); // 调用外部类的removeTable方法
    }

    @Override public void add(String name, Function function) { // 重写方法：添加函数
      CalciteSchema.this.add(name, function); // 调用外部类的add方法
    }

    @Override public void add(String name, RelProtoDataType type) { // 重写方法：添加类型
      CalciteSchema.this.add(name, type); // 调用外部类的add方法
    }

    @Override public void add(String name, Lattice lattice) { // 重写方法：添加Lattice
      CalciteSchema.this.add(name, lattice); // 调用外部类的add方法
    }
  }

  /**
   * Implementation of {@link CalciteSchema.TableEntry}
   * where all properties are held in fields.
   * CalciteSchema.TableEntry的实现，其中所有属性都保存在字段中
   */
  public static class TableEntryImpl extends TableEntry { // 公共静态内部类，实现TableEntry
    private final Table table; // 表对象

    /** Creates a TableEntryImpl. */
    // 创建TableEntryImpl
    public TableEntryImpl(CalciteSchema schema, String name, Table table, // 构造方法
        ImmutableList<String> sqls) { // SQL语句列表
      super(schema, name, sqls); // 调用父类构造方法
      this.table = requireNonNull(table, "table"); // 设置表对象，确保不为null
    }

    @Override public Table getTable() { // 重写方法：获取表对象
      return table; // 返回表对象
    }
  }

  /**
   * Implementation of {@link TypeEntry}
   * where all properties are held in fields.
   * TypeEntry的实现，其中所有属性都保存在字段中
   */
  public static class TypeEntryImpl extends TypeEntry { // 公共静态内部类，实现TypeEntry
    private final RelProtoDataType protoDataType; // 关系原型数据类型

    /** Creates a TypeEntryImpl. */
    // 创建TypeEntryImpl
    public TypeEntryImpl(CalciteSchema schema, String name, RelProtoDataType protoDataType) { // 构造方法
      super(schema, name); // 调用父类构造方法
      this.protoDataType = protoDataType; // 设置关系原型数据类型
    }

    @Override public RelProtoDataType getType() { // 重写方法：获取类型对象
      return protoDataType; // 返回关系原型数据类型
    }
  }

  /**
   * Implementation of {@link FunctionEntry}
   * where all properties are held in fields.
   * FunctionEntry的实现，其中所有属性都保存在字段中
   */
  public static class FunctionEntryImpl extends FunctionEntry { // 公共静态内部类，实现FunctionEntry
    private final Function function; // 函数对象

    /** Creates a FunctionEntryImpl. */
    // 创建FunctionEntryImpl
    public FunctionEntryImpl(CalciteSchema schema, String name, // 构造方法
        Function function) { // 函数对象
      super(schema, name); // 调用父类构造方法
      this.function = function; // 设置函数对象
    }

    @Override public Function getFunction() { // 重写方法：获取函数对象
      return function; // 返回函数对象
    }

    @Override public boolean isMaterialization() { // 重写方法：判断是否为物化视图
      return function // 判断函数是否为物化视图表宏
          instanceof MaterializedViewTable.MaterializedViewTableMacro; // 检查是否为物化视图表宏类型
    }
  }

  /**
   * Implementation of {@link LatticeEntry}
   * where all properties are held in fields.
   * LatticeEntry的实现，其中所有属性都保存在字段中
   */
  public static class LatticeEntryImpl extends LatticeEntry { // 公共静态内部类，实现LatticeEntry
    private final Lattice lattice; // Lattice对象
    private final CalciteSchema.TableEntry starTableEntry; // 星型表条目

    /** Creates a LatticeEntryImpl. */
    // 创建LatticeEntryImpl
    public LatticeEntryImpl(CalciteSchema schema, String name, // 构造方法
        Lattice lattice) { // Lattice对象
      super(schema, name); // 调用父类构造方法
      this.lattice = lattice; // 设置Lattice对象

      // Star table has same name as lattice and is in same schema.
      // 星型表与Lattice同名，并且在同一个Schema中
      final StarTable starTable = lattice.createStarTable(); // 从Lattice创建星型表
      starTableEntry = schema.add(name, starTable); // 将星型表添加到Schema中，保存条目引用
    }

    @Override public Lattice getLattice() { // 重写方法：获取Lattice对象
      return lattice; // 返回Lattice对象
    }

    @Override public TableEntry getStarTable() { // 重写方法：获取星型表条目
      return starTableEntry; // 返回星型表条目
    }
  }

} // 类定义结束