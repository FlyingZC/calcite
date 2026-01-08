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

import org.apache.calcite.DataContexts;
import org.apache.calcite.adapter.enumerable.EnumerableCalc;
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableInterpretable;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.avatica.AvaticaParameter;
import org.apache.calcite.avatica.ColumnMetaData;
import org.apache.calcite.avatica.Meta;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.config.CalciteSystemProperty;
import org.apache.calcite.interpreter.BindableConvention;
import org.apache.calcite.interpreter.Interpreters;
import org.apache.calcite.jdbc.CalcitePrepare;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.jdbc.CalciteSchema.LatticeEntry;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.Ord;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.BinaryExpression;
import org.apache.calcite.linq4j.tree.BlockStatement;
import org.apache.calcite.linq4j.tree.Blocks;
import org.apache.calcite.linq4j.tree.ConstantExpression;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.MemberExpression;
import org.apache.calcite.linq4j.tree.MethodCallExpression;
import org.apache.calcite.linq4j.tree.NewExpression;
import org.apache.calcite.linq4j.tree.ParameterExpression;
import org.apache.calcite.linq4j.tree.PseudoField;
import org.apache.calcite.materialize.MaterializationService;
import org.apache.calcite.plan.Contexts;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCostFactory;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollationTraitDef;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.core.Filter;
import org.apache.calcite.rel.core.Project;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexExecutorImpl;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexProgram;
import org.apache.calcite.runtime.Bindable;
import org.apache.calcite.runtime.Hook;
import org.apache.calcite.runtime.Typed;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.Schemas;
import org.apache.calcite.schema.Table;
import org.apache.calcite.server.CalciteServerStatement;
import org.apache.calcite.server.DdlExecutor;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlExplainFormat;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperatorTable;
import org.apache.calcite.sql.SqlUtil;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserImplFactory;
import org.apache.calcite.sql.parser.impl.SqlParserImpl;
import org.apache.calcite.sql.type.ExtraSqlTypes;
import org.apache.calcite.sql.type.MeasureSqlType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlOperatorTables;
import org.apache.calcite.sql.validate.SqlConformance;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql2rel.SqlRexConvertletTable;
import org.apache.calcite.sql2rel.SqlToRelConverter;
import org.apache.calcite.sql2rel.StandardConvertletTable;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.Pair;
import org.apache.calcite.util.Util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.apache.calcite.linq4j.Nullness.castNonNull;
import static org.apache.calcite.util.Static.RESOURCE;

import static java.util.Objects.requireNonNull;

/**
 * Shit just got real.
 *
 * <p>This class is public so that projects that create their own JDBC driver
 * and server can fine-tune preferences. However, this class and its methods are
 * subject to change without notice.
 */
// CalcitePrepareImpl类：Calcite SQL准备器的核心实现类，负责将SQL语句解析、验证、转换、优化并最终生成可执行的代码
// 实现了CalcitePrepare接口，是Calcite框架中SQL执行流程的核心组件
// 主要功能包括：SQL解析、SQL验证、关系代数转换、查询优化、代码生成等
// 该类是公开的，允许创建自定义JDBC驱动和服务器项目进行精细调整，但其方法可能会在没有通知的情况下发生变化
public class CalcitePrepareImpl implements CalcitePrepare {

  @Deprecated // to be removed before 2.0
  // 是否启用可枚举约定，已废弃，将在2.0版本前移除
  public static final boolean ENABLE_ENUMERABLE =
      CalciteSystemProperty.ENABLE_ENUMERABLE.value();

  @Deprecated // to be removed before 2.0
  // 是否启用流处理，已废弃，将在2.0版本前移除
  public static final boolean ENABLE_STREAM =
      CalciteSystemProperty.ENABLE_STREAM.value();

  @Deprecated // to be removed before 2.0
  // 可枚举规则列表，已废弃，将在2.0版本前移除
  public static final List<RelOptRule> ENUMERABLE_RULES =
      EnumerableRules.ENUMERABLE_RULES;

  /** Whether the bindable convention should be the root convention of any
   * plan. If not, enumerable convention is the default. */
  // 是否启用可绑定约定作为任何计划的根约定，如果未启用，则默认使用可枚举约定
  // BindableConvention允许使用解释器执行，而EnumerableConvention需要生成Java代码
  public final boolean enableBindable = Hook.ENABLE_BINDABLE.get(false);

  // 简单SQL语句集合，用于快速处理这些不需要完整优化流程的简单查询
  // 这些查询可以通过简化的准备流程直接返回结果，提高性能
  private static final Set<String> SIMPLE_SQLS =
      ImmutableSet.of(
          "SELECT 1",
          "select 1",
          "SELECT 1 FROM DUAL",
          "select 1 from dual",
          "values 1",
          "VALUES 1");

  // 无参构造方法，创建CalcitePrepareImpl实例
// 该构造方法不需要任何参数，所有配置通过系统属性或Hook机制动态获取
public CalcitePrepareImpl() {
  }

  // 解析SQL语句，返回解析结果
// @param context 准备上下文，包含类型工厂、schema路径、配置等信息
// @param sql 要解析的SQL语句字符串
// @return ParseResult 解析结果，包含验证器、SQL节点、验证后的节点类型等信息
// 该方法只进行SQL解析和验证，不进行关系代数转换
@Override public ParseResult parse(
      Context context, String sql) {
    return parse_(context, sql, false, false, false, false);
  }

  // 将SQL语句转换为关系代数表达式
// @param context 准备上下文，包含类型工厂、schema路径、配置等信息
// @param sql 要转换的SQL语句字符串
// @return ConvertResult 转换结果，包含验证器、SQL节点、验证后的节点类型、关系代数根节点等信息
// 该方法进行SQL解析、验证和关系代数转换，但不进行视图分析
@Override public ConvertResult convert(Context context, String sql) {
    return (ConvertResult) parse_(context, sql, true, false, false, false);
  }

  // 分析视图的可修改性，判断视图是否可以用于INSERT、UPDATE、DELETE操作
// @param context 准备上下文，包含类型工厂、schema路径、配置等信息
// @param sql 要分析的视图SQL语句字符串
// @param fail 如果为true，当视图不可修改时抛出异常；如果为false，返回分析结果但不抛出异常
// @return AnalyzeViewResult 视图分析结果，包含视图是否可修改、目标表、约束条件、列映射等信息
// 该方法进行SQL解析、验证、关系代数转换和视图分析
@Override public AnalyzeViewResult analyzeView(Context context, String sql,
      boolean fail) {
    return (AnalyzeViewResult) parse_(context, sql, true, true, fail, true);
  }

  /** Shared implementation for {@link #parse}, {@link #convert} and
   * {@link #analyzeView}. */
  // parse_方法：parse、convert和analyzeView方法的共享实现
  // @param context 准备上下文，包含类型工厂、schema路径、配置等信息
  // @param sql 要处理的SQL语句字符串
  // @param convert 是否进行关系代数转换
  // @param analyze 是否进行视图分析
  // @param fail 如果为true，当视图不可修改时抛出异常
  // @param embeddedQuery 是否为嵌入式查询
  // @return ParseResult 解析结果或其子类（ConvertResult或AnalyzeViewResult）
  // 该方法实现了SQL准备流程的前半部分：解析和验证
  private ParseResult parse_(Context context, String sql, boolean convert,
      boolean analyze, boolean fail, boolean embeddedQuery) {
    // 获取Java类型工厂，用于创建和操作Java类型
    final JavaTypeFactory typeFactory = context.getTypeFactory();
    // 创建目录读取器，用于读取schema中的表、函数等元数据信息
    CalciteCatalogReader catalogReader =
        new CalciteCatalogReader(
            context.getRootSchema(),
            context.getDefaultSchemaPath(),
            typeFactory,
            context.config());
    // 创建SQL解析器，用于将SQL字符串解析为SQL抽象语法树（AST）
    SqlParser parser = createParser(sql);
    // SQL节点，表示解析后的SQL抽象语法树
    SqlNode sqlNode;
    try {
      // 解析SQL语句，生成SQL节点（抽象语法树）
      sqlNode = parser.parseStmt();
    } catch (SqlParseException e) {
      // 如果解析失败，抛出运行时异常
      throw new RuntimeException("parse failed", e);
    }
    // 创建SQL验证器，用于验证SQL语句的语义正确性
    final SqlValidator validator =
        createSqlValidator(context, catalogReader,
            c -> c.withEmbeddedQuery(embeddedQuery));
    // 验证SQL节点，生成验证后的SQL节点（包含类型信息、表名解析等）
    SqlNode sqlNode1 = validator.validate(sqlNode);
    // 如果需要进行关系代数转换
    if (convert) {
      // 调用convert_方法进行关系代数转换和可能的视图分析
      return convert_(
          context, sql, analyze, fail, catalogReader, validator, sqlNode1);
    }
    // 如果不需要转换，直接返回解析结果
    return new ParseResult(this, validator, sql, sqlNode1,
        validator.getValidatedNodeType(sqlNode1));
  }

  // convert_方法：将验证后的SQL节点转换为关系代数表达式
// @param context 准备上下文
// @param sql SQL语句字符串
// @param analyze 是否进行视图分析
// @param fail 如果为true，当视图不可修改时抛出异常
// @param catalogReader 目录读取器
// @param validator SQL验证器
// @param sqlNode1 验证后的SQL节点
// @return ParseResult 转换结果或视图分析结果
// 该方法实现了SQL到关系代数的转换过程
private ParseResult convert_(Context context, String sql, boolean analyze,
      boolean fail, CalciteCatalogReader catalogReader, SqlValidator validator,
      SqlNode sqlNode1) {
    // 获取Java类型工厂
    final JavaTypeFactory typeFactory = context.getTypeFactory();
    // 确定结果约定：如果启用了可绑定约定，使用BindableConvention；否则使用EnumerableConvention
    // BindableConvention：使用解释器执行，不需要生成Java代码
    // EnumerableConvention：生成Java代码执行，性能更好
    final Convention resultConvention =
        enableBindable ? BindableConvention.INSTANCE
            : EnumerableConvention.INSTANCE;
    // Use the Volcano because it can handle the traits.
    // 创建Volcano优化器（基于代价的优化器），因为它可以处理trait（特性）
    final VolcanoPlanner planner = new VolcanoPlanner();
    // 向优化器添加约定trait定义，用于管理不同约定之间的转换
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE);

    // 配置SQL到关系代数转换器：启用修剪未使用字段的功能
    final SqlToRelConverter.Config config =
        SqlToRelConverter.config().withTrimUnusedFields(true);

