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
// Apache许可证声明，说明该代码的版权归属和使用条款
package org.apache.calcite.test; // 包声明，该类位于org.apache.calcite.test包下，是Calcite项目的测试包

import org.apache.calcite.config.CalciteConnectionProperty; // 导入Calcite连接属性类，用于配置Calcite连接的各种属性
import org.apache.calcite.config.Lex; // 导入词法分析策略枚举，控制SQL解析时的词法规则（如引号、标识符大小写等）
import org.apache.calcite.jdbc.CalciteConnection; // 导入Calcite连接接口，扩展了标准JDBC连接，提供Calcite特有功能
import org.apache.calcite.materialize.MaterializationService; // 导入物化服务类，用于管理物化视图的创建和维护
import org.apache.calcite.plan.Contexts; // 导入上下文工具类，用于创建和操作规划器上下文
import org.apache.calcite.schema.SchemaPlus; // 导入SchemaPlus接口，表示可扩展的数据库模式，支持动态添加表和函数
import org.apache.calcite.sql.SqlNode; // 导入SqlNode接口，表示SQL语法树的抽象节点，所有SQL语句都解析为SqlNode树
import org.apache.calcite.sql.SqlWriter; // 导入SqlWriter接口，用于将SqlNode序列化为SQL字符串
import org.apache.calcite.sql.dialect.BigQuerySqlDialect; // 导入BigQuery SQL方言类，用于生成BigQuery兼容的SQL语句
import org.apache.calcite.sql.parser.SqlParser; // 导入SQL解析器类，负责将SQL文本解析为SqlNode语法树
import org.apache.calcite.sql.parser.babel.SqlBabelParserImpl; // 导入Babel SQL解析器实现类，支持多种SQL方言的解析
import org.apache.calcite.sql.pretty.SqlPrettyWriter; // 导入SQL美化写入器，用于格式化输出SQL语句
import org.apache.calcite.sql.type.SqlTypeName; // 导入SQL类型名枚举，定义所有标准SQL数据类型
import org.apache.calcite.sql.validate.SqlConformanceEnum; // 导入SQL合规性枚举，定义不同SQL方言的语法兼容级别
import org.apache.calcite.tools.Frameworks; // 导入框架工具类，用于构建和配置Calcite规划器
import org.apache.calcite.tools.Planner; // 导入规划器接口，负责SQL语句的解析、验证和优化

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类，提供线程安全的不可变列表实现
import com.google.common.collect.ImmutableSet; // 导入Google Guava的不可变集合类，提供线程安全的不可变集合实现

import net.hydromatic.quidem.AbstractCommand; // 导入Quidem抽象命令类，Quidem是SQL测试框架，用于执行和验证SQL测试用例
import net.hydromatic.quidem.Command; // 导入Quidem命令接口，表示一个可执行的测试命令
import net.hydromatic.quidem.CommandHandler; // 导入Quidem命令处理器接口，负责解析和创建自定义命令
import net.hydromatic.quidem.Quidem; // 导入Quidem主类，提供SQL测试框架的核心功能

import org.checkerframework.checker.nullness.qual.Nullable; // 导入CheckerFramework的可空注解，用于静态分析空指针安全
import org.junit.jupiter.api.BeforeEach; // 导入JUnit5的BeforeEach注解，标记在每个测试方法执行前运行的方法

import java.sql.Connection; // 导入JDBC连接接口，表示与数据库的连接
import java.util.Collection; // 导入集合接口，表示一组对象的集合
import java.util.List; // 导入列表接口，表示有序的对象集合
import java.util.Locale; // 导入地区类，用于本地化相关的操作
import java.util.Set; // 导入集合接口，表示不重复的对象集合
import java.util.regex.Matcher; // 导入正则表达式匹配器，用于执行正则表达式匹配操作
import java.util.regex.Pattern; // 导入正则表达式模式类，用于编译正则表达式

