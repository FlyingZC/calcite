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
package org.apache.calcite.prepare; // 包声明，PlannerImpl属于prepare包，负责查询准备阶段的实现

import org.apache.calcite.adapter.java.JavaTypeFactory; // Java类型工厂，用于创建Java类型系统
import org.apache.calcite.config.CalciteConnectionConfig; // Calcite连接配置接口
import org.apache.calcite.config.CalciteConnectionConfigImpl; // Calcite连接配置实现类
import org.apache.calcite.config.CalciteConnectionProperty; // Calcite连接属性枚举
import org.apache.calcite.config.CalciteSystemProperty; // Calcite系统属性配置
import org.apache.calcite.jdbc.CalciteSchema; // Calcite模式(schema)包装类
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // Java类型工厂实现类
import org.apache.calcite.plan.Context; // 规划器上下文接口
import org.apache.calcite.plan.ConventionTraitDef; // 约定(Convention)特质定义，定义关系代数的约定
import org.apache.calcite.plan.RelOptCluster; // 关系表达式优化簇，包含共享的优化资源
import org.apache.calcite.plan.RelOptCostFactory; // 优化成本工厂接口，用于创建成本对象
import org.apache.calcite.plan.RelOptPlanner; // 关系表达式优化器接口
import org.apache.calcite.plan.RelOptTable.ViewExpander; // 视图扩展器接口，用于展开视图定义
import org.apache.calcite.plan.RelOptUtil; // 关系表达式优化工具类
import org.apache.calcite.plan.RelTraitDef; // 关系特质定义接口
import org.apache.calcite.plan.RelTraitSet; // 关系特质集合，表示关系表达式的一组特性
import org.apache.calcite.plan.volcano.VolcanoPlanner; // 火山优化器，基于动态规划的优化器
import org.apache.calcite.rel.RelCollationTraitDef; // 排序(collation)特质定义
import org.apache.calcite.rel.RelNode; // 关系表达式节点接口，代表关系代数操作
import org.apache.calcite.rel.RelRoot; // 关系表达式根节点，包含最终的关系表达式和额外信息
import org.apache.calcite.rel.type.RelDataType; // 关系数据类型接口
import org.apache.calcite.rel.type.RelDataTypeSystem; // 关系数据类型系统接口
import org.apache.calcite.rex.RexBuilder; // 行表达式构建器，用于构建RexNode表达式
import org.apache.calcite.rex.RexExecutor; // 行表达式执行器，用于执行和优化RexNode表达式
import org.apache.calcite.runtime.Hook; // 钩子接口，用于在特定点插入自定义逻辑
import org.apache.calcite.schema.SchemaPlus; // SchemaPlus接口，提供增强的schema功能
import org.apache.calcite.sql.SqlNode; // SQL抽象语法树节点接口
import org.apache.calcite.sql.SqlOperatorTable; // SQL操作符表接口，包含所有SQL操作符
import org.apache.calcite.sql.parser.SqlParseException; // SQL解析异常
import org.apache.calcite.sql.parser.SqlParser; // SQL解析器
import org.apache.calcite.sql.util.SqlOperatorTables; // SQL操作符表工具类
import org.apache.calcite.sql.validate.SqlValidator; // SQL验证器接口，用于验证SQL语义
import org.apache.calcite.sql2rel.RelDecorrelator; // 关系表达式去相关器，用于消除子查询的相关性
import org.apache.calcite.sql2rel.SqlRexConvertletTable; // SQL到Rex转换规则表
import org.apache.calcite.sql2rel.SqlToRelConverter; // SQL到关系表达式转换器
import org.apache.calcite.tools.FrameworkConfig; // 框架配置接口
import org.apache.calcite.tools.Planner; // 规划器接口
import org.apache.calcite.tools.Program; // 优化程序接口，定义优化阶段的程序
import org.apache.calcite.tools.RelBuilder; // 关系表达式构建器，用于构建RelNode
import org.apache.calcite.tools.ValidationException; // 验证异常
import org.apache.calcite.util.Pair; // 不可变对(pair)工具类