    // 创建准备语句对象，用于管理SQL准备过程
    final CalcitePreparingStmt preparingStmt =
        new CalcitePreparingStmt(this, context, catalogReader, typeFactory,
            context.getRootSchema(), null,
            createCluster(planner, new RexBuilder(typeFactory)),
            resultConvention, createConvertletTable());
    // 获取SQL到关系代数转换器实例
    final SqlToRelConverter converter =
        preparingStmt.getSqlToRelConverter(validator, catalogReader, config);

    // 将验证后的SQL节点转换为关系代数根节点
    // false：不需要验证（已经验证过了）
    // true：需要顶层优化
    final RelRoot root = converter.convertQuery(sqlNode1, false, true);
    // 如果需要进行视图分析
    if (analyze) {
      // 调用analyze_方法分析视图的可修改性
      return analyze_(validator, sql, sqlNode1, root, fail);
    }
    // 如果不需要视图分析，返回转换结果
    return new ConvertResult(this, validator, sql, sqlNode1,
        validator.getValidatedNodeType(sqlNode1), root);
  }

  // analyze_方法：分析视图的可修改性，判断视图是否可以用于INSERT、UPDATE、DELETE操作
// @param validator SQL验证器
// @param sql SQL语句字符串
// @param sqlNode SQL节点
// @param root 关系代数根节点
// @param fail 如果为true，当视图不可修改时抛出异常
// @return AnalyzeViewResult 视图分析结果
// 该方法检查视图是否符合可修改视图的条件：
// 1. 基于单个表的查询
// 2. 只包含投影和过滤操作
// 3. 过滤条件只包含等值谓词
// 4. 所有非投影列都有常量值或可为NULL
private AnalyzeViewResult analyze_(SqlValidator validator, String sql,
      SqlNode sqlNode, RelRoot root, boolean fail) {
    // 获取RexBuilder，用于创建RexNode（关系表达式节点）
    final RexBuilder rexBuilder = root.rel.getCluster().getRexBuilder();
    // 获取关系节点
    RelNode rel = root.rel;
    // 保存视图关系节点
    final RelNode viewRel = rel;
    // 投影节点，用于处理视图中的列选择和表达式计算
    Project project;
    // 检查关系节点是否为投影节点
    if (rel instanceof Project) {
      // 如果是投影节点，保存引用并获取其输入
      project = (Project) rel;
      rel = project.getInput();
    } else {
      // 如果不是投影节点，设置为null
      project = null;
    }
    // 过滤节点，用于处理视图中的WHERE条件
    Filter filter;
    // 检查关系节点是否为过滤节点
    if (rel instanceof Filter) {
      // 如果是过滤节点，保存引用并获取其输入
      filter = (Filter) rel;
      rel = filter.getInput();
    } else {
      // 如果不是过滤节点，设置为null
      filter = null;
    }
    // 表扫描节点，用于访问基础表
    TableScan scan;
    // 检查关系节点是否为表扫描节点
    if (rel instanceof TableScan) {
      // 如果是表扫描节点，保存引用
      scan = (TableScan) rel;
    } else {
      // 如果不是表扫描节点，设置为null
      scan = null;
    }
    // 如果没有表扫描节点，说明视图不是基于单个表的查询
    if (scan == null) {
      // 如果要求失败时抛出异常
      if (fail) {
        // 抛出验证错误：可修改视图必须基于单个表
        throw validator.newValidationError(sqlNode,
            RESOURCE.modifiableViewMustBeBasedOnSingleTable());
      }
      // 返回不可修改的分析结果
      return new AnalyzeViewResult(this, validator, sql, sqlNode,
          validator.getValidatedNodeType(sqlNode), root, null, null, null,
          null, false);
    }
    // 获取目标关系表
    final RelOptTable targetRelTable = scan.getTable();
    // 获取目标表的行类型
    final RelDataType targetRowType = targetRelTable.getRowType();
    // 获取目标表对象
    final Table table = targetRelTable.unwrapOrThrow(Table.class);
    // 获取目标表的限定名称（包括schema路径）
    final List<String> tablePath = targetRelTable.getQualifiedName();
    // 列映射列表，记录视图列到目标表列的映射关系
    List<Integer> columnMapping;
    // 投影映射，记录目标表列到视图表达式的映射
    final Map<Integer, RexNode> projectMap = new HashMap<>();
    // 如果没有投影节点
    if (project == null) {
      // 列映射为0到目标表字段数的连续整数
      columnMapping = ImmutableIntList.range(0, targetRowType.getFieldCount());
    } else {
      // 创建列映射列表
      columnMapping = new ArrayList<>();
      // 遍历投影节点的所有项目表达式
      for (Ord<RexNode> node : Ord.zip(project.getProjects())) {
        // 如果项目表达式是输入引用（直接引用基础表的列）
        if (node.e instanceof RexInputRef) {
          // 转换为输入引用
          RexInputRef rexInputRef = (RexInputRef) node.e;
          // 获取引用的列索引
          int index = rexInputRef.getIndex();
          // 检查该列是否已经被映射过
          if (projectMap.get(index) != null) {
            // 如果要求失败时抛出异常
            if (fail) {
              // 抛出验证错误：一个列不能被映射多次
              throw validator.newValidationError(sqlNode,
                  RESOURCE.moreThanOneMappedColumn(
                      targetRowType.getFieldList().get(index).getName(),
                      Util.last(tablePath)));
            }
            // 返回不可修改的分析结果
            return new AnalyzeViewResult(this, validator, sql, sqlNode,
                validator.getValidatedNodeType(sqlNode), root, null, null, null,
                null, false);
          }
          // 将目标表列映射到视图列的输入引用
          projectMap.put(index, rexBuilder.makeInputRef(viewRel, node.i));
          // 添加列映射
          columnMapping.add(index);
        } else {
          // 如果不是输入引用，标记为-1（表示这是一个计算列）
          columnMapping.add(-1);
        }
      }
    }
    // 约束条件节点
    final RexNode constraint;
    // 如果有过滤节点
    if (filter != null) {
      // 约束条件为过滤节点的条件表达式
      constraint = filter.getCondition();
    } else {
      // 如果没有过滤节点，约束条件为true（表示无约束）
      constraint = rexBuilder.makeLiteral(true);
    }
    // 过滤器列表，用于存储推断出的视图谓词
    final List<RexNode> filters = new ArrayList<>();
    // If we put a constraint in projectMap above, then filters will not be empty despite
    // being a modifiable view.
    // 第二个过滤器列表，用于重试判断
    final List<RexNode> filters2 = new ArrayList<>();
    // 重试标志
    boolean retry = false;
    // 推断视图谓词，将约束条件分解为独立的过滤条件
    RelOptUtil.inferViewPredicates(projectMap, filters, constraint);
    // 如果要求失败时抛出异常，且过滤器列表不为空
    if (fail && !filters.isEmpty()) {
      // 创建第二个投影映射，用于重试
      final Map<Integer, RexNode> projectMap2 = new HashMap<>();
      // 再次推断视图谓词
      RelOptUtil.inferViewPredicates(projectMap2, filters2, constraint);
      // 如果第二个过滤器列表也不为空
      if (!filters2.isEmpty()) {
        // 抛出验证错误：可修改视图只能包含等值谓词
        throw validator.newValidationError(sqlNode,
            RESOURCE.modifiableViewMustHaveOnlyEqualityPredicates());
      }
      // 设置重试标志为true
      retry = true;
    }

    // Check that all columns that are not projected have a constant value
    // 检查所有未投影的列是否都有常量值
    for (RelDataTypeField field : targetRowType.getFieldList()) {
      // 查找该列在列映射中的位置
      final int x = columnMapping.indexOf(field.getIndex());
      // 如果列被投影了
      if (x >= 0) {
        // 断言该列没有被投影多次
        assert !Util.skip(columnMapping, x + 1).contains(field.getIndex())
            : "column projected more than once; should have checked above";
        continue; // target column is projected
      }
      // 如果该列在投影映射中有表达式（常量表达式）
      if (projectMap.get(field.getIndex()) != null) {
        continue; // constant expression
      }
      // 如果该列可为NULL
      if (field.getType().isNullable()) {
        continue; // don't need expression for nullable columns; NULL suffices
      }
      // 如果要求失败时抛出异常
      if (fail) {
        // 抛出验证错误：视图列没有提供值
        throw validator.newValidationError(sqlNode,
            RESOURCE.noValueSuppliedForViewColumn(field.getName(),
                Util.last(tablePath)));
      }
      // 返回不可修改的分析结果
      return new AnalyzeViewResult(this, validator, sql, sqlNode,
          validator.getValidatedNodeType(sqlNode), root, null, null, null,
          null, false);
    }

    // 判断视图是否可修改：过滤器列表为空，或者重试后第二个过滤器列表为空
    final boolean modifiable = filters.isEmpty() || retry && filters2.isEmpty();
    // 返回视图分析结果
    return new AnalyzeViewResult(this, validator, sql, sqlNode,
        validator.getValidatedNodeType(sqlNode), root, modifiable ? table : null,
        ImmutableList.copyOf(tablePath),
        constraint, ImmutableIntList.copyOf(columnMapping),
        modifiable);
  }

  // 执行DDL（数据定义语言）语句
