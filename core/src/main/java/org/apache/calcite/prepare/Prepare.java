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
package org.apache.calcite.prepare;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.avatica.Meta;
import org.apache.calcite.jdbc.CalcitePrepare;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.CalciteSchema.LatticeEntry;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptLattice;
import org.apache.calcite.plan.RelOptMaterialization;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexExecutorImpl;
import org.apache.calcite.runtime.Bindable;
import org.apache.calcite.runtime.Hook;
import org.apache.calcite.runtime.Typed;
import org.apache.calcite.schema.ColumnStrategy;
import org.apache.calcite.schema.ExtensibleTable;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.Wrapper;
import org.apache.calcite.schema.impl.ModifiableViewTable;
import org.apache.calcite.schema.impl.StarTable;
import org.apache.calcite.sql.SqlExplain;
import org.apache.calcite.sql.SqlExplainFormat;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorCatalogReader;
import org.apache.calcite.sql.validate.SqlValidatorTable;
import org.apache.calcite.sql2rel.ConvertToChecked;
import org.apache.calcite.sql2rel.InitializerContext;
import org.apache.calcite.sql2rel.InitializerExpressionFactory;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.tools.Program;
import org.apache.calcite.tools.Programs;
import org.apache.calcite.util.Holder;
import org.apache.calcite.util.TryThreadLocal;
import org.apache.calcite.util.trace.CalciteTimingTracer;
import org.apache.calcite.util.trace.CalciteTrace;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.calcite.linq4j.Nullness.castNonNull;
import static org.apache.calcite.sql2rel.SqlToRelConverter.DEFAULT_IN_SUB_QUERY_THRESHOLD;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base for classes that implement
 * the process of preparing and executing SQL expressions.
 * 抽象基类,用于实现SQL表达式的准备和执行过程
 * Prepare类是Calcite中SQL语句准备阶段的核心抽象类,负责将SQL语句转换为可执行的计划
 * 主要职责包括:
 * 1. SQL语句的验证和转换(从SqlNode到RelNode)
 * 2. 查询优化(使用优化器对关系表达式进行优化)
 * 3. 物理计划的生成(将逻辑计划转换为可执行的物理计划)
 * 4. 物化视图和Lattice的处理
 * 5. EXPLAIN语句的特殊处理
 * 该类为不同的实现提供了统一的接口和框架,支持多种数据源和执行方式
 */
public abstract class Prepare {
  protected static final Logger LOGGER = CalciteTrace.getStatementTracer(); // 日志记录器,用于记录语句执行过程中的调试信息,使用Calcite的语句跟踪器

  protected final CalcitePrepare.Context context; // Calcite准备上下文,包含类型工厂、数据上下文、配置等环境信息,是整个准备过程的核心上下文对象
  protected final CatalogReader catalogReader; // 目录读取器,用于读取表、字段等元数据信息,实现了RelOptSchema和SqlValidatorCatalogReader接口
  /**
   * Convention via which results should be returned by execution.
   * 结果应该通过执行返回的约定(Convention)
   * Convention定义了关系表达式的物理实现方式,例如ENUMERABLE约定表示使用可枚举的方式执行
   */
  protected final Convention resultConvention; // 结果约定,指定执行结果应该通过哪种约定返回,影响物理计划的生成和执行方式
  protected @Nullable CalciteTimingTracer timingTracer; // 时间跟踪器,用于跟踪和记录各个阶段的执行时间,可为null表示不跟踪时间
  protected @MonotonicNonNull List<@Nullable List<String>> fieldOrigins; // 字段来源列表,记录每个结果字段的来源信息(数据库、schema、表、列),可为null表示未初始化
  protected @MonotonicNonNull RelDataType parameterRowType; // 参数行类型,描述SQL语句中参数的类型信息,可为null表示未初始化

  // temporary. for testing.
  public static final TryThreadLocal<Boolean> THREAD_TRIM =
      TryThreadLocal.of(false); // 线程本地变量,用于控制是否修剪未使用的字段,临时用于测试,默认为false表示不修剪

  /** Temporary, until
   * <a href="https://issues.apache.org/jira/browse/CALCITE-1045">[CALCITE-1045]
   * Decorrelate sub-queries in Project and Join</a> is fixed.
   * 临时变量,直到CALCITE-1045(在Project和Join中去相关子查询)问题被修复
   *
   * <p>The default is false, meaning do not expand queries during sql-to-rel,
   * but a few tests override and set it to true. After CALCITE-1045
   * is fixed, remove those overrides and use false everywhere. 
   * 默认为false,表示在SQL到Rel转换期间不展开查询,但一些测试会覆盖并设置为true
   * 当CALCITE-1045修复后,移除这些覆盖并在所有地方使用false */
  public static final TryThreadLocal<Boolean> THREAD_EXPAND =
      TryThreadLocal.of(false); // 线程本地变量,控制是否在SQL到Rel转换期间展开查询,临时用于测试

  // temporary. for testing.
  public static final TryThreadLocal<@Nullable Integer> THREAD_INSUBQUERY_THRESHOLD =
      TryThreadLocal.of(DEFAULT_IN_SUB_QUERY_THRESHOLD); // 线程本地变量,控制IN子查询的阈值,用于测试,默认值为DEFAULT_IN_SUB_QUERY_THRESHOLD

