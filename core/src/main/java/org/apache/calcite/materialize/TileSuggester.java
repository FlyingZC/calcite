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
// 声明包名，表示这个类属于 org.apache.calcite.materialize 包，用于处理物化视图相关功能
package org.apache.calcite.materialize;

// 导入 Calcite 工具类，提供各种实用方法
import org.apache.calcite.util.Util;

// 导入 Google Guava 库的不可变列表类，用于创建不可修改的集合
import com.google.common.collect.ImmutableList;
// 导入 Google Guava 库的不可变映射类，用于创建不可修改的键值对集合
import com.google.common.collect.ImmutableMap;

// 导入 Checker Framework 的可空注解，用于标记可能为 null 的返回值
import org.checkerframework.checker.nullness.qual.Nullable;
// 导入 Pentaho 聚合设计算法的 Algorithm 接口，定义了聚合设计算法的规范
import org.pentaho.aggdes.algorithm.Algorithm;
// 导入 Progress 接口，用于跟踪算法执行进度
import org.pentaho.aggdes.algorithm.Progress;
// 导入 Result 接口，表示算法执行的结果
import org.pentaho.aggdes.algorithm.Result;
// 导入 MonteCarloAlgorithm 类，这是蒙特卡洛算法的具体实现，用于选择最优的聚合组合
import org.pentaho.aggdes.algorithm.impl.MonteCarloAlgorithm;
// 导入 ArgumentUtils 工具类，提供参数处理和进度显示功能
import org.pentaho.aggdes.algorithm.util.ArgumentUtils;
// 导入 Aggregate 接口，表示一个聚合视图
import org.pentaho.aggdes.model.Aggregate;
// 导入 Attribute 接口，表示一个属性（维度列）
import org.pentaho.aggdes.model.Attribute;
// 导入 Dialect 接口，表示 SQL 方言（不同数据库的 SQL 语法差异）
import org.pentaho.aggdes.model.Dialect;
// 导入 Dimension 接口，表示一个维度
import org.pentaho.aggdes.model.Dimension;
// 导入 Measure 接口，表示一个度量（需要聚合的数值列）
import org.pentaho.aggdes.model.Measure;
// 导入 Parameter 接口，表示算法参数
import org.pentaho.aggdes.model.Parameter;
// 导入 Schema 接口，表示数据库模式（包含表、维度、度量等）
import org.pentaho.aggdes.model.Schema;
// 导入 StatisticsProvider 接口，提供统计信息（如行数、空间占用等）
import org.pentaho.aggdes.model.StatisticsProvider;
// 导入 Table 接口，表示一个数据表
import org.pentaho.aggdes.model.Table;

// 导入 Java IO 的 PrintWriter 类，用于向控制台输出文本
import java.io.PrintWriter;
// 导入 Java 的 List 接口，表示有序集合
import java.util.List;

/**
 * TileSuggester 类：用于为给定的 Lattice（晶格）推荐一组初始的 tiles（物化聚合视图）
 * 
 * 核心概念解释：
 * 1. Lattice（晶格）：在 Calcite 中，Lattice 是一个多维数据结构，包含一个事实表和多个维度表
 *    它定义了所有可能的聚合组合，形成一个多维立方体
 * 
 * 2. Tile（瓦片）：在 Lattice 中，一个 Tile 代表一个具体的物化聚合视图
 *    它包含一组维度列和度量列，是对事实表在特定维度组合上的预聚合
 *    例如：按 "年份" 和 "产品" 维度聚合销售额的视图就是一个 Tile
 * 
 * 3. 物化视图：预先计算并存储的查询结果，可以显著提高查询性能
 *    TileSuggester 的目标是从所有可能的聚合组合中，选择最有价值的一组进行物化
 * 
 * 4. 蒙特卡洛算法：一种基于随机抽样的算法，用于在大量可能组合中寻找近似最优解
 *    这里用于从 Lattice 的所有可能聚合中，选择收益最高的组合
 * 
 * 这个类的主要作用：
 * - 接收一个 Lattice 对象
 * - 使用蒙特卡洛算法分析所有可能的聚合组合
 * - 根据成本效益分析，推荐最优的一组 tiles 进行物化
 * - 返回推荐的结果
 * 
 * 使用场景：
 * - 数据仓库优化：自动推荐需要物化的聚合视图
 * - 查询性能优化：通过预计算常用聚合，加速 OLAP 查询
 * - 空间效率：在有限的存储空间内，选择最有价值的聚合组合
 */
