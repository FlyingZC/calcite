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
// Apache Calcite 是一个动态数据管理框架，提供 SQL 解析、优化、执行等功能
// 本文件位于 org.apache.calcite.interpreter 包下，是解释器执行引擎的一部分
package org.apache.calcite.interpreter; // 解释器包，包含解释器执行引擎的核心类

// 导入 Calcite 核心类
import org.apache.calcite.DataContext; // 数据上下文接口，提供执行 SQL 查询时的运行时环境信息（如用户定义的函数、变量等）
import org.apache.calcite.linq4j.Enumerable; // LINQ4J 的可枚举接口，支持类似 LINQ 的查询操作，用于延迟执行的数据流
import org.apache.calcite.linq4j.Queryable; // LINQ4J 的可查询接口，支持在数据源上执行查询操作
import org.apache.calcite.plan.RelOptTable; // 关系优化表，代表优化器中的表对象，包含表的元数据信息
import org.apache.calcite.plan.RelOptUtil; // 关系优化工具类，提供各种优化相关的实用方法
import org.apache.calcite.rel.core.TableScan; // 表扫描关系节点，代表关系代数中的表扫描操作
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型，描述表或表达式的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 关系数据类型工厂，用于创建和管理数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 关系数据类型字段，描述表中的单个列
import org.apache.calcite.rex.RexNode; // Rex 节点，代表关系表达式树中的一个节点（如条件、运算等）
import org.apache.calcite.rex.RexUtil; // Rex 工具类，提供关系表达式操作的实用方法
import org.apache.calcite.runtime.Enumerables; // 可枚举运行时工具类，提供可枚举对象的转换和操作
import org.apache.calcite.schema.FilterableTable; // 可过滤表接口，表可以接受过滤条件并在扫描时应用
import org.apache.calcite.schema.ProjectableFilterableTable; // 可投影可过滤表接口，表可以接受投影和过滤条件
import org.apache.calcite.schema.QueryableTable; // 可查询表接口，表可以作为 LINQ 查询的数据源
import org.apache.calcite.schema.ScannableTable; // 可扫描表接口，表可以被完整扫描
import org.apache.calcite.schema.SchemaPlus; // SchemaPlus 接口，扩展的 Schema，支持添加表、函数等
import org.apache.calcite.schema.Schemas; // Schema 工具类，提供 Schema 操作的实用方法
import org.apache.calcite.util.ImmutableBitSet; // 不可变位集合，用于高效地表示一组整数索引
import org.apache.calcite.util.ImmutableIntList; // 不可变整数列表，用于表示列索引列表
import org.apache.calcite.util.ReflectUtil; // 反射工具类，提供反射操作的实用方法
import org.apache.calcite.util.Util; // 通用工具类，提供各种实用方法
import org.apache.calcite.util.mapping.Mapping; // 映射接口，定义索引之间的映射关系
import org.apache.calcite.util.mapping.Mappings; // 映射工具类，提供映射创建和操作的实用方法

// 导入 Google Guava 库类
import com.google.common.collect.ImmutableList; // 不可变列表，线程安全的列表实现
import com.google.common.collect.Iterables; // 可迭代工具类，提供迭代操作的实用方法
import com.google.common.collect.Lists; // 列表工具类，提供列表创建和操作的实用方法

// 导入 Checkerframework 注解
import org.checkerframework.checker.nullness.qual.Nullable; // 可空注解，标记可能为 null 的类型

// 导入 Java 反射类
import java.lang.reflect.Field; // 字段类，用于通过反射访问类的字段
import java.lang.reflect.Type; // 类型接口，代表 Java 中的类型
import java.util.List; // 列表接口，Java 集合框架的核心接口

// 导入静态资源
import static org.apache.calcite.util.Static.RESOURCE; // 静态资源，包含错误消息和本地化字符串

// 导入 Java 对象工具
import static java.util.Objects.requireNonNull; // 对象工具类，提供空值检查方法