  protected Prepare(CalcitePrepare.Context context, CatalogReader catalogReader,
      Convention resultConvention) { // 构造方法,初始化Prepare对象,接收上下文、目录读取器和结果约定作为参数
    this.context = requireNonNull(context, "context"); // 设置上下文,使用requireNonNull确保context不为null,否则抛出NullPointerException
    this.catalogReader = catalogReader; // 设置目录读取器,用于读取元数据信息
    this.resultConvention = resultConvention; // 设置结果约定,指定执行结果应该通过哪种约定返回
  }

  protected abstract PreparedResult createPreparedExplanation( // 抽象方法,创建一个EXPLAIN语句的准备结果,由子类实现具体的创建逻辑
      @Nullable RelDataType resultType, // 结果类型,可为null表示不需要结果类型信息
      RelDataType parameterRowType, // 参数行类型,描述SQL语句中参数的类型信息
      @Nullable RelRoot root, // 关系表达式根节点,可为null表示不需要关系表达式
      SqlExplainFormat format, // EXPLAIN输出格式,例如XML、JSON、TEXT等
      SqlExplainLevel detailLevel); // EXPLAIN详细级别,控制输出信息的详细程度

  /**
   * Optimizes a query plan.
   *
   * @param root Root of relational expression tree
   * @param materializations Tables known to be populated with a given query
   * @param lattices Lattices
   * @return an equivalent optimized relational expression
   */
  protected RelRoot optimize(RelRoot root, // 优化方法,对查询计划进行优化,使用优化器、物化视图和Lattice来改进查询性能
      final List<Materialization> materializations, // 物化视图列表,包含已知的物化表信息,可用于查询重写和优化
      final List<CalciteSchema.LatticeEntry> lattices) { // Lattice列表,包含Lattice(星型模式)信息,可用于聚合查询优化
    final RelOptPlanner planner = root.rel.getCluster().getPlanner(); // 从关系表达式根节点获取优化器,用于执行优化过程

    final DataContext dataContext = context.getDataContext(); // 获取数据上下文,提供运行时环境信息
    planner.setExecutor(new RexExecutorImpl(dataContext)); // 为优化器设置Rex表达式执行器,用于在优化过程中执行常量表达式求值

    final List<RelOptMaterialization> materializationList = // 创建优化器可用的物化视图列表
        new ArrayList<>(materializations.size()); // 初始化列表,容量为物化视图的数量
    for (Materialization materialization : materializations) { // 遍历所有物化视图
      List<String> qualifiedTableName = materialization.materializedTable.path(); // 获取物化表的完整限定名称
      materializationList.add( // 将物化视图信息添加到优化器列表中
          new RelOptMaterialization( // 创建RelOptMaterialization对象
              castNonNull(materialization.tableRel), // 物化表的关系表达式,确保不为null
              castNonNull(materialization.queryRel), // 物化视图的查询关系表达式,确保不为null
              materialization.starRelOptTable, // 星型表的优化表对象
              qualifiedTableName)); // 物化表的限定名称
    }

    final List<RelOptLattice> latticeList = new ArrayList<>(lattices.size()); // 创建优化器可用的Lattice列表
    for (CalciteSchema.LatticeEntry lattice : lattices) { // 遍历所有Lattice
      final CalciteSchema.TableEntry starTable = lattice.getStarTable(); // 获取Lattice的星型表
      final JavaTypeFactory typeFactory = context.getTypeFactory(); // 获取类型工厂,用于创建类型信息
      final RelOptTableImpl starRelOptTable = // 创建星型表的优化表实现
          RelOptTableImpl.create(catalogReader, // 使用目录读取器
              starTable.getTable().getRowType(typeFactory), starTable, null); // 获取行类型并创建表
      latticeList.add( // 将Lattice添加到列表中
          new RelOptLattice(lattice.getLattice(), starRelOptTable)); // 创建RelOptLattice对象
    }

    final RelTraitSet desiredTraits = getDesiredRootTraitSet(root); // 获取期望的根节点特征集,包括约定和排序等

    final Program program = getProgram(); // 获取优化程序(Program),定义了优化的步骤和规则
    final RelNode rootRel4 = // 运行优化程序,对关系表达式进行优化
        program.run(planner, root.rel, desiredTraits, materializationList, // 传入优化器、根节点、期望特征、物化视图和Lattice
            latticeList); // 优化后的关系表达式
    if (LOGGER.isDebugEnabled()) { // 如果日志级别为DEBUG
      LOGGER.debug("Plan after physical tweaks:\n{}", // 记录优化后的计划
          RelOptUtil.toString(rootRel4, SqlExplainLevel.ALL_ATTRIBUTES)); // 以所有属性级别输出计划
    }

    return root.withRel(rootRel4); // 返回新的RelRoot,使用优化后的关系表达式
  }