// @param context 准备上下文
// @param node DDL语句的SQL节点
// 该方法通过配置的解析器工厂获取DDL执行器，然后执行DDL语句
@Override public void executeDdl(Context context, SqlNode node) {
    // 获取连接配置
    final CalciteConnectionConfig config = context.config();
    // 获取SQL解析器实现工厂，如果没有配置则使用默认工厂
    final SqlParserImplFactory parserFactory =
        config.parserFactory(SqlParserImplFactory.class, SqlParserImpl.FACTORY);
    // 从解析器工厂获取DDL执行器
    final DdlExecutor ddlExecutor = parserFactory.getDdlExecutor();
    // 执行DDL语句
    ddlExecutor.executeDdl(context, node);
  }

  /** Factory method for default SQL parser. */
  // 创建默认SQL解析器的工厂方法
  // @param sql 要解析的SQL语句字符串
  // @return SQL解析器实例
  protected SqlParser createParser(String sql) {
    // 使用默认解析器配置创建解析器
    return createParser(sql, createParserConfig());
  }

  /** Factory method for SQL parser with a given configuration. */
  // 使用给定配置创建SQL解析器的工厂方法
  // @param sql 要解析的SQL语句字符串
  // @param parserConfig SQL解析器配置
  // @return SQL解析器实例
  protected SqlParser createParser(String sql, SqlParser.Config parserConfig) {
    // 使用指定配置创建SQL解析器
    return SqlParser.create(sql, parserConfig);
  }

  @Deprecated // to be removed before 2.0
  // 已废弃的创建SQL解析器方法，将在2.0版本前移除
  protected SqlParser createParser(String sql,
      SqlParser.ConfigBuilder parserConfig) {
    // 构建配置并创建解析器
    return createParser(sql, parserConfig.build());
  }

  /** Factory method for SQL parser configuration. */
  // 创建SQL解析器配置的工厂方法
  // @return SQL解析器配置
  protected SqlParser.Config parserConfig() {
    // 返回默认的SQL解析器配置
    return SqlParser.config();
  }

  @Deprecated // to be removed before 2.0
  // 已废弃的创建SQL解析器配置方法，将在2.0版本前移除
  protected SqlParser.ConfigBuilder createParserConfig() {
    // 返回SQL解析器配置构建器
    return SqlParser.configBuilder();
  }

  /** Factory method for default convertlet table. */
  // 创建默认转换表（convertlet table）的工厂方法
  // 转换表用于将SQL运算符转换为Rex表达式
  // @return SQL到Rex转换表实例
  protected SqlRexConvertletTable createConvertletTable() {
    // 返回标准转换表实例
    return StandardConvertletTable.INSTANCE;
  }

  /** Factory method for cluster. */
  // 创建关系优化簇（cluster）的工厂方法
  // 簇是关系代数表达式的基本构建单元，包含优化器、RexBuilder等共享组件
  // @param planner 关系优化器
  // @param rexBuilder Rex表达式构建器
  // @return 关系优化簇实例
  protected RelOptCluster createCluster(RelOptPlanner planner,
      RexBuilder rexBuilder) {
    // 创建并返回关系优化簇
    return RelOptCluster.create(planner, rexBuilder);
  }

  /** Creates a collection of planner factories.
   *
   * <p>The collection must have at least one factory, and each factory must
   * create a planner. If the collection has more than one planner, Calcite will
   * try each planner in turn.
   *
   * <p>One of the things you can do with this mechanism is to try a simpler,
   * faster, planner with a smaller rule set first, then fall back to a more
   * complex planner for complex and costly queries.
   *
   * <p>The default implementation returns a factory that calls
   * {@link #createPlanner(org.apache.calcite.jdbc.CalcitePrepare.Context)}.
   */
  // 创建优化器工厂集合
  // 该集合必须至少包含一个工厂，每个工厂都必须能够创建一个优化器
  // 如果集合中有多个优化器，Calcite会依次尝试每个优化器
  // 使用这种机制可以先用简单快速的优化器（规则集较小）尝试，对于复杂查询再回退到更复杂的优化器
  // 默认实现返回一个调用createPlanner方法的工厂
  protected List<Function1<Context, RelOptPlanner>> createPlannerFactories() {
    // 返回包含单个工厂的列表，该工厂调用createPlanner方法
    return Collections.singletonList(
        context -> createPlanner(context, null, null));
  }

  /** Creates a query planner and initializes it with a default set of
   * rules. */
  // 创建查询优化器并使用默认规则集进行初始化
  // @param prepareContext 准备上下文
  // @return 关系优化器实例
  protected RelOptPlanner createPlanner(CalcitePrepare.Context prepareContext) {
    // 调用带外部上下文和代价工厂的重载方法
    return createPlanner(prepareContext, null, null);
  }

  /** Creates a query planner and initializes it with a default set of
   * rules. */
  // 创建查询优化器并使用默认规则集进行初始化（重载版本）
  // @param prepareContext 准备上下文
  // @param externalContext 外部上下文，如果为null则使用准备上下文的配置
  // @param costFactory 代价工厂，用于计算关系操作的代价
  // @return 关系优化器实例
  protected RelOptPlanner createPlanner(
      final CalcitePrepare.Context prepareContext,
      org.apache.calcite.plan.@Nullable Context externalContext,
      @Nullable RelOptCostFactory costFactory) {
    // 如果外部上下文为null，使用准备上下文的配置创建上下文
    if (externalContext == null) {
      externalContext = Contexts.of(prepareContext.config());
    }
    // 创建Volcano优化器（基于代价的优化器）
    final VolcanoPlanner planner =
        new VolcanoPlanner(costFactory, externalContext);
    // 设置Rex执行器，用于在优化过程中执行常量折叠等操作
    planner.setExecutor(new RexExecutorImpl(DataContexts.EMPTY));
    // 添加约定trait定义，用于管理不同约定之间的转换
    planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
    // 如果启用了排序trait，添加排序trait定义
    if (CalciteSystemProperty.ENABLE_COLLATION_TRAIT.value()) {
      planner.addRelTraitDef(RelCollationTraitDef.INSTANCE);
    }
    // 设置优化方向：自顶向下或自底向上
    planner.setTopDownOpt(prepareContext.config().topDownOpt());
    // 注册默认的优化规则，包括物化视图规则和可绑定规则
    RelOptUtil.registerDefaultRules(planner,
        prepareContext.config().materializationsEnabled(),
        enableBindable);

    // 获取Spark处理器（用于Spark集成）
    final CalcitePrepare.SparkHandler spark = prepareContext.spark();
    // 如果启用了Spark
    if (spark.enabled()) {
      // 注册Spark相关规则
      spark.registerRules(
          new SparkHandler.RuleSetBuilder() {
            @Override public void addRule(RelOptRule rule) {
              // TODO: 添加规则
            }

            @Override public void removeRule(RelOptRule rule) {
              // TODO: 移除规则
            }
          });
    }
    // 运行Hook.PLANNER，允许测试添加或移除规则
    Hook.PLANNER.run(planner); // allow test to add or remove rules

    // 返回配置好的优化器
    return planner;
  }

  // 准备可查询对象（Queryable）用于执行
// @param <T> 结果类型
// @param context 准备上下文
// @param queryable 可查询对象，表示LINQ风格的查询
// @return CalciteSignature 准备好的签名，包含执行所需的所有信息
// 该方法将LINQ查询转换为可执行的形式
@Override public <T> CalciteSignature<T> prepareQueryable(
      Context context,
      Queryable<T> queryable) {
    // 调用prepare_方法，将可查询对象包装为Query对象
    return prepare_(context, Query.of(queryable), queryable.getElementType(),
        -1);
  }

// 准备SQL查询用于执行
// @param <T> 结果类型
// @param context 准备上下文
// @param query 查询对象，包含SQL语句或其他查询信息
// @param elementType 结果元素的Java类型
// @param maxRowCount 最大返回行数，-1表示无限制
// @return CalciteSignature 准备好的签名，包含执行所需的所有信息
// 该方法将SQL查询转换为可执行的形式
@Override public <T> CalciteSignature<T> prepareSql(
      Context context,
      Query<T> query,
      Type elementType,
      long maxRowCount) {
    // 调用prepare_方法进行准备
    return prepare_(context, query, elementType, maxRowCount);
  }

  // prepare_方法：准备查询的核心实现
