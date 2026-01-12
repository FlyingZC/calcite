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
package org.apache.calcite.adapter.druid; // Druid连接器适配器包，提供与Druid数据源的连接和交互功能

import org.apache.calcite.avatica.AvaticaUtils; // Avatica工具类，提供通用的实用方法，如读取流到字节数组
import org.apache.calcite.avatica.ColumnMetaData; // Avatica列元数据，描述列的类型和属性，包括Java类型表示
import org.apache.calcite.avatica.util.DateTimeUtils; // Avatica日期时间工具类，提供日期时间格式化和解析功能
import org.apache.calcite.config.CalciteSystemProperty; // Calcite系统属性配置，用于调试等系统级设置
import org.apache.calcite.interpreter.Row; // Calcite解释器行对象，表示数据行，支持按字段名或索引访问
import org.apache.calcite.interpreter.Sink; // Calcite解释器数据接收器，用于接收处理后的数据行
import org.apache.calcite.linq4j.AbstractEnumerable; // LINQ4J抽象可枚举集合基类，提供可枚举的集合实现
import org.apache.calcite.linq4j.Enumerable; // LINQ4J可枚举集合接口，支持LINQ风格的查询操作
import org.apache.calcite.linq4j.Enumerator; // LINQ4J枚举器接口，用于遍历集合中的元素
import org.apache.calcite.sql.type.SqlTypeName; // SQL类型名称枚举，定义SQL标准类型
import org.apache.calcite.util.Holder; // Calcite持有者工具类，用于持有和传递值
import org.apache.calcite.util.Util; // Calcite通用工具类，提供各种实用方法

import com.fasterxml.jackson.core.JsonFactory; // Jackson JSON工厂，用于创建JSON解析器和生成器
import com.fasterxml.jackson.core.JsonParser; // Jackson JSON解析器，用于解析JSON数据流
import com.fasterxml.jackson.core.JsonToken; // Jackson JSON标记，表示JSON数据流中的标记类型
import com.fasterxml.jackson.databind.DeserializationFeature; // Jackson反序列化特性配置
import com.fasterxml.jackson.databind.ObjectMapper; // Jackson对象映射器，用于JSON和Java对象的相互转换
import com.fasterxml.jackson.databind.type.CollectionType; // Jackson集合类型，用于描述集合类型的Java类型
import com.google.common.collect.ImmutableMap; // Google Guava不可变Map，提供线程安全的不可变映射
import com.google.common.collect.ImmutableSet; // Google Guava不可变Set，提供线程安全的不可变集合

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework可空注解，用于标记可能为null的值
import org.joda.time.Interval; // Joda时间间隔类，表示时间范围

import java.io.ByteArrayInputStream; // 字节数组输入流，从字节数组读取数据
import java.io.IOException; // IO异常，表示输入输出操作失败
import java.io.InputStream; // 输入流基类，用于读取数据
import java.nio.charset.StandardCharsets; // 标准字符集，定义常见的字符编码
import java.text.ParseException; // 解析异常，表示文本解析失败
import java.text.SimpleDateFormat; // 简单日期格式化类，用于日期的格式化和解析
import java.util.ArrayList; // 数组列表，动态数组实现
import java.util.Collections; // 集合工具类，提供对集合的静态操作方法
import java.util.Date; // 日期类，表示特定的瞬间
import java.util.List; // 列表接口，表示有序集合
import java.util.Map; // 映射接口，表示键值对集合
import java.util.NoSuchElementException; // 无此元素异常，表示访问不存在的元素
import java.util.Set; // 集合接口，表示不包含重复元素的集合
import java.util.concurrent.ArrayBlockingQueue; // 数组阻塞队列，基于数组的有界阻塞队列
import java.util.concurrent.BlockingQueue; // 阻塞队列接口，支持阻塞的插入和移除操作
import java.util.concurrent.ExecutorService; // 执行器服务接口，用于异步执行任务
import java.util.concurrent.atomic.AtomicBoolean; // 原子布尔类，支持原子操作的布尔值

import static org.apache.calcite.runtime.HttpUtils.post; // HTTP工具类的post方法，用于发送HTTP POST请求
import static org.apache.calcite.util.DateTimeStringUtils.ISO_DATETIME_FRACTIONAL_SECOND_FORMAT; // ISO日期时间格式，带小数秒
import static org.apache.calcite.util.DateTimeStringUtils.getDateFormatter; // 获取日期格式化器的方法

import static java.util.Objects.requireNonNull; // Java Objects工具类的requireNonNull方法，用于检查对象非空

/**
 * Implementation of {@link DruidConnection}. // DruidConnection接口的实现类，提供与Druid数据源的具体连接和交互功能
 * 该类负责：
 * 1. 向Druid发送各种类型的查询请求（TIMESERIES、TOP_N、SELECT、GROUP_BY、SCAN）
 * 2. 解析Druid返回的JSON格式响应数据
 * 3. 读取Druid的数据源元数据（表名、列信息、指标等）
 * 4. 支持分页查询
 * 5. 提供异步查询功能，通过BlockingQueue实现生产者-消费者模式
 */
class DruidConnectionImpl implements DruidConnection { // DruidConnectionImpl类实现DruidConnection接口
  private final String url; // Druid Broker节点的URL，用于发送查询请求
  private final String coordinatorUrl; // Druid Coordinator节点的URL，用于获取元数据信息

  public static final String DEFAULT_RESPONSE_TIMESTAMP_COLUMN = "timestamp"; // 默认的时间戳列名，Druid响应中使用的时间戳字段名
  private static final SimpleDateFormat UTC_TIMESTAMP_FORMAT; // UTC时间戳格式化器，用于解析带时区的时间戳
  private static final SimpleDateFormat TIMESTAMP_FORMAT; // 普通时间戳格式化器，用于解析不带时区的时间戳

