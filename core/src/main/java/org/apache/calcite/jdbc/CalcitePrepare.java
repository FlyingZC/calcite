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
package org.apache.calcite.jdbc; // 声明包名，该类位于 org.apache.calcite.jdbc 包下，是 Calcite JDBC 层的核心接口

import org.apache.calcite.DataContext; // 导入数据上下文接口，用于在查询执行时传递运行时数据
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入Java类型工厂，用于Java类型和SQL类型之间的转换
import org.apache.calcite.avatica.AvaticaParameter; // 导入Avatica参数类，用于描述SQL语句中的参数
import org.apache.calcite.avatica.ColumnMetaData; // 导入列元数据类，用于描述查询结果集的列信息
import org.apache.calcite.avatica.Meta; // 导入Avatica元数据接口，提供JDBC驱动框架所需的核心功能
import org.apache.calcite.config.CalciteConnectionConfig; // 导入Calcite连接配置接口，用于获取连接级别的配置信息
import org.apache.calcite.linq4j.Enumerable; // 导入可枚举接口，表示可以被遍历的数据集合
import org.apache.calcite.linq4j.EnumerableDefaults; // 导入可枚举默认实现类，提供常用的LINQ操作方法
import org.apache.calcite.linq4j.Queryable; // 导入可查询接口，表示可以被查询的数据源
import org.apache.calcite.linq4j.function.Function0; // 导入无参函数接口，用于延迟初始化等场景
import org.apache.calcite.linq4j.tree.ClassDeclaration; // 导入类声明类，用于表示Java类的声明
import org.apache.calcite.plan.RelOptPlanner; // 导入关系表达式优化器接口，用于优化查询计划
import org.apache.calcite.plan.RelOptRule; // 导入关系表达式优化规则接口，定义优化规则
import org.apache.calcite.prepare.CalcitePrepareImpl; // 导入Calcite准备实现类，提供该接口的默认实现
import org.apache.calcite.rel.RelCollation; // 导入关系排序类，描述结果的排序方式
import org.apache.calcite.rel.RelNode; // 导入关系节点接口，表示关系代数表达式
import org.apache.calcite.rel.RelRoot; // 导入关系根节点类，表示优化后的查询计划根节点
import org.apache.calcite.rel.type.RelDataType; // 导入关系数据类型接口，表示SQL数据类型
import org.apache.calcite.rel.type.RelDataTypeFactory; // 导入关系数据类型工厂接口，用于创建和管理数据类型
import org.apache.calcite.rex.RexNode; // 导入行表达式节点接口，表示行级别的表达式
import org.apache.calcite.runtime.ArrayBindable; // 导入数组可绑定接口，用于绑定参数并返回数组结果
import org.apache.calcite.runtime.Bindable; // 导入可绑定接口，用于绑定参数并执行查询
import org.apache.calcite.schema.Table; // 导入表接口，表示数据库表
import org.apache.calcite.sql.SqlKind; // 导入SQL种类枚举，定义SQL语句的类型（如SELECT、INSERT等）
import org.apache.calcite.sql.SqlNode; // 导入SQL节点接口，表示SQL抽象语法树中的节点
import org.apache.calcite.sql.validate.CyclicDefinitionException; // 导入循环定义异常类，用于检测视图的循环引用
import org.apache.calcite.sql.validate.SqlValidator; // 导入SQL验证器接口，用于验证SQL语句的语义正确性
import org.apache.calcite.tools.RelRunner; // 导入关系运行器接口，用于执行关系表达式
import org.apache.calcite.util.ImmutableIntList; // 导入不可变整数列表类，用于存储整数序列
import org.apache.calcite.util.TryThreadLocal; // 导入尝试线程本地类，用于线程本地存储

import com.fasterxml.jackson.annotation.JsonIgnore; // 导入Jackson忽略注解，用于在序列化时忽略某些字段
import com.google.common.collect.ImmutableList; // 导入Guava不可变列表类，提供不可变的列表实现

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的值

