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
// Apache Calcite 物化器核心类，负责将物化视图的定义SQL转换为关系代数表达式，并尝试使用星型表（StarTable）优化物化视图
package org.apache.calcite.prepare;

// 导入可枚举关系表达式接口，用于表示可以被枚举的关系代数节点
import org.apache.calcite.adapter.enumerable.EnumerableRel;
// 导入系统属性配置类，用于读取调试开关等配置
import org.apache.calcite.config.CalciteSystemProperty;
// 导入可绑定约定类，用于表示可以被解释器执行的关系约定
import org.apache.calcite.interpreter.BindableConvention;
// 导入Calcite准备接口，包含准备SQL查询所需的核心接口和类型
import org.apache.calcite.jdbc.CalcitePrepare;
// 导入Calcite模式类，表示Calcite中的schema，包含表、函数等元数据
import org.apache.calcite.jdbc.CalciteSchema;
// 导入关系优化集群类，用于管理关系代数节点的共享资源（如表达式工厂、类型工厂等）
import org.apache.calcite.plan.RelOptCluster;
// 导入关系优化物化视图类，包含物化视图的核心逻辑和转换方法
import org.apache.calcite.plan.RelOptMaterialization;
// 导入关系优化表接口，表示在优化器中的表元数据
import org.apache.calcite.plan.RelOptTable;
// 导入关系优化工具类，提供关系代数节点的字符串表示等实用方法
import org.apache.calcite.plan.RelOptUtil;
// 导入关系节点接口，所有关系代数节点的基类
import org.apache.calcite.rel.RelNode;
// 导入关系根节点类，表示关系代数树的根，包含原始SQL和优化后的关系表达式
import org.apache.calcite.rel.RelRoot;
// 导入模式工具类，提供从schema中获取特定类型表的方法
import org.apache.calcite.schema.Schemas;
// 导入表接口，表示数据库表的基本接口
import org.apache.calcite.schema.Table;
// 导入星型表实现类，星型表是一种特殊的表，用于优化星型模式的查询
import org.apache.calcite.schema.impl.StarTable;
// 导入SQL节点接口，表示SQL抽象语法树的节点
import org.apache.calcite.sql.SqlNode;
// 导入SQL解析异常类，当SQL解析失败时抛出
import org.apache.calcite.sql.parser.SqlParseException;
// 导入SQL解析器类，用于将SQL字符串解析为SQL抽象语法树
import org.apache.calcite.sql.parser.SqlParser;
// 导入SQL到Rex转换表接口，定义了如何将SQL函数调用转换为Rex表达式
import org.apache.calcite.sql2rel.SqlRexConvertletTable;
// 导入SQL到关系转换器类，负责将SQL抽象语法树转换为关系代数表达式树
import org.apache.calcite.sql2rel.SqlToRelConverter;

// 导入Google Guava的不可变列表类，用于创建不可修改的集合
import com.google.common.collect.ImmutableList;

// 导入ArrayList类，用于动态数组的实现
import java.util.ArrayList;
// 导入List接口，表示有序集合
import java.util.List;

// 导入requireNonNull静态方法，用于参数非空校验
import static java.util.Objects.requireNonNull;

/**
 * Context for populating a {@link Prepare.Materialization}. // 用于填充物化视图记录的上下文类，负责将物化视图的SQL定义转换为可用的关系代数表达式
 * 该类继承自CalcitePreparingStmt，是Calcite准备语句的实现，专门用于物化视图的处理
 * 主要功能：1. 解析物化视图的SQL定义并转换为关系代数表达式 2. 尝试使用星型表（StarTable）优化物化视图 3. 将物化表转换为关系代数表达式
 */
