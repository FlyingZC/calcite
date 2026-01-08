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
// 声明包名，表示这个类属于 org.apache.calcite.adapter.clone 包
// CloneSchema 是 Calcite 框架中的一个适配器，用于创建表的内存克隆副本
package org.apache.calcite.adapter.clone;

// 导入 JavaTypeFactory，用于在 Java 类型系统和 Calcite 关系类型系统之间进行转换
import org.apache.calcite.adapter.java.JavaTypeFactory;
// 导入 JdbcSchema，用于通过 JDBC 连接访问外部数据库
import org.apache.calcite.adapter.jdbc.JdbcSchema;
// 导入 ColumnMetaData，用于描述列的元数据信息
import org.apache.calcite.avatica.ColumnMetaData;
// 导入 CalciteConnection，表示与 Calcite 数据库的连接
import org.apache.calcite.jdbc.CalciteConnection;
// 导入 Enumerable，表示可枚举的数据集合，用于 LINQ 风格的查询
import org.apache.calcite.linq4j.Enumerable;
// 导入 QueryProvider，提供 LINQ 查询执行能力的接口
import org.apache.calcite.linq4j.QueryProvider;
// 导入 Queryable，表示可查询的数据源，支持 LINQ 查询操作
import org.apache.calcite.linq4j.Queryable;
// 导入 RelCollation，表示关系的排序规则（collation 指排序规范）
import org.apache.calcite.rel.RelCollation;
// 导入 RelCollations，用于创建和操作关系排序规则的工具类
import org.apache.calcite.rel.RelCollations;
// 导入 RelProtoDataType，表示关系数据类型的原型，可以延迟创建实际的 RelDataType
import org.apache.calcite.rel.type.RelProtoDataType;
// 导入 QueryableTable，表示可以通过 LINQ 查询的表接口
import org.apache.calcite.schema.QueryableTable;
// 导入 Schema，表示数据库模式的接口，包含表、函数等对象
import org.apache.calcite.schema.Schema;
// 导入 SchemaFactory，用于创建 Schema 实例的工厂接口
import org.apache.calcite.schema.SchemaFactory;
// 导入 SchemaPlus，表示可以嵌套的 Schema，支持添加子 Schema
import org.apache.calcite.schema.SchemaPlus;
// 导入 Schemas，提供 Schema 相关的工具方法
import org.apache.calcite.schema.Schemas;
// 导入 Table，表示数据库表的接口
import org.apache.calcite.schema.Table;
// 导入 AbstractSchema，提供 Schema 的抽象基类实现
import org.apache.calcite.schema.impl.AbstractSchema;
// 导入 LikePattern，用于模式匹配，支持 LIKE 操作符
import org.apache.calcite.schema.lookup.LikePattern;
// 导入 Lookup，提供查找功能的通用接口
import org.apache.calcite.schema.lookup.Lookup;

// 导入 Suppliers，用于延迟加载和缓存值的工具类
import com.google.common.base.Suppliers;
// 导入 ImmutableList，提供不可变的列表实现
import com.google.common.collect.ImmutableList;

// 导入 @Nullable 注解，用于标记可能为 null 的参数或返回值
import org.checkerframework.checker.nullness.qual.Nullable;

// 导入 Type，表示 Java 语言的类型
import java.lang.reflect.Type;
// 导入 LinkedHashMap，保持插入顺序的 Map 实现
import java.util.LinkedHashMap;
// 导入 List，表示有序集合的接口
import java.util.List;
// 导入 Map，表示键值对映射的接口
import java.util.Map;

// 静态导入 MATERIALIZATION_CONNECTION 常量，表示物化视图使用的连接
import static org.apache.calcite.schema.impl.MaterializedViewTable.MATERIALIZATION_CONNECTION;

/**
 * Schema that contains in-memory copies of tables from a JDBC schema.
 * 这是一个包含 JDBC 数据库表的内存副本的 Schema
 * 
 * CloneSchema 是 Calcite 中的一个特殊 Schema，它将外部 JDBC 数据库中的表
 * 加载到内存中，创建表的克隆副本。这样可以加速查询性能，因为数据已经在内存中，
 * 不需要每次查询都访问外部数据库。
 * 
 * 主要用途：
 * 1. 提高查询性能：数据在内存中，避免频繁的 JDBC 调用
 * 2. 支持物化视图：可以作为物化视图的存储机制
 * 3. 数据缓存：缓存远程或慢速数据源的数据
 * 
 * 工作原理：
 * - CloneSchema 继承自 AbstractSchema，实现 Schema 接口
 * - 在 getTableMap() 方法中，从源 Schema（通常是 JdbcSchema）获取所有表
 * - 对于每个 QueryableTable，创建一个内存中的克隆表（ArrayTable）
 * - 克隆表使用 ArrayTable 存储，数据以列式存储在内存中
 * 
 * 使用场景：
 * - 需要频繁查询的静态数据或变化不频繁的数据
 * - 远程数据源的本地缓存
 * - 测试和开发环境中的数据模拟
 */