import com.google.common.collect.ImmutableList; // Google Guava的不可变列表类

import org.checkerframework.checker.nullness.qual.EnsuresNonNull; // CheckerFramework注解，确保非空
import org.checkerframework.checker.nullness.qual.Nullable; // CheckerFramework注解，可为空

import java.io.Reader; // Java IO读取器
import java.util.List; // Java列表接口

import static java.util.Objects.requireNonNull; // Java Objects工具类的requireNonNull静态方法

/** Implementation of {@link org.apache.calcite.tools.Planner}. */ // Planner接口的实现类，负责SQL查询的解析、验证、转换和优化
public class PlannerImpl implements Planner, ViewExpander { // PlannerImpl类实现Planner和ViewExpander接口
  private final SqlOperatorTable operatorTable; // SQL操作符表，包含所有可用的SQL操作符和函数
  private final ImmutableList<Program> programs; // 优化程序列表，定义了不同优化阶段的程序序列
  private final @Nullable RelOptCostFactory costFactory; // 优化成本工厂，用于计算关系表达式执行成本，可为空
  private final Context context; // 规划器上下文，包含规划器运行时的配置信息
  private final CalciteConnectionConfig connectionConfig; // Calcite连接配置，包含连接级别的配置属性
  private final RelDataTypeSystem typeSystem; // 关系数据类型系统，定义数据类型的规则和行为

  /** Holds the trait definitions to be registered with planner. May be null. */ // 保存要注册到规划器的特质定义，可能为null
  private final @Nullable ImmutableList<RelTraitDef> traitDefs; // 关系特质定义列表，定义了关系表达式的特性，可为空

  private final SqlParser.Config parserConfig; // SQL解析器配置，定义解析器的行为
  private final SqlValidator.Config sqlValidatorConfig; // SQL验证器配置，定义验证器的行为
  private final SqlToRelConverter.Config sqlToRelConverterConfig; // SQL到关系表达式转换器配置
  private final SqlRexConvertletTable convertletTable; // SQL到Rex转换规则表，定义SQL表达式到RexNode的转换规则

  private State state; // 规划器当前状态，表示查询准备流程处于哪个阶段

  // set in STATE_1_RESET // 在STATE_1_RESET状态时设置
  @SuppressWarnings("unused") // 抑制未使用警告
  private boolean open; // 规划器是否打开的标志，true表示规划器已打开可以使用

  // set in STATE_2_READY // 在STATE_2_READY状态时设置
  private final @Nullable SchemaPlus defaultSchema; // 默认schema，用于解析未限定的表名，可为空
  private @Nullable JavaTypeFactory typeFactory; // Java类型工厂，用于创建Java类型，可为空
  private @Nullable RelOptPlanner planner; // 关系表达式优化器，负责优化RelNode，可为空
  private final @Nullable RexExecutor executor; // 行表达式执行器，用于执行RexNode表达式，可为空

  // set in STATE_4_VALIDATE // 在STATE_4_VALIDATE状态时设置
  private @Nullable SqlValidator validator; // SQL验证器，用于验证SQL语义和类型，可为空
  private @Nullable SqlNode validatedSqlNode; // 验证后的SQL节点，代表语义正确的SQL AST，可为空

