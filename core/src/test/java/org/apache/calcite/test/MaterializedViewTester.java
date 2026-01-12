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
package org.apache.calcite.test; // 物化视图测试器所在的包

import org.apache.calcite.DataContexts; // 数据上下文工具类，用于创建空的数据上下文
import org.apache.calcite.adapter.enumerable.EnumerableTableScan; // 可枚举表扫描节点，用于物化视图的表扫描
import org.apache.calcite.config.CalciteConnectionConfig; // Calcite连接配置类
import org.apache.calcite.jdbc.CalciteSchema; // Calcite模式包装类
import org.apache.calcite.jdbc.JavaTypeFactoryImpl; // Java类型工厂实现类
import org.apache.calcite.materialize.MaterializationService; // 物化视图服务类
import org.apache.calcite.plan.RelOptCluster; // 关系表达式优化集群，包含优化器和类型工厂
import org.apache.calcite.plan.RelOptMaterialization; // 物化视图优化对象，包含物化视图的定义和替换信息
import org.apache.calcite.plan.RelOptUtil; // 关系表达式优化工具类，用于转换和打印关系表达式
import org.apache.calcite.prepare.CalciteCatalogReader; // Calcite目录读取器，用于读取模式中的表和函数
import org.apache.calcite.rel.RelNode; // 关系表达式节点接口，表示关系代数操作
import org.apache.calcite.rel.core.RelFactories; // 关系表达式工厂类，用于创建关系表达式
import org.apache.calcite.rel.logical.LogicalTableScan; // 逻辑表扫描节点
import org.apache.calcite.rex.RexExecutorImpl; // 行表达式执行器实现类，用于执行常量折叠等优化
import org.apache.calcite.schema.SchemaPlus; // 模式包装类，提供添加表和子模式的功能
import org.apache.calcite.schema.Table; // 表接口，表示数据库表
import org.apache.calcite.sql.SqlNode; // SQL抽象语法树节点接口
import org.apache.calcite.sql.fun.SqlStdOperatorTable; // SQL标准操作符表
import org.apache.calcite.sql.parser.SqlParseException; // SQL解析异常类
import org.apache.calcite.sql.parser.SqlParser; // SQL解析器，用于将SQL字符串解析为抽象语法树
import org.apache.calcite.sql.validate.SqlValidator; // SQL验证器，用于验证SQL语句的语义正确性
import org.apache.calcite.sql.validate.SqlValidatorUtil; // SQL验证器工具类
import org.apache.calcite.sql2rel.SqlToRelConverter; // SQL到关系表达式转换器，将SQL转换为关系表达式树
import org.apache.calcite.sql2rel.StandardConvertletTable; // 标准转换表，用于将SQL表达式转换为行表达式
import org.apache.calcite.tools.Frameworks; // Calcite框架工具类，用于创建规划器和相关组件
import org.apache.calcite.tools.RelBuilder; // 关系表达式构建器，用于构建关系表达式树
import org.apache.calcite.util.Pair; // 键值对类
import org.apache.calcite.util.TestUtil; // 测试工具类
import org.apache.calcite.util.Util; // 通用工具类

import com.google.common.collect.ImmutableList; // Google Guava的不可变列表类

import java.util.ArrayList; // Java集合框架的动态数组类
import java.util.List; // Java集合框架的列表接口
import java.util.function.Predicate; // Java函数式接口，用于谓词判断

/**
 * 物化视图测试的抽象基类
 * 
 * 该类提供了测试物化视图匹配和替换功能的基础框架
 * 物化视图(Materialized View)是预先计算并存储的查询结果，可以加速查询执行
 * 当查询与物化视图的定义匹配时，优化器会使用物化视图来替换原始查询，从而提高性能
 * 
 * 该类的主要功能包括：
 * 1. 提供抽象方法optimize，允许子类自定义物化视图匹配策略
 * 2. 提供checkMaterialize方法，验证查询能够成功使用物化视图
 * 3. 提供checkNoMaterialize方法，验证查询不能使用物化视图
 * 4. 提供build方法，构建测试配置，包括查询关系表达式和物化视图列表
 * 5. 提供toRel方法，将SQL字符串转换为关系表达式
 * 
 * @see MaterializedViewFixture 物化视图测试辅助类，提供测试数据和配置
 */
