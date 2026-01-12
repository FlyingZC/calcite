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
package org.apache.calcite.test;

import org.apache.calcite.DataContext; // 数据上下文接口，提供查询执行时所需的运行时环境信息，包括schema、类型工厂等
import org.apache.calcite.adapter.enumerable.EnumUtils; // 枚举工具类，用于处理可枚举数据类型和转换
import org.apache.calcite.adapter.java.JavaTypeFactory; // Java类型工厂，用于创建和管理Java相关的数据类型
import org.apache.calcite.avatica.util.DateTimeUtils; // 日期时间工具类，提供日期时间的转换和处理功能
import org.apache.calcite.interpreter.Interpreter; // 解释器类，用于直接执行关系代数表达式树，是Calcite解释执行模式的核心
import org.apache.calcite.linq4j.QueryProvider; // LINQ查询提供者接口，用于支持LINQ风格的查询
import org.apache.calcite.plan.hep.HepPlanner; // HepPlanner启发式规划器，用于基于规则的优化
import org.apache.calcite.plan.hep.HepProgram; // HepPlanner的程序定义，包含一系列优化规则
import org.apache.calcite.plan.hep.HepProgramBuilder; // HepProgram构建器，用于构建优化规则程序
import org.apache.calcite.rel.RelNode; // 关系节点接口，代表关系代数表达式树中的一个节点
import org.apache.calcite.rel.RelRoot; // 关系根节点，包含关系树及其相关的元数据信息
import org.apache.calcite.rel.rules.CoreRules; // 核心优化规则集合，包含Calcite内置的常用优化规则
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口，描述关系数据的类型信息
import org.apache.calcite.rel.type.RelDataTypeField; // 关系数据类型字段，描述数据表中的字段类型
import org.apache.calcite.rex.RexLiteral; // Rex字面量表达式，代表常量值
import org.apache.calcite.schema.ScalarFunction; // 标量函数接口，定义用户自定义的标量函数
import org.apache.calcite.schema.SchemaPlus; // Schema扩展接口，提供对schema的增强操作能力
import org.apache.calcite.schema.TableFunction; // 表函数接口，定义返回表数据的函数
import org.apache.calcite.schema.impl.AbstractSchema; // 抽象Schema基类，提供schema的基本实现
import org.apache.calcite.schema.impl.ScalarFunctionImpl; // 标量函数实现类，用于包装Java方法为标量函数
import org.apache.calcite.schema.impl.TableFunctionImpl; // 表函数实现类，用于包装Java方法为表函数
import org.apache.calcite.sql.SqlNode; // SQL节点接口，代表SQL语法树中的一个节点
import org.apache.calcite.sql.parser.SqlParseException; // SQL解析异常，当SQL语法错误时抛出
import org.apache.calcite.sql.parser.SqlParser; // SQL解析器，用于将SQL字符串解析为SQL语法树
import org.apache.calcite.sql2rel.SqlToRelConverter; // SQL到关系代数转换器，将SQL语法树转换为关系代数表达式树
import org.apache.calcite.tools.FrameworkConfig; // 框架配置接口，配置Calcite查询框架的各种参数
import org.apache.calcite.tools.Frameworks; // 框架工具类，用于创建和配置Calcite查询框架
import org.apache.calcite.tools.Planner; // 规划器接口，负责SQL的解析、验证和优化
import org.apache.calcite.tools.RelBuilder; // 关系表达式构建器，用于以编程方式构建关系代数表达式树
import org.apache.calcite.tools.RelConversionException; // 关系转换异常，当关系转换失败时抛出
import org.apache.calcite.tools.ValidationException; // 验证异常，当SQL验证失败时抛出
import org.apache.calcite.util.Smalls; // 小型工具类集合，包含测试用的各种辅助类和方法
import org.apache.calcite.util.Util; // 通用工具类，提供各种辅助方法

import com.google.common.collect.ImmutableList; // Google Guava不可变列表，提供线程安全的不可变列表实现

import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework注解，标记可空类型
import org.junit.jupiter.api.AfterEach; // JUnit5注解，标记在每个测试方法之后执行的方法
import org.junit.jupiter.api.BeforeEach; // JUnit5注解，标记在每个测试方法之前执行的方法
import org.junit.jupiter.api.Test; // JUnit5注解，标记测试方法

import java.util.ArrayList; // Java集合框架的动态数组实现
import java.util.Arrays; // Java数组工具类，提供数组的操作方法
import java.util.Collections; // Java集合工具类，提供集合的排序、搜索等操作
import java.util.List; // Java列表接口
import java.util.concurrent.atomic.AtomicInteger; // 原子整数类，支持线程安全的整数操作
import java.util.function.Function; // Java函数式接口，表示一个接受一个参数并产生结果的函数
import java.util.function.UnaryOperator; // Java函数式接口，表示对单个操作数进行操作的一元运算符

import static org.hamcrest.CoreMatchers.equalTo; // Hamcrest断言库，用于验证对象相等
import static org.hamcrest.CoreMatchers.is; // Hamcrest断言库，用于验证对象是否匹配
import static org.hamcrest.CoreMatchers.notNullValue; // Hamcrest断言库，用于验证对象不为null
import static org.hamcrest.MatcherAssert.assertThat; // Hamcrest断言工具，提供断言方法

import static java.util.Objects.requireNonNull; // Java对象工具类，用于要求对象不为null

/**
 * Unit tests for {@link org.apache.calcite.interpreter.Interpreter}.
 * Interpreter类的单元测试类，用于测试Calcite解释器（Interpreter）的各种功能
 * 
 * 解释器是Calcite提供的一种直接执行关系代数表达式树的机制，与传统的优化执行不同，
 * 解释器通过遍历关系树并逐个节点执行来产生结果，这种方式适合于调试、测试和某些特殊场景
 * 
 * 本测试类覆盖了以下主要功能：
 * 1. 基本操作：投影、过滤、排序、值表达式
 * 2. 聚合操作：COUNT、MAX、MIN、GROUP BY、HAVING等
 * 3. 集合操作：UNION、UNION ALL、INTERSECT、EXCEPT等
 * 4. 连接操作：INNER JOIN、LEFT JOIN、RIGHT JOIN、FULL JOIN、SEMI JOIN、ANTI JOIN
 * 5. 特殊操作：UNNEST、MULTISET、NULLIF等
 * 6. 用户定义函数：标量函数、表函数
 * 7. 表扫描：普通表、ScannableTable等
 * 8. 数据类型处理：NULL值、DECIMAL、ARRAY、MAP等
 * 
 * 测试方法命名规范：testInterpret + 功能名称
 */
class InterpreterTest { // 定义InterpreterTest测试类，用于测试Interpreter解释器的各种功能
  private @Nullable SchemaPlus rootSchema; // 根Schema对象，用于存储和管理所有的表和函数，@Nullable表示该字段可以为null

  /** Implementation of {@link DataContext} for executing queries without a
   * connection. */
  /** DataContext接口的自定义实现，用于在没有数据库连接的情况下执行查询
   * 
   * DataContext是Calcite查询执行时的上下文环境，提供了查询执行所需的运行时信息，
   * 包括schema、类型工厂、查询提供者等。这个实现是为了测试目的而创建的简化版本
   */
  private static class MyDataContext implements DataContext { // 定义MyDataContext静态内部类，实现DataContext接口
    private final SchemaPlus rootSchema; // 根Schema对象，存储所有的表和函数定义，final表示初始化后不可修改
    private final JavaTypeFactory typeFactory; // Java类型工厂，用于创建和管理Java数据类型，final表示初始化后不可修改

