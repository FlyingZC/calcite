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
package org.apache.calcite.adapter.jdbc; // 包声明：JDBC适配器包，包含JDBC相关的适配器实现

import org.apache.calcite.DataContext; // 导入：数据上下文，提供执行环境的元数据和参数
import org.apache.calcite.adapter.enumerable.EnumerableRel; // 导入：可枚举关系表达式接口，表示可以转换为LINQ4J枚举的物理关系节点
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor; // 导入：可枚举关系实现器，负责将关系节点转换为可执行代码
import org.apache.calcite.adapter.enumerable.JavaRowFormat; // 导入：Java行格式枚举，定义了行数据的表示方式
import org.apache.calcite.adapter.enumerable.PhysType; // 导入：物理类型接口，表示物理执行时的类型信息
import org.apache.calcite.adapter.enumerable.PhysTypeImpl; // 导入：物理类型实现类
import org.apache.calcite.adapter.enumerable.RexImpTable; // 导入：Rex实现表，包含Rex表达式到Java代码的转换规则
import org.apache.calcite.adapter.java.JavaTypeFactory; // 导入：Java类型工厂，用于创建Java类型
import org.apache.calcite.config.CalciteSystemProperty; // 导入：Calcite系统属性配置
import org.apache.calcite.linq4j.tree.BlockBuilder; // 导入：代码块构建器，用于构建Java代码块
import org.apache.calcite.linq4j.tree.BlockStatement; // 导入：代码块语句，表示一个Java代码块
import org.apache.calcite.linq4j.tree.ConstantExpression; // 导入：常量表达式，表示一个常量值
import org.apache.calcite.linq4j.tree.Expression; // 导入：表达式基类，所有LINQ4J表达式的基类
import org.apache.calcite.linq4j.tree.Expressions; // 导入：表达式工具类，提供创建各种表达式的静态方法
import org.apache.calcite.linq4j.tree.ParameterExpression; // 导入：参数表达式，表示一个方法参数
import org.apache.calcite.linq4j.tree.Primitive; // 导入：基本类型枚举，表示Java基本类型
import org.apache.calcite.linq4j.tree.UnaryExpression; // 导入：一元表达式，表示只有一个操作数的表达式
import org.apache.calcite.plan.ConventionTraitDef; // 导入：约定特征定义，定义了关系节点的调用约定
import org.apache.calcite.plan.RelOptCluster; // 导入：关系优化集群，包含优化器的共享资源
import org.apache.calcite.plan.RelOptCost; // 导入：关系优化成本，表示执行计划的成本估算
import org.apache.calcite.plan.RelOptPlanner; // 导入：关系优化器接口
import org.apache.calcite.plan.RelTraitSet; // 导入：关系特征集合，包含关系节点的所有特征
import org.apache.calcite.rel.RelNode; // 导入：关系节点接口，所有关系节点的基类
import org.apache.calcite.rel.convert.ConverterImpl; // 导入：转换器实现类，用于将一种约定转换为另一种约定
import org.apache.calcite.rel.metadata.RelMetadataQuery; // 导入：关系元数据查询，用于查询关系节点的元数据
import org.apache.calcite.rel.type.RelDataType; // 导入：关系数据类型，表示Calcite中的数据类型
import org.apache.calcite.runtime.Hook; // 导入：钩子，用于在特定执行点插入自定义逻辑
import org.apache.calcite.runtime.SqlFunctions; // 导入：SQL函数工具类，提供SQL相关的辅助函数
import org.apache.calcite.schema.Schemas; // 导入：Schema工具类，提供Schema相关的辅助方法
import org.apache.calcite.sql.SqlDialect; // 导入：SQL方言，表示不同数据库的SQL语法差异
import org.apache.calcite.sql.type.SqlTypeName; // 导入：SQL类型名称枚举，定义了所有SQL数据类型
import org.apache.calcite.sql.util.SqlString; // 导入：SQL字符串，封装了SQL语句及其元数据
import org.apache.calcite.util.BuiltInMethod; // 导入：内置方法，包含Calcite预定义的常用方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入：空值注解，用于标记可能为null的值

import java.lang.reflect.Method; // 导入：反射方法类，用于调用反射方法
import java.lang.reflect.Modifier; // 导入：反射修饰符类，用于获取方法的修饰符
import java.sql.ResultSet; // 导入：JDBC结果集接口，表示查询结果
import java.sql.SQLException; // 导入：SQL异常类，表示SQL执行过程中的错误
import java.util.ArrayList; // 导入：动态数组列表
import java.util.Calendar; // 导入：日历类，用于处理日期时间
import java.util.List; // 导入：列表接口
import java.util.TimeZone; // 导入：时区类，用于处理时区
import java.util.stream.Collectors; // 导入：流收集器，用于流的收集操作
import javax.sql.DataSource; // 导入：数据源接口，表示数据库连接池

import static org.apache.calcite.linq4j.Nullness.castNonNull; // 导入：空值转换工具，用于将可能为null的值转换为非null

import static java.util.Objects.requireNonNull; // 导入：对象工具，用于检查对象是否为null

