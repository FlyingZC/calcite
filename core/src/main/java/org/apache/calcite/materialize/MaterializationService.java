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
package org.apache.calcite.materialize; // 物化视图包,包含物化视图相关的核心类和接口

import org.apache.calcite.DataContext; // 数据上下文,提供查询执行时的运行时环境信息
import org.apache.calcite.DataContexts; // 数据上下文工具类,用于创建和操作DataContext
import org.apache.calcite.adapter.clone.CloneSchema; // 克隆模式,用于创建内存中的表副本
import org.apache.calcite.config.CalciteConnectionProperty; // Calcite连接属性配置
import org.apache.calcite.jdbc.CalciteConnection; // Calcite数据库连接接口
import org.apache.calcite.jdbc.CalciteMetaImpl; // Calcite元数据实现类
import org.apache.calcite.jdbc.CalcitePrepare; // Calcite查询准备接口
import org.apache.calcite.jdbc.CalciteSchema; // Calcite模式(schema)表示类,包含表、视图等元数据
import org.apache.calcite.linq4j.AbstractQueryable; // LINQ可查询对象抽象基类
import org.apache.calcite.linq4j.Enumerator; // LINQ枚举器接口,用于遍历查询结果
import org.apache.calcite.linq4j.QueryProvider; // LINQ查询提供者接口
import org.apache.calcite.linq4j.tree.Expression; // LINQ表达式树节点
import org.apache.calcite.prepare.Prepare; // 查询准备类,用于SQL解析和验证
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型,描述表或表达式的行类型
import org.apache.calcite.rel.type.RelDataTypeImpl; // 关系数据类型实现类
import org.apache.calcite.runtime.Hook; // 钩子机制,用于在特定点插入自定义逻辑
import org.apache.calcite.schema.Schemas; // 模式工具类,提供模式操作的各种静态方法
import org.apache.calcite.schema.Table; // 表接口,定义表的基本行为
import org.apache.calcite.util.ImmutableBitSet; // 不可变位集合,用于高效表示列索引集合
import org.apache.calcite.util.Pair; // 不可变键值对类
import org.apache.calcite.util.Util; // 通用工具类,提供各种辅助方法

import com.google.common.collect.ImmutableList; // Google Guava不可变列表
import com.google.common.collect.ImmutableMap; // Google Guava不可变映射

import org.checkerframework.checker.nullness.qual.Nullable; // 可空性注解,标记可能为null的类型

import java.lang.reflect.Type; // Java类型反射接口
import java.util.ArrayList; // 动态数组列表
import java.util.Comparator; // 比较器接口,用于定义对象排序规则
import java.util.Iterator; // 迭代器接口,用于遍历集合
import java.util.LinkedHashSet; // 链式哈希集合,保持插入顺序且元素唯一
import java.util.List; // 列表接口
import java.util.Map; // 映射接口
import java.util.PriorityQueue; // 优先级队列,基于堆实现
import java.util.Set; // 集合接口

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 空值转换工具,用于断言非空

import static java.util.Objects.requireNonNull; // 对象非空断言工具

/**
 * Manages the collection of materialized tables known to the system,
 * and the process by which they become valid and invalid.
 * 管理系统中已知的所有物化表集合,以及它们变为有效和无效的过程
 * 物化视图是预先计算并存储的查询结果,可以显著提高重复查询的性能
 * 此服务负责物化视图的注册、验证、创建、删除等生命周期管理
 */
public class MaterializationService { // 物化视图服务类,单例模式,管理整个系统的物化视图
  private static final MaterializationService INSTANCE = // 单例实例,全局唯一的物化视图服务对象
      new MaterializationService(); // 立即创建单例实例

  /** For testing. */ // 用于测试目的的线程本地实例
  private static final ThreadLocal<@Nullable MaterializationService> THREAD_INSTANCE = // 线程本地变量,每个测试线程可以有独立的物化视图服务实例
      ThreadLocal.withInitial(MaterializationService::new); // 使用lambda表达式初始化线程本地变量