/**
 * Unit tests for the Babel SQL parser.
 * Babel SQL解析器的单元测试类
 * 
 * 这个类继承自QuidemTest，使用Quidem测试框架来测试Babel SQL解析器的功能。
 * Babel是Calcite的一个扩展SQL解析器，支持多种SQL方言（如BigQuery、Redshift、PostgreSQL、Spark等）。
 * 
 * 主要功能：
 * 1. 提供多种SQL方言的测试环境（babel、scott-babel、scott-redshift、scott-big-query、scott-postgresql、scott-spark）
 * 2. 支持自定义命令处理器，可以解析和执行特殊的测试命令
 * 3. 实现explain-validated-on命令，用于验证和输出SQL解析树
 * 4. 通过Quidem框架执行.iq测试文件中的SQL测试用例
 * 
 * 测试覆盖：
 * - SQL语句的解析和验证
 * - 不同SQL方言的语法兼容性
 * - SQL语句的反解析（unparse）功能
 * - 多种数据库方言的连接配置
 */
class BabelQuidemTest extends QuidemTest { // 类定义：BabelQuidemTest继承自QuidemTest，是Babel SQL解析器的测试类
  /** Runs a test from the command line.
   * 从命令行运行测试的静态主方法
   *
   * <p>For example:
   * 使用示例：
   *
   * <blockquote>
   *   <code>java BabelQuidemTest sql/table.iq</code>
   * </blockquote> */
  public static void main(String[] args) throws Exception { // 主方法声明，接收命令行参数数组，可能抛出异常
    for (String arg : args) { // 遍历命令行参数，每个参数代表一个测试文件路径
      Unsafe.setDefaultLocale(Locale.US); // 设置默认地区为美国，确保测试结果的一致性，避免本地化差异影响测试
      new BabelQuidemTest().test(arg); // 创建BabelQuidemTest实例并执行指定路径的测试文件
    }
  }

  @BeforeEach public void setup() { // 使用JUnit5的BeforeEach注解，标记在每个测试方法执行前运行的初始化方法
    MaterializationService.setThreadLocal(); // 为当前线程设置物化服务，确保物化视图功能在线程级别可用
  }

  /** For {@link QuidemTest#test(String)} parameters.
   * 获取测试文件路径集合的方法，为QuidemTest#test方法提供参数
   * 
   * 该方法返回所有需要执行的测试文件路径，QuidemTest会遍历这些路径并执行每个文件中的测试用例。
   * 
   * @return 包含所有测试文件路径的集合，这些文件位于sql目录下，扩展名为.iq
   */
  @Override public Collection<String> getPath() { // 重写父类方法，返回测试文件路径集合
    // Start with a test file we know exists, then find the directory and list
    // 从一个已知存在的测试文件开始，然后找到该目录并列出所有文件
    // its files.
    final String first = "sql/select.iq"; // 定义第一个测试文件路径，作为参考文件用于定位测试目录
    return data(first); // 调用父类的data方法，根据参考文件路径返回该目录下所有.iq测试文件的路径集合
  }