public abstract class MaterializedViewTester { // 定义物化视图测试器的抽象基类
  /** 
   * 自定义物化视图匹配策略的抽象方法
   * 
   * 该方法由子类实现，用于定义如何将查询与物化视图进行匹配和替换
   * 不同的子类可以实现不同的优化策略，例如：
   * - 基于规则的匹配策略（使用VolcanoPlanner）
   * - 基于代价的匹配策略（考虑物化视图的维护成本）
   * - 基于子图匹配的策略（寻找查询与物化视图的公共子图）
   * 
   * @param queryRel 查询的关系表达式树，表示需要优化的原始查询
   * @param materializationList 可用的物化视图列表，每个元素包含物化视图的定义和替换信息
   * @return 优化后的关系表达式列表，可能包含使用了物化视图的替换方案
   * 
   * 该方法是整个物化视图测试的核心，不同的实现会产生不同的优化结果
   */
  protected abstract List<RelNode> optimize(RelNode queryRel, // 查询的关系表达式树
      List<RelOptMaterialization> materializationList); // 可用的物化视图列表

  /** 
   * 检查查询能够成功使用物化视图
   * 
   * 该方法验证给定的查询能够被优化器识别并使用指定的物化视图进行替换
   * 如果优化后的结果中包含对物化视图的扫描，则测试通过
   * 否则抛出断言错误，显示所有优化结果供调试
   * 
   * 测试流程：
   * 1. 构建测试配置，包括查询关系表达式和物化视图列表
   * 2. 创建检查器，用于验证优化结果中是否包含物化视图扫描
   * 3. 调用optimize方法进行优化，获取可能的替换方案
   * 4. 检查优化结果中是否有至少一个方案使用了物化视图
   * 5. 如果没有使用物化视图，则抛出异常并显示所有优化结果
   * 
   * @param f 物化视图测试辅助对象，包含查询SQL、物化视图定义和验证逻辑
   * @throws AssertionError 如果优化结果中没有使用物化视图
   */
  void checkMaterialize(MaterializedViewFixture f) { // 检查物化视图匹配的测试方法
    final TestConfig testConfig = build(f); // 构建测试配置，包括查询关系表达式和物化视图列表
    final Predicate<String> checker = // 创建谓词检查器，用于验证优化结果
        Util.first(f.checker, // 优先使用测试夹具提供的自定义检查器
            s -> MaterializedViewFixture.resultContains(s, // 如果没有自定义检查器，使用默认检查器
                "EnumerableTableScan(table=[[" // 检查结果中是否包含物化视图表扫描
                    + testConfig.defaultSchema + ", MV0]]")); // MV0是默认的物化视图名称
    final List<RelNode> substitutes = // 调用优化方法，获取可能的替换方案列表
        optimize(testConfig.queryRel, testConfig.materializationList); // 传入查询和物化视图列表
    if (substitutes.stream() // 检查所有替换方案
        .noneMatch(sub -> checker.test(RelOptUtil.toString(sub)))) { // 如果没有任何方案使用了物化视图
      StringBuilder substituteMessages = new StringBuilder(); // 创建字符串构建器，用于收集错误信息
      for (RelNode sub : substitutes) { // 遍历所有替换方案
        substituteMessages.append(RelOptUtil.toString(sub)).append("\n"); // 将每个方案转换为字符串并添加到错误信息
      }
      throw new AssertionError("Materialized view failed to be matched by optimized results:\n" // 抛出断言错误
          + substituteMessages); // 包含所有优化结果的详细信息
    }
  }

  /** 
   * 检查查询不能使用物化视图
   * 
   * 该方法验证给定的查询不能被优化器使用指定的物化视图进行替换
   * 如果优化后的结果中没有使用物化视图，则测试通过
   * 否则抛出断言错误，表示物化视图被错误地使用了
   * 
   * 测试流程：
   * 1. 构建测试配置，包括查询关系表达式和物化视图列表
   * 2. 调用optimize方法进行优化，获取可能的替换方案
   * 3. 检查优化结果是否为空或只有一个方案且不包含物化视图
   * 4. 如果满足条件，则测试通过
   * 5. 否则抛出异常，显示所有优化结果供调试
   * 
   * 该方法用于测试物化视图匹配的边界条件，确保不会错误地使用不匹配的物化视图
   * 
   * @param f 物化视图测试辅助对象，包含查询SQL、物化视图定义和验证逻辑
   * @throws AssertionError 如果优化结果中错误地使用了物化视图
   */
  void checkNoMaterialize(MaterializedViewFixture f) { // 检查物化视图不匹配的测试方法
    final TestConfig testConfig = build(f); // 构建测试配置
    final List<RelNode> results = // 获取优化结果列表
        optimize(testConfig.queryRel, testConfig.materializationList); // 调用优化方法
    if (results.isEmpty() // 如果优化结果为空（没有找到任何优化方案）
        || (results.size() == 1 // 或者只有一个优化方案
        && !RelOptUtil.toString(results.get(0)).contains("MV0"))) { // 且该方案不包含物化视图MV0
      return; // 测试通过，正常返回
    }
    final StringBuilder errMsgBuilder = new StringBuilder(); // 创建字符串构建器
    errMsgBuilder.append("Optimization succeeds out of expectation: "); // 添加错误消息前缀
    for (RelNode res : results) { // 遍历所有优化结果
      errMsgBuilder.append(RelOptUtil.toString(res)).append("\n"); // 将每个结果转换为字符串并添加到错误信息
    }
    throw new AssertionError(errMsgBuilder.toString()); // 抛出断言错误
  }