/**
 * 解释器节点，实现了 {@link org.apache.calcite.rel.core.TableScan} 表扫描操作
 * 
 * 类作用详解：
 * TableScanNode 是 Calcite 解释器执行引擎中的核心节点类，负责在解释器模式下执行表扫描操作。
 * 它是解释器执行计划中的一个执行单元，对应于关系代数中的 TableScan 算子。
 * 
 * 核心功能：
 * 1. 支持多种表 SPI（Service Provider Interface）接口，包括：
 *    - ScannableTable: 简单的可扫描表，只能全表扫描
 *    - FilterableTable: 可过滤表，支持在扫描时应用过滤条件
 *    - ProjectableFilterableTable: 可投影可过滤表，支持投影和过滤下推
 *    - QueryableTable: 可查询表，支持 LINQ 风格的查询
 *    - Enumerable: 可枚举表，直接提供可枚举的数据源
 * 
 * 2. 与表进行协商，确定哪些过滤条件和投影操作可以由表本身实现（下推优化）
 *    - 将过滤条件和投影尽可能下推到数据源，减少数据传输和处理量
 *    - 对于表无法实现的过滤和投影，在 Enumerable 层面补充实现
 * 
 * 3. 将表数据转换为可枚举的 Row 对象流，供解释器执行引擎使用
 *    - Row 对象封装了一行数据，以 Object[] 形式存储
 *    - 使用 LINQ4J 的 Enumerable 接口实现延迟执行和流式处理
 * 
 * 4. 支持运行时过滤和投影的动态应用
 *    - 对于表拒绝的过滤条件，在运行时使用 Scalar 编译执行
 *    - 对于表拒绝的投影，在运行时使用 select 操作实现
 * 
 * 设计模式：
 * - 工厂模式：通过静态 create 方法根据表的类型创建相应的 TableScanNode 实例
 * - 策略模式：根据表支持的 SPI 接口类型，选择不同的创建和处理策略
 * - 责任链模式：依次尝试不同的表 SPI 接口，找到第一个支持的接口进行处理
 * 
 * 性能优化：
 * - 尽可能将过滤和投影下推到数据源，减少数据传输量
 * - 使用延迟执行（Lazy Evaluation），只在需要时才处理数据
 * - 使用不可变集合（ImmutableList、ImmutableIntList）提高线程安全性
 * 
 * 使用场景：
 * - 当查询优化器选择解释器执行模式时使用
 * - 适用于需要快速执行、不需要复杂优化的场景
 * - 常用于测试、调试和小规模数据查询
 * 
 * 与其他类的关系：
 * - 继承自 Node 接口，是解释器执行计划树中的一个节点
 * - 与 Compiler 协作，编译过滤条件为可执行的 Scalar
 * - 与 DataContext 交互，获取运行时环境信息
 * - 与各种 Table SPI 接口交互，获取表数据
 */
public class TableScanNode implements Node { // TableScanNode 类实现 Node 接口，表示表扫描节点
  // 私有构造方法，创建 TableScanNode 实例
  // 参数说明：
  // - compiler: 编译器对象，用于编译过滤条件和创建执行上下文
  // - rel: 表扫描关系节点，包含表的元数据信息
  // - enumerable: 可枚举的 Row 对象流，代表表的数据
  private TableScanNode(Compiler compiler, TableScan rel, // 私有构造方法，初始化表扫描节点
      Enumerable<Row> enumerable) { // 接收可枚举的 Row 流作为数据源
    compiler.enumerable(rel, enumerable); // 将表扫描关系节点和可枚举对象注册到编译器中，用于后续执行
  } // 构造方法结束，TableScanNode 创建完成

  // 实现接口方法，执行表扫描操作
  // 注意：此方法为空实现，因为实际的扫描操作已经在创建 enumerable 时完成
  // 解释器执行引擎通过遍历 enumerable 来获取数据，不需要额外的执行逻辑
  @Override public void run() { // 重写 Node 接口的 run 方法
    // nothing to do // 无需执行任何操作，数据已经在 enumerable 中准备好
  } // run 方法结束