  private static final Comparator<Pair<CalciteSchema.TableEntry, TileKey>> C = // 比较器,用于对物化表进行排序,优先选择行数较少的表进行上卷操作
      (o0, o1) -> { // Lambda表达式定义比较逻辑
        // We prefer rolling up from the table with the fewest rows. // 我们倾向于从行数最少的表进行上卷操作
        final Table t0 = o0.left.getTable(); // 获取第一个表对象
        final Table t1 = o1.left.getTable(); // 获取第二个表对象
        Double rowCount0 = t0.getStatistic().getRowCount(); // 获取第一个表的行数统计信息
        Double rowCount1 = t1.getStatistic().getRowCount(); // 获取第二个表的行数统计信息
        if (rowCount0 != null && rowCount1 != null) { // 如果两个表的行数都已知
          int c = Double.compare(rowCount0, rowCount1); // 比较行数大小
          if (c != 0) { // 如果行数不同
            return c; // 返回比较结果,行数小的排在前面
          }
        } else if (rowCount0 == null) { // 如果第一个表行数未知
          // Unknown is worse than known // 未知比已知更差
          return 1; // 返回正值,将未知行数的表排在后面
        } else { // 否则第二个表行数未知(rowCount1 == null)
          // rowCount1 == null -> Unknown is worse than known // 未知比已知更差
          return -1; // 返回负值,将已知行数的表排在前面
        }
        // Tie-break based on table name. // 基于表名进行决胜
        return o0.left.name.compareTo(o1.left.name); // 比较表名字典序
      };

  private final MaterializationActor actor = new MaterializationActor(); // 物化视图执行器,负责存储和管理所有物化视图的元数据和状态
  private final DefaultTableFactory tableFactory = new DefaultTableFactory(); // 默认表工厂,用于创建物化视图的存储表

  private MaterializationService() { // 私有构造函数,防止外部实例化,确保单例模式
  }

  /** Defines a new materialization. Returns its key. */ // 定义一个新的物化视图,返回其唯一键
  public @Nullable MaterializationKey defineMaterialization(final CalciteSchema schema, // 参数:目标schema,物化视图将在此schema中创建
      @Nullable TileKey tileKey, // 参数:可选的Tile键,用于关联到特定的多维数据集瓦片
      String viewSql, // 参数:物化视图的SQL查询语句,定义了物化视图的内容
      @Nullable List<String> viewSchemaPath, // 参数:可选的视图schema路径,用于解析SQL中的表引用
      final @Nullable String suggestedTableName, // 参数:建议的表名,可为null
      boolean create, // 参数:是否创建物化表,如果为false则只查找不创建
      boolean existing) { // 参数:是否使用已存在的表,如果为true则尝试查找现有表
    return defineMaterialization(schema, tileKey, viewSql, viewSchemaPath, // 调用重载方法,使用默认的tableFactory
        suggestedTableName, tableFactory, create, existing);
  }