import java.lang.reflect.InvocationTargetException; // 导入反射调用目标异常类
import java.lang.reflect.Method; // 导入方法类，用于通过反射调用方法
import java.lang.reflect.Type; // 导入类型接口，表示Java类型
import java.util.ArrayDeque; // 导入数组双端队列类，用于实现栈结构
import java.util.Deque; // 导入双端队列接口
import java.util.List; // 导入列表接口
import java.util.Map; // 导入映射接口

import static com.google.common.base.Preconditions.checkArgument; // 导入Guava前置条件检查方法，用于参数验证

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入非空转换方法，用于将可能为null的值转换为非null

import static java.util.Objects.requireNonNull; // 导入对象非空检查方法，用于验证对象不为null

public interface CalcitePrepare { // 定义CalcitePrepare接口，提供SQL语句准备和执行的核心功能
  Function0<CalcitePrepare> DEFAULT_FACTORY = CalcitePrepareImpl::new; // 默认工厂方法，用于创建CalcitePrepare实例，指向默认实现类CalcitePrepareImpl
  TryThreadLocal<Deque<Context>> THREAD_CONTEXT_STACK = // 线程本地变量，存储当前线程的上下文栈，用于跟踪嵌套的SQL解析上下文
      TryThreadLocal.withInitial(ArrayDeque::new); // 初始化为空的ArrayDeque，用于实现栈结构

  ParseResult parse(Context context, String sql); // 解析SQL语句，返回解析结果，包括SQL节点和行类型信息

  ConvertResult convert(Context context, String sql); // 将SQL语句转换为关系代数表达式，返回转换结果

  void executeDdl(Context context, SqlNode node); // 执行DDL（数据定义语言）语句，如CREATE、DROP、ALTER等

  AnalyzeViewResult analyzeView(Context context, String sql, boolean fail); // 分析视图，检查视图是否可修改并返回分析结果，fail参数控制当视图不可修改时是否抛出异常

  <T> CalciteSignature<T> prepareSql( // 准备SQL查询，生成可执行的查询签名，T是结果元素类型
      Context context, // 准备上下文，包含类型工厂、schema等配置信息
      Query<T> query, // 查询对象，可以是SQL字符串、Queryable或RelNode
      Type elementType, // 结果元素的Java类型
      long maxRowCount); // 最大返回行数，用于限制结果集大小

  <T> CalciteSignature<T> prepareQueryable( // 准备Queryable查询，生成可执行的查询签名，T是结果元素类型
      Context context, // 准备上下文，包含类型工厂、schema等配置信息
      Queryable<T> queryable); // 可查询对象，表示LINQ风格的数据源

  /** Context for preparing a statement. */
  interface Context { // 定义内部接口Context，表示SQL语句准备时的上下文环境，包含类型工厂、schema、配置等信息
    JavaTypeFactory getTypeFactory(); // 获取Java类型工厂，用于Java类型和SQL类型之间的转换

    CalciteSchema getRootSchema(); // 获取根schema，用于需要读一致快照的语句（如SELECT查询），返回不可变的schema视图

    CalciteSchema getMutableRootSchema(); // 获取可变的根schema，用于需要修改schema并将结果对其他语句可见的语句，如DDL语句

    List<String> getDefaultSchemaPath(); // 获取默认schema路径，用于解析未限定的表名

    CalciteConnectionConfig config(); // 获取Calcite连接配置，包含连接级别的配置参数

    SparkHandler spark(); // 获取Spark处理器，用于与Spark集成，返回值从不为null

    DataContext getDataContext(); // 获取数据上下文，用于在查询执行时传递运行时数据（如变量、参数等）

    @Nullable List<String> getObjectPath(); // 获取正在分析的对象路径（如视图路径），如果不在分析视图则返回null；用于检测循环定义

    RelRunner getRelRunner(); // 获取关系运行器，用于执行关系表达式
  }

