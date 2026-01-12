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
package org.apache.calcite.test; // 定义包名，该类属于org.apache.calcite.test测试包

import org.apache.calcite.rel.RelNode; // 导入关系表达式节点类，代表关系代数操作的基础节点
import org.apache.calcite.rel.RelRoot; // 导入关系表达式根节点类，包含完整的查询树和必要字段信息
import org.apache.calcite.sql.test.SqlTestFactory; // 导入SQL测试工厂类，用于创建SQL测试所需的组件
import org.apache.calcite.sql.test.SqlTester; // 导入SQL测试器接口，定义SQL测试的基本行为
import org.apache.calcite.sql.test.SqlValidatorTester; // 导入SQL验证测试器类，提供默认的SQL验证器实现
import org.apache.calcite.sql.util.SqlOperatorTables; // 导入SQL操作符表工具类，用于管理SQL操作符
import org.apache.calcite.sql.validate.SqlConformance; // 导入SQL一致性接口，定义SQL语法兼容性级别
import org.apache.calcite.sql.validate.SqlValidatorUtil; // 导入SQL验证器工具类，提供验证器创建和配置的静态方法
import org.apache.calcite.sql2rel.SqlToRelConverter; // 导入SQL到关系表达式转换器类，负责将SQL转换为关系代数树
import org.apache.calcite.test.catalog.MockCatalogReaderDynamic; // 导入动态模拟目录读取器类，用于测试动态表
import org.apache.calcite.test.catalog.MockCatalogReaderExtended; // 导入扩展模拟目录读取器类，用于测试扩展功能
import org.apache.calcite.util.TestUtil; // 导入测试工具类，提供测试相关的辅助方法

import org.checkerframework.checker.nullness.qual.Nullable; // 导入可空注解，用于标记可能为null的字段或参数

import java.util.function.Predicate; // 导入谓词函数接口，用于定义条件判断逻辑
import java.util.function.UnaryOperator; // 导入一元操作符函数接口，用于定义对象转换逻辑

import static org.hamcrest.CoreMatchers.containsString; // 导入Hamcrest断言方法，用于验证字符串包含关系
import static org.hamcrest.CoreMatchers.is; // 导入Hamcrest断言方法，用于验证相等性
import static org.hamcrest.MatcherAssert.assertThat; // 导入Hamcrest断言工具类，提供断言方法

import static java.util.Objects.requireNonNull; // 导入Objects工具类方法，用于参数非空检查

/**
 * Parameters for a SQL-to-RelNode test.
 * SQL到RelNode转换测试的参数配置类
 * 该类封装了SQL到关系表达式转换测试所需的所有配置参数，包括SQL语句、验证器配置、
 * 转换器配置、目录读取器等。它提供了链式配置方法，使得测试代码可以灵活地配置
 * 各种转换选项，如是否去相关、是否修剪未使用字段、是否扩展子查询等。
 * 该类是Calcite测试框架的核心组件之一，用于支持SQL到RelNode转换的单元测试。
 */
public class SqlToRelFixture { // 定义SQL到RelNode测试夹具类，封装测试配置参数
  public static final SqlTester TESTER = SqlValidatorTester.DEFAULT; // 定义默认的SQL测试器，使用默认的验证器配置

  public static final SqlToRelFixture DEFAULT = // 定义默认的测试夹具实例，使用标准配置
      new SqlToRelFixture("?", true, TESTER, SqlTestFactory.INSTANCE, false, // 创建基础夹具：SQL为"?"，启用去相关，使用默认测试器和工厂，不修剪字段，不是表达式
          false, null) // 不修剪字段，不是表达式，不使用差异仓库
          .withFactory(f -> // 配置工厂，自定义验证器和转换器
              f.withValidator((opTab, catalogReader, typeFactory, config) -> { // 自定义验证器创建逻辑
                if (config.conformance().allowGeometry()) { // 如果配置允许空间几何类型
                  opTab = // 将空间操作符表链接到现有操作符表
                      SqlOperatorTables.chain(opTab, // 链接基础操作符表
                          SqlOperatorTables.spatialInstance()); // 添加空间操作符实例
                }
                return SqlValidatorUtil.newValidator(opTab, catalogReader, // 创建新的验证器，启用标识符扩展
                    typeFactory, config.withIdentifierExpansion(true)); // 配置启用标识符扩展
              })
                  .withSqlToRelConfig(c -> // 配置SQL到Rel转换器
                      c.withTrimUnusedFields(true) // 启用修剪未使用字段，优化生成的RelNode
                          .withExpand(true) // 启用子查询展开，将子查询转换为JOIN
                          .addRelBuilderConfigTransform(b -> // 添加RelBuilder配置转换
                              b.withAggregateUnique(true) // 聚合时使用唯一键优化
                                  .withPruneInputOfAggregate(false)))); // 不修剪聚合的输入字段

