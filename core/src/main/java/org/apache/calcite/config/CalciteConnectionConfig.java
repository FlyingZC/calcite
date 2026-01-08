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
package org.apache.calcite.config;  // 声明包名，属于org.apache.calcite.config包，该包包含Calcite配置相关的类

import org.apache.calcite.avatica.ConnectionConfig;  // 导入Avatica的ConnectionConfig接口，CalciteConnectionConfig继承自该接口
import org.apache.calcite.avatica.util.Casing;  // 导入Casing枚举，用于定义标识符的大小写规则（大写、小写、不区分大小写）
import org.apache.calcite.avatica.util.Quoting;  // 导入Quoting枚举，用于定义标识符的引用方式（双引号、反引号、方括号等）
import org.apache.calcite.model.JsonSchema;  // 导入JsonSchema类，用于表示JSON格式的Schema定义
import org.apache.calcite.sql.validate.SqlConformance;  // 导入SqlConformance接口，用于定义SQL语法符合性级别（如MySQL、Oracle、PostgreSQL等方言）

import org.checkerframework.checker.nullness.qual.Nullable;  // 导入Nullable注解，用于标记可能为null的返回值
import org.checkerframework.checker.nullness.qual.PolyNull;  // 导入PolyNull注解，用于标记多态的null性（根据参数决定是否为null）

import java.util.Properties;  // 导入Properties类，用于存储键值对形式的配置属性

/** Interface for reading connection properties within Calcite code. There is
 * a method for every property. At some point there will be similar config
 * classes for system and statement properties. */