  /** Creates a planner. Not a public API; call // 创建规划器。不是公共API，请调用Frameworks.getPlanner()方法
   * {@link org.apache.calcite.tools.Frameworks#getPlanner} instead. */ // 代替直接调用此构造函数
  @SuppressWarnings("method.invocation.invalid") // 抑制方法调用无效的警告
  public PlannerImpl(FrameworkConfig config) { // 构造函数，接收框架配置对象
    this.costFactory = config.getCostFactory(); // 从配置中获取成本工厂
    this.defaultSchema = config.getDefaultSchema(); // 从配置中获取默认schema
    this.operatorTable = config.getOperatorTable(); // 从配置中获取操作符表
    this.programs = config.getPrograms(); // 从配置中获取优化程序列表
    this.parserConfig = config.getParserConfig(); // 从配置中获取解析器配置
    this.sqlValidatorConfig = config.getSqlValidatorConfig(); // 从配置中获取验证器配置
    this.sqlToRelConverterConfig = config.getSqlToRelConverterConfig(); // 从配置中获取转换器配置
    this.state = State.STATE_0_CLOSED; // 初始化状态为已关闭状态
    this.traitDefs = config.getTraitDefs(); // 从配置中获取特质定义列表
    this.convertletTable = config.getConvertletTable(); // 从配置中获取转换规则表
    this.executor = config.getExecutor(); // 从配置中获取表达式执行器
    this.context = config.getContext(); // 从配置中获取规划器上下文
    this.connectionConfig = connConfig(context, parserConfig); // 构建连接配置
    this.typeSystem = config.getTypeSystem(); // 从配置中获取类型系统
    reset(); // 调用reset方法初始化规划器
  }

  /** Gets a user-defined config and appends default connection values. */ // 获取用户定义的配置并追加默认连接值
  private static CalciteConnectionConfig connConfig(Context context, // 静态方法，根据上下文和解析器配置创建连接配置
      SqlParser.Config parserConfig) { // 参数：规划器上下文和解析器配置
    CalciteConnectionConfigImpl config = // 从上下文中获取Calcite连接配置，如果没有则使用默认配置
        context.maybeUnwrap(CalciteConnectionConfigImpl.class) // 尝试从上下文中解包CalciteConnectionConfigImpl
            .orElse(CalciteConnectionConfig.DEFAULT); // 如果没有找到则使用默认配置
    if (!config.isSet(CalciteConnectionProperty.CASE_SENSITIVE)) { // 如果大小写敏感属性未设置
      config = // 设置大小写敏感属性为解析器配置的值
          config.set(CalciteConnectionProperty.CASE_SENSITIVE, // 设置CASE_SENSITIVE属性
              String.valueOf(parserConfig.caseSensitive())); // 使用解析器的大小写敏感设置
    }
    if (!config.isSet(CalciteConnectionProperty.CONFORMANCE)) { // 如果符合性(conformance)属性未设置
      config = // 设置符合性属性为解析器配置的值
          config.set(CalciteConnectionProperty.CONFORMANCE, // 设置CONFORMANCE属性
              String.valueOf(parserConfig.conformance())); // 使用解析器的符合性设置
    }
    return config; // 返回构建好的连接配置
  }

  /** Makes sure that the state is at least the given state. */ // 确保规划器状态至少达到指定状态
  private void ensure(State state) { // 私有方法，用于状态转换检查
    if (state == this.state) { // 如果目标状态和当前状态相同
      return; // 直接返回，无需转换
    }
    if (state.ordinal() < this.state.ordinal()) { // 如果目标状态的序号小于当前状态（不能倒退）
      throw new IllegalArgumentException("cannot move to " + state + " from " // 抛出非法参数异常，说明不能从当前状态转换到目标状态
          + this.state); // 包含当前状态和目标状态信息
    }
    state.from(this); // 调用目标状态的from方法，从当前状态转换到目标状态
  }

  @Override public RelTraitSet getEmptyTraitSet() { // 覆盖接口方法，获取空的特质集合
    return requireNonNull(planner, "planner").emptyTraitSet(); // 返回优化器的空特质集合
  }

  @Override public void close() { // 覆盖接口方法，关闭规划器
    open = false; // 设置打开标志为false
    typeFactory = null; // 清空类型工厂
    state = State.STATE_0_CLOSED; // 将状态设置为已关闭状态
  }

  @Override public void reset() { // 覆盖接口方法，重置规划器到初始状态
    ensure(State.STATE_0_CLOSED); // 确保处于已关闭状态
    open = true; // 设置打开标志为true
    state = State.STATE_1_RESET; // 将状态设置为重置状态
  }