  /** Defines a new materialization. Returns its key. */ // 定义一个新的物化视图(重载方法,接受自定义TableFactory),返回其唯一键
  public @Nullable MaterializationKey defineMaterialization(final CalciteSchema schema, // 参数:目标schema
      @Nullable TileKey tileKey, // 参数:可选的Tile键
      String viewSql, // 参数:物化视图SQL
      @Nullable List<String> viewSchemaPath, // 参数:视图schema路径
      @Nullable String suggestedTableName, // 参数:建议的表名
      TableFactory tableFactory, // 参数:表工厂,用于创建物化表
      boolean create, // 参数:是否创建
      boolean existing) { // 参数:是否使用已存在的表
    final MaterializationActor.QueryKey queryKey = // 创建查询键,用于唯一标识一个SQL查询
        new MaterializationActor.QueryKey(viewSql, schema, viewSchemaPath); // 基于SQL、schema和路径创建查询键
    final MaterializationKey existingKey = actor.keyBySql.get(queryKey); // 查找是否已存在相同SQL的物化视图
    if (existingKey != null) { // 如果找到已存在的物化视图
      return existingKey; // 返回已存在的物化视图键,避免重复创建
    }
    if (!create) { // 如果不允许创建新物化视图
      return null; // 返回null表示未找到且不创建
    }

    final CalciteConnection connection = // 创建Calcite连接,用于执行SQL和获取元数据
        CalciteMetaImpl.connect(schema.root(), null); // 连接到根schema
    CalciteSchema.TableEntry tableEntry; // 声明表条目变量,用于存储物化表的元数据
    // If the user says the materialization exists, first try to find a table // 如果用户表示物化视图已存在,首先尝试查找表
    // with the name and if none can be found, lookup a view in the schema // 如果找不到表,则在schema中查找视图
    if (existing) { // 如果existing参数为true
      requireNonNull(suggestedTableName, "suggestedTableName"); // 断言suggestedTableName不为null
      tableEntry = schema.getTable(suggestedTableName, true); // 在schema中查找指定名称的表(包括子schema)
      if (tableEntry == null) { // 如果未找到表
        tableEntry = schema.getTableBasedOnNullaryFunction(suggestedTableName, true); // 尝试基于无参函数查找(可能是视图)
      }
    } else { // 如果existing参数为false
      tableEntry = null; // 将tableEntry设为null,表示不查找现有表
    }
    if (tableEntry == null) { // 如果仍未找到表条目
      tableEntry = schema.getTableBySql(viewSql); // 尝试通过SQL查找表(可能已通过SQL注册)
    }

    RelDataType rowType = null; // 初始化行类型为null
    if (tableEntry == null) { // 如果仍未找到表条目,需要创建新的物化表
      Table table = tableFactory.createTable(schema, viewSql, viewSchemaPath); // 使用表工厂创建新表
      final String tableName = // 生成唯一的表名
          Schemas.uniqueTableName(schema, Util.first(suggestedTableName, "m")); // 使用建议表名或默认前缀"m"生成唯一名称
      tableEntry = schema.add(tableName, table, ImmutableList.of(viewSql)); // 将新表添加到schema,并关联SQL
      Hook.CREATE_MATERIALIZATION.run(tableName); // 触发物化视图创建钩子,可用于监控或日志记录
      rowType = table.getRowType(connection.getTypeFactory()); // 获取表的行类型(列定义)
    }

    if (rowType == null) { // 如果尚未获取行类型(即使用了现有表)
      // If we didn't validate the SQL by populating a table, validate it now. // 如果我们没有通过填充表来验证SQL,现在验证它
      final CalcitePrepare.ParseResult parse = // 解析SQL语句
          Schemas.parse(connection, schema, viewSchemaPath, viewSql); // 解析并验证SQL的语法和语义
      rowType = parse.rowType; // 从解析结果中获取行类型
    }
    final MaterializationKey key = new MaterializationKey(); // 创建新的物化视图键,用于唯一标识这个物化视图
    final MaterializationActor.Materialization materialization = // 创建物化视图对象,封装所有相关信息
        new MaterializationActor.Materialization(key, schema.root(), // 参数:键、根schema、
            tableEntry, viewSql, rowType, viewSchemaPath); // 参数:表条目、SQL、行类型、schema路径
    actor.keyMap.put(materialization.key, materialization); // 将物化视图对象存储在键映射中
    actor.keyBySql.put(queryKey, materialization.key); // 将查询键映射到物化视图键,用于通过SQL查找
    if (tileKey != null) { // 如果提供了Tile键
      actor.keyByTile.put(tileKey, materialization.key); // 将Tile键映射到物化视图键,用于多维数据集优化
    }
    return key; // 返回物化视图键
  }

  /** Checks whether a materialization is valid, and if so, returns the table // 检查物化视图是否有效,如果有效则返回存储数据的表
   * where the data are stored. */
  public CalciteSchema.@Nullable TableEntry checkValid(MaterializationKey key) { // 检查物化视图有效性并返回表条目
    final MaterializationActor.Materialization materialization = // 从actor的keyMap中查找物化视图对象
        actor.keyMap.get(key); // 使用物化视图键获取物化视图
    if (materialization != null) { // 如果找到了物化视图
      return materialization.materializedTable; // 返回物化表的表条目
    }
    return null; // 如果未找到或已失效,返回null
  }