// 类作用：CalciteConnectionConfig是一个接口，用于在Calcite代码中读取连接属性
// 它为每个连接属性都提供了一个getter方法，是Calcite连接配置的核心接口
// 该接口继承自Avatica的ConnectionConfig，扩展了Calcite特有的配置项
// 未来可能会有类似的配置类用于系统属性和语句属性
// 这个接口定义了Calcite连接的所有可配置参数，包括优化器行为、SQL方言、类型系统等
public interface CalciteConnectionConfig extends ConnectionConfig {  // 定义CalciteConnectionConfig接口，继承自ConnectionConfig
  /** Default configuration. */
  // 成员变量：DEFAULT是默认的CalciteConnectionConfig实现实例
  // 使用空的Properties对象初始化，提供所有配置项的默认值
  // 这是一个常量，可以在任何地方使用默认配置而不需要创建新实例
  // 默认配置通常使用Calcite的标准行为，不启用任何特殊优化或方言特性
  CalciteConnectionConfigImpl DEFAULT =  // 定义默认配置常量，类型为CalciteConnectionConfigImpl
      new CalciteConnectionConfigImpl(new Properties());  // 使用空的Properties创建默认配置实例
  /** Returns the value of
   * {@link CalciteConnectionProperty#APPROXIMATE_DISTINCT_COUNT}. */
  // 方法作用：返回是否启用近似DISTINCT COUNT计算
  // 返回true时，Calcite会使用近似算法（如HyperLogLog）来计算DISTINCT COUNT，以提高性能但可能牺牲精度
  // 返回false时，使用精确计算，保证结果准确但性能较差
  // 适用于数据量很大且可以接受一定误差的场景
  boolean approximateDistinctCount();  // 声明方法，返回是否启用近似DISTINCT COUNT
  /** Returns the value of
   * {@link CalciteConnectionProperty#APPROXIMATE_TOP_N}. */
  // 方法作用：返回是否启用近似TOP N查询
  // 返回true时，Calcite会使用近似算法来获取TOP N结果，提高查询性能
  // 返回false时，使用精确排序，保证结果的准确性
  // 适用于需要快速获取大致排序结果的场景
  boolean approximateTopN();  // 声明方法，返回是否启用近似TOP N查询
  /** Returns the value of
   * {@link CalciteConnectionProperty#APPROXIMATE_DECIMAL}. */
  // 方法作用：返回是否启用近似DECIMAL计算
  // 返回true时，对DECIMAL类型的计算可能使用近似值，提高性能
  // 返回false时，使用精确的DECIMAL计算，保证精度
  // 适用于对精度要求不高的数值计算场景
  boolean approximateDecimal();  // 声明方法，返回是否启用近似DECIMAL计算
  /** Returns the value of
   * {@link CalciteConnectionProperty#NULL_EQUAL_TO_EMPTY}. */
  // 方法作用：返回是否将NULL值视为等同于空字符串
  // 返回true时，NULL值在某些比较和排序操作中会被视为等同于空字符串
  // 返回false时，NULL值保持其标准SQL语义，与空字符串不同
  // 这个配置主要用于兼容某些数据库的特殊行为
  boolean nullEqualToEmpty();  // 声明方法，返回是否将NULL视为等同于空字符串
  /** Returns the value of
   * {@link CalciteConnectionProperty#AUTO_TEMP}. */
  // 方法作用：返回是否启用自动临时表功能
  // 返回true时，Calcite会自动创建和管理临时表，简化临时表的使用
  // 返回false时，需要显式创建和管理临时表
  // 这个配置可以简化某些需要临时表的查询操作
  boolean autoTemp();  // 声明方法，返回是否启用自动临时表功能
  /** Returns the value of
   * {@link CalciteConnectionProperty#MATERIALIZATIONS_ENABLED}. */
  // 方法作用：返回是否启用物化视图功能
  // 返回true时，Calcite会使用物化视图来加速查询，自动重写查询以使用物化视图
  // 返回false时，不使用物化视图，所有查询都从基础表计算
  // 物化视图是预先计算并存储的查询结果，可以显著提高复杂查询的性能
  boolean materializationsEnabled();  // 声明方法，返回是否启用物化视图功能
  /** Returns the value of
   * {@link CalciteConnectionProperty#CREATE_MATERIALIZATIONS}. */
  // 方法作用：返回是否允许创建物化视图
  // 返回true时，允许通过DDL语句创建物化视图
  // 返回false时，禁止创建新的物化视图
  // 这个配置与materializationsEnabled不同，它控制的是创建权限，而materializationsEnabled控制的是使用权限
  boolean createMaterializations();  // 声明方法，返回是否允许创建物化视图
  /** Returns the value of
   * {@link CalciteConnectionProperty#DEFAULT_NULL_COLLATION}. */
  // 方法作用：返回NULL值的默认排序规则
  // 返回NullCollation枚举值，定义NULL值在ORDER BY中的排序位置
  // NullCollation.HIGH表示NULL排在最后，NullCollation.LOW表示NULL排在最前
  // NullCollation.UNSPECIFIED表示使用数据库的默认行为
  // 这个配置影响包含NULL值的排序结果
  NullCollation defaultNullCollation();  // 声明方法，返回NULL值的默认排序规则
  /** Returns the value of {@link CalciteConnectionProperty#FUN},
   * or a default operator table if not set. If {@code defaultOperatorTable}
   * is not null, the result is never null. */
  // 方法作用：返回自定义的运算符表（函数库）
  // 这是一个泛型方法，允许获取任意类型的运算符表
  // 参数operatorTableClass：期望的运算符表类型，用于类型转换
  // 参数defaultOperatorTable：默认的运算符表，如果配置中没有设置则返回此默认值
  // 如果defaultOperatorTable不为null，则返回值永远不会为null
  // 运算符表定义了Calcite支持的所有函数和运算符，可以扩展自定义函数
  <T> @PolyNull T fun(Class<T> operatorTableClass,  // 声明泛型方法，返回自定义运算符表
      @PolyNull T defaultOperatorTable);  // 参数：默认运算符表，如果未设置则返回此值
  /** Returns the value of {@link CalciteConnectionProperty#MODEL}. */
  // 方法作用：返回模型文件的路径
  // 模型文件是JSON格式的文件，定义了Calcite的schema、表、视图等元数据
  // 返回null表示没有配置模型文件
  // 模型文件是Calcite连接的核心配置，定义了数据源的结构和映射关系
  @Nullable String model();  // 声明方法，返回模型文件路径，可能为null
  /** Returns the value of {@link CalciteConnectionProperty#LEX}. */
  // 方法作用：返回词法分析器配置
  // 返回Lex枚举值，定义SQL词法分析的行为
  // Lex包括标识符引用方式、大小写敏感规则等
  // 不同的Lex配置对应不同的SQL方言（如MySQL、Oracle、SQL Server等）
  Lex lex();  // 声明方法，返回词法分析器配置
  /** Returns the value of {@link CalciteConnectionProperty#QUOTING}. */
  // 方法作用：返回标识符引用方式
  // 返回Quoting枚举值，定义如何引用标识符（如表名、列名）
  // Quoting.DOUBLE_QUOTE：使用双引号引用（如"table_name"）
  // Quoting.BACK_TICK：使用反引号引用（如`table_name`）
  // Quoting.BRACKET：使用方括号引用（如[table_name]）
  // 这个配置影响SQL语句中标识符的书写方式
  Quoting quoting();  // 声明方法，返回标识符引用方式
  /** Returns the value of {@link CalciteConnectionProperty#UNQUOTED_CASING}. */
  // 方法作用：返回未引用标识符的大小写规则
  // 返回Casing枚举值，定义未加引号的标识符如何处理大小写
  // Casing.UNCHANGED：保持原样
  // Casing.TO_UPPER：转换为大写
  // Casing.TO_LOWER：转换为小写
  // 例如：如果设置为TO_UPPER，则table_name会被转换为TABLE_NAME
  Casing unquotedCasing();  // 声明方法，返回未引用标识符的大小写规则
  /** Returns the value of {@link CalciteConnectionProperty#QUOTED_CASING}. */
  // 方法作用：返回已引用标识符的大小写规则
  // 返回Casing枚举值，定义加引号的标识符如何处理大小写
  // Casing.UNCHANGED：保持原样（通常情况）
  // Casing.TO_UPPER：转换为大写
  // Casing.TO_LOWER：转换为小写
  // 例如：如果设置为TO_UPPER，则"table_name"会被转换为"TABLE_NAME"
  Casing quotedCasing();  // 声明方法，返回已引用标识符的大小写规则
  /** Returns the value of {@link CalciteConnectionProperty#CASE_SENSITIVE}. */
  // 方法作用：返回标识符是否区分大小写
  // 返回true时，标识符区分大小写，Table和table被视为不同的标识符
  // 返回false时，标识符不区分大小写，Table和table被视为相同的标识符
  // 这个配置影响SQL语句中的标识符匹配行为
  boolean caseSensitive();  // 声明方法，返回标识符是否区分大小写
  /** Returns the value of {@link CalciteConnectionProperty#PARSER_FACTORY},
   * or a default parser if not set. If {@code defaultParserFactory}
   * is not null, the result is never null. */
  // 方法作用：返回自定义的SQL解析器工厂
  // 这是一个泛型方法，允许获取任意类型的解析器工厂
  // 参数parserFactoryClass：期望的解析器工厂类型，用于类型转换
  // 参数defaultParserFactory：默认的解析器工厂，如果配置中没有设置则返回此默认值
  // 如果defaultParserFactory不为null，则返回值永远不会为null
  // 解析器工厂负责创建SQL解析器，可以扩展自定义的SQL语法支持
  <T> @PolyNull T parserFactory(Class<T> parserFactoryClass,  // 声明泛型方法，返回自定义解析器工厂
      @PolyNull T defaultParserFactory);  // 参数：默认解析器工厂，如果未设置则返回此值
  /** Returns the value of {@link CalciteConnectionProperty#SCHEMA_FACTORY},
   * or a default schema factory if not set. If {@code defaultSchemaFactory}
   * is not null, the result is never null. */
  // 方法作用：返回自定义的Schema工厂
  // 这是一个泛型方法，允许获取任意类型的Schema工厂
  // 参数schemaFactoryClass：期望的Schema工厂类型，用于类型转换
  // 参数defaultSchemaFactory：默认的Schema工厂，如果配置中没有设置则返回此默认值
  // 如果defaultSchemaFactory不为null，则返回值永远不会为null
  // Schema工厂负责创建Schema实例，Schema定义了表、视图等元数据的集合
  <T> @PolyNull T schemaFactory(Class<T> schemaFactoryClass,  // 声明泛型方法，返回自定义Schema工厂
      @PolyNull T defaultSchemaFactory);  // 参数：默认Schema工厂，如果未设置则返回此值
  /** Returns the value of {@link CalciteConnectionProperty#SCHEMA_TYPE}. */
  // 方法作用：返回Schema的类型
  // 返回JsonSchema.Type枚举值，定义Schema的存储和访问方式
  // JsonSchema.Type.MAP：基于Map的Schema
  // JsonSchema.Type.JDBC：基于JDBC的Schema
  // JsonSchema.Type.CUSTOM：自定义Schema
  // 这个配置决定了Schema的实现方式和数据源类型
  JsonSchema.Type schemaType();  // 声明方法，返回Schema的类型
  /** Returns the value of {@link CalciteConnectionProperty#SPARK}. */
  // 方法作用：返回是否启用Spark兼容模式
  // 返回true时，Calcite会使用与Apache Spark兼容的SQL语法和行为
  // 返回false时，使用Calcite的标准SQL语法
  // 这个配置用于需要与Spark SQL保持兼容的场景
  boolean spark();  // 声明方法，返回是否启用Spark兼容模式
  /** Returns the value of
   * {@link CalciteConnectionProperty#FORCE_DECORRELATE}. */
  // 方法作用：返回是否强制去相关化（Decorrelate）
  // 返回true时，Calcite会强制对子查询进行去相关化处理，将相关子查询转换为连接
  // 返回false时，根据优化器的判断决定是否去相关化
  // 去相关化可以优化子查询的性能，但可能会改变查询的语义
  boolean forceDecorrelate();  // 声明方法，返回是否强制去相关化
  /** Returns the value of {@link CalciteConnectionProperty#TYPE_SYSTEM},
   * or a default type system if not set. If {@code defaultTypeSystem}
   * is not null, the result is never null. */
  // 方法作用：返回自定义的类型系统
  // 这是一个泛型方法，允许获取任意类型的类型系统
  // 参数typeSystemClass：期望的类型系统类型，用于类型转换
  // 参数defaultTypeSystem：默认的类型系统，如果配置中没有设置则返回此默认值
  // 如果defaultTypeSystem不为null，则返回值永远不会为null
  // 类型系统定义了Calcite支持的数据类型、类型转换规则、类型推断规则等
  <T> @PolyNull T typeSystem(Class<T> typeSystemClass,  // 声明泛型方法，返回自定义类型系统
      @PolyNull T defaultTypeSystem);  // 参数：默认类型系统，如果未设置则返回此值
  /** Returns the value of {@link CalciteConnectionProperty#CONFORMANCE}. */
  // 方法作用：返回SQL符合性级别
  // 返回SqlConformance接口的实现，定义Calcite遵循的SQL方言标准
  // SqlConformance包括SQL:2011、SQL:2003等标准，以及MySQL、Oracle、PostgreSQL等方言
  // 这个配置决定了Calcite支持哪些SQL语法特性，以及如何解释SQL语句
  SqlConformance conformance();  // 声明方法，返回SQL符合性级别
  /** Returns the value of {@link CalciteConnectionProperty#TIME_ZONE}. */
  // 方法作用：返回时区配置
  // 返回时区字符串，如"UTC"、"GMT+8"、"Asia/Shanghai"等
  // 这个配置影响时间戳和日期时间类型的解释和显示
  // @Override表示重写了父接口ConnectionConfig的方法
  @Override String timeZone();  // 声明方法，返回时区配置，重写父接口方法
  /** Returns the value of {@link CalciteConnectionProperty#LOCALE}. */
  // 方法作用：返回区域设置（Locale）
  // 返回区域设置字符串，如"en_US"、"zh_CN"等
  // 这个配置影响错误消息、日期格式、数字格式等的本地化显示
  // Locale决定了如何格式化和解释区域相关的数据
  String locale();  // 声明方法，返回区域设置
  /** Returns the value of {@link CalciteConnectionProperty#TYPE_COERCION}. */
  // 方法作用：返回是否启用类型强制转换（Type Coercion）
  // 返回true时，Calcite会自动进行类型转换，如将INT转换为BIGINT
  // 返回false时，类型必须严格匹配，不进行自动转换
  // 类型强制转换可以简化SQL语句，但可能导致意外的类型转换
  boolean typeCoercion();  // 声明方法，返回是否启用类型强制转换
  /** Returns the value of
   * {@link CalciteConnectionProperty#LENIENT_OPERATOR_LOOKUP}. */
  // 方法作用：返回是否启用宽松的运算符查找
  // 返回true时，运算符查找更加宽松，允许模糊匹配
  // 返回false时，运算符查找严格，必须精确匹配
  // 宽松查找可以提高SQL的兼容性，但可能导致意外的运算符选择
  boolean lenientOperatorLookup();  // 声明方法，返回是否启用宽松的运算符查找
  /** Returns the value of {@link CalciteConnectionProperty#TOPDOWN_OPT}. */
  // 方法作用：返回是否启用自顶向下的优化策略
  // 返回true时，优化器使用自顶向下的优化策略
  // 返回false时，优化器使用自底向上的优化策略（默认）
  // 自顶向下优化可以更快地找到较好的执行计划，但可能错过最优计划
  boolean topDownOpt();  // 声明方法，返回是否启用自顶向下的优化策略

