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
package org.apache.calcite.test; // 声明包名，该类属于org.apache.calcite.test包

import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂接口，用于创建Java相关的类型
import org.apache.calcite.jdbc.CalcitePrepare; // 导入Calcite准备接口，提供SQL准备和执行上下文
import org.apache.calcite.jdbc.CalciteSchema; // 导入Calcite模式类，表示数据库模式
import org.apache.calcite.jdbc.ContextSqlValidator; // 导入上下文SQL验证器，用于验证SQL语句
import org.apache.calcite.linq4j.Enumerator; // 导入枚举器接口，用于遍历查询结果
import org.apache.calcite.linq4j.Linq4j; // 导入Linq4j工具类，提供LINQ风格的集合操作
import org.apache.calcite.linq4j.QueryProvider; // 导入查询提供者接口，用于创建可查询对象
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可查询的数据源
import org.apache.calcite.linq4j.tree.Expression; // 导入表达式类，表示LINQ表达式树
import org.apache.calcite.rel.RelRoot; // 导入关系表达式根节点，表示查询的关系代数树
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示关系模型中的数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建数据类型
import org.apache.calcite.rel.type.RelDataTypeImpl; // 导入关系数据类型实现类
import org.apache.calcite.rel.type.RelProtoDataType; // 导入关系原型数据类型接口，用于延迟创建数据类型
import org.apache.calcite.schema.Function; // 导入函数接口，表示模式中的函数
import org.apache.calcite.schema.SchemaPlus; // 导入增强模式接口，提供额外的模式操作能力
import org.apache.calcite.schema.Schemas; // 导入模式工具类，提供模式相关的实用方法
import org.apache.calcite.schema.TranslatableTable; // 导入可翻译表接口，表可以被转换为关系表达式
import org.apache.calcite.schema.impl.AbstractTableQueryable; // 导入抽象表可查询类，提供表查询的基础实现
import org.apache.calcite.schema.impl.ViewTable; // 导入视图表类，表示数据库视图
import org.apache.calcite.schema.impl.ViewTableMacro; // 导入视图表宏类，用于创建视图表
import org.apache.calcite.server.DdlExecutorImpl; // 导入DDL执行器实现基类，提供DDL执行的基础功能
import org.apache.calcite.sql.SqlCall; // 导入SQL调用类，表示SQL函数调用或操作符调用
import org.apache.calcite.sql.SqlDataTypeSpec; // 导入SQL数据类型规范类，表示SQL数据类型定义
import org.apache.calcite.sql.SqlIdentifier; // 导入SQL标识符类，表示表名、列名等标识符
import org.apache.calcite.sql.SqlNode; // 导入SQL节点基类，表示SQL语法树的节点
import org.apache.calcite.sql.SqlNodeList; // 导入SQL节点列表类，表示SQL节点的有序集合
import org.apache.calcite.sql.SqlSelect; // 导入SQL SELECT语句类，表示查询语句
import org.apache.calcite.sql.SqlUtil; // 导入SQL工具类，提供SQL相关的实用方法
import org.apache.calcite.sql.ddl.SqlColumnDeclaration; // 导入SQL列声明类，表示CREATE TABLE中的列定义
import org.apache.calcite.sql.ddl.SqlCreateTable; // 导入SQL创建表语句类，表示CREATE TABLE语句
import org.apache.calcite.sql.ddl.SqlCreateView; // 导入SQL创建视图语句类，表示CREATE VIEW语句
import org.apache.calcite.sql.dialect.CalciteSqlDialect; // 导入Calcite SQL方言类，用于SQL的格式化和解析
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // 导入SQL标准操作符表，包含所有标准SQL操作符
import org.apache.calcite.sql.parser.SqlParseException; // 导入SQL解析异常类，表示SQL解析错误
import org.apache.calcite.sql.parser.SqlParserPos; // 导入SQL解析位置类，表示SQL语法元素的位置
import org.apache.calcite.sql.pretty.SqlPrettyWriter; // 导入SQL美化写入器，用于格式化SQL输出
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口，用于验证SQL语句
import org.apache.calcite.tools.FrameworkConfig; // 导入框架配置接口，配置Calcite框架
import org.apache.calcite.tools.Frameworks; // 导入框架工具类，用于创建框架实例
import org.apache.calcite.tools.Planner; // 导入规划器接口，负责SQL到关系代数的转换
import org.apache.calcite.tools.RelConversionException; // 导入关系转换异常类，表示关系代数转换错误
import org.apache.calcite.tools.ValidationException; // 导入验证异常类，表示SQL验证错误
import org.apache.calcite.util.Pair; // 导入键值对类，表示两个对象的组合
import org.apache.calcite.util.Util; // 导入通用工具类，提供各种实用方法