    MyDataContext(SchemaPlus rootSchema, RelNode rel) { // MyDataContext构造方法，接收schema和关系节点作为参数
      this.rootSchema = rootSchema; // 将传入的rootSchema参数赋值给成员变量rootSchema
      this.typeFactory = (JavaTypeFactory) rel.getCluster().getTypeFactory(); // 从关系节点获取类型工厂并转换为JavaTypeFactory类型
    }

    public SchemaPlus getRootSchema() { // 获取根Schema的方法
      return rootSchema; // 返回存储的rootSchema对象
    }

    public JavaTypeFactory getTypeFactory() { // 获取类型工厂的方法
      return typeFactory; // 返回存储的typeFactory对象
    }

    public @Nullable QueryProvider getQueryProvider() { // 获取查询提供者的方法，用于支持LINQ查询
      return null; // 返回null，表示不支持LINQ查询
    }

    public @Nullable Object get(String name) { // 根据名称获取对象的方法，用于获取系统变量或参数
      return null; // 返回null，表示没有存储任何对象
    }
  }

  /** Fluent class that contains information necessary to run a test. */
  /** 流式API类，包含运行测试所需的所有信息
   * 
   * 这个类采用构建器模式，提供链式调用的方法来配置测试参数，
   * 包括SQL语句、Schema、是否需要投影、关系节点构建函数等
   * 
   * 主要方法：
   * - withSql: 设置SQL语句
   * - withProject: 设置是否需要投影
   * - withRel: 设置关系节点构建函数
   * - withSqlToRel: 设置SQL到关系转换的配置
   * - returnsRows: 验证有序结果
   * - returnsRowsUnordered: 验证无序结果
   */
  private static class Sql { // 定义Sql静态内部类，用于封装测试配置和执行逻辑
    private final String sql; // SQL语句字符串，存储要测试的SQL查询
    private final SchemaPlus rootSchema; // 根Schema对象，用于查询执行时的schema上下文
    private final boolean project; // 是否需要投影的标志，true表示需要投影
    private final @Nullable Function<RelBuilder, RelNode> relFn; // 关系节点构建函数，用于直接构建关系树而不是通过SQL
    private final UnaryOperator<SqlToRelConverter.Config> sqlToRelTransform; // SQL到关系转换的配置转换函数

    Sql(String sql, SchemaPlus rootSchema, boolean project, // Sql构造方法，初始化所有成员变量
        @Nullable Function<RelBuilder, RelNode> relFn, // 接收关系节点构建函数，可能为null
        UnaryOperator<SqlToRelConverter.Config> sqlToRelTransform) { // 接收SQL到关系转换的配置转换函数
      this.sql = sql; // 将传入的sql参数赋值给成员变量sql
      this.rootSchema = rootSchema; // 将传入的rootSchema参数赋值给成员变量rootSchema
      this.project = project; // 将传入的project参数赋值给成员变量project
      this.relFn = relFn; // 将传入的relFn参数赋值给成员变量relFn
      this.sqlToRelTransform = sqlToRelTransform; // 将传入的sqlToRelTransform参数赋值给成员变量sqlToRelTransform
    }

    Sql withSql(String sql) { // 设置SQL语句的方法，返回新的Sql对象以支持链式调用
      return new Sql(sql, rootSchema, project, relFn, sqlToRelTransform); // 创建并返回新的Sql对象，只修改sql参数
    }

    @SuppressWarnings("SameParameterValue") // 抑制警告：参数值总是相同的
    Sql withProject(boolean project) { // 设置是否需要投影的方法，返回新的Sql对象
      return new Sql(sql, rootSchema, project, relFn, sqlToRelTransform); // 创建并返回新的Sql对象，只修改project参数
    }

    Sql withRel(Function<RelBuilder, RelNode> relFn) { // 设置关系节点构建函数的方法，返回新的Sql对象
      return new Sql(sql, rootSchema, project, relFn, sqlToRelTransform); // 创建并返回新的Sql对象，只修改relFn参数
    }

    Sql withSqlToRel(UnaryOperator<SqlToRelConverter.Config> transform) { // 设置SQL到关系转换配置的方法，返回新的Sql对象
      final UnaryOperator<SqlToRelConverter.Config> newTransform = c -> // 创建新的转换函数，组合原有的转换和新的转换
          transform.apply(this.sqlToRelTransform.apply(c)); // 先应用原有转换，再应用新转换
      return new Sql(sql, rootSchema, project, relFn, newTransform); // 创建并返回新的Sql对象，使用新的转换函数
    }

    /** Interprets the sql and checks result with specified rows, ordered. */
    /** 解释执行SQL并验证结果行（有序）
     * 
     * 此方法会使用解释器执行SQL，然后验证结果行是否与预期的行完全匹配，
     * 包括行的顺序。这是测试中最常用的方法
     * 
     * @param rows 预期的结果行数组，每行用字符串表示，如"[1, a]"
     * @return 返回this以支持链式调用
     */
    @SuppressWarnings("UnusedReturnValue") // 抑制警告：返回值未使用
    Sql returnsRows(String... rows) { // 验证有序结果的方法
      return returnsRows(false, rows); // 调用内部方法，false表示结果需要有序比较
    }

    /** Interprets the sql and checks result with specified rows, unordered. */
    /** 解释执行SQL并验证结果行（无序）
     * 
     * 此方法会使用解释器执行SQL，然后验证结果行是否与预期的行匹配，
     * 不考虑行的顺序。适用于结果顺序不重要的场景
     * 
     * @param rows 预期的结果行数组，每行用字符串表示
     * @return 返回this以支持链式调用
     */
    @SuppressWarnings("UnusedReturnValue") // 抑制警告：返回值未使用
    Sql returnsRowsUnordered(String... rows) { // 验证无序结果的方法
      return returnsRows(true, rows); // 调用内部方法，true表示结果不需要有序比较
    }

    private Planner createPlanner() { // 创建规划器的方法，用于SQL的解析、验证和转换
      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
          .parserConfig(SqlParser.Config.DEFAULT) // 设置SQL解析器配置为默认配置
          .sqlToRelConverterConfig( // 设置SQL到关系转换器的配置
              sqlToRelTransform.apply(SqlToRelConverter.config())) // 应用自定义的转换配置
          .defaultSchema( // 设置默认schema
              CalciteAssert.addSchema(rootSchema, // 添加根schema
                  CalciteAssert.SchemaSpec.JDBC_SCOTT, // 添加JDBC_SCOTT测试schema
                  CalciteAssert.SchemaSpec.HR)) // 添加HR测试schema
          .build(); // 构建配置对象
      return Frameworks.getPlanner(config); // 使用配置创建并返回规划器
    }

    /** Performs an action that requires a {@link RelBuilder}, and returns the
     * result. */
    /** 执行需要RelBuilder的操作并返回结果
     * 
     * 此方法创建一个RelBuilder实例，然后执行传入的函数，
     * 函数可以使用RelBuilder来构建关系表达式树
     * 
     * @param <T> 返回值的泛型类型
     * @param fn 接收RelBuilder并返回结果的函数
     * @return 函数执行的结果
     */
    private <T> T withRelBuilder(Function<RelBuilder, T> fn) { // 使用RelBuilder执行操作的方法
      final FrameworkConfig config = Frameworks.newConfigBuilder() // 创建框架配置构建器
          .defaultSchema(rootSchema) // 设置默认schema为当前schema
          .build(); // 构建配置对象
      final RelBuilder relBuilder = RelBuilder.create(config); // 使用配置创建RelBuilder
      return fn.apply(relBuilder); // 应用函数并返回结果
    }