public class TileSuggester {
  // 成员变量：存储 Lattice 对象的引用，final 表示不可重新赋值
  // Lattice 包含了所有维度列、度量列、统计信息等，是推荐算法的基础数据源
  private final Lattice lattice;

  /**
   * 构造方法：创建 TileSuggester 实例
   * 
   * @param lattice Lattice 对象，包含晶格的完整定义（列、统计信息等）
   * 
   * 作用：
   * - 初始化 TileSuggester，绑定到特定的 Lattice
   * - 保存 Lattice 引用，供后续算法使用
   * 
   * 参数说明：
   * - lattice：不能为 null，必须包含有效的列定义和统计信息
   */
  public TileSuggester(Lattice lattice) {
    // 将传入的 lattice 参数赋值给成员变量，保存引用
    this.lattice = lattice;
  }

  /**
   * 核心方法：推荐并返回最优的 tiles 集合
   * 
   * @return Iterable<? extends Lattice.Tile> 推荐的 tiles 集合，每个 tile 代表一个物化聚合视图
   * 
   * 方法流程详解：
   * 1. 创建蒙特卡洛算法实例
   * 2. 初始化进度跟踪器，用于显示算法执行进度
   * 3. 创建统计信息提供者，从 Lattice 获取行数等统计信息
   * 4. 配置算法参数（时间限制、聚合数量限制、成本限制）
   * 5. 创建 Schema 模型，将 Lattice 转换为算法可理解的格式
   * 6. 运行算法，获取推荐的聚合组合
   * 7. 将算法推荐的聚合转换为 Lattice.Tile 对象
   * 8. 返回最终的 tiles 集合
   * 
   * 算法参数说明：
   * - timeLimitSeconds：算法运行的最大时间限制（秒），避免计算时间过长
   * - aggregateLimit：推荐的聚合数量上限（3个），控制物化视图的数量
   * - costLimit：成本上限（事实表行数 * 5），限制存储空间占用
   * 
   * 返回值说明：
   * - 返回一个不可变的 tiles 集合
   * - 每个 tile 包含一组维度列和默认度量列
   * - tiles 按推荐价值排序（价值高的在前）
   */
  public Iterable<? extends Lattice.Tile> tiles() {
    // 步骤1：创建蒙特卡洛算法实例
    // MonteCarloAlgorithm 是一种基于随机抽样的优化算法
    // 它通过随机采样和评估，从大量可能的聚合组合中找到近似最优解
    final Algorithm algorithm = new MonteCarloAlgorithm();

    // 步骤2：创建 PrintWriter 对象，用于向标准输出（控制台）打印信息
    final PrintWriter pw = Util.printWriter(System.out);

    // 步骤3：创建文本进度跟踪器，在算法执行时显示进度信息
    // 这对于长时间运行的算法很有用，可以让用户知道算法正在工作
    final Progress progress = new ArgumentUtils.TextProgress(pw);

    // 步骤4：创建统计信息提供者
    // StatisticsProviderImpl 是内部类，实现了 StatisticsProvider 接口
    // 它从 Lattice 中获取各种统计信息，如事实表行数、各维度的行数等
    // 这些统计信息对于算法评估聚合的价值至关重要
    final StatisticsProvider statisticsProvider =
        new StatisticsProviderImpl(lattice);

    // 步骤5：获取事实表的行数
    // 这表示基础数据表的大小，用于计算成本和收益
    // 行数越大，说明数据量越大，物化的价值可能越高
    final double f = statisticsProvider.getFactRowCount();

    // 步骤6：创建参数映射构建器，用于配置算法参数
    // ImmutableMap.Builder 用于构建不可变的键值对集合
    final ImmutableMap.Builder<Parameter, Object> map = ImmutableMap.builder();

    // 步骤7：如果 lattice 中配置了算法最大运行时间，则设置时间限制参数
    // algorithmMaxMillis >= 0 表示需要限制运行时间
    if (lattice.algorithmMaxMillis >= 0) {
      // 设置时间限制参数，单位是秒
      // 将毫秒转换为秒，并确保至少为 1 秒
      // Math.max(1, ...) 确保时间限制至少为 1 秒，避免设置为 0 或负数
      map.put(Algorithm.ParameterEnum.timeLimitSeconds,
          Math.max(1, (int) (lattice.algorithmMaxMillis / 1000L)));
    }

    // 步骤8：设置聚合数量限制参数
    // 限制推荐的 tile 数量最多为 3 个
    // 这是为了控制物化视图的总数，避免占用过多存储空间
    // 数值 3 是经验值，可以根据实际需求调整
    map.put(Algorithm.ParameterEnum.aggregateLimit, 3);

    // 步骤9：设置成本限制参数
    // 成本上限 = 事实表行数 * 5
    // 成本通常指存储空间或计算资源消耗
    // 这个限制确保推荐的 tiles 不会占用过多存储空间
    // f * 5d 表示允许的存储空间是事实表的 5 倍
    map.put(Algorithm.ParameterEnum.costLimit, f * 5d);

    // 步骤10：创建 Schema 模型
    // SchemaImpl 是内部类，将 Lattice 转换为算法可理解的 Schema 格式
    // Schema 包含了表、属性、统计信息等，是算法的输入数据模型
    final SchemaImpl schema = new SchemaImpl(lattice, statisticsProvider);

    // 步骤11：运行算法
    // algorithm.run() 执行蒙特卡洛算法，传入 schema、参数和进度跟踪器
    // 算法会分析所有可能的聚合组合，评估它们的价值，并返回最优的推荐
    final Result result = algorithm.run(schema, map.build(), progress);

    // 步骤12：创建 tiles 列表构建器
    // 用于收集和构建最终的 tiles 集合
    final ImmutableList.Builder<Lattice.Tile> tiles = ImmutableList.builder();

    // 步骤13：遍历算法推荐的每个聚合
    // result.getAggregates() 返回算法推荐的所有聚合视图
    for (Aggregate aggregate : result.getAggregates()) {
      // 将每个聚合转换为 Lattice.Tile 对象，并添加到列表中
      // toTile() 是私有辅助方法，负责转换逻辑
      tiles.add(toTile(aggregate));
    }

    // 步骤14：构建并返回不可变的 tiles 集合
    // 返回的集合不能被修改，保证了数据的完整性
    return tiles.build();
  }

