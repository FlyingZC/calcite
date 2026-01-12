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
package org.apache.calcite.adapter.file; // 包声明:文件适配器包,包含CSV文件相关的适配器类

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂,用于创建Java类型
import org.apache.calcite.avatica.util.DateTimeUtils; // 导入日期时间工具类
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口,实现数据遍历
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型,表示SQL类型
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名称枚举
import org.apache.calcite.util.ImmutableIntList; // 导入不可变整数列表
import org.apache.calcite.util.ImmutableNullableList; // 导入不可变可空列表
import org.apache.calcite.util.Pair; // 导入键值对工具类
import org.apache.calcite.util.Source; // 导入数据源抽象类
import org.apache.calcite.util.trace.CalciteLogger; // 导入Calcite日志记录器

import org.apache.commons.lang3.time.FastDateFormat; // 导入快速日期格式化类

import au.com.bytecode.opencsv.CSVReader; // 导入CSV读取器,用于解析CSV文件

import com.google.common.annotations.VisibleForTesting; // 导入测试可见性注解

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空类型注解
import org.slf4j.LoggerFactory; // 导入日志工厂

import java.io.IOException; // 导入IO异常类
import java.math.BigDecimal; // 导入BigDecimal类,处理精确小数
import java.math.RoundingMode; // 导入舍入模式枚举
import java.text.ParseException; // 导入解析异常类
import java.util.ArrayList; // 导入动态数组列表
import java.util.Date; // 导入日期类
import java.util.List; // 导入列表接口
import java.util.Locale; // 导入本地化类
import java.util.TimeZone; // 导入时区类
import java.util.concurrent.atomic.AtomicBoolean; // 导入原子布尔类,用于线程安全的取消标志
import java.util.regex.Matcher; // 导入正则匹配器
import java.util.regex.Pattern; // 导入正则表达式模式

import static com.google.common.base.Preconditions.checkArgument; // 导入参数检查工具

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入非空转换工具

import static java.lang.Boolean.parseBoolean; // 导入布尔值解析方法
import static java.lang.Byte.parseByte; // 导入字节解析方法
import static java.lang.Double.parseDouble; // 导入双精度解析方法
import static java.lang.Float.parseFloat; // 导入单精度解析方法
import static java.lang.Integer.parseInt; // 导入整数解析方法
import static java.lang.Long.parseLong; // 导入长整型解析方法
import static java.lang.Short.parseShort; // 导入短整型解析方法
import static java.util.Objects.requireNonNull; // 导入对象非空检查工具

/** Enumerator that reads from a CSV file. // 类注释:CSV文件枚举器,用于从CSV文件中逐行读取数据并转换为Java对象
 * // 该类实现了Enumerator接口,提供了迭代器模式的数据访问方式
 * // 支持类型转换、数据过滤和流式读取等功能
 * @param <E> Row type // 泛型参数E表示行类型,即每行数据转换后的Java对象类型 */
public class CsvEnumerator<E> implements Enumerator<E> { // 类定义:泛型枚举器类,实现Enumerator接口
  private static final CalciteLogger LOGGER = // 日志记录器:用于记录CSV读取过程中的警告和错误信息
      new CalciteLogger(LoggerFactory.getLogger(CsvEnumerator.class)); // 创建CalciteLogger实例,使用当前类名

  private final CSVReader reader; // CSV读取器:底层使用opencsv库读取CSV文件
  private final @Nullable List<@Nullable String> filterValues; // 过滤值列表:用于过滤CSV行,如果某列的值不匹配则跳过该行
  private final AtomicBoolean cancelFlag; // 取消标志:原子布尔值,用于在外部取消枚举操作(线程安全)
  private final RowConverter<E> rowConverter; // 行转换器:负责将CSV字符串数组转换为Java对象
  private @Nullable E current; // 当前行:保存当前读取并转换后的数据对象

  private static final FastDateFormat TIME_FORMAT_DATE; // 日期格式化器:用于解析日期类型(yyyy-MM-dd)
  private static final FastDateFormat TIME_FORMAT_TIME; // 时间格式化器:用于解析时间类型(HH:mm:ss)
  private static final FastDateFormat TIME_FORMAT_TIMESTAMP; // 时间戳格式化器:用于解析时间戳类型(yyyy-MM-dd HH:mm:ss)
  private static final Pattern DECIMAL_TYPE_PATTERN = Pattern // DECIMAL类型正则表达式:用于匹配"decimal(precision,scale)"格式的类型定义
      .compile("\"decimal\\(([0-9]+),([0-9]+)\\)"); // 编译正则表达式,捕获精度和标度两个数字