  /** 创建 TableScanNode 的静态工厂方法
   *
   * <p>此方法是 TableScanNode 的核心入口，负责根据表支持的 SPI 接口类型创建相应的节点实例。
   * 
   * 方法功能详解：
   * 1. 尝试各种表 SPI 接口，按优先级顺序检查表支持的接口类型
   * 2. 与表协商，确定哪些过滤条件和投影操作可以由表实现
   * 3. 将表无法实现的过滤和投影操作添加到 Enumerable 实现中
   * 
   * 参数说明：
   * - compiler: 编译器对象，用于编译过滤条件和创建执行上下文
   * - rel: 表扫描关系节点，包含表的元数据信息
   * - filters: 过滤条件列表，包含需要应用的 RexNode 过滤表达式
   * - projects: 投影列索引列表，null 表示全列投影，否则只投影指定的列
   * 
   * 返回值：创建的 TableScanNode 实例
   * 
   * 异常情况：
   * - 如果表不支持任何已知的 SPI 接口，抛出 AssertionError
   * 
   * 处理流程：
   * 1. 检查表是否支持 ProjectableFilterableTable（最强大的接口，支持投影和过滤）
   * 2. 检查表是否支持 FilterableTable（支持过滤）
   * 3. 检查表是否支持 ScannableTable（只支持扫描）
   * 4. 检查表是否可以直接提供 Enumerable（最简单的接口）
   * 5. 检查表是否支持 QueryableTable（支持 LINQ 查询）
   * 6. 如果都不支持，抛出异常
   * 
   * 性能优化：
   * - 优先使用功能最强的接口，以便尽可能下推过滤和投影
   * - 减少数据传输和处理量
   * 
   * 使用示例：
   * TableScanNode node = TableScanNode.create(compiler, rel, filters, projects);
   */
  static TableScanNode create(Compiler compiler, TableScan rel, // 静态工厂方法，创建 TableScanNode 实例
      ImmutableList<RexNode> filters, @Nullable ImmutableIntList projects) { // 接收过滤条件和投影列表
    final RelOptTable relOptTable = rel.getTable(); // 获取表扫描关系节点中的优化表对象
    // 尝试解包为 ProjectableFilterableTable（可投影可过滤表）
    // 这是最强大的表接口，支持同时下推投影和过滤条件
    final ProjectableFilterableTable pfTable = // 声明可投影可过滤表变量
        relOptTable.unwrap(ProjectableFilterableTable.class); // 尝试将表解包为可投影可过滤表接口
    if (pfTable != null) { // 如果表支持可投影可过滤接口
      return createProjectableFilterable(compiler, rel, filters, projects, // 调用专门的方法创建节点
          pfTable); // 传入可投影可过滤表对象
    } // if 结束，已处理可投影可过滤表
    
    // 尝试解包为 FilterableTable（可过滤表）
    // 支持下推过滤条件，但不支持投影
    final FilterableTable filterableTable = // 声明可过滤表变量
        relOptTable.unwrap(FilterableTable.class); // 尝试将表解包为可过滤表接口
    if (filterableTable != null) { // 如果表支持可过滤接口
      return createFilterable(compiler, rel, filters, projects, // 调用专门的方法创建节点
          filterableTable); // 传入可过滤表对象
    } // if 结束，已处理可过滤表
    
    // 尝试解包为 ScannableTable（可扫描表）
    // 只支持全表扫描，不支持过滤和投影下推
    final ScannableTable scannableTable = // 声明可扫描表变量
        relOptTable.unwrap(ScannableTable.class); // 尝试将表解包为可扫描表接口
    if (scannableTable != null) { // 如果表支持可扫描接口
      return createScannable(compiler, rel, filters, projects, // 调用专门的方法创建节点
          scannableTable); // 传入可扫描表对象
    } // if 结束，已处理可扫描表
    
    // 尝试解包为 Enumerable（可枚举接口）
    // 表直接提供可枚举的数据流，是最简单的接口
    //noinspection unchecked // 抑制未检查的转换警告
    final Enumerable<Row> enumerable = relOptTable.unwrap(Enumerable.class); // 尝试将表解包为可枚举接口
    if (enumerable != null) { // 如果表支持可枚举接口
      return createEnumerable(compiler, rel, enumerable, null, filters, // 调用专门的方法创建节点
          projects); // 传入可枚举对象和过滤投影条件
    } // if 结束，已处理可枚举表
    
    // 尝试解包为 QueryableTable（可查询表）
    // 支持 LINQ 风格的查询操作
    final QueryableTable queryableTable = // 声明可查询表变量
        relOptTable.unwrap(QueryableTable.class); // 尝试将表解包为可查询表接口
    if (queryableTable != null) { // 如果表支持可查询接口
      return createQueryable(compiler, rel, filters, projects, // 调用专门的方法创建节点
          queryableTable); // 传入可查询表对象
    } // if 结束，已处理可查询表
    
    // 如果表不支持任何已知的 SPI 接口，抛出断言错误
    // 这通常表示表的实现有问题，或者需要添加新的 SPI 支持
    throw new AssertionError("cannot convert table " + relOptTable // 抛出断言错误，提示无法转换表
        + " to enumerable"); // 错误消息：无法将表转换为可枚举对象
  } // create 方法结束，已根据表类型创建相应的节点

  /**
   * 创建基于 ScannableTable 的 TableScanNode
   * 
   * 方法功能详解：
   * ScannableTable 是最简单的表接口，只支持全表扫描，不支持过滤和投影下推。
   * 此方法将表扫描结果转换为可枚举的 Row 对象流。
   * 
   * 处理流程：
   * 1. 调用表的 scan 方法执行全表扫描，返回 Object[] 数组的可枚举对象
   * 2. 使用 Enumerables.toRow 将 Object[] 转换为 Row 对象
   * 3. 调用 createEnumerable 方法，传入过滤和投影条件，在 Enumerable 层面实现
   * 
   * 性能特点：
   * - 必须扫描全表数据，无法利用过滤条件减少数据量
   - 所有列都会被读取，无法利用投影减少数据传输
   - 适用于小表或需要全表扫描的场景
   * 
   * 参数说明：
   * - compiler: 编译器对象
   * - rel: 表扫描关系节点
   * - filters: 过滤条件列表（将在 Enumerable 层面应用）
   * - projects: 投影列索引列表（将在 Enumerable 层面应用）
   * - scannableTable: 可扫描表对象
   * 
   * 返回值：创建的 TableScanNode 实例
   */
  private static TableScanNode createScannable(Compiler compiler, TableScan rel, // 私有静态方法，创建可扫描表节点
      ImmutableList<RexNode> filters, @Nullable ImmutableIntList projects, // 接收过滤条件和投影列表
      ScannableTable scannableTable) { // 接收可扫描表对象
    // 调用表的 scan 方法执行全表扫描，传入数据上下文
    // scan 方法返回 Object[] 数组的可枚举对象，每个数组代表一行数据
    final Enumerable<Row> rowEnumerable = // 声明 Row 可枚举对象变量
        Enumerables.toRow(scannableTable.scan(compiler.getDataContext())); // 将 Object[] 转换为 Row 对象
    // 调用 createEnumerable 方法创建节点
    // 参数说明：
    // - compiler: 编译器
    // - rel: 表扫描关系节点
    // - rowEnumerable: 可枚举的 Row 流
    // - null: 表不接受任何投影（acceptedProjects 为 null）
    // - filters: 表拒绝的过滤条件（需要在 Enumerable 层面应用）
    // - projects: 表拒绝的投影（需要在 Enumerable 层面应用）
    return createEnumerable(compiler, rel, rowEnumerable, null, filters, // 返回创建的节点
        projects); // 传入投影条件
  } // createScannable 方法结束