  /** Callback to register Spark as the main engine. */
  interface SparkHandler { // 定义内部接口SparkHandler，用于与Spark引擎集成，提供Spark特定的优化和执行功能
    RelNode flattenTypes(RelOptPlanner planner, RelNode rootRel, // 展平类型，将嵌套的类型结构展平为扁平结构，planner是优化器，rootRel是根关系节点
        boolean restructure); // 是否重组结构，true表示需要重组

    void registerRules(RuleSetBuilder builder); // 注册Spark需要的优化规则到规则构建器中

    boolean enabled(); // 检查Spark是否启用，返回true表示Spark功能可用

    ArrayBindable compile(ClassDeclaration expr, String s); // 编译类声明为可执行的ArrayBindable，expr是类声明，s是源代码字符串

    Object sparkContext(); // 获取Spark上下文对象，返回Spark的SparkContext或SQLContext实例

    interface RuleSetBuilder { // 定义内部接口RuleSetBuilder，用于构建规则集，允许Spark声明它需要的规则
      void addRule(RelOptRule rule); // 添加优化规则到规则集

      void removeRule(RelOptRule rule); // 从规则集中移除优化规则
    }
  }

  /** Namespace that allows us to define non-abstract methods inside an
   * interface. */
  class Dummy { // 定义内部类Dummy，作为命名空间，允许在接口中定义非抽象方法
    private static @Nullable SparkHandler sparkHandler; // 静态成员变量，缓存Spark处理器实例，使用可空注解表示可能为null

    private Dummy() {} // 私有构造函数，防止实例化，该类只作为工具类使用

    /** Returns a spark handler. Returns a trivial handler, for which
     * {@link SparkHandler#enabled()} returns {@code false}, if {@code enable}
     * is {@code false} or if Spark is not on the class path. Never returns
     * null. */
    public static synchronized SparkHandler getSparkHandler(boolean enable) { // 获取Spark处理器，使用synchronized保证线程安全，enable参数控制是否启用Spark
      if (sparkHandler == null) { // 如果Spark处理器尚未初始化
        sparkHandler = enable ? createHandler() : new TrivialSparkHandler(); // 如果启用则创建真实的Spark处理器，否则创建空的处理器
      }
      return sparkHandler; // 返回Spark处理器实例
    }

    private static SparkHandler createHandler() { // 创建Spark处理器实例，使用反射机制动态加载
      try {
        final Class<?> clazz = // 加载Spark处理器实现类
            Class.forName("org.apache.calcite.adapter.spark.SparkHandlerImpl"); // 通过类名加载类
        Method method = clazz.getMethod("instance"); // 获取静态方法instance
        return (CalcitePrepare.SparkHandler) requireNonNull( // 调用instance方法获取实例并确保不为null
            method.invoke(null), // 调用静态方法，传入null作为对象
            () -> "non-null SparkHandler expected from " + method); // 如果返回null则抛出异常
      } catch (ClassNotFoundException e) { // 捕获类未找到异常，表示Spark不在类路径中
        return new TrivialSparkHandler(); // 返回空的Spark处理器
      } catch (IllegalAccessException // 捕获非法访问异常
          | ClassCastException // 捕获类型转换异常
          | InvocationTargetException // 捕获调用目标异常
          | NoSuchMethodException e) { // 捕获方法未找到异常
        throw new RuntimeException(e); // 包装为运行时异常抛出
      }
    }

    public static void push(Context context) { // 将上下文推入栈中，用于跟踪嵌套的SQL解析上下文
      final Deque<Context> stack = THREAD_CONTEXT_STACK.get(); // 获取当前线程的上下文栈
      final List<String> path = context.getObjectPath(); // 获取当前上下文的对象路径
      if (path != null) { // 如果对象路径不为null（表示正在分析视图）
        for (Context context1 : stack) { // 遍历栈中的所有上下文
          final List<String> path1 = context1.getObjectPath(); // 获取栈中上下文的对象路径
          if (path.equals(path1)) { // 如果路径相同，表示检测到循环定义
            throw new CyclicDefinitionException(stack.size(), path); // 抛出循环定义异常
          }
        }
      }
      stack.push(context); // 将当前上下文推入栈顶
    }

