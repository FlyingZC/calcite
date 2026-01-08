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
package org.apache.calcite.jdbc; // 定义包名，该类属于 Calcite JDBC 模块

import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系数据类型原型，用于延迟创建类型
import org.apache.calcite.schema.Function; // 导入函数接口，表示 Calcite 中的标量函数或表函数
import org.apache.calcite.schema.Schema; // 导入 Schema 接口，表示数据库模式（包含表、函数等）
import org.apache.calcite.schema.SchemaVersion; // 导入 Schema 版本接口，用于支持 Schema 版本控制
import org.apache.calcite.schema.Table; // 导入 Table 接口，表示数据库表
import org.apache.calcite.schema.TableMacro; // 导入 TableMacro 接口，表示可以生成表的宏函数
import org.apache.calcite.util.NameMap; // 导入 NameMap 工具类，用于按名称存储和查找对象
import org.apache.calcite.util.NameMultimap; // 导入 NameMultimap 工具类，用于按名称存储多个对象
import org.apache.calcite.util.NameSet; // 导入 NameSet 工具类，用于存储名称集合

import com.google.common.collect.ImmutableList; // 导入不可变列表，用于构建不可修改的列表
import com.google.common.collect.ImmutableSortedMap; // 导入不可变排序映射，用于构建不可修改的有序映射
import com.google.common.collect.ImmutableSortedSet; // 导入不可变排序集合，用于构建不可修改的有序集合

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为 null 的参数或返回值

import java.util.Collection; // 导入集合接口
import java.util.List; // 导入列表接口
import java.util.Locale; // 导入 Locale 类，用于区域设置（如大小写转换）
import java.util.Set; // 导入集合接口

/**
 * A concrete implementation of {@link org.apache.calcite.jdbc.CalciteSchema}
 * that maintains minimal state.
 * SimpleCalciteSchema 是 CalciteSchema 的具体实现，它维护最小化的状态
 * 这个类是 Calcite 中用于表示数据库模式（Schema）的核心类之一
 * 它不启用缓存功能，每次访问都直接从底层 Schema 获取数据
 * 这种设计适用于 Schema 内容频繁变化的场景，或者不需要缓存的场景
 */
class SimpleCalciteSchema extends CalciteSchema { // 定义 SimpleCalciteSchema 类，继承自 CalciteSchema 抽象类
  /** Creates a SimpleCalciteSchema.
   *
   * <p>Use {@link CalciteSchema#createRootSchema(boolean)}
   * or {@link #add(String, Schema)}.
   * 创建一个 SimpleCalciteSchema 实例的公共构造方法
   * 注意：通常不直接调用此构造方法，而是通过 createRootSchema 或 add 方法创建
   * @param parent 父 Schema，如果是根 Schema 则为 null
   * @param schema 底层的 Schema 实现，包含实际的表、函数等数据
   * @param name Schema 的名称
   */
  SimpleCalciteSchema(@Nullable CalciteSchema parent, Schema schema, String name) { // 公共构造方法，接收父 Schema、底层 Schema 和名称
    this(parent, schema, name, null, null, null, null, null, null, null, null); // 调用私有构造方法，所有映射集合都初始化为 null
  }

  private SimpleCalciteSchema(@Nullable CalciteSchema parent, // 父 Schema，可能是 null（对于根 Schema）
      Schema schema, // 底层的 Schema 实现，包含实际的表、函数等数据
      String name, // Schema 的名称
      @Nullable NameMap<CalciteSchema> subSchemaMap, // 子 Schema 的名称映射，键为 Schema 名称，值为 CalciteSchema 对象
      @Nullable NameMap<TableEntry> tableMap, // 表条目的名称映射，键为表名，值为 TableEntry 对象
      @Nullable NameMap<LatticeEntry> latticeMap, // Lattice（立方体）条目的名称映射，用于物化视图优化
      @Nullable NameMap<TypeEntry> typeMap, // 类型条目的名称映射，存储自定义类型
      @Nullable NameMultimap<FunctionEntry> functionMap, // 函数条目的名称多重映射，一个名称可能有多个函数（重载）
      @Nullable NameSet functionNames, // 函数名称集合，存储所有可用的函数名称
      @Nullable NameMap<FunctionEntry> nullaryFunctionMap, // 无参函数的名称映射，用于快速查找无参函数
      @Nullable List<? extends List<String>> path) { // Schema 的路径，表示从根 Schema 到当前 Schema 的完整路径
    super(parent, schema, name, subSchemaMap, tableMap, latticeMap, typeMap, // 调用父类 CalciteSchema 的构造方法，初始化所有字段
        functionMap, functionNames, nullaryFunctionMap, path); // 传递所有参数给父类构造方法
  }