  /**
   * 创建基于 QueryableTable 的 TableScanNode
   * 
   * 方法功能详解：
   * QueryableTable 支持 LINQ 风格的查询操作，可以更灵活地处理数据。
   * 此方法根据表的元素类型（elementType）选择不同的处理策略：
   * 1. 如果元素类型是 Class，通过反射提取字段，将对象转换为 Row
   * 2. 如果元素类型不是 Class（如匿名类型），直接查询 Row 对象
   * 
   * 处理流程：
   * 1. 获取表的元素类型（elementType）
   * 2. 解析表的完全限定名，定位到表所在的 Schema
   * 3. 根据元素类型选择处理策略
   * 4. 调用 createEnumerable 方法，传入过滤和投影条件
   * 
   * 反射处理：
   * - 提取类的所有公共非静态字段
   * - 使用反射获取字段值，构建 Row 对象
   * - 处理 IllegalAccessException 异常
   * 
   * Schema 解析：
   * - 从根 Schema 开始，逐步导航到表所在的子 Schema
   * - 使用表的完全限定名（如 "schema.table"）进行导航
   * - 检查 Schema 是否为 null，防止空指针异常
   * 
   * 参数说明：
   * - compiler: 编译器对象
   * - rel: 表扫描关系节点
   * - filters: 过滤条件列表
   * - projects: 投影列索引列表
   * - queryableTable: 可查询表对象
   * 
   * 返回值：创建的 TableScanNode 实例
   */
  private static TableScanNode createQueryable(Compiler compiler, // 私有静态方法，创建可查询表节点
      TableScan rel, ImmutableList<RexNode> filters, @Nullable ImmutableIntList projects, // 接收过滤条件和投影列表
      QueryableTable queryableTable) { // 接收可查询表对象
    final DataContext root = compiler.getDataContext(); // 获取数据上下文，提供运行时环境信息
    final RelOptTable relOptTable = rel.getTable(); // 获取优化表对象
    final Type elementType = queryableTable.getElementType(); // 获取表的元素类型（如自定义类或匿名类型）
    SchemaPlus schema = root.getRootSchema(); // 从根 Schema 开始导航
    // 遍历表的完全限定名，跳过最后一个元素（表名），只处理 Schema 部分
    // 例如：["sales", "customers"] -> 只处理 "sales"
    for (String name : Util.skipLast(relOptTable.getQualifiedName())) { // 遍历 Schema 名称
      requireNonNull(schema, () -> // 检查 Schema 是否为 null，如果是则抛出异常
          "schema is null while resolving " + name + " for table" // 错误消息
              + relOptTable.getQualifiedName()); // 包含完全限定名
      schema = schema.subSchemas().get(name); // 获取子 Schema，继续导航
    } // for 循环结束，已定位到表所在的 Schema
    
    final Enumerable<Row> rowEnumerable; // 声明 Row 可枚举对象变量
    if (elementType instanceof Class) { // 如果元素类型是 Class（普通类）
      //noinspection unchecked // 抑制未检查的转换警告
      // 创建 Queryable 对象，传入元素类型和完全限定名
      // 这会查询表的所有数据，返回泛型为 Object 的可查询对象
      final Queryable<Object> queryable = // 声明可查询对象变量
          Schemas.queryable(root, (Class) elementType, // 传入元素类型和完全限定名
              relOptTable.getQualifiedName()); // 查询表数据
      
      // 构建字段列表，提取类的所有公共非静态字段
      ImmutableList.Builder<Field> fieldBuilder = ImmutableList.builder(); // 创建字段列表构建器
      Class type = (Class) elementType; // 获取元素类型的 Class 对象
      for (Field field : type.getFields()) { // 遍历类的所有字段
        if (ReflectUtil.isPublic(field) && !ReflectUtil.isStatic(field)) { // 检查字段是否为公共且非静态
          fieldBuilder.add(field); // 添加字段到列表
        } // if 结束，已添加字段
      } // for 循环结束，已收集所有符合条件的字段
      
      final List<Field> fields = fieldBuilder.build(); // 构建不可变的字段列表
      // 使用 select 操作将对象转换为 Row 对象
      // 对于每个对象，提取所有字段的值，构建 Row
      rowEnumerable = queryable.select(o -> { // 使用 select 转换操作
        final @Nullable Object[] values = new Object[fields.size()]; // 创建字段值数组
        for (int i = 0; i < fields.size(); i++) { // 遍历字段列表
          Field field = fields.get(i); // 获取当前字段
          try { // 尝试获取字段值
            values[i] = field.get(o); // 使用反射获取字段值
          } catch (IllegalAccessException e) { // 捕获非法访问异常
            throw new RuntimeException(e); // 包装为运行时异常抛出
          } // catch 结束
        } // for 循环结束，已提取所有字段值
        return new Row(values); // 返回包含字段值的 Row 对象
      }); // select 操作结束
    } else { // 如果元素类型不是 Class（如匿名类型）
      // 直接查询 Row 对象，不需要转换
      rowEnumerable = // 声明 Row 可枚举对象变量
          Schemas.queryable(root, Row.class, relOptTable.getQualifiedName()); // 查询 Row 对象
    } // if-else 结束，已根据元素类型选择处理策略
    
    // 调用 createEnumerable 方法创建节点
    // 参数说明：
    // - compiler: 编译器
    // - rel: 表扫描关系节点
    // - rowEnumerable: 可枚举的 Row 流
    // - null: 表不接受任何投影
    // - filters: 表拒绝的过滤条件
    // - projects: 表拒绝的投影
    return createEnumerable(compiler, rel, rowEnumerable, null, filters, // 返回创建的节点
        projects); // 传入投影条件
  } // createQueryable 方法结束

