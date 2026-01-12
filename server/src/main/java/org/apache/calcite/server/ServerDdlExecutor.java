/* // Apache许可证声明，表明该代码遵循Apache 2.0许可证，允许在特定条件下自由使用、修改和分发
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
package org.apache.calcite.server; // 声明包名，该类属于org.apache.calcite.server包，用于Calcite服务器的DDL执行

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于创建Java相关的数据类型
import org.apache.calcite.adapter.jdbc.JdbcSchema; // 导入JDBC模式，用于通过JDBC连接外部数据库
import org.apache.calcite.avatica.AvaticaUtils; // 导入Avatica工具类，提供反射实例化等工具方法
import org.apache.calcite.jdbc.CalcitePrepare; // 导入Calcite准备上下文，用于SQL语句的准备工作
import org.apache.calcite.jdbc.CalciteSchema; // 导入Calcite模式，表示数据库模式结构
import org.apache.calcite.jdbc.ContextSqlValidator; // 导入上下文SQL验证器，用于验证SQL语句的语法和语义
import org.apache.calcite.linq4j.Ord; // 导入Ord工具类，用于将集合元素包装为带索引的对象
import org.apache.calcite.materialize.MaterializationKey; // 导入物化视图键，用于唯一标识物化视图
import org.apache.calcite.materialize.MaterializationService; // 导入物化视图服务，用于管理物化视图的生命周期
import org.apache.calcite.model.JsonSchema; // 导入JSON模式，用于从JSON配置创建数据库模式
import org.apache.calcite.plan.RelOptTable; // 导入关系优化表，表示优化器中的表对象
import org.apache.calcite.rel.RelRoot; // 导入关系根节点，表示关系代数表达式的根
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型，表示表或字段的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeField; // 导入关系数据类型字段，表示表或视图的字段
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入关系数据类型实现类
import org.apache.calcite.rex.RexNode; // 导入行表达式节点，表示关系表达式中的表达式
import org.apache.calcite.schema.ColumnStrategy; // 导入列策略，定义列的存储策略（如虚拟、存储、默认值等）
import org.apache.calcite.schema.Function; // 导入函数接口，表示数据库函数
import org.apache.calcite.schema.Schema; // 导入模式接口，表示数据库模式
import org.apache.calcite.schema.SchemaFactory; // 导入模式工厂接口，用于创建数据库模式
import org.apache.calcite.schema.SchemaPlus; // 导入扩展模式接口，提供额外的模式操作功能
import org.apache.calcite.schema.Table; // 导入表接口，表示数据库表
import org.apache.calcite.schema.TranslatableTable; // 导入可翻译表接口，表示可以转换为关系代数的表
import org.apache.calcite.schema.Wrapper; // 导入包装器接口，用于解包对象
import org.apache.calcite.schema.impl.AbstractSchema; // 导入抽象模式实现类
import org.apache.calcite.schema.impl.ViewTable; // 导入视图表实现类
import org.apache.calcite.schema.impl.ViewTableMacro; // 导入视图表宏，用于创建视图表
import org.apache.calcite.sql.SqlCall; // 导入SQL调用节点，表示函数调用或操作符调用
import org.apache.calcite.sql.SqlDataTypeSpec; // 导入SQL数据类型规范，定义数据类型
import org.apache.calcite.sql.SqlIdentifier; // 导入SQL标识符，表示表名、列名等标识符
import org.apache.calcite.sql.SqlKind; // 导入SQL种类，定义SQL语句的类型
import org.apache.calcite.sql.SqlLiteral; // 导入SQL字面量，表示常量值
import org.apache.calcite.sql.SqlNode; // 导入SQL节点基类，所有SQL语法树的基类
import org.apache.calcite.sql.SqlNodeList; // 导入SQL节点列表，表示节点集合
import org.apache.calcite.sql.SqlSelect; // 导入SQL SELECT语句节点
import org.apache.calcite.sql.SqlUtil; // 导入SQL工具类，提供SQL相关的工具方法
import org.apache.calcite.sql.SqlWriterConfig; // 导入SQL写入器配置，用于控制SQL输出的格式
import org.apache.calcite.sql.ddl.SqlAttributeDefinition; // 导入SQL属性定义，用于定义类型的属性
import org.apache.calcite.sql.ddl.SqlColumnDeclaration; // 导入SQL列声明，用于定义表的列
import org.apache.calcite.sql.ddl.SqlCreateForeignSchema; // 导入创建外部模式语句
import org.apache.calcite.sql.ddl.SqlCreateFunction; // 导入创建函数语句
import org.apache.calcite.sql.ddl.SqlCreateMaterializedView; // 导入创建物化视图语句
import org.apache.calcite.sql.ddl.SqlCreateSchema; // 导入创建模式语句
import org.apache.calcite.sql.ddl.SqlCreateTable; // 导入创建表语句
import org.apache.calcite.sql.ddl.SqlCreateTableLike; // 导入创建类似表语句
import org.apache.calcite.sql.ddl.SqlCreateType; // 导入创建类型语句
import org.apache.calcite.sql.ddl.SqlCreateView; // 导入创建视图语句
import org.apache.calcite.sql.ddl.SqlDropObject; // 导入删除对象语句
import org.apache.calcite.sql.ddl.SqlDropSchema; // 导入删除模式语句
import org.apache.calcite.sql.ddl.SqlTruncateTable; // 导入清空表语句
import org.apache.calcite.sql.dialect.CalciteSqlDialect; // 导入Calcite SQL方言，用于生成Calcite兼容的SQL
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表，包含所有标准SQL操作符
import org.apache.calcite.sql.parser.SqlAbstractParserImpl; // 导入SQL抽象解析器实现
import org.apache.calcite.sql.parser.SqlParseException; // 导入SQL解析异常
import org.apache.calcite.sql.parser.SqlParserImplFactory; // 导入SQL解析器工厂接口
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析器位置，记录语法位置信息
import org.apache.calcite.sql.parser.ddl.SqlDdlParserImpl; // 导入DDL解析器实现
import org.apache.calcite.sql.pretty.SqlPrettyWriter; // 导入SQL美化写入器，用于格式化输出SQL
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口，用于验证SQL语句
import org.apache.calcite.sql2rel.InitializerContext; // 导入初始化器上下文，用于列初始化
import org.apache.calcite.sql2rel.InitializerExpressionFactory; // 导入初始化表达式工厂，用于创建列的默认值表达式
import org.apache.calcite.sql2rel.NullInitializerExpressionFactory; // 导入空初始化表达式工厂，提供默认的初始化行为
import org.apache.calcite.tools.FrameworkConfig; // 导入框架配置，用于配置Calcite框架
import org.apache.calcite.tools.Frameworks; // 导入框架工具类，用于创建框架实例
import org.apache.calcite.tools.Planner; // 导入规划器接口，用于SQL优化和执行
import org.apache.calcite.tools.RelConversionException; // 导入关系转换异常
import org.apache.calcite.tools.ValidationException; // 导入验证异常
import org.apache.calcite.util.NlsString; // 导入国际化字符串，支持多语言的字符串值
import org.apache.calcite.util.Pair; // 导入键值对工具类，用于存储两个相关联的值
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种实用方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，标记可能为null的值

import java.io.Reader; // 导入字符读取器，用于读取字符流
import java.sql.PreparedStatement; // 导入预编译语句，用于执行参数化的SQL语句
import java.sql.SQLException; // 导入SQL异常
import java.util.ArrayList; // 导入动态数组列表
import java.util.Arrays; // 导入数组工具类
import java.util.LinkedHashMap; // 导入链式哈希映射，保持插入顺序的Map
import java.util.List; // 导入列表接口
import java.util.Locale; // 导入地区设置，用于国际化
import java.util.Map; // 导入映射接口
import java.util.Set; // 导入集合接口

import static com.google.common.base.Preconditions.checkArgument; // 导入参数检查工具

import static org.apache.calcite.util.Static.RESOURCE; // 导入静态资源，用于获取错误消息

import static java.util.Objects.requireNonNull; // 导入对象非空检查工具

/** // 类注释：ServerDdlExecutor是Calcite服务器中用于执行DDL（数据定义语言）命令的核心类
 * Executes DDL commands. // 执行DDL命令，包括创建、删除、修改数据库对象的操作
 *
 * <p>Given a DDL command that is a sub-class of {@link SqlNode}, dispatches // 接收一个SqlNode子类的DDL命令，并将命令分派到相应的execute方法
 * the command to an appropriate {@code execute} method. For example, // 例如，将"CREATE TABLE"（SqlCreateTable）分派到execute(SqlCreateTable, CalcitePrepare.Context)方法
 * "CREATE TABLE" ({@link SqlCreateTable}) is dispatched to // 这种设计模式采用了命令分发模式，根据不同的DDL类型调用不同的处理方法
 * {@link #execute(SqlCreateTable, CalcitePrepare.Context)}. // 该类继承自DdlExecutorImpl，提供了完整的DDL执行功能
 */ // 支持的DDL操作包括：创建/删除表、视图、物化视图、模式、类型、函数等