  @Override public void setCache(boolean cache) { // 重写父类的 setCache 方法，用于设置是否启用缓存
    throw new UnsupportedOperationException(); // SimpleCalciteSchema 不支持缓存功能，抛出不支持操作异常
  }

  @Override public CalciteSchema add(String name, Schema schema) { // 重写父类的 add 方法，用于添加子 Schema
    final CalciteSchema calciteSchema = // 创建新的 CalciteSchema 对象
        new SimpleCalciteSchema(this, schema, name); // 创建 SimpleCalciteSchema 实例，当前 Schema 作为父 Schema
    subSchemaMap.put(name, calciteSchema); // 将新创建的子 Schema 添加到子 Schema 映射中
    return calciteSchema; // 返回新创建的子 Schema 对象
  }

  private static @Nullable String caseInsensitiveLookup(Set<String> candidates, String name) { // 私有静态方法，用于在不区分大小写的候选集合中查找名称
    // Exact string lookup
    if (candidates.contains(name)) { // 首先尝试精确匹配（区分大小写）
      return name; // 如果找到精确匹配，直接返回该名称
    }
    // Upper case string lookup
    final String upperCaseName = name.toUpperCase(Locale.ROOT); // 将输入名称转换为大写，使用 ROOT 语言环境
    if (candidates.contains(upperCaseName)) { // 检查候选集合中是否有大写形式
      return upperCaseName; // 如果找到，返回大写形式的名称
    }
    // Lower case string lookup
    final String lowerCaseName = name.toLowerCase(Locale.ROOT); // 将输入名称转换为小写，使用 ROOT 语言环境
    if (candidates.contains(lowerCaseName)) { // 检查候选集合中是否有小写形式
      return lowerCaseName; // 如果找到，返回小写形式的名称
    }
    // Fall through: Set iteration
    for (String candidate : candidates) { // 遍历所有候选名称
      if (candidate.equalsIgnoreCase(name)) { // 使用不区分大小写的比较
        return candidate; // 如果找到匹配，返回候选名称
      }
    }
    return null; // 如果没有找到任何匹配，返回 null
  }

  @Override protected CalciteSchema createSubSchema(Schema schema, String name) { // 重写父类的 createSubSchema 方法，用于创建子 Schema
    return new SimpleCalciteSchema(this, schema, name); // 创建并返回新的 SimpleCalciteSchema 实例
  }

  @Override protected @Nullable TypeEntry getImplicitType(String name, boolean caseSensitive) { // 重写父类的 getImplicitType 方法，用于获取隐式类型（从底层 Schema 获取的类型）
    // Check implicit types.
    final String name2 = // 根据是否区分大小写，获取实际的类型名称
        caseSensitive ? name // 如果区分大小写，直接使用输入的名称
            : caseInsensitiveLookup(schema.getTypeNames(), name); // 如果不区分大小写，使用不区分大小写的查找
    if (name2 == null) { // 如果没有找到匹配的类型名称
      return null; // 返回 null，表示类型不存在
    }
    final RelProtoDataType type = schema.getType(name2); // 从底层 Schema 获取类型原型
    if (type == null) { // 如果类型不存在
      return null; // 返回 null
    }
    return typeEntry(name2, type); // 创建并返回类型条目
  }

  @Override protected void addImplicitFunctionsToBuilder( // 重写父类的 addImplicitFunctionsToBuilder 方法，用于将隐式函数添加到构建器中
      ImmutableList.Builder<Function> builder, // 函数列表构建器，用于收集函数
      String name, boolean caseSensitive) { // 函数名称和是否区分大小写的标志
    Collection<Function> functions = schema.getFunctions(name); // 从底层 Schema 获取指定名称的所有函数
    if (functions != null) { // 如果函数集合不为空
      builder.addAll(functions); // 将所有函数添加到构建器中
    }
  }

