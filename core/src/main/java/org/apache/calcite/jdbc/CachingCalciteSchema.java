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
// Apache 许可证头,声明该文件的版权和使用条款
package org.apache.calcite.jdbc; // 声明该类所属的包:org.apache.calcite.jdbc,包含 JDBC 相关的类

import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型接口,用于表示可以在运行时创建的 RelDataType
import org.apache.calcite.schema.Function; // 导入函数接口,表示 SQL 函数
import org.apache.calcite.schema.Schema; // 导入 Schema 接口,表示数据库模式(数据库的逻辑容器)
import org.apache.calcite.schema.SchemaVersion; // 导入 Schema 版本接口,用于支持 Schema 的版本控制
import org.apache.calcite.schema.Table; // 导入 Table 接口,表示数据库表
import org.apache.calcite.schema.TableMacro; // 导入表宏接口,表示可以生成表的特殊函数(无参数的函数可以当作表使用)
import org.apache.calcite.schema.lookup.Lookup; // 导入查找接口,用于按名称查找对象
import org.apache.calcite.schema.lookup.SnapshotLookup; // 导入快照查找接口,提供快照功能的查找实现
import org.apache.calcite.util.NameMap; // 导入名称映射工具类,用于存储名称到对象的映射,支持大小写敏感/不敏感
import org.apache.calcite.util.NameMultimap; // 导入名称多值映射工具类,用于存储名称到多个对象的映射
import org.apache.calcite.util.NameSet; // 导入名称集合工具类,用于存储名称集合,支持大小写敏感/不敏感

import com.google.common.collect.ImmutableList; // 导入 Google Guava 的不可变列表类,提供线程安全的不可变列表实现
import com.google.common.collect.ImmutableSortedMap; // 导入 Google Guava 的不可变排序映射类,提供线程安全的不可变有序映射
import com.google.common.collect.ImmutableSortedSet; // 导入 Google Guava 的不可变排序集合类,提供线程安全的不可变有序集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入 Checker Framework 的可空注解,用于静态空值检查

import java.util.Collection; // 导入 Java 集合接口,表示一组对象
import java.util.List; // 导入 Java 列表接口,表示有序的元素序列
import java.util.concurrent.ConcurrentLinkedDeque; // 导入并发双端队列,提供线程安全的无界队列实现

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入静态方法,用于将可空类型转换为非空类型(在已知非空时使用)

/**
 * Concrete implementation of {@link CalciteSchema} that caches tables,
 * functions and sub-schemas.
 */
// 类文档注释:这是 CalciteSchema 的具体实现,提供了对表、函数和子 Schema 的缓存功能
// 缓存机制可以提高性能,避免重复查询底层的 Schema 对象
class CachingCalciteSchema extends CalciteSchema { // 定义 CachingCalciteSchema 类,继承自 CalciteSchema 基类
  private final ConcurrentLinkedDeque<SnapshotLookup<?>> caches = new ConcurrentLinkedDeque<>(); // 成员变量:存储所有快照查找缓存的并发双端队列,用于统一管理所有缓存对象
  private final Cached<NameSet> implicitFunctionCache; // 成员变量:隐式函数名称集合的缓存,用于缓存从底层 Schema 获取的函数名称
  private final Cached<NameSet> implicitTypeCache; // 成员变量:隐式类型名称集合的缓存,用于缓存从底层 Schema 获取的类型名称

  private boolean cache = true; // 成员变量:缓存开关标志,true 表示启用缓存,false 表示禁用缓存

  /** Creates a CachingCalciteSchema. */
  // 方法文档注释:创建一个 CachingCalciteSchema 实例的构造方法
  CachingCalciteSchema(@Nullable CalciteSchema parent, Schema schema, // 构造方法定义,接收父 Schema、底层 Schema 和名称参数
      String name) { // 参数 name:当前 Schema 的名称
    this(parent, schema, name, null, null, null, null, null, null, null, null); // 调用全参数构造方法,所有映射和集合参数初始化为 null
  } // 构造方法结束