class CalciteMaterializer extends CalcitePrepareImpl.CalcitePreparingStmt { // 物化器类，继承自CalcitePreparingStmt，负责物化视图的准备和转换工作
  // 构造方法：初始化物化器实例
  // 参数说明：
  // - prepare: CalcitePrepareImpl实例，提供SQL准备的核心功能
  // - context: 准备上下文，包含连接信息、类型系统等
  // - catalogReader: 目录读取器，用于读取表、函数等元数据
  // - schema: Calcite模式，包含schema中的所有表和函数
  // - cluster: 关系优化集群，共享表达式工厂和类型工厂等资源
  // - convertletTable: SQL到Rex转换表，定义SQL函数到Rex表达式的转换规则
  CalciteMaterializer(CalcitePrepareImpl prepare, // Calcite准备实现类实例
      CalcitePrepare.Context context, // 准备上下文，提供运行时环境信息
      CatalogReader catalogReader, CalciteSchema schema, // 目录读取器和Calcite模式
      RelOptCluster cluster, SqlRexConvertletTable convertletTable) { // 关系优化集群和转换表
    // 调用父类构造方法，初始化所有必要的字段
    // 参数说明：
    // - prepare: 传递给父类的准备实现
    // - context: 传递给父类的上下文
    // - catalogReader: 传递给父类的目录读取器
    // - catalogReader.getTypeFactory(): 从目录读取器获取类型工厂，用于创建RelDataType
    // - schema: 传递给父类的模式
    // - EnumerableRel.Prefer.ANY: 表示对可枚举关系表达式无偏好
    // - cluster: 传递给父类的优化集群
    // - BindableConvention.INSTANCE: 使用可绑定约定，允许通过解释器执行
    // - convertletTable: 传递给父类的转换表
    super(prepare, context, catalogReader, catalogReader.getTypeFactory(), // 调用父类构造函数初始化基础功能
        schema, EnumerableRel.Prefer.ANY, cluster, BindableConvention.INSTANCE, // 设置模式和约定
        convertletTable); // 设置SQL到Rex转换表
  }