    /** Interprets the sql and checks result with specified rows. */
    /** 解释执行SQL并验证结果行（内部实现方法）
     * 
     * 这是实际执行测试的核心方法，流程如下：
     * 1. 创建规划器
     * 2. 如果有relFn，使用RelBuilder直接构建关系树
     * 3. 否则，解析SQL、验证SQL、转换为关系树
     * 4. 根据project标志决定是否进行投影
     * 5. 创建数据上下文
     * 6. 使用解释器执行并验证结果
     * 
     * @param unordered 是否忽略结果行的顺序
     * @param rows 预期的结果行数组
     * @return 返回this以支持链式调用
     * @throws RuntimeException 如果执行过程中出现异常
     */
    private Sql returnsRows(boolean unordered, String[] rows) { // 内部方法，执行SQL并验证结果
      try (Planner planner = createPlanner()) { // 使用try-with-resources创建并管理规划器
        final RelNode convert; // 声明关系节点变量
        if (relFn != null) { // 如果提供了关系节点构建函数
          convert = withRelBuilder(relFn); // 使用RelBuilder构建关系节点
        } else { // 否则使用SQL方式
          SqlNode parse = planner.parse(sql); // 解析SQL字符串为SQL语法树
          SqlNode validate = planner.validate(parse); // 验证SQL语法树的语义正确性
          final RelRoot root = planner.rel(validate); // 将验证后的SQL树转换为关系树根节点
          convert = project ? root.project() : root.rel; // 根据project标志决定是否投影
        }
        final MyDataContext dataContext = // 创建数据上下文
            new MyDataContext(rootSchema, convert); // 使用schema和关系节点初始化
        assertInterpret(convert, dataContext, unordered, rows); // 解释执行并验证结果
        return this; // 返回this以支持链式调用
      } catch (ValidationException // 捕获验证异常
          | SqlParseException // 捕获SQL解析异常
          | RelConversionException e) { // 捕获关系转换异常
        throw Util.throwAsRuntime(e); // 将检查异常转换为运行时异常并抛出
      }
    }
  }

  /** Creates a {@link Sql}. */
  /** 创建Sql测试辅助对象
   * 
   * 此方法创建一个默认配置的Sql对象，用于构建测试用例
   * 
   * @return 返回一个初始化的Sql对象
   */
  private Sql fixture() { // 创建Sql测试辅助对象的方法
    return new Sql("?", rootSchema(), false, null, UnaryOperator.identity()); // 创建Sql对象，使用默认参数
  }

  private SchemaPlus rootSchema() { // 获取根Schema的方法
    return requireNonNull(rootSchema, "rootSchema"); // 返回rootSchema，如果为null则抛出异常
  }

  private Sql sql(String sql) { // 创建Sql对象并设置SQL语句的便捷方法
    return fixture().withSql(sql); // 创建fixture对象并设置SQL语句
  }

  private void reset() { // 重置测试环境的方法
    rootSchema = Frameworks.createRootSchema(true); // 创建新的根Schema，true表示添加默认metadata
  }

  @BeforeEach public void setUp() { // 每个测试方法执行前的初始化方法
    reset(); // 重置测试环境，创建新的schema
  }

  @AfterEach public void tearDown() { // 每个测试方法执行后的清理方法
    rootSchema = null; // 将rootSchema置为null，释放资源
  }

  /** Tests executing a simple plan using an interpreter. */
  /** 测试使用解释器执行简单的查询计划
   * 
   * 这个测试验证解释器能够正确执行包含投影、过滤和值的查询
   * 
   * SQL: select y, x from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y) where x > 1
   * 
   * 执行步骤：
   * 1. 创建值表：(1, 'a'), (2, 'b'), (3, 'c')
   * 2. 应用过滤条件：x > 1，保留(2, 'b')和(3, 'c')
   * 3. 应用投影：选择y, x列，得到(b, 2)和(c, 3)
   * 
   * 预期结果：[b, 2], [c, 3]
   */
  @Test void testInterpretProjectFilterValues() { // 测试投影、过滤和值操作
    final String sql = "select y, x\n" // 定义SQL语句，选择y和x列
        + "from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)\n" // 从值表中选择
        + "where x > 1"; // 过滤条件：x大于1
    sql(sql).returnsRows("[b, 2]", "[c, 3]"); // 执行SQL并验证结果
  }

  /** Tests NULLIF operator. (NULLIF is an example of an operator that
   * is implemented by expanding to simpler operators - in this case, CASE.) */
  /** 测试NULLIF操作符
   * 
   * NULLIF(expr1, expr2)是一个SQL函数，如果expr1等于expr2则返回NULL，否则返回expr1
   * 这个操作符在Calcite中是通过展开为CASE表达式来实现的
   * 
   * SQL: select nullif(x, 2), x from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)
   * 
   * 执行步骤：
   * 1. 第一行：nullif(1, 2) = 1 (因为1 != 2)
   * 2. 第二行：nullif(2, 2) = NULL (因为2 == 2)
   * 3. 第三行：nullif(3, 2) = 3 (因为3 != 2)
   * 
   * 预期结果：[1, 1], [null, 2], [3, 3]
   */
  @Test void testInterpretNullif() { // 测试NULLIF操作符
    final String sql = "select nullif(x, 2), x\n" // 定义SQL语句，使用NULLIF函数
        + "from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)"; // 从值表中选择
    sql(sql).returnsRows("[1, 1]", "[null, 2]", "[3, 3]"); // 执行SQL并验证结果
  }

  /** Tests a plan where the sort field is projected away. */
  /** 测试排序字段被投影掉的查询计划
   * 
   * 这个测试验证解释器能够正确处理排序字段不在最终结果集中的情况
   * 
   * SQL: select y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y) order by -x
   * 
   * 执行步骤：
   * 1. 创建值表：(1, 'a'), (2, 'b'), (3, 'c')
   * 2. 按x的降序排序：(3, 'c'), (2, 'b'), (1, 'a')
   * 3. 投影y列：'c', 'b', 'a'
   * 
   * 注意：虽然最终结果中不包含x列，但排序仍然基于x列
   * 
   * 预期结果：[c], [b], [a]
   */
  @Test void testInterpretOrder() { // 测试排序和投影操作
    final String sql = "select y\n" // 定义SQL语句，只选择y列
        + "from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)\n" // 从值表中选择
        + "order by -x"; // 按x的降序排序（-x表示降序）
    sql(sql).withProject(true).returnsRows("[c]", "[b]", "[a]"); // 执行SQL并验证结果，withProject(true)确保投影
  }

  @Test void testInterpretMultiset() { // 测试MULTISET集合类型操作
    final String sql = "select multiset['a', 'b', 'c']"; // 定义SQL语句，创建MULTISET集合
    sql(sql).withProject(true).returnsRows("[[a, b, c]]"); // 执行SQL并验证结果，MULTISET表示为嵌套列表
  }

  private static void assertInterpret(RelNode rel, DataContext dataContext, // 断言解释执行结果的方法
      boolean unordered, String... rows) { // 参数：关系节点、数据上下文、是否无序、预期行
    try (Interpreter interpreter = new Interpreter(dataContext, rel)) { // 使用try-with-resources创建解释器
      final List<RelDataType> fieldTypes = // 获取字段类型列表
          Util.transform(rel.getRowType().getFieldList(), // 转换关系节点的字段列表
              RelDataTypeField::getType); // 提取每个字段的类型
      final Function<@Nullable Object[], List<@Nullable Object>> converter = // 创建类型转换器
          EnumUtils.toExternal(fieldTypes, DateTimeUtils.DEFAULT_ZONE); // 将内部类型转换为外部Java类型
      assertRows(interpreter, converter, unordered, rows); // 调用assertRows验证结果
    }
  }

  private static void assertRows(Interpreter interpreter, // 断言结果行的方法
      Function<Object[], List<Object>> converter, // 类型转换函数
      boolean unordered, String... rows) { // 参数：解释器、转换器、是否无序、预期行
    final List<String> list = new ArrayList<>(); // 创建结果行列表
    for (Object[] row : interpreter) { // 遍历解释器产生的每一行
      list.add(converter.apply(row).toString()); // 转换行数据并添加到列表
    }
    final List<String> expected = Arrays.asList(rows); // 将预期行转换为列表
    if (unordered) { // 如果需要无序比较
      Collections.sort(list); // 对实际结果排序
      Collections.sort(expected); // 对预期结果排序
    }
    assertThat(list, equalTo(expected)); // 断言实际结果等于预期结果
  }

