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
 */ // Apache许可证声明，定义代码的使用许可和限制
package org.apache.calcite.piglet; // Piglet包，包含Pig与Calcite集成的相关类

import org.apache.calcite.adapter.enumerable.EnumerableConvention; // 可枚举约定，定义可枚举物理实现的特征
import org.apache.calcite.adapter.enumerable.EnumerableRules; // 可枚举规则集，包含将逻辑节点转换为可枚举物理实现的规则
import org.apache.calcite.plan.RelOptPlanner; // 关系优化规划器，负责应用优化规则
import org.apache.calcite.plan.RelOptRule; // 优化规则基类，定义如何转换关系节点
import org.apache.calcite.rel.RelCollation; // 排序规则，定义字段的排序顺序
import org.apache.calcite.rel.RelCollations; // 排序规则工具类，提供创建和操作排序规则的方法
import org.apache.calcite.rel.RelNode; // 关系节点基类，代表查询计划中的一个操作符
import org.apache.calcite.rel.core.Sort; // 排序操作符，实现ORDER BY功能
import org.apache.calcite.rel.logical.ToLogicalConverter; // 物理到逻辑转换器，将物理计划转换为逻辑计划
import org.apache.calcite.rel.rel2sql.RelToSqlConverter; // 关系代数到SQL转换器，将RelNode转换为SqlNode
import org.apache.calcite.rel.rules.CoreRules; // 核心规则集，包含常用的优化规则
import org.apache.calcite.sql.SqlDialect; // SQL方言，定义不同数据库系统的SQL语法差异
import org.apache.calcite.sql.SqlNode; // SQL抽象语法树节点基类
import org.apache.calcite.sql.SqlWriter; // SQL写入器，负责将SqlNode转换为字符串
import org.apache.calcite.sql.SqlWriterConfig; // SQL写入器配置，定义输出格式和方言
import org.apache.calcite.sql.pretty.SqlPrettyWriter; // SQL美化写入器，生成格式化的SQL语句
import org.apache.calcite.tools.FrameworkConfig; // Calcite框架配置，包含优化器、目录等配置
import org.apache.calcite.tools.Program; // 优化程序，定义如何应用规则优化查询计划
import org.apache.calcite.tools.Programs; // 优化程序工具类，提供常用的优化程序
import org.apache.calcite.tools.RuleSets; // 规则集工具类，用于管理优化规则

import org.apache.pig.ExecType; // Pig执行类型，定义LOCAL或MAPREDUCE等执行模式
import org.apache.pig.PigServer; // Pig服务器类，提供Pig脚本执行的核心功能
import org.apache.pig.impl.logicalLayer.FrontendException; // Pig前端异常，表示解析或验证错误
import org.apache.pig.impl.util.PropertiesUtil; // Pig属性工具类，用于加载和管理配置
import org.apache.pig.newplan.logical.relational.LogicalPlan; // Pig逻辑计划，包含Pig操作符的有向无环图

import com.google.common.collect.ImmutableList; // Google Guava不可变列表，提供线程安全的不可变集合

import java.io.IOException; // IO异常基类，表示输入输出操作错误
import java.io.InputStream; // 输入流，用于读取文件或网络数据
import java.util.ArrayList; // 动态数组列表，Java集合框架的基础类
import java.util.List; // 列表接口，定义有序集合的行为
import java.util.Map; // 映射接口，定义键值对集合的行为
import java.util.Properties; // 属性类，用于管理配置参数

/**
 * Extension from PigServer to convert Pig scripts into logical relational
 * algebra plans and SQL statements.
 * PigServer的扩展类，用于将Pig脚本转换为逻辑关系代数计划和SQL语句
 * 该类作为Apache Pig和Apache Calcite之间的桥梁，实现了Pig Latin语言到SQL的转换
 * 主要功能包括：解析Pig脚本、生成关系代数计划、优化计划、转换为SQL语句
 */