import com.google.common.collect.ImmutableList; // 导入Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.lang.reflect.Type; // 导入Java反射类型接口
import java.sql.PreparedStatement; // 导入JDBC预处理语句接口，用于执行参数化SQL
import java.sql.SQLException; // 导入SQL异常类，表示数据库操作错误
import java.util.ArrayList; // 导入动态数组列表类
import java.util.Collection; // 导入集合接口
import java.util.List; // 导入列表接口
import java.util.function.BiConsumer; // 导入双参数消费者函数式接口

import static org.apache.calcite.util.Static.RESOURCE; // 静态导入资源常量，用于错误消息

import static java.util.Objects.requireNonNull; // 静态导入对象非空检查方法

/** Executes the few DDL commands. */ // 类注释：MockDdlExecutor类用于执行少量DDL（数据定义语言）命令，主要用于测试环境
public class MockDdlExecutor extends DdlExecutorImpl { // MockDdlExecutor类继承自DdlExecutorImpl，提供DDL命令的模拟执行功能

  /** Returns the schema in which to create an object. */ // 方法注释：schema方法返回要在其中创建对象的schema（模式）
  static Pair<CalciteSchema, String> schema(CalcitePrepare.Context context, // 参数：context表示Calcite准备上下文，包含schema路径等信息
      boolean mutable, // 参数：mutable布尔值，表示是否返回可变的schema
      SqlIdentifier id) { // 参数：id表示对象的SQL标识符（如表名或视图名）
    final String name; // 声明最终变量name，用于存储对象的名称
    final List<String> path; // 声明最终变量path，用于存储schema的路径
    if (id.isSimple()) { // 判断标识符是否是简单标识符（即没有schema前缀的名称）
      path = context.getDefaultSchemaPath(); // 如果是简单标识符，使用默认的schema路径
      name = id.getSimple(); // 获取简单标识符的名称部分
    } else { // 如果标识符不是简单标识符（包含schema路径）
      path = Util.skipLast(id.names); // 获取标识符中除最后一个元素外的所有元素作为schema路径
      name = Util.last(id.names); // 获取标识符的最后一个元素作为对象名称
    }
    CalciteSchema schema = // 声明CalciteSchema变量schema
        mutable ? context.getMutableRootSchema() // 如果mutable为true，获取可变的根schema
            : context.getRootSchema(); // 否则获取不可变的根schema
    for (String p : path) { // 遍历schema路径中的每个部分
      schema = requireNonNull(schema.getSubSchema(p, true)); // 获取子schema，要求子schema必须存在
    }
    return Pair.of(schema, name); // 返回包含schema和对象名称的键值对
  }

  /** Wraps a query to rename its columns. Used by CREATE VIEW and CREATE
   * MATERIALIZED VIEW. */ // 方法注释：renameColumns方法包装查询以重命名其列，用于CREATE VIEW和CREATE MATERIALIZED VIEW语句
  static SqlNode renameColumns(@Nullable SqlNodeList columnList, // 参数：columnList表示要重命名的列列表，可为null
      SqlNode query) { // 参数：query表示要包装的查询节点
    if (columnList == null) { // 如果列列表为null
      return query; // 直接返回原查询，不做任何包装
    }
    final SqlParserPos p = query.getParserPosition(); // 获取查询的解析位置信息
    final SqlNodeList selectList = SqlNodeList.SINGLETON_STAR; // 创建包含单个星号的SELECT列表，表示选择所有列
    final SqlCall from = // 声明SQL调用节点，用于构建FROM子句
        SqlStdOperatorTable.AS.createCall(p, // 使用AS操作符创建调用，用于给查询起别名
            ImmutableList.<SqlNode>builder() // 使用构建器创建不可变列表
                .add(query) // 添加原始查询
                .add(new SqlIdentifier("_", p)) // 添加下划线作为表别名
                .addAll(columnList) // 添加所有列名作为列别名
                .build()); // 构建不可变列表
    return new SqlSelect(p, null, selectList, from, null, null, null, null, // 创建新的SELECT语句，使用AS操作符重命名列
        null, null, null, null, null); // SELECT语句的其他参数都设为null
  }