  /** Tests executing a simple plan using an interpreter. */
  /** 测试使用解释器执行表查询
   * 
   * 这个测试验证解释器能够正确查询hr schema中的emps表
   * 
   * SQL: select * from "hr"."emps" order by "empid"
   * 
   * 执行步骤：
   * 1. 扫描hr.emps表
   * 2. 按empid排序
   * 
   * 预期结果：4行员工数据，按empid升序排列
   */
  @Test void testInterpretTable() { // 测试表查询
    sql("select * from \"hr\".\"emps\" order by \"empid\"") // 定义SQL语句，查询hr.emps表
        .returnsRows("[100, 10, Bill, 10000.0, 1000]", // 验证第一行结果
            "[110, 10, Theodore, 11500.0, 250]", // 验证第二行结果
            "[150, 10, Sebastian, 7000.0, null]", // 验证第三行结果
            "[200, 20, Eric, 8000.0, 500]"); // 验证第四行结果
  }

  /** Tests executing a plan on a
   * {@link org.apache.calcite.schema.ScannableTable} using an interpreter. */
  /** 测试使用解释器执行ScannableTable表查询
   * 
   * ScannableTable是Calcite提供的一种可扫描表接口，允许用户自定义表的扫描逻辑
   * 这个测试使用BeatlesTable作为示例，包含披头士乐队成员的信息
   * 
   * SQL: select * from "beatles" order by "i"
   * 
   * 执行步骤：
   * 1. 将BeatlesTable添加到schema
   * 2. 扫描beatles表
   * 3. 按i字段排序
   * 
   * 预期结果：4行数据，包含披头士成员的信息
   */
  @Test void testInterpretScannableTable() { // 测试ScannableTable表查询
    rootSchema().add("beatles", new ScannableTableTest.BeatlesTable()); // 将BeatlesTable添加到schema
    sql("select * from \"beatles\" order by \"i\"") // 定义SQL语句，查询beatles表
        .returnsRows("[4, John, 1940]", "[4, Paul, 1942]", "[5, Ringo, 1940]", // 验证结果
            "[6, George, 1943]");
  }

  /** Tests executing a plan on a
   * {@link org.apache.calcite.schema.ScannableTable} using an interpreter. */
  /** 测试ScannableTable的生命周期调用
   * 
   * 这个测试验证ScannableTable的scan、enumerate和close方法的调用次数
   * 
   * SQL: select * from "counting" order by "i"
   * 
   * 执行步骤：
   * 1. 创建计数器来跟踪方法调用次数
   * 2. 创建counting表并添加到schema
   * 3. 执行查询
   * 4. 验证方法调用次数
   * 
   * 预期行为：
   * - scan被调用1次
   * - enumerate被调用1次
   * - close被调用2次（最后一次fetch和interpreter关闭时）
   */
  @Test void testInterpretScannableTable2() { // 测试ScannableTable的生命周期
    final AtomicInteger scanCount = new AtomicInteger(); // 创建scan计数器
    final AtomicInteger enumerateCount = new AtomicInteger(); // 创建enumerate计数器
    final AtomicInteger closeCount = new AtomicInteger(); // 创建close计数器
    rootSchema().add("counting", // 添加counting表到schema
        ScannableTableTest.countingTable(scanCount, enumerateCount, // 传入计数器
            closeCount));
    sql("select * from \"counting\" order by \"i\"") // 定义SQL语句
        .returnsRows("[0]", "[10]", "[20]", "[30]"); // 验证结果
    assertThat(scanCount.get(), is(1)); // 断言scan被调用1次
    assertThat(enumerateCount.get(), is(1)); // 断言enumerate被调用1次
    assertThat("close is called twice: on last fetch, and interpreter close", // 验证close调用次数
        closeCount.get(), is(2)); // 断言close被调用2次
  }

  @Test void testAggregateCount() { // 测试COUNT聚合函数
    rootSchema().add("beatles", new ScannableTableTest.BeatlesTable()); // 添加beatles表
    sql("select count(*) from \"beatles\"") // 定义SQL语句，计算行数
        .returnsRows("[4]"); // 验证结果为4行
  }

  @Test void testAggregateMax() { // 测试MAX聚合函数
    rootSchema().add("beatles", new ScannableTableTest.BeatlesTable()); // 添加beatles表
    sql("select max(\"i\") from \"beatles\"") // 定义SQL语句，计算i的最大值
        .returnsRows("[6]"); // 验证结果为6
  }

  @Test void testAggregateMin() { // 测试MIN聚合函数
    rootSchema().add("beatles", new ScannableTableTest.BeatlesTable()); // 添加beatles表
    sql("select min(\"i\") from \"beatles\"") // 定义SQL语句，计算i的最小值
        .returnsRows("[4]"); // 验证结果为4
  }

  @Test void testAggregateGroup() { // 测试GROUP BY聚合
    rootSchema().add("beatles", new ScannableTableTest.BeatlesTable()); // 添加beatles表
    sql("select \"j\", count(*) from \"beatles\" group by \"j\"") // 定义SQL语句，按j分组计数
        .returnsRowsUnordered("[George, 1]", "[Paul, 1]", "[John, 1]", // 验证结果（无序）
            "[Ringo, 1]");
  }

  @Test void testAggregateGroupFilter() { // 测试带过滤的GROUP BY聚合（FILTER子句）
    rootSchema().add("beatles", new ScannableTableTest.BeatlesTable()); // 添加beatles表
    final String sql = "select \"j\",\n" // 定义SQL语句
        + "  count(*) filter (where char_length(\"j\") > 4)\n" // 使用FILTER子句过滤聚合
        + "from \"beatles\" group by \"j\""; // 按j分组
    sql(sql)
        .returnsRowsUnordered("[George, 1]", // George长度为6，计数1
            "[Paul, 0]", // Paul长度为4，不满足条件，计数0
            "[John, 0]", // John长度为4，不满足条件，计数0
            "[Ringo, 1]"); // Ringo长度为5，计数1
  }

  /** Tests a GROUP BY query that uses
   * {@link org.apache.calcite.sql.fun.SqlInternalOperators#LITERAL_AGG}. */
  /** 测试使用LITERAL_AGG的GROUP BY查询
   * 
   * LITERAL_AGG是一个内部操作符，用于在聚合中返回字面量值
   * 这个测试验证解释器能够正确处理字面量聚合
   * 
   * 执行步骤：
   * 1. 使用RelBuilder构建关系树
   * 2. 扫描beatles表
   * 3. 按k字段分组
   * 4. 计算count、literalAgg(true)、literalAgg(-3)
   * 
   * 预期结果：3行数据，按k分组，每行包含count和字面量值
   */
  @Test void testAggregateLiteralAgg() { // 测试LITERAL_AGG聚合
    rootSchema().add("beatles", new ScannableTableTest.BeatlesTable()); // 添加beatles表
    final Function<RelBuilder, RelNode> relFn = // 定义关系节点构建函数
        b -> b.scan("beatles") // 扫描beatles表
            .aggregate(b.groupKey("k"), // 按k字段分组
                b.count().as("c"), // 计数并命名为c
                b.literalAgg(true).as("t"), // 字面量聚合true并命名为t
                b.literalAgg(-3).as("minus3")) // 字面量聚合-3并命名为minus3
            .build(); // 构建关系节点
    fixture().withRel(relFn) // 使用关系节点构建fixture
        .returnsRows("[1940, 2, true, -3]", "[1942, 1, true, -3]", // 验证结果
            "[1943, 1, true, -3]");
  }