public class PigConverter extends PigServer {
  // Basic transformation and implementation rules to optimize for Pig-translated logical plans
  // 基本的转换和实现规则集，用于优化从Pig转换而来的逻辑计划
  // 这些规则专门针对Pig转换后的关系代数计划进行优化，确保计划的正确性和高效性
  private static final List<RelOptRule> PIG_RULES =
      ImmutableList.of(
          // 将Project转换为LogicalProject和Window，支持窗口函数
          CoreRules.PROJECT_TO_LOGICAL_PROJECT_AND_WINDOW,
          // Pig到SQL的聚合规则，专门处理Pig的GROUP BY和聚合操作
          PigToSqlAggregateRule.INSTANCE,
          // Values规则，将常量值转换为可枚举的Values节点
          EnumerableRules.ENUMERABLE_VALUES_RULE,
          // Join规则，将逻辑Join转换为可枚举的Join实现
          EnumerableRules.ENUMERABLE_JOIN_RULE,
          // Correlate规则，处理相关子查询
          EnumerableRules.ENUMERABLE_CORRELATE_RULE,
          // Project规则，将逻辑Project转换为可枚举的Project实现
          EnumerableRules.ENUMERABLE_PROJECT_RULE,
          // Filter规则，将逻辑Filter转换为可枚举的Filter实现
          EnumerableRules.ENUMERABLE_FILTER_RULE,
          // Aggregate规则，将逻辑Aggregate转换为可枚举的Aggregate实现
          EnumerableRules.ENUMERABLE_AGGREGATE_RULE,
          // Sort规则，将逻辑Sort转换为可枚举的Sort实现
          EnumerableRules.ENUMERABLE_SORT_RULE,
          // Limit规则，将逻辑Limit转换为可枚举的Limit实现
          EnumerableRules.ENUMERABLE_LIMIT_RULE,
          // Collect规则，处理集合收集操作
          EnumerableRules.ENUMERABLE_COLLECT_RULE,
          // Uncollect规则，处理集合展开操作
          EnumerableRules.ENUMERABLE_UNCOLLECT_RULE,
          // Union规则，将逻辑Union转换为可枚举的Union实现
          EnumerableRules.ENUMERABLE_UNION_RULE,
          // Window规则，处理窗口函数
          EnumerableRules.ENUMERABLE_WINDOW_RULE,
          // TableScan规则，将逻辑TableScan转换为可枚举的TableScan实现
          EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
          // 转换为解释器规则，用于解释执行
          EnumerableRules.TO_INTERPRETER);

  // 转换规则集，用于对关系代数计划进行进一步的优化转换
  // 这些规则主要用于计划重写，通过合并、转置等操作来优化执行计划
  private static final List<RelOptRule> TRANSFORM_RULES =
      ImmutableList.of(
          // Project和Window转置规则，优化窗口函数和Project的顺序
          CoreRules.PROJECT_WINDOW_TRANSPOSE,
          // Filter合并规则，将连续的Filter操作合并为一个
          CoreRules.FILTER_MERGE,
          // Project合并规则，将连续的Project操作合并为一个
          CoreRules.PROJECT_MERGE,
          // Filter和Project转置规则，优化Filter和Project的执行顺序
          CoreRules.FILTER_PROJECT_TRANSPOSE,
          // Values规则，将常量值转换为可枚举的Values节点
          EnumerableRules.ENUMERABLE_VALUES_RULE,
          // Join规则，将逻辑Join转换为可枚举的Join实现
          EnumerableRules.ENUMERABLE_JOIN_RULE,
          // Correlate规则，处理相关子查询
          EnumerableRules.ENUMERABLE_CORRELATE_RULE,
          // Project规则，将逻辑Project转换为可枚举的Project实现
          EnumerableRules.ENUMERABLE_PROJECT_RULE,
          // Filter规则，将逻辑Filter转换为可枚举的Filter实现
          EnumerableRules.ENUMERABLE_FILTER_RULE,
          // Aggregate规则，将逻辑Aggregate转换为可枚举的Aggregate实现
          EnumerableRules.ENUMERABLE_AGGREGATE_RULE,
          // Sort规则，将逻辑Sort转换为可枚举的Sort实现
          EnumerableRules.ENUMERABLE_SORT_RULE,
          // Limit规则，将逻辑Limit转换为可枚举的Limit实现
          EnumerableRules.ENUMERABLE_LIMIT_RULE,
          // Collect规则，处理集合收集操作
          EnumerableRules.ENUMERABLE_COLLECT_RULE,
          // Uncollect规则，处理集合展开操作
          EnumerableRules.ENUMERABLE_UNCOLLECT_RULE,
          // Union规则，将逻辑Union转换为可枚举的Union实现
          EnumerableRules.ENUMERABLE_UNION_RULE,
          // Window规则，处理窗口函数
          EnumerableRules.ENUMERABLE_WINDOW_RULE,
          // TableScan规则，将逻辑TableScan转换为可枚举的TableScan实现
          EnumerableRules.ENUMERABLE_TABLE_SCAN_RULE,
          // 转换为解释器规则，用于解释执行
          EnumerableRules.TO_INTERPRETER);