  private final String sql; // 待转换的SQL语句，存储需要测试的SQL查询字符串
  private final @Nullable DiffRepository diffRepos; // 差异仓库，用于存储和比较测试结果，可为null表示不使用
  private final boolean decorrelate; // 是否启用去相关优化，true表示将相关子查询转换为非相关子查询
  private final SqlTester tester; // SQL测试器，负责执行SQL验证和转换的核心组件
  private final SqlTestFactory factory; // SQL测试工厂，负责创建验证器、转换器等测试所需的组件
  private final boolean trim; // 是否修剪未使用的字段，true表示移除RelNode树中未被引用的输出字段
  private final boolean expression; // 是否为表达式模式，true表示SQL是表达式而非完整查询

  SqlToRelFixture(String sql, boolean decorrelate, // 私有构造方法，创建测试夹具实例，参数包括SQL语句、是否去相关、测试器、工厂、是否修剪、是否表达式、差异仓库
      SqlTester tester, SqlTestFactory factory, boolean trim, // 接收测试器和工厂对象，以及修剪和表达式标志
      boolean expression, // 接收表达式标志，指示SQL是表达式还是完整查询
      @Nullable DiffRepository diffRepos) { // 接收可选的差异仓库，用于测试结果比较
    this.sql = requireNonNull(sql, "sql"); // 验证SQL参数非空，否则抛出NullPointerException
    this.tester = requireNonNull(tester, "tester"); // 验证测试器参数非空
    this.factory = requireNonNull(factory, "factory"); // 验证工厂参数非空
    this.diffRepos = diffRepos; // 存储差异仓库引用，可为null
    if (sql.contains(" \n")) { // 检查SQL中是否包含行尾空格（空格后跟换行符）
      throw new AssertionError("trailing whitespace"); // 如果发现行尾空格，抛出断言错误，要求SQL格式规范
    }
    this.decorrelate = decorrelate; // 存储去相关标志
    this.trim = trim; // 存储修剪标志
    this.expression = expression; // 存储表达式标志
  }

  public void ok() { // 断言SQL成功转换为RelNode，不抛出异常，使用默认的计划模板"${plan}"
    convertsTo("${plan}"); // 调用convertsTo方法，验证SQL转换结果匹配期望的计划模板
  }

  public void throws_(String message) { // 断言SQL转换时抛出包含指定消息的异常，用于测试错误场景
    try { // 尝试执行SQL转换
      ok(); // 调用ok方法，期望会抛出异常
    } catch (Throwable throwable) { // 捕获所有可能的异常
      assertThat(TestUtil.printStackTrace(throwable), containsString(message)); // 验证异常堆栈包含指定的错误消息
    }
  }

  public void convertsTo(String plan) { // 断言SQL转换结果匹配指定的计划字符串，用于验证转换的正确性
    tester.assertConvertsTo(factory, diffRepos(), sql, plan, trim, expression, // 调用测试器的断言方法，验证SQL转换结果
        decorrelate); // 传入所有配置参数：工厂、差异仓库、SQL、期望计划、修剪标志、表达式标志、去相关标志
  }

  public DiffRepository diffRepos() { // 获取差异仓库，用于存储和比较测试结果
    return DiffRepository.castNonNull(diffRepos); // 将可能为null的差异仓库转换为非null，如果为null则抛出异常
  }

  public SqlToRelFixture withSql(String sql) { // 返回一个新的夹具，使用指定的SQL语句，其他配置保持不变
    return sql.equals(this.sql) ? this // 如果SQL与当前SQL相同，返回当前实例（避免不必要的对象创建）
        : new SqlToRelFixture(sql, decorrelate, tester, factory, trim, // 否则创建新实例，使用新SQL，保留其他所有配置
            expression, diffRepos); // 保留表达式标志和差异仓库
  }

  /**
   * Sets whether this is an expression (as opposed to a whole query).
   * 设置是否为表达式模式（相对于完整查询）
   * 表达式模式用于测试单个SQL表达式（如1+2、column*2等），而完整查询用于测试SELECT语句
   */
  public SqlToRelFixture expression(boolean expression) { // 返回一个新的夹具，设置指定的表达式标志
    return this.expression == expression ? this // 如果表达式标志与当前相同，返回当前实例
        : new SqlToRelFixture(sql, decorrelate, tester, factory, trim, // 否则创建新实例，使用新的表达式标志
            expression, diffRepos); // 保留差异仓库
  }

  public SqlToRelFixture withConfig( // 返回一个新的夹具，使用指定的转换器配置转换函数
      UnaryOperator<SqlToRelConverter.Config> transform) { // 接收一个转换函数，用于修改SqlToRelConverter的配置
    return withFactory(f -> f.withSqlToRelConfig(transform)); // 通过工厂配置转换器配置，实现链式调用
  }