  /** Tests executing a plan on a single-column
   * {@link org.apache.calcite.schema.ScannableTable} using an interpreter. */
  /** 测试单列ScannableTable的查询
   * 
   * 这个测试验证解释器能够正确查询单列表
   * 
   * SQL: select * from "simple" limit 2
   * 
   * 执行步骤：
   * 1. 添加SimpleTable到schema
   * 2. 扫描simple表
   * 3. 限制返回2行
   * 
   * 预期结果：2行数据
   */
  @Test void testInterpretSimpleScannableTable() { // 测试单列ScannableTable
    rootSchema().add("simple", new ScannableTableTest.SimpleTable()); // 添加simple表
    sql("select * from \"simple\" limit 2") // 定义SQL语句，限制2行
        .returnsRows("[0]", "[10]"); // 验证结果
  }

  /** Tests executing a UNION ALL query using an interpreter. */
  /** 测试UNION ALL集合操作
   * 
   * UNION ALL合并两个查询的结果，保留所有行（包括重复行）
   * 
   * SQL: select * from "simple" union all select * from "simple"
   * 
   * 执行步骤：
   * 1. 查询simple表得到4行：[0], [10], [20], [30]
   * 2. 再次查询simple表得到4行：[0], [10], [20], [30]
   * 3. 使用UNION ALL合并，保留所有8行
   * 
   * 预期结果：8行数据，包含重复行
   */
  @Test void testInterpretUnionAll() { // 测试UNION ALL操作
    rootSchema().add("simple", new ScannableTableTest.SimpleTable()); // 添加simple表
    final String sql = "select * from \"simple\"\n" // 定义SQL语句
        + "union all\n" // 使用UNION ALL合并
        + "select * from \"simple\""; // 再次查询simple表
    sql(sql).returnsRowsUnordered("[0]", "[10]", "[20]", "[30]", "[0]", "[10]", // 验证8行结果（无序）
        "[20]", "[30]");
  }

  /** Tests executing a UNION query using an interpreter. */
  /** 测试UNION集合操作
   * 
   * UNION合并两个查询的结果，去除重复行
   * 
   * SQL: select * from "simple" union select * from "simple"
   * 
   * 执行步骤：
   * 1. 查询simple表得到4行：[0], [10], [20], [30]
   * 2. 再次查询simple表得到4行：[0], [10], [20], [30]
   * 3. 使用UNION合并，去除重复行
   * 
   * 预期结果：4行数据，无重复
   */
  @Test void testInterpretUnion() { // 测试UNION操作
    rootSchema().add("simple", new ScannableTableTest.SimpleTable()); // 添加simple表
    final String sql = "select * from \"simple\"\n" // 定义SQL语句
        + "union\n" // 使用UNION合并
        + "select * from \"simple\""; // 再次查询simple表
    sql(sql).returnsRowsUnordered("[0]", "[10]", "[20]", "[30]"); // 验证4行结果（无序）
  }