  // PigRelBuilder实例，用于构建关系代数计划
  // PigRelBuilder是Calcite RelBuilder的Pig扩展，提供了构建Pig特定操作符的能力
  private final PigRelBuilder builder;

  /** Private constructor. */
  // 私有构造方法，防止外部直接实例化，强制使用工厂方法创建
  private PigConverter(FrameworkConfig config, ExecType execType,
      Properties properties) throws Exception {
    // 调用父类PigServer的构造方法，初始化Pig服务器
    // config: Calcite框架配置，包含优化器规则、目录等信息
    // execType: 执行类型（如LOCAL或MAPREDUCE）
    // properties: Pig配置属性
    super(execType, properties); // 初始化PigServer，设置执行类型和属性
    // 创建PigRelBuilder实例，用于构建关系代数计划
    this.builder = PigRelBuilder.create(config); // 使用配置创建Pig关系代数构建器
  }

  /** Creates a PigConverter using the given property settings. */
  // 使用给定的属性设置创建PigConverter实例
  // config: Calcite框架配置
  // properties: Pig配置属性
  // 返回: 新创建的PigConverter实例
  public static PigConverter create(FrameworkConfig config,
      Properties properties) throws Exception {
    // 创建PigConverter实例，使用LOCAL执行类型（本地模式执行）
    // LOCAL模式适合开发和测试，不需要Hadoop集群
    return new PigConverter(config, ExecType.LOCAL, properties); // 使用本地执行类型创建转换器
  }

  /** Creates a PigConverter using default property settings. */
  // 使用默认属性设置创建PigConverter实例
  // config: Calcite框架配置
  // 返回: 新创建的PigConverter实例
  public static PigConverter create(FrameworkConfig config) throws Exception {
    // 使用Pig的默认属性配置创建PigConverter
    // PropertiesUtil.loadDefaultProperties()加载Pig的默认属性文件
    return create(config, PropertiesUtil.loadDefaultProperties()); // 加载默认属性并创建转换器
  }

  // 获取PigRelBuilder实例
  // PigRelBuilder用于构建关系代数计划，可以访问和操作计划结构
  public PigRelBuilder getBuilder() {
    return builder; // 返回内部维护的PigRelBuilder实例
  }