  /**
   * 私有辅助方法：将算法推荐的 Aggregate 转换为 Lattice.Tile
   * 
   * @param aggregate 算法推荐的聚合对象，包含一组维度属性
   * @return Lattice.Tile 转换后的 Tile 对象，包含维度和度量
   * 
   * 方法流程：
   * 1. 创建 TileBuilder，用于构建 Tile 对象
   * 2. 将 Lattice 的默认度量添加到 Tile 中
   * 3. 将 Aggregate 中的维度属性转换为 Lattice.Column 并添加到 Tile 中
   * 4. 构建并返回 Tile 对象
   * 
   * 注意事项：
   * - Tile 包含所有默认度量，而不是 Aggregate 中的度量
   * - 维度来自 Aggregate 的属性列表
   * - 转换时需要将 AttributeImpl 转换回 Lattice.Column
   */
  private Lattice.Tile toTile(Aggregate aggregate) {
    // 步骤1：创建 TileBuilder 对象
    // TileBuilder 是 Lattice.Tile 的构建器，使用构建器模式创建 Tile
    final Lattice.TileBuilder tileBuilder = new Lattice.TileBuilder();

    // 步骤2：遍历 Lattice 的默认度量列表
    // defaultMeasures 是 Lattice 中预定义的度量列（如 SUM、COUNT 等）
    // 这些度量会包含在所有推荐的 tiles 中
    for (Lattice.Measure measure : lattice.defaultMeasures) {
      // 将每个度量添加到 Tile 构建器中
      tileBuilder.addMeasure(measure);
    }

    // 步骤3：遍历 Aggregate 中的属性（维度）
    // aggregate.getAttributes() 返回这个聚合包含的所有维度属性
    for (Attribute attribute : aggregate.getAttributes()) {
      // 将属性转换为 Lattice.Column 并添加到 Tile 中
      // ((AttributeImpl) attribute).column 是类型转换，从 AttributeImpl 获取原始的 Lattice.Column
      // AttributeImpl 是内部类，包装了 Lattice.Column
      tileBuilder.addDimension(((AttributeImpl) attribute).column);
    }

    // 步骤4：构建并返回 Tile 对象
    // Tile 对象包含了维度和度量的完整定义
    return tileBuilder.build();
  }