  @Test void testInterpretUnionWithNullValue() { // 测试UNION处理NULL值
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1))),\n" // 包含NULL的值表
        + "(cast(NULL as int), cast(NULL as varchar(1)))) as t(x, y))\n" // 两行NULL数据
        + "union\n" // 使用UNION合并
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1)))) as t2(x, y))"; // 一行NULL数据
    sql(sql).returnsRows("[null, null]"); // 验证结果为单行NULL（去重）
  }

  @Test void testInterpretUnionAllWithNullValue() { // 测试UNION ALL处理NULL值
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1))),\n" // 包含NULL的值表
        + "(cast(NULL as int), cast(NULL as varchar(1)))) as t(x, y))\n" // 两行NULL数据
        + "union all\n" // 使用UNION ALL合并
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1)))) as t2(x, y))"; // 一行NULL数据
    sql(sql).returnsRows("[null, null]", "[null, null]", "[null, null]"); // 验证3行NULL结果（不去重）
  }

  @Test void testInterpretIntersect() { // 测试INTERSECT集合操作
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'a'), (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y))\n" // 第一个表：4行
        + "intersect\n" // 使用INTERSECT求交集
        + "(select x, y from (values (1, 'a'), (2, 'c'), (4, 'x')) as t2(x, y))"; // 第二个表：3行
    sql(sql).returnsRows("[1, a]"); // 验证结果：只有(1, 'a')在两个表中都存在
  }

  @Test void testInterpretIntersectAll() { // 测试INTERSECT ALL集合操作
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'a'), (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y))\n" // 第一个表：包含(1, 'a')两次
        + "intersect all\n" // 使用INTERSECT ALL求交集（保留重复）
        + "(select x, y from (values (1, 'a'), (2, 'c'), (4, 'x')) as t2(x, y))"; // 第二个表：(1, 'a')一次
    sql(sql).returnsRows("[1, a]"); // 验证结果：取最小重复次数，即1次
  }

  @Test void testInterpretIntersectWithNullValue() { // 测试INTERSECT处理NULL值
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1))),\n" // 第一个表：两行NULL
        + " (cast(NULL as int), cast(NULL as varchar(1)))) as t(x, y))\n"
        + "intersect\n" // 使用INTERSECT求交集
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1)))) as t2(x, y))"; // 第二个表：一行NULL
    sql(sql).returnsRows("[null, null]"); // 验证结果：一行NULL
  }

  @Test void testInterpretIntersectAllWithNullValue() { // 测试INTERSECT ALL处理NULL值
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1))),\n" // 第一个表：两行NULL
        + " (cast(NULL as int), cast(NULL as varchar(1)))) as t(x, y))\n"
        + "intersect all\n" // 使用INTERSECT ALL求交集（保留重复）
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1)))) as t2(x, y))"; // 第二个表：一行NULL
    sql(sql).returnsRows("[null, null]"); // 验证结果：一行NULL（取最小重复次数）
  }

  @Test void testInterpretMinus() { // 测试EXCEPT集合操作（差集）
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'a'), (2, 'b'), (2, 'b'), (3, 'c')) as t(x, y))\n" // 第一个表：4行
        + "except\n" // 使用EXCEPT求差集
        + "(select x, y from (values (1, 'a'), (2, 'c'), (4, 'x')) as t2(x, y))"; // 第二个表：3行
    sql(sql).returnsRows("[2, b]", "[3, c]"); // 验证结果：(2, 'b')和(3, 'c')不在第二个表中
  }

  @Test void testDuplicateRowInterpretMinus() { // 测试EXCEPT处理重复行
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (2, 'b'), (2, 'b')) as t(x, y))\n" // 第一个表：两行(2, 'b')
        + "except\n" // 使用EXCEPT求差集
        + "(select x, y from (values (2, 'b')) as t2(x, y))"; // 第二个表：一行(2, 'b')
    sql(sql).returnsRows(); // 验证结果：空（所有行都被排除）
  }

  @Test void testInterpretMinusAll() { // 测试EXCEPT ALL集合操作
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'a'), (2, 'b'), (2, 'b'), (3, 'c')) as t(x, y))\n" // 第一个表：4行
        + "except all\n" // 使用EXCEPT ALL求差集（保留重复）
        + "(select x, y from (values (1, 'a'), (2, 'c'), (4, 'x')) as t2(x, y))"; // 第二个表：3行
    sql(sql).returnsRows("[2, b]", "[2, b]", "[3, c]"); // 验证结果：减去匹配的行，保留剩余的重复
  }

  @Test void testDuplicateRowInterpretMinusAll() { // 测试EXCEPT ALL处理重复行
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (2, 'b'), (2, 'b')) as t(x, y))\n" // 第一个表：两行(2, 'b')
        + "except all\n" // 使用EXCEPT ALL求差集
        + "(select x, y from (values (2, 'b')) as t2(x, y))\n"; // 第二个表：一行(2, 'b')
    sql(sql).returnsRows("[2, b]"); // 验证结果：一行(2, 'b')（减去一行）
  }

  @Test void testInterpretMinusAllWithNullValue() { // 测试EXCEPT ALL处理NULL值
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1))),\n" // 第一个表：两行NULL
        + " (cast(NULL as int), cast(NULL as varchar(1)))) as t(x, y))\n"
        + "except all\n" // 使用EXCEPT ALL求差集
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1)))) as t2(x, y))\n"; // 第二个表：一行NULL
    sql(sql).returnsRows("[null, null]"); // 验证结果：一行NULL（减去一行）
  }

  @Test void testInterpretMinusWithNullValue() { // 测试EXCEPT处理NULL值
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1))),\n" // 第一个表：两行NULL
        + "(cast(NULL as int), cast(NULL as varchar(1)))) as t(x, y))\n"
        + "except\n" // 使用EXCEPT求差集
        + "(select x, y from (values (cast(NULL as int), cast(NULL as varchar(1)))) as t2(x, y))\n"; // 第二个表：一行NULL
    sql(sql).returnsRows(); // 验证结果：空（所有NULL都被排除）
  }

  @Test void testInterpretInnerJoin() { // 测试INNER JOIN内连接
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)) t\n" // 左表：3行
        + "join\n" // 使用INNER JOIN内连接
        + "(select x, y from (values (1, 'd'), (2, 'c')) as t2(x, y)) t2\n" // 右表：2行
        + "on t.x = t2.x"; // 连接条件：x相等
    sql(sql).returnsRows("[1, a, 1, d]", "[2, b, 2, c]"); // 验证结果：只有x=1和x=2匹配
  }

  @Test void testInterpretLeftOutJoin() { // 测试LEFT JOIN左外连接
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)) t\n" // 左表：3行
        + "left join\n" // 使用LEFT JOIN左外连接
        + "(select x, y from (values (1, 'd')) as t2(x, y)) t2\n" // 右表：1行
        + "on t.x = t2.x"; // 连接条件：x相等
    sql(sql).returnsRows("[1, a, 1, d]", "[2, b, null, null]", "[3, c, null, null]"); // 验证结果：左表所有行，不匹配的填充NULL
  }

  @Test void testInterpretRightOutJoin() { // 测试RIGHT JOIN右外连接
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'd')) as t2(x, y)) t2\n" // 左表：1行
        + "right join\n" // 使用RIGHT JOIN右外连接
        + "(select x, y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)) t\n" // 右表：3行
        + "on t2.x = t.x"; // 连接条件：x相等
    sql(sql).returnsRows("[1, d, 1, a]", "[null, null, 2, b]", "[null, null, 3, c]"); // 验证结果：右表所有行，不匹配的填充NULL
  }

  @Test void testInterpretSemanticSemiJoin() { // 测试语义上的半连接（IN子查询）
    final String sql = "select x, y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)\n" // 定义SQL语句
        + "where x in\n" // 使用IN子查询（语义上的半连接）
        + "(select x from (values (1, 'd'), (3, 'g')) as t2(x, y))"; // 子查询：x=1或x=3
    sql(sql).withSqlToRel(c -> c.withExpand(true)) // 配置SQL到关系转换
        .returnsRows("[1, a]", "[3, c]"); // 验证结果：x=1和x=3的行
  }

  @Test void testInterpretSemiJoin() { // 测试显式的半连接操作
    final String sql = "select x, y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)\n" // 定义SQL语句
        + "where x in\n" // 使用IN子查询
        + "(select x from (values (1, 'd'), (3, 'g')) as t2(x, y))"; // 子查询
    try (Planner planner = // 创建规划器
             sql(sql).withSqlToRel(c -> c.withExpand(true)).createPlanner()) { // 配置并创建规划器
      SqlNode validate = planner.validate(planner.parse(sql)); // 解析并验证SQL
      RelNode convert = planner.rel(validate).rel; // 转换为关系节点
      final HepProgram program = new HepProgramBuilder() // 创建HepPlanner程序
          .addRuleInstance(CoreRules.PROJECT_TO_SEMI_JOIN) // 添加PROJECT_TO_SEMI_JOIN规则
          .build(); // 构建程序
      final HepPlanner hepPlanner = new HepPlanner(program); // 创建HepPlanner
      hepPlanner.setRoot(convert); // 设置根节点
      final RelNode relNode = hepPlanner.findBestExp(); // 执行优化
      final MyDataContext dataContext = // 创建数据上下文
          new MyDataContext(rootSchema(), relNode); // 使用schema和关系节点初始化
      assertInterpret(relNode, dataContext, true, "[1, a]", "[3, c]"); // 解释执行并验证结果
    } catch (ValidationException // 捕获验证异常
        | SqlParseException // 捕获解析异常
        | RelConversionException e) { // 捕获转换异常
      throw Util.throwAsRuntime(e); // 转换为运行时异常
    }
  }

  @Test void testInterpretAntiJoin() { // 测试反连接（NOT IN子查询）
    final String sql = "select x, y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)\n" // 定义SQL语句
        + "where x not in\n" // 使用NOT IN子查询（反连接）
        + "(select x from (values (1, 'd')) as t2(x, y))"; // 子查询：x=1
    sql(sql).withSqlToRel(c -> c.withExpand(true)) // 配置SQL到关系转换
        .returnsRows("[2, b]", "[3, c]"); // 验证结果：x不等于1的行
  }

  @Test void testInterpretFullJoin() { // 测试FULL JOIN全外连接
    final String sql = "select * from\n" // 定义SQL语句
        + "(select x, y from (values (1, 'a'), (2, 'b'), (3, 'c')) as t(x, y)) t\n" // 左表：3行
        + "full join\n" // 使用FULL JOIN全外连接
        + "(select x, y from (values (1, 'd'), (2, 'c'), (4, 'x')) as t2(x, y)) t2\n" // 右表：3行
        + "on t.x = t2.x"; // 连接条件：x相等
    sql(sql).returnsRows( // 验证结果：所有行，不匹配的填充NULL
        "[1, a, 1, d]", // x=1匹配
        "[2, b, 2, c]", // x=2匹配
        "[3, c, null, null]", // 左表的x=3不匹配
        "[null, null, 4, x]"); // 右表的x=4不匹配
  }

  @Test void testInterpretDecimalAggregate() { // 测试DECIMAL类型的聚合函数
    final String sql = "select x, min(y), max(y), sum(y), avg(y)\n" // 定义SQL语句，使用多种聚合函数
        + "from (values ('a', -1.2), ('a', 2.3), ('a', 15)) as t(x, y)\n" // 值表：3行DECIMAL数据
        + "group by x"; // 按x分组
    sql(sql).returnsRows("[a, -1.2, 15.0, 16.1, 5.3]"); // 验证结果：min=-1.2, max=15.0, sum=16.1, avg=5.3
  }

  @Test void testInterpretUnnest() { // 测试UNNEST操作（展开数组、多集、映射）
    sql("select * from unnest(array[1, 2])").returnsRows("[1]", "[2]"); // 测试展开数组

    reset(); // 重置环境
    sql("select * from unnest(multiset[1, 2])").returnsRowsUnordered("[1]", "[2]"); // 测试展开多集

    reset(); // 重置环境
    sql("select * from unnest(map['a', 12])").returnsRows("[a, 12]"); // 测试展开映射

    reset(); // 重置环境
    sql("select * from unnest(\n" // 测试展开嵌套数组并使用WITH ORDINALITY
        + "select * from (values array[10, 20], array[30, 40]))\n" // 值表包含两个数组
        + "with ordinality as t(i, o)") // WITH ORDINALITY添加序号列
        .returnsRows("[10, 1]", "[20, 2]", "[30, 1]", "[40, 2]"); // 验证结果：每个元素及其序号

    reset(); // 重置环境
    sql("select * from unnest(map['a', 12, 'b', 13]) with ordinality as t(a, b, o)") // 测试展开映射并使用WITH ORDINALITY
        .returnsRows("[a, 12, 1]", "[b, 13, 2]"); // 验证结果：键、值、序号

    reset(); // 重置环境
    sql("select * from unnest(\n" // 测试展开嵌套多集并使用WITH ORDINALITY
        + "select * from (values multiset[10, 20], multiset[30, 40]))\n" // 值表包含两个多集
        + "with ordinality as t(i, o)") // WITH ORDINALITY添加序号列
        .returnsRows("[10, 1]", "[20, 2]", "[30, 1]", "[40, 2]"); // 验证结果：每个元素及其序号

    reset(); // 重置环境
    sql("select * from unnest(array[cast(null as integer), 10])") // 测试展开包含NULL的数组
        .returnsRows("[null]", "[10]"); // 验证结果：包含NULL元素

    reset(); // 重置环境
    sql("select * from unnest(map[cast(null as integer), 10, 10, cast(null as integer)])") // 测试展开包含NULL的映射
        .returnsRowsUnordered("[null, 10]", "[10, null]"); // 验证结果：包含NULL键和值

    reset(); // 重置环境
    sql("select * from unnest(multiset[cast(null as integer), 10])") // 测试展开包含NULL的多集
        .returnsRowsUnordered("[null]", "[10]"); // 验证结果：包含NULL元素

    try { // 测试展开NULL数组
      reset(); // 重置环境
      sql("select * from unnest(cast(null as int array))").returnsRows(""); // 尝试展开NULL数组
    } catch (NullPointerException e) { // 捕获空指针异常
      assertThat(e.getMessage(), equalTo("NULL value for unnest.")); // 验证异常消息
    }
  }

  @Test void testInterpretJdbc() { // 测试JDBC表查询
    sql("select empno, hiredate from jdbc_scott.emp") // 定义SQL语句，查询JDBC表
        .returnsRows("[7369, 1980-12-17]", "[7499, 1981-02-20]", // 验证前两行结果
            "[7521, 1981-02-22]", "[7566, 1981-02-04]", "[7654, 1981-09-28]", // 验证中间行结果
            "[7698, 1981-01-05]", "[7782, 1981-06-09]", "[7788, 1987-04-19]", // 验证中间行结果
            "[7839, 1981-11-17]", "[7844, 1981-09-08]", "[7876, 1987-05-23]", // 验证中间行结果
            "[7900, 1981-12-03]", "[7902, 1981-12-03]", "[7934, 1982-01-23]"); // 验证最后行结果
  }

  /** Tests a user-defined scalar function that is non-static. */
  /** 测试用户定义的非静态标量函数
   * 
   * 这个测试验证解释器能够正确调用用户定义的标量函数
   * myPlus函数实现简单的加法运算：x + y
   * 
   * 执行步骤：
   * 1. 创建schema并添加myPlus函数
   * 2. 对每行数据调用myPlus(x, 1)
   * 3. 验证函数实例化次数（应该只实例化一次，而不是每行一次）
   * 
   * 预期结果：每行的x值加1
   */
  @Test void testInterpretFunction() { // 测试用户定义的标量函数
    SchemaPlus schema = rootSchema().add("s", new AbstractSchema()); // 创建schema
    final ScalarFunction f = // 创建标量函数
        ScalarFunctionImpl.create(Smalls.MY_PLUS_EVAL_METHOD); // 使用Smalls的myPlus方法
    schema.add("myPlus", f); // 将函数添加到schema
    final String sql = "select x, \"s\".\"myPlus\"(x, 1)\n" // 定义SQL语句，调用myPlus函数
        + "from (values (2), (4), (7)) as t (x)"; // 值表：3行
    String[] rows = {"[2, 3]", "[4, 5]", "[7, 8]"}; // 预期结果
    final int n = Smalls.MyPlusFunction.INSTANCE_COUNT.get().get(); // 获取初始实例计数
    sql(sql).returnsRows(rows); // 执行SQL并验证结果
    final int n2 = Smalls.MyPlusFunction.INSTANCE_COUNT.get().get(); // 获取执行后的实例计数
    assertThat(n2, is(n + 1)); // 断言实例只创建了一次（不是每行一次）
  }

  /** Tests a user-defined scalar function that is non-static and has a
   * constructor that uses
   * {@link org.apache.calcite.schema.FunctionContext}. */
  /** 测试使用FunctionContext的用户定义标量函数
   * 
   * 这个测试验证解释器能够正确调用带有初始化器的标量函数
   * FunctionContext允许函数在执行前访问参数信息
   * 
   * 执行步骤：
   * 1. 创建schema并添加myPlusInit函数
   * 2. 第一次调用：myPlusInit(x, 1)，参数1是常量
   * 3. 第二次调用：myPlusInit(x, 3 - 2)，参数是表达式
   * 4. 验证函数实例化和参数摘要
   * 
   * 预期结果：
   * - 第一次：每行加1，参数摘要显示参数1是常量
   * - 第二次：每行加100，参数摘要显示参数1不是常量
   */
  @Test void testInterpretFunctionWithInitializer() { // 测试带初始化器的标量函数
    SchemaPlus schema = rootSchema().add("s", new AbstractSchema()); // 创建schema
    final ScalarFunction f = // 创建标量函数
        ScalarFunctionImpl.create(Smalls.MY_PLUS_INIT_EVAL_METHOD); // 使用带初始化器的方法
    schema.add("myPlusInit", f); // 将函数添加到schema
    final String sql = "select x, \"s\".\"myPlusInit\"(x, 1)\n" // 定义SQL语句，参数1是常量
        + "from (values (2), (4), (7)) as t (x)"; // 值表：3行
    String[] rows = {"[2, 3]", "[4, 5]", "[7, 8]"}; // 预期结果
    final int n = Smalls.MyPlusInitFunction.INSTANCE_COUNT.get().get(); // 获取初始实例计数
    sql(sql).returnsRows(rows); // 执行SQL并验证结果
    final int n2 = Smalls.MyPlusInitFunction.INSTANCE_COUNT.get().get(); // 获取执行后的实例计数
    assertThat(n2, is(n + 1)); // 断言实例只创建了一次
    final String digest = Smalls.MyPlusInitFunction.THREAD_DIGEST.get(); // 获取参数摘要
    String expectedDigest = "parameterCount=2; " // 预期摘要
        + "argument 0 is not constant; " // 参数0不是常量
        + "argument 1 is constant and has value 1"; // 参数1是常量，值为1
    assertThat(digest, is(expectedDigest)); // 断言摘要匹配

    // Similar, but replace '1' with the expression '3 - 2' // 类似测试，但使用表达式
    final String sql2 = "select x, \"s\".\"myPlusInit\"(x, 3 - 2)\n" // 定义SQL语句，参数是表达式
        + "from (values (2), (4), (7)) as t (x)"; // 值表：3行
    String[] rows2 = {"[2, 102]", "[4, 104]", "[7, 107]"}; // 预期结果（加100）
    sql(sql2).returnsRows(rows2); // 执行SQL并验证结果
    final int n3 = Smalls.MyPlusInitFunction.INSTANCE_COUNT.get().get(); // 获取执行后的实例计数
    assertThat(n3, is(n2 + 1)); // 断言实例只创建了一次
    final String digest2 = Smalls.MyPlusInitFunction.THREAD_DIGEST.get(); // 获取参数摘要
    String expectedDigest2 = "parameterCount=2; " // 预期摘要
        + "argument 0 is not constant; " // 参数0不是常量
        + "argument 1 is not constant"; // 参数1不是常量（因为3-2是表达式）
    assertThat(digest2, is(expectedDigest2)); // 断言摘要匹配
  }

  /** Tests a table function. */
  /** 测试表函数
   * 
   * 表函数是返回表数据的函数，可以像普通表一样在FROM子句中使用
   * Maze函数生成一个迷宫，参数为宽度、高度和种子
   * 
   * SQL: select * from table("s"."Maze"(5, 3, 1))
   * 
   * 执行步骤：
   * 1. 创建schema并添加Maze表函数
   * 2. 调用Maze(5, 3, 1)生成迷宫
   * 3. 查询返回的表
   * 
   * 预期结果：3行数据，包含迷宫的行和生成信息
   */
  @Test void testInterpretTableFunction() { // 测试表函数
    assertThat(rootSchema, notNullValue()); // 断言rootSchema不为null
    SchemaPlus schema = rootSchema.add("s", new AbstractSchema()); // 创建schema
    final TableFunction table1 = TableFunctionImpl.create(Smalls.MAZE_METHOD); // 创建Maze表函数
    assertThat(table1, notNullValue()); // 断言table1不为null
    schema.add("Maze", table1); // 将Maze函数添加到schema
    final String sql = "select *\n" // 定义SQL语句
        + "from table(\"s\".\"Maze\"(5, 3, 1))"; // 调用Maze表函数
    String[] rows = {"[abcde]", "[xyz]", "[generate(w=5, h=3, s=1)]"}; // 预期结果
    sql(sql).returnsRows(rows); // 执行SQL并验证结果
  }

  /** Tests a table function that takes zero arguments.
   *
   * <p>Note that we use {@link Smalls#FIBONACCI_LIMIT_100_TABLE_METHOD}; if we
   * used {@link Smalls#FIBONACCI_TABLE_METHOD}, even with {@code LIMIT 6},
   * we would run out of memory, due to
   * <a href="https://issues.apache.org/jira/browse/CALCITE-4478">[CALCITE-4478]
   * In interpreter, support infinite relations</a>. */
  /** 测试无参数的表函数
   * 
   * 斐波那契数列是一个无限序列，使用LIMIT限制返回的行数
   * 注意：使用FIBONACCI_LIMIT_100_TABLE_METHOD而不是FIBONACCI_TABLE_METHOD，
   * 因为后者会产生无限序列导致内存溢出（CALCITE-4478）
   * 
   * SQL: select * from table("s"."fibonacciLimit100"()) limit 6
   * 
   * 执行步骤：
   * 1. 创建schema并添加fibonacciLimit100表函数
   * 2. 调用fibonacciLimit100()生成斐波那契数列
   * 3. 限制返回6行
   * 
   * 预期结果：斐波那契数列的前6个数字：1, 1, 2, 3, 5, 8
   */
  @Test void testInterpretNilaryTableFunction() { // 测试无参数的表函数
    SchemaPlus schema = rootSchema().add("s", new AbstractSchema()); // 创建schema
    final TableFunction table1 = // 创建表函数
        TableFunctionImpl.create(Smalls.FIBONACCI_LIMIT_100_TABLE_METHOD); // 使用限制100项的斐波那契方法
    assertThat(table1, notNullValue()); // 断言table1不为null
    schema.add("fibonacciLimit100", table1); // 将函数添加到schema
    final String sql = "select *\n" // 定义SQL语句
        + "from table(\"s\".\"fibonacciLimit100\"())\n" // 调用表函数
        + "limit 6"; // 限制返回6行
    String[] rows = {"[1]", "[1]", "[2]", "[3]", "[5]", "[8]"}; // 预期结果：斐波那契数列
    sql(sql).returnsRows(rows); // 执行SQL并验证结果
  }

  /** Tests a table function whose row type is determined by parsing a JSON
   * argument. */
  /** 测试动态类型的表函数
   * 
   * 这个表函数的行类型由JSON参数动态决定
   * JSON参数描述了返回表的字段信息，包括字段名、类型和可空性
   * 
   * SQL: select * from table("s"."dynamicRowTypeTable"('...', 0)) where "i" < 0 and "d" is not null
   * 
   * 执行步骤：
   * 1. 创建schema并添加dynamicRowTypeTable表函数
   * 2. 传入JSON参数定义表结构（i: INTEGER, d: DATE）
   * 3. 查询并过滤数据
   * 
   * 预期结果：空（过滤条件不满足）
   */
  @Test void testInterpretTableFunctionWithDynamicType() { // 测试动态类型的表函数
    SchemaPlus schema = rootSchema().add("s", new AbstractSchema()); // 创建schema
    final TableFunction table1 = // 创建表函数
        TableFunctionImpl.create(Smalls.DYNAMIC_ROW_TYPE_TABLE_METHOD); // 使用动态行类型方法
    assertThat(table1, notNullValue()); // 断言table1不为null
    schema.add("dynamicRowTypeTable", table1); // 将函数添加到schema
    final String sql = "select *\n" // 定义SQL语句
        + "from table(\"s\".\"dynamicRowTypeTable\"('" // 调用表函数，传入JSON参数
        + "{\"nullable\":false,\"fields\":[" // JSON定义表结构
        + "  {\"name\":\"i\",\"type\":\"INTEGER\",\"nullable\":false}," // 字段i：INTEGER，不可空
        + "  {\"name\":\"d\",\"type\":\"DATE\",\"nullable\":true}" // 字段d：DATE，可空
        + "]}', 0))\n" // 结束JSON和参数
        + "where \"i\" < 0 and \"d\" is not null"; // 过滤条件
    sql(sql).returnsRows(); // 验证结果为空
  }

  /** Tests a table function that is a non-static class. */
  /** 测试非静态类的表函数
   * 
   * 这个测试验证解释器能够正确调用非静态类的表函数
   * MyTableFunction是一个非静态类，需要实例化才能使用
   * 
   * SQL: select * from table("s"."t"('=100='))
   * 
   * 执行步骤：
   * 1. 创建schema并添加MyTableFunction表函数
   * 2. 传入参数'=100='
   * 3. 查询返回的表
   * 
   * 预期结果：3行数据，包含1, 3, 100
   */
  @Test void testInterpretNonStaticTableFunction() { // 测试非静态类的表函数
    SchemaPlus schema = rootSchema().add("s", new AbstractSchema()); // 创建schema
    final TableFunction tableFunction = // 创建表函数
        requireNonNull(TableFunctionImpl.create(Smalls.MyTableFunction.class)); // 使用MyTableFunction类
    schema.add("t", tableFunction); // 将函数添加到schema
    final String sql = "select *\n" // 定义SQL语句
        + "from table(\"s\".\"t\"('=100='))"; // 调用表函数
    sql(sql).returnsRows("[1]", "[3]", "[100]"); // 验证结果
  }

  /** Tests projecting zero fields. */

    /** 测试投影零个字段

     * 

     * 这个测试验证解释器能够正确处理没有字段的查询

     * 这种情况虽然少见，但在某些特殊场景下可能出现

     * 

     * 执行步骤：

     * 1. 创建空行（没有字段）

     * 2. 创建空行类型

     * 3. 使用RelBuilder构建关系树

     * 4. 执行查询

     * 

     * 预期结果：两行空数据

     */

    @Test void testZeroFields() { // 测试投影零个字段

  

      final List<RexLiteral> row = ImmutableList.of(); // 创建空行（没有字段）

      final Function<RelBuilder, RelNode> relFn = // 定义关系节点构建函数

          b -> b.values(ImmutableList.of(row, row), // 创建两行空数据

                  b.getTypeFactory().builder().build()) // 构建空行类型

              .build(); // 构建关系节点

      fixture().withRel(relFn) // 使用关系节点构建fixture

          .returnsRows("[]", "[]"); // 验证结果：两行空数据

    }

  }