/**
 * Relational expression representing a scan of a table in a JDBC data source.
 * 表示JDBC数据源中表扫描的关系表达式
 * 
 * 这个类是Calcite适配器体系中的关键组件，负责将JDBC关系节点（JdbcRel）转换为可枚举的关系节点（EnumerableRel）
 * 主要功能：
 * 1. 将JDBC约定（JDBC convention）转换为可枚举约定（Enumerable convention）
 * 2. 生成执行JDBC查询的Java代码
 * 3. 处理JDBC ResultSet到Java对象的转换
 * 4. 支持动态参数和预处理语句
 * 5. 处理不同数据库方言的SQL生成
 * 
 * 工作流程：
 * 1. 接收JdbcRel子节点（如JdbcTableScan、JdbcFilter等）
 * 2. 通过JdbcImplementor生成SQL语句
 * 3. 生成调用DataSource执行SQL的代码
 * 4. 生成从ResultSet读取数据并转换为Java对象的代码
 * 5. 返回可枚举的结果集
 * 
 * 应用场景：
 * - 当查询需要从JDBC数据源读取数据时
 * - 当优化器决定将部分查询下推到数据库执行时
 * - 当需要将JDBC结果转换为Calcite可处理的格式时
 */
public class JdbcToEnumerableConverter // 类定义：JDBC到可枚举转换器，继承自ConverterImpl，实现EnumerableRel接口
    extends ConverterImpl // 继承：转换器实现基类，提供约定转换的基础功能
    implements EnumerableRel { // 实现：可枚举关系接口，表示可以生成LINQ4J枚举代码的关系节点
  
  /**
   * 构造方法：创建JDBC到可枚举转换器实例
   * 
   * @param cluster 关系优化集群，包含优化器的共享资源和类型工厂
   *               用于共享优化器状态、类型系统等资源
   * @param traits 关系特征集合，定义了此节点的一组特征
   *               必须包含JDBC约定（输入）和可枚举约定（输出）
   * @param input 输入关系节点，通常是JdbcRel类型的节点
   *              表示需要转换的JDBC关系表达式（如JdbcTableScan、JdbcFilter等）
   */
  protected JdbcToEnumerableConverter( // 构造方法声明：protected修饰，允许子类访问
      RelOptCluster cluster, // 参数：关系优化集群
      RelTraitSet traits, // 参数：关系特征集合
      RelNode input) { // 参数：输入关系节点
    super(cluster, ConventionTraitDef.INSTANCE, traits, input); // 调用父类构造方法：传入集群、约定特征定义、特征集合和输入节点
  } // 构造方法结束

  /**
   * 复制方法：创建当前节点的一个副本，可以修改特征集合
   * 
   * @param traitSet 新的特征集合，用于替换当前节点的特征
   *                 可以包含不同的约定、排序、分布等特征
   * @param inputs 新的输入节点列表，用于替换当前节点的输入
   *               对于转换器，通常只有一个输入节点
   * @return 新的JdbcToEnumerableConverter实例，具有指定的特征集合和输入
   */
  @Override public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) { // 方法声明：重写父类的copy方法，返回关系节点
    return new JdbcToEnumerableConverter( // 返回：创建新的JdbcToEnumerableConverter实例
        getCluster(), traitSet, sole(inputs)); // 参数：使用当前集群、新特征集合、新输入节点（sole确保只有一个输入）
  } // copy方法结束

  /**
   * 计算自身成本方法：估算执行此转换器的成本
   * 
   * 这个方法的成本计算策略是：将父类计算的成本乘以0.1
   * 原因：
   * 1. 转换器本身不执行实际的数据操作，只是一个"桥梁"
   * 2. 实际的数据读取和转换工作在子节点中完成
   * 3. 降低转换器的成本可以鼓励优化器使用这种转换
   * 
   * @param planner 关系优化器，用于访问优化器相关的上下文信息
   * @param mq 关系元数据查询，用于查询输入节点的元数据（如行数、大小等）
   * @return 转换器的执行成本，如果无法计算则返回null
   */
  @Override public @Nullable RelOptCost computeSelfCost(RelOptPlanner planner, // 方法声明：重写父类的computeSelfCost方法
      RelMetadataQuery mq) { // 参数：关系元数据查询
    RelOptCost cost = super.computeSelfCost(planner, mq); // 调用父类方法计算基础成本
    if (cost == null) { // 如果基础成本为null
      return null; // 返回null，表示无法计算成本
    } // if结束
    return cost.multiplyBy(.1); // 将成本乘以0.1后返回，降低转换器的权重
  } // computeSelfCost方法结束

  /**
   * 实现方法：将此关系节点转换为可执行的Java代码
   * 
   * 这是整个转换过程的核心方法，负责生成执行JDBC查询的完整代码
   * 
   * 生成的代码结构：
   * 1. 创建DataSource和SQL语句
   * 2. 创建PreparedStatement（如果有动态参数）
   * 3. 执行查询获取ResultSet
   * 4. 从ResultSet中读取每一行数据
   * 5. 将每行数据转换为Java对象
   * 6. 返回可枚举的结果集
   * 
   * 生成的代码示例：
   * ```
   * DataSource dataSource = schema.getDataSource();
   * String sql = "select empno, ename from emp";
   * Function1<ResultSet, Object[]> rowBuilder = resultSet -> {
   *   Object[] values = new Object[2];
   *   values[0] = resultSet.getInt(1);
   *   values[1] = resultSet.getString(2);
   *   return values;
   * };
   * return ResultSetEnumerable.of(dataSource, sql, rowBuilder);
   * ```
   * 
   * @param implementor 可枚举关系实现器，用于访问类型工厂、变量注册等
   * @param pref 行格式偏好，指定生成的Java代码中行的表示方式
   * @return 实现结果，包含物理类型和生成的代码块
   */
  @Override public Result implement(EnumerableRelImplementor implementor, Prefer pref) { // 方法声明：重写接口的implement方法
    // Generate:
    //   ResultSetEnumerable.of(schema.getDataSource(), "select ...")
    // 注释：生成调用ResultSetEnumerable.of的代码，用于执行SQL查询
    final BlockBuilder builder0 = new BlockBuilder(false); // 创建代码块构建器：false表示不自动添加返回语句
    final JdbcRel child = (JdbcRel) getInput(); // 获取输入节点：强制转换为JdbcRel类型，因为输入必须是JDBC约定
    final PhysType physType = // 创建物理类型：描述行数据在Java中的表示方式
        PhysTypeImpl.of( // 使用PhysTypeImpl工厂方法创建
            implementor.getTypeFactory(), // 使用实现器的类型工厂
            getRowType(), // 使用当前节点的行类型
            pref.prefer(JavaRowFormat.CUSTOM)); // 使用自定义行格式偏好
    final JdbcConvention jdbcConvention = // 获取JDBC约定：包含数据源和方言信息
        (JdbcConvention) requireNonNull(child.getConvention(), // 获取子节点的约定并检查非null
            () -> "child.getConvention() is null for " + child); // 如果为null，提供详细的错误信息
    JdbcCorrelationDataContextBuilderImpl dataContextBuilder = // 创建数据上下文构建器：用于处理关联查询的相关变量
        new JdbcCorrelationDataContextBuilderImpl(implementor, builder0, DataContext.ROOT); // 参数：实现器、代码块构建器、根数据上下文
    SqlString sqlString = generateSql(jdbcConvention.dialect, dataContextBuilder); // 生成SQL字符串：使用方言和数据上下文构建器
    String sql = sqlString.getSql(); // 获取纯SQL语句字符串
    if (CalciteSystemProperty.DEBUG.value()) { // 如果调试模式开启
      System.out.println("[" + sql + "]"); // 打印生成的SQL语句，便于调试
    } // if结束
    Hook.QUERY_PLAN.run(sql); // 运行查询计划钩子：允许外部监听器捕获生成的SQL
    final Expression sql_ = // 创建SQL常量表达式：将SQL字符串包装为表达式
        builder0.append("sql", Expressions.constant(sql)); // 添加到代码块，变量名为"sql"
    final int fieldCount = getRowType().getFieldCount(); // 获取字段数量：行类型中的字段总数
    BlockBuilder builder = new BlockBuilder(); // 创建内部代码块构建器：用于构建行数据处理逻辑
    final ParameterExpression resultSet_ = // 创建ResultSet参数表达式：表示从数据库获取的结果集
        Expressions.parameter(Modifier.FINAL, ResultSet.class, // 参数：final修饰，类型为ResultSet
            builder.newName("resultSet")); // 自动生成唯一的参数名
    final SqlDialect.CalendarPolicy calendarPolicy = // 获取日历策略：数据库方言对日期时间的处理策略
        jdbcConvention.dialect.getCalendarPolicy(); // 从JDBC约定中获取方言的日历策略
    final Expression calendar_; // 声明日历表达式：可能为null，取决于日历策略
    switch (calendarPolicy) { // 根据日历策略进行分支处理
    case LOCAL: // 如果策略是LOCAL（使用本地时区）
      calendar_ = // 创建日历实例：使用本地时区
          builder0.append("calendar", // 添加到代码块，变量名为"calendar"
              Expressions.call(Calendar.class, "getInstance", // 调用Calendar.getInstance方法
                  getTimeZoneExpression(implementor))); // 传入时区表达式
      break; // 跳出switch
    default: // 其他策略（NULL、DIRECT、SHIFT等）
      calendar_ = null; // 不需要日历对象，设置为null
    } // switch结束
    if (fieldCount == 0) { // 如果字段数量为0（例如：SELECT 1 FROM dual或只返回行数的查询）
      // return (Object) null;
      // 注释：生成的代码直接返回null，因为没有实际数据字段
      final ParameterExpression value_ = // 创建值参数表达式：用于存储返回值
          Expressions.parameter(Object.class, builder.newName("value")); // 类型为Object，自动生成变量名
      builder.add(Expressions.declare(Modifier.FINAL, value_, null)); // 声明final变量并初始化为null
      builder.add(Expressions.return_(null, value_)); // 添加返回语句，返回null
    } else if (fieldCount == 1) { // 如果只有一个字段（单列查询）
      final ParameterExpression value_ = // 创建值参数表达式：用于存储单个字段的值
          Expressions.parameter(Object.class, builder.newName("value")); // 类型为Object，自动生成变量名
      builder.add(Expressions.declare(Modifier.FINAL, value_, null)); // 声明final变量并初始化为null
      generateGet(implementor, physType, builder, resultSet_, 0, value_, // 生成获取字段值的代码：字段索引为0
          calendar_, calendarPolicy); // 传入日历表达式和策略
      builder.add(Expressions.return_(null, value_)); // 添加返回语句，返回单个字段值
    } else { // 如果有多个字段（多列查询）
      final Expression values_ = // 创建数组表达式：用于存储所有字段的值
          builder.append("values", // 添加到代码块，变量名为"values"
              Expressions.newArrayBounds(Object.class, 1, // 创建Object类型的一维数组
                  Expressions.constant(fieldCount))); // 数组长度为字段数量
      for (int i = 0; i < fieldCount; i++) { // 遍历所有字段
        generateGet(implementor, physType, builder, resultSet_, i, // 生成获取第i个字段值的代码
            Expressions.arrayIndex(values_, Expressions.constant(i)), // 存储到数组的第i个位置
            calendar_, calendarPolicy); // 传入日历表达式和策略
      } // for循环结束
      builder.add( // 添加返回语句
          Expressions.return_(null, values_)); // 返回包含所有字段值的数组
    } // if-else结束
    final ParameterExpression e_ = // 创建SQLException参数表达式：用于捕获SQL异常
        Expressions.parameter(SQLException.class, builder.newName("e")); // 类型为SQLException，自动生成异常变量名
    BlockStatement valueBlock; // 声明值块语句：表示处理单行数据的代码块
    if (fieldCount == 0) { // 如果字段数量为0
      // For some queries, we don't need to rely on a specific column value in the data source,
      // we only need to know the number of rows in the table.
      // 注释：某些查询不需要依赖数据源中的特定列值，只需要知道表中的行数
      // For example: "select row_number() over () from dept"
      // 注释：例如：查询行号的SQL
      // we can't push down the "row_number() over ()"
      // 注释：我们无法将"row_number() over()"下推到数据库
      // So the generated code should be:
      // 注释：所以生成的代码应该是：
      // public org.apache.calcite.linq4j.function.Function0 apply(
      //    final java.sql.ResultSet resultSet) {
      //  return new org.apache.calcite.linq4j.function.Function0() {
      //    public Object apply() {
      //      return (Object) null;
      //    }
      //  };
      //  }
      // 注释：生成的Lambda函数直接返回null
      valueBlock = builder.toBlock(); // 将构建器转换为代码块（不需要异常处理）
    } else { // 如果有字段
      // public org.apache.calcite.linq4j.function.Function0 apply(
      //    final java.sql.ResultSet resultSet) {
      //  return new org.apache.calcite.linq4j.function.Function0() {
      //    public Object apply() {
      //      try {
      //        return resultSet.getString(1);
      //      } catch (java.sql.SQLException e) {
      //        throw new RuntimeException(
      //            e);
      //      }
      //    }
      //  };
      //  }
      // 注释：生成的Lambda函数包含try-catch异常处理
      valueBlock = // 创建带异常处理的代码块
          Expressions.block( // 创建代码块
              Expressions.tryCatch( // 创建try-catch表达式
              builder.toBlock(), // try块：读取字段值的代码
              Expressions.catch_( // catch块：捕获SQLException
                  e_, // 异常参数
                  Expressions.throw_( // 抛出异常
                      Expressions.new_( // 创建RuntimeException实例
                          RuntimeException.class, // 异常类型
                          e_))))); // 将SQLException包装为RuntimeException
    } // if-else结束
    final Expression rowBuilderFactory_ = // 创建行构建器工厂表达式：这是一个Lambda函数，接收ResultSet并返回行构建器
        builder0.append("rowBuilderFactory", // 添加到代码块，变量名为"rowBuilderFactory"
            Expressions.lambda( // 创建Lambda表达式
                Expressions.block( // Lambda体：代码块
                    Expressions.return_(null, // 返回语句
                        Expressions.lambda(valueBlock))), // 返回另一个Lambda：处理单行数据
                resultSet_)); // Lambda参数：ResultSet参数

    final Expression enumerable; // 声明可枚举表达式：表示最终生成的可枚举结果集

    if (sqlString.getDynamicParameters() != null // 如果SQL包含动态参数（占位符）
        && !sqlString.getDynamicParameters().isEmpty()) { // 并且动态参数列表不为空
      final Expression preparedStatementConsumer_ = // 创建预处理语句消费者表达式：用于设置预处理语句的参数值
          builder0.append("preparedStatementConsumer", // 添加到代码块，变量名为"preparedStatementConsumer"
              Expressions.call(BuiltInMethod.CREATE_ENRICHER.method, // 调用CREATE_ENRICHER方法创建参数设置器
                  Expressions.newArrayInit(Integer.class, 1, // 创建Integer数组：存储动态参数的索引
                      toIndexesTableExpression(sqlString)), // 将动态参数索引转换为表达式数组
                  dataContextBuilder.build())); // 传入数据上下文构建器的结果，用于获取参数值

      enumerable = // 创建可枚举对象：使用预处理语句版本
          builder0.append("enumerable", // 添加到代码块，变量名为"enumerable"
              Expressions.call( // 调用方法创建ResultSetEnumerable
                  BuiltInMethod.RESULT_SET_ENUMERABLE_OF_PREPARED.method, // 方法：使用预处理语句的枚举创建方法
                  Schemas.unwrap(jdbcConvention.expression, DataSource.class), // 参数1：解包获取DataSource对象
                  sql_, // 参数2：SQL语句
                  rowBuilderFactory_, // 参数3：行构建器工厂
                  preparedStatementConsumer_)); // 参数4：预处理语句参数设置器
    } else { // 如果SQL不包含动态参数
      enumerable = // 创建可枚举对象：使用普通语句版本
          builder0.append("enumerable", // 添加到代码块，变量名为"enumerable"
              Expressions.call( // 调用方法创建ResultSetEnumerable
                  BuiltInMethod.RESULT_SET_ENUMERABLE_OF.method, // 方法：普通语句的枚举创建方法
                  Schemas.unwrap(jdbcConvention.expression, DataSource.class), // 参数1：解包获取DataSource对象
                  sql_, // 参数2：SQL语句
                  rowBuilderFactory_)); // 参数3：行构建器工厂
    } // if-else结束
    builder0.add( // 添加语句：设置查询超时
        Expressions.statement( // 创建语句表达式
            Expressions.call(enumerable, // 调用可枚举对象的方法
                BuiltInMethod.RESULT_SET_ENUMERABLE_SET_TIMEOUT.method, // 方法：设置超时
                DataContext.ROOT))); // 参数：从数据上下文获取超时配置
    builder0.add( // 添加返回语句
        Expressions.return_(null, enumerable)); // 返回可枚举对象
    return implementor.result(physType, builder0.toBlock()); // 返回实现结果：包含物理类型和生成的代码块
  } // implement方法结束

  /**
   * 将动态参数索引转换为常量表达式列表
   * 
   * 此方法用于处理包含动态参数的SQL语句（如"SELECT * FROM emp WHERE deptno = ?"）
   * 动态参数在SQL中用?表示，需要从数据上下文中获取值并设置到预处理语句中
   * 
   * 工作原理：
   * 1. 从SqlString中获取动态参数的索引列表（如[0]表示第一个?）
   * 2. 将每个索引转换为常量表达式
   * 3. 返回常量表达式列表，用于生成设置参数的代码
   * 
   * 示例：
   * 输入：SqlString包含动态参数索引[0, 1]
   * 输出：[ConstantExpression(0), ConstantExpression(1)]
   * 生成的代码：preparedStatement.setInt(1, value0); preparedStatement.setInt(2, value1);
   * 
   * @param sqlString SQL字符串对象，包含动态参数的索引信息
   * @return 常量表达式列表，每个表达式代表一个动态参数的索引
   */
  private static List<ConstantExpression> toIndexesTableExpression(SqlString sqlString) { // 方法声明：私有静态方法
    return requireNonNull(sqlString.getDynamicParameters(), // 获取动态参数列表并检查非null
        () -> "sqlString.getDynamicParameters() is null for " + sqlString).stream() // 如果为null，提供详细错误信息；然后转换为流
        .map(Expressions::constant) // 将每个索引转换为常量表达式
        .collect(Collectors.toList()); // 收集为列表
  } // toIndexesTableExpression方法结束

  /**
   * 获取时区表达式：从数据上下文中提取时区信息
   * 
   * 时区信息对于正确处理日期时间类型非常重要，因为不同数据库对时区的处理方式不同
   * 
   * 工作原理：
   * 1. 获取实现器的根表达式（通常是DataContext对象）
   * 2. 调用DataContext的get方法，传入"timeZone"键
   * 3. 将返回值转换为TimeZone类型
   * 
   * 生成的代码示例：
   * ```
   * TimeZone timeZone = (TimeZone) dataContext.get("timeZone");
   * ```
   * 
   * @param implementor 可枚举关系实现器，提供对根表达式和数据上下文的访问
   * @return 时区表达式，表示从数据上下文获取的TimeZone对象
   */
  private static UnaryExpression getTimeZoneExpression( // 方法声明：私有静态方法，返回一元表达式
      EnumerableRelImplementor implementor) { // 参数：可枚举关系实现器
    return Expressions.convert_( // 返回：类型转换表达式
        Expressions.call( // 创建方法调用表达式
            implementor.getRootExpression(), // 对象：根表达式（DataContext）
            "get", // 方法名：get方法
            Expressions.constant("timeZone")), // 参数：键名"timeZone"
        TimeZone.class); // 目标类型：TimeZone类
  } // getTimeZoneExpression方法结束

  /**
   * 生成从ResultSet中获取单个字段值的代码
   * 
   * 这是整个转换过程中最复杂的方法之一，负责处理各种数据类型的读取
   * 
   * 支持的数据类型：
   * 1. 基本类型（int, long, double等）及其包装类
   * 2. 日期时间类型（DATE, TIME, TIMESTAMP）
   * 3. 数组类型（ARRAY）
   * 4. 可空类型（可能返回null）
   * 
   * 日期时间处理策略：
   * - LOCAL：使用本地时区，传入Calendar对象
   * - NULL：不指定日历，使用JDBC默认行为
   * - DIRECT：直接获取，不进行转换
   * - SHIFT：需要时区偏移转换
   * 
   * 生成的代码示例：
   * ```
   * // 基本类型
   * value = resultSet.getInt(1);
   * if (resultSet.wasNull()) value = null;
   * 
   * // 日期类型
   * value = SqlFunctions.dateToInt(resultSet.getDate(1, calendar));
   * 
   * // 数组类型
   * value = SqlFunctions.jdbcArrayToList((Array) resultSet.getObject(1));
   * ```
   * 
   * @param implementor 可枚举关系实现器，提供类型工厂等资源
   * @param physType 物理类型，包含Java类型信息
   * @param builder 代码块构建器，用于添加生成的代码
   * @param resultSet_ ResultSet参数表达式，表示查询结果集
   * @param i 字段索引（从0开始），但JDBC的getXXX方法使用从1开始的索引
   * @param target 目标表达式，用于存储读取的字段值（如变量或数组元素）
   * @param calendar_ 日历表达式，可能为null，取决于日历策略
   * @param calendarPolicy 日历策略，决定如何处理日期时间类型
   */
  private static void generateGet(EnumerableRelImplementor implementor, // 方法声明：私有静态方法
      PhysType physType, BlockBuilder builder, ParameterExpression resultSet_, // 参数：物理类型、代码块构建器、ResultSet参数
      int i, Expression target, @Nullable Expression calendar_, // 参数：字段索引、目标表达式、日历表达式（可空）
      SqlDialect.CalendarPolicy calendarPolicy) { // 参数：日历策略
    final Primitive primitive = Primitive.ofBoxOr(physType.fieldClass(i)); // 获取基本类型信息：处理基本类型和包装类
    final RelDataType fieldType = // 获取字段类型信息
        physType.getRowType().getFieldList().get(i).getType(); // 从行类型中获取第i个字段的类型
    final List<Expression> dateTimeArgs = new ArrayList<>(); // 创建日期时间参数列表：用于存储getXXX方法的参数
    dateTimeArgs.add(Expressions.constant(i + 1)); // 添加字段索引：JDBC使用从1开始的索引，所以是i+1
    SqlTypeName sqlTypeName = fieldType.getSqlTypeName(); // 获取SQL类型名称：如DATE, TIMESTAMP, VARCHAR等
    boolean offset = false; // 初始化偏移标志：是否需要时区偏移转换
    switch (calendarPolicy) { // 根据日历策略进行分支处理
    case LOCAL: // 如果策略是LOCAL（使用本地时区）
      requireNonNull(calendar_, "calendar_"); // 检查calendar_不为null
      dateTimeArgs.add(calendar_); // 添加日历对象作为参数
      break; // 跳出switch
    case NULL: // 如果策略是NULL（不指定日历）
      // We don't specify a calendar at all, so we don't add an argument and
      // instead use the version of the getXXX that doesn't take a Calendar
      // 注释：我们不指定日历，所以不添加参数，而是使用不带Calendar参数的getXXX版本
      break; // 跳出switch
    case DIRECT: // 如果策略是DIRECT（直接获取）
      sqlTypeName = SqlTypeName.ANY; // 将类型设置为ANY，表示不进行特殊处理
      break; // 跳出switch
    case SHIFT: // 如果策略是SHIFT（需要时区偏移）
      switch (sqlTypeName) { // 根据SQL类型名称进一步判断
      case TIMESTAMP: // 如果是时间戳类型
      case DATE: // 如果是日期类型
        offset = true; // 设置偏移标志为true，需要进行时区转换
        break; // 跳出内层switch
      default: // 其他类型
        break; // 跳出内层switch
      } // 内层switch结束
      break; // 跳出外层switch
    default: // 默认情况
      break; // 跳出switch
    } // switch结束
    final Expression source; // 声明源表达式：表示从ResultSet读取的值
    switch (sqlTypeName) { // 根据SQL类型名称生成不同的读取代码
    case DATE: // 如果是日期类型
    case TIME: // 如果是时间类型
    case TIMESTAMP: // 如果是时间戳类型
      source = // 生成日期时间类型的读取代码
          Expressions.call( // 调用转换方法
              getMethod(sqlTypeName, fieldType.isNullable(), offset), // 获取对应的转换方法（考虑可空性和偏移）
              Expressions.<Expression>list() // 创建表达式列表
                  .append( // 添加第一个参数：从ResultSet读取原始值
                      Expressions.call(resultSet_, // 调用ResultSet的方法
                          getMethod2(sqlTypeName), dateTimeArgs)) // 使用对应的getXXX方法（getDate, getTime等）
                  .appendIf(offset, getTimeZoneExpression(implementor))); // 如果需要偏移，添加时区表达式
      break; // 跳出switch
    case ARRAY: // 如果是数组类型
      final Expression x = // 创建临时表达式：从ResultSet读取Array对象
          Expressions.convert_( // 类型转换
              Expressions.call(resultSet_, jdbcGetMethod(primitive), // 调用getObject方法
                  Expressions.constant(i + 1)), // 字段索引
              java.sql.Array.class); // 转换为java.sql.Array类型
      source = Expressions.call(BuiltInMethod.JDBC_ARRAY_TO_LIST.method, x); // 调用工具方法将Array转换为List
      break; // 跳出switch
    case NULL: // 如果类型是NULL
      source = RexImpTable.NULL_EXPR; // 使用null表达式
      break; // 跳出switch
    default: // 默认情况：普通类型（VARCHAR, INTEGER, DOUBLE等）
      source = // 生成普通类型的读取代码
          Expressions.call(resultSet_, jdbcGetMethod(primitive), // 调用对应的getXXX方法（getString, getInt等）
              Expressions.constant(i + 1)); // 字段索引
    } // switch结束
    builder.add( // 添加赋值语句：将读取的值赋给目标变量
        Expressions.statement( // 创建语句表达式
            Expressions.assign( // 创建赋值表达式
                target, source))); // 将源值赋给目标

    // [CALCITE-596] If primitive type columns contain null value, returns null
    // object
    // 注释：[CALCITE-596] 如果基本类型列包含null值，返回null对象
    if (primitive != null) { // 如果是基本类型（int, long等）
      builder.add( // 添加null检查代码
          Expressions.ifThen( // 创建if-then表达式
              Expressions.call(resultSet_, "wasNull"), // 条件：检查上一个读取的值是否为null
              Expressions.statement( // then语句块
                  Expressions.assign(target, // 将目标变量赋值为null
                      Expressions.constant(null))))); // null常量
    } // if结束
  } // generateGet方法结束

  /**
   * 获取日期时间类型的转换方法
   * 
   * 此方法根据SQL类型名称、可空性和偏移标志选择合适的转换方法
   * 
   * 方法选择逻辑：
   * 1. DATE类型：转换为int（天数）
   * 2. TIME类型：转换为int（毫秒数）
   * 3. TIMESTAMP类型：转换为long（毫秒数）
   * 
   * 方法命名规则：
   * - OPTIONAL：表示值可能为null
   * - OFFSET：表示需要时区偏移转换
   * - 不带这些后缀：表示值不可能为null且不需要时区转换
   * 
   * 示例：
   * - DATE_TO_INT：将非null日期转换为int
   * - DATE_TO_INT_OPTIONAL：将可能为null的日期转换为int
   * - TIMESTAMP_TO_LONG_OFFSET：将非null时间戳转换为long并应用时区偏移
   * 
   * @param sqlTypeName SQL类型名称（DATE, TIME, TIMESTAMP）
   * @param nullable 是否可空，true表示值可能为null
   * @param offset 是否需要时区偏移，true表示需要进行时区转换
   * @return 对应的转换方法（反射Method对象）
   */
  private static Method getMethod(SqlTypeName sqlTypeName, boolean nullable, // 方法声明：私有静态方法
      boolean offset) { // 参数：SQL类型名称、可空标志、偏移标志
    switch (sqlTypeName) { // 根据SQL类型名称选择方法
    case DATE: // 如果是日期类型
      return (nullable // 根据可空性选择
          ? (offset // 如果可空，再根据偏移选择
          ? BuiltInMethod.DATE_TO_INT_OPTIONAL_OFFSET // 可空+偏移
          : BuiltInMethod.DATE_TO_INT_OPTIONAL) // 可空+无偏移
          : (offset // 如果不可空，再根据偏移选择
              ? BuiltInMethod.DATE_TO_INT_OFFSET // 不可空+偏移
              : BuiltInMethod.DATE_TO_INT)).method; // 不可空+无偏移
    case TIME: // 如果是时间类型
      return (nullable // 根据可空性选择
          ? BuiltInMethod.TIME_TO_INT_OPTIONAL // 可空
          : BuiltInMethod.TIME_TO_INT).method; // 不可空
    case TIMESTAMP: // 如果是时间戳类型
      return (nullable // 根据可空性选择
          ? (offset // 如果可空，再根据偏移选择
          ? BuiltInMethod.TIMESTAMP_TO_LONG_OPTIONAL_OFFSET // 可空+偏移
          : BuiltInMethod.TIMESTAMP_TO_LONG_OPTIONAL) // 可空+无偏移
          : (offset // 如果不可空，再根据偏移选择
              ? BuiltInMethod.TIMESTAMP_TO_LONG_OFFSET // 不可空+偏移
              : BuiltInMethod.TIMESTAMP_TO_LONG)).method; // 不可空+无偏移
    default: // 默认情况（不应该发生）
      throw new AssertionError(sqlTypeName + ":" + nullable); // 抛出断言错误
    } // switch结束
  } // getMethod方法结束

  /**
   * 获取ResultSet的getXXX方法（用于读取日期时间类型）
   * 
   * 此方法返回JDBC ResultSet接口中读取日期时间类型的方法
   * 这些方法会接收Calendar参数（如果需要）
   * 
   * 方法映射：
   * - DATE -> getDate(int, Calendar)
   * - TIME -> getTime(int, Calendar)
   * - TIMESTAMP -> getTimestamp(int, Calendar)
   * 
   * 注意：
   * 这些方法名中的"2"后缀表示它们是带Calendar参数的版本
   * 不带Calendar参数的版本使用的是getMethod方法返回的方法
   * 
   * @param sqlTypeName SQL类型名称（DATE, TIME, TIMESTAMP）
   * @return 对应的ResultSet方法（反射Method对象）
   */
  private static Method getMethod2(SqlTypeName sqlTypeName) { // 方法声明：私有静态方法
    switch (sqlTypeName) { // 根据SQL类型名称选择方法
    case DATE: // 如果是日期类型
      return BuiltInMethod.RESULT_SET_GET_DATE2.method; // 返回getDate(int, Calendar)方法
    case TIME: // 如果是时间类型
      return BuiltInMethod.RESULT_SET_GET_TIME2.method; // 返回getTime(int, Calendar)方法
    case TIMESTAMP: // 如果是时间戳类型
      return BuiltInMethod.RESULT_SET_GET_TIMESTAMP2.method; // 返回getTimestamp(int, Calendar)方法
    default: // 默认情况（不应该发生）
      throw new AssertionError(sqlTypeName); // 抛出断言错误
    } // switch结束
  } // getMethod2方法结束

  /** E,g, {@code jdbcGetMethod(int)} returns "getInt". */
  /**
   * 获取JDBC的getXXX方法名
   * 
   * 此方法根据Java基本类型返回对应的ResultSet方法名
   * 
   * 方法名映射规则：
   * - int -> "getInt"
   * - long -> "getLong"
   * - double -> "getDouble"
   * - String -> "getString"
   * - null -> "getObject"（用于非基本类型）
   * 
   * 工作原理：
   * 1. 如果primitive为null，返回"getObject"（用于复杂类型）
   * 2. 否则，获取基本类型的名称（如"int"）
   * 3. 将首字母大写（initcap）
   * 4. 拼接"get"前缀
   * 
   * @param primitive 基本类型枚举，可能为null
   * @return 对应的ResultSet方法名（如"getInt", "getString"等）
   */
  private static String jdbcGetMethod(@Nullable Primitive primitive) { // 方法声明：私有静态方法
    return primitive == null // 如果primitive为null
        ? "getObject" // 返回"getObject"方法名
        : "get" + SqlFunctions.initcap(castNonNull(primitive.primitiveName)); // 否则：拼接"get"和首字母大写的类型名
  } // jdbcGetMethod方法结束

  /**
   * 生成SQL语句：将关系表达式转换为SQL字符串
   * 
   * 这是整个转换过程的关键步骤，负责将Calcite的关系表达式树转换为可执行的SQL语句
   * 
   * 工作流程：
   * 1. 创建JdbcImplementor实例：负责遍历关系表达式树并生成SQL
   * 2. 传入SQL方言：确保生成的SQL符合目标数据库的语法
   * 3. 传入类型工厂：用于处理类型转换
   * 4. 传入数据上下文构建器：用于处理关联查询的相关变量
   * 5. 从输入节点开始遍历：visitRoot会递归遍历整个关系表达式树
   * 6. 将结果转换为SQL字符串：asStatement().toSqlString()
   * 
   * 支持的SQL特性：
   * - SELECT语句（投影、过滤、连接、聚合、排序等）
   * - 子查询
   * - 动态参数（占位符）
   * - 关联查询（相关子查询）
   * 
   * @param dialect SQL方言，定义了目标数据库的SQL语法规则
   * @param dataContextBuilder 数据上下文构建器，用于处理关联查询的相关变量
   * @return SQL字符串对象，包含SQL语句和动态参数信息
   */
  private SqlString generateSql(SqlDialect dialect, // 方法声明：私有方法
      JdbcCorrelationDataContextBuilder dataContextBuilder) { // 参数：SQL方言、数据上下文构建器
    final JdbcImplementor jdbcImplementor = // 创建JDBC实现器：负责生成SQL
        new JdbcImplementor(dialect, // 参数：SQL方言
            (JavaTypeFactory) getCluster().getTypeFactory(), // 参数：类型工厂（转换为Java类型工厂）
            dataContextBuilder); // 参数：数据上下文构建器
    final JdbcImplementor.Result result = // 访问输入节点并生成SQL结果
        jdbcImplementor.visitRoot(this.getInput()); // 从输入节点开始遍历关系表达式树
    return result.asStatement().toSqlString(dialect); // 将结果转换为SQL字符串对象
  } // generateSql方法结束
} // JdbcToEnumerableConverter类结束
