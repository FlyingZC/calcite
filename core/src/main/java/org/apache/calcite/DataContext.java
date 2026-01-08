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
package org.apache.calcite; // 声明包名，表示该类属于org.apache.calcite包，Calcite是Apache基金会的动态数据管理框架

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入JavaTypeFactory，用于创建Java类型系统中的类型对象
import org.apache.calcite.linq4j.QueryProvider; // 导入QueryProvider，提供LINQ风格的查询执行能力
import org.apache.calcite.linq4j.tree.Expressions; // 导入Expressions，用于构建表达式树
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入ParameterExpression，表示参数表达式
import org.apache.calcite.rel.type.TimeFrameSet; // 导入TimeFrameSet，表示时间帧集合，用于时间相关的函数
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus，表示数据库模式的增强版本，支持动态添加表和函数
import org.apache.calcite.sql.advise.SqlAdvisor; // 导入SqlAdvisor，用于提供SQL语句的自动完成和建议功能

import com.google.common.base.CaseFormat; // 导入Google Guava的CaseFormat，用于在不同命名格式之间转换

import org.checkerframework.checker.nullness.qual.Nullable; // 导入Nullable注解，标记可能为null的返回值

import java.io.InputStream; // 导入InputStream，用于标准输入流
import java.io.OutputStream; // 导入OutputStream，用于标准输出流
import java.lang.reflect.Modifier; // 导入Modifier，用于访问类的修饰符信息
import java.util.Locale; // 导入Locale，表示地区设置，影响日期时间函数的显示格式
import java.util.TimeZone; // 导入TimeZone，表示时区信息
import java.util.concurrent.atomic.AtomicBoolean; // 导入AtomicBoolean，用于原子布尔操作，支持并发安全

/**
 * Runtime context allowing access to the tables in a database.
 * 运行时上下文接口，允许访问数据库中的表和元数据
 * 
 * DataContext是Calcite中的核心接口，它提供了在整个查询执行过程中访问各种上下文信息的能力
 * 包括：数据库模式、类型工厂、查询提供者、时间戳、用户信息、时区、地区设置等
 * 
 * 主要作用：
 * 1. 提供对数据库模式的访问（getRootSchema）
 * 2. 提供类型系统支持（getTypeFactory）
 * 3. 提供查询执行能力（getQueryProvider）
 * 4. 提供各种上下文变量的访问（get方法）
 * 
 * 使用场景：
 * - 在查询执行过程中，需要访问表结构信息
 * - 在表达式求值时，需要获取当前时间、用户等上下文信息
 * - 在实现自定义函数时，需要访问数据库模式
 * 
 * @see DataContexts // 参考DataContexts工具类，提供了创建DataContext的工厂方法
 */
public interface DataContext { // 定义DataContext接口，作为数据上下文的抽象
  ParameterExpression ROOT = // 定义ROOT参数表达式，用于在LINQ查询中表示DataContext本身
      Expressions.parameter(Modifier.FINAL, DataContext.class, "root"); // 创建一个final修饰的参数，类型为DataContext，参数名为"root"，这个表达式在代码生成时会被使用

  /**
   * Returns a sub-schema with a given name, or null.
   * 返回根模式（root schema），如果不存在则返回null
   * 
   * SchemaPlus是Calcite中表示数据库模式的接口，它包含了所有的表、函数、视图等元数据
   * 根模式是整个数据库模式的入口点，通过它可以访问所有的子模式
   * 
   * @return 根模式对象，如果不存在则返回null
   */
  @Nullable SchemaPlus getRootSchema(); // 声明获取根模式的方法，返回值可能为null

  /**
   * Returns the type factory.
   * 返回Java类型工厂
   * 
   * JavaTypeFactory是Calcite类型系统的核心组件，负责创建和管理SQL类型到Java类型的映射
   * 它用于：
   * - 将SQL类型转换为Java类型
   - 创建类型对象（如整数类型、字符串类型等）
   - 处理类型推断和类型转换
   * 
   * @return Java类型工厂实例
   */
  JavaTypeFactory getTypeFactory(); // 声明获取类型工厂的方法