  /**
   * SchemaImpl 内部类：基于 Lattice 实现 Schema 接口
   * 
   * Schema 接口定义了数据库模式的结构，包含表、属性、统计信息等
   * 这个类将 Calcite 的 Lattice 概念适配到 Pentaho 的算法模型
   * 
   * 作用：
   * - 作为算法和 Lattice 之间的适配器
   * - 将 Lattice 的列信息转换为算法可理解的属性列表
   * - 提供统计信息访问接口
   * 
   * 设计模式：适配器模式
   * - 将 Calcite 的 Lattice 接口适配到 Pentaho 的 Schema 接口
   * - 使算法可以独立于 Calcite 的具体实现
   */
  private static class SchemaImpl implements Schema {
    // 成员变量1：统计信息提供者
    // 用于获取行数、空间占用等统计信息
    private final StatisticsProvider statisticsProvider;

    // 成员变量2：表对象
    // 在 Lattice 中，实际上只有一个表（事实表）
    // TableImpl 是内部类，实现了 Table 接口
    private final TableImpl table;

    // 成员变量3：属性列表
    // 包含 Lattice 中所有列的属性表示
    // 使用不可变列表确保数据不会被修改
    private final ImmutableList<AttributeImpl> attributes;

    /**
     * SchemaImpl 构造方法
     * 
     * @param lattice Lattice 对象，包含列定义
     * @param statisticsProvider 统计信息提供者
     * 
     * 构造流程：
     * 1. 保存统计信息提供者引用
     * 2. 创建表对象（Lattice 只有一个表）
     * 3. 遍历 Lattice 的所有列，为每个列创建 AttributeImpl
     * 4. 构建不可变的属性列表
     */
    SchemaImpl(Lattice lattice, StatisticsProvider statisticsProvider) {
      // 保存统计信息提供者引用
      this.statisticsProvider = statisticsProvider;

      // 创建表对象，Lattice 中只有一个表
      this.table = new TableImpl();

      // 创建属性列表构建器
      final ImmutableList.Builder<AttributeImpl> attributeBuilder =
          ImmutableList.builder();

      // 遍历 Lattice 的所有列
      // lattice.columns 包含了事实表和所有维度表的列
      for (Lattice.Column column : lattice.columns) {
        // 为每个列创建 AttributeImpl 对象
        // AttributeImpl 包装了 Lattice.Column，使其符合 Attribute 接口
        // 传入 table 参数，建立属性与表的关联
        attributeBuilder.add(new AttributeImpl(column, table));
      }

      // 构建不可变的属性列表
      this.attributes = attributeBuilder.build();
    }

