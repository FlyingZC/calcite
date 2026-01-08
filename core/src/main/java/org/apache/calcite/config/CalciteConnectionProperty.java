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
// Apache许可证声明，说明代码的版权和使用许可
package org.apache.calcite.config;  // 定义包名，这个类属于org.apache.calcite.config包，用于配置管理

import org.apache.calcite.avatica.ConnectionProperty;  // 导入ConnectionProperty接口，Calcite连接属性需要实现此接口
import org.apache.calcite.avatica.util.Casing;  // 导入Casing枚举，用于定义标识符的大小写规则
import org.apache.calcite.avatica.util.Quoting;  // 导入Quoting枚举，用于定义标识符的引用方式
import org.apache.calcite.model.JsonSchema;  // 导入JsonSchema类，用于JSON模式的定义
import org.apache.calcite.sql.validate.SqlConformanceEnum;  // 导入SqlConformanceEnum枚举，定义SQL的兼容性级别

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记可能为null的值

import java.util.HashMap;  // 导入HashMap类，用于存储名称到属性的映射
import java.util.Locale;  // 导入Locale类，用于本地化设置
import java.util.Map;  // 导入Map接口，用于映射关系
import java.util.Properties;  // 导入Properties类，用于处理属性配置
import java.util.TimeZone;  // 导入TimeZone类，用于时区设置

import static org.apache.calcite.avatica.ConnectionConfigImpl.PropEnv;  // 导入PropEnv类，用于属性环境封装
import static org.apache.calcite.avatica.ConnectionConfigImpl.parse;  // 导入parse方法，用于解析属性

/**
 * Properties that may be specified on the JDBC connect string.
 */
// 定义一个枚举类CalciteConnectionProperty，实现ConnectionProperty接口
// 这个类定义了所有可以在JDBC连接字符串中指定的Calcite连接属性
// 每个枚举常量代表一个可配置的连接属性
public enum CalciteConnectionProperty implements ConnectionProperty {
  /** Whether approximate results from {@code COUNT(DISTINCT ...)} aggregate
   * functions are acceptable. */
  // 枚举常量：是否接受COUNT(DISTINCT ...)聚合函数的近似结果
  // 参数说明："approximateDistinctCount"是属性的驼峰命名，Type.BOOLEAN表示属性类型是布尔值
  // false表示默认值为false，false表示该属性不是必需的
  APPROXIMATE_DISTINCT_COUNT("approximateDistinctCount", Type.BOOLEAN, false,  // 定义是否允许COUNT(DISTINCT)使用近似计算的属性
      false),  // 该属性不是必需的

  /** Whether approximate results from "Top N" queries
   * ({@code ORDER BY aggFun DESC LIMIT n}) are acceptable. */
  // 枚举常量：是否接受Top N查询（ORDER BY aggFun DESC LIMIT n）的近似结果
  APPROXIMATE_TOP_N("approximateTopN", Type.BOOLEAN, false,  // 定义是否允许Top N查询使用近似计算的属性
      false),  // 该属性不是必需的

  /** Whether approximate results from aggregate functions on
   * DECIMAL types are acceptable. */
  // 枚举常量：是否接受DECIMAL类型上聚合函数的近似结果
  APPROXIMATE_DECIMAL("approximateDecimal", Type.BOOLEAN, false,  // 定义是否允许DECIMAL类型聚合使用近似计算的属性
      false),  // 该属性不是必需的

  /** Whether to treat empty strings as null for Druid Adapter.
   */
  // 枚举常量：对于Druid适配器，是否将空字符串视为null
  NULL_EQUAL_TO_EMPTY("nullEqualToEmpty", Type.BOOLEAN, true,  // 定义是否将空字符串等同于null的属性，默认值为true
      false),  // 该属性不是必需的

  /** Whether to store query results in temporary tables. */
  // 枚举常量：是否将查询结果存储在临时表中
  AUTO_TEMP("autoTemp", Type.BOOLEAN, false,  // 定义是否自动使用临时表存储查询结果的属性
      false),  // 该属性不是必需的

  /** Whether Calcite should use materializations. */
  // 枚举常量：Calcite是否应该使用物化视图
  MATERIALIZATIONS_ENABLED("materializationsEnabled", Type.BOOLEAN, true,  // 定义是否启用物化视图的属性，默认值为true
      false),  // 该属性不是必需的