  private void ready() { // 私有方法，将规划器准备就绪
    switch (state) { // 根据当前状态进行不同处理
    case STATE_0_CLOSED: // 如果是已关闭状态
      reset(); // 调用reset方法重置规划器
      break; // 跳出switch
    default: // 其他状态
      break; // 直接跳出switch
    }
    ensure(State.STATE_1_RESET); // 确保处于重置状态

    typeFactory = new JavaTypeFactoryImpl(typeSystem); // 创建Java类型工厂实例
    RelOptPlanner planner = this.planner = new VolcanoPlanner(costFactory, context); // 创建火山优化器实例
    RelOptUtil.registerDefaultRules(planner, // 向优化器注册默认规则
        connectionConfig.materializationsEnabled(), // 是否启用物化视图
        Hook.ENABLE_BINDABLE.get(false)); // 是否启用可绑定规则
    planner.setExecutor(executor); // 设置优化器的表达式执行器

    state = State.STATE_2_READY; // 将状态设置为就绪状态

    // If user specify own traitDef, instead of default default trait, // 如果用户指定了自己的特质定义，而不是使用默认特质
    // register the trait def specified in traitDefs. // 则注册traitDefs中指定的特质定义
    if (this.traitDefs == null) { // 如果特质定义列表为null
      planner.addRelTraitDef(ConventionTraitDef.INSTANCE); // 添加默认的约定特质定义
      if (CalciteSystemProperty.ENABLE_COLLATION_TRAIT.value()) { // 如果启用了排序特质
        planner.addRelTraitDef(RelCollationTraitDef.INSTANCE); // 添加排序特质定义
      }
    } else { // 如果特质定义列表不为null
      for (RelTraitDef def : this.traitDefs) { // 遍历特质定义列表
        planner.addRelTraitDef(def); // 将每个特质定义添加到优化器
      }
    }
  }

  @Override public SqlNode parse(final Reader reader) throws SqlParseException { // 覆盖接口方法，解析SQL语句
    switch (state) { // 根据当前状态进行不同处理
    case STATE_0_CLOSED: // 如果是已关闭状态
    case STATE_1_RESET: // 或者是重置状态
      ready(); // 调用ready方法准备规划器
      break; // 跳出switch
    default: // 其他状态
      break; // 直接跳出switch
    }
    ensure(State.STATE_2_READY); // 确保处于就绪状态
    SqlParser parser = SqlParser.create(reader, parserConfig); // 创建SQL解析器
    SqlNode sqlNode = parser.parseStmt(); // 解析SQL语句，得到SQL AST节点
    state = State.STATE_3_PARSED; // 将状态设置为已解析状态
    return sqlNode; // 返回解析后的SQL节点
  }

  @EnsuresNonNull("validator") // 注解确保验证器不为null
  @Override public SqlNode validate(SqlNode sqlNode) throws ValidationException { // 覆盖接口方法，验证SQL节点
    ensure(State.STATE_3_PARSED); // 确保处于已解析状态
    this.validator = createSqlValidator(createCatalogReader()); // 创建SQL验证器
    try { // 尝试验证
      validatedSqlNode = validator.validate(sqlNode); // 验证SQL节点，得到验证后的节点
    } catch (RuntimeException e) { // 捕获运行时异常
      throw new ValidationException(e); // 包装为验证异常并抛出
    }
    state = State.STATE_4_VALIDATED; // 将状态设置为已验证状态
    return validatedSqlNode; // 返回验证后的SQL节点
  }

  @Override public Pair<SqlNode, RelDataType> validateAndGetType(SqlNode sqlNode) // 覆盖接口方法，验证SQL节点并获取其类型
      throws ValidationException { // 可能抛出验证异常
    final SqlNode validatedNode = this.validate(sqlNode); // 验证SQL节点
    final RelDataType type = // 获取验证后节点的类型
        this.validator.getValidatedNodeType(validatedNode); // 调用验证器获取节点类型
    return Pair.of(validatedNode, type); // 返回验证后的节点和类型的对(pair)
  }