  /**
   * Defines a tile. // 定义一个瓦片(Tile),瓦片是多维数据集的物化视图,包含特定维度组合和度量
   *
   * <p>Setting the {@code create} flag to false prevents a materialization // 设置create标志为false可以防止在物化视图不存在时创建它
   * from being created if one does not exist. Critically, it is set to false // 关键的是,在填充物化视图的递归SQL过程中该标志被设为false
   * during the recursive SQL that populates a materialization. Otherwise a // 否则,物化视图会试图创建自己来填充自己!这会导致无限递归
   * materialization would try to create itself to populate itself!
   */
  public @Nullable Pair<CalciteSchema.TableEntry, TileKey> defineTile(Lattice lattice, // 定义瓦片,返回表条目和Tile键的键值对
      ImmutableBitSet groupSet, // 参数:维度集合,使用位集合表示哪些列是维度
      List<Lattice.Measure> measureList, // 参数:度量列表,包含需要聚合计算的度量
      CalciteSchema schema, // 参数:目标schema
      boolean create, // 参数:是否创建物化视图
      boolean exact) { // 参数:是否要求精确匹配(不允许上卷)
    return defineTile(lattice, groupSet, measureList, schema, create, exact, // 调用重载方法,使用默认表名和默认tableFactory
        "m" + groupSet, tableFactory); // 默认表名为"m"加上维度集合
  }