  @SuppressWarnings({"argument.type.incompatible", "return.type.incompatible"}) // 忽略类型不兼容的警告,这是由于泛型类型擦除导致的编译器警告
  private CachingCalciteSchema(@Nullable CalciteSchema parent, Schema schema, // 私有构造方法,用于创建带有预填充数据的 CachingCalciteSchema
      String name, // 参数 name:当前 Schema 的名称
      @Nullable NameMap<CalciteSchema> subSchemaMap, // 参数 subSchemaMap:预填充的子 Schema 映射
      @Nullable NameMap<TableEntry> tableMap, // 参数 tableMap:预填充的表条目映射
      @Nullable NameMap<LatticeEntry> latticeMap, // 参数 latticeMap:预填充的 Lattice 条目映射(Lattice 是预计算的聚合结构)
      @Nullable NameMap<TypeEntry> typeMap, // 参数 typeMap:预填充的类型条目映射
      @Nullable NameMultimap<FunctionEntry> functionMap, // 参数 functionMap:预填充的函数条目多值映射
      @Nullable NameSet functionNames, // 参数 functionNames:预填充的函数名称集合
      @Nullable NameMap<FunctionEntry> nullaryFunctionMap, // 参数 nullaryFunctionMap:预填充的无参函数映射
      @Nullable List<? extends List<String>> path) { // 参数 path:当前 Schema 的完整路径(从根 Schema 到当前 Schema)
    super(parent, schema, name, subSchemaMap, tableMap, latticeMap, typeMap, // 调用父类 CalciteSchema 的构造方法,初始化所有继承的字段
        functionMap, functionNames, nullaryFunctionMap, path); // 继续传递参数给父类构造方法
    this.implicitFunctionCache = // 初始化隐式函数名称缓存,使用匿名内部类实现 Cached 接口
        new AbstractCached<NameSet>() { // 创建 AbstractCached 的匿名子类,泛型参数为 NameSet
          @Override public NameSet build() { // 重写 build 方法,用于构建缓存的值
            return NameSet.immutableCopyOf( // 从底层 Schema 获取函数名称集合,并创建不可变的副本
                CachingCalciteSchema.this.schema.getFunctionNames()); // 调用底层 Schema 的 getFunctionNames 方法获取所有函数名称
          } // build 方法结束,返回不可变的名称集合
        }; // 匿名内部类结束,完成隐式函数名称缓存的初始化
    this.implicitTypeCache = // 初始化隐式类型名称缓存,使用匿名内部类实现 Cached 接口
        new AbstractCached<NameSet>() { // 创建 AbstractCached 的匿名子类,泛型参数为 NameSet
          @Override public NameSet build() { // 重写 build 方法,用于构建缓存的值
            return NameSet.immutableCopyOf( // 从底层 Schema 获取类型名称集合,并创建不可变的副本
                CachingCalciteSchema.this.schema.getTypeNames()); // 调用底层 Schema 的 getTypeNames 方法获取所有类型名称
          } // build 方法结束,返回不可变的名称集合
        }; // 匿名内部类结束,完成隐式类型名称缓存的初始化
  } // 私有构造方法结束

  @Override public void setCache(boolean cache) { // 重写父类方法,用于设置缓存开关状态
    if (cache == this.cache) { // 如果新状态与当前状态相同,则无需操作
      return; // 直接返回,避免不必要的操作
    } // 条件判断结束
    enableCaches(cache); // 调用 enableCaches 方法,根据 cache 参数启用或禁用所有缓存
    final long now = System.currentTimeMillis(); // 获取当前时间戳,用于缓存的时间戳控制
    implicitFunctionCache.enable(now, cache); // 启用或禁用隐式函数名称缓存
    this.cache = cache; // 更新缓存开关标志
  } // setCache 方法结束

  @Override protected boolean isCacheEnabled() { // 重写父类方法,用于检查缓存是否启用
    return this.cache; // 返回缓存开关标志的当前值
  } // isCacheEnabled 方法结束

  @Override protected CalciteSchema createSubSchema(Schema schema, String name) { // 重写父类方法,用于创建子 Schema
    return new CachingCalciteSchema(this, schema, name); // 创建并返回一个新的 CachingCalciteSchema 实例作为子 Schema
  } // createSubSchema 方法结束