    /**
     * 获取表列表
     * 
     * @return 包含单个表的列表
     * 
     * 说明：
     * - Lattice 只有一个表（事实表）
     * - 返回包含 TableImpl 的不可变列表
     */
    @Override public List<? extends Table> getTables() {
      // 返回只包含一个表的列表
      return ImmutableList.of(table);
    }

    /**
     * 获取度量列表
     * 
     * @return 度量列表
     * 
     * 说明：
     * - 这个方法在当前实现中不被使用
     * - 抛出 UnsupportedOperationException 表示不支持此操作
     * - 算法通过其他方式获取度量信息
     */
    @Override public List<Measure> getMeasures() {
      // 抛出不支持操作异常
      throw new UnsupportedOperationException();
    }

    /**
     * 获取维度列表
     * 
     * @return 维度列表
     * 
     * 说明：
     * - 这个方法在当前实现中不被使用
     * - 抛出 UnsupportedOperationException 表示不支持此操作
     * - 算法通过属性列表获取维度信息
     */
    @Override public List<? extends Dimension> getDimensions() {
      // 抛出不支持操作异常
      throw new UnsupportedOperationException();
    }

    /**
     * 获取属性列表
     * 
     * @return 所有属性的不可变列表
     * 
     * 说明：
     * - 返回 Lattice 中所有列的属性表示
     * - 属性包括维度列和度量列
     * - 算法使用这些属性来构建聚合组合
     */
    @Override public List<? extends Attribute> getAttributes() {
      // 返回属性列表
      return attributes;
    }

    /**
     * 获取统计信息提供者
     * 
     * @return 统计信息提供者对象
     * 
     * 说明：
     * - 返回统计信息提供者，算法使用它获取行数、空间占用等信息
     * - 这些信息用于评估聚合的价值和成本
     */
    @Override public StatisticsProvider getStatisticsProvider() {
      // 返回统计信息提供者
      return statisticsProvider;
    }

    /**
     * 获取 SQL 方言
     * 
     * @return SQL 方言对象
     * 
     * 说明：
     * - 这个方法在当前实现中不被使用
     * - 抛出 UnsupportedOperationException 表示不支持此操作
     * - 算法不生成 SQL，因此不需要方言信息
     */
    @Override public Dialect getDialect() {
      // 抛出不支持操作异常
      throw new UnsupportedOperationException();
    }

    /**
     * 生成聚合 SQL 语句
     * 
     * @param aggregate 聚合对象
     * @param columnNameList 列名列表
     * @return SQL 语句字符串
     * 
     * 说明：
     * - 这个方法在当前实现中不被使用
     * - 抛出 UnsupportedOperationException 表示不支持此操作
     * - 算法只推荐聚合组合，不生成 SQL
     */
    @Override public String generateAggregateSql(Aggregate aggregate,
        List<String> columnNameList) {
      // 抛出不支持操作异常
      throw new UnsupportedOperationException();
    }
  }

  /**
   * TableImpl 内部类：基于 Lattice 实现 Table 接口
   * 
   * 说明：
   * - 在 Lattice 的上下文中，只有一个表（事实表）
   * - 这个表的概念与数据库表不同，更像是算法的一个逻辑容器
   * - 算法实际上不关心表的结构，它主要关注属性和统计信息
   * 
   * 设计考虑：
   * - Pentaho 算法模型要求有表的概念，但 Lattice 不需要
   * - 因此创建一个简单的表实现，满足接口要求即可
   */
  private static class TableImpl implements Table {
    /**
     * 获取表的标签
     * 
     * @return 表的标签字符串
     * 
     * 说明：
     * - 返回固定的字符串 "TABLE"
     * - 这个标签主要用于显示和识别，不影响算法逻辑
     */
    @Override public String getLabel() {
      // 返回固定的表标签
      return "TABLE";
    }

    /**
     * 获取父表
     * 
     * @return 父表对象，这里返回 null
     * 
     * 说明：
     * - Lattice 中没有父表的概念
     * - 返回 null 表示没有父表
     * - @Nullable 注解表示返回值可能为 null
     */
    @Override public @Nullable Table getParent() {
      // 返回 null，表示没有父表
      return null;
    }
  }

