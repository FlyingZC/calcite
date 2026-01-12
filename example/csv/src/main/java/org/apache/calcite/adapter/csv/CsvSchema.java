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
package org.apache.calcite.adapter.csv; // CSV适配器包，包含CSV数据源的实现

import org.apache.calcite.adapter.file.JsonScannableTable; // 导入JSON可扫描表类，用于处理JSON格式的数据文件
import org.apache.calcite.schema.Table; // 导入表接口，Calcite中所有表的基接口
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象模式类，作为所有模式实现的基类
import org.apache.calcite.util.Source; // 导入Source工具类，用于统一处理不同来源的文件资源
import org.apache.calcite.util.Sources; // 导入Sources工具类，提供创建Source对象的静态方法

import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map构建器，用于构建线程安全的映射表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.io.File; // 导入Java文件类，用于文件系统操作
import java.util.Map; // 导入Map接口，用于存储键值对映射关系

/**
 * Schema mapped onto a directory of CSV files. Each table in the schema
 * is a CSV file in that directory.
 * 映射到CSV文件目录的模式（Schema），模式中的每个表都是该目录中的一个CSV文件
 * 这个类是Calcite适配器模式的核心实现，负责管理CSV数据源的表结构
 * 继承自AbstractSchema，实现了Calcite的Schema接口，使得CSV文件可以作为SQL查询的数据源
 */
public class CsvSchema extends AbstractSchema { // CsvSchema类：CSV数据源的模式管理器，负责扫描目录并创建表映射
  private final File directoryFile; // 目录文件对象：存储CSV文件所在的目录路径，用于扫描该目录下的所有数据文件
  private final CsvTable.Flavor flavor; // 表类型枚举：指定创建的表类型（TRANSLATABLE可转换、SCANNABLE可扫描、FILTERABLE可过滤），影响查询优化策略
  private @Nullable Map<String, Table> tableMap; // 表映射表：延迟初始化的表名到表对象的映射，键为表名（文件名），值为Table实例，使用@Nullable标记可能为null

  /**
   * Creates a CSV schema.
   * 创建一个CSV模式实例
   *
   * @param directoryFile Directory that holds {@code .csv} files
   *                     directoryFile参数：包含.csv文件的目录路径
   * @param flavor     Whether to instantiate flavor tables that undergo
   *                   query optimization
   *                     flavor参数：表类型枚举，决定创建哪种类型的表对象，影响查询优化能力
   */
  public CsvSchema(File directoryFile, CsvTable.Flavor flavor) { // 构造方法：初始化CSV模式对象
    super(); // 调用父类AbstractSchema的构造方法，完成基础初始化
    this.directoryFile = directoryFile; // 保存目录文件引用，后续用于扫描目录下的CSV/JSON文件
    this.flavor = flavor; // 保存表类型枚举，后续创建表时根据此类型决定实例化哪个子类
  }

  /** Looks for a suffix on a string and returns
   * either the string with the suffix removed
   * or the original string.
   * 查找字符串的后缀，如果找到则返回移除后缀的字符串，否则返回原始字符串
   * 这是一个工具方法，用于处理文件名，支持去除.gz等压缩文件后缀
   */
  private static String trim(String s, String suffix) { // 静态方法：移除字符串后缀，如果后缀不存在则返回原字符串
    String trimmed = trimOrNull(s, suffix); // 调用trimOrNull方法尝试移除后缀，可能返回null
    return trimmed != null ? trimmed : s; // 如果移除成功（非null）则返回移除后的字符串，否则返回原字符串
  }

  /** Looks for a suffix on a string and returns
   * either the string with the suffix removed
   * or null.
   * 查找字符串的后缀，如果找到则返回移除后缀的字符串，否则返回null
   * 与trim方法不同，此方法在找不到后缀时返回null而不是原字符串
   */
  private static @Nullable String trimOrNull(String s, String suffix) { // 静态方法：移除字符串后缀，找不到后缀时返回null
    return s.endsWith(suffix) // 检查字符串是否以指定后缀结尾
        ? s.substring(0, s.length() - suffix.length()) // 如果以指定后缀结尾，则截取除后缀外的子字符串
        : null; // 如果不以指定后缀结尾，则返回null
  }