  static { // 静态初始化块，初始化时间戳格式化器
    UTC_TIMESTAMP_FORMAT = // 初始化UTC时间戳格式化器
        getDateFormatter(ISO_DATETIME_FRACTIONAL_SECOND_FORMAT); // 使用ISO格式（带小数秒的日期时间格式）
    TIMESTAMP_FORMAT = getDateFormatter(DateTimeUtils.TIMESTAMP_FORMAT_STRING); // 初始化普通时间戳格式化器
  }

  DruidConnectionImpl(String url, String coordinatorUrl) { // 构造方法，创建Druid连接实例
    this.url = requireNonNull(url, "url"); // 设置Broker URL，使用requireNonNull确保url不为null
    this.coordinatorUrl = requireNonNull(coordinatorUrl, "coordinatorUrl"); // 设置Coordinator URL，使用requireNonNull确保coordinatorUrl不为null
  }

  /** Executes a query request. // 执行查询请求，向Druid发送查询并解析响应
   *
   * @param queryType Query type // 查询类型，包括TIMESERIES、TOP_N、SELECT、GROUP_BY、SCAN等
   * @param data Data to post // 要发送的请求数据，通常为JSON格式的查询字符串
   * @param sink Sink to which to send the parsed rows // 数据接收器，用于接收解析后的行数据
   * @param fieldNames Names of fields // 字段名称列表，定义返回结果的列名
   * @param fieldTypes Types of fields (never null, but elements may be null) // 字段类型列表，定义每个字段的Java类型
   * @param page Page definition (in/out) // 分页定义对象，用于分页查询的输入输出参数
   */
  public void request(QueryType queryType, String data, Sink sink, // request方法，执行查询请求
      List<String> fieldNames, List<ColumnMetaData.Rep> fieldTypes,
      Page page) {
    final String url = this.url + "/druid/v2/?pretty"; // 构造完整的查询URL，添加Druid v2 API路径和pretty参数以美化输出
    final Map<String, String> requestHeaders = // 创建请求头映射
        ImmutableMap.of("Content-Type", "application/json"); // 设置Content-Type为application/json
    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了DEBUG模式
      System.out.println(data); // 打印请求数据到控制台
    }
    try (InputStream in0 = post(url, data, requestHeaders, 10000, 1800000); // 发送HTTP POST请求，连接超时10秒，读取超时30分钟
         InputStream in = traceResponse(in0)) { // 追踪响应流，在DEBUG模式下打印响应内容
      parse(queryType, in, sink, fieldNames, fieldTypes, page); // 解析响应数据，将结果发送到sink
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException("Error while processing druid request [" // 抛出运行时异常
          + data + "]", e); // 包含请求数据和原始异常信息
    }
  }

  /** Parses the output of a query, sending the results to a // 解析查询输出，将结果发送到数据接收器
   * {@link Sink}. */
  private static void parse(QueryType queryType, InputStream in, Sink sink, // parse方法，解析查询响应
      List<String> fieldNames, List<ColumnMetaData.Rep> fieldTypes, Page page) {
    final JsonFactory factory = new JsonFactory(); // 创建JSON工厂，用于创建JSON解析器
    final Row.RowBuilder rowBuilder = Row.newBuilder(fieldNames.size()); // 创建行构建器，用于构建数据行

    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了DEBUG模式
      try { // 尝试读取并打印响应数据
        final byte[] bytes = AvaticaUtils.readFullyToBytes(in); // 将输入流完全读取到字节数组
        System.out.println("Response: " // 打印响应标识
            + new String(bytes, StandardCharsets.UTF_8)); // 将字节数组转换为UTF-8字符串并打印
        in = new ByteArrayInputStream(bytes); // 从字节数组创建新的输入流，以便重新读取
      } catch (IOException e) { // 捕获IO异常
        throw new RuntimeException(e); // 抛出运行时异常
      }
    }

    int posTimestampField = -1; // 时间戳字段的位置，初始值为-1表示未找到
    for (int i = 0; i < fieldTypes.size(); i++) { // 遍历所有字段类型
      /*@TODO This need to be revisited. The logic seems implying that only // TODO：这个逻辑需要重新审查
      one column of type timestamp is present, this is not necessarily true, // 当前逻辑假设只有一个时间戳列，但这不一定正确
      see https://issues.apache.org/jira/browse/CALCITE-2175 // 参见JIRA问题CALCITE-2175
      */
      if (fieldTypes.get(i) == ColumnMetaData.Rep.JAVA_SQL_TIMESTAMP) { // 如果找到时间戳类型的字段
        posTimestampField = i; // 记录时间戳字段的位置
        break; // 跳出循环，只记录第一个时间戳字段
      }
    }

    try (JsonParser parser = factory.createParser(in)) { // 创建JSON解析器，使用try-with-resources自动关闭
      switch (queryType) { // 根据查询类型进行不同的解析处理
      case TIMESERIES: // 时间序列查询
        if (parser.nextToken() == JsonToken.START_ARRAY) { // 读取第一个标记，期望是数组开始
          while (parser.nextToken() == JsonToken.START_OBJECT) { // 循环读取每个对象
           // loop until token equal to "}" // 循环直到遇到对象结束标记
            final Long timeValue = extractTimestampField(parser); // 从JSON中提取时间戳字段值
            if (parser.nextToken() == JsonToken.FIELD_NAME // 读取下一个标记，期望是字段名
                    && parser.getCurrentName().equals("result") // 检查字段名是否为"result"
                    && parser.nextToken() == JsonToken.START_OBJECT) { // 读取下一个标记，期望是对象开始
              if (posTimestampField != -1) { // 如果存在时间戳字段
                rowBuilder.set(posTimestampField, timeValue); // 设置行构建器的时间戳字段值
              }
              parseFields(fieldNames, fieldTypes, rowBuilder, parser); // 解析所有字段
              sink.send(rowBuilder.build()); // 将构建的行发送到数据接收器
              rowBuilder.reset(); // 重置行构建器，准备构建下一行
            }
            expect(parser, JsonToken.END_OBJECT); // 期望遇到对象结束标记
          }
        }
        break; // 跳出switch语句

      case TOP_N: // Top N查询
        if (parser.nextToken() == JsonToken.START_ARRAY // 读取第一个标记，期望是数组开始
            && parser.nextToken() == JsonToken.START_OBJECT) { // 读取下一个标记，期望是对象开始
          final Long timeValue = extractTimestampField(parser); // 从JSON中提取时间戳字段值
          if (parser.nextToken() == JsonToken.FIELD_NAME // 读取下一个标记，期望是字段名
              && parser.getCurrentName().equals("result") // 检查字段名是否为"result"
              && parser.nextToken() == JsonToken.START_ARRAY) { // 读取下一个标记，期望是数组开始
            while (parser.nextToken() == JsonToken.START_OBJECT) { // 循环读取每个对象
              // loop until token equal to "}" // 循环直到遇到对象结束标记
              if (posTimestampField != -1) { // 如果存在时间戳字段
                rowBuilder.set(posTimestampField, timeValue); // 设置行构建器的时间戳字段值
              }
              parseFields(fieldNames, fieldTypes, rowBuilder, parser); // 解析所有字段
              sink.send(rowBuilder.build()); // 将构建的行发送到数据接收器
              rowBuilder.reset(); // 重置行构建器，准备构建下一行
            }
          }
        }
        break; // 跳出switch语句

      case SELECT: // 选择查询
        if (parser.nextToken() == JsonToken.START_ARRAY // 读取第一个标记，期望是数组开始
            && parser.nextToken() == JsonToken.START_OBJECT) { // 读取下一个标记，期望是对象开始
          page.pagingIdentifier = null; // 初始化分页标识符
          page.offset = -1; // 初始化偏移量
          page.totalRowCount = 0; // 初始化总行数
          expectScalarField(parser, DEFAULT_RESPONSE_TIMESTAMP_COLUMN); // 期望并跳过时间戳字段
          if (parser.nextToken() == JsonToken.FIELD_NAME // 读取下一个标记，期望是字段名
              && parser.getCurrentName().equals("result") // 检查字段名是否为"result"
              && parser.nextToken() == JsonToken.START_OBJECT) { // 读取下一个标记，期望是对象开始
            while (parser.nextToken() == JsonToken.FIELD_NAME) { // 循环读取所有字段名
              if (parser.getCurrentName().equals("pagingIdentifiers") // 如果字段名是"pagingIdentifiers"
                  && parser.nextToken() == JsonToken.START_OBJECT) { // 读取下一个标记，期望是对象开始
                JsonToken token = parser.nextToken(); // 读取下一个标记
                while (parser.getCurrentToken() == JsonToken.FIELD_NAME) { // 循环读取所有字段名
                  page.pagingIdentifier = parser.getCurrentName(); // 设置分页标识符
                  if (parser.nextToken() == JsonToken.VALUE_NUMBER_INT) { // 读取下一个标记，期望是整数
                    page.offset = parser.getIntValue(); // 设置偏移量
                  }
                  token = parser.nextToken(); // 读取下一个标记
                }
                expect(token, JsonToken.END_OBJECT); // 期望遇到对象结束标记
              } else if (parser.getCurrentName().equals("events") // 如果字段名是"events"
                  && parser.nextToken() == JsonToken.START_ARRAY) { // 读取下一个标记，期望是数组开始
                while (parser.nextToken() == JsonToken.START_OBJECT) { // 循环读取每个事件对象
                  expectScalarField(parser, "segmentId"); // 期望并跳过segmentId字段
                  expectScalarField(parser, "offset"); // 期望并跳过offset字段
                  if (parser.nextToken() == JsonToken.FIELD_NAME // 读取下一个标记，期望是字段名
                      && parser.getCurrentName().equals("event") // 检查字段名是否为"event"
                      && parser.nextToken() == JsonToken.START_OBJECT) { // 读取下一个标记，期望是对象开始
                    parseFields(fieldNames, fieldTypes, posTimestampField, rowBuilder, parser); // 解析所有字段
                    sink.send(rowBuilder.build()); // 将构建的行发送到数据接收器
                    rowBuilder.reset(); // 重置行构建器，准备构建下一行
                    page.totalRowCount += 1; // 增加总行数计数
                  }
                  expect(parser, JsonToken.END_OBJECT); // 期望遇到对象结束标记
                }
                parser.nextToken(); // 读取下一个标记
              } else if (parser.getCurrentName().equals("dimensions") // 如果字段名是"dimensions"
                  || parser.getCurrentName().equals("metrics")) { // 或字段名是"metrics"
                expect(parser, JsonToken.START_ARRAY); // 期望遇到数组开始标记
                while (parser.nextToken() != JsonToken.END_ARRAY) { // 循环直到遇到数组结束标记
                  // empty // 空循环，跳过数组内容
                }
              }
            }
          }
        }
        break; // 跳出switch语句

      case GROUP_BY: // 分组查询
        if (parser.nextToken() == JsonToken.START_ARRAY) { // 读取第一个标记，期望是数组开始
          while (parser.nextToken() == JsonToken.START_OBJECT) { // 循环读取每个对象
            expectScalarField(parser, "version"); // 期望并跳过version字段
            final Long timeValue = extractTimestampField(parser); // 从JSON中提取时间戳字段值
            if (parser.nextToken() == JsonToken.FIELD_NAME // 读取下一个标记，期望是字段名
                && parser.getCurrentName().equals("event") // 检查字段名是否为"event"
                && parser.nextToken() == JsonToken.START_OBJECT) { // 读取下一个标记，期望是对象开始
              if (posTimestampField != -1) { // 如果存在时间戳字段
                rowBuilder.set(posTimestampField, timeValue); // 设置行构建器的时间戳字段值
              }
              parseFields(fieldNames, fieldTypes, posTimestampField, rowBuilder, parser); // 解析所有字段
              sink.send(rowBuilder.build()); // 将构建的行发送到数据接收器
              rowBuilder.reset(); // 重置行构建器，准备构建下一行
            }
            expect(parser, JsonToken.END_OBJECT); // 期望遇到对象结束标记
          }
        }
        break; // 跳出switch语句

      case SCAN: // 扫描查询
        if (parser.nextToken() == JsonToken.START_ARRAY) { // 读取第一个标记，期望是数组开始
          while (parser.nextToken() == JsonToken.START_OBJECT) { // 循环读取每个对象
            expectScalarField(parser, "segmentId"); // 期望并跳过segmentId字段

            expect(parser, JsonToken.FIELD_NAME); // 期望遇到字段名标记
            if (parser.getCurrentName().equals("columns")) { // 如果字段名是"columns"
              expect(parser, JsonToken.START_ARRAY); // 期望遇到数组开始标记
              while (parser.nextToken() != JsonToken.END_ARRAY) { // 循环直到遇到数组结束标记
                // Skip the columns list // 跳过列列表内容
              }
            }
            if (parser.nextToken() == JsonToken.FIELD_NAME // 读取下一个标记，期望是字段名
                && parser.getCurrentName().equals("events") // 检查字段名是否为"events"
                && parser.nextToken() == JsonToken.START_ARRAY) { // 读取下一个标记，期望是数组开始
              // Events is Array of Arrays where each array is a row // events是数组的数组，每个数组代表一行
              while (parser.nextToken() == JsonToken.START_ARRAY) { // 循环读取每个行数组
                for (String field : fieldNames) { // 遍历所有字段名
                  parseFieldForName(fieldNames, fieldTypes, posTimestampField, rowBuilder, parser, // 解析指定名称的字段
                      field);
                }
                expect(parser, JsonToken.END_ARRAY); // 期望遇到数组结束标记
                Row row = rowBuilder.build(); // 构建行对象
                sink.send(row); // 将行发送到数据接收器
                rowBuilder.reset(); // 重置行构建器，准备构建下一行
                page.totalRowCount += 1; // 增加总行数计数
              }
            }
            expect(parser, JsonToken.END_OBJECT); // 期望遇到对象结束标记
          }
        }
        break; // 跳出switch语句
      default: // 默认情况
        break; // 直接跳出
      }
    } catch (IOException | InterruptedException e) { // 捕获IO异常或中断异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
  }

  private static void parseFields(List<String> fieldNames, List<ColumnMetaData.Rep> fieldTypes, // parseFields方法重载1：解析所有字段
      Row.RowBuilder rowBuilder, JsonParser parser) throws IOException {
    parseFields(fieldNames, fieldTypes, -1, rowBuilder, parser); // 调用parseFields方法重载2，时间戳字段位置设为-1
  }

  private static void parseFields(List<String> fieldNames, List<ColumnMetaData.Rep> fieldTypes, // parseFields方法重载2：解析所有字段，支持时间戳字段位置
      int posTimestampField, Row.RowBuilder rowBuilder, JsonParser parser) throws IOException {
    while (parser.nextToken() == JsonToken.FIELD_NAME) { // 循环读取所有字段名
      parseField(fieldNames, fieldTypes, posTimestampField, rowBuilder, parser); // 解析单个字段
    }
  }

  private static void parseField(List<String> fieldNames, List<ColumnMetaData.Rep> fieldTypes, // parseField方法：解析单个字段
      int posTimestampField, Row.RowBuilder rowBuilder, JsonParser parser) throws IOException {
    final String fieldName = parser.getCurrentName(); // 获取当前字段名
    parseFieldForName(fieldNames, fieldTypes, posTimestampField, rowBuilder, parser, fieldName); // 调用parseFieldForName方法解析指定名称的字段
  }

  @SuppressWarnings("JavaUtilDate") // 抑制关于使用Java Date的警告
  private static void parseFieldForName(List<String> fieldNames, // parseFieldForName方法：解析指定名称的字段
      List<ColumnMetaData.Rep> fieldTypes,
      int posTimestampField, Row.RowBuilder rowBuilder, JsonParser parser, String fieldName)
      throws IOException {
    // Move to next token, which is name's value // 移动到下一个标记，即字段名的值
    JsonToken token = parser.nextToken(); // 读取下一个标记，获取字段值的JSON标记类型

    boolean isTimestampColumn = fieldName.equals(DEFAULT_RESPONSE_TIMESTAMP_COLUMN); // 判断是否为时间戳列
    int i = fieldNames.indexOf(fieldName); // 查找字段在字段名列表中的索引
    ColumnMetaData.Rep type = null; // 初始化字段类型为null
    if (i < 0) { // 如果字段不在字段名列表中
      if (!isTimestampColumn) { // 且不是时间戳列
        // Field not present // 字段不存在，直接返回
        return;
      }
    } else { // 如果字段在字段名列表中
      type = fieldTypes.get(i); // 获取字段类型
    }

    if (isTimestampColumn || ColumnMetaData.Rep.JAVA_SQL_TIMESTAMP == type) { // 如果是时间戳列或字段类型为时间戳
      final int fieldPos = posTimestampField != -1 ? posTimestampField : i; // 确定字段位置，优先使用时间戳字段位置
      if (token == JsonToken.VALUE_NUMBER_INT) { // 如果标记是整数
        rowBuilder.set(posTimestampField, parser.getLongValue()); // 直接设置长整型时间戳值
        return; // 返回
      } else { // 如果标记不是整数
        // We don't have any way to figure out the format of time upfront since we only have // 我们无法提前确定时间格式，因为只有
        // org.apache.calcite.avatica.ColumnMetaData.Rep.JAVA_SQL_TIMESTAMP as type to represent // JAVA_SQL_TIMESTAMP类型来表示
        // both timestamp and timestamp with local timezone. // 时间戳和带本地时区的时间戳
        // Logic where type is inferred can be found at DruidQuery.DruidQueryNode.getPrimitive() // 类型推断逻辑可以在DruidQuery.DruidQueryNode.getPrimitive()中找到
        // Thus need to guess via try and catch // 因此需要通过try-catch来猜测格式
        synchronized (UTC_TIMESTAMP_FORMAT) { // 同步块，避免SimpleDateFormat的线程安全问题
          // synchronized block to avoid race condition // 同步块以避免竞态条件
          try { // 尝试解析
            // First try to parse as Timestamp with timezone. // 首先尝试解析为带时区的时间戳
            rowBuilder // 设置行构建器的字段值
                .set(fieldPos, UTC_TIMESTAMP_FORMAT.parse(parser.getText()).getTime()); // 使用UTC格式解析并获取毫秒数
          } catch (ParseException e) { // 如果解析失败
            // swallow the exception and try timestamp format // 吞掉异常，尝试时间戳格式
            try { // 尝试另一种格式
              rowBuilder // 设置行构建器的字段值
                  .set(fieldPos, TIMESTAMP_FORMAT.parse(parser.getText()).getTime()); // 使用普通时间戳格式解析并获取毫秒数
            } catch (ParseException e2) { // 如果再次解析失败
              // unknown format should not happen // 未知格式不应该发生
              throw new RuntimeException(e2); // 抛出运行时异常
            }
          }
        }
        return; // 返回
      }
    }

    switch (token) { // 根据JSON标记类型进行不同的处理
    case VALUE_NUMBER_INT: // 如果是整数
      if (type == null) { // 如果类型为null
        type = ColumnMetaData.Rep.LONG; // 默认设置为长整型
      }
      // fall through // 继续执行下一个case
    case VALUE_NUMBER_FLOAT: // 如果是浮点数
      if (type == null) { // 如果类型为null
        // JSON's "float" is 64-bit floating point // JSON的"float"是64位浮点数
        type = ColumnMetaData.Rep.DOUBLE; // 设置为双精度浮点型
      }
      switch (type) { // 根据类型进行不同的处理
      case BYTE: // 如果是字节类型
        rowBuilder.set(i, parser.getByteValue()); // 读取字节值并设置到行构建器
        break; // 跳出switch
      case SHORT: // 如果是短整型
        rowBuilder.set(i, parser.getShortValue()); // 读取短整型值并设置到行构建器
        break; // 跳出switch
      case INTEGER: // 如果是整型
        rowBuilder.set(i, parser.getIntValue()); // 读取整型值并设置到行构建器
        break; // 跳出switch
      case LONG: // 如果是长整型
        rowBuilder.set(i, parser.getLongValue()); // 读取长整型值并设置到行构建器
        break; // 跳出switch
      case DOUBLE: // 如果是双精度浮点型
        rowBuilder.set(i, parser.getDoubleValue()); // 读取双精度浮点值并设置到行构建器
        break; // 跳出switch
      default: // 默认情况
        break; // 直接跳出
      }
      break; // 跳出switch
    case VALUE_TRUE: // 如果是布尔值true
      rowBuilder.set(i, true); // 设置为true
      break; // 跳出switch
    case VALUE_FALSE: // 如果是布尔值false
      rowBuilder.set(i, false); // 设置为false
      break; // 跳出switch
    case VALUE_NULL: // 如果是null值
      break; // 直接跳出，不设置值
    case VALUE_STRING: // 如果是字符串值
    default: // 默认情况
      final String s = parser.getText(); // 获取字符串值
      if (type != null) { // 如果类型不为null
        switch (type) { // 根据类型进行不同的处理
        case LONG: // 如果是长整型
        case PRIMITIVE_LONG: // 如果是原始长整型
        case SHORT: // 如果是短整型
        case PRIMITIVE_SHORT: // 如果是原始短整型
        case INTEGER: // 如果是整型
        case PRIMITIVE_INT: // 如果是原始整型
          switch (s) { // 检查字符串内容
          case "Infinity": // 如果是"Infinity"
          case "-Infinity": // 如果是"-Infinity"
          case "NaN": // 如果是"NaN"
            throw new RuntimeException("/ by zero"); // 抛出除零异常
          default: // 默认情况
            break; // 直接跳出
          }
          rowBuilder.set(i, Long.valueOf(s)); // 将字符串转换为长整型并设置到行构建器
          break; // 跳出switch
        case FLOAT: // 如果是浮点型
        case PRIMITIVE_FLOAT: // 如果是原始浮点型
        case PRIMITIVE_DOUBLE: // 如果是原始双精度浮点型
        case NUMBER: // 如果是数字类型
        case DOUBLE: // 如果是双精度浮点型
          switch (s) { // 检查字符串内容
          case "Infinity": // 如果是"Infinity"
            rowBuilder.set(i, Double.POSITIVE_INFINITY); // 设置为正无穷大
            return; // 返回
          case "-Infinity": // 如果是"-Infinity"
            rowBuilder.set(i, Double.NEGATIVE_INFINITY); // 设置为负无穷大
            return; // 返回
          case "NaN": // 如果是"NaN"
            rowBuilder.set(i, Double.NaN); // 设置为NaN
            return; // 返回
          default: // 默认情况
            break; // 直接跳出
          }
          rowBuilder.set(i, Double.valueOf(s)); // 将字符串转换为双精度浮点型并设置到行构建器
          break; // 跳出switch
        default: // 默认情况
          break; // 直接跳出
        }
      } else { // 如果类型为null
        rowBuilder.set(i, s); // 直接将字符串值设置到行构建器
      }
    }
  }

  private static void expect(JsonParser parser, JsonToken token) throws IOException { // expect方法重载1：期望下一个标记为指定类型
    expect(parser.nextToken(), token); // 读取下一个标记并调用expect方法重载2
  }

  private static void expect(JsonToken token, JsonToken expected) { // expect方法重载2：验证标记是否为期望的类型
    if (token != expected) { // 如果标记不匹配
      throw new RuntimeException("expected " + expected + ", got " + token); // 抛出运行时异常
    }
  }

  private static void expectScalarField(JsonParser parser, String name) // expectScalarField方法：期望并跳过标量字段
      throws IOException {
    expect(parser, JsonToken.FIELD_NAME); // 期望遇到字段名标记
    if (!parser.getCurrentName().equals(name)) { // 如果字段名不匹配
      throw new RuntimeException("expected field " + name + ", got " // 抛出运行时异常
          + parser.getCurrentName());
    }
    final JsonToken t = parser.nextToken(); // 读取下一个标记
    switch (t) { // 根据标记类型进行验证
    case VALUE_NULL: // 如果是null值
    case VALUE_FALSE: // 如果是false值
    case VALUE_TRUE: // 如果是true值
    case VALUE_NUMBER_INT: // 如果是整数
    case VALUE_NUMBER_FLOAT: // 如果是浮点数
    case VALUE_STRING: // 如果是字符串
      break; // 跳出switch，这些都是有效的标量值
    default: // 默认情况
      throw new RuntimeException("expected scalar field, got  " + t); // 抛出运行时异常
    }
  }

  @SuppressWarnings("unused") // 抑制未使用警告
  private static void expectObjectField(JsonParser parser, String name) // expectObjectField方法：期望并跳过对象字段
      throws IOException {
    expect(parser, JsonToken.FIELD_NAME); // 期望遇到字段名标记
    if (!parser.getCurrentName().equals(name)) { // 如果字段名不匹配
      throw new RuntimeException("expected field " + name + ", got " // 抛出运行时异常
          + parser.getCurrentName());
    }
    expect(parser, JsonToken.START_OBJECT); // 期望遇到对象开始标记
    while (parser.nextToken() != JsonToken.END_OBJECT) { // 循环直到遇到对象结束标记
      // empty // 空循环，跳过对象内容
    }
  }

  @SuppressWarnings("JavaUtilDate") // 抑制关于使用Java Date的警告
  private static @Nullable Long extractTimestampField(JsonParser parser) // extractTimestampField方法：从JSON中提取时间戳字段
      throws IOException {
    expect(parser, JsonToken.FIELD_NAME); // 期望遇到字段名标记
    if (!parser.getCurrentName().equals(DEFAULT_RESPONSE_TIMESTAMP_COLUMN)) { // 如果字段名不是时间戳列名
      throw new RuntimeException("expected field " + DEFAULT_RESPONSE_TIMESTAMP_COLUMN + ", got " // 抛出运行时异常
          + parser.getCurrentName());
    }
    parser.nextToken(); // 读取下一个标记，即时间戳值
    try { // 尝试解析时间戳
      final Date parse; // 声明日期变量
      // synchronized block to avoid race condition // 同步块以避免竞态条件
      synchronized (UTC_TIMESTAMP_FORMAT) { // 同步SimpleDateFormat对象
        parse = UTC_TIMESTAMP_FORMAT.parse(parser.getText()); // 解析时间戳字符串为Date对象
      }
      return parse.getTime(); // 返回时间戳的毫秒数
    } catch (ParseException e) { // 如果解析失败
      // ignore bad value // 忽略错误的值
    }
    return null; // 返回null
  }

  /** Executes a request and returns the resulting rows as an // 执行请求并返回结果行的可枚举集合
   * {@link Enumerable}, running the parser in a thread provided by // 在提供的线程中运行解析器
   * {@code service}. */
  public Enumerable<Row> enumerable(final QueryType queryType, // enumerable方法：返回可枚举的结果行集合
      final String request, final List<String> fieldNames,
      final ExecutorService service)
      throws IOException {
    return new AbstractEnumerable<Row>() { // 返回匿名内部类，继承AbstractEnumerable
      @Override public Enumerator<Row> enumerator() { // 重写enumerator方法，返回枚举器
        final BlockingQueueEnumerator<Row> enumerator = // 创建阻塞队列枚举器
            new BlockingQueueEnumerator<>();
        final RunnableQueueSink sink = new RunnableQueueSink() { // 创建可运行队列接收器
          @Override public void send(Row row) throws InterruptedException { // 重写send方法，发送行到队列
            enumerator.queue.put(row); // 将行放入阻塞队列
          }

          @Override public void end() { // 重写end方法，标记结束
            enumerator.done.set(true); // 设置完成标志为true
          }

          @SuppressWarnings("deprecation") // 抑制过时警告
          @Override public void setSourceEnumerable(Enumerable<Row> enumerable) // 重写setSourceEnumerable方法
              throws InterruptedException {
            for (Row row : enumerable) { // 遍历可枚举集合
              send(row); // 发送每一行
            }
            end(); // 标记结束
          }

          @Override public void run() { // 重写run方法，在独立线程中执行查询
            try { // 尝试执行查询
              final Page page = new Page(); // 创建分页对象
              final List<ColumnMetaData.Rep> fieldTypes = // 创建字段类型列表，所有元素为null
                  Collections.nCopies(fieldNames.size(), null);
              request(queryType, request, this, fieldNames, fieldTypes, page); // 执行查询请求
              enumerator.done.set(true); // 设置完成标志为true
            } catch (Throwable e) { // 捕获所有异常
              enumerator.throwableHolder.set(e); // 保存异常到持有者
              enumerator.done.set(true); // 设置完成标志为true
            }
          }
        };
        service.execute(sink); // 在执行器服务中运行接收器
        return enumerator; // 返回枚举器
      }
    };
  }

  /** Reads segment metadata, and populates a list of columns and metrics. */ // 读取段元数据，填充列和指标列表
  void metadata(String dataSourceName, String timestampColumnName, // metadata方法：读取数据源的元数据
      List<Interval> intervals,
      Map<String, SqlTypeName> fieldBuilder, Set<String> metricNameBuilder,
      Map<String, List<ComplexMetric>> complexMetrics) {
    final String url = this.url + "/druid/v2/?pretty"; // 构造查询URL
    final Map<String, String> requestHeaders = // 创建请求头映射
        ImmutableMap.of("Content-Type", "application/json"); // 设置Content-Type为application/json
    final String data = DruidQuery.metadataQuery(dataSourceName, intervals); // 构造元数据查询请求
    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了DEBUG模式
      System.out.println("Druid: " + data); // 打印请求数据
    }
    try (InputStream in0 = post(url, data, requestHeaders, 10000, 1800000); // 发送HTTP POST请求
         InputStream in = traceResponse(in0)) { // 追踪响应流
      final ObjectMapper mapper = new ObjectMapper() // 创建对象映射器
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // 配置不因未知属性失败
      final CollectionType listType = // 创建列表类型
          mapper.getTypeFactory().constructCollectionType(List.class,
              JsonSegmentMetadata.class);
      final List<JsonSegmentMetadata> list = mapper.readValue(in, listType); // 反序列化JSON为段元数据列表
      in.close(); // 关闭输入流
      fieldBuilder.put(timestampColumnName, SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE); // 添加时间戳列到字段构建器
      for (JsonSegmentMetadata o : list) { // 遍历所有段元数据
        for (Map.Entry<String, JsonColumn> entry : o.columns.entrySet()) { // 遍历所有列
          if (entry.getKey().equals(DruidTable.DEFAULT_TIMESTAMP_COLUMN)) { // 如果是默认时间戳列
            // timestamp column // 跳过时间戳列
            continue;
          }
          final DruidType druidType; // 声明Druid类型变量
          try { // 尝试获取Druid类型
            druidType = DruidType.getTypeFromMetaData(entry.getValue().type); // 从元数据获取Druid类型
          } catch (AssertionError e) { // 如果获取失败
            // ignore exception; not a supported type // 忽略异常，不是支持的类型
            continue;
          }
          fieldBuilder.put(entry.getKey(), druidType.sqlType); // 添加字段到字段构建器
        }
        if (o.aggregators != null) { // 如果存在聚合器
          for (Map.Entry<String, JsonAggregator> entry // 遍历所有聚合器
              : o.aggregators.entrySet()) {
            if (!fieldBuilder.containsKey(entry.getKey())) { // 如果字段构建器中不包含该字段
              continue; // 跳过
            }
            DruidType type = DruidType.getTypeFromMetaData(entry.getValue().type); // 获取聚合器的Druid类型
            if (type.isComplex()) { // 如果是复杂类型
              // Each complex type will get their own alias, equal to their actual name. // 每个复杂类型都有自己的别名，等于其实际名称
              // Maybe we should have some smart string replacement strategies to make the column // 也许我们应该有一些智能字符串替换策略来使列名
              // names more natural. // 更自然
              List<ComplexMetric> metricList = new ArrayList<>(); // 创建复杂指标列表
              metricList.add(new ComplexMetric(entry.getKey(), type)); // 添加复杂指标
              complexMetrics.put(entry.getKey(), metricList); // 将复杂指标列表放入映射
            } else { // 如果不是复杂类型
              metricNameBuilder.add(entry.getKey()); // 添加指标名到指标名构建器
            }
          }
        }
      }
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
  }

  /** Reads data source names from Druid. */ // 从Druid读取数据源名称
  Set<String> tableNames() { // tableNames方法：获取所有数据源名称
    final Map<String, String> requestHeaders = // 创建请求头映射
        ImmutableMap.of("Content-Type", "application/json"); // 设置Content-Type为application/json
    final String data = null; // 请求数据为null
    final String url = coordinatorUrl + "/druid/coordinator/v1/metadata/datasources"; // 构造查询URL
    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了DEBUG模式
      System.out.println("Druid: table names" + data + "; " + url); // 打印调试信息
    }
    try (InputStream in0 = post(url, data, requestHeaders, 10000, 1800000); // 发送HTTP POST请求
         InputStream in = traceResponse(in0)) { // 追踪响应流
      final ObjectMapper mapper = new ObjectMapper(); // 创建对象映射器
      final CollectionType listType = // 创建列表类型
          mapper.getTypeFactory().constructCollectionType(List.class,
              String.class);
      final List<String> list = mapper.readValue(in, listType); // 反序列化JSON为字符串列表
      return ImmutableSet.copyOf(list); // 返回不可变的字符串集合
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 抛出运行时异常
    }
  }

  private static InputStream traceResponse(InputStream in) { // traceResponse方法：追踪响应流，在DEBUG模式下打印响应
    if (CalciteSystemProperty.DEBUG.value()) { // 如果开启了DEBUG模式
      try { // 尝试读取响应
        final byte[] bytes = AvaticaUtils.readFullyToBytes(in); // 将输入流完全读取到字节数组
        in.close(); // 关闭输入流
        System.out.println("Response: " // 打印响应标识
            + new String(bytes, StandardCharsets.UTF_8)); // 将字节数组转换为UTF-8字符串并打印
        in = new ByteArrayInputStream(bytes); // 从字节数组创建新的输入流
      } catch (IOException e) { // 捕获IO异常
        throw new RuntimeException(e); // 抛出运行时异常
      }
    }
    return in; // 返回输入流
  }

  /** A {@link Sink} that is also {@link Runnable}. */ // 既是数据接收器又是可运行对象的接口
  private interface RunnableQueueSink extends Sink, Runnable { // RunnableQueueSink接口，继承Sink和Runnable
  }

  /** An {@link Enumerator} that gets its rows from a {@link BlockingQueue}. // 从阻塞队列获取行的枚举器
   * There are other fields to signal errors and end-of-data. // 还有其他字段用于信号错误和数据结束
   *
   * @param <E> element type */ // 泛型参数E表示元素类型
  private static class BlockingQueueEnumerator<E> implements Enumerator<E> { // BlockingQueueEnumerator类，实现枚举器接口
    final BlockingQueue<E> queue = new ArrayBlockingQueue<>(1000); // 阻塞队列，容量为1000
    final AtomicBoolean done = new AtomicBoolean(false); // 原子布尔值，表示是否完成
    final Holder<Throwable> throwableHolder = Holder.empty(); // 异常持有者，用于保存异常

    E next; // 下一个元素

    @Override public E current() { // current方法：返回当前元素
      if (next == null) { // 如果下一个元素为null
        throw new NoSuchElementException(); // 抛出无此元素异常
      }
      return next; // 返回下一个元素
    }

    @Override public boolean moveNext() { // moveNext方法：移动到下一个元素
      for (;;) { // 无限循环
        next = queue.poll(); // 从队列中轮询下一个元素
        if (next != null) { // 如果元素不为null
          return true; // 返回true，表示有下一个元素
        }
        if (done.get()) { // 如果已完成
          close(); // 关闭枚举器
          return false; // 返回false，表示没有下一个元素
        }
      }
    }

    @Override public void reset() {} // reset方法：重置枚举器（空实现）

    @Override public void close() { // close方法：关闭枚举器
      final Throwable e = throwableHolder.get(); // 获取异常
      if (e != null) { // 如果异常不为null
        throwableHolder.set(null); // 清空异常持有者
        throw Util.throwAsRuntime(e); // 将异常作为运行时异常抛出
      }
    }
  }

  /** Progress through a large fetch. */ // 大量获取数据的进度
  static class Page { // Page类，表示分页信息
    @Nullable String pagingIdentifier; // 分页标识符，可能为null
    int offset = -1; // 偏移量，初始值为-1
    int totalRowCount = 0; // 总行数，初始值为0

    @Override public String toString() { // toString方法：返回字符串表示
      return "{" + pagingIdentifier + ": " + offset + "}"; // 返回分页标识符和偏移量的字符串
    }
  }

  /** Result of a "segmentMetadata" call, populated by Jackson. */ // segmentMetadata调用的结果，由Jackson填充
  @SuppressWarnings({ "WeakerAccess", "unused" }) // 抑制访问权限和未使用警告
  private static class JsonSegmentMetadata { // JsonSegmentMetadata类，表示段元数据
    public String id; // 段ID
    public List<String> intervals; // 时间间隔列表
    public Map<String, JsonColumn> columns; // 列映射，键为列名，值为列信息
    public long size; // 段大小
    public long numRows; // 行数
    public Map<String, JsonAggregator> aggregators; // 聚合器映射，键为聚合器名，值为聚合器信息
  }

  /** Element of the "columns" collection in the result of a // segmentMetadata结果中columns集合的元素
   * "segmentMetadata" call, populated by Jackson. */ // 由Jackson填充
  @SuppressWarnings({ "WeakerAccess", "unused" }) // 抑制访问权限和未使用警告
  private static class JsonColumn { // JsonColumn类，表示列信息
    public String type; // 列类型
    public boolean hasMultipleValues; // 是否有多个值
    public int size; // 列大小
    public Integer cardinality; // 基数
    public String errorMessage; // 错误信息
  }

  /** Element of the "aggregators" collection in the result of a // segmentMetadata结果中aggregators集合的元素
   * "segmentMetadata" call, populated by Jackson. */ // 由Jackson填充
  @SuppressWarnings({ "WeakerAccess", "unused" }) // 抑制访问权限和未使用警告
  private static class JsonAggregator { // JsonAggregator类，表示聚合器信息
    public String type; // 聚合器类型
    public String name; // 聚合器名称
    public String fieldName; // 字段名称

    DruidType druidType() { // druidType方法：获取Druid类型
      return DruidType.getTypeFromMetric(type); // 从指标类型获取Druid类型
    }
  }
}