    public static Context peek() { // 查看栈顶的上下文但不弹出，用于获取当前活动的上下文
      final Deque<Context> stack = THREAD_CONTEXT_STACK.get(); // 获取当前线程的上下文栈
      return castNonNull(stack.peek()); // 返回栈顶元素，使用castNonNull确保返回值不为null
    }

    public static void pop(Context context) { // 从栈中弹出上下文，用于结束当前的SQL解析上下文
      final Deque<Context> stack = THREAD_CONTEXT_STACK.get(); // 获取当前线程的上下文栈
      Context x = castNonNull(stack).pop(); // 弹出栈顶元素，使用castNonNull确保返回值不为null
      assert x == context; // 断言弹出的元素与传入的上下文相同，用于调试
    }

    /** Implementation of {@link SparkHandler} that either does nothing or
     * throws for each method. Use this if Spark is not installed. */
    private static class TrivialSparkHandler implements SparkHandler { // 定义内部类TrivialSparkHandler，实现SparkHandler接口，提供空的实现
      @Override public RelNode flattenTypes(RelOptPlanner planner, RelNode rootRel, // 展平类型方法，空实现直接返回原始节点
          boolean restructure) { // restructure参数被忽略
        return rootRel; // 不做任何处理，直接返回原始的关系节点
      }

      @Override public void registerRules(RuleSetBuilder builder) { // 注册规则方法，空实现不注册任何规则
      }

      @Override public boolean enabled() { // 检查Spark是否启用方法
        return false; // 返回false表示Spark功能不可用
      }

      @Override public ArrayBindable compile(ClassDeclaration expr, String s) { // 编译方法，空实现抛出异常
        throw new UnsupportedOperationException(); // 抛出不支持操作异常
      }

      @Override public Object sparkContext() { // 获取Spark上下文方法，空实现抛出异常
        throw new UnsupportedOperationException(); // 抛出不支持操作异常
      }
    }

  }

  /** The result of parsing and validating a SQL query. */
  class ParseResult { // 定义内部类ParseResult，表示SQL解析和验证的结果，包含SQL节点和类型信息
    public final CalcitePrepareImpl prepare; // 准备实现对象，用于后续的转换和执行
    public final String sql; // 原始SQL字符串，用于调试和错误报告
    public final SqlNode sqlNode; // 解析后的SQL节点，表示SQL抽象语法树
    public final RelDataType rowType; // 结果行类型，描述查询结果集的列信息（列名、类型等）
    public final RelDataTypeFactory typeFactory; // 类型工厂，用于创建和操作数据类型

    public ParseResult(CalcitePrepareImpl prepare, SqlValidator validator, // 构造函数，创建解析结果对象
        String sql, // 原始SQL字符串
        SqlNode sqlNode, // 解析后的SQL节点
        RelDataType rowType) { // 结果行类型
      super(); // 调用父类构造函数
      this.prepare = prepare; // 保存准备实现对象
      this.sql = sql; // 保存原始SQL字符串
      this.sqlNode = sqlNode; // 保存SQL节点
      this.rowType = rowType; // 保存结果行类型
      this.typeFactory = validator.getTypeFactory(); // 从验证器获取类型工厂
    }

    public SqlKind kind() { // 获取SQL语句的种类，用于区分查询、DML、DDL等不同类型的语句
      return sqlNode.getKind(); // 返回SQL节点的种类，包括：查询(SELECT、UNION等)、DML(INSERT、UPDATE等)、会话控制(COMMIT等)、DDL(CREATE_TABLE、DROP_INDEX等)
    }
  }

  /** The result of parsing and validating a SQL query and converting it to
   * relational algebra. */
  class ConvertResult extends ParseResult { // 定义内部类ConvertResult，继承自ParseResult，表示SQL转换为关系代数后的结果
    public final RelRoot root; // 关系根节点，包含优化后的查询计划