  static { // 静态初始化块:初始化日期时间格式化器
    final TimeZone gmt = TimeZone.getTimeZone("GMT"); // 创建GMT时区对象
    TIME_FORMAT_DATE = FastDateFormat.getInstance("yyyy-MM-dd", gmt); // 初始化日期格式化器,使用GMT时区
    TIME_FORMAT_TIME = FastDateFormat.getInstance("HH:mm:ss", gmt); // 初始化时间格式化器,使用GMT时区
    TIME_FORMAT_TIMESTAMP = // 初始化时间戳格式化器
        FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss", gmt); // 使用GMT时区,格式为年-月-日 时:分:秒
  } // 静态初始化块结束

  public CsvEnumerator(Source source, AtomicBoolean cancelFlag, // 构造方法1:创建CSV枚举器(简化版本)
      List<RelDataType> fieldTypes, List<Integer> fields) { // 参数:数据源、取消标志、字段类型列表、字段索引列表
    //noinspection unchecked // 忽略未检查的类型转换警告
    this(source, cancelFlag, false, null, // 调用主构造方法,stream=false表示非流式读取,filterValues=null表示无过滤
        (RowConverter<E>) converter(fieldTypes, fields)); // 根据字段类型和索引创建行转换器
  } // 构造方法1结束

  public CsvEnumerator(Source source, AtomicBoolean cancelFlag, boolean stream, // 构造方法2:创建CSV枚举器(完整版本)
      @Nullable String @Nullable [] filterValues, RowConverter<E> rowConverter) { // 参数:数据源、取消标志、是否流式、过滤值数组、行转换器
    this.cancelFlag = cancelFlag; // 保存取消标志,用于外部中断枚举操作
    this.rowConverter = rowConverter; // 保存行转换器,用于将CSV行转换为Java对象
    this.filterValues = // 保存过滤值列表
        filterValues == null ? null // 如果过滤值为空则设置为null
            : ImmutableNullableList.copyOf(filterValues); // 否则转换为不可变可空列表
    try { // 尝试块:初始化CSV读取器
      if (stream) { // 如果是流式读取模式
        this.reader = new CsvStreamReader(source); // 创建流式CSV读取器(支持监控文件变化)
      } else { // 如果是普通读取模式
        this.reader = openCsv(source); // 创建普通CSV读取器
      }
      this.reader.readNext(); // 跳过CSV文件的第一行(通常是列名行)
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 将IO异常包装为运行时异常抛出
    } // 捕获块结束
  } // 构造方法2结束

  private static RowConverter<?> converter(List<RelDataType> fieldTypes, // 私有静态方法:根据字段信息创建适当的行转换器
      List<Integer> fields) { // 参数:字段类型列表、字段索引列表
    if (fields.size() == 1) { // 如果只读取一个字段
      final int field = fields.get(0); // 获取字段索引
      return new SingleColumnRowConverter(fieldTypes.get(field), field); // 返回单列行转换器
    } else { // 如果读取多个字段
      return arrayConverter(fieldTypes, fields, false); // 返回数组行转换器(非流式)
    } // 条件判断结束
  } // 方法结束

  public static RowConverter<@Nullable Object[]> arrayConverter( // 公共静态方法:创建数组行转换器
      List<RelDataType> fieldTypes, List<Integer> fields, boolean stream) { // 参数:字段类型列表、字段索引列表、是否流式
    return new ArrayRowConverter(fieldTypes, fields, stream); // 创建并返回数组行转换器实例
  } // 方法结束