  @Override protected <S> Lookup<S> enhanceLookup(Lookup<S> lookup) { // 重写父类方法,用于增强查找功能,添加快照能力
    SnapshotLookup<S> snapshotLookup = new SnapshotLookup<>(lookup); // 创建一个 SnapshotLookup 包装器,为原始查找添加快照功能
    caches.add(snapshotLookup); // 将快照查找对象添加到缓存队列中,便于统一管理
    return snapshotLookup; // 返回增强后的快照查找对象
  } // enhanceLookup 方法结束

  /** Adds a child schema of this schema. */
  // 方法文档注释:添加一个子 Schema 到当前 Schema
  @Override public CalciteSchema add(String name, Schema schema) { // 重写父类方法,用于添加子 Schema
    final CalciteSchema calciteSchema = // 创建一个新的 CachingCalciteSchema 实例
        new CachingCalciteSchema(this, schema, name); // 使用当前 Schema 作为父 Schema,创建子 Schema
    subSchemaMap.put(name, calciteSchema); // 将新创建的子 Schema 添加到子 Schema 映射中
    return calciteSchema; // 返回新创建的子 Schema
  } // add 方法结束

  @Override protected @Nullable TypeEntry getImplicitType(String name, // 重写父类方法,用于获取隐式类型(未显式注册的类型)
      boolean caseSensitive) { // 参数 caseSensitive:是否区分大小写
    final long now = System.currentTimeMillis(); // 获取当前时间戳,用于缓存的时间戳控制
    final NameSet implicitTypeNames = implicitTypeCache.get(now); // 从缓存中获取隐式类型名称集合
    for (String typeName // 遍历与给定名称匹配的类型名称
        : implicitTypeNames.range(name, caseSensitive)) { // 根据名称和大小写敏感设置获取匹配的类型名称范围
      final RelProtoDataType type = schema.getType(typeName); // 从底层 Schema 获取指定名称的类型
      if (type != null) { // 如果类型存在
        return typeEntry(name, type); // 创建并返回类型条目
      } // 条件判断结束
    } // for 循环结束
    return null; // 未找到匹配的类型,返回 null
  } // getImplicitType 方法结束

  @Override protected void addImplicitFunctionsToBuilder( // 重写父类方法,用于将隐式函数添加到构建器
      ImmutableList.Builder<Function> builder, // 参数 builder:用于收集函数的构建器
      String name, boolean caseSensitive) { // 参数 name:函数名称,参数 caseSensitive:是否区分大小写
    // Add implicit functions, case-insensitive.
    // 注释:添加隐式函数,不区分大小写
    final long now = System.currentTimeMillis(); // 获取当前时间戳,用于缓存的时间戳控制
    final NameSet set = implicitFunctionCache.get(now); // 从缓存中获取隐式函数名称集合
    for (String name2 : set.range(name, caseSensitive)) { // 遍历与给定名称匹配的函数名称
      final Collection<Function> functions = schema.getFunctions(name2); // 从底层 Schema 获取指定名称的所有函数
      if (functions != null) { // 如果函数集合存在
        builder.addAll(functions); // 将所有函数添加到构建器中
      } // 条件判断结束
    } // for 循环结束
  } // addImplicitFunctionsToBuilder 方法结束

  @Override protected void addImplicitFuncNamesToBuilder( // 重写父类方法,用于将隐式函数名称添加到构建器
      ImmutableSortedSet.Builder<String> builder) { // 参数 builder:用于收集函数名称的构建器
    // Add implicit functions, case-sensitive.
    // 注释:添加隐式函数名称,区分大小写
    final long now = System.currentTimeMillis(); // 获取当前时间戳,用于缓存的时间戳控制
    final NameSet set = implicitFunctionCache.get(now); // 从缓存中获取隐式函数名称集合
    builder.addAll(set.iterable()); // 将所有函数名称添加到构建器中
  } // addImplicitFuncNamesToBuilder 方法结束