  /**
   * 创建基于 FilterableTable 的 TableScanNode
   * 
   * 方法功能详解：
   * FilterableTable 支持在扫描时应用过滤条件，但不支持投影下推。
   * 此方法将过滤条件传递给表，让表尽可能多地应用过滤，减少数据传输量。
   * 
   * 协商机制：
   * - 将过滤条件列表传递给表的 scan 方法
   * - 表可以选择接受或拒绝每个过滤条件
   * - 表返回修改后的过滤条件列表（mutableFilters）
   * - 检查表是否"发明"了新的过滤条件（不在原始列表中）
   * - 如果表发明了新条件，抛出异常（这是不允许的行为）
   * 
   * 性能优化：
   * - 将过滤尽可能下推到数据源，减少数据传输量
   * - 表可以利用索引或其他优化技术加速过滤
   * 
   * 参数说明：
   * - compiler: 编译器对象
   * - rel: 表扫描关系节点
   * - filters: 过滤条件列表
   * - projects: 投影列索引列表（将在 Enumerable 层面应用）
   * - filterableTable: 可过滤表对象
   * 
   * 返回值：创建的 TableScanNode 实例
   * 
   * 异常情况：
   * - 如果表发明了新的过滤条件，抛出异常
   */
  private static TableScanNode createFilterable(Compiler compiler, // 私有静态方法，创建可过滤表节点
      TableScan rel, ImmutableList<RexNode> filters, @Nullable ImmutableIntList projects, // 接收过滤条件和投影列表
      FilterableTable filterableTable) { // 接收可过滤表对象
    final DataContext root = compiler.getDataContext(); // 获取数据上下文
    // 创建可变的过滤条件列表副本，因为表可能会修改它
    final List<RexNode> mutableFilters = Lists.newArrayList(filters); // 创建过滤条件列表的可变副本
    // 调用表的 scan 方法，传入过滤条件列表
    // 表会尝试应用这些过滤条件，并返回修改后的列表
    // 返回值是 Object[] 数组的可枚举对象，每个数组代表一行数据
    final Enumerable<@Nullable Object[]> enumerable = // 声明可枚举对象变量
        filterableTable.scan(root, mutableFilters); // 调用表的 scan 方法，传入数据上下文和过滤条件
    
    // 检查表是否"发明"了新的过滤条件
    // 遍历修改后的过滤条件列表
    for (RexNode filter : mutableFilters) { // 遍历可变过滤条件列表
      if (!filters.contains(filter)) { // 如果过滤条件不在原始列表中
        // 表发明了新的过滤条件，这是不允许的行为
        // 抛出异常，提示表不能发明过滤条件
        throw RESOURCE.filterableTableInventedFilter(filter.toString()).ex(); // 抛出异常
      } // if 结束，已检查过滤条件
    } // for 循环结束，已检查所有过滤条件
    
    // 将 Object[] 转换为 Row 对象
    final Enumerable<Row> rowEnumerable = Enumerables.toRow(enumerable); // 转换为 Row 对象
    // 调用 createEnumerable 方法创建节点
    // 参数说明：
    // - compiler: 编译器
    // - rel: 表扫描关系节点
    // - rowEnumerable: 可枚举的 Row 流
    // - null: 表不接受任何投影
    // - mutableFilters: 表拒绝的过滤条件（需要在 Enumerable 层面应用）
    // - projects: 表拒绝的投影（需要在 Enumerable 层面应用）
    return createEnumerable(compiler, rel, rowEnumerable, null, // 返回创建的节点
        mutableFilters, projects); // 传入过滤条件和投影
  } // createFilterable 方法结束