public class CloneSchema extends AbstractSchema {
  // TODO: implement 'driver' property - 待实现：支持 driver 属性配置
  // TODO: implement 'source' property - 待实现：支持 source 属性配置
  // TODO: test Factory - 待实现：测试 Factory 类的功能

  // 成员变量：sourceSchema - 源 Schema 的引用
  // 这个字段保存了被克隆的原始 Schema（通常是 JdbcSchema）
  // CloneSchema 通过这个字段访问源 Schema 中的表数据
  // final 修饰表示一旦初始化就不能改变，保证数据源的稳定性
  // SchemaPlus 类型表示这是一个可以嵌套的 Schema，支持添加子 Schema
  private final SchemaPlus sourceSchema;

  /**
   * Creates a CloneSchema.
   * 创建一个 CloneSchema 实例
   *
   * @param sourceSchema JDBC data source - JDBC 数据源，即要克隆的源 Schema
   * 
   * 构造函数说明：
   * 1. 调用父类 AbstractSchema 的构造函数进行初始化
   * 2. 将传入的 sourceSchema 保存到成员变量中
   * 3. sourceSchema 通常是 JdbcSchema，通过 JDBC 连接访问外部数据库
   * 4. CloneSchema 会从这个源 Schema 中读取表数据并创建内存副本
   */
  public CloneSchema(SchemaPlus sourceSchema) {
    super(); // 调用父类 AbstractSchema 的默认构造函数
    this.sourceSchema = sourceSchema; // 保存源 Schema 的引用
  }

  /**
   * 获取表映射，返回该 Schema 中所有表的 Map
   * 键是表名，值是 Table 对象
   * 
   * 这个方法是 AbstractSchema 中的抽象方法，需要子类实现
   * CloneSchema 的实现是从源 Schema 中获取所有表，并为每个表创建克隆副本
   * 
   * @return Map<String, Table> - 表名到 Table 对象的映射
   * 
   * 实现细节：
   * 1. 创建一个 LinkedHashMap 来存储表映射，保持表的插入顺序
   * 2. 从源 Schema 获取表的 Lookup 对象
   * 3. 遍历源 Schema 中的所有表（使用 LikePattern.any() 匹配所有表名）
   * 4. 对于每个 QueryableTable 类型的表，创建其克隆副本
   * 5. 将克隆表添加到 map 中，键为表名
   * 6. 返回包含所有克隆表的 map
   * 
   * 注意：
   * - 只有 QueryableTable 类型的表会被克隆，其他类型的表会被忽略
   * - 克隆表使用 createCloneTable 方法创建，返回 ArrayTable 实例
   * - 表的克隆是延迟的，实际数据加载在访问表时才进行
   */
  @Override protected Map<String, Table> getTableMap() {
    // 创建 LinkedHashMap 来存储表映射，保持插入顺序
    // LinkedHashMap 比 HashMap 更适合这里，因为它保持了表的原始顺序
    final Map<String, Table> map = new LinkedHashMap<>();
    // 从源 Schema 获取表的 Lookup 对象
    // Lookup 提供了查找表的能力，支持模式匹配
    final Lookup<Table> tables = sourceSchema.tables();
    // 遍历源 Schema 中的所有表
    // LikePattern.any() 匹配所有表名，即获取所有表
    for (String name : tables.getNames(LikePattern.any())) {
      // 根据表名从 Lookup 中获取 Table 对象
      final Table table = tables.get(name);
      // 检查表是否是 QueryableTable 类型
      // QueryableTable 表示可以通过 LINQ 查询的表，这是可以克隆的表类型
      if (table instanceof QueryableTable) {
        // 将 Table 强制转换为 QueryableTable
        final QueryableTable sourceTable = (QueryableTable) table;
        // 创建克隆表并添加到 map 中
        // 表名作为键，克隆的 Table 对象作为值
        // createCloneTable 方法会创建一个 ArrayTable，将源表数据加载到内存
        map.put(name,
            createCloneTable(MATERIALIZATION_CONNECTION, sourceTable, name));
      }
    }
    // 返回包含所有克隆表的映射
    // 调用者可以通过这个 map 获取 Schema 中的所有表
    return map;
  }