  /**
   * AttributeImpl 内部类：基于 Lattice.Column 实现 Attribute 接口
   * 
   * 作用：
   * - 将 Calcite 的 Lattice.Column 适配到 Pentaho 的 Attribute 接口
   * - 属性代表一个维度列，算法使用属性来构建聚合组合
   * 
   * 成员变量：
   * - column: 原始的 Lattice.Column 对象
   * - table: 所属的表对象
   * 
   * 设计模式：适配器模式
   * - 适配 Lattice.Column 到 Attribute 接口
   * - 保持对原始 Column 的引用，便于后续转换
   */
  private static class AttributeImpl implements Attribute {
    // 成员变量1：Lattice.Column 对象
    // 保存原始的列定义，包含列名、别名、类型等信息
    private final Lattice.Column column;

    // 成员变量2：所属的表对象
    // 建立属性与表的关联
    private final TableImpl table;

    /**
     * AttributeImpl 构造方法
     * 
     * @param column Lattice.Column 对象
     * @param table 所属的表对象
     * 
     * 说明：
     * - 保存列和表的引用
     * - 建立属性与列的映射关系
     */
    private AttributeImpl(Lattice.Column column, TableImpl table) {
      // 保存列引用
      this.column = column;

      // 保存表引用
      this.table = table;
    }

    /**
     * 转换为字符串
     * 
     * @return 属性的标签字符串
     * 
     * 说明：
     * - 重写 toString() 方法，返回属性的标签
     * - 用于调试和日志输出
     */
    @Override public String toString() {
      // 返回属性的标签
      return getLabel();
    }

    /**
     * 获取属性标签
     * 
     * @return 属性的标签（列的别名）
     * 
     * 说明：
     * - 返回列的别名作为属性标签
     * - 标签用于识别和显示属性
     */
    @Override public String getLabel() {
      // 返回列的别名
      return column.alias;
    }

    /**
     * 获取所属的表
     * 
     * @return 表对象
     * 
     * 说明：
     * - 返回属性所属的表
     * - 算法可能使用这个信息进行分组或关联
     */
    @Override public Table getTable() {
      // 返回表对象
      return table;
    }

    /**
     * 估计属性占用的空间
     * 
     * @return 空间估计值（这里返回 0）
     * 
     * 说明：
     * - 当前实现返回 0，表示不进行空间估计
     * - 算法可能使用这个信息评估聚合的成本
     * - 可以根据实际情况实现更精确的空间估计
     */
    @Override public double estimateSpace() {
      // 返回 0，表示不进行空间估计
      return 0;
    }

    /**
     * 获取候选列名
     * 
     * @return 候选列名（这里返回 null）
     * 
     * 说明：
     * - 当前实现返回 null
     * - 这个方法可能用于生成 SQL 或列映射
     * - @Nullable 注解表示返回值可能为 null
     */
    @Override public @Nullable String getCandidateColumnName() {
      // 返回 null
      return null;
    }

    /**
     * 获取数据类型
     * 
     * @param dialect SQL 方言
     * @return 数据类型字符串（这里返回 null）
     * 
     * 说明：
     * - 当前实现返回 null
     * - 这个方法可能用于生成 SQL 或类型转换
     * - @Nullable 注解表示返回值可能为 null
     */
    @Override public @Nullable String getDatatype(Dialect dialect) {
      // 返回 null
      return null;
    }

    /**
     * 获取祖先属性列表
     * 
     * @return 祖先属性列表（这里返回空列表）
     * 
     * 说明：
     * - 当前实现返回空列表
     * - 祖先属性可能用于层次维度（如 年->月->日）
     * - 返回空列表表示没有层次关系
     */
    @Override public List<Attribute> getAncestorAttributes() {
      // 返回空的不可变列表
      return ImmutableList.of();
    }
  }