  /**
   * Parses a Pig script and converts it into relational algebra plans,
   * optimizing the result.
   * 解析Pig脚本并将其转换为关系代数计划，对结果进行优化
   *
   * <p>Equivalent to {@code pigQuery2Rel(pigQuery, true, true, true)}.
   * 等同于调用 pigQuery2Rel(pigQuery, true, true, true)，即启用所有优化选项
   *
   * @param pigQuery Pig script // Pig脚本字符串，包含Pig Latin语句
   * // Pig脚本示例: "A = LOAD 'data.txt' AS (x:int, y:int); B = FILTER A BY x > 10; STORE B INTO 'output';"
   *
   * @return A list of root nodes of the translated relational plans. Each of
   * these root corresponds to a sink operator (normally a STORE command) in the
   * Pig plan // 返回转换后的关系代数计划的根节点列表。每个根节点对应Pig计划中的一个sink操作符（通常是STORE命令）
   * // 如果Pig脚本中有多个STORE命令，则返回多个RelNode
   * // RelNode是Calcite中关系代数操作符的基类，代表查询计划中的一个节点
   *
   * @throws IOException Exception during parsing or translating Pig // 解析或转换Pig时的异常
   * // 可能的异常: 语法错误、类型不匹配、文件不存在等
   */
  public List<RelNode> pigQuery2Rel(String pigQuery) throws IOException {
    // 调用完整版本的pigQuery2Rel方法，启用所有优化选项
    // planRewrite=true: 启用计划重写优化
    // validate=true: 验证Pig逻辑计划
    // usePigRules=true: 使用Pig特定的优化规则
    return pigQuery2Rel(pigQuery, true, true, true); // 使用默认优化选项转换Pig查询
  }

  /**
   * Parses a Pig script and converts it into relational algebra plans.
   * 解析Pig脚本并将其转换为关系代数计划，提供详细的优化控制选项
   *
   * @param pigQuery Pig script // Pig脚本字符串，包含Pig Latin语句
   * @param planRewrite Whether to rewrite the translated plan // 是否重写转换后的计划
   * // true: 应用TRANSFORM_RULES规则集进行计划优化，包括合并Filter、合并Project等
   * // false: 不进行计划重写，保持原始转换结果
   * @param validate Whether to validate the Pig logical plan before doing
   *   translation // 是否在转换前验证Pig逻辑计划
   * // true: 验证Pig计划的正确性，检查类型、引用等
   * // false: 跳过验证，可能提高性能但可能产生错误结果
   * @param usePigRules Whether to use Pig Rules (see PigRelPlanner} to rewrite
   *   translated rel plan // 是否使用Pig特定规则重写转换后的关系计划
   * // true: 应用PIG_RULES规则集，这些规则专门针对Pig转换的计划
   * // false: 不使用Pig特定规则
   *
   * @return A list of root nodes of the translated relational plans. Each of
   * these root corresponds to a sink operator (normally a STORE command) in the
   * Pig plan // 返回转换后的关系代数计划的根节点列表
   * // 每个根节点对应Pig计划中的一个sink操作符（通常是STORE命令）
   * // RelNode代表关系代数操作符，可以是Scan、Filter、Project、Join等
   *
   * @throws IOException Exception during parsing or translating Pig // 解析或转换Pig时的异常
   */
  public List<RelNode> pigQuery2Rel(String pigQuery, boolean planRewrite,
      boolean validate, boolean usePigRules) throws IOException {
    // 开启批处理模式，Pig在批处理模式下可以优化执行计划
    setBatchOn(); // 设置批处理模式为开启状态
    // 注册Pig查询到PigServer，解析并构建Pig逻辑计划
    // registerQuery会解析Pig脚本并构建内部的逻辑计划
    registerQuery(pigQuery); // 注册并解析Pig查询语句
    // 获取当前DAG（有向无环图）的逻辑计划
    // DAG代表Pig脚本的执行流程，每个操作符是一个节点
    final LogicalPlan pigPlan = getCurrentDAG().getLogicalPlan(); // 获取当前Pig逻辑计划
    // 如果需要验证，则验证Pig逻辑计划
    // 验证包括：类型检查、引用检查、语义检查等
    if (validate) { // 检查是否需要验证
      pigPlan.validate(getPigContext(), scope, false); // 验证Pig计划的正确性
    }
    // 将Pig逻辑计划转换为关系代数计划
    // pigPlan2Rel方法负责实际的转换工作
    return pigPlan2Rel(pigPlan, planRewrite, usePigRules); // 转换为关系代数计划
  }