  public @Nullable Pair<CalciteSchema.TableEntry, TileKey> defineTile(Lattice lattice, // 定义瓦片的完整实现(重载方法)
      ImmutableBitSet groupSet, // 参数:维度集合
      List<Lattice.Measure> measureList, // 参数:度量列表
      CalciteSchema schema, // 参数:目标schema
      boolean create, // 参数:是否创建
      boolean exact, // 参数:是否要求精确匹配
      String suggestedTableName, // 参数:建议的表名
      TableFactory tableFactory) { // 参数:自定义表工厂
    MaterializationKey materializationKey; // 声明物化视图键变量
    final TileKey tileKey = // 创建Tile键,用于唯一标识这个瓦片(维度+度量的组合)
        new TileKey(lattice, groupSet, ImmutableList.copyOf(measureList)); // 基于格网、维度集合和度量列表创建键

    // Step 1. Look for an exact match for the tile. // 第一步:查找精确匹配的瓦片
    materializationKey = actor.keyByTile.get(tileKey); // 尝试从keyByTile映射中获取完全相同的Tile键
    if (materializationKey != null) { // 如果找到精确匹配
      final CalciteSchema.TableEntry tableEntry = // 验证物化视图是否仍然有效
          checkValid(materializationKey); // 检查物化视图键对应的表是否有效
      if (tableEntry != null) { // 如果表仍然有效
        return Pair.of(tableEntry, tileKey); // 返回表条目和Tile键的键值对
      }
    }

    // Step 2. Look for a match of the tile with the same dimensionality and an // 第二步:查找具有相同维度但度量可接受的瓦片
    // acceptable list of measures. // 如果找到了相同维度的瓦片,并且其度量列表能满足当前需求,则可以复用
    final TileKey tileKey0 = // 创建只包含维度的Tile键(不含度量),用于按维度分组查找
        new TileKey(lattice, groupSet, ImmutableList.of()); // 度量列表为空
    for (TileKey tileKey1 : actor.tilesByDimensionality.get(tileKey0)) { // 遍历所有具有相同维度的瓦片
      assert tileKey1.dimensions.equals(groupSet); // 断言维度确实相同
      if (allSatisfiable(measureList, tileKey1)) { // 检查当前瓦片的度量是否能满足所需的度量列表
        materializationKey = actor.keyByTile.get(tileKey1); // 获取该瓦片的物化视图键
        if (materializationKey != null) { // 如果找到了物化视图
          final CalciteSchema.TableEntry tableEntry = // 验证有效性
              checkValid(materializationKey); // 检查表是否有效
          if (tableEntry != null) { // 如果有效
            return Pair.of(tableEntry, tileKey1); // 返回表条目和Tile键
          }
        }
      }
    }

    // Step 3. There's nothing at the exact dimensionality. Look for a roll-up // 第三步:没有精确维度的匹配,查找可以从更高维度上卷的瓦片
    // from tiles that have a super-set of dimensions and all the measures we // 查找维度是当前维度的超集,并且包含所有所需度量的瓦片
    // need. // 通过上卷(聚合)可以从更高维度的瓦片得到当前维度的数据
    //
    // If there are several roll-ups, choose the one with the fewest rows. // 如果有多个可上卷的瓦片,选择行数最少的那个(性能最优)
    //
    // TODO: Allow/deny roll-up based on a size factor. If the source is only // TODO: 基于大小因子允许/拒绝上卷。如果源表只比目标表大2倍,不要物化,但如果是3倍,则物化
    // say 2x larger than the target, don't materialize, but if it is 3x, do.
    //
    // TODO: Use a partially-ordered set data structure, so we are not scanning // TODO: 使用偏序集数据结构,避免扫描所有瓦片
    // through all tiles.
    if (!exact) { // 如果不要求精确匹配(允许上卷)
      final PriorityQueue<Pair<CalciteSchema.TableEntry, TileKey>> queue = // 创建优先级队列,用于选择最优的上卷源
          new PriorityQueue<>(1, C); // 使用比较器C(按行数排序)
      for (Map.Entry<TileKey, MaterializationKey> entry // 遍历所有已注册的瓦片
          : actor.keyByTile.entrySet()) { // 遍历Tile键到物化视图键的映射
        final TileKey tileKey2 = entry.getKey(); // 获取当前瓦片的Tile键
        if (tileKey2.lattice == lattice // 检查是否属于同一个格网
            && tileKey2.dimensions.contains(groupSet) // 检查维度是否是当前维度的超集
            && !tileKey2.dimensions.equals(groupSet) // 确保不是完全相同(需要上卷)
            && allSatisfiable(measureList, tileKey2)) { // 检查是否包含所有所需度量
          materializationKey = entry.getValue(); // 获取物化视图键
          final CalciteSchema.TableEntry tableEntry = // 验证有效性
              checkValid(materializationKey); // 检查表是否有效
          if (tableEntry != null) { // 如果有效
            queue.add(Pair.of(tableEntry, tileKey2)); // 将表条目和Tile键加入优先级队列
          }
        }
      }
      if (!queue.isEmpty()) { // 如果优先级队列不为空(找到了可上卷的瓦片)
        return queue.peek(); // 返回队列头部的最优瓦片(行数最少)
      }
    }

    // What we need is not there. If we can't create, we're done. // 所需的瓦片不存在,如果不允许创建,则返回null
    if (!create) { // 检查create标志
      return null; // 返回null表示未找到且不创建
    }

    // Step 4. Create the tile we need. // 第四步:创建所需的瓦片
    //
    // If there were any tiles at this dimensionality, regardless of // 如果此维度已存在瓦片(无论是否有效),创建一个更宽的瓦片
    // whether they were current, create a wider tile that contains their // 包含它们的度量加上当前请求的度量,然后可以使其他瓦片过时
    // measures plus the currently requested measures. Then we can obsolete all
    // other tiles.
    final List<TileKey> obsolete = new ArrayList<>(); // 创建过时瓦片列表
    final Set<Lattice.Measure> measureSet = new LinkedHashSet<>(); // 创建度量集合(使用LinkedHashSet保持顺序)
    for (TileKey tileKey1 : actor.tilesByDimensionality.get(tileKey0)) { // 遍历相同维度的所有瓦片
      measureSet.addAll(tileKey1.measures); // 将现有瓦片的度量添加到集合中
      obsolete.add(tileKey1); // 将这些瓦片标记为过时
    }
    measureSet.addAll(measureList); // 将当前请求的度量也添加到集合中
    final TileKey newTileKey = // 创建新的Tile键,包含所有度量的并集
        new TileKey(lattice, groupSet, ImmutableList.copyOf(measureSet)); // 基于格网、维度和合并后的度量列表创建键

    final String sql = lattice.sql(groupSet, newTileKey.measures); // 生成SQL语句,用于创建物化视图
    materializationKey = // 调用defineMaterialization方法创建物化视图
        defineMaterialization(schema, newTileKey, sql, schema.path(null), // 参数:schema、Tile键、SQL、schema路径
            suggestedTableName, tableFactory, true, false); // 参数:表名、表工厂、创建标志、不使用现有表
    if (materializationKey != null) { // 如果成功创建物化视图
      final CalciteSchema.TableEntry tableEntry = // 验证有效性
          checkValid(materializationKey); // 检查表是否有效
      if (tableEntry != null) { // 如果有效
        // Obsolete all of the narrower tiles. // 使所有更窄的瓦片(度量较少的)过时
        for (TileKey tileKey1 : obsolete) { // 遍历过时瓦片列表
          actor.tilesByDimensionality.remove(tileKey0, tileKey1); // 从维度映射中移除
          actor.keyByTile.remove(tileKey1); // 从Tile键映射中移除
        }

        actor.tilesByDimensionality.put(tileKey0, newTileKey); // 将新的更宽的瓦片添加到维度映射
        actor.keyByTile.put(newTileKey, materializationKey); // 将新的Tile键映射到物化视图键
        return Pair.of(tableEntry, newTileKey); // 返回新瓦片的表条目和Tile键
      }
    }
    return null; // 创建失败,返回null
  }