  /**
   * Returns the query provider.
   * 返回查询提供者
   * 
   * QueryProvider是LINQ4J查询执行的核心接口，它提供了执行LINQ查询的能力
   * 它负责：
   * - 创建可查询对象（Queryable）
   * - 执行查询计划
   * - 管理查询的生命周期
   * 
   * @return 查询提供者实例
   */
  QueryProvider getQueryProvider(); // 声明获取查询提供者的方法

  /**
   * Returns a context variable.
   * 返回指定名称的上下文变量
   * 
   * 这个方法是访问上下文变量的通用入口，通过变量名称获取对应的值
   * 支持的变量包括时间戳、用户信息、时区、地区设置等
   * 
   * <p>Supported variables include: "sparkContext", "currentTimestamp",
   * "localTimestamp".
   * 支持的变量包括：Spark上下文、UTC时间戳、本地时间戳等
   * 
   * 完整的变量列表在Variable枚举中定义，包括：
   * - 时间相关：utcTimestamp, currentTimestamp, localTimestamp, sysTimestamp
   * - 引擎相关：sparkContext
   * - 执行控制：cancelFlag, timeout
   * - SQL支持：sqlAdvisor
   * - IO流：stdin, stdout, stderr
   * - 环境设置：locale, timeZone, timeFrameSet
   * - 用户信息：user, systemUser
   * 
   * @param name Name of variable // 变量名称，如"currentTimestamp"、"user"等
   * @return 变量的值，如果变量不存在则返回null
   */
  @Nullable Object get(String name); // 声明获取上下文变量的方法，参数为变量名称，返回值可能为null

  /** Variable that may be asked for in call to {@link DataContext#get}.
   * 可能通过DataContext.get方法请求的变量枚举
   * 
   * 这个枚举定义了所有可以在DataContext中访问的标准变量
   * 每个变量都有一个camelCase名称和一个类型
   * 变量分为以下几类：
   * 
   * 1. 时间相关变量：
   *    - UTC_TIMESTAMP: UTC时间戳
   *    - CURRENT_TIMESTAMP: 当前语句开始执行的UTC时间
   *    - LOCAL_TIMESTAMP: 当前语句开始执行的本地时间
   *    - SYS_TIMESTAMP: 数据库服务器时区的系统时间
   * 
   * 2. 引擎相关变量：
   *    - SPARK_CONTEXT: Spark引擎上下文
   * 
   * 3. 执行控制变量：
   *    - CANCEL_FLAG: 取消标志，用于中断查询执行
   *    - TIMEOUT: 查询超时时间（毫秒）
   * 
   * 4. SQL支持变量：
   *    - SQL_ADVISOR: SQL建议器，提供自动完成功能
   * 
   * 5. IO流变量：
   *    - STDIN: 标准输入流
   *    - STDOUT: 标准输出流
   *    - STDERR: 标准错误流
   * 
   * 6. 环境设置变量：
   *    - LOCALE: 地区设置，影响日期时间函数的显示
   *    - TIME_ZONE: 时区设置
   *    - TIME_FRAME_SET: 时间帧集合，用于FLOOR和EXTRACT函数
   * 
   * 7. 用户信息变量：
   *    - USER: 查询用户
   *    - SYSTEM_USER: 系统用户
   */
  enum Variable { // 定义变量枚举，列出所有支持的上下文变量
    UTC_TIMESTAMP("utcTimestamp", Long.class), // UTC时间戳，表示协调世界时的时间值，类型为Long

    /** The time at which the current statement started executing. In
     * milliseconds after 1970-01-01 00:00:00, UTC. Required.
     * 当前语句开始执行的时间（UTC时区）
     * 以1970-01-01 00:00:00以来的毫秒数表示，UTC时区
     * 这个字段是必需的，表示查询执行的起始时间点
     */
    CURRENT_TIMESTAMP("currentTimestamp", Long.class), // 当前语句开始执行的UTC时间戳，类型为Long