  /**
   * 创建基于 ProjectableFilterableTable 的 TableScanNode
   * 
   * 方法功能详解：
   * ProjectableFilterableTable 是最强大的表接口，支持同时下推投影和过滤条件。
   * 此方法实现了复杂的协商机制，以最优化的方式应用投影和过滤。
   * 
   * 核心挑战：
   * - 过滤条件可能需要使用未投影的列
   * - 如果表拒绝某个过滤条件，但该条件使用了未投影的列，则无法在 Enumerable 层面应用
   * - 解决方案：扩展投影列表，包含过滤条件需要的所有列
   * 
   * 协商流程（使用无限循环实现重试）：
   * 1. 将过滤条件传递给表，让表尝试应用
   * 2. 分析过滤条件使用的字段（usedFields）
   * 3. 检查是否有使用的字段不在投影列表中
   * 4. 如果有，将这些字段添加到投影列表中，并重试
   * 5. 重复步骤 1-4，直到所有需要的字段都被投影
   * 6. 调用表的 scan 方法，传入过滤条件和投影
   * 
   * 拒绝处理：
   * - 如果表拒绝了某些投影（需要额外投影字段用于过滤），记录这些投影
   * - 在 Enumerable 层面应用这些投影，只保留原始需要的列
   * 
   * 性能优化：
   * - 尽可能下推投影和过滤，减少数据传输量
   * - 只投影必要的列，避免读取无用数据
   * - 使用重试机制确保过滤条件可以正确应用
   * 
   * 参数说明：
   * - compiler: 编译器对象
   * - rel: 表扫描关系节点
   * - filters: 过滤条件列表
   * - projects: 投影列索引列表
   * - pfTable: 可投影可过滤表对象
   * 
   * 返回值：创建的 TableScanNode 实例
   * 
   * 异常情况：
   * - 如果表发明了新的过滤条件，抛出异常
   */
  private static TableScanNode createProjectableFilterable(Compiler compiler, // 私有静态方法，创建可投影可过滤表节点
      TableScan rel, ImmutableList<RexNode> filters, @Nullable ImmutableIntList projects, // 接收过滤条件和投影列表
      ProjectableFilterableTable pfTable) { // 接收可投影可过滤表对象
    final DataContext root = compiler.getDataContext(); // 获取数据上下文
    final ImmutableIntList originalProjects = projects; // 保存原始投影列表，用于后续比较
    
    // 使用无限循环实现重试机制
    // 如果发现需要额外投影字段，修改投影列表并重新尝试
    for (;;) { // 无限循环，直到成功或抛出异常
      // 创建可变的过滤条件列表副本
      final List<RexNode> mutableFilters = Lists.newArrayList(filters); // 创建过滤条件列表的可变副本
      
      // 将投影列表转换为 int 数组
      // 如果投影列表为 null 或是全列投影，则传 null
      final int[] projectInts; // 声明投影数组变量
      if (projects == null // 如果投影列表为 null
          || projects.equals(TableScan.identity(rel.getTable()))) { // 或是全列投影（identity）
        projectInts = null; // 传 null 表示投影所有列
      } else { // 如果投影列表不为 null 且不是全列投影
        projectInts = projects.toIntArray(); // 将 ImmutableIntList 转换为 int 数组
      } // if-else 结束，已处理投影数组
      
      // 检查表是否"发明"了新的过滤条件
      for (RexNode filter : mutableFilters) { // 遍历可变过滤条件列表
        if (!filters.contains(filter)) { // 如果过滤条件不在原始列表中
          // 表发明了新的过滤条件，抛出异常
          throw RESOURCE.filterableTableInventedFilter(filter.toString()) // 抛出异常
              .ex(); // 获取异常对象
        } // if 结束，已检查过滤条件
      } // for 循环结束，已检查所有过滤条件
      
      // 分析过滤条件使用的字段
      // InputFinder 会遍历 RexNode 表达式树，找出所有引用的输入字段
      final ImmutableBitSet usedFields = // 声明使用字段集合变量
          RelOptUtil.InputFinder.bits(mutableFilters, null); // 找出过滤条件使用的所有字段
      
      // 检查是否有使用的字段不在投影列表中
      if (projects != null) { // 如果投影列表不为 null
        int changeCount = 0; // 记录修改次数
        for (int usedField : usedFields) { // 遍历使用的字段
          if (!projects.contains(usedField)) { // 如果使用的字段不在投影列表中
            // 这是一个问题：表拒绝了某个过滤条件，但该条件使用了未投影的列
            // 在 Enumerable 层面无法应用这个过滤条件（因为列不存在）
            // 解决方案：将这个字段添加到投影列表中，重新尝试
            projects = // 修改投影列表
                ImmutableIntList.copyOf( // 创建新的不可变列表
                    Iterables.concat(projects, ImmutableList.of(usedField))); // 将新字段添加到列表末尾
            ++changeCount; // 增加修改计数
          } // if 结束，已检查字段
        } // for 循环结束，已检查所有使用的字段
        
        if (changeCount > 0) { // 如果有修改
          continue; // 重新尝试，从循环开始
        } // if 结束，已处理修改
      } // if 结束，已检查投影列表
      
      // 调用表的 scan 方法，传入过滤条件和投影
      // 返回值是 Object[] 数组的可枚举对象，每个数组代表一行数据
      final Enumerable<@Nullable Object[]> enumerable1 = // 声明可枚举对象变量
          pfTable.scan(root, mutableFilters, projectInts); // 调用表的 scan 方法
      
      // 将 Object[] 转换为 Row 对象
      final Enumerable<Row> rowEnumerable = Enumerables.toRow(enumerable1); // 转换为 Row 对象
      
      // 确定拒绝的投影
      // 如果投影列表被修改（添加了额外字段），则需要在 Enumerable 层面重新投影
      final ImmutableIntList rejectedProjects; // 声明拒绝投影列表变量
      if (originalProjects == null || originalProjects.equals(projects)) { // 如果原始投影为 null 或未修改
        rejectedProjects = null; // 没有拒绝的投影
      } else { // 如果投影列表被修改
        // 我们投影了额外的列（因为过滤条件需要），现在需要重新投影，只保留原始需要的列
        // 使用 identity 创建 [0, 1, 2, ..., n-1] 的索引列表
        rejectedProjects = ImmutableIntList.identity(originalProjects.size()); // 创建索引列表
      } // if-else 结束，已确定拒绝的投影
      
      // 调用 createEnumerable 方法创建节点
      // 参数说明：
      // - compiler: 编译器
      // - rel: 表扫描关系节点
      // - rowEnumerable: 可枚举的 Row 流
      // - projects: 表接受的投影（可能包含额外字段）
      // - mutableFilters: 表拒绝的过滤条件
      // - rejectedProjects: 需要在 Enumerable 层面应用的投影
      return createEnumerable(compiler, rel, rowEnumerable, projects, // 返回创建的节点
          mutableFilters, rejectedProjects); // 传入过滤条件和拒绝的投影
    } // for 循环结束（实际上通过 return 退出）
  } // createProjectableFilterable 方法结束