  @Override public RelDataType getParameterRowType() { // 覆盖接口方法，获取参数行类型
    if (state.ordinal() < State.STATE_4_VALIDATED.ordinal()) { // 如果状态未达到已验证状态
      throw new RuntimeException("Need to call #validate() first"); // 抛出运行时异常，提示需要先调用validate方法
    }

    return requireNonNull(validator, "validator") // 返回验证器的参数行类型
        .getParameterRowType(requireNonNull(validatedSqlNode, "validatedSqlNode")); // 获取验证后SQL节点的参数行类型
  }

  @SuppressWarnings("deprecation") // 抑制过时警告
  @Override public final RelNode convert(SqlNode sql) { // 覆盖接口方法，转换SQL节点为关系表达式（已过时）
    return rel(sql).rel; // 调用rel方法获取RelRoot，返回其rel字段
  }

  @Override public RelRoot rel(SqlNode sql) { // 覆盖接口方法，将SQL节点转换为关系表达式根节点
    ensure(State.STATE_4_VALIDATED); // 确保处于已验证状态
    SqlNode validatedSqlNode = // 获取验证后的SQL节点
        requireNonNull(this.validatedSqlNode, // 检查验证后的SQL节点不为null
            "validatedSqlNode is null. Need to call #validate() first"); // null时提示需要先调用validate方法
    final RexBuilder rexBuilder = createRexBuilder(); // 创建行表达式构建器
    final RelOptCluster cluster = // 创建关系表达式优化簇
        RelOptCluster.create(requireNonNull(planner, "planner"), // 使用优化器创建簇
            rexBuilder); // 使用rexBuilder创建簇
    final SqlToRelConverter.Config config = // 获取SQL到关系表达式转换器配置
        sqlToRelConverterConfig.withTrimUnusedFields(false); // 设置不修剪未使用的字段
    final SqlToRelConverter sqlToRelConverter = // 创建SQL到关系表达式转换器
        new SqlToRelConverter(this, validator, // 传入this作为ViewExpander，验证器
            createCatalogReader(), cluster, convertletTable, config); // 传入catalogReader、簇、转换规则表和配置
    RelRoot root = // 转换SQL查询为关系表达式根节点
        sqlToRelConverter.convertQuery(validatedSqlNode, false, true); // 参数：SQL节点、是否需要验证、是否需要顶层排序
    root = root.withRel(sqlToRelConverter.flattenTypes(root.rel, true)); // 展平关系表达式中的类型
    final RelBuilder relBuilder = // 创建关系表达式构建器
        config.getRelBuilderFactory().create(cluster, null); // 使用配置的构建器工厂创建
    root = // 去除关系表达式中的相关性
        root.withRel(RelDecorrelator.decorrelateQuery(root.rel, relBuilder)); // 使用RelDecorrelator去相关
    state = State.STATE_5_CONVERTED; // 将状态设置为已转换状态
    return root; // 返回关系表达式根节点
  }

  // CHECKSTYLE: IGNORE 2 // CheckStyle忽略检查
  /** @deprecated Now {@link PlannerImpl} implements {@link ViewExpander} // 已过时：现在PlannerImpl直接实现了ViewExpander接口
   * directly. */ // 不再需要这个内部类
  @Deprecated // to be removed before 2.0 // 标记为过时，将在2.0版本前移除
  public class ViewExpanderImpl implements ViewExpander { // 内部类，实现ViewExpander接口（已过时）
    ViewExpanderImpl() { // 构造函数
    }

    @Override public RelRoot expandView(RelDataType rowType, String queryString, // 覆盖接口方法，展开视图
        List<String> schemaPath, @Nullable List<String> viewPath) { // 参数：行类型、查询字符串、schema路径、视图路径
      return PlannerImpl.this.expandView(rowType, queryString, schemaPath, // 委托给外部类的expandView方法
          viewPath); // 传递所有参数
    }
  }