public class ServerDdlExecutor extends DdlExecutorImpl { // ServerDdlExecutor继承自DdlExecutorImpl，实现了DDL命令的执行逻辑
  /** Singleton instance. */ // 单例实例，整个应用程序共享一个ServerDdlExecutor实例，避免重复创建
  public static final ServerDdlExecutor INSTANCE = new ServerDdlExecutor(); // 静态常量，ServerDdlExecutor的唯一实例，通过INSTANCE访问

  /** Parser factory. */ // 解析器工厂，用于创建DDL语句的解析器
  @SuppressWarnings("unused") // 抑制未使用警告，该字段通过反射使用
  public static final SqlParserImplFactory PARSER_FACTORY = // 静态常量，SQL解析器工厂实例
      new SqlParserImplFactory() { // 匿名内部类，实现SqlParserImplFactory接口
        @Override public SqlAbstractParserImpl getParser(Reader stream) { // 重写getParser方法，创建SQL解析器
          return SqlDdlParserImpl.FACTORY.getParser(stream); // 返回DDL解析器实例，专门用于解析DDL语句
        }

        @Override public DdlExecutor getDdlExecutor() { // 重写getDdlExecutor方法，返回DDL执行器
          return ServerDdlExecutor.INSTANCE; // 返回ServerDdlExecutor的单例实例
        }
      };

  /** Creates a ServerDdlExecutor. // 构造函数注释：创建ServerDdlExecutor实例
   * Protected only to allow sub-classing; // 使用protected修饰符仅允许子类化，外部代码应使用INSTANCE单例
   * use {@link #INSTANCE} where possible. */ // 推荐使用INSTANCE静态实例而不是直接创建新实例
  protected ServerDdlExecutor() { // 受保护的构造函数，防止外部直接实例化
  } // 空构造函数，不需要任何初始化操作

  /** Returns the schema in which to create an object; // 方法注释：返回要在其中创建对象的模式
   * the left part is null if the schema does not exist. */ // 如果模式不存在，Pair的左部分为null
  static Pair<@Nullable CalciteSchema, String> schema( // 静态方法，返回一个Pair对象，包含CalciteSchema和对象名称
      CalcitePrepare.Context context, boolean mutable, SqlIdentifier id) { // 参数：上下文、是否可变、SQL标识符
    final String name; // 声明对象名称变量
    final List<String> path; // 声明路径变量，表示模式的层级路径
    if (id.isSimple()) { // 判断标识符是否为简单标识符（即不带模式前缀）
      path = context.getDefaultSchemaPath(); // 如果是简单标识符，使用默认的schema路径
      name = id.getSimple(); // 获取简单标识符的名称
    } else { // 如果标识符包含模式前缀（如schema.table）
      path = Util.skipLast(id.names); // 跳过最后一部分，得到模式路径
      name = Util.last(id.names); // 获取最后一部分，即对象名称
    }
    CalciteSchema schema = // 声明CalciteSchema变量
        mutable ? context.getMutableRootSchema() // 根据mutable参数决定获取可变还是不可变的根schema
            : context.getRootSchema(); // 如果mutable为true，获取可变schema；否则获取不可变schema
    for (String p : path) { // 遍历路径中的每个部分
      @Nullable CalciteSchema subSchema = schema.getSubSchema(p, true); // 获取子schema，第二个参数true表示不区分大小写
      if (subSchema == null) { // 如果子schema不存在
        return Pair.of(null, name); // 返回Pair，左部分为null，右部分为对象名称
      }
      schema = subSchema; // 如果子schema存在，更新当前schema引用
    }
    return Pair.of(schema, name); // 返回Pair对象，包含找到的schema和对象名称
  }

  /**
   * Returns the SqlValidator with the given {@code context} schema // 方法注释：返回给定上下文schema和类型工厂的SQL验证器
   * and type factory. // SqlValidator用于验证SQL语句的语法和语义正确性
   */
  static SqlValidator validator(CalcitePrepare.Context context, // 静态方法，创建并返回SQL验证器
      boolean mutable) { // 参数：上下文、是否可变
    return new ContextSqlValidator(context, mutable); // 返回ContextSqlValidator实例，使用给定的上下文和可变性标志
  }