  protected Program getProgram() { // 获取优化程序(Program),定义了优化的步骤和规则
    // Allow a test to override the default program.
    // 允许测试覆盖默认的优化程序
    final Holder<@Nullable Program> holder = Holder.empty(); // 创建空的Holder对象,用于存储可能被Hook覆盖的程序
    Hook.PROGRAM.run(holder); // 运行PROGRAM Hook,允许测试或其他代码自定义优化程序
    @Nullable Program holderValue = holder.get(); // 获取Holder中的程序值
    if (holderValue != null) { // 如果Holder中有自定义程序
      return holderValue; // 返回自定义程序
    }

    return Programs.standard(); // 返回标准优化程序,包含默认的优化规则和步骤
  }

  protected RelTraitSet getDesiredRootTraitSet(RelRoot root) { // 获取期望的根节点特征集,包括约定和排序等
    // Make sure non-CallingConvention traits, if any, are preserved
    // 确保非CallingConvention特征(如果有的话)被保留
    return root.rel.getTraitSet() // 获取根关系表达式的特征集
        .replace(resultConvention) // 替换为结果约定
        .replace(root.collation) // 替换为排序特征
        .simplify(); // 简化特征集,移除冗余特征
  }

  /**
   * Implements a physical query plan.
   * 实现物理查询计划,将优化后的逻辑计划转换为可执行的物理计划
   *
   * @param root Root of the relational expression tree
   * 关系表达式树的根节点,包含优化后的查询计划
   * @return an executable plan
   * 可执行的计划对象,包含执行查询所需的所有信息
   */
  protected abstract PreparedResult implement(RelRoot root); // 抽象方法,实现物理查询计划,由子类实现具体的生成逻辑

  public PreparedResult prepareSql( // 准备SQL语句的入口方法,将SQL语句转换为可执行的计划
      SqlNode sqlQuery, // SQL查询的抽象语法树(AST)节点
      Class runtimeContextClass, // 运行时上下文类,用于执行时提供上下文信息
      SqlValidator validator, // SQL验证器,用于验证SQL语句的语义正确性
      boolean needsValidation) { // 是否需要验证的标志
    return prepareSql( // 调用重载的prepareSql方法
        sqlQuery, // 传入SQL查询节点
        sqlQuery, // 传入原始SQL查询节点
        runtimeContextClass, // 传入运行时上下文类
        validator, // 传入SQL验证器
        needsValidation); // 传入是否需要验证的标志
  }