    /** The time at which the current statement started executing. In
     * milliseconds after 1970-01-01 00:00:00, in the time zone of the current
     * statement. Required.
     * 当前语句开始执行的时间（当前语句的时区）
     * 以1970-01-01 00:00:00以来的毫秒数表示，使用当前语句指定的时区
     * 这个字段是必需的，用于本地化时间相关的计算
     */
    LOCAL_TIMESTAMP("localTimestamp", Long.class), // 当前语句开始执行的本地时间戳，类型为Long

    /** The time in the operating system time zone of the database server. In
     * milliseconds after 1970-01-01 00:00:00, in the time zone of the database
     * server. Required.
     * 数据库服务器操作系统时区的时间
     * 以1970-01-01 00:00:00以来的毫秒数表示，使用数据库服务器的时区
     * 这个字段是必需的，用于获取服务器端的系统时间
     */
    SYS_TIMESTAMP("sysTimestamp", Long.class), // 数据库服务器时区的时间戳，类型为Long

    /** The Spark engine. Available if Spark is on the class path.
     * Spark引擎上下文
     * 只有当Spark在类路径上时才可用
     * 这个变量提供了对Spark API的访问，用于在Spark上执行Calcite查询
     */
    SPARK_CONTEXT("sparkContext", Object.class), // Spark引擎上下文，类型为Object

    /** A mutable flag that indicates whether user has requested that the
     * current statement be canceled. Cancellation may not be immediate, but
     * implementations of relational operators should check the flag fairly
     * frequently and cease execution (e.g. by returning end of data).
     * 一个可变标志，指示用户是否请求取消当前语句
     * 取消可能不会立即生效，但关系运算符的实现应该频繁检查该标志
     * 并在检测到取消请求时停止执行（例如，通过返回数据结束信号）
     * 
     * 使用场景：
     * - 用户在查询执行过程中点击取消按钮
     * - 查询超时后需要中断执行
     * - 客户端断开连接后需要清理资源
     */
    CANCEL_FLAG("cancelFlag", AtomicBoolean.class), // 取消标志，类型为AtomicBoolean，支持并发安全的读写操作

    /** Query timeout in milliseconds.
     * When no timeout is set, the value is 0 or not present.
     * 查询超时时间（毫秒）
     * 如果没有设置超时，值为0或不存在
     * 
     * 使用场景：
     * - 防止长时间运行的查询占用过多资源
     * - 在分布式系统中设置合理的超时时间
     * - 根据查询复杂度动态调整超时时间
     */
    TIMEOUT("timeout", Long.class), // 查询超时时间（毫秒），类型为Long

    /** Advisor that suggests completion hints for SQL statements.
     * SQL语句建议器，提供自动完成和提示功能
     * 
     * 功能包括：
     * - SQL语法自动完成
     * - 提供列名、表名建议
     * - 检测SQL语法错误
     * - 提供查询优化建议
     */
    SQL_ADVISOR("sqlAdvisor", SqlAdvisor.class), // SQL建议器，类型为SqlAdvisor

    /** Writer to the standard error (stderr).
     * 标准错误流（stderr）的写入器
     * 用于输出错误信息和调试信息
     */
    STDERR("stderr", OutputStream.class), // 标准错误流，类型为OutputStream

    /** Reader on the standard input (stdin).
     * 标准输入流（stdin）的读取器
     * 用于从用户输入读取数据
     */
    STDIN("stdin", InputStream.class), // 标准输入流，类型为InputStream

    /** Writer to the standard output (stdout).
     * 标准输出流（stdout）的写入器
     * 用于输出查询结果和一般信息
     */
    STDOUT("stdout", OutputStream.class), // 标准输出流，类型为OutputStream

    /** Locale in which the current statement is executing.
     * Affects the behavior of functions such as {@code DAYNAME} and
     * {@code MONTHNAME}. Required; defaults to the root locale if the
     * connection does not specify a locale.
     * 当前语句执行时的地区设置（Locale）
     * 影响DAYNAME、MONTHNAME等函数的行为，决定日期和月份名称的显示语言
     * 
     * 这个字段是必需的，如果连接没有指定地区设置，则默认为根区域设置
     * 
     * Locale的作用：
     * - 确定字符串比较和排序规则
     * - 影响日期时间格式化
     * - 决定数字和货币的显示格式
     * - 影响大小写转换规则
     */
    LOCALE("locale", Locale.class), // 地区设置，类型为Locale