  @Override protected Map<String, Table> getTableMap() { // 重写父类方法：获取表名到表对象的映射，实现懒加载模式
    if (tableMap == null) { // 检查表映射是否已初始化（懒加载）
      tableMap = createTableMap(); // 如果未初始化，则调用createTableMap方法创建表映射
    }
    return tableMap; // 返回表映射，供Calcite查询引擎使用
  }

  private Map<String, Table> createTableMap() { // 私有方法：创建表映射，扫描目录下的所有CSV和JSON文件
    // Look for files in the directory ending in ".csv", ".csv.gz", ".json",
    // ".json.gz".
    // 在目录中查找以.csv、.csv.gz、.json、.json.gz结尾的文件
    final Source baseSource = Sources.of(directoryFile); // 创建目录的Source对象，作为计算相对路径的基准
    File[] files = directoryFile.listFiles((dir, name) -> { // 使用文件过滤器列出目录中的文件
      final String nameSansGz = trim(name, ".gz"); // 先尝试移除.gz后缀，处理压缩文件
      return nameSansGz.endsWith(".csv") // 检查文件名是否以.csv结尾（已移除.gz）
          || nameSansGz.endsWith(".json"); // 或检查文件名是否以.json结尾（已移除.gz）
    }); // 过滤出CSV和JSON文件（包括压缩格式）
    if (files == null) { // 检查文件列表是否为null（目录不存在或无法访问）
      System.out.println("directory " + directoryFile + " not found"); // 输出错误信息，提示目录未找到
      files = new File[0]; // 创建空数组，避免后续处理出现空指针异常
    }
    // Build a map from table name to table; each file becomes a table.
    // 构建从表名到表的映射；每个文件都成为一个表
    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建不可变Map的构建器，保证线程安全
    for (File file : files) { // 遍历所有符合条件的文件
      Source source = Sources.of(file); // 为每个文件创建Source对象，统一文件访问接口
      Source sourceSansGz = source.trim(".gz"); // 移除.gz后缀，处理压缩文件
      final Source sourceSansJson = sourceSansGz.trimOrNull(".json"); // 尝试移除.json后缀，检查是否为JSON文件
      if (sourceSansJson != null) { // 如果成功移除.json后缀，说明是JSON文件
        final Table table = new JsonScannableTable(source); // 创建JSON可扫描表对象，用于处理JSON格式数据
        builder.put(sourceSansJson.relative(baseSource).path(), table); // 将表名（相对路径）和表对象放入映射中
      }
      final Source sourceSansCsv = sourceSansGz.trimOrNull(".csv"); // 尝试移除.csv后缀，检查是否为CSV文件
      if (sourceSansCsv != null) { // 如果成功移除.csv后缀，说明是CSV文件
        final Table table = createTable(source); // 调用createTable方法创建CSV表对象（根据flavor类型决定具体实现）
        builder.put(sourceSansCsv.relative(baseSource).path(), table); // 将表名（相对路径）和表对象放入映射中
      }
    }
    return builder.build(); // 构建并返回不可变的表映射Map
  }

  /** Creates different sub-type of table based on the "flavor" attribute.
   * 根据flavor属性创建不同子类型的表对象
   * 这是工厂方法模式的实现，根据配置创建不同能力的表对象
   */
  private Table createTable(Source source) { // 私有方法：根据flavor类型创建相应的CSV表对象
    switch (flavor) { // 根据flavor枚举值进行分支处理
    case TRANSLATABLE: // 如果flavor为TRANSLATABLE（可转换类型）
      return new CsvTranslatableTable(source, null); // 创建可转换表，支持将查询转换为关系代数表达式，查询优化能力最强
    case SCANNABLE: // 如果flavor为SCANNABLE（可扫描类型）
      return new CsvScannableTable(source, null); // 创建可扫描表，支持全表扫描，查询优化能力中等
    case FILTERABLE: // 如果flavor为FILTERABLE（可过滤类型）
      return new CsvFilterableTable(source, null); // 创建可过滤表，支持在扫描时应用过滤条件，查询优化能力较弱
    default: // 如果flavor为未知类型
      throw new AssertionError("Unknown flavor " + this.flavor); // 抛出断言错误，表示程序逻辑错误
    }
  }
}