  public PreparedResult prepareSql( // 准备SQL语句的核心方法,执行完整的准备流程
      SqlNode sqlQuery, // SQL查询的抽象语法树(AST)节点
      SqlNode sqlNodeOriginal, // 原始SQL查询节点,用于保留原始信息
      Class<?> runtimeContextClass, // 运行时上下文类,用于执行时提供上下文信息
      SqlValidator validator, // SQL验证器,用于验证SQL语句的语义正确性
      boolean needsValidation) { // 是否需要验证的标志
    init(runtimeContextClass); // 初始化准备过程,设置运行时上下文

    final SqlToRelConverter.Config config = // 创建SQL到Rel转换器的配置
        SqlToRelConverter.config() // 获取默认配置
            .withTrimUnusedFields(true) // 启用修剪未使用字段的功能
            .withExpand(THREAD_EXPAND.get()) // 设置是否展开查询,从线程本地变量获取
            .withInSubQueryThreshold(castNonNull(THREAD_INSUBQUERY_THRESHOLD.get())) // 设置IN子查询阈值
            .withExplain(sqlQuery.getKind() == SqlKind.EXPLAIN); // 设置是否为EXPLAIN语句
    final Holder<SqlToRelConverter.Config> configHolder = Holder.of(config); // 将配置放入Holder中,允许Hook修改
    Hook.SQL2REL_CONVERTER_CONFIG_BUILDER.run(configHolder); // 运行SQL2REL_CONVERTER_CONFIG_BUILDER Hook,允许自定义配置
    final SqlToRelConverter sqlToRelConverter = // 获取SQL到Rel转换器实例
        getSqlToRelConverter(validator, catalogReader, configHolder.get()); // 使用验证器、目录读取器和配置创建转换器

    SqlExplain sqlExplain = null; // 初始化EXPLAIN语句对象为null
    if (sqlQuery.getKind() == SqlKind.EXPLAIN) { // 如果SQL查询是EXPLAIN语句
      // dig out the underlying SQL statement
      // 挖掘出底层的SQL语句
      sqlExplain = (SqlExplain) sqlQuery; // 将SQL查询转换为EXPLAIN语句对象
      sqlQuery = sqlExplain.getExplicandum(); // 获取EXPLAIN语句中要解释的实际SQL语句
      sqlToRelConverter.setDynamicParamCountInExplain( // 设置EXPLAIN中的动态参数计数
          sqlExplain.getDynamicParamCount()); // 从EXPLAIN语句获取动态参数数量
    }

    RelRoot root = // 将SQL查询转换为关系表达式根节点
        sqlToRelConverter.convertQuery(sqlQuery, needsValidation, true); // 执行转换,传入查询、是否需要验证和是否需要验证标识符
    if (this.context.config().conformance().checkedArithmetic()) { // 如果配置要求检查算术运算
      ConvertToChecked checkedConv = new ConvertToChecked(root.rel.getCluster().getRexBuilder()); // 创建检查转换器,用于检查算术运算
      RelNode rel = checkedConv.visit(root.rel); // 访问关系表达式,进行算术运算检查
      root = root.withRel(rel); // 使用检查后的关系表达式创建新的根节点
    }
    Hook.CONVERTED.run(root.rel); // 运行CONVERTED Hook,通知SQL到Rel转换已完成

    if (timingTracer != null) { // 如果时间跟踪器不为null
      timingTracer.traceTime("end sql2rel"); // 记录SQL到Rel转换结束的时间
    }

    final RelDataType resultType = validator.getValidatedNodeType(sqlQuery); // 从验证器获取验证后的节点类型
    fieldOrigins = validator.getFieldOrigins(sqlQuery); // 从验证器获取字段来源信息
    assert fieldOrigins.size() == resultType.getFieldCount(); // 断言字段来源数量与结果类型字段数量一致

    parameterRowType = validator.getParameterRowType(sqlQuery); // 从验证器获取参数行类型

    // Display logical plans before view expansion, plugging in physical
    // storage and decorrelation
    // 在视图展开、插入物理存储和去相关之前显示逻辑计划
    if (sqlExplain != null) { // 如果是EXPLAIN语句
      SqlExplain.Depth explainDepth = sqlExplain.getDepth(); // 获取EXPLAIN深度(TYPE/LOGICAL/PHYSICAL)
      SqlExplainFormat format = sqlExplain.getFormat(); // 获取EXPLAIN输出格式
      SqlExplainLevel detailLevel = sqlExplain.getDetailLevel(); // 获取EXPLAIN详细级别
      switch (explainDepth) { // 根据EXPLAIN深度进行不同处理
      case TYPE: // 如果是TYPE级别,只显示类型信息
        return createPreparedExplanation(resultType, parameterRowType, null, // 创建EXPLAIN准备结果,不包含关系表达式
            format, detailLevel); // 传入格式和详细级别
      case LOGICAL: // 如果是LOGICAL级别,显示逻辑计划
        return createPreparedExplanation(null, parameterRowType, root, format, // 创建EXPLAIN准备结果,包含关系表达式
            detailLevel); // 传入格式和详细级别
      default: // 其他情况继续处理
      }
    }

    // Structured type flattening, view expansion, and plugging in physical
    // storage.
    // 结构化类型扁平化、视图展开和插入物理存储
    root = root.withRel(flattenTypes(root.rel, true)); // 扁平化类型,将嵌套结构转换为平面结构

    if (this.context.config().forceDecorrelate()) { // 如果配置强制去相关
      // Sub-query decorrelation.
      // 子查询去相关,将子查询转换为连接
      root = root.withRel(decorrelate(sqlToRelConverter, sqlQuery, root.rel)); // 执行去相关操作
    }

    if (configHolder.get().isTrimUnusedFields()) { // 如果配置要求修剪未使用的字段
      // Trim unused fields.
      // 修剪未使用的字段,移除查询中不需要的字段以提高性能
      root = trimUnusedFields(root); // 执行字段修剪操作

      Hook.TRIMMED.run(root.rel); // 运行TRIMMED Hook,通知字段修剪已完成
    }

    // Display physical plan after decorrelation.
    // 在去相关之后显示物理计划
    if (sqlExplain != null) { // 如果是EXPLAIN语句
      switch (sqlExplain.getDepth()) { // 根据EXPLAIN深度进行不同处理
      case PHYSICAL: // 如果是PHYSICAL级别,显示物理计划
      default: // 默认情况也显示物理计划
        root = optimize(root, getMaterializations(), getLattices()); // 优化查询计划
        return createPreparedExplanation(null, parameterRowType, root, // 创建EXPLAIN准备结果,包含优化后的关系表达式
            sqlExplain.getFormat(), sqlExplain.getDetailLevel()); // 传入格式和详细级别
      }
    }

    root = optimize(root, getMaterializations(), getLattices()); // 优化查询计划,使用物化视图和Lattice

    if (timingTracer != null) { // 如果时间跟踪器不为null
      timingTracer.traceTime("end optimization"); // 记录优化结束的时间
    }

    // For transformation from DML -> DML, use result of rewrite
    // (e.g. UPDATE -> MERGE).  For anything else (e.g. CALL -> SELECT),
    // use original kind.
    // 对于DML到DML的转换,使用重写的结果(例如UPDATE -> MERGE)
    // 对于其他情况(例如CALL -> SELECT),使用原始类型
    if (!root.kind.belongsTo(SqlKind.DML)) { // 如果根节点类型不属于DML
      root = root.withKind(sqlNodeOriginal.getKind()); // 使用原始SQL节点的类型
    }
    return implement(root); // 实现物理查询计划并返回准备结果
  }