    /** Time zone in which the current statement is executing. Required;
     * defaults to the time zone of the JVM if the connection does not specify a
     * time zone.
     * 当前语句执行时的时区
     * 
     * 这个字段是必需的，如果连接没有指定时区，则默认为JVM的时区
     * 
     * TimeZone的作用：
     * - 影响时间戳的转换
     * - 决定CURRENT_TIMESTAMP和LOCAL_TIMESTAMP的值
     * - 影响日期时间函数的计算结果
     * - 处理夏令时等时区特殊规则
     */
    TIME_ZONE("timeZone", TimeZone.class), // 时区设置，类型为TimeZone

    /** Set of built-in and custom time frames for use in functions such as
     * {@code FLOOR} and {@code EXTRACT}. Required; defaults to
     * {@link org.apache.calcite.rel.type.TimeFrames#CORE}.
     * 内置和自定义时间帧的集合，用于FLOOR和EXTRACT等函数
     * 
     * 时间帧（Time Frame）表示时间单位，如年、月、日、小时等
     * 这个字段是必需的，默认为TimeFrames.CORE（核心时间帧集合）
     * 
     * 使用场景：
     * - FLOOR(timestamp TO MONTH): 将时间戳向下取整到月
     * - EXTRACT(YEAR FROM timestamp): 从时间戳中提取年份
     * - 支持自定义时间单位，如季度、周等
     */
    TIME_FRAME_SET("timeFrameSet", TimeFrameSet.class), // 时间帧集合，类型为TimeFrameSet

    /** The query user.
     *
     * <p>Default value is "sa".
     * 查询用户，表示执行当前SQL语句的用户身份
     * 
     * 默认值为"sa"（system administrator的缩写）
     * 
     * 使用场景：
     * - 权限控制：根据用户身份决定是否允许访问某些表
     * - 审计日志：记录谁执行了哪些查询
     * - 行级安全：根据用户过滤返回的数据
     * - 自定义函数：在函数中访问用户信息
     */
    USER("user", String.class), // 查询用户，类型为String

    /** The system user.
     *
     * <p>Default value is "user.name" from
     * {@link System#getProperty(String)}.
     * 系统用户，表示运行数据库服务器的操作系统用户
     * 
     * 默认值从System.getProperty("user.name")获取
     * 
     * 使用场景：
     * - 区分数据库用户和操作系统用户
     * - 审计和日志记录
     * - 资源管理和配额控制
     */
    SYSTEM_USER("systemUser", String.class); // 系统用户，类型为String

    public final String camelName; // 变量的驼峰命名形式，如"currentTimestamp"，用于在get方法中查找变量
    public final Class clazz; // 变量的类型，如Long.class、String.class，用于类型检查和转换

    Variable(String camelName, Class clazz) { // 构造方法，初始化变量枚举
      this.camelName = camelName; // 设置变量的驼峰命名
      this.clazz = clazz; // 设置变量的类型
      assert camelName.equals( // 断言：确保驼峰命名与枚举名称的转换一致
          CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name())); // 将枚举名称（如CURRENT_TIMESTAMP）从大写下划线格式转换为驼峰格式（如currentTimestamp），并与传入的camelName比较
    }

    /** Returns the value of this variable in a given data context.
     * 返回该变量在给定数据上下文中的值
     * 
     * 这是一个泛型方法，返回指定类型的值
     * 方法内部会进行类型转换，确保返回值的类型正确
     * 
     * @param <T> 返回值的类型
     * @param dataContext 数据上下文对象
     * @return 变量的值，类型为T
     * @throws ClassCastException 如果值的类型与期望类型不兼容
     */
    public <T> T get(DataContext dataContext) { // 泛型方法，获取变量值
      //noinspection unchecked // 忽略未检查的类型转换警告，因为已经通过clazz.cast进行了类型检查
      return (T) clazz.cast(dataContext.get(camelName)); // 从dataContext中获取变量值，并转换为指定的类型T
    }
  }
}