  /**
   * 构建测试配置
   * 
   * 该方法负责准备物化视图测试所需的所有组件，包括：
   * 1. 创建Calcite规划器和相关组件
   * 2. 设置默认模式（schema），包含测试所需的表
   * 3. 将查询SQL转换为关系表达式
   * 4. 创建物化视图定义和替换信息
   * 5. 返回包含所有测试配置的TestConfig对象
   * 
   * 该方法使用Frameworks.withPlanner工具方法，自动处理规划器的创建和清理
   * 
   * @param f 物化视图测试辅助对象，包含测试数据和配置
   * @return 测试配置对象，包含默认模式名称、查询关系表达式和物化视图列表
   */
  private TestConfig build(MaterializedViewFixture f) { // 构建测试配置的私有方法
    return Frameworks.withPlanner((cluster, relOptSchema, rootSchema) -> { // 使用框架工具创建规划器
      cluster.getPlanner().setExecutor(new RexExecutorImpl(DataContexts.EMPTY)); // 设置行表达式执行器，用于常量折叠等优化
      try { // 开始try块，捕获可能的异常
        final SchemaPlus defaultSchema; // 声明默认模式变量
        if (f.schemaSpec == null) { // 如果测试夹具没有指定模式规范
          defaultSchema = // 创建默认模式
              rootSchema.add("hr", // 添加名为"hr"的模式
                  new ReflectiveSchemaWithoutRowCount(new MaterializationTest.HrFKUKSchema())); // 使用反射模式包装HR测试模式
        } else { // 如果测试夹具指定了模式规范
          defaultSchema = CalciteAssert.addSchema(rootSchema, f.schemaSpec); // 使用CalciteAssert工具添加模式
        }
        final RelNode queryRel = toRel(cluster, rootSchema, defaultSchema, f.query); // 将查询SQL转换为关系表达式
        final List<RelOptMaterialization> mvs = new ArrayList<>(); // 创建物化视图列表
        final RelBuilder relBuilder = // 创建关系表达式构建器
            RelFactories.LOGICAL_BUILDER.create(cluster, relOptSchema); // 使用逻辑构建器工厂创建
        final MaterializationService.DefaultTableFactory tableFactory = // 创建物化视图表工厂
            new MaterializationService.DefaultTableFactory(); // 使用默认表工厂实现
        for (Pair<String, String> pair : f.materializationList) { // 遍历物化视图定义列表
          String sql = pair.left; // 获取物化视图的SQL定义
          final RelNode mvRel = toRel(cluster, rootSchema, defaultSchema, sql); // 将物化视图SQL转换为关系表达式
          final Table table = // 创建物化视图表对象
              tableFactory.createTable(CalciteSchema.from(rootSchema), // 使用表工厂创建表
                  sql, // 传入SQL定义
                  ImmutableList.of(defaultSchema.getName())); // 传入模式路径
          String name = pair.right; // 获取物化视图名称
          defaultSchema.add(name, table); // 将物化视图表添加到默认模式中
          relBuilder.scan(defaultSchema.getName(), name); // 使用关系表达式构建器扫描物化视图表
          final LogicalTableScan logicalScan = (LogicalTableScan) relBuilder.build(); // 构建逻辑表扫描节点
          final EnumerableTableScan replacement = // 创建可枚举表扫描节点作为替换
              EnumerableTableScan.create(cluster, logicalScan.getTable()); // 使用逻辑扫描的表创建可枚举扫描
          mvs.add( // 将物化视图添加到列表
              new RelOptMaterialization(replacement, mvRel, null, // 创建物化视图优化对象
                  ImmutableList.of(defaultSchema.getName(), name))); // 传入表路径
        }
        return new TestConfig(defaultSchema.getName(), queryRel, mvs); // 返回测试配置对象
      } catch (Exception e) { // 捕获所有异常
        throw TestUtil.rethrow(e); // 重新抛出异常，保留原始异常类型
      }
    });
  }