  /** Returns the value of {@link CalciteConnectionProperty#META_TABLE_FACTORY},
   * or a default meta table factory if not set. If
   * {@code defaultMetaTableFactory} is not null, the result is never null. */
  // 方法作用：返回自定义的元表工厂
  // 这是一个泛型方法，允许获取任意类型的元表工厂
  // 参数metaTableFactoryClass：期望的元表工厂类型，用于类型转换
  // 参数defaultMetaTableFactory：默认的元表工厂，如果配置中没有设置则返回此默认值
  // 如果defaultMetaTableFactory不为null，则返回值永远不会为null
  // 元表工厂负责创建元表（如系统表、信息模式表等），元表提供数据库的元数据信息
  <T> @PolyNull T metaTableFactory(Class<T> metaTableFactoryClass,  // 声明泛型方法，返回自定义元表工厂
      @PolyNull T defaultMetaTableFactory);  // 参数：默认元表工厂，如果未设置则返回此值

  /** Returns the value of {@link CalciteConnectionProperty#META_COLUMN_FACTORY},
   * or a default meta column factory if not set. If
   * {@code defaultMetaColumnFactory} is not null, the result is never null. */
  // 方法作用：返回自定义的元列工厂
  // 这是一个泛型方法，允许获取任意类型的元列工厂
  // 参数metaColumnFactoryClass：期望的元列工厂类型，用于类型转换
  // 参数defaultMetaColumnFactory：默认的元列工厂，如果配置中没有设置则返回此默认值
  // 如果defaultMetaColumnFactory不为null，则返回值永远不会为null
  // 元列工厂负责创建元列（元表的列），元列提供表、列等对象的元数据信息
  <T> @PolyNull T metaColumnFactory(Class<T> metaColumnFactoryClass,  // 声明泛型方法，返回自定义元列工厂
      @PolyNull T defaultMetaColumnFactory);  // 参数：默认元列工厂，如果未设置则返回此值
}  // 接口定义结束