  /** Whether Calcite should create materializations. */
  // 枚举常量：Calcite是否应该创建物化视图
  CREATE_MATERIALIZATIONS("createMaterializations", Type.BOOLEAN, true,  // 定义是否创建物化视图的属性，默认值为true
      false),  // 该属性不是必需的

  /** How NULL values should be sorted if neither NULLS FIRST nor NULLS LAST are
   * specified. The default, HIGH, sorts NULL values the same as Oracle. */
  // 枚举常量：当没有指定NULLS FIRST或NULLS LAST时，NULL值应该如何排序
  // 默认值HIGH表示NULL值排序方式与Oracle相同（NULL值排在最后）
  DEFAULT_NULL_COLLATION("defaultNullCollation", Type.ENUM, NullCollation.HIGH,  // 定义NULL值的默认排序规则属性，默认值为HIGH
      true,  // 该属性不是必需的
      NullCollation.class),  // 枚举值的类型是NullCollation类

  /** How many rows the Druid adapter should fetch at a time when executing
   * "select" queries. */
  // 枚举常量：Druid适配器执行select查询时每次应该获取的行数
  DRUID_FETCH("druidFetch", Type.NUMBER, 16384,  // 定义Druid适配器的每次获取行数属性，默认值为16384
      false),  // 该属性不是必需的

  /** URI of the model. */
  // 枚举常量：模型的URI（统一资源标识符）
  MODEL("model", Type.STRING, null,  // 定义模型文件的URI属性，默认值为null
      false),  // 该属性不是必需的

  /** Lexical policy. */
  // 枚举常量：词法策略，定义标识符的解析规则
  LEX("lex", Type.ENUM, Lex.ORACLE,  // 定义词法策略属性，默认值为ORACLE风格
      false),  // 该属性不是必需的

  /** Collection of built-in functions and operators. Valid values include
   * "standard", "bigquery", "calcite", "hive", "mssql", "mysql", "oracle",
   * "postgresql", "redshift", "snowflake","spark", "spatial" and
   * "all"(operators that could be used in all libraries except "standard" and "spatial"),
   * and also comma-separated lists, for example "oracle,spatial". */
  // 枚举常量：内置函数和运算符的集合
  // 有效值包括："standard"（标准SQL）、"bigquery"、"calcite"、"hive"、"mssql"、"mysql"、"oracle"、"postgresql"、"redshift"、"snowflake"、"spark"、"spatial"
  // "all"表示可以使用除"standard"和"spatial"之外所有库的运算符
  // 也支持逗号分隔的列表，例如"oracle,spatial"
  FUN("fun", Type.STRING, "standard",  // 定义函数和运算符集合属性，默认值为"standard"
      true),  // 该属性不是必需的

  /** How identifiers are quoted.
   * If not specified, value from {@link #LEX} is used. */
  // 枚举常量：标识符如何引用（加引号）
  // 如果未指定，则使用LEX属性的值
  QUOTING("quoting", Type.ENUM, null,  // 定义标识符引用方式属性，默认值为null（从LEX继承）
      false,  // 该属性不是必需的
      Quoting.class),  // 枚举值的类型是Quoting类

  /** How identifiers are stored if they are quoted.
   * If not specified, value from {@link #LEX} is used. */
  // 枚举常量：加引号的标识符如何存储（大小写规则）
  // 如果未指定，则使用LEX属性的值
  QUOTED_CASING("quotedCasing", Type.ENUM, null,  // 定义加引号标识符的大小写规则属性，默认值为null（从LEX继承）
      false,  // 该属性不是必需的
      Casing.class),  // 枚举值的类型是Casing类

  /** How identifiers are stored if they are not quoted.
   * If not specified, value from {@link #LEX} is used. */
  // 枚举常量：不加引号的标识符如何存储（大小写规则）
  // 如果未指定，则使用LEX属性的值
  UNQUOTED_CASING("unquotedCasing", Type.ENUM, null,  // 定义不加引号标识符的大小写规则属性，默认值为null（从LEX继承）
      false,  // 该属性不是必需的
      Casing.class),  // 枚举值的类型是Casing类