  protected TableModify.@Nullable Operation mapTableModOp( // 映射表修改操作类型,将SQL的DML类型映射为TableModify操作类型
      boolean isDml, // 是否为DML语句的标志
      SqlKind sqlKind) { // SQL语句的类型
    if (!isDml) { // 如果不是DML语句
      return null; // 返回null表示不是表修改操作
    }
    switch (sqlKind) { // 根据SQL类型进行映射
    case INSERT: // 如果是INSERT语句
      return TableModify.Operation.INSERT; // 返回INSERT操作
    case DELETE: // 如果是DELETE语句
      return TableModify.Operation.DELETE; // 返回DELETE操作
    case MERGE: // 如果是MERGE语句
      return TableModify.Operation.MERGE; // 返回MERGE操作
    case UPDATE: // 如果是UPDATE语句
      return TableModify.Operation.UPDATE; // 返回UPDATE操作
    default: // 其他类型
      return null; // 返回null表示不是表修改操作
    }
  }

  /**
   * Protected method to allow subclasses to override construction of
   * SqlToRelConverter.
   * 受保护的方法,允许子类覆盖SqlToRelConverter的构造过程
   */
  protected abstract SqlToRelConverter getSqlToRelConverter( // 抽象方法,获取SQL到Rel转换器,由子类实现具体的创建逻辑
      SqlValidator validator, // SQL验证器,用于验证SQL语句
      CatalogReader catalogReader, // 目录读取器,用于读取元数据
      SqlToRelConverter.Config config); // 转换器配置,包含转换行为的各种选项

  public abstract RelNode flattenTypes( // 抽象方法,扁平化类型,将嵌套结构转换为平面结构
      RelNode rootRel, // 根关系表达式
      boolean restructure); // 是否重新结构的标志

  protected abstract RelNode decorrelate(SqlToRelConverter sqlToRelConverter, // 抽象方法,去相关,将子查询转换为连接
      SqlNode query, // SQL查询节点
      RelNode rootRel); // 根关系表达式

  protected abstract List<Materialization> getMaterializations(); // 抽象方法,获取物化视图列表,由子类实现

  protected abstract List<LatticeEntry> getLattices(); // 抽象方法,获取Lattice列表,由子类实现

  /**
   * Walks over a tree of relational expressions, replacing each
   * {@link org.apache.calcite.rel.RelNode} with a 'slimmed down' relational
   * expression that projects
   * only the columns required by its consumer.
   * 遍历关系表达式树,将每个RelNode替换为"精简"的关系表达式,只投影其消费者所需的列
   * 这个过程可以显著减少数据传输和处理开销,提高查询性能
   *
   * @param root Root of relational expression tree
   * 关系表达式树的根节点
   * @return Trimmed relational expression
   * 修剪后的关系表达式
   */
  protected RelRoot trimUnusedFields(RelRoot root) { // 修剪未使用字段的方法,移除查询中不需要的字段
    final SqlToRelConverter.Config config = SqlToRelConverter.config() // 创建转换器配置
        .withTrimUnusedFields(shouldTrim(root.rel)) // 设置是否修剪未使用字段,根据shouldTrim方法判断
        .withExpand(THREAD_EXPAND.get()) // 设置是否展开查询
        .withInSubQueryThreshold(castNonNull(THREAD_INSUBQUERY_THRESHOLD.get())); // 设置IN子查询阈值
    final SqlToRelConverter converter = // 获取SQL到Rel转换器
        getSqlToRelConverter(getSqlValidator(), catalogReader, config); // 使用验证器、目录读取器和配置创建转换器
    final boolean ordered = !root.collation.getFieldCollations().isEmpty(); // 判断是否有序(有排序要求)
    final boolean dml = SqlKind.DML.contains(root.kind); // 判断是否为DML语句
    return root.withRel(converter.trimUnusedFields(dml || ordered, root.rel)); // 执行字段修剪并返回新的RelRoot
  }

  private static boolean shouldTrim(RelNode rootRel) { // 私有静态方法,判断是否应该修剪未使用字段
    // For now, don't trim if there are more than 3 joins. The projects
    // near the leaves created by trim migrate past joins and seem to
    // prevent join-reordering.
    // 目前,如果有超过3个连接则不修剪。由trim创建的靠近叶子的project会迁移过连接,似乎会阻止连接重排序
    return THREAD_TRIM.get() || RelOptUtil.countJoins(rootRel) < 2; // 如果线程本地变量设置了修剪,或者连接数小于2则修剪
  }

  protected abstract void init(Class runtimeContextClass); // 抽象方法,初始化准备过程,由子类实现

  protected abstract SqlValidator getSqlValidator(); // 抽象方法,获取SQL验证器,由子类实现

  /** Interface by which validator and planner can read table metadata.
 * 目录读取器接口,验证器和规划器通过此接口读取表元数据
 * 该接口扩展了RelOptSchema、SqlValidatorCatalogReader和SqlOperatorTable
 * 提供了读取表、字段、操作符等元数据的统一接口 */
  public interface CatalogReader // 目录读取器接口
      extends RelOptSchema, SqlValidatorCatalogReader, SqlOperatorTable { // 扩展多个接口以提供完整的元数据访问功能
    @Override @Nullable PreparingTable getTableForMember(List<String> names); // 根据成员名称获取准备表,可为null

    /** Returns a catalog reader the same as this one but with a possibly
     * different schema path.
     * 返回与此目录读取器相同但具有不同schema路径的目录读取器 */
    CatalogReader withSchemaPath(List<String> schemaPath); // 根据schema路径创建新的目录读取器

    @Override @Nullable PreparingTable getTable(List<String> names); // 根据名称列表获取准备表,可为null

    ThreadLocal<@Nullable CatalogReader> THREAD_LOCAL = new ThreadLocal<>(); // 线程本地变量,存储当前线程的目录读取器
  }