  @Override public RelRoot expandView(RelDataType rowType, String queryString, // 覆盖接口方法，展开视图定义
      List<String> schemaPath, @Nullable List<String> viewPath) { // 参数：行类型、查询字符串、schema路径、视图路径
    RelOptPlanner planner = this.planner; // 获取优化器
    if (planner == null) { // 如果优化器为null
      ready(); // 调用ready方法准备规划器
      planner = requireNonNull(this.planner, "planner"); // 再次获取优化器并确保不为null
    }
    SqlParser parser = SqlParser.create(queryString, parserConfig); // 创建SQL解析器
    SqlNode sqlNode; // 声明SQL节点
    try { // 尝试解析
      sqlNode = parser.parseQuery(); // 解析查询语句
    } catch (SqlParseException e) { // 捕获解析异常
      throw new RuntimeException("parse failed", e); // 包装为运行时异常并抛出
    }

    final CalciteCatalogReader catalogReader = // 创建Calcite目录读取器
        createCatalogReader().withSchemaPath(schemaPath); // 设置schema路径
    final SqlValidator validator = createSqlValidator(catalogReader); // 创建SQL验证器

    final RexBuilder rexBuilder = createRexBuilder(); // 创建行表达式构建器
    final RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder); // 创建关系表达式优化簇
    final SqlToRelConverter.Config config = // 获取SQL到关系表达式转换器配置
        sqlToRelConverterConfig.withTrimUnusedFields(false); // 设置不修剪未使用的字段
    final SqlToRelConverter sqlToRelConverter = // 创建SQL到关系表达式转换器
        new SqlToRelConverter(this, validator, // 传入this作为ViewExpander，验证器
            catalogReader, cluster, convertletTable, config); // 传入catalogReader、簇、转换规则表和配置