  @Override protected void addImplicitFuncNamesToBuilder( // 重写父类的 addImplicitFuncNamesToBuilder 方法，用于将隐式函数名称添加到构建器中
      ImmutableSortedSet.Builder<String> builder) { // 排序集合构建器，用于收集函数名称
    builder.addAll(schema.getFunctionNames()); // 从底层 Schema 获取所有函数名称并添加到构建器中
  }

  @Override protected void addImplicitTypeNamesToBuilder( // 重写父类的 addImplicitTypeNamesToBuilder 方法，用于将隐式类型名称添加到构建器中
      ImmutableSortedSet.Builder<String> builder) { // 排序集合构建器，用于收集类型名称
    builder.addAll(schema.getTypeNames()); // 从底层 Schema 获取所有类型名称并添加到构建器中
  }

  @Override protected void addImplicitTablesBasedOnNullaryFunctionsToBuilder( // 重写父类的 addImplicitTablesBasedOnNullaryFunctionsToBuilder 方法，用于将基于无参函数的隐式表添加到构建器中
      ImmutableSortedMap.Builder<String, Table> builder) { // 排序映射构建器，用于收集表名到表的映射
    ImmutableSortedMap<String, Table> explicitTables = builder.build(); // 先构建当前已有的显式表映射

    for (String s : schema.getFunctionNames()) { // 遍历所有函数名称
      // explicit table wins.
      if (explicitTables.containsKey(s)) { // 如果显式表中已经存在同名表
        continue; // 跳过，显式表优先级更高
      }
      for (Function function : schema.getFunctions(s)) { // 遍历该名称下的所有函数
        if (function instanceof TableMacro // 如果函数是表宏（可以生成表的函数）
            && function.getParameters().isEmpty()) { // 并且函数没有参数（无参函数）
          final Table table = ((TableMacro) function).apply(ImmutableList.of()); // 调用表宏生成表，传入空参数列表
          builder.put(s, table); // 将生成的表添加到构建器中，使用函数名称作为表名
        }
      }
    }
  }

  @Override protected @Nullable TableEntry getImplicitTableBasedOnNullaryFunction(String tableName, // 重写父类的 getImplicitTableBasedOnNullaryFunction 方法，用于获取基于无参函数的隐式表
      boolean caseSensitive) { // 是否区分大小写的标志（注意：此参数在当前实现中未使用）
    Collection<Function> functions = schema.getFunctions(tableName); // 从底层 Schema 获取指定名称的所有函数
    if (functions != null) { // 如果函数集合不为空
      for (Function function : functions) { // 遍历所有函数
        if (function instanceof TableMacro // 如果函数是表宏
            && function.getParameters().isEmpty()) { // 并且函数没有参数
          final Table table = ((TableMacro) function).apply(ImmutableList.of()); // 调用表宏生成表
          return tableEntry(tableName, table); // 创建并返回表条目
        }
      }
    }
    return null; // 如果没有找到匹配的表，返回 null
  }

  @Override protected CalciteSchema snapshot(@Nullable CalciteSchema parent, // 重写父类的 snapshot 方法，用于创建 Schema 的快照
      SchemaVersion version) { // Schema 版本，用于创建特定版本的快照
    CalciteSchema snapshot = // 创建快照对象
        new SimpleCalciteSchema(parent, schema.snapshot(version), name, null, // 创建新的 SimpleCalciteSchema，使用底层 Schema 的快照
            tableMap, latticeMap, typeMap, // 复制表、Lattice 和类型的映射
            functionMap, functionNames, nullaryFunctionMap, getPath()); // 复制函数相关的映射和路径
    for (CalciteSchema subSchema : subSchemaMap.map().values()) { // 遍历所有子 Schema
      CalciteSchema subSchemaSnapshot = subSchema.snapshot(snapshot, version); // 递归创建子 Schema 的快照
      snapshot.subSchemaMap.put(subSchema.name, subSchemaSnapshot); // 将子 Schema 快照添加到快照的子 Schema 映射中
    }
    return snapshot; // 返回创建的快照对象
  }

  @Override protected boolean isCacheEnabled() { // 重写父类的 isCacheEnabled 方法，用于检查是否启用了缓存
    return false; // SimpleCalciteSchema 不启用缓存，始终返回 false
  }

}
