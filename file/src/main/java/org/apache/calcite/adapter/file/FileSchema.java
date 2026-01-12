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
package org.apache.calcite.adapter.file; // 声明包名，该类属于org.apache.calcite.adapter.file包，是Calcite文件适配器的一部分

import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示可以嵌套的Schema，用于构建Schema层次结构
import org.apache.calcite.schema.Table; // 导入Table接口，代表Calcite中的表抽象
import org.apache.calcite.schema.impl.AbstractSchema; // 导入AbstractSchema抽象类，提供Schema的基础实现
import org.apache.calcite.util.Source; // 导入Source类，表示数据源（文件、URL等）的抽象
import org.apache.calcite.util.Sources; // 导入Sources工具类，用于创建和操作Source对象
import org.apache.calcite.util.Util; // 导入Util工具类，提供各种通用工具方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，用于创建不可修改的列表
import com.google.common.collect.ImmutableMap; // 导入Google Guava的不可变Map类，用于创建不可修改的Map

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的参数或返回值

import java.io.File; // 导入Java的File类，用于文件系统操作
import java.util.List; // 导入Java的List接口，用于列表操作
import java.util.Map; // 导入Java的Map接口，用于键值对映射

/**
 * Schema mapped onto a set of URLs / HTML tables. Each table in the schema
 * is an HTML table on a URL.
 * // 该类是一个基于文件系统的Schema实现，将文件系统中的文件映射为Calcite Schema中的表
 * // 支持CSV、JSON等格式的文件，每个文件可以作为一个表来查询
 * // 继承自AbstractSchema，实现了Schema接口的核心功能
 */
class FileSchema extends AbstractSchema { // FileSchema类继承AbstractSchema，实现文件系统到Calcite Schema的映射
  private final ImmutableList<Map<String, Object>> tables; // 成员变量：存储表定义的不可变列表，每个Map包含一个表的配置信息（如name、url等）
  private final @Nullable File baseDirectory; // 成员变量：基础目录，用于解析相对路径的文件，可为null表示不使用基础目录

  /**
   * Creates an HTML tables schema.
   * // 构造方法：创建FileSchema实例
   *
   * @param parentSchema  Parent schema // 父Schema，用于构建Schema层次结构
   * @param name          Schema name // Schema名称
   * @param baseDirectory Base directory to look for relative files, or null // 基础目录，用于查找相对路径的文件，可为null
   * @param tables        List containing HTML table identifiers, or null // 表定义列表，包含表的配置信息，可为null
   */
  FileSchema(SchemaPlus parentSchema, String name, @Nullable File baseDirectory, // 构造方法签名，接收父Schema、名称、基础目录和表定义列表
      @Nullable List<Map<String, Object>> tables) { // 构造方法参数续：表定义列表
    this.tables = // 初始化成员变量tables
        tables == null ? ImmutableList.of() // 如果tables参数为null，则创建空的不可变列表
            : ImmutableList.copyOf(tables); // 否则将传入的列表转换为不可变列表
    this.baseDirectory = baseDirectory; // 初始化成员变量baseDirectory，保存基础目录引用
  }

  /**
   * Looks for a suffix on a string and returns
   * either the string with the suffix removed
   * or the original string.
   * // 静态方法：去除字符串的指定后缀，如果字符串以该后缀结尾则返回去除后缀的字符串，否则返回原字符串
   */
  private static String trim(String s, String suffix) { // 静态方法签名，接收字符串和后缀参数
    String trimmed = trimOrNull(s, suffix); // 调用trimOrNull方法尝试去除后缀
    return trimmed != null ? trimmed : s; // 如果去除成功返回去除后的字符串，否则返回原字符串
  }

  /**
   * Looks for a suffix on a string and returns
   * either the string with the suffix removed
   * or null.
   * // 静态方法：去除字符串的指定后缀，如果字符串以该后缀结尾则返回去除后缀的字符串，否则返回null
   */
  private static @Nullable String trimOrNull(String s, String suffix) { // 静态方法签名，接收字符串和后缀参数，返回可能为null的字符串
    return s.endsWith(suffix) // 检查字符串是否以指定后缀结尾
        ? s.substring(0, s.length() - suffix.length()) // 如果是，返回去除后缀的子字符串
        : null; // 如果不是，返回null
  }