  /** Populates a materialization record, converting a table path // 填充物化视图记录，将表路径（字符串列表，如["hr", "sales"]）转换为可以在规划过程中使用的表对象
   * (essentially a list of strings, like ["hr", "sales"]) into a table object // 将表路径转换为表对象
   * that can be used in the planning process. */ // 该对象可用于查询规划过程
  // populate方法：填充物化视图记录的核心方法
  // 参数说明：
  // - materialization: 物化视图对象，包含物化视图的SQL定义和目标表路径
  // 方法功能：1. 解析物化视图的SQL定义 2. 将SQL转换为关系代数表达式 3. 尝试使用星型表优化 4. 将物化表转换为关系代数表达式
  void populate(Materialization materialization) { // 填充物化视图记录的方法
    // 创建SQL解析器，用于解析物化视图的SQL定义字符串
    // materialization.sql是物化视图定义的SQL语句，例如：SELECT emp.deptno, dept.name FROM emp, dept WHERE emp.deptno = dept.deptno
    SqlParser parser = SqlParser.create(materialization.sql); // 创建SQL解析器实例
    // 声明SQL节点变量，用于存储解析后的SQL抽象语法树
    SqlNode node; // SQL节点，表示解析后的SQL抽象语法树
    try { // 尝试解析SQL语句
      // 使用解析器解析SQL语句，生成SQL抽象语法树的根节点
      // parseStmt()方法会解析完整的SQL语句（包括SELECT、FROM、WHERE等子句）
      node = parser.parseStmt(); // 解析SQL语句为抽象语法树
    } catch (SqlParseException e) { // 捕获SQL解析异常
      // 如果解析失败，抛出运行时异常，包装原始异常信息
      // 这通常意味着SQL语法错误或不支持的SQL特性
      throw new RuntimeException("parse failed", e); // 抛出运行时异常，包装解析异常
    }
    // 创建SQL到关系转换器的配置对象
    // withTrimUnusedFields(true)表示在转换后移除未使用的字段，这样可以减少关系代数树的复杂度
    // 未使用的字段是指在查询中没有被引用、投影、过滤或聚合的字段
    final SqlToRelConverter.Config config = // 创建转换器配置对象
        SqlToRelConverter.config().withTrimUnusedFields(true); // 配置移除未使用字段
    // 获取SQL到关系转换器实例
    // 参数说明：
    // - getSqlValidator(): 获取SQL验证器，用于验证SQL语义（如表名、字段名是否存在，类型是否匹配等）
    // - catalogReader: 目录读取器，用于解析表和字段引用
    // - config: 转换器配置，包含是否移除未使用字段等选项
    SqlToRelConverter sqlToRelConverter2 = // 创建SQL到关系转换器
        getSqlToRelConverter(getSqlValidator(), catalogReader, config); // 获取转换器实例，传入验证器、目录读取器和配置

    // 将SQL抽象语法树转换为关系代数树
    // 参数说明：
    // - node: SQL抽象语法树的根节点
    // - true: 第一个true表示需要验证（validate），确保SQL语义正确
    // - true: 第二个true表示需要优化（optimize），应用规则优化关系代数树
    // 返回值RelRoot包含原始SQL节点、优化后的关系表达式、字段类型等信息
    RelRoot root = sqlToRelConverter2.convertQuery(node, true, true); // 将SQL转换为关系代数树
    // 将优化后的关系表达式存储到物化视图对象中
    // trimUnusedFields(root).rel: 移除未使用的字段，然后获取关系表达式部分
    // queryRel表示物化视图查询的关系代数表示
    materialization.queryRel = trimUnusedFields(root).rel; // 存储查询关系表达式，移除未使用字段

    // 识别并替换queryRel中的星型表（StarTable）
    // 星型表是一种特殊的表，用于优化星型模式（事实表连接多个维度表）的查询
    // 星型表将多个维度表预先连接并缓存，避免每次查询都重新计算连接
    //
    // 可能没有匹配的星型表。这是可以的，但识别的物化模式将不会那么丰富。
    // 这意味着物化视图仍然可以使用，但优化器能应用的优化规则会减少
    //
    // 可能会有多个星型表匹配。待定：我们应该取最好的（不管这意味着什么），还是全部？
    // 当前实现使用第一个匹配的星型表
    useStar(schema, materialization); // 尝试使用星型表优化物化视图

    // 获取物化表的路径
    // materializedTable.path()返回表路径列表，例如["hr", "emps"]表示hr schema下的emps表
    // 物化表是实际存储物化视图数据的表
    List<String> tableName = materialization.materializedTable.path(); // 获取物化表的路径
    // 从目录读取器中获取表对象
    // requireNonNull确保表存在，如果不存在则抛出NullPointerException
    // 表不存在时会抛出带有描述性消息的异常
    RelOptTable table = // 获取关系优化表对象
        requireNonNull(this.catalogReader.getTable(tableName), // 从目录中获取表，确保表存在
            () -> "table " + tableName + " is not found"); // 如果表不存在，提供错误消息
    // 将物化表转换为关系代数表达式
    // toRel方法将表转换为关系代数节点（通常是TableScan）
    // ImmutableList.of()表示没有投影列表，使用表的所有字段
    // tableRel表示物化表的关系代数表示，通常是一个TableScan节点
    materialization.tableRel = // 存储表关系表达式
        sqlToRelConverter2.toRel(table, ImmutableList.of()); // 将表转换为关系代数表达式
  }