  /** Whether identifiers are matched case-sensitively.
   * If not specified, value from {@link #LEX} is used. */
  // 枚举常量：标识符是否区分大小写匹配
  // 如果未指定，则使用LEX属性的值
  CASE_SENSITIVE("caseSensitive", Type.BOOLEAN, null,  // 定义标识符是否区分大小写属性，默认值为null（从LEX继承）
      false),  // 该属性不是必需的

  /** Parser factory.
   *
   * <p>The name of a class that implements
   * {@link org.apache.calcite.sql.parser.SqlParserImplFactory}. */
  // 枚举常量：SQL解析器工厂
  // 这是一个实现了SqlParserImplFactory接口的类的名称
  PARSER_FACTORY("parserFactory", Type.PLUGIN, null,  // 定义SQL解析器工厂类名属性，默认值为null
      false),  // 该属性不是必需的

  /** MetaTableFactory plugin. */
  // 枚举常量：元数据表工厂插件
  META_TABLE_FACTORY("metaTableFactory", Type.PLUGIN, null,  // 定义元数据表工厂插件类名属性，默认值为null
      false),  // 该属性不是必需的

  /** MetaColumnFactory plugin. */
  // 枚举常量：元数据列工厂插件
  META_COLUMN_FACTORY("metaColumnFactory", Type.PLUGIN, null,  // 定义元数据列工厂插件类名属性，默认值为null
      false),  // 该属性不是必需的

  /** Name of initial schema. */
  // 枚举常量：初始schema（模式）的名称
  SCHEMA("schema", Type.STRING, null,  // 定义初始schema名称属性，默认值为null
      false),  // 该属性不是必需的

  /** Schema factory.
   *
   * <p>The name of a class that implements
   * {@link org.apache.calcite.schema.SchemaFactory}.
   *
   * <p>Ignored if {@link #MODEL} is specified. */
  // 枚举常量：schema工厂
  // 这是一个实现了SchemaFactory接口的类的名称
  // 如果指定了MODEL属性，则忽略此属性
  SCHEMA_FACTORY("schemaFactory", Type.PLUGIN, null,  // 定义schema工厂类名属性，默认值为null
      false),  // 该属性不是必需的

  /** Schema type.
   *
   * <p>Value may be null, "MAP", "JDBC", or "CUSTOM"
   * (implicit if {@link #SCHEMA_FACTORY} is specified).
   *
   * <p>Ignored if {@link #MODEL} is specified. */
  // 枚举常量：schema类型
  // 值可以是null、"MAP"、"JDBC"或"CUSTOM"
  // 如果指定了SCHEMA_FACTORY，则隐含为CUSTOM类型
  // 如果指定了MODEL属性，则忽略此属性
  SCHEMA_TYPE("schemaType", Type.ENUM, null,  // 定义schema类型属性，默认值为null
      false,  // 该属性不是必需的
      JsonSchema.Type.class),  // 枚举值的类型是JsonSchema.Type类

  /** Specifies whether Spark should be used as the engine for processing that
   * cannot be pushed to the source system. If false (the default), Calcite
   * generates code that implements the Enumerable interface. */
  // 枚举常量：指定是否使用Spark作为无法推送到源系统的处理引擎
  // 如果为false（默认值），Calcite生成实现Enumerable接口的代码
  SPARK("spark", Type.BOOLEAN, false,  // 定义是否使用Spark引擎属性，默认值为false
      false),  // 该属性不是必需的

  /** Returns the time zone from the connect string, for example 'gmt-3'.
   * If the time zone is not set then the JVM time zone is returned.
   * Never null. */
  // 枚举常量：从连接字符串返回时区，例如'gmt-3'
  // 如果未设置时区，则返回JVM时区
  // 永远不会为null
  TIME_ZONE("timeZone", Type.STRING, TimeZone.getDefault().getID(),  // 定义时区属性，默认值为JVM默认时区
      false),  // 该属性不是必需的

  /** Returns the locale from the connect string.
   * If the locale is not set, returns the root locale.
   * Never null.
   * Examples of valid locales: 'en', 'en_US',
   * 'de_DE', '_GB', 'en_US_WIN', 'de__POSIX', 'fr__MAC', ''. */
  // 枚举常量：从连接字符串返回本地化设置
  // 如果未设置locale，则返回根locale
  // 永远不会为null
  // 有效locale示例：'en'、'en_US'、'de_DE'、'_GB'、'en_US_WIN'、'de__POSIX'、'fr__MAC'、''
  LOCALE("locale", Type.STRING, Locale.ROOT.toString(),  // 定义本地化属性，默认值为根locale
      false),  // 该属性不是必需的