  /**
   * 将SQL字符串转换为关系表达式
   * 
   * 该方法实现了完整的SQL到关系表达式的转换流程，包括：
   * 1. SQL解析：将SQL字符串解析为抽象语法树（AST）
   * 2. 目录读取：创建目录读取器，用于访问模式中的表和函数
   * 3. SQL验证：验证SQL语句的语义正确性，包括表名、列名、类型等
   * 4. 关系转换：将验证后的SQL转换为关系表达式树
   * 
   * 该方法是Calcite查询处理的核心流程，展示了从SQL到关系代数的完整转换过程
   * 
   * @param cluster 关系表达式优化集群，包含优化器和类型工厂
   * @param rootSchema 根模式，包含所有模式和表
   * @param defaultSchema 默认模式，用于解析未限定的表名
   * @param sql 需要转换的SQL字符串
   * @return 转换后的关系表达式树
   * @throws SqlParseException 如果SQL解析失败
   */
  private RelNode toRel(RelOptCluster cluster, SchemaPlus rootSchema, // 关系表达式优化集群
      SchemaPlus defaultSchema, String sql) throws SqlParseException { // 默认模式和SQL字符串
    final SqlParser parser = SqlParser.create(sql, SqlParser.Config.DEFAULT); // 创建SQL解析器，使用默认配置
    final SqlNode parsed = parser.parseStmt(); // 解析SQL语句，得到抽象语法树

    final CalciteCatalogReader catalogReader = // 创建目录读取器
        new CalciteCatalogReader(CalciteSchema.from(rootSchema), // 从根模式创建Calcite模式
            CalciteSchema.from(defaultSchema).path(null), // 获取默认模式的路径
            new JavaTypeFactoryImpl(), // 创建Java类型工厂
            CalciteConnectionConfig.DEFAULT); // 使用默认连接配置

    final SqlValidator validator = // 创建SQL验证器
        SqlValidatorUtil.newValidator(SqlStdOperatorTable.instance(), // 使用标准操作符表
            catalogReader, new JavaTypeFactoryImpl(), // 传入目录读取器和类型工厂
            SqlValidator.Config.DEFAULT); // 使用默认验证配置
    final SqlNode validated = validator.validate(parsed); // 验证抽象语法树，得到验证后的SQL节点
    final SqlToRelConverter.Config config = SqlToRelConverter.config() // 创建SQL到关系表达式转换器配置
        .withTrimUnusedFields(true) // 启用修剪未使用字段的功能
        .withExpand(true) // 启用展开功能
        .withDecorrelationEnabled(true); // 启用去相关功能
    final SqlToRelConverter converter = // 创建SQL到关系表达式转换器
        new SqlToRelConverter( // 构造转换器
            (rowType, queryString, schemaPath, viewPath) -> { // 创建视图展开器（不支持视图展开）
              throw new UnsupportedOperationException("cannot expand view"); // 抛出不支持异常
            },
            validator, catalogReader, cluster, StandardConvertletTable.INSTANCE, // 传入验证器、目录读取器、集群和转换表
            config); // 传入配置
    return converter.convertQuery(validated, false, true).rel; // 转换查询并返回关系表达式
  }

  /**
   * 测试配置内部类
   * 
   * 该类封装了物化视图测试所需的所有配置信息，包括：
   * 1. 默认模式名称：用于标识当前使用的模式
   * 2. 查询关系表达式：表示需要优化的原始查询
   * 3. 物化视图列表：包含所有可用的物化视图定义和替换信息
   * 
   * 该类是不可变的，所有字段都是final，确保测试配置在构建后不会被修改
   * 使用不可变对象可以避免测试过程中的意外修改，提高测试的可靠性
   */
  private static class TestConfig { // 测试配置内部类
    final String defaultSchema; // 默认模式名称，标识当前使用的模式
    final RelNode queryRel; // 查询的关系表达式树，表示需要优化的原始查询
    final List<RelOptMaterialization> materializationList; // 可用的物化视图列表

    /**
     * 测试配置构造函数
     * 
     * @param defaultSchema 默认模式名称
     * @param queryRel 查询的关系表达式树
     * @param materializationList 可用的物化视图列表
     */
    TestConfig(String defaultSchema, RelNode queryRel, // 构造函数参数
        List<RelOptMaterialization> materializationList) { // 物化视图列表
      this.defaultSchema = defaultSchema; // 保存默认模式名称
      this.queryRel = queryRel; // 保存查询关系表达式
      this.materializationList = materializationList; // 保存物化视图列表
    }
  }
}