  /**
   * StatisticsProviderImpl 内部类：实现 StatisticsProvider 接口
   * 
   * 作用：
   * - 为算法提供统计信息
   * - 从 Lattice 获取行数、空间占用等数据
   * - 这些统计信息用于评估聚合的价值和成本
   * 
   * 提供的统计信息：
   * - 事实表行数：基础数据量
   * - 属性组合的行数：聚合后的数据量
   * - 属性组合的空间占用：存储成本
   * - 属性组合的加载时间：计算成本
   * 
   * 设计模式：代理模式
   * - 作为 Lattice 的代理，提供统计信息访问接口
   * - 将算法的统计请求转发给 Lattice
   */
  private static class StatisticsProviderImpl implements StatisticsProvider {
    // 成员变量：Lattice 对象
    // 保存 Lattice 引用，用于获取统计信息
    private final Lattice lattice;

    /**
     * StatisticsProviderImpl 构造方法
     * 
     * @param lattice Lattice 对象
     * 
     * 说明：
     * - 保存 Lattice 引用
     * - 准备提供统计信息
     */
    StatisticsProviderImpl(Lattice lattice) {
      // 保存 Lattice 引用
      this.lattice = lattice;
    }

    /**
     * 获取事实表的行数
     * 
     * @return 事实表的行数
     * 
     * 说明：
     * - 返回基础事实表的总行数
     * - 这是评估聚合价值的重要基准
     * - 行数越大，物化的价值可能越高
     */
    @Override public double getFactRowCount() {
      // 调用 Lattice 的方法获取事实表行数
      return lattice.getFactRowCount();
    }

    /**
     * 获取属性组合的行数
     * 
     * @param attributes 属性列表（维度组合）
     * @return 该属性组合的行数
     * 
     * 说明：
     * - 返回按照给定属性（维度）分组后的行数
     * - 这个值表示聚合后的数据量
     * - 行数越少，说明聚合程度越高，查询性能提升越大
     * 
     * 实现细节：
     * - 使用 Util.transform 将 Attribute 列表转换为 Lattice.Column 列表
     * - 调用 Lattice.getRowCount() 获取该列组合的行数
     */
    @Override public double getRowCount(List<Attribute> attributes) {
      // 将 Attribute 列表转换为 Lattice.Column 列表
      // 使用 lambda 表达式进行转换：input -> ((AttributeImpl) input).column
      // 然后调用 Lattice.getRowCount() 获取行数
      return lattice.getRowCount(
          Util.transform(attributes, input -> ((AttributeImpl) input).column));
    }

    /**
     * 获取属性组合的空间占用
     * 
     * @param attributes 属性列表
     * @return 空间占用估计值
     * 
     * 说明：
     * - 返回存储该属性组合所需的空间
     * - 当前实现简单地返回属性数量
     * - 这是一个简化估计，实际空间占用取决于数据类型和基数
     * 
     * 改进方向：
     * - 可以考虑数据类型（整数比字符串占用更少空间）
     * - 可以考虑基数（高基数属性占用更多空间）
     * - 可以考虑压缩比例
     */
    @Override public double getSpace(List<Attribute> attributes) {
      // 简单地返回属性数量作为空间估计
      return attributes.size();
    }

    /**
     * 获取属性组合的加载时间
     * 
     * @param attributes 属性列表
     * @return 加载时间估计值
     * 
     * 说明：
     * - 返回构建该物化视图所需的时间
     * - 加载时间 = 空间占用 * 行数
     * - 这个公式假设加载时间与数据量成正比
     * 
     * 计算逻辑：
     * - getSpace(attributes) 获取空间占用
     * - getRowCount(attributes) 获取行数
     * - 两者相乘得到加载时间估计
     * 
     * 用途：
     * - 评估物化视图的维护成本
     * - 平衡查询性能提升和维护成本
     */
    @Override public double getLoadTime(List<Attribute> attributes) {
      // 加载时间 = 空间占用 * 行数
      return getSpace(attributes) * getRowCount(attributes);
    }
  }
}