  /**
   * Gets a Pig script string from a file after doing param substitution.
   * 从文件中读取Pig脚本，并进行参数替换
   *
   * @param in Pig script file // Pig脚本文件的输入流
   * // 输入流可以来自本地文件系统或HDFS
   * @param params Param sub map // 参数替换映射表
   * // key: 参数名, value: 参数值
   * // 例如: {"input_path": "/data/input", "output_path": "/data/output"}
   * // 脚本中的$input_path会被替换为"/data/input"
   * // 返回: 参数替换后的Pig脚本字符串
   */
  public String getPigScript(InputStream in, Map<String, String> params)
      throws IOException {
    // 执行参数替换
    // doParamSubstitution会扫描脚本，替换所有${param_name}格式的参数
    // paramMapToList将Map转换为Pig需要的参数列表格式
    return getPigContext().doParamSubstitution(in, paramMapToList(params), null); // 执行参数替换并返回脚本
  }

  /**
   * Parses a Pig script and converts it into relational algebra plans.
   * 从文件中解析Pig脚本并转换为关系代数计划
   *
   * @param fileName File name // Pig脚本文件的路径
   * // 可以是本地文件路径或HDFS路径
   * @param params Param substitution map // 参数替换映射表
   * // 用于替换脚本中的参数占位符
   * @param planRewrite Whether to rewrite the translated plan // 是否重写转换后的计划
   * // true: 应用优化规则重写计划
   * // false: 不进行计划重写
   *
   * @return A list of root nodes of the translated relational plans. Each of
   * these root corresponds to a sink operator (normally a STORE command) in the
   * Pig plan // 返回转换后的关系代数计划的根节点列表
   *
   * @throws IOException Exception during parsing or translating Pig // 解析或转换Pig时的异常
   */
  public List<RelNode> pigScript2Rel(String fileName, Map<String, String> params,
      boolean planRewrite) throws IOException {
    // 开启批处理模式
    setBatchOn(); // 设置批处理模式为开启状态
    // 注册Pig脚本文件，执行参数替换并解析
    // registerScript会读取文件、替换参数、解析脚本
    registerScript(fileName, params); // 注册并解析Pig脚本文件
    // 获取解析后的Pig逻辑计划
    final LogicalPlan pigPlan = getCurrentDAG().getLogicalPlan(); // 获取Pig逻辑计划
    // 验证Pig逻辑计划的正确性
    // 检查类型、引用、语义等
    pigPlan.validate(getPigContext(), scope, false); // 验证Pig计划

    // 将Pig逻辑计划转换为关系代数计划
    // 强制使用Pig特定规则（usePigRules=true）
    return pigPlan2Rel(pigPlan, planRewrite, true); // 转换为关系代数计划
  }

  // 将Pig逻辑计划转换为关系代数计划的核心方法
  // pigPlan: Pig逻辑计划，包含Pig操作符的有向无环图
  // planRewrite: 是否应用转换规则重写计划
  // usePigRules: 是否使用Pig特定规则优化计划
  private List<RelNode> pigPlan2Rel(LogicalPlan pigPlan, boolean planRewrite,
      boolean usePigRules) throws FrontendException {
    // 创建Pig关系操作符遍历器（Walker）
    // PigRelOpWalker负责遍历Pig逻辑计划的拓扑结构
    final PigRelOpWalker walker = new PigRelOpWalker(pigPlan); // 创建遍历器
    // 使用访问者模式将Pig操作符转换为关系代数节点
    // PigRelOpVisitor遍历Pig计划，将每个Pig操作符转换为对应的RelNode
    List<RelNode> relNodes =
        new PigRelOpVisitor(pigPlan, walker, builder).translate(); // 执行转换
    // 获取STORE操作对应的关系节点
    // STORE是Pig的sink操作符，用于输出结果
    // 如果有STORE操作，返回STORE对应的节点；否则返回所有转换的节点
    final List<RelNode> storeRels = builder.getRelsForStores(); // 获取STORE节点
    relNodes = storeRels != null ? storeRels : relNodes; // 选择返回的节点

    // 如果启用Pig规则，应用PIG_RULES规则集优化计划
    // PIG_RULES包含专门针对Pig转换计划的优化规则
    if (usePigRules) { // 检查是否使用Pig规则
      relNodes = optimizePlans(relNodes, PIG_RULES); // 应用Pig规则优化
    }
    // 如果启用计划重写，应用TRANSFORM_RULES规则集优化计划
    // TRANSFORM_RULES包含通用的计划优化规则
    if (planRewrite) { // 检查是否重写计划
      relNodes = optimizePlans(relNodes, TRANSFORM_RULES); // 应用转换规则优化
    }
    // 返回优化后的关系代数计划列表
    return relNodes; // 返回优化后的计划
  }