    final RelRoot root = // 转换SQL查询为关系表达式根节点
        sqlToRelConverter.convertQuery(sqlNode, true, false); // 参数：SQL节点、是否需要验证、是否需要顶层排序
    final RelRoot root2 = // 展平关系表达式中的类型
        root.withRel(sqlToRelConverter.flattenTypes(root.rel, true)); // 展平类型
    final RelBuilder relBuilder = // 创建关系表达式构建器
        config.getRelBuilderFactory().create(cluster, null); // 使用配置的构建器工厂创建
    return root2.withRel( // 去除关系表达式中的相关性并返回
        RelDecorrelator.decorrelateQuery(root.rel, relBuilder)); // 使用RelDecorrelator去相关
  }

  // CalciteCatalogReader is stateless; no need to store one // CalciteCatalogReader是无状态的，不需要存储实例
  private CalciteCatalogReader createCatalogReader() { // 私有方法，创建Calcite目录读取器
    SchemaPlus defaultSchema = requireNonNull(this.defaultSchema, "defaultSchema"); // 获取默认schema并确保不为null
    final SchemaPlus rootSchema = rootSchema(defaultSchema); // 获取根schema

    return new CalciteCatalogReader( // 创建并返回CalciteCatalogReader实例
        CalciteSchema.from(rootSchema), // 从根schema创建CalciteSchema
        CalciteSchema.from(defaultSchema).path(null), // 从默认schema创建路径
        getTypeFactory(), connectionConfig); // 传入类型工厂和连接配置
  }

  private SqlValidator createSqlValidator(CalciteCatalogReader catalogReader) { // 私有方法，创建SQL验证器
    final SqlOperatorTable opTab = // 创建操作符表，将用户操作符表和目录操作符表链接起来
        SqlOperatorTables.chain(operatorTable, catalogReader); // 链接两个操作符表
    return new CalciteSqlValidator(opTab, // 创建并返回CalciteSQL验证器
        catalogReader, // 传入目录读取器
        getTypeFactory(), sqlValidatorConfig // 传入类型工厂和验证器配置
            .withDefaultNullCollation(connectionConfig.defaultNullCollation()) // 设置默认null排序规则
            .withLenientOperatorLookup(connectionConfig.lenientOperatorLookup()) // 设置宽松的操作符查找
            .withConformance(connectionConfig.conformance()) // 设置符合性规则
            .withIdentifierExpansion(true)); // 启用标识符扩展
  }

  private static SchemaPlus rootSchema(SchemaPlus schema) { // 私有静态方法，获取根schema
    for (;;) { // 无限循环
      SchemaPlus parentSchema = schema.getParentSchema(); // 获取父schema
      if (parentSchema == null) { // 如果父schema为null（说明已经到达根schema）
        return schema; // 返回当前schema作为根schema
      }
      schema = parentSchema; // 继续向上查找父schema
    }
  }

  // RexBuilder is stateless; no need to store one // RexBuilder是无状态的，不需要存储实例
  private RexBuilder createRexBuilder() { // 私有方法，创建行表达式构建器
    return new RexBuilder(getTypeFactory()); // 创建并返回RexBuilder实例
  }

  @Override public JavaTypeFactory getTypeFactory() { // 覆盖接口方法，获取类型工厂
    return requireNonNull(typeFactory, "typeFactory"); // 返回类型工厂并确保不为null
  }

  @SuppressWarnings("deprecation") // 抑制过时警告
  @Override public RelNode transform(int ruleSetIndex, RelTraitSet requiredOutputTraits, // 覆盖接口方法，转换关系表达式
      RelNode rel) { // 参数：规则集索引、所需的输出特质、关系表达式节点
    ensure(State.STATE_5_CONVERTED); // 确保处于已转换状态
    rel.getCluster().setMetadataProvider( // 设置关系表达式簇的元数据提供者
        new org.apache.calcite.rel.metadata.CachingRelMetadataProvider( // 创建缓存元数据提供者
            requireNonNull(rel.getCluster().getMetadataProvider(), "metadataProvider"), // 包装原有的元数据提供者
            rel.getCluster().getPlanner())); // 传入优化器
    Program program = programs.get(ruleSetIndex); // 获取指定索引的优化程序
    return program.run(requireNonNull(planner, "planner"), // 运行优化程序并返回优化后的关系表达式
        rel, requiredOutputTraits, ImmutableList.of(), // 传入关系表达式、所需特质、空列表
        ImmutableList.of()); // 传入空列表
  }

  /** Stage of a statement in the query-preparation lifecycle. */ // 查询准备生命周期中语句的阶段
  private enum State { // 私有枚举，定义规划器的状态
    STATE_0_CLOSED { // 状态0：已关闭
      @Override void from(PlannerImpl planner) { // 覆盖from方法，定义如何转换到此状态
        planner.close(); // 调用planner的close方法
      }
    },
    STATE_1_RESET { // 状态1：已重置
      @Override void from(PlannerImpl planner) { // 覆盖from方法，定义如何转换到此状态
        planner.ensure(STATE_0_CLOSED); // 确保先达到已关闭状态
        planner.reset(); // 调用planner的reset方法
      }
    },
    STATE_2_READY { // 状态2：就绪
      @Override void from(PlannerImpl planner) { // 覆盖from方法，定义如何转换到此状态
        STATE_1_RESET.from(planner); // 先从重置状态转换
        planner.ready(); // 调用planner的ready方法
      }
    },
    STATE_3_PARSED, // 状态3：已解析（无需特殊转换逻辑）
    STATE_4_VALIDATED, // 状态4：已验证（无需特殊转换逻辑）
    STATE_5_CONVERTED; // 状态5：已转换（无需特殊转换逻辑）

    /** Moves planner's state to this state. This must be a higher state. */ // 将规划器状态转换到此状态，必须是更高的状态
    void from(PlannerImpl planner) { // from方法，定义状态转换逻辑
      throw new IllegalArgumentException("cannot move from " + planner.state // 默认抛出异常，说明不能从当前状态转换到此状态
          + " to " + this); // 包含当前状态和目标状态信息
    }
  }
}