  /**
   * 创建克隆表的私有方法
   * 
   * @param queryProvider - 查询提供者，用于执行 LINQ 查询
   * @param sourceTable - 源表，即要克隆的 QueryableTable
   * @param name - 表名
   * @return Table - 克隆后的表对象（ArrayTable 实例）
   * 
   * 方法说明：
   * 1. 从源表创建一个 Queryable 对象
   * 2. 从 QueryProvider 获取 JavaTypeFactory
   * 3. 调用重载的 createCloneTable 方法创建克隆表
   * 
   * 注意：
   * - Queryable 是 LINQ 的核心概念，表示可查询的数据源
   * - JavaTypeFactory 用于在 Java 类型和 Calcite 关系类型之间转换
   * - 最终创建的克隆表使用 ArrayTable 实现，数据在内存中以列式存储
   */
  private Table createCloneTable(QueryProvider queryProvider,
      QueryableTable sourceTable, String name) {
    // 从源表创建 Queryable 对象
    // asQueryable 方法将表转换为可查询的对象
    // 参数：queryProvider 提供查询执行能力，sourceSchema 是表所在的 Schema，name 是表名
    final Queryable<Object> queryable =
        sourceTable.asQueryable(queryProvider, sourceSchema, name);
    // 从 QueryProvider 获取 JavaTypeFactory
    // 需要先将 QueryProvider 强制转换为 CalciteConnection
    // CalciteConnection 是 Calcite 特定的连接接口，提供类型工厂
    final JavaTypeFactory typeFactory =
        ((CalciteConnection) queryProvider).getTypeFactory();
    // 调用重载的 createCloneTable 方法创建克隆表
    // 参数：
    // - typeFactory: 类型工厂，用于类型转换
    // - Schemas.proto(sourceTable): 创建源表行类型的原型
    // - ImmutableList.of(): 空的排序规则列表
    // - null: 列的表示列表，使用默认值
    // - queryable: 可查询的数据源，包含表的实际数据
    return createCloneTable(typeFactory, Schemas.proto(sourceTable),
        ImmutableList.of(), null, queryable);
  }

  /**
   * 创建克隆表的静态方法（已废弃）
   * 
   * @param <T> - 数据类型泛型参数
   * @param typeFactory - Java 类型工厂
   * @param protoRowType - 行类型的原型
   * @param repList - 列的表示列表（可为 null）
   * @param source - 数据源
   * @return Table - 克隆后的表对象
   * 
   * @Deprecated - 这个方法已被废弃，将在 2.0 版本之前移除
   * 
   * 方法说明：
   * 这是一个旧版本的 createCloneTable 方法，不支持排序规则
   * 它内部调用新版本的方法，传入空的排序规则列表
   * 
   * 为什么废弃：
   * - 新版本增加了 collations 参数，支持表的排序规则
   * - 为了保持向后兼容，暂时保留这个方法
   * - 建议使用新版本的 createCloneTable 方法
   */
  @Deprecated // to be removed before 2.0 - 将在 2.0 版本之前移除
  public static <T> Table createCloneTable(final JavaTypeFactory typeFactory,
      final RelProtoDataType protoRowType,
      final @Nullable List<ColumnMetaData.Rep> repList,
      final Enumerable<T> source) {
    // 调用新版本的 createCloneTable 方法
    // 传入空的排序规则列表 ImmutableList.of()
    // 其他参数保持不变
    return createCloneTable(typeFactory, protoRowType, ImmutableList.of(),
        repList, source);
  }