    public ConvertResult(CalcitePrepareImpl prepare, SqlValidator validator, // 构造函数，创建转换结果对象
        String sql, // 原始SQL字符串
        SqlNode sqlNode, // 解析后的SQL节点
        RelDataType rowType, // 结果行类型
        RelRoot root) { // 关系根节点，包含优化后的查询计划
      super(prepare, validator, sql, sqlNode, rowType); // 调用父类构造函数初始化继承的字段
      this.root = root; // 保存关系根节点
    }
  }

  /** The result of analyzing a view. */
  class AnalyzeViewResult extends ConvertResult { // 定义内部类AnalyzeViewResult，继承自ConvertResult，表示视图分析的结果
    public final @Nullable Table table; // 底层表对象，当且仅当视图可修改时不为null
    public final @Nullable ImmutableList<String> tablePath; // 底层表路径，用于定位视图映射的表
    public final @Nullable RexNode constraint; // 约束条件，表示视图的WHERE子句或其他约束
    public final @Nullable ImmutableIntList columnMapping; // 列映射关系，表示视图列到表列的映射
    public final boolean modifiable; // 视图是否可修改的标志

    public AnalyzeViewResult(CalcitePrepareImpl prepare, // 构造函数，创建视图分析结果对象
        SqlValidator validator, // SQL验证器
        String sql, // 原始SQL字符串
        SqlNode sqlNode, // 解析后的SQL节点
        RelDataType rowType, // 结果行类型
        RelRoot root, // 关系根节点
        @Nullable Table table, // 底层表对象
        @Nullable ImmutableList<String> tablePath, // 底层表路径
        @Nullable RexNode constraint, // 约束条件
        @Nullable ImmutableIntList columnMapping, // 列映射关系
        boolean modifiable) { // 视图是否可修改
      super(prepare, validator, sql, sqlNode, rowType, root); // 调用父类构造函数初始化继承的字段
      this.table = table; // 保存底层表对象
      this.tablePath = tablePath; // 保存底层表路径
      this.constraint = constraint; // 保存约束条件
      this.columnMapping = columnMapping; // 保存列映射关系
      this.modifiable = modifiable; // 保存可修改标志
      checkArgument(modifiable == (table != null)); // 验证可修改标志与表对象的一致性
    }
  }

  /** The result of preparing a query. It gives the Avatica driver framework
   * the information it needs to create a prepared statement, or to execute a
   * statement directly, without an explicit prepare step.
   *
   * @param <T> element type */
  class CalciteSignature<T> extends Meta.Signature { // 定义内部类CalciteSignature，继承自Avatica的Signature类，表示准备好的查询签名
    @JsonIgnore public final @Nullable RelDataType rowType; // 结果行类型，使用JsonIgnore避免序列化
    @JsonIgnore public final @Nullable CalciteSchema rootSchema; // 根schema对象，使用JsonIgnore避免序列化
    @JsonIgnore private final List<RelCollation> collationList; // 排序列表，描述结果的排序方式
    private final long maxRowCount; // 最大返回行数，用于限制结果集大小
    private final @Nullable Bindable<T> bindable; // 可绑定对象，用于绑定参数并执行查询

    @Deprecated // 标记为已弃用，将在2.0版本之前移除
    public CalciteSignature(String sql, List<AvaticaParameter> parameterList, // 已弃用的构造函数，创建查询签名对象
        Map<String, Object> internalParameters, RelDataType rowType, // 内部参数映射和结果行类型
        List<ColumnMetaData> columns, Meta.CursorFactory cursorFactory, // 列元数据和游标工厂
        CalciteSchema rootSchema, List<RelCollation> collationList, // 根schema和排序列表
        long maxRowCount, Bindable<T> bindable) { // 最大返回行数和可绑定对象
      this(sql, parameterList, internalParameters, rowType, columns, // 调用新的构造函数
          cursorFactory, rootSchema, collationList, maxRowCount, bindable, // 传入所有参数
          castNonNull(null)); // 语句类型传入null
    }