// @param <T> 结果类型
// @param context 准备上下文
// @param query 查询对象
// @param elementType 结果元素的Java类型
// @param maxRowCount 最大返回行数
// @return CalciteSignature 准备好的签名
// 该方法处理简单查询的快速路径，或者使用优化器工厂尝试优化查询
<T> CalciteSignature<T> prepare_(
      Context context,
      Query<T> query,
      Type elementType,
      long maxRowCount) {
    // 检查是否为简单SQL（如SELECT 1），如果是则使用快速准备路径
    if (SIMPLE_SQLS.contains(query.sql)) {
      // 使用简单准备方法，绕过完整的优化流程
      return simplePrepare(context, castNonNull(query.sql));
    }
    // 获取Java类型工厂
    final JavaTypeFactory typeFactory = context.getTypeFactory();
    // 创建目录读取器
    CalciteCatalogReader catalogReader =
        new CalciteCatalogReader(
            context.getRootSchema(),
            context.getDefaultSchemaPath(),
            typeFactory,
            context.config());
    // 创建优化器工厂列表
    final List<Function1<Context, RelOptPlanner>> plannerFactories =
        createPlannerFactories();
    // 如果没有优化器工厂，抛出断言错误
    if (plannerFactories.isEmpty()) {
      throw new AssertionError("no planner factories");
    }
    // 保存第一个异常，用于在所有优化器都失败时抛出
    RuntimeException exception = Util.FoundOne.NULL;
    // 遍历所有优化器工厂，依次尝试每个优化器
    for (Function1<Context, RelOptPlanner> plannerFactory : plannerFactories) {
      // 应用工厂创建优化器
      final RelOptPlanner planner = plannerFactory.apply(context);
      // 如果优化器为null，抛出断言错误
      if (planner == null) {
        throw new AssertionError("factory returned null planner");
      }
      try {
        // 获取准备语句对象
        CalcitePreparingStmt preparingStmt =
            getPreparingStmt(context, elementType, catalogReader, planner);
        // 调用prepare2_方法进行准备
        return prepare2_(context, query, elementType, maxRowCount,
            catalogReader, preparingStmt);
      } catch (RelOptPlanner.CannotPlanException e) {
        // 如果优化失败，保存异常并尝试下一个优化器
        exception = e;
      }
    }
    // 如果所有优化器都失败，抛出保存的异常
    throw exception;
  }

  /** Returns CalcitePreparingStmt
   *
   * <p>Override this function to return a custom {@link CalcitePreparingStmt} and
   * {@link #createSqlValidator} to enable custom validation logic.
   */
  // 获取CalcitePreparingStmt对象
  // 重写此方法可以返回自定义的CalcitePreparingStmt和createSqlValidator以启用自定义验证逻辑
  // @param context 准备上下文
  // @param elementType 结果元素的Java类型
  // @param catalogReader 目录读取器
  // @param planner 关系优化器
  // @return CalcitePreparingStmt 准备语句对象
  protected CalcitePreparingStmt getPreparingStmt(
      Context context,
      Type elementType,
      CalciteCatalogReader catalogReader,
      RelOptPlanner planner) {
    // 获取Java类型工厂
    final JavaTypeFactory typeFactory = context.getTypeFactory();
    // 确定可枚举关系的偏好：数组或自定义
    final EnumerableRel.Prefer prefer;
    // 如果元素类型是Object[]数组，偏好数组
    if (elementType == Object[].class) {
      prefer = EnumerableRel.Prefer.ARRAY;
    } else {
      // 否则偏好自定义类型
      prefer = EnumerableRel.Prefer.CUSTOM;
    }
    // 确定结果约定
    final Convention resultConvention =
        enableBindable ? BindableConvention.INSTANCE
            : EnumerableConvention.INSTANCE;
    // 创建并返回CalcitePreparingStmt对象
    return new CalcitePreparingStmt(this, context, catalogReader, typeFactory,
            context.getRootSchema(), prefer, createCluster(planner, new RexBuilder(typeFactory)),
            resultConvention, createConvertletTable());
  }

  /** Quickly prepares a simple SQL statement, circumventing the usual
   * preparation process. */
  // 快速准备简单SQL语句，绕过通常的准备流程
  // 该方法用于处理如SELECT 1这样的简单查询，避免完整的解析、验证、优化流程
  // @param <T> 结果类型
  // @param context 准备上下文
  // @param sql SQL语句
  // @return CalciteSignature 准备好的签名
  private static <T> CalciteSignature<T> simplePrepare(Context context, String sql) {
    // 获取Java类型工厂
    final JavaTypeFactory typeFactory = context.getTypeFactory();
    // 创建简单的行类型：包含一个整数列
    final RelDataType x =
        typeFactory.builder()
            .add(SqlUtil.deriveAliasFromOrdinal(0), SqlTypeName.INTEGER)
            .build();
    // 创建结果列表，包含单个值1
    @SuppressWarnings("unchecked")
    final List<T> list = (List) ImmutableList.of(1);
    // 列的原始信息为null
    final List<String> origin = null;
    // 所有列的原始信息都为null
    final List<@Nullable List<String>> origins =
        Collections.nCopies(x.getFieldCount(), origin);
    // 获取列元数据列表
    final List<ColumnMetaData> columns =
        getColumnMetaDataList(typeFactory, x, x, origins);
    // 推断游标工厂类型
    final Meta.CursorFactory cursorFactory =
        Meta.CursorFactory.deduce(columns, null);
    // 返回准备好的签名
    return new CalciteSignature<>(
        sql,
        ImmutableList.of(),
        ImmutableMap.of(),
        x,
        columns,
        cursorFactory,
        context.getRootSchema(),
        ImmutableList.of(),
        -1, dataContext -> Linq4j.asEnumerable(list),
        Meta.StatementType.SELECT);
  }

  /**
   * Deduces the broad type of statement.
   * Currently returns SELECT for most statement types, but this may change.
   *
   * @param kind Kind of statement
   */
  // 推断语句的广义类型
  // 目前大多数语句类型返回SELECT，但这可能会改变
  // @param kind SQL语句类型
  // @return 语句类型元数据：DML（INSERT/DELETE/UPDATE/MERGE）或SELECT
  private static Meta.StatementType getStatementType(SqlKind kind) {
    switch (kind) {
    // 如果是INSERT、DELETE、UPDATE或MERGE语句
    case INSERT:
    case DELETE:
    case UPDATE:
    case MERGE:
      // 返回DML语句类型
      return Meta.StatementType.IS_DML;
    // 默认返回SELECT语句类型
    default:
      return Meta.StatementType.SELECT;
    }
  }

  /**
   * Deduces the broad type of statement for a prepare result.
   * Currently returns SELECT for most statement types, but this may change.
   *
   * @param preparedResult Prepare result
   */
  // 推断准备结果的语句广义类型
  // 目前大多数语句类型返回SELECT，但这可能会改变
  // @param preparedResult 准备结果
  // @return 语句类型元数据：DML或SELECT
  private static Meta.StatementType getStatementType(Prepare.PreparedResult preparedResult) {
    // 如果准备结果是DML语句
    if (preparedResult.isDml()) {
      // 返回DML语句类型
      return Meta.StatementType.IS_DML;
    } else {
      // 否则返回SELECT语句类型
      return Meta.StatementType.SELECT;
    }
  }

  // prepare2_方法：准备查询的第二阶段实现
// @param <T> 结果类型
// @param context 准备上下文
// @param query 查询对象
// @param elementType 结果元素的Java类型
// @param maxRowCount 最大返回行数
// @param catalogReader 目录读取器
// @param preparingStmt 准备语句对象
// @return CalciteSignature 准备好的签名
// 该方法处理SQL查询、可查询对象或关系节点的准备过程
<T> CalciteSignature<T> prepare2_(
      Context context,
      Query<T> query,
      Type elementType,
      long maxRowCount,
      CalciteCatalogReader catalogReader,
      CalcitePreparingStmt preparingStmt) {
    // 获取Java类型工厂
    final JavaTypeFactory typeFactory = context.getTypeFactory();

    // 结果行类型
    final RelDataType x;
    // 准备结果
    final Prepare.PreparedResult preparedResult;
    // 语句类型
    final Meta.StatementType statementType;
    // 如果查询包含SQL字符串
    if (query.sql != null) {
      // 获取连接配置
      final CalciteConnectionConfig config = context.config();
      // 配置SQL解析器：设置大小写、引用方式、符合性、大小写敏感等
      SqlParser.Config parserConfig = parserConfig()
          .withQuotedCasing(config.quotedCasing())
          .withUnquotedCasing(config.unquotedCasing())
          .withQuoting(config.quoting())
          .withConformance(config.conformance())
          .withCaseSensitive(config.caseSensitive());
      // 获取SQL解析器实现工厂
      final SqlParserImplFactory parserFactory =
          config.parserFactory(SqlParserImplFactory.class, null);
      // 如果配置了自定义解析器工厂
      if (parserFactory != null) {
        // 使用自定义解析器工厂
        parserConfig = parserConfig.withParserFactory(parserFactory);
      }
      // 创建SQL解析器
      SqlParser parser = createParser(query.sql,  parserConfig);
      // SQL节点
      SqlNode sqlNode;
      try {
        // 解析SQL语句
        sqlNode = parser.parseStmt();
        // 推断语句类型
        statementType = getStatementType(sqlNode.getKind());
      } catch (SqlParseException e) {
        // 如果解析失败，抛出运行时异常
        throw new RuntimeException(
            "parse failed: " + e.getMessage(), e);
      }

      // 运行Hook.PARSE_TREE，允许测试访问解析树
      Hook.PARSE_TREE.run(new Object[] {query.sql, sqlNode});

      // 如果是DDL语句
      if (sqlNode.getKind().belongsTo(SqlKind.DDL)) {
        // 执行DDL语句
        executeDdl(context, sqlNode);

        // 返回DDL语句的签名
        return new CalciteSignature<>(query.sql,
            ImmutableList.of(),
            ImmutableMap.of(), null,
            ImmutableList.of(), Meta.CursorFactory.OBJECT,
            null, ImmutableList.of(), -1, null,
            Meta.StatementType.OTHER_DDL);
      }

      // 创建SQL验证器
      final SqlValidator validator =
          preparingStmt.createSqlValidator(catalogReader,
              UnaryOperator.identity());

      // 准备SQL语句，生成准备结果
      preparedResult =
          preparingStmt.prepareSql(sqlNode, Object.class, validator, true);
      // 根据SQL语句类型确定结果行类型
      switch (sqlNode.getKind()) {
      // 如果是INSERT、DELETE、UPDATE、MERGE或EXPLAIN语句
      case INSERT:
      case DELETE:
      case UPDATE:
      case MERGE:
      case EXPLAIN:
        // FIXME: getValidatedNodeType is wrong for DML
        // 创建DML行类型
        x = RelOptUtil.createDmlRowType(sqlNode.getKind(), typeFactory);
        break;
      // 默认情况：使用验证器获取验证后的节点类型
      default:
        x = validator.getValidatedNodeType(sqlNode);
      }
    } else if (query.queryable != null) {
      // 如果查询包含可查询对象
      // 创建元素类型
      x = context.getTypeFactory().createType(elementType);
      // 准备可查询对象
      preparedResult =
          preparingStmt.prepareQueryable(query.queryable, x);
      // 推断语句类型
      statementType = getStatementType(preparedResult);
    } else {
      // 如果查询包含关系节点
      // 确保关系节点不为null
      requireNonNull(query.rel);
      // 获取关系节点的行类型
      x = query.rel.getRowType();
      // 准备关系节点
      preparedResult = preparingStmt.prepareRel(query.rel);
      // 推断语句类型
      statementType = getStatementType(preparedResult);
    }

    // 创建参数列表
    final List<AvaticaParameter> parameters = new ArrayList<>();
    // 获取参数行类型
    final RelDataType parameterRowType = preparedResult.getParameterRowType();
    // 遍历参数行类型的所有字段
    for (RelDataTypeField field : parameterRowType.getFieldList()) {
      // 获取字段类型
      RelDataType type = field.getType();
      // 添加参数到参数列表
      parameters.add(
          new AvaticaParameter(
              false, // 不允许为null
              getPrecision(type), // 获取精度
              getScale(type), // 获取标度
              getTypeOrdinal(type), // 获取类型序号
              getTypeName(type), // 获取类型名称
              getClassName(type), // 获取Java类名
              field.getName())); // 字段名称
    }

    // 创建JDBC结构类型
    RelDataType jdbcType = makeStruct(typeFactory, x);
    // 获取字段原始信息列表
    final List<? extends @Nullable List<String>> originList = preparedResult.getFieldOrigins();
    // 获取列元数据列表
    final List<ColumnMetaData> columns =
        getColumnMetaDataList(typeFactory, x, jdbcType, originList);
    // 结果类
    Class resultClazz = null;
    // 如果准备结果实现了Typed接口
    if (preparedResult instanceof Typed) {
      // 获取元素类型
      resultClazz = (Class) ((Typed) preparedResult).getElementType();
    }
    // 根据结果约定确定游标工厂类型
    final Meta.CursorFactory cursorFactory =
        preparingStmt.resultConvention == BindableConvention.INSTANCE
            ? Meta.CursorFactory.ARRAY // 可绑定约定使用数组游标
            : Meta.CursorFactory.deduce(columns, resultClazz); // 可枚举约定推断游标类型
    //noinspection unchecked
    // 获取可绑定对象
    final Bindable<T> bindable = preparedResult.getBindable(cursorFactory);
    // 返回准备好的签名
    return new CalciteSignature<>(
        query.sql, // SQL语句
        parameters, // 参数列表
        preparingStmt.internalParameters, // 内部参数
        jdbcType, // JDBC类型
        columns, // 列元数据
        cursorFactory, // 游标工厂
        context.getRootSchema(), // 根schema
        preparedResult instanceof Prepare.PreparedResultImpl
            ? ((Prepare.PreparedResultImpl) preparedResult).collations // 排序信息
            : ImmutableList.of(), // 无排序
        maxRowCount, // 最大返回行数
        bindable, // 可绑定对象
        statementType); // 语句类型
  }

  // 创建SQL验证器