  /** Converts a relational expression to use a // 将关系表达式转换为使用在schema中定义的星型表
   * {@link StarTable} defined in {@code schema}. // 使用schema中定义的星型表
   * Uses the first star table that fits. */ // 使用第一个匹配的星型表
  // useStar方法：尝试使用星型表优化关系表达式
  // 参数说明：
  // - schema: Calcite模式，包含所有可用的星型表
  // - materialization: 物化视图对象，包含queryRel和materializedTable等信息
  // 方法功能：遍历所有匹配的星型表，使用第一个匹配的星型表重写queryRel
  private void useStar(CalciteSchema schema, Materialization materialization) { // 使用星型表优化关系表达式
    // 从物化视图对象中获取查询关系表达式，确保queryRel不为null
    // queryRel是在populate方法中转换得到的关系代数表达式
    RelNode queryRel = requireNonNull(materialization.queryRel, "materialization.queryRel"); // 获取查询关系表达式，确保非空
    // 遍历所有匹配的星型表，useStar(schema, queryRel)返回匹配的星型表列表
    // 每个Callback对象包含重写后的关系表达式和星型表信息
    for (Callback x : useStar(schema, queryRel)) { // 遍历所有匹配的星型表回调
      // 成功 - 我们找到了匹配的星型表
      // 调用materialization.materialize方法，使用星型表重写的关系表达式和星型表元数据
      // x.rel: 重写后的关系表达式，使用星型表替代了原始的连接操作
      // x.starRelOptTable: 星型表的关系优化表对象，包含星型表的类型和元数据
      materialization.materialize(x.rel, x.starRelOptTable); // 使用星型表物化查询
      // 如果开启了调试模式，打印匹配信息
      // CalciteSystemProperty.DEBUG.value()读取系统属性calcite.debug的值
      if (CalciteSystemProperty.DEBUG.value()) { // 检查是否开启调试模式
        // 打印物化视图匹配星型表的信息
        // 包括：物化视图表名、匹配的星型表名、重写后的查询关系表达式
        System.out.println("Materialization " // 打印物化视图信息
            + materialization.materializedTable + " matched star table " // 打印匹配的星型表
            + x.starTable + "; query after re-write: " // 打印重写提示
            + RelOptUtil.toString(queryRel)); // 打印重写后的关系表达式
      }
    }
  }