    public CalciteSignature(@Nullable String sql, // 主构造函数，创建查询签名对象，sql是SQL字符串
        List<AvaticaParameter> parameterList, // 参数列表，描述SQL语句中的参数
        Map<String, Object> internalParameters, // 内部参数映射，用于传递内部配置
        @Nullable RelDataType rowType, // 结果行类型
        List<ColumnMetaData> columns, // 列元数据列表，描述结果集的列信息
        Meta.CursorFactory cursorFactory, // 游标工厂，用于创建结果集游标
        @Nullable CalciteSchema rootSchema, // 根schema对象
        List<RelCollation> collationList, // 排序列表
        long maxRowCount, // 最大返回行数
        @Nullable Bindable<T> bindable, // 可绑定对象
        Meta.StatementType statementType) { // 语句类型（SELECT、INSERT等）
      super(columns, sql, parameterList, internalParameters, cursorFactory, // 调用父类构造函数初始化继承的字段
          statementType); // 传入语句类型
      this.rowType = rowType; // 保存结果行类型
      this.rootSchema = rootSchema; // 保存根schema
      this.collationList = collationList; // 保存排序列表
      this.maxRowCount = maxRowCount; // 保存最大返回行数
      this.bindable = bindable; // 保存可绑定对象
    }

    public Enumerable<T> enumerable(DataContext dataContext) { // 获取可枚举结果，用于遍历查询结果
      Enumerable<T> enumerable = castNonNull(bindable).bind(dataContext); // 绑定数据上下文到可绑定对象，生成可枚举结果
      if (maxRowCount >= 0) { // 如果最大返回行数大于等于0（表示需要限制行数）
        enumerable = EnumerableDefaults.take(enumerable, maxRowCount); // 应用限制，只取前maxRowCount行，注意：JDBC中0表示无限制，但这里-1表示无限制，0是有效限制
      }
      return enumerable; // 返回可枚举结果
    }

    public List<RelCollation> getCollationList() { // 获取排序列表
      return collationList; // 返回排序列表
    }
  }

  /** A union type of the three possible ways of expressing a query: as a SQL
   * string, a {@link Queryable} or a {@link RelNode}. Exactly one must be
   * provided.
   *
   * @param <T> element type */
  class Query<T> { // 定义内部类Query，表示查询的联合类型，可以是SQL字符串、Queryable或RelNode之一
    public final @Nullable String sql; // SQL字符串，表示以SQL形式提供的查询
    public final @Nullable Queryable<T> queryable; // 可查询对象，表示以LINQ风格提供的查询
    public final @Nullable RelNode rel; // 关系节点，表示以关系代数形式提供的查询

    private Query(@Nullable String sql, @Nullable Queryable<T> queryable, @Nullable RelNode rel) { // 私有构造函数，创建查询对象
      this.sql = sql; // 保存SQL字符串
      this.queryable = queryable; // 保存可查询对象
      this.rel = rel; // 保存关系节点

      assert (sql == null ? 0 : 1) // 断言：确保只有一个查询形式不为null，sql不为null则计数1
          + (queryable == null ? 0 : 1) // queryable不为null则计数1
          + (rel == null ? 0 : 1) == 1; // rel不为null则计数1，总和必须等于1，确保只有一个查询形式被提供
    }

    public static <T> Query<T> of(String sql) { // 静态工厂方法，从SQL字符串创建查询对象
      return new Query<>(sql, null, null); // 创建Query对象，只设置sql字段
    }

    public static <T> Query<T> of(Queryable<T> queryable) { // 静态工厂方法，从Queryable创建查询对象
      return new Query<>(null, queryable, null); // 创建Query对象，只设置queryable字段
    }

    public static <T> Query<T> of(RelNode rel) { // 静态工厂方法，从RelNode创建查询对象
      return new Query<>(null, null, rel); // 创建Query对象，只设置rel字段
    }
  }
}