// @param context 准备上下文
// @param catalogReader 目录读取器
// @param configTransform 配置转换函数，用于自定义验证器配置
// @return SQL验证器实例
private static SqlValidator createSqlValidator(Context context,
      CalciteCatalogReader catalogReader,
      UnaryOperator<SqlValidator.Config> configTransform) {
    // 获取标准SQL运算符表
    final SqlOperatorTable opTab0 =
        context.config().fun(SqlOperatorTable.class,
            SqlStdOperatorTable.instance());
    // 创建运算符表列表
    final List<SqlOperatorTable> list = new ArrayList<>();
    // 添加标准运算符表
    list.add(opTab0);
    // 添加目录读取器（包含用户定义的函数）
    list.add(catalogReader);
    // 链接所有运算符表
    final SqlOperatorTable opTab = SqlOperatorTables.chain(list);
    // 获取Java类型工厂
    final JavaTypeFactory typeFactory = context.getTypeFactory();
    // 获取连接配置
    final CalciteConnectionConfig connectionConfig = context.config();
    // 应用配置转换，创建验证器配置
    final SqlValidator.Config config =
        configTransform.apply(
            SqlValidator.Config.DEFAULT
                .withLenientOperatorLookup(connectionConfig.lenientOperatorLookup()) // 宽松运算符查找
                .withConformance(connectionConfig.conformance()) // SQL符合性
                .withDefaultNullCollation(connectionConfig.defaultNullCollation()) // NULL排序规则
                .withIdentifierExpansion(true)); // 标识符展开
    // 创建并返回Calcite SQL验证器
    return new CalciteSqlValidator(opTab, catalogReader, typeFactory,
        config);
  }

  // 获取列元数据列表

  // @param typeFactory Java类型工厂

  // @param x 结果行类型

  // @param jdbcType JDBC结构类型

  // @param originList 列的原始信息列表

  // @return 列元数据列表

  private static List<ColumnMetaData> getColumnMetaDataList(

        JavaTypeFactory typeFactory, RelDataType x, RelDataType jdbcType,

        List<? extends @Nullable List<String>> originList) {

      // 创建列元数据列表

      final List<ColumnMetaData> columns = new ArrayList<>();

      // 遍历JDBC类型的所有字段

      for (Ord<RelDataTypeField> pair : Ord.zip(jdbcType.getFieldList())) {

        // 获取字段

        final RelDataTypeField field = pair.e;

  

        // 获取字段类型

        final RelDataType type = field.getType();

        // 获取字段类型（如果是结构类型，使用原始类型的字段类型）

        final RelDataType fieldType =

            x.isStruct() ? x.getFieldList().get(pair.i).getType() : type;

        // 添加列元数据

        columns.add(

            metaData(typeFactory, columns.size(), field.getName(), type,

                fieldType, originList.get(pair.i)));

      }

      // 返回列元数据列表

      return columns;

    }

  // 创建列元数据
// @param typeFactory Java类型工厂
// @param ordinal 列序号
// @param fieldName 字段名称
// @param type 字段类型
// @param fieldType 字段类型（可能为null）
// @param origins 列的原始信息
// @return 列元数据对象
private static ColumnMetaData metaData(JavaTypeFactory typeFactory, int ordinal,
      String fieldName, RelDataType type, @Nullable RelDataType fieldType,
      @Nullable List<String> origins) {
    // 获取Avatica类型
    final ColumnMetaData.AvaticaType avaticaType =
        avaticaType(typeFactory, type, fieldType);
    // 创建并返回列元数据
    return new ColumnMetaData(
        ordinal, // 列序号
        false, // 是否自动递增
        true, // 是否区分大小写
        false, // 是否可搜索
        false, // 是否为货币类型
        type.isNullable() // 是否可为NULL
            ? DatabaseMetaData.columnNullable
            : DatabaseMetaData.columnNoNulls,
        true, // 是否可读
        type.getPrecision(), // 精度
        fieldName, // 字段名称
        origin(origins, 0), // 表名
        origin(origins, 2), // schema名
        getPrecision(type), // 显示精度
        getScale(type), // 标度
        origin(origins, 1), // 目录名
        null, // 默认值
        avaticaType, // Avatica类型
        true, // 是否可写
        false, // 是否绝对可读
        false, // 是否绝对可写
        avaticaType.columnClassName()); // Java类名
  }

  // 获取Avatica类型
// @param typeFactory Java类型工厂
// @param type 字段类型
// @param fieldType 字段类型（可能为null）
// @return Avatica类型对象
private static ColumnMetaData.AvaticaType avaticaType(JavaTypeFactory typeFactory,
      RelDataType type, @Nullable RelDataType fieldType) {
    // 类型名称
    final String typeName;
    // 如果是度量类型
    if (type instanceof MeasureSqlType) {
      // 获取度量元素类型
      type = requireNonNull(type.getMeasureElementType(), "measure type");
      // 创建度量类型名称
      typeName = "MEASURE<" + getTypeName(type) + ">";
    } else {
      // 获取类型名称
      typeName = getTypeName(type);
    }
    // 如果是数组类型（有组件类型）
    if (type.getComponentType() != null) {
      // 递归获取组件类型
      final ColumnMetaData.AvaticaType componentType =
          avaticaType(typeFactory, type.getComponentType(), null);
      // 获取组件类型的Java类
      final Type clazz = typeFactory.getJavaClass(type.getComponentType());
      // 获取类型表示
      final ColumnMetaData.Rep rep =
          requireNonNull(ColumnMetaData.Rep.of(clazz));
      // 返回数组类型
      return ColumnMetaData.array(componentType, typeName, rep);
    } else {
      // 获取类型序号
      int typeOrdinal = getTypeOrdinal(type);
      // 根据类型序号处理不同类型
      switch (typeOrdinal) {
      // 如果是结构类型
      case Types.STRUCT:
        // 创建列元数据列表
        final List<ColumnMetaData> columns = new ArrayList<>(type.getFieldList().size());
        // 遍历所有字段
        for (RelDataTypeField field : type.getFieldList()) {
          // 添加列元数据
          columns.add(
              metaData(typeFactory, field.getIndex(), field.getName(),
                  field.getType(), null, null));
        }
        // 返回结构类型
        return ColumnMetaData.struct(columns);
      // 如果是几何类型
      case ExtraSqlTypes.GEOMETRY:
        // 转换为VARCHAR类型
        typeOrdinal = Types.VARCHAR;
        // fall through
      // 默认情况：标量类型
      default:
        // 获取Java类
        final Type clazz =
            typeFactory.getJavaClass(Util.first(fieldType, type));
        // 获取类型表示
        final ColumnMetaData.Rep rep =
            requireNonNull(ColumnMetaData.Rep.of(clazz));
        // 返回标量类型
        return ColumnMetaData.scalar(typeOrdinal, typeName, rep);
      }
    }
  }

  // 从原始信息列表中获取指定位置的原始信息
// @param origins 原始信息列表，格式为[目录名, schema名, 表名]
// @param offsetFromEnd 从末尾开始的偏移量（0=表名, 1=schema名, 2=目录名）
// @return 原始信息，如果不存在则返回null
private static @Nullable String origin(@Nullable List<String> origins,
      int offsetFromEnd) {
    // 如果原始信息为null或偏移量超出范围，返回null
    return origins == null || offsetFromEnd >= origins.size()
        ? null
        : origins.get(origins.size() - 1 - offsetFromEnd);
  }

  // 获取类型的JDBC类型序号
  // @param type 关系数据类型
  // @return JDBC类型序号
  private static int getTypeOrdinal(RelDataType type) {
    // 根据SQL类型名称处理
    switch (type.getSqlTypeName()) {
    // 如果是度量类型
    case MEASURE:
      // getMeasureElementType() for MEASURE types will never be null
      // 获取度量元素类型
      final RelDataType measureElementType =
          requireNonNull(type.getMeasureElementType(), "measureElementType");
      // 返回度量元素类型的JDBC序号
      return measureElementType.getSqlTypeName().getJdbcOrdinal();
    // 默认情况：返回类型的JDBC序号
    default:
      return type.getSqlTypeName().getJdbcOrdinal();
    }
  }

  // 获取类型的Java类名
  // @param type 关系数据类型
  // @return Java类名
  // 注意：目前总是返回Object.class.getName()，这是CALCITE-2613的临时解决方案
  private static String getClassName(@SuppressWarnings("unused") RelDataType type) {
    return Object.class.getName(); // CALCITE-2613
  }

  // 获取类型的标度（小数位数）
  // @param type 关系数据类型
  // @return 标度，如果未指定则返回0
  private static int getScale(RelDataType type) {
    // 如果标度未指定，返回0；否则返回实际标度
    return type.getScale() == RelDataType.SCALE_NOT_SPECIFIED
        ? 0
        : type.getScale();
  }

  // 获取类型的精度（长度或小数位数）
  // @param type 关系数据类型
  // @return 精度，如果未指定则返回0
  private static int getPrecision(RelDataType type) {
    // 如果精度未指定，返回0；否则返回实际精度
    return type.getPrecision() == RelDataType.PRECISION_NOT_SPECIFIED
        ? 0
        : type.getPrecision();
  }

  /** Returns the type name in string form. Does not include precision, scale
   * or whether nulls are allowed. Example: "DECIMAL" not "DECIMAL(7, 2)";
   * "INTEGER" not "JavaType(int)". */
  // 获取类型的字符串名称
  // 不包括精度、标度或是否允许NULL
  // 例如："DECIMAL"而不是"DECIMAL(7, 2)"；"INTEGER"而不是"JavaType(int)"
  // @param type 关系数据类型
  // @return 类型名称字符串
  private static String getTypeName(RelDataType type) {
    // 获取SQL类型名称
    final SqlTypeName sqlTypeName = type.getSqlTypeName();
    // 根据SQL类型名称处理
    switch (sqlTypeName) {
    // 如果是数组、多重集、映射、行或度量类型
    case ARRAY:
    case MULTISET:
    case MAP:
    case ROW:
    case MEASURE:
      // 返回类型的字符串表示（例如"INTEGER ARRAY"）
      return type.toString(); // e.g. "INTEGER ARRAY"
    // 间隔类型：年-月
    case INTERVAL_YEAR_MONTH:
      return "INTERVAL_YEAR_TO_MONTH";
    // 间隔类型：天-小时
    case INTERVAL_DAY_HOUR:
      return "INTERVAL_DAY_TO_HOUR";
    // 间隔类型：天-分钟
    case INTERVAL_DAY_MINUTE:
      return "INTERVAL_DAY_TO_MINUTE";
    // 间隔类型：天-秒
    case INTERVAL_DAY_SECOND:
      return "INTERVAL_DAY_TO_SECOND";
    // 间隔类型：小时-分钟
    case INTERVAL_HOUR_MINUTE:
      return "INTERVAL_HOUR_TO_MINUTE";
    // 间隔类型：小时-秒
    case INTERVAL_HOUR_SECOND:
      return "INTERVAL_HOUR_TO_SECOND";
    // 间隔类型：分钟-秒
    case INTERVAL_MINUTE_SECOND:
      return "INTERVAL_MINUTE_TO_SECOND";
    // 默认情况：返回SQL类型名称
    default:
      return sqlTypeName.getName(); // e.g. "DECIMAL", "INTERVAL_YEAR_MONTH"
    }
  }

  // 填充物化视图的数据