  /** Wraps a query to rename its columns. Used by CREATE VIEW and CREATE // 方法注释：包装查询以重命名其列，用于CREATE VIEW和CREATE MATERIALIZED VIEW
   * MATERIALIZED VIEW. */ // 通过在查询外层包裹SELECT语句，使用AS子句重命名列
  static SqlNode renameColumns(@Nullable SqlNodeList columnList, // 静态方法，重命名查询的列
      SqlNode query) { // 参数：列列表（可为null）、查询节点
    if (columnList == null) { // 如果列列表为null，表示不需要重命名
      return query; // 直接返回原始查询
    }
    final SqlParserPos p = query.getParserPosition(); // 获取查询的解析器位置，用于创建新节点
    final SqlNodeList selectList = SqlNodeList.SINGLETON_STAR; // 创建包含单个星号的SELECT列表，表示选择所有列
    final SqlCall from = // 创建FROM子句，使用AS操作符为查询创建别名
        SqlStdOperatorTable.AS.createCall(p, // 调用AS操作符，传入位置和参数
            ImmutableList.<SqlNode>builder() // 使用构建器创建不可变列表
                .add(query) // 添加原始查询
                .add(new SqlIdentifier("_", p)) // 添加下划线作为表别名
                .addAll(columnList) // 添加所有列名作为列别名
                .build()); // 构建参数列表
    return new SqlSelect(p, null, selectList, from, null, null, null, null, // 创建新的SELECT语句，包裹原始查询
        null, null, null, null, null); // 参数：位置、关键字、SELECT列表、FROM、WHERE、GROUP BY、HAVING、窗口、ORDER BY、OFFSET、FETCH、ALL等
  }

  /** Erase the table date that calcite-sever created. // 方法注释：擦除calcite-server创建的表数据，用于TRUNCATE TABLE操作
   */ // 直接清除表中的所有行数据，比执行SQL更高效
  static void erase(SqlIdentifier name, CalcitePrepare.Context context) { // 静态方法，擦除表数据
    // Directly clearing data is more efficient than executing SQL // 直接清除数据比执行SQL更高效，避免了SQL解析和优化的开销
    final Pair<@Nullable CalciteSchema, String> pair = // 获取表所在的schema和表名
        schema(context, true, name); // 使用可变schema获取表信息
    final CalciteSchema calciteSchema = requireNonNull(pair.left); // 获取CalciteSchema，确保不为null
    final String tblName = pair.right; // 获取表名
    final CalciteSchema.TableEntry tableEntry = // 获取表条目
        calciteSchema.getTable(tblName, context.config().caseSensitive()); // 根据表名和大小写敏感配置获取表条目
    final Table table = requireNonNull(tableEntry, "tableEntry").getTable(); // 获取Table对象，确保表条目不为null
    if (table instanceof MutableArrayTable) { // 判断表是否为MutableArrayTable类型（calcite-server创建的表）
      MutableArrayTable mutableArrayTable = (MutableArrayTable) table; // 强制转换为MutableArrayTable
      mutableArrayTable.rows.clear(); // 清空表中的所有行数据
    } else { // 如果不是MutableArrayTable类型
      // Not calcite-server created, so not support truncate. // 不是calcite-server创建的表，不支持truncate操作
      throw new UnsupportedOperationException("Only MutableArrayTable support truncate"); // 抛出不支持操作异常
    }
  }

  /** Populates the table called {@code name} by executing {@code query}. // 方法注释：通过执行查询来填充指定名称的表，用于CREATE TABLE AS SELECT和CREATE MATERIALIZED VIEW
   */ // 将查询结果插入到目标表中，实现表的数据填充
  static void populate(SqlIdentifier name, SqlNode query, // 静态方法，填充表数据
      CalcitePrepare.Context context) { // 参数：表名、查询节点、上下文
    // Generate, prepare and execute an "INSERT INTO table query" statement. // 生成、准备并执行"INSERT INTO table query"语句
    // (It's a bit inefficient that we convert from SqlNode to SQL and back // 将SqlNode转换为SQL字符串再转换回SqlNode，这种方式有些低效
    // again.) // 但这样可以复用现有的SQL执行逻辑
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
        .defaultSchema(context.getRootSchema().plus()) // 设置默认schema为根schema的扩展版本
        .build(); // 构建配置对象
    final Planner planner = Frameworks.getPlanner(config); // 使用配置获取规划器实例
    try { // 开始try块，捕获可能的异常
      final StringBuilder buf = new StringBuilder(); // 创建字符串构建器，用于构建SQL语句
      final SqlWriterConfig writerConfig = // 创建SQL写入器配置
          SqlPrettyWriter.config().withAlwaysUseParentheses(false); // 配置不总是使用括号
      final SqlPrettyWriter w = new SqlPrettyWriter(writerConfig, buf); // 创建SQL美化写入器
      buf.append("INSERT INTO "); // 追加INSERT INTO关键字
      name.unparse(w, 0, 0); // 将表名转换为SQL字符串并追加
      buf.append(' '); // 追加空格
      query.unparse(w, 0, 0); // 将查询转换为SQL字符串并追加
      final String sql = buf.toString(); // 获取完整的SQL语句字符串
      final SqlNode query1 = planner.parse(sql); // 解析SQL语句为SqlNode
      final SqlNode query2 = planner.validate(query1); // 验证SqlNode的语法和语义
      final RelRoot r = planner.rel(query2); // 将验证后的SqlNode转换为关系代数表达式
      final PreparedStatement prepare = // 准备执行语句
          context.getRelRunner().prepareStatement(r.rel); // 使用关系运行器准备执行语句
      int rowCount = prepare.executeUpdate(); // 执行更新操作，返回影响的行数
      Util.discard(rowCount); // 丢弃行数（不使用），避免编译器警告
      prepare.close(); // 关闭预编译语句，释放资源
    } catch (SqlParseException | ValidationException // 捕获SQL解析异常和验证异常
        | RelConversionException | SQLException e) { // 捕获关系转换异常和SQL异常
      throw Util.throwAsRuntime(e); // 将检查异常转换为运行时异常并抛出
    }
  }