  /**
   * Converts a Pig script to a list of SQL statements.
   * 将Pig脚本转换为SQL语句列表
   *
   * @param pigQuery Pig script // Pig脚本字符串
   * @param sqlDialect Dialect of SQL language // SQL语言方言
   * // 不同数据库系统的SQL语法可能有所不同，例如MySQL、PostgreSQL、Oracle等
   * // sqlDialect指定生成的SQL应该符合哪个数据库的语法规范
   * @throws IOException Exception during parsing or translating Pig // 解析或转换Pig时的异常
   * // 返回: SQL语句列表，每个RelNode对应一个SQL语句
   */
  public List<String> pigToSql(String pigQuery, SqlDialect sqlDialect)
      throws IOException {
    // 配置SQL输出格式
    // SqlPrettyWriter用于生成格式化的SQL语句
    final SqlWriterConfig config = SqlPrettyWriter.config()
        .withQuoteAllIdentifiers(false) // 不对所有标识符加引号，使SQL更简洁
        .withAlwaysUseParentheses(false) // 不总是使用括号，只在必要时使用
        .withSelectListItemsOnSeparateLines(false) // SELECT列表项不分行，使SQL更紧凑
        .withIndentation(2) // 缩进2个空格
        .withDialect(sqlDialect); // 设置SQL方言
    // 创建SQL美化写入器
    final SqlPrettyWriter writer = new SqlPrettyWriter(config); // 创建SQL写入器
    // 调用重载的pigToSql方法进行转换
    return pigToSql(pigQuery, writer); // 转换为SQL语句
  }

  /**
   * Converts a Pig script to a list of SQL statements.
   * 将Pig脚本转换为SQL语句列表（内部方法）
   *
   * @param pigQuery Pig script // Pig脚本字符串
   * @param writer The SQL writer to decide dialect and format of SQL statements // SQL写入器，决定SQL方言和格式
   * // SqlWriter负责将SqlNode转换为字符串，可以配置格式化选项
   * // 返回: SQL语句列表
   * @throws IOException Exception during parsing or translating Pig // 解析或转换Pig时的异常
   */
  private List<String> pigToSql(String pigQuery, SqlWriter writer)
      throws IOException {
    // 创建Pig特定的RelToSqlConverter
    // RelToSqlConverter负责将关系代数节点转换为SQL抽象语法树（SqlNode）
    // PigRelToSqlConverter是专门为Pig转换的定制版本
    final RelToSqlConverter sqlConverter =
        new PigRelToSqlConverter(writer.getDialect()); // 创建SQL转换器
    // 将Pig查询转换为关系代数计划
    // pigQuery2Rel执行完整的Pig到关系代数的转换流程
    final List<RelNode> finalRels = pigQuery2Rel(pigQuery); // 转换为关系代数计划
    // 创建SQL语句列表
    final List<String> sqlStatements = new ArrayList<>(); // 初始化SQL语句列表
    // 遍历每个关系代数节点，转换为SQL语句
    for (RelNode rel : finalRels) { // 遍历所有关系节点
      // 将RelNode转换为SqlNode（SQL抽象语法树）
      // visitRoot处理整个查询树，asStatement将其转换为语句形式
      final SqlNode sqlNode = sqlConverter.visitRoot(rel).asStatement(); // 转换为SQL AST
      // 将SqlNode序列化为SQL字符串
      // unparse将SqlNode转换为文本格式
      sqlNode.unparse(writer, 0, 0); // 生成SQL字符串
      // 将生成的SQL字符串添加到结果列表
      sqlStatements.add(writer.toString()); // 添加SQL语句
      // 重置写入器，准备生成下一个SQL语句
      writer.reset(); // 重置写入器状态
    }
    // 返回所有SQL语句
    return sqlStatements; // 返回SQL语句列表
  }