// @param context 准备上下文
// @param cluster 关系优化簇
// @param materialization 物化视图信息
// 该方法将物化视图的查询结果填充到物化表中
protected void populateMaterializations(Context context,
      RelOptCluster cluster, Prepare.Materialization materialization) {
    // REVIEW: initialize queryRel and tableRel inside MaterializationService,
    // not here?
    try {
      // 获取物化表的schema
      final CalciteSchema schema = materialization.materializedTable.schema;
      // 创建目录读取器
      CalciteCatalogReader catalogReader =
          new CalciteCatalogReader(
              schema.root(), // 根schema
              materialization.viewSchemaPath, // 视图schema路径
              context.getTypeFactory(), // 类型工厂
              context.config()); // 配置
      // 创建物化器
      final CalciteMaterializer materializer =
          new CalciteMaterializer(this, context, catalogReader, schema,
              cluster, createConvertletTable());
      // 填充物化视图数据
      materializer.populate(materialization);
    } catch (Exception e) {
      // 如果填充失败，抛出运行时异常
      throw new RuntimeException("While populating materialization "
          + materialization.materializedTable.path(), e);
    }
  }

  // 将类型转换为结构类型
// 如果类型已经是结构类型，直接返回；否则创建包含单个字段的结构类型
// @param typeFactory 类型工厂
// @param type 要转换的类型
// @return 结构类型
private static RelDataType makeStruct(
      RelDataTypeFactory typeFactory,
      RelDataType type) {
    // 如果类型已经是结构类型，直接返回
    if (type.isStruct()) {
      return type;
    }
    // 创建包含单个字段的结构类型，字段名为"$0"
    return typeFactory.builder().add("$0", type).build();
  }

  @Deprecated // to be removed before 2.0
  // 已废弃的perform方法，将在2.0版本前移除
  public <R> R perform(CalciteServerStatement statement,
      Frameworks.PrepareAction<R> action) {
    // 调用重载的perform方法
    return perform(statement, action.getConfig(), action);
  }

  /** Executes a prepare action. */
  // 执行准备操作
  // @param <R> 结果类型
  // @param statement 服务器语句
  // @param config 框架配置
  // @param action 准备操作
  // @return 操作结果
  public <R> R perform(CalciteServerStatement statement,
      FrameworkConfig config, Frameworks.BasePrepareAction<R> action) {
    // 创建准备上下文
    final CalcitePrepare.Context prepareContext =
        statement.createPrepareContext();
    // 获取Java类型工厂
    final JavaTypeFactory typeFactory = prepareContext.getTypeFactory();
    // 获取默认schema
    SchemaPlus defaultSchema = config.getDefaultSchema();
    // 确定要使用的schema
    final CalciteSchema schema =
        defaultSchema != null
            ? CalciteSchema.from(defaultSchema) // 使用配置的默认schema
            : prepareContext.getRootSchema(); // 使用根schema
    // 创建目录读取器
    CalciteCatalogReader catalogReader =
        new CalciteCatalogReader(schema.root(), // 根schema
            schema.path(null), // schema路径
            typeFactory, // 类型工厂
            prepareContext.config()); // 配置
    // 创建Rex构建器
    final RexBuilder rexBuilder = new RexBuilder(typeFactory);
    // 创建优化器
    final RelOptPlanner planner =
        createPlanner(prepareContext, // 准备上下文
            config.getContext(), // 外部上下文
            config.getCostFactory()); // 代价工厂
    // 创建关系优化簇
    final RelOptCluster cluster = createCluster(planner, rexBuilder);
    // 应用准备操作并返回结果
    return action.apply(cluster, catalogReader,
        prepareContext.getRootSchema().plus(), statement);
  }

  /** Holds state for the process of preparing a SQL statement.
   *
   * <p>Overload this class and {@link #createSqlValidator} to provide desired
   * SqlValidator and custom validation logic.
   */