  @Override protected void addImplicitTypeNamesToBuilder( // 重写父类方法,用于将隐式类型名称添加到构建器
      ImmutableSortedSet.Builder<String> builder) { // 参数 builder:用于收集类型名称的构建器
    // Add implicit types, case-sensitive.
    // 注释:添加隐式类型名称,区分大小写
    final long now = System.currentTimeMillis(); // 获取当前时间戳,用于缓存的时间戳控制
    final NameSet set = implicitTypeCache.get(now); // 从缓存中获取隐式类型名称集合
    builder.addAll(set.iterable()); // 将所有类型名称添加到构建器中
  } // addImplicitTypeNamesToBuilder 方法结束

  @Override protected void addImplicitTablesBasedOnNullaryFunctionsToBuilder( // 重写父类方法,用于将基于无参函数的隐式表添加到构建器
      ImmutableSortedMap.Builder<String, Table> builder) { // 参数 builder:用于收集表的构建器
    ImmutableSortedMap<String, Table> explicitTables = builder.build(); // 构建已包含的显式表映射,用于避免重复

    final long now = System.currentTimeMillis(); // 获取当前时间戳,用于缓存的时间戳控制
    final NameSet set = implicitFunctionCache.get(now); // 从缓存中获取隐式函数名称集合
    for (String s : set.iterable()) { // 遍历所有函数名称
      // explicit table wins.
      // 注释:显式表优先(如果已存在同名显式表,则跳过)
      if (explicitTables.containsKey(s)) { // 如果显式表中已存在同名的表
        continue; // 跳过当前函数,不创建隐式表
      } // 条件判断结束
      for (Function function : schema.getFunctions(s)) { // 遍历指定名称的所有函数
        if (function instanceof TableMacro // 如果函数是表宏(可以生成表的函数)
            && function.getParameters().isEmpty()) { // 并且函数没有参数
          final Table table = ((TableMacro) function).apply(ImmutableList.of()); // 调用表宏的无参应用,生成表对象
          builder.put(s, table); // 将生成的表添加到构建器中
        } // 条件判断结束
      } // for 循环结束
    } // for 循环结束
  } // addImplicitTablesBasedOnNullaryFunctionsToBuilder 方法结束

  @Override protected @Nullable TableEntry getImplicitTableBasedOnNullaryFunction( // 重写父类方法,用于获取基于无参函数的隐式表
      String tableName, boolean caseSensitive) { // 参数 tableName:表名称,参数 caseSensitive:是否区分大小写
    final long now = System.currentTimeMillis(); // 获取当前时间戳,用于缓存的时间戳控制
    final NameSet set = implicitFunctionCache.get(now); // 从缓存中获取隐式函数名称集合
    for (String s : set.range(tableName, caseSensitive)) { // 遍历与给定表名匹配的函数名称
      for (Function function : schema.getFunctions(s)) { // 遍历指定名称的所有函数
        if (function instanceof TableMacro // 如果函数是表宏
            && function.getParameters().isEmpty()) { // 并且函数没有参数
          final Table table = // 调用表宏的无参应用,生成表对象
              ((TableMacro) function).apply(ImmutableList.of()); // 使用空参数列表调用表宏
          return tableEntry(tableName, table); // 创建并返回表条目
        } // 条件判断结束
      } // for 循环结束
    } // for 循环结束
    return null; // 未找到匹配的表,返回 null
  } // getImplicitTableBasedOnNullaryFunction 方法结束

  @Override protected CalciteSchema snapshot(@Nullable CalciteSchema parent, // 重写父类方法,用于创建 Schema 的快照
      SchemaVersion version) { // 参数 version:Schema 版本,用于支持版本控制
    CalciteSchema snapshot = // 创建一个新的 CachingCalciteSchema 实例作为快照
        new CachingCalciteSchema(parent, schema.snapshot(version), name, null, // 使用父 Schema、Schema 快照、名称创建,映射参数为 null
            tableMap, latticeMap, typeMap, // 复制当前的表、Lattice 和类型映射
            functionMap, functionNames, nullaryFunctionMap, getPath()); // 复制当前的函数映射和路径
    for (CalciteSchema subSchema : subSchemaMap.map().values()) { // 遍历所有子 Schema
      CalciteSchema subSchemaSnapshot = subSchema.snapshot(snapshot, version); // 递归创建子 Schema 的快照
      snapshot.subSchemaMap.put(subSchema.name, subSchemaSnapshot); // 将子 Schema 快照添加到快照的子 Schema 映射中
    } // for 循环结束
    return snapshot; // 返回创建的快照 Schema
  } // snapshot 方法结束