  /** Returns the value of a literal, converting // 方法注释：返回字面量的值，将NlsString转换为String
   * {@link NlsString} into String. */ // NlsString是国际化字符串，需要提取其值部分
  @SuppressWarnings("rawtypes") // 抑制原始类型警告
  static @Nullable Comparable value(SqlNode node) { // 静态方法，获取字面量的值
    final Comparable v = SqlLiteral.value(node); // 从SqlLiteral中提取值
    return v instanceof NlsString ? ((NlsString) v).getValue() : v; // 如果值是NlsString类型，返回其字符串值；否则返回原值
  }

  /** Executes a {@code CREATE FOREIGN SCHEMA} command. // 方法注释：执行CREATE FOREIGN SCHEMA命令，创建外部数据库模式
   */ // 外部模式允许Calcite访问外部数据源（如JDBC数据库）的数据
  public void execute(SqlCreateForeignSchema create, // 方法重载，执行创建外部模式
      CalcitePrepare.Context context) { // 参数：创建外部模式语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取父schema和模式名称
        schema(context, true, create.name); // 使用可变schema获取模式信息
    requireNonNull(pair.left); // 确保父schema不为null
    // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
    if (pair.left.plus().subSchemas().get(pair.right) != null) { // 检查模式是否已存在
      if (!create.getReplace() && !create.ifNotExists) { // 如果模式已存在且没有REPLACE或IF NOT EXISTS选项
        throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.schemaExists(pair.right)); // 错误信息：模式已存在
      }
    }
    final Schema subSchema; // 声明子schema变量
    final String libraryName; // 声明库名称变量，用于指定SchemaFactory的类名
    if (create.type != null) { // 如果指定了类型（如JDBC）
      checkArgument(create.library == null); // 检查library必须为null（type和library互斥）
      final String typeName = (String) requireNonNull(value(create.type)); // 获取类型名称
      final JsonSchema.Type type = // 将类型名称转换为JsonSchema.Type枚举
          Util.enumVal(JsonSchema.Type.class, // 使用反射获取枚举值
              typeName.toUpperCase(Locale.ROOT)); // 转换为大写以匹配枚举值
      if (type != null) { // 如果类型不为null
        switch (type) { // 根据类型选择对应的SchemaFactory
        case JDBC: // 如果是JDBC类型
          libraryName = JdbcSchema.Factory.class.getName(); // 使用JdbcSchema.Factory作为SchemaFactory
          break; // 跳出switch
        default: // 其他类型
          libraryName = null; // 库名称设为null
        }
      } else { // 如果类型为null
        libraryName = null; // 库名称设为null
      }
      if (libraryName == null) { // 如果库名称为null
        throw SqlUtil.newContextException(create.type.getParserPosition(), // 抛出上下文异常
            RESOURCE.schemaInvalidType(typeName, // 错误信息：无效的模式类型
                Arrays.toString(JsonSchema.Type.values()))); // 显示所有可用的类型
      }
    } else { // 如果没有指定类型
      libraryName = // 获取library参数的值
          requireNonNull((String) value(requireNonNull(create.library))); // 确保library不为null并获取其值
    }
    final SchemaFactory schemaFactory = // 使用反射实例化SchemaFactory
        AvaticaUtils.instantiatePlugin(SchemaFactory.class, libraryName); // 根据类名创建SchemaFactory实例
    final Map<String, Object> operandMap = new LinkedHashMap<>(); // 创建操作数映射，用于存储SchemaFactory的参数
    for (Pair<SqlIdentifier, SqlNode> option : create.options()) { // 遍历所有选项
      operandMap.put(option.left.getSimple(), // 将选项名作为键
          requireNonNull(value(option.right))); // 将选项值转换为字面量值作为值
    }
    subSchema = // 调用SchemaFactory创建子schema
        schemaFactory.create(pair.left.plus(), pair.right, operandMap); // 传入父schema、名称和参数
    pair.left.add(pair.right, subSchema); // 将子schema添加到父schema中
  }

  /** Executes a {@code CREATE FUNCTION} command. // 方法注释：执行CREATE FUNCTION命令，创建自定义函数
   */ // 当前版本不支持创建函数，直接抛出异常
  public void execute(SqlCreateFunction create, // 方法重载，执行创建函数
      CalcitePrepare.Context context) { // 参数：创建函数语句、上下文
    throw new UnsupportedOperationException("CREATE FUNCTION is not supported"); // 抛出不支持操作异常
  }

  /** Executes {@code DROP FUNCTION}, {@code DROP TABLE}, // 方法注释：执行DROP FUNCTION、DROP TABLE、DROP MATERIALIZED VIEW、DROP TYPE、DROP VIEW命令
   * {@code DROP MATERIALIZED VIEW}, {@code DROP TYPE}, // 支持删除多种数据库对象
   * {@code DROP VIEW} commands. */ // 根据对象类型执行相应的删除操作
  public void execute(SqlDropObject drop, // 方法重载，执行删除对象
      CalcitePrepare.Context context) { // 参数：删除对象语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取对象所在的schema和对象名称
        schema(context, false, drop.name); // 使用不可变schema获取对象信息
    final @Nullable CalciteSchema schema = // 获取schema引用
        pair.left; // null if schema does not exist // 如果schema不存在则为null
    final String objectName = pair.right; // 获取对象名称

    boolean existed; // 声明对象是否存在的标志
    switch (drop.getKind()) { // 根据删除对象的类型进行切换
    case DROP_TABLE: // 如果是删除表
    case DROP_MATERIALIZED_VIEW: // 或者是删除物化视图
      Table materializedView = // 声明物化视图变量
          schema != null // 如果schema不为null
              && drop.getKind() == SqlKind.DROP_MATERIALIZED_VIEW // 且是删除物化视图操作
              ? schema.plus().tables().get(objectName) // 获取物化视图表对象
              : null; // 否则为null

      existed = schema != null && schema.removeTable(objectName); // 从schema中移除表，返回是否成功
      if (existed) { // 如果表存在且删除成功
        if (materializedView instanceof Wrapper) { // 如果物化视图实现了Wrapper接口
          ((Wrapper) materializedView).maybeUnwrap(MaterializationKey.class) // 尝试解包获取MaterializationKey
              .ifPresent(materializationKey -> // 如果获取到物化视图键
                  MaterializationService.instance() // 获取物化视图服务实例
                      .removeMaterialization(materializationKey)); // 从服务中移除物化视图
        }
      } else if (!drop.ifExists) { // 如果表不存在且没有IF EXISTS选项
        throw SqlUtil.newContextException(drop.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.tableNotFound(objectName)); // 错误信息：表未找到
      }
      break; // 跳出switch
    case DROP_VIEW: // 如果是删除视图
      // Not quite right: removes any other functions with the same name // 注释：不完全正确，会删除同名的其他函数
      existed = schema != null && schema.removeFunction(objectName); // 从schema中移除函数（视图作为函数存储）
      if (!existed && !drop.ifExists) { // 如果视图不存在且没有IF EXISTS选项
        throw SqlUtil.newContextException(drop.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.viewNotFound(objectName)); // 错误信息：视图未找到
      }
      break; // 跳出switch
    case DROP_TYPE: // 如果是删除类型
      existed = schema != null && schema.removeType(objectName); // 从schema中移除类型
      if (!existed && !drop.ifExists) { // 如果类型不存在且没有IF EXISTS选项
        throw SqlUtil.newContextException(drop.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.typeNotFound(objectName)); // 错误信息：类型未找到
      }
      break; // 跳出switch
    case DROP_FUNCTION: // 如果是删除函数
      existed = schema != null && schema.removeFunction(objectName); // 从schema中移除函数
      if (!existed && !drop.ifExists) { // 如果函数不存在且没有IF EXISTS选项
        throw SqlUtil.newContextException(drop.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.functionNotFound(objectName)); // 错误信息：函数未找到
      }
      break; // 跳出switch
    case OTHER_DDL: // 其他DDL操作
    default: // 默认情况
      throw new AssertionError(drop.getKind()); // 抛出断言错误，表示不支持的DDL类型
    }
  }

  /**
   * Executes a {@code TRUNCATE TABLE} command. // 方法注释：执行TRUNCATE TABLE命令，清空表中的所有数据
   */ // TRUNCATE比DELETE更高效，因为它不记录每一行的删除操作
  public void execute(SqlTruncateTable truncate, // 方法重载，执行清空表
      CalcitePrepare.Context context) { // 参数：清空表语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取表所在的schema和表名
        schema(context, true, truncate.name); // 使用可变schema获取表信息
    if (pair.left == null // 如果schema为null
        || pair.left.plus().tables().get(pair.right) == null) { // 或者表不存在
      throw SqlUtil.newContextException(truncate.name.getParserPosition(), // 抛出上下文异常
          RESOURCE.tableNotFound(pair.right)); // 错误信息：表未找到
    }

    if (!truncate.continueIdentify) { // 如果不支持RESTART IDENTITY
      // Calcite not support RESTART IDENTIFY // Calcite不支持RESTART IDENTITY选项
      throw new UnsupportedOperationException("RESTART IDENTIFY is not supported"); // 抛出不支持操作异常
    }

    erase(truncate.name, context); // 调用erase方法清空表数据
  }

  /** Executes a {@code CREATE MATERIALIZED VIEW} command. // 方法注释：执行CREATE MATERIALIZED VIEW命令，创建物化视图
   */ // 物化视图是预先计算并存储的查询结果，可以提高查询性能
  public void execute(SqlCreateMaterializedView create, // 方法重载，执行创建物化视图
      CalcitePrepare.Context context) { // 参数：创建物化视图语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取schema和物化视图名称
        schema(context, true, create.name); // 使用可变schema获取信息
    if (pair.left != null // 如果schema不为null
        && pair.left.plus().tables().get(pair.right) != null) { // 且物化视图已存在
      // Materialized view exists. // 物化视图已存在
      if (!create.ifNotExists) { // 如果没有IF NOT EXISTS选项
        // They did not specify IF NOT EXISTS, so give error. // 用户没有指定IF NOT EXISTS，抛出错误
        throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.tableExists(pair.right)); // 错误信息：表已存在
      }
      return; // 如果有IF NOT EXISTS选项，直接返回
    }
    final SqlNode q = renameColumns(create.columnList, create.query); // 重命名查询的列（如果指定了列列表）
    final String sql = q.toSqlString(CalciteSqlDialect.DEFAULT).getSql(); // 将查询转换为SQL字符串
    requireNonNull(pair.left); // 确保schema不为null
    // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
    final List<String> schemaPath = pair.left.path(null); // 获取schema路径
    final ViewTableMacro viewTableMacro = // 创建视图表宏
        ViewTable.viewMacro(pair.left.plus(), sql, schemaPath, // 传入schema、SQL、路径等参数
            context.getObjectPath(), false); // false表示不替换
    final TranslatableTable x = viewTableMacro.apply(ImmutableList.of()); // 应用宏获取可翻译表
    final RelDataType rowType = x.getRowType(context.getTypeFactory()); // 获取行类型

    // Table does not exist. Create it. // 表不存在，创建它
    final MaterializedViewTable table = // 创建物化视图表
        new MaterializedViewTable(pair.right, RelDataTypeImpl.proto(rowType)); // 传入表名和行类型原型
    pair.left.add(pair.right, table); // 将物化视图表添加到schema中
    populate(create.name, create.query, context); // 填充物化视图的数据
    table.key = // 设置物化视图的键
        MaterializationService.instance().defineMaterialization(pair.left, null, // 定义物化视图
            sql, schemaPath, pair.right, true, true); // 传入SQL、路径、名称等参数
  }

  /** Executes a {@code CREATE SCHEMA} command. // 方法注释：执行CREATE SCHEMA命令，创建新的数据库模式
   */ // 模式是数据库对象的命名空间，用于组织表、视图等对象
  public void execute(SqlCreateSchema create, // 方法重载，执行创建模式
      CalcitePrepare.Context context) { // 参数：创建模式语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取父schema和模式名称
        schema(context, true, create.name); // 使用可变schema获取信息
    requireNonNull(pair.left); // 确保父schema不为null
    // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
    if (pair.left.plus().subSchemas().get(pair.right) != null) { // 检查模式是否已存在
      if (create.ifNotExists) { // 如果有IF NOT EXISTS选项
        return; // 直接返回，不创建
      }
      if (!create.getReplace()) { // 如果没有REPLACE选项
        throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.schemaExists(pair.right)); // 错误信息：模式已存在
      }
    }
    final Schema subSchema = new AbstractSchema(); // 创建新的抽象schema实例
    pair.left.add(pair.right, subSchema); // 将子schema添加到父schema中
  }

  /** Executes a {@code DROP SCHEMA} command. // 方法注释：执行DROP SCHEMA命令，删除数据库模式
   */ // 删除模式会删除其中包含的所有对象（如果支持级联删除）
  public void execute(SqlDropSchema drop, // 方法重载，执行删除模式
      CalcitePrepare.Context context) { // 参数：删除模式语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取要删除的schema
        schema(context, false, drop.name); // 使用不可变schema获取信息
    final String name = pair.right; // 获取模式名称
    final boolean existed = pair.left != null // 判断schema是否存在
        && pair.left.removeSubSchema(name); // 尝试删除schema，返回是否成功
    if (!existed && !drop.ifExists) { // 如果schema不存在且没有IF EXISTS选项
      throw SqlUtil.newContextException(drop.name.getParserPosition(), // 抛出上下文异常
          RESOURCE.schemaNotFound(name)); // 错误信息：模式未找到
    }
  }

  /** Executes a {@code CREATE TABLE} command. // 方法注释：执行CREATE TABLE命令，创建新表
   */ // 支持两种方式：CREATE TABLE (列定义) 和 CREATE TABLE AS SELECT (查询)
  public void execute(SqlCreateTable create, // 方法重载，执行创建表
      CalcitePrepare.Context context) { // 参数：创建表语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取schema和表名
        schema(context, true, create.name); // 使用可变schema获取信息
    requireNonNull(pair.left); // 确保schema不为null
    // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
    final JavaTypeFactory typeFactory = context.getTypeFactory(); // 获取Java类型工厂
    final RelDataType queryRowType; // 声明查询行类型变量
    if (create.query != null) { // 如果有AS SELECT子句
      // A bit of a hack: pretend it's a view, to get its row type // 注释：有点hack，假装它是视图以获取行类型
      final String sql = // 将查询转换为SQL字符串
          create.query.toSqlString(CalciteSqlDialect.DEFAULT).getSql(); // 使用Calcite方言生成SQL
      final ViewTableMacro viewTableMacro = // 创建视图表宏
          ViewTable.viewMacro(pair.left.plus(), sql, pair.left.path(null), // 传入schema、SQL、路径
              context.getObjectPath(), false); // false表示不替换
      final TranslatableTable x = viewTableMacro.apply(ImmutableList.of()); // 应用宏获取可翻译表
      queryRowType = x.getRowType(typeFactory); // 获取查询的行类型

      if (create.columnList != null // 如果指定了列列表
          && queryRowType.getFieldCount() != create.columnList.size()) { // 且列数不匹配
        throw SqlUtil.newContextException( // 抛出上下文异常
            create.columnList.getParserPosition(), // 错误位置在列列表
            RESOURCE.columnCountMismatch()); // 错误信息：列数不匹配
      }
    } else { // 如果没有AS SELECT子句
      queryRowType = null; // 查询行类型为null
    }
    final List<SqlNode> columnList; // 声明列列表变量
    if (create.columnList != null) { // 如果指定了列列表
      columnList = create.columnList; // 使用指定的列列表
    } else { // 如果没有指定列列表
      if (queryRowType == null) { // 且也没有查询
        // "CREATE TABLE t" is invalid; because there is no "AS query" we need // 注释：CREATE TABLE t是无效的，因为没有AS查询
        // a list of column names and types, "CREATE TABLE t (INT c)". // 需要列名和类型列表
        throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.createTableRequiresColumnList()); // 错误信息：创建表需要列列表
      }
      columnList = new ArrayList<>(); // 从查询的行类型创建列列表
      for (String name : queryRowType.getFieldNames()) { // 遍历查询的所有字段名
        columnList.add(new SqlIdentifier(name, SqlParserPos.ZERO)); // 为每个字段创建标识符
      }
    }
    final ImmutableList.Builder<ColumnDef> b = ImmutableList.builder(); // 创建列定义列表构建器
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建数据类型构建器
    final RelDataTypeFactory.Builder storedBuilder = typeFactory.builder(); // 创建存储数据类型构建器
    // REVIEW 2019-08-19 Danny Chan: Should we implement the // 审查注释：是否应该实现validate(SqlValidator)方法
    // #validate(SqlValidator) to get the SqlValidator instance? // 以获取SqlValidator实例？
    final SqlValidator validator = validator(context, true); // 获取SQL验证器
    for (Ord<SqlNode> c : Ord.zip(columnList)) { // 遍历所有列，带索引
      if (c.e instanceof SqlColumnDeclaration) { // 如果是列声明
        final SqlColumnDeclaration d = (SqlColumnDeclaration) c.e; // 强制转换为列声明
        final RelDataType type = d.dataType.deriveType(validator, true); // 推导列的数据类型
        builder.add(d.name.getSimple(), type); // 添加到数据类型构建器
        if (d.strategy != ColumnStrategy.VIRTUAL) { // 如果列策略不是虚拟列
          storedBuilder.add(d.name.getSimple(), type); // 添加到存储数据类型构建器
        }
        b.add(ColumnDef.of(d.expression, type, d.strategy)); // 创建列定义并添加到列表
      } else if (c.e instanceof SqlIdentifier) { // 如果是简单标识符
        final SqlIdentifier id = (SqlIdentifier) c.e; // 强制转换为标识符
        if (queryRowType == null) { // 如果没有查询行类型
          throw SqlUtil.newContextException(id.getParserPosition(), // 抛出上下文异常
              RESOURCE.createTableRequiresColumnTypes(id.getSimple())); // 错误信息：创建表需要列类型
        }
        final RelDataTypeField f = queryRowType.getFieldList().get(c.i); // 获取对应字段
        final ColumnStrategy strategy = f.getType().isNullable() // 确定列策略
            ? ColumnStrategy.NULLABLE // 如果可为空，使用NULLABLE策略
            : ColumnStrategy.NOT_NULLABLE; // 否则使用NOT_NULLABLE策略
        b.add(ColumnDef.of(c.e, f.getType(), strategy)); // 创建列定义并添加到列表
        builder.add(id.getSimple(), f.getType()); // 添加到数据类型构建器
        storedBuilder.add(id.getSimple(), f.getType()); // 添加到存储数据类型构建器
      } else { // 其他类型
        throw new AssertionError(c.e.getClass()); // 抛出断言错误
      }
    }
    final RelDataType rowType = builder.build(); // 构建行类型
    final RelDataType storedRowType = storedBuilder.build(); // 构建存储行类型
    final List<ColumnDef> columns = b.build(); // 构建列定义列表
    final InitializerExpressionFactory ief = // 创建初始化表达式工厂
        new NullInitializerExpressionFactory() { // 匿名子类，提供自定义初始化逻辑
          @Override public ColumnStrategy generationStrategy(RelOptTable table, // 重写生成策略方法
              int iColumn) { // 参数：表、列索引
            return columns.get(iColumn).strategy; // 返回该列的存储策略
          }

          @Override public RexNode newColumnDefaultValue(RelOptTable table, // 重写列默认值方法
              int iColumn, InitializerContext context) { // 参数：表、列索引、初始化上下文
            final ColumnDef c = columns.get(iColumn); // 获取列定义
            if (c.expr != null) { // 如果列有表达式
              // REVIEW Danny 2019-10-09: Should we support validation for DDL nodes? // 审查注释：是否应该支持DDL节点的验证？
              final SqlNode validated = context.validateExpression(storedRowType, c.expr); // 验证表达式
              // The explicit specified type should have the same nullability // 注释：显式指定的类型应该具有相同的可空性
              // with the column expression inferred type, // 与列表达式推断的类型
              // actually they should be exactly the same. // 实际上它们应该完全相同
              return context.convertExpression(validated); // 转换表达式为RexNode
            }
            return super.newColumnDefaultValue(table, iColumn, context); // 调用父类的默认值方法
          }
        };
    if (pair.left.plus().tables().get(pair.right) != null) { // 检查表是否已存在
      // Table exists. // 表已存在
      if (create.ifNotExists) { // 如果有IF NOT EXISTS选项
        return; // 直接返回
      }
      if (!create.getReplace()) { // 如果没有REPLACE选项
        // They did not specify IF NOT EXISTS, so give error. // 用户没有指定IF NOT EXISTS，抛出错误
        throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.tableExists(pair.right)); // 错误信息：表已存在
      }
    }
    // Table does not exist. Create it. // 表不存在，创建它
    pair.left.add(pair.right, // 将表添加到schema中
        new MutableArrayTable(pair.right, // 创建可变数组表
            RelDataTypeImpl.proto(storedRowType), // 传入存储行类型原型
            RelDataTypeImpl.proto(rowType), ief)); // 传入行类型原型和初始化表达式工厂
    if (create.query != null) { // 如果有查询
      populate(create.name, create.query, context); // 填充表数据
    }
  }

  /** Executes a {@code CREATE TABLE LIKE} command. // 方法注释：执行CREATE TABLE LIKE命令，创建与现有表结构相同的新表
   */ // 支持复制表结构，可选择是否包含默认值和生成列
  public void execute(SqlCreateTableLike create, // 方法重载，执行创建类似表
      CalcitePrepare.Context context) { // 参数：创建类似表语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取schema和新表名
        schema(context, true, create.name); // 使用可变schema获取信息
    requireNonNull(pair.left); // 确保schema不为null
    // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
    if (pair.left.plus().tables().get(pair.right) != null) { // 检查表是否已存在
      // Table exists. // 表已存在
      if (create.ifNotExists) { // 如果有IF NOT EXISTS选项
        return; // 直接返回
      }
      if (!create.getReplace()) { // 如果没有REPLACE选项
        // They did not specify IF NOT EXISTS, so give error. // 用户没有指定IF NOT EXISTS，抛出错误
        throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.tableExists(pair.right)); // 错误信息：表已存在
      }
    }

    final Pair<@Nullable CalciteSchema, String> sourceTablePair = // 获取源表的信息
        schema(context, true, create.sourceTable); // 使用可变schema获取源表信息
    final CalciteSchema schema = // 获取源表的schema
        // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
        requireNonNull(sourceTablePair.left); // 确保schema不为null
    final String tableName = sourceTablePair.right; // 获取源表名
    final CalciteSchema.TableEntry tableEntry = // 获取源表的条目
        schema.getTable(tableName, context.config().caseSensitive()); // 根据表名和大小写敏感配置获取
    final Table table = requireNonNull(tableEntry, "tableEntry").getTable(); // 获取Table对象

    InitializerExpressionFactory ief = NullInitializerExpressionFactory.INSTANCE; // 默认使用空初始化表达式工厂
    if (table instanceof Wrapper) { // 如果表实现了Wrapper接口
      final InitializerExpressionFactory sourceIef = // 尝试获取源表的初始化表达式工厂
          ((Wrapper) table).unwrap(InitializerExpressionFactory.class); // 解包获取工厂实例
      if (sourceIef != null) { // 如果源表有初始化表达式工厂
        final Set<SqlCreateTableLike.LikeOption> optionSet = create.options(); // 获取LIKE选项集合
        final boolean includingGenerated = // 判断是否包含生成列
            optionSet.contains(SqlCreateTableLike.LikeOption.GENERATED) // 包含GENERATED选项
                || optionSet.contains(SqlCreateTableLike.LikeOption.ALL); // 或者包含ALL选项
        final boolean includingDefaults = // 判断是否包含默认值
            optionSet.contains(SqlCreateTableLike.LikeOption.DEFAULTS) // 包含DEFAULTS选项
                || optionSet.contains(SqlCreateTableLike.LikeOption.ALL); // 或者包含ALL选项

        // initializes columns based on the source Table InitializerExpressionFactory // 注释：基于源表的初始化表达式工厂初始化列
        // and like options. // 并根据LIKE选项进行配置
        ief = // 创建复制的表初始化表达式工厂
            new CopiedTableInitializerExpressionFactory( // 使用自定义的工厂类
                includingGenerated, includingDefaults, sourceIef); // 传入是否包含生成列、默认值和源工厂
      }
    }

    final JavaTypeFactory typeFactory = context.getTypeFactory(); // 获取Java类型工厂
    final RelDataType rowType = table.getRowType(typeFactory); // 获取源表的行类型
    // Table does not exist. Create it. // 表不存在，创建它
    pair.left.add(pair.right, // 将表添加到schema中
        new MutableArrayTable(pair.right, // 创建可变数组表
            RelDataTypeImpl.proto(rowType), // 传入行类型原型（存储和查询类型相同）
            RelDataTypeImpl.proto(rowType), ief)); // 传入行类型原型和初始化表达式工厂
  }

  /** Executes a {@code CREATE TYPE} command. // 方法注释：执行CREATE TYPE命令，创建自定义类型
   */ // 支持创建复合类型或基于现有数据类型的别名
  public void execute(SqlCreateType create, // 方法重载，执行创建类型
      CalcitePrepare.Context context) { // 参数：创建类型语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取schema和类型名称
        schema(context, true, create.name); // 使用可变schema获取信息
    requireNonNull(pair.left); // 确保schema不为null
    // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
    final SqlValidator validator = validator(context, false); // 获取SQL验证器
    pair.left.add(pair.right, typeFactory -> { // 将类型添加到schema中，使用lambda定义类型
      if (create.dataType != null) { // 如果指定了数据类型
        return create.dataType.deriveType(validator); // 推导并返回该数据类型
      } else { // 如果没有指定数据类型
        final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建数据类型构建器
        if (create.attributeDefs != null) { // 如果有属性定义
          for (SqlNode def : create.attributeDefs) { // 遍历所有属性定义
            final SqlAttributeDefinition attributeDef = // 强制转换为属性定义
                (SqlAttributeDefinition) def; // 转换节点类型
            final SqlDataTypeSpec typeSpec = attributeDef.dataType; // 获取数据类型规范
            final RelDataType type = typeSpec.deriveType(validator); // 推导属性的数据类型
            builder.add(attributeDef.name.getSimple(), type); // 将属性添加到构建器中
          }
        }
        return builder.build(); // 构建并返回复合类型
      }
    });
  }

  /** Executes a {@code CREATE VIEW} command. // 方法注释：执行CREATE VIEW命令，创建视图
   */ // 视图是虚拟表，基于查询结果，不存储实际数据
  public void execute(SqlCreateView create, // 方法重载，执行创建视图
      CalcitePrepare.Context context) { // 参数：创建视图语句、上下文
    final Pair<@Nullable CalciteSchema, String> pair = // 获取schema和视图名称
        schema(context, true, create.name); // 使用可变schema获取信息
    requireNonNull(pair.left); // 确保schema不为null
    // TODO: should not assume parent schema exists // TODO注释：不应该假设父schema存在
    final SchemaPlus schemaPlus = pair.left.plus(); // 获取扩展schema
    for (Function function : schemaPlus.getFunctions(pair.right)) { // 遍历同名函数
      if (function.getParameters().isEmpty()) { // 如果是无参函数（视图作为无参函数存储）
        if (!create.getReplace()) { // 如果没有REPLACE选项
          throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
              RESOURCE.viewExists(pair.right)); // 错误信息：视图已存在
        }
        pair.left.removeFunction(pair.right); // 删除同名函数
      }
    }
    final SqlNode q = renameColumns(create.columnList, create.query); // 重命名查询的列
    final String sql = q.toSqlString(CalciteSqlDialect.DEFAULT).getSql(); // 将查询转换为SQL字符串
    final ViewTableMacro viewTableMacro = // 创建视图表宏
        ViewTable.viewMacro(schemaPlus, sql, pair.left.path(null), // 传入schema、SQL、路径
            context.getObjectPath(), false); // false表示不替换
    final TranslatableTable x = viewTableMacro.apply(ImmutableList.of()); // 应用宏获取可翻译表
    Util.discard(x); // 丢弃结果（不使用），避免编译器警告
    schemaPlus.add(pair.right, viewTableMacro); // 将视图表宏添加到schema中
  }

  /**
   * Initializes columns based on the source {@link InitializerExpressionFactory} // 内部类注释：基于源初始化表达式工厂初始化列
   * and like options. // 根据CREATE TABLE LIKE的选项决定复制哪些列属性
   */
  private static class CopiedTableInitializerExpressionFactory // 私有静态内部类：复制的表初始化表达式工厂
      extends NullInitializerExpressionFactory { // 继承自空初始化表达式工厂

    private final boolean includingGenerated; // 成员变量：是否包含生成列
    private final boolean includingDefaults; // 成员变量：是否包含默认值
    private final InitializerExpressionFactory sourceIef; // 成员变量：源表的初始化表达式工厂

    CopiedTableInitializerExpressionFactory( // 构造函数：创建复制的表初始化表达式工厂
        boolean includingGenerated, // 参数：是否包含生成列
        boolean includingDefaults, // 参数：是否包含默认值
        InitializerExpressionFactory sourceIef) { // 参数：源表的初始化表达式工厂
      this.includingGenerated = includingGenerated; // 保存是否包含生成列标志
      this.includingDefaults = includingDefaults; // 保存是否包含默认值标志
      this.sourceIef = sourceIef; // 保存源表的初始化表达式工厂
    }

    @Override public ColumnStrategy generationStrategy( // 重写生成策略方法
        RelOptTable table, int iColumn) { // 参数：表、列索引
      final ColumnStrategy sourceStrategy = sourceIef.generationStrategy(table, iColumn); // 获取源列的策略
      if (includingGenerated // 如果包含生成列
          && (sourceStrategy == ColumnStrategy.STORED // 且源策略是STORED（存储的生成列）
          || sourceStrategy == ColumnStrategy.VIRTUAL)) { // 或VIRTUAL（虚拟的生成列）
        return sourceStrategy; // 返回源策略
      }
      if (includingDefaults && sourceStrategy == ColumnStrategy.DEFAULT) { // 如果包含默认值且源策略是DEFAULT
        return ColumnStrategy.DEFAULT; // 返回DEFAULT策略
      }

      return super.generationStrategy(table, iColumn); // 否则返回父类的默认策略
    }

    @Override public RexNode newColumnDefaultValue( // 重写列默认值方法
        RelOptTable table, int iColumn, InitializerContext context) { // 参数：表、列索引、初始化上下文

      if (includingDefaults || includingGenerated) { // 如果包含默认值或生成列
        return sourceIef.newColumnDefaultValue(table, iColumn, context); // 返回源表的默认值表达式
      } else { // 如果都不包含
        return super.newColumnDefaultValue(table, iColumn, context); // 返回父类的默认值
      }
    }
  }

  /** Column definition. // 内部类注释：列定义，用于存储列的元数据信息
   */ // 包含列的表达式、数据类型和存储策略
  private static class ColumnDef { // 私有静态内部类：列定义
    final @Nullable SqlNode expr; // 成员变量：列的表达式（可为null，表示没有默认值或生成表达式）
    final RelDataType type; // 成员变量：列的数据类型
    final ColumnStrategy strategy; // 成员变量：列的存储策略（NULLABLE、NOT_NULLABLE、DEFAULT、STORED、VIRTUAL）

    private ColumnDef(@Nullable SqlNode expr, RelDataType type, // 私有构造函数：创建列定义
        ColumnStrategy strategy) { // 参数：表达式、数据类型、存储策略
      this.expr = expr; // 保存表达式
      this.type = type; // 保存数据类型
      this.strategy = requireNonNull(strategy, "strategy"); // 保存策略，确保不为null
      checkArgument( // 检查参数有效性
          strategy == ColumnStrategy.NULLABLE // 如果策略是NULLABLE
              || strategy == ColumnStrategy.NOT_NULLABLE // 或NOT_NULLABLE
              || expr != null); // 或者表达式不为null，则通过检查
    }

    static ColumnDef of(@Nullable SqlNode expr, RelDataType type, // 静态工厂方法：创建列定义
        ColumnStrategy strategy) { // 参数：表达式、数据类型、存储策略
      return new ColumnDef(expr, type, strategy); // 返回新的列定义实例
    }
  }
} // 类结束