  private static boolean allSatisfiable(List<Lattice.Measure> measureList, // 静态方法:检查所有度量是否都能被Tile满足
      TileKey tileKey) { // 参数:需要的度量列表和Tile键
    // A measure can be satisfied if it is contained in the measure list, or, // 度量可以被满足的条件:要么在度量列表中,要么
    // less obviously, if it is composed of grouping columns. // 不太明显的是,如果它由分组列组成(即度量实际上是一个维度列)
    for (Lattice.Measure measure : measureList) { // 遍历所需的度量列表
      if (!(tileKey.measures.contains(measure) // 检查Tile的度量列表是否包含该度量
          || tileKey.dimensions.contains(measure.argBitSet()))) { // 或者检查Tile的维度是否包含该度量所依赖的列
        return false; // 如果都不满足,返回false
      }
    }
    return true; // 所有度量都能被满足,返回true
  }

  /** Gathers a list of all materialized tables known within a given root // 收集给定根schema中所有已知的物化表列表
   * schema. (Each root schema defines a disconnected namespace, with no overlap // 每个根schema定义了一个断开的命名空间,与当前schema没有重叠
   * with the current schema. Especially in a test run, the contents of two // 特别是在测试运行中,两个根schema的内容可能看起来相似
   * root schemas may look similar.) */
  public List<Prepare.Materialization> query(CalciteSchema rootSchema) { // 查询指定根schema中的所有物化视图
    final List<Prepare.Materialization> list = new ArrayList<>(); // 创建结果列表
    for (MaterializationActor.Materialization materialization // 遍历所有物化视图
        : actor.keyMap.values()) { // 遍历物化视图映射的所有值
      if (materialization.rootSchema.schema == rootSchema.schema // 检查物化视图是否属于指定的根schema
          && materialization.materializedTable != null) { // 并且物化表不为null
        list.add( // 将物化视图信息添加到结果列表
            new Prepare.Materialization(materialization.materializedTable, // 创建Materialization对象,包含表条目
                materialization.sql, // SQL语句
                requireNonNull(materialization.viewSchemaPath, // 视图schema路径(断言非空)
                    () -> "materialization.viewSchemaPath is null for " // 错误消息:如果为null则抛出异常
                        + materialization.materializedTable)));
      }
    }
    return list; // 返回物化视图列表
  }

  /** De-registers all materialized tables in the system. */ // 注销系统中的所有物化表
  public void clear() { // 清空所有物化视图
    actor.keyMap.clear(); // 清空物化视图键映射
  }

  /** Used by tests, to ensure that they see their own service. */ // 用于测试,确保测试线程看到自己的服务实例
  public static void setThreadLocal() { // 设置线程本地实例
    THREAD_INSTANCE.set(new MaterializationService()); // 为当前线程创建新的物化视图服务实例
  }