  @Override public boolean removeTable(String name) { // 重写父类方法,用于移除表
    if (cache) { // 如果缓存已启用
      enableCaches(false); // 先禁用所有缓存
      enableCaches(true); // 再重新启用所有缓存,以清除过期的缓存数据
    } // 条件判断结束
    return super.removeTable(name); // 调用父类的 removeTable 方法执行实际的移除操作
  } // removeTable 方法结束

  @Override public boolean removeFunction(String name) { // 重写父类方法,用于移除函数
    if (cache) { // 如果缓存已启用
      final long now = System.nanoTime(); // 获取当前时间戳(使用纳秒精度)
      implicitFunctionCache.enable(now, false); // 先禁用隐式函数名称缓存
      implicitFunctionCache.enable(now, true); // 再重新启用隐式函数名称缓存,以清除过期的缓存数据
    } // 条件判断结束
    return super.removeFunction(name); // 调用父类的 removeFunction 方法执行实际的移除操作
  } // removeFunction 方法结束

  private void enableCaches(final boolean cache) { // 私有方法,用于启用或禁用所有缓存
    for (SnapshotLookup<?> lookupCache : caches) { // 遍历缓存队列中的所有快照查找对象
      lookupCache.enable(cache); // 根据参数启用或禁用每个快照查找的缓存功能
    } // for 循环结束
  } // enableCaches 方法结束

  /** Strategy for caching the value of an object and re-creating it if its
   * value is out of date as of a given timestamp.
   *
   * @param <T> Type of cached object
   */
  // 内部接口文档注释:缓存策略接口,用于缓存对象的值,并在给定时间戳后如果值过期则重新创建
  // 泛型参数 T:缓存对象的类型
  private interface Cached<T> { // 定义 Cached 内部接口
    /** Returns the value; uses cached value if valid. */
    // 方法文档注释:返回值,如果缓存有效则使用缓存的值
    T get(long now); // 方法签名:获取缓存值,参数 now 为当前时间戳

    /** Creates a new value. */
    // 方法文档注释:创建一个新值
    T build(); // 方法签名:构建新值

    /** Called when CalciteSchema caching is enabled or disabled. */
    // 方法文档注释:当 CalciteSchema 缓存启用或禁用时调用
    void enable(long now, boolean enabled); // 方法签名:启用或禁用缓存,参数 now 为当前时间戳,参数 enabled 为启用状态
  } // Cached 接口结束

  /** Implementation of {@link CachingCalciteSchema.Cached}
   * that drives from {@link CachingCalciteSchema#cache}.
   *
   * @param <T> element type */
  // 内部抽象类文档注释:Cached 接口的实现,由 CachingCalciteSchema.cache 字段驱动
  // 泛型参数 T:元素类型
  private abstract class AbstractCached<T> implements Cached<T> { // 定义 AbstractCached 抽象类,实现 Cached 接口
    @Nullable T t; // 成员变量:缓存的值,可能为 null
    boolean built = false; // 成员变量:是否已构建标志,true 表示值已构建

    @Override public T get(long now) { // 重写 Cached 接口的 get 方法
      if (!CachingCalciteSchema.this.cache) { // 如果缓存未启用
        return build(); // 直接重新构建并返回新值
      } // 条件判断结束
      if (!built) { // 如果值尚未构建
        t = build(); // 构建新值并缓存
      } // 条件判断结束
      built = true; // 标记为已构建
      return castNonNull(t); // 返回缓存的值(转换为非空类型)
    } // get 方法结束

    @Override public void enable(long now, boolean enabled) { // 重写 Cached 接口的 enable 方法
      if (!enabled) { // 如果禁用缓存
        t = null; // 清空缓存的值
      } // 条件判断结束
      built = false; // 重置构建标志,下次 get 时会重新构建
    } // enable 方法结束
  } // AbstractCached 抽象类结束
} // CachingCalciteSchema 类结束