// CalcitePreparingStmt类：保存SQL语句准备过程的状态
// 继承自Prepare抽象类，实现RelOptTable.ViewExpander接口
// 重载此类和createSqlValidator方法可以提供自定义的SQL验证器和验证逻辑
public static class CalcitePreparingStmt extends Prepare
      implements RelOptTable.ViewExpander {
    // 关系优化器，用于优化关系代数表达式
    protected final RelOptPlanner planner;
    // Rex表达式构建器，用于创建Rex表达式
    protected final RexBuilder rexBuilder;
    // CalcitePrepareImpl实例，用于访问准备器功能
    protected final CalcitePrepareImpl prepare;
    // Calcite schema，包含表、函数等元数据
    protected final CalciteSchema schema;
    // 类型工厂，用于创建和操作类型
    protected final RelDataTypeFactory typeFactory;
    // SQL到Rex转换表，用于将SQL运算符转换为Rex表达式
    protected final SqlRexConvertletTable convertletTable;
    // 可枚举关系的偏好：数组或自定义
    private final EnumerableRel.@Nullable Prefer prefer;
    // 关系优化簇，包含优化器、RexBuilder等共享组件
    private final RelOptCluster cluster;
    // 内部参数映射，用于存储执行过程中的内部参数
    private final Map<String, Object> internalParameters =
        new LinkedHashMap<>();
    // 视图展开深度，用于跟踪视图嵌套展开的深度
    @SuppressWarnings("unused")
    private int expansionDepth;
    // SQL验证器，用于验证SQL语句的语义正确性
    private @Nullable SqlValidator sqlValidator;

    /** Constructor.
     *
     * <p>Overload this constructor and {@link #createSqlValidator} to provide
     * desired SqlValidator and custom validation logic.
     */
    // 构造方法
    // 重载此构造方法和createSqlValidator方法可以提供自定义的SQL验证器和验证逻辑
    // @param prepare CalcitePrepareImpl实例
    // @param context 准备上下文
    // @param catalogReader 目录读取器
    // @param typeFactory 类型工厂
    // @param schema Calcite schema
    // @param prefer 可枚举关系的偏好
    // @param cluster 关系优化簇
    // @param resultConvention 结果约定
    // @param convertletTable SQL到Rex转换表
    public CalcitePreparingStmt(CalcitePrepareImpl prepare,
        Context context,
        CatalogReader catalogReader,
        RelDataTypeFactory typeFactory,
        CalciteSchema schema,
        EnumerableRel.@Nullable Prefer prefer,
        RelOptCluster cluster,
        Convention resultConvention,
        SqlRexConvertletTable convertletTable) {
      // 调用父类构造方法
      super(context, catalogReader, resultConvention);
      // 保存CalcitePrepareImpl实例
      this.prepare = prepare;
      // 保存schema
      this.schema = schema;
      // 保存偏好
      this.prefer = prefer;
      // 保存关系优化簇
      this.cluster = cluster;
      // 从簇中获取优化器
      this.planner = cluster.getPlanner();
      // 从簇中获取Rex构建器
      this.rexBuilder = cluster.getRexBuilder();
      // 保存类型工厂
      this.typeFactory = typeFactory;
      // 保存转换表
      this.convertletTable = convertletTable;
    }

    // 初始化方法
    // @param runtimeContextClass 运行时上下文类
    // 当前实现为空，子类可以重写以提供自定义初始化逻辑
    @Override protected void init(Class runtimeContextClass) {
    }

    // 准备可查询对象（Queryable）用于执行
    // @param queryable 可查询对象，表示LINQ风格的查询
    // @param resultType 结果类型
    // @return 准备结果
    public PreparedResult prepareQueryable(
        final Queryable queryable,
        RelDataType resultType) {
      // 调用prepare_方法，提供关系节点生成函数
      return prepare_(() -> {
        // 创建关系优化簇
        final RelOptCluster cluster =
            prepare.createCluster(planner, rexBuilder);
        // 使用LixToRelTranslator将LINQ查询转换为关系代数表达式
        return new LixToRelTranslator(cluster, CalcitePreparingStmt.this)
            .translate(queryable);
      }, resultType);
    }

    // 准备关系节点用于执行
    // @param rel 关系节点
    // @return 准备结果
    public PreparedResult prepareRel(final RelNode rel) {
      // 调用prepare_方法，提供关系节点生成函数和结果类型
      return prepare_(() -> rel, rel.getRowType());
    }

    // prepare_方法：准备查询的核心实现
    // @param fn 关系节点生成函数
    // @param resultType 结果类型
    // @return 准备结果
    private PreparedResult prepare_(Supplier<RelNode> fn,
        RelDataType resultType) {
      // 运行时上下文类
      Class runtimeContextClass = Object.class;
      // 初始化
      init(runtimeContextClass);

      // 获取关系节点
      final RelNode rel = fn.get();
      // 获取行类型
      final RelDataType rowType = rel.getRowType();
      // 创建字段列表：字段索引和字段名称的对
      final List<Pair<Integer, String>> fields =
          Pair.zip(ImmutableIntList.identity(rowType.getFieldCount()),
              rowType.getFieldNames());
      // 确定排序规则：如果是Sort节点则使用其排序规则，否则使用空排序
      final RelCollation collation =
          rel instanceof Sort
              ? ((Sort) rel).collation
              : RelCollations.EMPTY;
      // 创建关系根节点
      RelRoot root =
          new RelRoot(rel, resultType, SqlKind.SELECT, fields, collation,
              ImmutableList.of());

      // 如果有时间跟踪器，记录SQL到关系代数转换结束时间
      if (timingTracer != null) {
        timingTracer.traceTime("end sql2rel");
      }

      // 创建JDBC结构类型
      final RelDataType jdbcType =
          makeStruct(rexBuilder.getTypeFactory(), resultType);
      // 设置字段原始信息为null
      fieldOrigins = Collections.nCopies(jdbcType.getFieldCount(), null);
      // 设置参数行类型为空
      parameterRowType = rexBuilder.getTypeFactory().builder().build();

      // Structured type flattening, view expansion, and plugging in
      // physical storage.
      // 展平结构化类型、展开视图、插入物理存储
      root = root.withRel(flattenTypes(root.rel, true));

      // Trim unused fields.
      // 修剪未使用的字段
      root = trimUnusedFields(root);

      // 创建空的物化视图列表
      final List<Materialization> materializations = ImmutableList.of();
      // 创建空的格点列表
      final List<CalciteSchema.LatticeEntry> lattices = ImmutableList.of();
      // 优化关系树
      root = optimize(root, materializations, lattices);

      // 如果有时间跟踪器，记录优化结束时间
      if (timingTracer != null) {
        timingTracer.traceTime("end optimization");
      }

      // 实现关系树，生成可执行代码
      return implement(root);
    }

    // 获取SQL到关系代数转换器
    // @param validator SQL验证器
    // @param catalogReader 目录读取器
    // @param config 转换器配置
    // @return SQL到关系代数转换器实例
    @Override protected SqlToRelConverter getSqlToRelConverter(
        SqlValidator validator,
        CatalogReader catalogReader,
        SqlToRelConverter.Config config) {
      // 创建并返回SQL到关系代数转换器
      return new SqlToRelConverter(this, validator, catalogReader, cluster,
          convertletTable, config);
    }

    // 展平结构化类型
    // @param rootRel 根关系节点
    // @param restructure 是否重构
    // @return 展平后的关系节点
    @Override public RelNode flattenTypes(
        RelNode rootRel,
        boolean restructure) {
      // 获取Spark处理器
      final SparkHandler spark = context.spark();
      // 如果启用了Spark
      if (spark.enabled()) {
        // 使用Spark处理器展平类型
        return spark.flattenTypes(planner, rootRel, restructure);
      }
      // 否则直接返回原始关系节点
      return rootRel;
    }

    // 解除关联（去相关化）
    // @param sqlToRelConverter SQL到关系代数转换器
    // @param query SQL查询节点
    // @param rootRel 根关系节点
    // @return 解除关联后的关系节点
    @Override protected RelNode decorrelate(SqlToRelConverter sqlToRelConverter,
        SqlNode query, RelNode rootRel) {
      // 调用转换器的去相关化方法
      return sqlToRelConverter.decorrelate(query, rootRel);
    }

    // 展开视图
    // @param rowType 行类型
    // @param queryString 视图查询字符串
    // @param schemaPath schema路径
    // @param viewPath 视图路径
    // @return 展开后的关系根节点
    @Override public RelRoot expandView(RelDataType rowType, String queryString,
        List<String> schemaPath, @Nullable List<String> viewPath) {
      // 增加展开深度
      expansionDepth++;

      // 创建SQL解析器
      SqlParser parser = prepare.createParser(queryString);
      // SQL节点
      SqlNode sqlNode;
      try {
        // 解析查询
        sqlNode = parser.parseQuery();
      } catch (SqlParseException e) {
        // 如果解析失败，抛出运行时异常
        throw new RuntimeException("parse failed", e);
      }
      // View may have different schema path than current connection.
      // 视图可能有与当前连接不同的schema路径
      // 使用视图的schema路径创建目录读取器
      final CatalogReader catalogReader =
          this.catalogReader.withSchemaPath(schemaPath);
      // 创建SQL验证器，配置为嵌入式查询
      SqlValidator validator =
          createSqlValidator(catalogReader, c -> c.withEmbeddedQuery(true));
      // 配置SQL到关系代数转换器：启用修剪未使用字段
      final SqlToRelConverter.Config config =
          SqlToRelConverter.config().withTrimUnusedFields(true);
      // 获取SQL到关系代数转换器
      SqlToRelConverter sqlToRelConverter =
          getSqlToRelConverter(validator, catalogReader, config);
      // 转换查询为关系代数
      RelRoot root =
          sqlToRelConverter.convertQuery(sqlNode, true, true);

      // 减少展开深度
      --expansionDepth;
      // 返回关系根节点
      return root;
    }

    // 创建SQL验证器
    // @param catalogReader 目录读取器
    // @param configTransform 配置转换函数
    // @return SQL验证器实例
    protected SqlValidator createSqlValidator(CatalogReader catalogReader,
        UnaryOperator<SqlValidator.Config> configTransform) {
      // 调用CalcitePrepareImpl的静态方法创建SQL验证器
      return CalcitePrepareImpl.createSqlValidator(context,
          (CalciteCatalogReader) catalogReader, configTransform);
    }

    // 获取SQL验证器
    // @return SQL验证器实例
    // 如果验证器不存在则创建，否则返回缓存的验证器
    @Override protected SqlValidator getSqlValidator() {
      // 如果验证器为null
      if (sqlValidator == null) {
        // 创建SQL验证器
        sqlValidator =
            createSqlValidator(catalogReader, UnaryOperator.identity());
      }
      // 返回SQL验证器
      return sqlValidator;
    }

    // 创建准备好的EXPLAIN结果
    // @param resultType 结果类型
    // @param parameterRowType 参数行类型
    // @param root 关系根节点
    // @param format EXPLAIN格式
    // @param detailLevel EXPLAIN详细级别
    // @return 准备好的EXPLAIN结果
    @Override protected PreparedResult createPreparedExplanation(
        @Nullable RelDataType resultType,
        RelDataType parameterRowType,
        @Nullable RelRoot root,
        SqlExplainFormat format,
        SqlExplainLevel detailLevel) {
      // 创建并返回CalcitePreparedExplain实例
      return new CalcitePreparedExplain(resultType, parameterRowType, root,
          format, detailLevel);
    }

    // 实现关系树，生成可执行代码
    // @param root 关系根节点
    // @return 准备结果
    @Override protected PreparedResult implement(RelRoot root) {
      // 运行Hook.PLAN_BEFORE_IMPLEMENTATION，允许测试在实现前访问计划
      Hook.PLAN_BEFORE_IMPLEMENTATION.run(root);
      // 获取结果类型
      RelDataType resultType = root.rel.getRowType();
      // 判断是否为DML语句
      boolean isDml = root.kind.belongsTo(SqlKind.DML);
      // 可绑定对象
      final Bindable bindable;
      // 如果结果约定是可绑定约定
      if (resultConvention == BindableConvention.INSTANCE) {
        // 使用解释器创建可绑定对象
        bindable = Interpreters.bindable(root.rel);
      } else {
        // 否则使用可枚举约定，需要生成Java代码
        // 转换为可枚举关系
        EnumerableRel enumerable = (EnumerableRel) root.rel;
        // 如果引用不是平凡的（需要重新投影）
        if (!root.isRefTrivial()) {
          // 创建投影表达式列表
          final List<RexNode> projects = new ArrayList<>();
          // 获取Rex构建器
          final RexBuilder rexBuilder = enumerable.getCluster().getRexBuilder();
          // 遍历所有字段
          for (int field : Pair.left(root.fields)) {
            // 为每个字段创建输入引用
            projects.add(rexBuilder.makeInputRef(enumerable, field));
          }
          // 创建Rex程序
          RexProgram program =
              RexProgram.create(enumerable.getRowType(), projects, null,
                  root.validatedRowType, rexBuilder);
          // 创建可枚举计算节点
          enumerable = EnumerableCalc.create(enumerable, program);
        }

        try {
          // 设置线程本地的目录读取器
          CatalogReader.THREAD_LOCAL.set(catalogReader);
          // 获取SQL符合性
          final SqlConformance conformance = context.config().conformance();
          // 将符合性添加到内部参数
          internalParameters.put("_conformance", conformance);
          // 将可枚举关系转换为可绑定对象（生成Java代码）
          bindable =
              EnumerableInterpretable.toBindable(internalParameters,
                  context.spark(), enumerable,
                  requireNonNull(prefer, "EnumerableRel.Prefer prefer"));
        } finally {
          // 清除线程本地的目录读取器
          CatalogReader.THREAD_LOCAL.remove();
        }
      }

      // 如果有时间跟踪器，记录代码生成结束时间
      if (timingTracer != null) {
        timingTracer.traceTime("end codegen");
      }

      // 如果有时间跟踪器，记录编译结束时间
      if (timingTracer != null) {
        timingTracer.traceTime("end compilation");
      }

      // 创建并返回准备结果实现
      return new PreparedResultImpl(
          resultType, // 结果类型
          requireNonNull(parameterRowType, "parameterRowType"), // 参数行类型
          requireNonNull(fieldOrigins, "fieldOrigins"), // 字段原始信息
          root.collation.getFieldCollations().isEmpty() // 排序信息
              ? ImmutableList.of()
              : ImmutableList.of(root.collation),
          root.rel, // 关系节点
          mapTableModOp(isDml, root.kind), // 表修改操作
          isDml) { // 是否为DML
        // 获取代码：不支持
        @Override public String getCode() {
          throw new UnsupportedOperationException();
        }

        // 获取可绑定对象
        @Override public Bindable getBindable(Meta.CursorFactory cursorFactory) {
          return bindable;
        }

        // 获取元素类型
        @Override public Type getElementType() {
          return ((Typed) bindable).getElementType();
        }
      };
    }

    // 获取物化视图列表
    // @return 物化视图列表
    @Override protected List<Materialization> getMaterializations() {
      // 如果启用了物化视图，查询物化视图服务获取物化视图列表；否则返回空列表
      final List<Prepare.Materialization> materializations =
          context.config().materializationsEnabled()
              ? MaterializationService.instance().query(schema)
              : ImmutableList.of();
      // 遍历所有物化视图
      for (Prepare.Materialization materialization : materializations) {
        // 填充物化视图数据
        prepare.populateMaterializations(context, cluster, materialization);
      }
      // 返回物化视图列表
      return materializations;
    }

    // 获取格点（Lattice）列表
    // 格点是用于多维度分析的数据结构，类似于物化视图但更灵活
    // @return 格点列表
    @Override protected List<LatticeEntry> getLattices() {
      // 从schema中获取格点条目列表
      return Schemas.getLatticeEntries(schema);
    }
  }

  /** An {@code EXPLAIN} statement, prepared and ready to execute. */
// CalcitePreparedExplain类：EXPLAIN语句的准备结果，继承自Prepare.PreparedExplain
// EXPLAIN语句用于显示查询执行计划
private static class CalcitePreparedExplain extends Prepare.PreparedExplain {
    // 构造方法
    // @param resultType 结果类型
    // @param parameterRowType 参数行类型
    // @param root 关系根节点
    // @param format EXPLAIN格式
    // @param detailLevel EXPLAIN详细级别
    CalcitePreparedExplain(
        @Nullable RelDataType resultType,
        RelDataType parameterRowType,
        @Nullable RelRoot root,
        SqlExplainFormat format,
        SqlExplainLevel detailLevel) {
      // 调用父类构造方法
      super(resultType, parameterRowType, root, format, detailLevel);
    }

