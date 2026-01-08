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
// Apache许可证声明，说明该代码遵循Apache 2.0许可证
// 这是Apache软件基金会的标准许可证头，允许在特定条件下自由使用、修改和分发代码
package org.apache.calcite.config; // 定义包名，该类位于org.apache.calcite.config包中，属于Calcite配置模块

import org.apache.calcite.avatica.ConnectionConfigImpl; // 导入Avatica的ConnectionConfigImpl类，这是父类，提供基础的连接配置功能
// Avatica是Calcite的底层框架，提供了数据库连接和查询执行的基础功能
import org.apache.calcite.avatica.util.Casing; // 导入Casing枚举类，用于定义标识符的大小写规则（如大写、小写、不敏感等）
// Casing控制SQL中未加引号和加引号的标识符如何被转换（例如：UPPER, LOWER, UNCHANGED）
import org.apache.calcite.avatica.util.Quoting; // 导入Quoting枚举类，用于定义SQL标识符的引号风格
// Quoting决定了SQL中标识符的引号类型，例如使用双引号(")还是反引号(`)来引用标识符
import org.apache.calcite.model.JsonSchema; // 导入JsonSchema类，用于表示JSON格式的schema模型
// JsonSchema允许通过JSON文件定义数据库的schema结构，包括表、视图等元数据
import org.apache.calcite.runtime.ConsList; // 导入ConsList类，这是一个不可变的持久化列表实现
// ConsList用于函数式编程风格的列表操作，提供高效的头部插入和共享功能
import org.apache.calcite.sql.SqlOperatorTable; // 导入SqlOperatorTable接口，定义SQL操作符表的接口
// SqlOperatorTable包含所有可用的SQL操作符（如函数、运算符），用于SQL解析和验证
import org.apache.calcite.sql.fun.SqlLibrary; // 导入SqlLibrary枚举类，定义SQL函数库的类型
// SqlLibrary指定了Calcite支持的SQL函数库，如STANDARD（标准SQL）、SPARK（Spark SQL）等
import org.apache.calcite.sql.fun.SqlLibraryOperatorTableFactory; // 导入SqlLibraryOperatorTableFactory类，用于创建SQL操作符表
// 该工厂类根据指定的SQL库类型创建对应的操作符表实例
import org.apache.calcite.sql.validate.SqlConformance; // 导入SqlConformance接口，定义SQL符合性标准
// SqlConformance控制Calcite对不同SQL方言的兼容性级别（如MySQL、PostgreSQL、Oracle等）
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SqlConformanceEnum枚举类，提供预定义的SQL符合性实现
// 该枚举包含了常见的SQL方言符合性配置，如LENIENT（宽松）、STRICT（严格）、ORACLE_10等

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，用于标记可能为null的返回值
// CheckerFramework是静态类型检查工具，Nullable注解帮助编译器检测潜在的空指针异常
import org.checkerframework.checker.nullness.qual.PolyNull; // 导入PolyNull注解，用于多态的null类型
// PolyNull表示类型可能是null也可能不是，取决于输入参数的null状态，用于泛型方法

import java.util.List; // 导入List接口，Java集合框架的核心接口，表示有序的元素集合
// List用于存储和管理有序的数据集合，如SQL库列表、配置属性列表等
import java.util.Properties; // 导入Properties类，用于管理键值对形式的配置属性
// Properties继承自Hashtable，专门用于存储字符串类型的配置键值对，是Java标准的配置管理类