  @Override protected Quidem.ConnectionFactory createConnectionFactory() { // 重写父类方法，创建Quidem连接工厂，用于根据名称创建不同配置的数据库连接
    return new QuidemConnectionFactory() { // 返回一个匿名内部类，实现了QuidemConnectionFactory接口
      @Override public Connection connect(String name, boolean reference) // 重写connect方法，根据连接名称创建对应的数据库连接
          throws Exception { // 方法可能抛出异常
        switch (name) { // 根据连接名称进行分支判断
        case "babel": // 如果连接名称是"babel"
          return BabelTest.connect(); // 调用BabelTest的connect方法，创建基本的Babel测试连接
        case "scott-babel": // 如果连接名称是"scott-babel"，使用Scott示例数据库和Babel解析器
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置连接
              .with(CalciteAssert.Config.SCOTT) // 配置使用Scott示例数据库（包含EMP、DEPT等经典测试表）
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  SqlBabelParserImpl.class.getName() + "#FACTORY") // 使用SqlBabelParserImpl的FACTORY作为解析器工厂
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL合规性属性
                  SqlConformanceEnum.BABEL) // 使用BABEL合规性级别，支持多种SQL方言特性
              .connect(); // 创建并返回配置好的连接
        case "scott-redshift": // 如果连接名称是"scott-redshift"，使用Scott数据库和Redshift方言
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置连接
              .with(CalciteAssert.Config.SCOTT) // 配置使用Scott示例数据库
              .with(CalciteConnectionProperty.FUN, "standard,redshift") // 设置函数库，支持标准函数和Redshift特定函数
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  SqlBabelParserImpl.class.getName() + "#FACTORY") // 使用SqlBabelParserImpl的FACTORY
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL合规性属性
                  SqlConformanceEnum.BABEL) // 使用BABEL合规性级别
              .with(CalciteConnectionProperty.LENIENT_OPERATOR_LOOKUP, true) // 启用宽松的运算符查找，允许识别更多运算符
              .connect(); // 创建并返回配置好的连接
        case "scott-big-query": // 如果连接名称是"scott-big-query"，使用Scott数据库和BigQuery方言
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置连接
              .with(CalciteAssert.Config.SCOTT) // 配置使用Scott示例数据库
              .with(CalciteConnectionProperty.FUN, "standard,bigquery") // 设置函数库，支持标准函数和BigQuery特定函数
              .with(CalciteConnectionProperty.LEX, Lex.BIG_QUERY) // 设置词法分析策略为BigQuery风格（如反引号标识符）
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  SqlBabelParserImpl.class.getName() + "#FACTORY") // 使用SqlBabelParserImpl的FACTORY
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL合规性属性
                  SqlConformanceEnum.BABEL) // 使用BABEL合规性级别
              .with(CalciteConnectionProperty.LENIENT_OPERATOR_LOOKUP, true) // 启用宽松的运算符查找
              .with(CalciteConnectionProperty.TYPE_SYSTEM, // 设置类型系统属性
                  BigQuerySqlDialect.class.getName() + "#TYPE_SYSTEM") // 使用BigQuery的类型系统
              .with( // 添加自定义类型映射
                  ConnectionFactories.addType("DATETIME", typeFactory -> // 将BigQuery的DATETIME类型映射为
                      typeFactory.createSqlType(SqlTypeName.TIMESTAMP))) // Calcite的TIMESTAMP类型
              .with( // 添加另一个自定义类型映射
                  ConnectionFactories.addType("TIMESTAMP", typeFactory -> // 将BigQuery的TIMESTAMP类型映射为
                      typeFactory.createSqlType( // Calcite的
                          SqlTypeName.TIMESTAMP_WITH_LOCAL_TIME_ZONE))) // TIMESTAMP_WITH_LOCAL_TIME_ZONE类型
              .connect(); // 创建并返回配置好的连接
        case "scott-postgresql": // 如果连接名称是"scott-postgresql"，使用Scott数据库和PostgreSQL方言
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置连接
              .with(CalciteAssert.SchemaSpec.SCOTT) // 配置使用Scott示例数据库
              .with(CalciteConnectionProperty.FUN, "standard,postgresql") // 设置函数库，支持标准函数和PostgreSQL特定函数
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  BabelDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用BabelDdlExecutor的PARSER_FACTORY（支持DDL语句）
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL合规性属性
                  SqlConformanceEnum.BABEL) // 使用BABEL合规性级别
              .with(CalciteConnectionProperty.LENIENT_OPERATOR_LOOKUP, true) // 启用宽松的运算符查找
              .connect(); // 创建并返回配置好的连接
        case "scott-spark": // 如果连接名称是"scott-spark"，使用Scott数据库和Spark SQL方言
          return CalciteAssert.that() // 使用CalciteAssert构建器开始配置连接
              .with(CalciteAssert.SchemaSpec.SCOTT) // 配置使用Scott示例数据库
              .with(CalciteConnectionProperty.FUN, "standard,spark") // 设置函数库，支持标准函数和Spark SQL特定函数
              .with(CalciteConnectionProperty.PARSER_FACTORY, // 设置解析器工厂属性
                  BabelDdlExecutor.class.getName() + "#PARSER_FACTORY") // 使用BabelDdlExecutor的PARSER_FACTORY
              .with(CalciteConnectionProperty.CONFORMANCE, // 设置SQL合规性属性
                  SqlConformanceEnum.BABEL) // 使用BABEL合规性级别
              .with(CalciteConnectionProperty.LENIENT_OPERATOR_LOOKUP, true) // 启用宽松的运算符查找
              .connect(); // 创建并返回配置好的连接
        default: // 如果连接名称不匹配任何已知的情况
          return super.connect(name, reference); // 调用父类的connect方法处理默认情况
        }
      }
    };
  }

  @Override protected CommandHandler createCommandHandler() { // 重写父类方法，创建自定义命令处理器
    return new BabelCommandHandler(); // 返回BabelCommandHandler实例，用于解析和处理特殊的Quidem测试命令
  }

  /** Command that prints the validated parse tree of a SQL statement.
   * ExplainValidatedCommand是一个自定义命令，用于打印SQL语句验证后的解析树
   * 
   * 该命令会执行以下操作：
   * 1. 使用Babel解析器解析SQL语句
   * 2. 对解析后的SqlNode进行语义验证
   * 3. 将验证后的SqlNode反解析（unparse）为SQL字符串
   * 4. 输出反解析后的SQL语句
   * 
   * 这个命令主要用于测试SQL解析器的正确性，通过比较原始SQL和反解析后的SQL来验证解析和验证过程。
   */
  static class ExplainValidatedCommand extends AbstractCommand { // 静态内部类，继承自AbstractCommand，表示一个可执行的Quidem命令
    private final ImmutableList<String> lines; // 成员变量：存储命令行的不可变列表，包含命令的所有行
    private final ImmutableList<String> content; // 成员变量：存储命令内容的不可变列表，包含命令的详细内容

    ExplainValidatedCommand(List<String> lines, List<String> content, // 构造方法：创建ExplainValidatedCommand实例
        Set<String> unusedProductSet) { // 参数lines：命令行列表；参数content：命令内容列表；参数unusedProductSet：未使用的产品集合（保留参数以兼容接口）
      this.lines = ImmutableList.copyOf(lines); // 将命令行列表转换为不可变列表并赋值给成员变量
      this.content = ImmutableList.copyOf(content); // 将命令内容列表转换为不可变列表并赋值给成员变量
    }

    @Override public void execute(Context x, boolean execute) throws Exception { // 重写execute方法，执行命令逻辑；参数x：Quidem上下文对象；参数execute：是否实际执行命令
      if (execute) { // 如果execute为true，表示需要实际执行命令逻辑
        // use Babel parser
        // 使用Babel解析器
        final SqlParser.Config parserConfig = // 创建SQL解析器配置对象
            SqlParser.config().withParserFactory(SqlBabelParserImpl.FACTORY); // 配置使用SqlBabelParserImpl的FACTORY作为解析器工厂

        // extract named schema from connection and use it in planner
        // 从连接中提取命名的schema并在规划器中使用
        final CalciteConnection calciteConnection = // 获取Calcite连接对象
            x.connection().unwrap(CalciteConnection.class); // 从Quidem上下文的连接中解包出CalciteConnection
        final String schemaName = calciteConnection.getSchema(); // 获取当前连接的schema名称
        final SchemaPlus schema = // 获取SchemaPlus对象，表示数据库模式
            schemaName != null // 如果schema名称不为null
                ? calciteConnection.getRootSchema().subSchemas().get(schemaName) // 则从根schema中获取指定名称的子schema
                : calciteConnection.getRootSchema(); // 否则直接使用根schema
        final Frameworks.ConfigBuilder config = // 创建框架配置构建器，用于构建Calcite规划器配置
            Frameworks.newConfigBuilder() // 使用Frameworks工具类创建新的配置构建器
                .defaultSchema(schema) // 设置默认schema为获取到的schema对象
                .parserConfig(parserConfig) // 设置解析器配置为之前创建的parserConfig
                .context(Contexts.of(calciteConnection.config())); // 设置上下文为Calcite连接的配置

        // parse, validate and un-parse
        // 解析、验证和反解析SQL语句
        final Quidem.SqlCommand sqlCommand = x.previousSqlCommand(); // 获取上下文中前一个SQL命令对象
        final Planner planner = Frameworks.getPlanner(config.build()); // 使用构建的配置创建Calcite规划器实例
        final SqlNode node = planner.parse(sqlCommand.sql); // 使用规划器解析SQL字符串，生成SqlNode语法树
        final SqlNode validateNode = planner.validate(node); // 使用规划器验证SqlNode，进行语义分析（如类型检查、表存在性验证等）
        final SqlWriter sqlWriter = new SqlPrettyWriter(); // 创建SQL美化写入器，用于格式化输出SQL语句
        validateNode.unparse(sqlWriter, 0, 0); // 将验证后的SqlNode反解析为SQL字符串，参数0,0表示缩进级别
        x.echo(ImmutableList.of(sqlWriter.toSqlString().getSql())); // 将反解析后的SQL字符串输出到Quidem上下文
      } else { // 如果execute为false，表示不实际执行命令逻辑
        x.echo(content); // 直接输出命令内容到Quidem上下文
      }
      x.echo(lines); // 输出命令行到Quidem上下文
    }
  }

  /** Command handler that adds a "!explain-validated-on dialect..." command
   * BabelCommandHandler是一个命令处理器，用于添加自定义的"!explain-validated-on dialect..."命令
   * 
   * 该处理器会解析以"explain-validated-on"开头的命令，并创建对应的ExplainValidatedCommand实例。
   * 
   * 命令格式示例：
   * !explain-validated-on dialect1 dialect2
   * 
   * 其中dialect部分是可选的，可以指定多个方言标识符（使用空格、下划线、加号、字母或数字）。
   * 
   * (see {@link ExplainValidatedCommand}). */
  private static class BabelCommandHandler implements CommandHandler { // 私有静态内部类，实现CommandHandler接口，用于解析和处理自定义命令
    @Override public @Nullable Command parseCommand(List<String> lines, // 重写parseCommand方法，解析命令行并创建对应的Command对象
        List<String> content, String line) { // 参数lines：命令行列表；参数content：命令内容列表；参数line：当前命令行字符串
      final String prefix = "explain-validated-on"; // 定义命令前缀字符串，用于识别自定义命令
      if (line.startsWith(prefix)) { // 如果当前命令行以"explain-validated-on"开头
        final Pattern pattern = // 创建正则表达式模式对象
            Pattern.compile("explain-validated-on( [-_+a-zA-Z0-9]+)*?"); // 编译正则表达式，匹配"explain-validated-on"后跟零个或多个方言标识符的模式
        final Matcher matcher = pattern.matcher(line); // 创建匹配器，将正则表达式应用于当前命令行
        if (matcher.matches()) { // 如果命令行完全匹配正则表达式模式
          final ImmutableSet.Builder<String> set = ImmutableSet.builder(); // 创建不可变集合构建器，用于存储解析出的方言标识符
          for (int i = 0; i < matcher.groupCount(); i++) { // 遍历匹配器捕获的所有组
            set.add(matcher.group(i + 1)); // 将每个捕获组（方言标识符）添加到集合中，注意从group(1)开始因为group(0)是整个匹配字符串
          }
          return new ExplainValidatedCommand(lines, content, set.build()); // 创建并返回ExplainValidatedCommand实例，传入命令行、内容和方言集合
        }
      }
      return null; // 如果命令行不匹配，返回null表示无法解析该命令
    }
  }
} // 类定义结束，BabelQuidemTest类的结束花括号
