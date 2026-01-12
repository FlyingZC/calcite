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
// Apache许可证声明，表明该代码遵循Apache 2.0许可证
package org.apache.calcite.test; // 定义包名，该类属于org.apache.calcite.test包

import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性枚举，用于配置Calcite连接的各种属性
import org.apache.calcite.config.Lex; // 导入词法分析器配置，定义SQL词法分析规则
import org.apache.calcite.sql.fun.SqlLibrary; // 导入SQL函数库，定义可用的SQL函数集合
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SQL兼容性枚举，定义不同数据库的SQL兼容级别

import net.hydromatic.quidem.Quidem; // 导入Quidem测试框架的主类，用于执行.iq测试文件

import java.sql.Connection; // 导入JDBC连接接口，用于数据库连接
import java.util.Collection; // 导入集合接口，用于存储测试文件路径集合

import static org.apache.calcite.util.Util.discard; // 导入工具类静态方法，用于弃用变量避免编译器警告

/**
 * Test that runs every Quidem file in the "core" module as a test.
 * // 该类用于运行core模块中的所有Quidem测试文件作为单元测试
 * // Quidem是一种SQL测试框架，通过.iq文件定义SQL测试用例
 * // 继承自QuidemTest基类，实现了测试执行的核心逻辑
 */
class CoreQuidemTest extends QuidemTest { // CoreQuidemTest类定义，继承QuidemTest基类，用于执行core模块的Quidem测试
  /** Runs a test from the command line.
   * // 该方法用于从命令行运行测试
   * // 允许用户直接执行特定的.iq测试文件
   * // 提供了命令行接口，方便独立运行测试而不需要通过JUnit
   *
   * <p>For example:
   * // 使用示例：通过命令行执行特定测试文件
   *
   * <blockquote>
   *   <code>java CoreQuidemTest sql/dummy.iq</code>
   *   // 示例命令：执行sql/dummy.iq测试文件
   * </blockquote> */
  public static void main(String[] args) throws Exception { // main方法，程序入口，接收命令行参数（测试文件路径）
    for (String arg : args) { // 遍历命令行参数，每个参数代表一个测试文件路径
      new CoreQuidemTest().test(arg); // 为每个测试文件创建CoreQuidemTest实例并执行测试
    }
  }

  /** For {@link QuidemTest#test(String)} parameters.
   * // 该方法用于获取测试文件路径集合
   * // 为QuidemTest.test(String)方法提供参数
   * // 返回所有需要执行的.iq测试文件的路径
   */
  @Override public Collection<String> getPath() { // 重写getPath方法，返回测试文件路径集合
    // Start with a test file we know exists, then find the directory and list
    // its files.
    // 从一个已知存在的测试文件开始，然后找到该目录并列出所有文件
    final String first = "sql/agg.iq"; // 定义第一个测试文件路径，作为查找目录的起点
    return data(first); // 调用父类data方法，根据第一个文件找到目录并返回所有.iq文件路径
  }

