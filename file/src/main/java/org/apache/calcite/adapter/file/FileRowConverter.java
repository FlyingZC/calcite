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
package org.apache.calcite.adapter.file;

import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.avatica.util.DateTimeUtils;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.util.Pair;

import com.google.common.collect.ImmutableMap;
import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.calcite.util.Util.first;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Byte.parseByte;
import static java.util.Objects.requireNonNull;

/**
 * FileRowConverter - 文件行转换器类
 * 该类负责将HTML表格中的行数据转换为Java对象数组，用于Calcite文件适配器
 * 主要功能包括：
 * 1. 解析HTML表格的表头信息，建立列名到列索引的映射
 * 2. 根据字段配置定义每个列的数据类型和转换规则
 * 3. 将HTML表格行元素转换为对应的Java对象数组
 * 4. 支持投影操作，只提取指定的列
 * 5. 支持多种数据类型转换：布尔、字节、短整、整型、长整、浮点、双精度、日期、时间、时间戳、字符串
 * 6. 支持从HTML单元格中提取、替换和匹配文本内容
 * 
 * 工作流程：
 * 1. 延迟初始化：首次使用时才读取HTML表头并初始化字段定义
 * 2. 字段定义：根据配置或自动推断列名和数据类型
 * 3. 行转换：将HTML行元素按照字段定义转换为Java对象
 */
class FileRowConverter {

  // cache for lazy initialization - 延迟初始化缓存
  private final FileReader fileReader; // 文件读取器，用于读取HTML表格内容
  private final @Nullable List<Map<String, Object>> fieldConfigs; // 字段配置列表，每个Map包含一个列的配置信息（列名、类型、是否跳过等）
  private boolean initialized = false; // 初始化标志，标记是否已完成初始化

  // row parser configuration - 行解析器配置
  private final List<FieldDef> fields = new ArrayList<>(); // 字段定义列表，存储所有列的转换规则和元数据

  /** Format for parsing numbers. Not thread-safe, but we assume that only
   * one thread uses this converter at a time. */
  private final NumberFormat numberFormat =
      NumberFormat.getInstance(Locale.ROOT); // 数字格式化器，用于解析浮点数和长整型数字，使用ROOT语言环境确保格式一致

  /** Format for parsing integers. Not thread-safe, but we assume that only
   * one thread uses this converter at a time. */
  private final NumberFormat integerFormat =
      NumberFormat.getIntegerInstance(Locale.ROOT); // 整数格式化器，用于解析短整、整型数字，使用ROOT语言环境确保格式一致

  /** Creates a FileRowConverter. - 构造方法，创建文件行转换器实例
   * @param fileReader - 文件读取器，负责读取HTML表格内容
   * @param fieldConfigs - 字段配置列表，每个Map定义一个列的配置信息，可包含：
   *                      - "th": 表头名称，对应HTML表头的文本
   *                      - "name": 列别名，可选，如果不指定则使用表头名称
   *                      - "type": 数据类型，可选，支持BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, DATE, TIME, TIMESTAMP, STRING
   *                      - "skip": 是否跳过该列，可选，true表示不转换该列
   *                      - "selector": CSS选择器，用于从HTML单元格中选择元素
   *                      - "selectedElement": 选择的元素索引，用于从多个匹配元素中选择特定一个
   *                      - "replace": 正则表达式，用于替换单元格内容
   *                      - "replaceWith": 替换字符串，默认为空字符串
   *                      - "match": 正则表达式，用于从单元格内容中提取匹配的部分
   *                      - "matchSeq": 匹配序列号，当有多个匹配时选择第几个，默认为0
   * 注意：构造方法不立即初始化，而是延迟到首次使用时才初始化，以避免不必要的URL读取
   */
  FileRowConverter(FileReader fileReader,
      List<Map<String, Object>> fieldConfigs) {
    this.fileReader = fileReader; // 保存文件读取器引用
    this.fieldConfigs = fieldConfigs; // 保存字段配置列表引用
  }