/** Implementation of {@link CalciteConnectionConfig}. */ // 类的JavaDoc注释：这是CalciteConnectionConfig接口的实现类
// CalciteConnectionConfig定义了Calcite连接配置的标准接口，本类提供了具体的实现
// 该类封装了Calcite数据库连接的所有配置属性，包括SQL方言、类型系统、优化器设置等
public class CalciteConnectionConfigImpl extends ConnectionConfigImpl // 定义CalciteConnectionConfigImpl类，继承自ConnectionConfigImpl
    implements CalciteConnectionConfig { // 实现CalciteConnectionConfig接口，提供Calcite特定的配置功能
  // 类继承关系：CalciteConnectionConfigImpl -> ConnectionConfigImpl -> Object
  // ConnectionConfigImpl来自Avatica框架，提供了基础的连接配置管理功能
  // CalciteConnectionConfig接口定义了Calcite特有的配置方法，如SQL符合性、类型系统等
  // 成员变量：本类没有定义额外的成员变量，所有配置属性都通过继承的properties字段管理
  // properties字段在父类ConnectionConfigImpl中定义，类型为Properties，存储所有配置键值对

  public CalciteConnectionConfigImpl(Properties properties) { // 构造方法：接收一个Properties对象作为配置参数
    super(properties); // 调用父类ConnectionConfigImpl的构造方法，将配置属性传递给父类初始化
    // 父类会将properties对象保存到实例变量中，供后续方法使用
    // Properties对象包含所有连接配置，如：lex=ORACLE, conformance=LENIENT, typeSystem=org.apache.calcite.rel.type.RelDataTypeSystemImpl等
    // 构造方法不进行任何验证，假设传入的properties对象是有效的
    // 典型的properties配置示例：
    //   lex=JAVA                    // 词法分析器配置
    //   conformance=LENIENT          // SQL符合性级别
    //   caseSensitive=false          // 是否区分大小写
    //   timeZone=GMT                 // 时区设置
    //   typeCoercion=true            // 是否启用类型强制转换
  } // 构造方法结束

  /** Returns a copy of this configuration with one property changed. // 返回一个修改了单个属性后的配置副本
   * // 该方法实现了不可变对象模式，不会修改当前对象，而是返回一个新的配置对象
   * <p>Does not modify this configuration. */ // 明确说明不会修改当前配置对象，而是返回新对象
  // 这种设计模式称为函数式编程风格，保证了配置对象的线程安全性和不可变性
  public CalciteConnectionConfigImpl set(CalciteConnectionProperty property, // 定义set方法，接收一个配置属性和要设置的值
      String value) { // value参数是要设置的属性值，类型为String
    final Properties newProperties = (Properties) properties.clone(); // 克隆当前的properties对象，创建新的Properties实例
    // 使用clone()方法创建深拷贝，确保新配置与原配置完全独立
    // 克隆操作避免了修改原配置对象，符合不可变对象设计原则
    newProperties.setProperty(property.camelName(), value); // 在新的Properties对象中设置指定的属性值
    // property.camelName()返回属性的驼峰命名形式，如"caseSensitive"、"materializationsEnabled"
    // setProperty方法将键值对存入Properties对象，如果键已存在则覆盖原值
    return new CalciteConnectionConfigImpl(newProperties); // 返回一个新的CalciteConnectionConfigImpl实例，包含修改后的配置
    // 创建新对象而不是修改当前对象，保证了配置的不可变性
    // 调用者可以使用返回的新配置，而原配置保持不变
  } // set方法结束

  /** Returns a copy of this configuration with the value of a property // 返回一个移除了指定属性后的配置副本
   * removed. // 该方法用于删除某个配置属性，恢复到默认值
   * <p>Does not modify this configuration. */ // 明确说明不会修改当前配置对象，而是返回新对象
  // 与set方法类似，也采用不可变对象模式，保证线程安全
  public CalciteConnectionConfigImpl unset(CalciteConnectionProperty property) { // 定义unset方法，接收要移除的配置属性
    final Properties newProperties = (Properties) properties.clone(); // 克隆当前的properties对象，创建新的Properties实例
    // 克隆操作确保原配置对象不被修改
    newProperties.remove(property.camelName()); // 从新的Properties对象中移除指定的属性
    // property.camelName()返回属性的驼峰命名形式
    // remove方法删除该键值对，后续访问该属性时将返回默认值
    return new CalciteConnectionConfigImpl(newProperties); // 返回一个新的CalciteConnectionConfigImpl实例，包含移除属性后的配置
    // 返回的新配置对象中，被移除的属性将使用其默认值
  } // unset方法结束

  /** Returns whether a given property has been assigned a value. // 检查指定的配置属性是否已被显式设置值
   * // 该方法用于判断某个配置属性是否在properties中存在
   * <p>If not, the value returned for the property will be its default value. */ // 如果未设置，该属性将返回其默认值
  // 这个方法对于区分显式设置的值和默认值很有用
  public boolean isSet(CalciteConnectionProperty property) { // 定义isSet方法，接收要检查的配置属性
    return properties.containsKey(property.camelName()); // 检查properties对象中是否包含指定的属性键
    // containsKey方法返回true表示该属性已被显式设置，false表示未设置（将使用默认值）
    // property.camelName()返回属性的驼峰命名形式，如"caseSensitive"
  } // isSet方法结束

  @Override public boolean approximateDistinctCount() { // 重写approximateDistinctCount方法，返回是否使用近似去重计数
    return CalciteConnectionProperty.APPROXIMATE_DISTINCT_COUNT.wrap(properties) // 获取APPROXIMATE_DISTINCT_COUNT属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值，如果未设置则返回默认值
    // APPROXIMATE_DISTINCT_COUNT控制是否使用近似算法计算DISTINCT COUNT
    // 近似算法（如HyperLogLog）在大数据量下性能更好，但结果可能有误差
    // 默认值通常为false，表示使用精确的去重计数
    // 该属性影响COUNT(DISTINCT expr)操作的计算方式
  } // approximateDistinctCount方法结束

  @Override public boolean approximateTopN() { // 重写approximateTopN方法，返回是否使用近似TopN算法
    return CalciteConnectionProperty.APPROXIMATE_TOP_N.wrap(properties) // 获取APPROXIMATE_TOP_N属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // APPROXIMATE_TOP_N控制是否使用近似算法执行ORDER BY ... LIMIT N操作
    // 近似TopN算法在大数据量下性能更好，但可能不保证结果的精确排序
    // 默认值通常为false，表示使用精确的排序和TopN算法
    // 该属性影响SQL查询中ORDER BY + LIMIT的性能和准确性
  } // approximateTopN方法结束

  @Override public boolean approximateDecimal() { // 重写approximateDecimal方法，返回是否使用近似小数计算
    return CalciteConnectionProperty.APPROXIMATE_DECIMAL.wrap(properties) // 获取APPROXIMATE_DECIMAL属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // APPROXIMATE_DECIMAL控制是否使用近似算法处理DECIMAL类型数据
    // 近似计算可以提高性能，但可能损失精度
    // 默认值通常为false，表示使用精确的小数计算
    // 该属性影响DECIMAL类型数据的算术运算和聚合操作
  } // approximateDecimal方法结束

  @Override public boolean nullEqualToEmpty() { // 重写nullEqualToEmpty方法，返回是否将NULL视为空字符串
    return CalciteConnectionProperty.NULL_EQUAL_TO_EMPTY.wrap(properties).getBoolean(); // 获取NULL_EQUAL_TO_EMPTY属性的布尔值
    // NULL_EQUAL_TO_EMPTY控制字符串比较时是否将NULL值等同于空字符串
    // 如果为true，NULL = '' 会返回true；如果为false，NULL = '' 返回NULL（SQL标准行为）
    // 默认值通常为false，遵循SQL标准，NULL与任何值比较都返回NULL
    // 该属性影响字符串比较、连接操作等涉及NULL的情况
  } // nullEqualToEmpty方法结束

  @Override public boolean autoTemp() { // 重写autoTemp方法，返回是否自动创建临时表
    return CalciteConnectionProperty.AUTO_TEMP.wrap(properties).getBoolean(); // 获取AUTO_TEMP属性的布尔值
    // AUTO_TEMP控制是否自动创建和管理临时表
    // 如果为true，Calcite会自动处理临时表的创建和清理
    // 默认值通常为false，需要显式创建临时表
    // 该属性影响CREATE TEMPORARY TABLE语句的行为
  } // autoTemp方法结束

  @Override public boolean materializationsEnabled() { // 重写materializationsEnabled方法，返回是否启用物化视图
    return CalciteConnectionProperty.MATERIALIZATIONS_ENABLED.wrap(properties) // 获取MATERIALIZATIONS_ENABLED属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // MATERIALIZATIONS_ENABLED控制是否启用物化视图功能
    // 物化视图是预先计算并存储的查询结果，可以显著提高查询性能
    // 如果为true，优化器会考虑使用物化视图来加速查询
    // 默认值通常为true，表示启用物化视图
    // 该属性影响查询优化器是否考虑物化视图替换
  } // materializationsEnabled方法结束

  @Override public boolean createMaterializations() { // 重写createMaterializations方法，返回是否自动创建物化视图
    return CalciteConnectionProperty.CREATE_MATERIALIZATIONS.wrap(properties) // 获取CREATE_MATERIALIZATIONS属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // CREATE_MATERIALIZATIONS控制是否自动创建物化视图
    // 如果为true，Calcite会根据查询模式自动创建物化视图
    // 默认值通常为false，需要显式创建物化视图
    // 该属性影响物化视图的自动创建策略
  } // createMaterializations方法结束

  @Override public NullCollation defaultNullCollation() { // 重写defaultNullCollation方法，返回NULL值的默认排序规则
    return CalciteConnectionProperty.DEFAULT_NULL_COLLATION.wrap(properties) // 获取DEFAULT_NULL_COLLATION属性的包装器
        .getEnum(NullCollation.class, NullCollation.HIGH); // 获取枚举值，如果未设置则默认为NullCollation.HIGH
    // DEFAULT_NULL_COLLATION控制NULL值在排序中的位置
    // NullCollation枚举值包括：
    //   - HIGH: NULL值排在最后（升序时）
    //   - LOW: NULL值排在最前（升序时）
    //   - UNSPECIFIED: 未指定，由数据库决定
    // 默认值为HIGH，表示NULL值在升序排序时排在最后
    // 该属性影响ORDER BY、GROUP BY等涉及NULL排序的操作
  } // defaultNullCollation方法结束

  @Override public <T> @PolyNull T fun(Class<T> operatorTableClass, // 重写fun方法，返回SQL函数操作符表
      @PolyNull T defaultOperatorTable) { // defaultOperatorTable参数是默认的操作符表
    final String fun = // 定义fun变量，存储函数库配置字符串
        CalciteConnectionProperty.FUN.wrap(properties).getString(); // 从配置中获取FUN属性的值
    if (fun == null || fun.equals("") || fun.equals("standard")) { // 如果fun为null、空字符串或"standard"
      return defaultOperatorTable; // 返回默认的操作符表
      // "standard"表示使用标准SQL函数库，不需要特殊处理
    } // if语句结束
    // Parse the libraries // 注释：解析函数库
    final List<SqlLibrary> libraryList = SqlLibrary.parse(fun); // 使用SqlLibrary.parse方法解析fun字符串，返回库列表
    // SqlLibrary.parse方法将逗号分隔的字符串解析为SqlLibrary枚举列表
    // 例如："SPARK,POSTGRESQL" 会被解析为 [SqlLibrary.SPARK, SqlLibrary.POSTGRESQL]
    // 支持的库包括：STANDARD, SPARK, POSTGRESQL, MYSQL, ORACLE, BIGQUERY, MSSQL等
    // Load standard plus the specified libraries. If 'all' is among the // 注释：加载标准库加上指定的库。如果'all'在指定的库中
    // specified libraries, it is expanded to all libraries (except standard, // 注释：它会被扩展为所有库（除了standard、spatial、all）
    // spatial, all). // 注释：spatial、all）
    final List<SqlLibrary> libraryList1 = // 定义libraryList1变量，存储扩展后的库列表
        SqlLibrary.expand(ConsList.of(SqlLibrary.STANDARD, libraryList)); // 调用SqlLibrary.expand方法扩展库列表
    // ConsList.of创建一个不可变列表，包含STANDARD库和解析出的库列表
    // SqlLibrary.expand方法处理特殊库：
    //   - "all"：扩展为所有可用库（除了STANDARD、SPATIAL、ALL本身）
    //   - "spatial"：扩展为空间函数库
    //   - 其他库：保持不变
    // 扩展后的列表包含了所有需要加载的函数库
    final SqlOperatorTable operatorTable = // 定义operatorTable变量，存储操作符表实例
        SqlLibraryOperatorTableFactory.INSTANCE.getOperatorTable(libraryList1); // 使用工厂类根据库列表创建操作符表
    // SqlLibraryOperatorTableFactory.INSTANCE是单例工厂对象
    // getOperatorTable方法根据库列表创建对应的SqlOperatorTable实例
    // 操作符表包含了所有指定库中的SQL函数和操作符，用于SQL解析和验证
    return operatorTableClass.cast(operatorTable); // 将操作符表转换为指定的类型并返回
    // operatorTableClass.cast()使用反射将operatorTable转换为调用者期望的类型
    // @PolyNull注解表示返回值的null状态取决于defaultOperatorTable的null状态
    // 如果fun未设置或为"standard"，返回defaultOperatorTable（可能为null）
    // 否则返回创建的操作符表（不为null）
  } // fun方法结束

  @Override public @Nullable String model() { // 重写model方法，返回模型配置文件的路径
    return CalciteConnectionProperty.MODEL.wrap(properties).getString(); // 获取MODEL属性的字符串值
    // MODEL属性指定了JSON格式的schema模型文件路径
    // 例如："file:/path/to/model.json" 或 "inline:{...}"
    // 如果未设置，返回null表示没有使用模型文件
    // 模型文件定义了数据库的schema结构，包括表、视图、函数等元数据
    // 该属性影响schema的初始化方式
  } // model方法结束

  @Override public Lex lex() { // 重写lex方法，返回词法分析器配置
    return CalciteConnectionProperty.LEX.wrap(properties).getEnum(Lex.class); // 获取LEX属性的枚举值
    // LEX属性指定了SQL词法分析器的配置，影响SQL的解析规则
    // Lex枚举值包括：
    //   - JAVA: Java风格（标识符区分大小写，使用双引号引用）
    //   - ORACLE: Oracle风格（标识符不区分大小写，默认转大写，使用双引号引用）
    //   - MYSQL: MySQL风格（标识符不区分大小写，使用反引号引用）
    //   - SQL_SERVER: SQL Server风格（标识符不区分大小写，使用方括号引用）
    //   - SQL99: SQL99标准风格
    // 默认值通常为JAVA，该属性影响SQL解析器的行为
  } // lex方法结束

  @Override public Quoting quoting() { // 重写quoting方法，返回标识符的引号风格
    return CalciteConnectionProperty.QUOTING.wrap(properties) // 获取QUOTING属性的包装器
        .getEnum(Quoting.class, lex().quoting); // 获取枚举值，如果未设置则使用lex()的默认引号风格
    // QUOTING属性指定了SQL标识符的引号类型
    // Quoting枚举值包括：
    //   - DOUBLE_QUOTE: 使用双引号(")引用标识符（标准SQL）
    //   - BACK_TICK: 使用反引号(`)引用标识符（MySQL风格）
    //   - BRACKET: 使用方括号([])引用标识符（SQL Server风格）
    // 如果未明确设置，使用lex()配置对应的默认引号风格
    // 该属性影响SQL解析器如何识别引用标识符
  } // quoting方法结束

  @Override public Casing unquotedCasing() { // 重写unquotedCasing方法，返回未加引号标识符的大小写规则
    return CalciteConnectionProperty.UNQUOTED_CASING.wrap(properties) // 获取UNQUOTED_CASING属性的包装器
        .getEnum(Casing.class, lex().unquotedCasing); // 获取枚举值，如果未设置则使用lex()的默认规则
    // UNQUOTED_CASING属性指定了未加引号的标识符如何转换大小写
    // Casing枚举值包括：
    //   - TO_UPPER: 转换为大写（Oracle、SQL Server默认）
    //   - TO_LOWER: 转换为小写（MySQL、PostgreSQL默认）
    //   - UNCHANGED: 保持原样（Java默认）
    // 如果未明确设置，使用lex()配置对应的默认规则
    // 该属性影响SQL解析器如何处理未加引号的标识符
  } // unquotedCasing方法结束

  @Override public Casing quotedCasing() { // 重写quotedCasing方法，返回加引号标识符的大小写规则
    return CalciteConnectionProperty.QUOTED_CASING.wrap(properties) // 获取QUOTED_CASING属性的包装器
        .getEnum(Casing.class, lex().quotedCasing); // 获取枚举值，如果未设置则使用lex()的默认规则
    // QUOTED_CASING属性指定了加引号的标识符如何转换大小写
    // 加引号的标识符通常保持原样（UNCHANGED），但某些数据库可能有不同规则
    // 如果未明确设置，使用lex()配置对应的默认规则
    // 该属性影响SQL解析器如何处理加引号的标识符
  } // quotedCasing方法结束

  @Override public boolean caseSensitive() { // 重写caseSensitive方法，返回是否区分标识符大小写
    return CalciteConnectionProperty.CASE_SENSITIVE.wrap(properties) // 获取CASE_SENSITIVE属性的包装器
        .getBoolean(lex().caseSensitive); // 获取布尔值，如果未设置则使用lex()的默认值
    // CASE_SENSITIVE属性控制标识符匹配时是否区分大小写
    // 如果为true，"TableName"和"tablename"被视为不同的标识符
    // 如果为false，标识符匹配时不区分大小写
    // 默认值取决于lex()配置：
    //   - JAVA: true（区分大小写）
    //   - ORACLE: false（不区分大小写，转大写）
    //   - MYSQL: false（不区分大小写，转小写）
    // 该属性影响SQL解析和元数据查询的行为
  } // caseSensitive方法结束

  @Override public <T> @PolyNull T parserFactory(Class<T> parserFactoryClass, // 重写parserFactory方法，返回SQL解析器工厂
      @PolyNull T defaultParserFactory) { // defaultParserFactory参数是默认的解析器工厂
    return CalciteConnectionProperty.PARSER_FACTORY.wrap(properties) // 获取PARSER_FACTORY属性的包装器
        .getPlugin(parserFactoryClass, defaultParserFactory); // 获取插件实例，如果未设置则返回默认值
    // PARSER_FACTORY属性指定了自定义的SQL解析器工厂类
    // 使用反射机制加载指定的解析器工厂类
    // getPlugin方法会尝试实例化配置的类，如果失败则返回defaultParserFactory
    // 允许用户扩展或替换Calcite的默认SQL解析器
    // @PolyNull注解表示返回值的null状态取决于defaultParserFactory的null状态
    // 该属性影响SQL解析器的创建和初始化
  } // parserFactory方法结束

  @Override public <T> @PolyNull T schemaFactory(Class<T> schemaFactoryClass, // 重写schemaFactory方法，返回Schema工厂
      @PolyNull T defaultSchemaFactory) { // defaultSchemaFactory参数是默认的Schema工厂
    return CalciteConnectionProperty.SCHEMA_FACTORY.wrap(properties) // 获取SCHEMA_FACTORY属性的包装器
        .getPlugin(schemaFactoryClass, defaultSchemaFactory); // 获取插件实例，如果未设置则返回默认值
    // SCHEMA_FACTORY属性指定了自定义的Schema工厂类
    // Schema工厂负责创建和管理数据库schema（表、视图等元数据）
    // 使用反射机制加载指定的Schema工厂类
    // 允许用户自定义schema的创建方式，例如从不同数据源加载元数据
    // 典型的schema工厂包括：JsonSchemaFactory（从JSON加载）、JdbcSchemaFactory（从JDBC加载）等
    // @PolyNull注解表示返回值的null状态取决于defaultSchemaFactory的null状态
    // 该属性影响schema的初始化和加载方式
  } // schemaFactory方法结束

  @Override public JsonSchema.Type schemaType() { // 重写schemaType方法，返回Schema的类型
    return CalciteConnectionProperty.SCHEMA_TYPE.wrap(properties) // 获取SCHEMA_TYPE属性的包装器
        .getEnum(JsonSchema.Type.class); // 获取枚举值
    // SCHEMA_TYPE属性指定了Schema的类型
    // JsonSchema.Type枚举值包括：
    //   - MAP: Map类型，使用嵌套的Map结构表示schema
    //   - JDBC: JDBC类型，通过JDBC连接访问schema
    //   - CUSTOM: 自定义类型
    // 默认值通常为MAP，该属性影响schema的存储和访问方式
    // 该属性主要与JsonSchema配置相关
  } // schemaType方法结束

  @Override public boolean spark() { // 重写spark方法，返回是否启用Spark兼容模式
    return CalciteConnectionProperty.SPARK.wrap(properties).getBoolean(); // 获取SPARK属性的布尔值
    // SPARK属性控制是否启用Apache Spark SQL兼容模式
    // 如果为true，Calcite会使用Spark SQL的语法和行为规则
    // 影响包括：函数库、类型系统、SQL语法等
    // 默认值通常为false，表示使用标准Calcite行为
    // 该属性影响SQL解析和查询执行的行为
  } // spark方法结束

  @Override public boolean forceDecorrelate() { // 重写forceDecorrelate方法，返回是否强制去关联
    return CalciteConnectionProperty.FORCE_DECORRELATE.wrap(properties) // 获取FORCE_DECORRELATE属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // FORCE_DECORRELATE控制是否强制执行子查询去关联（subquery decorrelation）
    // 去关联是将相关子查询转换为连接操作的过程，可以提高查询性能
    // 如果为true，优化器会尽可能去关联所有子查询
    // 如果为false，优化器根据成本决定是否去关联
    // 默认值通常为false，该属性影响查询优化器的转换策略
  } // forceDecorrelate方法结束

  @Override public <T> @PolyNull T typeSystem(Class<T> typeSystemClass, // 重写typeSystem方法，返回类型系统
      @PolyNull T defaultTypeSystem) { // defaultTypeSystem参数是默认的类型系统
    return CalciteConnectionProperty.TYPE_SYSTEM.wrap(properties) // 获取TYPE_SYSTEM属性的包装器
        .getPlugin(typeSystemClass, defaultTypeSystem); // 获取插件实例，如果未设置则返回默认值
    // TYPE_SYSTEM属性指定了自定义的类型系统类
    // 类型系统定义了Calcite支持的数据类型及其行为
    // 使用反射机制加载指定的类型系统类
    // 允许用户扩展或替换Calcite的默认类型系统
    // 典型的类型系统包括：RelDataTypeSystemImpl（标准类型系统）、JavaTypeSystem（Java类型系统）等
    // @PolyNull注解表示返回值的null状态取决于defaultTypeSystem的null状态
    // 该属性影响数据类型的行为，如精度、范围、类型转换等
  } // typeSystem方法结束

  @Override public SqlConformance conformance() { // 重写conformance方法，返回SQL符合性级别
    return CalciteConnectionProperty.CONFORMANCE.wrap(properties) // 获取CONFORMANCE属性的包装器
        .getEnum(SqlConformanceEnum.class); // 获取枚举值
    // CONFORMANCE属性指定了SQL符合性级别，控制Calcite对不同SQL方言的兼容性
    // SqlConformanceEnum枚举值包括：
    //   - DEFAULT: 默认符合性
    //   - LENIENT: 宽松符合性，允许非标准SQL语法
    //   - STRICT: 严格符合性，只允许标准SQL语法
    //   - MYSQL_5: MySQL 5.x兼容
    //   - ORACLE_10: Oracle 10g兼容
    //   - ORACLE_12: Oracle 12c兼容
    //   - SQL_SERVER_2008: SQL Server 2008兼容
    // 默认值通常为DEFAULT或LENIENT，该属性影响SQL解析和验证的严格程度
    // 该属性影响Calcite接受哪些SQL语法和函数
  } // conformance方法结束

  @Override public String timeZone() { // 重写timeZone方法，返回时区配置
    return CalciteConnectionProperty.TIME_ZONE.wrap(properties) // 获取TIME_ZONE属性的包装器
            .getString(); // 调用getString()方法获取字符串值
    // TIME_ZONE属性指定了时区设置，影响时间戳和日期时间类型的处理
    // 例如："GMT"、"UTC"、"Asia/Shanghai"、"America/New_York"等
    // 如果未设置，可能使用系统默认时区或UTC
    // 该属性影响TIMESTAMP WITH TIME ZONE类型的行为和时间函数的结果
  } // timeZone方法结束

  @Override public String locale() { // 重写locale方法，返回区域设置
    return CalciteConnectionProperty.LOCALE.wrap(properties) // 获取LOCALE属性的包装器
        .getString(); // 调用getString()方法获取字符串值
    // LOCALE属性指定了区域设置，影响本地化相关的行为
    // 例如："en_US"、"zh_CN"、"fr_FR"等
    // 格式为语言代码_国家代码
    // 如果未设置，可能使用系统默认区域设置
    // 该属性影响错误消息、日期格式、数字格式等本地化行为
  } // locale方法结束

  @Override public boolean typeCoercion() { // 重写typeCoercion方法，返回是否启用类型强制转换
    return CalciteConnectionProperty.TYPE_COERCION.wrap(properties) // 获取TYPE_COERCION属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // TYPE_COERCION控制是否启用类型强制转换（type coercion）
    // 类型强制转换是指在不同类型之间自动进行隐式转换
    // 如果为true，Calcite会自动将兼容的类型进行转换（如INTEGER到BIGINT）
    // 如果为false，只允许显式类型转换
    // 默认值通常为true，该属性影响SQL表达式的类型检查和转换行为
    // 该属性影响SQL的类型系统和表达式求值
  } // typeCoercion方法结束

  @Override public boolean lenientOperatorLookup() { // 重写lenientOperatorLookup方法，返回是否使用宽松的操作符查找
    return CalciteConnectionProperty.LENIENT_OPERATOR_LOOKUP.wrap(properties) // 获取LENIENT_OPERATOR_LOOKUP属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // LENIENT_OPERATOR_LOOKUP控制操作符查找是否宽松
    // 如果为true，在找不到精确匹配的操作符时，会尝试查找兼容的操作符
    // 如果为false，只接受精确匹配的操作符
    // 默认值通常为false，该属性影响SQL函数和操作符的解析行为
    // 该属性影响SQL解析器如何处理函数和操作符
  } // lenientOperatorLookup方法结束

  @Override public boolean topDownOpt() { // 重写topDownOpt方法，返回是否使用自顶向下的优化策略
    return CalciteConnectionProperty.TOPDOWN_OPT.wrap(properties) // 获取TOPDOWN_OPT属性的包装器
        .getBoolean(); // 调用getBoolean()方法获取布尔值
    // TOPDOWN_OPT控制查询优化器是否使用自顶向下的优化策略
    // 自顶向下优化：从查询树的根节点开始，逐步向下优化
    // 自底向上优化：从叶子节点开始，逐步向上优化
    // 如果为true，使用自顶向下的优化策略
    // 如果为false，使用自底向上的优化策略（Calcite默认）
    // 默认值通常为false，该属性影响查询优化器的优化顺序和策略
    // 该属性影响查询优化器的行为和性能
  } // topDownOpt方法结束

  @Override public <T> @PolyNull T metaTableFactory( // 重写metaTableFactory方法，返回元数据表工厂
      Class<T> metaTableFactoryClass, // metaTableFactoryClass参数是元数据表工厂的类类型
      @PolyNull T defaultMetaTableFactory) { // defaultMetaTableFactory参数是默认的元数据表工厂
    return CalciteConnectionProperty.META_TABLE_FACTORY.wrap(properties) // 获取META_TABLE_FACTORY属性的包装器
        .getPlugin(metaTableFactoryClass, defaultMetaTableFactory); // 获取插件实例，如果未设置则返回默认值
    // META_TABLE_FACTORY属性指定了自定义的元数据表工厂类
    // 元数据表工厂负责创建和管理元数据表（如系统表、信息schema等）
    // 使用反射机制加载指定的元数据表工厂类
    // 允许用户自定义元数据表的创建方式
    // @PolyNull注解表示返回值的null状态取决于defaultMetaTableFactory的null状态
    // 该属性影响元数据表的初始化和访问方式
  } // metaTableFactory方法结束

  @Override public <T> @PolyNull T metaColumnFactory( // 重写metaColumnFactory方法，返回元数据列工厂
      Class<T> metaColumnFactoryClass, // metaColumnFactoryClass参数是元数据列工厂的类类型
      @PolyNull T defaultMetaColumnFactory) { // defaultMetaColumnFactory参数是默认的元数据列工厂
    return CalciteConnectionProperty.META_COLUMN_FACTORY.wrap(properties) // 获取META_COLUMN_FACTORY属性的包装器
        .getPlugin(metaColumnFactoryClass, defaultMetaColumnFactory); // 获取插件实例，如果未设置则返回默认值
    // META_COLUMN_FACTORY属性指定了自定义的元数据列工厂类
    // 元数据列工厂负责创建和管理元数据列（如列名、类型、注释等）
    // 使用反射机制加载指定的元数据列工厂类
    // 允许用户自定义元数据列的创建方式
    // @PolyNull注解表示返回值的null状态取决于defaultMetaColumnFactory的null状态
    // 该属性影响元数据列的初始化和访问方式
  } // metaColumnFactory方法结束
} // 类定义结束