  /** Definition of a table, for the purposes of the validator and planner.
 * 表定义,用于验证器和规划器的目的
 * 该接口扩展了RelOptTable和SqlValidatorTable,提供了表在验证和规划期间所需的接口 */
  public interface PreparingTable // 准备表接口
      extends RelOptTable, SqlValidatorTable { // 扩展关系优化表和SQL验证表接口
  }

  /** Abstract implementation of {@link PreparingTable} with an implementation
   * for {@link #columnHasDefaultValue}.
   * PreparingTable的抽象实现,提供了columnHasDefaultValue的实现
   * 为子类提供了通用的表功能实现 */
  public abstract static class AbstractPreparingTable // 抽象准备表类
      implements PreparingTable { // 实现PreparingTable接口
    @SuppressWarnings("deprecation") // 抑制已弃用方法的警告
    @Override public boolean columnHasDefaultValue(RelDataType rowType, int ordinal, // 判断列是否有默认值
        InitializerContext initializerContext) { // 初始化器上下文
      // This method is no longer used
      // 此方法不再使用
      final Table table = this.unwrap(Table.class); // 解包获取Table对象
      if (table instanceof Wrapper) { // 如果Table是Wrapper的实例
        final InitializerExpressionFactory initializerExpressionFactory = // 获取初始化表达式工厂
            ((Wrapper) table).unwrap(InitializerExpressionFactory.class); // 从Wrapper中解包
        if (initializerExpressionFactory != null) { // 如果工厂不为null
          return initializerExpressionFactory // 创建列的默认值
              .newColumnDefaultValue(this, ordinal, initializerContext) // 生成默认值表达式
              .getType().getSqlTypeName() != SqlTypeName.NULL; // 检查类型是否为NULL
        }
      }
      if (ordinal >= rowType.getFieldList().size()) { // 如果序号超出字段列表范围
        return true; // 返回true表示有默认值
      }
      return !rowType.getFieldList().get(ordinal).getType().isNullable(); // 返回字段是否不可为空
    }

    @Override public final RelOptTable extend(List<RelDataTypeField> extendedFields) { // 扩展表,添加新的字段
      final Table table = unwrap(Table.class); // 解包获取Table对象

      // Get the set of extended columns that do not have the same name as a column
      // in the base table.
      // 获取扩展列的集合,这些列不与基表中的列同名
      final List<RelDataTypeField> baseColumns = getRowType().getFieldList(); // 获取基表的字段列表
      final List<RelDataTypeField> dedupedFields = // 去重字段,移除与基表同名的字段
          RelOptUtil.deduplicateColumns(baseColumns, extendedFields); // 执行去重操作
      final List<RelDataTypeField> dedupedExtendedFields = // 获取去重后的扩展字段
          dedupedFields.subList(baseColumns.size(), dedupedFields.size()); // 从去重字段列表中提取扩展部分

      if (table instanceof ExtensibleTable) { // 如果Table是可扩展表的实例
        final Table extendedTable = // 扩展表
                ((ExtensibleTable) table).extend(dedupedExtendedFields); // 使用扩展字段创建新表
        return extend(extendedTable); // 调用子类的extend方法创建RelOptTable
      } else if (table instanceof ModifiableViewTable) { // 如果Table是可修改视图表的实例
        final ModifiableViewTable modifiableViewTable = // 转换为可修改视图表
                (ModifiableViewTable) table;
        final ModifiableViewTable extendedView = // 扩展视图
            modifiableViewTable.extend(dedupedExtendedFields, // 传入扩展字段
                requireNonNull( // 确保relOptSchema不为null
                    getRelOptSchema(), // 获取关系优化schema
                    () -> "relOptSchema for table " + getQualifiedName()).getTypeFactory()); // 获取类型工厂
        return extend(extendedView); // 调用子类的extend方法创建RelOptTable
      }
      throw new RuntimeException("Cannot extend " + table); // 抛出异常,表示无法扩展该表
    }

    /** Implementation-specific code to instantiate a new {@link RelOptTable}
     * based on a {@link Table} that has been extended.
     * 实现特定的代码,用于基于已扩展的Table实例化新的RelOptTable */
    protected abstract RelOptTable extend(Table extendedTable); // 抽象方法,由子类实现具体的扩展逻辑

    @Override public List<ColumnStrategy> getColumnStrategies() { // 获取列策略列表
      return RelOptTableImpl.columnStrategies(AbstractPreparingTable.this); // 使用RelOptTableImpl的静态方法获取列策略
    }
  }