  /** Returns the instance of the materialization service. Usually the global // 返回物化视图服务的实例,通常是全局单例
   * one, but returns a thread-local one during testing (when // 但在测试时返回线程本地实例(当调用了setThreadLocal时)
   * {@link #setThreadLocal()} has been called by the current thread). */
  public static MaterializationService instance() { // 获取物化视图服务实例
    MaterializationService materializationService = THREAD_INSTANCE.get(); // 尝试获取线程本地实例
    if (materializationService != null) { // 如果线程本地实例存在(测试模式)
      return materializationService; // 返回线程本地实例
    }
    return INSTANCE; // 否则返回全局单例实例
  }

  public void removeMaterialization(MaterializationKey key) { // 移除指定的物化视图
    actor.keyMap.remove(key); // 从映射中删除该物化视图
  }

  /**
   * Creates tables that represent a materialized view. // 表工厂接口,用于创建代表物化视图的表
   */
  public interface TableFactory { // 表工厂接口,抽象化物化表的创建过程
    Table createTable(CalciteSchema schema, String viewSql, // 创建表的方法,接收schema、SQL和schema路径
        @Nullable List<String> viewSchemaPath); // 返回创建的表对象
  }

  /**
   * Default implementation of {@link TableFactory}. // TableFactory的默认实现
   * Creates a table using {@link CloneSchema}. // 使用CloneSchema创建表,将查询结果存储在内存中
   */
  public static class DefaultTableFactory implements TableFactory { // 默认表工厂实现类
    @Override public Table createTable(CalciteSchema schema, String viewSql, // 实现createTable方法
        @Nullable List<String> viewSchemaPath) { // 参数:schema、SQL、schema路径
      final CalciteConnection connection = // 创建Calcite连接
          CalciteMetaImpl.connect(schema.root(), null); // 连接到根schema
      final ImmutableMap<CalciteConnectionProperty, String> map = // 创建连接属性映射
          ImmutableMap.of(CalciteConnectionProperty.CREATE_MATERIALIZATIONS, // 禁止递归创建物化视图
              "false"); // 设置为false,防止物化视图创建时触发递归创建
      final CalcitePrepare.CalciteSignature<Object> calciteSignature = // 准备SQL查询,获取签名信息
          Schemas.prepare(connection, schema, viewSchemaPath, viewSql, map); // 解析和准备SQL,返回查询签名
      return CloneSchema.createCloneTable(connection.getTypeFactory(), // 使用CloneSchema创建克隆表
          RelDataTypeImpl.proto(castNonNull(calciteSignature.rowType)), // 表的行类型原型
          calciteSignature.getCollationList(), // 排序列表
          Util.transform(calciteSignature.columns, column -> column.type.rep), // 列类型表示
          new AbstractQueryable<Object>() { // 创建可查询对象,用于提供数据
            @Override public Enumerator<Object> enumerator() { // 实现枚举器方法,用于遍历数据
              final DataContext dataContext = // 创建数据上下文
                  DataContexts.of(connection, // 基于连接创建数据上下文
                      requireNonNull(calciteSignature.rootSchema, "rootSchema") // 获取根schema(断言非空)
                          .plus()); // 添加到schema路径
              return calciteSignature.enumerable(dataContext).enumerator(); // 返回可枚举对象的枚举器
            }

            @Override public Type getElementType() { // 实现获取元素类型方法
              return Object.class; // 返回Object类型
            }

            @Override public Expression getExpression() { // 实现获取表达式方法
              throw new UnsupportedOperationException(); // 抛出不支持操作异常(此方法不需要实现)
            }

            @Override public QueryProvider getProvider() { // 实现获取查询提供者方法
              return connection; // 返回Calcite连接作为查询提供者
            }

            @Override public Iterator<Object> iterator() { // 实现迭代器方法
              final DataContext dataContext = // 创建数据上下文
                  DataContexts.of(connection, // 基于连接创建
                      requireNonNull(calciteSignature.rootSchema, "rootSchema") // 获取根schema
                          .plus()); // 添加到schema路径
              return calciteSignature.enumerable(dataContext).iterator(); // 返回可枚举对象的迭代器
            }
          });
    }
  }
}