  /**
   * 创建基于 Enumerable 的 TableScanNode
   * 
   * 方法功能详解：
   * 这是所有创建方法的最终入口，负责处理表拒绝的过滤条件和投影。
   * 此方法在 Enumerable 层面补充实现表无法下推的操作。
   * 
   * 处理流程：
   * 1. 如果有拒绝的过滤条件（rejectedFilters 不为空）：
   *    a. 将过滤条件组合成一个复合条件（AND 逻辑）
   *    b. 如果表已经应用了投影，需要重新映射过滤条件中的字段引用
   *    c. 编译过滤条件为可执行的 Scalar 对象
   *    d. 使用 where 操作在运行时应用过滤条件
   * 2. 如果有拒绝的投影（rejectedProjects 不为 null）：
   *    a. 使用 select 操作提取指定的列
   *    b. 创建新的 Row 对象，只包含需要的列
   * 3. 返回创建的 TableScanNode 实例
   * 
   * 过滤条件处理：
   * - 使用 RexUtil.composeConjunction 将多个过滤条件组合成一个 AND 条件
   * - 如果表已经应用了投影，使用 Mappings.target 重新映射字段引用
   * - 使用 Compiler.compile 将 RexNode 编译为可执行的 Scalar
   * - 使用 Context 执行 Scalar，传入当前行的值
   * - 使用 where 操作过滤数据，只保留满足条件的行
   * 
   * 投影处理：
   * - 使用 select 操作提取指定的列
   * - 根据 rejectedProjects 索引列表提取列值
   * - 创建新的 Row 对象，只包含需要的列
   * - 使用 Row.asCopy 创建行的副本，避免修改原始数据
   * 
   * 字段映射：
   * - 当表应用了投影后，字段的索引会发生变化
   * - 例如：原始表有列 [A, B, C, D]，投影 [B, D]，则 B 的索引从 1 变为 0，D 从 3 变为 1
   * - 使用 Mappings.target 创建映射关系，将原始索引映射到新索引
   * - 使用 RexUtil.apply 重新映射过滤条件中的字段引用
   * 
   * 参数说明：
   * - compiler: 编译器对象
   * - rel: 表扫描关系节点
   * - enumerable: 可枚举的 Row 流
   * - acceptedProjects: 表接受的投影（可能包含额外字段），null 表示表未接受任何投影
   * - rejectedFilters: 表拒绝的过滤条件列表（需要在 Enumerable 层面应用）
   * - rejectedProjects: 需要在 Enumerable 层面应用的投影索引列表，null 表示不需要
   * 
   * 返回值：创建的 TableScanNode 实例
   */
  private static TableScanNode createEnumerable(Compiler compiler, // 私有静态方法，创建可枚举节点
      TableScan rel, Enumerable<Row> enumerable, // 接收表扫描关系节点和可枚举 Row 流
      final @Nullable ImmutableIntList acceptedProjects, List<RexNode> rejectedFilters, // 接收接受的投影和拒绝的过滤
      final @Nullable ImmutableIntList rejectedProjects) { // 接收拒绝的投影
    // 处理拒绝的过滤条件
    if (!rejectedFilters.isEmpty()) { // 如果有拒绝的过滤条件
      // 将多个过滤条件组合成一个复合条件（使用 AND 逻辑）
      // 例如：filter1 AND filter2 AND filter3
      final RexNode filter = // 声明组合后的过滤条件变量
          RexUtil.composeConjunction(rel.getCluster().getRexBuilder(), // 使用 RexBuilder 构建复合条件
              rejectedFilters); // 传入拒绝的过滤条件列表
      
      // 重新映射过滤条件（如果表已经应用了投影）
      final RexNode filter2; // 声明重新映射后的过滤条件变量
      final RelDataType inputRowType; // 声明输入行类型变量
      if (acceptedProjects == null) { // 如果表未接受任何投影
        filter2 = filter; // 不需要重新映射，直接使用原始过滤条件
        inputRowType = rel.getRowType(); // 输入行类型就是表的原始行类型
      } else { // 如果表接受了投影
        // 创建映射关系：将原始字段索引映射到投影后的索引
        // 例如：acceptedProjects = [1, 3]，则映射为 0->1, 1->3
        final Mapping mapping = // 声明映射变量
            Mappings.target(acceptedProjects, // 接受的投影列表
                rel.getTable().getRowType().getFieldCount()); // 原始表的字段数量
        filter2 = RexUtil.apply(mapping, filter); // 应用映射，重新映射过滤条件中的字段引用
        
        // 构建投影后的行类型
        final RelDataTypeFactory.Builder builder = // 声明类型构建器变量
            rel.getCluster().getTypeFactory().builder(); // 获取类型工厂并创建构建器
        final List<RelDataTypeField> fieldList = // 声明字段列表变量
            rel.getTable().getRowType().getFieldList(); // 获取原始表的字段列表
        for (int acceptedProject : acceptedProjects) { // 遍历接受的投影索引
          builder.add(fieldList.get(acceptedProject)); // 添加投影的字段到类型构建器
        } // for 循环结束，已添加所有投影字段
        inputRowType = builder.build(); // 构建投影后的行类型
      } // if-else 结束，已处理过滤条件映射
      
      // 编译过滤条件为可执行的 Scalar 对象
      // Scalar 是一个可执行的表达式，可以在运行时计算结果
      final Scalar condition = // 声明条件变量
          compiler.compile(ImmutableList.of(filter2), inputRowType); // 编译过滤条件，传入输入行类型
      
      // 创建执行上下文，用于在运行时传递行数据
      final Context context = compiler.createContext(); // 创建上下文对象
      
      // 使用 where 操作应用过滤条件
      // where 操作会遍历 enumerable，对每一行应用过滤条件
      // 只保留满足条件的行
      enumerable = enumerable.where(row -> { // 使用 where 过滤操作
        context.values = row.getValues(); // 将当前行的值设置到上下文中
        Boolean b = (Boolean) condition.execute(context); // 执行过滤条件，获取结果
        return b != null && b; // 返回 true 表示保留该行（条件为 true）
      }); // where 操作结束
    } // if 结束，已处理拒绝的过滤条件
    
    // 处理拒绝的投影
    if (rejectedProjects != null) { // 如果有拒绝的投影
      final @Nullable Object[] values = new Object[rejectedProjects.size()]; // 创建值数组，用于存储投影后的列值
      // 使用 select 操作提取指定的列
      enumerable = // 重新赋值 enumerable
          enumerable.select(row -> { // 使用 select 转换操作
            final @Nullable Object[] inValues = row.getValues(); // 获取当前行的所有列值
            for (int i = 0; i < rejectedProjects.size(); i++) { // 遍历拒绝的投影索引
              values[i] = inValues[rejectedProjects.get(i)]; // 根据索引提取列值
            } // for 循环结束，已提取所有需要的列值
            return Row.asCopy(values); // 返回新的 Row 对象（包含投影后的列）
          }); // select 操作结束
    } // if 结束，已处理拒绝的投影
    
    // 创建并返回 TableScanNode 实例
    return new TableScanNode(compiler, rel, enumerable); // 调用构造方法创建节点
  } // createEnumerable 方法结束
} // TableScanNode 类结束