  /** Converts a relational expression to use a // 将关系表达式转换为使用在schema中定义的星型表
   * {@link org.apache.calcite.schema.impl.StarTable} defined in {@code schema}. // 使用schema中定义的星型表
   * Uses the first star table that fits. */ // 使用第一个匹配的星型表
  // useStar重载方法：查找所有可以应用于关系表达式的星型表
  // 参数说明：
  // - schema: Calcite模式，包含所有可用的星型表
  // - queryRel: 查询关系表达式，需要被优化的关系代数树
  // 返回值：Callback对象的迭代器，每个Callback表示一个匹配的星型表
  // 方法功能：1. 获取schema中的所有星型表 2. 将queryRel转换为叶子连接形式 3. 尝试每个星型表是否能优化queryRel
  private Iterable<Callback> useStar(CalciteSchema schema, RelNode queryRel) { // 查找所有匹配的星型表
    // 从schema的根节点获取所有星型表
    // Schemas.getStarTables(schema.root())递归查找schema及其子schema中的所有星型表
    // 星型表是预先定义的，用于优化星型模式查询的特殊表
    List<CalciteSchema.TableEntry> starTables = // 获取所有星型表
        Schemas.getStarTables(schema.root()); // 从根schema获取所有星型表
    // 如果没有星型表，直接返回空列表
    // 没有星型表意味着无法进行星型表优化，但物化视图仍然可以使用
    if (starTables.isEmpty()) { // 检查是否有星型表
      // 不要浪费时间转换为叶子连接形式
      // 叶子连接形式是星型表匹配所需的特殊形式，如果没有星型表就不需要转换
      return ImmutableList.of(); // 返回空列表
    }
    // 创建Callback列表，用于存储所有匹配的星型表
    // 每个Callback包含重写后的关系表达式和星型表信息
    final List<Callback> list = new ArrayList<>(); // 创建回调列表
    // 将查询关系表达式转换为叶子连接形式
    // 叶子连接形式是一种特殊的关系代数树形式，其中所有连接都是叶子节点（即直接扫描表）
    // 这种形式便于星型表匹配和替换
    // RelOptMaterialization.toLeafJoinForm是静态方法，执行转换操作
    final RelNode rel2 = // 转换为叶子连接形式
        RelOptMaterialization.toLeafJoinForm(queryRel); // 将查询转换为叶子连接形式
    // 遍历每个星型表，尝试匹配
    for (CalciteSchema.TableEntry starTable : starTables) { // 遍历所有星型表
      // 获取星型表的Table对象
      // starTable.getTable()返回StarTable实例
      final Table table = starTable.getTable(); // 获取星型表对象
      // 断言表是StarTable类型
      // 这是类型安全检查，确保我们处理的是星型表
      assert table instanceof StarTable; // 断言表是星型表
      // 创建星型表的关系优化表对象
      // RelOptTableImpl.create创建关系优化表的实例
      // 参数说明：
      // - catalogReader: 目录读取器，用于解析表引用
      // - table.getRowType(typeFactory): 获取星型表的行类型（字段定义）
      // - starTable: 表条目，包含表名和schema信息
      // - null: 没有额外的表元数据
      RelOptTableImpl starRelOptTable = // 创建星型表的关系优化表对象
          RelOptTableImpl.create(catalogReader, table.getRowType(typeFactory), // 创建表对象，传入行类型
              starTable, null); // 传入表条目和空元数据
      // 尝试使用星型表优化关系表达式
      // RelOptMaterialization.tryUseStar是静态方法，尝试用星型表替换叶子连接形式的关系表达式
      // 参数说明：
      // - rel2: 叶子连接形式的关系表达式
      // - starRelOptTable: 星型表的关系优化表对象
      // 返回值：如果匹配成功，返回重写后的关系表达式；如果不匹配，返回null
      final RelNode rel3 = // 尝试使用星型表
          RelOptMaterialization.tryUseStar(rel2, starRelOptTable); // 尝试用星型表替换关系表达式
      // 如果匹配成功（rel3不为null），创建Callback对象并添加到列表
      if (rel3 != null) { // 检查是否匹配成功
        // 创建Callback对象，存储重写后的关系表达式和星型表信息
        // 参数说明：
        // - rel3: 重写后的关系表达式，使用星型表替代了原始连接
        // - starTable: 星型表的表条目
        // - starRelOptTable: 星型表的关系优化表对象
        list.add(new Callback(rel3, starTable, starRelOptTable)); // 添加回调对象到列表
      }
    }
    // 返回所有匹配的星型表回调列表
    return list; // 返回回调列表
  }

  /** Called when we discover a star table that matches. */ // 当发现匹配的星型表时调用
  // Callback内部类：星型表匹配的回调对象
  // 该类用于存储星型表匹配成功时的所有相关信息
  static class Callback { // 回调类，存储星型表匹配信息
    // 重写后的关系表达式，使用星型表替代了原始的连接操作
    // 这个关系表达式是优化后的，可以用于物化视图的查询重写
    public final RelNode rel; // 重写后的关系表达式
    // 星型表的表条目，包含表名、schema路径等信息
    // TableEntry是Calcite中表的元数据容器
    public final CalciteSchema.TableEntry starTable; // 星型表的表条目
    // 星型表的关系优化表对象，包含表的类型、字段信息等元数据
    // RelOptTable是优化器使用的表表示
    public final RelOptTableImpl starRelOptTable; // 星型表的关系优化表对象

    // Callback构造方法：初始化回调对象
    // 参数说明：
    // - rel: 重写后的关系表达式
    // - starTable: 星型表的表条目
    // - starRelOptTable: 星型表的关系优化表对象
    Callback(RelNode rel, // 关系表达式参数
        CalciteSchema.TableEntry starTable, // 星型表条目参数
        RelOptTableImpl starRelOptTable) { // 关系优化表参数
      // 将关系表达式赋值给成员变量
      this.rel = rel; // 初始化关系表达式
      // 将星型表条目赋值给成员变量
      this.starTable = starTable; // 初始化星型表条目
      // 将关系优化表对象赋值给成员变量
      this.starRelOptTable = starRelOptTable; // 初始化关系优化表对象
    }
  }
}