  /**
   * 创建克隆表的静态方法（当前版本）
   * 
   * 这是 CloneSchema 的核心方法，负责创建内存中的克隆表
   * 
   * @param <T> - 数据类型泛型参数，表示源数据的元素类型
   * @param typeFactory - Java 类型工厂，用于类型转换和创建
   * @param protoRowType - 行类型的原型，描述表的结构（列名、类型等）
   * @param collations - 排序规则列表，定义表的排序方式
   * @param repList - 列的表示列表，描述每列的 Java 类型（可为 null）
   * @param source - 数据源，包含表的实际数据
   * @return Table - 返回创建的克隆表（ArrayTable 实例）
   * 
   * 方法详细说明：
   * 
   * 1. 确定元素类型（elementType）：
   *    - 如果 source 是 QueryableTable，使用其 getElementType() 方法获取元素类型
   *    - 如果表只有一列（getFieldCount() == 1）：
   *      - 如果提供了 repList，使用 repList 中第一个元素的类型
   *      - 否则使用 Object.class
   *    - 如果表有多列，使用 Object[].class（表示每行是一个对象数组）
   * 
   * 2. 创建 ArrayTable：
   *    - ArrayTable 是内存表的实现，以列式存储数据
   *    - 使用 Suppliers.memoize 实现延迟加载，只在第一次访问时加载数据
   * 
   * 3. 数据加载过程（在 Supplier 中）：
   *    a. 创建 ColumnLoader 对象，负责从 source 加载数据
    *    b. ColumnLoader 将数据转换为列式存储格式
    *    c. 确定排序规则：
    *       - 如果没有提供排序规则且 ColumnLoader 检测到排序字段，使用该字段
    *       - 否则使用提供的排序规则
    *    d. 创建 ArrayTable.Content 对象，包含：
    *       - representationValues: 列式存储的数据值
    *       - size: 行数
    *       - collation2: 排序规则
   * 
   * 关键概念：
   * - RelProtoDataType: 关系数据类型的原型，可以延迟创建实际的 RelDataType
   * - ColumnMetaData.Rep: 列的表示，描述列的 Java 类型和表示方式
   * - ColumnLoader: 列加载器，负责将行式数据转换为列式存储
   * - ArrayTable: 内存表的实现，使用数组存储列数据
   * - Suppliers.memoize: Guava 提供的延迟加载工具，缓存计算结果
   * 
   * 性能优化：
   * - 使用延迟加载，只在需要时才加载数据
   * - 列式存储提高查询性能（特别是只需要部分列时）
   * - 使用缓存避免重复加载数据
   */
  public static <T> Table createCloneTable(final JavaTypeFactory typeFactory,
      final RelProtoDataType protoRowType, final List<RelCollation> collations,
      final @Nullable List<ColumnMetaData.Rep> repList, final Enumerable<T> source) {
    // 声明元素类型变量，用于确定每行数据的 Java 类型
    final Type elementType;
    // 判断 source 是否是 QueryableTable 类型
    // QueryableTable 是特殊的表，可以提供自己的元素类型信息
    if (source instanceof QueryableTable) {
      // 如果是 QueryableTable，使用其 getElementType() 方法获取元素类型
      // 这样可以获得更准确的类型信息
      elementType = ((QueryableTable) source).getElementType();
    } else if (protoRowType.apply(typeFactory).getFieldCount() == 1) {
      // 如果表只有一列
      // protoRowType.apply(typeFactory) 获取实际的 RelDataType
      // getFieldCount() 获取字段数量
      if (repList != null) {
        // 如果提供了列表示列表，使用第一列的类型
        // repList.get(0) 获取第一列的表示
        // clazz 是该列对应的 Java 类
        elementType = repList.get(0).clazz;
      } else {
        // 如果没有提供列表示列表，使用 Object.class
        // 这是最通用的类型，可以表示任何对象
        elementType = Object.class;
      }
    } else {
      // 如果表有多列，使用 Object[].class
      // 每行数据表示为一个对象数组，数组的每个元素对应一列
      // 这是多列表的标准表示方式
      elementType = Object[].class;
    }
    // 创建并返回 ArrayTable 对象
    // ArrayTable 是内存表的实现，以列式存储数据
    return new ArrayTable(
        elementType, // 元素类型，决定每行数据的 Java 类型
        protoRowType, // 行类型原型，描述表的结构
        Suppliers.memoize(() -> { // 使用 Guava 的 Suppliers.memoize 实现延迟加载
          // 创建 ColumnLoader 对象，负责从 source 加载数据
          // ColumnLoader 会将行式数据转换为列式存储格式
          final ColumnLoader loader =
              new ColumnLoader<>(typeFactory, source, protoRowType,
                  repList);
          // 确定最终的排序规则
          final List<RelCollation> collation2 =
              collations.isEmpty() // 如果没有提供排序规则
                  && loader.sortField >= 0 // 且 ColumnLoader 检测到排序字段（sortField >= 0）
                  ? RelCollations.createSingleton(loader.sortField) // 使用检测到的排序字段创建单字段排序规则
                  : collations; // 否则使用提供的排序规则
          // 创建并返回 ArrayTable.Content 对象
          // Content 包含了表的所有数据和元数据
          return new ArrayTable.Content(loader.representationValues, // 列式存储的数据值，每列是一个数组
              loader.size(), // 表的行数
              collation2); // 排序规则
        }));
  }