  // initialize() - 初始化方法，结合HTML表头信息和字段定义来初始化表格读取器
  //      该方法执行以下操作：
  //      1. 读取HTML表格的表头元素
  //      2. 建立表头名称到列索引的映射
  //      3. 根据fieldConfigs配置创建字段定义
  //      4. 自动添加未显式配置的列
  // NB:  对象初始化延迟到首次使用时执行，以避免不必要的URL读取
  private void initialize() {
    if (this.initialized) { // 检查是否已初始化
      return; // 如果已初始化，直接返回
    }
    try {
      final Elements headerElements = this.fileReader.getHeadings(); // 从文件读取器获取HTML表头元素集合

      // create a name to index map for HTML table elements - 创建HTML表头名称到索引的映射
      final Map<String, Integer> headerMap = new LinkedHashMap<>(); // 使用LinkedHashMap保持插入顺序
      int i = 0; // 列索引计数器
      for (Element th : headerElements) { // 遍历所有表头元素
        String heading = th.text(); // 获取表头元素的文本内容作为列名
        if (headerMap.containsKey(heading)) { // 检查是否有重复的表头名称
          throw new Exception("duplicate heading: '" + heading + "'"); // 抛出异常，表头名称不能重复
        }
        headerMap.put(heading, i++); // 将表头名称和对应的列索引存入映射，索引递增
      }

      // instantiate the field definitions - 实例化字段定义
      final Set<String> colNames = new HashSet<>(); // 已使用的列名集合，用于检测重复
      final Set<String> sources = new HashSet<>(); // 已处理的源表头名称集合
      if (this.fieldConfigs != null) { // 如果有字段配置
        try {
          for (Map<String, Object> fieldConfig : this.fieldConfigs) { // 遍历每个字段配置

            String thName = (String) fieldConfig.get("th"); // 获取表头名称，对应HTML表头文本
            String name = thName; // 初始化列名为表头名称
            String newName; // 新列名变量
            FileFieldType type = null; // 数据类型，初始为null
            boolean skip = false; // 是否跳过该列，初始为false

            if (!headerMap.containsKey(thName)) { // 检查表头名称是否在HTML表头中存在
              throw new Exception("bad source column name: '" + thName + "'"); // 抛出异常，源列名不存在
            }
            if ((newName = (String) fieldConfig.get("name")) != null) { // 如果配置中指定了列别名
              name = newName; // 使用别名作为列名
            }
            if (colNames.contains(name)) { // 检查列名是否已存在
              throw new Exception("duplicate column name: '" + name + "'"); // 抛出异常，列名不能重复
            }

            String typeString = (String) fieldConfig.get("type"); // 获取类型字符串
            if (typeString != null) { // 如果指定了类型
              type = FileFieldType.of(typeString); // 将类型字符串转换为FileFieldType枚举
            }

            String sSkip = (String) fieldConfig.get("skip"); // 获取skip配置
            if (sSkip != null) { // 如果配置了skip
              skip = parseBoolean(sSkip); // 解析skip值为布尔类型
            }

            Integer sourceIx = headerMap.get(thName); // 获取源列的索引
            colNames.add(name); // 将列名添加到已使用集合
            sources.add(thName); // 将源表头名称添加到已处理集合
            if (!skip) { // 如果不跳过该列
              addFieldDef(name, type, fieldConfig, sourceIx); // 添加字段定义
            }
          }
        } catch (RuntimeException e) { // 捕获运行时异常
          throw e; // 直接重新抛出
        } catch (Exception e) { // 捕获其他异常
          throw new RuntimeException(e); // 包装为运行时异常后抛出
        }
      }

      // pick up any data elements not explicitly defined - 拾取任何未显式定义的数据元素
      for (Map.Entry<String, Integer> e : headerMap.entrySet()) { // 遍历表头映射
        final String name = e.getKey(); // 获取表头名称
        if (!sources.contains(name) && !colNames.contains(name)) { // 如果该列既未在配置中指定，也未作为别名使用
          addFieldDef(name, null, ImmutableMap.of(), e.getValue()); // 自动添加该列，类型为null（默认字符串），配置为空Map
        }
      }

    } catch (RuntimeException e) { // 捕获运行时异常
      throw e; // 直接重新抛出
    } catch (Exception e) { // 捕获其他异常
      throw new RuntimeException(e); // 包装为运行时异常后抛出
    }
    this.initialized = true; // 标记初始化完成
  }

  // add another field definition to the FileRowConverter during initialization - 在初始化期间向FileRowConverter添加另一个字段定义
  private void addFieldDef(String name, @Nullable FileFieldType type,
      Map<String, Object> config, int sourceCol) {
    this.fields.add(new FieldDef(name, type, config, sourceCol)); // 创建新的FieldDef对象并添加到字段列表中
  }

  /** Converts a row of JSoup Elements to an array of java objects. - 将一行JSoup元素转换为Java对象数组
   * @param rowElements - HTML表格行的元素集合，每个元素对应一个单元格
   * @param projection - 投影数组，指定要提取哪些列，数组中的值是字段索引
   * @return - Java对象数组，每个元素对应投影中的一个列，已根据字段定义转换为相应的Java类型
   * 投影机制：允许只提取指定的列，而不是所有列，提高查询效率
   */
  Object toRow(Elements rowElements, int[] projection) {
    initialize(); // 确保已初始化
    final Object[] objects = new Object[projection.length]; // 创建结果数组，长度等于投影数组长度

    for (int i = 0; i < projection.length; i++) { // 遍历投影数组
      int field = projection[i]; // 获取字段索引
      objects[i] = this.fields.get(field).convert(rowElements); // 调用对应字段的convert方法转换单元格内容
    }
    return objects; // 返回转换后的对象数组
  }