  @Override protected Quidem.ConnectionFactory createConnectionFactory() { // 重写createConnectionFactory方法，创建Quidem连接工厂
    return new QuidemConnectionFactory() { // 返回匿名内部类实例，实现QuidemConnectionFactory接口
      @Override public Connection connect(String name, boolean reference) throws Exception { // 重写connect方法，根据名称创建数据库连接
        switch (name) { // 根据连接名称创建不同配置的数据库连接
        case "blank": // 空白连接配置
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteAssert.SchemaSpec.BLANK) // 使用空白schema规范
              .connect(); // 创建并返回连接
        case "scott": // Scott连接配置（标准测试schema）
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.FUN, SqlLibrary.CALCITE.fun) // 设置函数库为Calcite标准函数库
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置（包含EMP、DEPT等经典测试表）
              .connect(); // 创建并返回连接
        case "scott-spark": // Scott连接配置，使用Spark类型系统
          discard(CustomTypeSystems.SPARK_TYPE_SYSTEM); // 弃用Spark类型系统引用，避免编译器警告
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.FUN, SqlLibrary.CALCITE.fun) // 设置函数库为Calcite标准函数库
              .with(CalciteConnectionProperty.TYPE_SYSTEM, // 设置类型系统属性
                  CustomTypeSystems.class.getName() + "#SPARK_TYPE_SYSTEM") // 使用Spark类型系统
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-checked-rounding-half-up": // Scott连接配置，使用检查算术和HALF_UP舍入模式
          discard(CustomTypeSystems.ROUNDING_MODE_HALF_UP); // 弃用舍入模式引用，避免编译器警告
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              // Use bigquery conformance, which forces checked arithmetic
              // 使用BigQuery兼容性，强制进行检查算术运算
              .with(CalciteConnectionProperty.CONFORMANCE, SqlConformanceEnum.BIG_QUERY) // 设置SQL兼容性为BigQuery
              .with(CalciteConnectionProperty.FUN, SqlLibrary.CALCITE.fun) // 设置函数库为Calcite标准函数库
              .with(CalciteConnectionProperty.TYPE_SYSTEM, // 设置类型系统属性
                  CustomTypeSystems.class.getName() + "#ROUNDING_MODE_HALF_UP") // 使用HALF_UP舍入模式的类型系统
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-negative-scale": // Scott连接配置，支持负数精度
          discard(CustomTypeSystems.NEGATIVE_SCALE); // 弃用负数精度引用，避免编译器警告
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.FUN, SqlLibrary.CALCITE.fun) // 设置函数库为Calcite标准函数库
              .with(CalciteConnectionProperty.TYPE_SYSTEM, // 设置类型系统属性
                  CustomTypeSystems.class.getName() + "#NEGATIVE_SCALE") // 使用支持负数精度的类型系统
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-negative-scale-rounding-half-up": // Scott连接配置，支持负数精度和HALF_UP舍入
          discard(CustomTypeSystems.NEGATIVE_SCALE_ROUNDING_MODE_HALF_UP); // 弃用负数精度和舍入模式引用，避免编译器警告
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.FUN, SqlLibrary.CALCITE.fun) // 设置函数库为Calcite标准函数库
              .with(CalciteConnectionProperty.TYPE_SYSTEM, // 设置类型系统属性
                  CustomTypeSystems.class.getName() // 使用CustomTypeSystems类
                      + "#NEGATIVE_SCALE_ROUNDING_MODE_HALF_UP") // 使用支持负数精度和HALF_UP舍入的类型系统
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-lenient": // Scott连接配置，使用LENIENT（宽松）兼容性
          // Same as "scott", but uses LENIENT conformance.
          // 与"scott"相同，但使用LENIENT兼容性
          // TODO: add a way to change conformance without defining a new
          // connection
          // TODO：添加一种方法来改变兼容性而不需要定义新的连接
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL兼容性属性
                  SqlConformanceEnum.LENIENT) // 使用LENIENT兼容性（宽松模式，允许更多SQL语法变体）
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-babel": // Scott连接配置，使用BABEL兼容性
          // Same as "scott", but uses BABEL conformance.
          // 与"scott"相同，但使用BABEL兼容性
          // connection
          // 连接
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL兼容性属性
                  SqlConformanceEnum.BABEL) // 使用BABEL兼容性（支持Babel SQL扩展）
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-mysql": // Scott连接配置，使用MySQL兼容性
          // Same as "scott", but uses MySQL conformance.
          // 与"scott"相同，但使用MySQL兼容性
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL兼容性属性
                  SqlConformanceEnum.MYSQL_5) // 使用MySQL 5兼容性
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-oracle": // Scott连接配置，使用Oracle兼容性
          // Same as "scott", but uses Oracle conformance.
          // 与"scott"相同，但使用Oracle兼容性
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL兼容性属性
                  SqlConformanceEnum.ORACLE_10) // 使用Oracle 10g兼容性
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "scott-mssql": // Scott连接配置，使用SQL Server兼容性
          // Same as "scott", but uses SQL_SERVER_2008 conformance.
          // 与"scott"相同，但使用SQL Server 2008兼容性
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL兼容性属性
                  SqlConformanceEnum.SQL_SERVER_2008) // 使用SQL Server 2008兼容性
              .with(CalciteAssert.Config.SCOTT) // 使用Scott配置
              .connect(); // 创建并返回连接
        case "steelwheels": // SteelWheels连接配置（商业智能测试schema）
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  ExtensionDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用ExtensionDdlExecutor类的PARSER_FACTORY方法作为解析器工厂
              .with(CalciteConnectionProperty.FUN, SqlLibrary.CALCITE.fun) // 设置函数库为Calcite标准函数库
              .with(CalciteAssert.SchemaSpec.STEELWHEELS) // 使用SteelWheels schema规范（包含客户、产品、销售等商业数据表）
              .with(Lex.BIG_QUERY) // 使用BigQuery词法分析器
              .connect(); // 创建并返回连接
        default: // 默认连接配置
          return super.connect(name, reference); // 调用父类的connect方法处理未定义的连接名称
        }
      }
    };
  }

  /** Override settings for "sql/misc.iq".
 * // 该方法用于覆盖sql/misc.iq测试文件的设置
 * // 根据不同的数据库类型调整测试行为
 * // 处理特定数据库的兼容性问题
 */
  public void testSqlMisc(String path) throws Exception { // testSqlMisc方法，用于执行misc.iq测试文件
    switch (CalciteAssert.DB) { // 根据当前数据库类型进行不同处理
    case ORACLE: // 如果使用Oracle数据库
      // There are formatting differences (e.g. "4.000" vs "4") when using
      // Oracle as the JDBC data source.
      // 使用Oracle作为JDBC数据源时存在格式差异（例如"4.000" vs "4"）
      return; // 跳过该测试，避免因格式差异导致的测试失败
    }
    checkRun(path); // 调用checkRun方法执行测试
  }
}