  /** If the planner should try de-correlating as much as it is possible.
   * If true (the default), Calcite de-correlates the plan. */
  // 枚举常量：优化器是否应该尽可能尝试去相关化
  // 如果为true（默认值），Calcite会对计划进行去相关化处理
  FORCE_DECORRELATE("forceDecorrelate", Type.BOOLEAN, true,  // 定义是否强制去相关化属性，默认值为true
      false),  // 该属性不是必需的

  /** Type system. The name of a class that implements
   * {@link org.apache.calcite.rel.type.RelDataTypeSystem} and has a public
   * default constructor or an {@code INSTANCE} constant. */
  // 枚举常量：类型系统
  // 这是一个实现了RelDataTypeSystem接口的类的名称
  // 该类需要有一个公共的默认构造函数或INSTANCE常量
  TYPE_SYSTEM("typeSystem", Type.PLUGIN, null,  // 定义类型系统类名属性，默认值为null
      false),  // 该属性不是必需的

  /** SQL conformance level.
   *
   * <p>Controls the semantics of ISO standard SQL features that are implemented
   * in non-standard ways in some other systems.
   *
   * <p>For example, the {@code SUBSTRING(string FROM start [FOR length])}
   * operator treats negative {@code start} values as 1, but BigQuery's
   * implementation regards negative {@code starts} as counting from the end.
   * If {@code conformance=BIG_QUERY} we will use BigQuery's behavior.
   *
   * <p>This property only affects ISO standard SQL features. For example, the
   * {@code SUBSTR} function is non-standard, so is controlled by the
   * {@link #FUN fun} property. If you set {@code fun=oracle} you will get
   * {@code SUBSTR} with Oracle's semantics; if you set {@code fun=postgres} you
   * will get {@code SUBSTR} with PostgreSQL's (slightly different)
   * semantics. */
  // 枚举常量：SQL兼容性级别
  // 控制以非标准方式在其他系统中实现的ISO标准SQL特性的语义
  // 例如：SUBSTRING(string FROM start [FOR length])运算符将负的start值视为1
  // 但BigQuery的实现将负的start值视为从末尾开始计数
  // 如果conformance=BIG_QUERY，我们将使用BigQuery的行为
  // 此属性只影响ISO标准SQL特性
  // 例如，SUBSTR函数是非标准的，所以由FUN属性控制
  // 如果设置fun=oracle，将获得Oracle语义的SUBSTR
  // 如果设置fun=postgres，将获得PostgreSQL语义的SUBSTR（略有不同）
  CONFORMANCE("conformance", Type.ENUM, SqlConformanceEnum.DEFAULT,  // 定义SQL兼容性级别属性，默认值为DEFAULT
      false),  // 该属性不是必需的

  /** Whether to make implicit type coercion when type mismatch
   * for validation, default true. */
  // 枚举常量：验证时类型不匹配是否进行隐式类型转换
  TYPE_COERCION("typeCoercion", Type.BOOLEAN, true,  // 定义是否启用隐式类型转换属性，默认值为true
      false),  // 该属性不是必需的

  /** Whether to make create implicit functions if functions do not exist
   * in the operator table, default false. */
  // 枚举常量：如果运算符表中不存在函数，是否创建隐式函数
  LENIENT_OPERATOR_LOOKUP("lenientOperatorLookup", Type.BOOLEAN, false,  // 定义是否启用宽松运算符查找属性，默认值为false
      false),  // 该属性不是必需的

  /** Whether to enable top-down optimization in Volcano planner. */
  // 枚举常量：是否在Volcano优化器中启用自顶向下的优化
  TOPDOWN_OPT("topDownOpt", Type.BOOLEAN, CalciteSystemProperty.TOPDOWN_OPT.value(),  // 定义是否启用自顶向下优化属性，默认值从系统属性获取
      false);  // 该属性不是必需的