  /**
   * PreparedExplanation is a PreparedResult for an EXPLAIN PLAN statement.
   * It's always good to have an explanation prepared.
   * PreparedExplanation是EXPLAIN PLAN语句的PreparedResult
   * 准备好解释总是好的,这个类专门用于处理EXPLAIN语句的结果
   */
  public abstract static class PreparedExplain // 抽象类,用于EXPLAIN语句的准备结果
      implements PreparedResult { // 实现PreparedResult接口
    private final @Nullable RelDataType rowType; // 结果行类型,可为null
    private final RelDataType parameterRowType; // 参数行类型
    private final @Nullable RelRoot root; // 关系表达式根节点,可为null
    private final SqlExplainFormat format; // EXPLAIN输出格式
    private final SqlExplainLevel detailLevel; // EXPLAIN详细级别

    protected PreparedExplain( // 构造方法,初始化PreparedExplain对象
        @Nullable RelDataType rowType, // 结果行类型
        RelDataType parameterRowType, // 参数行类型
        @Nullable RelRoot root, // 关系表达式根节点
        SqlExplainFormat format, // EXPLAIN输出格式
        SqlExplainLevel detailLevel) { // EXPLAIN详细级别
      this.rowType = rowType; // 设置结果行类型
      this.parameterRowType = parameterRowType; // 设置参数行类型
      this.root = root; // 设置关系表达式根节点
      this.format = format; // 设置EXPLAIN输出格式
      this.detailLevel = detailLevel; // 设置EXPLAIN详细级别
    }

    @Override public String getCode() { // 获取EXPLAIN的代码字符串
      if (root == null) { // 如果关系表达式根节点为null
        return rowType == null ? "rowType is null" : RelOptUtil.dumpType(rowType); // 返回类型信息或错误消息
      } else { // 如果关系表达式根节点不为null
        return RelOptUtil.dumpPlan("", root.rel, format, detailLevel); // 转储计划信息
      }
    }

    @Override public RelDataType getParameterRowType() { // 获取参数行类型
      return parameterRowType; // 返回参数行类型
    }

    @Override public boolean isDml() { // 判断是否为DML语句
      return false; // EXPLAIN语句不是DML,返回false
    }

    @Override public TableModify.@Nullable Operation getTableModOp() { // 获取表修改操作
      return null; // EXPLAIN语句不涉及表修改,返回null
    }

    @Override public List<@Nullable List<String>> getFieldOrigins() { // 获取字段来源列表
      return Collections.singletonList( // 返回包含单个元素的列表
          Collections.nCopies(4, null)); // 该元素是包含4个null的列表,表示EXPLAIN语句没有字段来源
    }
  }

  /**
   * Result of a call to {@link Prepare#prepareSql}.
   * Prepare.prepareSql调用的结果
   * 这个接口定义了SQL准备完成后返回的结果,包含执行所需的所有信息
   */
  public interface PreparedResult { // 准备结果接口
    /**
     * Returns the code generated by preparation.
     * 返回准备过程中生成的代码
     * 这个代码可以是Java代码、Linq4j表达式或其他可执行的形式
     */
    String getCode(); // 获取生成的代码字符串

    /**
     * Returns whether this result is for a DML statement, in which case the
     * result set is one row with one column containing the number of rows
     * affected.
     * 返回此结果是否为DML语句,如果是,结果集为包含受影响行数的单行单列
     */
    boolean isDml(); // 判断是否为DML语句

    /**
     * Returns the table modification operation corresponding to this
     * statement if it is a table modification statement; otherwise null.
     * 如果此语句是表修改语句,则返回对应的表修改操作;否则返回null
     */
    TableModify.@Nullable Operation getTableModOp(); // 获取表修改操作类型

    /**
     * Returns a list describing, for each result field, the origin of the
     * field as a 4-element list of (database, schema, table, column).
     * 返回一个列表,描述每个结果字段的来源,每个来源是一个4元素列表(数据库、schema、表、列)
     */
    List<? extends @Nullable List<String>> getFieldOrigins(); // 获取字段来源列表

    /**
     * Returns a record type whose fields are the parameters of this statement.
     * 返回一个记录类型,其字段是此语句的参数
     */
    RelDataType getParameterRowType(); // 获取参数行类型

    /**
     * Executes the prepared result.
     * 执行准备好的结果
     *
     * @param cursorFactory How to map values into a cursor
     * 如何将值映射到游标中
     * @return producer of rows resulting from execution
     * 执行产生的行的生产者
     */
    Bindable getBindable(Meta.CursorFactory cursorFactory); // 获取可绑定的执行对象
  }