    // 获取可绑定对象
    // @param cursorFactory 游标工厂
    // @return 可绑定对象，返回EXPLAIN结果
    @Override public Bindable getBindable(final Meta.CursorFactory cursorFactory) {
      // 获取EXPLAIN结果字符串
      final String explanation = getCode();
      // 返回可绑定对象
      return dataContext -> {
        // 根据游标工厂样式返回不同格式的结果
        switch (cursorFactory.style) {
        // 如果是数组样式，返回字符串数组
        case ARRAY:
          return Linq4j.singletonEnumerable(new String[] {explanation});
        // 如果是对象样式，返回字符串
        case OBJECT:
        default:
          return Linq4j.singletonEnumerable(explanation);
        }
      };
    }
  }

  /** Translator from Java AST to {@link RexNode}. */
// ScalarTranslator接口：Java抽象语法树（AST）到RexNode的翻译器
// 用于将LINQ表达式转换为Calcite的关系表达式（RexNode）
interface ScalarTranslator {
    // 将块语句转换为RexNode
    RexNode toRex(BlockStatement statement);
    // 将块语句转换为RexNode列表
    List<RexNode> toRexList(BlockStatement statement);
    // 将表达式转换为RexNode
    RexNode toRex(Expression expression);
    // 绑定参数列表，返回新的翻译器
    ScalarTranslator bind(List<ParameterExpression> parameterList,
        List<RexNode> values);
  }

  /** Basic translator. */
  // EmptyScalarTranslator类：基本的标量翻译器实现
  static class EmptyScalarTranslator implements ScalarTranslator {
    // Rex构建器
    private final RexBuilder rexBuilder;

    // 构造方法
    // @param rexBuilder Rex构建器
    EmptyScalarTranslator(RexBuilder rexBuilder) {
      this.rexBuilder = rexBuilder;
    }

    // 创建空的标量翻译器
    // @param builder Rex构建器
    // @return 空的标量翻译器实例
    public static ScalarTranslator empty(RexBuilder builder) {
      return new EmptyScalarTranslator(builder);
    }

    // 将块语句转换为RexNode列表
    // @param statement 块语句
    // @return RexNode列表
    @Override public List<RexNode> toRexList(BlockStatement statement) {
      // 获取简单表达式列表
      final List<Expression> simpleList = simpleList(statement);
      // 创建RexNode列表
      final List<RexNode> list = new ArrayList<>();
      // 遍历所有表达式，转换为RexNode
      for (Expression expression1 : simpleList) {
        list.add(toRex(expression1));
      }
      // 返回RexNode列表
      return list;
    }

    // 将块语句转换为RexNode
    // @param statement 块语句
    // @return RexNode
    @Override public RexNode toRex(BlockStatement statement) {
      // 将块语句简化为单个表达式，然后转换为RexNode
      return toRex(Blocks.simple(statement));
    }

    // 将块语句简化为表达式列表
    // @param statement 块语句
    // @return 表达式列表
    private static List<Expression> simpleList(BlockStatement statement) {
      // 简化块语句为单个表达式
      Expression simple = Blocks.simple(statement);
      // 如果表达式是新建表达式
      if (simple instanceof NewExpression) {
        // 返回构造函数参数列表
        NewExpression newExpression = (NewExpression) simple;
        return newExpression.arguments;
      } else {
        // 否则返回包含单个表达式的列表
        return Collections.singletonList(simple);
      }
    }

    // 将表达式转换为RexNode
    // @param expression Java表达式
    // @return RexNode
    @Override public RexNode toRex(Expression expression) {
      // 根据表达式类型进行转换
      switch (expression.getNodeType()) {
      // 成员访问表达式
      case MemberAccess:
        // Case-sensitive name match because name was previously resolved.
        // 区分大小写的名称匹配，因为名称已经预先解析
        MemberExpression memberExpression = (MemberExpression) expression;
        // 获取伪字段
        PseudoField field = memberExpression.field;
        // 获取目标表达式
        Expression targetExpression =
            requireNonNull(memberExpression.expression,
                () -> "static field access is not implemented yet."
                    + " field.name=" + field.getName()
                    + ", field.declaringClass=" + field.getDeclaringClass());
        // 创建字段访问RexNode
        return rexBuilder.makeFieldAccess(
            toRex(targetExpression),
            field.getName(),
            true);
      // 等于表达式
      case Equal:
        return binary(expression, SqlStdOperatorTable.EQUALS);
      // 大于表达式
      case GreaterThan:
        return binary(expression, SqlStdOperatorTable.GREATER_THAN);
      // 大于等于表达式
      case GreaterThanOrEqual:
        return binary(expression, SqlStdOperatorTable.GREATER_THAN_OR_EQUAL);
      // 小于表达式
      case LessThan:
        return binary(expression, SqlStdOperatorTable.LESS_THAN);
      // 小于等于表达式
      case LessThanOrEqual:
        return binary(expression, SqlStdOperatorTable.LESS_THAN_OR_EQUAL);
      // 参数表达式
      case Parameter:
        return parameter((ParameterExpression) expression);
      // 方法调用表达式
      case Call:
        MethodCallExpression call = (MethodCallExpression) expression;
        // 获取对应的SQL运算符
        SqlOperator operator =
            RexToLixTranslator.JAVA_TO_SQL_METHOD_MAP.get(call.method);
        // 如果找到了对应的SQL运算符
        if (operator != null) {
          // 创建方法调用RexNode
          return rexBuilder.makeCall(
              type(call), // 返回类型
              operator, // SQL运算符
              toRex( // 转换参数列表
                  Expressions.<Expression>list()
                      .appendIfNotNull(call.targetExpression) // 目标表达式
                      .appendAll(call.expressions))); // 方法参数
        }
        // 如果找不到对应的SQL运算符，抛出异常
        throw new RuntimeException(
            "Could translate call to method " + call.method);
      // 常量表达式
      case Constant:
        final ConstantExpression constant =
            (ConstantExpression) expression;
        // 获取常量值
        Object value = constant.value;
        // 如果是数字
        if (value instanceof Number) {
          Number number = (Number) value;
          // 如果是浮点数
          if (value instanceof Double || value instanceof Float) {
            // 创建近似数字字面量
            return rexBuilder.makeApproxLiteral(
                BigDecimal.valueOf(number.doubleValue()));
          } else if (value instanceof BigDecimal) {
            // 如果是BigDecimal，创建精确字面量
            return rexBuilder.makeExactLiteral((BigDecimal) value);
          } else {
            // 否则创建长整型精确字面量
            return rexBuilder.makeExactLiteral(
                BigDecimal.valueOf(number.longValue()));
          }
        } else if (value instanceof Boolean) {
          // 如果是布尔值，创建布尔字面量
          return rexBuilder.makeLiteral((Boolean) value);
        } else {
          // 否则创建字符串字面量
          return rexBuilder.makeLiteral(constant.toString());
        }
      // 默认情况：不支持的表达式类型
      default:
        throw new UnsupportedOperationException(
            "unknown expression type " + expression.getNodeType() + " "
            + expression);
      }
    }

    // 处理二元表达式
    // @param expression 二元表达式
    // @param op SQL二元运算符
    // @return RexNode
    private RexNode binary(Expression expression, SqlBinaryOperator op) {
      // 转换为二元表达式
      BinaryExpression call = (BinaryExpression) expression;
      // 创建二元运算RexNode
      return rexBuilder.makeCall(type(call), op,
          toRex(ImmutableList.of(call.expression0, call.expression1)));
    }

    // 将表达式列表转换为RexNode列表
    // @param expressions Java表达式列表
    // @return RexNode列表
    private List<RexNode> toRex(List<Expression> expressions) {
      // 创建RexNode列表
      final List<RexNode> list = new ArrayList<>();
      // 遍历所有表达式，转换为RexNode
      for (Expression expression : expressions) {
        list.add(toRex(expression));
      }
      // 返回RexNode列表
      return list;
    }

    // 获取表达式的类型
    // @param expression Java表达式
    // @return 关系数据类型
    protected RelDataType type(Expression expression) {
      // 获取Java类型
      final Type type = expression.getType();
      // 转换为关系数据类型
      return ((JavaTypeFactory) rexBuilder.getTypeFactory()).createType(type);
    }

    // 绑定参数列表，返回新的翻译器
    // @param parameterList 参数表达式列表
    // @param values 参数值列表
    // @return 绑定了参数的翻译器
    @Override public ScalarTranslator bind(
        List<ParameterExpression> parameterList, List<RexNode> values) {
      // 创建并返回Lambda标量翻译器
      return new LambdaScalarTranslator(
          rexBuilder, parameterList, values);
    }

    // 处理参数表达式
    // @param param 参数表达式
    // @return RexNode
    // 注意：EmptyScalarTranslator不支持参数，抛出异常
    public RexNode parameter(ParameterExpression param) {
      // 抛出运行时异常：未知参数
      throw new RuntimeException("unknown parameter " + param);
    }
  }

  /** Translator that looks for parameters. */
// LambdaScalarTranslator类：支持参数绑定的标量翻译器
// 继承自EmptyScalarTranslator，重写parameter方法以支持参数绑定
private static class LambdaScalarTranslator extends EmptyScalarTranslator {
    // 参数表达式列表
    private final List<ParameterExpression> parameterList;
    // 参数值列表
    private final List<RexNode> values;

    // 构造方法
    // @param rexBuilder Rex构建器
    // @param parameterList 参数表达式列表
    // @param values 参数值列表
    LambdaScalarTranslator(
        RexBuilder rexBuilder,
        List<ParameterExpression> parameterList,
        List<RexNode> values) {
      // 调用父类构造方法
      super(rexBuilder);
      // 保存参数列表
      this.parameterList = parameterList;
      // 保存参数值列表
      this.values = values;
    }

    // 处理参数表达式
    // @param param 参数表达式
    // @return 对应参数值的RexNode
    @Override public RexNode parameter(ParameterExpression param) {
      // 查找参数在参数列表中的索引
      int i = parameterList.indexOf(param);
      // 如果找到了参数
      if (i >= 0) {
        // 返回对应的参数值
        return values.get(i);
      }
      // 如果找不到参数，抛出运行时异常
      throw new RuntimeException("unknown parameter " + param);
    }
  }
}