  private final String camelName;  // 成员变量：属性的驼峰命名形式，例如"approximateDistinctCount"
  private final Type type;  // 成员变量：属性的数据类型，例如BOOLEAN、STRING、ENUM等
  @SuppressWarnings("ImmutableEnumChecker")  // 抑制不可变枚举检查器的警告
  private final @Nullable Object defaultValue;  // 成员变量：属性的默认值，可能为null
  private final boolean required;  // 成员变量：属性是否为必需的，true表示必需，false表示可选
  private final @Nullable Class valueClass;  // 成员变量：属性值的类型，对于ENUM类型特别重要

  private static final Map<String, CalciteConnectionProperty> NAME_TO_PROPS;  // 静态成员变量：名称到属性的映射表，用于快速查找属性

  /** Deprecated; use {@link #TIME_ZONE}. */
  // 注释：已弃用，请使用TIME_ZONE
  @Deprecated // to be removed before 2.0  // 标记为已弃用，将在2.0版本之前移除
  public static final CalciteConnectionProperty TIMEZONE = TIME_ZONE;  // 静态常量：已弃用的时区属性，指向TIME_ZONE

  static {  // 静态初始化块，在类加载时执行，用于初始化静态成员变量
    NAME_TO_PROPS = new HashMap<>();  // 创建HashMap实例，用于存储属性名称到枚举常量的映射
    for (CalciteConnectionProperty p : CalciteConnectionProperty.values()) {  // 遍历所有枚举常量
      NAME_TO_PROPS.put(p.camelName.toUpperCase(Locale.ROOT), p);  // 将驼峰名称转大写后作为key，属性作为value存入map
      NAME_TO_PROPS.put(p.name(), p);  // 将枚举常量的name()（大写的枚举名）作为key，属性作为value存入map
    }
  }  // 静态初始化块结束

  CalciteConnectionProperty(String camelName, Type type, @Nullable Object defaultValue,  // 构造方法：创建连接属性实例（4参数版本）
      boolean required) {  // 参数：required表示属性是否必需
    this(camelName, type, defaultValue, required, null);  // 调用5参数版本的构造方法，valueClass传null
  }  // 构造方法结束

  CalciteConnectionProperty(String camelName, Type type, @Nullable Object defaultValue,  // 构造方法：创建连接属性实例（5参数版本）
      boolean required, @Nullable Class valueClass) {  // 参数：required表示属性是否必需，valueClass表示属性值的类型
    this.camelName = camelName;  // 初始化camelName成员变量，存储属性的驼峰命名
    this.type = type;  // 初始化type成员变量，存储属性的数据类型
    this.defaultValue = defaultValue;  // 初始化defaultValue成员变量，存储属性的默认值
    this.required = required;  // 初始化required成员变量，存储属性是否必需的标志
    this.valueClass = type.deduceValueClass(defaultValue, valueClass);  // 根据类型、默认值和valueClass推断实际的值类型
    if (!type.valid(defaultValue, this.valueClass)) {  // 验证默认值是否与类型匹配
      throw new AssertionError(camelName);  // 如果不匹配，抛出断言错误
    }
  }  // 构造方法结束

  @Override public String camelName() {  // 方法：返回属性的驼峰命名（实现ConnectionProperty接口）
    return camelName;  // 返回camelName成员变量的值
  }  // 方法结束

  @Override public @Nullable Object defaultValue() {  // 方法：返回属性的默认值（实现ConnectionProperty接口）
    return defaultValue;  // 返回defaultValue成员变量的值
  }  // 方法结束

  @Override public Type type() {  // 方法：返回属性的数据类型（实现ConnectionProperty接口）
    return type;  // 返回type成员变量的值
  }  // 方法结束

  @Override public @Nullable Class valueClass() {  // 方法：返回属性值的类型（实现ConnectionProperty接口）
    return valueClass;  // 返回valueClass成员变量的值
  }  // 方法结束

  @Override public boolean required() {  // 方法：返回属性是否必需（实现ConnectionProperty接口）
    return required;  // 返回required成员变量的值
  }  // 方法结束

  @Override public PropEnv wrap(Properties properties) {  // 方法：将Properties包装为PropEnv对象（实现ConnectionProperty接口）
    return new PropEnv(parse(properties, NAME_TO_PROPS), this);  // 解析properties并创建PropEnv对象，传入解析结果和当前属性
  }  // 方法结束
}