  // 优化关系代数计划列表
  // originalRels: 原始的关系代数计划列表
  // rules: 要应用的优化规则集
  private List<RelNode> optimizePlans(List<RelNode> originalRels,
      List<RelOptRule> rules) {
    // 获取第一个关系节点的规划器
    // 所有RelNode共享同一个RelOptPlanner
    final RelOptPlanner planner = originalRels.get(0).getCluster().getPlanner(); // 获取规划器
    // 保存规划器的旧规则集
    // 在重置规划器之前保存当前规则，以便后续恢复
    final List<RelOptRule> oldRules = planner.getRules(); // 获取当前规则集
    // 重置规划器规则，应用新的规则集
    resetPlannerRules(planner, rules); // 设置新的优化规则
    // 创建优化程序
    // Programs.of创建一个基于规则的优化程序
    // RuleSets.ofList将规则列表转换为规则集
    final Program program = Programs.of(RuleSets.ofList(planner.getRules())); // 创建优化程序
    // 创建优化后的计划列表
    final List<RelNode> optimizedPlans = new ArrayList<>(); // 初始化优化计划列表
    // 遍历每个原始关系节点，进行优化
    for (RelNode rel : originalRels) { // 遍历所有原始计划
      // 获取排序信息（如果有）
      // 如果节点是Sort，则获取其排序规则；否则使用空排序
      final RelCollation collation = rel instanceof Sort
          ? ((Sort) rel).collation // 获取Sort节点的排序规则
          : RelCollations.EMPTY; // 使用空排序
      // 应用规划器获得物理计划
      // 将逻辑计划转换为物理计划，应用可枚举约定
      // replace(EnumerableConvention.INSTANCE)将计划转换为可枚举的物理计划
      // replace(collation)保持排序特性
      // simplify()简化特征集
      final RelNode physicalPlan =
          program.run(planner, rel, // 运行优化程序
              rel.getTraitSet().replace(EnumerableConvention.INSTANCE) // 替换为可枚举约定
                  .replace(collation).simplify(), // 替换排序规则并简化
          ImmutableList.of(), ImmutableList.of()); // 传递空参数
      // 将物理计划转换回逻辑计划
      // ToLogicalConverter将物理实现节点转换为逻辑操作符
      // 这样可以在保持优化结果的同时，继续进行逻辑层的优化
      final RelNode logicalPlan = new ToLogicalConverter(builder).visit(physicalPlan); // 转换为逻辑计划
      // 将优化后的逻辑计划添加到结果列表
      optimizedPlans.add(logicalPlan); // 添加优化后的计划
    }
    // 恢复规划器的旧规则集
    resetPlannerRules(planner, oldRules); // 恢复原有规则集
    // 返回优化后的计划列表
    return optimizedPlans; // 返回优化计划列表
  }

  // 重置规划器的规则集
  // planner: 要重置的规划器
  // rulesToSet: 要设置的新规则集
  private static void resetPlannerRules(RelOptPlanner planner,
      List<RelOptRule> rulesToSet) {
    // 清空规划器的所有规则
    planner.clear(); // 清除所有现有规则
    // 添加新的规则集
    // 遍历规则列表，将每个规则添加到规划器中
    for (RelOptRule rule : rulesToSet) { // 遍历所有规则
      planner.addRule(rule); // 添加规则到规划器
    }
  }
}