  int width() { // 获取字段数量（列数）
    initialize(); // 确保已初始化
    return this.fields.size(); // 返回字段列表的大小，即列数
  }

  RelDataType getRowType(JavaTypeFactory typeFactory) { // 获取行的数据类型，返回一个结构化类型描述所有列
    initialize(); // 确保已初始化
    List<String> names = new ArrayList<>(); // 列名列表
    List<RelDataType> types = new ArrayList<>(); // 列类型列表

    // iterate through FieldDefs, populating names and types - 遍历字段定义，填充列名和类型
    for (FieldDef f : this.fields) { // 遍历所有字段定义
      names.add(f.getName()); // 添加字段名称

      @Nullable FileFieldType fieldType = f.getType(); // 获取字段类型
      RelDataType type; // RelDataType变量

      if (fieldType == null) { // 如果字段类型为null
        type = typeFactory.createJavaType(String.class); // 创建字符串类型作为默认类型
      } else { // 如果字段类型不为null
        type = fieldType.toType(typeFactory); // 将FileFieldType转换为RelDataType
      }

      types.add(type); // 添加类型到类型列表
    }

    if (names.isEmpty()) { // 如果没有列（特殊情况，如空表）
      names.add("line"); // 添加默认列名"line"
      types.add(typeFactory.createJavaType(String.class)); // 添加字符串类型
    }

    return typeFactory.createStructType(Pair.zip(names, types)); // 创建结构化类型，将列名和类型配对
  }

  /** Parses an HTML table cell. - 解析HTML表格单元格的静态内部类
   * 该类负责从HTML单元格元素中提取、处理和转换文本内容
   * 支持以下功能：
   * 1. 使用CSS选择器选择单元格内的特定元素
   * 2. 从多个匹配元素中选择特定索引的元素
   * 3. 使用正则表达式替换文本内容
   * 4. 使用正则表达式提取匹配的文本片段
   * 5. 从多个匹配中选择特定序列的匹配
   */
  private static class CellReader {
    private final String selector; // CSS选择器，用于从单元格中选择元素，默认为"*"（选择所有元素）
    private final @Nullable Integer selectedElement; // 选择的元素索引，当有多个匹配元素时，选择特定索引的元素，null表示选择所有
    private final @Nullable Pattern replacePattern; // 替换正则表达式模式，用于替换单元格内容，null表示不替换
    private final String replaceWith; // 替换字符串，默认为空字符串
    private final @Nullable Pattern matchPattern; // 匹配正则表达式模式，用于从单元格内容中提取匹配的部分，null表示不提取
    private final int matchSeq; // 匹配序列号，当有多个匹配时选择第几个，默认为0（第一个匹配）

    CellReader(Map<String, Object> config) { // 构造方法，从配置中初始化CellReader
      final @Nullable String unusedType = (String) config.get("type"); // 获取类型配置（未使用）
      this.selector = first((String) config.get("selector"), "*"); // 获取CSS选择器，默认为"*"
      this.selectedElement = (Integer) config.get("selectedElement"); // 获取选择的元素索引
      @Nullable String replace = (String) config.get("replace"); // 获取替换正则表达式
      this.replacePattern = replace == null ? null : Pattern.compile(replace); // 编译替换正则表达式
      this.replaceWith = first((String) config.get("replaceWith"), ""); // 获取替换字符串，默认为空
      @Nullable String match = (String) config.get("match"); // 获取匹配正则表达式
      this.matchPattern = match == null ? null : Pattern.compile(match); // 编译匹配正则表达式
      this.matchSeq = first((Integer) config.get("matchSeq"), 0); // 获取匹配序列号，默认为0
    }

    @Nullable String read(Element cell) { // 读取单元格内容并返回处理后的文本
      ArrayList<String> cellText = new ArrayList<>(); // 存储单元格文本片段的列表

      if (this.selectedElement != null) { // 如果指定了选择的元素索引
        cellText.add(cell.select(this.selector) // 使用CSS选择器选择元素
            .get(this.selectedElement).ownText()); // 获取指定索引元素的纯文本（不包含子元素的文本）
      } else { // 如果没有指定选择的元素索引
        for (Element child : cell.select(this.selector)) { // 遍历所有匹配的元素
          // String tagName = child.tag().getName(); // （注释掉的代码）获取元素标签名
          cellText.add(child.ownText()); // 获取每个元素的纯文本
        }
      }

      String cellString = String.join(" ", cellText).trim(); // 将所有文本片段用空格连接，并去除首尾空格

      // replace - 执行替换操作
      if (this.replacePattern != null) { // 如果配置了替换正则表达式
        Matcher m = this.replacePattern.matcher(cellString); // 创建匹配器
        cellString = m.replaceAll(this.replaceWith); // 执行替换操作
      }

      // match - 执行匹配提取操作
      if (this.matchPattern == null) { // 如果没有配置匹配正则表达式
        return cellString; // 直接返回处理后的字符串
      } else { // 如果配置了匹配正则表达式
        List<String> allMatches = new ArrayList<>(); // 存储所有匹配结果的列表
        Matcher m = this.matchPattern.matcher(cellString); // 创建匹配器
        while (m.find()) { // 遍历所有匹配
          allMatches.add(m.group()); // 添加匹配的文本
        }
        if (!allMatches.isEmpty()) { // 如果有匹配结果
          return allMatches.get(this.matchSeq); // 返回指定序列号的匹配
        } else { // 如果没有匹配结果
          return null; // 返回null
        }
      }
    }
  }