  public SqlToRelFixture withExpand(boolean expand) { // 返回一个新的夹具，设置是否启用子查询展开
    return withConfig(b -> b.withExpand(expand)); // 使用配置转换器设置expand标志，true表示展开子查询为JOIN
  }

  public SqlToRelFixture withDecorrelate(boolean decorrelate) { // 返回一个新的夹具，设置是否启用去相关优化
    return new SqlToRelFixture(sql, decorrelate, tester, factory, trim, // 创建新实例，使用新的去相关标志
        expression, diffRepos); // 保留其他所有配置不变
  }

  public SqlToRelFixture withFactory( // 返回一个新的夹具，使用指定的工厂转换函数
      UnaryOperator<SqlTestFactory> transform) { // 接收一个转换函数，用于修改SqlTestFactory的配置
    final SqlTestFactory factory = transform.apply(this.factory); // 应用转换函数到当前工厂，得到新工厂
    if (factory == this.factory) { // 如果工厂引用没有变化
      return this; // 返回当前实例，避免不必要的对象创建
    }
    return new SqlToRelFixture(sql, decorrelate, tester, factory, trim, // 否则创建新实例，使用新的工厂
        expression, diffRepos); // 保留其他所有配置不变
  }

  public SqlToRelFixture withCatalogReader( // 返回一个新的夹具，使用指定的目录读取器工厂
      SqlTestFactory.CatalogReaderFactory catalogReaderFactory) { // 接收目录读取器工厂，用于创建自定义的目录读取器
    return withFactory(f -> f.withCatalogReader(catalogReaderFactory)); // 通过工厂配置目录读取器
  }

  public SqlToRelFixture withExtendedTester() { // 返回一个新的夹具，使用扩展的目录读取器
    return withCatalogReader(MockCatalogReaderExtended::create); // 使用MockCatalogReaderExtended创建扩展目录读取器
  }

  public SqlToRelFixture withDynamicTable() { // 返回一个新的夹具，使用动态表的目录读取器
    return withCatalogReader(MockCatalogReaderDynamic::create); // 使用MockCatalogReaderDynamic创建动态表目录读取器
  }

  public SqlToRelFixture withTrim(boolean trim) { // 返回一个新的夹具，设置是否修剪未使用的字段
    return new SqlToRelFixture(sql, decorrelate, tester, factory, trim, // 创建新实例，使用新的修剪标志
        expression, diffRepos); // 保留其他所有配置不变
  }

  public SqlConformance getConformance() { // 获取当前的SQL一致性配置，定义SQL语法的兼容性级别
    return factory.parserConfig().conformance(); // 从工厂的解析器配置中获取一致性设置
  }

  public SqlToRelFixture withConformance(SqlConformance conformance) { // 返回一个新的夹具，使用指定的SQL一致性配置
    return withFactory(f -> // 通过工厂配置解析器和验证器的一致性设置
        f.withParserConfig(c -> c.withConformance(conformance)) // 配置解析器使用指定的一致性
            .withValidatorConfig(c -> c.withConformance(conformance))); // 配置验证器使用指定的一致性
  }

  public SqlToRelFixture withDiffRepos(DiffRepository diffRepos) { // 返回一个新的夹具，使用指定的差异仓库
    return new SqlToRelFixture(sql, decorrelate, tester, factory, trim, // 创建新实例，使用新的差异仓库
        expression, diffRepos); // 保留其他所有配置不变
  }

  public RelRoot toRoot() { // 将SQL转换为RelRoot，包含完整的查询树和必要字段信息
    return tester // 调用测试器的转换方法
        .convertSqlToRel(factory, sql, decorrelate, trim); // 传入工厂、SQL、去相关标志和修剪标志执行转换
  }

  public RelNode toRel() { // 将SQL转换为RelNode，返回关系表达式树的根节点
    return toRoot().rel; // 从RelRoot中提取rel字段，即关系表达式树的根节点
  }

  /** Returns a fixture that meets a given condition, applying a remedy if it
   * does not already.
   * 返回一个满足给定条件的夹具，如果不满足则应用补救措施
   * 该方法用于确保夹具满足特定条件，如果不满足则通过补救函数进行修正
   * 类似于断言模式，但返回修正后的对象而不是抛出异常
   */
  public SqlToRelFixture ensuring(Predicate<SqlToRelFixture> predicate, // 接收一个谓词，定义夹具需要满足的条件
      UnaryOperator<SqlToRelFixture> remedy) { // 接收一个补救函数，当条件不满足时应用
    SqlToRelFixture f = this; // 从当前夹具开始
    if (!predicate.test(f)) { // 测试当前夹具是否满足条件
      f = remedy.apply(f); // 如果不满足，应用补救函数得到新的夹具
      assertThat("remedy failed", predicate.test(f), is(true)); // 断言补救后的夹具满足条件，否则测试失败
    }
    return f; // 返回满足条件的夹具（可能是原始的或补救后的）
  }
}