  /** Executes a {@code CREATE TABLE} command. Called via reflection. */ // 方法注释：execute方法执行CREATE TABLE命令，通过反射调用
  public void execute(SqlCreateTable create, CalcitePrepare.Context context) { // 参数：create表示CREATE TABLE语句，context表示准备上下文
    final CalciteSchema schema = // 获取目标schema
        Schemas.subSchema(context.getRootSchema(), // 从根schema开始
            context.getDefaultSchemaPath()); // 使用默认schema路径导航到目标schema
    requireNonNull(schema, "schema"); // 确保schema不为null，否则抛出异常
    final JavaTypeFactory typeFactory = context.getTypeFactory(); // 从上下文中获取Java类型工厂
    final RelDataType queryRowType; // 声明查询行类型变量，用于存储查询结果的行类型
    if (create.query != null) { // 如果CREATE TABLE语句包含AS查询子句（即通过查询创建表）
      // A bit of a hack: pretend it's a view, to get its row type // 注释：这是一个技巧，假装是视图来获取行类型
      final String sql = // 将查询节点转换为SQL字符串
          create.query.toSqlString(CalciteSqlDialect.DEFAULT).getSql(); // 使用Calcite默认方言进行转换
      final ViewTableMacro viewTableMacro = // 创建视图表宏
          ViewTable.viewMacro(schema.plus(), sql, schema.path(null), // 使用schema、SQL和路径创建视图宏
              context.getObjectPath(), false); // 使用对象路径，不缓存
      final TranslatableTable x = viewTableMacro.apply(ImmutableList.of()); // 应用宏创建可翻译表
      queryRowType = x.getRowType(typeFactory); // 从可翻译表获取行类型

      if (create.columnList != null // 如果CREATE TABLE语句指定了列列表
          && queryRowType.getFieldCount() != create.columnList.size()) { // 并且列数与查询结果的列数不匹配
        throw SqlUtil.newContextException(create.columnList.getParserPosition(), // 抛出上下文异常
            RESOURCE.columnCountMismatch()); // 使用列数不匹配的错误消息
      }
    } else { // 如果CREATE TABLE语句不包含AS查询子句
      queryRowType = null; // 将查询行类型设为null
    }
    final RelDataTypeFactory.Builder builder = typeFactory.builder(); // 创建关系数据类型构建器
    if (create.columnList != null) { // 如果CREATE TABLE语句指定了列列表
      final SqlValidator validator = new ContextSqlValidator(context, false); // 创建上下文SQL验证器
      forEachNameType(create, (name, typeSpec) -> // 遍历每个列名和类型规范
          builder.add(name.getSimple(), typeSpec.deriveType(validator, true))); // 将列名和推导出的类型添加到构建器
    } else { // 如果CREATE TABLE语句没有指定列列表
      if (queryRowType == null) { // 并且也没有查询行类型
        // "CREATE TABLE t" is invalid; because there is no "AS query" we need // 注释：CREATE TABLE t是无效的，因为没有AS查询子句
        // a list of column names and types, "CREATE TABLE t (INT c)". // 注释：需要列名和类型列表，如CREATE TABLE t (INT c)
        throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
            RESOURCE.createTableRequiresColumnList()); // 使用需要列列表的错误消息
      }
      builder.addAll(queryRowType.getFieldList()); // 将查询行类型的所有字段添加到构建器
    }
    final RelDataType rowType = builder.build(); // 构建最终的行类型
    schema.add(create.name.getSimple(), // 在schema中添加表，使用表名作为键
        new MutableArrayTable(create.name.getSimple(), // 创建可变数组表实例，使用表名
            RelDataTypeImpl.proto(rowType))); // 使用行类型的原型
    if (create.query != null) { // 如果CREATE TABLE语句包含AS查询子句
      populate(create.name, create.query, context); // 调用populate方法，通过查询填充表数据
    }
  }

  /** Executes a {@code CREATE VIEW} command. */ // 方法注释：execute方法执行CREATE VIEW命令
  public void execute(SqlCreateView create, // 参数：create表示CREATE VIEW语句
      CalcitePrepare.Context context) { // 参数：context表示准备上下文
    final Pair<CalciteSchema, String> pair = // 获取schema和视图名称的键值对
        schema(context, true, create.name); // 使用可变schema和视图名称调用schema方法
    final SchemaPlus schemaPlus = pair.left.plus(); // 获取增强的schema对象
    for (Function function : schemaPlus.getFunctions(pair.right)) { // 遍历schema中同名函数（视图作为函数存储）
      if (function.getParameters().isEmpty()) { // 如果函数没有参数（视图是无参数函数）
        if (!create.getReplace()) { // 并且CREATE VIEW语句没有使用OR REPLACE选项
          throw SqlUtil.newContextException(create.name.getParserPosition(), // 抛出上下文异常
              RESOURCE.viewExists(pair.right)); // 使用视图已存在的错误消息
        }
        pair.left.removeFunction(pair.right); // 如果有OR REPLACE选项，删除旧的视图函数
      }
    }
    final SqlNode q = renameColumns(create.columnList, create.query); // 重命名查询的列（如果指定了列列表）
    final String sql = q.toSqlString(CalciteSqlDialect.DEFAULT).getSql(); // 将查询节点转换为SQL字符串
    final ViewTableMacro viewTableMacro = // 创建视图表宏
        ViewTable.viewMacro(schemaPlus, sql, pair.left.path(null), // 使用schema、SQL和路径创建视图宏
            context.getObjectPath(), false); // 使用对象路径，不缓存
    final TranslatableTable x = viewTableMacro.apply(ImmutableList.of()); // 应用宏创建可翻译表
    Util.discard(x); // 丢弃结果，这是为了触发视图的初始化
    schemaPlus.add(pair.right, viewTableMacro); // 在schema中添加视图表宏
  }

  /** Populates the table called {@code name} by executing {@code query}. */ // 方法注释：populate方法通过执行查询来填充指定名称的表
  protected static void populate(SqlIdentifier name, SqlNode query, // 参数：name表示表名，query表示查询节点
      CalcitePrepare.Context context) { // 参数：context表示准备上下文
    // Generate, prepare and execute an "INSERT INTO table query" statement. // 注释：生成、准备并执行INSERT INTO table query语句
    // (It's a bit inefficient that we convert from SqlNode to SQL and back // 注释：将SqlNode转换为SQL再转回有些低效
    // again.) // 注释：但这是必要的步骤
    final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
        .defaultSchema( // 设置默认schema
            requireNonNull( // 确保schema不为null
                Schemas.subSchema(context.getRootSchema(), // 获取子schema
                    context.getDefaultSchemaPath())).plus()) // 转换为增强schema
        .build(); // 构建框架配置
    final Planner planner = Frameworks.getPlanner(config); // 使用配置获取规划器实例
    try { // 开始try块，处理可能的异常
      final StringBuilder buf = new StringBuilder(); // 创建字符串构建器，用于构建SQL语句
      final SqlPrettyWriter w = // 创建SQL美化写入器
          new SqlPrettyWriter( // 初始化写入器
              SqlPrettyWriter.config() // 获取默认配置
                  .withDialect(CalciteSqlDialect.DEFAULT) // 设置使用Calcite默认方言
                  .withAlwaysUseParentheses(false), // 不总是使用括号
              buf); // 使用字符串构建器作为输出目标
      buf.append("INSERT INTO "); // 追加INSERT INTO关键字
      name.unparse(w, 0, 0); // 将表名解析并追加到字符串构建器
      buf.append(" "); // 追加空格
      query.unparse(w, 0, 0); // 将查询解析并追加到字符串构建器
      final String sql = buf.toString(); // 获取完整的SQL字符串
      final SqlNode query1 = planner.parse(sql); // 使用规划器解析SQL语句
      final SqlNode query2 = planner.validate(query1); // 使用规划器验证解析后的SQL节点
      final RelRoot r = planner.rel(query2); // 将验证后的SQL节点转换为关系代数树
      final PreparedStatement prepare = // 准备JDBC预处理语句
          context.getRelRunner().prepareStatement(r.rel); // 使用关系运行器准备执行关系表达式
      int rowCount = prepare.executeUpdate(); // 执行更新并获取影响的行数
      Util.discard(rowCount); // 丢弃行数（不使用）
      prepare.close(); // 关闭预处理语句
    } catch (SqlParseException | ValidationException // 捕获SQL解析异常和验证异常
             | RelConversionException | SQLException e) { // 捕获关系转换异常和SQL异常
      throw Util.throwAsRuntime(e); // 将异常作为运行时异常抛出
    }
  }

  /** Calls an action for each (name, type) pair from {@code SqlCreateTable::columnList}, in which
   * they alternate. */ // 方法注释：forEachNameType方法对SqlCreateTable的columnList中的每个(名称,类型)对执行操作
  @SuppressWarnings({"unchecked", "rawtypes"}) // 抑制未检查和原始类型警告
  protected void forEachNameType(SqlCreateTable createTable, // 参数：createTable表示CREATE TABLE语句
      BiConsumer<SqlIdentifier, SqlDataTypeSpec> consumer) { // 参数：consumer表示双参数消费者，接收列名和类型规范
    requireNonNull(createTable.columnList).forEach(sqlNode -> { // 确保列列表不为null，然后遍历每个SQL节点
      if (sqlNode instanceof SqlColumnDeclaration) { // 如果节点是列声明类型
        final SqlColumnDeclaration d = (SqlColumnDeclaration) sqlNode; // 将节点转换为列声明
        consumer.accept(d.name, d.dataType); // 调用消费者，传入列名和数据类型
      } else { // 如果节点不是列声明类型
        throw new AssertionError(sqlNode.getClass()); // 抛出断言错误，表示意外的节点类型
      }
    });
  }

  /** Table backed by a Java list. */ // 内部类注释：MutableArrayTable是内部类，表示由Java列表支持的表
  private static class MutableArrayTable // 声明私有静态内部类MutableArrayTable
      extends AbstractModifiableTable { // 继承自AbstractModifiableTable抽象类，表示可修改的表
    final List list = new ArrayList(); // 成员变量：list是存储表数据的ArrayList，每行数据作为一个元素
    private final RelProtoDataType protoRowType; // 成员变量：protoRowType是行类型的原型，用于延迟创建行类型

    MutableArrayTable(String name, RelProtoDataType protoRowType) { // 构造方法：接收表名和行类型原型
      super(name); // 调用父类构造方法，传入表名
      this.protoRowType = protoRowType; // 保存行类型原型
    }

    @Override public Collection getModifiableCollection() { // 重写方法：返回可修改的集合
      return list; // 返回内部ArrayList，允许外部修改表数据
    }

    @Override public <T> Queryable<T> asQueryable(QueryProvider queryProvider, // 重写方法：将表转换为可查询对象
        SchemaPlus schema, String tableName) { // 参数：queryProvider是查询提供者，schema是模式，tableName是表名
      return new AbstractTableQueryable<T>(queryProvider, schema, this, // 创建抽象表可查询对象
          tableName) { // 使用表名
        @Override public Enumerator<T> enumerator() { // 重写方法：返回枚举器用于遍历查询结果
          //noinspection unchecked // 抑制未检查转换警告
          return (Enumerator<T>) Linq4j.enumerator(list); // 将列表转换为枚举器
        }
      };
    }

    @Override public Type getElementType() { // 重写方法：返回元素的Java类型
      return Object[].class; // 返回Object数组类型，每行数据是一个对象数组
    }

    @Override public Expression getExpression(SchemaPlus schema, String tableName, // 重写方法：获取表的LINQ表达式
        Class clazz) { // 参数：schema是模式，tableName是表名，clazz是类类型
      return Schemas.tableExpression(schema, getElementType(), // 使用Schemas工具创建表表达式
          tableName, clazz); // 传入元素类型、表名和类
    }

    @Override public RelDataType getRowType(RelDataTypeFactory typeFactory) { // 重写方法：获取表的行类型
      return protoRowType.apply(typeFactory); // 使用行类型原型和类型工厂创建实际的行类型
    }
  }
}