  @Override protected Map<String, Table> getTableMap() { // 重写父类方法：获取Schema中所有表的Map映射，键为表名，值为Table对象
    final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder(); // 创建不可变Map的构建器，用于构建表名到Table对象的映射

    for (Map<String, Object> tableDef : this.tables) { // 遍历表定义列表，处理每个显式定义的表
      addTable(builder, tableDef); // 调用addTable方法，将表定义添加到构建器中
    }

    // Look for files in the directory ending in ".csv", ".csv.gz", ".json",
    // ".json.gz".
    // 扫描基础目录中的CSV和JSON文件，自动将它们注册为表
    if (baseDirectory != null) { // 如果基础目录不为null，则扫描该目录
      final Source baseSource = Sources.of(baseDirectory); // 将基础目录转换为Source对象，用于后续路径计算
      File[] files = baseDirectory.listFiles((dir, name) -> { // 列出基础目录中符合条件的文件
        final String nameSansGz = trim(name, ".gz"); // 去除文件名的.gz后缀（处理压缩文件）
        return nameSansGz.endsWith(".csv") // 检查文件名是否以.csv结尾
            || nameSansGz.endsWith(".json"); // 或以.json结尾
      });
      if (files == null) { // 如果listFiles返回null（目录不存在或无权限）
        System.out.println("directory " + baseDirectory + " not found"); // 输出错误信息
        files = new File[0]; // 创建空数组，避免空指针异常
      }
      // Build a map from table name to table; each file becomes a table.
      // 将每个文件映射为一个表，构建表名到Table对象的映射
      for (File file : files) { // 遍历找到的所有文件
        Source source = Sources.of(file); // 将文件转换为Source对象
        Source sourceSansGz = source.trim(".gz"); // 去除Source的.gz后缀
        final Source sourceSansJson = sourceSansGz.trimOrNull(".json"); // 尝试去除.json后缀
        if (sourceSansJson != null) { // 如果去除.json成功，说明是JSON文件
          addTable(builder, source, sourceSansJson.relative(baseSource).path(), // 调用addTable方法添加JSON表，使用相对路径作为表名
              null); // tableDef参数为null，表示使用默认配置
        }
        final Source sourceSansCsv = sourceSansGz.trimOrNull(".csv"); // 尝试去除.csv后缀
        if (sourceSansCsv != null) { // 如果去除.csv成功，说明是CSV文件
          addTable(builder, source, sourceSansCsv.relative(baseSource).path(), // 调用addTable方法添加CSV表，使用相对路径作为表名
              null); // tableDef参数为null，表示使用默认配置
        }
      }
    }

    return builder.build(); // 构建并返回不可变的表名到Table对象的映射
  }

  private boolean addTable(ImmutableMap.Builder<String, Table> builder, // 私有方法：根据表定义添加表到构建器
      Map<String, Object> tableDef) { // 参数：表定义Map，包含name、url等配置信息
    final String tableName = (String) tableDef.get("name"); // 从表定义中获取表名
    final String url = (String) tableDef.get("url"); // 从表定义中获取URL
    final Source source0 = Sources.url(url); // 将URL转换为Source对象
    final Source source; // 声明最终的Source对象
    if (baseDirectory == null) { // 如果基础目录为null
      source = source0; // 直接使用URL创建的Source
    } else { // 如果基础目录不为null
      source = Sources.of(baseDirectory).append(source0); // 将基础目录和URL合并，创建相对路径的Source
    }
    return addTable(builder, source, tableName, tableDef); // 调用重载的addTable方法完成表的添加，返回添加结果
  }

  private static boolean addTable(ImmutableMap.Builder<String, Table> builder, // 私有静态方法：根据Source和表名添加表到构建器
      Source source, String tableName, @Nullable Map<String, Object> tableDef) { // 参数：Source对象、表名、表定义（可为null）
    final Source sourceSansGz = source.trim(".gz"); // 去除Source的.gz后缀（处理压缩文件）
    final Source sourceSansJson = sourceSansGz.trimOrNull(".json"); // 尝试去除.json后缀
    if (sourceSansJson != null) { // 如果去除.json成功，说明是JSON文件
      final Table table = new JsonScannableTable(source); // 创建JsonScannableTable实例，用于扫描JSON数据
      builder.put(Util.first(tableName, sourceSansJson.path()), table); // 将表添加到构建器，优先使用指定的表名，否则使用文件路径作为表名
      return true; // 返回true表示成功添加表
    }
    final Source sourceSansCsv = sourceSansGz.trimOrNull(".csv"); // 尝试去除.csv后缀
    if (sourceSansCsv != null) { // 如果去除.csv成功，说明是CSV文件
      final Table table = new CsvTranslatableTable(source, null); // 创建CsvTranslatableTable实例，用于转换CSV数据，第二个参数为null表示不使用特定类型推断
      builder.put(Util.first(tableName, sourceSansCsv.path()), table); // 将表添加到构建器，优先使用指定的表名，否则使用文件路径作为表名
      return true; // 返回true表示成功添加表
    }

    if (tableDef != null) { // 如果表定义不为null，尝试创建自定义的FileTable
      try { // 开始异常处理
        FileTable table = FileTable.create(source, tableDef); // 调用FileTable.create方法创建自定义表实例
        builder.put(Util.first(tableName, source.path()), table); // 将表添加到构建器，优先使用指定的表名，否则使用文件路径作为表名
        return true; // 返回true表示成功添加表
      } catch (Exception e) { // 捕获创建表时的异常
        throw new RuntimeException("Unable to instantiate table for: " // 抛出运行时异常，提示无法实例化表
            + tableName); // 异常消息包含表名
      }
    }

    return false; // 返回false表示未能添加表（不支持的文件类型或配置）
  }
} // 类结束