  /** Responsible for managing field (column) definition,
   * and for converting an Element to a java data type. - 负责管理字段（列）定义并将元素转换为Java数据类型的内部类
   * 该类封装了单个列的所有转换逻辑：
   * 1. 存储列的名称、类型和配置信息
   * 2. 使用CellReader从HTML单元格中提取文本
   * 3. 将提取的文本转换为指定的Java类型
   * 4. 支持多种数据类型的转换
   */
  private class FieldDef {
    final String name; // 列名，不能为null
    final @Nullable FileFieldType type; // 字段类型，可为null（表示默认字符串类型）
    final Map<String, Object> config; // 字段配置Map，包含各种转换参数
    final CellReader cellReader; // 单元格读取器，负责从HTML单元格中提取文本
    final int cellSeq; // 单元格序列号，表示该字段在HTML表格中的列索引

    FieldDef(String name, @Nullable FileFieldType type,
        Map<String, Object> config, int cellSeq) { // 构造方法，初始化字段定义
      this.name = requireNonNull(name, "name"); // 验证列名不为null
      this.type = type; // 保存字段类型
      this.config = requireNonNull(config, "config"); // 验证配置不为null
      this.cellReader = new CellReader(config); // 创建单元格读取器
      this.cellSeq = cellSeq; // 保存单元格序列号
    }

    @Nullable Object convert(Elements row) { // 转换方法，将HTML行元素转换为Java对象
      return toObject(this.type, this.cellReader.read(row.get(this.cellSeq))); // 使用CellReader读取单元格文本，然后转换为Java对象
    }

    public String getName() { // 获取列名
      return this.name; // 返回列名
    }

    @Nullable FileFieldType getType() { // 获取字段类型
      return this.type; // 返回字段类型
    }

    private java.util.Date parseDate(String string) { // 解析日期字符串为java.util.Date对象
      Parser parser = new Parser(DateTimeUtils.UTC_ZONE); // 创建Natty日期解析器，使用UTC时区
      List<DateGroup> groups = parser.parse(string); // 解析日期字符串，获取日期组列表
      DateGroup group = groups.get(0); // 获取第一个日期组
      return group.getDates().get(0); // 返回第一个日期对象
    }

    @SuppressWarnings("JavaUtilDate") // 抑制使用java.util.Date的警告
    private @Nullable Object toObject(@Nullable FileFieldType fieldType,
        @Nullable String string) { // 将字符串转换为指定类型的Java对象
      if (string == null || string.isEmpty()) { // 如果字符串为null或空
        return null; // 返回null
      }

      if (fieldType == null) { // 如果字段类型为null
        return string; // 直接返回字符串
      }

      switch (fieldType) { // 根据字段类型进行转换
      case BOOLEAN: // 布尔类型
        return parseBoolean(string); // 解析为布尔值

      case BYTE: // 字节类型
        return parseByte(string); // 解析为字节

      case SHORT: // 短整型
        try {
          return integerFormat.parse(string).shortValue(); // 解析为短整型
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        }

      case INT: // 整型
        try {
          return integerFormat.parse(string).intValue(); // 解析为整型
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        }

      case LONG: // 长整型
        try {
          return numberFormat.parse(string).longValue(); // 解析为长整型
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        }

      case FLOAT: // 浮点型
        try {
          return numberFormat.parse(string).floatValue(); // 解析为浮点型
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        }

      case DOUBLE: // 双精度型
        try {
          return numberFormat.parse(string).doubleValue(); // 解析为双精度型
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        }

      case DATE: // 日期类型
        return new java.sql.Date(parseDate(string).getTime()); // 解析为java.sql.Date

      case TIME: // 时间类型
        return new java.sql.Time(parseDate(string).getTime()); // 解析为java.sql.Time

      case TIMESTAMP: // 时间戳类型
        return new java.sql.Timestamp(parseDate(string).getTime()); // 解析为java.sql.Timestamp

      case STRING: // 字符串类型
      default: // 默认情况
        return string; // 直接返回字符串
      }
    }
  }
}