  /** Deduces the names and types of a table's columns by reading the first line // 方法注释:通过读取CSV文件的第一行推断表的列名和类型
   * of a CSV file. // CSV文件的第一行格式为:列名:类型,例如"name:string,age:int,salary:decimal(10,2)" */
  public static RelDataType deduceRowType(JavaTypeFactory typeFactory, // 公共静态方法:推断CSV文件的行类型结构
      Source source, @Nullable List<RelDataType> fieldTypes, Boolean stream) { // 参数:类型工厂、数据源、输出字段类型列表、是否流式
    final List<RelDataType> types = new ArrayList<>(); // 创建字段类型列表
    final List<String> names = new ArrayList<>(); // 创建字段名称列表
    if (stream) { // 如果是流式读取模式
      names.add(FileSchemaFactory.ROWTIME_COLUMN_NAME); // 添加ROWTIME列名(时间戳列)
      types.add(typeFactory.createSqlType(SqlTypeName.TIMESTAMP)); // 添加TIMESTAMP类型
    } // 条件判断结束
    try (CSVReader reader = openCsv(source)) { // 尝试块:使用try-with-resources自动关闭CSV读取器
      String[] strings = reader.readNext(); // 读取第一行(列定义行)
      if (strings == null) { // 如果文件为空
        strings = new String[]{"EmptyFileHasNoColumns:boolean"}; // 使用默认列定义
      } // 条件判断结束
      for (String string : strings) { // 遍历每个列定义字符串
        final String name; // 列名变量
        final RelDataType fieldType; // 列类型变量
        final int colon = string.indexOf(':'); // 查找冒号位置(列名和类型的分隔符)
        if (colon >= 0) { // 如果找到冒号
          name = string.substring(0, colon); // 提取列名(冒号前的部分)
          String typeString = string.substring(colon + 1); // 提取类型字符串(冒号后的部分)
          Matcher decimalMatcher = DECIMAL_TYPE_PATTERN.matcher(typeString); // 创建DECIMAL类型匹配器
          if (decimalMatcher.matches()) { // 如果匹配DECIMAL类型(例如"decimal(10,2)")
            int precision = parseInt(decimalMatcher.group(1)); // 提取精度(第一个分组)
            int scale = parseInt(decimalMatcher.group(2)); // 提取标度(第二个分组)
            fieldType = parseDecimalSqlType(typeFactory, precision, scale); // 创建DECIMAL类型
          } else { // 如果不是DECIMAL类型
            switch (typeString) { // 根据类型字符串进行分支
            case "string": // 字符串类型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.VARCHAR); // 创建可空VARCHAR类型
              break; // 跳出分支
            case "boolean": // 布尔类型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.BOOLEAN); // 创建可空BOOLEAN类型
              break; // 跳出分支
            case "byte": // 字节类型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.TINYINT); // 创建可空TINYINT类型
              break; // 跳出分支
            case "char": // 字符类型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.CHAR); // 创建可空CHAR类型
              break; // 跳出分支
            case "short": // 短整型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.SMALLINT); // 创建可空SMALLINT类型
              break; // 跳出分支
            case "int": // 整型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.INTEGER); // 创建可空INTEGER类型
              break; // 跳出分支
            case "long": // 长整型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.BIGINT); // 创建可空BIGINT类型
              break; // 跳出分支
            case "float": // 单精度浮点
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.REAL); // 创建可空REAL类型
              break; // 跳出分支
            case "double": // 双精度浮点
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.DOUBLE); // 创建可空DOUBLE类型
              break; // 跳出分支
            case "date": // 日期类型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.DATE); // 创建可空DATE类型
              break; // 跳出分支
            case "timestamp": // 时间戳类型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.TIMESTAMP); // 创建可空TIMESTAMP类型
              break; // 跳出分支
            case "time": // 时间类型
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.TIME); // 创建可空TIME类型
              break; // 跳出分支
            default: // 未知类型
              LOGGER.warn( // 记录警告日志
                  "Found unknown type: {} in file: {} for column: {}. Will assume the type of " // 警告信息
                      + "column is string.", // 默认使用字符串类型
                  typeString, source.path(), name); // 填充日志参数
              fieldType = toNullableRelDataType(typeFactory, SqlTypeName.VARCHAR); // 创建可空VARCHAR类型作为默认
              break; // 跳出分支
            } // switch语句结束
          } // else分支结束
        } else { // 如果没有找到冒号
          name = string; // 整个字符串作为列名
          fieldType = typeFactory.createSqlType(SqlTypeName.VARCHAR); // 默认使用VARCHAR类型
        } // else分支结束
        names.add(name); // 将列名添加到名称列表
        types.add(fieldType); // 将类型添加到类型列表
        if (fieldTypes != null) { // 如果提供了输出字段类型列表
          fieldTypes.add(fieldType); // 将类型添加到输出列表
        } // 条件判断结束
      } // for循环结束
    } catch (IOException e) { // 捕获IO异常
      // ignore // 忽略异常
    } // 捕获块结束
    if (names.isEmpty()) { // 如果没有解析到任何列
      names.add("line"); // 添加默认列名"line"
      types.add(typeFactory.createSqlType(SqlTypeName.VARCHAR)); // 添加默认VARCHAR类型
    } // 条件判断结束
    return typeFactory.createStructType(Pair.zip(names, types)); // 创建结构化类型(包含列名和类型的对)
  } // 方法结束

  static CSVReader openCsv(Source source) throws IOException { // 静态方法:打开CSV文件并创建CSV读取器
    requireNonNull(source, "source"); // 检查数据源不为null
    return new CSVReader(source.reader()); // 创建CSVReader实例,使用数据源的Reader
  } // 方法结束

  @Override public E current() { // 实现接口方法:返回当前元素
    return castNonNull(current); // 返回当前元素(强制转换为非空)
  } // 方法结束

  @Override public boolean moveNext() { // 实现接口方法:移动到下一个元素
    try { // 尝试块
    outer: // 外层循环标签,用于内层continue跳到外层循环
      for (;;) { // 无限循环:持续读取CSV行
        if (cancelFlag.get()) { // 检查取消标志
          return false; // 如果被取消则返回false
        } // 条件判断结束
        final String[] strings = reader.readNext(); // 读取下一行CSV数据
        if (strings == null) { // 如果读取到文件末尾
          if (reader instanceof CsvStreamReader) { // 如果是流式读取器
            try { // 尝试块
              Thread.sleep(CsvStreamReader.DEFAULT_MONITOR_DELAY); // 休眠一段时间,等待新数据
            } catch (InterruptedException e) { // 捕获中断异常
              throw new RuntimeException(e); // 包装为运行时异常抛出
            } // 捕获块结束
            continue; // 继续循环,尝试读取新数据
          } // 条件判断结束
          current = null; // 设置当前元素为null
          reader.close(); // 关闭CSV读取器
          return false; // 返回false表示没有更多数据
        } // 条件判断结束
        if (filterValues != null) { // 如果设置了过滤值
          for (int i = 0; i < strings.length; i++) { // 遍历每个字段
            String filterValue = filterValues.get(i); // 获取第i列的过滤值
            if (filterValue != null) { // 如果该列设置了过滤值
              if (!filterValue.equals(strings[i])) { // 如果实际值不等于过滤值
                continue outer; // 跳过该行,继续读取下一行
              } // 条件判断结束
            } // 条件判断结束
          } // for循环结束
        } // 条件判断结束
        current = rowConverter.convertRow(strings); // 使用行转换器将CSV字符串数组转换为Java对象
        return true; // 返回true表示成功读取到下一行
      } // 无限循环结束
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException(e); // 包装为运行时异常抛出
    } // 捕获块结束
  } // 方法结束

  @Override public void reset() { // 实现接口方法:重置枚举器到初始状态
    throw new UnsupportedOperationException(); // 抛出不支持操作异常(CSV枚举器不支持重置)
  } // 方法结束

  @Override public void close() { // 实现接口方法:关闭枚举器并释放资源
    try { // 尝试块
      reader.close(); // 关闭CSV读取器
    } catch (IOException e) { // 捕获IO异常
      throw new RuntimeException("Error closing CSV reader", e); // 包装为运行时异常抛出
    } // 捕获块结束
  } // 方法结束

  /** Returns an array of integers {0, ..., n - 1}. */ // 方法注释:返回从0到n-1的整数数组
  public static int[] identityList(int n) { // 公共静态方法:生成恒等列表[0,1,2,...,n-1]
    int[] integers = new int[n]; // 创建长度为n的整数数组
    for (int i = 0; i < n; i++) { // 遍历数组
      integers[i] = i; // 将索引值赋给数组元素
    } // for循环结束
    return integers; // 返回整数数组
  } // 方法结束

  private static RelDataType toNullableRelDataType(JavaTypeFactory typeFactory, // 私有静态方法:创建可空的关系数据类型
      SqlTypeName sqlTypeName) { // 参数:类型工厂、SQL类型名称
    return typeFactory.createTypeWithNullability(typeFactory.createSqlType(sqlTypeName), true); // 创建指定SQL类型并设置为可空
  } // 方法结束

  /** Row converter. // 类注释:行转换器抽象类,负责将CSV字符串数组转换为Java对象
   * // 提供了类型转换的核心逻辑,支持所有Calcite SQL类型到Java类型的转换
   * @param <E> element type // 泛型参数E表示元素类型,即转换后的结果类型 */
  abstract static class RowConverter<E> { // 抽象静态内部类:行转换器基类
    abstract E convertRow(@Nullable String[] rows); // 抽象方法:将CSV字符串数组转换为Java对象,由子类实现

    @SuppressWarnings("JavaUtilDate") // 抑制使用Java日期类的警告
    protected @Nullable Object convert(@Nullable RelDataType fieldType, @Nullable String string) { // 受保护方法:将字符串转换为指定类型的Java对象
      if (fieldType == null || string == null) { // 如果字段类型为null或字符串为null
        return string; // 直接返回字符串(可能是null)
      } // 条件判断结束
      switch (fieldType.getSqlTypeName()) { // 根据SQL类型名称进行分支
      case BOOLEAN: // 布尔类型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseBoolean(string); // 解析为布尔值
      case TINYINT: // 字节类型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseByte(string); // 解析为字节
      case SMALLINT: // 短整型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseShort(string); // 解析为短整型
      case INTEGER: // 整型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseInt(string); // 解析为整型
      case BIGINT: // 长整型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseLong(string); // 解析为长整型
      case REAL: // 单精度浮点(REAL)
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseFloat(string); // 解析为单精度浮点
      case FLOAT: // 浮点类型(与DOUBLE合并)
      case DOUBLE: // 双精度浮点
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseDouble(string); // 解析为双精度浮点
      case DECIMAL: // DECIMAL类型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        return parseDecimal(fieldType.getPrecision(), fieldType.getScale(), string); // 解析为BigDecimal,考虑精度和标度
      case DATE: // 日期类型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        try { // 尝试块
          Date date = TIME_FORMAT_DATE.parse(string); // 解析日期字符串
          return (int) (date.getTime() / DateTimeUtils.MILLIS_PER_DAY); // 转换为天数(从1970-01-01开始)
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        } // 捕获块结束
      case TIME: // 时间类型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        try { // 尝试块
          Date date = TIME_FORMAT_TIME.parse(string); // 解析时间字符串
          return (int) date.getTime(); // 返回毫秒数
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        } // 捕获块结束
      case TIMESTAMP: // 时间戳类型
        if (string.length() == 0) { // 如果字符串为空
          return null; // 返回null
        } // 条件判断结束
        try { // 尝试块
          Date date = TIME_FORMAT_TIMESTAMP.parse(string); // 解析时间戳字符串
          return date.getTime(); // 返回毫秒数
        } catch (ParseException e) { // 捕获解析异常
          return null; // 解析失败返回null
        } // 捕获块结束
      case VARCHAR: // 字符串类型
      default: // 默认情况
        return string; // 直接返回字符串
      } // switch语句结束
    } // 方法结束
  } // 抽象类结束

  private static RelDataType parseDecimalSqlType(JavaTypeFactory typeFactory, int precision, // 私有静态方法:解析DECIMAL SQL类型
      int scale) { // 参数:类型工厂、精度、标度
    checkArgument(precision > 0, "DECIMAL type must have precision > 0. Found %s", precision); // 检查精度必须大于0
    checkArgument(scale >= 0, "DECIMAL type must have scale >= 0. Found %s", scale); // 检查标度必须大于等于0
    checkArgument(precision >= scale, // 检查精度必须大于等于标度
        "DECIMAL type must have precision >= scale. Found precision (%s) and scale (%s).", // 错误信息
        precision, scale); // 填充参数
    return typeFactory.createTypeWithNullability( // 创建可空类型
        typeFactory.createSqlType(SqlTypeName.DECIMAL, precision, scale), true); // 指定DECIMAL类型、精度和标度
  } // 方法结束

  @VisibleForTesting // 注解:该方法对测试可见
  protected static BigDecimal parseDecimal(int precision, int scale, String string) { // 受保护静态方法:解析DECIMAL字符串
    BigDecimal result = new BigDecimal(string); // 创建BigDecimal对象
    // If the parsed value has more fractional digits than the specified scale, round ties away // 注释:如果解析值的小数位数超过指定标度,则进行四舍五入
    // from 0. // 注释:四舍五入远离零
    if (result.scale() > scale) { // 如果实际标度大于指定标度
      LOGGER.warn( // 记录警告日志
          "Decimal value {} exceeds declared scale ({}). Performing rounding to keep the " // 警告信息
              + "first {} fractional digits.", // 警告信息续
          result, scale, scale); // 填充日志参数
      result = result.setScale(scale, RoundingMode.HALF_UP); // 四舍五入到指定标度
    } // 条件判断结束
    // Throws an exception if the parsed value has more digits to the left of the decimal point // 注释:如果解析值的小数点左侧位数超过指定值则抛出异常
    // than the specified value. // 注释:超过精度减去标度的值
    if (result.precision() - result.scale() > precision - scale) { // 如果整数部分位数超过允许范围
      throw new IllegalArgumentException(String // 抛出非法参数异常
          .format(Locale.ROOT, "Decimal value %s exceeds declared precision (%d) and scale (%d).", // 错误信息
              result, precision, scale)); // 填充参数
    } // 条件判断结束
    return result; // 返回解析后的BigDecimal
  } // 方法结束

  /** Array row converter. */ // 类注释:数组行转换器,将CSV字符串数组转换为Java对象数组
  static class ArrayRowConverter extends RowConverter<@Nullable Object[]> { // 静态内部类,继承RowConverter,转换结果为对象数组

    /** Field types. List must not be null, but any element may be null. */ // 成员变量注释:字段类型列表
    private final List<RelDataType> fieldTypes; // 字段类型列表:存储每个字段的SQL类型
    private final ImmutableIntList fields; // 字段索引列表:存储需要读取的字段索引
    /** Whether the row to convert is from a stream. */ // 成员变量注释:是否来自流式数据
    private final boolean stream; // 流式标志:如果是true,则在结果数组开头添加时间戳

    ArrayRowConverter(List<RelDataType> fieldTypes, List<Integer> fields, // 构造方法:创建数组行转换器
        boolean stream) { // 参数:字段类型列表、字段索引列表、是否流式
      this.fieldTypes = ImmutableNullableList.copyOf(fieldTypes); // 将字段类型列表转换为不可变可空列表
      this.fields = ImmutableIntList.copyOf(fields); // 将字段索引列表转换为不可变整数列表
      this.stream = stream; // 保存流式标志
    } // 构造方法结束

    @Override public @Nullable Object[] convertRow(@Nullable String[] strings) { // 实现抽象方法:转换CSV行为对象数组
      if (stream) { // 如果是流式模式
        return convertStreamRow(strings); // 调用流式行转换方法
      } else { // 如果是普通模式
        return convertNormalRow(strings); // 调用普通行转换方法
      } // 条件判断结束
    } // 方法结束

    public @Nullable Object[] convertNormalRow(@Nullable String[] strings) { // 公共方法:转换普通CSV行为对象数组
      final @Nullable Object[] objects = new Object[fields.size()]; // 创建对象数组,大小为字段数量
      for (int i = 0; i < fields.size(); i++) { // 遍历每个字段
        int field = fields.get(i); // 获取字段索引
        objects[i] = convert(fieldTypes.get(field), strings[field]); // 转换字段值并存储
      } // for循环结束
      return objects; // 返回对象数组
    } // 方法结束

    public @Nullable Object[] convertStreamRow(@Nullable String[] strings) { // 公共方法:转换流式CSV行为对象数组
      final @Nullable Object[] objects = new Object[fields.size() + 1]; // 创建对象数组,大小为字段数量+1(额外一列用于时间戳)
      objects[0] = System.currentTimeMillis(); // 第一列存储当前时间戳(毫秒)
      for (int i = 0; i < fields.size(); i++) { // 遍历每个字段
        int field = fields.get(i); // 获取字段索引
        objects[i + 1] = convert(fieldTypes.get(field), strings[field]); // 从第二列开始存储转换后的字段值
      } // for循环结束
      return objects; // 返回对象数组
    } // 方法结束
  } // 类结束

  /** Single column row converter. */ // 类注释:单列行转换器,将CSV字符串数组转换为单个对象(只取一列)
  private static class SingleColumnRowConverter extends RowConverter<Object> { // 定义私有静态内部类,继承RowConverter,转换结果为单个对象
    private final RelDataType fieldType; // 字段类型,存储该列的SQL类型
    private final int fieldIndex; // 字段索引,存储该列在CSV中的位置

    private SingleColumnRowConverter(RelDataType fieldType, int fieldIndex) { // 构造方法:创建单列行转换器
      this.fieldType = fieldType; // 保存字段类型
      this.fieldIndex = fieldIndex; // 保存字段索引
    }

    @Override public @Nullable Object convertRow(@Nullable String[] strings) { // 实现抽象方法:转换CSV行为单个对象
      return convert(fieldType, strings[fieldIndex]); // 只转换指定索引的字段并返回
    }
  }
} // 类结束