  /**
   * Schema factory that creates a
   * {@link org.apache.calcite.adapter.clone.CloneSchema}.
   * 这是一个 Schema 工厂类，用于创建 CloneSchema 实例
   * 
   * This allows you to create a clone schema inside a model.json file.
   * 这允许你在 model.json 配置文件中创建克隆 Schema
   * 
   * Factory 类实现了 SchemaFactory 接口，使 CloneSchema 可以通过配置文件创建
   * 这样就不需要编写 Java 代码来创建 CloneSchema，只需要在 JSON 配置中声明即可
   * 
   * 使用示例：
   * 在 Calcite 的 model.json 文件中，可以配置一个克隆 Schema：
   * {
   *   version: '1.0',
   *   defaultSchema: 'FOODMART_CLONE',
   *   schemas: [
   *     {
   *       name: 'FOODMART_CLONE',
   *       type: 'custom',
   *       factory: 'org.apache.calcite.adapter.clone.CloneSchema$Factory',
   *       operand: {
   *         jdbcDriver: 'com.mysql.jdbc.Driver',
   *         jdbcUrl: 'jdbc:mysql://localhost/foodmart',
   *         jdbcUser: 'foodmart',
   *         jdbcPassword: 'foodmart'
   *       }
   *     }
   *   ]
   * }
   * 
   * 配置说明：
   * - name: Schema 的名称
   * - type: 'custom' 表示使用自定义的 Schema 工厂
   * - factory: 工厂类的全限定名，这里使用 CloneSchema 的内部类 Factory
   * - operand: 传递给工厂的参数，包含 JDBC 连接信息
   * 
   * 工作流程：
   * 1. Calcite 读取 model.json 配置文件
   * 2. 发现 type 为 'custom' 的 Schema 配置
   * 3. 通过反射创建 Factory 实例
   * 4. 调用 Factory.create() 方法创建 Schema
   * 5. Factory 内部创建 JDBC Schema，然后基于它创建 CloneSchema
   * 
   * <blockquote><pre>
   * {
   *   version: '1.0',
   *   defaultSchema: 'FOODMART_CLONE',
   *   schemas: [
   *     {
   *       name: 'FOODMART_CLONE',
   *       type: 'custom',
   *       factory: 'org.apache.calcite.adapter.clone.CloneSchema$Factory',
   *       operand: {
   *         jdbcDriver: 'com.mysql.jdbc.Driver',
   *         jdbcUrl: 'jdbc:mysql://localhost/foodmart',
   *         jdbcUser: 'foodmart',
   *         jdbcPassword: 'foodmart'
   *       }
   *     }
   *   ]
   * }</pre></blockquote>
   */
  public static class Factory implements SchemaFactory {
    /**
     * 创建 Schema 的方法
     * 
     * @param parentSchema - 父 Schema，新创建的 Schema 会添加到这个父 Schema 中
     * @param name - Schema 的名称
     * @param operand - 配置参数，包含 JDBC 连接信息等
     * @return Schema - 返回创建的 CloneSchema 实例
     * 
     * 方法说明：
     * 1. 使用 JdbcSchema.create() 创建 JDBC Schema
     *    - 这个 Schema 连接到实际的数据库
     *    - 名称使用 name + "$source"，例如 "FOODMART_CLONE$source"
     *    - operand 包含 JDBC 连接参数（driver、url、user、password等）
     * 2. 将 JDBC Schema 添加到父 Schema 中
     * 3. 基于 JDBC Schema 创建 CloneSchema
     * 4. 返回 CloneSchema 实例
     * 
     * 注意：
     * - JDBC Schema 是数据源，CloneSchema 是其内存副本
     * - JDBC Schema 会作为子 Schema 添加到父 Schema 中
     * - CloneSchema 会从 JDBC Schema 读取数据并创建内存副本
     */
    @Override public Schema create(
        SchemaPlus parentSchema,
        String name,
        Map<String, Object> operand) {
      // 创建 JDBC Schema 并添加到父 Schema 中
      // JdbcSchema.create() 创建连接到数据库的 Schema
      // name + "$source" 为源 Schema 的名称，例如 "FOODMART_CLONE$source"
      // operand 包含 JDBC 连接信息（driver、url、user、password等）
      // parentSchema.add() 将创建的 JDBC Schema 添加到父 Schema 中
      SchemaPlus schema =
          parentSchema.add(name,
              JdbcSchema.create(parentSchema, name + "$source", operand));
      // 基于创建的 JDBC Schema 创建 CloneSchema
      // CloneSchema 会从 JDBC Schema 中读取表数据并创建内存副本
      // 返回 CloneSchema 实例
      return new CloneSchema(schema);
    }
  }
}