  /**
   * Abstract implementation of {@link PreparedResult}.
   * PreparedResult的抽象实现
   * 提供了PreparedResult接口的通用实现,子类只需实现特定的生成逻辑
   */
  public abstract static class PreparedResultImpl // 抽象类,实现PreparedResult和Typed接口
      implements PreparedResult, Typed { // 实现准备结果和类型化接口
    protected final RelNode rootRel; // 根关系表达式,包含完整的物理查询计划
    protected final RelDataType parameterRowType; // 参数行类型,描述SQL语句中参数的类型信息
    protected final RelDataType rowType; // 结果行类型,描述查询结果的类型信息
    protected final boolean isDml; // 是否为DML语句的标志
    protected final TableModify.@Nullable Operation tableModOp; // 表修改操作类型,可为null
    protected final List<? extends @Nullable List<String>> fieldOrigins; // 字段来源列表
    protected final List<RelCollation> collations; // 排序规则列表

    protected PreparedResultImpl( // 构造方法,初始化PreparedResultImpl对象
        RelDataType rowType, // 结果行类型
        RelDataType parameterRowType, // 参数行类型
        List<? extends @Nullable List<String>> fieldOrigins, // 字段来源列表
        List<RelCollation> collations, // 排序规则列表
        RelNode rootRel, // 根关系表达式
        TableModify.@Nullable Operation tableModOp, // 表修改操作类型
        boolean isDml) { // 是否为DML语句
      this.rowType = requireNonNull(rowType, "rowType"); // 设置结果行类型,确保不为null
      this.parameterRowType = requireNonNull(parameterRowType, "parameterRowType"); // 设置参数行类型,确保不为null
      this.fieldOrigins = requireNonNull(fieldOrigins, "fieldOrigins"); // 设置字段来源列表,确保不为null
      this.collations = ImmutableList.copyOf(collations); // 设置排序规则列表,创建不可变副本
      this.rootRel = requireNonNull(rootRel, "rootRel"); // 设置根关系表达式,确保不为null
      this.tableModOp = tableModOp; // 设置表修改操作类型
      this.isDml = isDml; // 设置是否为DML语句
    }

    @Override public boolean isDml() { // 判断是否为DML语句
      return isDml; // 返回isDml标志
    }

    @Override public TableModify.@Nullable Operation getTableModOp() { // 获取表修改操作类型
      return tableModOp; // 返回表修改操作类型
    }

    @Override public List<? extends @Nullable List<String>> getFieldOrigins() { // 获取字段来源列表
      return fieldOrigins; // 返回字段来源列表
    }

    @Override public RelDataType getParameterRowType() { // 获取参数行类型
      return parameterRowType; // 返回参数行类型
    }

    /**
     * Returns the physical row type of this prepared statement. May not be
     * identical to the row type returned by the validator; for example, the
     * field names may have been made unique.
     * 返回此准备语句的物理行类型。可能与验证器返回的行类型不同;
     * 例如,字段名可能已被唯一化
     */
    public RelDataType getPhysicalRowType() { // 获取物理行类型
      return rowType; // 返回结果行类型
    }

    @Override public abstract Type getElementType(); // 抽象方法,获取元素类型,由子类实现

    public RelNode getRootRel() { // 获取根关系表达式
      return rootRel; // 返回根关系表达式
    }
  }

  /** Describes that a given SQL query is materialized by a given table.
   * The materialization is currently valid, and can be used in the planning
   * process.
   * 描述给定的SQL查询由给定表物化
   * 物化当前有效,可以在规划过程中使用
   * 物化视图是预先计算并存储的查询结果,可以显著提高查询性能 */
  public static class Materialization { // 物化视图类,描述物化视图的信息
    /** The table that holds the materialized data.
     * 保存物化数据的表 */
    final CalciteSchema.TableEntry materializedTable; // 物化表条目,包含表的元数据和表对象
    /** The query that derives the data.
     * 派生数据的查询 */
    final String sql; // 物化视图的SQL查询语句
    /** The schema path for the query.
     * 查询的schema路径 */
    final List<String> viewSchemaPath; // 视图schema路径,用于解析查询中的表名
    /** Relational expression for the table. Usually a
     * {@link org.apache.calcite.rel.logical.LogicalTableScan}.
     * 表的关系表达式。通常是LogicalTableScan */
    @Nullable RelNode tableRel; // 物化表的关系表达式,可为null表示未初始化
    /** Relational expression for the query to populate the table.
     * 用于填充表的查询的关系表达式 */
    @Nullable RelNode queryRel; // 查询的关系表达式,用于生成物化数据,可为null表示未初始化
    /** Star table identified.
     * 识别的星型表 */
    private @Nullable RelOptTable starRelOptTable; // 星型表的优化表对象,可为null表示未初始化

    public Materialization(CalciteSchema.TableEntry materializedTable, // 构造方法,创建Materialization对象
        String sql, // 物化视图的SQL查询语句
        List<String> viewSchemaPath) { // 视图schema路径
      this.materializedTable = // 设置物化表条目
          requireNonNull(materializedTable, "materializedTable"); // 确保物化表条目不为null
      this.sql = requireNonNull(sql, "sql"); // 设置SQL查询语句,确保不为null
      this.viewSchemaPath = viewSchemaPath; // 设置视图schema路径
    }

    public void materialize(RelNode queryRel, // 物化方法,设置查询关系表达式和星型表
        RelOptTable starRelOptTable) { // 星型表的优化表对象
      this.queryRel = queryRel; // 设置查询关系表达式
      this.starRelOptTable = starRelOptTable; // 设置星型表的优化表对象
      assert starRelOptTable.maybeUnwrap(StarTable.class).isPresent(); // 断言星型表可以解包为StarTable
    }
  }
}
